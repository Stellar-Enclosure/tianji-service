# S07 Agent 到 Process 受治理 START 组合验收实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-05-52-s07-governed-start-e.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:52 Asia/Shanghai |
| Updated | 2026-11-06 05:52 Asia/Shanghai |
| Owner | Tianji Agent Server and Process Server test fixtures |
| Repository | tianji-service |
| Scope | Ten Java test paths and three review artifacts |
| Source Requirement | Master §17.2 S07 and §19 T3-07/T4-05 |
| Baseline Revision | cc2c6818a7e122e81ea5d2a59172894b2c29656c |
| Implements Spec | [S07 operator Spec](../spec/2026-11-06-05-51-s07-governed-start-e.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:51 Asia/Shanghai SHA-256 2eddd67f30d72fbf3eab4a2f7f4e18ecdddf6d698670026f2ea2e9570b55b31b |
| Effective Specs | [S07 operator Spec](../spec/2026-11-06-05-51-s07-governed-start-e.md) |
| Depends On Plans | [S07 Process authorization](2026-11-06-04-33-s07-process-start-authorization.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Governed Process tool assembly](2026-11-01-10-15-governed-process-tool-assembly.md) |

## 1. Summary

增加独立 S07 操作者测试，从现有 USER Agent Run 进入真实受治理 Tool，使用独立 SERVICE 发起精确 Process START，并核对一次写入、结果复用和外租户拒绝。源代码门禁只执行有限用例。

## 2. Target Spec and Effective Design

[S07 operator Spec](../spec/2026-11-06-05-51-s07-governed-start-e.md) §§4/7/14 defines existing protected runtime and isolated test prerequisites. The source Step covers the idempotent-write lane.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/16 | Exact S07 private manifest, child branch and previous suite compatibility. |
| REQ-002 | §7/10/11 | Exact published release, canonical Agent definition and verified current business version. |
| REQ-003 | §7/11/14 | Actual operator source asserts protected USER/SERVICE START and one exact Process receipt plus native task. |
| REQ-004 | §7/11/14 | Operator source asserts result reuse and foreign-object rejection without extra side effects. |
| REQ-005 | §14/16/18 | Typed finite tests and package/class proof remain distinct from actual operator E. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Pin the Service baseline and effective Spec. Add finite guards and compose the exact isolated S07 fixture through the existing Driver. Apply typed negatives individually, restore the candidate, execute the finite suite and compare actual packaged artifacts.

### 4.2 Test-first strategy

R1 removes only the exact original owner comparison in the S07 resolver, allowing a same-name SERVICE or wrong issuer with an otherwise valid object. R2 removes only exact model-set equality from the verified S07 release, allowing a validly formed but altered model digest. Require one missing IllegalStateException for each typed negative. In R1 remove only `profile.owner().equals(request.run().actor())` and its conjunction from the profile filter. In R2 remove only ` || !Set.copyOf(release.models()).equals(expected)`.

### 4.3 Sequential and parallel boundaries

AS and PS candidate test sources may be drafted in parallel under one locked interface. Apply their combined candidate before running the ordered exact gates. Do not select the operator fixture for source verification.

### 4.4 Commit boundaries

One focused source commit after restoring all guards, executing source/package gates and verifying exact staged paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Plan decision |
| --- | --- | --- | --- |
| Separate S07 suite | Required, §7 | Existing Driver owns artifact locks, scopes and children | Reuse while isolating the reverse case. |
| Fixture owner and version | Required, §7/10 | Existing business observer and canonical ledger codec | Bind current isolated row and exact Agent profile. |
| Existing governed Tool and Process service | Required, §7/11/14 | Existing actual IAM and durable receipt path | Use real runtime as independent operator test dependency. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| AS scoped owner, Fake and profile | REQ-002/003 | R1/G1/P1 | Existing canonical codec and governed Tool configuration |
| PS isolated release, driver and effect witnesses | REQ-001/002/003/004 | R2/G1/P1/E1 | Existing acceptance harness and PS contracts |
| Documentation and source audit | REQ-005 | S1 | Frozen exact paths, finite and package results |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Separate AS and PS operator children and existing production authority | Select test-only fixture through explicit suite; reuse ordinary runtime. |
| Persistence | Existing disposable business table, V13/V14 and native receipt | Read exact witnesses using observer roles and existing migrations. |
| Naming and model | Existing typed contracts and test helpers | Keep small scoped test-only helpers and immutable inputs. |
| Verification | Existing Maven reactor and artifact-pinned harness | Typed negatives, finite suite, actual package and independent E. |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Verification |
| --- | --- | --- | --- | --- | --- |
| Isolated orchestration | ExpenseAcceptanceHarness | EVD-001 exact manifest and child controls | New direction needs private mode | Add one isolated selector | TEST-002/003 |
| Scope and Tool definition | AgentJsonCodec, current governed bridge | EVD-002/003 fixed owner and definitions | Test-only current version resolver needed | Reuse codec and existing production authority | TEST-001/003 |
| Native effect | Existing Process START and V13/V14 | EVD-003/004/005 | Exact cross-service evidence needed | Verify existing durable owner, action and task rows | TEST-003 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Scope the exact S07 fixture and finite test types in existing test packages. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Validate owner, immutable release, Tool descriptor and business version at their owning fixture boundaries. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing immutable Agent, Tool and Process contracts plus small fixture records. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Register explicit named test-only model and START resolver within the selected test child. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use JDK, current JDBC, JUnit and existing SDK with no dependency additions. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing Jackson and canonical Agent definition codec for bounded test evidence. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep production properties stable and add only explicit private test mode inputs. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Reuse Test Fixture and SDK Adapter to exercise the existing runtime path. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use bounded waits and durations with existing source and engine time semantics. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep the AS/PS existing production responsibility and existing persistence and events. | R1/R2/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Child readiness, Run poll, observer and Tool fixture windows | Duration | java.time.Duration and existing bounded waits | Not persisted | Internal | Not applicable: elapsed test deadlines | Not applicable: no display | Existing child and network limits bound the operator run | Existing monotonic wait and bounded query clocks | Preserve prior suite timeouts and existing engine timestamps | Step 1 TEST-001/002/003 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixtureTest.java
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/S07FixtureDefinitionHash.java
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixture.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartAcceptanceIT.java
CREATE docs/egon/spec/2026-11-06-05-51-s07-governed-start-e.md
CREATE docs/egon/plan/2026-11-06-05-52-s07-governed-start-e.md
CREATE docs/egon/evidence/2026-11-06-s07-governed-start-e.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Use JDK 25 and the existing finite reactor. The actual operator test separately requires locked AS/PS archives and test trees, exact current identities and permissions, local HTTPS, three isolated pre-migrated PostgreSQL stores and a private root. Existing production and migration files are outside scope.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the isolated S07 governed START operator fixture

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing governed Tool bridge, ordinary current USER execution, fixed Process START profile, V13/V14, owner-scoped Process receipt and exact private harness.
- Baseline state: Existing Business/Recovery/S12 and Process authorization source coverage do not exercise this actual reverse direction through the governed Tool.
- Observable outcome: Exact dedicated operator source asserts one actual protected START, stable result reuse and foreign-object rejection under isolated prerequisites.
- End state: Ten Java test paths and three review artifacts are committed as a single scoped Step.
- Test-first gate: Required. R1 and R2 each demonstrate the matching finite guard rejects the exact typed negative.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`

- Purpose: Finite original owner and version-resolution guard plus exact bounded Fake script.
- Symbols: S07GovernedStartAgentFixtureTest.
- Repository evidence: EVD-002/004.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Finite original owner and version-resolution guard plus exact bounded Fake script.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Construct typed pinned definition and real-looking verified identities; use only in-memory JDBC and model fixture.
- Error and edge behavior: Reject same-name SERVICE, different issuer and missing or ambiguous scoped version.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertExactOwnedRequestQueriesOneBusinessVersion();
assertSameNamedServiceAndOtherIssuerNeverReachBusinessJdbc();
assertCanonicalFakeScriptProposesOnlyThePinnedStartTool();
assertMalformedToolResultDoesNotProduceTerminalInstanceMarker();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixtureTest.java`

- Purpose: Finite fixed release acceptance and mutation refusal.
- Symbols: S07GovernedStartFixtureTest.
- Repository evidence: EVD-004/005.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Finite fixed release acceptance and mutation refusal.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Use exact typed BPMN and DMN digests, release and input contracts.
- Error and edge behavior: A changed valid digest must fail exact published release verification.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
constructExactPublishedBpmnAndCompanionDmnWitness();
assertPinnedContentAndInputSchemaMatches();
assertChangedDigestIsRejectedBeforeS07ChildLaunch();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/S07FixtureDefinitionHash.java`

- Purpose: Use the existing canonical definition hash in a test-only same-package bridge.
- Symbols: S07FixtureDefinitionHash.
- Repository evidence: EVD-002.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Use the existing canonical definition hash in a test-only same-package bridge.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Canonical digest joins exact pinned Agent definition and Tool authority slots.
- Error and edge behavior: Actual ledger publication must return the identical digest.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
constructTheExistingAgentJsonCodec();
calculateTheExistingCanonicalDefinitionHashForFixedS07Definition();
keepTheBridgeInTheAgentServerTestTreeAndCheckPublishedDigestIndependently();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`

- Purpose: Assemble test-only fixed Agent profile, owned business version resolver and bounded Fake model.
- Symbols: S07GovernedStartAgentFixtureConfiguration.
- Repository evidence: EVD-002/003/004.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Assemble test-only fixed Agent profile, owned business version resolver and bounded Fake model.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Generate only fixed test Tool properties from published release and closed scoped inputs.
- Error and edge behavior: Reject wrong owner/definition/tool/business key, bad database row and malformed script or result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
prepareExactTenantSpecificStartAndDecisionBackendBindings();
verifyOriginalOwnerAndReadOneVersionFromScopedReadOnlyBusinessObject();
publishCanonicalAgentDefinitionAndRequireActualLedgerDigest();
emitOneFixedToolRequestAndOneValidatedTerminalFixtureResponse();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: Admit the S07 child only under its exact owned opt-in.
- Symbols: ExpenseAgentAcceptanceMain.
- Repository evidence: EVD-001/002.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Admit the S07 child only under its exact owned opt-in.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Select test-only S07 configuration and property source with existing ordinary AS application.
- Error and edge behavior: Reject mixed S07/S12 configuration and preserve established child startup.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
readExistingPrivateChildConfigurationAndAssertExactS07Mode();
prepareS07FixturePlanAndAddOnlyItsPinnedTestPropertySource();
bootstrapOrdinaryAgentServerWithSelectedTestConfiguration();
retainPreviousExpenseAndS12ChildBranchesWhenS07IsNotSelected();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixture.java`

- Purpose: Pin the S07 BPMN, held review task and required companion DMN model.
- Symbols: S07GovernedStartFixture.
- Repository evidence: EVD-004/005.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Pin the S07 BPMN, held review task and required companion DMN model.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Publish immutable fixed models, compare complete returned release and exact input contract.
- Error and edge behavior: Changed model, input schema or release fails before Agent launch.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
createPinnedS07BpmnWithOneHeldReviewAndIntegerAmount();
createPinnedCompanionDmnForRequiredGovernedProfile();
verifyExactPublishedReleaseAgainstBothCanonicalModelDigestsAndSchemas();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: Register exact S07 operator mode and child mode property in the owned harness.
- Symbols: ExpenseAcceptanceHarness.
- Repository evidence: EVD-001.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Register exact S07 operator mode and child mode property in the owned harness.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Reuse the existing private manifest, artifact identity, credentials and bounded lifecycle.
- Error and edge behavior: Separate S07 from generic expense evidence and preserve earlier suite selectors.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
addExactS07GovernedStartSuiteEnum();
writeOnlyOwnedS07ChildOptInWhenManifestMatchesExactMode();
rejectTreatingS07AsGenericExpenseEvidenceAndPreservePriorSuiteBranches();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`

- Purpose: Start actual Process child for the S07 fixed-release lane.
- Symbols: ExpenseProcessAcceptanceMain.
- Repository evidence: EVD-001/005.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Start actual Process child for the S07 fixed-release lane.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Check exact owned configuration and use existing protected Process runtime.
- Error and edge behavior: Refuse incompatible fixture flags and retain existing non-S07 child setup.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactOwnedFixturePropertyAndSeparateS07ChildGuards();
validateS07FlowableAndExistingRequiredProcessEndpoints();
launchOrdinaryProcessServerUsingTestChildRulesForOnlyTheSelectedMode();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: Drive the real isolated reverse path and exact Agent/Process witnesses.
- Symbols: BusinessProcessAgentAcceptanceIT.Driver.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Drive the real isolated reverse path and exact Agent/Process witnesses.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Boot Process first, publish verified release, seed two owned objects and start AS with exact fixture values.
- Error and edge behavior: Refuse extra invocation or START, wrong owner/hash, invalid replay and foreign object effects.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
selectOnlyPrivateGovernedStartManifestAndBootProcessFirst();
validateFixedPublishedReleaseAndSeedDisposableOwnerVersionRows();
startAgentWithActualPublishedManifestHashesAndScopedFixtureInputs();
verifyOneExactNativeStartAndCurrentAuthorizedReplayThenRejectForeignObject();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartAcceptanceIT.java`

- Purpose: Expose one independent operator-only scenario.
- Symbols: S07GovernedStartAcceptanceIT.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Expose one independent operator-only scenario.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Open the exact S07 mode in the existing driver, exercise both tenants and close owned children.
- Error and edge behavior: Missing manifest remains an unexecuted E gate; any scope or effect mismatch fails.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
openOnlyTheOwnedS07GovernedStartDriverWithItsPrivateManifest();
verifyBothTenantsUsingActualGovernedToolPathAndExistingReadOnlyWitnesses();
closeTheOwnedDriverAndItsIsolatedChildrenThroughExistingLifecycle();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 11 — `CREATE docs/egon/spec/2026-11-06-05-51-s07-governed-start-e.md`

- Purpose: Record exact S07 fixture boundaries, owners and finite/operator proof.
- Symbols: 2026-11-06-05-51-s07-governed-start-e.md.
- Repository evidence: Effective Spec §14.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Record exact S07 fixture boundaries, owners and finite/operator proof.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Retain exact source scope and current evidence.
- Error and edge behavior: Prevent claims beyond actual executed verification.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockExactBaselineAndOwnedS07Fixture();
recordIndependentCurrentIamAndReadOnlyWitnesses();
separateActualOperatorAcceptanceFromFiniteSourceEvidence();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 12 — `CREATE docs/egon/plan/2026-11-06-05-52-s07-governed-start-e.md`

- Purpose: Lock exact paths, source mutations and owning package gate.
- Symbols: 2026-11-06-05-52-s07-governed-start-e.md.
- Repository evidence: Effective Spec §14.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Lock exact paths, source mutations and owning package gate.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Retain exact source scope and current evidence.
- Error and edge behavior: Prevent claims beyond actual executed verification.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
listOnlyTenJavaFilesAndThreeReviewArtifacts();
executeExactTypedMutationSelectorsAndRestoreCandidate();
verifyCleanPackageAndInspectNoOperatorXmlBeforeCommit();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.

#### File 13 — `CREATE docs/egon/evidence/2026-11-06-s07-governed-start-e.md`

- Purpose: Record the actual verification and unresolved operator gate.
- Symbols: 2026-11-06-s07-governed-start-e.md.
- Repository evidence: Effective Spec §14.
- Dependencies and consumers: Existing test-mode AS/PS path in §4.6.
- Why now: Record the actual verification and unresolved operator gate.
- Contract/signature changes: Test-only source or review artifact.
- Input/output and state mapping: Retain exact source scope and current evidence.
- Error and edge behavior: Prevent claims beyond actual executed verification.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordOnlyObservedRedFailuresAndGreenCounts();
recordActualPackageAndMigrationParityEvidence();
identifyExactOperatorSelectorAndIndependentStillPendingRuntimeProof();
```

- Verification contribution: R1/R2/G1/P1/S1/E1.
- After this file: Verify the declared test-source boundary.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact source and separate operator gates in §8.
- Expected result: One missing guard assertion from each typed negative, then clean finite and package proof with no actual operator report.
- Failure returns to: First relevant guard or artifact check. Restore all original source guards before further validation.
- Completion criteria: Exact source and review scope, finite gate, artifact boundary and pending operator evidence are independently recorded.
- Rollback: Revert the single focused test-source commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixtureTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/S07FixtureDefinitionHash.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartFixture.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S07GovernedStartAcceptanceIT.java`, `docs/egon/spec/2026-11-06-05-51-s07-governed-start-e.md`, `docs/egon/plan/2026-11-06-05-52-s07-governed-start-e.md`, `docs/egon/evidence/2026-11-06-s07-governed-start-e.md`.
- Commit: test(acceptance): cover isolated governed Agent to Process START.

## 8. Test, Validation, and Quality Gates

```sh
s07_base=cc2c6818a7e122e81ea5d2a59172894b2c29656c
s07_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse HEAD)" = "$s07_base"
s07_r1='S07GovernedStartAgentFixtureTest#resolver_refuses_same_named_service_and_other_issuer_with_valid_business_row'
s07_r2='S07GovernedStartFixtureTest#process_publication_must_report_the_exact_model_digest_and_inputs'
s07_core='S07GovernedStartAgentFixtureTest,S07GovernedStartFixtureTest,ExpenseAcceptanceHarnessTest,TianjiGovernedProcessToolConfigurationTest,TianjiFixedBpmnStartToolTransportTest'
# Run each selector with its typed mutation, then restore exact B.
"$s07_runner" "$s07_r1"
"$s07_runner" "$s07_r2"
"$s07_runner" "$s07_core"
STELLAR_VERIFY_GOAL=verify "$s07_runner" "$s07_core" clean -Ppermission-producer -Dpermission.commit="$s07_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s07-governed-start-e
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-51-s07-governed-start-e.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-52-s07-governed-start-e.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | Remove only the exact owner comparison. One missing IllegalStateException on an otherwise valid wrong owner. |
| R2 | Remove only exact model set equality. One missing IllegalStateException on changed model digest. |
| G1 | 4 AS and 2 PS new finite cases, 11 ExpenseAcceptanceHarnessTest, 42 TianjiGovernedProcessToolConfigurationTest and 32 TianjiFixedBpmnStartToolTransportTest. Exactly 91 with zero failures, errors or skips. |
| P1 | Same exact finite suite in clean permission-producer. Confirm actual compiled classes and exact operator method. Class-only probe loads AS fixture configuration and the same-package digest bridge from the matched test tree under the actual AS archive without initialization. Both executable archives omit S07 fixtures and preserve configuration/semantic PaC. No operator E XML. |
| S1 | Strict linked Spec/Plan, exact 13 paths and all 28 existing SQL bytes. |
| E1 | Actual isolated operator test, exact scoped receipt, native task, V13/V14 witnesses and foreign-object rejection. Source gates do not execute E1. |

