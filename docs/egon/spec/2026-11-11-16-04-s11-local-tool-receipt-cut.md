# T4-03 S11 本地 START 成功收据提交后原执行进程退出规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Exact local V13 receipt and post-commit recorder exit |
| Created | 2026-11-11 16:04 Asia/Shanghai |
| Updated | 2026-11-11 16:04 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three AR test sources and three review documents |
| Change Surface | AR test source and evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | ec9dc02f8ab00ec788028581e8ab61ae2b5df47b |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S11 local Tool receipt cut](../plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md) |

## 1. Summary

沿用原 V13 固定 START recorder，受控 transport 在本地收据前观察 DISPATCHING 和空收据。真实本地 invocation SUCCEEDED 和 action APPLIED 在同一事务提交后，于现有 ToolCommittedFactObserver 回调强制回收原 JVM。替代观察者使旧 Run 租约过期并通过产品 reconciler 单次暂停，原收据不变，未完成预算标为 UNKNOWN，旧租约和新 claim 均不能启动再次执行。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ToolInvocationRecorder#dispatchAndRecordStart | DISPATCHING 先于 operation。V13 完成后回调 observer，随后独立结算。 |
| EVD-002 | ToolInvocationSql#completeIdempotentWrite and ToolActionIntentSql#completeWrite | invocation SUCCEEDED、intent APPLIED 和相同成功收据在同一事务持久化。 |
| EVD-003 | AgentLedgerService#suspendExpiredLease and JdbcAgentLedgerRepository#markOutstandingBudgetUnknown | 原 Run 单次 SUSPENDED，悬空预算 UNKNOWN，旧租约无权。 |
| EVD-004 | AgentLedgerService#inspectRecovery and #recoveryToolEvidence | 原 owner 可见完整成功 Tool receipt 与未核验 Run effect。 |
| EVD-005 | ToolExecutionJournalPostgresTest.StartRecorderFixture and AgentCheckpointCapturePostgresTest#reapForkChild | 已有真实 V13 夹具、受控端口和安全子进程惯例。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Only existing AR test infrastructure is extended.

## 3. Goals and Non-goals

覆盖本地成功 Tool receipt 已提交、结算尚未执行的真实进程故障。已有 PS 成功响应丢失产生本地 UNKNOWN，属于另一个切点。真实 PS 外部调用和 IAM 属于独立 E。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR tests | Affected | EVD-005 | 新子进程、证人及有限 guard | Fixture and test | §7, §8, §14 |
| AR production | Context-only | EVD-001–004 | 原收据与恢复事务保持 | Existing contract | §9/16 |
| AgentDB | Context-only | EVD-002/003 | 一次性 V1–V13 原 schema | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 证明本地成功收据事务边界 | Must | 同一实际 invocation 和 intent 在发送后由 DISPATCHING 变为 SUCCEEDED/APPLIED，相同 receipt；父进程在原回调阻塞时见 RESERVED 和无结果并强制回收 | EVD-001/002/005 |
| REQ-002 | 原结果和恢复安全 | Must | 原 V13 两行保持完整。reconcile 一次暂停，预算 UNKNOWN；无新投影或结果、旧租约及新 claim 被拒绝，owner 看到完整本地 receipt | EVD-003/004 |
| REQ-003 | 精确私有控制及有限证据 | Must | 专用库、长度和 marker 严格；R1、37 有限、实际入口编译和六路径 | EVD-005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 一次性 loopback V13 主库和 pinned pgvector |
| ACTOR-002 | Source reviewer | Java 25 和有限测试 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 验证本地成功收据在重启后保留 | ACTOR-001 | 独立真实数据库和受控 START transport | 实际 recorder 提交本地收据，回调中停驻，强制回收并 reconcile | 任一证据、原租约或清理不匹配即失败 | 本地成功收据保留，等待人工核对 Run | REQ-001/002 |
| UC-002 | 验证私有标记和源范围 | ACTOR-002 | 精确基线 | 变异 canonical UUID 守卫产生 RED，再运行候选 | 默认跳过真实 I1 | 报告有限及真实边界 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

隔离地址为 127.0.0.1，数据库使用 tianji_it_tool_cut_ 前缀、空 schema 和精确 pinned vector patch。原 START 远端由已有受控端口提供可确定成功，持久化、事务、租约及回收由实际产品路径完成。

## 6. Project Technology Context

