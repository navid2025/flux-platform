package io.github.navidzare.flux.connector.kafka;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorFactory;
import io.github.navidzare.flux.connector.ConnectorProperties;

/** Builds {@link KafkaConnector} instances from {@code type: kafka} declarations. */
public class KafkaConnectorFactory implements ConnectorFactory {

    @Override
    public String type() {
        return KafkaConnector.TYPE;
    }

    @Override
    public Connector create(String name, ConnectorProperties.Definition definition) {
        return new KafkaConnector(name, definition.getSettings());
    }
}
