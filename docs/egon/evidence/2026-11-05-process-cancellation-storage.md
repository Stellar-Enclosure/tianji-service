# Process cancellation storage foundation evidence

## Step 45 / Plan Step 3 — Verified for focused commit

- Step44 committed `167e2a20d26f2e9f7743921a8d71c2c76e26ea00`, exact six paths, clean worktree; final51 selected tests and actual offline verify/JAR proof passed before Step3 edits.
- Baseline886 tracked file SHA values `/tmp/tianji-step45-baseline.json`; exact eleven owned paths `/tmp/tianji-step45-paths.json`; all17 Manual Check/ten Literal Rule obligations `/tmp/tianji-step45-lock.json`.
- All24 existing SQL immutable; Process V8 latest and V9 absent at lock. Root reviewed a complete draft outside classpath and exclusively created the sole final V9 once; no placeholder, old migration edit or database execution.
- Root owns SQL/verifier/tests/HumanTask verifier test/evidence; receipt worker owns new immutable receipt store/test and generic command-store extension; dispatch worker owns only existing AgentDispatchStore/test. Test-first reflection/resource assertions precede production; Root alone runs offline exact-selector Maven and commits.
- The approved existing Process technical vertical uses plain JDBC/shared local transactions, externally provisioned Flyway history and immutable contract values. This is not native Light/Web/Service CRUD scaffolding; the verified CREATE-only generator does not support this profile and is not run. No dependency, generated catalog, MP migration or codegen journal is introduced. User's existing Flyway bytes/history remain protected.
- Source-only foundation: no Controller, feature admission, permission/config change, native ACT write, remote Agent call, application/listener/database/browser/GUI startup or provider/runtime acceptance.

## Test-first and frozen migration identity

- Initial presence/resource RED: `/tmp/tianji-process-cancellation-storage-red.log`, 16 tests / 4 expected assertion failures / 12 passed / zero errors or skips; 19.862 s, finished 2026-11-05T02:59:52+08:00.
- Dispatch typed behavior RED: `/tmp/tianji-process-cancellation-dispatch-red2.log`, 12 tests / 7 expected assertion failures / 5 passed / zero errors or skips; 20.031 s, finished 03:08:18+08:00.
- Receipt typed behavior RED after correcting compile-only fixture defects: `/tmp/tianji-process-cancellation-receipt-red3.log`, 21 tests / 8 expected receipt assertion failures / 13 passed / zero errors or skips; 18.493 s, finished 03:20:55+08:00. Dispatch's 12 tests passed. No compilation/fixture failure is treated as behavior RED.
- V9 was reviewed outside classpath and created once with exclusive creation. Its final 4,935 bytes/81 lines have SHA-256 `dd07ea808cb19f85e983f99a38950add4229208e66dc3c10a60970c1ffc0711b`. **Flyway line-normalized checksum is `-178365142`**; raw-file CRC32 `-1480618420` is a different value and is not used as the history checksum. The original scratch metadata/fixtures mixed these values; those records were corrected without editing SQL bytes.
- The migration adds one immutable 26-column audit table, PK and three exact-source FKs, one accepted-only uniqueness index and one open-dispatch scope index. Native finishedAt and DB decidedAt are independent microsecond instants with no cross-clock ordering constraint. It adds no grants/backfill/old-table ALTER/native ACT write.
- Intermediate compiler/Mockito vararg capture errors were investigated and corrected as fixture defects, never counted as RED or acceptance. Final finite suites below remain required before commit.

## Final finite verification and implementation

| Log | Actual result | Meaning |
| --- | --- | --- |
| `/tmp/tianji-process-cancellation-verifier-red3.log` | 15 tests, 6 expected assertion failures / 9 passed, zero errors/skips; 18.530 s, finished 03:28:28+08:00 | Typed catalog/history/least-privilege tests fail against the empty verifier and old V8-only detector before implementation. |
| `/tmp/tianji-process-cancellation-storage-green1.log` | 52 passed, zero failures/errors/skips; BUILD SUCCESS 21.596 s, finished 03:29:12+08:00 | First complete seven-class regression, followed by independent source review. |
| `/tmp/tianji-process-cancellation-storage-hardening-red2.log` | 16 tests, 3 expected assertion failures / 13 passed, zero errors/skips; 18.139 s, finished 03:32:01+08:00 | New tests expose foreign-affinity/hash precedence, history query shape and column-privilege guard gaps before correction. Exact exception matching is required because ProcessConflictException extends IllegalStateException. |
| `/tmp/tianji-process-cancellation-storage-green2.log` | **54 passed**, zero failures/errors/skips; BUILD SUCCESS 22.157 s, finished 03:32:55+08:00 | Final seven-class storage and existing Case/task regression. |

