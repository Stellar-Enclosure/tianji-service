# T4-03 Agent 首条持久输入 checkpoint 实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-43-agent-checkpoint-capture.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 01:43 Asia/Shanghai |
| Updated | 2026-11-06 01:43 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | Default-off first durable input checkpoint and finite/opt-in proof |
| Source Requirement | 主计划 §17 S11 / §19 T4-03 |
| Baseline Revision | f262564e5cc575de25d301bd6dd452fbd00d3434 |
| Implements Spec | [Checkpoint capture](../spec/2026-11-06-01-42-agent-checkpoint-capture.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 01:42 Asia/Shanghai SHA-256 6603549fdc736bb74f85a23cf67551a7948132da4830d690403435e635a7d8fe |
| Effective Specs | [Checkpoint capture](../spec/2026-11-06-01-42-agent-checkpoint-capture.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 source Step 将既有 V16 与真实 Session append 接通。默认 false，原运行和原生 Runner 保持兼容。定向有限测试、实际制品核验和独立操作者 PG fixture。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Checkpoint capture](../spec/2026-11-06-01-42-agent-checkpoint-capture.md)，Review，SHA-256 `6603549fdc736bb74f85a23cf67551a7948132da4830d690403435e635a7d8fe`，Service 基线 `f262564e5cc575de25d301bd6dd452fbd00d3434`。

### 2.2 Effective Spec set

同一主 Spec。现有 V16 存储、Run 和 Session 租约与主计划是已落地的依赖。它们的行为及 SQL 保持现状。

### 2.3 Superseded or excluded content

无替换。原生恢复、运行接管、外部对账、新的授予或产品部署留给独立验证。

## 3. Effective Requirements and Acceptance

| ID | Spec source | Acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §7/§16 | Explicit default off, exact same-source readiness on true | Existing runtime config, executor and AS YAML |
| REQ-002 | §7/§11 | First exact admitted native event only | Runtime and Session adapter |
| REQ-003 | §7/§11 | Atomic checkpoint/native append, exact provenance, rollback | JDBC writer and native transaction |
| REQ-004 | §7/§16/§17 | Existing states, routes, scopes and all SQL preserve | Source/package no external changes |
| REQ-005 | §14/§18 | Actual finite/package and compile-only I | Separate operator gates |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock clean source, exact V16, Spec and six-scope prerequisite. Run typed A RED before production code. Then repository → Session → executor → existing factory and default YAML → compile-only operator fixture → G1/P1/S1.

### 4.2 Test-first strategy

Use one failing assertion from exact first-input finite test. An incomplete compile or a skipped class is not R1. Add full admissibility and same transaction proof before final B.

### 4.3 Sequential and parallel boundaries

One Maven reactor at a time. Read-only research and code review can run in parallel. The actual DB/operator fixture remains out of the source gate.

### 4.4 Commit boundaries

One semantic commit and ten exact paths. No typed A, generated artifacts, environment files or other edits.

### 4.5 Spec Simplicity and Implementation-necessity Audit

Current Session transaction owns all required persistence. Direct optional binding and exact native identity comparison preserve ownership. A second writer, projection, recovery API or background task has no current function.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Files | Gate |
| --- | --- | --- | --- |
| Typed A | Locked V16 and finite capture seam | File 1 and temporary minimal typed A | R1 |
| Product B | Intended R1 | Files 1–6 | G1/P1 |
| Operator fixture | Trusted static product factory and same transaction source | File 7 | Compile only |
| Evidence | Actual source and artifact results | Files 8–10 | S1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | AR JDBC/ADK and current AS host | Existing infrastructure ownership |
| Validation | Existing lease and exact run material | Reuse, then check fixed first input |
| Migration | Current V16 shape | No DDL |
| Config and time | Current factory and DB lease clock | Existing bean, false default, UTC capture |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Scope | Current trusted run, Session, tenant/app lock | Existing material and native lock order | Fits | Reuse exact binding and key | Optional nonblocking check | Step 1 MC-REUSE-001 |
| Persistence | Current Session append and V16 | Existing primary tx and storage verifier | Fits | Direct insert and original fence | One fixed SQL | Step 1 MC-ARCH-001 |
| Runtime | Existing executor, factory and AS defaults | One verified owning DataSource | Fits | One default-off option | No dependency | Step 1 MC-CONFIG-001 |

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
| Rule 1 | §6.2 | Existing Agent runtime | Files 1–7 | Explicit local immutable value holder and existing storage classes | G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | Existing Agent runtime | Files 1–7 | Typed material validation plus exact identity, state and fences at storage boundary | G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | Existing Agent runtime | Files 1–7 | Private immutable value data only and redacted representation | G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | Existing Agent runtime | Files 1–7 | Reuse existing auto-configuration bean and fixed technical failure without payload logging | G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2 | Existing Agent runtime | Files 1–7 | Existing Spring/JDBC, ADK and JDK only | G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | Existing Agent runtime | Files 1–7 | Fixed SQL empty array; no external JSON schema | G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | Existing Agent runtime | Files 1–7 | Explicit false on authoritative packaged AS defaults | G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2 | Existing Agent runtime | Files 1–7 | Direct use of existing transaction boundary for one fixed technical case | G1/P1/S1 | Step 1 | N/A |
| Rule 10 | §6.2 | Existing Agent runtime | Files 1–7 | Use existing UTC occurred instant representation and database clock | G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | Existing Agent runtime | Files 1–7 | Current AR/AS infrastructure and existing owning AgentDB | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | agent_checkpoint.captured_at and original Run/Session lease checks | Occurred instant | PostgreSQL timestamptz(6), java.time.Instant for existing lease | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal only | Nonfinite capture rejected by V16, original lease/deadline equality still expires | Six fractional digits, primary PostgreSQL clock_timestamp(), no conversion to local date/time | No change to old clocks or timestamps | Step 1 TEST-002/004 and separate operator transaction round-trip |

## 5. Change File Tree

| Operation | Exact path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java` | Focused finite capture regression | Step 1 File 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java` | Single fenced checkpoint INSERT in current JDBC adapter | Step 1 File 2 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java` | Add optional bound first-input capture to current Session append | Step 1 File 3 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | Admit exact original run and input | Step 1 File 4 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java` | Explicit runtime option and V16 startup validation | Step 1 File 5 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Packaged default false | Step 1 File 6 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` | Opt-in actual PostgreSQL and rollback fixture | Step 1 File 7 |
| CREATE | `docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md` | Effective design | Step 1 File 8 |
| CREATE | `docs/egon/plan/2026-11-06-01-43-agent-checkpoint-capture.md` | One source Step | Step 1 File 9 |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-capture.md` | Actual results and runtime boundary | Step 1 File 10 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact clean baseline `f262564e5cc575de25d301bd6dd452fbd00d3434`, existing V16 resource, JDK25 and offline reactor. Existing POM, 28 production SQL, permissions, AS/PS boot, dispatcher and recovery API are frozen. Do not run any actual PG/operator fixture, app, network listener, collector or browser.

CLAR-001. Context-empty admission uses the existing tenant/app fence without waiting. An unavailable scope cannot yield an eligible historical record. This preserves ordinary append for that case.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Capture original first durable Agent input

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Committed V16, existing verifier, exact primary and canonical material.
- Baseline state: Inert V16 with explicit readiness and no startup caller.
- Observable outcome: One optional first-cut row atomic with the original native append when explicitly enabled on admitted source.
- End state: Exact ten paths, default false, source evidence only.
- Test-first gate: Required. Exact typed A assertion precedes B.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 10, Rule 11.
- Ordered files: Files 1–10 below.

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java`

- Purpose: Focused finite capture regression.
- Symbols: AdkCheckpointCaptureTest and exact admission and transaction fixtures.
- Repository evidence: Existing native codec and deterministic JDBC tests.
- Dependencies and consumers: JUnit and existing Agent runtime classes with no network or actual DataSource.
- Why now: Test-first regression before final capture implementation.
- Contract/signature changes: Finite fixture for optional binding and current Session append.
- Input/output and state mapping: Use the real ADK Content and durable decoded event, fixed claims, exact fake JDBC transaction.
- Error and edge behavior: Wrong role/content/action/history, disabled, duplicate and admitted insert failure.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 1 exact type, Rule 2 boundary assertions, Rule 5 existing testing APIs, Rule 11 current AR.
- Implementation pseudocode:

```java
// A compiles with the smallest typed admission seam and intentionally misses the case.
assertTrue(capture.accepts(originalUserEvent, durableDecodedEvent));
assertFalse(capture.accepts(otherContent, durableDecodedEvent));
// B finite SQL mock verifies event insert, Session revision, checkpoint and final fence share one transaction.
// A write fault must roll back and complete no model-facing Session append.
```

- Verification contribution: R1/G1/P1.
- After this file: Focused finite capture regression is present at the next required gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`

- Purpose: Single fenced checkpoint INSERT in current JDBC adapter.
- Symbols: JdbcAdkSessionRepository#tryLockScopeForCheckpoint and #insertFirstInputCheckpoint.
- Repository evidence: Current Run/Session owner predicates, lockScope, event insert and updateSession.
- Dependencies and consumers: Current lease-scoped Session service, existing JDBC transaction and V16.
- Why now: Define exact persistence binding before the Session caller.
- Contract/signature changes: One optional scope test/lock and one INSERT method.
- Input/output and state mapping: Use existing app scope lock key, canonical immutable digests and complete owner/event/Run predicates.
- Error and edge behavior: No extra work on disabled or out-of-scope path. Admitted write conflict/zero rows fails closed..
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 2 exact persistent binding, Rule 5 current Spring/JDK, Rule 10 primary occurred instant, Rule 11 existing AR JDBC.
- Implementation pseudocode:

```sql
SELECT pg_try_advisory_xact_lock(hashtextextended(existing_tenant_app_key, 0));
INSERT INTO tianji_agent.agent_checkpoint (typed_V16_columns)
SELECT bound_owner_run_event_provenance, '1.13.0', 'FIRST_DURABLE_INPUT_V1', '[]'::jsonb, clock_timestamp()
FROM original_owned_run JOIN its_published_definition_version
WHERE current_run_fence_and_original_revision_match AND no_earlier_budget_reservations;
-- Bind the just-inserted event through the existing full-owner Session-event FK.
-- Require one row for an already admitted attempt. Do not add RETURNING, UPSERT or a checkpoint read.
```

- Verification contribution: R1/G1/P1.
- After this file: Single fenced checkpoint INSERT in current JDBC adapter is present at the next required gate.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`

- Purpose: Add optional bound first-input capture to current Session append.
- Symbols: PostgresAdkSessionService#withLease, #appendEvent, #matchesFirstInput and FirstInputCheckpointBO.
- Repository evidence: Current withLease and durable append already fence Run and Session in one physical primary transaction.
- Dependencies and consumers: Existing caller AdkAgentRunExecutor and JDBC repository.
- Why now: Add exact capture boundary before normal executor wiring.
- Contract/signature changes: Immutable opt-in bound input, preserving older withLease path.
- Input/output and state mapping: Bind the original and decoded native event, local/shared empty state, exact first native counters and complete owner.
- Error and edge behavior: Duplicate and unavailable nonblocking scope skip optional capture. A selected insert fault rolls back existing append..
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 1 immutable internal data, Rule 2 fail-closed identities, Rule 3 redacted immutable binding, Rule 5 existing APIs, Rule 11 current native adapter.
- Implementation pseudocode:

```java
// Keep the existing lock order and duplicate return.
var admission = optionalBinding.matches(originalEvent, durableEvent) && firstEmptySession();
if (admission) admission = tryExistingScopeFenceAndCheckEmpty();
// Insert native event and update native Session revision on the existing write transaction.
if (admission) requireOneCheckpointInsert();
requireLiveFences(sessionId, lease);
```

- Verification contribution: R1/G1/P1.
- After this file: Add optional bound first-input capture to current Session append is present at the next required gate.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: Admit exact original run and input.
- Symbols: AdkAgentRunExecutor#forRuntime, private canonical constructor, #executeScoped.
- Repository evidence: Existing exact claim, verified execution material, compiled root and same DataSource.
- Dependencies and consumers: Current runtime bean and existing internal construction paths.
- Why now: Pass actual original Content and verified material to optional Session adapter.
- Contract/signature changes: Expose one trusted static forRuntime(..., boolean checkpointCaptureEnabled) factory. Private canonical constructor adds the flag. All eight existing public constructors keep their signatures and pass false..
- Input/output and state mapping: Enabled factory verifies pinned ADK package metadata and V16 via the private constructor. Bind only admitted actual CONFIG leaf input..
- Error and edge behavior: No product resume or additional authority check; mismatched or non-admitted scope preserves original runner..
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 1 existing executor, Rule 2 exact current source, Rule 3 immutable binding, Rule 5 existing JDK/ADK, Rule 11 current AR.
- Implementation pseudocode:

```java
if (checkpointCaptureEnabled) {
    requireExpectedAdkPackageVersion();
    new AgentStorageVerifier(sessionDataSource).verifyCheckpointCapture();
}
// Existing CLAIM and requireGovernedTools are unchanged.
var original = Content.fromParts(assembledParts);
var sessionAdapter = candidate ? boundWithCapture(lease, original, executionMaterial) : sessions.withLease(lease);
runner.runAsync(userId, sessionId, original, config);
```

- Verification contribution: R1/G1/P1.
- After this file: Admit exact original run and input is present at the next required gate.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`

- Purpose: Explicit runtime option and V16 startup validation.
- Symbols: TianjiAgentRuntimeAutoConfiguration#tianjiAdkAgentRunExecutor.
- Repository evidence: Current host factory, same DataSource and existing context/tool flags.
- Dependencies and consumers: Current Environment and executor, no new bean.
- Why now: Wire optional flag only once actual capture behavior exists.
- Contract/signature changes: Read stellar.tianji.agent.recovery.checkpoint-capture.enabled as Boolean default false.
- Input/output and state mapping: Pass exact boolean to the trusted static forRuntime factory and keep other gates independent.
- Error and edge behavior: Missing property uses old path; explicit true fails before use if package/schema is invalid.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 2 valid host option, Rule 4 reuse current factory, Rule 5 existing Environment, Rule 7 default shape, Rule 11 same runtime.
- Implementation pseudocode:

```java
boolean capture = environment.getProperty("stellar.tianji.agent.recovery.checkpoint-capture.enabled", Boolean.class, false);
// Keep existing DataSource, authorization and context/tool validation.
// Preserve ordinary base readiness when capture is false; explicit capture verifies its own prerequisite.
return AdkAgentRunExecutor.forRuntime(existingArguments, capture);
```

- Verification contribution: R1/G1/P1.
- After this file: Explicit runtime option and V16 startup validation is present at the next required gate.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Packaged default false.
- Symbols: AS application.yml exact default.
- Repository evidence: Existing stellar.tianji.agent subtree and explicit default-false recovery diagnostics.
- Dependencies and consumers: Current AR Environment reads this property when runtime already enabled.
- Why now: Publish disabled default after exact factory is present.
- Contract/signature changes: One explicit stellar.tianji.agent.recovery.checkpoint-capture.enabled=false under existing Agent root.
- Input/output and state mapping: Boot continues to use existing profiles and disabled Flyway.
- Error and edge behavior: No other configuration or production enablement.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 7 one false authoritative packaged default and Rule 11 existing AS host.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    agent:
      recovery:
        checkpoint-capture:
          enabled: false
# Keep this only under the existing Agent tree.
# Verify the exact packaged resource and unchanged boot defaults.
```

- Verification contribution: R1/G1/P1.
- After this file: Packaged default false is present at the next required gate.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: Opt-in actual PostgreSQL and rollback fixture.
- Symbols: AgentCheckpointCapturePostgresTest existing executor and V16 fault fixture.
- Repository evidence: Existing bounded explicit loopback PG probes, actual runtime and verifier.
- Dependencies and consumers: Isolated PG operator only, separate setup/role, Fake model, no service listener.
- Why now: Compile fixture after actual capture and host shape are available.
- Contract/signature changes: One independently authorized test fixture covering both codecs and the admitted write fault.
- Input/output and state mapping: Exact V15 then V16, role inserts checkpoint only, observe via setup and compare native first event.
- Error and edge behavior: After successful readiness revoke only checkpoint INSERT to trigger actual fault. Require atomic rollback and zero model calls..
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 1 exact probe type, Rule 2 exact isolated gate, Rule 5 existing JDK/PG utilities, Rule 10 actual UTC timestamp, Rule 11 AR owner.
- Implementation pseudocode:

```java
// Operator only. Require separate explicit opt-in and exact disposable V16 loopback primary.
for (boolean typedWrites : List.of(false, true)) {
    verifyAndRunNormalExecutorWithOneTextAndFake(typedWrites);
    assertOneExactCheckpointAndOriginalNativeInput();
    revokeOnlyCheckpointInsertAfterReadiness();
    assertFailedRunWithNoModelCallAndNoDurableNativeInputOrCheckpoint();
}
```

- Verification contribution: P1/S1.
- After this file: Opt-in actual PostgreSQL and rollback fixture is present at the next required gate.

#### File 8 — `CREATE docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md`

- Purpose: Effective design.
- Symbols: REQ-001 through REQ-005 and TEST-001 through TEST-005.
- Repository evidence: Master T4-03, committed V16 and actual existing normal path.
- Dependencies and consumers: Maintainer and Plan.
- Why now: Keep the actual effective reviewed Spec with the exact source commit.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact default-off behavior and distinct source/runtime evidence.
- Error and edge behavior: Drifted path, symbol or requirement stops staging.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 11 documents current ownership and source scope.
- Implementation pseudocode:

```text
Copy the strict Spec for the actual baseline and source scope.
Confirm all five requirements and bounded first-input owner/fault behavior.
Verify prerequisite links and the exact property from source.
Retain independent actual PG and product resume conditions.
```

- Verification contribution: P1/S1.
- After this file: Effective design is present at the next required gate.

#### File 9 — `CREATE docs/egon/plan/2026-11-06-01-43-agent-checkpoint-capture.md`

- Purpose: One source Step.
- Symbols: Step 1, R1/G1/P1/S1 and compile-only operator fixture.
- Repository evidence: Current offline source reactor and exact writer Spec.
- Dependencies and consumers: Source maintainer.
- Why now: Keep the exact sequence with the tested source.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Fixed finite selectors, actual package verification and unchanged SQL.
- Error and edge behavior: Do not stage if raw evidence or exact scope differs.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 11 exact owning source and code boundaries.
- Implementation pseudocode:

```text
Copy the strict one-Step Plan.
Confirm the primary Spec hash and exact Service baseline used to execute the gates.
Verify the file list, finite selector and separate operator fixture.
Keep the actual source/package proof distinct from runtime acceptance.
```

- Verification contribution: P1/S1.
- After this file: One source Step is present at the next required gate.

#### File 10 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-capture.md`

- Purpose: Actual results and runtime boundary.
- Symbols: Actual R1/G1/P1/S1 observations.
- Repository evidence: Fresh owning Surefire XML and actual built AS/PS archives.
- Dependencies and consumers: Reviewer and operator.
- Why now: Fill only after observing the finite and package gates.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact results and baseline, actual defaults and scope, compiled I boundary.
- Error and edge behavior: Compile-only is not a runtime pass.
- Standards impact: MC-SCOPE-001 and MC-TEST-001. Use current Agent AR/AS ownership.
- Literal rule enforcement: Rule 11 retain distinct execution gates.
- Implementation pseudocode:

```text
Record the intended actual RED and fresh finite GREEN totals.
Record archive/config and permission proof, and unchanged SQL bytes.
Record the compiled isolated fixture and absence of a live test report.
State restart, native resume and product activation separately.
```

- Verification contribution: P1/S1.
- After this file: Actual results and runtime boundary is present at the next required gate.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Execute §8 by exact R1/G1/P1/S1 phases.
- Expected result: One intended assertion on typed A; fresh nonzero exact finite classes, zero final failures/errors/skips; actual packaged false, unchanged 28 SQL and protected declarations; operator class compiled with no report.
- Failure returns to: First affected source file, fixture or source lock. Re-run any affected phase before commit.
- Completion criteria: All finite/package gates and strict docs, exact paths, no runtime or migration execution.
- Rollback: Disable optional capture or revert the source commit before enablement. Retain immutable V16 and recorded rows.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md`, `docs/egon/plan/2026-11-06-01-43-agent-checkpoint-capture.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-capture.md`.
- Commit: `feat(agent): capture first durable input checkpoint`.

## 8. Test, Validation, and Quality Gates

```sh
capture_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
capture_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
capture_base=f262564e5cc575de25d301bd6dd452fbd00d3434
test "$(git rev-parse --verify HEAD)" = "$capture_base"
capture_red='AdkCheckpointCaptureTest#accepts_only_exact_first_durable_user_input'
capture_core=AdkCheckpointCaptureTest,AdkSessionEventCodecTest,AgentCheckpointStorageVerifierTest,TianjiAgentRuntimeAutoConfigurationTest,AdkExecutionTransactionBoundaryTest
capture_finite() {
  JAVA_HOME="$capture_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$capture_settings" -s "$capture_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
capture_finite "$capture_red"
capture_finite "$capture_core"
JAVA_HOME="$capture_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
  ./scripts/verify-source-reactor.sh -gs "$capture_settings" -s "$capture_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$capture_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-03-agent-checkpoint-capture \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$capture_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-01-43-agent-checkpoint-capture.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and boundary |
| --- | --- |
| R1 | Exact selected method on typed A. Fresh one failed assertion, no errors/skips. |
| G1 | Exact five finite owner classes, 25 expected with zero final failures/errors/skips. Existing runtime constructor-shape assertion still passes. No I. |
| P1 | Clean permission-producer repeats counts. Byte-check actual AS YAML explicit false, V16 in AR and nested AS, 28 SQL exact, AS/PS PaC same content excluding build identity. Operator fixture class and exact gate exist, no execution report. |
| S1 | Strict docs, ten paths, no unrelated config or production app/package changes. |

Separate operator-only I selector `AgentCheckpointCapturePostgresTest#captures_first_input_before_budget_and_model_and_rolls_back_if_checkpoint_insert_is_denied`. The operator supplies an independently approved fresh loopback primary with database name `tianji_it_checkpoint_...`, installed exact pgvector version and separate pre-provisioned limited login. The fixture provisions exact V15 then V16 only there and verifies earlier history. Use these isolated properties with the existing reactor and exact selector after approval:

```sh
-Dtest='AgentCheckpointCapturePostgresTest#captures_first_input_before_budget_and_model_and_rolls_back_if_checkpoint_insert_is_denied'
-Dtianji.postgres.checkpoint.capture=true
-Dtianji.postgres.checkpoint.capture.url="$approved_disposable_loopback_url"
-Dtianji.postgres.checkpoint.capture.runtime-role="$approved_limited_role"
-Dtianji.postgres.checkpoint.capture.vector.version="$verified_vector_version"
```

Require a non-skipped successful report proving both codecs and same-transaction insert failure rollback. The source gate does not call this selector. Actual forced stop/restart and native resume remain independent.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration additions or edits. Verify existing V16 independently in the explicit true branch. Maintain packaged false and same production startup. Activate only after actual isolated I role and runtime behavior. Existing native V9 and usage V15 fixtures retain exact histories.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step and files | Gates |
| --- | --- | --- |
| REQ-001 | Step 1 Files 4–6 | G1/P1 |
| REQ-002 | Step 1 Files 1–4 | R1/G1 and operator I |
| REQ-003 | Step 1 Files 1–4 and 7 | G1/P1 and operator I |
| REQ-004 | Step 1 Files 1–7 | G1/P1/S1 |
| REQ-005 | Step 1 Files 1–10 | R1/G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Real PostgreSQL transactions, stop/restart, native resume, current resume authorization, product Run takeover and full S11 remain separate. The source Step authorizes no application launch, DB application, migration repair or production enablement.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

One historical first-input cut for the existing Agent runtime, with a real default-off path and owner fence.

### 12.2 Spec consistency

No independent authority, DDL, public API or resume path in either artifact. Exact operating constraints shared.

### 12.3 Repository executability

Use actual owning finite classes, existing Maven and the previous V16 source lock.

### 12.4 Test and release completeness

R1/G1/P1/S1 are actual source/package only. PG fixture has distinct operator activation and report.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/§7/§8 | Same existing AR/AS infrastructure and owning storage | None |
| MC-REUSE-001 | Applicable | PASS | §4.7/§7/§8 | Current leases, execution material, Session transaction and V16 | None |
| MC-DEP-001 | Applicable | PASS | §4.7/§7/§8 | No added dependency or build | None |
| MC-NAME-001 | Applicable | PASS | §4.7/§7/§8 | Existing types and one immutable internal capture value | None |
| MC-VALID-001 | Applicable | PASS | §4.7/§7/§8 | Exact original native input and fenced provenance | None |
| MC-MODEL-001 | Applicable | PASS | §4.7/§7/§8 | Same original Run and native Session identities | None |
| MC-CONVERT-001 | Not applicable | N/A | §4.7/§7/§8 | No API or persistence object mapping conversion | None |
| MC-LOG-001 | Applicable | PASS | §4.7/§7/§8 | Fixed sanitized failure; raw input stays private | None |
| MC-BEAN-001 | Applicable | PASS | §4.7/§7/§8 | Existing runtime factory owns one default-off decision | None |
| MC-UTIL-001 | Applicable | PASS | §4.7/§7/§8 | Current JDBC, JDK and pinned ADK APIs | None |
| MC-JSON-001 | Not applicable | N/A | §4.7/§7/§8 | No public JSON or mapper change | None |
| MC-TIME-001 | Applicable | PASS | §4.7/§7/§8 | UTC capture instant and database fences | None |
| MC-CONFIG-001 | Applicable | PASS | §4.7/§7/§8 | One explicit default-false AS property | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.7/§7/§8 | One fixed technical admission and no variable business behavior | None |
| MC-SCOPE-001 | Applicable | PASS | §4.7/§7/§8 | No Run transition, resume, authority or schema change | None |
| MC-TEST-001 | Applicable | PASS | §4.7/§7/§8 | Exact finite gates and independently gated operator fixture | None |
| MC-BLOCKER-001 | Applicable | PASS | §4.7/§7/§8 | Runtime evidence and native resume remain separately gated | None |

### 12.6 Final verdict

PASS — Ready for user review

Actual database and application acceptance remain independent.
