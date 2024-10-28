package com.ethlo.http.io.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
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
@RequestMapping("/files")
public class FileResourceController
{
    private final Map<String, LayeredFileSystem> layeredFileSystems;

    private final MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver;

    public FileResourceController(Map<String, LayeredFileSystem> layeredFileSystems, MappingMediaTypeFileExtensionResolver mediaTypeFileExtensionResolver)
    {
        this.layeredFileSystems = layeredFileSystems;
        this.mediaTypeFileExtensionResolver = mediaTypeFileExtensionResolver;
    }

    @GetMapping("/{systemKey}/**")
    public Mono<ResponseEntity<?>> getFile(@PathVariable String systemKey, ServerWebExchange exchange)
    {
        return Mono.fromCallable(() ->
        {
            final String filePath = exchange.getRequest().getURI().getPath().split("/files/" + systemKey + "/")[1];
            final LayeredFileSystem fileSystem = getLayeredFileSystem(systemKey);
            sanitizePath(filePath);

            final Path path = fileSystem.getPath(filePath);
            if (Files.isDirectory(path))
            {
                try (Stream<Path> paths = fileSystem.list(Path.of(filePath)))
                {
                    return ResponseEntity
                            .status(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(paths.map(Path::toString).toList());
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
                    final Resource resource = fileSystem.find(path).orElseThrow(() -> new IOException("File not found: " + filePath));
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
            throw new IllegalArgumentException("No such filesystem configured: " + systemKey);
        }
        return fileSystem;
    }

    private void sanitizePath(String path)
    {
        if (path.contains("..") || path.contains("//") || path.contains("\\"))
        {
            throw new IllegalArgumentException("Invalid path: " + path);
        }

        try
        {
            Paths.get(path);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Invalid path format: " + path);
        }
    }
}
