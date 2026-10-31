# T3-07 受治理ADK只读工具执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度02:09—02:25 |
| 执行计划 | [ADK只读工具核心接线](../plan/2026-11-01-01-59-governed-adk-readonly-tools.md)，Review；主代理全文审阅、strict/preflight通过后实施 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `eb2414f219d61374c04873a20577c4c1436d603d3715aaf7cc95be58b308b2b7` |
| Baseline | `5574b5ea1b1a9226de4c3dbcff41b4e7e90efe50` |
| 当前结果 | final选择75项+额外兼容10项，共85项不同测试通过；0 failures/Errors/skips；完整T3-07/平台Partial |
| 提交 | 主代理确认本报告与精确14路径stage后产生；hash以Git及最终交付为准 |

## 1. 实际实现与边界

精确14路径：新增AR的AdkReadOnlyDecisionToolBridge、AdkToolTelemetryPrivacy及三项测试；修改AdkAgentRunExecutor、AdkRunBudgetPlugin、TianjiAgentRuntimeAutoConfiguration与原AdkRunContextTest/JTokkitChatModelBudgetEstimatorTest/TianjiAgentRuntimeAutoConfigurationTest；README、本计划和本报告。AS、YAML、SQL、Components、estimator SPI、transport、Recorder、权限和依赖未修改。

Bridge复用ToolRegistry.visible(snapshot)，防御复制最多256注册项，host binding与可见readonly集合按fullrun/exact ref/backend/risk逐项1:1，拒绝缺/多/重复/漂移及LEGACY/CODE_FIRST。每run创建fresh BaseTool，并以IdentityHashMap限定实例；同名/克隆工具不获得能力。声明通过实际ToolConverter支持的parametersJsonSchema表示primitive/required/closed schema，parameters absent，additionalProperties=false；原BaseTool.runAsync永远安全拒绝，不成为隐藏执行fallback。

Plugin只在全部原MODEL授权、Session/context/模型预算检查成功后登记exact InvocationContext。Callback/ToolContext wrapper可以不同，但必须同一已admit leaf对象；Sequential/Parallel的clone逐个登记，同叶round可复用，有界数量≤maxModelCalls。工具前核native app/user/session/invocation/agent与membership、exact tool及Session fence，Plugin随机host UUID而非native functionCallId；使用原contextAwareIo scheduler，无tool retry，不占control/dispatch executor。scoped IdleGuard注册与admit在同一close锁原子段，close后不重新登记。

Bridge.execute依次normalize→typed START→原Recorder.executeReadOnly→typed FINISH→safe envelope。只有SUCCEEDED+matching schema outcome+Accounting SETTLED返回output；REJECTED/FAILED_KNOWN+SETTLED只返回status/errorCode；UNKNOWN/DISPATCHING/nulloutcome/任何PENDING失败封闭，不Maybe.empty/空成功/重派。STARTED state=REQUESTED。Recorder无RecordResult异常记FINISHED/UNAVAILABLE；typed epoch0仅是无结果sentinel，公共payload省略其dispatchEpoch，不伪称数据库epoch为0。

Executor保留全部旧ctors，仅追加lazy Supplier并cache exactly one bridge，identity helper可核实际对象。CONFIG claim/material/lease后bind，再用原compileRoot接声明列表；每leaf exact实例与六canonical model/tool callbacks、toolsets、model defaults、code executor及未审Agent类型均严格核验，CODE_FIRST工具仍关闭。成功绑定非空全readonly CONFIG scope在成功/失败/取消上派生NONE；legacy/noTool正常执行仍保留UNKNOWN，未放宽原已知未调用失败的NONE语义。

全部text与safe typedtool事件共用RunEventSink同一synchronized append/get-revision/set。tool payload只含完整toolRef、hostInvocationId、state及可用的epoch/accounting/error，author为独立event字段；无args/output/native callId、nonce及其hash、bearer或receipt。正常blocking返回后、catch入口及finally在同一append锁调用seal，完成run前封闭event流。真实RED证明取消与parallel失败后late FINISHED曾改变终态revision；修复后晚到event拒绝不擦除已持久journal、不重派工具，endappend失败抑制模型值。

Privacy.requireSafe在构造、bind及run execute entry要求literal env恰为false，并只读真实com.google.adk.telemetry.Tracing缓存字段：exact boolean/private static final、trySetAccessible、getBoolean=false；missing/type/modifier/access/linkage/value/env漂移全部封闭且无私密cause。无Unsafe/field mutation/GlobalOpenTelemetry.set/reset/setTracerForTesting/-D/YAML替代。7个实际短子JVM覆盖false通过、true/absent/-D-only/FALSE/前空格/garbage拒绝；每个wait≤10秒且finally destroy，无exporter或服务启动。

