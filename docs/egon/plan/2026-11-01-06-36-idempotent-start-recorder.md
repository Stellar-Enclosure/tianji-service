# T3-07 固定BPMN START的显式Recorder

| Field | Value |
| --- | --- |
| Document | `2026-11-01-06-36-idempotent-start-recorder.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 06:36 Asia/Shanghai` |
| Updated | `2026-11-01 06:46 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | 原Recorder显式固定内部START/2VO/测试；7路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@7c8a5e9c37693ce0d54ae7b8aa576aa6a5d01cdf`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint，用户原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `1d3e717f18919c4bfd78b9a014e0bb97329079abec32807a473d751403424be5` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [generic IDW journal](2026-11-01-05-41-idempotent-write-journal.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
仅扩现ToolInvocationRecorder Facade：显式固定内部nonbillable BPMN START，经原generic IDW Store与trustedSTART callback先持久begin、一次operation、先事实complete再bestEffort ownknown0 account。新2VO明确prep action快照与自身attempt事实；无Store provenance就只metadata、不合成outcome/结算。旧RO result/方法/错误不变。七路径单Step/commit，不接native/config/AA/MCP或更SQL/公共业务端点。

## 2. Target Spec and Effective Design
原蓝图§7短事务/nonce/迟到事实、§12稳定intent/receipt、§13当前权/预算与T3-07为唯一effective设计，root最终结果/race/映射合同闭合。原IDW Store已提交，本步只显式Facade协调；metadata不是许可/私密body或freshclose证明。用户exact授权延续，文档Review，root全文/strict/resources前不sourceGO，不伪造Accepted或平台完成。

## 3. Effective Requirements and Acceptance
PLAN-REQ是原要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | fixed START入口与真实结果语义闭合 | IDW/STARTresource/requiredinstanceId/est0、2VO明确snapshot/ownfact，旧ROreceiptNull不变 | Recorder/新Test |
| PLAN-REQ-002 | §7/12/13 | 当前DISPATCH与nonce commit后仅winner消费一次 | actualSERVICE、E1/check fullcall/E2、hash-onlynonce/beginACK、postcommitwindow、once mapping | Recorder/Test |
| PLAN-REQ-003 | §7/12 | 有效ACK先记事实，失败/late/结算不擦除已知结果 | 本noncecomplete后才可返回outcome/account；unknown/invalid/denied映射、outerpostfail抑制body/advanced不覆盖 | Recorder/Test/PGhelper |
| PLAN-REQ-004 | §13预算与隐私 | metadata race不捏造0或私密body，账务保守真实 | !started/early/outercleanup无outcome/noaccount、fee.03反证、advanced无resettle，只有freshnoncecomplete own0bestEffort | Recorder/Test |
| PLAN-REQ-005 | §6/17 | 最小兼容与范围/验证 | Rig只1行visibility/原17不动、旧RO回归、原PGoptin skip、7paths无consumer/schema/依赖扩大 | 所有路径/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
一行Rig可见性作为compile prerequisite；最小新方法/2VO声明供新Test编译后真实RED，再原Recorder最小GREEN；PGhelper/docs/旧相关回归。既有RO/private helpers仅复用已中性部分，不策略化重写，不提交scaffold。
### 4.2 Test-first strategy
新Test基于Rig(customStartDescriptor)复用Store/SQL/controlledJDBC，结算repo stub仅在新Test局部覆盖。先正反VO/profile/once/commit-before-account/failure/late/pending/fee.03 race真实RED；错误分类区分fixture/compiler与直接stub。原17断言/方法不变，不新testkit。
### 4.3 Sequential and parallel boundaries
root独占ToolInvocationRecorder.java与README两路径；budget_lock_tests独占新ToolIdempotentStartRecorderTest及旧ToolWriteInvocationStoreTest的一行Rig visibility两路径；journal_design独占PGhelper一条路径；docowner仅2docs，共7paths。sourceGO后root先最小新public方法/2VO编译声明，budget写真测试并由root观察有效RED后root才实现功能GREEN。PG helper可在GO且旧类型/新声明可编译后并行，root统一Maven时所有owner冻结；本Step提交前不并行下一Step。
### 4.4 Commit boundaries
七paths一个逻辑commit：`feat(agent): record fixed BPMN start tool calls`。显式stage、不push/改历史/启动环境。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Recorder新方法/2VO | T3-07已有STARTport+IDWjournal需协调 | 旧RO RecordResult严格receiptNull，IDW complete仅自身事实 | 扩现Facade，独立小writeVO，不放宽RO/新Recorder类 | Implement |
| metadata conservative race | §7/13真实事实/费用 | Store begin/cleanup无newlyClosed标志、只metadata | !started/cleanup一律无body/noaccount，不扩StoreSQL | Implement |
| transaction/accounting协调 | §7/13 | 原nonce/window/bookkeeping/account/IDW methods | 复用Facade+UnitofWork+Adapter，无Strategy/新引擎 | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 2VO/profile/test seam | PLAN-REQ-001/005 | 新Test closed值/旧RO断言 | Rig1行/新声明 | 明确snapshot+ownattempt值 | Recorder方法 | Step 1 |
| dispatch/fact/account | PLAN-REQ-002/003 | once/映射/late/故障RED | 原Store/STARTport/中性helpers | 显式内部START事实 | 后续配置/native另步 | Step 1 |
| race/privacy/兼容 | PLAN-REQ-004/005 | sameUUID fee.03/advanced/cleanup | 上项/原预算/旧RO | 无虚假0/outcome/resettle的保守结果 | 用户审查 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | 原AR Recorder Facade/IDW UnitofWork/START Adapter | 原边界不变 | 仅Recorder扩展，不动StoreSQL/域/ports/provider | Step1；MC-ARCH-001、MC-REUSE-001 |
| Validation/model | 原immutable typed值/RO result/descriptor | 2VO不可变、snapshot非global current | canonicalID/closed state/time/positiveepochbody/strictreceipt，no provenance不捏造 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time | manual技术库/原codec/Clock/mono | 无Bean/config/新格式 | 新START safe诊断/checkStart，旧RO消息不改；原typedOutcome复用 | Step1；MC-BEAN-001、MC-JSON-001、MC-TIME-001 |
| Pattern | 现Recorder Facade+Store UnitofWork+TransportAdapter | 复用具体协调变化点 | 先beginCommit/once再factCommit/account，比分离调用安全，不加Strategy/新抽象类 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| journal/nonce/权限 | 原IDW Store/完整Row/typedCheck/cap | 已有atomicbegin/complete/cleanup，freshnonce历史proof | 足够，不改StoreSQL | reuse | None | Step1；MC-REUSE-001 |
| START/结果 | FixedBpmnStartToolTransport/StartObservation/ProcessClient | 9Failure/stage/receipt/instance输出已定义 | 只缺Facade及小VO | reuse | None | Step1；MC-DEP-001 |
| time/accounting | 原nonce/window/account/settledZero/bookkeeping/earlier | 只account适用于明确known0 | 仅freshnonce路径复用；其余metadata保守 | reuse | None | Step1；MC-TIME-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/12/13/T3-07 | 2VO/explicit snapshot field名/新方法 | fixture prerequisite→test→Recorder | 命名区分prep snapshot与ownfact，START诊断独立 | 编译/source | Step1 | PASS |
| Rule 2 | §6/7/12/13/T3-07 | profile/typed check/E1E2/receipt/VO/current vsmetadata | 新test/Recorder | exactIDW/known0范围、无provenance时不自造outcome/account | 正反/race | Step1 | PASS |
| Rule 3 | §6/7/12/13/T3-07 | true immutable records/原typed值 | Recorder2VO | closed scalar/time/state/positiveepoch outcome，旧ROResult不放宽 | 构造/值测试 | Step1 | PASS |
| Rule 4 | §6/7/12/13/T3-07 | manual技术Facade，无新Bean/日志 | scope | 保既有构造/无业务DI伪称，固定safe错误 | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 5 | §6/7/12/13/T3-07 | JDK/现helpers/Store/ports | Recorder/test | 不新utility/依赖/线程引擎 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/12/13/T3-07 | 原Observation/Outcome/schema/codec | Recorder/回归 | strict onlyinstanceId+hostreceipt，仅新writeVO允许receipt | typed回归 | Step1 | PASS |
| Rule 7 | §6/7/12/13/T3-07 | 无config/profile/flag | README/scope | 无自动注册/native/权限启用 | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 9 | §6/7/12/13/T3-07 | 现Facade+UnitofWork+Adapter | Recorder | 先commitbegin再once、先factcommit再ownaccount，metadata不伪fact | 顺序/故障 | Step1 | PASS |
| Rule 10 | §6/7/12/13/T3-07 | Instant/Clock/mono/原deadline | Recorder/tests | checkStart独立kind、原entrywindow不reset，lateack facts先存 | 时间/late | Step1 | PASS |
| Rule 11 | §6/7/12/13/T3-07 | 原AR Recorder与7paths | 全scope | 不动StoreSQL/domain/port/provider/schema/deps/APIactivation | scope/旧RO | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | transport deadline/callDeadline/beginWindow | Occurred instant | Instant | UTC instant | Internal | Not applicable: 原绝对期限 | Not applicable: 无UI | Transport.Request.deadline EXACT原prepdeadline；call=minprep/run/executor/E1；本phase originalwall+mono10s不reset；audit覆盖整call | 原Clock/LongSupplier/DBtime，MICROS由原Store处理 | checkStart独立FixedSTART CANCELLED/DEADLINE，旧RO check及错误不变 | Step 1；TEST-001/002 |
| TIME-002 | write attempt dispatched/finished/late fact | Occurred instant | Instant | UTC instant | Internal | Not applicable: 原事实 | Not applicable: 无UI | snapshot只prep时一次；beginCommit后窗口耗尽也以本nonce记knownNoSend(falseinvoked)；latevalidACK先complete，outererror不降级；account独立后续事务 | 原Store boundedfact/interruptbookkeeping复用 | 不合成postcomplete actionstate/revision或当前全局事实 | Step 1；TEST-002 late/expiry/account |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java | 仅开放Rig类型包可见以复用测试seam | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java | 固定START Recorder choreography与race保守结果先真实RED | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java | 在现Recorder Facade增加显式固定内部START方法及独立write VO | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 原optin同文件追加fixed Recorder整合helper源码 | Step1 |
| MODIFY | README.md | 说明explicit固定START Recorder与保守metadata/accounting限制 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-06-36-idempotent-start-recorder.md | 严格7路径执行细化，供root全文/strict/resources后sourceGO | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-idempotent-start-recorder.md | PENDING骨架及实际执行后证据矩阵 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline7c8a5e9clean/SpecSHA1d3e717f18919c4bfd78b9a014e0bb97329079abec32807a473d751403424be5已核；writer18md24refs/executing15md19refs PASS，§8所有原selector实际存在，新Test按scope创建。严格7paths，无Store/SQL/domain/port/provider/contracts/config/native/AA/MCP/schema/deps/权限/factprefix/private终态重读/settlementAPI改动；V13固定f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3。

执行门禁事实：root已全文审阅本计划原362行（GO前SHA-256 f4887967bd1e9683e40dbf849fd07bf179d070481a9c47f8c895dcbccfc5fff7），strict与executing资源15 Markdown/19 refs及step-start checklist通过，于2026-11-01 06:45 Asia/Shanghai发RED-only source GO。原7c8a5e9基线与新计划在声明前已核；root仅加新方法UnsupportedOperationException编译stub及2个redacted无guard VO声明，功能实现须等待有效RED。这是原exact用户授权下的内部门禁，不是Spec Accepted或新增用户批准；实际实施/测试矩阵仍PENDING。

public IdempotentStartResultVO executeIdempotentStart(FixedBpmnStartToolTransport transport,PreparationRequestVO request)新增于原Recorder，原ctors/ownverifyStorage/affinities保持。入口仅IDEMPOTENT_WRITE、action与intentExpiry非null、estimatedCost exact0、ProcessClient.START_RESOURCE_KEY、descriptor output exact required {instanceId:STRING}；完整request由原VO/Store重建。noambient，Recorder自身verified。已知0仅固定内部START，不改genericStore费用/原RO规则。

新public immutable/redacted IdempotentStartResultVO(String actionIntentId,String actionStateAtPreparation,long actionRevisionAtPreparation,nullable WriteAttemptResultVO ownAttempt)：actionIntentId canonicalUUID，state只IDW7state PREPARED/DISPATCHING/APPLIED/REJECTED/FAILED_KNOWN/UNKNOWN/EXPIRED，revision>=1。三个action字段从prepare actionmetadata捕获ONCE，名称明确snapshot，不根据complete/ownrev合成globalAPPLIED/新currentrevision。

WriteAttemptResultVO(String runId,UUID invocationId,String state,long revision,long dispatchEpoch,Instant dispatchedAt,Instant finishedAt,nullable ToolExecutionOutcome outcome,boolean operationInvokedThisCall,existing AccountingState accountingState)。沿现metadata校验runId/epoch/time，state限PREPARED/DISPATCHING/SUCCEEDED/REJECTED/FAILED_KNOWN/UNKNOWN、revision>=1；epoch0无dispatched，positiveepoch有dispatched，终态/finish及先后关系闭合。operationInvokedThisCall只表示本call进入OneShot.start、非HTTPsent，true须positiveepoch；outcome非null也必须positiveepoch且status=state/knowninternalcost exact0。SUCCEEDED outcome必须通过strict StartObservation hostreceipt pattern和onlyvalidinstanceId output；非success outputempty/receiptNULL。epoch0 metadata永远outcomeNULL，不从outcomeNULL推费用。新writeVO IAE诊断不得说Read-only；原RO RecordResult全部guards/receiptNULL/zero/原消息保持。

prepareOrLoadIdempotentWrite后捕获snapshot。无owninv只snapshot+null，无Transport/account/body。ownadvanced/nonPREPARED只自身metadata/outcomeNULL，accounting仅由prepare已观测budget：SETTLED且各actual0才SETTLED，否则PENDING，不Transport/resettle/redrive。ownPREPARED但globalaction非PREPARED时，Store.close suppliedFAILED_KNOWN/ACTION_NOT_DISPATCHABLE/0后也只closedmetadata/outcomeNULL/PENDING，不settle、不把supplied0声称为所读存储body。

两者PREPARED才构造FixedSTART Request（run/lease/descriptor/action/hostUUID、deadline EXACT originalprepdeadline）进入trusted callback。实际executor必须nonLEGACY SERVICE、same tenant、operation非null；constructor元数据本身不认证。fresh E1 exactbinding→DISPATCH CheckResult，Request包括稳定intent、descriptor.parametersSHA与完整callDeadline=minprep/run/executor/E1；original entrywall+mono10s窗口不重启；ALLOW audit非null、echo/boundary/decision/originalsubject/grant/versions/checkedAt/expiry均沿原RO强度，StoreE2再核。mint32byte SecureRandom nonce只local，begin只传SHA与known0 loser Outcome，commitACK后才可能消费一次。

**无provenance则保守**：现Store begin/cleanup没有newlyClosed标志。所有begin !started只metadata/outcomeNULL、NOaccount；accounting仅从prepare已见budget settledZero判SETTLED，否则PENDING。不能用state/revision猜本call刚写0，不能重放原body。race反证：generic caller在prepare后把同UUID以fee.03关闭，当前begin返回FAILED_KNOWN metadata也不能自造0Outcome或settle0。early/globaladvanced close及outercleanup同理。其预算可留RESERVED/PENDING，run finish后可UNKNOWN；本步不声称即时资源/账务闭环，不扩Store/SQL以修provenance。

只有begin.started且持本call nonce，postcommit checkStart通过后调用operation.start一次，进入前标operationInvokedThisCall=true。若ACK后本地窗口先耗尽、operation还未进入，则用本noncecomplete FAILED_KNOWN/DISPATCH_NOT_SENT/0，invokedfalse。正常observation→SUCCEEDED/onlyinstanceId/receipt=hostref/0，经descriptor验证；OPERATION_DENIED→REJECTED/DOWNSTREAM_PERMISSION_DENIED/0；OPERATION_NOT_SENT及operation内部CANCELLED/DEADLINE→FAILED_KNOWN/DISPATCH_NOT_SENT/0（实际Adapter保证preSDK）；OPERATION_INVALID_RESPONSE→UNKNOWN/OUTCOME_INVALID_RESPONSE/0；OPERATION_RESULT_UNKNOWN/unexpectedRuntime或op内意外credential/releasekind→UNKNOWN/OUTCOME_UNKNOWN/0，emptyoutput/nullreceipt。原Adapter阶段合同不改。

completeIdempotentWrite原nonce事实先确认COMMIT，只有此fresh确认可把outcome放新VO并独立调用现account()bestEffort own0；PENDING不rollback/downgrade已知事实。advanced/!started/early/outercleanup绝不调用account。known0是本固定port合同，不代表generic provider费用为0。prepare的snapshot字段仍原值，返回只owninv事实不合成全局完成态。

Outer FixedSTART Failure保kind并换安全固定值；LedgerException保code/STARTsafe message，未知Runtime→dependencyunavailable。precallback cleanup调用Store.close ownPREPARED suppliedknown0，code为CANCELLED/DEADLINE/CREDENTIAL/RELEASE_CHANGED/RELEASE_UNAVAILABLE或AUTHORIZATION_DENIED/UNAVAILABLE，其他DISPATCH_NOT_SENT；bookkeeping清/恢复interrupt。cleanup返回metadata不证明newlyClosed，因此NOaccount/NOsettle；若确有closedprepared metadata则加sanitized accounting-pending suppressed，不更body/费用，Optional.empty不虚构欠费。advanced保留，lateACK callback内已完成事实不因outerpostcheck错误降级，仍抛safeFailure抑制返回body。

复用中性nonce/window/account/settledZero/bookkeeping/earlier。旧check(window)抛RO Failure，必须另private checkStart使用相同verified/Clock/mono/interruption谓词与FixedSTART CANCELLED/DEADLINE，newStart unavailable/invalid诊断安全；旧check/error/messages不改。模式仅现Recorder Facade+Store UnitofWork+TransportAdapter，no Strategy/新抽象/engine。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 显式记录固定内部START自身nonce事实并保守返回metadata
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交7c8a5e9 IDWjournal、固定START port/transport、现RO Recorder。
- Baseline state: 原Recorder仅RO/receiptNull，IDW Store可atomicjournal；没有fixedSTART Recorder协调或新2VO。
- Observable outcome: 本noncewinner先commit再once/complete后account，其他metadata不捏造body/0或重结算，prep snapshot语义真实。
- End state: explicit fixedSTART Recorder库可用；无native/config/AA/MCP/私密终态重放或genericStore变更。
- Test-first gate: Required — Rig仅编译前置，最小新声明后新focused真正RED再GREEN；旧17/RO断言保持，fixture/compiler不算RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java`
- Purpose: 仅开放Rig类型包可见以复用测试seam。
- Symbols: ToolWriteInvocationStoreTest.java；§6该角色精确合同。
- Repository evidence: 当前private static final class Rig；ctors/现methods可供同包复用。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: 一行private static final class Rig→static final class Rig，无原17断言/import/方法更改。
- Input/output and state mapping: 新Test以Rig(customStartDescriptor)复用真实Store/Sql/受控JDBC；结算stub只在新Test局部覆盖。
- Error and edge behavior: 这是compile prerequisite不是新行为RED；不增加testkit/更旧用例或fixture逻辑。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```java
// One-line visibility prerequisite for the new same-package recorder test.
static final class Rig implements AutoCloseable {
  keepAllExistingFieldsConstructorsHelpersAndSeventeenAssertionsUnchanged();
}
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java`
- Purpose: 固定START Recorder choreography与race保守结果先真实RED。
- Symbols: ToolIdempotentStartRecorderTest.java；§6该角色精确合同。
- Repository evidence: 原RO RecorderTest+刚开放Rig/custom descriptor、controlled FixedSTART callback。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: 最小public新方法/2VO声明供编译后新增正反/故障真实RED。
- Input/output and state mapping: profile/VO/once/commit-first/receipt/映射/accounting/outerlate，actionAtPreparation快照不合成globalstate。
- Error and edge behavior: generic同UUID并发关闭fee.03反证：!started只metadata/nulloutcome/noaccount；前置cleanup保留PENDING、旧RO不改。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```java
@Test fixed_start_records_only_this_nonce_authenticated_attempt_and_keeps_preparation_snapshot() {
  assertEntryProfileAndClosedResultVoContracts(); assertCommitBeginBeforeExactlyOneStartAndCommitOutcomeBeforeOwnZeroAccount();
  assertAllFailureMappingsUnknownLateAckAndAccountingPendingPreserveFacts(); assertOldAdvancedNoTransportNoBodyNoResettle();
  raceGenericCallerClosingSameUuidWithFee003AfterPreparation(); assertNoSyntheticZeroOutcomeOrZeroSettlementFromMetadata();
}
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`
- Purpose: 在现Recorder Facade增加显式固定内部START方法及独立write VO。
- Symbols: ToolInvocationRecorder.java；§6该角色精确合同。
- Repository evidence: 原nonce/window/account/settledZero/bookkeeping/earlier、RO RecordResult/guard不可放宽。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: public executeIdempotentStart(FixedBpmnStartToolTransport,PreparationRequestVO):IdempotentStartResultVO；2新public immutable VO及private checkStart/startsafe helpers，精确§6。
- Input/output and state mapping: 捕获3个actionprep snapshot字段一次；只有started+本noncefreshcomplete允许positiveepochoutcome并bestEffort account0；其他只metadata。
- Error and edge behavior: 不凭state/revision推新close provenance；no account early/outercleanup/!started；receipt非NULL仅新writeVO；原RO check/error/messages/receiptNull零费不改。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```java
IdempotentStartResultVO executeIdempotentStart(transport,request) {
  validateFixedStartIdwZeroProfileNoAmbientAndVerifiedRoots(); prepared = preparations.prepareOrLoadIdempotentWrite(request); snapshot = captureThreeActionMetadataFieldsOnce(prepared.action());
  if (noOwnInvocationOrAdvanced) returnSnapshotAndMetadataOnlyWithoutTransportOrAccount();
  if (globalActionNotPrepared) returnEarlyCloseMetadataNullOutcomePendingWithoutAccount();
  return transport.withVerifiedCall(exactOriginalDeadlineRequest, (executor,operation) -> freshStartDispatchAndRecordWithThisNonceOnly(snapshot,executor,operation));
}
checkStart(window) { preserveOriginalPredicatesWithFixedStartCancelledDeadlineKindsAndNewStartSafeDiagnostics(); }
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`
- Purpose: 原optin同文件追加fixed Recorder整合helper源码。
- Symbols: ToolExecutionJournalPostgresTest.java；§6该角色精确合同。
- Repository evidence: 现原PG URL/pinnedvector/Timeout120及IDW/RO helpers，用户不自动开DB。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: 仅sameclass Recorder helper，transport为controlled callback/ACK，不涉及IAM/HTTP。
- Input/output and state mapping: fresh run/cap/claim/request使用新Recorder记录receipt+ownaccount；replaymetadata/no transport；outerpostfail事实保留/结算故障Pending。
- Error and edge behavior: 缺原optin编译skip；不声称真实SQL/IAM/网络运行；genericStore费用合同不改。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```java
optInFixedStartRecorderHelper() {
  useFreshRunAndExistingIdwJournalWithControlledServiceStartCallbackOnly(); assertReceiptFactBeforeOwnAccountingAndNoSecondStartOnReplay();
  assertOuterPostCallbackFailureCannotDowngradeCommittedFactAndAccountingFailureStaysPending();
  preserveExplicitUrlVectorAndTimeoutPreconditionsAndDefaultSkipWithoutStartingDatabaseOrHttp();
}
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 5 — `MODIFY README.md`
- Purpose: 说明explicit固定START Recorder与保守metadata/accounting限制。
- Symbols: README.md；§6该角色精确合同。
- Repository evidence: 现START library/IDW journal/RO说明。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: 只文档，无配置/Bean/native/approval开放。
- Input/output and state mapping: actionAtPreparation是快照、ownAttempt是自身事实；仅本noncefreshcomplete可返回outcome/settleknown0；race cleanup可留RESERVED/PENDING。
- Error and edge behavior: 不合成globalAPPLIED/revision、无私密terminalreplay/费用猜0、PG/crypto/真实model尚未验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```text
Document explicit fixed internal nonbillable START recorder composition without enabling native or Spring consumers.
Distinguish action preparation snapshots from own-attempt facts and prohibit inferred global completed state.
Document metadata-only losers/preflight cleanup, no settlement without fresh nonce completion, and possible RESERVED/PENDING until later run finish.
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 6 — `CREATE docs/egon/plan/2026-11-01-06-36-idempotent-start-recorder.md`
- Purpose: 严格7路径执行细化，供root全文/strict/resources后sourceGO。
- Symbols: 2026-11-01-06-36-idempotent-start-recorder.md；§6该角色精确合同。
- Repository evidence: 原Spec/已提交7c8a5e9/currentRecorder/STARTport/IDW result合同。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: Review legacyexact授权、单Step/commit、test-first/5aliases/17MC10Rules。
- Input/output and state mapping: 最终race裁决优先：无Store/SQL扩展、不从metadata推close provenance；旧RO不变。
- Error and edge behavior: 没有新APIendpoint/config/schema/settlementAPI/factprefix；不得rootGO前source编辑。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```text
Lock baseline7c8a5e9 and fresh Spec hash with seven declared paths before source work.
Check the two closed value contracts, fresh-nonce-only outcomes, conservative metadata races and old read-only compatibility.
Run strict Plan validation and hand off for root full review and resource gate without fabricating Accepted or source GO.
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

#### File 7 — `CREATE docs/egon/evidence/2026-11-01-idempotent-start-recorder.md`
- Purpose: PENDING骨架及实际执行后证据矩阵。
- Symbols: 2026-11-01-idempotent-start-recorder.md；§6该角色精确合同。
- Repository evidence: 现subsystem evidence格式。
- Dependencies and consumers: 原Recorder/IDW Store/typedSTART port/ledger/test Rig；后续配置/native不在本步。
- Why now: 按compile prerequisite→focused真实RED→最小实现/兼容/文档顺序，root全文门禁前不source。
- Contract/signature changes: 5aliases/17MC/10Rules与真实日志/计数/范围；lock阶段PENDING。
- Input/output and state mapping: 组件有效RED→GREEN/原RO回归/PGoptin编译skip，只有固定内部START声明known0。
- Error and edge behavior: 不预填通过/crypto/PG/模型/即时结算闭环，不把metadata当存储body证明。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术Facade/immutable值复用，无业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实本角色snapshot/freshnonce/race/费用/时间/安全/兼容职责，N/A见§12。
- Implementation pseudocode:
```text
At lock retain pending implementation and validation conclusions with no guessed counts.
After actual logs assess seventeen MC and ten literal Rules independently and record nonce/receipt/accounting/race evidence.
Trace five local aliases while preserving full T3-07 Partial and real-runtime and unimplemented consumer boundaries.
```
- Verification contribution: TEST-001/002/003；实际计数/错误类型由日志填，不以编译/skip推实际运行。
- After this file: 对应编译前置/有效RED→GREEN/旧回归或文档门禁后推进，不自动启用消费者。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolWriteInvocationStoreTest,ToolInvocationStoreTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest,ToolExecutionJsonCodecTest,ToolExecutionStorageVerifierTest,ToolExecutionJournalPostgresTest,FixedBpmnStartToolTransportTest,TianjiFixedBpmnStartToolTransportTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 新Test有效RED→11类选择GREEN，原PG缺optin编译skip；旧RO/Store17保持、fee.03 race无fakebody/settle、once/late/known0范围通过；实际count以日志为准。
- Failure returns to: fixture/compiler回新Test；VO/映射/race/时间/费用回Recorder；如需StoreSQL/newAPI/config扩展回root，不自行增加路径。
- Completion criteria: 五别名/17MC/10Rule逐项证据、7scope/Rig仅1行、旧RO保持、独审无阻断与选择回归完成。
- Rollback: 仅7paths回退/forwardfix，保留既有SQL/journal/receipt事实，不repair/重派/删除数据。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java、README.md、docs/egon/plan/2026-11-01-06-36-idempotent-start-recorder.md、docs/egon/evidence/2026-11-01-idempotent-start-recorder.md
- Commit: `feat(agent): record fixed BPMN start tool calls`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7新focused→完整selector | 新方法/2VO/profile/snapshot/映射/once/race缺失行为 | 真RED→GREEN，stub/fixture/compiler分类；Rig1行非新行为RED | root Maven only |
| TEST-002 choreography/race | tianji-service | 新Recorder Test/旧RO与IDW/START回归 | freshcurrentproof/commitNonce/once/factBeforeAccount/late/pending、oldadvanced无resettle、fee.03反证 | 只freshnoncecomplete可body/account，metadata不作provenance；snapshot不造globalstate | actual Store/SQL +controlledJDBC/transport，非IAM/HTTP |
| TEST-003 scope/compat | tianji-service | diffcheck/7paths/V13sha/原17+RO/ports回归 | 不改StoreSQL/domain/port/provider/schema/config/费用API、旧receiptNull与错误保持 | 无native/AA/MCP/private knownbody重放/新factprefix | 非运行证明 |
| TEST-004 optinPG | tianji-service | 原ToolExecutionJournalPostgresTest helper | 固定Recorder与已有journal/own0account/外层抑制事实 | 缺原URL/vector前置只compile/skip，不开DB，无IAM/HTTP | PG runtime unverified |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-06-36-idempotent-start-recorder.md --strict` | header/files/trace/MC | exit0，Review供root全文/资源门禁，非实现PASS | docowner |

## 9. Migration, Compatibility, Rollout, and Rollback
无schema/SQL/新config/flag/Bean/native/APIendpoint/权限/依赖或settlementAPI更改，V13 immutable。旧RO RecordResult严格receiptNull/zero与原check/messages不放宽；新writeVO独立。genericStore费用契约原样，known0只固定内部START且只有本noncefreshcomplete可account；race cleanup留pending是明确限制，不宣称即时账务闭环。库不自动运行，AA/native配置/MCP另步；optinPG缺前置skip不启动任何服务/DB/GUI/浏览器。回滚7paths保留历史/journal，不repair/重派。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | Step 1 | executeIdempotentStart/2VO/Test | TEST-001/002 | fixedprofile/明确snapshot/ownfact，旧RO不变 |
| PLAN-REQ-002 | §7/12/13 | Step 1 | freshcheckStart/begin/nonce/once | TEST-002 | current proof/fullcall/E2、onlywinner且postcommit窗口 |
| PLAN-REQ-003 | §7/12 | Step 1 | mapping/complete/account/outererror | TEST-002/004 | fresh事实先commit，own0account失败不降级，late抑制无body |
| PLAN-REQ-004 | §13 | Step 1 | metadata/race/noaccount paths | TEST-002 | fee.03反证，无provenance不造0/私密body或resettle |
| PLAN-REQ-005 | §6/17 | Step 1 | 7files/旧回归/PG/docs | TEST-003/004/005 | 严格scope/原17与RO保持，真实边界明确 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | snapshot被错当postcomplete全局state | action三字段只capture一次，ownattempt事实独立，不合成APPLIED/revision | root | Closed：2VO字段名/校验与newTest |
| DEC-002 | metadata无法证明本call关闭0fee | Store无newlyClosed，!started/early/outercleanup一律无outcome/noaccount，fee.03并发反证 | root | Closed：不扩StoreSQL；预算可RESERVED/PENDING，runfinish后UNKNOWN，非即时资源闭环 |
| DEC-003 | RO结果/错误被write放宽 | 独立writeVO/checkStart/新诊断，复用中性helpers | root | Closed：旧receiptNull/zero/messages/contracts不变 |
| RISK-001 | 真实环境/消费者未验收 | explicitlibrary、controlledtransport/JDBC、PGoptin默认skip | 用户/root | Tracked：完整T3-07/平台Partial，AA/native/MCP配置后续 |

## 12. Review and Acceptance
最终root合同含保守race裁决，7paths、单Step/commit、新focused真实RED与原Rig仅1行/RO不变明确。模式复用现Facade/UnitofWork/Adapter，只有本noncefreshcomplete可暴露outcome并own0account；缺provenance不捏造。计划Review不伪Accepted，root全文/strict/resources后才sourceGO；实际证据lock时全部PENDING。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR persistence Recorder Facade、7paths | 不改Store/SQL/域/port/provider/config/native/其他模块 | None |
| MC-REUSE-001 | Applicable | PASS | 原Recorder nonce/window/account/settledZero/bookkeeping/earlier+IDW Store+FixedSTART Adapter | 扩现Facade/UnitofWork，不造Strategy/第二执行/认证/预算层 | None |
| MC-DEP-001 | Applicable | PASS | 原JDK/Spring/codec/typed ports/ledger | 无新依赖/工具/线程框架 | None |
| MC-NAME-001 | Applicable | PASS | IdempotentStartResultVO/WriteAttemptResultVO、actionStateAtPreparation等明确snapshot名 | public不可变真值，newSTART诊断不称Read-only，值脱敏 | None |
| MC-VALID-001 | Applicable | PASS | IDW/profile/action/expiry/est0/STARTresource/outputschema、E1/check/E2/fullcall/receipt/VO | freshnonce事实与metadata保守分离，不从revision猜body/0cost | None |
| MC-MODEL-001 | Applicable | PASS | 两closed immutable records/原typedOutcome与metadata复用 | snapshot与nullable ownAttempt无synthetic globalstate；outcome仅positiveepoch | None |
| MC-CONVERT-001 | Not applicable | N/A | 直接复用原typed domain/port/Store结果及固定Outcome语义映射 | 无实体/DTO Converter或Mapper新增 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Facade，无新业务日志Service | 固定安全START错误/脱敏，不泄nonce/参数/receipt/body | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config新面 | 原构造/verifiedStorage/affinities保持，不新增provider | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK SecureRandom/时间/现helpers与Store | 无新utility/harness/认证或调度平台 | None |
| MC-JSON-001 | Applicable | PASS | 原fixedport observation/ToolExecutionOutcome/descriptor schema不变 | 仅输出严格instanceId/hostreceipt，原codec/ROreceiptNull不改 | None |
| MC-TIME-001 | Applicable | PASS | §4.9 exactprepdeadline/fullcall/min/原wallmono/checkStart/latefact | 不重启beginwindow，postcommit窗口耗尽completeNoSend，outer拒绝不降级已存事实 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/flags/native/MCP/AA配置或权限更改 | explicit库不自动激活写执行 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Recorder Facade+Store UnitofWork+TransportAdapter | 具体receipt/会计协调复用，无Strategy/State/新pattern类 | None |
| MC-SCOPE-001 | Applicable | PASS | 7paths且Rig仅一行visibility，旧RO/StoreSQL/contracts未改 | 无private terminalbody replay/AA/新factprefix或settlementAPI | None |
| MC-TEST-001 | Applicable | PASS | 新Recorder Test有效RED、generic fee.03 race/once/late/pending/VO与旧回归、PGoptin默认skip | 实际计数执行后填写；controlled transport不声称IAM/HTTP/真实PG | None |
| MC-BLOCKER-001 | Applicable | PASS | root最终2VO/错误映射/metadata provenance race裁决闭合，基线/hash/路径已核 | 无未决合同或依赖；代码尚未授权先行，运行边界显式 | None |

### 12.6 Final verdict
PASS — Ready for user review
