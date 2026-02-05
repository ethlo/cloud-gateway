package com.ethlo.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.ethlo.http.logger.RedactUtil;
import com.ethlo.http.match.HeaderPredicate;

import com.ethlo.http.match.HeaderProcessing;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static com.ethlo.http.match.HeaderProcessing.DELETE;
import static com.ethlo.http.match.HeaderProcessing.REDACT;

public class ServletUtil
{
    public static final Set<String> untouchableHeaders = Set.of(HttpHeaders.CONTENT_ENCODING.toLowerCase(), HttpHeaders.CONTENT_TYPE.toLowerCase(), HttpHeaders.CONTENT_LENGTH.toLowerCase());

    public static HttpHeaders extractHeaders(HttpServletRequest request)
    {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.addAll(name, Collections.list(request.getHeaders(name))));
        return headers;
    }

    public static HttpHeaders extractHeaders(HttpServletResponse response)
    {
        HttpHeaders headers = new HttpHeaders();
        response.getHeaderNames().forEach(name -> headers.addAll(name, new ArrayList<>(response.getHeaders(name))));
        return headers;
    }

    public static HttpHeaders sanitizeHeaders(HeaderPredicate headerPredicate, HttpHeaders httpHeaders)
    {
        processHeader(REDACT, httpHeaders, HttpHeaders.AUTHORIZATION);
        httpHeaders.headerNames().forEach(name -> processHeader(headerPredicate.apply(name), httpHeaders, name));
        return httpHeaders;
    }

    private static void processHeader(HeaderProcessing processing, HttpHeaders headers, String name)
    {
        if (untouchableHeaders.contains(name.toLowerCase()))
        {
            return;
        }

        if (processing == DELETE)
        {
            headers.remove(name);
        }
        else if (processing == REDACT)
        {
            final List<String> values = headers.get(name);
            if (values != null)
            {
                headers.put(name, RedactUtil.redactAll(values));
            }
        }
    }
}
