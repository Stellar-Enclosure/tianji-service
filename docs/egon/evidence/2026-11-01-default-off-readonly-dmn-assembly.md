# T3-07 默认关闭AS只读DMN装配执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度02:46—02:59 |
| 执行计划 | [默认关闭只读DMN装配](../plan/2026-11-01-02-41-default-off-readonly-dmn-assembly.md)，Review；主代理全文审阅/strict/resources后发source GO |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `70226e8b2b28e942ef2f9c6188d339dd33434e64e08598aaaccaa002b95f5e9d` |
| Baseline | `22f87f36edc70b148b9b0a108f85ee97fe638fa6` |
| 当前结果 | 最终9类91通过（AR28+AS63），0 failures/Errors/skips；完整T3-07/平台Partial |
| 提交 | 等主代理最终独审/文档与精确十路径stage后产生，hash以Git与交付消息为准 |

## 1. 实际实现与边界

十路径：新AS integration/process的Properties/Test、Configuration/Test；原FixedDecisionToolTransport/Test仅增六identity observers/断言；baseapplication.yml、README、新plan和本报告。无AR、SQL/V13、Components、API、权限或依赖修改。

Properties为strict@ConfigurationProperties(prefix stellar.tianji.agent.tools.read-only-decision,ignoreUnknownFields=false)不可变record，默认enabled=false，concurrency0→4且1..8，bindings null collection→empty/null element拒绝、copy/max256、enabled非空、toString固定脱敏。复用原BindingVO/BackendVO16字段和100ms..30s timeout，不复制authority descriptors/globaltimeout。

实际Boot4.1.2根级unknown处理未封闭collection元素的deep backend leaves，真实RED留下1项失败。因此Properties无状态实现EnvironmentAware，对每项backend和tool分别用原Binder/NoUnboundElementsBindHandler作独立根绑定，并核值与已绑定不可变字段相等；不保存Environment、不修改record、不手写DTO/schema白名单。异常固定且无私密cause。Props10项涵盖root/nested tool/backend/schema、第二index正反与已有字段验证，未放宽测试。

新@Configuration默认关闭，精确七Bean为toolInvocationStore、toolInvocationRecorder、tianjiFixedDecisionToolBindings、tianjiFixedDecisionToolHttpClient、tianjiFixedDecisionToolTransport、lazy tianjiAdkReadOnlyDecisionTools和@Lazy(false) tianjiReadOnlyToolRuntimeChainVerifier。api/storage/execution-grants/execution-authorization/runtime/tools.authority六前置各必须true，不要求dispatch discovery；关闭无DataSource/IAM/HTTP交互。Store和Recorder在各factory独立verifyStorage一次，callback不重复readiness，concrete参数确保global-lazy也会创建两者。

Fixed mappings与ACTUAL tianjiToolGrantBindings.bindings()中的READ_ONLY且DMN execute resource子集按(tenant,issuer,type,definition key/version/hash,完整tool ref)作无重复exact slot-set equality。原FixedBindings另核descriptor/backend digest/closed schema/timeout。共享authority其他query/write/nonDMN及未引用descriptor合法。Bridge只注册fixed refs对应descriptors；host resolver完整调用实际grantBindings.resolve，不静默filter，因此run暴露额外可见工具仍由原Bridge拒绝，不能部分执行。

Transport仅新增usesBindings/OAuth/Authentication/Converter/IssuerRegistry/HttpClient六个candidate==既有field的方法，null/外部实例均false，无业务变更。新factory使用四个现IAM精确Qualifier和owned HttpClient：Redirect.NEVER/connectTimeout3s，destroyMethod=shutdownNow；transport destroyMethod=close仅关原自有executor，不关shared IAM或重复接管client。

lazy bridge先按TYPE调用ObjectProvider<OpenTelemetry>.getIfAvailable再构造原ARbridge。允许无Bean而host已由javaagent/programmatic Global预初始化；不新增OTel Bean名/Global set/reset，不把Bean解析等同Global实例或exporter readiness。原literalenvfalse+cachedprivatefalse隐私门禁保持，AR未改。

