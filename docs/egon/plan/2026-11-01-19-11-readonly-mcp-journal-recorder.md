# 固定 readonly MCP 调用的类型化 Journal Recorder

| Field | Value |
| --- | --- |
| Document | `2026-11-01-19-11-readonly-mcp-journal-recorder.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 19:11 Asia/Shanghai` |
| Updated | `2026-11-01 19:11 Asia/Shanghai` |
| Owner | 原用户连续执行授权；Root 负责实现、验证和提交 |
| Repository | `tianji-service` |
| Scope | Agent Runtime persistence；8 个唯一路径；类型化 MCP Recorder、MCP 专属微秒期限与既有 Journal 复用 |
| Source Requirement | 用户要求按原蓝图继续未完成工作并分步提交；本 Step 细化 T3-07 / §11.3、§12、§13、§17 的剩余 MCP journal 工作 |
| Baseline Revision | `main@24dfd9cf9e66405f223186b99baf1f9a979e19c5` clean |
| Implements Spec | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户明确授权按剩余目标继续执行，非形式化 Accepted Spec |
| Spec Revision | 2026-11-01 SHA-256 `c2f6f6317f9aade1ab74242edaceada865192e7d53e39d5e1c9c5cf5a7b4a0a9` |
| Effective Specs | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定 readonly MCP 合同](2026-11-01-17-49-fixed-readonly-mcp-contracts.md)；[可信固定 readonly MCP 传输](2026-11-01-18-23-verified-readonly-mcp-transport.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本 Plan 在已提交的固定 readonly MCP port/绑定/实际 SERVICE transport 之上增加一个类型化 `ToolInvocationRecorder.executeReadOnlyMcp` 入口。它复用现有 readonly Journal 的 preparation、当前能力、DISPATCH PDP、BEGIN nonce、replay、事实完成与零成本结算，不复制持久编排，不伪造 `DecisionObservation`。唯一的复杂变化是：MCP 的 required `OneShotCall.call(Instant)` 必须接收在 PDP、BEGIN、SQL fence 和实际调用之间完全相同的向下取整微秒期限；该精度规则仅适用于 `READ_ONLY + ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY`，DMN 与所有写路径保持现状。

本 Plan 只有一个实现 Step、八个唯一路径和一个语义提交。完成证据为两个真实 RED 矩阵、最小 GREEN、指定十一类回归、23 个历史 SQL 字节不变、路径/静态审阅以及完整 17 项 Manual Check。它不包含 DDL、依赖、配置、Bean、native/ADK 挂载、权限登记/授予、应用启动或真实 PostgreSQL/IAM/MCP 网络验收。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md)
- Status: legacy blueprint；用户授权继续原蓝图的未完成工作。本 Step 的八路径、签名、期限和失败投影是 Root 根据现有源码与已提交合同收敛的实现细节，未另获用户逐项审查。
- Revision: 2026-11-01；全文 SHA-256 `c2f6f6317f9aade1ab74242edaceada865192e7d53e39d5e1c9c5cf5a7b4a0a9`。
- Approval evidence: 原始用户指令为“按照这份plan 继续未完成的工作。完成后，分步提交，然后我会审查是否正确实现这份plan。”该指令授权继续实现原蓝图；Root 的内部设计接受与子代理消息不构成用户对本细化文档的另一次批准。文档保持 Review，不伪造 Accepted Spec。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) | Legacy；SHA-256 `c2f6f6317f9aade1ab74242edaceada865192e7d53e39d5e1c9c5cf5a7b4a0a9` | §11.3、§12、§13、§17；T3-07 剩余 MCP journal | 要求工具逐动作授权、BEGIN 先于外部调用、已知/未知事实不被取消覆盖、失败不自动重派、日志/权限/预算闭合 |
| Normative predecessor | [固定 readonly MCP 合同](2026-11-01-17-49-fixed-readonly-mcp-contracts.md) | 已提交 `81e72d8` | `ReadOnlyMcpToolTransport` Request/Callback/OneShotCall/CallObservation/Failure | 锁定 typed MCP port、required `call(Instant)`、SUCCESS/TOOL_ERROR/UNKNOWN 与 failure kinds |
| Normative predecessor | [可信固定 readonly MCP 传输](2026-11-01-18-23-verified-readonly-mcp-transport.md) | 已提交 `24dfd9c` | verified SERVICE callback、exact microsecond call deadline、known-fact-before-postcheck | Recorder 必须在调用 one-shot 前提交 BEGIN，并将同一微秒期限传入 transport；不能依赖外层取消证明未发送 |

### 2.3 Superseded or excluded content

本 Step 不替代蓝图其他阶段，也不宣称完成 T3-07 native consumer 或全部平台目标。明确排除：native/ADK bridge 与工具挂载、AS configuration/Bean、权限目录注册/激活/角色授予、T3-08 CMMN、后续 T3/T4、真实 PostgreSQL/IAM/MCP 网络验收、POM/依赖、配置、DDL/Flyway/MP-SDJ、Outbox/Event、前端与浏览器。Root 工作区蓝图位于 `tianji-service` Git 仓库之外，只作为有效 Spec 读取，不进入本提交。

## 3. Effective Requirements and Acceptance

以下 `PLAN-REQ-*` 是对 legacy blueprint 现有要求的追踪别名，不增加新业务要求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `PLAN-REQ-001` | 蓝图 §11.3、§12 | 固定 readonly MCP 调用必须进入现有工具授权/Journaling 路径，BEGIN 在外部调用前提交，不能用 DMN 结果伪装 MCP 事实 | 新增且仅新增 `executeReadOnlyMcp(ReadOnlyMcpToolTransport, PreparationRequestVO)`；成功、工具错误和 unknown 直接形成 `ToolExecutionOutcome` | `ToolInvocationRecorder` + focused tests；Store/SQL 表与 API 不新增 |
| `PLAN-REQ-002` | 蓝图 §12、§13 | 当前能力、DISPATCH PDP、持久 BEGIN、SQL recheck 与实际 MCP call 必须共享同一有界期限；PostgreSQL/JDBC 精度不能产生宽窗 | MCP 的四项最小期限向下截断到微秒，并在 PDP request、Store、SQL、attempt 和 `OneShotCall.call` 精确相等；floor 后非 future 拒绝 | `ToolInvocationStore.dispatchCallDeadline` 为唯一公式；Recorder/Store/SQL 三处复用 |
| `PLAN-REQ-003` | 蓝图 §13、§17 | 调用后的取消/期限不能冒充“未发送”；只有传输显式证明 NOT_SENT 才可写 known-unsent；工具自身错误是已知失败而非授权拒绝 | `OPERATION_NOT_SENT -> FAILED_KNOWN/DISPATCH_NOT_SENT`；consume 后 CANCELLED/DEADLINE/unknown -> UNKNOWN；`TOOL_ERROR -> FAILED_KNOWN/DOWNSTREAM_TOOL_ERROR`；DENIED 单独 REJECTED | MCP typed mapper/failure projector；事实完成沿原 nonce transaction |
| `PLAN-REQ-004` | 蓝图 §12、§13 | 复用既有 journal 编排且保持 DMN/START/approval/result-access 行为与 SQL 历史 | private `Function<Instant, ToolExecutionOutcome>` 仅隔离 transport 变化；现 `executeReadOnly` 签名和 DMN mapping 不变；23 SQL/POM/config/native 不改 | Recorder core 小改；Store helper只对 exact MCP resource floor；指定旧回归 |
| `PLAN-REQ-005` | 蓝图 §17 | 变更必须测试先行、隐私安全、可审计、单提交且不把离线验证冒充运行验收 | Recorder/Store 两个有效 RED；十一类 canonical 回归；17 MC/10 literal rules；一个 path-limited commit | tests、README、Plan、Evidence；无 runtime claim |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

1. 先在 `ToolInvocationRecorderTest` 写 typed MCP 正常/失败/事实顺序矩阵；为使新 public entry 可编译，只允许在 `ToolInvocationRecorder` 暂时增加精确签名且方法体 `throw new UnsupportedOperationException()`。该 UOE 只是 RED compile prerequisite，不是可提交中间版本，也不得包含任何行为。
2. 独立在 `ToolInvocationStoreTest` 写 MCP 微秒 floor、unfloored rejection、replay、floor-to-expired、DMN/write unchanged 与 cleanup-code RED。该矩阵调用既有 Store 行为，不以编译错误作为 RED。
3. 两个 RED 均被 Root 记录且确认不是 fixture/compiler failure 后，Store/SQL owner 可实现唯一 `dispatchCallDeadline` 与三处重核；Recorder/Test owner 可实现 typed entry、private deadline-aware outcome strategy 和 MCP projection。两组写范围不重叠，可并行；Root 负责最终集成审阅。
4. 最后更新 README、Plan、Evidence，冻结全部 Java，Root 独占运行 canonical selector、静态范围/SQL checksum 检查，再一次提交八路径。

该顺序不创建新持久模型或协议层。Store 是 dispatch deadline 公式所有者；SQL 只调用该公式而不保留第二份计算；Recorder 只负责 typed orchestration 与结果投影。

### 4.2 Test-first strategy

