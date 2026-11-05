# T4-02 人工任务完成时长实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-22-32-human-task-completion-duration.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 22:32 Asia/Shanghai |
| Updated | 2026-11-05 22:32 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | PS Human Task COMPLETE duration, seven code/config paths and three review/evidence paths |
| Source Requirement | 主计划 T4-02 任务 SLA 信号 |
| Baseline Revision | 7072e5b9153d1cda4ee38be60eeb9651b102a885 |
| Implements Spec | [Human Task completion duration](../spec/2026-11-05-22-31-human-task-completion-duration.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 22:31 Asia/Shanghai SHA-256 9dcda72f38cb9d1593117a6ba85356d7cdb9492d3902405e21a0bb5e8a757f5d |
| Effective Specs | [Human Task completion duration](../spec/2026-11-05-22-31-human-task-completion-duration.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 Step 在既有 PS 人工任务完成路径增加默认关闭的已提交 Timer。现有命令、存储、权限、Spring 事务和 MeterRegistry 构成实现边界。需待审阅字节和来源基线锁定。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Human Task completion duration](../spec/2026-11-05-22-31-human-task-completion-duration.md) 定义唯一新增本地指标。

### 2.2 Effective Spec set

REQ-001–005，METRIC-001，TIME-001–003。既有 Inbox 模式为技术证据，无规范改写。

### 2.3 Superseded or excluded content

既有任务命令、OpenAPI、权限、生产阈值、待办扫描和完整 S12 不进入此 Step。

## 3. Effective Requirements and Acceptance

| Requirement | Effect | Acceptance |
| --- | --- | --- |
| REQ-001 | Native creation to receipt duration | Fixed source time, zero/negative/overflow checked |
| REQ-002 | Fresh and committed COMPLETE | Real Spring transaction on mocked JDBC, replay/rollback exclusions |
| REQ-003 | Two fixed bounded dimensions | scope and due only, equality on time |
| REQ-004 | Default off and isolated failure | Exact old guards, no dependency or unexpected Registry failures |
| REQ-005 | Evidence boundary | Scope diff and finite proof; separate live acceptance |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Freeze reviewed pair and baseline. R1, typed A then R2, B behavior/configuration then G1 and static S1.

### 4.2 Test-first strategy

| Gate | State | Expected |
| --- | --- | --- |
| R1 | Existing task test adds only reflective positive assertion | One assertion failure |
| R2 | Test uses typed A observer no-op, original COMPLETE succeeds | One assertion failure on zero meters versus one |
| G1 | Complete B tests/production/PS YAML | Selected finite tests all pass |
| S1 | Exact staged source/doc paths and package invariants | Strict PASS and fixed owner diff |

### 4.3 Sequential and parallel boundaries

R1 precedes A. A includes package getters, constructor and hook. R2 precedes functional B. The config test and exact factory signature compile together in B. No parallel writes to the owner files.

### 4.4 Commit boundaries

One tianji-service commit after gates and review. Staged path list fixed in Step 1.

### 4.5 Spec Simplicity and Implementation-necessity Audit

One existing command and one optional local metric. Old Task, receipt, Spring synchronization and Registry suffice. No storage/read topology or control surface.

### 4.6 Change-unit Dependency Matrix

| Unit | Predecessor | Result |
| --- | --- | --- |
| R1 | Source lock | Test proves missing seam |
| A and R2 | R1 | Compiled expected behavior proves unimplemented sample |
| B/G1 | R2 | Exact process/case and measurement proof |
| S1 | G1 | Packaging and fixed scope evidence |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | Existing PS traditional task owner | §7/8 | Existing transaction and native authority | Step 1 MC-ARCH-001 |
| Reuse/dependency | PS Actuator, current Tx and native data | §6 | No new POM or service | Step 1 MC-REUSE-001/MC-DEP-001 |
| Model/validation | Immutable Task snapshot and receipt | §10 | Pass trusted scalar times and finite enum | Step 1 MC-VALID-001 |
| Log/Bean/config | Existing PS factory and fixed technical warning | §7/16 | One default false and lazy Registry | Step 1 MC-LOG-001/MC-CONFIG-001 |
| Time | Native task instants and PostgreSQL receipt clock | §6.3 | No new conversion or time source | Step 1 MC-TIME-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Time | Task snapshot and receipt | Existing create, due, applied Instant | Sufficient | Reuse | Two package getters | Step 1 MC-TIME-001 |
| Completion | Spring Tx | STATUS_COMMITTED | Sufficient | Reuse | One local callback | Step 1 MC-VALID-001 |
| Meter | PS Actuator and Inbox adapter | Existing Timer and Registry | Sufficient | Reuse | One fixed technical adapter | Step 1 MC-DEP-001 |

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
| Rule 1 | §6.2/§8 | Existing task package | Files 1–6 | Exact names | G1 | Step 1 | PASS |
| Rule 2 | §6.2/§7 | Native verified owner | Files 1–4 | Validate sample internals without changing input contract | G1 | Step 1 | PASS |
| Rule 3 | §6.2/§10 | Immutable snapshot | File 3 | Expose existing fields only | G1 | Step 1 | PASS |
| Rule 4 | §6.2/§8 | Current Bean and technical logging | Files 2/4/6 | Preserve ownership and fixed diagnostics | G1 | Step 1 | PASS |
| Rule 5 | §6.2 | JDK and current framework | Files 1–6 | Existing APIs | G1 | Step 1 | PASS |
| Rule 6 | §6.2/§10 | Public receipt unchanged | No affected JSON | No change | S1 | Step 1 | N/A |
| Rule 7 | §6.2/§16 | Single PS YAML | Files 5–7 | False by default | G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/§13 | Simple observation | Files 2/4 | Direct optional callback | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | Native and DB instants | Files 1–4 | No timezone or rounding | G1 | Step 1 | PASS |
| Rule 11 | §7/§11 | Existing command owner | All | No schema/API expansion | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | HumanTaskSnapshot.createdAt/dueAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute native times | Not applicable: no frontend change | Optional due, compare absolute instant | Existing native millis | No new mapping | Step 1 G1 TEST-001/002 |
| TIME-002 | HumanTaskActionReceiptDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: DB receipt time | Not applicable: no frontend change | Commit is eligibility only | Existing clock_timestamp and Timestamp.toInstant | Receipt unchanged | Step 1 G1 TEST-001/003 |
| TIME-003 | Completion duration | Duration | java.time.Duration | Not persisted | Internal | Not applicable: duration | Not applicable: backend base unit | Negative/overflow omitted, zero accepted | Preserve captured precision | New Timer | Step 1 G1 TEST-001/002 |

## 5. Change File Tree

| Action | Exact path | Purpose | Order |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java` | Validate the exact COMPLETE and post-commit samples. | Step 1, File 1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskCompletionObservation.java` | Contain best-effort metrics and its commit callback. | Step 1, File 2 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java` | Expose existing native-lock snapshot instants to its package. | Step 1, File 3 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java` | Hook only a fresh COMPLETE after existing receipt application. | Step 1, File 4 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java` | Prove safe opt-in with no alternate bootstrap. | Step 1, File 5 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | Use the existing task service bean factory for optional Registry. | Step 1, File 6 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Keep new feature disabled in distributed defaults. | Step 1, File 7 |
| CREATE | `docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md` | Freeze reviewed scope and exact metric semantics. | Step 1, File 8 |
| CREATE | `docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md` | Pin source order, exact scopes and actual finite gates. | Step 1, File 9 |
| CREATE | `docs/egon/evidence/2026-11-05-human-task-completion-duration.md` | Record exact finite and packaging evidence. | Step 1, File 10 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact service HEAD `7072e5b9153d1cda4ee38be60eeb9651b102a885`. Before repo execution, verify clean tree, effective Spec bytes and own fixture baseline. A legitimate intervening source commit needs an updated baseline and drift review.

### 6.2 Build, test, and environment prerequisites

Use JDK 25 and verified offline source reactor settings in §8. Record fresh owning XML. Exclude all PG/IT selectors and app/engine/collector startup.

### 6.3 Immutable constraints and approved decisions

No existing migration changes, new grants, public APIs, queries, POM or native action behavior changes.

### 6.4 Plan Clarifications

Draft detail only. The existing HumanTaskConfiguration factory keeps its qualifiers and predecessor beans. Direct callers retain compatible constructors.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Integrate optional committed Human Task Timer

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Reviewed exact Spec/Plan, source lock and offline reactor.
- Baseline state: Existing authorized COMPLETE and owner receipt; no completion Timer.
- Observable outcome: Default off; one valid committed COMPLETE uses exact native-to-receipt Duration and fixed tags.
- End state: One exact ten-path service commit after gate review.
- Test-first gate: Required. R1 and typed R2 precede B. Use exact selectors in §8.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java`

- Purpose: Validate the exact COMPLETE and post-commit samples.
- Symbols: R1, typed R2, TEST-001–003
- Repository evidence: Existing Fixture, mock native/receipt and real DataSourceTransactionManager with fake JDBC Connection.
- Dependencies and consumers: Current task classes and observer A
- Why now: Add R1 before any source, then typed positive test against A, finally add remaining finite regression methods.
- Contract/signature changes: R1#human_task_completion_observation_exists; R2#observes_only_fresh_committed_task_completion; B adds #classifies_only_fixed_due_postures_and_never_emits_invalid_or_rolled_back_times and #failed_commit_and_unavailable_registry_do_not_claim_a_sample.
- Input/output and state mapping: Use actual Fixture APIs, stable task create/due and receipt clock, one Micrometer SimpleMeterRegistry.
- Error and edge behavior: Negative, overflow, zero, rollback, failed commit, replay, unavailable meter and safe metadata.
- Standards impact: MC-TEST-001 and MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName(completionObserverFqcn));
try (var fixture = new Fixture(HumanTaskCompletionObservation.enabled(() -> registry))) {
    // Use BPMN fixture, original claim and fresh COMPLETE with exact form.
    assertEmptyWhileReceiptAppliesAndConnectionCommits();
    assertTimer("process", "on_time", Duration.ofSeconds(15));
    assertExactReplayDoesNotChangeTimer();
}
```

- Verification contribution: R1, R2 and G1.
- After this file: R1 compiles and fails absent class. Typed tests are activated after A seams.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskCompletionObservation.java`

