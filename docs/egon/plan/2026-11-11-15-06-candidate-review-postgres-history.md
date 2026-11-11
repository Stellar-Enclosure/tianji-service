# T4-01 A3 PostgreSQL 历史初始化修正实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-11-15-06-candidate-review-postgres-history.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:06 Asia/Shanghai |
| Updated | 2026-11-11 15:06 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 一个 Process Server 测试源、三份审查文档 |
| Source Requirement | 主计划 §19 T4-01 A3 PostgreSQL 验收 |
| Baseline Revision | 13b59a7dda2aeb013eff1c101d4f3f9705cba316 |
| Implements Spec | [A3 历史修正](../spec/2026-11-11-15-05-candidate-review-postgres-history.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:05 Asia/Shanghai SHA-256 2ded48cf36f5cc3eb4681fbb1654f65fc435cde93545117f9e2668abcbcbce10 |
| Effective Specs | [本修正](../spec/2026-11-11-15-05-candidate-review-postgres-history.md) |
| Depends On Plans | [A3 PostgreSQL 验收](2026-11-06-08-12-candidate-review-postgres-acceptance.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

修正原 A3 实库用例中的 Flyway 初始化，断言从 V1 至 V10 后追加 V11 的完整 SQL 历史。

## 2. Target Spec and Effective Design

本步骤实现当前 Spec §4、§7 和 §14。原操作者权限及并发验收要求继续由所依赖的计划描述。

## 3. Effective Requirements and Acceptance

| Requirement | Exact Spec source | Observable acceptance | Gate |
| --- | --- | --- | --- |
| REQ-001 | §4/7.2 | 已有 guard 后创建空 schema，V10 后迁移一次到 V11 | C1/I1 |
| REQ-002 | §4/7.3 | 两阶段都有完整 SQL 序列断言 | C1/I1 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先用原实现与实际 verifier 证明标记冲突，再应用单文件补丁，编译并执行有限回归。

### 4.2 Test-first strategy

基线反证为版本 0 被 laterMigrations 的筛选纳入，再因首项须为版本 8 而拒绝。Flyway DbSchemas 字节码显示创建 schema 会写标记。编译与有限回归核对修改后的路径，真实历史由 I1 核实。

### 4.3 Sequential and parallel boundaries

先核对基线和补丁，再运行 C1 和 S1。已有显式操作者 I1 需要独立隔离库。

### 4.4 Commit boundaries

一个只含现有操作者测试修正及对应审查文档的提交。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Handling |
| --- | --- | --- | --- |
| Flyway 空 schema 路径 | Modify §7 | 旧 baseline 调用与 exact verifier | 复用现有测试 owner |
| 历史逐行断言 | Add §7.3 | 已有计数与快照检查 | 复用 JdbcTemplate 查询 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| 操作者夹具 | REQ-001/002 | C1/I1 | 已提交 A3 实库用例 | Step 1 |
| 审查证据 | REQ-001/002 | S1 | 精确补丁和迁移字节 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Obligation | Gate |
| --- | --- | --- | --- | --- |
| Scope | Current A3 operator selector | §7/8 | Modify one Java test source | C1/S1 |
| Persistence | Existing Flyway/JDBC | §7/11 | Reuse ordered immutable SQL | I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 空库保护与迁移 | 原操作者夹具 | EVD-001–003 | 可复用 | 在原 guard 后显式创建 schema | 原测试内修改 | Step 1 MC-REUSE-001 |
| 历史观察 | JdbcTemplate | 原快照查询 | 可复用 | 检查全部版本及类型 | 一个 test helper | Step 1 MC-TEST-001 |

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
| Rule 1 | §6.2 | EVD-001–003 | File 1 | 保留测试类，增加与断言目的相符的 assertSqlHistory。 | C1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–003 | File 1 | 在已有空库和 schema guard 后创建，按安装序断言每一条历史。 | C1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–003 | File 1 | 通过已有 JdbcTemplate 取得字符串序列。 | C1/S1 | Step 1 | N/A |
| Rule 4 | §6.2 | EVD-001–003 | File 1 | 直接调用 JUnit、JdbcTemplate 和 Flyway。 | C1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–003 | File 1 | 复用 Java IntStream 和现有 test 依赖。 | C1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–003 | File 1 | 历史值使用 SQL 列和 Java 字符串。 | C1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | EVD-001–003 | File 1 | Flyway 构建器属于单一显式操作者用例。 | C1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–003 | File 1 | 按现有线性夹具顺序直接核对。 | C1/S1 | Step 1 | N/A |
| Rule 10 | §6.2 | EVD-001–003 | File 1 | 按 installed_rank 检查，无时钟或时间数据变动。 | C1/S1 | Step 1 | N/A |
| Rule 11 | §6.2 | EVD-001–003 | File 1 | 保留原 PostgreSQL 测试存储边界，复用 V1–V11 和现有 JDBC。 | C1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: 只比较 Flyway 版本、类型和 installed_rank，现有到期与事务时序合同继续由原操作者夹具验收。

## 5. Change File Tree

| Operation | Exact path | File | Requirement | Gate |
| --- | --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java | 1 | REQ-001/002 | C1/S1 |
| CREATE | docs/egon/spec/2026-11-11-15-05-candidate-review-postgres-history.md | 2 | REQ-001/002 | C1/S1 |
| CREATE | docs/egon/plan/2026-11-11-15-06-candidate-review-postgres-history.md | 3 | REQ-001/002 | C1/S1 |
| CREATE | docs/egon/evidence/2026-11-11-candidate-review-postgres-history.md | 4 | REQ-001/002 | C1/S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

精确基线 13b59a7dda2aeb013eff1c101d4f3f9705cba316。I1 沿用 A3 已有的独立 PostgreSQL 选择器和隔离库约束。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 修正 A3 PostgreSQL 夹具的 Flyway 历史初始化

- Requirements: REQ-001, REQ-002.
- Dependencies: 已提交的 A3 操作者测试、现有 Flyway 和 ProcessApiSchemaVerifier。
- Baseline state: 原测试主动 baseline 0，严格历史预期从版本 8 开始。
- Observable outcome: 两个阶段的历史完全等于有序的 SQL 版本，原有 V10 行的快照仍相同。
- End state: 精确 Java 修正、审查文档和所属模块验证证据。
- Test-first gate: Required. 基线直接源码与版本化 Flyway 字节码反证。真实 I1 由操作者执行。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files: Files 1–4 below.

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java`

- Purpose: 令空库操作者夹具生成严格的 SQL 历史。
- Symbols: PlatformReleaseCandidateReviewPostgresTest.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: 原 A3 PostgreSQL 操作者用例及审查记录。
- Why now: 修复原用例在真实物理验证之前生成的历史漂移。
- Contract/signature changes: 沿用原操作者入口。
- Input/output and state mapping: 空 schema 到严格 V10/V11 历史或明确断言失败。
- Error and edge behavior: 对额外历史、错序或不匹配类型拒绝。
- Standards impact: MC-SCOPE-001 和 MC-TEST-001。
- Literal rule enforcement: Rule 11 复用现有模块与已提交迁移，其他检查见 §4.8。
- Implementation pseudocode:
```java
requireGuardedFreshLoopbackDatabase();
owner.execute("CREATE SCHEMA " + SCHEMA);
migrateTo("10", createSchemas(false), baselineOnMigrate(false));
assertExactOrderedSqlHistory(10);
migrateTo("11", createSchemas(false), baselineOnMigrate(false));
assertExactOrderedSqlHistory(11);
assertExistingVersionRowsEqualSnapshot();
```
- Verification contribution: C1 和 S1，实库结果见 I1。
- After this file: 核对本路径和对应门禁。

#### File 2 — `CREATE docs/egon/spec/2026-11-11-15-05-candidate-review-postgres-history.md`

- Purpose: 冻结精确历史格式和原夹具适用边界。
- Symbols: 2026-11-11-15-05-candidate-review-postgres-history.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: 原 A3 PostgreSQL 操作者用例及审查记录。
- Why now: 修复原用例在真实物理验证之前生成的历史漂移。
- Contract/signature changes: 沿用原操作者入口。
- Input/output and state mapping: 空 schema 到严格 V10/V11 历史或明确断言失败。
- Error and edge behavior: 对额外历史、错序或不匹配类型拒绝。
- Standards impact: MC-SCOPE-001 和 MC-TEST-001。
- Literal rule enforcement: Rule 11 复用现有模块与已提交迁移，其他检查见 §4.8。
- Implementation pseudocode:
```text
Read the exact ProcessApiSchemaVerifier history query.
Record which marker rows violate the required sequence of SQL migrations.
Trace the correction to both source inspection and operator acceptance.
```
- Verification contribution: C1 和 S1，实库结果见 I1。
- After this file: 核对本路径和对应门禁。

#### File 3 — `CREATE docs/egon/plan/2026-11-11-15-06-candidate-review-postgres-history.md`

- Purpose: 锁定本修正的路径、基线和门禁。
- Symbols: 2026-11-11-15-06-candidate-review-postgres-history.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: 原 A3 PostgreSQL 操作者用例及审查记录。
- Why now: 修复原用例在真实物理验证之前生成的历史漂移。
- Contract/signature changes: 沿用原操作者入口。
- Input/output and state mapping: 空 schema 到严格 V10/V11 历史或明确断言失败。
- Error and edge behavior: 对额外历史、错序或不匹配类型拒绝。
- Standards impact: MC-SCOPE-001 和 MC-TEST-001。
- Literal rule enforcement: Rule 11 复用现有模块与已提交迁移，其他检查见 §4.8。
- Implementation pseudocode:
```text
Resolve the exact Spec digest and approved baseline revision.
List the single Java edit and the corresponding focused gates.
Compare the prior migration SQL and configuration bytes.
```
- Verification contribution: C1 和 S1，实库结果见 I1。
- After this file: 核对本路径和对应门禁。

#### File 4 — `CREATE docs/egon/evidence/2026-11-11-candidate-review-postgres-history.md`

- Purpose: 记录实际检查和独立 I1 状态。
- Symbols: 2026-11-11-candidate-review-postgres-history.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: 原 A3 PostgreSQL 操作者用例及审查记录。
- Why now: 修复原用例在真实物理验证之前生成的历史漂移。
- Contract/signature changes: 沿用原操作者入口。
- Input/output and state mapping: 空 schema 到严格 V10/V11 历史或明确断言失败。
- Error and edge behavior: 对额外历史、错序或不匹配类型拒绝。
- Standards impact: MC-SCOPE-001 和 MC-TEST-001。
- Literal rule enforcement: Rule 11 复用现有模块与已提交迁移，其他检查见 §4.8。
- Implementation pseudocode:
```text
Record the exact old initialization and verifier contradiction.
Record actual owning reactor and bytecode observations.
Keep the expected PostgreSQL history at each version as operator acceptance.
```
- Verification contribution: C1 和 S1，实库结果见 I1。
- After this file: 核对本路径和对应门禁。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 C1/S1.
- Expected result: 27 项现有有限测试通过，操作者路径编译，严格文档和文件范围校验通过。
- Failure returns to: File 1 或对应审查文档。
- Completion criteria: 通过 C1/S1 并提交一个精确范围，保留 I1 待验。
- Rollback: 单独回退本测试修正提交。
- Commit paths: stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java, docs/egon/spec/2026-11-11-15-05-candidate-review-postgres-history.md, docs/egon/plan/2026-11-11-15-06-candidate-review-postgres-history.md, docs/egon/evidence/2026-11-11-candidate-review-postgres-history.md.
- Commit: test(process): correct A3 PostgreSQL fixture history initialization

## 8. Test, Validation, and Quality Gates

| Gate | Exact action | Expected result |
| --- | --- | --- |
| C1 | Java 25 所属模块测试编译、javap、四个现有 A3 有限类 | 编译结果有两个 opt-in、关闭隐式 schema/baseline、两次历史断言，27 项通过 |
| S1 | 严格 Spec/Plan、git apply --check、git diff --check、精确四路径、历史 SQL 核对 | 补丁可应用，范围和历史字节核对通过 |
| I1 | 现有 A3 PostgreSQL 独立选择器 | 原操作者方法实际通过，真实 SQL 历史从 V10 到 V11，后续权限及并发断言通过 |

```sh
a3_history_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
"$a3_history_runner" 'PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseCandidateReviewStoreTest,ProcessApiSchemaVerifierTest'
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-05-candidate-review-postgres-history.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-06-candidate-review-postgres-history.md
git diff --check
```

### Separate operator-only I1

沿用原 A3 PostgreSQL 计划的选择器、三个显式属性和隔离库条件。以实际非跳过报告记录验证结果。

## 9. Migration, Compatibility, Rollout, and Rollback

隔离库夹具调用已存在的 SQL。源码回退单位为本次测试提交。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Files 1–4 | C1/S1/I1 |
| REQ-002 | Step 1 | Files 1–4 | C1/S1/I1 |

## 11. Risks, Blockers, and User Decisions

实际 PostgreSQL I1 由操作者执行，需现有授权新库与临时角色。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | 现有 Process Server 测试类负责库初始化。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | 复用现有 URL guard、Flyway 和 JdbcTemplate。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | 使用现有模块依赖。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | assertSqlHistory 的名称与断言范围一致。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | 比较完整有序 SQL 行，0 和空版本均无法通过。 | None |
| MC-MODEL-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | JDK IntStream 生成精确期望。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-TIME-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-PATTERN-001 | Not applicable | N/A | §7/8 | 本次只有测试库初始化和顺序断言。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | 只修改原操作者测试及对应审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | 已有历史验证器和 A3 有限测试，另设操作者执行证据。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | 编译与静态反证足以验收本次源码，I1 由操作者执行。 | None |

### 12.6 Final verdict

PASS — Ready for user review
