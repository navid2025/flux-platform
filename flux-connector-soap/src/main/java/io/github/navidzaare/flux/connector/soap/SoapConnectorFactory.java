package io.github.navidzaare.flux.connector.soap;

import io.github.navidzaare.flux.connector.Connector;
import io.github.navidzaare.flux.connector.ConnectorFactory;
import io.github.navidzaare.flux.connector.ConnectorProperties;

/** Builds {@link SoapConnector} instances from {@code type: soap} declarations. */
public class SoapConnectorFactory implements ConnectorFactory {

    @Override
    public String type() {
        return SoapConnector.TYPE;
    }

    @Override
    public Connector create(String name, ConnectorProperties.Definition definition) {
        return new SoapConnector(name, definition);
    }
}
