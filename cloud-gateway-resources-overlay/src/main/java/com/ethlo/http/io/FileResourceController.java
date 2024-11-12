package com.ethlo.http.io;

import static org.springframework.util.StringUtils.cleanPath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.MappingMediaTypeFileExtensionResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${static-files.url-prefix:files}")
public class FileResourceController
{
    private static final Logger logger = LoggerFactory.getLogger(FileResourceController.class);
    private final Map<Path, LayeredFileSystem> layeredFileSystems;
    private final FileSystemProperties fileSystemProperties;
    private final MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver;
    private final Path prefixPath;

    public FileResourceController(FileSystemProperties fileSystemProperties, MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver)
    {
        this.fileSystemProperties = fileSystemProperties;
        this.mediaTypeFileExtensionResolver = mediaTypeFileExtensionResolver;
        this.prefixPath = Path.of(cleanPath("/" + fileSystemProperties.getUrlPrefix()));

        this.layeredFileSystems = init();

        logger.info("Initializing static resource controller with prefix {}", this.prefixPath);
        logger.debug("File system layers: {}", layeredFileSystems);
        logger.debug("Media types registered: {}", this.mediaTypeFileExtensionResolver.getMediaTypes());
    }

    private static ResponseEntity<Page<PathListItem>> getPage(List<PathListItem> content)
    {
        return ResponseEntity
                .status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PageImpl<>(content, PageRequest.of(0, Math.max(1, content.size())), content.size()));
    }

    private Map<Path, LayeredFileSystem> init()
    {
        final Map<Path, LayeredFileSystem> fileSystems = new HashMap<>();

        if (fileSystemProperties.getDirectories() != null)
        {
            fileSystemProperties.getDirectories().forEach((k, layers) ->
            {
                final List<Path> layerPaths = layers.stream()
                        .map(Path::of)
                        .collect(Collectors.toList());
                final Path key = Path.of(cleanPath("/" + k));
                fileSystems.put(key, new LayeredFileSystem(layerPaths, fileSystemProperties.getCacheConfig()));
            });
        }
        return fileSystems;
    }

    @GetMapping("/**")
    public Mono<ResponseEntity<?>> getFile(ServerWebExchange exchange)
    {
        return Mono.fromCallable(() ->
        {
            final String requestPath = exchange.getRequest().getURI().getPath();
            sanitizePath(requestPath);
            final String fullRequestPath = cleanPath(requestPath);
            final String relativePath = fullRequestPath.substring(prefixPath.toString().length());

            if (relativePath.isEmpty())
            {
                if (fileSystemProperties.isAllowRootListing())
                {
                    return getPage(layeredFileSystems.keySet()
                            .stream()
                            .map(r -> new PathListItem(PathListItem.PathType.ROOT, r.toString(), 0L)).toList());
                }
                else
                {
                    final ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
                    pd.setDetail("Directory listing prohibited");
                    final ResponseEntity.HeadersBuilder<?> res = ResponseEntity.of(pd);
                    return res.build();
                }
            }

            final Map.Entry<Path, LayeredFileSystem> fileSystemEntry = getFileSystem(relativePath);
            final Path systemKey = fileSystemEntry.getKey();
            final LayeredFileSystem fileSystem = fileSystemEntry.getValue();
            final Path path = Path.of("/").relativize(Path.of(relativePath.substring(systemKey.toString().length())));

            final Path fileSystemPath = fileSystem.getPath(path.toString());
            if (Files.isDirectory(fileSystemPath))
            {
                try (Stream<Path> paths = fileSystem.list(path))
                {
                    final List<PathListItem> content = paths.map(p ->
                    {
                        final Path fullPath = fileSystem.getPath(path.resolve(p.getFileName()).toString());
                        return PathListItem.of(fullPath);
                    }).toList();

                    return getPage(content);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
            else
            {
                try
                {
                    final Resource resource = fileSystem.find(fileSystem.getPath(path.toString())).orElseThrow(() -> new IOException("File not found: " + path));
                    final String extension = FilenameUtils.getExtension(path.getFileName().toString().toLowerCase(Locale.ENGLISH));
                    final MediaType mediaType = mediaTypeFileExtensionResolver.getMediaTypes().getOrDefault(extension, MediaType.parseMediaType("application/octet-stream"));
                    return ResponseEntity
                            .status(HttpStatus.OK)
                            .contentType(mediaType)
                            .body(resource);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            }
        }).onErrorResume(UncheckedIOException.class, e ->
                {
                    final ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
                    pd.setDetail(e.getMessage());
                    final ResponseEntity.HeadersBuilder<?> res = ResponseEntity.of(pd);
                    return Mono.just(res.build());
                }
        );
    }

    private Map.Entry<Path, LayeredFileSystem> getFileSystem(String relativePath)
    {
        return layeredFileSystems.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().toString().length(), a.getKey().toString().length()))
                .filter(entry ->
                {
                    final Path relPath = Path.of(cleanPath(relativePath));
                    return relPath.startsWith(entry.getKey());
                })
                .findFirst().orElseThrow(() -> new UncheckedIOException(new FileNotFoundException("No such path configured: " + relativePath)));
    }

    private void sanitizePath(String path)
    {
        if (path.contains("..") || path.contains("//") || path.contains("\\"))
        {
            throw new UncheckedIOException(new IOException("Invalid path: " + path));
        }

        try
        {
            Paths.get(path);
        }
        catch (InvalidPathException e)
        {
            throw new UncheckedIOException(new IOException("Invalid path format: " + path));
        }
    }
}
