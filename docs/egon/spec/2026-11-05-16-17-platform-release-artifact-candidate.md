# T4-01A1 离线发布候选制品校验器

| Field | Value |
| --- | --- |
| Document | `2026-11-05-16-17-platform-release-artifact-candidate.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Feature` |
| Complexity | `Simple` |
| Complexity Drivers | `单一离线 CLI，有限文件读取及确定性输出；无运行服务、跨所有者写入或状态变更` |
| Created | `2026-11-05 16:17 Asia/Shanghai` |
| Updated | `2026-11-05 16:29 Asia/Shanghai` |
| Owner | `天机发布治理决策方` |
| Repository | `tianji-service` |
| Scope | `stellar-enclosure-tianji-process-server 现有 release 包，复用 C 和现有打包依赖` |
| Change Surface | `离线 CLI、文件/模型/打包清单校验、复用 Process 摘要、可复算候选 JSON 和有限测试` |
| Affected Chapters | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Source Requirement | `主计划 §19 T4-01 的制品检查部分，已锁定的 T3-12 源码交付是 A1 先决条件` |
| Baseline Revision | `tianji-service 92bac51aa8d131c9143dcc23e11c28e49fca4731` and `tianji-web f558e02c8d07da2da758c5273a5eb531ca251018`; T3-12 source committed, live E unexecuted |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [T3-12 四路径验收](2026-11-05-15-13-expense-four-path-acceptance.md#16-compatibility-migration-rollout-and-rollback) |
| Related Specs | `None` |
| Related Plans | [A1 implementation](../plan/2026-11-05-16-33-platform-release-artifact-candidate.md), [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

交付一个可由发布操作者直接运行的离线 CLI。给定同一份审阅过的描述文件与本地文件集合，CLI 核对 Agent 定义和契约源证据、Process 模型和现有 release 摘要、AS/PS 打包的权限清单，向 stdout 输出可复算的不可变候选 JSON。它检查的是本地字节、闭合结构和已可验证的关联。相同语义输入的候选摘要相同。

本阶段只完成 `ARTIFACT_CHECKED_ONLY`。包签名权威、当前 owner 发布/撤销、审核/CAS 与运行时仍由 T4-01 后续阶段解决。A1 源码依赖锁定的 T3-12 源码交付，并需本 Spec 及其 Plan 批准。用户执行的 T3-12 E 仍是后续 live T4 治理、激活和迁移的独立前置。

## 2. Background and Current State

### 2.1 Business context

发布者需要在接入治理前快速检查两个所有者的文件是否形成一个可审查的组合，避免在签发或激活前发现模型、契约或包内权限清单漂移。此处的输入是操作者已经准备的本地文件，无需读当前平台。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact source | Observed fact and limit |
| --- | --- | --- | --- |
| EVD-001 | Static repository | PS `release/ProcessReleaseService#prepare,manifestHash`、`ProcessModelValidator`、`SafeModelXml` | 发布摘要基于按 type/key 排序的验证模型字节与有效输入类型。现有模型校验是可直接复用的纯逻辑。实际发布还需要 engine/Worker/Case readiness |
| EVD-002 | Static repository | C `AgentDefinitionSpecDTO`、`DefinitionDependenciesDTO`、`AgentInputDTO`、`AgentExecutionResult`；AR `AgentJsonCodec#definitionHash` | Agent 定义为封闭载体，owner 内容 SHA 使用 AR 私有规范编码。PS 当前没有 AR 依赖，禁止复制其编码 |
| EVD-003 | Static repository | PS POM Boot `exec` classifier、`TianjiProcessServerApplication`；T3-12 Plan `PropertiesLauncher` | 同一 exec JAR 可通过 Boot PropertiesLauncher 显式选择另一个 main。旧 Start-Class 仍是服务入口 |
| EVD-004 | Static artifact | AS/PS `permission-producer`；Jianshen `PermissionManifestBuildCli`；exec `META-INF/permissions/manifest-v1.json` | G19 实测 PS producer profile 在 ZIP 根 META-INF，AS 同 profile 将在 A1 的独立包门禁核对；当前无 profile 的两个本地包没有该 entry。声明不证明注册、授予或审核 |
| EVD-005 | Static repository | Tianshu `DdcPromptClient#get`、`DdcSkillClient#get`；AS `TianshuContextProvider#read`；AR `ToolDescriptor#reference` 和 `ToolRegistry#visible` | 现有查询与目录绑定特定权限和运行上下文。A1 无会话或授权，不把描述引用升级为已发布或未撤销证明 |
| EVD-006 | Static repository | `ExpenseFixtureModelsTest`、三份 expense 模型、`expense-four-path-contract.json`、T3-12 现有测试工件 | 适合作为第一份可复算输入；模板/角色渲染/实际发布证据必须分别归类 |
| EVD-007 | Static repository | C `ProcessReleaseDTO`、`AgentDefinitionDTO`；`ProcessReleaseClient#get`、`AgentRunController` | PS 已有授权 release 读，AS 无对称公开 definition 发布读。裸 DTO 没有验证过的 tenant 身份 |

### 2.3 Gap

没有一个现有入口能够同时校验 Agent、Process、模型字节、契约证据和实际打包清单，并生成闭合、可重复摘要的候选。已有 owner 局部校验可以复用，继续保留原角色。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current chain | Scope |
| --- | --- | --- |
| PS publish | `ProcessController` → `ProcessReleaseService#prepare,manifestHash` → engine deployment | A1 复用纯校验和摘要，仅实际发布入口拥有写权 |
| AS publish/start | `AgentLedgerService#publish,start` → `AgentJsonCodec#definitionHash` 与快照 | A1 验证输入 DTO/原始字节，AR 摘要为声明 |
| PaC build | Maven profile → Jianshen CLI → actual AS/PS exec artifact | A1 读取已打包 entry 和原始摘要 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 现有 PS 技术模块新增独立离线入口，未创建新业务项目或引入样例域，既有生产资源和数据库脚本保持。

## 3. Goals and Non-goals

### 3.1 Goals

- 一个明确打包入口与无需凭据的本地操作合同。核对一组 pinned Agent + Process + 两个 PaC 包。
- 可重复生成或验证候选摘要，准确拒绝错误 scope、形状、模型、schema、路径、摘要和声明权限。
- 精确区分本地结构与外部 owner 事实。

### 3.2 Non-goals

签名/验签信任根、真实 owner 查询、权限注册或授予、接入发布审核/CAS、rollback/activation、生产 HTTP/SDK、运行新启动、BPMN/CMMN 迁移、数据库、角色和浏览器均不在 A1。CLI 不生成私钥、连接网络、发布、部署或保存任何状态。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapters |
| --- | --- | --- | --- | --- | --- |
| 离线 CLI 与封闭制品合同 | Affected | EVD-001/002/003/004 | 一次本地解析校验和确定性 stdout | 完整输入、结果、文件、失败及权限边界 | §7, §8, §9, §10, §13, §14, §15, §16, §17, §18 |
| PS 既有发布摘要 | Affected | EVD-001 | 纯摘要共用，既有旧 hash 完全保持 | 精确调用与兼容用例 | §7, §8, §14, §16 |
| Process/Agent 执行、SDK、数据库 | Unchanged | EVD-001/002/007 | 原 schema/入口/部署行为 | 无新 provider、Bean、migration | §11, §16 |
| IAM/天枢/玉衡 | Context-only | EVD-004/005/007 | 仅引用已有证据性质 | 关闭 live 假设，单列后续 | §15, §18 |
| Web | Unchanged | 当前无任何 A1 页面 | 无 UI 或浏览器 | 保持现有客户端 | §12 |

## 4. Requirements and Acceptance Criteria

| Requirement | Criterion |
| --- | --- |
| REQ-001 | 在现有 PS exec JAR 直接运行命名 CLI。只有成功返回完整的一行候选 JSON，失败返回非零和稳定错误，无网络或 App 连接 |
| REQ-002 | 严格绑定预期 tenant/channel，校验受限输入、原始文件 SHA、Agent 定义范围和现有契约源证据；拒绝凭据、外部权威和伪状态字段 |
| REQ-003 | 真实纯 BPMN/DMN/CMMN 与关系校验，校验一个声明的 Agent Dispatch 绑定，复算旧 Process manifest SHA，既有 publish/replay 的 digest 不变 |
| REQ-004 | 验证两份本地 Boot exec JAR 和各自明确 PaC entry、生产者、Start-Class 与主应用边界。没有包注册或部署结论 |
| REQ-005 | 相同事实跨输入重排、目录搬移后输出相同候选与摘要；输出无文件位置、原始 XML、配置、内容正文、时间或秘密 |
| REQ-006 | 真实签名/发布/权限/撤销证据均不由 A1 生成；T3-12 源码锁、用户执行 E 与另审门禁分别保留 |

### 4.2 Use-case analysis

| ID | Use case/goal | Actor | Trigger and precondition | Result/failure | Trace |
| --- | --- | --- | --- | --- | --- |
| UC-001 | 构建可审阅的候选 | ACTOR-001 已获本地制品访问权的发布操作者 | 审阅的输入、精确作用域和本地包齐备 | 获取可复算的候选或闭合失败，无远程操作 | REQ-001–006 |
| UC-002 | 复验候选不变 | ACTOR-002 审核/CI 操作者 | 具备同一制品和已记录候选摘要 | 精确重复输出，或摘要冲突且 stdout 为空 | REQ-001/002/005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision |
| --- | --- |
| DEC-001 | 首个支持一 Agent CONFIG 定义、零 Prompt/Skill/Tool、零工具预算和一 Process release。CLI 不把测试名称硬编码成行为 |
| DEC-002 | 现有 PS module 与 Boot PropertiesLauncher，纯本地 Java 25，复用 C 与已有 Jackson/Flowable 模型包。PS 不引入 AR、AS、CL 新依赖 |
| DEC-003 | 流程发布摘要共用纯助手，Agent owner SHA 仅声明；不复制 AR digest 代码 |
| DEC-004 | 只输出确定性的本地制品候选及摘要。输入租户/通道是预期内容作用域 |
| ASM-001 | 本地准备目录由同一操作者维护，运行中不改动。以私有 POSIX 目录和防 symlink 输入要求收敛，验签或不可信运行者单独评审 |
| OPEN-001 | T3-12 源码锁与本候选审批是 A1 源码前置。T3-12 E、后续签名、发布时当前 owner 读取、撤销/权限和审批 CAS 独立保留 |

## 6. Project Technology Context

PS 现有 JDK 25、Spring Boot 4.1.2 exec JAR、Jackson 3、Flowable 8.0.0 与 C 中立 DTO。只调用模型转换与既有校验，JDK 文件和 `JarFile`，无 Spring 容器调用。当前 AS/PS 打包 PaC 执行依赖由现有 Maven profile 提供。A1 无 ORM、MapStruct、注册 Bean、业务日志、profile、Flyway、事件中间件或新增包依赖。

### 6.2 User-mandated Java rule compliance

```text
1 类名规范，必须以 java的pojo规范命名。以dao po bo vo dto query command event等结尾
2 每层之间必须被 springboot-validation 校验，复用对象使用分组校验；基于原生和自定义约束注解、@Valid、@Validated 及 ConstraintValidator 扩展实现。ValidationUtils 只承担通用手工校验，不承载全部业务校验；电话号码复用 libphonenumber。
3 Java POJO 默认使用 class，注解为 @Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)，按构造目标选择 @Builder 或 @SuperBuilder；父类状态参与相等性时使用 @EqualsAndHashCode(callSuper = true)。只有不可变 value object 可以使用 record，并豁免 class 的 Lombok 规范；普通实体不能因简单而使用 record。使用 MapStruct、MapStructPlus 和 egon-cola-component-common-core 的 BaseConverter（双向）或 BaseForwardConverter（不可逆投影）进行转换。
4业务类必须使用@Slf4j注解注入log对象。如果业务类被spring管理，必须指定名称，如果是单例的情况下，参考@Service("userService")。如果需要依赖注入，必须@RequiredArgsConstructor进行修饰，不要代码中写。且属性必须被@qualify修饰。
5 工具类只允许使用jdk原生、Apache Commons(commons-lang3、commons-collections4、commons-io、commons-text、commons-codec、commons-beanutils)、Guava。针对Tika按需引入。
6 JSON 使用 Spring Boot Jackson；持久化枚举值使用 @EnumValue，向前端输出的枚举值使用 @JsonValue，禁止 ordinal 作为业务编码。
7 springboot 多环境配置文件，必须保持配置一致，但值不一定一致。
9 复杂业务必须引入设计模式，不允许硬编码
10 日期相关的必须使用java.time下的实体类，不允许使用java.util下的
11 只允许现有三层结构或当前 egon-cola-archetypes 的 DDD 结构，三层结构保持现状。必须尽量复用 egon-cola-components；持久化模块必须使用 Egon COLA MP Starter，DDL 统一由 egon-mp-sdj-ext-starter 分布式管理。业务唯一键必须组合业务列与 deletedAt（数据库 deleted_at），并验证 NULL、租户及重复软删除语义。采用 CQE（Command Query Event）；Event 必须经 egon-cola-component-transactional-outbox-starter 或 MQ 中间件投递。
```

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | PS release 及 C DTO | 使用 CLI/Validator 与不可变 Candidate/Result 语义 | §8/10 | TEST-001 | PASS |
| Rule 2 | Yes | EVD-001/002 | 入站严格 JSON、现有 C 构造校验和模型验证，外加独立 scope 边界 | §9 | TEST-001/002/003 | PASS |
| Rule 3 | Yes | 现有不可变 records 和类型 | 复用 C DTO，新增内部不可变投影；无可变业务实体或跨层 ORM 映射 | §10 | TEST-001/005 | PASS |
| Rule 4 | No | 独立 CLI，无 Bean 或业务服务 | 无框架注入和业务日志，stderr 固定诊断 | §9 | TEST-004/006 | N/A |
| Rule 5 | Yes | 现有 JDK 和 Jackson | JDK 摘要/ZIP/IO、现有 Jackson，零依赖 | §8/10 | TEST-001/004 | PASS |
| Rule 6 | Yes | Jackson 3 和 C enum | 严格字符串枚举与规范 JSON，不用 ordinal | §9/10 | TEST-001/005 | PASS |
| Rule 7 | No | A1 无 runtime 配置 | 不改 application YAML 和启动类 | §16 | TEST-006 | N/A |
| Rule 9 | No | 一组无副作用确定性输入校验 | 直接有限顺序，保留已有模型校验，不加策略框架 | §13 | TEST-001/003 | N/A |
| Rule 10 | No | A1 无事实时间、定时或数据库字段 | 输出不包含当前时间，保持可复算 | §9/10 | TEST-005 | N/A |
| Rule 11 | Yes | 现有 PS 技术垂直与 release 包 | 不建业务层、ORM、DB 或事件；保持同一所有者 | §8/11 | TEST-003/006 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: 只读本地文件和输出确定性摘要。CLI 不写创建时间、文件 mtime 或校验时刻，不改变现有 Process/Agent 时间合同。

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| PS offline CLI + validator | New | REQ-001–006 | 已有 Process release GET、PaC verify 和 Agent run 查询 | 三者没有独立交付的组合、无 live 环境下无法复算一份候选 | 一次本地调用；无持久化/网络 | Add |
| Shared Process digest | Expand | REQ-003 | 各自计算 | 复制生产摘要会造成模型和 schema 规则漂移 | 同包纯方法供原发布与 A1 | Reuse |
| Existing AR codec | Keep | REQ-002 | PS 引入 AR 或复制私有算法 | owner 跨模块边界和受信权威缺失 | owner SHA 只声明，raw bytes 独立验证 | Keep |
| Existing AS/PS executable archives | Keep | REQ-004 | 新 Maven module/打包 plugin | Boot launcher 已有按 main 显式选择能力 | 无新增 build 或服务入口 | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| 分别查看 owner 输出 | 可能多次 | 多份证据 | 既有各 owner | 易漏 scope、来源或版本关系 | 只能核对单项 |
| 单个离线 CLI | 0 | success/error | 无运行状态 | 输入路径、字节一致性和确定性 | 直接复算或拒绝一份候选 |

### 7.1 System Architecture Design

本地操作者使用 PS exec JAR 的 PropertiesLauncher 显式选择 `PlatformReleaseArtifactCli`。CLI 负责只读文件输入、独立 scope 与 stdout/stderr，委托同包 `PlatformReleaseArtifactValidator` 使用现有 C DTO、Process 模型校验器和同一 Process digest。AS 和 AR 通过输入的已有制品描述进入，不形成 Maven 模块依赖。原服务运行入口继续使用既有发布者、数据库和原 Start-Class。

### 7.2 High-Level Design

命令先校验参数与本地准备目录，再读闭合 JSON，校验所有 file references。之后调用纯模型和契约校验，验证两个包内清单，构建确定性 payload，复算候选摘要。可选 `--expect-sha256` 是额外比较，命令在完成所有校验前不写 stdout。

### 7.3 Detailed Design

1. Agent 声明文件经严格 Jackson 3 解析和实际 `AgentDefinitionSpecDTO` 构造。A1 只接受 CONFIG、null codeRef、三个依赖列表空、`maxToolCalls=0`、`allowedToolVersions=[]`。配置只支持一根 Agent，根对象精确 `chatModelBeanName,modelName,rootAgentName,agents`，agents 只含一个 `name,instruction`，名称等于根名；配置内容不进入输出。输入/输出契约证据分别绑定 C `AgentInputDTO`、`AgentExecutionResult` 和各自的定义 schemaVersion。CLI 核对原始定义 JSON 与两份契约源码证据字节。源文件 SHA 只代表本地结构材料，既不是 JsonSchema 证书，也不是 owner 已发布 schema 证明。Agent `definitionContentSha256` 保持 `DECLARED`，不会冒充 AR 摘要复算结果。
2. Process 模型经 `ProcessModelValidator#validateBpmn,validateDmn,validateCmmn`，再用当前 `validateDecisionBinding,validateHumanTaskBindings,validateWorkerBindings` 核对精确 BPMN→DMN、Task schema 和 Worker 输入映射。对每个 BPMN Case source，确认同组唯一 CMMN key。A1 不创建 Worker/Case registry，也不借用当前 lease、维护循环或配置证明。每组至少一 BPMN，至多 16 模型，重复 type/key 拒绝；CMMN inputs 空，DMN 声明 inputs 必须匹配解析类型，BPMN 声明 inputs 使用实际校验。复用 `ProcessReleaseService` 的旧 frame 计算 `manifestSha256`，保持同样的验证后 XML 字节、有效 schema、类型/模型顺序和摘要输出。
3. A1 要求候选 Process release 的 BPMN 中只有一个 Agent Message wait，并由 `agentLink` 精确选中。读取指定 `processDefinitionKey` 的 `ValidatedProcessModel.messageWaits()`，该条 `MessageWait.workerElementId` 与指定 `workerElementId` 相同。对应的 `WorkerTask.topic/schemaVersion/effectSafety` 精确等于链接声明和 `EXTERNAL_IDEMPOTENT`，使用现有 `ProcessModelValidator#nativeWorkerTopic` 约束。链接中的 Agent key/version/owner SHA 精确等于该单 Agent 声明。这个比较证明本地模型与声明可以关联，`AgentDispatchBinding` 的 SERVICE actor、来源、注册与实时授权均保留给 runtime owner。不会从 Worker payload 选择另一个 Agent。
4. 两份 exec JAR 以 JDK `JarFile` 读取，不执行任何内容。原始 archive SHA 与所需唯一 manifest entry 的原始 SHA 都必须等于输入约定。验证 `META-INF/MANIFEST.MF` 的真实 Boot 布局、各 owner Start-Class，和 ZIP 根 `META-INF/permissions/manifest-v1.json` 的 `schemaVersion="1.0"`、`application.businessCode=xingyuan`、`application.applicationCode=tianji`、`producer.kind=JAVA`、精确 producer/module 身份。A1 只接受 ZIP 根路径，拒绝缺少、重复或同时存在等价 BOOT-INF 路径。该 raw entry SHA 与 Jianshen 另算的 policy/metadata/manifest canonical digest 分开；后者由原 Maven profile 负责。
5. 创建一个内部不可变候选值，带确切输入摘要和限定声明。输出不含任何本地绝对/相对路径、原始内容、签名输入、权限 payload 或当前时间。所有检查满足后生成唯一确定性结果。

## 8. Package Structure and Code File Tree

| Operation | Exact path in tianji-service | Responsibility |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java` | 非 Spring main 与包内 `run(String[], OutputStream, PrintStream)`，参数/退出码和一次输出 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java` | 有界 IO、实际 DTO/模型/归属内容验证、候选编译与摘要；小型 immutable 记录内嵌 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java` | 同包纯 `manifestHash(List<ModelResource>)` 供发布及离线 caller；不暴露 API |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCandidateTest.java` | 真实 CLI run、JDK temp root/fixture、模型和字节/异常门禁 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java` | 旧发布摘要和输入排序回归；保留已有原生运行门禁 |

没有新增包依赖、`pom.xml`、通用脚本、运行配置、客户端接口或迁移。CLI 调用方式由批准 Spec 及后续证据记录。

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Name | Kind | Owner | Inputs | Outputs | Requirements |
| --- | --- | --- | --- | --- | --- | --- |
| CLI-001 | Verify candidate artifacts | Local CLI Query | PS | absolute root, relative input, expected tenant/channel, optional expected candidate hash | canonical JSON stdout on success, stable diagnostic stderr on failure | REQ-001–006 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CLI-001 — 离线核对与输出候选

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | 新增离线 CLI Query |
| Independent consumer goal | 操作者和 CI 能在不访问部署的情况下生成并重复核对候选内容 |
| Parameter ownership and derivation | 本地操作者提供独立预期 scope、审阅过的输入与已有制品，实际字节派生候选 |
| Direct/no-new-interface alternative | 多个分离的 owner 局部检查不能提供可复算、同一 scope 的候选文件 |
| Caller use of result | 本地审查、比对和后续签名设计审查，保持状态无副作用 |
| Round trips and failure points | 一次进程，无网络，拒绝目录/输入/owner 声明和摘要漂移 |
| Verdict | Add one bounded offline invocation，现有生产入口保留 |

##### Identity and purpose

使用 PS exec JAR 的显式 main。默认 `java -jar` 的 `Start-Class` 保持 `TianjiProcessServerApplication`。本地文件读取只以操作者自身文件权限运行，不提权、不加载或使用任何 bearer、私钥、角色或远端 URL。

```sh
java -Dloader.main=top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli \
  -cp "$ps_exec_jar" org.springframework.boot.loader.launch.PropertiesLauncher \
  verify --root "$private_artifact_root" --input candidate-input.json --tenant 20 --channel expense
```

##### Request parameters

| Name | Location | Rules |
| --- | --- | --- |
| `verify` | argv | 唯一命令 |
| `--root` | argv | 所有本地输入的绝对、规范、当前 owner 私有 POSIX 目录，不经 symlink |
| `--input` | argv | 根下相对 descriptor，UTF-8，1–65536 bytes |
| `--tenant` | argv | 正数 signed long 的十进制规范文本，不接收前导零 |
| `--channel` | argv | `[a-z][a-z0-9._-]{0,63}` |
| `--expect-sha256` | argv optional | 恰好 64 lowercase hex，附加已知候选摘要比较 |

只接受指定 argv 键，每键一次，独立比较 `tenantId` 和 `channel`。输入对象逐层闭合，模型 list 是一个 Process release。例中重复字符的哈希只示范字段形状，实际运行必须提供文件真值。

```json
{
  "schemaVersion": 1,
  "tenantId": "20",
  "channel": "expense",
  "sourceRevision": "1111111111111111111111111111111111111111",
  "agent": {
    "definitionKey": "expenseAnalysis",
    "definitionVersion": 1,
    "definitionContentSha256": "00b1d06cf7d973a32b03f2fdba1a25612892f0afa16ab6381b945f786d92ed7c",
    "definition": {"path": "agent/definition.json", "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
    "inputContract": {"path": "agent/AgentInputDTO.java", "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
    "outputContract": {"path": "agent/AgentExecutionResult.java", "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}
  },
  "process": {
    "releaseVersion": "expense-v1",
    "manifestSha256": "f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5",
    "models": [
      {"type": "BPMN", "definitionKey": "expenseReview", "xml": {"path": "models/expense-review.bpmn20.xml", "sha256": "5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6"}, "inputs": {"amount": "INTEGER", "operationId": "STRING"}},
      {"type": "DMN", "definitionKey": "expenseRouting", "xml": {"path": "models/expense-routing.dmn", "sha256": "94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74"}, "inputs": {"amount": "INTEGER"}},
      {"type": "CMMN", "definitionKey": "expenseException", "xml": {"path": "models/expense-exception.cmmn", "sha256": "c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1"}, "inputs": {}}
    ]
  },
  "agentLink": {
    "processDefinitionKey": "expenseReview",
    "workerElementId": "agentStart",
    "topic": "agentStart",
    "schemaVersion": "1",
    "agentDefinitionKey": "expenseAnalysis",
    "agentDefinitionVersion": 1,
    "agentDefinitionContentSha256": "00b1d06cf7d973a32b03f2fdba1a25612892f0afa16ab6381b945f786d92ed7c"
  },
  "packages": {
    "agent": {
      "archive": {"path": "dist/agent-exec.jar", "sha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},
      "manifestEntrySha256": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    },
    "process": {
      "archive": {"path": "dist/process-exec.jar", "sha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"},
      "manifestEntrySha256": "0000000000000000000000000000000000000000000000000000000000000000"
    }
  }
}
```

`sourceRevision` 为声明的完整 40 lowercase hex Git SHA，工具不调用 git 或推断仓库身份。Agent definition key 符合 `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`，正整数 long 版本和 definition JSON 自身字段一致。contract source basenames 分别为 `AgentInputDTO.java` 和 `AgentExecutionResult.java`，确认 UTF-8、C 包名和 record 名，保留字节证据。它不编译 Java 源码或推断语义等价。模型 key、releaseVersion 和 START schema 复用 C/PS 已有验证规则。descriptor 无 null、未知键或外部数据状态字段，只有实际 definition DTO 本身接受其既有 nullable 字段。

##### Success response

退出 0，stdout 写一次 UTF-8 规范 JSON 和一个 LF。外壳精确 `candidateSha256,candidate`，candidate 的全部字段如下。下列顺序是可读描述，实际 JSON 按 §10 的规范编码。

| Candidate object | Exact output fields |
| --- | --- |
| Root | `schemaVersion, status, tenantId, channel, sourceRevision, agent, process, agentLink, packages, pendingAuthority` |
| `agent` | `definitionKey, definitionVersion, definitionArtifactSha256, definitionContentSha256, ownerDigestState, inputContract, outputContract, modelRouteRef, policyVersionRef, authorityState, optionalDependencyState` |
| Agent contracts | each exact `symbol, version, artifactSha256`; symbols fixed to C `AgentInputDTO` and `AgentExecutionResult` FQCN, versions read from the definition |
| `process` | `releaseVersion, manifestSha256, state, models` and model objects `type, definitionKey, modelSha256, inputs` |
| `agentLink` | exact input link fields plus `state="ARTIFACT_CHECKED"` for its local shape; no runtime identity |
| `packages` | `agent, process`, each exact `producerId, archiveSha256, manifestEntrySha256, state` |
| `pendingAuthority` | fixed ordered array `["RELEASE_SIGNING","OWNER_PUBLICATION","LIVE_REVOCATION","REVIEW_AND_ACTIVATION"]` |

Root `status="ARTIFACT_CHECKED_ONLY"`。Agent `ownerDigestState="DECLARED"`，`authorityState="DECLARED"`，`optionalDependencyState="NOT_REQUIRED"`。Process 和包均为 `state="ARTIFACT_CHECKED"`。Agent route/policy 是输入定义中的精确声明，不包含 owner 账单或权限。输出 `candidateSha256` 总是 lowercase hex。

##### Error responses

校验失败 stdout 为空，stderr 仅固定安全代码加 LF。64 为 `TIANJI_RELEASE_ARTIFACT_USAGE`，65 为 `TIANJI_RELEASE_ARTIFACT_INVALID` 或 `TIANJI_RELEASE_ARTIFACT_DIGEST_MISMATCH`，66 为 `TIANJI_RELEASE_ARTIFACT_IO_UNAVAILABLE`。拒绝 unknown/duplicate/trailing keys、非 UTF-8、overflow、路径越界、缺/错包、业务模型绑定错误、无证据字段和伪造权限状态。异常消息、堆栈、XML、路径、凭据均不输出。若 stdout 设备在最终写入时失败，退出 66，操作者丢弃可能的部分写入。

##### Interface logic for frontend and consumers

操作者准备已审阅的本地目录、按此合同固定当前制品哈希和期望 scope，再运行一次。若需要留下文件，在受控目录以创建新文件的方式重定向 stdout 并检查退出码、完整 JSON 和候选摘要。复查时从已记录候选摘要传 `--expect-sha256`。拒绝时修正输入或回到 owner 证明，不能手动注入 `APPROVED` 等状态。此 CLI 没有浏览器、HTTP、隐式重试和授权 side effect。

##### Compatibility and verification

保持旧 PS publish、get、start、Flowable DDL/运行配置和 Jianshen producer 原义，老 manifest SHA 必须字节一致。使用 TEST-001–006 的本地有限资产和 archive 选中 main 证明。实际包可被检查，不推导出运行 readiness 或签名。

## 10. POJO and Data Model Design

候选 payload 使用私有不可变值，复用 `AgentDefinitionSpecDTO` 和 `ProcessReleaseService.ModelResource`，无需新增跨层转换对象。配置检查只在校验内存在，输出字段来自闭合投影。候选和 file ref 不作为 public SDK 或数据库接口。

规范函数 `canonicalJson(value)` 固定以下规则。

1. 输入 JSON 无 BOM，严格 UTF-8，重复键、未知键、尾随 token、无效 Unicode 和标量强转拒绝。解析/生成沿用 Jackson 3。对象键按原始 UTF-8 无符号字节排序。整数使用无前导零十进制，无浮点；字符串保留精确 Unicode、不做 normalization，UTF-8 编码，仅对引号、反斜杠及合法 JSON 必须转义的字符使用确定转义。输出值禁止 C0/DEL，故不依赖不同转义选择。无额外空白或末尾换行。
2. 候选中模型数组按 type 名、definitionKey 的 UTF-8 次序排序。输入 schema 字段通过上述对象排序规范化。固定数组保持规定顺序，未定义的数组无排序推断。目录、输入 JSON 缩进、入站模型声明顺序不参与摘要，模型自身字节、源 revision、精确 scope 与归属变化参与。
3. `candidateSha256 = lowerhex(SHA-256(UTF8("TIANJI_PLATFORM_RELEASE_ARTIFACT_V1") || 0x00 || canonicalJson(candidate)))`。仅 payload 参与，外壳摘要字段不参与。最终写 `canonicalJson(envelope) || LF`。该域与未来 signed bundle 的域分离。
4. 复用的 legacy Process digest 继续写 model count 和各长度前缀字段，包含 validated XML 与有效 inputs 的名称/类型。它与 A1 candidate 摘要、Agent raw definition SHA、Jianshen raw entry SHA 各自独立。

## 11. Database Design

Unchanged。CLI 不需要 DataSource、Flowable engine、ORM、Flyway 或任何持久化 schema，现有发布者的事务和 SQL 字节保持。

## 12. Frontend Page Design

Unchanged。A1 只有本地命令与标准输出，现有 tianji-web、Task/Case、Agent/Process SDK 入口保持。

## 13. Design Patterns and Architecture Principles

单个确定性验证顺序与内部纯函数足够。CLI 是本地输入/退出适配，验证器持有有界解析和现有模型的组合，既有 Process digest 是一处共用技术函数。无需策略插件、数据库抽象、发布状态机或跨服务 facade。

## 14. Test Design

| Test | Gate | Expected evidence |
| --- | --- | --- |
| TEST-001 | `PlatformReleaseArtifactCandidateTest` valid local fixture | Two real entrypoint returns identical bytes; copy root, reorder fields/models and vary whitespace without change; pinned golden candidate digest and bounded stdout |
| TEST-002 | 同类 negative | Missing/unknown/duplicate/trailing fields, malformed UTF-8, float/string version, symlink/root traversal, wrong tenant/channel, hash changes, fake owner/signature/status rejected |
| TEST-003 | `ExpenseFixtureModelsTest,ProcessReleaseServiceTest` | Existing pure model and release digest regression; companion XML/key/type/schema, Case link, unknown Agent Worker topic/schema/effect or declared Agent identity mismatch fails; old publication hash golden remains |
| TEST-004 | `PlatformReleaseArtifactCandidateTest` packaged entries | Missing, duplicate, oversized or mismatched JAR/entry/producer/start class rejected; synthetic ZIP tests verify decoder only, actual exec artifacts separately inspected |
| TEST-005 | `PlatformReleaseArtifactCandidateTest,AgentJsonCodecTest` | Typed Agent scope, nonempty dependency/Tool budget rejection, owner digest declared, no raw XML/config/path/token/time fields; exact expected-digest check |
| TEST-006 | Existing offline source reactor verify + actual PS exec archive | `jar tf` shows exact class; original manifest Start-Class unchanged; invoke only the finite validator on user-provided local fixture and observe no service/app/DB dependency. Check AS/PS current PaC archive artifacts |

Future selectors `PlatformReleaseArtifactCandidateTest,ExpenseFixtureModelsTest,ProcessReleaseServiceTest,AgentJsonCodecTest`. Existing `./scripts/verify-source-reactor.sh` with `STELLAR_VERIFY_GOAL=verify`, `-Ppermission-producer`, `-DskipITs=true` and exact owning reports. T3-12 E selectors excluded. New executable validates only file bytes, while release signing and runtime acceptance remain separate.

## 15. Non-functional and Cross-cutting Design

- Relative paths use `/` and 1–16 ASCII path components, each 1–128 bytes starting alphanumeric, remaining letters/digits/dot/underscore/hyphen, total max 512 bytes. Reject absolute, empty, dot segments, backslashes, duplicates, symlink components, root escape, nonregular files and unreadable paths. Verify same owner and no group/other write on every subtree component. Leaf opens without following symlinks. Root itself must be private POSIX, exact real path, no symlink. Compare regular-file identity (fileKey when supported), size and mtime before and after a read, including the archive digest and entry inspection pair. Reject observed drift. Threat boundary is one owner-private staged directory and no concurrent mutation by the operator.
- Bound descriptor 64 KiB, Agent JSON 1 MiB, each contract source 128 KiB, XML each existing 512 KiB and total existing 4 MiB, each archive 512 MiB, and each read ZIP manifest entry 1 MiB. Limits apply before and during read. Iterate only central metadata and required entries, no extraction or classload input artifacts. Reject more than 65,536 archive entries and duplicate expected entries. Bound strict JSON nesting/token count by descriptor/definition vs packaged manifest, max nesting 32, 100,000 tokens, 512-byte UTF-8 property names, and bounded source bytes.
- Allow exact closed root/name/type/hash and integer scope. Treat each claimed input digest as a comparison against this staged set; a matching digest establishes local content identity. No unbounded caches, parser callbacks, model deployment, remote endpoints, password read or outbound credential flow. Diagnostics fixed and content-free.

## 16. Compatibility, Migration, Rollout, and Rollback

Locked T3-12 source delivery and approval of A1 Spec/Plan precede actual source. T3-12 E remains the live governance prerequisite. Preserve Spring Boot Start-Class, existing SDK and security behavior, production Process release digest, old migrations and all data. A1 can be validated and used locally without initializing services. The change has no migration. Revert focused source if needed, and keep earlier captured candidate digests as historical content proof.

## 17. Alternatives and Decisions

| Candidate | Verdict |
| --- | --- |
| Extend CL or C | CL has no existing native Process validators, and a C implementation would introduce a parser or duplicate semantics. Keep SDK and C DTO boundary |
| New release module or executable assembly | Existing PS exec JAR PropertiesLauncher supplies the minimal usable entry point, with no extra packaging/build |
| Reimplement AR digest in PS | Reject owner logic duplication. Only PS digest is recomputed, Agent owner digest stays declared |
| Store bundle in DB first | Unnecessary for a reproducible offline artifact and currently lacks signing/owner proof |

## 18. Risks and Open Questions

| Risk or gate | Resolution |
| --- | --- |
| T3-12 source/E gates | Reconfirm exact source locks for A1 execution. Only live T4 governance requires separate E acceptance |
| Agent owner artifact SHA and contract semantics | Raw bytes checked, owner digest and external schema publication declared; future authenticated exact API may independently verify |
| Signing/revocation truth | Separate T4-01 follow-up approval and architecture, with actual signer trust, release authority and current revoke. No offline promotion |
| Future tool-bearing/non-CONFIG packages | Present A1 rejects them, later expansions need precise authority and compatible schema |
| Artifact archive drift | Exact archive digest rejects changed builds, even with same logical content. Operator re-reviews candidate and keeps it content-addressed |

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/UC-002 | §7/8/9 | TEST-001/006 |
| REQ-002 | UC-001/UC-002 | §7.3/9.2/15 | TEST-002/005 |
| REQ-003 | UC-001 | §7.3/8/10 | TEST-003 |
| REQ-004 | UC-001 | §7.3/9/15 | TEST-004/006 |
| REQ-005 | UC-001/UC-002 | §9/10/15 | TEST-001/002/005 |
| REQ-006 | UC-001/UC-002 | §3/5/16/18 | TEST-005/006 and independent T3-12 gate |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6–8, EVD-001/003 | Existing PS owner and pure CLI | None |
| MC-REUSE-001 | Applicable | PASS | §6/7.3 EVD-001/002/004 | Existing validators, digest, artifacts | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | No new dependency or packaging | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Semantic CLI/Validator and immutable result | None |
| MC-VALID-001 | Applicable | PASS | §7.3/9/15 | Exact input, scope, file and model guards | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Existing DTOs and immutable internal facts | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Direct closed JSON projection within technical CLI | None |
| MC-LOG-001 | Not applicable | N/A | §9/15 | Fixed stderr diagnostic only | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new runtime bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/15 | JDK and existing Jackson | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | Closed UTF-8 and canonical digest | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No time-dependent output | None |
| MC-CONFIG-001 | Not applicable | N/A | §8/16 | No profile or config change | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Deterministic local validation, existing pure functions | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Scope remains one PS CLI and regression | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and archive gates planned | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/16/18 | No unresolved in-slice design required to review | Locked T3-12 source and approved Spec/Plan before any A1 implementation; E is independent |

**PASS — Ready for user review**

结构与实现设计候选供审阅。当前没有批准实施，也没有调用 Maven、运行 CLI、访问网络、启动应用、连接数据库或修改仓库源文件。T3-12 源码锁是 A1 的前置。真实 E 与后续签名/撤销证据仍待独立验收。
