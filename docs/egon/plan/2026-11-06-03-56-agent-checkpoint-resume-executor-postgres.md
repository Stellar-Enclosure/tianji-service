# T4-03 Checkpoint 接管续跑 PostgreSQL 验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:56 Asia/Shanghai |
| Updated | 2026-11-06 03:56 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 7b8faefa38cd95e7807c4b92e0077991800b77c7 |
| Implements Spec | [Claimed resume PostgreSQL Spec](../spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:55 Asia/Shanghai SHA-256 c41b29630be53e24337b0e9467386528e786a99135c0b6772c9f239fbd33f6cd |
| Effective Specs | [Claimed resume PostgreSQL Spec](../spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

复用现有产品 fork 与协调夹具，以独立执行角色验证赢得接管后的同一 invocation 正常续跑和持久 ACK 重放。

## 2. Target Spec and Effective Design

[Claimed resume PostgreSQL Spec](../spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md) §§4/7/11/14 controls this Step. Existing takeover, Session first-read and resume executor behavior are dependencies; this Step only adds an operator fixture.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/11 | Isolated loopback primary and three identities, exact gate and bounded existing fork |
| REQ-002 | §7/10/14 | Same Run/attempt/invocation, exact RESUME and MODEL, one Fake, completion and settled MODEL, ACK-only unchanged replay |
| REQ-003 | §8/14/16 | Four paths, 28 SQL bytes, G0 absence, 28 finite tests and exact compiled fixture with no PG report |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Verify exact clean baseline, fixture candidate SHA and absence of the new method. Apply one test-source file. Run the owning finite suites and inspect exact bytecode. Fill observed evidence and stage exact four paths. Keep I1 independent.

### 4.2 Test-first strategy

This test-only scope has no production behavior change. G0 checks exact method absence instead of a typed implementation mutation. Only the operator may execute I1.

### 4.3 Sequential and parallel boundaries

Source G0, G1, G2 and S1 run sequentially in the owning reactor. The independent I1 uses disposable PostgreSQL and owned children after explicit authorization.

### 4.4 Commit boundaries

One focused test-only commit after exact finite and class evidence and strict validators.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Claimed resume operator case | Required §7/14 | EVD-001–004 | Existing isolated fork handles prerequisites | Extend exact test only |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Operator fixture | REQ-001/002/003 | G0 absent, G1/G2 source, I1 independent | 1 | Guarded executor and existing product fork |
| Review and evidence | REQ-003 | S1 strict and scope | 2–4 | Exact owning outputs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Existing AR PostgreSQL fixture | Exact new operator method and role |
| Native and DB evidence | Existing fork, ledger, executor and codec | Reuse actual paths with test-local authority and model |
| Production scope | Existing AR and Agent primary | No production source, DDL, config or wiring |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Controlled origin | Existing product fork and role fixture | EVD-001 | Reuse both codec paths | Extend exact callback | Existing test only | G2/I1 |
| Claimed completion and replay | Existing takeover and executor | EVD-002/003 | Need integrated operator witness | Use actual services | Existing test only | G1/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | File 1 | Existing fixture and exact named operator selector | G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | File 1 | Validate loopback database, exact role and current RESUME/MODEL request | G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | File 1 | Reuse native types and private immutable test witness | G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | File 1 | No production Bean, injection or logger changes | G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | File 1 | Reuse existing JDK, JUnit, JDBC, Flyway and ADK | G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | File 1 | Read existing JSONB acknowledgements and native codec payloads | G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | File 1 | Test-only opt-in properties; no profile changes | G1/G2/I1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | File 1 | Extend existing fork fixture through its bounded post-reconcile callback | G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | File 1 | Database-origin Instant permit and unchanged bounded child duration | G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | File 1 | Keep existing AR fixture package and scoped Agent primary | G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Fixture ResumePermit and original Run/Session deadline | Occurred instant | java.time.Instant and existing JDBC Timestamp | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Permit expires before original Run deadline; reauthorization returns the same bounded expiry | Existing database clock and PostgreSQL microseconds | Existing expiry contract and codec timestamps | Step 1 TEST-003 |
| TIME-002 | @Timeout and inherited child process bounds | Duration | JUnit Timeout and existing Java Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | One 150-second operator method and existing bounded owned children | Existing JUnit/child elapsed clocks | Prior fixture modes and limits preserved | Step 1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-executor-postgres.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 7b8faefa38cd95e7807c4b92e0077991800b77c7 and existing JDK25 offline settings. Keep all 28 existing migrations unchanged. The candidate is one test source and contains no production configuration, dependency, IAM or wiring changes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the exact PostgreSQL claimed resume acceptance method

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing guarded executor, Session first read, product fork and takeover fixture.
- Baseline state: Existing capture, fork, observation, native resume and takeover methods are present; the integrated executor test method is absent.
- Observable outcome: One independently selected operator fixture for both codecs, exact continuation, completion and ACK-only replay.
- End state: One existing test file and three reviewer documents; finite and compiled evidence only.
- Test-first gate: Not applicable. Test-only source addition; G0 exact method absence, with no production mutation.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: Add one isolated claimed resume and replay method.
- Symbols: AgentCheckpointCapturePostgresTest#@METHOD@, grantCheckpointResumeExecution and RunFixture#assertCheckpointResumeExecution.
- Repository evidence: Spec EVD-001–004 and exact Service baseline.
- Dependencies and consumers: Existing Agent Runtime owner, source reviewer and authorized operator.
- Why now: Add one isolated claimed resume and replay method within the one fixture Step.
- Contract/signature changes: Test-only method and fixture JVM properties.
- Input/output and state mapping: Keep the existing capture/fork. Add exact distinct role and scoped column grants. Reconcile both codecs, expire only original Session lease, claim with exact fixture authority, execute the winning capability and compare persisted completion and replay.
- Error and edge behavior: Wrong scope, current authorization, persisted witness or source evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
require exact capture/resume-executor flags and limited role; provision fresh PostgreSQL and retain V16 SELECT-only.
verifyProductCheckpointForks(database, RECONCILE, (fixture, cut) -> {
  fixture.expireReconciledSessionLease(cut); captureRawPrefixAndCheckpoint();
  winning = takeover.takeOver(actor, exactCommand, worker);
  executeResume(winning, sameResumeAuthority); verifyFreshResumeAndModelBeforeOneFakeStream();
  compareCompletedRunAttemptInvocationNativePrefixAgentOutputAndOneSettledModelReservation();
  assertConsumedCapabilityAndHistoricalAckReturnEmptyAndPreserveAllWitnesses();
});
```

- Verification contribution: G0/G1/G2 and separate I1.
- After this file: New fixture compiles; operator execution remains independent.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md`

- Purpose: Record exact fixture behavior.
- Symbols: REQ-001–003 and UC-001/002.
- Repository evidence: Spec EVD-001–004 and exact Service baseline.
- Dependencies and consumers: Existing Agent Runtime owner, source reviewer and authorized operator.
- Why now: Record exact fixture behavior within the one fixture Step.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Specify actual fork, exact role, authority, one Fake stream and replay witnesses.
- Error and edge behavior: Wrong scope, current authorization, persisted witness or source evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact prior product fork and scoped existing execution permissions.
Tie current RESUME and MODEL decisions to the one claimed continuation and compare durable witnesses.
Separate finite source and operator PostgreSQL acceptance.
```

- Verification contribution: S1.
- After this file: Exact scoped reviewer artifact is available.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md`

- Purpose: Lock implementation and validation.
- Symbols: Step 1 and G0/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–004 and exact Service baseline.
- Dependencies and consumers: Existing Agent Runtime owner, source reviewer and authorized operator.
- Why now: Lock implementation and validation within the one fixture Step.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Keep exact candidate, paths, baseline and exact owner finite/source boundary.
- Error and edge behavior: Wrong scope, current authorization, persisted witness or source evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock candidate and reviewed Spec before applying one test source.
Verify baseline method absence, exact 28 finite owner tests, class flags and absent PostgreSQL report.
Keep the single selected I1 invocation and its role prerequisites separate.
```

- Verification contribution: S1.
- After this file: Exact scoped reviewer artifact is available.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-resume-executor-postgres.md`

- Purpose: Record actual source proof.
- Symbols: G0/G1/G2/S1 and pending I1.
- Repository evidence: Spec EVD-001–004 and exact Service baseline.
- Dependencies and consumers: Existing Agent Runtime owner, source reviewer and authorized operator.
- Why now: Record actual source proof within the one fixture Step.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Update only observed counts, bytecode and exact staged scope. Leave actual PG and provider authority unverified.
- Error and edge behavior: Wrong scope, current authorization, persisted witness or source evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record baseline method absence and exactly 28 successful finite tests from the owning XML.
Record exact compiled method, guard flags, role property and no PostgreSQL report.
Verify four paths and immutable migration bytes; retain independent I1 as unexecuted.
```

- Verification contribution: S1.
- After this file: Exact scoped reviewer artifact is available.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact G0/G1/G2/S1. I1 is separate.
- Expected result: 28 finite tests and the exact method/flags in bytecode, with no PG report.
- Failure returns to: File 1 for fixture or compiled gate failure; Files 2–4 for documentation and evidence.
- Completion criteria: Four scoped paths, exact finite reports and class evidence, all 28 SQL unchanged.
- Rollback: Revert the one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md`, `docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-executor-postgres.md`.
- Commit: test(agent): prepare PostgreSQL checkpoint resume executor acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Clean baseline | Existing fixture and exact method absent |
| G1 | Offline clean reactor | Exact four finite selectors, 28 tests and zero failures, errors or skips |
| G2 | JDK25 javap actual test class | Exact selector, both opt-ins, third role property and preserved private child entry; no PG report |
| S1 | Strict docs and exact staged scope | Four paths and 28 byte-identical migrations |
| I1 | Separate authorized isolated PostgreSQL | One actually executed unskipped method covers both codecs, continuation, completed budget and replay witnesses |

```sh
resume_pg_base=7b8faefa38cd95e7807c4b92e0077991800b77c7
resume_pg_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
resume_pg_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
resume_pg_method=resumes_the_claimed_checkpoint_to_completion_and_replays_only_the_acknowledgement
resume_pg_ar=stellar-enclosure-tianji-agent-runtime
resume_pg_class=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointCapturePostgresTest
resume_pg_path=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java
test "$(git rev-parse --verify HEAD)" = "$resume_pg_base"
git cat-file -e "$resume_pg_base:$resume_pg_path"
test -z "$(git show "$resume_pg_base:$resume_pg_path" | rg -F "$resume_pg_method")"
resume_pg_reactor() {
  JAVA_HOME="$resume_pg_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$resume_pg_settings" -s "$resume_pg_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
resume_pg_reactor clean '-Dtest=AdkCheckpointResumeExecutorTest,AdkCheckpointResumeFirstReadTest,AgentCheckpointTakeoverServiceTest,AgentCheckpointForkHandoffTest'
"$resume_pg_jdk/bin/javap" -p -v -classpath "$resume_pg_ar/target/test-classes" "$resume_pg_class"
test ! -e "$resume_pg_ar/target/surefire-reports/TEST-$resume_pg_class.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

G1/G2 inspect the four fresh owning XML reports and exact method and JVM properties. The source gate never enables PostgreSQL, the resume role or the private child switch. There is no P1 gate for this test-only change.

### Separate operator-only I1

Use an independently authorized fresh disposable loopback PostgreSQL primary named `tianji_it_checkpoint_…` without existing Tianji schema, the installed vector patch and distinct setup, limited capture and resume-executor logins. Credentials stay outside URLs. The executor role must match `tianji_it_checkpoint_resume_executor_[a-z0-9_]{1,24}`. Test setup grants only fixture-scoped columns. Session revision UPDATE allows the existing row lock. [PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)

```sh
: "${TIANJI_CHECKPOINT_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_CHECKPOINT_RUNTIME_ROLE:?Provide the limited capture login}"
: "${TIANJI_CHECKPOINT_VECTOR_VERSION:?Provide the installed vector patch}"
: "${TIANJI_CHECKPOINT_RESUME_EXECUTOR_ROLE:?Provide the separate limited executor login}"
resume_pg_reactor "-Dtest=AgentCheckpointCapturePostgresTest#$resume_pg_method"   -Dtianji.postgres.checkpoint.capture=true   "-Dtianji.postgres.checkpoint.capture.url=$TIANJI_CHECKPOINT_PRIVATE_PG_URL"   "-Dtianji.postgres.checkpoint.capture.runtime-role=$TIANJI_CHECKPOINT_RUNTIME_ROLE"   "-Dtianji.postgres.checkpoint.capture.vector.version=$TIANJI_CHECKPOINT_VECTOR_VERSION"   -Dtianji.postgres.checkpoint.resume-executor=true   "-Dtianji.postgres.checkpoint.resume-executor.role=$TIANJI_CHECKPOINT_RESUME_EXECUTOR_ROLE"
```

Run only the exact method, without unrelated operator flags. Require one actually executed unskipped PostgreSQL method and retain its owning XML. Dispose of the fixture database separately. Finite reports and bytecode do not establish I1.

## 9. Migration, Compatibility, Rollout, and Rollback

Retain all prior fixture modes, production code, privileges, configuration, dependencies and 28 SQL files. Revert the one test-only commit to remove this scenario.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL privileges, controlled process death, native continuation, budget settlement and replay remain operator evidence. The fixture-local decisions and Fake model leave production IAM, model provider behavior and deployment separate.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing product fork, takeover, guarded executor and codecs | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test and runtime dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | One named exact guarded integration method | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Disposable primary, distinct role, exact owner/current decisions | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable witness retains exact artifacts for replay comparison | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing native codecs and result data, no cross-layer mapper | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger, raw payload or credentials in reviewer evidence | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Explicit fixture construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing fixed fixture helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Verify existing ACK/status and codec envelopes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database clock permit and bounded operator test | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test JVM properties only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing fork callback composes actual takeover and executor | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Production authority and actual operator results pending | None |

### 12.6 Final verdict

PASS — Ready for user review
