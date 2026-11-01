# 默认关闭的受控流程工具统一装配执行证据

| 项目 | 当前记录 |
| --- | --- |
| 执行计划 | [governed process tool assembly](../plan/2026-11-01-10-15-governed-process-tool-assembly.md)，Review |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/11.3/12/13/17、T3-07 |
| 主设计实施前SHA-256 | `1b55dd1bd97ada9eabad12fe1520fb78c4a06950aa178b22b04f8990611bf66f` |
| Baseline | `4c5e95741c02b0c982c769be92db814633f76c15`，实施前clean |
| 计划与Source GO | root全文554行、Spec重读、strict/资源15md19refs与18md24refs/checklist及hashassert通过后发RED-only GO；plan SHA-256 `a1a0681e6a1775cf3947a29cb7682da6e9aed2a6b61ed3d4471f7fab7ff72aa6`冻结不变 |
| 最终结论 | **Verified（nonruntime，precommit snapshot）**；精确11类159PASS、0fail/error/skip；source/独审/手工/static已闭合 |
| 提交 | PENDING；最后root文档/cached/staging/Git交付待执行，不预测hash，Review不伪Accepted |

## 1. 已验证范围与源码事实

精确14paths见plan§5/Step1：AR RuntimeAutoConfiguration/Test；新AS GovernedProcessToolProperties/Test、GovernedProcessToolConfiguration/Test；旧AS ReadOnlyDecisionToolConfiguration/Test；AR GovernedProcessToolBridge/Test（仅resolver affinity）；AS唯一application.yml、README、plan与本报告。5mainJava/5tests/YAML+3docs，root3、budget5、tool_audit4、doc2。root全文5prod/5tests、新cfg261行/测试524行、README/YAML，逐项17MC/10Rules复核，end checklist已读，无openfinding。

Props的governed-process prefix默认false；concurrency0→4且1..8，decision/start typedlists各immutable<=256，enabled时bothnonempty。复用现BindingVO/BackendVO timeout100ms..30s，不复制descriptor/globaltimeout/identity/context。stateless EnvironmentAware逐list/index.backend与.tool独立NoUnbound重绑，覆盖root/deep/secondindex未知值，toString脱敏。第13项实际YAMLloader验证在原12项RED后新增，不伪称其先前RED。

两mode互斥固定TIANJI_TOOL_RUNTIME_MODE_CONFLICT；AR factory在自身Verifier/contexts/provider前先guard，bothfalse不get两provider，oldonly旧lazy、新onlyqualifiednewlazy。新provider位于旧last ROprovider之前，原lastQualifier/Executorctor保持。old/new AS每ownedfactory先prereq/conflict，6前置api/storage/execution-grants/execution-authorization/runtime/tools.authority，discovery非前置；不承诺全Spring共享Bean依赖绝不先初始化，不新增BFP。

新11distinctBean为governedProcessToolInvocationStore、governedProcessToolInvocationRecorder、governedProcessToolResultAccessStore、governedProcessToolResultAccessRecorder、tianjiGovernedProcessDecisionBindings、tianjiGovernedProcessStartBindings、tianjiGovernedProcessHttpClient、tianjiGovernedProcessDecisionTransport、tianjiGovernedProcessStartTransport、tianjiAdkGovernedProcessTools、tianjiGovernedProcessToolRuntimeChainVerifier。Prep/InvRecorder各自实际verifyV13、AccessStore实际verifyV14，AccessRecorder exactstore/ledger，无fake readybool/自动DDL。测试观察3个真实Java Verifier对象，catalog/DataSource受控，不是真实PG。

双profile与actualgrantbindings的DMN RO和IDW START fulltuple slots分别exactall匹配，拒missing/extra/duplicate/drift及AA START；只选actualprofiles unionrefs给nativebridge，不误暴露其他authority descriptor。hostresolver仍原grantBindings.resolve完整函数，不filter/split；auth层原逻辑可独立核当前bindings。未新增authority/policy注册/角色grant。

host必须提供稳定qualified tianjiToolStartActionContextResolver；Lazybridge mandatory getObject，无fallback或config默认run/version/hash。core仅新增usesActionContextResolver pointerhelper，不执行resolver或改protocol。type OpenTelemetry provider.getIfAvailable先bridgector；缺OtelBean可由host既有agent/programmatic初始化，但Bean不是Globalready证明，不Global.set/reset/env变更，原privacy gate保持。

