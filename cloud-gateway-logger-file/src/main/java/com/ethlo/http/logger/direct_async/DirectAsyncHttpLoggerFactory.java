package com.ethlo.http.logger.direct_async;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.ethlo.Beta;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.HttpLoggerFactory;
import com.ethlo.http.logger.rendering.PebbleAccessLogTemplateRenderer;

@Beta
@Component
public class DirectAsyncHttpLoggerFactory implements HttpLoggerFactory
{
    @Override
    public String getName()
    {
        return "direct_async";
    }

    @Override
    public HttpLogger getInstance(final Map<String, Object> configuration, BiFunction<String, Object, Object> beanRegistration)
    {
        final DirectAsyncFileProviderConfig config = load(configuration, DirectAsyncFileProviderConfig.class);
        final OutputStream target = createLogTarget(config);
        return new DirectAsyncFileLogger(new PebbleAccessLogTemplateRenderer(config.pattern(), false), target);
    }

    private OutputStream createLogTarget(final DirectAsyncFileProviderConfig config)
    {
        if (config.storageDirectory() == null)
        {
            return System.out;
        }
        try
        {
            return new BufferedOutputStream(Files.newOutputStream(config.storageDirectory(), StandardOpenOption.APPEND, StandardOpenOption.CREATE));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
