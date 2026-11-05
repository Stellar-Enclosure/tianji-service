# T4-02 天机 Process Inbox 决策时延观测

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 单一 Process 所有者，现有持久时间与事务完成回调，一个固定标签 Timer |
| Created | 2026-11-05 18:43 Asia/Shanghai |
| Updated | 2026-11-05 18:43 Asia/Shanghai |
| Owner | 天机 Process Server 观测所有者 |
| Repository | tianji-service |
| Scope | PS Agent result Inbox 从接收到终态决策的已提交样本，默认关闭 |
| Change Surface | 既有 Inbox 回读与配置，小型 Micrometer 适配和有限回归 |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 §19 T4-02 与 §17/S12](../../../../docs/plans/agent-framework-upgrade-plan.md) 的运行时延证据首切片 |
| Baseline Revision | tianji-service 40ba8e1f0f968fd374cea389e9febed6bf952c7a，Components 87af09fea9a9d58e7127b009d76f60438c79e569 |
| Amends | None |
| Supersedes | None |
| Depends On | [T3-12 expense 验收边界](2026-11-05-15-13-expense-four-path-acceptance.md) §9/§14 |
| Related Specs | [AS 终态 Outbox Span Links](2026-11-05-17-35-agent-terminal-outbox-span-links.md) |
| Related Plans | [Inbox decision delay implementation](../plan/2026-11-05-18-47-process-inbox-decision-delay.md) |

## 1. Summary

