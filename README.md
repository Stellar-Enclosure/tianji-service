# Tianji Service

Tianji is the business process and intelligent orchestration platform for Stellar Enclosure. The current delivery contains engine-neutral Agent contracts and a PostgreSQL Agent ledger. It does not yet dispatch agents or expose HTTP APIs.

## Modules

- `stellar-enclosure-tianji-contract`: JDK-only Agent client contract, commands, results and events.
- `stellar-enclosure-tianji-client`: reserved for the HTTP client implementation; no transport is exposed yet.
- `stellar-enclosure-tianji-agent-runtime`: the embedded runtime boundary and transactional Agent ledger; disabled by default.
- `stellar-enclosure-tianji-agent-server`: non-web Spring Boot shell with an in-process health endpoint.

The selected dependency line is Java 25, Spring Boot 4.1.2, Spring AI 2.0.2 and Google ADK 1.13.0. The runtime does not yet depend on the Agent Flow starter: the updated Components artifact must first be consumed through a verified source reactor or a separately versioned publication. Contract and client modules do not depend on either engine or an AI framework.

`stellar.tianji.agent.runtime.enabled` is false by default. Setting it to true currently fails startup because dispatch and trusted identity integration are not implemented. No Agent or process endpoint is enabled in this delivery. The ledger accepts an internal `TrustedAgentActor`; a caller must authenticate and authorize before creating it. Input artifact references and policy overrides are requests, not authorization; the ledger only permits policy limits to be reduced.

The ledger stores immutable definition versions, scoped idempotent starts, run status and ordered events. Its additive migration is in `classpath:db/tianji-agent`; it is not run automatically. A deployment must use a dedicated Flyway configuration and schema owner for `tianji_agent`, separate from any other application's migration history. The ledger does not yet claim queued runs, implement cancellation or reconcile interrupted execution.

For a JDK 25 environment with the Stellar-Enclosure platform parent and Components BOM available in Maven, run:

```bash
./mvnw -B -ntp test
```

The default tests use in-memory Spring contexts without opening a listening port and skip the PostgreSQL integration class. To run the ledger integration tests, supply an explicitly created empty loopback database named `tianji_it_*` through `-Dtianji.postgres.test.url=jdbc:postgresql://127.0.0.1:<port>/tianji_it_<name>`. The tests reject any other URL and refuse a database that already contains the `tianji_agent` schema. They apply the migration and include concurrent starts, scope isolation and transaction rollback. Use a disposable test database, never a shared or production database.

Real model providers, MCP, full service execution and process engines require separate integration and acceptance work.
