package io.github.navidzare.flux.bpmn;

import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * Base class for delegates that need logic beyond a straight connector call.
 *
 * <p>{@link ConnectorDelegate} covers the common case where a task simply invokes one
 * operation and stores the result. Reach for this class when the task has to reshape
 * parameters, decide between several connectors, or interpret the response before the
 * process continues.</p>
 *
 * <pre>
 * &#64;Component("chargeCustomer")
 * public class ChargeCustomerDelegate extends BaseBpmnDelegate {
 *     &#64;Override
 *     public void execute(DelegateExecution execution) {
 *         long customerId = longVar(execution, "customerId");
 *         ConnectorResponse response = call("billing-api", "charge", Map.of("id", customerId));
 *         execution.setVariable("charged", response.success());
 *     }
 * }
 * </pre>
 */
public abstract class BaseBpmnDelegate implements JavaDelegate {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected ConnectorRegistry connectors;

    /** Invokes a connector and returns its response. */
    protected ConnectorResponse call(String connectorName, String operation, Map<String, Object> params) {
        return connectors.require(connectorName)
                .execute(new ConnectorRequest(operation, params, Map.of()));
    }

    protected String stringVar(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        return value == null ? null : value.toString();
    }

    protected String requireString(DelegateExecution execution, String name) {
        String value = stringVar(execution, name);
        if (value == null) {
            throw new IllegalStateException("Missing required process variable '%s'".formatted(name));
        }
        return value;
    }

    protected Long longVar(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(value.toString());
    }

    protected boolean boolVar(DelegateExecution execution, String name) {
        Object value = execution.getVariable(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    protected <T> T var(DelegateExecution execution, String name) {
        return (T) execution.getVariable(name);
    }

    /** Records a business failure on the process so a gateway can route it. */
    protected void markFailed(DelegateExecution execution, String code, String message) {
        execution.setVariable("fluxFailed", true);
        execution.setVariable("fluxErrorCode", code);
        execution.setVariable("fluxErrorMessage", message);
        log.warn("Process '{}' failed at '{}': {} - {}",
                execution.getProcessInstanceId(), execution.getCurrentActivityId(), code, message);
    }
}
