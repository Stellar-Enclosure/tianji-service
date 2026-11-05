# T4-02 Agent 首次认领已提交时延

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One existing claim CAS, one current database event clock, one optional Timer |
| Created | 2026-11-05 23:44 Asia/Shanghai |
| Updated | 2026-11-06 00:00 Asia/Shanghai |
| Owner | Tianji Agent Runtime and standalone Agent Server |
| Repository | tianji-service |
| Scope | First committed QUEUED-to-RUNNING claims with existing DB timestamps |
| Change Surface | AR claim observation seam and existing AS storage factory/configuration |
| Affected Chapters | §7, §8, §9, §14, §15, §16 |
| Source Requirement | [主计划 T4-02 与 S12](../../../../docs/plans/agent-framework-upgrade-plan.md) 的运行 SLA 证据 |
| Baseline Revision | 410874c58d88b766e27706d80e639ebf2d734f4d |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | None |

## 1. Summary

Agent 首次认领成功后，可选择记录入队创建到认领事件记录的已提交时延。起点与终点分别复用 AgentDB 已有的 run.created_at 和 RUN_STATUS_CHANGED 事件时刻。只对新认领 CAS 胜出且事务实际提交的首次尝试采样。默认关闭，按一个无应用标签的本地 Timer 输出。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | AR existing insert | V1__create_agent_ledger.sql, JdbcAgentLedgerRepository#insertRun, AgentLedgerService#start | 初次持久化 QUEUED，attempt 默认 0，created_at 使用当前 AgentDB 时钟，同一事务写 RUN_QUEUED。 |
| EVD-002 | AR exact claim | AgentLedgerService#claim, JdbcAgentLedgerRepository#claim | 现有全 owner/revision/QUEUED/deadline/cancel CAS 赢者才变 RUNNING、递增 attempt 和 lease epoch。 |
| EVD-003 | AR existing clock | AgentLedgerService#appendStatusEvent/#now, JdbcAgentLedgerRepository#databaseNow/#insertEvent | 新 RUN_STATUS_CHANGED 使用现有同源 clock_timestamp()，已有微秒归一和持久化。lease expiry 受 deadline 裁剪，不可倒推出确切认领时刻。 |
| EVD-004 | AS existing wiring | TianjiAgentStorageConfiguration#tianjiAgentServerLedgerService, AS application.yml and Actuator POM | 当前工厂保留终态 publisher 和可选 V15。MeterRegistry 已在 AS 的 AgentResultSpanObservation 使用。 |
| EVD-005 | Existing metrics scope | AgentResultSpanObservation, AgentResultInboxLagObservation, HumanTaskCompletionObservation, Component MicrometerOutboxMetrics | 已有 Link 诊断、Process Inbox 决策、人工任务完成和 Outbox backlog/transport 时间。尚未有 Agent first-claim 样本。 |
| EVD-006 | Existing poll selection | JdbcAgentLedgerRepository#queuedRuns/#grantReadyRuns, TianjiGrantDispatchPoller | 发现批次按固定 limit、资格和 cursor 筛选，不能推算全队列规模或最老等待。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR/AS module-local observation. No new archetype, module or example.

## 3. Goals and Non-goals

