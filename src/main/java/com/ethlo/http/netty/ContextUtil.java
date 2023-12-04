package com.ethlo.http.netty;

import static com.ethlo.http.netty.TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME;
import static com.ethlo.http.netty.TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.reactive.function.server.ServerRequest;

import io.micrometer.observation.Observation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import reactor.util.context.Context;

public class ContextUtil
{
    public static Optional<PredicateConfig> getLoggingConfig(ChannelHandlerContext ctx)
    {
        return getAttributes(ctx).map(m -> m.get(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME)).map(PredicateConfig.class::cast);
    }

    public static Optional<String> getRequestId(ChannelHandlerContext ctx)
    {
        return getAttributes(ctx).map(m -> m.get(REQUEST_ID_ATTRIBUTE_NAME)).map(String.class::cast);
    }

    public static Optional<PredicateConfig> getLoggingConfig(ServerRequest context)
    {
        return context.attribute(LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME).map(PredicateConfig.class::cast);
    }


    private static Optional<Map<String, Object>> getAttributes(ChannelHandlerContext ctx)
    {
        final Optional<Context> contextView = Optional.ofNullable(ctx.channel().attr(AttributeKey.valueOf("$CONTEXT_VIEW")))
                .filter(o -> o instanceof Context)
                .map(Context.class::cast);
        return contextView.flatMap(context -> Optional.ofNullable(context.getOrDefault("micrometer.observation", null))
                .map(Observation.class::cast)
                .map(Observation::getContextView)
                .map(ServerRequestObservationContext.class::cast)
                .map(ServerRequestObservationContext::getAttributes));
    }

}
