package io.github.navidzare.flux.connector.types;

import com.sun.net.httpserver.HttpServer;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.auth.BasicAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestConnectorTest {

    private HttpServer server;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
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

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void sendsTheGetPayloadAsQueryParameters() {
        RestConnector connector = new RestConnector("orders", baseUrl(),
                Duration.ofSeconds(5), null, Map.of());

        ConnectorResponse response = connector.execute(
                ConnectorRequest.of("findByStatus", Map.of("status", "PAID")));

        assertThat(response.success()).isTrue();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastQuery).hasValue("status=PAID");
    }

    @Test
    void attachesTheConfiguredAuthHeader() {
        RestConnector connector = new RestConnector("orders", baseUrl(),
                Duration.ofSeconds(5), new BasicAuth(),
                Map.of("username", "user", "password", "pass"));

        connector.execute(ConnectorRequest.of("getUser", Map.of("id", 1)));

        assertThat(lastAuth.get()).isEqualTo("Basic dXNlcjpwYXNz");
    }
}