| Behavior | RED file/reason | Minimum GREEN | Refactor/wiring allowance |
| --- | --- | --- | --- |
| typed MCP entry | `ToolInvocationRecorderTest`；compile-only UOE 被调用，所有新行为测试以 UOE 失败 | 实现 `executeReadOnlyMcp`，复用原 preparation/dispatch/replay/account | 只提取 private `Function<Instant, ToolExecutionOutcome>` seam，不增加 public generic API |
| MCP result/failure semantics | 同一 Recorder test；尚无 direct CallObservation/Failure projector | direct MCP observation/failure mapping，不创建 DecisionObservation | 允许命名 private static mapper；不得改变旧 DMN mapper |
| exact microsecond deadline | `ToolInvocationStoreTest`；现 Store/SQL 使用 unfloored min，MCP nano fixture 未精确 floor | Store 唯一 helper；Recorder/PDP/BEGIN/SQL/replay/call 复用 | 只抽取一个 package-private static helper，不新增 type/file |
| compatibility | 旧指定测试在 GREEN selector 中 | DMN、IDW、AA、result-access、codec 全保持 | 不借机重构旧 flows |

有效 RED 必须是新断言因缺失行为失败。compile-only UOE 用于消除“方法不存在”这一编译失败；UOE 本身触发 Recorder 新行为测试失败才计为 RED。Mockito fixture、JDK/Maven/settings、类选择或编译错误不计 RED。

### 4.3 Sequential and parallel boundaries

| Work unit | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Recorder test/UOE RED | baseline `24dfd9c` | Store test RED | Store/SQL production ownership | 新 entry 需 compile prerequisite，但不需要 deadline 实现即可证明 UOE RED |
| Store test RED | baseline `24dfd9c` | Recorder test/UOE RED | Recorder production ownership | 现 Store 方法可直接复现 unfloored behavior |
| Recorder minimum GREEN | 两个 RED 已记录；Store helper signature 锁定 | Store/SQL minimum GREEN | Store/SQL/Test files | 调用共享 helper，负责 typed transport 与 outcome |
| Store/SQL minimum GREEN | 两个 RED 已记录；helper signature 锁定 | Recorder minimum GREEN | Recorder/Test files | 负责 deadline formula、validation 和 SQL fence |
| Docs/final gate | 两组 GREEN 合并 | None | 所有 Java ownership 必须冻结 | Evidence 只能记录实际命令；Root 独占 Maven/commit |

所有执行者都不是代码库中的唯一参与者；不得回退用户现有 COLA rename 或并行修改。Root 只按声明路径集成。

### 4.4 Commit boundaries

Step 1 产生一个语义提交：`feat(agent): journal fixed read-only MCP invocations`。八个唯一路径全部属于同一不可分割行为：typed entry 没有 microsecond Store/SQL fence 不安全；deadline helper 没有 typed caller 没有当前产品效果。不得提交 UOE stub、仅测试、仅 docs 或半 GREEN；不得混入任何其他路径。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| typed MCP Recorder entry | 必要；§11.3/§12 | `ToolInvocationRecorder.executeReadOnly` 只接受 DMN port/DecisionObservation；Step 29 MCP port 是不同 SAM/Failure | 不能强转或构造 fake DecisionObservation；新增一个 typed overload 是最窄边界 | 一个 public method，无新模型/Bean | Implement |
| public generic recorder abstraction | Spec 未要求 | 只有 DMN 与 MCP 两种强类型 transport，外层 request/failure 不同 | 保留两 typed entries，共享 private core | 避免新 API/类型和错误类型擦除 | Do not implement |
| private deadline-aware outcome strategy | 必要；减少持久编排重复 | preparation/PDP/BEGIN/replay/complete/account 已经 transport-neutral | `Function<Instant, ToolExecutionOutcome>` 精确承载唯一变化 | 一个 private 参数与两个 typed lambdas | Implement；复杂 flow 的局部 Strategy |
| deadline helper | 必要；§13/§17 与 Step 29 exact µ SAM | Recorder/Store/SQL 三处现各自或隐式计算 unfloored min | Store-owned package-private static helper | 三个调用点，无新文件/状态 | Implement |
| DDL/schema/version | 不必要 | V13 已支持 generic READ_ONLY zero-cost/outcomes，V14不属于 source call | 复用现表/check/codec | 零 migration/rollout state | Exclude |
| new DTO/converter/mapper | 不必要 | `CallObservation`、`ToolExecutionOutcome`、`RecordResult` 已足够 | direct typed projection | 不增加 class/external schema | Exclude |
| Bean/config/native consumer | 后续 scope | 当前 Step 29 是 programmatic library；蓝图仍要求后续 native | 本 Step 只交 Recorder library | 避免 activation/permission/runtime failure points | Exclude |
| fetch-then-forward/security context API | 不必要 | actor/run/lease/capability/PDP 都由现 request/current stores 派生 | 复用可信 context；无 caller-supplied executor/deadline except verified callback | 无新增交互 | Exclude |

未发现需要返回 Spec 的材料缺陷；Root 已根据现有合同收敛局部实现决定。Plan 不增加 cache、agent、retrieval、network call、table 或配置。

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| MCP typed Recorder contract | PLAN-REQ-001/003 | RecorderTest + UOE valid RED | existing ReadOnlyMcpToolTransport/RecordResult | additive `executeReadOnlyMcp` | future native MCP bridge | Step 1 |
| MCP deadline identity | PLAN-REQ-002/004 | StoreTest nano fixtures RED | existing Store/SQL dispatch APIs | `dispatchCallDeadline` exact formula | PDP/BEGIN/replay/SQL/call | Step 1 |
| MCP fact projection | PLAN-REQ-001/003 | Recorder status/failure matrix RED | typed entry + common journal core | direct ToolExecutionOutcome facts | V13 journal/replay/account | Step 1 |
| compatibility/evidence | PLAN-REQ-004/005 | final eleven-class selector/static checks | both GREEN streams | reviewable eight-path commit evidence | user/next native Step | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Agent runtime existing `application.tool` -> `infrastructure.persistence`; `ToolInvocationRecorder/Store/Sql` already own journal orchestration | 保持 exact Agent archetype-derived runtime/server technical profile；不引入第三结构 | 仅改 `infrastructure.persistence` 现文件与 tests；README/docs 位于既有位置 | Step 1；MC-ARCH-001 |
| Reuse/capability | existing Recorder/Store/SQL/V13, `ReadOnlyMcpToolTransport`, `ToolExecutionOutcome`, `TransactionTemplate`, Clock/nanoTime | 复用现 journal/PDP/capability/codec/transaction；无 dependency | no new persistence class, auth layer, converter, utility, executor | Step 1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | `PreparationRequestVO`, `RecordResult`, existing guarded records and `validated(descriptor)` | 新 public behavior method 使用语义名称；无新 carrier/PO/Converter | direct typed mapping；null/risk/resource/cost/action/time guards at boundary | Step 1；MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001 |
| Bean/logging/util/JSON/time/config | three production types are existing non-Spring-manual infrastructure classes; codec/Jackson and config unchanged | 无新 Bean/DI/log/JSON/config；use JDK `Instant/ChronoUnit` and injected Clock | no Lombok/Qualifier/profile edits；fixed safe exceptions retained | Step 1；MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001 |
| Business variation/pattern | complex journal flow centralized in `dispatchAndRecord`; transport result mapping is sole variation | private Strategy via `Function<Instant, ToolExecutionOutcome>`；no public hierarchy | DMN lambda ignores deadline and preserves mapping；MCP lambda consumes exact deadline | Step 1；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| preparation/PDP/BEGIN/replay/fact/account | existing `ToolInvocationRecorder`, `ToolInvocationStore`, `ToolInvocationSql`, V13 | `dispatchAndRecord`, `beginReadOnlyDispatch`, `readRecorded`, `completeReadOnly` | 完全适合；仅 transport observation/SAM 不同 | reuse existing core | None；一个 private Function seam | Step 1；MC-REUSE-001/MC-PATTERN-001 |
| typed MCP values | `ReadOnlyMcpToolTransport` | committed Step 28/29 port and tests | exact Request/Failure/CallObservation available | reuse | None | Step 1；MC-DEP-001 |
| deadline/precision | JDK `Instant`, `ChronoUnit.MICROS`, Store current windows | START 已有 micro truncation；generic RO 未统一 | one Store-owned calculation | reuse JDK; no time lib | None | Step 1；MC-TIME-001 |
| transactions | existing Spring `TransactionTemplate`/DataSource transaction ownership | Store already rejects ambient transactions and runs fact transactions | fully fits | reuse; no new transaction manager | None | Step 1；MC-REUSE-001 |
| object conversion | direct existing records + `ToolExecutionOutcome.validated` | no layer/model mismatch requiring mapper | Converter would duplicate types | direct typed construction; no converter | None | Step 1；MC-CONVERT-001 |
| code generation/DDL | writing-plan generator supports native Light/Web/Service CREATE CRUD only; Agent profile excluded | no DDL/schema/catalog artifact in scope | generator inapplicable | no generator/journal entry | None | Step 1；MC-ARCH-001/MC-DEP-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | 蓝图 §11.3/§12/§13 | 仅新增行为方法与 private Function；既有 `*VO`, `RecordResult`, `ToolExecutionOutcome` | tests -> Store -> SQL -> Recorder | `executeReadOnlyMcp` semantic behavior name；不新增 Data/Info/Param/Bean carrier | source inventory + compile/tests | Step 1 | PASS |
| Rule 2 | 蓝图 §12/§13/§17 | internal boundary uses record constructors, `readOnly`, `validateDispatch`, PDP request equality, SQL callBounds | RecorderTest/StoreTest -> Store/SQL/Recorder | external typed port -> recorder -> Store -> SQL 每边界核 risk/resource/identity/deadline/outcome；无 Spring proxy handoff | positive/negative/failure tests | Step 1 | PASS |
| Rule 3 | 蓝图 §12/§13 | 无新 POJO/entity/converter；existing immutable records and outcome | same eight paths | 不复制对象、不 BeanUtils/JSON map；direct typed immutable values | compile + source review | Step 1 | PASS |
| Rule 4 | 蓝图 §12/§13 | 三类均非 Spring-managed Bean，构造/依赖不变；不新增业务类/日志 | Store/SQL/Recorder only | 不加 stereotype/DI；无新 dependency fields；existing fixed safe error style | source/wiring regression | Step 1 | PASS |
| Rule 5 | 蓝图 §13/§17 | only JDK Function/Instant/ChronoUnit + existing Spring/runtime types | production three paths | 不加 utils/dependency；不建 duplicate deadline utility class | POM unchanged/import review | Step 1 | PASS |
| Rule 6 | 蓝图 §13 | existing `ToolExecutionJsonCodec` owns persisted JSON；no wire DTO change | final selector includes CodecTest | 不手工 JSON mapping；outcome through existing codec/validated | CodecTest + source diff | Step 1 | PASS |
| Rule 7 | 蓝图 §13 | no configuration key/profile file | none outside eight paths | 不新增/修改 YAML/properties | path scope/static diff | Step 1 | PASS |
| Rule 9 | 蓝图 §12/§13 | complex journal flow and two transport variations | Recorder tests -> Recorder private strategy | private `Function<Instant, ToolExecutionOutcome>` Strategy; stable orchestration remains single | behavior matrix + code review | Step 1 | PASS |
| Rule 10 | 蓝图 §13/§17 | Clock/Instant/Duration/nanoTime existing; DB timestamps microsecond | StoreTest -> Store/SQL/Recorder | exact conditional floor, same deadline through PDP/BEGIN/SQL/call；no Date/Calendar | precision tests + forbidden import search | Step 1 | PASS |
| Rule 11 | 蓝图 §11.3/§12 | existing Agent runtime `infrastructure.persistence`; Agent generator excluded | all eight paths stay existing profile | no new layer/module/DDL/Event；journal writes remain existing infrastructure | file tree/module tests | Every Step | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-001` | `dispatchCallDeadline`, PDP `Request.deadlineAt`, `DispatchWindowVO.callDeadlineAt`, `DispatchAttemptVO.callDeadlineAt`, `OneShotCall.call(Instant)` | Occurred instant | `Instant` | UTC instant | Internal | Not applicable: technical authority deadline, no business calendar | Not applicable: no UI | min(dispatchNotAfter, run, evidence, executor); exact MCP only floor, never round up/extend; floor-to-now rejects | DB/JDBC microseconds；`Clock` wall + existing `nanoTime`; truncate before PDP | existing PostgreSQL timestamp contract unchanged；DMN/read/write non-MCP retain nano semantics；no historical data rewrite | Step 1; `TEST-001`, `TEST-002`, `TEST-003`; exact commands in §8 |
| `TIME-002` | `window()/check()` elapsed transaction-budget calculation | Duration | `Duration` / `long` elapsed nanos | Not persisted | Internal | Not applicable: elapsed technical budget | Not applicable: no UI | existing 10-second transaction window and monotonic checks remain; no window reset | `Duration.between` and injected monotonic `LongSupplier`; original instant/nano pair retained | existing cancellation/deadline behavior retained except typed MCP post-consumption projection | Step 1; `TEST-001`, `TEST-002`, `TEST-003`; exact commands in §8 |

No time zone, calendar, DST, date or frontend display semantics are introduced. `MC-TIME-001` is satisfied only by exact source/test evidence; static tests do not prove a live PostgreSQL driver round trip.

## 5. Change File Tree

```text
MODIFY stellar-enclosure-tianji-agent-runtime/
├── src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/
│   ├── ToolInvocationRecorderTest.java
│   └── ToolInvocationStoreTest.java
└── src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/
    ├── ToolInvocationStore.java
    ├── ToolInvocationSql.java
    └── ToolInvocationRecorder.java
