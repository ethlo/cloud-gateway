package com.ethlo.http.filters.jwt;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.logstash.logback.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class JwtTokenService
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.create();

    public Mono<DecodedJWT> fetchAccessToken(final String tokenUrl, final String refreshToken, final String clientId, final String clientSecret)
    {
        final Map<String, String> reqBody = new HashMap<>();
        reqBody.put("grant_type", "refresh_token");
        reqBody.put("refresh_token", refreshToken);

        // Add client_id only for public clients (when clientSecret is not present)
        if (StringUtils.isEmpty(clientSecret))
        {
            reqBody.put("client_id", clientId);
        }

        return httpClient
                .headers(headers -> {
                    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);

                    // Use Basic Auth if clientSecret is present
                    if (!StringUtils.isEmpty(clientSecret))
                    {
                        final byte[] authHeader = (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8);
                        final String encodedAuthHeader = Base64.getEncoder().encodeToString(authHeader);
                        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuthHeader);
                    }
                })
                .post()
                .uri(tokenUrl)
                .send((req, out) -> out.sendString(encodeFormData(reqBody)))
                .responseSingle((response, body) -> {
                    if (response.status() == HttpResponseStatus.OK)
                    {
                        return body.asString(StandardCharsets.UTF_8);
                    }
                    else
                    {
                        return body.asString(StandardCharsets.UTF_8)
                                .flatMap(decodedBody -> Mono.error(new TokenFetchException(
                                        "Failed to fetch access token. HTTP Status: " + response.status().code() +
                                                ". Response: " + decodedBody
                                )));
                    }
                })
                .flatMap(responseBody -> {
                    try
                    {
                        // Parse JSON response
                        final JsonNode json = objectMapper.readTree(responseBody);
                        final String accessToken = json.get("access_token").asText();
                        return Mono.just(JWT.decode(accessToken));
                    }
                    catch (Exception e)
                    {
                        return Mono.error(new TokenParseException("Failed to parse access token response", e));
                    }
                });
    }


    private Publisher<? extends String> encodeFormData(Map<String, String> data)
    {
        return Mono.just(data.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }
}
