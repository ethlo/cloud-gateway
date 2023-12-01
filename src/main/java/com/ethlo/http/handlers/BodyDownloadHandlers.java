package com.ethlo.http.handlers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ethlo.http.logger.BodyContentRepository;

@RefreshScope
@ConditionalOnProperty("features.body-handlers.enabled")
@RestController
public class BodyDownloadHandlers
{
    private final BodyContentRepository bodyContentRepository;

    public BodyDownloadHandlers(final BodyContentRepository bodyContentRepository)
    {
        this.bodyContentRepository = bodyContentRepository;
    }

    @GetMapping(path = "/data/requests/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> getRequestData(@PathVariable("id") final String requestId)
    {
        return sendBinaryData(requestId, bodyContentRepository.getRequestData(requestId).orElseThrow(() -> new EmptyResultDataAccessException("No request body data found for request with ID: " + requestId, 1)));
    }

    @GetMapping(path = "/data/responses/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> getResponseData(@PathVariable("id") final String requestId)
    {
        return sendBinaryData(requestId, bodyContentRepository.getResponseData(requestId).orElseThrow(() -> new EmptyResultDataAccessException("No response body data found for request with ID: " + requestId, 1)));
    }

    private ResponseEntity<Resource> sendBinaryData(String requestId, final Resource data)
    {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData(requestId, requestId);
        return ResponseEntity
                .ok()
                .headers(headers)
                .body(data);
    }
}
