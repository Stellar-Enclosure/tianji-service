# 原生只读与固定流程启动工具统一受控执行

| Field | Value |
| --- | --- |
| Document | `2026-11-01-09-05-governed-native-process-tools.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 09:05 Asia/Shanghai` |
| Updated | `2026-11-01 09:58 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR governed native RO+fixedSTART union core；13paths/单Step |
| Source Requirement | 原蓝图继续未完工作并分步提交；§7.2/11.3/12/13统一工具治理与真实副作用 |
| Baseline Revision | `main@574aa3da76f612a109fcb9cf77c7839cc9d2c0cc`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact用户执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `6f59b61ad9c183a7f3fb37c193798ef22b6f9b359946bd6641bc4da5064ecba7` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [已提交事实观察及deadlinefence](2026-11-01-08-51-committed-effect-deadline-fence.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
单个native core Step把完整snapshot工具集统一交给common bridge/一个Plugin入口，复用旧RO与fixedSTART/当前授权结果访问Recorders。START业务context只由host解析，native entry统一计额，实际HTTP前再fence；业务effect以已提交observer为准，与accounting分开，scope冻结后所有终态与deadline currentgeneration协调。十三路径一个commit，不加AS/config/schema/依赖、第二loop或AA/MCP。

## 2. Target Spec and Effective Design
蓝图§7.2要求claim fencing、终态一次提交及副作用账本真实，§11.3模型只业务args/host固定intent/相同intent返回原结果，§12/13要求薄原生Adapter、全预算与每执行边界当前权。现RO native seam、fixedSTART/Access库、Step18 observer/fenceddeadline已完成。本步将它们在AR programmatic native core连成一个可验证行为；旧RO/factory/defaultoff/privacy/CODE_FIRST守卫保持，AS接线及真实环境另验。Review沿原exact用户授权，不虚构Accepted；root全文/strict/resources后才sourceGO。

## 3. Effective Requirements and Acceptance
PLAN-REQ为原要求局部trace aliases，不新造业务需求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | 同一原生loop用完整当前snapshot/hostbindings暴露受控RO与固定START | 一visible/一hostresolve/exactset+names、identity/privacy/CODE_FIRST/hooks守卫；oldRO API/spy兼容 | common/decl/RO/Process/Plugin/tests |
| PLAN-REQ-002 | §11.3/13 | 模型只业务args，稳定intent由可信host业务上下文决定 | snapshot.businessKey唯一来源、hostversion/TTL canonical，DBnow一次/µexpiry不clamp；排除nativecallID/runfallback | Process/BridgeTest/Loop |
| PLAN-REQ-003 | §11.3/12/13.3 | 每合法native入口计额，执行前fence且原结果访问当前权 | onecounter含invalid/replay/meta/accessfail无refund；exacttool/context/session再fence；freshSTART/authorizedV14knownresult、SETTLED才model | Plugin/Process/nativeLoop |
| PLAN-REQ-004 | §7.2/13.4 | 终态effect反映已提交businessfact并与当前generation协调 | observerknown不因accountPending降级；UNKNOWN业务支配；seal→closefreeze→effect→终态；deadlinefenced/race旧generation不能写新 | Process/Executor/Loop |
| PLAN-REQ-005 | §17/T3-07 | 旧构造/defaultoff/RO/privacy与真实证据保持 | 精确13paths/23SQL/无依赖ASconfigAA-MCP；组件有效RED后11类回归，环境未验收诚实 | all/tests/README/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
最小common SAM/nestedtypes、sharedDeclaration/newbridge构造与UOE方法供编译；RO.RunScope只先implements commonScope及public声明提升，新增5arg仅UOE stub，保旧4arg执行不变以兼容oldLoop直接传oldRunScope给Plugin。RO/common兼容测试先有效RED→最小extract/adapter GREEN；Process测试独立RED→bridge GREEN；实际nativeLoop测试RED后才改Plugin/Executor行为。完整实现顺序按下面File blocks；compile-only声明可在所属生产file先作一次，不提交scaffold，不先写功能。ROadapter与Process各自GREEN后，为实际Loop测试编译可先做root最小protocol plumbing：Supplier/field/getter与Scope类型widen common、bind→bindScope、typed event/Phase/nullableepoch机械映射，Plugin execute暂用5arg签名（必要的临时NOOP Runnable只为编译，不提交最终）。这时不得先加sharedcounter/dynamicfreeze/frozenEffect/newfenceddeadline；Loop有效RED必须来自缺这些core行为，不是类型/UOE fixture。无sourceGO前不写Java/tests。
### 4.2 Test-first strategy
三个独立RED barrier，root各自观察有效缺行为后才该组件GREEN，允许相互独立实现并行。不把编译/fixture失败算RED，直接stub errors如实分类。accountingPending与businessknown/UNKNOWN的反证必须真实测试；oldRO directdeny/schema/spy、native context/session/hiddenhooks/privacy/defaultoff回归不可弱化。
### 4.3 Sequential and parallel boundaries
root owns commonBridge/Plugin/Executor/newLoopTest/AdkRunContextTest/README 6paths；budget_lock_tests owns sharedDeclaration/ROBridge/oldROBridgeTest 3；Process owner owns newProcessBridge/BridgeTest 2；docowner2。互不重叠，owner不是唯一工作者，不回滚其他改动。root统一reactor/Maven前全部Java冻结；本Step提交前不并行下一Step。无需提取旧testkit/新脚本。新增ContextTest仅是既有quota/5arg协议的必要fixture对齐，由root负责；root读本次amended文件块+strict后才编辑，原12paths的GO事实不改。
### 4.4 Commit boundaries
单Step/13paths、`feat(agent): govern native read and process start tools`，全部MC/literalRules实际证据与root审计后显式stage一个commit，无push/历史改写。不是先提交空common层再补native行为。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| common/RO static helper/declaration | §12薄Adapter且原生loop唯一 | 现Plugin/Executor耦合RO类型，RO声明与RunScope可精确复用 | 两实际bridge实现共用窄接口/原RunScope，静态resolvedhelper无第二registry/innerRO | Implement |
| union START/context/access | §11.3 hostintent/原结果/当前权限 | fixedSTART/Access/observer完整库已有，缺native组合 | 一个Process bridge，typedhostresolver只version/TTL；不重写transport/parser/journal | Implement |
| one entrycounter/preHTTP fence | §7/13整个run工具额度及Session边界 | journal预算不能计malformed/meta/privateaccessfail；原Plugin已有admittedContexts | existingPlugin一个线程安全计数器，无第二loop/策略engine | Implement |
| frozen businesseffect/CAS | §7.2迟到事实不改终态且effect指向ledger | Step18 observer先account、newfenced expire已可用 | dynamicScope锁内freeze，原event seal和ServiceCAS，不新表/状态/终态refinement | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| common/RO compatible seam | PLAN-REQ-001 | oldROBridgeTest新增RED | 最小common/shared/RO签名 | static resolvedRunScope/closed declarations/原RunScope实现common | Process/Plugin/Executor | Step 1 |
| Process union/native contexts | PLAN-REQ-002/003/004 | 新BridgeTest RED | typedRecorder/observer/Access/DBnow+上项 | hostcontext/执行/freeze scope | nativeLoop | Step 1 |
| actual native loop integration | PLAN-REQ-003/004/005 | 新LoopTest RED | ROadapter与Process GREEN+最小Supplier/Scope/bind/event协议plumbing、真实ADK fixtures | 全入口quota/fence/effect终态与兼容 | root最终回归/commit | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AR adk技术Adapter/原application ports/persistence；Agent generator excluded | 原结构/JDBC/Flyway保持，无AS/config/schema层变更 | 6prod/4tests/3docs精确范围，23SQL immutable，无CRUD/deps | Step1；MC-ARCH-001/MC-SCOPE-001 |
| Reuse/capability | RO.RunScope/Declaration、Recorders/V14Access/observer/fenceddeadline/nativecontext | 不重建权限/codec/Store/loop | static resolvedhelper+共享声明，sourceSHA由Store/Facade承担 | Step1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation | 原immutable ToolDescriptor/Binding/Outcome，新增hostcontext与typedmetadata | model仅args；exactrefs/hostkey/version/TTL/身份校验 | 真immutable VO，closed schema；不新entity/MapStruct层 | Step1；MC-NAME-001/MC-MODEL-001/MC-VALID-001 |
| Bean/log/JSON/time/config | manual技术桥/原gate、JDK/RxJava/contextAwareIo | no新Bean/日志/config/utility，privacy不降 | safe异常/元数据allowlist，DBTTL/原窗口，sharedschema精确extract | Step1；MC-BEAN-001/MC-JSON-001/MC-TIME-001 |
| Pattern | 现NativeAdapter/Facade/UoW/CAS与scope lifetime | 只有RO/union真实variation point，保单loop | common Bridge Adapter、scope冻结；不引通用Strategy/State/调度引擎 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| nativeRO复用 | 现RO172行与oldspy.bind/RunScope | 声明/readonly执行已有 | 只static subset helper与common seam，one Process registry | reuse | None | Step1；MC-REUSE-001 |
| START/readaccess/effect | Step16–18typedRecorders/Observer/V14/DBdeadlineAPI | 完整当前权与durablefact由既有库持有 | 不越层codec/第二parser/新receipt协议 | reuse | None | Step1；MC-DEP-001 |
| Plugin/Executor | 已admitted identity/session/contextAwareIo/sealed sink/compileRoot | 缺全entrybudget/动态scope与postclaimeffectfence | 最小现有Plugin+Executor扩展，保持autoconfig与guard | reuse | None | Step1；MC-PATTERN-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | common/Process/Declaration实际职责，typed ContextVO/EventVO名清楚 | port/tests先行→6prod | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | 命名/source | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | hostcontext/refs/risk/identity/session/全binding/typedbody/限额fence校验 | Bridge/Plugin/tests | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | 正反/无modelauthority | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | 不可变context/metadata record，原VO/SQL/实体不变 | common/Process/旧domain合同 | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | constructor/旧兼容 | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | manual技术桥/既有Executor，未新增业务Bean/DI/log | scope | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | N/A | Step1 | N/A：无新业务Bean/config面 |
| Rule 5 | §7/11.3/12/13/17 | 原ADK/RxJava/JDK/Recorders/registry/scheduler，无新utility | 6prod | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | imports/POM | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 现closedparametersJsonSchema/typedOutcome，model/event白名单 | Declaration/RO/Process/Executor | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | schema/隐私 | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 原defaultoff/factory与envprivacygate保持，无配置路径 | scope/旧配置回归 | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | N/A | Step1 | N/A：无新业务Bean/config面 |
| Rule 9 | §7/11.3/12/13/17 | Adapter/one-native-loop/原Facade-UoW-CAS与scopefreeze变化点 | common/RO/Process/Plugin/Executor | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | 并发/latefact/race | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | DBnow/DurationTTL/µfloor/绝对窗口/freeze与leasegeneration | Process/Executor/tests | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | 时间/旧generation | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | Agent现结构/JDBC/Flyway/generatorexcluded/原事件路径/23SQL冻结 | 13paths | 本文件块及§6完整hostcontext/quota/fence/业务effect/兼容合同 | scope/bytefreeze | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | 固定intentExpiry（Duration TTL见TIME-004） | Occurred instant | Instant | UTC instant | Internal | Not applicable: DB绝对时刻 | Not applicable: 无UI | bind一次ledger.databaseNow，expiry=floorMICROS(DBnow+positiveTTL<=7d)，future且<=+7d，不clamp runDeadline；每START resolver once | 现DBclock/MICROS，无新Clock框架 | 原run/dispatch/cap窗口继续独立限制实际调用，不借TTL延长 | Step 1；TEST-002/003 |
| TIME-002 | scope admission/observer/freeze/eventseal | Occurred instant | Instant | Not persisted | Internal | Not applicable: 原生生命周期顺序 | Not applicable: 无UI | Recorder前短锁open+entryUNKNOWN后IO解锁，HTTP前fence在锁外再短锁admit；close冻结不等IO，lateobserver不改frozen；accountpending不降已提交known | 既有同步锁/原Clock/Mono窗口不reset | journal迟到facts可留存但不reopen公共events/终态 | Step 1；TEST-002/003 |
| TIME-003 | postclaim deadline/currentgeneration | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: DB harddeadline | Not applicable: 无UI | seal→freeze→effect后newleasefencedexpire；REVconflict reloadterminal，非terminal DBexpired仅oldwideUNKNOWNfallback；preclaim旧逻辑 | Step18 DBclock/现Timestamp映射，oldhint不当当前值 | 不把旧scopeCONFIRMED用于新generation，不终态refinement | Step 1；TEST-003/004 |
| TIME-004 | host intentTtl | Duration | Duration | Not persisted | Internal | Not applicable: 相对TTL | Not applicable: 无UI | positive且<=7days；host-only，bind解析一次，用DBnow计算固定expiry；不默认为run剩余期限 | Duration精度保留到expiry floorMICROS，future校验 | 原run/call窗口独立，无延长harddeadline | Step 1；TEST-002/003 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java | common窄bridge/Scope及typed事件合同供RO与union两实际实现 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolDeclaration.java | 共享现closed primitive native declaration，direct fallback仍deny | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java | 先锁定旧RO包内API/新commonadapter/共享声明兼容 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java | 兼容旧RO API，implements common并提供union已解析subset绑定 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java | 先证明union绑定/可信START上下文/执行与冻结的真实业务边界 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java | 一套union声明执行只读与固定START，可信intent与当前授权原结果访问，锁内冻结业务effect | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java | 实际NativeRunner mixed工具loop先RED，锁定Plugin/Executor计数/冻结/终态协调 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java | 原Plugin统一合法native入口计数与再次执行fence，保持精确context/session/工具身份 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java | 单一cached commonroot，所有终态路径先seal/freeze动态effect，再fenced deadline协调 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java | 仅工具fixture配额2与真实5arg转旧4argmock协议对齐，12项旧断言原样 | Step1 |
| MODIFY | README.md | 说明programmatic governed union与稳定业务context/配额/effect边界，未新增AS启用 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-09-05-governed-native-process-tools.md | 冻结13paths、完整closed合同、组件RED与nativeCore一逻辑Step | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-governed-native-process-tools.md | PENDING骨架与实际source/log逐项证据，不用nativeFakeModel当真实服务验收 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
baseline574aa3dclean、SpecSHA6f59b61ad9c183a7f3fb37c193798ef22b6f9b359946bd6641bc4da5064ecba7与既有selector paths已核。writing18md24refs/executing15md19refs资源通过。Created09:05为root明确请求的草稿时刻；Review沿legacy exact授权，不伪Accepted。无source GO前只本两docs。全部23SQL immutable，无新DDL/依赖/CRUD/MP迁移/codegen日志；Agent excluded，维持现技术JDBC/独立Flyway。

**Common/RO。** public AdkGovernedToolBridge.bindScope(material,lease,ToolEventSink):Scope。nested Scope含declaredTools、execute(BaseTool,Mapargs,UUIDhostId,Stringauthor,RunnablecallFence)、sideEffectOutcome、close；common ToolEventVO字段沿现phase/author/fulltoolref/hostUUID/state/nullableLongdispatchEpoch/accounting/error，Phase STARTED/FINISHED，typed sink.append。STARTED0；freshinvFINISHED实际epoch；获权accessknown3 epochnull（不是新dispatchgeneration）；UNAVAILABLE null或旧0由sink省略。无参数/输出/receipt/nativecallID/nonce/凭据。

旧RO.bind/RunScope/ToolEventVO/Phase包内API不变。bindScope必须delegate oldbind保旧Loop spy.bind并返回同一oldRunScope，不新增wrapper。旧RunScope直接implements commonScope，declaredTools/sideEffectOutcome仅加public；旧4argexecute原样，新增public5argexecute先校验/执行Runnable再调用旧4arg。nullRunnable在events/Recorder前固定safeDep；私有Runtime sanitized、AgentLedger code保留且固定安全消息。oldbind一次原visible/hostBindings后delegate package STATIC bindResolved(recorder,transport,material,lease,oldSink,List<ToolDescriptor>,List<BindingVO>):oldRunScope；helper验证material/exact READ_ONLY subset/backend，完全不再查fullvisible或hostresolver。newProcess直接此statichelper，不建innerRO/第二registry/nullablector。共享package-private AdkGovernedToolDeclaration精确extract现closedprimitive parametersJsonSchema（parameters absent、required/allproperties/additionalPropertiesfalse），runAsync永远safe FEATURE_NOT_AVAILABLE。可包内toGovernedEvent复用转换，不能造第二事件schema/parser。

**Union与可信context。** Process mandatory public ctor(AgentLedgerService ledger,ToolInvocationRecorder recorder,ReadOnlyDecisionToolTransport decisionTransport,FixedBpmnStartToolTransport startTransport,ToolResultAccessRecorder resultAccessRecorder,Collection<ToolDescriptor> descriptors,Function<ExecutionMaterial,List<BindingVO>> hostBindings,StartActionContextResolver resolver)。使用真实usesLedger/recorderaffinity，调用的Recorders/Access API仍独立readiness gate；不新增reflection/verifiedflag/Bean或第二transport。public usesLedger/usesRecorder/usesDecisionTransport/usesStartTransport/usesResultAccessRecorder仅pointer相等。

sole Process ToolRegistry作一次fullvisible(snapshot)与一次fullhostBindings.apply(material)：全set count/exactrun authorization/fulltoolref/backend、无extra/dup、全局native名唯一。READ_ONLY走已解析subset，IDW只ProcessClient.START_RESOURCE_KEY且output exactrequired STRING instanceId/estimate0；AA/otherwrite拒绝，不静默过滤。privacy constructor/bind/entry沿AdkToolTelemetryPrivacy gate，不改env/cachedTracing或GlobalOTel。

nested public functional StartActionContextResolver.resolve(StartActionContextRequestVO(AgentRunAuthorization.Request run,AgentExecutionSnapshot snapshot,ToolDescriptor descriptor)):StartActionContextVO(String businessVersion,Duration intentTtl)。每visible START bind时resolve一次。snapshot.businessKey是唯一canonical业务key，非null长1..255，不trim/truncate或由model/native/run补；version host-owned canonical1..128/controlfree/validUTF8，TTL positive<=7d，无默认resolver/factory；ContextRequestVO含snapshot/input，ContextVO与commonEventVO的toString一律固定redacted。bind一次ledger.databaseNow供全部START固定expiry=floorMICROS(DBnow+TTL)，future且<=+7d，不clamp runDeadline。ToolActionRequest从binding+descriptor+normalizedargs+host业务key/version构造，expiry同run/tool固定；intent跨run/nativecallID稳定，version只是hostintentmetadata，不声称PS业务DB CAS。

**执行与当前原结果访问。** scope.declaredTools为每run新实例，IdentityMap仅exacttool。nullRunnable先于events/Recorder固定safeDep，Runnable privateRuntime sanitized且保AgentLedger code/固定安全消息。entry传入hostUUID由Plugin随机生成，不从nativefunctionCallId取。START用既有Recorder请求action/expiry/est0；START输入/context/STARTED事件成功后，紧邻Recorder调用前在scopeLock内requireopen并将本entry业务标UNKNOWN，随即解锁执行Recorder IO；per-entry map有界且不是第二配额counter。unused声明、quota拒绝、malformed args/invalidcontext以及Recorder前STARTED事件失败保持NONE。包装private oneShot在可能HTTP前先Runnable（scopeLock外，SessionDB可阻塞），再短锁requireopen/admit，解锁后delegate.start IO；wrapper不再首次建立UNKNOWN。只catch fence/admission失败，存original safeRunStop并throw OPERATION_NOT_SENT；绝不能catch delegate.start失败当NOT_SENT。captured fencefailure在Recorderreturn后及bridgecatch优先，outertransportpostcheck包装也不能变可恢复业务负结果。

observer仅actualcommitted status：SUCCEEDED→CONFIRMED，REJECTED/FAILED_KNOWN→NONE，UNKNOWN→UNKNOWN。freshownOutcome校exactrun/hostid/status/descriptor schema/strictportreceipt/cost0/localSETTLED才model envelope；只status+output(instanceId)或closednegative error，无receipt/cost/actionID/bodymetadata。ownAttemptNULL才表示无owninv：scope短锁markUNKNOWN，再同hostUUID fresh V14resultAccess，其observer更新knownbusinessfact；桥校公开run/accessUUID/actionintent/positiveRev/hash64/typedOutcome.validated(descriptor)/localSETTLED，fullref由exactRequest固定，sourceSHA/权由Store/Facade负责，不重算codec/hash/另parser，也无expectedSourceRevision参数。sourcecost null/nonzero保持且不当本地费。ownAttempt非null但outcomeNULL只failclosed，不access（regularinvUUIDcollision），UNKNOWN/inflight不redrive，不从preparation snapshot捏finalglobalactionstate。

**CLAR-003 Recorder前UNKNOWN与freeze-gap。** valid START紧邻Recorder调用前短锁requireopen+markUNKNOWN，填平已存在APPLIED元数据返回与V14access开始之间的freeze空窗；不是等HTTP前才初标记。仅COMMITTED observer可把entry变CONFIRMED/NONE，permission/credential等无observer的preflightfailure保守UNKNOWN，不另推断no-send。HTTPwrapper仍须fence+open/admit且不能catch delegate失败当NOT_SENT；fence先失败但Recorder已COMMIT FAILED_KNOWN并observer在freeze前到达可NONE，否则freeze UNKNOWN后晚NONE不能改。SessionFence阻塞freeze-first测试期望UNKNOWN且delegate0；另测APPLIED metadata返回后阻塞access、freeze UNKNOWN、晚accessobserver不改frozen。无新增状态/API，RO沿原非空NONE，不将RO费用待定混为写业务副作用。

**CLAR-001 业务effect与accounting严格分离。** 已提交observer得到的CONFIRMED/NONE在accountingPENDING、settlementthrow或outerpostcheck失败后仍保持；accountingPENDING只阻model并停止本调用、无retry，不降已知businessfact。业务inflight/尚未认证metadata/原结果尚未获权才UNKNOWN。aggregate任一业务UNKNOWN支配，其次任一CONFIRMED，否则NONE；unused声明START为NONE，旧empty/noscope legacy UNKNOWN保留。freeze发生在observer和account之间必须保known。

| Business fact / observer | Accounting | Model access | Scope business effect |
| --- | --- | --- | --- |
| committed SUCCEEDED | SETTLED | schema通过才output | CONFIRMED |
| committed SUCCEEDED | PENDING/settlethrow | 阻止model，无重派 | CONFIRMED，不降UNKNOWN |
| committed REJECTED/FAILED_KNOWN | SETTLED或PENDING | 仅SETTLED可closednegative | NONE，不因费用待定降级 |
| START已进入Recorder/无observer的preflightfailure/UNKNOWN/未认证advancedmetadata | 任意 | 阻止model，无redrive | UNKNOWN |
| unused START声明/Recorder前quota或invalid/STARTED失败 | 任意 | 无调用 | NONE |
| siblings含CONFIRMED与业务UNKNOWN | 任意 | 每entry独立门禁 | aggregate UNKNOWN支配 |

Scope.close短锁freeze并禁entry/HTTPadmission，不等待IO、不清tracker；lateobserver/privatebookkeeping可留journal但不能改frozenaggregate/重新开event。scopeLock绝不跨PDP/DB/transport IO。STARTED在journal前，FINISHED每entry最终一次（freshown实际meta或authorizedaccess known3/null epoch），不得先metadata再access二次FINISHED。失败UNAVAILABLE不伪造epoch。

**CLAR-004 Loop编译前置不是功能GREEN。** ROadapter与Process分别有效RED→GREEN后，root可仅widen common Supplier/field/getter/Scope、bindScope和eventnullableepoch机械类型映射（包括5arg调用所需临时NOOP fence），不先实现sharedcounter/dynamicfreeze/frozenEffect/fenceddeadline。实际NativeLoop RED须证明这些core缺失，而非Process UOE/类型/fixture失败；之后才给core功能GREEN。oldRunScope implements common、无ambiguous Supplier overload，oldloop directconstructor继续编译。新commonEvent guards可由RO有效RED覆盖或在coreRED后实现。

**Plugin/Executor。** Plugin唯一共享nativeentry counter在既有admittedContexts锁/atomic下；context已admitted/exacttool/session先核，然后consume，必须早于args/events/Store/hostUUID。所有合法native入口RO/write/malformed/replay/metadata/privateaccessfail均计、无refund，同entryaccess不第二计；max来自snapshot.effectivePolicy.maxToolCalls，超额BUDGET_EXCEEDED不碰Store/transport。传Runnable再次复核同context/exacttool/Session，不再计数。保持contextAwareIo、leafcontexts、六canonical LlmAgent callbacks/toolsets/modeldefaults/codeexecutors等hiddenpath拒绝，无第二loop/SpringAIcallback。

Executor同erasure Supplier<? extends AdkGovernedToolBridge>一个cachedroot，oldctors/defaultoff/factorySAM继续，usesReadOnlyDecisionTools只match实际root，不matchunion内部；新增usesGovernedProcessTools同exactroot。CONFIG compileRoot继续用scope exactdecls，CODE_FIRST tools仍closed。每个completion/failure/cancel/idle/deadline/authstop/finishrace一律eventSink.seal→scope.close FREEZE→readeffect→submit；finally幂等close在/先于runnercleanup。单serialized事件sink保text/tool revision互斥，lateevent不得改终态。

postclaim旧388/409/447/472等分支用Step18新fenced expire(actor,lease,query,currentrev,frozenEffect) helper；REVconflict reload terminal就return，非terminal且DBexpired才fallback旧wide UNKNOWN，绝不把oldscope CONFIRMED给newgeneration。preclaim旧240/247/263/276不变；noscope/legacy UNKNOWN语义保持。无AS配置、默认resolver/新flag/schema或批准消费/MCP。

**CLAR-005 必要兼容fixture范围修订（root于测试编辑前明确批准）。** 首次完整11类于`/tmp/tianji-governed-native-process-final.log`得到133 total=131PASS/2ERROR/0FAIL/skip（25.637s，09:55:52），仅AdkRunContextTest两项ToolBudgetExceeded：其tools()沿用snapshot(0)却预期两次成功，且只stub旧4arg。新统一entrycounter与5arg fence协议按既定合同生效，需追加唯一该测试路径，不放宽生产校验/配额/断言。tools()用local snapshot(2)，definition与effective policy均2，并给该immutable toolSnapshot的estimator estimate/contextWindow单独stub；mock oldRunScope 5arg用doCallRealMethod，实际fence→已有4arg stub。12项旧断言（含4arg never/times、UUID、context）全保持；non-tool fixtures默认snapshot(0)，原snapshot()delegate snapshot(0)，加snapshot(int toolCalls)复用现构造。精确13paths=6prod/4tests/3docs、root6/budget3/Process2/docs2，selector已含此类不变，仍一个nativecore逻辑commit，无其他生产/API/SQL变更。原9ad SourceGO、4ef机械重锁与本次amendedSHA在evidence分别记录；本次test edit必须等root读新块+strict后再GO。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 同一原生loop治理只读与固定START工具
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交574aa3d及前置START/Access/observer/fenceddeadline；原ADK/compiler/Plugin/serializedsink/privacy。
- Baseline state: native只RO bridge；fixedSTART/Access是显式library，未统一nativeentry计数/动态businesseffect冻结。
- Observable outcome: 实际NativeRunner mixed RO+START受同entryquota/fence，hostintent/knownresult当前权，所有终态用frozenbusinessfact且旧generation不能覆盖。
- End state: AR programmatic governed union core与兼容RO/旧factory可用，AS启用/AA/MCP/真实环境验收仍未交付。
- Test-first gate: Required — 仅最小compile声明→RO/common、Process、nativeLoop各有效RED→各自GREEN，不提交stub，不以fixture/compiler错误作RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java`
- Purpose: common窄bridge/Scope及typed事件合同供RO与union两实际实现。
- Symbols: public AdkGovernedToolBridge.bindScope(material,lease,ToolEventSink):Scope；nested Scope/ToolEventVO/Phase/ToolEventSink。
- Repository evidence: 现Plugin/Executor耦合RO.RunScope/ToolEventVO，实际需RO与union两种root而非第二native loop。
- Dependencies and consumers: ROadapter、Process桥、Plugin/Executor；旧RO包内API保留。
- Why now: 最小interface/value声明使兼容与新native测试编译，不先实现执行行为。
- Contract/signature changes: Scope.declaredTools/execute(BaseTool,Map,UUID,String,Runnable)/sideEffectOutcome/close；common typedEvent沿old metadata但dispatchEpoch为nullable Long。
- Input/output and state mapping: STARTED epoch0；freshinv FINISHED actualepoch；获权access known3 epochnull；UNAVAILABLE null或旧0由sink省略。
- Error and edge behavior: 无args/body/receipt/nativecallID/nonce/凭据；公共接口不授执行权限；不新增框架/Bean。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
public interface AdkGovernedToolBridge {
    Scope bindScope(AgentLedgerService.ExecutionMaterial material, AgentRunLease lease, ToolEventSink sink);
    interface Scope extends AutoCloseable { List<BaseTool> declaredTools(); Map<String,Object> execute(BaseTool tool, Map<String,Object> args, UUID hostId, String author, Runnable callFence); String sideEffectOutcome(); void close(); }
    // Typed status-only ToolEventVO retains author/ref/hostUUID/state/accounting/error, with nullable Long dispatchEpoch for access; Phase is STARTED/FINISHED.
}
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: common合同可编译，旧native行为尚未切换。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolDeclaration.java`
- Purpose: 共享现closed primitive native declaration，direct fallback仍deny。
- Symbols: package-private AdkGovernedToolDeclaration extends BaseTool。
- Repository evidence: 当前RO.DeclarationTool已构造parametersJsonSchema/required/additionalPropertiesfalse，runAsync FeatureNotAvailable。
- Dependencies and consumers: RO与Process声明实例；现BaseTool/FunctionDeclaration/ToolSchema。
- Why now: compile-only构造/方法可先声明；RO兼容测试真实RED后再精确extract，不先扩大执行路径。
- Contract/signature changes: 提取当前声明语义，不改Components/ADK模型options/SDK；每run新tool实例。
- Input/output and state mapping: primitive字段/闭合required排序不变，parameters必须absent，parametersJsonSchema保留additionalPropertiesfalse。
- Error and edge behavior: BaseTool.runAsync始终sanitized FEATURE_NOT_AVAILABLE；不隐藏transport调用。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
final class AdkGovernedToolDeclaration extends BaseTool {
    // Copy the existing DeclarationTool closed primitive schema construction exactly: sorted properties/required, additionalProperties=false, parametersJsonSchema only.
    @Override public Optional<FunctionDeclaration> declaration() { return Optional.of(declaration); }
    @Override public Single<Map<String,Object>> runAsync(Map<String,Object> args, ToolContext context) { return Single.error(sanitizedFeatureUnavailable()); }
}
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: shared declaration保持原schema/hash/output隐私和directdeny。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java`
- Purpose: 先锁定旧RO包内API/新commonadapter/共享声明兼容。
- Symbols: 旧RO tests保留；新增bindScope/bindResolved/declaration guards。
- Repository evidence: 既有RO9 tests与旧nativeLoop spy.bind线229不可移除；SDK要求parametersJsonSchema。
- Dependencies and consumers: common/Declaration最小声明、RO新方法最小UOE；不改旧Loop测试文件。
- Why now: ROadapter/共享声明组件先真实RED，再其owner最小GREEN。
- Contract/signature changes: 原bind/RunScope/ToolEventVO/Phase与usesRecorder/Transport维持；bindScope必须调用oldbind。
- Input/output and state mapping: bindScope返回identical oldRunScope；public5arg先safeRunnable再old4arg、null/throw无events/Recorder；boxedmetadata；bindResolved给exactsubset而不重复visible/hostresolver。
- Error and edge behavior: privacy/material/backend/READ_ONLY/exactidentity/null/dup/missing/extra与directdeny/closed schema；旧9断言不弱化。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
assertCommonBindCallsOldBindExactlyOnceAndReturnsIdenticalOldRunScope();
assertSharedDeclarationUsesParametersJsonSchemaClosedRequiredAndDirectAsyncStillDenies();
assertResolvedSubsetDoesNotCallFullVisibilityOrHostResolverAgain();
assertNewFiveArgumentExecutionRunsFenceBeforeStartedAndRecorderAndRejectsNullOrThrowWithoutIo();
assertStaticResolvedSubsetAcceptsMixedSnapshotReadOnlyPartAndRejectsWrongDuplicateRefBackendOrOwner();
assertPrivacyMaterialRiskAndExactToolIdentityRemainFailClosedWithAllOldNineTestsRetained();
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: RO/common seam有效RED已观察，旧tests与spy不被替换。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java`
- Purpose: 兼容旧RO API，implements common并提供union已解析subset绑定。
- Symbols: implements AdkGovernedToolBridge；public bindScope；package static bindResolved；原bind/RunScope/ToolEventVO/Phase unchanged。
- Repository evidence: 旧RO172行有requireMaterial/fullregistry.visible/hostbindings与privateDeclaration；现oldLoopspy依赖bind。
- Dependencies and consumers: common/Declaration与ROBridgeTest有效RED；原recorder/decisiontransport隐私/执行行为。
- Why now: 先完成最小adapter以让union复用RO，不copy旧执行引擎。
- Contract/signature changes: bindScope→oldbind并返回同一oldRunScope；oldRunScope implements commonScope、声明/sideEffect升public、4arg保留，5argsafeRunnable后调用4arg；oldbind只作原一次visible/hostBindings后delegate static bindResolved(recorder,transport,material,lease,oldSink,List<ToolDescriptor>,List<BindingVO>)；oldRunScope的public5arg先Runnable再旧4arg。
- Input/output and state mapping: static helper校material/exactREAD_ONLY subset/fullbinding/backend，构造原RunScope；common event转换复用包内toGovernedEvent，无Scope wrapper/第二registry/innerRObridge。
- Error and edge behavior: 旧publicctor/usesidentity、包内符号/4arg/spy/defaultprivacy/directdeny保持；null/throw Runnable safeDep/保Ledger code固定消息先于events/Recorder；不让union重复resolve。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
public Scope bindScope(material, lease, sink) { return bind(material, lease, event -> sink.append(toGovernedEvent(event))); }
// Old bind performs the original one registry.visible and one hostBindings, then delegates static helper.
static RunScope bindResolved(recorder, transport, material, lease, oldSink, subset, bindings) {
    requireMaterialAndExactReadOnlySubsetBackendBindingsWithoutRegistryOrHostResolver();
    return buildOriginalRunScopeWithSharedGovernedDeclarations(recorder, transport, lease, oldSink, subset, bindings);
}
// Old RunScope implements common Scope; public five-argument execute validates/runs the fence safely, then invokes unchanged four-argument execute; declarations/effect methods widen to public only.
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: 旧RO路径与common union可同时使用，旧内部API/spy仍可见。

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java`
- Purpose: 先证明union绑定/可信START上下文/执行与冻结的真实业务边界。
- Symbols: 新AdkGovernedProcessToolBridgeTest。
- Repository evidence: 现完整ToolRegistry、typed START/Access Recorders与Step18 observer可用；无可信version/TTL模型字段。
- Dependencies and consumers: common/Declaration与Process最小ctor方法UOE，既有domain/recorders/Clock fixtures。
- Why now: Process组件真实RED后才实现binding/执行/aggregate；不以空negative测试替代正向。
- Contract/signature changes: 对一fullvisible/一hostbindings调用、全set/noextra/globalnames/risk/affinity/privacy与resolver strict guards测试。
- Input/output and state mapping: freshknown4/Accessknown3；fullids/schema/receipt/cost0/SETTLED；accountPending blocksmodel却保businessknown；observer/freeze并发/inflightUNKNOWNdominance。
- Error and edge behavior: ownAttemptnull才V14accesssameUUID；nonnull/outcomenull无access；unknown/inflight不redrive；fence被outermask仍原stop优先、delegate failure不能NOT_SENT。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
assertOneFullVisibilityAndOneHostResolverWithExactUnionRefsBindingsNamesAndSupportedRisks();
assertTrustedContextResolvedOncePerStartAndDatabaseTimeOnceWithMicrosTtlExpiryNotClampedToRun();
assertFreshStartAndAuthorizedOriginalResultAccessValidateIdentitySchemaAccountingWithoutReceiptInModelOrEvents();
assertAccountingPendingRetainsObservedConfirmedOrNoneWhileBusinessInflightUnknownDominates();
assertFreezeAfterRecorderEntryBeforeHttpOrMetadataToAccessGapStaysUnknownWithDelegateZeroAndLateObserverFrozen();
assertPreDelegateFenceFailureWinsOuterWrappingButDelegateFailureIsNeverNotSent();
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: Process桥矩阵有效RED；hostversion/TTL/sourcefee/accounting与effect分开证明。

