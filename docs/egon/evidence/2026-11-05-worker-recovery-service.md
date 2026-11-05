# Governed native Worker recovery — Source59 / recovery Plan Step 4

Baseline `dc1be79ee317ad9cdeec3ec56de4d6d19f21500a`; [Spec](../spec/2026-11-05-13-41-process-worker-recovery.md), [Plan](../plan/2026-11-05-14-27-process-worker-recovery.md).

The composed `WorkerRecoverySpecification` supplies one fixed disposition and semantic version to both observation and locked admission. It separates current deadletter presence from V4 history, keeps safe reported failure independent, and admits only one supported ordinary READ_ONLY Worker with complete exact exhausted retry evidence and a reverified current registration/input digest. The bound history read excludes credential and payload data. Ineligible or contradictory states never gain a version.

GET takes one repeatable snapshot. POST binds one owning transaction and orders START, command replay, root, scoped deadletter lock, V4 and current binding, native command and flushed same-ID external readback, immutable audit, and generic APPLY. An exact stored replay precedes native reads. Fresh success requires retry count 1, exact native affinity, no collision and both native lock fields null. Known races are classified after rollback; no lease row is reopened and no direct native DML is used.

| Gate | Actual result |
| --- | --- |
| G14 presence RED | 14 tests, 3 expected assertion failures for absent seams, 0 errors/skips. |
| G15 behavior RED | 28 tests, 17 expected assertion failures against phase A, 0 errors/skips. |
| G16 exact finite regression | 120 passed in 12 named PS classes, 0 failures/errors/skips; finished 2026-11-05 15:15:24 +08:00. |

G16 selector: `WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest,ProcessWorkerRecoveryReceiptStoreTest,ProcessControlServiceTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,ProcessTaskOriginTest,FlowableCaseServiceTest,FlowableHumanTaskServiceTest,CaseNativeAccessTest,HumanTaskNativeAccessTest`. Offline JDK 25 source reactor with credential-free task settings, `-DskipITs=true`, no OTel exporters or ADK content capture. XML and actual logs under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/recovery/`. Native SQL, transaction and rollback requests are asserted with finite mocks; no database or Flowable engine started.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS execution and Worker stores own native and lease authority; exact same source and manager. |
| MC-REUSE-001 | PASS | Current release/model, reporter/registry, input and occurrence digest, native root fence and generic receipt lifecycle. |
| MC-DEP-001 | PASS | Existing JDBC/Spring/Flowable/JDK and SLF4J, POMs unchanged. |
| MC-NAME-001 | PASS | Explicit recovery Specification, native facts, service and fixed blocked error. |
| MC-VALID-001 | PASS | Bounded exact inputs, authority checks, fixed precedence and hash, scoped native lock and readback, negative rollback fixtures. |
| MC-MODEL-001 | PASS | Immutable facts/decision and closed public enum/DTO projections. |
| MC-CONVERT-001 | PASS | Explicit same-package technical row/DTO mapping, with affinity and safety checks at projection. |
| MC-LOG-001 | PASS | Existing SLF4J convention, safe tenant/outcome fields only; no actor ID, reason, exception, token or data values. |
| MC-BEAN-001 | N/A | Same-source participants via explicit constructor; named Spring factory is Step 5. |
| MC-UTIL-001 | PASS | Existing framed digest and current Worker hash machinery; JDK UTF-8/order/Instant APIs. |
| MC-JSON-001 | PASS | Existing C validated DTOs and replay identity; no native payload or command decision JSON. |
| MC-TIME-001 | PASS | DB clock and Instant framing, strict attempt intervals and separate observation/decision time. |
| MC-CONFIG-001 | N/A | No application property, profile or role change in Step 4. |
| MC-PATTERN-001 | PASS | A single composed Specification drives both reads and writes; existing transaction-participant pattern. |
| MC-SCOPE-001 | PASS | Exact eleven paths; 954 excluded tracked hashes; all 26 SQL files, including V10, unchanged. |
| MC-TEST-001 | PASS | Actual presence/typed RED and full finite G16 with earlier Worker/Control/task/Case regressions. |
| MC-BLOCKER-001 | PASS | Current source gates closed; pinned native behavior and transactions await authorized runtime acceptance. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing PS names, explicit recovery decision vocabulary. |
| 2 | PASS | Exact identity/authority/provenance/safety, size and clock guards in test fixtures and code. |
| 3 | PASS | Immutable evidence/decision and explicit validated public DTO projections. |
| 4 | PASS | Established explicit PS technical construction and structured SLF4J, no sensitive fields or added dependency. |
| 5 | PASS | Existing store/Flowable/Worker hash/JDK utilities. |
| 6 | PASS | Existing closed contract enum/typed responses, no decision JSON or native diagnostics. |
| 7 | N/A | No runtime config or feature wiring in this Step. |
| 9 | PASS | One Specification for GET and POST, existing transaction participant and Worker verification. |
| 10 | PASS | UTC Instant, explicit clock and lease-order guards; no commit-time inference. |
| 11 | PASS | Approved plain JDBC/Flowable technical profile, exact paths, no native DML or migration edits. |

Actual Flowable movement, PostgreSQL transaction/role behavior, Worker reacquisition and IAM were not run.
