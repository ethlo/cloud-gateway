package com.ethlo.http.netty;

import static com.ethlo.http.netty.ContextUtil.getLoggingConfig;
import static com.ethlo.http.netty.ContextUtil.getRequestId;
import static com.ethlo.http.netty.ServerDirection.REQUEST;
import static com.ethlo.http.netty.ServerDirection.RESPONSE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        final ServerDirection serverDirection = WRITE.equalsIgnoreCase(eventName) ? REQUEST : RESPONSE;

        return getLoggingConfig(ctx).map(predicateConfig ->
        {
            final String requestId = getRequestId(ctx).orElseThrow();

            final int bytesAvailable = msg.readableBytes();
            dataBufferRepository.appendSizeAvailable(serverDirection, requestId, bytesAvailable);
            final boolean isRequestAndShouldStore = predicateConfig.request().mustBuffer() && serverDirection == REQUEST;
            final boolean isResponseAndShouldStore = predicateConfig.response().mustBuffer() && serverDirection == RESPONSE;
            if (isRequestAndShouldStore || isResponseAndShouldStore)
            {
                final byte[] data = getBytes(msg);
                if (bytesAvailable > 0)
                {
                    try
                    {
                        final int written = dataBufferRepository.write(serverDirection, requestId, data).get(10, TimeUnit.SECONDS);
                        logger.debug("Wrote {} bytes for {} for request {}", written, serverDirection.name(), requestId);
                    }
                    catch (InterruptedException exc)
                    {
                        Thread.currentThread().notify();
                    }
                    catch (ExecutionException | TimeoutException exc)
                    {
                        throw new UncheckedIOException(new IOException(exc));
                    }
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
