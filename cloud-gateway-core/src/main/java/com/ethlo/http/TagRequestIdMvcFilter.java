package com.ethlo.http;

import static com.ethlo.http.ServletUtil.sanitizeHeaders;

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

import org.apache.catalina.connector.ClientAbortException;
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
import com.ethlo.http.util.ConfigUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
                                 HttpLoggingConfiguration httpLoggingConfiguration)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.repository = repository != null ? repository : NopDataBufferRepository.INSTANCE;
        this.logPreProcessor = logPreProcessor;
        this.predicateConfigs = ConfigUtil.toMatchers(httpLoggingConfiguration.getMatchers());
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

    private static void handleNoMatch(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain, Chronograph chronograph, String requestId)
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
                        handleNoMatch(request, response, filterChain, chronograph, requestId);
                        return;
                    }

                    handleMatch(request, response, filterChain, chronograph, requestId, matchedConfig);
                }
        );
        performanceLogger.debug("{}", chronograph);
    }

    private void handleMatch(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain, Chronograph chronograph, String requestId, PredicateConfig matchedConfig)
    {
        // Write Request headers immediately
        chronograph.time("persist_request_headers", () ->
                repository.putHeaders(ServerDirection.REQUEST, requestId, sanitizeHeaders(matchedConfig.request().headers(), ServletUtil.extractHeaders(request)))
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
                    repository.putHeaders(ServerDirection.RESPONSE, requestId, sanitizeHeaders(matchedConfig.response().headers(), ServletUtil.extractHeaders(response)))
            );

            chronograph.time("buffer_flush", wrappedResponse::copyBodyToResponse);
        }
        catch (Throwable e)
        {
            connectionException = (e instanceof RuntimeException && e.getCause() != null) ? e.getCause() : e;
            handleException(connectionException, requestId, request, response);
        } finally
        {
            final Throwable finalExc = connectionException;
            chronograph.time("logging", () ->
                    saveLog(wrappedRequest, wrappedResponse, requestId, chronograph, finalExc, loggingFilterService.merge(matchedConfig))
            );
        }
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
                                .protocol(req.getProtocol())

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

    private void handleException(Throwable exception, final String requestId, final HttpServletRequest request, HttpServletResponse response)
    {
        final Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(exception);
        if (rootCause instanceof SocketException)
        {
            handle(rootCause, requestId, request, HttpStatus.BAD_GATEWAY, response);
        }
        else if (rootCause instanceof ClientAbortException)
        {
            handle(rootCause, requestId, request, HttpStatus.valueOf(499), response);
        }
        else
        {
            logger.warn("Unexpected exception {} occurred during request processing for request {}: {} {}", rootCause.getClass().getName(), requestId, request.getMethod(), request.getRequestURI(), exception);
        }
    }

    private void handle(final Throwable rootCause, String requestId, HttpServletRequest request, HttpStatus httpStatus, HttpServletResponse response)
    {
        logger.info("Error {} during request processing for request {}: {} {}", rootCause.getClass().getName(), requestId, request.getMethod(), request.getRequestURI());
        response.setStatus(httpStatus.value());
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}