# T4-02 Agent 有界待认领队列观测

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One bounded technical read, one optional Gauge and existing poll cadence |
| Created | 2026-11-06 00:24 Asia/Shanghai |
| Updated | 2026-11-06 00:24 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | Actual primary Agent QUEUED lifecycle capped observation, default off |
| Change Surface | AR bounded scalar read, optional AS poller sample and MeterRegistry Gauge |
| Affected Chapters | §7, §8, §9, §14, §15, §16 |
| Source Requirement | [主计划 T4-02 与 S12](../../../../docs/plans/agent-framework-upgrade-plan.md) 队列积压观测 |
| Baseline Revision | 9a509342767d6b01dfa94e482b530cc69ccc5536 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Agent initial claim delay](2026-11-05-23-44-agent-initial-claim-delay.md) |
| Related Plans | [Queued backlog implementation](../plan/2026-11-06-00-25-agent-queued-backlog-metric.md) |

## 1. Summary

可选采样当前 Agent 主库中未取消、未过期的 QUEUED 记录。返回 0 至 256 的准确计数，257 表示至少 257。复用现有调度轮询，在正常发现和派发尝试之后最多每 30 秒读取一次。Gauge 只读内存，失败或过期使用 NaN。此数值来自持久队列，不代表 grant-ready 子集或执行容量。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Existing data | AR V1__create_agent_ledger.sql and JdbcAgentLedgerRepository#queuedRuns | Existing QUEUED dispatch index covers status/deadline/created_at. Current queue read already excludes cancelled/expired rows. |
| EVD-002 | Existing dispatch | JdbcAgentLedgerRepository#grantReadyRuns; TianjiGrantDispatchPoller#poll | Discovery is paged, capped, grant-filtered and capacity constrained, so its batch cannot yield the full current queue population. |
| EVD-003 | Existing primary read | AgentLedgerService#listByCorrelation, JdbcAgentLedgerRepository#beginPrimaryReadObservation | Existing source binds primary first and verifies PostgreSQL physical read-only and standby state. |
| EVD-004 | Existing AS wiring | TianjiGrantDispatchConfiguration; AS application.yml; existing Micrometer | Current poller prerequisites and default-off observation config convention. MeterRegistry exists through AS Actuator. |
| EVD-005 | Adjacent metrics | Existing first claim delay, Process Inbox, Human Task completion, Components Outbox backlog | Different lifecycle signals, no current Agent pending queue depth. |
| EVD-006 | Current access | TianjiAgentSecurityConfiguration and existing AgentDB dispatcher SELECT | No new endpoint/grant. Existing host scope and registry configuration remain authoritative. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extension of current AR/AS storage and poller, no generated modules or examples.

## 3. Goals and Non-goals

交付明确标注上限和新鲜度的当前 Agent lifecycle queue sample。全量大队列精确计数、grant-ready 度量、跨库汇总、最老任务年龄、阈值、告警部署、供应商费用与完整 S12 各自保持独立。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR exact technical aggregate read | Affected | EVD-001/003 | Bounded SELECT, safe source, deadline and failure | Query and time policy | §7/§8/§9/§14/§15 |
| AS cadence, local gauge, config | Affected | EVD-002/004/005 | Optional post-dispatch cached scalar, freshness | Exact owner behavior, gate and package | §7/§8/§9/§14/§15/§16 |
| IAM, DDL, active business dispatch | Context-only | EVD-001–006 | Same existing permissions and owner lifecycle | Source and runtime boundaries | §10/§11/§16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Sample actual lifecycle queue | Must | Exactly QUEUED, noncancelled and deadline after statement time. 0–256 exact and 257 saturated, scalar-only query | EVD-001/002 |
| REQ-002 | Bound and isolate database access | Must | Existing bound primary, physical read-only, 100 ms statement budget, one-second Spring transaction timeout, no ambient/interrupt | EVD-003 |
| REQ-003 | Rate-limit and expose freshness | Must | Existing successful poll only after dispatch work, max one attempt per 30 seconds, stale at 60 seconds or immediate failure/stop, no scrape SQL | EVD-002/004 |
| REQ-004 | Preserve source ownership | Must | One default-false opt-in, no DB read without unique existing registry, no new scheduler, permissions, DDL or dependency | EVD-004/006 |
| REQ-005 | Verify without overstating acceptance | Must | Finite exact tests and static AS artifact, runtime plan and exporter separately gated | EVD-005/006 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | Existing AS operator | Enable optional technical metric | Current deployment config | Existing metrics and storage authority | EVD-004/006 |
| ACTOR-002 | Existing dispatch poller | Discover/admit work and optionally sample queue afterward | Existing scheduler | Exact owning ledger and AS registry | EVD-002/004 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe capped eligible queue lifecycle count | ACTOR-001 | ACTOR-002 and AgentDB | Existing poll success | Opt-in, eligible cadence, usable existing registry | Cached capped value | Disabled, failure or stale produces unavailable observation | Dispatch authority remains | REQ-001–005 | METRIC-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | Queue is a database lifecycle population, without grant or capacity filters. No tenant-level dimensions. |
| DEC-002 | Limit protects result size. The 100 ms PostgreSQL statement timeout protects statement execution, including wait. Pool/transport bounds are existing host concerns and not a claimed end-to-end deadline. |
| DEC-003 | Monotonic 30-second attempt floor and 60-second freshness expiry. A failed current discovery or sample clears the prior observation. |
| DEC-004 | Gauges are per publisher on the shared source. Observations across replicas are not additive. |
| ASM-001 | Existing actual AS profile may have no registry or slow dispatch interval; no DB sample is needed without a unique registry, and stale remains unavailable until next success. |