原executor Bean只加@Qualifier(tianjiAdkReadOnlyDecisionTools) ObjectProvider；新Environment读键stellar.tianji.agent.tools.read-only-decision.enabled默认false，关闭时从不取provider，开启时仅保存provider::getObject，不提前触Tracing。AR提供programmatic能力；AS factory按OpenTelemetry类型先初始化、实际链readiness及配置启用是下一Step，不声称当前已接。原估算器已计native tools schema，本步只增加token增量断言，无SPI/options wrapper放宽。

本次V13实际SHA-256仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN与命令

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据与错误分类 |
| --- | --- | --- |
| 初次组合运行 | 02:09:00；32项、8 failures/5 Errors/0 skips；exit1，18.959s | `/tmp/tianji-native-loop-red.log`；Context新增5场景含nested Mockito fixture错误，不能作为这些场景的有效RED。AutoCfg6项中1项lazy exact identity缺失断言失败；Estimator6项通过。Loop9项6 failures/1直接bind stub Error；各组不混作全部有效 |
| Context中间复跑 | 02:10:14；11项、1 failure/3 Errors/0 skips；exit1，16.044s | `/tmp/tianji-native-context-valid-red.log`；foreign context场景仍有fixture错误，虽文件名含valid，也不把该错误计为有效RED |
| Loop+Context有效RED | 02:11:42；20项、7 failures/3直接stub Errors/0 skips；exit1，15.009s | `/tmp/tianji-native-loop-valid-red.log`；Loop9项6 failures/1直接bind stub Error；Context11项1 failure/2直接FEATURE错误，无fixture错误 |
| Bridge+Privacy+Context有效RED | 02:15:43；24项、6 failures/9直接stub Errors/0 skips；exit1，15.007s | `/tmp/tianji-native-bridge-privacy-valid-red.log`；Bridge9项1 failure/7 Errors，Privacy3项2 failures，Context12项3 failures/2 Errors；均为缺失行为而非编译/fixture错误 |
| 晚到event seal有效RED | 02:22:18；31项、1 failure/0 Errors/0 skips；exit1，19.481s | `/tmp/tianji-native-seal-red.log`；Bridge9/Privacy3/Context12/AutoCfg6已通过，仅新增Loop方法失败；assertAll的cancel与parallel两路径均复现late tool event改变terminal revision，无fixture错误 |
| 最终计划选择GREEN | 02:23:52；75通过、0 failures/Errors/skips；exit0、BUILD SUCCESS，26.043s | `/tmp/tianji-native-readonly-final.log`；包含same-lock seal与close/admit/IdleGuard原子化，Loop共10方法 |
| 额外原兼容GREEN | 02:25:12；10通过、0 failures/Errors/skips；exit0、BUILD SUCCESS，15.962s | `/tmp/tianji-native-readonly-compat.log`；与75项为不同测试，合计85通过；未选择PG测试 |

