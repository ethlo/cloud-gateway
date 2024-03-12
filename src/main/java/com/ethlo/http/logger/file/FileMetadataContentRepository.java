package com.ethlo.http.logger.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import com.ethlo.http.logger.MetadataContentRepository;
import com.ethlo.http.netty.PooledFileDataBufferRepository;
import com.ethlo.http.netty.ServerDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class FileMetadataContentRepository implements MetadataContentRepository
{
    private static final Logger logger = LoggerFactory.getLogger(FileMetadataContentRepository.class);

    private final Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    ;

    public FileMetadataContentRepository(final Path directory)
    {
        logger.info("Meta content files stored in directory: {}", directory);
        this.directory = directory;
        try
        {
            Files.createDirectories(this.directory);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void save(String requestId, ServerDirection direction, Resource requestBody)
    {
        final Path targetFile = directory.resolve(PooledFileDataBufferRepository.getFilename(directory, direction, requestId));
        try
        {
            Files.copy(requestBody.getInputStream(), targetFile);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Resource> getFromFile(String requestId, final ServerDirection operation)
    {
        final Path candidate = directory.resolve(PooledFileDataBufferRepository.getFilename(directory, operation, requestId));
        try
        {
            return Optional.of(new InputStreamResource(Files.newInputStream(candidate), candidate.toString()));
        }
        catch (IOException e)
        {
            return Optional.empty();
        }
    }

    @Override
    public void save(final String requestId, final Map<String, Object> metamap)
    {
        try
        {
            objectMapper.writeValue(getAbsoluteFile(requestId).toFile(), metamap);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Unable to write metadata file for request ID " + requestId, e);
        }
    }

    private Path getAbsoluteFile(String requestId)
    {
        return directory.resolve(requestId + "_metadata.json");
    }
}
