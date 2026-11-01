# 原生固定 readonly MCP 工具接入

| Field | Value |
| --- | --- |
| Document | `2026-11-01-19-44-native-readonly-mcp-tools.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 19:44 Asia/Shanghai` |
| Updated | `2026-11-01 19:44 Asia/Shanghai` |
| Owner | 原用户授权按蓝图继续未完成工作并分步提交；Root 负责本派生设计、实现和验证 |
| Repository | `tianji-service` |
| Scope | Agent Runtime ADK；5 Java + README/Plan/Evidence 共 8 路径；programmatic native MCP union，无装配激活 |
| Source Requirement | 原用户继续蓝图剩余 MCP/T3-08..T4 的授权；蓝图 T3-07 / §11.3、§12、§13、§17 |
| Baseline Revision | `main@1ec81466592a8c08d5df330404b394493977fabc` clean |
| Implements Spec | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本 Plan 是 Root 基于当前源码的 Review 设计，不冒充用户另行接受 API |
| Spec Revision | 2026-11-01 SHA-256 `ae6e41f9ae5129703080bd39deb346b82a80ce9467d55edc2a5e787e892f4710` |
| Effective Specs | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [可信 MCP transport](2026-11-01-18-23-verified-readonly-mcp-transport.md)；[MCP Journal Recorder](2026-11-01-19-11-readonly-mcp-journal-recorder.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本 Plan 将已提交的固定 readonly MCP transport 与 typed Journal Recorder 接入现有 `AdkReadOnlyDecisionToolBridge.RunScope`，再由 `AdkGovernedProcessToolBridge` 将 Decision、MCP、IDW START 与审批 START 合并到同一个 `AdkGovernedToolBridge.Scope`。不新增 MCP bridge、Toolset、Executor、Plugin、loop、quota 或运行配置。精确 MCP resource 走 MCP；所有其他 READ_ONLY resource 保持原 Decision 路由。MCP 在 Recorder BEGIN 后、实际 `operation.call(exactDeadline)` 前重跑 native/session fence 和 closed check；已准入 IO 可能在 close 后完成，其 Journal 事实保留但 model/event late publication 被抑制。

一个 Step、八个路径、一个提交。证据为三组有效 RED、三类 focused GREEN、九类 canonical 回归、17 MC/10 Rules、路径/隐私/兼容审阅；不包含应用、DB、IAM、真实 MCP 网络或配置验收。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md)
- Status: legacy blueprint；原用户只授权继续并分步提交，本 Plan 的具体 API/路由是 Root 源码派生 Review 决策。
- Revision: SHA-256 `ae6e41f9ae5129703080bd39deb346b82a80ce9467d55edc2a5e787e892f4710`。
- Approval evidence: 原始 continuation authority；任何实现 GO 仍需 Root 全文读取、strict/hash 和当前任务确认。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) | Legacy / above SHA | §11.3、§12、§13、§17 | 单一 native tool loop、逐动作授权、BEGIN-before-call、quota/fence/privacy/unknown 规则 |
| Dependency | [Step 29](2026-11-01-18-23-verified-readonly-mcp-transport.md) | committed `24dfd9c` | verified SERVICE one-shot | 提供 exact MCP transport/deadline/known-fact ordering |
| Dependency | [Step 30](2026-11-01-19-11-readonly-mcp-journal-recorder.md) | committed `1ec8146` | typed MCP Recorder | 提供 PDP/BEGIN/journal/outcome/accounting |

### 2.3 Superseded or excluded content

不修改/完成 AS configuration、Bean、权限、Executor、Plugin、T3-08..T4、DDL、依赖、数据库、真实网络或部署。既有非 MCP READ_ONLY resource 的 Decision 路由不被新严格 key 规则收窄。

## 3. Effective Requirements and Acceptance

`PLAN-REQ-*` 仅追踪 legacy blueprint 现有要求。

| Requirement | Source | Effective statement | Observable acceptance | Impact |
| --- | --- | --- | --- | --- |
| `PLAN-REQ-001` | §12 | MCP 与现 Decision/START 共用一个 native Scope/Plugin/loop/quota | actual Runner 可声明/执行 Decision→MCP→START；只解析一次 Scope | 两 bridge + 三 tests |
| `PLAN-REQ-002` | §12/§13 | exact MCP resource 走 typed MCP；其他 RO 保持 Decision | MCP missing fail closed；`MCP_KEY + ".other"` 仍走 Decision | package bind overload/derived predicate |
| `PLAN-REQ-003` | §13/§17 | BEGIN 后、MCP delegate 前重核 native/session/closed；原停止错误优先 | fence failure→Recorder NOT_SENT fact；presentation 保原 safe ledger code | MCP-only guarded adapter |
| `PLAN-REQ-004` | §13/§17 | late/closed 不泄 model/event/private body；已提交事实不撤销 | PENDING/UNKNOWN无输出；TOOL_ERROR仅 status/code；late无 FINISHED | MCP-only result/error ordering |
| `PLAN-REQ-005` | §17 | 旧构造/DMN ordering/START/AA/Plugin API 不变，单提交可验证 | old4/8/10 compatible；new9/11 exact；九类全回归 | 8 paths + evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先写三个 test matrix；随后仅加 compile signatures：new package `bindResolved` 8-arity、Process public MCP 9/11 constructors、`usesMcpTransport`，允许 UOE/未路由以得到行为 RED，不提交 stub。Root 合并两个 bridge tests 为一轮 RED、Loop 单独 RED。有效 RED 后，Readonly owner 实现 package union/MCP guard，Process owner 实现 constructors/field/delegation；两者可在不重叠文件上并行。三类 focused GREEN 后冻结全部 Java，由 Root 独占 Maven 跑 final 9。

