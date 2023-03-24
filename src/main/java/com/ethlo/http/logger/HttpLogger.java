package com.ethlo.http.logger;

import java.io.BufferedInputStream;
import java.util.Map;

public interface HttpLogger
{
    void accessLog(Map<String, Object> data, final BufferedInputStream requestData, final BufferedInputStream responseData);
}
