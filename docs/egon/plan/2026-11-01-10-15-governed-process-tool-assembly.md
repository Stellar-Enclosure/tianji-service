# 默认关闭的受控流程工具统一装配

| Field | Value |
| --- | --- |
| Document | `2026-11-01-10-15-governed-process-tool-assembly.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 10:15 Asia/Shanghai` |
| Updated | `2026-11-01 10:23 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR单mode选择/AS defaultOFF governed-process union装配；14paths |
| Source Requirement | 原蓝图继续未完工作并分步提交；§7/11.3/12/13显式tool运行与可信source/治理 |
| Baseline Revision | `main@4c5e95741c02b0c982c769be92db814633f76c15`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact用户执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `1b55dd1bd97ada9eabad12fe1520fb78c4a06950aa178b22b04f8990611bf66f` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [受控native core](2026-11-01-09-05-governed-native-process-tools.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
十四路径/一个Step把Step19 programmatic union core按显式defaultOFF新模式接入AS实际worker链：有限strict双profiles、真实V13/V14 readiness、mandatory稳定hostresolver、两模式互斥与eager actualroot验证。保旧RO V13链/defaultoff/构造与厂方法兼容；无新协议、authority、schema、依赖或权限激活。单commit `feat(agent): assemble governed process tools by explicit opt-in`，全Goal仍Partial。

## 2. Target Spec and Effective Design
蓝图§11.3模型只业务args/host固定intent、§12 nativeAdapter与统一治理、§13显式可信source/权限/配置版本、§7存储事实边界落实为本装配。core/库已具完整当前权/Recorder/原结果语义，本步只配置与实际依赖链连接，constructor/profile元数据不代表真实远程权限或角色grant。Review沿legacy原exact授权，不虚构Accepted；source GO必须root全文/strict/resources后发出。AA/MCP及其他蓝图任务/真实IAM/PG/模型验收后续。

## 3. Effective Requirements and Acceptance
PLAN-REQ是原要求局部trace aliases，不新增业务需求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | 单一明确模式装配actual native工具链，默认关闭且旧RO兼容 | 双flag冲突早拒；neither provider get；旧only/新only懒选择正确root，旧lastQualifier/ctor保持 | ARfactory/Test、旧ROcfg/Test、YAML |
| PLAN-REQ-002 | §11.3/13.2 | operator双profile有限闭合且不扩大authority | strictimmutableprops/deep unknown，DMN/IDW START fullslot exactclosure，拒AAprofile；uniondescriptor refs/full原resolver | Props/Test、新Cfg/Test |
| PLAN-REQ-003 | §7/11.3/13 | 执行前实际存储readiness与hostcontext/currentauthority同源 | 真实Prep/Recorder verify13、AccessStore verify14；mandatorystablequalified resolver无fallback；实际PDP/runAuth/ports/ledger/root身份全核 | 新Cfg/Test、Bridgehelper/Test |
| PLAN-REQ-004 | §13.3/13.4 | 生命周期/Telemetry顺序与partial readiness失败闭合 | 类型Otel provider先bridge ctor；globalLazy仍eager验证actualworker/root；只close自有HTTP/两pool，不关IAM | newCfg/Test、旧兼容 |
| PLAN-REQ-005 | §17/T3-07 | 范围/配置/证据真实，不误称权限或运行已验收 | 14paths、base唯一YAML四default键、23SQL bytes不改、有效RED后11类回归；全GoalPartial | all/tests/README/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
先最小Props record/EnvironmentAware、AR新provider参数、resolver affinity返回false与newCfg @Bean signatures/UOE供测试编译（不提交stub）；各组件测试先有效RED，再最小GREEN。按Props→mode/affinity/旧guard→newAS actualchain→YAML/docs及完整回归依赖顺序。helper/directfactory必要参数编译调整不能当行为RED，正向Bean/Props fixtures必须真实合法；无sourceGO前只本两docs。
### 4.2 Test-first strategy
Props strictBinder、AR modes+bridgeaffinity/oldRO conflict、新ASfactory各自有效RED barrier；root各自观察后可该组件独立GREEN。闭合类型先声明可编译，missingguards/fixture失败要如实分类；不得用mockfakeverified/unusedBean替代actualreadiness/worker。newcfg正向fixture依赖已初始化typedProps或真实Binder各按测试目标选，避免无关配置重绑错误伪RED。
### 4.3 Sequential and parallel boundaries
root owns newAS Configuration/Test+README 3paths；budget owns newProps/Test+oldROCfg/Test+YAML 5；tool_audit owns ARCfg/Test+coreBridge/Test（仅identityhelper）4；docowner2。不是独自在仓库，不回滚其他owner变更；root统一Maven时冻结所有Java，本Step提交前不并行下一Step。首个READY后plan冻结，后续只root明确批准clarification才改，避免交叉SHA。
### 4.4 Commit boundaries
14paths单逻辑Step/commit `feat(agent): assemble governed process tools by explicit opt-in`。实际17MC/10Rules、source/audits/静态/cached后显式stage；不push/改历史或自动运行项目。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| AR唯一mode选择/旧guard | §12单实际loop/明确启用边界 | 原factory只有qualified ROprovider，core wildcardSupplier已能容union | 增一个倒数第二provider/flag guard，保旧lastQualifier/Executorctor | Implement |
| typed双profile Props | §13.2 operator绑定有限版本 | 现BindingVO/BackendVO与旧EnvironmentAware rootrebind模式 | 只4组件record，不复制descriptor/globaltimeout/hostcontext | Implement |
| 11distinctBeans+actualroot | §7/11.3/13可信host/source/readiness | 现Stores/Recorders/2transports/bridge/Resolver SPI全有 | 只Factory/actualchain，不重建protocol/认证/存储/默认resolver | Implement |
| 旧RO兼容/operationalboundary | §17非破坏性 | 旧7Beans/ownV13verify/read-only defaultfalse | guard一处、旧语义保持；new模式需V14不强迫旧RO | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| strict profiles | PLAN-REQ-002/005 | newPropsTest | 最小record声明/原BackendVO | finiteimmutable operator输入 | newCfg | Step 1 |
| mode/hostaffinity | PLAN-REQ-001/003 | ARTest/coreBridgeTest/oldCfgTest | 最小provider/helper声明，旧root | 唯一lazy selected root与resolverobserver | newactualchain | Step 1 |
| AS实际装配 | PLAN-REQ-002/003/004/005 | newCfgTest | 上项+现库，新@Bean声明 | readiness/mandatoryresolver/11Beans+lifecycle | runtime worker，仍defaultOFF | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 原AR autoCfg/AS integration@Configuration，Agent profile generatorexcluded | 只显式装配现core/library | 不新层/CRUD/DDL/MP迁移/authority或依赖，23SQL不改 | Step1；MC-ARCH-001/MC-SCOPE-001 |
| Reuse/capability | existing Binder/Binding/Backend/Stores/Recorders/transports/root helpers | 不造第二parser/transport/proof或defaultresolver | 真实实例verify；现固定库构造+slotclosure+typedroot验证 | Step1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | 旧Properties record/EnvironmentAware，原typedVO与cfgsafeerror | immutable operator值、strictinput/actualaffinity | newProps4components；不新entity/转换层或模型identity | Step1；MC-NAME-001/MC-MODEL-001/MC-VALID-001 |
| Bean/log/JSON/time/config | 命名@Bean/@Qualifier factorymethod、Lazyfalse guard/owned destroy | 参数注入沿原technical factory而非businessService | 明确11names/资源owner；无新日志/parser/Clock，单baseYAML四default键 | Step1；MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001 |
| Pattern | 现Factory/Adapter+actualchain verifier，原Facade/UoW/CAS | 解决mode/lifecycle/readiness实际variation | 不另建认证/状态/工作流引擎，不复制执行协议 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| strict config | 旧ROProps独立NoUnbound list[index].backend/.tool | Boot4.1.2深unknown需每root重绑 | 足够复用，不改库模型 | samepattern双lists | None | Step1；MC-REUSE-001 |
| readiness/source | Prep/InvRecorder verify13，AccessStore verify14，AccessRecorder affinity | 方法/指针helpers真实存在 | newcfg组装实际实例，不读私有verifiedflag | existingAPIs | None | Step1；MC-VALID-001 |
| identity/lifecycle | 两transports各6helpers、newcore5helpers+新resolverhelper、rootexecutoridentity | 无需library改动，ownedHTTP/2privatepools已有close | distinctownedBeans与actualroot verifier | existing libraries | None | Step1；MC-BEAN-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | Properties/Configuration现模式、11Bean名字与typed值明确 | tests→Cfg/Props/helper | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | source/编译 | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | Binder/rootdeepunknown、modeprereq/slots/roots/mandatoryhostresolver校验 | Props/Cfg/ARfactory/tests | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | 正反/failclosed | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | immutable Properties record复用typedVO，no实体/Converter/旧模型改动 | Props/既有Binding/Backend | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | copy/bounds/compat | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | explicit命名@Bean/@Qualifier参数/生命周期沿现factory风格，无新业务Service | newCfg/oldCfg/ARfactory | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | BeanGraph/actualroots | Step1 | PASS |
| Rule 5 | §7/11.3/12/13/17 | 现Spring Binder/NoUnbound/JDK/现SDK/typedlibrary复用 | imports/5prod | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | POM/无新工具 | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 原JSON/enum/port合同不动，typedYAML与redacted值 | Props/原libs | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | 绑定/无ordinal | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 唯一baseYAML新增四default键，旧key保持，mode互斥 | application.yml/Props/guards | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | keyparity/默认off | Step1 | PASS |
| Rule 9 | §7/11.3/12/13/17 | 现Factory/Adapter+actualchain生命周期验证，原Facade/UoW/CAS不动 | AR/ASfactories | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | 模式/资源/affinity | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | java.time Duration复用backendtimeout/HTTP3s，无新window/TTLsource | newCfg/原core | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | 时间兼容 | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | 现Agent结构/JDBC/Flyway/generator-excluded，23SQL/authority/业务库不改 | 14paths | 本file块及§6闭合mode/profile/实际root/readiness/生命周期合同 | scope/bytefreeze | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | backend.timeout/HTTP connectTimeout | Duration | Duration | Not persisted | Internal | Not applicable: technical duration | Not applicable: 无UI | 现BackendVO100ms..30s校验原样；ownedHTTP connect3s，不加全局timeout | JDK Duration，no新Clock | core intentTTL/dispatch/currentauth/µ窗口全不改 | Step 1；TEST-001/003 |
| TIME-002 | Telemetry/bridge/verifier lifecycle顺序 | Occurred instant | Instant | Not persisted | Internal | Not applicable: 构造顺序 | Not applicable: 无UI | mandatoryresolver与typeOtel在bridgector前；Lazyfalsecallback globalLazy仍getactualroot；destroy只自有资源 | 顺序验证无新时间字段/Globalmutation | 不声称SpringOtelBean即Global证明；原env/cachedTracing gate保持 | Step 1；TEST-003/004 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java | 先证明新双profile配置值的闭合Binder与不可变边界 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java | 不可变strict governed-process配置，不复制authority或hostcontext | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java | 先验证双模式选择、provider lazy/不解引用与early conflict | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java | 同一Executor factory选择唯一common bridge root并拒双模式 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java | 新增hostresolver精确affinity观察测试，原22功能断言保留 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java | 仅给装配actualchain暴露hostresolver指针observer | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java | 先证明旧模式在双flag时owned factory早拒，旧7Bean链回归保持 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java | 仅在既有prereq入口加入新flag互斥guard | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java | 先证明11实际命名Bean/readiness/全profile闭合/hostresolver与globalLazy root链 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java | defaultOFF装配全union链与mandatoryresolver/eager actualroot证明 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | 仅在唯一baseYAML新增新模式默认关闭四键 | Step1 |
| MODIFY | README.md | 记录实际opt-in prerequisites/typedprofiles/hostresolver/资源生命周期与未验收边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-10-15-governed-process-tool-assembly.md | 冻结14paths/11Beans/完整合同与组件RED顺序 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-governed-process-tool-assembly.md | 实际source/RED/GREEN/手工规则PENDING骨架与后续证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
已核baseline4c5e957clean、SpecSHA1b55dd1bd97ada9eabad12fe1520fb78c4a06950aa178b22b04f8990611bf66f，实际paths/qualifiers/helpers与既有9个selector。Created10:15为root请求草稿时刻。writing18md24refs/executing15md19refs资源PASS。23SQL immutable，无新版本/应用SQL/repair/backfill；Agent generatorexcluded，保JDBC/独立Flyway，不产CRUD/MP迁移/codegen日志。

**Flags/Props。** prefix stellar.tianji.agent.tools.governed-process；record(boolean enabled,int concurrency,List<TianjiFixedDecisionToolBindings.BindingVO> decisionBindings,List<TianjiFixedBpmnStartToolBindings.BindingVO> startBindings) implements EnvironmentAware，ignoreUnknownFields=false；enabled默认false，concurrency0→4且1..8，两lists null→empty/defensive List.copyOf/各<=256/null元素拒，enabled时两者都nonempty。stateless setEnvironment对每list每index.backend和.tool独立NoUnboundElements重绑/相等校验，拒unknown root/deep/laterindex，复用现Backend16/100ms..30s，不复制descriptor/globaltimeout/identity/contextsettings；toStringredacted。

新old两flag互斥，固定冲突TIANJI_TOOL_RUNTIME_MODE_CONFLICT。ARfactory在自身AgentStorageVerifier.verify、contexts/provider解析前读flags并拒bothtrue；bothfalse nullsupplier且两个provider都不get（null legacy fixture也不碰）；oldonly旧qualifiedprovider lazy行为保持；newonly qualified tianjiAdkGovernedProcessTools ObjectProvider<AdkGovernedProcessToolBridge> lazy。**newprovider参数必须在旧最后ROprovider之前**，保旧lastQualifier反射与Executorpublicctor不变，knownfactorycallhelper仅补参数。旧RO requirePrerequisites首查newflag冲突，newcfg首查oldflag；每ownedfactory/其callback均核原6prereq api/storage/execution-grants/execution-authorization/runtime/tools.authority，不require discovery。只保证局部factory不启动自有verify/provider/transport；ordinarySpring可能已resolve共享依赖，不承诺全局零初始化/新BFP。

**New Configuration。** @Configuration(proxyBeanMethods=false)、@ConditionalOnProperty(prefix newmode,name enabled,havingValue true)、@EnableConfigurationProperties(newProps)。全部新Bean names distinct，旧RO 7names/行为不改。每factory先prereq/conflict，实际对象职责与资源如下：

| Exact Bean name | Type | Qualified/input roots | Readiness/lifecycle |
| --- | --- | --- | --- |
| governedProcessToolInvocationStore | ToolInvocationStore | DataSource/ledger/@toolCapabilityStore/@tianjiToolGrantAuthorizer/Environment | new actual instance.verifyStorage()，V13；no destroy |
| governedProcessToolInvocationRecorder | ToolInvocationRecorder | same roots + @governedProcessToolInvocationStore | new actual instance.verifyStorage()，V13；no destroy |
| governedProcessToolResultAccessStore | ToolResultAccessStore | DataSource/ledger/@toolCapabilityStore/@tianjiToolGrantAuthorizer | new actual instance.verifyStorage()，V14；no destroy |
| governedProcessToolResultAccessRecorder | ToolResultAccessRecorder | @governedProcessToolResultAccessStore/ledger | new actual(store,ledger)，ctor亲和；no fake readinessflag |
| tianjiGovernedProcessDecisionBindings | TianjiFixedDecisionToolBindings | authorityProps/newProps/@tianjiToolGrantBindings | actual DMN slots exactall匹配；reuse现lib |
| tianjiGovernedProcessStartBindings | TianjiFixedBpmnStartToolBindings | authorityProps/newProps/@tianjiToolGrantBindings | actual IDW START slots exactall匹配；配置AA必须拒 |
| tianjiGovernedProcessHttpClient | HttpClient | Environment | Redirect.NEVER/connectTimeout3s；destroyMethod=shutdownNow，仅自有 |
| tianjiGovernedProcessDecisionTransport | TianjiFixedDecisionToolTransport | @decisionBindings/四qualifiedIAM/@sharedHttp/newProps.concurrency | existing ctor，destroyMethod=close，只关私有executor |
| tianjiGovernedProcessStartTransport | TianjiFixedBpmnStartToolTransport | @startBindings/四qualifiedIAM/@sharedHttp/newProps.concurrency | existing ctor，destroyMethod=close，只关私有executor |
| tianjiAdkGovernedProcessTools | AdkGovernedProcessToolBridge | ledger/@invRecorder/@2transports/@accessRecorder/selecteddescriptors/fullgrantresolver/@hostResolver provider/typeOtel provider | @Lazy；mandatoryresolver.getObject，Otel.getIfAvailable先ctor |
| tianjiGovernedProcessToolRuntimeChainVerifier | SmartInitializingSingleton | 所有actual typed roots + qualified bridge/worker/hostresolver providers + Environment | @Lazy(false) callback，globalLazy仍强制actualreadiness/roots；不重复verify |

四qualified IAM为idpServiceOAuth2Client、tenantIssuerAuthenticationManagerComponent、verifiedPrincipalConverter、trustedIssuerRegistry，都是caller-owned不得close。cap/toolPDP/grantBindings/真实runAuthorizer分别现toolCapabilityStore、tianjiToolGrantAuthorizer、tianjiToolGrantBindings、tianjiExecutionGrantAuthorizer，ledger复用实际AgentLedgerService。sharedownedHttp只一实例，2transport ctor均existinglib+newProps.concurrency，各只关private boundedpool。Store和Recorder factory实际调用自身verifyStorage：Prep/InvRecorder require V13，AccessStore require V14；AccessRecorder ctor exactstore/ledger，无额外readybool/autoDDL。callback不重复verify。

**Profiles闭合。** factory按旧装配模式先核实际authority.enabled()/newProps.enabled()，两FixedBindings由authority.descriptors+新typed lists构造。actual原tianjiToolGrantBindings.bindings作为权威slot来源，tuple为tenant/issuerFingerprint/principalType/Agentdefinition key-version-sha/fulltoolref。DMN expected集=ALL actual grant descriptor riskREAD_ONLY且DecisionClient.EXECUTE_RESOURCE_KEY；START expected集=ALL actual IDW且ProcessClient.START_RESOURCE_KEY。配置两sets分别exact equality/无duplicate/missing/extra/drift；START配置即使library容AA也必须本cfg拒绝。未引用其他RO/write/AA authority描述可存在，不误暴露。nativebridge仅用actualDMN/START profile refs的UNION从authority选exactdescriptors，无missing/duplicates；原grantBindings.resolve(material).map(binding)完整函数不filter/split，core自己ONE fullRegistry.visible，auth resolver可按原逻辑独立核当前bindings，不声称两层合计只resolve一次。

**Mandatoryhost与actualchain。** host必须提供稳定qualified tianjiToolStartActionContextResolver，类型core.StartActionContextResolver。Lazybridge factory以mandatory @Qualifier ObjectProvider.getObject取，不getIfAvailable/default/config派生/造runid-version-hashfallback；再按原模式type ObjectProvider<OpenTelemetry>.getIfAvailable于bridgector前让可用hosttelemetry初始化，缺OtelBean可由host Javaagent/programmatic Global既已初始化；不造Bean名/Global.set/reset/env变更或声称Bean即Global证明。core原env literal false+cachedTracing false gate不降。bridge传实际ledger/invRecorder/2transports/accessRecorder、selecteddescriptors、原fullresolver、实际hostresolver。只增corepublic usesActionContextResolver(candidate)==actionContexts observer，不执行resolver/改协议。

Lazy(false) SmartInitializingSingleton callback在globalLazy下也强制actualStores/Recorder typed依赖（因此其factory各verify）及qualified actualbridge/worker provider getObject；再次取mandatoryresolver并核bridge.usesActionContextResolver，missing/ambiguous/prototype/foreign failclosed。显式校ledger/source、cap/source+ledger、prep/source+ledger+cap+auth、invRecorder/source+ledger+prep+cap+auth、accessStore/source+ledger+cap+auth、accessRecorder/store+ledger；toolPDP.usesLedger/Bindings/IssuerRegistry；runAuthorization.usesToolAuthorization(toolPDP)。两transport各6helpers exact Bindings/OAuth/Authentication/Converter/IssuerRegistry/HttpClient+http.NEVER；bridge6helpers exact ledger/recorder/decision/start/access/resolver；worker.usesLedger/usesAuthorization(actualqualifiedrunAuthorizer)/usesGovernedProcessTools(actualbridge)。不能用unusedparallelBean、假readyflag或只Bean存在代替实际链，no cycles/extraBean。

CLAR-001：actual transport库各6个affinity helpers已核，不改其源码；新bridge第6helper仅resolverpointer。实际AS资源仅application.yml，添enabledfalse/concurrency4/decision-bindings[]/start-bindings[]四键，无profile生成或真实tools启用。技术@Configuration method参数注入沿现pattern，不增业务Service/新依赖；配置metadata不代表issuer/permission/role激活，实际出站为SERVICE不是USER OBO，hostbusinessVersion非DB CASproof。所有运行权限/费用/时间原库语义保留。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 显式defaultOFF装配真实governed process工具链
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交4c5e957/core与既有FixedBinding/Transport/Store/Recorder/authority/IAM/旧RO配置。
- Baseline state: programmatic union已可用，AS仅旧RO 7Bean/AR单ROprovider；newmode/typedprops/mandatoryhostactualroot未装配。
- Observable outcome: 新模式显式开启时全profile/六prereq/readiness/hostresolver/actualworker链精确；disabled行为保持、双mode早拒。
- End state: AS opt-in union配置/typedprops与pointerhelper/默认off可用，不改变库协议/authority/SQL或真实权限/运行验收。
- Test-first gate: Required — 最小record/method/Bean declarations→各组件合法fixture真实RED→最小GREEN，无编译/fixture伪RED，不提交stub。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java`
- Purpose: 先证明新双profile配置值的闭合Binder与不可变边界。
- Symbols: PropertiesTest；真实ApplicationContextRunner/Binder。
- Repository evidence: 旧ROProps/Test已补Boot4.1.2 list深层unknown陷阱，现两BindingVO/BackendVO库闭合。
- Dependencies and consumers: 新Props最小record/EnvironmentAware无行为声明先供编译；既有valid双profile fixtures。
- Why now: 首个组件真实RED后才Props GREEN，不能编译/无效fixtures充证明。
- Contract/signature changes: 新测试，不改既有BindingVO/Backend timeout/JSON协议。
- Input/output and state mapping: false/0→4，1..8、each<=256防御copy/null元素拒绝，enabled双列表nonempty；root/deep/secondindex.backend/.tool unknown拒绝。
- Error and edge behavior: 真实Boot绑定验证动态input schema正常键，不借非初始list元素绕unknown；toString不泄漏configmetadata。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
assertDisabledDefaultsAndConcurrencyBoundsAndImmutableFiniteLists();
assertEnabledRequiresBothDecisionAndStartProfilesWithValidExistingBackendModels();
assertRealBinderRejectsUnknownRootBackendToolAndLaterIndexedLeavesWithoutRejectingValidInputSchemaKeys();
assertRedactedToStringAndNoCopiedDescriptorTimeoutOrIdentitySettings();
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: Props行为有效RED已被root记录，合法typed fixture可到达目标guard。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java`
- Purpose: 不可变strict governed-process配置，不复制authority或hostcontext。
- Symbols: record(enabled,concurrency,decisionBindings,startBindings) implements EnvironmentAware。
- Repository evidence: 旧ROProps42行stateless rootrebind、现两fixed BindingVO与16fieldBackend timeout100ms..30s。
- Dependencies and consumers: File1有效RED；newCfg消费者与YAML。
- Why now: 仅最小类型声明先于测试编译，完整guards/Binder在自身RED后实现。
- Contract/signature changes: @ConfigurationProperties(prefix=stellar.tianji.agent.tools.governed-process,ignoreUnknownFields=false)；四components，无descriptor/globaltimeout/context/identity字段。
- Input/output and state mapping: false默认；concurrency0→4/1..8；两list null→empty/List.copyOf/each<=256，enabled必须bothnonempty；toString固定redacted。
- Error and edge behavior: setEnvironment无状态，对两lists每index.backend和.tool独立NoUnboundElementsBindHandler重绑并与原值相等，覆盖Boot深unknown陷阱。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
record TianjiGovernedProcessToolProperties(boolean enabled,int concurrency,List<DecisionBindingVO> decisionBindings,List<StartBindingVO> startBindings) implements EnvironmentAware {
    // Normalize concurrency and copy both finite lists; reject invalid enabled configuration without adding timeout/context defaults.
    setEnvironment(env) { for (each list/index) { rebindExactBackendAndToolRootsWithNoUnboundElements(); requireEqualsAlreadyBoundValues(); } }
    @Override public String toString() { return "TianjiGovernedProcessToolProperties[redacted]"; }
}
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 合法typed两profile只由operator配置，unknown root/deep/lateindex失败闭合。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java`
- Purpose: 先验证双模式选择、provider lazy/不解引用与early conflict。
- Symbols: 既有factory helper/lastQualifier反射与新增mode matrix。
- Repository evidence: 现ARfactory6 tests，最后参数Qualifier反射tianjiAdkReadOnlyDecisionTools；helper直接调用旧factory。
- Dependencies and consumers: ARfactory仅先插newprovider参数但不功能选择供编译；原ROOT/shared mocks。
- Why now: 新增mode行为先真实RED，helper仅必要参数对齐，不弱化旧断言。
- Contract/signature changes: knownhelper加倒数第二newprovider；旧lastQualifier不变；不改Executor构造API。
- Input/output and state mapping: bothfalse neitherget/nulllegacy兼容；oldonly懒old；newonly懒new；bothtrue固定TIANJI_TOOL_RUNTIME_MODE_CONFLICT先于ownVerifier/contexts/providers。
- Error and edge behavior: 不声称全Spring共享Bean绝不初始化；只断言此factory自身verify/provider未触达；newcachedroot exactidentity。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
updateKnownDirectFactoryHelperWithNewProviderBeforeExistingLastReadOnlyProvider();
assertBothDisabledNeverDereferenceEitherProviderIncludingNullLegacyFixtures();
assertEachExclusiveModeResolvesOnlyItsQualifiedProviderLazilyAndCachesActualRoot();
assertBothEnabledConflictBeforeOwnedStorageVerificationContextOrProviderResolution();
assertExistingLastParameterReadOnlyQualifierAndOldConstructorBehaviorUnchanged();
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: AR选择行为有效RED，无误把参数编译调整当功能RED。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`
- Purpose: 同一Executor factory选择唯一common bridge root并拒双模式。
- Symbols: tianjiAdkAgentRunExecutor新增qualified govProcess ObjectProvider置旧最后ROprovider之前。
- Repository evidence: 现factory只readOnlyflag并传lazy tools::getObject，旧Executor wildcard Supplier已支持common。
- Dependencies and consumers: File3有效RED，existingExecutor/publicctors不改。
- Why now: mode组件自身RED后最小GREEN，无新factory/第二supplierloop。
- Contract/signature changes: 新增@Qualifier(tianjiAdkGovernedProcessTools)ObjectProvider<AdkGovernedProcessToolBridge>参数在旧lastprovider前；旧lastQualifier保留。
- Input/output and state mapping: 先读两flags并bothtrue固定conflict；在ownAgentStorageVerifier/contexts/provider之前；none nullsupplier，oldonlyold lazy，新onlynew lazy。
- Error and edge behavior: false不可dereference null/newprovider；旧ownstorage/context/model/control校验其余保持；不追加globalBFP或全Spring初始化保证。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
boolean readOnly = env.getProperty(oldFlag,Boolean.class,false); boolean governed = env.getProperty(newFlag,Boolean.class,false);
if (readOnly && governed) throw new IllegalStateException("TIANJI_TOOL_RUNTIME_MODE_CONFLICT");
// This guard precedes this factory's verifier/context/provider operations; ordinary pre-resolved Spring dependencies are not globally suppressed.
Supplier<? extends AdkGovernedToolBridge> selected = governed ? governedTools::getObject : readOnly ? oldTools::getObject : null;
return existingExecutorConstructorWithUnchangedStorageContextOptionsAndOneSelectedLazySupplier(selected);
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: runtime mode互斥/lazy兼容root选择可被新actualchain verifier验证。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java`
- Purpose: 新增hostresolver精确affinity观察测试，原22功能断言保留。
- Symbols: usesActionContextResolver positive/same/foreign/null。
- Repository evidence: Step19桥已持actionContexts final字段与其他5identityhelpers，ctor已mandatoryresolver。
- Dependencies and consumers: bridge compile-onlyhelperreturnfalse支持真实RED；不改其执行协议。
- Why now: 最小新helper先有效RED，再一行pointer实现，无其他native变更。
- Contract/signature changes: 新增一组identity测试，无Ctor/Scope/VO/行为断言弱化。
- Input/output and state mapping: same object true，foreign/null false；observer-only无resolve调用。
- Error and edge behavior: 不把pointer相同当resolver业务授权/结果有效证明，不注册默认resolver。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
var resolver = validHostResolver(); var bridge = existingBridgeWith(resolver);
assertTrue(bridge.usesActionContextResolver(resolver));
assertFalse(bridge.usesActionContextResolver(foreignResolver)); assertFalse(bridge.usesActionContextResolver(null));
verifyResolverWasNotCalledByIdentityObservers();
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 新exactaffinity行为有效RED，旧Process桥完整执行语义仍保留。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java`
- Purpose: 仅给装配actualchain暴露hostresolver指针observer。
- Symbols: public boolean usesActionContextResolver(StartActionContextResolver candidate)。
- Repository evidence: 现actionContexts field及usesLedger/Recorder/DecisionTransport/StartTransport/ResultAccessRecorder。
- Dependencies and consumers: File5有效RED；ASnewchainverifier。
- Why now: helpercomponent有效RED后最小GREEN，一行实现不改nativeprotocol。
- Contract/signature changes: 仅新增public exactpointer方法；无其他field/constructor/context/time/执行变更。
- Input/output and state mapping: return actionContexts==candidate；constructor已nonNull，所以null false。
- Error and edge behavior: 不执行resolver/不建fallback/不改变现privatefacts/权限/聚合。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
public boolean usesActionContextResolver(StartActionContextResolver candidate) {
    return actionContexts == candidate;
}
// No resolver invocation, replacement, caching, context fabrication or native execution behavior change.
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: actualresolver可以被新装配核验，旧core行为/测试未扩大。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java`
- Purpose: 先证明旧模式在双flag时owned factory早拒，旧7Bean链回归保持。
- Symbols: 旧CfgTest新增mutualmode guardcase；原13case保留。
- Repository evidence: 旧ROcfg7个命名Bean/两actual readiness/globalLazy verifier与factoryMocks可复用。
- Dependencies and consumers: 现oldCfg/sourcevalidfixtures，无新source声明必要。
- Why now: 旧guard新增行为先真实RED，不能改既有Bean名称/断言省略。
- Contract/signature changes: only必要newflag失败case，无旧factory签名变化。
- Input/output and state mapping: oldonly同V13链；newflag同时true时requirePrerequisites先throwModeConflict，ownStore/transport不得构造/verify。
- Error and edge behavior: 只ownedfactory guards，不假全Spring依赖解析零副作用；discovery仍非前置。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
assertOldReadOnlyModeRetainsSevenBeanNamesV13ReadinessAndActualExecutorIdentity();
assertDualModeFailsWithRuntimeModeConflictBeforeOwnedStoreVerificationOrTransportConstruction();
assertOriginalPrerequisiteDiscoveryIndependentGlobalLazyAndTelemetryOrderingCasesRemainValid();
retainAllExistingAssertionsWithoutRelaxingAuthorityOrSourceAffinities();
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 旧RO组件有效RED可独立最小GREEN。

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java`
- Purpose: 仅在既有prereq入口加入新flag互斥guard。
- Symbols: private requirePrerequisites(Environment)。
- Repository evidence: 现每旧Bean factory与chain callback都先调用requirePrerequisites。
- Dependencies and consumers: File7有效RED；不改变任何7Bean/类型/scope/destroy语义。
- Why now: 旧guardRED后加最小一处冲突检查，避免新旧模式同时注册owned链。
- Contract/signature changes: newgovflagtrue时固定TIANJI_TOOL_RUNTIME_MODE_CONFLICT，原6prereq照旧。
- Input/output and state mapping: check模式冲突先于此factory自身验证/transport构造，其余V13only行为保持。
- Error and edge behavior: 不require14、不依赖newproperties/provider，不创建BFP或假全局初始化顺序。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
private static void requirePrerequisites(Environment environment) {
    if (environment.getProperty("stellar.tianji.agent.tools.governed-process.enabled",Boolean.class,false))
        throw new IllegalStateException("TIANJI_TOOL_RUNTIME_MODE_CONFLICT");
    requireExistingSixPrerequisiteFlagsExactlyAsBefore(environment);
}
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 旧RO模式独立兼容但不能与新模式同时启用。

#### File 9 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java`
- Purpose: 先证明11实际命名Bean/readiness/全profile闭合/hostresolver与globalLazy root链。
- Symbols: 真实nonweb ApplicationContextRunner/factory fixtures；NoUnbound/actualLazyProcessor。
- Repository evidence: 旧ROCfgTest与StoragePostgresTest已有actual LazyInitializationBeanFactoryPostProcessor用法；newlibs有6各transporthelpers。
- Dependencies and consumers: 新Props/bridgehelper各自RED→GREEN；newcfg可先@Bean signatures/UOE供编译，无空noop fakeverified。
- Why now: ASfactory component有效RED后才生产GREEN，正向fixture必须全affinity/typedprofiles有效。
- Contract/signature changes: new11Beans；所有6prereq/互斥/discovery/默认off/hostmandatory/ownedlifecycle/源14验证。
- Input/output and state mapping: 实际Store/Recorderverify被调用；missing14失败；决策与START slot exactclosure/拒AA；uniondescriptor subset+originalfullresolver；globalLazy所有roots/mandatorystableResolver精确。
- Error and edge behavior: missing/ambiguous/prototype/wrongresolver；detachedSource/ledger/cap/toolPDP/runAuth/2transports/bridge/worker皆拒；OTel type获取先ctor；closeownedHTTP/2pool但不IAM。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
assertDefaultOffNoOwnedBeanOrProviderInteractionAndAllSixPrerequisitesFailClosedWithoutDiscoveryRequirement();
assertRealInvocationStoreAndRecorderVerifyV13AndAccessStoreVerifyV14BeforeActualNativeRootCanPass();
assertBothProfileSlotSetsExactlyMatchActualGrantBindingsAndRejectAaStartOrMissingExtraDrift();
assertGlobalLazyProcessorStillForcesSameStoreRecorderBridgeExecutorRunAuthorizationAndMandatoryStableResolverObjects();
assertTelemetryProviderByTypeResolvesBeforeBridgeConstructionWithoutGlobalMutation();
assertOnlyOwnedHttpAndTransportExecutorsCloseAndCallerIamBeansRemainOpen();
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 新AS装配真实RED已记录，未用仅创建unusedbean替代actualchain。

