# T4-01 A3 PostgreSQL 历史初始化修正

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-15-05-candidate-review-postgres-history.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Fix |
| Complexity | Simple |
| Complexity Drivers | 一个已有操作者测试的 Flyway 历史初始化与检查 |
| Created | 2026-11-11 15:05 Asia/Shanghai |
| Updated | 2026-11-11 15:05 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 已有 A3 PostgreSQL 用例的空 schema 创建与历史检查 |
| Change Surface | One Process Server test source and three review documents |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | 主计划 §19 T4-01 A3 的 V10 至 V11 PostgreSQL 验收 |
| Baseline Revision | 13b59a7dda2aeb013eff1c101d4f3f9705cba316 |
| Amends | [A3 PostgreSQL 验收](2026-11-06-08-11-candidate-review-postgres-acceptance.md#73-detailed-design) |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [历史初始化修正计划](../plan/2026-11-11-15-06-candidate-review-postgres-history.md) |

## 1. Summary

令原 A3 操作者用例从显式创建的空 schema 应用 V1 至 V10，再追加 V11。两个阶段均直接比较按安装顺序排列的完整 SQL 历史。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | PlatformReleaseCandidateReviewPostgresTest.upgrades_v10_and_serializes_real_independent_review_writes / flyway | 原夹具调用 baseline() 并指定版本 0。 |
| EVD-002 | ProcessApiSchemaVerifier.laterMigrations | 查询纳入空版本和 1–7 之外的条目，随后只接受从 8 开始的精确 SQL 迁移序列。 |
| EVD-003 | Flyway 11.15.0 DbSchemas / FluentConfiguration | 已有版本支持关闭 createSchemas 和 baselineOnMigrate，DbSchemas 的 schema 创建路径写入历史标记。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 修正一个已存在的 PostgreSQL 操作者测试。

## 3. Goals and Non-goals

使原操作者测试创建严格验证器可接受的历史，并在 V10 与 V11 都报告历史漂移。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| A3 operator test | Affected | EVD-001–003 | 由测试 owner 创建空 schema，Flyway 只应用 SQL，读取完整历史 | 初始化与验证设计 | §7, §8, §14 |
| ProcessDB production history | Context-only | EVD-002 and existing SQL | 以已有严格历史为预期 | 合同引用 | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 使用空 schema 运行原 Flyway 升级 | Must | 已有新库 guard 后显式创建 schema，从 V1 应用，V10 后只新增 V11 | EVD-001/002/003 |
| REQ-002 | 核对全部历史 | Must | V10 和 V11 均按 installed_rank 比较 1 到目标的版本与 SQL 类型，且保存原快照相等断言 | EVD-001/002 |

### 4.2 Use-case analysis

| Actor ID | Role | Scope |
| --- | --- | --- |
| ACTOR-001 | PostgreSQL 操作者 | 已授权的隔离 loopback 新库与临时 runtime 角色 |

| ID | Use case/goal | Primary actor | Trigger | Preconditions | Outcome | Failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 验证真实 V10 至 V11 历史 | ACTOR-001 | 现有独立操作者选择器 | 目标库无 Process schema，既有门禁和临时角色就绪 | 先观察 10 行，随后 11 行，旧历史快照相同 | 额外行、错序、类型不符均断言失败 | 进入已有权限与并发用例 | REQ-001/002 |

## 5. Constraints, Assumptions, and Decisions

在已有 guard 之后使用当前测试 owner。由操作者保留隔离库的运行和清理权。

## 6. Project Technology Context

Java 25、JUnit、已有 Flyway 测试依赖、Spring JDBC、PostgreSQL 历史目录。

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
| Rule 1 | Yes | EVD-001–003 | 保留测试类，增加与断言目的相符的 assertSqlHistory。 | §7/8 | TEST-001/002 | PASS |
| Rule 2 | Yes | EVD-001–003 | 在已有空库和 schema guard 后创建，按安装序断言每一条历史。 | §7/8 | TEST-001/002 | PASS |
| Rule 3 | No | EVD-001–003 | 通过已有 JdbcTemplate 取得字符串序列。 | §7/8 | TEST-001/002 | N/A |
| Rule 4 | No | EVD-001–003 | 直接调用 JUnit、JdbcTemplate 和 Flyway。 | §7/8 | TEST-001/002 | N/A |
| Rule 5 | Yes | EVD-001–003 | 复用 Java IntStream 和现有 test 依赖。 | §7/8 | TEST-001/002 | PASS |
| Rule 6 | No | EVD-001–003 | 历史值使用 SQL 列和 Java 字符串。 | §7/8 | TEST-001/002 | N/A |
| Rule 7 | No | EVD-001–003 | Flyway 构建器属于单一显式操作者用例。 | §7/8 | TEST-001/002 | N/A |
| Rule 9 | No | EVD-001–003 | 按现有线性夹具顺序直接核对。 | §7/8 | TEST-001/002 | N/A |
| Rule 10 | No | EVD-001–003 | 按 installed_rank 检查，无时钟或时间数据变动。 | §7/8 | TEST-001/002 | N/A |
| Rule 11 | Yes | EVD-001–003 | 保留原 PostgreSQL 测试存储边界，复用 V1–V11 和现有 JDBC。 | §7/8 | TEST-001/002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: 只比较 Flyway 版本、类型和 installed_rank，现有到期与事务时序合同继续由原操作者夹具验收。

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap and verdict |
| --- | --- | --- | --- | --- |
| 显式空 schema 与关闭 Flyway 隐式标记 | Modify | REQ-001 | 原 baseline 0 和自动 schema 创建 | 两者会生成严格历史拒绝的行，使用现有 owner 的空 schema。 |
| 完整有序 SQL 历史断言 | Add | REQ-002 | 迁移数量与末版本 | 完整行断言能发现额外标记，复用已有 JdbcTemplate。 |

| Path | Network calls | Client states | Failure points |
| --- | --- | --- | --- |
| Source verification | None | Existing compile and finite test selectors | Patch scope, bytecode and finite regression |
| Operator acceptance | JDBC loopback | Guarded schema owner and temporary runtime role | Migrations and exact full history |

### 7.1 System Architecture Design

沿用现有 JUnit 操作者测试和 Process owner JDBC。

### 7.2 High-Level Design

通过已存在的新库检查后创建目标 schema，按顺序迁移至 V10 和 V11，并在两个阶段检查完整历史。

### 7.3 Detailed Design

显式 CREATE SCHEMA 后，Flyway 构建器关闭 createSchemas 和 baselineOnMigrate，直接应用原 SQL。assertSqlHistory 按 installed_rank 读取每行版本及类型，与 1 至目标版本的精确 SQL 行比较。保留原快照断言，V11 后的先前历史行应逐值相等。

## 8. Package Structure and Code File Tree

唯一修改的 Java 路径为 `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java`。实施计划锁定该路径和三份审查文档。

## 9. Interface Definitions

沿用原方法名和 opt-in 属性。

## 10. POJO and Data Model Design

使用 JdbcTemplate 读取字符串序列。期望值由 IntStream 按版本生成。

## 11. Database Design

隔离库夹具使用现有 V1–V11，沿用 ProcessDb 历史表、现有物理验证与权限检查。

## 12. Frontend Page Design

本步骤属于 Process Server PostgreSQL 测试。

## 13. Design Patterns and Architecture Principles

复用现有线性操作者夹具，把两个相同的历史检查提取为一个直接断言方法。

## 14. Test Design

| ID | Test | Expected observation |
| --- | --- | --- |
| TEST-001 | Java 25 编译、精确字节码、A3 有限测试和旧夹具静态反证 | 两项 opt-in 存在，旧 baseline 0 会进入严格筛选，修正后两阶段均调用历史断言。 |
| TEST-002 | 独立 PostgreSQL 操作者执行 | 实际历史分别为 10 和 11 条 SQL，旧历史行相等，后续原验收继续执行。 |

## 15. Non-functional and Cross-cutting Design

沿用测试查询超时与临时角色，历史断言只记录版本和类型。

## 16. Compatibility, Migration, Rollout, and Rollback

该修正随操作者测试源码交付。实际运行由隔离测试 owner 完成，源码可用独立提交回退。

## 17. Alternatives and Decisions

显式创建空 schema 让 Flyway 以实际 SQL 记录迁移，直接符合已有严格验证器。

## 18. Risks and Open Questions

实际 PostgreSQL 历史和后续并发用例由操作者提供 I1 结果。

## 19. Traceability Matrix

| Requirement | Use case | Design | Test |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7.2/7.3 | TEST-001/002 |
| REQ-002 | UC-001 | §7.3 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | 现有 Process Server 测试类负责库初始化。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | 复用现有 URL guard、Flyway 和 JdbcTemplate。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | 使用现有模块依赖。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | assertSqlHistory 的名称与断言范围一致。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | 比较完整有序 SQL 行，0 和空版本均无法通过。 | None |
| MC-MODEL-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | JDK IntStream 生成精确期望。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-TIME-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-PATTERN-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | 只修改原操作者测试及对应审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | 已有历史验证器和 A3 有限测试，另设操作者执行证据。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | 编译与静态反证足以验收本次源码，I1 由操作者执行。 | None |

### 20.6 Final verdict

PASS — Ready for user review
