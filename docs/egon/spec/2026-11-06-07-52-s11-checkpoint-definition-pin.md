# T4-03 S11 Checkpoint 原定义版本与摘要故障验收规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Extend one guarded operator fixture and one finite binding case |
| Created | 2026-11-06 07:52 Asia/Shanghai |
| Updated | 2026-11-06 07:52 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Two AR test files and three review documents |
| Change Surface | Test source and operator evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | e42281d4e08120755ac9afbc9d8c8bce09d3b1f0 |
| Amends | None |
| Supersedes | None |
| Depends On | [Checkpoint takeover PostgreSQL](./2026-11-06-03-15-agent-checkpoint-takeover-postgres.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [S11 definition pin plan](../plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md) |

## 1. Summary

沿用已有双 codec PostgreSQL 进程接管夹具。发布同家族第二版之后精确预览原第一版。分别注入 checkpoint 版本和摘要错配，证明签发之前拒绝，复原后原接管与 ACK 重放仍成立。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentLedgerService#publish and #verifiedSnapshot | 发布追加不可变版本，存量 Run 按自己的版本、摘要和内容校验快照。 |
| EVD-002 | AgentCheckpointObservationService.CheckpointRow#matchesBinding | V16 的定义 key、版本、定义和快照摘要必须与当前原 Run 的授权材料一致。 |
| EVD-003 | AgentCheckpointCapturePostgresTest#takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit and RunFixture#assertCheckpointTakeover | 独立角色、双 codec、原进程强制退出、过期、真实接管、历史 ACK 已有专用操作者入口。 |
| EVD-004 | AgentCheckpointObservationServiceTest#rejects_stale_binding_and_malformed_or_unexpected_native_event | 有限测试覆盖快照、引擎和原生事件错配，可独立补齐版本与定义摘要。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 本步骤只扩展既有 AR 测试。

## 3. Goals and Non-goals

验证同家族发布新版和实际 V16 定义证据错配的独立行为。范围为首次持久输入的现有 checkpoint 接管窗口。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR tests | Affected | EVD-003/004 | 同一操作者方法扩展版本发布和两项故障，对应有限反证 | Fixture and test | §7, §8, §14 |
| AR production | Context-only | EVD-001/002 | 复用现有不可变发布、原 Run 快照与绑定校验 | Existing contract | §9, §16 |
| AgentDB | Context-only | EVD-003 | 只在既有一次性夹具对准确的 V16 行注入并复原故障 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 发布新版本不移动原 Run 版本 | Must | 新 v2 与 v1 摘要不同，真实精确预览和最终许可仍使用原 v1 完整绑定 | EVD-001–003 |
| REQ-002 | 两个定义证据错误独立拒绝 | Must | 仅版本改变与仅摘要改变分别令预览和接管冲突，授权调用和 RESUME 回执均为零，恢复后原边界、原 ACK 接管和重放成立 | EVD-002/003 |
| REQ-003 | 独立的有限与真实操作证据 | Must | 移除任一守卫有独立 typed RED，有限用例通过，默认跳过 I1，两源三文档且 28 原迁移逐字节相等 | EVD-003/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 既有一次性 V16、三角色、双 codec 子进程夹具 |
| ACTOR-002 | Source reviewer | JDK 25 和有限观察／接管测试 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 验证原版本和安全拒绝 | ACTOR-001 | 原 Run 的 V16 完整 owner 和准确过期双租约 | 发布第二版，精确预览原绑定，依次注入并复原两个独立故障，再走原接管 | 任一错配在签发之前拒绝 | 原 v1、原生前缀和原 ACK 保持 | REQ-001/002 |
| UC-002 | 离线审查守卫 | ACTOR-002 | 基线和已有 AS/AR 编译依赖 | 运行两个 R1、G1/G2/S1 | 错过任一错误的有限反证阻断 | 独立 I1 待授权 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

原 I1 的一次性 loopback、pinned vector、三个独立角色、双 codec 子进程和现有开关仍为必备。故障注入使用原 fixture owner 的既有连接，仅更新原 Run 的完整 V16 tuple，并以准确 CAS 复原。操作者负责实际运行。

## 6. Project Technology Context

JDK 25、JUnit、Mockito、现有 Spring JDBC、原 AR AgentLedger 和 PostgreSQL V16 夹具。

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
| Rule 1 | Yes | EVD-001–004 | 在两个既有测试内扩展准确场景。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–004 | 复用产品发布与预览入口，夹具 CAS 严格验证旧值和完整 owner。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–004 | 复用原不可变规格、快照与授权模型。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–004 | 无新业务类、日志或 Spring Bean。 | §7/8/14 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–004 | 既有 JDK、JDBC、JUnit 和 Mockito。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–004 | 保留当前定义和 Session 编码。 | §7/8/14 | TEST-001–004 | N/A |
| Rule 7 | Yes | EVD-001–004 | 沿用原操作者 opt-in，生产默认与环境保持一致。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–004 | 复用测试 fixture 和隔离数据库 CAS。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 10 | No | EVD-001–004 | 原策略 timeout、计时和存储语义保持原值。 | §7/8/14 | TEST-001–004 | N/A |
| Rule 11 | Yes | EVD-001–004 | 使用真实 PostgreSQL 既有 V16，所有原 SQL 字节不变。 | §7/8/14 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: Use the existing original policy totalTimeout for version 2 and reuse the existing I1 deadlines, acquisition times, database clock and lease-expiration protocol. No time field or interpretation changes.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 原接管 operator fixture | Extend | REQ-001/002 | 原真实接管和 ACK | 没有版本变更和实际定义错配 | 已有一份测试 | Use |
| 原观察有限 fixture | Extend | REQ-003 | 原快照与原生事件错配 | 定义版本、定义摘要需独立证明 | 一个现有用例 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 一次性 PostgreSQL | 原 V16 两种 codec、原进程与替换 reconciler | v1/v2、checkpoint 和原 ACK | 不匹配定义在授权前拒绝 |
| R1/G1/G2/S1 | 无 operator 连接 | JDK 25 与有限模拟 | 有限报告 | 任一守卫弱化被检出 |

### 7.1 System Architecture Design

沿用当前 Run/Session 双进程 PostgreSQL fixture。真实 AR 账本发布 v2，现有 takeover 的只读预览识别原 v1。专用恢复角色和受控授权与原用例一致。

### 7.2 High-Level Design

在准确过期且已 reconcile 的原 cut 上发布一份内容不同的新 v2。读取同一原 v1 绑定。独立替换 V16 的 version 或 digest，保持另一字段准确。每次在 finally 用准确原值复原。

### 7.3 Detailed Design

分别要求预览和 claim 返回 TIANJI_REVISION_CONFLICT。在恢复后比较原始 checkpoint 摘要、Run/Session 原 fence、原生前缀、无预算或工具推进和零 RESUME receipt。真实授权调用次数为零。原有过期许可、正向接管、接管事务、原 ACK 重放与冲突用例随后继续验证。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-checkpoint-definition-pin.md` |

## 9. Interface Definitions

沿用现有 AgentLedgerService#publish、AgentCheckpointTakeoverService#previewAuthorization 和 #takeOver，原 operator 方法签名与开关保持。

## 10. POJO and Data Model Design

复用 AgentDefinitionSpecDTO、AgentExecutionSnapshot、ResumeRequest 与 Acknowledgement。依赖原定义的不可变版本及标准 digest。

## 11. Database Design

Relational model change: No. V16 的版本和摘要只在隔离 fixture 中更新并按准确前值复原。CAS 绑定 tenant、Run、USER 类型、issuer、subject、app、Session、attempt、checkpoint number、安全点和旧版本及摘要。所有原 28 SQL 保持原字节。

## 12. Frontend Page Design

无页面变更。

## 13. Design Patterns and Architecture Principles

复用原 Test Fixture 与精确 CAS，使受检逻辑保持在生产 AR。两项独立负向输入复用原有限测试。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 分别删除 version equality 或 definition SHA equality 的 scratch 生产副本。各自触发同一有限测试 typed RED。 |
| TEST-002 | G1 owning 运行原 observation、takeover 和 fork handoff，22 项有限验证通过。 |
| TEST-003 | 编译既有 operator 方法和新增精确 fixture 分支，默认 operator 跳过，严格文档和源范围。 |
| TEST-004 | I1 实际 PostgreSQL 双 codec 运行既有接管 fixture，验证 v2 发布和两种物理错配。 |

## 15. Non-functional and Cross-cutting Design

沿用原 operator 的连接、线程和时间上限。故障数据限定在一次性数据库完整 owner。错误路径保留现有安全枚举。

## 16. Compatibility, Migration, Rollout, and Rollback

复用原测试选择器和默认关闭开关。回滚是撤销一个测试文档提交。

## 17. Alternatives and Decisions

原有限检查仅证明单独 Java 绑定逻辑。物理 PostgreSQL 错配加入同一双 codec 的已有接管 fixture，重用原子接管和回执证明。

## 18. Risks and Open Questions

实际 PostgreSQL 和子进程故障证据由授权操作者运行原 I1。其他 S11 工具、投影及真实当前授权切点分别验收。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9/10 | TEST-004 |
| REQ-002 | UC-001 | §7/11/14 | TEST-001/004 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001–003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 复用 AR V16、定义账本与同一主库三角色夹具。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | 复用现有发布、预览、接管、进程与 ACK 见证。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 只使用既有 JDK、Spring JDBC、JUnit 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 保留两个现有测试类和操作者选择器。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 分别核验版本和摘要，注入及复原使用完整精确 CAS。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用不可变定义、执行快照与预览类型。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无新层模型转换或 DTO。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新增日志或证据值输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 无 Bean 或运行配置改动。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用已有 Spring JDBC 测试夹具、JDK 与 JUnit。 | None |
| MC-JSON-001 | Not applicable | N/A | §10 | 沿用既有定义和 checkpoint 格式，无新解析。 | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | 原定义既有 timeout 原样传入第二版，既有时钟和界限保持。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 复用已有 opt-in 开关、角色和地址门。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 沿用独立操作者 fixture 和现有 CAS 控制。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 精确两份测试和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 两个独立 RED、有限回归与真实 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 明确记录实际主库和进程未验收。 | None |

### 20.6 Final verdict

PASS — Ready for user review
