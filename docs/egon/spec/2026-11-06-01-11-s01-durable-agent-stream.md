# S01 单 Agent 持久流验收场景

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-11-s01-durable-agent-stream.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated JUnit case joining existing HTTP adapter, Runner and ledger with a local Fake |
| Created | 2026-11-06 01:11 Asia/Shanghai |
| Updated | 2026-11-06 01:11 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | One existing AS PostgreSQL test class, plus Spec, Plan and evidence |
| Change Surface | New opt-in S01 method and test-local assertions |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [Master §17.2 S01 and T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service e5f6df84372774bac48bc7c6649fd56f0ed31520 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S01 source plan](../plan/2026-11-06-01-12-s01-durable-agent-stream.md) |

## 1. Summary

为 S01 增加一条操作者专用的 PostgreSQL 集成用例。一次 HTTP POST 创建运行，真实 ADK Runner 消费确定性的 Spring AI Fake 流，既有 SSE 读取并重放两条持久 TEXT_DELTA，结果与 COMPLETED 状态写入同一行并与终态事件共享 PostgreSQL 事务。源码门禁运行相邻有限测试并检查新用例的实际编译产物。隔离 PostgreSQL 集成执行独立留证。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Release acceptance matrix](../evidence/2026-11-05-release-acceptance-matrix.md) | S01 的 HTTP、SSE、ADK 和 PostgreSQL 各有证据入口，现有报告缺一条贯穿上述路径的用例。 |
| EVD-002 | [AgentRunHttpPostgresTest](../../../stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java) | 既有 MockMvc 使用真实 controller、SSE、owner ledger 和 Flyway V1–V9，复用隔离 loopback 入口。现有 POST/SSE 方法通过取消进入终态。 |
| EVD-003 | [AdkAgentRunExecutorPostgresTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java) | 单 CONFIG writer、既有 ADK Runner、Spring AI ChatModel Fake、已验证 owner 和授权替身覆盖执行层。 |
| EVD-004 | [AgentLedgerService](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java) | finish 在同一事务执行 fenced UPDATE、结清未决预算状态并追加 RUN_STATUS_CHANGED。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing Agent Server test class only. No project generation or archetype scaffolding.

## 3. Goals and Non-goals

同一运行标识连接公开 POST/GET/SSE、真实 ADK Runner 和主库证据，结果正常完成。线上鉴权、调度器自动发现、实际网络传输和真实供应商各由现有其他阶段验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS PostgreSQL fixture | Affected | EVD-001–004 | New isolated method and its assertions | Flow, evidence, failure and gates | §7, §8, §14, §15, §16 |
| AS public HTTP and SSE | Context-only | EVD-002 | Existing owner-bound methods and SSE frames | Exact reuse | §9 |
| AR runner and database | Context-only | EVD-003/004 | Existing terminal transaction and definitions | Observer only | §11 |
| Web | Not applicable | EVD-001 | Current Web tests retain separate evidence | Boundary only | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | One POST reaches real Runner under the existing fixture | Must | One accepted POST creates one QUEUED run. An exact bounded execution uses the same owner/run and Fake subscription count is one. | S01, EVD-002/003 |
| REQ-002 | Durable delta order and replay | Must | At least two distinct ordered TEXT_DELTA values, exact SSE event IDs and persisted event sequence. Reconnect from the first delta ID yields exactly the suffix and stream.complete. | S01, EVD-001/002 |
| REQ-003 | Atomic durable completion and result | Must | GET result and PostgreSQL agree on the terminal run/result. Current terminal run row and its terminal event at the watermark have matching xmin and terminal status. Repeated GET/SSE does not add a model subscription. | S01, EVD-004 |
| REQ-004 | Preserve runtime gates and record actual source evidence | Must | Compile exact new operator test, run finite regression separately, preserve all production files and migrations, and record actual result boundaries. | T4-05, EVD-001 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Check actual Java compilation and finite suites | Local offline test reactor |
| ACTOR-002 | Authorized disposable-fixture operator | Observe one complete S01 normal path | Explicit method selection and fresh private loopback PostgreSQL |
| ACTOR-003 | Synthetic owner | Create and query a fixture-owned run | Existing test SERVICE identity and isolated permission guard |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source and report boundaries | ACTOR-001 | Maven, JUnit, javap | Source gate | Clean exact baseline | Finite suites and compiled probe | Compilation, test or scope mismatch fails | Operator I remains pending | REQ-004 | Internal test runner | TEST-001/002 |
| UC-002 | Execute, replay and observe durable result | ACTOR-002 | ACTOR-003, ADK, Fake, PostgreSQL | Exact opt-in JUnit method | Existing fixture guard and unique synthetic command | One run, ordered replay and atomic terminal evidence | Invalid fixture, stream or durable evidence fails | Private I report | REQ-001/002/003 | Existing Run HTTP and SSE | TEST-003 |