### Separate operator-only E1

Require a private manifest with suite `S07_GOVERNED_START` and existing artifact and test-tree locks. Supply two tenant issuers, current protected AS USER callers and actual existing Tool policy decisions, distinct verified SERVICE roles for the fixed Process target, isolated PostgreSQL stores and bounded local HTTPS endpoints. No credential values are part of source or proof.

Each tenant's private fixture settings include `s07.service-subject`, `s07.service-app`, `s07.service-biz`, `s07.service-source-app`, `s07.service-environment`, `s07.start-registration`, `s07.decision-registration`, `s07.start-authority-resource` and `s07.decision-authority-resource`. Existing IAM owner/resource checks remain the independent authority for the test. The fixed test purposes are `TIANJI_TOOL_S07_START` and `TIANJI_TOOL_S07_DECISION`. Existing private `token.owner` supplies the authorized Process release publisher and reader. The current ordinary Agent submission and read credential `token.agent-owner` must be a verified USER whose exact subject equals the manifest tenant owner and whose issuer matches that tenant. The fixed original outbound SERVICE `s07.service-subject` is separate from the USER. Its START registration uses `tianji:process:release:read` and `tianji:process:start`; the companion Decision registration uses release read and `tianji:decision:execute` under the same fixed expected SERVICE subject. No Process receipt READ is needed because witnesses use the separate isolated observer. The existing preflight requires the pinned V14/V10/V2 store histories and Outbox histories. AS test-only business context additionally reads the exact seeded object under the isolated observer role. The companion DMN profile is assembled through the existing required governed profiles and remains outside the Fake script action.