MODIFY README.md
CREATE docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md
CREATE docs/egon/evidence/2026-11-01-readonly-mcp-journal-recorder.md
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java` | existing DMN recorder/failure/replay/account fixtures | typed MCP matrix + DMN compatibility assertions | public behavior RED/GREEN | Step 1 | PLAN-REQ-001/003/004/005 | Recorder/Test owner; Root final |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java` | existing begin/read/complete/window fixtures | MCP microfloor/replay/unchanged-path matrix | Store/SQL deadline RED/GREEN | Step 1 | PLAN-REQ-002/004/005 | Store owner; Root final |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java` | `validateDispatch`, `CLEANUP_CODES`, existing transaction boundary | package-private `dispatchCallDeadline`; validation uses it; safe MCP cleanup codes | deadline formula owner | Step 1 | PLAN-REQ-002/003/004 | Store owner |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java` | `callBounds` used by begin IDW, begin RO, read replay | all three call sites derive exact bound from Store helper; no second formula | DB fence consistency | Step 1 | PLAN-REQ-002/004 | Store/SQL owner |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java` | DMN-only `executeReadOnly` and `dispatchAndRecord` | additive typed MCP entry, private deadline Function, direct MCP mappers; DMN API preserved | orchestration and projection | Step 1 | PLAN-REQ-001/002/003/004 | Recorder/Test owner |
| MODIFY | `README.md` | lines 126–134 describe MCP contract/transport and say Recorder future | journal availability/limits/remaining native boundary | accurate consumer documentation | Step 1 | PLAN-REQ-005 | Root docs owner |
| CREATE | `docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md` | absent at baseline | strict V5 execution contract | scope/order/gates | Step 1 | all | Root docs owner |
| CREATE | `docs/egon/evidence/2026-11-01-readonly-mcp-journal-recorder.md` | absent at baseline | actual RED/GREEN/static/commit boundary evidence without live claims | evidence | Step 1 | PLAN-REQ-005 | Root docs owner |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- Repository: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`, branch `main`, baseline `24dfd9cf9e66405f223186b99baf1f9a979e19c5`, clean at planning.
- Workspace root is not the target Git repository. `../../../../docs/plans/agent-framework-upgrade-plan.md` remains outside the Tianji commit.
- Preserve all unrelated changes and user COLA renames. Stage only the eight paths in §5; no broad formatting or history rewrite.
- Historical 23 SQL files across Agent Runtime, Process Server and Worker Starter `src/main/resources/db` are immutable and out of scope. Record the complete pre/post file set and SHA comparison; never edit/repair/squash them.
- Agent profile is excluded from `stellar-enclosure-codegen.sh`; no DDL/CRUD/template/generator action or classpath SQL journal entry applies.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK | `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` | JDK 25 available | compile/test only |
| Source reactor | `./scripts/verify-source-reactor.sh` | current sibling source repositories and existing script | compiles source dependency reactor; no service start |
| Maven settings | `-gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml` | existing isolated settings readable; do not edit global/user settings | offline/cache-dependent Maven resolution; no dependency change |
| ADK/OTel privacy | `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, three exporters `none` | literal values in every test command | no exporter/network telemetry |
| Test ownership | Root freezes all Java after owner GREEN and runs final command exclusively | no concurrent Java edits during final gate | avoids mixed evidence |
| Runtime | none | application/database/container/browser/network remain stopped | not validated |

### 6.3 Immutable constraints and approved decisions

- Existing `executeReadOnly(ReadOnlyDecisionToolTransport transport, PreparationRequestVO request)` public signature, DMN observation/failure mapping and non-MCP precision remain compatible.
- `ToolInvocationSql.approvedCallBounds`、approved START/IDW 的既有显式微秒 guard 与审批期限链保持不变；新 helper 只替换 generic `callBounds` 中的重复 min 计算，且其条件对非 MCP 调用是 identity。
- New public API is exactly `executeReadOnlyMcp(ReadOnlyMcpToolTransport, PreparationRequestVO)`; no public generic callback or shared transport supertype.
- New method rejects null transport/request/operation before BEGIN. The actual MCP callback must translate any shared Decision `CANCELLED`/`DEADLINE_EXCEEDED` exception inside the MCP callback before it crosses the MCP port; otherwise Step 29 would collapse a foreign runtime failure.
- Preserve the existing DMN null-operation guard explicitly in `executeReadOnly` before constructing its non-null Function or entering the shared core: `if (operation == null) throw unavailable()`. A DMN null-operation regression must show no DISPATCH PDP, BEGIN, operation or dispatch epoch, with the same sanitized failure and eligible PREPARED cleanup as the baseline. Wrapping a null operation in a lambda must never move the failure after BEGIN.
- `TOOL_ERROR` is `FAILED_KNOWN/DOWNSTREAM_TOOL_ERROR`, not REJECTED. Only explicit `OPERATION_NOT_SENT` is known unsent after one-shot consumption. Cancellation/deadline after consumption is conservatively UNKNOWN.
- `operationInvoked=true` means this recorder invoked the one-shot capability; it does not attest HTTP transmission. Explicit NOT_SENT can therefore have `operationInvoked=true` with a known-unsent outcome.
- MCP-only floor can turn a future capability expiry of less than one microsecond into now/past. Reject that result before constructing the final `DispatchWindowVO`, DISPATCH PDP or BEGIN by throwing the existing shared Decision `DEADLINE_EXCEEDED`; translate it inside the MCP callback to MCP `DEADLINE_EXCEEDED`. Do not change legacy DMN deadline guards. Add a targeted capability-expiry fixture proving no BEGIN/operation.
- Terminal fact completion remains authoritative. A later Step 29 cleanup/final-current error may suppress presentation, but existing terminal cleanup is no-op and cannot erase the fact, release budget, or authorize retry.
- No new Event/Outbox is emitted. Journal state change uses the existing command/transaction path; readonly refers to the business tool, not absence of audit writes.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-001` | `ToolInvocationStore.dispatchCallDeadline(request, dispatchNotAfter, evidenceExpiresAt, executorExpiresAt)` owns the one formula; it floors only exact READ_ONLY+MCP resource | current formula is duplicated between Recorder/Store/SQL; Step 29 requires µ exact | same min sources, only DB-safe downward precision for new MCP consumer | a second formula could drift and widen authority; tests block it |
| `PLAN-CLAR-002` | private `Function<Instant, ToolExecutionOutcome>` is the only pattern participant | current `dispatchAndRecord` is otherwise transport-neutral | no public API or lifecycle change; DMN lambda uses old mapper | duplicated orchestration could diverge transaction/failure behavior |
| `PLAN-CLAR-003` | compile-only UOE method body is allowed solely to obtain behavior-valid RED and is removed before GREEN/commit | new method does not exist at baseline; compile failure is not valid RED | no executable behavior/commit state is accepted from stub | committing stub would violate completion; final diff/evidence checks reject it |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 将固定 readonly MCP 调用接入现有授权 Journal

