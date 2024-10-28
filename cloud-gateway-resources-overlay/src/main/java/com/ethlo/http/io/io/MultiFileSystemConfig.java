package com.ethlo.http.io.io;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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

    @Bean
    public MappingMediaTypeFileExtensionResolver mediaTypeMappings()
    {
        return new MappingMediaTypeFileExtensionResolver(Map.ofEntries(
                // Text files
                Map.entry("txt", MediaType.TEXT_PLAIN),
                Map.entry("html", MediaType.TEXT_HTML),
                Map.entry("css", MediaType.valueOf("text/css")),
                Map.entry("csv", MediaType.valueOf("text/csv")),

                // JSON and XML
                Map.entry("json", MediaType.APPLICATION_JSON),
                Map.entry("xml", MediaType.APPLICATION_XML),

                // JavaScript
                Map.entry("js", MediaType.valueOf("application/javascript")),

                // Images
                Map.entry("jpg", MediaType.IMAGE_JPEG),
                Map.entry("jpeg", MediaType.IMAGE_JPEG),
                Map.entry("png", MediaType.IMAGE_PNG),
                Map.entry("gif", MediaType.IMAGE_GIF),
                Map.entry("svg", MediaType.valueOf("image/svg+xml")),
                Map.entry("ico", MediaType.valueOf("image/x-icon")),

                // PDF
                Map.entry("pdf", MediaType.APPLICATION_PDF),

                // Application files
                Map.entry("zip", MediaType.valueOf("application/zip")),
                Map.entry("jar", MediaType.valueOf("application/jar")),
                Map.entry("tar", MediaType.valueOf("application/x-tar")),
                Map.entry("gz", MediaType.valueOf("application/gzip")),
                Map.entry("doc", MediaType.valueOf("application/msword")),
                Map.entry("docx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                Map.entry("xls", MediaType.valueOf("application/vnd.ms-excel")),
                Map.entry("xlsx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                Map.entry("ppt", MediaType.valueOf("application/vnd.ms-powerpoint")),
                Map.entry("pptx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")),

                // Audio files
                Map.entry("mp3", MediaType.valueOf("audio/mpeg")),
                Map.entry("wav", MediaType.valueOf("audio/wav")),
                Map.entry("ogg", MediaType.valueOf("audio/ogg")),

                // Video files
                Map.entry("mp4", MediaType.valueOf("video/mp4")),
                Map.entry("avi", MediaType.valueOf("video/x-msvideo")),
                Map.entry("mpeg", MediaType.valueOf("video/mpeg")),
                Map.entry("webm", MediaType.valueOf("video/webm"))
        ));
    }
}