#### File 10 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java`
- Purpose: defaultOFF装配全union链与mandatoryresolver/eager actualroot证明。
- Symbols: @Configuration(proxyBeanMethods=false)/ConditionalOnProperty/EnableProps；11命名@Bean。
- Repository evidence: 旧ASRO装配191行及Step19/前置Stores/typedBinding/transportFactory APIs均已有。
- Dependencies and consumers: File9有效RED；Props/helper/ARmode components；现qualified IAM/ledger/cap/toolPDP/runAuth/grantroots。
- Why now: newcfg有效RED后构造真实verified实例，不引新library/config版本或cycle。
- Contract/signature changes: §6精确11Bean names；新各factory/chain callback先prereq+旧flagconflict；distinctnames不碰旧7Beans。
- Input/output and state mapping: Prep/InvRecorder实际verify13；AccessStore实际verify14/AccessRecorder sameStore；双binding exactall supported slots；sharedownedHTTP+两transports；Lazybridge mandatoryresolver/typeOtel；Lazyfalse chainforceroot。
- Error and edge behavior: 不造默认resolver/descriptor/globaltimeout，no filter fullgrantresolver；no GlobalOTel/envmutation/metadata权限；no discoveryrequire；Caller SDK/IAM不close。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```java
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(prefix="stellar.tianji.agent.tools.governed-process",name="enabled",havingValue="true")
@EnableConfigurationProperties(TianjiGovernedProcessToolProperties.class)
class TianjiGovernedProcessToolConfiguration {
    // Create the eleven exact named Beans from Section 6; real store and recorder factories call their own verifyStorage before returning.
    @Lazy bridge(...) { requirePrerequisitesAndNoOldMode(); var resolver=qualifiedHostResolver.getObject(); telemetryByType.getIfAvailable(); return new AdkGovernedProcessToolBridge(actualLedger,actualRecorder,actualDecision,actualStart,actualAccess,unionDescriptors,originalFullGrantResolver,resolver); }
    @Lazy(false) SmartInitializingSingleton verifier(...) { return () -> { recheckFlagsAndEveryConcreteAffinity(); var resolver=qualifiedHostResolver.getObject(); var bridge=bridges.getObject(); var worker=executors.getObject(); requireExactRootAndResolver(bridge,worker,resolver); }; }
}
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 新模式可显式构造实际union链，readiness/profile/mandatoryhost/root不符启动失败。

#### File 11 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Purpose: 仅在唯一baseYAML新增新模式默认关闭四键。
- Symbols: stellar.tianji.agent.tools.governed-process。
- Repository evidence: rg资源仅application.yml，旧tools.authority/read-only-decision默认false。
- Dependencies and consumers: Props exactkeys与newcfg已有，rootread最终schema。
- Why now: typedcontract闭合后补operator defaults，源test覆盖默认off。
- Contract/signature changes: enabled:false/concurrency:4/decision-bindings:[]/start-bindings:[]；不设置实际profile/hostcontext。
- Input/output and state mapping: 一个新prefix，旧keys/默认与flag保持；不启tools/权限/后台worker。
- Error and edge behavior: 不生成其他profile文件/重复键/端点credential；strictunknown对可选配置依旧。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```yaml
# Add only the four governed-process defaults beneath the existing stellar.tianji.agent.tools hierarchy.
stellar:
  tianji:
    agent:
      tools:
        governed-process:
          enabled: false
          concurrency: 4
          decision-bindings: []
          start-bindings: []
