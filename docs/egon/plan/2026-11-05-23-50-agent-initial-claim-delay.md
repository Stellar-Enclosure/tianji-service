# T4-02 Agent 首次认领时延实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-50-agent-initial-claim-delay.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 23:50 Asia/Shanghai |
| Updated | 2026-11-06 00:00 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | 现有 Agent 首次认领时间观测和默认关闭的 AS 选项 |
| Source Requirement | 主计划 §19 T4-02 / S12 运行 SLA 样本 |
| Baseline Revision | Service 410874c58d88b766e27706d80e639ebf2d734f4d，B1 后独立 AR/AS source |
| Implements Spec | [Agent initial claim delay](../spec/2026-11-05-23-44-agent-initial-claim-delay.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 00:00 Asia/Shanghai SHA-256 50ef9d0b5e20edfd47b15981ad235caf779f3414bb8cf99514e36fd36da0e781 |
| Effective Specs | [Agent initial claim delay](../spec/2026-11-05-23-44-agent-initial-claim-delay.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 Step 利用已提交首次 Agent claim 的原事件和创建时间记录可选 Timer。先锁定 B1 交付后的干净服务源，再执行两个断言 RED、四个定向有限套件和 AS 实际制品验证。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Agent initial claim delay](../spec/2026-11-05-23-44-agent-initial-claim-delay.md)，REQ-001–005，METRIC-001，TIME-001/002。

### 2.2 Effective Spec set

Only the reviewed primary Spec governs this metric. The Process migration B1 commit is a sequential source predecessor with no ownership change in this Step.

### 2.3 Superseded or excluded content

No release-migration, PS, Worker, Tool or provider scope. No live SLA, active backlog or full S12 claim.

## 3. Effective Requirements and Acceptance

| Requirement | Implementation | Acceptance |
| --- | --- | --- |
| REQ-001 | Use existing first-claim event clock and creation | Same original timestamps and unchanged SQL |
| REQ-002 | Observe after actual same-source commit | Winning CAS only, no rollback/later attempt |
| REQ-003 | One optional fixed Timer | No app tags, safe interval and optional failure |
| REQ-004 | Default off and compatible existing factories | Actual AS YAML false, old V15/publisher/resource scope unchanged |
| REQ-005 | Scoped finite/packaging evidence | 21 tests and exact artifact scope, real runtime separately gated |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定 B1 后干净 HEAD、Spec/Plan 和 scratch source。先 R1 再 A/R2。应用 B 后依次 G1、P1、S1。

### 4.2 Test-first strategy

| Gate | Exact source state | Observation |
| --- | --- | --- |
| R1 | New AR test red only | Missing exact factory assert |
| R2 | Typed AR test and AgentLedgerService A | No elapsed sample assert after same-source outer commit |
| G1 | All five B paths | Four finite suites pass 21 |
| P1/S1 | Same B source, clean AS/PS permission-producer profile and exact stage | AS actual YAML false and cmp, protected manifest unchanged |

### 4.3 Sequential and parallel boundaries

B1 commit precedes metrics source writes. No PS overlap. R1 precedes A; R2 precedes B. AS config/test and YAML are applied together after AR positive behavior is specified.

### 4.4 Commit boundaries

One Service commit. Eight exact paths only.

### 4.5 Spec Simplicity and Implementation-necessity Audit

One existing claim CAS and an existing status event give the required source times. One optional Duration observer preserves AR dependency direction. No query, meter service or second host.

### 4.6 Change-unit Dependency Matrix

| Unit | Predecessor | Result |
| --- | --- | --- |
| R1 | Clean B1 HEAD and reviewed lock | Exact reflective failure |
| A and R2 | R1 | Compiling known business flow, one expected missing metric |
| B/G1 | R2 | Exact committed observation and configuration proof |
| P1/S1 | G1 | Actual AS package and eight-path commit review |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR ownership and AS storage host | §7 same authority | No added layer, existing host factory | Step 1 MC-ARCH-001 |
| Reuse/dependency | Existing JDBC transaction and AS Actuator | §6 no AR Micrometer dependency | Duration-only injection | Step 1 MC-REUSE-001/MC-DEP-001 |
| Model/validation | Current run DTO and event DTO | §7 exact first winner | Same immutable Instant and actual synchronization | Step 1 MC-VALID-001/MC-TIME-001 |
| Bean/logging/config | Existing AS factory and local technical logging | §7/16 optional fixed Recorder | Explicit false, safe fixed warning | Step 1 MC-LOG-001/MC-CONFIG-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Claim timestamp | Existing appendStatusEvent event and V1 createdAt | Same existing clock | Fits | Reuse | No SQL | Step 1 MC-TIME-001 |
| Commit | Existing Spring transaction | afterCompletion STATUS_COMMITTED | Fits | Reuse | One local callback | Step 1 MC-VALID-001 |
| Meter | Existing AS MeterRegistry/Actuator | Micrometer in AS | Fits | Reuse | No dependency | Step 1 MC-DEP-001 |

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
| Rule 3 | §6.2/§10 | Immutable run and event DTOs | File 2 | Reuse existing event Instant and Duration only | G1 | Step 1 | PASS |
| Rule 4 | §6.2/§8 | Current Bean and technical logging | Files 2/4/6 | Preserve ownership and fixed diagnostics | G1 | Step 1 | PASS |
| Rule 5 | §6.2 | JDK and current framework | Files 1–6 | Existing APIs | G1 | Step 1 | PASS |
| Rule 6 | §6.2/§10 | Public receipt unchanged | No affected JSON | No change | S1 | Step 1 | N/A |
| Rule 7 | §6.2/§16 | Single AS YAML | Files 3–5 | False by default | G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/§13 | Simple observation | Files 2/4 | Direct optional callback | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing AgentDB instants | Files 1–4 | No additional clock read or rounding | G1 | Step 1 | PASS |
| Rule 11 | §7/§11 | Existing command owner | All | No schema/API expansion | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.createdAt and status AgentEventDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: same AgentDB clock | Not applicable: no display | Actual current owner claim and original event | Existing DB and microsecond normalisation | Existing SQL and event wire unchanged | Step 1 TEST-001/002 |
| TIME-002 | Initial claim delay | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: Registry unit | Zero valid; negative/overflow omitted | Original microsecond inputs; nanosecond representable | New optional Timer only | Step 1 TEST-001/003 |

## 5. Change File Tree

| Action | Exact path | Responsibility | Order |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentInitialClaimTimingTest.java` | Lock exact first claim observation and commit behavior. | Step 1 File 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | Use the existing claim event time and attach best-effort post-commit observation. | Step 1 File 2 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java` | Verify default-off and coexistence on existing factory. | Step 1 File 3 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java` | Wire the existing AS MeterRegistry only for enabled default storage factory. | Step 1 File 4 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Distribute explicit default-off flag under independent observation section. | Step 1 File 5 |
| CREATE | `docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md` | Commit the reviewed metrics behavior and B1 baseline lock. | Step 1 File 6 |
| CREATE | `docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md` | Commit the reviewed exact finite and package sequence. | Step 1 File 7 |
| CREATE | `docs/egon/evidence/2026-11-05-agent-initial-claim-delay.md` | Record actual repo gate outputs and residual verification. | Step 1 File 8 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Service `410874c58d88b766e27706d80e639ebf2d734f4d`, clean after migration-preflight B1. Earlier AR/AS drafts were prepared against `d9bec9c34bdbaa337cb5bd8eb776e2383bac97c6`. Verify the intervening commit touches no five metric source/config paths, and compare those baseline blobs before copy.

### 6.2 Build, test, and environment prerequisites

JDK 25 and existing offline wrapper/cache and settings. Existing five local repos with approved source heads. Run no Postgres, IT, app, browser or actual exporter.

### 6.3 Immutable constraints and approved decisions

Source only. No touched PS path, SQL, dependency or permissions. AS actual permission manifest is compared against the last accepted AS producer with identity fields excluded only when the old/new identity is validated.

### 6.4 Plan Clarifications

Use root scratch exact `source-draft/{red,a,b}` phases. Keep storage model-usage YAML exact by placing the new flag under run-observation. Source scopes are serial, with separate commits.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Observe committed initial Agent claim delay

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: B1 source lock, reviewed Spec/Plan, current owning build environment.
- Baseline state: Existing first claim and durable status event, no observation metric.
- Observable outcome: One optional fixed Timer from first committed claim using original source times.
- End state: Eight-path Agent metrics commit, zero PS scope.
- Test-first gate: Required. R1 red baseline and R2 typed A precede B.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentInitialClaimTimingTest.java`

- Purpose: Lock exact first claim observation and commit behavior.
- Symbols: initial_claim_observation_factory_exists, observes_first_claim_using_existing_clock_after_outer_commit and three B finite tests
- Repository evidence: Current claim/clock/SQL and existing finite JDBC Mockito examples.
- Dependencies and consumers: Existing transaction manager, mock Connection/JdbcTemplate and A or B AR ledger.
- Why now: R1 checks reflective missing factory, R2 checks exact outcome after typed A.
- Contract/signature changes: Finite test only.
- Input/output and state mapping: One owner row, winning CAS, one existing DB clock and status event, sample only after outer commit.
- Error and edge behavior: Later attempt, zero, negative/overflow, failed CAS, event write, rollback, commit failure and observer failure.
- Standards impact: MC-VALID-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> AgentLedgerService.class.getMethod("withInitialClaimTiming",
        DataSource.class, AgentTerminalEventPublisher.class, Consumer.class));
