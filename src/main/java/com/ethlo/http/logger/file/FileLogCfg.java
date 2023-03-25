package com.ethlo.http.logger.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty("logging.providers.file.enabled")
@Configuration
public class FileLogCfg
{
    @Bean
    public FileLogger fileLogger(FileProviderConfig fileProviderConfig)
    {
        return new FileLogger(new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }
}
