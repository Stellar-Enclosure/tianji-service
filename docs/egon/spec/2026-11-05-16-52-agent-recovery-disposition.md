# T4-03A Owner-only Recovery Disposition

| Field | Value |
| --- | --- |
| Document | `2026-11-05-16-52-agent-recovery-disposition.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Feature` |
| Complexity | `Simple` |
| Complexity Drivers | `Read-only classification of one owner-scoped observation, no execution or state changes` |
| Created | `2026-11-05 16:52 Asia/Shanghai` |
| Updated | `2026-11-05 17:44 Asia/Shanghai` |
| Owner | `Tianji Agent and Process owner authorities` |
| Repository | `tianji-service` |
| Scope | `Agent run and Tool journal metadata, existing Worker owner query, finite source proof` |
| Change Surface | `Owner-scoped read disposition and classification tests, no checkpoint, writes or new external endpoint` |
| Affected Chapters | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Source Requirement | `Master §19 T4-03 first disposition/reconciliation slice` |
| Baseline Revision | `tianji-service 6b806e43888ea5e133780f9b755eb610326f52b8 and tianji-web f558e02c8d07da2da758c5273a5eb531ca251018, clean` |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [Worker recovery](2026-11-05-13-41-process-worker-recovery.md#9-interface-definitions) |
| Related Specs | [T3-12 source and E boundary](2026-11-05-15-13-expense-four-path-acceptance.md#16-compatibility-migration-rollout-and-rollback) |
| Related Plans | [T4-03A implementation](../plan/2026-11-05-17-42-agent-recovery-disposition.md), [Master §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

本切片用一个既有 owner 的当前观察区分排队、运行、停滞、终态与证据不足，并分别表示 Agent 结果、Tool 调用及 Worker 报告的 UNKNOWN。首个可独立交付是同源 owner-only AR 只读诊断与有限故障窗口证据，供已确定的后续 T4-04 运维消费。它不改变任何 runtime 状态或允许的动作。

现有 Agent/Worker 入口继续分别鉴权和读取各自 owner。新增同一 AR owner 的最小内部 Agent/Tool 元数据投影；T4-04 是已确认的下一消费方，但本切片不接入外部接口。没有新 HTTP、checkpoint、resume、重放、补偿、budget settle 或 synthetic state。T4-03 的有界原生恢复仍单独审批。

## 2. Background and Current State

### 2.1 Business context

同一事故可能包含 run 停滞、Tool 已发出但本地 receipt 未写，以及一个无关 Worker 的历史失败。需要明确哪项已持久化、哪项无法证明，才能避免拿终态、空列表或查询失败推断副作用没有发生。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact source | Observed fact and limit |
| --- | --- | --- | --- |
| EVD-001 | Static repository | AS `AgentRunController#get,result`, `TianjiAgentAccess#require`; C `AgentClient#get,getResult`, `AgentRunDTO` | GET exact owner returns status/revision/attempt/deadline but no lease. Result GET returns output and sideEffectOutcome, so a new read model should not materialize it |
| EVD-002 | Static repository | AR `AgentLedgerService#reconcileDue,suspendExpiredLease,expireDeadline,suspendForAuthorization`, `JdbcAgentLedgerRepository#dueRuns`, `AgentRunDispatcher` | Worker loss suspends and may mark budget UNKNOWN, deadline uses DB time, dispatcher only discovers QUEUED. Existing mutation owner is separate |
| EVD-003 | Static repository | AR `AgentLedgerService#requireRun`, `JdbcAgentLedgerRepository#run,ownedRunExists` | Full tenant/type/issuer/subject/run binding. Existing `run` loads `SELECT *` including private snapshot/result and lease owner, unsuitable for a new minimized report |
| EVD-004 | Static repository | AR `ToolInvocationRecorder#metadataOnly,metadata`, `ToolInvocationSql`, V13 `agent_tool_invocation`, V14 result access | DISPATCHING or UNKNOWN does not dispatch again. Closed states, owner tuple, PK tenant/run/invocation and local receipt fields exist. No generic provider reconciliation or proof of no effect |
| EVD-005 | Static repository | PS `ProcessWorkerRecoveryService#workerFailures`, C `ProcessWorkerFailuresDTO` / `ProcessWorkerFailureDTO` | Current T3-10 exact START owner and bounded Worker observation. `reportedFailure=UNKNOWN` is distinct from native status. Read response itself is not restore authorization |
| EVD-006 | Static repository | AR `PostgresAdkSessionService#appendEvent`, `AdkSessionEventCodec`, `AdkAgentRunExecutor`; AS Outbox and PS Inbox | Committed event/typed state and terminal delivery exist. The current normal invocation entry and pinned SDK signatures do not prove resumable checkpoint, invocation recovery or exactly-once external effect |
| EVD-007 | Existing evidence boundary | T3-12 finite reports and opt-in `WorkerRecoveryIT`; inventory `t4-03-05-inventory.md` | Source and finite fixtures can be reused, real independent process replacement/PG/IAM E remains separately operated |

### 2.3 Current gap

Existing Agent GET can confirm persisted status but not current lease, Tool journal or a private result's scalar outcome without full result materialization. T3-10 Worker GET already has its own closed recovery observation. Neither endpoint authorizes a join from Agent owner to Process owner or a search by correlation ID. No existing checkpoint or owner-wide recovery scan was found.

### 2.4 Evidence and current-chain map

| Entry/trigger | Current chain | Data and consumers |
| --- | --- | --- |
| Agent owner read | AS `TianjiAgentAccess#require(GET_API)` → `AgentLedgerService#get` → exact owner run | Own AgentRunDTO and no-store |
| AR recovery scheduler | `AgentRunReconciler#poll` → `reconcileDue` → current due candidates/revision CAS | Existing expire/suspend/events/budget transitions; read disposition never calls it |
| Tool call proof | `ToolInvocationRecorder` → V13 intent/invocation and exact finish fence | Durable local state, receipt and accounting; current read does not run recorder |
| Process owner read | `ProcessWorkerRecoveryController#failures` → exact START owner → `ProcessWorkerRecoveryService#workerFailures` | Existing bounded rows and closed reason; no new Worker query |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing Tianji technical modules only. The read classification and finite evidence create no project, archetype, DDL or example domain.

## 3. Goals and Non-goals

### 3.1 Goals

- Provide one finite, repeatable classification matrix with exact known and missing evidence boundaries.
- Use exact owner and exact entity ID for each existing source. A Process START owner and an Agent run owner are independently verified.
- Distinguish recorded lifecycle from business-effect uncertainty and explain positive local receipts without exposing content.

### 3.2 Non-goals

No cross-tenant queue, no cross-owner aggregate, no runtime endpoint yet, no new permissions, no mutable owner state, no external reconciliation, no retry or resume, no checkpoint or Session replay, no data migration, no provider calls, no role grants. T4-02 cost/observability UNKNOWN remains independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapters |
| --- | --- | --- | --- | --- | --- |
| Finite disposition evidence | Affected | EVD-001–007 | Exact classification and fault tests only | Complete matrix, read inputs and expected effects | §7, §8, §9, §10, §13, §14, §15, §16, §17, §18 |
| Owner-local Agent/Tool projection | Affected | EVD-001/003/004 | Internal existing ledger query for future T4-04, no external route | Exact scope, bounded projection and refusal | §7, §8, §9, §10, §14, §16, §18 |
| Existing Process Worker owner query | Unchanged | EVD-005 | Exact T3-10 contract retained | Use existing read, no new restore call | §9, §14, §16 |
| Existing execution/checkpoint/data | Unchanged | EVD-002/006 | Current dispatcher/reconcile/session and SQL stay | Static boundary and finite regression | §11, §16 |
| Web/IAM/transport | Unchanged | EVD-001/005 | Current owner rights retained | No resource mapping/UI | §12, §15 |

## 4. Requirements and Acceptance Criteria

| Requirement | Acceptance |
| --- | --- |
| REQ-001 | The internal owner query and finite fixture choose the right lifecycle from exact observed run state, deadline and lease evidence. Missing/contradictory facts are distinct |
| REQ-002 | Agent terminal side effect and Tool journal evidence remain orthogonal, UNKNOWN never counts as no effect or permission to replay |
| REQ-003 | Exact tenant/type/issuer/subject/run binding and existing T3-10 owner Worker read. Foreign, legacy/unverified or ambiguous source never becomes a safe result |
| REQ-004 | Prove finite failure windows before/after claim/lease expiry, Tool dispatch/local receipt, committed native event and Worker late/duplicate state. No source test claims live E |
| REQ-005 | Reports contain fixed categories and scoped versions/receipt-presence facts only. Read classification produces no mutation or provider call |
| REQ-006 | No checkpoint/resume/write/HTTP change without a separate accepted consumer/runtime design |

### 4.2 Use-case analysis

| ID | Use case/goal | Actor | Trigger and precondition | Result/failure | Trace |
| --- | --- | --- | --- | --- | --- |
| UC-001 | Understand one Agent run's current disposition | ACTOR-001 authenticated exact run owner | Existing GET plus owner-held verified evidence | Closed run/Agent/Tool axes or explicit unavailable | REQ-001/002/003 |
| UC-002 | Inspect one Process Worker observation | ACTOR-002 exact applied START owner | Existing READ plus WORKER_RECOVER rights, PS readiness | Existing T3-10 outcome projected separately | REQ-002/003/005 |
| UC-003 | Verify incident windows before any recovery design | ACTOR-003 technical test operator | Locked source and finite fixtures only | Reproducible observation/negative mutation checks | REQ-004/005/006 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision |
| --- | --- |
| DEC-001 | Keep Agent lifecycle, Agent effect, Tool journal and Worker outcome as separate axes. No universal safe-to-retry boolean |
| DEC-002 | Exact internal AR owner-only Query and finite evidence; T4-04 is the planned consumer. Existing public owner reads remain context only, no AS/C/PS code or new wire in this Step |
| DEC-003 | Worker remains current T3-10 exact owner query. No SQL/REST changes in PS |
| DEC-004 | Time checks use one owner DB observation and strict equality-at-expiry. Fixed fixture time proves logic only |
| DEC-005 | Root scope locks only the exact AR Query under the user master Plan and current plain JDBC technical profile. T4-04 external HTTP/PaC contract needs separate exact review; no authority bypass or new owner |
| DEC-006 | Existing T2-01, T3-10 recovery and T4-02 owner-local source evidence are locked. Runtime S11, T3-12 E, cross-source diagnosis and checkpoint/real resume remain separate |

## 6. Project Technology Context

JDK 25, existing Spring Boot, PostgreSQL AgentDB and ProcessDB, immutable V13/V14, JUnit and existing C records. No new dependency. Finite source keeps current AR/PS technical test structure. Existing Agent owner query and T3-10 Worker remain the owner paths.

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
| Rule 1 | Yes | AR/PS semantic Test and DTO names | Test and private immutable evidence value | §8/10 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001/003/005 | Exact current owner and shape, no new auth bypass | §9 | TEST-001/003 | PASS |
| Rule 3 | Yes | Existing immutable DTOs | Reuse owner inputs and small immutable test facts | §10 | TEST-001/002 | PASS |
| Rule 4 | No | No new business Bean or runtime log | No logging/injection change | §8 | TEST-005 | N/A |
| Rule 5 | Yes | JDK and current test libraries | Existing JDK/JUnit/Mockito | §14 | TEST-001–005 | PASS |
| Rule 6 | Yes | Existing JSON scalar effect values and enum names | Exact closed classifications, no arbitrary JSON output | §9/10 | TEST-002/003 | PASS |
| Rule 7 | No | No config source | Existing flags stay | §16 | TEST-005 | N/A |
| Rule 9 | No | Deterministic fixture classification | Pure finite decision tables, no workflow pattern needed | §13 | TEST-001–004 | N/A |
| Rule 10 | Yes | Existing owner DB clock and Instant | Use one snapshot time and fixed finite Instant | §6.3 | TEST-001/002 | PASS |
| Rule 11 | Yes | Existing technical test ownership | No new production persistence/route/event | §8/11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt, deadlineAt, leaseExpiresAt, dispatchNotAfter | Occurred instant | Instant | UTC instant | Internal | Not applicable: elapsed run facts | Not applicable: no UI | due when instant <= observation; no clock/zone inference | one DB sample, fixed finite Instant, DB precision unchanged | Existing timestamps remain authoritative | TEST-001/002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy | Added calls/state/coupling | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Finite classification matrix | New | REQ-001/002/004/005 | Existing single concern tests | No one proof distinguishes lifecycle vs uncertain external fact at the precise windows | One test source/evidence, no runtime | Add |
| Agent/Tool read projection | New | REQ-003/005/006 | Existing Agent GET and Tool journal | GET has no lease/tool data, result GET carries private output | One exact owner SQL snapshot; T4-04 future consumer | Add |
| Process Worker read | Keep | REQ-003/004 | Existing T3-10 route | Already verifies owner and bounded result | No new state/call surface | Reuse |
| Checkpoint/replay | Deferred | REQ-006 | Existing Session event persistence | Storage fact is insufficient for resumability and external side-effect safety | Dedicated future fault, authority and compatibility proof | Outside slice |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Existing independent reads | Each existing API separately | Exact owner status | Existing contracts only | Snapshots are independent | Direct source verification |
| First owner-local disposition slice | 0 network | Fixed evidence matrix and internal read | Existing AR source, no route | One snapshot may become stale; no write authority | Reviewable and bounded UNKNOWN handling |
| Future T4-04 public adaptation | Independently reviewed | Existing-authority owner lookup | Outside current source | No privilege inference | No raw result exposure |

### 7.1 System Architecture Design

The owner source remains authoritative for each fact. AS/AR own run and Tool evidence. PS owns START, native and V4 Worker observations. Their snapshots are independently sampled and correlated only by an already authenticated owner and exact entity selected for that source. No synchronous join, tenant fallback, queue listing or cross-system action is added.

### 7.2 High-Level Design

Capture known run lifecycle, local terminal-result scalar, bounded Tool journal metadata and an independent Worker observation. Classify each axis without resolving or writing it. On any source absence, schema drift, overflow or contradictory fact, retain a distinct not-proven state.

### 7.3 Detailed Design

#### Run lifecycle decision

Evaluate owner/snapshot validity first, then terminal, then deadline, then leased or waiting state. Current terminal set is `COMPLETED,FAILED,CANCELLED,TIMED_OUT` from `AgentRunTransitions#terminal`. `ACTIVE` indicates a recorded current lease, not live process health. `STALLED` indicates only the recorded fence condition.

| Observed fact | Lifecycle | Fixed reason |
| --- | --- | --- |
| Exact owner absent, malformed state/revision/clock or source error | No report for missing/foreign; otherwise UNVERIFIED | NOT_FOUND or SOURCE_UNAVAILABLE |
| Terminal status and valid shape | TERMINAL | TERMINAL_RECORDED |
| Valid nonterminal status that current `dueRuns` checks and deadline <= sampled DB time | STALLED | DEADLINE_DUE |
| QUEUED and deadline future | QUEUED | QUEUE_DISCOVERY |
| RUNNING, valid current positive epoch/attempt, owner-present Boolean and lease > sampled time | ACTIVE | CURRENT_LEASE or CANCEL_REQUESTED when recorded |
| RUNNING lease null within current due predicate | STALLED | LEASE_MISSING |
| RUNNING, valid positive fence and nonnull lease <= sampled time | STALLED | LEASE_EXPIRED |
| RUNNING with contradictory nonnull fence metadata | UNVERIFIED | FENCE_UNVERIFIED |
| SUSPENDED with known stored reason | STALLED | LEASE_EXPIRED, AUTHORIZATION_DENIED, AUTHORIZATION_UNAVAILABLE |
| Other SUSPENDED, CREATED or WAITING without a proved due deadline | UNVERIFIED | SUSPENSION_UNVERIFIED or LEGACY_WAIT_UNVERIFIED |

Check a contradictory nonnull fence before accepting an ACTIVE or LEASE_EXPIRED row. Known suspended reason comes from current code literals only. A new reason remains unavailable until classified. Keep terminal and UNKNOWN as simultaneous facts. With normal existing GET alone a recorded RUNNING row with no lease proof maps to UNVERIFIED for lease-based disposition. Do not infer an active lease from the DTO or wall time.

#### Agent, Tool and Worker axes

| Source | Positive evidence | Classification constraints |
| --- | --- | --- |
| Agent stored terminal result scalar | Same owner/run and result runId/status match; exact `sideEffectOutcome=NONE,CONFIRMED,UNKNOWN` | Expose `RECORDED_NONE`, `RECORDED_CONFIRMED` or `UNKNOWN`. Missing/malformed/legacy or nonterminal result yields `UNVERIFIED`. No raw result or provider-side certainty |
| V13 Tool journal | For exact run/owner and capped records, map closed `state,risk,side_effect_outcome` plus receipt-present boolean. `DISPATCHING` is positive outbound intent, `UNKNOWN` indicates unresolved outcome, write-risk DISPATCHING or UNKNOWN with effect UNKNOWN stays unresolved | Report `coverage` COMPLETE/TRUNCATED/UNAVAILABLE and nullable `hasDispatching,hasUnknownResult,hasUnresolvedWrite,hasLocalReceipt`. Local confirmed write receipt requires `SUCCEEDED`, non-READ_ONLY, CONFIRMED, matched scope and nonempty receipt. A READ_ONLY UNKNOWN still means result unknown. With incomplete coverage positive observed flags may be true, otherwise null. Complete false flags mean no matching local record observed, not external no-effect proof |
| Existing T3-10 Worker observation | Exact Process START owner, exact job row and `reportedFailure=UNKNOWN` or reason UNKNOWN_OUTCOME | Preserve UNKNOWN independent of native deadletter, historical/manual/eligible status. UNVERIFIED, HISTORY_INCOMPLETE, source error or empty observation never become proof of no effect. Retain existing public version and action semantics only in current API, not in this evidence |
| Session and bridge | Committed Session event, native wait, or terminal Outbox/Inbox fact | Record as separately scoped positive evidence. They do not decide Tool certainty, change lifecycle, prove a resumable invocation or authorize any action |

If Agent result says NONE while a verified Tool write is unresolved, preserve both facts and mark contradictory evidence rather than collapse to safe. Budget UNKNOWN belongs to accounting and does not manufacture business-effect UNKNOWN or CONFIRMED. In-flight, unknown and receipt-observed facts do not imply external publication/settlement.

## 8. Package Structure and Code File Tree

| Scope | Path and symbols | Change |
| --- | --- | --- |
| Finite first slice | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRecoveryDispositionEvidenceTest.java` | New finite table and owner-binding evidence using current DTO, exact source facts and controlled fixtures, no runtime caller |
| Existing regressions | AR `AgentRunReconcilerTest,AgentRunDispatcherTest,ToolInvocationRecorderTest,AdkSessionEventCodecTest`; PS `WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest` | Existing files unchanged. Run exact selected tests |
| Existing AR owners | AR current `AgentLedgerService.java`, `JdbcAgentLedgerRepository.java` and nested immutable evidence | Internal `inspectRecovery(TrustedAgentActor,GetAgentRunQuery)`. T4-04 future consumer, no new query in PS |

No test writes real state. The finite evidence test exercises the actual new internal query, exact owned SQL shape and closed classification with injected source facts. This is not live SQL or E proof. Any future external API or package-level contract exposure requires an accepted amendment and actual consumer.

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Name | Kind | Owner | Scope | Requirements |
| --- | --- | --- | --- | --- | --- |
| INTERNAL-001 | Scoped recovery disposition | Internal read Query | AR existing ledger | One exact Agent run; PS Worker evidence independently remains with its existing owner query | REQ-001–006 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-001 — Owner-local disposition evidence

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New existing-owner internal AR read, external owner APIs unchanged |
| Independent consumer goal | Operator/test reviewer can distinguish record from effect uncertainty before any recovery capability is designed |
| Parameter ownership and derivation | Exact authenticated owner and run ID or separate START owner come from existing source. No user-supplied tenant/issuer override |
| Direct/no-new-interface alternative | Existing owner APIs remain authoritative; the evidence matrix adds no endpoint |
| Caller use of result | Understand positive/missing evidence and retain separate owner facts, no forwarding of action or command parameters |
| Round trips and failure points | One existing same-source repeatable read, no network or other owner |
| Verdict | Add only the owner-local projection; public wire and T4-04 scope remain separate |

##### Identity and purpose

Owner queries remain existing AS `GET /api/tianji/v1/agent-runs/{runId}` and PS `GET /api/tianji/v1/process-instances/{instanceId}/worker-failures`. Existing current permissions and exact owner checks remain. The new internal Query requires an existing trusted actor and GetAgentRunQuery. Finite tests verify it without a route, listener or IAM configuration.

##### Request parameters

The caller passes only the existing trusted actor and exact run query. The same source derives status, revision, lease and sanitized evidence; the separate Worker owner continues using PS. Fixture owner always includes tenant, type, issuer fingerprint, subject and run. Test exact USER/SERVICE ownership, legacy/foreign/unavailable refusal and closed state/time fields. Do not accept a correlation ID, session selector, raw provider result or receipt as authorization.

##### Success response

The internal immutable read contains lifecycle, fixed reason, optional observed run revision/attempt, terminal Agent effect and bounded Tool flags as §7 defines. Worker evidence remains a separate owner snapshot. No merged timestamp, raw diagnostic, recovery action, replay handle or guaranteed provider outcome. The expected finite fixtures retain source distinction.

##### Error responses

Missing and foreign target have the same not-found handling at existing boundaries. Invalid/contradictory snapshot or unavailable source stays explicitly unverified, with no operator action. Report safe fixed categories and do not expose credentials, original result, lease capability or arbitrary source strings.

##### Interface logic for frontend and consumers

No new frontend operation. Obtain current owner-scoped reads, treat facts not provided by the existing response as unverified, and use the finite matrix to judge outcomes. Do not call getResult merely to display the classification, and do not pass observation into automatic retry, cancel, reconcile or restore.

##### Compatibility and verification

Preserve all existing DTO and OAuth/resource contracts. TEST-001–005 checks exact owner, finite failure windows and existing Worker/Session/Tool regressions. Any T4-04 external wire/permission contract remains independently reviewed.

### 9.3 Exact minimized Agent read projection

Put `inspectRecovery(TrustedAgentActor actor, GetAgentRunQuery query)` on existing AR `AgentLedgerService` and add bounded scalar SQL beside current `JdbcAgentLedgerRepository`. Unlike `requireRun`, do not use `SELECT *`. First select exact run tuple with run PK bound by tenant/run and subject type/issuer/id. Return safe run status/revision/attempt, lease presence/expiry comparison and selected result scalars only. The V1 JSONB result is never materialized: check that stored result.runId and result.status match this row and the closed effect is a string; project only that closed scalar. Use one sampled `clock_timestamp()` and verify existing owned primary. As `AgentLedgerService#terminalReference` warns about replica routing on transaction readOnly, preserve primary selection, then require an explicit transaction-level read-only guard before business SQL in the repeatable-read transaction. Reject ambient transaction, cap local statement/transaction time, no `FOR UPDATE`.

Tool rows use the V13 `(tenant_id,run_id,invocation_id)` prefix and stable invocation order, limit 65 to prove whether the first 64 are complete. Verify exact owner metadata without projecting another owner's state; mask unsafe fields unless the bound type/issuer/subject all match or use an equivalent bounded mismatch probe. An unexpected owner within the already scoped run gives unavailable evidence only. Select lifecycle/risk/closed effect and receipt-present predicate, no raw ID, result JSON, capability, dispatch nonce/hash or arbitrary error. A known missing V13 or missing permissions yields unavailable coverage and cannot mean empty; check availability before query when possible. Unexpected SQL failure aborts the snapshot. No partial or fallback read from a different source. No store write.

## 10. POJO and Data Model Design

| Evidence value | Fields |
| --- | --- |
| Run fixture | Exact trusted owner/run selector, persisted status/revision/attempt, safe reason, lease boolean/known expiry, deadline, DB observation |
| Agent effect | Closed RECORDED_NONE, RECORDED_CONFIRMED, UNKNOWN, UNVERIFIED, scoped result identity validation |
| Tool summary | Coverage COMPLETE/TRUNCATED/UNAVAILABLE and nullable Boolean flags as defined in §7.3 |
| Worker reference | Existing separate DTO; preserve reported failure and closed reason, do not join by arbitrary link |
| Finite report | Fixed lifecycle/reason and individual positive/missing facts, no private raw source |

The nested immutable AR disposition DTO remains an internal authority boundary. Existing production wire structs unchanged.

## 11. Database Design

Unchanged for this slice. No DDL. The new bounded read uses existing AgentDB `agent_run` and V13 `agent_tool_invocation` exact tenant/run prefix with owner fields, no new table or index. Existing V13/V14 and all migrations remain immutable. PS keeps current T3-10 data ownership.

## 12. Frontend Page Design

Unchanged. No dashboard or operator UI is added, and all existing clients and routes retain their current behavior. T4-04 can choose an actual consumer separately.

## 13. Design Patterns and Architecture Principles

One finite classification table with independent observation axes suffices. Avoid workflow/state-machine or resolver wrappers that would imply execution authority. Future owner-local query uses current ledger ownership rather than a cross-store snapshot.

## 14. Test Design

| Test | Exact finite window | Expected evidence |
| --- | --- | --- |
| TEST-001 | `AgentRecoveryDispositionEvidenceTest` plus `AgentRunDispatcherTest#discovers_queued_runs_using_the_persisted_owner`, `AgentRunReconcilerTest#a_callback_from_a_stopped_generation_cannot_run_after_restart` | Before claim QUEUED; current lease ACTIVE only with positive scope/fence; equality at expiry, null lease and due deadline classify expected stall; stale scheduler/ambiguous/foreign/legacy produce no safe classification or action |
| TEST-002 | `AgentRecoveryDispositionEvidenceTest`, `ToolInvocationRecorderTest#unknown_replay_is_metadata_only_and_never_resends_or_resettles_but_keeps_verified_accounting_truth`, existing dispatch-before-receipt and `late_known_facts_survive_cancellation_capability_reissue_and_thread_interruption_before_accounting` | Before dispatch, after DISPATCHING without receipt, after known local receipt and late receipt observed separately. Unresolved write never dispatches again under existing regression. Terminal Agent UNKNOWN and Tool UNKNOWN never overwritten by status or budget. Cap+1 proves incomplete |
| TEST-003 | `WorkerRecoverySpecificationTest#dispositionPrecedenceAndReportedFailureAreIndependent`, `ProcessWorkerRecoveryServiceTest#rejectsAmbientTransactionsAndConcealsMissingExactStart` | Preserve owner auth and separate Worker UNKNOWN even if native absent/historical or evidence incomplete. Existing read path never executes restore |
| TEST-004 | `AdkSessionEventCodecTest#typed_events_restore_generic_values_in_every_supported_native_slot` plus source evidence fixture | Committed Session event and typed projection are distinguishable from checkpoint eligibility. Unsupported codec, incomplete projection or changed definition may never promote resume |
| TEST-005 | Targeted source/fixture audit | Assert 0 synthetic writes, new dispatch, settle, current PS restore or raw result read in the disposition slice. No new public API, configuration or migration; finite safe evidence only |

Finite selector `AgentRecoveryDispositionEvidenceTest,AgentRunReconcilerTest,AgentRunDispatcherTest,ToolInvocationRecorderTest,AdkSessionEventCodecTest,WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest` with the existing JDK 25 source-reactor settings and explicit `-DskipITs=true`. Each owning report must actually run; no assertion uses an opt-in skip as proof.

Future authorized I uses `AgentReconcilerPostgresTest,AgentRunGovernancePostgresTest,AdkSessionPostgresTest,ToolExecutionJournalPostgresTest,AgentTerminalOutboxPostgresTest,AgentResultInboxPostgresTest` under separate isolated credentials. Future E/S11 verifies T3-12 Worker replacement and separate AR before claim, after claim before finish, Tool after dispatch before receipt, and after persisted Session event. These are not part of finite acceptance. No runtime currently executed.

## 15. Non-functional and Cross-cutting Design

Exact owner boundaries before any report. No broad operator bypass, tenant selection, correlation enumeration, raw identifiers from other scope, tokens, hashes tied to credentials, lease nonce, private output, model data, SQL messages, action versions or arbitrary reason. Bound any candidate query and output. Existing no-store and current per-request IAM remain authoritative. Clock interpretation uses UTC and one same-owner observation only.

## 16. Compatibility, Migration, Rollout, and Rollback

Lock exact source prerequisites first. Initial source includes the internal AR read and finite matrix/evidence. Do not apply checkpoint migration, switch any flag, schedule, start an app or expose a new permission. Existing real run/Worker behavior stays. Any eventual external T4-04 adapter adds only an independently reviewed owner read. New resume needs a separate pinned-mode S11 fault proof and current external-effect/authority checks.

## 17. Alternatives and Decisions

| Candidate | Verdict |
| --- | --- |
| Treat legacy run status as full retry verdict | Reject: no lease, receipt, tool or owner completeness proof |
| Reuse full getResult on every report | Reject: reads private output to obtain one scalar |
| Cross-source JOIN or shared operator access | Reject: no shared authority/transaction |
| Internal minimized Agent projection + existing T3-10 owner query + finite evidence | Select for first source slice; T4-04 external caller remains separate |
| New checkpoint/resume now | Outside scope; upstream method signatures do not establish Tianji runtime safety |

## 18. Risks and Open Questions

| Risk / decision | Resolution |
| --- | --- |
| Actual consumer and deployment authorization | DEC-005 locks only internal query for T4-04; HTTP/PaC and runtime need separate reviewed contract |
| Source sequencing | DEC-006 locks existing prerequisite source. T3-12 E and S11 remain unexecuted |
| Incomplete sources or late facts | Retain uncertainty and exact source boundary. No guessed no-effect or replay |
| Snapshot vs process liveness | ACTIVE only current recorded lease. Real S11, independent E and actual resume remain gated |

## 19. Traceability Matrix

| Requirement | Use case | Design and proof |
| --- | --- | --- |
| REQ-001 | UC-001/UC-003 | §7 lifecycle, TEST-001 |
| REQ-002 | UC-001/UC-002 | §7 independent axes, TEST-002/003 |
| REQ-003 | UC-001/UC-002 | §9 exact owners, TEST-001/003 |
| REQ-004 | UC-003 | §14 fault windows, TEST-001–004 |
| REQ-005 | UC-001/UC-002/UC-003 | §10/15, TEST-002/003/005 |
| REQ-006 | UC-003 | §16/18, TEST-005 and future separate gates |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 existing AR/PS | Current owner-local test boundary | None |
| MC-REUSE-001 | Applicable | PASS | §2/9 current readers | Existing owner APIs and finite test seams | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Semantic evidence test and typed facts | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | Strict scope and closed classes | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Existing immutable DTO and test values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No new production mapping layer | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No production logging changes | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | No Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/14 | Existing JDK/JUnit | None |
| MC-JSON-001 | Applicable | PASS | §7/9 | Closed existing scalar/DTO semantics | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | DB snapshot and finite exact time | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No config changes | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | No complex mutable behavior | None |
| MC-SCOPE-001 | Applicable | PASS | §3/5/8/16 | Exact internal read and finite source locked | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and separate I/E gates | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/16/18 | Exact source boundary reviewed; S11/E runtime separate | Recheck finite source; never claim live readiness |

**PASS — Ready for user review**

仓库外候选供 Root 和 owner 审阅。本次只读源码。没有仓库、Maven、PostgreSQL、应用、浏览器、checkpoint 或运行数据变更。
