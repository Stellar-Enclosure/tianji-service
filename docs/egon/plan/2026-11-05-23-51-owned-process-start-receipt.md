# T4-03 Process owner 精确 START 回执实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-51-owned-process-start-receipt.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 23:51 Asia/Shanghai |
| Updated | 2026-11-05 23:51 Asia/Shanghai |
| Owner | Tianji Process |
| Repository | tianji-service |
| Scope | Exact C/PS current owner START receipt read, 14 source paths and 3 docs |
| Source Requirement | Master §17.2 S11 and §19 T4-03 positive external effect observation |
| Baseline Revision | Service fd5ebbc329d3a4f78221fb03d3230483f49f77b1; re-lock if other approved work lands |
| Implements Spec | [Owned Process START receipt](../spec/2026-11-05-23-50-owned-process-start-receipt.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 23:50 Asia/Shanghai, SHA-256 8ba4691ed7f5b97b669f467bc2a8f6bc11c6ee9842a05ab2c66a2559bdf3ed0f |
| Effective Specs | [Owned Process START receipt](../spec/2026-11-05-23-50-owned-process-start-receipt.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

One exact, default-off Process READ endpoint returns positive same-owner applied receipt evidence. No inferred negative effect, no AS write reconciliation, replay or scope extension. One logical backend commit.

## 2. Target Spec and Effective Design

Spec §§5/7/9/11/14 defines existing authority, exact command/digest, physical primary read-only and finite/artifact proof. Source, grant, activation and runtime decisions are separate.

## 3. Effective Requirements and Acceptance

| ID | Evidence |
| --- | --- |
| REQ-001 | Both flags, exact READ and distinct resource, four-part owner |
| REQ-002 | One primary receipt read, 2s, exact digest and closed three fields |
| REQ-003 | Uniform inconclusive 404 and no action |
| REQ-004 | Actual finite and producer evidence, no migration/runtime |

## 4. Implementation Strategy and Dependency Order

Run baseline contract and policy reds independently. Add typed C and A Service to observe absent behavior. Apply B C → existing PS store/service → exact permission, security and endpoint → OpenAPI. Run finite G1 and actual P1, then one scoped commit.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| C immutable values | OrchestrationChecks and GetProcessInstanceQuery | Two bounded records and singleton status |
| PS auth | TianjiProcessAccess and existing READ | Same guard with separate exact resource |
| Storage | Existing scoped V1/V3 and read-only primary | Reuse guard and time profile, no new storage |
| OpenAPI | Existing static PS producer | Contract-first, no new dependency |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Exact evidence | old instance GET and correlation | no exact digest and unknown instance | one additional atomic query | Add |
| Auth | current READ and AS original START scopes | independent owner and resource | no AS authority | Reuse |
| Receipt | current store and primary guard | exact PK and immutable APPLIED | no engine dependencies | Modify |
| Validation | Mockito/JDBC, MockMvc, static producer | finite and isolated | real I/E separate | Reuse |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Existing C/PS | §7 File blocks | Exact Query/DTO names and existing adapters | G1/P1/S1 | 1 | PASS |
| Rule 2 | §6.2 | Existing C/PS | §7 File blocks | Validate trusted owner then closed parameters and typed Query | G1/P1/S1 | 1 | PASS |
| Rule 3 | §6.2 | Existing C/PS | §7 File blocks | Immutable value records only, no ordinary production PO | G1/P1/S1 | 1 | PASS |
| Rule 4 | §6.2 | Existing C/PS | §7 File blocks | Reuse current controller/security configuration without new injected business bean | G1/P1/S1 | 1 | PASS |
| Rule 5 | §6.2 | Existing C/PS | §7 File blocks | Existing JDK time and current digest | G1/P1/S1 | 1 | PASS |
| Rule 6 | §6.2 | Existing C/PS | §7 File blocks | Current Spring Jackson and closed OAS fields | G1/P1/S1 | 1 | PASS |
| Rule 7 | §6.2 | Existing C/PS | §7 File blocks | Existing API plus one false default | G1/P1/S1 | 1 | PASS |
| Rule 9 | §6.2 | Existing C/PS | §7 File blocks | Reuse Query adapter, no new business pattern | G1/P1/S1 | 1 | PASS |
| Rule 10 | §6.2 | Existing C/PS | §7 File blocks | Strict UTC Instant and native OffsetDateTime JDBC | G1/P1/S1 | 1 | PASS |
| Rule 11 | §6.2 | Existing C/PS | §7 File blocks | Existing owner JDBC technical boundary, no DDL or CQE writer | G1/P1/S1 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | recordedAt / existing created_at | Occurred instant | OffsetDateTime JDBC and Instant | UTC instant | UTC Z | Explicit UTC | UTC wire | Gregorian 0001–9999 and no local zone | PostgreSQL micros and existing receipt creation clock | No commit clock claim | Step 1 TEST-001/002/003 |

## 5. Change File Tree

| Operation | Exact path | Purpose | Gate |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java` | C exact wire test | R1/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java` | Exact identity policy test | R3/G1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessStartReceiptQuery.java` | Immutable exact query | R2/G1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptDTO.java` | Closed applied receipt | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java` | Same primary and exact scoped SQL proof | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java` | Reuse bound primary and add narrow lookup | G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java` | Own bounded read and projection | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | Resource constant and label | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | Exact authenticated GET/HEAD | G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptController.java` | Exact REST boundary | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Explicit false base configuration | G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptControllerTest.java` | MockMvc closed response and condition | G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java` | Real local filter and current policies | G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-start-receipt-v1.json` | Actual static producer contract | P1 |
| CREATE | `docs/egon/spec/2026-11-05-23-50-owned-process-start-receipt.md` | Reviewed contract | S1 |
| CREATE | `docs/egon/plan/2026-11-05-23-51-owned-process-start-receipt.md` | Reviewed implementation order | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-owned-process-start-receipt.md` | Actual finite and package evidence | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Start from clean fd5ebbc329d3a4f78221fb03d3230483f49f77b1, or first re-lock after independent source, checking PS prior source and exact 27 migration bytes. Raw PS baseline manifest is retained at t4-02/agent-initial-claim-delay/root-process-server-manifest.json from actual PS producer at the verified prior package (SHA-256 b2725cec7d7340c0116b046d400c70d1f01e610e66a399f75cc3da76eb2d0e84). Scratch A/B/red is under /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/process-start-receipt. No tracked source change before GO, no app/server/DB/provider/network/grant. R2 uses C B plus Service A, old store and B owning test. The A placeholder cannot remain in staged source.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Read one exact owned applied START receipt

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Exact approved scope, clean lock, reviewed Spec/Plan and Source GO.
- Baseline state: Known instance and correlation are available; no exact command and digest read.
- Observable outcome: Current Process owner observes one applied receipt, with all absence inconclusive.
- End state: 14 exact source paths and three docs, one logical commit.
- Test-first gate: Required. R1 missing C, R2 actual typed stub missing expected evidence, R3 identity policy USER vs USER_OR_SERVICE.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`

- Purpose: C exact wire test.
- Symbols: process_start_receipt_contract_exists; start_receipt_contract_is_an_exact_non_authorizing_digest_and_three_output_fields.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R1/G1 contribution.
- Contract/signature changes: process_start_receipt_contract_exists; start_receipt_contract_is_an_exact_non_authorizing_digest_and_three_output_fields.
- Input/output and state mapping: assert class exists in baseline RED; assert exact C digest, query grammar and three output fields in B;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
expectMissingClass(GetProcessStartReceiptQuery.className());
assertExactStartDigestForNormalizedPrimitiveInputs();
assertClosedResponseAndStrictTime();
```

- Verification contribution: R1/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`

- Purpose: Exact identity policy test.
- Symbols: classifies_exact_start_receipt_get_as_mixed_principal.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R3/G1 contribution.
- Contract/signature changes: classifies_exact_start_receipt_get_as_mixed_principal.
- Input/output and state mapping: assert exact GET uses USER_OR_SERVICE; expect baseline USER to fail;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
request = mockGet("/api/tianji/v1/process-start-receipts");
requirement = endpointAuthenticationPolicy.requirement(request);
assertEquals(USER_OR_SERVICE, requirement);
```

- Verification contribution: R3/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 3 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessStartReceiptQuery.java`

- Purpose: Immutable exact query.
- Symbols: GetProcessStartReceiptQuery.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R2/G1 contribution.
- Contract/signature changes: GetProcessStartReceiptQuery.
- Input/output and state mapping: commandId = OrchestrationChecks.command(commandId); require lowercase hexadecimal digest length 64;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
commandId = OrchestrationChecks.command(commandId);
requireLowercaseHex(requestSha256, 64);
return immutableQuery(commandId, requestSha256);
```

- Verification contribution: R2/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptDTO.java`

- Purpose: Closed applied receipt.
- Symbols: ProcessStartReceiptDTO; StatusEnum.APPLIED.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R2/G1 contribution.
- Contract/signature changes: ProcessStartReceiptDTO; StatusEnum.APPLIED.
- Input/output and state mapping: validate existing navigable instanceId, sole APPLIED and UTC microsecond timestamp;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
require(status == APPLIED);
requireUtcMicrosecondInstant(recordedAt);
```

- Verification contribution: R2/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java`

- Purpose: Same primary and exact scoped SQL proof.
- Symbols: reads_one_applied_start_on_the_same_primary_and_exact_owner_and_request_digest.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R2/G1 contribution.
- Contract/signature changes: reads_one_applied_start_on_the_same_primary_and_exact_owner_and_request_digest.
- Input/output and state mapping: assertDoesNotThrow(service.getStartReceipt(owner, exactQuery)); assert query has all four owner fields and exact command/digest; assert no engine interactions;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
result = assertDoesNotThrow(() -> service.getStartReceipt(owner, exactQuery));
assertEquals(expectedAppliedReceipt, result);
assertExactSqlBindingsAndNoEngineCalls();
```

- Verification contribution: R2/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`

- Purpose: Reuse bound primary and add narrow lookup.
- Symbols: beginReceiptObservation; findAppliedStart; StartReceiptObservation.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1 contribution.
- Contract/signature changes: beginReceiptObservation; findAppliedStart; StartReceiptObservation.
- Input/output and state mapping: SELECT resource_id, created_at FROM same_receipt WHERE full_owner AND START AND exact_command AND exact_digest AND APPLIED LIMIT 2; refuse duplicate;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
rows = jdbc.query(selectResourceAndCreatedAtWhereExactOwnerStartCommandDigestAndAppliedLimit2, parameters);
if (rows.size() > 1) throw unavailable();
return rows.stream().findFirst();
```

- Verification contribution: G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`

- Purpose: Own bounded read and projection.
- Symbols: getStartReceipt; receiptRead.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise R2/G1 contribution.
- Contract/signature changes: getStartReceipt; receiptRead.
- Input/output and state mapping: reject legacy, ambient or interrupted; own existing primary RR 2s; assert physically read-only; map one row; absent -> 404; corrupt -> 503;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
requireVerifiedActorAndNoAmbientTransaction();
return receiptRead.execute(status -> { beginReceiptObservation(); return mapAppliedOr404(); });
mapCorruptionOrTransactionFailureToUnavailable();
```

- Verification contribution: R2/G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Resource constant and label.
- Symbols: START_RECEIPT_GET_API.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1/P1 contribution.
- Contract/signature changes: START_RECEIPT_GET_API.
- Input/output and state mapping: declare existing-page label and resource constant only;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
existingPermission = TianjiProcessPermissions.READ;
resourceKey = "tianji.api.process.startReceipt.get";
addEnglishLabel("api.process.startReceipt", "Read an owned Process START receipt");
```

- Verification contribution: G1/P1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Exact authenticated GET/HEAD.
- Symbols: processSecurityFilterChain; processEndpointAuthenticationPolicy.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1/P1 contribution.
- Contract/signature changes: processSecurityFilterChain; processEndpointAuthenticationPolicy.
- Input/output and state mapping: admit GET and HEAD only when both flags; classify exact path as mixed-principal; preserve all other paths;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
if (apiEnabled && startReceiptReadEnabled) authenticateExactGetAndHead(PATH);
endpointPolicy.allowMixedPrincipalExactPath(PATH);
keepExistingDenyFallback();
```

- Verification contribution: G1/P1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptController.java`

- Purpose: Exact REST boundary.
- Symbols: receipt; PATH.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1/P1 contribution.
- Contract/signature changes: receipt; PATH.
- Input/output and state mapping: actor = access.require(START_RECEIPT_GET_API); validate exact two query parameters once each; return noStore(service.getStartReceipt(actor, query));
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
actor = access.require(START_RECEIPT_GET_API);
query = exactTypedQuery(parameters, "commandId", "requestSha256");
return noStore(processes.getStartReceipt(actor, query));
```

- Verification contribution: G1/P1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 11 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Explicit false base configuration.
- Symbols: stellar.tianji.process.api.start-receipt-read-enabled.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1/P1 contribution.
- Contract/signature changes: stellar.tianji.process.api.start-receipt-read-enabled.
- Input/output and state mapping: start-receipt-read-enabled: false
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```yaml
# Add only the new property; retain all existing Process configuration and environment semantics.
stellar:
  tianji:
    process:
      api:
        start-receipt-read-enabled: false
```

- Verification contribution: G1/P1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 12 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptControllerTest.java`

- Purpose: MockMvc closed response and condition.
- Symbols: reads_only_the_applied_receipt_for_an_exact_owned_request; declares_only_existing_read_authority_and_needs_both_flags.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1 contribution.
- Contract/signature changes: reads_only_the_applied_receipt_for_an_exact_owned_request; declares_only_existing_read_authority_and_needs_both_flags.
- Input/output and state mapping: assert exact 200, raw authorization order, uniform 404, 503 and all four flag combinations;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
assertExactNoStoreSuccessThroughMockMvc();
assertUnauthorizedBeforeInvalidQueryAndUniformNotFound();
assertOnlyBothFlagsRegisterController();
```

- Verification contribution: G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 13 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`

- Purpose: Real local filter and current policies.
- Symbols: servlet_chain_checks_current_user_permission_scope_and_trusted_tenant; service_requires_its_own_scope_and_policy.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise G1 contribution.
- Contract/signature changes: servlet_chain_checks_current_user_permission_scope_and_trusted_tenant; service_requires_its_own_scope_and_policy.
- Input/output and state mapping: assert missing auth/scope/code denied, wrong method denied, exact USER actor succeeds; distinct SERVICE policy independently required;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
assertExactOwnerPassesBothCurrentFilters();
assertMissingScopeAndWrongMethodDenied();
assertServicePolicyIsResourceSpecific();
```

- Verification contribution: G1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 14 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-start-receipt-v1.json`

- Purpose: Actual static producer contract.
- Symbols: getOwnedProcessStartReceipt.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise P1 contribution.
- Contract/signature changes: getOwnedProcessStartReceipt.
- Input/output and state mapping: declare one exact GET, explicit required parameters, existing authority, closed three-field DTO and typed current errors;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
publishOperation("getOwnedProcessStartReceipt", GET, PATH);
requireExactCommandAndDigestQuery();
closeResponseSchema("instanceId", "status", "recordedAt");
```

- Verification contribution: P1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 15 — `CREATE docs/egon/spec/2026-11-05-23-50-owned-process-start-receipt.md`

- Purpose: Reviewed contract.
- Symbols: Spec §§5/7/9/11/14.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise S1 contribution.
- Contract/signature changes: Spec §§5/7/9/11/14.
- Input/output and state mapping: record exact source lock and static-only acceptance boundary;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
lockScopeToSameOwnerReadOnly();
recordEffectiveSpecVersionAndDecisionsForExactScopeAndDefaultOff();
keepExternalReconciliationAndDeploymentSeparate();
```

- Verification contribution: S1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 16 — `CREATE docs/egon/plan/2026-11-05-23-51-owned-process-start-receipt.md`

- Purpose: Reviewed implementation order.
- Symbols: Step 1 and gates R1–S1.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise S1 contribution.
- Contract/signature changes: Step 1 and gates R1–S1.
- Input/output and state mapping: bind exact Spec SHA and actual current source authorization;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
bindEffectiveSpecShaAndExactCleanSourceLock();
writeExactAssertionAndArtifactProofOrderForR1ToP1();
listExactStageAndCommitPaths();
```

- Verification contribution: S1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

#### File 17 — `CREATE docs/egon/evidence/2026-11-05-owned-process-start-receipt.md`

- Purpose: Actual finite and package evidence.
- Symbols: R1/R2/R3/G1/P1/S1.
- Repository evidence: Spec §2/7/8 describes existing owner boundary and exact artifact path.
- Dependencies and consumers: Ordered C/PS predecessors or declared source-phase test input in §8.
- Why now: Provide this precise S1 contribution.
- Contract/signature changes: R1/R2/R3/G1/P1/S1.
- Input/output and state mapping: record actual raw reports, artifact SHA, exact PaC delta, migration preservation and outstanding I/E;
- Error and edge behavior: Preserve fixed current auth/errors; absent scoped evidence remains inconclusive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing persistence, ownership and CQE boundaries.
- Implementation pseudocode:

```java
recordActualRawRedAndGreenReportsAndCounts();
recordActualExecutableArtifactAndAdditivePermissionManifestHashes();
recordIndependentUnexecutedRuntimeGates();
```

- Verification contribution: S1.
- After this file: The exact bounded path or artifact is ready for its declared gate.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact source phases.
- Expected result: Three intended assertion failures, all finite and actual package positive with no failed/skipped tests.
- Failure returns to: Failed owning phase; investigate without widening authority.
- Completion criteria: Exact staged inventory, actual packaged OAS/PaC, fixed default and historical migration evidence.
- Rollback: Before commit withdraw only this Step. Later disable flag or use separately approved scoped revert.
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessStartReceiptQuery.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptDTO.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptController.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessStartReceiptControllerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`, `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-start-receipt-v1.json`, `docs/egon/spec/2026-11-05-23-50-owned-process-start-receipt.md`, `docs/egon/plan/2026-11-05-23-51-owned-process-start-receipt.md`, `docs/egon/evidence/2026-11-05-owned-process-start-receipt.md`
- Commit: feat(process): expose exact owned applied START receipts.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Outcome |
| --- | --- | --- |
| R1 | red OrchestrationContractTest.java on baseline | Expected missing GetProcessStartReceiptQuery, one assertion |
| R2 | B C Query/DTO, A Service, B ProcessCorrelationLookupTest.java on old store | Expected unavailable stub instead of applied receipt, one assertion |
| R3 | red ProcessSecurityConfigurationTest.java on baseline security | Expected USER_OR_SERVICE, baseline USER, one assertion |
| G1 | Exact B source paths plus existing finite regressions | All selected suites nonzero, zero skipped/errors/failures |
| P1 | Clean offline permission-producer verify | Exact OAS, PaC additive only, default false, 27 SQL byte equality |
| S1 | Strict docs, raw report accounting and exact staged scope | One commit, no I/E credit |

```sh
receipt_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
receipt_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
receipt_base=fd5ebbc329d3a4f78221fb03d3230483f49f77b1
receipt_core=OrchestrationContractTest,ProcessCorrelationLookupTest,ProcessStartReceiptControllerTest,ProcessSecurityConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,ProcessExecutionServiceTest,ProcessStorageConfigurationTest
receipt_finite() {
  JAVA_HOME="$receipt_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$receipt_settings" -s "$receipt_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
receipt_finite 'OrchestrationContractTest#process_start_receipt_contract_exists'
receipt_finite 'ProcessCorrelationLookupTest#reads_one_applied_start_on_the_same_primary_and_exact_owner_and_request_digest'
receipt_finite 'ProcessSecurityConfigurationTest#classifies_exact_start_receipt_get_as_mixed_principal'
receipt_finite "$receipt_core"
test "$(git rev-parse --verify HEAD)" = "$receipt_base"
JAVA_HOME="$receipt_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$receipt_settings" -s "$receipt_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$receipt_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-03-owned-process-start-receipt-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$receipt_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py docs/egon/spec/2026-11-05-23-50-owned-process-start-receipt.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-05-23-51-owned-process-start-receipt.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

Run selectors only at their declared source phases. Restore baseline security or keep it unchanged for R3. Retain raw Surefire reports, including independent RED causes and exact G1/P1 counts. P1 opens actual Process Server executable archive, checks the unique static contract and unchanged existing resources, exact GET/HEAD path, existing READ and OAuth scope, USER/SERVICE and one EN label, no changed permission definition or grant. Verify both Process feature flags default off or absent, no dependency/DDL, and bytes of all 27 existing production SQL. The source/producer gate does not run actual PostgreSQL, IAM, collector or runtime.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or activation. Existing startup and routes remain. Only exact new flag and existing API enable the mapping. Current deployed read scope and distinct resource policies must be independently verified before authorized enablement.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | 1 | R3/G1/P1 |
| REQ-002 | 1 | R1/R2/G1/P1 |
| REQ-003 | 1 | R2/G1/P1 |
| REQ-004 | 1 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

The original AS credential still lacks Process READ. The new route does not settle any Tool intent or classify a missing response as NONE. Actual PostgreSQL, current IAM, operator authorization, the external loss witness and S11 remain separate.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/9/14/16 | Existing C/PS technical profile | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/9/14/16 | Reuse exact owner and transaction | None |
| MC-DEP-001 | Applicable | PASS | §7/8/9/14/16 | No dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8/9/14/16 | Exact immutable Query/DTO | None |
| MC-VALID-001 | Applicable | PASS | §7/8/9/14/16 | Current owner and exact two parameters | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/9/14/16 | Closed immutable receipt | None |
| MC-CONVERT-001 | Applicable | PASS | §7/8/9/14/16 | Explicit JDBC projection | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/9/14/16 | No new logs | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/9/14/16 | Existing Spring controller style | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/9/14/16 | Existing Spring/JDK primitives | None |
| MC-JSON-001 | Applicable | PASS | §7/8/9/14/16 | Closed static OpenAPI and output | None |
| MC-TIME-001 | Applicable | PASS | §7/8/9/14/16 | UTC microsecond receipt | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/9/14/16 | Default off and both flags | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/9/14/16 | Direct query boundary | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/9/14/16 | One PS resource only | None |
| MC-TEST-001 | Applicable | PASS | §7/8/9/14/16 | Finite and actual producer separate | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/9/14/16 | External reconciliation and environment separate | None |

**PASS — Ready for user review**
