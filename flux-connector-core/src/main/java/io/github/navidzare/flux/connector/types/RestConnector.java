package io.github.navidzare.flux.connector.types;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategy;
import io.github.navidzare.flux.connector.exception.ConnectorException;
import io.github.navidzare.flux.connector.support.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls an HTTP/JSON downstream system.
 *
 * <p>Operations map onto a URL suffix, so {@code getUser} against a connector with
 * base URL {@code https://api.example.com} becomes {@code GET https://api.example.com/getUser}.</p>
 *
 * <p>Payload entries are sent as query parameters on GET and as a JSON body otherwise.
 * The method is taken from an {@code X-Flux-Method} request header, which is consumed here
 * and never forwarded to the peer.</p>
 *
 * <p>Redirects are not followed. Replaying a request to a host chosen by the response would
 * hand the credentials to whoever answered first.</p>
 */
public class RestConnector implements Connector {

    public static final String TYPE = "rest";

    private static final String METHOD_HEADER = "X-Flux-Method";
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

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
        this.authSettings = authSettings == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(authSettings));
        if (auth != null) {
            auth.validate(this.authSettings);
        }
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
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
            HttpResponse<InputStream> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            String body;
            try (InputStream stream = response.body()) {
                body = readAtMost(stream, MAX_RESPONSE_BYTES);
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            return new ConnectorResponse(ok, response.statusCode(), body, Map.of(), elapsed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(name, request.operation(), "Interrupted while calling peer", ex);
        } catch (ConnectorException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConnectorException(name, request.operation(), "HTTP call failed", ex);
        }
    }

    private HttpRequest build(ConnectorRequest request) {
        String method = methodOf(request);
        String url = url(request.operation(), method.equals("GET") ? request.payload() : Map.of());

        HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(timeout).uri(URI.create(url));

        request.headers().forEach((header, value) -> {
            if (!METHOD_HEADER.equalsIgnoreCase(header)) {
                builder.header(header, value);
            }
        });
        if (auth != null) {
            auth.apply(authSettings).forEach(builder::header);
        }

        if (request.payload().isEmpty() || method.equals("GET")) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(Json.write(request.payload())))
                .build();
    }

    private String methodOf(ConnectorRequest request) {
        String method = request.header(METHOD_HEADER);
        return method == null || method.isBlank() ? "GET" : method.toUpperCase();
    }

    private String url(String operation, Map<String, Object> query) {
        if (operation == null || operation.isBlank()) {
            throw new ConnectorException(name, operation, "No operation name supplied");
        }
        StringBuilder url = new StringBuilder(baseUrl).append('/').append(encodePathSegment(operation));
        if (!query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                first = false;
                url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
        }
        return url.toString();
    }

    /**
     * Percent-encodes a path segment. A segment of {@code .} or {@code ..} is refused rather
     * than encoded, so an operation can never walk out of the configured path.
     */
    private String encodePathSegment(String operation) {
        if (operation.equals(".") || operation.equals("..")) {
            throw new ConnectorException(name, operation, "Operation name is not a valid path segment");
        }
        StringBuilder out = new StringBuilder();
        for (byte b : operation.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    private static String readAtMost(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            if (out.size() + read > limit) {
                throw new IOException("Response body exceeded " + limit + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
