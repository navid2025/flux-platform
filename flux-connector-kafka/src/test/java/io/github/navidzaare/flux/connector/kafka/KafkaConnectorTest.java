package io.github.navidzaare.flux.connector.kafka;

import io.github.navidzaare.flux.connector.ConnectorRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConnectorTest {

    @Test
    void aTopicAliasWins() {
        KafkaConnector connector = connector(Map.of(
                "default-topic", "flux.orders",
                "topic.publish", "orders.created"));

        assertThat(connector.resolveTopic("publish")).isEqualTo("orders.created");
    }

    @Test
    void aGenericOperationFallsBackToTheDefaultTopic() {
        KafkaConnector connector = connector(Map.of("default-topic", "flux.orders"));

        assertThat(connector.resolveTopic("publish")).isEqualTo("flux.orders");
        assertThat(connector.resolveTopic("send")).isEqualTo("flux.orders");
    }

    @Test
    void anyOtherOperationNamesTheTopic() {
        KafkaConnector connector = connector(Map.of("default-topic", "flux.orders"));

        assertThat(connector.resolveTopic("orders.created")).isEqualTo("orders.created");
    }

    @Test
    void withoutADefaultTopicTheOperationIsTheTopic() {
        assertThat(connector(Map.of()).resolveTopic("publish")).isEqualTo("publish");
    }

    @Test
    void theMessageKeyComesFromThePayloadThenTheHeader() {
        KafkaConnector connector = connector(Map.of());

        assertThat(connector.resolveKey(ConnectorRequest.of("publish", Map.of("key", "abc"))))
                .isEqualTo("abc");
        assertThat(connector.resolveKey(
                new ConnectorRequest("publish", Map.of(), Map.of("X-Flux-Key", "xyz"))))
                .isEqualTo("xyz");
    }

    @Test
    void closingTwiceIsHarmless() {
        KafkaConnector connector = connector(Map.of());

        connector.close();
        connector.close();
    }

    private KafkaConnector connector(Map<String, String> settings) {
        return new KafkaConnector("orders-out", settings);
    }
}
