# T4-03 Checkpoint 观察 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-02-28-agent-checkpoint-observation-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated existing AR fixture with real product fork and explicit independent reader |
| Created | 2026-11-06 02:28 Asia/Shanghai |
| Updated | 2026-11-06 02:28 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents |
| Change Surface | Dedicated operator-only V16 reader observation proof and evidence drift cases |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 49fb8d481435ca4b4b5d58291ffd9650ed0935f5 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Checkpoint observation](./2026-11-06-02-18-agent-checkpoint-observation.md), [Product checkpoint fork](./2026-11-06-02-11-agent-checkpoint-product-fork.md) |
| Related Plans | [Observer PostgreSQL proof](../plan/2026-11-06-02-29-agent-checkpoint-observation-postgres.md) |

## 1. Summary

补齐 V16 观察器在真实 PostgreSQL 上的独立操作者用例。复用现有产品执行进程终止、第二 JVM 协调和原始持久 checkpoint 证据，在独立 SELECT-only 身份下观察两个原生 codec。确认跨 owner、工具证据列权限丢失和 Session revision 漂移会收紧结果。源码门禁只运行有限测试并检查编译产物。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Checkpoint observer](./2026-11-06-02-18-agent-checkpoint-observation.md), AgentCheckpointObservationService#observe | 已有单主库 RR 只读观察源码和有限 JDBC 测试；当前缺少真实观察者 PostgreSQL 用例 |
| EVD-002 | [Product checkpoint fork](./2026-11-06-02-11-agent-checkpoint-product-fork.md), AgentCheckpointCapturePostgresTest | 现有两 JVM 产品捕获与协调路径、两种 codec 和只接收一次性 loopback V16 库的隔离前置 |
| EVD-003 | AgentStorageVerifier#verifyCheckpointObservation/#checkpointToolEvidenceReadable | 独立 V16 reader 权限和最小 V13 scope 列是实际 PostgreSQL 观察的前置 |
| EVD-004 | AgentCheckpointObservationServiceTest, AgentCheckpointStorageVerifierTest, AgentCheckpointForkHandoffTest | 安全有限门禁，负责观察、角色校验和已有 handoff 源码 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR integration fixture. No project generation or examples are part of this change.

## 3. Goals and Non-goals

提供独立 reader 在真实 V16 PostgreSQL 上的正向观察与负向漂移证据。当前交付新增可复跑用例和有限源码证据，实际 I1 留给授权操作者。产品启动装配、生产数据库角色、恢复授权、原生 invocation 续跑与过期 Run 接管由独立步骤负责。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR PostgreSQL test fixture | Affected | EVD-001–004 | 新增一个独立 opt-in 用例和局部辅助 | 精确 role、进程复用、观察和负向断言 | §7, §8, §14, §15, §16 |
| PostgreSQL V16 and reader service | Context-only | EVD-001/003 | Existing roles, schema and query semantics | Fixture evidence only | §11 |
| Production runtime, HTTP, PaC and Web | Unchanged | EVD-001/002 | Current defaults and permissions | Explicit source boundary | §9/12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Observe the exact product first-input checkpoint as a separate reader | Must | Disposable V16 primary, existing product capture and second JVM reconciliation; exact original owner gets BOUNDARY_OBSERVED for both codecs after the Session lease is expired with the existing fence | S11, EVD-001/002/003 |
| REQ-002 | Reject owner and evidence drift | Must | Different verified USER within the same tenant gets TIANJI_RESOURCE_NOT_FOUND; removing only V13 invocation run_id column SELECT returns TOOL_EVIDENCE_UNAVAILABLE and restoration recovers the positive result; isolated Session revision increment returns NOT_AT_CAPTURE_FENCE | EVD-001/003 |
| REQ-003 | Keep source and operator evidence separate | Must | Original fork selector and gates remain; one exact new method compiles under dedicated opt-in, 17 finite tests pass, no PG report in source gate, 28 migrations unchanged | EVD-002/004, T4-03 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Inspect exact class and finite evidence | Existing offline Maven source reactor |
| ACTOR-002 | Disposable database operator | Verify real independent reader behavior | Approved fresh primary, setup identity, runtime writer and distinct limited reader logins |
| ACTOR-003 | Fixture USER owner | Observe one exact Run checkpoint | Synthetic verified identity and existing reader |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source readiness | ACTOR-001 | Maven, JUnit, javap | G0/G1/G2/S1 | Exact clean baseline | 17 finite tests and exact compiled guards | Missing class, report or migration mismatch stops | I1 remains separate | REQ-003 | Existing source reactor | TEST-001/002 |
| UC-002 | Verify persisted boundary and drift | ACTOR-002 | ACTOR-003, PostgreSQL, real product fixture | Exact I1 | Disposable primary and independent SELECT-only role | Both codecs match and three negative cases refuse | Any wrong outcome or missing privilege isolation fails | Restore temporary fixture perturbations and retain owning XML | REQ-001/002 | Existing Java fixture | TEST-003 |

