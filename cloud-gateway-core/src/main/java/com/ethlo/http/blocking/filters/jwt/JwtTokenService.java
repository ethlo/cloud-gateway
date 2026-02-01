package com.ethlo.http.blocking.filters.jwt;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;

public class JwtTokenService
{
    private final RestClient restClient = RestClient.create();

    public DecodedJWT fetchAccessToken(final String tokenUrl, final String refreshToken, final String clientId, final String clientSecret)
    {
        final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        // RestClient is blocking and straightforward
        final JsonNode json = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> {
                    if (clientSecret != null && !clientSecret.isBlank())
                    {
                        headers.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
                    }
                    else
                    {
                        body.add("client_id", clientId);
                    }
                })
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> {
                            throw new TokenFetchException("Failed to fetch access token. HTTP Status: " + response.getStatusCode());
                        }
                )
                .body(JsonNode.class);

        // Pure imperative logic
        if (json != null && json.has("access_token"))
        {
            final String accessToken = json.get("access_token").asText();
            return JWT.decode(accessToken);
        }

        throw new TokenFetchException("Response did not contain an access_token field");
    }
}