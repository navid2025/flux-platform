package io.github.navidzare.flux.bpmn;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.exception.ConnectorException;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorDelegateTest {

    private final Map<String, Object> variables = new HashMap<>();
    private final AtomicReference<ConnectorRequest> lastRequest = new AtomicReference<>();

    private ConnectorRegistry registry;
    private ConnectorDelegate delegate;
    private DelegateExecution execution;

    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
        registry.register(recordingConnector("customer-api", ConnectorResponse.ok(Map.of("id", 7), Duration.ZERO)));
        delegate = new ConnectorDelegate(registry);
        execution = execution();
    }

    @Test
    void forwardsConnectorPrefixedVariablesAsParameters() {
        variables.put("connectorName", "customer-api");
        variables.put("connectorOperation", "getUser");
        variables.put("cn_id", 7L);

        delegate.execute(execution);

        assertThat(lastRequest.get().operation()).isEqualTo("getUser");
        assertThat(lastRequest.get().payload()).containsEntry("id", 7L);
    }

    @Test
    void doesNotForwardTheReservedAliasesAsParameters() {
        variables.put("cn_connector", "customer-api");
        variables.put("cn_operation", "getUser");

        delegate.execute(execution);

        assertThat(lastRequest.get().payload()).isEmpty();
    }

    @Test
    void usesAnExplicitParameterMapWhenOneIsSupplied() {
        variables.put("connectorName", "customer-api");
        variables.put("connectorOperation", "getUser");
        variables.put("connectorParams", Map.of("id", 42L));
        variables.put("cn_ignored", "value");

        delegate.execute(execution);

        assertThat(lastRequest.get().payload()).containsOnlyKeys("id");
    }

    @Test
    void publishesTheResponseAsVariables() {
        variables.put("connectorName", "customer-api");
        variables.put("connectorOperation", "getUser");

        delegate.execute(execution);

        assertThat(variables).containsEntry("connectorSuccess", true);
        assertThat(variables).containsEntry("connectorStatus", 200);
        assertThat(variables).containsEntry("connectorData", Map.of("id", 7));
        assertThat(variables.get("connectorDurationMs")).isNotNull();
    }

    @Test
    void failsWhenTheConnectorNameIsMissing() {
        variables.put("connectorOperation", "getUser");

        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connectorName");
    }

    @Test
    void recordsTheFailureAndContinuesByDefault() {
        registry.register(recordingConnector("broken", ConnectorResponse.failure(500, "boom", Duration.ZERO)));
        variables.put("connectorName", "broken");
        variables.put("connectorOperation", "getUser");

        delegate.execute(execution);

        assertThat(variables).containsEntry("connectorSuccess", false);
        assertThat(variables).containsEntry("connectorError", "boom");
    }

    @Test
    void raisesABpmnErrorWhenConfiguredToThrow() {
        registry.register(recordingConnector("broken", ConnectorResponse.failure(500, "boom", Duration.ZERO)));
        delegate.setThrowOnFailure(true);
        variables.put("connectorName", "broken");
        variables.put("connectorOperation", "getUser");

        assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("broken.getUser");
    }

    @Test
    void treatsAThrowingConnectorAsAFailure() {
        registry.register(new Connector() {
            @Override
            public String name() {
                return "kafka";
            }

            @Override
            public String type() {
                return "kafka";
            }

            @Override
            public ConnectorResponse execute(ConnectorRequest request) {
                throw new ConnectorException("kafka", request.operation(), "broker unreachable", null);
            }
        });
        variables.put("connectorName", "kafka");
        variables.put("connectorOperation", "publish");

        delegate.execute(execution);

        assertThat(variables).containsEntry("connectorSuccess", false);
        assertThat((String) variables.get("connectorError")).contains("broker unreachable");
    }

    private Connector recordingConnector(String name, ConnectorResponse response) {
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
                lastRequest.set(request);
                return response;
            }
        };
    }

    private DelegateExecution execution() {
        DelegateExecution mockExecution = mock(DelegateExecution.class);
        when(mockExecution.getVariable(anyString()))
                .thenAnswer(invocation -> variables.get(invocation.getArgument(0)));
        when(mockExecution.getVariableNames())
                .thenAnswer(invocation -> Set.copyOf(variables.keySet()));
        when(mockExecution.getCurrentActivityId()).thenReturn("lookupCustomer");
        when(mockExecution.getProcessInstanceId()).thenReturn("proc-1");
        doAnswer(invocation -> variables.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(mockExecution).setVariable(anyString(), any());
        return mockExecution;
    }
}
