package com.ethlo.http.logger.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty("http-logging.providers.file.enabled")
@Configuration
public class FileLogCfg
{
    @Bean
    public FileLogger fileLogger(FileProviderConfig fileProviderConfig, FileBodyContentRepository bodyContentRepository)
    {
        return new FileLogger(bodyContentRepository, new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }

    @Bean
    public FileBodyContentRepository fileBodyContentRepository(FileProviderConfig fileProviderConfig)
    {
        return new FileBodyContentRepository(fileProviderConfig.getBodyStorageDirectory());
    }
}
