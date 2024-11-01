package com.ethlo.http.io;

import static org.springframework.util.StringUtils.cleanPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.MappingMediaTypeFileExtensionResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${file.static-url-prefix:files}")
public class FileResourceController
{
    private final Map<String, LayeredFileSystem> layeredFileSystems;

    private final MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver;
    private final Path prefixPath;

    public FileResourceController(Map<String, LayeredFileSystem> layeredFileSystems, MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver, @Value("${static-files.url-prefix:files}") final String urlPrefix)
    {
        this.layeredFileSystems = layeredFileSystems;
        this.mediaTypeFileExtensionResolver = mediaTypeFileExtensionResolver;
        this.prefixPath = Path.of(cleanPath("/" + urlPrefix));
    }

    @GetMapping("/{systemKey}/**")
    public Mono<ResponseEntity<?>> getFile(@PathVariable String systemKey, ServerWebExchange exchange)
    {
        return Mono.fromCallable(() ->
        {
            final String requestPath = exchange.getRequest().getURI().getPath();
            sanitizePath(requestPath);
            final Path fullRequestPath = Path.of(cleanPath(requestPath));
            final Path relativePath = Path.of(fullRequestPath.toString().substring(prefixPath.toString().length()));
            final int length = relativePath.getNameCount();
            final Path path = length > 1 ? relativePath.subpath(1, length) : Path.of("");

            final LayeredFileSystem fileSystem = getLayeredFileSystem(systemKey);

            if (Files.isDirectory(fileSystem.getPath(path.toString())))
            {
                try (Stream<Path> paths = fileSystem.list(path))
                {
                    final List<PathListItem> content = paths.map(p ->
                    {
                        final Path fullPath = fileSystem.getPath(path.resolve(p.getFileName()).toString());
                        return PathListItem.of(fullPath);
                    }).toList();

                    return ResponseEntity
                            .status(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new PageImpl<>(content, PageRequest.of(0, content.size()), content.size()));
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
                Mono.just(ResponseEntity.notFound().build())
        );
    }

    private LayeredFileSystem getLayeredFileSystem(String systemKey)
    {
        LayeredFileSystem fileSystem = layeredFileSystems.get(systemKey);
        if (fileSystem == null)
        {
            throw new UncheckedIOException(new IOException("No such path configured: " + systemKey));
        }
        return fileSystem;
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
