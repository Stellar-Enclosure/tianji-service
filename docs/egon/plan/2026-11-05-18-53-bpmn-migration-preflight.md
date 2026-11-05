# 天机单实例 BPMN 迁移只读预检实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-18-53-bpmn-migration-preflight.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 18:53 Asia/Shanghai` |
| Updated | `2026-11-05 19:04 Asia/Shanghai` |
| Owner | Root 审阅并负责锁定实际执行范围 |
| Repository | `tianji-service` |
| Scope | T4-01B0 单个 PS 内部诊断提交 |
| Source Requirement | [整体蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) `§19 T4-01` 与主 Spec `REQ-001–006` |
| Baseline Revision | `tianji-service 695c9115921ba3068db445526b7bf51be87a350b` clean，Web `e5d14225abacd4766aa21f9b59ce2364cd23bb3b` context |
| Implements Spec | [BPMN 迁移只读预检](../spec/2026-11-05-18-46-bpmn-migration-preflight.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 18:52 Asia/Shanghai` SHA-256 `dc5bda0f6ec4ec472791c58194fd15faffc267cab8e8c972bebc3733b99dfc45` |
| Effective Specs | [主 Spec](../spec/2026-11-05-18-46-bpmn-migration-preflight.md)，[蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) `§19 T4-01`，[现有 Process Control](../spec/2026-11-04-23-22-process-control-cancellation.md) `§7, §9, §11` preserved only |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

Root approved only this exact internal default-off source under the user master Plan. It grants no migration, approval, rollback, activation, native state writes or real runtime opt-in. Current PS metric and prior source/SQL are protected.

一个 PS commit 实现默认关闭的内部诊断。先核既有两个资源权限、同一可信 actor 和 exact START，再在同一物理只读 ProcessDB 快照比对两个当前发布、普通 timer 和缺席证据，最后调用唯一 Flowable 单实例 validation。有限 U 与实际隔离 I、运行启用和治理迁移分别管理。

## 2. Target Spec and Effective Design

### 2.1 Primary target

Root 明确批准 exact owner-local PS 技术诊断、普通 timer 范围、默认关闭及无公开入口。Spec/Plan 保持 Review。源实施以前复核最终 SHA、路径和 exact PS baseline。先于治理激活的源交付仅限独立结构诊断。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [BPMN 迁移只读预检](../spec/2026-11-05-18-46-bpmn-migration-preflight.md) | Review, exact SHA above | §3–§20 | six new PS requirements |
| Existing authority | [Process Control](../spec/2026-11-04-23-22-process-control-cancellation.md) | current source | §7, §9, §11 | current exact native owner/read, no new cancellation requirements |
| Roadmap | [整体蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) | current | §19 T4-01 | preserve migration/activation sequencing |

### 2.3 Superseded or excluded content

No supersession. Activation, live signing, revocation, pointer, public transport or permission, any apply, ACT DML and row locks, engine task/dispatch operations, migrations, CMMN and dependencies remain outside the Step.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/7/9 | internal off, current dual authorizations, exact START | no bean off; no foreign target lookup | service/config |
| REQ-002 | §4/7/10 | source/target existing releases and native authority | same tenant/key, legacy digests, exact caller pin | release/model/service |
| REQ-003 | §4/7.3 | exact plain timer and no attached evidence | root/leaf/timer only and plain graph | model/native |
| REQ-004 | §4/6.3/7.3 | one physical read-only primary snapshot | source/manager/UTC/timing and post-call guard | native/service |
| REQ-005 | §4/9 | native structure-only | one exact validator, no writes or raw diagnostics | service |
| REQ-006 | §4/14/16 | finite and actual I are distinct | package and finite evidence; actual I pending | tests/docs |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

One PS source commit. Add finite assertions. A provides only exact compile-safe types and inert stubs. After all real REDs, implement existing model/release additions, same-owner native scalar evidence, internal service and default-off named wiring. Finish exact finite/static package gates and review.

### 4.2 Test-first strategy

M1 baseline reflection missing service. M4 baseline YAML false expectation remains missing. A provides exact Query/DTO/service and model validator signatures. M2 then genuinely rejects an expected typed success, M3 returns empty for a valid plain graph. Record four distinct assertion failures. A does not implement readiness/occupancy/validation/feature behavior. Environment, missing typed imports or fixture errors do not count.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | exact baseline, reviewed locks, pinned 8.0.0 | disjoint off-repo drafts and review | other writes to PS paths, database/runtime or activation | one source and transaction boundary |

### 4.4 Commit boundaries

Twelve exact PS paths: nine source/config/tests plus three docs. No generated archive, Maven/build/tooling, DDL, native engine verification runtime or user grant. One logical commit.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| owner and START | Keep §7.0 | TianjiProcessAccess, readStartByInstance | reuse dual current guards | existing IAM only | Already exists |
| model/release proof | Add §7.3 | manifestHash, VerifiedProcessModels and safe parser | current Control cannot verify target or graph | two bounded models | Implement |
| native read gate | Add §7.3.4 | current root and native scoped SELECT | raw validation has no owner/occupancy | scoped read only | Implement |
| internal query | Add §9 | current native single validation | no direct wire or fallback | one local Query | Implement |
| write/public APIs | Remove §3.2 | immutable releases | local report | none | Already excluded |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| release and graph | REQ-002/003 | M3 | release and validator | pure plain shape, exact legacy digest | service | Step 1 |
| native scope | REQ-003/004 | M2/G1 | PS native helper, pinned JDBC | bounded snapshot facts | service | Step 1 |
| owner + native validate | REQ-001/005 | M1/M2 | existing access and all guards | closed result | factory | Step 1 |
| default off/proof | REQ-001/006 | M4/P1 | existing PS config and permission producer | safe default/artifact evidence | docs | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | current technical PS JDBC/Flowable | Spec §6.1 | existing packages and source | Step 1 MC-ARCH-001 |
| Reuse/capability | current verifier, XML, native helper | Spec §7 | reuse exact source and functions | Step 1 MC-REUSE-001 |
| Naming/model/validation/conversion | current C validators/immutable values | Spec §9/10 | semantic nested records and safe JDBC technical mapping | Step 1 MC-VALID-001 |
| Bean/logging/JSON/time/config | existing named factory and fixed technical errors | Spec §6/8 | no endpoint/log growth, explicit UTC and default false | Step 1 MC-BEAN-001/TIME-001 |
| Business variation/pattern | Worker Specification and current read query | Spec §13 | exact pure graph and occupancy, no extra layer | Step 1 MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| identity | TianjiProcessAccess | GET_API and RELEASE_GET_API | fit | reuse | None | Step 1 MC-REUSE-001 |
| graph/release | SafeModelXml, ProcessReleaseService, VerifiedProcessModels | bounded immutable owner model | timer-only gap | extend existing owners | no dependency | Step 1 MC-VALID-001 |
| readiness | ProcessApiSchemaVerifier | verifyAgentResultInbox and hasCaseMigration | same known history | reuse plus exact V10 presence | no DDL | Step 1 MC-ARCH-001 |
| native read | ProcessControlNativeAccess | exact owner root/primary | bounded timer facts absent | scoped SELECT and SET | no dependency | Step 1 MC-CONVERT-001 |
| native structural | ProcessMigrationService 8.0.0 | exact dedicated command | fit | one mapping and validation | no dependency | Step 1 MC-PATTERN-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/8 | PS immutable technical values | Files 4–8 | semantic Query/DTO/VO | G1 | Step 1 | PASS |
| Rule 2 | §6.2/9 | existing C validators | Files 1/4/7 | exact validated input and owner | M2/G1 | Step 1 | PASS |
| Rule 3 | §6.2/10 | existing immutable records | Files 4/6/7 | records only for value carriers, scalar JDBC | G1 | Step 1 | PASS |
| Rule 4 | §6.2/8 | PS named config | Files 7/8 | exact named qualified factory, no new logger | G1 | Step 1 | PASS |
| Rule 5 | §6.2 | existing JDK/JDBC/XML | Files 4–8 | reuse current helpers | G1 | Step 1 | PASS |
| Rule 6 | §6.2/9 | existing wire unchanged | Files 6–8 | strict pinned native CFG, no new JSON serializer | G1/P1 | Step 1 | PASS |
| Rule 7 | §6.2/16 | current PS YAML | Files 3/8/9 | explicit false and existing profile keys | M4/P1 | Step 1 | PASS |
| Rule 9 | §6.2/13 | technical Specification | Files 4/6/7 | pure closed graph, direct Query | M3/G1 | Step 1 | PASS |
| Rule 10 | §6.2/6.3 | Instant and Duration | Files 4/6/7 | fixed UTC and native conversion | G1, I later | Step 1 | PASS |
| Rule 11 | §6.1/8/11 | existing PS owner | all paths | no new layer/store/migration/command | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: no calendar | Not applicable: no UI | one primary observation | existing DB precision | no wall-clock fallback | Step 1 TEST-003/TEST-005 |
| TIME-002 | native DUEDATE_ | Occurred instant | PostgreSQL timestamp without time zone | Legacy UTC | Internal | reviewed explicit native writer/reader UTC | Not applicable: no UI | due strictly after sample | native precision | UTC conversion only with proven mapping | Step 1 TEST-003/TEST-005 |
| TIME-003 | fixed BPMN timer duration | Duration | Duration | Not persisted | Internal | Not applicable: fixed elapsed | Not applicable: no UI | equal canonical whole seconds | existing validator 1s..365d | no model byte change | Step 1 TEST-002 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java` | Current ProcessWorkerRecoveryServiceTest and ProcessControlNativeAccessTest plus pinned Flowable mock APIs. | migration_preflight_entry_exists, validates_exact_owned_plain_timer_once, denies_foreign_or_unsafe_evidence, binds_physical_read_only_and_catalog. | Prove exact owner-local success/failure with finite tests. | Step 1 | REQ-001–006 | M1/M2 assertion RED and G1 service/SQL matrix. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java` | Existing validateBpmn fixtures and released_timer_wait_metadata_covers_every_supported_timer_without_changing_older_models. | derives_exact_plain_migration_timer, rejects_nonplain_migration_timer_models. | Lock the narrow graph and timer domain independently. | Step 1 | REQ-001–006 | M3 genuine assertion failure then G1. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java` | Current YAML/Worker recovery finite condition tests and private nested configuration. | migration_preflight_defaults_off, migration_preflight_requires_enabled_servlet_api. | Prove default false, conditions and limited factory. | Step 1 | REQ-001–006 | M4 missing false then G1/P1. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java` | SafeModelXml.parse(bytes,true), children/required helpers and current timerWaits metadata. | Optional<PlainMigrationTimerVO> plainMigrationTimer(ValidatedProcessModel), nested immutable PlainMigrationTimerVO(String activityId, Duration duration). | Derive exact timer-only topology from verified bytes. | Step 1 | REQ-001–006 | M3/G1 model coverage and old validator regression. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java` | Existing process/jdbc/read manager, ModelResource and package-scope manifestHash. | boolean usesStorage(SpringProcessEngineConfiguration,DataSource,DataSourceTransactionManager), boolean verifiesSingleBpmnManifest(Release,Definition,ValidatedProcessModel). | Verify same-source release and reuse exact legacy manifest. | Step 1 | REQ-001–006 | G1 owner/manifest tests and old release artifact regression. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java` | Existing requireTransaction/readRecoveryRoot and pinned ACT DDL/job mappings. | Instant beginMigrationObservation(), void requireMigrationReadOnly(), boolean hasExactPlainTimer(TaskOrigin,LockedRoot,String,Instant), void verifyMigrationReadCoverage(). | Prove physically read-only owner and exact native/owner occupancy. | Step 1 | REQ-001–006 | G1 finite real JdbcTemplate mock SQL and future I proof. |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java` | ProcessWorkerRecoveryService exact source/guard patterns, C query validators, pinned Flowable native implementation. | public inspect(ValidateProcessMigrationQuery), nested ProcessMigrationPreflightDTO/status and query, public verifyReadiness(). | Implement the exact internal Query and native call. | Step 1 | REQ-001–006 | M1/M2 before B and G1 exact calls; no runtime I. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | Outer servlet config, processConfiguration/caseDataSource/decisionConfiguration and existing Recovery factory. | MigrationPreflightConfiguration, processMigrationPreflightService named bean. | Bind disabled-by-default exact technical service. | Step 1 | REQ-001–006 | G1 finite config and P1 same PaC policy. |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Current PS default-false cancellation/recovery in one base YAML. | stellar.tianji.process.migration-preflight.enabled. | Package independent explicit false default. | Step 1 | REQ-001–006 | M4 and P1. |
| CREATE | `docs/egon/spec/2026-11-05-18-46-bpmn-migration-preflight.md` | Exact approved off-repo bytes and EGON docs convention. | Reviewed Spec and REQ-001–006. | Persist locked source authority. | Step 1 | REQ-001–006 | S1 strict Spec. |
| CREATE | `docs/egon/plan/2026-11-05-18-53-bpmn-migration-preflight.md` | Current EGON Plan convention and locked outside-repo candidate. | Reviewed Plan M1–S1 and twelve paths. | Persist exact one-step execution scope. | Step 1 | REQ-001–006 | S1 strict Plan and exact paths. |
| CREATE | `docs/egon/evidence/2026-11-05-bpmn-migration-preflight.md` | Current PS technical evidence style. | M1–M4, G1, P1, S1 and open actual I. | Record actual finite/static facts and separate I. | Step 1 | REQ-001–006 | S1 final evidence. |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact clean PS baseline above. Root locks all existing main SQL, C/CL/AS/AR and PS producer evidence before edits. No changes to Web. Reuse exact Flowable JAR and digest from Spec EVD-004.

### 6.2 Build, test, and environment prerequisites

Explicit JDK25, offline settings and finite source-reactor selectors. Existing permission-producer profile for static JAR proof only. No Boot, Engine, PG or integration test. Actual I and future enablement separately controlled.

### 6.3 Immutable constraints and approved decisions

Only existing dual resources and exact START. No public transport, IAM/grant, authority, DML or state-changing native path. Feature remains false by default. Unreadable source or unverified native deployment fails closed.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | Reuse current V1–V6 verifier and exact V8–V10 prefix plus known V10 presence | ProcessApiSchemaVerifier existing verified reads | no duplicate CRC or recovery write privilege | fail closed at readiness |
| PLAN-CLAR-002 | Service and validator nested immutable carrier types | current PS technical value conventions | no new public wire | freeze exact signature before A |
| PLAN-CLAR-003 | Native minimal derived booleans and exact scoped EXISTS | existing PS JDBC and pinned ACT mappings | no raw variable/job payload or broad listing | prove index/role bounds in I |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Validate one plain-timer BPMN pair inside the owning Process Server

- Requirements: Primary REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006. Existing Process Control requirements REQ-501, REQ-502, REQ-503, REQ-504, REQ-505, REQ-506, REQ-507, REQ-508, REQ-509, REQ-510, REQ-511, REQ-512, REQ-513, REQ-514, REQ-515 are preserved boundaries only.
- Dependencies: exact reviewed Spec/Plan, clean PS baseline, pinned Flowable 8.0.0, existing migrations and current dual resources.
- Baseline state: START/release and current Control available, no new internal diagnostic bean.
- Observable outcome: trusted owner can inspect one current structural result after independent enablement; source is packaged off.
- End state: twelve exact PS paths in one commit, finite proof only, actual isolated I pending.
- Test-first gate: Required. M1 missing method, M2 typed service behavior, M3 typed graph and M4 explicit false assertion.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java`

- Purpose: Prove exact owner-local success/failure with finite tests.
- Symbols: migration_preflight_entry_exists, validates_exact_owned_plain_timer_once, denies_foreign_or_unsafe_evidence, binds_physical_read_only_and_catalog.
- Repository evidence: Current ProcessWorkerRecoveryServiceTest and ProcessControlNativeAccessTest plus pinned Flowable mock APIs.
- Dependencies and consumers: A types, finite DataSource and Flowable mocks.
- Why now: M1 tests missing service first, M2 uses compiling A for genuine behavior failure.
- Contract/signature changes: Reflection then typed immutable DTO and capture-only mocks.
- Input/output and state mapping: Exact current dual resource order, owner-first, same native ID and one mapping. Capture actual SQL/scalars through mocked JDBC.
- Error and edge behavior: Foreign, ambiguous source, wrong role, due equality, lock, V4/5/6/8/10 presence, engine rejected/null/throw, forbidden calls and raw output.
- Standards impact: MC-TEST-001, MC-VALID-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 10, Rule 11. Immutable technical type, current owner and time proof.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName(PACKAGE + ".ProcessMigrationPreflightService"));
assertEquals(expectedValidated, assertDoesNotThrow(() -> service.inspect(verifiedQuery)));
verify only source actor exact guards, one physical read-only snapshot and one native call;
capture prepared SQL and exact source/target/child/timer IDs inside finite JDBC fixture;
exercise null/foreign/mixed/timeout/unsafe rows and assert never native validation.
```

- Verification contribution: M1/M2 assertion RED and G1 service/SQL matrix.
- After this file: Compile-only stubs can prove missing real behavior.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`

- Purpose: Lock the narrow graph and timer domain independently.
- Symbols: derives_exact_plain_migration_timer, rejects_nonplain_migration_timer_models.
- Repository evidence: Existing validateBpmn fixtures and released_timer_wait_metadata_covers_every_supported_timer_without_changing_older_models.
- Dependencies and consumers: Current validator and A nested PlainMigrationTimerVO.
- Why now: M3 before model behavior.
- Contract/signature changes: New tests only, use small inline XML within existing test.
- Input/output and state mapping: Start→wait→end with PT1S, exact two flows and namespaces returns Optional plain VO. Existing validated broader models still validate normally.
- Error and edge behavior: Reject parallel, message, extension, cycle/date/fraction, extra links/nodes and untrusted shape.
- Standards impact: MC-VALID-001, MC-TIME-001, MC-PATTERN-001.
- Literal rule enforcement: Rule 2, Rule 9, Rule 10, Rule 11. Pure same owner shape and Duration.
- Implementation pseudocode:

```java
var model = validator.validateBpmn(plainXmlBytes, "migrationTimer");
assertEquals(new PlainMigrationTimerVO("wait", Duration.ofSeconds(1)),
        validator.plainMigrationTimer(model).orElse(null));
assertTrue(validator.plainMigrationTimer(validBroaderGraph).isEmpty());
retain existing validator source tests and assert prior model metadata unchanged.
```

- Verification contribution: M3 genuine assertion failure then G1.
- After this file: No native engine required for exact graph proof.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`

- Purpose: Prove default false, conditions and limited factory.
- Symbols: migration_preflight_defaults_off, migration_preflight_requires_enabled_servlet_api.
- Repository evidence: Current YAML/Worker recovery finite condition tests and private nested configuration.
- Dependencies and consumers: Existing application YAML and B named condition factory.
- Why now: M4 before config edits.
- Contract/signature changes: Test explicit default and reflection/finite condition wiring.
- Input/output and state mapping: Absent base mapping returns null via getOrDefault, then explicit false assert. Servlet+API+preflight required. Inspect expected factory bean.
- Error and edge behavior: No scope/permission mapping or controller added. No default runtime path.
- Standards impact: MC-CONFIG-001, MC-BEAN-001, MC-TEST-001.
- Literal rule enforcement: Rule 4, Rule 7, Rule 11. Same config profile and safe default.
- Implementation pseudocode:

```java
var yaml = new YamlMapFactoryBean(); load application.yml;
var process = section(yaml.getObject(), "stellar", "tianji", "process");
assertEquals(Boolean.FALSE, safeLookup(process, "migration-preflight", "enabled"));
assert exact servlet/API/flag conditions and named bean from nested configuration;
verify no permission scope changes or new endpoint behavior.
```

- Verification contribution: M4 missing false then G1/P1.
- After this file: Explicit false test is independent of bean class linkage.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`

- Purpose: Derive exact timer-only topology from verified bytes.
- Symbols: Optional<PlainMigrationTimerVO> plainMigrationTimer(ValidatedProcessModel), nested immutable PlainMigrationTimerVO(String activityId, Duration duration).
- Repository evidence: SafeModelXml.parse(bytes,true), children/required helpers and current timerWaits metadata.
- Dependencies and consumers: Existing VerifiedProcessModels and File 7.
- Why now: A typed empty Optional for M3, B after RED.
- Contract/signature changes: Add pure method only, keep validateBpmn results unchanged.
- Input/output and state mapping: Check BPMN, one executable process, exact Start→timer→End graph, two plain edges and same derived single canonical whole-second timer.
- Error and edge behavior: No scripts, tasks, extra namespaces/children, event extensions, variables/date/cycle/calendar or broadened model validator.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-PATTERN-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11. Immutable pure technical model value.
- Implementation pseudocode:

```java
// A returns Optional.empty() for compile only.
Document modelDocument = SafeModelXml.parse(model.xml(), true);
accept exact definitions/process child hierarchy, three nodes and two unconditioned sequence flows;
require a single intermediateCatchEvent timer and canonical whole-second Duration literal;
compare timer metadata with existing model.timerWaits, return verified PlainMigrationTimerVO;
unsupported valid shapes return empty, invalid data never becomes eligible.
```

- Verification contribution: M3/G1 model coverage and old validator regression.
- After this file: Service receives one closed timer shape.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`

- Purpose: Verify same-source release and reuse exact legacy manifest.
- Symbols: boolean usesStorage(SpringProcessEngineConfiguration,DataSource,DataSourceTransactionManager), boolean verifiesSingleBpmnManifest(Release,Definition,ValidatedProcessModel).
- Repository evidence: Existing process/jdbc/read manager, ModelResource and package-scope manifestHash.
- Dependencies and consumers: File 7 query only; no changes to existing callers.
- Why now: Release integrity prerequisite before inspecting native facts.
- Contract/signature changes: Two narrow local read validations, no changes to publish/get signatures.
- Input/output and state mapping: Compare engine/source/transaction manager identities and exact binding. Recompute single BPMN legacy digest over verified bytes and stored empty schema.
- Error and edge behavior: Reject duplicate/nonmatching definition, invalid hash or source affinity. Never republish or reencode historical bytes.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001.
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 11. Preserve exact released bytes and owner.
- Implementation pseudocode:

```java
usesStorage(...) requires this.process == engine and jdbc.getDataSource() == source
    and exact read/write template manager and configured schema;
verifiesSingleBpmnManifest(...) requires one exact BPMN definition and empty inputs,
    model logical key and type match, and manifestHash(List.of(new ModelResource(...)))
    exactly equals the stored Release.manifestSha256(); keep original publish unchanged.
```

- Verification contribution: G1 owner/manifest tests and old release artifact regression.
- After this file: No mutable release behavior introduced.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`

- Purpose: Prove physically read-only owner and exact native/owner occupancy.
- Symbols: Instant beginMigrationObservation(), void requireMigrationReadOnly(), boolean hasExactPlainTimer(TaskOrigin,LockedRoot,String,Instant), void verifyMigrationReadCoverage().
- Repository evidence: Existing requireTransaction/readRecoveryRoot and pinned ACT DDL/job mappings.
- Dependencies and consumers: File 7 same owner; current helpers remain.
- Why now: After model/release gates, before orchestration.
- Contract/signature changes: Package-local bounded technical read methods, fixed query strings and safe Boolean projection.
- Input/output and state mapping: One pg clock, exact native root/leaf with limit3 and timer with limit2, zero other fixed ACT and linked rows. Due UTC predicate; exact pinned handler cfg.
- Error and edge behavior: Wrong/unknown storage, role, null clock, misplaced rows and unsafe flags fail closed. No payload/raw IDs returned from this layer to API.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-CONVERT-001, MC-TIME-001.
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11. Minimized same-source read.
- Implementation pseudocode:

```java
beginMigrationObservation verifies bound owning transaction before SET TRANSACTION READ ONLY;
set local statement_timeout 2s, then require primary/UTF8/origin/schema/read-only/RR/UTC;
sample clock_timestamp once and reject null, verify fixed ordinary relation SELECT/catalog coverage;
read ACT_RU_EXECUTION exact proc_inst_id or root_proc_inst_id LIMIT 3, require root and direct leaf;
read ACT_RU_TIMER_JOB exact process/leaf LIMIT 2; match tenant/definition/activity/handler and exact future due;
fixed SELECT EXISTS for all other ACT and V4/V5/V6/V8/V10 scoped sources;
unknown/inconsistent evidence fails unavailable, supported nonmatch returns false, no write or lock.
```

- Verification contribution: G1 finite real JdbcTemplate mock SQL and future I proof.
- After this file: Only bounded local evidence, no engine validation yet.

#### File 7 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java`

- Purpose: Implement the exact internal Query and native call.
- Symbols: public inspect(ValidateProcessMigrationQuery), nested ProcessMigrationPreflightDTO/status and query, public verifyReadiness().
- Repository evidence: ProcessWorkerRecoveryService exact source/guard patterns, C query validators, pinned Flowable native implementation.
- Dependencies and consumers: Existing access, exact Spring engine/DMN config, current releases/model validator and existing helpers.
- Why now: A signature only for M2. B after source proof helpers.
- Contract/signature changes: One internal named service, no actor parameter, no external transport.
- Input/output and state mapping: Authorize both current keys same verified actor, one owned read, resolve source/target exact manifest, compare plain shape, observe native/attached facts, call only exact builder validation.
- Error and edge behavior: Missing exact owner fixed not-found before target. Known negatives mapped to closed status, uncertain data/engine to fixed unavailable. Recheck transaction after native call.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11. Preserve exact ownership and fixed failure.
- Implementation pseudocode:

```java
public ProcessMigrationPreflightDTO inspect(ValidateProcessMigrationQuery query) {
  require nonambient/current GET_API and RELEASE_GET_API actor equality;
  return ownedRepeatableRead.execute(status -> {
    var now = nativeAccess.beginMigrationObservation(); read exact START and root;
    load both explicit releases and exact legacy hashes; require plain timer/same shape and evidence;
    var nativeResult = process.getProcessMigrationService().createProcessInstanceMigrationBuilder()
      .migrateToProcessDefinition(exactTargetNativeId)
      .addActivityMigrationMapping(ActivityMigrationMapping.createMappingFor(timerId, timerId))
      .validateMigration(instanceId);
    nativeAccess.requireMigrationReadOnly(); return safe closed result from isMigrationValid only;
  });
}
verifyReadiness uses same physically guarded snapshot, existing verifier.verifyAgentResultInbox(),
verifier.hasCaseMigration(), known V10 presence, exact projected catalog and pinned default config.
```

- Verification contribution: M1/M2 before B and G1 exact calls; no runtime I.
- After this file: Internal behavior available for conditional factory only.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Bind disabled-by-default exact technical service.
- Symbols: MigrationPreflightConfiguration, processMigrationPreflightService named bean.
- Repository evidence: Outer servlet config, processConfiguration/caseDataSource/decisionConfiguration and existing Recovery factory.
- Dependencies and consumers: Existing ProcessEngine/DmnEngine and named validator, release and TianjiProcessAccess.
- Why now: Wire only after service exists.
- Contract/signature changes: New nested non-proxy config requires servlet, api.enabled and migration-preflight.enabled true.
- Input/output and state mapping: Select same owning engine/source/manager, existing resource collaborators. Call new service.verifyReadiness on explicit enablement.
- Error and edge behavior: Off does zero readiness and creates no bean; source or approval mismatch fails closed, no HTTP/IAM change.
- Standards impact: MC-ARCH-001, MC-BEAN-001, MC-CONFIG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 4, Rule 7, Rule 11. Named qualified same-source bean, no new endpoint.
- Implementation pseudocode:

```java
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.process",
    name = {"api.enabled", "migration-preflight.enabled"}, havingValue = "true")
@Bean("processMigrationPreflightService") create using same ProcessEngine and qualified existing collaborators;
assert exact engine/source/manager, call service.verifyReadiness and return;
no change to existing filter chain, permissions, routes or worker/cancel beans.
```

- Verification contribution: G1 finite config and P1 same PaC policy.
- After this file: No capability is active by default.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Package independent explicit false default.
- Symbols: stellar.tianji.process.migration-preflight.enabled.
- Repository evidence: Current PS default-false cancellation/recovery in one base YAML.
- Dependencies and consumers: File 8 exact property name.
- Why now: After M4 and factory.
- Contract/signature changes: One Boolean setting, no other profile or defaults.
- Input/output and state mapping: New nested key false, existing flags/scopes values stay exact.
- Error and edge behavior: No runtime enable or maintenance action configuration.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 11. Existing profile key structure extends only one false.
- Implementation pseudocode:

```java
under stellar.tianji.process add migration-preflight.enabled: false;
strictly compare semantic YAML paths before/after, every existing value remains identical;
in packaged BOOT-INF/classes/application.yml require the exact source bytes and explicit false.
```

- Verification contribution: M4 and P1.
- After this file: Explicit default false is present in source and archive.

#### File 10 — `CREATE docs/egon/spec/2026-11-05-18-46-bpmn-migration-preflight.md`

- Purpose: Persist locked source authority.
- Symbols: Reviewed Spec and REQ-001–006.
- Repository evidence: Exact approved off-repo bytes and EGON docs convention.
- Dependencies and consumers: Root approved hash and Plan.
- Why now: Canonical docs before final strict review.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact committed link and semantics, no surprise amendment.
- Error and edge behavior: Mismatched source hash or scope blocks.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 locks current PS owner and no external surface.
- Implementation pseudocode:

```text
verify the exact reviewed primary Spec hash and immutable source baseline;
create canonical Spec from the reviewed bytes at the locked repository path;
run strict validation with canonical predecessor and roadmap links.
```

- Verification contribution: S1 strict Spec.
- After this file: Canonical source scope present.

#### File 11 — `CREATE docs/egon/plan/2026-11-05-18-53-bpmn-migration-preflight.md`

- Purpose: Persist exact one-step execution scope.
- Symbols: Reviewed Plan M1–S1 and twelve paths.
- Repository evidence: Current EGON Plan convention and locked outside-repo candidate.
- Dependencies and consumers: Exact primary Spec and reviewer lock.
- Why now: Final path before evidence and commit.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact reviewed bytes link to governing Spec and existing authority.
- Error and edge behavior: No implementation-time expansion or unlocked metadata edits.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 records current dependency order and technical scope.
- Implementation pseudocode:

```text
verify Root-reviewed Plan hash and exact primary Spec relation;
create canonical Plan from the locked bytes and run both strict validators;
compare the stage path allowlist against all declared Step paths before commit.
```

- Verification contribution: S1 strict Plan and exact paths.
- After this file: Canonical Plan and Spec are present.

#### File 12 — `CREATE docs/egon/evidence/2026-11-05-bpmn-migration-preflight.md`

- Purpose: Record actual finite/static facts and separate I.
- Symbols: M1–M4, G1, P1, S1 and open actual I.
- Repository evidence: Current PS technical evidence style.
- Dependencies and consumers: Actual fresh reports and actual packaged archive.
- Why now: After verification, before one commit.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Record exact selectors, reports, YAML and manifest proof, SQL hashes and scope.
- Error and edge behavior: Any skipped/failed proof or runtime inference blocks completion.
- Standards impact: MC-TEST-001, MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 records source only and separate owner/runtime.
- Implementation pseudocode:

```text
record actual four RED assertion causes and fresh focused/package finite results;
record JAR and manifest/YAML bytes, exact scope, no execution route and all SQL unchanged;
retain I/UTC/native 0-DML, authorized caller, activation/migration and live E as independently pending.
```

- Verification contribution: S1 final evidence.
- After this file: Close only when actual finite/static gates and twelve exact paths pass.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 M1–M4, G1, P1, S1.
- Expected result: Four exact real assertion REDs, finite GREEN with no skip, actual exec JAR with false default and unchanged permission policy. No actual I.
- Failure returns to: Files 1–3 for test setup or source invariants, Files 4–7 for scope/validation, Files 8–9 for config and authority mismatch. Material policy change returns to Spec.
- Completion criteria: Actual proof and exact staged paths, old SQL/source contracts preserved.
- Rollback: One PS-only commit revert, no data/migration rollback.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/spec/2026-11-05-18-46-bpmn-migration-preflight.md`, `docs/egon/plan/2026-11-05-18-53-bpmn-migration-preflight.md`, `docs/egon/evidence/2026-11-05-bpmn-migration-preflight.md`.
- Commit: `feat(process): validate owned BPMN migration structure`.

## 8. Test, Validation, and Quality Gates

All source commands run only after exact reviewed authorization and the source lock. Stop if owning PS report is missing, compilation/fixture/environment errors occur, or a listener/engine test enters the selector. The finite source wrapper and current dependency cache are the same as A2.

```bash
migration_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
migration_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
migration_finite() {
  JAVA_HOME="$migration_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$migration_settings" -s "$migration_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
migration_finite 'ProcessMigrationPreflightServiceTest#migration_preflight_entry_exists'
migration_finite 'ProcessMigrationPreflightServiceTest#validates_exact_owned_plain_timer_once'
migration_finite 'ProcessModelValidatorTest#derives_exact_plain_migration_timer'
migration_finite 'ProcessStorageConfigurationTest#migration_preflight_defaults_off'
migration_finite ProcessMigrationPreflightServiceTest,ProcessModelValidatorTest,ProcessStorageConfigurationTest,ProcessReleaseServiceTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessWorkerRecoveryServiceTest,PlatformReleaseArtifactCandidateTest,PlatformReleaseSignatureVerifierTest
```

| Gate | Execution order / exact expectation | Failure return |
| --- | --- | --- |
| M1 | File 1 reflection-only source; one assertion fails because the class is absent | existing baseline and reflection fixture |
| M2 | Files 1/7 A typed, one assertion fails because inert service does not return expected current success | File 1/7, no B yet |
| M3 | Files 2/4 A typed, one assertion fails because supported plain timer derives no facts | File 2/4 |
| M4 | File 3 YAML explicit-false assertion, one failure because flag is absent, not a null dereference | File 3/9 |
| G1 | after B, all named PS reports fresh and nonzero actual tests, zero fail/error/skip | relevant File 1–9 |
| P1 | actual packaged artifact and selected finite permission regression, verify off/source/PaC and unaffected SQL | Files 8–9 or package proof |
| S1 | exact approved docs, 12 staged paths, clean after commit, actual I pending | exact owned source or scope |

P1 runs the existing static profile. The committed baseline provided by Root may advance only through an explicit fresh exact lock, never an implicit branch drift. Do not start the archive.

```bash
migration_base=$(git rev-parse --verify HEAD)
test "$migration_base" = 695c9115921ba3068db445526b7bf51be87a350b
JAVA_HOME="$migration_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$migration_settings" -s "$migration_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$migration_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-01b0-20261105-step1 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=ProcessMigrationPreflightServiceTest,ProcessModelValidatorTest,ProcessStorageConfigurationTest,ProcessReleaseServiceTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessWorkerRecoveryServiceTest,ProcessPermissionIntegrationTest,PlatformReleaseArtifactCandidateTest,PlatformReleaseSignatureVerifierTest
```

Inspect actual `stellar-enclosure-tianji-process-server/target/stellar-enclosure-tianji-process-server-exec.jar` with Python zipfile/unzip. Require exactly one `BOOT-INF/classes/application.yml` matching source bytes, exact explicit false, new service class and no new Controller/HTTP mapping. Require unique existing root `META-INF/permissions/manifest-v1.json`. Compare decoded schemaVersion/application/producer and exact resources/permissionDefinitions/permissionReferences/resourceReferences/messages against retained current PS manifest authority. Allow only build artifact metadata differences outside those fields. Confirm archive SHA via existing producer evidence and source SQL hashes from frozen baseline. Retain only private off-repo proof/logs. No new harness file, because existing verifier/profile and inline official artifact inspection cover each gate.

S1 executes existing strict Spec/Plan validators at the reviewed paths and `git diff --check`. Stage precisely §7 Commit paths. Compare against `git diff --cached --name-only` and check clean excluded paths and all old SQL bytes. After one approved commit, verify its exact parent and file set.

### Independent I, intentionally not selected

An authorized operator uses exact disposable PS/Flowable8/PostgreSQL and preprovisioned existing migrations/role, capture only statements while the diagnostic runs, and produce before/after START, owner receipt, execution/timer ID, revision and due witness. The isolated setup must publish and start the two exact timer model releases separately, not as part of this diagnostic. Scheduler is controlled while proving no DML. Include an independently observed timer race, wrong owner/target/hash, due equality, unsafe shape, wrong source, UTC provenance and no mutation. Verify same physical connection/transaction under the actual Spring REQUIRED interceptor and preserve fixed safe outcomes. Confirm plan cost in actual supported data volume. No test or profile in this source Step starts that environment or flips enablement.

## 9. Migration, Compatibility, Rollout, and Rollback

No DDL, schema edit, handler/route, permissions, token, scheduler or live change. Source-only PS commit and explicitly disabled behavior. No enablement until actual I/UTC, acceptable role/source/plan coverage and sanctioned internal caller are proven. A later command needs independent governance and reauthorization. Revert only source or keep flag off. Existing state, cancellation, Worker and release remain their current behavior.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | §4/7/9 | Step 1 | 1, 3, 7–9 | M1/M4/G1/P1 | existing dual resources and packaged off |
| REQ-002 | §4/7/10 | Step 1 | 1, 4, 5, 7 | M2/G1 | exact release and native identity |
| REQ-003 | §4/7.3 | Step 1 | 1/2/4/6/7 | M3/G1 | plain timer and exact evidence |
| REQ-004 | §4/6.3/7 | Step 1 | 1/6/7 | G1 plus later I | repeatable physical read-only and UTC |
| REQ-005 | §4/9/10 | Step 1 | 1/7 | M2/G1 | exact native validation only, safe report |
| REQ-006 | §4/14/16 | Step 1 | 1–12 | M1–P1/S1 and I later | finite/package source proof separate from runtime |
| REQ-501, REQ-502, REQ-503, REQ-512, REQ-513, REQ-515 | Existing Process Control compatibility | Step 1 preserved only | no old public behavior | G1/P1/S1 | old read, public contract, permission and UTC unchanged |
| REQ-504, REQ-505, REQ-506, REQ-507, REQ-508, REQ-509, REQ-510, REQ-511, REQ-514 | Existing Process Control writes/native/schema/readiness and runtime boundary | Step 1 preserved only | no implementation of old writes | G1/P1/S1 | old cancel/receipts/fencing/bridge/SQL and acceptance boundary unchanged |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | exact internal source-only diagnostic independently of live governance | Step 1 | Root scope and Spec §5 | Root | closed for design review |
| RISK-001 | physically joined native Flowable behavior and timer write-time timezone | File 6/7 and I | pinned static source only | Operator | default off until actual I |
| RISK-002 | optional absence proofs or V4/V10 scope performance unavailable | File 6/7 and I | current index inventory | Operator | fail closed, no permissions/index expansion |
| RISK-003 | default engine or binding drift | Files 6–8 | exact owner and 8.0.0 | Root | stop and review |
| RISK-004 | source success mistaken for authority | docs/report/operation | source guards only | Root | no activation/recovery/live proof |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Only exact owner-local diagnostic for one currently valid existing pair. No public exposure, write or actual eligibility. Scope narrow and default off.

### 12.2 Spec consistency

Existing ProcessDB/engine and exact native validation reused. Simple plain timer is admitted by independent Specification. No target rewrite, table or deployment behavior.

### 12.3 Repository executability

One cohesive PS Step, explicit A/RED/B, fixed offline JDK and finite selectors. Exact paths and source/authority locks. No runtime target executed during planning.

### 12.4 Test and release completeness

Four genuine REDs, finite GREEN, actual packaged default and PaC comparison, strict exact stage and separate isolated I. No source proof claimed before it occurs.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/5 | existing PS owning technical JDBC/Flowable | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 and Files 4–8 | existing access/model/readiness/native | None |
| MC-DEP-001 | Applicable | PASS | §5/P1 | no new POM/tool/dependency | None |
| MC-NAME-001 | Applicable | PASS | Files 4/6/7 | semantic immutable Query/DTO/VO | None |
| MC-VALID-001 | Applicable | PASS | Files 1/2/4/6/7 | exact input, roles, storage, model and facts | None |
| MC-MODEL-001 | Applicable | PASS | Files 4/6/7 | immutable values | None |
| MC-CONVERT-001 | Applicable | PASS | File 6 | narrow technical JDBC, no new business mapper | None |
| MC-LOG-001 | Not applicable | N/A | Files 6/7 | no new business logs or raw diagnostics | None |
| MC-BEAN-001 | Applicable | PASS | Files 7/8 | named explicit same-source wiring | None |
| MC-UTIL-001 | Applicable | PASS | Files 4/6/7 | existing JDK/Spring/XML/Flowable helpers | None |
| MC-JSON-001 | Not applicable | N/A | §5/9 | no external serializer/contract changes | None |
| MC-TIME-001 | Applicable | PASS | §4.9/G1/I | exact DB instant, explicit UTC and duration | None |
| MC-CONFIG-001 | Applicable | PASS | Files 3/8/9/P1 | exact false and preserved existing config | None |
| MC-PATTERN-001 | Applicable | PASS | §4.7 Files 4/6/7 | direct Query + closed Specification | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7/S1 | twelve paths only, no runtime | None |
| MC-TEST-001 | Applicable | PASS | §8/10 | exact finite and package gates, I separate | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | review source with enablement gated | None |

### 12.6 Final verdict

PASS — Ready for user review

This verdict is planning only. Actual source execution and real I are separately tracked.
