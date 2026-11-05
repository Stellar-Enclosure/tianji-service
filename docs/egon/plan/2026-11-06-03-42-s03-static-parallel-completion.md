# S03 静态原生并行分支完成实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:42 Asia/Shanghai |
| Updated | 2026-11-06 03:42 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Two existing AR Java paths and three review documents |
| Source Requirement | Master §6.2, §17.2 S03, §19 T1-02/T3-09 |
| Baseline Revision | 816b159a41b6b1312b2861c73a39088d5c2bd508 |
| Implements Spec | [Static parallel completion](../spec/2026-11-06-03-41-s03-static-parallel-completion.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:41 Asia/Shanghai SHA-256 6d015d526e54f68b993001ec1af6fd48db9f9d8e0c46d5bf5fc4b8beb1660ab2 |
| Effective Specs | [Static parallel completion](../spec/2026-11-06-03-41-s03-static-parallel-completion.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Prove the existing incomplete static Parallel success with one typed RED, add the narrow post-stream check, verify finite and package gates and commit the five exact paths.

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Primary Spec](../spec/2026-11-06-03-41-s03-static-parallel-completion.md) §§4/7/14 fixes exact static topology and success conditions. The master supplies source execution scope.

### 2.2 Effective Spec set

The linked Spec controls this Step. Existing native Session, Run claim, checkpoint, ownership and authority contracts remain effective.

### 2.3 Superseded or excluded content

No supersession. Dynamic transfer, loops, governed tools, Native Gate, product startup and S03 I remain independent.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7 | Required output names for exact static topology, no overcounted sequential intermediate | AR executor |
| REQ-002 | §7/9/14 | Missing terminal produces exact FAILED dependency result with empty output and retained projections | AR executor and test |
| REQ-003 | §8/14/16 | Complete and delegated controls; precise finite/package gates; 28 SQL byte parity | Scope and evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock exact clean baseline. Apply File 1 only and capture actual R1. Apply File 2, run G1 and clean P1, then record exact evidence and commit.

### 4.2 Test-first strategy

One exact RED must fail expected FAILED and actual COMPLETED because native projected output lacks a required branch. A compile error, timeout, unrelated assertion or skip is not the intended RED.

### 4.3 Sequential and parallel boundaries

One source Step. Run exact R1, then G1/P1. PostgreSQL, application, provider and other integration commands remain outside this Step.

### 4.4 Commit boundaries

One focused commit after five-path review. Scratch and generated outputs stay out of staging.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity and evidence | Direct alternative | Plan decision |
| --- | --- | --- | --- |
| Static required branch check | EVD-001–003 | Current nonempty output check misses a missing sibling | Add private completion check |
| New workflow layer | No separate owner | Existing executor and Native Gate cover known paths | No new layer |

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Files | Gate |
| --- | --- | --- | --- |
| Test-only RED | Current actual native Runner fixture | File 1 | R1 |
| Exact completion guard | Intended RED | File 2 | G1/P1 |
| Review evidence | Actual gate outputs and scope | Files 3–5 | S1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Current native adapter and fenced Agent ledger | Keep exact private execution scope |
| Validation | Actual compiled graph and projected output names | Require complete terminal output only for proved static graph |
| Error | Existing AgentLedgerException result mapping | Fixed dependency error and no raw branch names |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Compiled graph | BaseAgent, pinned workflow classes | EVD-002/003 | Sufficient for exact static shape | Reuse actual topology | No new dependency | Step 1 MC-REUSE-001 |
| Terminal projection | AdkEventProjector and agentOutputs | EVD-002/003 | Reliable final text authors | Reuse current map keys | One local required set | Step 1 MC-UTIL-001 |
| Regression | AdkNativeAgentCompletionTest | EVD-004 | Actual bounded Fake Runner | Reuse fixture | Three focused tests | Step 1 MC-TEST-001 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–2 | Existing executor, test and pinned native SDK types | G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–2 | Validate exact expected outputs before writing a successful result | G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–2 | No new POJO, mapper or transfer contract | G1/P1/S1 | Step 1 | N/A |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–2 | No Spring Bean or new logger | G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–2 | JDK Set plus pinned ADK and existing JUnit fixture | G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–2 | No JSON mapping or persisted enum change | G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–2 | No profile or configuration change | G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–2 | Direct scoped recursive invariant, without a new orchestration path | G1/P1/S1 | Step 1 | N/A |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–2 | No timestamp, deadline or conversion change | G1/P1/S1 | Step 1 | N/A |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–2 | Existing AR native adapter and Agent ledger ownership | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: The completion decision uses compiled topology and projected author names. Existing Run deadline, lease, event and test time semantics remain the same.

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s03-static-parallel-completion.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Verify exact Service HEAD 816b159a41b6b1312b2861c73a39088d5c2bd508, JDK25 and existing offline settings. Use the pinned ADK 1.13.0 class semantics. Stage no application, configuration, SQL, permissions or dependencies. Preserve any previous resume code already committed to the same executor.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Require all static native Parallel terminal outputs

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing compiled topology, projected output map and actual Runner fixture.
- Baseline state: One usable output is sufficient for ordinary root success.
- Observable outcome: A proved static parallel graph with a missing terminal branch fails before durable success.
- End state: Two Java paths and three documents in one commit.
- Test-first gate: Required, File 1 only.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`

- Purpose: Reproduce missing required parallel output.
- Symbols: AdkNativeAgentCompletionTest#static_parallel_completion_rejects_missing_required_branch_output.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime and source reviewer.
- Why now: Capture exact RED before changing completion.
- Contract/signature changes: Existing finite fixture or review artifact only.
- Input/output and state mapping: Use the existing actual Runner and two deterministic Fake model paths. The blank right terminal yields old COMPLETED. Cover nested Parallel under Sequential, a blank intermediate with valid terminal and delegated control.
- Error and edge behavior: A missing required terminal or wrong verification result stops the corresponding path.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
var run = rig.executeWithFactory(staticParallelWithOneBlankTerminal);
assertEquals(FAILED, run.status());
assertEquals(TIANJI_DEPENDENCY_UNAVAILABLE.name(), persisted.errorCode());
assertUsefulProjectedOutputsRemain();
```

- Verification contribution: R1/G1.
- After this file: Exact R1-ready test-only state.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: Guard actual compiled static branch output.
- Symbols: AdkAgentRunExecutor completion, requireStaticParallelOutputs and collectStaticParallelOutputs.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime and source reviewer.
- Why now: Follow the ordered source and evidence gates.
- Contract/signature changes: Private completion helpers only.
- Input/output and state mapping: After the existing empty-output check and Gate handling, only ordinary tool-free execution evaluates the exact static graph and verifies the required author names. Reuse existing fenced failure on missing terminal output.
- Error and edge behavior: A missing required terminal or wrong verification result stops the corresponding path.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
if (activeNativeGate == null && toolScope == null) {
    requireStaticParallelOutputs(root, agentOutputs.keySet());
}
// Collect only proved exact built-in leaves; Parallel requires every child terminal.
// Sequential contributes its final child and still visits nested Parallel nodes.
```

- Verification contribution: G1/P1.
- After this file: Exact source or review artifact ready for its gate.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md`

- Purpose: Lock required static semantics.
- Symbols: REQ-001–003.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime and source reviewer.
- Why now: Follow the ordered source and evidence gates.
- Contract/signature changes: Existing finite fixture or review artifact only.
- Input/output and state mapping: Document proved scope, terminal algorithm, dynamic exclusions, failure behavior and traceability.
- Error and edge behavior: A missing required terminal or wrong verification result stops the corresponding path.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record strict static topology and exact output requirement.
Bind RED, all positive controls and artifact scope.
Trace each requirement to the native Runner, current result path, exact finite selector and package gate.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact ready for its gate.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md`

- Purpose: Lock the source and validation order.
- Symbols: Step 1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime and source reviewer.
- Why now: Follow the ordered source and evidence gates.
- Contract/signature changes: Existing finite fixture or review artifact only.
- Input/output and state mapping: Pin baseline and Spec hash. Stage the test-only RED before B. Record exact reports and scope.
- Error and edge behavior: A missing required terminal or wrong verification result stops the corresponding path.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Verify current baseline.
Apply File 1 and prove intended R1.
Apply File 2 and run G1 then P1.
Stage only five locked paths after strict verification.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact ready for its gate.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-s03-static-parallel-completion.md`

- Purpose: Record observed validation and package evidence.
- Symbols: R1/G1/P1/S1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime and source reviewer.
- Why now: Follow the ordered source and evidence gates.
- Contract/signature changes: Existing finite fixture or review artifact only.
- Input/output and state mapping: Record observed report, actual artifact, scope and pending runtime gates.
- Error and edge behavior: A missing required terminal or wrong verification result stops the corresponding path.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record R1 and owning test counts plus actual archives and parity.
Verify all 28 protected SQL resources and note the unchanged PaC and configuration comparison.
Keep S03 runtime acceptance explicit.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact ready for its gate.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact R1/G1/P1/S1.
- Expected result: One intended RED, all 60 finite tests at G1 and P1, exact package and scope parity.
- Failure returns to: File 1 for wrong RED, File 2 for behavior, Files 3–5 for evidence and scope.
- Completion criteria: Record actual R1, G1, P1 and S1 evidence.
- Rollback: Revert the one focused source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md`, `docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md`, `docs/egon/evidence/2026-11-06-s03-static-parallel-completion.md`.
- Commit: fix(agent): require static parallel branch outputs.

## 8. Test, Validation, and Quality Gates

```sh
s03_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s03_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s03_base=816b159a41b6b1312b2861c73a39088d5c2bd508
test "$(git rev-parse --verify HEAD)" = "$s03_base"
s03_red='AdkNativeAgentCompletionTest#static_parallel_completion_rejects_missing_required_branch_output'
s03_core=AdkNativeAgentCompletionTest,AdkGovernedProcessLoopTest,AdkCheckpointCaptureTest
s03_finite() {
  JAVA_HOME="$s03_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$s03_settings" -s "$s03_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
s03_finite "$s03_red"
s03_finite "$s03_core"
JAVA_HOME="$s03_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify   ./scripts/verify-source-reactor.sh -gs "$s03_settings" -s "$s03_settings" -o clean -B -ntp   -Ppermission-producer "-Dpermission.commit=$s03_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s03-static-parallel-completion   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$s03_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and boundary |
| --- | --- |
| R1 | File 1 only. Exact selected method, one intended FAILED-versus-COMPLETED assertion, zero errors/skips. |
| G1 | 17 native completion, 37 governed loop and 6 capture tests. Zero failures/errors/skips. |
| P1 | Clean verify repeats 60 finite tests. Confirm actual AR class in AS, equal AS/PS config and PaC after excluding build identity, all 28 SQL bytes equal and no PostgreSQL report. |
| S1 | Both strict docs, exact five paths and no POM/config/permission/DB changes. |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration, public API, permission or configuration changes. Preserve existing dynamic and successful static behavior. Revert one source commit if required.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–2 | R1/G1 |
| REQ-002 | Step 1 | 1–2 | R1/G1 |
| REQ-003 | Step 1 | 1–5 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

The narrow source correction has no external runtime prerequisite. Actual S03 PostgreSQL, native provider and full branch failure and cancellation matrix remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing Agent Runtime infrastructure adapter | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002–005 | Existing native projected outputs and existing Runner fixture | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Current JDK, ADK and JUnit | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Existing executor and exact semantic tests | None |
| MC-VALID-001 | Applicable | PASS | §7.3/14 | Guard only proved static required branch terminal outputs | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No new carrier | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Reuse native projection and existing result | None |
| MC-LOG-001 | Applicable | PASS | §7.3/15 | Fixed failure diagnostic, no branch identifiers | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No Spring Bean changes | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JDK Set and exact ADK node semantics | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing output contract and error enum | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No time change | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No profile or enablement | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Small invariant in existing runner completion path | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Five exact paths and 28 protected SQL files | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite R1 and G1 plus package gate | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Bounded source gate, independent runtime acceptance | None |

### 12.6 Final verdict

PASS — Ready for user review
