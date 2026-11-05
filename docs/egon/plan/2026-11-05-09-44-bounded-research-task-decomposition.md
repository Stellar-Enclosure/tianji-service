# 有界研究任务分解实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-09-44-bounded-research-task-decomposition.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 09:44 Asia/Shanghai` |
| Updated | `2026-11-05 10:11 Asia/Shanghai` |
| Owner | Root；仅Root运行Maven、stage、commit与授权源码 |
| Repository | `tianji-service` |
| Scope | 两个顺序逻辑提交：AR bounded core；AS default-off factory/配置与最终finite打包证据 |
| Source Requirement | Root全文审查原1081行、完整受控修订delta及final1086行规范后明确批准仅写Plan；用户原goal按计划继续并分步提交 |
| Baseline Revision | `main@237e1d25d860adbaf6752d1cdc5b4aac8bedae65` clean；Native Step51 final；924 tracked files、25 immutable main-classpath SQL |
| Implements Spec | [Bounded task Spec](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md) |
| Spec Status | `Review` |
| Spec Revision | `Updated 2026-11-05 09:38 Asia/Shanghai`；Root批准normative SHA `18f4d7ba1015dc07833c6fb48e98cad19a16c583d17045c87f0122d43d88cff3`；仅Related Plans backlink副本SHA `2eb396ab89c0dbdd3dad18d4d586b92dadc76c632a9e7974a732b2ec706936bb` |
| Effective Specs | [Primary bounded Spec](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md)；[Native transfer Spec](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md) §6-7/§9-10/§14-16；[Workspace master](../../../../docs/plans/agent-framework-upgrade-plan.md) T3-09/S03 当前实际条目 |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [前置固定模板计划](2026-11-05-05-22-native-agentic-transfer-templates.md)；其源码已在baseline提交，不重做 |

## 1. Summary

本Review Plan仅实施已批准的有界任务Spec，以两次逻辑提交完成：Step1交付可独立验证的AR core与规范/计划/证据，Step2在同一既有qualified模型接线下提供默认关闭的AS第三factory并完成有限clean verify与实际JAR检查。Step1提交前不开始Step2；每步一semanticcommit，保持两个步骤的源码路径不重叠。

所有行为变化遵循断言RED→必要compile seam A→typed RED→最小B→GREEN。RED只能是预期JUnit assertion failures，不能是编译、fixture、依赖、网络或环境错误。Root独占Maven/stage/commit；worker可按互斥文件ownership并行准备，但必须遵守依赖freeze顺序。本文不授予source GO、不执行Maven、不运行应用、PG、provider、listener或浏览器。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path：[Bounded task Spec](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md)。
- Status：文档`Review`，Root明确接受此精确文本用于Plan；不能据此把Plan标Ready或开始源码。
- Revision：原规范SHA18f4...、1086行、2026-11-05 09:38；仅追加`Related Plans`backlink，其他metadata/body逐字不改。
- Approval evidence：Root已FULLREAD原稿/289行delta/机械链接diff，并确认两Step、SDK机械落点、必要ConstructorBinding与workspace master归属；最终Plan仍待Root全文审查。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Bounded Spec](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md) | Review；Root精确批准SHA18f4... | 全文/REQ-931-947 | 唯一新增行为/文件/验收合同 |
| Normative dependency | [Native Spec](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md) | baseline既有；已实现固定模板 | §6-7/§9-10/§14-16，受Primary局部amend | provenance/currentCAS/统一预算/实际SDKcontext/安全fatal与旧两policy兼容 |
| Roadmap boundary | [Workspace master](../../../../docs/plans/agent-framework-upgrade-plan.md) | baseline当前文件 | T3-09与S03实际条目 | 源码审计对实际列表，不以Spec自证；真实验收与整个Goal分开 |

### 2.3 Superseded or excluded content

无整份supersession。前置REQ-901至REQ-920仅作为preservation/回归义务，不新增重做源码；REQ-920对第一切片的不足由Primary当前有界切片补足，本轮仍不宣称真实S03/整体Goal验收。Primary仅允许第三topology/1-2schema，不改变旧两tuple/oldhash；Graph/T4、task持久化/resume、第二Run/Runner、DSL/AgentTool、生产codec/C/CL/API/权限/POM/SQL/Components/PS/frontend全部排除。前置固定模板源码已经完成，不把其原RED或181结果当本轮执行。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-931 | §4/7/10 | thirdfixedtuple1/2、3nodes/depth2/max16 | exact metadata/tree且旧hash不变 | Step1Compiler/Gate、Step2Factory |
| REQ-932 | §4/7/9 | 首complete root closedplan+唯一task1call | partial!=true；turnComplete=false允许；wrongframe拒 | Parser/Gate/Plugin/tests |
| REQ-933 | §4/7/10 | schema1、1–8唯一ID/fixedpool | duplicate/unknown/coercion/extra拒 | Parser/tests |
| REQ-934 | §4/7/10 | task/question/plan原UTF16/UTF8上限 | 边界安全、不trim/normalize/repair | Parser/Gate/tests |
| REQ-935 | §4/7/9 | plan一次冻结，后rootroute正文必须空 | 不重派/改plan；真final可JSON答案 | Gate/Runner |
| REQ-936 | §4/7/10 | CAS后DISPATCHED，returnaction后RETURNED | stored original confirmation exactreplay跨phase no-op | Gate/tests |
| REQ-937 | §4/7/10 | actualcontext/taskgeneration隔离 | 重复role合法，旧未确认context不能进新task | Gate/Context/Runner |
| REQ-938 | §4/7/9 | history后snapshotprepare前USER问题一次注入 | PreparedCall/ADK_CONTENTS/estimate含question，system/AgentExecutionSnapshot不变 | Gate/Plugin/Context |
| REQ-939 | §4/7/15 | sameRun/Runner/MODEL/currentCAS/总预算/取消 | zeroordinaryTOOLreserve；配额/取消不能旁路 | Plugin/Executor/tests |
| REQ-940 | §4/7/10 | schema2 orderedtasks权威/agentslastrole/currentroot | one-shotcandidate+JSON-nativeMaps先构造DTO | Gate/Executor/Completion/CodecTest |
| REQ-941 | §4/7/9 | sameAppender新type/exactsixpayload | 无rawquestion/taskID；oldtypefourkeys不变 | Gate/Executor/tests |
| REQ-942 | §4/7/10 | semanticframed planHash | 原字段/任务顺序参与；JSON空白/keyorder不参与 | Parser/Gate |
| REQ-943 | §4/7/8 | 一个新hostfactory，同qualifiedmodel/adapter | exacttree/usesidentity，构造不callprovider | Step2Factory/config |
| REQ-944 | §4/7/16 | fifthfalseproperty+4argABI，exact2-or3 | nativefalse无map；bothtrueexact3 | Step2props/config/YAML |
| REQ-945 | §4/8/16 | oldtwo/schema1/Function/codecprod不变 | 原tests基座保留，schema2 genericroundtrip | 两Step回归 |
| REQ-946 | §4/7/15 | sticky safe fatal覆盖所有新边界 | knowncode保留，unknownprivate无cause/echo，晚root不能成功 | Gate/Plugin/tests |
| REQ-947 | §4/14/18 | finiteonly/noApp/PG/provider/browser/no假验收 | exactselectors/JAR/25SQL/source审计；S03等RuntimeUnverified | 两Step evidence/Root |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

仅两Step：S1的15paths形成一个不可拆半的core；S2的7paths是可独立defaultoff的host接线。新增enum会使既有Gate多个exhaustive switch不再编译，故Compiler与Gate A必须在一次compile batch内补齐；不能先单独提交Compiler，也不能用compileFAIL冒充RED。S1结束是完整可编译core+AS仍旧exacttwo；S2结束才多第三factory。无新迁移/生成代码/公共contract发布。

### 4.2 Test-first strategy

精确依赖节点：`F1 presence RED → F2 A → F1 typed RED → F2 B GREEN → F3 enum-presence RED → (F4 A + F5 A) paired compile → F3 typed tuple RED → F4 B GREEN → F6 typed Gate RED → F5 B GREEN → F7/F8 integration RED → F9 B GREEN → F10/F11 result contract RED → F12 B GREEN → F13-15 docs/audit/commit`。

文件列表是首次处理顺序，唯一允许的回访是上述同一文件的A/B闭合；A不得提前实现行为，B不得越过其typed RED。File4 A到File5 A之间的瞬时不编译状态不运行Maven、不是RED、不是commit点。File11 codec回归可以先绿色；G11 RED必须由缺Executor tasks/type的Completion断言给出，不能强改codec制造失败。第二Step同样区分missingctor/type/resource断言→A→typed assertions→B。

Root记录每次exit、Owning suite tests/F/E/S与预期失败方法；`F>0,E=0,S=0`且只有approved缺行为方可计RED。happy setup/run使用assertDoesNotThrow，类型/内容先assertNotNull/assertInstanceOf再访问；negative使用assertThrows。未到API A时仅对自有类型/构造做反射presence，不反射SDK私有状态。任何compile/fixture/environment错误必须先修正，不跨到B。

### 4.3 Sequential and parallel boundaries

| Scope | Proposed exclusive ownership | Freeze/dependency boundary | Authority |
| --- | --- | --- | --- |
| S1 Parser+Compiler四paths | oneworker | Gate A编译批由Root协调；不能独立stage | Root唯一Maven/commit |
| S1 Gate/GateTest两paths | oneworker | A先给签名/enumseam；typedGate RED后B | 不改Plugin/Executor/tests别人的文件 |
| S1 Plugin/Context/Runner三paths | oneworker | Gate B先freeze，先tests RED再Plugin B | 真Runner无provider/PG |
| S1 Executor/Completion/CodecTest +3docs | Root | integrationGREEN后定义projectionRED再B | exact15paths一次commit |
| S2 Factory/FactoryTest两paths | oneworker | Properties A后typed测试；fullfactory后cfg注册 | 不碰旧两factory |
| S2 Properties/Config/ConfigTest/YAML/EVD五paths | Root | S1已commit，actualconstructorbinding/flag测试顺序 | exact7paths一次commit |

每个worker必须明确“不独占整个代码库”，保留他人改动、只写分配路径、不revert/stage/commit、不跑Maven。并行仅允许互斥文件的/tmp草稿或已满足依赖的实施；Root每个freeze点读完整diff并单跑测试。下一Step开始前必须有前一Step完整commit SHA与clean路径审计。

### 4.4 Commit boundaries

S1：`feat(agent): govern bounded research task decomposition`，exact15paths；S2：`feat(agent): register default-off bounded research template`，exact7paths。两个commit源码路径不交叉，没有空commit/微提交；同Step内A/B不是独立commit。外层workspace master仅S2结束后Root信息更新，不是serviceGit路径或第三commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/evidence | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| TaskPlanVO/strictparser | Primary§7.0 Add | 现genericJSON无任务shape | 广义Map/通用parser不足 | 一个同包VO，不改通用parser |
| boundedpolicy/windows | Primary§7.0 Add | 现role单次FSM不能复用角色 | Session/outputKey污染已证 | Gate私有State，不新store |
| questionhooks | Primary§7/9 Add | 现Plugin精准计量点/partial早return | system注入/晚注入错误 | 两个同包hooks，不加新adapter |
| event/tasksprojection | Primary§7/9 Add | 现Appender/CAS/genericcodec已足 | 新port/DDL/API无必要 | 复用；先JSON-native DTO |
| ASfactory/flag | Primary§7.0 Add | 现qualifiedmap/exacttwo | 改旧codeRef破兼容 | 一factory+第五falsekey |
| Graph/resume/newschema/dependency | Primary排除 | 现run治理足够 | 更大平台无当前需求 | 不进入filetree |

### 4.6 Change-unit Dependency Matrix

| Unit | Prerequisite | Files | Observable end state | Why one commit boundary |
| --- | --- | --- | --- | --- |
| parser | presence/typedRED | S1 F1-2 | strictimmutableplan可单测 | 和core同一逻辑功能，非独立发布API |
| compiler/Gate | parser+paired A | S1 F3-6 | 新enum与所有exhaustive switch一起完整 | 拆commit会半编译/半治理 |
| Plugin integration | GateB+Context/RunnerRED | S1 F7-9 | question/partialhook真实SDK路径 | 新topology不能先绕治理 |
| Executor/result | previousGREEN+DTO/typeRED | S1 F10-12 | currentCAS/newtype/orderedJSONtasks | 同core才可完整finish |
| AS defaultoff | S1commit | S2 F1-6 | oldexacttwo/bothflagsexactthree | 可独立审查/回退host激活 |
| Evidence/release | respectiveGREEN | S1F13-15/S2F7 | exactperStepfacts/JAR | 和对应语义commit一起审查 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