- Purpose: Contain best-effort metrics and its commit callback.
- Symbols: disabled, enabled, recordOnCommit, record
- Repository evidence: AgentResultInboxLagObservation provides same PS timing framework.
- Dependencies and consumers: Existing Registry, Spring Tx; called by FlowableHumanTaskService.
- Why now: A provides exact disabled/enabled signatures and no-op callback. B only after typed R2.
- Contract/signature changes: recordOnCommit(HumanTaskScopeTypeEnum, Instant createdAt, Instant dueAt, Instant appliedAt).
- Input/output and state mapping: Capture only fixed tags and exact duration in committed callback.
- Error and edge behavior: No current transaction/sync, null required time, negative/overflow, unavailable Registry, callback failure or commit status other than committed yields no valid sample.
- Standards impact: MC-REUSE-001, MC-TIME-001, MC-LOG-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
if (disabled || invalidRequiredInput || noActualTransactionOrSynchronization) return;
Duration elapsed = Duration.between(createdAt, appliedAt);
long nanos = elapsed.toNanos(); // suppress overflow, accept zero
String due = dueAt == null ? "unscheduled" : appliedAt.isAfter(dueAt) ? "late" : "on_time";
registerSynchronization(afterCompletion(status -> {
    if (status == STATUS_COMMITTED && registryIsResolvable())
        timer("tianji.process.human.task.completion.duration", scopeTag, due).record(Duration.ofNanos(nanos));
}));
```

- Verification contribution: R2, G1.
- After this file: A compiles and emits no timer. B records only after a valid committed callback.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java`

