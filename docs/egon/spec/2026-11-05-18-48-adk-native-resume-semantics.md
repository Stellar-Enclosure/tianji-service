# T4-03B ADK BaseAgent 原生恢复语义证据

| Field | Value |
| --- | --- |
| Document | 2026-11-05-18-48-adk-native-resume-semantics.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Technical Design |
| Complexity | Simple |
| Complexity Drivers | 三个有界技术测试固定一个 ADK BaseAgent 场景及对照 |
| Created | 2026-11-05 18:48 Asia/Shanghai |
| Updated | 2026-11-05 18:48 Asia/Shanghai |
| Owner | Tianji AR |
| Repository | tianji-service |
| Scope | AR 既有依赖上的三个有限断言和配套文档 |
| Change Surface | ADK BaseAgent Session 恢复实验及边界证据 |
| Affected Chapters | §7, §8, §14, §15 |
| Source Requirement | Master T4-03 首先验证选定原生恢复窗口和未知工具副作用 |
| Baseline Revision | tianji-service 6e4997d2f352bba04e53bc404306079aab9d4ae0 |
| Amends | None |
| Supersedes | None |
| Depends On | [T4-03A disposition §7/§16](2026-11-05-16-52-agent-recovery-disposition.md#16-compatibility-migration-rollout-and-rollback) |
| Related Specs | None |
| Related Plans | [Master T4-03/S11](../../../../docs/plans/agent-framework-upgrade-plan.md#t4-03--有界checkpoint失联恢复与对账) |

## 1. Summary

保留固定 ADK 1.13.0 对同一 invocation ID 的正反例。已保存读结果的单一 LlmAgent 可在内存会话中续跑。工具返回后、结果保存前被中断时，ADK 会重新执行同一个工具。T4-03B 交付可重复的有限技术证据，实际产品恢复仍需新的授权设计和 S11 验收。

## 2. Background and Current State

| ID | Evidence | Consequence |
| --- | --- | --- |
| EVD-001 | service pom.xml 属性 google-adk.version 为 1.13.0。缓存 JAR SHA-256 36f13af457b1657e1ef67cf5361f4eb59009a2b911bc5b0de61019f3cbea4e32 | 试验锁定实际二进制而非签名推断 |
| EVD-002 | AR AdkAgentRunExecutor#buildRoot 使用 AgentFlowFactory#compileRoot 构造 CONFIG 的 BaseAgent。#execute 选择 AgentFlowInMemoryRunner 或 AgentFlowSessionRunner，调用无 invocation ID 的 Content 重载 | 生产 CONFIG 路径没有试验的显式 App 恢复配置或入口 |
| EVD-003 | AR AdkSessionEventCodec 与 PostgresAdkSessionService 管理既有事件，T4-03A AgentLedgerService#inspectRecovery 读取当前 owner 的处置证据 | 保存事件与允许恢复是独立判断 |
| EVD-004 | 2026-11-05 仓库外 JDK 25.0.3 探针运行实际 ADK Runner、LlmAgent、InMemorySessionService 并观察三组因果场景 | 可用现有 JUnit 模块持久化有限证据 |
| EVD-005 | AR AdkReadOnlyDecisionLoopTest 已在无外部服务时运行真实 ADK 调用，AR POM 有既有测试依赖 | 沿用相同模块及包，无需新依赖 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable
Reason: 现有 AR 技术依赖的 JUnit 证据，无新生成业务模块或示例链修改。

## 3. Goals and Non-goals

固定一次已保存结果续跑和一次已执行未保存结果的重复调度，另区分显式恢复、普通新文本和 function-response。交付范围为一个测试及三份文档。生产 Runner、checkpoint、恢复命令、表与配置的授权范围均独立。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Exact delta or preserved invariant | Required Spec treatment | Chapters |
| --- | --- | --- | --- | --- | --- |
| AR in-process test | Affected | AdkReadOnlyDecisionLoopTest and pinned Runner | 新增三组有限测试 | 精确目录、事件和调用次数 | §7, §8, §14, §15 |
| Production execution | Context-only | AdkAgentRunExecutor#buildRoot | Existing fresh call and own runtime wiring | §9 owner boundary | §9 |
| Persistence | Unchanged | PostgresAdkSessionService and V13 Tool journal | 现有数据格式 | §11 preservation | §11 |
| UI | Not applicable | 本任务无用户页面 | 无 UI 入口 | §12 applicability | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Acceptance |
| --- | --- | --- |
| REQ-001 | 用同一已保存结果、原用户消息和 invocation ID 续跑 | 新 Runner 只调用一次 Fake model，工具累计一次，追加终止标记，旧事件前缀与原用户消息数量不变 |
| REQ-002 | 保留实际的重复工具调用窗口 | afterTool 因果屏障证明工具先执行一次而无结果事件，同 ID 恢复后工具累计两次 |
| REQ-003 | 区分完成、未知、关停、普通文本和 function-response | 完成 ID 空结果且无追加。未知/禁用拒绝。普通文本得新 ID。挂起时 null 不调用模型。错误回答拒绝，匹配回答追加新的用户数据并保持 ID |
| REQ-004 | 固定纯本地范围并报告验收限制 | 三项 JUnit 有界且没有应用/PG/模型提供方。区分内存证据、生产 CONFIG 和 S11 |

### 4.2 Use-case analysis

| ID | Actor | Evidence |
| --- | --- | --- |
| ACTOR-001 | Tianji AR 维护者 | Master T4-03 原生恢复验证责任 |

| ID | Use case/goal | Actor | Trigger/preconditions | Main outcome | Failure/alternative | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 核对固定 ADK 能力与停止条件 | ACTOR-001 | 固定依赖和全新的内存 fixture | 看到保存结果、重复窗口和匹配回答的实际断言 | 结果、事件或次数有差异时测试失败 | 证据不会开启实际恢复 | REQ-001, REQ-002, REQ-003, REQ-004 |

## 5. Constraints, Assumptions, and Decisions

DEC-001 选择真实 ADK 单根 LlmAgent 和原生 Tool。只替换模型及工具副作用为确定性本地行为，保持原生 Session append 路径。DEC-002 已有上游行为以真实 JUnit 固化，测试只接受实际调用次数。该窗口不成为自动恢复条件。仓库外探针采用 SSE、最大三次模型调用、禁用自动建 Session 和隐式纯文本续跑。

## 6. Project Technology Context

AR 既有 Agent Runtime 技术实现与 owner 边界沿用 T4-03A，不构造新的生产层或业务模块。复用 JDK 25、现有 ADK、RxJava 与 JUnit。Tool 使用 ADK FunctionTool/LongRunningFunctionTool。Session 使用真实 InMemorySessionService。

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | Existing AR JUnit names | AdkNativeResumeSemanticsTest and immutable NativeOutcome test record | §8 Java test | Selected class | PASS |
| Rule 2 | No | Existing native public ADK signatures | Test-only local fixtures and typed IDs | §7 | No new layer contract | N/A |
| Rule 3 | Yes | JDK records and AR JUnit | Immutable stream outcome record and fixture classes | §8 Java test | Source and compilation | PASS |
| Rule 4 | No | No Spring context in AR test | No business bean, injection or logging | §7 | Source scope | N/A |
| Rule 5 | No | Existing JDK, ADK, RxJava and JUnit | No standalone utility layer | §7 | Dependency diff | N/A |
| Rule 6 | No | Native events supplied by Runner | No new JSON transport or enum storage | §7 | Scope inspection | N/A |
| Rule 7 | No | Test-local App configuration | No environment profile edits | §16 | Exact path audit | N/A |
| Rule 9 | No | Fixed native scenarios | No production business logic | §13 | Scope audit | N/A |
| Rule 10 | No | Rx and JUnit finite execution ceilings | No domain timestamp or conversion | §6.3 | Bounded test time | N/A |
| Rule 11 | Yes | Existing AR technical test package | Preserve production owner/CQE/DB boundary | §8 Java test | Exact path and baseline diff | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable
Reason: JUnit and Rx watchdog limits bound local fixture execution only. No business timestamp, storage/wire mapping, schedule or time-zone conversion changes.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Direct baseline | Necessity verdict |
| --- | --- | --- | --- | --- |
| AR JUnit class | Create | REQ-001, REQ-002, REQ-003, REQ-004 | Existing test runner and native ADK | Required to preserve repeatable assertions |
| Production checkpoint/runner | None | REQ-004 | Current owner and executor | Independent design gate |

| Path | Network calls | Client states | Service states | Added work |
| --- | --- | --- | --- | --- |
| Existing production path | No change | No change | No change | None |
| Finite JUnit | 0 | None | In-process test Session | Three cases |

### 7.1 System Architecture Design

In-process JUnit → Runner with a BaseAgent LlmAgent root → deterministic BaseLlm and native Tool → InMemorySessionService. The resumed runner/root is recreated with the same in-memory service and exact stored invocation ID.

### 7.2 High-Level Design

Use App with resumable true and plaintext auto-resume false, exact Runner.runAsync(user, session, invocationId, null, config, Map.of()) for native no-input continuation, ordinary Content overload for fresh text and matching response. Confirm persisted event kind and counters before entering continuation.

### 7.3 Detailed Design

Case one stops after Runner emits the native response, verifies stored call/response and absent root end marker, closes the Runner and resumes on a new root. Include terminal, unknown, disabled and fresh controls. Case two stops from native afterTool callback after read returns and before native response append. Assert one tool invocation and a saved call without response, then two invocations after same-ID continuation. Case three uses genuine empty-map LongRunningFunctionTool, verifies saved wait, rejects mismatched answer, checks null no-op and verifies matching response is stored as user data before continuing. The fixtures add no raw Session events.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Create | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeResumeSemanticsTest.java |
| Create | docs/egon/spec/2026-11-05-18-48-adk-native-resume-semantics.md |
| Create | docs/egon/plan/2026-11-05-18-49-adk-native-resume-evidence.md |
| Create | docs/egon/evidence/2026-11-05-adk-native-resume-semantics.md |

## 9. Interface Definitions

Scope disposition is Context-only. Existing Runner overloads are exercised as pinned upstream APIs. Existing Tianji Agent and Process service APIs and authorization keep their current owner boundary.

## 10. POJO and Data Model Design

Only test-local fake model, read tool, fixture and immutable NativeOutcome. Event instances and invocation IDs originate from ADK. No production DTO, state codec or database shape is introduced.

## 11. Database Design

Existing schema and Session persistence are unchanged. The finite fixture uses the upstream in-memory SessionService. Actual PG event/projection atomicity remains a separate gate.

## 12. Frontend Page Design

Not applicable because the owner is a finite AR evidence test.

## 13. Design Patterns and Architecture Principles

Direct fixture composition suffices for three cases. Shared setup preserves the actual upstream runner and session boundaries, with no reusable production abstraction.

## 14. Test Design

| Test | Assertions | Requirements |
| --- | --- | --- |
| TEST-001 | resumes_exact_invocation_from_saved_read_result_without_tool_replay, including completed/unknown/disabled/fresh controls | REQ-001, REQ-003 |
| TEST-002 | reexecutes_the_tool_when_the_native_call_exists_without_a_saved_result, real counts one then two | REQ-002 |
| TEST-003 | pending_long_running_call_requires_a_matching_function_response, wrong/no-input/matching branches | REQ-003 |
| TEST-004 | exact report, four paths and no production diff | REQ-004 |

## 15. Non-functional and Cross-cutting Design

Max three model calls per Runner invocation. Rx wait at most three seconds, JUnit test at most twelve seconds. Fixtures have no provider, database, listener, network client or telemetry exporter. Use static per-case IDs, assert affinity and counts, and read current Session history without direct event writes.

## 16. Compatibility, Migration, Rollout, and Rollback

Delivery is a test and evidence commit. No production feature flag or rollout is involved. Before commit drop only the four authorized candidates if scope changes. Actual recovery implementation requires new review for owner/attempt/epoch/definition/engine/session revision, receipts, lease and authorization, and S11 process-restart evidence.

## 17. Alternatives and Decisions

A signature-only or raw-event synthetic test cannot show that the native flow actually executes the tool twice. Retain the real finite Runner fixture. A production resume path is a separate decision beyond this evidence.

## 18. Risks and Open Questions

The positive result covers one LlmAgent window and retained in-memory history. Pending or ambiguous effects require fail-closed disposition. Unverified axes include real PG/projection/receipts, actual CONFIG compositions, process replacement, external provider effects, IAM and S11. The earlier Graph probe governs its own tested shape.

## 19. Traceability Matrix

| Requirement | Use case | Evidence |
| --- | --- | --- |
| REQ-001 | UC-001 | TEST-001 |
| REQ-002 | UC-001 | TEST-002 |
| REQ-003 | UC-001 | TEST-001, TEST-003 |
| REQ-004 | UC-001 | TEST-004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §2/7/8 | Existing AR scope | None |
| MC-REUSE-001 | Applicable | PASS | §6/7 | Real existing ADK and JUnit | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §6.2/8 | Named technical test and result | None |
| MC-VALID-001 | Not applicable | N/A | §9 | No new contract validation | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable test outcome and native events | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No mapping layer | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No logging change | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | No Spring context | None |
| MC-UTIL-001 | Applicable | PASS | §6 | Existing finite native tools | None |
| MC-JSON-001 | Not applicable | N/A | §10 | No codec/JSON changes | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | Watchdogs only | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | Fixture configuration only | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct test composition | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four exact paths, runtime independent | None |
| MC-TEST-001 | Applicable | PASS | §14/15 | Exact native cases and bounded limits | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/18 | Candidate evidence scope closed | Source GO before execution |

**PASS — Ready for user review**
