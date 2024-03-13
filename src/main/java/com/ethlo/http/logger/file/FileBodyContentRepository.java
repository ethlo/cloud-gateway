package com.ethlo.http.logger.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import com.ethlo.http.logger.BodyContentRepository;
import com.ethlo.http.netty.PooledFileDataBufferRepository;
import com.ethlo.http.netty.ServerDirection;

public class FileBodyContentRepository implements BodyContentRepository
{
    private static final Logger logger = LoggerFactory.getLogger(FileBodyContentRepository.class);

    private final Path directory;

    public FileBodyContentRepository(final Path directory)
    {
        logger.info("Body content files stored in directory: {}", directory);
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

    @Override
    public void saveRequest(final String requestId, final Resource requestBody)
    {
        save(requestId, ServerDirection.REQUEST, requestBody);
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

    @Override
    public void saveResponse(final String requestId, final Resource responseBody)
    {
        save(requestId, ServerDirection.RESPONSE, responseBody);
    }

    @Override
    public Optional<Resource> getRequestData(final String requestId)
    {
        return getFromFile(requestId, ServerDirection.REQUEST);
    }

    @Override
    public Optional<Resource> getResponseData(final String requestId)
    {
        return getFromFile(requestId, ServerDirection.RESPONSE);
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
}