- Purpose: Expose existing native-lock snapshot instants to its package.
- Symbols: createdAt(), dueAt()
- Repository evidence: Both existing snapshot constructors already convert native fields to Instant.
- Dependencies and consumers: Existing task service, no external API.
- Why now: A needs current frozen facts for the typed positive test.
- Contract/signature changes: Two package-private Instant getters.
- Input/output and state mapping: Return existing immutable Instant fields, dueAt nullable.
- Error and edge behavior: Do not re-read Flowable or add validation policy.
- Standards impact: MC-MODEL-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 10, Rule 11.
- Implementation pseudocode:

```java
// Pass through the existing immutable timestamp captured under the native task lock.
Instant createdAt() {
    return createdAt;
}
// Return null when the original native Task had no configured due date.
Instant dueAt() {
    return dueAt;
}
```

- Verification contribution: R2 and G1.
- After this file: A service has exact original task time values.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java`

- Purpose: Hook only a fresh COMPLETE after existing receipt application.
- Symbols: two old constructors, seven-arg constructor, mutate
- Repository evidence: Existing native completion verifies disappearance and APPLIED receipt.
- Dependencies and consumers: Uses HumanTaskSnapshot and local observation; existing configuration/test callers.
- Why now: Add A wiring so one typed-positive test fails solely on unimplemented metric.
- Contract/signature changes: Retain old overloads; append HumanTaskCompletionObservation to the existing Case-capable constructor.
- Input/output and state mapping: Only after receipts.apply and only COMPLETE call recordOnCommit with four scalar facts.
- Error and edge behavior: Existing replay early return, rollback, authorization, receipt and native control stay in old locations.
- Standards impact: MC-BEAN-001, MC-VALID-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 10, Rule 11.
- Implementation pseudocode:

```java
oldConstructor -> constructor(..., HumanTaskCompletionObservation.disabled());
receipts.apply(context, result);
var receipt = result.receipt();
if (action == COMPLETE)
    completionObservation.recordOnCommit(receipt.scopeType(), before.createdAt(), before.dueAt(), receipt.occurredAt());
