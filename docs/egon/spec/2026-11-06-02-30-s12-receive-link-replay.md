# T4-02 S12 Agent 结果重放 Span Link 验收探针

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-30-s12-receive-link-replay.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated replay scenario by composing two existing operator fixtures with a bounded trace oracle |
| Created | 2026-11-06 02:30 Asia/Shanghai |
| Updated | 2026-11-06 02:30 Asia/Shanghai |
| Owner | Tianji Process Server acceptance fixtures |
| Repository | tianji-service |
| Scope | Eight PS test files and three review documents |
| Change Surface | Separate replay manifest suite, exact relay header and collector witness |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | [Master §17.2 S12, §19 T4-02/T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | Service c07b8eddbb8459c04c7fd26953933a02ef0b347c |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Delivered S12 direct receive Link](./2026-11-06-00-37-s12-receive-link-evidence.md) |
| Related Plans | [S12 replay plan](../plan/2026-11-06-02-31-s12-receive-link-replay.md) |

## 1. Summary

增加独立的 S12 重放操作者用例。既有故障 relay 先丢弃已提交 PS Inbox 的 202 响应，AS 持久 Outbox 等待重试。替代 AS 进程重发相同事件后，collector 要把两次真实 PS Receive Link 分别连到首发和替代进程的交付 Span，并回溯到同一条持久入队载体。现有业务、恢复及直接 Link 用例保持各自证据边界。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [S12 direct receive evidence](../evidence/2026-11-06-s12-receive-link-evidence.md) | 原 S12a 从 AS 直接 POST 到 PS，按两次正常运行核对六条 Span。 |
| EVD-002 | [WorkerRecoveryIT](../../../stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java) | earlyResultAndAgentRestart 已覆盖真实 early Inbox、已提交 202 丢失、持久重试、AS 替代、同事件和单次业务推进。 |
| EVD-003 | [ExpenseAcceptanceFaultRelay](../../../stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java) | 现有 RESULT relay 精确匹配路由，转发白名单不含诊断 Link header，普通未匹配请求可直接通过。 |
| EVD-004 | [S12ReceiveLinkCollectorEvidence](../../../stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java) | 现有 reader 固定 SDK、关闭敏感属性、验证六条正常 Span，不能直接接收重放的额外交付尝试。 |
| EVD-005 | [Existing private Link carrier](../../../stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentResultTraceLinkCarrier.java) | 现有生产载体为精确路径与单一已采样格式。复用常量和验证方法。 |
| EVD-006 | [Existing operator preparation](../evidence/2026-11-06-s12-receive-link-operator.md) | 固定 privateRoot、三存储、可执行包、测试类树和 collector 原始证据。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extend existing acceptance test sources only. No project generation, scaffold or business layer change.

## 3. Goals and Non-goals