用已持久化事实获得 Agent 初次认领时延样本。待认领运行总数、全局最老等待、申请容量失败、入队后取消未认领、重入队年龄、实时告警阈值、实际部署采集和完整 S12 各有独立边界。此项源码可在模型用量关闭时工作。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR first claim and transaction observation | Affected | EVD-001–003 | Reuse event timestamp and first winning claim with post-commit callback | Detailed scope/time and callback/failure semantics | §7, §8, §9, §14, §15 |
| AS factory and MeterRegistry wiring | Affected | EVD-004/005 | One default-off option and no-tag Timer | Existing branches and default/rollout | §7, §8, §9, §14, §16 |
| SQL, event, provider, API, deployment | Context-only | EVD-001–006 | Existing lifecycle/authority, no additional data/behavior | Independent evidence | §10, §11, §16, §18 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Use exact existing timestamps for initial claim | Must | Existing createdAt and existing status-event occurredAt, owner CAS winner, attempts 0→1, unchanged createdAt, no extra DB query | EVD-001–003 |
| REQ-002 | Observe only after actual commit | Must | Same bound source, real transaction and active synchronization. STATUS_COMMITTED records once. Lost CAS, event failure, rollback/unknown commit, later attempt omit | EVD-002–004 |
| REQ-003 | Bound and isolate telemetry | Must | One Timer, zero application tags, only Duration callback, valid zero, negative/overflow omitted, Registry/observer failure isolated with fixed bounded warning | EVD-003–005 |
| REQ-004 | Preserve default and existing paths | Must | Flag absent/false, original factory signatures, exact publisher and V15 verification preserved, no new SQL/POM/API/permissions | EVD-004 |
| REQ-005 | Separate sample evidence from active backlog and live SLA | Must | Exact finite tests and evidence scope, runtime and threshold independent | EVD-005/006, S12 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | AS operator | Enable local observation only within existing host | Existing AS storage configuration | Existing deployment authority | EVD-004 |
| ACTOR-002 | AR claim path | Complete original exact lease claim and optional measurement | AgentLedgerService#claim | Existing verified owner and fenced update | EVD-002 |
| ACTOR-003 | Existing metrics consumer | Read committed first claim sample | Existing authorized Registry | No new exporter or authorization | EVD-004/005 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe first committed claim delay | ACTOR-003 | ACTOR-001/002 | First CAS success and actual commit | Existing storage and flag, valid times and Registry | One elapsed Timer sample | Rollback, later claim, invalid time, no Registry or failure yield no valid sample | Existing claim unchanged | REQ-001–005 | METRIC-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | The existing status event time measures claim recording. Commit is eligibility, not the interval endpoint. |
| DEC-002 | Only attempt 0→1 in the current claim path. Existing original createdAt is not a requeue timestamp. |
| DEC-003 | Flag stellar.tianji.agent.run-observation.initial-claim-metrics-enabled defaults false. The flag alone does not create storage. A custom externally supplied ledger remains responsible for its own observer construction. |
| DEC-004 | Existing publisher and model usage opt-in continue unchanged; no dependency on V15, cost or provider approval for this metric. |
| ASM-001 | Existing SQL clock can go backwards on host adjustment; negative durations are omitted. No timekeeper accuracy claim from finite tests. |

## 6. Project Technology Context

Existing traditional AR/AS layered owner boundary. Java 25, PostgreSQL, Spring JDBC/Tx, AS Micrometer/Actuator and Mockito/JUnit. AR receives a host-provided Consumer of Duration, so no Micrometer API dependency is added to AR. No new dependency, query or service.

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
| Rule 2 | Yes | Existing claim owner and CAS | 保留输入校验，内部校验 scope、时刻、事务 | §7/§9 | TEST-001–003 | PASS |
| Rule 3 | Yes | Existing immutable run and event | 复用原事件 Instant 与不可变 Duration，无新 POJO | §8/§10 | TEST-001 | PASS |
| Rule 4 | Yes | Existing AS storage factory and current local technical diagnostics | 保留 factory，技术 collaborator 非业务 Bean，固定诊断 | §7/§15 | TEST-003/004 | PASS |
| Rule 5 | Yes | Existing Java and Micrometer/Tx | 无新增工具或依赖 | §6 | TEST-002 | PASS |
| Rule 6 | No | EVD-003 | 原公开 JSON 字段保留 | §9/§10 | TEST-001 | N/A |
| Rule 7 | Yes | AS application.yml | 唯一新增默认 false | §8/§16 | TEST-004 | PASS |
| Rule 9 | No | One existing transaction | 简单观测适配 | §13 | TEST-001 | N/A |
| Rule 10 | Yes | Existing Agent DB Instant | 以 Duration 和绝对时刻比较 | §6.3 | TEST-001/002 | PASS |
| Rule 11 | Yes | Current Agent claim ownership | 原分层、CQE 和同源事务 | §7/§11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.createdAt, existing status AgentEventDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: database timestamps | Not applicable: no display change | Existing AgentDB source and same committed claim | Existing JDBC PostgreSQL timestamps and microsecond Agent clock | No changed event fields, original timestamp reused | TEST-001/002 |
| TIME-002 | Initial claim interval | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: Registry unit | Zero valid, negative/overflow omitted | Preserve original micros, representable in nanos | New Timer only | TEST-001/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing first-claim event seam | Expand | REQ-001/002 | Poll discovery counts | Cannot infer committed winning claim or exact first delay | One optional local commit callback | Add |
| AR callback and AS Timer factory | Expand | REQ-002–004 | Inline AS clock at dispatch request | Wrong clock/attempt, may see retry or failure | Duration-only injection, existing Registry | Add |
| Actual active backlog | Keep | REQ-005 | Use bounded poll results as backlog | Poll filtering and page caps hide live state | Independent design would need additional authority | Keep |
| Current public APIs and storage | Keep | REQ-004 | New DB field/scan | Existing authoritative timestamps suffice | No migration or SQL | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 new | Existing claim | Existing event and lease | Original write | Known run state |
| Selected design | 0 new | Existing claim | One optional Timer | Only after commit, local failure isolated | First committed claim latency distribution |

