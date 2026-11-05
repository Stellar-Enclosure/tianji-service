# T4-05 固定 Agent 质量评估实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-22-fixed-agent-quality-evaluation.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 01:22 Asia/Shanghai |
| Updated | 2026-11-06 01:22 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One AR fixture and test, three documents, one commit |
| Source Requirement | Master §19 T4-05 and §17.2 S07 |
| Baseline Revision | Service 1605f9aebf25445076d611d661b86038ea5e18d8 |
| Implements Spec | [Fixed Agent quality evaluation](../spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 01:21 Asia/Shanghai SHA-256 e8ee05e7ec6997861d3d23738671e6a2dee596a879ba3b9cd915e2eb2afd3b45 |
| Effective Specs | [Fixed Agent quality evaluation](../spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Add five fixed sample assertions and one redacted, versioned local quality summary under the existing real Runner and Fake model test rig. The unit remains finite and uses the existing mock ports.

## 2. Target Spec and Effective Design

[Fixed Agent quality evaluation](../spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md) §§4/7/14 defines the evaluation. The existing S07 injection corpus and runtime owners stay separate. Current master authorizes scoped source proof. A real provider or runtime gate needs separate authority and evidence.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/10 | Exact five fixed samples with versioned fixture SHA and precise result assertions | Fixture and owning test |
| REQ-002 | §4/7/11 | Existing real Runner and budget plugin produce expected tool and mock-ledger observations | Owning test |
| REQ-003 | §4/7/15 | One bounded marker with versions, hashes, actual aggregates and explicit unavailable/not-run fields | Surefire and evidence |
| REQ-004 | §4/14/16 | Exact RED, selected finite GREEN, five paths and unchanged 27 SQL | One commit |

## 4. Implementation Strategy and Dependency Order

Use the existing Rig and add test assertions in A. Give its new fixture one deliberately wrong expected count and run the exact RED method. Replace that count with its reviewed B value after the exact failure. Run the existing owning test class and adjacent native completion suite, verify the summary and preserve the six injection cases. Stage exact files only.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | AR test owns real Runner and test mock ports | §6–8 | Add a method and resource to the existing test vertical | S1 |
| Validation and outcomes | Existing Rig uses real policy and budget logic | §7/10 | Check per-sample status, output, transport/model and budget observations | R1/G1 |
| JSON and safe report | Existing Jackson 3 fixture reader | §7/15 | Hash original bytes, print one redacted test marker | G1/G2 |
| Storage, time and scope | Mockito boundaries and local elapsed timing | §6.3/11 | No runtime activation, SQL, POM or SLO claim | G2/S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Actual Runner and Fake | Existing AdkGovernedProcessLoopTest | Rig#executor and FakeChatModel | Fits | Direct reuse | Private test methods only | Step 1 MC-REUSE-001 |
| Budget and policy | Existing plugin and Rig ports | Ledger reservation/settlement stubs, estimator, trusted tool scope | Fits | Verify existing behavior | No production changes | Step 1 MC-VALID-001 |
| Versioned sample and report | Existing JSON resource and Surefire | Jackson parsing and owning JUnit reports | Fits | Keep report in exact finite case | One test resource and marker | Step 1 MC-JSON-001 |

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
| Rule 1 | §7/8 | Existing AR test and JSON style | Files 1–2 | Semantic method name and fixture path | G1 | Step 1 | PASS |
| Rule 2 | §7/10 | Existing Rig and policy gates | Files 1–2 | Test exact inputs, statuses and counters | R1/G1 | Step 1 | PASS |
| Rule 3 | §10 | Existing DTOs | File 1 | No production POJO or mapping changes | S1 | Step 1 | N/A |
| Rule 4 | §6/8 | Test-local collaborators only | File 1 | No production logger or Bean | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing JDK/JUnit/Mockito/Jackson | Files 1–2 | Existing library APIs and JDK digest/timing | G1/G2 | Step 1 | PASS |
| Rule 6 | §7/10 | Existing Jackson test fixtures | Files 1–2 | Closed fixed JSON and safe report | G1/G2 | Step 1 | PASS |
| Rule 7 | §5/16 | No application property changes | All | Keep profile unaffected | S1 | Step 1 | N/A |
| Rule 9 | §13 | Direct five-case evaluation | File 1 | Existing rig, no business pattern expansion | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Elapsed local test observation | File 1 | Use monotonic timing and report a duration | G2 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AR test owner | All | No production, dependency or SQL change | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Local observed evaluation duration | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | Surefire observation only | Sum of five local execution intervals, no threshold | Monotonic nanoTime converted to reported duration | Existing runtime clocks stay unchanged | Step 1 G2 TEST-003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/agent-run-quality-v1.json` |
| Create | `docs/egon/spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md` |
| Create | `docs/egon/plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md` |
| Create | `docs/egon/evidence/2026-11-06-fixed-agent-quality-evaluation.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Clean exact Service 1605f9aebf25445076d611d661b86038ea5e18d8, existing JDK25 and offline reactor, all 27 migration bytes preserved. A/B Java is byte-identical. The fixture differs only in authorized-read.expected.reads, 0 in A and 1 in B. Do not run PostgreSQL, app, collector, provider, browser or listener. Existing 6 injection dynamic cases stay unchanged. Retain only redacted summary data in tracked evidence.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Record versioned fixed Agent quality evaluation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing AR Rig, FakeChatModel, governance plugin, test Maven reactor.
- Baseline state: Existing injection and native tests, no fixed quality method or dedicated fixture.
- Observable outcome: Exact RED rejects the deliberately wrong count, then all 5 verified quality samples produce one bounded versioned summary under existing finite gates.
- End state: Two test paths and three docs in one commit.
- Test-first gate: Required. Run one named method against typed A with a deliberately wrong expected tool dispatch count; demand one exact assertion and no errors/skips. Use B for G1/G2.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`

- Purpose: Evaluate exact five fixed cases and emit one safe summary.
- Symbols: AdkGovernedProcessLoopTest#fixed_fake_agent_quality_v1, existing Rig and FakeChatModel.
- Repository evidence: EVD-002/003 show actual local executor and existing mock ports.
- Dependencies and consumers: Existing AR Rig/Mockito/Runner and File 2. Surefire owns the report.
- Why now: Define the expected witness and A failure before green fixture proof.
- Contract/signature changes: One extra @Test and fixture-local test support only.
- Input/output and state mapping: Closed case input to exact status/output, tool verdict and verified counters.
- Error and edge behavior: Unexpected field, output, counter, budget or policy observation fails before final report.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001 and MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test void fixed_fake_agent_quality_v1() {
    var locked = readExistingJacksonFixtureAndHashOriginalBytes();
    for (var sample : locked.fiveFixedSamples()) {
        // Execute the existing real executor/Runner and production plugin with one local Rig.
        var result = runBoundedFake(sample, existingRig);
        assertExactStatusStructuredOutputAndToolDecision(result, sample);
        assertObservedTransportModelAndReserveSettlementFacts(sample, existingRig);
    }
    emitOneRedactedVersionedSummary(locked.sha256(), verifiedCounters, observedDuration);
}
```

- Verification contribution: R1/G1/G2.
- After this file: Exact method available for A and B.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/agent-run-quality-v1.json`

- Purpose: Lock five synthetic scenarios and their exact expected facts.
- Symbols: evaluation/agent-run-quality-v1.json.
- Repository evidence: EVD-004 and the owning injection resource conventions.
- Dependencies and consumers: File 1 and Maven test resources.
- Why now: Supply the sole controlled RED mutation and correct B expectations.
- Contract/signature changes: Private versioned JSON fixture.
- Input/output and state mapping: One scenario per locked intent and deterministic expected counters/output.
- Error and edge behavior: A differs in the selected tool-dispatch expectation only. Missing or altered data fails File 1.
- Standards impact: MC-VALID-001, MC-JSON-001 and MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 6, Rule 11.
- Implementation pseudocode:

```text
Create one fixed version and five stable vectors: baseline, read, forged START, shared tool cap, context cap.
Lock expected structured result, observed tool policy and deterministic model/transport/reserve/settle evidence.
In A mutate only the designated expected dispatch count. In B restore its correct reviewed value.
Hash original B bytes and verify that exact digest against the versioned Surefire observation.
```

- Verification contribution: R1/G1/G2.
- After this file: A and B have exact independent identities; original injection fixture remains.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md`

- Purpose: Record scope and accepted design for the fixed U evidence.
- Symbols: REQ-001–004, UC-001/002, TEST-001–003.
- Repository evidence: Master and existing finite evidence.
- Dependencies and consumers: Existing review readers and File 4.
- Why now: Capture the settled model/output/policy/usage scope before staging.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Exact five vectors, traceability and operator boundary.
- Error and edge behavior: Spec must pass strict validation.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact test/resource ownership, fixed vector subjects and actual Runner plus budget evidence.
Name the metric boundary, one safe summary and citation NOT_AVAILABLE/model NOT_RUN fields.
Preserve application, provider and production SLO boundaries in REQ-004 and §16.
```

- Verification contribution: S1.
- After this file: Strict Spec ready for reviewer.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md`

- Purpose: Lock exact source phases and tests.
- Symbols: Step 1 and R1/G1/G2/S1.
- Repository evidence: Spec and current reactor.
- Dependencies and consumers: File 3 and repository reviewer.
- Why now: Capture exact digest and selector before committing.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Five paths, one RED, finite selectors, report marker checks and clean stage.
- Error and edge behavior: Any mismatched source baseline or validation result stops staging.
- Standards impact: MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Bind effective Spec SHA and one exact step against the current clean Service HEAD.
Require R1 exact method/one expected tool count mismatch, and exact owning green suite.
Record checked test summary, immutable migrations and the five commit paths before staging.
```

- Verification contribution: S1.
- After this file: Strict Plan ready for reviewer.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-fixed-agent-quality-evaluation.md`

- Purpose: Record actual observed test facts.
- Symbols: R1/G1/G2/S1.
- Repository evidence: Fresh owning Surefire and exact commit scope.
- Dependencies and consumers: All prior files and source reviewer.
- Why now: Replace placeholders after actual execution.
- Contract/signature changes: Evidence document only.
- Input/output and state mapping: Counts, fixture/report hashes and explicit runtime boundary.
- Error and edge behavior: Skip or generic failure cannot be reported as RED or GREEN.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact expected RED assertion and actual finite GREEN suite counts with no skipped results.
Record one redacted marker, test fixture SHA and independent NOT_AVAILABLE/NOT_RUN evidence fields.
Record strict docs and byte-checked immutable SQL scope, with live provider and SLO still pending.
```

- Verification contribution: S1.
- After this file: Evidence supports the one source commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Execute exact §8 R1, G1, G2 and S1 at their named phases.
- Expected result: One expected dispatch-count assertion, no unexpected results, then complete green and one bounded versioned marker.
- Failure returns to: Files 1–2 for behavior/fixture, Files 3–5 for reporting and scope.
- Completion criteria: Five vectors and adjacent finite pass, exact marker, old corpus untouched, five staged paths.
- Rollback: Revert the one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/agent-run-quality-v1.json`, `docs/egon/spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md`, `docs/egon/plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md`, `docs/egon/evidence/2026-11-06-fixed-agent-quality-evaluation.md`.
- Commit: test(tianji): record versioned fixed Agent quality evaluation.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | A test and one fixture mutation | One authorized-read fixed read dispatch count assertion, expected 0 and actual 1, zero errors/skips |
| G1 | B test and correct fixture | Owning class plus native completion, 49 tests at the reviewed fixture, zero failures/errors/skips |
| G2 | Fresh B Surefire report and copied test resource | Exactly one redacted TIANJI_AGENT_QUALITY_V1 marker with locked fixture SHA, version, exact counts, local duration, NOT_AVAILABLE and NOT_RUN |
| S1 | Strict docs and exact stage | Five paths, old injection fixture and all 27 SQL unchanged |

```sh
quality_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
quality_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
quality_base=1605f9aebf25445076d611d661b86038ea5e18d8
quality_finite() {
  JAVA_HOME="$quality_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$quality_settings" -s "$quality_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
test "$(git rev-parse --verify HEAD)" = "$quality_base"
# A: run one exact method and inspect the one intended failure in its owning XML.
quality_finite '-Dtest=AdkGovernedProcessLoopTest#fixed_fake_agent_quality_v1'
# B: replace A with exact reviewed B before running the complete two finite classes.
quality_finite '-Dtest=AdkGovernedProcessLoopTest,AdkNativeAgentCompletionTest'
shasum -a 256 stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/agent-run-quality-v1.json   stellar-enclosure-tianji-agent-runtime/target/test-classes/evaluation/agent-run-quality-v1.json
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Run only the applicable command at each source phase. Capture fresh XML before changing A. Inspect B's owning Surefire XML for one `TIANJI_AGENT_QUALITY_V1 ` JSON marker. Require reportVersion=1, mode=FAKE_PROTOCOL, fixtureSha256=13859e47ba042e921db911b5e2a7caa2a1e0df9e06c16c99b55fb3374a336e19, samples=5, structuredOutputMatches=5, toolSelectionMatches=5, budgetMatches=5, modelCalls=6, modelReservations=6, modelSettlements=6, observedDurationNanos at least zero, citationQuality=NOT_AVAILABLE and realModelQuality=NOT_RUN. Verify the definition/schema/model/route/policy/prompt-hash/tool refs match the fixed synthetic definition. Five matched cases includes the expected denied cases. Compare the test resource bytes against source; validate no new data gets into the marker. Verify six current injection cases still execute. No test report or status is credited by copying or reading fixtures alone.

## 9. Migration, Compatibility, Rollout, and Rollback

Scope is test and review only. Existing fixture and runtime keep their contracts. Revert the single test-only commit to remove this evaluation.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–2 | R1/G1 |
| REQ-002 | Step 1 | 1–2 | R1/G1 |
| REQ-003 | Step 1 | 1–2, 5 | G1/G2 |
| REQ-004 | Step 1 | 1–5 | R1/G1/S1 |

## 11. Risks, Blockers, and User Decisions

This is fixed Fake U evidence. Real-model quality, citation scoring, live S07 I/E and environment-qualified latency/throughput require independent operator inputs and reports.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | Existing AR test owner | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003 | Existing Rig and real Runner | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing dependencies only | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact method and resource | None |
| MC-VALID-001 | Applicable | PASS | §7/10/14 | Exact vectors and expected assertions | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs and test values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No production mapping | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One sanitized Surefire marker | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing Jackson/JUnit/JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Closed test resource and limited evidence | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Local elapsed duration only | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | No profile | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct local Rig | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Five paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact RED and finite regression | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator follow-up separate | None |

### 12.6 Final verdict

PASS — Ready for user review
