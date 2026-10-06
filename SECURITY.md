# Security Policy

## Reporting a vulnerability

Please do not open a public issue for a security problem.

Use GitHub's [private vulnerability reporting](https://github.com/navid2025/flux-platform/security/advisories/new),
or email the maintainer using the address on their GitHub profile.

Include what you can:

- What the problem is and where
- How to reproduce it
- What an attacker could do with it
- The version or commit you tested

You will get an acknowledgement, and credit in the release notes if you want it.

## Supported versions

The project is pre-1.0. Fixes land on the latest release; there are no maintained older lines
yet.

## What this project is responsible for

Flux is a library. It handles outbound calls and the credentials for them, so the areas worth
attention are:

| Area | Relevant code |
|---|---|
| Credential handling and caching | `flux-connector-core` — `auth` |
| Request construction (encoding, injection) | `flux-connector-core` — `types/RestConnector`, `support/Json` |
| Connection and thread lifecycle | `ConnectorRegistry`, the connector `initialise`/`close` pair |

## What it does not do

Flux does not authenticate incoming requests. That belongs in front of it — a gateway such as
APISIX, or your application's own filter chain. A deployment that exposes a Flux service
directly to an untrusted network has no entry authentication at all.

Credentials are read from configuration. Keep them in environment variables or a secret
manager, never in a committed file. `examples/demo-app/application.yml` shows the pattern.
