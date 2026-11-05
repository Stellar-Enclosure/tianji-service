# T4-02 人工任务已提交完成时长

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 一个既有 Process 所有者事务和一个固定维度本地 Timer |
| Created | 2026-11-05 22:31 Asia/Shanghai |
| Updated | 2026-11-05 22:31 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | 当前授权的 Process 与 Case 人工任务新完成回执 |
| Change Surface | Process 服务完成分支、内部任务快照、可选 Timer、配置与有限测试 |
| Affected Chapters | §7, §8, §9, §14, §15, §16 |
| Source Requirement | [主计划 T4-02 与 S12](../../../../docs/plans/agent-framework-upgrade-plan.md) 的任务 SLA 时间信号 |
| Baseline Revision | 7072e5b9153d1cda4ee38be60eeb9651b102a885 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Process Inbox decision delay](2026-11-05-18-43-process-inbox-decision-delay.md) |
| Related Plans | [Human Task completion duration](../plan/2026-11-05-22-32-human-task-completion-duration.md) |

## 1. Summary

Process Server 对新完成的授权人工任务可选记录原生任务创建至已应用回执时刻的时长。样本在原事务成功提交后采集。用原生到期时间与回执数据库时间区分按时、逾期和无到期时间。此指标覆盖已完成样本，开放任务等待与完整 S12 由独立运行证据支撑。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Current PS command | FlowableHumanTaskService#complete/mutate, HumanTaskNativeAccess#find/findCase | 保留原权限、身份和行锁。新 COMPLETE 校验原生任务已消失，调用 receipts.apply，再返回 receipt。幂等回放提前返回。 |
| EVD-002 | Current native model | HumanTaskSnapshot BPMN/CMMN constructors, #projection, PinnedCaseHumanTaskActivityBehavior | 快照已把原生 Task.getCreateTime()/getDueDate() 转为 Instant，并参与版本计算。当前 pinned Case 行为拒绝模型 dueDate。 |
| EVD-003 | Current receipt | HumanTaskReceiptStore#current/currentDeadline/apply, HumanTaskActionResult#applied, HumanTaskActionReceiptDTO.occurredAt | 已应用回执时刻来自同一所有者交易的 PostgreSQL clock_timestamp()，有现行校验和持久化路径。 |
| EVD-004 | Existing observation | AgentResultInboxLagObservation#recordOnCommit, ProcessSecurityConfiguration.AgentResultConfiguration | 同模块用 Spring STATUS_COMMITTED、Micrometer Timer、ObjectProvider.getIfUnique、固定诊断和默认关闭。 |
| EVD-005 | Existing wiring | ProcessSecurityConfiguration.HumanTaskConfiguration, PS application.yml, PS actuator POM | Human Task 持有现行 API/human-tasks 双开前提，复用当前 Registry。 |
| EVD-006 | Evidence limit | MicrometerOutboxMetrics、AgentResultInboxLagObservation、release acceptance matrix S12 | 已有 Outbox backlog 和 delivery 时长，Inbox 已决定时长，尚无人工任务完成时长或完整 S12 E。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 在现有 PS 模块扩展已用的任务完成事务，无 Archetype 生成或示例范围。

## 3. Goals and Non-goals

