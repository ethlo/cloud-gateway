package com.ethlo.http;

import com.ethlo.http.netty.ServerDirection;
import com.ethlo.http.util.HttpBodyUtil;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class BodyParserTest
{
    @Test
    void parseChunked() throws BodyDecodeException
    {
        final String response = """
                HTTP/1.1 500\s
                Server: nginx
                Date: Tue, 24 Sep 2024 10:20:49 GMT
                Content-Type: application/json
                Transfer-Encoding: chunked
                Connection: keep-alive
                X-Content-Type-Options: nosniff
                X-XSS-Protection: 0
                Cache-Control: no-cache, no-store, max-age=0, must-revalidate
                Pragma: no-cache
                Expires: 0
                X-Frame-Options: DENY
                Strict-Transport-Security: max-age=31536000; includeSubDomains
                                
                35
                {"status":500,"message":"Authentication is required"}
                HTTP/1.1 500\s
                Server: nginx
                Date: Tue, 24 Sep 2024 10:20:49 GMT
                Content-Type: application/json
                Transfer-Encoding: chunked
                Connection: keep-alive
                X-Content-Type-Options: nosniff
                X-XSS-Protection: 0
                Cache-Control: no-cache, no-store, max-age=0, must-revalidate
                Pragma: no-cache
                Expires: 0
                X-Frame-Options: DENY
                Strict-Transport-Security: max-age=31536000; includeSubDomains
                                
                35
                {"status":500,"message":"Authentication is required"}
                HTTP/1.1 500\s
                Server: nginx
                Date: Tue, 24 Sep 2024 10:20:49 GMT
                Content-Type: application/json
                Transfer-Encoding: chunked
                Connection: keep-alive
                X-Content-Type-Options: nosniff
                X-XSS-Protection: 0
                Cache-Control: no-cache, no-store, max-age=0, must-revalidate
                Pragma: no-cache
                Expires: 0
                X-Frame-Options: DENY
                Strict-Transport-Security: max-age=31536000; includeSubDomains
                                
                35
                {"status":500,"message":"Authentication is required"}
                HTTP/1.1 500\s
                Server: nginx
                Date: Tue, 24 Sep 2024 10:20:49 GMT
                Content-Type: application/json
                Transfer-Encoding: chunked
                Connection: keep-alive
                X-Content-Type-Options: nosniff
                X-XSS-Protection: 0
                Cache-Control: no-cache, no-store, max-age=0, must-revalidate
                Pragma: no-cache
                Expires: 0
                X-Frame-Options: DENY
                Strict-Transport-Security: max-age=31536000; includeSubDomains
                                
                35
                {"status":500,"message":"Authentication is required"}
                0""";
        HttpBodyUtil.extractBody(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)), ServerDirection.RESPONSE);
    }
}