## 6. Project Technology Context

Current traditional AR/AS ownership, Java 25, Spring JDBC/Tx, PostgreSQL and existing AS Micrometer. No POM/dependency change. Reuse existing storage privileges, primary transaction shape and poll scheduling.

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
| Rule 1 | Yes | Existing AR/AS packages | 具名技术组件与现有服务/快照 | §8/§10 | TEST-001 | PASS |
| Rule 2 | Yes | Existing technical dispatch owner and primary read | 保留输入校验，内部校验 scope、时刻、事务 | §7/§9 | TEST-001–003 | PASS |
| Rule 3 | Yes | Private immutable numeric Sample | 局部不可变 scalar Sample，无新公开 DTO | §8/§10 | TEST-001 | PASS |
| Rule 4 | Yes | Existing AS storage factory and current local technical diagnostics | 保留 factory，技术 collaborator 非业务 Bean，固定诊断 | §7/§15 | TEST-003/004 | PASS |
| Rule 5 | Yes | Existing Java and Micrometer/Tx | 无新增工具或依赖 | §6 | TEST-002 | PASS |
| Rule 6 | No | EVD-003 | 原公开 JSON 字段保留 | §9/§10 | TEST-001 | N/A |
| Rule 7 | Yes | AS application.yml | 唯一新增默认 false | §8/§16 | TEST-004 | PASS |
| Rule 9 | No | One existing transaction | 简单观测适配 | §13 | TEST-001 | N/A |
| Rule 10 | Yes | Existing Agent DB Instant | 以 Duration 和绝对时刻比较 | §6.3 | TEST-001/002 | PASS |
| Rule 11 | Yes | Current Agent storage and dispatch ownership | 原分层、CQE 和同源事务 | §7/§11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | agent_run.deadline_at versus PostgreSQL statement_timestamp() | Occurred instant | PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute deadline | Not applicable: aggregate only | deadline equality excluded | Single existing database statement clock | No DB field or API change | TEST-001 |
| TIME-002 | Last sample attempt and sample age | Duration | java.time.Duration and monotonic nano time | Not persisted | Internal | Not applicable: elapsed | Not applicable: meter is a count | 30-second floor, stale at 60 seconds; negative age unavailable | System.nanoTime, fixed cadence constants | No persistent time | TEST-003 |
| TIME-003 | Statement and Spring timeouts | Duration | PostgreSQL and Spring timeout settings | Not persisted | Internal | Not applicable: timeouts | Not applicable: no output | 100 ms statement and one-second Spring budget | Existing JDBC and PostgreSQL implementation | Applies only to optional query | TEST-001/002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing primary-bound aggregate | Add | REQ-001/002 | Count eligible poll batch | Discovery is capped and grant filtered | One bounded opt-in SQL sample | Add |
| Poller-local observer and cached gauge | Add | REQ-003/004 | Run SQL per scrape | User cadence or scraper could increase load | One scalar and monotonic freshness state | Add |
| Existing dispatch configuration and false property | Expand | REQ-004 | New scheduler | Existing successful cycle is sufficient | No scheduler or grant | Add |
| Large active queue and exporter thresholds | Keep | REQ-005 | Unlimited COUNT or new index | Runtime scope and load not verified | Separate assessment | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 new | Existing dispatch | Paged candidates | Current poll | No queue depth |
| Selected design | 0 new external, one local SQL sample per cadence | Existing dispatch | Cached bounded count | Freshness and failure suppress | Current queue pressure observation |