## 5. Constraints, Assumptions, and Decisions

Existing capture property must be true. Require separate `tianji.postgres.checkpoint.observation=true` and `tianji.postgres.checkpoint.observation.reader-role` matching `tianji_it_checkpoint_observer_[a-z0-9_]{1,24}`. The reader is a distinct pre-provisioned limited login, with no membership or admin rights, on the same existing credential-free `jdbc:postgresql://127.0.0.1:<port>/tianji_it_checkpoint_<name>` isolated primary. Use the existing capture runtime-role, installed vector patch and fresh schema guards. Apply exact V1–V15 then V16 only in the authorized disposable fixture. Only test setup grants/revokes narrow privileges. No production or shared database role change.

## 6. Project Technology Context

Existing Java 25, Spring JDBC, Flyway, JUnit, ADK Fake product executor and controlled child JVM fixture. Reuse the current immutable V16 and verified reader. Existing independent production privileges are not supplied by the source test.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–004 | Exact semantic method and fixture role | §7/8 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Use current PG guards, exact owner and disjoint reader checks | §7/8 | TEST-001–003 | PASS |
| Rule 3 | No | EVD-001–004 | Existing typed fixture, owner and observation enum | §7/8 | TEST-001–003 | N/A |
| Rule 4 | No | EVD-001–004 | No production bean/logger/constructor change | §7/8 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse existing JDK, JDBC, Fake model and handoff protocol | §7/8 | TEST-001–003 | PASS |
| Rule 6 | No | EVD-001–004 | Preserve production JSON and existing event codecs | §7/8 | TEST-001–003 | N/A |
| Rule 7 | No | EVD-001–004 | Only test opt-in, existing application profiles unchanged | §7/8 | TEST-001–003 | N/A |
| Rule 9 | No | EVD-001–004 | One fixture flow and direct existing fork reuse | §7/8 | TEST-001–003 | N/A |
| Rule 10 | Yes | EVD-001–004 | DB-origin lease expiry and bounded child waits | §7/8 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Existing AR integration fixture and exact immutable history | §7/8 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run and Session lease_expires_at, current Run deadline_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Database clock | No display surface | Expire exact fixture leases before deadline; preserve owner/epoch witness | PostgreSQL clock and existing precision | Existing Run/Session lease schema | Step 1 TEST-003 |
| TIME-002 | Existing child wait and test timeout | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | No display surface | Bounded existing fork child lifetime and test timeout | Existing fixture timeout and JVM wait | Existing protocol unchanged | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One method and bounded local post-reconcile hook | Add | REQ-001–003 | Existing source-only observer and process-exit fixture | Current tests do not exercise real independent reader SQL and role loss | One guarded fixture reuses existing product child processes | Add |
| Existing fixture provisioner and child protocol | Keep | REQ-001/003 | New fixture or runner | Current primary, Fake runtime and exact restart boundary suffice | Existing setup only | Reuse |
| Existing V16 and role verifier | Keep | REQ-001/002 | New schema or production role | Existing reader/writer separation is the contract to prove | One separately supplied reader login for I1 | Reuse |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Finite source gate | None | Mocked evidence and class proof | Existing local reactor | A passing compile cannot establish PostgreSQL behavior | Bounded source proof |
| Operator I1 | Private loopback PostgreSQL only | One exact Run per native codec | Existing product checkpoint and reconciler, test-only reader | Role loss and Session drift must refuse positive | Real independent observer evidence |

### 7.1 System Architecture Design

Keep the new method in AgentCheckpointCapturePostgresTest. Reuse its product execution fork and second JVM reconciliation; run the existing observer from the parent using a distinct SELECT-only DataSource in the same disposable primary. The original process-death method keeps its own exact guard and behavior.

### 7.2 High-Level Design

Validate distinct setup, runtime and reader identities before reader access. Provision existing migrations under current setup guard. For each native codec let the current product child commit its checkpoint and stop before model work, forcibly stop the child and use the existing second JVM reconciliation. Use exact Run and captured Session lease fences before the parent observes. Retain both positive and negative reader results without returning the native event payload.

