package com.ethlo.http.blocking;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.RoutePredicateLocator;
import com.ethlo.http.blocking.configuration.HttpLoggingConfiguration;
import com.ethlo.http.blocking.model.WebExchangeDataProvider;
import com.ethlo.http.logger.LoggingFilterService;
import com.ethlo.http.logger.delegate.SequentialDelegateLogger;
import com.ethlo.http.netty.PredicateConfig;
import com.ethlo.http.processors.LogPreProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//@RefreshScope
@Component
public class TagRequestIdMvcFilter extends OncePerRequestFilter implements Ordered
{
    private static final Logger logger = LoggerFactory.getLogger(TagRequestIdMvcFilter.class);

    private final LoggingFilterService loggingFilterService;
    private final SequentialDelegateLogger httpLogger;
    private final DataBufferRepository repository;
    private final LogPreProcessor logPreProcessor;
    private final List<PredicateConfig> predicateConfigs;
    private final boolean autoCleanup;

    public TagRequestIdMvcFilter(LoggingFilterService loggingFilterService,
                                 SequentialDelegateLogger httpLogger,
                                 DataBufferRepository repository,
                                 LogPreProcessor logPreProcessor,
                                 HttpLoggingConfiguration httpLoggingConfiguration,
                                 RoutePredicateLocator routePredicateLocator,
                                 @Value("${content-logging.buffer-files.cleanup:true}") final boolean autoCleanup)
    {
        this.loggingFilterService = loggingFilterService;
        this.httpLogger = httpLogger;
        this.repository = repository;
        this.logPreProcessor = logPreProcessor;
        this.autoCleanup = autoCleanup;
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
        final long started = System.nanoTime();

        // deterministic matching
        final PredicateConfig matchedConfig = predicateConfigs.stream()
                .filter(c -> c.predicate().test(request))
                .findFirst()
                .orElse(null);

        if (matchedConfig == null)
        {
            // No match. Just pass through
            filterChain.doFilter(request, response);
            return;
        }

        final String requestId = generateId();

        request.setAttribute("requestId", requestId);

        // We use your repository to stream directly to disk instead of memory
        final MvcRequestCapture wrappedRequest = new MvcRequestCapture(request, requestId, repository);
        final MvcResponseCapture wrappedResponse = new MvcResponseCapture(response, requestId, repository);

        Throwable connectionException = null;
        try
        {
            filterChain.doFilter(wrappedRequest, wrappedResponse);

            // Ensure response is fully flushed to capture the body
            wrappedResponse.copyBodyToResponse();
        }
        catch (Throwable e)
        {
            connectionException = e;
            handleException(requestId, e, response);
        } finally
        {
            saveLog(wrappedRequest, wrappedResponse, requestId, started, connectionException, loggingFilterService.merge(matchedConfig));
        }
    }

    private String generateId()
    {
        final String timestampPart = Long.toString(Instant.now().toEpochMilli(), 36);
        final String randomPart = new BigInteger(56, ThreadLocalRandom.current()).toString(36);
        return timestampPart + "-" + randomPart;
    }

    private void saveLog(MvcRequestCapture req, MvcResponseCapture res, String requestId, long start, Throwable exc, final PredicateConfig mergedConfig)
    {
        try
        {
            final Duration duration = Duration.ofNanos(System.nanoTime() - start);

            final Route route = getRoute(req);

            final WebExchangeDataProvider provider = new WebExchangeDataProvider(repository, mergedConfig)
                    .requestId(requestId)
                    .method(HttpMethod.valueOf(req.getMethod()))
                    .path(req.getRequestURI())
                    .route(route)
                    .uri(java.net.URI.create(req.getRequestURL().toString()))
                    .statusCode(HttpStatus.valueOf(res.getStatus()))
                    .requestHeaders(ServletUtil.extractHeaders(req))
                    .responseHeaders(ServletUtil.extractHeaders(res))
                    .duration(duration)
                    .timestamp(OffsetDateTime.now())
                    .exception(exc);

            httpLogger.accessLog(logPreProcessor.process(provider));
        }
        catch (Exception e)
        {
            logger.error("Failed to save log for {}", requestId, e);
            repository.persistForError(requestId);
        } finally
        {
            if (autoCleanup)
            {
                repository.cleanup(requestId);
            }
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