- Requirements: `PLAN-REQ-001`, `PLAN-REQ-002`, `PLAN-REQ-003`, `PLAN-REQ-004`, `PLAN-REQ-005`
- Dependencies: baseline `24dfd9c` clean；Step 28 port/bindings 与 Step 29 verified transport 已提交；V13 generic READ_ONLY journal/storage/codec 已存在。
- Baseline state: `ToolInvocationRecorder` 只有 DMN typed readonly entry；Store/SQL generic READ_ONLY 能记录零成本事实，但 dispatch min 未按 MCP required call 精确 floor，且没有直接 MCP result/failure projector。
- Observable outcome: 一个 MCP typed caller 可在同一当前能力/PDP/BEGIN/SQL fence 下消费 exact microsecond one-shot，并将 SUCCESS/TOOL_ERROR/UNKNOWN/Failure 写为规定事实；DMN/write/result-access 不变。
- End state: programmatic MCP Recorder library 完成；native/config/Bean/permission/runtime 仍明确缺失。
- Test-first gate: `Required` — Recorder 使用 compile-only UOE 获得有效行为 RED；Store 使用现 unfloored behavior 获得独立 RED；两个 RED 后才能写 GREEN。
- Manual Checks: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java`

- Purpose: 在 production behavior 前固定 typed MCP preparation/BEGIN/observation/failure/replay/compatibility 合同。
- Symbols: 新测试覆盖 `executeReadOnlyMcp`、exact deadline、SUCCESS、TOOL_ERROR、UNKNOWN、FailureKind matrix、operation-null、shared-check translation、terminal replay、pre-callback cleanup、DMN unchanged。
- Repository evidence: 该类已有 `executeReadOnly` 的真实 controlled Store/ledger/capability/authorization fixture、nonce/fact/account/replay 断言；同 package 可观察 `RecordResult` 和 journal collaborator interactions。
- Dependencies and consumers: 编译依赖现 `ReadOnlyMcpToolTransport`、`ToolInvocationRecorder.RecordResult`、`ToolInvocationStore.PreparationRequestVO`；生产 consumer 是 File 5。
- Why now: 它定义 typed public contract；先写断言，随后只允许 File 5 增加精确 UOE signature 以获得行为 RED。
- Contract/signature changes: 测试目标精确为 `public RecordResult executeReadOnlyMcp(ReadOnlyMcpToolTransport transport, PreparationRequestVO request)`；不要求任何新 DTO。
- Input/output and state mapping: fixed READ_ONLY MCP descriptor/resource + normalized arguments + run/lease/invocation -> PREPARED/BEGIN -> `CallObservation`/Failure -> existing `RecordResult`/`ToolExecutionOutcome`/zero accounting。
- Error and edge behavior:
  - null transport/request/operation must reject before BEGIN；invalid risk/resource/action/intent/nonzero cost reject；ambient transaction and unverified storage preserve current fixed errors。
  - original DMN transport supplies null operation: preserve baseline sanitized unavailable and PREPARED cleanup, with no DISPATCH PDP/BEGIN/epoch/operation; the new private Function must not hide this null.
  - SUCCESS persists exact locally validated output；TOOL_ERROR persists `FAILED_KNOWN`, empty output, `DOWNSTREAM_TOOL_ERROR`；UNKNOWN persists `UNKNOWN/OUTCOME_UNKNOWN`。
  - after BEGIN: only `OPERATION_NOT_SENT` -> `FAILED_KNOWN/DISPATCH_NOT_SENT`; DENIED -> REJECTED; invalid response -> UNKNOWN invalid; CANCELLED/DEADLINE/credential/profile/result-unknown -> UNKNOWN。
  - pre-callback credential/profile failure must not BEGIN；cleanup only closes eligible PREPARED row。Terminal replay never consumes one-shot。
  - force shared `check(window)` cancellation/deadline inside MCP callback and assert caller receives MCP Failure kind, never Decision Failure.
- Standards impact: `MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-TIME-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001` — uses existing typed values; proves every internal handoff/failure/precision without new model/converter.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11` — semantic method/type names, boundary guards, immutable records, existing codec, private Strategy behavior and exact `java.time` precision in current Agent persistence package。
- Implementation pseudocode:

```java
@Test void mcp_success_commits_begin_then_calls_exact_micro_deadline_and_records_zero_cost_output() {
    arrange MCP request whose evidence expiry has sub-microsecond nanos
    fake transport invokes callback with verified SERVICE executor
    OneShotCall captures callDeadline and asserts BEGIN already committed
    return CallObservation(SUCCESS, expectedOutput)
    result = recorder.executeReadOnlyMcp(transport, request)
    assert captured deadline == floorMicros(min(dispatch/run/evidence/executor))
    assert result SUCCEEDED, operationInvoked true, exact output, SETTLED/PENDING per existing account
}

@Test void mcp_known_and_unknown_results_map_without_decision_observation() {
    execute controlled TOOL_ERROR and assert FAILED_KNOWN / DOWNSTREAM_TOOL_ERROR / empty output
    execute controlled UNKNOWN and assert UNKNOWN / OUTCOME_UNKNOWN / empty output
    execute invalid observation and assert UNKNOWN / OUTCOME_INVALID_RESPONSE
}

@Test void mcp_failures_after_one_shot_are_conservative() {
    for each FailureKind invoke a fresh controlled recorder fixture
    assert OPERATION_NOT_SENT -> FAILED_KNOWN / DISPATCH_NOT_SENT
    assert OPERATION_DENIED -> REJECTED / DOWNSTREAM_PERMISSION_DENIED
    assert OPERATION_INVALID_RESPONSE -> UNKNOWN / OUTCOME_INVALID_RESPONSE
    assert CANCELLED, DEADLINE_EXCEEDED, CREDENTIAL_UNAVAILABLE,
      PROFILE_UNAVAILABLE, PROFILE_CHANGED, OPERATION_RESULT_UNKNOWN
      -> UNKNOWN / OUTCOME_UNKNOWN
}

@Test void null_operation_and_shared_deadline_failure_never_cross_as_decision_failure() {
    callback null operation -> reject before beginReadOnlyDispatch
    shared check throws Decision DEADLINE_EXCEEDED
    MCP callback wrapper translates to MCP DEADLINE_EXCEEDED
}
```

- Verification contribution: Recorder RED command in §8；GREEN/final selector proves PLAN-REQ-001/003 and DMN compatibility。
- After this file: test intent is complete; before File 5 compile prerequisite it may not compile, which is not RED evidence. After exact UOE signature only, new tests fail because UOE/missing behavior; existing tests continue to run. No stub is committed。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`

- Purpose: 固定 MCP-only deadline formula、Store validation、replay 和非 MCP compatibility。
- Symbols: tests around `beginReadOnlyDispatch`, `readRecorded`, `validateDispatch` indirect behavior and cleanup codes; observe deadlines through actual attempts/SQL fences, without calling the new helper directly before GREEN。
- Repository evidence: existing fixtures construct exact request/evidence/check/executor/window and exercise READ_ONLY/IDW/AA rows without a live DB；same package can test package-private helper。
- Dependencies and consumers: production File 3 helper and File 4 SQL consumer；Recorder File 5 also calls helper。
- Why now: independent valid RED before Store/SQL changes; prevents implementing a formula that passes Recorder only but diverges at storage fence。
- Contract/signature changes: package-private `static Instant dispatchCallDeadline(PreparationRequestVO, Instant dispatchNotAfter, Instant evidenceExpiresAt, Instant executorExpiresAt)`；no public contract。
- Input/output and state mapping: four authority ceilings -> min；only exact MCP READ_ONLY -> `truncatedTo(MICROS)`；the same instant must equal checked request/window/attempt/replay expectations。
- Error and edge behavior: unfloored or rounded-up MCP deadline rejected；floor-to-now/past rejected；DMN READ_ONLY preserves nano；IDW/AA unchanged；PROFILE cleanup codes accepted and arbitrary strings rejected。
- Standards impact: `MC-REUSE-001/MC-VALID-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001` — validates current Store transaction boundary and exact time semantics, no new dependency/model。
- Literal rule enforcement: `Rule 2, Rule 5, Rule 9, Rule 10, Rule 11` — same typed guards, JDK-only helper, direct single formula, `java.time` precision, existing persistence package。
- Implementation pseudocode:

```java
@Test void mcp_dispatch_floors_the_exact_minimum_before_begin_and_replay() {
    evidence.expiresAt = instant with 987 nanos beyond microsecond
    expected = min(dispatchNotAfter, runDeadline, evidenceExpiry, executorExpiry)
        .truncatedTo(MICROS)
    checked.request.deadlineAt = expected
    window.callDeadlineAt = expected
    assert beginReadOnlyDispatch succeeds and attempt.callDeadlineAt == expected
    assert readRecorded requires the same expected instant
}

