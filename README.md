# Tianji Service

Tianji is the business process and intelligent orchestration platform for Stellar Enclosure. The current delivery contains engine-neutral Agent contracts, a PostgreSQL Agent ledger, a manually invoked single-run executor and an opt-in secured HTTP adapter. A background dispatcher and complete runtime assembly are still being built.

## Modules

- `stellar-enclosure-tianji-contract`: JDK-only Agent client contract, commands, results and events.
- `stellar-enclosure-tianji-client`: engine-neutral HTTP client and resumable Agent event stream.
- `stellar-enclosure-tianji-agent-runtime`: the embedded runtime boundary and transactional Agent ledger; disabled by default.
- `stellar-enclosure-tianji-agent-server`: Spring Boot host and disabled-by-default Agent HTTP/SSE adapter.

The selected dependency line is Java 25, Spring Boot 4.1.2, Spring AI 2.0.2 and Google ADK 1.13.0. The runtime uses Agent Flow `0.0.2-SNAPSHOT`; until that artifact is published, build it together with the Components source repository. Contract and client modules do not depend on either engine or an AI framework.

The client uses JDK `HttpClient` and Jackson 3. Construct `HttpAgentClient` with a service URI, a client configured with `Redirect.NEVER`, a supplier of the current access token, a request timeout and a bounded event-stream reconnect policy. It sends writes once, limits JSON responses and resumes SSE using the last delivered persisted sequence. A subscriber controls demand and may cancel its subscription without cancelling the Agent run. A `stream.complete` frame closes a terminal stream; a planned `stream.reconnect` rotates the connection without treating it as a failed run; an expired cursor is reported as `TIANJI_EVENT_CURSOR_EXPIRED`. Error bodies and access tokens are not retained in client exceptions. The client has been tested against local HTTP stubs; real deployment integration remains to be verified.

The HTTP adapter is disabled by default. Enabling `stellar.tianji.agent.api.enabled` requires a servlet application, a configured ledger, the Tianquan-Shoubing and Tianquan-Jianshen filters and the compiled permission policy. It derives tenant and subject exclusively from verified USER or tenant-scoped SERVICE identity and checks the resource policy before accessing the ledger. No permission is granted by building the application. SSE keeps the verified request context for repeated authorization, allows at most 32 simultaneous streams, reads at most eight events per page and rotates connections after 30 seconds. Deployment must also bound servlet and proxy write timeouts for slow or disconnected clients. A result that has not yet been committed returns `TIANJI_RESULT_NOT_READY` with HTTP 409.

`stellar.tianji.agent.runtime.enabled` is false by default. Setting it to true currently fails startup because scheduling and complete runtime assembly are not yet wired into the service. No Agent or process endpoint is enabled by default. The ledger accepts an internal `TrustedAgentActor`; a caller must authenticate and authorize before creating it. Input artifact references and policy overrides are requests, not authorization; the ledger only permits policy limits to be reduced.

The ledger stores immutable definition versions, scoped idempotent starts, run status and ordered events. Its additive migrations are in `classpath:db/tianji-agent`; they are not run automatically. A deployment must use a dedicated Flyway configuration and schema owner for `tianji_agent`, separate from any other application's migration history. A host can dispatch a selected queued run through a bounded executor; cancellation and lease recovery are persisted, but periodic discovery and the secured public API still need application wiring. Until tool governance and persistent sessions are ready, the executor accepts one-shot text only and rejects tools. Code-first factories must be registered by trusted application code and use the supplied context-aware scheduler for every parallel agent.

Claims use PostgreSQL time and a fencing epoch; an expired lease is suspended for reconciliation rather than automatically retried. Cancellation intent and its command receipt commit together, and the worker polls the persisted intent while executing. The host supplies and owns the bounded dispatch executor and control scheduler. It must also supply a thread-safe, provider-specific `AgentModelBudgetEstimator` that conservatively bounds input tokens, output tokens and cost and can interpret verified provider usage. Reservations are atomic across parallel branches, unknown usage retains its allowance, and unavailable required usage fails the run. The provider must honor the requested output limit; an overrun can only be detected after the provider reports it. Total and per-model idle deadlines are enforced independently.

`TEXT_DELTA` events are increments, while `AGENT_OUTPUT` events are complete snapshots for individual Agents; neither is the root completion signal. The projector accepts only authors from the compiled Agent tree and excludes native parts marked as thoughts and tool arguments. Provider-specific text may still contain sensitive content and requires the later authorization and redaction controls. The final run status is written after the root stream finishes. A single execution snapshot or event is limited to 1 MiB of UTF-8 JSON, and a final result to 2 MiB; oversized values fail rather than being silently truncated.

For a JDK 25 environment with the Stellar-Enclosure platform parent and Components BOM available in Maven, run this from the repository root. It builds the sibling Components, Tianquan-Shoubing and Tianquan-Jianshen checkouts and Tianji in one reactor without installing or publishing artifacts:

```bash
./scripts/verify-source-reactor.sh -B -ntp
```

Set `STELLAR_COMPONENTS_DIR`, `STELLAR_SHOUBING_DIR` or `STELLAR_JIANSHEN_DIR` when the repositories are not sibling checkouts. The script requires Python 3 only to create a temporary reactor POM. After matching versioned Agent Flow and identity/authorization artifacts are available in the configured Maven repository, `./mvnw -B -ntp test` can also build Tianji on its own.

The `permission-producer` build profile uses Tianquan-Jianshen's processor and manifest CLI to embed and verify the Agent server's resource declarations in its executable JAR. A release build must supply the exact source commit and a unique build identity; generating a manifest neither activates it nor assigns permissions:

```bash
STELLAR_VERIFY_GOAL=verify ./scripts/verify-source-reactor.sh -B -ntp -Ppermission-producer \
  -Dpermission.commit="$(git rev-parse HEAD)" -Dpermission.buildId="<build-identity>"
```

The default tests use in-memory Spring contexts and a temporary loopback HTTP stub; they do not start the application server and they skip PostgreSQL integration classes. To run the runtime database tests, supply an explicitly created empty loopback database named `tianji_it_*` through `-Dtianji.postgres.test.url=jdbc:postgresql://127.0.0.1:<port>/tianji_it_<name>`. The HTTP ledger test uses a second independently created empty database through `-Dtianji.http.postgres.test.url` with the same URL restrictions. The tests reject any other URL and refuse a database that already contains the `tianji_agent` schema. They apply the migration and include concurrent starts, scope isolation, transaction rollback, HTTP replay and real ADK Runner execution with a fake model. Use disposable test databases, never a shared or production database.

Real model providers, MCP, full service execution and process engines require separate integration and acceptance work.
