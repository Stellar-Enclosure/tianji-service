# T4-03 S11 Checkpoint 原定义版本与摘要故障验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:53 Asia/Shanghai |
| Updated | 2026-11-06 07:53 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Two existing AR tests and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | e42281d4e08120755ac9afbc9d8c8bce09d3b1f0 |
| Implements Spec | [S11 definition pin](../spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:52 Asia/Shanghai SHA-256 21a6ad4cf198fcb8fed047fa4f02efc38f503d4c9d3be6be933ad05e493b4bf3 |
| Effective Specs | [S11 definition pin](../spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md) |
| Depends On Plans | [Checkpoint takeover PostgreSQL](./2026-11-06-03-16-agent-checkpoint-takeover-postgres.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

沿用已有双 codec PostgreSQL 进程接管夹具。发布同家族第二版之后精确预览原第一版。分别注入 checkpoint 版本和摘要错配，证明签发之前拒绝，复原后原接管与 ACK 重放仍成立。

## 2. Target Spec and Effective Design

[S11 definition pin](../spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md) §§4/7/11/14 controls the two test changes. The existing AR production service owns publication, observation, exact claim and ACK.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9/10 | Distinct real v2 does not replace v1 in the original preview or permit |
| REQ-002 | §7/11/14 | Version-only and digest-only fixture drift independently reject without authority, receipt or progress |
| REQ-003 | §8/14/16 | Two typed REDs, focused green, gated operator proof and exact five-path scope |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the two baseline files and exact parent. Run independent finite guard mutations in scratch, then green and compile gates. Verify strict review scope, stage exact paths and commit. I1 is independent.

### 4.2 Test-first strategy

Compile two independent copies of the existing observation source. Each copy removes only one guard. The unchanged extended finite test must report BOUNDARY_OBSERVED when it expected SOURCE_UNAVAILABLE. Run the original without weakened production code.

### 4.3 Sequential and parallel boundaries

Run G0/R1/G1/G2/S1 in order. I1 needs the separate authorized disposable environment.

### 4.4 Commit boundaries

One focused test-only commit includes the two tests and three documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Existing V16 takeover fixture | Required §7/14 | EVD-001–003 | Existing dual codec operator branch | Extend existing branch |
| Independent finite guard cases | Required §10/14 | EVD-004 | Reuse existing finite loop | Add two inputs |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Operator fixture | REQ-001/002 | G2/I1 | 1 | Existing AR publication and PG takeover |
| Finite guard | REQ-003 | R1/G1 | 2 | Existing observation row matching |
| Review evidence | REQ-003 | S1 | 3–5 | Finite and source checks |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–004 | Existing tests own the change |
| Persistence | EVD-003 | One exact disposable V16 row and checked restoration |
| Production scope | EVD-001/002 | Existing APIs, POMs, roles and migrations |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Real version publication and original pin | AgentLedgerService | EVD-001 | Existing production path | Reuse | Test calls only | G2/I1 |
| Real drift fail closed | AgentCheckpointCapturePostgresTest and AgentCheckpointObservationService | EVD-002/003 | Missing version and SHA fixture cases | Reuse | Two test-local helper methods | G2/I1 |
| Typed independent proof | AgentCheckpointObservationServiceTest | EVD-004 | Existing negative assertion loop | Reuse | Two test inputs | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | Files 1–2 | 在两个既有测试内扩展准确场景。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | Files 1–2 | 复用产品发布与预览入口，夹具 CAS 严格验证旧值和完整 owner。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | Files 1–2 | 复用原不可变规格、快照与授权模型。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | Files 1–2 | 无新业务类、日志或 Spring Bean。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | Files 1–2 | 既有 JDK、JDBC、JUnit 和 Mockito。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | Files 1–2 | 保留当前定义和 Session 编码。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–004 | Files 1–2 | 沿用原操作者 opt-in，生产默认与环境保持一致。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–004 | Files 1–2 | 复用测试 fixture 和隔离数据库 CAS。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | Files 1–2 | 原策略 timeout、计时和存储语义保持原值。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 11 | §6.2/7 | EVD-001–004 | Files 1–2 | 使用真实 PostgreSQL 既有 V16，所有原 SQL 字节不变。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: Use the existing original policy totalTimeout for version 2 and reuse the existing I1 deadlines, acquisition times, database clock and lease-expiration protocol. No time field or interpretation changes.

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-checkpoint-definition-pin.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline e42281d4e08120755ac9afbc9d8c8bce09d3b1f0. Existing JDK 25 offline settings and separate existing operator environment. Preserve all 28 existing SQL bytes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Cover original checkpoint definition pin and mismatches

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing AR ledger, checkpoint fork and bounded permit fixture.
- Baseline state: Exact operator and finite methods exist without definition cases.
- Observable outcome: Original version remains bound after real v2 publish. Either injected mismatch denies.
- End state: Two existing tests updated and three evidence documents. I1 pending authorized execution.
- Test-first gate: Required. Independent R1 per removed version and digest guard.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: 验证原 v1 在发布 v2 后继续准确绑定.
- Symbols: RunFixture#assertCheckpointTakeover, #assertCheckpointDefinitionDriftRejected and #changeCheckpointDefinition.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR publication, observation and operator fixture.
- Why now: 验证原 v1 在发布 v2 后继续准确绑定.
- Contract/signature changes: Test fixture assertions only.
- Input/output and state mapping: Original binding stays exact, deliberate fixture mismatches are rejected and restored.
- Error and edge behavior: Missing expected scope, failure or restoration stops the operator case.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```text
Publish a distinct version 2 with the existing ledger and compare the old pinned preview.
Inject version-only and digest-only V16 mismatch by exact row CAS, restoring each in finally.
Require preview and claim conflict before authority with no receipt, then perform existing positive and replay.
```

- Verification contribution: G0/G2/I1.
- After this file: Reconcile with REQ-001–003 and keep I1 separately attributable.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`

- Purpose: 验证独立有限守卫.
- Symbols: rejects_stale_binding_and_malformed_or_unexpected_native_event.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR publication, observation and operator fixture.
- Why now: 验证独立有限守卫.
- Contract/signature changes: Test fixture assertions only.
- Input/output and state mapping: Original binding stays exact, deliberate fixture mismatches are rejected and restored.
- Error and edge behavior: Missing expected scope, failure or restoration stops the operator case.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 11.
- Implementation pseudocode:

```text
Add independent definition_version and definition_sha256 mismatches to the actual finite observation cases.
Require SOURCE_UNAVAILABLE for each input and no tool evidence read.
Delete each comparison in a different scratch copy and require a typed failure in the unchanged case.
```

- Verification contribution: R1/G1.
- After this file: Reconcile with REQ-001–003 and keep I1 separately attributable.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md`

- Purpose: 固定 S11 定义 pin 的确切源范围.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR publication, observation and operator fixture.
- Why now: 固定 S11 定义 pin 的确切源范围.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Original binding stays exact, deliberate fixture mismatches are rejected and restored.
- Error and edge behavior: Missing expected scope, failure or restoration stops the operator case.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record immutable definition publication, existing recovery observation and authorization boundaries.
Define positive preserved v1 and independent version and digest rejection.
Trace the two RED gates, scope and separately authorized PostgreSQL.
```

- Verification contribution: S1.
- After this file: Reconcile with REQ-001–003 and keep I1 separately attributable.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md`

- Purpose: 锁定独立有限和 operator 门.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR publication, observation and operator fixture.
- Why now: 锁定独立有限和 operator 门.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Original binding stays exact, deliberate fixture mismatches are rejected and restored.
- Error and edge behavior: Missing expected scope, failure or restoration stops the operator case.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock exact parent and both existing test paths.
Run isolated version and digest RED followed by 26 owning finite results.
Compare exact paths and all existing migration bytes before commit.
```

- Verification contribution: S1.
- After this file: Reconcile with REQ-001–003 and keep I1 separately attributable.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-s11-checkpoint-definition-pin.md`

- Purpose: 归档实际有限证据与原 operator 命令.
- Symbols: Finite evidence and I1 requirements.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR publication, observation and operator fixture.
- Why now: 归档实际有限证据与原 operator 命令.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Original binding stays exact, deliberate fixture mismatches are rejected and restored.
- Error and edge behavior: Missing expected scope, failure or restoration stops the operator case.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact red assertions, successful owning finite results and compiled operator branch.
Link the existing isolated fixture prerequisites and selector.
Leave PostgreSQL and processes as pending until actually executed.
```

- Verification contribution: S1/I1.
- After this file: Reconcile with REQ-001–003 and keep I1 separately attributable.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 is separate.
- Expected result: Two typed REDs, 26 owning finite successes, actual gated compiled branch and five paths.
- Failure returns to: File 1–2 for source or verification failure, Files 3–5 for review or scope.
- Completion criteria: Exact five paths, both fault guards proven and all 28 migration bytes equal.
- Rollback: Revert this one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`, `docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md`, `docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md`, `docs/egon/evidence/2026-11-06-s11-checkpoint-definition-pin.md`.
- Commit: test(agent): cover checkpoint definition pin and drift.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Both existing test methods and neither new definition mismatch |
| R1 | Two independent scratch copies | SOURCE_UNAVAILABLE expected and BOUNDARY_OBSERVED actual for each removed guard |
| G1 | Owning offline source reactor | 26 finite successes and zero failures, errors and skips |
| G2 | Actual JDK 25 classes | Exact existing operator method and both injected mismatch paths compile. Independent default skip. |
| S1 | Strict review and staged scope | Two test modifications, three new docs, 28 unchanged SQL |
| I1 | Operator-only isolated real PostgreSQL | One executed method covers both codecs, existing fork and claim plus two independent faults |

```sh
definition_base=e42281d4e08120755ac9afbc9d8c8bce09d3b1f0
definition_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
definition_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-checkpoint-definition-pin/step155
definition_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
definition_ar=stellar-enclosure-tianji-agent-runtime
definition_operator=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointCapturePostgresTest
test "$(git rev-parse --verify HEAD)" = "$definition_base"
git cat-file -e "$definition_base:stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java"
git cat-file -e "$definition_base:stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java"
if git show "$definition_base:stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java" | rg -q 'assertCheckpointDefinitionDriftRejected'; then exit 1; fi
"$definition_scratch/run-scratch.sh"
"$definition_runner" "AgentCheckpointObservationServiceTest,AgentCheckpointTakeoverServiceTest,AgentCheckpointForkHandoffTest" clean
"$definition_jdk/bin/javap" -p -v -classpath "$definition_ar/target/test-classes" "$definition_operator"
test -z "$(find "$definition_ar/target/surefire-reports" -name "TEST-$definition_operator*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

G1 expects 7 observation, 14 takeover and 5 handoff results. The scratch script compiles the same two candidate tests plus isolated guard mutations and never enables the operator flags.

### Separate operator-only I1

Use the existing exact operator setup and command in [checkpoint takeover PostgreSQL Plan §8](./2026-11-06-03-16-agent-checkpoint-takeover-postgres.md#separate-operator-only-i1). Select `AgentCheckpointCapturePostgresTest#takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit` with both existing fixture flags and only the authorized exact disposable roles. Require one non-skipped XML report and both codecs. Collect real artifacts independently of this source Step.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration change. Revert the test and document commit to remove the extra fixture branches.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1–2 | R1/G1/I1 |
| REQ-003 | Step 1 | 1–5 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL, role/lease behavior and child JVM results require authorized separate execution. The other S11 windows and real IAM remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 复用 AR V16、定义账本与同一主库三角色夹具。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | 复用现有发布、预览、接管、进程与 ACK 见证。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 只使用既有 JDK、Spring JDBC、JUnit 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 保留两个现有测试类和操作者选择器。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 分别核验版本和摘要，注入及复原使用完整精确 CAS。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用不可变定义、执行快照与预览类型。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无新层模型转换或 DTO。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新增日志或证据值输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 无 Bean 或运行配置改动。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用已有 Spring JDBC 测试夹具、JDK 与 JUnit。 | None |
| MC-JSON-001 | Not applicable | N/A | §10 | 沿用既有定义和 checkpoint 格式，无新解析。 | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | 原定义既有 timeout 原样传入第二版，既有时钟和界限保持。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 复用已有 opt-in 开关、角色和地址门。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 沿用独立操作者 fixture 和现有 CAS 控制。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 精确两份测试和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 两个独立 RED、有限回归与真实 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 明确记录实际主库和进程未验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