```sh
: "${TIANJI_S07_PRIVATE_MANIFEST:?Provide the separately authorized isolated S07 operator manifest}"
"$s07_runner" 'S07GovernedStartAcceptanceIT#starts_once_through_actual_agent_tool_and_preserves_service_owner' "-Dtianji.s07.governed-start.manifest=$TIANJI_S07_PRIVATE_MANIFEST"
```

Require the actual selected method's non-skipped passing XML. Verify one protected START per tenant, one exact original SERVICE-owned APPLIED receipt and held review task, V13 first dispatch and V14 result access under current authority, and foreign-object failure without extra Tool invocation. This case is limited to the idempotent-write lane.

## 9. Migration, Compatibility, Rollout, and Rollback

No production behavior or Flyway schema is added by this test scope. The existing private operator harness owns the isolated fixture lifecycle. Revert the single test source change for rollback.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Boundary | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Exact harness, children and suite | G1/P1/E1 |
| REQ-002 | Step 1 | Resolver, canonical Agent digest and fixed PS release | R1/R2/G1/E1 |
| REQ-003 | Step 1 | Actual governed Tool and native receipt witnesses | P1/E1 |
| REQ-004 | Step 1 | Exact replay and foreign object SQL witnesses | P1/E1 |
| REQ-005 | Step 1 | All declared paths and distinct verification | R1/R2/G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

