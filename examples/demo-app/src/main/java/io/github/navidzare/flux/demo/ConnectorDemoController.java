package io.github.navidzare.flux.demo;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shows the two things an application ever does with Flux: list the connectors
 * it was given, and invoke one by name.
 *
 * <p>Nothing here knows that {@code orders-db} is a database. Swapping it for an
 * HTTP API is a change to {@code application.yml} and nothing else.</p>
 */
@RestController
@RequestMapping("/demo")
public class ConnectorDemoController {

    private final ConnectorRegistry registry;

    public ConnectorDemoController(ConnectorRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/connectors")
    public Map<String, Object> connectors() {
        return Map.of(
                "count", registry.size(),
                "names", registry.names());
    }

    @GetMapping("/orders")
    public ConnectorResponse ordersByStatus(@RequestParam(defaultValue = "PAID") String status) {
        Connector orders = registry.require("orders-db");
        return orders.execute(ConnectorRequest.of("findByStatus", Map.of("status", status)));
    }

    @GetMapping("/orders/{id}")
    public ConnectorResponse orderById(@PathVariable long id) {
        Connector orders = registry.require("orders-db");
        return orders.execute(ConnectorRequest.of("findById", Map.of("id", id)));
    }
}
