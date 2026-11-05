# T4-05 Worker 执行器交接与并发槽位

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Bugfix |
| Complexity | Simple |
| Complexity Drivers | One existing bounded Worker admission and one atomic decision per submission |
| Created | 2026-11-06 00:40 Asia/Shanghai |
| Updated | 2026-11-06 00:40 Asia/Shanghai |
| Owner | Tianji Worker starter |
| Repository | tianji-service |
| Scope | Current RemoteWorkerPoller executor handoff after a synchronous RuntimeException |
| Change Surface | One existing Worker class and its finite owning test |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17 S10 and §19 T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) 的 Worker 故障和并发边界 |
| Baseline Revision | 4ff3f279ff302a8d7aef7c885bbadd952a908165 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [Worker executor handoff](../plan/2026-11-06-00-41-worker-executor-handoff.md) |

## 1. Summary

Worker 为每个已准入任务保留一个并发槽位。运行回调与执行器抛错路径必须原子决定哪个路径拥有该槽位的归还职责。抛错路径赢后才到达的回调必须在领取任务前退出。现有同步、正常排队、异步和停机合同保留。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Existing ownership | RemoteWorkerPoller#tick/#finished | tick 在 monitor 内取得 semaphore permit 且增加 inFlight。finished 同时归还 permit、减少 inFlight、通知关闭等待。 |
| EVD-002 | Reproduced path | RemoteWorkerPoller#tick current started.set(true) vs catch !started.get | If the executor preserves a callback but throws before that callback starts, catch releases. A late same-generation callback still sets started, acquires and finally releases again. |
| EVD-003 | Existing tests | RemoteWorkerPollerTest#executor_that_runs_inline_then_throws_does_not_release_the_same_slot_twice, #bounded_slots_and_shutdown_prevent_a_new_generation_from_overlapping_old_work | Inline start-before-throw and controlled async/stop are covered. Delayed callback-after-throw was uncovered. |
| EVD-004 | Existing operational boundary | RemoteWorkerPoller#active/#start/#stop/#process and WorkerDependencyBoundaryTest | Current generation gate, bounded shutdown, host managed execution, exact WorkerClient and business receipts remain independent. |
| EVD-005 | S10 requirements | [Release acceptance matrix](../evidence/2026-11-05-release-acceptance-matrix.md) | Real business DB, new lease and acknowledgement/restart are separate I/E evidence. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Small fix in existing Worker starter; no new module, example or generated code.

## 3. Goals and Non-goals

Restore one slot release per submission under synchronous RuntimeException, including callback-after-throw. Preserve currently accepted inline and async scheduling, receipts and stop behavior. Real lease expiry, exactly-once business storage, remote acknowledgement recovery and full S10 require existing I/E evidence.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Worker executor admission and finite test | Affected | EVD-001–004 | One winner for work and completion | Exact state/race and finite proof | §7/§8/§14/§15/§16 |
| Worker remote, storage and deployment | Context-only | EVD-004/005 | Existing scope, receipt and source boundaries | Preserve and verify | §9/§11/§16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Atomically decide completion ownership | Must | Callback or synchronous RuntimeException path claims once; never releases twice | EVD-001/002 |
| REQ-002 | Reject a late callback when rejection won | Must | No WorkerClient.acquire or store work; second accepted callback still owns the only slot; no negative inFlight | EVD-002 |
| REQ-003 | Preserve existing valid execution and stop | Must | Inline-before-throw and ordinary queued/asynchronous work keep original lifecycle; stale generation avoids remote work and releases if it still owns an outstanding slot | EVD-003/004 |
| REQ-004 | Keep source and evidence scope exact | Must | Two Java paths, no API, data, permission, dependency or actual runtime; one intended RED and finite green | EVD-004/005 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | Existing Worker poller | Bound accepted local admissions | Current scheduler tick | Existing runtime Worker scope | EVD-001 |
| ACTOR-002 | Host Executor | Deliver Runnable or report admission failure | Executor.execute | Existing host-provided dependency | EVD-002/003 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Assign the slot once | ACTOR-001 | ACTOR-002 | Existing permit reserved | Current generation and configured capacity | Callback performs current permitted work and releases once | Rejection wins and retires later callback | Capacity and shutdown counters remain valid | REQ-001–004 | Existing internal executor seam | TEST-001–004 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | Reuse one existing AtomicBoolean per submission; both paths use compareAndSet(false,true). |
| DEC-002 | Preserve RuntimeException handling, warning, semaphore, inFlight monitor and generation check. |
| DEC-003 | No guarantee for a host Executor that returns normally then drops its callback permanently or JVM Error. Existing timeout behavior remains. |
| DEC-004 | Finite fake executor proves the delivered callback race. Business lease, external writes and S10 I/E remain independent. |

