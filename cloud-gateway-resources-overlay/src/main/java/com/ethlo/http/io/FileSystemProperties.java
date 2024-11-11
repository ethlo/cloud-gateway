package com.ethlo.http.io;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
@ConfigurationProperties(prefix = "static-files")
public class FileSystemProperties
{
    private Map<String, List<String>> directories;

    private Map<String, MediaType> extensions = Map.ofEntries(
            Map.entry("txt", MediaType.TEXT_PLAIN),
            Map.entry("html", MediaType.TEXT_HTML),
            Map.entry("css", MediaType.valueOf("text/css")),
            Map.entry("csv", MediaType.valueOf("text/csv")),
            Map.entry("json", MediaType.APPLICATION_JSON),
            Map.entry("xml", MediaType.APPLICATION_XML),
            Map.entry("properties", MediaType.TEXT_PLAIN),
            Map.entry("yaml", MediaType.valueOf("application/yaml")),
            Map.entry("js", MediaType.valueOf("application/javascript")),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("svg", MediaType.valueOf("image/svg+xml")),
            Map.entry("ico", MediaType.valueOf("image/x-icon")),
            Map.entry("zip", MediaType.valueOf("application/zip")),
            Map.entry("jar", MediaType.valueOf("application/jar")),
            Map.entry("tar", MediaType.valueOf("application/x-tar")),
            Map.entry("gz", MediaType.valueOf("application/gzip")),
            Map.entry("mp3", MediaType.valueOf("audio/mpeg")),
            Map.entry("wav", MediaType.valueOf("audio/wav")),
            Map.entry("ogg", MediaType.valueOf("audio/ogg")),
            Map.entry("mp4", MediaType.valueOf("video/mp4")),
            Map.entry("avi", MediaType.valueOf("video/x-msvideo")),
            Map.entry("mpeg", MediaType.valueOf("video/mpeg")),
            Map.entry("webm", MediaType.valueOf("video/webm"))
    );

    private CacheConfig cacheConfig = new CacheConfig(Duration.ofMinutes(10), 1_000);

    public Map<String, List<String>> getDirectories()
    {
        return directories;
    }

    public void setDirectories(Map<String, List<String>> directories)
    {
        this.directories = directories;
    }

    public Map<String, MediaType> getExtensions()
    {
        return extensions;
    }

    public FileSystemProperties setExtensions(final Map<String, MediaType> extensions)
    {
        this.extensions = extensions;
        return this;
    }

    public CacheConfig getCacheConfig()
    {
        return cacheConfig;
    }

    public FileSystemProperties setCacheConfig(final CacheConfig cacheConfig)
    {
        this.cacheConfig = cacheConfig;
        return this;
    }

    public record CacheConfig(Duration duration, int maxSize)
    {
    }
}
