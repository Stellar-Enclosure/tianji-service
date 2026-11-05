# T4-03 V16 产品原生续跑探针实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-57-agent-checkpoint-native-resume.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 02:57 Asia/Shanghai |
| Updated | 2026-11-06 02:57 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Existing V16 PG fixture, private test handoff and protocol tests, three documents |
| Source Requirement | Master §17.2 S11 and §19 T4-03 |
| Baseline Revision | Service 8126c32db1f303f858cbc317cf6db0db4d7ab1f1; Components 6d26bf8724e8993b631db68c95b7238d7a93fb8c |
| Implements Spec | [V16 product native resume Spec](../spec/2026-11-06-02-56-agent-checkpoint-native-resume.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 02:56 Asia/Shanghai SHA-256 2af32b5957e5b90ffa24f8ff6c642b371b859bb42284e633404e903c6fd7afc8 |
| Effective Specs | [V16 product native resume Spec](../spec/2026-11-06-02-56-agent-checkpoint-native-resume.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Prepare one operator-only V16 test that resumes the ordinary product producer's exact native invocation after forced process death while the original Run lease remains live. Source gates exercise finite protocol and capture checks and inspect bytecode.

## 2. Target Spec and Effective Design

The [V16 product native resume Spec](../spec/2026-11-06-02-56-agent-checkpoint-native-resume.md) §§4/7/10/14 defines the test-only continuation. The existing V16 product process-death fixture and [Components bridge §7/§14](../../../../Stellar-Enclosure-Components/docs/egon/spec/2026-11-06-02-33-agent-flow-explicit-resume.md) govern the captured producer, roles and explicit native lifecycle. Master §17.2 S11 and §19 T4-03 govern scope. I1 needs separate operator execution.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/11/14 | Both codecs with ordinary product V16 writer, same exact cut across death and original Run still live | PG fixture |
| REQ-002 | §4/7/9/11/14 | Exact recorded invocation under successor Session, one Fake, stable raw repeat, unchanged product evidence | PG fixture |
| REQ-003 | §4/5/7/10/13 | Existing phases 0/1 remain valid. Phase 2 requires bound digest and closed frame and child guards. | Handoff and fixture |
| REQ-004 | §4/8/14/16 | Typed R1, 14 finite cases, compiled guards and scoped six paths and 28 SQL | Review evidence |

## 4. Implementation Strategy and Dependency Order

Load the whole test-compiling A set together. The handoff test and PG fixture are final B files during RED. Only the handoff serializer has a typed A mutation that writes RECONCILE for native resume. Record the intended one assertion. Replace only the mutated serializer with B, then run finite and class-only gates. Stage the exact three test files and three documents. PostgreSQL and test child invocation belong to operator I1.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR V16 fork and exact Component bridge | §6–8 | Test-only child phases with real production writer and explicit Runner | S1 |
| Validation | Existing DB guard plus private handoff | §5/7/10 | Guard exact scope, roles, pipe and marker before DB effects | R1/G1/G2/I1 |
| Time and isolation | Existing DB clock, JDK child lifecycle and V16 role gate | §5/6.3/11 | Bound all waits and fault only the exact owned original Session lease | G2/I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Product checkpoint witness | AgentCheckpointCapturePostgresTest, AdkAgentRunExecutor | Existing owner/role setup, pre-reservation estimator and normal product Runner | Fits | Reuse | Private operator method only | Step 1 MC-REUSE-001 |
| Child isolation | Existing V9 probe and JDK ProcessBuilder | Test-only child gate, stdin framing, timeout and reaping | Fits | Reuse pattern with separate V16 payload | One additional phase on existing private helper | Step 1 MC-VALID-001 |
| Native continuation | AgentFlowSessionRunner#withExplicitResume and PostgresAdkSessionService | Existing trusted bridge and active Run-bound successor Session | Fits | Reuse | Exact fixture assertions | Step 1 MC-REUSE-001 |

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
| Rule 1 | §7/8/10 | Existing AR test naming | Files 1–3 | Exact operator/helper/test symbols | G2 | Step 1 | PASS |
| Rule 2 | §5/7/10 | Existing operator role gate and bounded pipe | Files 1–3 | Reject ambiguous inputs, verify exact tenant/run/cut | R1/G1/I1 | Step 1 | PASS |
| Rule 3 | §10 | Immutable private values | Files 2/3 | Use private test records and redacted textual forms | G1/G2 | Step 1 | PASS |
| Rule 4 | §6/8 | Existing fixture objects | Files 1–3 | No new business bean or logging | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing JDK, JDBC, JUnit and ADK | Files 1–3 | No library or new utility dependency | G1/G2 | Step 1 | PASS |
| Rule 6 | §10/11 | Existing typed model and database observations | Files 2/3 | Keep binary private handoff and existing status representations | S1 | Step 1 | N/A |
| Rule 7 | §5/16 | Existing test fixture opt-in | File 3 | No application profile change | S1 | Step 1 | N/A |
| Rule 9 | §13 | Direct fixture reuse | Files 1–3 | No production pattern or architecture change | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing DB clock and bounded waits | File 3 | Use current UTC instant and monotonic scheduling conventions | G2/I1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AR and AgentDB | All | Preserve production contracts and all 28 migrations | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Lease expiry, deadline, checkpoint captured_at | Occurred instant | java.time.Instant and database timestamptz | UTC instant | Internal | Existing primary DB | No display change | Original Run lease remains live and exact. The fixture fault expires the original Session only. | DB clock_timestamp() and existing precision | Existing schema | Step 1 I1 TEST-004 |
| TIME-002 | Child waits and estimator park | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | 16-second child wait, 32-second estimator cap, 150-second test | JDK monotonic wait | Test-only | Step 1 G2/I1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-56-agent-checkpoint-native-resume.md` |
| Create | `docs/egon/plan/2026-11-06-02-57-agent-checkpoint-native-resume.md` |
| Create | `docs/egon/evidence/2026-11-06-agent-checkpoint-native-resume.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service baseline 8126c32db1f303f858cbc317cf6db0db4d7ab1f1 and Components baseline 6d26bf8724e8993b631db68c95b7238d7a93fb8c. Freeze the exact A/B source digests, Java 25, the existing offline reactor and finite selectors. Keep actual PG flags outside source gates. Existing capture, rollback, reconciliation and reader modes retain their selectors. I1 requires a separate authorized fresh loopback primary, existing role split and vector, POSIX private workspace, exact child PID and original live Run. Only the original Session lease is intentionally expired in the native-resume branch.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare the V16 product-native continuation probe

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing V16 process fork and exact Component withExplicitResume bridge.
- Baseline state: Existing V16 capture, reconciliation and reader paths are present. Exact product-origin native continuation under the original Run is absent.
- Observable outcome: One typed RED then 14 passing finite tests, exact compiled I1 method and guards, six paths.
- End state: Three AR test files and three review documents in one commit.
- Test-first gate: Required. Put all three A-stage files in place before invoking the finite RED selector. Only the handoff serializer uses its typed mutation. Compilation failure, wrong assertion or skip invalidates RED.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`

- Purpose: Define typed RED expectation and finite phase guards.
- Symbols: AgentCheckpointForkHandoffTest#round_trips_native_resume_as_a_distinct_digest_bound_phase and existing frame cases.
- Repository evidence: Existing phase 0/1 tests and shared bounded frame, Spec EVD-003.
- Dependencies and consumers: Existing JUnit fixture and File 2 phase API.
- Why now: Freeze expected phase 2 independently of serializer behavior.
- Contract/signature changes: New focused round-trip case, preserved legacy byte assertions and unknown-phase rejection.
- Input/output and state mapping: Both codec frames retain 0/1 and new native frame decodes to NATIVE_RESUME with exact digest and byte 2.
- Error and edge behavior: Invalid digest or null phase, native with no digest, capture with digest and malformed/truncated/trailing/oversize frames fail closed.
- Standards impact: MC-VALID-001, MC-UTIL-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5.
- Implementation pseudocode:

```java
var decoded = receive(send(nativeRequestFor(codec, originalCutDigest)));
assertEquals(Phase.NATIVE_RESUME, decoded.phase());
assertEquals(2, nativeFrame[phaseOffset(nativeFrame)]);
assertRejectsMissingOrMisplacedDigestAndUnknownPhase();
```

- Verification contribution: R1/G1.
- After this file: Final B test source installed, R1 waits for Files 2 and 3 so all tests compile.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`

- Purpose: Extend the same bounded private frame with exactly one new phase.
- Symbols: AgentCheckpointForkHandoff#send/#receive, Phase and Request.
- Repository evidence: Existing format 1 and 512-byte frame, Spec EVD-003.
- Dependencies and consumers: Files 1 and 3.
- Why now: Provide the typed A compile shape and one precise serializer fault before B.
- Contract/signature changes: Explicit phase codes 0/1/2 and marker name, replacing previous capture/reconcile boolean in the same byte.
- Input/output and state mapping: Preserve canonical loopback URL, roles, vector, codec and UUID. Capture has no digest. Each replacement requires lowercase 64-hex digest. Request representation redacted.
- Error and edge behavior: Unknown phase, absent/misplaced digest or malformed frame rejected before DB.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-UTIL-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5.
- Implementation pseudocode:

```java
validateFormat1AndBoundedFields();
int code = switch (phase) { case CAPTURE -> 0; case RECONCILE -> 1; case NATIVE_RESUME -> 2; };
// A only writes code 1 for NATIVE_RESUME, so valid input round-trips to the wrong phase.
encodePhaseAndDigest(code, phase);
decodeClosedPhaseAndRejectTrailingBytes();
```

- Verification contribution: R1/G1.
- After this file: A serializer in place before R1. Replace it with B after the exact assertion.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: Prepare actual isolated ordinary-origin V16 continuation and keep whole testCompile compatible with the phase API.
- Symbols: AgentCheckpointCapturePostgresTest#resumes_product_first_input_checkpoint_after_process_death_under_original_run_lease, main, verifyProductCheckpointForks, resumeNativeFork and native witness helpers.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing V16 fixture, current Session store and locked Component bridge, exact operator selector.
- Why now: Install final B fixture before R1 compilation. Source gates never select its PG method.
- Contract/signature changes: One new independently gated operator method and additional private branch and witness.
- Input/output and state mapping: Both codec variants, ordinary A prebudget cut, parent exact Session-only expiry, B same original invocation under next Session epoch. Handoff digest binds exact original Run owner and expiry, checkpoint and premodel history. Preserve raw prefix and released successor lease.
- Error and edge behavior: Any wrong scope, invalid digest, original Run expiry, stale Session CAS, extra Fake, extra user event, product ledger effect, marker/PID mismatch or cleanup failure fails I1.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var cut = killAndReapOrdinaryCaptureAfterVerifiedCommit();
var before = captureOriginalRunLeaseAndRawNativePrefix(cut);
expireOnlyExactOriginalSessionLeaseUsingDbTimeAndCas(before);
// Child B independently checks digest and the original live Run before acquiring the new Session.
var lease = store.acquireSessionLease(sessionId, before.runLease(), leaseDuration);
var bound = store.withLease(lease);
var runner = AgentFlowSessionRunner.withExplicitResume(rootFromStoredConfig, app, bound, plugins);
assertSameInvocationOneFakeAndNoDuplicateInput(runner.runAsync(userId, sessionId, invocationId, null, config, null));
assertCompletedRepeatPreservesRawHistoryAndProviderCount();
releaseReplacementSessionAndVerifyUnchangedOriginalRunAndCheckpoint(before);
```

- Verification contribution: G2/I1, testCompile for R1/G1.
- After this file: Run actual R1 with the typed mutation, then replace only File 2 by B and run G1/G2.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-02-56-agent-checkpoint-native-resume.md`

- Purpose: Trace ordinary V16 native continuation to the master.
- Symbols: REQ-001–004, UC-001–003 and TEST-001–004.
- Repository evidence: Existing V16 fork, V9 probe and explicit Components bridge.
- Dependencies and consumers: Existing master and reviewer.
- Why now: Freeze role, lease, timing and scope distinctions.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Existing facts to exact proposed witness and gates.
- Error and edge behavior: Any unsupported broader recovery claim or scope mismatch blocks review.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Trace the ordinary product cut and original live Run fence to the S11 subset.
Require exact observer and child scope for both codecs.
Keep native continuation, reconciliation and product takeover as distinct evidence.
```

- Verification contribution: S1.
- After this file: Strict Spec and exact source boundaries are available.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-02-57-agent-checkpoint-native-resume.md`

- Purpose: Freeze ordered files, source gates and operator command.
- Symbols: Step 1, R1/G1/G2/S1 and I1.
- Repository evidence: Effective Spec, current test source and reviewed baseline.
- Dependencies and consumers: File 4 and reviewer.
- Why now: Provide executable test-first and class-only verification.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Exact RED/B candidates to fresh XML, javap, scoped commit and future I1.
- Error and edge behavior: Unexpected compile, reports or paths stop the Step.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock baseline, Spec digest, RED/B candidates and six exact paths.
Require one expected R1 assertion, fourteen G1 passes and actual G2 compiled entry.
Keep I1 opt-in and absent from source gates.
```

- Verification contribution: S1.
- After this file: Strict Plan supports one source commit.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-native-resume.md`

- Purpose: Record actual source execution, finite counts and limits.
- Symbols: R1/G1/G2/S1 with pending I1.
- Repository evidence: Actual reactor, compiled class and migration byte comparisons.
- Dependencies and consumers: Previous files and reviewer.
- Why now: Supply proof for exact Step without misreporting live acceptance.
- Contract/signature changes: Evidence document only.
- Input/output and state mapping: Fresh outputs and scoped verification to one redacted review entry.
- Error and edge behavior: Absent or skipped report cannot satisfy I1.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact failed assertion and actual finite cases.
Record compiled operator method, both flags and private child gate with no PG report.
Confirm 28 unchanged production migration bytes and distinguish pending I1.
```

- Verification contribution: S1.
- After this file: Six exact paths ready to stage and review.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Execute §8 R1/G1/G2/S1. I1 is independent.
- Expected result: One intended RED, 14 passing finite tests, exact compiled gate and scope, no PG report.
- Failure returns to: Files 1–3 for exact typed RED, Files 1–3 for source validation, Files 4–6 for evidence/scope.
- Completion criteria: Actual finite/compile/source evidence, exact staged paths and I1 pending.
- Rollback: Revert the focused test-only source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `docs/egon/spec/2026-11-06-02-56-agent-checkpoint-native-resume.md`, `docs/egon/plan/2026-11-06-02-57-agent-checkpoint-native-resume.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-native-resume.md`.
- Commit: test(agent): prepare V16 product native resume probe.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | A serializer plus B test and B fixture | Exact AgentCheckpointForkHandoffTest#round_trips_native_resume_as_a_distinct_digest_bound_phase, one assertion expected NATIVE_RESUME but actual RECONCILE, zero errors/skips. |
| G1 | All B, clean finite reactor | AgentCheckpointForkHandoffTest 5, AdkResumeProbeHandoffTest 4 and AdkCheckpointCaptureTest 5. Require 14 total. |
| G2 | Actual compiled fixture javap, no launch | Exact new method, existing operator modes, both parent flags, private child switch/main and absent PG report. |
| S1 | Strict docs and exact stage | Six paths and 28 unchanged production SQL. |
| I1 | Separately authorized operator invocation | One unskipped passing method executing both codecs under the same live original Run lease. |

```sh
native_base=8126c32db1f303f858cbc317cf6db0db4d7ab1f1
native_component=6d26bf8724e8993b631db68c95b7238d7a93fb8c
native_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
native_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
test "$(git rev-parse --verify HEAD)" = "$native_base"
test "$(git -C ../Stellar-Enclosure-Components rev-parse --verify HEAD)" = "$native_component"
native_finite() {
  JAVA_HOME="$native_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
    ./scripts/verify-source-reactor.sh -gs "$native_settings" -s "$native_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
native_finite '-Dtest=AgentCheckpointForkHandoffTest#round_trips_native_resume_as_a_distinct_digest_bound_phase'
native_finite clean '-Dtest=AgentCheckpointForkHandoffTest,AdkResumeProbeHandoffTest,AdkCheckpointCaptureTest'
ar_target=stellar-enclosure-tianji-agent-runtime/target
native_test=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointCapturePostgresTest
"$native_jdk/bin/javap" -p -v -classpath "$ar_target/test-classes" "$native_test"
test ! -e "$ar_target/surefire-reports/TEST-$native_test.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-02-56-agent-checkpoint-native-resume.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-02-57-agent-checkpoint-native-resume.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Verify fresh owning XML. Run R1 and G1 only with their exact A/B bytes, and inspect the actual compiled guards for G2. No PostgreSQL flag belongs to the source gates. Stage only the six exact paths and compare all 28 production migration bytes.

### Separate operator-only I1

Use a new isolated loopback primary named tianji_it_checkpoint_<name>, no tianji_agent schema, exact public vector patch, separately provisioned restricted runtime role and POSIX private temp workspace. Both local fixture identities must authenticate with no credentials in URL, argv or inherited child environment. Run only the exact method. Operator owns database setup and cleanup and retains fresh scoped XML. An unavailable or expired original Run fence fails this method.

```sh
: "${TIANJI_CHECKPOINT_NATIVE_PG_URL:?Provide a fresh authorized disposable loopback database URL}"
: "${TIANJI_CHECKPOINT_NATIVE_RUNTIME_ROLE:?Provide the verified restricted fixture login role}"
: "${TIANJI_CHECKPOINT_NATIVE_VECTOR_VERSION:?Provide the exact preinstalled vector version}"
native_finite '-Dtest=AgentCheckpointCapturePostgresTest#resumes_product_first_input_checkpoint_after_process_death_under_original_run_lease' \
  -Dtianji.postgres.checkpoint.capture=true \
  -Dtianji.postgres.checkpoint.capture.native-resume=true \
  "-Dtianji.postgres.checkpoint.capture.url=$TIANJI_CHECKPOINT_NATIVE_PG_URL" \
  "-Dtianji.postgres.checkpoint.capture.runtime-role=$TIANJI_CHECKPOINT_NATIVE_RUNTIME_ROLE" \
  "-Dtianji.postgres.checkpoint.capture.vector.version=$TIANJI_CHECKPOINT_NATIVE_VECTOR_VERSION"
```

Require one actual unskipped passing method. A failed run is negative acceptance evidence. Further runs require a fresh authorized isolated fixture.

## 9. Migration, Compatibility, Rollout, and Rollback

Existing SQL, configuration, capture writer and Run state machine stay outside this source diff. Operator-only fixture applies unchanged V1–V16 to its disposable primary. Revert the focused source commit if needed.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 3 | G2/I1 |
| REQ-002 | Step 1 | 3 | G2/I1 |
| REQ-003 | Step 1 | 1–3 | R1/G1/G2/I1 |
| REQ-004 | Step 1 | 1–6 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL, process death and native continuation require operator evidence. An expired original Run, scope or authentication mismatch, unexpected child exit or timeout fails the isolated fixture. Current execution authority, transactional expired-Run takeover and complete S11 remain separate design or acceptance work.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR persistence test owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing V16 process fork, Session store and explicit native Runner | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Reactor, JUnit, Spring AI and PG fixture already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Bounded handoff, guarded database roles and exact persisted witness | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable private request and witness values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Reactor and JUnit | None |
| MC-JSON-001 | Not applicable | N/A | §7.3/10 | Existing typed events and payload maps | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB clock and monotonic child wait | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Independent test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct reuse of existing test-owned children and Session adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Six exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 12.6 Final verdict

PASS — Ready for user review
