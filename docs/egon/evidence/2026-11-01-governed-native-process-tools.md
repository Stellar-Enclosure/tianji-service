# 原生只读与固定流程启动工具统一治理执行证据

| 项目 | 当前记录 |
| --- | --- |
| 执行计划 | [governed native process tools](../plan/2026-11-01-09-05-governed-native-process-tools.md)，Review |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7.2/11.3/12/13/17、T3-07 |
| 主设计实施前SHA-256 | `6f59b61ad9c183a7f3fb37c193798ef22b6f9b359946bd6641bc4da5064ecba7` |
| Baseline | `574aa3da76f612a109fcb9cf77c7839cc9d2c0cc`，实施前clean |
| 原GO/机械重锁 | root全文529行及旧RO/Plugin/Executor、Spec与strict/resources/checklist通过后对9ad发RED-only GO；逐行复核6块机械delta后，在任何功能代码前重锁4ef；Review不伪Accepted |
| 依赖fixture修订/最终锁 | root在测试编辑前批准唯一ContextTest路径，完整审阅4ef→a2bf（含File10/CLAR-005），再审仅2处twelve→thirteen计数；strict/hash PASS，最终plan SHA-256 `c7d46004c6876ff305bcac43ebd60faaf81ad0b99ff7d2a09da90d2ef73c40c9`，之后才修改该fixture |
| 最终结论 | **Verified（nonruntime，precommit snapshot）**；精确11类133PASS、0fail/error/skip |
| 提交 | PENDING；实际13paths均未暂存或未跟踪，最后文档/cached/Git由root执行，不预测commit hash |

## 1. 已验证范围与实现

精确13paths见计划§5/Step1：新common AdkGovernedToolBridge、shared AdkGovernedToolDeclaration、新AdkGovernedProcessToolBridge；原ROBridge/AdkRunBudgetPlugin/AdkAgentRunExecutor；新ProcessBridgeTest/ProcessLoopTest、原ROBridgeTest与必要依赖AdkRunContextTest；README、plan与本报告。6prod/4tests/3docs，root6、budget3、Processowner2、docowner2。root完整审阅所有6生产文件、4测试新文件/完整diff及最终README，逐项17MC/10Rules已核，无openfinding。

common Scope/typed event只传author/fullref/hostUUID/state/nullableepoch/account/error；获权access没有dispatchgeneration，epochnull，sink不伪造。无args/output/receipt/actionID/nativecallID/nonce/凭据。oldRO.bind/4arg/RunScope/ToolEventVO/Phase与spy兼容：RunScope直接implements commonScope，bindScope调用oldbind并返回同一实例；public5arg安全执行Runnable后转旧4arg。static bindResolved复用原RO执行，不重复fullvisible/hostresolve、无innerRO/第二registry。共享Declaration沿原closed primitive parametersJsonSchema/required/additionalPropertiesfalse，direct runAsync仍safe deny。

Process sole registry一次fullvisible与hostBindings，校全set/exactrun/fullref/backend/globalnames，READ_ONLY走static subset，IDW只STARTresource/required STRING instanceId/est0，AA和其他write拒绝。businessKey只snapshot提供；host resolver每visible START一次给canonical version/positive TTL<=7d，含START时单次DBnow供固定expiry floorMICROS(DBnow+TTL)，future且<=7d、不clamp runDeadline，不由model/native/run补身份或版本。ContextRequestVO/ContextVO/EventVO toString脱敏，privacy原gate保持。

valid START在STARTED成功后、紧邻Recorder前短锁mark UNKNOWN，填平metadata/APPLIED→access间freeze空窗；unused/quota拒绝/malformed/context无效/Recorder前STARTED失败不冒充副作用。HTTP前Runnable在scopeLock外再核context/tool/Session，再短锁open/admit；仅catch fence/admission为NOT_SENT并保heldsafeRunStop优先，delegate错误不假称未发送。锁不跨PDP/DB/transport IO；close立即冻结，不等IO，晚private/journal事实不能改frozen aggregate或重开events。