证明一次 owned 结果首发提交但确认丢失后，AS 进程替代和同一结果重发产生两条确证 Receive Link，并且 Process 仅推进一次。全链路查询、Worker Link、指标、真实供应商和生产重试运行需独立验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS private acceptance fixture | Affected | EVD-002/003/006 | Replay-only manifest/transport/fault choreography | Detailed fixture, bounds, oracle and validation | §7, §8, §10, §13, §14, §15, §16 |
| Collector reader | Affected | EVD-001/004 | Separate replay predicate reusing closed parser | Exact source/delivery/receive witness and time scope | §7, §8, §10, §14 |
| Production Link wire and store | Context-only | EVD-001/005 | Existing carrier, owner boundary and Outbox/Inbox reused | Inspect current contract only | §9, §11 |
| Web and IAM | Unchanged | EVD-002/006 | Existing test profiles and roles | Scope boundary | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Compose exact lost-202 and restarted AS replay in a separate private suite | Must | One owned fixture Run, first exact 202 dropped after Inbox commit, AS replaces with distinct PID/start, same event/fingerprint resent after persisted retry, one applied Inbox and native advancement, durable original enqueue remains. | EVD-002, S12 |
| REQ-002 | Carry diagnostic header only on two authorized test RESULT phases | Must | Replay-only relay requires one valid carrier on exact loopback HTTPS POST path and closed first/drop and replacement phases. Unmatched, reused, other fault phases or malformed values cannot create successful RESULT forwarding. Existing suites, other relay routes and production transport contract remain. | EVD-003/005 |
| REQ-003 | Witness exact collector graph and replacement generation | Must | One owned durable enqueue, exact relay-carrier-linked first and replacement deliveries, two real Receive Links, separate trace IDs and no parent substitution. First delivery precedes replacement start; replay delivery begins at/after that start. Bounded nonforwarded attempts may add only AS deliver spans linked to the same enqueue. | EVD-001/002/004 |
| REQ-004 | Keep source, package and E evidence independent | Must | Actual typed RED, finite GREEN/package verification, new E class compiled outside production archive, no E report during source work, exact 11 paths and existing 28 SQL. Operator holds separate raw collector and XML proofs. | EVD-001/006, T4-05 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify exact finite and package evidence | Offline source reactor and compiled test inspection |
| ACTOR-002 | Isolated acceptance operator | Verify replay under actual collector and independent AS/PS children | Separate private suite manifest, artifact locks and explicit environment |
| ACTOR-003 | Private fixture owner and test relay | Correlate one owned Run and two exact forwards | Existing two-tenant fixture with one selected owner and exact RESULT POST |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source safety and compatibility | ACTOR-001 | Maven, JUnit and packages | Exact source selectors | Reviewed baseline and frozen A/B | Finite RED/GREEN and actual package boundary | Wrong test, scope or report fails | E1 pending | REQ-002/003/004 | Test-only relay and parser | TEST-001/002/003 |
| UC-002 | Replay one committed result after AS replacement | ACTOR-002 | ACTOR-003, isolated AS/PS, collector | Exact E1 | New replay suite and fresh owner-private fixture | Two exact PS receives linked to same durable enqueue and one Process advance | Scope/time/link/receipt/digest failure rejects proof | Raw operator evidence remains private | REQ-001/002/003/004 | Existing result POST | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Separate private property `tianji.s12.receive-link-replay.manifest` and suite `RECEIVE_LINK_REPLAY`. Reuse existing operator procedure for two synthetic tenant authorities, three fresh isolated stores, real AS/PS archives and test trees, exact local TLS routes and pinned collector. The new case exercises only the first configured owner. The ordinary manifest property and direct S12a property retain exact suites. Both trace suites use existing private tracing setup and are excluded from ordinary Expense evidence publication. No new production headers, transport routes, dependencies, SQL, config or authority.

The collector evidence uses DB-persisted enqueue carrier and exact relay-captured wire carriers. Per scope, blocked old generation attempts can emit AS deliver spans before no PS delivery. The reader allows at most eight total delivery spans attached to the one enqueue while requiring exactly two PS receives. The existing six-span direct predicate stays strict.

## 6. Project Technology Context

