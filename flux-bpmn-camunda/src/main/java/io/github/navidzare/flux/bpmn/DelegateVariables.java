package io.github.navidzare.flux.bpmn;

/**
 * Process variable names Flux reads and writes.
 *
 * <p>Collected in one place so a modeller and a developer are never guessing at a
 * string. The short {@code cn_} aliases exist because they are quicker to type
 * across a large BPMN model.</p>
 */
public final class DelegateVariables {

    private DelegateVariables() {
    }

    // ---- read by the delegate ------------------------------------------------

    /** Which connector to call. Required. */
    public static final String CONNECTOR = "connectorName";
    public static final String CONNECTOR_ALIAS = "cn_connector";

    /** Which operation to invoke on it. Required. */
    public static final String OPERATION = "connectorOperation";
    public static final String OPERATION_ALIAS = "cn_operation";

    /** Optional explicit parameter map. When absent, {@code cn_*} variables are collected. */
    public static final String PARAMS = "connectorParams";

    /** Prefix for parameters passed positionally, e.g. {@code cn_orderId}. */
    public static final String PARAM_PREFIX = "cn_";

    // ---- written by the delegate ---------------------------------------------

    /** Whether the call succeeded. */
    public static final String SUCCESS = "connectorSuccess";

    /** Transport status code, or 0 for non-HTTP connectors. */
    public static final String STATUS = "connectorStatus";

    /** The response body. */
    public static final String DATA = "connectorData";

    /** Error message when the call did not succeed. */
    public static final String ERROR = "connectorError";

    /** How long the call took, in milliseconds. */
    public static final String DURATION_MS = "connectorDurationMs";
}