#### File 6 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java`
- Purpose: 一套union声明执行只读与固定START，可信intent与当前授权原结果访问，锁内冻结业务effect。
- Symbols: implements AdkGovernedToolBridge；StartActionContextResolver/RequestVO/VO；Scope与identityhelpers。
- Repository evidence: 已提交fixedSTART/readonly transports、两Recorders、V14Access与statusObserver，ToolRegistry/fullmaterial与ledger.databaseNow。
- Dependencies and consumers: common/Declaration与ProcessBridgeTest真实RED、RO.static bindResolved(recorder,decisionTransport,...)、Step18observer/deadlineAPI；不引用AS或建innerRO/第二registry。
- Why now: 本组件有效RED后实现；复用RO与既有recorders，不重建权限/JSON/业务journal。
- Contract/signature changes: mandatory ctor(ledger,invRecorder,decisionTransport,startTransport,resultAccessRecorder,descriptors,hostBindings,StartActionContextResolver)；public exact usesLedger/Recorder/DecisionTransport/StartTransport/ResultAccessRecorder。
- Input/output and state mapping: 一fullvisible+一hostbindings exactset→ROsubset与IDW START；resolver(hostrun/snapshot/descriptor)once/START，DBnowonce，固定业务key/version/TTLexpiry；runScope自持exacttool IdentityMap与perentrybusinessfact aggregate。
- Error and edge behavior: 无model/native/run fallback；AA/otherwrite拒绝；no scopeLock overIO；closefreeze不等IO/晚fact不改；knownaccountPending不降effect但不出model；一entry最多一FINISHED。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
bindScope(material, lease, sink) {
    validatePrivacyMaterialAffinityAndFullRegistryVisibleAgainstOneHostBindingsResult();
    var dbNow = ledger.databaseNow(); // one bind-time read for fixed START expiries; resolve host context once per visible START.
    buildReadOnlySubsetViaStaticBindResolved(recorder,decisionTransport,material,lease,roSink,subset,bindings);
    buildIdempotentStartTargetsWithCanonicalHostBusinessContext();
}
executeStart(entry) { appendStarted(); markEnteredStartUnknownUnderScopeLockThenRelease(); var result = recorder.executeIdempotentStart(wrapOneShotWithFenceAndShortLockedAdmission(), exactRequest, committedStatusObserver(entry));
    if (heldFenceFailure != null) throw heldFenceFailure; if (result.ownAttempt()==null) accessSameHostAfterLockedUnknownMarkAndFreshAuthority(); else validateActualOwnResultOrFailClosedWithoutAccess();
    appendExactlyOneMetadataOnlyFinished(); requireOpenAndSettledBeforeReturningClosedModelEnvelope();
}
close() { synchronized(scopeLock) { freezeAggregateFromCommittedBusinessFacts(); closed=true; } }
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: programmatic union桥具备可信固定START/只读/V14访问，native core消费者下一文件接入，AS未配置。

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`
- Purpose: 实际NativeRunner mixed工具loop先RED，锁定Plugin/Executor计数/冻结/终态协调。
- Symbols: AdkGovernedProcessLoopTest；本类私有fixture。
- Repository evidence: 现RO Loop nativeRunner+FakeChatModel可参考，但不抽取/改其testkit；Step18 fenceddeadline已commit。
- Dependencies and consumers: ROadapter与Process组件已GREEN、最小common Supplier/Scope/bind/event/5arg编译plumbing；实际ADK/FakeChatModel/受控Ledger/Recorder。
- Why now: 完成必要compile-only protocol plumbing后，以缺sharedcounter/freeze/新deadline/正确CONFIRMED等实际core行为RED，再改Plugin/Executor功能。
- Contract/signature changes: 新增独立私有fixture，不新框架/脚本、不提取旧testkit。
- Input/output and state mapping: 同步/流式mixedread+START→secondmodelrequest；入口budget含invalid/replay/meta/accessfail；parallel/cancel/idle/deadline/fence/preserved terminal。
- Error and edge behavior: Loop RED隔离缺sharedcounter/旧cachedbool导致START给NONE/旧宽deadline/未closebeforefinish，禁止类型/Processstub或无效fixture充RED；旧context/session/hook/defaultoff/privacy/spy回归保留。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
runActualNativeRunnerWithFakeChatModelReadThenStartAndAssertSingleGovernedOperation();
assertOneEntryCounterCoversMalformedReplayMetadataAndAccessFailuresWithoutRefundOrSecondAccessCount();
assertParallelLeafContextsSessionFenceAndExactToolIdentityCannotBypassLimitOrPreHttpFence();
assertFreezeBeforeSuccessFailureCancelIdleDeadlineAndAuthorizationStopPreservesCommittedEffects();
assertLateEventsStaySealedAndDeadlineGenerationRaceNeverReusesOldConfirmedEffectForNewGeneration();
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: native core真实RED后root才给Plugin/Executor GREEN GO。

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`
- Purpose: 原Plugin统一合法native入口计数与再次执行fence，保持精确context/session/工具身份。
- Symbols: commonScope字段/兼容constructors/beforeToolCallback；existing admittedContexts lock内counter。
- Repository evidence: 当前beforeTool已requireNativeContext/admitted/exacttool/sessionfence并contextAwareIo，maxToolCalls只有底层journal不能计malformed/meta。
- Dependencies and consumers: common接口与NativeLoopTest有效RED、Process/ROscope；现nativecontext/modeladmission/sessionguards。
- Why now: nativeLoop自身RED后实现，单入口计数不增加第二loop或SpringAIcallback。
- Contract/signature changes: 旧constructors默认null保留，scope类型common；一个existingPlugin内线程安全counter。
- Input/output and state mapping: context/tool/session先校→consume entry quota→才args/event/Store/hostUUID；malformed/replay/meta/accessfail都计且无refund，access不二次计。
- Error and edge behavior: exceedBUDGET_EXCEEDED不触Store/transport；传Runnable复核相同context/exacttool/session而不重复计；不loosen六canonicalcallbacks/toolsets/defaults/codeexecutor/hiddenpaths。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
beforeToolCallback(tool,args,context) {
    return safeErrors(Maybe.fromCallable(() -> {
        requireNativeContext(context,true); requireExactDeclaredTool(tool); requireSessionFence();
        synchronized(admittedContexts) { requireOpenAndConsumeOneEntryOrBudgetExceeded(); }
        Runnable callFence = () -> { requireNativeContext(context,true); requireExactDeclaredTool(tool); requireSessionFence(); };
        return toolScope.execute(tool,args,UUID.randomUUID(),context.agentName(),callFence);
    }).subscribeOn(AgentFlowSchedulers.contextAwareIo()));
}
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: 统一maxToolCalls入口限制与preHTTP再次fence生效，parallel/重放不能绕计数。

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`
- Purpose: 单一cached commonroot，所有终态路径先seal/freeze动态effect，再fenced deadline协调。
- Symbols: Supplier<? extends AdkGovernedToolBridge>；usesReadOnlyDecisionTools/usesGovernedProcessTools；Scope/eventSink/finish helpers。
- Repository evidence: 当前611行保留oldctors/singlecachedROsupplier/serializedsealed sink，postclaimdeadline多处旧宽expire且effect固定boolean。
- Dependencies and consumers: common接口与NativeLoopTest有效RED、commonScope与Step18 fenced expireDeadline；原flowFactory.compileRoot。
- Why now: nativeLoop RED后改真实消费链，一套nativeRunner/无AS或factory改动。
- Contract/signature changes: same-erasure wildcard supplier兼容oldctors/factorySAM，onlyonecachedroot；usesRO仅rootidentity不匹配inner，newusesProcess同exactroot。
- Input/output and state mapping: 所有completion/failure/cancel/idle/deadline/authstop/finishrace eventSink.seal→Scope.close冻结→readEffect→提交；finally幂等close后runnercleanup；无scope/empty旧UNKNOWN保持。
- Error and edge behavior: postclaim deadline用newfenced expire(lease,rev,frozenEffect)；REVconflict reload terminal返回，非终态DBexpired才oldwide UNKNOWNfallback，不能用旧scopeCONFIRMED写新generation；preclaim原分支不变。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
AdkGovernedToolBridge root = cachedSingleSupplierRoot(); Scope scope = root.bindScope(material,lease,eventSink::tool);
compileRootWithExactScopeDeclarationsAndRunSingleNativeLoop(scope);
// Every result/stop/race path first serializes event sealing and scope closure, then reads the frozen business effect.
eventSink.seal(); if (scope!=null) scope.close(); String effect = frozenEffectOrOriginalLegacyUnknown(scope);
try { return ledger.expireDeadline(actor,lease,query,currentRevision,effect); }
catch (REVISION_CONFLICT) { var latest=ledger.get(actor,query); if (terminal(latest)) return latest; if (databaseDeadlineExpired(latest)) return oldWideDeadlineWithUnknown(); throw conflict; }
// Pre-claim expiry branches and old constructors/default-off/CODE_FIRST/privacy/hidden-hook guards remain unchanged.
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: 实际native mixedtool链使用动态frozeneffect/入口quota，迟到事件与旧generation不能覆盖终态。

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`
- Purpose: 只对齐既有工具fixture的合法配额与5arg执行协议，保持旧12项测试断言。
- Symbols: tools()、snapshot()、snapshot(int toolCalls)、toolSnapshot estimator stubs、mock RunScope.execute五参数。
- Repository evidence: 实读tools()使用global snapshot()，现policy.maxToolCalls=0且旧4arg execute被stub；完整11类仅本类两项ToolBudgetExceeded，非生产功能缺失。
- Dependencies and consumers: 新统一Plugin计数/5arg→旧4arg协议；本类所有12项既有测试，不改其他testkit/source。
- Why now: 首完整回归暴露必要fixture依赖，root先批准将此唯一路径纳入13scope，并阅读本块/strict后才允许测试编辑。
- Contract/signature changes: 只增private snapshot(int toolCalls)，原snapshot()保持默认0；tools()用local immutable snapshot(2)，model estimator对应该新snapshot；无生产接口变化。
- Input/output and state mapping: 工具fixture declared/effective maxToolCalls均2；非工具snapshot(0)不变；5arg doCallRealMethod执行真实Runnable fence后委托已有4arg stub与验证。
- Error and edge behavior: 不绕counter、不将quota0放行、不弱化12项旧断言/4arg never/times/UUID/context；第一完整131PASS/2ERROR作为该依赖对齐的实际失败证据，修fixture后重跑既定11类。
- Standards impact: MC-VALID-001、MC-MODEL-001、MC-REUSE-001、MC-SCOPE-001、MC-TEST-001；本域immutable fixture与现Mockito模式，不加业务Bean/config。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；只fixture配额/协议对齐、现JDK/Mockito、原断言与非工具默认保持，无新增依赖/时间或生产变化。
- Implementation pseudocode:
```java
private static AgentExecutionSnapshot snapshot() { return snapshot(0); }
private static AgentExecutionSnapshot snapshot(int toolCalls) { return existingSnapshotWithSamePolicyForDefinitionAndEffectivePolicy(toolCalls); }
private RunScope tools() {
    var toolSnapshot = snapshot(2);
    stubExistingEstimateAndContextWindowForExactImmutableSnapshot(toolSnapshot);
    doCallRealMethod().when(scope).execute(any(), anyMap(), any(), anyString(), any(Runnable.class));
    plugin = existingPluginWith(toolSnapshot, scope);
    return scope;
}
// Retain all twelve existing assertions and all old four-argument stubs/verifications verbatim.
```
- Verification contribution: TEST-004既定AdkRunContextTest/完整11类；只修合同依赖fixture，不能写成生产bypass或旧断言放宽。
- After this file: 工具fixture合法quota与5arg协议一致，non-tool默认0/12项旧断言保持，完整回归再验证。