### 4.2 Test-first strategy

| Behavior | RED reason | Minimum GREEN |
| --- | --- | --- |
| bind/resource routing | new overload/signatures 无行为或 UOE | exact MCP→MCP；all other RO→Decision |
| MCP predelegate guard | guarded adapter 缺失 | forward null；BEGIN后 fence/closed；exact bound |
| native loop | MCP未进入 shared Scope | actual Runner mixed sequence/quota/session/late tests |

编译、fixture、constructor ambiguity 不是有效 RED；旧 tests 在 RED 期间必须保持绿色。

### 4.3 Sequential and parallel boundaries

| Unit | Depends on | Parallel | No overlap | Reason |
| --- | --- | --- | --- | --- |
| three test matrices | baseline | each other | production behavior | lock contract first |
| Readonly source | bridge RED | Process source | Process/Test | owns route/guard |
| Process source | bridge RED | Readonly source | Readonly/Test | owns public union ctor/delegation |
| final docs/gate | all GREEN | none | Java frozen | truthful evidence |

### 4.4 Commit boundaries

One path-limited commit: `feat(agent): mount fixed read-only MCP tools natively`。No signature-only/UOE commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Element | Necessity | Evidence/direct alternative | Decision |
| --- | --- | --- | --- |
| new MCP bridge/Toolset | not required | existing declaration/RunScope is transport-neutral | exclude |
| package bind overload | required | Process needs optional typed transport without public readonly API | implement |
| route enum/Target field | not required | immutable descriptor policy derives exact route | exclude；single `isMcp` predicate |
| Process new constructors | required for later exact assembly without changing old calls | unique 9/11 arities | implement |
| Plugin/Executor/quota | already owns admission | existing `beforeToolCallback` supplies fence/UUID/count | unchanged |
| common DMN event cleanup | unrelated | old ordering is established compatibility | MCP-only late/error delta |

No fetch-then-forward identity API、cache、new agent、table、retry or remote undo。

### 4.6 Change-unit Dependency Matrix

