# Offline expense release evidence gate — Source79

Effective [Spec](../spec/2026-11-05-16-56-expense-release-evidence-gate.md) and [Plan](../plan/2026-11-05-18-08-expense-release-evidence-gate.md); baseline `fafa874e460ca87a56ea652cd651c219a5028faa`.

The existing scripts directory now contains a bounded offline evidence reader and versioned expense policy. It independently checks the A1 candidate, actual artifact hashes and five source revisions, exact Surefire class ownership, paired E witness files and a caller-specified UTC audit window. Optional A2 evidence must match an independent pin and the same candidate; it does not grant trust or readiness. Missing, stale, skipped, failed, invalid and compiled-only evidence cannot count as successful runtime execution. The tool does not execute a verification suite, launch an app or deploy anything.

| Gate | Actual result |
| --- | --- |
| R1 missing checker | 1 expected assertion failure, 0 errors. |
| R2 typed stub | 1 expected assertion failure (wanted 0, stub 65), 0 errors. |
| G1 exact seven tests | All 7 passed in Python 3.14.6, 0 errors/skips. Includes actual source parsing, malformed/bounded inputs, exact five-repo/candidate locks, positive/negative synthetic reports and real separation of compiled-only/blocked states. |

Exact command: `python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py -v`. Actual logs and immutable draft/test evidence under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-05/`. An independent read-only probe parsed the 14 archived Source64 U suites and their 183 recorded cases, and paired the retained A1 and A2 fixture shape. Archived bytes were not treated as current execution. No real current I/E evidence bundle was submitted or accepted.

Source lock for the scoped work: service `fafa874e460ca87a56ea652cd651c219a5028faa`, Components `87af09fea9a9d58e7127b009d76f60438c79e569`, Shoubing `e423428e01591c122551f0c11aca8a4ffda9b4c1`, Jianshen `3afec38631ff16f44018a297c1267d48fc04a508` and Tianshu `b1c607f4d8a03855be8179d73849ebfb4488468b`. All 27 main SQL files, POMs, app configuration and backend/frontend wire remained unchanged.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing scripts/evidence boundary only. |
| MC-REUSE-001 | PASS | Current A1/A2 output, existing raw Surefire and E report shapes, bounded read-only Git. |
| MC-DEP-001 | PASS | Existing Python standard library only; POMs and dependencies unchanged. |
| MC-NAME-001 | PASS | Explicit verifier, versioned policy and exact test names. |
| MC-VALID-001 | PASS | Closed descriptors, safe local files, exact hashes, Git locks and report/witness/time tests. |
| MC-MODEL-001 | PASS | Exact evidence/status values without new business state. |
| MC-CONVERT-001 | N/A | No entity or persistence mapping. |
| MC-LOG-001 | N/A | Fixed errors; no raw report bodies, paths or secrets echoed. |
| MC-BEAN-001 | N/A | No Java/app dependency injection. |
| MC-UTIL-001 | PASS | Existing Python standard library and Git only. |
| MC-JSON-001 | PASS | Closed manifest/policy/A1/A2/witness shapes and fixed result. |
| MC-TIME-001 | PASS | Explicit UTC half-open bounds and future/stale test cases. |
| MC-CONFIG-001 | N/A | No app profile, CI, deployment or feature config. |
| MC-PATTERN-001 | PASS | One direct read-only evidence checker, no runner. |
| MC-SCOPE-001 | PASS | Exact six files and prior SQL/owner source unchanged. |
| MC-TEST-001 | PASS | Actual R1/R2 red and seven green, no claim of real I/E. |
| MC-BLOCKER-001 | PASS | Offline source gates closed; live acceptance remains independently pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | N/A | No Java POJO. |
| 2 | PASS | Exact strict evidence and safe file scope. |
| 3 | N/A | No Java/entity/mapper. |
| 4 | N/A | No managed Java class or business logger. |
| 5 | PASS | Existing Python standard library and Git. |
| 6 | PASS | Closed local JSON and stable names. |
| 7 | N/A | No profile config. |
| 9 | PASS | Direct bounded offline verification. |
| 10 | PASS | Exact UTC nanosecond audit window. |
| 11 | PASS | Existing scripts boundary, no new state layer/schema/runner. |

Full S01–S12, model quality and load/SLO, real independent U/I/E/L release acceptance, CI/deployment enforcement and production activation remain separate. No browser, listener, app, database or provider ran.