eager SmartInitializingSingleton在真实Boot LazyInitializationBeanFactoryPostProcessor下复核prereqs、source/ledger/cap/Store/Recorder/authorizer/Transport六fields与HttpClient Redirect，随后取得实际qualified bridge/executor核消费identity。补充真实RED发现有效工具PDP和unused run authorizer仍可假就绪，故末尾追加qualified tianjiExecutionGrantAuthorizer，核runAuthorization.usesToolAuthorization(toolPDP)及executor.usesAuthorization(runAuthorization)；新增方法的assertAll覆盖两直接断链与actual@Primary foreign root context，旧12项保留。

base唯一YAML只增加enabled:false/concurrency:4/bindings:[]；不打开任何原flag。README记录实际AS装配及默认关闭/宿主遥测与限制，并修正原programmatic-only旧描述。真实启动、权限注册/激活/角色授予或业务网络均未执行。V13本次实际SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN与排除记录

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据/分类 |
| --- | --- | --- |
| Props/helpers有效RED | 02:46:05；28项9 failures/0 Errors/0 skips，exit1，18.352s | `/tmp/tianji-readonly-assembly-props-red.log`；Props9项8 failures，Transport19项仅新helper1 failure，原18通过；全部有效缺失行为 |
| 初次配置组合RED | 02:48:08；40项5 failures/4 Errors/0 skips，exit1，16.123s | `/tmp/tianji-readonly-assembly-config-red.log`；Props deepunknown真失败1，Transport19通过；Cfg12项4 failures/4 Errors中2项context被withBean意外重绑upstream authority props，不能算有效RED |
| 配置有效RED复跑 | 02:52:39；12项4 failures/4直接stub Errors/0 skips，exit1，15.941s | `/tmp/tianji-readonly-assembly-config-valid-red.log`；修为initialized singleton fixture后无fixture错误，未把stub Errors伪写为0 |
| 首次组合实现验证（非GREEN） | 02:54:13；41项0 failures/1 Error/0 skips，exit1，17.117s | `/tmp/tianji-readonly-assembly-green.log`；虽文件名green，Cfg fullresolver fixture使用run-one而非canonical UUID被原grant fingerprint拒绝；修固定UUID/matching lease，无断言削减；Props10/Transport19已通过 |
| root-chain错误selector（排除） | 02:57:15；0 tests，exit0、BUILD SUCCESS，14.539s | `/tmp/tianji-readonly-assembly-root-chain-red.log`；错误方法selector未执行任何测试，不作RED/GREEN或验收证明 |
| root-chain有效RED | 02:57:59；Cfg13项1 failure/0 Errors/0 skips，exit1，16.980s | `/tmp/tianji-readonly-assembly-root-chain-valid-red.log`；新第13方法assertAll三种真实gap均被接受，原12通过；随后补两root identity checks |
| 最终选择GREEN | 02:59:46；91通过、0 failures/Errors/skips，exit0、BUILD SUCCESS，23.305s | `/tmp/tianji-readonly-assembly-final.log`；AR28+AS63，源码冻结 |

