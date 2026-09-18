package com.ethlo.http.logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
public class CaptureConfiguration
{
    private static final Logger logger = LoggerFactory.getLogger(CaptureConfiguration.class);
    private static final Duration DEFAULT_ORPHAN_FILE_RETENTION = Duration.ofHours(1);

    private Boolean enabled;
    @NotNull
    private Path logDirectory;
    private Duration orphanFileRetention;

    /**
     * How long a buffer file must have been untouched before the sweeper treats it as orphaned and deletes it. This
     * only affects files left behind by requests that could not be fully logged, as everything else is cleaned up as
     * soon as the request completes. Keep it long enough to inspect such files before they are removed.
     */
    public Duration getOrphanFileRetention()
    {
        return Optional.ofNullable(orphanFileRetention).orElse(DEFAULT_ORPHAN_FILE_RETENTION);
    }

    public void setOrphanFileRetention(final Duration orphanFileRetention)
    {
        this.orphanFileRetention = orphanFileRetention;
    }

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
