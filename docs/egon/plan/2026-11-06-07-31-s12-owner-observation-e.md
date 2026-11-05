# T4-02 S12 原始所有者保护读取验收实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-07-31-s12-owner-observation-e.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:31 Asia/Shanghai |
| Updated | 2026-11-06 07:31 Asia/Shanghai |
| Owner | Tianji acceptance fixtures |
| Repository | tianji-service |
| Scope | Six Java test paths and three review documents |
| Source Requirement | Master §17.2 S12 and §19 T4-02/T4-05 |
| Baseline Revision | Service 9013b635dcc950968a8c100b10d6725bc5543924 |
| Implements Spec | [S12 owner observation](../spec/2026-11-06-07-30-s12-owner-observation-e.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:30 Asia/Shanghai SHA-256 b684f5ca037b75cee3fe6f4488b5811c5e6d6d5e3f5477be45c0306c58c24068 |
| Effective Specs | [S12 owner observation](../spec/2026-11-06-07-30-s12-owner-observation-e.md) |
| Depends On Plans | [Java owner SDK](2026-11-06-07-05-java-owner-correlation-sdk.md), [S12 committed metrics](2026-11-06-04-39-s12-metrics-subset.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

添加一个独立操作者入口，复用两条正常租户路径和指标 Collector，使用原始 PS USER 和 AS bridge SERVICE 证明三项实际保护读取。

## 2. Target Spec and Effective Design

[S12 owner observation Spec](../spec/2026-11-06-07-30-s12-owner-observation-e.md) 的 §§4/5/7/14 约定私有模式、精确凭据、三项 owner 证据和独立 E1。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7 | 原 owner 及精确 READ、独立 suite | Driver and harness |
| REQ-002 | §7/9/14 | 两个 PS 正向 START、outcome 与两个 404 | SDK and fixture |
| REQ-003 | §7/9/14 | 两个 AS 正向 LIST 对应持久 bridge Run | SDK and fixture |
| REQ-004 | §8/14/16 | R1/G1/P1/S1 与单独操作者 E1 | Validation |

## 4. Implementation Strategy and Dependency Order

冻结 Service 基线，先固定原 owner 证据及 typed RED，再组装专用模式并验证现有有限用例。包与源码验证仅编译操作者入口。E1 独立执行。

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Driver, metrics suite and current HTTP clients | §7/8 | Reuse isolated fixture and published contracts | S1 |
| Validation | Private manifest and exact original owner observers | §5/7/14 | Bind credentials, exact pages and independent durable run | R1/G1/E1 |
| Lifecycle and time | Existing child deadlines, JDBC and Collector reader | §6.3/7 | Reuse owner primaries and exact bounded windows | G1/E1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Owner HTTP | Existing HttpProcessClient and HttpAgentClient | Typed correlation and instance outcome | Fits | Call current SDK with dedicated owner scope | Existing modules | Step 1 MC-REUSE-001 |
| Child fixture | Existing metrics Driver and Harness | Actual workflow, artifact locks and Collector | Fits | Add independent suite and PS test-only opt-in | Test switch and evidence | Step 1 MC-ARCH-001 |
| Durable AS witness | Existing Driver owner query | Complete issuer, subject and native source predicates | Fits | Project stored createdAt for equality | Test record | Step 1 MC-VALID-001 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–9 | 沿用 PS 测试包，名称标明 S12 owner observation。 | R1/G1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–9 | 在 fixture 边界验证凭据和类型化证据。 | R1/G1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–9 | 沿用公开 immutable DTO 和一个内部持久观察记录。 | R1/G1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–9 | 沿用现有测试子进程和 Registry。 | R1/G1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–9 | 复用 Java SDK、JDBC、Jackson 和 JUnit。 | R1/G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–9 | 使用严格 Jackson 预检 OAuth payload，不保留解析异常。 | R1/G1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–9 | 新私有 suite 只向隔离 PS 注入现有 outcome 开关。 | R1/G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–9 | 沿用 Test Fixture 和 HTTP Adapter。 | R1/G1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–9 | 按各自主库时钟使用微秒 UTC 半开窗口。 | R1/G1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–9 | 保持测试和生产边界与已有 AS/PS 存储归属。 | R1/G1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive, receipt submittedAt and Agent createdAt | Occurred instant | java.time.Instant and OffsetDateTime | UTC instant | UTC Z | Absolute instant | No display | Each owning store gives a single exclusive upper bound and one-hour inclusive lower bound for both tenants | Existing primary database microseconds | Existing correlation queries and typed times | Step 1 TEST-001/004 |
| TIME-002 | Request timeout and Collector deadline | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time | No display | Five seconds for each HTTP request and existing Collector poll | Existing monotonic deadline | Existing fixture timeout | Step 1 TEST-002/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidenceTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidence.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationAcceptanceIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-30-s12-owner-observation-e.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-31-s12-owner-observation-e.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s12-owner-observation-e.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

使用 JDK 25 和已有 permission-producer。独立真实操作者准备两租户、隔离 HTTPS、三个隔离库、两个原 owner READ token 每租户和固定 Collector。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare isolated S12 protected owner observation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing isolated expense fixture, S12 metrics and Java owner SDK.
- Baseline state: Existing typed SDK and independently scoped durable witnesses.
- Observable outcome: One precise assertion RED, twenty-three finite passes, six operator success reads and two swapped denials after independently passing E1.
- End state: Six test paths and three review documents in one focused commit.
- Test-first gate: Required. Remove only `linkedRunId.equals(agent.runId()) && ` from the AS singleton witness equality and select `S12OwnerObservationEvidenceTest#rejects_foreign_owner_ids_even_when_both_tenants_share_correlation`. Require one missing AssertionError for the foreign AS identity substitution, zero skips or compile errors.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidenceTest.java`

- Purpose: 证实精确对应、租户身份绑定及跨租户反证。
- Symbols: `S12OwnerObservationEvidenceTest`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 证实精确对应、租户身份绑定及跨租户反证。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertExactOwnerPagesPassBothTenantIndependentWitnesses();
assertForeignAgentIdWithSameCorrelationFailsTheRunIdentityCheck();
assertIncorrectAudiencePrincipalTypeScopeAndTenantAreRejectedWithoutPayload();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidence.java`

- Purpose: 验证 SDK 响应与本地持久事实，并在私有配置入口核对专用凭据。
- Symbols: `S12OwnerObservationEvidence`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 验证 SDK 响应与本地持久事实，并在私有配置入口核对专用凭据。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireReadTokenForOneAudienceOriginalSubjectAndExactTenantScope();
requireSingleStartWithExactInstanceReleaseAndSubmissionTime();
requireClosedAppliedCompletedAndExactLinkedServiceAgentRun();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: 增加独立 suite 并复用原有指标子进程、Collector 与有界资源锁。
- Symbols: `ExpenseAcceptanceHarness`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 增加独立 suite 并复用原有指标子进程、Collector 与有界资源锁。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
acceptExactOwnerObservationManifestThroughTheExistingPrivateHarness();
selectExistingMetricsChildConfigurationAndBoundedPrivateFileRules();
rejectPublishingObservationAsTheGenericExpenseEvidenceFormat();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: 绑定两个原 owner，调用现有 SDK 并核对独立数据库观察。
- Symbols: `BusinessProcessAgentAcceptanceIT`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 绑定两个原 owner，调用现有 SDK 并核对独立数据库观察。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requirePrivateReadCredentialsBeforeOpeningStoresOrLaunchingChildren();
reconcileEachNormalRunUnderItsOwnDeadlineThenReadBothOwningPrimaryClocks();
compareAllThreeTypedSdkPagesWithExactOwnerWitnessesAndDenySwappedOutcome();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java`

- Purpose: 原有选择器共享双租户流程，只有新模式运行保护读取。
- Symbols: `S12MetricsAcceptanceIT`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 原有选择器共享双租户流程，只有新模式运行保护读取。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
retainExistingStandaloneMetricsAndPrivacySelectors();
invokeOwnerObservationAfterBothIdenticalCorrelationPathsHaveCompleted();
requireExistingRawCollectorVerificationBeforePrintingSanitizedSuccess();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationAcceptanceIT.java`

- Purpose: 提供一项独立操作者入口。
- Symbols: `S12OwnerObservationAcceptanceIT`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 提供一项独立操作者入口。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactIndependentOwnerObservationOptInThroughDriver();
runBothActualProtectedOwnerScopesWithExistingIsolatedMetricsFlow();
waitForBothCollectorWitnessesBeforeCompletingTheExactOperatorMethod();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 7 — `CREATE docs/egon/spec/2026-11-06-07-30-s12-owner-observation-e.md`

- Purpose: 固定本次验收契约、权限和证据边界。
- Symbols: `2026-11-06-07-30-s12-owner-observation-e`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 固定本次验收契约、权限和证据边界。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordExistingShoubingPrincipalAndResourceBindings();
declareEveryProtectedReadAndIndependentDurableWitness();
traceFixtureRequirementsToFiniteAndSeparateOperatorEvidence();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 8 — `CREATE docs/egon/plan/2026-11-06-07-31-s12-owner-observation-e.md`

- Purpose: 固定路径、反证、包检查和操作者命令。
- Symbols: `2026-11-06-07-31-s12-owner-observation-e`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 固定路径、反证、包检查和操作者命令。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
freezeTheSixJavaSourcesAndThreeReviewDocuments();
lockExactSelectorsAndAssertionsForTheOwnedFiniteGates();
documentFreshAuthorizedE1EnvironmentAndPrivateEvidenceRetention();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

#### File 9 — `CREATE docs/egon/evidence/2026-11-06-s12-owner-observation-e.md`

- Purpose: 如实记录源码结果与实际 E 前置门槛。
- Symbols: `2026-11-06-s12-owner-observation-e`。
- Repository evidence: Spec §2 EVD-001–005 和 §7。
- Dependencies and consumers: 按本 Step 的精确文件顺序，由 TEST-001–004 核对。
- Why now: 关闭当前选定的 S12 owner 读取验收范围。
- Contract/signature changes: 测试或审查文档范围。
- Input/output and state mapping: 如实记录源码结果与实际 E 前置门槛。
- Error and edge behavior: 任何未经核对的 owner 或缺失证据均使对应门禁失败。
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordTheExpectedSingleForeignRunMutationFailure();
recordObservedFiniteCountsAndSeparateOwningArtifactVerification();
retainOperatorStatusPendingUntilExactPassingReportAndCollectorDigest();
```

- Verification contribution: 按 §8 核对有限、包和 E 证据。
- After this file: 对照 §5 锁定路径和明确的 owner 边界。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 R1/G1/P1/S1，E1 独立执行。
- Expected result: R1 精确一个反证失败，G1/P1 共 23 项有限用例通过，E 入口编译。
- Failure returns to: 对应证据或 owner 绑定步骤，保持未提交状态。
- Completion criteria: 精确九条路径，已通过有限与包验证，真实 E1 独立留档。
- Rollback: 撤回本步骤的独立测试提交。
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidenceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidence.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationAcceptanceIT.java`, `docs/egon/spec/2026-11-06-07-30-s12-owner-observation-e.md`, `docs/egon/plan/2026-11-06-07-31-s12-owner-observation-e.md`, `docs/egon/evidence/2026-11-06-s12-owner-observation-e.md`。
- Commit: test(process): prepare protected S12 owner observation.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Required evidence |
| --- | --- | --- |
| R1 | `S12OwnerObservationEvidenceTest#rejects_foreign_owner_ids_even_when_both_tenants_share_correlation` under the single source mutation | One AssertionFailedError for the missed foreign AS run, zero skips. |
| G1 | `S12OwnerObservationEvidenceTest,ExpenseAcceptanceHarnessTest,S12MetricsCollectorEvidenceTest,S12MetricsTelemetryTest` | Four finite owner evidence and nineteen adjacent cases. |
| P1 | Clean permission-producer | Same finite selectors, compiled E, only test tree carries fixture, no E report. |
| S1 | Strict Spec/Plan, exact diff, package and migration parity | Nine paths, reviewed baseline and artefact boundaries. |
| E1 | `S12OwnerObservationAcceptanceIT#observes_each_tenants_owned_process_and_bridge_agent_run` | One passed unskipped operator report, fixed counts and private raw Collector digest. |

```sh
owner_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
owner_jdk=$(/usr/libexec/java_home -v 25)
owner_base=9013b635dcc950968a8c100b10d6725bc5543924
owner_finite='S12OwnerObservationEvidenceTest,ExpenseAcceptanceHarnessTest,S12MetricsCollectorEvidenceTest,S12MetricsTelemetryTest'
JAVA_HOME="$owner_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$owner_settings" -s "$owner_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$owner_finite"
JAVA_HOME="$owner_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
  ./scripts/verify-source-reactor.sh -gs "$owner_settings" -s "$owner_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$owner_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-owner-observation-e \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$owner_finite"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-30-s12-owner-observation-e.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-31-s12-owner-observation-e.md
git diff --check
```

R1 仅在临时验证副本按上述 Step 变更指定表达式，使用同一 test 命令将 `owner_finite` 设为 R1 精确方法。恢复候选后再执行 G1。检查实际 PS test-classes 中存在 E，实际 AS/PS 可执行包无新测试类，比较生产配置、权限资源与迁移。

### Separate operator-only E1

沿用 [S12 指标操作者条件](2026-11-06-04-39-s12-metrics-subset.md#separate-operator-only-e1)。为本轮准备新授权的 privateRoot、三个隔离库、两个真实子进程、准确制品和完整测试树锁，以及专用空的 Collector JSONL。manifest `suite` 设为 `S12_OWNER_OBSERVATION`。

每租户使用原启动配置之外单独提供的两个 READ token。PS USER 保持实际 START 所有者，既有授权包含 `tianji.api.process.instance.correlations` 与 `tianji.api.process.agent.outcomes`。AS SERVICE 使用实际链接桥接客户端和既有 `tianji.api.agent.list` 授权。凭据应在已有 IAM 授权下签发，并覆盖保护读取时间窗。按 Spec §5 绑定唯一 audience 和 scope。不要在命令行提供 bearer，测试从已有 0600 私有文件读取。

```sh
: "${TIANJI_S12_OWNER_OBSERVATION_PRIVATE_MANIFEST:?Provide an authorized isolated S12 owner observation manifest}"
owner_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
owner_jdk=$(/usr/libexec/java_home -v 25)
JAVA_HOME="$owner_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$owner_settings" -s "$owner_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  '-Dtest=S12OwnerObservationAcceptanceIT#observes_each_tenants_owned_process_and_bridge_agent_run' \
  "-Dtianji.s12.owner-observation.manifest=$TIANJI_S12_OWNER_OBSERVATION_PRIVATE_MANIFEST"
```

在私有环境保留精确方法的通过 XML、manifest、完整 Collector 锁和原始 JSONL。要求固定标记 `S12_OWNER_OBSERVATION_SUBSET tenants=2 protectedReads=6 foreignOutcomeDenials=2`，停止 Collector 后校验最终原始文件摘要与 `S12_METRICS_SUBSET` 输出相同。

## 9. Migration, Compatibility, Rollout, and Rollback

测试提交可独立撤回，已有测试选择器与生产数据继续使用原先的入口和授权规则。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–6 | G1/P1/E1 |
| REQ-002 | Step 1 | 1–6 | G1/P1/E1 |
| REQ-003 | Step 1 | 1–6 | R1/G1/P1/E1 |
| REQ-004 | Step 1 | 1–9 | R1/G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

实际双租户 IAM、隔离数据库、HTTPS 和 Collector 需独立操作者完成。完整 S12 需要主蓝图剩余证据。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §5/7/14 | 既有 PS 验收驱动、AS/PS 独立子进程和原有授权边界。 | None |
| MC-REUSE-001 | Applicable | PASS | §5/7/14 | 复用 Java SDK、正向流程、原 owner 查询和固定 Collector。 | None |
| MC-DEP-001 | Applicable | PASS | §5/7/14 | 沿用 JDK 25、Jackson、JUnit 与已有 JDBC。 | None |
| MC-NAME-001 | Applicable | PASS | §5/7/14 | 独立 S12 suite、操作者选择器和测试证据验证器。 | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | 完整 owner、精确私有 READ 和单页证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §5/7/14 | 沿用已发布只读 DTO，仅增加测试观察记录。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/7/14 | 已有 SDK 解码发布的合同 DTO。 | None |
| MC-LOG-001 | Applicable | PASS | §5/7/14 | 只在全部证据通过后打印固定计数，凭据失败信息固定。 | None |
| MC-BEAN-001 | Not applicable | N/A | §5/7/14 | 沿用既有子进程和 Registry。 | None |
| MC-UTIL-001 | Applicable | PASS | §5/7/14 | 复用 Java 时间、编码与现有测试基础设施。 | None |
| MC-JSON-001 | Applicable | PASS | §5/7/14 | 重复字段、尾随对象和错误 scope 在转发前拒绝。 | None |
| MC-TIME-001 | Applicable | PASS | §5/7/14 | 按各 owner 主库时间确定同租户对比窗口。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/7/14 | 独立私有 opt-in，仅在该 PS 测试子进程打开 outcome。 | None |
| MC-PATTERN-001 | Applicable | PASS | §5/7/14 | 复用 Test Fixture 和 HTTP Adapter，证据用直接比较。 | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7/14 | 六个测试路径和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §5/7/14 | 精确反证、有限回归与单独操作者门槛。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/7/14 | 真实授权、隔离 PostgreSQL 和 Collector 仍由 E1 验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
