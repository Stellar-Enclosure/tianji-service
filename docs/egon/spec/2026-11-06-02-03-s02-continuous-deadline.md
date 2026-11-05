# S02 持续增量总时限验收场景

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-03-s02-continuous-deadline.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated AR JUnit method with existing Runner, Fake model and durable owner ledger |
| Created | 2026-11-06 02:03 Asia/Shanghai |
| Updated | 2026-11-06 02:03 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test class plus three review documents |
| Change Surface | One opt-in S02 deadline regression method and local assertions |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [Master §17.2 S02, T1-03 and T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service 539cd2b58027299a0693170f42404d17f9b23b8b |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S02 source plan](../plan/2026-11-06-02-04-s02-continuous-deadline.md) |

## 1. Summary

补充一条持续输出时的 S02 PostgreSQL 验收场景。真实 CONFIG Runner 使用本地 Fake 持续发送非终态文本。操作者通过同一 owner 的持久事件、数据库总时限和终态结果，验证空闲续期不会延长总时限。源码门禁只执行相邻有限测试并验证新增集成方法的编译产物。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Release acceptance matrix](../evidence/2026-11-05-release-acceptance-matrix.md) | S02 同时要求持续输出跨总时限、排队取消、执行中取消与失联后处置。本切片补持续输出证据。 |
| EVD-002 | [AdkAgentRunExecutorPostgresTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java) | 总时限测试的 Fake 从不发帧。空闲续期测试只发送五帧并提前正常完成。并行测试由另一分支的空闲计时终止。 |
| EVD-003 | [AdkAgentRunExecutor](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java) | executeScoped 将独立数据库总时限、控制栅栏与模型 idle 合并成终止信号，通过现有 Runner 消费模型流，并写入持久结果。 |
| EVD-004 | [AgentLedgerService](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java) | appendEvent 与 appendStatusEvent 使用数据库 Instant。现有 owner 查询可核对有序事件、总时限和结果。 |
| EVD-005 | [PostgresTestDatabase](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/PostgresTestDatabase.java) | 当前 opt-in 属性仅接受显式本机一次性库，要求 tianji_agent schema 缺失，使用 Flyway target=9 并验证。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: One existing AR integration test owns the scenario. No project generation or archetype example work is involved.

## 3. Goals and Non-goals

验证同一 fixture Run 在模型仍持续发送非终态增量时到达数据库总时限并终止。排队和运行中取消、失联恢复、实时供应商、线上 IAM 和性能仍由各自验收路径负责。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR PostgreSQL test | Affected | EVD-001/002/005 | Add one isolated test and assertions | Flow, deadline and evidence gates | §7, §8, §14, §15, §16 |
| Native Runner and deadline policy | Context-only | EVD-002/003 | Existing execution and policy behavior | Call and observe existing behavior | §9/10 |
| PostgreSQL and ledger | Context-only | EVD-004/005 | Existing owner read, write and version history | Database clock and evidence | §11 |
| HTTP and Web | Unchanged | EVD-001 | S02 HTTP/Web tests own their existing scope | Independent from AR fixture | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Observe ongoing model output on one real CONFIG Runner | Must | One bounded fixture run uses existing verified actor and one Fake stream subscription. Several projected TEXT_DELTA are committed in order, and the last committed delta is inside the idle window before the original database deadline. | S02, EVD-002/003/004 |
| REQ-002 | Enforce the original total deadline independently of model idle | Must | Fake subscription is cancelled without natural completion. The returned run and owner ledger agree on TIMED_OUT, original deadline and terminal state. Result error is exactly TIANJI_RUN_TIMED_OUT. The ordered durable stream ends with RUN_STATUS_CHANGED to TIMED_OUT at the run watermark. | S02, EVD-003/004 |
| REQ-003 | Keep the new evidence isolated and distinguish compilation from runtime proof | Must | Exact method and original PG guard compile, three finite suites pass separately, exact four-path scope with 28 production migrations byte-identical. A source turn generates no PostgreSQL test report. | T4-05, EVD-001/005 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify exact compiled method and finite regressions | Existing local offline Maven reactor |
| ACTOR-002 | Authorized disposable-fixture operator | Observe one live sustained-delta timeout | Exact one-method opt-in and fresh private loopback PostgreSQL |
| ACTOR-003 | Synthetic verified USER owner | Start and read only its fixture Run | Existing actor and fixture authorizer |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source boundaries | ACTOR-001 | Maven, JUnit, javap | Exact source gate | Reviewed clean baseline | Three finite suites and compiled opt-in method | Scope or verification mismatch fails | Separate I1 still pending | REQ-003 | Existing offline reactor | TEST-001/002 |
| UC-002 | Observe hard timeout while deltas are active | ACTOR-002 | ACTOR-003, Runner, Fake, PostgreSQL | Exact I1 selector | Fresh isolated primary and owner-bound synthetic definition | Last timely delta, cancellation, terminal ledger and exact error | Insufficient delta, unexpected idle, missing cancellation or wrong result fails | Private report for this S02 scenario | REQ-001/002 | Existing AR methods | TEST-003 |