### 7.3 Detailed Design

Add `observes_only_exact_owned_product_checkpoint_after_reconciliation_and_rejects_evidence_drift` under the class capture gate and separate observation gate. Refactor only the existing fork fixture helper enough to pass a post-reconcile hook. Use exact captured Session owner and epoch to advance only its expiry to a past database time; verify unchanged durable checkpoint and Session input. Invoke existing AgentCheckpointObservationService for the original verified actor and assert BOUNDARY_OBSERVED for both codec runs. A different verified USER in the same tenant receives TIANJI_RESOURCE_NOT_FOUND for the original Run. Revoke only V13 invocation run_id column SELECT from the exact reader, assert TOOL_EVIDENCE_UNAVAILABLE, then restore and re-observe BOUNDARY_OBSERVED. Increment only the observed Session revision by one, require NOT_AT_CAPTURE_FENCE, then restore the fixture revision. Isolate every grant and perturbation to the disposable database.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-02-28-agent-checkpoint-observation-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-02-29-agent-checkpoint-observation-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-observation-postgres.md` |

## 9. Interface Definitions

No product API. Add one test method with exact test-local properties. The fixture directly calls the existing AR Java observer with existing trusted owner and run query.

## 10. POJO and Data Model Design

Use existing product fork, capture and observation types. No new production carrier, transport, schema or conversion.

## 11. Database Design

Context-only. Reuse exact V1–V15 then V16 history in the existing disposable database. The reader uses SELECT-only grants for existing required tables and narrow V13 scope columns. Setup controls one exact fixture lease/revision and reader test grants. No new SQL version.

## 12. Frontend Page Design

Unchanged. No browser, Web source or deployed endpoint in this scenario.

## 13. Design Patterns and Architecture Principles

Reuse the existing owner-fenced product fork as one shared test helper. A bounded post-reconciliation hook extends the fixture. No new production abstraction or alternate ADK runner.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean offline G1 runs exact three safe finite suites, expected owning reports 6 + 7 + 4 with no failures, errors or skips. |
| TEST-002 | G0 proves the method is absent on exact baseline. G2 checks bytecode method/guards and absence of a PG owning report. S1 checks exact four paths, strict docs and unchanged 28 SQL. |
| TEST-003 | Operator I1 alone exercises actual V16 PostgreSQL, existing product fork and distinct reader; exact positive, cross-owner, V13 role and Session revision outcomes on both codecs. |

## 15. Non-functional and Cross-cutting Design

Preserve existing bounded child process lifetime, independent Fake runtime and test cleanup. Test activation requires disposable loopback, fresh schema and separately supplied approved database authentication. No observer credentials, raw state or native payload in source evidence.

## 16. Compatibility, Migration, Rollout, and Rollback

One existing test path and three review documents. Preserve current class and original fork gates. Production schema, dependencies, reader code, configuration, PaC and permissions remain unchanged. Revert this test-only commit to remove the scenario.

## 17. Alternatives and Decisions

Reuse existing product process-death and reconciliation infrastructure for the stronger physical boundary. Existing finite tests continue to run by default in source verification; dedicated PostgreSQL fixtures have independent permission and runtime prerequisites.

## 18. Risks and Open Questions

Actual I1 requires a separately authorized primary with pinned V16, vector patch and disjoint limited roles. Missing or skipped I1 leaves runtime observation pending. Original invocation resume, expired Run takeover and full S11 are separate.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §5/7/11 | TEST-003 |
| REQ-002 | UC-002 | §7/11/14 | TEST-003 |
| REQ-003 | UC-001 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR PostgreSQL fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002/003 | Reuse product fork, provisioner, current observer and roles | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, JUnit, PostgreSQL driver and Flyway | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Dedicated method, reader property and fixture scope | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Exact dedicated owner and physical reader preconditions | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing evidence and result types | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing observer codecs | None |
| MC-LOG-001 | Not applicable | N/A | §15 | Existing JUnit output, no new logging boundary | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | Direct fixture instantiation only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing test helpers and JDK | None |
| MC-JSON-001 | Not applicable | N/A | §10 | Existing native encoders and observer proof | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | DB expiration and immutable causal evidence | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | New test-local opt-in properties only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Extend existing fork fixture by a bounded post-reconcile hook | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | One test path and three review files | None |
| MC-TEST-001 | Applicable | PASS | §14 | Compile evidence and independent operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Owning source gate remains independent of actual I1 | None |

### 20.6 Final verdict

PASS — Ready for user review
