package com.ethlo.http.io;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.MappingMediaTypeFileExtensionResolver;

@Configuration
public class MultiFileSystemConfig
{
    private final FileSystemProperties fileSystemProperties;

    public MultiFileSystemConfig(FileSystemProperties fileSystemProperties)
    {
        this.fileSystemProperties = fileSystemProperties;
    }

    @Bean
    public MappingMediaTypeFileExtensionResolver mediaTypeMappings()
    {
        return new MappingMediaTypeFileExtensionResolver(fileSystemProperties.getExtensions());
    }
}
