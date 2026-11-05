# T4-03 历史 Tool START 正向收据对账实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:29 Asia/Shanghai |
| Updated | 2026-11-06 05:29 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | 11 Java paths and three review artifacts |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 2c886ae84e01e01d095a108bcbe36af86b3a80e7 |
| Implements Spec | [Historical receipt reconciliation Spec](../spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:28 Asia/Shanghai SHA-256 e87b2beb0d287e3bbf0ca6c727723e837cbb067e5468bff18bcc7225f29c66a8 |
| Effective Specs | [Historical receipt reconciliation Spec](../spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md) |
| Depends On Plans | [Process receipt SDK](2026-11-06-05-06-process-start-receipt-sdk.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Explicit resume host](2026-11-06-05-07-agent-checkpoint-as-resume-host.md) |

## 1. Summary

使用现有 V13 历史和 Process START 收据，给确证 APPLIED 的原 dispatch 追加一份正向证据。AS 负责使用原 SERVICE 的独立当前 READ 调用已有 SDK。AR 负责当前历史许可、数据源和写入栅栏。原执行事实、预算和工具结果保持原值。

## 2. Target Spec and Effective Design

[Historical receipt reconciliation Spec](../spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md) §§4/7/9/11/14 defines exact owned history, independent authority, fixed GET and positive observation. Keep other operations and I/E acceptance independent.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9/11 | Exact verified owner, original dispatch and coherent stopped V13 snapshot. |
| REQ-002 | §7/9 | Historical current permission before GET and exact recorded SERVICE with current independent READ. |
| REQ-003 | §7/10/11 | Closed APPLIED proof, unchanged source guard and one scoped receipt insert only. |
| REQ-004 | §7/9/11 | Repeat uses current authority and returns matching first evidence; contradictory evidence fails closed. |
| REQ-005 | §14/16/18 | Typed RED, exact finite suites, actual clean artifacts and immutable migration byte parity. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Freeze exact baseline and Spec digest. Add finite tests and assemble the typed source unit in declared order. Run isolated negatives and restore candidate guards before the finite GREEN and clean package gates.

### 4.2 Test-first strategy

R1 removes only the exact recorded SERVICE versus verified SERVICE equality in the AS read boundary. The focused test rejects mismatched SERVICE before GET. R2 verifies the separate historical authorization boundary in the finite AR service. Replace the second authorize(request, window) result with beforeRead. The revoked current authority must otherwise produce one missing AgentLedgerException in the finite service test. R3 inverts only the invocation stamp equality in the conditional append and fails the focused SQL assertion.

### 4.3 Sequential and parallel boundaries

AR service and storage plus AS adapter may be drafted separately under one frozen contract. Apply the complete combined Step to the owning repository and run typed negatives, full finite suite and artifact proof in sequence.

### 4.4 Commit boundaries

One focused commit covers the first complete callable positive observation path. Stage only exact frozen paths after tests, document checks and package review.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Plan decision |
| --- | --- | --- | --- |
| AR historical append command | Required, §7/11 | Existing V13 read validators and V2/V5 owner-operation key | Reuse authoritative history and receipt table without adding DDL. |
| AS receipt adapter | Required, §7/9 | Fixed START bindings and independent existing receipt SDK | Derive original command and call only exact existing Process GET. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| AR history, authority and append | REQ-001/002/003/004 | R2/R3/G1/P1 | Existing V13 validation, V2/V5 and JDK/PostgreSQL time semantics |
| AS fixed Process read | REQ-002/003 | R1/G1/P1 | AR boundary and established receipt SDK |
| Documentation and package parity | REQ-005 | P1/S1 | Actual source gates and package output |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | AR JDBC and AS Process adapter ownership | Maintain explicit internal command with same source and existing SDK. |
| Persistence | V13 historical validation and V2/V5 receipt | Add exact operation and closed result with guarded append only. |
| Transactions and time | AgentCheckpointTakeoverService short transactions and primary checks | Bound statements, locks, read, authorizations and remote observation. |
| Verification | Existing JDK HTTP fixture and finite JDBC tests | Use typed negatives and actual clean artifacts with runtime gates separate. |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Verification |
| --- | --- | --- | --- | --- | --- |
| History | ToolActionIntentSql | EVD-001 typed row and Run validation | Existing query callers have live or preselected action constraints | Extend for exact owned historical lookup | TEST-001 |
| Read | ProcessStartReceiptClient | EVD-003/004 existing fixed GET and transport | Requires independently verified original executor READ | Adapt via sealed fixed target | TEST-002 |
| Evidence | agent_run_command_receipt | EVD-002 full-owner operation key | Operation-specific schema and append guard needed | Reuse existing table and trusted writer | TEST-001/003 |

### 4.8 User-mandated Java Rule Implementation Matrix

```text
1 类名规范，必须以 java的pojo规范命名。以dao po bo vo dto query command event等结尾
2 每层之间必须被 springboot-validation 校验，复用对象使用分组校验；基于原生和自定义约束注解、@Valid、@Validated 及 ConstraintValidator 扩展实现。ValidationUtils 只承担通用手工校验，不承载全部业务校验；电话号码复用 libphonenumber。
3 Java POJO 默认使用 class，注解为 @Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)，按构造目标选择 @Builder 或 @SuperBuilder；父类状态参与相等性时使用 @EqualsAndHashCode(callSuper = true)。只有不可变 value object 可以使用 record，并豁免 class 的 Lombok 规范；普通实体不能因简单而使用 record。使用 MapStruct、MapStructPlus 和 egon-cola-component-common-core 的 BaseConverter（双向）或 BaseForwardConverter（不可逆投影）进行转换。
4业务类必须使用@Slf4j注解注入log对象。如果业务类被spring管理，必须指定名称，如果是单例的情况下，参考@Service("userService")。如果需要依赖注入，必须@RequiredArgsConstructor进行修饰，不要代码中写。且属性必须被@qualify修饰。
5 工具类只允许使用jdk原生、Apache Commons(commons-lang3、commons-collections4、commons-io、commons-text、commons-codec、commons-beanutils)、Guava。针对Tika按需引入。
6 JSON 使用 Spring Boot Jackson；持久化枚举值使用 @EnumValue，向前端输出的枚举值使用 @JsonValue，禁止 ordinal 作为业务编码。
7 springboot 多环境配置文件，必须保持配置一致，但值不一定一致。
9 复杂业务必须引入设计模式，不允许硬编码
10 日期相关的必须使用java.time下的实体类，不允许使用java.util下的
11 只允许现有三层结构或当前 egon-cola-archetypes 的 DDD 结构，三层结构保持现状。必须尽量复用 egon-cola-components；持久化模块必须使用 Egon COLA MP Starter，DDL 统一由 egon-mp-sdj-ext-starter 分布式管理。业务唯一键必须组合业务列与 deletedAt（数据库 deleted_at），并验证 NULL、租户及重复软删除语义。采用 CQE（Command Query Event）；Event 必须经 egon-cola-component-transactional-outbox-starter 或 MQ 中间件投递。
```

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Exact historical command, transport and persistence type names match their owning modules. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Closed owner and target input, provenance, current decisions and positive result are checked at their boundary. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Immutable records carry validated historical evidence, with no mutable business DTO projection. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Explicitly constructed internal components keep diagnostic representations redacted and add no discovered bean. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing SHA-256, Spring JDBC, Jackson, JDK HTTP and the Process SDK. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use closed versioned JSON and named fixed status with exact persisted digest and scalar types. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Preserve configuration and profiles by exact source and packaged byte checks. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Reuse the SDK Adapter and JDBC Ledger around one focused reconciliation command. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use UTC Instant for observed moments and Duration/monotonic nanos for bounded elapsed work. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Preserve AR ledger and AS adapter boundaries, add one receipt operation and retain existing event ownership. | R1/R2/R3/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | V13 dispatch_not_after, current permit times | Occurred instant | java.time.Instant | UTC instant | Internal | UTC Agent primary and current authority | Not applicable: no display | Original cutoff elapsed and current authorization valid when appending | Database clock and exact microsecond comparison | Existing original authorization is historical provenance | Step 1 TEST-001/003 |
| TIME-002 | Process recordedAt and Agent created_at | Occurred instant | java.time.Instant | UTC instant | UTC Z | Separate Process and Agent clocks | Not applicable: no display | No ordering inference across two databases | Preserve Process UTC microseconds and Agent primary observation time | Existing closed Process receipt contract | Step 1 TEST-002/003 |
| TIME-003 | Operation, transport, statement and lock windows | Duration | java.time.Duration and monotonic elapsed nanos | Not persisted | Internal | Not applicable: duration measured by local monotonic clock | Not applicable: no display | Bound calls and lock waits independently of wall clock or DST | JDK monotonic time and local PostgreSQL timeouts | Existing bounded HTTP SDK and JDBC transaction semantics | Step 1 TEST-001/002/003 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java
CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationServiceTest.java
CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSqlTest.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/HistoricalToolReceiptAuthorization.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartReceiptRead.java
MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSql.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationService.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java
CREATE docs/egon/spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md
CREATE docs/egon/plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md
CREATE docs/egon/evidence/2026-11-06-tool-start-receipt-reconciliation.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

The source gate uses JDK 25 and existing offline settings. The existing trusted ledger writer and isolated operator authorization are prerequisites for real deployment. Default application configuration, role provisioning, Flyway bytes, and production routes are outside this Step.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Seal positive historical START receipt evidence

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: V13 typed source, V2/V5 receipt owner key, AS fixed START binding and exact existing Process receipt SDK.
- Baseline state: Process can read a positive START receipt, and AR has no exact durable historical observation for a stopped original Tool dispatch.
- Observable outcome: The internal command can append one exact positive receipt under independent current authority; absent proof and contradictory replay refuse mutation.
- End state: All declared Java and review artifacts are committed in one focused source change.
- Test-first gate: Required. R1, R2 and R3 fail with one specified assertion each when the exact guarded branch is bypassed.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java`

- Purpose: Exercise independent READ and exact Process GET through an in-memory JDK transport.
- Symbols: TianjiFixedBpmnStartReceiptReaderTest.
- Repository evidence: EVD-003/004.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Exercise independent READ and exact Process GET through an in-memory JDK transport.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Bind original actor and typed dispatch; record request without exposing credential.
- Error and edge behavior: Wrong verified SERVICE, malformed response and nonpositive receipt stay unavailable.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
bindExactHistoricalDispatchToOriginalProcessCommand();
assertAnotherVerifiedServiceCannotReachTheReadOnlyHttpClient();
assertMatchingServiceIssuesOneScopedGetWithIndependentProcessDigest();
assertMissingReceiptAndMismatchedTargetCannotProducePositiveEvidence();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationServiceTest.java`

- Purpose: Cover stopped exact historical evidence, authorization ordering and append-only replay.
- Symbols: ToolStartReceiptReconciliationServiceTest.
- Repository evidence: EVD-001/002/005.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Cover stopped exact historical evidence, authorization ordering and append-only replay.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Use controlled clocks, current policy decisions and recording transaction/transport doubles.
- Error and edge behavior: Reject drift and expiry, block unauthorized reads, verify no repeated mutation.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
sealOwnedStoppedDispatchInBoundReadOnlyTransaction();
assertHistoricalAuthorizationPrecedesExactlyOneExternalRead();
assertRevokedHistoricalDecisionAfterGetCannotAppend();
assertExactConcurrentWinnerReplaysOriginalAckAndContradictoryEvidenceFails();
assertInvalidActualReturningReceiptRollsBackBeforeCommit();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSqlTest.java`

- Purpose: Prove exact historical scope and positive append predicate with controlled JDBC.
- Symbols: ToolStartReceiptReconciliationSqlTest.
- Repository evidence: EVD-001/002/005.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Prove exact historical scope and positive append predicate with controlled JDBC.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Capture actual prepared operation and the four source stamps in predicate order.
- Error and edge behavior: Fail on missing or changed guard, malformed persisted evidence or an invalid read transaction.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertExactFullOwnerReceiptKeyAndIndependentOperation();
inspectConditionalAppendForAllFourSourceStamps();
assertInvocationStampEqualityRejectsChangedHistoricalSource();
assertReadOnlyRepeatableReadGateForHistoryAndClosedStoredEvidence();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/HistoricalToolReceiptAuthorization.java`

- Purpose: Define independent current AS authority for an exact historical receipt command.
- Symbols: HistoricalToolReceiptAuthorization.
- Repository evidence: EVD-001/005.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Define independent current AS authority for an exact historical receipt command.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Bind verified owner, original source digest and short-lived audit fields.
- Error and edge behavior: Missing, expired, denied or binding mismatch blocks use of Process evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
defineOneHostProvidedHistoricalAuthorizationContract();
bindCurrentPermitToExactOwnedSourceAndProcessQueryDigest();
validateDecisionEvidenceAndShortExpiryBeforeNetworkAndAppend();
rejectUsingHistoricalDispatchDecisionAsCurrentAuthorization();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartReceiptRead.java`

- Purpose: Model the narrow historical Process read and its independently bound result.
- Symbols: FixedBpmnStartReceiptRead.
- Repository evidence: EVD-003/004.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Model the narrow historical Process read and its independently bound result.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Seal original dispatch target, recorded executor and canonical Process START query.
- Error and edge behavior: Unbound or inconclusive result cannot yield append evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
takeVerifiedHistoricalDispatchAndBindItsOriginalFixedBackend();
deriveProcessReadQueryFromTheCanonicalOriginalStartCommand();
returnOnlyPositiveReceiptWithMatchingBoundTargetAndServiceIdentity();
preserveNoProofForMissingOrMalformedResponse();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`

- Purpose: Load exact stopped historical Tool action and dispatch provenance using current validators.
- Symbols: ToolActionIntentSql.
- Repository evidence: EVD-001/005.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Load exact stopped historical Tool action and dispatch provenance using current validators.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Use existing closed row mapping and exact owner, intent, invocation and Run joins.
- Error and edge behavior: Reject active lease, incomplete pairs, wrong original executor, malformed source and missing evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
selectExactOwnerIntentAndDispatchRunWithPairedInvocation();
reuseClosedToolRowAndHistoricalRunProvenanceValidation();
sealActionInvocationAndRunStampsForConditionalInsert();
requireStoppedRunWithoutLeaseAndElapsedOriginalDispatchWindow();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSql.java`

- Purpose: Read and append exact positive evidence within the existing command receipt key.
- Symbols: ToolStartReceiptReconciliationSql.
- Repository evidence: EVD-001/002.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Read and append exact positive evidence within the existing command receipt key.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Write only the new operation, with original Run, immutable request and closed result.
- Error and edge behavior: Zero rows require fresh exact replay or refusal; differing request or evidence is a conflict.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
selectExactPriorReceiptByOwnerOperationIntentAndEpoch();
appendPositiveEvidenceOnlyIfTheOriginalSourceStampsStillMatch();
applyExistingFullOwnerUniqueKeyWithOnConflictDoNothing();
validateStrictStoredResultAndKeepFirstEvidenceOnReplay();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationService.java`

- Purpose: Coordinate independent source, authorization, HTTP and write phases.
- Symbols: ToolStartReceiptReconciliationService.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Coordinate independent source, authorization, HTTP and write phases.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Match current authority before and after the bounded read; only exact positive evidence is appended.
- Error and edge behavior: Use separate transactions, reject ambient calls and avoid persistent execution state changes.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
readAndSealTheOriginalDispatchInBoundedReadOnlyTransaction();
obtainCurrentHistoricalPermitBeforeCallingProcessReceiptReader();
verifySecondCurrentDecisionAndAppendWithDatabaseDeadlineRecheck();
validateReturnedPersistedEvidenceBeforeCommittingGuardedInsert();
returnOriginalMatchingAcknowledgementOrFailWithoutChangingExecution();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java`

- Purpose: Reuse the exact immutable START binding for history without a fake live lease.
- Symbols: TianjiFixedBpmnStartToolBindings.
- Repository evidence: EVD-003.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Reuse the exact immutable START binding for history without a fake live lease.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Resolve historical Run material and tool descriptor through the same pinned backend.
- Error and edge behavior: Reject mismatched tenant, owner, policy, definition, tool or backend.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
extractOnePureExactResolverForExistingLiveAndHistoricalCallers();
matchOwnerPolicyDefinitionToolAndBackendThroughExistingBinding();
keepLiveLeaseVerificationAtTheOriginalCallBoundary();
rejectAnyBindingDriftBeforeIndependentCredentialAcquisition();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java`

- Purpose: Share the exact existing native receipt reference with the historical adapter.
- Symbols: TianjiFixedBpmnStartToolTransport.
- Repository evidence: EVD-003/004.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Share the exact existing native receipt reference with the historical adapter.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Retain existing canonical formula and derive the same reference from verified instance ID.
- Error and edge behavior: Reject result that cannot match original exact native receipt reference.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
extractExactNativeReceiptReferenceFromVerifiedCommandAndInstance();
keepCurrentLiveStartPathUsingTheSameCanonicalReferenceFormula();
deriveHistoricalReferenceFromTheOriginalCommandAndPositiveReceiptInstance();
proveExistingStartCredentialAndTransportSemanticsArePreserved();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 11 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java`

- Purpose: Use the existing receipt SDK with independent current original SERVICE READ.
- Symbols: TianjiFixedBpmnStartReceiptReader.
- Repository evidence: EVD-003/004.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Use the existing receipt SDK with independent current original SERVICE READ.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Bind fixed original command and require exact recorded SERVICE before the sole GET.
- Error and edge behavior: Reject incorrect bearer identity, wrong scope, malformed receipt and out-of-window proof.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
resolveFixedHistoricalTargetWithoutFabricatingLiveLease();
requireIndependentCurrentReadCredentialForExactRecordedService();
useExistingProcessSdkForOneExactGetWithCanonicalProcessRequestSha256();
returnClosedPositiveReceiptAndOriginalNativeReferenceWithinDeadline();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 12 — `CREATE docs/egon/spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md`

- Purpose: Record exact scope, current authorization and immutable database semantics.
- Symbols: Effective historical receipt Spec.
- Repository evidence: Master §17 S11 and §19 T4-03.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Record exact scope, current authorization and immutable database semantics.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Describe historical source and positive evidence boundaries.
- Error and edge behavior: Reject scope claims unsupported by the finite source gates.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordExactBaselineAndOwnedHistoricalCommand();
describeSeparateCurrentAuthoritiesAndSingleProcessRead();
documentGuardedInsertReplayAndAbsenceOfRuntimeActivation();
linkPlanAndIdentifyIndependentOperatorAcceptance();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 13 — `CREATE docs/egon/plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md`

- Purpose: Lock file scope, typed negative tests, finite suite and artifacts.
- Symbols: Historical receipt implementation Plan.
- Repository evidence: Effective Spec §§4/7/14.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Lock file scope, typed negative tests, finite suite and artifacts.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Declare implementation order and precise verification.
- Error and edge behavior: Reject unexpected tests, incorrect baseline or artifacts containing extra changes.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockOrderedSourcePathsAndExactSpecRevision();
selectTypedAuthorityAndServiceIdentityNegativeCases();
verifyActualFiniteTestsAndPackageParityBeforeStaging();
recordTheSingleFocusedCommitAfterReview();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

#### File 14 — `CREATE docs/egon/evidence/2026-11-06-tool-start-receipt-reconciliation.md`

- Purpose: Record actual observed finite results and operator boundary.
- Symbols: Historical receipt source evidence.
- Repository evidence: Effective Spec §14.
- Dependencies and consumers: See the owning path and §4.6 source chain.
- Why now: Record actual observed finite results and operator boundary.
- Contract/signature changes: Internal Java or review artifact, detailed in effective Spec §9.
- Input/output and state mapping: Write observed test names, counts, package evidence and unchanged migrations.
- Error and edge behavior: Mark isolated runtime proof pending unless actually executed.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordObservedTypedNegativeAssertionsAndFinitePassCounts();
recordActualArchiveContentsAndUnchangedSchemaBytes();
separateSourceProofFromPostgresqlIamAndProcessOperatorProof();
linkEffectiveSpecAndPlanToTheCommittedEvidence();
```

- Verification contribution: R1/R2/R3/G1/P1/S1.
- After this file: Verify the declared source, inputs and owning test boundary.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact R1/R2/R3/G1/P1/S1 in §8.
- Expected result: Typed negative gates fail for the specified assertion; selected finite tests, actual package and document gates pass.
- Failure returns to: First owning failed assertion or package proof; restore all guards before further work.
- Completion criteria: Exact source, test and evidence scope is reviewed, finite gates pass, and actual live acceptance stays independently recorded.
- Rollback: Revert the single focused commit; existing rows remain isolated under the new receipt operation.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSqlTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/HistoricalToolReceiptAuthorization.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartReceiptRead.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolStartReceiptReconciliationService.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java`, `docs/egon/spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md`, `docs/egon/plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md`, `docs/egon/evidence/2026-11-06-tool-start-receipt-reconciliation.md`.
- Commit: feat(agent): reconcile positive historical START receipts.

## 8. Test, Validation, and Quality Gates

```sh
history_base=2c886ae84e01e01d095a108bcbe36af86b3a80e7
history_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse HEAD)" = "$history_base"
history_r1='TianjiFixedBpmnStartReceiptReaderTest#refuses_a_different_verified_service_before_get'
history_r2='ToolStartReceiptReconciliationServiceTest#a_revoked_historical_decision_after_get_cannot_append'
history_r3='ToolStartReceiptReconciliationSqlTest#checks_all_four_stamps_in_the_conditional_append'
history_core='ToolStartReceiptReconciliationServiceTest,ToolStartReceiptReconciliationSqlTest,TianjiFixedBpmnStartReceiptReaderTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest,ProcessStartReceiptHttpTest,TianjiFixedBpmnStartToolBindingsTest,TianjiFixedBpmnStartToolTransportTest'
# Run each selector with its typed negative candidate and then restore the guard.
"$history_runner" "$history_r1"
"$history_runner" "$history_r2"
"$history_runner" "$history_r3"
"$history_runner" "$history_core"
STELLAR_VERIFY_GOAL=verify "$history_runner" "$history_core" clean -Ppermission-producer -Dpermission.commit="$history_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-tool-start-receipt-reconciliation
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | Exact recorded SERVICE guard removal yields one missing IllegalStateException before GET in the new AS test. |
| R2 | Replace the second authorize(request, window) result with beforeRead. The revoked current authority must otherwise produce one missing AgentLedgerException in the finite service test. |
| R3 | Invert only invocation stamp equality to yield the Missing exact CAS stamp for i assertion. |
| G1 | New 7 AS, 5 AR SQL and 7 AR service, plus 10 ToolActionWriteSqlTest, 23 ToolActionIntentStoreTest and 6 ProcessStartReceiptHttpTest, plus 11 TianjiFixedBpmnStartToolBindingsTest and 32 TianjiFixedBpmnStartToolTransportTest cases. Exactly 101, with zero failures, errors or skips. |
| P1 | Repeat G1 in clean permission-producer; actual AS package contains the explicit adapter and nested AR command. Compare existing configurations, semantic PaC and 28 existing SQL files. |
| S1 | Strict Spec/Plan and exact commit scope. Separate live acceptance remains visible. |