| Unit | Requirements | RED | Prerequisite | Produces | Step |
| --- | --- | --- | --- | --- | --- |
| readonly MCP route | 001/002 | two bridge tests | Step30 Recorder | mixed RunScope | 1 |
| guarded MCP call | 003/004 | Readonly + Loop | real callFence | NOT_SENT + held error | 1 |
| Process union/compat | 001/005 | Process + Loop | package overload | old8/10,new9/11 | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | existing Agent `infrastructure.adk`; one governed scope | preserve exact Agent profile | no new layer/loop/bridge | Step 1；MC-ARCH-001/MC-SCOPE-001 |
| Reuse/capability | declaration, Scope, Plugin, Recorder, transports | reuse current components | no dependency or duplicate orchestration | Step 1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | descriptors/bindings/records already typed | exact route/fullref/hash/null/fence guards | no new POJO/converter；semantic helpers only | Step 1；MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001 |
| Bean/logging/util/JSON/time/config | programmatic non-Beans; privacy guard and `Instant` existing | no Bean/log/config change；exact deadline pass-through | existing safe envelope/JDK types only | Step 1；MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001 |
| Business variation/pattern | existing Adapter/Composite scope | package overload + MCP-only Decorator | no switch framework or second loop | Step 1；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| native declaration | `AdkGovernedToolDeclaration` | current Readonly bridge source/tests | exact closed schema fits both routes | reuse | None | Step 1；MC-REUSE-001 |
| admission/fence/quota | `AdkRunBudgetPlugin` | current `beforeToolCallback` context/session/counter/UUID | complete；no gap | unchanged | None | Step 1；MC-REUSE-001/MC-PATTERN-001 |
| journal/IO | Step30 Recorder + Step29 transport | committed typed BEGIN/deadline/privacy contracts | complete；native route absent | add package routing/Decorator only | no dependency | Step 1；MC-DEP-001 |
| union/effect | Process RunScope | Decision/START/AA composite and effect aggregation | readonly MCP fits existing subset | extend subset only | no new bridge/loop | Step 1；MC-ARCH-001/MC-SCOPE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | 蓝图 §12/§13 | behavior classes/records existing | tests→two bridges | semantic methods；no carrier | source inventory/final9 | Step 1 | PASS |
| Rule 2 | 蓝图 §12/§13/§17 | native→Scope→Recorder handoffs | three tests→two sources | typed/fullref/fence/null guards | positive/negative tests | Step 1 | PASS |
| Rule 3 | 蓝图 §12/§13 | no new POJO/converter | all five Java | reuse records/direct mapping | compile/source review | Step 1 | PASS |
| Rule 4 | 蓝图 §12/§13 | no new Spring Bean/injection/log class | two sources | preserve constructors/manual wiring | final9/source review | Step 1 | PASS |
| Rule 5 | 蓝图 §13/§17 | JDK/existing types only | two sources | no utility/dependency | import/POM review | Step 1 | PASS |
| Rule 6 | 蓝图 §13 | safe Map envelope existing | three tests→sources/README | no raw MCP data | privacy tests | Step 1 | PASS |
| Rule 7 | 蓝图 §13 | no config path | exact eight paths | no profile edits | path diff | Step 1 | PASS |
| Rule 9 | 蓝图 §12/§13 | existing Composite + MCP Decorator | tests→two sources | no duplicated loop/switch framework | actual Runner tests | Step 1 | PASS |
| Rule 10 | 蓝图 §13/§17 | exact `Instant` forwarded | Readonly test→source | no recalculation/Date | TEST-001/003 | Step 1 | PASS |
| Rule 11 | 蓝图 §11.3/§12 | exact Agent ADK profile | every path | no hybrid/layer/generator | file tree/final9 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-001` | MCP `callDeadlineAt` | Occurred instant | `Instant` | UTC instant | Internal | Not applicable: authority expiry | Not applicable: no UI | no calendar/DST；fence then closed then delegate | pass Step30 exact µ value unchanged；no Clock/math | Decision/START deadlines unchanged | Step 1；`TEST-001`,`TEST-003`；§8 |

## 5. Change File Tree

| Op | Path | Current → final responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java` | Decision-only tests → mixed route/guard/privacy RED/GREEN | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java` | Process union tests → ctor/affinity/mixed binding | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java` | actual Runner START/Decision → mixed MCP/quota/session/late | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java` | Decision RunScope → package optional MCP route/guard | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java` | old8/10 union → optional MCP new9/11 | 1 |
| MODIFY | `README.md` | MCP Recorder future native → programmatic native library boundary | 1 |
| CREATE | `docs/egon/plan/2026-11-01-19-44-native-readonly-mcp-tools.md` | strict V5 contract | 1 |
| CREATE | `docs/egon/evidence/2026-11-01-native-readonly-mcp-tools.md` | actual evidence/limits | 1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository baseline

`/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`，`main@1ec81466592a8c08d5df330404b394493977fabc` clean。Root docs blueprint is outside target Git。Preserve unrelated changes/COLA renames；stage exact eight only；23 SQL immutable。

### 6.2 Build/test prerequisites

JDK25 `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`；`./scripts/verify-source-reactor.sh`；both settings `/tmp/tianji-compat-fix/settings.xml`；ADK content spans false；OTel exporters none。Root alone runs Maven after all Java freezes。

### 6.3 Immutable decisions

- No new public readonly bridge API；old public4 and package execute4 remain。
- exact MCP key alone routes MCP；every other RO, including near-key, remains Decision。
- old Process8/10 remain；new MCP9/11 require nonnull；borrowed MCP never closed。
- MCP-only heldFence/late ordering；old DMN event/failure ordering unchanged。
- predelegate fence is admission, not a long lock；already admitted IO may finish after close，fact persists，model/event suppressed；no remote undo/retry promise。

Exact additive/internal signatures:

```java
// AdkReadOnlyDecisionToolBridge; existing public4 and package bind7 remain.
static RunScope bindResolved(
    ToolInvocationRecorder recorder,
    ReadOnlyDecisionToolTransport decisionTransport,
    ReadOnlyMcpToolTransport mcpTransport,
    AgentLedgerService.ExecutionMaterial material,
    AgentRunLease lease,
    ToolEventSink sink,
    List<ToolDescriptor> subset,
    List<ToolExecutionGrant.BindingVO> bindings);

private static boolean isMcp(ToolDescriptor descriptor);
private ReadOnlyMcpToolTransport guardedMcpTransport(
    Runnable callFence,
    AtomicReference<AgentLedgerException> heldFence);
private Map<String, Object> executeResolved(
    BaseTool tool, Map<String, Object> arguments, UUID hostInvocationId,
    String author, Runnable callFence);

// AdkGovernedProcessToolBridge; MCP appears immediately after Decision.
public AdkGovernedProcessToolBridge(
    AgentLedgerService ledger, ToolInvocationRecorder recorder,
    ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
    FixedBpmnStartToolTransport startTransport, ToolResultAccessRecorder resultAccessRecorder,
    Collection<ToolDescriptor> descriptors,
    Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
    StartActionContextResolver actionContexts);

public AdkGovernedProcessToolBridge(
    AgentLedgerService ledger, ToolInvocationRecorder recorder,
    ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
    FixedBpmnStartToolTransport startTransport, ToolResultAccessRecorder resultAccessRecorder,
    Collection<ToolDescriptor> descriptors,
    Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
    StartActionContextResolver actionContexts, FixedToolApprovalVerifier approvalVerifier,
    ApprovalReceiptResolver approvalReceipts);

private AdkGovernedProcessToolBridge(
    AgentLedgerService ledger, ToolInvocationRecorder recorder,
    ReadOnlyDecisionToolTransport decisionTransport, ReadOnlyMcpToolTransport mcpTransport,
    FixedBpmnStartToolTransport startTransport, ToolResultAccessRecorder resultAccessRecorder,
    Collection<ToolDescriptor> descriptors,
    Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>> hostBindings,
    StartActionContextResolver actionContexts, FixedToolApprovalVerifier approvalVerifier,
    ApprovalReceiptResolver approvalReceipts, boolean approved);

public boolean usesMcpTransport(ReadOnlyMcpToolTransport candidate);
```

