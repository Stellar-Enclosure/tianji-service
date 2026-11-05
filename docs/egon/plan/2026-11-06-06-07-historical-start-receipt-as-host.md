# T4-03 Agent Server 历史 START 对账宿主实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-07-historical-start-receipt-as-host.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:07 Asia/Shanghai |
| Updated | 2026-11-06 06:07 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Three production Java paths, two finite-test paths and three review documents |
| Source Requirement | Master §17.2 S11 and §19 T4-03 |
| Baseline Revision | 1d98cc93d68fbc736cf1d2ba5dc19aead0a6bf93 |
| Implements Spec | [Historical host Spec](../spec/2026-11-06-06-06-historical-start-receipt-as-host.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 06:06 Asia/Shanghai SHA-256 a9acea4e8cb437beb1e3282027bec9c4b2f005ec64f5b3489ed53d326f9d64c8 |
| Effective Specs | [Historical host Spec](../spec/2026-11-06-06-06-historical-start-receipt-as-host.md) |
| Depends On Plans | [Historical command](2026-11-06-05-29-tool-start-receipt-reconciliation.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Explicit checkpoint host](2026-11-06-05-07-agent-checkpoint-as-resume-host.md) |

## 1. Summary

为既有历史 START 对账增加独立显式导入宿主。宿主按原后端摘要选择独立 READ 注册，不改原命令或命名空间。继续通过既有原主体和当前授权证明。

## 2. Target Spec and Effective Design

[Historical host Spec](../spec/2026-11-06-06-06-historical-start-receipt-as-host.md) §§4/7/9/14 defines opt-in composition, original routing and lifecycle. Preserve existing storage, runtime authority and receipt write.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/9 | Actual AS shell does not import host; explicit import requires exact qualified holder. |
| REQ-002 | §7/9 | Exact retained alias coverage and disjoint START registrations with original query digest. |
| REQ-003 | §7/9/14 | Exact non-secret registration preflight and existing current authority/actor proof. |
| REQ-004 | §7/11/15 | Owning ledger and source, bounded no-redirect reader, close only owned resources. |
| REQ-005 | §14/16/18 | Typed RED, scoped finite and actual package proof. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Pin exact source and Spec. Add finite host and reader tests, then existing verifier preflight, alias reader and named composition. Verify the typed negative, restore candidate and compare actual package boundaries.

### 4.2 Test-first strategy

R1 removes only the exact alias disjointness predicate `|| startRegistrations.contains(id)`. The reader must then wrongly accept a retained START registration and the matching finite test fails once. Restore the predicate before other gates.

### 4.3 Sequential and parallel boundaries

Draft exact test and production code in scratch. Lock one corrected standalone AS baseline, then apply one scoped source Step and run owning checks sequentially.

### 4.4 Commit boundaries

One focused production host commit after exact finite and package proof and review artifacts.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Plan decision |
| --- | --- | --- | --- |
| Explicit host | Required, §7 | Actual scanner and existing AR direct construction | One owned façade, no discovery change. |
| Distinct READ alias | Required, §7/9 | Original START registration is in immutable digest | Pin alias separately at credential resolution. |
| Existing Process and storage | Required, §7/11 | Exact READ adapter and positive append exist | Reuse both without new persistence. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| Finite guards | REQ-001/002/004 | R1/G1 | Existing test harness and typed contracts |
| Verifier and reader | REQ-002/003 | R1/G1/P1 | Existing fixed binding and OAuth key |
| Explicit host | REQ-001/003/004 | G1/P1 | Exact owning ledger and reader |
| Review artifacts | REQ-005 | S1 | Actual owning result and exact package scope |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Gate |
| --- | --- | --- | --- |
| Architecture | Existing AS scanner and AR owning service | One named technical host outside default scan | G1/P1 |
| Validation | Exact Process target and verified SERVICE credential | Local registration key preflight and existing request-time proof | R1/G1 |
| Ownership | Existing bounded reader and JDK HTTP | Close owned reader and client only | G1/P1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Verification |
| --- | --- | --- | --- | --- | --- |
| Historical observation | ToolStartReceiptReconciliationService | EVD-002 current authority and ledger | Existing command | Compose directly | TEST-003 |
| Independent exact READ | IdpServiceOAuth2Client and existing verifier | EVD-003/004 exact key and actor | Existing proof, missing alias pin | Select separate alias and reuse verification | TEST-002 |
| AS startup | Existing explicit checkpoint host and ordinary scanner | EVD-001 exact source and opt-in import | Existing pattern | Use separate explicit import and same ledger | TEST-001 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep exact technical host and reader names and existing command input types. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Validate retained key coverage, disjoint START identity and ordinary ledger/source. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use an immutable explicit binding and existing receipt value. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use one named technical bean outside default scan, qualified existing identity resources and redacted diagnostics. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing verifier, JDK HTTP and Spring. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Reuse the existing Process receipt codec and persistence representation. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use exact Java import, keep production profiles and configuration unchanged. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use the Composition Root and lifecycle façade around the existing SDK Adapter. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Use existing bounded elapsed windows and java.time.Duration for HTTP connect. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | §5 and §7 ordered files | Keep AS host and AR ledger ownership, with existing command behavior and no DDL. | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Host HTTP connect and existing reader budget | Duration | java.time.Duration and monotonic elapsed nanos | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Three-second connect bound and existing reader caps apply independently of wall clock or DST | JDK elapsed clock and current receipt deadlines | Original AR time checks and persisted clocks retained | Step 1 TEST-002/003 |

## 5. Change File Tree

```text
MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfigurationTest.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java
CREATE docs/egon/spec/2026-11-06-06-06-historical-start-receipt-as-host.md
CREATE docs/egon/plan/2026-11-06-06-07-historical-start-receipt-as-host.md
CREATE docs/egon/evidence/2026-11-06-historical-start-receipt-as-host.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Use JDK 25 and existing finite reactor. Explicit trusted importer supplies the retained bindings, exact READ registration map, independent current authority and configured OAuth facade. Keep the ordinary owning ledger. Runtime IAM grant, schema operation and application activation remain separate.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Compose the explicit AS historical START host

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing AR historical reconciliation, fixed Process READ adapter, exact OAuth identity proof and ordinary AS ledger.
- Baseline state: Existing command and reader can be manually constructed; no distinct named host or retained READ alias pin.
- Observable outcome: One named host requires explicit import, exact retained READ alias and ordinary owning ledger.
- End state: Five Java and three review paths verified and committed as one source Step.
- Test-first gate: Required. R1 drops only alias disjointness and must fail once.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java`

- Purpose: Exercise retained binding identity and the distinct alias through the existing exact GET.
- Symbols: TianjiFixedBpmnStartReceiptReaderTest.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Exercise retained binding identity and the distinct alias through the existing exact GET.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Compare legacy and aliased target and check the actual READ request proof.
- Error and edge behavior: Refuse missing, extra, reused START or invalid aliases without a token or Process request.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertAliasMapCoversOnlyTheExactRetainedBindings();
assertStartRegistrationCannotBeReusedForRead();
assertDistinctReadRegistrationIsUsedForOneExactGet();
assertPreflightNeverRequestsAToken();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfigurationTest.java`

- Purpose: Test the actual dormant AS shell and explicit non-web Spring import with controlled collaborators.
- Symbols: TianjiHistoricalStartReceiptConfigurationTest.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Test the actual dormant AS shell and explicit non-web Spring import with controlled collaborators.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Use exact named retained binding, fake OAuth metadata, mocked storage and owned HTTP.
- Error and edge behavior: Missing named holder, storage mismatch or changed client must prevent host creation.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertActualDefaultApplicationOmitsTheHost();
assertExplicitImportRequiresTheExactQualifiedBinding();
assertWrongRegisteredClientCannotReachStoreOrTokenAcquisition();
assertCloseStopsOnlyOwnedResourcesAndRejectsNewCalls();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java`

- Purpose: Reuse the exact non-secret registration and trust checks during explicit startup.
- Symbols: TianjiProcessToolCredentialVerifier#requireConfigured.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Reuse the exact non-secret registration and trust checks during explicit startup.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Build the same typed token request and check original issuer, client, tenant and scope.
- Error and edge behavior: Reject untrusted or mismatched registration without token acquisition.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireCurrentTenantIssuerTrust();
buildTheExactExistingServiceTokenRequest();
readConfiguredAuthorizationKeyWithoutAuthorizing();
requireKeyToMatchAllRetainedActorDimensions();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java`

- Purpose: Provide explicit READ alias selection for every retained backend.
- Symbols: TianjiFixedBpmnStartReceiptReader.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Provide explicit READ alias selection for every retained backend.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Keep the original material and namespace, use alias only to form the READ target.
- Error and edge behavior: Reject incomplete or extra mapping and any reuse of a retained START registration.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyExactReadAliasCoverageByOriginalBackendSha256();
rejectEveryAliasThatMatchesAnyRetainedStartRegistration();
preflightEveryReadTargetBeforeOwningWorkerCreation();
resolveHistoricalGetFromOriginalBackendAndUseOnlyTheBoundReadAlias();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java`

- Purpose: Compose the existing exact command and reader as one explicitly imported bean outside default AS scan.
- Symbols: TianjiHistoricalStartReceiptConfiguration and HistoricalStartReceiptHost.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Compose the existing exact command and reader as one explicitly imported bean outside default AS scan.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Use named retained binding and exact ordinary data source, existing authority and bounded owned HTTP.
- Error and edge behavior: Reject invalid storage or IAM setup at startup and refuse closed host use.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireEnabledStorageAndExactOrdinaryLedgerSource();
assembleExistingCredentialVerifierAndAliasReader();
constructExistingHistoricalReconciliationServiceAndExposeOnlyTheHost();
closeOwnedReaderAndClientOnFailureOrShutdown();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 6 — `CREATE docs/egon/spec/2026-11-06-06-06-historical-start-receipt-as-host.md`

- Purpose: Define the exact historical authority, retained binding and host boundary.
- Symbols: 2026-11-06-06-06-historical-start-receipt-as-host.md.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Define the exact historical authority, retained binding and host boundary.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Preserve the original command scope and exact evidence links.
- Error and edge behavior: Reject any claim beyond the observed gates.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockRetainedBindingAndCurrentReadAuthorizationSemantics();
traceEachScopeRequirementToFiniteSource();
recordIndependentOperatorIamAndDatabaseRequirements();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 7 — `CREATE docs/egon/plan/2026-11-06-06-07-historical-start-receipt-as-host.md`

- Purpose: Lock source paths, finite gates and actual package evidence.
- Symbols: 2026-11-06-06-07-historical-start-receipt-as-host.md.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Lock source paths, finite gates and actual package evidence.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Preserve the original command scope and exact evidence links.
- Error and edge behavior: Reject any claim beyond the observed gates.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockTheBaselineAndExactFiveJavaFiles();
applyTheExactAliasGuardMutationAndRestoreCandidate();
verifyOwnedPackageAndAllMigrationBytesBeforeCommit();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.

#### File 8 — `CREATE docs/egon/evidence/2026-11-06-historical-start-receipt-as-host.md`

- Purpose: Record actual source verification independently of live operator acceptance.
- Symbols: 2026-11-06-historical-start-receipt-as-host.md.
- Repository evidence: EVD-001–005 and effective Spec §7.
- Dependencies and consumers: Existing explicit AS historical host flow in §4.6.
- Why now: Record actual source verification independently of live operator acceptance.
- Contract/signature changes: Scoped Java overload, exact internal host, finite check or review document.
- Input/output and state mapping: Preserve the original command scope and exact evidence links.
- Error and edge behavior: Reject any claim beyond the observed gates.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordActualTypedRedAndFiniteResults();
recordProductionPackageScopeAndExactSqlParity();
stateCurrentLiveAuthorizationAndIamAsIndependentOperatorGates();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared owner and exact evidence.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact typed negative, finite and package commands in §8.
- Expected result: Single missing alias rejection under mutation, then 96 finite cases and package/config parity.
- Failure returns to: First relevant guard or package check. Restore original predicate before finite validation.
- Completion criteria: Exact scope, immutable migrations, real finite/package proof and separate runtime boundaries.
- Rollback: Revert the single scoped source commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReaderTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartReceiptReader.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiHistoricalStartReceiptConfiguration.java`, `docs/egon/spec/2026-11-06-06-06-historical-start-receipt-as-host.md`, `docs/egon/plan/2026-11-06-06-07-historical-start-receipt-as-host.md`, `docs/egon/evidence/2026-11-06-historical-start-receipt-as-host.md`.
- Commit: feat(agent): compose explicit historical START receipt host.

## 8. Test, Validation, and Quality Gates

```sh
history_base=1d98cc93d68fbc736cf1d2ba5dc19aead0a6bf93
history_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse HEAD)" = "$history_base"
history_r1='TianjiFixedBpmnStartReceiptReaderTest#alias_map_must_cover_only_the_exact_retained_backends_and_never_use_a_start_registration'
history_core='TianjiHistoricalStartReceiptConfigurationTest,TianjiFixedBpmnStartReceiptReaderTest,TianjiFixedBpmnStartToolTransportTest,TianjiGovernedProcessToolConfigurationTest,ToolStartReceiptReconciliationServiceTest'
# Apply only the disjointness mutation for the first selector and then restore.
"$history_runner" "$history_r1"
"$history_runner" "$history_core"
STELLAR_VERIFY_GOAL=verify "$history_runner" "$history_core" clean -Ppermission-producer -Dpermission.commit="$history_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-historical-start-receipt-as-host
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-06-historical-start-receipt-as-host.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-07-historical-start-receipt-as-host.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | One missing IllegalStateException on a reused START alias. |
| G1 | Host 5, receipt reader 10, live START 32, governed configuration 42 and AR historical service 7. Exactly 96 finite cases without failures, errors or skips. |
| P1 | Same 96 in clean permission-producer. Actual AS contains exact host, reader and verifier classes outside default shell import. AS/PS configuration and semantic PaC match. No PostgreSQL/IT report. |
| S1 | Strict Spec/Plan, exact eight paths and all 28 SQL bytes. |

