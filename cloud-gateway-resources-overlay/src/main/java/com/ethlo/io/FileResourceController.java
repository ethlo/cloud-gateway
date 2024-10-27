package com.ethlo.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/files")
public class FileResourceController
{

    private final Map<String, LayeredFileSystem> layeredFileSystems;

    public FileResourceController(Map<String, LayeredFileSystem> layeredFileSystems)
    {
        this.layeredFileSystems = layeredFileSystems;
    }

    @GetMapping(value = "/{systemKey}/{filename}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Resource>> getFile(
            @PathVariable String systemKey,
            @PathVariable String filename)
    {
        return Mono.fromCallable(() ->
        {
            LayeredFileSystem fileSystem = getLayeredFileSystem(systemKey);
            sanitizePath(filename);
            try
            {
                Resource resource = fileSystem.find(Path.of(filename))
                        .orElseThrow(() -> new IOException("File not found: " + filename));
                return ResponseEntity.ok(resource);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }).onErrorResume(UncheckedIOException.class, e ->
                Mono.just(ResponseEntity.notFound().build())
        );
    }

    @GetMapping(value = "/{systemKey}/list/{directory:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Stream<String>>> listFiles(
            @PathVariable String systemKey,
            @PathVariable String directory)
    {
        return Mono.fromCallable(() ->
        {
            LayeredFileSystem fileSystem = getLayeredFileSystem(systemKey);
            sanitizePath(directory);
            try (var paths = fileSystem.list(Path.of(directory)))
            {
                return ResponseEntity.ok(paths.map(Path::toString));
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }).onErrorResume(UncheckedIOException.class, e ->
                Mono.just(ResponseEntity.badRequest().body(Stream.of("Error listing files: " + e.getMessage())))
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
        if (path.contains("..") || path.contains("//") || path.contains("\\") || path.contains(":"))
        {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        if (path.length() > 255)
        {
            throw new IllegalArgumentException("Path too long: " + path);
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