Final selectors were ProcessCancellationSqlTest1, ProcessApiSchemaVerifierTest6, ProcessCancellationReceiptStoreTest10, AgentDispatchStoreTest12, HumanTaskStorageVerifierTest9, CaseStorageConfigurationTest9 and CaseStartProvenanceStoreTest7. Commands used the frozen Plan's JDK25, isolated local Maven settings, offline source reactor, skipITs=true, finite explicit selectors and disabled OTel exporters. No PostgreSQL, HTTP listener or native engine integration selector was executed.

The generic receipt extension adds only PROCESS_CANCEL and exact-owner APPLIED START locking. The new detailed store rechecks generic PENDING cancel, APPLIED START, owner/source/hash/release/correlation and released BPMN definition before one immutable 26-column insertion. Its plain LEFT JOIN replay path detects incomplete APPLIED state, duplicates, foreign affinity and legitimate hash conflict without locking or mutating audit rows. Entry values are immutable/redacted; native and DB result clocks preserve either ordering and DB-bound values require microsecond precision.

Dispatch closure requires the caller's same-source active transaction. It discovers at most257 open tenant/BPMN/instance candidates under deterministic row locks, reuses existing Seed/fingerprint/idempotency validation, validates every candidate before any write, rejects overflow/foreign/malformed/duplicate rows, then updates only the captured full stored SERVICE keys through one VALUES batch. It checks the exact affected count, counts existing CLOSING, preserves CLOSED and never borrows the process owner's identity or sweeps phantom rows. Remote Agent calls remain outside this primitive.

The verifier pins packaged V8/V9 SHA and Flyway checksums, accepts only no later migration/exact V8/exact V8 then V9 in actual installed_rank order, and rejects unknown/null-version/repeatable/failed/duplicate/wrong-script/checksum histories. Cancellation reuses the existing Agent dispatch schema verifier, checks exact V9 columns (including both BOOLEANs), grouped checks, PK/FKs and indexes; checks only immutable V8 provenance and release model authority; and requires provenance SELECT with optional INSERT, audit SELECT+INSERT and dispatch SELECT+UPDATE. It checks schema usage, nonowner/non-superuser/no-bypass-RLS, persistent nonpartitioned/no-RLS tables and existing trigger safety, denies dangerous table privileges and additionally denies column-level REFERENCES/immutable UPDATE. Full Case activation retains its existing three-table write-grant semantics. Cancellation never calls verifyCase or constructs CaseStartProvenanceStore.

V9 catalog fixtures explicitly list all26 native columns, eleven check expressions, four keys and expected indexes independently of the production descriptor parser. Strict Spec/Plan validators and git diff --check passed. The final scope is exactly eleven paths; all881 excluded tracked files (including all24 old SQL, contracts, POMs and prior evidence) match baseline SHA-256. No permission/config/Bean/controller/route/frontend change occurs in this Step.

