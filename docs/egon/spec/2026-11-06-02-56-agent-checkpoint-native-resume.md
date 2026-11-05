# T4-03 V16 产品原生续跑探针

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-56-agent-checkpoint-native-resume.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One fixed V16 operator scenario reusing bounded test JVMs and adding one closed continuation phase |
| Created | 2026-11-06 02:56 Asia/Shanghai |
| Updated | 2026-11-06 02:56 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three AR test files and their three review documents |
| Change Surface | Existing opt-in V16 fixture, compatible phase frame and finite protocol checks |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | [Master §17.2 S11 and §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service 8126c32db1f303f858cbc317cf6db0db4d7ab1f1; Components 6d26bf8724e8993b631db68c95b7238d7a93fb8c |
| Amends | None |
| Supersedes | None |
| Depends On | [Explicit Agent Flow bridge §7/§14](../../../../Stellar-Enclosure-Components/docs/egon/spec/2026-11-06-02-33-agent-flow-explicit-resume.md) |
| Related Specs | [V16 process death and reconciliation](./2026-11-06-02-11-agent-checkpoint-product-fork.md), [Delivered V16 capture](./2026-11-06-01-42-agent-checkpoint-capture.md), [Earlier V9 native Session fork](./2026-11-06-00-31-adk-durable-resume-fork.md) |
| Related Plans | [V16 product native resume plan](../plan/2026-11-06-02-57-agent-checkpoint-native-resume.md) |

## 1. Summary

为 V16 增加独立操作者用例。普通产品执行器在原生首条输入及 checkpoint 已提交、预算尚未预留时停在估算器。父测试强制终止并回收该 JVM，只使选中的原 Session 租约过期。替代 JVM 核对原 Run 活租约和持久截点，取得后继 Session 租约，并经 Components 显式入口沿同一 invocation 续跑。两种 codec 都检查单次 Fake、原始持久前缀、唯一用户输入和已完成后的重复续跑。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Product process-death source evidence](../evidence/2026-11-06-agent-checkpoint-product-fork.md) | 原产品 checkpoint 探针在持久化后强制退出，对选中 Run 注入过期并用独立 JVM 完成对账。 |
| EVD-002 | [AgentCheckpointCapturePostgresTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java) | 既有隔离主库、角色分离、V15→V16、两种 codec、预算前见证、强制退出、进程回收及独立 reader 分支。 |
| EVD-003 | [AgentCheckpointForkHandoff](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java) | 格式 1 私有 512 字节帧和严格 URL、角色、codec、UUID、摘要检查。现有 capture 与 reconcile 使用 0/1。 |
| EVD-004 | [Agent Flow explicit resume evidence](../../../../Stellar-Enclosure-Components/docs/egon/evidence/2026-11-06-agent-flow-explicit-resume.md) | withExplicitResume 已在锁定 ADK 1.13.0 的内存 Session 中续跑普通构造写入的 invocation。 |
| EVD-005 | [V9 independent fork](./2026-11-06-00-31-adk-durable-resume-fork.md) | 已有 ADK App 来源的早期 V9 持久 Session 探针，验证跨 Session 租约而保留原 Run 租约。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR integration test and its private test protocol. No generated example or project scaffolding.

## 3. Goals and Non-goals

