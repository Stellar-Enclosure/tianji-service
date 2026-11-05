# T4-03 Agent checkpoint 边界观察

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One exact owner query on an independently provisioned primary and one fixed checkpoint boundary |
| Created | 2026-11-06 02:18 Asia/Shanghai |
| Updated | 2026-11-06 02:18 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Explicit V16 primary-reader internal observation and finite proof |
| Change Surface | Existing AR Session and storage infrastructure |
| Affected Chapters | §7, §8, §9, §10, §11, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 6fa58d2ff7dd9f0a286ca9a149f9b7ee30f8de62 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [V16 storage](./2026-11-06-01-25-agent-checkpoint-storage.md), [Checkpoint writer](./2026-11-06-01-42-agent-checkpoint-capture.md) |
| Related Plans | [Checkpoint observation](../plan/2026-11-06-02-19-agent-checkpoint-observation.md) |

## 1. Summary

在已有 V16 写入后增加显式构造的 Agent Runtime 内部观察能力。用独立 SELECT-only 主库身份检查既有 owner 的首条输入 checkpoint 是否仍与租约失效后的持久证据吻合。结果只表示一次物理只读快照；当前不接入 HTTP、调度、恢复命令或授权。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [V16 contract](./2026-11-06-01-25-agent-checkpoint-storage.md), AgentStorageVerifier#verifyCheckpointCapture | V16 pins immutable Run, native Session and owner evidence; the capture role explicitly lacks SELECT |
| EVD-002 | AgentLedgerService#executionMaterial/#suspendExpiredLease/#appendStatusEvent and JdbcAgentLedgerRepository#suspendExpiredLease | Owned material verifies canonical hashes; expiry increments epoch, revision and event sequence and atomically appends RUN_STATUS_CHANGED |
| EVD-003 | [Checkpoint writer](./2026-11-06-01-42-agent-checkpoint-capture.md), PostgresAdkSessionService#matchesFirstInput and native codecs | Exact first USER input is already checked at capture; existing codecs support both read versions |
| EVD-004 | V13 tool journal and V2 agent_budget_reservation | Missing Tool evidence is not absence; old reserved calls must still deny after expiry zeroes active counters |
| EVD-005 | AgentExecutionGrantStore and AdkAgentRunExecutor | Ordinary grant and Run start paths do not cover SUSPENDED continuation |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR infrastructure only. No generation or archetype example work.

## 3. Goals and Non-goals

Observe the stored first input boundary under the full trusted owner, V16 and primary-reader role. Keep a conservative result when evidence is incomplete. Product claims, IAM decisions, continuation, external reconciliation, DB provisioning and live S11 are independent scopes.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR Session and storage infrastructure | Affected | EVD-001–004 | Bounded exact owner snapshot and V16 reader check | Role, SQL, lifecycle, codecs and failure semantics | §7, §8, §9, §10, §11, §14, §15, §16 |
| Existing capture and auth | Context-only | EVD-001/003/005 | Capture remains INSERT-only, ordinary dispatch unchanged | Contract preservation | §7/11/16 |
| External interfaces and Web | Unchanged | EVD-005 | No route, DTO, permission or browser change | Explicit boundary | §9/12/18 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Separate explicit checkpoint read role | Must | Trusted constructor and each owning primary read validate effective SELECT-only role; capture stays INSERT-only | EVD-001, T4-03 |
| REQ-002 | Observe one exact owned capture fence | Must | Full USER identity and exact run; suspended expiry must advance epoch, revision and Run watermark by one; no later ledger event or other checkpoint | EVD-001/002 |
| REQ-003 | Require intact Session and no later side-effect evidence | Must | Exact native event/state with current codecs and first input; expired same Session epoch, no extra events or scopes, zero persistent budgets, exact readable V13 with no matching invocation or action origin | EVD-003/004 |
| REQ-004 | Return bounded fail-closed evidence and preserve current runtime | Must | Closed enum only, no raw identifiers or runtime activation; clean finite tests and package comparisons, 28 SQL preserved | EVD-001/005, S11 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Internal source consumer | Evaluate one known owner and Run without mutation | Verified USER context already supplied and independently provisioned primary reader |
| ACTOR-002 | Source reviewer | Verify negative evidence and ABI scope | Offline Maven and controlled mock JDBC |
| ACTOR-003 | Provisioning/acceptance operator | Establish actual read role, storage and runtime proof separately | Existing independent PostgreSQL environment |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe exact checkpoint fence | ACTOR-001 | AR and AgentDB | Explicit internal call | Verified owner and fixed primary reader | BOUNDARY_OBSERVED after all exact comparisons | Absence, stale history, invalid binding, unclear tool evidence or role drift refuse positive | No writes or credentials returned | REQ-001–004 | Java only | TEST-001/002/003 |
| UC-002 | Verify source and package boundary | ACTOR-002 | JUnit and Maven | Exact source gate | Reviewed clean baseline | Actual finite/source artifacts, existing write role unchanged | Test or digest mismatch blocks | ACTOR-003 still owns live evidence | REQ-004 | Existing reactor | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Keep all 28 existing migrations immutable. The observer receives an independent fixed Agent-primary DataSource and never provisions roles. Validate non-owner, non-superuser, no BYPASSRLS and no mutation/grant privileges over the observed relations. The exact existing V16 capture writer capability remains separately checked, with no SELECT privilege added.

