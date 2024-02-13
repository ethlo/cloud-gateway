package com.ethlo.http.filters.workaround;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@ConditionalOnProperty("workarounds.avoid-double-encoded-url")
@Component
public class WorkaroundGlobalUriFilter implements GlobalFilter, Ordered
{
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        final URI incomingUri = exchange.getRequest().getURI();
        if (isUriEncoded(incomingUri))
        {
            // Get the original Gateway route (contains the service's original host)
            final Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            if (route == null)
            {
                return chain.filter(exchange);
            }
            // Save it as the outgoing URI to call the service, and override the "wrongly" double encoded URI
            // in  ReactiveLoadBalancerClientFilter LoadBalancerUriTools::containsEncodedParts double encoded URI again
            final URI balanceUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
            final URI mergedUri = createUri(incomingUri, balanceUrl);
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, mergedUri);
        }

        return chain.filter(exchange);
    }

    private URI createUri(URI incomingUri, URI balanceUrl)
    {
        final var port = balanceUrl.getPort() != -1 ? ":" + balanceUrl.getPort() : "";
        final var rawPath = balanceUrl.getRawPath() != null ? balanceUrl.getRawPath() : "";
        final var query = incomingUri.getRawQuery() != null ? "?" + incomingUri.getRawQuery() : "";
        return URI.create(balanceUrl.getScheme() + "://" + balanceUrl.getHost() + port + rawPath + query);
    }

    private static boolean isUriEncoded(URI uri)
    {
        return (uri.getRawQuery() != null && uri.getRawQuery().contains("%"))
                || (uri.getRawPath() != null && uri.getRawPath().contains("%"));
    }

    @Override
    public int getOrder()
    {
        return ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER + 1;
    }
}