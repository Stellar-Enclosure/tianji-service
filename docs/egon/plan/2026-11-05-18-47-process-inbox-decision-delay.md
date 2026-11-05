# T4-02 Process Inbox 决策时延实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-18-47-process-inbox-decision-delay.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 18:47 Asia/Shanghai |
| Updated | 2026-11-05 18:47 Asia/Shanghai |
| Owner | Process Server 观测所有者 |
| Repository | tianji-service |
| Scope | 一个 PS owner-local Micrometer Timer，六个 source/test/config 路径及审阅证据 |
| Source Requirement | 主计划 §19/T4-02 运行时延证据首切片 |
| Baseline Revision | tianji-service main 40ba8e1f0f968fd374cea389e9febed6bf952c7a |
| Implements Spec | [Process Inbox decision delay](../spec/2026-11-05-18-43-process-inbox-decision-delay.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 18:43 Asia/Shanghai SHA-256 8425d678db19ebd6ded62717955d2ab88c49b4cda3a0fe8e8c52cf75018172e8 |
| Effective Specs | [Process Inbox delay](../spec/2026-11-05-18-43-process-inbox-decision-delay.md), [T3-12 runtime boundaries](../spec/2026-11-05-15-13-expense-four-path-acceptance.md#9-interface-definitions) |
| Depends On Plans | [T3-12 source](2026-11-05-15-30-expense-four-path-acceptance.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Root has reviewed and approved only the exact default-off PS source under the user master Plan. No extra query, migration, runtime metrics exporter or threshold is authorized. Existing receipt semantics and application defaults remain protected.

同一 Inbox 行持久接收到终态决定的时间使用既有 JDBC 回读。原事务成功后通过已有 Micrometer 记录，默认 false。一个 PS Step，有限 R1/R2/G1 加 PG 测试编译证据。当前待处理年龄、实时导出、业务 SLA、跨源查询和 S12 独立。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Process Inbox delay](../spec/2026-11-05-18-43-process-inbox-decision-delay.md) Review，修订锁见头部。已授权设计/计划范围为可选 PS terminal Timer，无新 SQL、依赖和运行时。source GO 时锁定目标 HEAD/Spec/Plan。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Inbox delay](../spec/2026-11-05-18-43-process-inbox-decision-delay.md) | Review and header SHA | All | New Timer and guard |
| Dependency | [T3-12](../spec/2026-11-05-15-13-expense-four-path-acceptance.md#9-interface-definitions) | Source64 delivered | §9/§14 | Existing event flow and separate runtime gate |

### 2.3 Superseded or excluded content

None. AS trace, Outbox stats, PS existing durable authority, SDK, web and existing runtime gates keep scope.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/§6.3 | Same-row persisted interval | Three terminal states, zero allowed, negative omitted | Inbox and observer |
| REQ-002 | §4/§7.3 | Winning transition and committed only | No duplicates, reads, pending, rollback or unknown completion | Exact finish seam |
| REQ-003 | §4/§9 | Fixed cardinality and contained failure | One name/outcome, no IDs, no business impact on Registry failure | Observer |
| REQ-004 | §4/§8/§16 | Default off and existing resources | No new query, POM, migration, thread or endpoint | Config |
| REQ-005 | §4/§18 | Clear evidence limits | No backlog/SLO/S12 promotion | Tests and evidence |
| REQ-006 (T3-12 compatibility) | T3-12 §9/§10 | Preserve owner flow and durable evidence | No change to receipts and authority | PS regression |
| REQ-007 (T3-12 compatibility) | T3-12 §14 | Keep finite and actual runtime separate | PG test compiled only, no app | Test scope |
| REQ-008 (T3-12 compatibility) | T3-12 §8/§14 | Preserve common fixture/web | No fixture or client changes | Scope |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Finite test → observer → Inbox exact terminal hook → config fixture test → factory wiring → false flag → actual limited evidence/approved docs. Keep source paths and tests in one service commit.

### 4.2 Test-first strategy

| Gate | Before behavior | Expected failure | GREEN |
| --- | --- | --- | --- |
| R1 | File 1 presence method | Assertion on missing class, not a compile error | File 2 A |
| R2 | Observer same-package signatures and disabled bodies, typed tests | Exact one sample assertion expects 1, observes absent/zero | File 2 B |
| R3 | File 4 new factory test uses old config via stable fixture or reflected shape until File 5 | Missing opt-in wiring assertion | File 5 |
| G1 | Final source and YAML | Any skipped, missing, failure or compile error blocks | Exact finite suite |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Reviewed pair, existing PS owner baseline | Read-only artifact review | Same PS file writes or build report path | Evidence provenance |

### 4.4 Commit boundaries

One semantic service commit, six source/test/config plus evidence, Spec and Plan. No intermediate source commit.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Timer | §7.0 Add | Existing finish time and readback | Component transport Timer has different meaning | One local callback | Add |
| Observer helper | §7.0 Add | Existing AS technical metric isolation pattern | Inline would mix backend and transaction | One small final class | Add |
| Global pending reader | §18 Deferred | due has writes; current V6 index not aligned | Existing metadata as context | Requires actual query plan | Omit |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Observer | REQ-001–003 | R1/R2 | Current Tx and Micrometer | Fixed metric | Inbox | Step 1 |
| Finish seam | REQ-001/002 | G1 | Existing PS Entry and observer | Winner/time hook | Factory | Step 1 |
| Wiring | REQ-003/004 | R3/G1 | Existing config | Default false | PS | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Existing PS bridge and ProcessSecurityConfiguration | §7/§8 | Same owner | Step 1 MC-ARCH-001 |
| Reuse/capability | Micrometer Core 1.17.2 and Spring Tx 7.0.10 in PS | §6 | No dependency | Step 1 MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | Current internal Entry record and receipt | §10 | Nullable internal Instant only, no wire or converter | Step 1 MC-VALID-001 |
| Bean/logging/util/JSON/time/config | Existing PS factory and local diagnostics | §6.3/§9 | Existing bean, fixed log and Duration | Step 1 MC-TIME-001 |
| Business variation/pattern | Single existing finish | §13 | Direct observer adapter | Step 1 MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Duration | PS Inbox V6 and exact finish/read | Existing received_at/decided_at | Same row complete | Reuse | No SQL | Step 1 MC-REUSE-001 |
| Registry | Current PS Actuator and Component | Packaged Micrometer Timer | Existing backend | Reuse | No POM | Step 1 MC-DEP-001 |
| Commit boundary | Existing JDBC manager and TSM | STATUS_COMMITTED | Same owner | Reuse | No transaction/event | Step 1 MC-VALID-001 |

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
| Rule 1 | §6.2/§8 | Bridge/Entry | 1–5 | Semantic adapter, existing value | G1 | Step 1 | PASS |
| Rule 2 | §6.2/§7 | Existing owner path | 1–5 | Exact terminal, duration, flag | G1 | Step 1 | PASS |
| Rule 3 | §6.2/§10 | Immutable Entry | 3 | Internal Instant only | G1 | Step 1 | PASS |
| Rule 4 | §6.2/§8 | Existing PS factory | 2/5 | Existing PS technical LoggerFactory, no Lombok dependency; existing bean | G1 | Step 1 | PASS |
| Rule 5 | §6.2 | Existing JDK/Micrometer | 1–5 | No dependency | G1 | Step 1 | PASS |
| Rule 6 | §6.2/§10 | Existing JSON | 3 | No JSON changes | G1 | Step 1 | N/A |
| Rule 7 | §6.2/§16 | Single PS YAML | 5/6 | False/absent | G1 | Step 1 | PASS |
| Rule 9 | §6.2/§13 | Fixed flow | 2/3 | Direct observation | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | DB Instant | 1–3 | One Duration, no rounding | G1 | Step 1 | PASS |
| Rule 11 | §7/§11 | Existing owner | All | Same layer/DB and no CQE change | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | received_at, decided_at and corresponding values | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: DB UTC instants | Not applicable: not emitted | Same row and database clock | Existing PostgreSQL microseconds, no rounding | SQL/public receipt unchanged | Step 1 G1 TEST-001/002 |
| TIME-002 | receipt to decision | Duration | java.time.Duration | Not persisted | Internal | Not applicable: duration | Not applicable: backend base unit | Negative excluded, zero valid | Preserve input exactness | New Timer only | Step 1 G1 TEST-001/003 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservationTest.java | Existing AgentDispatchStoreTest Mockito and AS SimpleMeterRegistry test | Presence + three exact methods | Freeze finite owner, timing and cardinality behavior | Step 1 | REQ-001–008 | R1/R2/G1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservation.java | PS packaged Micrometer, AS existing technical observation, Spring 7.0.10 | disabled(), enabled(Supplier<MeterRegistry>), recordOnCommit(AgentResultReceiptStateEnum,Instant,Instant) | Keep optional metrics separate from persisted path | Step 1 | REQ-001–008 | R2 expected zero then G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java | Existing read obtains decided timestamp and validates state; finish requires count 1 then rereads | Current constructor/read/finish/internal Entry | Hook only the existing positive terminal transition | Step 1 | REQ-001–008 | G1 winner/rollback/replay |
| MODIFY | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java | Existing context runner and mocked ProcessEngine | agentResultInboxLagObservationIsExplicitAndDoesNotChangePrerequisites | Verify the flag and existing prerequisites | Step 1 | REQ-001–008 | R3 and G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java | Existing API plus agent-results gates; same DataSource/schema | AgentResultConfiguration#agentResultInbox and existing owner guards | Wire optional observer through current PS bean | Step 1 | REQ-001–008 | R3 GREEN/G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/resources/application.yml | Single existing PS base YAML | stellar.tianji.process.agent-results.lag-metrics-enabled | Keep production default off | Step 1 | REQ-001–008 | G1 exact default/static |
| CREATE | docs/egon/evidence/2026-11-05-process-inbox-decision-delay.md | Existing source-only evidence patterns | R1/R2/R3/G1/S1 | Preserve actual finite evidence boundary | Step 1 | REQ-001–008 | S1 |
| CREATE | docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md | Existing docs/egon/spec | Owner approved Spec and reciprocal Plan | Archive approved exact Spec | Step 1 | REQ-001–008 | S1 strict Spec |
| CREATE | docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md | Existing docs/egon/plan | Implements Spec, current Step and scope | Archive approved exact Plan | Step 1 | REQ-001–008 | S1 strict Plan |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Service main 40ba8e1f0f968fd374cea389e9febed6bf952c7a clean when Spec locked. Components 87af09fea9a9d58e7127b009d76f60438c79e569. Recheck target and all existing source-reactor siblings before source validation. Preserve concurrent work and no broad staging.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK/source reactor | JDK25 and scripts/verify-source-reactor.sh | Existing offline settings and sibling sources | Finite only |
| Registry and transactions | Existing PS Actuator and transaction manager | SimpleMeterRegistry and Mockito | No real PG or engine |
| Config | PS only application.yml | Existing API/Inbox guards | Default false |
| PG test | Existing AgentResultInboxPostgresTest | Compiled test source | No execution |

### 6.3 Immutable constraints and approved decisions

Preserve current DDL/DML/query count, public receipt and owner transaction. No dependency, runtime flag expansion or startup. Proposed only metric flag and local Timer, no thresholds.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | STATUS_COMMITTED via afterCompletion, not afterCommit | Spring Tx existing contract | Observer failure cannot change completion | G1 failure |
| PLAN-CLAR-002 | Registry provider lazy and unique | Existing ObjectProvider and AS precedent | No new Registry or export | G1 failure |
| PLAN-CLAR-003 | One safe warning per adapter lifetime | Existing operator diagnostics | No IDs or high-frequency telemetry log | G1 failure |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 接入同源终态 Inbox Timer

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008.
- Dependencies: Exact reviewed pair and source lock.
- Baseline state: Existing owner readback, no local delay Timer.
- Observable outcome: When enabled, one committed winning terminal decision records an exact Duration under one fixed outcome.
- End state: One nine-path service commit with finite evidence.
- Test-first gate: Required. R1/R2 and wiring R3 before source behavior, G1 after.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservationTest.java`

- Purpose: Freeze finite owner, timing and cardinality behavior.
- Symbols: Presence + three exact methods.
- Repository evidence: Existing AgentDispatchStoreTest Mockito and AS SimpleMeterRegistry test.
- Dependencies and consumers: Direct adapter, fake registry, existing Tx; use fake JDBC for winning finish only after File 3.
- Why now: Test-first R1 missing class, R2 compiles with File 2 A.
- Contract/signature changes: Exact four planned selectors, stage typed methods after A.
- Input/output and state mapping: Fixed observed elapsed and status → exact tag/count/no record.
- Error and edge behavior: Rollback, unknown completion, negative/overflow, unavailable registry and old winner false paths.
- Standards impact: MC-TEST-001/MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test void lagObservationSeamExists() { assertDoesNotThrow(() -> Class.forName(OBSERVATION_FQCN)); }
@Test void recordsOnlyCommittedTerminalOutcomesWithFixedTags() {
    registerExactDurationInLocalSynchronization();
    assertNoSampleBeforeCompletion();
    completeWith(STATUS_COMMITTED);
    assertExactTimerCountDurationAndThreeAllowedTagValues();
}
@Test void rollbackAndNonTerminalOrReplayDoNotRecord() { proveMockJdbcWinnerAndRollbackWithoutDatabase(); }
@Test void unavailableRegistryAndMeterFailureAreIsolated() { assertSafeFallbackAndInvalidIntervals(); }
```

- Verification contribution: R1/R2/G1.
- After this file: R1 compiles and has one missing class assertion; typed test enters after File 2 A.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservation.java`

- Purpose: Keep optional metrics separate from persisted path.
- Symbols: disabled(), enabled(Supplier<MeterRegistry>), recordOnCommit(AgentResultReceiptStateEnum,Instant,Instant).
- Repository evidence: PS packaged Micrometer, AS existing technical observation, Spring 7.0.10.
- Dependencies and consumers: File 3 and File 5; ObjectProvider supplies registry lazily.
- Why now: Minimal A signatures for R2, B enables exact behavior.
- Contract/signature changes: One name/outcome tag; static disabled and one-time safe diagnostic.
- Input/output and state mapping: Terminal state and Duration only in callback, no context/owner values.
- Error and edge behavior: Skip off/null/missing sync/negative/overflow; contain runtime recorder failures.
- Standards impact: MC-REUSE-001/MC-TIME-001/MC-LOG-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
void recordOnCommit(AgentResultReceiptStateEnum state, Instant receivedAt, Instant decidedAt) {
    if (disabledOrMissingSynchronizationOrUnsupportedState(state)) return;
    Duration duration = validatedNonNegativeDurationWithNanosRange(receivedAt, decidedAt);
    if (duration == null) return;
    String outcome = exactOutcome(state);
    registerSafely(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) recordLocallySafely(outcome, duration);
        }
    });
}
```

- Verification contribution: R2 expected zero then G1.
- After this file: Observer implements fixed local timer; original Inbox still has no call.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java`

- Purpose: Hook only the existing positive terminal transition.
- Symbols: Current constructor/read/finish/internal Entry.
- Repository evidence: Existing read obtains decided timestamp and validates state; finish requires count 1 then rereads.
- Dependencies and consumers: Old constructors and old five-argument Entry test callers; File 2 observer.
- Why now: Observer must compile before direct hook.
- Contract/signature changes: Retain old constructors and receipt; add observer overload and nullable Instant Entry field.
- Input/output and state mapping: Readback receivedAt/decidedAt and matching expected terminal state → optional observation.
- Error and edge behavior: No new DB query or validation. Ignore telemetry on mismatch/null; same returned receipt and errors.
- Standards impact: MC-ARCH-001/MC-VALID-001/MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
// Keep the exact current JDBC update, parameters and changed count handling.
Entry decided = find(registration, event.eventId(), true);
if (decided.receipt().state() == state) {
    lag.recordOnCommit(state, decided.receipt().receivedAt(), decided.decidedAt());
}
return decided.receipt();
// In read(), retain existing optional decided timestamp as Entry.decidedAt.
// Existing six-argument Inbox and five-argument Entry delegate to disabled/null.
```

- Verification contribution: G1 winner/rollback/replay.
- After this file: Owner hook active only through an explicitly configured observer.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`

- Purpose: Verify the flag and existing prerequisites.
- Symbols: agentResultInboxLagObservationIsExplicitAndDoesNotChangePrerequisites.
- Repository evidence: Existing context runner and mocked ProcessEngine.
- Dependencies and consumers: File 5 exact factory; no real web server or engine.
- Why now: Write reflective R3 missing overload first, then real mock/factory assertions.
- Contract/signature changes: One named regression selector; default, explicit true and missing existing API flags.
- Input/output and state mapping: Mock configuration and registry; no launch.
- Error and edge behavior: Absence/default flags cannot introduce Inbox or meter; configuration must select enabled observer only when explicitly true.
- Standards impact: MC-CONFIG-001/MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```java
@Test void agentResultInboxLagObservationIsExplicitAndDoesNotChangePrerequisites() {
    assertDoesNotThrow(() -> lookupExpectedAgentResultInboxFactorySignature());
    assertLagFlagAloneCreatesNoInboxInExistingContextRunner();
    assertFactorySelectsDisabledWhenAbsentOrFalseAndEnabledForTrue();
}
```

- Verification contribution: R3 and G1.
- After this file: R3 actual old factory missing signature, no startup error.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Wire optional observer through current PS bean.
- Symbols: AgentResultConfiguration#agentResultInbox and existing owner guards.
- Repository evidence: Existing API plus agent-results gates; same DataSource/schema.
- Dependencies and consumers: File 2 adapter, File 3 overload, existing Environment/ObjectProvider.
- Why now: File 4 locks desired factory first.
- Contract/signature changes: Add Environment and ObjectProvider<MeterRegistry> to same method. No new bean or registry.
- Input/output and state mapping: Boolean config true → adapter with provider::getIfUnique, otherwise disabled.
- Error and edge behavior: Preserve original prerequisite validation; typed invalid boolean follows existing property conversion, missing registry is runtime no-sample.
- Standards impact: MC-BEAN-001/MC-CONFIG-001/MC-REUSE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```java
var lag = environment.getProperty(AGENT_RESULT_LAG_PROPERTY, Boolean.class, false)
        ? AgentResultInboxLagObservation.enabled(registries::getIfUnique)
        : AgentResultInboxLagObservation.disabled();
return new AgentResultInbox(manager.getDataSource(), manager, configuration.getDatabaseSchema(),
        dispatches, producers, properties.pendingRetention(), lag);
```

- Verification contribution: R3 GREEN/G1.
- After this file: Factory compiled with same existing bean name and guards.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Keep production default off.
- Symbols: stellar.tianji.process.agent-results.lag-metrics-enabled.
- Repository evidence: Single existing PS base YAML.
- Dependencies and consumers: Same factory property.
- Why now: Set explicit default once source path complete.
- Contract/signature changes: One boolean false key, no other config.
- Input/output and state mapping: False emits nothing, no new startup or exporter.
- Error and edge behavior: Keep existing API/agent-results owner controls and untouched defaults.
- Standards impact: MC-CONFIG-001/MC-SCOPE-001.
- Literal rule enforcement: Rule 7, Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    process:
      agent-results:
        lag-metrics-enabled: false
        # Existing API and agent-results enablement remain independently owned; no exporter setting is added.
```

- Verification contribution: G1 exact default/static.
- After this file: Only desired false key changed.

#### File 7 — `CREATE docs/egon/evidence/2026-11-05-process-inbox-decision-delay.md`

- Purpose: Preserve actual finite evidence boundary.
- Symbols: R1/R2/R3/G1/S1.
- Repository evidence: Existing source-only evidence patterns.
- Dependencies and consumers: Owning Surefire XML and compiled PG class.
- Why now: Record actual observations after finite gates.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Exact head, selectors, counts, no I/E or runtime, compiled class digest.
- Error and edge behavior: Missing proof blocks close. No guessed measurements.
- Standards impact: MC-SCOPE-001/MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
compare actual fresh owning reports and prescribed RED causes
record owner tests, source locks, file hashes and exact final flag
record PG test compile-only and live collector/SLO/S12 separately
```

- Verification contribution: S1.
- After this file: Reviewable limited proof ready.

#### File 8 — `CREATE docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md`

- Purpose: Archive approved exact Spec.
- Symbols: Owner approved Spec and reciprocal Plan.
- Repository evidence: Existing docs/egon/spec.
- Dependencies and consumers: Locked approved scratch bytes.
- Why now: Approval precedes source, archive in same commit.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Match approved SHA and exact destination.
- Error and edge behavior: No overwrite or mismatching metadata.
- Standards impact: MC-SCOPE-001/MC-BLOCKER-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md
cp -n "$LAG_APPROVED_SPEC" docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md
cmp "$LAG_APPROVED_SPEC" docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md
```

- Verification contribution: S1 strict Spec.
- After this file: Reviewed bytes linked once Plan is archived.

#### File 9 — `CREATE docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md`

- Purpose: Archive approved exact Plan.
- Symbols: Implements Spec, current Step and scope.
- Repository evidence: Existing docs/egon/plan.
- Dependencies and consumers: Locked approved scratch bytes.
- Why now: Source evidence complete.
- Contract/signature changes: Docs only.
- Input/output and state mapping: Match approved SHA and exact destination.
- Error and edge behavior: No overwrite or broken link.
- Standards impact: MC-SCOPE-001/MC-BLOCKER-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md
cp -n "$LAG_APPROVED_PLAN" docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md
cmp "$LAG_APPROVED_PLAN" docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md
```

- Verification contribution: S1 strict Plan.
- After this file: Ready for one exact commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/R2/R3/G1/S1 in §8.
- Expected result: Three prescribed assertion failures, then all selected actual finite tests with zero fail/error/skip and PG test only compiled.
- Failure returns to: File 1/4 for test setup, File 2 for metric timing, File 3 for exact JDBC seam, File 5/6 for flag, 7–9 for evidence.
- Completion criteria: Default off, same source, no behavior change to original flow, exact finite proof.
- Rollback: Disable feature or revert one commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInboxLagObservation.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-05-process-inbox-decision-delay.md`, `docs/egon/spec/2026-11-05-18-43-process-inbox-decision-delay.md`, `docs/egon/plan/2026-11-05-18-47-process-inbox-decision-delay.md`.
- Commit: feat(process): observe committed agent inbox decision delay.

## 8. Test, Validation, and Quality Gates

Work from /Users/mario/SelfProject/Stellar-Enclosure/tianji-service only after GO. Use fresh owning PS Surefire XML. Do not start services or connect PostgreSQL.

```sh
lag_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
lag_finite() {
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$lag_settings" -s "$lag_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
lag_finite 'AgentResultInboxLagObservationTest#lagObservationSeamExists'
lag_finite 'AgentResultInboxLagObservationTest#recordsOnlyCommittedTerminalOutcomesWithFixedTags'
lag_finite 'ProcessApiConfigurationTest#agentResultInboxLagObservationIsExplicitAndDoesNotChangePrerequisites'
lag_finite AgentResultInboxLagObservationTest,ProcessApiConfigurationTest,AgentDispatchStoreTest
git diff --check
git diff --cached --check
```

| Gate | Required actual result | Failure returns to |
| --- | --- | --- |
| R1 | 1 failure from explicit absent-class assertion, 0 error/skip | File 1 |
| R2 | 1 failure expecting one committed sample, no discovery/compile error | Files 1/2 A |
| R3 | 1 failure on missing optional factory wiring, not invalid old prerequisites | File 4 |
| G1 | All owning selected tests actual pass, no fail/error/skip; PS compiled AgentResultInboxPostgresTest.class exists and no executed report | Files 1–6 |
| S1 | Existing strict Spec and Plan validators plus exact nine staged paths and no SQL/POM deltas | Files 7–9 |

## 9. Migration, Compatibility, Rollout, and Rollback

No migrations, new library, endpoint, collector or deployments. Existing calls use disabled overload. Default false. Rollback one Step or keep flag off. Any real collector acceptance is separately authorized.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | §6.3/§9 | Step 1 | 1/2/3 | TEST-001/002 | Exact DB elapsed |
| REQ-002 | §7/§9 | Step 1 | 1/2/3 | TEST-002 | Committed winner |
| REQ-003 | §9/§15 | Step 1 | 1/2/5 | TEST-001/003 | Fixed tags and safe failure |
| REQ-004 | §8/§16 | Step 1 | 4/5/6 | TEST-004/005 | Default false |
| REQ-005 | §18 | Step 1 | 7/8/9 | S1 | Scoped proof |
| REQ-006 | T3-12 §9/§10 | Step 1 | 3/5 | G1 | Existing behavior |
| REQ-007 | T3-12 §14 | Step 1 | 1/7 | G1/S1 | No I/E claim |
| REQ-008 | T3-12 §8/§14 | Step 1 | 7 | S1 | Existing fixture scope |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- |
| RISK-001 | Completion timer omits pending rows | Step 1 | Source SQL and §18 | PS ops | Live oldest age requires query-plan proof and approved budget |
| RISK-002 | DB clock skew or backend failure | Step 1 | Existing DB clock and local registry | PS ops | Skip invalid/failed sample without changing original work |
| OPEN-001 | Runtime S12 and SLO | Future | No accepted collector or numeric workload | Release owner | Separate real isolated I/E and threshold agreement |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Existing owner, default false, exact local measurement and no live authority.

### 12.2 Spec consistency

Same scope, fixed label, existing SQL, readback and time precision.

### 12.3 Repository executability

Current source, exact ownership, packaged Tx/Micrometer, existing reactor and config mock pattern inspected.

### 12.4 Test and release completeness

Finite source proof and compile-only PG. Runtime checks stay separate.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing PS | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Same SQL, Tx and MeterRegistry | None |
| MC-DEP-001 | Applicable | PASS | §6 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7 | Exact winner/status/timestamp | None |
| MC-MODEL-001 | Applicable | PASS | §7 | Immutable Entry | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/§7 | No DTO/PO mapping | None |
| MC-LOG-001 | Applicable | PASS | §7 | Fixed once diagnostic | None |
| MC-BEAN-001 | Applicable | PASS | §7 | Same factory | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK and existing libraries | None |
| MC-JSON-001 | Not applicable | N/A | §7 | No JSON change | None |
| MC-TIME-001 | Applicable | PASS | §4.9 | DB Instant/Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §7 | One default false | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Simple fixed observer | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | Exact nine paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Source ready for review | Actual I/E/S12 outside Step |

### 12.6 Final verdict

PASS — Ready for user review

本次只编制仓库外 Spec/Plan。当前无源码执行、Maven、应用、数据库或真实 exporter 验证。
