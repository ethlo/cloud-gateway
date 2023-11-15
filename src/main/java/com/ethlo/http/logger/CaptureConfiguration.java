package com.ethlo.http.logger;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("http-logging.capture")
public class CaptureConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(CaptureConfiguration.class);
    private final boolean enabled;
    private final Path tempDirectory;
    private final DataSize memoryBufferSize;

    public CaptureConfiguration(final boolean enabled, final Path tempDirectory, final DataSize memoryBufferSize)
    {
        if (enabled)
        {
            logger.info("Capture is enabled");
        }
        else
        {
            logger.warn("Capture is disabled");
        }
        this.enabled = enabled;
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

    public boolean isEnabled()
    {
        return enabled;
    }
}