补齐普通产品 V16 输入与已提交 Components 显式续跑入口之间的真实 PostgreSQL 验收路径。该路径观察既有 Run 活租约下的 Session 续跑。产品当前授权、过期 Run 接管、产品结果和完整 S11 另有设计或验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Existing V16 PG fixture | Affected | EVD-001/002 | 独立 native-resume 方法复用普通捕获和进程清理。旧捕获、对账和 reader 分支保留。 | Exact roles, Run fence, Session expiry CAS and evidence | §7, §8, §14, §15, §16 |
| Private stdin handoff | Affected | EVD-003 | 阶段 2 绑定摘要。旧 0/1 字节保持兼容。 | Type, validation and bounded framing | §7, §8, §10, §13, §14 |
| Production AR and Components | Context-only | EVD-001/004/005 | 复用已有 V16 writer、Session store 和显式 Runner 入口。 | Exact caller and boundary | §9/11 |
| AS/PS/Web | Unchanged | EVD-001 | 配置、权限、控制台和部署保持原范围。 | Boundary only | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Witness the ordinary product cut across process death and preserve the live Run | Must | Both codecs use the existing real CONFIG executor. The parent verifies the committed V16 cut before and after killing and reaping A. Exact CAS expires only the original Session lease while the original Run lease remains live. | EVD-001/002, S11 |
| REQ-002 | Continue the exact persisted native invocation under a successor Session lease | Must | B confirms the original fence, acquires the next Session epoch under a distinct Session owner, and runs the existing explicit bridge with the recorded invocation and no new input. Exactly one Fake call and one original user event, terminal native output, no extra model work or durable writes on repeat. Parent independently checks original Run lease and checkpoint, raw first event, native history and no product budget, tool or result. | EVD-002/004/005, S11 |
| REQ-003 | Bind the native test phase and guard existing operator modes | Must | Format 1 phase codes remain 0/1 for capture/reconcile and add 2 for native resume. The native digest binds the captured cut plus exact original Run owner and expiry. Exact private opt-ins, bounded canonical frame and child lifecycle are enforced. Existing fixture modes retain their behavior. | EVD-002/003 |
| REQ-004 | Separate finite source checks from operator proof | Must | One intended typed RED, fourteen finite cases and compiled selector and guards. Six changed paths and 28 production SQL byte-identical. Actual PostgreSQL evidence is pending unless the operator runs I1. | T4-03/T4-05 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify finite handoff protocol and actual compiled gate | Offline reactor and JDK 25 |
| ACTOR-002 | Isolated acceptance operator | Run exact process-death method | Authorized disposable loopback PG and POSIX workspace |
| ACTOR-003 | Test parent and independent test children | Exercise only their own fixture Run and cut | Existing split setup/observer and restricted runtime roles |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source and compiled operator gates | ACTOR-001 | Maven, JUnit and JDK | Exact source Step | Locked sources and repositories | RED, finite regressions and class-only inspection | Missing, unrelated or skipped report fails | I1 pending | REQ-003/004 | Private frame | TEST-001/002/003 |
| UC-002 | Observe the ordinary product cut across process death | ACTOR-002 | ACTOR-003, Runner and PostgreSQL | Exact I1 selector | Fresh scoped primary and role identities | Parent verifies matching original child witness before and after exit | Different cut, bad marker or timeout fails | Exact original live Run and prior Session lease identified | REQ-001/003 | Product fixture | TEST-004 |
| UC-003 | Continue native Session history from the same invocation | ACTOR-002 | ACTOR-003, PostgreSQL and explicit bridge | Original Session lease expired by exact fixture CAS | Original Run remains live and unchanged | Next Session epoch, exact continuation, one Fake, stable repeat and unchanged product ledger | Wrong scope, expired Run, changed digest or extra write fails | Released successor Session lease and private witness | REQ-002/003 | Existing explicit Runner | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Operator uses the existing guarded disposable loopback primary named tianji_it_checkpoint_<name>, no pre-existing tianji_agent schema, an exact preinstalled public vector patch and existing distinct setup/runtime roles. Both logins need local fixture-only authentication under an empty child environment. Use a new isolated database per method and only the exact selector. The child verifies actual database and role, loopback primary, UTF8/origin/read-write, vector, exact V1–V16 history and readiness.

The fixture's total Run deadline is 180 seconds. The selected JUnit method has a 150-second cap, each child wait is bounded to 16 seconds and child A estimator parking has a 32-second cap. Original product Run lease uses the executor's 30-second duration and stays live through the entire native continuation and final verification. Its exact expiry must remain equal. Expire only the original Session lease after the capture child has been reaped. If either original lease no longer satisfies its guarded boundary, fail I1.

## 6. Project Technology Context

