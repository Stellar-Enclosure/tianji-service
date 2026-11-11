# T4-03 S11 原 START 已提交后成功响应丢失切点计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 08:28 Asia/Shanghai |
| Updated | 2026-11-06 08:28 Asia/Shanghai |
| Owner | Tianji Agent and Process test fixtures |
| Repository | tianji-service |
| Scope | Six test sources and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | ac8384069257a457089bcf3d13b688b79ea9091b |
| Implements Spec | [START cut producer](../spec/2026-11-06-08-27-s11-start-response-loss-cut.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 08:27 Asia/Shanghai SHA-256 65eecc8b41b2fb0ccaa3fe5e4511835c81eb6d7a45a8c9bf8e93e60e9dc3102d |
| Effective Specs | [START cut producer](../spec/2026-11-06-08-27-s11-start-response-loss-cut.md) |
| Depends On Plans | [Protected historical START observer](./2026-11-06-08-04-s11-protected-historical-start-receipt.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在独立操作者套件中沿用 S07 真实 Tool、IAM 和两个主库。对同一原始 START，只在实际 HTTPS 成功并由主库证明确已提交后丢弃调用方响应，生成可复用的真实 UNKNOWN 切点。

## 2. Target Spec and Effective Design

[START cut producer](../spec/2026-11-06-08-27-s11-start-response-loss-cut.md) §§4/7/11/14 governs this step. The existing AS and PS own actual production behavior.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/9 | Isolated manifest, exact existing qualified client and original roles and routes |
| REQ-002 | §7/10/11 | One real successful response suppressed only after exact committed witness, original durable unresolved state |
| REQ-003 | §8/14/16 | Typed RED, finite green, compiled operator class and exact source scope |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the exact parent and source paths. Implement the opt-in test decorator and S07-derived suite. Run R1, G1, G2 and S1 before staging. Real I1 stays separate.

### 4.2 Test-first strategy

Remove the one-shot CAS only in a scratch copy. The finite duplicate invocation must reveal the repeated I/O loss where a rejection was expected.

### 4.3 Sequential and parallel boundaries

Run G0/R1/G1/G2/S1 in sequence. I1 requires separate real operator configuration.

### 4.4 Commit boundaries

One focused commit for the six test sources and three review documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Exact AS decorator | Required §7/14 | EVD-002–004 | Generic relay changes URI and route surface | Wrap the existing qualified client in only one mode |
| S07-derived suite | Required §5/7 | EVD-001 | Existing suite requires success | Reuse boot and add independent method |
| Independent evidence | Required §10/14 | EVD-005 | Controlled Process source does not create this cut | Private marker and operator proofs |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| AS fault | REQ-001/002 | R1/G1/G2/I1 | 1–3 | Original JDK client and exact sources |
| PS suite and witness | REQ-001/002 | G1/G2/I1 | 4–6 | Existing S07 launcher and actual primaries |
| Review evidence | REQ-003 | S1 | 7–9 | Actual local evidence |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–005 | Extend test fixture only |
| Persistence | Existing AR V13 and PS START | Independent read-only witnesses and original business transaction |
| Transport | Original named JDK Bean and endpoints | Test-only Decorator and scoped one-shot |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Authorized workflow setup | Existing S07 fixture | EVD-001 | Ready under separately authorized manifest | Reuse | One new suite | G2/I1 |
| Preserve backend and authority | JDK Client and generic relay | EVD-002/004 | Relay changes digest and rejects prerequisite GET | Test Decorator | No dependencies | G1/G2/I1 |
| Verify original durable fact | Existing PS ledger and AR journal | EVD-003/005 | Need exact committed physical snapshot | Reuse | Witness SQL and private marker | G2/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–6 | 在已存在 fixture package 使用具名测试和选择器。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–6 | 复用命令约束，增加精确原命令、上下文和证人验证。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–6 | 复用不可变 DTO 和有限字段 marker。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–6 | 专用 fixture Bean 由显式 initializer 以准确名称登记。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–6 | JDK、JDBC、Jackson、Mockito 和现有验收 fixture。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–6 | 复用严格 Jackson 和原合同。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–6 | 只有新测试 suite 能开启装饰，部署配置保持。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–6 | 使用 Decorator 保持原 transport 和凭据。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–6 | 只用 java.time 和单调时钟、原数据库时钟。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–6 | 测试观察现有 schema，原 29 SQL 不变。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | dispatch_not_after | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Original dispatch is current before forwarding and expired before cut acceptance | PostgreSQL clock_timestamp | Preserve original V13 timestamps | Step 1 TEST-004 |
| TIME-002 | Prepared START request, decode and JDBC budgets | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Witness must finish before original client request budget and no timed-out marker | JDK monotonic clock, JDBC query timeout | Preserve production SDK bounds | Step 1 TEST-001/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLoss.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLossTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S11HistoricalStartCutIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md` |
| CREATE | `docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-start-response-loss-cut.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline ac8384069257a457089bcf3d13b688b79ea9091b. Use existing JDK 25 offline settings, production scope and migration set. I1 requires a separate authorized manifest.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare exact committed START response-loss producer

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing S07, original AS/PS services, and separate operator permission.
- Baseline state: Exact S07 real Tool success is present. New S11 cut and decorator are absent.
- Observable outcome: One actual committed START whose success is hidden before delivery to AS client, with verified durable UNKNOWN.
- End state: Nine exact paths, real I1 separate.
- Test-first gate: Required. R1 removes only the one-shot CAS.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: Scope the new child flag.
- Symbols: ExpenseAgentAcceptanceMain#main.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Scope the new child flag.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Require the exact S11 child mode together with existing S07 mode.
Reject simultaneous approval mode.
Register the scoped named BeanPostProcessor only before the authorized test context refresh.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLoss.java`

- Purpose: Generate the response-loss cut.
- Symbols: S11HistoricalStartLoss and LossClient.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Generate the response-loss cut.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Parse and match a bounded real START command and exact original V13 owner and SERVICE.
Delegate actual HTTPS and validate real 200 plus read-only applied and native evidence.
Complete exceptionally once and write a private non-overwriting marker only after the loss.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLossTest.java`

- Purpose: Exercise the causal and one-shot guards.
- Symbols: S11HistoricalStartLossTest.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Exercise the causal and one-shot guards.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Pass release and unrelated START unchanged, then call the exact selected request.
Supply a verified response and observe commit validation before the intended I/O failure.
Reject duplicate and unsuccessful upstream responses.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: Pass manifest and prove the durable cut.
- Symbols: Driver#open, #bootGovernedStart and #verifyHistoricalStartCut.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Pass manifest and prove the durable cut.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Set the exact scoped child settings from manifest observers.
Start one real first-tenant governed run and wait for the private marker.
Verify terminal UNKNOWN, expired dispatch, original receipt and stable native review.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: Name the isolated mode.
- Symbols: ExpenseAcceptanceHarness.Suite and #addTrustedFixtureSettings.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Name the isolated mode.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Add a distinct S11 enum member and reuse the existing S07 setup guard.
Forward the new flag only to the selected Agent child.
Keep all existing S07, S12 and Process flags and modes effective.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S11HistoricalStartCutIT.java`

- Purpose: Select the operator execution.
- Symbols: S11HistoricalStartCutIT#retains_the_original_start_when_the_agent_loses_its_committed_success_response.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Select the operator execution.
- Contract/signature changes: Test-only fixture.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Open the explicit S11 manifest using the existing isolated Driver gate.
Execute only the exact producer method with real already-authorized fixtures.
Close the child processes while retaining their independently witnessed database state.
```

- Verification contribution: R1/G1/G2/I1.
- After this file: Reconcile against REQ-001–003.

#### File 7 — `CREATE docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md`

- Purpose: Define the operator and durable boundaries.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Define the operator and durable boundaries.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the exact caller response fault and preserved original endpoint and registration.
Trace both before and after physical database evidence.
Separate independently pending real IAM and downstream reconciliation.
```

- Verification contribution: S1.
- After this file: Reconcile against REQ-001–003.

#### File 8 — `CREATE docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md`

- Purpose: Lock execution and exact scopes.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Lock execution and exact scopes.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Pin all exact source and document paths against the current Service parent.
Run isolated typed negative and owning finite proofs plus compile operator method.
Verify all existing migrations and the staged diff before the single commit.
```

- Verification contribution: S1.
- After this file: Reconcile against REQ-001–003.

#### File 9 — `CREATE docs/egon/evidence/2026-11-06-s11-start-response-loss-cut.md`

- Purpose: Record only executed gates and operator prerequisites.
- Symbols: Evidence and I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing S07 fixtures, original AS/PS services and operator.
- Why now: Record only executed gates and operator prerequisites.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact S11 mode, source evidence and isolated runtime proof.
- Error and edge behavior: Missing or invalid fixture evidence stops its gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Provide the exact manifest key, authorized command and private marker format.
Record direct finite green and typed RED without claiming live execution.
Preserve independent historical observer prerequisites for the same durable cut.
```

- Verification contribution: S1.
- After this file: Reconcile against REQ-001–003.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 remains independent.
- Expected result: Exact typed RED, 21 finite successes, compiled operator method and scope.
- Failure returns to: Files 1–6 for sources and executable checks, Files 7–9 for review.
- Completion criteria: Exact nine paths, all existing migrations unchanged, finite proof and separate I1.
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLoss.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLossTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S11HistoricalStartCutIT.java`, `docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md`, `docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md`, `docs/egon/evidence/2026-11-06-s11-start-response-loss-cut.md`.
- Commit: test(agent): prepare committed start response loss cut.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Baseline lock | Exact parent and existing/new files classified |
| R1 | Isolated candidate copy | Duplicate now returns IOException and finite expected IllegalStateException rejects |
| G1 | Owning offline reactor | 2 decorator, 8 S07 and 11 harness finite successes |
| G2 | Actual JDK 25 classes | AS and PS test classes compile, actual exact operator method and gate present |
| S1 | Strict review and staged scope | Nine exact paths and unchanged 29 SQL |
| I1 | Operator-only real services | One executed method and private validated cut marker |

```sh
cut_base=ac8384069257a457089bcf3d13b688b79ea9091b
cut_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cut_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-start-response-loss-cut/step157
cut_ps=stellar-enclosure-tianji-process-server
cut_operator=top.egon.cola.platform.tianji.process.server.acceptance.S11HistoricalStartCutIT
test "$(git rev-parse --verify HEAD)" = "$cut_base"
"$cut_scratch/run-scratch.sh"
"$cut_runner" "S11HistoricalStartLossTest,S07GovernedStartAgentFixtureTest,ExpenseAcceptanceHarnessTest" clean
/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home/bin/javap -v -p -classpath "$cut_ps/target/test-classes" "$cut_operator"
test -z "$(find "$cut_ps/target/surefire-reports" -name "TEST-$cut_operator*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

Use the exact [operator prerequisites and command](../evidence/2026-11-06-s11-start-response-loss-cut.md). Require one actual executed method with no skip and the corresponding private marker. Later protected reconciliation remains a separate observer.

## 9. Migration, Compatibility, Rollout, and Rollback

No production or migration changes. Revert the exact test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–6 | G0/G2/I1 |
| REQ-002 | Step 1 | 2–6 | R1/G1/I1 |
| REQ-003 | Step 1 | 1–9 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual operator IAM, Process and Agent child processes, PostgreSQL and original historical observer are independent. Keep stable native Review and no mutations to original evidence.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 沿用真实 AS Tool、PS START 与独立主库证人。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用 S07、现有同名 HttpClient Bean、JDK 和 JDBC。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 现有 JDK、JUnit、Mockito、Jackson 和 PostgreSQL JDBC。 | None |
| MC-NAME-001 | Applicable | PASS | §8 | 独立套件和测试类沿用当前命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 严格原命令、完整 owner、原 SERVICE、主库与单次状态。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 使用现有合同 DTO，私有 marker 保留四个字段。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 没有新增业务层模型或转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 不增加生产 logger 或输出凭据。 | None |
| MC-BEAN-001 | Applicable | PASS | §7 | 专用 child flag 注册具名 test-only 装饰器，绑定准确生产 Bean 名。 | None |
| MC-UTIL-001 | Applicable | PASS | §6 | 复用 JDK、Spring、Jackson 和已有 Driver。 | None |
| MC-JSON-001 | Applicable | PASS | §10 | 精确限长和严格 Jackson 入参及成功 DTO。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 使用原请求界限和单调时钟，派发到期由 PostgreSQL 判定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 新独立操作者 manifest，原生产与 S07 启动条件保持。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 测试装饰器仅拦截精确成功交付，避免更换 URL 和鉴权。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 仅六测试源和三份文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | typed RED、有限测试和实际独立 E 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 明确真实 IAM、HTTPS、子进程和 PostgreSQL 由操作者运行。 | None |

### 12.6 Final verdict

PASS — Ready for user review