@Test void unfloored_or_expired_after_floor_mcp_deadline_is_rejected() {
    assert exact unfloored checked request is rejected for MCP
    assert a floor result not after Clock.instant is rejected before dispatch
}

@Test void decision_readonly_and_write_deadlines_keep_existing_nanoseconds() {
    assert DMN READ_ONLY deadline retains the supplied nanoseconds
    assert IDW and approval deadline guards retain their existing values
}

@Test void profile_cleanup_codes_are_closed_and_arbitrary_codes_still_fail() {
    assert PROFILE_UNAVAILABLE and PROFILE_CHANGED close only eligible PREPARED rows
    assert an unlisted cleanup code is rejected without a state change
}
```

- Verification contribution: Store RED/GREEN command and final compatibility selector prove PLAN-REQ-002/004。
- After this file: tests compile against unchanged Store/SQL production and fail on real unfloored dispatch/profile-cleanup behavior. No direct new-helper test or production change is needed for this RED。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`

- Purpose: 成为 dispatch call deadline 的唯一 formula owner，并保持 Store/PDP/transaction validation 闭合。
- Symbols: new package-private static `dispatchCallDeadline`; `validateDispatch`; `CLEANUP_CODES`。
- Repository evidence: Store already owns `DispatchWindowVO`, validates exact reconstructed deadline against checked DISPATCH request, and owns closed cleanup-code allowlist。
- Dependencies and consumers: called by File 5 Recorder and File 4 SQL; tests File 2。No new class/module。
- Why now: tests have fixed exact result; helper must exist before SQL/Recorder consumers can compile GREEN。
- Contract/signature changes:

```java
static Instant dispatchCallDeadline(
    PreparationRequestVO request,
    Instant dispatchNotAfter,
    Instant evidenceExpiresAt,
    Instant executorExpiresAt);
```

No public API change. Add only `PROFILE_UNAVAILABLE`, `PROFILE_CHANGED` to cleanup allowlist; MCP operation failures map to existing `DISPATCH_NOT_SENT` before calling cleanup。
- Input/output and state mapping: validates non-null trusted existing inputs; computes min of dispatch/run/evidence/executor; conditional exact descriptor risk/resource floor；returns Instant only, no Clock read/state mutation。
- Error and edge behavior: caller `checkDispatch` still rejects expired/cancelled/unverified window；`validateDispatch` requires helper result equals `window.callDeadlineAt` and checked request；audit must cover it。No relaxing audit/evidence/run/tenant/lease equality。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-UTIL-001/MC-TIME-001/MC-PATTERN-001/MC-SCOPE-001` — JDK-only deterministic helper in existing owner; no utility class/Bean。
- Literal rule enforcement: `Rule 1, Rule 2, Rule 4, Rule 5, Rule 9, Rule 10, Rule 11` — behavior method naming, exact handoff validation, no DI change, JDK time only, single formula in current architecture。
- Implementation pseudocode:

```java
static Instant dispatchCallDeadline(request, dispatchNotAfter, evidenceExpiresAt, executorExpiresAt) {
    require existing typed inputs
    deadline = min(dispatchNotAfter, request.binding().run().deadlineAt(),
                   evidenceExpiresAt, executorExpiresAt)
    if (request.descriptor().policy().risk() == READ_ONLY
        && ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY.equals(
               request.descriptor().policy().executionResourceKey())) {
        deadline = deadline.truncatedTo(ChronoUnit.MICROS)
    }
    return deadline
}

validateDispatch(PreparationRequestVO request, RecorderPreparationVO expected,
                 ToolExecutionGrant.EvidenceVO evidence,
                 ToolExecutionAuthorization.CheckResult checked,
                 ReadOnlyDecisionToolTransport.VerifiedExecutor executor,
                 DispatchWindowVO window) {
    deadline = dispatchCallDeadline(request, expected.dispatchNotAfter(),
                                    evidence.expiresAt(), executor.expiresAt())
    require deadline == window.callDeadlineAt
    require checked.request == exact DISPATCH request(deadline)
    preserve all existing audit/current/window guards
}
```

- Verification contribution: StoreTest exact precision/replay/compatibility；final write/approved tests guard shared method callers。
- After this file: Store behavior is GREEN for its controlled path once File 4 consumes the same helper at SQL; Recorder new entry still UOE/not GREEN。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`

- Purpose: 使 transaction 内的 live run/current evidence SQL fence 使用与 Recorder/Store 相同的 MCP deadline，消除第二份 unfloored 公式。
- Symbols: private `callBounds` only; its three existing callers remain begin IDW、begin READ_ONLY、readRecorded。
- Repository evidence: current `callBounds` independently computes stored dispatch/run/evidence/executor minimum and compares `callDeadline`; SQL timestamps are truncated elsewhere to microseconds。
- Dependencies and consumers: calls `ToolInvocationStore.dispatchCallDeadline` in same package；no SQL text/schema change；observed through Store/Recorder tests and final selector。
- Why now: Store helper exists；SQL must adopt it before typed MCP BEGIN/replay is safe。
- Contract/signature changes: no method signature or SQL statement change；replace local min calculation only。
- Input/output and state mapping: pass request, stored `dispatchNotAfter`, current evidence expiry, executor expiry to helper；compare exact callDeadline；preserve live toolTimeout, audit, current grant and DB clock checks。
- Error and edge behavior: any mismatch remains `unavailable`; does not round DB now, weaken `isAfter(now)`, change lock order, state transition, nonce, outcome or side-effect fields。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001` — same-source transaction participant, no DDL/query change。
- Literal rule enforcement: `Rule 2, Rule 5, Rule 9, Rule 10, Rule 11` — typed boundary, JDK existing helper, no duplicate complex rule, exact time semantics, existing infrastructure layer。
- Implementation pseudocode:

```java
private void callBounds(PreparationRequestVO request, StoredInvocationRow stored,
                        ToolExecutionGrant.EvidenceVO evidence,
                        ToolExecutionAuthorization.AuditVO audit,
                        ReadOnlyDecisionToolTransport.VerifiedExecutor executor,
                        Instant callDeadline, RunSourceVO live,
                        Instant authorityDeadline) {
    bound = ToolInvocationStore.dispatchCallDeadline(
        request,
        stored.invocation().dispatchNotAfter(),
        evidence.expiresAt(),
        executor.expiresAt())
    require bound.equals(callDeadline)
    preserve callDeadline > databaseNow
    preserve audit grant/expiry and live toolTimeout checks
}
```

- Verification contribution: Store begin/replay matrix; `ToolWriteInvocationStoreTest` and `ToolApprovedInvocationStoreTest` prove shared callers unchanged；SQL checksum gate proves no resource SQL edit。
- After this file: Store/SQL deadline path is internally consistent and its tests can become GREEN；Recorder typed UOE remains the only missing product behavior。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`

- Purpose: 增加 typed MCP entry，并以一个 private deadline-aware result strategy 复用现有 journal orchestration。
- Symbols: `executeReadOnlyMcp`; changed private `dispatchAndRecord` with a final `Function<Instant, ToolExecutionOutcome>` parameter; `mcpOperationOutcome`; `mcpObservation`; `mcpOperationFailure`; MCP cleanup mapping/Decision-to-MCP callback translation；existing DMN entry explicitly keeps its null-operation pre-BEGIN guard before adapting to a lambda。
- Repository evidence: current `executeReadOnly` prepares/metadata/transport callback/cleanup；`dispatchAndRecord` already owns current capability, DISPATCH PDP, BEGIN, replay, complete, account；Step 29 callback requires `OneShotCall.call(Instant)`。
- Dependencies and consumers: File 3 helper, existing MCP/Decision ports, Store methods, outcome/ledger types；future native bridge is not in this Step。
- Why now: RED contracts and shared deadline implementation are locked；this is minimum product GREEN。
- Contract/signature changes: one additive public typed entry exact signature；existing constructors/public methods/records unchanged。Private core receives `Function<Instant, ToolExecutionOutcome>`; no public generic abstraction。
- Input/output and state mapping:
  - request guards -> existing prepare；metadata-only unchanged。
  - verified executor + current evidence -> `dispatchCallDeadline` -> exact DISPATCH authorization -> BEGIN -> invoke strategy with exact deadline。
  - typed MCP `CallObservation` -> direct `ToolExecutionOutcome`; no DecisionObservation construction。
  - existing `completeReadOnly`/account -> `RecordResult`。
