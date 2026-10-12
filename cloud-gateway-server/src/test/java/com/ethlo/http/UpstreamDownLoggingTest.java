package com.ethlo.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Optional;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;


class UpstreamDownLoggingTest extends BaseTest
{
    private static final String USERNAME = "down-user";
    private static final String JWT_USERNAME = "jwt-down-user";
    private static final String REALM = "down-realm";

    // Port that was free at startup and has nothing listening on it
    private static final int DEAD_PORT = findFreePort();

    @Autowired
    private WebTestClient client;

    private static int findFreePort()
    {
        try (final ServerSocket socket = new ServerSocket(0))
        {
            return socket.getLocalPort();
        }
        catch (IOException exc)
        {
            throw new IllegalStateException(exc);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry)
    {
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "upstream-down");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].uri", () -> "http://localhost:" + DEAD_PORT);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/get,/jwt");
        registry.add("http-logging.auth.jwt.username-claim-name", () -> "preferred_username");
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

        final Optional<String> user = awaitLoggedUser("/get", REALM);
        assertThat(user).as("log row for failed request").contains(USERNAME);
    }

    @Test
    void jwtUserIsLoggedForPostWhenUpstreamIsDown() throws Exception
    {
        final String token = JWT.create()
                .withIssuer("https://idp.example.com/" + REALM)
                .withClaim("preferred_username", JWT_USERNAME)
                .sign(Algorithm.HMAC256("secret"));

        final int status = client.post()
                .uri("/jwt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue("{\"some\":\"payload\"}")
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();

        assertThat(status).isGreaterThanOrEqualTo(500);

        final Optional<String> user = awaitLoggedUser("/jwt", REALM);
        assertThat(user).as("log row for failed request").contains(JWT_USERNAME);
    }

    private Optional<String> awaitLoggedUser(final String path, final String expectedRealm) throws Exception
    {
        final long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline)
        {
            try (final Connection conn = DriverManager.getConnection(clickHouse().getJdbcUrl(), clickHouse().getUsername(), clickHouse().getPassword());
                 final Statement stmt = conn.createStatement();
                 final ResultSet rs = stmt.executeQuery("SELECT user_claim, realm_claim, matcher_id FROM log WHERE route_id = 'upstream-down' AND path = '" + path + "' AND status >= 500 ORDER BY timestamp DESC LIMIT 1"))
            {
                if (rs.next())
                {
                    assertThat(rs.getString("matcher_id")).isEqualTo("All");
                    assertThat(rs.getString("realm_claim")).isEqualTo(expectedRealm);
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