## 6. Project Technology Context

Existing traditional Worker starter source, Java 25, Spring TaskScheduler/SmartLifecycle, java.util.concurrent and existing Mockito/JUnit. Dependency boundary is engine-free.

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
| Rule 1 | Yes | Existing Worker starter | Use existing RemoteWorkerPoller and owning test | §8 | TEST-001 | PASS |
| Rule 2 | Yes | Existing Worker admission and exact fixture | Atomically admit only one completion owner | §7 | TEST-001/002 | PASS |
| Rule 3 | No | Existing AtomicBoolean | No POJO or conversion change | §10 | TEST-001 | N/A |
| Rule 4 | No | Existing warning and host wiring | No logger, Bean or injection change | §15 | TEST-002 | N/A |
| Rule 5 | Yes | Existing JDK AtomicBoolean | Reuse current concurrency primitives | §6 | TEST-001/004 | PASS |
| Rule 6 | No | Existing WorkerClient | No JSON or wire change | §9 | TEST-003 | N/A |
| Rule 7 | No | Existing Worker defaults | No property change | §8 | TEST-003 | N/A |
| Rule 9 | No | One existing local ownership decision | No new pattern or framework | §13 | TEST-001 | N/A |
| Rule 10 | No | Existing lease and shutdown | No time contract change | §6.3 | TEST-002 | N/A |
| Rule 11 | Yes | Current Worker starter layering | Retain owner, scope and engine-free dependency | §8/§11 | TEST-003/004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: No deadline, lease, retry delay, wall clock, duration calculation or shutdown timeout semantics change. The defect concerns atomic ownership of the existing completion action.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing atomic callback flag | Expand | REQ-001–003 | Separate get and set | Read/write gap lets both paths release | Two CAS on same existing local flag, no new object | Add |
| Existing scheduler/generation/store | Keep | REQ-003/004 | New admission abstraction | Current concurrency owner and scopes suffice | None | Keep |
| Finite deferred-after-throw fixture | Add | REQ-002/004 | Current inline test | Inline cannot reproduce catch-before-start order | One local deterministic regression | Add |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | Original acquisition | Existing poll | One existing started flag | Late callback can double release | Inline case only |
| Selected design | Same original acquisition on accepted path | Existing poll | One atomic ownership decision | Both paths cannot own one permit | No acquisition from rejected delayed callback |

### 7.1 System Architecture Design

Existing tick permit and inFlight → one atomic callback/rejection ownership → existing process if active and winning callback → existing finished exactly once.

### 7.2 High-Level Design

Change the existing AtomicBoolean from informational started to one completion claim. A callback that loses the claim returns without calling process or finished. A rejection path that loses logs its existing bounded warning and leaves the callback's completion intact.

### 7.3 Detailed Design

| Order | Winning path | Remote work | Slot release |
| --- | --- | --- | --- |
| Inline runnable completes, executor then throws | Callback CAS | Existing generation gate; one normal attempt | Existing finally once; catch no second release |
| Executor queues callback and returns normally | Callback CAS when dispatched | Existing current generation and capacity | Finally exactly once |
| Asynchronous callback starts before submit thread observes rejection | Callback CAS | Existing work only | Finally once |
| Executor throws before callback starts, including delayed delivery | Catch CAS | Late callback returns before acquire/store | Catch once; callback does not release |
| Accepted callback runs after stop | Callback CAS if still outstanding | Existing generation gate rejects work | Finally once; no stale acquisition |

Preserve current warning and stop flow. The flag is local to a submission and cannot be shared across tasks or generations.

## 8. Package Structure and Code File Tree