架构仅继承Primary§6.1和前序NativeSpec已批准的Tianji Agent technical vertical例外：AR现adk包与AS现agenttemplate包，现层/模块/POM方向不变，不创造biz.*或新COLA层。所有新增值仅immutable VO/config record；无普通entity/PO/DTO新增。第三方SDK与host配置验证沿该已批准技术边界手工闭合，不引入伪Controller/DTO/ValidationGroup。没有businessService/Mapper/新注入层，现Plugin的LoggerFactory技术logger与AS@Bean/qualifier方法注入保持；Root批准technical factory显式值构造，不补无意义Lombok/logger。

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | AR/adk与AS/templates现technicalvertical，NativeSpec§6.1 Root例外 | Primary§6.1保持 | 只22paths，不创层/模块/POM/CRUD | Step 1/Step 2；MC-ARCH-001 |
| Reuse/capability | 现adapter/Gate/ledger/codec/digest/Jackson | Primary§6.1/7.0复用 | 新VO/factory只补批准gap，零依赖/DDL | Step 1/Step 2；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | 现immutable记录/SDKcompact guards/C.ContractChecks | Primary§6.2/10技术边界 | VO防御copy、先closedshape/actualcontext，JSON-native投影，无新Mapper | Step 1/Step 2；MC-NAME-001/MC-VALID-001/MC-MODEL-001 |
| Bean/logging/util/JSON/time/config | 现PluginLoggerFactory技术logger、ASexplicit@Bean/qualifiers、soleYAML、Instant | Primary§6/15/16 | 保持技术构造/日志；第五falsekey及必要canonicalbinding；现Jackson/java.time | Step 1/Step 2；MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001 |
| Business variation/pattern | CompilerStrategy/GateFSM/factorymap | Primary§13 Strategy+State | boundedpolicy内聚与两阶段确认，不造框架 | Step 1/Step 2；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| strictJSON/UTF16/hash | JDK/现Jackson/digest | AdkSemanticJsonDigest.fingerprint；Toolcodec模式 | 缺closed taskshape | 新task专用VO，复用hash | 无依赖；一个approved VO/parser | Step 1；MC-REUSE-001/MC-DEP-001 |
| provenance/state/currentauthority | actualADK/IdentityHashMap | NativeGate/Plugin/RunEventSink/ledger | 现rolekey不能重复task | 扩boundedState，保持authority | 无新store/port | Step 1；MC-REUSE-001 |
| modelbudget/context | 现Content/Builder/estimator | boundNative/context/trim/snapshot | 缺currentquestion/partialhook | 两hook复用现pipeline | 无新adapter | Step 1；MC-REUSE-001 |
| genericDTO/resultcodec | Map/List/ContractChecks | AgentExecutionResult/AgentJsonCodec | codec已足，record需投影 | JSON-nativeMap列表先DTO | codec生产不改 | Step 1；MC-JSON-001 |
| hostconstruct/binding | Springtypedproperties/adapter | namedmap/qualifiedadapter/两个factory | 缺第三factory/fifthkey | 一个factory/兼容ctor/ConstructorBinding | 无新Beanregistry/依赖 | Step 2；MC-BEAN-001 |
| finalverification | 现reactorscript/JUnit/strict/JAR/git/SHA | 181selector/25SQL列表 | 增新tests/artifactproof | exact20classes/无新harness | 无scratch文件 | Step 2；MC-TEST-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/8/10 | *VO/Gate/Factory/Test既有技术角色 | S1F1-12、S2F1-5 | final语义类型，不能Data/Info/Bean载体 | 类型/fileinventory+typedtests | 两Step | PASS |
| Rule 2 | §6.2/7/9 | SDK/host内部技术边界既有Root例外 | parser→Gate→Plugin→Executor；hostprops→factory→map | 验shape/actualcontext/currentauthority/hostmetadata在mutation前，无伪DTO层 | 负例/时序/strictbinding | 两Step | PASS |
| Rule 3 | §6.2/10 | 现immutable records/技术finalfields | S1VO/Gate值；S2props | defensivecopy/redacted；无普通实体record或新Mapper | constructor/immutability/DTOJSONtests | 两Step | PASS |
| Rule 4 | §6.2/20 | 现PluginLoggerFactory技术logger、ASexplicit@Bean/qualifiers | S1Plugin/Executor原构造保持；S2config/props/factory | 不造businessDI；实际Bean名/qualified map；必要ConstructorBinding而非Autowired | context/reflection/static | 两Step | PASS |
| Rule 5 | §6.2 | 现JDK/Jackson/ADK/adapter | 所有Java文件 | 无新dependency/Utils、无BeanUtils映射 | POM/excludedhash/importreview | 两Step | PASS |
| Rule 6 | §6.2/9/10 | com.fasterxml/genericDTO/checks | S1parser/event/tasks，S2immutablemetadata | strictJSON/closedpayload/无ordinal存储；先MapList后DTO | parse/codec/eventroundtrip | 两Step | PASS |
| Rule 7 | §6.2/16 | AS唯一application.yml | S2F1/2/5/6 | fifthfalsekey，旧四key不变，无新profile | YAMLunit+packagedcmp | Step2；S1验证未触 | PASS |
| Rule 9 | §6.2/13 | CompilerStrategy+GateFSM | S1boundedpolicy；S2Strategyfactory | 现Strategy+内聚State，不建框架/纯string分支流程 | repeatedrole/state/registration | 两Step | PASS |
| Rule 10 | §6.3/15 | existingInstant/Duration/DBclock | S1Plugin/Executor现deadline边界 | 无新Date/Clock/时间存储，复用原UTC | deadline/cancel/forbiddenimports | Step1；Step2验证未改 | PASS |
| Rule 11 | §6.1/8 | AR/AS技术vertical例外与Components复用 | 每个declaredfile | 现package/module方向，不加表/CRUD/生成器/Outbox替代 | 全path/scope/25SQLaudit | Every Step | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-931 | existingdeadlineAt/leaseExpiresAt/eventoccurredAt | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: elapsedrunfencing | Not applicable: noUI | 现MODEL/CAS/currentclock检查，不改日历/DST | 现DBclock/精度，未增转换/Clock | 现字段/pgJDBC/JSON不改，PG本轮不跑 | Step 1 Files 7/10/12，TEST-941/TEST-944/TEST-949；G12/G18；Step 2仅不变审计 |

## 5. Change File Tree

下列22unique servicepaths是完整write/commit scope；Step1 15、Step2 7，没有源码路径重复。workspace master不在此Git清单。每个File N的A/B属于同一个path的受控阶段，不增加额外路径。

```text
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVOTest.java  CREATE  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVO.java  CREATE  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java  MODIFY  [Step 1]
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java  MODIFY  [Step 1]
docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md  CREATE  [Step 1]
docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md  CREATE  [Step 1]
docs/egon/evidence/2026-11-05-bounded-task-runtime.md  CREATE  [Step 1]
stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java  MODIFY  [Step 2]
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java  MODIFY  [Step 2]
stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactoryTest.java  CREATE  [Step 2]
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactory.java  CREATE  [Step 2]
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java  MODIFY  [Step 2]
stellar-enclosure-tianji-agent-server/src/main/resources/application.yml  MODIFY  [Step 2]
docs/egon/evidence/2026-11-05-bounded-task-templates.md  CREATE  [Step 2]
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVOTest.java` | bounded_plan_type_is_present; accepts_one_and_eight_immutable_tasks; rejects_duplicate_unknown_coercion_and_trailing_json; preserves_safe_question_and_caps; framed_hash_preserves_order_and_original_strings | File1先得到真实缺类F；typed tests只在File2 A之后编译并因缺行为F；不能把ClassNotFound/NPE当Errors接受。 | 先锁新任务解析类型，再定义闭合JSON/构造/hash行为。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVO.java` | record NativeTaskPlanVO(String schemaVersion,List<TaskVO> tasks); nested record TaskVO(String taskId,String agent,String question); static parse(String); String planHash() | Parser完整GREEN，可供Compiler/Gate编译；无新topology执行或host注册。 | 拥有任务专用闭合解析/immutable构造与semantic framed hash。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java` | bounded_topology_is_present; bounded_tuple_is_exact_and_old_tuples_unchanged; shared_or_reserved_output_keys_are_rejected | 旧enum缺失F已记录；typed tests待paired A后，仅缺tuple行为F。 | 在enum与schema扩展前锁exact third tuple和old hash冻结。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java` | TransferTopologyEnum.BOUNDED_RESEARCH_V1; TemplateSpec constructor; validateTopology | File4 B完成后Compiler绿色；Gate仍A/fail-closed至File6 RED后B；无半编译commit。 | 增加唯一bounded topology/schema branch，保持原tuple/hash ABI。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java` | prepareModelRequest(CallbackContext,LlmRequest.Builder); validateResponseEnvelope(CallbackContext,LlmResponse); requireCompletedTasks():List<TaskResultVO>; private BoundedTaskPolicy/TaskContextVO; package TaskResultVO; PendingTransfer/ConfirmedTransfer original window | Gate B功能独立绿色，旧两FSM不变；新Pluginhooks尚未接入，因此Context/Runner将在File7/8得到缺集成RED。 | 先补新增enum的编译接点，再按typed RED实施bounded policy、窗口、输入与completion。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java` | bounded_plan_route_and_repeated_role; bounded_context_window_and_stored_confirmation; bounded_partial_semantic_allowlist; bounded_final_candidate_is_one_shot; bounded_audit_and_failed_cas | G7必须F>0/E0；随后允许File5 B，G8全selected绿色，Gate不再扩展范围。 | 保留17旧tests并先定义bounded task/window/frame/候选的typed RED。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java` | bounded_question_after_history_before_snapshot; bounded_question_once_across_capacity_retry_and_trim; bounded_partial_semantics_checked_before_return; bounded_bad_complete_frame_settles_known_usage; raw_unknown_on_tool_error_is_sanitized | 测试可编译但因Plugin无prepare/early-partial hook断言失败；无fixture/env/timeout当RED。 | 在Plugin接线前证明准确question时序/retry计量及partial校验缺口。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java` | bounded_repeated_role_tasks_use_distinct_windows; bounded_max_tasks_share_budget; bounded_final_json_is_answer_only; bounded_failure_remains_sticky | 正例RED只有JUnitF；Plugin集成后整个脚本耗尽且无额外modelcall。 | 用真实Runner/script模型证明重复role/currentquestion/tasks与旧native事实。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java` | beforeModelCallback; afterModelCallback | Gate/Plugin/真实Runner集成GREEN，尚无Executor tasks字段或新event type；由Files10/11先给RED。 | 将bounded question和frame validation接入现模型治理，不改变旧two/ordinarytool行为。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java` | bounded_result_has_ordered_tasks_and_last_role; bounded_event_type_and_payload; bounded_final_candidate_and_current_finish_fences | 缺Executor projection/newtype已证RED；oldcompletion tests保留。 | 在Executor改输出前锁current CAS、new event type与JSON-native tasks结果。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java` | schema_two_ordered_task_maps_round_trip; raw_records_are_not_json_contract_values; old_schema_one_unchanged | JSON-native construct/roundtrip证明可用；Executor仍等File12 GREEN。 | 证明先满足C的JSON-native DTO构造，再由现genericcodec承载schema2。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | executeScoped native binding/appender; output construction after requireCompletedOutputs | AR bounded core完整且可编译/有限运行；AS仍exact-two/defaultoff，无第三hostfactory。 | 只按verifiedtopology选择授权type并在现finish前投影JSON-native task输出。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| CREATE | `docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md` | exact approved Spec + Related Plans row | Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。 | 发布已批准规范，仅Related Plans backlink metadata差异。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| CREATE | `docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md` | Template5 all chapters/ordered paths/gates | Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。 | 发布本两Step计划与Root执行门禁。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| CREATE | `docs/egon/evidence/2026-11-05-bounded-task-runtime.md` | actual logs/counts/hashes/17MC/10Rules/REQ931-947 source coverage | Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。 | 记录Step1实际RED/A/typedRED/B/GREEN及core限界。 | Step 1 | REQ-931-942/945-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java` | decomposition_constructor_is_present; decomposition_flag_defaults_off; enabled_decomposition_contributes_exact_three; native_off_decomposition_on_still_no_map | 缺fifthctor/flag已证F；source仍old AS直到File2开始，不能把startup exception当无包装Error。 | 先锁fifthctor/flag/map绑定与默认资源缺口，再验证实际namedconsumer。 | Step 2 | REQ-943-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java` | record components(enabled,modelBeanName,modelName,modelRouteRef,decompositionEnabled); 5arg canonical compact @ConstructorBinding; public4arg delegating ctor | newhostvalueavailable，old4argsource/binary兼容；第三factory/map尚未注册。 | 追加defaultfalse fifthboolean并保持public4arg binary descriptor及可靠绑定。 | Step 2 | REQ-943-947 | Root / owning tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactoryTest.java` | bounded_factory_type_is_present; bounded_factory_metadata_and_tree_are_exact; bounded_factory_requires_enabled_flags_and_exact_model | 类型及typed行为缺失F已锁，允许File4 B；现两个factorytests不改继续回归。 | 在factory full实现前定义exact tree/model/instructions与无provider事实。 | Step 2 | REQ-943-947 | Root / owning tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactory.java` | implements NativeAgentCompilerStrategy; ctor(NativeAgentTemplateProperties,ChatModel,SpringAiModelAdapterFactory); apply(Scheduler); templateSpec(); usesChatModel(ChatModel) | factory完整可用但仍未namedmap注册，File5/6闭合默认flag接线。 | 实现第三受信factory，复用同qualifiedhostmodel和existingadapter。 | Step 2 | REQ-943-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java` | tianjiAgentCodeFirstFactories(NativeAgentTemplateProperties,@Qualifier("agentFlowChatModels") Map<String,ChatModel>,@Qualifier("springAiModelAdapterFactory") SpringAiModelAdapterFactory) | hostbothflag运行接线正确，默认资源仍等File6 falsekey。 | 将第三factory只在两flag都true时贡献到同一namedimmutablemap。 | Step 2 | REQ-943-947 | Root / owning tests |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | stellar.tianji.agent.native-templates.decomposition-enabled: false | 第三factory defaultoff交付，olddefaultmap2不变；须G18freshJAR和最终sourceaudit才Step2commit。 | 在唯一AS base profile加入第五个明确defaultfalsekey。 | Step 2 | REQ-943-947 | Root / owning tests |
| CREATE | `docs/egon/evidence/2026-11-05-bounded-task-templates.md` | actualStep2RED/typedRED/GREEN; finalcleanverify/181baseplusnewtests; AR/ASJAR/YAMLsha; 17MC/10Rules; REQ931-947 conformance | Step2源码交付可供用户审查，serviceclean；master仅Root信息更新；不启动任何运行环境。 | 收口两Step源码符合性、真实finite clean/JAR/旧SQL与未执行验收。 | Step 2 | REQ-943-947 | Root / owning tests |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Root先确认cwd为`/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`、HEAD237e1d2且clean；当前只读检查一致。924tracked/25mainSQL来自NativeStep51，执行前重新计算，差异必须解释，不能覆盖他人worktree。适用用户AGENTS指令：小改动、按逻辑提交、旧SQLimmutable、不自动启动运行环境/GUI。所有worker互斥ownership，不stage/revert他人路径。Root只能stage当前Step精确清单，不用`git add .`，不把target/tmp/secret/generated产物提交。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact source/command | Required state | Boundary |
| --- | --- | --- | --- |
| Java25 | `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` | 当前已知JDK；缺失回Root环境问题 | 不下载/升级依赖 |
| Source reactor | `./scripts/verify-source-reactor.sh` | 既有脚本，四邻仓POM/cache可用 | Root单Maven/offline |
| Settings | `/tmp/tianji-compat-fix/settings.xml` | 现无credential/cache配置可用；不存在不能改global | 非RED条件 |
| SDK | ADK1.13/GenAI1.77既有jar | SHA与§6.4一致 | javappublic事实，不是provider运行 |
| Documents | skill validators | 本轮preflight实际PASS；未来strictactualpaths | docs only |
| Database/provider | 既有PGtests仅compile | 不设置PGenv、不selectPostgres/网络/App | RuntimeUnverified |

### 6.3 Immutable constraints and approved decisions

全部25个已存在main-classpath `.sql`路径/bytes/SHA immutable；不新增migration/manifest、POM/dependency、C/CL/API/permissions/Components/PS/frontend生产修改。保持oldtuple/hash域/字段顺序、1/1schema、public4argPropertiesdescriptor、oldMap exact2、两参Appender、codec生产hash；newonlybounded1/2。RootPlan审查前不创建任何repo文件。Step1commit前不能启动Step2；Step2后仅Root信息更新外层master，分清T3-09本轮source与S03/provider/IAM/PG未验收、wholeGoal/T3-10/T4Required，Graph不是T3-09额外前置。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-931 | prepareModelRequest/validateResponseEnvelope为Gate同包签名；partial在afterModel touch/return前validate，complete在settle后observeResponse内先validate | 当前partial分支先于observeResponse | 补Primary partial allowlist且保持complete capture after settlement，不新API/状态owner | hookplacement测试会RED，回Gate/Plugin |
| PLAN-CLAR-932 | ROOT_PLAN窗口(ordinal1,generation0)，ROOT_ROUTE(next,returnedCount)，CHILD(i,i)，ROOT_FINAL(N,N) | SDK不同agentclonecontext，GateactualidentityMap | 仅实现已批准ordinal/generation/phase；无caller字段 | currentcontext tests阻断 |
| PLAN-CLAR-933 | 5arg canonical compact ctor显式@ConstructorBinding，4arg无注解 | Boot4.1.2 Constructors.getCandidateConstructors保全部nonsynthetic，deduce第二publicctor返回null | Root已认可，保证已批准fifthkey+4argABI，非新框架/Autowired | 真实ApplicationContextRunner绑定测试必须通过 |
| PLAN-CLAR-934 | DTO前exactJSON-nativeMap投影，不直接record | C.ContractChecks.copyValue在codec前拒record | Primary显式合同，Map/List同包投影无新跨层Mapper | Completion/CodecTest先构造DTO证明 |
| PLAN-CLAR-935 | 主Spec仅RelatedPlans一行修改；现source范畴由22paths与Rootmaster信息更新分开 | workspace外层不是serviceGit | Root明确确认归属，规范内容不变 | TinyDiff/strict/commitpath阻断 |

**已核公共SDK/JSON API。** ADK jar SHA `36f13af457b1657e1ef67cf5361f4eb59009a2b911bc5b0de61019f3cbea4e32`；GenAI1.77 jar SHA `454ecb0de8009da6191599a198df93e1e669c5937b9b0b4873c622bc8490f8a5`。Part没有discriminator：`text()/functionCall()/functionResponse()/executableCode()/codeExecutionResult()/fileData()/inlineData()/toolCall()/toolResponse()`均独立Optional；再检查`audioTranscription()/mediaResolution()/videoMetadata()/mediaProcessing()/speechMetadata()`。bounded只允许text与唯一nativecall，以上其它semantic/media非空全部拒绝；`thought()`只影响text抽取，FunctionCall仍必须验证；`thoughtSignature()/partMetadata()`及正常LlmResponse transportmetadata不作authority/新audit字段，不因存在就拒绝。禁止把metadata建议变成Spec额外禁令。

`LlmResponse.content():Optional<Content>`、`Content.parts():Optional<List<Part>>`；complete=`!partial().orElse(false)`，`turnComplete()`独立Optional，不作为transfer门槛。complete native `FunctionCall`还查`partialArgs():Optional<List<PartialArg>>`缺省/空、`willContinue():Optional<Boolean>`缺省/false；同时保持existing name/args/id规则；partial不产生pending/candidate/授权。`LlmRequest.Builder`没有contents getter：读取`request.build().contents():List<Content>`，copy追加后`request.contents(List<Content>)`。只用公开`context.invocationContext()`及actualidentity；不猜CallbackContext.eventId等于Event.id。

Jackson真实对象是`com.fasterxml.jackson.databind.JsonNode`，不是新JSONObject库：`isObject/isArray/isTextual/size/fieldNames/get/textValue`，配现JsonFactory/StreamReadConstraints/STRICT_DUPLICATE_DETECTION和JsonMapper FAIL_ON_TRAILING_TOKENS。全部getter/方法来自现源码及localjavap；实施若pin API漂移先回Root，不杜撰getter/反射SDK私有接口。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Govern ordered bounded tasks in the existing AgentRun pipeline

- Requirements: REQ-901, REQ-902, REQ-903, REQ-904, REQ-905, REQ-906, REQ-907, REQ-908, REQ-909, REQ-910, REQ-911, REQ-912, REQ-913, REQ-914, REQ-915, REQ-916, REQ-917, REQ-918, REQ-919, REQ-920, REQ-931, REQ-932, REQ-933, REQ-934, REQ-935, REQ-936, REQ-937, REQ-938, REQ-939, REQ-940, REQ-941, REQ-942, REQ-945, REQ-946, REQ-947
- Dependencies: baseline237e1d2 + 本Plan获Root sourceGO；无新依赖/迁移。
- Baseline state: 旧两native策略与181finite基座可用；缺TaskPlan/bounded enum/taskwindows/questionhooks/tasksoutput。
- Observable outcome: 单Runner可受控执行1–8orderedtasks、重复role独立summary、currentCAS/预算/候选一次消费，AS仍旧exacttwo。
- End state: exact15paths完整编译/typed/finiteGREEN，oldtuple/hash/SDKfacts与codec生产保持；Step2host尚未开启。
- Test-first gate: Required — G1/G2/G4/G5/G7/G9/G11分别为缺类型/解析/tuple/状态/集成/投影的JUnit assertion failures；任何compile/fixture/env不是RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001 — 逐项状态见下表，N/A必须证据。
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — Rule7本Step未改配置但核其不变，Rule11始终Applicable。
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVOTest.java`

