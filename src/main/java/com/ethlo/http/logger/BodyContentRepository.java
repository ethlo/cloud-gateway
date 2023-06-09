package com.ethlo.http.logger;

import java.util.Optional;

import org.springframework.core.io.Resource;

public interface BodyContentRepository
{
    void saveRequestBody(final String requestId, Resource requestBody);

    void saveResponseBody(final String requestId, Resource responseBody);

    Optional<Resource> getRequestData(final String requestId);

    Optional<Resource> getResponseData(final String requestId);
}