# Preserve read-only-decision defaults; enabling both modes is rejected by the actual factory guards.
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: base唯一本profile四default键与Props匹配，默认运行行为不变。

#### File 12 — `MODIFY README.md`
- Purpose: 记录实际opt-in prerequisites/typedprofiles/hostresolver/资源生命周期与未验收边界。
- Symbols: AS native governed-process装配说明。
- Repository evidence: 前step README已有programmatic core和RO装配，现补真实newfactory默认off。
- Dependencies and consumers: newAS模式实际GREEN后root按最终实现写。
- Why now: 仅写已交付source behavior，不宣称权限激活/真实运行或全GoalDone。
- Contract/signature changes: 新flag与互斥/11Beans/mandatoryhostresolver/手动V13+V14前置。
- Input/output and state mapping: hostbusinessVersion不是DB proof，sourceactualSERVICE非USER OBO，union原结果访问/费用语义保留。
- Error and edge behavior: 无真实IAM/crypto/PG/model验收，AA/MCP及剩余蓝图后续；GlobalOTel只hostowned，不承诺Bean即Globalready。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```text
Document the explicit default-off governed-process mode and mutual exclusion with read-only-decision.
List the six prerequisite flags, manually controlled V13/V14 readiness, typed profile closure and mandatory stable qualified host resolver.
Explain owned lifecycle and actual SERVICE execution, retain business-version and runtime-validation limits, and do not announce complete platform delivery.
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: 文档与actual opt-in/readiness/root链和未完成目标一致。

#### File 13 — `CREATE docs/egon/plan/2026-11-01-10-15-governed-process-tool-assembly.md`
- Purpose: 冻结14paths/11Beans/完整合同与组件RED顺序。
- Symbols: Template5/Review/5aliases/17MC/10Rules/time。
- Repository evidence: 4c5e957 clean与root新SpecSHA已核；source合同A–D。
- Dependencies and consumers: actual paths/methods/qualifiers/skill资源。
- Why now: 只文档先行，root全文/strict/resources后才能源码GO；第一次READY后冻结。
- Contract/signature changes: Created10:15为root请求草稿时刻，Review沿legacy exactauth不伪Accepted。
- Input/output and state mapping: 测试可有最小compile声明，各组件真实RED后GREEN；单逻辑commit。
- Error and edge behavior: 不擅自扩source路径/私有API/SDK/backend或新profile。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```text
Record fourteen exact paths, eleven owned Bean names, effective baseline and source specification.
Map strict properties, mutually exclusive selection, qualified stable resolver and actual-root readiness to independent RED barriers and final tests.
Validate strictly, notify root only after all document self-checks, then freeze this plan until an explicit approved clarification.
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: root可全文/严格/资源复核后sourceGO，无悄改SHA。

