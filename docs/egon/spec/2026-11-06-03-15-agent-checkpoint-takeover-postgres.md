# T4-03 Checkpoint 单次接管 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated existing AR fixture with real product fork, independent takeover identity and typed fixture authority |
| Created | 2026-11-06 03:15 Asia/Shanghai |
| Updated | 2026-11-06 03:15 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents |
| Change Surface | Dedicated operator-only one-shot V16 takeover and no-repeat evidence |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 41050e26f35af6b2ca98d33c6a63e26b9df387d2 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Checkpoint takeover](./2026-11-06-03-04-agent-checkpoint-takeover.md), [Product checkpoint fork](./2026-11-06-02-11-agent-checkpoint-product-fork.md), [Native resume fixture](./2026-11-06-02-56-agent-checkpoint-native-resume.md) |
| Related Plans | [Takeover PostgreSQL proof](../plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md) |

## 1. Summary

在现有产品 checkpoint 双 JVM 协调夹具中增加独立 PostgreSQL 接管用例。第三登录角色经受限 V16 读权限和精确核心列写权限调用实际接管事务。正向覆盖同一 Run 和 attempt 的双租约、状态事件与回执；负向覆盖 owner、revision、过期许可和同键异请求。源码门禁只编译并运行有限测试，真实 I1 由操作者独立执行。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Checkpoint takeover](./2026-11-06-03-04-agent-checkpoint-takeover.md), AgentCheckpointTakeoverService#takeOver | 已有未接线的一次性接管事务、当前授权 port、两条状态事件与受限 RESUME 回执；实际 PostgreSQL 尚需验收 |
| EVD-002 | [Product checkpoint fork](./2026-11-06-02-11-agent-checkpoint-product-fork.md), AgentCheckpointCapturePostgresTest#verifyProductCheckpointForks | 现有 V16 产品捕获、强制退出和第二 JVM 协调支持两种 codec；原生续跑用例另有入口 |
| EVD-003 | AgentCheckpointCapturePostgresTest#provision and #grantCheckpointTakeover; PostgreSQL row lock rule | V16 维持 SELECT-only；核心行锁和接管 DML 有明确受限授权。[PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html) |
| EVD-004 | AgentCheckpointTakeoverServiceTest, AgentCheckpointForkHandoffTest, AgentCheckpointObservationServiceTest | 已有可在源码门禁安全选择的有限测试，覆盖接管事务分支、交接和观察守卫 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR integration fixture. No generated project or template examples are in scope.

## 3. Goals and Non-goals

为 V16 首条原生输入已持久、原 Run 已协调为 SUSPENDED 的场景提供真实 PostgreSQL 接管、事务回滚及重放证据。fixture authority 仅给精确请求发测试许可。生产授权注册、真实身份平台、应用接线、运行接管后的模型调用及完整 S11 由独立步骤负责。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR PostgreSQL test fixture | Affected | EVD-001–004 | 新增独立 opt-in 方法及局部断言 | 精确角色、进程复用、回滚及提交证据 | §7, §8, §14, §15, §16 |
| PostgreSQL V16 and takeover service | Context-only | EVD-001/003 | Current schema and claim semantics | Fixture access paths and permission proof | §11 |
| Production runtime, HTTP, PaC and Web | Unchanged | EVD-001/002 | Current startup and permission boundaries | Explicit source scope | §9/12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Claim the exact reconciled product checkpoint once with the scoped permit | Must | Existing isolated V16 fork and both codecs; new epochs, same Run and attempt, two ordered status events and one ACK receipt; checkpoint and exact native prefix retained | S11, EVD-001/002/003 |
| REQ-002 | Prove refusal, rollback and stable replay | Must | Wrong owner is not found, stale revision conflicts, expired typed permit leaves zero receipt, exact repeat returns original ACK and same lease/event/receipt witnesses with no new decision; changed content conflicts | EVD-001/003 |
| REQ-003 | Separate finite and operator evidence | Must | Exact new method and guards compile, 22 finite tests pass, no PG report, 28 migrations byte-identical and exact four paths | EVD-002/004, T4-03 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify exact compiled method and finite reports | Existing offline source reactor |
| ACTOR-002 | Disposable database operator | Verify real transaction, isolated role and replay | Approved fresh primary, setup identity, capture runtime and separate takeover login |
| ACTOR-003 | Fixture USER and authority callback | Request one exact claim | Synthetic verified owner; callback echoes typed binding and uses DB-origin expiry |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source readiness | ACTOR-001 | Maven, JUnit, javap | G0/G1/G2/S1 | Exact clean baseline | 22 finite tests and exact compiled guards | Missing class, report or migration mismatch stops | I1 remains independent | REQ-003 | Existing source reactor | TEST-001/002 |
| UC-002 | Verify persisted one-shot takeover | ACTOR-002 | ACTOR-003, PostgreSQL, current V16 fork | Exact I1 selector | Disposable primary and independent takeover role | Both codecs prove exact same-attempt claim and stable replay | Any guard leak, extra event, changed lease or receipt fails | Preserve raw owning XML and dispose of test DB | REQ-001/002 | Existing Java fixture | TEST-003 |