Old Process public8 delegates `mcp=null/approval=false`; old public10 `mcp=null/approval=true`; new9/new11 call `requireMcp` before the private12. `usesMcpTransport(null)` is false。The guarded adapter forwards an underlying null operation as null; otherwise it runs `callFence`, checks closed, and delegates `operation.call(callDeadlineAt)` unchanged。

The new package bind overload retains every old null/material/risk/full-reference/hash/uniqueness guard, including a required Decision transport. MCP is optional only when no exact MCP descriptor is visible. The four-argument package execute helper rejects an MCP target with existing `unavailable()` / `TIANJI_FEATURE_NOT_AVAILABLE` before STARTED or Recorder access. TargetVO remains unchanged; no route enum or derived route field is added.

The MCP-only adapter and presentation ordering are exact:

```java
// Called only by the public execute path that retained its real runtime fence.
mcpTransport.withVerifiedCall(request, (executor, operation) ->
    callback.apply(executor, operation == null ? null : bound -> {
        try { callFence.run(); }
        catch (RuntimeException failure) {
            heldFence.compareAndSet(null, safe(failure));
            throw new ReadOnlyMcpToolTransport.Failure(OPERATION_NOT_SENT);
        }
        if (closed.get()) {
            heldFence.compareAndSet(null, unavailable());
            throw new ReadOnlyMcpToolTransport.Failure(OPERATION_NOT_SENT);
        }
        return operation.call(bound); // no new deadline calculation or post-response fence
    }));

// In the MCP route only; the old Decision append/catch order remains unchanged.
try {
    result = recorder.executeReadOnlyMcp(guardedMcpTransport(callFence, heldFence), request);
    if (heldFence.get() != null) throw heldFence.get();
    validate existing run/invocation identity and checkedOutcome;
} catch (RuntimeException failure) {
    safeFailure = heldFence.get() == null ? safe(failure) : heldFence.get();
    try { appendUnavailableIfOpen(..., safeFailure); }
    catch (RuntimeException eventFailure) {
        if (heldFence.get() != null) throw heldFence.get();
        throw eventFailure;
    }
    throw safeFailure;
}
requireOpen(); append normal FINISHED; requireOpen();
use existing SETTLED/output-schema/zero-cost/no-receipt model projection;
```

Only authorized schema-validated primitive success output may reach the model; raw remote metadata, content, error body and extra structured fields are excluded. Events contain no tool input/output. A held safe fence/closed error wins Recorder, transport and event failure. The guard is bounded admission, not an atomic physical-send/close guarantee; no scope lock is held across delegate IO, and a late admitted fact remains durable while publication is suppressed.

### 6.4 Plan Clarifications

| ID | Root source-derived Review decision | Evidence | Semantic boundary |
| --- | --- | --- | --- |
| CLAR-001 | `isMcp(descriptor)` exact equality；default Decision | current bind accepts all RO | preserves old resources |
| CLAR-002 | MCP-only Decorator forwards null and exact bound | Step29/30 contracts | no fake SAM/DMN |
| CLAR-003 | new Process9/11 + private12 | current public8/10/private11 | additive/no ambiguity |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 将固定 readonly MCP 接入现有 native governed Scope

