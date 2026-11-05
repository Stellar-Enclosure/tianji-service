# 天机本地有限 U 门禁实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-20-50-local-finite-u-gate.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 20:50 Asia/Shanghai` |
| Updated | `2026-11-05 20:50 Asia/Shanghai` |
| Owner | 天机平台决策人、Root |
| Repository | `tianji-service` |
| Scope | T4-05 单本地 finite U tooling commit |
| Source Requirement | 主 Spec REQ-001–004 与现有验收手册 |
| Baseline Revision | `tianji-service 1b5700ae9ba409c948c4d1a261d7b002de2b4381` clean |
| Implements Spec | [本地有限 U 门禁](../spec/2026-11-05-20-47-local-finite-u-gate.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 20:47 Asia/Shanghai` SHA-256 `0c9d15d28b7b73eb6efafadaf66bde86b10012bc2a66f764b08fe7d586f2d184` |
| Effective Specs | [主 Spec](../spec/2026-11-05-20-47-local-finite-u-gate.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

一个 source Step 交付本地 U runner、有限单测、最小共享严格报告校验。先纯 assertion RED，再纯 U GREEN、实际归档报告只读验证和 Root 单独授权的本地 actual gate。无远程/其他 runtime。

## 2. Target Spec and Effective Design

### 2.1 Primary target

Root 请求 exact 17 Service U、本地 cached wrapper、clean five HEAD、nonce 与脱敏。当前为 Review。正式源锁定三份脚本与 docs exact SHA。实际 Maven 由 Root 依次控制。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [local U](../spec/2026-11-05-20-47-local-finite-u-gate.md) | Review, hash above | all | only current REQ-001–004 |
| Existing authority | current runbook/expense verifier | current | exact 17 classes, locked source and report parser | preserve old semantics and tests |

### 2.3 Superseded or excluded content

No supersession. No remote CI, publish approval, DB/server/provider/browser, install, credentials, arbitrary Maven flags or input report copies.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §7/9 | fixed 17 current classes | exact fixed command, no pass-through | runner and pure test |
| REQ-002 | §7/9 | clean exact five-head and cached toolchain | pre/post verify, closed settings and JDK25/cache | runner and pure test |
| REQ-003 | §7/9/10 | nonce and exact owner reports | fresh suffix/property, strict count and known dynamic rows only | shared parser and runner |
| REQ-004 | §9/15 | sanitized scoped proof | exact success/error only, no raw report or remote claim | runner, test and evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

One Service commit. Add pure test and compile/import-only A stub. R1 requires actual assertion. B first refactors exact shared strict suite helper, then builds local runner. Record pure 13-test and historical 17/220 proof. Root may run the reviewed runner on a clean exact checkout from the scratch copy before staging, or after a clean committed checkout. No dirty bypass.

### 4.2 Test-first strategy

Run focused fake-subprocess acceptance assertion against A returning fixed unavailable. It genuinely fails 66 vs expected 0, without invoking Maven. Only then use B and run all six new plus seven existing pure tests. An import or malformed fixture error does not count.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | approved hashes and exact current clean five source owners | pure off-repo tests and reads | other edits to owned scripts or actual Maven | existing source reactor and report owner boundaries |

### 4.4 Commit boundaries

Exactly three script files and three docs, one Service commit. Do not add runbook, workflow, generated reports, cache, POM or policy files.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| local current U tool | Add §7 | runbook and existing source reactor | manual leaves stale proof | one fixed wrapper call | Implement |
| existing strict XML helper | Keep §7 | prior verifier parser | duplicated parser unnecessary | one compatible extraction | Implement |
| remote release workflow | Remove §3 | no current runtime authority | local only | none | Excluded |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| pure behavior | REQ-001–004 | R1 | A stub and mock fixture | test harness | B | Step 1 |
| shared report proof | REQ-003 | G1/G2 | existing expense validator | strict optional local suffix/props | runner | Step 1 |
| fixed local runner | REQ-001–004 | G1/G3 | approved existing wrapper/cache/locks | exact summary | operator | Step 1 |
| canonical proof | REQ-004 | S1 | real gates and locks | exact evidence | review | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

N/A Java implementation. Python stdlib, existing Service scripts and existing validation.

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| architecture | current scripts and wrapper | preserve local owner | 3 scripts only | Step 1 MC-ARCH-001 |
| reuse | existing strict report/Git validators | share no dependency | minimal helper extraction | Step 1 MC-REUSE-001 |
| validation/diagnostic | current U XML and runbook | bounded exact proof | no raw output | Step 1 MC-VALID-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| build | current verify-source-reactor | exact fixed 17 selectors | fits | reuse | none | Step 1 MC-REUSE-001 |
| lock/XML | verify-release-evidence | exact clean five heads and strict report parser | nonce-specific optional parameters | minimal reuse | no library | Step 1 MC-VALID-001 |
| summary | existing stdlib canonical JSON | redacted output | local digest fields only | reuse | none | Step 1 MC-JSON-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Python only | §5 | no Java names | S1 | Step 1 | N/A |
| Rule 2 | §6.2 | Python CLI | Files 1–3 | exact local input validation | G1 | Step 1 | N/A |
| Rule 3 | §6.2 | no POJO | §5 | no Java model | S1 | Step 1 | N/A |
| Rule 4 | §6.2 | no Spring | §5 | fixed safe diagnostics only | G1 | Step 1 | N/A |
| Rule 5 | §6.2 | stdlib | §5 | no new dependency | S1 | Step 1 | N/A |
| Rule 6 | §6.2 | existing strict XML | Files 2/3 | no Java JSON contract | G1 | Step 1 | N/A |
| Rule 7 | §6.2 | no profiles | §5 | no runtime settings change | S1 | Step 1 | N/A |
| Rule 9 | §6.2 | direct local | File 3 | no framework | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | Python UTC | File 3 | precise UTC observation | G1 | Step 1 | N/A |
| Rule 11 | §6.1/8 | existing scripts | all | preserve owner/runtime/DB | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | startedAt, finishedAt | Occurred instant | Python aware datetime | Not persisted | UTC Z | Not applicable: no calendar | UTC | elapsed reporting only, nonce separate | microseconds, current UTC | no existing source contract change | Step 1 TEST-001 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `scripts/test_verify_local_release_u.py` | current offline evidence test style | six pure tests | finite proof | Step 1 | REQ-001–004 | R1/G1 |
| MODIFY | `scripts/verify-release-evidence.py` | strict parse/verify/summarize_report | shared summarize_xml_suite | exact shared report logic | Step 1 | REQ-003 | G1/G2 |
| CREATE | `scripts/verify-local-release-u.py` | current wrapper/runbook | fixed local gate | runner and summary | Step 1 | REQ-001–004 | G1/G3 |
| CREATE | `docs/egon/spec/2026-11-05-20-47-local-finite-u-gate.md` | reviewed authority | canonical Spec | docs | Step 1 | REQ-001–004 | S1 |
| CREATE | `docs/egon/plan/2026-11-05-20-50-local-finite-u-gate.md` | reviewed Plan | canonical Plan | docs | Step 1 | REQ-001–004 | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-local-finite-u-gate.md` | actual gates | redacted evidence | docs | Step 1 | REQ-004 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Service 1b5700ae9ba409c948c4d1a261d7b002de2b4381, Components 87af09fea9a9d58e7127b009d76f60438c79e569, Shoubing e423428e01591c122551f0c11aca8a4ffda9b4c1, Jianshen 3afec38631ff16f44018a297c1267d48fc04a508 and Tianshu b1c607f4d8a03855be8179d73849ebfb4488468b. All read clean for this review. Root may lock an explicitly reviewed successor before actual execution.

### 6.2 Build, test, and environment prerequisites

Python stdlib (runner and new tests verified with 3.9, shared legacy test suite uses 3.14), current JDK25, already cached wrapper 3.9.14, approved credential-free local settings and offline dependencies. Actual reactor only after Root authorizes exact sequential gate. Pure tests do not connect Maven.

### 6.3 Immutable constraints and approved decisions

Exact 17 finite classes, no arbitrary arguments or raw copies. Existing expense verifier strict default unchanged. No remote workflow, configuration, permissions or runtime.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | exact suffix and CLI property fresh nonce | pinned Surefire 3.2.5 class bytes | no old file overwrite or raw copying | actual gate fails closed |
| PLAN-CLAR-002 | exact six dynamic rows for one existing test | source @TestFactory and actual 17 reports | legacy default still disallows repeats | update only after reviewed owner evidence |
| PLAN-CLAR-003 | no output on any incomplete result | existing fixed error style | preserves redaction and release independence | report gate fails closed |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Lock and verify one actual local finite U run

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: exact approved Spec/Plan and current five clean source locks.
- Baseline state: existing manual runbook and standalone expense evidence verifier.
- Observable outcome: one local gate for fixed 17 classes, with exact fresh reports and redacted summary.
- End state: six exact Service paths, one commit. No release approval.
- Test-first gate: Required. A produces actual fake-fixture 66-vs-0 assertion RED, no Maven.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-LOG-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 11. Other literal rules reviewed separately.
- Ordered files:

#### File 1 — `CREATE scripts/test_verify_local_release_u.py`

- Purpose: Pin pure input/report/scope and redaction proof.
- Symbols: LocalReleaseUGateTest, exact six tests and fake Fixture.
- Repository evidence: current evidence gate pure unittest and retained 17 report counts.
- Dependencies and consumers: A runner, existing strict verifier, fake Git and reactor.
- Why now: expected behavior before B.
- Contract/signature changes: no production contract beyond exact tested CLI.
- Input/output and state mapping: known synthetic 17/220 positive, fake outputs with private canaries, mismatch/dirty/flags/nonce/settings cases.
- Error and edge behavior: fail closed on stale, wrong owner, extra args, source drift, symlink and flaky, with no raw output.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-TEST-001.
- Literal rule enforcement: Rule 11. Existing tooling-only scope.
- Implementation pseudocode:

```python
create fake five clean HEAD checkouts, local wrapper, locked settings and an intercepted reactor;
emit 17 exact synthetic report fixtures with current nonce and the single six-row factory;
assert successful redacted summary, then reject old nonce/owner, extra argv, drift and bad cache;
A runner returns fixed unavailable so the focused positive assertion genuinely fails.
```

- Verification contribution: R1 and G1.
- After this file: exact pure assertions ready.

#### File 2 — `MODIFY scripts/verify-release-evidence.py`

- Purpose: Reuse strict existing suite parser and counting validation.
- Symbols: summarize_report, summarize_xml_suite.
- Repository evidence: old strict XML parser/owner checks and actual accepted expense tests.
- Dependencies and consumers: existing expense wrapper and new local U.
- Why now: shared verification before runner.
- Contract/signature changes: existing public CLI/default behavior unchanged. New internal shared suite helper.
- Input/output and state mapping: one parsed suite, exact owner/selector counts, optional suffix, exact basedir/properties/repetitions only for local U.
- Error and edge behavior: old strict duplicate rejection preserved; one exact supported repetition mapping allowed only when passed.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-TEST-001.
- Literal rule enforcement: Rule 11. Preserve owner and existing evidence contract.
- Implementation pseudocode:

```python
keep summarize_report file, SHA, XML and bounds checks unchanged;
call summarize_xml_suite on the strict parsed element;
the new local caller supplies exact suffix, basedir, properties and one exact DynamicTest count;
default callers still demand unique case names and original report identity.
```

- Verification contribution: all seven existing tests plus G2 actual archived validation.
- After this file: one shared parser/validation path ready.

#### File 3 — `CREATE scripts/verify-local-release-u.py`

- Purpose: Run and bind one fresh exact local finite U.
- Symbols: arguments, offline_settings, wrapper_ready, verify_report_targets, reactor_command, collect_reports, run.
- Repository evidence: existing source reactor, fixed 17 test runbook and pinned Surefire artifact.
- Dependencies and consumers: current strict verifier, local JDK/cache/settings, approved operator.
- Why now: A supports R1, B only after exact tests.
- Contract/signature changes: five required CLI flags, redacted fixed summary or safe status.
- Input/output and state mapping: validate five source heads pre/post, closed settings and cached wrapper, nonce filename and XML property, verify 17 and return only SHA/counts.
- Error and edge behavior: bounded reads/child/targets; unavailable or drift returns fixed error. No subprocess logs, raw XML copies or user Maven args.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-LOG-001, MC-JSON-001, MC-TIME-001.
- Literal rule enforcement: Rule 11. Local tooling only, no runtime/config/DB layer.
- Implementation pseudocode:

```python
parse exact absolute CLI inputs, require bounded credential-free settings and cached wrapper;
verify five exact clean HEADs, generate new nonsecret nonce and require unoccupied reports;
invoke fixed offline reactor with exact 17 selectors, -D nonce and report suffix, no raw process output;
validate expected same-run owner XML, zero failures/errors/skips/flaky, exact current locks;
write only fixed canonical JSON of heads, digests, observed UTC and counts.
```

- Verification contribution: G1, G2 and later Root-authorized G3.
- After this file: complete local source gate.

#### File 4 — `CREATE docs/egon/spec/2026-11-05-20-47-local-finite-u-gate.md`

- Purpose: Persist exact reviewed local source authority.
- Symbols: REQ-001–004.
- Repository evidence: canonical EGON conventions.
- Dependencies and consumers: reviewed hash, Plan and evidence.
- Why now: lock scope before commit.
- Contract/signature changes: docs only.
- Input/output and state mapping: exact reviewed bytes and canonical relative paths.
- Error and edge behavior: scope/hash mismatch blocks.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Local source only.
- Implementation pseudocode:

```text
verify exact reviewed Spec hash and existing operator runbook links;
create the canonical file without expanding policy or release authority;
strictly validate the file and exact scope before commit.
```

- Verification contribution: S1 strict Spec.
- After this file: exact source authority in review.

#### File 5 — `CREATE docs/egon/plan/2026-11-05-20-50-local-finite-u-gate.md`

- Purpose: Persist exact reviewed gate/Step order.
- Symbols: R1, G1–G3, S1.
- Repository evidence: existing Plan validators.
- Dependencies and consumers: primary Spec and actual source.
- Why now: one fixed implementation scope.
- Contract/signature changes: docs only.
- Input/output and state mapping: exact path list and named local gates.
- Error and edge behavior: extra runtime or outputs block.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. No production or workflow.
- Implementation pseudocode:

```text
create canonical Plan from exact reviewed bytes and bind primary Spec revision;
run both strict validators and compare all ordered file paths with actual staged files;
keep actual source execution and external acceptance distinct.
```

- Verification contribution: S1 strict Plan.
- After this file: exact ordered scope in review.

#### File 6 — `CREATE docs/egon/evidence/2026-11-05-local-finite-u-gate.md`

- Purpose: Record actual test/source proof and historical vs current distinction.
- Symbols: R1, G1/G2 and G3 if explicitly run.
- Repository evidence: existing Service local evidence convention.
- Dependencies and consumers: actual reports, hashes, redacted summary.
- Why now: after actual gates before commit.
- Contract/signature changes: docs only.
- Input/output and state mapping: pure and historical actual result, current run only when executed.
- Error and edge behavior: no raw evidence or unwitnessed release claim.
- Standards impact: MC-SCOPE-001, MC-LOG-001, MC-TEST-001.
- Literal rule enforcement: Rule 11. Owner proof only.
- Implementation pseudocode:

```text
record actual pure RED/GREEN and retained historical 17/220 nonce-rejection proof;
record exact actual current redacted summary only if separately executed and reviewed;
confirm all original authority and SQL remain unchanged and no remote/live claim.
```

- Verification contribution: S1 actual evidence.
- After this file: ready for exact staged audit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: R1/G1/G2/S1; G3 Root-only on clean checkout.
- Expected result: one pure intended RED, 13 pure GREEN, 17 historical/220 counts with missing nonce rejected, exact six paths.
- Failure returns to: file owner or clean lock/cached prerequisite. No runtime fallback.
- Completion criteria: exact tests/docs and redacted evidence, actual G3 only as available.
- Rollback: one focused source commit revert.
- Commit paths: `scripts/test_verify_local_release_u.py`, `scripts/verify-release-evidence.py`, `scripts/verify-local-release-u.py`, `docs/egon/spec/2026-11-05-20-47-local-finite-u-gate.md`, `docs/egon/plan/2026-11-05-20-50-local-finite-u-gate.md`, `docs/egon/evidence/2026-11-05-local-finite-u-gate.md`.
- Commit: `feat(tooling): verify locked local finite U run`.

## 8. Test, Validation, and Quality Gates

R1 A stub with new pure test; expected exactly one assertion 66 vs 0 and no actual Maven. G1 B pure tests, 6 new and all 7 existing legacy.

```bash
PYTHONPATH=scripts python3 -B -m unittest \
  test_verify_local_release_u.LocalReleaseUGateTest.test_binds_exact_fixed_selectors_nonce_and_redacted_17_report_summary
python3.14 -B -m unittest discover -s scripts -p 'test_verify*.py' -v
```

G2 read only the private retained U reports in place. Validate exact owning 17 and aggregate historical 220 with default legacy source identities and exact approved DynamicTest count, then demand current arbitrary nonce absent from all historical XML. No copy or raw content. Scratch sanitized `historical-proof.json` captures actual review result.

G3 is explicitly Root-authorized only. Use a clean checkout and exact reviewed five-head JSON with §6.1 IDs. Before staging, the runner and shared verifier may execute from the same outside-repo scratch B directory. After staging/commit, execute only on a clean exact checkout with a fresh reviewed lock. No dirty override. Use the following fixed local inputs if still verified.

```bash
python3 -B /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-05/local-u-gate/b/verify-local-release-u.py \
  --checkout /Users/mario/SelfProject/Stellar-Enclosure/tianji-service \
  --source-lock "$reviewed_u_source_lock" \
  --settings /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml \
  --java-home /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  --maven-user-home /Users/mario/.m2
```

Expected exact 17 fresh owner reports. Current baseline's historical reference is 220, but the tool counts actual current tests. No raw reports copied or printed. An old report with a different nonce cannot validate. The checkout before/after must match exact lock. Source work may verify all actual pure gates without claiming G3 when Root has not separately run it.

S1 both strict docs validators, `git diff --check` and exact six staged paths. Keep original SQL, source reactor, toolchain and policy unchanged. One exact commit and clean final state.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or live action. Existing expense verifier default stays strict. Revert focused source commit only. Actual operator U separately scoped from remote CI, runtime and release approval.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | §7/9 | Step 1 | 1/3 | R1/G1/G3 | exact 17 selector command |
| REQ-002 | §7/9 | Step 1 | 1/3 | G1/G3 | five heads/cache/settings |
| REQ-003 | §7/10 | Step 1 | 1–3 | G1/G2/G3 | exact nonce/report validation |
| REQ-004 | §9/15 | Step 1 | 1/3–6 | G1/S1 | redacted result and explicit limits |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- |
| DEC-001 | fixed local source gate | Step 1 | reviewed runbook/current 17 | Root | exact review |
| RISK-001 | wrapper cache missing or drift | File 3/G3 | exact installed distribution | Operator | reject before invocation |
| RISK-002 | old/wrong-owner report mistaken for current | Files 1–3 | actual XML and nonce | Root | reject all |
| RISK-003 | current U treated as release PASS | docs/summary | T4-05 matrix separate I/E/L | Root | explicit source-only |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

One local gate with exact safe allowlist, no production/remote effects.

### 12.2 Spec consistency

Fixed CLI, five HEAD lock, nonce and exact report scope. Existing expense behavior unchanged.

### 12.3 Repository executability

Exact three source and three docs. Pure tests and separate authorized current repo gate.

### 12.4 Test and release completeness

Pure assertion, seven legacy plus six new, retained actual no-nonce rejection, actual current Maven only if separately authorized. No I/E/L claim.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/5 | existing scripts owner | None |
| MC-REUSE-001 | Applicable | PASS | Files 2/3 | current XML/Git and wrapper | None |
| MC-DEP-001 | Applicable | PASS | §4.7/6 | no dependency | None |
| MC-NAME-001 | Not applicable | N/A | §5 | no Java | None |
| MC-VALID-001 | Applicable | PASS | R1/G1/G2 | strict local proof | None |
| MC-MODEL-001 | Not applicable | N/A | §5 | no Java model | None |
| MC-CONVERT-001 | Not applicable | N/A | §5 | no converter | None |
| MC-LOG-001 | Applicable | PASS | File 3 | no raw log output | None |
| MC-BEAN-001 | Not applicable | N/A | §5 | no Spring | None |
| MC-UTIL-001 | Not applicable | N/A | §4.7 | no Java utility | None |
| MC-JSON-001 | Applicable | PASS | File 3/G1 | fixed lock and summary | None |
| MC-TIME-001 | Applicable | PASS | TIME-001 | UTC observation | None |
| MC-CONFIG-001 | Not applicable | N/A | §5 | no profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §4.5 | direct runner and reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7 | six local paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | pure/current/remote separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | design ready, actual gate pending | None |

### 12.6 Final verdict

PASS — Ready for user review

Actual Maven and any remote/runtime operation remain separate from this Plan review.