## 9. Migration, Compatibility, Rollout, and Rollback

Use V2/V5 exact receipt operation and existing write identity. Preserve all existing SQL and interfaces. Explicit host configuration and current authority are independent operator conditions. Source rollback uses the focused commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File boundary | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | AR verified source and reconciliation service | R2/G1 |
| REQ-002 | Step 1 | AR authorization and AS adapter | R1/R2/R3/G1 |
| REQ-003 | Step 1 | AR guarded receipt and AS positive read | G1/P1 |
| REQ-004 | Step 1 | AR strict persisted receipt and replay | G1/P1 |
| REQ-005 | Step 1 | Exact combined files and review artifacts | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Current historical permission, original SERVICE READ, production activation and isolated live verification are separate operator acceptance. Independent writer privilege isolation and effect promotion require a different approved design.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | AR owns historical orchestration and JDBC; AS owns Process credential and SDK read. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse V13 history, V2/V5 receipts, fixed target and existing exact SDK. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use JDK and established Spring, Jackson and test classpaths. | None |
| MC-NAME-001 | Applicable | PASS | §8–10 | Use focused Java names for observation, authorization and persistence. | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Check exact owner, independent authority, paired provenance and closed response. | None |
| MC-MODEL-001 | Applicable | PASS | §10/11 | Use immutable typed evidence, redacted private stamps and closed JSON. | None |
| MC-CONVERT-001 | Applicable | PASS | §9/10 | Existing typed canonical digest and explicit storage serialization boundaries. | None |
| MC-LOG-001 | Applicable | PASS | §15 | Fixed errors and redacted private metadata prevent credential or SQL leakage. | None |
| MC-BEAN-001 | Not applicable | N/A | §5/16 | Explicit host construction preserves ordinary application discovery. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse SHA-256 helper and existing HTTP and JDBC infrastructure. | None |
| MC-JSON-001 | Applicable | PASS | §10/11 | Validate strict versioned receipt and positive Process response. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7/11 | Separate source/observation clocks and bound operation, statements and locks. | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | No runtime setting or permission change; verify package parity. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse SDK Adapter and JDBC Ledger within a direct command service. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Audit exact planned files and all 28 existing SQL migrations. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Declare typed negative, finite, artifact and separate operator proof. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Current authority provisioning and PostgreSQL acceptance have independent operator gates. | None |

### 12.6 Final verdict

PASS — Ready for user review