- Requirements: `PLAN-REQ-001, PLAN-REQ-002, PLAN-REQ-003, PLAN-REQ-004, PLAN-REQ-005`
- Dependencies: committed Step29/30；existing Scope/Plugin/Runner。
- Baseline state: MCP transport+Recorder programmatic only；native RO all use Decision。
- Observable outcome: one actual governed Scope routes exact MCP through typed Recorder while preserving Decision/START/AA and shared quota/fence。
- End state: mixed native programmatic library works；no configuration/Bean/permission/runtime activation。
- Test-first gate: Required — signatures-only/UOE then two bridge combined RED + Loop RED；compiler/fixture failure is invalid RED。
- Manual Checks: `MC-ARCH-001,MC-REUSE-001,MC-DEP-001,MC-NAME-001,MC-VALID-001,MC-MODEL-001,MC-CONVERT-001,MC-LOG-001,MC-BEAN-001,MC-UTIL-001,MC-JSON-001,MC-TIME-001,MC-CONFIG-001,MC-PATTERN-001,MC-SCOPE-001,MC-TEST-001,MC-BLOCKER-001`
- Literal Rules: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java`
- Purpose: define mixed readonly routing, real fence, null operation, held-error, late-close and privacy behavior before implementation。
- Symbols: old/new `bindResolved`, package/public execute paths, MCP guarded transport and event assertions。
- Repository evidence: existing exact declaration, identity-map, event, Recorder and result-envelope fixtures in this class。
- Dependencies and consumers: Step29 MCP port/transport and Step30 Recorder；observes File4 behavior。
- Why now: first valid RED for the internal route/guard contract。
- Contract/signature changes: old7/new8 package bind；no new public bridge API。
- Input/output and state mapping: exact MCP→`executeReadOnlyMcp`; every other RO→`executeReadOnly`; same request/event/result envelope。
- Error and edge behavior: missing MCP/fullref/hash/extra fail；near-key Decision；package4 MCP pre-STARTED fail；null forwarded；held beats append；late no event/output。
- Standards impact: `MC-VALID-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 5,Rule 6,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```java
bind mixed descriptors; assert identity declarations
mocked MCP Recorder invokes guardedTransport.withVerifiedCall and operation.call(bound)
flip fence/close; assert delegate count, fact, safe code, events and privacy
```
- Verification contribution: `TEST-001` bridge RED/GREEN。
- After this file: after signature stubs it fails for absent routing/guard, not compile/fixture。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java`
- Purpose: lock constructor compatibility, MCP affinity and mixed Process binding。
- Symbols: old8/10, new9/11, private12 delegation, `usesMcpTransport`, Process `bindScope`。
- Repository evidence: existing constructor/pointer/START/AA/full-binding/databaseNow fixtures。
- Dependencies and consumers: new File4 bind overload；observes File5 public union behavior。
- Why now: fixes additive API and compatibility before production edits。
- Contract/signature changes: MCP parameter immediately after Decision；old arities unchanged；new configured-null invalid。
- Input/output and state mapping: one visible ordered descriptor/binding set becomes one shared readonly+START Scope。
- Error and edge behavior: old ctor+visible MCP fails；invisible MCP unused；near-key Decision；all-read no databaseNow；START/AA unchanged。
- Standards impact: `MC-ARCH-001/MC-VALID-001/MC-BEAN-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 4,Rule 5,Rule 9,Rule 11`。
- Implementation pseudocode:
```java
instantiate old8, old10, new9 and new11; assert affinity and null false
bind Decision + MCP + START/AA ordered references
assert readonly captures remain separate from START preparations
```
- Verification contribution: `TEST-001` bridge RED/GREEN。
- After this file: valid RED for missing constructor/delegation behavior。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`
- Purpose: prove actual Runner mixed roundtrip, shared quota, parallel UUIDs, session/revision seal and privacy。
- Symbols: Rig MCP descriptor/transport/read captures and new mixed/parallel/fence/late tests。
- Repository evidence: current real Runner/FakeChatModel/shared Plugin/cancellation/parallel fixtures。
- Dependencies and consumers: both bridge implementations；unchanged Executor/Plugin APIs。
- Why now: proves one existing loop/Scope rather than a mocked declaration-only path。
- Contract/signature changes: none outside test Rig；one Scope resolution and actual callback roundtrip are observable。
- Input/output and state mapping: scripted Decision→MCP→START shares quota; read captures are separate from START preparations。
- Error and edge behavior: mock Recorder invokes transport callback/operation；second fence prevents delegate；settled `FAILED_KNOWN/DOWNSTREAM_TOOL_ERROR` returns the existing minimal native tool response and permits normal same-Runner continuation（one tool call then the next model stream），not a new run failure/stop policy；PENDING、UNKNOWN or invalid accounting stops before another model call；late close has no FINISHED/model publication。
- Standards impact: `MC-REUSE-001/MC-VALID-001/MC-JSON-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 2,Rule 5,Rule 6,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```java
run one governed Scope with scripted Decision, MCP and START calls
run parallel leaves; assert one resolution, callback roundtrip, shared quota and distinct UUIDs
assert fence counts, revision seal, TOOL_ERROR continuation and safe envelopes
```
- Verification contribution: `TEST-002` Loop RED/GREEN。
- After this file: fails only because MCP is not native-routed。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java`
- Purpose: implement package optional MCP route and MCP-only predelegate/error/late guard。
- Symbols: bind8 overload, RunScope MCP field, `isMcp`, `guardedMcpTransport`, MCP-only append/error helpers。
- Repository evidence: current transport-neutral declaration/request/result path and established DMN ordering。
- Dependencies and consumers: Step29 transport, Step30 Recorder, File5 Process bind and tests Files1/3。
- Why now: minimum GREEN after bridge/Loop RED。
- Contract/signature changes: keep public4/bind7/package execute4；add bind8 `(recorder,decision,mcp,material,lease,sink,subset,bindings)`；private RunScope ctor adds MCP。
- Input/output and state mapping: TargetVO unchanged；exact `isMcp` key routes MCP；all other RO Decision；existing fullref/hash/material validation retained。
- Error and edge behavior: MCP null bind fail；package4 MCP before STARTED fail；public5 forwards real fence；null stays null；fence/closed→held+MCP NOT_SENT；borrowed never close。
- Standards impact: all explicit `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: all `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```java
initial fence; create request; append STARTED
if exact MCP: executeReadOnlyMcp(guarded transport); held check; validate
for MCP requireOpen before FINISHED; held error wins appendUnavailableIfOpen failure
otherwise executeReadOnly with the existing Decision append/catch ordering
```
- Verification contribution: `TEST-001/002` and final compatibility selector。
- After this file: Files1/3 route/guard GREEN；old DMN ordering unchanged。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java`
- Purpose: add optional MCP dependency to the existing Process union without changing old APIs。
- Symbols: MCP field, new9/11 constructors, private12, `usesMcpTransport`, `bindScope` delegation。
- Repository evidence: current public8/10/private11, pointer helpers and one readonly subset。
- Dependencies and consumers: File4 bind8；future configuration is explicitly out of scope。
- Why now: minimum Process union GREEN after API RED。
- Contract/signature changes: old8→null/noapproval；old10→null/approval；new9→`requireMcp`/noapproval；new11→`requireMcp`/approval；private12 adds MCP after Decision。
- Input/output and state mapping: optional field passes into bind8；`usesMcp(candidate)=candidate!=null&&field==candidate`。
- Error and edge behavior: new null invalid；old absent valid until visible exact MCP then bind fail；no second route switch；START/database/effects unchanged。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-NAME-001/MC-VALID-001/MC-BEAN-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 4,Rule 5,Rule 9,Rule 11`。
- Implementation pseudocode:
```java
store optional MCP field; preserve old8 and old10 delegation
require nonnull MCP only for new9 and new11
pass MCP field to bindResolved8; return the existing Process RunScope
```
- Verification contribution: `TEST-001/002` and old START/AA final tests。
- After this file: Files2/3 GREEN；no configuration consumer exists。

