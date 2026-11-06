# T4-03 S11 受保护历史 START 对账的跨主库验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 08:04 Asia/Shanghai |
| Updated | 2026-11-06 08:04 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Two AS test sources and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 5052339f5d7cb3aa1b2510a28d849b7b6894456b |
| Implements Spec | [Historical START operator acceptance](../spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 08:03 Asia/Shanghai SHA-256 1c424ba4f77174b30cd5b0e55d3a44ca7b5f40cbf53c56f0662f110524974ae1 |
| Effective Specs | [Historical START operator acceptance](../spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md) |
| Depends On Plans | [Protected historical START](./2026-11-06-06-56-protected-historical-start-receipt.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在已配置的真实 AS、PS 和独立主库上，核对一次已提交但原 Tool 结果仍未知的 START。以原 SERVICE 独立读取、当前 USER 授权、两个主库只读证人与精确重放证明一次原业务动作和一次历史观察。

## 2. Target Spec and Effective Design

[Historical START operator acceptance](../spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md) §§4/7/11/14 governs this change. The existing AS current USER command and PS original SERVICE READ stay authoritative.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/11 | Exact opt-in two loopback TLS origins and two read-only witnesses of real stores |
| REQ-002 | §7/9/14 | Owner READ-only and foreign deny, exact original SERVICE GET, one positive AS observation and no repeated PS native START |
| REQ-003 | §8/14/16 | Typed cross-store RED, focused G1, actual gated class, exact five paths and unchanged migrations |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline and paths. Compile focused scratch test copies, run typed negative control, run owning finite and confirm actual compiled gate. Stage exact scope. I1 is operator-only.

### 4.2 Test-first strategy

Remove only the cross-store original Process recordedAt comparison in one isolated copy. The unchanged finite witness must reject the weakened fixture. Use the original for green and class gates.

### 4.3 Sequential and parallel boundaries

Run G0/R1/G1/G2/S1 sequentially. I1 requires the authorized real isolated cut and external services.

### 4.4 Commit boundaries

One focused test-only commit of two AS test files and three review documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Actual protected cross-store witness | Required §7/14 | EVD-001–005 | Existing AS/AR/PS evidence is independent or controlled | One test using existing SDKs |
| Exact independent timing witness | Required §10/14 | EVD-004 | SDK already validates own response wire | One focused finite test |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Protected external witness | REQ-001/002 | G2/I1 | 1 | Existing AS and PS with real IAM, V13 and native Process |
| Finite witness | REQ-003 | R1/G1 | 2 | Existing receipt types |
| Review evidence | REQ-003 | S1 | 3–5 | Exact scope and runtime boundaries |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Existing AS protected API package | Add test-only fixture |
| Persistence | Existing PS and AR receipt schemas | Two tightly scoped SELECT-only witness connections |
| Production scope | EVD-001–005 | Existing transport, SDK, config, policy and schema |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Real AS and PS calls | HttpAgentClient, HttpProcessClient | EVD-001/002/004 | SDK methods available at baseline | Reuse | Test call orchestration | G2/I1 |
| Durable source facts | Existing Process receipt and AR V13 and V2/V5 | EVD-003/005 | Need cross-store physical evidence | Reuse | Read-only witness SQL | G2/I1 |
| Exact receipt comparison | Existing typed wire DTOs | EVD-004 | Need independent storage correlation | Reuse | One fixture guard | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–2 | 沿用既有 AS 测试命名与不可变局部证人。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–2 | 沿用 SDK 合同，精确绑定真实两主库输入与证人。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–2 | 复用已发布合同类型，局部 record 为不可变证人。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–2 | 无新生产 Bean 或日志。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–2 | JDK、JDBC、JUnit 和既有 SDK。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–2 | 复用原 SDK Jackson 严格解码与数据库 JSONB。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–2 | 独立测试开关，实际配置仍由操作者控制。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–2 | 实际受保护 operator fixture 和双只读 Query。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–2 | Instant 按各原数据库时间精确比对。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–2 | 沿用两主库原 SQL 和业务事实，观察连接只读。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Process recordedAt and Agent observedAt | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | UTC Z | Not applicable: absolute instants | Not applicable: no user display | Compare timestamps to their own primary without assuming clock ordering | Existing independent database clocks and precision | No reinterpretation of original receipt times | Step 1 TEST-001/004 |
| TIME-002 | Test and JDBC/HTTPS bounds | Duration | java.time.Duration and JUnit Timeout | Not persisted | Internal | Not applicable: elapsed budget | Not applicable: no user display | Bounded transport and statement per fixture request | JDK and JUnit deadlines | Original production timeouts stay unchanged | Step 1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptHttpPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptWitnessTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-protected-historical-start-receipt.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 5052339f5d7cb3aa1b2510a28d849b7b6894456b. Existing JDK 25 offline settings. I1 requires real independently authorized services and primary witnesses prepared by the operator. Preserve all 28 existing SQL bytes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare protected historical START cross-store acceptance

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing AS protected historical command and SDK, PS receipt and original SERVICE, real operator cut.
- Baseline state: Related controlled AR and AS finite tests exist. Exact external witness and finite correlation tests are absent.
- Observable outcome: An explicitly selected operator test can prove original Process START and one historical observation across both primaries.
- End state: Two test classes and three review documents, I1 separate.
- Test-first gate: Required. One isolated original Process recordedAt typed RED.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptHttpPostgresTest.java`

- Purpose: 执行真实 HTTPS 和双主库只读核对.
- Symbols: reconciles_the_exact_committed_process_start_without_repeating_or_rewriting_the_original_effect.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected routes, PS original receipt, existing SDK and source or operator reviewer.
- Why now: 执行真实 HTTPS 和双主库只读核对.
- Contract/signature changes: Test-only fixture and witness.
- Input/output and state mapping: The actual exact scope and independent Agent and Process primary evidence.
- Error and edge behavior: Missing fixture, authority or evidence fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Verify distinct loopback TLS origins, each disposable primary, private bearer files and read-only observers.
Read exact unresolved V13 and original APPLIED Process receipt and one Flowable native history row.
Independently GET original receipt as original SERVICE, then prove owner READ-only and foreign USER deny.
Reconcile with exact original USER and compare one stored observation, unchanged original facts and exact repeat.
```

- Verification contribution: G0/G2/I1.
- After this file: Keep finite source and operator evidence separate.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptWitnessTest.java`

- Purpose: 有限证明跨主库关联.
- Symbols: requires_independent_process_recorded_time_and_exact_agent_observation.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected routes, PS original receipt, existing SDK and source or operator reviewer.
- Why now: 有限证明跨主库关联.
- Contract/signature changes: Test-only fixture and witness.
- Input/output and state mapping: The actual exact scope and independent Agent and Process primary evidence.
- Error and edge behavior: Missing fixture, authority or evidence fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Construct independent Process recordedAt and Agent observedAt without an ordering assumption.
Reject mismatch in Process recordedAt, Agent observedAt and native instance identity.
Require typed RED after deleting the Process recordedAt equality in an isolated test copy.
```

- Verification contribution: R1/G1.
- After this file: Keep finite source and operator evidence separate.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md`

- Purpose: 记录真实 operator 的准备与原事实范围.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected routes, PS original receipt, existing SDK and source or operator reviewer.
- Why now: 记录真实 operator 的准备与原事实范围.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: The actual exact scope and independent Agent and Process primary evidence.
- Error and edge behavior: Missing fixture, authority or evidence fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record V13 unresolved and original PS APPLIED prerequisites and exact actor affinity.
Trace two-primary observations and the finite cross-store witness to acceptance.
Separate existing configured runtime and actual response-drop proof from source checks.
```

- Verification contribution: S1.
- After this file: Keep finite source and operator evidence separate.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md`

- Purpose: 锁定执行顺序和批准的精确范围.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected routes, PS original receipt, existing SDK and source or operator reviewer.
- Why now: 锁定执行顺序和批准的精确范围.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: The actual exact scope and independent Agent and Process primary evidence.
- Error and edge behavior: Missing fixture, authority or evidence fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock current exact Service parent and the two source plus three review paths.
Run typed RED, owning finite report, compiled guard and strict docs.
Verify all 28 existing migrations and POMs are byte-identical.
```

- Verification contribution: S1.
- After this file: Keep finite source and operator evidence separate.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-s11-protected-historical-start-receipt.md`

- Purpose: 记录独立运行前置和实际已跑有限证据.
- Symbols: E1 and I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing AS protected routes, PS original receipt, existing SDK and source or operator reviewer.
- Why now: 记录独立运行前置和实际已跑有限证据.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: The actual exact scope and independent Agent and Process primary evidence.
- Error and edge behavior: Missing fixture, authority or evidence fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the exact scratch compile, typed failure and finite result count.
Provide named private settings, real current bearer prerequisites and exact isolated selector.
Keep the real IAM, fault trigger and databases pending until operator execution.
```

- Verification contribution: S1/I1.
- After this file: Keep finite source and operator evidence separate.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 is separate.
- Expected result: One typed RED, 20 owning finite results and compiled default-skipped operator gate.
- Failure returns to: Files 1–2 for test logic, Files 3–5 for review, exact scope and evidence.
- Completion criteria: Five exact new paths, actual finite gates and all existing migrations unchanged.
- Rollback: Revert this single test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptHttpPostgresTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptWitnessTest.java`, `docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md`, `docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md`, `docs/egon/evidence/2026-11-06-s11-protected-historical-start-receipt.md`.
- Commit: test(agent): prepare protected historical start receipt acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Both new Java sources absent |
| R1 | Isolated candidate copy | Removing original Process recordedAt equality triggers typed failure |
| G1 | Owning offline source reactor | 20 finite successes, zero errors, failures or skips |
| G2 | Actual JDK 25 classes | Compiled operator test with exact default-off gate and direct JUnit skip |
| S1 | Strict review and staged scope | Five exact paths and 28 byte-identical migrations |
| I1 | Authorized true AS, PS and DB | One actually executed operator method on a genuinely prepared cut |

```sh
history_base=5052339f5d7cb3aa1b2510a28d849b7b6894456b
history_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
history_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-historical-start-protected/step156
history_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
history_as=stellar-enclosure-tianji-agent-server
history_operator=top.egon.cola.platform.tianji.agent.server.api.AgentHistoricalStartReceiptHttpPostgresTest
test "$(git rev-parse --verify HEAD)" = "$history_base"
for source in stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptHttpPostgresTest.java stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptWitnessTest.java; do
  if git cat-file -e "$history_base:$source" 2>/dev/null; then exit 1; fi
done
"$history_scratch/run-scratch.sh"
"$history_runner" "AgentHistoricalStartReceiptWitnessTest,AgentHistoricalStartReceiptControllerTest,TianjiFixedBpmnStartReceiptReaderTest" clean
"$history_jdk/bin/javap" -p -v -classpath "$history_as/target/test-classes" "$history_operator"
test -z "$(find "$history_as/target/surefire-reports" -name "TEST-$history_operator*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

G1 expects 1 witness, 9 controller and 10 reader tests. R1 uses a scratch copy of the fixture with only the original Process recordedAt comparison removed, with no actual network, app, DB or IAM.

### Separate operator-only I1

Follow the exact [operator evidence](../evidence/2026-11-06-s11-protected-historical-start-receipt.md) for preparation and private arguments. Use one separate non-skipped method and collect real START-fault evidence with sanitized AS/PS/IAM audit showing original SERVICE READ. Do not run this selector in G1.

## 9. Migration, Compatibility, Rollout, and Rollback

Only tests and reviewer documents change. Runtime configuration, migrations and permission declarations remain at the current baseline. Revert the one commit to remove the test.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1–2 | R1/G1/I1 |
| REQ-003 | Step 1 | 1–5 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

The existing fault relay lacks Process START committed-response drop. Operator I1 requires a genuine externally produced cut and independent current IAM and original service auditing. The positive acceptance observer cannot create these artifacts. A missing-receipt E path remains separate.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 真实 AS 与 PS 分别保持其已授权职责，两个独立只读主库。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用既有 SDK、合同、精确 loopback 校验和生产路由。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 使用现有 JDK、SDK、JDBC 和 JUnit。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 两份新类沿用现有 AS API 测试命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 精确 owner、原 SERVICE、私有文件、只读角色与时间戳关联。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用合同 DTO，局部证人为不可变 record。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无新业务层转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新 logger 或凭据输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 无 Spring Bean 改动。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用 JDK、两 SDK 和先前 operator guard。 | None |
| MC-JSON-001 | Applicable | PASS | §10/14 | 由原 SDK 严格解码，SQL JSONB 验证原账本。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 按各来源独立精确比对 recordedAt 与 observedAt。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 独立测试 flag，部署配置由操作者准备。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 复用外部 operator fixture 与两个只读 Query。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 两份测试和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 独立 typed RED、有限回归、实际 operator E 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 真实 START 响应丢失切点和实际 IAM 审计需外部。 | None |

### 12.6 Final verdict

PASS — Ready for user review