outer.executeWithoutResult(status -> {
    assertEquals(RUNNING, fixture.claim().run().status());
    assertTrue(observed.isEmpty());
});
assertEquals(List.of(Duration.ofSeconds(8).plusNanos(1000)), observed);
```

- Verification contribution: R1, R2, G1 and P1.
- After this file: R1 baseline fails reflection; R2 typed A fails sample assertion; B verifies full cases.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: Use the existing claim event time and attach best-effort post-commit observation.
- Symbols: withInitialClaimTiming, withModelUsage overload, claim, appendStatusEvent, observeInitialClaim
- Repository evidence: Existing owner CAS/attempt increment, already stored event Instant and exact transaction.
- Dependencies and consumers: AS existing factory, previous callers and AR tests.
- Why now: A defines exact signatures and no-op observer, B only after R2.
- Contract/signature changes: Original constructors and factories preserved. Add Duration-only trusted host seam.
- Input/output and state mapping: Owned first attempt, event persisted, exact source bound, afterCompletion STATUS_COMMITTED.
- Error and edge behavior: Invalid/nonfirst/aborted claims omit sample. Catch observer RuntimeException and emit one fixed warning per ledger.
- Standards impact: MC-REUSE-001, MC-LOG-001, MC-TIME-001, MC-VALID-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
Instant claimedAt = appendStatusEvent(actor, previous, claimed, QUEUED, RUNNING, null);
var result = new ClaimedAgentRun(claimed.run(), winningLease);
observeInitialClaim(previous.run(), claimed.run(), claimedAt); // A no-op
return result;
// B verifies attempt 0 to 1, same creation and current source transaction.
registerSynchronization(afterCompletion(status -> {
    if (status == STATUS_COMMITTED) initialClaimTiming.accept(elapsed);
}));
```