## 5. Constraints, Assumptions, and Decisions

Class gate `tianji.postgres.checkpoint.capture=true`. Require separate `tianji.postgres.checkpoint.takeover=true` and `tianji.postgres.checkpoint.takeover.role` matching `tianji_it_checkpoint_takeover_[a-z0-9_]{1,24}`. Setup, capture runtime and takeover are three distinct identities on the same approved credential-free loopback URL with a fresh disposable V16 schema. Use existing runtime-role, vector patch and fresh schema guards. Provision only the exact fixture history and role permissions. Authority returns a typed test permit from the observed DB time after verifying exact execution binding, permitted command and bounded cut SHA. The expired control returns DB-now minus one second; fresh control uses plus twenty seconds.

## 6. Project Technology Context

Existing Java 25, Spring JDBC, Flyway, JUnit, ADK product fork and Fake capture. Reuse current V16, current exact takeover service and role verification. Source gate runs the existing offline Maven reactor.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–004 | Exact semantic test method and separately named fixture role | §7/8 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Use current PG guards, exact owner, revision and permit checks | §7/8 | TEST-001–003 | PASS |
| Rule 3 | No | EVD-001–004 | Reuse current types; any private witness is immutable | §7/8 | TEST-001–003 | N/A |
| Rule 4 | No | EVD-001–004 | Direct fixture construction without production Spring components or log changes | §7/8 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Use existing JDK, JDBC, Fake and fork handoff | §7/8 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–004 | Verify persisted typed ACK and exact status events via existing ledger and PostgreSQL JSONB | §7/8 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–004 | Only test-local opt-in properties | §7/8 | TEST-001–003 | N/A |
| Rule 9 | No | EVD-001–004 | One bounded fixture with direct reuse of existing fork helper | §7/8 | TEST-001–003 | N/A |
| Rule 10 | Yes | EVD-001–004 | Database-origin expiry and bounded operator process waits | §7/8 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Existing AR test owner and immutable Flyway history | §7/8 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run deadline, permit and Run/Session lease expiry | Occurred instant | java.time.Instant, PostgreSQL timestamptz, JDBC Timestamp | UTC instant | Internal | Database clock | Not applicable: no display | Expired permit rolls back; positive lease must be future and bounded by permit/deadline; no local-zone conversion | PostgreSQL clock and existing microsecond precision | Existing Run/Session lease schema | Step 1 TEST-003 |
| TIME-002 | Child wait and method timeout | Duration | java.time.Duration and test timeout | Not persisted | Internal | Not applicable: elapsed time has no zone | Not applicable: no display | Bounded existing fork child lifetime and test timeout | Existing fixture timeout and JVM wait | Existing child protocol unchanged | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One dedicated operator method and local post-reconcile assertion | Add | REQ-001–003 | Existing fork and finite takeover tests | They do not establish physical PostgreSQL claim, role and rollback semantics | One separate guarded fixture with existing child processes | Add |
| Existing fork, role setup and takeover service | Keep | REQ-001/003 | New fixture or SQL implementation | Existing production DAO and service are the verification targets | Reuse setup and exact claim path | Reuse |
| Separate takeover login | Reuse fixture role pattern | REQ-001/002 | Existing capture role or schema owner | Neither proves restricted V16 SELECT-only claim with column-level DML | Only explicitly supplied third login in I1 | Reuse |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Finite source gate | None | Mocked ownership and class evidence | Existing offline reactor | Compiled fixture cannot prove PG behavior | Bounded owning evidence |
| Operator I1 | Private loopback PostgreSQL only | One product Run per codec | Existing fork, reconciler, claim and receipt | Expired permit must roll back, replay must preserve exact persisted facts | Physical one-shot verification |

### 7.1 System Architecture Design

Keep the new method in AgentCheckpointCapturePostgresTest. Reuse the existing two-JVM product capture and reconciliation path. Parent fixture connects a distinct takeover identity to the same primary and calls the actual AR claim service with a typed authority callback. Existing product fork, observation and native resume entry points retain their guard behavior.

### 7.2 High-Level Design

Provision and verify the exact third role before assigning restricted claim DML. For each codec preserve the existing first-input checkpoint and post-reconcile evidence, then expire only the exact old Session lease with the captured fence. Confirm owner and revision guards run before authority. For expired permit check rollback of provisional receipt and reconciled state; for fresh permit capture ordered transition and lease evidence and compare exact replay witnesses.

