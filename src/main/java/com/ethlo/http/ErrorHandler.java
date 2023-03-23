package com.ethlo.http;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ErrorHandler extends DefaultErrorAttributes
{
    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options)
    {
        final Map<String, Object> errorAttributes = super.getErrorAttributes(request, options);
        final Throwable error = getError(request);
        final MergedAnnotation<ResponseStatus> responseStatusAnnotation = MergedAnnotations.from(error.getClass(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).get(ResponseStatus.class);
        final Map.Entry<? extends HttpStatusCode, String> errorStatus = getHttpStatus(error, responseStatusAnnotation);
        errorAttributes.put("status", errorStatus.getKey().value());
        errorAttributes.put("error", errorStatus.getValue());
        return errorAttributes;
    }

    private Map.Entry<? extends HttpStatusCode, String> getHttpStatus(Throwable error, MergedAnnotation<ResponseStatus> responseStatusAnnotation)
    {
        if (error instanceof ResponseStatusException responseStatusException)
        {
            return new AbstractMap.SimpleImmutableEntry<>(responseStatusException.getStatusCode(), responseStatusException.getReason());
        }

        final Optional<HttpStatus> status = responseStatusAnnotation.getValue("code", HttpStatus.class);

        return status.map(httpStatus -> new AbstractMap.SimpleImmutableEntry<>(httpStatus, httpStatus.getReasonPhrase())).orElseGet(() ->
        {
            if (error instanceof java.net.ConnectException)
            {
                return new AbstractMap.SimpleImmutableEntry<>(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
            }
            return new AbstractMap.SimpleImmutableEntry<>(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        });
    }
}
