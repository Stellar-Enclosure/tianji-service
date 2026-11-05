# S07 Agent 验收配置扫描隔离计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:14 Asia/Shanghai |
| Updated | 2026-11-06 06:14 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Exact fixture discovery conditions and finite proof |
| Source Requirement | [主计划 T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 9364b27a96c0ae66687784425055a4fab217ad17 |
| Implements Spec | [Fixture isolation Spec](../spec/2026-11-06-06-13-s07-fixture-isolation.md) |
| Spec Status | Review |
| Spec Revision | SHA-256 2b4a3e4f93dfc7f80b49eda0b902631a4bacc9e75197db7d1e82f3ea97f44ed7 |
| Effective Specs | [Fixture isolation Spec](../spec/2026-11-06-06-13-s07-fixture-isolation.md) |
| Depends On Plans | [S07 fixture](./2026-11-06-05-52-s07-governed-start-e.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Correct fixture discovery and prove both condition branches in a finite matrix.

## 2. Target Spec and Effective Design

Use the effective Spec for exact authorized mode selection.

## 3. Effective Requirements and Acceptance

| Requirement | Outcome |
| --- | --- |
| REQ-001 | Exact selected scan candidate. |
| REQ-002 | Two typed REDs, 26 finite cases and six paths. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline, validate both negative candidates and restore complete guards before finite green.

### 4.2 Test-first strategy

R1 removes ordinary mode discriminator. R2 removes S07 mode discriminator while preserving run authorization. Each fails the same exact scan test independently.

### 4.3 Sequential and parallel boundaries

One source step and one commit. Actual operator execution stays separate.

### 4.4 Commit boundaries

Three test Java files and three review files only.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Delivery |
| --- | --- | --- | --- |
| Condition matrix | Required §7 | EVD-001–003 | Existing test classes |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| Conditions and scan | REQ-001/002 | R1/R2/G1 | Spring existing fixture |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Fixture configuration | EVD-001–002 | Reuse existing condition type. |
| Verification | EVD-003 | Use no-runtime scanner. |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Selection | ConditionalOnProperty | Existing ordinary authorization | Exact flags | Reuse |
| Proof | ClassPathScanningCandidateComponentProvider | Existing Spring fixture test path | No context startup | Reuse |

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
| Rule 1 | §6.2 | EVD-001–003 | Files 1–6 | Use existing technical names and one focused test method. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–003 | Files 1–6 | Require exact existing authorization and exact fixture mode. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–003 | Files 1–6 | No POJO or DTO change. | R1/R2/G1/S1 | Step 1 | N/A |
| Rule 4 | §6.2 | EVD-001–003 | Files 1–6 | Spring conditions isolate existing technical fixtures. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 5 | §6.2 | EVD-001–003 | Files 1–6 | Reuse Spring scanner and existing JUnit. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–003 | Files 1–6 | No wire or persisted shape. | R1/R2/G1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | EVD-001–003 | Files 1–6 | No property introduced; existing mode now gates fixtures. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2 | EVD-001–003 | Files 1–6 | Reuse existing condition pattern and main switch. | R1/R2/G1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–003 | Files 1–6 | No time behavior. | R1/R2/G1/S1 | Step 1 | N/A |
| Rule 11 | §6.2 | EVD-001–003 | Files 1–6 | Keep test fixture layer and existing DB ownership. | R1/R2/G1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: The change reads existing Boolean properties during component scanning and does not change a time-bearing field or time boundary.

## 5. Change File Tree

```text
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java
CREATE docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md
CREATE docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md
CREATE docs/egon/evidence/2026-11-06-s07-fixture-isolation.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact baseline `9364b27a96c0ae66687784425055a4fab217ad17`, JDK 25, existing offline reactor. No packaging or real operator run required for this test-only step.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Isolate both Agent fixture configurations

- Requirements: REQ-001, REQ-002.
- Dependencies: Existing S07 and ordinary acceptance configuration.
- Baseline state: Main selects a configuration but both are scan candidates under some flags.
- Observable outcome: Exact mutually exclusive discovered fixture candidates.
- End state: Six scoped paths in one test-only commit.
- Test-first gate: Required. Two typed mutation results in the exact scan test.
- Manual Checks: MC-ARCH-001, MC-VALID-001, MC-BEAN-001, MC-SCOPE-001, MC-TEST-001.
- Literal Rules: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`

- Purpose: Use an isolated component scanner with four exact activation states.
- Symbols: S07GovernedStartAgentFixtureTest.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Use an isolated component scanner with four exact activation states.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
var candidates = scanFixturesWithoutRuntime(environment);
assertNoneIfAuthorizationMissingOrS07Alone(candidates);
assertOrdinaryOnlyForAuthorizedAbsentOrFalse(candidates);
assertS07OnlyForAuthorizedTrue(candidates);
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: Add the ordinary fixture mode discriminator to the existing authorization condition.
- Symbols: ExpenseAgentAcceptanceMain.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Add the ordinary fixture mode discriminator to the existing authorization condition.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
requireFixtureRunAuthorizedTrue();
requireS07ModeFalseOrMissingForOrdinaryFixture();
retainTheExactExistingBeansAndOrdinaryMainBranch();
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`

- Purpose: Add both exact authorization and S07 mode conditions to its scanned class.
- Symbols: S07GovernedStartAgentFixtureConfiguration.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Add both exact authorization and S07 mode conditions to its scanned class.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
requireFixtureRunAuthorizedTrueForS07();
requireS07GovernedStartTrueForGovernedFixture();
keepExistingS07PropertiesIssuerAndBindingInputs();
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md`

- Purpose: Describe exact test configuration isolation and finite acceptance.
- Symbols: 2026-11-06-06-13-s07-fixture-isolation.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Describe exact test configuration isolation and finite acceptance.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
recordScannerEvidenceForBothFixtureClasses();
lockTheFourExpectedSelectionStates();
recordThatRealIamAndDatabaseEvidenceIsIndependent();
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md`

- Purpose: Lock two RED cases and the exact finite selector.
- Symbols: 2026-11-06-06-14-s07-fixture-isolation.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Lock two RED cases and the exact finite selector.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
validateTheExactReviewedSpecDigest();
removeEachConditionSeparatelyForTypedRed();
restoreFullCandidateAndVerifyTheFourOwningSuites();
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-s07-fixture-isolation.md`

- Purpose: Record actual finite report and exact scoped source facts.
- Symbols: 2026-11-06-s07-fixture-isolation.
- Repository evidence: EVD-001–003 in the effective Spec.
- Dependencies and consumers: Existing S07/ordinary Agent fixture and scoped reviewers.
- Why now: Establish exact fixture isolation before the operator run.
- Contract/signature changes: Test configuration or review evidence only.
- Input/output and state mapping: Record actual finite report and exact scoped source facts.
- Error and edge behavior: Refuse a mismatching discovery state or unrelated report.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```java
recordTheTwoOwningRedAssertionFailures();
recordTheTwentySixFiniteCasesAndExpectedSourceScope();
verifyHistoricalSqlByteParityAndListPendingOperatorEvidence();
```

- Verification contribution: R1/R2/G1/S1.
- After this file: Check the exact scoped path.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact R1/R2/G1/S1 in §8.
- Expected result: Two separate expected single failures and 26 finite tests.
- Failure returns to: Current Step 1 and owning source or test.
- Completion criteria: Exact six paths, strict documents and 28 migration bytes verified.
- Rollback: Revert the one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java`, `docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md`, `docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md`, `docs/egon/evidence/2026-11-06-s07-fixture-isolation.md`.
- Commit: test(agent): isolate ordinary and S07 fixtures during scanning.

## 8. Test, Validation, and Quality Gates

```sh
s07_base=9364b27a96c0ae66687784425055a4fab217ad17
s07_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
s07_red='S07GovernedStartAgentFixtureTest#fixture_configurations_are_mutually_exclusive_during_component_scanning'
s07_core='S07GovernedStartAgentFixtureTest,TianjiAgentCheckpointResumeConfigurationTest,S07GovernedStartFixtureTest,ExpenseAcceptanceHarnessTest'
test "$(git rev-parse --verify HEAD)" = "$s07_base"
# Run the same exact selector separately for R1 and R2, restoring each owning source before the next gate.
"$s07_runner" "$s07_red"
"$s07_runner" "$s07_red"
"$s07_runner" "$s07_core" clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md
git diff --check
git diff --cached --check
```

| Gate | Expected |
| --- | --- |
| R1 | S07 scan also includes ordinary after removing the ordinary mode check. |
| R2 | Authorized ordinary scan also includes S07 after removing the S07 mode check. |
| G1 | AS 5 and 8, PS 2 and 11. Total 26 finite cases. |
| S1 | Strict docs, six paths and all 28 existing main SQL byte matches. |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration. Scoped test source only.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–3 | R1/R2/G1 |
| REQ-002 | Step 1 | 1–6 | R1/R2/G1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual S07 operator run remains independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-MODEL-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-CONVERT-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-LOG-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-BEAN-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-JSON-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-TIME-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |

### 12.6 Final verdict

PASS — Ready for user review
