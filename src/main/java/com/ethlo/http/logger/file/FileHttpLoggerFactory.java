package com.ethlo.http.logger.file;

import java.util.Map;

import org.springframework.context.support.GenericApplicationContext;
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
    public HttpLogger getInstance(GenericApplicationContext applicationContext, final Map<String, Object> configuration)
    {
        final FileProviderConfig fileProviderConfig = load(configuration, FileProviderConfig.class);
        return new FileLogger(new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }
}
