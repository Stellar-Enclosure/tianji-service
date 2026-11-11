# T4-01 受保护 Process START 的试点约束

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Exact routing before the native receipt claim, historical replay and opt-in host assembly |
| Created | 2026-11-11 16:12 Asia/Shanghai |
| Updated | 2026-11-11 16:12 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Four PS production Java sources, two existing PS tests and three review documents |
| Change Surface | Process execution, protected host assembly and finite regression |
| Affected Chapters | §7, §8, §9, §14, §16 |
| Source Requirement | [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 99740bb7c817979e396e8a34fd1054d591ea4486 |
| Amends | None |
| Supersedes | None |
| Depends On | [A5 治理 START](./2026-11-11-15-28-governed-process-start.md#73-detailed-design) |
| Related Specs | [A6 Worker 路由证据](./2026-11-11-15-43-ps-worker-route-evidence.md) |
| Related Plans | [本切片计划](../plan/2026-11-11-16-13-protected-process-start-pilots.md) |

## 1. Summary

在现有受保护 START 中按已验证 tenant 与 BPMN definitionKey 选择受控发布试点。匹配试点的新命令经 A5 激活选择、当前许可、Worker 证明和原子 V13 持久化。旧回执按原请求重放。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ProcessController#start, TianjiProcessAccess#require | 原 POST、权限、scope 和完整 verified actor 已存在，调用普通 ProcessExecutionService#start。 |
| EVD-002 | ProcessExecutionService#startInTransaction, #resolvedStartHash, ProcessCommandReceiptStore#lockIfPresent, #findAppliedStart | 原摘要及回执绑定 release、definition、business、correlation、规范输入与完整 owner。 |
| EVD-003 | ProcessExecutionService#startGoverned, #governedStartInTransaction, #replayGoverned, GovernedProcessStartStore | A5 锁后确认当前指针及 Worker，原 APPLIED 和 V13 同事务，历史回放锚定原修订。 |
| EVD-004 | ProcessSecurityConfiguration#processExecutionService | 现有 servlet 装配默认创建普通 START service，支持受信宿主覆盖 bean。 |
| EVD-005 | ProcessApiErrors#versionConflict, #conflict | 已有通用 409 版本冲突及幂等冲突，响应可以保持原错误 DTO。 |
| EVD-006 | ProcessAgentLocalWorkerRouteEvidence#find, PermissionExecutionGuard#requireExecution | 本地 Worker 配置证据没有跨实例资格。既有入口守卫只返回 actor，无法凭空推导独立短期当前许可。 |
| EVD-007 | ProcessRequestDigestCompatibilityTest, TianjiFixedBpmnStartToolTransport#hostReceiptReference | 客户端固定显式 release，并与历史 START 查询共享摘要，不可静默替换。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 既有 Process Server 项目内扩展一个 service、一个 store 和原 servlet 装配。

## 3. Goals and Non-goals

交付明确默认关闭的试点源码，复用原受保护 POST、请求响应、权限和持久摘要。宿主实际启用、可信当前 caller/Worker 权威、PG／Flowable 和分批部署的一致性由独立证据验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS execution and servlet assembly | Affected | EVD-001–006 | 新的精确试点、原回放与失效关闭 | Java and finite regression | §7, §8, §9, §14, §16 |
| Existing HTTP route and ProcessDB | Context-only | EVD-001/002/003/005 | 原请求响应、权限、SQL 和摘要 | Preserve contracts | §9, §11, §16 |
| Worker and Agent Server | Context-only | EVD-006/007 | 原验证器、合同与客户端 | Current authorities deferred | §15, §18 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 精确试点和装配 | Must | 默认空列表走普通构造器。只匹配同 tenant 和 definition 的成对键。同租户的 definition 和 channel 均唯一，最多 256 项。有试点时要求唯一显式 authority 和唯一落实同配置的执行服务。 | EVD-001/004/006 |
| REQ-002 | 原 POST 不绕过治理 | Must | 新试点命令在 A5 锁后检查显式 release 等于激活目标。失配 409。无指针、无当前权限、无当前 Worker 及事务失败都无法走普通启动。 | EVD-001/003/005 |
| REQ-003 | 摘要、旧回执和历史重放 | Must | 新试点命令保留原 digest 与 V13。历史 V13 使用原 channel 的当前许可与原 release、原输入。旧 APPLIED 普通收据仅允许 exact owner/命令/摘要的只读重放。旧命令值变化冲突，PENDING 拒绝。 | EVD-002/003/007 |
| REQ-004 | 有限门与运行边界 | Must | 真实 service.start 的正反例，装配验证，移除分流后的确定失败。仅 PS 6 Java 和三文档。真实启用及 authority 无本步运行结论。 | EVD-001–007 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Verified Process USER or SERVICE | 原受保护 START 权限和 scope |
| ACTOR-002 | Process host operator | 部署时显式试点与独立权威 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 在原入口启动试点流程 | ACTOR-001 | 完整 actor，精确试点和当前证明 | 同一原 POST 经 A5 事务检查并提交 | 不可用或冲突不创建实例 | 普通 APPLIED 摘要及 V13 来源一致 | REQ-002/003 |
| UC-002 | 精确重放 | ACTOR-001 | 当前授权与完全相同的原请求 | 读取锁定原回执。已有 V13 使用原指针，旧 APPLIED 以原模型校验 | 失配或 PENDING 拒绝 | 不写原生、收据或来源 | REQ-003 |
| UC-003 | 安装并审核受保护试点 | ACTOR-002 | 每个宿主显式且一致的配置 | 绑定、检查唯一 authority 和 service，运行有限门 | 任一装配不明确则启动失败 | 原产品交付默认空试点 | REQ-001/004 |

## 5. Constraints, Assumptions, and Decisions

试点按启动时加载的 immutable list 生效。试点 channel 重映射后仍使用 V13 保存的原 channel 为已有精确回执取得当前许可。请求不提供 channel。作用域从已验证 actor 的 tenant 和既有 definitionKey 计算。现有权限守卫继续在控制器，A5 在 service 层另要求显式当前 proof。

## 6. Project Technology Context

JDK 25、Spring Boot、现有 Flowable 与 JDBC 事务、Jianshen/Shoubing、JUnit 和 Mockito。继续沿用 Process Server 的现有依赖和角色。

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
| Rule 1 | Yes | EVD-003/004 | 原 service 与 store，独立 properties | §7/8 | TEST-001 | PASS |
| Rule 2 | Yes | EVD-001–004 | 保留入口验证及再验权限，绑定精确试点 | §7/9 | TEST-001/002 | PASS |
| Rule 3 | Yes | EVD-002/003 | 复用原不可变命令、回执和 pointer | §7/10 | TEST-001/002 | PASS |
| Rule 4 | Yes | EVD-004 | 复用原 bean 工厂、显式构造注入和同 scope guard | §7/8 | TEST-002 | PASS |
| Rule 5 | Yes | EVD-002/004 | 仅现有 JDK、Spring Boot、JDBC | §7/8 | TEST-002 | PASS |
| Rule 6 | No | EVD-001/002 | 请求 JSON 和摘要 wire 维持原定义 | §7/9 | TEST-002 | N/A |
| Rule 7 | Yes | EVD-004 | 同一个默认空配置，现有 YAML 无差异 | §7/9 | TEST-002 | PASS |
| Rule 9 | Yes | EVD-003 | 复用 A5 事务所有权和 release guard | §7 | TEST-001/002 | PASS |
| Rule 10 | Yes | EVD-003 | 复用 A5 PostgreSQL clock 和有效时间 | §6.3 | TEST-002 | PASS |
| Rule 11 | Yes | EVD-002/003 | 既有 JDBC，原迁移和存储契约 | §7/11 | TEST-002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | A5 grants and Worker proofs | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal | A5 既有 30 s 窗口，事务回放返回前再验 | PostgreSQL clock_timestamp | Original A5 contract | TEST-002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing service START | Extend | REQ-001/002 | 控制器另加一个接口 | service 边界拦截现有调用者 | 一个精确分支 | Use |
| A5 shared fenced transaction | Reuse | REQ-002/003 | 控制器提前查当前 release | 锁后检查才与切换互斥 | 既有事务 | Use |
| Older APPLIED replay | Extend | REQ-003 | 直接调用旧 START | 旧 START 尝试插入，可能新建 | 仅查精确回执 | Use |
| Independent current authority | Reuse | REQ-001/004 | 只读本地 A6 route | 单实例配置无法作当前部署证明 | 既有 A5 authority 端口 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| Default | Existing | 原显式 release | 普通 APPLIED | 原规则 |
| Pilot new START | Host-owned current authority | 显式 release precondition | 普通 APPLIED and V13 | A5 共享栅栏、版本冲突 |
| Pilot replay | Host-owned caller permission | 原命令 | 原收据和来源 | digest 和实际历史身份 |

### 7.1 System Architecture Design

保留原 servlet、身份与权限拦截器。ProcessExecutionService 是最后一个普通 native claim 前的路径。该服务由原 ProcessSecurityConfiguration 装配，使用原引擎和事务管理器。

### 7.2 High-Level Design

显式启动配置使用 `stellar.tianji.process.governed-start.pilots`，每项包含 `tenant-id`、`definition-key` 和 `channel`。相同 tenant 下定义和 channel 均唯一。配置最多 256 项。空列表不初始化 A5 或访问 authority。非空列表要求宿主明确提供唯一 `GovernedStartAuthority`。启动 guard 同时验证唯一执行服务、相同 immutable 配置和同一已注册 authority 实例。

### 7.3 Detailed Design

`start(actor, StartCommand)` 根据完整 actor 的 tenant 和 definitionKey 选择 channel。匹配则把原 release 作为 A5 事务前提传入，现有公开 Java `startGoverned` 保持 V13-only replay。用完整 owner 和 command ID 观察 V13。存在同定义的原 V13 时，从原 channel 取得当前 caller grant。其他命令使用当前配置的 channel。已拥有普通收据只作为是否查 Worker 的提示。进入 A5 共享租户锁后重新锁定收据并重读 V13，确认观察和授权的历史依据完全一致。旧普通 APPLIED 用已固定 release schema 校验规范化摘要，再验证实际实例的定义、business 和 correlation。所有历史路径核验当前 caller 有效时间。

没有收据则重读当前指针。指针移动或者显式 release 不符返回现有 409。授权、当前 Worker、已发布模型和输入均通过才落原 APPLIED 与 V13。已有 receipt 的原请求变化使用现有幂等冲突。任何拒绝都终止本次调用。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedStartPilotProperties.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md` |
| CREATE | `docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md` |
| CREATE | `docs/egon/evidence/2026-11-11-protected-process-start-pilots.md` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Operation | Input | Output |
| --- | --- | --- | --- |
| INTERNAL-001 | ProcessExecutionService#start | 原已验证 actor 和 StartCommand | 原 Snapshot |
| INTERNAL-002 | ProcessSecurityConfiguration | 受信宿主不可变试点和唯一当前 authority | 已验证装配的执行服务 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-001 — Select a pilot on protected START

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Existing internal service call |
| Independent consumer goal | 现有控制器执行经验证的 START。 |
| Parameter ownership and derivation | 原 verified actor 和 StartCommand 来自现有受保护入口。 |
| Direct/no-new-interface alternative | 保留原 Java 方法与 HTTP；在同 service 选择 A5。 |
| Caller use of result | 现有 Snapshot 转换为原 ProcessInstanceDTO。 |
| Round trips and failure points | 复用 A5 事务，锁前历史检查只用于选择许可 scope。 |
| Verdict | Extend. REQ-002/003，EVD-001/002/003。 |

##### Identity and purpose

ProcessExecutionService#start 持有唯一的普通 START 到 A5 分流。继续沿用受保护 POST 和现有 START_API。

##### Request parameters

原完整 ProcessActor 和 StartCommand。输入的 releaseVersion 保留为新试点启动的显式前提。

##### Success response

原 Snapshot。新试点完成原 APPLIED 和 V13，历史精确请求得到原 instance。

##### Error responses

Existing 409 version and idempotency conflicts keep their structured error codes. Missing activation, invalid authority, mismatched route and expired evidence reject the request before any fallback write.

##### Interface logic for frontend and consumers

原 URL、字段、响应、幂等命令 ID 和权限契约。已有回执即使 active pointer 或试点 channel 改变，也使用原 release。

##### Compatibility and verification

G1 checks the existing controller, service, normalized digest, original receipt and actor permission suites. G2 checks the actual Process Server artifact and its unchanged route and permission contracts.

#### 9.2.2 INTERNAL-002 — Assemble the protected service

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Extend existing host factory |
| Independent consumer goal | 宿主准确启用一组有限试点。 |
| Parameter ownership and derivation | 可信宿主加载配置并注册当前 authority。 |
| Direct/no-new-interface alternative | 复用现有 ProcessSecurityConfiguration 工厂。 |
| Caller use of result | 正常受保护入口使用已验证服务。 |
| Round trips and failure points | 启动时校验 config/bean，当前权限仍在调用时核对。 |
| Verdict | Extend. REQ-001，EVD-003/004/006。 |

##### Identity and purpose

ProcessSecurityConfiguration 持有受保护 START 的 servlet host 装配。

##### Request parameters

绑定 `stellar.tianji.process.governed-start.pilots` 的 tenant-id、definition-key、channel。显式配置要求唯一的 `GovernedStartAuthority`。

##### Success response

原 ProcessExecutionService，精确试点和唯一权威实例已核对。空列表使用旧构造器。

##### Error responses

An invalid or duplicate route and a missing, ambiguous or different registered authority fail at application initialization. The empty configuration continues the existing default assembly.

##### Interface logic for frontend and consumers

The host operator supplies the settings at deployment. Existing callers continue to send the original request and receive the existing response, without a channel field or a new endpoint.

##### Compatibility and verification

ProcessApiConfigurationTest 覆盖默认、精确构造、替换 bean、重复 route 和数量边界。

## 10. POJO and Data Model Design

配置值为 immutable Pilot 与完整 Scope。保留原 StartCommand、GovernedStartCommand 和 GovernedStartResult。内部 StartOutcome 承载既有 Snapshot 和存在的原 pointer。普通历史回执没有 pointer，仅在正常受保护形状返回 Snapshot。

## 11. Database Design

Relational model change: No. 查询既有完整 owner 命令键的 `EXISTS` 是有界提示。原 A5 写事务和 V12/V13 关系与角色不变。历史 replay 的行锁只用于核对，无持久数据改动。

## 12. Frontend Page Design

原页面合同和接口。

## 13. Design Patterns and Architecture Principles

沿用既有 service 和 authority 端口。试点配置在正常 START 的持久申请前强制选择原 A5，实现单一 native 写入口。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 临时源码强制试点调用回普通 START，原 missing Worker case 必须因为没有抛出 AccessDeniedException 而失败。 |
| TEST-002 | 53 项有限 service、装配、受保护控制器、原回执、摘要与 actor 门。真实 PS 包及不变合同复核。 |

## 15. Non-functional and Cross-cutting Design

当前 authority 由宿主提供，保留 A5 数据库时钟边界。既有权限执行、角色/issuer 校验、限时和审计 digest 延续。所有可接收该 POST 的副本须以同一试点快照和独立权威切换。混跑启用前版本会绕过此机内栅栏，需操作者在启用前证明入口集合一致。回滚选 A4 前任并保留试点门。

## 16. Compatibility, Migration, Rollout, and Rollback

默认无试点，普通 START、原 schema 启动和显式 source constructor 继续沿用。配置后同一 release 响应和历史查询摘要保持兼容。受控试点当前不启用。部署后暂停调用可由 authority 拒绝，避免将删除试点作为临时降级路径。

## 17. Alternatives and Decisions

在控制器读取当前 release 再调用普通 START 会有切换竞争。新 POST 会留下原 POST 的试点绕过。使用现有 service 在同一 A5 栅栏内锁后复核。

## 18. Risks and Open Questions

真正启用需要可信且独立的当前 caller/Worker authority、全实例试点一致、历史 Worker 绑定保留和真实 HTTP/PG/Flowable 验收。现有 A6 只是 local evidence。此源码切片不依赖新建的 PG 操作者测试。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-003 | §7.1/7.2/16 | TEST-002 |
| REQ-002 | UC-001 | §7.3/9/11 | TEST-001/002 |
| REQ-003 | UC-001, UC-002 | §7.3/10/11 | TEST-002 |
| REQ-004 | UC-003 | §8/14/18 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 原 PS 内部 service | G0 |
| MC-REUSE-001 | Applicable | PASS | EVD-001–007 | 权限、A5、digest 和普通回执 | G1 |
| MC-DEP-001 | Applicable | PASS | §6 | 既有依赖 | S1 |
| MC-NAME-001 | Applicable | PASS | §8/9 | 精确 Pilot 和既有 START | G1 |
| MC-VALID-001 | Applicable | PASS | §7/9 | 角色、scope、发布前提及全 owner | G1 |
| MC-MODEL-001 | Applicable | PASS | §10 | 原不可变合同 | G1 |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无业务 DTO 转换 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新业务日志 | None |
| MC-BEAN-001 | Applicable | PASS | §7.2 | 拒绝 legacy 或 ambiguous override | G1 |
| MC-UTIL-001 | Applicable | PASS | §6 | 现有 JDK、JDBC 与测试依赖 | G1 |
| MC-JSON-001 | Not applicable | N/A | §9 | 现有 wire | S1 |
| MC-TIME-001 | Applicable | PASS | §6.3 | 复用 A5 clock | G1 |
| MC-CONFIG-001 | Applicable | PASS | §7.2 | 空试点，显式 authority | G1 |
| MC-PATTERN-001 | Applicable | PASS | §13 | 单一 native 入口 | R1 |
| MC-SCOPE-001 | Applicable | PASS | §8/11 | 9 路径 | S1 |
| MC-TEST-001 | Applicable | PASS | §14 | 确定反证和有限回归 | R1/G1 |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 真实运行独立 | I1 |

### 20.6 Final verdict

PASS — Ready for user review
