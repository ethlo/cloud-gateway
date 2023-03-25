package com.ethlo.http.logger.file;

import java.util.Map;

public interface AccessLogTemplateRenderer
{
    String render(Map<String, Object> data);

    String getPattern();
}