- Error and edge behavior:
  - reject null operation inside MCP callback before entering shared dispatch/BEGIN；translate shared Decision failure inside MCP callback to same MCP CANCELLED/DEADLINE or NOT_SENT fallback before it crosses actual Step 29 wrapper。
  - outer/pre-callback MCP failures get fresh same-kind safe Failure and existing cleanup；ledger errors preserve code/fixed message；private runtime -> existing unavailable/safe cleanup。
  - common core marks `operationInvoked=true` immediately before applying strategy. Strategy catches typed transport failure and returns mapped fact. Only explicit NOT_SENT known unsent；post-consume cancel/deadline unknown。
  - after terminal fact, outer transport failure may mask result but cleanup cannot rewrite terminal state。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001` — central complex flow uses private Strategy; no Bean/log/config/model/JSON/dependency expansion；exact transaction/time/privacy behavior tested。
- Literal rule enforcement: all ten `Rule 1, 2, 3, 4, 5, 6, 7, 9, 10, 11` — typed semantic API, every handoff guard, no new carrier/converter/DI/config, JDK Function/time, existing architecture and codec。
- Implementation pseudocode:

```java
public RecordResult executeReadOnlyMcp(ReadOnlyMcpToolTransport transport,
                                       PreparationRequestVO request) {
    require transport/request and exact READ_ONLY MCP resource
    require no action/intent, zero estimated cost, no ambient transaction, verified storage
    prepared = preparations.prepareOrLoadForRecorder(request)
    if metadataOnly return metadata
    transportRequest = new ReadOnlyMcpToolTransport.Request(
        request.binding().run(), request.lease(), request.descriptor(),
        request.arguments(), request.invocationId(), expected.dispatchNotAfter())
    return transport.withVerifiedCall(transportRequest, (executor, operation) -> {
        if (operation == null) throw invalid() // before shared core/BEGIN
        try {
            return dispatchAndRecord(request, prepared.invocation(), prepared.budget(), executor,
                deadline -> mcpOperationOutcome(request, operation, deadline))
        } catch (ReadOnlyDecisionToolTransport.Failure shared) {
            throw new ReadOnlyMcpToolTransport.Failure(switch shared.kind() {
                CANCELLED -> MCP.CANCELLED
                DEADLINE_EXCEEDED -> MCP.DEADLINE_EXCEEDED
                default -> MCP.OPERATION_NOT_SENT
            })
        }
    })
    catch MCP Failure -> fresh same kind; cleanup with safe MCP code; rethrow
    catch ledger/private -> existing sanitized cleanup behavior
}

// Preserve the original DMN guard before wrapping its operation in a non-null lambda.
existingDmnTransport.withVerifiedCall(dmnRequest, (executor, operation) -> {
    if (operation == null) throw unavailable()
    return dispatchAndRecord(request, original, prepared.budget(), executor,
        deadline -> decisionOperationOutcome(request, operation))
})

private RecordResult dispatchAndRecord(
        PreparationRequestVO request,
        RecorderPreparationVO expected,
        AgentBudgetReservation originalBudget,
        ReadOnlyDecisionToolTransport.VerifiedExecutor executor,
        Function<Instant, ToolExecutionOutcome> invoke) {
    require executor and invoke
    preserve initial wall/mono checks
    evidence = capabilities.current(request.binding(), request.descriptor())
    deadline = ToolInvocationStore.dispatchCallDeadline(
        request, expected.dispatchNotAfter(), evidence.expiresAt(), executor.expiresAt())
    if exact READ_ONLY MCP resource and deadline is not after clock.instant():
        throw shared Decision DEADLINE_EXCEEDED // translated inside MCP callback before BEGIN
    window = existingWindow(started, nanos, deadline); check(window)
    checked = authorization.check(exact DISPATCH Request(deadline))
    preserve audit/evidence/run guards
    if replay return existing recorded result without invoke.apply
    begin = preparations.beginReadOnlyDispatch(
        request, expected, evidence, checked, executor,
        ToolInvocationStore.dispatchNonceSha256(nonce), window)
    if loser replay/metadata as today
    invoked = false
    try {
        check(window) // retain the existing post-BEGIN local no-send fence
        invoked = true
        outcome = invoke.apply(deadline) // MCP operation.call(deadline); DMN ignores deadline
    } catch (RuntimeException localFailure) {
        outcome = invoked ? UNKNOWN/OUTCOME_UNKNOWN : FAILED_KNOWN/DISPATCH_NOT_SENT
    }
    recorded = complete(request, begin.attempt(), nonce, outcome)
    return result(recorded, invoked, account(request, recorded.budget()))
}

private ToolExecutionOutcome mcpOperationOutcome(request, operation, deadline) {
    try { return mcpObservation(request, operation.call(deadline)); }
    catch (MCP Failure f) { return mcpOperationFailure(f.kind()); }
    catch (RuntimeException privateFailure) { return UNKNOWN/OUTCOME_UNKNOWN; }
}

mcpObservation:
  SUCCESS -> validated SUCCEEDED(output, zero)
  TOOL_ERROR -> FAILED_KNOWN(empty, DOWNSTREAM_TOOL_ERROR, zero)
  UNKNOWN -> UNKNOWN(empty, OUTCOME_UNKNOWN, zero)
  invalid -> UNKNOWN(empty, OUTCOME_INVALID_RESPONSE, zero)

mcpOperationFailure:
  OPERATION_NOT_SENT -> FAILED_KNOWN/DISPATCH_NOT_SENT
  OPERATION_DENIED -> REJECTED/DOWNSTREAM_PERMISSION_DENIED
  OPERATION_INVALID_RESPONSE -> UNKNOWN/OUTCOME_INVALID_RESPONSE
  all remaining -> UNKNOWN/OUTCOME_UNKNOWN
```

- Verification contribution: RecorderTest new matrix; Store/SQL tests exact deadline; all old selector classes guard compatibility, codec, fact and write flows。
- After this file: both focused suites are GREEN; no UOE body remains；typed library is complete but unmounted。

#### File 6 — `MODIFY README.md`

- Purpose: 将 lines 126–134 的“future Recorder”边界更新为已具备 programmatic typed journal，并保留 native/runtime 未完成事实。
- Symbols: readonly MCP contract/transport/journal paragraphs and evidence link。
- Repository evidence: current README already documents Step 28/29 exact boundaries and explicitly says Journal execution remains subsequent work。
- Dependencies and consumers: written only after source/tests GREEN；read by operators/developers，not runtime。
- Why now: documentation must not claim implementation before actual evidence，也不能继续说 Recorder future。
- Contract/signature changes: name `executeReadOnlyMcp`、MCP-only µ floor、outcome mappings、no native/config/DDL/runtime。
- Input/output and state mapping: no data mutation；describe existing V13 reuse and zero-cost facts。
- Error and edge behavior: clearly state cancellation/deadline after consumed call is UNKNOWN unless explicit NOT_SENT；TOOL_ERROR known failure；outer cleanup/postcheck cannot erase fact；no retry claim。
- Standards impact: `MC-SCOPE-001/MC-TEST-001`；no Java/Bean/config impact。
- Literal rule enforcement: `Rule 6, Rule 7, Rule 10, Rule 11` — protocol/state/time wording matches actual code; no config or architecture claim。
- Implementation pseudocode:

```markdown
Document additive typed Recorder and existing V13 journal reuse.
Document exact MCP-only microsecond deadline shared by PDP/BEGIN/SQL/call.
Document SUCCESS/TOOL_ERROR/UNKNOWN and failure projection.
Document no native/config/permission/DDL activation and live validation boundary.
Link actual Step 30 evidence.
```

- Verification contribution: manual README/source consistency and path-limited diff。
- After this file: user-facing technical boundary matches implemented library without implying runtime readiness。

#### File 7 — `CREATE docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md`

- Purpose: 保存本 strict V5 实施合同、八路径、RED/GREEN、17 MC/10 literal rules 和 one-commit gate。
- Symbols: Template Version 5 chapters 1–12, `PLAN-REQ-001..005`, `PLAN-CLAR-001..003`。
- Repository evidence: current Plan directory and prior Step 28/29 V5 documents；effective blueprint link relative path proven。
- Dependencies and consumers: source design accepted before GO；Root copies this draft, reads fully, strict-validates and hashes before implementation。
- Why now: implementation may not start from an informal summary；this file becomes declared commit path。
- Contract/signature changes: documentation only；must equal actual implementation decisions and commands。
- Input/output and state mapping: baseline/requirements -> ordered files/gates/evidence；no runtime state。
- Error and edge behavior: any strict validation failure, path drift, test mismatch or unresolved MC blocks GO/commit；do not weaken Plan silently。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001` — all checks are compiled into file order, pseudocode, commands and the blocking table。
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — every rule is independently represented in §4.8, the Step and per-file blocks。
- Implementation pseudocode:

```text
Root copies /tmp draft to exact repository path.
Root reads complete Plan and effective blueprint sections.
Run validate_plan.py docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md --strict.
Record plan SHA-256 before implementation GO.
Reject any source/API/path drift not covered by accepted clarification.
```