### 7.1 System Architecture Design

Existing grant dispatch poll → optional capped AgentDB primary read → one cached local gauge. Current owner, permissions, scheduler and claim behavior remain.

### 7.2 High-Level Design

Complete the current discovery/dispatch work before an eligible sample. If registry is unavailable there is no query. The getter reads the cached scalar with monotonic age check only.

### 7.3 Detailed Design

1. AR AgentLedgerService.sampleQueuedBacklog refuses ambient transaction, synchronization, bound data source or interrupted threads. Bind primary first with Spring readOnly false and READ_COMMITTED on the existing manager. Confirm actual transaction/current resource. Set physical transaction READ ONLY, local statement_timeout 100ms and Spring timeout 1 second.
2. Read one scalar using a primary and transaction-read-only check. COUNT the fixed LIMIT 257 of existing QUEUED with no cancel_requested_at and deadline_at after statement_timestamp(). Null, negative, greater than 257 or JDBC/transaction failures use a fixed unavailable error. No SELECT of identifiers, model, owner or raw rows.
3. AS AgentQueuedBacklogObservation keeps one immutable cached scalar observation, no database in getter. After successful existing discovery and current admission attempt, run no more than once per 30 seconds using monotonic time. Existing poller guards retain single active sample. The gauge reads NaN until success, at age 60 seconds, on current discovery/sample failure or close. Existing stop still owns cancellation/quiescence.
4. Enable only with stellar.tianji.agent.run-observation.queued-backlog-metrics-enabled=true and existing dispatch prerequisites. Unique current MeterRegistry is required to sample. No extra scheduling, startup query, scrape I/O or policy change. Local fixed warning is emitted once per consecutive sample-failure streak.

## 8. Package Structure and Code File Tree

| Action | Exact path | Responsibility |
| --- | --- | --- |
| Modify | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java | Technical primary scope and capped result validation |
| Modify | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java | One optional bounded aggregate |
| Create | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentQueuedBacklogReadTest.java | Primary, deadline, bound and failure proof |
| Create | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/AgentQueuedBacklogObservation.java | Cached freshness-aware local gauge |
| Modify | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPoller.java | Existing successful poll seam |
| Modify | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchConfiguration.java | Existing dispatch factory and optional registry |
| Modify | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | Default false |
| Modify | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPollerTest.java | Existing poll, cadence, stale and disablement regression |

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| METRIC-001 | Existing local MeterRegistry | tianji.agent.run.queue.pending.capped | Existing AS dispatch/AR technical owner | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 METRIC-001 — Agent queued backlog observation

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Local capped gauge |
| Independent consumer goal | Observe actual current queued lifecycle count |
| Parameter ownership and derivation | Existing AgentDB scope only, constant bound and time |
| Direct/no-new-interface alternative | Existing granted discovery samples a separate subset |
| Caller use of result | Existing allowed observation channel, capped interpretation |
| Round trips and failure points | One optional bounded DB read per cadence; no scrape or external calls |
| Verdict | One fixed untagged cached gauge |

##### Identity and purpose

Name tianji.agent.run.queue.pending.capped. Unexpired and uncancelled QUEUED work on the actual primary.

##### Request parameters

No HTTP API. One default false boolean on existing AS dispatch wiring. Fixed caps/cadence.

##### Success response

Local gauge 0–256 exact, 257 means at least 257, no application tags. Observations are per publisher and cannot be summed for one shared database.

##### Error responses

NaN locally for unavailable, failed, stale or stopped samples. Preserve original dispatch outcome. No private error or rows exposed.

##### Interface logic for frontend and consumers

Existing authorized collector may consume this local gauge if configured. Its missing/NaN representation and live access need separate verification. There is no UI change.

##### Compatibility and verification

Current constructors/poll readiness and other metrics stay compatible. Static/finite and runtime evidence separately recorded.

## 10. POJO and Data Model Design

Internal immutable Sample of capped int and monotonic time. No persisted POJO or external DTO. No owner metadata retained.

## 11. Database Design

