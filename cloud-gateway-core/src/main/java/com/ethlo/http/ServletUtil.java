package com.ethlo.http;

import java.util.ArrayList;
import java.util.Collections;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ServletUtil
{
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
}
