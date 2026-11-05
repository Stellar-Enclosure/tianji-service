# T4-02 首切片实施计划：Agent 模型用量账本

| Field | Value |
| --- | --- |
| Document | `2026-11-05-16-50-agent-model-usage-implementation.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 16:50 Asia/Shanghai` |
| Updated | `2026-11-05 17:10 Asia/Shanghai` |
| Owner | `天机决策负责人` |
| Repository | `tianji-service` |
| Scope | `AR 现有预算与账本、独立 AgentDB V15、AS 私有诊断与 default-off` |
| Source Requirement | `主计划 §19 T4-02、§17 S12 及首切片 Spec §4` |
| Baseline Revision | `tianji-service 2f10711b152bbce7000ba7fb31d6f931e4f52927；Components 87af09fea9a9d58e7127b009d76f60438c79e569；tianji-web f558e02c8d07da2da758c5273a5eb531ca251018` |
| Implements Spec | [T4-02 首切片](../spec/2026-11-05-16-08-agent-model-usage-ledger.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 17:10 Asia/Shanghai；SHA-256 50e5874df03053619a0241021c52956d2b3b4e3f043bd559cbb8ab9d5c524370` |
| Effective Specs | [T4-02 首切片](../spec/2026-11-05-16-08-agent-model-usage-ledger.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

先固定可验证费率和值契约，再交付 V14 兼容的源内 owner reader。随后加入一条 immutable V15 和独立 readiness、原预算事务内写入、最后接通 AS 的默认关闭开关与既有授权。五个提交各自可独立有限验证。真实 PostgreSQL 及 T3-12 E/S12 留在单独受权门禁。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [T4-02 首切片](../spec/2026-11-05-16-08-agent-model-usage-ledger.md)
- Status: Review
- Revision: 2026-11-05 17:10 Asia/Shanghai；Spec SHA 锁见头部。
- Approval evidence: Root 审查锁定 DEC-005 的既有 AR JDBC/外部 Flyway、reader-first、default-off 精确技术范围。当前 Plan 为 Review，源码 gate 按 Root 明确锁定后执行。用户批准的运行边界另行处理。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [T4-02 首切片](../spec/2026-11-05-16-08-agent-model-usage-ledger.md) | Review 17:01 | §§4–16 | 唯一源实现设计 |
| Context | [主计划](../../../../docs/plans/agent-framework-upgrade-plan.md) | 当前 T4-02/S12 | §§17/19 | 时序和未来 S12；不扩展本切片 |

### 2.3 Superseded or excluded content

总候选中的晚到补写、origin、增量、PS 跨源、Span Link 和指标在本切片外。无新增公开 endpoint、PaC 资源、依赖或业务 Event。T4-01A1 的 PS 文件不进入本变更。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/7/11 | 一次确证 MODEL 一个终值 | 只首写，重复同值无写，异值冲突 | AR 现有 reserve/settle |
| REQ-002 | §4/7/10 | 解释价格、计数和未知 | 冻结价八位、null≠0、预算不变 | AR estimator/账本 |
| REQ-003 | §4/9/11 | exact owner 不依赖采样 | 源内 GET/LIST scope、bounded run/correlation | AR query / AS façade |
| REQ-004 | §4/11/16 | additive V15 与 default-off | V14 off，on 缺 V15 失败，既有 API 原样 | AR readiness / AS config |
| REQ-005 | §4/14/15 | 分层证据与隐私 | 实际有限报告，PG/E 单列，安全 payload | 所有验证 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Step 1 定义现有 estimator 提供的可选冻结价格。Step 2 在无 V15 字节时建立关闭安全、owner 范围和有限读取。Step 3 才提交一条不可变 V15 及校验。Step 4 接原 MODEL active settlement 的写路径，standalone 仍 off。Step 5 接既有同源 storage 工厂与 AS 私有授权 façade，默认 off。

### 4.2 Test-first strategy

每步先真实可编译反射/资源 RED 或已有行为断言 RED，再在签名 A 后执行有限 typed RED，接着以最小实现 GREEN。红阶段须实际测试断言失败，编译、依赖、fixture、环境错误或 skipped 不计入。I 测试只有受权独立 PostgreSQL 时运行。无 schema 时用有限 mock/readiness fixtures 明确证明，不能代称 PG。

### 4.3 Sequential and parallel boundaries

严格 Step 1→2→3→4→5。Step 2 创建纯有限 StoreTest；Step 3 单独新增 compile-only PostgresTest，Step 4 扩展二者。Step 1 的 capture tests 在 Step 4 扩充。A1 与这些目标文件无交叉，但须等待父代理锁定源码和共同 reactor 状态后开始源码。

### 4.4 Commit boundaries

五个 repository-local 聚焦提交。同一 Java 文件若在两步出现，后一处仅补下一阶段直接义务。Step 1 接收两份精确规范文档，Steps 1–4 实际门禁证据在仓库外；Step 5 提交一份源范围交付证据。无生产文档、POM 或工具路径。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| 观测事实 | §7/11 必须 | V2 状态兼作额度，UNKNOWN 可含实测 | 不写回 V2 语义，现有事务内 1:1 | 一表/每 MODEL 一行 | Implement |
| 价格 | §7.3.1 必须 | JTokkit 原 bound model/profile | reuse estimator + 可选小值 | 无新网络 | Implement |
| 私有 owner Query | §9 | GET/LIST 已真实 READ，无明细 | reuse current authority + same-source query | 无公网 API | Implement |
| 独立 readiness | §16 | 基础 V1–V6 与现有 feature 各自验证 | V15 专用、保持 baseline | 仅 opt-in 检查 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| frozen tariff | REQ-002/005 | estimator finite RED | 当前 JTokkit binding | optional immutable snapshot | writer | Step 1 |
| exact owner read | REQ-003/004/005 | reader finite RED | 旧 ledger/schema，stub V15 select | default-off compatibility | V15/AS | Step 2 |
| V15 + guard | REQ-001/002/004 | resource absence / verifier RED | Step 2 read path | immutable schema/verify | writes/host | Step 3 |
| model transaction | REQ-001/002/005 | plugin/store finite RED | Steps 1–3 | active final only | AS host | Step 4 |
| standalone | REQ-003/004/005 | config/authorization finite RED | all AR seams | off default and private read | final regressions | Step 5 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AR AgentLedgerService/Budget/JdbcAgentLedgerRepository、AS storage | 原技术纵切 | 无新业务层、ORM、mapper 或事务管理器 | Step 1–5；MC-ARCH-001 |
| Reuse | JTokkit, AgentBudgetSettlement, C checks | 原 API 和输入边界复用 | 对现有类加 scope-local 值/方法 | Step 1–5；MC-REUSE-001 |
| Validation/model | 现有 exact owner 和 immutable records | §6.2 | 新值严格构造、同源显式 JDBC 映射 | Step 1–5；MC-VALID-001 |
| Bean/config/logging | AS named storage，当前 READ | §9/16 | 显式命名，旧 controller 不动，无 payload 日志 | Step 5；MC-BEAN-001 |
| Pattern | estimator 已是 Strategy | §13 | 继续 current bound 选择，CAS 同源 | Step 1/4；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| tariff | 原 JTokkit estimator，BigDecimal | require/actualUsage/cost | 缺只读冻结值 | 默认空扩展，复用 bound | None | Step 1；MC-DEP-001 |
| owner and storage | Spring JDBC、AS existing guard | get/list/terminalReference、verifyLinked | 缺条件能力 | 既有 owner/transaction/Verifiers | None | Step 2/3；MC-REUSE-001 |
| command and private query | C DTO、AS IAM | beforeModelCallback、TianjiAgentAccess.require | 缺独立观测 | 扩同源路径，保留旧 wire | None | Step 4/5；MC-JSON-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/8/10 | AR existing DTO and records | §7 exact order | semantic VO/DTO/Query | compile/static | Step 1–5 | PASS |
| Rule 2 | §6.2/9 | strict C and source authority | §7 Step 2/4/5 | validate C/scope before reads, no supplied auth | negative tests | Step 2/4/5 | PASS |
| Rule 3 | §6.2/10 | immutable values and JDBC | §7 Step 1/2/4 | explicit exact immutable projection under scoped profile | construction/PG | Step 1/2/4 | PASS |
| Rule 4 | §6.2/16 | current named factory and technical logging | §7 Step 5 | same DataSource/manager and no sensitive logs | context test | Step 5 | PASS |
| Rule 5 | §6.2 | existing JDK/Spring | §7 all | no new utility/dependency | POM/source diff | Step 1–5 | PASS |
| Rule 6 | §6.2/9 | current C wire | §7 Step 2/5 | named internal states only, existing Jackson unchanged | SDK/API regression | Step 2/5 | PASS |
| Rule 7 | §6.2/16 | sole AS application.yml | §7 Step 5 | explicit false and empty list | configuration tests | Step 5 | PASS |
| Rule 9 | §6.2/13 | current bound Strategy | §7 Step 1/4 | preserve Strategy, no duplication | profile/callback tests | Step 1/4 | PASS |
| Rule 10 | §6.3 | databaseNow and timestamp proof | §7 Step 2/3/4 | UTC microseconds, half-open | TEST-003/004 | Step 2/3/4 | PASS |
| Rule 11 | §6.1/8/11 | AR JDBC and external Flyway | §7 all | one exact technical profile in DEC-005 | exact paths and no POM | Every Step | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt, from/to, created_at, measured_at | Occurred instant | Instant | UTC instant | Internal | Not applicable: owner technical facts | Not applicable: no UI | UTC half-open, measured >= created | AgentDB clock after lock; micros | old SSE/time unchanged | Step 2 and Step 4; TEST-003 and TEST-004 |
| TIME-002 | range and read timeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no UI | <=31d and 2s | bounded JDBC/source | old list unaffected | Step 2; TEST-004 |

## 5. Change File Tree

`AR` = `stellar-enclosure-tianji-agent-runtime`，`AS` = `stellar-enclosure-tianji-agent-server`；具体仓库相对路径在 §7 每一文件块列明。

| File | Operation | Step | Responsibility |
| --- | --- | --- | --- |
| AR AgentModelUsageCaptureTest.java | Create, Modify | 1,4 | 价格和 callback finite |
| AR AgentModelUsagePriceVO.java | Create | 1 | frozen tariff |
| AR AgentModelBudgetEstimator.java / JTokkitChatModelBudgetEstimator.java | Modify | 1 | optional bound price |
| AR AgentModelUsageStoreTest.java | Create, Modify | 2,3,4 | finite only |
| AR AgentModelUsagePostgresTest.java | Create, Modify | 3,4 | source testCompile only；实际 I 另获准 |
| AR JdbcAgentLedgerRepository.java | Modify | 2,4 | bounded exact read, then atomic SQL writes |
| AR AgentLedgerService.java | Modify | 2,3 | disabled construction / exact reader / verified opt-in |
| AR AgentStorageVerifier.java | Modify | 3 | optional exact V15 |
| AR V15__add_agent_model_usage.sql | Create once | 3 | only additive schema |
| AR AgentBudgetService.java / AdkRunBudgetPlugin.java | Modify | 4 | exact active MODEL writes |
| AS AgentModelUsageDiagnosticsTest.java / AgentModelUsageDiagnostics.java | Create | 5 | real resource authorization and default off |
| AS TianjiAgentStorageConfiguration.java / application.yml | Modify | 5 | existing factory with bounded opt-in |
| docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md / docs/egon/plan/2026-11-05-16-50-agent-model-usage-implementation.md | Create | 1 | exact locked docs |
| docs/egon/evidence/2026-11-05-agent-model-usage-ledger.md | Create | 5 | actual source evidence only |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

工作目录 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。只读当前版本与正在进行的 A1 分开，禁止将 A1 或非相关路径纳入该 Plan 的 stage。锁定最终 HEAD、使用的 Spec SHA、所有旧 SQL 与 POM/公开契约哈希。

### 6.2 Build, test, and environment prerequisites

现有 `scripts/verify-source-reactor.sh`，JDK 25，沿现有独立 settings/offline proof。源码门禁不使用 PG/应用/真实 provider，只有新测试类中明确 finite 方法。I 在独立预置 PostgreSQL 审批后运行，用既有 opt-in 的环境管理方式。T3-12 source 已完成，T3-12 E/S12 仍是用户控制运行门禁。

### 6.3 Immutable constraints and approved decisions

Root 锁定 DEC-005 现有 AR JDBC/外部 Flyway 技术范围。I/E/S12 仍由用户独立控制。V1–V14 immutable，V15 最终字节提交一次，无 schema runner、Flyway repair、自动迁移或新表以外的 DDL。新 feature 默认 off。CONFIG exact reviewed cumulative only；late、CODE_FIRST、delta 不纳入。

### 6.4 Plan Clarifications

| ID | Clarification | Evidence | Effect if wrong |
| --- | --- | --- | --- |
| PC-001 | V15 只在 Step 3 创建；Step 2 可编译 SELECT seam 仍以 no-capability 拒绝 | base AgentStorageVerifier 按 owned known versions 验证，旧成功 history 可含新版 | 兼容性改变则回 Spec，不削弱 gate |
| PC-002 | 有限 StoreTest 与真实 PostgreSQL 独立；有限选择器永不选择 PostgresTest | PG 类只用于获准 I | 防 skipped count 混入有限报告 |
| PC-003 | V2 MODEL 明细 C 排序使用显式 COLLATE 语句，并在 same-source primary tx 内读 | current repository.list/terminalReference，V5 owner 索引 | 查不到或超时固定 unavailable，未经授权不加索引 |
| PC-004 | AS façade 用现有 GET_API/LIST_API；storage 已装配时才处理新 flags | TianjiAgentStorageConfiguration 外部已条件装配，TianjiAgentAccess 校验实际 verified actor | 不造 public route 或 ambient actor |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 冻结现有 JTokkit 可验证价目

- Requirements: REQ-002, REQ-005
- Dependencies: Spec/Plan 与源码锁批准
- Baseline state: existing estimator 只有预算 estimate/actualUsage 和现行 ContextWindow。
- Observable outcome: 复用可信绑定提供可选 immutable tariff，未支持的 host 仍照旧。
- End state: 四 AR files 与两份锁定文档，无 ledger/schema/capture wiring。
- Test-first gate: Required — 模型价格方法反射 RED，A 默认空，已绑定 JTokkit 价目 typed RED。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-REUSE-001, MC-PATTERN-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelUsageCaptureTest.java`

- Purpose: 固定可选价格与冻结精度。
- Symbols: `priceSeamExists`（临时），`boundJtokkitTariffIsExactAndStable`。
- Repository evidence: `JTokkitChatModelBudgetEstimatorTest` 现有绑定/漂移 fixtures。
- Dependencies and consumers: File 2–4, JUnit and Fake ChatModel。
- Why now: 先断言，再声明 A seam。
- Contract/signature changes: 测试现有 model/route/currency 和默认 optional empty。
- Input/output and state mapping: 同价格式与固定 SHA；变更费率或路由变版本。
- Error and edge behavior: 错误 route/policy、NaN/负值与不支持 host 无有效价。
- Standards impact: MC-VALID-001 / MC-TEST-001 精确实际 binding。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 9, Rule 11；只测已有技术 Strategy。
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO"));
var same = boundEstimator.modelUsagePrice(snapshot, finalRequest).orElseThrow();
assertEquals(knownPriceVersion, same.priceVersionRef());
assertNotEquals(same.priceVersionRef(), changedRatePrice.priceVersionRef());
assertTrue(legacyEstimator.modelUsagePrice(snapshot, finalRequest).isEmpty());
```

- Verification contribution: G1 finite RED/GREEN。
- After this file: 反射可先运行，A 后激活 typed cases。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/AgentModelUsagePriceVO.java`

- Purpose: 小型不可变冻结价目。
- Symbols: `AgentModelUsagePriceVO(routeRef, modelName, currency, inputRatePerMillion, outputRatePerMillion)`, `priceVersionRef()`。
- Repository evidence: 现有 ModelBudgetProperties.Profile 和 ContextWindow limits。
- Dependencies and consumers: domain value，File 3/4 及后续预算读写。
- Why now: A 提供纯值，typed RED 后加规范摘要。
- Contract/signature changes: 校验 scope 文本、币种、费率；无 provider credential 或 policy 可变引用。
- Input/output and state mapping: BigDecimal 规范 stripTrailingZeros.toPlainString；固定价目算法域和 length-delimited 字段。
- Error and edge behavior: null、负值、超精度、控制/坏代理及路由漂移拒绝；0 合法。
- Standards impact: MC-MODEL-001 / MC-UTIL-001 不可变 record，无新工具。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11；值与已有技术域一致。
- Implementation pseudocode:

```java
record AgentModelUsagePriceVO(String routeRef, String modelName, String currency, BigDecimal inputRate, BigDecimal outputRate) {
    // Validate both rates against current Profile numeric limits and exact scope text.
    String priceVersionRef() { return lengthDelimitedSha("TIANJI_MODEL_USAGE_PRICE_V1", routeRef, modelName,
        currency, normalize(inputRate), normalize(outputRate), "PER_MILLION_CEILING_8"); }
}
```

- Verification contribution: 价目版本、等价数字表示、变化触发。
- After this file: 值可由现有 estimator 返回。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java`

- Purpose: 保持宿主预算 SPI 兼容。
- Symbols: `default Optional<AgentModelUsagePriceVO> modelUsagePrice(snapshot, request)`。
- Repository evidence: 现有 `contextWindow/modelAdapterFingerprint/contextPolicy` 都是 optional/default 扩展形态。
- Dependencies and consumers: 旧宿主实现、JTokkit、Step 4 callback。
- Why now: 先提供 A 编译接口与默认行为。
- Contract/signature changes: 只加 default optional，原 estimate/actualUsage 不动。
- Input/output and state mapping: 仅可信实现会返回价目，默认 empty。
- Error and edge behavior: 不把任意 actualCost 反解成价格；原异常保留。
- Standards impact: MC-REUSE-001 / MC-PATTERN-001 现有 Strategy。
- Literal rule enforcement: Rule 1, Rule 3, Rule 9, Rule 11；无公开 C SDK 或新注入。
- Implementation pseudocode:

```java
default Optional<AgentModelUsagePriceVO> modelUsagePrice(AgentExecutionSnapshot snapshot, LlmRequest request) {
    // Compatibility: host estimators retain original estimate/actualUsage semantics.
    return Optional.empty(); // Absence is unpriced, never an invented zero rate.
}
```

- Verification contribution: 旧 estimator 编译，默认空测试。
- After this file: 其他实现无需补方法。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`

- Purpose: 从同次已绑定配置输出冻结价。
- Symbols: `modelUsagePrice` 调 `require(snapshot, request.model())`。
- Repository evidence: JTokkit require/current 已核验 CONFIG、snapshot route/policy、model bean/options、currency。
- Dependencies and consumers: 复用 Bound/profile，保留 native wrapper 当前兼容。
- Why now: typed RED 后接真实价目。
- Contract/signature changes: CONFIG bound 返回 immutable optional；native path 不进入首切片。
- Input/output and state mapping: 取 descriptor modelRouteRef、profile modelName/currency/rates。
- Error and edge behavior: mismatch 保持现有 unavailable；未审路线的调用者不会请求价格。
- Standards impact: MC-REUSE-001 / MC-VALID-001 原绑定不旁路。
- Literal rule enforcement: Rule 2, Rule 5, Rule 9, Rule 11；保持原有技术 Strategy。
- Implementation pseudocode:

```java
var selected = require(snapshot, request.model().orElse(null));
var profile = selected.profile();
return Optional.of(new AgentModelUsagePriceVO(selected.descriptor().modelRouteRef(), profile.modelName(),
    profile.currency(), profile.inputRatePerMillion(), profile.outputRatePerMillion()));
```

- Verification contribution: G1 exact known price + JTokkit regression。
- After this file: 有限 price slice 可以独立交付。

#### File 5 — `CREATE docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md`

- Purpose: 纳入 Root 已锁的源实施规范。
- Symbols: 有效 Spec 内容与 scope lock。
- Repository evidence: review mirror strict PASS 与 exact SHA。
- Dependencies and consumers: 下游 Plan/实现/证据。
- Why now: 四条有限源路径通过后同步已审核文本。
- Contract/signature changes: 源文本 byte-for-byte，不重新解释当前状态。
- Input/output and state mapping: exact canonical target path，Root 锁的文件 SHA。
- Error and edge behavior: 内容漂移停止重审；既有规范不覆盖。
- Standards impact: MC-SCOPE-001 / MC-TEST-001。
- Literal rule enforcement: Rule 11：DEC-005 技术范围可追踪。
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md
sha256sum "$approved_t4_02_spec"
cmp "$approved_t4_02_spec" docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md # compare after copying the approved bytes
```

- Verification contribution: 通过 strict Spec 与 staged SHA。
- After this file: canonical Spec 具备可追踪落点。

#### File 6 — `CREATE docs/egon/plan/2026-11-05-16-50-agent-model-usage-implementation.md`

- Purpose: 纳入审核后的执行规范。
- Symbols: 现行唯一 primary target link 和 Step 分组。
- Repository evidence: writing-plan strict mirror；Step scope lock。
- Dependencies and consumers: Root 实施及最终 scope audit。
- Why now: 源测试和 exact Spec 在前。
- Contract/signature changes: 按 canonical relative links 保存已锁 Plan。
- Input/output and state mapping: Plan SHA、逐步 scope、待执行真实运行验收。
- Error and edge behavior: 配置或范围漂移返回 Root 审查。
- Standards impact: MC-SCOPE-001 / MC-TEST-001。
- Literal rule enforcement: Rule 11：每步精确文件和 profile 证据。
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-16-50-agent-model-usage-implementation.md
sha256sum "$approved_t4_02_plan"
cmp "$approved_t4_02_plan" docs/egon/plan/2026-11-05-16-50-agent-model-usage-implementation.md # verify exact staged review plan
```

- Verification contribution: strict Plan 和 staged checksum。
- After this file: Step 1 exact six paths可提交。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `t4_02_finite AgentModelUsageCaptureTest` reflection→typed RED→GREEN；后加 `JTokkitChatModelBudgetEstimatorTest`。
- Expected result: 每个 owning class N>0；RED F>0/E=S=0，最终 F=E=S=0。
- Failure returns to: File 1 fixture、Files 2–4 bound identity 或环境。
- Completion criteria: 六条 exact paths、locked Spec/Plan SHA 和有限 XML，无 DDL/API 变更。
- Rollback: 只回退该源码提交。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelUsageCaptureTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/AgentModelUsagePriceVO.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`, `docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md`, `docs/egon/plan/2026-11-05-16-50-agent-model-usage-implementation.md`.
- Commit: `feat(agent): expose verified model usage tariff`

### Step 2 — 实现先于 V15 的 owner 读取与关闭兼容

- Requirements: REQ-003, REQ-004, REQ-005
- Dependencies: Step 1
- Baseline state: 旧 ledger get/list/terminalReference 工作；无 V15/usage reader。
- Observable outcome: 旧构造和现有读完全无 V15 SQL；新 owner 查询 SQL/映射有限验证。
- End state: 三个 AR files，真正 standalone 无 enabled construction，V15 仍缺。
- Test-first gate: Required — reader 反射 RED 后，A declared read 让 fake JDBC 正向 shape assertion RED。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-VALID-001, MC-MODEL-001, MC-REUSE-001, MC-TIME-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`

- Purpose: 纯有限 StoreTest 固定 owner/security/limit 与关闭语义。
- Symbols: `usageReaderExists`（临时）、`finiteOwnerReadScopeAndDisabledNoV15`。
- Repository evidence: `AgentStorageVerifierPostgresTest`/`AgentBudgetPostgresTest` 的 I 属性；有限 mock JdbcTemplate 属独立方法。
- Dependencies and consumers: File 2/3、JUnit Mockito；I 加在 Step 3/4。
- Why now: typed reader 行为先固定，避免 V15 先提交。
- Contract/signature changes: 此类只有有限方法；本步不创建 PostgreSQL 测试类。
- Input/output and state mapping: mock owner/row SQL proof + legacy off read。
- Error and edge behavior: foreign/legacy/no-capability、wrong cursor、future/31 天、20/5/100 sentinel、source timeout。
- Standards impact: MC-VALID-001 / MC-TIME-001 / MC-TEST-001 不给 fake 事务验收。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11；有限与 PG 分开。
- Implementation pseudocode:

```java
assertThrows(AgentLedgerException.class, () -> defaultLedger.getModelUsage(actor, getQuery, null));
verifyNoV15SqlWhenDisabled(jdbc);
assertOwnerFirstAndExclusiveSlotOrder(repository.modelUsageSlots(actor, runId, "slot-0", 101));
assertCorrelationBoundaryAndNoForeignCount(repository.modelUsageRuns(actor, sameCorrelation, from, to, cursor, 21));
```

- Verification contribution: G2 reflection/typed RED and GREEN；PG still unexecuted。
- After this file: 只定义有限方法；Step 3 加 opt-in PG。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: 提供同源 owner-first bounded reads。
- Symbols: `modelUsageRun, modelUsageRuns, modelUsageSlots`（同包）。
- Repository evidence: 既有 `run/list` 按 tenant,subject_type,subject_issuer,subject_id；V5 owner created 索引。
- Dependencies and consumers: File 3 事务封套，V1/V2/V15 exact shapes。
- Why now: A 声明返回空；B 在 DDL 前固定只读查询，不会由旧构造调用。
- Contract/signature changes: 已核验 run 与 V2 MODEL 左联 V15，reservation_id C 排序；related window + stable DESC keyset。
- Input/output and state mapping: 只读安全列，101/21/6 sentinel；数据缺失、真实源异常闭合。
- Error and edge behavior: owner 过滤先于 LIMIT，不放宽 LEGACY；异常不回未过滤部分或 raw JDBC text。
- Standards impact: MC-VALID-001 / MC-REUSE-001 SQL 参数化与既有 owner。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11；同源原位。
- Implementation pseudocode:

```sql
SELECT run_id, created_at, correlation_id, status, lease_epoch, lease_expires_at
FROM tianji_agent.agent_run
WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?
  AND correlation_id=? AND created_at>=? AND created_at<? AND (created_at,run_id)<(?,?)
ORDER BY created_at DESC,run_id DESC LIMIT ?;
// V2 MODEL scoped slot LEFT JOIN V15 USING full three keys, C collation and exact limit.
```

- Verification contribution: G2 finite exact mock SQL/parameters；PG later。
- After this file: repository bounded mapping 编译，旧读取无调用。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: 构造关闭状态与受信 bounded primary reader。
- Symbols: `getModelUsage(actor,GetAgentRunQuery,afterReservationId)`、`listModelUsageByCorrelation(actor,AgentModelUsageCorrelationQuery)`及嵌套 DTO。
- Repository evidence: `get/list/terminalReference` 已有 exact owner/primary snapshot，原 constructors/withTerminalEvents。
- Dependencies and consumers: 同源 repository；未来 AS wrapper 调，Step 3 才配置 guarded enabled 工厂。
- Why now: repository type 先编译；B 为 owner reader 和 disabled guard。
- Contract/signature changes: 仅可信 actor、拒绝 LEGACY/ambient TX，old factories 固定 disabled；immutable cursor binding。
- Input/output and state mapping: primary REPEATABLE_READ，2s，DB observedAt 一次；按 run 100、correlation 20 × 5。
- Error and edge behavior: 缺/foreign 统一；UTC 窗口、cursor mismatch、超时闭合；PENDING 非当前有效 lease 投影 UNKNOWN。
- Standards impact: MC-VALID-001 / MC-MODEL-001 / MC-TIME-001，内嵌值避免新 public DTO。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11；保持 C/SDK。
- Implementation pseudocode:

```java
requireOptInAndExactVerifiedActor(actor);
rejectAmbientTransaction(); // existing terminalReference uses primary-safe transaction route
return boundedPrimaryRepeatableRead(() -> {
    Instant observedAt = databaseNow();
    validateUtcWindowAndOwnerCursor(observedAt, query, actor);
    return projectSafeUsage(repository.modelUsageRuns(actor, query, 21), observedAt, 5);
});
```

- Verification contribution: G2 typed disabled/owner boundary + no schema byte。
- After this file: reader 编译，不开 V15/写/AS feature。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `t4_02_finite AgentModelUsageStoreTest#finiteOwnerReadScopeAndDisabledNoV15`（初次反射用 `#usageReaderExists`）。
- Expected result: 反射/typed 正常断言 RED 后有限 GREEN，无 V15 resource/DB。
- Failure returns to: File 1 test fixture，Files 2/3 exact scope/clock。
- Completion criteria: 三个路径，V15 仍不存在，旧公开代码/SQL 不变。
- Rollback: 回退兼容 reader 提交。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`.
- Commit: `feat(agent): add gated owner-local model usage reads`

### Step 3 — 新增唯一 immutable V15 和专用 readiness

- Requirements: REQ-001, REQ-002, REQ-004, REQ-005
- Dependencies: Step 2 reader committed
- Baseline state: V14 最新，已有 reader 不触发 V15。
- Observable outcome: 唯一 V15 按冻结合约添加，基础旧 profile 不依赖它，opt-in 专用 schema proof。
- End state: SQL 与 verifier/factory 有限证据；捕获/standalone 仍不启用。
- Test-first gate: Required — V15 resource absent 真实 assertion RED；typed verifier 假 history 初期拒绝准入。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-DEP-001, MC-TIME-001, MC-CONFIG-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 10, Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`

- Purpose: 锁住资源、校验和及 gate 优先级。
- Symbols: `requiresImmutableV15Resource, finiteModelUsageReadinessMatrix`。
- Repository evidence: 现有 `AgentStorageVerifierPostgresTest` 与 feature-specific history verifiers。
- Dependencies and consumers: Files 2–4；纯有限 mock/history；独立 PG 在 File 2。
- Why now: 资源先 RED，再最终只创建一次 V15。
- Contract/signature changes: finite 检查预期 DDL 资源和 mock catalog 的必要状态，I 在同源真实验证。
- Input/output and state mapping: 缺 V15/mismatch、列或 privileges 异常、off V14、on V15。
- Error and edge behavior: 真实迁移仅可在独立授权的已锁 V1–14 历史实例。
- Standards impact: MC-TEST-001 / MC-VALID-001 区分源码和 I。
- Literal rule enforcement: Rule 2, Rule 5, Rule 10, Rule 11；资源与架构证据。
- Implementation pseudocode:

```java
assertNotNull(getClass().getResource("/db/tianji-agent/V15__add_agent_model_usage.sql"));
assertThrows(IllegalStateException.class, () -> verifierWithMissingOrWrongV15.verifyModelUsage());
assertDoesNotThrow(() -> verifierWithExactOwnedFacts.verifyModelUsage());
assertOldDisabledConfigurationDoesNotReadUsageHistoryOrTable();
```

- Verification contribution: G3 resource assertion、typed verifier、I separately。
- After this file: RED 后才加 immutable 文件。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsagePostgresTest.java`

- Purpose: 为独立 I 定义实际同源 PostgreSQL 断言。
- Symbols: `v15MigrationOnIsolatedPostgres`。
- Repository evidence: 既有 AgentBudgetPostgresTest、AgentStorageVerifierPostgresTest 与 PostgresTestDatabase 隔离 opt-in。
- Dependencies and consumers: 实际源/角色和迁移验证仅独立用户批准；非 finite gate。
- Why now: 先由本 Step 独立 StoreTest 固定有限 RED，再补 compile-only I。
- Contract/signature changes: 实际 PostgreSQL 只在独立 class 与显式 I 参数运行；有限选择器绝不选择此类。
- Input/output and state mapping: V14/off 与 V15/on exact schema/role，同源原预算和账本结果。
- Error and edge behavior: 无隔离配置不可执行；实际断言失败不由 mock 替代。
- Standards impact: MC-SCOPE-001 / MC-TEST-001 严格分开编译与运行。
- Literal rule enforcement: Rule 5, Rule 10, Rule 11；同源现有测试依赖和 DB UTC。
- Implementation pseudocode:

```java
assertExistingV14LedgerRemainsCompatible(isolatedSource);
applyReviewedMigrationInIsolatedFixtureOnly("V15__add_agent_model_usage.sql");
assertExactOptInHistoryCatalogAndUsageRole(isolatedSource);
```

- Verification contribution: 本次只检查 testCompile/class；获准 I 才实际选择。
- After this file: PG 证据 seam 可编译，未生成真实运行报告。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V15__add_agent_model_usage.sql`

- Purpose: 一张 1:1 MODEL observation 表。
- Symbols: `tianji_agent.agent_model_usage`，PK/FK 和命名 checks。
- Repository evidence: 原 V2 PK 与 §11；V14 是最新已存在版本。
- Dependencies and consumers: File 3 schema identity/CRC、reader、later writer。
- Why now: Step 2 compatibility已独立提交，且 resource RED 真实执行。
- Contract/signature changes: 精确 column inventory 原样遵循 Spec §11；NO ACTION，零附加索引或 ALTER。
- Input/output and state mapping: numeric(32,8) 两费率，numeric(24,8) cost，finite timestamptz(6)，三元 PK/FK；相加 numeric 防 bigint overflow。
- Error and edge behavior: all-or-none price、闭集状态/理由/nullable matrix、measured>=created、非负有限及 lower hex。
- Standards impact: MC-SCOPE-001 / MC-TIME-001 additive one file。
- Literal rule enforcement: Rule 2, Rule 10, Rule 11；DEC-005 外部 Flyway 技术边界。
- Implementation pseudocode:

```sql
CREATE TABLE tianji_agent.agent_model_usage (
    -- Exact Spec §11 columns; retain V2 key names and positive frozen lease_epoch.
    PRIMARY KEY (tenant_id, run_id, reservation_id),
    FOREIGN KEY (tenant_id, run_id, reservation_id)
        REFERENCES tianji_agent.agent_budget_reservation (tenant_id, run_id, reservation_id) ON UPDATE NO ACTION ON DELETE NO ACTION
    -- Named checks: finite UTC, positive scope, price group, state-specific all-or-none actual facts.
);
```

- Verification contribution: G3 checksum/sql shape；真正 PG I 后续。
- After this file: 最终 bytes 在创建前审阅，之后不编辑。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`

- Purpose: 独立 V15 readiness，不扩基础 V1–6。
- Symbols: `verifyModelUsage()`。
- Repository evidence: `verifyLinked/verifyContextSnapshots` pattern，原 checksum()/catalog/readConstraints。
- Dependencies and consumers: immutable V15 bytes，File 4 factory。
- Why now: SQL 最终字节已锁定后计算 artifact checksum。
- Contract/signature changes: 源主库，exact version/script/success checksum，真实列/PK/FK/checks/privileges。
- Input/output and state mapping: unique V15 SQL row，不依赖 V15 on old verify；只读 catalog。
- Error and edge behavior: 拒绝错源/只读副本、部分 DDL、可写 frozen fields 权限 drift；固定错误。
- Standards impact: MC-REUSE-001 / MC-VALID-001 external Flyway。
- Literal rule enforcement: Rule 2, Rule 5, Rule 7, Rule 10, Rule 11；无应用迁移。
- Implementation pseudocode:

```java
verify(); // existing baseline remains V1–V6
verifyInOwnedRepeatableRead(() -> {
    requirePrimaryAndExactFlywayRow("15", "V15__add_agent_model_usage.sql", checksumFromResource);
    verifyExactUsageColumnsKeysNamedChecksAndMinimalPrivileges();
});
```

- Verification contribution: G3 finite mock contract + opt-in I。
- After this file: 独立校验可以供后续 capability factory。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`

- Purpose: 显式 verified same-source usage capability。
- Symbols: `withModelUsage(dataSource, optionalPublisher, exactRouteRefs)`、`modelUsageEnabled/usesModelUsageRoutes`。
- Repository evidence: 现有 constructor、withTerminalEvents、usesDataSource 与 Step 2 disabled branch。
- Dependencies and consumers: File 3 readiness，后续 AS only；已有消费者继续旧工厂。
- Why now: 真实 V15 校验 seam 已完成后才开放 capability construction。
- Contract/signature changes: 由既有 ledger 建 immutable opt-in 模式，默认旧构造 off。
- Input/output and state mapping: 同 DataSource 与原 terminalPublisher；验证 route refs ≤32/unique/exact，不修改 old wire。
- Error and edge behavior: 缺 schema/错源/readiness 失败不能成为 enabled；off 无 SQL。
- Standards impact: MC-ARCH-001 / MC-CONFIG-001 兼容名与单一管理器。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 7, Rule 11；现有源码边界。
- Implementation pseudocode:

```java
static AgentLedgerService withModelUsage(DataSource source, AgentTerminalEventPublisher publisher, List<String> refs) {
    var reviewed = validatedImmutableExactRefs(refs);
    new AgentStorageVerifier(source).verifyModelUsage();
    return existingLedgerConstructionWithOptionalUsage(source, publisher, reviewed);
}
```

- Verification contribution: G3 typed factory readiness/disabled compatibility。
- After this file: 只有显式可信调用可获得 capability，standalone wiring 尚未提交。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `t4_02_finite AgentModelUsageStoreTest#requiresImmutableV15Resource` 与 `#finiteModelUsageReadinessMatrix`；I 另用 §8。
- Expected result: absence 与 typed RED 有断言；最终 finite F=E=S=0，checksum/DDL artifact 已记录。
- Failure returns to: File 1 gate、File 2 before immutable creation、Files 3/4 guard。
- Completion criteria: 五路径；old migration hashes 和 V15 reviewed bytes 核对。
- Rollback: 保持 V15 bytes；关闭能力/回退代码，不删除或改已存在 SQL。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsagePostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V15__add_agent_model_usage.sql`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`.
- Commit: `feat(agent): add immutable model usage storage`

### Step 4 — 在原 MODEL 结算事务写入当前可信终值

- Requirements: REQ-001, REQ-002, REQ-005
- Dependencies: Steps 1–3
- Baseline state: 已有 verified schema/read，当前未录入调用。
- Observable outcome: 现有 reserve/settle 原子持久种子与终值；预算语义保持。
- End state: 五 AR 路径；standalone 仍未打开；无 late/delta。
- Test-first gate: Required — A 签名 compile；mock budget/plugin 的正向 seed/final 真实 assertion RED。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-PATTERN-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelUsageCaptureTest.java`

- Purpose: 固定现有 ADK 回调对账本的精确入口。
- Symbols: `onlyAdmittedConfigFinalAddsUsage, partialAndClosedCallbacksDoNotRecord`。
- Repository evidence: AdkRunBudgetPlugin 已有 before/after、MODEL authorization、session fence、partial/idle。
- Dependencies and consumers: File 4/5 接口；现有 Fake model、有限 JUnit。
- Why now: 先固定 typed RED。
- Contract/signature changes: 测试原上下文/eventId 与 frozen price seed。
- Input/output and state mapping: exact CONFIG allowed route 才写；off/TOOL/CODE_FIRST/unlisted 原路径。
- Error and edge behavior: usage-only 无文本，partial/close 0 写，原错误与额度保持。
- Standards impact: MC-TEST-001 / MC-VALID-001 真实 callback seam。
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 11；保持现有治理。
- Implementation pseudocode:

```java
assertOldReserveOverloadUsedForDisabledAndCodeFirst();
assertExactModelSeedAfterExistingAuthorizationAndFences(snapshot, request, eventId);
assertNoSettleOnPartialOrClosedCallback();
assertEquals(verifiedCountsOnlyWhenRawMatchesBudgetObservation(), capturedUsage);
```

- Verification contribution: G4 before/after assertion RED then GREEN。
- After this file: 追加而不替换原 Step 1 价格门禁。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`

- Purpose: 证明同源写顺序与幂等。
- Symbols: `finiteUsageMutationFollowsExistingBudgetScope`。
- Repository evidence: AgentBudgetPostgresTest 与本类 Step 2/3 兼容 gate。
- Dependencies and consumers: Files 4–6；有限 mock，独立 PG 在 File 3。
- Why now: writer 之前锁实际 typed RED。
- Contract/signature changes: finite mock SQL + own transaction calls；I committed rows。
- Input/output and state mapping: exact seed/final/rollback/旧 epoch/超额；预算与 ledger 一起写。
- Error and edge behavior: 无 authorized I 不执行；close 只读 UNKNOWN；不替换真实事务证明。
- Standards impact: MC-TEST-001 / MC-TIME-001。
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 10, Rule 11；有限/I 分开。
- Implementation pseudocode:

```java
assertOnlyModelSameTenantEpochCanSeed();
assertStatementOrder("run-lock", "reservation", "usage-slot");
assertFirstFinalWinsAndIdenticalReplayDoesNotChangeMeasuredAt();
assertReservationAndUsageRollbackTogetherOnForcedSqlFailure();
```

- Verification contribution: G4 finite RED 和独立 I。
- After this file: 既有 reader/schema test 保留。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsagePostgresTest.java`

- Purpose: 为独立 I 定义实际同源 PostgreSQL 断言。
- Symbols: `immutableUsageAndBudgetOnIsolatedPostgres`。
- Repository evidence: 既有 AgentBudgetPostgresTest、AgentStorageVerifierPostgresTest 与 PostgresTestDatabase 隔离 opt-in。
- Dependencies and consumers: 实际源/角色和迁移验证仅独立用户批准；非 finite gate。
- Why now: 先由本 Step 独立 StoreTest 固定有限 RED，再补 compile-only I。
- Contract/signature changes: 实际 PostgreSQL 只在独立 class 与显式 I 参数运行；有限选择器绝不选择此类。
- Input/output and state mapping: V14/off 与 V15/on exact schema/role，同源原预算和账本结果。
- Error and edge behavior: 无隔离配置不可执行；实际断言失败不由 mock 替代。
- Standards impact: MC-SCOPE-001 / MC-TEST-001 严格分开编译与运行。
- Literal rule enforcement: Rule 5, Rule 10, Rule 11；同源现有测试依赖和 DB UTC。
- Implementation pseudocode:

```java
assertOneWinnerFromConcurrentExactBudgetAndUsageTransactions();
assertSameDigestReplayPreservesMeasuredAt();
assertOldEpochAndForcedSqlFailureDoNotWriteUsageOrLeakBudget();
```

- Verification contribution: 本次只检查 testCompile/class；获准 I 才实际选择。
- After this file: PG 证据 seam 可编译，未生成真实运行报告。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: 同源 seed、CAS、exact reread。
- Symbols: `insertModelUsageSeed, settleModelUsage, modelUsageForUpdate`。
- Repository evidence: 现有 V2 insertBudgetReservation/settleBudget 和 Step 2 owner predicates。
- Dependencies and consumers: File 4 原同源事务及 V15。
- Why now: schema 已冻结后只实现最小 SQL。
- Contract/signature changes: INSERT SELECT V2 exact MODEL/epoch；UPDATE exact PENDING；冻结字段不更新。
- Input/output and state mapping: 单次 DB 微秒、safe digest 不含 measuredAt。
- Error and edge behavior: 0-row scoped reread 区分同值/缺历史/冲突；原 SQLState 不对外。
- Standards impact: MC-REUSE-001 / MC-VALID-001 / MC-TIME-001。
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 10, Rule 11；无新 Store。
- Implementation pseudocode:

```sql
INSERT INTO tianji_agent.agent_model_usage (...)
SELECT ... FROM tianji_agent.agent_budget_reservation
WHERE tenant_id=? AND run_id=? AND reservation_id=? AND kind='MODEL' AND lease_epoch=?
ON CONFLICT (tenant_id,run_id,reservation_id) DO NOTHING;
-- UPDATE only measurement fields for exact PENDING slot and epoch; exact reread on zero.
```

- Verification contribution: G4 SQL 参数与 I replay。
- After this file: 原 entry points 尚不调用新方法。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetService.java`

- Purpose: 同一原预算事务处理新事实。
- Symbols: `reserveBudget(...,AgentContextMetadataDTO,ModelUsageSeedVO)`、`settleBudget(...,AgentBudgetSettlement,ModelUsageFinalVO)`，嵌套 immutable 值。
- Repository evidence: 现有 reserve/settle 锁 run，过量在 txn 提交后抛，UNKNOWN 原额度保持。
- Dependencies and consumers: File 3 SQL 和 File 5 ADK；旧 overloads。
- Why now: 已有 typed/SQL/Schema 之后接同源 orchestration。
- Contract/signature changes: 旧 overloads 传无观察；预留和观测槽原子；原 active settlement 后写首值。
- Input/output and state mapping: 新 MODEL seed；冻结 drift/conflict；不回填旧行；终值以相同摘要或同 UNKNOWN 原因重放。
- Error and edge behavior: 原失败或旧 lease 不写，原预算异值冲突在先。真实超额预算的 txn 结果和抛出位置保留。
- Standards impact: MC-ARCH-001 / MC-VALID-001 / MC-PATTERN-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11；原 Command。
- Implementation pseudocode:

```java
return existingBudgetTransaction.execute(status -> {
    lockRunAndExactOriginalReservation(actor, lease, reservationId);
    var previous = settleExistingBudgetWithoutChangingSemantics(actual);
    if (admittedModelUsagePresent()) settleExactUsageSlotOrCompareReplay(previous, observed, databaseNow());
    return previous; // existing post-transaction exceeded outcome remains.
});
```

- Verification contribution: G4 order/rollback/replay。
- After this file: old scope 未启用无 V15 SQL。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`

- Purpose: 仅通过现有可信 before/after 接入。
- Symbols: `beforeModelCallback, afterModelCallback`。
- Repository evidence: MODEL authorization、ContextWindow、session fences、eventId、existing settle branch。
- Dependencies and consumers: Step 1 price / File 4 overloads，现有 lifecycle。
- Why now: 最后接原入口，不重复校验策略。
- Contract/signature changes: exact CONFIG + route allowlist 走可选 seed/final；保留 original estimator and budget。
- Input/output and state mapping: usageMetadata 两计数与原 settlement 精确一致、可安全加总，total 有则吻合；缺/错或 error 安全 UNKNOWN。
- Error and edge behavior: partial only idle，estimator failures 按原逻辑，post-close 无补写/重订阅。
- Standards impact: MC-VALID-001 / MC-PATTERN-001 existing governance。
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11；旧路径原语。
- Implementation pseudocode:

```java
if (ledger.acceptsModelUsage("CONFIG", validatedContextWindow.modelRouteRef())) {
    reserveWithOptionalSeedAfterExistingFences(eventId, validatedModel, optionalFrozenPrice);
}
if (response.partial().orElse(false)) return existingIdleGuardBranch();
settleWithOptionalExactFinal(actualUsage(response), rawUsageMetadataIfAuthorized, originalEventId);
```

- Verification contribution: G4 actual callback finite and old ADK tests。
- After this file: guarded capture available; AS wiring next。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `t4_02_finite AgentModelUsageCaptureTest,AgentModelUsageStoreTest#finiteUsageMutationFollowsExistingBudgetScope,AdkNativeAgentCompletionTest,AdkContextWindowPlanTest`。
- Expected result: typed RED 后有限 suites 实际 F/E/S=0，I 单列。
- Failure returns to: File 1/2 fixtures、File 3 SQL、File 4 txn、File 5 callback。
- Completion criteria: 六路径、legacy finite、off 无 V15 SQL。
- Rollback: 关闭或回退代码；保留 immutable V15。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelUsageCaptureTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsageStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentModelUsagePostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`.
- Commit: `feat(agent): record admitted model usage atomically`

### Step 5 — 用现有 AS 权限接通私有查询并保持默认关闭

- Requirements: REQ-003, REQ-004, REQ-005
- Dependencies: Step 4
- Baseline state: AR 已有 guarded capability，AS 沿用原 factory。
- Observable outcome: 现有同源 Bean opt-in，私有 exact-owner read，缺省无新 SQL。
- End state: 四 AS 路径加一份来源证据，原 HTTP/SSE/SDK。
- Test-first gate: Required — façade reflection RED，A 编译后 typed resource/flag assertion RED。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-VALID-001, MC-BEAN-001, MC-LOG-001, MC-JSON-001, MC-CONFIG-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java`

- Purpose: 锁定资源 key、off 路径和 source binding。
- Symbols: `usageDiagnosticsSeamExists, exactGetAndListAuthorization, storageOffDoesNotReadV15, verifiedOptInOnly`。
- Repository evidence: TianjiAgentAccessTest、现有 storage named factory。
- Dependencies and consumers: Files 2–4；Mockito/ContextRunner 无 listener/DB。
- Why now: 先反射后 typed A/RED。
- Contract/signature changes: 原 auth path，default off and invalid opt-in。
- Input/output and state mapping: GET/LIST 各 exact resource，旧 constructor、API off、runtime off、custom ledger。
- Error and edge behavior: bad/duplicate refs、wrong data source、missing gate 拒绝；无公开 mapping。
- Standards impact: MC-TEST-001 / MC-BEAN-001 finite gates。
- Literal rule enforcement: Rule 2, Rule 4, Rule 6, Rule 7, Rule 11；现有 IAM。
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.agent.server.AgentModelUsageDiagnostics"));
verify(access).require(TianjiAgentPermissions.GET_API);
verify(access).require(TianjiAgentPermissions.LIST_API);
assertNoV15SqlFromOldFactoryAndNoNewRequestMapping();
assertContextFailsForUnverifiedCustomLedgerWhenOptedIn();
```

- Verification contribution: G5 RED/GREEN 与旧 wire regression。
- After this file: 只定义有限 tests。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnostics.java`

- Purpose: 同一个已认证请求内调用源内 exact owner 诊断。
- Symbols: `AgentModelUsageDiagnostics.byRun/byCorrelation`。
- Repository evidence: 现有 AgentRunController get/list 调用 access.require(RESOURCE).actor()。
- Dependencies and consumers: AS existing named factory，同源 AR service。
- Why now: A 声明两方法；typed RED 后实现。
- Contract/signature changes: GET_API/LIST_API 复用当前 TIANJI_AGENT_READ/tianji:agent:read，不接收 user-supplied actor。
- Input/output and state mapping: 取当前 verified actor 传 exact query。
- Error and edge behavior: 无可信认证/资源不符/过期拒绝，无 route/权限新增。
- Standards impact: MC-VALID-001 / MC-BEAN-001 existing resources。
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 6, Rule 11；私有来源。
- Implementation pseudocode:

```java
var actor = access.require(TianjiAgentPermissions.GET_API).actor();
return ledger.getModelUsage(actor, runQuery, afterReservationId);
// The independent correlation method uses LIST_API and the same verified identity flow.
```

- Verification contribution: G5 exact resource strings and actor equality。
- After this file: façade 无 Controller mapping。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java`

- Purpose: 现有 named storage Bean 选择 guarded factory，保留原 publisher。
- Symbols: `tianjiAgentServerLedgerService, tianjiAgentStorageGuard, tianjiAgentModelUsageDiagnostics`。
- Repository evidence: 现有 storage.enabled conditional 与 ObjectProvider optional publisher。
- Dependencies and consumers: private façade、existing TianjiAgentAccess Bean、verified ledger。
- Why now: 真 capability ready 才接 AS。
- Contract/signature changes: 两项 Binder flags；off 调旧 factory 无 V15；on 调同源 verified withModelUsage。
- Input/output and state mapping: exact route list；API off 无 façade；runtime off 可读已存；custom ledger 必须同源且确证 exact refs。
- Error and edge behavior: 缺/错 ready 时安全失败，保持 guard、无 auto-migrate 或不受控 fallback。
- Standards impact: MC-BEAN-001 / MC-CONFIG-001 existing factory。
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11；受限技术 profile。
- Implementation pseudocode:

```java
if (!usageEnabled(environment)) return existingLedgerFactory(source, publisher);
var routes = bindAndValidateExactRouteList(environment);
return AgentLedgerService.withModelUsage(source, publisher, routes);
// Existing guard checks same source and opted-in custom ledger capability/route equality.
```

- Verification contribution: G5 ContextRunner 与 AS regressions。
- After this file: 真实 wiring 已接，默认键最后。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: 配置明确 default off 与空名单。
- Symbols: `stellar.tianji.agent.storage.model-usage-enabled, model-usage-cumulative-route-refs`。
- Repository evidence: AS sole base application.yml，现有 storage subtree。
- Dependencies and consumers: File 3 Binder。
- Why now: wired tests 后添加安全默认。
- Contract/signature changes: false 和 []；现有 API/runtime/database/Flyway flags 保持。
- Input/output and state mapping: 开启只由 operator 精确提供 refs。
- Error and edge behavior: on 空或重复拒绝，off 不读取 V15。
- Standards impact: MC-CONFIG-001 / MC-SCOPE-001。
- Literal rule enforcement: Rule 7, Rule 11；无新增 profile。
- Implementation pseudocode:

```yaml
# Merge into the existing base AS application.yml; keep every current API, runtime, JDBC and Flyway default.
stellar:
  tianji:
    agent:
      storage:
        model-usage-enabled: false
        model-usage-cumulative-route-refs: []
```

- Verification contribution: G5 final config proof。
- After this file: 完整 feature 默认关闭。

#### File 5 — `CREATE docs/egon/evidence/2026-11-05-agent-model-usage-ledger.md`

- Purpose: 交付实际有限与兼容证据，分开运行级别。
- Symbols: T4-02 source evidence、各步 SHA/XML 和验证边界。
- Repository evidence: Source64 证据格式；本 Plan G1–G5。
- Dependencies and consumers: 操作者和后续 S12，非生产配置。
- Why now: 最后所有有限门禁真实通过后整理。
- Contract/signature changes: 只引用实际本次报告与 SHA。
- Input/output and state mapping: 说明 V15 bytes、old SQL、POM、owner/wire 和未执行 I/E/S12。
- Error and edge behavior: 缺 test/预期失败或 smoke 不等于真实验收。
- Standards impact: MC-SCOPE-001 / MC-TEST-001。
- Literal rule enforcement: Rule 11：源路径、依赖与 runtime 分级。
- Implementation pseudocode:

```sh
git diff --check
sha256sum stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V15__add_agent_model_usage.sql
verify_actual_owning_xml_and_recompute_path_hashes # from the finished finite source gate only
```

- Verification contribution: 最终证据审阅与 scope。
- After this file: AS 四路径加一证据可提交，I/E 仍由 operator。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `t4_02_finite AgentModelUsageDiagnosticsTest,TianjiAgentAccessTest,AgentRunHttpTest,HttpAgentClientTest`；最终 §8 verify。
- Expected result: reflection/typed RED 后 owning tests F=E=S=0。
- Failure returns to: File 1 fixture、File 2 IAM、File 3 source、File 4 defaults。
- Completion criteria: 四个 AS exact paths 加一份源交付证据，原接口/SQL/POM baseline；I/E 独立。
- Rollback: 关闭 feature 或部署旧制品；V15 数据保留。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnosticsTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/AgentModelUsageDiagnostics.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/TianjiAgentStorageConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-05-agent-model-usage-ledger.md`.
- Commit: `feat(agent-server): gate private model usage diagnostics`

## 8. Test, Validation, and Quality Gates

命令为后续 source GO 的待执行命令，工作目录 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。只认当前开始时间后的 owning-module XML，selected test 非零，RED F>0/E=S=0，GREEN F=E=S=0；允许上游 unrelated no-match。有限 selector 永不选择 PostgresTest；真实 I 类在 source gates 仅 testCompile。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false
t4_02_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
t4_02_finite() {
  STELLAR_VERIFY_GOAL=test ./scripts/verify-source-reactor.sh \
    -gs "$t4_02_settings" -s "$t4_02_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
    "-Dtest=$1"
}
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1 RED/GREEN | tianji-service | `t4_02_finite AgentModelUsageCaptureTest`，再加 JTokkitChatModelBudgetEstimatorTest | optional bound price | actual reflective/typed RED then finite | Step 1 | REQ-002/005，U |
| G2 RED/GREEN | tianji-service | `t4_02_finite AgentModelUsageStoreTest#usageReaderExists`，then `#finiteOwnerReadScopeAndDisabledNoV15` | V14/no schema owner read | assertion RED then finite；无 V15 | Step 2 | REQ-003/004/005，U |
| G3 RED/GREEN | tianji-service | `t4_02_finite AgentModelUsageStoreTest#requiresImmutableV15Resource+finiteModelUsageReadinessMatrix` | V15 resource and guard | no-resource / safe typed RED then finite | Step 3 | REQ-001/002/004/005，U；I 独立 |
| G4 RED/GREEN | tianji-service | `t4_02_finite AgentModelUsageCaptureTest,AgentModelUsageStoreTest#finiteUsageMutationFollowsExistingBudgetScope,AdkNativeAgentCompletionTest,AdkContextWindowPlanTest` | existing ADK/budget | typed RED then finite | Step 4 | REQ-001/002/005，U |
| G5 RED/GREEN | tianji-service | `t4_02_finite AgentModelUsageDiagnosticsTest,TianjiAgentAccessTest,AgentRunHttpTest,HttpAgentClientTest` | private authorization/default off | reflective/typed RED then finite | Step 5 | REQ-003/004/005，U |
| Final source/package | tianji-service | 下方 clean verify + old SQL/POM/wire hashes | owning modules | finite actual passed/packaged | owning Step | no runtime |
| I separately authorized | tianji-service | exact PG selectors below | isolated schema | V14 off/V15 on、atomicity and permissions | Steps 2–5 | REQ-001–005；独立 PG |
| E/S12 | future approved env | existing T3-12 E and later S12 | full chain | separate evidence | owning future scope | beyond this source slice |

```sh
STELLAR_VERIFY_GOAL=verify ./scripts/verify-source-reactor.sh \
  -gs "$t4_02_settings" -s "$t4_02_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none clean \
  '-Dtest=AgentModelUsageCaptureTest,JTokkitChatModelBudgetEstimatorTest,AgentModelUsageStoreTest#finiteOwnerReadScopeAndDisabledNoV15+requiresImmutableV15Resource+finiteModelUsageReadinessMatrix+finiteUsageMutationFollowsExistingBudgetScope,AdkNativeAgentCompletionTest,AdkContextWindowPlanTest,AgentModelUsageDiagnosticsTest,TianjiAgentAccessTest,AgentRunHttpTest,AgentRunEventStreamCapacityTest,AgentJsonCodecTest,HttpAgentClientTest'
```

I 获授权后仍用同一 wrapper/settings/JDK，经现有 `tianji.postgres.test.url` opt-in 管理隔离 PostgreSQL，精确 selector 为 `-Dtest=AgentModelUsagePostgresTest#v15MigrationOnIsolatedPostgres+immutableUsageAndBudgetOnIsolatedPostgres,AgentBudgetPostgresTest,AgentStorageVerifierPostgresTest`。新 I methods 应分别检查 V14→V15、专用角色、真实事务。当前任务仅用既有 source-reactor 和有限 test seams，无需新增 scripts/work 临时工具或部署。

## 9. Migration, Compatibility, Rollout, and Rollback

Step 2 独立提交 reader compatibility。Step 3 前确认 V14 是最新、V15 未占用、旧 SQL 哈希精确；先在仓库外完成 SQL 审阅，单独新增 `classpath:db/tianji-agent/V15__add_agent_model_usage.sql`，锁 SHA-256/Flyway CRC。仅一张表；外部既有 Flyway 执行，应用不会 DDL。

先使旧/off 可用；受控 AgentDB V15 apply/verify 后才允许已授权 AS opt-in，校验同源/role/历史。V14 继续 off。回滚关闭配置或还原旧制品，保留已提交 V15；修正用新获批版本，既有 SQL 永久不改。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Steps | Changed files / symbols | Required evidence |
| --- | --- | --- | --- |
| REQ-001 | Step 3/4 | V15, reserve/settle | finite plus independent I |
| REQ-002 | Step 1/3/4 | frozen price/observation | profile+transaction |
| REQ-003 | Step 2/5 | exact-owner bounded read/private AS | identity/UTC/cursor |
| REQ-004 | Step 2/3/5 | readers first, independent V15, default off | compatibility and readiness |
| REQ-005 | Step 1–5 | evidence and private data | actual XML / separate I/E |

## 11. Risks, Blockers, and User Decisions

| ID | Risk/blocker | Evidence/decision | Required action |
| --- | --- | --- | --- |
| R-000 | 当前 Plan awaiting review | primary Review 与 DEC-005 exact scope | Root 锁定 source GO 后执行；独立运行须另获准 |
| R-001 | 无可信 late final 入口 | 当前 close 与 Spec active CONFIG | 保留 UNKNOWN，后续另审 |
| R-002 | PG/E/S12 未获准 | source delivery 不代表 E | 独立环境和真实报告 |
| R-003 | exact correlation 查询变大 | 既有 V5 与 bounded cutoff | 独立 EXPLAIN，保持 unavailable；不擅自加索引 |
| R-004 | V15 占用或 checksum 漂移 | AGENTS immutable | 停止查明历史，禁止覆盖 |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

五步仅首切片，继续保持公开、原预算与运行语义。

### 12.2 Spec consistency

receiver、authority、价格与 V15 对齐，按 DEC-005 精确技术范围。

### 12.3 Repository executability

AR/AS 当前 factory、callback、JDBC、verifier、selector 与 source-reactor 已只读核实。源码前重新锁定 A1 后 HEAD 与精确 scope。

### 12.4 Test and release completeness

已有 runtime gates 分开，Review Plan 不声称代码、Maven、应用或数据验证结果。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/7 | exact existing profile | DEC-005 |
| MC-REUSE-001 | Applicable | PASS | §4/7 | existing AR/AS | None |
| MC-DEP-001 | Applicable | PASS | §5/7 | no dependency change | None |
| MC-NAME-001 | Applicable | PASS | §5/7 | semantic DTO/VO | None |
| MC-VALID-001 | Applicable | PASS | §7 Step 2/5 | strict current auth/technical boundaries | DEC-005 |
| MC-MODEL-001 | Applicable | PASS | §7 Step 1/4 | immutable technical data | DEC-005 |
| MC-CONVERT-001 | Applicable | PASS | §7 Step 2 | existing JDBC explicit mapping | DEC-005 |
| MC-LOG-001 | Applicable | PASS | §7 Step 5 | fixed diagnostic | DEC-005 |
| MC-BEAN-001 | Applicable | PASS | §7 Step 5 | existing named factory | DEC-005 |
| MC-UTIL-001 | Applicable | PASS | §4/7 | existing APIs | None |
| MC-JSON-001 | Applicable | PASS | §7 Step 5 | old wire kept | None |
| MC-TIME-001 | Applicable | PASS | §4.9/7 | DB UTC micros | None |
| MC-CONFIG-001 | Applicable | PASS | §7 Step 5/9 | default off | None |
| MC-PATTERN-001 | Applicable | PASS | §4/7 | existing estimator Strategy | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7 | exact paths, old SQL/POM kept | None |
| MC-TEST-001 | Applicable | PASS | §7/8 | finite/I/E separate | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | review and runtime scope explicit | DEC-005 |

### 12.6 Final verdict

PASS — Ready for user review

## Java / CQE contract review

原 MODEL reserve/settle Command、两个内部 Query、既有 Outbox Event；沿现有 owner/immutable 技术值和 UTC；DEC-005 精确技术 JDBC/显式投影，公开 JSON 不变。

## Backend generation and dependency evidence

一张 AR AgentDB 技术观测表，经既有 externally applied Flyway；业务 archetype CRUD generator 无该输入 profile。source lock 后执行，保留旧 migrations；没有新依赖或任何实际 generator/DDL 运行。
