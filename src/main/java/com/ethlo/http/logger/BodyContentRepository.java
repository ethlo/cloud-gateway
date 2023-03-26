package com.ethlo.http.logger;

import org.springframework.core.io.Resource;

import java.util.Optional;

public interface BodyContentRepository
{
    Optional<Resource> getRequestData(final String requestId);

    Optional<Resource> getResponseData(final String requestId);
}
