package io.github.navidzare.flux.connector.factory;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorFactory;
import io.github.navidzare.flux.connector.ConnectorProperties;
import io.github.navidzare.flux.connector.types.JdbcConnector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds {@link JdbcConnector} instances, sharing the application's {@link JdbcTemplate}. */
public class JdbcConnectorFactory implements ConnectorFactory {

    private final ObjectProvider<JdbcTemplate> jdbcTemplates;

    public JdbcConnectorFactory(ObjectProvider<JdbcTemplate> jdbcTemplates) {
        this.jdbcTemplates = jdbcTemplates;
    }

    @Override
    public String type() {
        return JdbcConnector.TYPE;
    }

    @Override
    public Connector create(String name, ConnectorProperties.Definition definition) {
        JdbcTemplate template = jdbcTemplates.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException(
                    "Connector '%s' is jdbc but no JdbcTemplate is available. "
                            + "Add spring-boot-starter-jdbc and a DataSource.".formatted(name));
        }
        return new JdbcConnector(name, template, definition.getSettings());
    }
}