No DDL or role/grant change. The query uses existing AgentDB selection authority and immutable V1 dispatch index where the runtime planner selects it. It does not claim bounded physical scanned rows, only result cap and server statement timeout.

## 12. Frontend Page Design

No frontend or API change.

## 13. Design Patterns and Architecture Principles

Direct optional observation after the current poll, with a tiny snapshot holder. Separate database sampling from scrape, without a new scheduler or store.

## 14. Test Design

| Test ID | Exact finite or packaging proof | Expected observation |
| --- | --- | --- |
| TEST-001 | AgentQueuedBacklogReadTest#reads_one_bounded_primary_scalar_after_setting_physical_read_only | Physical READ ONLY and timeout before scalar; exact bounded predicate, no identifiers |
| TEST-002 | AgentQueuedBacklogReadTest#real_empty_is_zero_and_failed_or_untrusted_reads_are_unavailable, #refuses_ambient_and_interrupted_calls_before_jdbc | Real zero distinct, wrong/failed source safe, ambient no read |
| TEST-003 | TianjiGrantDispatchPollerTest#backlog_samples_after_discovery_and_dispatch_and_never_on_scrape, #backlog_probe_failure_is_unknown_and_does_not_block_the_next_dispatch | Rate/freshness, after admission, no scrape IO, failure/stop invalidate |
| TEST-004 | TianjiGrantDispatchPollerTest#default_off_and_absent_registry_do_not_read_backlog and AgentModelUsageDiagnosticsTest | Exact false, no startup/new query absent registry, existing host defaults |
| TEST-005 | Clean permission-producer package/static AS artifact and git path check | Exact source/packaged AS YAML and false, existing SQL/POM/PaC unchanged |

## 15. Non-functional and Cross-cutting Design

One local aggregate at most once per 30 seconds per poller and after work admission. Server statement budget and capped count are measurable finite properties. Runtime index use, connection acquisition and observable end-to-end cadence need separate evidence. Local Gauge output contains only bounded numeric state.

## 16. Compatibility, Migration, Rollout, and Rollback

Default off and no source or authority changes to current grant discovery. No migrations. Setting false or reverting the isolated source commit stops new observations. Existing Registry deployment and access unchanged.

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| Use grant-ready batch count | Rejected | Capped eligibility-specific subset |
| Run COUNT per scrape | Rejected | Introduces unbounded caller-driven DB work |
| Unlimited DB count / add index | Deferred | Cap is sufficient for a pressure signal |
| Bounded post-dispatch primary sample | Selected | Actual current pending lifecycle with explicit bound |

## 18. Risks and Open Questions

| ID | Risk | Disposition |
| --- | --- | --- |
| OPEN-001 | Runtime PostgreSQL plan and connection wait | Separate pre-production evidence |
| OPEN-002 | Actual exporter representation of NaN and metrics access | Existing configuration and explicit owner validation |
| OPEN-003 | Production thresholds and full S12 | Separate evidence and approval |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/METRIC-001 | §6.3/§7/§9 | TEST-001 |
| REQ-002 | UC-001/METRIC-001 | §7/§11 | TEST-001/002 |
| REQ-003 | UC-001/METRIC-001 | §7/§15 | TEST-003 |
| REQ-004 | UC-001/METRIC-001 | §8/§16 | TEST-004/005 |
| REQ-005 | UC-001/METRIC-001 | §14/§18 | TEST-005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Current AR storage and AS poller | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | Existing primary and dispatch, Tx and Registry | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing packaged Micrometer/Tx | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Narrow semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | Existing ownership and exact time checks | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable Sample | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO/PO mapping change | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One safe fixed optional diagnostic, no IDs | None |
| MC-BEAN-001 | Applicable | PASS | §7/8 | Existing AS storage factory | None |
| MC-UTIL-001 | Applicable | PASS | §6 | JDK and current Micrometer/Tx | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing wire bytes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Exact database deadline and monotonic freshness | None |
| MC-CONFIG-001 | Applicable | PASS | §8/9/16 | One false flag and existing gates | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct adapter, simple | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | One bounded technical SELECT, no DDL/export or authority | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact future finite gates and runtime boundary | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source slice independently reviewable | Pending live backlog/SLO/S12 decisions remain outside source slice |

### 20.6 Final verdict

PASS — Ready for user review

实际 PostgreSQL、指标采集与 S12 独立。