#### File 14 — `CREATE docs/egon/evidence/2026-11-01-governed-process-tool-assembly.md`
- Purpose: 实际source/RED/GREEN/手工规则PENDING骨架与后续证据。
- Symbols: 5aliases/17MC/10Rules/Partial边界。
- Repository evidence: 当前仅计划，尚无SourceGO或本Step执行日志。
- Dependencies and consumers: 计划与后续root真实source/log/audits/static。
- Why now: plan READY后建PENDING，实施后逐项闭合。
- Contract/signature changes: 无预填测试数/功能PASS/真实authority/未来commithash。
- Input/output and state mapping: 配置/adapter/mockcatalog tests不是实际HTTP/PDP/crypto/SQL/LLM；旧23SQL bytefreeze待实际核。
- Error and edge behavior: docowner无source/YAML/README/Maven/commit，no repo harness。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；本块与§6精确合同，N/A有§12证据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守明确Bean名/Qualifier、strictmodel/mode、实际readiness/root、ownerlifecycle/默认off/旧兼容，沿现technical factory style。
- Implementation pseudocode:
```text
Create separate pending rows for five requirement aliases, seventeen manual checks and ten literal rules.
Preserve actual RED behavior, fixtures and compilation failures distinctly when root supplies logs, then close only evidenced source/test gates.
Keep default-off, no role activation, no real-environment acceptance and full-goal partial limits explicit; defer commit facts to Git delivery.
```
- Verification contribution: TEST-001/002/003/004/005按角色；实际结果后填，不用mockcatalog/factory tests推真实PG/IAM/crypto/model验收。
- After this file: evidence全PENDING，实际后填，不以strict替代实现或runtime验收。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace root。
- Verification command: §8 canonical source-reactor exact11类，由root统一运行，所有Javaowner冻结；docowner不Maven。
- Expected result: 各component真实RED后完整GREEN，默认off/旧lastQualifier/oldV13only/新V14require/actualroot/mandatoryresolver/lifecycle矩阵闭合；actualcount执行后填。
- Failure returns to: property/deepunknown回Props，mode/qualifier回AR或旧guard，actualreadiness/slot/resolver/globalLazy/资源回newCfg；无效fixtures先修重跑有效RED，不弱化原断言；需新library/SQL/deps/权限返回root不扩scope。
- Completion criteria: 14paths无operationalstub，17MC/10Rules逐项实际证据，23SQL bytes/finalsource/独审/diff/cached通过，不假真实环境或全Goal完成。
- Rollback: 回退binary/配置保持默认off，旧RO可单独使用V13；现V14表/SQL文件与facts保持，不repair/drop/delete/backfill，不自动重派。无新DDL撤销。
- Commit paths: stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfigurationTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java、stellar-enclosure-tianji-agent-server/src/main/resources/application.yml、README.md、docs/egon/plan/2026-11-01-10-15-governed-process-tool-assembly.md、docs/egon/evidence/2026-11-01-governed-process-tool-assembly.md
- Commit: `feat(agent): assemble governed process tools by explicit opt-in`