- Verification contribution: strict Plan validator plus manual checklist；not code/runtime evidence。
- After this file: implementation has a reviewable source-of-truth Plan；status remains Review until user/Root GO。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-readonly-mcp-journal-recorder.md`

- Purpose: 记录实际 baseline、Plan hash、RED/GREEN 命令/exit/counts、static checks、SQL hashes、Manual Check 和残余边界。
- Symbols: evidence status, requirement table, actual commands/logs, 17 MC, 10 Rules, diff/commit results, unverified live boundaries。
- Repository evidence: existing evidence naming/style and prior Step 28/29 separation of finite tests from live acceptance。
- Dependencies and consumers: only actual execution results; Root owns final content and commit hash update。
- Why now: evidence is written after tests/static review, never prefill pass counts。
- Contract/signature changes: documentation only。
- Input/output and state mapping: real command/log/diff -> exact evidence; no fabricated PASS。
- Error and edge behavior: fixture/compiler failures recorded separately；skips/warnings interpreted；no live DB/network claim；commit result added only after actual commit。
- Standards impact: `MC-TEST-001/MC-BLOCKER-001/MC-SCOPE-001` and every relevant row evidence。
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — every rule gets actual source/test/static evidence or a blocker；not copied aspirational prose。
- Implementation pseudocode:

```markdown
Record baseline and immutable SQL hashes.
Record compile-only UOE and both effective RED matrices separately.
Record minimum GREEN and final canonical eleven-class result exactly.
Record diff/check/Plan validator/hash and path-limited commit result.
Record no application, DB, IAM, MCP network, container, browser or native acceptance.
```

- Verification contribution: final acceptance artifact；must reconcile every §3 requirement and §12 check。
- After this file: eight-path change is evidence-complete and ready for Root’s single commit if every gate passes。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: exact RED/GREEN/final commands in §8；Root alone runs final after all Java frozen。
- Expected result: both new RED matrices fail only for named missing behavior；minimum GREEN passes focused tests；final eleven specified classes pass with no unexpected failure/error；any deliberate skip is recorded and not called live acceptance；23 SQL hashes unchanged。
- Failure returns to: File 1 for typed behavior/fixture mismatch；File 2 for deadline expectation；File 3 for Store/PDP equality；File 4 for SQL fence；File 5 for orchestration/projection；Spec/user only if a required behavior cannot fit accepted contracts。
- Completion criteria: all five requirements mapped to actual tests/source；UOE absent；DMN/write/access/codec regression green；eight-path diff only；17 MC PASS/N/A；strict Plan valid；Evidence truthful；no required work within Step remains。
- Rollback: revert only the eight paths before runtime consumers exist；no migration/data/config rollback。If committed terminal journal behavior later exposes an issue, source revert is safe because no native MCP consumer is in this Step；do not modify historical SQL。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`, `README.md`, `docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md`, `docs/egon/evidence/2026-11-01-readonly-mcp-journal-recorder.md`。
- Commit: `feat(agent): journal fixed read-only MCP invocations`

## 8. Test, Validation, and Quality Gates

| Test ID | Exact scope | Owning command/evidence |
| --- | --- | --- |
| `TEST-001` | typed MCP Recorder normal/failure/orchestration matrix and DMN compatibility | Recorder RED/GREEN commands in §8.1/§8.2 |
| `TEST-002` | Store/SQL MCP-only microsecond floor, replay, expired floor, cleanup codes and non-MCP compatibility | Store RED/GREEN commands in §8.1/§8.2 |
| `TEST-003` | eleven-class final regression, exact path/static/SQL/strict review | canonical and static gates in §8.3 and the gate table |

### 8.1 Effective RED commands

Recorder RED is run only after the exact `executeReadOnlyMcp` signature exists with a compile-only `UnsupportedOperationException` body and before any behavior implementation:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ToolInvocationRecorderTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-readonly-mcp-recorder-red.log 2>&1
```

Store/SQL RED is run against unchanged production behavior:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ToolInvocationStoreTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-readonly-mcp-store-red.log 2>&1
```

### 8.2 Focused GREEN commands

两个 RED 都被确认、两组 owner 完成实现并冻结全部 Java 后，由 Root 顺序运行；其他 owner 不执行 Maven。不得以 final selector 代替 focused GREEN 定位：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ToolInvocationRecorderTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-readonly-mcp-recorder-green.log 2>&1
```

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ToolInvocationStoreTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-readonly-mcp-store-green.log 2>&1
```

### 8.3 Final canonical command

All Java owners freeze before Root runs this command:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ToolInvocationRecorderTest,ToolInvocationStoreTest,ToolWriteInvocationStoreTest,ToolApprovedInvocationStoreTest,ToolIdempotentStartRecorderTest,ToolApprovedStartRecorderTest,ToolResultAccessRecorderTest,ReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolBindingsTest,ToolExecutionJsonCodecTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-readonly-mcp-journal-recorder-final.log 2>&1
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| 0 baseline | Tianji repo | `git status --short && git rev-parse HEAD` | clean baseline | clean; exact `24dfd9cf9e66405f223186b99baf1f9a979e19c5` | stop/reconcile unrelated changes | all; static |
| 1 RED Recorder | Tianji repo | §8.1 first command | RecorderTest | exit nonzero only for UOE/missing typed behavior; old tests not broken by fixture | File 1/UOE signature | PLAN-REQ-001, PLAN-REQ-003; module |
| 2 RED Store | Tianji repo | §8.1 second command | StoreTest | exit nonzero for unfloored/missing helper behavior | File 2 | PLAN-REQ-002, PLAN-REQ-004; module |
| 3 focused GREEN | Tianji repo | §8.2 exact commands | two focused classes | exit 0; all named tests pass | Files 3–5 | PLAN-REQ-001, PLAN-REQ-002, PLAN-REQ-003, PLAN-REQ-004; module |
| 4 final regression | Tianji repo | §8.3 canonical command | eleven classes | exit 0; exact Maven count recorded; no unexpected failures/errors/skips | owning source/test file | all; source reactor/module |
| 5 UOE/static | Tianji repo | exact `rg` command below and manual diff | new signature/helper/stub absence | no UOE in production; one formula; expected symbols only | File 3/5 | PLAN-REQ-001, PLAN-REQ-002, PLAN-REQ-004; static |
| 6 forbidden scope | Tianji repo | exact Git commands below | path scope | modified + untracked set equals exactly eight paths | Root integration | PLAN-REQ-005; static |
| 7 whitespace | Tianji repo | `git diff --check` | all eight paths | exit 0 | owning file | all; static |
| 8 immutable SQL | Tianji repo | exact before/after commands below | 23 SQL files | same file set and hashes | stop; remove unauthorized edit | PLAN-REQ-004, PLAN-REQ-005; static |
| 9 Plan | workspace/Tianji | `python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md --strict` | V5 Plan | PASS | Plan file | PLAN-REQ-005; documentation |
| 10 manual review | Tianji repo | inspect diff, source, logs, evidence, cached artifacts | privacy/transactions/time/compatibility | all 17 MC closed; no raw arguments/output/credential logging; no DDL/config/native | owning file/user if design drift | all; static/module |
| 11 live/manual | user-controlled system | not authorized in this Step | PostgreSQL/IAM/MCP/native runtime | explicitly not run | future authorized acceptance | residual; live |

No `scripts/work` harness is necessary: existing `verify-source-reactor.sh`, JUnit fixtures, `rg`, Git diff and SHA tools cover all Step gates. No new durable script is planned.

Exact static commands:

```bash
rg -n "UnsupportedOperationException|executeReadOnlyMcp|dispatchCallDeadline" \
 stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java \
 stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java \
 stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java \
 stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java \
 stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java

git status --short
git diff --name-only 24dfd9cf9e66405f223186b99baf1f9a979e19c5 --
git ls-files --others --exclude-standard
git diff --check
```

在实现前、实现后分别执行同一 SQL 清单命令，仅输出文件名和 SHA；第二次输出路径改为 `after`，随后 `diff`：

```bash
rg --files stellar-enclosure-tianji-agent-runtime/src/main/resources/db stellar-enclosure-tianji-process-server/src/main/resources/db stellar-enclosure-tianji-worker-starter/src/main/resources/db -g '*.sql' \
 | LC_ALL=C sort \
 | xargs shasum -a 256 \
 > /tmp/tianji-readonly-mcp-journal-sql-before.sha256

rg --files stellar-enclosure-tianji-agent-runtime/src/main/resources/db stellar-enclosure-tianji-process-server/src/main/resources/db stellar-enclosure-tianji-worker-starter/src/main/resources/db -g '*.sql' \
 | LC_ALL=C sort \
 | xargs shasum -a 256 \
 > /tmp/tianji-readonly-mcp-journal-sql-after.sha256

diff -u /tmp/tianji-readonly-mcp-journal-sql-before.sha256 \
 /tmp/tianji-readonly-mcp-journal-sql-after.sha256
```

## 9. Migration, Compatibility, Rollout, and Rollback

