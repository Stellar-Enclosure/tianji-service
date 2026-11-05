# AS terminal Outbox Span Links — Source77

Effective [Spec](../spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md) and [Plan](../plan/2026-11-05-17-45-agent-terminal-span-link-implementation.md). Baseline `c7b4e7dcd171e3762791bc8de0905393a5850dde`.

When explicitly enabled for the Tianji terminal channel, the existing enqueue stores a closed optional diagnostic carrier for a sampled recorded producer span. The span ends through the existing transaction completion callback. Verified AS delivery creates an independent root with a real SDK Span Link and keeps the original event, headers, idempotency fingerprint, owner checks, deadline and retry behavior. No tracing header or carrier is sent to PS or Worker. The finite metrics use four fixed result values without tenant, run, job, token or correlation labels.

| Gate | Actual result |
| --- | --- |
| Source76 local SDK | Reflection and typed assertion RED, 1 each; 2 actual SDK finite tests passed. |
| G2 typed integration RED | 2 expected assertion failures, 0 errors/skips, 18:24:18 +08:00. |
| G2 finite GREEN | 20 passed in five selected classes, 0 failures/errors/skips, 18:26:18 +08:00. |
| Final clean verify | 32 passed in six selected classes (CL 2, AS 18, PS 12), 0 failures/errors/skips, 18:27:31 +08:00. |

Current test-local `SpanProcessor` records actual `SpanData` from the existing SDK. No exporter endpoint, application, listener or database was started. The related JDK listener-based HTTP client suite was replaced in the reviewed finite Plan by `ClientDependencyBoundaryTest`, and the actual AS sender boundary remains covered by finite fake transport tests. Exact reports and logs are under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/`.

The actual AS executable JAR includes the observation class and exact packaged YAML with `span-links-enabled: false`. YAML SHA-256 `a1790465f9b38d1c1f43f74b04423b3a4b48e0196e398f232a988874ae7b9d47`, archive SHA-256 `8c84e9b4078eef7d87b6fa88d26f9b9786bb531d0cb83ce869f165def0cb0165`. All 27 main SQL files and original C/CL/PS/W, Components Outbox schema/dependency, event/header/fingerprint sources remained unchanged.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Exact current AS terminal-channel ownership only. |
| MC-REUSE-001 | PASS | Existing Outbox diagnostic slot, transaction, verified sender and OTel/Micrometer APIs. |
| MC-DEP-001 | PASS | Current SDK classpath, no POM/dependency. |
| MC-NAME-001 | PASS | Fixed observation, carrier, span and metric names. |
| MC-VALID-001 | PASS | Closed sampled carrier, exact channel/authority and original replay/timeout tests. |
| MC-MODEL-001 | PASS | Existing immutable events and bounded diagnostic input. |
| MC-CONVERT-001 | N/A | No entity/contract projection. |
| MC-LOG-001 | N/A | No new logging, credentials or raw payloads. |
| MC-BEAN-001 | PASS | Current named factories, optional tracing only after opt-in. |
| MC-UTIL-001 | PASS | Current SDK/registry/Spring transaction and JDK helpers. |
| MC-JSON-001 | PASS | Exact unchanged event/header/fingerprint fixture assertions. |
| MC-TIME-001 | PASS | Original deadline/callback lifecycle, no new persisted time. |
| MC-CONFIG-001 | PASS | Explicit default false in the actual executable artifact. |
| MC-PATTERN-001 | PASS | Existing publisher and verified delivery handler with narrow optional observation. |
| MC-SCOPE-001 | PASS | Exact 8 Step files, 12 aggregate; all 27 SQL immutable. |
| MC-TEST-001 | PASS | Both real typed RED, finite and clean package, actual in-memory Links. |
| MC-BLOCKER-001 | PASS | Current source gates closed; deployed listener and real exporter separately gated. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing AS process integration names. |
| 2 | PASS | Closed carrier and exact original verified scope and deadline. |
| 3 | PASS | Current immutable event, bounded optional context. |
| 4 | PASS | Existing named factory, no new business logger. |
| 5 | PASS | Existing JDK/OTel/Micrometer/Spring only. |
| 6 | PASS | No change to actual event JSON or digest. |
| 7 | PASS | Exact packaged off configuration. |
| 9 | PASS | Existing terminal event adapter and delivery lifecycle. |
| 10 | PASS | No business clock or existing event time change. |
| 11 | PASS | Existing AS technical Event owner and Outbox; no schema/runner/dependency. |

Actual opt-in requires independent review of deployed DEAD listeners and a separately authorized isolated exporter/first-write/retry proof. PS/Worker links, cross-source correlation, production lag/SLO, late usage and S12 remain separate.