Existing AR infrastructure persistence tests run on Java 25, ADK Java 1.13.0, Spring AI, JUnit Jupiter, Spring JDBC and Flyway. The existing Components dependency from the locked source reactor exposes the explicit bridge. This modifies only the existing test package and retains the current three-layer scope, production actor boundaries and persistence owner.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-002/005 | Exact private helper and semantic test names | Test only | TEST-001/002/003 | PASS |
| Rule 2 | Yes | EVD-002/005 | Fixed local URL/role/UUID/digest framing and actual database guards | Test-only input | TEST-002/004 | PASS |
| Rule 3 | Yes | EVD-005 | Immutable record for private Request and ForkCut, redacted textual representation | Existing test pattern | TEST-002/003 | PASS |
| Rule 4 | No | EVD-002 | Existing runtime construction and test assertions | No new business bean or log | Scope | N/A |
| Rule 5 | Yes | EVD-002/005 | Existing JDK, JDBC, JUnit and ADK | Test only | TEST-002/003 | PASS |
| Rule 6 | No | EVD-002 | Existing typed ledger, private binary handoff and exact read-only digest witness | No new JSON wire or enum persistence | Scope | N/A |
| Rule 7 | No | EVD-002 | Test opt-ins and private child switch | No runtime configuration | Scope | N/A |
| Rule 9 | No | EVD-002/005 | Direct orchestration of one fixture | No new production design pattern | Scope | N/A |
| Rule 10 | Yes | EVD-002/004/005 | Database instants for due checks and bounded monotonic wait | Test only | TEST-003/004 | PASS |
| Rule 11 | Yes | EVD-001/004 | Existing owning Agent DB and AR test scope | No new schema or CQE contract | Scope | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Original Run lease, deadline and captured_at | Occurred instant | java.time.Instant / database timestamptz | UTC instant | Internal | Existing primary database clock | No display change | Original Run lease remains live and unchanged. Only the exact original live Session lease is expired. | PostgreSQL clock_timestamp(), fixture compares exact database evidence | Existing database and writer contracts | TEST-004 |
| TIME-002 | 16-second child waits and 32-second estimator cap | Duration | java.time.Duration and monotonic System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | Reap each owned child within finite wait | JDK monotonic nanoseconds and bounded JUnit test | Existing test-only timeout | TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Independent native-resume branch of existing V16 test | Modify | REQ-001/002/004 | V16 process fork and V9 continuation | V16 reconciliation deliberately expires the Run. V9 starts with an ADK App instead of the ordinary V16 producer. | One isolated method with same two bounded subprocesses and one guarded Session-only fixture fault | Modify |
| Existing private protocol and finite tests | Modify | REQ-003/004 | Reuse 0/1 as-is | Native continuation requires an unambiguous phase with a live Run fence witness | Phase 2 and required bound digest. Existing role and byte limits retained. | Modify |
| Component bridge, persistence and ledger | Keep | REQ-001/002 | New service, permission or scheduler | Existing AgentFlowSessionRunner and Session store already support the scoped native path | No new production dependency, migration or runtime path | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Source gates | None | Finite tests and bytecode inspection | Existing reactor and test ownership | Class evidence cannot establish actual PG runtime | Reproducible source evidence |
| Operator I1 | Isolated loopback PostgreSQL only | Parent, capture JVM, replacement JVM | V16 cut and original Run ledger, successor Session only | Recheck captured digest, lease before action, and raw history after repeat | Actual ordinary-origin V16 native continuation witness |

### 7.1 System Architecture Design

Parent owns fixture and POSIX marker directory. A follows the ordinary product V16 CONFIG path with the existing opted-in capture writer and test-only authorization and model ports. B reads the exact owned checkpoint through fixture observer evidence, validates its live original Run lease and invokes Components' explicit continuation with a successor Session under the existing restricted runtime role. The parent verifies evidence before and after B independently.

### 7.2 High-Level Design

For both codec modes, start A and await its pre-reservation marker after the committed checkpoint and first native input. Verify zero product reservation, tool or result evidence and the original persisted cut. Kill and reap A, compare the same cut and snapshot the exact live Run lease including expiry. With a test-only owner/run/checkpoint/epoch/expiry CAS, expire only the original Session lease. B independently validates role/history, cut and digest of the original Run owner and expiry before acquisition. It acquires the next Session epoch, compiles the same persisted CONFIG leaf, uses the recorded invocation through withExplicitResume, checks one Fake stream, durable output and completed retry stability, and releases the replacement Session. B and parent verify unchanged Run lease and checkpoint, unchanged raw first event and one native user input.

### 7.3 Detailed Design

The method `resumes_product_first_input_checkpoint_after_process_death_under_original_run_lease` requires class flag `tianji.postgres.checkpoint.capture=true` and method flag `tianji.postgres.checkpoint.capture.native-resume=true`. The child entry requires `tianji.postgres.checkpoint.capture.fork.child=true`. Existing capture, reconciliation and observation modes stay independent. Phase-specific marker names identify capture, reconcile or resume in the same canonical mode-0700 private directory. Parent validates stage, PID and digest, reaps every owned child and removes private markers. Child argv excludes database identity, roles, raw input and credentials. Environment is cleared except prompt capture false, telemetry exporters are disabled and output is discarded.