- Purpose: 先锁新任务解析类型，再定义闭合JSON/构造/hash行为。
- Symbols: bounded_plan_type_is_present; accepts_one_and_eight_immutable_tasks; rejects_duplicate_unknown_coercion_and_trailing_json; preserves_safe_question_and_caps; framed_hash_preserves_order_and_original_strings
- Repository evidence: 同包NativeAgentCompilerStrategyTest使用assertDoesNotThrow/Class.forName作presence RED；当前NativeTaskPlanVO不存在。
- Dependencies and consumers: JUnit5；File2新增同包类型；typed阶段仅Jackson/JDK，无SDK/服务。
- Why now: 先于生产；反射presence使缺类成为JUnit assertion failure，非compile error。
- Contract/signature changes: A之前只Class.forName断言；File2 A存在后追加typed parse/record constructor/planHash tests，不用反射SDK。
- Input/output and state mapping: strictJSON→immutable ordered TaskVO；hashkeyorder/whitespace等价，原字段/顺序变化不同。
- Error and edge behavior: 覆盖1/8与0/9、重复ID/key、unknown/missing/null/错误类型/extra token/depth；UTF16/UTF8边界、controls/surrogates、redacted toString；无原文异常cause。
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001；无业务Bean/Mapper/新时间边界。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11 — 命名Test/VO、strict边界、immutable值、JDK/Jackson、原adk包。
- Implementation pseudocode:

```java
@Test bounded_plan_type_is_present(): assertDoesNotThrow(Class.forName(exact adk NativeTaskPlanVO))
// Root records presence RED before File2 A.
// After File2 A: compile typed tests; every positive setup/call uses assertDoesNotThrow.
assertEquals(expected ordered tasks, assertDoesNotThrow(() -> NativeTaskPlanVO.parse(validJson)).tasks())
assertThrows(IllegalArgumentException.class, () -> NativeTaskPlanVO.parse(closedShapeViolation))
assertThrows(AgentLedgerException.class, () -> NativeTaskPlanVO.parse(overLimitJson))
assertEquals(equal semantic fields hash); assertNotEquals(changed original string/order hash)
assertThrows(UnsupportedOperationException.class, () -> plan.tasks().clear())
```

- Verification contribution: G1 presence RED / G2 typed RED / G3 parser GREEN；Spec TEST-931-935。
- After this file: File1先得到真实缺类F；typed tests只在File2 A之后编译并因缺行为F；不能把ClassNotFound/NPE当Errors接受。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVO.java`

- Purpose: 拥有任务专用闭合解析/immutable构造与semantic framed hash。
- Symbols: record NativeTaskPlanVO(String schemaVersion,List<TaskVO> tasks); nested record TaskVO(String taskId,String agent,String question); static parse(String); String planHash()
- Repository evidence: AdkSemanticJsonDigest.fingerprint现有；ToolExecutionJsonCodec已用JsonFactory/JsonNode严格字段模式；不修改这两生产文件。
- Dependencies and consumers: File1；现Jackson2 com.fasterxml/JDK；Gate File5 B消费者。
- Why now: File1 presence RED后建compile A；A只声明approved API并fail-closed，typed RED后同一文件B完成。
- Contract/signature changes: A: constructors/parse/hash signatures + redacted toString，无成功计划行为；B:所有可达构造均校验，不接受caller planHash。
- Input/output and state mapping: schemaVersion literal string1；tasks1..8原序List.copyOf；ID regex [A-Za-z0-9][A-Za-z0-9_.:-]{0,47}逐值唯一；fixed pool；question安全原值。
- Error and edge behavior: shape非法固定IAE无Jackson cause；容量明确TIANJI_REQUEST_TOO_LARGE；禁trim/normalize/截断/弱类型转换；canonical field序列hash不使用raw JSON whitespace。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11 — immutable VO而非普通实体record；严格所有构造；现Jackson/JDK；不建Utils/Converter/依赖。
- Implementation pseudocode:

```java
// A after G1: compile immutable values/signatures; parse/hash reject with fixed safe exception.
// Return to File1 typed tests, record G2 RED; only then B below.
JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .streamReadConstraints(maxNestingDepth=4,maxNameLength=32,maxStringLength=16384,maxNumberLength=32)
JsonMapper.builder(factory).enable(FAIL_ON_TRAILING_TOKENS).disable(ALLOW_COERCION_OF_SCALARS)
check raw UTF16 <=16384 and UTF8 <=65536 before parse, reject isolated surrogates
JsonNode root = mapper.readTree(raw); require isObject/size/fieldNames exact {schemaVersion,tasks}
require root.get("schemaVersion").isTextual() && textValue().equals("1")
require tasks.isArray() and 1..8; each node exact fields and isTextual(), then new TaskVO(original strings)
compact constructors defensive-copy, validate all fields/duplicates and canonical encoded size; no bypass constructor
planHash = AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_TASK_PLAN_V1", ["1",count,ordered id,agent,question...])
catch JSON IOException -> fixed IAE without raw cause; preserve explicit size code; redacted toString
```

- Verification contribution: G3；constructor/parse/hash/bounds tests all pass，现digest/toolcodec hashes不变。
- After this file: Parser完整GREEN，可供Compiler/Gate编译；无新topology执行或host注册。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java`

- Purpose: 在enum与schema扩展前锁exact third tuple和old hash冻结。
- Symbols: bounded_topology_is_present; bounded_tuple_is_exact_and_old_tuples_unchanged; shared_or_reserved_output_keys_are_rejected
- Repository evidence: 当前两topology/1-1/旧canonical hash测试；valueOf(String)是现enum公开入口。
- Dependencies and consumers: 现TemplateSpec/AgentSpec；Files4-5 paired compile seam；原tests保留。
- Why now: 先在旧enum上assertDoesNotThrow(valueOf("BOUNDED_RESEARCH_V1"))得到F，无新enum直接常量编译依赖。
- Contract/signature changes: typed helper从valueOf取enum；所有positive构造assertDoesNotThrow、negative assertThrows，禁止fixture error。
- Input/output and state mapping: bounded exact codeRef/root/pool/outputkeys/flags/depth2/max16/poolOrder/schema1-2；old两tuple与hash fields/domain顺序不变。
- Error and edge behavior: wrong codeRef/name/schema/limits/order/shared keys全部拒；旧1-1仍通过。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 9, Rule 11 — immutable metadata与已选Strategy，不改旧DTO/enum持久编码。
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> TransferTopologyEnum.valueOf("BOUNDED_RESEARCH_V1")) // G4 old enum RED
// After Files4 A +5 A, add typed assertions and record G5 semantic RED.
bounded = assertDoesNotThrow(() -> new TemplateSpec(exact bounded 1/2 tuple))
assertEquals(2,bounded.maxDepth()); assertEquals(16,bounded.maxTransfers())
assertThrows(IllegalArgumentException.class, () -> wrong bounded tuple or shared key)
assertEquals(frozen baseline hashes, old conditional/supervisor topologyHash(POLICY))
```

- Verification contribution: G4/G5/G6；Spec TEST-931/TEST-945。
- After this file: 旧enum缺失F已记录；typed tests待paired A后，仅缺tuple行为F。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java`

- Purpose: 增加唯一bounded topology/schema branch，保持原tuple/hash ABI。
- Symbols: TransferTopologyEnum.BOUNDED_RESEARCH_V1; TemplateSpec constructor; validateTopology
- Repository evidence: 当前enum两值，validateTopology/requireAgent严格固定元数据，topologyHash按既有framed fields。
- Dependencies and consumers: File3 tests；File5 Gate多个exhaustive switch同时需case；AS新factory在Step2。
- Why now: File3 G4后先A；不得在File5 A补齐exhaustive switches前运行编译。File5 A后G5 typed RED，再回本File4 B并G6。
- Contract/signature changes: A仅enum及可编译fail-closed bounded分支；B按topology允许旧1/1或新1/2；public record/Function ABI不变。
- Input/output and state mapping: new exact tuple来自Spec§7.3.1；requiredChildOrder仅canonical pool顺序；topologyHash域和所有原fields/顺序不改。
- Error and edge behavior: nonempty/unknown/wrong tuple拒；不得放开所有schema2或旧codeRef1/2。
- Standards impact: MC-ARCH-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11 — 沿Strategy值模型；enum内部仅name hash，不新增ordinal存储。
- Implementation pseudocode:

