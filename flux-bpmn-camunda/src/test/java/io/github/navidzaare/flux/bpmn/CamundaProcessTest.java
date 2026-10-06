package io.github.navidzaare.flux.bpmn;

import io.github.navidzaare.flux.connector.Connector;
import io.github.navidzaare.flux.connector.ConnectorRegistry;
import io.github.navidzaare.flux.connector.ConnectorRequest;
import io.github.navidzaare.flux.connector.ConnectorResponse;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real BPMN process on an embedded Camunda engine and checks that a service
 * task routed through the connector delegate reaches the connector.
 */
class CamundaProcessTest {

    private static final AtomicReference<ConnectorRequest> lastRequest = new AtomicReference<>();
    private static ConnectorRegistry registry;
    private static ProcessEngine engine;

    private static final String PROCESS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              targetNamespace="http://flux.test">
              <bpmn:process id="lookupCustomer" isExecutable="true" camunda:historyTimeToLive="30">
                <bpmn:startEvent id="start">
                  <bpmn:outgoing>flow1</bpmn:outgoing>
                </bpmn:startEvent>
                <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="lookup"/>
                <bpmn:serviceTask id="lookup"
                                  camunda:class="io.github.navidzaare.flux.bpmn.CamundaProcessTest$StubDelegate">
                  <bpmn:extensionElements>
                    <camunda:inputOutput>
                      <camunda:inputParameter name="connectorName">customer-api</camunda:inputParameter>
                      <camunda:inputParameter name="connectorOperation">getUser</camunda:inputParameter>
                      <camunda:inputParameter name="cn_id">7</camunda:inputParameter>
                    </camunda:inputOutput>
                  </bpmn:extensionElements>
                  <bpmn:incoming>flow1</bpmn:incoming>
                  <bpmn:outgoing>flow2</bpmn:outgoing>
                </bpmn:serviceTask>
                <bpmn:sequenceFlow id="flow2" sourceRef="lookup" targetRef="end"/>
                <bpmn:endEvent id="end">
                  <bpmn:incoming>flow2</bpmn:incoming>
                </bpmn:endEvent>
              </bpmn:process>
            </bpmn:definitions>
            """;

    @BeforeAll
    static void startEngine() {
        engine = new StandaloneInMemProcessEngineConfiguration()
                .setProcessEngineName("flux-bpmn-test")
                .setJdbcUrl("jdbc:h2:mem:flux-bpmn;DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .buildProcessEngine();
    }

    @AfterAll
    static void stopEngine() {
        engine.close();
    }

    @BeforeEach
    void setUp() {
        lastRequest.set(null);
        registry = new ConnectorRegistry();
        registry.register(new Connector() {
            @Override
            public String name() {
                return "customer-api";
            }

            @Override
            public String type() {
                return "stub";
            }

            @Override
            public ConnectorResponse execute(ConnectorRequest request) {
                lastRequest.set(request);
                return ConnectorResponse.ok(Map.of("id", 7), Duration.ZERO);
            }
        });
    }

    @Test
    void runsAServiceTaskThroughTheConnectorDelegate() {
        engine.getRepositoryService()
                .createDeployment()
                .addString("lookup-customer.bpmn", PROCESS)
                .deploy();

        ProcessInstance instance = engine.getRuntimeService()
                .startProcessInstanceByKey("lookupCustomer");

        assertThat(instance).isNotNull();
        assertThat(engine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(instance.getId())
                .singleResult()).isNull();

        assertThat(lastRequest.get()).isNotNull();
        assertThat(lastRequest.get().operation()).isEqualTo("getUser");
        assertThat(lastRequest.get().payload()).containsEntry("id", "7");
    }

    /**
     * Camunda instantiates {@code camunda:class} through a no-argument constructor, so the
     * delegate under test is reached through a thin subclass that borrows the test registry.
     */
    public static class StubDelegate extends ConnectorDelegate {

        public StubDelegate() {
            super(registry);
        }
    }
}
