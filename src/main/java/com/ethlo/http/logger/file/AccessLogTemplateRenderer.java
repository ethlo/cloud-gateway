package com.ethlo.http.accesslog;

import java.util.Map;

public interface AccessLogTemplateRenderer
{
    String render(Map<String, Object> data);
}