- Verification contribution: R2, G1 and P1.
- After this file: One old status event timestamp is reused, no added query or event.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java`

- Purpose: Verify default-off and coexistence on existing factory.
- Symbols: initialClaimTimingIsDefaultOffAndUsesOneFixedMeter and verifiedOptInOnly
- Repository evidence: Current AS finite configuration tests and existing SimpleMeterRegistry usages.
- Dependencies and consumers: Exact B storage factory and current AS application.yml.
- Why now: Apply after R2 with B signatures, preserve existing model defaults test.
- Contract/signature changes: No production API change.
- Input/output and state mapping: False/absent, flag-only still storage-off, enabled one fixed no-tag Timer; V15 and publisher coexist.
- Error and edge behavior: Unknown/absent Registry never manufactures a zero value; no actual DB.
- Standards impact: MC-CONFIG-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertFlagOnlyDoesNotCreateLedger();
assertPackagedYamlValue("run-observation.initial-claim-metrics-enabled", false);
var observer = extractEnabledFactoryObserver();
observer.accept(Duration.ofNanos(1500));
assertOneUntaggedTimerWithExactCountAndTime();
```

- Verification contribution: G1 and P1.
- After this file: Original model-usage subtree and exact publisher validation still pass.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java`

- Purpose: Wire the existing AS MeterRegistry only for enabled default storage factory.
- Symbols: INITIAL_CLAIM_METRICS, INITIAL_CLAIM_DELAY, tianjiAgentServerLedgerService
- Repository evidence: AS Actuator and existing local metrics patterns, current optional storage/publisher and V15 wiring.
- Dependencies and consumers: AR trusted host factory and existing MeterRegistry bean.
- Why now: B after AR callback and RED gates.
- Contract/signature changes: Only add ObjectProvider<MeterRegistry> to internal bean factory.
- Input/output and state mapping: False produces original branches. True creates Duration consumer and passes existing model/publisher inputs.
- Error and edge behavior: getIfUnique yields absent sample; AR callback isolates RuntimeException. Custom ledger remains its owner.
- Standards impact: MC-BEAN-001, MC-CONFIG-001, MC-REUSE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```java
Consumer<Duration> timing = enabled ? elapsed -> {
    MeterRegistry registry = registries.getIfUnique();
    if (registry != null) Timer.builder("tianji.agent.run.initial.claim.delay").register(registry).record(elapsed);
} : null;
return exactExistingStorageBranch(dataSource, publisher, modelUsageRoutes, timing);
```

- Verification contribution: G1 and P1.
- After this file: Existing storage owner and model-usage verification remain authoritative.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Distribute explicit default-off flag under independent observation section.
- Symbols: stellar.tianji.agent.run-observation.initial-claim-metrics-enabled
- Repository evidence: Existing terminal/recovery/storage sections and exact current model-usage subtree test.
- Dependencies and consumers: Current Boot property and AS factory.
- Why now: Complete B default policy before G1 and P1.
- Contract/signature changes: One new boolean setting, no added prerequisite.
- Input/output and state mapping: Absent or false means no observer.
- Error and edge behavior: Keep existing storage keys unchanged and no deployed config.
- Standards impact: MC-CONFIG-001.
- Literal rule enforcement: Rule 7 and Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    agent:
      run-observation:
        initial-claim-metrics-enabled: false # Optional Timer and independent from existing storage model usage values
```