## 5. Constraints, Assumptions, and Decisions

The existing class uses the property tianji.http.postgres.test.url, an exact credential-free 127.0.0.1 URL with the tianji_it_ prefix, and rejects a pre-existing tianji_agent schema before Flyway V1–V9. Supply the explicit method selector to an authorized operator and use a fresh disposable primary. The test Fixture reuses existing AS security/context construction and the existing AR fixture authorization pattern. It never obtains an actual IAM credential or provider route. Runtime launch, database creation and disposable database disposal remain with the operator.

## 6. Project Technology Context

The existing Java 25 agent server boundary uses Spring MockMvc, JUnit Jupiter, Jackson and the existing AR Spring AI adapter, native ADK Runner, JDBC and Flyway. All constructor use is local to the test. Existing AS→AR module dependency owns runtime invocation. Existing production routing and three-layer ownership are preserved.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-002 | Existing AgentRunHttpPostgresTest naming and exact method | Test | TEST-002 | PASS |
| Rule 2 | Yes | EVD-002/003 | Exercise existing command/owner checks and reject wrong replay or result identity in the test | Existing DTOs | TEST-001/003 | PASS |
| Rule 3 | No | EVD-002 | Reuse DTOs and local fixture values | No business POJO or mapper | Scope | N/A |
| Rule 4 | No | EVD-002 | Existing test-local collaborators | No bean or logger | Scope | N/A |
| Rule 5 | Yes | EVD-002/003 | Existing JDK, Spring test, JUnit and framework APIs | Test | TEST-001/002 | PASS |
| Rule 6 | Yes | EVD-002 | Existing JsonMapper decodes DTOs and payload; no ordinal conversions | Test wire reading | TEST-001/003 | PASS |
| Rule 7 | No | EVD-002 | Existing test property and no application profile change | Existing opt-in | Scope | N/A |
| Rule 9 | No | EVD-002/003 | Direct single-case fixture | No complex production behavior | Scope | N/A |
| Rule 10 | Yes | EVD-002/004 | Existing UTC instants and java.time.Duration for bounds | Test | TEST-002/003 | PASS |
| Rule 11 | Yes | EVD-002/004 | Existing AS/AR owners, Flyway and public CQE | Test only | Scope | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run and event timestamps | Occurred instant | java.time.Instant | UTC instant | UTC Z | Explicit UTC fixture clock | No display change | Existing lease and watermark | Existing Clock and DB precision | Existing contracts | TEST-002/003 |
| TIME-002 | Fixture runtime bound | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | No display change | Existing fixture and model control timeout | Existing scheduler and test timeout | Existing fixture semantics | TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Method in existing AS fixture | Add | REQ-001–004 | Separate existing HTTP and AR tests | Separate tests cannot prove one shared run and commit | One fixture-only run and read-only PostgreSQL observation | Add |
| Existing Runner and stream stack | Keep | REQ-001/002 | Alternate endpoint or runtime | Existing path already owns the desired behavior | No production cost | Keep |
| Existing PostgreSQL fixture | Keep | REQ-003/004 | Persistence mocks | Mocks cannot witness durable transaction | Existing disposable V1–V9 | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Existing source gates | None | Local finite reports | Existing public contracts | Report can support only isolated behavior | Fast regression |
| New operator I | Local PostgreSQL only | Standalone POST, complete and resume SSE, result | One run and existing events | Exact owner/sequence and DB snapshot required | Same-run, transaction-backed witness |

### 7.1 System Architecture Design

One AS JUnit method uses existing MockMvc with AgentRunController, AgentRunSseController, AgentRunEventStream, VerifiedAgentIssuerFilter and AgentLedgerService. Use existing AR AdkAgentRunExecutor with the native Runner and a local Fake ChatModel via AgentFlowFactory. Exercise only the observed owner/run. The explicit fixture execution boundary keeps unrelated queued rows out of the case.

### 7.2 High-Level Design

Publish one synthetic CONFIG writer, post one command and require the queued run and unique key to agree. Emit two known nonempty Spring AI chunks using the existing adapter, require one model subscription and terminal COMPLETED. Fetch complete SSE, resume from the first delta cursor and require the ordered suffix and terminal frame. Query result and join the exact run/event rows in one read-only SQL statement.

### 7.3 Detailed Design

