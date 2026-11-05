# Worker recovery SDK — Source57 / recovery Plan Step 2

Baseline `c6af7ba1aefeef1f804153964a372ebf00dc9a23`; [Spec](../spec/2026-11-05-13-41-process-worker-recovery.md), [Plan](../plan/2026-11-05-14-27-process-worker-recovery.md). `HttpProcessClient` implements the separate capability, extending its existing transport adapter. The recovery decoder closes field sets, media types, status/code pairs, UTC representation, nullable fields and DTO/target invariants. The adapter sends once with the existing no-redirect client, preserves interruption and timeout handling, and exposes fixed protocol failures without response details. No dependency or existing Control artifact changed.

| Gate | Actual result |
| --- | --- |
| G5 presence RED | 1 expected assertion failure, 0 errors/skips. |
| G6 typed RED | 7 tests, 6 expected failures against shape-only transport, 0 errors/skips. |
| G7 transport GREEN | 7 passed, 0 failures/errors/skips. |
| G8 OpenAPI RED | 1 expected missing-resource failure, 0 errors/skips. |
| G9 selected package | 35 passed in 6 classes (C 5, CL 30), 0 failures/errors/skips; 6.259 s, 2026-11-05 14:55:33 +08:00. |

G9 selector: `ProcessWorkerRecoveryContractTest,ProcessWorkerRecoveryHttpTest,ProcessWorkerRecoveryOpenApiContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ClientDependencyBoundaryTest`. Offline JDK 25 `./mvnw -pl :stellar-enclosure-tianji-client -am -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false` using the credential-free task settings. XML/logs and extracted JAR members are preserved under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/recovery/`. The packaged recovery JSON equals source (SHA-256 `fa1bb047f3a0a721ea790590fda6a7ea3d8f6c4b055c252b5d9e2c9f5a2cbd21`); the old packaged Control JSON equals its unchanged source (SHA-256 `4dc8f28d432bf8674c190cae3e240a9ca110fcd894b57397a3ccc5510f8f91e5`), and its codec matches the original baseline.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Recovery adapter and decoder stay in CL, provider independent. |
| MC-REUSE-001 | PASS | Existing HTTP request, path, bearer, timeout and bounded subscriber machinery. |
| MC-DEP-001 | PASS | JDK and existing Jackson 3; unchanged POMs and passing dependency gate. |
| MC-NAME-001 | PASS | Existing transport and codec naming; exact HTTP and error vocabulary. |
| MC-VALID-001 | PASS | Single-send, status/media, cap, strict UTF-8/JSON, request affinity and fixed failure tests. |
| MC-MODEL-001 | PASS | Explicit validated record mapping and immutable code sets. |
| MC-CONVERT-001 | PASS | Local schema-aware decoder constructs typed C records without coercion. |
| MC-LOG-001 | N/A | No logger added; HTTP and JSON details are not forwarded by recovery. |
| MC-BEAN-001 | N/A | No managed bean or injection changes. |
| MC-UTIL-001 | PASS | Existing bounded subscriber, URI segment and JDK charset/time APIs. |
| MC-JSON-001 | PASS | Closed JSON, fixed error matrix, OpenAPI tests and actual JAR parity. |
| MC-TIME-001 | PASS | Canonical Instant only; existing Duration timeout and interruption semantics. |
| MC-CONFIG-001 | N/A | No property or profile additions. |
| MC-PATTERN-001 | PASS | Existing adapter and operation-specific response codec; no additional transport. |
| MC-SCOPE-001 | PASS | Exact six paths, 949 excluded tracked files and all 25 old SQL bytes unchanged. |
| MC-TEST-001 | PASS | G5/G6/G8 actual RED, G7/G9 GREEN, six XML suites, two packaged byte comparisons. |
| MC-BLOCKER-001 | PASS | Current source gates closed; provider, database and deployment unverified. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Explicit transport capability, endpoint and response decoder names. |
| 2 | PASS | Exact success/error response validation, cap and scope negative tests. |
| 3 | PASS | Decoder projects immutable contract records with closed named fields. |
| 4 | N/A | No managed or business logging component. |
| 5 | PASS | Current Jackson 3/JDK and bounded subscriber. |
| 6 | PASS | Strict input types and local OpenAPI exact paths/security/status schema. |
| 7 | N/A | CL does not add profile/config properties. |
| 9 | PASS | Reuses current Adapter and dedicated decoder; no new generic client. |
| 10 | PASS | Canonical UTC response values and existing request duration. |
| 11 | PASS | Current technical CL profile, no schema/build/dependency/runtime change. |

The fake client does not open a socket. No engine, server, provider, IAM or database was started or verified.
