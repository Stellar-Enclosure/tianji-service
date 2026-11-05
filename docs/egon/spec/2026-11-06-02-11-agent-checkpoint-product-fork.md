# T4-03 产品 checkpoint 强制退出探针

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-11-agent-checkpoint-product-fork.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One fixed operator probe extending the existing V16 fixture with two bounded test JVM roles |
| Created | 2026-11-06 02:11 Asia/Shanghai |
| Updated | 2026-11-06 02:11 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three AR test files and their three review documents |
| Change Surface | Existing opt-in V16 fixture, bounded private handoff and finite protocol checks |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | [Master §17.2 S11 and §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service 7013768227b778ce762ca18f72c4cb24b5facd3f |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Delivered V16 capture](./2026-11-06-01-42-agent-checkpoint-capture.md), [Earlier V9 native Session fork](./2026-11-06-00-31-adk-durable-resume-fork.md) |
| Related Plans | [Product checkpoint fork plan](../plan/2026-11-06-02-12-agent-checkpoint-product-fork.md) |

## 1. Summary

为 V16 产品执行器增加一个操作者专用的双 JVM 强制退出探针。第一个子进程经过真实 CONFIG Runner，在原生输入及 checkpoint 已提交、模型预算尚未预留时停在估算器。父测试核对持久证据后强制终止它，对同一 Run 注入租约到期。第二个子进程重新读取同一 checkpoint，通过既有对账入口得到 SUSPENDED，验证未开始模型或工具且持久截点未变化。实际 PostgreSQL 和子进程只属于独立 I1。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [V16 writer source evidence](../evidence/2026-11-06-agent-checkpoint-capture.md) | 首条输入写入与 Session 原生事件共事务，默认关闭，现有 I 测试已编译。 |
| EVD-002 | [AgentCheckpointCapturePostgresTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java) | 原有隔离主库 gate、V15→V16、分离 runtime role、两种 codec 和估算器见证。已有方法验证同进程写入和 INSERT 失败回滚。 |
| EVD-003 | [AdkAgentRunExecutor](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java) | 当前 forRuntime 构建产品 Runner，使用已获授权的单 CONFIG Run。 |
| EVD-004 | [AgentLedgerService](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java) | reconcileDue(1) 读取到期候选并在到期租约分支将当前 Run 置为 SUSPENDED，追加 LEASE_EXPIRED。 |
| EVD-005 | [V9 independent fork](./2026-11-06-00-31-adk-durable-resume-fork.md) | 更早的 ADK 语义探针保留有效原 Run lease 并续跑原生 invocation，提供有界 pipe 与子进程回收模式。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR integration test and its private test protocol. No generated example or project scaffolding.

## 3. Goals and Non-goals

验证正常产品执行器写入的 V16 checkpoint 在原 JVM 强制退出后保持持久，并证明现有租约到期对账仅给出悬挂处置。产品 Run 接管、授权重办、恢复命令、原生续跑、全量 S11 和部署均有各自验收入口。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Existing V16 PG fixture | Affected | EVD-001/002 | New double-opt-in fork method and test-only child entry | Timeline, fault, safeguards and evidence | §7, §8, §14, §15, §16 |
| Private stdin handoff | Affected | EVD-005 | Bounded fixed test protocol and finite negative cases | Type, validation and lifecycle | §7, §8, §10, §13, §14 |
| Production AR runtime and ledger | Context-only | EVD-003/004 | Existing writer, lease and reconciler used | Exact caller and preserved boundaries | §9/11 |
| AS/PS/Web | Unchanged | EVD-001 | Product configuration, transports and authorization stay in prior scope | Boundary only | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Witness the real product checkpoint across forced process exit | Must | Both codec modes start from isolated owned Run. In child A, normal CONFIG product executor reaches the pre-reservation estimator after committed original Session event and V16 row. Parent independently verifies the exact digest and zero execution evidence, kills/reaps A and verifies the same cut. | EVD-001–003, S11 |
| REQ-002 | Reconcile the exact expired Run once without execution | Must | Test CAS expires only the selected still-live Run lease. Independent child B verifies role/schema/cut and the only due Run. reconcileDue(1) commits one SUSPENDED/LEASE_EXPIRED event, advances epoch, revision and watermark once, clears lease, keeps attempt one, and leaves checkpoint, native history and no-model/tool/result facts unchanged. Parent independently rechecks. | EVD-002/004, S11 |
| REQ-003 | Bound and protect test process handoff | Must | Explicit parent and private child gates, guarded loopback name and distinct roles, bounded canonical stdin and private marker, no raw credential or fixture input in argv/stdout. Invalid framing is rejected before connecting. Owned children are always reaped within bounds. | EVD-002/005 |
| REQ-004 | Separate source verification from operator proof | Must | One actual finite RED followed by eighteen protocol, capture and storage cases and exact compiled method/guard verification. Six changed paths and all 28 production migrations byte-identical. Absent operator I report stays pending. | T4-03/T4-05 |

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
| UC-001 | Verify protocol source and compile gate | ACTOR-001 | Maven/JUnit/JDK | Source Step | Exact baseline | Finite checks and actual compiled bytecode | Missing/invalid or unexpected integration report fails | Operator pending | REQ-003/004 | Private stdin | TEST-001/002/003 |
| UC-002 | Observe and preserve the committed cut across death | ACTOR-002 | ACTOR-003, Runner, PostgreSQL | Exact I1 opt-in | Fresh target and verified roles | Matching original child and parent witnesses before and after force kill | Missing/mutated cut or unsafe exit fails | One exact Run lease eligible for induced expiry | REQ-001/003 | Product fixture | TEST-004 |
| UC-003 | Suspend the single expired Run in child B | ACTOR-002 | ACTOR-003, reconciliation ledger | Exact test CAS marks original Run due | Same cut and one due row | One SUSPENDED decision; no model/tool/result or altered Session | Wrong scope/epoch/revision, extra due row or witness drift fails | Private I1 result | REQ-002/003 | Existing reconcileDue | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Keep current fixture guard, existing V15→V16 migration checks and least-privilege role grants. Operator provisions a fresh loopback `tianji_it_checkpoint_<name>` database without tianji_agent, exact preinstalled public vector patch, valid separately provisioned limited runtime role and local fixture-only authentication that works for both roles without URL credentials or inherited environment. Use the exact one-method selector and a new disposable database for each attempt. The child verifies live database name, current role, loopback primary, UTF8/origin/read-write, vector version, exact migration history, V16 checksum and runtime readiness. No production activation is implied.