actual committed observer解出SUCCEEDED→CONFIRMED、knownnegative→NONE、UNKNOWN→UNKNOWN，业务UNKNOWN支配aggregate。accounting PENDING/settlethrow/outerpostcheck失败只阻model，不降已知businessfact；freeze在observer/account之间也保known。ownAttemptNULL才同hostUUID fresh V14原结果访问，nonnull/outcomeNULL不access/redrive。公开ids/rev/hash/typedOutcome/schema/SETTLED由桥核，fullref/sourceSHA与当前权由exactRequest/Store/Facade承担，无第二codec/parser。历史sourcecost null/nonzero保持，不当localfee；model只closed status/output(instanceId)或negative error，无receipt/cost/actionID。

Plugin在原admittedContexts锁内用唯一counter：先admitted context/exacttool/Session，再consume，先于args/events/Store/hostUUID；malformed/replay/meta/accessfail计额无refund，同entryaccess不二计，超额不碰Store/transport。真实Runnable再次核验不计。Executor一个cached common root，旧ctor/defaultoff/factorySAM/ROidentity保持，新增Process identity只match实际root。所有终态路径seal→close freeze→effect→submit，postclaim用Step18 fenceddeadline；REVrace reload terminal返回，非terminal DBexpired才旧wide UNKNOWN fallback，不用oldscope CONFIRMED覆盖新generation。preclaim/CODE_FIRST/hiddenhooks/Privacy原守卫不降。

ContextTest依赖修订只有private helpers：tools() local snapshot(2)并stub exact immutable estimator，5arg doCallRealMethod执行真实fence→旧4arg；默认snapshot()仍0。独审用Python核全部12个@Test bodies与HEAD逐字节相同（changedtests=[]），旧never/times/UUID/context断言未改，生产无bypass。root静态核全部23 tracked SQL与HEAD逐字节相同，plan c7d未改，diffcheck通过；无AS/config/schema/新依赖/实体/Converter/JSONparser/CRUD/MP迁移/AA/MCP，Agent generator excluded。

## 2. 实际验证与修订历史

| 日志/阶段 | 结果 | 时长/结束时间（2026-11-01 +08:00） | 原因/边界 |
| --- | --- | --- | --- |
| `/tmp/tianji-governed-native-bridges-red.log` | exit1 TEST-COMPILE | 17.423s；09:23:04 | ProcessTest Answer缺return，仅改一行return fail；不是有效RED，无行为计数 |
| `/tmp/tianji-governed-native-bridges-red-valid.log` | exit1；27=9PASS/4FAIL/14直接UOE ERROR/0skip | 16.036s；09:24:09 | RO15=旧9PASS+2fail4err；Process12=2missingguards fail+10err；当前stub缺行为，无编译/fixture阻断 |
| `/tmp/tianji-governed-native-readonly-green.log` | exit0；RO15PASS，0fail/error/skip | 20.631s；09:29:12 | commonEvent/shared/RO在有效RED后实现；Process增强测试只编译未选中 |
| `/tmp/tianji-governed-native-process-expanded-red.log` | exit1；Process20=0PASS/4FAIL/16直接UOE ERROR/0skip | 15.497s；09:30:31 | guards/AA stub及双线程freeze/fence/heldfail矩阵；root观察后才Process功能GREEN GO |
| `/tmp/tianji-governed-native-bridges-green.log` | exit1；35=18PASS/17ERROR/0FAIL/skip | 17.695s；09:34:41 | 实现暴露潜在purpose fixture错误：TIANJI_NATIVE_PROCESS_TOOL不满足既有TIANJI_TOOL_；改合法值，不放宽guard，另修siblings真实SUC/AA合法policyRef，不弱化断言 |
| `/tmp/tianji-governed-native-bridges-green-fixed.log` | exit0；37PASS=RO15+Process22，0fail/error/skip | 23.144s；09:40:11 | root读最终282source/599tests；只有此后才允许Loop编译协议plumbing/临时NOOP与创建测试，未提前counter/freeze |
| `/tmp/tianji-governed-native-core-red.log` | exit1；52=39PASS/13断言FAIL/0ERROR/skip | 22.238s；09:48:45 | RO15+Process22通过，Loop15=2PASS13FAIL；真实NativeRunner缺counter/dynamic effect/close/leasefenceddeadline/Session再次fence，非compiler/fixture；之后才core功能GREEN GO |
| `/tmp/tianji-governed-native-process-final.log` | exit1；133=131PASS/2ERROR/0FAIL/skip | 25.637s；09:55:52 | Loop17等通过，仅Context工具fixture quota0与4arg stub不匹配新合同；必要依赖修订非生产bug，未放行quota0/弱化断言 |
| 范围修订/fixture GO | root先批唯一测试路径，再读amendment/2计数行并锁c7d，之后才helper edit | 最终重跑前 | 13paths=6prod4tests3docs；原11类selector/flags不变，原9ad/4ef事实不覆写 |
| `/tmp/tianji-governed-native-process-final-fixed.log` | exit0 BUILD SUCCESS；133PASS/0FAIL/ERROR/skip | 28.657s；10:02:46 | 精确11类完整source-reactor成功；仅输出文件suffix获批以保留原131/2日志，非命令/覆盖面变更 |