### Independent operator prerequisites

An actual host uses a trusted configuration with `@Import(TianjiHistoricalStartReceiptConfiguration.class)`. It registers a named `tianjiHistoricalStartReceiptBinding` containing retained exact fixed START bindings, the immutable alias map, a current historical authority implementation and a configured OAuth client. Alias registrations resolve the original tenant issuer, SERVICE subject, app and Process audience with the existing exact `tianji:process:read` grant. The caller obtains the named host from its trusted AS composition. Actual current IAM, Agent DB and original Process receipt are separate operator verification.

## 9. Migration, Compatibility, Rollout, and Rollback

The new host uses existing schemas, permissions and routes. Default application omits it. Existing reader and live START compatibility stay in place. Revert the single focused commit for rollback.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Boundary | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Exact explicit AS import | G1/P1 |
| REQ-002 | Step 1 | Immutable alias map and original target | R1/G1/P1 |
| REQ-003 | Step 1 | Existing current exact authority and verifier | G1/P1 |
| REQ-004 | Step 1 | Exact storage and owned lifecycle | G1/P1 |
| REQ-005 | Step 1 | Exact source and verification evidence | R1/G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual alias registration, current historical authority, PostgreSQL and Process receipt access remain independently provisioned operator conditions.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | AS owns explicit composition and AR owns historical storage. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse exact verifier, original bindings, reader and AR command. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Existing JDK, Spring, JDBC and test dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Use existing technical reader, host and binding naming. | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Exact retained mapping, credential key and owning storage guards. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable Java binding and existing receipt. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO converter or new representation. | None |
| MC-LOG-001 | Applicable | PASS | §15 | Redact retained metadata and sanitize startup failures. | None |
| MC-BEAN-001 | Applicable | PASS | §5/7 | One named bean through explicit import outside ordinary AS scan. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing verifier, hashing and JDK HTTP. | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing Process SDK contract remains unchanged. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing bounded request window and explicit HTTP connect bound. | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | Explicit Java import and unchanged production configuration. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Composition Root and lifecycle façade reuse SDK Adapter. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Five Java paths, three review documents and 28 immutable SQL files. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Typed negative, 96 finite tests and real packaged scope. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual grants, authoritative implementation and S11 remain operator work. | None |

### 12.6 Final verdict

PASS — Ready for user review