The 180-second synthetic Run deadline separates total deadline from injected Run lease expiry. Parent method timeout is 150 seconds, waits for each child are bounded to 16 seconds and child A is parked at most 32 seconds in its test estimator. Inject expiry only after child A is forcibly terminated and reaped. Treat unsupported POSIX private directory semantics, authentication, scope mismatch and timeout as failed operator prerequisites.

## 6. Project Technology Context

Existing AR tests run on Java 25, ADK, Spring AI, JUnit Jupiter, Spring JDBC and Flyway. The new helper is confined to test source. Keep existing production actor boundaries, data paths and project structure.

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
| TIME-001 | Original Run lease, deadline and captured_at | Occurred instant | java.time.Instant / database timestamptz | UTC instant | Internal | Existing primary database clock | No display change | Only original still-live lease is expired while total deadline remains future | PostgreSQL clock_timestamp(), fixture compares exact database evidence | Existing database and writer contracts | TEST-004 |
| TIME-002 | 16-second child waits and 32-second estimator cap | Duration | java.time.Duration and monotonic System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | Reap each owned child within finite wait | JDK monotonic nanoseconds and bounded JUnit test | Existing test-only timeout | TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Extra V16 probe method and two child roles | Add | REQ-001/002/004 | Existing same-process writer test or V9 native continuation | Neither observes product V16 cut surviving process loss and expired Run disposition | One opt-in fixture, two child roles per codec, bounded cleanup | Add |
| Private binary handoff with finite tests | Add | REQ-003/004 | Existing V9 request carries original lease and native continuation fields | V16 needs verified role/vector and cut identity without native resume fields | One bounded private request and independent finite tests | Add |
| Existing ledger and migrations | Keep | REQ-001/002 | New recovery coordinator | Current reconcileDue and V16 capture already own the behavior | No new production dependency or migration | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Source gates | None | Finite tests and bytecode inspection | Existing test ownership | Cannot supply PostgreSQL or child execution evidence | Controlled source regression |
| Operator I1 | Private loopback PostgreSQL only | Parent, capture JVM, independent reconciliation JVM | Existing V16 history and SUSPENDED Run ledger | Recheck checkpoint before/after exit and bound exact expiry CAS | Product durable cut and actual disposition witness |

### 7.1 System Architecture Design

Parent uses the existing V16 test fixture and private temporary directory. Child A uses the actual normal product executor with the explicit checkpoint option and test-only authorization/model ports. Child B calls the existing reconciliation ledger as the restricted runtime role. The observer role only witnesses fixture data and performs the exact test-only expiry CAS. Both subprocesses are owned by the selected JUnit test.

### 7.2 High-Level Design

For both native codec variants, create one fresh owned fixture Run with three-minute total deadline. Launch A and await the verified pre-reservation marker. Check that one original durable input and one V16 checkpoint exist with no reservations, model/tool or terminal evidence. Force-kill and reap A, then compare the same cut and set the existing Run lease expiry to one second before database time using exact owner, status, attempt, revision, watermark, epoch and lease owner. Launch B. It validates the original evidence and that this is the only due Run, performs bounded reconcileDue(1), and independently verifies the new single SUSPENDED status event. Parent verifies the same durable results after B exits.

### 7.3 Detailed Design

