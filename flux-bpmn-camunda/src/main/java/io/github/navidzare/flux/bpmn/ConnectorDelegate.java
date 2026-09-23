package io.github.navidzare.flux.bpmn;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets a BPMN service task call any connector registered in Flux.
 *
 * <p>Reference it from a service task with an expression:</p>
 *
 * <pre>
 * &lt;serviceTask id="lookupCustomer"
 *              camunda:delegateExpression="${connectorDelegate}"&gt;
 *   &lt;extensionElements&gt;
 *     &lt;camunda:inputOutput&gt;
 *       &lt;camunda:inputParameter name="connectorName"&gt;customer-api&lt;/camunda:inputParameter&gt;
 *       &lt;camunda:inputParameter name="connectorOperation"&gt;getUser&lt;/camunda:inputParameter&gt;
 *     &lt;/camunda:inputOutput&gt;
 *   &lt;/extensionElements&gt;
 * &lt;/serviceTask&gt;
 * </pre>
 *
 * <p>Input parameters arrive as process variables, so the model stays the only place where
 * a connector is named. Changing which system a process talks to is a model change, not a
 * redeploy of Java code.</p>
 *
 * <p><b>Parameters.</b> Anything prefixed {@code cn_} that is not one of the reserved names
 * is forwarded to the connector. A flow that only needs a few values can therefore skip the
 * parameter map entirely.</p>
 *
 * <p><b>Results.</b> The response is published back as process variables — see
 * {@link DelegateVariables}. A model can branch on {@code connectorSuccess} directly.</p>
 *
 * <p><b>Failures.</b> By default a failed call sets the variables and lets the process
 * continue, so the model decides what to do. Set {@link #setThrowOnFailure(boolean)} to
 * raise a {@link BpmnError} instead and route it through an error boundary event.</p>
 */
public class ConnectorDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(ConnectorDelegate.class);

    private final ConnectorRegistry registry;

    private boolean throwOnFailure;
    private String errorCode = "CONNECTOR_FAILURE";

    public ConnectorDelegate(ConnectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String connectorName = require(execution, DelegateVariables.CONNECTOR, DelegateVariables.CONNECTOR_ALIAS);
        String operation = require(execution, DelegateVariables.OPERATION, DelegateVariables.OPERATION_ALIAS);
        Map<String, Object> params = collectParams(execution);

        String activity = execution.getCurrentActivityId();
        log.debug("Process '{}' task '{}' -> {}.{}",
                execution.getProcessInstanceId(), activity, connectorName, operation);

        Connector connector = registry.require(connectorName);
        ConnectorResponse response = connector.execute(new ConnectorRequest(operation, params, Map.of()));

        publish(execution, response);

        if (!response.success()) {
            log.warn("Connector '{}.{}' failed for process '{}': {}",
                    connectorName, operation, execution.getProcessInstanceId(), response.body());

            if (throwOnFailure) {
                throw new BpmnError(errorCode,
                        "Connector %s.%s failed".formatted(connectorName, operation));
            }
        }
    }

    /** Raise a BPMN error instead of continuing when a call fails. */
    public void setThrowOnFailure(boolean throwOnFailure) {
        this.throwOnFailure = throwOnFailure;
    }

    /** Error code attached to the raised {@link BpmnError}. */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    private void publish(DelegateExecution execution, ConnectorResponse response) {
        execution.setVariable(DelegateVariables.SUCCESS, response.success());
        execution.setVariable(DelegateVariables.STATUS, response.statusCode());
        execution.setVariable(DelegateVariables.DURATION_MS, response.duration().toMillis());

        if (response.body() != null) {
            execution.setVariable(DelegateVariables.DATA, response.body());
        }
        if (!response.success()) {
            execution.setVariable(DelegateVariables.ERROR, String.valueOf(response.body()));
        }
    }

    private String require(DelegateExecution execution, String name, String alias) {
        Object value = execution.getVariable(name);
        if (value == null) {
            value = execution.getVariable(alias);
        }
        if (value == null) {
            throw new IllegalStateException(
                    "BPMN task '%s' is missing the '%s' variable".formatted(
                            execution.getCurrentActivityId(), name));
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> collectParams(DelegateExecution execution) {
        Object explicit = execution.getVariable(DelegateVariables.PARAMS);
        if (explicit instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        Map<String, Object> params = new HashMap<>();
        for (String name : execution.getVariableNames()) {
            if (!name.startsWith(DelegateVariables.PARAM_PREFIX)) {
                continue;
            }
            if (name.equals(DelegateVariables.CONNECTOR_ALIAS)
                    || name.equals(DelegateVariables.OPERATION_ALIAS)) {
                continue;
            }
            params.put(name.substring(DelegateVariables.PARAM_PREFIX.length()),
                    execution.getVariable(name));
        }
        return params;
    }
}
