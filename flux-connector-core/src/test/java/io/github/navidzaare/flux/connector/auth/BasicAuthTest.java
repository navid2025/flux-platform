package io.github.navidzaare.flux.connector.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasicAuthTest {

    @Test
    void encodesTheCredentialsIntoAnAuthorizationHeader() {
        BasicAuth auth = new BasicAuth();

        Map<String, String> headers = auth.apply(Map.of("username", "user", "password", "pass"));

        assertThat(headers).containsEntry("Authorization", "Basic dXNlcjpwYXNz");
    }

    @Test
    void reportsItsType() {
        assertThat(new BasicAuth().type()).isEqualTo("basic");
    }

    @Test
    void refusesSettingsWithoutACredential() {
        BasicAuth auth = new BasicAuth();

        assertThatThrownBy(() -> auth.validate(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> auth.validate(Map.of("username", "user")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void acceptsCompleteCredentials() {
        new BasicAuth().validate(Map.of("username", "user", "password", "pass"));
    }
}
