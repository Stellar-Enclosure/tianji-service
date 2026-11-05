# Worker recovery audit and schema verification — Source58 / recovery Plan Step 3

Baseline `e029a88ac371f60238795c9b3dbe54bfda97ac5c`; [Spec](../spec/2026-11-05-13-41-process-worker-recovery.md), [Plan](../plan/2026-11-05-14-27-process-worker-recovery.md).

The JDBC participant writes the exact accepted recovery attribution and result under the existing command and START authority. It has no update/delete path. The targeted source-global job conflict returns false, while unrelated integrity failures propagate. Replay rereads the scoped generic/detail/START/definition facts and checks exact identity and hash. Existing readiness accepts only known successful additive prefixes; recovery has independent V10, catalog and runtime role checks, including its native column/PK/privilege requirements.

V10 was reviewed outside the repository, then created once with no-clobber and verified using `cmp`. SHA-256 `5a3511659af4c44b2455e55e15d373d6c643fea29ed513b658c59595b5ef6dc9`, Flyway line CRC32 `186112865`; 32 required columns, 3 existing-target foreign keys, 11 named checks and one unconditional job-ID unique index. All 25 pre-existing main SQL files equal the original base. The reviewed bytes do not apply a grant, update native records or start a migration.

| Gate | Actual result |
| --- | --- |
| G10 missing storage RED | 2 expected assertion failures for absent V10 and the older two-migration limit, 0 errors/skips. |
| G11 immutable SQL/resource | Independent byte/shape/hash check, exclusive copy and byte equality; 1 test passed. |
| G12 typed RED | 4 expected assertion failures against missing Java behavior, 0 errors/skips. |
| G13 exact finite regression | 62 passed in 5 named PS classes, 0 failures/errors/skips; finished 2026-11-05 15:02:49 +08:00. |

G13 selector: `ProcessWorkerRecoveryReceiptStoreTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,ProcessControlServiceTest,ProcessControlNativeAccessTest`. Offline JDK25 source reactor, credential-free task settings, `-DskipITs=true`, OTel exporters and ADK message capture disabled. Reports, logs and exact SQL review under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/recovery/`. Existing Case/task/cancel readiness probes on known V10 history do not query the new audit catalog or require recovery grants.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS JDBC and additive schema verification; audit joined to existing local authority. |
| MC-REUSE-001 | PASS | Existing command lifecycle, START scope, schema descriptor and role verification mechanisms. |
| MC-DEP-001 | PASS | All POMs unchanged; existing JdbcTemplate and test tools. |
| MC-NAME-001 | PASS | Explicit audit, command kind, typed Entry and immutable migration names. |
| MC-VALID-001 | PASS | Exact owner, state, correlation, result, release and digest predicates; catalog and privilege fixtures. |
| MC-MODEL-001 | PASS | Immutable Entry and readback records with closed command/result/policy constants. |
| MC-CONVERT-001 | PASS | Explicit exact JDBC mapping and typed contract projection; no entity lifecycle. |
| MC-LOG-001 | N/A | Existing technical infrastructure profile; no business logging. |
| MC-BEAN-001 | N/A | Direct participants; conditional managed factories are Step 5. |
| MC-UTIL-001 | PASS | Existing JDBC schema utilities, JDK exact hashes and timestamps. |
| MC-JSON-001 | PASS | Exact typed result is reconstructed from immutable columns; existing generic decision JSON remains null. |
| MC-TIME-001 | PASS | DB decision clock and exact microsecond failure/decision Instant round trips. |
| MC-CONFIG-001 | N/A | No runtime config or grant changes. |
| MC-PATTERN-001 | PASS | Existing transaction participant/receipt pattern; V10 is an additive audit detail. |
| MC-SCOPE-001 | PASS | Exact seven paths, 952 excluded tracked hashes, 25 unchanged SQL and immutable new V10. |
| MC-TEST-001 | PASS | G10/G12 assertion RED, G11/G13 GREEN and finite reports preserved. |
| MC-BLOCKER-001 | PASS | Current finite source gates closed; live catalog, privileges and atomic restore remain unverified. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Explicit PS schema/audit and immutable value naming. |
| 2 | PASS | Bound transaction, authority, drift and targeted insert result guards. |
| 3 | PASS | Immutable audit Entry and deliberate row mapping. |
| 4 | N/A | Technical JDBC verification; no managed business class/logger. |
| 5 | PASS | JDK and existing Spring JDBC/schema verification utilities. |
| 6 | PASS | Typed scheduling result and fixed policy; generic decision JSON is checked absent. |
| 7 | N/A | No application profile or property changed. |
| 9 | PASS | Existing receipt store and separate schema readiness verification, no redundant runtime abstraction. |
| 10 | PASS | Instant/microsecond checks and timestamp binds, no commit-time assumption. |
| 11 | PASS | Existing plain JDBC/Flyway profile, externally applied additive DDL; no schema edits beyond V10. |

Deploy a compatible binary before separately applying V10. PostgreSQL catalog deparsing, effective grants, transaction and native rollback remain runtime-unverified. No database, engine or application started.