Existing Java 25 PS test fixture uses JUnit, JDK HttpClient/HTTPS relay, Jackson, actual AS/PS test mains, existing OpenTelemetry SDK/collector pin and PostgreSQL/Worker fixtures. The new source is confined to the existing PS acceptance test package and current production contracts.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-002/004 | Exact test owner and semantic names | Test files | TEST-002/003 | PASS |
| Rule 2 | Yes | EVD-003/005/006 | Exact route/suite, one valid header, owned phases and bounded collector validation | Test-only input | TEST-002/004 | PASS |
| Rule 3 | No | EVD-003/004 | Existing immutable DTOs and private test evidence | No business model | Scope | N/A |
| Rule 4 | No | EVD-006 | Existing fixture construction | No new bean or logger | Scope | N/A |
| Rule 5 | Yes | EVD-002/003 | Existing JDK/JUnit and fixture capabilities | Test only | TEST-002/003 | PASS |
| Rule 6 | Yes | EVD-004 | Reuse current closed Jackson parser and existing synthetic span shapes | Collector test reader | TEST-001/002/004 | PASS |
| Rule 7 | No | EVD-006 | Private test-only manifest mode | No runtime configuration change | Scope | N/A |
| Rule 9 | No | EVD-002/006 | Direct fixture reuse for one E method | No production design pattern | Scope | N/A |
| Rule 10 | Yes | EVD-002/004 | Actual child/span UTC instants and monotonic read bounds | Test only | TEST-002/004 | PASS |
| Rule 11 | Yes | EVD-002/005 | Existing AS/PS ownership and CQE contract | No production change | Scope | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ChildIdentityDTO.startedAt and OTLP span start | Occurred instant | java.time.Instant | UTC instant | Internal | Existing process/collector UTC clock | No display change | Original before replacement start; replay at or after start | Existing process and OTLP timestamps | No production field change | TEST-002/004 |
| TIME-002 | Collector read bound and existing recovery wait | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | Collector polling bounded to five seconds after closing children | Existing monotonic nanos | Private fixture only | TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Separate replay test and suite | Add | REQ-001/004 | Existing direct and recovery tests separately | Neither proves exact collector links across the same result replay | One opt-in E test and one private suite enum | Add |
| Exact relay forwarding and private link witness | Add | REQ-002/003 | Existing unchanged forwarding | Current test whitelist strips diagnostic header | One fixture boolean and phase-local witness only | Add |
| Strict replay collector predicate | Add | REQ-003/004 | Reuse six-span direct result predicate alone | Replay needs one enqueue, two exact forwards and blocked delivery handling | Reuse parser with separate bounded replay cardinality | Add |
| Existing recovery choreography and production transport | Keep | REQ-001/002 | New fault relay or production replay logic | Existing recovery helper and exact approved header suffice | No production changes | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Source gates | None | Finite request construction, stub forwarding and collector parser | Existing test source | Cannot establish actual E | Scoped regression and artifact proof |
| Replay E1 | Existing private loopback TLS and collector only | One fixture Run, first-drop and replacement windows | Existing Outbox/Inbox/terminal paths | Reject old unbound requests; verify actual replacement timing and exact receipt | Joined restart and two actual Receive Link evidence |

### 7.1 System Architecture Design

Use current Driver, WorkerRecoveryIT helper, ExpenseAcceptanceFaultRelay, existing fixture startup tracing and pinned collector. New private suite routes AS RESULT through the existing exact test relay. Ordinary recovery/expense keeps its prior manifest/launch and forwarding. Direct RECEIVE_LINK keeps the direct PS route and existing six-span predicate.

### 7.2 High-Level Design

Arm existing linked-submit hold and committed-result loss. First valid authorized RESULT POST forwards exactly once to PS and its 202 is dropped after durable receipt. Capture the exact accepted wire carrier in that window. Before releasing linked response, arm replacement phase for exact event and fingerprint; reject unbound requests in the transition. Observe Outbox retry, replace AS with distinct child identity, forward the valid replacement request once and capture its carrier. Wait for existing same-event APPLIED, one result advance and successful Outbox. Read the matching durable enqueue carrier after that. Close children, verify collector graph and summarize only approved evidence digests.

### 7.3 Detailed Design

The replay boolean comes only from private suite selection in the operator relay. Match existing POST and exact path and validate one carrier with AgentResultTraceLinkCarrier; relay upstream in this mode must be exact loopback HTTPS origin. For replay RESULT allow only the first committed-drop and observed replacement phases, once each. Reject any unbound RESULT phase or unmatched result window; other directions never forward the header. Store phase carrier only after exact receipt and durable Inbox verification. Keep it inside test memory and off the published marker.

Reuse existing parser restrictions on structure, unique trace/span IDs, time, no raw attributes and link fields. Exact persisted enqueue determines source span. Exact validated lost/replay wire carriers determine two delivery keys; both link to enqueue. First delivery starts before replacement child, replay delivery starts at/after it, fencing a queued old request. Two Receive spans link to the corresponding distinct delivery, with no delivery-parent substitution. Any additional AS delivery must link to the same enqueue, remain within eight delivery attempts total and have no unmatched Receive. Any additional enqueue, receive, secret field or graph mismatch rejects evidence. An incomplete collector file may be polled within the existing bound. Existing S12a retains six distinct spans and two normal owned sources.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java` |
| Create | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkReplayAcceptanceIT.java` |
| Create | `docs/egon/spec/2026-11-06-02-30-s12-receive-link-replay.md` |
| Create | `docs/egon/plan/2026-11-06-02-31-s12-receive-link-replay.md` |
| Create | `docs/egon/evidence/2026-11-06-s12-receive-link-replay.md` |