```java
// A: append enum BOUNDED_RESEARCH_V1; bounded validation initially throws existing invalid().
// File5 A adds all matching exhaustive case labels before any Maven invocation.
// G5 typed RED now compiles; then B:
switch(topology): old two cases retain exact existing validation; bounded delegates exact approved tuple checks
require schema = old ? ("1","1") : bounded ("1","2"); reject all other combinations
require codeRef=bounded-research-v1, root=bounded_supervisor, exactly approved two children/flags/keys
require depth2,maxTransfers16,poolOrder=[bounded_researcher,bounded_critic]
keep topologyHash domain and field serialization byte-for-byte for old variants
```

- Verification contribution: G6 compiler GREEN；old expected hashes literal回归，source diff review旧hash方法无改动。
- After this file: File4 B完成后Compiler绿色；Gate仍A/fail-closed至File6 RED后B；无半编译commit。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java`

- Purpose: 先补新增enum的编译接点，再按typed RED实施bounded policy、窗口、输入与completion。
- Symbols: prepareModelRequest(CallbackContext,LlmRequest.Builder); validateResponseEnvelope(CallbackContext,LlmResponse); requireCompletedTasks():List<TaskResultVO>; private BoundedTaskPolicy/TaskContextVO; package TaskResultVO; PendingTransfer/ConfirmedTransfer original window
- Repository evidence: 现bind/requireExpectedAgent/pending+confirmed IdentityHashMap/validateTransition/markDispatched/completeTransition/transitionComplete/observeEvent/markFatal；原两FSM已17tests。
- Dependencies and consumers: File2 Parser/File4 enum，File6 Gate tests；Plugin File9调用新hooks，Executor File12消费结果。
- Why now: A必须紧跟File4 A，在同一编译批次补所有exhaustive switches/新签名；B冻结到File6真正typed RED后。
- Contract/signature changes: A所有bounded新路径安全拒绝，旧两路径no-op/原逻辑；新methods签名可编译。B只bounded启用private State policy；无新port/registry/DB。
- Input/output and state mapping: plan→≤8 states/ordinal；context identity→TaskContextVO；builder identity→samewindow once marker；outboundCAS后DISPATCHED、return action后RETURNED；rolelastsummary+orderedtasks。
- Error and edge behavior: stored confirmation先actualcontext+callID查原window/args/action/result，identical跨phase no-op；无confirmation才验current；fatal/unknown仍按现safe token，永不重新派发。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — 同包immutable值/State policy；actualidentity/strictJackson/JDK；java.time旧边界保持；不加Bean/Logger/Converter。
- Implementation pseudocode:

```java
// A: after File4 A, all enum exhaustive switches add BOUNDED -> safe false/reject;
// add three own-package signatures + TaskResultVO record; old variants no-op where new hooks are called.
// G5 compiles paired A; File4 B/G6; then File6 typed G7 RED; only then this B.
BoundedTaskPolicy owns frozen NativeTaskPlanVO, per-task NEW/DISPATCHED/RETURNED and current phase/index
TaskContextVO ROOT_PLAN(ordinal1,generation0), ROOT_ROUTE(nextOrdinal,returnedCount), CHILD(i,i), ROOT_FINAL(N,N)
requireExpectedAgent binds actual InvocationContext identity to current window; same Session reference/invocationId required
validateResponseEnvelope: bounded only, validate current context; inspect public Part getters per §6.4; partial!=true is complete
  reject forbidden semantic payload on partial too; on complete require native FunctionCall partialArgs empty/willContinue!=true
observeResponse (called after durable complete usage settlement): first validateResponseEnvelope, then plan/route/summary/state capture; first plan+task1 call freezes all, later route text exact empty, child summary+parentcall same response
beforeTool: existing exact Method/callID/context/MODEL/source/Appender CAS; attach planHash/taskOrdinal; NEW->DISPATCHED only after CAS
confirmed call replay: first lookup by actual context+ID, compare stored original window/args/result/action, identical -> no-op
unconfirmed afterTool: validate current window+authorized pending+actual transfer action; return commits task summary/RETURNED
prepareModelRequest: CHILD only, identity-mark builder/window; copy request.build().contents(); append one Jackson-encoded USER question Content
root final: store actual context+ROOT_FINAL/N+text once; second complete candidate fatal
observeEvent: consume candidate once only, match root/current invocation/final/no calls/no pending/exact text; wrong/second consumption fatal
requireCompletedTasks: all returned + candidate consumed + fatalfree -> immutable ordered TaskResultVOs; no outputKey reads
close/fatal: clear bounded pending/windows/markers; preserve old token/control semantics and old two policies
```

- Verification contribution: G7 typed Gate RED→G8 Gate GREEN；Spec TEST-932/TEST-936/TEST-937/TEST-939-944/945。所有SDKgetter事实见§6.4，不使用SDK私有反射。
- After this file: Gate B功能独立绿色，旧两FSM不变；新Pluginhooks尚未接入，因此Context/Runner将在File7/8得到缺集成RED。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java`

- Purpose: 保留17旧tests并先定义bounded task/window/frame/候选的typed RED。
- Symbols: bounded_plan_route_and_repeated_role; bounded_context_window_and_stored_confirmation; bounded_partial_semantic_allowlist; bounded_final_candidate_is_one_shot; bounded_audit_and_failed_cas
- Repository evidence: 现GateTest公开SDK对象/transfer Method fixtures；File5 A提供编译签名而bounded行为拒绝。
- Dependencies and consumers: Files2/4B/5A；真实SDK Context/Part/Event，mock仅appender事实。
- Why now: File5 B前记录G7；所有positive setup Gate.bind/response/action包assertDoesNotThrow，invalid包assertThrows。
- Contract/signature changes: 按Spec TEST-936-944，不为绿色改合同或使用假的全agent同context。
- Input/output and state mapping: two same-role tasks→distinct contexts/windows/summary；confirmation replay oldwindow no-op；task2不变。
- Error and edge behavior: plan/order/part/context/CAS/summary/one-shot三负例与repeat Event消费；rawunknown只在生产Gate appender内部注入。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — 实际SDKpublic事实/State边界、无服务；不可把模拟等同native运行。
- Implementation pseudocode:

```java
arrange exact bounded tree/spec + actual Session/InvocationContext; all setup uses assertDoesNotThrow
run root plan+firstcall with partial=false,turnComplete=false; assert no task completion before CAS/action
confirm child return; reuse same role with new InvocationContext -> two task results, role map last summary
replay stored original confirmation after phase advances -> no-op and current task unchanged
old unconfirmed context/call/builder -> safe fatal; CAS throw -> no DISPATCHED/RETURNED
validate partial Part functionResponse/media/code -> assertThrows; normal thoughtSignature/partMetadata do not grant authority
complete FunctionCall with nonempty partialArgs/willContinue=true -> reject; turnComplete=false alone valid
all returned: first current ROOT_FINAL candidate captured; wrong Event text / second candidate / old root / second consume -> fatal
assert exact six audit keys; private sentinel absent in safe error/cause; no plausible later root can succeed
```

- Verification contribution: G7/G8；新方法均真实执行，0 fixture errors；旧17tests继续通过。
- After this file: G7必须F>0/E0；随后允许File5 B，G8全selected绿色，Gate不再扩展范围。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`

- Purpose: 在Plugin接线前证明准确question时序/retry计量及partial校验缺口。
- Symbols: bounded_question_after_history_before_snapshot; bounded_question_once_across_capacity_retry_and_trim; bounded_partial_semantics_checked_before_return; bounded_bad_complete_frame_settles_known_usage; raw_unknown_on_tool_error_is_sanitized
- Repository evidence: 现19contexttests、模型预算/session/context snapshot/trim fixtures；现Plugin afterModel partial早return在Gate.observeResponse之前。
- Dependencies and consumers: File5 B Gate；现Plugin13argctor已经存在；File9负责集成，sourcehistory/budget mocks而非新PDP。
- Why now: 先定义缺hooks的断言RED，不能先改Plugin。
- Contract/signature changes: no ctor change；精确验证revalidateHistory之后、snapshots.prepare之前；AgentExecutionSnapshot/system不变而PreparedCall/ADK_CONTENTS含question。
- Input/output and state mapping: captured request contents/hash→现estimator/reservation metadata；一次capacity exception重订阅同builder，question Content恰1。
- Error and edge behavior: partial非法payload在idle touch/return前fatal；complete坏语义帧先durable settle knownusage再fatal，error/interrupted原UNKNOWNsettle不变；oldtwo no-op；samegeneration marker与crossgeneration builder拒绝；known/unknown errorbranchunit不走Plugin外rawspy。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — 现Snapshot/time/计量权限边界，实际content而非伪system权限；无新Bean。
- Implementation pseudocode:

```java
beforeModel with current child context; ledger.history answer asserts request has not yet gained question
assertDoesNotThrow(beforeModel.blockingGet); capture estimator LlmRequest + budget metadata
assert USER marker occurs once; ADK_CONTENTS digest equals request including current question; system/AgentExecutionSnapshot unchanged
force first reserve CAPACITY_EXCEEDED then reserved; compare retry request retains exactly one current question
enable existing snapshots+trim, retainRecentUserTurns>=1; prepared original-derived contents preserve last question
admit current model then afterModel(partial=true,forbidden semantic Part): assertThrows before idle touch/successful empty return
complete bad semantic response with valid reported usage: verify settleBudget(known actual) occurs before Gate validation fatal, no task/state success
error/interrupted response: verify original UNKNOWN settlement, preserving prior safe disposition
normal transport metadata and transfer turnComplete=false: assertDoesNotThrow
call onToolError directly with private raw sentinel -> Maybe.error(safe); knownsafe -> empty; fatal sticky
```

- Verification contribution: G9 Context/Runner RED，G10 GREEN；TEST-937/TEST-938/TEST-939/TEST-942/TEST-944；既有19保留。
- After this file: 测试可编译但因Plugin无prepare/early-partial hook断言失败；无fixture/env/timeout当RED。

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java`

- Purpose: 用真实Runner/script模型证明重复role/currentquestion/tasks与旧native事实。
- Symbols: bounded_repeated_role_tasks_use_distinct_windows; bounded_max_tasks_share_budget; bounded_final_json_is_answer_only; bounded_failure_remains_sticky
- Repository evidence: 现7真实Runner tests、InMemorySessionService、ScriptModel/globalresponsequeue、per-event state快照和safe appender故障注入。
- Dependencies and consumers: Gate B、Plugin现API、Root脚本模型；ledger/budget可mock，SDK agent/function/action不能mock成假流程。
- Why now: 与File7一同在File9前RED；所有happy run以assertDoesNotThrow包裹，负例assertThrows。
- Contract/signature changes: re-use现Rig支持指定 bounded spec；禁止新productionhelper/第二Runner/PG选择。
- Input/output and state mapping: plan→root/role1/root/same-role2/root/critic/rootfinal；questioncapture→each righttask；output tasks独立，rolemap最后summary。
- Error and edge behavior: 0/9/duplicate/unknownrole/wrongorder由unit及代表Runner覆盖；共享预算耗尽/cancel-fencedappender、late plausible root fatal；SDKchildkey晚rootfinal覆盖保持独立。
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — sameRunner/预算/actualSDKidentity/State与privacy边界。
- Implementation pseudocode:

```java
ScriptModel queue uses root plan+nativecall, child summary+native parent call, root empty-text nextcall, final JSON answer
all transfer responses partial=false,turnComplete=false; no provider/network
assertDoesNotThrow(rig::run); assert ordered author/target/task summaries and current question per child request
assertNotSame(previous same-role InvocationContext,current); assertSame(actual Session); same invocationId
capture handoff-time Session/stateDelta: no child outputKey write then; final SDK role key may be root final and is ignored
assert exactly shared MODEL reservations and zero ordinary TOOL reservations; low policy budget cannot be expanded
appender inner safe/unknown failure -> SDK receives sanitized boundary; failureSignal sticky; plausible later final fails
retain all seven old native Runner cases unmodified in behavior
```

- Verification contribution: G9 RED→G10 GREEN；TEST-936-945；真实inmemorySDK，不是实际provider/IAM/PG。
- After this file: 正例RED只有JUnitF；Plugin集成后整个脚本耗尽且无额外modelcall。

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`

- Purpose: 将bounded question和frame validation接入现模型治理，不改变旧two/ordinarytool行为。
- Symbols: beforeModelCallback; afterModelCallback
- Repository evidence: beforeModel现revalidateHistory→snapshots.prepare→applySources→estimate/trim/reserve；afterModel现tracking→partial早return→settle→observeResponse。
- Dependencies and consumers: Files7/8 RED、File5新hooks；现MODEL authority/lease/source/session/bound estimator保持。
- Why now: 只在真实缺集成RED后实现两个调用点，不添加构造器/Bean/后台执行器。
- Contract/signature changes: beforeModel exact调用gate.prepareModelRequest；afterModel仅在partial分支早return/idle touch前调用gate.validateResponseEnvelope；complete帧仍先durable settlement，随后observeResponse内部先validate再parse/state；methods对oldtwo内部no-op。
- Input/output and state mapping: current USER question在PreparedCall.original/ADK_CONTENTS/estimate内；原AgentExecutionSnapshot/system/policy不变；同builderidentity marker由Gate持有。
- Error and edge behavior: history失败不能注入；prepare失败不能reserve；partial非法在touch/成功idle return前safe fatal，不因earlyreturn跳过；complete非法语义也必须先settle已报告known usage后再fatal；error/interrupted保持原UNKNOWNsettle后安全失败。保持callbackErrors/known token分类和现retry。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — 保留现LoggerFactory技术logger/技术构造与依赖；不加business注入；existingState策略/权限/时间。
- Implementation pseudocode:

```java
beforeModel deferred body:
  keep tracking / gate.requireExpectedAgent / MODEL / session fence as existing
  keep conditional revalidateHistory at its exact position
  if(nativeGate!=null) nativeGate.prepareModelRequest(context,request) // bounded-only inside Gate
  keep snapshots.prepare, applySources(copy current contents), estimate, trim, reserve, admission unchanged
  same request-builder retry reaches idempotent preparation; no second question Content
 afterModel deferred body:
  keep requireNativeContext(context,true)
  if(response.partial().orElse(false)):
    if(nativeGate!=null) nativeGate.validateResponseEnvelope(context,response) // BEFORE idle touch/early return
    keep existing idle touch/return; no complete settlement on this partial branch
  keep error/interrupted -> original UNKNOWN usage settlement -> safe failure
  keep complete actualUsage -> durable settleBudget; then Gate.observeResponse internally validates envelope before parse/state
  invalid complete semantics cannot skip already reported known usage settlement
 keep old constructors/LoggerFactory技术logger/error/control/ordinary tools and all source authority untouched
