# T4-01 A3 PostgreSQL 候选审阅操作者验收

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 一个受控数据库验收用例，复用现有生产事务与读取路径 |
| Created | 2026-11-06 08:11 Asia/Shanghai |
| Updated | 2026-11-06 08:11 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | PS 仅测试源码和评审文档；真实 PostgreSQL 由操作者另行授权 |
| Change Surface | Disposable ProcessDB operator test and evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | 主计划 §19 T4-01 的 U+I，以及 A3 已有 Spec TEST-007 的独立隔离验收 |
| Baseline Revision | 28ad7d522a214adf7544545c7fe9eedf3e645ae5；A3 621fa10d0a4d52473368251accd93b14f3718719 |
| Amends | None |
| Supersedes | None |
| Depends On | [A3 §11、§14](2026-11-06-07-46-platform-release-candidate-review.md#11-database-design) |
| Related Specs | None |
| Related Plans | [PostgreSQL 操作者验收](../plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md) |

## 1. Summary

准备一个显式操作者测试。它将在全新 loopback ProcessDB 中通过真实 Flyway 应用 V1–V10 后推进 V11，验证实际目录与临时受限角色，再使用两条 PostgreSQL 会话演练独立审阅、超时回滚和重放。

## 2. Background and Current State

| Evidence ID | Source | Fact |
| --- | --- | --- |
| EVD-001 | [A3 finite evidence](../evidence/2026-11-06-platform-release-candidate-review.md) | A3 签名、目录、事务和角色的有限测试使用合成 authority、模拟 PostgreSQL 目录或 JDBC。 |
| EVD-002 | ProcessServerPostgresTest.explicitTestUrl/explicitRuntimeRole | 已有精确 loopback tianji_it_ 数据库与独立临时角色验证。 |
| EVD-003 | ProcessApiSchemaVerifier.verifyPlatformCandidateReviews/hasCaseMigration、V11、PlatformReleaseCandidateReviewStore | 现有程序明确检查 exact V11、表和列权限、作用域与写后时间，需实际数据库确认。 |
| EVD-004 | PlatformReleaseSignatureVerifierTest、ProcessReleaseService.get/usesStorage | 已有公开签名向量和实际 ProcessDB release 读取；测试可通过合成配置绑定真实源，不必运行引擎。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 在现有 PS 模块添加一项显式测试和一个包内夹具桥接类型。已有代码、迁移及任何业务示例均不改动。

## 3. Goals and Non-goals

源码需准备一个可独立选择的真实 PostgreSQL 验收。后续运行需要操作者提供专用 DB、临时角色和授权。当前源码阶段只编译测试及验证 opt-in 注解。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS operator test | Affected | EVD-001/002/003/004 | 真实 PG 物理存储与并发验证源码 | 精确隔离门禁、两条连接、回放及权限断言 | §7, §8, §14 |
| Production JDBC/storage and V11 | Context-only | A3 §11、ProcessApiSchemaVerifier | 用例执行已有功能，迁移和程序字节保持 | 沿用已有合同，不新增表或字段 | §7, §14 |
| API, frontend, live authority and deploy | Not applicable | EVD-002/004 | 没有新增公开入口或运行启用 | 只记录不在此测试中的证据 | §9, §12, §16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 显式隔离选择并升级 V10→V11 | Must | 专用方法开关与受控 loopback 新库；实际一次新增 V11；既有历史行保持；缺 V11 或缺权限时 A3 readiness 拒绝，既有历史读取继续接受 V10/V11 | EVD-001/002/003 |
| REQ-002 | 验证物理目录及追加角色 | Must | 受限角色成功、owner 失败；列 UPDATE/REFERENCES 可控漂移被拒；角色禁止实际 UPDATE/DELETE/TRUNCATE，异渠道审阅 FK 失败 | EVD-003 |
| REQ-003 | 证明实际双连接审核、回滚及重放 | Must | 公开向量与真实 owner SQL 读取；DB 锁视图见两个不同 PID；恰一个终态、历史精确重放、另一命令失败、同命令改值失败；写入等待后到期回滚 | EVD-001/003/004 |

### 4.2 Use-case analysis

| Actor ID | Role | Scope |
| --- | --- | --- |
| ACTOR-001 | 授权的本地数据库操作者 | 预置空的隔离库与临时非 owner 角色 |
| ACTOR-002 | 合成签名发布者、签名责任人及两个审阅者 | 专用候选 scope；提供确定的 fixture grants |

| ID | Use case/goal | Primary actor | Trigger | Preconditions | Outcome | Failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 检查实际升级与角色 | ACTOR-001 | 显式选中测试方法 | 独立 loopback 空库、预先创建的临时角色 | V11 迁移、历史和 role constraints 通过 | 不满足门禁或物理目录不合格即失败 | 保留隔离证据由操作者清理 | REQ-001/002 |
| UC-002 | 检查独立审阅竞争 | ACTOR-002 | V11 和有限角色已就绪 | 有效签名、合成 owner row 和当前授权 | 一次回滚、一次唯一赢家及精确回放 | 真实等待、权限、时限或 scoped FK 漂移失败 | 候选一条且决定一条 | REQ-002/003 |

## 5. Constraints, Assumptions, and Decisions

通过已有 Process test URL/role 方法校验输入，并增加专门 tianji.process.postgres.candidate-review=true。当前源码轮无运行数据库授权。操作者另行提供 pg_locks 基础视图读取、数据库 schema owner 能力及临时 runtime 登录。授权仅针对隔离的证据库和该角色。

## 6. Project Technology Context

使用当前 PS Java 25/JUnit 6、Flyway test scope、Spring JDBC 及 PostgreSQL 驱动。复用既有 PS 事务和 SQL，无新依赖或生产装配。

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
| Rule 1 | Yes | EVD-002/004 | Test、Scenarios 与不可变 ReviewOutcome 分角色 | §8 | TEST-001 | PASS |
| Rule 2 | Yes | EVD-002/003 | 复用现有 URL/role 检查、签名和真实 service validation | §7 | TEST-001/002 | PASS |
| Rule 3 | Yes | EVD-004 | ReviewOutcome record，复用正式 A3 value contracts | §8 | TEST-001 | PASS |
| Rule 4 | No | PS tests | 纯 JUnit，无 Bean 或业务 logger | §8 | TEST-001 | N/A |
| Rule 5 | Yes | 既有 JDK/JDBC/Flyway/Mockito | 复用当前 test dependencies | §6 | TEST-001 | PASS |
| Rule 6 | Yes | PS 现有 Jackson 3 | 只投影已验签模型的输入 map；无 wire 变更 | §7 | TEST-001/002 | PASS |
| Rule 7 | No | 无环境配置改动 | 独立 test-only flag | §7 | TEST-001 | N/A |
| Rule 9 | No | 线性测试操作者流程 | 两个固定测试类型，直接复用生产服务 | §13 | TEST-001 | N/A |
| Rule 10 | Yes | EVD-003 | DB Instant 与本地 Duration/nanoTime 分离 | §6.3 | TEST-002 | PASS |
| Rule 11 | Yes | AGENTS 迁移冻结、A3 §6.2/11 | 本步骤只执行测试源码设计，复用已提交普通 JDBC 和既有 V1–V11；没有新的偏离、生产 SQL、MP 或迁移部署 | §11/16 | TEST-001/002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | authority deadline, databaseNow | Occurred instant | Instant | Not persisted | Internal | UTC instant, no zone conversion | No user display | End exclusive; no DST conversion | PostgreSQL clock_timestamp and short fixture grant | Existing A3 half-open contract | TEST-002 |
| TIME-002 | lock wait, polling and future bounds | Duration | Duration | Not persisted | Internal | Monotonic elapsed time independent of zone | No user display | Bounded await and no wall-clock subtraction | System.nanoTime and bounded future wait | Existing 5 s query/lock timeout | TEST-002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap and verdict |
| --- | --- | --- | --- | --- |
| Opt-in PS operator selector | Add | REQ-001/002 | Existing B2 class | Different scope. Reuse URL/role guards, no engine or app assembly. |
| Release-package Scenarios | Add | REQ-003 | Finite A3 Mockito tests | Need actual PostgreSQL transactions and package-local signed vectors. |

| Path | Network calls | Client states | Failure points |
| --- | --- | --- | --- |
| Finite compilation | Zero | No test DB | Java source and fixture boundaries |
| Operator test | JDBC loopback only | Pre-provisioned owner and limited roles | DDL, physical readiness, lock observation and service outcomes |

### 7.1 System Architecture Design

A root-package JUnit test owns database gates and version/role assertions. A release-package test helper reuses A2 vectors, reads actual Process release rows through the production JDBC service, and exercises actual A3 JDBC adapter.

### 7.2 High-Level Design

Apply existing migrations to exact V10 in a fresh schema, validate and snapshot history. Apply only V11 and recheck old-history compatibility and V11 readiness before/after narrowly scoped grants. Test actual column grants by adding then revoking one at a time; no account, DB or schema deletion.

### 7.3 Detailed Design

Bootstrap seeds synthetic owner release rows using the verified signed tuple. Engine configuration affinity is stubbed, so no native model is deployed. The actual ProcessReleaseService queries its real scoped source and runtime role. The configured provider issues fixed-scope fixture grants using primary DB clock, denies unknown actors and proves only fixture independence. Owner holds SHARE relation lock while runtime invokes review. pg_locks must observe one distinct blocked RowExclusiveLock before deliberate expiry, or two before competing writers can proceed. An expired INSERT rolls back, then same scope runs two real connections, commits one row, observes a version conflict, and tests read authorization and exact history after trust is disabled. Cross-channel insertion attempts must fail against the physical composite FK. Runtime UPDATE, DELETE and TRUNCATE must fail with SQLSTATE 42501 on both tables.

## 8. Package Structure and Code File Tree

Two new PS test-source paths under top/egon/cola/platform/tianji/process/server. PlatformReleaseCandidateReviewPostgresTest owns the operator method and schema/role guard. release/PlatformReleaseCandidateReviewPostgresScenarios owns package-local signed vector reuse and real PostgreSQL candidate writes. The Plan lists exact paths.

## 9. Interface Definitions

No production interface, authorization, HTTP or CLI changes. The operator selector consumes two existing test properties and one dedicated opt-in flag.

## 10. POJO and Data Model Design

Reuse production immutable Scope/SignedMaterial/Grant/Receipt and exact signature fixture. The local ReviewOutcome records a typed winner or expected conflict.

## 11. Database Design

Context only. Reuse exact existing ProcessDB V1–V11 and A3 physical constraints. Test grants, data and locks exist only in the operator's disposable schema. No SQL version is added or edited.

## 12. Frontend Page Design

No UI target or browser requirement in a PostgreSQL operator fixture.

## 13. Design Patterns and Architecture Principles

Retain existing production JDBC Adapter. The test uses a direct fixture with bounded helper calls for observable lock conditions and no new production abstractions.

## 14. Test Design

| ID | Test | Expected observation |
| --- | --- | --- |
| TEST-001 | Java 25 test compile, javap annotation/method inspection and finite A3 regression | Two test files compile, both opt-in properties present, no PG invocation; existing A3 assertions remain green. |
| TEST-002 | Future exact operator selector | One unskipped successful method on fresh dedicated loopback DB. Verify actual V11/role, lock wait, one winner, expiry rollback, scoped FK and exact history. |

## 15. Non-functional and Cross-cutting Design

JDBC calls and waits bounded. No secrets in source or logs. No authentication bypass against external services. No web, Flowable engine, provider or application context starts.

## 16. Compatibility, Migration, Rollout, and Rollback

Test source only. Existing migrations and production artifacts remain immutable. No real DB run or role grants from this source preparation. The operator supplies fresh and temporary resources and handles isolated teardown independently.

## 17. Alternatives and Decisions

A data-source-only fixture is sufficient for A3 schema and transaction claims. Reusing the full native engine provisioner would broaden I to deployment without affecting the checked release-query and storage contracts. Real model publication and IAM remain separate.

## 18. Risks and Open Questions

Operator loopback authentication and temporary roles must already exist. Exact PG catalog behavior and both transaction paths remain unverified until I runs. Current trust and authorization providers in this fixture are synthetic; results cannot establish live governance authority.

## 19. Traceability Matrix

| Requirement | Use case | Design | Test |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7.2 and test guard | TEST-001/002 |
| REQ-002 | UC-001, UC-002 | §7.2/7.3 role and scope | TEST-002 |
| REQ-003 | UC-002 | §7.3 lock and history | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7, exact PS test owner | Current test structure preserved | Recheck at scoped intake |
| MC-REUSE-001 | Applicable | PASS | EVD-002–004, §7 | Existing URL/role guards, A2 vectors and A3 production JDBC reused | Recheck at scoped intake |
| MC-DEP-001 | Applicable | PASS | §6, PS POM | Existing Java/Flyway/JDBC/Mockito sufficient | Recheck at scoped intake |
| MC-NAME-001 | Applicable | PASS | §8/10 named types | Test selector, fixture and outcome separate | Recheck at scoped intake |
| MC-VALID-001 | Applicable | PASS | §7 existing exact URL/role and candidate scope | Gate explicit and closed before operator writes | Recheck at scoped intake |
| MC-MODEL-001 | Applicable | PASS | §10 current signed immutable values | Test-only ReviewOutcome and existing receipts | Recheck at scoped intake |
| MC-CONVERT-001 | Not applicable | N/A | §7/10 existing A3 SQL mapping | Fixture only seeds the already verified input map | Recheck at scoped intake |
| MC-LOG-001 | Not applicable | N/A | §15 JUnit/JDBC fixture | No logger or secret data added | Recheck at scoped intake |
| MC-BEAN-001 | Not applicable | N/A | §7/15 no application context | No Spring managed component added | Recheck at scoped intake |
| MC-UTIL-001 | Applicable | PASS | §6.3 and test helpers | Existing JDK/JDBC tools and bounds | Recheck at scoped intake |
| MC-JSON-001 | Not applicable | N/A | §7 and unchanged A2 wire | Fixture only uses existing JSON map serialization | Recheck at scoped intake |
| MC-TIME-001 | Applicable | PASS | §6.3, TEST-002 | DB instant and monotonic polling separate | Recheck at scoped intake |
| MC-CONFIG-001 | Not applicable | N/A | §3.3, §16 no config | Opt-in test system property only | Recheck at scoped intake |
| MC-PATTERN-001 | Not applicable | N/A | §7/13 fixed operator fixture | Current production Adapter reused | Recheck at scoped intake |
| MC-SCOPE-001 | Applicable | PASS | §8/11/16 test-only | Two test paths and three docs; existing migrations unchanged | Recheck at scoped intake |
| MC-TEST-001 | Applicable | PASS | §14 TEST-001/002 | Compilation differs from operator acceptance | Recheck at scoped intake |
| MC-BLOCKER-001 | Applicable | PASS | §5/18 and TEST-002 | Closed source-review scope; real DB operator gate remains separate | Recheck at scoped intake |

### 20.6 Final verdict

PASS — Ready for user review
