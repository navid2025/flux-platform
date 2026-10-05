package io.github.navidzare.flux.connector.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Registers the Kafka connector type.
 *
 * <p>Adding {@code flux-connector-kafka} to the classpath is the entire setup step. The
 * factory is picked up by {@code FluxAutoConfiguration} and becomes available as
 * {@code type: kafka}.</p>
 */
@AutoConfiguration(afterName = "io.github.navidzare.flux.autoconfigure.FluxAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
public class FluxKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KafkaConnectorFactory.class)
    public KafkaConnectorFactory kafkaConnectorFactory() {
        return new KafkaConnectorFactory();
    }
}
