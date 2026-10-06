package io.github.navidzaare.flux.connector.factory;

import io.github.navidzaare.flux.connector.ConnectorProperties;
import io.github.navidzaare.flux.connector.auth.AuthenticationStrategyFactory;
import io.github.navidzaare.flux.connector.auth.BasicAuth;
import io.github.navidzaare.flux.connector.types.RestConnector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestConnectorFactoryTest {

    private final RestConnectorFactory factory =
            new RestConnectorFactory(new AuthenticationStrategyFactory(List.of(new BasicAuth())));

    @Test
    void buildsARestConnectorFromItsDefinition() {
        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType("rest");
        definition.setBaseUrl("https://api.example.com");

        assertThat(factory.create("customer-api", definition)).isInstanceOf(RestConnector.class);
    }

    @Test
    void refusesAConnectorWithoutABaseUrl() {
        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType("rest");

        assertThatThrownBy(() -> factory.create("customer-api", definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base-url");
    }

    @Test
    void refusesBasicAuthWithoutCredentials() {
        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType("rest");
        definition.setBaseUrl("https://api.example.com");
        definition.getAuth().setType("basic");

        assertThatThrownBy(() -> factory.create("customer-api", definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }
}
