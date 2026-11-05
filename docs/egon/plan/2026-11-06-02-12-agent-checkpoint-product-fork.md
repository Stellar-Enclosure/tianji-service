# T4-03 产品 checkpoint 强制退出探针实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-12-agent-checkpoint-product-fork.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 02:12 Asia/Shanghai |
| Updated | 2026-11-06 02:12 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Existing V16 PG operator fixture, one private test handoff, one finite handoff test, three documents |
| Source Requirement | Master §17.2 S11 and §19 T4-03 |
| Baseline Revision | Service 7013768227b778ce762ca18f72c4cb24b5facd3f |
| Implements Spec | [Product checkpoint fork Spec](../spec/2026-11-06-02-11-agent-checkpoint-product-fork.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 02:11 Asia/Shanghai SHA-256 490169c0d4db2facb2c3027e4318bd7b6f063344c4e3f097f50f68d9cce3cb00 |
| Effective Specs | [Product checkpoint fork Spec](../spec/2026-11-06-02-11-agent-checkpoint-product-fork.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Prepare one operator-only case for the product V16 checkpoint surviving process death and for the expired Run's exact SUSPENDED disposition. Actual source gates exercise only finite protocol, capture and storage checks and inspect compiled bytecode.

## 2. Target Spec and Effective Design

The [Product checkpoint fork Spec](../spec/2026-11-06-02-11-agent-checkpoint-product-fork.md) §§4/7/10/14 defines the test-only case. The existing V16 writer design and original fixture continue to define checkpoint shape, current roles and normal Runner path. The master governs the source Step. Separate operator authorization is required for I1.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/11/14 | Both codecs with product writer and exact committed cut before and after force-kill | PG fixture |
| REQ-002 | §4/7/9/11/14 | Exact selected Run is the only due candidate and receives one SUSPENDED/LEASE_EXPIRED disposition with no execution | PG fixture |
| REQ-003 | §4/5/7/10/13 | Valid bounded role/DB/cut handoff, private marker and child lifecycle, finite rejections | Handoff and fixture |
| REQ-004 | §4/8/14/16 | Actual RED, 18 finite cases, bytecode and six exact paths with 28 SQL preserved | Review evidence |

## 4. Implementation Strategy and Dependency Order

Run the exact missing-class RED with the one RED handoff-test draft alone. Replace it with its full B draft, add the helper, then add the existing PG fixture extension. Run finite regressions and class-only inspection. Write actual evidence and stage only the six paths. The operator fixture never runs in the source Step.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR V16 fixture and earlier V9 test fork | §6–8 | Test-only child roles with real production writer/reconciler | S1 |
| Validation | Existing DB guard plus private handoff | §5/7/10 | Guard exact scope, roles, pipe and marker before DB effects | R1/G1/G2/I1 |
| Time and isolation | Existing DB clock, JDK child lifecycle and V16 role gate | §5/6.3/11 | Bound all waits and fault only the owned original lease | G2/I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Product checkpoint witness | AgentCheckpointCapturePostgresTest, AdkAgentRunExecutor | Existing owner/role setup, pre-reservation estimator and normal product Runner | Fits | Reuse | Private operator method only | Step 1 MC-REUSE-001 |
| Child isolation | Existing V9 probe and JDK ProcessBuilder | Test-only child gate, stdin framing, timeout and reaping | Fits | Reuse pattern with separate V16 payload | One small private helper | Step 1 MC-VALID-001 |
| Due Run disposition | AgentLedgerService.reconcileDue | Bounded current expiry flow and status event | Fits | Reuse | Exact fixture assertions | Step 1 MC-REUSE-001 |

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
| TIME-001 | Lease expiry, deadline, checkpoint captured_at | Occurred instant | java.time.Instant and database timestamptz | UTC instant | Internal | Existing primary DB | No display change | Exact lease-only fault before total deadline | DB clock_timestamp() and existing precision | Existing schema | Step 1 I1 TEST-004 |
| TIME-002 | Child waits and estimator park | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | 16-second child wait, 32-second estimator cap, 150-second test | JDK monotonic wait | Test-only | Step 1 G2/I1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-02-11-agent-checkpoint-product-fork.md` |
| Create | `docs/egon/plan/2026-11-06-02-12-agent-checkpoint-product-fork.md` |
| Create | `docs/egon/evidence/2026-11-06-agent-checkpoint-product-fork.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service baseline 7013768227b778ce762ca18f72c4cb24b5facd3f. Use reviewed frozen RED/B digests, JDK 25, the existing offline source reactor and no PostgreSQL properties in source gates. Preserve original capture/rollback I case, all production files and all 28 existing migrations. I1 requires a separately authorized fresh local primary, V16 fixture role split, exact vector patch and private POSIX workspace. Force-kill only the exact child launched by this test and inject expiry only on its verified synthetic Run.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare the V16 product process-death and suspension probe

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing V16 product writer and operator fixture, bounded V9 child-process fixture and current Agent ledger.
- Baseline state: Product cut is observable in-process and native V9 continuation has a separate source probe. Exact V16 process-death method/handoff are absent.
- Observable outcome: One finite RED then 18 passing finite tests, exact compiled opt-in operator method and six paths. Actual operator proof remains separate.
- End state: Three AR test files and three review documents in one commit.
- Test-first gate: Required. Apply only the RED test draft and require one exact missing-class assertion; a compile error, unrelated failure, skip or unexpected pass invalidates RED.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`

- Purpose: Define exact test-only handoff RED and finite GREEN contract.
- Symbols: AgentCheckpointForkHandoffTest#disposable_checkpoint_fork_handoff_protocol_exists and three canonical protocol cases.
- Repository evidence: Spec EVD-002/005; existing bounded V9 fork handoff pattern.
- Dependencies and consumers: JUnit only in RED; final helper for finite GREEN.
- Why now: Require actual missing-class evidence before implementation.
- Contract/signature changes: New test-only class.
- Input/output and state mapping: RED uses Class.forName with assertDoesNotThrow; B round-trips both codec variants and phase digest, verifies redaction and rejects malformed frames.
- Error and edge behavior: Invalid role/database, duplicate/trailing/noncanonical boolean, truncation and size cap fail closed.
- Standards impact: MC-VALID-001, MC-UTIL-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff"));
assertEquals(request, receive(send(request))); // both codec modes and capture/reconcile
assertThrows(IllegalArgumentException.class, () -> receive(malformedFrame));
```

- Verification contribution: R1/G1.
- After this file: Actual RED retained and B waits for helper.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`

- Purpose: Serialize a bounded private request between test-owned JVMs.
- Symbols: AgentCheckpointForkHandoff#send/#receive/#databaseName/#tenant and Request.
- Repository evidence: Spec EVD-002/005.
- Dependencies and consumers: Existing V16 operator test and File 1 finite contract.
- Why now: Implement only the private frame required by both new child roles.
- Contract/signature changes: New package-private test helper and immutable nested Request.
- Input/output and state mapping: Format 1, maximum 512 bytes, canonical URL/roles/version/Run UUID, boolean codec and optional cut digest. Distinct roles, fixed tenant per codec and redacted representation.
- Error and edge behavior: Reject malformed/truncated/trailing, wrong format, oversize, role/URL/digest mismatch or noncanonical flag before DB.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-UTIL-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5.
- Implementation pseudocode:

```java
validateCanonicalRequestFields(request);
writeFormat1AndBoundedFields(pipe, request);
var bytes = input.readNBytes(513);
requireSizeAndFormat1ThenRejectTrailingInput(bytes);
return validatedRequestWithOptionalCutDigest(bytes);
```

- Verification contribution: G1.
- After this file: New and existing pure protocol suites can pass without DB.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: Observe actual V16 product capture before and after test-owned process death, then suspend the exact expired Run through existing reconciliation.
- Symbols: AgentCheckpointCapturePostgresTest#forced_process_exit_preserves_checkpoint_and_reconciles_expired_run_without_execution, private child main, fixture role guards, fork cut and exact expiry/reconciliation assertions.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing product executor/Fake, V16 provisioning and AgentLedgerService; later operator uses exact single selector.
- Why now: Compose the real write/disposition path using bounded helper after R1.
- Contract/signature changes: One opt-in test method, private test process entry and test-local helpers only.
- Input/output and state mapping: Both codec modes, test-owned parent/capture/reconcile JVMs, validated database and full cut, exact expiry CAS then one suspension/status event.
- Error and edge behavior: Reject stale/foreign lease, other due runs, missing child marker, bad PID or role, altered digest, extra lifecycle transitions, model/tool/budget/terminal output, or failed reaping.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
for (boolean typed : List.of(false, true)) {
    var run = createExactOwnedV16FixtureWithThreeMinuteDeadline(typed);
    // Child A executes forRuntime(..., true) and parks before any reservation.
    var cut = verifyObserverCutAndCaptureMarker(run);
    killAndReapOwnedCaptureChild();
    verifySameCutAndZeroExecutionEvidence(cut);
    expireExactlyStillLiveRunLeaseUsingDbTimeAndCas(cut);
    // Child B independently checks role/history/cut/only due row, then runs reconcileDue(1).
    requireIndependentChildCompletionWithSameDigest(cut);
    assertSingleSuspendedLeaseExpiredEventAndOriginalCut(run, cut);
}
```

- Verification contribution: G2/I1.
- After this file: Actual class compiles and is inspected. I1 remains unexecuted.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-02-11-agent-checkpoint-product-fork.md`

- Purpose: Trace exact product durability and suspension evidence to the master.
- Symbols: REQ-001–004, UC-001–003 and TEST-001–004.
- Repository evidence: Existing product writer and V9 test fork.
- Dependencies and consumers: Existing master and reviewer.
- Why now: Freeze role, lease, timing and scope distinctions.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Existing facts to exact proposed witness and gates.
- Error and edge behavior: Any unsupported broader recovery claim or scope mismatch blocks review.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Trace existing product capture and expired Run facts to the S11 subset.
Require exact observer and child scope for both codecs.
Keep native continuation and product takeover as distinct evidence.
```

- Verification contribution: S1.
- After this file: Strict Spec and exact source boundaries are available.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-02-12-agent-checkpoint-product-fork.md`

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
Require one expected R1 assertion, eighteen G1 passes and actual G2 compiled entry.
Keep I1 opt-in and absent from source gates.
```

- Verification contribution: S1.
- After this file: Strict Plan supports one source commit.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-product-fork.md`

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
- Expected result: One intended RED, 18 passing finite tests, exact compiled gate and scope, no PG report.
- Failure returns to: File 1 for exact RED, Files 1–3 for source validation, Files 4–6 for evidence/scope.
- Completion criteria: Actual finite/compile/source evidence, exact staged paths and I1 pending.
- Rollback: Revert the focused test-only source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `docs/egon/spec/2026-11-06-02-11-agent-checkpoint-product-fork.md`, `docs/egon/plan/2026-11-06-02-12-agent-checkpoint-product-fork.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-product-fork.md`.
- Commit: test(agent): prepare product checkpoint process-death probe.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | Apply only File 1 RED draft | Exact AgentCheckpointForkHandoffTest#disposable_checkpoint_fork_handoff_protocol_exists, one wrapped ClassNotFoundException assertion, zero errors/skips |
| G1 | All B, clean finite reactor | New and previous handoff 4 + 4, capture and storage 5 + 5, all pass |
| G2 | Actual compiled fixture javap, no launch | Original/new method, both operator flags, private child switch and main, no PostgreSQL report |
| S1 | Strict docs and exact stage | Six paths and all 28 previous production SQL unchanged |
| I1 | Separate authorized operator invocation | Both codec branches within one executed passing method, exact forced exit and one suspension disposition |

```sh
fork_base=7013768227b778ce762ca18f72c4cb24b5facd3f
fork_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
fork_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
test "$(git rev-parse --verify HEAD)" = "$fork_base"
fork_finite() {
  JAVA_HOME="$fork_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$fork_settings" -s "$fork_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
fork_finite '-Dtest=AgentCheckpointForkHandoffTest#disposable_checkpoint_fork_handoff_protocol_exists'
fork_finite clean '-Dtest=AgentCheckpointForkHandoffTest,AdkResumeProbeHandoffTest,AdkCheckpointCaptureTest,AgentCheckpointStorageVerifierTest'
ar_target=stellar-enclosure-tianji-agent-runtime/target
fork_test=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointCapturePostgresTest
"$fork_jdk/bin/javap" -p -v -classpath "$ar_target/test-classes" "$fork_test"
test ! -e "$ar_target/surefire-reports/TEST-$fork_test.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict   docs/egon/spec/2026-11-06-02-11-agent-checkpoint-product-fork.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict   docs/egon/plan/2026-11-06-02-12-agent-checkpoint-product-fork.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Use the RED and B commands only in their exact phases. Require fresh owning XML and inspect the compiled test/method guards. Never supply the PostgreSQL flags to source gates. Verify exact six staged paths and previous migration bytes.

### Separate operator-only I1

Use a fresh isolated loopback primary with existing checkpoint database name guard, no tianji_agent schema, exact preinstalled public vector patch and an explicitly provisioned restricted runtime role. Validate local fixture-only authentication without credentials in URL or inherited child environment. The setup account and runtime role must both be able to connect under the existing fixture. Run only the exact single method; operator owns setup/teardown and retains fresh XML separately.

```sh
: "${TIANJI_CHECKPOINT_FORK_PG_URL:?Provide a fresh authorized disposable loopback database URL}"
: "${TIANJI_CHECKPOINT_FORK_RUNTIME_ROLE:?Provide the verified restricted fixture login role}"
: "${TIANJI_CHECKPOINT_FORK_VECTOR_VERSION:?Provide the exact preinstalled vector version}"
fork_finite '-Dtest=AgentCheckpointCapturePostgresTest#forced_process_exit_preserves_checkpoint_and_reconciles_expired_run_without_execution'   -Dtianji.postgres.checkpoint.capture=true   -Dtianji.postgres.checkpoint.capture.fork=true   "-Dtianji.postgres.checkpoint.capture.url=$TIANJI_CHECKPOINT_FORK_PG_URL"   "-Dtianji.postgres.checkpoint.capture.runtime-role=$TIANJI_CHECKPOINT_FORK_RUNTIME_ROLE"   "-Dtianji.postgres.checkpoint.capture.vector.version=$TIANJI_CHECKPOINT_FORK_VECTOR_VERSION"
```

Require one actual unskipped passing method. Treat failure as evidence and preserve the scope of the original fixture. This action is outside source execution.

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

Actual process death and the database witness need separate operator evidence. The scope is durable product capture and existing expiry disposition. Native continuation, takeover, full S11 and production activation require separate implementation or acceptance.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR persistence test owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing V16 capture fixture, Runner and reconciliation | None |
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
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct existing test-owned children and fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Six exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and operator integration evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gate independent of later I1 | None |

### 12.6 Final verdict

PASS — Ready for user review
