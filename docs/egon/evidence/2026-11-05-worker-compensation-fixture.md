# Transactional Worker compensation receipt fixture — Source55 / compensation Plan Step2

- Baseline: `a1ab50ab8460ea1bac29e11061647e4bf77c1d9e`, Source54 committed and verified clean before this Step; 937 tracked files, 25 immutable SQL files.
- Effective [Spec](../spec/2026-11-05-12-09-worker-compensation-fixture.md) SHA `de12e22b3aa322da4f0d7128aa3376b4d6c7aa76800502a7ee9c414556b58f0d` and [Plan](../plan/2026-11-05-12-44-worker-compensation-fixture.md) SHA `039d43b2806da6c45e084e1459d1d664c5d1300d37d0d7f3d8416ba51fd0458d`. Root full design/Plan review and exact fresh lock; user master goal authorizes source, user/runtime acceptance is not claimed.
- The prior [model Step](2026-11-05-worker-compensation-model.md) supplies exact-one ordinary Worker metadata and all-edge normal-success proof. This Step changes six test/documentation paths, with no production schema/POM/API/Worker main modification.

The technical fixture uses the existing Handler Strategy and Store-owned transaction. Both handlers reject wrong BPMN/case/enrollment/topic/schema/effect inputs before any fixture JDBC or connection access, then check the actual connection already bound to the supplied JdbcTemplate for READ_COMMITTED. The original path locks its receipt when present; after an empty lookup, it locks the object and rereads the immutable receipt without taking a reverse receipt lock. Only a still-absent matching operation reserves once. Undo locks the original, derives the stable key from that persisted ID and replays a prior compensation before any fresh object version lookup. A first undo restores the original reserved amount and increments the version again. Checked arithmetic, exact CAS and insert failures escape the handler; the Store remains responsible for local commit/rollback and technical receipts.

`recorded_at` is a BusinessDB write clock read as OffsetDateTime→Instant, not a commit instant or cross-database ordering proof. The post-lock deadline guard compares that database's current clock with the existing Store-supplied deadline. Tests do not establish global clock skew or a global hard deadline.

## Actual finite verification

| Gate | Actual result | Persistent log |
| --- | --- | --- |
| G7 fixture presence RED | 1 expected assertion failure, 0 errors/skips; 18.897 s, 2026-11-05 13:15:43 +08:00. | `step55-g7-presence-red.log` |
| Fixture A compile | 1 passed, 0 failures/errors/skips; 17.731 s, 13:17:24 +08:00. | `step55-g8-fixture-a.log` |
| G8 typed RED | 15 tests, 13 expected stub-related assertion failures, 0 errors/skips; 19.008 s, 13:18:40 +08:00. Presence and Default enrollment tests pass. | `step55-g8-typed-red.log` |
| G9 fixture GREEN | 15 passed, 0 failures/errors/skips; 22.006 s, 13:21:58 +08:00. | `step55-g9-fixture-green.log` |
| G10 exact finite regression | 177 passed in 12 named classes (PS165, W12), 0 failures/errors/skips; 26.332 s, 13:24:08 +08:00. Both future PG scenario and caller class files compiled. | `step55-g10-regression.log` |

