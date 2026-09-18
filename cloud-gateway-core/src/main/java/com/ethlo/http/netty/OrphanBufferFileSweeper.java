package com.ethlo.http.netty;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ethlo.http.configuration.HttpLoggingConfiguration;

/**
 * Removes buffer files that were left behind because a request could not be fully logged.
 * <p>
 * {@link TagRequestIdGlobalFilter} only cleans up after a request when every logger reported success. A truncated
 * capture, such as an upstream closing the connection mid-response, therefore keeps its request and response files on
 * disk even though the access log entry has already been written. Nothing reads them back, so they would otherwise
 * accumulate until the log directory fills up.
 */
@Component
@ConditionalOnProperty("http-logging.capture.enabled")
public class OrphanBufferFileSweeper
{
    private static final Logger logger = LoggerFactory.getLogger(OrphanBufferFileSweeper.class);

    private final HttpLoggingConfiguration httpLoggingConfiguration;
    private final DataBufferRepository dataBufferRepository;

    public OrphanBufferFileSweeper(final HttpLoggingConfiguration httpLoggingConfiguration, final DataBufferRepository dataBufferRepository)
    {
        this.httpLoggingConfiguration = httpLoggingConfiguration;
        this.dataBufferRepository = dataBufferRepository;
    }

    @Scheduled(fixedDelayString = "${http-logging.capture.orphan-file-sweep-interval:PT10M}")
    public void sweep()
    {
        // Read on every run rather than cached, as the configuration is refreshable
        final Duration retention = httpLoggingConfiguration.getCapture().getOrphanFileRetention();

        final List<Path> deleted = dataBufferRepository.deleteOrphaned(retention);
        if (deleted.isEmpty())
        {
            logger.debug("No orphaned buffer files untouched for more than {}", retention);
        }
        else
        {
            logger.info("Deleted {} orphaned buffer file(s) untouched for more than {}", deleted.size(), retention);
        }
    }
}
