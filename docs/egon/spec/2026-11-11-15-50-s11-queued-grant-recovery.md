# T4-03 S11 原授权已持久化的 QUEUED Run 重建发现规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Separate producer process and existing AS durable grant dispatch |
| Created | 2026-11-11 15:50 Asia/Shanghai |
| Updated | 2026-11-11 15:50 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Four AS test sources and three review documents |
| Change Surface | AS operator, private test handoff and finite evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | bc9e62f34752b78c305a53ffb349f150cdbcffa8 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S11 queued recovery plan](../plan/2026-11-11-15-51-s11-queued-grant-recovery.md) |

## 1. Summary

一次性 PostgreSQL 中由原 JVM 通过实际 ledger 和 sealed grant 存储提交同一 QUEUED Run，再在首次 claim 前强制回收。父 JVM 新建独立 DataSource 和真实 AS 发现、授权、调度组件，仅从已持久化证据发现原 Run。Fake 模型执行一次，缺失或过期 grant 不进入发现，当期显式拒绝不得 claim。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentLedgerService#start and AgentExecutionGrantStore#complete | Run 和 RUN_QUEUED 同事务提交，V6 grant 另外以 owner 和 binding 精确绑定。 |
| EVD-002 | TianjiGrantDispatchPoller#poll and JdbcAgentLedgerRepository#grantReadyRuns | AS 轮询只选择具当前有效 sealed grant 的未过期 QUEUED。 |
| EVD-003 | TianjiExecutionGrantAuthorizer#evaluateRun and AdkAgentRunExecutor#executeScoped | 当前授权影响 claim 和模型，明确 DENY 只暂停原 Run。 |
| EVD-004 | TianjiGrantDispatchPostgresTest | 原真实 PostgreSQL 双 poller 和 Fake 模型用例在同一 JVM。 |
| EVD-005 | AgentCheckpointCapturePostgresTest#startForkChild and #reapForkChild | 已有受控子 JVM 启动、私有 stdin、有界强制回收实践。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Only existing AS test infrastructure is extended.

## 3. Goals and Non-goals

覆盖已提交、尚未 claim 的普通 USER Run 在原提交 JVM 被强制结束后由新 AS 轮询发现。独立完整 AS 宿主替换及真实 IAM/HTTPS 属于另行的 E 证据。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS tests | Affected | EVD-004/005 | 独立 operator、私有协议和有限守卫 | Fixture and test | §7, §8, §14 |
| AS/AR production | Context-only | EVD-001–003 | 复用原 queue、grant、claim 和 model 流程 | Existing contract | §9/16 |
| AgentDB | Context-only | EVD-001/002 | 一次性真实 V1–V9 和原证据表 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 真实持久预 claim 切点 | Must | 原生产者使用实际 ledger 和 V6 store；同一 owner 的初始 QUEUED 和精确 grant 可见，零 attempt、epoch、预算、结果；原 JVM 活着后强制退出 | EVD-001/005 |
| REQ-002 | 新 AS poller 只执行一次合法 Run | Must | 独立新组件发现原 Run；原 grant 保持；仅一次 claim、模型和完成结果；缺失/过期 grant 排除，当前 DENY 零 attempt | EVD-002/003/004 |
| REQ-003 | 独立可复跑且失败关闭 | Must | 专用 scope、私有 stdin、有界 poll/reap；R1 反证、26 有限结果、精确编译与路径 | EVD-004/005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 一次性 loopback PostgreSQL 主库 |
| ACTOR-002 | Source reviewer | JDK 25 与有限测试 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 恢复原未 claim Run | ACTOR-001 | 独立空库、原限定 owner 和 Fake 模型 | 原 producer 提交并停驻；回收后启动新 poller | 任一不准确原证据、授权或运行使验收失败 | 同 Run 一次完成，负对照零 attempt | REQ-001/002 |
| UC-002 | 复核私有启动范围 | ACTOR-002 | 源基线和 JDK 25 | 放宽库名触发 R1，原代码编译和有限通过 | 真实 operator 默认跳过 | 保留可单独执行的 I1 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

仅允许专用 tianji_it_queue_recovery_ 名称和 127.0.0.1，原 Agent schema 必须为空。原进程使用实际 ledger/store 提交队列。父 JVM 使用新建的独立生产类实例。IAM Transport 与模型在本 I 中均为受控测试边界。

## 6. Project Technology Context