```

- Verification contribution: G10三coreselectors green，随后S1 regression；Code review exact hook positions、oldpaths/sourcehash。
- After this file: Gate/Plugin/真实Runner集成GREEN，尚无Executor tasks字段或新event type；由Files10/11先给RED。

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`

- Purpose: 在Executor改输出前锁current CAS、new event type与JSON-native tasks结果。
- Symbols: bounded_result_has_ordered_tasks_and_last_role; bounded_event_type_and_payload; bounded_final_candidate_and_current_finish_fences
- Repository evidence: 现finite Completion mocks/leasedfinish及sameboundarycode regressions；Executor真实方法executeScoped。
- Dependencies and consumers: Gate/Plugin B，现Executor仍旧eventtype及agents/text；File12实现投影。
- Why now: 先让缺tasks/type成为assertNotNull/assertEquals的F，不能get(null)或强转NPE成为E。
- Contract/signature changes: 现AgentExecutionResult DTO不改；output.tasks必须List<Map>；topology选newtype不可由模型决定。
- Input/output and state mapping: Gate结果→actual execution outputMap/CAS eventcaptor→finish request；taskorder/ids/summary/knownagents精确。
- Error and edge behavior: current cancel/lease/revision/deadline拒后续转交；rootcandidatewrong/second/old/window失败不得finishCOMPLETED。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 9, Rule 10, Rule 11 — JSON-native DTO/原currentCAS/时间resolver，不扩C/CL。
- Implementation pseudocode:

```java
result = assertDoesNotThrow(() -> executor.execute(existing trusted actor/query))
assertNotNull(result.output().get("tasks")); assertInstanceOf(List.class,...) before element access
for each task assertInstanceOf(Map.class,...); assertEquals(Set.of("taskId","agent","summary"), map.keySet())
assert exact frozen order and independent same-role summaries; agents last-role and current root text
capture ledger event appends: bounded -> NATIVE_TASK_TRANSFER_AUTHORIZED + exact six safe keys
old two -> existing type/exact four keys; failed current append never confirms task/native dispatch
reuse existing leased-finish cancellation/terminal resolver cases; no forged context/lease/pdp
```

- Verification contribution: G11 Executor RED，仅missing tasks/type assertions；G12完成后S1 GREEN；TEST-940/TEST-941/TEST-943/TEST-944/TEST-945。
- After this file: 缺Executor projection/newtype已证RED；oldcompletion tests保留。

#### File 11 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java`

- Purpose: 证明先满足C的JSON-native DTO构造，再由现genericcodec承载schema2。
- Symbols: schema_two_ordered_task_maps_round_trip; raw_records_are_not_json_contract_values; old_schema_one_unchanged
- Repository evidence: AgentExecutionResult constructor先ContractChecks.jsonObject，copyValue只accept scalar/Map/List；现codec MAX_RESULT_BYTES=2MiB。
- Dependencies and consumers: 现C DTO/genericcodec；File12输出同表示；不新生产codec分支。
- Why now: 与File10同批锁representation；此codec独立回归可已有GREEN，不伪称它自身RED；G11语义RED由Completion承担。
- Contract/signature changes: immutable exact3key Map<String,Object>列表，不把TaskResultVO直接塞output；schemaVersion2字段在旧DTO内。
- Input/output and state mapping: 先assertDoesNotThrow(new AgentExecutionResult(...output2...))，再write/readResult→ordered List<Map>，keys3。
- Error and edge behavior: 直接record值assertThrowsIAE证明边界；2MiB cap/schema1/event/envelope UTC原行为保持。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11 — 现C边界验证/只Jackson/原time，不加Converter或codec实现。
- Implementation pseudocode:

```java
tasks = List.of(Map.<String,Object>of("taskId","a","agent","bounded_researcher","summary","one"), second exact map)
output = immutable Map<String,Object> containing agents,text,tasks
dto = assertDoesNotThrow(() -> new AgentExecutionResult(RUN,COMPLETED,output,null,"2",...))
stored = assertDoesNotThrow(() -> codec.write(dto,MAX_RESULT_BYTES)); decoded = codec.readResult(stored)
assert exact ordered task Maps and Set.of(taskId,agent,summary); no question/raw record
assertThrows(IllegalArgumentException.class, () -> new AgentExecutionResult(...Map.of("tasks",List.of(rawRecord))...))
keep schema1/event/2MiB boundary assertions; production AgentJsonCodec hash stays baseline
```

- Verification contribution: G11/G12/G18 finite verify；TEST-949；现codec已支持则此test可先GREEN，绝不改生产求RED。
- After this file: JSON-native construct/roundtrip证明可用；Executor仍等File12 GREEN。

#### File 12 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: 只按verifiedtopology选择授权type并在现finish前投影JSON-native task输出。
- Symbols: executeScoped native binding/appender; output construction after requireCompletedOutputs
- Repository evidence: 现executeScoped单Runner/bindNative/RunEventSink current CAS；现requireNativeDefinition比schema字符串，不硬编码1；现output只agents/text。
- Dependencies and consumers: Files10/11 G11 RED、Gate B requireCompletedTasks；现C DTO/codec/store原样。
- Why now: 最小GREEN闭合已有完整Gate/Plugin路径，不先引入AS factory。
- Contract/signature changes: Appender两参ABI不变；只bounded type=NATIVE_TASK_TRANSFER_AUTHORIZED；只bounded output.tasks=immutable ordered List<Map<String,Object>>。
- Input/output and state mapping: TaskResultVO -> Map.<String,Object>of(taskId,agent,summary) -> immutableList -> existingAgentExecutionResult；agents/text流程保留。
- Error and edge behavior: 任务不全/candidate未消费/fatal先失败；无record/C/codec改动；currentfinish/cancel/deadline/source/ordinarytool保持。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 — 现技术构造/Bean边界；显式同包JSON投影非跨层Mapper；现Strategy/State/currenttime。
- Implementation pseudocode:

```java
nativeType = nativeSpec.topology()==BOUNDED_RESEARCH_V1 ? "NATIVE_TASK_TRANSFER_AUTHORIZED" : existing literal
NativeAgentGate.bind(...,(author,payload)->eventSink.append(nativeType,author,payload))
keep bindNative estimator, single Runner, explicit RunConfig/shared policy and failureSignal merge unchanged
on actual event keep projector then activeGate.observeEvent before existing append
after runner keep requireCompletedOutputs; bounded only call requireCompletedTasks()
project each TaskResultVO into Map.<String,Object>of("taskId",...,"agent",...,"summary",...)
collect immutable ordered List<Map<String,Object>>; put tasks into existing output before constructing AgentExecutionResult
schemaVersion stays snapshot.definition.outputSchemaVersion; no oldschema1 tasks field
keep sealAndFreeze/finishOrResolve/current revision and existing failure/cancel/source classifications unchanged
```

- Verification contribution: G12 S1 selected GREEN+regression；capture exact DTO/event/currentCAS behavior。
- After this file: AR bounded core完整且可编译/有限运行；AS仍exact-two/defaultoff，无第三hostfactory。

#### File 13 — `CREATE docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md`

- Purpose: 发布已批准规范，仅Related Plans backlink metadata差异。
- Symbols: exact approved Spec + Related Plans row
- Repository evidence: 现docs/egon/{spec,plan,evidence}命名风格；当前新paths不存在；本/tmp已审规范作为唯一内容来源。
- Dependencies and consumers: Files1-12行为与命令证据；既有NativeSpec/NativePlan只读。
- Why now: core GREEN后发布文档，Step1 commit前strict+路径审查；不把后续Step2/运行测试写成已完成。
- Contract/signature changes: Spec仅Related Plans元数据backlink；Plan为本次批准文本；EVD仅已实际完成证据。
- Input/output and state mapping: normative SHA18f4...与backlink tiny delta可重建；实际测试F/E/S、时间/log/oldSQL/JAR边界。
- Error and edge behavior: 任何新Spec语义变更返回Root；不得静默改已批内容/捏造数值；newfactory/真实验收仍待Step2/用户。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001；其余17项独立closure在EVD，不一行合并。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — 文档逐项体现effective technical exceptions和时间/配置/JSON/State约束，不扩大源码。
- Implementation pseudocode:

```text
copy exact Root-approved document into declared path; no prose redesign
for Spec allow only Related Plans row diff against normative SHA18f4; preserve all 1086 lines otherwise
for EVD record actual commands/logs/test counts/failures/errors/skips, current bound behavior and remaining AS/runtime gates
run both strict validators at actual relative paths; perform Step1 exact-path staging and immutable SQL audit
no build outputs, private model bodies, credentials or SQL in commit
```

- Verification contribution: G13 strict/docs + S1 perStepMC/Rule + Root path-limited commit。
- After this file: Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。

#### File 14 — `CREATE docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md`

- Purpose: 发布本两Step计划与Root执行门禁。
- Symbols: Template5 all chapters/ordered paths/gates
- Repository evidence: 现docs/egon/{spec,plan,evidence}命名风格；当前新paths不存在；本/tmp已审规范作为唯一内容来源。
- Dependencies and consumers: Files1-12行为与命令证据；既有NativeSpec/NativePlan只读。
- Why now: core GREEN后发布文档，Step1 commit前strict+路径审查；不把后续Step2/运行测试写成已完成。
- Contract/signature changes: Spec仅Related Plans元数据backlink；Plan为本次批准文本；EVD仅已实际完成证据。
- Input/output and state mapping: normative SHA18f4...与backlink tiny delta可重建；实际测试F/E/S、时间/log/oldSQL/JAR边界。
- Error and edge behavior: 任何新Spec语义变更返回Root；不得静默改已批内容/捏造数值；newfactory/真实验收仍待Step2/用户。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001；其余17项独立closure在EVD，不一行合并。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — 文档逐项体现effective technical exceptions和时间/配置/JSON/State约束，不扩大源码。
- Implementation pseudocode:

```text
copy exact Root-approved document into declared path; no prose redesign
for Spec allow only Related Plans row diff against normative SHA18f4; preserve all 1086 lines otherwise
for EVD record actual commands/logs/test counts/failures/errors/skips, current bound behavior and remaining AS/runtime gates
run both strict validators at actual relative paths; perform Step1 exact-path staging and immutable SQL audit
no build outputs, private model bodies, credentials or SQL in commit
```

- Verification contribution: G13 strict/docs + S1 perStepMC/Rule + Root path-limited commit。
- After this file: Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。

#### File 15 — `CREATE docs/egon/evidence/2026-11-05-bounded-task-runtime.md`

- Purpose: 记录Step1实际RED/A/typedRED/B/GREEN及core限界。
- Symbols: actual logs/counts/hashes/17MC/10Rules/REQ931-947 source coverage
- Repository evidence: 现docs/egon/{spec,plan,evidence}命名风格；当前新paths不存在；本/tmp已审规范作为唯一内容来源。
- Dependencies and consumers: Files1-12行为与命令证据；既有NativeSpec/NativePlan只读。
- Why now: core GREEN后发布文档，Step1 commit前strict+路径审查；不把后续Step2/运行测试写成已完成。
- Contract/signature changes: Spec仅Related Plans元数据backlink；Plan为本次批准文本；EVD仅已实际完成证据。
- Input/output and state mapping: normative SHA18f4...与backlink tiny delta可重建；实际测试F/E/S、时间/log/oldSQL/JAR边界。
- Error and edge behavior: 任何新Spec语义变更返回Root；不得静默改已批内容/捏造数值；newfactory/真实验收仍待Step2/用户。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001；其余17项独立closure在EVD，不一行合并。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — 文档逐项体现effective technical exceptions和时间/配置/JSON/State约束，不扩大源码。
- Implementation pseudocode:

```text
copy exact Root-approved document into declared path; no prose redesign
for Spec allow only Related Plans row diff against normative SHA18f4; preserve all 1086 lines otherwise
for EVD record actual commands/logs/test counts/failures/errors/skips, current bound behavior and remaining AS/runtime gates
run both strict validators at actual relative paths; perform Step1 exact-path staging and immutable SQL audit
no build outputs, private model bodies, credentials or SQL in commit
```

