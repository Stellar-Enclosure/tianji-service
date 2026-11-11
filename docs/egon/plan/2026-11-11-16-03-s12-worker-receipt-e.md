# T4-02 S12 Worker 持久回执与 Process 确认重放计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 16:03 Asia/Shanghai |
| Updated | 2026-11-11 16:03 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | PS test-only Worker V1 / Process V4 receipt evidence and existing acknowledgement replay |
| Source Requirement | 主计划 T4-02/S12 |
| Baseline Revision | 2c72a6ef2f22c460dcae8cd04591237a2642e101 |
| Implements Spec | [S12 Worker receipt E Spec](../spec/2026-11-11-16-02-s12-worker-receipt-e.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 16:02 Asia/Shanghai SHA-256 813b5272e8761f93acfa8244e1faa5de49a99bc4d3d99c064299956551b7de1d |
| Effective Specs | [S12 Worker receipt E Spec](../spec/2026-11-11-16-02-s12-worker-receipt-e.md) |
| Depends On Plans | [S12 Receive Link replay](./2026-11-06-02-31-s12-receive-link-replay.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

沿用一次性双租户 Worker 恢复夹具。在真实 Worker 业务回执已提交、首个 PS 完成请求尚未转发时，从原业务 observer 读取 Worker V1 行。随后按原协议丢弃已提交 200 并核对同一命令重放。独立重读原 Worker 行、PS V4 完成行及唯一 Flowable 活动，验证同一业务收据在两套摘要域及两个原所有者下的关系。

## 2. Target Spec and Effective Design

目标 [S12 Worker receipt E Spec](../spec/2026-11-11-16-02-s12-worker-receipt-e.md) §§4/7/10/14。主计划 T4-02 支持独立 S12 验证。保留已有结果接收重放设计及 §14.7 传播限制。

## 3. Effective Requirements and Acceptance

| Requirement | Spec section | Effective statement | Observable acceptance | Files |
| --- | --- | --- | --- | --- |
| REQ-001 | §4 | 首次转发前读取已提交原 Worker 回执 | 只读 Business observer 通过 source、tenant、idempotency 精确读取 W V1，留本地记录。 | Files 1–8 |
| REQ-002 | §4 | 同一已提交完成可通过丢失 200 重放复核 | 同一请求、同一 WorkerReceiptDTO、原 W V1 全投影相等、唯一原 PS V4 及原生活动。 | Files 1–8 |
| REQ-003 | §4 | 原范围与摘要域完整 | 独立验明 Worker scope 与 PS full actor，两种原结果摘要，双租户同 correlation 的跨 owner 拒绝。 | Files 1–8 |
| REQ-004 | §4 | 独立有限与 E 证据且保持现有模式 | 单独 manifest，16 项有限、包不带夹具、源和资源边界核对；真实 E 明确待执行。 | Files 1–8 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定当前 Service HEAD。先写跨租户有限反证，补纯核验，再接原测试 Driver 的只读 observer 和独立 E 入口，运行有限门禁与干净包。

### 4.2 Test-first strategy

R1 在 `S12WorkerReceiptCorrelationEvidence#verify` 只删除 `&& scope.tenant() == before.tenant()`，运行 `S12WorkerReceiptCorrelationEvidenceTest#another_tenants_worker_row_and_another_process_worker_are_rejected`。预期唯一 `org.opentest4j.AssertionFailedError: Expected java.lang.AssertionError to be thrown, but nothing was thrown.`。保留首次原证人 scope 攻击维度一致的夹具。恢复 SHA 固定候选再运行 G1。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | 原 Worker recovery 与 S12 基线 | 独立 scratch 分析 | 共享 Maven、owner 索引及 operator 运行 | 同一成品和证据归属。 |

### 4.4 Commit boundaries

单一 PS test-only 提交，八个精确路径。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 精确故障阶段 | Use §7 | EVD-003 | 原 forwardedAcknowledgement | 仅扩测试包可见性。 |
| W/PS 独立读证人 | Use §7 | EVD-001/002/004 | 原效果与 PS lease 不含 W 行 | 加只读验证。 |
| 分开 operator | Use §7 | EVD-004 | 扩大默认 RECOVERY 套件会改变语义 | 独立属性和同旧 RECOVERY 结构。 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 纯回执核验 | REQ-001–003 | R1/G1 | 1–2 | 现有 scope key 和摘要。 |
| 独立 operator | REQ-001–004 | P1/I1 | 3–5 | 原已提交确认重放和三库权限。 |
| 审查 | REQ-004 | S1 | 6–8 | 当前基线。 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001/002/004 | 独立只读观察两库与 native。 |
| Transport | EVD-003/005 | 原有 relay 和原 header 规则。 |
| Testing | EVD-003/004 | 增量 opt-in，无生产装配。 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 原 200 重放 | WorkerRecoveryIT and ExpenseAcceptanceFaultRelay | EVD-003 | 确保同请求/DTO | 原逻辑直接复用 | 测试包可见性 | I1 |
| 持久观察 | Driver and Worker store | EVD-001/004 | 现有 observer 可读取原表 | 两个受限投影 | 无依赖 | G1/I1 |
| 摘要 | WorkerDigests and ProcessRequestDigest | EVD-001/002 | 长度编码相同，域独立 | 复用 ProcessRequestDigest 验证原两域 | 无依赖 | G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–5 | 有限核验、原测试类及独立 operator selector。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–5 | 全 actor、tenant、source、任务、occurrence、收据、原生活动和摘要。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–5 | 私有不可变证人使用现有 CompleteWorkerTaskCommand 语义。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–5 | 当前步骤无对应生产变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–5 | JDK 与已有摘要、Flowable 和 JDBC 原能力。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–5 | 当前步骤无对应生产变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–5 | 仅独立操作者 manifest 属性，既有配置和 suite 保持。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–5 | 在已有已提交 200 故障夹具上作一次性只读证人。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–5 | 各库原持久时刻只在各自的域验证；不跨库排序。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–5 | PS acceptance fixture 使用已有 W、PS 和只读 observer 归属。 | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Worker committed_at, PS decided_at | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute | Not applicable: no display | W pre/post exact equality; no cross-database timestamp order | Each owner PostgreSQL clock and existing precision | Existing Worker V1 and Process V4 | Step 1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path | Purpose | Step | Gate |
| --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidenceTest.java` | 有限跨租户、跨 actor、摘要域及不可变反证 | Step 1 | R1/G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidence.java` | 跨库证人纯校验 | Step 1 | R1/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` | 显式 operator 入口与两端受限 observer | Step 1 | G1/P1/I1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java` | 复用现有已提交 200 重放步骤 | Step 1 | P1/I1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptAcceptanceIT.java` | 双租户 operator 场景 | Step 1 | P1/I1 |
| CREATE | `docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md` | 锁定原所有者和可观察证据 | Step 1 | S1 |
| CREATE | `docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md` | 锁定验证顺序及路径 | Step 1 | S1 |
| CREATE | `docs/egon/evidence/2026-11-11-s12-worker-receipt-e.md` | 记录已执行与待执行状态 | Step 1 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

使用 JDK 25、现有离线 owner reactor 与无网络有限选择器。只提交列明八路径。真实操作使用独立私有 RECOVERY manifest、明确授权、新隔离三库、两租户及同批来源和产物。既有 fixture 锁定 PS V1–10、AS V1–14、W V1–2，operator 无需新增迁移版本或角色权限。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Verify the durable Worker receipt across replay of the committed Process acknowledgement

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: 原 Worker recovery、W V1、PS V4 和已有 scope key。
- Baseline state: 已有丢失已提交 200 重放证据，缺 W V1 pre/post 同一性。
- Observable outcome: 分别核对两库原持久回执、原 native 活动和两租户隔离。
- End state: 五个 test 源、三个审查文件及独立 E 入口。
- Test-first gate: Required. §4.2 精确 RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidenceTest.java`

- Purpose: 有限跨租户、跨 actor、摘要域及不可变反证。
- Symbols: S12WorkerReceiptCorrelationEvidenceTest.
- Repository evidence: EVD-001/002。
- Dependencies and consumers: 现有 PS acceptance、原 Worker 完成命令、双库 observer 和有限核验。
- Why now: 先固定错租户拒绝和实际重复确认期望。
- Contract/signature changes: 有限跨租户、跨 actor、摘要域及不可变反证。
- Input/output and state mapping: 完整原归属、已提交 V1/V4 记录、既有 native history，测试只读核验。
- Error and edge behavior: 缺失/错误归属/错误摘要/变化或多完成即失败，返回固定诊断。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。既有测试边界、不可变原投影、原摘要与 Duration/Instant；现有作用域和 SQL 保持。
- Implementation pseudocode:

```text
Construct exact original command and redacted V1/V4 rows.
Substitute another tenant for the Worker row and require the typed AssertionError.
Also exercise Process owner, immutable commit and each independent result digest.
```

- Verification contribution: R1/G1.
- After this file: 精确失败与正确路径可核对。

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidence.java`

- Purpose: 跨库证人纯校验。
- Symbols: S12WorkerReceiptCorrelationEvidence.
- Repository evidence: EVD-001/002。
- Dependencies and consumers: 现有 PS acceptance、原 Worker 完成命令、双库 observer 和有限核验。
- Why now: 在有限反证后恢复纯核验。
- Contract/signature changes: 跨库证人纯校验。
- Input/output and state mapping: 完整原归属、已提交 V1/V4 记录、既有 native history，测试只读核验。
- Error and edge behavior: 缺失/错误归属/错误摘要/变化或多完成即失败，返回固定诊断。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。既有测试边界、不可变原投影、原摘要与 Duration/Instant；现有作用域和 SQL 保持。
- Implementation pseudocode:

```text
derive occurrence key from tenant + task with FlowableWorkerService; verify original V1 owner and stable before/after row.
Verify V4 actor/instance/occurrence and exact receipt.
Use the two product digest domains and assert exactly one native completion.
```

- Verification contribution: R1/G1.
- After this file: 纯有限源证据。

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: 显式 operator 入口与两端受限 observer。
- Symbols: BusinessProcessAgentAcceptanceIT.
- Repository evidence: EVD-003/004。
- Dependencies and consumers: 现有 PS acceptance、原 Worker 完成命令、双库 observer 和有限核验。
- Why now: 在纯核验确定后只接原 fixture 的读边界。
- Contract/signature changes: 显式 operator 入口与两端受限 observer。
- Input/output and state mapping: 完整原归属、已提交 V1/V4 记录、既有 native history，测试只读核验。
- Error and edge behavior: 缺失/错误归属/错误摘要/变化或多完成即失败，返回固定诊断。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。既有测试边界、不可变原投影、原摘要与 Duration/Instant；现有作用域和 SQL 保持。
- Implementation pseudocode:

```text
openWorkerReceiptEvidence() chooses one distinct manifest property and the existing RECOVERY suite.
On the first exact committed Worker callback capture the V1 row before forwarding; after replay independently reread V1, V4 and native history.
Use bounded exact owner queries, no token result, and no wall-clock ordering across databases.
```

- Verification contribution: G1/P1/I1.
- After this file: 只在新 manifest 模式读取原行。

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`

- Purpose: 复用现有已提交 200 重放步骤。
- Symbols: WorkerRecoveryIT.
- Repository evidence: EVD-003/004。
- Dependencies and consumers: 现有 PS acceptance、原 Worker 完成命令、双库 observer 和有限核验。
- Why now: 复用已经在原用例中运行的准确故障窗口。
- Contract/signature changes: 复用现有已提交 200 重放步骤。
- Input/output and state mapping: 完整原归属、已提交 V1/V4 记录、既有 native history，测试只读核验。
- Error and edge behavior: 缺失/错误归属/错误摘要/变化或多完成即失败，返回固定诊断。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。既有测试边界、不可变原投影、原摘要与 Duration/Instant；现有作用域和 SQL 保持。
- Implementation pseudocode:

```text
Change visibility of forwardedAcknowledgement(Driver, RunDTO) within the test package.
Keep the existing scenario and all its actions unchanged.
The new operator entry may invoke the same verified relay stage.
```

- Verification contribution: P1/I1.
- After this file: 旧测试仍运行原方法。

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptAcceptanceIT.java`

- Purpose: 双租户 operator 场景。
- Symbols: S12WorkerReceiptAcceptanceIT.
- Repository evidence: EVD-003/004。
- Dependencies and consumers: 现有 PS acceptance、原 Worker 完成命令、双库 observer 和有限核验。
- Why now: 连接显式选择器及两租户。
- Contract/signature changes: 双租户 operator 场景。
- Input/output and state mapping: 完整原归属、已提交 V1/V4 记录、既有 native history，测试只读核验。
- Error and edge behavior: 缺失/错误归属/错误摘要/变化或多完成即失败，返回固定诊断。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。既有测试边界、不可变原投影、原摘要与 Duration/Instant；现有作用域和 SQL 保持。
- Implementation pseudocode:

```text
Open the isolated operator manifest; for each existing configured tenant start the same business label.
Call forwardedAcknowledgement and verifyWorkerReceiptCorrelation; confirm protected cross-tenant reads are denied.
After closing owned resources report only the success marker and fixed count.
```

- Verification contribution: P1/I1.
- After this file: operator 场景编译。

#### File 6 — `CREATE docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md`

- Purpose: 锁定原所有者和可观察证据。
- Symbols: 2026-11-11-16-02-s12-worker-receipt-e.
- Repository evidence: EVD-001–005。
- Dependencies and consumers: 同一步骤的评审和执行者。
- Why now: 冻结范围与证人。
- Contract/signature changes: 锁定原所有者和可观察证据。
- Input/output and state mapping: 锁定需求、精确路径和当前证据。
- Error and edge behavior: 缺失或未执行的验收明确保持待定。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。保存原规则的对应设计和验证证据。
- Implementation pseudocode:

```text
Record exact ownership and durable observation seams.
Trace each requirement and digest domain to a source or finite test.
Keep durable trace carrier and actual E operator proof separate.
```

- Verification contribution: S1.
- After this file: 范围完整。

#### File 7 — `CREATE docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md`

- Purpose: 锁定验证顺序及路径。
- Symbols: 2026-11-11-16-03-s12-worker-receipt-e.
- Repository evidence: EVD-001–005。
- Dependencies and consumers: 同一步骤的评审和执行者。
- Why now: 固定复现和验证顺序。
- Contract/signature changes: 锁定验证顺序及路径。
- Input/output and state mapping: 锁定需求、精确路径和当前证据。
- Error and edge behavior: 缺失或未执行的验收明确保持待定。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。保存原规则的对应设计和验证证据。
- Implementation pseudocode:

```text
Lock the selected base, exact mutation and isolated test selection.
Verify artifact scope and exact paths after finite gates.
Keep explicit operator manifest and runtime acceptance independent.
```

- Verification contribution: S1.
- After this file: 执行门禁明确。

#### File 8 — `CREATE docs/egon/evidence/2026-11-11-s12-worker-receipt-e.md`

- Purpose: 记录已执行与待执行状态。
- Symbols: 2026-11-11-s12-worker-receipt-e.
- Repository evidence: EVD-001–005。
- Dependencies and consumers: 同一步骤的评审和执行者。
- Why now: 填入实际有限结果和待执行 operator。
- Contract/signature changes: 记录已执行与待执行状态。
- Input/output and state mapping: 锁定需求、精确路径和当前证据。
- Error and edge behavior: 缺失或未执行的验收明确保持待定。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。保存原规则的对应设计和验证证据。
- Implementation pseudocode:

```text
Record actual typed RED and finite GREEN counts.
Record whether the owning clean package and explicit operator test have run.
State which privacy and source boundaries were checked.
```

- Verification contribution: S1.
- After this file: 有限/实际分离。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 精确 R1/G1/P1/S1，I1 由获授权 operator 执行。
- Expected result: 一项精确反证，16 项有限通过，生产包无夹具和配置变化，八路径。
- Failure returns to: 反证回到 Files 1–2，集成门禁回到 Files 3–5，范围回到 Files 6–8。
- Completion criteria: 通过有限门禁和实际包检查；独立真实 E 单列。
- Rollback: Revert this test-only commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidenceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptCorrelationEvidence.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12WorkerReceiptAcceptanceIT.java`, `docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md`, `docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md`, `docs/egon/evidence/2026-11-11-s12-worker-receipt-e.md`.
- Commit: test(observability): correlate durable Worker acknowledgement evidence.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | S12WorkerReceiptCorrelationEvidenceTest#another_tenants_worker_row_and_another_process_worker_are_rejected | 唯一预期 AssertionFailedError 与 §4.2 精确文本。 |
| G1 | S12WorkerReceiptCorrelationEvidenceTest,ExpenseWorkerCompletionFaultTest,ExpenseAcceptanceHarnessTest | 3 / 2 / 11 共 16 项，零失败、错误和跳过。 |
| P1 | owner clean verify with permission producer | 同 16 项；新 IT 编译仅在 test-classes，实际 AS/PS 配置、包、PaC 与原 SQL 保持。 |
| S1 | strict Spec / Plan、git diff --check、staged path list | 两个文档验证通过，恰好八路径。 |
| I1 | S12WorkerReceiptAcceptanceIT#both_owned_worker_receipts_survive_the_committed_process_acknowledgement_replay | 隔离双租户通过，输出固定 `S12_WORKER_RECEIPT_SUBSET paths=2`。 |

```sh
s12_base=2c72a6ef2f22c460dcae8cd04591237a2642e101
s12_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
s12_r1='S12WorkerReceiptCorrelationEvidenceTest#another_tenants_worker_row_and_another_process_worker_are_rejected'
s12_suite='S12WorkerReceiptCorrelationEvidenceTest,ExpenseWorkerCompletionFaultTest,ExpenseAcceptanceHarnessTest'
test "$(git rev-parse HEAD)" = "$s12_base"
# Run on the §4.2 mutation; confirm the exact typed failure and restore the source hash.
"$s12_runner" "$s12_r1"
"$s12_runner" "$s12_suite"
STELLAR_VERIFY_GOAL=verify "$s12_runner" "$s12_suite" clean -Ppermission-producer "-Dpermission.commit=$s12_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-worker-receipt
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-16-02-s12-worker-receipt-e.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-16-03-s12-worker-receipt-e.md
git diff --check
```

P1 比对源 SQL/应用配置与当前基线，检查 AS/PS exec JAR 无 S12WorkerReceipt 新夹具或其他生产变化，确认三个新增 fixture 类及 Driver/WorkerRecovery fixture 在 PS test-classes。权限清单按原 semantic PaC 比较，排除其既有构建版本标签。

### Separate operator-only I1

从 Service 根目录，在有权操作者的隔离环境中提供只读观察者、同批已构建制品及真实 IAM/HTTPS 的 RECOVERY 私有 manifest。借用现有 suite 版本与权限。路径只在私有输入传递。

```sh
test -n "$S12_WORKER_RECEIPT_MANIFEST"
"$s12_runner" 'S12WorkerReceiptAcceptanceIT#both_owned_worker_receipts_survive_the_committed_process_acknowledgement_replay' "-Dtianji.s12.worker-receipt.manifest=$S12_WORKER_RECEIPT_MANIFEST"
```

保留 PS owning module 的 Surefire XML，核对该方法实际执行且未跳过，并保留固定计数标记。

## 9. Migration, Compatibility, Rollout, and Rollback

生产配置、SQL、授权、公开合约和默认测试模式沿用当前版本。回滚仅撤销本测试提交。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate | Evidence |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | I1 | 首次转发前独立 W V1 观测。 |
| REQ-002 | Step 1 | G1/I1 | 原行、原确认、单次 native。 |
| REQ-003 | Step 1 | R1/G1/I1 | tenant/source/actor 与两个独立摘要域。 |
| REQ-004 | Step 1 | P1/S1 | 干净包和独立 operator。 |

## 11. Risks, Blockers, and User Decisions

真实运行仍待操作者。持久 Span Context 未进入现有 Worker 协议。跨重启实际原 Span Link 需独立批准的受信载体、持久化、保留及按现有权限导出，不从原业务 ID 合成。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | PS acceptance fixture 使用已有 W、PS 和只读 observer 归属。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用原 Driver、relay、业务处理、scope key 和原摘要编码。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 原 JDK 25、JUnit、JDBC 与依赖足够。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 有限核验、原测试类及独立 operator selector。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 全 actor、tenant、source、任务、occurrence、收据、原生活动和摘要。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 私有不可变证人使用现有 CompleteWorkerTaskCommand 语义。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | JDK 与已有摘要、Flowable 和 JDBC 原能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 测试只读证人无此生产职责。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 各库原持久时刻只在各自的域验证；不跨库排序。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 仅独立操作者 manifest 属性，既有配置和 suite 保持。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 在已有已提交 200 故障夹具上作一次性只读证人。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 五份测试源、三份文档，无新生产范围。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | 定向反证、16 项有限用例、成品与真实 operator 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 真实 E、Worker 持久 Span Link、collector 与授权独立。 | None |

### 12.6 Final verdict

PASS — Ready for user review