## 8. Test, Validation, and Quality Gates
root运行以下计划命令，并非声称已执行；所有Javaowner先冻结：
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

| Gate | Working directory | Classes/command | Proof | Boundary | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-001 Props | tianji-service | newPropertiesTest +旧ROPropertiesTest | default/bounds/immutable/256/bothnonempty/root-deep-lateindex/typedbackend | realBootBinder，非远程IAM或schema应用 | budget/root |
| TEST-002 modes/affinity | tianji-service | ARautoCfgTest/coreBridgeTest/oldROCfgTest；新增行为先RED | flags与lazyprovider/lastQualifier/earlyownedguard、新pointer同/foreign/null，旧7Beans不变 | 局部factoryguard，不承诺全Spring共享依赖不初始化 | tool_audit/budget/root |
| TEST-003 actualchain | tianji-service | newConfigurationTest；有效RED后GREEN | 6prereq/discovery-independent/slotclosure/AAdeny/actual13+14verify/mandatorystablehostresolver/globalLazy/rootmismatch/ownedclose/OTelbeforector | mockedDatasource/catalog+controlledIAM，no实际HTTP/DB/collector/app | root |
| TEST-004 compatibility | tianji-service | §8 exact11类 | 旧authority/grantBinding/nativeLoop/AccessStore/verifier/RO配置与模式回归 | 原protocol/cost/权限/time不变，no AA/MCP/真实runtime验收 | root |
| TEST-005 static/docs | workspace root/service | strict/diffcheck/显式14paths/23SQLbytecompare/YAMLkeys/独审 | v5/fileorder/trace/17MC/10Rules/源码范围 | no-index或cached检查untracked尾空白；无repo harness | root/doc |

