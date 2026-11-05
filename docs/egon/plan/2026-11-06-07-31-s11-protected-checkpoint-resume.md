# T4-03 S11 首条输入 checkpoint 的受保护 RESUME 验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:31 Asia/Shanghai |
| Updated | 2026-11-06 07:31 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Two AS tests and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 79779815f88b2b128363e27922ceac093157826b |
| Implements Spec | [S11 protected RESUME acceptance](../spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:30 Asia/Shanghai SHA-256 3b79bcaece4c5ad9cb1dda045c5dfc072d5579a7354e23bad4bb0d9e915a883d |
| Effective Specs | [S11 protected RESUME acceptance](../spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md) |
| Depends On Plans | [Protected checkpoint RESUME](./2026-11-06-06-49-protected-checkpoint-resume.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

为真实独立签发的 USER、现有 AS 受保护入口和同一主库的独立只读证人准备可选择的验收。分别证明拒绝、原 ACK、单次持久接续与历史重放。

## 2. Target Spec and Effective Design

[S11 protected RESUME acceptance](../spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md) §§4/7/11/14 governs this step. The existing AS owns HTTP and IAM and AR owns the durable continuation.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/11 | Exact opt-in, protected loopback origin, disposable primary and scoped observer |
| REQ-002 | §7/10/14 | Existing read, current permission denial, exact durable ACK and one continuation, historical replay and conflict |
| REQ-003 | §8/14/16 | Typed RED, finite owning green, exact two sources and three documents, default operator skip and immutable SQL |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Pin baseline and scope. Compile the two test classes and perform the deliberate revision-decoder RED in scratch. Run the focused owning reactor and inspect compiled test gating. Validate documents and stage exact files. I1 is a separate operator action.

### 4.2 Test-first strategy

The finite witness must detect removal of the strict ACK revision comparison in an isolated copy. The original then accepts only the exact revision. G0 proves the baseline absence of both new classes.

### 4.3 Sequential and parallel boundaries

G0/R1/G1/G2/S1 are ordered local source and evidence checks. I1 uses authorized real IAM, AS and prepared PostgreSQL only.

### 4.4 Commit boundaries

One commit after finite, review and scope gates for the two test sources and three documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| True protected witness | Required §7/14 | EVD-001–005 | Existing AR operator and AS finite tests provide disjoint evidence | Add one gated operator fixture |
| Strict finite witness | Required §10/14 | EVD-005 protects runtime behavior | Test the test's exact boundary | Add two finite cases |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Operator fixture | REQ-001/002 | G2 and I1 | 1 | Existing protected flow, V16 and actual roles |
| Finite witness | REQ-003 | R1/G1 | 2 | Exact boundary functions |
| Review evidence | REQ-003 | S1 | 3–5 | Exact scope, local gates and separately pending I1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001/002/005 | Add test-only AS fixture in the existing API test package |
| Persistence | EVD-003/004 | Exact current primary schema, single read-only SQL snapshot per witness |
| Production scope | EVD-001–005 | Existing protected chain and durable continuation |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Real protection | Existing protected route and MockMvc coverage | EVD-001/002/005 | Need external actual bearer | Reuse deployed route through JDK HTTP | Test-only fixture | G2/I1 |
| Durable transition | Existing V16 fork and takeover | EVD-003/004 | Need same-primary observer after protected call | Reuse existing tables and bounded JDBC read | Test-only query | G2/I1 |
| Exact acknowledgement | Existing command/ack types and Jackson | EVD-001/004 | Need strict fixture observation | Reuse types and parser | Test-only validation | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–2 | 使用现有 AS 测试命名及独立选择器。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–2 | 生产端口复用现有命令校验，测试对输入与 ACK 执行边界校验。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–2 | 复用不可变产品类型，测试局部证人为不可变 value object。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–2 | 范围为测试与文档，无新业务 Spring Bean。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–2 | JDK 工具、Jackson、JDBC 和现有 JUnit。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–2 | Spring Boot Jackson 读取闭合 ACK，PG JSONB 见证存储。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–2 | 独立 JVM 测试开关，生产多环境配置与默认值一致。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–2 | 复用外部 operator fixture 与只读 Query 证人。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–2 | JDK Duration、单调计时与 PostgreSQL 原时钟。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–2 | 使用现有真实主库既有 schema，独立核验不可变迁移。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Existing run deadline and prior Session lease | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | UTC Z | Not applicable: absolute instants | Not applicable: no user display | Lease expiration and at least 50 seconds to deadline evaluated at the primary | Existing PostgreSQL clock | Retain current stored values and data types | Step 1 TEST-004 initial snapshot |
| TIME-002 | Connect, body and completion deadlines | Duration | java.time.Duration, JUnit Timeout and monotonic elapsed nanos | Not persisted | Internal | Not applicable: relative elapsed duration | Not applicable: no user display | One bounded HTTP exchange and bounded completion observation | JDK monotonic clock and JUnit test budget | Preserve production endpoint response timeout | Step 1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeHttpPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeWitnessTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-protected-checkpoint-resume.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 79779815f88b2b128363e27922ceac093157826b. Use existing JDK 25 offline settings. I1 only observes already prepared V16 stores and actual IAM. All 28 existing migrations are immutable.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare the S11 protected checkpoint RESUME operator witness

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing protected AS, AR V16 and original current authorities.
- Baseline state: Existing protected controller and AR fork present, new witness classes absent.
- Observable outcome: Independently selected I1 can test real owner authorization, one durable continuation and exact replay.
- End state: Two tests and three documents. Finite gates execute and I1 remains separate.
- Test-first gate: Required. R1 mutates only exact revision equality in isolated scratch.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeHttpPostgresTest.java`

- Purpose: 执行真实保护请求与只读 PG 见证.
- Symbols: requires_current_owner_permissions_and_replays_the_exact_durable_acknowledgement.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected flow, AR V16 witness and authorized operator or source reviewer.
- Why now: 执行真实保护请求与只读 PG 见证.
- Contract/signature changes: Test-only fixture and validation methods.
- Input/output and state mapping: Exact owner, immutable cut and bounded result. Runtime evidence belongs to I1.
- Error and edge behavior: Missing exact evidence rejects without broadening fixture scope.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Validate exact loopback HTTPS origin, disposable database, private bearers and read-only witness.
Read the exact expired cut and require current same-owner protected READ.
Check unauthenticated and READ-only rejection and unchanged witness.
Send exact owner RESUME and compare original ACK, one native continuation and one model settlement.
Replay exact command and reject changed revision while confirming unchanged completed witness.
```

- Verification contribution: G0/G2/I1.
- After this file: Reconcile this file against REQ-001–003.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeWitnessTest.java`

- Purpose: 增加有限保护证人.
- Symbols: accepts_only_the_original_exact_acknowledgement and allows_only_the_exact_explicit_loopback_https_origin.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected flow, AR V16 witness and authorized operator or source reviewer.
- Why now: 增加有限保护证人.
- Contract/signature changes: Test-only fixture and validation methods.
- Input/output and state mapping: Exact owner, immutable cut and bounded result. Runtime evidence belongs to I1.
- Error and edge behavior: Missing exact evidence rejects without broadening fixture scope.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Reject wrong revision, wrong type, duplicate key, extra fields and trailing tokens.
Reject wrong scheme, host, path, credentials, query and fragment in the origin.
Require both tests to pass on the original strict matcher and fail after weakening revision equality.
```

- Verification contribution: R1/G1.
- After this file: Reconcile this file against REQ-001–003.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md`

- Purpose: 固定需求、作用域和 I1 前提.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected flow, AR V16 witness and authorized operator or source reviewer.
- Why now: 固定需求、作用域和 I1 前提.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact owner, immutable cut and bounded result. Runtime evidence belongs to I1.
- Error and edge behavior: Missing exact evidence rejects without broadening fixture scope.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the fixed POST and real owner prerequisites alongside the V16 cut and observer gates.
Trace the wire and durable outcomes to the operator matrix.
Classify separately observed IAM, deployment and process-exit evidence.
```

- Verification contribution: S1.
- After this file: Reconcile this file against REQ-001–003.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md`

- Purpose: 固定精确基线、文件和测试门.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected flow, AR V16 witness and authorized operator or source reviewer.
- Why now: 固定精确基线、文件和测试门.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact owner, immutable cut and bounded result. Runtime evidence belongs to I1.
- Error and edge behavior: Missing exact evidence rejects without broadening fixture scope.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock baseline and the exact two Java and three document paths.
Run isolated typed RED, focused AS test report, class gate and strict review in that order.
Prove all existing migrations and POMs remain byte-identical before staging.
```

- Verification contribution: S1.
- After this file: Reconcile this file against REQ-001–003.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-s11-protected-checkpoint-resume.md`

- Purpose: 归档真实执行证据和待运行范围.
- Symbols: Finite and operator evidence.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected flow, AR V16 witness and authorized operator or source reviewer.
- Why now: 归档真实执行证据和待运行范围.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact owner, immutable cut and bounded result. Runtime evidence belongs to I1.
- Error and edge behavior: Missing exact evidence rejects without broadening fixture scope.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the isolated JDK 25 compile, exact typed failure and passing finite witness count.
Provide the exact operator selector and private inputs for both codecs.
Keep I1 conditional until actually run with real IAM and PostgreSQL.
```

- Verification contribution: S1/I1.
- After this file: Reconcile this file against REQ-001–003.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 is separate.
- Expected result: Exact revision typed RED, 19 finite green and one opt-in operator gate compiled and skipped in the independent finite check.
- Failure returns to: File 1 or 2 for source/typed gates, Files 3–5 for documents and evidence.
- Completion criteria: Five exact paths, finite evidence, 28 immutable SQL, explicit pending I1.
- Rollback: Revert the one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeHttpPostgresTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeWitnessTest.java`, `docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md`, `docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md`, `docs/egon/evidence/2026-11-06-s11-protected-checkpoint-resume.md`.
- Commit: test(agent): prepare protected checkpoint resume acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Existing protected flow present and both new classes absent |
| R1 | Isolated copied sources | Remove exact ACK revision equality, obtain typed org.opentest4j.AssertionFailedError from the finite witness |
| G1 | Owning offline source reactor | Exact three finite suites, 19 successes and zero failures, errors or skips |
| G2 | JDK 25 actual classes | Compiled operator method, actual class opt-in, independent default skip and no operator execution |
| S1 | Strict documents and staged scope | Five exact new files and 28 byte-identical migrations |
| I1 | Operator-only actual HTTPS and PostgreSQL | One executed non-skipped method per codec with exact protected and durable effects |

```sh
s11_base=79779815f88b2b128363e27922ceac093157826b
s11_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
s11_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s11_as=stellar-enclosure-tianji-agent-server
s11_operator=top.egon.cola.platform.tianji.agent.server.api.AgentCheckpointResumeHttpPostgresTest
s11_core=AgentCheckpointResumeWitnessTest,AgentCheckpointResumeControllerTest,TianjiCheckpointResumeGrantHandoffTest
test "$(git rev-parse --verify HEAD)" = "$s11_base"
for source in stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeHttpPostgresTest.java stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeWitnessTest.java; do
  if git cat-file -e "$s11_base:$source" 2>/dev/null; then exit 1; fi
done
"$s11_runner" "$s11_core" clean
"$s11_jdk/bin/javap" -p -v -classpath "$s11_as/target/test-classes" "$s11_operator"
test -z "$(find "$s11_as/target/surefire-reports" -name "TEST-$s11_operator*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Owning G1 expects 2 witness, 9 controller and 8 handoff tests. For R1, compile a scratch copy of the operator class with the one `revision` equality removed and the unchanged finite witness, use the same AS classpath and JUnit launcher, and require a typed `Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown`. Then run the finite gate on the original sources.

### Separate operator-only I1

The exact private inputs and command are documented in [I1 operator evidence](../evidence/2026-11-06-s11-protected-checkpoint-resume.md). Prepare a current authorized run under each codec, opt in to the single named test, and verify one success with zero skips per fresh run. Require independently captured sanitized Shoubing/Jianshen decisions and process-exit evidence. I1 is never included in G1.

## 9. Migration, Compatibility, Rollout, and Rollback

Keep original deployment, role definitions and migration bytes. Revert the one test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G0/G2/I1 |
| REQ-002 | Step 1 | 1–2 | R1/G1/I1 |
| REQ-003 | Step 1 | 1–5 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

The real operator deployment, current IAM evidence, prepared original cut and actual isolated run remain pending. The current AR fork uses a controlled identity and cannot be used to claim actual AS IAM. Other S11 scenarios remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 复用 AS 保护路由、签发交接与 AR 现有主库。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用真实保护端点、原命令和 ACK 类型、现有 PostgreSQL JDBC。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 使用现有 JDK、Jackson、PG JDBC 和 JUnit。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 测试类与不可变 fixture 记录使用既有命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 保护本机 origin、一次性库、角色、凭据文件和 ACK 类型。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用命令与 ACK，证人记录仅保留标量和摘要。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 没有新业务层对象或转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 没有生产日志和凭据输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 没有新组件或运行配置绑定。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用 JDK HTTPS、URI、POSIX、JDBC 和现有 Jackson。 | None |
| MC-JSON-001 | Applicable | PASS | §10/14 | 严格 Jackson 对象、原整数类型、重复键和末尾 token 检查。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 使用相对 Duration 和主库 clock，现有存储类型一致。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 一个测试专用开关，部署配置仍由操作者提供。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 现有真实端到端 fixture 加独立只读证人。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 只有两份测试与三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 有限 RED 和 G1，与实际执行 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 精确记录操作者的真实 IAM、主库、外部进程和剩余 S11 窗口。 | None |

### 12.6 Final verdict

PASS — Ready for user review
