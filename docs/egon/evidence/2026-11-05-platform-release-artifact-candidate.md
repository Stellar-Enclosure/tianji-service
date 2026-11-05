# T4-01A1 offline release candidate verification — Source68

Effective [Spec](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md) and [Plan](../plan/2026-11-05-16-33-platform-release-artifact-candidate.md). This is the independent offline artifact stage. Baseline `6bcf7204733088610e3eccd931a009b8f2fa2a92` retains the exact old Process manifest frame and public release behavior.

The explicit PS executable entry verifies a closed scope and local artifacts, validates model and Agent link structure using existing contracts, and emits one deterministic `ARTIFACT_CHECKED_ONLY` JSON line. The Agent owner digest remains `DECLARED`; it is not recomputed by copying an AR-private codec. Package evidence means the actual raw ZIP entry and archive bytes matched, not that permissions were registered or authority is current. No state, configuration, API or persistence migration is added.

| Gate | Actual result |
| --- | --- |
| D1/D2 prior digest commit | Expected package-seam RED; 22 passed after sharing the exact frame, including old two-model and real linked BPMN/CMMN publication goldens. |
| C1 absent CLI RED | 1 expected assertion failure, 0 errors/skips. |
| C2 typed stub RED | 1 expected valid fixture assertion failure, 0 errors/skips. |
| C3 finite GREEN | 36 passed in four selected classes, 0 failures/errors/skips. |
| C4 clean verify and packaged command | 64 passed in six named classes (AR 8, AS 16, PS 40), 0 failures/errors/skips; 2026-11-05 16:57:41 +08:00. Both actual packaged permissions verified; alternate main positive, repeat, and wrong-hash rejection passed. |

C4 used the exact Plan selector, offline JDK25 source reactor, existing permission-producer profile, local uncommitted artifact label, and `-DskipITs=true`. Both actual executable JARs retain their original service Start-Class. Only the explicit alternate offline main was invoked. The independent proof recalculated the canonical candidate digest, reproduced byte-identical output with `--expect-sha256`, and observed exit 65 with empty stdout for an incorrect digest.

| Local proof | SHA-256 |
| --- | --- |
| Candidate digest (domain-framed canonical payload) | `4a3dfdf0125caba3366d5d07c20d1cffb16d05d636224688be389019e4240c7e` |
| AS actual exec archive | `ed0d6d3fd809f73e6ac1b6847c5767b12d2fc4cbe70e4a933642778f568ddb10` |
| AS raw root PaC entry | `f3eba6ca647cf3c931fbc80f35014259722573ba5a59ccec8e707c615ef2e797` |
| PS actual exec archive | `9987ccdc6fdeaa09c4146e33ff506286959d6a4a1531c2e510b5ab62df2f5bfe` |
| PS raw root PaC entry | `70b50e5331fb4ce68c6d0d030778d57521a2a65189f803b8a4b2f089ea4fed32` |

The Plan proof uses Python 3.11+ for `hashlib.file_digest`. On this host’s Python 3.9 the exact same SHA-256 was computed by reading 1 MiB blocks; no fixture, artifact, assertion or JVM command changed. The compatible proof copy is retained with the logs.

Evidence and exact reports under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/`. Actual permission artifact output was verified locally; no permission registration, signing, deployment, database, application, listener, browser or provider invocation. No stale report or runtime acceptance is inferred.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS release package and explicit alternate offline main only. |
| MC-REUSE-001 | PASS | Existing C DTOs, pure model/binding validators and exact legacy Process digest. |
| MC-DEP-001 | PASS | Existing JDK/Jackson/Flowable and profile; all POMs unchanged. |
| MC-NAME-001 | PASS | Explicit CLI, validator, immutable facts and exact error tokens. |
| MC-VALID-001 | PASS | Closed bounded scope, owner/path/ZIP/schema checks, artifact identity and typed negatives. |
| MC-MODEL-001 | PASS | Immutable candidate facts, current ModelResource and neutral Agent definition. |
| MC-CONVERT-001 | N/A | No new entity or cross-layer persistence conversion. |
| MC-LOG-001 | N/A | No business logger; only fixed safe CLI diagnostics. |
| MC-BEAN-001 | N/A | Offline main uses no Spring application context or managed component. |
| MC-UTIL-001 | PASS | Current JDK file/ZIP/digest and Jackson tools. |
| MC-JSON-001 | PASS | Strict parse, exact canonical output, deterministic golden and independent domain verification. |
| MC-TIME-001 | N/A | No output facts depend on wall time; file metadata is only a read integrity check. |
| MC-CONFIG-001 | N/A | No app profile or property modification. |
| MC-PATTERN-001 | PASS | Thin local command adapter and direct validation with one shared pure digest. |
| MC-SCOPE-001 | PASS | Exact four paths here, eight aggregate, 26 immutable main SQL files and original POMs. |
| MC-TEST-001 | PASS | Both true assertion RED, C3/C4 finite and actual packaged positive/negative/replay. |
| MC-BLOCKER-001 | PASS | Offline A1 closed; signing, live source authority, CAS/activation and runtime E separately open. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing semantic package and type names. |
| 2 | PASS | Exact descriptor/asset scope and current validated C/Process model binding. |
| 3 | PASS | Immutable facts and direct projection without new ORM entities. |
| 4 | N/A | No managed business class or logging framework. |
| 5 | PASS | Existing JDK/Jackson APIs, no dependency addition. |
| 6 | PASS | Deterministic JSON, closed input and fixed authority-state names. |
| 7 | N/A | No environment profile changes. |
| 9 | PASS | Direct bounded validation; no additional runtime pattern or scheduler. |
| 10 | N/A | No time or runtime change. |
| 11 | PASS | Existing pure technical release package, unchanged persistence and CQE owners. |

The result authorizes no deployment or activation. Live T3-12 E, current owner/revocation proof, release signatures, review/CAS, governed starts and migration remain separate T4-01 work.
