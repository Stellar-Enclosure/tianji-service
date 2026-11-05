# Single-instance BPMN migration preflight — Source82

Effective [Spec](../spec/2026-11-05-18-46-bpmn-migration-preflight.md) and [Plan](../plan/2026-11-05-18-53-bpmn-migration-preflight.md), source baseline `695c9115921ba3068db445526b7bf51be87a350b`. Their strict validators passed, with SHA-256 `dc5bda0f6ec4ec472791c58194fd15faffc267cab8e8c972bebc3733b99dfc45` and `ca4cdf8791e865b726944b76324d20ba5c26aa91194e39d7def69543bef149b9`.

Process Server exposes an internal, default-off diagnostic. It checks both existing permissions, the exact START owner, two persisted and verified single-BPMN releases, and one identical plain timer. It rejects unsupported shapes or inconsistent/insufficient native evidence before the pinned Flowable 8 single-instance `validateMigration` call. The positive result means structural validation only. There is no migration command, HTTP endpoint, new authority, model deployment, or activation. The technical Specification narrows model and native evidence; the existing native adapter holds the bound read and the release lookup joins it directly.

| Gate | Actual result |
| --- | --- |
| M1 | 1 assertion failure: preflight class absent. |
| M2 | 1 assertion failure: typed inert service cannot return the expected owner-bound result. |
| M3 | 1 assertion failure: typed inert validator derives no facts for the supported timer. |
| M4 | 1 assertion failure: packaged-source flag absent rather than explicitly false. |
| G1 | 114 passed in 9 selected Process suites, zero failures/errors/skips. |
| P1 | Clean JDK 25 permission-producer build: 126 passed in 10 suites including ProcessPermissionIntegrationTest, zero failures/errors/skips, finished 19:24:07 +08:00. |
| S1 | Strict effective docs, exact 12 paths, original 27 classpath SQL bytes and all excluded tracked files checked against source lock. |

Each M gate had zero errors/skips. Exact selectors are in the Plan. The real exec archive SHA-256 was `a2d19814011957d53aa72ae4897c529214de11cc76e0502e18b2ece5e7bcc1e8`. It contained one new internal service, one application YAML matching source bytes and containing the explicit false flag, and one root PaC manifest (`a6e8dc9a9edece682dbcf04b0584aa09dd1337b78f953bb5bfaa86b38c0fb4e9`). The manifest matched the existing Process manifest outside build artifact metadata. No new controller was packaged. Exact reports and artifact inspection are retained under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/`.

| Manual check | Status | Actual source evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Process owner only; current API and native storage boundary. |
| MC-REUSE-001 | PASS | Existing permission, actor, START, release digest, model verification and native adapter. |
| MC-DEP-001 | PASS | Existing Flowable 8.0.0, Spring JDBC, JDK and BPMN XML converter; POM untouched. |
| MC-NAME-001 | PASS | Closed query, status and narrow internal diagnostic. |
| MC-VALID-001 | PASS | Existing command ID validators, exact owner and pin, graph/evidence rejection tests. |
| MC-MODEL-001 | PASS | Immutable typed query, diagnostics and narrow scalar facts. |
| MC-CONVERT-001 | PASS | Existing typed model converter; exact bounded SQL projections. |
| MC-LOG-001 | N/A | No added logging or disclosure of native messages. |
| MC-BEAN-001 | PASS | Named default-off factory using existing qualified owner beans. |
| MC-UTIL-001 | PASS | Existing Flowable helpers, release digest, JDK time and JDBC. |
| MC-JSON-001 | PASS | Existing wire unchanged; exact pinned ordinary timer configuration only. |
| MC-TIME-001 | PASS | Fixed UTC comparison and literal duration semantics; real engine clock alignment pending I. |
| MC-CONFIG-001 | PASS | False in the actual archive, prior settings preserved. |
| MC-PATTERN-001 | PASS | Closed shape Specification and existing native Adapter. |
| MC-SCOPE-001 | PASS | Exact 12 files; no new SQL, dependency, permission or HTTP mapping. |
| MC-TEST-001 | PASS | Four assertion REDs and targeted green/package checks. |
| MC-BLOCKER-001 | PASS | Finite/static boundary recorded; runtime transaction, clock and read budget are prerequisites to enabling. |

| Rule | Status | Independent source evidence |
| --- | --- | --- |
| 1 | PASS | Owner-local semantic query and immutable diagnostics. |
| 2 | PASS | Validated exact input, same trusted actor and bounded evidence. |
| 3 | PASS | Existing immutable and typed scalar conventions. |
| 4 | PASS | One named qualified factory in existing PS configuration; no new logger. |
| 5 | PASS | Existing JDK, JDBC and Flowable helpers. |
| 6 | PASS | No HTTP or shared serialization change; canonical pinned handler check. |
| 7 | PASS | Explicit false and preserved profile defaults. |
| 9 | PASS | Closed graph/occupancy Specification with direct read orchestration. |
| 10 | PASS | Explicit UTC, typed Instant/Duration, no fallback to host zone. |
| 11 | PASS | Existing owner and schema profile; no new layer, native writer or migration. |

I is pending. Before enablement, verify the pinned real Flowable Spring command joins the same physical ProcessDB read-only repeatable-read transaction, that native UTC and job evidence match actual engine storage, and that no native writes and acceptable read/index budgets are observed. No application, Flowable engine, DB, or listener was started. Governance approval, actual instance migration and activation remain separate.
