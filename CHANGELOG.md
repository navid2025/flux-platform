# Changelog

Notable changes, most recent first. This project follows [semantic versioning](https://semver.org/).
While the version is below 1.0.0, the API may still change between minor releases.

## [Unreleased]

### Changed

- Relicensed from MIT to the Apache License 2.0. Both are permissive; Apache 2.0 adds an
  explicit patent grant, which enterprise buyers look for. Releases published under MIT keep
  their MIT terms.

## [0.1.0] — 2026-10-06

First release.

### Added

- `Connector` contract, `ConnectorRegistry`, and `ConnectorRequest`/`ConnectorResponse`.
- YAML declaration of downstream systems under `flux.connectors`, with `fail-fast` and
  per-connector `enabled`.
- `RestConnector`: query parameters on GET, JSON bodies otherwise, request and response
  encoding, a bounded response read, and no following of redirects.
- `JdbcConnector`: SQL templates named by operation, with named parameters.
- `ConnectorFactory` extension point, so a new connector type needs no change to core.
- `flux-connector-kafka`: producer with topic aliases, `default-topic` and `producer.*`
  pass-through.
- `flux-bpmn-camunda`: `ConnectorDelegate` and `BaseBpmnDelegate`, so a BPMN service task can
  call any registered connector.
- Authentication strategies for basic and OAuth2 client credentials, with token caching and
  configuration validated when the connector is built.
- `flux-spring-boot-starter`: one dependency, and everything under `flux.*` is registered
  before the application's own beans.
- `examples/demo-app`: four connectors against an in-memory database.

### Known limitations

- No retry or circuit breaking. Timeouts are enforced; retries are not.
- No SOAP connector.
- No metrics export. The response carries its duration, but nothing publishes it.
- No connector generation from OpenAPI or WSDL.
- The Kafka connector publishes only; it does not consume.

[Unreleased]: https://github.com/navid2025/flux-platform/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/navid2025/flux-platform/releases/tag/v0.1.0
