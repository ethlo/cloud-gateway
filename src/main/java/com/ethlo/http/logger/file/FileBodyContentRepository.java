package com.ethlo.http.logger.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import com.ethlo.http.logger.BodyContentRepository;
import com.ethlo.http.netty.DataBufferRepository;
import com.ethlo.http.netty.PooledFileDataBufferRepository;

public class FileBodyContentRepository implements BodyContentRepository
{
    private final Path directory;

    public FileBodyContentRepository(final Path directory)
    {
        this.directory = directory;
    }

    @Override
    public Optional<Resource> getRequestData(final String requestId)
    {
        return getFromFile(requestId, DataBufferRepository.Operation.REQUEST);
    }

    @Override
    public Optional<Resource> getResponseData(final String requestId)
    {
        return getFromFile(requestId, DataBufferRepository.Operation.RESPONSE);
    }

    private Optional<Resource> getFromFile(String requestId, final DataBufferRepository.Operation operation)
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