Lazy(false) SmartInitializingSingleton在globalLazy下仍强制actualtypedStores/Recorders/bridge/worker，重新取resolver并校pointer；missing/ambiguous/prototype/foreign拒绝。source/ledger/cap/toolPDP/runAuthorizer/两个transport各6helper/bridge与worker实际root全核，不用unusedparallelBean代替。sharedownedHTTP NEVERredirect/connect3s/destroy shutdownNow；两transports exact props.concurrency，各close自有pool，不close caller IAM/OAuth/issuer。

唯一baseYAML只新增enabledfalse/concurrency4/decision-bindings[]/start-bindings[]四defaults，无真实profiles/权限启用。旧7Bean RO链仍V13only，新union需V14；hostbusinessVersion不是PS对象DB CASproof，出站实际SERVICE不是USER OBO。root核全部23 tracked SQL与HEAD逐字节相同；V13 f0c642...26b3、V14 888768...3760a保持。无library/authority/Stores/Recorders/协议/schema/依赖或AA/MCP改变，Agent generator excluded，沿现JDBC/独立Flyway。

## 2. 实际验证记录

| 日志/阶段 | 实际结果 | 时长/结束时间（2026-11-01 +08:00） | 分类与边界 |
| --- | --- | --- | --- |
| Source GO/编译前置 | root在全文门禁后向budget5/tool_audit4发RED-only GO | Maven前 | baseline仅两docs；newAScfg先只11@Bean signatures/UOE，未构造owned资源，新AS tests等Props/helper GREEN后才进入 |
| `/tmp/tianji-governed-process-modes-red.log` | exit1；31=28PASS/3FAIL/0ERROR/skip | 19.894s；10:33:19 | AR8旧6PASS+新mode/dualguard2FAIL；Bridge23旧22PASS+resolver恒false1FAIL；dualguard刻意禁DS，缺earlyguard触Verifier→CannotCreateTransactionException是目标缺行为，非无关环境错误 |
| `/tmp/tianji-governed-process-properties-red.log` | exit1；58=48PASS/10FAIL/0ERROR/skip | 23.771s；10:36:49 | runtime31GREEN；AS27=Props12的4PASS8缺defaults/bounds/copy/deepBinder FAIL+旧ROCfg15的原13PASS2conflict FAIL；compiler/positivefixtures有效 |
| `/tmp/tianji-governed-process-prerequisites-green.log` | exit0；59PASS | 18.893s；10:39:53 | 试跑root误写-Dotel.metrics/exporter；只记录functional pass，不冒称canonical或telemetry验证 |
| `/tmp/tianji-governed-process-prerequisites-green-canonical.log` | exit0 BUILD SUCCESS；59PASS/0fail/error/skip | 19.462s；10:40:41 | AR8+Bridge23+Props13+旧ROCfg15；已按正确三exporter参数重跑，Props13含真实YAML默认off新增项 |
| `/tmp/tianji-governed-process-assembly-red.log` | exit1；19=5PASS/8断言FAIL/6直接UOE ERROR/0skip | 21.358s；10:47:41 | 11Bean body全UOE，positivegraph/readiness/telemetry/lifecycle缺行为；5个MissingBean负例PASS不能充全装配证明；root读524行19tests后才功能GREEN GO |
| `/tmp/tianji-governed-process-assembly-green.log` | exit0 BUILD SUCCESS；78PASS/0fail/error/skip | 21.188s；10:51:55 | runtime31+AS47（Props13+旧Cfg15+新Cfg19）；root只在有效19RED后实现newCfg，未变其他protocol |
| `/tmp/tianji-governed-process-assembly-final.log` | exit0 BUILD SUCCESS；159PASS/0fail/error/skip | 27.397s；10:53:38 | 精确11类完整source-reactor成功；PG未选中，0skip不证明PG |
| 最终source/独审/static | PASS（precommit） | 最终snapshot | root逐项17MC/10Rules及23SQL/14scope/strict/diff通过；独审budget5与newcfg261+tests524全部PASS，无实质或nice-to-have finding；最后cached/Git待root |