JDK 25、JUnit、Spring JDBC、PostgreSQL、Flyway、现有 Tool V13 和预算 V2。

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
| Rule 1 | Yes | EVD-001–005 | 三份测试源码及精确选择器。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–005 | 专用数据库、完整 owner 和原执行证据。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–005 | 复用原 Run DTO、V13 outcome 和私有 witness。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–005 | 生产合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK、JUnit、Spring JDBC、Flyway。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | No | EVD-001–005 | 生产合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 7 | Yes | EVD-001–005 | 独立测试和子进程开关。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 9 | Yes | EVD-001–005 | 原 committed fact observer 与精确租约 CAS。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–005 | 单调限时和 PostgreSQL 租约时钟。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–005 | 只复用真实 V1–V13 schema。 | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | RECEIPT_CUT_SECONDS and child reap | Duration | monotonic nanoseconds and java.time.Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Sixteen seconds per operation and bounded JDBC | System.nanoTime | Existing child fixture approach | Step 1 TEST-003 |
| TIME-002 | Original Run lease expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Expire exact active lease while original total deadline is live | PostgreSQL clock_timestamp with microseconds | Existing lease and reconcile storage | Step 1 TEST-003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 原 recorder post-commit observer | Reuse | REQ-001 | 结算失败或回调错误 | 无原进程退出 | 测试回调停驻 | Use |
| 真实 V13 收据证人与 reconcile | Reuse | REQ-002 | 单机相同 recorder 复读 | 无持久跨进程证据 | 原 fixture 和 SQL 见证 | Use |
| 私有启动与 marker | Extend | REQ-003 | 原 AR checkpoint fork | 角色及证据字段不同 | 两份测试辅助源码 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 一次性 loopback PostgreSQL | 原 recorder 子 JVM 与替代 ledger | 本地 V13 receipt 和 V2 budget | 本地收据提交后、预算结算前 |
| R1/G1/G2/S1 | 无真实数据库 | 有限 JUnit | canonical marker 守卫和已编译入口 | 非规范 invocation UUID 被拒绝 |

### 7.1 System Architecture Design

原 JVM 使用现有 StartRecorderFixture 和受控 START port，分别观测本地 receipt 前后的持久状态。ToolCommittedFactObserver 在已经提交且不处于事务时写私有精确标记并停驻。

### 7.2 High-Level Design

父 JVM 根据 Run、invocation、intent、owner、原执行 SERVICE 和预算锁定确切 V13 双记录，确认成功与 RESERVED，强制回收原 JVM。从新 DataSource 建立替代 ledger。

### 7.3 Detailed Design

按原 revision、sequence、lease owner、epoch 与 expiry 精确过期。产品 reconcile 写一次 SUSPENDED/LEASE_EXPIRED，原 V13 双行逐字节保持，预算转 UNKNOWN。旧心跳失败，发现队列为空，新 claim 冲突。原 owner 读取 STALLED、LEASE_EXPIRED、Run effect UNVERIFIED 和 COMPLETE 本地 receipt。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoff.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoffTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md` |
| CREATE | `docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-local-tool-receipt-cut.md` |

## 9. Interface Definitions

沿用 ToolInvocationRecorder#executeIdempotentStart 的既有 ToolCommittedFactObserver 及 AgentLedgerService#inspectRecovery。仅测试新增 opt-in `tianji.tool.journal.postgres.start-receipt-cut`。

## 10. POJO and Data Model Design

复用原 Run、工具 outcome 与状态，私有 Cut 仅包含精确 Run、invocation、intent 的规范 UUID。

## 11. Database Design

Relational model change: No. 真实隔离 schema 使用 V1–V13 与预装 pgvector。原 31 份迁移保持。

## 12. Frontend Page Design

无页面改动。

## 13. Design Patterns and Architecture Principles

复用产品 post-commit hook，故障在隔离操作者 fixture 内注入。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 移除 invocation UUID canonical guard 的独立 scratch 副本，marker 有限用例必须失败。 |
| TEST-002 | 2 私有协议、30 recorder、5 owner 处置有限项通过，3 个 PG 用例默认跳过。 |
| TEST-003 | 实际隔离 PostgreSQL 观察提交后的双行、强制回收、原租约 reconcile 和原 owner 证据。 |

## 15. Non-functional and Cross-cutting Design

精确目录 0700 与文件 0600、受限 stdin 和 marker。原进程无输出。等待、回收及查询均有界。

## 16. Compatibility, Migration, Rollout, and Rollback

保留既有测试与生产配置。回滚为撤销测试提交。

## 17. Alternatives and Decisions

已提交 PS 响应丢失与本地成功收据后原进程退出是不同边界。既有同 JVM 结算反证保留，本 I1 独立观察实际原 JVM。

## 18. Risks and Open Questions

真实 PostgreSQL、原进程退出和本地持久证据待独立 I1。受控 transport 不提供真实 PS 调用或 IAM 证据。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/11 | TEST-003 |
| REQ-002 | UC-001 | §7/9/10 | TEST-003 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001–003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | 沿用原 AR 固定 START recorder、工具账本和 reconciler。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用现有 ToolCommittedFactObserver、V13 fixture 及 owner 处置。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK 25、JUnit、Spring JDBC、Flyway 和 PostgreSQL。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 原 Tool fixture 包内新增精确测试 selector。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 专用 loopback、完整 owner/Run/invocation/intent/原执行 SERVICE。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 原 V13 outcome、V2 budget 和私有不可变证人。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | JDK 安全子进程、受限文件与原持久测试能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 单调限时，租约时间由 PostgreSQL 统一判定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 专用操作属性和私有子进程属性默认关闭。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 原 post-commit 观察接口和测试进程故障注入。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 精确三份测试和三份文档，生产及迁移保持。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | R1 有限反证、37 有限项、独立编译和真实 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 真实 PostgreSQL 和原进程退出由独立操作者验收。 | None |

### 20.6 Final verdict

PASS — Ready for user review