#### File 11 — `MODIFY README.md`
- Purpose: 说明programmatic governed union与稳定业务context/配额/effect边界，未新增AS启用。
- Symbols: AR native tool治理段落。
- Repository evidence: 现README只有ROnative与显式START/Access库说明；Step18observer/fence已commit。
- Dependencies and consumers: 本步生产/test真实GREEN后root更新。
- Why now: 写最终事实，不预称AS配置/真实环境已验收。
- Contract/signature changes: 无flags/权限/依赖/APIendpoint变化。
- Input/output and state mapping: model只业务args；hostkey/version/TTL，onecounter、committedfactvsaccounting二维、freeze/CAS与V14knownresult。
- Error and edge behavior: 无receipt/cost/actionID/modelbodymetadata泄漏；unknown不retry，accountPending阻输出但不降已知effect；latefactsprivateonly。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Document the programmatic union bridge, unchanged default-off factory behavior and host-owned START context.
Explain global native-entry counting and the distinction between committed business effects and accounting that may remain pending.
Retain AS assembly/AA/MCP and real IAM/PostgreSQL/model-network acceptance as pending; never describe one-shot unknown as safe to resend.
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: README与实际unioncore/AS未接线及真实验收边界一致。

#### File 12 — `CREATE docs/egon/plan/2026-11-01-09-05-governed-native-process-tools.md`
- Purpose: 冻结13paths、完整closed合同、组件RED与nativeCore一逻辑Step。
- Symbols: Template5/Review/5aliases/17MC/10Rules。
- Repository evidence: 原legacyexact执行授权、574aa3d与masterfreshSHA已核。
- Dependencies and consumers: 权威合同1–4及business/accounting最终CLAR、实际路径/skill资源。
- Why now: root全文/strict/resources前只文档，source/test不先写。
- Contract/signature changes: Created09:05为root请求草稿时刻，filename保持；无Accepted/sourceGO/计数虚构。
- Input/output and state mapping: common/RO、Process、nativeLoop三组件独立真实RED；单commit。
- Error and edge behavior: 不拆先行native空壳/不扩AS/config/schema/依赖或新engine。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Record the exact baseline, effective blueprint SHA and thirteen scoped files with three independent RED barriers.
Bind every implementation participant to trusted context, pre-HTTP fencing, business-only effect aggregation and frozen terminal CAS contracts.
Run strict validation and stop for root full review/resource gates before any source GO; leave actual implementation conclusions pending.
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: strict与root全文/资源通过后才源码GO。