- Verification contribution: G13 strict/docs + S1 perStepMC/Rule + Root path-limited commit。
- After this file: Step1完整证据/规范/计划可review；Root仅一次semanticcommit后才允许Step2。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: §8 G1-13及S1 regression的逐字命令；Root独占。
- Expected result: 所有named owningreports存在，GREEN为0F/E/S；RED仅指定缺行为JUnitF，strict/paths/旧SQL均通过。
- Failure returns to: 对应File N；API/设计漂移回Root/Spec，环境问题先修复且不得记RED。
- Completion criteria: 本Step下表17MC与10Rule逐项闭合，strict/GREEN与exactpaths证据完整。
- Rollback: review/source层只回退本Step提交的声明paths；无数据迁移；不得rewritesharedhistory或回退他人改动。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVOTest.java`; `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVO.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java`; `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java`; `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java`; `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`; `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java`; `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`; `docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md`; `docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md`; `docs/egon/evidence/2026-11-05-bounded-task-runtime.md`

```text
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVOTest.java
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeTaskPlanVO.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java
docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md
docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md
docs/egon/evidence/2026-11-05-bounded-task-runtime.md
```

- Commit: `feat(agent): govern bounded research task decomposition`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | §4.7现technicalprofile/declaredpaths，无新layer；Rule11核对；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-REUSE-001 | Applicable | reuseledger+实际imports/adapter/currentCAS；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-DEP-001 | Applicable | 所有POM/依赖/25SQL hashes baseline相同；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-NAME-001 | Applicable | VO/Factory/Properties/Gate/Tests语义命名与filetree；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-VALID-001 | Applicable | 本Step所有JSON/SDK/hosthandoff正反测试与先验时序；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-MODEL-001 | Applicable | immutable VO/configcompact构造、防御copy、无普通实体record；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-CONVERT-001 | Not applicable | 同包技术投影/配置值，无新跨层Converter；不造Mapper；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-LOG-001 | Not applicable | 无新businesslogger/Service；现safePlugin日志保持，rawplan/sentinel不echo；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-BEAN-001 | Not applicable | 未改任何@Bean/DI注册/ctor ABI；保留现Plugin技术构造；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-UTIL-001 | Applicable | JDK/现Jackson/SDK，no utilitydependency；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-JSON-001 | Applicable | strictplan/JSON-nativeDTO/closedaudit；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-TIME-001 | Applicable | 原Instant/Duration/currentDBtime、deadline/cancel回归；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-CONFIG-001 | Not applicable | AS配置/四key/defaulttwo路径不改，排除hash证明；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-PATTERN-001 | Applicable | existingCompilerStrategy+boundedState/两阶段确认；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-SCOPE-001 | Applicable | exact15paths+excludedtracked/25SQL审计，无unrelatedstage；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-TEST-001 | Applicable | 本StepactualRED/A/typedRED/B/GREEN、0E/0S与namedreports；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-BLOCKER-001 | Applicable | 所有适用项PASS；N/A有本行证据；Root sourceGO与Stepcommit门禁；执行EVD记录实际PASS或证据N/A，任何失败不commit |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | 语义角色/文件命名清单；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 2 | Applicable | 所有实际SDK/JSON/host边界negative和时序；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 3 | Applicable | immutable记录/防御copy/同包Map投影；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 4 | Applicable | 保留现technical构造/LoggerFactory技术logger；AS修改时explicitBean/qualifiers/ConstructorBinding；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 5 | Applicable | imports/POM基线，只有JDK/现Jackson/SDK/adapter；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 6 | Applicable | closedJSON/audit/result/metadata合同，禁止ordinal持久化；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 7 | Not applicable | 唯一ASprofilefalsekey/actualJAR，未改Step给excludedhash；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 9 | Applicable | 实际CompilerStrategy+State或Factory注册测试；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 10 | Applicable | 现java.time/UTC/currentclock边界，未改Step明确N/A；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 11 | Applicable | 现technicalprofile/依赖方向/Components复用，Applicable；逐条EVD记PASS或本项有据N/A，不合并 |

### Step 2 — Register the bounded factory behind the default-off decomposition flag

- Requirements: REQ-931, REQ-939, REQ-943, REQ-944, REQ-945, REQ-946, REQ-947
- Dependencies: Step1 exactsemanticcommit已存在且15paths/GREEN审计完成；不能只有未提交工作树。
- Baseline state: AR boundedcore完整；AS四字段/旧4argctor/exacttwo/四keydefaultoff仍与baseline一致。
- Observable outcome: nativeenabled+decompenabled显式true才有第三factory；默认off精确旧2；samequalifiedmodeladapter与freshJAR证据。
- End state: exact7paths typed/hostGREEN、final20classcleanverify、freshJAR/YAML双false/25SQL/17MC/10Rules均有实际记录，Root一次commit后serviceclean。
- Test-first gate: Required — G14 missing5arg/YAML、G15新map绑定、G16 absenttype、G16B typedtree必须为JUnitF，不是compile/env/fixture。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001 — 独立closure见下表。
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — Rule10无新time，仍核不变；Rule11Applicable。
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java`

- Purpose: 先锁fifthctor/flag/map绑定与默认资源缺口，再验证实际namedconsumer。
- Symbols: decomposition_constructor_is_present; decomposition_flag_defaults_off; enabled_decomposition_contributes_exact_three; native_off_decomposition_on_still_no_map
- Repository evidence: 现9 configurationtests检查四字段/YAML/exacttwo与qualifiedconsumer；无provider调用。
- Dependencies and consumers: Step1 commit；旧Properties/config/yml；File2第五构造/File5 map/File6 key。
- Why now: 先以getDeclaredConstructor/assertDoesNotThrow锁新增5arg presence RED；新key resource RED仍留至File6。
- Contract/signature changes: 旧4argctor/default exacttwo测试保留；new5arg配置binding由真实ApplicationContextRunner验证。
- Input/output and state mapping: nativefalse无map/props；nativetrue+decompmissing/falseexacttwo；bothtrueexactthreeimmutable且same consumer map。
- Error and edge behavior: unknownkey/missingmodel/qualifier/options/adapter/callback defaults继续fail；无runtime/provider自动启动；constructor选择不能猜。
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 11 — actualBean/Qualifier/strictbinder与defaultflag，旧ABI保持；无provider。
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> NativeAgentTemplateProperties.class.getDeclaredConstructor(boolean,String,String,String,boolean)) // G14 missing ctor F
assertEquals(Boolean.FALSE, loadedYaml.getProperty(prefix+"decomposition-enabled")) // missing resource F
// After File2 A typed tests:
props4 = new NativeAgentTemplateProperties(true,MODEL_BEAN,MODEL,ROUTE); assertFalse(props4.decompositionEnabled())
ApplicationContextRunner exact existing qualified map/adapter:
  flags false/missing -> exact old two; both true -> exact three including bounded; native false -> no map or props
assert binding chooses annotated 5arg canonical and rejects unknown metadata; verify model.call/stream never
retain all existing two-factory/unknownkey/callback/qualifier tests
```

- Verification contribution: G14 presence/resource RED、G15 typedConfig RED、G17 host GREEN；TEST-947/TEST-948。
- After this file: 缺fifthctor/flag已证F；source仍old AS直到File2开始，不能把startup exception当无包装Error。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java`

- Purpose: 追加defaultfalse fifthboolean并保持public4arg binary descriptor及可靠绑定。
- Symbols: record components(enabled,modelBeanName,modelName,modelRouteRef,decompositionEnabled); 5arg canonical compact @ConstructorBinding; public4arg delegating ctor
- Repository evidence: 现strict@ConfigurationProperties四字段record；Boot4.1.2 DefaultBindConstructorProvider在第二nonprivatector返回null，无recordcanonicalfallback。
- Dependencies and consumers: File1 G14；现Boot绑定annotation；新Factory/config消费者。
- Why now: 先新canonical+兼容ctor A，File1 typed binding RED后确认最小B；YAML/map尚未加入，相关测试继续F。
- Contract/signature changes: import org.springframework.boot.context.properties.bind.ConstructorBinding；5arg canonical显式注解；4arg overload无该注解/无@Autowired，this(...,false)。
- Input/output and state mapping: missingdecomp boolean=false；旧enabled/metadata guard和不trim逻辑完全保持；disabled时仍由配置条件不绑定。
- Error and edge behavior: 不使用@Autowired破constructorbinding；不改旧4arg签名/行为；unknownkey仍strict；不新增Lombok/业务logger。
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 11 — immutableconfigurationrecord、strictbinder、必要ConstructorBinding非businessDI；保持metadata。
- Implementation pseudocode:

```java
record NativeAgentTemplateProperties(boolean enabled,String modelBeanName,String modelName,String modelRouteRef,boolean decompositionEnabled)
@ConstructorBinding public NativeAgentTemplateProperties { keep exact current enabled/model metadata validation }
public NativeAgentTemplateProperties(boolean enabled,String bean,String name,String route) {
  this(enabled,bean,name,route,false)
}
keep @ConfigurationProperties(prefix=existing,ignoreUnknownFields=false)
no new @Autowired/Lombok/business logger; typed binding tests prove 5arg choice and old descriptor existence
```

- Verification contribution: G15 typedconstructor/property tests；source/javap aftercompile verifies bothpublicctors and bindingannotation；actualcontext tests mandatory。
- After this file: newhostvalueavailable，old4argsource/binary兼容；第三factory/map尚未注册。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactoryTest.java`

- Purpose: 在factory full实现前定义exact tree/model/instructions与无provider事实。
- Symbols: bounded_factory_type_is_present; bounded_factory_metadata_and_tree_are_exact; bounded_factory_requires_enabled_flags_and_exact_model
- Repository evidence: 现ConditionalResearchAgentFactoryTest/SupervisorResearchAgentFactoryTest使用actualLlmAgent/adapter及mockhostmodel。
- Dependencies and consumers: Step1Compiler/Gate；File2 props；File4 factory A/B。
- Why now: 先Class.forName assertion RED；A后typed testspositive assertDoesNotThrow/NotNull，不能NPE算RED。
- Contract/signature changes: publicctor(props,ChatModel,SpringAiModelAdapterFactory)；templateSpec/apply/usesChatModel沿现Strategy。
- Input/output and state mapping: new1/2exacttuple、fresh3nodes/sameexplicitBaseLlm、unique outputkeys/flags/root/childinstruction、0ordinarytools。
- Error and edge behavior: nullscheduler/flagsfalse/metadatawrong拒绝；same-namewrongmodelidentity不算匹配；构造/apply不发providerrequest。
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 11 — 技术Factory/Strategy值构造，immutabletuple与实际SDK树，无新业务Bean。
- Implementation pseudocode:

```java
assertDoesNotThrow(Class.forName(exact BoundedResearchAgentFactory)) // G16 missing type F
// File4 A then typed G16B:
factory = assertDoesNotThrow(() -> new BoundedResearchAgentFactory(enabledDecompProps,model,adapter))
spec = assertDoesNotThrow(factory::templateSpec); assertNotNull(spec); assert exact tuple 1/2/depth2/max16
root = assertDoesNotThrow(() -> factory.apply(Schedulers.trampoline())); assert exact tree/flags/unique keys
assert same explicit BaseLlm reference through each LlmAgent.model().flatMap(Model::model)
assert instruction fixed plan/zero-text route/untrusted current question/same-response return; no raw Session key authority
verify mock ChatModel.call/stream never; second apply returns fresh Agent tree
```

- Verification contribution: G16/G16B RED→factory GREEN；TEST-946/TEST-948。
- After this file: 类型及typed行为缺失F已锁，允许File4 B；现两个factorytests不改继续回归。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactory.java`

- Purpose: 实现第三受信factory，复用同qualifiedhostmodel和existingadapter。
- Symbols: implements NativeAgentCompilerStrategy; ctor(NativeAgentTemplateProperties,ChatModel,SpringAiModelAdapterFactory); apply(Scheduler); templateSpec(); usesChatModel(ChatModel)
- Repository evidence: 现两factory是finaltechnicalclass+final字段/显式值ctor；adapter仅create(ChatModel,String)无Scheduler overload。
- Dependencies and consumers: File3反射RED→本File A→File3 typedRED→本FileB；Step1exacttuple。
- Why now: 类型A先可编译但安全拒绝，typedRED之后才写exactmetadata和tree；不更改旧两factory。
- Contract/signature changes: 需properties.enabled && decompositionEnabled；无新Bean stereotype/DI层；只配置创建此值。
- Input/output and state mapping: TemplateSpec exactbounded1/2；apply一次adapter.create得到sameBaseLlm树；每次freshagents、roottrue/truechildrenfalse/true。
- Error and edge behavior: scheduler/hostmetadata为空安全拒绝；不自动provider/runtime/ordinarytools；rootinstruction约束首plan/zero-text routes/final，child currentdata+summaryreturn。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 11 — 沿Root批准technicalFactory finalfields/显式值构造，不加Lombok/businesslogger；existingStrategy。
- Implementation pseudocode:

```java
// A signatures implement Function-compatible Strategy; templateSpec/apply fail closed; usesChatModel false.
// Root records File3 typed G16B RED, then B:
store final existing ChatModel/adapter and immutable exact bounded TemplateSpec
usesChatModel(candidate) -> candidate == original host model
apply(nonnull Scheduler): model = adapter.create(chatModel,templateSpec.modelName())
create bounded_researcher/bounded_critic sharing model, no tools/subagents, parent false/peers true, fixed outputkeys
create bounded_supervisor sharing model with exactly those children, root parent/peer true, fixed key
instructions reflect only approved strictplan/currentquestion/same-response summary+return/zero-text route/final rules
return fresh tree; never call ChatModel.call/stream during construction or apply
```

- Verification contribution: G16B→factory typed GREEN；G17wholehost；actualtreeidentity/metadata/instruction验证。
- After this file: factory完整可用但仍未namedmap注册，File5/6闭合默认flag接线。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java`

- Purpose: 将第三factory只在两flag都true时贡献到同一namedimmutablemap。
- Symbols: tianjiAgentCodeFirstFactories(NativeAgentTemplateProperties,@Qualifier("agentFlowChatModels") Map<String,ChatModel>,@Qualifier("springAiModelAdapterFactory") SpringAiModelAdapterFactory)
- Repository evidence: 现@Configuration conditional nativeenabled，@Bean(name="tianjiAgentCodeFirstFactories")与两Qualifier/modeloptions guards。
- Dependencies and consumers: File1 configRED/File2properties/File4factoryGREEN；现AR consumes same Functionmap。
- Why now: 实现已存在后才接namedconsumer；原guard/conditional不动。
- Contract/signature changes: missing/false decomposition精确旧Map.of两项；true同immutableMap加bounded；publicmap类型/Bean名/qualifiers保持。
- Input/output and state mapping: sameexisting ChatModel/adapter引用传入第三Factory；不新建modelclient/registry，不自动runtimeenable。
- Error and edge behavior: 缺qualifiedmap/adapter、modeloptions mismatch、rawcallbacks继续failclosed；nativefalse由classconditional无map/props。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11 — existingexplicit@Bean/qualifier方法注入，技术例外不引新businessDI/Lombok；simpleconfigbranch选既有Strategy。
- Implementation pseudocode:

```java
keep existing configuration annotations and model/options/rawcallback validation exactly
if(!properties.decompositionEnabled()) return existing exact two Map.of entries
return Map.of(existing conditional entry, existing supervisor entry,
 "bounded-research-v1",new BoundedResearchAgentFactory(properties,model,adapter))