#### File 6 — `MODIFY README.md`
- Purpose: document programmatic native union and remaining assembly boundary。
- Symbols: MCP native route/fallback/fence/late/privacy/evidence paragraphs。
- Repository evidence: current README ends MCP at Journal future-native boundary。
- Dependencies and consumers: actual GREEN source/evidence；developers/operators only。
- Why now: write only after behavior exists。
- Contract/signature changes: document old/new constructors and package-only readonly extension without presenting it as public product API。
- Input/output and state mapping: no runtime state；accurate safe envelope/fact ownership wording。
- Error and edge behavior: no activation/permission/runtime/undo/retry claim；borrowed transport ownership and admitted-IO race explicit。
- Standards impact: `MC-SCOPE-001/MC-TEST-001` — documentation remains inside the exact implementation boundary。
- Literal rule enforcement: `Rule 6,Rule 7,Rule 10,Rule 11` — source-accurate privacy/config/time/architecture wording。
- Implementation pseudocode:
```markdown
Replace the statement that native MCP remains future work with the exact programmatic library boundary.
Describe exact-MCP versus fallback-Decision routing, old/new Process constructors and the shared Scope/Plugin quota.
Describe the initial and predelegate fences, admitted-I/O close race, Journal fact ownership and safe result envelopes.
Link the Step evidence; state that configuration, permissions, IAM, network and deployed runtime remain unverified.
```
- Verification contribution: README/source/evidence manual review。
- After this file: documentation matches library without activation claim。

