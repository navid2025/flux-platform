package io.github.navidzaare.flux.connector.types;

import com.sun.net.httpserver.HttpServer;
import io.github.navidzaare.flux.connector.ConnectorRequest;
import io.github.navidzaare.flux.connector.ConnectorResponse;
import io.github.navidzaare.flux.connector.auth.BasicAuth;
import io.github.navidzaare.flux.connector.exception.ConnectorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestConnectorTest {

    private HttpServer server;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));

            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(), String.join(",", values)));
            lastHeaders.set(headers);

            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl() + "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
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

    private RestConnector connector() {
        return new RestConnector("orders", baseUrl(), Duration.ofSeconds(5), null, Map.of());
    }

    @Test
    void sendsTheGetPayloadAsQueryParameters() {
        ConnectorResponse response = connector()
                .execute(ConnectorRequest.of("findByStatus", Map.of("status", "PAID")));

        assertThat(response.success()).isTrue();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastQuery).hasValue("status=PAID");
    }

    @Test
    void encodesQueryValuesSoTheyCannotBreakTheUrl() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("q", "a&b=c");
        payload.put("name", "John Doe");

        connector().execute(ConnectorRequest.of("search", payload));

        assertThat(lastQuery.get())
                .contains("q=a%26b%3Dc")
                .contains("name=John+Doe");
    }

    @Test
    void attachesTheConfiguredAuthHeader() {
        RestConnector connector = new RestConnector("orders", baseUrl(),
                Duration.ofSeconds(5), new BasicAuth(),
                Map.of("username", "user", "password", "pass"));

        connector.execute(ConnectorRequest.of("getUser", Map.of("id", 1)));

        assertThat(lastAuth.get()).isEqualTo("Basic dXNlcjpwYXNz");
    }

    @Test
    void writesAJsonBodyForNonGetMethods() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 1);
        payload.put("name", "Ada");

        connector().execute(new ConnectorRequest("createUser", payload, Map.of("X-Flux-Method", "POST")));

        assertThat(lastMethod.get()).isEqualTo("POST");
        // Map.copyOf does not preserve order, so assert on content rather than the exact text.
        assertThat(lastBody.get())
                .startsWith("{").endsWith("}")
                .contains("\"id\":1")
                .contains("\"name\":\"Ada\"");
    }

    @Test
    void doesNotForwardTheMethodHeaderToThePeer() {
        connector().execute(new ConnectorRequest("getUser", Map.of("id", 1),
                Map.of("X-Flux-Method", "GET")));

        assertThat(lastHeaders.get()).doesNotContainKey("x-flux-method");
    }

    @Test
    void refusesAnOperationThatWouldEscapeThePath() {
        assertThatThrownBy(() -> connector().execute(ConnectorRequest.of("..", Map.of())))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("path segment");
    }

    @Test
    void doesNotFollowRedirects() {
        ConnectorResponse response = connector().execute(ConnectorRequest.of("moved", Map.of()));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.success()).isFalse();
    }
}
