package com.ethlo.http.netty;

import java.util.Optional;

import org.springframework.web.reactive.function.server.ServerRequest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import reactor.util.context.Context;

public class ContextUtil
{
    public static Optional<PredicateConfig> getLoggingConfig(ChannelHandlerContext ctx)
    {
        return getOptional(getContext(ctx), TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME);
    }

    private static <T> Optional<T> getOptional(Context gatewayCtx, String attrName)
    {
        return gatewayCtx.hasKey(attrName) ? Optional.of(gatewayCtx.get(attrName)) : Optional.empty();
    }

    public static Optional<String> getRequestId(ChannelHandlerContext ctx)
    {
        return getOptional(getContext(ctx), TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME);
    }

    private static Context getContext(ChannelHandlerContext ctx)
    {
        final Attribute<?> context = ctx.channel().attr(AttributeKey.valueOf("$CONTEXT_VIEW"));
        return (Context) context.get();
    }

    public static Optional<PredicateConfig> getLoggingConfig(ServerRequest context)
    {
        return context.attribute(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME)
                .map(PredicateConfig.class::cast);
    }
}