Strict：`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-10-15-governed-process-tool-assembly.md --strict`。new2Test按scope创建，既有9selector与factoryhelper已rg核；无其他Maven/服务/DB/container/browser/GUI启动，验证不自动部署/权限激活。

## 9. Migration, Compatibility, Rollout, and Rollback
无SQL/schema/authority/library/Store/Recorder协议/依赖改变，全部23migration immutable；V13 f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3、V14 88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a保持。用户未来手动控制V13/V14部署；旧RO仍V13only，新union要求V14，readiness不是自动DDL。唯一application.yml四default值不启实际profile；hostresolver另由host稳定提供，无默认业务version/TTL。可回退binary并保持关闭，现表/SQL/facts不删改/repair/backfill；Agent excluded不做CRUD/MP迁移/codegen日志。无注册/激活/角色grant或USER OBO承诺。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | Step 1 | ARfactory/Test、旧ROcfg/Test、YAML | TEST-001/002/003/004 | 双flag冲突早拒；neither provider get；旧only/新only懒选择正确root，旧lastQualifier/ctor保持 |
| PLAN-REQ-002 | §11.3/13.2 | Step 1 | Props/Test、新Cfg/Test | TEST-001/002/003/004 | strictimmutableprops/deep unknown，DMN/IDW START fullslot exactclosure，拒AAprofile；uniondescriptor refs/full原resolver |
| PLAN-REQ-003 | §7/11.3/13 | Step 1 | 新Cfg/Test、Bridgehelper/Test | TEST-001/002/003/004 | 真实Prep/Recorder verify13、AccessStore verify14；mandatorystablequalified resolver无fallback；实际PDP/runAuth/ports/ledger/root身份全核 |
| PLAN-REQ-004 | §13.3/13.4 | Step 1 | newCfg/Test、旧兼容 | TEST-001/002/003/004 | 类型Otel provider先bridge ctor；globalLazy仍eager验证actualworker/root；只close自有HTTP/两pool，不关IAM |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | all/tests/README/docs | TEST-004/005 | 14paths、base唯一YAML四default键、23SQL bytes不改、有效RED后11类回归；全GoalPartial |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | 双mode provider/Bean意外同时执行 | AR早flagguard、old/newcfg ownedguard；distinct11names | root | Closed，fixedconflict code，局部guard不假全Spring初始化顺序 |
| DEC-002 | unusedparallelBean或globalLazy跳readiness | typed actualStores/Recorder+Lazyfalse actualbridge/worker/qualifiedrunAuth/mandatoryresolver pointer全核 | root | Closed，真verify/actualchain测试，不造readybool |
| DEC-003 | hostcontext被config/model默认生成 | mandatorystable qualifiedResolver.getObject，bridge.usesResolver，prototype/缺失/歧义fail | root | Closed，无fallback/getIfAvailable/业务version假proof |
| DEC-004 | typedbindings扩大authority/AA写 | actualGrant slots全tuple exactDMN/IDWSTART，newcfg禁AA；fullresolver不filter，bridge只unionrefs | root | Closed，复用原lib/无新policy激活 |
| RISK-001 | SpringOtelBean被误当Globalready或关闭callerSDK | typeprovider仅先初始化可用hostOtel，原env/cachedgate；close只ownedHTTP/2pool | root | Tracked，不Globalmutation/collector/runtime假验收 |
| RISK-002 | defaultOFF装配被误称全Goal完成 | 本步source/config而非真实IAM/PG/模型/AA/MCP | 用户/root | Tracked，GoalPartial/T3-08..T4后续，无自动运行 |

