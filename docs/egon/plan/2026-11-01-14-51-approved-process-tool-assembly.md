# 默认关闭的审批型Process工具装配与实际链核验

| Field | Value |
| --- | --- |
| Document | `2026-11-01-14-51-approved-process-tool-assembly.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 14:51 Asia/Shanghai` |
| Updated | `2026-11-01 15:29 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AS strict AA profiles/optional factories/actual-chain readiness；9paths |
| Source Requirement | 原蓝图§11.3/12/13 fixed approval工具装配/当前身份与配置版本闭合，继续分步提交 |
| Baseline Revision | `main@89d25ced35c87347738e9fefb4724b2b9c56a311`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `5bf1e31571e03cbba4425ce126791312b7aee78fb991b50c6cf33508c99d11c5` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [native AA core](2026-11-01-14-18-native-approved-process-tools.md)；[governed assembly](2026-11-01-10-15-governed-process-tool-assembly.md)；[approval verifier](2026-11-01-11-13-fixed-tool-approval-verification.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
在既有defaultOFF governed-process装配接入已提交AA core；可选approval profile与旧4arg配置兼容，START/AA来源slot精确闭合，原IDW模式不要求审批对象。两lazy namedfactories用NullBean/ObjectProvider分支避免构造空registry；actualContextRunner验证该机制而非假设。eager实际链保globalLazy/资源ownership，无新模式/层/权限或运行启动。

## 2. Target Spec and Effective Design
有效蓝图§11.3/12/13/17、T3-07；用户原exact继续授权。89d/e052/a7已提供nativeAA/Recorder/verifier；AS当前只有11factories且START closure拒AA。Props4fields需optional第五列表，固定审批registry拒EMPTY，故不能把可选默认误变成必须依赖。原host context/AR单supplier/互斥mode/sixprerequisites/只读storage verify保持。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/12/13/17 | 5字段可选immutable finiteapproval配置/旧4ctor/canonical strictBinder | Props/Test/YAML |
| PLAN-REQ-002 | §11.3/12/13/17 | START exact IDW+AA与approval exactAA slots闭合 | Config/Test |
| PLAN-REQ-003 | §11.3/12/13/17 | noAA兼容/NullBean与lazy optional真实行为/原模式保持 | Config/Test/Props |
| PLAN-REQ-004 | §11.3/12/13/17 | qualified singleton receiptSPI+actualworker/verifier全链/lifecycle资源ownership | Config/Test |
| PLAN-REQ-005 | §11.3/12/13/17 | 默认OFF/无角色runtime动作/9scope23SQL/实证验证分步提交 | all9paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
main89d25ced35c87347738e9fefb4724b2b9c56a311 clean；Spec SHA5bf1e31571e03cbba4425ce126791312b7aee78fb991b50c6cf33508c99d11c5，23SQL immutable。
### 4.2 Test-first strategy
Props compile-declaration第五component/old4delegator可先落，旧字段guards保持，新list/closure/annotation行为须actualRED后；Config新factory/signature仅UOE编译声明。Props GREEN再有效Config RED；合法SERVICE/source fixtures先核，compiler/fixture/constructorbinding随机错误不算缺行为RED。
### 4.3 Sequential and parallel boundaries
Root owns baseYAML/README/plan/evidence四路径与TrustedAgentActor canonical注解一处；tool_audit owns Props/Test两路径；journal owns ConfigTest，Root于有效RED后接管production Config（原journal仅编译stub冻结）。不是独自在代码库，保他人改动；Root统一Maven前所有Java冻结，code/Test owners不自行build/commit。
### 4.4 Commit boundaries
一Step九路径一commit `feat(agent): assemble governed approval-required process tools`；实际17MC10Rules/独审/strict/cached后stage，不push/改历史/runtime启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Element | Source necessity | Existing capability | Decision |
| --- | --- | --- | --- |
| optionalAA metadata | definition/profile版本固定 | 实际BindingVO/registry/Verifier已有 | 加list，不复制descriptor/timeout/auth模型 |
| noAA默认兼容 | 不能隐式启用审批依赖 | registry不允EMPTY/旧8 bridge | 两lazy NullBeanfactories+branch，不新flag/Condition类 |
| allslotclosure | §13当前工具与后端固定 | 现expectedSlots/fullorigin/bindings | 原Start factory永远核union+AA，即使AAfactory未触 |
| actualreadiness | unusedbean不能证明runtime链 | 原eagerLazyfalse actualworker检查 | AA分支延伸6verifier协作者+hostSPI指针 |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| optionalstrictProps | PLAN-REQ-001/005 | PropsTest | 5component/4ctor声明/合法typedprofiles | closedimmutablelist | Config | Step1 |
| AAassembly | PLAN-REQ-002/003/004 | ConfigTest | Props GREEN/newfactoryUOE | actualoptionalAAchain | nativecore | Step1 |
| YAML/docs/regression | PLAN-REQ-005 | bind/static/回归 | 两GREEN | reviewable8pathcommit | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现AS @Configuration factories/AR技术core | 结构保持 | 无新层/Bean collaborator/DDL/CRUD | Step1；MC-ARCH-001 |
| Reuse | profiles/Verifier/providers/HTTP/readiness | 实际对象接线 | 不新协议/认证/Registry框架 | Step1；MC-REUSE-001 |
| Model/validation | recordProps+原typedprofiles | canonical5+old4compat | copy/max256/strictdeep/consumeridentity，metadata非auth | Step1；MC-VALID-001 |
| Bean/config/lifecycle | 原11named factories/Lazyfalse verifier | 增2optionalnamedfactory | fullqualifier/close/sharedHTTP/borrowIAM，IDWnoAAget | Step1；MC-BEAN-001/MC-CONFIG-001 |
| Pattern/time | Factory/Adapter/actualrootverification | 现variationpoint扩AA | 不新timer/globaltimeout/mode | Step1；MC-PATTERN-001/MC-TIME-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| fixedsource/identity | TianjiFixedToolApprovalBindings/Verifier | actual7argctor+6purepointer helpers | 非空metadata已有严格校验 | reuse | None | Step1；MC-REUSE-001 |
| optionalmode/lifecycle | ObjectProvider/@Lazy/NullBean/SmartInitializingSingleton | 现config有provider/eageractualchain模式 | returnnull语义需actualContextRunner证明 | reuse | None | Step1；MC-DEP-001 |
| strictdeepbinding | Binder/NoUnbound/index roots | 现Boot深层list忽略陷阱已反证 | 4AAroots独立逐index重绑 | reuse | None | Step1；MC-CONFIG-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §11.3/12/13/17 | typedProps/13Bean名角色明确 | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 2 | §11.3/12/13/17 | strictroot/index/slot/consumer/pointer边界 | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 3 | §11.3/12/13/17 | immutable record/Listcopy，原VO复用 | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 4 | §11.3/12/13/17 | 命名@Beans/Qualifier/providers/ownedlifecycle，manual factory非新ServiceDI | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 5 | §11.3/12/13/17 | JDK/原Boot Binder与库复用 | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 6 | §11.3/12/13/17 | 无JSON/SDK/codec变化，无ordinal | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 7 | §11.3/12/13/17 | base一emptykey/defaultOFF与配置strict parity | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 9 | §11.3/12/13/17 | 原Factory/Adapter+actualchain verifier | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 10 | §11.3/12/13/17 | 原Duration/HTTP3s/timeout保持 | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 11 | §11.3/12/13/17 | 现AS/AR技术架构/23SQL保留，无CRUD/MP迁移，Agent excluded | PropsTest→Props→ConfigTest→Config→YAML/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | approval backend.timeout/concurrency/clientconnect | Duration | Duration | Not persisted | Internal | Not applicable: 技术duration | Not applicable: 无UI | 复用100ms..30s/HTTPconnect3s/每pool同concurrency1..8，未加globaltimeout/时钟 | 原Duration/boundedVerifier | 原call budgets/nonce/lifetime不改 | Step 1；TEST-001/002 |
| TIME-002 | ownedshutdown/borrowedclients/readiness | Duration | Duration | Not persisted | Internal | Not applicable: 生命周期 | Not applicable: 无UI | emptyAA noinstantiation/providerget；AA只关新Verifier私pool，HTTP原owner关，IAM不关 | 原close/SmartInitializingSingleton | readiness无网络auth/timer启动 | Step 1；TEST-002/003 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java | 5字段配置/4arg兼容/深层未知字段反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java | 可选有限approval list与严格重绑 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java | AA/IDW实际context/profileclosure/readiness/lifecycle反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java | 两optionalAA factories与全链真实闭合 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | defaultOFF新增emptyapproval list | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/TrustedAgentActor.java | canonical配置构造选择，保旧3arg及身份语义 | Step1 |
| MODIFY | README.md | 记录精确AA装配/hostSPI/optional-lifecycle边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-14-51-approved-process-tool-assembly.md | 锁定9scope/API/两RED/17MC10Rules | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-approved-process-tool-assembly.md | 实际门禁与限制证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Properties exact.** 追加第五record component `List<TianjiFixedToolApprovalBindings.BindingVO> approvalBindings`，null→empty/List.copyOf/≤256；enabled时原decision/start非空规则保持，approval仍可empty。保public4argconstructor委托canonical5(nullAA)，canonical compact constructor显式@ConstructorBinding，禁止@Autowired onbindingctor。实际ContextRunner正例+reflection证明，旧component exact4测试有意更新5并核old4descriptor兼容。无descriptor/identity/timeout/resolver副本模型。

**CLAR-001 — actual nested constructor binding.** 实测nested Binder对既有`TrustedAgentActor`返回empty（consumer为null），其canonical4与兼容3arg构造并存；合法fullapproval graph因而被ToolApprovalBinding拒绝。AR已直接依赖spring-boot-autoconfigure，最小必要配置依赖修正：Root在已有canonical compact constructor加`@ConstructorBinding`，不改构造体/3arg/JVM descriptors/identity semantics，无新依赖/Bean/converter/模型。此文件为第9路径，属于用户允许的直接configuration correctness依赖；现PropsTest的nested exactconsumer有效RED证明，修后保为durable regression。必须先此修复并观察合法Binder正例，然后实际unknown-field RED才AA deep-loop。

**Strict profile binding.** root ignoreUnknownFields=false，EnvironmentAware每approval index分别NoUnbound重绑backend、tool、approval(ToolApprovalBinding)、approval.consumer(TrustedAgentActor)，与绑定值完整相等；包括index1与disabledconfigured。合法SERVICE consumer同tenant，ownerissuer==consumerissuer==backendissuer指纹，consumer.subject==expectedClient，HTTPSissuer/resource、base沿旧HTTPS/loopback，固定policy/modeltaskhashes，approvalbackend独立businessSTART SHA。错误固定cause-free配置消息，不泄privatevalues。

**Always closure.** Existing StartBindings factory每次执行start configuredslots EXACT expectedIDW∪AA registered fixedSTART grantSlots；raw properties.approval slots EXACT expectedAA START grantSlots（empty也比较），拒duplicates/missing/orphans，eachdescriptorAA/resource/policyRef闭合。fullslot tenant/issuer/type/Agentdefkey-version-contenthash/fulltoolref。无AA grant slots的未引用AA descriptors不要求host审批依赖。原decision closure/fullunfiltered grantResolver/selectedcore profiles union保持。

**Optional factories.** 新named `tianjiGovernedProcessApprovalBindings` @Lazy，emptyAA直接returnnull、不newemptyregistry；非空才construct实际registry。新named `tianjiGovernedProcessApprovalVerifier` @Lazy destroyMethod close，bindings/OAuth/authentication/converter/issuerRegistry/HTTP均qualified ObjectProvider，先empty判定后才get。empty returnsNullBean，IDWbridge/eagerchain完全不getAAproviders或receiptResolver；实际ContextRunner证明typedprovider/销毁/globalLazy，无假设成功。不要新增Condition/flag/Optionalregistrywrapper或scope框架。

**Core/root wiring.** 无AA使用原8Bridge；AA才getObject实际verifier与@Qualifier `tianjiToolApprovalReceiptResolver` stableHOST SAM，使用new10。旧qualified `tianjiToolStartActionContextResolver`始终mandatory/stable，不给fallback。optional typeOtel-before-core/privacyGate保持。现eager @Lazy(false) chain强制actual worker/bridge/run/tool auth/stores/facades/decision-start actual pointers；AA还核verifier6collaborators与bridgeusesVerifier/ReceiptResolver同实例，missing/ambiguous/prototype/detached拒。13namedfactories，新Verifier私pool close；sameownedHTTP NEVER/connect3s/shutdownNow，borrowIAM不close，不在startup调用PDP/OAuth/HTTP/模型/crypto。

**CLAR-002 — receipt HOST singleton proof.** 新AA receipt resolver除actual bridge/eager两次指针相同外，必须用既有Spring BeanFactory metadata证明qualified candidate为singleton；拒绝prototype即使工厂返回JVM复用constant SAM。仅在AA分支检查，不读取noAA provider。保留标准Qualifier语义：bean name/alias或不同bean name上的@Qualifier均可匹配；不得仅isSingleton(qualifier字符串)误拒合法host。本Config可使用现有ListableBeanFactory/BeanFactoryUtils/BeanFactoryAnnotationUtils，不新增类型/依赖/层。测试包含constant-SAM prototype拒绝及different-name qualified singleton正例。旧StartActionContextResolver保持已有actual-instance稳定性合同，不扩大成新scope/name限制。

**Scope/defaults.** 九路径，base唯一新增approval-bindings:[]，modeenabledfalse/并发4/原两list empty/sixprereq/mutualexclusive/ARsingleSupplier不改。23SQL/ARcore/SDK/权限只读，无migration/deps/新业务Springcollaborator；factory方法DI与configbinding不误套@Autowired amend，若新增业务Service则越scope。真实IAM/网络/PG/模型用户验收，配置/metadata不授角色或authority。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 默认关闭的审批型Process工具装配与实际链核验
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交89d/e052/a7及原governed-process assembly/props。
- Baseline state: config四字段/11factories，START闭合IDW-only，无可选AA实际根。
- Observable outcome: typedAAprofiles显式时真实装配+稳定hostSPI/readiness，emptyAA兼容原IDW/defaultOFF且不触审批组件。
- End state: defaultOFF AS链闭合，有限ContextRunner/绑定证据，无角色或环境启用。
- Test-first gate: Required — 两unit最小声明供编译，actual合法RED后GREEN，无compiler/fixture伪RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java`
- Purpose: 5字段配置/4arg兼容/深层未知字段反证。
- Symbols: approvalBindings/canonicalBinding/strictbackend-tool-approval-consumer。
- Repository evidence: 现Binder/EnvironmentAware indexed-root tests。
- Dependencies and consumers: 5component+4ctor compile declarations，newguard/closure缺失真实RED；合法consumer fixtures。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: 默认OFF/emptyAA，finite256/copy/old4ctor，实际canonical Binder正例。
- Error and edge behavior: 全部index含later/disabled未知字段；有效SERVICEconsumer/issuer/client/tenant/hash。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
assertFiveCanonicalComponentsAndOldFourConstructorCompatibility();
assertRealConstructorBindingAndImmutableFiniteOptionalApprovalList();
assertEveryIndexBackendToolApprovalAndConsumerUnknownFieldFailsSanitized();
assertCoherentConsumerOriginWorkflowAndUriBoundsWithoutFakeBusinessHashCoupling();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java`
- Purpose: 可选有限approval list与严格重绑。
- Symbols: record5thapprovalBindings/old4argctor/@ConstructorBinding/setEnvironment。
- Repository evidence: 现record4/两list+NoUnboundindexedpattern。
- Dependencies and consumers: File1有效RED后功能；保旧4arg与existingrules。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: approvalnull→empty/copy/max256，旧concurrency/decision/start规则不变。
- Error and edge behavior: canonical compactctor显式ConstructorBinding非Autowired；不新增descriptor/timeout/identity模型。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
public oldFourConstructor(...) { this(...,null); }
@ConstructorBinding canonicalFive { preserveOldRules(); approvalBindings=immutableOptionalMax256(); }
forEachApprovalIndex { rebindClosed(backend,tool,approval,approval.consumer); requireEqualsBoundValues(); }
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/TrustedAgentActor.java`
- Purpose: 选择现有完整四字段不可变consumer构造器用于配置绑定。
- Symbols: TrustedAgentActor canonical compact constructor/ConstructorBinding。
- Repository evidence: 既有canonical4+compatibility3；实际nested Binder返回empty；AR POM已有Boot autoconfigure。
- Dependencies and consumers: Props有效consumer-binding RED后，完整graph与深层未知字段测试之前。
- Why now: 不添加身份副本或全局converter；原身份构造和校验原样保留。
- Contract/signature changes: 仅canonical注解，两个公开构造JVM descriptor不变。
- Input/output and state mapping: 原4fields与旧3arg LEGACY原样；metadata不构成认证。
- Error and edge behavior: 不用Autowired、不放宽SERVICE/issuer/tenant校验。
- Standards impact: MC-VALID-001/MC-MODEL-001/MC-BEAN-001/MC-SCOPE-001/MC-TEST-001。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；canonical record绑定非Spring DI。
- Implementation pseudocode:
```java
annotateOnlyExistingCanonicalValueConstructorForBootBinding();
keepAllConstructorBodiesAndOldThreeArgumentDescriptor();
proveExactNestedConsumerAndFullApprovalGraphBeforeUnknownFieldRed();
```
- Verification contribution: TEST-001/003实际nested/fullgraph与旧身份回归。
- After this file: 回到Props deep-binding有效RED，不开始Config功能。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java`
- Purpose: AA/IDW实际context/profileclosure/readiness/lifecycle反证。
- Symbols: 13factories/NullBean/qualifiedhostresolver/actualpointers/globalLazy。
- Repository evidence: 现19 context tests/storage mocks/realfactorytypes。
- Dependencies and consumers: Props GREEN+newAAfactories UOE只供编译，独立AS行为RED才实现。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: IDW-only不newAAregistry/verifier/resolver，AAactualchain正例/preciseslots。
- Error and edge behavior: NullBean typedprovider/destroy/null-empty/lazy要actualContextRunner证明，不能假设。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
assertNoAaDefaultAndGlobalLazyNeverResolveApprovalComponentsOrReceiptProvider();
assertNullBeanTypedProvidersAndOwnedLifecycleWithoutEmptyRegistryConstruction();
assertExactUnionStartSlotsAndAaApprovalSlotsRejectMissingOrphansDuplicates();
assertActualAaWorkerBridgeVerifierSixCollaboratorsAndStableQualifiedResolver();
assertConstantSamPrototypeReceiptRejectedAndDifferentNameQualifiedSingletonAccepted();
assertPrototypeAmbiguousDetachedMissingRootsFailAndNoAuthNetworkRoleOperationsOccur();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java`
- Purpose: 两optionalAA factories与全链真实闭合。
- Symbols: tianjiGovernedProcessApprovalBindings/ApprovalVerifier +start/bridge/chain extension。
- Repository evidence: 现11factories/qualified ObjectProvider/eager Lazyfalse/readiness。
- Dependencies and consumers: File4有效RED后功能；原ARsupplier/模式不改。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: 13namedfactories，共用HTTP与IAM，新10Bridge仅AA，否则旧8。
- Error and edge behavior: emptyAA lazyfactoriesreturnnull，不newemptyregistry，不解析provider；closure在Start factory ALWAYS。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
startFactory { requireConfiguredStartSlotsExactlyIdwUnionAaGrantSlots(); requireApprovalPropertySlotsExactlyAaGrantSlotsEvenEmpty(); validateAaDescriptorResourcePolicyAndNoDuplicates(); }
@BeanApprovalBindings @Lazy { if (props.approvalBindings.empty) return null; return actualFixedRegistry(...); }
@BeanApprovalVerifier(destroyClose) @Lazy { if (props.approvalBindings.empty) return null; resolveQualifiedBindingIamHttpProvidersThenConstructActualVerifier(); }
bridgeFactory { retainMandatoryStableStartContextResolverAndPrivacyOtelOrder(); if (hasAa) return newTen(...,actualVerifier,qualifiedStableApprovalReceiptResolver); else return oldEight(...); }
eagerChain { preserveAllOldActualWorkerRunToolStoresTransportsPointers(); if (hasAa) requireAllSixVerifierPointersAndExactBridgeVerifierReceiptResolver(); }
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Purpose: defaultOFF新增emptyapproval list。
- Symbols: governed-process.approval-bindings: []。
- Repository evidence: 唯一baseYAML原4keys。
- Dependencies and consumers: Props/Config GREEN后Root改一键。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: 保enabledfalse/concurrency4/oldlists空。
- Error and edge behavior: 不设新timeout/模式/源别名/identity/hostresolver fallback。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
keepGovernedProcessEnabledFalseAndOriginalFourDefaults();
addOnlyEmptyApprovalBindingsListBelowStartBindings();
verifyBaseConfigBindingAndNoEnvironmentProfileKeyDrift();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 7 — `MODIFY README.md`
- Purpose: 记录精确AA装配/hostSPI/optional-lifecycle边界。
- Symbols: approval profiles/qualifier/readiness/defaultOFF说明。
- Repository evidence: 现programmaticAA与IDWassembly段落。
- Dependencies and consumers: actualsource/log后更新。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: 公开配置canonical5+old4compat，noAA无需receiptSPI。
- Error and edge behavior: 配置不授角色、不启动RPC/HTTP/模型/数据库或验证crypto。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
documentOptionalFiniteApprovalProfilesAndExactSlotClosure();
documentQualifiedStableReceiptHostAndActualChainReadiness();
documentDefaultOffNoAaCompatibilityOwnedResourcesAndUnverifiedRuntimeBoundary();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 8 — `CREATE docs/egon/plan/2026-11-01-14-51-approved-process-tool-assembly.md`
- Purpose: 锁定9scope/API/两RED/17MC10Rules。
- Symbols: V5/5aliases/来源与兼容合同。
- Repository evidence: 蓝图/现Config261/Props50/actualconstructor与explorerinventory。
- Dependencies and consumers: Root全文/strict/resources/checklist/hash后GO。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: Review原用户授权，不伪Accepted。
- Error and edge behavior: NullBean必须用context证明，不引newCondition框架。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
lockBaseline89dAndNinePathsWithOwnership();
requirePropertiesAndAssemblyActualRedBeforeGreen();
requireManualRulesSourceReviewAndScopedCommitBeforeNextStep();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

