# T4-03 S11 原生 Session 输出已提交而账本投影未提交计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:34 Asia/Shanghai |
| Updated | 2026-11-11 15:34 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three existing AR tests and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 4313d3319ca228c2310d06dba9128dcb981d3f30 |
| Implements Spec | [S11 native output projection cut](../spec/2026-11-11-15-33-s11-native-output-projection-cut.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:33 Asia/Shanghai SHA-256 0a4b970bcdaa3982e3d10a4b05d5a0436e0ce473f307873fc40808e3ac513252 |
| Effective Specs | [S11 native output projection cut](../spec/2026-11-11-15-33-s11-native-output-projection-cut.md) |
| Depends On Plans | [Checkpoint takeover PostgreSQL](./2026-11-06-03-16-agent-checkpoint-takeover-postgres.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

扩展原 Agent Runtime 双 codec PostgreSQL 独立操作者用例，在真实原生 Session 输出已提交、AGENT_OUTPUT 投影等待时强制退出原 JVM。原 Run 通过现有过期 reconciler 进入 SUSPENDED。原首条输入 checkpoint 预览及接管在授权之前拒绝。

## 2. Target Spec and Effective Design

[S11 native output projection cut](../spec/2026-11-11-15-33-s11-native-output-projection-cut.md) §§4/7/11/14 控制隔离夹具、私有协议和有限测试。原 AR 拥有产品事务和恢复逻辑。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/11 | 精确等待时的已提交原生输出和空投影，原 JVM 在释放锁之前退出 |
| REQ-002 | §7/9/10 | 双 codec 保留原生事件，首输入恢复冲突，无授权和回执 |
| REQ-003 | §8/14/16 | 独立协议反证、有限测试、操作报告与六路径验证 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定基线与原三份测试，执行独立协议 RED、有限运行、字节码编译和严格文档范围。I1 由操作者运行。

### 4.2 Test-first strategy

独立 scratch 副本只放宽 PROJECT_OUTPUT 的无摘要检查。有限用例应因缺少 IllegalArgumentException 失败。准确候选运行同一有限用例。

### 4.3 Sequential and parallel boundaries

依序运行 G0/R1/G1/G2/S1。I1 使用独立授权环境。

### 4.4 Commit boundaries

一个测试提交包含三份测试源和三份文档。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 精确 JVM 切点 | Required §7/14 | EVD-001/002/005 | 复用真实 AR fork | 增加只拦截最终投影的守卫 |
| 私有阶段 | Required §7/9 | EVD-005 | 复用有界 handoff | 保留原编号及格式并扩展一个代码 |
| 故障后恢复验证 | Required §7/14 | EVD-003/004 | 原观察和接管入口 | 直接调用 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 操作者夹具 | REQ-001/002 | G2/I1 | 1 | 真实 AR 和原 V16 |
| 私有协议 | REQ-003 | R1/G1 | 2–3 | 原 fork handoff |
| 文档证据 | REQ-003 | S1 | 4–6 | 有限结果与源范围 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–005 | 原 AR fixture |
| Persistence | EVD-001–003 | 使用原事务和一次性测试触发器 |
| Production scope | EVD-001–004 | 现有业务源、迁移与 POM |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 源事件真实提交 | PostgresAdkSessionService and Runner | EVD-001 | 现有 | Reuse | 原运行产品路径 | G2/I1 |
| 精确切点和结果拒绝 | AgentCheckpointCapturePostgresTest and takeover | EVD-002–005 | 缺输出与投影故障 | Extend | 测试触发器与证人 | G2/I1 |
| 有界控制 handoff | AgentCheckpointForkHandoff | EVD-005 | 原阶段停在预算前 | Extend | 私有枚举值 | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–3 | 修改三份既有测试源码。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–3 | 严格按一次性数据库、角色、owner、Run 和投影类型限定故障。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–3 | 沿用原不可变 fixture 模型。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–3 | 原生产及其合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–3 | 既有 JDBC/JUnit/JDK 与测试工具。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–3 | 原生产及其合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–3 | 新开关仅属于独立测试方法。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–3 | 使用原 Test Fixture 与 PostgreSQL 事务 advisory lock。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–3 | 单调计时和数据库租约时钟。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–3 | 操作者触发器在一次性 schema 清理，原迁移无差异。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Waiter, reap and backend disconnect deadlines | Duration | monotonic long nanoseconds and java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Each wait bounded at 16 seconds with per-query bounds | System.nanoTime | Existing process fixture | Step 1 TEST-003 |
| TIME-002 | Run and Session lease expiry | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Existing exact fixture sets expiry in the past | PostgreSQL clock_timestamp | Existing fenced reconciliation | Step 1 TEST-003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md` |
| CREATE | `docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-native-output-projection-cut.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

固定 Service 基线 4313d3319ca228c2310d06dba9128dcb981d3f30。使用已有 JDK 25 有限依赖与独立 operator 环境。对比既有 31 份 SQL。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Cover the committed native output and pending ledger projection cut

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: 原 AR fork、V16、观察和接管。
- Baseline state: 已有首输入恢复、定义变更和原 process fork。
- Observable outcome: 保留原生输出，账本投影未提交，首输入接管拒绝。
- End state: 三份既有测试和三份文档。I1 独立验收。
- Test-first gate: Required. scratch 绕过 PROJECT_OUTPUT 摘要守卫时有限断言必须失败。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: 完成原生输出与账本投影切点.
- Symbols: AgentCheckpointCapturePostgresTest#rejects_first_input_takeover_after_native_output_commits_before_ledger_projection.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 完成原生输出与账本投影切点。
- Contract/signature changes: 既有测试内的私有 fixture 变更。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
while (child.isAlive() && before(waitDeadline)) verifyExactAdvisoryWait(runtimeRole, key, parentPid);
nativeRows = fixture.nativeRows(); fixture.assertProjectionAbsent();
try { reapForkChild(child); } finally { rollbackAdvisoryLock(); }
awaitOutputProjectionDisconnect(observer, waiterPid);
fixture.expireForkRunLease(cut); reconciler.reconcileDue(1); fixture.expireReconciledSessionLease(cut);
assertEquals(Observation.NOT_AT_CAPTURE_FENCE, observation.observe(actor, query));
```

- Verification contribution: G2/I1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`

- Purpose: 为原 worker 启动增加准确的内部阶段.
- Symbols: AgentCheckpointForkHandoff.Phase.PROJECT_OUTPUT.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 为原 worker 启动增加准确的内部阶段。
- Contract/signature changes: 既有测试内的私有 fixture 变更。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Phase.PROJECT_OUTPUT(3, "project-output", false);
if (phase.requiresDigest) output.writeUTF(cutSha256);
Request request = new Request(scope, phase, phase.requiresDigest ? input.readUTF() : null);
require(phase.requiresDigest == (request.cutSha256() != null) && input.read() == -1);
```

- Verification contribution: R1/G1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`

- Purpose: 验证新阶段和原协议.
- Symbols: AgentCheckpointForkHandoffTest#round_trips_output_projection_as_a_distinct_launch_without_a_digest.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 验证新阶段和原协议。
- Contract/signature changes: 既有测试内的私有 fixture 变更。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
for (boolean typed : List.of(false, true)) {
    byte[] frame = send(new Request(fixtureScope, typed, Phase.PROJECT_OUTPUT, null));
    assertEquals(3, frame[phaseOffset(frame)]); assertEquals(phaseOffset(frame) + 1, frame.length);
    assertThrows(IllegalArgumentException.class, () -> receive(appendByte(frame)));
}
assertThrows(IllegalArgumentException.class, () -> new Request(fixtureScope, Phase.PROJECT_OUTPUT, DIGEST));
```

- Verification contribution: R1/G1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。

#### File 4 — `CREATE docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md`

- Purpose: 记录准确方案和作用范围.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 记录准确方案和作用范围。
- Contract/signature changes: 评审文档。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
trace REQ-001 to native Session commit, scoped AGENT_OUTPUT wait and JVM reap;
trace REQ-002 to the existing observation and takeover before authority;
trace REQ-003 to the private PROJECT_OUTPUT handoff, default skip and precise migration scope.
```

- Verification contribution: S1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。

#### File 5 — `CREATE docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md`

- Purpose: 锁定按文件实现和验证门.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 锁定按文件实现和验证门。
- Contract/signature changes: 评审文档。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
lock exact Service HEAD and compare the three existing test files;
run R1 with PROJECT_OUTPUT digest validation relaxed, then recompile candidate with validation;
run both strict documentation validators and compare the six file paths and every migration.
```

- Verification contribution: S1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。

#### File 6 — `CREATE docs/egon/evidence/2026-11-11-s11-native-output-projection-cut.md`

- Purpose: 记录独立执行证据.
- Symbols: R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 原 Agent Runtime 产品和 PostgreSQL 测试夹具。
- Why now: 记录独立执行证据。
- Contract/signature changes: 评审文档。
- Input/output and state mapping: 双 codec 原生输出保留，首输入 takeover 在授权前拒绝。
- Error and edge behavior: 缺少精确等待、边界、清理或状态即失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
record the precise assertion failure and the full 27 passing finite results;
attach the compiled selector and properties from javap and report eight default skips;
record I1 pending separately with the isolated primary, exact role and two codec prerequisites.
```

- Verification contribution: S1.
- After this file: 维持 REQ-001–003 与单独 I1 的证据对应。


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 独立。
- Expected result: 一项精确 RED、27 项有限测试通过、可编译的默认关闭 operator 和六条路径。
- Failure returns to: Files 1–3 检查源和测试，Files 4–6 检查评审与证据。
- Completion criteria: 六条路径，既有 31 份 SQL 无差异，I1 明确待执行。
- Rollback: Revert this one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoff.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointForkHandoffTest.java`, `docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md`, `docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md`, `docs/egon/evidence/2026-11-11-s11-native-output-projection-cut.md`.
- Commit: test(agent): prepare committed native output projection cut.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | 三份既有测试，原 operator 没有新方法 |
| R1 | Isolated Java 25 mutation | 有限新用例因预期 IllegalArgumentException 未抛而失败，其他五项通过 |
| G1 | Owning offline source reactor | 6 handoff + 7 observation + 14 takeover，共 27 项成功 |
| G2 | Java 25 javap and opt-in test discovery | 精确方法和新开关，默认跳过八个 operator 方法 |
| S1 | Strict docs and scoped diff | 六条路径，31 份既有 SQL 不变 |
| I1 | Operator-only isolated PostgreSQL | 一项实际执行方法内验证两种 codec、真实子进程和不可接管结果 |

```sh
cut_base=4313d3319ca228c2310d06dba9128dcb981d3f30
cut_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-native-output-projection-cut/step163
cut_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cut_operator=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java
test "$(git rev-parse --verify HEAD)" = "$cut_base"
git cat-file -e "$cut_base:$cut_operator"
if git show "$cut_base:$cut_operator" | rg -q 'rejects_first_input_takeover_after_native_output_commits_before_ledger_projection'; then exit 1; fi
"$cut_scratch/run-scratch.sh"
"$cut_runner" 'AgentCheckpointForkHandoffTest,AgentCheckpointObservationServiceTest,AgentCheckpointTakeoverServiceTest' clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-33-s11-native-output-projection-cut.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-34-s11-native-output-projection-cut.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

沿用[原精确 PostgreSQL takeover 角色、pgvector 及运行函数](./2026-11-06-03-16-agent-checkpoint-takeover-postgres.md#separate-operator-only-i1)。启动前由操作者准备一次性空 schema 的 loopback 主库、独立受限 runtime 与 takeover 登录和 setup owner。沿用同名四个环境变量，凭据不得放入 URL。

```sh
cut_method=rejects_first_input_takeover_after_native_output_commits_before_ledger_projection
takeover_pg_reactor "-Dtest=AgentCheckpointCapturePostgresTest#$cut_method" \
  -Dtianji.postgres.checkpoint.capture=true \
  -Dtianji.postgres.checkpoint.capture.output-projection-cut=true \
  "-Dtianji.postgres.checkpoint.capture.url=$TIANJI_CHECKPOINT_PRIVATE_PG_URL" \
  "-Dtianji.postgres.checkpoint.capture.runtime-role=$TIANJI_CHECKPOINT_RUNTIME_ROLE" \
  "-Dtianji.postgres.checkpoint.capture.vector.version=$TIANJI_CHECKPOINT_VECTOR_VERSION" \
  "-Dtianji.postgres.checkpoint.takeover.role=$TIANJI_CHECKPOINT_TAKEOVER_ROLE"
```

保留 owning XML，核对方法实际执行且未跳过。由独立操作者按原夹具流程处置一次性数据库。

## 9. Migration, Compatibility, Rollout, and Rollback

新增阶段仅用于测试。三个原代码和格式兼容。回滚为撤销测试提交。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–6 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

双 codec 原生事件和真实进程退出尚待独立 PostgreSQL 执行。真实 IAM/HTTPS 与其他 S11 故障窗口另行验收。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | 复用 AR 持久 Session、投影和 takeover，故障由一次性夹具持有。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用现有 process fork、FakeModel、会话解码、租约过期和 reconciler。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK、JUnit、Spring JDBC、PG JDBC 和 Flyway。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 新增测试选择器和私有 handoff 枚举值。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 通过 tenant、Run、类型、runtime role 和精确 blocker PID 确认证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 复用 NativeRow、ForkCut 和原 owner 账本接口。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | 复用原 checkpoint fixture 和 JDK 单调时钟。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 仅既有测试 fixture 和私有二进制 handoff。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 有界等待、reap、连接查询与原数据库租约时钟。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 新增独立测试 opt-in，URL、角色、vector、V16 前置校验沿用原夹具。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 夹具触发器仅截获指定投影，原模型和运行链路保留。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 三份原测试源和三份评审文档。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | 私有 handoff 有限反证、源码编译及实际 operator 运行分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 实际两种 codec 的一次性 PostgreSQL 操作者报告待执行。 | None |

### 12.6 Final verdict

PASS — Ready for user review
