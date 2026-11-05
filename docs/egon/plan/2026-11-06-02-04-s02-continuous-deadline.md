# S02 持续增量总时限验收实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-04-s02-continuous-deadline.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 02:04 Asia/Shanghai |
| Updated | 2026-11-06 02:04 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test method, three review documents, one commit |
| Source Requirement | Master §17.2 S02, §19 T1-03 and T4-05 |
| Baseline Revision | Service 539cd2b58027299a0693170f42404d17f9b23b8b |
| Implements Spec | [S02 continuous deadline Spec](../spec/2026-11-06-02-03-s02-continuous-deadline.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 02:03 Asia/Shanghai SHA-256 b3aff6574ac25c53dd03c51c8facb5079bb6980d5081b2decd1f2b2f83a8619a |
| Effective Specs | [S02 continuous deadline Spec](../spec/2026-11-06-02-03-s02-continuous-deadline.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Add one operator-only S02 case to the existing AR PostgreSQL test to observe the total deadline while committed deltas still refresh the idle window. Use the existing native CONFIG Runner, bounded Fake and owner ledger. The source Step runs the finite selectors and compiles the operator method.

## 2. Target Spec and Effective Design

The [S02 continuous deadline Spec](../spec/2026-11-06-02-03-s02-continuous-deadline.md) §§4/7/14 defines this exact scenario. Existing test guard, policy defaults, owner and runtime contracts are authoritative. The master authorizes the source step. Actual I1 remains a separately authorized operator action.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/14 | One subscribed continuous Fake through real CONFIG Runner and durable deltas inside the final idle window | AR test |
| REQ-002 | §4/6.3/7/11/14 | Original deadline, cancelled subscription, correct terminal run/result/event, stable repeat execution | AR test |
| REQ-003 | §4/14/16 | Exact bytecode/property, finite reports and four-path scope with all 28 migration files preserved | Evidence |

## 4. Implementation Strategy and Dependency Order

Lock the reviewed baseline and frozen candidate. Apply only the AR test method, capture actual finite and bytecode evidence, then finalize the three review files and verify the exact staged scope. The current Step is source-only. The future operator uses the same existing database fixture and separate exact selector.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR persistence test invokes current Runner and ledger | §6–8 | Existing test owner and dependencies | S1 |
| Validation | Owner-scoped typed query and disposable PG guard | §5/7 | Verify exact deadline, terminal watermark, result and cancellation | G2/I1 |
| Time and isolation | Existing DB clock and FixtureModel lifecycle | §5/6.3/11 | Compare persisted Instants and bound Fake delivery and waits | G2/I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Native CONFIG run | AdkAgentRunExecutorPostgresTest | Existing actor, start, singleSpec, FixtureModel, executor | Fits | Reuse | One test method | Step 1 MC-REUSE-001 |
| Periodic Fake | Existing idle and parallel cases | Reactor Flux and JDK latch in same test class | Fits | Reuse | Local assertions only | Step 1 MC-UTIL-001 |
| Deadline/result evidence | AgentLedgerService | Existing DB clock, appendStatusEvent and owner queries | Fits | Reuse | Bounded fixture reads | Step 1 MC-TIME-001 |
| Database protection | PostgresTestDatabase | Existing strict URL, schema guard and V1–V9 | Fits | Reuse | No new setting or dependency | Step 1 MC-VALID-001 |

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
| Rule 1 | §7/8 | Existing AR test | File 1 | Exact test method in current class | G2 | Step 1 | PASS |
| Rule 2 | §5/7 | Existing guarded fixture and scoped ledger | File 1 | Preserve actor and exact run and validate terminal evidence | G2/I1 | Step 1 | PASS |
| Rule 3 | §10 | Existing typed DTOs | File 1 | No new POJO or converter | S1 | Step 1 | N/A |
| Rule 4 | §6/8 | Existing test-local wiring | File 1 | No new bean or logger | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing JDK, Reactor and JUnit | File 1 | Bounded test using current APIs | G1/G2 | Step 1 | PASS |
| Rule 6 | §7/10 | Existing typed payload and error code | File 1 | No JSON encoding or mapping change | S1 | Step 1 | N/A |
| Rule 7 | §5/16 | Existing fixture property | All | No configuration profile change | S1 | Step 1 | N/A |
| Rule 9 | §13 | Direct isolated fixture | File 1 | No new abstraction or pattern | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing authoritative DB instants | File 1 | java.time duration and instant comparisons | G2/I1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AR and Flyway owner | All | Keep source inside existing test and preserve all SQL | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.deadlineAt and AgentEventDTO.occurredAt | Occurred instant | java.time.Instant | UTC instant | Internal | Database clock | No display change | Last delta at or before deadline and later than deadline minus idle | Existing DB clock and microsecond precision | Existing mapping | Step 1 I1 TEST-003 |
| TIME-002 | Idle window, inter-frame delay and cancellation wait | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | No display change | Existing class timeout and bounded Fake cap | Existing scheduling | No scheduling contract change | Step 1 G2/I1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-03-s02-continuous-deadline.md` |
| Create | `docs/egon/plan/2026-11-06-02-04-s02-continuous-deadline.md` |
| Create | `docs/egon/evidence/2026-11-06-s02-continuous-deadline.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Use Service 539cd2b58027299a0693170f42404d17f9b23b8b. Verify actual clean source, JDK 25 and the existing approved offline Maven settings. Candidate Java SHA-256 is `d49e6297f16807c5d77bf5d0139288ec4801a32739348e83112fce2e542214ab`. Keep all production code, configuration, authorization, dependencies and 28 migrations out of the diff. The separate I1 selector requires an operator-provisioned fresh loopback primary with fixture authentication supplied outside the URL. The fixture uses V1–V9 and never applies V16.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare one S02 sustained-output operator fixture

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing CONFIG fixture, current ADK Runner, owner ledger and PostgresTestDatabase target V1–V9.
- Baseline state: Existing silent total timeout, short completing stream and parallel idle tests. Exact S02 sustained deadline method absent.
- Observable outcome: One compiled opt-in integration test that verifies the ongoing stream and durable hard cutoff.
- End state: One existing AR test class and three documents in one commit, with actual G0/G1/G2/S1 evidence and I1 pending.
- Test-first gate: Not applicable. This adds integration evidence only. G0 records method absence. No fabricated finite RED or invocation of the new PostgreSQL test is part of source delivery.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java`

- Purpose: Exercise the untested overlap between continuous durable deltas and total deadline using the actual CONFIG Runner.
- Symbols: AdkAgentRunExecutorPostgresTest#continuous_deltas_cannot_extend_the_total_deadline and existing actor, singleSpec, policy, start, executor and FixtureModel helpers.
- Repository evidence: Spec EVD-002–005; silent total deadline and short completed stream cases do not observe this overlap.
- Dependencies and consumers: Existing JUnit/Reactor/Spring AI and AR fixture; separately authorized operator.
- Why now: This one source path defines the missing runtime proof.
- Contract/signature changes: One added test method. Existing test guard and production signatures remain.
- Input/output and state mapping: Five-second total, two-second idle, nonempty nonfinal tick every 100 ms and 90-frame cap; original owned Run progresses QUEUED→RUNNING→TIMED_OUT with stable result/event evidence.
- Error and edge behavior: Reject absent subscription cancellation, cap exhaustion, insufficient committed deltas, late last delta, idle or other error, natural output, wrong terminal watermark or different replayed state.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-UTIL-001 and MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test
void continuous_deltas_cannot_extend_the_total_deadline() throws Exception {
    // Use the existing owner, policy, fixture and real CONFIG writer.
    model.script = prompt -> Flux.interval(Duration.ZERO, Duration.ofMillis(100))
        .take(90)
        .map(index -> emitAndCountNonfinalFixtureTick(index))
        .doOnCancel(cancelled::countDown);
    ledger.publish(actor, singleSpec(key, policy(Duration.ofSeconds(5), Duration.ofSeconds(2))));
    var query = start(actor, key, "key");
    var accepted = ledger.get(actor, query);
    var timedOut = worker.executeQueued(actor, query);
    assertOriginalDeadlineDbTimeAndExactTimeoutResult(accepted, timedOut, ledger.getResult(actor, query));
    assertCancelledSingleSubscriptionBeforeCap(cancelled, model, emitted);
    var events = ledger.events(actor, new AgentEventsQuery(query.runId(), 0, 100));
    assertAtLeastTwoDeltasAndLastInsideIdleWindow(events, accepted.deadlineAt(), idle);
    assertNoFinalOutputAndTerminalStatusAtWatermark(events, timedOut);
    assertEquals(timedOut, worker.executeQueued(actor, query));
    assertOneSubscriptionAndUnchangedEventList(events);
}
```

- Verification contribution: G1/G2 and independent I1.
- After this file: Exact operator method compiles; no live PostgreSQL verdict.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-02-03-s02-continuous-deadline.md`

- Purpose: Anchor scope and precise evidence requirements to master S02.
- Symbols: REQ-001–003, UC-001/002 and TEST-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing master, test fixture and reviewer.
- Why now: Freeze total/idle witness and evidence boundaries alongside the source.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Existing facts, exact observable outcome and source/operational boundaries.
- Error and edge behavior: Wrong scope or inconsistent timing oracle blocks staging.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the continuous-delta S02 slice and exact opt-in owner.
Preserve the current database protection and all runtime boundaries.
Trace REQ-001/002 to the pending I1 report and REQ-003 to exact finite/source gates.
```

- Verification contribution: S1.
- After this file: Strict Spec and trace available.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-02-04-s02-continuous-deadline.md`

- Purpose: Lock the one-Step implementation and exact gates.
- Symbols: Step 1, G0, G1, G2, S1 and I1.
- Repository evidence: Effective Spec, approved source reactor and existing fixture method guard.
- Dependencies and consumers: File 2 and executing reviewer.
- Why now: Verify exact paths and separate operator invocation.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Actual finite XML and bytecode, exact scope and later private operator report.
- Error and edge behavior: Any digest, fixture guard, report or staged scope mismatch stops this Step.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Verify baseline/spec digest, compile exact added method using finite selectors.
Record future exact I1 and confirm only four paths stage.
Stop the Step on any nonzero finite result or an unexpected PostgreSQL report.
```

- Verification contribution: S1.
- After this file: Strict Plan and reproducible gates available.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-s02-continuous-deadline.md`

- Purpose: Record fresh observed validation with scope and limitations.
- Symbols: G0/G1/G2/S1 and I1.
- Repository evidence: Fresh owning XML, javap and tracked migration diff.
- Dependencies and consumers: Prior files and reviewer.
- Why now: Evidence is filled before staging.
- Contract/signature changes: Evidence document only.
- Input/output and state mapping: Fresh actual report counts, class/method guard and path evidence.
- Error and edge behavior: Missing or skipped PostgreSQL reports cannot establish I1.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record actual finite counts, exact compiled method and no PostgreSQL report.
Record all 28 unchanged migration resources and exact staged paths.
Leave actual PostgreSQL acceptance pending unless a separate operator report exists.
```

- Verification contribution: S1.
- After this file: Four exact source/review files are ready to commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run §8 G0/G1/G2/S1. I1 is operator-only.
- Expected result: Finite regressions pass, new method compiles, existing guard remains, no PostgreSQL report, exact scope.
- Failure returns to: File 1 for behavior/compilation; Files 2–4 for scope or evidence.
- Completion criteria: Actual source gates, four paths and explicit I1 pending.
- Rollback: Revert one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java`, `docs/egon/spec/2026-11-06-02-03-s02-continuous-deadline.md`, `docs/egon/plan/2026-11-06-02-04-s02-continuous-deadline.md`, `docs/egon/evidence/2026-11-06-s02-continuous-deadline.md`.
- Commit: test(agent): prepare S02 continuous deadline acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Clean baseline | Existing class and PG guard, exact new method absent |
| G1 | Offline clean reactor, exact three finite selectors | Fresh owning reports, zero failures/errors/skips; reviewed source inventory 2/1/12 |
| G2 | JDK 25 javap new class | Exact method and existing property, no PostgreSQL fixture report |
| S1 | Strict docs and exact staged scope | Four exact paths, 28 migration files match |
| I1 | Separate authorized isolated PostgreSQL | One selected method, zero failures/errors/skips, exact sustained-output hard cutoff |

```sh
s02_base=539cd2b58027299a0693170f42404d17f9b23b8b
s02_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s02_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s02_method=continuous_deltas_cannot_extend_the_total_deadline
s02_ar=stellar-enclosure-tianji-agent-runtime
s02_class=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AdkAgentRunExecutorPostgresTest
s02_path=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java
test "$(git rev-parse --verify HEAD)" = "$s02_base"
git cat-file -e "$s02_base:$s02_path"
test -z "$(git show "$s02_base:$s02_path" | rg -F "$s02_method")"
s02_reactor() {
  JAVA_HOME="$s02_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$s02_settings" -s "$s02_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
s02_reactor clean '-Dtest=AdkEventProjectorTest,AdkExecutionTransactionBoundaryTest,AdkNativeAgentCompletionTest'
"$s02_jdk/bin/javap" -p -v -classpath "$s02_ar/target/test-classes" "$s02_class"
test ! -e "$s02_ar/target/surefire-reports/TEST-$s02_class.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict   docs/egon/spec/2026-11-06-02-03-s02-continuous-deadline.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict   docs/egon/plan/2026-11-06-02-04-s02-continuous-deadline.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Inspect the three fresh G1 owning XML files and G2 exact method/property. Never set the PostgreSQL opt-in for G1/G2. S1 compares the exact four staged paths and prior migration bytes. Failure or skipped cases are reported explicitly.

### Separate operator-only I1

In a separately authorized environment provision a fresh loopback primary matching PostgresTestDatabase guard, with the existing tianji_it_ database prefix and tianji_agent schema absent. Provide approved fixture authentication outside the URL. Retain the single fresh owning XML and dispose of the fixture separately. No application, HTTP listener or real model provider is part of this I1.

```sh
: "${TIANJI_S02_PRIVATE_PG_URL:?Provide an authorized fresh loopback fixture URL}"
s02_reactor "-Dtest=AdkAgentRunExecutorPostgresTest#$s02_method"   "-Dtianji.postgres.test.url=$TIANJI_S02_PRIVATE_PG_URL"
```

Require exactly one executed passing method with no skips. An absent report, XML only from finite suites or bytecode inspection cannot satisfy I1.

## 9. Migration, Compatibility, Rollout, and Rollback

All production SQL, code and configuration retain their existing scope. The fixture keeps V1–V9. Revert the focused test-only commit to remove the added scenario.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual I1 needs an independently authorized disposable PostgreSQL primary. Host pressure that prevents the timing witness makes the integration case fail. Full S02 cancellation and shutdown, provider, live authorization and performance remain separate gates.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR persistence test owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing CONFIG fixture, Runner and ledger | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Reactor, JUnit, Spring AI and PG fixture already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Existing PG guard and exact scoped status, deadline and event assertions | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Reactor and JUnit | None |
| MC-JSON-001 | Not applicable | N/A | §7.3/10 | Existing typed events and payload maps | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB instant evidence and bounded elapsed timeout | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 12.6 Final verdict

PASS — Ready for user review
