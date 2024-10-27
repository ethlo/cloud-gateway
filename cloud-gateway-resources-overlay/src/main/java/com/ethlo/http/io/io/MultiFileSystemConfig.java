package com.ethlo.http.io.io;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiFileSystemConfig
{
    private final FileSystemProperties fileSystemProperties;

    public MultiFileSystemConfig(FileSystemProperties fileSystemProperties)
    {
        this.fileSystemProperties = fileSystemProperties;
    }

    @Bean
    public Map<String, LayeredFileSystem> layeredFileSystems()
    {
        Map<String, LayeredFileSystem> fileSystems = new HashMap<>();

        fileSystemProperties.getLayers().forEach((key, layers) -> {
            List<Path> layerPaths = layers.stream()
                    .map(Path::of)
                    .collect(Collectors.toList());
            fileSystems.put(key, new LayeredFileSystem(layerPaths, Duration.ofMinutes(10)));
        });

        return fileSystems;
    }
}