The test method `forced_process_exit_preserves_checkpoint_and_reconciles_expired_run_without_execution` uses class gate `tianji.postgres.checkpoint.capture=true` and method gate `tianji.postgres.checkpoint.capture.fork=true`. The child entry requires `tianji.postgres.checkpoint.capture.fork.child=true`. Its bounded stdin frame chooses capture or reconcile by absence or presence of the original cut digest. Marker directory must be canonical and private with mode 0700, marker name corresponds to the operation and file creation is exclusive. Parent verifies exact stage, child PID and cut digest, rejects output mismatches or child exits, and reaps its children even on failure. Child argv contains no database URL, role, raw input or secret. Child output is discarded, telemetry exporters disabled and environment cleared except explicit ADK prompt capture false.

The cut digest covers the full scoped checkpoint, native Session event and Session state/codec/revision/watermark and Session lease facts. The independent final witness requires attempt one, revision/watermark/Run epoch each advanced exactly one, old Run owner cleared, exactly one RUN_STATUS_CHANGED from RUNNING to SUSPENDED with LEASE_EXPIRED, and no new budget, tool or result evidence. Checkpoint and native history remain exactly at the captured digest. No production recovery path, alternate state machine, new permission or native resume action is part of this probe.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-11-agent-checkpoint-product-fork.md` |
| Create | `docs/egon/plan/2026-11-06-02-12-agent-checkpoint-product-fork.md` |
| Create | `docs/egon/evidence/2026-11-06-agent-checkpoint-product-fork.md` |

## 9. Interface Definitions

Context-only. Existing AgentCheckpointCapturePostgresTest fixture remains opt-in, and the same guarded product executor and AgentLedgerService#reconcileDue operate on the isolated Run. The new stdin frame is a private fixture protocol with typed fields described in §10. There is no external API/SDK change.

## 10. POJO and Data Model Design

Request is an immutable test-only record with canonical loopback URL, setup role, distinct runtime role, pinned vector version, exact typed-codec boolean, canonical fixture Run UUID and optional lowercase 64-hex cut digest. Format 1 is bounded to 512 bytes, UTF fields are length-prefixed and both flags must be exactly 0 or 1. Reject malformed, oversize, truncated, trailing or wrong-format input before a database connection. Fixed synthetic tenant selection binds each codec. The private ForkCut contains witness digest and original epoch/revision/sequence/owner and is redacted in diagnostic representation.

## 11. Database Design

Context-only. Reuse existing fixture and unchanged V1–V15 plus V16 in the isolated operator primary. Fixture validates exact successful V16 script and CRC -415542202. Existing runtime role remains INSERT-only for checkpoint, observer reads exact checkpoint, Run, Session and event identity. Only intentional data fault is existing Run lease expiry in the disposable fixture. Production SQL, schema, wiring and roles retain their existing authority.

## 12. Frontend Page Design

Unchanged. The test exercises no Web, HTTP listener, process orchestration or deployment.

## 13. Design Patterns and Architecture Principles

Use the existing operator test fixture and its normal execution entry. Keep private child orchestration close to the witness it protects. Reuse the V9 bounded pipe and process cleanup approach while defining the distinct V16 evidence frame explicitly.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | RED exact handoff existence test against baseline, one assertion wrapping ClassNotFoundException. |
| TEST-002 | Existing AdkResumeProbeHandoffTest and new AgentCheckpointForkHandoffTest, four each, plus AdkCheckpointCaptureTest and AgentCheckpointStorageVerifierTest, five each. Cover bounded framing, role/database guard, both codecs, digest and invalid inputs, plus current finite writer/storage compatibility. |
| TEST-003 | JDK 25 javap actual compiled fixture. Inspect original/new methods, parent and method opt-ins, private child gate and main, and absence of PostgreSQL report. Strict scope preserves 28 migrations. |
| TEST-004 | Separately authorized operator-only exact PostgreSQL and child JVM probe across both codecs. One executed passing report required, including matching forced-exit witness and exact suspension disposition. |

## 15. Non-functional and Cross-cutting Design

Use existing timeout/provisioning controls, bounded pipe/marker reads and owned-child cleanup. Do not retain credentials, raw input or event content in published evidence. A new operator attempt uses a fresh authorized database. Restrict inspection and fault injection to the exact fixture identity and lease.

## 16. Compatibility, Migration, Rollout, and Rollback

The source Step modifies three AR test paths and adds three documents. Existing V16 write/rollback method and production behavior are preserved. Revert one source commit to remove the new fork. Actual process and database acceptance remain operator-only.

## 17. Alternatives and Decisions

Existing same-process V16 tests verify durable write and insert-denial rollback. Existing V9 fork verifies an ADK continuation boundary under a surviving Run lease. Extend the V16 test directly for product Runner process death and exact Run suspension; a new production API or scheduler would widen the task unnecessarily.

## 18. Risks and Open Questions

Actual V16 database authentication and execution remain unverified during source delivery. An unexpected child exit, mutable witness or privilege mismatch fails I1 and should be retained as negative evidence. A future authorized recovery action and full S11 require independent proof.

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
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing V16 capture fixture, Runner and reconciliation | None |
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
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct existing test-owned children and fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Six exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 20.6 Final verdict

PASS — Ready for user review