## 6. Project Technology Context

Java 25, existing Spring JDBC transactions, JUnit Mockito fixtures and pinned ADK 1.13.0. The current Agent infrastructure architecture owns both the native event codec and the AgentDB technical checks.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–005 | Exact observation service and typed private row | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 2 | Yes | EVD-001–005 | Exact verified USER, run ID and existing material plus native evidence validation | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 3 | Yes | EVD-001–005 | Immutable observation row private to JDBC and redacted | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 4 | No | EVD-001–005 | Explicit constructor without bean registration or new logging | §7/8/10 | TEST-002/003/004 | N/A |
| Rule 5 | Yes | EVD-001–005 | Existing JDK, Spring JDBC and ADK codecs | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 6 | Yes | EVD-001–005 | Existing codec paths and fixed typed JSONB comparisons | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 7 | No | EVD-001–005 | No application profiles or runtime flags change | §7/8/10 | TEST-002/003/004 | N/A |
| Rule 9 | No | EVD-001–005 | One fixed query boundary without a strategy hierarchy | §7/8/10 | TEST-002/003/004 | N/A |
| Rule 10 | Yes | EVD-001–005 | Authoritative PostgreSQL timestamp comparison and existing Java instants | §7/8/10 | TEST-002/003/004 | PASS |
| Rule 11 | Yes | EVD-001–005 | Existing AR infrastructure and immutable Flyway history; no new events | §7/8/10 | TEST-002/003/004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.deadlineAt, checkpoint captured_at, Session lease_expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Database UTC clock | No display surface | Finite times compared to same transaction; expired lease at equality, deadline must be later | PostgreSQL transaction timestamp, existing precision | Existing formats and values preserved | Step 1 TEST-002/003 |
| TIME-002 | event_timestamp_ms and native Event timestamp | Occurred instant | Existing ADK epoch milliseconds | UTC instant | Internal | Database and recorded ADK instant | No display surface | Exact native id, invocation and timestamp match | Existing native milliseconds | Both native codecs retained | Step 1 TEST-002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| AgentCheckpointObservationService | Add | REQ-001–004 | Existing owner recovery disposition | Existing capture role forbids SELECT and current disposition lacks native boundary evidence | One explicit Java observer and bounded query | Add |
| AgentStorageVerifier reader role | Modify | REQ-001/003 | Existing V16 capture role | Reader and writer require disjoint checkpoint permissions | Reuse catalog gates, add explicit separate checks | Reuse and extend |
| Native codecs and ledger | Keep | REQ-002/003 | New mappers or runner | Existing codecs and canonical material already own interpretation | None | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Explicit Java observation | Fixed Agent primary only | Closed result enum | One RR physical read-only transaction | Owner, role, V16/V13 or persisted evidence drift refuses positive | Defines exact persisted pre-model evidence |
| Existing capture and external runtime | Existing contracts | Existing states | Existing Run/Session and permissions | Unchanged write-side fences | Independent future takeover proof |

### 7.1 System Architecture Design

Existing AR infrastructure contains the only observer. It constructs the checkpoint verifier, ledger and JDBC template from the same explicit read DataSource. Existing production startup, CLI, HTTP and recovery callers do not instantiate this observer.

### 7.2 High-Level Design

The observer owns one bounded physical read-only REPEATABLE READ snapshot, verifies the current primary and effective reader role before any owner material read, and compares the immutable capture against the exact latest SUSPENDED/LEASE_EXPIRED state. No fallback onto a replica, capture writer or admin account.