#### File 7 — `CREATE docs/egon/plan/2026-11-01-19-44-native-readonly-mcp-tools.md`
- Purpose: save this strict V5 implementation contract。
- Symbols: requirements/clarifications/eight paths/gates/17 MC/10 Rules。
- Repository evidence: predecessor Plans and exact baseline。
- Dependencies and consumers: Root full read/strict/hash before GO。
- Why now: implementation cannot start from informal design。
- Contract/signature changes: documentation only。
- Input/output and state mapping: accepted baseline/design → ordered execution/evidence。
- Error and edge behavior: strict failure/drift blocks implementation；no user-acceptance fabrication。
- Standards impact: all `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: all `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```text
Copy this draft to docs/egon/plan/2026-11-01-19-44-native-readonly-mcp-tools.md.
Full-read the Plan and effective blueprint; verify baseline, constructor signatures, eight paths and exclusions.
Run validate_plan.py on the destination with --strict; repair every diagnostic without changing accepted semantics.
Record the Plan SHA-256 and permit implementation GO only after Root Review confirmation.
```
- Verification contribution: strict validator and recorded SHA。
- After this file: executable Review Plan。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-native-readonly-mcp-tools.md`
- Purpose: record actual RED/GREEN/final/static/commit and unverified boundaries。
- Symbols: baseline/Plan SHA/test logs/counts/privacy/path/commit/residual risks。
- Repository evidence: existing Evidence format and predecessor truthful boundaries。
- Dependencies and consumers: only actual gate outputs；Root/user review。
- Why now: final acceptance requires reproducible evidence。
- Contract/signature changes: documentation only。
- Input/output and state mapping: commands/diffs/logs → exact evidence, no prefilled PASS counts。
- Error and edge behavior: compiler/fixture failures separate；no live claim/private logs。
- Standards impact: `MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```markdown
Record baseline commit, clean status, blueprint SHA and the strict-valid Plan SHA before RED.
Record bridge RED, Loop RED, focused GREEN and final-nine commands, exits, test counts and log paths exactly.
Record source review for routing, null forwarding, held-error precedence, privacy, borrowed ownership and old compatibility.
Record exact path/SQL/POM/config checks and the focused commit result.
List database, IAM, MCP network, configuration and deployed runtime as unverified; never prefill live PASS claims.
```
- Verification contribution: requirement/MC closure with actual artifacts。
- After this file: truthful acceptance artifact。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: the exact bridge RED, Loop RED, focused GREEN and final9 commands in §8。
- Expected result: valid behavior REDs；three focused tests and final9 GREEN；no unexpected failure/error/skip；static/privacy/path checks pass。
- Failure returns to: File1/4 for route/guard；File2/5 for constructor/union；File3 for actual Runner/quota/session/late behavior；Spec/user only for material drift。
- Completion criteria: exact8；strict PASS；no stub/extra path/private output；old DMN/START/AA behavior and all five requirements evidenced。
- Rollback: eight-path source revert；no data/config migration。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java`, `README.md`, `docs/egon/plan/2026-11-01-19-44-native-readonly-mcp-tools.md`, `docs/egon/evidence/2026-11-01-native-readonly-mcp-tools.md`。
- Commit: `feat(agent): mount fixed read-only MCP tools natively`

## 8. Test, Validation, and Quality Gates

| Test ID | Scope |
| --- | --- |
| `TEST-001` | combined two bridge RED/GREEN：API/routing/fence/error/privacy |
| `TEST-002` | actual Process Loop RED/GREEN：mixed/quota/parallel/session/late |
| `TEST-003` | final9 + static/path/privacy/strict |

Exact commands from the Tianji repository root:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkGovernedProcessToolBridgeTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-native-mcp-bridges-red.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=AdkGovernedProcessLoopTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-native-mcp-loop-red.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-native-mcp-focused-green.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,AdkReadOnlyDecisionLoopTest,AdkToolTelemetryPrivacyTest,ToolInvocationRecorderTest,ReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolBindingsTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-native-readonly-mcp-tools-final.log 2>&1
```

| Gate/order | Working directory | Command/method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED | Tianji repo | first exact command | two bridge tests | behavior failures only；old assertions green | Files1/2/signatures | 001/002/003；module |
| TEST-002 RED | Tianji repo | second exact command | actual Loop | MCP not routed；no fixture/compiler | File3 | 001/003/004；module |
| focused GREEN | Tianji repo | third exact command | changed tests | exit0 | Files4/5 | 001..005；module |
| TEST-003 final | Tianji repo | fourth exact command after Java freeze | exact9 | exit0；counts recorded | owning file | all；source reactor |
| static | Tianji repo | `rg`/source review | five Java | no new bridge/Toolset/Executor/Plugin/quota/retry/logger/config/POM/SQL/Bean；null not wrapped；borrowed not closed | source | all；static |
| path/diff | Tianji repo | `git status --short` and `git diff --check` | exact8/23 SQL | exact paths；no whitespace/SQL change | stop | 005；static |
| Plan | Tianji repo | `python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-01-19-44-native-readonly-mcp-tools.md --strict` | V5 | PASS + SHA | File7 | 005；docs |
| live | user-controlled | not authorized | runtime | not run | later authority | residual；live |

Exact static review commands:

```bash
rg -n "McpToolset|ReadOnlyMcpToolTransport|usesMcpTransport|bindResolved|executeReadOnlyMcp|operation == null|operation.call" stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java
git status --short
git diff --check
git diff --name-only 1ec81466592a8c08d5df330404b394493977fabc -- 'stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/*.sql' pom.xml '*/pom.xml'
```

Check the complete immutable SQL set independently against the committed baseline (all 23 Agent/Process/Worker files, with no startup or migration):