Native phase digest combines the existing committed cut SHA-256 with the length-bound original Run lease owner and exact expiry instant. Existing cut digest binds checkpoint, raw first event and Session state/watermarks and prior Session lease facts. B checks the native digest before acquisition, verifies replacement Session fence inside its Fake stream, requires one original input in the prompt, one Fake stream and no synchronous Fake call, and confirms completed retry adds no output, model work or raw history. After release, both JVMs check original live Run fence and unchanged checkpoint, raw first event, one original user event, exact invocation, terminal native output, and zero product budget, tool invocation, tool intent and result evidence.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-56-agent-checkpoint-native-resume.md` |
| Create | `docs/egon/plan/2026-11-06-02-57-agent-checkpoint-native-resume.md` |
| Create | `docs/egon/evidence/2026-11-06-agent-checkpoint-native-resume.md` |

## 9. Interface Definitions

Existing test-only command selectors and private child entry are affected. The separate native-resume selector and flags are defined in §7.3 and the bounded private frame in §10. Existing production interface, claim and authorization contracts are context only.

## 10. POJO and Data Model Design

Request keeps format 1 and a 512-byte cap. Canonical loopback URL, setup and distinct runtime role, exact vector version, typed-codec boolean and UUID remain encoded with existing bounded fields. The phase byte is fixed to 0 CAPTURE, 1 RECONCILE or 2 NATIVE_RESUME, with no digest for CAPTURE and one lowercase 64-hex digest for each replacement phase. Reject malformed, oversize, truncated, trailing, wrong-format or unknown-phase input before connecting. NativeForkWitness, NativeRow and SessionFence are private redacted test records. Raw native rows stay inside the disposable fixture, and the handoff carries only the bound SHA-256.

## 11. Database Design

The V16 operator fixture reuses exact V1–V15 plus V16 and its existing V16 script and CRC check. Database mutation for the native branch is limited to the guarded expiry update of the selected Session lease, ordinary successor Session acquisition and release, and native Session append. Existing scoped runtime permissions provide those operations. Reuse exact checkpoint, Run, Session and raw event reads and the owned V13 invocation and intent absence checks. Production DDL and ownership are context only.

## 12. Frontend Page Design

Unchanged. The test exercises no Web, HTTP listener, process orchestration or deployment.

## 13. Design Patterns and Architecture Principles

Use the existing private fork harness and observer evidence. Extend its explicit phase for native continuation and reuse the guarded Component adapter, durable Session store and production capture branch. A new test harness or production adapter adds no evidence for this isolated case.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Typed RED selects AgentCheckpointForkHandoffTest#round_trips_native_resume_as_a_distinct_digest_bound_phase. Encode the native phase with old reconcile byte 1 and require expected NATIVE_RESUME versus actual RECONCILE, one assertion failure with zero errors/skips. |
| TEST-002 | AgentCheckpointForkHandoffTest 5, AdkResumeProbeHandoffTest 4 and AdkCheckpointCaptureTest 5. Validate exact phase bytes and digest requirement, malformed framing, old modes and adjacent capture behavior. |
| TEST-003 | JDK 25 javap the actual compiled fixture for the exact new method, old operator modes, both parent flags, private child switch and main. Require absent PostgreSQL report and 28 previous migration bytes equal. |
| TEST-004 | Authorized operator-only exact method with both codecs. Require one executed passing report for the original live Run fence, successor Session, exact same invocation, immutable prefix and checkpoint, one Fake and stable completed repeat. |

## 15. Non-functional and Cross-cutting Design

Use existing timeout/provisioning controls, bounded pipe/marker reads and owned-child cleanup. Do not retain credentials, raw input or event content in published evidence. A new operator attempt uses a fresh authorized database. Restrict inspection and fault injection to the exact fixture identity and lease.

## 16. Compatibility, Migration, Rollout, and Rollback

Three existing AR test files and three review documents form the focused commit. The original V16 capture and rollback, product fork/reconciliation and reader observer methods retain their exact selectors and role behavior. Old phase bytes 0/1 and rejection behavior stay valid. Revert one source commit to remove this operator branch.

## 17. Alternatives and Decisions

V9 proves a different producer and the Components finite case uses memory storage. The existing V16 reconciliation fixture expires the Run and confirms suspension. Reuse its original process-death cut with a separate Session-only branch so native continuation can be verified without introducing a product Run claim.

## 18. Risks and Open Questions

Actual PostgreSQL role, process death and native Session continuation require I1. Missing or expired original fences, unexpected child exits, malformed evidence or timeouts fail the operator run. Product current authority, transactional takeover and full S11 require separate design and acceptance.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §7/11/14 | TEST-003/004 |
| REQ-002 | UC-003 | §7/9/11/14 | TEST-003/004 |
| REQ-003 | UC-001/002/003 | §5/7/10/13/15 | TEST-001/002/003/004 |
| REQ-004 | UC-001 | §8/14/16 | TEST-001/002/003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR persistence test owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing V16 process fork, Session store and explicit native Runner | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Reactor, JUnit, Spring AI and PG fixture already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Bounded handoff, guarded database roles and exact persisted witness | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable private request and witness values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Reactor and JUnit | None |
| MC-JSON-001 | Not applicable | N/A | §7.3/10 | Existing typed events and payload maps | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB clock and monotonic child wait | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Independent test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct reuse of existing test-owned children and Session adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Six exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 20.6 Final verdict

PASS — Ready for user review
