package com.ethlo.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class UpstreamDownLoggingTest extends BaseTest
{
    private static final String USERNAME = "down-user";
    private static final String REALM = "down-realm";

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry)
    {
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "upstream-down");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + server.getPort());
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0].name", () -> "Path");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0].args.pattern", () -> "/get");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].name", () -> "SetRequestHeader");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.name", () -> "x-realm");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[0].args.value", () -> REALM);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].name", () -> "SetRequestHeader");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.name", () -> "x-unused");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].filters[1].args.value", () -> "1");

        configureClickHouseProperties(registry);
    }

    @Test
    void usernameIsLoggedWhenUpstreamIsDown() throws Exception
    {
        server.stubFor(get(urlPathEqualTo("/get"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        final String credentials = Base64.getEncoder().encodeToString((USERNAME + ":secret-password").getBytes(StandardCharsets.UTF_8));

        final int status = client.get()
                .uri("/get")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .header("x-realm", REALM)
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();

        assertThat(status).isGreaterThanOrEqualTo(500);

        final Optional<String> user = awaitLoggedUser();
        assertThat(user).as("log row for failed request").contains(USERNAME);
    }

    private Optional<String> awaitLoggedUser() throws Exception
    {
        final long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline)
        {
            try (final Connection conn = DriverManager.getConnection(clickHouse().getJdbcUrl(), clickHouse().getUsername(), clickHouse().getPassword());
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery("SELECT user_claim, realm_claim FROM log WHERE route_id = 'upstream-down' AND status >= 500 ORDER BY timestamp DESC LIMIT 1"))
            {
                if (rs.next())
                {
                    assertThat(rs.getString("realm_claim")).isEqualTo(REALM);
                    return Optional.ofNullable(rs.getString("user_claim"));
                }
            }
            catch (SQLException ignored)
            {
                // table or row not ready yet
            }
            Thread.sleep(250);
        }
        return Optional.empty();
    }
}
