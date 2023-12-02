package com.ethlo.http.configuration;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;

@ConditionalOnProperty(value = "features.config-files-watch.enabled", matchIfMissing = true)
@Component
public class FileConfigurationChangeDetector
{
    private static final Logger logger = LoggerFactory.getLogger(FileConfigurationChangeDetector.class);

    private final WatchService watchService = FileSystems.getDefault().newWatchService();

    private final ApplicationEventPublisher applicationEventPublisher;
    private Thread watchThread;

    public FileConfigurationChangeDetector(final StandardEnvironment environment, final ApplicationEventPublisher applicationEventPublisher) throws IOException, InterruptedException
    {
        this.applicationEventPublisher = applicationEventPublisher;
        final List<Path> paths = new ArrayList<>(getPaths(environment.getProperty("spring.config.location", "")));
        paths.addAll(getPaths(environment.getProperty("spring.config.additional-location", "")));
        setup(paths);
    }

    private void setup(List<Path> paths) throws IOException
    {
        for (Path path : paths)
        {
            // We can only watch directories
            if (Files.isDirectory(path))
            {
                path.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
            }
            else
            {
                // In case of a file, we watch the parent directory
                path.getParent().register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
            }
            logger.info("Watching config location {} for change", path);
        }

        this.watchThread = new Thread(() ->
        {
            boolean poll = true;
            long lastLoaded = 0;
            while (poll)
            {
                final WatchKey key;
                try
                {
                    key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents())
                    {
                        if (System.currentTimeMillis() - lastLoaded > 1_000)
                        {
                            final Path modified = (Path) event.context();
                            logger.info("Triggering refresh due to modification of configuration: {}", modified);
                            applicationEventPublisher.publishEvent(new RefreshEvent(this, "RefreshEvent", "Refreshing scope"));
                            lastLoaded = System.currentTimeMillis();
                        }
                    }
                    poll = key.reset();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        });
        watchThread.start();
    }

    private static List<Path> getPaths(String locations)
    {
        return StringUtils.commaDelimitedListToSet(locations).stream().map(FileSystemResource::new)
                .filter(FileSystemResource::exists)
                .map(FileSystemResource::getFile)
                .map(File::toPath)
                .toList();
    }

    @PreDestroy
    public void destroy()
    {
        this.watchThread = null;
    }
}
