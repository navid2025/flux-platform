package io.github.navidzare.flux.connector.types;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategy;
import io.github.navidzare.flux.connector.exception.ConnectorException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls an HTTP/JSON downstream system.
 *
 * <p>Operations map onto a URL suffix, so {@code getUser} against a connector with
 * base URL {@code https://api.example.com} becomes {@code GET https://api.example.com/getUser}.</p>
 *
 * <p>Payload entries are sent as query parameters on GET and as a JSON body otherwise.</p>
 */
public class RestConnector implements Connector {

    public static final String TYPE = "rest";

    private final String name;
    private final String baseUrl;
    private final Duration timeout;
    private final AuthenticationStrategy auth;
    private final Map<String, String> authSettings;
    private final HttpClient client;

    public RestConnector(String name, String baseUrl, Duration timeout,
                         AuthenticationStrategy auth, Map<String, String> authSettings) {
        this.name = name;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        this.auth = auth;
        this.authSettings = authSettings == null ? Map.of() : Map.copyOf(authSettings);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        long started = System.nanoTime();
        try {
            HttpRequest httpRequest = build(request);
            HttpResponse<String> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ConnectorResponse(ok, response.statusCode(), response.body(), Map.of(), elapsed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(name, request.operation(), "Interrupted while calling peer", ex);
        } catch (Exception ex) {
            throw new ConnectorException(name, request.operation(), "HTTP call failed", ex);
        }
    }

    private HttpRequest build(ConnectorRequest request) {
        String url = baseUrl + "/" + request.operation();
        HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(timeout);

        Map<String, String> headers = new HashMap<>(request.headers());
        if (auth != null) {
            headers.putAll(auth.apply(authSettings));
        }
        headers.forEach(builder::header);

        if (request.payload().isEmpty()) {
            return builder.uri(URI.create(url)).GET().build();
        }

        // On GET the payload becomes query parameters; elsewhere it is the body.
        String method = headers.getOrDefault("X-Flux-Method", "GET").toUpperCase();
        if ("GET".equals(method)) {
            String query = request.payload().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            return builder.uri(URI.create(url + "?" + query)).GET().build();
        }
        return builder.uri(URI.create(url))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(toJson(request.payload())))
                .build();
    }

    /**
     * Minimal JSON writer so the core has no hard dependency on a JSON library.
     * Replace with Jackson when richer payloads are needed.
     */
    private String toJson(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .map(e -> "\"%s\":\"%s\"".formatted(e.getKey(), String.valueOf(e.getValue())))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
