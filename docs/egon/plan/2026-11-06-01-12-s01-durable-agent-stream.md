# S01 单 Agent 持久流验收场景实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-12-s01-durable-agent-stream.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 01:12 Asia/Shanghai |
| Updated | 2026-11-06 01:12 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | One AS opt-in JUnit scenario, three review documents, one commit |
| Source Requirement | Master §17.2 S01 and §19 T4-05 |
| Baseline Revision | Service e5f6df84372774bac48bc7c6649fd56f0ed31520 |
| Implements Spec | [S01 durable stream Spec](../spec/2026-11-06-01-11-s01-durable-agent-stream.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 01:11 Asia/Shanghai SHA-256 55a1b8e5b79bc7b4c25801dad6936e8ff231d33ffff8ef1a66f19179955f0e48 |
| Effective Specs | [S01 durable stream Spec](../spec/2026-11-06-01-11-s01-durable-agent-stream.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Add one operator-selectable S01 acceptance case to the existing AS HTTP PostgreSQL test. Reuse the real HTTP adapter, ADK Runner and transactional ledger. Run exact finite source selectors and inspect the compiled opt-in method. Preserve all runtime prerequisites and existing API, authorization, schema and deployment contracts.

## 2. Target Spec and Effective Design

The [S01 durable stream Spec](../spec/2026-11-06-01-11-s01-durable-agent-stream.md) §§4/7/14 defines one test-only scenario. Existing HTTP and AR tests supply reusable behavior. Their existing methods and fixture scope are kept. The master goal governs this source work. Operator-run I remains independently authorized and unexecuted in this Step.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/14 | Exact opt-in method posts once and uses the true Runner and one Fake model subscription | Existing AS class |
| REQ-002 | §4/7/14 | Operator verifies exact durable TEXT_DELTA sequence and cursor suffix | Existing SSE and mapper |
| REQ-003 | §4/7/11 | Operator verifies one terminal result/status transaction with terminal event xmin and same owner | Existing persistence observer |
| REQ-004 | §4/14/16 | Actual finite results, compiled method, four exact paths, 27 migration hashes | Evidence |

## 4. Implementation Strategy and Dependency Order

Lock exact clean HEAD and inspect the existing class fixture and owner boundaries. Add the operator method and its bounded local helpers in the existing test. Validate finite existing suites and the compiled class without selecting an integration test. Capture observed results, validate scope and commit the four files together. Operator I has no dependency on untracked production tooling.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | AS existing HTTP test invokes existing AR dependency | §6–8 | One existing test owner, zero production changes | S1 |
| Validation | Existing test guard, controller and SSE input path | §5/7 | Keep owner binding and verify exact sequence, result and transaction | G1/G2/I1 |
| Model and mapping | Existing DTOs and Jackson | §10 | No new production carrier or converter | S1 |
| Time and isolation | Existing fixture timeout and disposable V1–V9 | §5/6.3/11 | Bound Fake completion and preserve schema guard | G2/I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| HTTP and SSE | AgentRunHttpPostgresTest | Existing standalone MockMvc, controllers, issuer filter and ledger | Fits | Reuse | New test assertions only | Step 1 MC-REUSE-001 |
| Native Runner | AdkAgentRunExecutorPostgresTest | Existing CONFIG writer, factory and local ChatModel | Fits | Reuse production Runner and the fixture pattern | Local deterministic Fake | Step 1 MC-REUSE-001 |
| Commit evidence | AgentLedgerService.finish and existing Pg schema | One current run row and exact terminal event | Fits | Compare transaction identity and known result in one scoped SELECT | Test-only read | Step 1 MC-VALID-001 |

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
| Rule 1 | §7/8 | Existing AS class | File 1 | Exact test method and existing types | G2 | Step 1 | PASS |
| Rule 2 | §5/7 | Existing HTTP validation and owner fence | File 1 | Reuse request checks and validate replay and result identity | G1/I1 | Step 1 | PASS |
| Rule 3 | §10 | Existing immutable DTOs | File 1 | No business modeling or conversion change | S1 | Step 1 | N/A |
| Rule 4 | §6/8 | Existing test-local wiring | File 1 | No new business bean or logger | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing Java and dependency APIs | File 1 | No utility or framework additions | G1/G2 | Step 1 | PASS |
| Rule 6 | §7/10 | Existing Spring Jackson mapper | File 1 | Parse known SSE and result DTO payloads | G1/I1 | Step 1 | PASS |
| Rule 7 | §5/16 | Existing integration property | All | No application profile change | S1 | Step 1 | N/A |
| Rule 9 | §13 | Direct isolated fixture | File 1 | No complex business pattern | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing UTC instants and test bounds | File 1 | Use java.time.Duration and existing UTC facts | G2/I1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AS/AR, Flyway and owner contracts | All | No production path or DDL | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run and event timestamps | Occurred instant | java.time.Instant | UTC instant | UTC Z | Explicit UTC fixture clock | No display change | Existing lease and watermark | Existing Clock and DB precision | Existing contracts | Step 1 G2/I1 TEST-002/003 |
| TIME-002 | Fixture runtime bound | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | No display change | Existing fixture and model control timeout | Existing scheduler and test timeout | Existing fixture semantics | Step 1 G2/I1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-01-11-s01-durable-agent-stream.md` |
| Create | `docs/egon/plan/2026-11-06-01-12-s01-durable-agent-stream.md` |
| Create | `docs/egon/evidence/2026-11-06-s01-durable-agent-stream.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Baseline Service e5f6df84372774bac48bc7c6649fd56f0ed31520. Verify clean source, JDK 25, reviewed offline Maven settings, four paths and 27 immutable production SQL. Standalone HTTP testing is in-process with no application/listener. No production auth, config, schema or dependency change. Separate I1 requires an operator-provisioned private disposable primary and explicit one-method opt-in. The read-only xmin witness applies to the immediate fixture result before any maintenance or update of the terminal run row.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare one durable S01 operator fixture

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing AS HTTP fixture, AR CONFIG writer/model test idioms and existing V1–V9.
- Baseline state: Existing HTTP cancel/replay and separate AR execution tests. Exact integrated normal-flow method absent.
- Observable outcome: One compiled opt-in S01 method and actual passing adjacent finite reports, ready for independent I.
- End state: One existing test class and three documents in one commit.
- Test-first gate: Not applicable. This Step adds integration evidence only and changes no runtime behavior. A fabricated class-presence failure or a finite mock cannot prove the missing live transaction. G0 records the exact absence and I1 supplies the only runtime verdict.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java`

- Purpose: Bridge current public HTTP adapter, real Runner and durable result in one private integration scenario.
- Symbols: AgentRunHttpPostgresTest#one_http_start_projects_and_replays_two_durable_deltas_with_atomic_completion.
- Repository evidence: Spec EVD-002–004. Existing fixture uses the same real datasource/ledger/controller/SSE and verified synthetic owner.
- Dependencies and consumers: Existing AS→AR dependency, Spring AI ChatModel and existing JUnit/Maven infrastructure; a later operator selects this single test.
- Why now: This test defines the evidence that the repository lacks and is the only changed Java file.
- Contract/signature changes: One added opt-in JUnit method and test-local helper logic.
- Input/output and state mapping: One accepted start, exact owned run, two distinct ordered Fake chunks, replay after first delta, expected COMPLETED result and terminal event.
- Error and edge behavior: Wrong fixture, missing frame, wrong sequence, mismatched xmin, unexpected text, extra run/model work or any timeout fails the selected I test.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001 and MC-TIME-001; existing Spring and AR paths with test-only calls.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test
void one_http_start_projects_and_replays_two_durable_deltas_with_atomic_completion() {
    // Existing before-all checks and migrates only a fresh local fixture.
    var published = ledger.publish(OWNER, syntheticSingleWriterConfig);
    var queued = mvc.perform(post(BASE).content(singleStartBody(published)))
        .andExpect(status().isAccepted());
    // Use the actual AR executor for the exact observed query with its normal factory and a local Fake.
    runOnceWithRealAdk(queued.runId(), twoDistinctTextChunks);
    var all = decodeExistingSse(mvc.perform(get(eventsUri(queued))));
    var replay = decodeExistingSse(mvc.perform(get(eventsUri(queued))
        .header("Last-Event-ID", firstDeltaSequence(all))));
    assertOrderedDurableDeltasAndExactSuffix(all, replay);
    assertExistingOwnerResultAndScopedPostgresTerminalTuple(queued, all);
    assertSingleModelSubscriptionAndRun();
}
```

- Verification contribution: G1/G2 and I1.
- After this file: Exact operator scenario compiles. It has no source-turn PostgreSQL report.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-01-11-s01-durable-agent-stream.md`

- Purpose: Anchor the S01 requirement and limit source and runtime evidence.
- Symbols: REQ-001–004, UC-001/002, TEST-001–003.
- Repository evidence: Master S01 and release acceptance matrix.
- Dependencies and consumers: Existing baseline and reviewer.
- Why now: Capture settled reuse and isolation alongside the scenario.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Existing evidence, test boundary and exact future pass requirements.
- Error and edge behavior: Drift or a malformed Spec stops staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record original S01 evidence requirement, exact existing paths and future I prerequisites.
Keep source/test proof distinct from I and avoid a new runtime design.
Preserve the existing isolated loopback guard, AS route definitions, ADK entry and V1–V9.
```

- Verification contribution: S1.
- After this file: Strict Spec and source trace are available.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-01-12-s01-durable-agent-stream.md`

- Purpose: Lock exact order, validation commands and commit paths.
- Symbols: Step 1 and gates G0/G1/G2/S1/I1.
- Repository evidence: Effective Spec and current reactor.
- Dependencies and consumers: File 2 and the executing reviewer.
- Why now: Include exact runnable source and future operator selectors.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Scoped command inputs to finite XML, bytecode and exact path validation.
- Error and edge behavior: Any scope, gate or signature discrepancy stops staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock reviewed baseline/spec digest. Compile only the new I method under finite selectors.
Require separate owner-authorized I1 and one exact commit.
Match the four staged paths against §5 and ensure G2 reports the existing integration guard.
```

- Verification contribution: S1.
- After this file: Strict Plan can be reproduced by the reviewer.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-s01-durable-agent-stream.md`

- Purpose: Record observed scope and validation accurately.
- Symbols: G0/G1/G2/S1 and separate I1.
- Repository evidence: Fresh reactor XML, javap and migration diff.
- Dependencies and consumers: Previous files and reviewer.
- Why now: Evidence is written from actual observations before staging.
- Contract/signature changes: Evidence document only.
- Input/output and state mapping: Actual test counts and bytecode checks to one redacted evidence entry.
- Error and edge behavior: A skipped or absent report cannot substantiate I success.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record finite test counts and exact compiled method/guard with no integration report.
State live S01 boundary and omit raw credentials, URL and customer data.
Record byte equality of all 27 original SQL files and distinguish source verification from operator I1.
```

- Verification contribution: S1.
- After this file: All four files support one source commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run §8 G0/G1/G2/S1 exactly, without I1.
- Expected result: Existing finite reports pass, compiled test method and guard present, no PostgreSQL test report, exact scope.
- Failure returns to: File 1 for behavior or compilation, Files 2–4 for scope and evidence.
- Completion criteria: All source gates passed, explicit I1 pending and exact four paths ready.
- Rollback: Revert the one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java`, `docs/egon/spec/2026-11-06-01-11-s01-durable-agent-stream.md`, `docs/egon/plan/2026-11-06-01-12-s01-durable-agent-stream.md`, `docs/egon/evidence/2026-11-06-s01-durable-agent-stream.md`.
- Commit: test(agent): prepare S01 durable HTTP stream acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Clean fixed baseline | Existing class and integration property, no new method |
| G1 | New fixture source, offline clean reactor, exact four finite selectors | Four owning reports with nonzero tests, zero failures/errors/skips |
| G2 | JDK 25 javap test class | Exact new method, existing property and no PostgreSQL suite report |
| S1 | Strict docs, status and migration diff | Four exact files, 27 prior SQL unchanged |
| I1 | Separate authorized operator-only isolated run | One method executed, zero failed/errors/skipped, assertions prove S01 path |

```sh
s01_base=e5f6df84372774bac48bc7c6649fd56f0ed31520
s01_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s01_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s01_method=one_http_start_projects_and_replays_two_durable_deltas_with_atomic_completion
s01_as=stellar-enclosure-tianji-agent-server
s01_class=top.egon.cola.platform.tianji.agent.server.api.AgentRunHttpPostgresTest
s01_path="$s01_as/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunHttpPostgresTest.java"
test "$(git rev-parse --verify HEAD)" = "$s01_base"
test -z "$(git show "$s01_base:$s01_path" | rg -F "$s01_method")"
s01_reactor() {
  JAVA_HOME="$s01_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$s01_settings" -s "$s01_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
s01_reactor clean '-Dtest=AgentRunHttpTest,AgentRunEventStreamCapacityTest,AdkEventProjectorTest,AdkNativeAgentCompletionTest'
"$s01_jdk/bin/javap" -p -v -classpath "$s01_as/target/test-classes" "$s01_class"
test ! -e "$s01_as/target/surefire-reports/TEST-$s01_class.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-06-01-11-s01-durable-agent-stream.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-06-01-12-s01-durable-agent-stream.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Fresh owning G1 XML must be nonzero in all four suites with zero errors/failures/skips. Inspect the javap method and original tianji.http.postgres.test.url guard. The source gate does not supply the property or select any PostgreSQL test. Staged scope is exactly the four named paths; the immutable SQL/POM path output is empty.

### Separate operator-only I1

Use the existing fresh isolated PostgreSQL class guard. Provide a local database named with the tianji_it_ prefix, no existing tianji_agent schema and approved fixture authentication outside the URL. The operator creates and later disposes of that separate database. Run the exact method from the same Service working directory and retain the fresh owning XML. No extra listener, application or provider is part of this method.

```sh
: "${TIANJI_S01_PRIVATE_PG_URL:?Provide an authorized fresh loopback fixture URL}"
s01_reactor "-Dtest=AgentRunHttpPostgresTest#$s01_method" \
  "-Dtianji.http.postgres.test.url=$TIANJI_S01_PRIVATE_PG_URL"
```

One executed, passing method and no skips are required for the S01 I observation. Source G1/G2 never executes this command.

## 9. Migration, Compatibility, Rollout, and Rollback

No production migration, rollout or configuration change. Existing integration fixture methods and all production APIs remain. Roll back by reverting the focused test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G1/I1 |
| REQ-003 | Step 1 | 1 | I1 |
| REQ-004 | Step 1 | 1–4 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL integration needs a separately authorized fresh fixture and report. This test does not establish deployed IAM, automatic production queue discovery, live browser/network behavior or runtime performance.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AS test owns the fixture | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing MockMvc, ADK and ledger | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing module dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Reuse strict fixture and wire checks | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing Jackson decoding only | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | No new runtime logger | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK/Spring/JUnit/ADK | None |
| MC-JSON-001 | Applicable | PASS | §7.3/10 | Existing JsonMapper and payload | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing UTC and timeout contracts | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Current test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite vs integration provenance | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source scope independent of later I | None |

### 12.6 Final verdict

PASS — Ready for user review
