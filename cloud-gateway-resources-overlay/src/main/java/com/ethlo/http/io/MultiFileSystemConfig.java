package com.ethlo.http.io;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.MappingMediaTypeFileExtensionResolver;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class MultiFileSystemConfig {
    private final FileSystemProperties fileSystemProperties;

    public MultiFileSystemConfig(FileSystemProperties fileSystemProperties) {
        this.fileSystemProperties = fileSystemProperties;
    }

    @Bean
    public Map<String, LayeredFileSystem> layeredFileSystems() {

        final Map<String, LayeredFileSystem> fileSystems = new HashMap<>();

        if (fileSystemProperties.getDirectories() != null) {
            fileSystemProperties.getDirectories().forEach((key, layers) ->
            {
                List<Path> layerPaths = layers.stream()
                        .map(Path::of)
                        .collect(Collectors.toList());
                fileSystems.put(key, new LayeredFileSystem(layerPaths, fileSystemProperties.getCacheConfig()));
            });
        }
        return fileSystems;
    }

    @Bean
    public MappingMediaTypeFileExtensionResolver mediaTypeMappings() {
        return new MappingMediaTypeFileExtensionResolver(fileSystemProperties.getExtensions());
    }
}