Test method `one_http_start_projects_and_replays_two_durable_deltas_with_atomic_completion` uses existing fixture setup. Fake stream counts subscriptions, emits deterministic text, and forbids synchronous calls. Ordinary HTTP authorization and owner binding reuse EVD-002. Check frame ordering by decoding AgentEventDTO from the existing data field and matching its sequence to SSE id. Compare durable event sequence, exact TEXT_DELTA payloads, the replay suffix and terminal stream.complete watermark. Read existing result through the same owner's result route. For terminal evidence scope run by tenant/subject type/issuer/subject/run ID and join event by tenant/run/watermark. Validate result status and runId, terminal event payload status, final sequence and equality of run/event xmin; this fresh test performs no intervening post-completion mutation. Fixture fails on timeout, missing status/result/event, incorrect transaction ID, additional model subscription or replay mismatch.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-01-11-s01-durable-agent-stream.md` |
| Create | `docs/egon/plan/2026-11-06-01-12-s01-durable-agent-stream.md` |
| Create | `docs/egon/evidence/2026-11-06-s01-durable-agent-stream.md` |

## 9. Interface Definitions

Context-only. AgentRunController POST `/api/tianji/v1/agent-runs` and GET `/{runId}/result`, and AgentRunSseController GET `/{runId}/events` retain existing current owner policy, wire fields and cursor behavior. Existing JUnit source property and fixture are reused.

## 10. POJO and Data Model Design

Existing AgentRunDTO, AgentEventDTO, AgentExecutionResult and Spring AI response types serve the test. Any private decoded facts are immutable test-local values. No new production mapping, carrier or persistence representation.

## 11. Database Design

Context-only. Use the existing empty-schema guard and Flyway V1–V9. Verify `tianji_agent.agent_run` and `tianji_agent.agent_event` using scoped read-only queries over their existing identity, result, event type/payload and sequence. Transaction proof uses PostgreSQL's live MVCC row transaction ID in a fresh isolated fixture. Schema, migration scripts, indices and transactional writes remain owned by the existing runtime.

## 12. Frontend Page Design

Not applicable. Browser and BFF verification are separate. The test consumes existing SSE as an HTTP adapter client.

## 13. Design Patterns and Architecture Principles

Considered a new reusable end-to-end testkit and an alternate transport adapter. A single existing fixture plus dependency substitution at the current model port is sufficient. The real Runner, HTTP adapter and storage paths need no parallel abstractions.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Existing finite AgentRunHttpTest, AgentRunEventStreamCapacityTest, AdkEventProjectorTest and AdkNativeAgentCompletionTest. Record fresh passing counts and zero skipped cases. |
| TEST-002 | New exact method compiled in AgentRunHttpPostgresTest class with original opt-in property, no PG report. Verify source scope and migration bytes. |
| TEST-003 | Separately selected method with actual PostgreSQL and fixture Fake, one unskipped report required. Verify atomicity, durable ordered replay and single subscription. |

## 15. Non-functional and Cross-cutting Design

Use current class timeout, bounded model execution, actual framework cleanup and isolated synthetic data. Preserve existing source test defaults, permission wiring and network boundary. Omit raw fixture URL, event body and identity from published evidence.

## 16. Compatibility, Migration, Rollout, and Rollback

All source changes are in the existing test class and its three documents. The existing integration entry remains opt-in. Revert the one source commit to remove the added scenario. The current scope compiles the test and runs finite gates only.

## 17. Alternatives and Decisions

Keep the current tests as adjacent regressions and add the integrated case where HTTP setup and exact disposable database checks already exist. The direct in-process adapter boundary is sufficient for S01 I; a hosted browser/service stack belongs to independent release acceptance.

## 18. Risks and Open Questions

A positive source gate proves compilation and finite coverage. Actual S01 I is pending until the explicit method passes on a fresh isolated primary. Production IAM, automatic dispatch, transport through a live BFF and performance stay separate.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §5/7/9 | TEST-002/003 |
| REQ-002 | UC-002 | §7/9/14 | TEST-001/003 |
| REQ-003 | UC-002 | §7/11/14 | TEST-003 |
| REQ-004 | UC-001 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AS test owns the fixture | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing MockMvc, ADK and ledger | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing module dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Reuse strict fixture and wire checks | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing Jackson decoding only | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | No new runtime logger | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK/Spring/JUnit/ADK | None |
| MC-JSON-001 | Applicable | PASS | §7.3/10 | Existing JsonMapper and payload | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing UTC and timeout contracts | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Current test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite vs integration provenance | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source scope independent of later I | None |

### 20.6 Final verdict

PASS — Ready for user review
