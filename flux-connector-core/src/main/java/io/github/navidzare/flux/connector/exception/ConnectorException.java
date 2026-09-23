package io.github.navidzare.flux.connector.exception;

/**
 * Raised when a connector cannot complete its work. Callers are expected to translate
 * this into their own error model rather than letting it reach the transport layer.
 */
public class ConnectorException extends RuntimeException {

    private final String connectorName;
    private final String operation;

    public ConnectorException(String connectorName, String operation, String message) {
        this(connectorName, operation, message, null);
    }

    public ConnectorException(String connectorName, String operation, String message, Throwable cause) {
        super("[%s:%s] %s".formatted(connectorName, operation, message), cause);
        this.connectorName = connectorName;
        this.operation = operation;
    }

    public String connectorName() {
        return connectorName;
    }

    public String operation() {
        return operation;
    }
}
