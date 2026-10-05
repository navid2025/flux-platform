package io.github.navidzare.flux.connector.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
