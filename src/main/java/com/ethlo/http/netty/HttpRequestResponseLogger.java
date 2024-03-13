package com.ethlo.http.netty;

import static com.ethlo.http.netty.ContextUtil.getLoggingConfig;
import static com.ethlo.http.netty.ContextUtil.getRequestId;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.util.concurrent.ExecutionException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class HttpRequestResponseLogger extends LoggingHandler
{
    public static final String WRITE = "WRITE";
    public static final String READ = "READ";
    private final DataBufferRepository dataBufferRepository;

    public HttpRequestResponseLogger(final DataBufferRepository dataBufferRepository)
    {
        super(LogLevel.TRACE, ByteBufFormat.HEX_DUMP);
        this.dataBufferRepository = dataBufferRepository;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
    {
        format(ctx, WRITE, msg);
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg)
    {
        format(ctx, READ, msg);
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
        final ServerDirection operation = WRITE.equalsIgnoreCase(eventName) ? REQUEST : RESPONSE;

        return getLoggingConfig(ctx).map(predicateConfig ->
        {
            final String requestId = getRequestId(ctx).orElseThrow();

            final int bytesAvailable = msg.readableBytes();
            dataBufferRepository.appendSizeAvailable(operation, requestId, bytesAvailable);
            final boolean isRequestAndShouldStore = predicateConfig.request().mustBuffer() && operation == REQUEST;
            final boolean isResponseAndShouldStore = predicateConfig.response().mustBuffer() && operation == RESPONSE;
            if (isRequestAndShouldStore || isResponseAndShouldStore)
            {
                try
                {
                    dataBufferRepository.write(operation, requestId, msg.nioBuffer()).get();
                }
                catch (InterruptedException | ExecutionException e)
                {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return requestId;
        }).orElse(null);
    }

    private byte[] getBytes(ByteBuf buf)
    {
        return ByteBufUtil.getBytes(buf, buf.readerIndex(), buf.readableBytes(), false);
    }
}
