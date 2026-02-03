package com.ethlo.http.configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;

@ConditionalOnBean(FileConfigurationChangeDetectorConfiguration.class)
@Component
public class FileConfigurationChangeDetector
{
    private static final Logger logger = LoggerFactory.getLogger(FileConfigurationChangeDetector.class);

    private final FileAlterationListenerAdaptor listener;
    private final FileAlterationMonitor monitor;

    public FileConfigurationChangeDetector(final StandardEnvironment environment, final ApplicationEventPublisher applicationEventPublisher, final FileConfigurationChangeDetectorConfiguration config) throws Exception
    {
        if (!config.enabled())
        {
            logger.info("Configuration file watcher is disabled");
        }

        logger.info("Starting configuration file watcher");
        this.listener = new FileAlterationListenerAdaptor()
        {
            @Override
            public void onFileChange(File file)
            {
                logger.info("Triggering refresh due to modification of configuration: {}", file);
                applicationEventPublisher.publishEvent(new RefreshEvent(this, "RefreshEvent", "Refreshing scope"));
            }
        };

        this.monitor = new FileAlterationMonitor(config.interval().toMillis());
        final List<Path> paths = new ArrayList<>(getPaths(environment.getProperty("spring.config.location", "")));
        paths.addAll(getPaths(environment.getProperty("spring.config.additional-location", "")));
        for (Path path : paths)
        {
            final FileAlterationObserver observer = new FileAlterationObserver(path.toFile().isDirectory() ? path.toFile() : path.getParent().toFile(), FileFilterUtils.nameFileFilter(path.getFileName().toString()));
            observer.addListener(listener);
            monitor.addObserver(observer);
            logger.info("Watching config location {} for change", path);
        }
        monitor.start();
    }

    private static List<Path> getPaths(String locations)
    {
        return StringUtils.commaDelimitedListToSet(locations).stream().map(FileSystemResource::new)
                .peek(location -> logger.debug("Checking location: {}   ", location))
                .filter(FileSystemResource::exists)
                .map(FileSystemResource::getFile)
                .map(File::toPath)
                .toList();
    }

    @PreDestroy
    public void destroy() throws Exception
    {
        monitor.stop();
    }
}
