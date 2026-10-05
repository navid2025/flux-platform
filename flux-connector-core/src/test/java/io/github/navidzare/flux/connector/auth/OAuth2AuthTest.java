package io.github.navidzare.flux.connector.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthTest {

    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            byte[] body = "{\"access_token\":\"abc123\",\"expires_in\":3600}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String tokenUri() {
        return "http://localhost:" + server.getAddress().getPort() + "/token";
    }

    @Test
    void fetchesATokenAndReusesIt() {
        OAuth2Auth auth = new OAuth2Auth();
        Map<String, String> settings = Map.of(
                "token-uri", tokenUri(),
                "client-id", "svc",
                "client-secret", "secret");

        assertThat(auth.apply(settings)).containsEntry("Authorization", "Bearer abc123");
        assertThat(auth.apply(settings)).containsEntry("Authorization", "Bearer abc123");

        assertThat(tokenCalls).hasValue(1);
    }

    @Test
    void keepsASeparateTokenPerClient() {
        OAuth2Auth auth = new OAuth2Auth();

        auth.apply(Map.of("token-uri", tokenUri(), "client-id", "one", "client-secret", "s"));
        auth.apply(Map.of("token-uri", tokenUri(), "client-id", "two", "client-secret", "s"));

        assertThat(tokenCalls).hasValue(2);
    }
}