Process Agent result Inbox 在一条结果持久接收后，可能重试、等候原生消息订阅、忽略晚到结果或应用消息。使用同一条已持久化 Inbox 行的 received_at 与 decided_at，新增一个按终态区分的 Micrometer Timer。每次提交成功的终态转换最多尝试采集一条。现有 Outbox backlog、delivery timer、retry 和 dead 指标分别继续承担其既有信号。默认关闭，发布者可以单独验证此项源码能力，再由实际采集环境验证 S12 和 SLA。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Component source | Components JdbcOutboxStore#countBacklog, OutboxDispatcher#dispatch/updateBacklog, MicrometerOutboxMetrics | 在选定 JDBC schema 的 egon_cola_outbox_message 内计数 PENDING、PROCESSING、RETRY_WAIT。查询没有按 channel 或到期时间过滤。Dispatcher 取得本地 permit 并成功调用 claim 后更新 gauge，包括成功的空 batch。Gauge 初值为零，未采样或测量失败时可能保留旧值。 |
| EVD-002 | Component source | OutboxMetricsAutoConfiguration, TransactionalOutboxAutoConfiguration, MicrometerOutboxMetrics#delivery/retry/dead/leaseLost | 当已有 DataSource 与 MeterRegistry 且 Outbox 启用时，组件提供 egon.cola.outbox.backlog、egon.cola.outbox.delivery.duration、retry、dead、lease_lost 等。delivery.duration 观测 transport 尝试，使用 channel/result，和 Inbox 决策时长含义不同。没有 oldest age 或 last successful measurement meter。 |
| EVD-003 | AS and PS source | AS application.yml 与 TianjiAgentTerminalOutboxConfiguration，PS application.yml 和 PS POM | AS 基础配置 Outbox disabled。终态 publisher 的 opt-in guard 核对 JDBC、当前 Agent DataSource、AgentLedgerService 和 publisher。PS 基础 Outbox 指向 tianji_process JDBC，polling disabled。两个 Server 已依赖 Actuator。当前 PS exec 包实际包含 micrometer-core 1.17.2 与 spring-tx 7.0.10。 |
| EVD-004 | PS source and migration | PS V6__add_agent_result_inbox.sql、AgentResultInbox#receive/due/finish/read | receive 用数据库时钟写 received_at。finish 对 PENDING 条件更新 state/decided_at，现有 find(..., true) 回读胜出行。read 已校验终态 decided_at 非空。due 选择并推进 next_attempt_at，是操作写路径。V6 的 PENDING 索引以 tenant/producer/source 为前缀并按 next_attempt_at/event_id 排序，缺少与全局 received_at 最老值匹配的索引。 |
| EVD-005 | PS current owner | AgentResultCorrelationService#apply/applyInTransaction/reconcile, ProcessSecurityConfiguration.AgentResultConfiguration | apply 拒绝外部已活跃事务，使用 Process 同源 DataSourceTransactionManager。已有终态直接返回既有回执。reschedule 可能在重试到期或耗尽时走同一 finish。Process 配置需要 API 与 agent-results 双开。 |
| EVD-006 | AR source and migration | V1__create_agent_ledger.sql、JdbcAgentLedgerRepository#insertRun 及 dispatch 读 | 当前插入 run 初态为 QUEUED，已有 created_at、deadline_at、lease_epoch 与部分 dispatch 索引。此处没有独立的通用 queued_at/重新入队时戳合同。按 created_at 可定义另一个 owner 的初始创建年龄，但不能作为 Process Inbox 或 Worker 的排队样本。 |
| EVD-007 | Adjacent source | [AS 终态 Outbox Span Links](2026-11-05-17-35-agent-terminal-outbox-span-links.md) 与 AgentResultSpanObservation | 现有 AS 可选 OTel Link 和固定 carrier result 计数属于 trace 诊断。采样、传播或 Span Link 的缺席与这里的持久时间观测相互独立。 |
| EVD-008 | Framework APIs | [Spring 7.0.10 TransactionSynchronization](https://docs.spring.io/spring-framework/docs/7.0.10/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCompletion(int)) · [Micrometer Timer](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) | STATUS_COMMITTED 用于辨别事务完成状态。Timer 提供次数与总时长，导出单位及额外分布统计由现有 Registry/backend 决定。 |

没有现成的 PS result Inbox 持久接收到已提交终态决策的 Timer。现有 E 报告源码、有限用例和制品证明均不能确定真实运行时分布或阈值。

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 复用现有 PS 模块的 Inbox 与配置，未生成新模块、示例或迁移。

## 3. Goals and Non-goals

交付可选择的 Inbox 终态决策时延数据。保留现有消息、重试、回执、数据库和权限行为。实时最老待处理年龄、动态 backlog 查询、端到端 SLA/告警阈值、跨源 correlation、Worker/Case 指标、UNKNOWN 工具告警、S12 真实验收另行设计和验证。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS Inbox projection and Timer | Affected | AgentResultInbox#finish/read and same-source TransactionTemplate | 现有终态回读中保留 decidedAt，提交完成后本地记录 | Time, count, failures, exact tags | §7, §8, §9, §10, §14, §15 |
| PS config and test | Affected | ProcessSecurityConfiguration.AgentResultConfiguration, application.yml | 新默认关闭开关，现有 MeterRegistry | Optional wiring, compatibility | §8, §9, §14, §16 |
| AS/AR/W/Components, SQL, API/trace | Context-only | EVD-001–007 | 沿用本地所有者与原运行行为 | Reuse and runtime boundaries | §11, §12, §16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Measure one persisted decision interval | Must | APPLIED/IGNORED_LATE/DEAD 样本使用同一行 received_at 和 decided_at，零值允许，负值或纳秒溢出不记录 | EVD-004 |
| REQ-002 | Respect committed ownership and replay | Must | 只有 PENDING→终态成功回读与 STATUS_COMMITTED 产生尝试。rollback、旧终态回放、仍 PENDING、接收或查读不增加 Timer | EVD-004/005 |
| REQ-003 | Bound metrics dimensions and failure effects | Must | 固定一个 Meter 名和一个有限标签，Registry 缺席/不唯一/失败不影响持久化、原异常或回执 | Master S12, EVD-008 |
| REQ-004 | Default off and reuse existing owner resources | Must | 默认关闭、原有双 flag 原样、无新增 SQL/查询/轮询/依赖/endpoint | EVD-003/005 |
| REQ-005 | Preserve evidence boundary | Must | 明确区分 Outbox backlog、transport 尝试时长、Inbox 已决定样本与当前等待，有限测试不等价于运行时 S12 或阈值 | EVD-001/002/007 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | PS 操作者 | 选择启用已有运行环境内的诊断 | Existing operator PS config | 当前 PS 部署权威 | EVD-003/005 |
| ACTOR-002 | PS Inbox 维护 | 完成原有 Inbox 终态并可选记录 | Existing owner transaction | 原 producer registry 与同源锁 | EVD-004/005 |
| ACTOR-003 | 观测审阅者 | 读取现有授权 MeterRegistry 后端 | Existing collector/access policy | 保持当前访问规则 | EVD-008 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe a committed result decision interval | ACTOR-001 | ACTOR-002/003 | Existing Inbox finish and commit | API/Inbox opt-in, lag-metrics-enabled=true and usable local Registry | One terminal-result Timer attempt with database-derived duration | Off, uncommitted, replay, invalid interval or meter unavailable means no valid sample | Normal result flow retained | REQ-001–005 | METRIC-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | 仅 PS owner 新增一个 Meter。现有 AS、AR、Worker、Components 数据或组件行为不变。 |
| DEC-002 | 使用既有终态更新和回读，无新 SQL、调度、后台表、数据字段或导出配置。 |
| DEC-003 | 开关缺席等同 false。启用依赖现有 API/agent-results 路径。Registry 不可用不影响原路径。 |
| DEC-004 | Timer 统计的是收到到决定的终态样本，包括等待重试/订阅绑定的时间，不提供未完成队列视图或跨服务端到端时长。 |
| DEC-005 | 保留已有运行环境的 Meter 访问控制、registry/collector 设置与默认应用关闭配置。生产 SLA、分位数策略与告警由未来证据评审确定。 |

## 6. Project Technology Context

现有 JDK25、Spring Boot 4.1.2、Micrometer Core 1.17.2、Spring JDBC/Tx 7.0.10、Flowable 8.0.0、PostgreSQL timestamptz、PS Actuator 和 Mockito/JUnit。直接使用 Timer、ObjectProvider、Duration 和现有 TransactionSynchronizationManager。同源 transaction manager 已被现有 Inbox 构造及 AgentResultCorrelationService 验证。

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
| Rule 1 | Yes | PS bridge technical types and private Entry | Named AgentResultInboxLagObservation and existing Entry | §8/§10 | TEST-001/004 | PASS |
| Rule 2 | Yes | Existing receipt/registry and same-source contract | Existing owner validation plus local terminal outcome and valid duration | §7/§9 | TEST-001/002 | PASS |
| Rule 3 | Yes | Existing immutable Inbox Entry record | Internal immutable timestamp, no new transport DTO or conversion layer | §10 | TEST-002 | PASS |
| Rule 4 | Yes | Existing explicitly named Spring config and technical diagnostics | Current factory retained, new adapter is ordinary technical value collaborator, existing PS SLF4J LoggerFactory and fixed failure diagnostic; no Lombok dependency | §7/§8 | TEST-003/004 | PASS |
| Rule 5 | Yes | JDK + existing Actuator/Micrometer and Tx APIs | Use approved existing APIs, no utility dependency | §6/§8 | TEST-001/005 | PASS |
| Rule 6 | No | Existing public receipt fields unchanged | No wire JSON change | §9/§10 | TEST-005 | N/A |
| Rule 7 | Yes | Single PS application.yml | Add exact false flag, all existing config keys retained | §8/§16 | TEST-004 | PASS |
| Rule 9 | No | Single fixed observation path | Direct observer adapter, no event strategy registry | §13 | TEST-001/003 | N/A |
| Rule 10 | Yes | Existing DB timestamptz to Instant | Duration between same source immutable instants | §6.3/§9 | TEST-001/002 | PASS |
| Rule 11 | Yes | Existing owner-local Inbox and Spring transaction | Reuse current PS layering and CQE semantics, no new DB operation, event or layer | §7/§11 | TEST-002/005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Inbox received_at, decided_at, receipt.receivedAt, internal Entry.decidedAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: DB operational time | Not applicable: raw time excluded | Same row, same owning PostgreSQL source | Existing timestamptz and getTimestamp().toInstant(), DB clock | No JDBC wire or SQL change | TEST-001/002 |
| TIME-002 | decidedAt minus receivedAt | Duration | java.time.Duration | Not persisted | Internal | Not applicable: duration | Not applicable: registry-specific metric unit | Equal stamps valid; negative or nanosecond-overflow interval suppressed | Preserve existing microsecond input and record Duration without rounding | New Timer, existing response untouched | TEST-001/002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing finish/readback hook | Expand | REQ-001/002 | Component dispatch Timer | Measures transport attempt, lacks Process timestamps | One optional commit callback | Add |
| AgentResultInboxLagObservation | New | REQ-002/003/004 | Inline meter code in store | Mixes optional Registry/failure contract into persisted path | One small adapter, no new Bean or I/O | Add |
| Existing PS factory and false flag | Expand | REQ-004 | Enable telemetry by default | No confirmed collector or source benchmark | One flag with no new startup behavior | Add |
| Global pending-age SQL/scheduler | Keep | REQ-005 | Due query or cross-owner metric | Due has DML; V6 index not aligned with global age | Future schema/plan/proof | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 new | Existing receipt states | Same Inbox row | Existing transaction and no Inbox delay sample | Outbox dispatch stats retain meaning |
| Selected design | 0 new | Existing receipt states | One local Timer | Callback only after commit, meter failure contained | Scope-local observed decision latency |

### 7.1 System Architecture Design

Owner PS Agent result Inbox → existing exact row update/readback → same transaction STATUS_COMMITTED callback → existing local MeterRegistry → existing metrics delivery if configured by its owner. AS terminal Outbox, external Worker, Process native engine and their existing data paths remain separate.

### 7.2 High-Level Design

Extend internal readback Entry with nullable decidedAt exactly as existing row validation determines. In finish, retain the existing successful count and exact lookup. The returned receipt stays the existing public value. Only after complete successful readback, pass the terminal enum and duration to the observation helper to register on the current synchronization. Capture only outcome and duration in callback.

### 7.3 Detailed Design

1. The original AgentResultInbox constructor delegates to a disabled observation collaborator. The current PS AgentResultConfiguration factory reads the boolean flag and passes either the disabled instance or an adapter using existing ObjectProvider<MeterRegistry>. The existing API and Inbox activation guards remain authoritative. Absence of a uniquely resolvable registry yields no sample.
2. finish performs its unchanged PENDING predicate update and current exact locked find. For APPLIED, IGNORED_LATE, DEAD, derive Duration.between(readback.receipt.receivedAt, readback.decidedAt). Reject a negative or nanosecond-overflow interval. Zero is a valid measurement. If returned state differs from the expected terminal state, omit telemetry and return the original result without any new business rejection. Existing terminal replay, rescheduling without a decision and other read paths do not invoke this seam.
3. With an active synchronization, register a TransactionSynchronization whose afterCompletion records only STATUS_COMMITTED. No database, network or context lookup from callback except local Registry resolution and Timer.record. If registry/meter/callback registration throws a RuntimeException, do not propagate into owner work; any local diagnostic is fixed text and emitted at most once per active adapter lifetime, with no raw exception or IDs. Optional unavailable instrumentation does not generate synthetic values.
4. This is best-effort operational telemetry. Crash after durable commit, unavailable registry or export loss may lose a sample. The durable Inbox remains the lifecycle authority. Metrics do not redrive or infer missing lifecycle rows.

## 8. Package Structure and Code File Tree

All paths below belong to stellar-enclosure-tianji-process-server. Technical configuration and Java ownership stay in the current packages.

| Change | Exact relative path within PS | Responsibility |
| --- | --- | --- |
| C | src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservation.java | Fixed Timer mapping, optional registry, committed synchronization and contained failure |
| M | src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java | Existing decided_at readback, only winning finish calls observer, old constructor remains |
| M | src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java | Existing AgentResultConfiguration factory selects optional observer using property and MeterRegistry provider |
| M | src/main/resources/application.yml | Exact PS lag flag defaults false |
| C | src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservationTest.java | Bounded Timer/transaction/replay/failure tests without PostgreSQL or engine startup |
| M | src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java | Existing finite mock configuration checks assert disabled/opt-in and non-intrusive boundaries |

A Plan may archive actual finite evidence under existing docs/egon/evidence. Production paths are the six listed above.

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| METRIC-001 | Existing local MeterRegistry | tianji.process.agent.result.inbox.decision.delay | PS | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 METRIC-001 — 已提交 Inbox 终态决策时长

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | 新增本地运维 Timer |
| Independent consumer goal | 区分已收但较慢决定的终态样本以及晚到或死信结果的时延 |
| Parameter ownership and derivation | 两个时间与 outcome 来自同一已提交 PS Inbox 行 |
| Direct/no-new-interface alternative | Component Outbox backlog/delivery 保持原义，无法推导 Inbox 决策时间 |
| Caller use of result | 当前授权 Collector 的局部诊断与将来真实基线测量 |
| Round trips and failure points | 零新查询/网络，单次事务完成回调 |
| Verdict | 一个固定名与有限标签 |

##### Identity and purpose

Meter 名 tianji.process.agent.result.inbox.decision.delay，Micrometer Timer，描述为 Process Agent result Inbox receipt-to-decision duration for committed terminal decisions。当前本地 Registry/Actuator 授权和已有自动化配置负责传递。

##### Request parameters

该接口无请求载体。操作者配置 stellar.tianji.process.agent-results.lag-metrics-enabled=true 时启用，缺席或 false 时关闭。现有 stellar.tianji.process.api.enabled 与 agent-results.enabled 仍决定 Inbox 是否存在。配置不创建新 endpoint、Registry 或 exporter。

##### Success response

Timer 记录 Duration（秒单位输出由现有 backend 决定），一个应用标签 outcome。仅允许 APPLIED→applied，IGNORED_LATE→ignored_late，DEAD→dead。Registry 可以附加已有环境级 common tags。应用不会附加 tenant、producer、source、run、event、definition、business/correlation、trace/span、reason、URL、payload 或异常文本。新 meter 按首条有效样本注册，零样本无需注册零值。输出代表样本数和时长，分位数/分桶/阈值由既有后端和未来实测决定。

##### Error responses

保持现有业务返回及错误。关闭、Registry 不可用、缺少当前同步、回滚、负时长、纳秒溢出或观察失败无有效采样。已记录的 samples 不回写原流程；没有回填或补送历史行的行为。

##### Interface logic for frontend and consumers

现有采集平台读本地已经注册的 Timer。消费端把结果视为终态样本分布，不能将无样本或低时延推断为无等待积压。Outbox gauge 值仍是成功刷新时非终态消息数，不能推算 Inbox pending age。

##### Compatibility and verification

旧 Inbox 构造、既有包内 Entry 五参构造（测试兼容）、AgentResultReceipt、现有 route、Process 事务及 T4-02 trace/用量保持原合同。使用 TEST-001–005 的实际有限路径。任何真实 S12 证据单独标记。

## 10. POJO and Data Model Design

AgentResultInbox.Entry 为当前包内不可变记录，增加 Instant decidedAt 只在回读到终态时非空。已经读取的 receivedAt 仍归 AgentResultReceipt。适配器内部将终态 enum 映射到三固定文字量。无新 DTO、持久字段或通用标签 map。事务回调只持有低基数 outcome 和 Duration。

## 11. Database Design

复用 PS V6 表 received_at/decided_at 的原值和既有 SQL。迁移、权限、索引与 SQL 保持原样。当前 V6 due 索引适用精确 producer/source 和 next_attempt_at 有界领取，缺少按 received_at 的全局最老等待查询证据。未做 PostgreSQL EXPLAIN 或真实查询计划验证。

## 12. Frontend Page Design

不涉及页面和浏览器。保持现有业务接口与 Actuator 授权路由，诊断开关不开放 /actuator/metrics。

## 13. Design Patterns and Architecture Principles

选择现有事务同步的直接 Observer 技术适配。隔离可选 MeterRegistry、固定标签和故障处理有助于保留 owner 仓储的事务语义。单一算法与单一消费点无需策略框架、额外事件、中间件或观察服务。

## 14. Test Design

| Test ID | Exact finite or separately gated proof | Expected observation |
| --- | --- | --- |
| TEST-001 | AgentResultInboxLagObservationTest#recordsOnlyCommittedTerminalOutcomesWithFixedTags | SimpleMeterRegistry 验证精确三标签、样本数及纳秒时长，无负值/溢出伪零、零时长有效、回调前无增量 |
| TEST-002 | AgentResultInboxLagObservationTest#rollbackAndNonTerminalOrReplayDoNotRecord | 通过现有 Spring 事务同步和有限 JDBC mocks 核对 STATUS_ROLLED_BACK/UNKNOWN、PENDING/旧终态读、胜出 finish 回读输入与重复调用边界。若测试只覆盖 callback，记录 JDBC 胜出验证为待 I。 |
| TEST-003 | AgentResultInboxLagObservationTest#unavailableRegistryAndMeterFailureAreIsolated | 缺失/不唯一 registry、构造/record 抛异常、无同步、DB 时钟倒退，均保持原路径和安全诊断 |
| TEST-004 | ProcessApiConfigurationTest#agentResultInboxLagObservationIsExplicitAndDoesNotChangePrerequisites | 关闭/缺席默认无 meter，单开指标不引导 Inbox，双既有 flag 正确时可 opt in，验证实际 PS YAML false |
| TEST-005 | Existing ProcessApiConfigurationTest, AgentDispatchStoreTest, existing AgentResultInboxPostgresTest compile gate | 正常编译及已有有限语义/配置回归，PG I 仅编译时证据标注 compile-only |

真正的 PostgreSQL 验证需独立授权的隔离 ProcessDB，检验真实一行回读、互斥提交、回滚与重复。真实 S12 需现有两独立应用、Fake 模型、真实 collector 与 metrics sink、身份隔离和敏感信息扫描。运行时与 SLA 验收单独启用和记录。

## 15. Non-functional and Cross-cutting Design

记录路径不发起新查询、事务、线程或外部 I/O。生产默认关闭；启用后一条已决定且提交的行最多登记一次 callback，严格 O(1) 额外本地操作。标签固定三值，Registry 全局策略照旧。指标故障不得改写、重试或回滚业务，固定诊断避免高频日志或泄露。

## 16. Compatibility, Migration, Rollout, and Rollback

不涉及迁移或新依赖。默认关闭，现有部署保持原运行。通过独立运行审批，在已有隔离配置启用并读取实际 exporter 数据。回滚设置 false，或撤回本切片源代码。旧样本留在已有后端按原保留策略处理，Inbox 行无需迁移。

## 17. Alternatives and Decisions

| Alternative | Disposition | Evidence |
| --- | --- | --- |
| Reuse Component Outbox backlog as Inbox lag | Rejected | Current JDBC count is nonterminal Outbox count and delivery timer is transport attempt |
| Add global oldest Inbox pending age scan | Deferred | V6 current index does not align with received_at; needs actual cardinality/query plan and operator interval decision |
| Read-only use of Inbox due() | Rejected | Method changes next_attempt_at and claims observation scope |
| Record one Process owner-local terminal Timer | Selected | Exact persisted timestamps and winner readback already present, no extra SQL |
| Define SLO buckets, thresholds or Alert | Deferred | No reviewed real runtime distribution, window or target |

## 18. Risks and Open Questions

| ID | Scope | Finding | Required action |
| --- | --- | --- | --- |
| RISK-001 | Operational measure | Completion sampling misses pending jobs, and best-effort meter can miss committed observations | Use existing owner inspection for durable facts and separately review current pending-age design |
| OPEN-001 | Live backlog | Global oldest Inbox scan lacks aligned index and approved sampling budget. Existing due query mutates retry schedule | Before new backlog slice, acquire read-only representative query-plan/cardinality proof and choose exact scope/index or bounded method |
| OPEN-002 | SLO and S12 | No agreed workload, bucket, time window, collector result, runtime aggregation or threshold | Separate real I/E S12 and numeric SLO governance; keep this feature default off |
| OPEN-003 | Cross-source correlation | AS Link and PS local Timer cannot substitute for full process/run/tool/model correlation or durable join | Track S12 correlation/search independently |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/METRIC-001 | §6.3/§7.3/§9 | TEST-001/002 |
| REQ-002 | UC-001/METRIC-001 | §7.3/§10 | TEST-001/002/005 |
| REQ-003 | UC-001/METRIC-001 | §9/§15 | TEST-001/003 |
| REQ-004 | UC-001/METRIC-001 | §7/§8/§16 | TEST-004/005 |
| REQ-005 | UC-001/METRIC-001 | §2/§11/§18 | TEST-005 and evidence boundary |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Same PS owner and persisted flow | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–008 | Existing Outbox metrics, Inbox DB time, Tx and Registry | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing packaged Micrometer/Tx | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Narrow semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | Existing ownership with exact terminal/time checks | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Existing immutable internal Entry | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO/PO mapping change | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One safe fixed optional diagnostic, no IDs | None |
| MC-BEAN-001 | Applicable | PASS | §7/8 | Existing PS named factory, no new runtime bean | None |
| MC-UTIL-001 | Applicable | PASS | §6 | JDK and current Micrometer/Tx | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing wire bytes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Exact DB times and Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §8/9/16 | One false flag and existing gates | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct adapter, simple | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | No new queries/DDL/export or runtime authority | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact future finite gates and runtime boundary | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source slice independently reviewable | Pending live backlog/SLO/S12 decisions remain outside source slice |

### 20.6 Final verdict

PASS — Ready for user review

本次为仓库外设计和静态核对。无源码、Maven、应用、数据库或运行环境操作。
