package com.ethlo.http.logger.direct_async;

import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.ethlo.Beta;
import com.ethlo.http.DataBufferRepository;
import com.ethlo.http.configuration.BeanProvider;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.HttpLoggerFactory;
import com.ethlo.http.logger.rendering.PebbleAccessLogTemplateRenderer;

@Beta
@Component
public class DirectHttpLoggerFactory implements HttpLoggerFactory
{
    @Override
    public String getName()
    {
        return "direct_file";
    }

    @Override
    public HttpLogger getInstance(final Map<String, Object> configuration, BiFunction<String, Object, Object> beanRegistration)
    {
        final DirectFileProviderConfig config = load(configuration, DirectFileProviderConfig.class);
        return new DirectFileLogger(new PebbleAccessLogTemplateRenderer(config.pattern(), false), config.storageDirectory(), config.maxRolloverSize(), BeanProvider.get(DataBufferRepository.class));
    }
}
