package com.ethlo.http;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ethlo.chronograph.Chronograph;
import com.ethlo.http.configuration.HttpLoggingConfiguration;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.logger.delegate.DelegateHttpLogger;
import com.ethlo.http.model.WebExchangeDataProvider;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.processors.LogPreProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//@RefreshScope
@Component
public class TagRequestIdMvcFilter extends OncePerRequestFilter implements Ordered
{
    private static final Logger performanceLogger = LoggerFactory.getLogger("chronograph");
    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdMvcFilter.class);

    private final LoggingFilterService loggingFilterService;
    private final DelegateHttpLogger httpLogger;
    private final DataBufferRepository repository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;

    public TagRequestIdMvcFilter(LoggingFilterService loggingFilterService,
                                 DelegateHttpLogger httpLogger,
                                 DataBufferRepository repository,
                                 LogPreProcessor logPreProcessor,
                                 HttpLoggingConfiguration httpLoggingConfiguration,
                                 RoutePredicateLocator routePredicateLocator)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.repository = repository != null ? repository : NopDataBufferRepository.INSTANCE;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = httpLoggingConfiguration.getMatchers()
                .stream()
                .map(c ->
                {
                    List<MvcPredicateDefinition> mvcDefs = c.predicates().stream()
                            .map(p -> new MvcPredicateDefinition(p.name(), p.args()))
                            .toList();

                    final Predicate<HttpServletRequest> combinedPredicate = routePredicateLocator.getPredicates(mvcDefs);

                    return new PredicateConfig(c.id(), combinedPredicate, c.request(), c.response());
                })
                .toList();
    }

    @NotNull
    private static Route getRoute(MvcRequestCapture req)
    {
        // Attributes are null if the request didn't match any route (e.g., a 404)
        final String routeId = Optional.ofNullable(req.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR))
                .map(Object::toString)
                .orElse("unmatched");

        // Fallback to a safe, non-null URI if the gateway hasn't determined a target
        final URI routeUri = Optional.ofNullable((URI) req.getAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR))
                .orElse(URI.create("unknown://404"));

        return new Route(routeId, routeUri);
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException
    {
        final Chronograph chronograph = Chronograph.create();
        chronograph.time("request", () ->
                {
                    final String requestId = generateId();
                    logger.debug("Starting request id {}", requestId);

                    request.setAttribute("requestId", requestId);
                    request.setAttribute("chronograph", chronograph);

                    final PredicateConfig matchedConfig = chronograph.time("predicate_match", () ->
                            predicateConfigs.stream()
                                    .filter(c -> c.predicate().test(request))
                                    .findFirst()
                                    .orElse(null)
                    );

                    if (matchedConfig == null)
                    {
                        chronograph.time("upstream", () ->
                                {
                                    try
                                    {
                                        filterChain.doFilter(request, response);
                                        logger.debug("Finished request id {} without capture", requestId);
                                    }
                                    catch (IOException | ServletException e)
                                    {
                                        throw new RuntimeException(e);
                                    }
                                }
                        );
                        return;
                    }

                    // Write Request headers immediately
                    chronograph.time("persist_request_headers", () ->
                            repository.writeHeaders(ServerDirection.REQUEST, requestId, ServletUtil.extractHeaders(request))
                    );

                    final MvcRequestCapture wrappedRequest = new MvcRequestCapture(chronograph, request, requestId, repository);
                    final MvcResponseCapture wrappedResponse = new MvcResponseCapture(chronograph, response, requestId, repository);

                    Throwable connectionException = null;
                    try
                    {
                        chronograph.time("upstream", () -> {
                                    try
                                    {
                                        filterChain.doFilter(wrappedRequest, wrappedResponse);
                                        logger.debug("Finished request id {} with capture", requestId);
                                    }
                                    catch (IOException | ServletException e)
                                    {
                                        throw new RuntimeException(e);
                                    }
                                }
                        );

                        chronograph.time("persist_response_headers", () ->
                                repository.writeHeaders(ServerDirection.RESPONSE, requestId, ServletUtil.extractHeaders(response))
                        );

                        chronograph.time("buffer_flush", wrappedResponse::copyBodyToResponse);
                    }
                    catch (Throwable e)
                    {
                        connectionException = (e instanceof RuntimeException && e.getCause() != null) ? e.getCause() : e;
                        handleException(requestId, connectionException, response);
                    } finally
                    {
                        final Throwable finalExc = connectionException;
                        chronograph.time("logging", () ->
                                saveLog(wrappedRequest, wrappedResponse, requestId, chronograph, finalExc, loggingFilterService.merge(matchedConfig))
                        );
                    }
                }
        );
        performanceLogger.debug("{}", chronograph);
    }

    private String generateId()
    {
        final String timestampPart = Long.toString(Instant.now().toEpochMilli(), 36);
        final String randomPart = new BigInteger(56, ThreadLocalRandom.current()).toString(36);
        return timestampPart + "-" + randomPart;
    }

    private void saveLog(MvcRequestCapture req, MvcResponseCapture res, String requestId, Chronograph chronograph, Throwable exc, final PredicateConfig mergedConfig)
    {
        try
        {
            final WebExchangeDataProvider provider = chronograph.time("prepare_log_data", () ->
                    {
                        final Duration requestTime = chronograph.getTotalTime();

                        final Route route = getRoute(req);

                        return new WebExchangeDataProvider(repository, mergedConfig)
                                .requestId(requestId)
                                .cleanupTask(() -> repository.cleanup(requestId))
                                .method(HttpMethod.valueOf(req.getMethod().toUpperCase()))
                                .path(req.getRequestURI())
                                .route(route)
                                .uri(java.net.URI.create(req.getRequestURL().toString()))
                                .statusCode(HttpStatus.valueOf(res.getStatus()))
                                .duration(requestTime)
                                .timestamp(OffsetDateTime.now())
                                .exception(exc);
                    }
            );
            chronograph.time("log_providers", () -> httpLogger.accessLog(chronograph, logPreProcessor.process(provider)));
        }
        catch (Exception e)
        {
            logger.error("Failed to save log for {}", requestId, e);
            repository.persistForError(requestId);
        }
    }

    private void handleException(final String requestId, Throwable e, HttpServletResponse response)
    {
        if (isReset(e))
        {
            logger.warn("Connection reset during request processing for request {}", requestId);
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
        }
        else
        {
            logger.warn("Exception occurred during request processing for request {}", requestId, e);
        }
    }

    private boolean isReset(Throwable ex)
    {
        final Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(ex);
        return rootCause instanceof SocketException;
    }


    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}