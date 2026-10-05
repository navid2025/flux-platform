package io.github.navidzare.flux.connector.factory;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorFactory;
import io.github.navidzare.flux.connector.ConnectorProperties;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategyFactory;
import io.github.navidzare.flux.connector.types.RestConnector;

/** Builds {@link RestConnector} instances from {@code type: rest} declarations. */
public class RestConnectorFactory implements ConnectorFactory {

    private final AuthenticationStrategyFactory authentication;

    public RestConnectorFactory(AuthenticationStrategyFactory authentication) {
        this.authentication = authentication;
    }

    @Override
    public String type() {
        return RestConnector.TYPE;
    }

    @Override
    public Connector create(String name, ConnectorProperties.Definition definition) {
        String baseUrl = definition.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Connector '%s' is type 'rest' and needs a base-url".formatted(name));
        }
        return new RestConnector(
                name,
                baseUrl,
                definition.getTimeout(),
                authentication.resolve(definition.getAuth().getType()).orElse(null),
                definition.getAuth().getSettings());
    }
}
