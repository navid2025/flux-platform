package io.github.navidzare.flux.connector.kafka;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.exception.ConnectorException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Publishes messages to Kafka.
 *
 * <p>The connector resolves its target topic in three steps, first match wins:</p>
 * <ol>
 *   <li>a {@code topic.<operation>} entry in its settings</li>
 *   <li>otherwise the operation name is used as the topic</li>
 *   <li>falling back to {@code default-topic} if one is configured</li>
 * </ol>
 *
 * <pre>
 * flux:
 *   connectors:
 *     orders-out:
 *       type: kafka
 *       settings:
 *         bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
 *         default-topic: flux.orders
 *         topic.publish: orders.created
 * </pre>
 *
 * <p>{@code execute} blocks until the broker acknowledges the write, so a success
 * returned to the caller means the message really was persisted.</p>
 */
public class KafkaConnector implements Connector {

    public static final String TYPE = "kafka";
    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);
    private static final String TOPIC_PREFIX = "topic.";
    private static final String SEND_TIMEOUT_SETTING = "send-timeout";
    private static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final Map<String, String> topicAliases;
    private final String defaultTopic;
    private final Duration sendTimeout;
    private final KafkaTemplate<String, String> template;

    public KafkaConnector(String name, Map<String, String> settings) {
        this.name = name;
        this.topicAliases = settings.entrySet().stream()
                .filter(e -> e.getKey().startsWith(TOPIC_PREFIX))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(TOPIC_PREFIX.length()),
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new));
        this.defaultTopic = settings.get("default-topic");
        this.sendTimeout = parseDuration(settings.get(SEND_TIMEOUT_SETTING), DEFAULT_SEND_TIMEOUT);
        this.template = new KafkaTemplate<>(producerFactory(settings));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        String topic = resolveTopic(request.operation());
        String key = resolveKey(request);
        String value = toJson(request.payload());
        long started = System.nanoTime();

        try {
            var result = template.send(topic, key, value)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .getRecordMetadata();

            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            log.debug("Published to '{}-{}' at offset {} in {} ms",
                    result.topic(), result.partition(), result.offset(), elapsed.toMillis());

            return new ConnectorResponse(true, 200,
                    Map.of("topic", result.topic(),
                            "partition", result.partition(),
                            "offset", result.offset()),
                    Map.of(), elapsed);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(name, request.operation(), "Interrupted while publishing", ex);
        } catch (Exception ex) {
            throw new ConnectorException(name, request.operation(),
                    "Failed to publish to topic '%s'".formatted(topic), ex);
        }
    }

    @Override
    public void close() {
        template.flush();
        template.destroy();
        log.debug("Kafka connector '{}' closed", name);
    }

    private String resolveTopic(String operation) {
        String alias = topicAliases.get(operation);
        if (alias != null) {
            return alias;
        }
        if (defaultTopic != null && !defaultTopic.isBlank() && isGenericOperation(operation)) {
            return defaultTopic;
        }
        return operation;
    }

    /** {@code send} and {@code publish} mean "use the default topic". */
    private boolean isGenericOperation(String operation) {
        return "send".equalsIgnoreCase(operation) || "publish".equalsIgnoreCase(operation);
    }

    private String resolveKey(ConnectorRequest request) {
        Object key = request.payload().get("key");
        if (key != null) {
            return key.toString();
        }
        return request.header("X-Flux-Key");
    }

    private ProducerFactory<String, String> producerFactory(Map<String, String> settings) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                settings.getOrDefault("bootstrap-servers", "localhost:9092"));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Anything prefixed with "producer." is passed straight through, so settings
        // such as acks, compression or idempotence do not need code changes.
        settings.entrySet().stream()
                .filter(e -> e.getKey().startsWith("producer."))
                .forEach(e -> config.put(e.getKey().substring("producer.".length()), e.getValue()));

        return new DefaultKafkaProducerFactory<>(config);
    }

    private Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Duration.parse(value.startsWith("PT") ? value : "PT" + value.toUpperCase());
        } catch (RuntimeException ex) {
            log.warn("Connector '{}' has an unreadable duration '{}', using {}",
                    name, value, fallback);
            return fallback;
        }
    }

    /** Minimal JSON writer, matching the core so the module stays dependency free. */
    private String toJson(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .map(e -> "\"%s\":\"%s\"".formatted(e.getKey(), escape(String.valueOf(e.getValue()))))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
