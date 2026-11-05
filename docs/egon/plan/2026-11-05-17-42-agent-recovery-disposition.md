# T4-03A Owner-only Recovery Disposition Implementation Plan

| Field | Value |
| --- | --- |
| Document | `2026-11-05-17-42-agent-recovery-disposition.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 17:42 Asia/Shanghai` |
| Updated | `2026-11-05 17:47 Asia/Shanghai` |
| Owner | `Tianji Agent owner, with Process owner boundaries preserved` |
| Repository | `tianji-service` |
| Scope | `One AR owner-local Query and finite evidence, no HTTP/PaC/schema/state changes` |
| Source Requirement | `Master §19 T4-03 A and Root direction naming T4-04 ops as the future internal consumer` |
| Baseline Revision | `tianji-service 6b806e43888ea5e133780f9b755eb610326f52b8, tianji-web f558e02c8d07da2da758c5273a5eb531ca251018` |
| Implements Spec | [T4-03A Owner Recovery Disposition](../spec/2026-11-05-16-52-agent-recovery-disposition.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 17:44 Asia/Shanghai, SHA-256 eb47aed30b050208d694b30d86805486037dd8f03ebf75b2ced352411cfc5904` |
| Effective Specs | [T4-03A](../spec/2026-11-05-16-52-agent-recovery-disposition.md) |
| Depends On Plans | [T4-02 source](2026-11-05-16-50-agent-model-usage-implementation.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

Add one actual internal AgentLedgerService owner-only read, `inspectRecovery`, together with narrow AR scalar SQL and finite disposition tests. It observes one run and bounded Tool metadata in the same read-only primary snapshot. Existing Agent GET and PS Worker recovery queries remain separate, and no observation invokes a recovery action. T4-04 ops is the named future consumer. The source can be independently completed after the exact T4-02 source lock and owner review. Live T3-12 E and S11 remain separate.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [T4-03A Owner Recovery Disposition](../spec/2026-11-05-16-52-agent-recovery-disposition.md).
- Status: Review. Canonical Spec DEC-005 locks exact AR internal source with T4-04 ops named; DEC-006 locks existing source prerequisites.
- Revision: 2026-11-05 17:44 Asia/Shanghai, exact SHA above. Root verified the strict normalized copy in the off-repo review mirror.
- Approval evidence: Root approved the exact internal AR source and finite gates under the user master Plan after verifying the current baseline and Spec. This is not runtime, IAM, HTTP or user acceptance.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [T4-03A](../spec/2026-11-05-16-52-agent-recovery-disposition.md) | Review, exact SHA above | §3–§20, including §7.3/§9.3 and DEC-005/006 | Only behavior authority for the new query |
| Source prerequisites only | Existing T4-02 Agent model-usage source, T3-10 Worker recovery and T2-01 Agent | Existing technical owners and state rules | Reuse exact source and selectors, no extra requirement IDs | They establish existing state and read boundary |

### 2.3 Superseded or excluded content

Canonical DEC-005 resolves the old deferred projection choice for AR only. Preserve the exact technical scope and current owner. Live authorization and external exposure, cross-owner aggregation, checkpoint, tool-provider reconciliation, mutation, resume and T3-12/S11 stay out of scope.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/§7.3 | Exact run disposition with one DB time | QUEUED/ACTIVE/STALLED/TERMINAL/UNVERIFIED table and boundaries | AR scalar run read and classifier |
| REQ-002 | §4/§7.3 | Orthogonal Agent effect and Tool journal flags | UNKNOWN remains explicit, local receipt and coverage proven only for matching facts | AR minimized effect/Tool evidence |
| REQ-003 | §4/§9 | Exact owner/run and current PS owner boundary | Foreign/missing same not found, no cross-owner projection, PS T3-10 untouched | AR owner predicates, finite regressions |
| REQ-004 | §4/§14 | Finite proof of precise loss/failure windows | Positive and negative window assertions, no test interpreted as live S11 | One new AR test plus current selectors |
| REQ-005 | §4/§10/§15 | Minimized output and no mutation | SQL/DTO/transaction assertion and static no-write audit | AR read-only snapshot and closed values |
| REQ-006 | §4/§16/§18 | No automatic resume, write, checkpoint or external endpoint | Existing runtime/SDK/HTTP/IAM/schema unchanged | Exact scope and commit |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

One cohesive commit is sufficient. The query only becomes useful with the repository's scoped scalar read, so separating these into producer/consumer commits would leave an unused runtime seam. Start with reflective presence RED, add only compiling AR signatures, then run typed behavior RED. Finish direct repository and ledger logic and regression checks. Keep the existing T4-02 model-usage and T3-10 Worker paths independent.

### 4.2 Test-first strategy

| Gate | Selector | Expected result |
| --- | --- | --- |
| R1 | `AgentRecoveryDispositionEvidenceTest#inspectRecoveryHasInternalSignature` | One genuine missing method assertion on baseline |
| R2 | `AgentRecoveryDispositionEvidenceTest#readsExactOwnerOnBoundPrimaryWithoutWrites` | One genuine positive assertion failure against safe compiled stub |
| R3 | `AgentRecoveryDispositionEvidenceTest` | New scoped query and full disposition matrices GREEN |
| R4 | New test plus existing Agent/Tool/Session/Worker finite regressions | Every owning test report exists and runs, F/E/S=0 |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| 1 | Approved T4-03A Spec/Plan and exact T4-02 source | Read-only review | Current T4-02 AR/AS writes and new runtime work | Baseline and scope must be fixed |

### 4.4 Commit boundaries

One focused service repository commit. Three exact AR Java paths and three approved documentation paths. No AS, C, CL, PS or Web edits, generated code or migration.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Existing owner access | Preserve §9 | AS TianjiAgentAccess and existing GET, exact AR run tuple | Use trusted actor, no new permission | No route change | Reuse |
| Scalar run and Tool evidence | Needed §7.3/§9.3 under Root decision | Existing `run` reads full result/snapshot and Tool recorder is active writer | Same AR owner, fixed metadata columns only | One primary read transaction | Implement |
| Worker evidence | Preserve §7.3 | Existing PS T3-10 owner read | Existing DTO, no cross-store join | No new call or surface | Reuse |
| Resume/cross-owner query | Excluded §3/§16 | Session event persistence is not checkpoint | Keep current actor/source rules | None | Out of scope |

All state is read from its exact owner. No fetch-then-forward, unauthenticated diagnostics, second source selector or action eligibility is introduced.

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Exact owner-local run snapshot | REQ-001/003/005 | R1/R2 | Current AR repo/ledger | Scalar run/Agent effect and observed time | Disposition result | 1 |
| Bounded Tool evidence | REQ-002/005 | R2/R3 | Exact owned run and optional V13 relation | Coverage and flags | Independent Tool axis | 1 |
| Current finite evidence | REQ-003/004/006 | R3/R4 | Existing Agent, Session, Tool and Worker tests | Actual finite reports | Separate future source/live review | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Existing AR technical JDBC persistence, current AS authority | Preserve exact owner and module | Internal Query only, no new package layer | Step 1, MC-ARCH-001 |
| Reuse/capability | Existing owner tuple, JdbcTemplate, TransactionTemplate, DB clock | Reuse direct current seams | Same source, guarded repeatable-read | Step 1, MC-REUSE-001 |
| Naming/model/validation/conversion | Existing C GetAgentRunQuery and internal immutable values | Typed internal evidence | Closed nested value DTO/enums, exact scalar mapping | Step 1, MC-NAME-001/MC-VALID-001/MC-MODEL-001 |
| Bean/logging/util/JSON/time/config | Current Agent ledger and Jackson | No new Bean/flag/log or dynamic output | Existing exception families and Instant | Step 1, MC-TIME-001/MC-JSON-001 |
| Business variation/pattern | One bounded read and classification | Deterministic evidence table | Direct existing owner query, no state pattern or writer | Step 1, MC-PATTERN-001 N/A |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Current auth/owner | AS TianjiAgentAccess, AR requireRun/modelUsageRun | Same typed owner, existing exact tuple | Reuse predicates, full stored row contains private fields | Minimized read only | No dependency | 1, MC-REUSE-001 |
| Primary read | AR terminalReference/modelUsageRead | Current transaction manager/primary routing | Reuse construction pattern; new explicit transaction read-only guard | Same manager and source | No dependency | 1, MC-VALID-001 |
| Tool proof | V13 invocation, Tool recorder | PK tenant/run/invocation, exact risk/state/effect | Read metadata only | No recorder/provider | No dependency | 1, MC-SCOPE-001 |
| Worker proof | T3-10 WorkerRecoverySpecification and service | Exact already governed PS GET | Existing output fits | Unchanged | None | 1, MC-REUSE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/§8 | Existing named Query/DTO | EvidenceTest → Jdbc repo → ledger | Semantic method/value names | R3/R4 | 1 | PASS |
| Rule 2 | §7/§9 | Existing owner tuple and GetAgentRunQuery | Same | Validate trusted owner/one run and closed evidence | R2/R3 | 1 | PASS |
| Rule 3 | §10 | Existing immutable runtime values | Same | Private row facts and immutable nested DTOs | R3 | 1 | PASS |
| Rule 4 | §6.2 | No Bean or log change | Same | Use existing safe exception boundary | R3/R4 | 1 | N/A |
| Rule 5 | §6/§15 | JDK/standard JdbcTemplate | Same | Existing helpers, no dependency | R3/R4 | 1 | PASS |
| Rule 6 | §9/§10 | V13 and Agent result scalar | Same | SQL scalar exact names, no raw JSON projection | R3 | 1 | PASS |
| Rule 7 | §16 | No config source change | Same | Existing profiles unchanged | R4 | 1 | N/A |
| Rule 9 | §13 | Single deterministic read-only classification | Same | Direct bounded classification, no new workflow | R3 | 1 | N/A |
| Rule 10 | §6.3/§7 | DB clock + Instant | Same | One observation, equality-at-expiry | R3 | 1 | PASS |
| Rule 11 | §6/§8/§11 | Existing AR JDBC owner | All exact paths | Preserve technical package, SQL history and existing event ownership | R4 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt, deadlineAt, leaseExpiresAt | Occurred instant | Instant | UTC instant | Internal | Not applicable: elapsed state | Not applicable: no external display | due when <=, active only when > | one DB clock sample, existing microseconds | existing runtime timestamps unchanged | TEST-001, Step 1 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRecoveryDispositionEvidenceTest.java` | Existing AR owner tests and exact Jdbc mocks | R1/R2/R3 fixtures and assertion matrix | Finite evidence | Step 1 | REQ-001–006 | R1–R4 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java` | modelUsageRun/dueRuns/owner tuple | Guarded primary scalar read + V13 bounded metadata | Exact read-only SQL | Step 1 | REQ-001–003/005 | R2/R3 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | terminalReference/modelUsageRead/requireRun | Internal inspectRecovery and immutable result values | Safe classification | Step 1 | REQ-001–006 | R2–R4 |
| CREATE | `docs/egon/spec/2026-11-05-16-52-agent-recovery-disposition.md` | Exact normalized reviewed Spec | Canonical approved authority | Spec | Step 1 | REQ-001–006 | Strict docs |
| CREATE | `docs/egon/plan/2026-11-05-17-42-agent-recovery-disposition.md` | Exact reviewed Plan | Scope/source lock | Implementation Plan | Step 1 | REQ-001–006 | Strict docs |
| CREATE | `docs/egon/evidence/2026-11-05-agent-recovery-disposition.md` | Existing Tianji evidence convention | Actual owner finite and scope proof | Evidence | Step 1 | REQ-001–006 | R1–R4 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Completed T4-02 source baseline is `6b806e43888ea5e133780f9b755eb610326f52b8`. Reconfirm exact source and finite evidence before execution. Web `f558e02c8d07da2da758c5273a5eb531ca251018` remains untouched. Do not absorb parallel T4-02 source or assume live T3-12 E.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK/finite source reactor | Existing `./scripts/verify-source-reactor.sh`, JDK25 and existing isolated settings | Clean source baseline, exact named reports | No database or Boot listener |
| Owner metadata | Exact existing AR model and V13 | No source migration introduced | Missing optional evidence unavailable, no fallback |
| Existing PS observation | T3-10 WorkerRecoveryService | Reuse only existing exact owner contract and finite tests | No PS source or live call |

### 6.3 Immutable constraints and approved decisions

No schema, migration, route, auth policy, profile, SDK, remote client, tool/provider, recorder, dispatcher, Session or native state change. The query does not trigger read repair. Same owner and source required, never infer action authority from classification.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | Internal response uses nested immutable `AgentRecoveryDispositionDTO` and `AgentRecoveryToolEvidenceDTO` in AgentLedgerService | Same T4-02 internal DTO style | No external shape and no new file/layer | Review against normalized Spec |
| PLAN-CLAR-002 | Two-second snapshot and statement budget | T4-02 owner-local read is bounded to two seconds | Bounded technical safeguard, same owner/read behavior | Timeout means unavailable, no fallback |
| PLAN-CLAR-003 | Treat source/transaction failures as existing dependency error, while known owner + missing Tool evidence keeps run with UNAVAILABLE coverage | Existing notFound and unavailable conventions | No fabricated snapshot, no cross-owner partial content | Any change in public error semantics must return to Spec |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Inspect one owner-local Agent recovery disposition

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006.
- Dependencies: Exact completed T4-02 source lock, normalized T4-03A Spec and Plan review. Root names T4-04 ops as future internal consumer. T3-10 Worker finite source exists.
- Baseline state: Agent GET is exact owner but materializes a full stored run. Model usage has a separate bounded read. No Agent recovery query exists. T3-10 Worker has its own owner read.
- Observable outcome: One `inspectRecovery(TrustedAgentActor,GetAgentRunQuery)` returns a bounded safe disposition from same-owner primary evidence or rejects safely, without modifying facts or allowed actions.
- End state: Three AR Java paths and approved Spec, Plan and actual source evidence in one commit. No AS/C/PS, HTTP, PaC, schema or resume.
- Test-first gate: Required. R1 genuinely fails reflection on existing types. R2 compiles against typed safe stub and genuinely fails ready read assertion. Only then implement final read.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRecoveryDispositionEvidenceTest.java`

- Purpose: Prove exact owner, SQL/transaction minimization and independent failure windows before implementation.
- Symbols: `inspectRecoveryHasInternalSignature`, `readsExactOwnerOnBoundPrimaryWithoutWrites`, `classifiesPersistedLifecycleAndEffectWithoutGuessing`, `keepsBoundedToolCoverageAndUnknownIndependent`, `rejectsForeignLegacyAndUnavailableEvidence`.
- Repository evidence: AR `AgentModelUsageStoreTest` uses same-module JDBC facts and DTOs. Current Agent/Tool/Session and PS Worker finite tests prove existing behavior separately.
- Dependencies and consumers: JUnit, Mockito, current Spring JDBC/transaction types and actual `AgentLedgerService`, `TrustedAgentActor`, `GetAgentRunQuery`. No Boot context, PG or network.
- Why now: R1 can reflect exact signature before new types. After R1, declare only ledger method/DTO signatures in File 3 and run typed R2 before actual repository work. This is a compile prerequisite.
- Contract/signature changes: New finite test only. Mock JdbcTemplate construction and one connection under real transaction template.
- Input/output and state mapping: Fixed AgentDB observation and exact actors. Fake scalar rows for QUEUED, RUNNING current/expired/missing, SUSPENDED known/unknown, deadline equality, terminal known/UNKNOWN/malformed, Tool coverage 0/64/65, masked foreign and optional unavailable. Assert exact same owner predicates and fixed SQL limit/order.
- Error and edge behavior: Assert the only executed JDBC SQL is two fixed transaction-local SET statements and known SELECT. Reject raw result, lease credentials, Tool capabilities, FOR UPDATE, V15 gating or foreign owner lookup. No synthetic writes, checkpoint, external effect or invoked provider.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Existing typed owner and immutable evidence, stable names and Instant, same AR package.
- Implementation pseudocode:

```java
@Test void inspectRecoveryHasInternalSignature() {
    assertDoesNotThrow(() -> AgentLedgerService.class.getMethod("inspectRecovery",
        TrustedAgentActor.class, GetAgentRunQuery.class));
}
@Test void readsExactOwnerOnBoundPrimaryWithoutWrites() {
    arrange exact USER/SERVICE trusted actor, fixed run/Tool scalar rows and fake same-source connection
    actual = assertDoesNotThrow(() -> ledger.inspectRecovery(actor, new GetAgentRunQuery(runId)));
    assert expected lifecycle, Agent effect, coverage and fixed safe fields
    assert same bound DataSource, REPEATABLE_READ, 2s and primary routing before business SELECT
    assert two exact SET guards, one clock, exact owner WHERE and metadata-only selected fields
    assert no JdbcTemplate.update, FOR UPDATE, recorder, expire/suspend or action calls
}
@Test void classifiesPersistedLifecycleAndEffectWithoutGuessing() {
    apply exact table at before/equal/after deadline and lease
    assert terminal and UNKNOWN coexist, absent result stays UNVERIFIED
}
@Test void keepsBoundedToolCoverageAndUnknownIndependent() {
    apply complete and sentinel rows, read-only unknown, write unresolved and local receipt
    assert truncated negatives null, verified positives retained, mismatch unavailable
}
@Test void rejectsForeignLegacyAndUnavailableEvidence() {
    prove foreign and absent identical not-found, no Tool SQL before owned run
    prove legacy/null/ambient refusal, primary failure and unexpected SQL unavailable
}
```

- Verification contribution: R1 missing method and R2 typed positive assertion RED, R3 exact SQL/classification, R4 existing finite regressions.
- After this file: Reflection compiles and is RED. Typed test awaits File 3 compile-only declarations, then records its own assertion RED.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: Return same-owner scalar run/Agent/Tool facts without raw sensitive source fields.
- Symbols: `beginRecoveryObservation()`, `Optional<RecoveryRunRow> recoveryRun(TrustedAgentActor,String)`, `boolean recoveryToolReadable()`, `List<RecoveryToolRow> recoveryToolRows(TrustedAgentActor,String)`, package-local immutable repository rows.
- Repository evidence: Current `run,ownedRunExists,modelUsageRun,dueRuns,databaseNow`; V1/V5 owner tuple and V13 `agent_tool_invocation_pkey`. Current `run` loads `SELECT *`, including private fields.
- Dependencies and consumers: Existing JdbcTemplate and exact AR DataSource, File 3 sole production caller. No new SQL resources, migrations or Store dependency.
- Why now: After typed R2 implement actual scoped scalar data before final ledger projection.
- Contract/signature changes: Package-local read helpers only. One primary DB clock, no row lock, bounded SQL. Tool absence never triggers a second source.
- Input/output and state mapping: Set transaction read-only after owning primary routing and before business SELECT, transaction-local statement timeout 2s. Query one `clock_timestamp()` with `NOT pg_is_in_recovery()` and `current_setting('transaction_read_only')='on'`. Exact owner `tenant_id,subject_type,subject_issuer,subject_id,run_id`, limit 2. Project only run_id/status/revision/attempt/deadline/lease expiry, lease-owner-present and positive-epoch Boolean, cancel request and safe wait reason. Result is a SQL scalar CASE restricted to existing JSON object with string runId/status matching same row, and exact NONE/CONFIRMED/UNKNOWN. Raw result never returned.
- Error and edge behavior: Duplicate exact run is unavailable. Missing/foreign exact run returns empty and stops before Tool catalog. V13 absent or no SELECT means Tool unavailable. Guard ordinary table/no RLS where checking read completeness. If readable, exact tenant/run PK prefix, stable invocation_id order and limit65. Compare full owner and mask other owner in SQL, returning only owner-match Boolean and matched-only state/risk/closed effect/receipt-present Boolean. Unexpected SQL aborts snapshot. No lock, update, repair or fallback.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Existing JDK/JDBC shapes, fixed SQL/Instant and current AR package.
- Implementation pseudocode:

```java
Instant beginRecoveryObservation() {
    jdbc.execute("SET TRANSACTION READ ONLY")
    jdbc.execute("SET LOCAL statement_timeout = '2s'")
    return one primary-only clock sample with PostgreSQL transaction_read_only='on'
}
Optional<RecoveryRunRow> recoveryRun(actor, runId) {
    SELECT safe scalars and closed effect CASE
      FROM tianji_agent.agent_run
      WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
      LIMIT 2
    validate at most one and exact returned run identity
}
boolean recoveryToolReadable() {
    return fixed tianji_agent.agent_tool_invocation ordinary-table catalog and SELECT coverage available
}
List<RecoveryToolRow> recoveryToolRows(actor, runId) {
    SELECT owner_match and matched-only state,risk,effect,receipt_present
      FROM fixed V13 rows scoped tenant/run, ORDER BY invocation_id LIMIT 65
    no raw identifiers, hashes, result, capability or arbitrary diagnostics
}
```

- Verification contribution: R3 exact bound SQL, snapshot, no-write and coverage tests.
- After this file: Repository can supply facts, final ledger projection follows.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: Expose one actual owner-local internal read with independent evidence axes.
- Symbols: `public AgentRecoveryDispositionDTO inspectRecovery(TrustedAgentActor actor, GetAgentRunQuery query)`, nested immutable `AgentRecoveryDispositionDTO`, `AgentRecoveryToolEvidenceDTO`, lifecycle/reason/effect/coverage enums, closed classifier.
- Repository evidence: Current `requireRun`/safe `notFound`, `terminalReference` primary routing, `modelUsageRead` bounded transaction convention, `AgentRunTransitions#terminal`.
- Dependencies and consumers: File 2 exact repository, current transaction manager, C query/actor, finite owner tests and named future T4-04 ops. No AS adapter.
- Why now: Declare compiling method/result before R2 with safe stub only. After R2 and File 2, implement actual query and classification.
- Contract/signature changes: Internal method and immutable result only. Fields runId, revision, attempt, observedAt, lifecycle, fixed reason, agentEffect, toolEvidence, evidenceConflict. ToolEvidence has coverage and four nullable flags per Spec. No raw error/status text, provider result, grant or action fields.
- Input/output and state mapping: Reject null/legacy actor at current verified-owner boundary, invalid query at existing invalid-command, ambient transaction. Use existing manager REPEATABLE_READ timeout2, Spring readOnly=false only to preserve primary routing, then physical read-only in File 2. Find exact owned run before optional Tool. Lifecycle priority terminal, existing due predicate, QUEUED, RUNNING exact valid/missing/expired fence, SUSPENDED closed reason, otherwise UNVERIFIED. Agent effect exact RECORDED_NONE/RECORDED_CONFIRMED/UNKNOWN/UNVERIFIED. Tool verified from first64 plus 65th sentinel. Match V13 exact state/risk/effect/receipt combinations. `hasLocalReceipt` only SUCCEEDED write, CONFIRMED and receipt present; read-only UNKNOWN still sets hasUnknownResult. Invalid/masked rows yield unavailable coverage.
- Error and edge behavior: Known owner plus absent/no-rights Tool source yields UNAVAILABLE and null flags. Truncation retains verified positives true, unproved negatives null. Complete false means absence of matching local fact, not provider absence. Agent NONE + verified unresolved write sets evidenceConflict and preserves both facts. Missing/foreign exact not-found. Unexpected data/transaction failure fixed dependency error, no report. Never call resume, cancel, restore, settlement, provider, recorder or read repair.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Existing owner/query, immutable closed facts and one DB Instant, current technical owner.
- Implementation pseudocode:

```java
// A: exact signatures and immutable response types with safe failing stub.
public AgentRecoveryDispositionDTO inspectRecovery(actor, query) {
    require verified non-LEGACY owner and valid exact run; reject ambient
    read = TransactionTemplate(existing manager), REPEATABLE_READ, timeout 2
    Spring readOnly=false for routing; enforce physical read-only before business SELECT
    read.execute {
        observedAt = repository.beginRecoveryObservation()
        run = repository.recoveryRun(actor, query.runId()).orElseThrow(existing notFound)
        lifecycle, reason = classify using exact source facts and one DB Instant
        agentEffect = closed terminal scalar or UNVERIFIED
        tool = unavailable unless optional source readable
        verify at most 65 rows; classify first64 with sentinel, closed states and owner masks
        return immutable report, explicit conflict, no action
    }
    DataAccessException or TransactionException → fixed dependency error
}
```

- Verification contribution: R2 typed RED then R3/R4 actual owner-scoped finite query and lifecycle.
- After this file: Internal query tested, no new runtime adapter, writer or checkpoint.

#### File 4 — `CREATE docs/egon/spec/2026-11-05-16-52-agent-recovery-disposition.md`

- Purpose: Preserve exact approved source contract and owner boundary.
- Symbols: REQ-001–006, exact internal query, T4-04 future consumer and separate E/S11.
- Repository evidence: Existing service EGON docs and prior deferred candidate.
- Dependencies and consumers: Final normalized reviewed Spec and File 5.
- Why now: Commit actual approved scope with the implementation.
- Contract/signature changes: Documentation only. Lock approved bytes/status and exact Plan link.
- Input/output and state mapping: Final approved Spec bytes to exact service path.
- Error and edge behavior: Prior candidate that deferred projection and any scope mismatch block commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Exact current technical owner and read-only scope.
- Implementation pseudocode:

```text
verify accepted Spec and frozen source locks
create exact service Spec file with approved relationship
after File 5 run both strict validators
```

- Verification contribution: Exact effective Spec and approved source review.
- After this file: Normative authority present, Plan/evidence follow.

#### File 5 — `CREATE docs/egon/plan/2026-11-05-17-42-agent-recovery-disposition.md`

- Purpose: Lock one exact reviewed commit and finite gates.
- Symbols: Approved Plan, R1–R4 and source paths.
- Repository evidence: Current service EGON Plan and T4-02 source boundary.
- Dependencies and consumers: File 4 and final reviewed source/plan bytes.
- Why now: Freeze execution scope and owner before staging.
- Contract/signature changes: Documentation only, canonical relative links.
- Input/output and state mapping: Approved Plan bytes to exact repository path.
- Error and edge behavior: Wrong baseline, relationship, hashes or extra scope blocks staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Exact service owner and live boundary.
- Implementation pseudocode:

```text
verify the approved Plan SHA and exact baseline before creating the canonical file
create the exact Plan from reviewed bytes at the locked service path
validate Spec and Plan strictly from their canonical relative positions
compare the staged paths with the six paths declared for Step 1
```

- Verification contribution: Reproducible ordered scope and actual source boundary.
- After this file: Approved docs linked, actual finite evidence next.

#### File 6 — `CREATE docs/egon/evidence/2026-11-05-agent-recovery-disposition.md`

- Purpose: Record actual finite assertions and scope without a live recovery claim.
- Symbols: R1–R4 reports, exact source/approval, read-only scope and live gates.
- Repository evidence: Existing Tianji finite/independent runtime evidence convention.
- Dependencies and consumers: Current test reports, source diff and strict docs.
- Why now: Record actual observations after both RED and final GREEN.
- Contract/signature changes: Documentation only. No runtime state.
- Input/output and state mapping: Exact selectors, counts, DB-free mock facts, owner scope and unchanged external/SQL inventory. Distinguish S11/T3-12 E and unimplemented transport.
- Error and edge behavior: Missing/skipped owning reports, out-of-scope paths or live assertion without evidence block completion.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Preserve existing data owner and no new write.
- Implementation pseudocode:

```text
capture actual R1/R2 assertion reports and R3/R4 GREEN owner reports
record source SHA, actual scope and read-only evidence
retain S11 and T3-12 E as independent user-controlled gates
```

- Verification contribution: Actual reviewable source completion.
- After this file: Step is complete when tests and exact six paths pass.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Use exact R1–R4 and strict commands in §8.
- Expected result: R1 and R2 one intended assertion failure each with 0 errors/skips. Then all named finite reports actual GREEN, no skipped selector, and exact six paths.
- Failure returns to: File 1 fixture/discovery, File 2 scalar SQL/source, File 3 classification/transaction. A changed contract returns to approved Spec.
- Completion criteria: Actual owner-only no-write internal query, finite windows, exact docs and scope. Live gates stay independent.
- Rollback: Revert only this focused source commit, no DB work.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRecoveryDispositionEvidenceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `docs/egon/spec/2026-11-05-16-52-agent-recovery-disposition.md`, `docs/egon/plan/2026-11-05-17-42-agent-recovery-disposition.md`, `docs/egon/evidence/2026-11-05-agent-recovery-disposition.md`.
- Commit: `feat(agent): inspect owner-local recovery disposition`.

## 8. Test, Validation, and Quality Gates

Run from `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`. Use only exact finite selectors. R1/R2 must record real assertion failures F=1 E=S=0, no startup, discovery error or skipped owning selector. R3/R4 require fresh named JUnit reports, N>0 for each owning class and F=E=S=0.

```sh
t4_03_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
t4_03_finite() {
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$t4_03_settings" -s "$t4_03_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
# Run individually at the declared file/stage point.
t4_03_finite 'AgentRecoveryDispositionEvidenceTest#inspectRecoveryHasInternalSignature'
t4_03_finite 'AgentRecoveryDispositionEvidenceTest#readsExactOwnerOnBoundPrimaryWithoutWrites'
t4_03_finite AgentRecoveryDispositionEvidenceTest
t4_03_finite AgentRecoveryDispositionEvidenceTest,AgentRunReconcilerTest,AgentRunDispatcherTest,ToolInvocationRecorderTest,AdkSessionEventCodecTest,WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,AgentModelUsageStoreTest
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-16-52-agent-recovery-disposition.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-17-42-agent-recovery-disposition.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

No PostgresTest, integration, E, provider, network or packaged application invocation. Scope review also verifies no AS/C/PS source, API metadata, SQL, schema and unrelated change. No additional scratch harness needed: the real finite evidence test and existing wrapper cover all source gates.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or rollout action. Current JDBC/owner mechanisms, existing public API and IAM metadata, scheduler, Tool recorder and PS Worker behavior remain. All existing migration bytes immutable. Revert the one commit if necessary. T4-04 can later consume the internal result only with separately approved authority and presentation. Actual T3-12 E/S11, checkpoint and resume remain independent.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Step | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | §7.3/§9 | 1 | Files 1–3 | R1/R2/R3 | Exact DB time and lifecycle matrix |
| REQ-002 | §7.3/§10 | 1 | Files 1–3 | R3 | Independent Agent effect and Tool evidence flags |
| REQ-003 | §9/§15 | 1 | Files 1–3 | R3/R4 | Owner predicates and existing Worker read behavior |
| REQ-004 | §14 | 1 | Files 1/6 | R3/R4 | Actual finite loss and stale windows |
| REQ-005 | §9/§10/§15 | 1 | Files 1–3/6 | R3/R4 | Read-only SQL, fixed minimized output |
| REQ-006 | §3/§16/§18 | 1 | All | Scope and R4 | No resume/schema/API or runtime action |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-005 | Exact internal projection approved for scope | Step 1 | Canonical Spec and Root T4-04 direction | Decision owner | Final Spec/Plan acceptance required before source |
| DEC-006 | Complete T4-02 source lock | Step 1 | Clean service 6b806e43888ea5e133780f9b755eb610326f52b8 | Root | Closed for review; reconfirm exact lock before source |
| RISK-001 | Tool absence/permissions/source corruption | Step 1 Files 2–3 | V13 optional schema and scoped access | AR owner | Closed unavailable or truncated states; no fallback |
| RISK-002 | Live liveness, checkpoint or retry inferred from snapshot | Step 1 | Existing run lease and T3-12/S11 evidence limits | Runtime owner | Preserve separate live gates; no action output |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Exact owner-only internal read, finite classification and no mutation or external exposure. Existing Worker and Session boundaries remain.

### 12.2 Spec consistency

The Plan uses the canonical reviewed Spec, DEC-005 and DEC-006, and its lifecycle/evidence matrix. No second API or policy decision is embedded.

### 12.3 Repository executability

Exact three AR Java paths and three reviewed docs. Real PostgreSQL V1 column `result` is JSONB, queried only through selected scalars. The final source lock and owning reports precede implementation and commit.

### 12.4 Test and release completeness

R1/R2 require actual assertion failures, R3/R4 exact finite reports. No app/DB/network/skip substitution. No migration or runtime rollout.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/§5 | Existing AR technical owner and C input | None |
| MC-REUSE-001 | Applicable | PASS | §4.5–4.7, Step 1 | Existing exact owner/read boundaries | None |
| MC-DEP-001 | Applicable | PASS | §4/§8 | No dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Named actual Query and immutable DTO | None |
| MC-VALID-001 | Applicable | PASS | Step 1 Files 1–3 | Exact owner, source and evidence scopes | None |
| MC-MODEL-001 | Applicable | PASS | §4.7/Step 1 | Existing C and closed internal evidence | None |
| MC-CONVERT-001 | Not applicable | N/A | Step 1 Files 2–3 | Direct local technical projection | None |
| MC-LOG-001 | Not applicable | N/A | Step 1 File 3 | Existing error boundary; no new log | None |
| MC-BEAN-001 | Not applicable | N/A | §5/§7 | No runtime bean or wiring | None |
| MC-UTIL-001 | Applicable | PASS | §4.7/§8 | Existing JDK, JDBC and JUnit | None |
| MC-JSON-001 | Applicable | PASS | Step 1 File 2 | Closed scalar effect only, no raw JSON | None |
| MC-TIME-001 | Applicable | PASS | §4.9/Step 1 | One DB Instant and exact due comparisons | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/§9 | No new profile or flag | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | One deterministic classification, no state action | None |
| MC-SCOPE-001 | Applicable | PASS | §2/§5/§11 | Canonical DEC-005 internal-only scope and six exact paths | None |
| MC-TEST-001 | Applicable | PASS | §4.2/§8 | Exact finite gates and no runtime | None |
| MC-BLOCKER-001 | Applicable | PASS | §2/§6/§11 | Canonical reviewed Spec and final T4-02 source locked for review | Final approval before source |

**PASS — Ready for user review**

This is a Plan candidate for review only. No source, Maven, database, application or runtime action occurred in Plan authoring.
