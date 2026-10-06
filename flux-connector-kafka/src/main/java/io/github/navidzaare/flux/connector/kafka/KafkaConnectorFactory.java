package io.github.navidzaare.flux.connector.kafka;

import io.github.navidzaare.flux.connector.Connector;
import io.github.navidzaare.flux.connector.ConnectorFactory;
import io.github.navidzaare.flux.connector.ConnectorProperties;

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