return receipt;
```

- Verification contribution: R2 and G1.
- After this file: Fresh completion follows unchanged authority and optional observer.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`

- Purpose: Prove safe opt-in with no alternate bootstrap.
- Symbols: humanTaskCompletionObservationIsExplicitAndKeepsExistingPrerequisites
- Repository evidence: Existing Inbox test and current Human Task conditional context.
- Dependencies and consumers: Old and extended PS factory, mocked engines and existing static providers.
- Why now: Stage typed config test together with factory signature change after R2.
- Contract/signature changes: No production signature introduced here.
- Input/output and state mapping: Assert YAML false and flag-only no service; capture existing BPMN/CMMN args and observer disabled/enabled choices.
- Error and edge behavior: Unknown/Missing registry is a runtime-sample issue and does not add a factory requirement.
- Standards impact: MC-CONFIG-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```java
assertDefaultYamlFlag(false);
assertFlagAloneDoesNotBootstrapTaskService();
assertFactoryPreservesProcessAndCaseCollaboratorsAndObserverFlag();
```

- Verification contribution: G1.
- After this file: Wiring regression includes both current owner paths.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Use the existing task service bean factory for optional Registry.
- Symbols: HumanTaskConfiguration.flowableHumanTaskService, COMPLETION_METRICS_ENABLED
- Repository evidence: Same module AgentResultConfiguration uses existing MeterRegistry getIfUnique.
- Dependencies and consumers: Existing conditional factory, registry provider, process/case native engine beans.
- Why now: After R2 add test and final factory wiring.
- Contract/signature changes: Add ObjectProvider<MeterRegistry> only to existing internal Bean factory signature.
- Input/output and state mapping: Absent/false disabled, true enabled with lazy unique Registry resolution.
- Error and edge behavior: Keep existing two feature prerequisites and case-engine/provenance checks.
- Standards impact: MC-BEAN-001, MC-CONFIG-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 11.
- Implementation pseudocode:

```java
var completion = environment.getProperty(COMPLETION_METRICS_ENABLED, Boolean.class, false)
    ? HumanTaskCompletionObservation.enabled(registries::getIfUnique)
    : HumanTaskCompletionObservation.disabled();
return new FlowableHumanTaskService(processConfig, caseConfig, decisionConfig, validator, receipts, provenance, completion);
```