- Migration/DDL: N/A. Existing V13 already models generic READ_ONLY `PREPARED/DISPATCHING/SUCCEEDED/REJECTED/FAILED_KNOWN/UNKNOWN`, zero cost and no business receipt；V14 result-access is unchanged。No SQL or manifest path is touched。
- Generator: N/A. Agent profile is unsupported by the CRUD generator, and no table/catalog/CRUD scaffold changes。
- Public compatibility: additive `executeReadOnlyMcp`; existing `executeReadOnly`, START/AA/result-access APIs and all records remain source/binary compatible within current build。Private `dispatchAndRecord` change has no external consumer。
- Data compatibility: no existing row rewrite/backfill. MCP facts use the same descriptor/policy/outcome codec and V13 constraints。Conditional floor affects only future exact MCP dispatches before a native consumer exists。
- Configuration/permissions: none. No feature flag, Bean, permission registration, activation or role grant；library is not reachable from runtime/native yet。
- Rollout: after commit, only programmatic consumers can call the new method. A later separately planned native/config Step must bind the exact transport instance, current requester PDP, budget and plugin entry; it may not bypass this Recorder。
- Application rollback: path-limited source revert is safe while no native consumer is deployed. Persisted terminal rows, if produced by controlled tests or future programmatic callers, remain valid generic READ_ONLY facts and must not be deleted/rewritten。
- Forward fix: any discovered SQL/live precision problem requires a new authorized Step and, if schema correction is needed, a new migration; never edit V13/V14/history。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/§12 | Step 1 | Recorder/Test, docs | Recorder success/status/replay tests; final selector | typed method + direct outcomes + actual logs |
| PLAN-REQ-002 | §12/§13 | Step 1 | StoreTest, Store, SQL, Recorder | nano/micro floor, unfloored reject, replay, expired-floor tests | one helper used by all three boundaries |
| PLAN-REQ-003 | §13/§17 | Step 1 | RecorderTest/Recorder | full MCP observation/failure matrix, fact-before-outer-failure | actual terminal outcomes and no retry/reopen |
| PLAN-REQ-004 | §12/§13 | Step 1 | all five Java paths | DMN/write/AA/result-access/codec final regression; 23 SQL hash | compatibility and path diff |
| PLAN-REQ-005 | §17 | Step 1 | tests/README/Plan/Evidence | two RED logs, final canonical, strict Plan, 17 MC/10 Rules | truthful evidence + one eight-path commit |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| RISK-001 | shared deadline helper could alter IDW/DMN semantics | Store/SQL/Recorder | conditional exact risk+resource guard and compatibility tests | implementer/Root | Closed by design; tests required |
| RISK-002 | foreign Decision Failure inside MCP callback would be collapsed by Step 29 wrapper | Recorder | actual Step 29 catches foreign RuntimeException as NOT_SENT/UNKNOWN | Recorder owner | Closed: translate inside MCP callback |
| RISK-003 | cancellation/deadline after consume could be misrecorded known-unsent | Recorder mapping | MCP one-shot consumes before validation/network lifecycle | Recorder owner | Closed: only explicit OPERATION_NOT_SENT known |
| RISK-004 | TOOL_ERROR could be confused with business rejection | Recorder mapping/docs | MCP port has distinct TOOL_ERROR and no business evidence | Recorder owner | Closed: FAILED_KNOWN/DOWNSTREAM_TOOL_ERROR |
| RISK-005 | tests do not prove live PostgreSQL/JDBC rounding/locking | Store/SQL | controlled Store/SQL fixtures only; no DB authorized | future runtime owner | Residual, non-blocking for library; state explicitly |
| RISK-006 | no native consumer/config means end-user MCP execution remains unavailable | outside eight paths | explicit scope exclusion | future Step owner | Residual, expected; no completion claim |
| BLOCK-001 | repository/Spec/API drift before GO | all | baseline/hash/path inspection | Root/user | Closed only if baseline still exact; otherwise stop and revise Plan |

No open design decision remains for this Step. Runtime/live acceptance and native mounting are deliberate later work, not blockers to this bounded library commit。

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

本 Plan 在用户对原蓝图的连续执行授权内，记录 Root 收敛的八路径、typed entry、private deadline-aware outcome Function、MCP-only µ floor、direct MCP mapping、null operation before BEGIN、inside-callback foreign failure translation、only NOT_SENT known unsent、TOOL_ERROR known failure、one commit 和 canonical selector。用户将在提交后审查实现；这里不声称已获逐项设计批准。没有加入 DDL、依赖、配置、Bean、native、runtime 或第二持久编排。

### 12.2 Spec consistency

设计继续执行蓝图的逐动作授权、BEGIN-before-call、未知不重派、私密审计和 bounded failure 原则。`PLAN-CLAR-001..003` 都是 Root 根据源码确定的局部实现细节，未增加业务范围。Simplicity audit 没有 fetch-then-forward、caller-supplied identity、重复 mapper/model、cache、agent 或 layer。

### 12.3 Repository executability

五个 Java 路径和符号已在 baseline `24dfd9c` 核实；README MCP paragraphs 已存在；两个 docs 路径当前 absent。Test-first 中 compile-only UOE、两个独立 RED、两 owner GREEN、Root freeze/final sequence 明确。Canonical JDK/settings/script/selector、失败返回点、八路径 staging 和一个 commit 均已给出。

### 12.4 Test and release completeness

测试覆盖 normal path（SUCCESS）、failure path（TOOL_ERROR/DENIED/NOT_SENT/cancel/deadline/profile/invalid）和 orchestration edge（micro floor/PDP-BEGIN-SQL-call equality、replay without call、terminal fact survives outer failure）。Static gates覆盖 UOE absence、one formula、23 SQL immutable、diff/Plan strict。真实 PostgreSQL/IAM/MCP/native 未验证并明确保留。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | existing Agent runtime `infrastructure.persistence` five Java paths；generator Agent exclusion；§5 tree | exact current profile preserved; no third layer/module/DDL | None |
| `MC-REUSE-001` | Applicable | PASS | §4.7 ledger；existing Recorder/Store/SQL/V13/ports/codec/transactions | existing capabilities reused; no duplicate journal/auth/transport | None |
| `MC-DEP-001` | Applicable | PASS | POM excluded; JDK/Spring/current SDK only; backend-generation reference reviewed | no dependency/plugin/download/introduction | None |
| `MC-NAME-001` | Applicable | PASS | additive `executeReadOnlyMcp`, existing semantic VO/outcome/result; no new carrier | semantic behavior naming; no Data/Info/Param/Bean | None |
| `MC-VALID-001` | Applicable | PASS | §7 per-file typed guards and negative tests across port->Recorder->Store->SQL | every affected internal handoff validates exact typed state/deadline; no Spring proxy boundary added | None |
| `MC-MODEL-001` | Not applicable | N/A | no new/changed POJO/record/entity/enum; existing immutable values reused | no modeling/Lombok construction change | None |
| `MC-CONVERT-001` | Not applicable | N/A | no new cross-layer type or Converter; direct existing record/outcome construction | MapStruct/BaseConverter not applicable without a conversion boundary | None |
| `MC-LOG-001` | Not applicable | N/A | no production log statement/class annotation change; existing classes unchanged in logging role | no concrete business log change | None |
| `MC-BEAN-001` | Not applicable | N/A | three production types remain non-Spring manually constructed infrastructure; no dependency field/Bean/config | no Bean/injection/Qualifier/lombok.config change | None |
| `MC-UTIL-001` | Applicable | PASS | JDK `Function`, `Instant`, `ChronoUnit`; no helper class/dependency | closed utility allowlist satisfied | None |
| `MC-JSON-001` | Applicable | PASS | `ToolExecutionJsonCodec` unchanged; final selector includes `ToolExecutionJsonCodecTest`; no wire contract edit | persisted JSON remains single existing Jackson codec | None |
| `MC-TIME-001` | Applicable | PASS | §4.9 TIME-001/002；Store-owned exact formula；precision/compat tests | explicit technical instant/duration and µ policy; live PostgreSQL remains unverified | None for library; retain live boundary in Evidence |
| `MC-CONFIG-001` | Not applicable | N/A | §5 has no YAML/properties/config class; no key change | profile parity unaffected | None |
| `MC-PATTERN-001` | Applicable | PASS | §4.5/§7 File5 private `Function<Instant, ToolExecutionOutcome>` | private Strategy isolates real transport variation while one orchestration remains | None |
| `MC-SCOPE-001` | Applicable | PASS | exact eight paths, excluded scopes, SQL/POM/config/native prohibition, path gate | smallest accepted architecture-safe change | None |
| `MC-TEST-001` | Applicable | PASS | §8 two RED commands, focused GREEN, eleven-class final, static/SQL/strict gates | normal/failure/orchestration edge and regressions planned with exact commands | None before GO; execution must record actual results |
| `MC-BLOCKER-001` | Applicable | PASS | §11 risks closed/residual; no open implementation decision | no unresolved blocker inside Step scope | None |

### 12.6 Final verdict

`PASS — Ready for user review`

该 verdict 仅表示 Plan 内部完整、可执行且无未决 Step 30 设计阻断；不表示代码、数据库、服务、IAM、MCP 网络或 native runtime 已验证，也不自动授权实现。Root 必须先将 draft 复制到 exact path、全文读取、strict validate、记录 Plan SHA，并获得/确认 implementation GO。

## Java / CQE contract review

- POJO/value-object/enum: 无新或变更 model；existing immutable records/ToolExecutionOutcome 复用，`MC-MODEL-001` N/A 有证据。
- Components/MP reuse: existing runtime journal/transaction/capability/PDP/codec reused；无 persistence dependency/MP/DDL change。
- Annotation validation: 无新 Spring proxy/layer entry；internal typed validation 在 Recorder/Store/SQL exact guards，测试正反例；不以 ValidationUtils 替代业务规则。
- Business uniqueness/DDL: 无表、唯一键、soft-delete 或 migration 变化；23 historical SQL immutable。
- CQE delivery: 本操作是现有 journal command；readonly 业务调用仍写 audit fact。无新 Event 要求或 Event 发布；不使用 local event 冒充 Outbox/MQ。
- Architecture: exact Agent runtime infrastructure persistence preserved；no hybrid layer/generator。

## Backend generation and dependency evidence

已读 `references/backend-code-generation.md`。本 Step 无依赖引入、DDL、CRUD、catalog artifact 或新项目；Agent profile 本身不由该 generator 支持，因此 `stellar-enclosure-codegen.sh`、FreeMarker、MP-SDJ manifest、classpath SQL journal 均 N/A。任何实现中出现 POM、SQL、generator state、catalog Java/XML、配置或新依赖都构成 scope violation 并阻断提交。
