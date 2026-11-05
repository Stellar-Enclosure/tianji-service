# T4-03B ADK BaseAgent 原生恢复有限证据实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-18-49-adk-native-resume-evidence.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 18:49 Asia/Shanghai |
| Updated | 2026-11-05 18:49 Asia/Shanghai |
| Owner | Tianji AR |
| Repository | tianji-service |
| Scope | 一个 AR JUnit、Spec、Plan 和实际验证证据 |
| Source Requirement | Master T4-03 原生窗口有限验证 |
| Baseline Revision | 6e4997d2f352bba04e53bc404306079aab9d4ae0 |
| Implements Spec | [T4-03B](../spec/2026-11-05-18-48-adk-native-resume-semantics.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 18:48 Asia/Shanghai, SHA-256 a2941c837d5d17a8bb504ea450c7f7d823b49a89f81d91c7bd851daa0ab4aade |
| Effective Specs | [T4-03B](../spec/2026-11-05-18-48-adk-native-resume-semantics.md) |
| Depends On Plans | [T4-03A](2026-11-05-17-42-agent-recovery-disposition.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master T4-03/S11](../../../../docs/plans/agent-framework-upgrade-plan.md#t4-03--有界checkpoint失联恢复与对账) |

## 1. Summary

Root approved the exact finite evidence-only source under the user master Plan. The test exercises pinned ADK with a local Fake and an in-memory SessionService; no production App, checkpoint, authorization or execution path changes.

将已实测的 pinned ADK 语义固化在 AR 测试。覆盖已保存的读结果可续跑，工具已执行但结果未保存时再次执行，以及等待回答、新文本与显式同 ID 的区分。一个聚焦提交。

## 2. Target Spec and Effective Design

T4-03B §§7/14 定义三项测试。T4-03A §16 的 owner 只读处置和独立运行授权边界继续有效。Master T4-03/S11 的 PostgreSQL、重启和安全恢复验收仍独立。Spec/Plan 处于 Review，实际 source GO 由决策人记录。

## 3. Effective Requirements and Acceptance

| ID | Expected evidence |
| --- | --- |
| REQ-001 | 保存结果后同 ID 续跑，新模型一次、总工具一次、保存前缀不变 |
| REQ-002 | 因果屏障后仅保存调用，真实工具计数一变二 |
| REQ-003 | 明确完成/未知/禁用、普通新文本、挂起 null、错误/匹配回答 |
| REQ-004 | 三项实际有限 JUnit 以及准确的生产/S11 边界和范围核对 |

## 4. Implementation Strategy and Dependency Order

同包、同 ADK 依赖和现有 Maven 有限验证。先锁依赖和差异基线，创建测试和审阅文档，按 §8 获取真实 owning JUnit，再填写 EVD、检查和单提交。

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Owner | AR AdkReadOnlyDecisionLoopTest | 同一技术模块测试包 |
| Data | ADK Event 和 Session | 直接使用 upstream 事件及不可变 test result |
| Layering and dependencies | service parent POM and AR POM | No application layer, framework or dependency change |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Native execution | Runner, LlmAgent | ADK 1.13.0 | Actual same-ID branch | Reuse |
| Test runtime | JUnit, RxJava | Existing AR tests and dependencies | Finite assertions and causal cut | Reuse |
| Session | InMemorySessionService, PostgresAdkSessionService | Real upstream in-memory storage, production PG adapter separate | Isolates native mechanism | Use in-memory |
| Test-only scope | Spring Boot/Components | Current wrappers serve production | No Spring context needed | Preserve current paths |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | AR JUnit convention | File 1 | Exact test name and NativeOutcome | G1 | 1 | PASS |
| Rule 2 | §6.2 | Typed ADK signatures | File 1 | No layer handoff | G2 | 1 | N/A |
| Rule 3 | §6.2 | Existing records and fixtures | File 1 | Immutable stream result | G1 | 1 | PASS |
| Rule 4 | §6.2 | Test runs without Spring | File 1 | No production business component | G2 | 1 | N/A |
| Rule 5 | §6.2 | JDK, JUnit and native APIs | File 1 | No production utility | G2 | 1 | N/A |
| Rule 6 | §6.2 | ADK native Event | File 1 | No serialization contract changes | G2 | 1 | N/A |
| Rule 7 | §6.2 | App fixture | File 1 | No environment configuration | G2 | 1 | N/A |
| Rule 9 | §6.2 | Three technical assertions | File 1 | Direct setup and controls | G2 | 1 | N/A |
| Rule 10 | §6.3 | Watchdog only | File 1 | Bounds only, no business time | G1 | 1 | N/A |
| Rule 11 | §§6/8 | Existing AR owner and CQE boundary | Files 1–4 | No production state/API changes | G2 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable
Reason: JUnit and Rx watchdogs only. No business timestamp, wire/storage format, scheduling or time-zone conversion changes.

## 5. Change File Tree

| Operation | Exact path | Role |
| --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeResumeSemanticsTest.java | Existing ADK semantics |
| CREATE | docs/egon/spec/2026-11-05-18-48-adk-native-resume-semantics.md | Reviewed design |
| CREATE | docs/egon/plan/2026-11-05-18-49-adk-native-resume-evidence.md | Approved execution |
| CREATE | docs/egon/evidence/2026-11-05-adk-native-resume-semantics.md | Actual G1 and G2 proof |

## 6. Prerequisites, Constraints, and Plan Clarifications

Verify explicit source GO, clean exact service baseline (or independently recorded clean fast-forward without relevant drift), the existing scratch Maven settings, JDK 25 and cached artifact hash. Unset provider credentials through the established source verifier environment. Existing reactor resolves dependencies offline. All production paths are excluded. No alternative dependency or scratch production shim.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Pin three native BaseAgent resume semantics

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Reviewed exact Spec, Plan and source GO. Completed T4-03A and actual pinned binary.
- Baseline state: 6e4997d2f352bba04e53bc404306079aab9d4ae0, no durable native resume test in the exact AR package.
- Observable outcome: Three actual native cases pass including the unsafe duplicate execution counterexample.
- End state: One test and three documents in one commit.
- Test-first gate: Not applicable. The change records existing pinned upstream behavior and adds no production implementation. An optional assertion mutation may verify sensitivity outside the repository.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 3, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeResumeSemanticsTest.java`

- Purpose: Preserve executable pinned ADK behavior.
- Symbols: AdkNativeResumeSemanticsTest, NativeOutcome, ScriptedModel, ReadOnlyTools, Fixture.
- Repository evidence: Existing AR AdkReadOnlyDecisionLoopTest, ADK 1.13 Runner and SessionService.
- Dependencies and consumers: Current AR JUnit and cached ADK, no production consumers.
- Why now: Make the observed positive/negative boundary durable.
- Contract/signature changes: Three test methods only.
- Input/output and state mapping: Natural Session event history, exact invocation IDs, matching call/response, root marker and tool/model counts.
- Error and edge behavior: Unknown and disabled calls refused. Completed/no-input wait inert. Mismatched response refused. Tool-before-append cut can repeat a real tool.
- Standards impact: MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 3, Rule 11. Same AR test package, immutable result and technical fixture boundaries.
- Implementation pseudocode:

```java
fixture = real InMemorySessionService + Runner(App(resumable=true, plaintextAutoResume=false), new LlmAgent(fake))
save read result through native flow; cut after response; open new Runner and exact same invocation with null input
assert stored prefix and single original prompt; new model once; read count one; assert new root end marker
cut from native afterTool callback before response append; verify read count one; explicit resume; assert count two
test unknown, completed, disabled and fresh controls; native long-running empty map waits until exact FunctionResponse
```

- Verification contribution: G1 actual JUnit XML and three assertions groups.
- After this file: Native cases are discoverable and runnable with the current dependency.

#### File 2 — `CREATE docs/egon/spec/2026-11-05-18-48-adk-native-resume-semantics.md`

- Purpose: Fix the test scope and the native safety boundary.
- Symbols: REQ-001 through REQ-004, TEST-001 through TEST-004.
- Repository evidence: T4-03A, Master S11 and AR source owner.
- Dependencies and consumers: Existing approved scopes and this Plan.
- Why now: Preserve exact contract for evidence review.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Four requirements, four exact paths and future runtime gates.
- Error and edge behavior: The duplicate invocation window remains explicit and production activation remains separate.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing production owner and CQE boundaries.
- Implementation pseudocode:

```text
copy the reviewed Spec bytes matching the Plan's exact canonical filename and pinned SHA
verify the T4-03A disposition relation and Master T4-03/S11 boundary links
confirm the four-file inventory and exact positive, duplicate and function-response cases match File 1
```

- Verification contribution: G2 strict Spec and inventory.
- After this file: Normative reviewed candidate exists at the canonical path.

#### File 3 — `CREATE docs/egon/plan/2026-11-05-18-49-adk-native-resume-evidence.md`

- Purpose: Record bounded source execution and scope verification.
- Symbols: Step 1, G1, G2.
- Repository evidence: Existing scripts/verify-source-reactor.sh and T4-03A evidence workflow.
- Dependencies and consumers: File 2, reviewer and execution owner.
- Why now: Pin the staged evidence order and exact selector.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact path list, actual named XML and commit criteria.
- Error and edge behavior: No success inferred from process exit without the owning report and counts.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 confines all tests to existing AR infrastructure.
- Implementation pseudocode:

```text
copy the approved Plan bytes into the single exact Plan file and verify primary Spec link and hash
compare the one Step inventory with expected four repo-relative paths before staging
preserve the exact finite selector and require the owning 3/0/0/0 report with no runtime activation
```

- Verification contribution: G2 strict Plan.
- After this file: Scope and gates available at the committed location.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-adk-native-resume-semantics.md`

- Purpose: Publish actual bounded results.
- Symbols: G1 count, G2 audit and S11 disposition.
- Repository evidence: Existing T4-03A evidence convention and actual owner report.
- Dependencies and consumers: Files 1–3, exact source-run report and SHA audit.
- Why now: Record proof only after actual execution.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Pinned JDK/ADK, causal evidence and all verification limits.
- Error and edge behavior: Report a failed or missing source gate as blocked. Do not state real CONFIG/runtime recovery acceptance.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves test-only technical scope and existing owner boundaries.
- Implementation pseudocode:

```text
read fresh owning Surefire XML, preserve actual selector and verify three tests with zero failures/errors/skips
record old call and saved result boundaries separately, and actual tool count one then two
compare staged paths and migration baseline, cite unresolved PG/lease/receipt/activation and real process restart acceptance
```

- Verification contribution: Reviewable actual proof with its limitations.
- After this file: One commit is eligible after G2.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G1 and G2.
- Expected result: 3 tests, zero failures/errors/skips, strict documents, no scope drift.
- Failure returns to: Exact failing gate. Never bypass JUnit, alter pinned dependency, or broaden scope.
- Completion criteria: Fresh owning JUnit, documented safety boundary and exact staged inventory.
- Rollback: Before commit withdraw only these four task paths. After commit a focused revert restores the prior evidence inventory.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeResumeSemanticsTest.java`, `docs/egon/spec/2026-11-05-18-48-adk-native-resume-semantics.md`, `docs/egon/plan/2026-11-05-18-49-adk-native-resume-evidence.md`, `docs/egon/evidence/2026-11-05-adk-native-resume-semantics.md`.
- Commit: test(agent): pin native ADK resume boundaries.

## 8. Test, Validation, and Quality Gates

G1 runs only the selected finite owning class using the existing source reactor wrapper. Verify fresh AR target/surefire-reports/TEST-top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkNativeResumeSemanticsTest.xml has exactly 3 tests and 0 failures/errors/skips. Do not select PostgresTest or IT. G2 runs both strict document validators, git diff --check, git diff --cached --check and staged inventory audit against §5. Inspect production/migrations diff against the locked baseline.

```sh
t4_03b_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
./scripts/verify-source-reactor.sh -gs "$t4_03b_settings" -s "$t4_03b_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=AdkNativeResumeSemanticsTest
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-18-48-adk-native-resume-semantics.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-18-49-adk-native-resume-evidence.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

## 9. Migration, Compatibility, Rollout, and Rollback

No runtime or migration activation. The existing AR configuration, dependencies, production runners, database and APIs are outside the commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | 1 | G1 saved result |
| REQ-002 | 1 | G1 duplicate invocation |
| REQ-003 | 1 | G1 native controls |
| REQ-004 | 1 | G1/G2 |

## 11. Risks, Blockers, and User Decisions

The native result is limited to one BaseAgent fixture. T4-03B neither grants resume authority nor satisfies S11. Exact approval is required before source execution. Runtime effect uncertainty remains fail-closed under the existing owner.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/§5 | Current AR test placement | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Current ADK, JUnit and SessionService | None |
| MC-DEP-001 | Applicable | PASS | §5/§8 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | Step 1 File 1 | Exact named technical test | None |
| MC-VALID-001 | Not applicable | N/A | §4.7 | No new layer contract | None |
| MC-MODEL-001 | Applicable | PASS | Step 1 File 1 | Immutable native outcome, no production DTO | None |
| MC-CONVERT-001 | Not applicable | N/A | §4.7 | No mapping layer | None |
| MC-LOG-001 | Not applicable | N/A | §5 | No logging source | None |
| MC-BEAN-001 | Not applicable | N/A | §4.7 | No Spring startup | None |
| MC-UTIL-001 | Applicable | PASS | §4.7/§8 | Current SDK and finite JDK/JUnit/Rx | None |
| MC-JSON-001 | Not applicable | N/A | §4.7 | No codec | None |
| MC-TIME-001 | Not applicable | N/A | §4.9 | Watchdogs only | None |
| MC-CONFIG-001 | Not applicable | N/A | §9 | Test-local App only | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.7 | No complex business behavior | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§8/§11 | Four exact paths and independent runtime gate | None |
| MC-TEST-001 | Applicable | PASS | §7/§8 | Native 3-case finite report | None |
| MC-BLOCKER-001 | Applicable | PASS | §6/§11 | Technical evidence scope closed | Source GO before execution |

**PASS — Ready for user review**