- Verification contribution: G1.
- After this file: Normal factory supports exact optional observer.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Keep new feature disabled in distributed defaults.
- Symbols: stellar.tianji.process.human-tasks.completion-metrics-enabled
- Repository evidence: Single existing PS application.yml human-tasks section.
- Dependencies and consumers: Existing Boot property and ProcessApiConfigurationTest.
- Why now: Finish default contract before G1.
- Contract/signature changes: Add one boolean key.
- Input/output and state mapping: Actual package default false.
- Error and edge behavior: Absent and false equivalent; old keys unchanged.
- Standards impact: MC-CONFIG-001.
- Literal rule enforcement: Rule 7.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    process:
      human-tasks:
        enabled: false         # Existing API enablement prerequisite
        publish-enabled: false # Existing deployment capability
        completion-metrics-enabled: false # New independent local sample opt-in
```

- Verification contribution: G1 and S1.
- After this file: Application package opt-in only.

#### File 8 — `CREATE docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md`

- Purpose: Freeze reviewed scope and exact metric semantics.
- Symbols: REQ-001–005, METRIC-001
- Repository evidence: Current PS command and prior Inbox design.
- Dependencies and consumers: Spec consumers and Plan.
- Why now: Before source execution, record reviewed file bytes.
- Contract/signature changes: Default-off, fixed metric and boundaries.
- Input/output and state mapping: Atomic requirements and exact finite evidence.
- Error and edge behavior: Live S12 remains separate.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 10, Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md
cp -n "$TASK_REVIEWED_SPEC" docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md
cmp "$TASK_REVIEWED_SPEC" docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md
```

- Verification contribution: S1.
- After this file: Canonical Spec is staged with the logical task.

#### File 9 — `CREATE docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md`

- Purpose: Pin source order, exact scopes and actual finite gates.
- Symbols: Step 1, R1/R2/G1/S1
- Repository evidence: Current source reactor and approved source boundary.
- Dependencies and consumers: Review and release process.
- Why now: Before source execution, archive exact approved SHA.
- Contract/signature changes: One Step and commit scope.
- Input/output and state mapping: Bound finite validation, no live invocation.
- Error and edge behavior: Stop if source/evidence drifts.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md
cp -n "$TASK_REVIEWED_PLAN" docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md
cmp "$TASK_REVIEWED_PLAN" docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md
```

- Verification contribution: S1.
- After this file: Canonical Plan is staged with the logical task.

#### File 10 — `CREATE docs/egon/evidence/2026-11-05-human-task-completion-duration.md`

- Purpose: Record exact finite and packaging evidence.
- Symbols: Evidence file
- Repository evidence: Existing PS Inbox evidence layout.
- Dependencies and consumers: Evidence readers, Plan and master S12 row.
- Why now: After actual gate execution.
- Contract/signature changes: No runtime claim from mock tests.
- Input/output and state mapping: Capture baseline and commit paths, report counts, packaged default and unchanged historical SQL.
- Error and edge behavior: Omit unknown or unexecuted claims.
- Standards impact: MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record baseline and exact staged code/config/docs path inventory.
Record owning R1/R2/G1 XML outcomes and actual PS package inspection.
Keep database, Flowable runtime, exporter and S12 in the separate unexecuted row.
```

