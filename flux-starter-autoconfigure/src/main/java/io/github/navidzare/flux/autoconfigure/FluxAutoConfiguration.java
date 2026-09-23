package io.github.navidzare.flux.autoconfigure;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorProperties;
import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategy;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategyFactory;
import io.github.navidzare.flux.connector.auth.BasicAuth;
import io.github.navidzare.flux.connector.auth.OAuth2Auth;
import io.github.navidzare.flux.connector.types.JdbcConnector;
import io.github.navidzare.flux.connector.types.RestConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Wires every connector declared under {@code flux.connectors} into the application context.
 *
 * <p>Applications get the registry as a bean and never construct connectors themselves.
 * Declaring a connector in YAML is the whole setup step.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ConnectorProperties.class)
public class FluxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FluxAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationStrategyFactory authenticationStrategyFactory(
            List<AuthenticationStrategy> discovered) {
        AuthenticationStrategyFactory factory = new AuthenticationStrategyFactory(discovered);
        log.debug("Authentication strategies available: {}", factory.registeredTypes());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public BasicAuth basicAuth() {
        return new BasicAuth();
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2Auth oauth2Auth() {
        return new OAuth2Auth();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectorRegistry connectorRegistry(ConnectorProperties properties,
                                               AuthenticationStrategyFactory authFactory,
                                               ObjectProvider<JdbcTemplate> jdbcTemplates) {

        ConnectorRegistry registry = new ConnectorRegistry();

        properties.getConnectors().forEach((name, definition) -> {
            if (!definition.isEnabled()) {
                log.info("Connector '{}' is disabled, skipping", name);
                return;
            }
            try {
                Connector connector = build(name, definition, authFactory, jdbcTemplates);
                registry.register(connector);
            } catch (RuntimeException ex) {
                String message = "Failed to initialise connector '%s'".formatted(name);
                if (properties.isFailFast()) {
                    throw new IllegalStateException(message, ex);
                }
                log.error(message, ex);
            }
        });

        log.info("Flux started with {} connector(s): {}", registry.size(), registry.names());
        return registry;
    }

    private Connector build(String name,
                            ConnectorProperties.Definition definition,
                            AuthenticationStrategyFactory authFactory,
                            ObjectProvider<JdbcTemplate> jdbcTemplates) {

        String type = definition.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Connector '%s' has no type".formatted(name));
        }

        return switch (type.toLowerCase()) {
            case RestConnector.TYPE -> new RestConnector(
                    name,
                    definition.getBaseUrl(),
                    definition.getTimeout(),
                    authFactory.resolve(definition.getAuth().getType()).orElse(null),
                    definition.getAuth().getSettings());

            case JdbcConnector.TYPE -> new JdbcConnector(
                    name,
                    jdbcTemplates.getIfAvailable(() -> {
                        throw new IllegalStateException(
                                "Connector '%s' is jdbc but no JdbcTemplate is available"
                                        .formatted(name));
                    }),
                    definition.getSettings());

            default -> throw new IllegalArgumentException(
                    "Unknown connector type '%s' for connector '%s'".formatted(type, name));
        };
    }

    /** Releases every connector when the context shuts down. */
    @Bean
    @ConditionalOnClass(name = "jakarta.annotation.PreDestroy")
    public FluxShutdownHook fluxShutdownHook(ConnectorRegistry registry) {
        return new FluxShutdownHook(registry);
    }

    static class FluxShutdownHook implements AutoCloseable {

        private final ConnectorRegistry registry;

        FluxShutdownHook(ConnectorRegistry registry) {
            this.registry = registry;
        }

        @jakarta.annotation.PreDestroy
        public void shutdown() {
            log.info("Shutting down {} connector(s)", registry.size());
            registry.closeAll();
        }

        @Override
        public void close() {
            shutdown();
        }
    }
}