- Verification contribution: G1 and P1.
- After this file: Actual packaged source will expose false by exact YAML byte comparison.

#### File 6 — `CREATE docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md`

- Purpose: Commit the reviewed metrics behavior and B1 baseline lock.
- Symbols: REQ-001–005, METRIC-001, TIME-001/002
- Repository evidence: Accepted same-source first-claim design and exact source baseline.
- Dependencies and consumers: Primary normative Spec.
- Why now: Freeze and review before any source execution.
- Contract/signature changes: No runtime contract change.
- Input/output and state mapping: Accepted source-only scope and limited measurements.
- Error and edge behavior: Stop if baseline or reviewed bytes drift.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 10 and Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md
cp -n "$CLAIM_REVIEWED_SPEC" docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md
cmp "$CLAIM_REVIEWED_SPEC" docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md
```

- Verification contribution: S1.
- After this file: Exact reviewed Spec available.

#### File 7 — `CREATE docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md`

- Purpose: Commit the reviewed exact finite and package sequence.
- Symbols: R1/R2/G1/P1/S1
- Repository evidence: Current exact phase drafts and source reactor.
- Dependencies and consumers: Owns this one Step and eight-path commit.
- Why now: Freeze reviewed source order.
- Contract/signature changes: No runtime launch.
- Input/output and state mapping: Exact paths and gate prerequisites.
- Error and edge behavior: Stop before staging on any failed gate.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md
cp -n "$CLAIM_REVIEWED_PLAN" docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md
cmp "$CLAIM_REVIEWED_PLAN" docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md
```