## 12. Review and Acceptance
root权威合同A–D、14paths/11names/实际helpers/qualifiers/defaults/资源owner均闭合，当前无协议/依赖未决点。以下PASS仅计划可执行性；Review沿legacy原exact授权，不伪Accepted。首次READY后冻结plan，root全文/strict/resources后发sourceGO；evidence实际结论先PENDING，实施/计数/MC/Rules/Git后按真实结果填，非runtime或全Goal验收。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR factory/AS技术@Configuration、原core/library SPI，14paths | 不引新层/框架/认证或DDL体系；Agent generator-excluded | None |
| MC-REUSE-001 | Applicable | PASS | 现fixedBindings/transports/Stores/Recorders/bridge/factory/oldBinder模式 | 新装配串真实existing对象，不重造protocol/parser/authority/默认resolver | None |
| MC-DEP-001 | Applicable | PASS | 现Spring Boot/JDK HttpClient/OTel/所有qualified IAM库 | 无新依赖/构建脚本/SDK变更 | None |
| MC-NAME-001 | Applicable | PASS | Properties record/11明确Bean名/usesActionContextResolver | 角色明确；只immutable operator值与technical factory，无新entity | None |
| MC-VALID-001 | Applicable | PASS | strict root/deep/lateindex、双mode、sixprereq、slot/ref/AA/actualroots/mandatoryresolver | constructor/metadata不是fresh IAMproof；运行Recorders原权限守卫不变 | None |
| MC-MODEL-001 | Applicable | PASS | immutable Props typedlists复用BindingVO/BackendVO；旧VO/protocol不变 | 每list<=256、bothenablednonempty、无identity/context/globaltimeout复制 | None |
| MC-CONVERT-001 | Not applicable | N/A | 复用typed config/backend/descriptor/slot tuple，无PO/entity转换 | 无新Mapper/Converter层或第二JSONparser | 原模型边界 |
| MC-LOG-001 | Not applicable | N/A | technical config无新业务日志，fixed safe异常/Properties redacted | 不泄露profile/body/token或冒充权限日志 | 无日志注入变更 |
| MC-BEAN-001 | Applicable | PASS | 11 distinct named @Bean/qualified params/@Lazy bridge/@Lazyfalse verifier/destroy | 现factory method注入风格，稳定hostResolver实际指针，no业务Service/Lombok伪注入 | None |
| MC-UTIL-001 | Applicable | PASS | 原Spring Binder/NoUnbound/JDK Duration/HttpClient与library/helpers | 不新通用工具/线程pool/loader/GlobalOTel mutation | None |
| MC-JSON-001 | Applicable | PASS | 原typed BindingVO/BackendVO/原codec与enum不变，仅YAML Binder | 无新JSON/ordinal/业务传输格式或model metadata | None |
| MC-TIME-001 | Applicable | PASS | 原backendtimeout100ms..30s/ownedHTTPconnect3s/hostcoreTTL与窗口保持 | 无globaltimeout/context配置或时钟改写，OTel only初始化顺序 | None |
| MC-CONFIG-001 | Applicable | PASS | actual仅base application.yml；gov四default键，两mode互斥 | disabledfalse不activation/rolegrant；各ownedfactory本地早guard，不假全Spring初始化排序 | None |
| MC-PATTERN-001 | Applicable | PASS | existingFactory/Adapter与eager actualchain verifier、原Facade/UoW/CAS不改 | 解决opt-in两mode/生命周期/readiness变化点，不新引擎/策略框架 | None |
| MC-SCOPE-001 | Applicable | PASS | 14paths/5mainJava/5tests/YAML+3docs，23SQL frozen | 无library/authority/Recorders/Stores/AA/MCP/schema/权限变化；bridge只1identityhelper | None |
| MC-TEST-001 | Applicable | PASS | Props/modes-affinity/ASactualfactory组件真实RED，最终11类含旧compat/globalLazy | 正向fixtures合法、编译/fixture错误不当RED；mockcatalog非SQL/IAM/runtime验收 | 实际执行后evidence填 |
| MC-BLOCKER-001 | Applicable | PASS | root合同A–D/实际paths/11Bean与qualifiers/已有helpers闭合，baseline/hash核 | 当前无待决协议，root全文/strict/resources后才sourceGO | None |

### 12.6 Final verdict
PASS — Ready for user review