| Action | Exact path | Responsibility |
| --- | --- | --- |
| Modify | stellar-enclosure-tianji-worker-starter/src/main/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPoller.java | Atomic decision in current tick |
| Modify | stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java | Delayed-after-rejection regression |
| Create | docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md | Exact behavior and limits |
| Create | docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md | Exact finite gate and scope |
| Create | docs/egon/evidence/2026-11-06-worker-executor-handoff.md | Source evidence |

## 9. Interface Definitions

No new or changed public interface. Existing WorkerClient, TaskScheduler, Executor, business receipt and configuration contracts remain.

## 10. POJO and Data Model Design

No new model or wire contract. One existing local AtomicBoolean has an explicit ownership meaning.

## 11. Database Design

No SQL, migrations, database access pattern, permissions or state transition change.

## 12. Frontend Page Design

No frontend change.

## 13. Design Patterns and Architecture Principles

Use a direct atomic one-winner decision on the existing submission; no abstractions or scheduling framework required.

## 14. Test Design

| Test ID | Exact finite proof | Expected observation |
| --- | --- | --- |
| TEST-001 | RemoteWorkerPollerTest#callback_delivered_after_executor_rejection_cannot_acquire_or_release_a_slot | Delayed rejected callback cannot acquire, accepted callback retains sole slot, duplicate delivery is harmless and stop/restart remains valid |
| TEST-002 | RemoteWorkerPollerTest#executor_that_runs_inline_then_throws_does_not_release_the_same_slot_twice and #bounded_slots_and_shutdown_prevent_a_new_generation_from_overlapping_old_work | Existing inline, bounded async and shutdown behavior retained |
| TEST-003 | Full RemoteWorkerPollerTest | Existing exact acknowledgement retry, failure classification and config lifecycle regressions |
| TEST-004 | WorkerDependencyBoundaryTest | Worker classpath stays engine-free |

## 15. Non-functional and Cross-cutting Design

Constant one atomic ownership flag per admitted callback, one CAS per competing path. No changes to scheduler cadence, max concurrency, deadlines, protocol logs or deployment authority. Warning retains the current safe failure class policy.

## 16. Compatibility, Migration, Rollout, and Rollback

Existing Worker start/stop, remote API and client types unchanged. No migrations or config. Revert the single focused fix if authorized.

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| Preserve started get/set | Rejected | Catch-before-start race permits two releases |
| Add scheduler/executor framework | Rejected | Current semaphore and monitor suffice |
| Ignore callback if rejection claimed its permit | Selected | Atomic outcome with minimum surface |

## 18. Risks and Open Questions

| ID | Risk | Disposition |
| --- | --- | --- |
| OPEN-001 | Host silently drops callbacks after returning success | Existing stop timeout; outside this fault contract |
| OPEN-002 | Real lease/ack/DB and S10 | Independent I/E |
| OPEN-003 | Adjacent source commits | Re-lock current baseline with Worker paths unchanged before execution |

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7.3 | TEST-001/002 |
| REQ-002 | UC-001 | §7.3 | TEST-001 |
| REQ-003 | UC-001 | §7/§16 | TEST-002/003 |
| REQ-004 | UC-001 | §8/§11/§14 | TEST-003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Current Worker owner and poll flow | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Existing Worker scheduler, executor and permit | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK and engine-free Worker dependencies | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Existing Worker tick and exact test naming | None |
| MC-VALID-001 | Applicable | PASS | §7/9 | One atomic winner, original current-generation gate | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No POJO change | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO/PO mapping change | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | Existing bounded failure warning | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | Existing host wiring unchanged | None |
| MC-UTIL-001 | Applicable | PASS | §6 | Current JDK atomic and concurrent types | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing wire bytes | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No time contract change | None |
| MC-CONFIG-001 | Not applicable | N/A | §8 | No property change | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct adapter, simple | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Exact Worker source scope, no data or authority change | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact future finite gates and runtime boundary | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source slice independently reviewable | Pending live backlog/SLO/S12 decisions remain outside source slice |

### 20.6 Final verdict

PASS — Ready for user review

只验证有限宿主执行器和当前 Worker 源码。真实数据库、外部业务和 S10 由各自证据验收。
