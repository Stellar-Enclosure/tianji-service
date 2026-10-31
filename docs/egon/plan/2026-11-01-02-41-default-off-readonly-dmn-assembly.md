# T3-07 默认关闭的AS只读DMN工具装配

| Field | Value |
| --- | --- |
| Document | `2026-11-01-02-41-default-off-readonly-dmn-assembly.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 02:41 Asia/Shanghai` |
| Updated | `2026-11-01 02:41 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | AS默认关闭readonly DMN装配；10路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@22f87f36edc70b148b9b0a108f85ee97fe638fa6`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户明确exact未完成工作执行授权，不存在Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `70226e8b2b28e942ef2f9c6188d339dd33434e64e08598aaaccaa002b95f5e9d` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [AR受治理native接线](2026-11-01-01-59-governed-adk-readonly-tools.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

已提交AR seam可消费受治理readonly工具，本步仅在AS默认关闭地装配现有Store/Recorder/固定DMN transport/bridge，并在global lazy下证明真实executor消费同一链。一个Step/commit、十路径；Transport只增六identity helper，不改业务。无AR/SQL/权限/新依赖或自动运行。

## 2. Target Spec and Effective Design

唯一effective设计为原蓝图§6/7/12/13及T3-07，用户原exact执行授权延续；局部工厂/配置合同由主代理闭合。复用原tool authority与现AR/AS技术扩展点，不把metadata、Spring Bean存在或OTel Bean解析当身份/权限/Global遥测证明。计划保持Review；真实IAM/crypto/PG/模型验收不在本步。

## 3. Effective Requirements and Acceptance

PLAN-REQ是原要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/13/T3-07 | 固定可审配置默认关闭且边界有限 | strict nested Binder、immutable/max256、并发1..8、启用非空，复用BackendVO16 | Props/Test/YAML |
| PLAN-REQ-002 | §12/13/T3-07 | exact固定DMN子集与authority一致，不能默默漏工具 | actual grant mappings中readonly DMN slot-set equality；其他query/write合法；运行visible额外工具failclosed | Config/Test |
| PLAN-REQ-003 | §7/13 | 源/存储/授权与实际runtime链一致 | 两独立readiness、actual global-lazy eager callback、所有affinity与actualexecutor/bridge核验 | Config/helpers/tests |
| PLAN-REQ-004 | §13.3 | 宿主遥测先初始化，资源所有权明确 | optional OTel bytype先resolve再Bridge；原双隐私gate；owned HttpClient/transport正确关闭 | Config/Test |
| PLAN-REQ-005 | §6/17 | 默认关闭兼容、严格前置与真实验证 | 每prereq缺失failclosed、默认无依赖交互、原transport/native回归；无SQL/AR/API/权限扩大 | 全部10paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy

先最小record/Configuration/helper声明供测试编译；各owner真实RED后最小GREEN，最后base配置/docs及相关回归。初始record不含目标校验、空Configuration不创建行为，helper暂false；不能用编译/fixture错误冒充RED，不提交scaffold。
### 4.2 Test-first strategy

Props/Test+Transport/Test由同owner，Configuration/Test另owner；独立RED→GREEN，全体单commit。配置测试nonweb ContextRunner与受控verifier/identity，actual LazyInitializationBeanFactoryPostProcessor证明global-lazy，不只设property；不启动BootServer/DB或远端请求。
### 4.3 Sequential and parallel boundaries

Props/六helper签名锁定后两组件独立RED→GREEN并行，文件所有权不交叉；root独占YAML/README/Maven。文档owner不构建。AS只复用已提交AR，不并行下一Step。
### 4.4 Commit boundaries

§5十路径一个逻辑默认关闭装配commit，显式stage；无push/历史改写/系统启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| AS factory/finite props | T3-07实际native消费 | AR provider seam已存在；原authority Factory模式 | 装配原Store/Recorder/Bindings/Transport/Bridge，不造新引擎 | Implement |
| 六identity helpers | §13实际链同源 | Transport已持六final字段但不公开identity | 仅==观察，不加状态/业务逻辑 | Implement |
| eager proof/OTel ordering | §7/13.3 | 原SmartInitializingSingleton、AR lazy Supplier与privacy gate | concrete Store参数+provider callback，避免DI cycle/unusedBean假证明 | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Props/helpers | PLAN-REQ-001/003 | Binder/identity正反 | 仅最小声明、旧fields | strict配置/同指针观察 | Configuration | Step 1 |
| Config/actual链 | PLAN-REQ-002/003/004/005 | prerequisite/mapping/global-lazy/OTel/lifecycle | 上项、已提交AR/authority | defaultoff实际同源装配 | 用户后续环境验收 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AS integration/process与原security Config，现AR库 | 原技术Adapter profile保持 | 仅Factory/immutable props/helpers，无新层/业务Service | Step1；MC-ARCH-001 |
| Validation/model | 原immutable Properties/BindingVO/BackendVO | 复用验证不复制模型 | strict unknown、finite Lists、exactfullref slots/redacted | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/log/config | 原named @Bean/@Qualifier/手工技术工厂 | 七names/生命周期锁定 | 无业务Lombok DI伪称；无日志secret；仅base新三key | Step1；MC-BEAN-001、MC-CONFIG-001 |
| Pattern/time/JSON | 原Factory/Adapter、sharedVerifier、typedSDK、java.time | 只真实装配和核验 | 不造协议/引擎；不延长原窗口 | Step1；MC-PATTERN-001、MC-TIME-001、MC-JSON-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| authority/storage/native | 原Config/Bindings/Recorder/sharedVerifier/ARbridge | 同源helpers及verifyStorage已存在 | 只缺AS factory | reuse | None | Step1；MC-REUSE-001 |
| fixedtransport/IAM | 原Transport final六fields、实际IAM beans | timeout/oneShot/close已实现 | 只缺identity observer/owned wiring | reuse | None | Step1；MC-DEP-001 |
| lazy/telemetry | 原Boot postprocessor/OTel type/AR gate | AS TianjiAgentStoragePostgresTest:81 precedent | 足够，无Global写或新starter | reuse | None | Step1；MC-UTIL-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/12/13/T3-07 | Properties/Configuration/uses*与namedBeans | tests→record/helpers→config | 角色名称和Bean稳定 | 编译/source | Step1 | PASS |
| Rule 2 | §6/7/12/13/T3-07 | Binder/BackendVO/fullref slot/pointer | 新tests/config | strict unknown/校验前置与真实链同源 | 正反/context | Step1 | PASS |
| Rule 3 | §6/7/12/13/T3-07 | immutable record与原BindingVO | Props/config | List.copyOf/max256/redacted，不复制DTO/Converter | 构造/嵌套绑定 | Step1 | PASS |
| Rule 4 | §6/7/12/13/T3-07 | 现技术@Bean named/Qualifier | Config | 七精确names/qualified依赖、manual技术构造，无新业务Service | wiring/global-lazy | Step1 | PASS |
| Rule 5 | §6/7/12/13/T3-07 | JDK/Spring/原组件 | 全部source | 无新工具/全局遥测写 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/12/13/T3-07 | 原typed SDK/Backend schema | 旧Transport/Test、新PropsTest | 不改JSON/codec/业务，strict nested binding | 原SDK回归 | Step1 | PASS |
| Rule 7 | §6/7/12/13/T3-07 | 仅baseapplication.yml | YAML/Props/Test | enabledfalse/concurrency4/bindings空，prefix一致，无timeout副本 | 配置结构/默认关闭 | Step1 | PASS |
| Rule 9 | §6/7/12/13/T3-07 | 既有Factory/Adapter+eagerproof | Config/Tests | actual消费链而非unusedBean证明，独立readiness/owned lifecycle | global-lazy/rootmismatch | Step1 | PASS |
| Rule 10 | §6/7/12/13/T3-07 | Duration 3s/Backendtimeout | Config/Props/Test | java.time，原call/wall/mono不变，无globaltimeout | 时间边界/回归 | Step1 | PASS |
| Rule 11 | §6/7/12/13/T3-07 | 原AS/AR接口边界 | 所有10paths | 不新层/deps/SQL/API/权限/AR/Components | scope/旧回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | HttpClient connectTimeout/BackendVO.timeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无UI | owned HttpClient connect3s；原Backend100ms..30s仍由既有constructor与overallwindow控制，不新增globaltimeout | 原Clock/mono/DB不改 | 不改变任何callDeadline或SDK超时行为 | Step 1；TEST-001/002边界与原transport回归 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolPropertiesTest.java | 定义strict properties/Binder合同 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java | 定义七Bean/readiness/真实消费链与兼容 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java | 锁定六个identity helper语义并保留旧业务回归 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolProperties.java | 固定默认关闭与有限profile列表 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java | 仅让实际链校验观察已持有的六个对象 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java | 默认关闭装配并证明实际runtime消费同一链 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | 在唯一base配置显式保持新能力关闭 | Step1 |
| MODIFY | README.md | 准确记录AS默认关闭装配、宿主遥测责任及边界 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-default-off-readonly-dmn-assembly.md | 仅在实际执行后填写结果/两套矩阵 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-02-41-default-off-readonly-dmn-assembly.md | 锁定十路径及实施顺序 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline22f87f3 clean/blueprint SHA已核；writing resources18md24refs PASS。§8所有既有selector类已rg核，新增2Test按scope创建；AS资源只有baseapplication.yml，无其他profile需增路径。精确十路径，不改AR/Components/SQL/API/权限/依赖/transport业务。旧V13 SHA保持f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3；不迁移/repair。

Props prefix stellar.tianji.agent.tools.read-only-decision、ignoreUnknownFields=false；record(enabled,int concurrency,List<FixedBindings.BindingVO>)，boolean默认false；0→4且1..8；null collection→empty、null element拒绝、immutable/max256、enabled非空、redacted。只复用既有BackendVO16字段与timeout100ms..30s，不复制authority descriptors/globaltimeout。

Plan Clarification PLAN-CLAR-001：实际Boot4.1.2的NoUnboundElementsBindHandler把已绑定collection元素中的backend深层未知字段视为被覆盖项，ignoreUnknownFields=false本身不足以兑现本步闭合合同（已由真实测试复现）。Properties可无状态实现EnvironmentAware，在初始化时以独立的每项backend/tool根，复用Binder和NoUnboundElementsBindHandler对现有BackendVO/DefinitionResourceRef重新严格绑定并核与不可变字段相等。不得保存Environment、修改record字段、复制DTO/schema白名单、增加Bean/依赖或放松未知字段测试；异常安全且无私密cause。仅落实既定deep validation，在原Props/Test路径内实现。

Plan Clarification PLAN-CLAR-002：实际消费链还必须连接原CLAIM/MODEL工具授权root。chainVerifier在既有参数末尾追加@Qualifier("tianjiExecutionGrantAuthorizer") TianjiExecutionGrantAuthorizer runAuthorization，并验证runAuthorization.usesToolAuthorization(authorization)及executor.usesAuthorization(runAuthorization)，防止一个未被worker消费的有效run authorizer形成假就绪证明。该root与两个identity helper已存在；仅补齐原actual-chain合同，不增加Bean、路径或权限。新增断链负例先实际RED，再实现检查；positive global-lazy在任何getBean前断言bridge/executor已是singleton。

七Bean精确合同（所有受控factory复核prereq）：

| Bean name | Factory dependencies/creation | Lifecycle/contract |
| --- | --- | --- |
| toolInvocationStore | DataSource,ledger,@Qualifier(toolCapabilityStore),@Qualifier(tianjiToolGrantAuthorizer),Environment；new原Store | 独立verifyStorage一次，failclosed |
| toolInvocationRecorder | 上项roots+@Qualifier(toolInvocationStore)；new原Recorder | 自身独立verifyStorage一次，不能以Store成功替代 |
| tianjiFixedDecisionToolBindings | authorityProps,newProps,@Qualifier(tianjiToolGrantBindings),Environment | 原FixedBindings(authority.descriptors,profiles)，exact DMN slot closure |
| tianjiFixedDecisionToolHttpClient | Environment；JDK newBuilder Redirect.NEVER/connectTimeout3s | owned，destroyMethod=shutdownNow |
| tianjiFixedDecisionToolTransport | @Qualifier固定bindings、idpServiceOAuth2Client、tenantIssuerAuthenticationManagerComponent、verifiedPrincipalConverter、trustedIssuerRegistry、固定HttpClient，newProps,Environment | 原constructor(...,concurrency)，destroyMethod=close只关原自有executor；不关shared IAM |
| tianjiAdkReadOnlyDecisionTools | @Lazy；Recorder,Transport,authorityProps,fixedBindings,actualGrantBindings,ObjectProvider<OpenTelemetry>按TYPE,Environment | 先getIfAvailable，再new ARBridge；只注册fixed refs引用descriptor |
| tianjiReadOnlyToolRuntimeChainVerifier | @Lazy(false) SmartInitializingSingleton；下面具体roots与qualified providers | afterSingletonsInstantiated强制actual bridge/executor与affinity，不重复readiness |

class使用@Configuration(proxyBeanMethods=false)、ConditionalOnProperty(prefix新tools.read-only-decision,name enabled,havingValue true)、EnableConfigurationProperties。prereqs完整为stellar.tianji.agent.api/storage/execution-grants/execution-authorization/runtime.enabled和tools.authority.enabled；每项必须true，不要求dispatch discovery。默认关闭不触DataSource/IAM/HTTP。配置期间不authorize/发HTTP/调用模型/discovery/迁移/全局遥测写。

Mapping closure：使用ACTUAL tianjiToolGrantBindings.bindings()，而非复制authority.properties.bindings；按authority注册exact完整ref解析descriptor，只选择READ_ONLY且executionResourceKey==DecisionClient.EXECUTE_RESOURCE_KEY。构造唯一full slot(tenantId,issuerFingerprint,principalType,definitionKey,definitionVersion,definitionSha256,full tool ref)。profiles用ownerIssuerFingerprint/definitionContentSha256映射相同slot；双方无重复且set exact equality。FixedBindings原constructor另核fullref/backend digest/closed DMN schema/timeouts。其他query/write/nonDMN grant及未引用descriptor保持合法。Bridge只注册fixed profiles exact refs对应authority descriptors；host resolver必须完整actualGrantBindings.resolve(material).map(ResolvedToolResult::binding)，不静默filter结果，因此run若还暴露其他工具就由原Bridge exact visible/size核验failclosed，不部分执行。

chainVerifier精确roots：Environment/source/ledger/@Qualifier(toolCapabilityStore) capabilities/@Qualifier(tianjiToolGrantAuthorizer) authorization/@Qualifier(tianjiToolGrantBindings) grantBindings/@Qualifier(toolInvocationStore) preparations/@Qualifier(toolInvocationRecorder) recorder/@Qualifier(tianjiFixedDecisionToolBindings) bindings/@Qualifier(tianjiFixedDecisionToolHttpClient) http/@Qualifier(tianjiFixedDecisionToolTransport) transport，以及上述四精确IAM Qualifier。最后为@Qualifier(tianjiAdkReadOnlyDecisionTools) ObjectProvider<Bridge>和@Qualifier(tianjiAdkAgentRunExecutor) ObjectProvider<Executor>。callback先prereqs，再ledger/cap/Store/Recorder exact DS/ledger/cap/auth/preparation helpers；authorization.usesLedger/usesBindings/usesIssuerRegistry；transport新增六uses*各candidate==对应原field；http Redirect.NEVER；实际bridge=provider.getObject/executor=provider.getObject，核worker.usesLedger/usesReadOnlyDecisionTools、bridge.usesRecorder/usesTransport。direct concrete Store/Recorder参数确保global-lazy下各factory+readiness执行；callback不重复verify。

OTel仅byTYPE getIfAvailable在Bridge ctor前解析；允许无Bean的host已由javaagent/programmatic Global预初始化，无新Bean名/Global set/reset/newdep，Spring Bean存在也不证明就是Global或exporter已就绪。原literalenvfalse+cachedprivatefalse gate仍判定，不修改AR。配置测试用实际LazyInitializationBeanFactoryPostProcessor，非只传spring.main.lazy-initialization属性。两owner API均已闭合，无未决语义；root核全计划后方sourceGO。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 默认关闭装配并核验实际只读DMN运行链
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交22f87f3 AR bridge/provider/identity seam及既有authority/FixedBindings/Transport/Store/Recorder。
- Baseline state: AR已可programmatic运行，AS缺新profile/config/实际consumer readiness，Transport缺六identity观察。
- Observable outcome: 默认关闭无交互；显式开启且全部前置/映射/源一致时真实executor消费同一已验证readonly链。
- End state: 七AS namedBeans/strict配置可用；真实IAM/PG/模型及写审批未验收，不自动启用runtime。
- Test-first gate: Required — 最小声明供编译，各组件正向/负向实际RED后GREEN；无fixture/编译伪RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolPropertiesTest.java`
- Purpose: 定义strict properties/Binder合同。
- Symbols: TianjiReadOnlyDecisionToolPropertiesTest.java；§6对应精确合同。
- Repository evidence: 同包新Test；原TianjiToolAuthorityProperties及真实Binder测试。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 最小record声明供编译后真RED；null collection空/null element拒绝、enabled非空、1..8、max256/immutable/redacted。
- Input/output and state mapping: 真实嵌套BindingVO/BackendVO，根及nested unknown、URI/schema/timeout非法都拒绝；不新增timeout/descriptors。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
@Test strict_binding_reuses_the_existing_fixed_backend_contract() {
  assertDisabledDefaultsNullListEmptyAndZeroConcurrencyFour(); assertEnabledEmptyNullElementBoundsAndMutationRejected();
  bindActualNestedBackendWithSpringBinder(); assertRootAndNestedUnknownUriSchemaTimeoutFail(); assertRedactedToString();
}
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java`
- Purpose: 定义七Bean/readiness/真实消费链与兼容。
- Symbols: TianjiReadOnlyDecisionToolConfigurationTest.java；§6对应精确合同。
- Repository evidence: 现ApplicationContextRunner/authority config tests；AS TianjiAgentStoragePostgresTest:81真实LazyInitializationBeanFactoryPostProcessor。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 空Configuration/最小签名供编译后真RED；默认关闭无依赖交互；逐prereq、readiness分别失败。
- Input/output and state mapping: 实际global-lazy postprocessor；exact mapping子集与错误slot；mock verifier不跑PG；OTel先于bridge；actual root mismatch/owned lifecycle。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
@Test configured_readonly_chain_is_eagerly_verified_even_under_actual_global_lazy() {
  useNonWebContextRunnerAndActualLazyInitializationBeanFactoryPostProcessor(); assertDisabledHasNoDataSourceIdentityOrHttpInteractions();
  assertEachPrerequisiteMissingFailsAndBothStoreReadinessExecuteIndependentlyOnce();
  assertExactDmnSubsetClosureOtherToolsRemainLegalAndActualRootPointersCannotBeReplaced();
  assertTelemetryTypeResolutionBeforeBridgeAndOwnedHttpShutdownTransportCloseOnly();
}
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java`
- Purpose: 锁定六个identity helper语义并保留旧业务回归。
- Symbols: TianjiFixedDecisionToolTransportTest.java；§6对应精确合同。
- Repository evidence: 原同名真实SDK/mock JDK bytewire测试。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: helper最小false声明后正向sameidentity真实RED，已有transport业务测试不得放宽。
- Input/output and state mapping: 每field相同实例true，null/外部实例false；不触发authorize/HTTP/生命周期。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
@Test helper_identity_exposes_only_the_six_actual_constructor_collaborators() {
  assertSameBindingsOAuthAuthenticationConverterIssuerRegistryAndHttpClientMatch();
  assertNullAndDistinctCollaboratorsAllFailIdentityChecks(); verifyNoIdentityOrHttpMethodInvokedByHelpers();
}
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolProperties.java`
- Purpose: 固定默认关闭与有限profile列表。
- Symbols: TianjiReadOnlyDecisionToolProperties.java；§6对应精确合同。
- Repository evidence: 沿原authority的@ConfigurationProperties immutable record风格。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: public record(boolean enabled,int concurrency,List<TianjiFixedDecisionToolBindings.BindingVO> bindings)；prefix stellar.tianji.agent.tools.read-only-decision，ignoreUnknownFields=false。
- Input/output and state mapping: null list→List.of；List.copyOf拒null element；concurrency0→4/1..8；max256；enabled非空；redacted。BackendVO16与100ms..30s保持。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
public record TianjiReadOnlyDecisionToolProperties(boolean enabled,int concurrency,List<BindingVO> bindings) {
  canonicalConstructor { concurrency = concurrency == 0 ? 4 : concurrency; bindings = bindings == null ? List.of() : List.copyOf(bindings); requireBoundsAndEnabledNonempty(); }
  public String toString() { return "TianjiReadOnlyDecisionToolProperties[redacted]"; }
}
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java`
- Purpose: 仅让实际链校验观察已持有的六个对象。
- Symbols: TianjiFixedDecisionToolTransport.java；§6对应精确合同。
- Repository evidence: 原bindings/oauth/authentication/converter/issuers/http final字段。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 新增public boolean usesBindings/usesOAuth/usesAuthentication/usesConverter/usesIssuerRegistry/usesHttpClient；每个candidate==对应field。
- Input/output and state mapping: 无业务/签名/超时/close行为变更，不新保存依赖或创建clients。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
public boolean usesBindings(TianjiFixedDecisionToolBindings candidate) { return candidate == bindings; }
public boolean usesOAuth(IdpServiceOAuth2Client candidate) { return candidate == oauth; }
// usesAuthentication/usesConverter/usesIssuerRegistry/usesHttpClient apply the same exact-pointer rule to their existing fields.
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 6 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java`
- Purpose: 默认关闭装配并证明实际runtime消费同一链。
- Symbols: TianjiReadOnlyDecisionToolConfiguration.java；§6对应精确合同。
- Repository evidence: 原authority Configuration/SmartInitializingSingleton与AR lazy provider seam。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: @Configuration(proxyBeanMethods=false)、ConditionalOnProperty新prefix enabled=true、EnableConfigurationProperties；§6七Bean及helpers合同。
- Input/output and state mapping: 每prereq failclosed；actual mapping slot equality；各store独立verify；lazy bridge先OTel bytype；eager callback actual链；不RPC/迁移/模型调用。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```java
@Bean toolInvocationStore(...) { requirePrerequisites(environment); value = new ToolInvocationStore(source,ledger,capabilities,authorization); value.verifyStorage(); return value; }
@Bean toolInvocationRecorder(...) { requirePrerequisites(environment); value = new ToolInvocationRecorder(source,ledger,preparations,capabilities,authorization); value.verifyStorage(); return value; }
@Bean fixedBindings(...) { requireExactDmnGrantSlotSetEqualsConfiguredProfileSlots(); return new TianjiFixedDecisionToolBindings(authority.descriptors(),properties.bindings()); }
@Bean @Lazy bridge(...) { telemetry.getIfAvailable(); return new AdkReadOnlyDecisionToolBridge(recorder,transport,exactReferencedDescriptors,fullActualGrantResolver); }
@Bean @Lazy(false) SmartInitializingSingleton chainVerifier(...) { return () -> { requirePrerequisitesAndAllExactAffinities(); requireActualBridgeAndExecutorConsumption(); }; }
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Purpose: 在唯一base配置显式保持新能力关闭。
- Symbols: application.yml；§6对应精确合同。
- Repository evidence: resources inventory仅application.yml；tools.authority已默认false。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: stellar.tianji.agent.tools.read-only-decision: enabled:false / concurrency:4 / bindings:[]。
- Input/output and state mapping: 不添加globaltimeout/descriptors副本/秘密/部署profile；不打开现runtime/authority。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```yaml
stellar:
  tianji:
    agent:
      tools:
        # No duplicated timeout/descriptors: existing authority/profile contracts own them.
        read-only-decision:
          enabled: false
          concurrency: 4
          bindings: []
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 8 — `MODIFY README.md`
- Purpose: 准确记录AS默认关闭装配、宿主遥测责任及边界。
- Symbols: README.md；§6对应精确合同。
- Repository evidence: 现programmatic AR/Recorder/authority说明。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 说明七Bean、各前置、DMN子集、bridge严格全可见集合、OTel可选bytype与envcached gate。
- Input/output and state mapping: 不把Bean存在当Global/exporter验证，不把配置元数据当权限、真实网络/PG/crypto通过。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```text
Document default-off configured AS assembly, finite fixed profiles and all prerequisite flags.
Document eager actual-chain verification, separately verified stores and host telemetry ordering without global mutation.
Retain real IAM/PG/LLM/write-approval limitations and no automatic runtime-start claims.
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 9 — `CREATE docs/egon/evidence/2026-11-01-default-off-readonly-dmn-assembly.md`
- Purpose: 仅在实际执行后填写结果/两套矩阵。
- Symbols: 2026-11-01-default-off-readonly-dmn-assembly.md；§6对应精确合同。
- Repository evidence: 既有docs/egon/evidence模板。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 5aliases、17MC/10Rule、RED/GREEN实际日志计数、scope/V13、global-lazy/生命周期/OTel限制。
- Input/output and state mapping: 未执行阶段PENDING；fixture错误与stub错误分开；不预填真实crypto/PG/网络结果。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```text
Record actual RED and GREEN logs, exit codes, counts and timestamps after root executes them.
Evaluate seventeen MC and ten literal Rule rows independently from the final source and tests.
Trace five local aliases and retain full T3-07 Partial with explicit runtime and cryptographic limits.
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

#### File 10 — `CREATE docs/egon/plan/2026-11-01-02-41-default-off-readonly-dmn-assembly.md`
- Purpose: 锁定十路径及实施顺序。
- Symbols: 2026-11-01-02-41-default-off-readonly-dmn-assembly.md；§6对应精确合同。
- Repository evidence: blueprint/已提交AR seam/本scope实际source与owner闭合API。
- Dependencies and consumers: §6原typed域值/配置factory及现AR消费者；source只在本组件有效RED之后实现。
- Why now: 先声明与行为测试，再最小实现/工厂/文档；本文件按本序解锁相邻消费者。
- Contract/signature changes: 保持Review/legacy exact授权，先strict与root全文审阅后sourceGO。
- Input/output and state mapping: 不发明Accepted；不扩AR/SQL/配置profile/权限/deps，不先写代码。
- Error and edge behavior: 默认关闭无调用；无效映射/前置/identity/readiness失败安全拒绝，shared资源不被错误关闭。
- Standards impact: MC-ARCH-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-CONFIG-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；原技术工厂/immutable值/typed协议复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件以上边界与§6 exact Bean/validation/config合同执行，N/A见§12。
- Implementation pseudocode:
```text
Lock baseline22f87f3, blueprint SHA and the exact ten paths before source edits.
Check component-specific real RED barriers, seven named factories, exact pointer and mapping closure contracts.
Run strict Plan validation and hand off only the reviewed bounded implementation scope.
```
- Verification contribution: TEST-001/002/003；本角色对应正反/兼容/静态证据，实际计数归报告。
- After this file: 对应组件已过自身RED→GREEN或文档门禁；无自动外部runtime/权限激活。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiReadOnlyDecisionToolPropertiesTest,TianjiReadOnlyDecisionToolConfigurationTest,TianjiFixedDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,TianjiToolAuthorityConfigurationTest,AdkReadOnlyDecisionToolBridgeTest,AdkToolTelemetryPrivacyTest,AdkReadOnlyDecisionLoopTest,TianjiAgentRuntimeAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 各组件真实RED后9类选定回归GREEN，defaultoff无依赖调用、eachprereq/readiness/global-lazy actualroot/OTel/lifecycle负例闭合；实际数量以日志为准。
- Failure returns to: fixture/compile回对应Test，映射/Bean/时序失败回Config；若需AR/SQL/deps/API/权限扩展回root，不自行扩scope。
- Completion criteria: 五别名、17MC/10Rule实际证据、十路径审阅、独立review无阻断与指定回归通过。
- Rollback: 仅十路径回退/forward-fix；已有journal/SQL不可删改，配置保持defaultfalse；无数据迁移。
- Commit paths: stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolPropertiesTest.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfigurationTest.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolProperties.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiReadOnlyDecisionToolConfiguration.java、stellar-enclosure-tianji-agent-server/src/main/resources/application.yml、README.md、docs/egon/evidence/2026-11-01-default-off-readonly-dmn-assembly.md、docs/egon/plan/2026-11-01-02-41-default-off-readonly-dmn-assembly.md
- Commit: `feat(agent): assemble opt-in read-only DMN runtime`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7命令focused组件selector→完整9类 | 新Props/Config/helper真实缺失行为 | 各组件独立RED→GREEN，stub/fixture错误照实区分 | root Maven only |
| TEST-002 wiring/lifecycle | tianji-service | 新ConfigTest实际非web ContextRunner+LazyInitializationBeanFactoryPostProcessor | 默认无调用、六prereq、独立readiness、mapping子集、actualroot drift、OTel先构造、owned close | 真实factory/SDK对象结构，受控verifier/IAM/HTTP，无SQL/网络/模型调用 | 非运行/非crypto验收 |
| TEST-003 scope/config | tianji-service | git diff --check、10paths/V13sha；实际strict nested Binder | singlebase三key/defaultfalse、无新timeout/descriptor副本、六helper纯identity | 旧transport/native/authority回归及范围准确 | 无PGtest/自动环境 |
| TEST-004 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-02-41-default-off-readonly-dmn-assembly.md --strict` | metadata/file/trace/MC | exit0，不表示实现测试通过 | 文档owner |

## 9. Migration, Compatibility, Rollout, and Rollback
无迁移/新权限/新依赖/AR/Components变更；旧V13 immutable。Transport六纯identity helper保留既有业务行为。base显式enabledfalse/concurrency4/bindings空，不能触source/IAM/HTTP；只有用户/部署明确开启全部prereqs并提供fixed映射后使用，配置不注册权限/授角色。OTel宿主初始化与真实PG/IAM/模型由后续环境验收；不自动启动应用/DB/GUI/浏览器。回滚十路径保留既有journal和SQL，无repair。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/13 | Step 1 | Props/Test/YAML | TEST-001/003 | strict immutable有限配置defaultoff |
| PLAN-REQ-002 | §12/13 | Step 1 | ConfigTest/FixedBindings factory/bridge resolver | TEST-001/002 | exact DMN子集闭合，额外visible工具failclosed |
| PLAN-REQ-003 | §7/13 | Step 1 | 两Store factory/eager verifier/六helpers | TEST-001/002 | global-lazy实际消费链/独立readiness/同源identity |
| PLAN-REQ-004 | §13.3 | Step 1 | lazybridge/OTel TYPE/owned clients | TEST-002 | 顺序+资源所有权，原privacy gate不变 |
| PLAN-REQ-005 | §6/17 | Step 1 | 全十路径/旧回归/docs | TEST-001/003/004 | 默认兼容与真实证据，不声明平台全完成 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | sharedauthority额外工具 | actual DMN fullslot set equality；bridge仅fixed refs，完整host resolver不静默filter | root/config owner | Closed：其他映射可配置，但混用run按原bridge failclosed |
| DEC-002 | lazy未验证/DI cycle | concrete Store roots+eager callback、bridge/executor qualified providers；原lazy Supplier | root | Closed：实际postprocessor测试，无重复readiness |
| DEC-003 | OTel类型与Global不一致 | 只resolve byTYPE先于bridge，host可无Bean已预初始化；原cached/env gate判定 | root/host | Closed：不宣称Bean=Global/exporter proof，无全局写 |
| RISK-001 | 真实运行未验收 | 当前nonweb/受控IAM+storage，不调用真实模型/PG/网络 | 用户/root | Tracked：完整T3-07/平台Partial，写审批与环境验收后续 |

## 12. Review and Acceptance
本计划只装配既有AR/AS能力，不改变授权/传输/持久业务。十路径、每组件真实RED、单一逻辑commit及原默认关闭兼容均明确。主代理闭合映射、七Bean、pointer/OTel/lifecycle合同；legacy用户授权允许继续但文档保持Review。下面仅计划级可执行性结论，实施报告按真实source/log逐项重评。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AS integration/process配置+既有AR库，10路径 | 现模块/技术Adapter边界保持，无AR/Components/新层改动 | None |
| MC-REUSE-001 | Applicable | PASS | 原FixedBindings/Transport/BackendVO/Registry/Recorder/sharedVerifier/ARbridge | 只装配现能力，不复制授权/HTTP/执行/DDL引擎 | None |
| MC-DEP-001 | Applicable | PASS | 既有Spring/Boot/JDK HttpClient/OTel TYPE依赖 | 不新增POM/deps/SDK或全局遥测框架 | None |
| MC-NAME-001 | Applicable | PASS | Properties/Configuration/Test、七精确Bean名、uses* | 技术值/工厂角色明确，record为immutable配置值 | None |
| MC-VALID-001 | Applicable | PASS | strict Binder/BackendVO、actual grant slot equality、pointer affinities | 每配置/技术边界精确校验，无新HTTP/CQE请求 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变Properties/List.copyOf/原BindingVO复用 | 无重复descriptors/globaltimeout模型或实体转换 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无实体/DTO Mapper新增，原BindingVO/BackendVO直接绑定 | 不建手工Converter层 | None |
| MC-LOG-001 | Not applicable | N/A | 配置factory/identity helpers无新业务日志Service | 固定安全异常、配置toString脱敏，无token/后端正文日志 | None |
| MC-BEAN-001 | Applicable | PASS | 七named @Bean、精确IAM Qualifier、Lazy/eager callback | 沿现技术工厂构造，不伪称Lombok业务DI；destroy所有权明确 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Spring/原registry/verifier/transport | 无新utility、Unsafe、GlobalOTel mutation | None |
| MC-JSON-001 | Applicable | PASS | 原typed SDK/Backend schema复用；helper业务不改 | 真实nested Binder+旧transport回归，不新协议/codec | None |
| MC-TIME-001 | Applicable | PASS | §4.9 HttpClient 3s与原Backend 100ms..30s/原事务门禁 | 不加globaltimeout或放宽原deadline | None |
| MC-CONFIG-001 | Applicable | PASS | base唯一application.yml，strict prefix/defaultfalse | 三新key结构明确，无新增profile或开启现flag | None |
| MC-PATTERN-001 | Applicable | PASS | 原@Bean Factory/Adapter+现SmartInitializingSingleton proof | 只完成实际消费链装配，不造第二运行/验证层 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确10路径，无AR/SQL/权限/依赖/Components改变 | 只默认关闭AS assembly，未扩大运行权限 | None |
| MC-TEST-001 | Applicable | PASS | §8 strict Binder/defaultoff/prereq/readiness/global-lazy/mismatch/OTel/lifecycle | 组件独立真实RED→GREEN，实际计数报告填写，不自动环境测试 | None |
| MC-BLOCKER-001 | Applicable | PASS | 最终Props null语义、七Bean、mapping算法/identity6和OTel合同已闭合 | 无待决API/依赖；真实运行边界显式 | None |

### 12.6 Final verdict
PASS — Ready for user review
