# T4-05A1 天机离线发布证据门禁实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-18-08-expense-release-evidence-gate.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 18:08 Asia/Shanghai |
| Updated | 2026-11-05 18:08 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 一个离线查询 Step，三个 scripts 源路径和三个同范围归档文档 |
| Source Requirement | 主计划 §17/§19 T4-05 expense 离线证据首切片 |
| Baseline Revision | main fafa874e460ca87a56ea652cd651c219a5028faa，A1/A2/AS Link 源码已合入 |
| Implements Spec | [T4-05A1](../spec/2026-11-05-16-56-expense-release-evidence-gate.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 18:08 Asia/Shanghai, SHA-256 d0088eca7b8781921a66bd4e7a3e6e52dc106b6e95eff20cf3c35d0bcba83f03 |
| Effective Specs | [T4-05A1](../spec/2026-11-05-16-56-expense-release-evidence-gate.md), [T3-12 evidence shape](../spec/2026-11-05-15-13-expense-four-path-acceptance.md#9-interface-definitions), [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions), [A2 optional reference](../spec/2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) |
| Depends On Plans | [T3-12 source gates](2026-11-05-15-30-expense-four-path-acceptance.md#7-ordered-file-by-file-implementation-steps), [A1](2026-11-05-16-33-platform-release-artifact-candidate.md#7-ordered-file-by-file-implementation-steps), [A2](2026-11-05-17-44-platform-release-detached-signature.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Root approved the exact offline evidence-reader source under the user master Plan. No test/app/database is launched by the new tool; only current finite synthetic checks run here. A missing or unproven real E stays blocked and no result grants publication or activation.

一个标准库离线查询核对 A1 候选、实际 AS/PS 归档、五仓版本、独立声明的 UTC 审计窗口以及真实 U/I/E 原始证据。A2 离线结果可独立钉选为候选引用。一个 Step 交付策略、查询和有限测试。真实 I/E、CI、部署、发布权威、L 和 SLO 独立。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[T4-05A1](../spec/2026-11-05-16-56-expense-release-evidence-gate.md) 为 Review。Scope 已明确为离线读取、独立 UTC 窗口、可选 A2 引用。实际实施前 owner 锁定审批后 Spec/Plan 以及 当前五仓提交。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [T4-05A1](../spec/2026-11-05-16-56-expense-release-evidence-gate.md) | Review and header SHA | All | 新查询 |
| Dependency | [T3-12](../spec/2026-11-05-15-13-expense-four-path-acceptance.md#9-interface-definitions) | Source64 delivered | §9/§14 | 现有原始 manifest/witness |
| Dependency | [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) | Committed 2f10711b152bbce7000ba7fb31d6f931e4f52927 | §9/§10 | 现有 canonical candidate |
| Dependency | [A2](../spec/2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) | Committed 74369e760829d60e41d658b780681a54efcb22a0 | §9.2 output only | 可选、独立 pin 的现有离线结果引用 |

### 2.3 Superseded or excluded content

弃用初稿推测的 7 天和 24 小时值。固定 profile 仍拥有四组门禁。T4-05 全矩阵、运行时授权、L/模型质量及负载 SLO 各自保持原范围。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | T4-05 §4/§9, A1 §9/10, A2 §9.2 | Pin candidate, archives and five repos | A1 两类摘要、实际归档、五仓和可选 A2 raw pin 一致 | CLI provenance |
| REQ-002 | T4-05 §7/§9 | Verify actual owning reports | FQCN/module/selector、实际案例及 F/E/S 对应 | XML and policy |
| REQ-003 | T4-05 §6.3/§9 | Distinguish stale, failed, skipped, compiled, absent | 关闭状态与原因，UTC 边界精确 | classifier |
| REQ-004 | T4-05 §10, T3-12 §9/§14 | Require actual E durable witness inputs | 两套 manifest/witness、六/二精确路径、不同环境及进程 | E consumer |
| REQ-005 | T4-05 §9/§15 | Bound scope and safe output | POSIX、只读 Git、固定错误、无 credential 读取 | reader and CLI |
| REQ-006 | T4-05 §16 and A1/A2 scope | Scoped review only | 独立审阅结果、A2 可缺席、无 live 结论 | verdict and evidence |
| REQ-007 (T3-12) | T3-12 §14–16 | Preserve current finite/E separation | 有限测试独立，真实缺席或跳过可见 | Validation |
| REQ-008 (T3-12) | T3-12 §8/§14 | Preserve shared fixture contract | 复用已提交路径常量、无 web/fixture 变更 | Compatibility |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Test → fixed profile → one checker → finite proof → same-commit docs. 现有 A1/A2、Surefire 和 T3-12 harness 继续作为唯一证据来源。

### 4.2 Test-first strategy

| Gate | Before behavior | Expected failure | GREEN |
| --- | --- | --- | --- |
| R1 | File 1 presence test only | Absent checker, one expected assertion | File 3 exists |
| R2 | Files 1/2 and File 3 A-only CLI/run seam | Valid fixture returns fixed stub 65, expected 0 | File 3 B |
| G1 | File 3 B | Any unexpected failure, skip or invalid status blocks | Seven exact finite methods |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Reviewed pair and clean source locks | Read-only artifact shape checks | Same script/docs write scope or capture of final owner Git lock | Exact provenance and staging |

### 4.4 Commit boundaries

一个 tianji-service commit，精确六路径。固定 profile、checker 与测试是一个行为。三个文档记录审阅和有限结果。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Query | Add §7.0 | Existing source-reactor executes, does not inspect | Manual report tally | One local bounded read | One script |
| Profile | Add §7.2 | Exact Source64 XML and I/E owner selectors | Inline gates would hide versioned scope | One adjacent JSON | One policy |
| Owner artifacts | Keep §7.3 | A1/A2, reports and E witness | Reuse formats | No producer changes | Read only |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Test | REQ-001–008 | R1/R2 | Existing Python | Finite behavior contract | Checker | Step 1 |
| Policy | REQ-002/003/004 | R2/G1 | Source64 and I/E owners | Fixed owner/witness matrix | Checker | Step 1 |
| Checker | REQ-001–008 | G1 | Python/Git and raw local files | Scoped result | Reviewer | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | scripts/verify-source-reactor.sh uses Python | §6/§8 local tooling | One standard-library script | Step 1 MC-ARCH-001 |
| Reuse/capability | Existing Surefire/A1/A2/T3-12 | §7.3 | Reuse current formats | Step 1 MC-REUSE-001 |
| Naming/model/validation/conversion | Existing Java source owners | §8/§10 | Bounded Python input and projection | Step 1 MC-VALID-001 |
| Bean/logging/util/JSON/time/config | Existing command boundary | §6.3/§9 | Fixed diagnostics, UTC, no Spring | Step 1 MC-UTIL-001 |
| Business variation/pattern | Four fixed groups | §13 | Direct classification table | Step 1 MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Source lock | Existing source reactor and Git | Four conventional siblings | Fixed read-only Git metadata | Reuse | Python subprocess | Step 1 MC-REUSE-001 |
| Candidate/report | A1/A2 and actual owning XML | Committed A1, reviewed A2 output and Source64 | Current bytes suffice | Reuse | Strict parser | Step 1 MC-DEP-001 |
| E proof | ExpenseAcceptanceHarness DTO | Current manifest/witness and fixture | Current source owner | Reuse | No new producer | Step 1 MC-REUSE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | No Java files | 1–6 | Python names | S1 | Step 1 | N/A |
| Rule 2 | §6.2/§9 | No Java boundaries | 1–3 | Strict Python | G1 | Step 1 | N/A |
| Rule 3 | §6.2 | DTO read only | 1–3 | No Java model | S1 | Step 1 | N/A |
| Rule 4 | §6.2 | No Spring | 1–3 | Fixed stderr | G1 | Step 1 | N/A |
| Rule 5 | §6.2 | Python standard lib | 1–3 | No dependencies | S1 | Step 1 | N/A |
| Rule 6 | §6.2 | Existing producer bytes | 1–3 | Closed Python projection | G1 | Step 1 | N/A |
| Rule 7 | §6.2 | No runtime profile | 1–6 | Offline policy only | S1 | Step 1 | N/A |
| Rule 9 | §6.2 | One bounded query | 1–3 | Direct | G1 | Step 1 | N/A |
| Rule 10 | §6.2/§6.3 | Python only | 1–3 | UTC ns | G1 | Step 1 | N/A |
| Rule 11 | §6.2/§11 | Current owner boundaries | 1–6 | No layer/event/SQL | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | run start/finish, compile capturedAt, E observedAt/child start | Occurred instant | Python UTC epoch ns integer | Not persisted | UTC Z | Not applicable: operational UTC | Not applicable: CLI | start≤child≤observed≤finish≤now, finish/capture within window | 0–9 digits exact | No report mtime inference | Step 1 G1 TEST-004/005 |
| TIME-002 | auditFrom/auditUntil, auditWindow | Occurred instant | Python UTC epoch ns integer | Not persisted | UTC Z | Not applicable: reviewer UTC input | Not applicable: CLI | from < until ≤ now, lower inclusive | One time.time_ns sample, no rounding | New explicit window | Step 1 G1 TEST-004 |
| TIME-003 | optional A2 issuedAt/expiresAt | Occurred instant | Python UTC epoch ns integer | Not persisted | UTC Z | Not applicable: A2 UTC | Not applicable: not copied | Strict UTC seconds and positive interval, not gate freshness | No rounding | A2 reference shape | Step 1 G1 TEST-006 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | scripts/test_verify_release_evidence.py | Existing raw report shapes | Seven finite methods | Verify one query | Step 1 | REQ-001–008 | R1/R2/G1 |
| CREATE | scripts/release-evidence-policy-v1.json | Source64 + existing I/E classes | Fixed v1 rules | Exact coverage | Step 1 | REQ-002/003/004 | G1 |
| CREATE | scripts/verify-release-evidence.py | Existing scripts boundary | One CLI | Offline query | Step 1 | REQ-001–008 | G1 |
| CREATE | docs/egon/evidence/2026-11-05-expense-release-evidence-gate.md | Existing Source64/A1 evidence | Actual finite proof | Scope closure | Step 1 | REQ-001–008 | S1 |
| CREATE | docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md | Reviewed scratch Spec | Exact locked bytes | Contract | Step 1 | REQ-001–008 | S1 |
| CREATE | docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md | Reviewed scratch Plan | Exact locked bytes | Execution | Step 1 | REQ-001–008 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact final source lock tianji-service fafa874e460ca87a56ea652cd651c219a5028faa with A2 and AS Link committed, Web e5d14225abacd4766aa21f9b59ce2364cd23bb3b clean. Existing siblings are Components 87af09fea9a9d58e7127b009d76f60438c79e569, Shoubing e423428e01591c122551f0c11aca8a4ffda9b4c1, Jianshen 3afec38631ff16f44018a297c1267d48fc04a508, Tianshu b1c607f4d8a03855be8179d73849ebfb4488468b. Recheck all five clean HEADs and actual committed A2 source/output before execution, and preserve unrelated work. No more specific scripts AGENTS.md was observed.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| Python | python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py | Local standard library | Finite only |
| Source | scripts/verify-source-reactor.sh fixed sibling layout | POSIX and exact Git locks | Metadata read only |
| Raw owners | Source64 G7 raw reports, FormalCasePostgresTest, BusinessProcessAgentAcceptanceIT, WorkerRecoveryIT | Exact XML ownership and manifest/witness shapes | No I/E execution |
| Candidate | Actual A1 canonical contract and A2 output | Exact target and independent pins | No signer/credentials |

### 6.3 Immutable constraints and approved decisions

Keep source-reactor/POMs/CI/Java/DB/web untouched. Explicit UTC audit window, no duration defaults. A2 independently pinned optional reference. No inference from compile or synthetic evidence.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | Internal clock/Git call seams only | §14 test design | No public test flags | S1 failure |
| PLAN-CLAR-002 | Historical raw XML/candidate only anchor shapes | Source64 and A1 provenance | No current PASS reuse | Return to fixture review |
| PLAN-CLAR-003 | One evidence document archives actual finite proof | §8 and existing evidence | No authority | Stage blocks |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付只读 expense 证据判定

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007 (T3-12 compatibility), REQ-008 (T3-12 compatibility).
- Dependencies: Reviewed pair, committed A1/A2 output contract and clean five-repo lock.
- Baseline state: Existing producers, no combined verifier.
- Observable outcome: Exactly one scoped result per verified candidate/audit interval.
- End state: Six exact paths in one service commit.
- Test-first gate: Required. R1 explicit absence, R2 behavior, G1 complete finite.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 11 applies to owner boundaries. No Java files.
- Ordered files:

#### File 1 — `CREATE scripts/test_verify_release_evidence.py`

- Purpose: Freeze checker behavior through standard unittest.
- Symbols: EvidenceGateTest with test_cli_entrypoint_exists and the six methods below.
- Repository evidence: Existing Python use in source-reactor, Source64 raw XML and expense fixture contracts.
- Dependencies and consumers: File 3 run, fixed policy, temporary private files, fake Git and UTC.
- Why now: First R1 assertion, then R2 on compiling A-only seam.
- Contract/signature changes: New test class only. File 3 run(argv, stdout, stderr, *, now_ns=None, git_runner=None) accepts internal test overrides.
- Input/output and state mapping: Independently build minimal canonical A1, optional typed A2, synthetic archive bytes, 14 U XML, I and E raw fixtures. Freeze exact result and no protected output.
- Error and edge behavior: Test symlink/hardlink, group write, DTD/oversize/UTF8/JSON, mixed module/selector/duplicates/counts, FAILED/SKIPPED/STALE/COMPILED_ONLY/NOT_EXECUTED, audit limits, wrong source/artifact/A2 pins and distinct E witnesses.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 11. Uses current owners and no runtime.
- Implementation pseudocode:

```python
class EvidenceGateTest(unittest.TestCase):
    def test_cli_entrypoint_exists(self):
        self.assertTrue(CHECKER.is_file())
    def test_valid_bundle_is_scoped_ready(self):
        outcome = evaluate(synthetic_private_bundle(), utc_clock, five_clean_heads)
        self.assertEqual(0, outcome.exit_code)
        self.assertEqual(EXPECTED_SAFE_BYTES, outcome.stdout)
    def test_rejects_unsafe_or_malformed_evidence(self):
        assert_fixed_errors_for_invalid_inputs_and_io()
    def test_blocks_unexecuted_and_compiled_only(self):
        assert_states("PASS", "NOT_EXECUTED", "COMPILED_ONLY", "COMPILED_ONLY")
    def test_classifies_actual_reports_and_audit_window(self):
        assert_failure_skip_stale_missing_zero_and_exact_nanosecond_boundaries()
    def test_checks_exact_expense_witnesses(self):
        assert_exact_paths_status_store_child_and_identity_combinations()
    def test_binds_five_repositories_candidate_and_optional_a2(self):
        assert_clean_detached_head_and_independent_artifact_reference_matrix()
```

- Verification contribution: R1/R2/G1.
- After this file: R1 one expected failure, no runtime.

#### File 2 — `CREATE scripts/release-evidence-policy-v1.json`

- Purpose: Freeze source-owned selectors and witness requirements.
- Symbols: schemaVersion=1, profileId=expense-release-evidence-v1, windowMode=DECLARED_UTC_AUDIT_WINDOW, groups, baseWitnesses and expensePaths.
- Repository evidence: Actual Source64 fourteen XML reports and §7.2 ownership.
- Dependencies and consumers: File 3 fixed adjacent reader and File 1 fixture.
- Why now: Typed checker uses fixed expected selectors and report scopes.
- Contract/signature changes: Groups in order U_SOURCE/U, I_CASE/I, E_BUSINESS/E, E_RECOVERY/E. Each report has exact module/className/selector/minimumTests/methods. U methods empty, I/E exact owning methods.
- Input/output and state mapping: Raw policy SHA checked against index. Base and extra witnesses and outcomes from Spec §10. Below is the exact owner table. AS=stellar-enclosure-tianji-agent-server, PS=stellar-enclosure-tianji-process-server, W=stellar-enclosure-tianji-worker-starter.
- Error and edge behavior: No input policy overrides or arbitrary duration. Malformed local policy gives top-level invalid.
- Standards impact: MC-CONFIG-001, MC-VALID-001, MC-JSON-001.
- Literal rule enforcement: Rule 11. Versioned local offline policy.
- Implementation pseudocode:

```json
{
  "schemaVersion": 1,
  "profileId": "expense-release-evidence-v1",
  "windowMode": "DECLARED_UTC_AUDIT_WINDOW",
  "groups": [{"id": "U_SOURCE", "level": "U", "reports": []}],
  "baseWitnesses": [],
  "expensePaths": {}
}
```

Populate the illustration with the complete groups/table and witnesses in Spec §10. Selector equals class basename. I/E minimumTests is exact method count.

| Group | Module | ClassName | Minimum or exact methods |
| --- | --- | --- | --- |
| U_SOURCE | AS | top.egon.cola.platform.tianji.agent.server.acceptance.ExpenseAcceptanceModelTest | 2 |
| U_SOURCE | AS | top.egon.cola.platform.tianji.agent.server.integration.process.AgentResultDeliveryHandlerTest | 6 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarnessTest | 9 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.acceptance.ExpenseFixtureModelsTest | 4 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.bridge.AgentStartWorkerTest | 10 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixtureTest | 15 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.release.CmmnModelValidatorTest | 67 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.release.HumanTaskModelTest | 5 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.release.HumanTaskTimerModelTest | 5 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.release.ProcessModelValidatorTest | 19 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.release.WorkerCompensationModelTest | 12 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerServiceTest | 16 |
| U_SOURCE | PS | top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistryTest | 2 |
| U_SOURCE | W | top.egon.cola.platform.tianji.worker.RemoteWorkerPollerTest | 11 |
| I_CASE | PS | top.egon.cola.platform.tianji.process.server.FormalCasePostgresTest | native_case_lifecycle_and_exact_bpmn_continuation_are_atomic_and_recoverable |
| E_BUSINESS | PS | top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT | normalWorkflowAcrossBothTenants, failedAgentCompensatesAcrossBothTenants, manualCaseAndAuthorizedReviewAcrossBothTenants |
| E_RECOVERY | PS | top.egon.cola.platform.tianji.process.server.acceptance.WorkerRecoveryIT | recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants |

- Verification contribution: Exact profile, owners, witnesses, and all required groups in G1.
- After this file: Policy exists; File 3 A can produce R2.

#### File 3 — `CREATE scripts/verify-release-evidence.py`

- Purpose: Evaluate four fixed groups and output one safe scoped line.
- Symbols: main(), run(argv, stdout, stderr, *, now_ns=None, git_runner=None), read_ref, parse_candidate, bind_signature_reference, verify_checkout, summarize_report, classify_group, validate_expense_pair.
- Repository evidence: Existing source-reactor layout, actual A1/A2 fields, owner XML and current ExpenseAcceptanceHarness DTOs.
- Dependencies and consumers: Python standard library, adjacent policy, fixed Git metadata. CLI is explicit.
- Why now: Add shape-only return 65 for R2, then full behavior B after observed RED.
- Contract/signature changes: Spec §9.2 exact argv, 0/1/64/65/66. Internal test seams only, production samples now once.
- Input/output and state mapping: Implement closed Spec §9/§10 index/output. Same A1 semantic and original SHA, actual archives, all five clean revisions. Canonical JSON uses sorted keys, ensure_ascii=False, compact separators, no floats or surrogates and single LF. Optional A2 fields/pins tied to same A1, no separate readiness condition.
- Error and edge behavior: Top-level structural errors fixed 65, usage 64, I/O 66. Group malformed evidence reported separately. Stale precedes fresh run statuses. Actual failures/skips precede missing siblings and missing I/E environment; malformed supplied metadata blocks. No output until verdict complete.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-LOG-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 11. Existing owner artifacts only.
- Implementation pseudocode:

```python
def run(argv, stdout, stderr, *, now_ns=None, git_runner=None):
    args = parse_exact_flags(argv)
    now = time.time_ns() if now_ns is None else now_ns()
    audit = utc_half_open_window(args.audit_from, args.audit_until, now)
    policy = read_fixed_adjacent_policy()
    with PrivateRoot.open(args.root) as root:
        index = read_closed_index(root, args.index, policy)
        revisions = verify_checkout(args.checkout, args.expect_revision, index["revisions"], git_runner)
        candidate, envelope_sha = parse_candidate(root, index["candidate"], args.expect_candidate_sha256)
        archives = stream_bind_two_archives(root, index["archives"], candidate, revisions)
        proof = bind_signature_reference(root, index.get("signatureProof"), args.proof_pin, candidate, envelope_sha)
        groups = [classify_group(rule, index, root, revisions, archives, audit, now) for rule in policy["groups"]]
        validate_expense_pair(groups)  # Compare private facts only when both E groups are otherwise PASS.
        result = project_exact_safe_summary(policy, candidate, revisions, archives, audit, proof, groups)
    write_bounded_complete_line(stdout, result, 32768)
    return 0 if all(group.status == "PASS" for group in groups) else 1

def classify_group(rule, index, root, revisions, archives, audit, now):
    run = exact_optional_group_record(rule, index)
    if run is None:
        return valid_current_compile_proof_or_not_executed(rule, index, root, audit, now)
    reports = read_closed_owning_reports(root, run, rule)
    env = validate_supplied_environment(root, run, rule)
    if valid_provenance_differs(run, revisions, archives):
        return stale("SOURCE_LOCK_MISMATCH")
    if not audit.contains(run.finished_at):
        return stale("AUDIT_WINDOW_MISS")
    if reports.has_failure_error_or_rerun:
        return failed("TEST_FAILURE", reports)
    if run.exit_code:
        return failed("RUN_EXIT_NONZERO", reports)
    if reports.skipped:
        return skipped("TEST_SKIPPED", reports)
    if reports.missing_required:
        if not reports.present and group_has_compile_proof(rule, index):
            return valid_current_compile_proof_or_not_executed(rule, index, root, audit, now)
        return not_executed("REQUIRED_REPORT_MISSING", reports)
    if not reports.executed:
        return not_executed("NO_EXECUTED_TESTS", reports)
    return require_actual_matching_witness(rule, run, reports, env)
```

- Path and digest implementation: Canonical absolute private root, descriptor-relative open with O_NOFOLLOW, same owner and modes, dev/inode/size/mtime_ns/ctime_ns before and after, path/inode uniqueness, bounded reads and streaming 512 MiB archives. Fixed max counts derive from four runs, 17 reports, three compile proofs, four E refs, two archives, candidate and optional A2. Never open paths embedded in manifest/XML. Strict UTF-8/duplicate/shape/depth JSON. A1 SHA domain plus NUL and exact canonical object, raw canonical envelope plus LF. A2 validates strict existing output, raw independent SHA, both candidate digests and source/scope, records REFERENCE_MATCHED or NOT_SUPPLIED.
- Source lock implementation: Git roots exact checkout plus four conventional siblings. Use git --no-optional-locks -c core.fsmonitor=false -c core.untrackedCache=false -C ROOT with rev-parse --show-toplevel, rev-parse --verify HEAD^{commit}, status --porcelain=v1 -z --untracked-files=all --ignore-submodules=none. Timeout 3 s, no shell, disable global/system config and terminal prompts, clear caller Git env. Exact clean detached HEAD is valid. Dirty/mismatch invalid, tool failure unavailable, no raw output.
- Report implementation: Decode UTF-8, reject DTD/ENTITY, parse bounded ElementTree. Exact TEST-<FQCN>.xml name, testsuite, property test and basedir once, unique selector set and owning module basename. Count direct actual testcase statuses, reject conflicting/duplicate counts and unknown owners or methods. A missing expected method or U count below minimum makes otherwise green coverage incomplete, while actual fresh failure/skip remains visible. Rerun/flaky failures/errors are failures even where Surefire counters omit them. Only a matching expected class proof, raw SHA and CAFEBABE, with owning report absent, can yield COMPILED_ONLY.
- E implementation: Match the actual Manifest/Evidence DTO shapes, run/suite, runAuthorized, identical current artifact/test-class-tree/revision hashes and exact two tenants. Require the same tenant ID pair and three source-specific store owners with isolated disjoint database names between suites, no credential or embedded path reads. Require child roles, ordered generations 1..n and start, distinct PIDs and matching exits, minimum two generations per role in RECOVERY. Match six BUSINESS and two RECOVERY results, per-scenario shared input keys across tenants with disjoint process/run/receipt references and exact witness kind/status/terminal rules in Spec §10. Nonzero owned child shutdown is recorded, distinct from the Surefire test exit.
- Verification contribution: R2 fixed missing behavior then G1 closed input/output and state matrix.
- After this file: Finite checker ready, no owner test or runtime execution.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-expense-release-evidence-gate.md`

- Purpose: Record actual finite gates and runtime boundary.
- Symbols: R1/R2/G1/S1 and exact five-repo source lock.
- Repository evidence: Existing A1/Source64 evidence conventions.
- Dependencies and consumers: Actual finite results and final path-scoped audit.
- Why now: Capture observed facts after G1.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Exact commands, two deliberate RED results, seven actual finite tests, source/Spec/Plan locks and real I/E/L unexecuted.
- Error and edge behavior: No invented result, raw report content or secret.
- Standards impact: MC-TEST-001, MC-LOG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 11. Same local owner.
- Implementation pseudocode:

```text
read actual R1/R2/G1 unittest output and compare selected test counts
record exact selectors, finite pass/failure counts, five Git revisions and approved Spec/Plan hashes
record separately which input checks used synthetic or historical artifacts
verify current matching I/E/L and live signer are outside this source-only proof
retain bounded evidence without raw files or credential references
```

- Verification contribution: S1.
- After this file: Reviewable proof ready.

#### File 5 — `CREATE docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md`

- Purpose: Archive the approved governing design.
- Symbols: T4-05A1 requirements and Related Plans.
- Repository evidence: Existing service Spec naming and dependency links.
- Dependencies and consumers: Exact owner-approved scratch Spec and File 6.
- Why now: Lock before code, archive in same focused commit.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Exact SHA and original approved bytes to this path.
- Error and edge behavior: Occupied path or differing bytes blocks.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Existing owner.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md
cp -n "$T405_APPROVED_SPEC" docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md
cmp "$T405_APPROVED_SPEC" docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md
```

- Verification contribution: S1 strict Spec.
- After this file: Exact Spec bytes ready for link validation.

#### File 6 — `CREATE docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md`

- Purpose: Archive approved plan.
- Symbols: Implements Spec and exact Step 1.
- Repository evidence: Existing service Plan naming.
- Dependencies and consumers: Exact owner-approved scratch Plan and File 5.
- Why now: Preserve actual execution contract and scope.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Exact approved bytes to this path.
- Error and edge behavior: Occupied path, wrong SHA or broken relation blocks.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. One owner.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md
cp -n "$T405_APPROVED_PLAN" docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md
cmp "$T405_APPROVED_PLAN" docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md
```

- Verification contribution: S1 strict Plan.
- After this file: Six exact paths ready for static audit and commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/R2/G1/S1 in §8.
- Expected result: Two expected one-method REDs and seven finite actual passes.
- Failure returns to: File 1 fixtures, File 2 policy, File 3 implementation, Files 4–6 proof or links.
- Completion criteria: Exact scoped readiness and honest finite proof.
- Rollback: Revert this one Step commit.
- Commit paths: `scripts/test_verify_release_evidence.py`, `scripts/release-evidence-policy-v1.json`, `scripts/verify-release-evidence.py`, `docs/egon/evidence/2026-11-05-expense-release-evidence-gate.md`, `docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md`, `docs/egon/plan/2026-11-05-18-08-expense-release-evidence-gate.md`.
- Commit: feat(release): verify offline expense evidence.

## 8. Test, Validation, and Quality Gates

Work from /Users/mario/SelfProject/Stellar-Enclosure/tianji-service after source GO.

```sh
python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py -k test_cli_entrypoint_exists
python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py -k test_valid_bundle_is_scoped_ready
python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py -v
git diff --check
git diff --cached --check
```

| Gate | Exact expected result | Failure returns to |
| --- | --- | --- |
| R1 | One assertion on absent checker, no discovery failure | File 1 |
| R2 | One assertion expected 0 actual stub 65, no fixture failure | Files 1–3 A |
| G1 | Seven actual passing methods, zero failures/errors/skips, no real I/E | Files 1–3 B |
| S1 | validate_spec.py and validate_plan.py --strict on Files 5/6; six-path staged diff and accurate EVD | Files 4–6 |

Use actual archived owning XML only as parser-shape anchors. Synthetic fixture success is neither I/E execution nor same-HEAD release readiness. Before one commit, stage only §7 paths and compare staged SHA/path list with approval scope.

## 9. Migration, Compatibility, Rollout, and Rollback

No migrations, rollout, applications or CI. Existing A1/A2 and raw owner contracts remain. Revert one focused commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | T4-05 §9, A1/A2 §9 | Step 1 | 1/3/4 | TEST-002/006 | Exact hashes and source locks |
| REQ-002 | T4-05 §7/§9 | Step 1 | 1/2/3 | TEST-001/003/004 | Raw report ownership |
| REQ-003 | T4-05 §6.3/§9 | Step 1 | 1/3/4 | TEST-003/004 | Distinct statuses and audit window |
| REQ-004 | T4-05 §10/T3-12 §9 | Step 1 | 1/2/3 | TEST-005 | Exact E witnesses |
| REQ-005 | T4-05 §9/§15 | Step 1 | 1/3/4 | TEST-002/005/006 | Bounded safe diagnostics |
| REQ-006 | T4-05 §16 and A1/A2 | Step 1 | 1/3/4 | TEST-001/003/006 | Scoped verdict |
| REQ-007 (T3-12) | T3-12 §14–16 | Step 1 | 1/3/4 | G1/S1 | Finite/E distinction |
| REQ-008 (T3-12) | T3-12 §8/§14 | Step 1 | 2/3 | TEST-005/S1 | Existing fixture contract preserved |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- |
| LOCK-001 | Parallel AS work may advance service baseline | Step 1 | Committed A2 and observed unrelated files | Release owner | Relock five current clean HEADs and review pair before GO |
| RISK-001 | Operator-controlled raw reports are unsigned | File 3 | T4-05 §10 | Evidence owner | Use independent pinned inputs; live authority separate |
| RISK-002 | Current matching true I/E not observed | File 4 | Source64 | Runtime operator | Real readiness remains blocked absent evidence |
| RISK-003 | Auditor interval may differ between runs | File 3 | Explicit UTC output | Reviewer | Keep exact audit inputs with proof |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

One offline checker; current source and exact owner evidence. No producer execution.

### 12.2 Spec consistency

A1 candidate contract, optional typed A2 reference, four groups, explicit UTC window and fixed source-only scope agree.

### 12.3 Repository executability

Source reactor, 14 raw reports, native Case I, actual E DTOs, A1/A2 contracts and five Git heads were inspected. Concurrent AS activity requires fresh source lock.

### 12.4 Test and release completeness

Finite RED/GREEN and exact review scope. I/E, live signing, deployment and SLO separately outstanding.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing scripts owner | None |
| MC-REUSE-001 | Applicable | PASS | §4.7/§7 | Existing owner artifacts | None |
| MC-DEP-001 | Applicable | PASS | §6 | Python/Git only | None |
| MC-NAME-001 | Not applicable | N/A | §5 | No Java | None |
| MC-VALID-001 | Applicable | PASS | §7 | Exact ownership and validation | None |
| MC-MODEL-001 | Not applicable | N/A | §5 | No Java or persistent model | None |
| MC-CONVERT-001 | Not applicable | N/A | §7 | Closed local projection | None |
| MC-LOG-001 | Applicable | PASS | §7 | Fixed private diagnostics | None |
| MC-BEAN-001 | Not applicable | N/A | §5 | No Spring bean | None |
| MC-UTIL-001 | Applicable | PASS | §4.7/§7 | Standard library and Git | None |
| MC-JSON-001 | Applicable | PASS | §7 | Exact candidate and result | None |
| MC-TIME-001 | Applicable | PASS | §4.9/§7 | Explicit UTC half-open boundary | None |
| MC-CONFIG-001 | Applicable | PASS | §5/§7 | Fixed offline policy, no duration defaults | None |
| MC-PATTERN-001 | Not applicable | N/A | §4 | Direct query | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | Three source and three docs | None |
| MC-TEST-001 | Applicable | PASS | §7/§8 | Finite and exact scope | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Scope ready for review | Relock actual source/approval before GO |

### 12.6 Final verdict

PASS — Ready for user review

本次只编制仓库外 Spec 和 Plan。源码、Maven、有限测试、应用、数据库、CI 和浏览器均未执行或修改。
