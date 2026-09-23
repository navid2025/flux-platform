package io.github.navidzare.flux.connector.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * HTTP basic authentication. Expects {@code username} and {@code password} in settings.
 */
public class BasicAuth implements AuthenticationStrategy {

    public static final String TYPE = "basic";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, String> apply(Map<String, String> settings) {
        String username = settings.getOrDefault("username", "");
        String password = settings.getOrDefault("password", "");

        String token = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        return Map.of("Authorization", "Basic " + token);
    }
}
