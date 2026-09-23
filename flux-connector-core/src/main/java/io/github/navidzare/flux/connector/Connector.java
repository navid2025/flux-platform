package io.github.navidzare.flux.connector;

import io.github.navidzare.flux.connector.exception.ConnectorException;

/**
 * The single abstraction every outbound integration is built on.
 *
 * <p>A connector knows how to talk to exactly one kind of downstream system — an HTTP API,
 * a database, a queue, a file share. Everything above it (routing, mediation, orchestration)
 * stays unaware of the transport, which is what makes the platform extensible.</p>
 *
 * <p>Implementations must be thread safe. A single instance is shared across all callers.</p>
 */
public interface Connector {

    /** Unique name used to look the connector up at runtime. */
    String name();

    /** Transport family, e.g. {@code rest}, {@code soap}, {@code jdbc}. */
    String type();

    /** Executes one operation against the downstream system. */
    ConnectorResponse execute(ConnectorRequest request) throws ConnectorException;

    /** Whether this connector can serve the given operation. */
    default boolean supports(String operation) {
        return true;
    }

    /**
     * Called after the connector is registered. Implementations may open pools,
     * warm caches or validate that required settings are present.
     */
    default void initialise() {
        // no-op by default
    }

    /** Called on application shutdown so implementations can release resources. */
    default void close() {
        // no-op by default
    }
}
