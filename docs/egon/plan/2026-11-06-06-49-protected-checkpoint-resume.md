# T4-03 受保护的失联 checkpoint 恢复入口实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-49-protected-checkpoint-resume.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:49 Asia/Shanghai |
| Updated | 2026-11-06 06:49 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Exact protected checkpoint RESUME route and independent production composition |
| Source Requirement | [Master T4-03 and S11](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 50f957a7cb3536d1aadce06403f98969d99c17f2 |
| Implements Spec | [Protected checkpoint resume](../spec/2026-11-06-06-48-protected-checkpoint-resume.md) |
| Spec Status | Review |
| Spec Revision | SHA-256 2f68e287194621793cf292989d56368614473679797bb8c62905ce5c6c421cd9 |
| Effective Specs | [Protected checkpoint resume](../spec/2026-11-06-06-48-protected-checkpoint-resume.md) |
| Depends On Plans | [Resume grant handoff](./2026-11-06-06-21-checkpoint-resume-grant-handoff.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Deliver one default-off protected owner POST for the existing verified expired cut and explicit independent host composition.

## 2. Target Spec and Effective Design

[Effective Spec](../spec/2026-11-06-06-48-protected-checkpoint-resume.md) controls literal route, closed command, exact owner authorization, wait and artifact proofs.

## 3. Effective Requirements and Acceptance

| Requirement | Source | Expected acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | One exact USER command with both current permissions and 200/no-store original ACK |
| REQ-002 | §7/14 | Explicit independent issuance and bounded detached wait |
| REQ-003 | §14/16 | Two typed negatives, 32 finite and actual executable parity |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the parent and effective Spec, add the focused tests, then apply the exact controller, security and composition and compare owning artifacts.

### 4.2 Test-first strategy

Typed R1 removes the explicit USER check and reproduces SERVICE admission; R2 removes the ordinary START alias exclusion and reproduces unsafe issuance configuration.

### 4.3 Sequential and parallel boundaries

Route and composition may be prepared independently in scratch. Apply the whole source Step only after exact lock and run owning validations sequentially.

### 4.4 Commit boundaries

One focused Service commit contains only the thirteen declared paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Delivery |
| --- | --- | --- | --- |
| Exact USER POST | Required §9 | EVD-001–005 | Route and exact PaC |
| Independent configured host | Required §7 | Existing handoff and host | Explicit guarded composition |
| Live IAM and PostgreSQL | Separate §18 | Existing operator prerequisites | Runtime gate |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| Route and strict body | REQ-001 | R1/G1 | Existing exact access and worker |
| Composition | REQ-002 | R2/G1 | Existing recovery and current IAM clients |
| Package evidence | REQ-003 | P1/S1 | Owning executable jars |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Security and identity | EVD-003–005 | Exact protected USER resource and literal path |
| Composition | EVD-001/002 | Explicit named deployment and independent issue clients |
| Bounded contract | EVD-001/002 | Existing command and ACK, 4 KiB body and 35s detached wait |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Current admission | TianjiAgentAccess | EVD-003 | Exact current resource already available | Reuse |
| Checkpoint command | Existing handoff | EVD-001/002 | Exact ownership and durable result already available | Reuse |
| Packaged contract | Existing AS static OAS | EVD-003 | Existing packaging suitable | Reuse |

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
| Rule 1 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Name the scoped controller, configuration, deployment binding and focused tests by their owning operation. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Bound body bytes and validate a closed JSON shape before current owner admission and exact existing command validation. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Reuse the existing immutable ResumeCommand and Acknowledgement and explicit deployment binding. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Keep named dependencies explicit and use existing sanitized error mapping and runtime diagnostics. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Use the existing JDK duration, Spring security and Jackson without an added dependency. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Reuse strict Jackson parsing and validate required integer JSON tokens before existing MVC conversion. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Add one explicit default-off configuration and the exact USER permission-to-scope mapping. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Reuse the existing handoff facade and adapter composition for the independently authorized worker. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Bound synchronous servlet response wait independently of the worker and preserve current interrupt status. | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5/7 ordered files | Retain AS/AR ownership and the established V16 transaction and command receipt. | R1/R2/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | responseWait | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: internal wait | At most 35 seconds from successful handoff return; original worker keeps its lifecycle | JDK timed wait | Durable original ACK and existing worker deadline | Step 1 TEST-001 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeControllerTest.java
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfigurationTest.java
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiSecurityConfigurationTest.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeController.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRequestBodyAdvice.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfiguration.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml
CREATE stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-checkpoint-resume-v1.json
CREATE docs/egon/spec/2026-11-06-06-48-protected-checkpoint-resume.md
CREATE docs/egon/plan/2026-11-06-06-49-protected-checkpoint-resume.md
CREATE docs/egon/evidence/2026-11-06-protected-checkpoint-resume.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 50f957a7cb3536d1aadce06403f98969d99c17f2, JDK 25 and established offline reactor. Source scope includes a default-off protected declaration. Live activation, runtime IAM and S11 remain operator acceptance.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add exact protected expired checkpoint RESUME

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing AR checkpoint host, current decision client and synchronous handoff.
- Baseline state: Current worker and host require a trusted externally authorized Java caller.
- Observable outcome: Exact USER REST command and closed durable response through independent configuration.
- End state: Thirteen exact paths in one commit.
- Test-first gate: Required. Execute two isolated typed mutations and restore guards before G1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeControllerTest.java`

- Purpose: Exercise the exact guarded HTTP route and test bounded detached response behavior.
- Symbols: AgentCheckpointResumeControllerTest.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Exercise the exact guarded HTTP route and test bounded detached response behavior.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
buildMockMvcWithExistingBodyAndErrorAdvice();
verifyTheDedicatedCurrentGuardBeforeCallingTheExactHandoff();
assertClosedBodyAndBoundedResponseTimeoutLeaveTheWorkerFutureUntouched();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfigurationTest.java`

- Purpose: Prove opt-in composition and fail-closed selection of the exact independent issuance alias.
- Symbols: TianjiCheckpointResumeApiConfigurationTest.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Prove opt-in composition and fail-closed selection of the exact independent issuance alias.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
createAnExplicitDeploymentWithExactRecoveryDatasourceAndIssuerBinding();
assertMissingPrerequisitesFailBeforeAnyIssuerOrRecoveryConstruction();
assertAReusedOrdinarySTARTRegistrationIsRejectedBeforeBindingTheIssueClient();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiSecurityConfigurationTest.java`

- Purpose: Extend the existing Agent security test with the default-off controller and exact USER path.
- Symbols: TianjiSecurityConfigurationTest.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Extend the existing Agent security test with the default-off controller and exact USER path.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertDefaultServletContextLacksTheCheckpointResumeController();
resolveAuthenticationPolicyForFixedCheckpointResumePostAndRequireUSER();
retainExistingAssertionsForSharedAgentAndServiceOnlyLinkedPaths();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeController.java`

- Purpose: Expose the protected command and return only the exact durable acknowledgement.
- Symbols: AgentCheckpointResumeController.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Expose the protected command and return only the exact durable acknowledgement.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
authorizeTheDedicatedResourceAndRequireVerifiedOriginalUSERBeforeAnyHandoff();
submitTheExactResumeCommandOnTheCurrentServletRequest();
waitForAtMostThirtyFiveSecondsThenReturnTheExactDurableAcknowledgementOrSafeError();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRequestBodyAdvice.java`

- Purpose: Validate the four KiB closed command before MVC conversion.
- Symbols: AgentRequestBodyAdvice.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Validate the four KiB closed command before MVC conversion.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
selectExistingRequestBodyAdviceOnlyForTheDeclaredResumeCommandType();
readAtMostFourKiBPlusOneAndRejectOversizedBodies();
requireThreeExactFieldsAndAnIntegralRevisionBeforeExistingJacksonConversion();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java`

- Purpose: Declare dedicated resource identity, RESUME permission, scope and localized labels.
- Symbols: TianjiAgentPermissions.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Declare dedicated resource identity, RESUME permission, scope and localized labels.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
declareDedicatedCheckpointResumePermissionAndExactResourceAndScopeConstants();
provideMatchingEnglishAndChinesePermissionAndResourceLabels();
reuseExistingREADPermissionForTheSessionBackedResumePolicy();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java`

- Purpose: Admit one fixed authenticated POST in the existing AS security chain.
- Symbols: TianjiAgentSecurityConfiguration.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Admit one fixed authenticated POST in the existing AS security chain.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
addTheFixedCheckpointResumePostToTheExistingAuthenticatedMatchers();
verifyPathIsOutsideSharedAgentNamespaceSoIdentityPolicyRequiresUSER();
leaveAllOtherDispatchAndAuthenticationRulesInTheirExistingOrdering();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfiguration.java`

- Purpose: Compose the existing AR host, handoff and current authority from exact supplied deployment binding.
- Symbols: TianjiCheckpointResumeApiConfiguration.java.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Compose the existing AR host, handoff and current authority from exact supplied deployment binding.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
gateTheCompositionOnExistingAgentApiAndExplicitCheckpointResumeApiEnablement();
requireTheNamedDeploymentBindingAndExactIndependentIssuerRegistration();
importExistingRecoveryHostAndBuildExactBoundIssueAndDecisionClients();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Provide the default-off API gate and exact USER permission-to-scope mapping.
- Symbols: application.yml.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Provide the default-off API gate and exact USER permission-to-scope mapping.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
addExplicitCheckpointResumeApiDefaultFalseUnderAgentConfiguration();
mapDedicatedCheckpointResumePermissionToTheExactExistingNewScopeString();
retainOtherConfigurationKeysAndValuesWithinTheOwningApplicationResource();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 10 — `CREATE stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-checkpoint-resume-v1.json`

- Purpose: Publish the exact closed REST contract as the packaged OpenAPI document.
- Symbols: agent-checkpoint-resume-v1.json.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Publish the exact closed REST contract as the packaged OpenAPI document.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
declareOneLiteralProtectedPostAndBothRequiredUserOAuthScopes();
packageClosedCommandAcknowledgementAndExistingSafeErrorSchemas();
assertExactOperationAndResourceIdentityAgainstTheRealBootArchive();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 11 — `CREATE docs/egon/spec/2026-11-06-06-48-protected-checkpoint-resume.md`

- Purpose: Document source and operator authority boundaries.
- Symbols: 2026-11-06-06-48-protected-checkpoint-resume.md.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Document source and operator authority boundaries.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
writeTheExactOwnerCurrentAuthorityAndExistingCheckpointEligibilityRequirements();
recordBoundedTransportContractAndProductionEnablementPreconditions();
separateOfflineFiniteProofFromLiveIAMAndPostgreSQLOperatorEvidence();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 12 — `CREATE docs/egon/plan/2026-11-06-06-49-protected-checkpoint-resume.md`

- Purpose: Lock this exact implementation and verification scope.
- Symbols: 2026-11-06-06-49-protected-checkpoint-resume.md.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Lock this exact implementation and verification scope.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockExactParentSpecHashAndTenSourcePathsBeforeApplyingTheCombinedCandidate();
runTheTwoTypedNegativesAndRestoreEachGuardBeforeOwningRegression();
reviewActualASAndPSExecutableArtifactsBeforeCommittingTheOneStep();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

#### File 13 — `CREATE docs/egon/evidence/2026-11-06-protected-checkpoint-resume.md`

- Purpose: Record actual local proof and the separate operator gate.
- Symbols: 2026-11-06-protected-checkpoint-resume.md.
- Repository evidence: EVD-001–005 and exact protected command contract.
- Dependencies and consumers: Existing AS checkpoint command chain and owning offline verification.
- Why now: Gate one exact REST command on independent current authority.
- Contract/signature changes: Record actual local proof and the separate operator gate.
- Input/output and state mapping: Follow the exact command and response boundaries in §7/9.
- Error and edge behavior: Reject mismatched source, authority, body or worker response and preserve existing sanitized envelope.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordOnlyExecutedNegativeAndFiniteResultsAgainstExactCurrentBaseline();
recordPackagedConfigurationManifestOpenAPIAndMigrationChecksumEvidence();
stateExactRemainingLiveOperatorAcceptanceAndProductionProvisioningBoundaries();
```

- Verification contribution: R1/R2/G1/P1/S1.
- After this file: Verify exact path and owning step dependency.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Use exact §8 selectors and clean producer gate.
- Expected result: One intended R1 and R2 failure each, 32 finite green tests and exact artifact delta.
- Failure returns to: The exact affected file or guard, then rerun the blocked gate.
- Completion criteria: Strict docs, exact owning evidence and all thirteen paths audited.
- Rollback: Disable exact feature property; revert the focused commit for source rollback.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeControllerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiSecurityConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeController.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRequestBodyAdvice.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentSecurityConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeApiConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-checkpoint-resume-v1.json`, `docs/egon/spec/2026-11-06-06-48-protected-checkpoint-resume.md`, `docs/egon/plan/2026-11-06-06-49-protected-checkpoint-resume.md`, `docs/egon/evidence/2026-11-06-protected-checkpoint-resume.md`.
- Commit: feat(agent): expose protected checkpoint resume.

## 8. Test, Validation, and Quality Gates

```sh
resume_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
resume_base=50f957a7cb3536d1aadce06403f98969d99c17f2
resume_r1='AgentCheckpointResumeControllerTest#rejects_services_denied_current_permission_and_query_parameters_before_handoff'
resume_r2='TianjiCheckpointResumeApiConfigurationTest#refuses_reused_start_registration_before_building_issuance_clients'
resume_core='AgentCheckpointResumeControllerTest,TianjiCheckpointResumeApiConfigurationTest,TianjiSecurityConfigurationTest,TianjiCheckpointResumeGrantHandoffTest,TianjiAgentCheckpointResumeConfigurationTest'
"$resume_runner" "$resume_r1"
"$resume_runner" "$resume_r2"
"$resume_runner" "$resume_core"
STELLAR_VERIFY_GOAL=verify "$resume_runner" "$resume_core" clean -Ppermission-producer -Dpermission.commit="$resume_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-protected-checkpoint-resume
```

| Gate | Evidence |
| --- | --- |
| R1 | Remove only explicit SERVICE refusal in controller. Exact selector must fail with Status expected:<403> but was:<200>. |
| R2 | Change anyMatch(ordinaryRegistrations::contains) to anyMatch(id -> false). Exact selector must fail once with missing IllegalStateException. |
| G1 | Restore both guards. Run nine wire, four composition, three security, eight handoff and eight host tests. |
| P1 | Repeat 32 in clean producer, audit actual AS metadata and packaged OAS, PS parity and absence of PostgreSQL/IT. |
| S1 | Strict Spec/Plan and exact scope, 28 SQL checksums and clean commit. |

## 9. Migration, Compatibility, Rollout, and Rollback

Verify immutable existing migrations and ordinary AS and PS resource parity outside the exact declared addition. Enablement is an independent operator action with configured trust and recovery identity.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Proof |
| --- | --- | --- |
| REQ-001 | Step 1 | R1/G1/P1 exact route and contract |
| REQ-002 | Step 1 | R2/G1/P1 configured independent host |
| REQ-003 | Step 1 | G1/P1/S1 actual owning evidence |

## 11. Risks, Blockers, and User Decisions

Active protected policy, real tenant scopes and independently verified PostgreSQL recovery role remain deployment and S11 acceptance prerequisites.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/9/14 | Retain exact AS identity and AR checkpoint ownership. | None |
| MC-REUSE-001 | Applicable | PASS | §7/9/14 | Reuse established handoff, immutable command and current IAM clients. | None |
| MC-DEP-001 | Applicable | PASS | §7/9/14 | Existing JDK and Spring security and Jackson provide all needed support. | None |
| MC-NAME-001 | Applicable | PASS | §7/9/14 | Checkpoint-specific command clearly distinguishes the REST lane. | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Verify exact USER, closed body, exact original ACK and bound deployment. | None |
| MC-MODEL-001 | Applicable | PASS | §7/9/14 | Reuse immutable command and acknowledgement with closed OAS. | None |
| MC-CONVERT-001 | Applicable | PASS | §7/9/14 | Existing MVC conversion follows bounded exact Jackson validation. | None |
| MC-LOG-001 | Applicable | PASS | §7/9/14 | Return only existing safe error codes and keep bearer and opaque ticket out of diagnostics. | None |
| MC-BEAN-001 | Applicable | PASS | §7/9/14 | Gate production composition and controller on exact opt-in, with named dependencies. | None |
| MC-UTIL-001 | Applicable | PASS | §7/9/14 | Use existing security guard, JSON tooling and JDK bounded wait. | None |
| MC-JSON-001 | Applicable | PASS | §7/9/14 | Enforce explicit integral revision, unknown fields and duplicate rejection. | None |
| MC-TIME-001 | Applicable | PASS | §7/9/14 | Bound HTTP response wait and retain worker lifecycle on timeout and interruption. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/9/14 | Ship default off, document required independent deployment resources. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/9/14 | Compose exact protected facade over existing handoff. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/9/14 | Ten exact source paths, three documents and all 28 SQL hashes. | None |
| MC-TEST-001 | Applicable | PASS | §7/9/14 | Two typed mutation gates, 32 finite tests and actual executable proof. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/9/14 | Live protected catalog, scope and role configuration and S11 remain independently gated. | None |

### 12.6 Final verdict

PASS — Ready for user review. Live operator S11 remains independently gated.