- Verification contribution: S1.
- After this file: Canonical Plan available.

#### File 8 — `CREATE docs/egon/evidence/2026-11-05-agent-initial-claim-delay.md`

- Purpose: Record actual repo gate outputs and residual verification.
- Symbols: Owning reports, actual AS executable and scope evidence
- Repository evidence: Existing AS and Task metrics evidence convention.
- Dependencies and consumers: Maintainers and operator validation.
- Why now: After actual finite and package proof.
- Contract/signature changes: No new data/source contract.
- Input/output and state mapping: Record actual assertion sites, counts, archive/YAML/PaC evidence and scope.
- Error and edge behavior: No unverified runtime and SLA claim.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record actual expected assertion failures, four green suites and zero skip/errors.
Record exact AS archive hash and package/source application.yml byte equality and protected manifest comparison.
Record unchanged historical SQL, exclusions and real AgentDB/exporter/S12 as separate gates.
```

- Verification contribution: S1.
- After this file: One auditable source commit ready.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: R1/R2/G1/P1/S1 in §8.
- Expected result: Two actual intended assertion failures, 21 exact tests each green run, AS actual YAML byte equality and false, unchanged permission content and exact scope.
- Failure returns to: R1/R2 Files 1/2, G1 Files 1–5, P1 exact AS package/source proof, S1 exact scope.
- Completion criteria: Actual owning tests and AS executable locked, unchanged PS/SQL/POM and one repository commit.
- Rollback: Set exact config false or revert the focused commit if authorized.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentInitialClaimTimingTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md`, `docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md`, `docs/egon/evidence/2026-11-05-agent-initial-claim-delay.md`.
- Commit: `feat(agent): observe committed initial claim delay`.

## 8. Test, Validation, and Quality Gates

Keep separate fresh owning Surefire XML for each phase. The exact adjacent tests are finite and have no PostgreSQL, listener or provider startup.

```sh
claim_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/agent-initial-claim-delay
claim_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
claim_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
claim_base=410874c58d88b766e27706d80e639ebf2d734f4d
test "$(git rev-parse --verify HEAD)" = "$claim_base"
claim_core=AgentInitialClaimTimingTest,AgentModelUsageDiagnosticsTest,AgentRecoveryDispositionEvidenceTest,AgentModelUsageStoreTest
claim_finite() {
  JAVA_HOME="$claim_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$claim_settings" -s "$claim_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
claim_finite 'AgentInitialClaimTimingTest#initial_claim_observation_factory_exists'
claim_finite 'AgentInitialClaimTimingTest#observes_first_claim_using_existing_clock_after_outer_commit'
claim_finite "$claim_core"
JAVA_HOME="$claim_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$claim_settings" -s "$claim_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$claim_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-agent-initial-claim-delay-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$claim_core"
claim_as=stellar-enclosure-tianji-agent-server
claim_jar="$claim_as/target/stellar-enclosure-tianji-agent-server-exec.jar"
unzip -p "$claim_jar" BOOT-INF/classes/application.yml > "$claim_root/actual-as-application.yml"
cmp "$claim_as/src/main/resources/application.yml" "$claim_root/actual-as-application.yml"
rg -U -q '      run-observation:\n        initial-claim-metrics-enabled: false' "$claim_root/actual-as-application.yml"
unzip -p "$claim_jar" META-INF/permissions/manifest-v1.json > "$claim_root/actual-as-manifest.json"
```

Confirm the preceding actual AS resource against its retained SHA and the new archive against its own permission-producer evidence. Only the two verified build identity fields may differ. No registry/exporter run is implied.