cwd=tianji-service；实际final命令使用JDK25、同一global/user settings、literal envfalse和关闭OTel exporters；shell续行均为单反斜线：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkToolTelemetryPrivacyTest,AdkReadOnlyDecisionLoopTest,AdkRunContextTest,JTokkitChatModelBudgetEstimatorTest,TianjiAgentRuntimeAutoConfigurationTest,ToolInvocationRecorderTest,ReadOnlyDecisionToolTransportTest,AdkContextWindowPlanTest,AdkContextSnapshotProjectorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-native-readonly-final.log 2>&1
```

同一前缀的额外兼容SELECTOR为AdkExecutionTransactionBoundaryTest,AdkEventProjectorTest,AdkSessionSourceHistoryTest,TianjiModelBudgetAutoConfigurationTest，输出至/tmp/tianji-native-readonly-compat.log。RED同前缀依次选Loop+Context+JTokkit+AutoCfg、Context、Loop+Context、Bridge+Privacy+Context；seal RED选Bridge+Privacy+Context+AutoCfg及Loop新增单方法。各实际计数以日志表为准，不把fixture错误/直接stub Errors改写为0 Errors。

| 实际测试组 | 通过数 | 证明范围 |
| --- | ---: | --- |
| Bridge9、Privacy3、Loop10 | 22 | exact binding/实例/schema、7 child probes、真实native sync/stream/多leaf/hidden路径/late seal |
| Context12、Estimator6、AutoCfg6 | 24 | per-leaf admission/Session/close原子、schema token增量、旧ctor/defaultoff/lazy qualified identity |
| Recorder15、Port8、ContextWindow5、SnapshotProjector1 | 29 | 原持久readonly/port/context兼容 |
| TransactionBoundary1、EventProjector2、SessionSourceHistory5、ModelBudgetAutoCfg2 | 10 | 额外既有事务/事件/Session来源/模型预算自动配置回归 |
| 合计 | 85 | 14类不同测试，0 failures/Errors/skips；不是全平台运行验收 |

真实ADK Runner/官方模型桥与FakeChatModel用于观察typed工具结果进入第二模型请求；Recorder/ledger/外部身份边界为受控fixture。7个子JVM直接读取真实ADK缓存与环境，不替代真实exporter、网络/密码学或模型质量验收。没有选择或运行PG测试，0skip不能解释为PG已通过。主代理已全文核生产差异/tests/README，两个独立审阅分别核Executor/Plugin与Bridge/Privacy无其他阻断；最终文档和stage门禁见MC-BLOCKER。

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 唯一loop/固定工具 | Satisfied（本步） | Bridge9/Loop10：exact binding/实例、runAsync拒绝、真实sync/stream一次typed结果到第二model request | 无真实业务网络/AS配置验收 |
| PLAN-REQ-002 模型/Session/预算治理 | Satisfied（本步） | Context12/Loop10：逐leaf identity、陌生context/tool拒绝、bounded集合、Session fence、close原子与IO/no retry | 不放宽原MODEL治理或费用政策 |
| PLAN-REQ-003 事实/模型输出/event一致性 | Satisfied（本步） | known settled envelope、safe metadata/原Recorder15；one revision锁+新增late seal RED→GREEN、end失败不重派 | 控制fixture下验证，不声称真实PG/全链运行 |
| PLAN-REQ-004 遥测内容隐私 | Satisfied（本步） | Privacy3包含7实际child probes与cached field drift；constructor/bind/execute复核、无全局写 | host实际OpenTelemetry初始化与AS组合下一Step |
| PLAN-REQ-005 兼容/边界 | Satisfied（本步） | AutoCfg6、Estimator6、额外旧10；defaultfalse/lazy/旧ctor/hidden拒绝；14路径/V13固定 | AR core only，写审批与AS配置未交付 |
| 完整T3-07/平台蓝图 | Partial | 当前AR受控只读能力 | AS实际接线、写dispatch/审批消费、真实IAM/crypto/PG/模型与平台后续仍未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR/adk与原autoconfigure实际diff、精确14路径 | 无AS/Components/新层/estimator SPI，原模块方向保持 | None |
| MC-REUSE-001 | Applicable | PASS | ToolRegistry/Recorder/transport/compileRoot/Runner/contextAwareIo | 声明Adapter与Plugin组合现治理，无重复HTTP/PDP/预算引擎 | None |
| MC-DEP-001 | Applicable | PASS | 现ADK1.13/JDK/Spring/RxJava/JUnit及全部imports | 无POM/依赖/工具框架新增 | None |
| MC-NAME-001 | Applicable | PASS | Bridge/Privacy.requireSafe/RunScope/ToolEventVO/RunEventSink/Test | 技术角色与不可变值明确，事件边界采用typed值，工具参数Map在准备入口规范校验 | None |
| MC-VALID-001 | Applicable | PASS | fullrun/ref/backend/risk、exact实例/context/Session/原Recorder及result accounting | Bridge9/Context12/Loop10正反及真实native循环；声明不授执行权 | None |
| MC-MODEL-001 | Applicable | PASS | immutable registry/typed event，fresh BaseTool/IdentityHashMap、有界admittedContexts | true value record、防御复制、close清理和原子admission；无secret事件 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed域值与native schema、本地固定envelope/metadata投影 | 无实体转换或新Converter层，不复制原codec/Mapper | None |
| MC-LOG-001 | Applicable | PASS | 现executor/plugin技术日志风格；新边界安全固定异常 | 无参数/output/nonce/bearer日志或私密cause；typed值脱敏 | None |
| MC-BEAN-001 | Applicable | PASS | 原tianjiAdkAgentRunExecutor Bean；@Qualifier(tianjiAdkReadOnlyDecisionTools) ObjectProvider | factory只追加lazy Supplier；defaultfalse不取，旧ctors保留；无新业务Service | None |
| MC-UTIL-001 | Applicable | PASS | readonly反射/JDK进程/原scheduler/Schema/Recorder | 无Unsafe、field mutation、全局OTel写或新工具平台 | None |
| MC-JSON-001 | Applicable | PASS | parametersJsonSchema/parameters absent/additionalProperties=false；native typed tool response | tool event固定allowlist，无参数/output/nativeCallID；UNAVAILABLE省略epoch | None |
| MC-TIME-001 | Applicable | PASS | 原Recorder/Session期限不改；java.time；7 child probe单次10s wait+finally kill | close/admit/IdleGuard原子，event seal同锁；晚到事实不因event拒绝丢失 | None |
| MC-CONFIG-001 | Applicable | PASS | 单Environment新读键read-only-decision.enabled defaultfalse；AutoCfg6项 | 无YAML/profile修改、不提前resolve/Tracing init；AS factory/readiness另步 | None |
| MC-PATTERN-001 | Applicable | PASS | 声明BaseTool Adapter+原Plugin治理+原Recorder+typed event sink | 真实Runner sync/stream唯一loop，无runAsync fallback/tool retry/第二引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | root最终14scope/diffcheck；本次V13实测f0c642…26b3 | 无SQL/AS/Components/Recorder/transport/依赖/配置文件扩张 | None |
| MC-TEST-001 | Applicable | PASS | 有效RED分类、seal新增RED；final75+compat10不同测试均通过 | 85通过/0fail/0error/0skip；PG未选，7实际短child probe非真实服务验收 | None |
| MC-BLOCKER-001 | Applicable | PASS | root全文生产/tests/README与本报告复核；两独立审阅无阻断；85项回归与精确14路径scope/diffcheck通过 | 本步所有适用MC与Literal Rule闭合；按显式14路径清单执行提交，不把平台未交付范围认作已完成 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Bridge/Privacy/RunScope/ToolEventVO/RunEventSink与对应Test | 实际编译及85项不同测试通过 | 技术角色/值命名明确 | None |
| Rule 2 | Applicable | PASS | fullrun/ref/backend/risk、exact tool/context/Session、safe envelope/accounting | Bridge9/Context12/Loop10及原Recorder15 | 模型输入不替代受信能力；缺失/漂移拒绝 | None |
| Rule 3 | Applicable | PASS | immutable registry/typed event/identity scope，现domain typed值复用 | fresh instance/metadata/redaction/closed-scope测试 | 真value record，无普通实体record或新手工Converter | None |
| Rule 4 | Applicable | PASS | 原named factory/qualified lazy provider与既有technical ctor/log风格 | AutoCfg6、旧ctors/原兼容10及diff | 非新业务Service，不伪称新增Lombok DI；无secret日志 | None |
| Rule 5 | Applicable | PASS | 原JDK/ADK/Rx/Spring/context-aware scheduler、readonly反射 | imports/POM审阅与7 bounded child probes | 无Unsafe/utility/依赖框架新增 | None |
| Rule 6 | Applicable | PASS | 原ToolConverter支持的parametersJsonSchema与typed函数响应、metadata allowlist | schema与native loop/event断言 | 不改descriptor/持久codec，不写参数/output到tool metadata | None |
| Rule 7 | Applicable | PASS | 仅新Environment读取defaultfalse，无YAML/profile修改 | AutoCfg默认关闭/lazy identity/provider测试 | AS真实注册与readiness下一Step，未自动启用 | None |
| Rule 9 | Applicable | PASS | BaseTool声明Adapter+Plugin治理+原Recorder，ONE event sink/monitor | 一次loop/unknown抑制/parallel/cancel/late seal | 真实变化点复用，未造第二执行循环 | None |
| Rule 10 | Applicable | PASS | java.time、原期限/Session fence、child wait与finally清理 | 时间/Session/取消测试及7真实短JVM | 无期限放宽；晚到事实与终态event封闭分离 | None |
| Rule 11 | Applicable | PASS | 原AR/adk/autoconfigure十四路径；V13固定 | 85相关回归、最终root scope/diffcheck | 无新层/AS/SQL/Components/Recorder/transport/deps扩张 | None |

## 6. 未验证与最终门禁

真实LLM、IAM/IdP/PDP/JWKS/crypto、HTTP网络、PostgreSQL和AS链readiness/配置启用未验收。当前是AR programmatic接线及default-off lazy依赖能力；没有写工具dispatch/approval消费/平台全闭环。native event seal保留迟到journal事实，但不声称本步新增PS规则级持久provenance。

未自动启动应用、数据库、容器、浏览器/GUI、native scheduler或长期runtime；未注册/激活权限、授角色、部署、push、PR或release。无新仓库scratch/scripts/work脚本。短测试子JVM有界并清理，不等于启动服务。

报告作者只编辑plan/evidence，未改README/source、未构建或提交。主代理已完成实际source/相关回归、独立review及本报告核对；精确14路径stage/commit在本报告定稿后执行，hash以Git记录为准，不预填提交事实。

**PARTIAL — Spec requirements are unmet or unverified**：本步AR只读能力85项不同受控回归通过；AS接线及真实运行与完整T3-07/平台仍未完成。
