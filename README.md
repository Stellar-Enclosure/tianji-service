# Tianji Service

Tianji is the business process and intelligent orchestration platform for Stellar Enclosure. The current delivery contains engine-neutral Agent contracts, a PostgreSQL Agent ledger and a manually invoked single-run executor. It does not yet provide a background dispatcher or expose HTTP APIs.

## Modules

- `stellar-enclosure-tianji-contract`: JDK-only Agent client contract, commands, results and events.
- `stellar-enclosure-tianji-client`: reserved for the HTTP client implementation; no transport is exposed yet.
- `stellar-enclosure-tianji-agent-runtime`: the embedded runtime boundary and transactional Agent ledger; disabled by default.
- `stellar-enclosure-tianji-agent-server`: non-web Spring Boot shell with an in-process health endpoint.

The selected dependency line is Java 25, Spring Boot 4.1.2, Spring AI 2.0.2 and Google ADK 1.13.0. The runtime uses Agent Flow `0.0.2-SNAPSHOT`; until that artifact is published, build it together with the Components source repository. Contract and client modules do not depend on either engine or an AI framework.

`stellar.tianji.agent.runtime.enabled` is false by default. Setting it to true currently fails startup because scheduling and trusted identity are not yet wired into the service. No Agent or process endpoint is enabled in this delivery. The ledger accepts an internal `TrustedAgentActor`; a caller must authenticate and authorize before creating it. Input artifact references and policy overrides are requests, not authorization; the ledger only permits policy limits to be reduced.

The ledger stores immutable definition versions, scoped idempotent starts, run status and ordered events. Its additive migrations are in `classpath:db/tianji-agent`; they are not run automatically. A deployment must use a dedicated Flyway configuration and schema owner for `tianji_agent`, separate from any other application's migration history. A host can dispatch a selected queued run through a bounded executor; cancellation and lease recovery are persisted, but periodic discovery and the secured public API still need application wiring. Until tool governance and persistent sessions are ready, the executor accepts one-shot text only and rejects tools. Code-first factories must be registered by trusted application code and use the supplied context-aware scheduler for every parallel agent.

Claims use PostgreSQL time and a fencing epoch; an expired lease is suspended for reconciliation rather than automatically retried. Cancellation intent and its command receipt commit together, and the worker polls the persisted intent while executing. The host supplies and owns the bounded dispatch executor and control scheduler. It must also supply a thread-safe, provider-specific `AgentModelBudgetEstimator` that conservatively bounds input tokens, output tokens and cost and can interpret verified provider usage. Reservations are atomic across parallel branches, unknown usage retains its allowance, and unavailable required usage fails the run. The provider must honor the requested output limit; an overrun can only be detected after the provider reports it. Total and per-model idle deadlines are enforced independently.

`TEXT_DELTA` events are increments, while `AGENT_OUTPUT` events are complete snapshots for individual Agents; neither is the root completion signal. The projector accepts only authors from the compiled Agent tree and excludes native parts marked as thoughts and tool arguments. Provider-specific text may still contain sensitive content and requires the later authorization and redaction controls. The final run status is written after the root stream finishes. A single execution snapshot or event is limited to 1 MiB of UTF-8 JSON, and a final result to 2 MiB; oversized values fail rather than being silently truncated.

For a JDK 25 environment with the Stellar-Enclosure platform parent and Components BOM available in Maven, run this from the repository root. It builds the sibling Components checkout and Tianji in one reactor without installing or publishing artifacts:

```bash
./scripts/verify-source-reactor.sh -B -ntp
```

Set `STELLAR_COMPONENTS_DIR` if Components is not a sibling checkout. The script requires Python 3 only to create a temporary reactor POM. After a separately versioned Agent Flow artifact is available in the configured Maven repository, `./mvnw -B -ntp test` can also build Tianji on its own.

The default tests use in-memory Spring contexts without opening a listening port and skip the PostgreSQL integration classes. To run them, supply an explicitly created empty loopback database named `tianji_it_*` through `-Dtianji.postgres.test.url=jdbc:postgresql://127.0.0.1:<port>/tianji_it_<name>`. The tests reject any other URL and refuse a database that already contains the `tianji_agent` schema. They apply the migration and include concurrent starts, scope isolation, transaction rollback and real ADK Runner execution with a fake model. Use a disposable test database, never a shared or production database.

Real model providers, MCP, full service execution and process engines require separate integration and acceptance work.
