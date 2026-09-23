package io.github.navidzare.flux.connector.auth;

import java.util.Map;

/**
 * Applies credentials to an outbound request.
 *
 * <p>Authentication is deliberately separate from the connector itself. A REST connector
 * behaves the same whether the peer expects basic auth, a bearer token or a signed header,
 * so the strategy is chosen from configuration rather than hard coded.</p>
 */
public interface AuthenticationStrategy {

    /** Identifier used in configuration, e.g. {@code basic} or {@code oauth2}. */
    String type();

    /**
     * Returns the headers to merge into the outbound request.
     *
     * @param settings connector level settings, already resolved from configuration
     */
    Map<String, String> apply(Map<String, String> settings);

    /** Whether this strategy needs to refresh credentials periodically. */
    default boolean requiresRefresh() {
        return false;
    }
}