#### File 13 — `CREATE docs/egon/evidence/2026-11-01-governed-native-process-tools.md`
- Purpose: PENDING骨架与实际source/log逐项证据，不用nativeFakeModel当真实服务验收。
- Symbols: 5aliases/17MC/10Rules及组件RED/GREEN/Partial边界。
- Repository evidence: 本步开始没有实际sourceGO/有效RED/GREEN。
- Dependencies and consumers: 计划/后续root实际日志与source/audits。
- Why now: plan先交root后建骨架，真实结果到达再闭合。
- Contract/signature changes: 不预填PASS/count/runtime或未来Git SHA。
- Input/output and state mapping: one-entryquota/effect-accounting二维/时序/冻结/metadata隐私/nativeRunner受控证据。
- Error and edge behavior: 不新repo脚本/harness；无DB/app/浏览器/模型网络；无源码或README/commit由docowner改。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块及§6，Bean/转换等N/A按§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守§6exactcontext/hostauthority/nativequota/业务fact与accounting分离/freeze/旧兼容；Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Create independent pending execution rows for all five aliases, seventeen manual checks and ten literal rules.
Preserve actual component RED failures/direct stub errors and exclude invalid fixtures or compilation as behavioral proof.
Close source/test gates only from root logs and audits, retain real-environment limits and full-platform partial status, and defer commit facts to Git delivery.
```
- Verification contribution: TEST-001/002/003/004/005按本角色；实际结果后填evidence，不把FakeChatModel当远端LLM/IAM/PG证明。
- After this file: 实际evidence全PENDING，不把计划strict当实现PASS。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace root。
- Verification command: §8 canonical exact11类，root统一Maven，全部owner冻结Java；docowner不构建。
- Expected result: 各组件真实RED后完整GREEN，oldROspy/ctor/defaultoff/privacy/hiddenpath/CODE_FIRST/VO兼容；actualcount由日志填，不假runtime验收。
- Failure returns to: schema/adapter兼容回RO/common；context/业务effect/当前结果/fence回Process；counter/Session/终态/lateevent回Plugin/Executor；fixture错先修再重跑有效RED，需扩AS/依赖/旧SQL回root不自行扩大。
- Completion criteria: 13paths内无operationalstub，17MC/10Rules实际逐项闭合，23SQL bytefreeze、root全文/独审/diff/cached通过，真实边界诚实。
- Rollback: 可回退binary与旧RO/defaultoff链；不改删现migration/事实、不repair/drop/backfill或业务重派。无新DDL或配置需撤销。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolDeclaration.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java、README.md、docs/egon/plan/2026-11-01-09-05-governed-native-process-tools.md、docs/egon/evidence/2026-11-01-governed-native-process-tools.md
- Commit: `feat(agent): govern native read and process start tools`

## 8. Test, Validation, and Quality Gates
root运行以下计划命令，尚未实际执行；owner先冻结全部Java：
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,TianjiAgentRuntimeAutoConfigurationTest,AdkToolTelemetryPrivacyTest,AdkRunContextTest,AdkExecutionTransactionBoundaryTest,ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest,AgentDeadlineEffectTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-governed-native-process-final.log 2>&1
```

