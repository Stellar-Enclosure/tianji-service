# Worker recovery contract — Source56 / recovery Plan Step 1

Baseline `415a2814cf4a025bd868543c48b22e32e54e41ff`, clean after the compensation fixture. Effective [Spec](../spec/2026-11-05-13-41-process-worker-recovery.md) SHA `bae688fe7a85e86dc47e07127aeada60adc2b5bcf9494f920b6378ec3c0b588b` and [Plan](../plan/2026-11-05-14-27-process-worker-recovery.md) SHA `94f2dced9f6ced2c02349cebf2ae0925a7b1ea9edd5c4a73c9ed8919f17b6b53`.

The independent `ProcessWorkerRecoveryClient` capability defines the two approved routes and exact request/response shapes. It does not extend or change existing Process or Control contracts. Immutable records reuse the existing safe orchestration validation, keep the exact recovery reason while redacting its display, enforce closed row dispositions and result affinity, and require strictly increasing unsigned UTF-8 job IDs in an immutable, bounded observation. This Step has no provider, transport or database behavior.

| Gate | Actual result |
| --- | --- |
| G1 shape RED | 1 expected assertion failure (capability absent), 0 errors/skips. |
| Phase A compile | Passed on JDK 25 with the five declared contract types. |
| G2 shape GREEN | 1 passed, 0 errors/skips. |
| G3 behavior RED | 5 tests, 4 expected assertion failures for the missing constructor validation and list behavior, 0 errors/skips. |
| G4 exact finite regression | 31 passed across 3 selected classes, 0 failures/errors/skips; 6.122 s, 2026-11-05 14:48:51 +08:00. |

Offline `./mvnw -pl :stellar-enclosure-tianji-contract -DskipITs=true -DskipTests=false` with JDK 25 and the credential-free task settings. G4 selector: `ProcessWorkerRecoveryContractTest,ProcessControlContractTest,OrchestrationContractTest`. Logs and Surefire XML are preserved under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/recovery/`. Exact nine additions, 941 excluded tracked hashes and all 25 old SQL bytes verified.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Five additions in the existing C orchestration package, independent of Process/Control inheritance. |
| MC-REUSE-001 | PASS | Existing `GetProcessInstanceQuery`, `OrchestrationChecks` and current contract test style. |
| MC-DEP-001 | PASS | JDK only in production; existing JUnit, all POM bytes unchanged. |
| MC-NAME-001 | PASS | Named Worker recovery capability, action/result and DTOs; closed nested enum names. |
| MC-VALID-001 | PASS | Constructor checks cover exact ID/reason/version, row/status affinity, immutable sorted observation, and scheduled result. |
| MC-MODEL-001 | PASS | Four immutable records, exact components and defensive list copy. |
| MC-CONVERT-001 | N/A | No persistence/DTO conversion; query constructor reused for exact path validation. |
| MC-LOG-001 | N/A | No business logger or logging path; command display explicitly redacted. |
| MC-BEAN-001 | N/A | No managed bean, provider or injection. |
| MC-UTIL-001 | PASS | Existing contract validators and JDK UTF-8/unsigned byte comparison. |
| MC-JSON-001 | PASS | Reflection locks exact record names/types/order and enum wire tokens; transport handling belongs to Step 2. |
| MC-TIME-001 | PASS | Required typed `Instant` observation/decision fields and fixed UTC test inputs. |
| MC-CONFIG-001 | N/A | No application or build configuration. |
| MC-PATTERN-001 | PASS | Separate capability follows existing Control boundary, with direct record invariants and no new extension hierarchy. |
| MC-SCOPE-001 | PASS | Exact nine additions; current API files, POMs and all 25 SQL bytes unchanged. |
| MC-TEST-001 | PASS | Real shape/behavior RED then GREEN; existing Control and orchestration regression. |
| MC-BLOCKER-001 | PASS | All current-Step source checks closed. Runtime and next-Step behavior remain unverified. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Public DTO, command, result and nested enums reflect the existing orchestration vocabulary. |
| 2 | PASS | Closed validation at each record boundary, negative and exact Unicode cases. |
| 3 | PASS | Record identity and closed state combinations; no entity or cross-layer mapping. |
| 4 | N/A | Contract values/interface only, no application Bean or business logging. |
| 5 | PASS | Existing validation reuse and standard JDK APIs; no extra utility library. |
| 6 | PASS | Exact named record fields, list order and closed enum values; no serializer changes. |
| 7 | N/A | No profile, config keys or config binder in C. |
| 9 | PASS | Direct immutable record validation, existing separate capability pattern. |
| 10 | PASS | `Instant` only, with UTC fixtures. |
| 11 | PASS | Existing technical C profile; no new layer, dependency, generator, schema or runtime activation. |

Provider, IAM, PostgreSQL and native recovery were not executed. The next recovery Steps and the remaining master-plan work are open.
