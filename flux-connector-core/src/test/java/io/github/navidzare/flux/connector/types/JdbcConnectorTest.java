package io.github.navidzare.flux.connector.types;

import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConnectorTest {

    private JdbcConnector connector;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:orders-schema.sql")
                .build();

        connector = new JdbcConnector("orders-db", new JdbcTemplate(dataSource),
                Map.of("template.findByStatus",
                        "SELECT id, status FROM orders WHERE status = :status ORDER BY id"));
    }

    @Test
    void runsTheTemplateNamedByTheOperation() {
        ConnectorResponse response = connector.execute(
                ConnectorRequest.of("findByStatus", Map.of("status", "PAID")));

        assertThat(response.success()).isTrue();
        assertThat(response.bodyAs(List.class)).hasSize(2);
    }

    @Test
    void reportsWhichOperationsHaveATemplate() {
        assertThat(connector.supports("findByStatus")).isTrue();
        assertThat(connector.supports("unknown")).isFalse();
    }
}