最终分类：AutoCfg6、ROBridge15、NewLoop17、TxnBoundary1、oldROLoop10、Privacy3、RunContext12、ProcessBridge22、StartRecorder23、AccessRecorder16、Deadline8，共133PASS。PG未选中，0skip不是PG通过。Privacy3实际包含受控child JVM，直接Session路径用InMemorySession/真实context与ContextHistory；ADK NativeRunner/FakeChatModel/controlled Ledger与Recorder不等真实LLM/HTTP/IAM/crypto/DB。

原GO plan SHA为`9ad08df5b49c0f1a8f795ba6b1eae20059e19e775c2d0e53d9e25719fb18cf29`；机械重锁SHA为`4ef707a52fbde9fe28b329bd31e1bc3559cb2056c5082e0b8a0d881a224ea8f7`，依赖amendment SHA为`a2bfe758259ae0acbc6e56d29c8afccb5c6cb0e4356c7556c2db31339999b0b5`，最终c7d见上表，分别保留，不伪称字节/范围从未改变。原9ad及4ef文档备份在/tmp供审阅，不是repo harness。最终命令由root执行，docowner未Maven；cwd为tianji-service：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=AdkReadOnlyDecisionToolBridgeTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,TianjiAgentRuntimeAutoConfigurationTest,AdkToolTelemetryPrivacyTest,AdkRunContextTest,AdkExecutionTransactionBoundaryTest,ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest,AgentDeadlineEffectTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-governed-native-process-final-fixed.log 2>&1
```

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | 一full registry/hostresolve、exact refs/backend/names、same oldScope/spy与共享声明，RO15/Process22/旧loop回归 | programmatic core，AS装配未交付 |
| PLAN-REQ-002 | Satisfied | snapshot业务key/hostversion/TTL与DB µexpiry、不clamp、不受nativeID/run影响，typedcontext脱敏 | hostversion不是PS对象DB CAS证明，无模型身份覆盖 |
| PLAN-REQ-003 | Satisfied | 单entrycounter含invalid/meta/accessfail，Session再次fence，freshSTART/V14访问ids/schema/localSETTLED及receipt/cost/intent私密字段不进入model | 原结果成本不被本地费归零，UNKNOWN不redrive |
| PLAN-REQ-004 | Satisfied | 业务fact/accounting二维、Recorder前UNKNOWN/freeze gap、latefact/所有终态seal-freeze/fenceddeadline与generation race，Loop17 | 终态不refine，真实DB/远端运行未验收 |
| PLAN-REQ-005 | Satisfied | 有效RED/fixture历史完整、最终133PASS、Context12 bodies不变、13scope/23SQL/独审 | 最后cached/Git由root执行，全T3-07/Goal Partial |
| 完整T3-07/Goal | Partial | 本Step AR native core源验证完成 | ASassembly、AA/MCP及真实IAM/PG/模型验收后续 |

## 4. Blocking Manual Check

以下为已验证precommit snapshot；root逐项审阅及独审无openfinding，最后文档/cached/Git交付仍待执行，不把受控native测试等同真实服务验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR adk/common桥与现单NativeRunner，13paths | 现Agent技术结构/JDBC/Flyway保留，无AS/config/新层或引擎 | None |
| MC-REUSE-001 | Applicable | PASS | static RO resolvedhelper/原RunScope/共享Declaration、Recorders/Observer/V14/CAS | 无innerRO/第二registry/loop/parser/transport；复用实际扩展点 | None |
| MC-DEP-001 | Applicable | PASS | 现ADK/RxJava/JDK/contextAwareIo/typedports，POM无改 | 无依赖/框架/脚本/模型层新增 | None |
| MC-NAME-001 | Applicable | PASS | common/Process/Declaration、Context RequestVO/VO与typedEvent | roles明确，records为不可变值，toString脱敏，无业务entity | None |
| MC-VALID-001 | Applicable | PASS | refs/risk/hostkey-version-TTL/ctx-tool-session/freshbody及配额正反测试 | 不借model权限；只predelegate fence映NOT_SENT，heldstop优先 | None |
| MC-MODEL-001 | Applicable | PASS | 原ports/VO/SQL不改，immutable context/metadata与closed model envelope | 不输出receipt/cost/actionID或造持久实体/软删除状态 | None |
| MC-CONVERT-001 | Not applicable | N/A | 现typedOutcome/metadata适配与精确schema提取 | 无entity/DTO通用转换层，sourceSHA归Store/Facade，不新Mapper/parser | 沿现模型边界 |
| MC-LOG-001 | Not applicable | N/A | 未新增业务日志，serialized typed sink只allowlist metadata | 无args/output/nativeID/nonce/credential泄露，lateevent不重开终态 | 无日志注入改动 |
| MC-BEAN-001 | Not applicable | N/A | programmatic technical bridges/现ctor，AS factory与autoconfig未改 | 无新业务Bean/Qualifier/配置激活，不伪Lombok DI | 沿现技术模式 |
| MC-UTIL-001 | Applicable | PASS | 原JDK identity/lock/Runnable/Instant/Duration与existing scheduler/recorders | counter复用原锁，scopeLock无IO，无自建工具/线程/认证框架 | None |
| MC-JSON-001 | Applicable | PASS | closed parametersJsonSchema/typedOutcome/事件与model白名单 | 无ordinal、新JSONparser或越层codec，原字符串状态合同保持 | None |
| MC-TIME-001 | Applicable | PASS | START bind DBnow/TTL µfloor、原run/callwindow、Step18fenceddeadline | 不clamp intentTTL/延harddeadline；freeze不等IO，latefacts不改终态 | 真PG映射/锁仍未验收 |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/flag/AS配置/权限变化；旧defaultoff/Privacy gate | 不自动启用，literal env与cachedTracing约束不降 | 无配置面改动 |
| MC-PATTERN-001 | Applicable | PASS | 窄Adapter+既有Facade/Observer/UnitofWork/CAS | RO/union真实变化点与scope生命周期，无额外Strategy/状态引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | root精确13paths/23SQL bytecompare，Context仅批准helper | 6prod4tests3docs，无Components/AS/schema/依赖/AA/MCP扩大 | 最后cached范围由root复核 |
| MC-TEST-001 | Applicable | PASS | 三组件有效RED、完整失败/fixture历史、133PASS、Context12 bodies unchanged | NativeRunner/FakeModel/childJVM受控；0skip非PG验收，断言不弱化 | 真实环境待用户验收 |
| MC-BLOCKER-001 | Applicable | PASS | root全文/17MC10Rules分别审阅、各独审无openfinding、strict/diff检查通过 | source/test/static闭合，Review非Accepted，全Goal仍Partial | 最后docs/cached/commit由root执行 |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | common/Process/Declaration真实职责，ContextVO/EventVO明确 | source/constructor/脱敏测试 | 技术接口与不可变值命名一致，无实体风格漂移 | None |
| Rule 2 | Applicable | PASS | refs/hostcontext/typedbody/ctx-tool-session/配额fence校验 | RO15/Process22/Loop17/Context12正反 | 原域与technical boundary guards，未新增HTTP层/伪权限 | None |
| Rule 3 | Applicable | PASS | immutable records/原VO保持，未新建实体或Converter | source与旧接口/fixture兼容测试 | record豁免用于值对象，无宽松业务DTO替换 | None |
| Rule 4 | Not applicable | N/A | manual技术桥/现Executor，无新业务Bean/DI/log | 13scope与imports/配置检查 | 不伪造Lombok注入或无关日志 | 沿现technical构造模式 |
| Rule 5 | Applicable | PASS | 现JDK/ADK/RxJava/Recorders/scheduler复用 | POM/import/static | 无新utility/依赖或框架 | None |
| Rule 6 | Applicable | PASS | 原closed native schema/typedOutcome/字符串状态 | schema/directdeny/模型事件隐私 | 无ordinal/新JSONparser，源成本精度保持 | None |
| Rule 7 | Not applicable | N/A | 无环境配置/flag改变 | 旧AutoCfg6/defaultoff/Privacy3回归 | 无key parity或自动激活变化 | None |
| Rule 9 | Applicable | PASS | Adapter/单loop/Facade-Observer-UoW-CAS与scopefreeze | 并发freeze/partialfailure/heldfence/generation测试 | 选用实际变化点所需模式，无新工作流引擎 | None |
| Rule 10 | Applicable | PASS | DB Instant/DurationTTL/MICROS与原绝对窗口 | TTL/freeze/DBdeadline/oldgeneration边界 | 无java.util时间或reset，业务effect与费用待定分离 | None |
| Rule 11 | Applicable | PASS | Agent现结构/JDBC/独立Flyway/原事件路径，generator excluded | 23SQL相同、13scope、Context12旧test bodies不变 | 无CRUD/新层/MP迁移/softdelete/依赖或事件机制改造 | None |

## 6. 结论与未验证边界

本Step达到**Verified（nonruntime，precommit）**：5aliases Satisfied，17MC/10Rules逐项PASS或证据支持N/A，root已读end checklist。独审native_start_inventory覆盖common/RO/shared与Process及测试；tool_audit_contract覆盖Plugin/Executor与新17Loop；budget_lock_tests核Context fixture与12@Test bodies字节，均PASS，无openfinding。plan最终c7d保持，原GO/机械重锁/13path依赖修订已分别记录；当前unstaged/untracked，最后root文档/cached/Git待执行，不预测SHA。

业务UNKNOWN不自动重派，accountPending阻model而不抹已提交CONFIRMED/NONE；scopefreeze后的private/journal事实不能改终态。programmatic AR core完成源验证，不等AS配置启用、AA审批消费、MCP或完整平台完成。

无真实PG/IAM/PDP/OAuth/JWT crypto/HTTP网络/LLM验收，未启动app/server/DB/容器/listener/浏览器/GUI或生产调度器；有限测试的NativeRunner、控制线程及child JVM随测试生命周期关闭、DDL apply/repair/backfill/drop、权限激活/角色grant、push/PR/release。无repo scratch/scripts/work/harness新增，测试保留subsystem；/tmp仅文档审阅备份/日志。docowner仅定稿evidence，未改plan/source/tests/README、未构建或提交；完整T3-07/Goal仍**Partial**。
