# T4-01 A6 PS 本地 Worker 路由证据规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | None |
| Created | 2026-11-11 15:43 Asia/Shanghai |
| Updated | 2026-11-11 15:43 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | PS 本地 Worker 注册与 Agent 桥接路由证据 |
| Change Surface | One explicit local adapter and finite tests |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | ae8ad136e65a5924ebf236dcddda3274cd049696 |
| Amends | None |
| Supersedes | None |
| Depends On | [A5 §7](./2026-11-11-15-28-governed-process-start.md#7-architecture-design) |
| Related Specs | None |
| Related Plans | [A6 Plan](../plan/2026-11-11-15-44-ps-worker-route-evidence.md) |

## 1. Summary

从同一个 Process Server 的现有桥接验证输入读取唯一 Worker 路由，生成绑定与可复算的注册摘要。A4/A5 可以复用该输入，实际部署、跨实例一致性与历史绑定保留由独立权威承担。

## 2. Background and Current State

| Evidence ID | Classification | Exact path/symbol/decision/command | Observed fact | Design significance | Verification limit/freshness |
| --- | --- | --- | --- | --- | --- |
| EVD-001 | Static repository | ProcessAgentBridgeConfiguration#validateBindings, ProcessAgentBridgeProperties.Binding#dispatch, WorkerTopicRegistry#require | 已有精确服务身份、内部 dispatch、租约、Inbox owner、租户 issuer 和本地 OAuth 授权键校验。 | 直接复用同一组显式入参。 | 不能证明实际 Spring 启动或出站授权。 |
| EVD-002 | Static repository | FlowableWorkerService#acquire and WorkerTopicRegistry#registrations | Flowable 原生获取按 tenant 和 native topic。Registry 可对同一三元组保存不同 actor。 | 单个 actor 命中不足以排除第二个有效 Worker。 | 只对一份注册清单作判断。 |
| EVD-003 | Static repository | PlatformReleaseActivationService#workerFingerprint and ProcessExecutionService#requireWorker | A4/A5 已比较实际 Agent 绑定及 Worker 注册摘要。 | 返回可直接组合的两段值。 | 权威装配待 I1。 |
| EVD-004 | Static repository | ProcessAgentBridgeConfiguration#contexts, IdpServiceOAuth2Client#authorizationKey | 三个固定 operation scope 使用同一资源、应用和租户。获取授权键检查本地凭证存在。 | 摘要用公开配置字段，复用校验但不取 token。 | 实际远程权限、可达性和签名另行证明。 |
| EVD-005 | Static repository | OrchestrationRequestDigests#hash, ProcessAgentBridgeConfigurationTest and WorkerTopicRegistryTest | 契约已有长度分隔和有序的原始值摘要。原测试覆盖装配和注册行为。 | 复用摘要和兼容测试。 | 单元范围。 |

### 2.4 Evidence and current-chain map

| Entry/trigger | Current call chain | Data read/written | External dependency | Consumers | Evidence |
| --- | --- | --- | --- | --- | --- |
| Local bridge validation | Binding#dispatch → validateBindings → Registry#require | 本地配置及已有授权键，无写 | 已绑定 Shoubing starter，本地检查 | PS 内部 | EVD-001/004 |
| Internal Worker acquire | acquireAgentDispatch → acquire → Flowable native tenant/topic | 原流程租约与任务 | Flowable/ProcessDB | 既有内部桥接 | EVD-002 |
| A4/A5 evidence | workerFingerprint → requireWorker | 独立权威证据 | 部署权威待接 | A4/A5 | EVD-003 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 既有 PS bridge 内部技术适配器增量，当前职责及入口基于实际生产类。

## 3. Goals and Non-goals

本步证明单份已验证 PS 配置内的唯一原生路由和非秘密注册摘要。跨实例部署、权威租约及 AS 发布证据在独立边界验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS bridge internal adapter | Affected | EVD-001–005 | 显式本地快照和精确三元组查找 | Java contract, model, finite proof | §7, §8, §9, §10, §14, §15, §16 |
| Runtime assembly and authority | Context-only | EVD-001–004 | A4/A5 和现有 Bridge 装配继续由现有入口持有 | 本地与部署证据边界 | §18 |
| ProcessDB and frontend | Unchanged | EVD-002/003 | 当前原生租约、A4/A5 和页面 | 原边界 | §11, §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 仅启用的已验证本地桥接可出具证据 | Must | 复用实际桥接绑定、Worker 注册、结果接收、信任及 OAuth 身份校验。关闭时在 OAuth 读取前拒绝，后续查找无 I/O。 | EVD-001/004 |
| REQ-002 | 按原生三元组跨身份检查唯一性 | Must | 正 tenant、原 topic/schema 语法。完整注册与绑定各恰一条，且服务身份、内部 dispatch 模式一致。缺失或多条返回空。 | EVD-001/002 |
| REQ-003 | 稳定且限于非秘密配置的注册摘要 | Must | 独立固定向量、乱序等价、映射/租约/重试/并发/资源/应用/身份变动产生新摘要。可交给既有 A4 外层绑定算法。 | EVD-003/004/005 |
| REQ-004 | 仅增加显式本地适配器和有限验收 | Must | 运行入口、配置、权限、存储及 A4/A5 调用关系按基线。单反证、11 项有限测试、真实包结构及精确 5 路径可核对。 | EVD-002/005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | PS 内部配置所有者 | 提供实际单实例已装配源对象。 |
| ACTOR-002 | 源码评审者 | 复核精确拒绝、摘要和打包。 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 核对一份本地 Worker 路由 | ACTOR-001 | 已启用 dispatch，实际配置依赖齐备 | 既有校验后按三元组读取并生成摘要 | 无匹配、重复或完整身份冲突时无证据；配置非法时原验证失败 | 成功值可供调用者和独立部署权威组合 | REQ-001/002/003 |
| UC-002 | 复核有限交付 | ACTOR-002 | 锁定基线与两个 Java 路径 | 运行 R1/G1/P1/S1 | 任一门禁失败阻止源码验收 | 保留尚待完成 I1 的范围 | REQ-004 |

## 5. Constraints, Assumptions, and Decisions

ASM-001：显式调用方提供其所观察 PS 实例的真实配置源。适配器生成时读取一个本地快照；调用方应将该输入与所观察部署对应。动态配置和集群替换属于 I1。本步沿用受管桥接校验。

## 6. Project Technology Context

传统分层，Process Server bridge 适配器位置。Java 25、Spring Boot、Flowable、现有 Shoubing starter 和 Tianji Contract 摘要，以及 JUnit/Mockito。

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
| Rule 1 | Yes | EVD-001–005 | 技术适配器与私有构造的不可变 Route 证据。 | §7/8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 在现有配置验证之后核对原生三元组、跨身份唯一和完整服务身份。 | §7/8 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 组合现有 AgentDispatchBinding 和固定摘要，无复制模型。 | §7/8 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–005 | 当前步骤无对应变更。 | §7/8 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK 集合及现有 OrchestrationRequestDigests。 | §7/8 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–005 | 当前步骤无对应变更。 | §7/8 | TEST-001–004 | N/A |
| Rule 7 | Yes | EVD-001–005 | 复用已绑定 dispatchEnabled 和现有配置，验证各环境资源一致。 | §7/8 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 用只读技术适配器组合原注册和桥接权威。 | §7/8 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | 现有 Duration 的毫秒配置，不签发时间窗。 | §7/8 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | 保留 PS 的传统分层与 bridge 边界。 | §7/8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | leaseDuration, retryDelay, httpTimeout, acknowledgementReserve, maxClockSkew | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Existing validated bounds; no instant or freshness guarantee | Whole milliseconds, no clock read | Existing timing values encoded losslessly | Step 1 TEST-003/G1 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 配置校验 | Reuse | REQ-001 | 原 validateBindings | 已有完整校验 | 显式一次调用 | Use |
| 本地查找和摘要 | Add | REQ-002/003 | Registry#require 按单 actor 查找 | 原生路由共用，无法识别第二个 actor | 一个内部适配器 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| Explicit local evidence | 0 | 0 | 0 | 配置校验失败或三元组无法唯一对应。 |
| A4/A5 fleet verification | 本步骤未接入 | 既有状态 | 既有 V12/V13 | 由独立部署权威和后续验收承担。 |

### 7.1 System Architecture Design

工厂接收现有 bridge properties、Worker registry、producer registry、Inbox properties、Shoubing starter properties、issuer registry、OAuth2 client。调用与现有 BridgeResources 共用的配置校验。该类由调用方显式构造，无 Spring bean。

### 7.2 High-Level Design

检查 dispatchEnabled 后按原租户与 Agent 绑定、内部派发、租约预算、结果 owner、issuer 状态和 OAuth 注册身份校验整份配置。快照持有不可变 properties/registrations 和经校验 appId。查找先验证正 tenant 与原生 topic/schema，再分别对完整绑定与注册清单计数到 2。两侧都恰为 1，Worker 内部模式成立且两边的完整 ProcessActor 相等时返回证据。

### 7.3 Detailed Design

| Input | Existing bounds / exact treatment |
| --- | --- |
| Tenant, topic and schema | tenant > 0；topic `[A-Za-z_][A-Za-z0-9_.-]{0,127}`；schema `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}`；原生拼接长度 ≤ 255。字符串精确相等。 |
| Full actor | SERVICE，正 tenant，原 issuer SHA-256 与精确 subject（≤ 256）；同 tenant 不同 issuer 或 subject 均分别计数。 |
| Collection/schema | 绑定和注册分别最多 1000；输入和输出各最多 16；每个字段允许的映射 1–16，字段最多 64 个 ASCII 标识字符。内部 dispatch 注册为 EXTERNAL_IDEMPOTENT，输出为 `runId: STRING`。 |
| Timing | 并发 1–32，租约与提交超时各 1s–15m，重试 0–1h，HTTP 与 acknowledgement reserve 各 100ms–30s，clock skew 0–30s。租约至少是两倍 HTTP timeout + acknowledgement reserve + clock skew，且小于 submission timeout。已验证 Duration 使用整毫秒。 |
| OAuth / transport | 精确 HTTPS ASCII URI，规范形式，无 userinfo/query/fragment；base URI ≤ 2048，资源 URI ≤ 512。registration/app ID ≤ 128，三种固定租户 scope，HTTP token call 上限 1–8。 |

摘要域固定 `TIANJI_PS_LOCAL_WORKER_REGISTRATION_V1`，依次编码注册的 tenant、actor type/issuer fingerprint/subject、topic/schema、并发、租约/重试毫秒、effect、内部模式、bridge base URI、OAuth registration/app/resource/context、三 scope、HTTP timeout/token call 限额、acknowledgement reserve/clock skew。schema 和映射以带前缀的有序键表达输入/输出、原始类型和允许字段。JDK UTF-8 与现有长度分隔编码确定唯一摘要。Agent 定义、指令及提交超时在既有 A4 外层 Worker 指纹中绑定。

## 8. Package Structure and Code File Tree

| Operation | Exact path | Symbol | Purpose | Step | Gate |
| --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidenceTest.java` | ProcessAgentLocalWorkerRouteEvidenceTest | 先锁定本地三元组歧义和既有校验。 | Step 1 | R1/G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidence.java` | ProcessAgentLocalWorkerRouteEvidence | 生成可独立复算的本地路由证据。 | Step 1 | G1/P1 |
| CREATE | `docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md` | REQ-001–004 | 记录证据范围、边界和可观察结果。 | Step 1 | S1 |
| CREATE | `docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md` | Step 1 | 固定测试、变异、工作目录和提交范围。 | Step 1 | S1 |
| CREATE | `docs/egon/evidence/2026-11-11-ps-worker-route-evidence.md` | R1/G1/P1/S1/I1 | 记录执行证据与待核验条件。 | Step 1 | S1 |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Java internal operation | Input/output | Failure |
| --- | --- | --- | --- |
| INTERNAL-001 | ProcessAgentLocalWorkerRouteEvidence#validate | 七项已有本地对象 → 不可变适配器 | properties 缺失抛 NullPointerException。dispatch false 抛 ProcessResourceNotFoundException。其他沿用原校验。 |
| INTERNAL-002 | ProcessAgentLocalWorkerRouteEvidence#find | 正 tenant、原 topic/schema → Optional<Route> | 非法 tenant 抛 IllegalArgumentException。非法 topic/schema 沿用验证器。缺失/歧义/身份冲突返回空。 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-001 — Validate local inputs

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New internal factory |
| Independent consumer goal | 核对所观察 PS 实例的一份合法本地路由配置。 |
| Parameter ownership and derivation | PS 配置 owner 提供实际绑定、注册、Inbox 与 Shoubing 对象。 |
| Direct/no-new-interface alternative | 直接复用原 validateBindings 的校验，其返回类型缺少可查询快照。 |
| Caller use of result | 同一调用方按原生三元组读取本地证据。 |
| Round trips and failure points | 单次本地校验，无网络；关闭或配置无效拒绝。 |
| Verdict | Add. REQ-001，EVD-001/004。 |

##### Identity and purpose

PS 内部技术入口，供实际配置拥有者构造单份不可变本地快照。调用者负责验证该配置与当前 PS 实例的部署关联，以及外部权威能否组合此证据。

##### Request parameters

七个已有 Java 参数均应是实际本地对象。按顺序传入 ProcessAgentBridgeProperties、WorkerTopicRegistry、AgentResultProducerRegistry、AgentResultInboxProperties、IdpStarterProperties、TrustedIssuerRegistry、IdpServiceOAuth2Client。

##### Success response

持有已验证的不可变 properties、registrations 和 appId 快照的适配器。

##### Error responses

properties 缺失抛 NullPointerException；dispatch false 抛 ProcessResourceNotFoundException；配置及依赖拒绝沿用 validateBindings 的现有异常和约束。

##### Interface logic for frontend and consumers

后续 PS owner 在本地查找前显式调用。关闭 dispatch 时在 OAuth 校验前退出。没有前端消费者。

##### Compatibility and verification

同一有效配置可重复生成等价快照。现有 BridgeResources 调用、默认 Spring 配置和授权行为保持，见 TEST-002/004。

#### 9.2.2 INTERNAL-002 — Find the native tuple

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New internal query |
| Independent consumer goal | 确认原生路由只有一个有效本地注册和桥接。 |
| Parameter ownership and derivation | 调用方提供来自候选的 tenant、topic 和 schema；快照导出 actor。 |
| Direct/no-new-interface alternative | 现有 actor-scoped require 无法独立判定三元组唯一性。 |
| Caller use of result | 仅将证据交给独立所有者核验，与候选要求比较。 |
| Round trips and failure points | 只读本地快照，无网络；未匹配或歧义为空。 |
| Verdict | Add. REQ-002/003，EVD-002/005。 |

##### Identity and purpose

PS 调用方在已验证快照上请求本地证据。按原生路由三元组检索，并对照双方完整服务身份。

##### Request parameters

正值 long tenantId 和精确 String topic/schemaVersion，使用 §7.3 既有语法与长度规则。三项没有默认值。

##### Success response

Optional<Route>，值包含固定域注册摘要及原 AgentDispatchBinding。不存在、非唯一或完整身份冲突时为空。

##### Error responses

非正 tenant 抛 IllegalArgumentException。非法或空 topic/schema 由 ProcessModelValidator 拒绝。

##### Interface logic for frontend and consumers

本地调用幂等。当前部署、集群范围及历史保留由调用方从独立权威验证后才能与快照组合。

##### Compatibility and verification

查找不改变现有 Worker lease、绑定或运行线程。TEST-001/002/003 核对候选组合、拒绝路径及摘要。

## 10. POJO and Data Model Design

`Route` 是私有构造的不可变内部值，含现有 `AgentDispatchBinding` 与 64 位小写十六进制 `registrationSha256`。toString 固定脱敏。直接复用绑定，无序列化或 DTO 转换。

## 11. Database Design

Relational model change: No. 本地查找无数据库访问。

## 12. Frontend Page Design

页面继续由现有前端持有。本步仅 Java 内部契约。

## 13. Design Patterns and Architecture Principles

适配器复用已有配置检查和摘要契约，以最小新增行为保持所有权。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 两个不同服务身份的合法注册和桥接在同一原生三元组时返回空。反证选第一个 actor 并据此过滤注册时出现指定断言错误。 |
| TEST-002 | 唯一且启用时返回 exact binding；不同租户、topic 或 schema 返回空。关闭时无 OAuth 交互。过短租约和 OAuth actor 不匹配按原校验拒绝。 |
| TEST-003 | 独立固定摘要和映射/时序/资源/身份差异断言；Map/Set 顺序不影响摘要，无令牌授权或日志回显。 |
| TEST-004 | 现有桥接、Worker registry、实际 PS 包、基线资源、精确路径和严格文档。I1 单独记录真实部署与运行。 |

## 15. Non-functional and Cross-cutting Design

本地配置上界固定，单次查找最多扫描各 1000 项，达到两个命中即停止。摘要只读取明确配置字段，既有 authorizationKey 内部读取凭证存在性，无令牌值、client secret 或 credentialVersion 进入摘要。URI path 与普通标识符按配置语义属于非秘密元数据；摘要不提供保密性，配置所有者负责避免把秘密填入这些字段。

## 16. Compatibility, Migration, Rollout, and Rollback

A4、A5、原桥接装配、权限清单和 SQL 以当前路径继续运行。适配器仅显式构造使用。回滚撤销 A6 单一提交。

## 17. Alternatives and Decisions

Registry#require 用于验证指定 actor，原生 acquire 的路由维度更宽。复用其验证能力并对完整清单检查唯一性可保持现有通用 Worker 注册行为。

## 18. Risks and Open Questions

I1 需真实部署、各实例配置一致、唯一可消费 Worker 路由、历史绑定保留和独立 A4/A5 权威装配。调用方负责明确区分一份有效配置与运行时集群事实。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9 | TEST-002 |
| REQ-002 | UC-001 | §7/9 | TEST-001/002 |
| REQ-003 | UC-001 | §7/10/15 | TEST-003 |
| REQ-004 | UC-002 | §8/14/16 | TEST-004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | 保留 PS 的传统分层与 bridge 边界。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | 复用桥接校验、Worker 注册和契约摘要编码。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | 沿用 JDK 25、PS、Contract、Shoubing starter、JUnit 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | 技术适配器与私有构造的不可变 Route 证据。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | 在现有配置验证之后核对原生三元组、跨身份唯一和完整服务身份。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | 组合现有 AgentDispatchBinding 和固定摘要，无复制模型。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | JDK 集合及现有 OrchestrationRequestDigests。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | 现有 Duration 的毫秒配置，不签发时间窗。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/14 | 复用已绑定 dispatchEnabled 和现有配置，验证各环境资源一致。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | 用只读技术适配器组合原注册和桥接权威。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | 两份 Java 加三份文档，真实部署、AS 和授权接入独立。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | R1 一处预期反证、G1 有限回归、P1 成品检查和 S1 路径。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | 源码契约完整，I1 的真实部署和历史路由权威仍待独立验收。 | None |

### 20.6 Final verdict

PASS — Ready for user review