Actual current IAM, isolated PostgreSQL, bound roles and native effect acceptance remain separate operator conditions. The new source covers the idempotent-write lane. Approval and broader live policy denial need additional authorized coverage.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Reuse existing AS/PS fixture and owner boundaries. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse existing IAM, canonical codec, SDK and disposable DB witnesses. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use JDK, Spring, JUnit and existing test dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Names scope the exact S07 fixture and test child. | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Finite guards verify fixed release and original owner, E requires separate actual proof. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Use immutable existing wire and fixture records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing codec and SDK cover conversion. | None |
| MC-LOG-001 | Applicable | PASS | §15 | Keep exact private fixture settings and credentials out of proof. | None |
| MC-BEAN-001 | Applicable | PASS | §5/7 | Explicit test-only child registers the scoped resolver and Fake model. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse existing SDK, hashing and fixtures. | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Closed fixture inputs and bounded Fake script use existing JSON tools. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing bounded lifecycle and fixed fixture durations. | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | Exact private test properties and unchanged packaged configuration. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse existing Test Fixture and SDK Adapter patterns. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Ten Java test paths, three documents and all 28 migration bytes audited. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Typed negatives, 91 finite tests, real artifact and separate operator verification. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Live current IAM, PostgreSQL and S07 approval scope remain separate. | None |

### 12.6 Final verdict

PASS — Ready for user review
