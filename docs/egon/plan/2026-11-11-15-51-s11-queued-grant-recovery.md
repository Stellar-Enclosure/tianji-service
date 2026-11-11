# T4-03 S11 原授权已持久化的 QUEUED Run 重建发现计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:51 Asia/Shanghai |
| Updated | 2026-11-11 15:51 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Four AS test sources and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | bc9e62f34752b78c305a53ffb349f150cdbcffa8 |
| Implements Spec | [S11 queued recovery](../spec/2026-11-11-15-50-s11-queued-grant-recovery.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:50 Asia/Shanghai SHA-256 d4e8965def61f16925d2d978b01484c645246b549dd292ee0a02c10cb1ace582 |
| Effective Specs | [S11 queued recovery](../spec/2026-11-11-15-50-s11-queued-grant-recovery.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一次性 PostgreSQL 中由原 JVM 通过实际 ledger 和 sealed grant 存储提交同一 QUEUED Run，再在首次 claim 前强制回收。父 JVM 新建独立 DataSource 和真实 AS 发现、授权、调度组件，仅从已持久化证据发现原 Run。Fake 模型执行一次，缺失或过期 grant 不进入发现，当期显式拒绝不得 claim。

## 2. Target Spec and Effective Design

[S11 queued recovery](../spec/2026-11-11-15-50-s11-queued-grant-recovery.md) §§4/7/11/14 控制原进程退出、准确持久化、AS 重建发现和有效授权。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/11 | 原 QUEUED 与有效 V6 grant 已提交，原 JVM 被强制回收 |
| REQ-002 | §7/9/10 | 仅原有效 Run 一次执行，缺失/过期 grant 排除，当前拒绝保持零 attempt |
| REQ-003 | §8/14/16 | 独立 R1、26 有限验证、两个默认跳过和七路径范围 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定 Service 基线、旧测试与三个新测试路径，执行 R1、G1/G2 和 S1。实际 I1 独立执行。

### 4.2 Test-first strategy

独立变异副本将专用库名放宽至普通 tianji_it_。新增有限用例应收到预期 IllegalArgumentException 缺失，再对原候选运行完整有限集。

### 4.3 Sequential and parallel boundaries

依序 G0/R1/G1/G2/S1。I1 需要独立的一次性 PostgreSQL 授权。

### 4.4 Commit boundaries

一个测试提交包含四份测试源和三份评审文档。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 原生产者退出 | Required §7/14 | EVD-001/004/005 | 同 JVM 双 poller 不能证明退出 | 独立子 JVM |
| AS 重建发现 | Required §7/9 | EVD-002/003 | 原生产 grant-aware poller | 复用 |
| 受控启动 | Required §5/14 | EVD-005 | 既有 AR 私有协议惯例 | test-local exact |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 原 queue、重建发现、负对照 | REQ-001/002 | G2/I1 | 3–4 | 既有 AS 和 AR |
| 私有启动和有限守卫 | REQ-003 | R1/G1 | 1–2 | JDK 子进程和 JDBC |
| 评审与证据 | REQ-003 | S1 | 5–7 | 原源路径和结果 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–005 | AS test owner |
| Persistence | EVD-001/002 | 原 V6 grant 和 Run 账本 |
| Production scope | EVD-001–003 | 原生产默认配置和合同 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 写入耐久原队列 | AgentLedgerService and AgentExecutionGrantStore | EVD-001 | 现有生产能力 | Reuse | 原进程测试驱动 | G2/I1 |
| AS 恢复 | TianjiGrantDispatchPostgresTest and TianjiGrantDispatchPoller | EVD-002/004 | 原 Fake 和真实 poller | Reuse | 小型共用方法 | G2/I1 |
| 进程安全 | AgentCheckpointCapturePostgresTest | EVD-005 | AR 既有包私有测试 | Reuse approach | 私有二字段 handoff | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–4 | 四份测试源码及固定选择器。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–4 | 严格一次性库名和准确 grant、Run、owner。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–4 | 复用原不可变 Run、Ticket 与私有 handoff。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–4 | 生产合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–4 | JDK、Mockito、JUnit 和已有 JDBC。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–4 | 生产合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–4 | 独立测试开关及私有启动。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–4 | 复用原 AS 发现与授权。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–4 | 单调等待及数据库时钟。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–4 | V1–V9 真实独立夹具及原迁移。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | CHILD_SECONDS and producer reap | Duration | System.nanoTime and java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Each phase bounded at 16 seconds with per-query limit | JVM monotonic nanos | Compatible with existing runtime fixture | Step 1 TEST-003 |
| TIME-002 | Original and expired grant expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Compare expiry against database clock before discovery | PostgreSQL microseconds and clock_timestamp | V6 preserves current expires_at | Step 1 TEST-003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoff.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoffTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiQueuedGrantRecoveryPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-queued-grant-recovery.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

精确 Service 基线 bc9e62f34752b78c305a53ffb349f150cdbcffa8。用 JDK 25 及现有离线 classpath。独立 I1 使用空 tianji_agent schema，保持基线 31 份迁移。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prove QUEUED recovery after the original grant producer exits

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: 原 Agent ledger、sealed grant、AS poller、当前授权和模型。
- Baseline state: 现有 PostgreSQL 同 JVM 双 poller 夹具。
- Observable outcome: 原提交后无首次 claim，退出后原 Run 由新实例一次完成，当前保护仍生效。
- End state: 四份测试源、三份文档和独立待执行 I1。
- Test-first gate: Required. 放宽专用库名必须触发有限 typed RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoff.java`

- Purpose: 私有、有界的生产者启动.
- Symbols: QueuedGrantRecoveryHandoff.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 私有、有界的生产者启动。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
if (!disposableLoopbackName(url) || invalidPostgresPort(url)) throw invalid();
writeFormatAndTwoBoundedFieldsToPrivateStdin();
requireExactFormatAndEndOfFrame(input.readNBytes(MAX_BYTES + 1));
```

- Verification contribution: R1/G1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoffTest.java`

- Purpose: 保护一次性库范围.
- Symbols: QueuedGrantRecoveryHandoffTest.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 保护一次性库范围。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertEquals(request, receive(send(request)));
assertThrows(IllegalArgumentException.class, () -> receive(extraTrailingBytes));
assertThrows(IllegalArgumentException.class, () -> handoff(genericTestDatabaseName, role));
```

- Verification contribution: R1/G1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPostgresTest.java`

- Purpose: 复用既有 Fake 执行依赖.
- Symbols: TianjiGrantDispatchPostgresTest shared fixture methods.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 复用既有 Fake 执行依赖。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
return fixtureDefinitionWithSameConfigPolicyAndModelRoute();
return fixtureModelThatCountsSubscriptionsAndEmitsTheSameUsageAndFinalResponse();
return fixtureFlowAndEstimatorWithUnchangedStrategiesAndBudgetEvidence();
```

- Verification contribution: G2/I1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiQueuedGrantRecoveryPostgresTest.java`

- Purpose: 证明原提交及重建发现.
- Symbols: TianjiQueuedGrantRecoveryPostgresTest#a_fresh_product_poller_recovers_one_committed_grant_ready_queue_after_producer_exit.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 证明原提交及重建发现。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
var producer = startProducer(privateHandoff);
assertQueuedCut(ledger, observer, awaitQueuedCut(observer, producer), queued);
reap(producer);
newPollerFromFreshComponents().start();
assertOneClaimAndModelResultWhileMissingExpiredAndDeniedRunsStayUnclaimed();
```

- Verification contribution: G2/I1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 5 — `CREATE docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md`

- Purpose: 记录 source ownership 与边界.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 记录 source ownership 与边界。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
trace REQ-001 to original committed Run and sealed V6 grant;
trace REQ-002 to AS poller and current authorizer, including missing and expired and denied controls;
trace REQ-003 to bounded private frame, exact selector, and isolated I1.
```

- Verification contribution: S1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 6 — `CREATE docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md`

- Purpose: 固定逐文件实现范围.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 固定逐文件实现范围。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
verify expected baseline, one modified test and three new test files;
run dedicated prefix R1 and 26 finite Java 25 tests with operator properties disabled;
validate strict Spec and Plan and compare exact seven paths and existing migrations.
```

- Verification contribution: S1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

#### File 7 — `CREATE docs/egon/evidence/2026-11-11-s11-queued-grant-recovery.md`

- Purpose: 保存精确有限和 I1 证据.
- Symbols: R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AS、AR 和独立 PostgreSQL 测试。
- Why now: 保存精确有限和 I1 证据。
- Contract/signature changes: 仅测试与评审。
- Input/output and state mapping: 原 Run 跨提交进程退出继续合法执行。
- Error and edge behavior: 精确范围、状态、授权或清理不匹配即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
record the expected missing IllegalArgumentException in R1 and 26 successful tests;
record bytecode selector and properties and two skipped operator methods;
leave actual isolated PostgreSQL and process outcomes awaiting the one selected I1 case.
```

- Verification contribution: S1.
- After this file: 与 REQ-001–003 及独立 I1 对齐。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1，I1 单独执行。
- Expected result: 一项精准反证、26 项有限验证、两个 PostgreSQL 默认跳过。
- Failure returns to: Files 1–4 检查源范围与行为，Files 5–7 检查证据。
- Completion criteria: 七条路径与基线 31 份迁移字节等同。
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoff.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/QueuedGrantRecoveryHandoffTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiGrantDispatchPostgresTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiQueuedGrantRecoveryPostgresTest.java`, `docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md`, `docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md`, `docs/egon/evidence/2026-11-11-s11-queued-grant-recovery.md`.
- Commit: test(agent): prepare queued grant recovery after producer exit.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | 原测试文件一致，三份新测试不存在 |
| R1 | Isolated Java 25 mutation | 仅专用库名保护反证失败 |
| G1 | Owning offline source reactor | 2 handoff + 11 poller + 13 authorizer，26 项通过 |
| G2 | Java 25 javap | 新方法、独立 PostgreSQL 和 child 开关，两 PostgreSQL 默认跳过 |
| S1 | Strict docs and scoped diff | 七条路径，31 份 SQL 无改动 |
| I1 | Operator-only isolated PostgreSQL | 一项实际执行的方法覆盖原进程退出及当前 grant 保护 |

```sh
queue_base=bc9e62f34752b78c305a53ffb349f150cdbcffa8
queue_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-queued-grant-recovery/step164
queue_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse --verify HEAD)" = "$queue_base"
"$queue_scratch/run-scratch.sh"
"$queue_runner" 'QueuedGrantRecoveryHandoffTest,TianjiGrantDispatchPollerTest,TianjiExecutionGrantAuthorizerTest' clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-50-s11-queued-grant-recovery.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-51-s11-queued-grant-recovery.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

由操作者提供独立、一次性空 schema 的可写 loopback PostgreSQL。库名符合 tianji_it_queue_recovery_ 加一至二十四个小写字母、数字或下划线。由操作者授权本地角色访问 setup 与子 JVM，URL 不接受凭据或查询参数。

```sh
: "${TIANJI_QUEUE_PRIVATE_PG_URL:?Provide the authorized disposable loopback URL}"
queue_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
queue_java=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
JAVA_HOME="$queue_java" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$queue_settings" -s "$queue_settings" -o -B -ntp \
  -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false \
  "-Dtest=TianjiQueuedGrantRecoveryPostgresTest#a_fresh_product_poller_recovers_one_committed_grant_ready_queue_after_producer_exit" \
  -Dtianji.postgres.queue.recovery=true "-Dtianji.postgres.queue.recovery.url=$TIANJI_QUEUE_PRIVATE_PG_URL"
```

保留新选择器的一项实际通过 XML。独立处置一次性数据库。实际 AS 服务替换、HTTPS 与 IAM 由另一份 E 证据覆盖。

## 9. Migration, Compatibility, Rollout, and Rollback

回滚为撤销该测试提交。既有 PostgreSQL 集成入口、SQL 与生产行为保持。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 4 | G2/I1 |
| REQ-002 | Step 1 | 3–4 | G2/I1 |
| REQ-003 | Step 1 | 1–7 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

一次性 PostgreSQL、实际原生产者退出和结果由独立操作者确认。真实 AS 服务器、IAM、HTTPS 与完整 S11 仍待 E。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | AS test owner 调用真实 AR grant ledger 和 AS poller。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用原 Fake 模型、预算、Definition、Poller 与 Authorizer。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK 25、JUnit、Spring JDBC、PG JDBC、Flyway 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 使用现有 AS 安全测试包与独立操作者属性。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 专用 loopback database、精确 owner 和 V6 grant 的提交证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 复用 AgentRunDTO 和 Ticket，私有 handoff 仅两个字段。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | 复用 AS 测试和 PostgreSQL 可用能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 既有生产合同及私有二进制 handoff。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 单调计时约束子进程，授权到期由数据库时钟确定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 独立测试和子进程入口默认关闭。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 复用生产发现路径和测试-only IAM adapter。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 四份测试、三份文档、原迁移和生产范围保持。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | R1 有限反证、G1/G2 和 I1 证据独立。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 实际 PostgreSQL、进程与 grant 可见性待隔离验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