## 5. Constraints, Assumptions, and Decisions

Existing class guard `tianji.postgres.test.url` and PostgresTestDatabase verify the exact credential-free `jdbc:postgresql://127.0.0.1:<port>/tianji_it_<name>` form and require a fresh database without tianji_agent schema. Keep the current target V1–V9 for this fixture. Operator provisions, authorizes and disposes of that database independently. Existing verified fixture actor and bounded authorization substitute stay in test code.

Use a five-second total budget, a two-second idle window, and a nonterminal fragment every 100 milliseconds with a 90-frame safety cap. Bound the case by the existing JUnit class timeout and cancellation wait by two seconds. Only DB-origin instants establish the relative last-delta/deadline condition.

## 6. Project Technology Context

Existing Java 25 AR persistence test uses JUnit Jupiter, Reactor, Spring AI ChatModel/ChatResponse, AgentFlowFactory, ADK Runner, JdbcTemplate and Flyway. The test-only design stays within the existing Agent runtime architecture and its normal owner-ledger entry points.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-002 | Semantic method in the existing test | Test | TEST-002 | PASS |
| Rule 2 | Yes | EVD-002/004 | Reuse existing fixture guards and typed owner queries | Existing DTOs | TEST-003 | PASS |
| Rule 3 | No | EVD-002 | Existing DTOs and fixture values | No new POJO or mapper | Scope | N/A |
| Rule 4 | No | EVD-002 | Reuse existing test-local wiring | No bean/logger additions | Scope | N/A |
| Rule 5 | Yes | EVD-002/003 | Existing JDK, Reactor and JUnit facilities | Test | TEST-001/002 | PASS |
| Rule 6 | No | EVD-004 | Read existing typed DTO fields/payload maps | No mapper or enum encoding changes | Scope | N/A |
| Rule 7 | No | EVD-005 | Preserve current opt-in | No application profile change | Scope | N/A |
| Rule 9 | No | EVD-002 | Single fixture case | No new production pattern | Scope | N/A |
| Rule 10 | Yes | EVD-003/004 | Instant for authoritative deadlines and events, Duration for elapsed bounds | Test | TEST-003 | PASS |
| Rule 11 | Yes | EVD-004/005 | Existing AR owner ledger and Flyway target | Test only | Scope | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.deadlineAt and AgentEventDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Database clock | No display change | Compare last committed delta to original deadline | Existing DB clock and microsecond event precision | Existing mapping | TEST-003 |
| TIME-002 | Idle interval, inter-frame delay and worker/cancellation bound | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | No display change | Last delta within idle duration before deadline, independent test timeout | Existing scheduling | No scheduler or policy change | TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Method in existing AR fixture | Add | REQ-001–003 | Existing silent total timeout and short completing stream cases | Separate cases never witness active text at total deadline | One bounded operator run and owner ledger reads | Add |
| Existing Runner, Fake model seam and ledger | Keep | REQ-001/002 | Alternate runner or provider | Existing path owns this behavior | Existing fixture only | Keep |
| Existing database guard | Keep | REQ-003 | New service or separate fixture provisioning | Existing disposable isolation is sufficient | Existing target V1–V9 | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Source gate | None | Finite JUnit and bytecode report | Existing source and class | Passing finite cases only certify their own scope | Bounded source verification |
| New operator I1 | Private loopback PostgreSQL | Single synthetic run and model stream | Existing deadline, subscriptions and events | Require authoritative original deadline and durable terminal watermark | Ongoing-output deadline evidence |

### 7.1 System Architecture Design

One method in AdkAgentRunExecutorPostgresTest uses existing single CONFIG writer, FixtureModel, executor and owner-bound ledger. This exercises real production ADK execution and persistence with the test model port. No separate app, HTTP listener, collector or provider is needed for this I case.

### 7.2 High-Level Design