- Verification contribution: S1.
- After this file: One auditable logical commit ready after review.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Execute §8 R1/R2/G1/S1 with each corresponding source state.
- Expected result: R1/R2 one expected assertion failure each, G1 all selected actual tests pass with no failures or skips, S1 exact paths and no migration/POM delta.
- Failure returns to: R1 File 1, R2 Files 1–4 A, G1 Files 1–7 B, S1 exact docs and scope.
- Completion criteria: Current packaged YAML false, frozen migration inventory unchanged, signed-off finite evidence and unchanged API/grant paths.
- Rollback: Config false stops new samples. Source reversion is isolated to this commit if authorized.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskCompletionObservation.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/spec/2026-11-05-22-31-human-task-completion-duration.md`, `docs/egon/plan/2026-11-05-22-32-human-task-completion-duration.md`, `docs/egon/evidence/2026-11-05-human-task-completion-duration.md`.
- Commit: `feat(process): observe committed human task completion duration`.

## 8. Test, Validation, and Quality Gates

Run only after approval, using the current source owner checkout.

```sh
task_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
task_finite() {
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$task_settings" -s "$task_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
task_finite 'FlowableHumanTaskServiceTest#human_task_completion_observation_exists'
task_finite 'FlowableHumanTaskServiceTest#observes_only_fresh_committed_task_completion'
task_finite FlowableHumanTaskServiceTest,ProcessApiConfigurationTest,HumanTaskSnapshotTest,HumanTaskReceiptStoreTest
git diff --check
git diff --cached --check
```

| Gate | Exact evidence | Required result |
| --- | --- | --- |
| R1 | Exact class-presence owning XML | 1 expected assertion failure, zero errors/skips |
| R2 | Typed COMPLETE owning XML | 1 expected sample-count assertion failure, zero errors/skips |
| G1 | Four named finite owning XML reports | Positive counts, zero failures/errors/skips |
| S1 | Both skill validators strict, exact staged paths, all prior SQL bytes and actual PS resource | PASS; default false, no public/PaC/POM/SQL delta |

For package proof use the same offline flags with STELLAR_VERIFY_GOAL=verify, clean, the existing permission-producer profile, the exact baseline commit, build ID LOCAL_UNCOMMITTED-t4-02-task-completion-duration-20261105, and the same four exact finite selectors. Inspect PS executable JAR resource `BOOT-INF/classes/application.yml` and unchanged migration/permission entries against baseline. The original `HumanTaskReceiptPostgresTest` may compile during Maven but its selector must not run.

## 9. Migration, Compatibility, Rollout, and Rollback

No migrations. Existing Task paths and flags remain. Opt in separately through normal operations. Metric can be disabled with the exact false setting.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step and files | Gate |
| --- | --- | --- |
| REQ-001 | Step 1 files 1–4 | R2/G1 |
| REQ-002 | Step 1 files 1–4 | G1 |
| REQ-003 | Step 1 files 1/2 | G1 |
| REQ-004 | Step 1 files 5–7 | G1/S1 |
| REQ-005 | Step 1 files 8–10 | S1 |

## 11. Risks, Blockers, and User Decisions

| ID | Status | Action |
| --- | --- | --- |
| OPEN-001 | Native/DB clock and real collectors not yet verified | Separate deployment proof and S12 gate |
| OPEN-002 | Current task backlog and SLA threshold separate | Require real workload and explicit metric policy |
| OPEN-003 | Canonical Spec/Plan draft awaits repository execution lock | Root review exact artifact bytes |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Exact fixed-dimension current-owner completion sample.

### 12.2 Spec consistency

One owner-local timer, explicit existing clock provenance and scope.

### 12.3 Repository executability

Existing source reactor and finite selectors. Scratch proofs do not replace repository gate execution.

### 12.4 Test and release completeness

R1/R2/G1/S1 required before one commit. Runtime and exporter separate.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing PS | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Same SQL, Tx and MeterRegistry | None |
| MC-DEP-001 | Applicable | PASS | §6 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7 | Fresh COMPLETE with native/receipt time | None |
| MC-MODEL-001 | Applicable | PASS | §7 | Existing immutable Task snapshot | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/§7 | No DTO/PO mapping | None |
| MC-LOG-001 | Applicable | PASS | §7 | Fixed once diagnostic | None |
| MC-BEAN-001 | Applicable | PASS | §7 | Same factory | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK and existing libraries | None |
| MC-JSON-001 | Not applicable | N/A | §7 | No JSON change | None |
| MC-TIME-001 | Applicable | PASS | §4.9 | Native and DB Instants, Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §7 | One default false | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Simple fixed observer | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | Exact ten paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Source ready for review | Actual I/E/S12 outside Step |

### 12.6 Final verdict

PASS — Ready for user review

仓库外审阅候选。执行需要当前基线及精确批准字节，真实数据库、运行时与 S12 留在独立验收范围。
