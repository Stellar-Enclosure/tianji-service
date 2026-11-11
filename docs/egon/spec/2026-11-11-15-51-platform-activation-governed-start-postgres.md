# T4-01 A4/A5 发布切换与受控 START PostgreSQL 操作者验收

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Disposable PostgreSQL migration history and one native START/activation lock interleaving |
| Created | 2026-11-11 15:51 Asia/Shanghai |
| Updated | 2026-11-11 15:51 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Two Process Server test sources and three review documents |
| Change Surface | Test source and operator evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 423b54b68081172dcc3d5c998ed7bd45f0995a77 |
| Amends | None |
| Supersedes | None |
| Depends On | [A4 发布激活](./2026-11-11-15-08-platform-release-activation.md#73-detailed-design), [A5 治理 START](./2026-11-11-15-28-governed-process-start.md#73-detailed-design) |
| Related Specs | [A3 PostgreSQL](./2026-11-06-08-11-candidate-review-postgres-acceptance.md) |
| Related Plans | [本验收实施计划](../plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md) |

## 1. Summary

增加一次性 PostgreSQL 与真实 Flowable 的操作者用例。通过两份已独立审阅的签名 BPMN 发布，检查 V12 切换、V13 START 来源、已有实例、精确重放及同事务失败回滚。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | PlatformReleaseCandidateReviewPostgresTest#upgrades_v10_and_serializes_real_independent_review_writes | 显式空 schema 和纯 SQL 历史可经过 V11 门槛。 |
| EVD-002 | ProcessServerPostgresTest#explicitTestUrl, #explicitRuntimeRole, #verifyAgentDispatchAdapter, #verifyReleaseTransactions | 既有 loopback 与角色守卫、Flowable 初始化、原生单 Worker 模型及相同资源键的不同发布版本。 |
| EVD-003 | ProcessReleaseService#publish, #getForActivation, PlatformReleaseCandidateReviewService#revalidateForActivation | 真正发布后重读原生模型，签名、独立审阅和 owner 发布证据共同约束激活。 |
| EVD-004 | PlatformReleaseActivationStore#appendInTransaction, PlatformReleaseTenantFence | 同租户排它事务锁串行化 CAS 和精确前任回滚。 |
| EVD-005 | ProcessExecutionService#startGoverned, #governedStartInTransaction, #replayGoverned | 共享租户栅栏，原生实例、原 APPLIED 回执和 V13 来源共同提交。 |
| EVD-006 | PlatformReleaseSignatureVerifierTest#CANDIDATE, PlatformReleaseArtifactValidator#candidateSha256, #canonical | A2 公开候选提供规范 JSON 形状。新签名可绑定实际发布的每份独立摘要。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 两份新测试复用既有 Process Server、Flyway 和原生 BPMN 夹具。

## 3. Goals and Non-goals

交付明确选择的 A4/A5 组合测试和可审查的源码证据。真实运行由获授权的操作者在隔离主库验收。产品的当前 Agent 部署权威、IAM、HTTP 接入和完整 S07 使用独立验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS test sources | Affected | EVD-001–006 | 双开关、现有迁移历史和真实引擎组合 | Test design | §7, §8, §14 |
| PS production | Context-only | EVD-003–005 | 原 API、服务与事务 | Existing contract | §7, §9, §16 |
| ProcessDB | Context-only | EVD-001/004/005 | 一次性 schema 和测试触发器 | Existing migrations | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 隔离及连续迁移 | Must | 精确 URL、额外开关和独立受限角色。V1–V13 历史仅为 SQL，V11→V12→V13 每次新增一条，既有行一致，V12/V13 未授权时 readiness 拒绝。 | EVD-001/002 |
| REQ-002 | 真实发布切换及回滚 | Must | 实际部署的 BPMN v1/v2 原生定义和部署 ID 不同，两份候选经 Ed25519 与独立审阅接受。并发 CAS 同一预期修订只产生一个赢家，错误回滚目标被拒。 | EVD-003/004/006 |
| REQ-003 | START 来源、重放和故障 | Must | 旧观察竞争失败，Worker 不符拒绝。一个已经开始写入的 START 持有共享栅栏，rollback 排它锁等待。其提交指向 v2，回滚后的新实例指向 v1。历史重放沿用原实例和修订。V13 插入故障前见到真实原生与 APPLIED 行，随后三处均无残留。普通 START 保持既有命令路径。 | EVD-004/005 |
| REQ-004 | 可审查门槛 | Must | JDK 25 及有限回归、编译后的精确测试方法和开关，默认跳过且非法 URL 在 JDBC 前失败。实际操作者证据单独记录。 | EVD-001/002 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Source reviewer | JDK 25 and offline finite tests |
| ACTOR-002 | Authorized integration operator | Dedicated fresh loopback ProcessDB and limited role |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 验证原生版本切换和回滚 | ACTOR-002 | 双开关和有效隔离主库 | 迁移、发布、签名、审阅、竞争切换、START、回滚、重放 | 断言失败即测试失败，释放连接、栅栏和触发器 | 三次修订与原 APPLIED 事实对应 | REQ-001/002/003 |
| UC-002 | 复核源范围及默认门 | ACTOR-001 | 精确基线和本地依赖 | 检查 Java、签名、测试发现、负向 URL 和变更路径 | 任一门槛不符即停止提交 | 保留独立 I1 | REQ-004 |

## 5. Constraints, Assumptions, and Decisions

单独选择一次性 PostgreSQL 方法。测试角色同时具备 V12 追加和 V13 追加，以同一正常 Flowable JDBC 数据源覆盖组合事务。V12/V13 均检查不可变角色权限。签名、PDP 与注册证据由场景提供，测试中不联网。测试唯一写入目标为显式隔离库。

## 6. Project Technology Context

JDK 25、Spring Boot 既有测试运行时、Flowable BPMN、Flyway、JUnit、Spring JDBC 和 PostgreSQL。沿用当前 Process Server POM。

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
| Rule 1 | Yes | EVD-001/002 | 现有 Test、Scenarios 命名 | §7/8 | TEST-001 | PASS |
| Rule 2 | Yes | EVD-001/002/005 | 复用一次性数据库、角色及封闭命令 | §7/9 | TEST-001/002 | PASS |
| Rule 3 | Yes | EVD-003/006 | 既有不可变 Release、Scope、签名与回执 | §7/10 | TEST-001/002 | PASS |
| Rule 4 | No | EVD-002 | 无新生产 Bean | §8 | TEST-001 | N/A |
| Rule 5 | Yes | EVD-001/002/006 | JDK、JUnit、Spring JDBC | §7/8 | TEST-001 | PASS |
| Rule 6 | Yes | EVD-006 | 现有 Jackson 与规范签名 JSON | §7/9 | TEST-001 | PASS |
| Rule 7 | Yes | EVD-001 | JUnit 独立显式开关 | §9/14 | TEST-001 | PASS |
| Rule 9 | Yes | EVD-002/004/005 | 操作者夹具复用产品事务 | §7 | TEST-002 | PASS |
| Rule 10 | Yes | EVD-004/005 | 有限等待、数据库时间和 UTC 秒签名 | §6.3 | TEST-001/002 | PASS |
| Rule 11 | Yes | EVD-001/002 | 只读原生产迁移字节。测试在一次性 schema 清理触发器 | §11 | TEST-001/002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Current grants and detached signature | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal | Fixture grants 20 s. Signature expires at exclusive deadline | PostgreSQL clock_timestamp, seconds for Ed25519 claims | Existing A2/A4/A5 contract | TEST-001/002 |
| TIME-002 | Backend, latch and future waits | Duration | System.nanoTime and java.util.concurrent.TimeUnit | Not persisted | Internal | Not applicable: elapsed | Not applicable: internal | Bounded by test, JDBC and store deadlines | Monotonic nanoseconds | Existing test behavior | TEST-002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| SQL history and schema owner | Reuse | REQ-001 | A3 marker-free pattern | 初始化 V11 后进入 V12/V13 | 独立一次性入口 | Use |
| Native BPMN and signed candidate | Reuse | REQ-002 | 现有单 Worker fixture 与 A2 JSON 形状 | 签名必须绑定不同发布资源的实际摘要 | 改变一个 timer 字面量并分别签名 | Use |
| Lock and late failure | Extend | REQ-003 | 实际 V13 插入和 PostgreSQL advisory lock | 独立读证人验证 pending 和回滚边界 | 一个 test trigger | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| G1/G2 | 无数据库连接 | 规范签名及 JUnit discovery | 源码和报告 | 非法 URL 在 JDBC 前拒绝 |
| I1 | 一次性 PostgreSQL | runtime 执行，owner 观察 | V12 修订、APPLIED 与 V13 来源 | 旧观察、共享锁等待、失败回滚 |

### 7.1 System Architecture Design

操作者使用现有 Process Server 的 Spring 配置连接一次性 PostgreSQL。独立 owner/JDBC 检查 schema 和 backend，受限 runtime 通过同一个 Flowable 数据源执行发布、候选审阅、激活和 START。

### 7.2 High-Level Design

先创建空 Process schema 并生成 SQL-only V1–V11 历史。其后在同一 schema 预置 Flowable 及独立 Outbox 历史。按顺序升级 V12、V13 并验证权限门。相同 BPMN 定义键保留单 Worker 绑定，第二次部署只改 PT5M 为 PT6M，以两个真实部署 ID、定义 ID、版本和摘要作证。

### 7.3 Detailed Design

一次性 Ed25519 密钥对两份规范候选签名。以不同完整 actor 提交、签名、接受、操作和 START。先激活 v1，令一个 START 停在事务前 Worker 证明，两个激活命令竞争修订 2 后恢复旧观察并确认拒绝。随后 V13 BEFORE INSERT 触发器使实际 START 持共享锁等待。由独立连接确认同一锁键的 rollback 排它等待。释放后验证 v2 START 和回滚修订 3。另一个触发条件必须先在同事务读到原生及 APPLIED 行，然后抛错，确认 runtime/history/receipt/provenance 均未提交。验证精确重放、变更命令冲突和普通 START。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformActivationGovernedStartPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformActivationGovernedStartPostgresScenarios.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-11-platform-activation-governed-start-postgres.md` |

## 9. Interface Definitions

操作者选择 `PlatformActivationGovernedStartPostgresTest#rolls_back_the_pointer_and_keeps_each_native_start_pinned_to_its_revision`。测试属性 `tianji.process.postgres.activation-start=true`、`tianji.process.postgres.test.url` 和 `tianji.process.postgres.test.runtime-user`。服务调用复用现有内部 Java 入口。

## 10. POJO and Data Model Design

测试私有 SignedCandidate 聚合已验证的规范候选、信任材料和 A2 验证结果。产品的 Release、Scope、ActivationReceipt、GovernedStartResult、普通 START 记录直接用于断言。

## 11. Database Design

Relational model change: No. 正常部署迁移保持原字节。一次性 V13 触发器只匹配两条测试命令，用于暂停或强制事务回滚，并在夹具结束时删除。候选、审阅、V12 和 V13 只授予测试角色 SELECT/INSERT。

## 12. Frontend Page Design

无页面接口。

## 13. Design Patterns and Architecture Principles

真实产品存储、Flyway 和 Flowable 调用组合在测试入口。模拟的当前权威仅限固定 actor、候选和有限期限。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | JDK 25 编译，A2 规范签名及两份 BPMN 校验，有限 owning 测试，默认跳过，非法 URL 在连接前失败，包与源码边界。 |
| TEST-002 | 真实 PostgreSQL 和 Flowable 操作者选中一次，验证 V11–V13，双发布、竞争、回滚、原生证据及重放。 |

## 15. Non-functional and Cross-cutting Design

仅用内存测试签名密钥与假输入。报告记录断言及运行结果。独立等待、SQL、事务、全测试时长均有限。

## 16. Compatibility, Migration, Rollout, and Rollback

夹具默认关闭。交付时现有配置、迁移与 API 不需要同步升级。撤销此单一测试提交即可移除操作者入口。

## 17. Alternatives and Decisions

既有通用 Process 测试使用平台 baseline 0，纯 SQL 历史是 V12/V13 verifier 所需条件。完整 expense 包括其他平台切片的 DMN、Case 和多 Worker 条件。此验收使用已有实际发布的单 Worker BPMN，覆盖 A4/A5 的组合事务。

## 18. Risks and Open Questions

需要独立 PostgreSQL 主库运行报告。测试内权威不构成产品 IAM、Agent 发布权限、正式 Worker 注册或全量模型包的验收。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7.1/7.2/11 | TEST-001/002 |
| REQ-002 | UC-001 | §7.2/7.3 | TEST-001/002 |
| REQ-003 | UC-001 | §7.3/9/11 | TEST-002 |
| REQ-004 | UC-002 | §8/14/18 | TEST-001 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 原 PS 测试结构 | G0 |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | 现有 guard、模型、产品入口 | G1 |
| MC-DEP-001 | Applicable | PASS | §6 | 既有依赖 | S1 |
| MC-NAME-001 | Applicable | PASS | §8/9 | 精确选择 | G2 |
| MC-VALID-001 | Applicable | PASS | §7/9 | 连接前 guard | G2 |
| MC-MODEL-001 | Applicable | PASS | §10 | 现有不可变合同 | G1 |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无业务 DTO 转换 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无业务日志 | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | 无新 Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6 | 既有 JDK 和测试依赖 | G1 |
| MC-JSON-001 | Applicable | PASS | §7.3 | 既有 canonical JSON 与实际 Ed25519 | G1 |
| MC-TIME-001 | Applicable | PASS | §6.3 | 数据库时钟和单调等待 | I1 |
| MC-CONFIG-001 | Not applicable | N/A | §9 | 测试 JVM 属性 | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | 原产品事务组合 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/11 | 五条路径 | S1 |
| MC-TEST-001 | Applicable | PASS | §14 | 有限结果与 I1 | G1/G2/I1 |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 实际 DB 独立执行 | I1 |

### 20.6 Final verdict

PASS — Ready for user review
