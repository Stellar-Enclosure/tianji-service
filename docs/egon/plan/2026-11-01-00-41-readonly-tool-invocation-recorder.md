# T3-07 只读工具调用的持久dispatch与结果记录

| Field | Value |
| --- | --- |
| Document | `2026-11-01-00-41-readonly-tool-invocation-recorder.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 00:41 Asia/Shanghai` |
| Updated | `2026-11-01 00:41 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | readonly Recorder、单次持久dispatch/nonce、历史结果及分离预算结算；9路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@06219937f9c1f10b65a1a8e689e9ab56e71ec8a0`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本轮用户明确exact剩余工作执行授权，无规范Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `9bcfc5c22299a746ab537d9693b0ede2133aa6ebe44b1c737435058187f3cfce` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定DMN callback传输](2026-10-31-23-32-fixed-dmn-tool-transport.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

固定DMN transport已提供可信SERVICE callback/oneShot，本步骤将它与当前requester权限、持久nonce、typed outcome和原预算组合成显式readonly Recorder。先COMMIT dispatch，才允许首个committer消费oneShot；结果先独立COMMIT，才best-effort结算。重复/UNKNOWN不重派，迟到事实不因当前run/lease/cap变化丢弃。当前不注册native consumer、不发独立run events、不改SQL/codec/budget/transport/config/权限；仅9路径。

## 2. Target Spec and Effective Design

原蓝图§7短事务/fencing/终态、§12工具可靠执行与unknown、§13当前权限/预算/审计、T3-07为主设计。基线0621993已有callback传输和准备库；本次保留现V13及public prepare两状态语义，通过ONE内部准备流/projection和ONE共用stored-row parser支撑Recorder历史metadata。live权限只用于开始/私密结果读取，历史完成凭原nonce/epoch/executor和不可变tuple，不从当前token或模型参数推导权限。

## 3. Effective Requirements and Acceptance

PLAN-REQ仅为原要求的局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/12/13/T3-07权限与单次执行 | 真实callback内fresh requester DISPATCH且短事务commit nonce后才oneShot | exactcheck/audit/E1E2/run live fence；PREPARED CAS一次、随机32byte只存SHA、actual SERVICE executor；重复/UNKNOWN不重派 | Recorder/Sql/Test |
| PLAN-REQ-002 | §7/12/13迟到/未知事实 | 结果持久化独立于当前授权/租约，unknown不等于允许重试 | originalnonce/epoch/executor/tool/params/cap历史/audit grant完成；late terminal/fence/cap reissue仍接；UNKNOWN可同nonce收敛known；known幂等或冲突 | shared parser/complete/Test |
| PLAN-REQ-003 | §13预算与故障一致性 | outcome先commit、结算失败不能擦除或降级事实 | 单独best-effort settle(0,0,0)，PENDING可见；非billable DMN已知cost0，不把未知操作结果误当未知费用 | Recorder/预算原API |
| PLAN-REQ-004 | §6/7兼容与秘密 | 不复制parser/prepare流程，不泄Outcome/nonce或误改run终态 | metadata-only recorder准备投影；public prepare仍2state；knownprivate outcome需fresh权限/live/旧deadline；cleanup只PREPARED0不泄advanced | Store/Sql/Test |
| PLAN-REQ-005 | §17/T3-07验证/边界 | 有受控/optin故障证据且不伪称PS完整provenance或native闭环 | first-only/concurrency/late/nonce/cleanup/accounting/outerfailure测试，现PGhelper仅compile/skip；无新表或借字段存PS信息 | tests/README/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

最小新类型/签名声明供测试编译；各owner独立真RED后实现。先common row/parser+单prepare投影保持旧17项，再Recorder begin/complete/cleanup/accounting；扩原PG helper和说明/报告。dispatch outcome采用原typed codec，不改传输/预算类。9路径一个逻辑commit，不提交scaffold。

### 4.2 Test-first strategy

新增RecorderTest先覆盖COMMIT后首个only operation、并发begin/no resend、known读取freshdeny无outcome、nonce/epoch/executor/hash错、late after cap reissue/run terminal、UNKNOWN refinement/idempotence/conflict、precallback cleanup、budgetfail及outerpostfail保留实际结果。原StoreTest保留public两state断言，另测metadata allvalidstates/common parser；其既有Fixture可仅改package-private供新RecorderTest复用，由SQL owner独占编辑、不建testkit；准备只一次PREPAREcheck，不复制第二套授权/parser。所有RED区别缺失stub/断言与fixture/编译错误。

### 4.3 Sequential and parallel boundaries

唯一Step。共用parser/projection签名先锁；Recorder/测试与旧Store/Sql/PG按明确所有权独立RED→GREEN，全体单commit。后续native/run-event revision协调/审批write不提前实现。

### 4.4 Commit boundaries

§5九路径为持久readonly执行一个逻辑切片。原SQL/codec/BudgetService/transport不改，显式stage后commit；不push/改历史，后续再接native。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Recorder | T3-07先receipt/权限再动作 | 原prepare+oneShot callback分离 | 组合现有port和同源Sql，不造执行引擎 | Implement |
| metadata投影/common parser | §7/13历史安全与兼容 | 旧prepare只PREPARED/AA，未来readonly已有7state | 单flow/projection+单parser，非第二执行parser | Implement |
| late completion/accounting分离 | §7/13故障/预算 | 原settle需live/可能失败；receipt不能因此丢失 | 单独结果tx后原best-effort settle，Pending明确 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| shared历史metadata | PLAN-REQ-004 | Store现有/新增metadata断言 | 单projection/common row类型 | 不含outcome/secret的prepared metadata | Recorder | Step 1 |
| begin/complete/cleanup | PLAN-REQ-001/002/004 | Recorder故障/权限/nonce断言 | sharedSql、原cap/check/transport | 一次dispatch与历史typed事实 | 后续native消费者 | Step 1 |
| accounting/运行证明定义 | PLAN-REQ-003/005 | settlementfail、PGhelper | 上项、原budget API | Settled/Pending及持久测试 | 用户验收 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AR技术Store/Sql、原typed port/callback/codec/budget | 原库边界不动 | 只新Recorder组合，不改AS/DTO/依赖框架 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Model/validation | 不可变Request/Outcome/metadata、strict旧audit | metadata不认证，secret私有 | nested RecordResult/attempt metadata真值，nonce只private且redacted；完整tuple/state/budget解析 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time | manual技术JDBC库/原codec/Clock/DBtime | 无新Bean/JSON格式 | 原codec复用；live gate与历史事实时间分离，完成中断不吞事实 | Step1；MC-BEAN-001、MC-JSON-001、MC-TIME-001 |
| Pattern | 原授权port/callback依赖倒置+transaction participant | 不复制调度/运行态 | Recorder协调已存在扩展点，commit-before-operation/outcome-before-accounting | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 当前/历史准备 | ToolInvocationStore/Sql、cap、typed check | 原10s准备/原parser两state | 只缺projection与历史state解析 | 单flow/common row扩展 | None | Step1；MC-REUSE-001 |
| transport/outcome | ReadOnlyDecisionToolTransport、ToolExecutionOutcome/codec | 可信callback一次操作/typed观测已有 | 足够，不改SDK/transport | callback内Recorder先持久事实 | None | Step1；MC-DEP-001 |
| accounting | ledger.budgets.settleBudget | 原run-first/终态幂等已修 | live失败可能Pending | outcome commit后独立best effort | None | Step1；MC-REUSE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Recorder/Result/PreparationVO/Sql角色 | 声明→tests→实现 | 职责后缀清楚、nonce不public | 编译/类型审阅 | Step1 | PASS |
| Rule 2 | §7/12/13 | exact typed check/cap/nonce/source/state/budget | Store/Sql/Recorder | live开始和历史完成权限模式分开，完整tuple/scopedSQL校验 | 正反/故障测试 | Step1 | PASS |
| Rule 3 | §13 | immutable metadata/results/attempt值 | 新/改库 | true values，集合/secret防御；不新实体/Converter | 构造/脱敏/兼容 | Step1 | PASS |
| Rule 4 | §6 | 原manual技术JDBC库，无新Spring服务 | Recorder/Sql | 不增加业务Bean/DI/日志，nonce不日志 | diff | Step1 | N/A：无业务Bean面 |
| Rule 5 | §6 | JDK SecureRandom/SHA及原SDK/Spring | 全代码 | 32byte随机、只存SHA、不增库 | imports/POM | Step1 | PASS |
| Rule 6 | §13 | 原typed audit/outcome codec | common parser/Record | 不加JSON字段，不借输出/audit存PS元数据 | 原codec回归/strict row | Step1 | PASS |
| Rule 7 | §6 | 无profile/config改动 | 文档 | 不自动启用native/Recorder | diff | Step1 | N/A：配置不变 |
| Rule 9 | §7/12/13 | 原port/callback/transaction participant | Recorder | 使用已有Adapter/callback，分离commit边界，不造第二执行层 | 原子性/顺序测试 | Step1 | PASS |
| Rule 10 | §7/13 | Instant/DBclock/mono，原prepared deadline | tests/Recorder/Sql | callDeadline与beginWindow分离，audit覆盖整调用，历史late不因期限丢弃 | TEST-002 | Step1 | PASS |
| Rule 11 | §6 | 原AR库/V13冻结 | 9路径 | 不改SQL/codec/BudgetService/transport/native/events/权限/deps | diff/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | original dispatchNotAfter、callDeadline、DISPATCH Audit expiry | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对期限 | Not applicable: 无UI | callDeadline=min(原prep期限,run.deadline,VerifiedExecutor expiry,E1expiry)，Request.deadline=callDeadline且audit覆盖整call，不是短begin窗口 | 原Clock/DBclock/MICROS，不延长历史deadline | 保持Step8强合同；transportRequest仍exact originaldispatchNotAfter | Step 1；TEST-002：短proof拒绝/过期无输出 |
| TIME-002 | begin local wall/mono及事务窗口 | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无展示 | beginWindow=min(now+10s,callDeadline)，原wall/nano；begin COMMIT后OneShot前再核window/callDeadline | 原Clock/LongSupplier，RC tx10s/query5s | PDP RPC自身bounded但验证expiry覆盖callDeadline | Step 1；TEST-002：锁后/迟到beforeeffect |
| TIME-003 | dispatchedAt/finishedAt/历史receipt/accounting | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对事实 | Not applicable: 无UI | 原nonce完成允许期限/lease/run/cap过期，不新发sideeffect；结果先commit再独立结算 | DBtime，短事务；完成阶段可暂清并恢复中断以不吞已发生事实 | 不改run终态/事件revision；预算原live失败记PENDING | Step 1；TEST-002：晚完成/终态/中断/结算失败 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java | readonly execute/begin/complete/cleanup/accounting协调与Result | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java | 一次/权限/nonce/late/cleanup/accounting故障 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | ONE共用7state row/parser及历史事实/SQL参与 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | ONE prepareInternal +LEGACY/RECORDER metadata投影 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java | 原17兼容/metadata历史/单check/parser | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 原opt-in并发/晚完成/trigger/预算失败helper | Step1 |
| MODIFY | README.md | 当前typed事实及provenance/native边界 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-readonly-tool-invocation-recorder.md | 实际命令/17MC/10Rule/未验收 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-00-41-readonly-tool-invocation-recorder.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

只改§5九路径；原V13 SHA保持`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`。原codec、BudgetService、transport、AS/native/config/权限及run event不动；不建第二verifier，Recorder own verified flag复用既有shared verifier。已有所有selector类已核存在，新RecorderTest按scope创建。原PG URL/vector patch前置不变，默认compile/skip，不启动DB。

Recorder public ctor(source,ledger,preparations,capabilities,authorization)，package追加Clock/LongSupplier；usesDataSource/Ledger/Preparations/Capabilities/Authorization按exact pointer核同源。唯一业务方法executeReadOnly(ReadOnlyDecisionToolTransport,PreparationRequestVO):RecordResult，只READ_ONLY/action+expiry null、estimatedCost为known zero。RecordResult为不可变(runId,UUID invocationId,state,revision,dispatchEpoch,dispatchedAt,finishedAt,nullable ToolExecutionOutcome outcome,boolean operationInvoked,AccountingState accountingState)，AccountingState为SETTLED/PENDING、toString脱敏。operationInvoked只表示本方便方法是否实际调用OneShotDecision.evaluate，调用前置true，不等于HTTP已发送/有副作用；所有replay和begin commit后窗口过期未调用为false。

Store新增package-private prepareOrLoadForRecorder(request):RecorderPreparationResultVO(RecorderPreparationVO invocation,AgentBudgetReservation budget)，在同一PREPARE/live/E2事务取得metadata及accounting，不能额外查询或PDP。内层RecorderPreparationVO为(runId,invocationId,reservationId,state,revision,dispatchEpoch,capabilityIssueEpoch,createdAt,dispatchNotAfter,dispatchedAt,finishedAt)，**只有metadata**，不含outcome/audit/nonce/executor/args。UNKNOWN且原budget已SETTLED0才Accounting.SETTLED，否则PENDING；UNKNOWN/DISPATCHING replay绝不重派或主动结算。重构ONE prepareInternal，Projection LEGACY_PREPARATION/RECORDER_METADATA，共用一次PREPARE check、原E1/ownedTX/E2/intent/budget。public prepare仍只PREPARED/AA，advanced仍unavailable；Recorder metadata允许所有valid V13七state。metadata ACK在commit后即便prepare窗口已过也可返回事实（非permit），public legacy原postcommit检查保持。

ToolInvocationSql使用ONE common StoredInvocationRow解析七state全部identity/caphistory/dispatch/audit/outcome/budget；不创建第二执行parser。PREPARED/AA仍核当前capepoch/grant；advanced历史不得因currentcap epoch/grant轮换而失败。metadata load仍须current E1/E2/live/fixed source，历史completion没有这些当前条件。

执行顺序：prepare/load metadata→transport Request.deadline EXACT原dispatchNotAfter→trusted callback。fresh requester DISPATCH在任何privateoutcome读取前执行（即使已knownterminal）；callDeadline=min(原dispatchNotAfter,run.deadline,VerifiedExecutor.expiresAt,E1 expiry)，本地beginWindow=min(callback内DISPATCH阶段entry now10s,callDeadline)原wall/nano，绝不在PDP后重启。**authorization.Request.deadlineAt=callDeadline**，Audit非nullALLOW、原requester类型/grant/checkedwindow、expiry覆盖整个callDeadline；PDP RPC自身有界。Store事务内guard before/after SQL、不RPC。包内DispatchWindowVO(startedAt,startedNanos,deadlineAt即begin截止,callDeadlineAt)必须传原值，不在PDP后重建；begin/read使用原window/可信本地guard。

begin事务run FOR UPDATE核live actuallease/owner/epoch/cancel/deadline/policy→E2 full equality→inv/budget scoped locks。仅首次PREPARED CAS/revision/epoch0可进入DISPATCHING：Recorder本地SecureRandom32byte生成canonical64lowerhex plaintext，Store只接SHA256，持久epoch1/hash/actual trusted callback SERVICE issuer+id/DISPATCH Audit/DBdispatched/updated，readonly side effect NONE。begin返回DispatchBeginResultVO(invocation,DispatchAttemptVO attempt)，started()等于attempt非null，仅首committer有attempt，advanced返回null。begin确认COMMIT后的attempt ACK必须先返回；Recorder拿ACK后才postcommit check beginWindow/callDeadline，过期则用持有的nonce写FAILED_KNOWN/DISPATCH_NOT_SENT，operationInvoked=false；不能先丢ACK导致无法记录未发送事实。重复DISPATCHING/UNKNOWN只metadata/noOneShot；knownterminal只有fresh权限/live/旧deadline仍未来才返回outcome。

nonce plaintext只Recorder局部，不进任何DTO/SQL/log/model。私有attempt只携historical immutable metadata+nonce SHA/executor/DISPATCH audit/callDeadline；complete(request,attempt,plainNonceHex,outcome)对decoded32bytes重新SHA，DB已有hash本身不是credential。完成独立短事务只核原nonce/epoch/executor/tool/backend/params/capIssueEpoch/原audit grant/state，不查当前PDP/cap/grant/runstatus/livelease/deadline。允许run终态/新fence/cap reissue后记late事实，不改runstate。DISPATCHING→terminal，UNKNOWN可由同原nonce收敛known；known同outcome幂等，改变则冲突。完成不能仅因worker中断丢事实，可在私有bounded receipt阶段暂清/最终恢复interrupt，无新sideeffect。

结果映射：MATCHED→SUCCEEDED/实际schema output；NO_MATCH→REJECTED/DECISION_NO_MATCH；OPERATION_DENIED→REJECTED/DOWNSTREAM_PERMISSION_DENIED；OPERATION_REJECTED→REJECTED/DOWNSTREAM_REJECTED；evaluate的CANCELLED/DEADLINE→FAILED_KNOWN/DISPATCH_NOT_SENT（port保证pre-HTTP）；invalid response→UNKNOWN/OUTCOME_INVALID_RESPONSE；unknown/unexpected Runtime→UNKNOWN/OUTCOME_UNKNOWN。nonSuccess空output、readonly receipt null，fixedDMN nonbillable actualCost0是已知零，不等于捏造unknown fee为零。

outcome确认COMMIT后，**另一事务**best-effort原ledger.budgets.settle(0L,0L,ZERO)；失败AccountingState.PENDING，不rollback/downgrade outcome。caller外层credential/release/close/deadline失败只metadata cleanup：严格fullrequest/originallease、仍PREPARED/epoch0/nonce+executor null才FAILED_KNOWN fixed allowlisted error/0cost、保留PREPAREaudit，随后独立结算；expectedMetadata可null以覆盖prepare commit后ACK丢失，但advanced只metadata、无outcome/overwrite。cleanup只允许CANCELLED、DEADLINE_EXCEEDED、CREDENTIAL_UNAVAILABLE、RELEASE_UNAVAILABLE、RELEASE_CHANGED、AUTHORIZATION_DENIED、AUTHORIZATION_UNAVAILABLE、DISPATCH_NOT_SENT八个固定code，返回Optional metadata，不返回outcome。若callback已记录真实结果而outer transport postkey/trust/time拒绝，重新抛安全Failure抑制model输出，不把success改UNKNOWN。

V13没有PS decisionExecutionId/rule/submittedAt/receivedAt的合法位置。不得借businessReceipt/audit/error/output塞provenance，不新增migration。本步只声称durable typedOutcome和可重建deterministic downstream requestId/run correlation，不声称durable PS rule级溯源；有具体新需求时另行计划迁移。无需独立RunEvents，Executor revision协调留后续。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 只读callback内一次持久dispatch、历史结果与分离结算

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: baseline0621993 callback transport及原prepare/typed audit/cap/budget/V13；无未提交前置。
- Baseline state: prepare只两state且无Recorder、持久nonce或业务结果；callback协议已允许先记事实后outer检查。
- Observable outcome: 首个confirmed committer才调用oneShot，历史nonce完成不丢late事实；重复/unknown不重派，私密outcome需fresh授权。
- End state: 未注册readonly Recorder和strict共用七state parser可用；native/writeapproval/run events/PS provenance未交付。
- Test-first gate: Required — package DTO/guard/方法和Recorder最小声明供编译，各owner合法fixture后真实RED再实现；不把编译/fixture错误或stub缺失伪写成零Errors。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java`

- Purpose: Define first-only durable dispatch, private result authorization and historical completion before behavior.
- Symbols: New readonly execute, commit/nonce, concurrent begin/replay, fresh deny, late receipt, cleanup, accounting/outer postfailure tests.
- Repository evidence: Existing controlled JDBC/Spring/typed port fixtures; final common helper declarations as section6.
- Dependencies and consumers: Minimal Recorder/packageStore/row declarations; controlled trusted transport callback/authorization/Clock/nano, no real service.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: Public convenience/RecordResult only; package tests can validate original nonce proof without exposing public cleanup.
- Input/output and state mapping: Known-zero readonly; first CAS commit before oneShot; current DISPATCH audit covers callDeadline; actual SERVICE executor; once/UNKNOWN no resend; metadata accounting truth.
- Error and edge behavior: Wrong plaintext/hash/epoch/executor/tool/params/auditgrant rejected; DBhash alone not credential; late terminal/fence/cap change accepts original proof; unknown convergence/idempotence/conflict; completion interrupted still records; no outcome on denied/expired replay.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void one_committer_records_dispatch_before_consuming_the_callback_capability() {
    result = recorder.executeReadOnly(trustedTransport,validKnownZeroReadRequest()); assertNotNull(result);
    assertBeginCommitBeforeOneShotAndOnlyFirstReceivesAttempt(); assertPlainNonceNeverInSqlDtoLogOrModel();
    assertFreshDispatchProofCoversActualCallDeadlineAndBeforeOneShotWindowIsRechecked();
    assertKnownPrivateReadRequiresCurrentAuthorityAndUnknownDispatchReplaysAreMetadataOnly();
    assertOriginalNonceLateCompletionUnknownRefinementIdempotenceConflictAndInterruptedReceipt();
    assertOutcomeCommitBeforeSeparateAccountingAndOuterFailureNeverDowngradesKnownFact();
    assertPrecallbackCleanupOnlyPreparedZeroAndNoAdvancedOutcomeLeak();
}
```

- Verification contribution: TEST-001 real Recorder RED/GREEN; TEST-002 transaction/authority/secret/history/accounting boundaries.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`

- Purpose: Preserve public two-state preparation while defining metadata/history and one shared parser.
- Symbols: Original17 tests retained; recorder metadata+budget wrapper, advanced states, projectionACK and common strict row tests.
- Repository evidence: Existing prepare/audit/cap/intent/budget fixtures and strict current two-state assertion.
- Dependencies and consumers: Compile-only package Store projection/metadata/helper DTOs; no new parser test class.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: ONE prepareInternal, LEGACY_PREPARATION vs RECORDER_METADATA; RecorderPreparationResultVO contains same-TX metadata+budget only.
- Input/output and state mapping: Preparedcap current epoch/grant checks remain; valid advancedhistory no mutable currentcap comparison; metadata ACKmayreturnafterpostcommitwindow, legacy oldcheck remains.
- Error and edge behavior: No outcome/audit/nonce/executor/args in metadata; fake advanced states/invalid audit/budget still reject; publicprepare advanced unavailable and no duplicated PREPARE calls.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void one_prepare_flow_exposes_only_the_selected_projection() {
    assertAllOriginalPreparationCasesAndLegacyAdvancedRejectionRemain();
    metadata = store.prepareOrLoadForRecorder(validRequest()); assertNotNull(metadata); assertMetadataAndBudgetFromOneTransaction();
    assertNoPrivateOutcomeOrNonceFieldsAndOnlyOnePrepareCheck();
    assertAllValidV13StateRowsShareOneParserAndHistoricalCapRotationDoesNotInvalidateAdvancedFacts();
    assertRecorderAckSurvivesOnlyPostcommitWindowExpiryWhileLegacyGuardRemains();
}
```

- Verification contribution: TEST-001 projection/parser RED/GREEN and original17 compatibility.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`

- Purpose: Own one canonical stored row/parser and transactional begin/read/complete/cleanup operations.
- Symbols: StoredInvocationRow all7V13 states; scoped source/row/budget locks; CAS, nonce SHA, original-dispatch proof, metadata projections.
- Repository evidence: Original V13/state/audit/outcome/budget constraints and prepare participant; no schema change.
- Dependencies and consumers: Existing codec, cap, budget typed values; Store-owned transactions/guard; no external calls.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: No second execution parser. Shared package DTOs live in Store. Begin takes nonceSHA only, complete recomputesSHA(decodedcanonical32byte nonce); currentlive vs historical fact validation separated.
- Input/output and state mapping: PREPARED->DISPATCHING revision/epoch1/audit/actualSERVICE/time/hash atomic; historical complete DISPATCHING->known/UNKNOWN and UNKNOWN->known; known exact idempotent else conflict.
- Error and edge behavior: Prepared rows currentcap strict; advanced audit/caphistory do not compare mutable current grant/epoch. No currentPDP/lease/deadline gates for exact past proof completion; no runstate/events write. Cleanup only prepared0/nullsecret, advanced metadata only.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
parse ONE StoredInvocationRow with full identity/tool/backend/parameters/caphistory/dispatch/audit/outcome/budget and all7V13 state groups;
prepare reused by both projections; begun dispatch: live runFORUPDATE->current capE2->scoped invocation/budget, require original guard and firstPREPARED CAS;
store only nonceSHA, epoch1, actualSERVICEissuer/id and DISPATCH audit/time; return committed metadata/attempt ACK, never plaintext;
complete: hash decoded32byte canonical private nonce; match original epoch/executor/immutabletuple/capIssueEpoch/auditgrant and state, ignoring current live authority;
commit typed terminal outcome (or same-nonce UNKNOWN->known), exact known replay only; do not modify run or join accounting;
cleanup: strict original request/lease and PREPARED0/nullnonce/executor only, allowlistedFAILEDKNOWN/cost0/PREPAREaudit; alladvanced metadata-only;
```

- Verification contribution: TEST-001 Recorder/Store state and original proof tests; TEST-004 true PG if explicitly supplied.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`

- Purpose: Provide one preparation projection flow and bounded private Recorder transaction surface.
- Symbols: prepareInternal Projection; RecorderPreparationVO/ResultVO; DispatchWindowVO; DispatchBeginResultVO/AttemptVO; begin/read/complete/cleanup helpers.
- Repository evidence: Original publicprepare/E1/check/TX/E2/intent/budget implementation and strict shared Sql.
- Dependencies and consumers: Only package-private Recorder surface; exact final fields and ACK rules in section6.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: Public prepare signature/result unchanged; prepareOrLoadForRecorder returns metadata+budget atomically; shared DTOs no plainnonce or outputs in metadata.
- Input/output and state mapping: ONE PREPARE check; RecorderACKpostcommitcanoutlivepreparewindow; current begin/read uses original call proof and beginWindow guards around SQL; complete/cleanup bounded technical facts use historical modes.
- Error and edge behavior: Never reset now10 afterPDP, never RPC in transaction; begin must return ACKbefore Recorderpostcommitcheck, so committed nonce can close NOT_SENT; complete cannot discard merely interrupt; accounting separate.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
prepareInternal(request,projection): keep original single10sE1/PREPARE/ownedTXE2/intent/invbudget path, return common internal row/action;
LEGACY maps only2states with old postcommit guard; RECORDER returns metadata+sameTXbudget, no secret/outcome, ACKevenpostcommitexpiry;
begin/read: carry original DispatchWindowVO and checked actualcallDeadline proof, guard before/after SQL, ownRC10s, no RPC;
begin ACK = DispatchBeginResultVO(invocation,firstOnlyAttempt), started iff attempt!=null; after confirmedcommit return before callerpostcheck;
complete(req,attempt,plainNonceHex,outcome): privatebounded historical proof mode, temporaryclear/restore interrupt if required, no current businessvalidity gate;
cleanup nullable expectedMeta: fullscope/originallease+allowlist, onlyprepared0/no nonce/executor; advanced metadata and no privateoutput;
```

- Verification contribution: TEST-001 oneflow/ACK/private helpers/legacy compatibility; TEST-002 past receipt vs current authority.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`

- Purpose: Compose existing verified callback with current requester authority, durable once-only begin and fact-first accounting.
- Symbols: Constructors/identityhelpers/verifyStorage; executeReadOnly; RecordResult/AccountingState; privatebegin/evaluate/map/complete/cleanup.
- Repository evidence: Existing readonly transport callback, ToolInvocationStore, typed check/cap and old budget service.
- Dependencies and consumers: Exact ctor/source/ledger/preparations/capabilities/authorization affinity; privateClock/nano; JDKSecureRandom.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: Public executeReadOnly(transport,request):RecordResult only; known-zero READ_ONLY/action+expiryNULL; no public callback/cleanup; output metadata fields section6.
- Input/output and state mapping: Prepare/loadmetadata; callbackactualSERVICE; fresh originalrequesterDISPATCH for begin or known private read; proofcoverscallDeadline. Generate canonical64hex nonce local, begin stores onlySHA. Firstcommitter ACKthenwindowcheck then operationInvoked=true justbeforeoneShot.
- Error and edge behavior: Repeated DISPATCHING/UNKNOWN no operation/no settlement; accounting from same metadata budget, UNKNOWNalreadySETTLED0 truthfullySETTLED. Knownoutcome only fresh/live/unexpired. AfterACKexpiry completeFAILEDKNOWN/NOT_SENT with nonce, falseflag. Preserve actualcallback fact if outer transportlater fails.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
executeReadOnly: validate exactreadonlyknown0request and ownverified/affinity; prepareOrLoad metadata+budget;
transport.withVerifiedCall with EXACT originaldispatchNotAfter; callback obtains actualServiceexecutor, E1 and original beginWindow/callDeadline;
authorization.check(DISPATCH Request.deadline=callDeadline) outsideTX with exact nonnullALLOWaudit; begin/read guarded sameTXE2 and live source;
only started ACK holds private local nonce; recheck beginWindow/callDeadline aftercommit, recordNOT_SENT if expired; otherwise setoperationInvoked=true and calloneShot outsideTX;
map observation/failure to exact typed outcomes as section6; complete and COMMIT historicalfact first, then separatebestEffort settle0 returning SETTLED/PENDING;
knownterminal reads require freshgate; UNKNOWN/DISPATCHING metadata-only use originalbudgetstate and neverresend/settle onreplay;
outer safeFailure: metadatacleanupallowlist onlyPREPARED0; if actualoutcome alreadycommitted, suppressmodelvalue/rethrowwithoutdowngrade;
```

- Verification contribution: TEST-001/002 one-operation, outcome mappings, private nonce/history/cleanup/accounting/outer failures GREEN.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`

- Purpose: Extend existing explicit empty-loopback fixture with real begin concurrency/late completion/rollback definitions.
- Symbols: Fresh helper20sameinvbegin oneoperation, late originalproof, triggerfailure, budgetfail keepingcommittedoutcome.
- Repository evidence: Original opted class URL/vector requirements and scoped fresh-run helpers.
- Dependencies and consumers: New Recorder/current helper objects and controlled identity/oneShot; no new property or infrastructure.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: Only opt-in class extension; defaultcompile/skip, neverstartsPG.
- Input/output and state mapping: Freshqueuecapattach/claim/prepare,20sameinv attempts -> single persisted epoch/noncehash/one evaluate; receipt acceptedlate, actualrunstate notchanged; outcomecommit survivesbudget failure.
- Error and edge behavior: Do not use expired oldfixture or catch real SQL/deadlocks as expected; triggerrollback/no extraoperation; allsource/schema hashesfixed.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
helper(): use fresh scopedrun and existing explicit toolPGurl/vector patch; prepare readonlyknown0inv;
race20sameinvRecorder attempts with controlledtrustedcallback and assert one begin/oneoperation plus metadata-only replays;
exercise originalnonce latecomplete afterrunterminal/capreissue and assert no runstate overwrite;
inject fixture-only completionwrite failure/rollback and separatebudgetfailure, prove committed outcome never becomesUNKNOWN or rollsback with accounting;
without explicitinputs compile/skip and retain runtime-unverified label, no database startup;
```

- Verification contribution: TEST-004 opt-in actual SQL/concurrency/atomicity definitions only; nonruntime does not prove them.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 7 — `MODIFY README.md`

- Purpose: Describe real readonly recording and limited provenance without claiming native activation.
- Symbols: CurrentT3-07 preparation/transport notes.
- Repository evidence: READMEalready distinguishes unregistered libraries and actual runtime acceptance.
- Dependencies and consumers: Final actual source/tests only.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: No configuration/endpoint/migration added.
- Input/output and state mapping: Explain commit-beforeoneShot, freshprivate-read, originalnonce latefact, separatedaccounting, noresendunknown and metadataonly paths.
- Error and edge behavior: No PSexecution/rule timestampsdurablyclaimed; no businessReceipt/audit/output misuse; no run events/native/approvalwrite.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Append current readonly Recorder facts and fact-first accountingPending/noresend semantics.
Explain actualcallDeadlineproof vs short beginWindow, historicalcompletion and outerpostfailure model suppression.
State durable typedOutcome/deterministicPSrequestId+run correlation only, not durablePSrule provenance; native/Recorderconsumer/runtime/IAM/PG acceptance remain pending.
```

- Verification contribution: TEST-003 source/README scope fidelity.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-readonly-tool-invocation-recorder.md`

- Purpose: Record actual per-component RED/GREEN and the nine-path historical-fact/accounting safety boundary.
- Symbols: Baseline0621993, exact outputs/exits,17MC/10Rule/fivealiases/fullT3Partial.
- Repository evidence: Priorper-Step reports and strict execution gates.
- Dependencies and consumers: Final actual source/tests/diff/stage, no assumedpasses.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: No productcontract, only concrete evidence and limitations.
- Input/output and state mapping: Trace proof/callwindow/CASnonce/oneShot/outcome/UNKNOWNrefinement/cleanup/accounting/compatibility and actual optinskip.
- Error and edge behavior: No plaintextnonce/privateoutcomeinlogs, no mock→crypto/SQL claims, no PSprovenance fabricated.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Record actual realRED/finalGREENcommands/counts/exits/skips and ninepaths with frozenV13.
Evaluate17ManualChecks and10LiteralRules independently with source/test evidence and valid N/A.
Record latehistoricalproofvsfreshprivate-read, commitACK/nonceSHA and outcomecommitbeforeaccounting, no runstate/events change.
Report no durablePSrule-provenance/native/approval/realcrypto/PG proof, no scripts/workharness or automaticruntime/push; commitonlyafterrootgates.
```

- Verification contribution: TEST-003 final actual audit beforeexplicitcommit.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

#### File 9 — `CREATE docs/egon/plan/2026-11-01-00-41-readonly-tool-invocation-recorder.md`

- Purpose: Lock the exact convenience/internal protocol and safe state/transaction commitments before source GO.
- Symbols: Metadata/currentSpec/baseline/strongdeadline/ACK/nonce/rows/accounting/fileorder/gates.
- Repository evidence: Originalexactlegacyuserauthorization and current API review, no new scope.
- Dependencies and consumers: Closedsection6 and ninepathinventory.
- Why now: Declare only compile prerequisites first; owning component tests observe real missing behavior before final implementation. Preserve old public semantics and use one shared protocol/parser.
- Contract/signature changes: Strictplan is source-groundedReview, not a fabricatedAccepted Spec.
- Input/output and state mapping: Singleflow/parser, historicalcompletion/currentauthority, no durablePSrule-levelprovenance claims; readonlyscope only.
- Error and edge behavior: Do not expand transport/codec/budget/SQL/config/native/events or mark implementationpassed from document.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical JDBC/value scope, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact current or originalproof validation, private nonce, existing codec/budget/callback and time semantics; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Finalize exactpublicandpackagecontracts, originalwindow/ACK/status/nonce/accountingtruth and privatecleanupallowlist.
Run strict validator on thisfile and inspect nineunique paths/scopedsource/currentSpechash.
Deliver validatedPlan to rootbeforeproductionfunctionality; actualresults later belong only in the evidence report.
```

- Verification contribution: TEST-005 strict document and root review, not runtime proof.
- After this file: Its declared proof is ready for focused verification; no automatic native/config/run-event consumer is enabled.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolInvocationRecorderTest,ToolInvocationStoreTest,ToolExecutionJournalPostgresTest,ToolCapabilityStoreTest,ToolExecutionJsonCodecTest,ToolExecutionStorageVerifierTest,AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,ReadOnlyDecisionToolTransportTest,TianjiFixedDecisionToolTransportTest,TianjiToolGrantAuthorizerTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: meaningfulRecorder/projection/parserREDthenallselectednonruntimeGREEN, actualcounts/errors/skipsrecorded; oldopt-inPGcompile/skipwithoutURL/vector, no server/DB startup.
- Failure returns to: owningfixtureifinvalidRED, exactRecorder/Store/Sqlifbehaviorwrong; no speculative schema/transport/native/codec/budgetservice changes.
- Completion criteria: fivealiasesimplementationevidence,ninepathscope/frozenV13,oldpublicpreparecompatibility,nonce/pastproof/currentread/accountingtruth;17MC/10RulesindividuallyPASSorvalidN/A.
- Rollback: stopnewunregisteredconsumer/explicitrevertthissliceifneeded; preservepersistedfacts/receipts/schema/history, unknownneverbecomesretrypermission, no automatic re-dispatch.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorderTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`, `README.md`, `docs/egon/evidence/2026-11-01-readonly-tool-invocation-recorder.md`, `docs/egon/plan/2026-11-01-00-41-readonly-tool-invocation-recorder.md`
- Commit: `feat(agent): record read-only tool dispatch and durable outcomes`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 source-reactor，按owner先focused再选定全组 | Recorder/oneflow/projection/commonparser与原兼容 | 真行为RED后GREEN，stub Errors/fixture errors分别诚实记录 | test/所属实现 | 五别名；受控SDK/JDBC |
| TEST-002 safety/failure | tianji-service | 同test nonce/ACK/window/currentread/pastcomplete/cleanup/accounting/outerfailure断言 | first-only/无resend/历史事实/私密数据 | 结果先commit、结算失败Pending；同nonce late事实保留；过期/拒绝不读outcome | Recorder/Store/Sql | 不代表真实DB/身份证明 |
| TEST-003 static/scope | tianji-service | `git diff --check`、9paths/V13 SHA/17MC/10Rule | 当前全差异与未改codec/预算/transport | 无nonce明文/PS元数据乱塞，原prepare17/旧guard保持 | 所属文件 | 无native/config/权限/events |
| TEST-004 optinPG | tianji-service | 原ToolExecutionJournalPostgresTest及原显式URL/vectorpatch | 20sameinv、late、trigger/独立budgetfail | 有库才实际运行；默认compile/skip，不自动启PG | Sql/Recorder/fixture | SQL/并发未跑必须Runtime unverified |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-00-41-readonly-tool-invocation-recorder.md --strict` | 文档/追踪/顺序 | exit0 | 本文 | 非实现/运行验收 |

所有既有selector已核真实存在，新RecorderTest为声明CREATE。可复用原StoreTest.Fixture最小package-private可见性，不新建testkit或scripts/work harness。测试为受控transport/SDK/Connection/Clock，不启动HTTP listener/应用/数据库/容器。文档生成脚本完成删除，错误与跳过不伪报通过。

## 9. Migration, Compatibility, Rollout, and Rollback

不增改SQL/V13、codec、BudgetService、transport/port、native/config/权限/deps或run events。现publicprepare继续2state并对advanced unavailable，原一次PREPAREflow保持；Recorder metadata允许7state但不暴露outcome/audit/nonce/executor。历史parser只扩一处，不新第二套执行parser。

已确认强合同：DISPATCH proof覆盖actual callDeadline，局部begin guard至多10s且原entry不reset；begin已commit则先ACK后Recorder再验窗口，不能丢原nonce控制权。完成是原历史事实，不因currentcap/grant/lease/run失效而删；但private模型outcome读取仍需fresh当前授权/live及旧deadline未来。Outcome与预算结算两个事务；PENDING表示本次结果尚未确认结算，可能需要后续核对，并不表示费用未知。不把成功降UNKNOWN或unknown当自动retry。

固定DMN费用已知0，仅当前readonly方法可用，不泛化成任何未知provider费用0。V13无PSexecution/rule/timestamps存放位置，本步明确不提供durable rule级provenance，不借receipt/audit/error/output字段、不加migration。回退停止新未注册Recorder消费者并显式revert，保留已有typed事实/noncehash/预算/原历史，不自动重派。native/run-event revision协调及业务write/审批消费留后续。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/12/13/T3-07 | Step 1 | Recorder/Sql/Test、Store helpers | TEST-001/002 | actual callbackSERVICE、freshRequester proof、CAS/commit/hash-only nonce、once |
| PLAN-REQ-002 | §7/12/13/T3-07 | Step 1 | commonrow/complete、Recorder/Test | TEST-001/002/004 | 原nonce tuple/history late、UNKNOWN→known、known幂等/冲突、无runstate写 |
| PLAN-REQ-003 | §13/T3-07 | Step 1 | Recorder/accounting和PGhelper | TEST-001/002/004 | outcome commit先、独立settle0/Pending、UNKNOWNreplay accountingtruth不重结算 |
| PLAN-REQ-004 | §6/7/T3-07 | Step 1 | Store/Sql/两test | TEST-001/002/003 | oneflow/projection/commonparser、private读/metadata-only/cleanup ACK |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | 所有tests/README/plan/evidence | TEST-003/004/005 | exact9paths、无durablePS provenance伪承诺、默认optinskip与native关闭 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | actualcallDeadline proof与短beginWindow分离 | Recorder/Store | 主代理明确保留Step8强合同，originalwall/nano不reset | 主代理 | Closed：§6/7精确两值及afterACK检查 |
| DEC-002 | 私有DTO/hash-only proof/ACK/cleanup/accounting | Store/Recorder/tests | 最终API审阅闭合，nonce仅Recorderlocal、metadata+budget同TX、allowlist和字段名固定 | 主代理/owners | Closed：不新增公共callback/cleanup或第二parser |
| DEC-003 | 结果先commit、late历史证明不查当前live | complete/accounting | 需避免PDP/租约/中断/预算故障抹掉已发生响应 | 主代理 | Closed：独立短receiptTX+best-effort结算，actual结果不降级 |
| RISK-001 | schema无法持久PSexecution/rule/time provenance | README/后续 | 冻结V13没有合法字段 | 主代理 | Tracked：只typedOutcome+确定requestId/correlation，不挪用字段或新迁移 |
| RISK-002 | 未执行真实PG/crypto/业务/native | 运行验收/后续 | 当前受控SDK/JDBC；无配置消费者或run events协调 | 用户/主代理 | Tracked：完整T3-07/平台继续Partial，不自动启动环境 |

## 12. Review and Acceptance

本计划直接落实原T3-07只读先行的nonce/receipt/failure一致性要求，复用现有callback、原预算和冻结schema。九路径、声明→独立真RED→实现→相关回归→一个commit明确。无引擎/新框架、权限或SQL扩张，不把constructor metadata或数据库hash当认证，不将历史receipt权限与当前私密输出读取混用；也不把迟到真实结果因outer拒绝改UNKNOWN。实际命令/失败/skip及fullPartial由报告记录，计划通过不等于runtime通过。

### 12.5 Blocking Manual Check

以下为计划级可执行性结论，实际source/output在实施报告逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence库、九路径、oneflow/commonrow | exact技术账本边界，无新层/引擎/AS改变 | None |
| MC-REUSE-001 | Applicable | PASS | 原prepare/cap/check/transport callback/codec/budget/verifier | 不复制身份、序列化、预算或运行框架 | None |
| MC-DEP-001 | Applicable | PASS | JDK SecureRandom/SHA+既有Spring/SDK/组件 | 无新增依赖/工具 | None |
| MC-NAME-001 | Applicable | PASS | Recorder/Result/PreparationVO/WindowVO/AttemptVO/StoredRow/Test | 明确角色值名，plainnonce不进DTO/公开面 | None |
| MC-VALID-001 | Applicable | PASS | fresh proof/E1E2/live与历史nonce原tuple两mode、strict row/state/audit/budget | 当前授权和历史完成不混用，无新HTTP传输入口 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变metadata/result/attempt值、原Outcome复用 | 非实体/可变POJO，secret只local，typed结果metadata安全 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层DTO复制；Observation→Outcome为语义结果映射 | 原codec/域值构造复用，不建绕过BaseConverter映射层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术JDBC Recorder库，无新增业务日志Service | 不打印nonce/参数/outcome/cipher，toString脱敏 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config/autowire变化 | 原技术manual构造及identity helpers，无Qualifier新面 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK/SDK/schema/hash/预算 | 无utility/线程/认证新平台 | None |
| MC-JSON-001 | Applicable | PASS | 原audit/outcome codec不变；严格common row | 不加JSON/provenance字段、不误用receipt/output/audit | None |
| MC-TIME-001 | Applicable | PASS | §4.9及最终ACK/window/call/late规则 | proof覆盖整call、shortbegin不reset，历史完成不取当前时限授权 | None |
| MC-CONFIG-001 | Not applicable | N/A | 九路径无profile/properties/flags | 新库未自动启用，native状态保持 | None |
| MC-PATTERN-001 | Applicable | PASS | 既有port依赖倒置+callback+transaction participant | 真实变化点复用，commit顺序明确，无第二执行/调度层 | None |
| MC-SCOPE-001 | Applicable | PASS | 九路径，原SQL/codec/budget/transport/native/events不动 | readonly窄方法，无write审批或PS provenance假承诺 | None |
| MC-TEST-001 | Applicable | PASS | §8有真RED/权限/nonce/late/accounting/outerfailure/旧17/optin命令 | 非运行证明可复跑，PG未跑保留标识 | None |
| MC-BLOCKER-001 | Applicable | PASS | 三项已闭合决定、final DTO/helper命名与各独立行 | 当前合同/事务/安全/兼容无未决设计，runtime限制显式 | None |

### 12.6 Final verdict

PASS — Ready for user review
