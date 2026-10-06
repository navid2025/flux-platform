# Flux Platform

[![build](https://github.com/navid2025/flux-platform/actions/workflows/build.yml/badge.svg)](https://github.com/navid2025/flux-platform/actions/workflows/build.yml)
[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

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

## Try it

```bash
git clone https://github.com/navid2025/flux-platform.git
cd flux-platform
./mvnw clean install

cd examples/demo-app
./mvnw spring-boot:run
```

The demo starts an in-memory database and registers four connectors.

```bash
# what got registered
curl localhost:8080/demo/connectors

# invoke a connector
curl localhost:8080/demo/orders?status=PAID
```

---

## Using Flux in your own application

One dependency. That is the whole installation.

```xml
<dependency>
  <groupId>io.github.navidzare</groupId>
  <artifactId>flux-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Then declare a connector and use it.

**1. `application.yml`**

```yaml
flux:
  connectors:
    customer-api:
      type: rest
      base-url: https://api.example.com/v1
      auth:
        type: basic
        settings:
          username: ${CUSTOMER_API_USER}
          password: ${CUSTOMER_API_PASSWORD}
```

**2. Inject the registry**

```java
@Service
public class CustomerService {

    private final ConnectorRegistry registry;

    public CustomerService(ConnectorRegistry registry) {
        this.registry = registry;
    }

    public ConnectorResponse lookup(long id) {
        return registry.require("customer-api")
                .execute(ConnectorRequest.of("getUser", Map.of("id", id)));
    }
}
```

No configuration class. No `@Bean`. The auto-configuration reads `flux.*` and registers
everything before your beans are created.

### Where the artifact comes from

| Source | Status | Who it suits |
|---|---|---|
| **Build from source** | Works today | Anyone cloning the repo |
| **JitPack** | Works once the repo is public | Anyone wanting a one-line dependency |
| **Maven Central** | Not published | The long-term goal |

**JitPack** builds straight from a GitHub tag and needs no publishing pipeline:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.navid2025.flux-platform</groupId>
  <artifactId>flux-spring-boot-starter</artifactId>
  <version>v0.1.0</version>
</dependency>
```

Maven Central is worth doing eventually, but it needs a signed release and a verified
group ID. JitPack gets you a working, citable artifact in minutes.

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
          token-uri: ${BILLING_TOKEN_URI}
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
| `flux-connector-core` | Connector contract, registry, REST and JDBC connectors, auth strategies |
| `flux-connector-kafka` | Kafka connector — optional |
| `flux-bpmn-camunda` | BPMN delegates for Camunda 7 — optional |
| `flux-starter-autoconfigure` | Reads `flux.*` properties and registers the beans |
| `flux-spring-boot-starter` | The single dependency applications add |
| `examples/demo-app` | A running example |

The registry is the only thing callers depend on. Everything below it is replaceable.

### Connector types

| Type | Module | Operations |
|---|---|---|
| `rest` | core | the operation names the URL suffix |
| `jdbc` | core | the operation names a SQL template |
| `kafka` | `flux-connector-kafka` | the operation names a topic, or `send` / `publish` for the default topic |

---

## Kafka

```xml
<dependency>
  <groupId>io.github.navidzare</groupId>
  <artifactId>flux-connector-kafka</artifactId>
  <version>0.1.0</version>
</dependency>
```

```yaml
flux:
  connectors:
    orders-out:
      type: kafka
      settings:
        bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
        default-topic: flux.orders
        topic.publish: orders.created     # operation "publish" -> orders.created
        producer.acks: all                # passed straight to the Kafka producer
        producer.enable.idempotence: "true"
```

```java
registry.require("orders-out")
        .execute(ConnectorRequest.of("publish", Map.of("orderId", 1001, "status", "PAID")));
```

`execute` blocks until the broker acknowledges the write, so a success means the message was
actually persisted. The response body carries the topic, partition and offset.

Anything under the `producer.` prefix is forwarded to the Kafka producer, so tuning `acks`,
`compression.type` or `linger.ms` never needs a code change.

---

## Camunda

```xml
<dependency>
  <groupId>io.github.navidzare</groupId>
  <artifactId>flux-bpmn-camunda</artifactId>
  <version>0.1.0</version>
</dependency>
```

A BPMN service task then calls any registered connector:

```xml
<serviceTask id="lookupCustomer" camunda:delegateExpression="${connectorDelegate}">
  <extensionElements>
    <camunda:inputOutput>
      <camunda:inputParameter name="connectorName">customer-api</camunda:inputParameter>
      <camunda:inputParameter name="connectorOperation">getUser</camunda:inputParameter>
      <camunda:inputParameter name="cn_id">${customerId}</camunda:inputParameter>
    </camunda:inputOutput>
  </extensionElements>
</serviceTask>
```

The delegate reads the connector and operation from process variables, forwards every `cn_`
variable as a parameter, and publishes the result back:

| Variable | Written |
|---|---|
| `connectorSuccess` | whether the call succeeded |
| `connectorStatus` | transport status code |
| `connectorData` | the response body |
| `connectorError` | error message on failure |
| `connectorDurationMs` | how long the call took |

A gateway can branch on `connectorSuccess` directly. A connector that fails, whether it
returns a failed response or throws, still writes those variables and lets the process
continue. To route failures through an error boundary event instead, subclass
`ConnectorDelegate` and set `throwOnFailure`.

One Camunda rule worth knowing: the engine refuses to deploy a process that does not declare
`camunda:historyTimeToLive`, so set it on the process element (or configure a default on the
engine) before your first deployment.

`BaseBpmnDelegate` is there for tasks that need more than a straight call — reshaping
parameters, choosing between connectors, or interpreting a response.

---

## Writing your own connector

Implement the interface, then tell Flux about it with a factory.

```java
public class AmqpConnector implements Connector {

    @Override
    public String name() { return name; }

    @Override
    public String type() { return "amqp"; }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        // publish, then return
    }
}
```

```java
@Bean
public ConnectorFactory amqpConnectorFactory() {
    return new ConnectorFactory() {
        public String type() { return "amqp"; }
        public Connector create(String name, ConnectorProperties.Definition def) {
            return new AmqpConnector(name, def.getSettings());
        }
    };
}
```

Declare the factory as a bean and `type: amqp` works immediately. No core class is edited,
and the same pattern is what lets `flux-connector-kafka` be an optional module sitting
outside the core.

---

## Project status

Version 0.1.0. The core, Kafka and Camunda modules are built and tested. What is deliberately
not here yet:

- **Retry and circuit breaking.** Timeouts are enforced; retries are not. Wrap calls at the
  call site until this lands.
- **SOAP connector.** XML, WSDL and XSD handling is not implemented. The extension point
  exists; the connector does not.
- **Metrics export.** The response carries its duration, but nothing publishes it to a
  registry yet.
- **Connector generation.** The idea of generating a connector from an OpenAPI document is
  not built. It remains the most interesting thing on the list.
- **A Kafka consumer.** The Kafka connector publishes only.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

---

## Requirements

- Java 17 or newer
- Spring Boot 3.3 or newer
- Camunda 7.22 (only for `flux-bpmn-camunda`)

Maven is not required — the wrapper is checked in. Use `./mvnw` instead of `mvn`.

---

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Security problems go through
[SECURITY.md](SECURITY.md), not the public issue tracker.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
