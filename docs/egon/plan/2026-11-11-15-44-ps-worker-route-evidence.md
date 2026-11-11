# T4-01 A6 PS 本地 Worker 路由证据计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:44 Asia/Shanghai |
| Updated | 2026-11-11 15:44 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | PS 本地 Worker 注册与 Agent 桥接路由证据 |
| Source Requirement | 主计划 T4-01 |
| Baseline Revision | ae8ad136e65a5924ebf236dcddda3274cd049696 |
| Implements Spec | [A6 Spec](../spec/2026-11-11-15-43-ps-worker-route-evidence.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:43 Asia/Shanghai SHA-256 2ca59f2e06c7b85191293521f28628bc0da40a227718f8bb745093d3bbecec74 |
| Effective Specs | [A6 Spec](../spec/2026-11-11-15-43-ps-worker-route-evidence.md) |
| Depends On Plans | [A5](./2026-11-11-15-29-governed-process-start.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

从同一个 Process Server 的现有桥接验证输入读取唯一 Worker 路由，生成绑定与可复算的注册摘要。A4/A5 可以复用该输入，实际部署、跨实例一致性与历史绑定保留由独立权威承担。

## 2. Target Spec and Effective Design

[A6 Spec](../spec/2026-11-11-15-43-ps-worker-route-evidence.md) §§4/7/9/10/14 决定唯一性、本地证据和安全边界。[A5](../spec/2026-11-11-15-28-governed-process-start.md) §7 仅为下游使用背景。主计划授权继续残余源码和逐步提交，本规格保持 Review。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | §4 | 仅启用的已验证本地桥接可出具证据 | 复用实际桥接绑定、Worker 注册、结果接收、信任及 OAuth 身份校验。关闭时在 OAuth 读取前拒绝，后续查找无 I/O。 | EVD-001/004 |
| REQ-002 | §4 | 按原生三元组跨身份检查唯一性 | 正 tenant、原 topic/schema 语法。完整注册与绑定各恰一条，且服务身份、内部 dispatch 模式一致。缺失或多条返回空。 | EVD-001/002 |
| REQ-003 | §4 | 稳定且限于非秘密配置的注册摘要 | 独立固定向量、乱序等价、映射/租约/重试/并发/资源/应用/身份变动产生新摘要。可交给既有 A4 外层绑定算法。 | EVD-003/004/005 |
| REQ-004 | §4 | 仅增加显式本地适配器和有限验收 | 运行入口、配置、权限、存储及 A4/A5 调用关系按基线。单反证、11 项有限测试、真实包结构及精确 5 路径可核对。 | EVD-002/005 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定 Service 基线及 5 路径。先写有效重复三元组测试，再用 scratch 变异观测唯一预期失败。恢复跨身份匹配源码，运行既有装配及 Registry 回归，然后干净打包。

### 4.2 Test-first strategy

R1 变异有且仅有两处同一查找行为的替换。将 `properties.bindings().stream()` 过滤后的 `.limit(2)` 改成 `.limit(1)`，并在 `registrations.stream()` 原三元组过滤上增加 `&& item.actor().equals(bindings.getFirst().dispatch().actor())`。运行 `ProcessAgentLocalWorkerRouteEvidenceTest#two_valid_different_worker_actors_on_the_same_native_tuple_are_ambiguous`。预期唯一失败 `org.opentest4j.AssertionFailedError: expected: <true> but was: <false>`。恢复逐字节候选后进入 G1。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | A5 已有 Worker 指纹；确认最终基线 | 独立 AS 本地读取设计或 S11 测试候选 | 共享 Maven reactor 与同一提交索引 | 源码分开，执行只用实际 owner。 |

### 4.4 Commit boundaries

一个 PS 提交，两个源码路径与三份评审文档。路径按精确清单核对。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 已有桥接校验 | Use §7 | EVD-001/004 | 直接调用原验证 | 复用。 |
| 本地三元组和摘要 | Use §7 | EVD-002/005 | 单 actor require 无法识别另一个同路由服务 | 单个显式适配器。 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 精确本地证据 | REQ-001–003 | R1/G1 | 1–2 | PS bridge 与 Worker 现有注册、契约摘要。 |
| 提交与可复算 | REQ-004 | P1/S1 | 3–5 | 源码、产物和评审基线。 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–003 | 传统 PS bridge 适配器，直接使用现有配置，无存储和 Bean。 |
| Validation | EVD-001/004 | 原配置验证加精确三元组和完整身份匹配。 |
| Dependency | EVD-004/005 | 现有 Shoubing、Tianji Contract 和 JDK。 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 配置校验 | ProcessAgentBridgeConfiguration#validateBindings | EVD-001/004 | 已验证实际配置、注册、租约、结果、issuer、OAuth key | 直接调用 | 无 | G1 |
| 唯一性 | WorkerTopicRegistry#require/#registrations | EVD-002 | actor require 不跨 actor | 过滤完整不可变列表 | 技术适配器 | R1/G1 |
| 注册摘要 | OrchestrationRequestDigests#hash | EVD-005 | 现有按值/长度有序编码 | 专用 V1 领域和有限映射 | 无依赖 | G1/P1 |

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
| Rule 1 | §6.2 | EVD-001–005 | Files 1–2 | 技术适配器与私有构造的不可变 Route 证据。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–005 | Files 1–2 | 在现有配置验证之后核对原生三元组、跨身份唯一和完整服务身份。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–005 | Files 1–2 | 组合现有 AgentDispatchBinding 和固定摘要，无复制模型。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–005 | Files 1–2 | 当前步骤无对应变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–005 | Files 1–2 | JDK 集合及现有 OrchestrationRequestDigests。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–005 | Files 1–2 | 当前步骤无对应变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | EVD-001–005 | Files 1–2 | 复用已绑定 dispatchEnabled 和现有配置，验证各环境资源一致。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §6.2 | EVD-001–005 | Files 1–2 | 用只读技术适配器组合原注册和桥接权威。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–005 | Files 1–2 | 现有 Duration 的毫秒配置，不签发时间窗。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–005 | Files 1–2 | 保留 PS 的传统分层与 bridge 边界。 | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | leaseDuration, retryDelay, httpTimeout, acknowledgementReserve, maxClockSkew | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Existing validated bounds; no instant or freshness guarantee | Whole milliseconds, no clock read | Existing timing values encoded losslessly | Step 1 TEST-003/G1 |

## 5. Change File Tree

| Operation | Exact path | Symbol | Purpose | Step | Gate |
| --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidenceTest.java` | ProcessAgentLocalWorkerRouteEvidenceTest | 先锁定本地三元组歧义和既有校验。 | Step 1 | R1/G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidence.java` | ProcessAgentLocalWorkerRouteEvidence | 生成可独立复算的本地路由证据。 | Step 1 | G1/P1 |
| CREATE | `docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md` | REQ-001–004 | 记录证据范围、边界和可观察结果。 | Step 1 | S1 |
| CREATE | `docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md` | Step 1 | 固定测试、变异、工作目录和提交范围。 | Step 1 | S1 |
| CREATE | `docs/egon/evidence/2026-11-11-ps-worker-route-evidence.md` | R1/G1/P1/S1/I1 | 记录执行证据与待核验条件。 | Step 1 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

JDK 25、现有离线 Maven settings 与 operator 预备的 owner runner。核对 Service HEAD 后使用精确路径，保留其他工作。R1 反证源是 scratch 拷贝，退出后恢复候选。已有 Spring 启用、授权、租约和 AS 桥接由既有入口持有。当前编译依赖只使用原 pom 声明。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prove one configured PS Worker route

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: A5 Worker 指纹与既有桥接验证。
- Baseline state: 各 actor 可独立拥有同一原生三元组，本地证据适配器缺失。
- Observable outcome: 配置有效、启用且唯一时返回已验证绑定和注册摘要。
- End state: 精确五路径与有限反证、回归、成品和审核记录。
- Test-first gate: Required. 按 §4.2 变异时唯一确定性断言失败。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidenceTest.java`

- Purpose: 先锁定本地三元组歧义和既有校验。
- Symbols: ProcessAgentLocalWorkerRouteEvidenceTest.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: 现有桥接配置、Worker 注册、Shoubing 和契约摘要；本地调用方显式提供。
- Why now: 先锁失败断言和合法两身份夹具。
- Contract/signature changes: JUnit 有限 test class，四个 test 方法。
- Input/output and state mapping: 双 actor 使用同一三元组并建立完整合法绑定；验证有序 SHA、限额和失败。
- Error and edge behavior: RED 精确 AssertionFailedError；其他测试保持通过。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。显式 JUnit 配置 fixture 和不可变对象，使用既有值校验、JDK 集合及 Duration。
- Implementation pseudocode:

```text
Local fixture creates two distinct fully configured services for one tenant, topic and schema.
assertTrue(validate(...).find(20, "agent-start", "1").isEmpty());
assert other fixtures pin registration digest, scope, limits and no token calls.
```

- Verification contribution: R1/G1.
- After this file: 可执行确定性用例。

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidence.java`

- Purpose: 生成可独立复算的本地路由证据。
- Symbols: ProcessAgentLocalWorkerRouteEvidence.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: 现有桥接配置、Worker 注册、Shoubing 和契约摘要；本地调用方显式提供。
- Why now: 在有限反证后恢复跨身份唯一性。
- Contract/signature changes: validate(properties, topics, producers, inbox, starter, issuers, oauth)，find(long, String, String) → Optional<Route>。
- Input/output and state mapping: 单份配置快照经验证后读取；完整三元组和 actor 对齐，输出绑定和 V1 摘要。
- Error and edge behavior: 非法参数和原配置错误按既有校验；关闭拒绝；缺失或重复为空。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。明确分层与显式构造，复用现有配置、身份、Duration 和摘要。
- Implementation pseudocode:

```text
validate(...) calls existing validateBindings(...) after dispatchEnabled.
find(...) validates native topic; filter full lists by tenant/topic/schema; each size must equal one.
Compare full ProcessActor and internal mode; hash exact non-secret typed fields; return Optional.of(Route).
```

- Verification contribution: G1/P1.
- After this file: 可显式读取本地证据。

#### File 3 — `CREATE docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md`

- Purpose: 记录证据范围、边界和可观察结果。
- Symbols: REQ-001–004.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: 本步骤的评审者与执行者，关联 A5。
- Why now: 追踪本地源码与受限证据。
- Contract/signature changes: Spec V9，REQ-001–004。
- Input/output and state mapping: 请求、验收、适配器合同。
- Error and edge behavior: 记录本地快照前提和外部依赖。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。文档记录对应设计和执行证据。
- Implementation pseudocode:

```text
Document all seven validation collaborators and exact three-part lookup input.
Record the full SERVICE actor comparison and absence on ambiguous native routes.
Trace local immutable evidence and keep deployment checks at I1.
```

- Verification contribution: S1.
- After this file: 可独立核对完整边界。

#### File 4 — `CREATE docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md`

- Purpose: 固定测试、变异、工作目录和提交范围。
- Symbols: Step 1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: 本步骤的评审者与执行者，关联 A5。
- Why now: 冻结执行门禁与精确路径。
- Contract/signature changes: Plan V5，一个 Step。
- Input/output and state mapping: 基线、测试、源码、包与证据。
- Error and edge behavior: 失败回到具体源码或证据阶段。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。文档记录对应设计和执行证据。
- Implementation pseudocode:

```text
Lock the single Step and five exact paths against the current Service base.
Use the exact red selector, validate JUnit failure type and restore the candidate.
Run G1 and clean package, then check strict documents.
```

- Verification contribution: S1.
- After this file: 可按确定的门禁顺序执行。

#### File 5 — `CREATE docs/egon/evidence/2026-11-11-ps-worker-route-evidence.md`

- Purpose: 记录执行证据与待核验条件。
- Symbols: R1/G1/P1/S1/I1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: 本步骤的评审者与执行者，关联 A5。
- Why now: 记录已实际执行的有限验证。
- Contract/signature changes: R1/G1/P1/S1/I1 实际状态。
- Input/output and state mapping: 有限和实际 operator 状态。
- Error and edge behavior: 只记已执行内容。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11。文档记录对应设计和执行证据。
- Implementation pseudocode:

```text
Capture actual finite JUnit counts and the exact expected assertion failure.
Add the artifact entries, configuration/resource comparison and PaC verification after owner run.
Keep fleet proof open for the independent operator.
```

- Verification contribution: S1.
- After this file: 有限和实际部署证据分别可追踪。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: 依 §8 的精确 selector 和 owner runner 依次执行 R1/G1/P1/S1。
- Expected result: 一项精确 RED，G1 与 P1 各 11 项且零失败/错误/跳过，严格文档及成品检查通过。
- Failure returns to: R1 检查 fixture/变异，G1 检查适配器，P1 检查打包与配置证据，S1 检查精确范围。
- Completion criteria: 全部门禁、五条精确路径，I1 独立留档。
- Rollback: Revert the one A6 commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidenceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidence.java`, `docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md`, `docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md`, `docs/egon/evidence/2026-11-11-ps-worker-route-evidence.md`.
- Commit: feat(process): derive local worker route evidence.

## 8. Test, Validation, and Quality Gates

| Gate | Selector / method | Expected |
| --- | --- | --- |
| R1 | ProcessAgentLocalWorkerRouteEvidenceTest#two_valid_different_worker_actors_on_the_same_native_tuple_are_ambiguous | 唯一预期 org.opentest4j.AssertionFailedError，消息 expected: <true> but was: <false>。 |
| G1 | ProcessAgentLocalWorkerRouteEvidenceTest,ProcessAgentBridgeConfigurationTest,WorkerTopicRegistryTest | 11 项，零失败、错误或跳过。 |
| P1 | owner reactor clean verify + permission producer | 同 11 项，实际 PS jar 含 adapter 及 Route 类，PS/AS 打包 SQL 与配置逐字节基线一致。 |
| S1 | strict Spec/Plan, git diff --check, expected staged paths | Strict PASS，精确 5 路径。 |
| I1 | 真实部署、跨实例唯一、历史保留、PS 与 AS 权威结合 | 操作者另行执行。 |

```sh
a6_base=ae8ad136e65a5924ebf236dcddda3274cd049696
a6_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
a6_r1='ProcessAgentLocalWorkerRouteEvidenceTest#two_valid_different_worker_actors_on_the_same_native_tuple_are_ambiguous'
a6_suite='ProcessAgentLocalWorkerRouteEvidenceTest,ProcessAgentBridgeConfigurationTest,WorkerTopicRegistryTest'
a6_checks=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/ps-worker-route-evidence/checks
a6_previous=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/as-published-definition-evidence
test "$(git rev-parse HEAD)" = "$a6_base"
# Run on the temporary §4.2 mutation; the selected Surefire testcase must fail exactly as described.
"$a6_runner" "$a6_r1"
# Restore reviewed source and verify its SHA-256 before continuing.
"$a6_runner" "$a6_suite"
STELLAR_VERIFY_GOAL=verify "$a6_runner" "$a6_suite" clean -Ppermission-producer "-Dpermission.commit=$a6_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-ps-worker-route-evidence
python3 "$a6_checks/check-owned-package.py" "$a6_checks/owned-proof" "$a6_previous" "$a6_base" LOCAL_UNCOMMITTED-ps-worker-route-evidence
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-43-ps-worker-route-evidence.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-44-ps-worker-route-evidence.md
git diff --check
```

P1 从实际 Process Server JAR 读取 `BOOT-INF/classes/top/egon/cola/platform/tianji/process/server/bridge/ProcessAgentLocalWorkerRouteEvidence.class` 和对应 `$Route.class`，核对公开 Java 签名、摘要域和无 Spring stereotype。将两端 `BOOT-INF/classes/db/`、`application*.yml` / `*.yaml` / `*.properties` 逐项和基线对应资源核对，权限生成物按已有 PaC 语义门禁校验。S1 核对实际 staged paths 与 §7 列表完全相等。

## 9. Migration, Compatibility, Rollout, and Rollback

源配置、V12/V13、原装配和 AS 契约保持兼容。上线接入由后续独立权威负责。只回滚新增的 A6 适配器、测试和文档。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Verification | Evidence |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | G1 | 精确配置、dispatch 与 OAuth 验证。 |
| REQ-002 | Step 1 | R1/G1 | 合法双 actor 场景可稳定反证。 |
| REQ-003 | Step 1 | G1/P1 | 固定摘要、语义差异和成品字节码。 |
| REQ-004 | Step 1 | P1/S1 | 成品无自动装配变更，精确路径，I1 独立。 |

## 11. Risks, Blockers, and User Decisions

跨实例有效性和历史路由保留仍需真实部署权威。显式调用方将本地配置与实际实例关联，避免将配置快照用作运行声明。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | 保留 PS 的传统分层与 bridge 边界。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | 复用桥接校验、Worker 注册和契约摘要编码。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | 沿用 JDK 25、PS、Contract、Shoubing starter、JUnit 和 Mockito。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | 技术适配器与私有构造的不可变 Route 证据。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | 在现有配置验证之后核对原生三元组、跨身份唯一和完整服务身份。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | 组合现有 AgentDispatchBinding 和固定摘要，无复制模型。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | JDK 集合及现有 OrchestrationRequestDigests。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/8/14 | 当前本地证据路径无该职责。 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | 现有 Duration 的毫秒配置，不签发时间窗。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/14 | 复用已绑定 dispatchEnabled 和现有配置，验证各环境资源一致。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | 用只读技术适配器组合原注册和桥接权威。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | 两份 Java 加三份文档，真实部署、AS 和授权接入独立。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | R1 一处预期反证、G1 有限回归、P1 成品检查和 S1 路径。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | 源码契约完整，I1 的真实部署和历史路由权威仍待独立验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
