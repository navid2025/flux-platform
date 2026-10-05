package io.github.navidzare.flux.connector.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client credentials flow with a cached token.
 *
 * <p>The token is fetched from the token endpoint and cached until shortly before it expires,
 * so an in-flight call never picks up a credential that expires mid-request.</p>
 *
 * <p>Settings:</p>
 * <ul>
 *   <li>{@code token-uri} — the token endpoint, required</li>
 *   <li>{@code client-id} — the client identifier, required</li>
 *   <li>{@code client-secret} — the client secret, required</li>
 *   <li>{@code scope} — optional, space delimited</li>
 * </ul>
 */
public class OAuth2Auth implements AuthenticationStrategy {

    public static final String TYPE = "oauth2";

    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofMinutes(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    // Keyed on the settings that produced the token, so two connectors pointed at
    // different identity providers never end up sharing a credential.
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, String> apply(Map<String, String> settings) {
        String key = cacheKey(settings);

        CachedToken token = cache.get(key);
        if (token == null || token.isExpired()) {
            synchronized (cache) {
                token = cache.get(key);
                if (token == null || token.isExpired()) {
                    token = fetch(settings);
                    cache.put(key, token);
                }
            }
        }

        return Map.of("Authorization", "Bearer " + token.value());
    }

    @Override
    public void validate(Map<String, String> settings) {
        if (first(settings, "token-uri", "token-url", "tokenUrl").isBlank()) {
            throw new IllegalArgumentException("oauth2 requires a 'token-uri'");
        }
        if (first(settings, "client-id", "clientId").isBlank()) {
            throw new IllegalArgumentException("oauth2 requires a 'client-id'");
        }
        if (first(settings, "client-secret", "clientSecret").isBlank()) {
            throw new IllegalArgumentException("oauth2 requires a 'client-secret'");
        }
    }

    @Override
    public boolean requiresRefresh() {
        return true;
    }

    /**
     * Calls the token endpoint and returns the credential it issued.
     */
    private CachedToken fetch(Map<String, String> settings) {
        String tokenUri = first(settings, "token-uri", "token-url", "tokenUrl");
        String clientId = first(settings, "client-id", "clientId");
        String clientSecret = first(settings, "client-secret", "clientSecret");

        if (tokenUri.isBlank()) {
            throw new IllegalStateException("oauth2 requires a token-uri");
        }
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("oauth2 requires client-id and client-secret");
        }

        StringBuilder form = new StringBuilder()
                .append("grant_type=client_credentials")
                .append("&client_id=").append(encode(clientId))
                .append("&client_secret=").append(encode(clientSecret));

        String scope = first(settings, "scope");
        if (!scope.isBlank()) {
            form.append("&scope=").append(encode(scope));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "oauth2 token endpoint returned HTTP " + response.statusCode());
            }

            String accessToken = jsonString(response.body(), "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("oauth2 token response carried no access_token");
            }

            long seconds = jsonNumber(response.body(), "expires_in");
            Duration ttl = seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_TTL;
            return new CachedToken(accessToken, Instant.now().plus(ttl));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("oauth2 token request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("oauth2 token request failed: " + e.getMessage(), e);
        }
    }

    private static String cacheKey(Map<String, String> settings) {
        return first(settings, "token-uri", "token-url", "tokenUrl")
                + "|" + first(settings, "client-id", "clientId");
    }

    private static String first(Map<String, String> settings, String... keys) {
        if (settings == null) {
            return "";
        }
        for (String key : keys) {
            String value = settings.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Reads a top-level string field out of a flat JSON object. Small enough to do here
     * rather than pull a parser into the core module.
     */
    private static String jsonString(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) {
            return null;
        }
        int colon = json.indexOf(':', start);
        if (colon < 0) {
            return null;
        }
        int open = json.indexOf('"', colon);
        if (open < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                value.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }

    private static long jsonNumber(String json, String field) {
        int start = json.indexOf("\"" + field + "\"");
        if (start < 0) {
            return 0;
        }
        int colon = json.indexOf(':', start);
        if (colon < 0) {
            return 0;
        }
        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minus(EXPIRY_SAFETY_MARGIN));
        }
    }
}
