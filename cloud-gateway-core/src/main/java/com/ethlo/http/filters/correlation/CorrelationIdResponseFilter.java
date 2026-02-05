package com.ethlo.http.filters.correlation;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdResponseFilter extends OncePerRequestFilter
{

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException
    {

        filterChain.doFilter(request, response);

        String headerName = (String) request.getAttribute(
                CorrelationIdFilterSupplier.ATTR_HEADER_NAME);
        String correlationId = (String) request.getAttribute("correlationId");

        if (headerName != null && correlationId != null && !response.isCommitted())
        {
            response.setHeader(headerName, correlationId);
        }
    }
}
