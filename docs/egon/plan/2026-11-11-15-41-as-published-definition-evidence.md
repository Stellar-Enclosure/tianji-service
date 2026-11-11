# T4-01 AS 已发布定义所有者证据实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-41-as-published-definition-evidence.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:41 Asia/Shanghai |
| Updated | 2026-11-11 15:41 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | 一个 AR 内部读取、一个有限测试与三份审查文档 |
| Source Requirement | Master T4-01 |
| Baseline Revision | fb89f4c6d96b1aeee325ddb59937c2a9ef366c4a |
| Implements Spec | [AS 发布证据 Spec](../spec/2026-11-11-15-40-as-published-definition-evidence.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:40 Asia/Shanghai SHA-256 49d203c5bb564f40d065814ba8f0751c7b43d962ef16e143ba650c51a71841e6 |
| Effective Specs | [AS 发布证据 Spec](../spec/2026-11-11-15-40-as-published-definition-evidence.md) |
| Depends On Plans | [A4](./2026-11-11-15-09-platform-release-activation.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [A5 治理 START](./2026-11-11-15-29-governed-process-start.md) |

## 1. Summary

同源 AR 发布账本读取一个精确提交版本，重算已有发布摘要并输出安全证据。范围为单一步骤和有限验证。

## 2. Target Spec and Effective Design

使用头部精确 revision 的 AS 发布证据 Spec。A4 的当前部署证明是需求来源，A5 的 PS 实现为独立相邻功能。本步只覆盖 AgentDB 事实。

## 3. Effective Requirements and Acceptance

| ID | Requirement | Acceptance |
| --- | --- | --- |
| REQ-001 | 精确主库读取 | 同源独立只读事务中按 actor tenant、key 和 version 查询。 |
| REQ-002 | 摘要及嵌入身份 | 重算、比较并安全拒绝任何不符。 |
| REQ-003 | 最小安全投影 | 内部授权先于调用，仅输出 Spec §10。 |
| REQ-004 | 可复核范围 | 精确反证、有限回归、严格结构、31 份 SQL 及精确提交。 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

冻结 HEAD、精确基线 SQL 和候选 SHA。以已有 AR codec 和 JdbcTemplate fixture 写测试，加入生产查询，再运行反证和有限回归。

### 4.2 Test-first strategy

R1 在三份隔离源码副本各移除一个关键条件。严格选择一个方法并检查失败类型和测试计数。

### 4.3 Sequential and parallel boundaries

测试与源码按顺序配对。结构和只读范围检查可并行。只保留仓库内五个精确路径。

### 4.4 Commit boundaries

所有本地检查通过后以单一 AR 提交落地。先核对精确暂存路径及差异。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Existing ledger query and projection | Required §7/10 | EVD-001–003 | Reuse service, data source, repository and codec | Add a local method and immutable projection. |
| Per-version finite fixture | Required §14 | EVD-001–004 | Existing Mockito/JUnit fixture style | Add one test class. |
| Transport and current authority | Separate §18 | EVD-004/005 | Current AS host authorization and future independent owner transport | Outside this Step. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Owner publication query | REQ-001–003 | R1/G1 | 1–2 | Existing ledger, canonical hash and primary read |
| Step evidence | REQ-004 | S1 | 3–5 | Exact candidate and baseline |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | EVD-001/003 的原 owner JDBC 账本和同源事务 | 同一个模块和 DataSource。 |
| Input and DTO | EVD-004 issuer 绑定身份及现有错误代码 | 已授权的内部调用、显式精确键校验、不可变投影。 |
| Data processing | EVD-002 既有 Jackson 摘要 | 复用原算法，无额外格式。 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Tenant-bound owner | `TrustedAgentActor` | EVD-004 | Verified issuer shape, host still owns authorization | Derive tenant from actor | Query guard only | Step 1/G1 |
| Physical read-only primary | `JdbcAgentLedgerRepository#beginPrimaryReadObservation` | EVD-003 | Existing source SQL and clock | Reuse in independent TransactionTemplate | No dependency | Step 1/G1 |
| Definition identity | `JdbcAgentLedgerRepository#definition`, `AgentJsonCodec#definitionHash` | EVD-001/002 | Exact row and canonical content | Read and recompute | One read path | Step 1/R1 |
| Finite assertion | Existing AR JUnit and Mockito | AR current test classpath | Supports exact query and typed rejection tests | Reuse test dependencies | No dependency | Step 1/G1 |

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
| Rule 1 | §6.2 | EVD-001–005 | Files 1–5 | 既有账本上增加查询方法与 PublishedDefinitionEvidenceDTO。 | R1/G1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–005 | Files 1–5 | 非 Bean 入口显式校验精确身份、key、版本及仓储返回；复用既有 DTO 构造校验。 | R1/G1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–005 | Files 1–5 | 新投影仅为不可变 record，无可变 POJO 或 Converter。 | R1/G1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–005 | Files 1–5 | 现有账本构造和装配不变，无新 Spring Bean、注入构造器或限定符。 | R1/G1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–005 | Files 1–5 | 复用 JDK 字符校验及现有摘要和 JSON 工具。 | R1/G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–005 | Files 1–5 | 沿用既有 Jackson canonical 发布摘要，无枚举或序列化合同变更。 | R1/G1/S1 | Step 1 | PASS |
| Rule 7 | §6.2 | EVD-001–005 | Files 1–5 | 无应用配置文件、属性及运行开关变更。 | R1/G1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–005 | Files 1–5 | 沿用本地 Query、现有 Repository 与同源 TransactionTemplate。 | R1/G1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–005 | Files 1–5 | 观察时刻使用 UTC Instant。 | R1/G1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–005 | Files 1–5 | 按主计划 §18 AR 技术账本所有权复用既有 JDBC，零表、迁移、索引、Event 或模块改动。 | R1/G1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | java.time.Instant | Not persisted | Internal | Not applicable: absolute UTC | Not applicable: no display | No time windows or calendar arithmetic | Owner PostgreSQL clock_timestamp, database precision | Additive observation of existing immutable row | Step 1 TEST-002 fixture and G1 |
| TIME-002 | Read transaction and statement timeout | Duration | Spring seconds and PostgreSQL statement_timeout | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | 2 seconds for transaction configuration and SQL limit, no end-to-end guarantee | Spring transaction budget and PostgreSQL timeout | Existing read-observation policy | Step 1 TEST-002 fixture and G1 |

## 5. Change File Tree

| Operation | Exact path | Responsibility |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java` | 精确 owner 查询、受限 SQL、摘要、内嵌键及版本的有限反证。 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java` | 既有账本扩展精确发布版本查询与不可变证据投影。 |
| CREATE | `docs/egon/spec/2026-11-11-15-40-as-published-definition-evidence.md` | 冻结所有者证明、时限与调用方身份边界。 |
| CREATE | `docs/egon/plan/2026-11-11-15-41-as-published-definition-evidence.md` | 冻结路径、摘要、有限门禁和提交范围。 |
| CREATE | `docs/egon/evidence/2026-11-11-as-published-definition-evidence.md` | 记录实际执行的本地证据和待验收事项。 |

## 6. Prerequisites, Constraints, and Plan Clarifications

工作目录为 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。必须以精确基线 `fb89f4c6d96b1aeee325ddb59937c2a9ef366c4a` 校验预期。使用 JDK 25 和现有离线 classpath，不连接实际 PostgreSQL。原 `ContractChecks.required` 对发布入参使用 trim，当前查询要求已规范化的精确 key，拒绝首尾空白、控制字符、无效 UTF-8 及非正版本，避免隐式归一造成歧义。内部输出仅供按租户和版本获授权的调用者使用。

| Item | SHA-256 |
| --- | --- |
| Baseline `AgentLedgerService.java` | `d6cbc6cce3bb35dc53e6ceb9512d2a8eb539358a52913a3707168a5f80ad8ac5` |
| Candidate `AgentLedgerService.java` | `2c95a9c05631723247578d8b8462191d4f215943f2542ec29c95b80944dadbd8` |
| Candidate `AgentPublishedDefinitionEvidenceTest.java` | `0153611166816f01c349887062274c99b71b1da14f5c887434bc3dd507ae50b9` |
| Spec | `49d203c5bb564f40d065814ba8f0751c7b43d962ef16e143ba650c51a71841e6` |
| Evidence | `8d7544871bbe1dd15d34567b7a4408244f11d56a24ab30623722db33bf53bbb3` |
| Sorted 31-path SQL manifest | `0a56bb4317086f7078a6f31a1f8ffbb3aae71797c94a687c8d6c7586a515e9e0` |

完整五路径 SHA 清单与 Plan 自身摘要在冻结的外置 `candidate.sha256`，避免文档递归散列。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Read and verify the exact committed AS publication

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing AR V1 definition and version tables, owner data source, canonical digest and observation.
- Baseline state: Published content exists in the owner ledger. Current run lookup requires a previously created run.
- Observable outcome: Authorized owner-host query receives an immutable verified exact-version projection or a safe failure.
- End state: One additive local method and immutable record with finite proof. No active host consumer.
- Test-first gate: Required. R1 executes the exact method selector against three independent mutations.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java`

- Purpose: 精确 owner 查询、受限 SQL、摘要、内嵌键及版本的有限反证。
- Symbols: AgentPublishedDefinitionEvidenceTest and JDBC fixture
- Repository evidence: 原账本摘要和查询在已有 AR 测试环境可见。
- Dependencies and consumers: EVD-001–005 and Spec §7/8/10.
- Why now: 针对拟扩展查询构造最小只读 mock JDBC。
- Contract/signature changes: 添加六个定向用例。
- Input/output and state mapping: 通过 SELECT mapper 回传已发布样本和精确 bind。
- Error and edge behavior: 缺失、未认证、存储漂移、数据源异常与嵌套事务均须拒绝。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
invoke exact owner query against fixture rows
check primary, SQL guard and tenant/key/version tuple
mutate digest, JSON key and JSON version independently
assert typed failure and no sensitive content
```

- Verification contribution: R1/G1/S1.
- After this file: 精确 owner 查询、受限 SQL、摘要、内嵌键及版本的有限反证。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: 既有账本扩展精确发布版本查询与不可变证据投影。
- Symbols: AgentLedgerService#publishedDefinitionEvidence and PublishedDefinitionEvidenceDTO
- Repository evidence: 仓储已按 owner tuple 读取，既有 codec 生成发布摘要。
- Dependencies and consumers: EVD-001–005 and Spec §7/8/10.
- Why now: 复用唯一拥有者及现有事务模板，避免新增存储或 API。
- Contract/signature changes: 新增本地 Query 与不可变投影。
- Input/output and state mapping: 接收经宿主授权的 actor 和精确 key/version。只返回 Spec §10 标量。
- Error and edge behavior: 保持缺失资源错误。数据错误及数据源失败转为脱敏的依赖不可用。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
reject missing or LEGACY actor, inexact key or invalid version
reject interrupted thread or existing transaction
begin independent primary read-only observation and exact owner lookup
check canonical hash and embedded identity
return immutable verified projection
```

- Verification contribution: R1/G1/S1.
- After this file: 既有账本扩展精确发布版本查询与不可变证据投影。

#### File 3 — `CREATE docs/egon/spec/2026-11-11-15-40-as-published-definition-evidence.md`

- Purpose: 冻结所有者证明、时限与调用方身份边界。
- Symbols: Specification
- Repository evidence: A4 权威契约区分 AS 发布身份、部署制品和路由。
- Dependencies and consumers: EVD-001–005 and Spec §7/8/10.
- Why now: 明确当前证据与未来完整 CurrentDeploymentEvidence 的关系。
- Contract/signature changes: 本地查询合同与验证矩阵。
- Input/output and state mapping: 以准确的代码所有权、约束、错误和 DTO 字段描述实现。
- Error and edge behavior: 显式记录 PostgreSQL、运行时配置和授权接入待验收。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
trace tenant and committed version requirements to the existing repository and tests
record host authentication and authorization as an explicit upstream precondition
state SQL and transaction budgets and pending end-to-end bounds
```

- Verification contribution: R1/G1/S1.
- After this file: 冻结所有者证明、时限与调用方身份边界。

#### File 4 — `CREATE docs/egon/plan/2026-11-11-15-41-as-published-definition-evidence.md`

- Purpose: 冻结路径、摘要、有限门禁和提交范围。
- Symbols: Implementation Plan
- Repository evidence: 已存在两处明确的同源复用能力及有限 fixture。
- Dependencies and consumers: EVD-001–005 and Spec §7/8/10.
- Why now: 审查与执行都需要固定范围和回滚。
- Contract/signature changes: 精确记录 Step、路径、选择器及哈希。
- Input/output and state mapping: 冻结实际基线与实现路径。
- Error and edge behavior: 任何范围、哈希或用例漂移阻断当前步骤。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
pin the repository revision and source/test SHA-256 before applying the reviewed change
select the exact RED test and verify the three independent assertion failures
verify the finite GREEN suite and exact staged files before committing
```

- Verification contribution: R1/G1/S1.
- After this file: 冻结路径、摘要、有限门禁和提交范围。

#### File 5 — `CREATE docs/egon/evidence/2026-11-11-as-published-definition-evidence.md`

- Purpose: 记录实际执行的本地证据和待验收事项。
- Symbols: Evidence
- Repository evidence: 隔离 JDK 25 测试可复核 exact selector，生产权限未接入。
- Dependencies and consumers: EVD-001–005 and Spec §7/8/10.
- Why now: 交付记录需要区分有限测试与真实数据库。
- Contract/signature changes: 记录实际有限测试和下一验收边界。
- Input/output and state mapping: 只记有效的基线、代码行为及运行命令。
- Error and edge behavior: 真实主库、身份和模型证明由后续步骤验证。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
record the actual compilation and the exact focused and regression test totals
record the specific typed RED failure for each omitted guard
list real PostgreSQL, current caller authorization and full deployment evidence as later acceptance
```

- Verification contribution: R1/G1/S1.
- After this file: 记录实际执行的本地证据和待验收事项。


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/S1.
- Expected result: R1 各为预期类型失败，G1 34 项全通过，S1 结构、路径和 SHA 一致。
- Failure returns to: 修复对应身份、查询、内容摘要或证据门禁。
- Completion criteria: Step 的有限门禁通过并记录仍需实际主库和 IAM 的验收。
- Rollback: 撤销单一提交，无迁移回退。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentPublishedDefinitionEvidenceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`, `docs/egon/spec/2026-11-11-15-40-as-published-definition-evidence.md`, `docs/egon/plan/2026-11-11-15-41-as-published-definition-evidence.md`, `docs/egon/evidence/2026-11-11-as-published-definition-evidence.md`.
- Commit: feat(agent): verify exact published definition evidence.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | `AgentPublishedDefinitionEvidenceTest#refuses_mismatched_digest_and_embedded_identity_independently` | 摘要、key 和 version 三个独立变体各发现并运行 1 项，均报告缺失预期 `AgentLedgerException` 的 JUnit 断言。 |
| G1 | `AgentPublishedDefinitionEvidenceTest`, `AgentJsonCodecTest`, `AgentRunCorrelationLookupTest`, `JTokkitChatModelBudgetEstimatorTest` | 34 tests found and successful, zero failed or skipped. 新增类 6 项。 |
| S1 | strict Spec and Plan, exact paths and candidate SHA | 两份结构验证通过，31 份既有 SQL 逐字节一致，仓库差异只含五个批准路径。 |
| I1 | Separate future acceptance | 实际 AgentDB 主库只读、权限、端到端限时及完整权威接入由后续验收，本步不执行。 |

```sh
as_pub_dir=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/as-published-definition-evidence
test "$(git rev-parse HEAD)" = fb89f4c6d96b1aeee325ddb59937c2a9ef366c4a
"$as_pub_dir/run-scratch.sh" \
  top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentJsonCodecTest \
  top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRunCorrelationLookupTest \
  top.egon.cola.platform.tianji.agent.infrastructure.adk.JTokkitChatModelBudgetEstimatorTest
"$as_pub_dir/run-negative.sh"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-40-as-published-definition-evidence.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-41-as-published-definition-evidence.md
shasum -a 256 -c "$as_pub_dir/candidate.sha256"
shasum -a 256 -c "$as_pub_dir/sql-baseline.sha256"
git diff --cached --name-only | LC_ALL=C sort | diff - "$as_pub_dir/expected-paths"
git diff --check
git diff --cached --check
```

G1 的编译输出独立于共享 Maven，R1 使用独立临时输出覆盖每个变体。S1 在候选应用后检查实际工作树及暂存集。

## 9. Migration, Compatibility, Rollout, and Rollback

原 31 份 SQL 不变。本地入口由宿主后续显式装配，当前无 HTTP、客户端或部署开启动作。回滚直接撤销本步骤。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Files 1–2 | G1 |
| REQ-002 | Step 1 | Files 1–2 | R1/G1 |
| REQ-003 | Step 1 | Files 1–3 | G1/S1 |
| REQ-004 | Step 1 | Files 1–5 | R1/G1/S1 |

## 11. Risks, Blockers, and User Decisions

当前 2 秒事务与 SQL 限值没有覆盖可变的连接获取和事务清理，接入前需另验实际整体时限。运行时模型与部署证明及 actor 的授权身份来源需独立接入。本步交付可供审查的 owner-side source。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | 仅扩展既有 AR 所有者账本。同一事务管理器和主数据源。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用既有仓储、只读主库观察与发布摘要。 | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | AR 现有 Java、Spring JDBC、Jackson、JUnit、Mockito 足够。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 查询行为在现有服务上，投影为不可变 PublishedDefinitionEvidenceDTO。 | None |
| MC-VALID-001 | Applicable | PASS | §5/7/9 | 在无代理本地入口拒绝无效身份、非精确键或版本以及已有事务。在仓储返回后校验摘要及内嵌身份。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 不可变结果只含安全的标量和 UTC Instant。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §8/10 | 已有账本上直接投影少量标量，无新层间 Converter 或业务实体映射。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/15 | 无生产日志或新的业务日志 Bean。既有异常输出只暴露安全错误。 | None |
| MC-BEAN-001 | Not applicable | N/A | §6/7 | 现有账本构造和装配不变，无新 Spring Bean、注入构造器或限定符。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用 JDK 字符校验和既有摘要实现。 | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | 复用既有 Jackson 持久 JSON 与 canonical definitionHash。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | 沿用主库 clock_timestamp，转换为 UTC Instant。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §6/16 | 无应用配置文件、属性及运行开关变更。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/13 | 同源 TransactionTemplate 复用现有只读 Query 与 Repository。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 限定一个 AR 生产类、一个有限测试和三份评审文档。保留 31 份 SQL。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | R1 精确三种反证、G1 有限回归、S1 结构和 SHA 检查。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §14/18 | 本步为未装配的内部读，真实 PostgreSQL、调用者鉴权和完整激活证据由后续独立验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
