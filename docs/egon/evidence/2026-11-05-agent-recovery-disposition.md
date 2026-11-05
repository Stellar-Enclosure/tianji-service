# Agent owner-local recovery disposition — Source74

Effective [Spec](../spec/2026-11-05-16-52-agent-recovery-disposition.md) and [Plan](../plan/2026-11-05-17-42-agent-recovery-disposition.md), baseline `6b806e43888ea5e133780f9b755eb610326f52b8`.

The internal `AgentLedgerService.inspectRecovery` query selects one exact owner on the existing Agent primary before reading bounded Tool metadata. It uses one explicitly read-only repeatable snapshot, verifies the source clock/primary and exact V13 history before accepting Tool coverage, and projects only safe status, scalar effect and receipt-presence facts. Terminal state, UNKNOWN outcome and incomplete evidence stay independent. The query never reads raw result content, lease credentials or a Tool result body and never changes a run, resends a Tool call, settles budget or authorizes resume. The Process Worker owner query and existing public SDK remain unchanged.

| Gate | Actual result |
| --- | --- |
| R1 missing method | 1 expected reflection assertion failure, 0 errors/skips, 17:51:43 +08:00. |
| R2 typed safe stub | 1 expected positive read assertion failure, 0 errors/skips, 17:57:05 +08:00. |
| R3 exact query | 5 passed, 0 failures/errors/skips, 17:59:51 +08:00. |
| R4 finite regression | 66 passed in eight selected classes (AR 52, PS 14), 0 failures/errors/skips, 18:00:23 +08:00. |

Offline JDK25 source reactor, `-DskipITs=true`, no OTel exporters or ADK content capture. R4 exact selector: `AgentRecoveryDispositionEvidenceTest,AgentRunReconcilerTest,AgentRunDispatcherTest,ToolInvocationRecorderTest,AdkSessionEventCodecTest,WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,AgentModelUsageStoreTest`. Exact owning XML and logs are in `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/`. Finite mocks prove transaction and SQL shape, boundaries and status disposition. No app, PostgreSQL, listener, provider, Browser or actual checkpoint/reconciliation was executed.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing AR ledger/repository and same owned primary manager only. |
| MC-REUSE-001 | PASS | Current actor/query, state constants, source read pattern and V13 identity. |
| MC-DEP-001 | PASS | No dependencies/POM changes. |
| MC-NAME-001 | PASS | Explicit recovery lifecycle, reason, effect and Tool coverage names. |
| MC-VALID-001 | PASS | Exact owner/safe state/fence/source/clock/bounds/foreign tests. |
| MC-MODEL-001 | PASS | Closed immutable DTOs and independently retained facts. |
| MC-CONVERT-001 | PASS | Explicit scalar metadata mapping, no raw payload or entity. |
| MC-LOG-001 | N/A | No new business logging; fixed failure messages. |
| MC-BEAN-001 | N/A | No managed Bean or adapter wiring. |
| MC-UTIL-001 | PASS | Existing JDBC/JSON scalar and time APIs. |
| MC-JSON-001 | PASS | SQL extracts only validated closed outcome; no external encoding change. |
| MC-TIME-001 | PASS | One DB Instant and exact deadline/lease equality fixtures. |
| MC-CONFIG-001 | N/A | No profile/default change. |
| MC-PATTERN-001 | PASS | Current owner-local Query and a direct closed classifier. |
| MC-SCOPE-001 | PASS | Exact six paths, all 27 main SQL files unchanged. |
| MC-TEST-001 | PASS | Actual R1/R2 RED, R3/R4 GREEN, named fresh reports. |
| MC-BLOCKER-001 | PASS | Source verification closed; actual PG snapshot/E/S11 and T4-04 public boundary separate. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Semantic existing AR names and closed types. |
| 2 | PASS | Exact owner and verified read/time/shape boundaries. |
| 3 | PASS | Immutable values and checked scalar row projection. |
| 4 | N/A | No managed business logging or new Bean. |
| 5 | PASS | Existing JDK/Spring/JDBC without additional libraries. |
| 6 | PASS | Closed known outcome, existing public JSON untouched. |
| 7 | N/A | No profile keys. |
| 9 | PASS | Existing repository Query, direct finite disposition. |
| 10 | PASS | UTC Instant and two-second observation bound. |
| 11 | PASS | Existing read-only technical JDBC profile, no layer, DDL or Event. |

The internal query exposes no HTTP/PaC operation. Actual S11, checkpoint eligibility, automatic resume and external-effect reconciliation remain unverified and outside this slice.
