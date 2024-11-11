package com.ethlo.http.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nonnull;

public class LayeredFileSystem extends FileSystem
{
    private static final Logger logger = LoggerFactory.getLogger(LayeredFileSystem.class);
    private final List<Path> layers;
    private final Cache<String, Optional<Path>> pathCache;
    private final WatchService watchService;
    private final Thread watcherThread;
    private volatile boolean open;

    public LayeredFileSystem(final List<Path> layers, final FileSystemProperties.CacheConfig cacheConfig)
    {
        this.layers = layers;
        this.open = true;

        // Create a cache to store file paths based on requests
        this.pathCache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.maxSize())
                .expireAfterWrite(cacheConfig.duration().getSeconds(), TimeUnit.SECONDS)
                .build();

        try
        {
            this.watchService = FileSystems.getDefault().newWatchService();
            initWatchService(layers);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

        // Watcher thread to invalidate cache entries on file changes
        this.watcherThread = new Thread(this::processWatchEvents);
        watcherThread.start();
    }

    private void initWatchService(List<Path> layers) throws IOException
    {
        for (Path layer : layers)
        {
            layer.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }
    }

    private void processWatchEvents()
    {
        while (open)
        {
            try
            {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents())
                {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY)
                    {
                        pathCache.invalidateAll();
                    }
                }
                key.reset();
            }
            catch (ClosedWatchServiceException ignored)
            {

            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void shutdown()
    {
        open = false;
        watcherThread.interrupt();
        try
        {
            watchService.close();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public FileSystemProvider provider()
    {
        return FileSystems.getDefault().provider();
    }

    @Override
    public void close()
    {
        shutdown();
    }

    @Override
    public boolean isOpen()
    {
        return open;
    }

    @Override
    public boolean isReadOnly()
    {
        return false;
    }

    @Override
    public String getSeparator()
    {
        return FileSystems.getDefault().getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories()
    {
        return layers;
    }

    @Override
    public Iterable<FileStore> getFileStores()
    {
        return FileSystems.getDefault().getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews()
    {
        return FileSystems.getDefault().supportedFileAttributeViews();
    }

    @Override
    @Nonnull
    public Path getPath(@Nonnull String first, @Nonnull String... more)
    {
        String path = first;
        if (more.length > 0)
        {
            path = first + getSeparator() + String.join(getSeparator(), more);
        }
        final String fullPath = path;
        return findInLayers(Paths.get(fullPath)).orElseThrow(() -> new UncheckedIOException(new FileNotFoundException("Path not found: " + fullPath)));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern)
    {
        return FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService()
    {
        return FileSystems.getDefault().getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException
    {
        return FileSystems.getDefault().newWatchService();
    }

    /**
     * Returns a merged view of files in all layers for the given directory path.
     */
    public Stream<Path> list(Path path) throws IOException
    {
        final SortedSet<Path> uniqueFiles = new TreeSet<>();
        for (Path layer : layers)
        {
            logger.debug("Searching layer {} for path {}", layer, path);
            Path resolvedLayerPath = layer.resolve(path);
            if (Files.exists(resolvedLayerPath) && Files.isDirectory(resolvedLayerPath))
            {
                try (Stream<Path> files = Files.list(resolvedLayerPath))
                {
                    files.forEach(f ->
                    {
                        final Path relative = layer.relativize(f);
                        logger.debug("Adding {} to the listing", relative);
                        uniqueFiles.add(relative);
                    });
                }
            }
            else
            {
                logger.debug("Layer {} did not have path {} as a directory", layer, path);
            }
        }
        if (uniqueFiles.isEmpty())
        {
            throw new UncheckedIOException(new FileNotFoundException("Error listing files for path: " + path));
        }
        return uniqueFiles.stream();
    }

    /**
     * Finds a file across layers, returning a FileSystemResource if found.
     */
    public Optional<Resource> find(Path path)
    {
        return findInLayers(path).map(FileSystemResource::new);
    }

    private Optional<Path> findInLayers(Path path)
    {
        logger.debug("Attempting to fetch path {}", path);
        final String key = path.toString();
        return pathCache.get(key, k ->
        {
            for (Path layer : layers)
            {
                logger.debug("Checking layer {}", layer);
                Path resolvedPath = layer.resolve(path);
                if (Files.exists(resolvedPath))
                {
                    logger.debug("Path {} was found in layer {}", path, layer);
                    return Optional.of(resolvedPath);
                }
            }
            logger.debug("Path {} not found in any layer", path);
            return Optional.empty();
        });
    }

    @Override
    public String toString()
    {
        return "LayeredFileSystem{" +
                "layers=" + layers +
                '}';
    }
}