### 7.3 Detailed Design

Add `takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit` under existing capture and dedicated takeover gates. Reuse `provision(role)`, `assertCheckpointReaderPrivileges` and `verifyProductCheckpointForks(database, RECONCILE, ...)`. Invoke `grantCheckpointTakeover` with the verified quoted fixture role, and check V16 SELECT with absence of write privileges. The post-reconcile helper retains native prefix and checkpoint digest, checks exact old lease expiry, and issues wrong-owner, stale-revision, expired-permit and fresh-permit commands in that order. Positive ACK must have revision suspended+2 and attempt 1. Verify Run epoch original+2 and Session epoch original+1, distinct Session owner and common expiry no later than original deadline or permit. The two status event payloads are SUSPENDED→QUEUED and QUEUED→RUNNING with CHECKPOINT_RESUME, in consecutive sequence order. Exact replay uses a different worker input, returns original ACK, preserves complete events and both lease/receipt SHA witnesses, and makes no additional authority call. Changed expected revision under the same command ID conflicts. Verify exact checkpoint, raw native prefix and zero budget, tool invocation, tool intent and product result evidence.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-takeover-postgres.md` |

## 9. Interface Definitions

Add one operator-only test method with exact JVM properties. It calls the existing AR Java takeover service using the fixture verified actor and typed ResumePermit. Product transport and permission declarations are outside this change.

## 10. POJO and Data Model Design

Reuse existing Run, fork and takeover types. Test-only witnesses contain existing event values and digests. Preserve production DTO, schema and codecs.

## 11. Database Design

Context-only. The isolated setup reuses V1–V15 then V16 and the existing third-role SELECT surface. The takeover role has SELECT-only on V16. Extra fixture grants are SELECT on command receipt; UPDATE on Run status, wait reason, revision, event sequence and lease columns; UPDATE (revision) on Session for row locking; UPDATE on Session lease epoch, owner and expiry; INSERT on selected status event columns; and selected receipt INSERT plus result-only UPDATE. PostgreSQL 18 row locks require UPDATE on at least one column of each locked table along with SELECT, which is why the fixture grants the single Session revision column even though takeover does not update that row. [PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)

## 12. Frontend Page Design

Context-only. Existing Web code and deployed endpoints lie outside this test.

## 13. Design Patterns and Architecture Principles

Extend the existing fork fixture with its bounded post-reconciliation callback. Reuse the production transaction and fake only current authority. Status/event/receipt checks independently observe committed facts.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean offline G1 selects three safe finite suites, owning reports 10 + 5 + 7 and zero failures, errors or skips. |
| TEST-002 | G0 proves exact method absence. G2 verifies method, class/method opt-ins, role property, preserved private child entry and absence of PostgreSQL report. S1 checks four paths and immutable SQL. |
| TEST-003 | Operator I1 alone exercises actual restricted role, PostgreSQL transactions, existing child fork, exact same-attempt claim, refusal/rollback and replay across both codecs. |

## 15. Non-functional and Cross-cutting Design

Preserve existing bounded child process lifetime and test cleanup. Test activation requires disposable loopback, absent schema and independently supplied authentication. Source evidence records counts, flags and expected outcomes; no credentials, lease owner or raw native payload are published.

## 16. Compatibility, Migration, Rollout, and Rollback

One existing test path and three review documents. Preserve production SQL, startup defaults, PaC and existing operator modes. Revert this test-only commit to remove the new proof. Role operations occur only within a separately authorized disposable database.

## 17. Alternatives and Decisions

Reuse product fork and existing takeover service. A separate mode exercises PostgreSQL SQL and grants with strong isolation; source-only mocks continue to prove their own behavior under G1.

## 18. Risks and Open Questions

Actual I1 requires pinned V16, exact installed vector patch, three disjoint logins and an authorized disposable primary. Missing/skipped I1 leaves physical transaction, role and child-process evidence pending. Real IAM, product wiring, native model execution after takeover and full S11 require independent work.

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
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002 | Existing fork, provisioner, takeover service and evidence checks | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, JUnit, Spring JDBC, driver and Flyway | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact guarded method and separate takeover identity | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Loopback primary, exact role, owner, revision and permit | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing types and private immutable witness only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing codecs and lease observations | None |
| MC-LOG-001 | Not applicable | N/A | §15 | JUnit assertions without credential, lease owner or payload logs | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | Explicit fixture construction without runtime wiring | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Validate existing acknowledgement projection and fixed event payload | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database-origin expiry and strictly bounded permits and leases | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test-local JVM opt-in | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing guarded fork with local callback | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | One test path and three review files | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and class gates separate from I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator runtime evidence pending | None |

### 20.6 Final verdict

PASS — Ready for user review
