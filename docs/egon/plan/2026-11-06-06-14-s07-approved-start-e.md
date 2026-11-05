# S07 原请求者与真实审批 START 实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-14-s07-approved-start-e.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:14 Asia/Shanghai |
| Updated | 2026-11-06 06:14 Asia/Shanghai |
| Owner | Tianji Agent Server and Process Server test fixtures |
| Repository | tianji-service |
| Scope | Nine Java test paths and three review artifacts |
| Source Requirement | Master §17.2 S07 and §19 T3-07/T4-05 |
| Baseline Revision | 0f15cf7ec45952e3e2b217358eb8493c1d5285cd |
| Implements Spec | [S07 approved operator Spec](../spec/2026-11-06-06-13-s07-approved-start-e.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 06:13 Asia/Shanghai SHA-256 7424f4d07dbdc7fcd5380a01791b4e229a9fa60305fae37aa9daba7519de9690 |
| Effective Specs | [S07 approved operator Spec](../spec/2026-11-06-06-13-s07-approved-start-e.md) |
| Depends On Plans | [S07 governed START](2026-11-06-05-52-s07-governed-start-e.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Approved Process Tool assembly](2026-11-01-14-51-approved-process-tool-assembly.md) |

## 1. Summary

增加独立审批 E 入口。原 USER 提交原生来源并由不同 USER 执行真实任务审批，当前独立 verifier SERVICE 确认原始 V7 证据，实际受治理 Tool 使用 START SERVICE 派发一次。

## 2. Target Spec and Effective Design

[S07 approved operator Spec](../spec/2026-11-06-06-13-s07-approved-start-e.md) §§4/7/14 defines exact source binding, genuine reviewer, existing IAM and the independent operator-only positive effect.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/16 | Separate private approved mode and preserved earlier selectors. |
| REQ-002 | §7/11 | Original V13 waiting values form the exact native requester source. Actual distinct eligible reviewer signs true approval. |
| REQ-003 | §7/9 | Opaque lookup uses exact source and current version. Separate current PS SERVICE verifies real receipt. |
| REQ-004 | §7/11/14 | Native approval consumed and one exact SERVICE-owned target START receipt plus native task. |
| REQ-005 | §14/16/18 | Typed finite, actual packaging and independent pending E evidence. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Pin current Service and effective Spec. Extend exact shared S07 fixture under a separate approved mode. Apply each typed negative, restore candidate, run finite verification and inspect real packages.

### 4.2 Test-first strategy

R1 removes only the current version equality guard before reading the opaque native receipt. R2 removes only the approval release model-set equality. Each mutated candidate must fail one exact missing IllegalStateException assertion.

### 4.3 Sequential and parallel boundaries

AS and PS source preparation can be parallel inside the agreed fixture contract. Integrate both child paths and close exact scopes before running source gates. Keep actual operator proof separate.

### 4.4 Commit boundaries

One focused test-source commit after finite gates, archive checks and exact stage scope.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Plan decision |
| --- | --- | --- | --- |
| Separate approved fixture | Required, §7 | Existing IDW E and child guards | Preserve previous modes and add exact approved opt-in. |
| Native approval source and reviewer | Required, §7/11 | Existing V7 provenance, protected reviewer routes and pinned metadata | Use existing real BPMN and distinct USER reviewer. |
| Opaque selection and actual verifier | Required, §7/9 | Existing resolver and independent SERVICE approval verification | Select exact native reference under read-only role and verify current scope. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| AS profile and source-bound selector | REQ-002/003 | R1/G1 | Existing S07 child and approved Tool bridge |
| PS approved source model and driver | REQ-001/002/004 | R2/G1/P1/E1 | Existing harness and V7 native task |
| Exact package and scope | REQ-005 | S1 | Frozen source and finite artifacts |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Separate AS/PS and existing approved Tool | Reuse existing owner boundaries. |
| Persistence | V7 native task receipt and V13 approved action | Read exact witnesses using existing observer roles. |
| Validation | Existing model validator and canonical hash | Pin approval and target, reject digest and version drift. |
| Verification | Existing JDK/Maven/Boot source gates | Exact finite, package, and separate operator proof. |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Verification |
| --- | --- | --- | --- | --- | --- |
| Isolated orchestration | Existing ExpenseAcceptanceHarness | EVD-001 | Approved lane needs separate mode | Reuse lifecycle and exact artifact lock. | TEST-002/003 |
| Native approval | PS V7 and governed human task | EVD-003/004 | Requires exact source BPMN | Reuse real native claimant and completion. | TEST-003 |
| Scope and receipt | Actual ApprovalVerifier and V13 recorder | EVD-002/003/005 | Fixture must select opaque reference | Use existing current verification with independent SERVICE. | TEST-001/003 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Scope exact S07 approved fixture and finite test classes. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Validate original owner, current version, source binding and reviewer provenance. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing immutable Agent, Tool and Process wire contracts. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Register the qualified test-only receipt selector only for the approved mode. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use JDK, current JDBC, JUnit and existing SDK with no dependencies added. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing Jackson, SHA and canonical Agent definition codec. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep packaged production configuration stable and add only explicit private test inputs. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Reuse Test Fixture and SDK Adapter patterns. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use bounded waits and UTC approval time contracts. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep current ownership, persistence and native Process responsibility. | R1/R2/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Existing driver readiness, Run poll and observer windows | Duration | java.time.Duration and bounded waits | Not persisted | Internal | Not applicable: elapsed test time | Not applicable: no display | Existing child and network limits apply | Existing monotonic wait, bounded query | Preserve previous operator selectors | Step 1 TEST-002/003 |
| TIME-002 | Native approval occurrence and verified expiry | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: elapsed age | Not applicable: no display | Tighten actual verification deadline using existing PS checks | Existing database and issuer clocks | Existing runtime time rules | Step 1 TEST-003 |

## 5. Change File Tree

```text
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixtureTest.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixture.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovedStartAcceptanceIT.java
CREATE docs/egon/spec/2026-11-06-06-13-s07-approved-start-e.md
CREATE docs/egon/plan/2026-11-06-06-14-s07-approved-start-e.md
CREATE docs/egon/evidence/2026-11-06-s07-approved-start-e.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Use JDK25 and the existing finite reactor. Operator E requires private exact mode, artifact locks, current isolated IAM and existing V14/V10/V2 fixture stores. Production grants and schema remain independently owned.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add isolated S07 native approval operator fixture

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing S07 test harness and shared child guards, approved Tool bridge, native Process task and approval verification.
- Baseline state: S07 IDW fixture covers independent current user and Service without a native approval chain.
- Observable outcome: Separate operator source correlates native requester, reviewer, independently verified receipt and one target START.
- End state: Nine test Java paths and three review documents committed as one Step.
- Test-first gate: Required. Run exact R1 and R2 and restore guards.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`

- Purpose: Finite approved binder, exact source guard and explicit waiting marker tests.
- Symbols: S07GovernedStartAgentFixtureTest.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Finite approved binder, exact source guard and explicit waiting marker tests.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
bindApprovedPolicyWithProductionSpringBinder();
rejectStaleVersionBeforeOpaqueReceiptRead();
assertOnlyApprovedProfileAcceptsWaitingOutput();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixtureTest.java`

- Purpose: Finite native model validation and altered published hash rejection.
- Symbols: S07ApprovalFixtureTest.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Finite native model validation and altered published hash rejection.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
validateGovernedBpmnWithExistingModelValidator();
compareExactApprovalConsumerRoleAndSourceStartFields();
assertChangedApprovalModelDigestIsRejected();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: Admit only exact approved operator suite and set its trusted child flags.
- Symbols: ExpenseAcceptanceHarness.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Admit only exact approved operator suite and set its trusted child flags.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
selectExplicitS07ApprovedStartSuite();
emitSharedS07FlagAndSeparateApprovalFlag();
retainIndependentPriorEvidenceAndDispatchModes();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixture.java`

- Purpose: Pin the native BPMN, exact reviewer and typed Process source contract.
- Symbols: S07ApprovalFixture.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Pin the native BPMN, exact reviewer and typed Process source contract.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
renderExactOneReviewerRoleInNativeApprovalBpmn();
registerExistingApprovalConsumerWithExactFixedPolicy();
verifyActualPublishedReleaseModelDigestAndInputs();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`

- Purpose: Require exact opted-in human task and approval verification flags in the Process child.
- Symbols: ExpenseProcessAcceptanceMain.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Require exact opted-in human task and approval verification flags in the Process child.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
loadPrivateChildProperties();
rejectMixedOrIncompleteApprovalChildFlags();
startExistingProcessApplicationOnlyInSelectedOperatorPath();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`

- Purpose: Pin APPROVAL_REQUIRED Tool, independent verifier and opaque receipt selection.
- Symbols: S07GovernedStartAgentFixtureConfiguration.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Pin APPROVAL_REQUIRED Tool, independent verifier and opaque receipt selection.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
buildExactTenantSpecificApprovedDefinitionAndTransportBindings();
selectExactNativeReferenceOnlyAfterCurrentBusinessVersionComparison();
returnWaitingMarkerWithoutPositiveProofInApprovedFixture();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: Register qualified opaque resolver only in the approved fixture initializer.
- Symbols: ExpenseAgentAcceptanceMain.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Register qualified opaque resolver only in the approved fixture initializer.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
prepareExistingS07FixturePlanWithSharedChildGuard();
registerExactQualifiedReceiptResolverOnlyForApprovalMode();
keepExistingOrdinaryAndS12ChildBranches();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: Drive genuine requester and reviewer through actual approved START.
- Symbols: BusinessProcessAgentAcceptanceIT.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Drive genuine requester and reviewer through actual approved START.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
publishAndVerifyExactApprovalSourceBeforeAgentChild();
startNativeApprovalAsOriginalUserUsingPersistedActionValues();
claimAndCompleteAsDistinctReviewerThenVerifyExactNativeReceipt();
startSecondActualAgentRunAndAssertOneConsumedApprovalAndTargetStart();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 9 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovedStartAcceptanceIT.java`

- Purpose: Expose only independent operator selector.
- Symbols: S07ApprovedStartAcceptanceIT.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Expose only independent operator selector.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
openExactApprovedStartSuiteWithPrivateManifest();
verifyBothTenantsThroughExistingProductBridges();
closeExistingOwnedFixtureDriverAndItsChildren();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 10 — `CREATE docs/egon/spec/2026-11-06-06-13-s07-approved-start-e.md`

- Purpose: Record exact native approval design and operator scope.
- Symbols: 2026-11-06-06-13-s07-approved-start-e.md.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Record exact native approval design and operator scope.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordExistingAuthorityBoundaries();
pinSourceRoleAndBusinessVersion();
separateFiniteAndOperatorEvidence();
keepExistingApprovalAndIdwProductionScope();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 11 — `CREATE docs/egon/plan/2026-11-06-06-14-s07-approved-start-e.md`

- Purpose: Lock exact paths and verification gates.
- Symbols: 2026-11-06-06-14-s07-approved-start-e.md.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Lock exact paths and verification gates.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockExactCurrentBaselineAndSourcePaths();
runEachTypedMutationAndRestoreGuards();
verifyPackagedFixtureBoundary();
compareExistingPermissionsAndMigrationBytes();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.

#### File 12 — `CREATE docs/egon/evidence/2026-11-06-s07-approved-start-e.md`

- Purpose: Record actual observed proof and pending operator boundary.
- Symbols: 2026-11-06-s07-approved-start-e.md.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-owned AS/PS source boundaries in §4.6.
- Why now: Record actual observed proof and pending operator boundary.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact native source and existing typed fixtures under §4.6.
- Error and edge behavior: Reject inconsistent owner, source or package evidence.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordOnlyObservedRedAndFiniteResults();
recordArtifactAndMigrationParity();
identifyStillPendingOperatorRun();
linkSeparateOperatorManifestAndCurrentAuthorityInputs();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify exact test-source boundaries.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact source and separate operator gates in §8.
- Expected result: Exact intended RED assertion then finite and archive proof, no operator E report.
- Failure returns to: First failing mutation, source, or archive check. Restore candidate first.
- Completion criteria: Source and package boundary proven with actual E clearly pending.
- Rollback: Revert the single scoped test-source commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixtureTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovalFixture.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07ApprovedStartAcceptanceIT.java`, `docs/egon/spec/2026-11-06-06-13-s07-approved-start-e.md`, `docs/egon/plan/2026-11-06-06-14-s07-approved-start-e.md`, `docs/egon/evidence/2026-11-06-s07-approved-start-e.md`.
- Commit: test(process): prepare S07 native approval governed START acceptance.

## 8. Test, Validation, and Quality Gates

```sh
s07_base=0f15cf7ec45952e3e2b217358eb8493c1d5285cd
s07_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse HEAD)" = "$s07_base"
s07_r1='S07GovernedStartAgentFixtureTest#approval_reference_selection_requires_current_business_version_before_receipt_lookup'
s07_r2='S07ApprovalFixtureTest#approval_source_publication_must_report_the_exact_model_digest_and_inputs'
s07_core='S07GovernedStartAgentFixtureTest,S07ApprovalFixtureTest,S07GovernedStartFixtureTest,TianjiFixedToolApprovalVerifierTest,TianjiFixedToolApprovalBindingsTest,TianjiGovernedProcessToolConfigurationTest,ApprovalVerificationServiceTest,HumanTaskAccessRulesTest,ExpenseAcceptanceHarnessTest'
# Apply each typed negative for its exact selector, then restore exact B.
"$s07_runner" "$s07_r1"
"$s07_runner" "$s07_r2"
"$s07_runner" "$s07_core"
STELLAR_VERIFY_GOAL=verify "$s07_runner" "$s07_core" clean -Ppermission-producer -Dpermission.commit="$s07_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s07-approved-start-e
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-13-s07-approved-start-e.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-14-s07-approved-start-e.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | Remove only `if (!current.businessVersion().equals(request.action().businessVersion())) throw invalid();`. One missing IllegalStateException. |
| R2 | Remove only native approval release model equality. One missing IllegalStateException on valid-but-altered digest. |
| G1 | Eight AS fixture, two new PS approval fixture, two existing PS IDW fixture, 17 approval verifier, 13 approval bindings, 42 Tool configuration, five PS approval verification, five human task access rules, 11 harness. Exactly 105 with zero failures, errors or skips. |
| P1 | Same exact finite gate in clean permission-producer. Verify compiled operator selector, class-only test-tree fixture and both archives excluding test classes. Semantic PaC and production configuration match. No operator XML. |
| S1 | Strict linked Spec/Plan, exact 12 paths, and all 28 existing SQL bytes. |
| E1 | Actual isolated operator test, one V13 waiting action, genuine V7 approval and one target SERVICE START per tenant. Source gates never execute E1. |

### Separate operator-only E1

Use a private `S07_APPROVED_START` manifest with the original S07 artifact locks, two exact tenant issuers and three isolated pre-migrated stores. `token.agent-owner` and `token.owner` must authenticate the same exact original USER for Agent Run and native approval source START. `token.owner` uses existing PS release publish/read and START. `token.reviewer-1` must authenticate the distinct first reviewer with the tenant's first manifest role, existing `tianji:task:read`, `tianji:task:claim`, `tianji:task:complete` scopes and actual `TIANJI_TASK_READ`, `TIANJI_TASK_CLAIM`, `TIANJI_TASK_COMPLETE` resource authority. The requester denial is checked before reviewer claim.

Use existing S07 keys and additionally private `s07.approval-service-subject`, `s07.approval-service-app`, `s07.approval-service-biz`, `s07.approval-service-source-app`, `s07.approval-service-environment` and `s07.approval-registration`. The approval verifier has a distinct SERVICE subject and registration from START. It needs the existing `tianji:task:approval:verify` scope and its exact existing PS permission `TIANJI_TASK_APPROVAL_VERIFY` and protected resource `tianji.api.process.task.approval.verify`. START keeps existing release READ and START, and the required companion decision uses its existing scopes. Use current Tool policy resources matching the pinned approved fixture purpose/binding and actual definition. The test reads the existing V7 table only through the already restricted Process observer. Exact separate Process/source guards and production IAM still determine proof.

```sh
: "${TIANJI_S07_APPROVED_PRIVATE_MANIFEST:?Provide separately authorized isolated approved S07 manifest}"
"$s07_runner" 'S07ApprovedStartAcceptanceIT#requires_real_requester_source_and_reviewer_approval_before_one_governed_start' "-Dtianji.s07.approved-start.manifest=$TIANJI_S07_APPROVED_PRIVATE_MANIFEST"
```

Require the actual selected method's non-skipped passing XML and full native V7/AS V13/target Process receipt and task witnesses. No V14 approved cached result is part of this step.

## 9. Migration, Compatibility, Rollout, and Rollback

Existing schema and runtime remain owned by the product. The test-only extension can be removed by reverting its focused commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Boundary | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Existing harness and explicit approved mode | G1/P1/E1 |
| REQ-002 | Step 1 | Native pinned source and reviewer provenance | R2/G1/E1 |
| REQ-003 | Step 1 | Exact current business version and independent verifier | R1/G1/E1 |
| REQ-004 | Step 1 | Genuine approved Tool effect and native receipt | P1/E1 |
| REQ-005 | Step 1 | Exact scoped source and separate runtime proof | R1/R2/G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

Actual current IAM, isolated PostgreSQL and verified native effect remain separate operator conditions. Approved cached result access and the broader live denial matrix remain outside this step.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Reuse existing AS and PS test child ownership. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse actual IAM, existing SDK, V7 provenance and V13 recorder. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use existing JDK, Spring and test dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Exact named S07 approved fixture and owner records. | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Validate current version, source digest, native reviewer and actual effect. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Use immutable existing wire contracts and small fixture records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing SDK performs wire conversion. | None |
| MC-LOG-001 | Applicable | PASS | §15 | Keep private settings and credentials out of proof. | None |
| MC-BEAN-001 | Applicable | PASS | §5/7 | Qualified test-only receipt selector under the dedicated mode. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse existing SDK, hashing and fixtures. | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Keep closed Fake protocol, existing canonical codec and typed contracts. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Bound fixture waits and preserve existing approval time ownership. | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | Separate private approved test inputs and packaged configuration. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse existing Test Fixture and SDK Adapter patterns. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Nine Java test paths and three documents plus 28 migration byte audit. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite, package and independently pending operator evidence. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Live current IAM and PostgreSQL remain independent operator conditions. | None |

### 12.6 Final verdict

PASS — Ready for user review