## 9. Interface Definitions

Context-only. Existing `POST /api/tianji/v1/agent-result-inbox` and `X-Egon-Tianji-Agent-Result-Link` production format remain. The exact replay use stays private to the test relay and its new suite. No production resource, permission, OpenAPI or SDK changes.

## 10. POJO and Data Model Design

Existing Window holds one additional private wire link fact after accepted receipt and durable Inbox witness. Existing observed span retains parsed start Instant for the replay generation check. Existing DTOs, existing real events and durable rows remain authoritative. No new persistence or external DTO.

## 11. Database Design

Context-only. Existing T3-12 private V1–V14 Agent, PS and Worker stores. The test reads the existing owner-bound Inbox and exact successful terminal Outbox carrier. Existing recovery helper verifies persisted retry and same event/fingerprint and native result behavior. No migration or query boundary changes outside test source.

## 12. Frontend Page Design

Unchanged. This is an independent fixture acceptance case.

## 13. Design Patterns and Architecture Principles

Compose existing recovery and direct-link fixture capabilities in a separate explicit mode. A distinct test predicate protects strict direct S12a cardinality. The alternative of a new test relay or runtime header policy adds unnecessary network and authority scope.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Typed A replay predicate returns false, new exact finite positive assertion gives one expected failure. |
| TEST-002 | G1/P1 finite exact Harness, CollectorEvidence and Telemetry suites. Verify route/header construction, old mode header stripping, replay-only committed phases and transition window, bounded graph and exact generation. |
| TEST-003 | Actual package/source checks and compiled new E method, prior entry points and no E report. Both AS/PS actual packages contain no test fixture or collector YAML additions. |
| TEST-004 | Independent exact E1 with isolated AS/PS, Worker fixture, PostgreSQL and pinned collector. Require one unskipped successful test plus private artifact/collector proof. |

## 15. Non-functional and Cross-cutting Design

Use existing manifest limits, existing bounded relay/child operations and private collector input. Published marker contains only digests and expected receive count. Operator stops collector after test, retains final unmodified JSONL and checks its digest equals JUnit output.

## 16. Compatibility, Migration, Rollout, and Rollback

The new mode has a separate private manifest and selection. Existing BUSINESS/RECOVERY/direct RECEIVE_LINK source behavior, authority and evidence formats remain. Revert the focused test-only commit to remove the mode and fixture.

## 17. Alternatives and Decisions

Keep existing independent direct link and replay tests intact. A second replay oracle inside the current test harness is the smallest way to join both observations. No production service or relay expansion follows.

## 18. Risks and Open Questions

Actual operator E1 requires separate private stores, TLS, collector and explicit startup authorization. An absent or skipped report leaves live replay pending. Worker links, logs, metrics, cross-owner correlation and complete S12 need separate evidence.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §5/7/11/14 | TEST-003/004 |
| REQ-002 | UC-001/002 | §5/7/9/10 | TEST-002/003/004 |
| REQ-003 | UC-001/002 | §6.3/7/10/14 | TEST-001/002/004 |
| REQ-004 | UC-001/002 | §8/14/16 | TEST-001/002/003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing PS acceptance fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing recovery flow, relay, SDK and collector reader | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | JDK, JUnit, existing HTTPS test fixture and pinned SDK already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Exact private suite, validated header, phase/owner checks and collector evidence | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Spring tests and JUnit | None |
| MC-JSON-001 | Applicable | PASS | §7.3/10 | Reuse closed bounded collector parser | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing process start and span UTC instants, bounded elapsed waits | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Eleven exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite sources and independent E evidence | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gates independent of later E1 | None |

### 20.6 Final verdict

PASS — Ready for user review