目标为可选且低基数的当前 Human Task COMPLETE 时长与既有到期状态。实时待办年龄、全量已完成任务统计、阈值、目标、跨服务关联和真实 exporter/collector/S12 继续由各自验证边界决定。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS service and local observation | Affected | EVD-001–004 | 新完成回执提交成功后单次本地样本 | Input/time/outcome/ownership and failures | §7, §8, §9, §14, §15 |
| PS factory and defaults | Affected | EVD-004/005 | 可选注入、默认关闭 | Configuration and release | §7, §8, §9, §14, §16 |
| SQL, public contract, native engine, external metrics | Context-only | EVD-001–006 | 使用原回执、Task、Registry 和权限 | Evidence and rollout limits | §10, §11, §12, §16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Measure native creation to applied receipt time | Must | Duration.between(locked createdAt, receipt.occurredAt), equal yields zero, negative/overflow omitted | EVD-001–003 |
| REQ-002 | Observe only freshly committed COMPLETE | Must | Authorization, replay and native verification preserved. Rollback, unknown commit, replay, read, claim, unclaim or transfer yield no sample | EVD-001/003/004 |
| REQ-003 | Bound data and tags | Must | Fixed name and exactly two app tags, scope process/case and due on_time/late/unscheduled. Equal deadline on_time, no due date unscheduled. | EVD-002/003, S12 |
| REQ-004 | Default-off and isolate failure | Must | Flag absent/false is disabled, unchanged existing dual API prerequisites. Registry unavailable or runtime failure cannot change a business result or expose IDs/data | EVD-004/005 |
| REQ-005 | Separate measurement from acceptance | Must | Zero new DB queries, grants, migrations, jobs, routes or exporter. Mark native clocks and live coverage limits | EVD-001–006 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | PS 操作者 | 在已有获准环境启用计量 | Existing PS config | Existing deploy authority | EVD-005 |
| ACTOR-002 | Current Task owner/operator | 依据原规则完成任务 | Existing COMPLETE | Existing PS IAM and owner scope | EVD-001 |
| ACTOR-003 | 运维观测者 | 读取已授权的本地指标 | Existing MeterRegistry path | Existing observation authority | EVD-004/006 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Count and time newly committed completions | ACTOR-003 | ACTOR-001/002, native owner transaction | New COMPLETE | Existing guards, opt-in, valid times, current registry | One Timer attempt following actual commit | Invalid time, meter failure, replay or uncommitted yields no valid sample | Existing task receipt retained | REQ-001–005 | METRIC-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | 观测复用原生命令与 JDBC 所有者，内部直接回调，业务行为和回执保持原样。 |
| DEC-002 | 唯一新配置 stellar.tianji.process.human-tasks.completion-metrics-enabled，缺席等同 false。 |
| DEC-003 | due 标签比较实际回执 DB 时刻与原生绝对 dueAt，不计算服务等级或业务日历。 |
| DEC-004 | 样本聚合允许六种应用 tag 组合；pinned Case 当前没有约定 dueDate。 |
| ASM-001 | Native Task 和 DB 提供可比较的绝对时刻，但当前有限证据未量化两个时钟的偏差。负时长不采样，真实时钟和 S12 由独立运行检验。 |

## 6. Project Technology Context

现有传统分层的独立 Process Server 使用 Java 25、Flowable 8、Spring JDBC/Tx、Micrometer Actuator 与 JUnit/Mockito。沿用现有技术 adapter、Registry 和事务角色。无新依赖。

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
| Rule 1 | Yes | PS task package | 具名技术组件与现有服务/快照 | §8/§10 | TEST-001 | PASS |
| Rule 2 | Yes | Existing receipt and permission validation | 保留输入校验，内部校验 scope、时刻、事务 | §7/§9 | TEST-001–003 | PASS |
| Rule 3 | Yes | Existing immutable snapshot | 包内读出已有 Instant，不增加 POJO 或映射 | §8/§10 | TEST-001 | PASS |
| Rule 4 | Yes | Existing PS named factory and Inbox technical LoggerFactory | 保留 factory，技术 collaborator 非业务 Bean，固定诊断 | §7/§15 | TEST-003/004 | PASS |
| Rule 5 | Yes | Existing Java and Micrometer/Tx | 无新增工具或依赖 | §6 | TEST-002 | PASS |
| Rule 6 | No | EVD-003 | 原公开 JSON 字段保留 | §9/§10 | TEST-001 | N/A |
| Rule 7 | Yes | PS application.yml | 唯一新增默认 false | §8/§16 | TEST-004 | PASS |
| Rule 9 | No | One existing transaction | 简单观测适配 | §13 | TEST-001 | N/A |
| Rule 10 | Yes | Native and DB Instant | 以 Duration 和绝对时刻比较 | §6.3 | TEST-001/002 | PASS |
| Rule 11 | Yes | PS task ownership | 原分层、CQE 和同源事务 | §7/§11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | HumanTaskSnapshot.createdAt/dueAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: native absolute instants | Not applicable: metric emits no times | Native dueDate optional, no calendar computation | Existing native Date.toInstant milliseconds | Native task reads and public projection unchanged | TEST-001/002 |
| TIME-002 | HumanTaskActionReceiptDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute receipt time | Not applicable: metric emits no times | Current owning DB operation time, commit only gates eligibility | Existing PostgreSQL clock_timestamp and Timestamp.toInstant | Public receipt and JDBC unchanged | TEST-001/003 |
| TIME-003 | Completion interval | Duration | java.time.Duration | Not persisted | Internal | Not applicable: absolute duration | Not applicable: Registry base unit | Zero valid, negative or nanosecond overflow omitted | Preserve input precision and do no timezone conversion | New Timer only | TEST-001/002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing COMPLETE after apply | Expand | REQ-001/002 | Reuse Inbox/Outbox metrics | Neither observes native task creation or due date | One local callback | Add |
| HumanTaskCompletionObservation | New | REQ-002–004 | Inline Micrometer in command | Optional failure and tags would live in native mutation path | Small technical collaborator, no new Bean or I/O | Add |
| Existing factory and false flag | Expand | REQ-004 | Enable always | Live exporter and clock evidence independent | One configuration property | Add |
| Existing history/backlog/export | Keep | REQ-005 | New scan or scheduler | Adds DB/runtime scope and different metric authority | No change to current lifecycle | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 new | Existing receipt | Existing COMPLETE | Current transaction | Existing completion authority |
| Selected design | 0 new | Existing receipt | One local Timer | Isolated STATUS_COMMITTED callback | Completion duration by native due posture |