### 7.1 System Architecture Design

Existing AgentLedgerService#claim → unchanged owner CAS and status event → same bound transaction completion → Duration-only host observer → existing AS MeterRegistry.

### 7.2 High-Level Design

Return the exact existing written event's occurredAt from the private append helper, preserving current event writes and terminal callbacks. In a successful first claim, use existing createdAt and that event time. Existing host constructor/factory calls stay compatible and default uninstrumented.

### 7.3 Detailed Design

1. Only after normal claim write and status event insert, check QUEUED to RUNNING, original attempt zero, resulting attempt one and unchanged creation timestamp. Use current owner/correlation/SQL checks without added access or DB calls. A later attempt or invalid clock interval yields no sample.
2. Require actual transaction, active synchronization and the exact ledger DataSource bound on current thread. Register afterCompletion, record only STATUS_COMMITTED, including a joined same-source outer commit. Payload is one elapsed Duration. If callback or registration throws RuntimeException, original transaction outcome remains. Emit at most one fixed warning per ledger on observer failure.
3. AS factory checks the new boolean only when current storage bean is eligible. Optional recorder resolves existing MeterRegistry with getIfUnique. One Micrometer Timer, no application tags. Model usage/publisher variants retain current validation, route and publisher authority. Existing externally supplied custom ledger remains unaffected.
4. No real-time backlog, dead-end run estimate, history backfill, new report/store or automated operator action.

## 8. Package Structure and Code File Tree

| Action | Exact path | Responsibility |
| --- | --- | --- |
| Modify | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java | First-claim observer and current event timestamp reuse |
| Create | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentInitialClaimTimingTest.java | Exact owned claim, one clock, commit and exception proofs |
| Modify | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java | Existing optional factory and Timer |
| Modify | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java | Default off and coexistence checks |
| Modify | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | One default-false flag |

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| METRIC-001 | Existing local MeterRegistry | tianji.agent.run.initial.claim.delay | AS via AR claim | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 METRIC-001 — Agent initial claim Timer

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New local observation |
| Independent consumer goal | Measure initial run creation to first committed claim record |
| Parameter ownership and derivation | AR owns the exact same-source event time and run created time |
| Direct/no-new-interface alternative | Outbox and poll metrics measure other work |
| Caller use of result | Existing authorized operational telemetry |
| Round trips and failure points | Zero new SQL/network, one optional commit callback |
| Verdict | One fixed untagged Timer |

##### Identity and purpose

Micrometer Timer tianji.agent.run.initial.claim.delay with description Agent run creation to first recorded claim for committed claims.

##### Request parameters

No new request. The existing AS storage owner may set stellar.tianji.agent.run-observation.initial-claim-metrics-enabled=true. Existing storage eligibility continues.

##### Success response

Timer count and elapsed duration, with no application tags. No run, tenant, actor, owner, definition, attempt, event, model, correlation or payload data. Existing common tags are governed by the deployment, outside this change.

##### Error responses

Retain existing claim behavior. Lost CAS, rollback, invalid/overflow clock interval, absent Registry and failed observation do not produce a valid sample. No synthetic zero for absent evidence.

##### Interface logic for frontend and consumers

Existing observation backend can read winning first-claim samples. Any configured SLO, active backlog threshold or runtime availability must have distinct proof.

##### Compatibility and verification

No wire, event or API change. TEST-001–005 verify source. Actual deployment and S12 remain independent.

## 10. POJO and Data Model Design

