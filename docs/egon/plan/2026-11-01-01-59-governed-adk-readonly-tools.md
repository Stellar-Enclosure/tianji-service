# T3-07 受治理只读工具的ADK核心接线

| Field | Value |
| --- | --- |
| Document | `2026-11-01-01-59-governed-adk-readonly-tools.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 01:59 Asia/Shanghai` |
| Updated | `2026-11-01 01:59 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | AR core only：readonly native声明、Plugin治理、隐私与安全metadata events；14路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@5574b5ea1b1a9226de4c3dbcff41b4e7e90efe50`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户明确原未完成工作exact执行授权，无规范Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `eb2414f219d61374c04873a20577c4c1436d603d3715aaf7cc95be58b308b2b7` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [只读Recorder](2026-11-01-00-41-readonly-tool-invocation-recorder.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

原Recorder已持久化readonly dispatch/outcome；本步仅将CONFIG的固定可见readonly工具按exact native实例与经治理context接入现ADK唯一loop。先真实隐私门禁、声明Adapter，再Plugin拦截原Recorder，全部工具metadata与text event共享一个run revision锁。一个逻辑Step/commit、十四路径。AS default-off factory/完整链readiness留下一独立Step，不修改SQL/配置YAML/Components/估算SPI/transport/Recorder。

## 2. Target Spec and Effective Design

原蓝图§6唯一ADK loop、§7 run/lease/event一致性、§12可靠工具执行、§13权限/隐私/预算及T3-07为唯一effective设计；局部API由主代理在原用户执行授权下闭合。已存在ToolRegistry/Recorder/可信transport/native Runner/compileRoot，缺少受治理的声明与beforeTool消费。延续AR基础设施/自动配置边界，模型只提供参数，不能选择身份/backend/资源/host invocation ID。没有新Spec Accepted或平台runtime验收推断。

## 3. Effective Requirements and Acceptance

PLAN-REQ为原要求的局部追踪别名，不新增要求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/12/T3-07 | ADK唯一loop消费受治理固定readonly工具 | exact visible binding/实例；runAsync直执拒绝；真实sync/stream工具结果进入第二model请求一次 | Bridge/Plugin/LoopTest |
| PLAN-REQ-002 | §7/13 | 原模型/Session/预算治理不可被native工具绕过 | 每leaf exact invocationContext在ALL beforeModel checks后单独admit；membership/tool/session核验；host UUID随机；无tool retry | Plugin/ContextTest |
| PLAN-REQ-003 | §7/12/13 | 事实持久后安全返回模型，run event无revision竞态 | START/Recorder/FINISH顺序；仅settled known envelope；text/tool共享锁；event失败不重派；非空已绑定readonly scope effect NONE | Bridge/Executor/tests |
| PLAN-REQ-004 | §13.3 | 工具内容不进入ADK spans，宿主显式初始化遥测 | literal env false与actual cached false双核，constructor/bind/run entry复核；child true/absent/-D-only拒绝 | Privacy/test/bridge |
| PLAN-REQ-005 | §6/17/T3-07 | 兼容默认关闭与隐藏路径failclosed，真实证据限定范围 | 旧ctors保留；lazy qualified provider defaultfalse不取；六callbacks/toolsets/defaults/codeexecutor拒绝；token schema增量/相关回归 | Executor/AutoCfg/tests/docs |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

只加最小新类/嵌套值/overload声明供测试编译，保留旧ctor委托null。先privacy/bridge/loop/context/provider真实RED，按各owner实现最小GREEN，再原autoconfig延迟接入与回归。fixture/编译错误不可算RED。终态/预算/授权仍归原Recorder/ledger；不加第二工具循环。

### 4.2 Test-first strategy

privacy子JVM验证真实cached字段而非mock env；bridge正反数据与native实例/事件/结果边界；真实Runner+FakeChatModel同步/流式、Sequential/Parallel clone上下文；原context/estimator/factory新增兼容断言。各组件先独立有效RED再实现，允许其他已过自身RED的组件并行GREEN，最终一个完整commit不留scaffold。

### 4.3 Sequential and parallel boundaries

同一Step中privacy/bridge owner与Plugin/Executor owner在嵌套签名锁定后可独立RED→GREEN；共享Executor/Plugin/事件sink只由指定owner写。新test复用现有fixture，不另建testkit文件。旧AutoCfg Test由factory owner单写。Maven由主代理统一执行，文档作者不构建。

### 4.4 Commit boundaries

十四路径合成一个AR核心治理逻辑commit；AS factory/readiness/default-off配置在下一Step再提交。显式stage精确路径，不push/改历史，不启动runtime。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Bridge | T3-07固定工具进入唯一loop | compileRoot接List<BaseTool>，beforeTool当前一律拒绝 | 声明Adapter+原Recorder，无第二HTTP/预算/执行引擎 | Implement |
| Context identity/typed sink | §7/13防绕过/并发revision | ADK leaf clone context；原text current.get/append/set未与tool共享 | 包内有界identity集合+ONE synchronized sink，不造event bus | Implement |
| Privacy gate | §13.3内容保护 | 固定ADK私有cached flag与env独立 | readonly反射验证已存在字段，不修改ADK/全局OTel | Implement |
| Lazy provider | §6兼容/有序初始化 | 原executor@Bean，现独立AR/AS | 仅读flag+Supplier；AS实际遥测/链factory下步 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| privacy/声明scope | PLAN-REQ-001/004 | child/bridge负例 | 最小声明/现registry与Recorder | exact tool instances/typed sink | Plugin/native compile | Step 1 |
| native治理与events | PLAN-REQ-002/003 | 真Runner/context/session/metadata断言 | 上项及原model预算流程 | 单loop/no hiddenpaths/serialized revisions | 未来AS宿主factory | Step 1 |
| 延迟配置/兼容 | PLAN-REQ-005 | defaultfalse/provider/tokenSchema | append ctor与原factory | AR可编程入口仍defaultoff | 下一AS Step | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | 原AR infrastructure/adk/autoconfigure技术库；原蓝图独立模块 | 保持现profile | 不增层/模块/业务Service，No AS imports | Step1；MC-ARCH-001 |
| Validation/model | ToolRegistry/Schema、immutable grant/Recorder typed结果 | native技术边界exact验证 | 不可变ToolEventVO；primitive schema/required/closed；identity而非名称 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/logging | 原@Bean/Qualifier/manual技术ctors/现技术日志 | 保持named factory和旧ctors | qualified lazy provider；不新业务Bean或secret日志，不伪称Lombok DI | Step1；MC-BEAN-001、MC-LOG-001 |
| JSON/time | 原Jackson/native typed function message/Recorder期限 | 无新wire或持久codec | 固定allowlist event与envelope；java.time、无期限放宽 | Step1；MC-JSON-001、MC-TIME-001 |
| Pattern | 原Plugin/compileRoot/Recorder扩展点 | 复用Adapter与治理拦截 | BaseTool仅声明，Plugin完成控制，不造第二工具loop | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 工具执行 | 原Recorder/Registry/transport/ToolSchema | 已有dispatch nonce与typed outcome | 足够，不改这些类 | bridge组合 | None | Step1；MC-REUSE-001 |
| native/scheduler | compileRoot/AgentFlowInMemoryRunner/AgentFlowSchedulers.contextAwareIo | 接原BaseTool与context-aware IO | 缺admission/Plugin拦截 | 现Plugin追加 | None | Step1；MC-DEP-001 |
| schema tokens | 原JTokkit估算器LlmRequest tools | estimator已经计schema | 只测试增量，不改SPI/options | reuse | None | Step1；MC-REUSE-001 |
| privacy测试 | JDK ProcessBuilder、固定1.13 jar Tracing字段 | owner javap已核cached字段/contexts | 无公开setter需要 | readonly验证+JDK子JVM | None | Step1；MC-UTIL-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | 技术Bridge/Privacy/RunScope/ToolEventVO | tests→bridge/plugin/executor | 语义类名/不可变事件值 | 编译/类型审阅 | Step1 | PASS |
| Rule 2 | §7/12/13 | 原native Plugin handoff/registry/typedRecorder | bridge/tests/plugin | fullrun/ref/backend/risk+exactcontext/tool/session/result验证 | 正反与真实loop | Step1 | PASS |
| Rule 3 | §13 | 真不可变event值及registry；现domain值 | bridge/executor/test | 只typed值record，无实体/手工Converter层 | 事件/immutability/schema | Step1 | PASS |
| Rule 4 | §6 | 原technical manual ctor与@Bean/Qualifiers | autoconfig/plugin/executor | Bean名保持、qualified lazyprovider，沿现日志不泄secret；无新增业务Service需Lombok DI | provider与日志diff | Step1 | PASS |
| Rule 5 | §6 | JDK/ADK/Rx/Spring现依赖 | privacy/bridge/plugin | 仅原scheduler/JDK readonly reflection，禁Unsafe/新库 | imports/POM | Step1 | PASS |
| Rule 6 | §13 | 原native schema/Jackson/typed metadata | bridge/executor/tests | 闭合schema/固定allowlist；不改codec/descriptor哈希 | 模型typedresponse/event | Step1 | PASS |
| Rule 7 | §6 | 现Environment读取flags | autoconfig/README/test | 仅新读键defaultfalse，不添加profile/YAML；AS后续另步 | 默认false/provider | Step1 | PASS |
| Rule 9 | §6/12 | BaseTool Adapter+Plugin+原Recorder | bridge/plugin/executor | 唯一loop、typed sink/同锁revision、受控能力identity | once/concurrent/failure | Step1 | PASS |
| Rule 10 | §7/13 | 原Recorder/Session期限，JDK child wait | tests/plugin/bridge | java.time、无期限放宽；child10s/finallykill | 期限/Session/child | Step1 | PASS |
| Rule 11 | §6 | 原AR/adk/autoconfig profile | 全部十四路径 | 无新层/deps/SQL/events协议/AS启用/Components修改 | scope/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | lease/session/run/tool deadlines | Occurred instant | Instant | Not persisted | Internal | Not applicable: 原绝对权限期限 | Not applicable: 无UI | 原Recorder/Session核验，bridge与Plugin不放宽或重置授权deadline；NONE不由期限/工具名称猜测 | 原Clock/DBtime保持 | 不改Recorder/transport期限和持久事实 | Step 1；TEST-002 session/cancel/late/endappend |
| TIME-002 | privacy child wait/kill | Duration | Duration | Not persisted | Internal | Not applicable: 测试elapsed | Not applicable: 无展示 | wait最多10s，失败/超时finally destroy，四env组合无exporter | JDK waitFor/TimeUnit，原期限不变 | 不启动长期服务或新harness脚本 | Step 1；TEST-001 child |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacyTest.java | 子JVM验证真实环境和cached ADK field的fail-closed隐私门禁 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java | 固定注册工具实例、typed事件与模型envelope的桥边界 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionLoopTest.java | 真实Native Runner证明唯一工具loop及多agent上下文 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java | 保留原context/model治理并验证tool上下文admission | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java | 证明原估算器已计入原生工具schema且不扩SPI | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java | 延迟qualified provider、默认关闭与旧构造兼容 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacy.java | 对真实ADK内容capture同时核env与cached field | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java | 声明native工具并组合原Recorder/可信transport | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java | 把经模型阶段治理的exact native context与工具调用关联 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java | CONFIG装配native声明、序列化全部run event revision并保持禁用边界 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java | 原executor Bean仅延迟选择新增qualified bridge provider | Step1 |
| MODIFY | README.md | 解释AR可编程接线与默认关闭、隐私前置及未验证范围 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-governed-adk-readonly-tools.md | 记录实际RED/GREEN/17MC/10Rule及局部/full平台状态 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-01-59-governed-adk-readonly-tools.md | 锁定本次十四路径与代码前执行顺序 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline5574b5e clean，root blueprint SHA已核；writing resource preflight18 Markdown/24 refs PASS。既有测试AdkRunContextTest/JTokkitChatModelBudgetEstimatorTest/TianjiAgentRuntimeAutoConfigurationTest/ToolInvocationRecorderTest/ReadOnlyDecisionToolTransportTest/AdkContextWindowPlanTest/AdkContextSnapshotProjectorTest均已rg核存在。tianjiAdkAgentRunExecutor只有原factory声明，无其他Java直接factory caller需增路径；旧AdkAgentRunExecutor constructors全部保留，原PG/transaction测试无需机械改调用。

Bridge公共ctor为(ToolInvocationRecorder,ReadOnlyDecisionToolTransport,Collection<ToolDescriptor>,Function<AgentLedgerService.ExecutionMaterial,List<ToolExecutionGrant.BindingVO>>)，usesRecorder/usesTransport仅exact identity。registry防御复制max256。bind(material,lease,ToolEventSink)返回包内RunScope；重新registry.visible(snapshot)，hostBindings与可见集合exact1:1，full run/ref/backend/risk READ_ONLY一致，缺/多/重复/hash drift拒绝，CODE_FIRST工具保持closed。CONFIG以definition-wide visible tools装配，每次run fresh BaseTool实例/IdentityHashMap。声明含name/description/primitive input schema/required/closed；其runAsync永远安全FEATURE_NOT_AVAILABLE，不能成为第二执行入口。

RunScope.declaredTools()/execute(exactTool,args,UUIDhostInvocationId,Stringauthor)。Typed ToolEventSink.append(ToolEventVO)为包内技术边界；ToolEventVO不可变字段(Phase STARTED/FINISHED,author,DefinitionResourceRef toolRef,UUIDhostInvocationId,state,dispatchEpoch,nullable AccountingState accountingState,nullable errorCode)。event不能含args/output/native functionCallId/nonce/hash/bearer/receipt。execute归一参数→START→Recorder.executeReadOnly(transport,PreparationRequestVO(binding,lease,descriptor,args,null,hostUUID,ZERO,null))→FINISH→envelope；SUCCEEDED+matching outcome+SETTLED且schema正确才{status:SUCCEEDED,output}；REJECTED/FAILED_KNOWN+SETTLED只{status,errorCode}。UNKNOWN/DISPATCHING/nulloutcome/任何PENDING均安全dependency/usage失败，先写safe end metadata，不Maybe.empty、不重试/伪空成功。

Plugin追加RunScope overload，旧ctors传null。ADK每leaf/Parallel分支clone InvocationContext，同叶多round可复用；CallbackContext/ToolContext wrapper不同但.invocationContext()应为同一对象。使用synchronized identity set，只有ALL beforeModel auth/session/context/modelBudget检查成功后admit，size≤maxModelCalls；tool前必须exact membership+exact BaseTool instance+Session fence。host UUID由Plugin随机，与native callId无关。工具分支Maybe.fromCallable.subscribeOn(AgentFlowSchedulers.contextAwareIo())，无retryWhen，不用controlExecutor/dispatchExecutor；close清context。原model容量retry策略不放宽。

Executor保留全部旧ctor，追加Supplier<AdkReadOnlyDecisionToolBridge>；lazy resolve且cache exactly one，usesReadOnlyDecisionTools按identity。claim/material/lease后CONFIG bind并用原compileRoot(flowId,config,chatModels,scope.declaredTools())，所有leaf只允许exact scope实例。所有六canonical callbacks（before/after/onError model与tool）、toolsets、model默认callbacks、code executors及未审路径仍拒绝；不为接工具放宽options/模型wrapper。CODE_FIRST工具不开放。

Executor为AtomicReference<AgentRunDTO> current创建ONE synchronized sink，原text投影append与typedtool metadata payload构造后的get/revision/append/set必须同一锁。tool payload只allowlist toolRef{key,version,contentSha256},hostInvocationId,state,dispatchEpoch,accountingState,errorCode，author为事件author字段；不写参数/输出/native callId。Recorder只记journal不发run event；endappend失败可抑制模型值但不得redispatch，lease fence后end失败不能抹journal。仅成功绑定NONEMPTY全readonly CONFIG RunScope在成功、失败、取消均导出sideEffectOutcome NONE；legacy/noTool仍UNKNOWN，不从名称/raw返回值推断。

Privacy在bridge ctor AND bind/run entry核literal System.getenv("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS")恰好"false"，并只读反射实际Tracing.CAPTURE_MESSAGE_CONTENT_IN_SPANS：exact boolean/private-static-final/trySetAccessible/getBooleanfalse。missing/type/modifier/access/linkage/value/env drift failclosed；无Unsafe/field mutation/GlobalOpenTelemetry.set/reset/setTracerForTesting/-D/YAML替代。固定jar位于/Users/mario/maven/repository/com/google/adk/google-adk/1.13.0/google-adk-1.13.0.jar，已由native API owner javap核。programmatic host须在bridge构造前初始化telemetry；本步不假造公开canary API或新增exporter。

Plan Clarification PLAN-CLAR-001：privacy helper采用`public static AdkToolTelemetryPrivacy.requireSafe()`，与既有技术类可验证入口命名一致；下文原require伪码仅表示同一门禁，未增加职责/配置/路径，bridge构造与bind/run entry均调用该方法。

Plan Clarification PLAN-CLAR-002：STARTED事件state=REQUESTED；Recorder未返回RecordResult的异常以FINISHED/UNAVAILABLE记录，typed事件dispatchEpoch=0仅是无结果sentinel，executor公共payload必须省略该dispatchEpoch，不宣称实际数据库epoch=0。只细化原safe metadata语义，无新状态机或字段。

Plan Clarification PLAN-CLAR-003：closed primitive声明schema使用现ToolConverter实际支持的parametersJsonSchema，parameters必须absent，同时保留additionalProperties=false。此为既有native API表示法选择，不修改Components，不放宽schema或增加工具执行入口。

Plan Clarification PLAN-CLAR-004：同一RunEventSink在正常blocking消费结束后、catch入口及finally均调用seal()；seal与append使用同一monitor，先封闭event流再完成run，避免cancel/parallel失败后已提交tool的迟到FINISHED改变终态revision。Plugin的scoped IdleGuard注册和context admission与close状态核验放入同一admittedContexts锁原子段，close后不重新登记guard/context。均为原并发/关闭一致性边界的机械落实，不扩路径或新执行逻辑；迟到journal事实仍保留，event拒绝不触发重派。

AutoCfg原tianjiAdkAgentRunExecutor末尾加@Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider bridge。仅读新flag stellar.tianji.agent.tools.read-only-decision.enabled；false default从不dereference（即使null旧fixtures），true只传provider::getObject，不提前构造/触Tracing。AS下一Step提供lazy factory、按OpenTelemetry TYPE先初始化再bridge、eager实际链readiness在scheduler-ready前；本步未接这些AS对象或YAML。

十四路径外不改AS/Components/SQL/configYAML/estimatorSPI/transport/Recorder/预算/权限/依赖。所有旧SQL不可改，V13 SHA仍f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3。native子JVM测试为JDK/已有ADK类、nested main、10秒wait/killfinally、无exporter，不是启动应用/服务器。无新scratch harness/scripts/work；测试直接留持久类。不存在待批准新依赖。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 将CONFIG只读工具受控接入原ADK循环

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交5574b5e readonly Recorder与现ADK1.13编译/预算/context链。
- Baseline state: 原beforeTool一律拒绝、compileRoot工具为空，run text append独占current；无native readonly bridge/privacy gate。
- Observable outcome: exact声明与admitted native context只能经Recorder执行一次，safe typed结果完成唯一loop且metadata event无revision竞态。
- End state: AR core程序化接线可用；defaultflag关闭，AS factory/readiness/真实环境/写审批未交付。
- Test-first gate: Required — 最小声明使测试编译后各组件先有效行为RED；不将编译/fixture错误算RED，不提交scaffold。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacyTest.java`

- Purpose: 子JVM验证真实环境和cached ADK field的fail-closed隐私门禁。
- Symbols: AdkToolTelemetryPrivacyTest；nested main、ProcessBuilder、10s wait/kill。
- Repository evidence: 现JDK25/JUnit及固定ADK 1.13.0；不引入外部test harness。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 仅最小privacy声明使测试编译；四种子进程env/property组合先真实RED。
- Contract/signature changes: AdkToolTelemetryPrivacyTest；nested main、ProcessBuilder、10s wait/kill；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: env false→PASS；true/absent/仅-Dfalse→拒绝；无exporter/全局OTel修改。
- Error and edge behavior: 进程超时即失败且finally kill；缺field/type/modifiers/access/linkage均封闭拒绝。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test child_processes_verify_the_literal_environment_and_the_actual_cached_adk_flag() {
  runNestedMainWithEnv("false"); assertExitSuccess();
  for (env : List.of("true", "absent", "absent-with-system-property-false")) assertChildRejects(env);
  assertEveryChildWaitIsBoundedByTenSecondsAndFinallyDestroyed();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java`

- Purpose: 固定注册工具实例、typed事件与模型envelope的桥边界。
- Symbols: AdkReadOnlyDecisionToolBridgeTest；bind/declaredTools/execute。
- Repository evidence: ToolRegistry.visible、原Recorder/typed transport与不可变descriptor测试模式。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: privacy+bridge最小声明→有效fixture真RED；不先接旧executor。
- Contract/signature changes: AdkReadOnlyDecisionToolBridgeTest；bind/declaredTools/execute；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: exact material/binding/ref/backend/risk→fresh声明实例；START/Recorder/FINISH→schema envelope。
- Error and edge behavior: extra/missing/duplicate/hash drift/foreign sameName tool拒绝；pending/unknown不伪成功；event失败抑制输出。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test bridge_owns_exact_declared_instances_and_only_releases_settled_known_results() {
  scope = bridge.bind(material, lease, events::append); assertFreshPrimitiveRequiredClosedDeclarations(scope);
  assertForeignSameNameInstanceDenied(); assertDeclarationRunAsyncAlwaysFeatureUnavailable();
  assertStartedBeforeRecorderAndFinishedBeforeEnvelope(); assertNoPrivateDataInTypedEvents();
  assertSucceededSettledSchemaOutputOnly(); assertPendingUnknownAndEventFailureSuppressModelValue();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionLoopTest.java`

- Purpose: 真实Native Runner证明唯一工具loop及多agent上下文。
- Symbols: AdkReadOnlyDecisionLoopTest；sync/stream、Sequential/Parallel与run event序列。
- Repository evidence: 原AgentFlowInMemoryRunner/ChatModel fixture及ADK1.13 ToolContext。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: plugin/executor最小overload声明后先RED；新bridge业务经自身RED后可并行GREEN。
- Contract/signature changes: AdkReadOnlyDecisionLoopTest；sync/stream、Sequential/Parallel与run event序列；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 真实Runner+FakeChatModel工具调用→typed function result→第二model request；exact contexts。
- Error and edge behavior: clone叶/parallel各自admit；陌生context/session fence/hidden callbacks/toolsets/codeExecutor拒绝；安全event序列无revision竞态。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test native_sync_and_stream_loops_use_one_governed_operation_and_typed_second_model_request() {
  runActualAgentFlowInMemoryRunnerWithFakeChatModelAndFreshDeclaredTools();
  assertExactlyOneRecorderCallAndOneTypedToolResponseBeforeTheSecondModelRequest();
  assertSequentialAndParallelLeafInvocationContextsAreIndividuallyAdmitted();
  assertForeignContextHiddenHooksAndSessionFenceFailClosedWithoutFallbackOrResend();
  assertTextAndToolEventsShareOneRevisionLockAndFailedEndAppendSuppressesOutput();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`

- Purpose: 保留原context/model治理并验证tool上下文admission。
- Symbols: AdkRunContextTest；原beforeModel/session/context测试及新RunScope fixture。
- Repository evidence: 现beforeModel授权/Session fence/estimate/reservation测试和旧构造器。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 新plugin overload只编译声明先行；旧原test不得放宽。
- Contract/signature changes: AdkRunContextTest；原beforeModel/session/context测试及新RunScope fixture；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 所有beforeModel check完成才登记exact invocationContext；same叶round复用不重复占capacity。
- Error and edge behavior: 失败model admission不留context；bounded identity set≤maxModelCalls，close清空；工具不走control/retry。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test tools_require_the_exact_context_after_all_model_authorization_and_budget_checks() {
  assertBeforeToolRejectsBeforeModelAdmission(); admitSuccessfulModelCall(contextA);
  assertDifferentWrapperWithSameInvocationContextPassesAndForeignContextFails();
  assertFailedAuthorizationSessionOrBudgetNeverAdmits(); assertPerLeafIdentitySetBoundAndCloseClears();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java`

- Purpose: 证明原估算器已计入原生工具schema且不扩SPI。
- Symbols: JTokkitChatModelBudgetEstimatorTest；request.tools schema增量断言。
- Repository evidence: 现exact_converter_messages及contextWindow/option fingerprint测试。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 新声明schema可构造后增断言，production estimator无修改。
- Contract/signature changes: JTokkitChatModelBudgetEstimatorTest；request.tools schema增量断言；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 同请求加入声明schema使inputTokens增加，route/policy/fingerprint等原规则保持。
- Error and edge behavior: 不包装options/放开default callbacks或重新实现token estimator。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test native_tool_declaration_schema_increases_the_existing_request_estimate() {
  baseline = estimator.estimate(snapshot, textRequest);
  withTools = textRequest.toBuilder().tools(nativeDeclarations).build();
  assertTrue(estimator.estimate(snapshot, withTools).inputTokens() > baseline.inputTokens());
  verifyNoChatModelCallOrNewEstimatorSpiAndKeepAllOriginalOptionSafetyAssertions();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java`

- Purpose: 延迟qualified provider、默认关闭与旧构造兼容。
- Symbols: TianjiAgentRuntimeAutoConfigurationTest；context runner/provider tests。
- Repository evidence: 现runtime默认关闭/依赖缺失failclosed；rg仅factory声明无其他直接Java调用。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 先定义flag/provider的真实行为RED再改配置factory。
- Contract/signature changes: TianjiAgentRuntimeAutoConfigurationTest；context runner/provider tests；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: flagfalse从不dereference provider（包括null旧fixture）；true保存lazy Supplier而非提前构造。
- Error and edge behavior: 同一bridge只resolve/cache一次；qualified名称准确；本步不提供AS Bean或YAML。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Test read_only_tool_provider_is_qualified_lazy_and_never_touched_when_disabled() {
  buildRuntimeWithFalseFlagAndThrowingOrNullProvider(); assertProviderNeverDereferenced();
  buildRuntimeWithTrueFlagAndQualifiedProvider(); assertNoEarlyTelemetryOrBridgeInitialization();
  assertOldConstructorsRemainValidAndTheEnabledExecutorCachesOneExactBridgeInstance();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacy.java`

- Purpose: 对真实ADK内容capture同时核env与cached field。
- Symbols: AdkToolTelemetryPrivacy；public static void requireSafe()。
- Repository evidence: 固定jar /Users/mario/maven/repository/com/google/adk/google-adk/1.13.0/google-adk-1.13.0.jar中Tracing字段已javap核。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: File1真实RED之后最小GREEN；仅只读reflection不写ADK。
- Contract/signature changes: AdkToolTelemetryPrivacy；public static void requireSafe()；仅门禁入口机械命名，不改原接口/持久合同。
- Input/output and state mapping: literal getenv(ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS)==false AND private cached CAPTURE_MESSAGE_CONTENT_IN_SPANS==false。
- Error and edge behavior: field必须boolean/private static final、trySetAccessible并getBoolean；任一缺失/异常/true failclosed；无Unsafe/setTracer/GlobalOtel.set/reset。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
public static void requireSafe() {
  requireLiteralEnvironmentValueEqualsFalse("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS");
  field = actualAdkTracingClass.getDeclaredField("CAPTURE_MESSAGE_CONTENT_IN_SPANS");
  requireBooleanPrivateStaticFinal(field); requireAccessibleReadOnly(field); requireFalse(field.getBoolean(null));
  mapMissingTypeModifierAccessLinkageOrEnvironmentDriftToSanitizedFeatureUnavailable();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java`

- Purpose: 声明native工具并组合原Recorder/可信transport。
- Symbols: AdkReadOnlyDecisionToolBridge(recorder,transport,Collection<ToolDescriptor>,Function<ExecutionMaterial,List<BindingVO>>)；usesRecorder/usesTransport；bind；包内RunScope/ToolEventSink/ToolEventVO。
- Repository evidence: 现ToolRegistry/ToolSchema/Recorder/ReadOnlyDecisionToolTransport/原native BaseTool API。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: File2独立RED后GREEN；constructor和每次bind/run entry都privacy检查。
- Contract/signature changes: AdkReadOnlyDecisionToolBridge(recorder,transport,Collection<ToolDescriptor>,Function<ExecutionMaterial,List<BindingVO>>)；usesRecorder/usesTransport；bind；包内RunScope/ToolEventSink/ToolEventVO；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: max256 registry copy；visible(snapshot) exact1:1fullrun/ref/backend READ_ONLY bindings；fresh工具、IdentityHashMap；typed事件与envelope。
- Error and edge behavior: 不可直接runAsync、no tool-name权限；CODE_FIRST不开；UNKNOWN/DISPATCHING/nulloutcome/PENDING返回安全dependency/usage失败而非Maybe.empty/retry。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
RunScope bind(material, lease, ToolEventSink sink) {
  AdkToolTelemetryPrivacy.requireSafe(); visible = registry.visible(material.snapshot());
  requireExactOneToOneHostBindingsForFullRunToolReferenceBackendAndReadOnlyRisk();
  createFreshBaseToolsWithPrimitiveRequiredClosedSchemasAndAlwaysDeniedRunAsync(); return identityScopedRun;
}
Object execute(exactTool, args, UUID hostInvocationId, String author) {
  requireIdentityAndNormalizeArgs(); appendTypedStarted(); result = recorder.executeReadOnly(transport, knownZeroReadPreparation);
  appendTypedFinishedMetadata(); returnOnlySettledKnownEnvelopeOrSanitizedDependencyUsageError(result);
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`

- Purpose: 把经模型阶段治理的exact native context与工具调用关联。
- Symbols: 追加RunScope构造overload；beforeModelCallback/beforeToolCallback/close；bounded identity contexts。
- Repository evidence: 旧ctors、model auth/session/estimator/budget流程、beforeTool一律FEATURE_NOT_AVAILABLE。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: File4及loop测试各有效RED后GREEN；保留原model retry行为但tool分支无retry。
- Contract/signature changes: 追加RunScope构造overload；beforeModelCallback/beforeToolCallback/close；bounded identity contexts；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: after ALL modelchecks才admit context.invocationContext() exact object；beforeTool核membership+exactTool+Session fence，随机host UUID。
- Error and edge behavior: same叶多round不重复count；不同leaf/parallel clone分别admit≤maxModelCalls；非native functionCallId；close清理，不用control/dispatch scheduler。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
beforeModelCallback(context, request) {
  preserveExistingModelAuthorizationSessionContextAndBudgetChecks();
  onSuccessfulAdmissionOnlyAddExactInvocationContextIdentityWithinMaxModelCalls(); returnExistingModelResult();
}
beforeToolCallback(tool, args, context) {
  requireRunScopeExactToolAndAdmittedInvocationContext(); requireSessionFence(); UUID hostId = UUID.randomUUID();
  return safeErrors(Maybe.fromCallable(() -> runScope.execute(tool,args,hostId,context.agentName())).subscribeOn(AgentFlowSchedulers.contextAwareIo()));
}
close() { clearAdmittedContexts(); preserveExistingIdleAndContextCleanup(); }
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: CONFIG装配native声明、序列化全部run event revision并保持禁用边界。
- Symbols: 保留所有旧ctors、追加Supplier<AdkReadOnlyDecisionToolBridge>；lazy cached bridge；usesReadOnlyDecisionTools；buildRoot/guard/event sink/result effect。
- Repository evidence: 原compileRoot("tianji",config,chatModels,List.of())、AtomicReference current、projector/text append及requireNoTools。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: privacy/bridge/plugin存在后执行loop RED→GREEN；不改ledger/Recorder。
- Contract/signature changes: 保留所有旧ctors、追加Supplier<AdkReadOnlyDecisionToolBridge>；lazy cached bridge；usesReadOnlyDecisionTools；buildRoot/guard/event sink/result effect；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: claim/material/lease后CONFIG bind→compileRoot(...scope.declaredTools)；ONE synchronized sink为text+typedtool共享current revision/get/set；NONE只成功绑定非空readonly scope。
- Error and edge behavior: CODE_FIRST工具仍closed；六canonical model/tool callbacks、toolsets、modeldefaults/codeexecutor/unreviewedpaths拒绝；append失败抑制模型值不重派，journal仍持久；legacy/noTool effect UNKNOWN。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
executeScoped(actor, query) {
  preserveClaimAndSourceChecks(); current = new AtomicReference<>(claimed.run());
  synchronizedEventSink = oneLockAroundRevisionAppendAndCurrentSetForTextAndTypedAllowlistedToolPayload();
  scope = configMode ? lazyExactCachedBridge.bind(material,lease,typedSink) : null;
  root = compileConfigWithScopeDeclarationsOrKeepCodeFirstToolsClosed(); requireOnlyExactGovernedToolsAndNoHiddenPaths(root,scope);
  budget = new AdkRunBudgetPlugin(existingArguments,scope); runExistingNativeRunner();
  useNONEForSuccessfullyBoundNonemptyReadonlyScopeOnSuccessFailureOrCancelOtherwiseUNKNOWN();
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 11 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`

- Purpose: 原executor Bean仅延迟选择新增qualified bridge provider。
- Symbols: tianjiAdkAgentRunExecutor；末尾@Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge>。
- Repository evidence: 原@Bean factory、Environment flags、现@Qualifier及exact DataSource验证。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: File6真实RED后，旧executor append overload可用再接。
- Contract/signature changes: tianjiAdkAgentRunExecutor；末尾@Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge>；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 唯一新读键stellar.tianji.agent.tools.read-only-decision.enabled defaultfalse；true传provider::getObject，false null。
- Error and edge behavior: 禁止提前getIfAvailable/resolve/Tracing init；无新Bean/YAML/defaulton；AS同源readiness与OpenTelemetry按type初始化属下一Step。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```java
@Bean AdkAgentRunExecutor tianjiAdkAgentRunExecutor(existingDependencies, @Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge> tools) {
  preserveExistingDataSourceModelExecutorAndContextValidation();
  enabled = environment.getProperty("stellar.tianji.agent.tools.read-only-decision.enabled",Boolean.class,false);
  return new AdkAgentRunExecutor(existingArguments, enabled ? tools::getObject : null);
}
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 12 — `MODIFY README.md`

- Purpose: 解释AR可编程接线与默认关闭、隐私前置及未验证范围。
- Symbols: Agent Runtime governed readonly工具段落。
- Repository evidence: 现Recorder/固定transport说明及用户runtime由其自行启动规则。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 相关代码GREEN后按实际结果更新。
- Contract/signature changes: Agent Runtime governed readonly工具段落；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 说明声明不可直执、单native loop、metadata安全、NONE精确来源、env+cached privacy及Step12仍未接AS。
- Error and edge behavior: 不声称有AS配置Bean、真实IAM/PG/LLM、写审批或自动运行。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```text
Document CONFIG governed read-only declarations and plugin-owned single native loop.
Document literal environment false plus cached ADK privacy gate and host telemetry-before-construction duty.
Record that AR provider is default-off and AS factory/readiness integration follows separately; keep unverified boundaries explicit.
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 13 — `CREATE docs/egon/evidence/2026-11-01-governed-adk-readonly-tools.md`

- Purpose: 记录实际RED/GREEN/17MC/10Rule及局部/full平台状态。
- Symbols: 实际日志/计数/5aliases/冻结SQL/child进程测试范围。
- Repository evidence: 既有docs/egon/evidence模式。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 最终选定回归后写真实数据，不预填成功。
- Contract/signature changes: 实际日志/计数/5aliases/冻结SQL/child进程测试范围；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: 列controlled Runner/FakeChatModel/JDBC与子JVMenv证明；各MC/rule独立。
- Error and edge behavior: fixture错误与stub错误分开；无真实LLM/IAM/PG/AS启用/GUI或外部runtime。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```text
Record actual component RED and selected GREEN commands, timestamps, failures, errors and skips.
Complete seventeen independent MC rows and ten literal Rule rows from final source/test evidence.
Trace five local aliases; preserve full T3-07 Partial and no automatic runtime/AS activation claims.
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

#### File 14 — `CREATE docs/egon/plan/2026-11-01-01-59-governed-adk-readonly-tools.md`

- Purpose: 锁定本次十四路径与代码前执行顺序。
- Symbols: 本Review计划/strict validator/traceability。
- Repository evidence: 原blueprint T3-07、已提交Recorder5574b5e。
- Dependencies and consumers: §6锁定API；本文件对应新/旧AR测试、bridge/plugin/executor链，最终由原factory消费。
- Why now: 最后审视全部14file marker/owner/RED与闭合API后strict。
- Contract/signature changes: 本Review计划/strict validator/traceability；除已锁overload/包内值外不改原接口/持久合同。
- Input/output and state mapping: legacy用户exact执行授权保持Review，不虚构Accepted；14path一个逻辑commit。
- Error and edge behavior: 若出现真正API/依赖/范围冲突回root，不能默认扩文件或运行。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术库/immutable值/qualified factory模式，无新业务层/依赖。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按本文件上述exact boundary/schema/identity/time/日志/Bean作用执行，N/A面见§12独立行。
- Implementation pseudocode:

```text
Resolve the effective blueprint hash and baseline5574b5e before production edits.
Validate exact fourteen declared paths, per-component RED barriers, privacy/context identity/event lock constraints.
Run validate_plan.py --strict and preserve Review status; hand off implementation only after root review.
```

- Verification contribution: TEST-001/002/003；证明本文件角色对应§6合同与§8选择回归，不扩大真实运行结论。
- After this file: 本组件经过自己的RED→GREEN/静态门禁后继续依赖链；尚未启用AS或外部runtime。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false ./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkToolTelemetryPrivacyTest,AdkReadOnlyDecisionLoopTest,AdkRunContextTest,JTokkitChatModelBudgetEstimatorTest,TianjiAgentRuntimeAutoConfigurationTest,ToolInvocationRecorderTest,ReadOnlyDecisionToolTransportTest,AdkContextWindowPlanTest,AdkContextSnapshotProjectorTest -Dsurefire.failIfNoSpecifiedTests=false`；`git diff --check`。
- Expected result: 各组件先缺失行为RED后选定10类全部GREEN；child false成功/true与absent和-D-only失败；无外部服务启动；实际计数由报告写。
- Failure returns to: fixture/compile问题回对应test；identity/privacy/result/event顺序失败回Bridge/Plugin/Executor；如需扩AS/SQL/依赖则回主代理，不静默扩scope。
- Completion criteria: 五别名、14paths、17MC/10Rule逐项有实际证据；single loop/once、privacy两值、admitted context、事件锁、默认关闭和旧ctor通过；独立审阅无阻断。
- Rollback: 仅回退本逻辑十四路径或forward-fix，保留既有Recorder journal/SQL历史；无需数据回滚，不启动系统。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacyTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridgeTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionLoopTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkToolTelemetryPrivacy.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkReadOnlyDecisionToolBridge.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java、README.md、docs/egon/evidence/2026-11-01-governed-adk-readonly-tools.md、docs/egon/plan/2026-11-01-01-59-governed-adk-readonly-tools.md
- Commit: `feat(agent): govern native read-only ADK tools`

## 8. Test, Validation, and Quality Gates

| Test ID | Working directory | Exact command/source | Proof | Required result | Owner/boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 selector的组件focused -Dtest | privacy/bridge/loop/context/provider缺失行为 | 最小scaffold后有效RED→GREEN；fixture/编译失败排除、stub Errors照实记录 | 主代理协调，各组件独立RED barrier |
| TEST-002 native safety | tianji-service | §7 10类完整selector | sync/stream、Sequential/Parallel、exact tool/context/Session、callback/default/codeexecutor拒绝、事件/结果/费用 | 无fallback/重试/双loop；仅known settled output；NONE只非空readonly scope | 真Runner/FakeChatModel、受控JDBC/Recorder，无真实LLM/IAM/PG |
| TEST-003 static/compat | tianji-service | `git diff --check`；14paths/V13sha/import审阅 | 原ctor/provider false不取、旧context/token规则、schema token增量、无秘密event | 无SQL/configYAML/AS/Components/estimatorSPI/transport/Recorder改动 | 根代理最终stage审查 |
| TEST-004 privacy child | tianji-service | 新AdkToolTelemetryPrivacyTest中的ProcessBuilder nested main | literal env和实际cached flag，10s finallykill | false PASS，true/absent/-D-only failclosed；无exporter/全局写 | 仅短子JVM，非长期runtime |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-01-59-governed-adk-readonly-tools.md --strict` | metadata/14file/trace/ManualChecks | exit0，不代表实施/运行已通过 | 文档owner |

## 9. Migration, Compatibility, Rollout, and Rollback

无迁移/codec/权限/依赖变化，旧SQL immutable。原constructors、CODE_FIRST/noTool与feature-off保持拒绝工具及UNKNOWN effect语义；仅非空已成功绑定readonly CONFIG scope得到NONE。无AS Bean/YAML启用，下一Step独立验证真实链。宿主programmatic使用需先初始化OTel并显式literal envfalse；禁止GlobalOTel mutation或-D替代。当前不启动模型/DB/服务器/GUI，后续用户自备真实环境验收；回滚只十四路径且保留journal/history。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/12/T3-07 | Step 1 | Bridge/Plugin/LoopTest | TEST-001/002 | exact native声明+唯一loop，已存Recorder消费 |
| PLAN-REQ-002 | §7/13/T3-07 | Step 1 | Plugin/ContextTest/LoopTest | TEST-001/002 | per-leaf identity admission/session/budget/无retry |
| PLAN-REQ-003 | §7/12/13 | Step 1 | Bridge/Executor/typed event sink | TEST-002/003 | outcome/accounting envelope，text/tool同锁revision，journal不抹 |
| PLAN-REQ-004 | §13.3 | Step 1 | Privacy/PrivacyTest/Bridge | TEST-001/004 | env+actualcached false双门禁；short子进程 |
| PLAN-REQ-005 | §6/17 | Step 1 | AutoCfg/旧ctors/estimator test/docs | TEST-002/003/005 | defaultoff/hidden关闭、兼容/范围与真实证据 |

## 11. Risks, Blockers, and User Decisions

| ID | Concern | Evidence/decision | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | clone context误拒/绕过 | fixedADK leaf clone/wrapper同invocationContext已由API owner核；按identity set逐个admit | 主代理 | Closed：同叶复用、分支独立、maxModelCalls有界 |
| DEC-002 | tracing缓存早初始化 | literal env与cached双核；AR只lazy Supplier，AS后续先OTel再构造 | 主代理/宿主 | Closed：不写全局状态，任何不符failclosed |
| DEC-003 | run event revision与结果输出 | 所有text/tool append同锁；Recorder先存事实；end失败抑制模型不派第二次 | 主代理 | Closed：只typed metadata/allowlist，无Recorder run event |
| RISK-001 | 私有ADK字段漂移 | 固定1.13字段/修饰符已核，未来升级可能不可读 | 宿主/后续升级 | Tracked：拒绝启用而非静默放开，child证明 |
| RISK-002 | 真实运行与AS接线尚未完成 | 当前只有AR/fake模型/短子JVM，Step12 AS配置/链readiness后续 | 用户/主代理 | Tracked：完整T3-07/平台Partial，不自动启动环境 |

## 12. Review and Acceptance

本计划只将已存在readonly Recorder受控挂载到既有ADK唯一loop；不创建第二执行/身份/预算平台。exact实例/context、双privacy门禁、single event revision锁与已知settled结果输出边界已闭合；十四路径、声明→各组件真RED→GREEN→相关回归→一个commit明确。原用户授权支持继续实现，计划保持Review，不虚构Accepted；运行/AS后续未交付不被当前计划PASS掩盖。

### 12.5 Blocking Manual Check

以下为计划级可执行性结论；实施报告必须按实际source/log逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/adk+autoconfigure边界，十四路径 | 无AS/Components/新层或estimator SPI | None |
| MC-REUSE-001 | Applicable | PASS | 原compileRoot/Runner/BaseTool/ToolRegistry/Recorder/typed transport/scheduler | 复用现唯一native loop和治理库，不复制HTTP/PDP/预算 | None |
| MC-DEP-001 | Applicable | PASS | 现ADK1.13/JDK/Spring/RxJava/JUnit | 不新增POM/依赖/工具框架 | None |
| MC-NAME-001 | Applicable | PASS | Bridge/Privacy/RunScope/ToolEventVO/Phase/Test | 技术角色与不可变值明确，事件不使用rawMap外露 | None |
| MC-VALID-001 | Applicable | PASS | fullrun/ref/backend/risk、identity tools/contexts、privacy与result accounting | 本层native/技术值边界逐项验证；无新增HTTP/CQE DTO | None |
| MC-MODEL-001 | Applicable | PASS | typed immutable ToolEventVO，immutable registration，fresh native实例 | record仅真值，identity登记有界/线程安全，事件不含私密数据 | None |
| MC-CONVERT-001 | Not applicable | N/A | 声明schema/固定event allowlist及结果envelope为本地技术协议 | 无实体转换或新Mapper层，原Jackson/typed域值复用 | None |
| MC-LOG-001 | Applicable | PASS | 旧executor/plugin现技术日志风格，bridge/privacy只安全异常 | 不加参数/output/nonce/bearer日志；不借新业务Service扩日志面 | None |
| MC-BEAN-001 | Applicable | PASS | 原@Bean tianjiAdkAgentRunExecutor、qualified ObjectProvider | 只追加现factory依赖，manual技术bridge不新业务Bean；无提前resolve | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK/ADK/schema/scheduler/Recorder，readonly reflection | 无Unsafe/自造token或线程平台 | None |
| MC-JSON-001 | Applicable | PASS | 原Jackson与native function schema；typed ToolEventVO固定allowlist | 不更descriptor reference/持久codec，不写参数或模型output到metadata事件 | None |
| MC-TIME-001 | Applicable | PASS | §4.9；原Recorder期限/预算不改，child10s wait/kill | 所有期限java.time，scope不延长原授权，child有界无长runtime | None |
| MC-CONFIG-001 | Applicable | PASS | 只Environment读取一新键，defaultfalse；无YAML/profile改动 | AS配置/strict注册/OTel init与readiness下一Step；不扩配置文件 | None |
| MC-PATTERN-001 | Applicable | PASS | BaseTool声明Adapter+原Plugin治理拦截+typed event sink | 隔离ADK调用变化点，不增加第二loop或通用执行层 | None |
| MC-SCOPE-001 | Applicable | PASS | §5精确十四路径；SQL/AS/Components/Recorder/transport不改 | 只AR程序化能力，原CODE_FIRST/hidden路径关闭 | None |
| MC-TEST-001 | Applicable | PASS | §8真实Native Runner/FakeChatModel+child env+原context/token/Recorder回归 | 计划规定组件独立真实RED与默认关闭兼容，实际结果由evidence填写 | None |
| MC-BLOCKER-001 | Applicable | PASS | 根代理最终API/identity/privacy/oneeventlock决定闭合，现class/factory callers已核 | 无未决设计；运行和AS接线限制显式 | None |

### 12.6 Final verdict

PASS — Ready for user review