完整分类为runtime83（AR8、nativeLoop17、coreBridge23、AccessStore18、Verifier17），AS76（GrantBindings8、AuthorityCfg11、oldProps10、newProps13、oldCfg15、newCfg19）。每组件有效RED后才其GREEN，不提交stub；新AS Test独占委托保持root3 umbrella，没有路径扩张。以下实际命令由root运行，cwd tianji-service，docowner未Maven：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=TianjiGovernedProcessToolPropertiesTest,TianjiGovernedProcessToolConfigurationTest,TianjiReadOnlyDecisionToolPropertiesTest,TianjiReadOnlyDecisionToolConfigurationTest,TianjiAgentRuntimeAutoConfigurationTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,TianjiToolAuthorityConfigurationTest,TianjiToolGrantBindingsTest,ToolResultAccessStoreTest,ToolExecutionStorageVerifierTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-governed-process-assembly-final.log 2>&1
```

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | AR8/旧Cfg15验证none/old/new/both modes、owned早guard/lastQualifier/旧7Bean V13链 | 局部factory保证，不承诺全Spring共享依赖初始化排序 |
| PLAN-REQ-002 | Satisfied | Props13 realBinder/bounds/immutable/lateindex、newCfg19全slot closure/AA拒绝/unionrefs+fullresolver | metadata不代表远程policy或role已激活 |
| PLAN-REQ-003 | Satisfied | actual13/14verify、globalLazy actualroots、mandatorystablequalified resolver与全部affinity矩阵 | 无fallback/fakecontext，hostversion非DBproof，真实IAM/PG未验收 |
| PLAN-REQ-004 | Satisfied | Otel类型provider先ctor、原privacygate、ownedHTTP与两pool lifecycle、callerSDK不关 | OTelBean不证明Globalready；测试资源不是生产应用运行 |
| PLAN-REQ-005 | Satisfied | 各有效RED/完整159PASS、14scope/base四defaults/23SQL/独审，plan冻结 | 最后cached/Git待root，AA/MCP/T3-08..T4及全Goal Partial |
| 完整T3-07/Goal | Partial | 本步defaultOFF装配源码验证完成 | 真实IAM/crypto/PG/model及剩余蓝图工作后续 |

## 4. Blocking Manual Check

以下是已验证precommit snapshot的逐项结论，root最终文档/cached/Git尚待执行；不以受控测试替代真实环境验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR/AS现技术factory与原core/library SPI，14paths | 现结构/JDBC/Flyway保持，Agent excluded，无新认证/DDL框架或层 | None |
| MC-REUSE-001 | Applicable | PASS | 现FixedBindings/transports/Stores/Recorders/Binder与roothelpers | 实际对象装配，不重造protocol/parser/authority/defaultresolver | None |
| MC-DEP-001 | Applicable | PASS | 现Spring Boot/JDK HttpClient/OTel/IAM库，POM无改 | 无依赖/SDK/构建脚本变更 | None |
| MC-NAME-001 | Applicable | PASS | immutable Properties/11明确Bean名/usesActionContextResolver | technical角色明确，无新entity或业务DTO | None |
| MC-VALID-001 | Applicable | PASS | root/deep/lateindex、mode/sixprereq/slots-AA/mandatoryresolver/actualroots tests | metadata不是fresh IAMproof，原执行权限守卫保持 | None |
| MC-MODEL-001 | Applicable | PASS | immutable双typedlists/BackendVO复用、旧VO/port不变 | each<=256、bothnonempty，未复制身份/context/globaltimeout | None |
| MC-CONVERT-001 | Not applicable | N/A | typed config/backend/ref/slot复用 | 无PO/entity转换、新Mapper/Converter或第二JSONparser | 沿现模型边界 |
| MC-LOG-001 | Not applicable | N/A | 未新增业务日志，fixedsafe异常/Properties脱敏 | 无profile/body/token泄露或虚构权限日志 | 无日志注入变化 |
| MC-BEAN-001 | Applicable | PASS | 11命名@Bean/@Qualifier/Lazy bridge/Lazyfalse verifier/owned destroy | actualroots/resolverpointer全核，factory method注入沿现模式，无业务Service伪Lombok | None |
| MC-UTIL-001 | Applicable | PASS | 现Binder/NoUnbound/JDK Duration/HttpClient/库helpers | 无自建pool/loader框架或GlobalOTel mutation，复用原transport私有pool | None |
| MC-JSON-001 | Applicable | PASS | 现Binding/Backend/codec/enum不变，只typed YAML Binder | 无ordinal/JSON或业务传输格式改变 | None |
| MC-TIME-001 | Applicable | PASS | Backend100ms..30s/ownedHTTP3s、coreTTL/window保持，OTel前置顺序 | 无globaltimeout/时钟/业务时间改写或新context默认值 | None |
| MC-CONFIG-001 | Applicable | PASS | 唯一baseYAML四defaults、两mode互斥/defaultOFF | 不自动注册/启用profiles/rolegrant，旧keys保持 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Factory/Adapter+eager actualchain verifier，原Facade/UoW/CAS不动 | 对实际mode/readiness/lifecycle变化点装配，不引新引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | root核14paths=5mainJava5tests/YAML3docs及23SQL字节不变 | 库/authority/Stores/Recorders/协议/AA/MCP/schema不动，core仅1helper | 最后cached范围root复核 |
| MC-TEST-001 | Applicable | PASS | component RED/59/78/159GREEN、合法正向fixtures/独审19cases | mockcatalog/ContextRunner/NativeRunner非真实HTTP/PG/IAM，0skip非PGproof | 真实环境待验收 |
| MC-BLOCKER-001 | Applicable | PASS | root逐项17MC10Rules/独审budget5及new261prod524test均PASS，end checklist已读 | source/test/static无openfinding，Review非Accepted，Goal仍Partial | 最后文档/cached/staging/commit待root |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Properties/Configuration/11Bean名字与typed值角色明确 | source/编译/BeanGraph | 命名沿现模式，无新普通entity | None |
| Rule 2 | Applicable | PASS | strictBinder/mode/prereq/slots/actualroots/mandatoryhost校验 | Props13/newCfg19/旧guard及AR模式正反 | 沿本域technical boundary，不用metadata冒权限 | None |
| Rule 3 | Applicable | PASS | immutable Props record及typedVO，旧模型不变 | copy/bounds/unknown/兼容回归 | record用于不可变配置值，无实体/Converter层新增 | None |
| Rule 4 | Applicable | PASS | 明确命名@Bean/@Qualifier参数/owned生命周期，现factory注入风格 | actualroots/globalLazy/resolverpointer | 没有新业务Service/Lombok伪注入，无无关日志 | None |
| Rule 5 | Applicable | PASS | 原Spring Binder/JDK/SDK/typed库复用 | imports/POM/14scope | 无新utility/依赖/框架 | None |
| Rule 6 | Applicable | PASS | 原JSON/enum/port保持，typedYAML与redacted值 | realBinder/旧库回归 | 无ordinal/新parser或业务格式改变 | None |
| Rule 7 | Applicable | PASS | 唯一application.yml四default键，旧keys保留 | YAMLloader默认off/互斥测试 | 无其他profile文件，不启真实tools或角色权限 | None |
| Rule 9 | Applicable | PASS | existingFactory/Adapter与actualchain验证，原Facade/UoW/CAS不改 | 模式/readiness/affinity/lifecycle矩阵 | 不引策略/工作流/身份引擎 | None |
| Rule 10 | Applicable | PASS | Duration Backend/HTTP3s与原core时间窗口 | 边界与顺序验证/原库回归 | 无java.util时间或时钟/globaltimeout/context改写 | None |
| Rule 11 | Applicable | PASS | 现Agent/JDBC/独立Flyway/generator-excluded与23SQL保持 | source14scope/bytecompare/无POM改动 | 无CRUD/MP迁移/authority/新业务事件机制改造 | None |

## 6. 结论与未验证边界

本Step达到**Verified（nonruntime，PRECOMMIT）**：5aliases Satisfied，17MC/10Rules逐项PASS或有据N/A，root全文/手工/static/独审/end checklist已闭合。新cfg261行与测试524行/19cases独审无实质或nice-to-have finding；budget5独审亦PASS。plan a1a068...冻结，完整159PASS日志真实；最后root文档/cached/staging/commit仍PENDING，不预测Git SHA。

仅使用测试生命周期内的有限ContextRunner/NativeRunner/JVM与owned资源，未启动生产应用/后台调度器、DB/容器、HTTP listener/collector/浏览器/GUI；无真实IAM/PDP/OAuth/JWT crypto/HTTP网络/LLM/PG验收。无SQL应用/repair/backfill/drop、权限激活/角色grant、push/PR/release。defaultOFF装配不是实际启用；hostresolver业务值仍由host提供，实际出站SERVICE非USER OBO，businessVersion非DBproof。

无repo scratch/scripts/work/harness新增，测试保留subsystem。docowner仅定稿evidence，不改plan/source/tests/README/YAML、未构建或提交。完整T3-07/Goal仍**Partial**，AA/MCP、T3-08..T4及真实环境验收后续。
