package com.ethlo.http.logger.file;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ethlo.http.logger.BodyContentRepository;
import com.ethlo.http.logger.MetadataContentRepository;

@ConditionalOnProperty("http-logging.providers.file.enabled")
@Configuration
public class FileLogCfg
{
    @Bean
    public FileLogger fileLogger(FileProviderConfig fileProviderConfig, MetadataContentRepository fileMetaContentRepository, BodyContentRepository bodyContentRepository)
    {
        return new FileLogger(fileMetaContentRepository, bodyContentRepository, new PebbleAccessLogTemplateRenderer(fileProviderConfig.getPattern(), false));
    }

    @Bean
    public FileMetadataContentRepository fileMetadataContentRepository(FileProviderConfig fileProviderConfig)
    {
        return new FileMetadataContentRepository(fileProviderConfig.getStorageDirectory());
    }

    @Bean
    public FileBodyContentRepository fileBodyContentRepository(FileProviderConfig fileProviderConfig)
    {
        return new FileBodyContentRepository(fileProviderConfig.getStorageDirectory());
    }
}
