package com.ethlo.http.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
@RefreshScope
public class HttpUpstreamServicesUpdateServiceImpl implements HttpUpstreamServicesUpdateService
{
    private static final Logger logger = LoggerFactory.getLogger(HttpUpstreamServicesUpdateServiceImpl.class);
    private final HttpClient httpClient;
    private final UpstreamServiceConfiguration upstreamServiceConfiguration;
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    private final ConcurrentMap<String, LastModifiedRouteDefinition> lastModified = new ConcurrentHashMap<>();

    public HttpUpstreamServicesUpdateServiceImpl(final HttpClient httpClient, UpstreamServiceConfiguration upstreamServiceConfiguration)
    {
        this.httpClient = httpClient;
        this.upstreamServiceConfiguration = upstreamServiceConfiguration;
    }

    private Map<String, RouteDefinition> parse(final ConfigSourceData configSourceData) throws IOException
    {
        final JsonNode root = mapper.readTree(configSourceData.data());
        return Optional.ofNullable(root.at("/spring/cloud/gateway/routes"))
                .filter(node -> !node.isMissingNode())
                .map(array ->
                {
                    final Map<String, RouteDefinition> result = new LinkedHashMap<>();
                    for (int i = 0; i < array.size(); i++)
                    {
                        final JsonNode entry = array.get(i);
                        final String id = Optional.ofNullable(entry.get("id")).map(JsonNode::asText).orElseThrow(() -> new IllegalArgumentException("Missing id for route"));
                        final String uri = Optional.ofNullable(entry.get("uri")).map(JsonNode::asText).orElseThrow(() -> new IllegalArgumentException("Missing uri for route"));
                        final List<PredicateDefinition> predicates = Optional.ofNullable(entry.get("predicates"))
                                .map(ArrayNode.class::cast)
                                .map(predicateArray ->
                                {
                                    final List<PredicateDefinition> predicateDefs = new ArrayList<>();
                                    predicateArray.elements().forEachRemaining(pred -> predicateDefs.add(new PredicateDefinition(pred.asText())));
                                    return predicateDefs;
                                }).orElseThrow(() -> new IllegalArgumentException("Missing predicates for route"));

                        final List<FilterDefinition> filters = Optional.ofNullable(entry.get("filters"))
                                .map(ArrayNode.class::cast)
                                .map(filterArray ->
                                {
                                    final List<FilterDefinition> filterDefs = new ArrayList<>();
                                    filterArray.elements().forEachRemaining(pred -> filterDefs.add(new FilterDefinition(pred.asText())));
                                    return filterDefs;
                                }).orElse(List.of());


                        final String routeId = configSourceData.name() + "_" + i;
                        final RouteDefinition route = new RouteDefinition();
                        route.setId(id);
                        route.setUri(URI.create(uri));
                        route.setPredicates(predicates);
                        route.setFilters(filters);
                        result.put(routeId, route);
                    }
                    return result;
                }).orElseThrow(() -> new IllegalArgumentException("No routes in file"));
    }

    private ConfigSourceData fetchSourceData(UpstreamService upstreamService)
    {
        return httpClient.get().uri(upstreamService.configUrl()).responseSingle((response, bytes) ->
        {
            if (response.status() != HttpResponseStatus.OK)
            {
                return Mono.error(new UncheckedIOException(new IOException("Bad response code: " + response.status())));
            }

            return bytes.asByteArray().map(data -> new ConfigSourceData(upstreamService.name(), response.responseHeaders().get("Content-Type"), data));
        }).block();
    }

    @Scheduled(fixedDelayString = "${upstream.interval:30000}")
    public void update()
    {
        updateAll();
    }

    @Override
    public Map.Entry<Map<String, RouteDefinition>, Boolean> updateAll()
    {
        final AtomicBoolean refreshRequired = new AtomicBoolean(false);
        upstreamServiceConfiguration.getServices().forEach(upstreamService ->
        {
            logger.trace("Fetching upstream config: {}", upstreamService);
            final String uri = upstreamService.configUrl().toString();
            ConfigSourceData configSourceData;
            try
            {
                configSourceData = fetchSourceData(upstreamService);
            }
            catch (Exception exc)
            {
                lastModified.remove(uri);
                logger.warn("Unable to fetch config for upstream service {}: {}", upstreamService, exc.toString());
                return;
            }

            // Create hash of fetched data
            final String hash = hash(configSourceData);

            // Compare the payload hash to the previous payload hash
            lastModified.compute(uri, (k, v) ->
            {
                if (v == null || !v.hash.equals(hash))
                {
                    // We have new data or no data previously
                    try
                    {
                        final Map<String, RouteDefinition> routeDefinitions = parse(configSourceData);
                        refreshRequired.set(true);
                        logger.info("{} upstream config for {}", v == null ? "New" : "Updated", upstreamService);
                        return new LastModifiedRouteDefinition(routeDefinitions, hash, OffsetDateTime.now());
                    }
                    catch (IOException e)
                    {
                        logger.warn("Unable to parse config for upstream service {}: {}", upstreamService, e.toString());
                        return null;
                    }
                }
                else
                {
                    // Old data matches hash
                    return v;
                }
            });
        });

        final Map<String, RouteDefinition> collapsed = lastModified.entrySet().stream()
                .filter(Objects::nonNull)
                .flatMap(e -> e.getValue().routeDefinitions().entrySet().stream().map(en -> Map.entry(e.getKey() + "_" + en.getKey(), en.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return Map.entry(collapsed, refreshRequired.get());
    }

    private String hash(ConfigSourceData configSourceData)
    {
        return Integer.toString(Arrays.hashCode(configSourceData.data()));
    }

    public record LastModifiedRouteDefinition(Map<String, RouteDefinition> routeDefinitions, String hash,
                                              OffsetDateTime lastUpdated)
    {

    }
}