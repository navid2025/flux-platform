## What this changes

A short description of the problem and the fix.

## How it was verified

The command you ran and what it showed. `./mvnw clean verify` output is ideal; a manual call
against the demo app is fine too.

## Checklist

- [ ] `./mvnw clean verify` passes
- [ ] A test covers the change (a bug fix gets a test that fails without it)
- [ ] No new dependency added to `flux-connector-core`
- [ ] New tunables are configuration, not constants in code
- [ ] Documentation updated if behaviour or configuration changed