实际最终命令，cwd=tianji-service（报告作者未执行）：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=TianjiReadOnlyDecisionToolPropertiesTest,TianjiReadOnlyDecisionToolConfigurationTest,TianjiFixedDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,TianjiToolAuthorityConfigurationTest,AdkReadOnlyDecisionToolBridgeTest,AdkToolTelemetryPrivacyTest,AdkReadOnlyDecisionLoopTest,TianjiAgentRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-readonly-assembly-final.log 2>&1
```

| 最终实际测试 | 通过数 | 证明范围 |
| --- | ---: | --- |
| 新Props10、Cfg13 | 23 | strict nested Binder/默认关闭/前置/映射/独立readiness/真实global-lazy实际run root/OTel顺序/owned lifecycle |
| 原Transport18+新helper1、FixedBindings10、AuthorityConfig11 | 40 | 六pure pointer observers、原传输/metadata与原authority链兼容 |
| AR AutoCfg6、Bridge9、Privacy3、Loop10 | 28 | 原默认关闭lazy/原native唯一loop与隐私回归；Privacy3含7实际bounded child probes，不能计成7 tests |
| 合计 | 91 | 九类相关非运行回归，0fail/error/skip；未选择PG测试 |

配置测试为nonweb ContextRunner和实际global-lazy postprocessor；storage verifier/IAM/HTTP是受控collaborators。默认关闭无外部交互、启用装配也不authorize/HTTP/模型/迁移。原Transport tests使用实际SDK/mock JDK bytewire而非真实网络，AR tests用真实Runner/FakeChatModel。7短子JVM无exporter并有界清理；没有真实IAM/crypto/PG/LLM或服务验收。0skip不表示PG执行过。

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 strict有限默认关闭配置 | Satisfied（本步） | Props10及baseYAML、deepunknown真实RED→independent roots；null/bounds/immutable/redacted | 不复制descriptors/timeout模型 |
| PLAN-REQ-002 exact固定DMN子集 | Satisfied（本步） | Cfg13/FixedBindings10：actual fullslot equality、额外query/write合法、完整resolver/额外visible failclosed | 元数据不是权限/身份 |
| PLAN-REQ-003 存储/源/实际消费链 | Satisfied（本步） | 两readiness独立；真实global-lazy、root链新增RED→GREEN，actual@Primary foreign root拒绝、所有affinity | 受控storage，非真实PG/IAM验收 |
| PLAN-REQ-004 遥测与生命周期 | Satisfied（本步） | OTel TYPE先bridge、允许无Bean、原双gate与7 child probes、owned client/transport关闭 | 不声称Bean=Global/exporter ready |
| PLAN-REQ-005 兼容/真实验证 | Satisfied（本步） | defaultoff无交互/六prereq、91相关回归/十路径/V13固定 | 无AR/SQL/API/权限/deps扩大，真实运行未验收 |
| 完整T3-07/平台蓝图 | Partial | 当前默认关闭AS只读装配能力 | 真实IAM/crypto/网络/PG/LLM、写dispatch/审批消费和平台后续未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AS integration/process配置/原AR接口、十路径diff | 无AR/Components/新层/API改动 | None |
| MC-REUSE-001 | Applicable | PASS | 原Store/Recorder/sharedVerifier/FixedBindings/Transport/Bridge/authority | 仅装配，未复制授权/HTTP/执行/DDL引擎 | None |
| MC-DEP-001 | Applicable | PASS | 既有Spring Binder/JDK HttpClient/OTel TYPE与原SDK | 无POM/dependency/framework新增 | None |
| MC-NAME-001 | Applicable | PASS | Properties/Configuration/七明确Bean名、六uses* | immutable配置值与技术Factory/observer角色明确 | None |
| MC-VALID-001 | Applicable | PASS | strict nested independent Binder roots、prereqs/fullslot set/pointer proof | Props10/Cfg13正反；deep unknown真实RED后封闭；模型/metadata不授权限 | None |
| MC-MODEL-001 | Applicable | PASS | immutable record/List.copyOf/null元素拒绝/max256/redacted、复用原BindingVO与BackendVO | 无重复descriptor/globaltimeout模型；EnvironmentAware无状态不保存env | None |
| MC-CONVERT-001 | Not applicable | N/A | 原BindingVO/BackendVO/DefinitionResourceRef直接绑定 | 无实体/DTO Mapper或新手工Converter层 | None |
| MC-LOG-001 | Not applicable | N/A | technical configuration/helper无业务日志Service | 固定安全异常/无私密cause，Props.toString脱敏 | None |
| MC-BEAN-001 | Applicable | PASS | 七namedBean/精确Qualifier、Lazybridge/eager callback、run root同指针 | 真实global-lazy actualroot验证，concrete Store roots各独立readiness；无业务Lombok DI伪称 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK/Spring Binder/NoUnbound handler/registry/verifier | 无Unsafe/GlobalOTel写/新工具平台，原组件直接复用 | None |
| MC-JSON-001 | Applicable | PASS | 原typed SDK/schema不改，真实nested configuration Binder和旧Transport19 | helper纯identity，无新wire/JSONcodec或业务协议 | None |
| MC-TIME-001 | Applicable | PASS | HttpClient connect3s、原Backend100ms..30s/transport窗口保持 | java.time，无globaltimeout/原deadline放宽；owned生命周期测试 | None |
| MC-CONFIG-001 | Applicable | PASS | 唯一baseapplication.yml的enabledfalse/concurrency4/bindings空 | strict prefix/parity测试通过；不打开既有flag或新profile | None |
| MC-PATTERN-001 | Applicable | PASS | 原@Bean Factory/Adapter+SmartInitializingSingleton实际链proof | 不以unusedBean作就绪证明，无第二运行/验证引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 当前十声明路径、V13实测固定、AR/SQL/权限/deps无差异 | 原Transport仅六pure pointer observers；source冻结 | None |
| MC-TEST-001 | Applicable | PASS | 有效RED与deepunknown/rootchain补充RED；最终九类91通过 | fixture重绑/非UUID错误与零测试误选均排除；7 child probes不是7test，PG未选 | None |
| MC-BLOCKER-001 | Applicable | PASS | 主代理生产/全部新增测试/README/本报告全文复核；Props/Transport与Config两次独审无阻断；91项通过、精确十路径及V13摘要已核 | 所有适用MC与Literal Rule已闭合；按显式十路径清单执行提交，不把平台未交付范围认作已完成 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Properties/Configuration/uses*及七精确Bean | 编译/source与91项回归 | 原技术工厂/immutable值/observer命名明确 | None |
| Rule 2 | Applicable | PASS | 严格Binder/BackendVO、actual fullslot set/prereqs/root pointers | Props10/Cfg13含deepunknown、second index、root漂移 | 每配置/技术边界校验真实生效，不信元数据授权限 | None |
| Rule 3 | Applicable | PASS | true immutable record/List.copyOf/原BindingVO、stateless EnvironmentAware | immutable/null/redacted及独立nested roots测试 | 不保存Environment或修改record字段，不复制DTO/转换层 | None |
| Rule 4 | Applicable | PASS | 七named @Bean/精确Qualifier、技术manual factory | actual global-lazy/readiness/root/生命周期测试 | 无新业务Service/日志或Lombok DI伪称 | None |
| Rule 5 | Applicable | PASS | 现JDK/Spring Binder/原库/OTel TYPE | imports/POM/source审阅 | 无新框架/utility/Unsafe/全局遥测写 | None |
| Rule 6 | Applicable | PASS | 原typed SDK/Backend schema与六pure helpers | 旧Transport18+helper1、nested Binder测试 | 无JSON/codec/传输业务变化 | None |
| Rule 7 | Applicable | PASS | 唯一base配置三key defaultfalse/4/空列表 | base YAML及strict properties真实绑定 | 无新timeout/descriptors副本、其他profile或默认启用 | None |
| Rule 9 | Applicable | PASS | 既有Factory/Adapter与eager actual-chain proof | global-lazy root/rediness/mapping/lifecycle测试 | 实际consumer同源，避免unusedBean假证明/DI cycle | None |
| Rule 10 | Applicable | PASS | Duration3s及原Backendtimeout/java.time | 时间边界与原Transport回归 | 原call/mono/DB期限不变，无globaltimeout | None |
| Rule 11 | Applicable | PASS | AS integration与原AR接口、十路径/V13固定 | 91相关测试及scope审阅 | 无新层/SQL/AR/Components/API/权限/deps扩张 | None |

## 6. 未验证与最终门禁

当前默认关闭的AS装配与原AR消费者连接已通过受控选择回归，不等于已部署或已在真实身份/数据库/模型环境验收。真实IAM/IdP/PDP/JWKS/crypto、网络、PostgreSQL、LLM、写工具dispatch/approval消费及平台后续仍未完成。

未自动启动应用、PG、HTTP listener、容器、浏览器/GUI、native scheduler或长期runtime；未注册/激活权限、授角色、部署、push、PR或release。无新仓库scratch/scripts/work脚本，无AR/SQL/V13修改或新依赖。短child probes非业务服务启动。

报告作者仅编辑本证据，未改source/YAML/README，未构建或提交。主代理已全文核对生产、新增测试、README与本报告，两组独审无阻断；精确十路径stage/commit在本报告定稿后执行，hash以Git记录为准，不预填提交事实。

**PARTIAL — Spec requirements are unmet or unverified**：本步九类91项相关回归通过；真实运行与完整T3-07/平台仍未完成。
