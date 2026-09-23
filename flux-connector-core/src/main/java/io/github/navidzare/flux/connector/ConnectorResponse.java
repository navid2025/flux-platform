package io.github.navidzare.flux.connector;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * The outcome of a {@link Connector} invocation.
 *
 * @param success   whether the downstream call succeeded
 * @param statusCode transport status, or 0 when the connector is not HTTP based
 * @param body      the raw or deserialised response body
 * @param headers   response headers, for example a trace id echoed by the peer
 * @param duration  wall clock time spent inside the connector, useful for metrics
 */
public record ConnectorResponse(
        boolean success,
        int statusCode,
        Object body,
        Map<String, String> headers,
        Duration duration) {

    public ConnectorResponse {
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }

    public static ConnectorResponse ok(Object body, Duration duration) {
        return new ConnectorResponse(true, 200, body, Collections.emptyMap(), duration);
    }

    public static ConnectorResponse failure(int statusCode, String message, Duration duration) {
        return new ConnectorResponse(false, statusCode, message, Collections.emptyMap(), duration);
    }

    @SuppressWarnings("unchecked")
    public <T> T bodyAs(Class<T> type) {
        return (T) body;
    }
}
