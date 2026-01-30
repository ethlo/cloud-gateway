package com.ethlo.http.logger.file;

import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.HttpLoggerFactory;

@Component
public class FileHttpLoggerFactory implements HttpLoggerFactory
{
    @Override
    public String getName()
    {
        return "file";
    }

    @Override
    public HttpLogger getInstance(final Map<String, Object> configuration, BiFunction<String, Object, Object> beanRegistration)
    {
        final FileProviderConfig fileProviderConfig = load(configuration, FileProviderConfig.class);
        return new FileLogger(new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }
}
