package com.ethlo.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import io.netty.channel.ConnectTimeoutException;

@ControllerAdvice
public class ExtraErrorHandlers
{
    @ExceptionHandler(ConnectTimeoutException.class)
    public ProblemDetail handleConnectTimeoutException()
    {
        final ProblemDetail problemDetails = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        problemDetails.setTitle("Upstream service is not available");
        problemDetails.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        return problemDetails;
    }
}
