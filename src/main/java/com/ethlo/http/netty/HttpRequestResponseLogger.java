package com.ethlo.http.netty;

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
        final String requestId = getRequestId(ctx);
        dataBufferRepository.save("write".equalsIgnoreCase(eventName) ? DataBufferRepository.Operation.REQUEST : DataBufferRepository.Operation.RESPONSE, requestId, getBytes(msg));
        return requestId;
    }

    private static String getRequestId(ChannelHandlerContext ctx)
    {
        final Attribute<?> context = ctx.channel().attr(AttributeKey.valueOf("$CONTEXT_VIEW"));
        final Context gatewayCtx = (Context) context.get();
        return gatewayCtx.get(TagRequestIdGlobalFilter.REQUEST_ID_ATTRIBUTE_NAME);
    }

    private byte[] getBytes(ByteBuf buf)
    {
        int readerIndex = buf.readerIndex();
        return ByteBufUtil.getBytes(buf, readerIndex, buf.readableBytes(), false);
    }
}