#### File 9 — `CREATE docs/egon/evidence/2026-11-01-approved-process-tool-assembly.md`
- Purpose: 实际门禁与限制证据。
- Symbols: 5aliases/17MC10Rules/REDGREEN/Git。
- Repository evidence: 本plan与真实源码/stdout。
- Dependencies and consumers: 先PENDING，实际执行后逐项闭合。
- Why now: tests先于行为，最小声明后本unit有效RED才功能。
- Contract/signature changes: §6 exact5Props/4ctor兼容、13Bean/nullableAA实际根。
- Input/output and state mapping: 记录constructorBinder/NullBean/未知字段/Rootpointer证明。
- Error and edge behavior: mockstorage/ContextRunner非实际IAM或PG验收，不预测commit。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-BEAN-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的binding/slots/optional/pointer/lifecycle义务。
- Implementation pseudocode:
```java
recordActualRedGreenAndValidFixtureEvidence();
recordEachManualCheckLiteralRuleAndSlotLifecycleFinding();
recordExactNinePathCommitAndUnperformedRuntimeRoleActions();
```
- Verification contribution: TEST-001/002/003，actual日志/边界记evidence。
- After this file: 本贡献完成，不以Bean存在/metadata/Binder冒实际authority/runtime。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace。
- Verification command: §8 canonical八类；focusedProps/Config各运行，RootMaven前allJava冻结。
- Expected result: 两actualRED后GREEN；validCtor/Binder/NullBean/noAA/globalLazy/13Bean/actualAAchain+strictclosure矩阵闭合。
- Failure returns to: list/ctor/deepbind回File1/2；slot/optional/readiness/lifecycle回File4/5；YAML回File6；fixture先修再有效RED，范围漂移先Root修计划。
- Completion criteria: exact9paths/23SQL/无stub/17MC10Rules/独审/strict/cached通过后commit。
- Rollback: defaultOFF/停用新AAprofiles保已消费facts/nonce/schema；binary回退不删receipt/repair/drop或重派UNKNOWN。
- Commit paths: stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java、stellar-enclosure-tianji-agent-server/src/main/resources/application.yml、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/TrustedAgentActor.java、README.md、docs/egon/plan/2026-11-01-14-51-approved-process-tool-assembly.md、docs/egon/evidence/2026-11-01-approved-process-tool-assembly.md
- Commit: `feat(agent): assemble governed approval-required process tools`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Evidence |
| --- | --- | --- |
| TEST-001 | PropsTest | real canonicalBinder/old4/finitecopy/strictunknown有效RED→GREEN |
| TEST-002 | ConfigTest | realContextRunner AA/noAA/NullBean/globalLazy/slots/actualpointers/lifecycle有效RED→GREEN |
| TEST-003 | 八类回归/9scope/23SQL/YAML/17MC10Rules/strict | 全适用PASS后commit，无真实IAM/网络/PG验收 |

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=TianjiGovernedProcessToolPropertiesTest,TianjiGovernedProcessToolConfigurationTest,TianjiFixedToolApprovalBindingsTest,TianjiFixedToolApprovalVerifierTest,TianjiAgentRuntimeAutoConfigurationTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,ToolApprovedStartRecorderTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-approved-process-assembly-final.log 2>&1
```
strict从workspace用writing-plan validate_plan.py本文件 --strict；current execute-plan资源15md19/writing18md24/checklist复核；不访问已改名旧executing-plan路径，不动用户COLA并行工作。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/schema/权限/catalog激活/角色grant，23SQL原样。旧4argProps/ARsupplier/old8-IDW+emptyAA/defaultOFF路径保持，canonical5显式绑定。新AAprofiles显式配置且startup完整闭合、hostSPI稳定才ready；不自动外部调用。回退停新入口保已消费收据/历史，UNKNOWN不重派。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/12/13/17 | Step 1 | Props/Test/YAML | TEST-001/002/003 | 5字段可选immutable finiteapproval配置/旧4ctor/canonical strictBinder |
| PLAN-REQ-002 | §11.3/12/13/17 | Step 1 | Config/Test | TEST-001/002/003 | START exact IDW+AA与approval exactAA slots闭合 |
| PLAN-REQ-003 | §11.3/12/13/17 | Step 1 | Config/Test/Props | TEST-001/002/003 | noAA兼容/NullBean与lazy optional真实行为/原模式保持 |
| PLAN-REQ-004 | §11.3/12/13/17 | Step 1 | Config/Test | TEST-001/002/003 | qualified singleton receiptSPI+actualworker/verifier全链/lifecycle资源ownership |
| PLAN-REQ-005 | §11.3/12/13/17 | Step 1 | all9paths | TEST-001/002/003 | 默认OFF/无角色runtime动作/9scope23SQL/实证验证分步提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | empty审批registry构造失败/强加依赖 | 两lazyNullBean factories，noAA分支不get，actualcontext证明 | Root | Closed设计/执行验证 |
| DEC-002 | lazyfactory未执行遗漏coverage | existingStart factory ALWAYS核startunion+approvalAA精确slots | Root | Closed |
| DEC-003 | 多构造误用Autowired配置绑定 | canonical ConstructorBinding+保4arg，realBinder/reflection | Root | Closed |
| RISK-001 | 深层未知字段被Boot忽略 | 四indexedroots独立NoUnbound，laterindex/disabled反例 | Root | Tracked |
| RISK-002 | unused/prototypehost伪readiness | actualworker→core→same6verifiercollaborators/hostpointers | Root | Tracked |
| RISK-003 | sourceconfig被当角色/验收 | defaultOFF/noauthnetworkstartup/真实环境用户验证 | 用户 | Tracked |

## 12. Review and Acceptance
Root与两agent只读设计闭合；Review沿原用户授权，不伪Accepted。Root全文/strict/resources/checklist/hash后SourceGO，evidence初始PENDING。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AS factory/ARcore既有技术架构，Agent excluded；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-REUSE-001 | Applicable | PASS | existingBindingVO/Verifier/Bridge/HTTP/provider/readiness复用；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-DEP-001 | Applicable | PASS | 现Boot/JDK/IAM/SDK，无新依赖/框架/构建步骤；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-NAME-001 | Applicable | PASS | 13stable Bean names/typed5fieldProps与qualifiers；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-VALID-001 | Applicable | PASS | finiteprofile/strictindexedclosure/slots/sourceconsumer/actualpointer/hostSPI；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-MODEL-001 | Applicable | PASS | immutableoptional list/record+old4ctor，无新实体/宽泛DTO；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONVERT-001 | Not applicable | N/A | typedexistingrecords直接绑定，无PO/entity/Mapper转换；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志，Props/errors脱敏无privatecause；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-BEAN-001 | Applicable | PASS | 13namedfactories/qualifiedproviders/ownedclose/actualchain；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-UTIL-001 | Applicable | PASS | 原Binder/NoUnbound/JDK/Frame，不newregistryengine/timer；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-JSON-001 | Applicable | PASS | 原wire/codec保持，仅typedYAML Binder，无ordinal；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TIME-001 | Applicable | PASS | 原backend100ms..30s/HTTPconnect3s/原call budget，无globaltimeout新增；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONFIG-001 | Applicable | PASS | base仅1emptykey/defaultOFF，canonical5/4ctor及disabledconfiguredstrict；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Factory/Adapter+eageractualchain verifier，解决mode/profile变点；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-SCOPE-001 | Applicable | PASS | exact9paths/23SQL/ARsupplier-core/SDK/权限不改；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TEST-001 | Applicable | PASS | Props与Config各有效RED/actualContextRunnerBinder/NullBean/旧回归；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-BLOCKER-001 | Applicable | PASS | optionalconstruction/compat/sourceclosure/actualchain合同闭合，最终门禁后commit；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |

### 12.6 Final verdict
PASS — Ready for user review

执行记录：本Step已完成9path source与161项精确回归，23SQL不变；CLAR001/002与中途fixture诊断均见evidence。原用户授权下Review实施，不伪Accepted；whole T3-07/MCP及其余蓝图仍继续。