keep @Bean name and exact @Qualifier method parameters; no field injection/second registry
ApplicationContextRunner consumer must observe same immutable map instance, never a copied alternate map
```

- Verification contribution: G17 beforeYAMLmay留resource assertionF；所有map/model/DItests绿色，旧4argdefaulttwoexact。
- After this file: hostbothflag运行接线正确，默认资源仍等File6 falsekey。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: 在唯一AS base profile加入第五个明确defaultfalsekey。
- Symbols: stellar.tianji.agent.native-templates.decomposition-enabled: false
- Repository evidence: AS仅此application.yml；现四key enabledfalse、empty模型metadata；现默认shell不开client/DataSource/server。
- Dependencies and consumers: File1 resource RED；File2typedproperties/File5map；实际ASjar后续proof。
- Why now: 源码能力和typed接线可用后最后写defaultoff，完成resourceGREEN。
- Contract/signature changes: 仅追加fifthkey，不改existingfour、runtime.enabled、provider/modelmetadata/env。
- Input/output and state mapping: missingfalse兼容；source与packagedYAML必须byteidentical，双false不能被打包默认值替换。
- Error and edge behavior: 不得开runtime/provider、凭证或新profile；如果出现新ASprofile漂移先回Root，不擅自加环境文件。
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001。
- Literal rule enforcement: Rule 7, Rule 11 — sole profile key parity、typedstrictproperties、保留所有旧默认值与技术模块。
- Implementation pseudocode:

```yaml
stellar.tianji.agent.native-templates:
  enabled: false
  model-bean-name: ""
  model-name: ""
  model-route-ref: ""
  decomposition-enabled: false
# Actual file nesting preserves existing YAML indentation; only last key added.
# No credential/provider/default model or runtime toggle change.
```

- Verification contribution: G17host GREEN；G18clean verify+实际JAR application.yml cmp/falsekey proof；TEST-948。
- After this file: 第三factory defaultoff交付，olddefaultmap2不变；须G18freshJAR和最终sourceaudit才Step2commit。

#### File 7 — `CREATE docs/egon/evidence/2026-11-05-bounded-task-templates.md`

- Purpose: 收口两Step源码符合性、真实finite clean/JAR/旧SQL与未执行验收。
- Symbols: actualStep2RED/typedRED/GREEN; finalcleanverify/181baseplusnewtests; AR/ASJAR/YAMLsha; 17MC/10Rules; REQ931-947 conformance
- Repository evidence: 现native templates EVD记录181clean、25immutableSQL与actualJAR proof；当前新EVD不存在。
- Dependencies and consumers: S1commit与S2files1-6/G18；Rootworkspace进度更新单独不入servicecommit。
- Why now: 最终cleanverify与artifact/staticaudit均成功后才能写实际完成；一逻辑提交，不追加空commit。
- Contract/signature changes: 分清sourceimplemented与S03/provider/IAM/PG RuntimeUnverified、wholeGoal/T3-10/T4Required；不把Graph作T3-09源码额外前置。
- Input/output and state mapping: exact20classselector报告及所有owningSuite 0F/E/S；fresh classes/YAML双false与sha；25SQL逐path旧hash不变。
- Error and edge behavior: failure/skip/缺报告/旧JAR不能PASS；不把原181当本轮新结果；记录实际数而非预填。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001；EVD必须逐项17MC/10Rule，不能合并省略。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — 每条独立证据，尤其Rule11 Applicable与现technicalprofile。
- Implementation pseudocode:

```text
after G17, Root runs exact G18 clean finite verify; inspect all named Surefire reports and 181 baseline coverage plus new tests
inspect fresh AR/AS JAR entries/new bounded classes, both public Properties constructors and @ConstructorBinding
unzip packaged YAML -> compare source bytes -> assert native.enabled=false and decomposition-enabled=false; record artifact SHA256
compare exact 25 old SQL path hashes and excluded tracked files; aggregate diff exactly22 service paths
record REQ931-947, each 17 MC and each ten Rule row independently with actual evidence
state T3-09 present source coverage audit vs actual workspace master; S03/provider/IAM/PG/overallGoal not falsely accepted
Root stages exact7 Step2 paths, one semanticcommit; afterward updates workspace master information only, outside serviceGit
```

- Verification contribution: G18/G19-finalaudit；strictdocs/source/JAR/MC/rules；RootperStepcommit。
- After this file: Step2源码交付可供用户审查，serviceclean；master仅Root信息更新；不启动任何运行环境。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: §8 G14-19及finalcleanverify的逐字命令；Root独占。
- Expected result: 所有named owningreports存在，GREEN为0F/E/S；RED仅指定缺行为JUnitF，strict/paths/旧SQL均通过。
- Failure returns to: 对应File N；API/设计漂移回Root/Spec，环境问题先修复且不得记RED。
- Completion criteria: 本Step下表17MC与10Rule逐项闭合，strict/GREEN与exactpaths证据完整。
- Rollback: review/source层只回退本Step提交的声明paths；无数据迁移；不得rewritesharedhistory或回退他人改动。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java`; `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java`; `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactoryTest.java`; `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactory.java`; `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java`; `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`; `docs/egon/evidence/2026-11-05-bounded-task-templates.md`

```text
stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java
stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactoryTest.java
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/BoundedResearchAgentFactory.java
stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java
stellar-enclosure-tianji-agent-server/src/main/resources/application.yml
docs/egon/evidence/2026-11-05-bounded-task-templates.md
```

- Commit: `feat(agent): register default-off bounded research template`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | §4.7现technicalprofile/declaredpaths，无新layer；Rule11核对；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-REUSE-001 | Applicable | reuseledger+实际imports/adapter/currentCAS；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-DEP-001 | Applicable | 所有POM/依赖/25SQL hashes baseline相同；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-NAME-001 | Applicable | VO/Factory/Properties/Gate/Tests语义命名与filetree；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-VALID-001 | Applicable | 本Step所有JSON/SDK/hosthandoff正反测试与先验时序；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-MODEL-001 | Applicable | immutable VO/configcompact构造、防御copy、无普通实体record；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-CONVERT-001 | Not applicable | 同包技术投影/配置值，无新跨层Converter；不造Mapper；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-LOG-001 | Not applicable | 无新businesslogger/Service；现safePlugin日志保持，rawplan/sentinel不echo；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-BEAN-001 | Applicable | ASexplicitBean/qualifiedmap/绑定ctor与4argdescriptor测试；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-UTIL-001 | Applicable | JDK/现Jackson/SDK，no utilitydependency；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-JSON-001 | Applicable | TemplateSpec metadata/旧wire不变，finalcodec回归；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-TIME-001 | Not applicable | 无time symbol变化，最终回归核现边界；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-CONFIG-001 | Applicable | soleYAML第五falsekey+typedbinder+actualpackagedcmp；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-PATTERN-001 | Applicable | 同Strategy新增factory/samequalifiedmap，无第二registry；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-SCOPE-001 | Applicable | exact7paths+excludedtracked/25SQL审计，无unrelatedstage；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-TEST-001 | Applicable | 本StepactualRED/A/typedRED/B/GREEN、0E/0S与namedreports；执行EVD记录实际PASS或证据N/A，任何失败不commit |
| MC-BLOCKER-001 | Applicable | 所有适用项PASS；N/A有本行证据；Root sourceGO与Stepcommit门禁；执行EVD记录实际PASS或证据N/A，任何失败不commit |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | 语义角色/文件命名清单；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 2 | Applicable | 所有实际SDK/JSON/host边界negative和时序；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 3 | Applicable | immutable记录/防御copy/同包Map投影；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 4 | Applicable | 保留现technical构造/LoggerFactory技术logger；AS修改时explicitBean/qualifiers/ConstructorBinding；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 5 | Applicable | imports/POM基线，只有JDK/现Jackson/SDK/adapter；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 6 | Applicable | closedJSON/audit/result/metadata合同，禁止ordinal持久化；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 7 | Applicable | 唯一ASprofilefalsekey/actualJAR，未改Step给excludedhash；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 9 | Applicable | 实际CompilerStrategy+State或Factory注册测试；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 10 | Not applicable | 现java.time/UTC/currentclock边界，未改Step明确N/A；逐条EVD记PASS或本项有据N/A，不合并 |
| Rule 11 | Applicable | 现technicalprofile/依赖方向/Components复用，Applicable；逐条EVD记PASS或本项有据N/A，不合并 |

## 8. Test, Validation, and Quality Gates

所有Maven命令只供Root将来获sourceGO后执行，本轮未运行。统一base command如下，表内selector逐字替换为该行值；无wildcard、PG/网络/服务选择。

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml \
  -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipITs=true -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=NativeTaskPlanVOTest
```

上面是G1的完整命令；下表的base表示同一固定选项，以该行唯一明确的`-Dtest=...`替换示例末尾selector，不移除selector或叠加多个选择器。source-reactor最后追加test目标。named owningreport必须存在且不skip；无selectedtest的非ownerreactormodule才可使用surefire.failIfNoSpecifiedTests=false。missingJDK/settings/cache/source、compile/fixture/timeout均不计RED。

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1 presence RED | service root | base `-Dtest=NativeTaskPlanVOTest`，S1F1反射版 | 缺TaskPlan类 | JUnit F>0/E0/S0，missing-type assertion | S1F1 | REQ-932；JVM only |
| G2 typed RED | same | base `-Dtest=NativeTaskPlanVOTest`，S1F2A后typed版 | parser/construct/hash | 编译成功，缺行为assertF | S1F1/2A | REQ-932-934/942 |
| G3 parser GREEN | same | base `-Dtest=NativeTaskPlanVOTest` | strictparser/values | 0F/E/S | S1F1-2 | REQ-932-934/942 |
| G4 enum RED | same | base `-Dtest=NativeAgentCompilerStrategyTest`，未加enum | missingenum assertion | 仅expectedJUnitF | S1F3 | REQ-931 |
| G5 paired A/typed RED | same | base `-Dtest=NativeAgentCompilerStrategyTest,NativeTaskPlanVOTest`，F4A+F5A都已写 | typedtuple | compile通过，boundedpositive缺tupleF/E0 | S1F3-5A | REQ-931/REQ-945 |
| G6 compiler GREEN | same | base `-Dtest=NativeAgentCompilerStrategyTest,NativeTaskPlanVOTest`，F4B | exactnew/oldhash | 0F/E/S | S1F3-4 | REQ-931/REQ-945 |
| G7 Gate typed RED | same | base `-Dtest=NativeAgentGateTest`，GateA+新tests | taskFSM/hooks/candidate | 仅bounded缺行为F/E0，oldtests仍正常 | S1F5A/6 | REQ-932/REQ-935-942/946 |
| G8 Gate GREEN | same | base `-Dtest=NativeAgentGateTest,NativeTaskPlanVOTest,NativeAgentCompilerStrategyTest` | fullboundedGate/oldtwo | 0F/E/S | S1F1-6 | REQ-931-942/945/946 |
| G9 integration RED | same | base `-Dtest=AdkRunContextTest,NativeAgentTransferRunnerTest,NativeAgentGateTest`，F7/8已写F9未改 | question/partialhook | 缺question/partialhook的JUnitF/E0；complete knownusage先settle回归必须通过 | S1F7-9 | REQ-937-939/946 |
| G10 integration GREEN | same | same selector after F9 | actualSDK/currentquestion/completeusage-before-fatal | 0F/E/S，完整script耗尽 | S1F5/7-9 | REQ-935-939/946 |
| G11 projection RED | same | base `-Dtest=AdkNativeAgentCompletionTest,AgentJsonCodecTest`，F10/11已写F12未改 | newtype/tasks | Completion缺tasks/type断言F/E0；codec既有能力可先GREEN | S1F10-12 | REQ-940/REQ-941/REQ-945 |
| G12 core GREEN | same | base `-Dtest=NativeTaskPlanVOTest,NativeAgentCompilerStrategyTest,NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,TianjiAgentRuntimeAutoConfigurationTest,TianjiNativeAgentTemplateConfigurationTest` | S1affected+oldcore/defaulttwo | namedreports存在，0F/E/S | owningS1file | REQ-931-942/945-947 |
| G13 docs/commit | actualrepo paths afterGO | 两strictvalidators+gitdiff/path/hash审计 | S1docs15paths/25SQL | 0errors，Root一次semanticcommit | S1F13-15 | allS1/无runtime |
| G14 host presence/resource RED | service root | base `-Dtest=TianjiNativeAgentTemplateConfigurationTest`，S2F1 | missing5arg/YAML | expectedJUnitF/E0 | S2F1 | REQ-944 |
| G15 typed host RED | same | same selector after PropertiesA | typedflagbinding/map | 编译通过；missingthirdmap/resource预期F/E0 | S2F1/2 | REQ-943/REQ-944 |
| G16 factory presence RED | same | base `-Dtest=BoundedResearchAgentFactoryTest`，反射type版 | 缺factory | missingtype assertionF/E0 | S2F3 | REQ-943 |
| G16B typed factory RED | same | same selector after FactoryA | exacttree/tuple/identity | 缺factory行为assertF/E0 | S2F3/4A | REQ-931/REQ-943 |
| G17 host GREEN | same | base `-Dtest=BoundedResearchAgentFactoryTest,TianjiNativeAgentTemplateConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,NativeAgentCompilerStrategyTest,TianjiAgentServerApplicationTest` afterF4B/F5/F6 | thirdhost/defaulttwo/ABI | 0F/E/S，无model.call/stream | S2F1-6 | REQ-931/REQ-943-945/947 |
| G18 final clean finite | same | 下方exact20classcleanverify | 181基座+新增类/methods | exit0，namedreports齐全、0F/E/S，实际新总数记录不预填 | owningStep | all；仅finite/package |
| G19 artifacts/audit/commit | same | 下方JAR/YAML/SHA/diffchecks与两strict | actualfreshAR/AS/25SQL/exact7+22aggregate | 通过后Root一次S2commit/serviceclean | S2F7/owningfile | all；非provider/PG验收 |
| Manual/runtime | 用户自己的环境 | 不由本Plan执行；另行实际S03/provider/IAM/PG验收 | realdeployment | RuntimeUnverified/ProductionAccepted=false保持直到实际执行 | 后续验收任务 | wholeGoal/T3-10/T4Required |

最终G18精确命令继承前置181clean的18-class selector并只新增NativeTaskPlanVOTest和BoundedResearchAgentFactoryTest（已有类中的新增methods自动包含）：

```bash
STELLAR_VERIFY_GOAL=verify \
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o clean -B -ntp -DskipITs=true -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,TianjiAgentRuntimeAutoConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,TianjiNativeAgentTemplateConfigurationTest,TianjiAgentServerApplicationTest,AgentFlowAdapterRegressionTest,NativeTaskPlanVOTest,BoundedResearchAgentFactoryTest
```

当前181为baseline evidence，不是本轮实际数；最终须保留全部基座case且新增tests真实执行，报告实际总数与所有suite的0F/E/S。禁止`*PostgresTest`、HttpProcessClientTest、App/listener/provider/network或wildcard。TianjiAgentServerApplicationTest是既有ApplicationContextRunner default-shell断言测试，不是启动SpringBoot服务。

freshartifact检查（Root在G18成功后执行）：

```bash
test -f stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar
test -f stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar
jar tf stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar \
  | rg 'top/egon/cola/platform/tianji/agent/infrastructure/adk/(NativeTaskPlanVO|NativeAgentCompilerStrategy|NativeAgentGate|AdkRunBudgetPlugin|AdkAgentRunExecutor)\.class$'
