# T4-05 天机单仓发布工具 CI 实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-10-repo-release-tooling-ci.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 23:10 Asia/Shanghai |
| Updated | 2026-11-05 23:10 Asia/Shanghai |
| Owner | Tianji Service release tooling |
| Repository | tianji-service |
| Scope | One manual workflow and three review/evidence paths |
| Source Requirement | 主计划 T4-05 单仓发布工具的持续验证 |
| Baseline Revision | 75d0c15a2ff9bef03be80e7cd7584b2bcbe8401e |
| Implements Spec | [Repository release tooling CI](../spec/2026-11-05-23-09-repo-release-tooling-ci.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 23:09 Asia/Shanghai SHA-256 b18fd5680d7a469ff1d2b1b6d137a699e78cffa25cf5dbc5c06374824f69480d |
| Effective Specs | [Repository release tooling CI](../spec/2026-11-05-23-09-repo-release-tooling-ci.md), [Local finite U gate](../spec/2026-11-05-20-47-local-finite-u-gate.md) |
| Depends On Plans | [Local finite U gate](2026-11-05-20-50-local-finite-u-gate.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 repository-local Step 增加手动的单仓发布工具 CI 工作流。此计划止于源码及本地有限证据。真实托管运行与当前跨仓 Java U 维持独立。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Repository release tooling CI](../spec/2026-11-05-23-09-repo-release-tooling-ci.md) REQ-001–004 和 CI-001。

### 2.2 Effective Spec set

Primary Spec plus existing Local finite U gate. 本工作流仅使用该工具的 fixture 测试，不修改其接口、五仓或 Maven 要求。

### 2.3 Superseded or excluded content

既有 Maven、Python 工具、发布策略、跨仓 Git、私有报告和部署各保留其所有者边界。

## 3. Effective Requirements and Acceptance

| Requirement | Implementation | Acceptance |
| --- | --- | --- |
| REQ-001 | Manual own repo and exact event commit | Same SHA, one exact fetch and no bad context |
| REQ-002 | Minimal checkout and five tracked blobs | Transient auth, regular mode, archive byte rehash |
| REQ-003 | Isolated exact 13 fixture tests | 7+6, no skipped, failed or missing case |
| REQ-004 | Independent source and hosted boundaries | Workflow-only implementation and no live invocation |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline and reviewed bytes, prove absence, materialize one workflow, run syntax/transport/archive/Python local proof, inspect exact four paths and commit only after approval.

### 4.2 Test-first strategy

R1 is a source-presence assertion because current baseline has no workflow. G1 validates the actual candidate scripts and synthetic negatives without a live transport. No assertion requires contacting GitHub.

### 4.3 Sequential and parallel boundaries

R1 precedes source. Validate existing file blobs independently of YAML structure. Simulated checkout and archive precede Python tests. No concurrent writes to the same repository paths.

### 4.4 Commit boundaries

One logical commit in Service. It contains one workflow and the three documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

Existing two suites are sufficient. Direct Bash/Git and standard Python avoid new workflow action dependencies and installer. Test data comes from existing synthetic fixtures.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Result |
| --- | --- | --- |
| R1 | Exact local HEAD | Prove no baseline workflow |
| G1 static | Candidate | YAML event and permission plus Bash validity |
| G1 execution | Candidate and five exact Git blobs | Local transport fixture and 13 actual Python tests |
| S1 | G1, reviewed docs | One exact source commit scope |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

No Java or Spring target. Preserve all existing application and source-reactor boundaries.

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | Existing Service scripts and no workflow | §7 single tooling owner | One job under current repo | Step 1 MC-ARCH-001 |
| Reuse | Current stdlib verifier suites | §6/§7 exact fixture reuse | No new script or dependency | Step 1 MC-REUSE-001 |
| Security and error | Git and isolated Python fixtures | §9 source and token checks | Fixed checkout denial and five file limit | Step 1 MC-VALID-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Own commit source | GitHub event SHA, existing Git | Service Git metadata | Exact SHA | Native Git | One shell step | Step 1 MC-VALID-001 |
| Verify existing tools | Two Python suites | 7+6 current exact tests | Sufficient | Reuse | No fixture or tool edit | Step 1 MC-TEST-001 |
| Workflow execution | Runner and stdlib | Source assumes Ubuntu/Bash/CPython 3.10+ | Local proof only | Fail closed | No installation | Step 1 MC-DEP-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Workflow only | §5 | no Java names | S1 | Step 1 | N/A |
| Rule 2 | §6.2 | Workflow context | File 1 | exact Git context checks | G1 | Step 1 | N/A |
| Rule 3 | §6.2 | no POJO | §5 | no Java model | S1 | Step 1 | N/A |
| Rule 4 | §6.2 | no Spring | §5 | fixed safe diagnostics only | G1 | Step 1 | N/A |
| Rule 5 | §6.2 | stdlib | §5 | no new dependency | S1 | Step 1 | N/A |
| Rule 6 | §6.2 | existing strict XML | File 1 | no Java JSON contract | G1 | Step 1 | N/A |
| Rule 7 | §6.2 | no profiles | §5 | no runtime settings change | S1 | Step 1 | N/A |
| Rule 9 | §6.2 | direct local | File 1 | no framework | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | No affected time | File 1 | no time change | G1 | Step 1 | N/A |
| Rule 11 | §6.1/8 | existing scripts | all | preserve owner/runtime/DB | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: Only a five-minute infrastructure timeout is new. Existing Python audit/UTC evidence and application times keep their previous contract.

## 5. Change File Tree

| Action | Exact path | Purpose | Order |
| --- | --- | --- | --- |
| Create | `.github/workflows/release-tooling.yml` | Single manual Service workflow. | Step 1 File 1 |
| Create | `docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md` | Record exact approved source and security scope. | Step 1 File 2 |
| Create | `docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md` | Keep exact order, evidence and source boundary. | Step 1 File 3 |
| Create | `docs/egon/evidence/2026-11-05-repo-release-tooling-ci.md` | Record actual source-local proof and runtime limitations. | Step 1 File 4 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Service `75d0c15a2ff9bef03be80e7cd7584b2bcbe8401e` clean. Before source operation confirm no drift, exact doc hashes, worktree ownership and no preexisting workflow at this path.

### 6.2 Build, test, and environment prerequisites

Local proof uses Git, Bash, Ruby Psych and Python 3.14.6. Workflow runtime declares Ubuntu 24.04 and Python >=3.10. Local `actionlint` unavailable, use the actual workflow YAML and extracted Bash checks. Hosted runner remains unverified.

### 6.3 Immutable constraints and approved decisions

No push, manual dispatch, runner provisioning, token acquisition, sibling checkout, new dependency, Maven, app or database. Only local Git metadata and sandbox fixtures.

### 6.4 Plan Clarifications

Use the native Git repo-scoped transient header to avoid an Action pin or configured credential. Quote YAML `'on'` to preserve structure in local YAML readers.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the repository release tooling workflow

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Locked own source, reviewed Spec/Plan, Git, Bash, local Python and Ruby for isolated proof.
- Baseline state: Service contains two reusable tooling suites, no workflow at the proposed path.
- Observable outcome: Exact one manual job source; local proof reads only five tracked blobs and executes 13 fixture tests.
- End state: Four exact repository paths for one focused local commit, no hosted execution.
- Test-first gate: Required. R1 exact tracked workflow-presence assertion fails on locked baseline before source.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 11. Rules 1, 2, 3, 4, 5, 6, 7, 9 and 10 are inapplicable to the workflow-only change.
- Ordered files:

#### File 1 — `CREATE .github/workflows/release-tooling.yml`

- Purpose: Single manual Service workflow.
- Symbols: on.workflow_dispatch, permissions.contents, jobs.release-tooling, fetch and archive outputs
- Repository evidence: Five exact archived files pass 13 local fixture tests; no current Service workflow.
- Dependencies and consumers: GitHub own contents context and exact tested Python tool files.
- Why now: R1 confirms current scope is absent before materializing the reviewed file.
- Contract/signature changes: Only manual GitHub job, no new app API.
- Input/output and state mapping: Own context and token to transient fetch; verified five blobs to isolated Python; fixed tests to job status.
- Error and edge behavior: Reject context, SHA, mode, byte drift, Python version, discovery, skips and errors. No shell interpolation of untrusted event expressions.
- Standards impact: MC-SCOPE-001, MC-VALID-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 applies to preserving existing tooling ownership; other Java rules N/A.
- Implementation pseudocode:

```yaml
on: workflow_dispatch # the actual YAML uses a quoted on mapping
permissions: {contents: read}
# Step 1 gates exact GitHub event/repo/host/SHA, fetches once with ephemeral config and checks FETCH_HEAD.
# Step 2 validates all five Git blobs, materializes git archive and compares each byte digest.
# Step 3 uses a fresh environment with Python -I -B, exact 7 and 6 tests, no skips.
# No upstream or app invocation.
```

- Verification contribution: G1 native workflow source proof.
- After this file: The source-only workflow candidate has one exact owner job.

#### File 2 — `CREATE docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md`

- Purpose: Record exact approved source and security scope.
- Symbols: REQ-001–004, CI-001
- Repository evidence: Existing T4-05 and finite-U design.
- Dependencies and consumers: Read before Plan materialization.
- Why now: Freeze approved Spec bytes before execution.
- Contract/signature changes: No runtime contract change.
- Input/output and state mapping: Defines exact trigger, token scope, archive and test outcome.
- Error and edge behavior: Hosted availability explicitly separate.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 11 scope preservation; no Java.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md
cp -n "$CI_REVIEWED_SPEC" docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md
cmp "$CI_REVIEWED_SPEC" docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md
```

- Verification contribution: S1 strict Spec.
- After this file: Approved Spec bytes recorded with the logical change.

#### File 3 — `CREATE docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md`

- Purpose: Keep exact order, evidence and source boundary.
- Symbols: Step 1, R1, G1, S1
- Repository evidence: Existing reviewed Spec and offline tooling history.
- Dependencies and consumers: Consumes exact reviewed Spec hash.
- Why now: Bind the approved execution plan before source commit.
- Contract/signature changes: One manual workflow Step.
- Input/output and state mapping: Source test sequence and exact repository-local commit paths.
- Error and edge behavior: Hosted run remains separate from approval.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11 scope preservation; no Java.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md
cp -n "$CI_REVIEWED_PLAN" docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md
cmp "$CI_REVIEWED_PLAN" docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md
```

- Verification contribution: S1 strict Plan.
- After this file: Implementation scope frozen.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-repo-release-tooling-ci.md`

- Purpose: Record actual source-local proof and runtime limitations.
- Symbols: Evidence R1/G1/S1 and unexecuted hosted boundary
- Repository evidence: Git archive fixture proof and current policy constraints.
- Dependencies and consumers: Current operator runbook consumers.
- Why now: After actual checks run.
- Contract/signature changes: No application/result claim.
- Input/output and state mapping: Record exact baseline and final artifact SHA and actual test counts.
- Error and edge behavior: Keep credentials and hosted outcome out of this proof.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11 scope preservation; no Java.
- Implementation pseudocode:

```text
Record clean source revision and the one immutable workflow candidate digest.
Attach local parser and transport-fixture outcome, exact Git archive and 7+6 proof.
Keep remote workflow trigger and full Java U/I/E/L as explicitly unexecuted.
```

- Verification contribution: S1 scope audit.
- After this file: Evidence can be reviewed without private data.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Run R1/G1/S1 in §8 at the corresponding source state.
- Expected result: One R1 source-presence failure, exact G1 structural proof and 7+6 tests, negative fixtures fail closed, S1 four exact paths.
- Failure returns to: Workflow context/bytes or Python suite selector in File 1, evidence/scope in Files 2–4.
- Completion criteria: Verified exact candidate SHA, actual offline results, no existing tooling/migration diff and no hosted result.
- Rollback: Remove the single workflow by reverting the isolated commit if authorized.
- Commit paths: `.github/workflows/release-tooling.yml`, `docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md`, `docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md`, `docs/egon/evidence/2026-11-05-repo-release-tooling-ci.md`.
- Commit: `ci(tianji): check release tooling from exact repo source`.

## 8. Test, Validation, and Quality Gates

Run from locked Service root. Use the retained off-repository validation helper, whose only real Git fetch uses a local fixture. The helper and logs remain outside the commit.

```sh
git cat-file -e 75d0c15a2ff9bef03be80e7cd7584b2bcbe8401e:.github/workflows/release-tooling.yml
ci_proof=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-05/repo-tooling-ci/proof/verify_workflow_local.py
python3.14 -I -B "$ci_proof" "$PWD" "$PWD/.github/workflows/release-tooling.yml"
git diff --check
git diff --cached --check
```

| Gate | Expected exact proof | Failure |
| --- | --- | --- |
| R1 | `git cat-file -e` nonzero from absent workflow at exact baseline | Unexpected existing file blocks source |
| G1 | Ruby Psych, Bash syntax, five invalid context cases, one correct local transport fixture, five Git blobs, 7+6 in clean env, missing/skip/unsupported Python fail | Correct source or fixture before scope review |
| S1 | Both strict validators, exactly four paths, current tool/SQL files unchanged, no hosted run | Restore exact scope |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or runtime. Source publishing and manual run remain independent decisions.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | Step 1 File 1 | R1/G1 |
| REQ-002 | Step 1 File 1 | G1 |
| REQ-003 | Step 1 File 1 | G1 |
| REQ-004 | Step 1 Files 1–4 | S1 |

## 11. Risks, Blockers, and User Decisions

| Risk | Current status | Follow-up |
| --- | --- | --- |
| Hosted repository and workflow availability | Unverified | Approved separate publication and dispatch |
| Actual remote Java U | Unverified | Requires exact sibling refs, checkout authority and offline cache |
| Runtime requirements | Outside source scope | Existing owner acceptance gates |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Independent exact manual lane for existing verification tools.

### 12.2 Spec consistency

Same source, transient auth, exact archive and test scopes.

### 12.3 Repository executability

Local proof can run without Maven/siblings/runtime. Hosted runner and network not exercised.

### 12.4 Test and release completeness

R1/G1/S1 are source gates. No release result is inferred.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/5 | existing scripts owner | None |
| MC-REUSE-001 | Applicable | PASS | File 1 | existing Python tooling fixtures | None |
| MC-DEP-001 | Applicable | PASS | §4.7/6 | no dependency | None |
| MC-NAME-001 | Not applicable | N/A | §5 | no Java | None |
| MC-VALID-001 | Applicable | PASS | R1/G1 | strict local proof | None |
| MC-MODEL-001 | Not applicable | N/A | §5 | no Java model | None |
| MC-CONVERT-001 | Not applicable | N/A | §5 | no converter | None |
| MC-LOG-001 | Applicable | PASS | File 1 | no raw log output | None |
| MC-BEAN-001 | Not applicable | N/A | §5 | no Spring | None |
| MC-UTIL-001 | Not applicable | N/A | §4.7 | no Java utility | None |
| MC-JSON-001 | Not applicable | N/A | File 1/G1 | no evidence JSON change | None |
| MC-TIME-001 | Not applicable | N/A | §4.9 | no affected time contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §5 | no profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §4.5 | direct runner and reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7 | four exact paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | pure/current/remote separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | local proof and hosted boundary | None |

### 12.6 Final verdict

PASS — Ready for user review

Source-local proof only. No hosted run, application, credentials or upstream mutation.
