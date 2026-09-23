package io.github.navidzare.flux.connector.types;

import io.github.navidzare.flux.connector.Connector;
import io.github.navidzare.flux.connector.ConnectorRequest;
import io.github.navidzare.flux.connector.ConnectorResponse;
import io.github.navidzare.flux.connector.exception.ConnectorException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Executes a query named by the operation against a relational database.
 *
 * <p>SQL is not embedded in flows: the operation carries a template identifier that the
 * connector resolves from its {@code templates} settings. Keeping SQL out of the flow
 * definition means a DBA can review it without reading mediation logic.</p>
 *
 * <pre>
 * flux:
 *   connectors:
 *     customers:
 *       type: jdbc
 *       settings:
 *         template.findByStatus: SELECT id, name FROM customers WHERE status = :status
 * </pre>
 */
public class JdbcConnector implements Connector {

    public static final String TYPE = "jdbc";
    private static final String TEMPLATE_PREFIX = "template.";

    private final String name;
    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, String> templates;

    public JdbcConnector(String name, JdbcTemplate jdbcTemplate, Map<String, String> settings) {
        this.name = name;
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.templates = settings.entrySet().stream()
                .filter(e -> e.getKey().startsWith(TEMPLATE_PREFIX))
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().substring(TEMPLATE_PREFIX.length()),
                        Map.Entry::getValue));
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
    public boolean supports(String operation) {
        return templates.containsKey(operation);
    }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        long started = System.nanoTime();
        String sql = templates.get(request.operation());
        if (sql == null) {
            throw new ConnectorException(name, request.operation(), "No SQL template registered");
        }

        try {
            List<Map<String, Object>> rows =
                    jdbc.queryForList(sql, new MapSqlParameterSource(request.payload()));
            return ConnectorResponse.ok(rows, Duration.ofNanos(System.nanoTime() - started));
        } catch (Exception ex) {
            throw new ConnectorException(name, request.operation(), "Query failed", ex);
        }
    }
}
