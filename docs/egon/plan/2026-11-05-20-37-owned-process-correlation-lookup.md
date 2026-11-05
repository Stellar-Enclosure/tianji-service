# T4-02 Process owner 精确关联查询实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-20-37-owned-process-correlation-lookup.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 20:37 Asia/Shanghai |
| Updated | 2026-11-05 20:37 Asia/Shanghai |
| Owner | Tianji Process |
| Repository | tianji-service |
| Scope | C/PS 一个公开 owner 关联 GET，12 个源码/测试/资源路径及三份文档 |
| Source Requirement | Master §13.3、§17.2 S12、§19 T4-02 持久 correlation 查询 |
| Baseline Revision | Service d5ccd8225da3c8f27adffdee04cb9d1d787ed7f2 |
| Implements Spec | [Owned Process correlation](../spec/2026-11-05-20-36-owned-process-correlation-lookup.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 20:36 Asia/Shanghai, SHA-256 d98a298bb4bc2c95edb03d0b7f44f4e91e79da182e6a0b382dbc58ee69ce0adc |
| Effective Specs | [Owned Process correlation](../spec/2026-11-05-20-36-owned-process-correlation-lookup.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master T4-02](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

在 C/PS 增加一个同 owner immutable START 收据读取。精确 correlation、UTC 半开窗口与有界导航 cursor。保持原实例 GET、权限定义、技术配置、依赖、迁移和 Link。一个 backend 逻辑提交。

## 2. Target Spec and Effective Design

Primary Spec §§7/9/10/14 是同 owner、固定页大小、物理只读主库和实际 producer OAS/PaC。当前完整部署授权仍独立。未来 CL/Web 只能消费已验证的 producer contract。

## 3. Effective Requirements and Acceptance

| ID | Evidence |
| --- | --- |
| REQ-001 | 新 exact GET/HEAD 同现有 READ/scope，独立资源与完整 owner |
| REQ-002 | 同主库一表，2s，UTC ≤31d，20+1，确定 keyset |
| REQ-003 | v1 受限 cursor、同域成员、非法/foreign/stale 统一 400 |
| REQ-004 | no POM/DDL/trace change；actual finite/OAS/PaC，I/S12 separate |

## 4. Implementation Strategy and Dependency Order

R1 current controller missing mapping. R2 only typed A Query/Page/Service and record seam, assert empty vs required. R3 typed A Query rejects some invalids but allows 31d+1µs. Final B C → store/service → permission/security/controller → OAS, exact scope. One commit after G1/P1/S1.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| C immutable values | OrchestrationChecks and other Process DTOs | Two records, one nested receipt DTO |
| PS boundary | Existing execution service, receipt storage and permissions | Reuse current authority, strict route and bounded query |
| Storage | V1/V3 and native primary-read precedent | Existing primary JDBC, no schema change |
| OpenAPI | Existing contract-first packaged models | Static PS artifact, no springdoc |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Owner | TianjiProcessAccess/PaC | Four current identity parts, distinct resource | Existing authority usable | Reuse |
| Persistent query | ProcessExecutionService/ProcessCommandReceiptStore | No correlation discovery | Narrow SELECT uses immutable provenance | Modify |
| Time and cursor | C native time and ProcessRequestDigest | Existing JDK, length-safe digest | Sufficient without signer | Reuse |
| Finite proof | existing Mockito/JDBC/MockMvc and source reactor | Source no listener/PG | exact offline gate | Reuse |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Existing C/PS | §7 File blocks | Exact Query/DTO names and existing adapters | G1/P1/S1 | 1 | PASS |
| Rule 2 | §6.2 | Existing C/PS | §7 File blocks | Validate trusted owner then closed parameters and typed Query | G1/P1/S1 | 1 | PASS |
| Rule 3 | §6.2 | Existing C/PS | §7 File blocks | Immutable value records only, no ordinary production PO | G1/P1/S1 | 1 | PASS |
| Rule 4 | §6.2 | Existing C/PS | §7 File blocks | Reuse current controller/security configuration without new injected business bean | G1/P1/S1 | 1 | PASS |
| Rule 5 | §6.2 | Existing C/PS | §7 File blocks | Existing JDK time/Base64 and current digest | G1/P1/S1 | 1 | PASS |
| Rule 6 | §6.2 | Existing C/PS | §7 File blocks | Current Spring Jackson and closed OAS fields | G1/P1/S1 | 1 | PASS |
| Rule 7 | §6.2 | Existing C/PS | §7 File blocks | Existing API flag and profile remain | G1/P1/S1 | 1 | N/A |
| Rule 9 | §6.2 | Existing C/PS | §7 File blocks | Reuse Query adapter, no new business pattern | G1/P1/S1 | 1 | PASS |
| Rule 10 | §6.2 | Existing C/PS | §7 File blocks | Strict UTC Instant and native OffsetDateTime JDBC | G1/P1/S1 | 1 | PASS |
| Rule 11 | §6.2 | Existing C/PS | §7 File blocks | Existing owner JDBC technical boundary, no DDL or CQE writer | G1/P1/S1 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive, cursor anchor | Occurred instant | Instant / OffsetDateTime for JDBC | Not persisted | UTC Z | Explicit ZoneOffset.UTC | Wire UTC Z | Half-open and <=31d, no offset/leap/24:00 | Micros, valid Gregorian year 0001–9999, upper bound database clock | Accept JS .000Z without loss | Step 1 TEST-001 and TEST-002 |
| TIME-002 | observedAt, submittedAt | Occurred instant | Instant / OffsetDateTime for JDBC | UTC instant | UTC Z | Explicit UTC Instant | Wire UTC Z | Exact immutable receipt and same DB observation | Micros, PostgreSQL native timestamptz/clock_timestamp | No conversion through local wall-clock | Step 1 TEST-002 and TEST-003 |

## 5. Change File Tree

| Operation | Exact path | Purpose | Gate |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java` | C contract tests | R3/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java` | MockMvc query tests | R1/G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java` | Finite JDBC read tests | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java` | Resource and scope tests | G1/P1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ListProcessInstancesByCorrelationQuery.java` | Query value contract | R2/R3/G1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessInstanceCorrelationPageDTO.java` | Receipt response contract | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java` | Narrow existing store read | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java` | Owner query orchestration | R2/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | New resource and label | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | Exact existing filters | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessController.java` | HTTP binding | R1/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-instance-correlation-v1.json` | Contract-first producer | P1 |
| CREATE | `docs/egon/spec/2026-11-05-20-36-owned-process-correlation-lookup.md` | Reviewed design | S1 |
| CREATE | `docs/egon/plan/2026-11-05-20-37-owned-process-correlation-lookup.md` | Reviewed execution | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-owned-process-correlation-lookup.md` | Actual proof | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Lock exact clean Service d5ccd8225da3c8f27adffdee04cb9d1d787ed7f2, baseline source manifest and 27 SQL; current Source84 PS producer retained at t4-02/step84-after-process-server-manifest.json. R1 uses root/r1 test; R2 uses root/a Query, root/b Page, ps-execution-draft/a Service and record-only Store and ps-execution-draft/b owning test; R3 uses root/r3 C test and existing typed Query A. Scratch paths under /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation. Final B is exact proposed paths and requires source GO. No source authority is inferred from scratch evidence. Do not fetch dependencies, start a service/DB or register/grant anything.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Find current owner's Process receipts by exact correlation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Exact source lock, reviewed Spec/Plan and Source GO.
- Baseline state: Known-instance GET exists. No exact current owner's correlation GET.
- Observable outcome: Existing permissions authorize one independently managed receipt query, no native or cross-owner lookup.
- End state: 12 exact source/test/resource paths and three docs, one logical commit.
- Test-first gate: Required. R1 404 vs 200, R2 actual typed empty vs 20, R3 missing invalid-window exception.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`

- Purpose: C contract tests.
- Symbols: OrchestrationContractTest#rejects_invalid_process_correlation_queries.
- Repository evidence: Current C record/Process validation tests.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R3/G1 phase.
- Contract/signature changes: OrchestrationContractTest#rejects_invalid_process_correlation_queries for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
assertThrows for 31 days plus one microsecond, empty or reverse windows and bad cursor;
assert rejects malformed calendar, leap second and 24:00, accepts JS .000Z;
assert exact immutable page/receipt bounds and valid detail IDs;
```

- Verification contribution: R3/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java`

- Purpose: MockMvc query tests.
- Symbols: ProcessControllerTest#lists_exact_owned_process_receipts_by_correlation.
- Repository evidence: Current standalone MockMvc and controller authority fixture.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R1/G1 phase.
- Contract/signature changes: ProcessControllerTest#lists_exact_owned_process_receipts_by_correlation for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
GET exact sibling route with three query fields;
assert 200 and no-store plus closed receipt-only projection; // on baseline expected 200 actual 404
assert current authority before duplicate/unknown validation and no storage for rejected inputs;
```

- Verification contribution: R1/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java`

- Purpose: Finite JDBC read tests.
- Symbols: ProcessCorrelationLookupTest#returns_only_exact_owned_correlation_receipts_and_binds_the_cursor.
- Repository evidence: Same-module mocked JDBC and PS migration read precedent.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R2/G1 phase.
- Contract/signature changes: ProcessCorrelationLookupTest#returns_only_exact_owned_correlation_receipts_and_binds_the_cursor for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
configure 21 exact owner START rows with equal times;
assert first result size 20; // typed A returns 0
verify physical read-only first, complete four-part owner and order/limit; test next, stale/foreign and UTC constraints;
```

- Verification contribution: R2/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`

- Purpose: Resource and scope tests.
- Symbols: ProcessPermissionIntegrationTest new exact resource coverage.
- Repository evidence: Current USER/SERVICE permission policy fixtures and MockMvc.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared G1/P1 phase.
- Contract/signature changes: ProcessPermissionIntegrationTest new exact resource coverage for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
install expected exact correlation route resource with READ and read scope;
require current USER and separately exact SERVICE policy allow, reject missing grants/scope;
test tenant assertions, exact method/path and preexisting paths retain behavior;
```

- Verification contribution: G1/P1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 5 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ListProcessInstancesByCorrelationQuery.java`

- Purpose: Query value contract.
- Symbols: ListProcessInstancesByCorrelationQuery and parseUtc.
- Repository evidence: OrchestrationChecks current immutable inputs.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R2/R3/G1 phase.
- Contract/signature changes: ListProcessInstancesByCorrelationQuery and parseUtc for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
validate exact existing command-like correlation key and optional v1 cursor text <=1200;
parse strict Gregorian UTC Z with 1–6 optional fractional digits and year 0001–9999;
require microsecond precision, lower before upper and duration <=31 days;
```

- Verification contribution: R2/R3/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 6 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessInstanceCorrelationPageDTO.java`

- Purpose: Receipt response contract.
- Symbols: ProcessInstanceCorrelationPageDTO and nested ReceiptDTO.
- Repository evidence: Existing C exact instance ID and release validation.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R2/G1 phase.
- Contract/signature changes: ProcessInstanceCorrelationPageDTO and nested ReceiptDTO for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
require correlation, database observedAt and immutable list with <=20 unique owned rows;
validate receipt instanceId through GetProcessInstanceQuery, exact release and submittedAt;
accept nullable cursor only on a full 20-row page and reject row time after observedAt;
```

- Verification contribution: R2/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`

- Purpose: Narrow existing store read.
- Symbols: ProcessCommandReceiptStore and nested CorrelationReceipt.
- Repository evidence: Existing V1/V3 immutable owner receipt and source primary read precedent.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R2/G1 phase.
- Contract/signature changes: ProcessCommandReceiptStore and nested CorrelationReceipt for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
A adds only CorrelationReceipt declaration for typed fixture compile;
B establishes current physical read-only primary and bounded DB timestamp in same transaction;
query exact owner START/APPLIED and correlation/window, same-table anchor EXISTS and C exclusive order LIMIT 21;
```

- Verification contribution: R2/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`

- Purpose: Owner query orchestration.
- Symbols: ProcessExecutionService#listByCorrelation.
- Repository evidence: Existing same-source manager, receipt adapter and ProcessRequestDigest.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R2/G1 phase.
- Contract/signature changes: ProcessExecutionService#listByCorrelation for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
A only returns typed empty page, no JDBC;
B reject unverified actor/ambient tx; decode v1 normalized scope digest then begin own RR/2s primary transaction;
validate current DB upper time and scoped anchor; verify strict row order/affinity and return 20+sentinel result;
```

- Verification contribution: R2/G1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: New resource and label.
- Symbols: TianjiProcessPermissions.CORRELATION_LIST_API and api.process.correlations.
- Repository evidence: Current GET read scope and PaC declaration conventions.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared G1/P1 phase.
- Contract/signature changes: TianjiProcessPermissions.CORRELATION_LIST_API and api.process.correlations for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
retain current TIANJI_PROCESS_READ definition and OAuth read scope;
declare exact independent tianji.api.process.instance.correlations and one EN permission message;
keep current USER/SERVICE at mapping and existing resource identities intact;
```

- Verification contribution: G1/P1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Exact existing filters.
- Symbols: ProcessSecurityConfiguration Spring authorization list and IdP policy.
- Repository evidence: Current protected Process routes and filters.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared G1/P1 phase.
- Contract/signature changes: ProcessSecurityConfiguration Spring authorization list and IdP policy for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
add exact GET and implicit HEAD path to enabled PS chain, no wildcards;
add same exact path to dual-principal current IdP endpoint list;
verify prior dispatch, worker and default-deny methods/paths remain unchanged;
```

- Verification contribution: G1/P1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 11 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessController.java`

- Purpose: HTTP binding.
- Symbols: ProcessController#correlations and CORRELATION_PATH.
- Repository evidence: Current noStore and scope/access patterns.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared R1/G1/P1 phase.
- Contract/signature changes: ProcessController#correlations and CORRELATION_PATH for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```java
@GetMapping exact sibling path and protected resource with current READ scope;
require new exact resource first, then parse @RequestParam MultiValueMap closed key set and cardinality;
construct typed query, call existing execution owner and return 200 no-store page;
```

- Verification contribution: R1/G1/P1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 12 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-instance-correlation-v1.json`

- Purpose: Contract-first producer.
- Symbols: process-instance-correlation-v1.json and listOwnedProcessStartsByCorrelation.
- Repository evidence: Existing packaged OpenAPI 3.1 convention.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared P1 phase.
- Contract/signature changes: process-instance-correlation-v1.json and listOwnedProcessStartsByCorrelation for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```json
define exactly one GET operation and four exact query parameters;
document closed receipt fields, no-store, typed current error codes and OAuth scope plus PaC metadata;
check exact bytes in actual PS exec, no runtime docs endpoint;
```

- Verification contribution: P1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 13 — `CREATE docs/egon/spec/2026-11-05-20-36-owned-process-correlation-lookup.md`

- Purpose: Reviewed design.
- Symbols: Canonical Spec and REQ-001–004.
- Repository evidence: Master T4-02 and verified existing authority.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared S1 phase.
- Contract/signature changes: Canonical Spec and REQ-001–004 for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```text
copy approved reviewed Spec bytes and verify its SHA at canonical exact path;
confirm exact owner Query, no SQL schema and no wider consumer;
run strict validator and record actual artifact authority;
```

- Verification contribution: S1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 14 — `CREATE docs/egon/plan/2026-11-05-20-37-owned-process-correlation-lookup.md`

- Purpose: Reviewed execution.
- Symbols: Canonical Plan and R1–R3/G1/P1/S1.
- Repository evidence: Current source-reactor and evidence format.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared S1 phase.
- Contract/signature changes: Canonical Plan and R1–R3/G1/P1/S1 for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```text
copy approved Plan and verify primary Spec link and digest;
keep exact test-first phases and current owner static checks;
verify one logical Step and exact 15 staged paths;
```

- Verification contribution: S1.
- After this file: Exact source or evidence exists for the declared gate.

#### File 15 — `CREATE docs/egon/evidence/2026-11-05-owned-process-correlation-lookup.md`

- Purpose: Actual proof.
- Symbols: Canonical evidence report.
- Repository evidence: Current C/PS finite and static artifact evidence.
- Dependencies and consumers: Exact preceding C/PS owner paths and source phase gates.
- Why now: File participates in the declared S1 phase.
- Contract/signature changes: Canonical evidence report for this exact owner lookup.
- Input/output and state mapping: Exact typed or artifact transformation described below.
- Error and edge behavior: Uniform input errors, preserved existing authorization and unavailable semantics, no hidden writes.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner, persistence, CQE and technology boundaries.
- Implementation pseudocode:

```text
record exact red and green owning reports and artifact SHAs from actual run;
record new resource/label only, preserved 27 migration bytes, unchanged trace and config;
record separate runtime PG, grant/deployment, SDK/Web and S12 acceptance;
```

- Verification contribution: S1.
- After this file: Exact source or evidence exists for the declared gate.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact source phases.
- Expected result: R1–R3 each one assertion, owning GREEN/P1 reports pass with zero failures/errors/skips.
- Failure returns to: Exact failed owning phase. No permission or source scope expansion.
- Completion criteria: Finite actual tests, PS Boot OAS/PaC and old behavior proof, exact staged inventory.
- Rollback: Before commit withdraw only authorized paths. After commit a separately approved focused revert.
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCorrelationLookupTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ListProcessInstancesByCorrelationQuery.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessInstanceCorrelationPageDTO.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessController.java`, `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-instance-correlation-v1.json`, `docs/egon/spec/2026-11-05-20-36-owned-process-correlation-lookup.md`, `docs/egon/plan/2026-11-05-20-37-owned-process-correlation-lookup.md`, `docs/egon/evidence/2026-11-05-owned-process-correlation-lookup.md`
- Commit: feat(process): discover owned starts by exact correlation.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Outcome |
| --- | --- | --- |
| R1 | root/r1/ProcessControllerTest.java on baseline | missing route actual 404, expected 200, one test |
| R2 | Root C Query A + Page B, PS record-only Store A + Service A, real B ProcessCorrelationLookupTest | actual 0 visible vs expected 20, one test, no JDBC call |
| R3 | root/r3/OrchestrationContractTest.java, still C Query A | missing exception for window 31d+1µs, one test |
| G1 | All B source, core exact selector | all owned classes nonzero and zero failures/errors/skips |
| P1 | Existing clean offline permission-producer verify | actual packaged OAS and exact PaC delta, 27 SQL untouched |
| S1 | Strict docs, exact diff, one commit | no I/E claim |

```sh
correlation_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
correlation_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
correlation_base=d5ccd8225da3c8f27adffdee04cb9d1d787ed7f2
correlation_core=OrchestrationContractTest,ProcessCorrelationLookupTest,ProcessControllerTest,ProcessPermissionIntegrationTest,ProcessExecutionServiceTest,ProcessStorageConfigurationTest
correlation_finite() {
  JAVA_HOME="$correlation_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$correlation_settings" -s "$correlation_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
correlation_finite 'ProcessControllerTest#lists_exact_owned_process_receipts_by_correlation'
correlation_finite 'ProcessCorrelationLookupTest#returns_only_exact_owned_correlation_receipts_and_binds_the_cursor'
correlation_finite 'OrchestrationContractTest#rejects_invalid_process_correlation_queries'
correlation_finite "$correlation_core"
test "$(git rev-parse --verify HEAD)" = "$correlation_base"
JAVA_HOME="$correlation_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$correlation_settings" -s "$correlation_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$correlation_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-owned-process-correlation-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$correlation_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-20-36-owned-process-correlation-lookup.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-20-37-owned-process-correlation-lookup.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

Run each R command only at its declared source phase, and record one actual assertion failure with 0 errors/skips. No opt-in I/E classes. P1 reads actual PS exec Boot artifact, checks unique root META-INF/tianji/openapi/process-instance-correlation-v1.json matching source byte-for-byte and exact closed schema, operationId and OAuth. Require exact PaC delta one API resource with GET+HEAD same path, existing READ plus tianji:process:read, current USER/SERVICE, one EN label, no permission definition or preexisting resource change. Verify old default-off config, declared no POM/DDL/trace and 27 SQL byte preservation. Existing wrapper and inline archive inspection suffice; no new repo tooling.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration, schema/config, activation or grants. Existing Process API remains default false. Exact additive resource has separate current catalog authority. Real I may assess physical plan and timeout later. Web/SDK separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | 1 | R1/G1/P1 |
| REQ-002 | 1 | R2/R3/G1 |
| REQ-003 | 1 | R2/R3/G1 |
| REQ-004 | 1 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

No physical scan or SLO claim. Current scope covers exact correlation discovery only. Another owner, direct business-key search, trace changes, new index, actual PG/I, deployment and S12 remain separate.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | Existing C/PS boundary | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | Existing permission/JDBC/receipt adapters | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | Exact Query/DTO/adapter symbols | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | Current authority, query, exact cursor | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | Immutable C value DTOs | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | No mapping layer or PO | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | No new log/diagnostic echo | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Existing named beans and injection | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing JDK and length-safe digest | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 | Closed wire/OAS and exact errors | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | UTC microsecond/DB clock | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14 | No config or profile changes | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Existing owner query adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | One exact route, zero schema/trace scope | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Finite plus actual package and separate I | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Source review, runtime separate | None |

**PASS — Ready for user review**
