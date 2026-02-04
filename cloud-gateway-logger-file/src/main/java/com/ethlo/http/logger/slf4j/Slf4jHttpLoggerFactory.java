package com.ethlo.http.logger.slf4j;

import java.util.Map;
import java.util.function.BiFunction;

import com.ethlo.http.logger.rendering.PebbleAccessLogTemplateRenderer;

import org.springframework.stereotype.Component;

import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.HttpLoggerFactory;

@Component
public class Slf4jHttpLoggerFactory implements HttpLoggerFactory
{
    @Override
    public String getName()
    {
        return "slf4j";
    }

    @Override
    public HttpLogger getInstance(final Map<String, Object> configuration, BiFunction<String, Object, Object> beanRegistration)
    {
        final Slf4jProviderConfig fileProviderConfig = load(configuration, Slf4jProviderConfig.class);
        return new Slf4jFileLogger(new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }
}
