package io.github.navidzare.flux.connector;

import io.github.navidzare.flux.connector.exception.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every connector the application has registered and resolves them by name.
 *
 * <p>Mediation flows address downstream systems by logical name — {@code billing-api} —
 * never by host or class. That indirection is what lets a flow move between environments
 * without a code change.</p>
 */
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();

    /** Registers a connector, replacing any previous registration under the same name. */
    public void register(Connector connector) {
        String name = connector.name();
        Connector previous = connectors.put(name, connector);
        if (previous != null) {
            log.warn("Connector '{}' was replaced", name);
            closeQuietly(previous);
        }
        connector.initialise();
        log.info("Registered connector '{}' ({})", name, connector.type());
    }

    /** Removes and closes a connector. */
    public void unregister(String name) {
        Connector removed = connectors.remove(name);
        if (removed != null) {
            closeQuietly(removed);
        }
    }

    public Optional<Connector> find(String name) {
        return Optional.ofNullable(connectors.get(name));
    }

    /** Resolves a connector or fails with a message that names the missing one. */
    public Connector require(String name) {
        Connector connector = connectors.get(name);
        if (connector == null) {
            throw new ConnectorException(name, "*", "No connector registered under this name");
        }
        return connector;
    }

    public Collection<String> names() {
        return Collections.unmodifiableSet(connectors.keySet());
    }

    public int size() {
        return connectors.size();
    }

    /** Closes every connector. Called on application shutdown. */
    public void closeAll() {
        connectors.values().forEach(this::closeQuietly);
        connectors.clear();
    }

    private void closeQuietly(Connector connector) {
        try {
            connector.close();
        } catch (RuntimeException ex) {
            log.warn("Connector '{}' did not shut down cleanly", connector.name(), ex);
        }
    }
}
