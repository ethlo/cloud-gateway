package com.ethlo.http.logger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@ConfigurationProperties("logging.capture")
public class CaptureConfiguration
{
    private final Path tempDirectory;
    private final DataSize memoryBufferSize;

    public CaptureConfiguration(final Path tempDirectory, final DataSize memoryBufferSize)
    {
        this.tempDirectory = tempDirectory;
        this.memoryBufferSize = memoryBufferSize;
    }

    public Path getTempDirectory()
    {
        return tempDirectory;
    }

    public DataSize getMemoryBufferSize()
    {
        return memoryBufferSize;
    }
}
