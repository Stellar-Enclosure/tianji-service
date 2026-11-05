# T4-03 Protected historical START receipt execution plan

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-56-protected-historical-start-receipt.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:56 Asia/Shanghai |
| Updated | 2026-11-06 06:56 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | One default-off protected historical receipt POST |
| Source Requirement | Master §19 T4-03 and S11 |
| Baseline Revision | a082ab1d7b98cc524e9e5880075aba6defccfa78 |
| Implements Spec | [Protected historical receipt Spec](../spec/2026-11-06-06-55-protected-historical-start-receipt.md) |
| Spec Status | Review |
| Spec Revision | SHA-256 81550275a15d6917dd49827ac738969dc225403384fd3e1f7bf35a71022445a7 |
| Effective Specs | [Protected historical receipt Spec](../spec/2026-11-06-06-55-protected-historical-start-receipt.md) |
| Depends On Plans | [AS historical host](2026-11-06-06-07-historical-start-receipt-as-host.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Add one explicit default-off protected USER historical receipt action using current Jianshen twice. Keep existing AR positive append and original Process READ.

## 2. Target Spec and Effective Design

The [Spec](../spec/2026-11-06-06-55-protected-historical-start-receipt.md) binds the dedicated route to exact source and same authority. Runtime registration and independently granted READ stay separate.

## 3. Effective Requirements and Acceptance

| Requirement | Spec owner | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Exact dormant protected USER POST and explicit composition |
| REQ-002 | §7/9 | Two fresh exact request-bound decisions and no stale authority |
| REQ-003 | §7/11 | Existing AR exact source and Process evidence, no change to original dispatch |
| REQ-004 | §8/9/14 | R1, 34 finite tests, actual manifest and static OpenAPI |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Freeze source, prove isolated revocation RED, assemble the adapter, run owning finite and package verification, and commit only listed paths.

### 4.2 Test-first strategy

Use the typed fixture with real guard and controlled SQL-owner snapshots. R1 replaces only the later authority refresh with a stale fabricated local result. A revoked second permit must produce one expected failure. Restore the fresh guard before all GREEN checks.

### 4.3 Sequential and parallel boundaries

One AS Step. Rebase exact central matcher and YAML additions after any previous RESUME route commit. Preserve prior protected endpoints and permissions.

### 4.4 Commit boundaries

One focused AS commit after strict docs, tests and actual package. Runtime remains separate.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Step decision |
| --- | --- | --- | --- |
| Independent authority and route | Add §7/9 | EVD-001–005 | Use exact USER scope and current guard |
| Existing receipt implementation | Reuse §7/11 | EVD-001/002 | Keep unchanged evidence semantics |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependency |
| --- | --- | --- | --- |
| Authority and exact host | REQ-002/003 | Revocation R1 and finite tests | Existing guard and host |
| HTTP and package | REQ-001/004 | G1/P1/S1 | Same exact historical authority |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Configuration | Existing AS Spring MVC flags and ordinary shell | One explicit default-off route |
| Validation | Existing AgentApiErrors and Jackson | Closed 512-byte body and safe responses |
| Persistence | Existing AR positive evidence | No SQL change |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Current independent authority | PermissionExecutionGuard and TianjiAgentAccess | EVD-003 | Existing current authority on same servlet thread | Reuse | Scoped adapter only | R1/G1 |
| Append and read | Existing historical host and AR command | EVD-001/002 | Need identity validation at composition | Reuse | Small identity check | G1/P1 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/7 | EVD-001–005 | §5/7 | Keep the dedicated technical servlet/authority/permissions and immutable command record names. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5/7 | Check owned exact request, bounded shape and current verified authority at the affected servlet handoff. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5/7 | Reuse existing immutable ReceiptVO and small private immutable command/context records. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5/7 | Name each technical bean and qualify dependencies, using the existing explicit technical MVC constructor pattern. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5/7 | Use JDK, Spring and existing project dependencies with no new utilities. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5/7 | Use strict existing Jackson3 and static closed HTTP JSON contract. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5/7 | Keep existing hierarchy and add one deliberate default-false feature and independent scope mapping. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5/7 | Use an Adapter and one scoped request authority with existing AR command orchestration. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5/7 | Use Clock and Instant for UTC permits and existing receipt instants. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5/7 | Keep AS/AR/PS responsibilities and existing transaction/SQL and Event boundaries. | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Permit checkedAt and expiresAt; recordedAt and observedAt | Occurred instant | java.time.Clock and Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Limit permit by USER, fresh policy and command deadline | Existing clock and AR microsecond append cutoff | Existing historical semantics | Step 1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/AgentHistoricalStartReceiptControllerTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiHistoricalStartReceiptPermissions.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServletHistoricalStartReceiptAuthority.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiProtectedHistoricalStartReceiptConfiguration.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptController.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-historical-start-receipt-v1.json` |
| CREATE | `docs/egon/spec/2026-11-06-06-55-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/plan/2026-11-06-06-56-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/evidence/2026-11-06-protected-historical-start-receipt.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact baseline a082ab1d7b98cc524e9e5880075aba6defccfa78. Existing offline JDK25 runner, prior AR/host commits, and static OAS artifact. Runtime configuration, IAM and PostgreSQL remain outside this Step.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Protect exact historical receipt reconciliation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing historical host and AR reconciliation.
- Baseline state: Historical reconciliation has an explicit Java host and no servlet command.
- Observable outcome: One default-off USER POST delegates to same authority and existing positive append.
- End state: Nine source paths and three review documents.
- Test-first gate: Required. R1 exact revoked second permit test.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/AgentHistoricalStartReceiptControllerTest.java`

- Purpose: Verify current authorization and exact dormant servlet contract with finite in-process tests.
- Symbols: AgentHistoricalStartReceiptControllerTest.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Verify current authorization and exact dormant servlet contract with finite in-process tests.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Set up existing guard and controlled snapshot loader with exact historical permission.
Drive finite MockMvc commands and verify pre/post evidence revocation with typed negative.
Check default servlet shell, exact scope and closed input/error cases.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiHistoricalStartReceiptPermissions.java`

- Purpose: Declare one independent protected historical receipt permission and stable route constants.
- Symbols: TianjiHistoricalStartReceiptPermissions.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Declare one independent protected historical receipt permission and stable route constants.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Declare exact resource and permission constants as immutable values.
Declare exact USER scope and localized labels with existing PaC annotations.
Share the endpoint and enabled property constants with mapper and MVC gate.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServletHistoricalStartReceiptAuthority.java`

- Purpose: Use existing SQL-backed current guard twice within exact scoped USER request.
- Symbols: TianjiServletHistoricalStartReceiptAuthority.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Use existing SQL-backed current guard twice within exact scoped USER request.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Require exact selected POST and dedicated current original USER.
Pin request identity, owner, token, Run and intent before calling host.
Refresh current guard twice and bound each permit by snapshot, USER and AR deadline.
Clear scoped state in finally and fail on wrong identity or ambient transaction.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java`

- Purpose: Expose historical authority object identity for protected wiring validation.
- Symbols: TianjiHistoricalStartReceiptConfiguration.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Expose historical authority object identity for protected wiring validation.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Retain the supplied HistoricalToolReceiptAuthorization object on the owned host.
Expose only reference identity comparison for the new controller.
Preserve existing Java host behavior, independent READ adapter and owned close.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiProtectedHistoricalStartReceiptConfiguration.java`

- Purpose: Compose explicit protected host with exact authority and operator-retained START/READ inputs.
- Symbols: TianjiProtectedHistoricalStartReceiptConfiguration.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Compose explicit protected host with exact authority and operator-retained START/READ inputs.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Gate explicit import by servlet API, storage and historical flag.
Build one authority and pass the same instance to the retained host binding.
Require exact externally supplied original fixed bindings, isolated READ OAuth and alias map.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptController.java`

- Purpose: Parse a single closed POST and return only a safe exact result or typed unknown.
- Symbols: AgentHistoricalStartReceiptController.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Parse a single closed POST and return only a safe exact result or typed unknown.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Read at most 513 bytes and refuse malformed, duplicate or surplus input.
Decode exact Run and canonical UUID, enter dedicated authority scope, invoke host.
Verify exact positive response identity and translate only unknown to safe 409.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java`

- Purpose: Admit only an exact enabled USER POST outside shared principal patterns.
- Symbols: TianjiAgentSecurityConfiguration.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Admit only an exact enabled USER POST outside shared principal patterns.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Keep all old route matchers and default denial intact.
Add exact POST matcher for the new path guarded by enabled API/storage/feature and authenticated USER.
Keep endpoint outside existing mixed Shoubing route prefixes.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Publish one default-off property and its dedicated existing user-scope mapping.
- Symbols: application.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Publish one default-off property and its dedicated existing user-scope mapping.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Insert one explicit default-false historical property under existing recovery hierarchy.
Map independent permission to exact existing new scope semantics.
Do not modify any prior property or runtime grant values.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 9 — `CREATE stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-historical-start-receipt-v1.json`

- Purpose: Package contract-first OpenAPI with exact permission, schema and safe error codes.
- Symbols: agent-historical-start-receipt-v1.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Package contract-first OpenAPI with exact permission, schema and safe error codes.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Record one USER-only POST with operation ID, bounded command and no-store response.
Declare exact permission/resource/scope and safe known response codes.
Place under existing META-INF/tianji/openapi convention for actual Boot packaging.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 10 — `CREATE docs/egon/spec/2026-11-06-06-55-protected-historical-start-receipt.md`

- Purpose: Record protected HTTP contract and current authorization evidence boundary.
- Symbols: 2026-11-06-06-55-protected-historical-start-receipt.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Record protected HTTP contract and current authorization evidence boundary.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Record exact baseline, independent privilege and historical command contract.
Trace current source, bounded current proof and actual runtime gate.
Apply contract-first PaC and finite test requirements.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 11 — `CREATE docs/egon/plan/2026-11-06-06-56-protected-historical-start-receipt.md`

- Purpose: Lock exact file ordering, typed RED and finite/package gates.
- Symbols: 2026-11-06-06-56-protected-historical-start-receipt.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Lock exact file ordering, typed RED and finite/package gates.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Lock baseline with exact source and review paths.
Record typed revocation mutation, restore before owning clean tests.
Audit scope and preserve all existing migration bytes before commit.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

#### File 12 — `CREATE docs/egon/evidence/2026-11-06-protected-historical-start-receipt.md`

- Purpose: Record observed proof and independent deployment acceptance.
- Symbols: 2026-11-06-protected-historical-start-receipt.
- Repository evidence: Spec EVD-001–005 and exact current baseline.
- Dependencies and consumers: Existing AS receipt reconciliation and protected servlet callers.
- Why now: Record observed proof and independent deployment acceptance.
- Contract/signature changes: One protected AS action, existing callers preserved.
- Input/output and state mapping: Keep exact source evidence and separate current authorization.
- Error and edge behavior: Fail closed on malformed command, stale authority or absent positive evidence.
- Standards impact: MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10 and Rule 11.
- Implementation pseudocode:

```text
Capture actual finite and package results and exact scope.
Record sanitized witness metadata and source/production difference.
Keep separate remaining live IAM, HTTPS and PostgreSQL acceptance.
```

- Verification contribution: R1/G1/P1/S1 for exact scope.
- After this file: Continue only within the same declared Step.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service
- Verification command: §8 exact owning commands.
- Expected result: One intended R1 and 34 finite tests with package/PaC scope checks.
- Failure returns to: Exact source/test or package difference, then rerun Step gates.
- Completion criteria: Strict docs, finite tests, actual AS archive, all existing SQL bytes and 12 exact paths.
- Rollback: Revert this one commit or keep the new property disabled.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/AgentHistoricalStartReceiptControllerTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiHistoricalStartReceiptPermissions.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServletHistoricalStartReceiptAuthority.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiProtectedHistoricalStartReceiptConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptController.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-historical-start-receipt-v1.json`, `docs/egon/spec/2026-11-06-06-55-protected-historical-start-receipt.md`, `docs/egon/plan/2026-11-06-06-56-protected-historical-start-receipt.md`, `docs/egon/evidence/2026-11-06-protected-historical-start-receipt.md`.
- Commit: feat(agent): protect historical START receipt reconciliation.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | Bypass only second-phase fresh guard call by replacing fresh assignment with synthetic old owner authorization | One intended HTTP 403 versus 200 assertion after revocation |
| G1 | Finite owning reactor | 34 tests across five suites and no unexpected errors |
| P1 | Clean permission producer and artifact comparison | 34 repeated, AS new resource/permission, OAS and default false, PS semantics unchanged |
| S1 | Exact scope | 12 paths, strict docs and 28 original migration bytes |

```sh
historical_base=a082ab1d7b98cc524e9e5880075aba6defccfa78
historical_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
historical_red='AgentHistoricalStartReceiptControllerTest#revocation_after_process_read_refuses_the_second_permit'
historical_core='AgentHistoricalStartReceiptControllerTest,TianjiSecurityConfigurationTest,TianjiHistoricalStartReceiptConfigurationTest,TianjiFixedBpmnStartReceiptReaderTest,ToolStartReceiptReconciliationServiceTest'
test "$(git rev-parse --verify HEAD)" = "$historical_base"
# Run against the single typed stale-authority variant, then restore.
"$historical_runner" "$historical_red"
"$historical_runner" "$historical_core"
STELLAR_VERIFY_GOAL=verify "$historical_runner" "$historical_core" clean -Ppermission-producer -Dpermission.commit="$historical_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-protected-historical-start-receipt
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-55-protected-historical-start-receipt.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-56-protected-historical-start-receipt.md
git diff --check
```

Actual IAM, HTTPS, Process receipt READ enrollment and PostgreSQL require separate operator authorization and evidence.

## 9. Migration, Compatibility, Rollout, and Rollback

The prior Java host and SQL remain. The operator keeps default disabled or explicitly imports and provisions the exact route policy. Revert one commit for source rollback.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Verification |
| --- | --- | --- |
| REQ-001 | Step 1 | G1/P1 |
| REQ-002 | Step 1 | R1/G1 |
| REQ-003 | Step 1 | G1/P1 |
| REQ-004 | Step 1 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Runtime owner registration and dedicated grants, exact retained READ alias and S11 are independent. No live startup in this Step.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7–9/14 | Existing AS protected adapter and AR/PS evidence ownership. | None |
| MC-REUSE-001 | Applicable | PASS | §7–9/14 | Reuse PermissionExecutionGuard, host and existing reconciliation. | None |
| MC-DEP-001 | Applicable | PASS | §7–9/14 | Use existing JDK/Spring/Jackson/JUnit with no dependency. | None |
| MC-NAME-001 | Applicable | PASS | §7–9/14 | Technical servlet, configuration and semantic historical authority. | None |
| MC-VALID-001 | Applicable | PASS | §7–9/14 | Exact Run, intent, current owner and two fresh guard checks. | None |
| MC-MODEL-001 | Applicable | PASS | §7–9/14 | Reuse immutable receipt, identity and request DTOs. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7–9/14 | Existing unchanged ReceiptVO only. | None |
| MC-LOG-001 | Applicable | PASS | §7–9/14 | No credential or body echo. | None |
| MC-BEAN-001 | Applicable | PASS | §7–9/14 | Qualified technical beans and explicit import. | None |
| MC-UTIL-001 | Applicable | PASS | §7–9/14 | Use JDK bounded InputStream and existing Jackson. | None |
| MC-JSON-001 | Applicable | PASS | §7–9/14 | Closed request, safe response and OAS artifact. | None |
| MC-TIME-001 | Applicable | PASS | §7–9/14 | Current clock deadline and UTC receipt. | None |
| MC-CONFIG-001 | Applicable | PASS | §7–9/14 | Dedicated default-off property and scope map. | None |
| MC-PATTERN-001 | Applicable | PASS | §7–9/14 | Servlet Adapter and request-local authority. | None |
| MC-SCOPE-001 | Applicable | PASS | §7–9/14 | Nine source files and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §7–9/14 | Finite mock servlet proof and package comparison. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7–9/14 | Live IAM and PostgreSQL remain separate. | None |

### 12.6 Final verdict

PASS — Ready for user review