### 7.3 Detailed Design

Verify the complete non-LEGACY USER and exact run ID. Use existing executionMaterial for owner, definition, canonical snapshot, request and policy provenance. Require first attempt and first checkpoint, exact Run lease/revision/watermark +1 with no newer ledger event or other checkpoint key, exact RUN_STATUS_CHANGED expiry payload, null cancelled/terminal/lease fields and live finite deadline. Verify the checkpoint app/session match, safe boundary, engine marker 1.13.0 and empty receipts.

Require native Session revision and watermark exactly one, previous counters zero, captured Session epoch unchanged and lease non-live, no other native event, no APP/USER state, empty decoded local state. Read only size-bounded payload/state and compare actual decoded native event ID, invocation ID, row timestamp and exact original plain USER content via existing codecs and matchesFirstInput. Require zero active and total model/tool/token/cost counters plus no reservation for any epoch/state. Require exact readable V13 and absence of any invocation or originating action for tenant/run; unknown or inaccessible evidence never matches. Fixed status values and no raw data in output.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java` |
| CREATE | `docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md` |
| CREATE | `docs/egon/plan/2026-11-06-02-19-agent-checkpoint-observation.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-observation.md` |

## 9. Interface Definitions

### 9.1 Interface Inventory

Only the AR-internal Java observer and reader verification methods. No REST, CQE event, GraphQL, client or PaC contract.

### 9.2 Per-interface Detailed Contracts

An explicit independently provisioned DataSource constructs AgentCheckpointObservationService. observe(TrustedAgentActor, GetAgentRunQuery) returns the closed Observation under its own bounded snapshot. Existing scoped not-found/owner error remains. Unavailable physical read, reader privileges or evidence fails closed.

## 10. POJO and Data Model Design

Reuse existing material, request and input values, PostgresAdkSessionService#matchesFirstInput and both ADK codecs. A private immutable JDBC row holds bounded observations for comparison, uses redacted toString and never leaves the observer. Return one enum among BOUNDARY_OBSERVED, NO_MATCHING_CHECKPOINT, NOT_AT_CAPTURE_FENCE, PROGRESS_OBSERVED, TOOL_EVIDENCE_UNAVAILABLE or SOURCE_UNAVAILABLE.

## 11. Database Design

### 11.1 Table Inventory

| Table | Existing/new | Purpose and owner | Read/write paths | Change | DDL script | Requirements |
| --- | --- | --- | --- | --- | --- | --- |
| tianji_agent.agent_checkpoint | Existing | AR exact capture evidence | Explicit reader SELECT on first owned checkpoint | New internal observation path, no schema change | Existing V16 | REQ-001/002 |
| Current Run, native Session, budget and V13 journal tables | Existing | AR authoritative boundary witnesses | Owner-bound joins and NOT EXISTS scope queries | Existing rows read in the same snapshot | Existing V1–V16 | REQ-002/003 |

### 11.2 Per-table Detailed Design

#### 11.2.1 tianji_agent.agent_checkpoint

##### Purpose, ownership, and lifecycle

The existing V16 historical cut stays immutable and owned by the Agent primary. A distinct reader checks one exact observed boundary; its SELECT does not alter retention or existing capture.

##### Complete column design

| Column | Native type | Source / change |
| --- | --- | --- |
| All V16 typed columns | See the locked V16 storage Spec | Use the exact existing types and limits. The observer projects the immutable hashes, boundary, engine, and necessary native provenance only. No column changes. |

##### Keys, relationships, and constraints

Use the existing composite V16 key for one first attempt/checkpoint. Current full owner and exact native-event relationships are checked in the same read snapshot.

##### Index inventory and per-index justification

| Index | Type/unique | Ordered columns/expressions | Predicate/include | Query and operation | Cardinality/selectivity | Sort/coverage role | Write/storage cost | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| agent_checkpoint_pkey | Existing B-tree unique | tenant_id, run_id, attempt, checkpoint_no | None | Exact first capture and other-key check | One expected row | No new sort or covering index | No new writes or storage | Reuse |

##### Access patterns and SQL shape

| Operation | Caller | Predicate/join/order | Expected rows | Index/constraint | Lock/isolation | Failure/idempotency |
| --- | --- | --- | --- | --- | --- | --- |
| Observe one boundary | AR observer | Exact owner Run and first checkpoint, native Session and negative evidence | At most one | Existing PK and FK | Read-only repeatable read, no write lock | Missing, progressed or invalid evidence refuses positive |

##### Migration and historical-data handling

Existing 28 SQL files remain immutable, including V16. The observer requires its exact migration and never installs, rewrites or deletes any checkpoint.

##### Transaction, consistency, and recovery

No DDL or change to Flyway. Fixed full-owner Run/Session keys, bounded checkpoint join and NOT EXISTS negative checks on Run events, checkpoints, Session events/state, budget and V13 journal. The explicit reader role is not the INSERT-only role; exact V16 shape and role, RLS, owner and inheritance checks are fail-closed. V13 invocation and origin projections need only scope columns. Lack of proof remains unavailable.

### 11.3 Entity-relationship diagram

Relational model change: No

Existing relationship: checkpoint references one owning Run and one exact full-owner native Session event. The Session lease is joined by the same Session key; current Run event and budget witnesses are joined by tenant and run.

## 12. Frontend Page Design

Unchanged. No UI or browser source involved.

## 13. Design Patterns and Architecture Principles

Reuse the existing JDBC adapter and verified owner material. One internal concrete query is sufficient. Native event parsing uses the existing codec rather than a parallel mapper. No scheduler, policy strategy or API abstraction is introduced.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Typed R1 temporarily omits the exact V13 readiness guard; unreadable_v13_never_observes_boundary fails with BOUNDARY_OBSERVED instead of TOOL_EVIDENCE_UNAVAILABLE. |
| TEST-002 | Six observation tests include both native codecs, physical read-only RR, exact owner and SQL fences, role drift, stale hashes and model/budget/Tool negative proof. |
| TEST-003 | Existing storage verifier and writer regression tests exercise reader/capture separation, actual constructor contracts and exact V16 evidence. |
| TEST-004 | Clean permission-producer artifacts, unchanged permissions/config/SQL and no production caller. No actual role, PostgreSQL, restart or S11 run. |

## 15. Non-functional and Cross-cutting Design

Bound the physical snapshot and statements to 2 seconds. Project only size-bounded native values. Preserve owner and schema checks and redact internal row string representations. SQL timeout, missing required rows or invalid codecs refuse positive. No PII, token, payload or hash enters the reported enum.

## 16. Compatibility, Migration, Rollout, and Rollback

One source-only commit. No production binding or grants. Keep all 28 migrations, current writer app configuration and permissions. Revert the source commit if needed.

## 17. Alternatives and Decisions

Do not add SELECT to the V16 capture identity. The existing broad recovery disposition stays independent. A separate explicitly supplied observer permits verification of persisted evidence without assuming runtime takeover authority.

## 18. Risks and Open Questions

Provisioned primary-reader privileges, PostgreSQL behavior and the native continuation mode require independent live verification. Tool action origin scans are bounded by statement timeout and may become unavailable. A BOUNDARY_OBSERVED result can immediately go stale and cannot authorize the future writer. SUSPENDED-to-RUNNING takeover needs a fresh IAM decision, exact original-invocation support and atomic locked recheck.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001, UC-002 | §5/7/11 | TEST-002/003 |
| REQ-002 | UC-001 | §7/9/11 | TEST-002 |
| REQ-003 | UC-001 | §7/10/11 | TEST-001/002/003 |
| REQ-004 | UC-001, UC-002 | §8/14/15/16 | TEST-004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR native Session and storage infrastructure | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing ledger material, JDBC, codecs and V16 checks | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, Spring JDBC, Mockito, ADK | None |
| MC-NAME-001 | Applicable | PASS | §7/8/10 | Exact observation service, JDBC row and closed result | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Exact verified owner and fail-closed snapshot evidence | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable mapped row with redacted string and closed result | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No layer or wire mapper, only existing codecs | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logs and no raw values in result/errors | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | Explicit construction and no Spring registration | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, JDBC and codecs | None |
| MC-JSON-001 | Applicable | PASS | §10/11 | Existing codecs and exact JSONB status evidence | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB UTC comparison and unchanged instant contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration change | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing adapters and fixed bounded query | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four Java paths and three documents | None |
| MC-TEST-001 | Applicable | PASS | §14 | R1, G1, P1 and actual result evidence required | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator permissions, PostgreSQL and S11 remain separate | None |

### 20.6 Final verdict

PASS — Ready for user review