```bash
python3 - <<'PY'
from pathlib import Path
import hashlib, subprocess
baseline = '1ec81466592a8c08d5df330404b394493977fabc'
paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', baseline], text=True).splitlines()
paths = [p for p in paths if '/src/main/resources/db/' in p and p.endswith('.sql')]
before = {p: hashlib.sha256(subprocess.check_output(['git', 'show', baseline + ':' + p])).hexdigest() for p in paths}
after = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('.').glob('**/src/main/resources/db/**/*.sql')}
assert len(before) == 23 and before == after
print('PASS: all 23 SQL files match the baseline byte-for-byte')
PY
```

No `scripts/work` harness；existing source reactor/tests/static tools suffice。Root runs Maven exclusively after Java freeze。

## 9. Migration, Compatibility, Rollout, and Rollback

N/A migration/DDL/dependency/config/permission。Old readonly public4、bind7、package execute4；Process public8/10 and all pointers stay。New Process9/11 are programmatic only；no Spring consumer/activation。Rollback eight paths；Journal facts remain valid generic readonly facts，不删除/重写。Later AS assembly is separate Plan。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step/files | Tests/evidence |
| --- | --- | --- |
| `PLAN-REQ-001` | 1；all 5 Java | TEST-001/002 |
| `PLAN-REQ-002` | 1；Readonly+Process/tests | TEST-001/final |
| `PLAN-REQ-003` | 1；Readonly/tests/Loop | TEST-001/002 |
| `PLAN-REQ-004` | 1；Readonly/Loop/README | privacy/late/PENDING tests |
| `PLAN-REQ-005` | 1；all8 | final9/static/strict/commit |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Evidence/action | Status |
| --- | --- | --- | --- |
| R1 | close after final check cannot cancel admitted IO | persist fact，suppress late event/model；no undo claim | residual/nonblocking |
| R2 | strict key could break custom RO | exact MCP only；default Decision test | closed |
| R3 | held error lost to event failure | MCP-only precedence tests | closed |
| R4 | second loop/quota | actual Runner one Scope/shared Plugin tests | closed |
| B1 | source drift before GO | recheck baseline/signatures；revise if changed | closed at baseline |

No open Step31 design blocker。Specific API remains derivative Review, not user-accepted Spec。

## 12. Review and Acceptance

### 12.1–12.4 Fidelity/executability

Plan preserves original continuation scope, exact old non-MCP behavior, eight paths, test-first, one commit and no runtime expansion。Normal: Decision→MCP→START one Scope。Failure: second fence produces durable NOT_SENT plus original safe presentation。Edge: close during admitted IO preserves fact and suppresses late model/event。Live DB/IAM/network/config remain unverified。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | existing ADK profile/5 Java | exact profile preserved | None |
| MC-REUSE-001 | Applicable | PASS | Scope/Plugin/Recorder/transports | reused without duplicate loop | None |
| MC-DEP-001 | Applicable | PASS | no POM/dependency path | no introduction | None |
| MC-NAME-001 | Applicable | PASS | type/method inventory | no new carrier；semantic helpers | None |
| MC-VALID-001 | Applicable | PASS | fullref/resource/null/fence/closed tests | all affected handoffs guarded | None |
| MC-MODEL-001 | Not applicable | N/A | no POJO/enum/model path | model unchanged | None |
| MC-CONVERT-001 | Not applicable | N/A | no conversion boundary | converter unnecessary | None |
| MC-LOG-001 | Not applicable | N/A | no logging path | logging unchanged | None |
| MC-BEAN-001 | Not applicable | N/A | no Bean/DI/config path | wiring not activated | None |
| MC-UTIL-001 | Applicable | PASS | JDK/existing imports only | allowlist satisfied | None |
| MC-JSON-001 | Applicable | PASS | safe envelope/privacy tests | raw MCP data excluded | None |
| MC-TIME-001 | Applicable | PASS | TIME-001/TEST-001/003 | exact Instant pass-through | retain live boundary |
| MC-CONFIG-001 | Not applicable | N/A | no key/profile path | parity unaffected | None |
| MC-PATTERN-001 | Applicable | PASS | existing Composite + MCP Decorator | no duplicate orchestration | None |
| MC-SCOPE-001 | Applicable | PASS | exact8/exclusions | minimum bounded change | None |
| MC-TEST-001 | Applicable | PASS | TEST-001..003 exact commands | normal/failure/edge covered | execute/record actual |
| MC-BLOCKER-001 | Applicable | PASS | §11/all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

Plan PASS is planning completeness only。Root must copy、full-read、strict validate、hash and confirm GO before RED；no code/runtime claim。

## Java / CQE contract review

No PO/enum/converter/DDL/uniqueness/Event change。Existing annotation-free typed internal boundaries remain；readonly business call continues writing authorized Journal through Step30。Exact Agent architecture preserved；no generator/MP action。

## Backend generation and dependency evidence

Generator reference reviewed；Agent profile/no DDL/CRUD/dependency makes generator、manifest、SQL journal N/A。Any POM/SQL/config/generated path blocks commit。