| Gate | Working directory | Exact classes/command | Proof | Expected boundary | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RO/common | tianji-service | AdkReadOnlyDecisionToolBridgeTest；先新行为RED后GREEN | shared schema/directdeny/staticresolved/oldspy与原RunScope5arg fence适配 | old9 assertions不弱化，非真实HTTP/LLM | budget/root |
| TEST-002 Process bridge | tianji-service | AdkGovernedProcessToolBridgeTest；有效RED后GREEN | onefullregistry/contextTTL/typedbody/currentaccess/knownaccountPending/freeze/heldfence/不误NOT_SENT | controlledRecorders/transport/ledger，非真实IAM/PG/crypto | Process/root |
| TEST-003 native core | tianji-service | AdkGovernedProcessLoopTest；实际nativeLoop RED后GREEN | mixed工具/secondmodelrequest/单counter/parallel/session/cancel/idle/deadline/frozenknown与lateevents/oldgeneration | 真实NativeRunner+FakeChatModel，无模型网络或应用server | root |
| TEST-004 compatibility | tianji-service | §8 exact11类（含AdkRunContextTest） | 原ROLoop/AutoCfg/Privacy/RunContext/transaction与START/Access/deadline旧接口回归；Context tools()仅按CLAR-005对齐snapshot(2)/5arg，12项断言不变 | 默认off、constructor/factorySAM/旧spy/hiddenpaths/codefirst保持；实际childprobe如发生按日志而非猜 | root |
| TEST-005 static/plan | workspace root/service | strict/diffcheck/显式13paths/23SQLbytecompare/独审 | v5/trace/MC/Rule/time+source/lifecycle/无新deps | plan可执行性不等功能/运行PASS；untracked用no-index或最终cached检查 | root/doc |