jar tf stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar \
  | rg 'top/egon/cola/platform/tianji/agent/server/agenttemplate/(BoundedResearchAgentFactory|NativeAgentTemplateProperties|ConditionalResearchAgentFactory|SupervisorResearchAgentFactory|TianjiNativeAgentTemplateConfiguration)\.class$'
javap -p -classpath stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar \
  top.egon.cola.platform.tianji.agent.server.agenttemplate.NativeAgentTemplateProperties
unzip -p stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar application.yml \
  > /tmp/tianji-bounded-packaged-application.yml
cmp stellar-enclosure-tianji-agent-server/src/main/resources/application.yml /tmp/tianji-bounded-packaged-application.yml
rg -U 'native-templates:\n[[:space:]]+enabled: false\n[[:space:]]+model-bean-name: ""\n[[:space:]]+model-name: ""\n[[:space:]]+model-route-ref: ""\n[[:space:]]+decomposition-enabled: false' /tmp/tianji-bounded-packaged-application.yml
shasum -a 256 stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar \
  stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar /tmp/tianji-bounded-packaged-application.yml
```

`jar tf|rg`输出必须逐项核齐预期5+5classes，不是只看到其中一个就PASS；Properties必须见public4arg和5arg，typedbindingtest证明canonicalannotation，不用javap猜运行。JAR/YAML/SHA是actualartifact证据，不是安装或真实AgentRun。

执行开始前和每步commit前，Root用现git/SHA工具记录并比较25SQL；以下仅在sourceGO后作为执行门禁，绝不改SQL：

```bash
git ls-files '*src/main/resources/db/*.sql' > /tmp/tianji-bounded-sql-paths.txt
test "$(wc -l < /tmp/tianji-bounded-sql-paths.txt | tr -d ' ')" -eq 25
while IFS= read -r task_sql_path; do
  shasum -a 256 "$task_sql_path"
done < /tmp/tianji-bounded-sql-paths.txt > /tmp/tianji-bounded-sql-current.sha256
```

首次保存baseline清单/hash；后续每次输出独立current文件并`diff -u baseline current`为零差异；路径也须相同25条。所有其它excludedtrackedfiles同样基于baseline gitblob/SHA核对，只有当前Step允许路径能改变。Root执行`git diff --check`、`git diff --name-only --cached`并逐字对比Step Commit paths；不得stage build/tmp/private/unrelated文件。静态检查changedlines不引入Date/Calendar/SimpleDateFormat、Gson/Fastjson/BeanUtils映射、AgentTool/Graph/第二Runner、provider/App/PG选择器或新permission。

所有gate已有JUnit/strictvalidators/source-reactor/git/rg/JAR/SHA工具覆盖，不新增scratchharness，不留scripts/work待定项；若实施发现现工具不能证明新gate，先回Root，不自行扩大scope。

/tmp draft在eventual-path镜像中验证，未来获sourceGO后在真实路径重跑：

```bash
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-08-47-bounded-research-task-decomposition.md --strict
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-09-44-bounded-research-task-decomposition.md --strict
```

本轮写作只执行资源preflight和镜像文档strict；不执行上述Maven/JAR/运行gate。实际日志/结果留给执行EVD。

## 9. Migration, Compatibility, Rollout, and Rollback

N/A迁移/代码生成：Primary§8/11不增schema/CRUD/模块，25oldSQL全部immutable。没有版本/manifest/POM/依赖/C/CL/API/permission变化。

| Boundary | Preserved / additive behavior | Proof |
| --- | --- | --- |
| oldtwo metadata/hash | 原schema1/1、hashdomain/fieldorder/codeRefs不变 | frozenhash/Compiler/Gate/Runner |
| Function/constructors | sameFunctionmap、Plugin/Executorctor不改，Props4argdescriptor仍在 | compile/context/javap |
| hostdefault | nativefalse无map，true+missing/false精确旧2，bothtrue3 | typedbinding/map/YAML |
| result/event | newdefinition1/2才tasks/newtype；old1仅agents/text/fourkeys；codecprod不变 | Completion/CodecTest |
| budget/lifecycle | sameRunner/run/MODEL/currentCAS/总配额/timeout/cancel | Context/Runner/Completion |

rollout先S1commit再S2commit；binary交付保持双false，host用户自行选existingmodel/budgetprofile/immutabledefinition后显式enable，不自动启动runtime/provider。rollback可关闭decomposition停止新factory贡献；原两保持。排队/已运行bounded缺factory沿现安全失败，无平滑resume承诺；终态result/event仍generic可读。源码回退按各semanticcommit路径，禁止rewrite共享历史/修改SQL。Graph/T4为独立后续，不作为T3-09本轮源码额外门槛。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-931 | Primary §4/7/9/10/14-16 | Steps 1-2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-932 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-933 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-934 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-935 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-936 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-937 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-938 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-939 | Primary §4/7/9/10/14-16 | Steps 1-2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-940 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-941 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-942 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-943 | Primary §4/7/9/10/14-16 | Step 2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-944 | Primary §4/7/9/10/14-16 | Step 2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-945 | Primary §4/7/9/10/14-16 | Steps 1-2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-946 | Primary §4/7/9/10/14-16 | Step 1 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-947 | Primary §4/7/9/10/14-16 | Steps 1-2 | §5 inventory及§7对应exactFiles | Primary TEST-931-949中对应行；§8 exact G1-19 | owning EVD actual F/E/S、scope与源码conformance；不声称runtime |
| REQ-901, REQ-902, REQ-903, REQ-904, REQ-905, REQ-906, REQ-907, REQ-908, REQ-909, REQ-910, REQ-911, REQ-912, REQ-913, REQ-914, REQ-915, REQ-916, REQ-917, REQ-918, REQ-919, REQ-920 | Native Spec §4/6-7/9-10/14-16；Primary局部amend | Step 1 preservation + Step 2 final regression | Existing compiler/Gate/Plugin/Runner/Completion/modelbudget/factories；只改§5批准paths | 原181selector全部保留+G12/G18；REQ-920按Primary当前source边界解释 | 已有行为回归，非重做前置；真实S03/整个Goal未验收 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| RISK-931 | enum新增导致Gate switch编译依赖 | S1F4-5A | 当前多个exhaustive switches | Root | Closedby paired compile，未补齐不跑Maven/不commit |
| RISK-932 | Properties新增publicctor使自动绑定不确定 | S2F2 | Boot4.1.2本地bytecode，Root认可 | Root | Closedby explicitcanonicalConstructorBinding+typedcontext |
| RISK-933 | SDK字段/semanticpayload漂移 | S1F5/7/8/9 | §6.4公共javap清单 | owningworker/Root | pin变动先回Root，当前API已核；真实provider未验 |
| RISK-934 | 每task窗/候选/replay误接 | S1Gate/Plugin/tests | Primaryone-shot/originalwindow合同 | Root | negative/currentSDK tests闭合，不引新replaytransport |
| RISK-935 | 误把0编译或先GREEN算RED | 两Step | 原生真实TDD纪律 | Root | 每门按namedJUnitF/E0记录，失败种类不符即停止B |
| RISK-936 | 最终cleanJAR或181基座遗漏 | S2F7 | exact20classselector/actualsourceartifact | Root | 逐report/class/key/SHA检查，不能复用旧产物 |
| RISK-937 | 源码完成误报真实S03/wholeGoal | finalEVD/master | Primary§18/20 | Root | T3-09source按master审计；provider/IAM/PG与overall/T3-10/T4仍Required |
| DEC-931 | workspace master归属 | S2后Root信息更新 | Root明确确认外层docs不在serviceGit | Root | Closed，不入两个commit清单 |
| DEC-932 | 最终Plan/source授权 | all | 本Plan仅Review，Root精确Spec接受仅用于Plan | Root | Plan待全读后才SourceGO；本轮不实施 |

没有未解决的设计/依赖blocker。真实运行风险保留RuntimeUnverified，不把它们伪装已PASS；本文内部完成不等授权。

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

REQ-931-947全部在Step Requirements/逐file/Gates/Trace出现；两个semanticcommits、单run治理、defaultoff与用户不自动启动运行环境要求均落到执行门禁。没有缩减明确task/context/frame/候选/DTO边界，也没有把Graph或T4加作本轮额外前置。

### 12.2 Spec consistency

只实现Primary批准类型/字段/状态/文件/限界；唯一Spec修改是RelatedPlans一行backlink。PLAN-CLAR-931-935只是公开SDK/编译依赖/窗口数值/必要构造绑定/文档归属机械细节，Root已认可，不引新禁令/框架。Spec simplicity审计无fetch-forward或codec生产过度扩展。

### 12.3 Repository executability

所有22paths/现方法executeScoped、两个Pluginhook点、Gateexhaustiveswitch、qualifiedMap、Bootconstructor选择与SDKgetter均有本地源码/bytecode依据。A/B回访与只在paired A后编译明确；Root单Maven/commit，sameStep文件互斥；S1提交后才S2，没有半编译commit。

### 12.4 Test and release completeness

G1-19规定每个RED/GREEN/回归/strict/SQLscope/JAR门；最终继承181finite基座加两新类及既有类新增methods。无PG/App/provider/wildcard选择、无新scratchharness、无运行验收冒充。正常metadata与partial/turnComplete语义没有被机械误读成额外禁令。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/Rule11/22paths | 继承Roottechnicalprofile，无新层 | 沿Primary已批准technicalboundary |
| MC-REUSE-001 | Applicable | PASS | §4.5/4.7reuseledger/逐file | adapter/Gate/budget/codec/digest都复用 | None |
| MC-DEP-001 | Applicable | PASS | §5/6/25SQLscope | 无新POM/dependency/codegen | None |
| MC-NAME-001 | Applicable | PASS | §5/逐filefinaltypes | VO/Factory/Properties/State角色明确 | None |
| MC-VALID-001 | Applicable | PASS | Parser/Gate/Plugin/AS负例/时序 | 所有实际handoff先校验，禁止normalize | 沿PrimarytechnicalSDK边界例外 |
| MC-MODEL-001 | Applicable | PASS | VO/config构造与List/Mapdefensivecopy | 仅immutable值record，无普通Entity | None |
| MC-CONVERT-001 | Not applicable | N/A | S1F11/12同包JSON-nativeprojection | 没有新跨层Converter/Mapper | None |
| MC-LOG-001 | Not applicable | N/A | 无新businessService/logger，现Plugin安全边界 | 不加rawmodel/question/secret日志 | None |
| MC-BEAN-001 | Applicable | PASS | S2F1/2/4/5/G15/17 | explicitBean/qualifiedmap/canonicalbinding与4arg兼容 | technicalfactory值构造沿现style |
| MC-UTIL-001 | Applicable | PASS | imports/SDK/JsonNodepublic清单 | JDK/现Jackson/SDK，无Utils库 | None |
| MC-JSON-001 | Applicable | PASS | S1parser/audit/DTO-first/codecTest | closedJSON/6fieldpayload/3keymap，codecprod不动 | None |
| MC-TIME-001 | Applicable | PASS | TIME-931/G12/18 | 现Instant/UTC/DBclock保持，无新转换 | PGroundtrip本轮未执行 |
| MC-CONFIG-001 | Applicable | PASS | S2props/config/YAML/actualJAR | soleprofilefifthfalsekey，oldfour保持 | None |
| MC-PATTERN-001 | Applicable | PASS | §4.7/S1GateState/S2Strategy | Complex使用现Strategy+boundedState，不新framework | None |
| MC-SCOPE-001 | Applicable | PASS | 22uniquepaths/exact15+7/25SQL | 两个commit明确，无repo/sourceGO本轮 | workspace master仅Root信息更新 |
| MC-TEST-001 | Applicable | PASS | §8G1-19/逐fileproof | 所有RED必须JUnitF，finite/JARgate完整 | futurevalidation非已执行 |
| MC-BLOCKER-001 | Applicable | PASS | §11/全部rows | 无设计阻塞，Plan待Root审查 | 不代表SourceGO |

### 12.6 Final verdict

`PASS — Ready for user review`

Plan为Review；只有Root读完整Plan及tinySpecbacklinkdelta并明确SourceGO后才实施。本轮没有Maven、repo/source写入、服务或数据库执行。

## Java / CQE contract review

- immutableVO/config/state与internalenum明确；不引普通实体record/持久ordinal。
- 现Componentsmodeladapter/currentledger/budget/codec/Jackson复用，无MP/DDL新能力需求。
- 所有SDK/JSON/host边界按Primary技术例外严格验证；无新publicvalidation层/DTO。
- 没有新的businessunique/soft-delete/table/migration；25SQL保持。
- 现Commands/Queries不变，authorizationevent继续existingdurableledgerCAS，不增加本地Eventbus或替代Outbox。
- inheritedRoottechnicalprofile保持；每StepRule11Applicable、everyfile可追踪。

## Backend generation and dependency evidence

生成器不运行：没有新module/CRUDcatalog/DDL/manifest/POM/dependency。两个新productiontypes是已批准技术VO/Factory而非catalog输出。现构造/annotation/SDK/Jackson依赖已具备；无未批准依赖或工具缺口；无classpathsqjournal义务，因为没有codegen/apply或计划中的generator动作。
