# T4-03 持久 ADK 双进程恢复探针实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-00-32-adk-durable-resume-fork.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 00:32 Asia/Shanghai |
| Updated | 2026-11-06 00:32 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One AR test-only source Step, compiled child and finite pipe, later isolated I |
| Source Requirement | Master §19 T4-03 S11 process-rebuild candidate |
| Baseline Revision | Service 8fa8474a2880242c7ef6824a285eea15a0adca3f |
| Implements Spec | [ADK durable resume fork](../spec/2026-11-06-00-31-adk-durable-resume-fork.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 00:31 Asia/Shanghai SHA-256 f47ef864e13d67373489c8d111e2da7f429cd19aca71a67638ab470868ba1ddc |
| Effective Specs | [Fork Spec](../spec/2026-11-06-00-31-adk-durable-resume-fork.md), [Existing probe](../spec/2026-11-05-19-58-adk-durable-resume-probe.md#7-architecture-design) |
| Depends On Plans | [Existing durable probe](2026-11-05-20-01-adk-durable-resume-probe.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

One exact AR source-only test commit. Presence RED, pure bounded handoff, actual compilation of the distinct child case, finite native and owner evidence, and explicit future operator-only I. The source gate never runs PostgreSQL or child JVMs.

## 2. Target Spec and Effective Design

[Fork Spec](../spec/2026-11-06-00-31-adk-durable-resume-fork.md) §4/7/14 extends the existing probe test only. Prior production Run/Session behavior and three existing probe methods remain. S11 is independent.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7/14 | Distinct opt-in local process death test compiles | Existing probe test |
| REQ-002 | §7/14 | Same original live Run, new Session owner, explicit native invocation and two modes represented in source | Existing probe test |
| REQ-003 | §7/10/14 | Actual bounded strict handoff finite and true RED | New test protocol and test |
| REQ-004 | §14/16/18 | Finite/source passes only, no PG/child I execution claims | Evidence |

## 4. Implementation Strategy and Dependency Order

Stage the reflection-safe missing-class test and run R1. Add finite pipe implementation and full tests, then the existing probe's only new opt-in method and child helpers. Run a clean finite build and inspect compiled bytecode. Capture observed source evidence, then one exact commit. Future I has separate prerequisites and no authorization from this Plan.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing AR test-only package | §7/8 | Three exact test paths, no runtime scope | G2/S1 |
| Reuse | Current fixed ADK Runner, ledger/Session/Flyway | §6/7 | One added test, existing original owner invariants | G1/G2 |
| Validation and time | Existing dedicated local URL and leases | §5/6.3 | Strict 1024-byte frame, exact local primary and bounded child | G1/G2 |
| Dependency and storage | Existing V1–V9 and fixtures | §11 | No new SQL or dependency | S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Saved native invocation | Existing ADK probe | Fixed SDK Runner and Fake | Fits | Keep | New process fixture only | Step 1 MC-REUSE-001 |
| Local database/lease | Existing Session fixture | Existing V1–V9 and exact actor/owner | Fits | Keep | Bound child read of same source | Step 1 MC-VALID-001 |
| Process handoff | JDK stream and ProcessBuilder | Existing JDK APIs | Fits | Use exact test binary | No dependency | Step 1 MC-UTIL-001 |

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
| Rule 1 | §8/10 | AR existing fixture names | Files 1–3 | Exact test and immutable handoff names | G1/G2 | Step 1 | PASS |
| Rule 2 | §5/7 | Current unique opt-ins and guards | Files 1–3 | Bounded exact validation | R1/G1 | Step 1 | PASS |
| Rule 3 | §10 | Current test value records | File 2 | Immutable Request and redaction | G1 | Step 1 | PASS |
| Rule 4 | §6/8 | No Spring business bean | All | No new logging or bean | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing JDK and pinned SDK | Files 1–3 | JDK stream/process/time, no dependency | G1 | Step 1 | PASS |
| Rule 6 | §7/10 | Existing codecs unchanged | Files 2/3 | Test-only binary, no JSON edits | S1 | Step 1 | N/A |
| Rule 7 | §5/16 | Test-only JVM flags | File 3 | No application profile or runtime flag | G2 | Step 1 | N/A |
| Rule 9 | §13 | Direct finite fixture | File 3 | No business design pattern | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing Instant and test bounds | Files 2/3 | Exact time and monotonic wait | G1/G2 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AR test package | All | No production or SQL change | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | run.expiresAt | Occurred instant | java.time.Instant | UTC instant | Internal | Explicit UTC Instant | Not applicable: test only | Exact epoch seconds/nanos | Original Run deadline | No production wire change | Step 1 G1 TEST-002 |
| TIME-002 | child timeout and lease durations | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: test only | 12s child, 75s test, existing fixture lease | Monotonic wait | Prior methods retained | Step 1 G2 TEST-003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoffTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md` |
| Create | `docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md` |
| Create | `docs/egon/evidence/2026-11-06-adk-durable-resume-fork.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Clean exact Service `8fa8474a2880242c7ef6824a285eea15a0adca3f`. Existing JDK25/offline source reactor, exact six paths and all 27 original SQL unchanged. No PG flags or child execution in current source gates. Separate I requires operator-created fresh disposable primary on 127.0.0.1, accepted fixture authentication for both children and POSIX temp directory. No credentials, actual app/provider/browser or grant in this scope.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare bounded dual-process ADK Session probe

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Exact reviewed test drafts, existing Source86 probe, current pinned ADK and JDK25.
- Baseline state: Existing three probe methods and no fork handoff helper.
- Observable outcome: Bounded test-only child flow compiles and finite protocol passes; I remains separately selectable.
- End state: Three test files and three docs in one source commit.
- Test-first gate: Required. One genuine class absence RED, then exact finite green.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoffTest.java`

- Purpose: Freeze genuine missing protocol and four pure tests.
- Symbols: disposable_resume_handoff_protocol_exists; preserves_the_exact_test_run_and_independent_capture_and_resume_facts; rejects_non_disposable_or_ambiguous_handoffs_before_any_connection; rejects_unknown_format_truncation_trailing_bytes_and_an_oversized_pipe.
- Repository evidence: Existing JUnit and exact AR test package.
- Dependencies and consumers: Original baseline for RED, File 2 for finite B.
- Why now: Observe actual missing class before source implementation.
- Contract/signature changes: Reflection RED then strict test-only binary fixture input and output.
- Input/output and state mapping: Reflection RED then strict test-only binary fixture input and output.
- Error and edge behavior: Reject malformed framing, unsupported version, wrong local names, oversized/truncated/trailing and invalid booleans.
- Standards impact: MC-SCOPE-001 and R1 exact failure, then four G1 finite passes.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test void disposable_resume_handoff_protocol_exists() {
    assertDoesNotThrow(() -> Class.forName(HANDOFF_FQN)); // R1 wraps ClassNotFoundException.
}
@Test void preserves_the_exact_test_run_and_independent_capture_and_resume_facts() {
    assertEquals(capture, receive(encode(capture)));
    assertEquals(resume, receive(encode(resume)));
}
// In B, validate local URL, fixed owner/epoch, truncation, trailing bytes and size with in-memory streams.
```

- Verification contribution: R1 exact failure, then four G1 finite passes.
- After this file: Red test first; B test after File 2.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoff.java`

- Purpose: Bound private child handoff without production credentials.
- Symbols: AdkResumeProbeHandoff#databaseName, send, receive; Request.
- Repository evidence: Existing fixture URL, original Run lease, JDK DataInputStream/DataOutputStream.
- Dependencies and consumers: File 1 finite and File 3 probe.
- Why now: Freeze test input before implementing child main.
- Contract/signature changes: Format 1, max1024 bytes, exact fixture identity, owner/epoch, expiry and paired optional invocation/prior-owner digest.
- Input/output and state mapping: Format 1, max1024 bytes, exact fixture identity, owner/epoch, expiry and paired optional invocation/prior-owner digest.
- Error and edge behavior: Reject unknown/truncated/overlimit/trailing/noncanonical input before database.
- Standards impact: MC-SCOPE-001 and G1 four pure pipe tests; no database use.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
static Request receive(InputStream pipe) throws IOException {
    byte[] bytes = pipe.readNBytes(MAX_BYTES + 1);
    requireBoundedVersionOne(bytes);
    var input = new DataInputStream(new ByteArrayInputStream(bytes));
    var request = readExactRunAndOptionalInvocation(input);
    if (input.read() != -1) throw invalid();
    return request;
}
// Send writes the same exact field order; Request validates loopback fixture, fixed synthetic owner and epoch.
```

- Verification contribution: G1 four pure pipe tests; no database use.
- After this file: Protocol B ready; copy final File 1.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java`

- Purpose: Add actual separate child candidate to existing opt-in probe.
- Symbols: resumes_saved_pre_model_invocation_after_the_original_process_is_killed, main, startChild, captureChild, resumeChild.
- Repository evidence: Existing Source86 test class and pinned SDK/local fixture.
- Dependencies and consumers: File 2, old Runner/Session/ledger and current test classpath.
- Why now: Only after finite protocol is ready.
- Contract/signature changes: Parent double opt-in, two codec modes, old Run lease passed to both child JVMs, distinct Session owner and committed prefix proof.
- Input/output and state mapping: Parent double opt-in, two codec modes, old Run lease passed to both child JVMs, distinct Session owner and committed prefix proof.
- Error and edge behavior: Exact disposable guard, bounded private markers/children, forced kill and reap, no fallback or product state.
- Standards impact: MC-SCOPE-001 and G2 actual class/main compiled only; separate operator I pending.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@EnabledIfSystemProperty(named = "tianji.postgres.resume.probe.fork", matches = "true")
void resumes_saved_pre_model_invocation_after_the_original_process_is_killed() {
    for (boolean typedWrites : List.of(false, true)) {
        var fixture = new Fixture(typedWrites, false);
        var original = startChild(captureRequest(fixture), captureMarker);
        awaitCapture(original, captureMarker);
        destroyAndReap(original);
        var saved = verifyCommittedOriginalEventAndExpireOnlyItsSessionOwner(fixture);
        var replacement = startChild(resumeRequest(fixture, saved), resumeMarker);
        verifyDistinctChildNewSessionEpochAndSameInvocation(replacement, fixture, saved);
    }
}
// Child checks exact fixture DB and history, uses fixed Fake and original Run lease; bounded pipe and markers.
```

- Verification contribution: G2 actual class/main compiled only; separate operator I pending.
- After this file: B test compiles, do not execute current gate.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md`

- Purpose: Archive exact reviewed SPEC.
- Symbols: Canonical governing doc.
- Repository evidence: Current strict docs workflow.
- Dependencies and consumers: Exact approved bytes and SHA.
- Why now: Preserve reviewed source scope.
- Contract/signature changes: Copy one exact approved file.
- Input/output and state mapping: Copy one exact approved file.
- Error and edge behavior: Occupied path or wrong SHA blocks source.
- Standards impact: MC-SCOPE-001 and S1 strict bytes/hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md
printf '%s  %s\n' "$FORK_APPROVED_SPEC_SHA" "$FORK_APPROVED_SPEC" | shasum -a 256 -c -
cp -n "$FORK_APPROVED_SPEC" docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md
cmp "$FORK_APPROVED_SPEC" docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md
```

- Verification contribution: S1 strict bytes/hash.
- After this file: Reviewed source document archived.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md`

- Purpose: Archive exact reviewed PLAN.
- Symbols: Canonical governing doc.
- Repository evidence: Current strict docs workflow.
- Dependencies and consumers: Exact approved bytes and SHA.
- Why now: Preserve reviewed source scope.
- Contract/signature changes: Copy one exact approved file.
- Input/output and state mapping: Copy one exact approved file.
- Error and edge behavior: Occupied path or wrong SHA blocks source.
- Standards impact: MC-SCOPE-001 and S1 strict bytes/hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md
printf '%s  %s\n' "$FORK_APPROVED_PLAN_SHA" "$FORK_APPROVED_PLAN" | shasum -a 256 -c -
cp -n "$FORK_APPROVED_PLAN" docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md
cmp "$FORK_APPROVED_PLAN" docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md
```

- Verification contribution: S1 strict bytes/hash.
- After this file: Reviewed source document archived.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-adk-durable-resume-fork.md`

- Purpose: Record actual finite and compiled source evidence.
- Symbols: R1/G1/G2/S1.
- Repository evidence: Existing Source86 evidence.
- Dependencies and consumers: Fresh actual reports and class inspection.
- Why now: Record only observed current source evidence.
- Contract/signature changes: Actual test, compile and immutable path findings.
- Input/output and state mapping: Actual test, compile and immutable path findings.
- Error and edge behavior: No PG/child or S11 claim.
- Standards impact: MC-SCOPE-001 and S1 exact source closure.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the actual R1 missing-class assertion and four protocol plus existing finite counts.
Record actual classfile/flags and no PG test report, exact source paths and all 27 immutable SQL.
Mark operator child/DB experiment, product takeover and S11 independently pending.
```

- Verification contribution: S1 exact source closure.
- After this file: Stage six paths and commit once.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/G1/G2/S1 in §8; do not run separate I command.
- Expected result: One ClassNotFound assertion, 12 selected finite passes, exact compiled child and no probe report.
- Failure returns to: File 1 RED; Files 1/2 protocol; File 3 static child compile.
- Completion criteria: Actual finite and compile/source evidence and exact scope. Separate I pending.
- Rollback: Revert one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoffTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoff.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java`, `docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md`, `docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md`, `docs/egon/evidence/2026-11-06-adk-durable-resume-fork.md`.
- Commit: test(agent): prepare durable ADK process death probe.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | File 1 RED on unchanged original baseline | AdkResumeProbeHandoffTest#disposable_resume_handoff_protocol_exists, one wrapped ClassNotFound assertion |
| G1 | All B, clean offline finite selectors | 4 protocol + 3 native + 5 recovery = 12 passed, zero errors/skips |
| G2 | javap actual compiled test class, no launch | Original and new method, exact opt-in/child main/protocol, no PG report |
| S1 | strict source/docs | Six exact paths, all 27 prior SQL unchanged |

```sh
fork_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
fork_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
test "$(git rev-parse --verify HEAD)" = 8fa8474a2880242c7ef6824a285eea15a0adca3f
fork_finite() {
  JAVA_HOME="$fork_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$fork_settings" -s "$fork_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
fork_finite '-Dtest=AdkResumeProbeHandoffTest#disposable_resume_handoff_protocol_exists'
fork_finite clean -Dtest=AdkResumeProbeHandoffTest,AdkNativeResumeSemanticsTest,AgentRecoveryDispositionEvidenceTest
ar_target=stellar-enclosure-tianji-agent-runtime/target
fork_test=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AdkDurableResumeBoundaryPostgresTest
"$fork_jdk/bin/javap" -p -v -classpath "$ar_target/test-classes" "$fork_test"
test ! -e "$ar_target/surefire-reports/TEST-$fork_test.xml"
```

Run R1 only with exact RED. Inspect current compiled four probe methods, main, dedicated URL, both probe flags and child gate from javap. No test invocation for the child, PostgreSQL probe or provider. Report fresh finite XML and exact output.

### Separate operator-only I, not part of source execution

Operator provides a fresh local disposable primary without tianji_agent, matching exact fixture URL, and verifies driver local authentication works without credentials in URL or inherited child environment. Parent provisions unchanged V1–V9 only there. Use exactly the single new method, not the whole class or a broad suite.

```sh
: "${TIANJI_RESUME_FORK_PROBE_URL:?Provide a fresh authorized disposable loopback fixture URL}"
fork_finite '-Dtest=AdkDurableResumeBoundaryPostgresTest#resumes_saved_pre_model_invocation_after_the_original_process_is_killed' \
  -Dtianji.postgres.resume.probe=true -Dtianji.postgres.resume.probe.fork=true \
  "-Dtianji.postgres.resume.probe.url=$TIANJI_RESUME_FORK_PROBE_URL"
```

Expected actual report one method executing both codec cases, zero failures/errors/skips. Treat failure as probe evidence, no fallback. The source gate never executes this block.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git add -- \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoffTest.java \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoff.java \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java \
  docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md \
  docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md \
  docs/egon/evidence/2026-11-06-adk-durable-resume-fork.md
git diff --cached --check
git diff --cached --name-status
```

Expect exactly six paths and empty immutable scope output. Review and commit once.

## 9. Migration, Compatibility, Rollout, and Rollback

No production migration or runtime configuration. Separate isolated I uses existing V1–V9 only in fresh disposable fixture. One source-only revert.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 3 | G2 and separate I |
| REQ-002 | Step 1 | 3 | G2 and separate I |
| REQ-003 | Step 1 | 1/2 | R1/G1 |
| REQ-004 | Step 1 | 1–6 | G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

No-tool proof is a pending isolated hypothesis. Separate operator fixture/I and full S11 remain outside source approval. A new Session owner under original lease supplies no product Run takeover proof.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | existing AR test owner | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002/003 | existing services/toolchain | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | no dependencies | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | semantic test/value names | None |
| MC-VALID-001 | Applicable | PASS | §7.2/14 | exact isolation guard | None |
| MC-MODEL-001 | Applicable | PASS | §10 | test-local immutable facts | None |
| MC-CONVERT-001 | Not applicable | N/A | §8/10 | no production mapper | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | no business logger | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | no Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | existing JDK/JUnit/JDBC/Flyway | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | no contract/codec edit | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | bounded test and existing DB clock | None |
| MC-CONFIG-001 | Not applicable | N/A | §7.2/8 | test-only props, no profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | direct native probe | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | exact source-only test/docs | None |
| MC-TEST-001 | Applicable | PASS | §14 | compile/source vs actual I | None |
| MC-BLOCKER-001 | Applicable | PASS | §18/20 | source design ready, live pending | None |

### 12.6 Final verdict

PASS — Ready for user review