Strict：`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-09-05-governed-native-process-tools.md --strict`。既有9个selector已rg核、新2Test按scope创建；无repo harness/script需要新增，测试保留subsystem。无额外DB/PGtestselector；不自动启动服务/DB/浏览器/容器或真实OAuth/LLM。

## 9. Migration, Compatibility, Rollout, and Rollback
无新SQL/schema/config/ASfactory/依赖/权限变化；23migration bytes immutable，V13 f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3与V14 88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a保持。不跑Agent不支持的CRUD/codegen或MP迁移，无软删除业务entity/consumptionlog新需求。旧RO包内API/spy/ctors/factorySAM/defaultoff/CODE_FIRST/privacy/hook守卫保持；nativeSTART只programmatic core，AS assembly后续。binary可回退，已提交journal与SQL不可删除/改写，不自动重派或repair/drop/backfill。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | Step 1 | common/decl/RO/Process/Plugin/tests | TEST-001/002/003/004 | 一visible/一hostresolve/exactset+names、identity/privacy/CODE_FIRST/hooks守卫；oldRO API/spy兼容 |
| PLAN-REQ-002 | §11.3/13 | Step 1 | Process/BridgeTest/Loop | TEST-001/002/003/004 | snapshot.businessKey唯一来源、hostversion/TTL canonical，DBnow一次/µexpiry不clamp；排除nativecallID/runfallback |
| PLAN-REQ-003 | §11.3/12/13.3 | Step 1 | Plugin/Process/nativeLoop | TEST-001/002/003/004 | onecounter含invalid/replay/meta/accessfail无refund；exacttool/context/session再fence；freshSTART/authorizedV14knownresult、SETTLED才model |
| PLAN-REQ-004 | §7.2/13.4 | Step 1 | Process/Executor/Loop | TEST-001/002/003/004 | observerknown不因accountPending降级；UNKNOWN业务支配；seal→closefreeze→effect→终态；deadlinefenced/race旧generation不能写新 |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | all/tests（含ContextTest必要fixture）/README/docs | TEST-004/005 | 精确13paths/23SQL/无依赖ASconfigAA-MCP；组件有效RED后11类回归，环境未验收诚实 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| CLAR-005 | 既有Context工具fixture quota0/4arg与新合同不符 | 完整133=131PASS/2ERROR，追加仅此依赖test路径；localtoolSnapshot2+estimatorstubs/5arg real→old4arg，旧12断言保持 | root | Approved scope amendment；root读新增块+strict后test edit，selector不变 |
| CLAR-001 | accountingPending被误当业务UNKNOWN | Spec7.2/Step18 observer；已commitknown保持，只业务inflight/未认证才UNKNOWN | root/独审 | Closed；二维表/反证测试，source前已纠正 |
| CLAR-004 | common Supplier同erasure与Loop fixture编译 | 仅机械protocol plumbing先行；RO/Process已GREEN再Loop真实core RED，不能类型或stub充证据 | root | Closed；functionalcounter/freeze/deadline等待LoopRED |
| CLAR-003 | metadata/APPLIED返回与access间freeze误报NONE | valid START在Recorder前短锁标UNKNOWN；仅committedobserver解析，preflight无observer保守UNKNOWN | root/独审 | Closed；SessionFencefreeze-first/metadata-to-access gap反证 |
| CLAR-002 | 第二registry/RObridge或源digestparser | oldbind与union共用static resolvedhelper；exactRequest+Store负责ref/digest，bridge仅公开VO guards | root | Closed；无innerRO、越层codec/expectedRevision参数 |
| DEC-001 | Session fence变成可恢复业务negative/POST重派 | 只catch predelegatefence/admission且heldsafeRunStop优先，delegate失败不映NOT_SENT | root | Closed；beforeHTTP/freeze竞争测试 |
| DEC-002 | oldgeneration CONFIRMED覆盖新run/终态 | seal+freeze后newfenceddeadline，REVrace reloadterminal；fallback仅旧wide UNKNOWN | root | Closed；preclaim原样/晚facts只journal |
| RISK-001 | native output/事件在close后到达 | close不等IO，observerlate不改aggregate，sinksealed不增terminalrevision；journal仍保facts | root | Tracked；需要实际并发受控测试 |
| RISK-002 | programmatic core被误称AS/runtime完成 | 本步无AS/config/AA/MCP/真实IAM/PG/模型网络 | 用户/root | Tracked；fullT3-07/Goal仍Partial，用户自行runtime验收 |

