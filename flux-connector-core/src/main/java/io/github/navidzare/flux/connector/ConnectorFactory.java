package io.github.navidzare.flux.connector;

/**
 * Creates connectors of one type from configuration.
 *
 * <p>This is the platform's extension point. A module that ships a new kind of connector
 * declares a factory bean, and the auto-configuration picks it up. Nothing in the core
 * has to be edited, and no switch statement has to grow.</p>
 *
 * <pre>
 * &#64;Bean
 * public ConnectorFactory amqpConnectorFactory() {
 *     return new ConnectorFactory() {
 *         public String type() { return "amqp"; }
 *         public Connector create(String name, ConnectorProperties.Definition def) {
 *             return new AmqpConnector(name, def.getSettings());
 *         }
 *     };
 * }
 * </pre>
 */
public interface ConnectorFactory {

    /** The value that selects this factory in {@code flux.connectors.<name>.type}. */
    String type();

    /**
     * Builds a connector. The definition has already been bound from configuration.
     *
     * @param name       the logical name under {@code flux.connectors}
     * @param definition the connector's configuration, never null
     */
    Connector create(String name, ConnectorProperties.Definition definition);
}
