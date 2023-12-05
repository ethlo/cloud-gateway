package com.ethlo.http.netty;

import static com.ethlo.http.netty.TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME;

import java.util.Optional;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import reactor.util.context.Context;

public class ContextUtil
{
    public static Optional<PredicateConfig> getLoggingConfig(ChannelHandlerContext ctx)
    {
        return getServerWebExchange(ctx).map(r -> r.getAttribute(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME)).map(PredicateConfig.class::cast);
    }

    public static Optional<String> getRequestId(ChannelHandlerContext ctx)
    {
        return getServerWebExchange(ctx).map(ServerWebExchange::getRequest).map(ServerHttpRequest::getId);
    }

    public static Optional<PredicateConfig> getLoggingConfig(ServerRequest context)
    {
        return context.attribute(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME).map(PredicateConfig.class::cast);
    }

    private static Optional<ServerWebExchange> getServerWebExchange(ChannelHandlerContext ctx)
    {
        final Optional<Attribute<?>> contextView = getContextView(ctx);
        final String key = ServerWebExchangeContextFilter.EXCHANGE_CONTEXT_ATTRIBUTE;
        return contextView.map(Attribute::get).map(Context.class::cast).map(context -> context.getOrDefault(key, null))
                .map(ServerWebExchange.class::cast);
    }

    private static Optional<Attribute<?>> getContextView(ChannelHandlerContext channelHandlerContext)
    {
        final Object attr = channelHandlerContext.channel().attr(AttributeKey.valueOf("$CONTEXT_VIEW"));
        return Optional.ofNullable(attr).map(Attribute.class::cast);
    }
}
