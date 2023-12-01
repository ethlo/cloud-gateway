package com.ethlo.http.logger;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.unit.DataSize;

@RefreshScope
@ConfigurationProperties("http-logging.capture")
public class CaptureConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(CaptureConfiguration.class);
    private final Boolean enabled;
    private final Path tempDirectory;
    private final DataSize memoryBufferSize;

    public CaptureConfiguration(final Boolean enabled, final Path tempDirectory, final DataSize memoryBufferSize)
    {
        this.enabled = Optional.ofNullable(enabled).orElse(true);
        if (this.enabled)
        {
            logger.info("Capture is enabled");
        }
        else
        {
            logger.warn("Capture is disabled");
        }
        
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
