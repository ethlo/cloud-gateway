package com.ethlo.http.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Configuration
@RefreshScope
public class HttpUpstreamServicesCfg
{
    private final HttpClient httpClient;
    private final UpstreamServiceConfiguration upstreamServiceConfiguration;

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));

    private static final Logger logger = LoggerFactory.getLogger(HttpUpstreamServicesCfg.class);

    public HttpUpstreamServicesCfg(final HttpClient httpClient, UpstreamServiceConfiguration upstreamServiceConfiguration)
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


                        final RouteDefinition route = new RouteDefinition();
                        route.setId(id);
                        route.setUri(URI.create(uri));
                        route.setPredicates(predicates);
                        route.setFilters(filters);
                        result.put(configSourceData.name() + "_" + i, route);
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

    @Bean
    @RefreshScope
    public UpstreamServiceProperties upstreamServiceProperties()
    {
        final Map<String, RouteDefinition> mappings = new ConcurrentHashMap<>();

        upstreamServiceConfiguration.getServices().forEach(upstreamService ->
        {
            logger.info("Fetching upstream config: {}", upstreamService);
            try
            {
                final ConfigSourceData configSourceData = fetchSourceData(upstreamService);
                final Map<String, RouteDefinition> routeDefinitions = parse(configSourceData);
                mappings.putAll(routeDefinitions);
            }
            catch (Exception exc)
            {
                logger.warn("Unable to process property source from {}: {}", upstreamService, exc.toString());
            }
        });
        return new UpstreamServiceProperties(mappings);
    }
}