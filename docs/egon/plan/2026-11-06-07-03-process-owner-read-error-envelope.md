# T4-02 Process owner 只读错误信封实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-07-03-process-owner-read-error-envelope.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:03 Asia/Shanghai |
| Updated | 2026-11-06 07:03 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Align early security response for existing Process owner reads |
| Source Requirement | [Master T4-02 and S12](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 76f5f41d07695b8743fc037df6b3ea7f7486de98 |
| Implements Spec | [Owner read error envelope](../spec/2026-11-06-07-02-process-owner-read-error-envelope.md) |
| Spec Status | Review |
| Spec Revision | SHA-256 6f185ebcd6b941ca7f3c1e3639c1f142665dc9c35070d75452c4f8f0ff83fd6e |
| Effective Specs | [Owner read error envelope](../spec/2026-11-06-07-02-process-owner-read-error-envelope.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Correct one PS filter-chain selection and HEAD response path for the existing closed owner error envelope.

## 2. Target Spec and Effective Design

[Effective Spec](../spec/2026-11-06-07-02-process-owner-read-error-envelope.md) controls exact route method, enabled feature flags and wire parity.

## 3. Effective Requirements and Acceptance

| Requirement | Source | Expected acceptance |
| --- | --- | --- |
| REQ-001 | §7/14 | Exact GET and HEAD closed owner error envelope |
| REQ-002 | §7/14/16 | Preserve exact Worker behavior and package scope |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock current source, introduce exact focused assertions, extend the existing filter and verify finite plus producer parity.

### 4.2 Test-first strategy

Typed R1 removes only the correlation match entry. The exact owner GET test fails at no-store for the omitted route.

### 4.3 Sequential and parallel boundaries

Prepare in scratch, apply only after exact parent lock and run owning validations sequentially.

### 4.4 Commit boundaries

One focused Service commit contains only the five declared paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Delivery |
| --- | --- | --- | --- |
| Scoped owner error normalization | Required §7 | EVD-001–004 | Existing wrapper extension |
| Actual live IAM | Separate §18 | Existing runtime acceptance | Operator gate |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| Exact route and response | REQ-001 | R1/G1 | Existing security chain |
| Compatibility and package | REQ-002 | G1/P1/S1 | Existing Worker and OpenAPI artifacts |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Wrapper and ownership | EVD-002/004 | Reuse fixed safe normalized codes |
| Exact match and gate | EVD-001/003 | Existing feature flag plus exact method/path |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Scoped early error shape | Existing Worker wrapper | EVD-002 | Closed wire already available | Extend |
| Contract ownership | Existing packaged OAS and ProcessApiErrors | EVD-001/004 | Existing contract is authoritative | Preserve |

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
| Rule 1 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Name the wrapper for the closed Process security envelope and use exact existing route identifiers. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Select existing feature flags and exact method and path before normalizing the response. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Reuse the existing static two-member OrchestrationErrorDTO wire representation. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Keep filter ownership in the existing explicit security chain and retain diagnostics. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Use existing JDK collection and Spring Security matchers with no dependency change. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Retain the fixed safe JSON envelope already specified by the packaged OpenAPI documents. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Read existing feature properties and preserve their packaged values. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Reuse the existing security response wrapper pattern for selected owner routes. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Use no new dates, durations or timestamp transformations in this transport correction. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 11 | §6.2/7 | EVD-001–004 | §5/7 ordered files | Keep the existing Process Controller and IAM ownership and schema contracts. | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: ProcessSecurityConfiguration.ClosedProcessSecurityErrors handles HTTP status, response bytes and headers only. TEST-001/002 cover wire behavior; existing controller time fields are forwarded without modification.

## 5. Change File Tree

```text
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java
CREATE docs/egon/spec/2026-11-06-07-02-process-owner-read-error-envelope.md
CREATE docs/egon/plan/2026-11-06-07-03-process-owner-read-error-envelope.md
CREATE docs/egon/evidence/2026-11-06-process-owner-read-error-envelope.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 76f5f41d07695b8743fc037df6b3ea7f7486de98, JDK 25 and established offline reactor. Existing Process API and optional feature flags govern enablement. Live IAM and S12 are independent.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Align the exact owner read security error envelope

- Requirements: REQ-001, REQ-002.
- Dependencies: Existing Process security filter chain, owner-read endpoints and Worker recovery wrapper.
- Baseline state: Existing owner OAS requires both fields while generic IAM short circuit emits only code.
- Observable outcome: Exact enabled owner GET/HEAD response aligns with the documented envelope.
- End state: Five exact paths in one commit.
- Test-first gate: Required. Remove only the correlation matcher before G1 and observe the intended failure.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`

- Purpose: Extend the existing Worker test and cover exact enabled owner GET and HEAD wire behavior.
- Symbols: ProcessStorageConfigurationTest.java.
- Repository evidence: EVD-001–004 and the exact existing wire envelope.
- Dependencies and consumers: Existing Process route selection and owning offline verification.
- Why now: Correct security short circuit serialization for enabled owner reads.
- Contract/signature changes: Extend the existing Worker test and cover exact enabled owner GET and HEAD wire behavior.
- Input/output and state mapping: Follow exact method/path, feature gate and existing controlled status mapping in §7.
- Error and edge behavior: Preserve headers and nonselected body; HEAD has an empty entity.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
captureTheWrapperInstalledByEnabledProcessApiWithWorkerRecoveryOff();
checkExactOwnerGET401403503AndHEADWithoutEntity();
assertFeatureOffNeighborMethodWorkerAndOtherStatusParity();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Reuse the current wrapper with a closed method/path and feature gate selection.
- Symbols: ProcessSecurityConfiguration.java.
- Repository evidence: EVD-001–004 and the exact existing wire envelope.
- Dependencies and consumers: Existing Process route selection and owning offline verification.
- Why now: Correct security short circuit serialization for enabled owner reads.
- Contract/signature changes: Reuse the current wrapper with a closed method/path and feature gate selection.
- Input/output and state mapping: Follow exact method/path, feature gate and existing controlled status mapping in §7.
- Error and edge behavior: Preserve headers and nonselected body; HEAD has an empty entity.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
buildImmutableExactMethodMatchersFromExistingEnabledFeatureFlags();
retainBothExactWorkerMatchersOnlyWhenBothWorkerFlagsAreTrue();
normalizeSelected401403503ToClosedErrorEnvelopeAndPreserveHeadMetadata();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-07-02-process-owner-read-error-envelope.md`

- Purpose: Record the existing source and wire parity requirements.
- Symbols: 2026-11-06-07-02-process-owner-read-error-envelope.md.
- Repository evidence: EVD-001–004 and the exact existing wire envelope.
- Dependencies and consumers: Existing Process route selection and owning offline verification.
- Why now: Correct security short circuit serialization for enabled owner reads.
- Contract/signature changes: Record the existing source and wire parity requirements.
- Input/output and state mapping: Follow exact method/path, feature gate and existing controlled status mapping in §7.
- Error and edge behavior: Preserve headers and nonselected body; HEAD has an empty entity.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
describeExistingPackagedOwnerReadSchemasAndCurrentIamShortCircuitGap();
lockExactMethodPathFlagAndHeadResponseBehavior();
separateOwnerFiniteProofAndIndependentLiveS12OperatorAcceptance();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-07-03-process-owner-read-error-envelope.md`

- Purpose: Lock the scoped source and ordered verification commands.
- Symbols: 2026-11-06-07-03-process-owner-read-error-envelope.md.
- Repository evidence: EVD-001–004 and the exact existing wire envelope.
- Dependencies and consumers: Existing Process route selection and owning offline verification.
- Why now: Correct security short circuit serialization for enabled owner reads.
- Contract/signature changes: Lock the scoped source and ordered verification commands.
- Input/output and state mapping: Follow exact method/path, feature gate and existing controlled status mapping in §7.
- Error and edge behavior: Preserve headers and nonselected body; HEAD has an empty entity.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
lockExactParentSpecHashAndTwoSourceFilesBeforeApplyingTheCandidate();
executeTheTypedCorrelationMatcherMutationAndRestoreTheExactGuard();
reviewActualProducerPackageAndExistingSqlBeforeOneFocusedCommit();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-process-owner-read-error-envelope.md`

- Purpose: Record actual owning finite and package evidence and the independent live gate.
- Symbols: 2026-11-06-process-owner-read-error-envelope.md.
- Repository evidence: EVD-001–004 and the exact existing wire envelope.
- Dependencies and consumers: Existing Process route selection and owning offline verification.
- Why now: Correct security short circuit serialization for enabled owner reads.
- Contract/signature changes: Record actual owning finite and package evidence and the independent live gate.
- Input/output and state mapping: Follow exact method/path, feature gate and existing controlled status mapping in §7.
- Error and edge behavior: Preserve headers and nonselected body; HEAD has an empty entity.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
recordOnlyActuallyExecutedTypedRedAndFiniteCounts();
recordActualOpenApiPermissionsConfigurationAndMigrationParity();
stateExactRemainingLiveOperatorAcceptanceBoundaries();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Use exact §8 selectors and clean producer gate.
- Expected result: One intended R1 failure, 49 finite tests and existing package parity.
- Failure returns to: Exact matcher or response gate, then rerun owning verification.
- Completion criteria: Strict docs, exact scope and artifact parity.
- Rollback: Revert the one focused commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `docs/egon/spec/2026-11-06-07-02-process-owner-read-error-envelope.md`, `docs/egon/plan/2026-11-06-07-03-process-owner-read-error-envelope.md`, `docs/egon/evidence/2026-11-06-process-owner-read-error-envelope.md`.
- Commit: fix(process): preserve closed owner read security envelopes.

## 8. Test, Validation, and Quality Gates

```sh
owner_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
owner_base=76f5f41d07695b8743fc037df6b3ea7f7486de98
owner_r1='ProcessStorageConfigurationTest#owner_read_gets_emit_the_exact_closed_security_envelope'
owner_core='ProcessStorageConfigurationTest,ProcessSecurityConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,ProcessAgentOutcomesControllerTest,ProcessStartReceiptControllerTest'
"$owner_runner" "$owner_r1"
"$owner_runner" "$owner_core"
STELLAR_VERIFY_GOAL=verify "$owner_runner" "$owner_core" clean -Ppermission-producer -Dpermission.commit="$owner_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-process-owner-read-error-envelope
```

| Gate | Evidence |
| --- | --- |
| R1 | Remove only addRead(selected, CORRELATION). Exact selector must fail once with expected no-store but null. |
| G1 | Restore matcher. Run storage 15, security 6, permission 13, controller 7, outcomes 4 and receipt 4. |
| P1 | Repeat 49, audit actual AS/PS configuration and semantic PaC, PS existing OAS bytes and absence of PostgreSQL/IT. |
| S1 | Strict Spec/Plan and exact scope, all 28 SQL bytes and clean commit. |

## 9. Migration, Compatibility, Rollout, and Rollback

Existing route metadata, feature defaults and migration checksums are parity gates. Deploy through existing Process owner-read ownership and feature enablement. Source rollback reverts the focused commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Proof |
| --- | --- | --- |
| REQ-001 | Step 1 | R1/G1 exact owner response |
| REQ-002 | Step 1 | G1/P1/S1 scoped compatibility |

## 11. Risks, Blockers, and User Decisions

Live IAM and S12 remain operator verification.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | Keep PS controller and filter ownership. | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | Reuse the existing scoped Worker error wrapper behavior. | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | Use existing Spring Security, JDK and Servlet test dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | ClosedProcessSecurityErrors reflects the scope. | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Require exact enabled method and path before normalizing a response. | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | Existing closed two-member wire model. | None |
| MC-CONVERT-001 | Applicable | PASS | §7/14 | Existing static envelope string contains fixed safe values. | None |
| MC-LOG-001 | Applicable | PASS | §7/14 | Do not expose downstream diagnostics. | None |
| MC-BEAN-001 | Applicable | PASS | §7/14 | Install within the existing enabled Process security chain. | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | Reuse PathPatternRequestMatcher and caching wrapper. | None |
| MC-JSON-001 | Applicable | PASS | §7/14 | Maintain existing closed JSON response contract. | None |
| MC-TIME-001 | Not applicable | N/A | §7/14 | Existing time fields are unaffected by status-only rewriting. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | Reuse existing flags and verify packaged parity. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | Use the existing scoped response wrapper. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | Two exact source paths, three documents and all 28 SQL checksums. | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | Typed matcher mutation, 49 finite cases and actual executable parity. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | Deployed IAM and S12 remain independent runtime proof. | None |

### 12.6 Final verdict

PASS — Ready for user review. Live operator S12 remains independently gated.
