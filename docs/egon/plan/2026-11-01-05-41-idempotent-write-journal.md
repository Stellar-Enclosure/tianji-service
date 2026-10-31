# T3-07 通用幂等写journal原子准入与事实完成

| Field | Value |
| --- | --- |
| Document | `2026-11-01-05-41-idempotent-write-journal.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 05:41 Asia/Shanghai` |
| Updated | `2026-11-01 06:22 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR Store/SQL-only generic IDW admission/claim/completion；9路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@c155273186f93e43b5694ba34c999fc137635a37`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户原exact执行授权延续，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `078e0fc999e6531783e134bd880675aad5ce90d7b801d1d1da972732f5370b80` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [审批proof/完整intent历史](2026-11-01-04-56-approval-proof-intent-history.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
通用IDEMPOTENT_WRITE的package journal入口允许对仍unclaimed PREPARED action准入多个当前合法attempt，begin时全局仅一committer获得nonce attempt。相同current pointer完成原子写invocation+action；真实旧generation迟到结果只写认证过的旧invocation，不覆盖新current action。仅Store/SQL，无业务dispatch/Recorder/native/AAconsume/私密终态重读/settlement/config。九路径单Step与semantic commit；root完成全文/strict/resources审阅后于05:49发RED-only source GO，文档转Review，不虚构Spec Accepted或用户新增批准。

## 2. Target Spec and Effective Design
唯一effective设计为蓝图§7短事务/fence/迟到事实、§12稳定intent/receipt/UNKNOWN、§13当前权限/预算/审计与T3-07。复用已提交原Projection/prepareInternal/typedcheck/E1E2、完整ActionRow/commonStoredInvocationRow与预算API，不造执行/审批引擎。generic Store不假定START零费，也不实际调用transport。旧publicprepare/readonlyguard/contracts保持；fullSpec/运行验收仍Partial。

## 3. Effective Requirements and Acceptance
PLAN-REQ为原要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/12 | 非独占PREPARED action可准入合法自身attempt | 新projection对unclaimed/futureexpiry创建budget+inv，可新UUID/当前run；firstorigin/expiry不变；旧public facts-only | Store/InvocationSql/Test |
| PLAN-REQ-002 | §7/12/13 | 当前权限与全局action claim只产生一个dispatch attempt | freshDISPATCH actionID/fullcall audit/E1E2，run→cap→action→owninv/budget；inv先action CAS，loser仅关自身prepared | 3prod/new2tests |
| PLAN-REQ-003 | §7/12 | 原nonce历史事实有界完成，不受当前live抹除 | 同tuple原子pair，未来currenttuple不同则只旧inv；known幂等/UNKNOWN收敛/冲突，无redrive | SQL/Store/Test |
| PLAN-REQ-004 | §13 | generic费用/cleanup不得捏造0或擅自结算 | caller提供失败Outcome/null或非零fee；事实cost可>estimate；每freshattempt计自身TOOL调用/activecapacity，loserclose非refund；保原SETTLED guard与budgetUNKNOWN历史 | Store/SQL/Test |
| PLAN-REQ-005 | §6/17 | 原接口兼容/范围与真实证据 | 原readonly/public不变、新2focused tests真RED、旧回归/PGoptin compile skip、9paths | tests/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
最小package DTO/方法声明供编译→新2focused tests各真RED→Action无readCAS→Invocation双表编排/Projection→Store gate/fact保护→PG helper/文档。旧27项InvocationStoreTest不改，fixture留新focused类，不新增testkit路径。最终一个commit不留scaffold。
### 4.2 Test-first strategy
各组件先有效行为RED再GREEN；验证同action多attempt/firstexpiry、onlywinner/no-oldattemptmutation、inv先action/两表故障回滚、loser supplied fee、nonce/late/未来pointer/UNKNOWN、cleanup/interrupt、generic超estimate事实+budgetUNKNOWN及旧兼容。编译/fixture错误不能当RED，root统一Maven。
### 4.3 Sequential and parallel boundaries
Action owner独占ToolActionIntentSql、新ToolActionWriteSqlTest及原PG helper三路径；budget owner独占ToolInvocationStore/Sql与新ToolWriteInvocationStoreTest三路径。root负责README、统一Maven与门禁编排，不编辑PG helper；docowner仅两docs。05:56在Action组件33项GREEN后由root重新分配未编辑PG helper，原owner明确释放并由新owner接手；只改变内部所有权，九路径、合同、单Step/commit及验证范围不变。锁定§6合同后各组件有效RED→GREEN可并行，root已发RED-only GO，实施GREEN须各组件真实RED确认；不并行下一Step。
### 4.4 Commit boundaries
九paths一个逻辑Step，`feat(agent): commit idempotent write journal transitions`。只显式stage，无push/历史改写/服务启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| IDW Projection/package API | §12原stableintent治理 | 原public prepare对existing action facts-only，commonrow/typed gate已存在 | 扩同一flow，非新Store/Recorder/状态机 | Implement |
| Action两个CAS参与者 | §7双表/即刻FK | V13 backlink要求inv已存在同generation；Action完整parser已有 | 先inv后action；participant只CAS不midread | Implement |
| authenticoldfact/费用 | §7/13真事实不丢与真实成本 | nonce/历史metadata/parser/预算已存在 | latefact只改旧inv，cost如实/no settle，非重试/新generation API | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Action CAS | PLAN-REQ-002/003 | 新ActionWriteSqlTest | 最小声明/原完整Row | lock/no-read begin/complete participant | Invocation双表编排 | Step 1 |
| IDW gate/projection | PLAN-REQ-001/002/004 | 新WriteInvocationStoreTest | 原E1/check/E2/预算/上项 | metadata准入/唯一attempt | 未来Recorder，不是本步 | Step 1 |
| fact/cleanup/回归 | PLAN-REQ-003/004/005 | late/nonce/成本/interrupt/fault | 上项/原codec/parser | 成对或旧inv-only真实结果 | 后续consumer与环境验收 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | 原AR persistence/Projection/participant/完整Row/ledger | 不加层/新引擎 | 仅3prod扩展，旧public/RO不变 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Validation/model | 原immutable Request/Attempt/Outcome与历史Row | metadata非执行/当前读取许可 | 新VO真值/nullablepaired，无隐藏outcome；exact owner/action/audit/CAS | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time | 原manual JDBC/codec/Clock/DBtime | 无Bean/config新面 | 安全错误/redacted；genericcost原样；MICROS与fact窗口 | Step1；MC-JSON-001、MC-TIME-001 |
| Pattern | 现Unit of Work：TransactionTemplate+transaction participants/CAS，原singleparser | 复用当前事务风格处理claim并发与部分失败 | 同事务原子pair/旧fact分支；比分离直接更新安全，比新Handler/Stateengine更小，不建pattern类 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| action/准备/预算 | 原prepareInternal/Action.lockedfreshJOIN/ledger.budgets.reserve | 原currentgate/counters已实现 | 缺新IDW准入与claim编排 | reuse/扩原flow | None | Step1；MC-REUSE-001 |
| nonce/事实/解析 | 原DispatchAttempt/nonceSHA/commonStoredRow/ActionRow15 | 已有RO晚事实与八state投影 | 加IDW paired与autholdinv-only分支 | reuse | None | Step1；MC-DEP-001 |
| persistence/schema | V13立即backlink/已有全部列 | 不需新schema | 足够，禁止改迁移 | reuse | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/12/13/T3-07 | 新Write VO/package API/Sql参与者命名 | 新tests→ActionSql→InvocationSql/Store | 明确metadata/attempt/action职责与redacted | 编译/类型 | Step1 | PASS |
| Rule 2 | §6/7/12/13/T3-07 | IDW/action/audit/E1E2/nonce/backlink/SQLCAS | Store/SQL/新tests | 当前权与历史proof分离，完整owner/source/time验证 | 正反/并发 | Step1 | PASS |
| Rule 3 | §6/7/12/13/T3-07 | immutable DTO/原ActionRow/typedOutcome | Store/SQL | nullable成对、无outcome私密读/实体副本 | 构造/metadata | Step1 | PASS |
| Rule 4 | §6/7/12/13/T3-07 | manual技术Store/SQL无Bean/日志新增 | scope | 无业务DI面，不虚构Lombok注入验收 | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 5 | §6/7/12/13/T3-07 | JDK/JDBC/Spring/原ledger | 产线3paths | 不新utility/deps，nonce/hash已有机制 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/12/13/T3-07 | 原codec/Outcome/Audit闭合JSON | SQL/回归 | 不改JSONschema/codec，generic成本真实保留 | typed回归 | Step1 | PASS |
| Rule 7 | §6/7/12/13/T3-07 | 无config/flag/profile | README/scope | 不自动注册IDW执行 | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 9 | §6/7/12/13/T3-07 | 原Projection/transactionparticipant/commonparser | Store/SQL | wholeTX先inv后action，lateoldinv-only分支不覆盖current | 原子/故障 | Step1 | PASS |
| Rule 10 | §6/7/12/13/T3-07 | Instant/Clock/mono/MICROS/DBtime | 所有写路径/tests | begin fullcall/原短窗；fact10s/interrupt恢复；timestamp≤DBnow | 时间/late | Step1 | PASS |
| Rule 11 | §6/7/12/13/T3-07 | 原AR persistence/冻结V13 | 九paths | IDW only，不新增架构/权限/SQL/native/settlement/Recorder | scope/旧回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | prep firstexpiry/dispatchNotAfter/callDeadline | Occurred instant | Instant | UTC instant | Internal | Not applicable: 原权限期限 | Not applicable: 无UI | 新attempt只unclaimed未来actionexpiry；新UUID不延firstexpiry，originalprep已含action/run/toolTimeout/cap边界；DISPATCHaudit覆盖完整callDeadline | 原Clock/DBtime/MICROS | 不min Java lease初次hint，不改旧public/RO时限 | Step 1；TEST-001/002 |
| TIME-002 | DispatchWindow/写时刻 | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对事实 | Not applicable: 无UI | 原wall/mono begin≤10s不reset；begin/complete输入MICROS精确且≤DBnow；begin>=expected.updated且<action.expiry；complete>=updated，不限oldexpiry；maxrevision拒绝 | DBclock/MICROS，query5s/TX10s | 即时FK双写顺序/commitACK后才返回attempt，非执行动作 | Step 1；TEST-002 时钟/rollback/ACK |
| TIME-003 | completion/cleanup技术fact window | Duration | Duration | Not persisted | Internal | Not applicable: 技术elapsed | Not applicable: 无UI | 独立10s wall+mono，不查current权/live/oldexpiry；private fact helper清Thread.interrupted再DB，finally恢复，无新IO/sideeffect | 原Clock/LongSupplier/DBclock | prepare/begin仍拒绝中断；RO不变 | Step 1；TEST-002 late/interrupt |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java | 新focused Store/Invocation双表IDW行为先RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionWriteSqlTest.java | 低层Action CAS必须先RED且无mid-update fullJOIN | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | 同源Action锁与两个无read CAS参与者 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | IDW准入/开始/完成双表编排与共同parser复用 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | 新package IDW入口与DTO/验证/原窗口/事实TX保护 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 原optin真实SQL场景源码扩展，默认只compile/skip | Step1 |
| MODIFY | README.md | 说明generic IDW journal原子事实与未接写执行 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-idempotent-write-journal.md | 实际执行证据两矩阵与局部追踪 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-05-41-idempotent-write-journal.md | Review执行细化，记录root gates/RED-only GO与机械delta | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

基线c155273已提交且clean，SpecSHA078e0fc999e6531783e134bd880675aad5ce90d7b801d1d1da972732f5370b80已核；writer18md24refs/executing15md19refs资源核通过。root全文审阅原404行、strict及执行资源preflight通过，于2026-11-01 05:49 Asia/Shanghai发RED-only source GO；这是现有exact用户授权下的内部实施门禁，不是Spec Accepted或虚构新的用户批准。本轮ownership/pattern/capacity文档澄清由root复核delta。§8既有selector均实际存在，新2focused Test按scope创建。只有§5九paths，旧publicprepare/readonlyguard/metadata与原SQL不可变；V13 SHA=f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3。无Recorder/native/AAconsume/私密终态outcome replay/config/publicAPI/迁移/结算或其他模块修改。

精确Store package API：WritePreparationResultVO prepareOrLoadIdempotentWrite(PreparationRequestVO request)；WriteDispatchBeginResultVO beginIdempotentWriteDispatch(request,RecorderPreparationVO expected,ToolExecutionGrant.EvidenceVO evidence,ToolExecutionAuthorization.CheckResult checked,ReadOnlyDecisionToolTransport.VerifiedExecutor executor,String nonceSha256,ToolExecutionOutcome notDispatchedOutcome,DispatchWindowVO window)；RecordedResultVO completeIdempotentWrite(request,DispatchAttemptVO attempt,String plainNonceHex,ToolExecutionOutcome outcome)；Optional<RecorderPreparationVO> closePreparedWriteFailure(request,nullable RecorderPreparationVO expected,ToolExecutionOutcome notDispatchedOutcome)。全为IDW/action完全重建验证；拒绝READ_ONLY/APPROVAL_REQUIRED，无新public入口。

新nested immutable/redacted WritePreparationResultVO(nullable RecorderPreparationVO invocation,PreparedActionResult action,nullable AgentBudgetReservation budget)，action必有，inv/budget同presence，无隐藏outcome/nonce。WriteDispatchBeginResultVO(RecorderPreparationVO invocation,PreparedActionResult action,nullable DispatchAttemptVO attempt)，仅首committer attempt非null。复用原metadata/attempt/result，不新传输DTO。plain nonce只有caller局部，begin只接canonical SHA；complete重算decoded canonical32byte nonce的SHA验证，DBhash不是凭据。

Projection加IDEMPOTENT_WRITE_METADATA，沿原prepareInternal一次typed PREPAREcheck/E1/ownedTX live当前run/E2完全相等。action当前unclaimed PREPARED且expiry未来才可给新hostUUID/当前run创建预算+inv，包括同owner不同run；firstorigin/firstexpiry不变。PREPARED非独占firstattempt permit，不锁旧origin run、不改旧attempt。advanced/wait/closed/expired action只有metadata不新inv/budget；exact自身已有advanced inv可返metadata+budget。旧public LEGACY仍existingaction facts-only；原RO行为不改。保留newlyCreated budget/orphan拒绝、immutable replaydeadline/audit/history与source校验。

begin fresh DISPATCH请求.actionIntentId必须exact action.intentId（RO仍null）；callDeadline=min(原prep.dispatchNotAfter,run.deadline,E1expiry,executor expiry)，prep已含actionexpiry。原短DispatchWindow=min(entry+10s,callDeadline)wall+mono不reset；audit非null/ALLOW、原requester/type/grant/checked时刻精确，expiry覆盖整callDeadline；RPC在TX外。TX锁序currentrun→currentcap→action FOR UPDATE→owninv/budget，核expected/source/policy/实际DBlease/cancel/deadline/E2fullequality。不将旧claim leasehint当当前DB值。

Globalaction PREPARED/unclaimed且owninv PREPARED的CASwinner先UPDATE inv DISPATCHING，再UPDATE action DISPATCHING（V13即刻backlink FK），epoch1/hash/actual SERVICEexecutor/links/audit/DBtime一致、effectUNKNOWN；只有确认COMMIT的winner获attempt。两更新完成后才fresh fullActionJOIN；中间participant不read，任一更新/trigger失败整TXrollback。action已advanced且owninv仍PREPARED时只关此inv为FAILED_KNOWN/ACTION_NOT_DISPATCHABLE，保留winning action和老attempt；ownadvanced只metadata，无新attempt/no redrive UNKNOWN/APPLIED/terminal。

notDispatchedOutcome必须FAILED_KNOWN/emptyoutput/nullreceipt；begin的code EXACT ACTION_NOT_DISPATCHABLE，仅loser时persist、winner不存该失败。cleanup code必须原八allowlist(CANCELLED,DEADLINE_EXCEEDED,CREDENTIAL_UNAVAILABLE,RELEASE_UNAVAILABLE,RELEASE_CHANGED,AUTHORIZATION_DENIED,AUTHORIZATION_UNAVAILABLE、DISPATCH_NOT_SENT)+ACTION_NOT_DISPATCHABLE。generic actualCost可null/非零且如实保留，任何新loser/cleanup都不能硬编码0；future fixedSTART Recorder才会explicit提供known0。每个freshattempt通过原reserveBudget计自身TOOL调用与active capacity；loserclose不是refund，没有新增settle/refund/counter释放调用，不触旧run counters。保留原SETTLED预算cost>estimate parser guard，budgetService超额结果stateUNKNOWN已有路径；IDW outcome事实可actual>estimate，不能因此丢失已发生事实，测试含该fact+budgetUNKNOWN。

Action lowerparticipants：Row lockForWrite(ToolActionRequest,ToolDescriptor,Instant deadline)复用现lockedfreshJOIN与sameAction；void beginWrite(Row expected,DispatchVO,Instant dispatchedAt,Instant deadline)；void completeWrite(Row expected,ToolExecutionOutcome,Instant finishedAt,Instant deadline)。IDWonly、exacttenant/type/issuer/id/fullrequest/toolref/risk/revision/epoch0或original linkedtuple CAS，更新影响恰1否则冲突；begin/complete无read。所有输入时刻MICROS exact且≤currentDBnow，begin>=expected.updated且<action.expires，complete>=updated、旧expiry不再live核；revision MAX拒绝。

completion仅identity run→action→owninv/budget锁；原noncepreimage/dispatch epoch/executor/audit/caphistory/fullowner/actionrequest/toolref/backend/parameters/backlink认证，不查currentPDP/livelease/runstatus/capcurrentepoch/grant/oldexpiry。独立10s技术fact window，private bounded helper在DB前清Thread.interrupted()并finally恢复，确保Action primary/history parser可工作；不新IO。prepare/begin仍拒中断。若current fullvalid action.dispatch exact原tuple，inv+action原子完成后freshJOIN；SUCCEEDED→action APPLIED/CONFIRMED+receipt，REJECTED/FAILED_KNOWN→NONE，UNKNOWN→UNKNOWN，各typed outcome原域验证。

若完整有效current action已指向不同positive generation/nonce/run/inv，不能覆盖它，也不能因该未来pointer变化丢弃真实旧事实：只认证并完成原owninv，保current action，再fresh currentActionJOIN/返回owninv事实。原fullactionowner/request仍须相同；action unlinked/epoch0却owninv已dispatch为不一致而拒绝。本步没有创建新generation或redrive API，fixture仅证明兼容V13真实历史。same已知exactoutcome幂等，different冲突；UNKNOWN可由同原nonce收敛known，不重派。已知body不通过prepare/begin或新terminal read重放；complete返回是caller持原nonce并提供outcome的事实确认。

cleanup只原identity，无currentPDP/live门禁；action先锁以保持相同顺序，只关自身PREPARED inv且不改action；expected可null处理ACK缺失，strict fullrequest/原leaseidentity仍匹配。advanced只metadata保留，不改已知outcome或付费事实；无settlement。现完整ActionRow/StoredInvocationRow共享parser继续工作，不新增第二审批/预算/快照parser。

Plan Clarification PLAN-CLAR-001：新测试的programming-invalid输入（risk、非法failure code、VO不变量）按既有invalid()风格断言IllegalArgumentException；当前授权、租约、evidence不可用和幂等冲突仍为AgentLedgerException。GREEN阶段只修正新测试原先误期待LedgerException的精确类型，不改产线异常类型、不放宽为任意RuntimeException；最初真实RED计数仍按原日志保留。

Plan Clarification PLAN-CLAR-002：原readonly complete约12行original attempt/nonce/epoch/executor/audit/caphistory证明逐字抽至同文件requireAttempt供IDW复用。原readonly调用该helper，字段/比较/失败分支均不变；这是既有proof复用，不增加类型、路径或行为，旧27项源代码不修改并继续回归。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 提交通用IDW action准入与原子journal事实转换
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交c155273完整Action历史、原Invocation/RO/typedcheck/cap/budget/冻结V13。
- Baseline state: 原publicprepare existingaction facts-only，RO dispatch/complete已存在，IDW尚无package atomic双表动作。
- Observable outcome: 合法多attempt准入、唯一actionwinner、同pointer原子pair/真实旧inv-only迟到完成，generic成本不猜零。
- End state: Store/SQL-only IDW journal可供后续Recorder消费，无实际业务dispatch/native/AA/settlement/private terminal replay。
- Test-first gate: Required — 新2focused Test编译声明后有效RED→GREEN，旧27不改，fixture/编译不作RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java`
- Purpose: 新focused Store/Invocation双表IDW行为先RED。
- Symbols: ToolWriteInvocationStoreTest.java；§6本角色精确API/字段。
- Repository evidence: 原InvocationStoreTest/ActionStoreTest/Recorder受控JDBC模式；不修改原27测试。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: 最小package Write DTO/方法声明供编译，测试新增IDW projection/begin/complete/cleanup。
- Input/output and state mapping: 同unclaimed action多UUID/新run准入；onlywinner attempt；loser自身inv关闭；atomicpair rollback/lateoldfact/nonce/成本/interrupt。
- Error and edge behavior: 旧public/RO兼容、AA拒绝、no private replay/settlement；orphan budget不retrofit，generic nonzero/null/overestimate事实与budgetUNKNOWN。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
@Test idempotent_write_journal_is_atomic_and_only_one_action_claim_wins() {
  prepareFreshOwnAttemptsAcrossRunsWithoutRebindingOriginOrExtendingExpiry(); assertExactlyOneBeginAttemptAndNoOlderAttemptMutation();
  assertInvocationThenActionCommitOrBothRollback(); assertLoserClosesOnlyOwnPreparedInvocationWithSuppliedFailureCost();
  assertEveryFreshAttemptCountsItsOwnToolCallAndActiveCapacityAndLoserCloseDoesNotRefundOrTouchOlderRunCounters();
  assertOriginalNonceLateCompletionUnknownRefinementAndFutureCurrentPointerPreservation(); assertInterruptedFactRestoresFlagAndNoSettlement();
}
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionWriteSqlTest.java`
- Purpose: 低层Action CAS必须先RED且无mid-update fullJOIN。
- Symbols: ToolActionWriteSqlTest.java；§6本角色精确API/字段。
- Repository evidence: 现ActionSql完整Row/parser/ownedtx/primary guards，V13immediate backlink。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: lockForWrite:Row、beginWrite/completeWrite:void；精确§6。
- Input/output and state mapping: IDW/fullowner/request/ref/revision/epoch/tuple CAS，timestamps MICROS exact≤DBnow，maxrevision拒绝。
- Error and edge behavior: 错误owner/输入/nonce/generation/过期begin或时间未来拒绝；complete旧expiry忽略；CAS返回不主动read。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
@Test action_participants_compare_the_exact_locked_request_and_dispatch_generation() {
  row = actionSql.lockForWrite(action,descriptor,deadline); assertExactOwnerAndFreshJoinedRowAfterActionLock();
  assertBeginAndCompleteUpdateOnlyWithRevisionIdentityEpochNonceExecutorPredicates(); assertNoPostUpdateJoinedReadInsideParticipants();
  assertMicrosExactNotFutureMonotonicTimestampsAndMaxRevisionRejection(); assertIdempotentWriteOnlyAndNoCurrentAuthorityForCompletion();
}
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`
- Purpose: 同源Action锁与两个无read CAS参与者。
- Symbols: ToolActionIntentSql.java；§6本角色精确API/字段。
- Repository evidence: 原fullRow15/lockedfreshJOIN/sameAction/primary queryTimeout5。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: Row lockForWrite(action,descriptor,deadline)；void beginWrite(Row,DispatchVO,InstantdispatchedAt,Instantdeadline)；void completeWrite(Row,Outcome,InstantfinishedAt,Instantdeadline)。
- Input/output and state mapping: IDWonly/fullaction一致；begin PREPARED/epoch0→DISPATCHING/UNKNOWNeffect；completion映射SUCCEEDED→APPLIED/CONFIRMEDreceipt等。
- Error and edge behavior: fullowner/request/ref/risk/revision/原dispatch tuple predicates；maxrevision/time拒绝；原public/历史parser不弱化，CAS不read。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
Row lockForWrite(action,descriptor,deadline) { requireIdempotentWrite(); row = existingExactOwnerLockedFreshJoinedRead(); requireSameAction(row,action); return row; }
void beginWrite(expected,dispatch,dispatchedAt,deadline) { requireMicrosExactNotFutureTimeAndPreparedRevision(); updateActionWithExactCasToDispatchingWithoutRead(); }
void completeWrite(expected,outcome,finishedAt,deadline) { validateExistingOutcomeAndOriginalLinkedCas(); updateMappedActionOutcomeWithoutReadOrLiveExpiryCheck(); }
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`
- Purpose: IDW准入/开始/完成双表编排与共同parser复用。
- Symbols: ToolInvocationSql.java；§6本角色精确API/字段。
- Repository evidence: 原Projection/prepare/read/common StoredInvocationRow、run/cap/inv/budget与原Action participant。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: Projection.IDEMPOTENT_WRITE_METADATA；package IDW SQL配合Store§6方法，复用现metadata/attempt/result。
- Input/output and state mapping: run→cap→action→owninv/budget，先inv后action开始；完成根据current action exacttuple paired或authenticoldinv-only，再freshJOIN。
- Error and edge behavior: 不锁old origin run/不改旧attempt/不再派UNKNOWN；无自动settlement/AA/transport，通用费用不猜0，保留预算SETTLED>estimate原guard。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
prepare(request,projection) { preserveOriginalTypedPrepareGate(); allowNewBudgetAndInvocationForIdwOnlyWhenActionUnclaimedPreparedAndFutureExpiry(); preserveLegacyFactsOnly(); }
beginIdw(...) { lockCurrentRunCapActionOwnInvocationBudget(); verifyCurrentAuditAndBounds(); updateInvocationFirstThenActionOnWinner(); closeOnlyOwnPreparedLoserWithSuppliedFailure(); returnCommittedMetadataAttempt(); }
completeIdw(...) { lockIdentityRunActionOwnInvocationBudget(); authenticateOriginalNonceAttemptAndHistory(); updateInvocationAndMatchingActionOrOnlyAuthenticOldInvocation(); freshJoinedActionAgreementAfterAllWrites(); }
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`
- Purpose: 新package IDW入口与DTO/验证/原窗口/事实TX保护。
- Symbols: ToolInvocationStore.java；§6本角色精确API/字段。
- Repository evidence: 原prepareInternal/typedPREPARE/DispatchWindow/nonceSHA/transact与RO methods。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: §6四精确package方法、WritePreparationResultVO/WriteDispatchBeginResultVO；不加public body API。
- Input/output and state mapping: IDW/action完整重建；prepare沿原E1/PDP/E2；beginCheck actionIntentId准确/fullcallDeadline；fact completion/cleanup独立10s并清恢复interrupt。
- Error and edge behavior: no ambient/unverified；non-dispatch outcome strictFAILED_KNOWN/empty/nullreceipt/allowlistederror但nullable/nonzero费用保留；旧RO零费规则不动。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
WritePreparationResultVO prepareOrLoadIdempotentWrite(request) { requireIdwActionReconstruction(); return prepareInternal(request,IDEMPOTENT_WRITE_METADATA).metadataOnly(); }
WriteDispatchBeginResultVO beginIdempotentWriteDispatch(...,String nonceSha,ToolExecutionOutcome notDispatchedOutcome,DispatchWindowVO window) { validateFreshExactDispatchActionIdAndFullCallDeadline(); returnOwnBoundedTransactionAck(); }
RecordedResultVO completeIdempotentWrite(request,attempt,plainNonceHex,outcome) { authenticateNoncePreimage(); return interruptRestoringFactTransaction(() -> sql.completeIdw(...)); }
Optional<RecorderPreparationVO> closePreparedWriteFailure(request,expected,notDispatchedOutcome) { validateAllowlistedFailureWithOriginalCost(); returnInterruptRestoringOwnAttemptCleanup(); }
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`
- Purpose: 原optin真实SQL场景源码扩展，默认只compile/skip。
- Symbols: ToolExecutionJournalPostgresTest.java；§6本角色精确API/字段。
- Repository evidence: 现显式URL/vector前置与旧journal fixtures，用户不自动启动DB。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: 新增same-file IDW helper，20contenders/triggerrollback/latefact；不新PGclass/infra。
- Input/output and state mapping: newrun同action多attempt仅一begin许可；inv→action immediateFK与atomiccompletiontrigger、未来pointeroldfact保留。
- Error and edge behavior: 无URL不运行、不启DB；assertbudget/counters不因loser或cleanup擅自settle，lateafterrunterminal/capreissue。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```java
optInIdwHelpers() {
  createFreshControlledRunAndTwentyOwnAttemptsForOneAction(); assertOneCommittedDispatchAttemptWithMatchingInvocationActionTuple();
  forceBeginOrCompletionTriggerFailureAndAssertAtomicRollback(); assertLateAuthenticFactAndFutureCurrentPointerPreserved();
  keepExistingExplicitUrlVectorPreconditionAndDefaultSkipWithoutStartingPostgres();
}
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 7 — `MODIFY README.md`
- Purpose: 说明generic IDW journal原子事实与未接写执行。
- Symbols: README.md；§6本角色精确API/字段。
- Repository evidence: 现历史/readonly/START library说明。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: 只文档；旧publicprepare与readonly语义保持。
- Input/output and state mapping: PREPARED非独占准入、多attempt先存预算；onlybeginwinner；samepointer双表、oldfact仅本inv；generic费用。
- Error and edge behavior: 不声称Recorder/native/AAconsume/配置/settlement/private terminal replay或真实PG已交付。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```text
Document generic IDEMPOTENT_WRITE package journal admission, action claim and atomic same-pointer completion.
Document metadata-only replay, preserved future action pointer and caller-supplied failure cost with no automatic settlement.
Keep Recorder/native/approval consumption/private terminal read/configuration and real-runtime acceptance explicitly pending.
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-idempotent-write-journal.md`
- Purpose: 实际执行证据两矩阵与局部追踪。
- Symbols: 2026-11-01-idempotent-write-journal.md；§6本角色精确API/字段。
- Repository evidence: 原evidence格式；本次lock阶段全PENDING。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: 5aliases/17MC/10Rules、实际RED/GREEN/count/skip/commands/source范围。
- Input/output and state mapping: 独立事实记录，PG缺optin编译skip，scope9path/V13freeze与no businessdispatch。
- Error and edge behavior: 不预填实施PASS或猜计数；fixture/stub错误分开，完整T3-07Partial。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```text
At lock record pending implementation and test conclusions without fabricating PASS or test counts.
After actual logs evaluate seventeen MC and ten literal Rules independently for generic cost, atomic writes, nonce history and compatibility.
Trace five local aliases and preserve unimplemented Recorder/native/approval/settlement and real database boundaries.
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

#### File 9 — `CREATE docs/egon/plan/2026-11-01-05-41-idempotent-write-journal.md`
- Purpose: Review执行细化，记录root完整审阅/strict/resources后的RED-only source GO。
- Symbols: 2026-11-01-05-41-idempotent-write-journal.md；§6本角色精确API/字段。
- Repository evidence: 原Spec/已提交c155273 source与root最终合同。
- Dependencies and consumers: 本Step相邻participants/typed域值与原budget/codec；后续Recorder未接。
- Why now: 先本组件RED，再依赖顺序实现/回归；最小类型声明仅供编译，不先实现行为。
- Contract/signature changes: Review、legacyexact用户授权，单Step/commit，九路径file markers，实际RED-only GO已记录。
- Input/output and state mapping: 原接口保持；新package API/paired-current vs late-oldonly/state/cost/时钟/失败/验证闭合。
- Error and edge behavior: 禁止写production先于rootGO，不虚构Accepted/实现结果或扩paths。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；以上本角色exact验证/成本/时间/事务与§6执行，N/A见§12。
- Implementation pseudocode:
```text
Lock baseline c155273, fresh Spec hash, exact nine paths and the final idempotent-write package APIs.
Validate action/invocation ordering, supplied generic failure outcomes, interrupt restoration and authentic old-generation completion.
Run strict Plan validation after the ownership/pattern/capacity delta and preserve Review with the actual 05:49 RED-only GO record.
```
- Verification contribution: TEST-001/002/003；实际计数和失败分类只由执行后日志填写，非真实PG/权限验收。
- After this file: 对应RED→GREEN/兼容或文档门禁闭合后继续，无外部业务调用或配置启用。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=ToolWriteInvocationStoreTest,ToolActionWriteSqlTest,ToolInvocationStoreTest,ToolActionIntentStoreTest,ToolInvocationRecorderTest,ToolExecutionJsonCodecTest,ToolCapabilityStoreTest,AgentBudgetLockOrderingTest,ToolExecutionJournalPostgresTest,ToolExecutionStorageVerifierTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 新2组件有效RED后所选10类GREEN，原PG类缺optin仅编译skip；原public/RO/成本guard兼容，实际count由日志填写。
- Failure returns to: fixture/compile回新focused tests；CAS/成本/顺序/late指针/interrupt回对应SQL/Store；需Recorder/AA/config/SQL/权限扩展回root不自行增加。
- Completion criteria: 五别名/17MC/10Rules实际证据、九路径/独审无阻断、exact回归通过，无弱fixture/未定scratch。
- Rollback: 仅九路径回退/forward-fix，保留现SQL/journal/已存真实事实；无repair/数据删除或运行重派。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionWriteSqlTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java、README.md、docs/egon/evidence/2026-11-01-idempotent-write-journal.md、docs/egon/plan/2026-11-01-05-41-idempotent-write-journal.md
- Commit: `feat(agent): commit idempotent write journal transitions`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 focused新2Tests→完整selector | 新IDW projection/claim/CAS/fact/cleanup | 真行为RED→GREEN，stub/fixture/compile分类，旧27不改 | root Maven only |
| TEST-002 atomic/history/cost | tianji-service | 新2focused/旧RO/Action/预算回归 | 多run/UUID准入、唯一winner、inv先action/故障rollback、未来pointer旧fact、原nonce/UNKNOWN、interrupt恢复、非零/null/超estimate成本 | metadata不泄体，action不被loser/oldfact/cleanup覆盖，无settlement/猜零 | fake JDBC/时钟/事务；非真实业务/PG |
| TEST-003 static/compat | tianji-service | diffcheck/9paths/V13sha/原codec/cap/verifier/publicRO回归 | 原接口/guards/cost规则、无新consumer | 无Recorder/native/AA/config/publicAPI/SQL/依赖变化 | 原SETTLED guard保留 |
| TEST-004 optinPG | tianji-service | 同原ToolExecutionJournalPostgresTest显式URL/vector前置 | 20contenders/triggeratomicrollback/latefact源码 | 缺前置compile/skip，不开DB，不声称真实SQL/并发通过 | PG runtime unverified |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-05-41-idempotent-write-journal.md --strict` | header/files/trace/MC | exit0；Review及实际RED-only GO记录，非实现PASS | docowner |

## 9. Migration, Compatibility, Rollout, and Rollback
无SQL/迁移/新配置/API/权限/依赖，V13 immutable。原publicprepare facts-only、readonly guards/zero-cost与预算SETTLED guard保持；新方法package IDWonly，generic成本如实，无settlement或业务调用。未来Recorder/START known0/AAconsume/native接线需后续单独实现，不将metadata当许可。原optinPG缺前置skip；不自动服务/DB/GUI/浏览器/容器。回滚只九路径，保留历史/receipt/SQL，无repair/重派。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/12 | Step 1 | IDW Projection/prepare DTO | TEST-001/002 | 多合法attempt非独占，firstorigin/expiry不变，publiclegacy保持 |
| PLAN-REQ-002 | §7/12/13 | Step 1 | Store.begin/InvocationSql/Action.beginWrite | TEST-002/004 | freshactionID audit/完整call期限、onlywinner/loser只owninv |
| PLAN-REQ-003 | §7/12 | Step 1 | complete/nonce/Action.completeWrite | TEST-002/004 | 相同currenttuple双表原子；真实oldinv-only不覆盖未来pointer |
| PLAN-REQ-004 | §13 | Step 1 | suppliedfailure/cleanup/genericOutcome/budgethistory | TEST-002/003 | null/nonzero/overestimate truth，无猜零/settle |
| PLAN-REQ-005 | §6/17 | Step 1 | 新focused/PG/原回归/docs | TEST-003/004/005 | 九paths/旧RO与public保持/真实边界 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | PREPARED被误当独占firstattempt | root最终：当前合法新run/UUID可准入，begin actionCAS才唯一claim，不动旧attempt/旧origin | root | Closed：§6Projection与winner/loser合同 |
| DEC-002 | generic费用被伪造0/超过estimate丢事实 | caller明确notDispatchedOutcome，nullable/nonzero保留，事实可超estimate；原预算guard不删除 | root | Closed：新增成本/UNKNOWNbudget回归，无settlement |
| DEC-003 | 晚到nonce与未来actionpointer | only原proof认证，同tuplepair或只旧inv，current必须fullvalid positive；无新generation API | root | Closed：§6branch与fixture证明，不redrive |
| DEC-004 | 中断/时间/FK部分失败 | fact helper清恢复interrupt；MICROS≤DBnow；先inv后action、pair后freshJOIN、TXrollback | root | Closed：旧live只begin，completion独立技术窗 |
| RISK-001 | 实际业务/PG/权限消费者未验收 | Store/SQL-only，原PGoptin默认skip | 用户/root | Tracked：完整T3-07/平台Partial，Recorder/AA/native后续 |

## 12. Review and Acceptance
本Review记录root最终闭合IDW合同及05:49全文/strict/resources后的RED-only source GO，非Spec Accepted或新的用户批准。九路径、单逻辑Step/commit、新focused真RED与旧接口边界明确；现Unit of Work/participant/codec/parser复用，成本/历史/当前权职责分开。下面是计划可执行性检查，不是实施PASS；报告at-lock实际结论仍全部PENDING，本轮文档delta由root复核。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR persistence Store/SQL与九路径 | 无新层/模块/Recorder/native/config/SQL变更 | None |
| MC-REUSE-001 | Applicable | PASS | 原prepareInternal/commonRow/parser/ActionRow/ledger budget/cap/current typedcheck | 沿现participant与parser扩展，无第二执行/认证/预算平台 | None |
| MC-DEP-001 | Applicable | PASS | 既有JDK/Spring/Jackson/JDBC/Clock | 无新依赖/工具框架 | None |
| MC-NAME-001 | Applicable | PASS | WritePreparationResultVO/WriteDispatchBeginResultVO/现metadata/attempt/Result | 包内immutable值角色明确，plaintextnonce只caller局部 | None |
| MC-VALID-001 | Applicable | PASS | IDW/fullaction/evidence/audit/window/nonce/history/actionbacklink/CAS | current begin与historical completion分离，不把PREPARED当独占permit | None |
| MC-MODEL-001 | Applicable | PASS | 现records/新metadata-onlyVO、nullable inv+budget同presence | 无私密terminaloutcome偷渡、无新实体/Mapper副本 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed域值/codec/metadata投影直接复用 | 无Converter层新增 | None |
| MC-LOG-001 | Not applicable | N/A | 技术manual Store/SQL，无业务日志Service新增 | 安全固定错误/redacted，不打印nonce/结果 | None |
| MC-BEAN-001 | Not applicable | N/A | 无新Spring Bean/DI/config/public入口 | 现manual技术构造与helpers保持 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK noncehash/codec/participant/事务 | 无utility/线程/认证框架或新增scratch | None |
| MC-JSON-001 | Applicable | PASS | 原closed codec/typedOutcome/audit，不更schema | 结果cost nullable/nonzero如实，旧RO约束保持 | None |
| MC-TIME-001 | Applicable | PASS | §4.9 fullcall/shortbegin/fact10s/MICROS≤DBnow/interrupt | 迟到事实独立于live/oldexpiry，begin旧窗口不reset | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/flag修改 | 包内IDW库未自动派发或消费 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Unit of Work由TransactionTemplate+transaction participants/CAS实现，原Projection/commonRow复用 | 处理atomic journal的竞争/部分失败，比分散更新安全，比新增Handler/Stateengine更小，无新pattern类 | None |
| MC-SCOPE-001 | Applicable | PASS | 九明确paths；IDWonly/AA与原RO/public不变 | 无settlement/费用猜0/ASAPI/native/migration扩大 | None |
| MC-TEST-001 | Applicable | PASS | 新focused2Test真实RED、旧回归、原PG helper optin默认skip | metadata/claim/atomic/late/成本/interrupt/未来pointer均有计划证据；实际结果evidence待填 | None |
| MC-BLOCKER-001 | Applicable | PASS | root最终API/cost/interrupt/latepointer/time合同闭合，基线/hash与原稿全文gates已核 | Review及实际RED-only GO已记录；当前机械delta供root复核，非代码/运行验收 | None |

### 12.6 Final verdict
PASS — Ready for user review
