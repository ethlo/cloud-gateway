package com.ethlo.http.logger.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty("logging.provider.file.enabled")
@Configuration
public class FileLogCfg
{
    @Bean
    public FileLogger fileLogger(@Value("${logging.provider.file.pattern}") final String pattern)
    {
        return new FileLogger(new PebbleAccessLogTemplateRenderer(pattern, false));
    }
}
