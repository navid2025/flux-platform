# Flux Platform

A small ESB development platform for Spring Boot.

Declare your downstream systems in `application.yml`, and the platform gives you a
registry of connectors to call them by name. Your mediation code never touches a host,
a credential, or a protocol again.

```java
Connector orders = registry.require("orders-db");
ConnectorResponse response = orders.execute(
        ConnectorRequest.of("findByStatus", Map.of("status", "PAID")));
```

Nothing in that snippet says whether `orders-db` is a database, an HTTP API, or something
you write next month. That is the point.

---

## Why

Most integration code fails the same way. A URL gets hard coded, credentials leak into a
class, and swapping an old system for a new one means touching twenty files.

Flux puts a seam in the middle:

- **Connectors** own the transport. One class per protocol family.
- **Configuration** owns the wiring. Which system, which credentials, which timeout.
- **Callers** own the business step. They ask for a name and get a result.

Adding a downstream system becomes a YAML change.

---

## Quick start

```bash
git clone https://github.com/navid2025/flux-platform.git
cd flux-platform
mvn clean install

cd examples/demo-app
mvn spring-boot:run
```

The demo starts an in-memory database and registers three connectors.

```bash
# what got registered
curl localhost:8080/demo/connectors

# invoke a connector
curl localhost:8080/demo/orders?status=PAID
```

---

## Configuration

Everything lives under `flux.connectors`. Each entry is one downstream system.

```yaml
flux:
  fail-fast: true          # refuse to start if a connector cannot be built

  connectors:

    customer-api:
      type: rest
      base-url: https://api.example.com/v1
      timeout: 5s
      auth:
        type: basic
        settings:
          username: ${CUSTOMER_API_USER}
          password: ${CUSTOMER_API_PASSWORD}

    billing-api:
      type: rest
      base-url: https://billing.example.com/api
      auth:
        type: oauth2
        settings:
          client-id: ${BILLING_CLIENT_ID}
          client-secret: ${BILLING_CLIENT_SECRET}

    orders-db:
      type: jdbc
      settings:
        template.findByStatus: >
          SELECT id, customer_id, status, total_amount
          FROM orders WHERE status = :status

    legacy-erp:
      type: rest
      base-url: https://erp.internal.example.com
      enabled: false         # declared, but off in this environment
```

### Settings reference

| Key | Applies to | Meaning |
|---|---|---|
| `type` | all | `rest`, `jdbc` |
| `base-url` | http connectors | root address; the operation is appended |
| `timeout` | all | per-call budget, e.g. `5s`, `500ms` |
| `auth.type` | all | `none`, `basic`, `oauth2` |
| `auth.settings` | all | values for the chosen strategy |
| `settings.template.*` | jdbc | operation name to SQL |
| `enabled` | all | skip registration when `false` |

Credentials are read from environment variables. Nothing secret belongs in this file.

---

## Architecture

```
        caller
          │  registry.require("orders-db")
          ▼
   ┌─────────────────┐
   │ ConnectorRegistry│   name → Connector
   └────────┬────────┘
            │
     ┌──────┴───────┬──────────────┐
     ▼              ▼              ▼
 RestConnector  JdbcConnector  YourConnector
     │              │              │
     ▼              ▼              ▼
  HTTP peer      database       anything
     ▲              ▲
     └──────┬───────┘
            │
   AuthenticationStrategy
       basic · oauth2
```

**Modules**

| Module | Responsibility |
|---|---|
| `flux-connector-core` | Connector contract, registry, built-in connectors, auth strategies |
| `flux-starter-autoconfigure` | Reads `flux.*` properties and registers the beans |
| `examples/demo-app` | A running example |

The registry is the only thing callers depend on. Everything below it is replaceable.

---

## Writing your own connector

Implement three methods.

```java
public class KafkaConnector implements Connector {

    @Override
    public String name() { return "events"; }

    @Override
    public String type() { return "kafka"; }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        // publish, then return
    }
}
```

Register it as a bean and add a `case` to `FluxAutoConfiguration#build`. A plugin
mechanism using `ServiceLoader` is the next thing on the list.

---

## Project status

Version 1.0.0-SNAPSHOT. The core is stable enough to build on, and the extension points
are settled. What is deliberately not here yet:

- **Retry and circuit breaking.** Configured values are read but not yet enforced.
  Until then, wrap calls at the call site.
- **BPMN delegation.** A connector can be invoked from a service task, but the bridge is
  not packaged.
- **Metrics.** The response carries its duration; exporting it is left to the caller.
- **Plugin discovery.** Connector types are resolved in a switch statement today.

---

## Requirements

- Java 17 or newer
- Spring Boot 3.2 or newer
- Maven 3.9+

---

## License

MIT. See [LICENSE](LICENSE).
