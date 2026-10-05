package io.github.navidzare.flux.connector;

import io.github.navidzare.flux.connector.exception.ConnectorException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorRegistryTest {

    @Test
    void returnsTheConnectorRegisteredUnderAName() {
        ConnectorRegistry registry = new ConnectorRegistry();
        Connector orders = new StubConnector("orders");

        registry.register(orders);

        assertThat(registry.find("orders")).contains(orders);
        assertThat(registry.require("orders")).isSameAs(orders);
        assertThat(registry.names()).containsExactly("orders");
    }

    @Test
    void failsLoudlyWhenANameIsNotRegistered() {
        ConnectorRegistry registry = new ConnectorRegistry();

        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void initialisesAConnectorOnRegistration() {
        ConnectorRegistry registry = new ConnectorRegistry();
        StubConnector stub = new StubConnector("orders");

        registry.register(stub);

        assertThat(stub.initialised).isTrue();
    }

    @Test
    void closesAConnectorWhenItIsReplaced() {
        ConnectorRegistry registry = new ConnectorRegistry();
        StubConnector first = new StubConnector("orders");

        registry.register(first);
        registry.register(new StubConnector("orders"));

        assertThat(first.closed).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void closesEverythingOnShutdown() {
        ConnectorRegistry registry = new ConnectorRegistry();
        StubConnector orders = new StubConnector("orders");
        StubConnector billing = new StubConnector("billing");

        registry.register(orders);
        registry.register(billing);
        registry.closeAll();

        assertThat(orders.closed).isTrue();
        assertThat(billing.closed).isTrue();
    }

    @Test
    void aConnectorIsNotVisibleWhileItIsInitialising() {
        ConnectorRegistry registry = new ConnectorRegistry();
        AtomicBoolean visibleDuringInit = new AtomicBoolean(true);

        registry.register(new Connector() {
            @Override
            public String name() {
                return "orders";
            }

            @Override
            public String type() {
                return "stub";
            }

            @Override
            public ConnectorResponse execute(ConnectorRequest request) {
                return ConnectorResponse.ok(Map.of(), Duration.ZERO);
            }

            @Override
            public void initialise() {
                visibleDuringInit.set(registry.find("orders").isPresent());
            }
        });

        assertThat(visibleDuringInit).isFalse();
        assertThat(registry.names()).containsExactly("orders");
    }

    @Test
    void aConnectorThatFailsToInitialiseIsNotRegistered() {
        ConnectorRegistry registry = new ConnectorRegistry();

        assertThatThrownBy(() -> registry.register(failing("broken")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.names()).isEmpty();
    }

    @Test
    void aFailedRegistrationLeavesThePreviousConnectorInPlace() {
        ConnectorRegistry registry = new ConnectorRegistry();
        StubConnector original = new StubConnector("orders");
        registry.register(original);

        assertThatThrownBy(() -> registry.register(failing("orders")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.require("orders")).isSameAs(original);
    }

    @Test
    void namesIsASnapshotTakenWhenItIsCalled() {
        ConnectorRegistry registry = new ConnectorRegistry();
        registry.register(new StubConnector("orders"));

        Collection<String> names = registry.names();
        registry.register(new StubConnector("billing"));

        assertThat(names).containsExactly("orders");
        assertThat(registry.names()).containsExactlyInAnyOrder("orders", "billing");
    }

    private static Connector failing(String name) {
        return new Connector() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String type() {
                return "stub";
            }

            @Override
            public ConnectorResponse execute(ConnectorRequest request) {
                return ConnectorResponse.ok(Map.of(), Duration.ZERO);
            }

            @Override
            public void initialise() {
                throw new IllegalStateException("cannot start");
            }
        };
    }

    private static final class StubConnector implements Connector {

        private final String name;
        final AtomicBoolean initialised = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();

        StubConnector(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String type() {
            return "stub";
        }

        @Override
        public ConnectorResponse execute(ConnectorRequest request) {
            return ConnectorResponse.ok(Map.of(), Duration.ZERO);
        }

        @Override
        public void initialise() {
            initialised.set(true);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
