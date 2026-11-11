# T4-01 受保护 Process START 试点实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 16:13 Asia/Shanghai |
| Updated | 2026-11-11 16:13 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Four PS Java sources, two PS tests and three review documents |
| Source Requirement | Master §19 T4-01 |
| Baseline Revision | 99740bb7c817979e396e8a34fd1054d591ea4486 |
| Implements Spec | [受保护 START](../spec/2026-11-11-16-12-protected-process-start-pilots.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 16:12 Asia/Shanghai SHA-256 deada404328aac47d5c3daba56373b2747e4adeb714d355d83db81fd3feb9906 |
| Effective Specs | [受保护 START](../spec/2026-11-11-16-12-protected-process-start-pilots.md) |
| Depends On Plans | [A5 治理 START](./2026-11-11-15-29-governed-process-start.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在既有受保护 POST 所经 service 强制执行精确试点。保留原显式版本字段、摘要及回执重放。

## 2. Target Spec and Effective Design

本计划复用已持久 A5 pointer 和原权限。上方精确 SHA-256 的 Spec 规定 REQ-001–004 和 TEST-001/002。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/8/16 | 空配置旧构造器，最多 256 个精确试点，唯一实际 authority。 |
| REQ-002 | §7/9 | 匹配试点前置检查和 A5 原事务，不发生普通回退。 |
| REQ-003 | §7/9/10/11 | 原 digest，精确 APPLIED、历史 channel 和 V13 重放。 |
| REQ-004 | §8/14/18 | 六份 Java、三文档，有限门和真实运行边界。 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

固定 Service 基线，先明确纯配置和精确查找，再接入原写入口和真实 servlet 工厂。有限测试与确定反证通过后提交一笔。

### 4.2 Test-first strategy

以 missing Worker 旧路径返回成功作为反证，修改 scratch 仅跳过 pilot dispatch。新测试在反证中明确缺少预期的 AccessDeniedException，在真实候选中通过。

### 4.3 Sequential and parallel boundaries

本 Step 内四份生产 Java 后执行两份有限测试。T4-01 PostgreSQL 操作者测试可以独立交付，本 Step 无此依赖。

### 4.4 Commit boundaries

一个精确九路径的 Service 提交，Components、Agent Server、合同、SQL、YAML、POM、OpenAPI 和 PaC 保持原版本。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Protected pilot | Required §7/9 | EVD-001/004 | 原 service | 精确分流在唯一写入口 |
| Historical replay | Required §7/10 | EVD-002/003 | 复用既有 receipt 和 V13 | 锁后校验且原许可 |
| Current authority | Required §7/18 | EVD-003/006 | 现有 port | 显式唯一，实际实施待宿主 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Config and guarded assembly | REQ-001 | G1 | 1/4/6 | Existing servlet factory |
| Start and historical replay | REQ-002/003 | R1/G1 | 2/3/5 | Existing A5 |
| Documentation | REQ-004 | S1 | 7–9 | G0/G1/G2 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001/004 | PS 内部执行 service |
| Persistence | EVD-002/003 | 原 A5、native Flowable 与同源 JDBC |
| Time | EVD-003 | 原数据库时钟及事务时限 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Normal protected START | ProcessController and TianjiProcessAccess | EVD-001 | Fit | Reuse | 原入口 | Step 1 G1/G2 |
| Release selection and receipts | ProcessExecutionService and GovernedProcessStartStore | EVD-002/003 | Fit | Extend | 精确 scope、原 release 前提和原回放 | Step 1 R1/G1 |
| Host selection | ProcessSecurityConfiguration | EVD-004 | Fit | Extend | 现有 factory 和启动 guard | Step 1 G1 |
| Current source | GovernedStartAuthority and A6 | EVD-003/006 | Runtime gap | Reuse port | 实际宿主独立接入 | Step 1 G1/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–007 | Files 1–6 | Pilot 值对象和现有名称 | R1/G1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–007 | Files 1–6 | 输入、授权、owner 与锁后证据 | R1/G1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–007 | Files 1–6 | 不可变 Pilot 和原 DTO | R1/G1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–007 | Files 1–6 | 原工厂和显式单一 authority | R1/G1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–007 | Files 1–6 | 现有 JDK、Spring 和 JUnit | R1/G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–007 | Files 1–6 | 现有 API JSON | R1/G1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–007 | Files 1–6 | 默认空配置与外部 opt-in | R1/G1/S1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–007 | Files 1–6 | 原 service、事务和 authority | R1/G1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–007 | Files 1–6 | 原数据库时间和 proof expiry | R1/G1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–007 | Files 1–6 | 现有 schema 和 JDBC | R1/G1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | A5 grants and Worker proofs | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal | A5 既有 30 s 窗口，事务回放返回前再验 | PostgreSQL clock_timestamp | Original A5 contract | Step 1 TEST-002 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedStartPilotProperties.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md` |
| CREATE | `docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md` |
| CREATE | `docs/egon/evidence/2026-11-11-protected-process-start-pilots.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

固定 Service 99740bb7c817979e396e8a34fd1054d591ea4486，现有 JDK 25。保留 31 份已提交 SQL，使用当前 PS 独立有限测试。Components 与 AS 无此 Step 修改。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Enforce a configured pilot at protected Process START

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: 原 Process START 和 A5 产品源码。
- Baseline state: A5 可显式内部使用，正常 servlet 工厂创建旧 service。
- Observable outcome: 指定试点强制治理，原历史精确重放，其他请求继续现有路径。
- End state: 六份 Java、三文档，交付默认空试点。
- Test-first gate: Required. R1 缺少 Worker 时绕过变异必须使确切断言失败。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedStartPilotProperties.java`

- Purpose: 校验并存储试点。
- Symbols: GovernedStartPilotProperties#channelFor.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-001。
- Contract/signature changes: 本切片 校验并存储试点。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
copy immutable pilot list;
require bounded count and unique tenant/definition and tenant/channel;
lookup only exact tenant and definition.
```

- Verification contribution: G1.
- After this file: 保留 REQ-001 对应的有限与独立运行证据。


#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java`

- Purpose: 读取精确原收据与来源。
- Symbols: GovernedProcessStartStore#hasReceipt/#find.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-003。
- Contract/signature changes: 本切片 读取精确原收据与来源。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
select only exact owner START key as hint;
read V13 together with its pinned activation;
reject provenance with no valid activation.
```

- Verification contribution: G1.
- After this file: 保留 REQ-003 对应的有限与独立运行证据。


#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`

- Purpose: 强制正常入口治理及历史重放。
- Symbols: ProcessExecutionService#start/#executeGoverned.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-002/003。
- Contract/signature changes: 本切片 强制正常入口治理及历史重放。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
select pilot using verified tenant and requested definition;
resolve historical channel for owned provenance;
authorize exact channel and command;
inside shared tenant transaction recheck prior source or current pointer;
verify explicit release and preserve old digest before native and V13 commit;
permit only exact existing APPLIED old receipt replay without persistent writes.
```

- Verification contribution: R1/G1.
- After this file: 保留 REQ-002/003 对应的有限与独立运行证据。


#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: 装配唯一实际执行服务和当前权威。
- Symbols: ProcessSecurityConfiguration#processExecutionService/#governedStartPilotGuard.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-001。
- Contract/signature changes: 本切片 装配唯一实际执行服务和当前权威。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
empty scope uses the original constructor;
configured scope needs exactly one explicit authority;
verify that the sole service uses the same authority instance and exact bound pilot list.
```

- Verification contribution: G1/G2.
- After this file: 保留 REQ-001 对应的有限与独立运行证据。


#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java`

- Purpose: 验证服务正常调用和全部回放边界。
- Symbols: GovernedProcessStartServiceTest.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-002/003。
- Contract/signature changes: 本切片 验证服务正常调用和全部回放边界。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
exercise actual service.start and original digest;
prove no writes on mismatch or missing proof;
replay exact ordinary or governed with old channel and catch source drift.
```

- Verification contribution: R1/G1.
- After this file: 保留 REQ-002/003 对应的有限与独立运行证据。


#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`

- Purpose: 验证绑定、缺权威和覆盖 bean。
- Symbols: ProcessApiConfigurationTest.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-001。
- Contract/signature changes: 本切片 验证绑定、缺权威和覆盖 bean。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
bind existing mock servlet host with empty and configured properties;
assert exact authority and service guard;
verify duplicate tenant/definition, tenant/channel and list bound.
```

- Verification contribution: G1.
- After this file: 保留 REQ-001 对应的有限与独立运行证据。


#### File 7 — `CREATE docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md`

- Purpose: 固定需求与运营范围。
- Symbols: REQ-001–004.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-004。
- Contract/signature changes: 本切片 固定需求与运营范围。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
capture exact source and current boundaries;
map each requirement to finite and deferred evidence;
review preserved API and SQL.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的有限与独立运行证据。


#### File 8 — `CREATE docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md`

- Purpose: 固定按文件执行与验收。
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-004。
- Contract/signature changes: 本切片 固定按文件执行与验收。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
pin exact baseline and affected paths;
run the typed mutation, finite and artifact gates;
require unchanged SQL and external contracts;
record independently scoped operator prerequisites.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的有限与独立运行证据。


#### File 9 — `CREATE docs/egon/evidence/2026-11-11-protected-process-start-pilots.md`

- Purpose: 记录有限结果及运行边界。
- Symbols: Finite evidence.
- Repository evidence: Spec EVD-001–007.
- Dependencies and consumers: 原 PS 入口、A5 owner 和现有 JDK/JDBC。
- Why now: 落实 REQ-004。
- Contract/signature changes: 本切片 记录有限结果及运行边界。
- Input/output and state mapping: 保留原受保护入口合同和原子事务。
- Error and edge behavior: 冲突与不可用应拒绝，不进入备用写入。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
record deterministic typed failure and passing source tests;
attach compiled scope and owning report;
leave actual activation and independent authorities pending.
```

- Verification contribution: S1.
- After this file: 保留 REQ-004 对应的有限与独立运行证据。


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1. I1 独立。
- Expected result: 确定一项反证，53 项有限通过，实际 PS 包和严格九路径。
- Failure returns to: Files 1–6 检查产品与断言，Files 7–9 检查范围。
- Completion criteria: 有限与包验证、31 份原迁移，独立运行仍需证据。
- Rollback: Revert this source-only commit before enabling pilots. 已启用环境须独立计划保留对受控试点的拒绝路径。
- Commit paths: `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedStartPilotProperties.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md`, `docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md`, `docs/egon/evidence/2026-11-11-protected-process-start-pilots.md`.
- Commit: feat(process): enforce governed starts for configured pilots.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | HEAD and six Java paths match frozen baseline. |
| R1 | Isolated Java 25 bypass mutation | Missing Worker test fails once with expected AccessDeniedException never thrown. |
| G1 | Owning source reactor and isolated finite tests | 53 pass, zero failed or skipped. |
| G2 | Actual packaged Process Server | New property and authority guard classes present; original servlet signature and packaged YAML, SQL and PaC verified. |
| S1 | Strict docs and scoped diff | Nine exact paths; original SQL, dependencies and contracts byte-identical. |
| I1 | Independently scoped operator | Current caller/Worker authority, all serving replicas and actual protected HTTP/PG/Flowable. Outside Step completion. |

```sh
pilot_base=99740bb7c817979e396e8a34fd1054d591ea4486
pilot_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
pilot_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/protected-start-pilots.4Nqnbqpd
test "$(git rev-parse --verify HEAD)" = "$pilot_base"
"$pilot_scratch/check-finite.sh"
"$pilot_runner" 'GovernedProcessStartServiceTest,ProcessApiConfigurationTest,ProcessControllerTest,ProcessStartReceiptControllerTest,ProcessRequestDigestCompatibilityTest,TianjiProcessAccessTest' clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-16-12-protected-process-start-pilots.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-16-13-protected-process-start-pilots.md
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml '*.yaml' '*.yml'
git diff --check
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

操作者须先提供 independently verified current caller/Worker authority，并确认所有可接收原 START 路由的副本均加载相同试点列表。检查启用前旧副本已停止接收试点流量，同时保留旧历史 Worker 路由及原 channel 许可。实际运行通过 HTTP 和 ProcessDB 观察新启动、版本冲突、原回执重放及跨副本一致。该阶段使用额外批准的隔离环境。

## 9. Migration, Compatibility, Rollout, and Rollback

不改迁移、API 或默认配置。原构造器继续工作。启用后的新命令始终受控，重新选择当前版本通过已有 A4 回滚。通过同一 owner 的原历史保持重放。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1/4/6 | G1/G2 |
| REQ-002 | Step 1 | 2/3/5 | R1/G1 |
| REQ-003 | Step 1 | 2/3/5 | G1 |
| REQ-004 | Step 1 | 1–9 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

生产 caller/Worker 权威、跨实例一致和原生 HTTP/PostgreSQL 验收待独立操作。现有 Local Worker 证据只承担本地可复算输入。本 Step 无新增权限，原环境默认关闭。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 原 PS 内部 service | G0 |
| MC-REUSE-001 | Applicable | PASS | EVD-001–007 | 权限、A5、digest 和普通回执 | G1 |
| MC-DEP-001 | Applicable | PASS | §6 | 既有依赖 | S1 |
| MC-NAME-001 | Applicable | PASS | §8/9 | 精确 Pilot 和既有 START | G1 |
| MC-VALID-001 | Applicable | PASS | §7/9 | 角色、scope、发布前提及全 owner | G1 |
| MC-MODEL-001 | Applicable | PASS | §10 | 原不可变合同 | G1 |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无业务 DTO 转换 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新业务日志 | None |
| MC-BEAN-001 | Applicable | PASS | §7.2 | 拒绝 legacy 或 ambiguous override | G1 |
| MC-UTIL-001 | Applicable | PASS | §6 | 现有 JDK、JDBC 与测试依赖 | G1 |
| MC-JSON-001 | Not applicable | N/A | §9 | 现有 wire | S1 |
| MC-TIME-001 | Applicable | PASS | §6.3 | 复用 A5 clock | G1 |
| MC-CONFIG-001 | Applicable | PASS | §7.2 | 空试点，显式 authority | G1 |
| MC-PATTERN-001 | Applicable | PASS | §13 | 单一 native 入口 | R1 |
| MC-SCOPE-001 | Applicable | PASS | §8/11 | 9 路径 | S1 |
| MC-TEST-001 | Applicable | PASS | §14 | 确定反证和有限回归 | R1/G1 |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 真实运行独立 | I1 |

### 12.6 Final verdict

PASS — Ready for user review