```sh
claim_prior=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/migration-preflight-api/root-agent-server-manifest.json
printf '%s  %s\n' d2d122631a6e341385b8f13856c08c6e36a058628ba3b25bef8982eb7ae8d2d9 "$claim_prior" | shasum -a 256 -c -
python3.14 -I -B - "$claim_jar" "$claim_prior" "$claim_root/actual-as-manifest.json" "$claim_base" <<'PY'
import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile

archive, baseline, current = map(Path, sys.argv[1:4])
expected_commit = sys.argv[4]
with archive.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
artifact = json.loads((archive.parent / "permissions/artifact-evidence.json").read_bytes())
assert artifact["producerId"] == "tianji-agent-java" and artifact["artifactDigest"] == digest
with ZipFile(archive) as jar:
    names = jar.namelist()
    assert names.count("BOOT-INF/classes/application.yml") == 1
    assert names.count("META-INF/permissions/manifest-v1.json") == 1
    assert jar.read("META-INF/permissions/manifest-v1.json") == current.read_bytes()
old = json.loads(baseline.read_bytes())
new = json.loads(current.read_bytes())
assert new["artifact"]["commit"] == expected_commit
assert new["artifact"]["buildId"] == "LOCAL_UNCOMMITTED-t4-02-agent-initial-claim-delay-20261105"
for manifest in (old, new):
    manifest["artifact"].pop("commit")
    manifest["artifact"].pop("buildId")
assert old == new
print("Verified exact AS artifact " + digest)
PY
```

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-05-23-44-agent-initial-claim-delay.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-05-23-50-agent-initial-claim-delay.md
git diff --check
git diff --name-only HEAD -- 'stellar-enclosure-tianji-process-server/**' '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

| Gate | Expected actual outcome | Failure |
| --- | --- | --- |
| R1 | Exact one assertion failure due to missing factory, 0 error/skip | Correct R1 scope |
| R2 | Exact one assertion failure because observed list was empty, 0 error/skip | Correct typed A or test |
| G1 | AR claim 4, AS model config 8, AR recovery 5, AR usage 4. No fail/error/skip | Resolve owner source and rerun |
| P1 | Same four finite suites, actual AS archive, flag false and cmp, no permission contents drift | Correct package or scope |
| S1 | Both strict docs, exact eight staged paths, no PS/SQL/POM delta | Repair scope |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or changes to runtime owner authority. Config false stops future samples. Actual setting, exporter and SLA separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step and files | Gate |
| --- | --- | --- |
| REQ-001 | Step 1 Files 1/2 | R2/G1 |
| REQ-002 | Step 1 Files 1/2 | G1 |
| REQ-003 | Step 1 Files 1–4 | G1/P1 |
| REQ-004 | Step 1 Files 3–5 | G1/P1 |
| REQ-005 | Step 1 Files 6–8 | S1 |

## 11. Risks, Blockers, and User Decisions

| Risk | Status | Disposition |
| --- | --- | --- |
| B1 source baseline | Locked clean B1; no AR/AS source drift | Before executing scope, verify only allowed AR/AS edits |
| Registry and actual AgentDB clock | Unverified runtime | Independent environment |
| Active backlog and S12 | Separate | Do not infer from first-claim samples |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Same-source committed first claim. No backlog claim.

### 12.2 Spec consistency

Fixed same source clock, post-commit observer, default off and compatible host.

### 12.3 Repository executability

Existing offline wrapper, explicit four selectors, actual AS package and exact scope.

### 12.4 Test and release completeness

Source tests and artifacts are separate from live runtime and S12.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing AR/AS | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Same SQL, Tx and MeterRegistry | None |
| MC-DEP-001 | Applicable | PASS | §6 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7 | First winning claim and original event time | None |
| MC-MODEL-001 | Applicable | PASS | §7 | Existing immutable Agent DTOs | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/§7 | No DTO/PO mapping | None |
| MC-LOG-001 | Applicable | PASS | §7 | Fixed once diagnostic | None |
| MC-BEAN-001 | Applicable | PASS | §7 | Same factory | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK and existing libraries | None |
| MC-JSON-001 | Not applicable | N/A | §7 | No JSON change | None |
| MC-TIME-001 | Applicable | PASS | §4.9 | Existing AgentDB Instants and Duration | None |
| MC-CONFIG-001 | Applicable | PASS | §7 | One default false | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Simple fixed observer | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | Exact eight paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Source ready for review | Actual I/E/S12 outside Step |

### 12.6 Final verdict

PASS — Ready for user review

当前为仓库外 Plan。实际仓库 RED/GREEN、打包和真实运行边界按本 Plan 记录。
