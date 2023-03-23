package reactor.netty.http.server.logging;

import java.lang.reflect.Field;
import java.util.Objects;

import org.springframework.util.ReflectionUtils;

import reactor.netty.http.server.HttpServerRequest;

public class NettyLoggingBridge
{
    public static HttpServerRequest getRequest(final AccessLogArgProvider accessLogArgProvider)
    {
        final Field requestField = Objects.requireNonNull(ReflectionUtils.findField(AccessLogArgProviderH1.class, "request"));
        ReflectionUtils.makeAccessible(requestField);
        return (HttpServerRequest) ReflectionUtils.getField(requestField, accessLogArgProvider);
    }
}
