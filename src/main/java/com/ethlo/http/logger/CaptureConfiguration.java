package com.ethlo.http.logger;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@RefreshScope
@ConfigurationProperties("http-logging.capture")
public class CaptureConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(CaptureConfiguration.class);
    private Boolean enabled;
    private Path tempDirectory;
    private DataSize memoryBufferSize;

    public void setEnabled(final Boolean enabled)
    {
        this.enabled = Optional.ofNullable(enabled).orElse(false);
        if (this.enabled)
        {
            logger.info("Capture is enabled");
        }
        else
        {
            logger.warn("Capture is disabled");
        }
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
        return Optional.ofNullable(enabled).orElse(false);
    }

    public void setTempDirectory(final Path tempDirectory)
    {
        this.tempDirectory = tempDirectory;
    }

    public void setMemoryBufferSize(final DataSize memoryBufferSize)
    {
        this.memoryBufferSize = memoryBufferSize;
    }
}