### 7.1 System Architecture Design

PS HumanTaskController → existing FlowableHumanTaskService → current native and receipt transaction → afterCompletion STATUS_COMMITTED → existing local MeterRegistry.

### 7.2 High-Level Design

沿用同一原生锁定快照的创建和到期时间。完成本地 native 验证后，把原有已应用回执时间与快照时间送进技术适配器。适配器仅在 commit 成功后尝试记录。

### 7.3 Detailed Design

1. 保留两种原构造，默认使用 disabled collaborator。现有 HumanTaskConfiguration 以 ObjectProvider.getIfUnique 提供已存在的 Registry，并保留 Process/Case 所有者依赖。
2. 新 COMPLETE 在 receipts.apply 正常返回后将 receipt.scopeType、before.createdAt、before.dueAt、receipt.occurredAt 传入 recordOnCommit。旧回放提前返回。其他动作仍走原流程。
3. 只接受有效 scope、创建时间、回执时间和当前实际事务/同步。固定 PROCESS→process、CASE→case。dueAt 为空得到 unscheduled，回执时间晚于 dueAt 为 late，其余为 on_time。到期早于创建仍为已存在的绝对时刻。仅回调 STATUS_COMMITTED 记录。
4. Duration、固定 tags 是回调捕获的唯一样本数据。Registry 缺席不注册计量，注册/record/回调注册的 RuntimeException 只可产生每适配器实例最多一次固定诊断。样本丢失不回填。

## 8. Package Structure and Code File Tree

| Action | Exact path in stellar-enclosure-tianji-process-server | Responsibility |
| --- | --- | --- |
| Create | src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskCompletionObservation.java | Fixed timer and commit isolation |
| Modify | src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java | Package-local accessors for existing Instants |
| Modify | src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java | Winning COMPLETE seam, compatible constructors |
| Modify | src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java | Existing factory and optional registry |
| Modify | src/main/resources/application.yml | Default false |
| Modify | src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java | Finite command/commit/due tests |
| Modify | src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java | Prerequisites and wiring |

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| METRIC-001 | Existing local MeterRegistry | tianji.process.human.task.completion.duration | PS | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 METRIC-001 — Committed Human Task completion Timer

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Local observation |
| Independent consumer goal | Measure committed task time and existing native due posture |
| Parameter ownership and derivation | Locked native Task snapshot and existing persisted receipt |
| Direct/no-new-interface alternative | Other queue meters have different time sources |
| Caller use of result | Existing authorized metrics consumers |
| Round trips and failure points | Zero new queries, one optional commit callback |
| Verdict | One Timer and two fixed tags |

##### Identity and purpose

Micrometer Timer `tianji.process.human.task.completion.duration` records native task creation to applied receipt time for committed Human Task completions.

##### Request parameters

无新请求。仅现有双条件 Human Task API 中配置 `stellar.tianji.process.human-tasks.completion-metrics-enabled=true` 选择采集。

##### Success response

Existing Timer count and Duration use app tags `scope=process|case` and `due=on_time|late|unscheduled`. Registry common tags remain under existing owner configuration. No task, tenant, correlation, definition, owner, release, approval, form or raw errors in the metric. An absent sample is distinct from a measured zero duration.

##### Error responses

现有业务错误和回执照旧。无 Registry、无当前同步、回滚、无法确定提交、异常或无效时间均不生成有效样本。正常选择器没有额外业务响应。

##### Interface logic for frontend and consumers

当前授权 Registry 后端可读取已完成样本。待办分布、SLA 阈值和完整 S12 分别评审与验收。

##### Compatibility and verification

保留当前构造入口、API、权限、回执与完整任务执行语义。验证 TEST-001–005。

## 10. POJO and Data Model Design

仅复用现有 HumanTaskSnapshot 与 HumanTaskActionReceiptDTO，observer 入口接收现有 enum 和不可变 Instant。无新 PO/DTO/Converter。

## 11. Database Design

