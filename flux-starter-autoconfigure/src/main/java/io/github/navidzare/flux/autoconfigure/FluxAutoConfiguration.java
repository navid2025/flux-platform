package io.github.navidzare.flux.autoconfigure;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorFactory;
import io.github.navidzare.flux.connector.ConnectorProperties;
import io.github.navidzare.flux.connector.ConnectorRegistry;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategy;
import io.github.navidzare.flux.connector.auth.AuthenticationStrategyFactory;
import io.github.navidzare.flux.connector.auth.BasicAuth;
import io.github.navidzare.flux.connector.auth.OAuth2Auth;
import io.github.navidzare.flux.connector.factory.JdbcConnectorFactory;
import io.github.navidzare.flux.connector.factory.RestConnectorFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@code flux.connectors} block of application configuration into a populated
 * {@link ConnectorRegistry}.
 *
 * <p>Connector types are discovered, not hard coded. Every {@link ConnectorFactory} bean in
 * the context — including ones contributed by optional modules such as Kafka — is collected
 * and indexed by its type name. Adding a connector type means adding a module, not editing
 * this class.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ConnectorProperties.class)
public class FluxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FluxAutoConfiguration.class);

    // ---- authentication -------------------------------------------------------

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
    public AuthenticationStrategyFactory authenticationStrategyFactory(
            List<AuthenticationStrategy> discovered) {
        AuthenticationStrategyFactory factory = new AuthenticationStrategyFactory(discovered);
        log.debug("Authentication strategies: {}", factory.registeredTypes());
        return factory;
    }

    // ---- connector factories --------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public RestConnectorFactory restConnectorFactory(AuthenticationStrategyFactory authentication) {
        return new RestConnectorFactory(authentication);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcConnectorFactory jdbcConnectorFactory(ObjectProvider<JdbcTemplate> jdbcTemplates) {
        return new JdbcConnectorFactory(jdbcTemplates);
    }

    // ---- registry -------------------------------------------------------------

    @Bean
    @ConditionalOnMissingBean
    public ConnectorRegistry connectorRegistry(ConnectorProperties properties,
                                               List<ConnectorFactory> factories) {

        Map<String, ConnectorFactory> byType = indexByType(factories);
        log.debug("Connector types available: {}", byType.keySet());

        ConnectorRegistry registry = new ConnectorRegistry();

        properties.getConnectors().forEach((name, definition) -> {
            if (!definition.isEnabled()) {
                log.info("Connector '{}' is disabled, skipping", name);
                return;
            }
            try {
                registry.register(build(name, definition, byType));
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

    private Map<String, ConnectorFactory> indexByType(List<ConnectorFactory> factories) {
        Map<String, ConnectorFactory> byType = new LinkedHashMap<>();
        for (ConnectorFactory factory : factories) {
            ConnectorFactory previous = byType.put(factory.type().toLowerCase(), factory);
            if (previous != null) {
                log.warn("Two factories claim connector type '{}'; using {}",
                        factory.type(), factory.getClass().getName());
            }
        }
        return byType;
    }

    private Connector build(String name,
                            ConnectorProperties.Definition definition,
                            Map<String, ConnectorFactory> byType) {

        String type = definition.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Connector '%s' declares no type".formatted(name));
        }

        ConnectorFactory factory = byType.get(type.toLowerCase());
        if (factory == null) {
            throw new IllegalArgumentException(
                    "No factory for connector type '%s' (connector '%s'). Available: %s"
                            .formatted(type, name, byType.keySet()));
        }
        return factory.create(name, definition);
    }

    /** Closes every connector on shutdown so pools and producers are released. */
    @Bean
    @ConditionalOnMissingBean
    public FluxShutdown fluxShutdown(ConnectorRegistry registry) {
        return new FluxShutdown(registry);
    }

    static class FluxShutdown {

        private final ConnectorRegistry registry;

        FluxShutdown(ConnectorRegistry registry) {
            this.registry = registry;
        }

        @PreDestroy
        public void shutdown() {
            log.info("Closing {} connector(s)", registry.size());
            registry.closeAll();
        }
    }
}
