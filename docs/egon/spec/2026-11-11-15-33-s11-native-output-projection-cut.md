# T4-03 S11 原生 Session 输出已提交而账本投影未提交规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Existing dual codec fork and one fixture-owned projection wait |
| Created | 2026-11-11 15:33 Asia/Shanghai |
| Updated | 2026-11-11 15:33 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three AR test files and three review documents |
| Change Surface | Test source and operator evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 4313d3319ca228c2310d06dba9128dcb981d3f30 |
| Amends | None |
| Supersedes | None |
| Depends On | [Checkpoint takeover PostgreSQL](./2026-11-06-03-15-agent-checkpoint-takeover-postgres.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [S11 native output projection cut plan](../plan/2026-11-11-15-34-s11-native-output-projection-cut.md) |

## 1. Summary

扩展原 Agent Runtime 双 codec PostgreSQL 独立操作者用例，在真实原生 Session 输出已提交、AGENT_OUTPUT 投影等待时强制退出原 JVM。原 Run 通过现有过期 reconciler 进入 SUSPENDED。原首条输入 checkpoint 预览及接管在授权之前拒绝。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ADK 1.13.0 Runner#processEvent, PostgresAdkSessionService#appendEvent | 非 partial 原生事件先提交到持久 Session。 |
| EVD-002 | AdkAgentRunExecutor#runClaimed, AgentLedgerService#appendEvent | 完整输出由独立 Run 事务更新修订与插入 AGENT_OUTPUT。 |
| EVD-003 | AgentCheckpointObservationService#inspectInTransaction and OBSERVATION_SQL | 当前 run fence、Session 首条事件和未推进预算共同约束首输入观察。 |
| EVD-004 | AgentCheckpointTakeoverService#previewAuthorization and #takeOver | 缺失精确边界返回修订冲突，发生在授权或写事务前。 |
| EVD-005 | AgentCheckpointCapturePostgresTest#verifyProductCheckpointForks and AgentCheckpointForkHandoff | 原双 codec JVM、输入切点、私有 stdin 和过期夹具可复用。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 扩展既有测试夹具。

## 3. Goals and Non-goals

覆盖原生输出和账本投影之间的强制退出窗口。现有产品恢复入口继续使用首输入证据。跨库交付、HTTP/IAM 和其他 S11 窗口独立验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR tests | Affected | EVD-005 | 新 opt-in JVM 场景及私有阶段 | Fixture and test | §7, §8, §14 |
| AR production | Context-only | EVD-001–004 | 原写入、投影、观察、恢复入口 | Existing contract | §9, §16 |
| AgentDB | Context-only | EVD-001/002 | 一次性守卫和只读证人 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 真实输出已提交，投影未提交 | Must | 独立连接见到同原 invocation 的完整已提交输出，守卫仅阻塞本 Run 的 AGENT_OUTPUT，原 JVM 在释放锁前强制退出 | EVD-001/002/005 |
| REQ-002 | 已推进 Run 拒绝首输入恢复 | Must | 两种 codec 均单次 reconcile 到 SUSPENDED，输出和 checkpoint 保留，观察失去 fence，预览与接管冲突，零授权与回执 | EVD-003/004/005 |
| REQ-003 | 可复跑源证据 | Must | 独立开关、私有二进制阶段、有限 typed RED 和通过，源路径、迁移及待执行 operator 边界准确 | EVD-005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 一次性 V16、角色和双 codec |
| ACTOR-002 | Source reviewer | JDK 25 和有限测试 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 保留原生进展并拒绝首输入接管 | ACTOR-001 | 精确 loopback 角色与空一次性 schema | 停在精确等待，读证据，结束原 JVM，reconcile 并请求观察和接管 | 漏掉任何切点、进程或状态条件使场景失败 | 原生输出保留，账本无 AGENT_OUTPUT、结果、RESUME 回执 | REQ-001/002 |
| UC-002 | 复核隔离交付 | ACTOR-002 | 基线和既有依赖 | 执行 R1/G1/G2/S1 | 缺门控或不准确范围阻断 | 实际 I1 单独报告 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

沿用原 fixture 的 loopback 数据库、空 schema、V16、精确 vector 和独立运行及接管角色。原 worker 运行普通产品路径。相同 Run 的 TEXT_DELTA 和模型预算证据可先于最终输出，任一既有安全边界失配均保守拒绝恢复。

## 6. Project Technology Context

JDK 25、JUnit、Spring JDBC、PostgreSQL、ADK 1.13.0 和既有 AR PostgreSQL 夹具。

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
| Rule 1 | Yes | EVD-001–005 | 修改三份既有测试源码。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–005 | 严格按一次性数据库、角色、owner、Run 和投影类型限定故障。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–005 | 沿用原不可变 fixture 模型。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–005 | 原生产及其合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–005 | 既有 JDBC/JUnit/JDK 与测试工具。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | No | EVD-001–005 | 原生产及其合同保持。 | §7/8/14 | TEST-001–003 | N/A |
| Rule 7 | Yes | EVD-001–005 | 新开关仅属于独立测试方法。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 9 | Yes | EVD-001–005 | 使用原 Test Fixture 与 PostgreSQL 事务 advisory lock。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–005 | 单调计时和数据库租约时钟。 | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–005 | 操作者触发器在一次性 schema 清理，原迁移无差异。 | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Waiter, reap and backend disconnect deadlines | Duration | monotonic long nanoseconds and java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Each wait bounded at 16 seconds with per-query bounds | System.nanoTime | Existing process fixture | Step 1 TEST-003 |
| TIME-002 | Run and Session lease expiry | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Existing exact fixture sets expiry in the past | PostgreSQL clock_timestamp | Existing fenced reconciliation | Step 1 TEST-003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 精确一次性投影触发器 | Extend | REQ-001 | 表级锁 | 表级锁会阻断更早的 TEXT_DELTA | 一段测试 DDL | Use |
| 私有 JVM 阶段 | Extend | REQ-001/003 | CAPTURE | CAPTURE 停在预算之前 | 一种阶段和一项有限用例 | Use |
| 原观察与接管 | Reuse | REQ-002 | 原产品入口 | 有待执行真实故障 | 夹具调用 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 一次性 PostgreSQL | 原执行子 JVM 与父观察者 | 原生输出，原 checkpoint，Run SUSPENDED | AGENT_OUTPUT 插入等待 |
| R1/G1/G2/S1 | 无 operator 连接 | JDK 25 | 私有协议和编译结果 | 变异摘要守卫应失败 |

### 7.1 System Architecture Design

现有原运行子 JVM 正常执行 V16 和 FakeModel。一次性 owner 安装由精确 tenant、Run 和 AGENT_OUTPUT 限定的 BEFORE INSERT 触发器。原投影事务在对应 advisory key 等待。

### 7.2 High-Level Design

父进程持锁，从独立连接核对对应 runtime role、两个键与唯一 blocker。读取原生行和空投影，强制结束并回收原 JVM，再释放锁。触发器在获锁后抛出测试专用异常，保证待处理事务即使发现断连前继续也回滚。等待后端断开后清理触发器和函数。

### 7.3 Detailed Design

读取并解码切点快照，要求同 invocation 的单个 writer 输出、单条原输入、单个模型预算行及原 checkpoint。按原 Run fence 精确过期，通过实际 reconcile 执行一次 SUSPENDED，然后精确过期 Session。观察返回 NOT_AT_CAPTURE_FENCE，精确预览及接管返回 TIANJI_REVISION_CONFLICT。授权回调一旦发生即失败。重读原生证据、checkpoint、Run/Session fence 及零 RESUME 回执。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-native-output-projection-cut.md` |

## 9. Interface Definitions

沿用现有 AgentCheckpointObservationService#observe 和 AgentCheckpointTakeoverService#previewAuthorization/#takeOver。私有 PROJECT_OUTPUT 阶段编号 3，初次运行没有切点摘要。操作者测试开关为 `tianji.postgres.checkpoint.capture.output-projection-cut`。

## 10. POJO and Data Model Design

复用 NativeRow、ForkCut、ResumeCommand 和现有 owner。私有 handoff 仍限长、精确两种 codec、严格字段和完整读取。

## 11. Database Design

Relational model change: No. 测试触发器只在一次性 schema 内存在，适用于精确 Run 的 AGENT_OUTPUT 插入。只读证人关联完整 owner。现有测试租约更新走已有精确限定。既有迁移字节保持。

## 12. Frontend Page Design

无页面改动。

## 13. Design Patterns and Architecture Principles

在原测试 fixture 的写入边界注入单点故障。原产品 API、事务与观测代码执行现有路径。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 变异 scratch 构造器仅绕过 PROJECT_OUTPUT 摘要守卫，有限测试应得到 typed failure。 |
| TEST-002 | 有限 handoff、observation 和 takeover 通过，实际操作者入口编译且默认跳过。 |
| TEST-003 | 隔离 PostgreSQL 单方法运行两种 codec，确认真实等待、原生证据和授权前恢复拒绝。 |

## 15. Non-functional and Cross-cutting Design

私有进程环境和 stdin 复用。守卫等待、终止、JDBC、断连和测试时间均有界。证据不回显原生 payload 或凭据。

## 16. Compatibility, Migration, Rollout, and Rollback

原阶段编号兼容。新增开关仅用于 operator test，默认关闭。回滚为撤销该测试提交。

## 17. Alternatives and Decisions

表锁可能影响首输入之后的早期投影。精确触发器对前序进展透明，可从原 PostgreSQL 等待关系验明切点。

## 18. Risks and Open Questions

实际 PostgreSQL、双 codec 和进程退出需独立操作者执行。该窗口不证明人工处置工具、HTTP/IAM 或全套 S11 验收。

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
| MC-ARCH-001 | Applicable | PASS | §7/14 | 复用 AR 持久 Session、投影和 takeover，故障由一次性夹具持有。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用现有 process fork、FakeModel、会话解码、租约过期和 reconciler。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK、JUnit、Spring JDBC、PG JDBC 和 Flyway。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 新增测试选择器和私有 handoff 枚举值。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 通过 tenant、Run、类型、runtime role 和精确 blocker PID 确认证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 复用 NativeRow、ForkCut 和原 owner 账本接口。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | 复用原 checkpoint fixture 和 JDK 单调时钟。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 有界等待、reap、连接查询与原数据库租约时钟。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 新增独立测试 opt-in，URL、角色、vector、V16 前置校验沿用原夹具。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 夹具触发器仅截获指定投影，原模型和运行链路保留。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 三份原测试源和三份评审文档。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | 私有 handoff 有限反证、源码编译及实际 operator 运行分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 实际两种 codec 的一次性 PostgreSQL 操作者报告待执行。 | None |

### 20.6 Final verdict

PASS — Ready for user review
