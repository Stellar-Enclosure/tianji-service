# T4-02 S12 Worker 持久回执与 Process 确认重放规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | None |
| Created | 2026-11-11 16:02 Asia/Shanghai |
| Updated | 2026-11-11 16:02 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | PS test-only Worker V1 / Process V4 receipt evidence and existing acknowledgement replay |
| Change Surface | Explicit S12 receipt fixture and finite verification |
| Affected Chapters | §7, §8, §10, §14, §15, §16 |
| Source Requirement | [主计划 T4-02 / S12](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 2c72a6ef2f22c460dcae8cd04591237a2642e101 |
| Amends | None |
| Supersedes | None |
| Depends On | [S12 接收重放 §7](./2026-11-06-02-30-s12-receive-link-replay.md#7-architecture-design) |
| Related Specs | None |
| Related Plans | [S12 Worker receipt E](../plan/2026-11-11-16-03-s12-worker-receipt-e.md) |

## 1. Summary

沿用一次性双租户 Worker 恢复夹具。在真实 Worker 业务回执已提交、首个 PS 完成请求尚未转发时，从原业务 observer 读取 Worker V1 行。随后按原协议丢弃已提交 200 并核对同一命令重放。独立重读原 Worker 行、PS V4 完成行及唯一 Flowable 活动，验证同一业务收据在两套摘要域及两个原所有者下的关系。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | RemoteWorkerPoller#process/#acknowledge, JdbcWorkerExecutionStore#insertReceipt/#replay and W V1 | 本地效果与 W receipt 同源事务，成功回执在确认前已提交。W receipt 主键为 source、tenant、idempotency。 |
| EVD-002 | FlowableWorkerService#complete/#workerOccurrenceKey and WorkerLeaseStore#finish, PS V4 | 原生成功与 PS 完成回执同事务。重放检查完整 actor、token hash、原 occurrence 与结果摘要。 |
| EVD-003 | WorkerRecoveryIT#forwardedAcknowledgement and ExpenseAcceptanceFaultRelay#forwardWorkerCompletion | 已有原请求 SHA、已提交 200 丢失、同一原生租约和同一 WorkerReceiptDTO 的测试。现有断言未直接重读 W V1。 |
| EVD-004 | BusinessProcessAgentAcceptanceIT.Driver#assertDisjoint/#businessCommitted/#verifySources | 原双租户 fixture，独立 Business/PS observer，已校验原业务效果和只读角色。 |
| EVD-005 | 主计划 §14.7、AcquireWorkerTaskResult、WorkerTaskDTO、WorkerReceiptDTO、HttpProcessClient | 标准请求上下文与唯一 AS→PS 私有头范围明确。现有 Worker wire/storage 无已持久化的原 Span Context。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 扩展既有 PS operator 测试，生产模块和示例范围保持。

## 3. Goals and Non-goals

建立不依赖采样的原 Worker 收据与 PS 确认因果关联。真正的跨重启原生 Worker Span Link 需要另行确定受信载体的持久化与传播方案。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS test fixture | Affected | EVD-001–004 | 独立 operator 用例及只读证人 | 细化收据、隔离、时序和测试 | §7, §8, §10, §14, §15, §16 |
| Production worker wire and trace | Context-only | EVD-002/005 | 原 DTO、协议和授权 | 原传递边界 | §9, §18 |
| Worker/PS schema | Unchanged | EVD-001/002 | 原表、原事务及权限 | 仅只读观测 | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 首次转发前读取已提交原 Worker 回执 | Must | 只读 Business observer 通过 source、tenant、idempotency 精确读取 W V1，留本地记录。 | 主计划 T4-02/S12 |
| REQ-002 | 同一已提交完成可通过丢失 200 重放复核 | Must | 同一请求、同一 WorkerReceiptDTO、原 W V1 全投影相等、唯一原 PS V4 及原生活动。 | 主计划 T4-02/S12 |
| REQ-003 | 原范围与摘要域完整 | Must | 独立验明 Worker scope 与 PS full actor，两种原结果摘要，双租户同 correlation 的跨 owner 拒绝。 | 主计划 T4-02/S12 |
| REQ-004 | 独立有限与 E 证据且保持现有模式 | Must | 单独 manifest，16 项有限、包不带夹具、源和资源边界核对；真实 E 明确待执行。 | 主计划 T4-02/S12 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 双租户、独立 observer、显式私有 manifest。 |
| ACTOR-002 | Source reviewer | 复核有限反证、配套回归和产物。 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 确认原 Worker 收据在 Process ACK 重放后仍可核对 | ACTOR-001 | 独立一次性 RECOVERY manifest，各库 fixture 准备完毕 | 同一 W 事务完成后采集、丢弃已提交 200、精确重放、重读 | 跨 tenant/actor、错摘要或额外原生活动拒绝 | 输出仅固定 marker/数量 | REQ-001/002/003 |
| UC-002 | 检查有限 source 与接入边界 | ACTOR-002 | 精确 Service HEAD | 运行 R1/G1/P1/S1 | 缺证据不通过 | 实际 I1 单独保留 | REQ-004 |

## 5. Constraints, Assumptions, and Decisions

既有 RECOVERY manifest 结构及隔离环境。默认入口继续原属性。独立属性只由新测试使用。实际操作另行授权。W 与 PS 读写归属独立，跨库顺序由 relay 的观察阶段决定。

## 6. Project Technology Context

现有 PS acceptance test package，Java 25、JUnit、Spring JDBC、Flowable 和 Worker JDBC。沿用传统层次与已有库角色。

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
| Rule 1 | Yes | EVD-001–005 | 有限核验、原测试类及独立 operator selector。 | §7/8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 全 actor、tenant、source、任务、occurrence、收据、原生活动和摘要。 | §7/8 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 私有不可变证人使用现有 CompleteWorkerTaskCommand 语义。 | §7/8 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–005 | 当前步骤无对应生产变更。 | §7/8 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK 与已有摘要、Flowable 和 JDBC 原能力。 | §7/8 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–005 | 当前步骤无对应生产变更。 | §7/8 | TEST-001–004 | N/A |
| Rule 7 | Yes | EVD-001–005 | 仅独立操作者 manifest 属性，既有配置和 suite 保持。 | §7/8 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 在已有已提交 200 故障夹具上作一次性只读证人。 | §7/8 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | 各库原持久时刻只在各自的域验证；不跨库排序。 | §7/8 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | PS acceptance fixture 使用已有 W、PS 和只读 observer 归属。 | §7/8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Worker committed_at, PS decided_at | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute | Not applicable: no display | W pre/post exact equality; no cross-database timestamp order | Each owner PostgreSQL clock and existing precision | Existing Worker V1 and Process V4 | Step 1 TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 原提交确认重放 | Reuse | REQ-002 | forwardedAcknowledgement | 已有精确故障点 | 测试包可见性 | Use |
| 原 V1/V4 只读证人 | Extend | REQ-001–003 | 原 business effect / PS lease | 无 W V1 pre/post 对照 | 两段只读 SQL 和纯核验 | Use |
| 独立 operator 入口 | Add | REQ-004 | 原 RECOVERY 默认入口 | 需要独立授权、不扩大隐含套件 | 单独 selector/property | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| R1/G1 | 无 | 无真实子进程 | 有限夹具 | 省略 tenant 校验时单一精确失败 |
| I1 | 已有受保护 HTTPS 与三库 observer | 既有 200 丢失与重试 | W V1，PS V4 和原生历史 | 证人不匹配、额外写入或跨租户访问即失败 |

### 7.1 System Architecture Design

同一业务/correlation 由原 USER 启动各租户真实实例。原 Worker 与两个 owner observer 各自受限。沿用 relay 只观察 `DROP_COMMITTED_WORKER_COMPLETE_200` 的首次转发及重试。原收据域、权限和业务提交路径由既有组件持有。

### 7.2 High-Level Design

在 relay 核对原业务效果及已领取租约的回调内，显式模式从 Business observer 先读 W V1。relay 完成原始 PS 请求并丢弃 200，随后验证原请求及同一完成 DTO。Process 正常结束后分别读取 W V1、PS V4 与 Flowable 历史。原 `WorkerRecoveryIT#forwardedAcknowledgement` 保留其断言，只在测试包中复用。

### 7.3 Detailed Design

| Evidence | Scope / provenance |
| --- | --- |
| W V1 pre/post | `tianji_worker.tianji_worker_execution_receipt`，source 为原 fixture 注册的 `expense-process-{tenant}`，正 tenant，原 `FlowableWorkerService.workerOccurrenceKey(tenant,job)`。LIMIT 2 且仅一行。精确 job/occurrence、reserveBusiness/1、TRANSACTIONAL_RECEIPT，核对 handler、原输入摘要、结果、收据及提交时间投影稳定。只读输出是否为空的布尔值。 |
| PS V4 completed | `tianji_process.tianji_worker_lease_receipt`，tenant、原 Worker issuer SHA + subject、BPMN 原 instance、release/model、reserveExpense、原 lease hash 和 COMPLETED。LIMIT 2 且仅一行。核对 topic/schema、idempotency、job、occurrence、业务收据及决定时刻。 |
| W result SHA-256 | `JdbcWorkerExecutionStore#resultHash` 的 `["worker-business-result-v1", businessReceiptId]`，固定空输出；`WorkerDigests` 与 `ProcessRequestDigest` 的原长度分隔原始值编码相同。 |
| PS result SHA-256 | `FlowableWorkerService#complete` 的 `["worker-complete-v1", taskId, activityOccurrenceId, businessReceiptId]` 和原命令输出。分别比较预期值。 |
| Native outcome and isolation | 原 instance/tenant 的 reserveExpense 正常完成恰一次。同一 business/correlation 双租户，独立 START/业务收据和既有跨租户两次受保护 404。 |

## 8. Package Structure and Code File Tree

| Operation | Exact path | Purpose | Step | Gate |
| --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidenceTest.java` | 有限跨租户、跨 actor、摘要域及不可变反证 | Step 1 | R1/G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidence.java` | 跨库证人纯校验 | Step 1 | R1/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` | 显式 operator 入口与两端受限 observer | Step 1 | G1/P1/I1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java` | 复用现有已提交 200 重放步骤 | Step 1 | P1/I1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptAcceptanceIT.java` | 双租户 operator 场景 | Step 1 | P1/I1 |
| CREATE | `docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md` | 锁定原所有者和可观察证据 | Step 1 | S1 |
| CREATE | `docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md` | 锁定验证顺序及路径 | Step 1 | S1 |
| CREATE | `docs/egon/evidence/2026-11-11-s12-worker-receipt-e.md` | 记录已执行与待执行状态 | Step 1 | S1 |

## 9. Interface Definitions

沿用原 Worker acquire/complete 和已授权 HTTPS relay。新属性仅为 operator test 启动项 `tianji.s12.worker-receipt.manifest`。主计划 §14.7 已批准的 AS→PS 私有头继续局限于原结果接收路径。

## 10. POJO and Data Model Design

纯核验使用私有 `Scope`、`WorkerRow`、`ProcessRow`，输出为成功或固定 AssertionError 文本。值对象固定脱敏 toString。业务回执、trace 及凭证不输出。原确认命令只在既有测试内部作用域处理。

## 11. Database Design

Relational model change: No. 原 V1、V4 和 Flowable 历史使用已存在的只读 observer。无触发器或追加存储。

## 12. Frontend Page Design

本场景由 operator fixture 执行。

## 13. Design Patterns and Architecture Principles

使用已有故障 relay 界定真实提交顺序，独立 observer 报告各库原所有权。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 仅删除 W tenant 相等保护。原单一错租户断言应得到精确 JUnit 失败。 |
| TEST-002 | 有限证明正确数据、错 actor、错摘要域、被重写提交时刻。保留已有 relay 与 harness 回归。 |
| TEST-003 | 独立 operator 双租户同 business/correlation，核对精确 pre/post、两个结果摘要、唯一 native 完成和跨租户受保护 404。 |
| TEST-004 | 隔离编译、实际包不包含测试和新配置、严格文件范围。 |

## 15. Non-functional and Cross-cutting Design

仅固定摘要域、受限 SQL 和有限观测。数据库时间只对各自事实作等值或存在性检查，两个时钟不排序。输出固定结果与计数，无原始 ID、业务内容、token、trace、连接、完整日志或头值。测试存在证明与实际 E 结果分开。

## 16. Compatibility, Migration, Rollout, and Rollback

现有 RECOVERY 和其他 suite 保留原 manifest 选择、relay 和断言。新 operator 场景显式提供单独属性。保留全部生产配置、权限、协议、迁移和组件。可回滚单测试提交。

## 17. Alternatives and Decisions

当前 Worker/PS DTO 和持久回执无原 Span Context。同步标准 traceparent 只覆盖实时请求上下文。持久回执证人复用已提交业务事实，为独立 Span Link 设计保留可追溯验证。

## 18. Risks and Open Questions

真实 operator 环境、PS/W/AS 权限和 collector 尚待验收。跨重启关联原 Worker span 需要决定受信 provenance 保存位置、绑定及保留，若扩展头/DTO/存储/OTel 依赖另行确认。当前切片只覆盖采样独立的收据关联。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7 | TEST-003 |
| REQ-002 | UC-001 | §7/10 | TEST-002/003 |
| REQ-003 | UC-001 | §7/15 | TEST-001/002/003 |
| REQ-004 | UC-002 | §8/14/16 | TEST-002/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | PS acceptance fixture 使用已有 W、PS 和只读 observer 归属。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用原 Driver、relay、业务处理、scope key 和原摘要编码。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 原 JDK 25、JUnit、JDBC 与依赖足够。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 有限核验、原测试类及独立 operator selector。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 全 actor、tenant、source、任务、occurrence、收据、原生活动和摘要。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 私有不可变证人使用现有 CompleteWorkerTaskCommand 语义。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | JDK 与已有摘要、Flowable 和 JDBC 原能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 各库原持久时刻只在各自的域验证；不跨库排序。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 仅独立操作者 manifest 属性，既有配置和 suite 保持。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 在已有已提交 200 故障夹具上作一次性只读证人。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 五份测试源、三份文档，无新生产范围。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | 定向反证、16 项有限用例、成品与真实 operator 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 真实 E、Worker 持久 Span Link、collector 与授权独立。 | None |

### 20.6 Final verdict

PASS — Ready for user review
