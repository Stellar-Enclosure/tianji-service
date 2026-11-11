# T4-01 A4/A5 发布切换与受控 START PostgreSQL 实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:52 Asia/Shanghai |
| Updated | 2026-11-11 15:52 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Two Process Server test sources and three review documents |
| Source Requirement | Master §19 T4-01 |
| Baseline Revision | 423b54b68081172dcc3d5c998ed7bd45f0995a77 |
| Implements Spec | [A4/A5 PostgreSQL operator](../spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:51 Asia/Shanghai SHA-256 ab13dea0aa12f27732b7034b84f1dcfeb764f2c834924e625d3fd1599eaa535c |
| Effective Specs | [A4/A5 PostgreSQL operator](../spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md) |
| Depends On Plans | [A4 发布激活](./2026-11-11-15-09-platform-release-activation.md#7-ordered-file-by-file-implementation-steps), [A5 治理 START](./2026-11-11-15-29-governed-process-start.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一次性操作者测试通过真实 V11–V13、Flowable 和已签名候选，组合核验切换、回滚和 START 来源。

## 2. Target Spec and Effective Design

同上方精确哈希的 Spec。应用其 REQ-001–004 和 TEST-001/002。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/11 | V11→V12→V13 原历史与实际权限门。 |
| REQ-002 | §7 | 两个发布、独立审阅、CAS 与准确回滚。 |
| REQ-003 | §7/9/11 | 共享 START 约束排它切换，重放和原生回滚。 |
| REQ-004 | §8/14/18 | 源证据和真实 I1 独立。 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定真实基线，固定独立测试及两份真实发布模型。核对有界并发和断言，运行有限 G1/G2/S1。操作者在独立 I1 运行真实 PostgreSQL。

### 4.2 Test-first strategy

首先证明现有 URL guard 与 JUnit 双门在真实选择器下关闭，双属性启用后给非法 URL 时抛出精确异常。签名和 BPMN 规范有限检查，再运行 owning 单元测试。

### 4.3 Sequential and parallel boundaries

同一 Step 拥有两份测试和三份文档。基线校验之后逐项收证，提交前汇总。

### 4.4 Commit boundaries

一个 Process Server 测试提交。精确五个路径。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 迁移和授权 | Required §7/11 | EVD-001/002 | 复用 A3 SQL-only 和 runtime guard | 在独立夹具按 V11→V12→V13 运行 |
| 原生发布 | Required §7 | EVD-002/003/006 | 复用既有单 Worker 模型 | 修改一个 timer 字面量并签名实际发布 |
| 栅栏与回滚 | Required §7/14 | EVD-004/005 | 直接调用当前事务 | 用一次性触发器观察关键边界 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 操作者隔离与迁移 | REQ-001/004 | G2/I1 | 1 | EVD-001/002 |
| 原生和事务场景 | REQ-002/003 | G1/I1 | 2 | 1 与现有 A3–A5 |
| 评审和执行证据 | REQ-004 | S1 | 3–5 | G0/G1/G2/I1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–006 | 当前 PS 测试边界 |
| Persistence | EVD-001/003–005 | 原 Flowable 和 JDBC 同事务 |
| Integration | EVD-002/006 | 现有仓库 test classpath |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| URL 和权限保护 | ProcessServerPostgresTest | EVD-002 | Fit | Reuse | 两属性 test 门 | Step 1 G2 |
| 初始 SQL-only history | PlatformReleaseCandidateReviewPostgresTest | EVD-001 | Fit | Reuse | V11/12/13 检查 | Step 1 I1 |
| 候选与原生模型 | PlatformReleaseSignatureVerifier and ProcessReleaseService | EVD-002/003/006 | Fit | Reuse | test Ed25519 与两个 BPMN | Step 1 G1/I1 |
| 真实事务栅栏 | ProcessExecutionService, PlatformReleaseActivationStore | EVD-004/005 | Fit | Reuse | 一次性测试触发器 | Step 1 I1 |

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
| Rule 1 | §6.2/7 | EVD-001–006 | Files 1–2 | 原测试命名及清楚的场景标识 | G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–006 | Files 1–2 | 精确操作者门控和实际命令校验 | G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–006 | Files 1–2 | 原不可变发布、范围和回执 | G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–006 | Files 1–2 | 无生产 Bean 或业务日志 | G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–006 | Files 1–2 | 现有 JDK/JUnit/Spring JDBC | G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–006 | Files 1–2 | 复用规范 JSON 签名 | G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–006 | Files 1–2 | 新开关仅属测试 JVM | G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–006 | Files 1–2 | 原生发布及 A4/A5 事务产品路径 | G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–006 | Files 1–2 | 数据库绝对时间及单调等待 | G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–006 | Files 1–2 | 隔离触发器并保存迁移字节 | G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Current grants and detached signature | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal | Fixture grants 20 s. Signature expires at exclusive deadline | PostgreSQL clock_timestamp, seconds for Ed25519 claims | Existing A2/A4/A5 contract | Step 1 TEST-001/002 |
| TIME-002 | Backend, latch and future waits | Duration | System.nanoTime and java.util.concurrent.TimeUnit | Not persisted | Internal | Not applicable: elapsed | Not applicable: internal | Bounded by test, JDBC and store deadlines | Monotonic nanoseconds | Existing test behavior | Step 1 TEST-002 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformActivationGovernedStartPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformActivationGovernedStartPostgresScenarios.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-11-platform-activation-governed-start-postgres.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service 基线为 423b54b68081172dcc3d5c998ed7bd45f0995a77。使用既有 JDK 25 与组件依赖。I1 需要另外批准的一次性主库和独立受限角色。验证当前已提交的 31 份迁移不变。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare operator acceptance for activation, rollback and governed START

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: 现有 A3 PostgreSQL、A4、A5、Flowable 发布和运行。
- Baseline state: A4/A5 产品路径与有限测试存在，缺少 V12/V13 原生组合验收。
- Observable outcome: 一个隔离测试验证发布切换、回滚、来源、重放和失败原子性。
- End state: 五个路径，I1 待操作者。
- Test-first gate: Required. G2 双属性启用后，非法 URL 必须在 JDBC 前抛出既有精确异常。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformActivationGovernedStartPostgresTest.java`

- Purpose: 隔离数据库、平台迁移及角色门。
- Symbols: PlatformActivationGovernedStartPostgresTest.
- Repository evidence: Spec EVD-001–006.
- Dependencies and consumers: 原 PS 测试入口、SQL 与原生发布。
- Why now: 为 REQ-001/004 提供可执行验收。
- Contract/signature changes: 测试或评审边界。
- Input/output and state mapping: 隔离数据库、平台迁移及角色门。
- Error and edge behavior: 缺少准确的前置或结果使验收失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
guard URL and temporary role;
create empty schema and migrate V1 to V11 without a marker;
provision native engines and distinct Outbox history;
migrate V12, then V13 and verify the existing rows and limited role before opening runtime.
```

- Verification contribution: G2/I1.
- After this file: 保留 REQ-001/004 对应的源证据。


#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformActivationGovernedStartPostgresScenarios.java`

- Purpose: 签名、真实发布和事务场景。
- Symbols: PlatformActivationGovernedStartPostgresScenarios#verify.
- Repository evidence: Spec EVD-001–006.
- Dependencies and consumers: 原 PS 测试入口、SQL 与原生发布。
- Why now: 为 REQ-002/003 提供可执行验收。
- Contract/signature changes: 测试或评审边界。
- Input/output and state mapping: 签名、真实发布和事务场景。
- Error and edge behavior: 缺少准确的前置或结果使验收失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
publish two agentDispatch resources and sign the exact native release manifests;
submit and accept each; activate v1 and start;
compete for the v2 CAS while an old START has observed v1;
observe the shared START fence blocking rollback and release it;
check earlier native identity, new rollback START and exact historical replay;
force a V13 insertion failure only after native and APPLIED rows exist and verify rollback.
```

- Verification contribution: G1/I1.
- After this file: 保留 REQ-002/003 对应的源证据。


#### File 3 — `CREATE docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md`

- Purpose: 冻结可验证的需求与作用域。
- Symbols: REQ-001–004.
- Repository evidence: Spec EVD-001–006.
- Dependencies and consumers: 原 PS 测试入口、SQL 与原生发布。
- Why now: 为 REQ-004 提供可执行验收。
- Contract/signature changes: 测试或评审边界。
- Input/output and state mapping: 冻结可验证的需求与作用域。
- Error and edge behavior: 缺少准确的前置或结果使验收失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
record current native and SQL contracts;
map each requirement to source and operator verification;
assert explicit source versus runtime acceptance.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的源证据。


#### File 4 — `CREATE docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md`

- Purpose: 冻结按文件执行及权限边界。
- Symbols: G0/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–006.
- Dependencies and consumers: 原 PS 测试入口、SQL 与原生发布。
- Why now: 为 REQ-004 提供可执行验收。
- Contract/signature changes: 测试或评审边界。
- Input/output and state mapping: 冻结按文件执行及权限边界。
- Error and edge behavior: 缺少准确的前置或结果使验收失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
freeze clean base and exact files;
run strict and finite source gates;
check production artifacts and leave operator I1 separately scoped.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的源证据。


#### File 5 — `CREATE docs/egon/evidence/2026-11-11-platform-activation-governed-start-postgres.md`

- Purpose: 保留执行证据及待验收范围。
- Symbols: Finite and operator evidence.
- Repository evidence: Spec EVD-001–006.
- Dependencies and consumers: 原 PS 测试入口、SQL 与原生发布。
- Why now: 为 REQ-004 提供可执行验收。
- Contract/signature changes: 测试或评审边界。
- Input/output and state mapping: 保留执行证据及待验收范围。
- Error and edge behavior: 缺少准确的前置或结果使验收失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
record finite outcomes and compiled method;
record the exact gate proof and scope hash;
record independently authorized live test command and expected report.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的源证据。


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/G1/G2/S1. I1 独立。
- Expected result: 有限测试通过，精确方法和默认关闭证据，五条路径。
- Failure returns to: Files 1–2 检查夹具，Files 3–5 检查范围与证据。
- Completion criteria: 五条路径，既有 SQL 无差异，I1 留待真实执行报告。
- Rollback: Revert this one test-only commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformActivationGovernedStartPostgresTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformActivationGovernedStartPostgresScenarios.java`, `docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md`, `docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md`, `docs/egon/evidence/2026-11-11-platform-activation-governed-start-postgres.md`.
- Commit: test(process): prepare PostgreSQL activation and governed start acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Exact current HEAD and existing production sources. |
| G1 | Owning finite source reactor | A1–A5 tests, verifier, release tests and actual PS packaging. |
| G2 | Java 25 compiled operator and discovery | One method. No URL or flag means skipped. Both set with invalid URL fail closed before JDBC. |
| S1 | Strict docs and scoped diff | Five exact paths and 31 previous SQL files unchanged. |
| I1 | Operator-only isolated PostgreSQL | One unskipped method for native V11–V13 with exact assertions. |

```sh
activation_base=423b54b68081172dcc3d5c998ed7bd45f0995a77
activation_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse --verify HEAD)" = "$activation_base"
"$activation_runner" 'PlatformReleaseArtifactCandidateTest,PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseActivationServiceTest,PlatformReleaseActivationStoreTest,GovernedProcessStartServiceTest,ProcessApiSchemaVerifierTest,ProcessReleaseServiceTest,PlatformActivationGovernedStartPostgresTest' clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-51-platform-activation-governed-start-postgres.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --check
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

按既有 ProcessServerPostgresTest 明确提供一次性 UTF8 主库、预置 owner 和独立受限登录。URL 只接受 loopback `tianji_it_` 数据库。runtime 角色采用 `tianji_it_runtime_` 加 12 位小写十六进制字符。使用本地安全的测试凭据交付方式。

```sh
"$activation_runner" 'PlatformActivationGovernedStartPostgresTest#rolls_back_the_pointer_and_keeps_each_native_start_pinned_to_its_revision'   -Dtianji.process.postgres.activation-start=true   "-Dtianji.process.postgres.test.url=$TIANJI_PROCESS_TEST_URL"   "-Dtianji.process.postgres.test.runtime-user=$TIANJI_PROCESS_TEST_RUNTIME"
```

保留真实 owning XML，报告一项实际执行且未跳过，失败时保留具体断言和运行错误，避免回显连接信息。

## 9. Migration, Compatibility, Rollout, and Rollback

I1 在一次性 schema 内迁移并清理 test trigger。正常源码交付的迁移、应用配置和产物按原合同构建。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 2 | G1/I1 |
| REQ-003 | Step 1 | 2 | G1/I1 |
| REQ-004 | Step 1 | 1–5 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

真实 PostgreSQL、Flowable 锁、生产权威、IAM 和受保护入口需后续范围的具体证据。I1 仅验证前两项中的测试路径。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 原 PS 测试结构 | G0 |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | 现有 guard、模型、产品入口 | G1 |
| MC-DEP-001 | Applicable | PASS | §6 | 既有依赖 | S1 |
| MC-NAME-001 | Applicable | PASS | §8/9 | 精确选择 | G2 |
| MC-VALID-001 | Applicable | PASS | §7/9 | 连接前 guard | G2 |
| MC-MODEL-001 | Applicable | PASS | §10 | 现有不可变合同 | G1 |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无业务 DTO 转换 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无业务日志 | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | 无新 Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6 | 既有 JDK 和测试依赖 | G1 |
| MC-JSON-001 | Applicable | PASS | §7.3 | 既有 canonical JSON 与实际 Ed25519 | G1 |
| MC-TIME-001 | Applicable | PASS | §6.3 | 数据库时钟和单调等待 | I1 |
| MC-CONFIG-001 | Not applicable | N/A | §9 | 测试 JVM 属性 | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | 原产品事务组合 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/11 | 五条路径 | S1 |
| MC-TEST-001 | Applicable | PASS | §14 | 有限结果与 I1 | G1/G2/I1 |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 实际 DB 独立执行 | I1 |

### 12.6 Final verdict

PASS — Ready for user review
