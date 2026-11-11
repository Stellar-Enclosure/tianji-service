# T4-01 AS 已发布 CONFIG 本地就绪证据实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-16-13-as-published-config-readiness.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 16:13 Asia/Shanghai |
| Updated | 2026-11-11 16:13 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | 一个显式本地查询、共享 CONFIG 编译和当前 JTokkit 绑定 |
| Source Requirement | Master T4-01 |
| Baseline Revision | 55c8c42df600cfd46c5f6a7a5eabfcf8299ba81d |
| Implements Spec | [AS CONFIG 就绪 Spec](../spec/2026-11-11-16-12-as-published-config-readiness.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 16:12 Asia/Shanghai SHA-256 3358e64331d8933470c3d6fac92b73e20b9071bee3bfce24f118669e814235c0 |
| Effective Specs | [AS CONFIG 就绪 Spec](../spec/2026-11-11-16-12-as-published-config-readiness.md) |
| Depends On Plans | [AS 发布账本](./2026-11-11-15-41-as-published-definition-evidence.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在 AS 用现有已提交发布、安装的 CONFIG 编译及当前预算估算器提供本地就绪检查。一次 AR 提交。

## 2. Target Spec and Effective Design

使用头部精确 Spec。原发布账本保证已提交且摘要一致，组件固定 API 检查实际编译模型。本步骤只消费本地事实。

## 3. Effective Requirements and Acceptance

| ID | Requirement | Observable acceptance |
| --- | --- | --- |
| REQ-001 | 读取精确已提交版本并核对候选内容摘要。 | 读取现有 AR 主库，一次精确租户、键和版本读取。摘要不同在接触运行时前失败。 |
| REQ-002 | 仅验证 A1 单叶 CONFIG 并复用实际装配。 | 没有 CODE_FIRST、工作流、Prompt、Skill、Tool 或工具预算。使用安装的编译器并匹配编译后模型身份。 |
| REQ-003 | 校验实际 JTokkit 当前版本绑定。 | 完整路由、策略、模型名、bean、币种、实例和默认选项一致。自定义估算器、外层编译器、内层适配器工厂或校验器在调用前被拒绝。 |
| REQ-004 | 只输出有限安全投影。 | 保留元数据，无配置、指令和源异常。正例无模型 call/stream；不创建 run、Session 或模拟请求。 |
| REQ-005 | 保持现有运行路径和可审查范围。 | 普通 CONFIG 与 CODE_FIRST 回归、精确五种反证、31 份 SQL 及无新增依赖。 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定 Service 和 Components 的精确 revision。测试复用 owner fixture，随后接通包内发布、JTokkit 查找、执行器和本地 Query。

### 4.2 Test-first strategy

每个关键检查使用独立隔离源码变体。要求精确单方法出现预期断言，恢复完整候选后跑普通执行回归。

### 4.3 Sequential and parallel boundaries

R1/G1/P1/S1 按验证依赖执行。拥有者 Maven 使用独立 source checkout 或与共享任务串行。只读文档校验不占用编译输出。

### 4.4 Commit boundaries

仅一个 Service AR 提交。组件已有前置提交，且从相同坐标的 source reactor 编译。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 已提交定义交接 | Required §7/10 | EVD-001 | 同一查询方法内部保留完整定义 | 一份包内值及既有投影。 |
| CONFIG 与 JTokkit 检查 | Required §7 | EVD-002–006 | 复用原装配与 estimator | 不造 Run 或请求。 |
| 有限测试 | Required §14 | Existing owner fixture | 复用已有 JDBC mock | 修改一个测试类。 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 已提交发布与编译绑定 | REQ-001–004 | R1/G1 | 1–5 | 原 AR 账本与固定 Components API。 |
| 兼容与审查范围 | REQ-005 | G1/P1/S1 | 1–8 | 现有真实普通执行测试和 exact hashes。 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Owner source | EVD-001 AR primary | 复用只读事务和发布摘要。 |
| Runtime assembly | EVD-002/005/006 | 使用实际已装配编译器、默认工厂和校验器，核对编译后对象。 |
| Exact model policy | EVD-003/004 | 复用 JTokkit 查找及当前选项，使用已发布策略。 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 已提交内容 | AgentLedgerService / existing JDBC read | EVD-001 | 同一 owner | 包内保留定义 | 复用，无依赖 | Step 1/G1 |
| 实际编译 | AdkAgentRunExecutor / Component AgentFlowFactory | EVD-002/005/006 | 已安装的原运行路径 | 共用 Config 准备和编译 | 现有组件坐标 | Step 1/R1 |
| 策略绑定 | JTokkitChatModelBudgetEstimator | EVD-003/004 | 原精确绑定与当前默认值 | 共用查找、增加实例检查 | 无依赖 | Step 1/R1 |

### 4.8 User-mandated Java Rule Implementation Matrix

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

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | EVD-001–006 | Files 1–8 | 复用 Agent 账本和执行器命名，新增一个 QueryExe 与不可变证据 DTO。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–006 | Files 1–8 | 本地非代理查询显式拒绝无效摘要、所有者和形状，并复用现有 Config Validator。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–006 | Files 1–8 | 只新增不可变语义值，保持定义与投影封装。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–006 | Files 1–8 | 显式本地构造，无新 Spring Bean、日志组件或注入点。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–006 | Files 1–8 | JDK 精确比较及现有 Agent Flow/ADK 类型和 JTokkit。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–006 | Files 1–8 | 继续使用原 Jackson 配置读取和原发布摘要，没有 wire DTO 或枚举改动。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2 | EVD-001–006 | Files 1–8 | 没有应用配置、Profile 或新开关。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–006 | Files 1–8 | 复用现有 Config compiler、估算器 Strategy 与 owner Query。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–006 | Files 1–8 | 沿用原主库 observedAt，未赋予运行时有效期。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–006 | Files 1–8 | 仅在现有 AR 包和普通 JDBC 账本内读取，无表、事件或架构迁移。 | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | publication.observedAt | Occurred instant | java.time.Instant | Not persisted | Internal | Not applicable: UTC instant | Not applicable: no display | Records the existing primary observation, no proof TTL or calendar arithmetic | AgentDB UTC clock_timestamp | Preserve published-definition evidence semantics | Step 1 TEST-001 fixture |

## 5. Change File Tree

| Operation | Exact path | Responsibility |
| --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java` | 复用既有主库 JDBC fixture，增加七项就绪证明用例。 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | 提取包内已验证发布结果，公共读取保留原投影。 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java` | 共享精确 JTokkit 绑定查找，增加执行器所选模型身份校验。 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | 复用实际 CONFIG 装配并检查单叶及编译模型身份。 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedConfigReadinessQueryExe.java` | 按预期摘要查询已提交发布，执行本地就绪检查并返回安全投影。 |
| CREATE | `docs/egon/spec/2026-11-11-16-12-as-published-config-readiness.md` | 固定行为、信任边界与验收条件。 |
| CREATE | `docs/egon/plan/2026-11-11-16-13-as-published-config-readiness.md` | 固定依赖版本、步骤、测试选择器和文件摘要。 |
| CREATE | `docs/egon/evidence/2026-11-11-as-published-config-readiness.md` | 记录实际隔离证明和待验收范围。 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service 基线 `55c8c42df600cfd46c5f6a7a5eabfcf8299ba81d`，Components 模型身份 API `a9539edf1c420132fff04024a51ee7f6d4faff2a`，内建编译工厂校验的 owner 提交 `c68730198f6345926c1a88e98b957781673310be`。工作目录为 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 或完整隔离检出。P1 与 G1 从同一 Components 源码 checkout 编译。隔离证明的 JAR 摘要为 `c67a538fe22f862ba0f1392c6a28448b16ad6b386f8fd3141949d2bf33b6d447`，前置编译器源码的 SHA 为 `d535829e40981b330d8457459a01a26b1d696769168ef12c868705676b089fbf`。既有数据库源、权限、配置和 Maven 坐标不变。

| Candidate path | SHA-256 |
| --- | --- |
| `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java` | `50d60be3ad4de1511ff4fc671b8be8b0ef7252f8d283cb4c94d1e8f7f8794934` |
| `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | `d1f932170f9c0a0b5f04735300f148b5c12575483a12ddd4b0ce7d608f04869d` |
| `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java` | `c2d4d773a4b04871b921e0ba3b589757185af75c269d60515b9fed87a42ed0da` |
| `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | `533b0062dfbc297914d473b63d8fa75dde999991aa7bc050955dc14679a6f1a1` |
| `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedConfigReadinessQueryExe.java` | `96a5807ab1e51a6cd6d948a7a6a4c5aa73e7dcdec84108a73db57bfbf79dd6d5` |
| `docs/egon/spec/2026-11-11-16-12-as-published-config-readiness.md` | `3358e64331d8933470c3d6fac92b73e20b9071bee3bfce24f118669e814235c0` |
| `docs/egon/evidence/2026-11-11-as-published-config-readiness.md` | `73c773e88219f15f5fce0a7ea04dd0c6183c6a840270a5e02a621f559120cb8e` |

外置 `full-candidate.sha256` 包含 Plan 本身和其他七份文件。31 份基线 SQL 的逐文件 SHA 记录在 `sql-baseline.sha256`。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Verify the installed AS CONFIG assembly for one exact publication

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing owner read, installed executor and exact Component compiler and adapter predicates.
- Baseline state: Owner metadata read exists, and the execution path compiles CONFIG independently.
- Observable outcome: One exact published A1 configuration produces local readiness only when installed binding and actual compiled model match.
- End state: Additive query, private full definition handoff and shared runtime validation.
- Test-first gate: Required. R1 must execute one exact method for each deliberately weakened check.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java`

- Purpose: 复用既有主库 JDBC fixture，增加七项就绪证明用例。
- Symbols: AgentPublishedDefinitionEvidenceTest.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 复用既有主库 JDBC fixture，增加七项就绪证明用例。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
fixture.store(committedA1Definition);
var ready = query.requireReady(actor, key, version, digest);
assertNoProviderIo(selectedModel);
assertProjectionHasNoConfigurationOrInstruction(ready);
assertExactMismatchIsDeniedForEachAuthorityGuard();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: 提取包内已验证发布结果，公共读取保留原投影。
- Symbols: AgentLedgerService#readVerifiedPublication.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 提取包内已验证发布结果，公共读取保留原投影。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var row = repository.definition(actor.tenantId(), key, version);
verifyOriginalOwnerHashAndEmbeddedIdentity(row);
return new VerifiedPublishedDefinition(safeMetadata(row), row.specification());
// The existing public query returns only evidence().
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`

- Purpose: 共享精确 JTokkit 绑定查找，增加执行器所选模型身份校验。
- Symbols: JTokkitChatModelBudgetEstimator#requirePublishedConfigBinding.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 共享精确 JTokkit 绑定查找，增加执行器所选模型身份校验。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var bound = configBinding(definition, expectedModelName, definition.policy().currency());
requireSameReference(bound.model(), selectedModel);
current(bound);
// The normal snapshot path retains its existing effective currency.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: 复用实际 CONFIG 装配并检查单叶及编译模型身份。
- Symbols: AdkAgentRunExecutor#requirePublishedConfigReady.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 复用实际 CONFIG 装配并检查单叶及编译模型身份。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireBuiltInRuntimeComponentsBeforeInvocation();
var flow = configFlow(exactSingleLeafDefinition);
var root = compileConfigRoot(flow, List.of());
requireKnownCompiledAdapter(root, flow.model());
installedEstimator.requirePublishedConfigBinding(definition, flowName, flow.model());
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedConfigReadinessQueryExe.java`

- Purpose: 按预期摘要查询已提交发布，执行本地就绪检查并返回安全投影。
- Symbols: AgentPublishedConfigReadinessQueryExe#requireReady.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 按预期摘要查询已提交发布，执行本地就绪检查并返回安全投影。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var publication = ledger.readVerifiedPublication(actor, key, version);
requireExactExpectedCandidateHash(publication.evidence());
executor.requirePublishedConfigReady(publication.definition());
return new PublishedConfigReadinessEvidenceDTO(publication.evidence());
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 6 — `CREATE docs/egon/spec/2026-11-11-16-12-as-published-config-readiness.md`

- Purpose: 固定行为、信任边界与验收条件。
- Symbols: Specification.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 固定行为、信任边界与验收条件。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Trace owner digest, built-in components and installed estimator to exact local source.
Define the private handoff and safe result.
Record remaining real deployment, authentication and provider acceptance.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 7 — `CREATE docs/egon/plan/2026-11-11-16-13-as-published-config-readiness.md`

- Purpose: 固定依赖版本、步骤、测试选择器和文件摘要。
- Symbols: Implementation plan.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 固定依赖版本、步骤、测试选择器和文件摘要。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Pin both repository revisions and all candidate file hashes before editing.
Run exact independent R1 selectors and the finite regression.
Inspect the packaged adapter and AR symbols from the same source reactor.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。

#### File 8 — `CREATE docs/egon/evidence/2026-11-11-as-published-config-readiness.md`

- Purpose: 记录实际隔离证明和待验收范围。
- Symbols: Evidence.
- Repository evidence: EVD-001–006 and Spec §7/9/14.
- Dependencies and consumers: 原发布账本、安装的执行器和组件的已审核 API。
- Why now: 交付单一 AS 本地兼容性检查的这部分职责。
- Contract/signature changes: 记录实际隔离证明和待验收范围。
- Input/output and state mapping: 已提交定义与候选摘要在同一调用绑定，输出安全元数据。
- Error and edge behavior: 错误输入、来源不符、运行时装配替换和漂移均失败关闭。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record isolated JDK and actual component classpath provenance.
Record the exact negative controls and finite test counts.
Record actual owner reactor status separately from source or runtime acceptance.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: 保留原发布与普通执行合同。


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/P1/S1.
- Expected result: 精确反证、88 项有限用例、实际 AS 包和严格 scope 核对。
- Failure returns to: 对应内容、装配、身份、当前预算或包门禁。
- Completion criteria: 有限验证及包检查，记录真实集成验收边界。
- Rollback: 撤销单一 AR 提交。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedConfigReadinessQueryExe.java`, `docs/egon/spec/2026-11-11-16-12-as-published-config-readiness.md`, `docs/egon/plan/2026-11-11-16-13-as-published-config-readiness.md`, `docs/egon/evidence/2026-11-11-as-published-config-readiness.md`.
- Commit: feat(agent): verify published CONFIG runtime readiness.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | 下面五个精确 mutation/selector 对照。 | 各隔离变体发现并运行一项，精确失败为缺失预期 `AgentLedgerException`。 |
| G1 | `AgentPublishedDefinitionEvidenceTest,JTokkitChatModelBudgetEstimatorTest,AdkGovernedProcessLoopTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest` | 五个类分别 13、13、37、17、8 项，共 88 项，零失败或跳过。 |
| P1 | 同 selector 的 clean source-reactor verify 和双嵌套 JAR 字节码。 | AR 包的 Query/DTO 和执行器真实调用两处组件 predicates 及 JTokkit 绑定。组件包包含两处精确 API 和引用比较。权限和 31 份 SQL 保持不变。 |
| S1 | Strict Spec and Plan plus exact scope and SHA | 8 路径和文件 SHA 完全匹配。 |

| Mutation | Exact source needle | Exact AgentPublishedDefinitionEvidenceTest method |
| --- | --- | --- |
| Candidate digest | `!expectedContentSha256.equals(publication.evidence().definitionContentSha256())` guard | `refuses_wrong_candidate_digest_before_touching_the_runtime` |
| Compiled model | `!SpringAiModelAdapterFactory.matchesCompiledModel(adapter, config.modelName(), flow.model())` guard | `refuses_a_compiled_root_bound_to_a_different_model_instance` |
| Estimator model | `bound.model() != selectedModel` guard | `refuses_same_name_options_when_the_estimator_owns_a_different_model_instance` |
| Built-in compiler | `!AgentFlowFactory.usesBuiltInModelFactory(flowFactory)` disjunct | `refuses_delegating_adapter_factory_and_validator_before_either_is_invoked` |
| Built-in validator | `flowValidator.getClass() != AgentFlowConfigValidator.class` disjunct | `refuses_delegating_adapter_factory_and_validator_before_either_is_invoked` |

```sh
readiness_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/as-published-config-readiness
readiness_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false
export STELLAR_COMPONENTS_DIR=/Users/mario/SelfProject/Stellar-Enclosure/Stellar-Enclosure-Components
readiness_tests=AgentPublishedDefinitionEvidenceTest,JTokkitChatModelBudgetEstimatorTest,AdkGovernedProcessLoopTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest
test "$(git rev-parse HEAD)" = 55c8c42df600cfd46c5f6a7a5eabfcf8299ba81d
test "$(git -C "$STELLAR_COMPONENTS_DIR" rev-parse HEAD)" = "c68730198f6345926c1a88e98b957781673310be"
"$readiness_root/run-scratch.sh" \
  top.egon.cola.platform.tianji.agent.infrastructure.adk.JTokkitChatModelBudgetEstimatorTest \
  top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessLoopTest \
  top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkNativeAgentCompletionTest \
  top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentJsonCodecTest
"$readiness_root/run-negative.sh"
./scripts/verify-source-reactor.sh -gs "$readiness_settings" -s "$readiness_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$readiness_tests" clean
STELLAR_VERIFY_GOAL=verify ./scripts/verify-source-reactor.sh \
  -gs "$readiness_settings" -s "$readiness_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$readiness_tests" clean -Ppermission-producer \
  "-Dpermission.commit=55c8c42df600cfd46c5f6a7a5eabfcf8299ba81d" -Dpermission.buildId=LOCAL_UNCOMMITTED-as-config-readiness
as_exec=stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server-exec.jar
"$readiness_root/verify-packaged-api.sh" "$as_exec"
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-16-12-as-published-config-readiness.md
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-16-13-as-published-config-readiness.md
shasum -a 256 -c "$readiness_root/full-candidate.sha256"
shasum -a 256 -c "$readiness_root/sql-baseline.sha256"
git diff --cached --name-only | LC_ALL=C sort | diff - "$readiness_root/full-expected-paths"
git diff --cached --check
```

保留 G1 XML 后再执行 P1 的 clean。解包 AS JAR 中实际 AR 和 Components JAR，核对 Query、DTO、两处 predicates 和原权限清单。生成的权限清单与基线的实际资源和操作集合一致才通过。

## 9. Migration, Compatibility, Rollout, and Rollback

31 份 SQL 不变。组件和 Service 通过源码 reactor 在同一构建中编译；缓存、安装或发布 SNAPSHOT 的产物来源需单独核对。当前只有内部调用，未装配生产适配器。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Files 1–2 and 5 | R1/G1 |
| REQ-002 | Step 1 | Files 1 and 4–5 | R1/G1/P1 |
| REQ-003 | Step 1 | Files 1 and 3–4 | R1/G1 |
| REQ-004 | Step 1 | Files 1–5 | G1 |
| REQ-005 | Step 1 | Files 1–8 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

当前只交付 AS 进程内装配。真实授权、linked allowlist、跨副本部署、供应商可用性、实际请求预算与端到端时限留待独立验收。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | AR 本地调用和同一 owner 账本，Components 持有适配器的身份判断。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | 复用主库读取、Agent Flow 编译和现有模型预算绑定。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 沿用已有 Components SNAPSHOT 坐标和测试依赖。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 新查询类、内部 verified record 与安全 DTO。 | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | 精确摘要、租户、A1 形状、实际模型和估算器均校验。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 新值类型只携带既有安全投影，定义只限包内查询交接。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无新业务实体和独立层间转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 新增路径不写运行日志，失败不透出源异常或定义。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 只有宿主显式本地构造，无 Bean、注入或启动行为变化。 | None |
| MC-UTIL-001 | Applicable | PASS | §6 | 复用 JDK 和已安装编译器、适配器及预算估算器。 | None |
| MC-JSON-001 | Applicable | PASS | §7/10/14 | 复用现有 Jackson，fixture 检查投影序列化不含原配置和指令。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | 沿用已提交发布观察时刻，分开当前装配和 TTL。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | 无新开关和应用配置。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 沿用同源 Query、编译器和预算 Strategy。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 五个 AR Java 与三份文档，31 份 SQL 保持基线。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 精确五次 R1、88 项隔离回归和后续源码包验收。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 当前只交付本地证明。实际主库、授权与部署权威独立验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