## Final Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Exact PS execution/bridge/verifier vertical; eleven paths | Existing plain-JDBC technical profile preserved | Approved Spec/Plan profile retained |
| MC-REUSE-001 | Applicable | PASS | Generic receipts/TaskOrigin/Seed/Case descriptors/current schema verifier reused | No parallel store framework or lifecycle | None |
| MC-DEP-001 | Applicable | PASS | All POM/dependency SHA unchanged | No dependency or generator introduced | None |
| MC-NAME-001 | Applicable | PASS | ProcessCancellationReceiptStore/Entry/ProcessClosureScope and existing package suffixes | Behavior classes and immutable values fit current technical profile | None |
| MC-VALID-001 | Applicable | PASS | Typed RED/final54 tests; source, authority, hash, schema and TX gates | Real boundary validation and fail-closed reads/writes | None |
| MC-MODEL-001 | Applicable | PASS | Immutable redacted Entry, existing immutable TaskOrigin/Seed | No mutable business entity or duplicated public carrier | Record exemption applies to values |
| MC-CONVERT-001 | Not applicable | N/A | JDBC row mapping into immutable receipt values | No separate Java semantic carrier or MapStruct conversion boundary | None |
| MC-LOG-001 | Applicable | PASS | Cause-free safe exceptions; Entry redaction tests | No reason/subject/hash payload logging | No new business logger required |
| MC-BEAN-001 | Not applicable | N/A | No Spring Bean/DI/config change | Storage foundation only | Explicit existing constructor profile retained |
| MC-UTIL-001 | Applicable | PASS | JDK/current JDBC utilities and existing signature/checksum helpers | No new utility dependency | None |
| MC-JSON-001 | Applicable | PASS | Existing dispatch Jackson/Seed path unchanged | No alternate JSON stack or enum ordinal | None |
| MC-TIME-001 | Applicable | PASS | Both native/DB clock orders, microsecond DB fields, no chronology constraint | Native finishedAt and DB decidedAt remain independent | Runtime clock behavior unverified |
| MC-CONFIG-001 | Not applicable | N/A | YAML/profiles/permissions unchanged | Default-off API wiring belongs to Step4 | None |
| MC-PATTERN-001 | Applicable | PASS | Existing Command/receipt participant and bounded exact-key lifecycle reused | No new hierarchy/registry; one detailed immutable audit has a concrete distinct responsibility | None |
| MC-SCOPE-001 | Applicable | PASS | Eleven-path lock,881 excluded SHA, old24SQL and V9 frozen hash | No unrelated change | None |
| MC-TEST-001 | Applicable | PASS | Genuine assertion RED and final54 finite tests, independent review | Compiler/fixture failures excluded from RED claims | PG/native concurrency RuntimeUnverified |
| MC-BLOCKER-001 | Applicable | PASS | Strict frozen Spec/Plan and all source gates | No remaining source blocker for this storage Step | Step4 and broader roadmap remain required |

## Literal Rules

| Rule | Applicability | Status | Evidence / approved disposition |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Existing behavior suffixes and immutable nested value names; no new business carrier/layer |
| Rule 2 | Applicable | PASS | Store constructors/Entry/SQL authority/caller TX/schema/role validation; no HTTP or new Spring validation layer in this Step |
| Rule 3 | Applicable | PASS | Entry/Authority/Scope are immutable values with explicit invariants; no mutable entity or semantic converter introduced |
| Rule 4 | Applicable | PASS | Existing technical explicit constructor/factory profile retained; no new Spring-managed class or raw sensitive logging |
| Rule 5 | Applicable | PASS | JDK/current Spring JDBC helpers only; existing utility stack unchanged |
| Rule 6 | Applicable | PASS | Existing Jackson unchanged; persisted enums use existing explicit name strings, never ordinal |
| Rule 7 | Not applicable | N/A | No environment/profile configuration change |
| Rule 9 | Applicable | PASS | Existing Command/receipt and dispatch lifecycle extension addresses atomic audit/exact-key variation without speculative abstraction |
| Rule 10 | Applicable | PASS | Public/native value time uses java.time.Instant; java.sql.Timestamp only adapts JDBC; no native/DB ordering |
| Rule 11 | Applicable | PASS | Frozen Spec/Plan explicitly preserve the existing PS technical JDBC/TransactionTemplate/external-Flyway profile; no business CRUD/soft-delete/entity/event layer or MP migration is introduced; new sole V9 preserves all existing migration bytes |

This is source-complete storage foundation for the first cancellation slice. It does not expose or enable an API, apply a migration, prove PostgreSQL locks/role operator behavior/native deletion/Agent closure, register permissions, grant access, or complete T3-10. Step4 default-off provider behavior and all PG/native/IAM/deployment/browser acceptance remain Required/RuntimeUnverified; compensation, dead-letter and manual recovery remain later roadmap requirements.
