# Authority test fixture isolation correction evidence

Origin: `56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963`; isolated unchanged baseline `174851fd226d74a3adc653ed5ccca83ad39795ae`.
Plan [2026-11-01-21-29-isolate-authority-test-fixture.md](../plan/2026-11-01-21-29-isolate-authority-test-fixture.md) SHA-256 `d8d49a8f363864a4f9c3fda63098acfb301afe91dba0ce265de90dbf3ac89d89`; Review under original continuation authority.

Root fully read all342 Plan lines and source cause; strict/resource/start-checklist confirmed. Existing baseline RED: /tmp/tianji-application-fixture-baseline-red.log exit1,25.301s,21:27:31, App3 one failure and Authority11 pass (14 total,1fail0error0skip). The ordinary nested Configuration is scanned and forces all beans lazy, masking the correct missing-runtime-dependency failure. Current main Step32 eight-path work stays untouched.

Only edit: remove the Configuration import and stereotype; retain static Bean body and all three explicit registrations. No new regression needed; existing App negative is the exact proof point.

## Requirements

- PLAN-REQ-001: PASS - App negative gate restored, explicit authority contexts preserved, independent three-path evidence complete.
- PLAN-REQ-002: PASS - App negative gate restored, explicit authority contexts preserved, independent three-path evidence complete.
- PLAN-REQ-003: PASS - App negative gate restored, explicit authority contexts preserved, independent three-path evidence complete.

## Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | One existing AS security test source plus two docs | No production architecture change | None |
| MC-REUSE-001 | Applicable | PASS | Existing App3 and Authority11 tests reused | No duplicate regression/harness | None |
| MC-DEP-001 | Not applicable | N/A | No dependency/POM path touched | Dependency surface unchanged | None |
| MC-NAME-001 | Not applicable | N/A | Class/method/Bean names unchanged | No type naming change | None |
| MC-VALID-001 | Not applicable | N/A | No business input/handoff change | Validation unchanged | None |
| MC-MODEL-001 | Not applicable | N/A | No model/record/entity changed | Model construction unchanged | None |
| MC-CONVERT-001 | Not applicable | N/A | No mapping or Converter | Conversion unchanged | None |
| MC-LOG-001 | Not applicable | N/A | No logging change | No new data exposure | None |
| MC-BEAN-001 | Applicable | PASS | Only test Configuration stereotype/import removed; static Bean and explicit registrations retained | Component scan isolated; explicit lite configuration14-test gate passes | None |
| MC-UTIL-001 | Applicable | PASS | One unused Spring import removed; no addition | No utility/framework introduced | None |
| MC-JSON-001 | Not applicable | N/A | No JSON contract/codec path changed | Serialization unchanged | None |
| MC-TIME-001 | Not applicable | N/A | No time field/calculation touched | Time semantics unchanged | None |
| MC-CONFIG-001 | Applicable | PASS | Test-only configuration visibility corrected; application YAML unchanged | Default shell and missing-dependency negative gate restored | None |
| MC-PATTERN-001 | Not applicable | N/A | Simple two-line isolation | No pattern needed | None |
| MC-SCOPE-001 | Applicable | PASS | Exact test+Plan+Evidence, two removed source lines | No main Step32 or production path touched | None |
| MC-TEST-001 | Applicable | PASS | Isolatedbaseline14/1failure -> same14/0failure exit0 | Existing negative and explicit global-lazy tests prove behavior | None |
| MC-BLOCKER-001 | Applicable | PASS | All rows and focused gate closed | Correction ready for independent commit | None |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 2 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 3 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 4 | Applicable | PASS | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 5 | Applicable | PASS | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 6 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 7 | Applicable | PASS | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 9 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 10 | Not applicable | N/A | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |
| Rule 11 | Applicable | PASS | Two-line test annotation/import deletion only; no model/DI/JSON/time/architecture addition | App3+Authority11 pass; source/path review | Existing fixture and production contracts preserved | None |

No application/server/database/network/container/browser or permission/push/PR action. Finite raw ContextRunner only; no Step32 source or production paths change in this correction.

## Observed GREEN and correction verdict

/tmp/tianji-application-fixture-isolation-green.log finished 2026-11-01 21:35:06, 22.406s, exit0 BUILD SUCCESS: App3 and AuthorityConfiguration11, total14 tests, zero failure/error/skip. Same selector and baseline as RED. Exactly two source lines were removed; static Bean body, class, all three explicit registrations and App test expectations remain unchanged. Lite explicit configuration still proves global-lazy authority checks; automatic application scanning no longer imports the test postprocessor.

Root full-read/strict/resources/start/end checklist, source diff, exact three-path whitespace/cached scope and immutable23 SQL/POM/YAML/production checks close the correction. No scratch harness or new test was added. Finite ContextRunner refresh only; no SpringApplication main, web server, database, container, browser or remote network/model/OAuth action. No push/PR/permission/history rewrite.

Corrective Step Verified pending its independent commit. Origin is56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963. Main Step32 changes remain untouched in their separate worktree. Record the new full hash/three paths, integrate the correction without mixing Step32, then rerun the required final15 gate before committing MCP assembly.
