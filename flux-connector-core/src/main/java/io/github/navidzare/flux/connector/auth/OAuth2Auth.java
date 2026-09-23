package io.github.navidzare.flux.connector.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client credentials flow with a cached token.
 *
 * <p>The token is refreshed a minute before it expires so an in-flight call never picks up
 * a credential that expires mid-request.</p>
 *
 * <p>This is the extension point for a real implementation: swap {@link #fetchToken} for a
 * call to the configured token endpoint.</p>
 */
public class OAuth2Auth implements AuthenticationStrategy {

    public static final String TYPE = "oauth2";
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofMinutes(1);

    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, String> apply(Map<String, String> settings) {
        CachedToken current = cache.get();
        if (current == null || current.isExpired()) {
            current = new CachedToken(fetchToken(settings), Instant.now().plus(Duration.ofHours(1)));
            cache.set(current);
        }
        return Map.of("Authorization", "Bearer " + current.value());
    }

    @Override
    public boolean requiresRefresh() {
        return true;
    }

    /**
     * Obtains a new access token. Override or replace this class with a real client.
     */
    protected String fetchToken(Map<String, String> settings) {
        String clientId = settings.getOrDefault("clientId", "");
        String clientSecret = settings.getOrDefault("clientSecret", "");
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("oauth2 requires clientId and clientSecret");
        }
        throw new UnsupportedOperationException(
                "Wire OAuth2Auth.fetchToken to the token endpoint of your identity provider");
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minus(EXPIRY_SAFETY_MARGIN));
        }
    }
}
