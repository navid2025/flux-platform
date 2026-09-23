package io.github.navidzare.flux.connector;

import java.util.Collections;
import java.util.Map;

/**
 * A single unit of work handed to a {@link Connector}.
 *
 * @param operation the operation to invoke, e.g. {@code getUser} or {@code SELECT_ORDER}
 * @param payload   request body, query parameters or bind variables
 * @param headers   transport level headers such as content type or correlation id
 */
public record ConnectorRequest(
        String operation,
        Map<String, Object> payload,
        Map<String, String> headers) {

    public ConnectorRequest {
        payload = payload == null ? Collections.emptyMap() : Map.copyOf(payload);
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }

    public static ConnectorRequest of(String operation, Map<String, Object> payload) {
        return new ConnectorRequest(operation, payload, Collections.emptyMap());
    }

    public Object param(String name) {
        return payload.get(name);
    }

    public String header(String name) {
        return headers.get(name);
    }
}
