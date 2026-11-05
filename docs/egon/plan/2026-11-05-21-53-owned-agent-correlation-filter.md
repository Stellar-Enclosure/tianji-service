# T4-02 当前 Agent owner 精确关联筛选实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-21-53-owned-agent-correlation-filter.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 21:53 Asia/Shanghai |
| Updated | 2026-11-05 21:53 Asia/Shanghai |
| Owner | Tianji Agent |
| Repository | tianji-service |
| Scope | 既有 Agent LIST 的一个 opt-in filter，8 个 C/AR/AS 代码/测试/资源和三份文档 |
| Source Requirement | Master §13.3、§17.2 S12、§19 T4-02 |
| Baseline Revision | Read-only observed af504a47f5251bfd96528bab7568983ca3a02516; re-lock after independent snapshot commit before Source GO |
| Implements Spec | [Owned Agent correlation filter](../spec/2026-11-05-21-52-owned-agent-correlation-filter.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 21:52 Asia/Shanghai, SHA-256 a17c0c0496c250260d4c042d4a8b27c824b7a3ed576e3e46090c7337edf1d0b6 |
| Effective Specs | [Owned Agent correlation filter](../spec/2026-11-05-21-52-owned-agent-correlation-filter.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master T4-02](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

保持现有 LIST URI、权限、C DTO 和未筛选行为。C typed filter + AR exact owner 非用量主库读 + AS optional branch。一个逻辑 backend 提交，实际 producer 后才接 Web。

## 2. Target Spec and Effective Design

Spec §§7/9/10/14 定义 exact owner、窗口、旧契约、绑定 cursor、静态 OAS 及独立 I/E。没有跨 owner Agent 访问、发布或 grant。来源实际锁定后再给 Source GO。

## 3. Effective Requirements and Acceptance

| ID | Acceptance |
| --- | --- |
| REQ-001 | exact existing LIST authority and unfiltered old branch |
| REQ-002 | same-owner agent_run only, UTC ≤31d, primary 2s, max20+1 |
| REQ-003 | v1 ≤256 scope digest, exact member and uniform 400 |
| REQ-004 | actual finite and Boot OAS, zero declarative PaC and SQL delta, runtime separate |

## 4. Implementation Strategy and Dependency Order

R1 missing contract; R2 typed A no-op repository outcome; R3 exact existing route partial filter passes legacy behavior on baseline and must be rejected in B. Final C → AR → AS → static OAS. No live or Web changes.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Authority | Current LIST permission guard | Preserve exact policy and owner tuple |
| Storage | AgentDB V1/V5 and AR owner queries | One existing table and same primary |
| DTO | Existing AgentRunPageDTO | New typed query only and old response |
| Docs | Existing contract-first OpenAPI | Static AS artifact only |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Discovery | Existing Agent LIST and private usage query | Existing correlation scalar but no public exact filter | Existing list sufficient with optional branch | Reuse |
| Bound read | Existing AR recovery physical observation | Existing safe owner DB route and limits | Reuse shared local observation only | Modify |
| Cursor | JDK and AgentJsonCodec hash | Current owner identity digest | deterministic navigation without signer | Reuse |
| Tests | Existing C/AR/AS finite and wrapper | exact owner and MockMvc | no DB/listener required | Reuse |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Current C/AR/AS | §7 exact files | Exact Query and existing DTO/adapter names | G1/P1/S1 | 1 | PASS |
| Rule 2 | §6.2 | Current C/AR/AS | §7 exact files | Exact parameters, verified actor and bounded cursor | G1/P1/S1 | 1 | PASS |
| Rule 3 | §6.2 | Current C/AR/AS | §7 exact files | Immutable value Query, existing page DTO | G1/P1/S1 | 1 | PASS |
| Rule 4 | §6.2 | Current C/AR/AS | §7 exact files | Existing named Spring owner boundary | G1/P1/S1 | 1 | PASS |
| Rule 5 | §6.2 | Current C/AR/AS | §7 exact files | Existing JDK and AgentJsonCodec hash | G1/P1/S1 | 1 | PASS |
| Rule 6 | §6.2 | Current C/AR/AS | §7 exact files | Existing Jackson and static OAS | G1/P1/S1 | 1 | PASS |
| Rule 7 | §6.2 | Current C/AR/AS | §7 exact files | No profile/config change | G1/P1/S1 | 1 | N/A |
| Rule 9 | §6.2 | Current C/AR/AS | §7 exact files | Direct existing query adapter | G1/P1/S1 | 1 | PASS |
| Rule 10 | §6.2 | Current C/AR/AS | §7 exact files | UTC Instant and native OffsetDateTime | G1/P1/S1 | 1 | PASS |
| Rule 11 | §6.2 | Current C/AR/AS | §7 exact files | Existing owner JDBC technical boundary and no DDL | G1/P1/S1 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive and createdAt cursor | Occurred instant | Instant and JDBC OffsetDateTime | Not persisted | UTC Z | Explicit UTC | UTC wire | Gregorian and half-open ≤31d | Micros, DB upper bound | Supports JS .000Z and exact historical years | Step 1 TEST-001 and TEST-002 |
| TIME-002 | Existing Agent run createdAt and database observation | Occurred instant | Instant and JDBC OffsetDateTime | UTC instant | UTC Z | Explicit UTC | UTC wire | One exact current primary snapshot | micros and DB clock | Same existing public summary | Step 1 TEST-002 and TEST-003 |

## 5. Change File Tree

| Operation | Exact path | Purpose | Gate |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/agent/AgentContractTest.java` | C typed contract test | R1/G1 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunCorrelationLookupTest.java` | AR owner read test | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpTest.java` | AS LIST transport test | R3/G1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/ListAgentRunsByCorrelationQuery.java` | New C filter Query | R1/R2/G1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java` | Existing AR receipt query | G1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | Existing AR ledger adapter | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunController.java` | Optional existing route branch | R3/G1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-run-correlation-v1.json` | Contract-first static AS OAS | P1 |
| CREATE | `docs/egon/spec/2026-11-05-21-52-owned-agent-correlation-filter.md` | Approved design | S1 |
| CREATE | `docs/egon/plan/2026-11-05-21-53-owned-agent-correlation-filter.md` | Approved execution | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-owned-agent-correlation-filter.md` | Actual proof | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Re-lock exact clean Service HEAD after approved independent snapshot task and verify its diff has no overlap with the eight Agent paths. The observed pre-snapshot baseline is af504a47f5251bfd96528bab7568983ca3a02516. Lock 27 existing SQL, previous actual AS PaC at t4-02/process-correlation/root/agent-server-manifest.json SHA 4dea8055697cffe9744696c3c204d006cf05a88b57d90b841999002bf445874e, and no Web change. Existing cached JDK25/settings only. Source phases under /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/agent-correlation; root owns repo. No dependencies, grant, listener, GUI or runtime.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add bounded exact Agent correlation to existing LIST

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Reviewed Spec/Plan, source GO, exact clean re-lock after snapshot.
- Baseline state: Existing current-owner LIST, no exact correlation branch.
- Observable outcome: Existing callers use same route/authority and opt into exact owner discovery.
- End state: 8 exact source/test/resource paths and three docs, one commit.
- Test-first gate: Required. R1 missing Query; R2 typed positive against inert empty A; R3 incomplete filtered request takes baseline old 200 instead of 400.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/agent/AgentContractTest.java`

- Purpose: C typed contract test.
- Symbols: AgentContractTest#correlation_query_contract_exists.
- Repository evidence: Existing JDK C immutable records.
- Dependencies and consumers: Current C/AR/AS owner plus exact R1/G1 source phase.
- Why now: This file establishes the exact R1/G1 phase and its owning behavior.
- Contract/signature changes: AgentContractTest#correlation_query_contract_exists, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
reflect the exact query class and expected component names before it exists;
after B require strict exact text, Gregorian UTC and positive bounded window;
preserve old ListAgentRunsQuery and AgentRunPageDTO assertions;
```

- Verification contribution: R1/G1.
- After this file: Owning source or evidence can enter the declared R1/G1.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunCorrelationLookupTest.java`

- Purpose: AR owner read test.
- Symbols: AgentRunCorrelationLookupTest#reads_only_the_exact_owner_on_the_bound_primary.
- Repository evidence: Existing same-primary fake JDBC and AgentRecoveryDispositionEvidenceTest patterns.
- Dependencies and consumers: Current C/AR/AS owner plus exact R2/G1 source phase.
- Why now: This file establishes the exact R2/G1 phase and its owning behavior.
- Contract/signature changes: AgentRunCorrelationLookupTest#reads_only_the_exact_owner_on_the_bound_primary, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
call typed listByCorrelation for an owned known row;
expect one verified result where A returns empty;
assert owned primary read-only, four predicates, exact window/order, cursor anchor and no V15 access;
```

- Verification contribution: R2/G1.
- After this file: Owning source or evidence can enter the declared R2/G1.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpTest.java`

- Purpose: AS LIST transport test.
- Symbols: AgentRunHttpTest#rejects_incomplete_agent_correlation_filter_before_ledger_access.
- Repository evidence: Existing Agent LIST MockMvc and permission fixture.
- Dependencies and consumers: Current C/AR/AS owner plus exact R3/G1 source phase.
- Why now: This file establishes the exact R3/G1 phase and its owning behavior.
- Contract/signature changes: AgentRunHttpTest#rejects_incomplete_agent_correlation_filter_before_ledger_access, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
GET existing LIST with only correlationId and expect 400; baseline old branch returns 200;
test old defaults/limit/cursor unchanged and complete exact filter after current LIST authorization;
verify unauthorized or malformed new fields do not touch ledger;
```

- Verification contribution: R3/G1.
- After this file: Owning source or evidence can enter the declared R3/G1.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/ListAgentRunsByCorrelationQuery.java`

- Purpose: New C filter Query.
- Symbols: ListAgentRunsByCorrelationQuery and parseUtc.
- Repository evidence: ContractChecks.contextIdentifier and immutable Agent queries.
- Dependencies and consumers: Current C/AR/AS owner plus exact R1/R2/G1 source phase.
- Why now: This file establishes the exact R1/R2/G1 phase and its owning behavior.
- Contract/signature changes: ListAgentRunsByCorrelationQuery and parseUtc, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
validate exact safe broad Agent correlation and optional v1 cursor <=256;
parse strict UTC Gregorian Z and 1–6 optional fractional digits, micros only;
validate lower before upper and duration no more than 31 days;
```

- Verification contribution: R1/R2/G1.
- After this file: Owning source or evidence can enter the declared R1/R2/G1.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: Existing AR receipt query.
- Symbols: JdbcAgentLedgerRepository beginPrimaryReadObservation, hasCorrelationAnchor, correlationRuns.
- Repository evidence: Existing primary read and RUN_COLUMNS / owner queries.
- Dependencies and consumers: Current C/AR/AS owner plus exact G1 source phase.
- Why now: This file establishes the exact G1 phase and its owning behavior.
- Contract/signature changes: JdbcAgentLedgerRepository beginPrimaryReadObservation, hasCorrelationAnchor, correlationRuns, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
reuse physical primary observation, retain old recovery error wrapper;
bind exact full owner, correlation COLLATE C, UTC half-open, optional scoped anchor;
select only agent_run and existing summary plus internal correlation; C order and limit 21;
```

- Verification contribution: G1.
- After this file: Owning source or evidence can enter the declared G1.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: Existing AR ledger adapter.
- Symbols: AgentLedgerService#listByCorrelation.
- Repository evidence: Existing owner list, transaction manager and AgentJsonCodec hash.
- Dependencies and consumers: Current C/AR/AS owner plus exact R2/G1 source phase.
- Why now: This file establishes the exact R2/G1 phase and its owning behavior.
- Contract/signature changes: AgentLedgerService#listByCorrelation, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
A returns an empty typed page and never touches JDBC;
B validates current owner and no ambient state, binds cursor to exact normalized query and checks member;
use same primary RR/2s, validate row/sentinel, cap 20 and derive continuation from last accepted row;
```

- Verification contribution: R2/G1.
- After this file: Owning source or evidence can enter the declared R2/G1.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunController.java`

- Purpose: Optional existing route branch.
- Symbols: AgentRunController#list with exact raw MultiValueMap.
- Repository evidence: Existing LIST annotation, int/String binders, current access and no-store.
- Dependencies and consumers: Current C/AR/AS owner plus exact R3/G1 source phase.
- Why now: This file establishes the exact R3/G1 phase and its owning behavior.
- Contract/signature changes: AgentRunController#list with exact raw MultiValueMap, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```java
require existing LIST resource and current actor before inspecting optional filter fields;
if none present use old cursor/int limit unchanged;
otherwise require exact trio once plus optional cursor, forbid limit, call typed ledger branch and noStore;
```

- Verification contribution: R3/G1.
- After this file: Owning source or evidence can enter the declared R3/G1.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-run-correlation-v1.json`

- Purpose: Contract-first static AS OAS.
- Symbols: agent-run-correlation-v1.json and operationId listOwnedAgentRuns.
- Repository evidence: Existing static packaged OpenAPI precedent.
- Dependencies and consumers: Current C/AR/AS owner plus exact P1 source phase.
- Why now: This file establishes the exact P1 phase and its owning behavior.
- Contract/signature changes: agent-run-correlation-v1.json and operationId listOwnedAgentRuns, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```json
document existing GET and optional branch with same resource and READ scopes;
describe old vs filtered parameters, fixed 20, current DTO/errors and uniform cursor failures;
require exact Boot artifact bytes and unchanged actual PaC declarations;
```

- Verification contribution: P1.
- After this file: Owning source or evidence can enter the declared P1.

#### File 9 — `CREATE docs/egon/spec/2026-11-05-21-52-owned-agent-correlation-filter.md`

- Purpose: Approved design.
- Symbols: Canonical Spec and exact REQ-001–004.
- Repository evidence: Master T4-02 and actual source authority.
- Dependencies and consumers: Current C/AR/AS owner plus exact S1 source phase.
- Why now: This file establishes the exact S1 phase and its owning behavior.
- Contract/signature changes: Canonical Spec and exact REQ-001–004, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```text
copy reviewed Spec at its exact canonical path and lock its digest;
check no new route/grants, existing source fields and narrowed owner queries;
run strict Spec validation against canonical relative links;
```

- Verification contribution: S1.
- After this file: Owning source or evidence can enter the declared S1.

#### File 10 — `CREATE docs/egon/plan/2026-11-05-21-53-owned-agent-correlation-filter.md`

- Purpose: Approved execution.
- Symbols: Canonical Plan and R1–R3/G1/P1/S1.
- Repository evidence: Existing source reactor and static proof procedure.
- Dependencies and consumers: Current C/AR/AS owner plus exact S1 source phase.
- Why now: This file establishes the exact S1 phase and its owning behavior.
- Contract/signature changes: Canonical Plan and R1–R3/G1/P1/S1, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```text
copy exact reviewed Plan and lock linked Spec hash and post-snapshot HEAD;
verify exact source phases, actual tests and current artifact policy;
stage eleven paths once after final evidence checks;
```

- Verification contribution: S1.
- After this file: Owning source or evidence can enter the declared S1.

#### File 11 — `CREATE docs/egon/evidence/2026-11-05-owned-agent-correlation-filter.md`

- Purpose: Actual proof.
- Symbols: Canonical EVD.
- Repository evidence: Current source and artifact report conventions.
- Dependencies and consumers: Current C/AR/AS owner plus exact S1 source phase.
- Why now: This file establishes the exact S1 phase and its owning behavior.
- Contract/signature changes: Canonical EVD, constrained to this existing owner LIST.
- Input/output and state mapping: See exact local behavior and boundaries below.
- Error and edge behavior: Keep legacy default and fixed errors, deny invalid owner/cursor, no business writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves the current owner, technology and persistence boundaries.
- Implementation pseudocode:

```text
record exact R1–R3 reports and G1/P1 actual counts, source and artifact hashes;
prove no new authority or data source, 27 migrations unchanged and 11 scoped paths;
keep runtime, Web/SDK, cross-owner join and S12 independent;
```

- Verification contribution: S1.
- After this file: Owning source or evidence can enter the declared S1.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/R2/R3/G1/P1/S1.
- Expected result: exactly one owning assertion per RED, zero errors/skips. Actual core and package pass.
- Failure returns to: exact source gate without permission/runtime expansion.
- Completion criteria: Actual finite and Boot OAS/zero PaC, immutable SQL and exact 11 staged paths.
- Rollback: Before commit withdraw only exact task changes. After commit separate approved focused revert.
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/agent/AgentContractTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunCorrelationLookupTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/ListAgentRunsByCorrelationQuery.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunController.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-run-correlation-v1.json`, `docs/egon/spec/2026-11-05-21-52-owned-agent-correlation-filter.md`, `docs/egon/plan/2026-11-05-21-53-owned-agent-correlation-filter.md`, `docs/egon/evidence/2026-11-05-owned-agent-correlation-filter.md`
- Commit: feat(agent): filter owned runs by exact correlation.

## 8. Test, Validation, and Quality Gates

| Gate | Exact source phase | Result |
| --- | --- | --- |
| R1 | source-draft/red/AgentContractTest.java only | missing typed query, assertion only |
| R2 | source-draft/a C/ledger/owning test, original repo data path | typed safe empty misses expected same-owner read |
| R3 | root/r3/AgentRunHttpTest.java, controller baseline | only correlationId param, actual old 200 vs expected 400 |
| G1 | Final C/AR/AS B, exact finite owning selector | each owner fresh reports, no failures/errors/skips |
| P1 | Actual clean offline permission-producer | packaged AS OAS, zero PaC authority delta and old SQL |
| S1 | Strict docs, staged exact scope | one clean focused commit |

```sh
agent_corr_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
agent_corr_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
# Set from the exact clean post-snapshot source lock before executing.
test "$(git rev-parse --verify HEAD)" = "$agent_corr_base"
agent_corr_core=AgentContractTest,AgentRunCorrelationLookupTest,AgentRunHttpTest,TianjiPermissionIntegrationTest,AgentModelUsageDiagnosticsTest,AgentRecoveryDispositionEvidenceTest
agent_corr_finite() {
  JAVA_HOME="$agent_corr_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$agent_corr_settings" -s "$agent_corr_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
agent_corr_finite 'AgentContractTest#correlation_query_contract_exists'
agent_corr_finite 'AgentRunCorrelationLookupTest#reads_only_the_exact_owner_on_the_bound_primary'
agent_corr_finite 'AgentRunHttpTest#rejects_incomplete_agent_correlation_filter_before_ledger_access'
agent_corr_finite "$agent_corr_core"
JAVA_HOME="$agent_corr_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$agent_corr_settings" -s "$agent_corr_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$agent_corr_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-agent-correlation-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$agent_corr_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-21-52-owned-agent-correlation-filter.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-21-53-owned-agent-correlation-filter.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

Execute each R command only at the declared phase. Require owning actual assertion evidence, no opt-in Postgres/IT or listener. P1 read actual AS exec, unique BOOT-INF/classes/META-INF/tianji/openapi/agent-run-correlation-v1.json matching source bytes, exact existing operation/resource/old and filter schemas. Compare actual AS PaC against the retained current producer manifest with no resource/permissions/references/messages/policy delta, apart from build metadata. Existing default-off config and 27 SQL remain byte-identical. Existing wrapper and inline static archive check cover the gates.

## 9. Migration, Compatibility, Rollout, and Rollback

No POM/config/SQL/grant. Existing LIST default and result. Runtime activation, PG/I, SDK/Web and S12 separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | 1 | R3/G1/P1 |
| REQ-002 | 1 | R1/R2/G1 |
| REQ-003 | 1 | R2/G1 |
| REQ-004 | 1 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

No cross-owner match or real SQL plan claim. Same owner correlation is only discovery. Future Web must check existing Agent READ independently and not imply Process relation.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | Existing C/AR/AS boundary | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | Existing LIST, digest and physical read | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | Exact Query names | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | Current verified owner and bounded query | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | Immutable typed values | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | No new entity mapping | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | No new log | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Reuse existing Spring bean and controller | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing JDK and hash | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 | Closed OAS, same public DTO | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | Strict UTC micros | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14 | No profile/config changes | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Direct owner query composition | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | Same GET and scope, exact paths | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Exact source and actual artifact gates | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Source scope and independent runtime | None |

**PASS — Ready for user review**