Publish one synthetic single CONFIG writer and start its owner-scoped Run. Capture the accepted absolute deadline before execution. Supply bounded periodic nonterminal Fake chunks, execute once, observe cancellation, then read the final Run, result and ordered persisted events from the existing ledger.

### 7.3 Detailed Design

Method `continuous_deltas_cannot_extend_the_total_deadline` uses the current actor, singleSpec/policy, FixtureModel.response and executor helpers. The Fake produces small nonempty text chunks with no terminal stop metadata, and counts cancellation. Keep emission/release and test waits bounded. Obtain durable events with the exact same owner/run and bounded query sized for the fixture. Require at least two committed TEXT_DELTA events, increasing first-to-last sequence, one model subscription, fewer than 90 emitted frames, and a last delta at or before the original DB deadline with a nonnegative remaining duration strictly below idle. Require the returned/persisted Run to be TIMED_OUT, the original deadline to remain equal, result error code TIANJI_RUN_TIMED_OUT, and the last event to match RUN_STATUS_CHANGED to TIMED_OUT and the persisted watermark. Require zero AGENT_OUTPUT events. A second executeQueued call on the same terminal Run must return the same state, preserve the exact event list and cause no second subscription. Any timeout of the fixture itself, missing evidence, natural completion, wrong result reason or wrong terminal boundary fails I1.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-03-s02-continuous-deadline.md` |
| Create | `docs/egon/plan/2026-11-06-02-04-s02-continuous-deadline.md` |
| Create | `docs/egon/evidence/2026-11-06-s02-continuous-deadline.md` |

## 9. Interface Definitions

Context-only. Reuse AdkAgentRunExecutor#executeQueued and AgentLedgerService#start, #get, #getResult and #events with the existing verified owner and current typed queries.

## 10. POJO and Data Model Design

Use existing AgentRunDTO, AgentEventDTO, AgentExecutionResult, ChatResponse and policy types. The test introduces no production carrier, serializer or result transformation.

## 11. Database Design

Context-only. Reuse existing isolated target V1–V9 and existing owner-scoped ledger calls. Compare database-origin deadline and event instants. The fixture uses only its own synthetic definition/run and current production status/result/event transaction paths.

## 12. Frontend Page Design

Unchanged. This case uses the AR fixture directly. Existing HTTP, SSE, Web and cancellation behaviors remain covered by separate tests.

## 13. Design Patterns and Architecture Principles

Reuse the existing Fake substitution at the model port and the real native Runner. The existing fixture suffices for a single deadline test. No testkit extraction, timing strategy or parallel infrastructure is needed.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Run existing AdkEventProjectorTest, AdkExecutionTransactionBoundaryTest and AdkNativeAgentCompletionTest. Record actual passing counts with no failures, errors or skips. |
| TEST-002 | Inspect JDK 25 javap output for exact added method and existing PG property; require no PostgreSQL fixture report. Verify four-path scope and 28 production migration bytes. |
| TEST-003 | Independent operator-only exact selector with real PostgreSQL, real Runner and local Fake. Require one unskipped test and database deadline, active delta, cancellation and terminal ledger evidence. |

## 15. Non-functional and Cross-cutting Design

Use bounded Fake output and waits, existing class timeout and disposable synthetic data. Evidence reports omit raw URLs, identity values and event payloads. Operator verifies actual deadline behavior separately.

## 16. Compatibility, Migration, Rollout, and Rollback

One existing test file and three review documents. Existing PostgreSQL opt-in and Flyway target remain. Rollback is the revert of this test-only commit.

## 17. Alternatives and Decisions

Retain existing silent deadline and idle refresh cases as independent coverage. The new method adds the missing actual overlap between ongoing deltas and total deadline using the current fixture.

## 18. Risks and Open Questions

The loopback primary must sustain the small synthetic stream for the timing witness. Missing or skipped I1 evidence leaves live acceptance pending. Full S02 cancellation and shutdown requirements, real provider behavior and throughput require their own gates.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §5/7/10/14 | TEST-003 |
| REQ-002 | UC-002 | §6.3/7/11/14 | TEST-003 |
| REQ-003 | UC-001 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR persistence test owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing CONFIG fixture, Runner and ledger | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Reactor, JUnit, Spring AI and PG fixture already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Existing PG guard and exact scoped status, deadline and event assertions | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Reactor and JUnit | None |
| MC-JSON-001 | Not applicable | N/A | §7.3/10 | Existing typed events and payload maps | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB instant evidence and bounded elapsed timeout | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 20.6 Final verdict

PASS — Ready for user review
