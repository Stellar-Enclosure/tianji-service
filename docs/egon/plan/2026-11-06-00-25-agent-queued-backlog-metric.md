# T4-02 Agent 有界待认领队列观测实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-00-25-agent-queued-backlog-metric.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 00:25 Asia/Shanghai |
| Updated | 2026-11-06 00:25 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | 一次有界 primary queue count、原调度轮询和默认关闭 gauge |
| Source Requirement | 主计划 §19 T4-02 / S12 队列观测 |
| Baseline Revision | 9a509342767d6b01dfa94e482b530cc69ccc5536 |
| Implements Spec | [Agent queued backlog metric](../spec/2026-11-06-00-24-agent-queued-backlog-metric.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 00:24 Asia/Shanghai SHA-256 101a04ecfec83db240f3e88eeb52882fe22364dce7d1ce4d10d163a6c5af0ed5 |
| Effective Specs | [Agent queued backlog metric](../spec/2026-11-06-00-24-agent-queued-backlog-metric.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 Service Step，保留当前调度权威，增加可选的 capped primary count 和无 scrape I/O 的本地 Gauge。编译型 RED 后执行三个有限套件和实际 AS 包验证。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Agent queued backlog metric](../spec/2026-11-06-00-24-agent-queued-backlog-metric.md)，REQ-001–005，METRIC-001，TIME-001–003。

### 2.2 Effective Spec set

Use the primary Spec only for new behavior. Existing dispatch, authorization, storage and adjacent claim Timer retain authority.

### 2.3 Superseded or excluded content

No PS, schema, grant, runtime startup, exporter configuration or live S12 claim.

## 3. Effective Requirements and Acceptance

| Requirement | Implementation | Acceptance |
| --- | --- | --- |
| REQ-001 | Same source capped scalar | Exact lifecycle predicate and 257 bound |
| REQ-002 | Exact primary read with deadline | Physical read-only, 100 ms and one-second Spring budget |
| REQ-003 | Existing scheduler cadence and fresh cache | 30 sec floor, 60 sec expiry, failure/stop NaN, no scrape query |
| REQ-004 | Independent default false and no authority change | Same dispatch prerequisites, no grant/DDL/extra scheduler |
| REQ-005 | Exact finite and package evidence | 22 tests plus actual AS configuration and PaC comparison |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock current clean HEAD, approved pair and scratch bytes. Apply A for R1, then B for G1 and P1, then exact stage.

### 4.2 Test-first strategy

| Gate | Exact source state | Observation |
| --- | --- | --- |
| R1 | AR typed A reader stub + A test | Existing candidate expects 257, unimplemented returns 0, one assertion |
| G1 | All B code/test/config paths | Three owning/adjacent suites, 22 finite passes |
| P1/S1 | Same B and actual clean producer build | AS default false and byte cmp, semantic permissions unchanged, exact staged paths |

### 4.3 Sequential and parallel boundaries

No actual source writes while S11 or other owner change is in progress. If S11 commits first, re-lock after verifying all eight source paths unchanged. R1 before B.

### 4.4 Commit boundaries

One repository-local logical commit. Eleven exact paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

A single scalar aggregate is necessary to measure the actual lifecycle population. Existing paged grant discovery cannot measure it. Reuse current poll, data source and registry. Add no scheduler or public contract.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Result |
| --- | --- | --- |
| Typed A/R1 | Clean baseline and reviewed Spec | Expected count assertion |
| AR B | R1 | Existing owner bounded technical sample |
| AS B | AR B | Default-off cached gauge and current poll integration |
| G1/P1/S1 | All B | Exact finite/package/scope evidence |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR data source and AS dispatch owner | §7 same owner | Existing layers only | Step 1 MC-ARCH-001 |
| Reuse/dependency | Existing JDBC/Tx, current AS Micrometer | §6 | No new dependency | Step 1 MC-DEP-001 |
| Model/validation | Exact bounded scalar and current lifecycle | §7/10 | Private immutable sample, fail closed | Step 1 MC-VALID-001/MC-MODEL-001 |
| Bean/logging/time/config | Current AS factory and fixed technical warning | §7/16 | Default-off and scoped monotonic measurement | Step 1 MC-LOG-001/MC-TIME-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Queue scope | Existing queuedRuns and V1 dispatch index | Same lifecycle filter | One aggregate needed | Existing owner | One bounded SELECT | Step 1 MC-VALID-001 |
| Database protection | Current primary binding and Tx | Same source | Fits | Reuse | 100ms statement setting | Step 1 MC-REUSE-001 |
| Cadence and metrics | Current successful dispatch and AS MeterRegistry | Existing scheduler | Fits | Reuse | No new thread or dependency | Step 1 MC-DEP-001 |

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
| Rule 3 | §6.2/§10 | Immutable Sample | File 2 | Keep one cached count and monotonic time | G1 | Step 1 | PASS |
| Rule 4 | §6.2/§8 | Current Bean and technical logging | Files 2/4/6 | Preserve ownership and fixed diagnostics | G1 | Step 1 | PASS |
| Rule 5 | §6.2 | JDK and current framework | Files 1–6 | Existing APIs | G1 | Step 1 | PASS |
| Rule 6 | §6.2/§10 | Public receipt unchanged | No affected JSON | No change | S1 | Step 1 | N/A |
| Rule 7 | §6.2/§16 | Single AS YAML | Files 4–8 | False by default | G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/§13 | Simple observation | Files 2/4 | Direct optional callback | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing AgentDB instants | Files 1–4 | No additional clock read or rounding | G1 | Step 1 | PASS |
| Rule 11 | §7/§11 | Existing command owner | All | No schema/API expansion | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | deadline_at and statement_timestamp() | Occurred instant | PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute | Not applicable: count only | Equality excluded | Stable statement clock | No stored field change | Step 1 TEST-001 |
| TIME-002 | Sample cadence/age | Duration | java.time.Duration and monotonic long | Not persisted | Internal | Not applicable: elapsed | Not applicable: aggregate | 30 sec minimum, stale at 60 sec | System.nanoTime | No persisted time | Step 1 TEST-003 |
| TIME-003 | JDBC/server timeout | Duration | PostgreSQL/Spring timeout settings | Not persisted | Internal | Not applicable: timeouts | Not applicable: aggregate | 100 ms statement, one-second Spring setting | Existing source budget | Optional read only | Step 1 TEST-001/002 |

## 5. Change File Tree

| Action | Exact path | Responsibility | Order |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentQueuedBacklogReadTest.java` | Lock exact bound and physical transaction. | Step 1 File 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | Bound a technical sample to exact current primary. | Step 1 File 2 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java` | Read a scalar from current eligible lifecycle rows. | Step 1 File 3 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPollerTest.java` | Verify current poll, cadence, stale/failure and factory default. | Step 1 File 4 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/AgentQueuedBacklogObservation.java` | Keep capped sample separate from local scrape. | Step 1 File 5 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPoller.java` | Reuse only the current successful discovery/admission lifecycle. | Step 1 File 6 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchConfiguration.java` | Wire one explicit optional sampler in existing dispatch factory. | Step 1 File 7 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Keep the new gauge off in AS packaged defaults. | Step 1 File 8 |
| CREATE | `docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md` | Freeze reviewed queue, metric and runtime semantics. | Step 1 File 9 |
| CREATE | `docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md` | Freeze exact execution order and file scope. | Step 1 File 10 |
| CREATE | `docs/egon/evidence/2026-11-06-agent-queued-backlog-metric.md` | Record actual owning tests, package and scope. | Step 1 File 11 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Clean Service `9a509342767d6b01dfa94e482b530cc69ccc5536`. If the separately planned S11 work commits first, verify all eight source paths before re-locking. Preserve unrelated scope.

### 6.2 Build, test, and environment prerequisites

Use existing JDK 25, offline settings, current Maven wrapper and local source siblings. No external downloads, test app, PostgreSQL or deployment.

### 6.3 Immutable constraints and approved decisions

One optional technical SELECT using existing table/grants. No migration, POM, permissions, engine or runtime changes. Existing dispatch authorization and no new scheduler.

### 6.4 Plan Clarifications

Off-repo exact code in `source-draft/{a,b}`. Existing Gauge and scratch test proof use actual AS Registry implementation, no exporter. Capped output is not a bound on physical rows visited.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Observe capped Agent pending lifecycle queue

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Current baseline and reviewed Spec/Plan, exact source draft and offline environment.
- Baseline state: Existing paged discovery, no capped queue metric.
- Observable outcome: Default off; when enabled the successful current poll can record one bounded primary count under fixed cadence, scrape reads cached number only.
- End state: Eleven exact paths and one repository-local commit.
- Test-first gate: Required. R1 typed A positive expectation fails before implementing B.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentQueuedBacklogReadTest.java`

- Purpose: Lock exact bound and physical transaction.
- Symbols: reads_one_bounded_primary_scalar_after_setting_physical_read_only and two final finite cases
- Repository evidence: Existing JdbcTemplate/transaction mock fixture and owner-local aggregate.
- Dependencies and consumers: AR typed A/B and current Spring APIs.
- Why now: R1 tests A while the behavior is deliberately unimplemented.
- Contract/signature changes: No production contract change.
- Input/output and state mapping: One scalar count, zero vs cap, primary guard and exact SQL order.
- Error and edge behavior: Wrong source/fail/count and ambient/interruption unavailable.
- Standards impact: MC-VALID-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertEquals(257, fixture.ledger.sampleQueuedBacklog());
assertEquals(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '100ms'", "count"),
    fixture.calls);
verify(fixture.connection).commit();
```

- Verification contribution: R1/G1.
- After this file: Typed A fails only the expected count assertion; final B verifies source and failure matrix.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: Bound a technical sample to exact current primary.
- Symbols: sampleQueuedBacklog, QUEUED_BACKLOG_CAP, queuedBacklogUnavailable
- Repository evidence: Current primary routing and source transaction guards.
- Dependencies and consumers: Uses repository and only optional AS caller.
- Why now: A publishes compiling stub. B after R1 adds checked read.
- Contract/signature changes: Public technical method returns scalar, no application API.
- Input/output and state mapping: Refuse ambient, interrupted or wrong resource. Bind existing primary and one-second Spring budget.
- Error and edge behavior: Null, negative, >257 and data/transaction failures are fixed unavailable.
- Standards impact: MC-VALID-001, MC-REUSE-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
if (interruptedOrAmbient()) throw queuedBacklogUnavailable();
sample.setReadOnly(false);
sample.setIsolationLevel(ISOLATION_READ_COMMITTED);
sample.setTimeout(1);
return sample.execute(status -> verifyBoundPrimaryResult(repository.sampleQueuedBacklog()));
```

- Verification contribution: R1/G1.
- After this file: Exact technical read is isolated from claim and existing routes.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: Read a scalar from current eligible lifecycle rows.
- Symbols: sampleQueuedBacklog
- Repository evidence: Existing V1 partial dispatch index and queuedRuns filters.
- Dependencies and consumers: Existing JDBC owner, no added table/role.
- Why now: B after typed R1.
- Contract/signature changes: One optional bound SELECT and two transaction setup commands.
- Input/output and state mapping: Read-only mode and 100ms timeout, primary guard, stable DB statement timestamp.
- Error and edge behavior: Wrong source returns null; no fallback, identifiers, full rows or write.
- Standards impact: MC-VALID-001, MC-TIME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```sql
SET TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '100ms';
SELECT CASE WHEN NOT pg_catalog.pg_is_in_recovery()
 AND pg_catalog.current_setting('transaction_read_only') = 'on' THEN (
 SELECT count(*)::integer FROM (
  SELECT 1 FROM tianji_agent.agent_run WHERE status = 'QUEUED'
   AND cancel_requested_at IS NULL AND deadline_at > pg_catalog.statement_timestamp()
   LIMIT ?
 ) AS bounded_queue
) END;
```

- Verification contribution: G1/P1.
- After this file: Actual SQL source has fixed cap bind 257 and no DDL.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPollerTest.java`

- Purpose: Verify current poll, cadence, stale/failure and factory default.
- Symbols: Three backlog-specific tests and existing dispatch cases
- Repository evidence: Current fake scheduler/direct factory/context runner and SimpleMeterRegistry.
- Dependencies and consumers: B AS adapter, unchanged runtime dispatch owner.
- Why now: Compile alongside B final code.
- Contract/signature changes: No production signature introduced by test.
- Input/output and state mapping: Admission before probe, exact count/freshness, no scrape query, cap, default and actual opt-in.
- Error and edge behavior: Probe/discovery failures and stale/stop clear cached sample without affecting dispatch.
- Standards impact: MC-TEST-001, MC-CONFIG-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```java
order.verify(dispatcher).dispatch(run.actor(), run.query());
order.verify(ledger).sampleQueuedBacklog();
assertEquals(257d, gauge.value());
clock.set(Duration.ofSeconds(90).toNanos());
assertTrue(Double.isNaN(gauge.value()));
```

- Verification contribution: G1/P1.
- After this file: New behavior covered and existing scheduler/authority regression stays finite.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/AgentQueuedBacklogObservation.java`

- Purpose: Keep capped sample separate from local scrape.
- Symbols: sampleIfDue, invalidate, close, value, private Sample
- Repository evidence: Existing AS Micrometer and one active poller.
- Dependencies and consumers: Uses AR technical reader and existing unique registry.
- Why now: B optional collaborator.
- Contract/signature changes: No transport/DTO change.
- Input/output and state mapping: Monotonic 30 sec attempt floor, 60 sec stale, no app tags, cached scalar only.
- Error and edge behavior: Unavailability clears snapshot; one safe warning per consecutive sample-failure streak.
- Standards impact: MC-REUSE-001, MC-LOG-001, MC-MODEL-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
if (closed || notYetDue(monotonicTime.getAsLong())) return;
var registry = registries.get();
if (registry != null) {
    Gauge.builder(METER, this, AgentQueuedBacklogObservation::value).register(registry);
    latest = new Sample(ledger.sampleQueuedBacklog(), startedAt);
}
// value reads cached count or NaN with no ledger call.
```

- Verification contribution: G1/P1.
- After this file: Dedicated gauge state and freshness are isolated from domain authority.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPoller.java`

- Purpose: Reuse only the current successful discovery/admission lifecycle.
- Symbols: Compatible ctor, poll finally and close
- Repository evidence: Current lock, one active poll and shutdown semantics.
- Dependencies and consumers: Existing scheduler and optional observer.
- Why now: B after exact sampler defined.
- Contract/signature changes: Preserve existing public constructor and add package-local optional overload.
- Input/output and state mapping: Sample after completed discovery and admission, including successful empty batch.
- Error and edge behavior: Discovery failure invalidates; stop closes sample; nested finally always clears active poll.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 10, Rule 11.
- Implementation pseudocode:

```java
boolean discovered = false;
try {
    var candidates = existingDiscovery();
    discovered = true;
    existingDispatch(candidates);
} finally {
    try { if (discovered && backlog != null) backlog.sampleIfDue(); }
    finally { clearExistingActivePoll(); }
}
```

- Verification contribution: G1/P1.
- After this file: No new scheduling or change to cursor, claim or limits.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchConfiguration.java`

- Purpose: Wire one explicit optional sampler in existing dispatch factory.
- Symbols: tianjiGrantDispatchPoller and exact flag
- Repository evidence: Current auth/reconciler prerequisites and AS registry.
- Dependencies and consumers: Existing factory, MeterRegistry provider and observer.
- Why now: B after AR/AS observation paths.
- Contract/signature changes: Append ObjectProvider to internal Bean factory only.
- Input/output and state mapping: Existing prerequisites first, flag absent/false no sampler, getIfUnique otherwise.
- Error and edge behavior: No startup query or alternate permissions.
- Standards impact: MC-BEAN-001, MC-CONFIG-001, MC-REUSE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 11.
- Implementation pseudocode:

```java
var observation = enabled
    ? new AgentQueuedBacklogObservation(ledger, registries::getIfUnique, System::nanoTime)
    : null;
return new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, batch,
    maxInFlight, interval, shutdownTimeout, observation);
```

- Verification contribution: G1/P1.
- After this file: Only current eligible deployment can opt in.

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Keep the new gauge off in AS packaged defaults.
- Symbols: stellar.tianji.agent.run-observation.queued-backlog-metrics-enabled
- Repository evidence: Existing first claim metric and storage configuration.
- Dependencies and consumers: Boot and existing AS test.
- Why now: B before G1/P1.
- Contract/signature changes: One additive false key.
- Input/output and state mapping: Absent/false no SQL and no change to old first claim flag.
- Error and edge behavior: Existing model usage subtree and startup remain.
- Standards impact: MC-CONFIG-001.
- Literal rule enforcement: Rule 7 and Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    agent:
      run-observation:
        initial-claim-metrics-enabled: false # Existing committed first claim Timer
        queued-backlog-metrics-enabled: false # New optional bounded aggregate Gauge
```

- Verification contribution: G1/P1.
- After this file: Actual YAML cmp confirms default off and original config bytes.

#### File 9 — `CREATE docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md`

- Purpose: Freeze reviewed queue, metric and runtime semantics.
- Symbols: REQ-001–005, TIME-001–003, METRIC-001
- Repository evidence: Current exact queue/poller code and finite draft.
- Dependencies and consumers: Normative target for one Step.
- Why now: Lock reviewed bytes before source.
- Contract/signature changes: No new authority.
- Input/output and state mapping: One default-off gauge with bounded query.
- Error and edge behavior: Source drift blocks execution.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 10 and Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md
cp -n "$BACKLOG_REVIEWED_SPEC" docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md
cmp "$BACKLOG_REVIEWED_SPEC" docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md
```

- Verification contribution: S1.
- After this file: Spec frozen.

#### File 10 — `CREATE docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md`

- Purpose: Freeze exact execution order and file scope.
- Symbols: R1/G1/P1/S1, existing reactor and AS package proof
- Repository evidence: Approved primary Spec and retained phase sources.
- Dependencies and consumers: Source execution one Step.
- Why now: Lock before execution.
- Contract/signature changes: No runtime invocation.
- Input/output and state mapping: Exact finite and package selectors.
- Error and edge behavior: Failure returns to exact path owner.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md
cp -n "$BACKLOG_REVIEWED_PLAN" docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md
cmp "$BACKLOG_REVIEWED_PLAN" docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md
```

- Verification contribution: S1.
- After this file: Plan frozen.

#### File 11 — `CREATE docs/egon/evidence/2026-11-06-agent-queued-backlog-metric.md`

- Purpose: Record actual owning tests, package and scope.
- Symbols: R1/G1/P1/S1 and remaining runtime proof
- Repository evidence: Current evidence convention and finite drafts.
- Dependencies and consumers: Owner evidence.
- Why now: After actual gates.
- Contract/signature changes: Source record only.
- Input/output and state mapping: Counts and digests, no private or raw output.
- Error and edge behavior: No query plan or runtime acceptance from finite tests.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record exact typed red, actual owner XML counts and clean AS package.
Record manifest identity verification and untouched permission/DDL content.
Keep actual plan, scrape, exporter, load and S12 as separate verification.
```

- Verification contribution: S1.
- After this file: Single auditable source commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Execute §8 R1/G1/P1/S1 at exact source phases.
- Expected result: R1 one intended assertion, G1/P1 22 actual finite passes, byte-equal actual AS YAML and identical protected permission content.
- Failure returns to: Files 1–3 for AR, 4–8 for AS, 9–11 for exact documentation/scope.
- Completion criteria: Actual default-off package, no POM, grant or SQL migration diff, exact one commit.
- Rollback: Set the exact flag false or revert the isolated commit if authorized.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentQueuedBacklogReadTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPollerTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/AgentQueuedBacklogObservation.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPoller.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md`, `docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md`, `docs/egon/evidence/2026-11-06-agent-queued-backlog-metric.md`.
- Commit: `feat(agent): sample bounded pending queue backlog`.

## 8. Test, Validation, and Quality Gates

Capture fresh owning Surefire XML and process exit for each phase. No I/E, application or engine.

```sh
backlog_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/agent-queue-backlog
backlog_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
backlog_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
backlog_base=9a509342767d6b01dfa94e482b530cc69ccc5536
test "$(git rev-parse --verify HEAD)" = "$backlog_base"
backlog_core=AgentQueuedBacklogReadTest,TianjiGrantDispatchPollerTest,AgentModelUsageDiagnosticsTest
backlog_finite() {
  JAVA_HOME="$backlog_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$backlog_settings" -s "$backlog_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
backlog_finite 'AgentQueuedBacklogReadTest#reads_one_bounded_primary_scalar_after_setting_physical_read_only'
backlog_finite "$backlog_core"
JAVA_HOME="$backlog_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$backlog_settings" -s "$backlog_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$backlog_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-agent-queued-backlog-20261106 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$backlog_core"
backlog_as=stellar-enclosure-tianji-agent-server
backlog_jar="$backlog_as/target/stellar-enclosure-tianji-agent-server-exec.jar"
unzip -p "$backlog_jar" BOOT-INF/classes/application.yml > "$backlog_root/actual-as-application.yml"
cmp "$backlog_as/src/main/resources/application.yml" "$backlog_root/actual-as-application.yml"
rg -U -q '      run-observation:\n        initial-claim-metrics-enabled: false\n        queued-backlog-metrics-enabled: false' "$backlog_root/actual-as-application.yml"
backlog_prior=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/process-start-receipt/root-agent-server-manifest.json
printf '%s  %s\n' a3fa58e67effd0aac992786e360eadd4652bf4755aea35d02cd1b336a5b47def "$backlog_prior" | shasum -a 256 -c -
python3.14 -I -B - "$backlog_jar" "$backlog_prior" "$backlog_base" <<'PY'
import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile

archive, baseline = map(Path, sys.argv[1:3])
with archive.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
artifact = json.loads((archive.parent / "permissions/artifact-evidence.json").read_bytes())
assert artifact["producerId"] == "tianji-agent-java" and artifact["artifactDigest"] == digest
with ZipFile(archive) as jar:
    names = jar.namelist()
    assert names.count("BOOT-INF/classes/application.yml") == 1
    assert names.count("META-INF/permissions/manifest-v1.json") == 1
    new = json.loads(jar.read("META-INF/permissions/manifest-v1.json"))
old = json.loads(baseline.read_bytes())
assert new["artifact"]["commit"] == sys.argv[3]
assert new["artifact"]["buildId"] == "LOCAL_UNCOMMITTED-t4-02-agent-queued-backlog-20261106"
for manifest in (old, new):
    manifest["artifact"].pop("commit")
    manifest["artifact"].pop("buildId")
assert old == new
print("Verified exact AS artifact " + digest)
PY
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-00-24-agent-queued-backlog-metric.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-00-25-agent-queued-backlog-metric.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml 'stellar-enclosure-tianji-process-server/**'
git diff --cached --check
git diff --cached --name-status
```

| Gate | Expected exact proof | Failure |
| --- | --- | --- |
| R1 | 1 expected assertion 257 vs 0, no error/skip | Correct typed A fixture |
| G1 | AR read 3, AS dispatch 11, AS storage 8. 22 pass, no error/skip | Correct owning path |
| P1 | 22 again, actual AS flag false and whole YAML cmp, same AS permission contents and archive evidence | Correct package/scope |
| S1 | Strict docs, exact eleven paths, no PS/SQL/POM/grant delta | Restore scope |

## 9. Migration, Compatibility, Rollout, and Rollback

No migrations. Preserve all existing owner contracts. False stops sampling. No hosted or live metric/DB evidence.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step and files | Gate |
| --- | --- | --- |
| REQ-001 | Step 1 Files 1–3 | R1/G1 |
| REQ-002 | Step 1 Files 1–3 | G1 |
| REQ-003 | Step 1 Files 4–7 | G1/P1 |
| REQ-004 | Step 1 Files 4–8 | G1/P1/S1 |
| REQ-005 | Step 1 Files 9–11 | S1 |

## 11. Risks, Blockers, and User Decisions

| Risk | Status | Disposition |
| --- | --- | --- |
| Source execution order | Current baseline lock | Re-lock if S11 lands first |
| Real query plan and pool/transport | Outside finite proof | Independent runtime load evidence |
| Exporter NaN and production thresholds | Unverified | Existing owner deployment and S12 gate |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Actual current queue count under an explicit cap, no grant-ready or SLA claim.

### 12.2 Spec consistency

Fixed query, source, budget, 30-second cadence and 60-second expiry, cached only gauge.

### 12.3 Repository executability

Current offline reactor, existing tests and actual AS package proof.

### 12.4 Test and release completeness

Local source proof and independent runtime acceptance.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing AR/AS | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Existing Tx, scalar SQL scope and MeterRegistry | None |
| MC-DEP-001 | Applicable | PASS | §6 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7 | Bounded primary queue and exact deadline | None |
| MC-MODEL-001 | Applicable | PASS | §7 | Private immutable scalar Sample | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/§7 | No DTO/PO mapping | None |
| MC-LOG-001 | Applicable | PASS | §7 | Fixed once diagnostic | None |
| MC-BEAN-001 | Applicable | PASS | §7 | Same factory | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK and existing libraries | None |
| MC-JSON-001 | Not applicable | N/A | §7 | No JSON change | None |
| MC-TIME-001 | Applicable | PASS | §4.9 | Exact DB timestamp and monotonic cadence | None |
| MC-CONFIG-001 | Applicable | PASS | §7 | One default false | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Simple fixed observer | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | Exact eleven paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Source ready for review | Actual I/E/S12 outside Step |

### 12.6 Final verdict

PASS — Ready for user review

当前为仓库外 Plan，实际 source/packaging 和运行验收由独立门禁记录。
