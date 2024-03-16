package com.ethlo.http.logger;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.Optional;

@Validated
@RefreshScope
@ConfigurationProperties("http-logging.capture")
public class CaptureConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(CaptureConfiguration.class);
    private Boolean enabled;
    @NotNull
    private Path logDirectory;

    public Path getLogDirectory()
    {
        return logDirectory;
    }

    public void setLogDirectory(final Path logDirectory)
    {
        this.logDirectory = logDirectory;
    }

    /**
     * Use {@link #setLogDirectory(Path)} instead
     *
     * @param logDirectory The directory to store the logs
     */
    @Deprecated
    public void setTempDirectory(final Path logDirectory)
    {
        this.logDirectory = logDirectory;
    }

    public boolean isEnabled()
    {
        return Optional.ofNullable(enabled).orElse(false);
    }

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
}