JDK 25、JUnit、Flyway、Spring JDBC、PostgreSQL V1–V9、Mockito 和现有 AS/AR。

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
| Rule 1 | Yes | EVD-001–005 | 四份测试源码及固定选择器。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–005 | 严格一次性库名和准确 grant、Run、owner。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–005 | 复用原不可变 Run、Ticket 与私有 handoff。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–005 | 生产合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK、Mockito、JUnit 和已有 JDBC。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | No | EVD-001–005 | 生产合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 7 | Yes | EVD-001–005 | 独立测试开关及私有启动。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 9 | Yes | EVD-001–005 | 复用原 AS 发现与授权。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–005 | 单调等待及数据库时钟。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–005 | V1–V9 真实独立夹具及原迁移。 | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | CHILD_SECONDS and producer reap | Duration | System.nanoTime and java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Each phase bounded at 16 seconds with per-query limit | JVM monotonic nanos | Compatible with existing runtime fixture | Step 1 TEST-003 |
| TIME-002 | Original and expired grant expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Compare expiry against database clock before discovery | PostgreSQL microseconds and clock_timestamp | V6 preserves current expires_at | Step 1 TEST-003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 原生产者 JVM | Extend | REQ-001/003 | 同 JVM 双轮询 | 没有提交进程退出 | 受限子进程和协议 | Use |
| 新 AS poller | Reuse | REQ-002 | AR 通用 pendingRuns | AS 必须按 sealed grant 发现 | 原生产对象 | Use |
| Fake 和负对照 | Reuse | REQ-002 | 原双 poller Fake | 需验证有效性和当前 DENY | 三个同 scope 对照 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 专用 loopback PostgreSQL | 原 detached producer 和父 JVM 新 AS 组件 | V6 grant、零首次 claim、一次最终结果 | 第一次 claim 之前原 producer 终止 |
| R1/G1/G2/S1 | 无数据库 | 有限 JVM | 精确协议和编译证据 | 普通测试库名须拒绝 |

### 7.1 System Architecture Design

父进程校验精确数据库及空 schema，再通过 Flyway 安装 V1–V9。原子进程在私有 stdin 收到库和 setup role，验证同一主库，再通过实际生产 ledger/store 写入。父进程从独立连接读精确 owner 的原记录。

### 7.2 High-Level Design

父进程在原 JVM 尚存活时核对确切 Run、grant、无租约、预算和结果，再强制回收。新 DataSource、Ledger、GrantStore、Poller、Dispatcher 和 Authorizer 仅凭持久化证据恢复同一 Run。

### 7.3 Detailed Design

保持精确 Run ID、定义版本、创建时刻和 V6 grant。缺失和按数据库时钟过期的 grant 排除出发现；有效但被当前受控 IAM 拒绝的原 owner 记录为 SUSPENDED/AUTHORIZATION_DENIED，attempt 零。原 RUN_QUEUED、首次 QUEUED→RUNNING 和 Fake 调用各一次，结果完成后再轮询仍无新增。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoff.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoffTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiQueuedGrantRecoveryPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-queued-grant-recovery.md` |

## 9. Interface Definitions

生产入口复用 AgentLedgerService#start、AgentExecutionGrantStore#complete 和 TianjiGrantDispatchPoller。I1 为独立 tianji.postgres.queue.recovery=true，加专用 URL 属性。子进程只能使用私有 stdin。

## 10. POJO and Data Model Design

复用 AgentRunDTO、GetAgentRunQuery、TrustedAgentActor 和 AgentExecutionGrant.Ticket。测试内部手递只含规范化数据库 URL 与不含凭据的 setup role。

## 11. Database Design

Relational model change: No. 一次性库使用 V1–V9，复用 V6 evidence 和原 Run/Event/预算表。原 31 份迁移逐字节一致。

## 12. Frontend Page Design

无页面改动。

## 13. Design Patterns and Architecture Principles

复用原 AS grant-aware poller 和真实 runtime。测试源保留原双节点入口并抽取四项共同 Fake fixture。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 在独立 scratch 把库名缩小约束变为普通 tianji_it_，accepts_only_the_dedicated_loopback_queue_fixture 必须出现缺少 IllegalArgumentException 的 typed RED。 |
| TEST-002 | 2 handoff、11 poller、13 authorizer 有限通过；两个 PostgreSQL 方法保持跳过，精确新入口编译。 |
| TEST-003 | 真实隔离 PG 见证原持久 QUEUED，强制回收，现有 AS 组件恢复原 Run，一次执行和三个负对照。 |

## 15. Non-functional and Cross-cutting Design

生产者环境清除且输出丢弃。私有 stdin 有长度与格式限制，数据库和子进程时间有界。源证据无凭据。

## 16. Compatibility, Migration, Rollout, and Rollback

原生产默认开关、配置和持久协议保持。独立 operator 和子进程入口默认关闭。回滚为撤销测试提交。

## 17. Alternatives and Decisions

同 JVM 并发证明 claim CAS。独立生产者提交和父 JVM 的新发现链证明已持久原状态跨原进程丢失继续处理。

## 18. Risks and Open Questions

实际 PostgreSQL、原进程终止和精确运行证据由操作者执行 I1。另行验收真正的 AS 服务器替换、IAM、HTTPS 与整个 S11。

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
| MC-ARCH-001 | Applicable | PASS | §7/14 | AS test owner 调用真实 AR grant ledger 和 AS poller。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用原 Fake 模型、预算、Definition、Poller 与 Authorizer。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK 25、JUnit、Spring JDBC、PG JDBC、Flyway 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 使用现有 AS 安全测试包与独立操作者属性。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 专用 loopback database、精确 owner 和 V6 grant 的提交证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 复用 AgentRunDTO 和 Ticket，私有 handoff 仅两个字段。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | 复用 AS 测试和 PostgreSQL 可用能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 单调计时约束子进程，授权到期由数据库时钟确定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 独立测试和子进程入口默认关闭。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 复用生产发现路径和测试-only IAM adapter。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 四份测试、三份文档、原迁移和生产范围保持。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | R1 有限反证、G1/G2 和 I1 证据独立。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 实际 PostgreSQL、进程与 grant 可见性待隔离验收。 | None |

### 20.6 Final verdict

PASS — Ready for user review