No new persisted type. Existing AgentRunDTO and AgentEventDTO supply source times. Duration-only callback has no owner, identifier or raw event data.

## 11. Database Design

Unchanged SQL, migrations, roles and indexes. No new read, writer or clock invocation. Existing AgentDB source is reused.

## 12. Frontend Page Design

No frontend change.

## 13. Design Patterns and Architecture Principles

Direct Spring afterCompletion observer keeps optional AS metrics outside the AR owner decision. The Consumer seam preserves AR dependency direction and current storage composition.

## 14. Test Design

| Test ID | Exact finite proof | Expected observation |
| --- | --- | --- |
| TEST-001 | AgentInitialClaimTimingTest#observes_first_claim_using_existing_clock_after_outer_commit | Exact owned SQL, existing event clock once, no value before outer commit, exact elapsed after |
| TEST-002 | AgentInitialClaimTimingTest#does_not_sample_lost_cas_failed_event_rollback_or_unknown_commit | All omit samples and preserve original errors/rollback |
| TEST-003 | AgentInitialClaimTimingTest#accepts_zero_but_omits_later_attempts_and_invalid_time, #observer_failure_cannot_replace_the_committed_claim | Valid zero, invalid excluded, later attempt and Registry fault semantics |
| TEST-004 | AgentModelUsageDiagnosticsTest#initialClaimTimingIsDefaultOffAndUsesOneFixedMeter and existing #verifiedOptInOnly | Exact false, no new storage activation, one untagged timer, publisher/V15 coexistence |
| TEST-005 | Existing AgentRecoveryDispositionEvidenceTest and AgentModelUsageStoreTest | Adjacent source behavior and compile proof without runtime |

## 15. Non-functional and Cross-cutting Design

Fixed zero application tags. No extra data access, network, poll, instrumentation dependency or required new storage. Each qualifying commit attempts one in-process observation, whose actual cost and export coverage require live evidence. Queue selection bias is explicit.

## 16. Compatibility, Migration, Rollout, and Rollback

Existing callers and default deployment behavior remain. Setting the metric flag false stops future sampling. No backfill, action, storage or permissions.

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| Claim expiry minus requested lease duration | Rejected | Existing expiry is capped at deadline and may be renewed |
| Count only discovery rows | Rejected | Limit, cursor and eligibility cannot establish active queue size or confirmed claim |
| New claimed_at field/query | Rejected | Existing claim event timestamp suffices |
| Existing event time after commit | Selected | Same source, no new I/O, least coupling |

## 18. Risks and Open Questions

| ID | Risk | Disposition |
| --- | --- | --- |
| OPEN-001 | Clock adjustments and sampled first winners | Negative interval suppressed; actual clocks and coverage outside finite proof |
| OPEN-002 | Live exporter/S12 and thresholds | Require independent authorized runtime evidence |
| OPEN-003 | Current active backlog | Independent measurement/ownership design, no pending-state inference |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/METRIC-001 | §6.3/§7 | TEST-001 |
| REQ-002 | UC-001/METRIC-001 | §7/§9 | TEST-001/002 |
| REQ-003 | UC-001/METRIC-001 | §7/§15 | TEST-003/004 |
| REQ-004 | UC-001/METRIC-001 | §8/§11/§16 | TEST-004/005 |
| REQ-005 | UC-001/METRIC-001 | §14/§18 | TEST-005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Current Agent owner and persisted flow | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | Existing Agent event clock, owner claim, Tx and Registry | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing packaged Micrometer/Tx | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Narrow semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | Existing ownership and exact time checks | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Existing immutable Agent run/event | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO/PO mapping change | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One safe fixed optional diagnostic, no IDs | None |
| MC-BEAN-001 | Applicable | PASS | §7/8 | Existing AS storage factory | None |
| MC-UTIL-001 | Applicable | PASS | §6 | JDK and current Micrometer/Tx | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing wire bytes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Existing exact AgentDB instants and Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §8/9/16 | One false flag and existing gates | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct adapter, simple | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | No new queries/DDL/export or runtime authority | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact future finite gates and runtime boundary | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source slice independently reviewable | Pending live backlog/SLO/S12 decisions remain outside source slice |

### 20.6 Final verdict

PASS — Ready for user review

此为 source-only 候选。实际 AgentDB、运行指标导出和 S12 独立验收。