无 SQL、查询、写入、索引、Flyway、事务源、角色或权限变化。现有 HumanTaskReceiptStore 的写入与 native 事务保留。

## 12. Frontend Page Design

无前端变化。仅当前授权的本地 metrics 路径消费。

## 13. Design Patterns and Architecture Principles

采用现有 Spring 事务同步的直接 Observer 模式。只分离可选 Registry、固定标签、采样失败，服务继续拥有命令和回执。单个固定算法无需注册表或新事件。

## 14. Test Design

| Test ID | Exact finite or separately gated proof | Expected observation |
| --- | --- | --- |
| TEST-001 | FlowableHumanTaskServiceTest#observes_only_fresh_committed_task_completion | 同一快照和回执，commit 前为空，后 exact on_time，CLAIM 和 replay 无增量 |
| TEST-002 | FlowableHumanTaskServiceTest#classifies_only_fixed_due_postures_and_never_emits_invalid_or_rolled_back_times | late、Case unscheduled、零值、负值/溢出、无事务/rollback |
| TEST-003 | FlowableHumanTaskServiceTest#failed_commit_and_unavailable_registry_do_not_claim_a_sample | commit 失败无样本，Registry 故障且原 COMPLETE 成功 |
| TEST-004 | ProcessApiConfigurationTest#humanTaskCompletionObservationIsExplicitAndKeepsExistingPrerequisites | 缺席/false/true、单 flag 不启服务、Process/Case 参数及 YAML false |
| TEST-005 | FlowableHumanTaskServiceTest, ProcessApiConfigurationTest, HumanTaskSnapshotTest, HumanTaskReceiptStoreTest | 有限已有命令和配置；不启动 Flowable、应用或 PostgreSQL |

## 15. Non-functional and Cross-cutting Design

仅局部已完成样本。业务路径无新增数据库或网络往返。Meter 调用在 Spring 完成回调中，故障不改业务结果，但同步本地调用成本仍需在真实负载确认。只允许固定安全诊断。部署的 common tags 和 collector 配置继续受既有治理。

## 16. Compatibility, Migration, Rollout, and Rollback

默认关闭。旧构造继续默认关闭。部署方在独立运行授权下按需启用并验证现有 Registry。配置设回 false 即停止新采样，无历史数据回填或迁移。

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| Compute oldest native task by scanning | Deferred | 需新增查询、运行授权与独立一致性设计 |
| Observe after native operation and before commit | Rejected | 可计入失败事务 |
| New global or per-task tags | Rejected | 现有维度足以区分已完成样本，标识有隐私/基数影响 |
| Post-commit local Timer | Selected | 现有交易、时间与 Registry 足够 |

## 18. Risks and Open Questions

| ID | Risk / open question | Disposition |
| --- | --- | --- |
| OPEN-001 | Native and DB clocks may diverge | Record semantics explicitly, negative durations omitted, validate actual clock alignment independently |
| OPEN-002 | Registry/exporter runtime and S12 | 独立授权的真实部署、采集和 workload/阈值评审 |
| OPEN-003 | Completion survivor bias and absent samples | 面板仅按已完成样本解释，待办年龄独立设计 |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/METRIC-001 | §6.3/§7.3 | TEST-001/002 |
| REQ-002 | UC-001/METRIC-001 | §7/§9 | TEST-001–003 |
| REQ-003 | UC-001/METRIC-001 | §7.3/§9 | TEST-001/002 |
| REQ-004 | UC-001/METRIC-001 | §8/§16 | TEST-003/004 |
| REQ-005 | UC-001/METRIC-001 | §11/§14/§18 | TEST-005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Same PS owner and persisted flow | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | Existing native Task/receipt, Tx and Registry | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing packaged Micrometer/Tx | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Narrow semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | Existing ownership and exact time checks | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Existing immutable HumanTaskSnapshot | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO/PO mapping change | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One safe fixed optional diagnostic, no IDs | None |
| MC-BEAN-001 | Applicable | PASS | §7/8 | Existing HumanTaskConfiguration factory | None |
| MC-UTIL-001 | Applicable | PASS | §6 | JDK and current Micrometer/Tx | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing wire bytes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Existing native and DB instants, exact Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §8/9/16 | One false flag and existing gates | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct adapter, simple | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | No new queries/DDL/export or runtime authority | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact future finite gates and runtime boundary | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source slice independently reviewable | Pending live backlog/SLO/S12 decisions remain outside source slice |

### 20.6 Final verdict

PASS — Ready for user review

本规范为待审阅源码切片。真实数据库、Flowable 运行、exporter、S12 与生产 SLA 数值尚未验证。
