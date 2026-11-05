# Agent model usage ledger and private diagnostics — Source73

Effective [Spec](../spec/2026-11-05-16-08-agent-model-usage-ledger.md) and [Plan](../plan/2026-11-05-16-50-agent-model-usage-implementation.md). This is the owner-local T4-02 measurement slice. The final Step starts at `854de365374d588197cab9c10609377bf3228e16`.

The current approved CONFIG route binds the exact model and freezes its optional tariff at the existing MODEL reservation. A trusted final and the original budget settlement share the original local transaction. Partial, unknown and stale facts cannot overwrite a measurement; confirmed zero differs from absent usage, unpriced measurements keep their counts, and historical reservations are not backfilled. Off and old overloads do not access V15. The private AS diagnostic runs current GET/LIST authorization before using the exact owner-local AR reader. It exposes no new HTTP, IAM resource or public SDK.

| Gate | Actual result |
| --- | --- |
| Source69 frozen tariff | Two expected assertion RED stages; 17 finite tests passed. |
| Source70 compatible reader | Reflection and typed assertion RED; scoped finite query/off test passed before V15 was added. |
| Source71 immutable V15 | Resource assertion RED and typed readiness RED; two named finite checks passed. PostgreSQL source compiled only. |
| Source72 budget/usage | 2 expected typed failures; 24 finite tests passed. Both PostgreSQL methods compiled only. |
| Source73 G5 private diagnostics | 1 missing-seam RED, 1 typed RED, then 22 passed in four classes, 0 errors/skips. |
| Final clean verify | 88 tests in 12 selected classes (CL 2, AR 48, AS 38), zero failures/errors/skips, 2026-11-05 17:41:02 +08:00. |

The Plan listed `HttpAgentClientTest`; source inspection found that suite starts a JDK HTTP listener. To preserve the user's runtime boundary, the finite selectors use `ClientDependencyBoundaryTest`, `AgentRunHttpTest` and existing AS authorization tests. The final clean run also included the existing mock permission integration suite because AS storage conditions changed. No `PostgresTest`, HTTP listener test, application or E test was selected. Exact selector/report manifest and logs are under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/`.

V15 was reviewed outside the repository and created once. Its 4,433 bytes are identical in source, actual AR JAR and embedded AR JAR in the AS exec artifact: SHA-256 `f5b414af7c1b422697b7ab98823d78fbff0f6327e858a1f5bf816e6b75a74238`, Flyway signed CRC32 `1512602432`. The AS executable contains the exact source YAML (SHA-256 `9cd82d329328744a330e8d4e07d099482868d7f603de6a89dfedef0cb4c79dfa`) with `model-usage-enabled: false` and an empty reviewed route list. All 26 pre-existing SQL files, original POMs and public wire source were preserved. Both PG test methods are present in actual test bytecode; there are no PG execution reports.

The source checks establish safe bounded admission, scope, replay, writer and rollback requests in finite fixtures. Actual PostgreSQL application and catalog normalization, effective roles, current provider cumulative-token semantics, real costs and E/S12 remain separately unverified. No grants, schema changes, feature activation, deployment or application startup occurred.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing AR/AS technical layers and same owning database, DEC-005. |
| MC-REUSE-001 | PASS | Current estimator, Budget/ledger/fence, owner GET/LIST and storage guard. |
| MC-DEP-001 | PASS | Existing JDK/JDBC/Jackson/ADK; POMs and build structure unchanged. |
| MC-NAME-001 | PASS | Semantic tariff/measurement/cursor and explicit private diagnostic. |
| MC-VALID-001 | PASS | Closed trusted final, exact owner, bounded cursor, schema/source/role and config negatives. |
| MC-MODEL-001 | PASS | Immutable value/read projections, closed states and exact null/zero rules. |
| MC-CONVERT-001 | PASS | Explicit checked technical JDBC mapping under DEC-005; no raw payload. |
| MC-LOG-001 | N/A | No new business logger; fixed safe failures only. |
| MC-BEAN-001 | PASS | Existing named storage factory/guard, conditional private façade, exact source/ref checks. |
| MC-UTIL-001 | PASS | Current helpers and JDK numeric/time APIs. |
| MC-JSON-001 | PASS | Existing public JSON preserved; internal framed measurement/cursor only. |
| MC-TIME-001 | PASS | DB observation and persisted microsecond UTC, bounded expiry/window. |
| MC-CONFIG-001 | PASS | Actual default-off artifact, invalid opt-in fails before DS, API-off has no façade. |
| MC-PATTERN-001 | PASS | Existing estimator Strategy and original budget Command/repository transaction. |
| MC-SCOPE-001 | PASS | Exact reviewed Step path sets, 26 old SQL and new immutable V15 unchanged afterward. |
| MC-TEST-001 | PASS | All finite gates, actual artifacts, explicit compile-only I; no unsupported runtime tests. |
| MC-BLOCKER-001 | PASS | Source slice closed; PostgreSQL/provider and full S12 remain independently gated. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Current semantic domain/repository and adapter names. |
| 2 | PASS | Existing request authorization plus exact binding, bounded rows and verified final. |
| 3 | PASS | Immutable values and explicit technical projection. |
| 4 | PASS | Current named factory and fixed diagnostics; no new business logger/DI policy. |
| 5 | PASS | Existing JDK/Spring/JDBC/Jackson/ADK capabilities. |
| 6 | PASS | Existing external wire/enums unchanged; closed internal state/digest. |
| 7 | PASS | Packaged default false and empty reviewed refs, existing profile behavior. |
| 9 | PASS | Current estimator Strategy and owner transaction; no extra executor. |
| 10 | PASS | UTC Instant, DB microseconds and correct source observations. |
| 11 | PASS | Current AR/AS technical profile, one external additive immutable migration; no generator or extra state owner. |

This slice does not implement cross-service correlation, asynchronous Span Links, production metrics/SLA, post-close usage ingest or S12 acceptance.
