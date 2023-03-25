package com.ethlo.http.netty;

import java.util.Optional;

import com.ethlo.http.match.RequestMatchingProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import reactor.util.context.Context;

public class HttpRequestResponseLogger extends LoggingHandler
{
    private final DataBufferRepository dataBufferRepository;

    public HttpRequestResponseLogger(final DataBufferRepository dataBufferRepository)
    {
        super(LogLevel.TRACE, ByteBufFormat.HEX_DUMP);
        this.dataBufferRepository = dataBufferRepository;
    }

    private static Optional<String> getRequestId(ChannelHandlerContext ctx)
    {
        final Context gatewayCtx = getContext(ctx);
        return gatewayCtx.hasKey(TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME) ? Optional.of(gatewayCtx.get(TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME)) : Optional.empty();
    }

    private static Context getContext(ChannelHandlerContext ctx)
    {
        final Attribute<?> context = ctx.channel().attr(AttributeKey.valueOf("$CONTEXT_VIEW"));
        return (Context) context.get();
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
    {
        format(ctx, "WRITE", msg);
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg)
    {
        format(ctx, "READ", msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    protected String format(ChannelHandlerContext ctx, String eventName, Object arg)
    {
        if (arg instanceof ByteBuf byteBuf)
        {
            return format(ctx, eventName, byteBuf);
        }
        return super.format(ctx, eventName, arg);
    }

    private String format(ChannelHandlerContext ctx, String eventName, ByteBuf msg)
    {
        return getRequestId(ctx).map(requestId ->
        {
            final DataBufferRepository.Operation operation = "write".equalsIgnoreCase(eventName) ? DataBufferRepository.Operation.REQUEST : DataBufferRepository.Operation.RESPONSE;
            final RequestMatchingProcessor pattern = getRequestPattern(ctx).orElseThrow();
            if (pattern.isLogRequestBody() && operation == DataBufferRepository.Operation.REQUEST
                    || pattern.isLogResponseBody() && operation == DataBufferRepository.Operation.RESPONSE)
            {
                final byte[] data = getBytes(msg);
                if (data.length > 0)
                {
                    dataBufferRepository.save(operation, requestId, data);
                }
            }
            return requestId;
        }).orElse(null);
    }

    private Optional<RequestMatchingProcessor> getRequestPattern(ChannelHandlerContext ctx)
    {
        final Context gatewayCtx = getContext(ctx);
        return gatewayCtx.hasKey(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME) ? Optional.of(gatewayCtx.get(TagRequestIdGlobalFilter.LOG_CAPTURE_CONFIG_ATTRIBUTE_NAME)) : Optional.empty();
    }

    private byte[] getBytes(ByteBuf buf)
    {
        int readerIndex = buf.readerIndex();
        return ByteBufUtil.getBytes(buf, readerIndex, buf.readableBytes(), false);
    }
}
