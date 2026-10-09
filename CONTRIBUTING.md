# Contributing

Thanks for taking the time. This is a small project, so the process is small too.

## Before you start

For anything larger than a bug fix, open an issue first. It is cheaper to agree on the
shape of a change than to rework a pull request.

## Build and test

```bash
git clone https://github.com/navidzaare/flux-platform.git
cd flux-platform
./mvnw clean verify
```

The build runs every module's tests. It has to be green before a pull request is reviewed.

## Working on a change

Never commit to `main`, even for a one-line fix. Branch, then open a pull request:

```bash
git checkout -b add-example-connector
# make your changes
./mvnw clean verify
git add .
git commit -m "add a worked example connector"
git push -u origin add-example-connector
```

GitHub then offers a **Compare & pull request** button on the repository page. Open the pull
request against `main` and it will be reviewed there. Working on a branch means CI runs before
the change lands, and if something needs reworking it can be done without unpicking `main`.

If you would rather not push a branch to this repository, fork it and open the pull request
from your fork instead. Both are welcome.

## Project layout

| Module | Responsibility |
|---|---|
| `flux-connector-core` | `Connector` contract, registry, REST and JDBC connectors, auth strategies |
| `flux-connector-kafka` | Kafka connector |
| `flux-bpmn-camunda` | BPMN delegates for Camunda 7 |
| `flux-starter-autoconfigure` | Reads `flux.*` and registers the beans |
| `flux-spring-boot-starter` | The single dependency applications add |
| `examples/demo-app` | A running example |

## Adding a connector type

The core does not need to change. Implement `Connector`, then declare a `ConnectorFactory`
bean:

```java
@Bean
public ConnectorFactory amqpConnectorFactory() {
    return new ConnectorFactory() {
        public String type() { return "amqp"; }
        public Connector create(String name, ConnectorProperties.Definition definition) {
            return new AmqpConnector(name, definition.getSettings());
        }
    };
}
```

`type: amqp` works from that point on. If it is generally useful, a separate module is
preferred over a change to core — that is how `flux-connector-kafka` sits outside.

## What a good change looks like

- **Tests.** Every bug fix gets a test that fails without it. New behaviour gets a test that
  describes it.
- **No new dependencies in core.** `flux-connector-core` stays free of JSON libraries, HTTP
  clients beyond the JDK, and anything else that would make it heavier than it needs to be.
- **Configuration over code.** A new tunable belongs in `flux.*` settings, not in a constant.
- **Comments explain why.** The code says what it does. A comment earns its place by
  explaining a decision that is not obvious from reading it.

## Style

Match what is already there. Four spaces, no wildcard imports, `var` only where the type is
on the same line. Test method names read as sentences: `refusesAnOperationThatWouldEscapeThePath`.

## Pull requests

One change per pull request. Describe what problem it solves and how you verified it — a
command and its output is worth more than a paragraph.

## Licence and the Contributor License Agreement

The project is licensed under the Apache License 2.0. Contributions come in under the same terms.

Before a contribution can be merged, you also need to accept the
[Contributor License Agreement](CLA.md). You keep the copyright to your work — the CLA grants
the maintainer the right to distribute it, including under different licence terms later.

Until signing is automated, state it in your pull request:

> I have read and accept CLA.md

That is enough for now. The
[CLA Assistant](https://github.com/contributor-assistant/github-action) action takes over once
there are regular contributors; it records signatures in a separate branch and needs a
personal access token stored as a repository secret.