## 12. Review and Acceptance
合同1–4与最终business/accounting、Recorder前UNKNOWN、RO直适配CLAR已闭合，实际paths/constructors/旧seam/selector核实，当前无协议未决点；首次完整回归发现的ContextTest必要fixture已按root明确批准纳入CLAR-005，新增test edit等待本块审阅/strict门禁。下面PASS仅计划可执行性，Review沿原exact授权，不虚构Accepted；原SourceGO及4ef机械锁已实际完成，本次ContextTest仅在root读amended block+strict后才编辑。执行证据保持最终PENDING，各组件RED/GREEN、17MC/10Rules与最终Git按真实执行填。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR adk/application ports/typedRecorders与单nativeRunner | 只真实RO/union variation port与Adapter，现JDBC/Flyway/Agent excluded保持 | None |
| MC-REUSE-001 | Applicable | PASS | 原RO.RunScope/staticresolved helper、declaration、Recorders/observer/V14access/fenceddeadline | 不建innerRO/第二registry或parser/transport/engine，保原Native loop | None |
| MC-DEP-001 | Applicable | PASS | 现ADK/RxJava/JDK/AgentFlowSchedulers/registry/typedports | 无依赖/框架/脚本/模型层变动 | None |
| MC-NAME-001 | Applicable | PASS | commonBridge/Declaration/ProcessBridge与Context RequestVO/VO/typedEvent（toString脱敏） | 接口为实际variation点，records为不可变metadata，不造业务entity | None |
| MC-VALID-001 | Applicable | PASS | fullmaterial/全refs/backend/names、trustedkey/version/TTL、exactcontext/tool/session/freshRecorders | 调用fence无二次quota；START/accesstypedbody与SETTLED；无modelauthority | None |
| MC-MODEL-001 | Applicable | PASS | 原VO/ports/SQL不改，新增immutable hostcontext/typedmetadata | output不含receipt/cost/actionID/私有metadata，无新持久实体/softdelete | None |
| MC-CONVERT-001 | Not applicable | N/A | metadata适配/原typedOutcome复用，sharedclaration精确extract | 无PO/entity/通用DTO转换或newMapper；sourceSHA归Store | 不新parser/converter |
| MC-LOG-001 | Not applicable | N/A | 无新业务log；现typed sink只allowlist状态metadata | 不泄漏args/output/nativecallID/nonce/credential；scope晚facts不reopenevent | 无日志注入变更 |
| MC-BEAN-001 | Not applicable | N/A | programmatic technical bridges/现ctor；AS factory/autoconfig不改 | 无新SpringBean/Qualifier/配置激活，不伪Lombok DI | 沿现technicalctor |
| MC-UTIL-001 | Applicable | PASS | 现JDK/IdentityHashMap/原ledger/domain/typedRecorders/contextAwareIo | 无新utility/JSON/身份/线程pool，scopeLock不跨IO | None |
| MC-JSON-001 | Applicable | PASS | 现parametersJsonSchema/directdeny，typedOutcome/原portVO | 参数闭合primitive/required/additionalPropertiesfalse；model/event白名单，无第二parser | None |
| MC-TIME-001 | Applicable | PASS | ledger.databaseNow一次/TTLµfloor；Step18DBfence与原window/lease | TTL不clamp run，绝对run/callwindow仍Recorders核；freeze不等IO，迟到facts不改终态 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/flags/AS/schema/config路径，olddefaultoff factory保持 | 无权限/自动启用；privacy env+cachedTracing false原gate不降 | 无配置面更改 |
| MC-PATTERN-001 | Applicable | PASS | Adapter/commonBridge两实际实现+scope lifecycle，原Facade/UoW/CAS | 解决RO复用/union受控执行/终态冻结，不新Strategy/State引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 13paths/6prod/4tests/3docs，23SQL immutable | 无AS/config/schema/Components/依赖/AA/MCP扩展，不造第二loop | None |
| MC-TEST-001 | Applicable | PASS | RO/common、Process、nativeLoop三独立有效RED门禁；exact11类 | 真实NativeRunner/FakeChatModel与受控Recorders非远端LLM/IAM/PG验收；旧spy/hook/privacy回归 | 实际计数后填evidence |
| MC-BLOCKER-001 | Applicable | PASS | root合同1–4/最终CLAR及staticROhelper、refdigest机械点闭合；baseline/hash已核 | 无待决协议；sourceGO仍需root全文/strict/resources | None |

### 12.6 Final verdict
PASS — Ready for user review
