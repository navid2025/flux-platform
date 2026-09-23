package io.github.navidzare.flux.connector.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an {@link AuthenticationStrategy} from its configured name.
 *
 * <p>Strategies are discovered from the application context, so adding a new one is a
 * matter of declaring a bean — no change to this class or to any connector.</p>
 */
public class AuthenticationStrategyFactory {

    private static final String NONE = "none";

    private final Map<String, AuthenticationStrategy> strategies = new HashMap<>();

    public AuthenticationStrategyFactory(List<AuthenticationStrategy> discovered) {
        discovered.forEach(s -> strategies.put(s.type().toLowerCase(), s));
    }

    /**
     * @return the strategy for {@code type}, or empty when the connector is unauthenticated
     * @throws IllegalArgumentException when a named strategy is not registered
     */
    public Optional<AuthenticationStrategy> resolve(String type) {
        if (type == null || type.isBlank() || NONE.equalsIgnoreCase(type)) {
            return Optional.empty();
        }
        AuthenticationStrategy strategy = strategies.get(type.toLowerCase());
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown authentication strategy '%s'. Registered: %s".formatted(type, strategies.keySet()));
        }
        return Optional.of(strategy);
    }

    /** Registers an additional strategy at runtime. */
    public void add(AuthenticationStrategy strategy) {
        strategies.put(strategy.type().toLowerCase(), strategy);
    }

    public java.util.Set<String> registeredTypes() {
        return java.util.Set.copyOf(strategies.keySet());
    }
}