Exact G10 selector: `CompensationBusinessFixtureTest,WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,RemoteWorkerPollerTest,WorkerDependencyBoundaryTest`. JDK25, offline source-reactor, persistent credential-free task settings, ADK capture disabled, OTel exporters none, `-DskipITs=true`. All current XML reports and the compile proof are preserved under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/compensation/`. PostgreSQL classes/listener/provider tests were never selected, no opt-in was set, and no engine/app/database/browser was started.

## Source-only future scenario boundary

The guarded three-argument hook is in the existing PG caller's actual app/JdbcTemplate/runtime-role scope. The helper requires distinct actual isolated loopback Process and Business databases, verifies a limited non-owner role and an absent fixture schema, and tracks ownership for cleanup. It creates three test business tables and consumes unchanged Worker V1/V2 migrations. DEC-711 pairs the necessary original receipt `UPDATE(recorded_at)` column grant with an always-enabled rejecting UPDATE trigger; the authored future probe checks a successful row-lock SELECT and actual SQLSTATE23514, with rollback and unchanged value. The [PostgreSQL locking privilege](https://www.postgresql.org/docs/current/sql-select.html) and [trigger event](https://www.postgresql.org/docs/current/sql-createtrigger.html) rules were checked, but the role/trigger statements have not run.

The future scenario separates native accepted ACK from original leave/subscription, downstream Error, undo dispatch, persisted original/undo and technical receipts, and terminal state. The ACK-loss path waits for natural native lease expiry/reset and verifies same occurrence/key with a new token, without adding direct ACT-table writes. Other probes use distinct local-only technical tasks for RC lock visibility, replay, injected technical receipt insert failure, object drift and new Store/DataSource reads. No synthetic task is acknowledged to PS. Source compilation and ordered mocks do not prove actual PostgreSQL isolation/atomicity, engine liveness or independent-process restart. PG-701, S10/S11 and production acceptance remain unexecuted.

## Requirement conformance

| Requirement | Evidence |
| --- | --- |
| REQ-708 | Scope/enrollment/topic/schema/effect mismatch matrix verifies zero fixture JDBC/DataSource/connection access. |
| REQ-709 | Actual bound ConnectionHolder API; finite active/same-resource/RC failure probes and no borrowed connection. |
| REQ-710 | Original CAS/insert/result order with retained Store contract; real local rollback only authored future scenario. |
| REQ-711 | Empty lookup/object lock/clock/plain reread replay; no reverse row lock or second reserve. |
| REQ-712 | Exact original receipt derived semantic digest; no technical task or caller-selected action key. |
| REQ-713 | Prior compensation before object lookup and stable new-technical-task replay. |
| REQ-714 | Restore beforeReserved, version+1, exact CAS and safe propagation of insert/drift/overflow failures. |
| REQ-715 | Microsecond/equality deadline tests and typed OffsetDateTime mapping; explicit clock limitations. |
| REQ-716 | Actual compile of separately observed future native and BusinessDB facts; no ACK/end inference. |
| REQ-717 | Three test-only business tables and owned privilege guard, no new production migration, all 25 SQL unchanged. |
| REQ-718 | Exact finite suites, source-only PG compilation and no runtime setup or acceptance claim. |

REQ-701–707 remain covered by Source54 and rerun in G10. Scope audit verifies exact six paths, 935 excluded tracked hashes and all 25 old SQL bytes; combined slice 13 paths.

## Blocking Manual Checks

| Check | Applicability | Status | Actual evidence |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing PS test-only helper in current technical vertical; no new main layer. |
| MC-REUSE-001 | Applicable | PASS | Existing Worker Strategy/Store/JDBC/ValidationUtils/framed digest and current opt-in harness reused. |
| MC-DEP-001 | Applicable | PASS | All POMs unchanged; no new dependency or logger. |
| MC-NAME-001 | Applicable | PASS | Semantic fixture/receipt/value and scenario names. |
| MC-VALID-001 | Applicable | PASS | Default metadata, scope-before-JDBC, bound RC, persisted binding and finite negative cases. |
| MC-MODEL-001 | Applicable | PASS | Immutable value/row records, fixed schema/keys and no production entity. |
| MC-CONVERT-001 | Not applicable | N/A | Same-package row mapping only; no cross-layer PO/DTO converter. |
| MC-LOG-001 | Not applicable | N/A | No new business class/logger under exact test-helper DEC710 scope. |
| MC-BEAN-001 | Not applicable | N/A | No new managed bean/injection; direct finite test values only. |
| MC-UTIL-001 | Applicable | PASS | Existing JDK/Spring/Common helper/digest APIs. |
| MC-JSON-001 | Not applicable | N/A | Existing bounded empty output result and unchanged external JSON/enum contracts. |
| MC-TIME-001 | Applicable | PASS | Typed OffsetDateTime→Instant, DB write time and deadline checks, finite microsecond/equality tests. |
| MC-CONFIG-001 | Not applicable | N/A | Existing opt-in property names only; no config/profile changes. |
| MC-PATTERN-001 | Applicable | PASS | Existing Handler Strategy and Store transaction lifecycle; no second executor/TM in handlers. |
| MC-SCOPE-001 | Applicable | PASS | Exact 6 / combined 13 / 935 excluded / 25 immutable SQL; production main unchanged. |
| MC-TEST-001 | Applicable | PASS | Observed G7/G8 RED, A/G9/G10 GREEN, 12 XML reports and actual compiled PG source only. |
| MC-BLOCKER-001 | Applicable | PASS | All current-Step source checks closed; live acceptance and recovery/T3-12/T4 remain pending. |

## Literal Rule Gate

| Rule | Applicability | Status | Actual independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic immutable values and test-only Strategy names. |
| Rule 2 | Applicable | PASS | Actual Default validation and all fixture-boundary guards. |
| Rule 3 | Applicable | PASS | Immutable records/defensive values; no entity conversion. |
| Rule 4 | Not applicable | N/A | Exact DEC710 test-helper boundary; no business Bean/logger/dependency. |
| Rule 5 | Applicable | PASS | Existing JDK/Spring/Flowable/Common utilities only. |
| Rule 6 | Not applicable | N/A | No wire JSON/enum changes; existing DTO output. |
| Rule 7 | Not applicable | N/A | No application/profile key change. |
| Rule 9 | Applicable | PASS | Reuse existing Strategy and Store owner transaction lifecycle. |
| Rule 10 | Applicable | PASS | Actual Instant/OffsetDateTime and deadline tests; no java.util time or commit-time claim. |
| Rule 11 | Applicable | PASS | Approved technical profile, no production schema/module/MP/DDL runner/generator or business Event. |

SourceCompensationSliceComplete=true; RuntimeUnverified=true; ProductionAccepted=false. Governed failure/deadletter recovery and remaining master tasks remain required.
