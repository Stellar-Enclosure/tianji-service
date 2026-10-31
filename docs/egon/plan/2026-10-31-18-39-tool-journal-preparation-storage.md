# T3-07 工具凭据与动作准备的同源持久存储

| Field | Value |
| --- | --- |
| Document | `2026-10-31-18-39-tool-journal-preparation-storage.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 18:39 Asia/Shanghai` |
| Updated | `2026-10-31 18:39 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | T3-07唯一V13、共享只读verifier、capability reserve/attach/current、授权action prepare/current；不开放派发 |
| Source Requirement | 用户要求继续原plan未完成工作、分步验证提交，并允许接管未跟踪ToolActionIntentSql |
| Baseline Revision | `main@2c0fc5e9cf19c3774ad086135b0c90eb30c02f86` |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本轮用户明确授权剩余工作，源文件无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `e6a93f4bfd144066eefaa152a18523d70860c092c52a353e996c11c4e02ebb0e` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [独立工具凭据合同](2026-10-31-17-58-tool-execution-grant-binding.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

以一个逻辑提交交付工具journal的唯一加法版本和最小安全存储入口。已提交codec与ToolExecutionGrant继续复用；先保存独立工具凭据，再允许受控当前host授权下准备和读取稳定动作事实。整个步骤不签发grant、不验证真实密文、不消费审批、不写invocation、不派发工具、不改AS/ADK默认拒绝。三表schema一次定稿承载后续必要receipt关系，已有迁移字节不变；编译和受控JDBC不等于PostgreSQL运行验收。

## 2. Target Spec and Effective Design

唯一设计来源是上方原蓝图§6/7/13/14与§19 T3-07及本轮仓库核对。实际天机独立JDBC ledger与隔离Flyway history沿原蓝图和用户最新Flyway不可变约束保持；不借此切片改造迁移框架、引入依赖或新层。本文保持Review，用户对exact原计划剩余工作的执行授权有效，不能伪造原文Accepted。前序2c0fc5e已完成纯域grant合同，566a665已完成私有codec。

## 3. Effective Requirements and Acceptance

PLAN-REQ别名只追踪原文已有要求，不扩大产品范围。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07 固定工具与权限 | 凭据独立绑定完整来源、工具、后端、purpose/resource和固定run策略 | cap reserve/attach只QUEUED，current允许QUEUED/RUNNING；工具ref/backend/snapshot/allowlist/budget匹配 | ToolCapabilityStore |
| PLAN-REQ-002 | §7/13/T3-07 当前授权及fencing | prepare/read必须当前独立工具权限，取消/epoch/deadline不被旧持有者绕过 | 事务外PREPARE；双时钟10秒窗口；DB锁后实际lease重核；旧expiresAt hint可在已heartbeat续期时使用 | IntentStore/Sql/port文档 |
| PLAN-REQ-003 | §13/T3-07 actionIntent与receipt | 完整owner namespace稳定意图，重放保留原事实，漂移冲突 | 跨run同owner同intent可重放，变内容冲突；expiry不续期，过期事实仍可查询；结果无私密body | IntentSql/PreparedActionResult |
| PLAN-REQ-004 | §13/T3-07 单一tool_execution迁移 | 一次加法schema承载凭据、动作及调用receipt；旧历史不可变 | 唯一V13三表及精确列/check/key/index校验；无bearer/软删除释放/级联删除；仅预备入口可写 | V13/verifier |
| PLAN-REQ-005 | §17 S07/S10、T3-07测试与边界 | 安全/冲突/故障可验证，未执行场景真实标识 | 受控JDBC门禁/回滚/时钟/脱敏回归，optinPG已编译默认跳过；无真实授权/派发声明 | tests/README/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

同一步先只增verifier/capStore/intentStore最小声明以供测试编译，方法无功能返回；随后测试真实行为RED，再提升唯一V13、完成verifier和两个Store及SQL参与者，最后文档与证据。scaffold不独立提交。V13不可在冻结后继续扩充同一逻辑迁移；当前保存后续必要列但没有相应写入口。

### 4.2 Test-first strategy

RED必须是合法fixture后验证器未拒绝漂移、cap/reserve未返回预期事实、intent未执行应有授权/fence等行为断言，不以编译/环境问题作RED。三个受控JDBC类覆盖sameDS/verified/ambient门禁、当前callback无事务、锁后时间、epoch/replay/conflict、序列化重建与rollback。PG类仅在显式提供空loopback库时运行，默认skip可编译但不算PG通过。

### 4.3 Sequential and parallel boundaries

一个Step保持In Progress；其文件内verifier/capStore/intent实现可按明确所有权并行准备，但全体GREEN/审计后只能产生一个聚焦提交。不得提前开始invocation/approval/dispatch步骤，不修改其他工作。

### 4.4 Commit boundaries

唯一语义提交覆盖§5全部13条路径；新V13与所有者Store/verifier是不可分割schema切片。显式暂存，提交后记hash/路径再推进；不push/改历史。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| action/cap存储 | T3-07意图/当前权限/receipt | 已有ToolActionIntentSql草稿、ToolExecutionGrant和codec | 复用既有AgentLedgerService/JdbcTemplate/TransactionTemplate，不新建执行器 | Implement |
| 共享verifier | T3-07持久可靠性 | MemoryStorageVerifier/MemoryPreferenceIntentStorageVerifier | 复用contract/booleanSignature/checksum，避免两Store各复制schema守卫 | Implement |
| 单一三表 | T3-07一个新AgentDB版本 | 现有V1-V12，独立run/budget账本 | 保留唯一V13；invocation/审批列预备但不给写入口 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| V13精确schema/verifier | PLAN-REQ-004 | verifier contract/drift断言 | 最小声明、审阅SQL | verified只读门禁 | 两个Store | Step 1 |
| capability准备/存凭据 | PLAN-REQ-001 | reserve/attach/current断言 | verifier、现有grant/ledger | 独立密封证据槽事实 | 后续真实authorizer | Step 1 |
| action准备/读取 | PLAN-REQ-002/003 | 当前授权/lease/replay断言 | verifier、codec、ledger | 脱敏PreparedActionResult | 后续审批/派发 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AR/domain、application、infrastructure和蓝图§6 | 保持天机库边界 | 技术JDBC库手工构造，非新增业务Bean；无层迁移 | Step1；MC-ARCH-001、MC-BEAN-001 |
| Value/validation | ToolExecutionGrant/ActionRequest、codec | 固定域值和SQL shape边界 | 复用值校验/schema重建；PreparedActionResult是不可变事实投影，无HTTP DTO | Step1；MC-VALID-001、MC-MODEL-001 |
| JSON/time | Boot-managed Jackson、Instant、JDBC Timestamp | 不存Bearer/安全上下文 | 私密JSON只在内部codec，返回只投影；时钟见§4.9 | Step1；MC-JSON-001、MC-TIME-001 |
| Variation/pattern | 独立ToolExecutionAuthorization域port；两Store固定流程 | 已有依赖倒置/Adapter | port隔离host当前权限，SQL参与者复用现有ledger；不造State层 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| tx/schema | Spring JDBC、MemoryStores/Verifier | owned transaction、contract/booleanSignature/checksum | 足够；仅工具专属表/绑定缺口 | 复用同DS和辅助解析 | None；工具专属Store/verifier | Step1；MC-REUSE-001、MC-DEP-001 |
| 领域/JSON/摘要 | ToolExecutionGrant、ToolActionRequest、codec、OrchestrationRequestDigests | 固定来源/版本/参数、严格JSON、长度前缀hash | 足够，不复制schema/摘要框架 | 直接复用 | None | Step1；MC-VALID-001、MC-UTIL-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Store/Verifier/Result职责 | 声明→test→实现 | 明确角色后缀，投影PreparedActionResult | 编译/类型审阅 | Step1 | PASS |
| Rule 2 | §13/T3-07 | 域值/codec/SQL重建 | tests→Stores/Sql | 无新HTTP handoff；逐边界复用schema/身份/来源约束 | 正反受控JDBC | Step1 | PASS |
| Rule 3 | §13/T3-07 | 原grant值records | Sql/Result投影 | 只新增不可变事实值，无实体/Converter复制 | 构造/投影测试 | Step1 | PASS |
| Rule 4 | §6/T3-07 | MemoryStores技术JDBC库manual constructors | 新技术库 | 无业务Bean/DI新增；异常脱敏 | 源码差异 | Step1 | N/A：无业务Bean |
| Rule 5 | §6/T3-07 | JDK/Spring/既有组件 | 全部Java | 无工具库或digestUtils新增 | imports/POM差异 | Step1 | PASS |
| Rule 6 | §13/T3-07 | ToolExecutionJsonCodec | Sql→codec | 原严格Jackson闭合重建，无明文错误cause | codec/回滚测试 | Step1 | PASS |
| Rule 7 | §6/T3-07 | 无profile修改 | README/报告 | 不新增开关/配置键 | 路径检查 | Step1 | N/A：配置不变 |
| Rule 9 | §13/T3-07 | 既有授权port+JDBC Adapter | Stores/Sql | 用已有依赖倒置，固定投影直接实现，不建第二引擎 | 行为/结构审阅 | Step1 | PASS |
| Rule 10 | §7/T3-07 | Instant/DBclock/单调时钟 | tests→Stores/Sql | 微秒截断不延长、双时钟窗口、锁后DBtime | TEST-002 | Step1 | PASS |
| Rule 11 | §6/T3-07 | 原蓝图技术ledger与用户Flyway规则 | §5全部 | 单一V13、旧SQL不改，无新层/事件/依赖 | diff/checksum | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | cap ticket expiresAt/deadlineAt；intent created/updated/expires；live DB lease | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时间，无日历区域计算 | Not applicable: 无UI | DB锁后clock_timestamp重核；lease旧hint可续期，expiry固定且不向上延 | 写前truncatedTo(MICROS)，Timestamp映射；DBclock唯一持久判断 | 保持已有ledger已微秒化deadline，旧SQL不变 | Step 1；TEST-002：微秒/锁后/续期/固定expiry |
| TIME-002 | authorization Request.deadlineAt/window | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对截止窗口 | Not applicable: 无UI | min(run.deadline,Clock.now+10s)；不取旧lease hint；ALLOW proof覆盖此截止 | systemUTC或包私有Clock，返回前重核 | 原ToolExecutionAuthorization签名不变 | Step 1；TEST-002：回拨Clock仍被单调预算阻断 |
| TIME-003 | callback+owned事务总预算、query timeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed时间 | Not applicable: 无展示 | 单调10s不因wall回拨增加；DB锁等待后再核 | System.nanoTime或LongSupplier，tx10s、query5s、verifier15s | 不引新Clock框架/全局时间修改 | Step 1；TEST-002：确定nano推进而不sleep10s |

## 5. Change File Tree

下列AR路径均属于`stellar-enclosure-tianji-agent-runtime`。每条实体文件仅列一次，声明scaffold和最终实现是同一步内回访。

| Operation | Path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java | 唯一共享三表只读schema守卫 | Step1 |
| CREATE | src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java | schema/checksum/drift测试 | Step1 |
| CREATE | src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java | 凭据槽reserve/attach/current | Step1 |
| CREATE | src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java | 同源/epoch/绑定/时限测试 | Step1 |
| CREATE | src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | 接管已授权草稿，内部事务参与者 | Step1 |
| CREATE | src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java | 当前授权与owned tx/脱敏事实入口 | Step1 |
| CREATE | src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java | 授权/lease/replay/deadline/rollback测试 | Step1 |
| CREATE | src/main/resources/db/tianji-agent/V13__add_tool_execution_journal.sql | 唯一加法三表schema | Step1 |
| CREATE | src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 显式空库optin，默认skip | Step1 |
| MODIFY | src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java | 仅Javadoc澄清ALLOW窗口及lease hint | Step1 |
| MODIFY | ../README.md（仓库根README.md） | 局部能力/预置/未运行边界 | Step1 |
| CREATE | ../docs/egon/evidence/2026-10-31-tool-journal-preparation-storage.md | 实际结果、17MC/10Rules | Step1 |
| CREATE | ../docs/egon/plan/2026-10-31-18-39-tool-journal-preparation-storage.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline2c0fc5e，只有已授权SQL参与者草稿未跟踪；保留原始备份，其他仓库/SQL不动。JDK25、source-reactor及已存在Jackson/JDBC/PG测试依赖可用。审阅输入为`/tmp/tianji-tool-execution-v13-reviewed-draft.sql`与`/tmp/tianji-tool-execution-v13-reviewed-contract.json`；它们不是已执行迁移，实施将最终SQL提升为classpath唯一V13并由持久测试固定摘要/合同。三表27/43/34列、5/11/10命名CHECK；必须校验完整PK/UNIQUE/FK/index/predicate/opclass/collation/default、RLS/trigger和必要权限。ANY/numeric/168hours规范仅改善预期catalog兼容，真实pg_get_expr仍未验证。

局部澄清：source run行FOR SHARE→intent advisory→intent row顺序；cap行在source FOR SHARE后ON CONFLICT。永久技术幂等键不会软删除释放，无deleted_at生命周期。新表最终schema承载后续审批/dispatch/invocation，但本步骤没有这些写API；approval proof/audit JSON形状不证明权限。当前lease.expiresAt是claim hint，executor heartbeat不会更新Java对象；必须从锁后DB行取live expiry，再在返回前DB时钟重核，禁止把旧hint作为授权deadline。callback必须无事务、有界，并保证ALLOW有效票据窗口覆盖Request.deadlineAt，否则UNAVAILABLE。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 持久化独立工具凭据和当前授权动作准备事实

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 2c0fc5e/566a665及现有ledger/MemoryStore/codec；无其他未提交步骤。
- Baseline state: domain和codec存在；参与者为授权草稿，schema/verifier/owned Stores缺失。
- Observable outcome: 只有verified同源存储和当前有效来源可保存/返回独立凭据及准备事实，重放不扩大权限或延长expiry。
- End state: V13和最小存储slice可静态/受控验证；invocation/审批消费/dispatch/真实authorizer/ADK仍未开放。
- Test-first gate: Required — 最小声明scaffold只供编译，合法fixture后断言行为RED，再完成同文件；不把编译或默认PG skip算RED/GREEN。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java`

- Purpose: Compile prerequisite only; no readiness behavior before RED.
- Symbols: ToolExecutionStorageVerifier(DataSource), verify(), migration()/contract helpers.
- Repository evidence: Existing MemoryPreferenceIntentStorageVerifier owns read-only verification, never provisions schema.
- Dependencies and consumers: DataSource and existing MemoryStorageVerifier parser/checksum helpers; both new Stores consume it.
- Why now: The focused tests need typed declarations, not reflection or a compilation failure.
- Contract/signature changes: Declare the exact constructor/verify/helper signatures; no schema success decision yet.
- Input/output and state mapping: Keep methods inert/default for compiling tests; final implementation belongs to File9.
- Error and edge behavior: No DDL, repair, startup scan or ready flag in the scaffold.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
public final class ToolExecutionStorageVerifier {
    // Declare DataSource constructor, verify(), migration() and the test-visible contract entry points.
    // Return compile-only defaults; do not claim readiness or implement schema behavior before RED.
    // Final behavior is completed after the focused verifier tests in this same Step.
}
```

- Verification contribution: Only supplies compilation prerequisites; File4 observes real missing validation.
- After this file: Compiles without functionality; no standalone scaffold commit.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java`

- Purpose: Compile prerequisite for independent tool capability persistence.
- Symbols: Constructor(DataSource,AgentLedgerService); verifyStorage/usesDataSource/usesLedger; reserve/attach/current.
- Repository evidence: AgentServiceExecutionGrantStore and MemoryPreferenceIntentStore have manual technical-library constructors.
- Dependencies and consumers: Existing BindingVO/ReservationVO/EvidenceVO and ToolDescriptor; no issuer or HTTP dependency.
- Why now: Typed capability tests must compile before behavior is added.
- Contract/signature changes: reserve(BindingVO,ToolDescriptor):Optional<ReservationVO>; attach(EvidenceVO,ToolDescriptor):boolean; current(BindingVO,ToolDescriptor):Optional<EvidenceVO>.
- Input/output and state mapping: Compile-only defaults; source and slot checks are deferred until File10.
- Error and edge behavior: No grant signing, ALLOW decision, callbacks, ambient transaction joining or Beans.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
public final class ToolCapabilityStore {
    // Declare the exact DataSource/ledger constructor and readiness/identity methods.
    // Declare reserve(binding,descriptor), attach(evidence,descriptor), current(binding,descriptor).
    // Use compile-only empty/false defaults until focused behavior RED; do not commit this scaffold.
}
```

- Verification contribution: File5 reserve/current/guard assertions expose missing behavior.
- After this file: Tests can compile; no operational capability yet.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java`

- Purpose: Compile prerequisite for current-authorized intent facts.
- Symbols: Public(DataSource,ledger,ToolExecutionAuthorization); package-private Clock/LongSupplier ctor; prepare/current; PreparedActionResult.
- Repository evidence: MemoryPreferenceIntentStore owns short transactions; ToolExecutionAuthorization is the existing host port.
- Dependencies and consumers: Run/lease/action/descriptor, existing codec and participant; no AS/ADK dependency.
- Why now: Tests need stable signatures and immutable output before behavior RED.
- Contract/signature changes: prepare(run,lease,action,descriptor,requestedExpiry):PreparedActionResult; current(run,lease,action,descriptor):Optional<PreparedActionResult>; verifyStorage/usesDataSource/usesLedger.
- Input/output and state mapping: PreparedActionResult(intentId,requestSha256,toolRef,risk,state String,revision,originRunId,createdAt,expiresAt); no body/ciphertext.
- Error and edge behavior: No review-by-ID endpoint, execution permit or approval-consumption result.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
public final class ToolActionIntentStore {
    // Declare public constructor plus package-private Clock and LongSupplier nanoTime injection.
    // Declare prepare/current and the immutable PreparedActionResult metadata projection.
    // Return compile-only null/empty defaults until real authorization/fencing tests produce RED.
}
```

- Verification contribution: File6 tests observe missing facts/current authorization without fixture initialization failure.
- After this file: Typed test entry points compile, functionality remains absent.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java`

- Purpose: Define frozen three-table contract and read-only failure guards first.
- Symbols: Migration SHA/frozen earlier versions; column/check/key/index/drift/ambient tests.
- Repository evidence: MemoryPreferenceIntentStorageVerifierTest tests exact SHA and booleanSignature preserving grouped AND/OR.
- Dependencies and consumers: File1 declarations; draft SQL fallback only until final resource exists; no live PostgreSQL.
- Why now: This is verifier RED before implementing schema readiness.
- Contract/signature changes: No external contract; use local fixture input only, never mark a draft as an applied migration.
- Input/output and state mapping: Assert capability/intent/invocation counts 27/43/34 and 5/11/10 checks; C collations/defaults/keys/FKs/indices and closed groups.
- Error and edge behavior: Mutated namespace literals, weakened boolean grouping/default/index, invalid history/RLS/trigger must fail; ambient tx touches no DS.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void finalized_contract_is_exact_and_drift_is_not_ready() {
    raw = finalResourceOrExplicitReviewDraft(); expected = contract(raw);
    assertColumnAndCheckCounts(expected, 27,5, 43,11, 34,10);
    assertNotEquals(signature(originalGroupedCheck), signature(weakenedOrCheck));
    assertUnverifiedAfterHistoryCatalogOrPrivilegeFailure(); assertAmbientTransactionTouchesNoDataSource();
}
```

- Verification contribution: TEST-001 verifier RED/GREEN and TEST-003 static schema fidelity.
- After this file: Tests compile and fail because exact validation/readiness is not implemented.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java`

- Purpose: Define queued issuance, epoch attachment and current evidence boundaries.
- Symbols: sameDS/verified/ambient; queued reserve/attach; current queued/running; binding drift/old epoch/expiry/rollback.
- Repository evidence: MemoryPreferenceIntentStoreTest local source/templates/result/values helpers plus real Spring transaction binding.
- Dependencies and consumers: File2 scaffold and actual BindingVO/EvidenceVO constructors; controlled JDBC only.
- Why now: This is capability behavior RED before final Store implementation.
- Contract/signature changes: No protocol changes; local test fixtures are not authenticated grants.
- Input/output and state mapping: Match full run material/tool ref/backend/allowed versions/maxToolCalls; same slot differs binding -> conflict, stale issue epoch -> no mutation.
- Error and edge behavior: Wrong namespace/descriptor/source tuple, canceled/expired source, late ticket/lock clock, mismatched DS/unverified/ambient must fail without writes.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void immutable_slot_and_issued_epoch_bound_evidence() {
    store = verifiedStoreWithControlledJdbc(); reservation = store.reserve(binding,descriptor).orElseThrow();
    assertStableSlotAndOriginalBinding(reservation); assertNoWriteForChangedBindingOrOldEpoch();
    attachMatchingTicketAndReadCurrentForQueuedThenRunningSource();
    assertNoEvidenceAfterDatabaseExpiryOrSourceDrift(); verifyOwnedCommitOrRollbackAndNoSecretErrors();
}
```

- Verification contribution: TEST-001 capability real RED then GREEN; TEST-002 time/epoch/source transitions.
- After this file: Assertions expose empty scaffold/missing guards rather than compilation errors.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java`

- Purpose: Define independent current authorization, real live lease fencing, replay and private projection.
- Symbols: prepare/current authority; wall/monotonic window; expired hint with refreshed live lease; lock-after expiry/cancel; idempotency/rollback.
- Repository evidence: Existing controlled JdbcTemplate constructors and actual DataSourceTransactionManager/Connection helpers.
- Dependencies and consumers: File3 scaffold, existing participant draft/codec/domain; fixed Clock and LongSupplier prevent sleeps.
- Why now: This fixes safety behavior before source or intent writes exist.
- Contract/signature changes: No real IAM; authorizer lambda must assert no active transaction and exact PREPARE request.
- Input/output and state mapping: parametersSha256=action.requestSha256; deadline=min(run deadline,Clock+10s), independent monotonic10s; full row/share/advisory/intent lock ordering.
- Error and edge behavior: DENY/UNAVAILABLE/throw touches no journal; stale epoch fails; old expiresAt hint succeeds only if locked DB lease is live; clocks expiring after waits forbid returning facts.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void current_authority_and_locked_live_lease_fence_preparation() {
    authorizer = request -> { assertNoTransaction(); assertExactActionAndDeadline(request); return ALLOW; };
    assertPrepareAndCurrentReturnOnlyMetadataAndPreserveFirstExpiry();
    assertExpiredJavaLeaseHintSucceedsWithSameEpochAndLiveHeartbeatRow();
    assertNoWriteForStaleEpochOrClockExpiryAfterAdvisoryLock();
    advanceNanoTimeTenSecondsAndMoveWallClockBack(); assertUnavailableBeforeJournalAccess();
    assertCrossRunSameOwnerReplayAndChangedBodyConflict(); verifyConnectionRollbackForInjectedInsertFailure();
}
```

- Verification contribution: TEST-001 intent RED/GREEN; TEST-002 deterministic live lease/double-clock edges.
- After this file: Tests compile and show missing behavior, while no PostgreSQL correctness claim is made.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`

- Purpose: Durably encode opt-in real schema/transaction/concurrency checks without starting a database.
- Symbols: Explicit property tianji.tool.journal.postgres.test.url; V12-to-V13/frozen history/catalog/rollback/replay/fencing scenarios.
- Repository evidence: Existing Agent/Memory opt-in tests require empty loopback tianji_it_* database and refuse unsafe URLs/nonempty schemas.
- Dependencies and consumers: Existing PostgreSQL/Flyway test dependencies only; operator supplies disposable DB.
- Why now: Define remaining real-database proof obligations in the same schema slice.
- Contract/signature changes: Missing URL property -> skip; invalid/nonempty/unsafe URL -> reject; explicit URL also requires the preinstalled vector extension patch via tianji.tool.journal.postgres.vector.version, as in the existing Memory fixture; no automatic service/database startup.
- Input/output and state mapping: Apply unchanged earlier migrations and new V13 only inside supplied fixture; actual stores exercise queued ticket and running intent paths.
- Error and edge behavior: Run schema drift, late epoch, concurrent exact replays, changed payload, namespace, current lease and failure rollback; do not fake completion if unavailable.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void opt_in_tool_journal_contract_and_atomicity() {
    url = System.getProperty("tianji.tool.journal.postgres.test.url"); assumeExplicitEmptyLoopbackDatabase(url);
    provisionImmutableEarlierVersionsThenV13OnlyInDisposableFixture(); verifyThreeTableCatalogAndChecksums();
    exerciseCapabilityEpochAndPrepareReplayConflictScopeAndRollback();
    proveLiveLeaseHeartbeatAndLockedDeadlineSemanticsIfDatabaseWasProvided(); // otherwise record skip, not PASS
}
```

- Verification contribution: TEST-004 compiles by default and explicitly skips without supplied DB; no runtime evidence inferred.
- After this file: Real SQL proof suite exists; execution remains user-controlled and unverified by default.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V13__add_tool_execution_journal.sql`

- Purpose: Promote the reviewed final schema as the single new immutable V13.
- Symbols: agent_tool_capability, agent_tool_action_intent, agent_tool_invocation; cycle backlink and five secondary indexes.
- Repository evidence: Reviewed SQL/contract files, existing V1-V12 and strict MemoryStorageVerifier parser.
- Dependencies and consumers: No old table modifications; new FK references existing agent_run and agent_budget_reservation.
- Why now: Tests now fix the schema; freeze it before Store/verifier final implementation.
- Contract/signature changes: 27/43/34 columns; 5/11/10 named CHECK; all exact PK/UNIQUE/FK/index/default/collation; only ALTER creates new-table cycle backlink.
- Input/output and state mapping: Logical action SHA=Tianji v1 namespace+owner.identityFingerprint+actionKey+businessKey+businessVersion; unique short hash avoids long UTF8 index tuples; compare both hash and full identities.
- Error and edge behavior: No cascades/soft-delete release/bearers; receipt proof shape is not authenticity; invocation AWAITING_APPROVAL is finished with NONE/zero dispatch and action remains pending.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
-- Copy reviewed SQL bodies into the sole V13 resource; replace mutable-review headers with nonautomatic-provisioning instructions.
-- Keep 27/43/34 columns, 5/11/10 grouped checks, C owner_type/collations and exact three-table relationships.
-- Keep outcomes inside CREATE, only the new-table cyclic FK in ALTER, ANY membership, numeric casts and 168-hour interval.
-- Preserve all V1-V12 bytes; freeze new resource SHA in durable tests. Do not execute SQL or claim pg_get_expr was verified.
```

- Verification contribution: TEST-001 static contract/frozen SHA and TEST-004 future actual PostgreSQL verification.
- After this file: The single classpath version is fixed; later phases must not edit it to add their behavior.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java`

- Purpose: Finish shared read-only readiness against exact provisioned history and all three catalogs.
- Symbols: verify, migration, expected contract, columns/checks/keys/index/privileges checks.
- Repository evidence: Existing MemoryStorageVerifier.contract/booleanSignature/checksum; MemoryPreferenceIntentStorageVerifier catalog query patterns.
- Dependencies and consumers: Final V13 plus actual DataSource; both Stores must complete this before ready.
- Why now: Real verifier RED is established and final SQL is now fixed.
- Contract/signature changes: Own15s REPEATABLE_READ primary transaction with setReadOnly(false), performing read-only queries, queryTimeout5; reject ambient; never provision or repair schema.
- Input/output and state mapping: Require UTF8 primary/origin, exact known V1-V13 history/checksum/script; exact table type/default/column/check grouping/validated/enforced constraints/key/index/opclass/collation/RLS/trigger/needed privileges.
- Error and edge behavior: Missing/duplicate/changed catalog rows/history, disabled triggers or unexpected defaults/indexes fail closed; failed reverification resets Store readiness.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
verify(): reject ambient transaction; parse final V13 contract and exact tables;
ownedRepeatableReadTransaction(timeout15s,readOnly=false): require primary UTF8 origin; execute only queries and transaction-local search_path; set bounded query timeout;
require one successful SQL type/script/raw CRC per known V1-V13 version and no failed history; V10 CRC uses raw placeholder SQL; compare all new-table columns/defaults/check signatures/keys/indexes/opclasses/collations;
require safe table kinds, enforced validated constraints, RLS/trigger and required privileges; throw sanitized dependency error on drift;
never execute DDL, update history or set a Store verified flag before the entire check returns successfully.
```

- Verification contribution: TEST-001 verifier GREEN; true catalog rendering remains TEST-004 opt-in only.
- After this file: Both Stores can share a single strict read-only schema contract.

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java`

- Purpose: Finish exact-source capability reserve/attach/current in short owned transactions.
- Symbols: verifyStorage/identity APIs; reserve(BindingVO,ToolDescriptor), attach(EvidenceVO,ToolDescriptor), current(BindingVO,ToolDescriptor).
- Repository evidence: MemoryPreferenceIntentStore owned READ_COMMITTED transactions and immutable reservation checks.
- Dependencies and consumers: Final shared verifier, ToolExecutionGrant, ledger.executionMaterial and strict tool descriptor.
- Why now: Schema readiness and behavior RED now exist.
- Contract/signature changes: Same exact DataSource as ledger; own10s READ_COMMITTED/queryTimeout5; reject ambient; reserve/attach QUEUED, current QUEUED/RUNNING.
- Input/output and state mapping: Source row FOR SHARE then capability slot lock/INSERT ON CONFLICT; reconstruct BindingVO/ref/backend/source/deadline; snapshot tools/allowlist/maxToolCalls must match; issueEpoch fences attach.
- Error and edge behavior: Stable slot changed binding conflicts; null/old/expired or mismatched ticket cannot attach; expiry truncated MICROS and checked with DBclock after locks and before return; no grant issuance/authentication.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
reserve(binding,descriptor): require verified/sameDS/owned tx; lock exact uncancelled QUEUED source and verify current material/tool policy;
lock or insert stable capability slot; compare complete immutable binding; reserve issue epoch without minting another slot on drift;
attach(evidence,descriptor): lock source then slot, require matching binding/epoch and typed8or9 ticket; truncate expiry MICROS; DBclock checks source and ticket;
current(binding,descriptor): allow exact QUEUED/RUNNING source, return only matching currently live sealed evidence after DBclock recheck;
on any persistence failure roll back and sanitize; no invocation, approval or dispatch writes.
```

- Verification contribution: TEST-001 capability GREEN; TEST-002 lock-time/epoch/source/wire confidentiality boundaries.
- After this file: Only structurally sealed evidence persistence works, not actual cryptographic authority.

#### File 11 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`

- Purpose: Complete authorized draft as an internal transaction participant with actual live lease and full identity checks.
- Symbols: prepare/read/requireRun; row reconstruction/logicalIdentitySha256 and fixed state representation.
- Repository evidence: Existing authorized draft already owns run-share/advisory/intent locks and codec reconstruction.
- Dependencies and consumers: Owning Store must verify storage and bind same DataSource transaction; ledger/codec remain reused.
- Why now: Owned source/authorization behavior is fixed before internal persistence is completed.
- Contract/signature changes: No independent public transaction/startup entry; rows follow final V13 shape; state may project FAILED_KNOWN but no new state transition API.
- Input/output and state mapping: Lock full owner RUNNING source FOR SHARE -> intent advisory -> intent row; select locked actual lease_expires_at; after waits and before return compare clock_timestamp, owner/epoch/cancel/run deadline and snapshot/tool policy.
- Error and edge behavior: Ignore stale Java expiry hint when actual sameepoch lease is live; changed logical/request/identity/tool conflicts; first expiry<=7 elapsed days, fixed on replay; never auto-EXPIRE in read.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
requireRun(): lock full namespace RUNNING row FOR SHARE, selecting live lease expiry and cancellation/deadline/owner/epoch;
recheck actual DBclock after lock; verify ledger authorization, definition tool ref/allowlist/maxToolCalls and approvalPolicyRef;
prepare(): advisory lock stable intent, recheck source, read locked owner row; compare complete logical SHA/action UUID/identity/request/descriptor;
insert only PREPARED or AWAITING_APPROVAL with NONE side effects, revision1 and truncated fixed expiry<=created+168hours;
read(): reconstruct closed descriptor/arguments/action and all full hashes; preserve old expiry and project no private body; recheck locked source/time before returning.
```

- Verification contribution: TEST-001 intent replay/conflict/live lease/rollback GREEN with final owning Store.
- After this file: Participant only works inside verified owned Store transaction; no execution/approval/dispatch entry is exposed.

#### File 12 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java`

- Purpose: Finish current host authorization and safe metadata prepare/current.
- Symbols: Clock/nanoTime constructors, authorizeRequest, prepare/current, PreparedActionResult projection, readiness identity methods.
- Repository evidence: Existing ToolExecutionAuthorization, technical owned Stores and final participant.
- Dependencies and consumers: Verified sameDS ledger and shared verifier; callback implementation supplied by host, not model input.
- Why now: RED defines authority/time/facts and SQL participant now enforces source fencing.
- Contract/signature changes: Public ctor uses systemUTC/System.nanoTime; packageprivate Clock/LongSupplier for deterministic tests; own10s READ_COMMITTED, queryTimeout5; no ambient transaction.
- Input/output and state mapping: PREPARE request binds run/lease/tool/backend/action.requestSHA/intent and min(run deadline,wall now+10s); callback outside tx; wall and monotonic10s checked before DB and returning; actual lease from DB, not hint.
- Error and edge behavior: DENY distinguished, UNAVAILABLE/throw/expired proof fail closed; no DB during callback; ALLOW must cover full Request.deadline; prepare/current return immutable metadata only, old facts never become permits or automatic retries.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
prepare/current(): reject unverified storage, wrongDS or ambient transaction; validate exact action owner/ref/descriptor;
start wall+monotonic budget; request = independent PREPARE(run,lease,tool,backend,action.requestSha256,intent,min(run.deadline,Clock.now+10s));
call bounded host.authorize outside all DB transactions; require ALLOW proof window covers request.deadline, otherwise deny/unavailable;
recheck both clocks; own transaction calls participant with fixed authority deadline and locked actual lease; before commit/return recheck clocks;
project PreparedActionResult(intentId,requestSha256,toolRef,risk,state,revision,originRunId,createdAt,expiresAt); never return body, descriptor or cipher.
```

- Verification contribution: TEST-001 intent GREEN; TEST-002 wall rollback/mono expiry/live heartbeat/locked expiry.
- After this file: Current-authorized preparation facts are durable; no live dispatcher or current cryptographic verifier is activated.

#### File 13 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java`

- Purpose: Clarify existing host port validity and lease-hint contract without changing signatures.
- Symbols: ToolExecutionAuthorization class/Request Javadoc only.
- Repository evidence: Existing port says current tool authority is separate from CLAIM/MODEL and must run outside transactions.
- Dependencies and consumers: Host-supplied authorizer plus Store current handoff; no new DTO or method.
- Why now: Final storage gate needs a precise deadline obligation for host ALLOW.
- Contract/signature changes: No signature/enum/behavior change: document exact independent tool permission and ALLOW valid through Request.deadlineAt, otherwise UNAVAILABLE.
- Input/output and state mapping: Document lease.expiresAt as original claim hint that may be renewed in DB; Store owns locked actual epoch/owner/live expiry validation.
- Error and edge behavior: Do not promise constructor authenticates source or uses stale hint as DB truth.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
Document ALLOW as the exact independent tool decision whose verified ticket/window covers Request.deadlineAt.
Document insufficient evidence or coverage as UNAVAILABLE, and calls as bounded and outside database transactions.
Document AgentRunLease.expiresAt as a possibly old claim hint; actual live expiry/owner/epoch are rechecked under the Store DB lock.
```

- Verification contribution: TEST-003 Javadoc/signature diff and controlled authorizer assertions.
- After this file: Host obligations match Store windows without public API expansion.

#### File 14 — `MODIFY README.md`

- Purpose: Describe provisioned storage slice and runtime boundaries faithfully.
- Symbols: Existing T3-07 codec/grant progress paragraphs and test instructions.
- Repository evidence: Current README distinguishes metadata contracts from real authority/execution.
- Dependencies and consumers: Actual final code and tests only.
- Why now: Describe only after nonruntime GREEN and diff review.
- Contract/signature changes: Document independent preprovisioned V13/read-only verifier, typed capability storage, PREPARE/current facts and opt-in test property.
- Input/output and state mapping: State only cap/intent preparation is exposed as library seam; original expiry preserved; no secrets in projections.
- Error and edge behavior: Explicitly retain SQL/PG catalog/crypto/IAM/approval/dispatch/ADK unverified or unimplemented; no auto schema/startup.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
Append the exact V13 and shared-verifier provisioning prerequisites and existing immutable-history boundary.
Explain queued capability reserve/attach, queued/running current evidence, and independently authorized running intent prepare/read.
Give the opt-in PG property and actual default skip; state no real grant issuer, crypto verifier, approval consumer, invocation writer or dispatcher is enabled.
```

- Verification contribution: TEST-003 implementation-to-documentation audit.
- After this file: Reader can distinguish safe stored facts from operational tool permission.

#### File 15 — `CREATE docs/egon/evidence/2026-10-31-tool-journal-preparation-storage.md`

- Purpose: Persist actual test/skip/migration freeze and all manual gates for this one slice.
- Symbols: Baseline2c0fc5e, final V13 SHA, RED/GREEN outputs, 17MC and10Rule tables, requirement status.
- Repository evidence: Earlier codec/grant evidence and skill per-Step audit contract.
- Dependencies and consumers: Final diff/outputs/staged scope; no generated runtime claims.
- Why now: Facts can only be filled after validation.
- Contract/signature changes: No product contract; metadata facts and runtime gaps stated separately.
- Input/output and state mapping: Each requirement/check maps exact path/symbol/test result; full T3-07 remains Partial and PG remains unverified when skipped.
- Error and edge behavior: Record failed/retried commands honestly; no private JSON/cipher output; dispose of any scratch harness, never stage /tmp files.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; apply only touched concerns, no Bean/dependency/config expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; technical-library/value/schema scope, separate evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
Record original baseline/authorized draft backup and frozen V1-V12/newV13 hashes with exact commands and outcomes.
Record focused real RED and final GREEN, opt-in PG compile/skip, 17 separate MC rows and10 separate Rule rows with evidence.
Map slice requirements to implementation and mark actual PostgreSQL/catalog/concurrency/crypto/IAM/dispatch acceptance unverified or pending.
Confirm no undetermined scripts/work harness, automatic runtime, push or unrelated change; commit only the declared paths.
```

- Verification contribution: TEST-003 final scoped audit and actual evidence; no prefilled execution pass.
- After this file: All nonruntime slice gates have evidence before explicit staging/commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolExecutionStorageVerifierTest,ToolCapabilityStoreTest,ToolActionIntentStoreTest,ToolExecutionJournalPostgresTest,ToolExecutionGrantTest,ToolExecutionJsonCodecTest,ToolExecutionContractTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: real focused behavior RED first; final named nonruntime tests GREEN, actual counts recorded; absent opt-in PG properties mean explicit skip, never PG acceptance.
- Failure returns to: owning test fixture/scaffold for invalid RED, exact Store/verifier/schema file for behavioral failures; environment/dependency failures reported independently.
- Completion criteria: all slice requirements have actual nonruntime proof, all17MC/10Rules separately PASS or evidenced N/A, immutable old migrations and exact staged scope; PG/runtime gaps explicitly retained.
- Rollback: stop consuming the new library seams; revert only this semantic commit if unapplied. If V13 has been externally applied, preserve it/history and require a later corrective version; never edit/repair/drop applied schema.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V13__add_tool_execution_journal.sql`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java`, `README.md`, `docs/egon/evidence/2026-10-31-tool-journal-preparation-storage.md`, `docs/egon/plan/2026-10-31-18-39-tool-journal-preparation-storage.md`
- Commit: `feat(agent): persist tool capabilities and authorized action preparation`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 source-reactor command | 三个新unit类及原工具合同/codec | 真行为RED后GREEN，数量以实际报告为准 | 当前实现/fixture | 全部；受控JDBC/纯域 |
| TEST-002 时间/事务 | tianji-service | 同命令的lease/Clock/nanoTime/rollback测试 | 锁后live expiry、旧hint续期、wall回拨/mono10s、微秒/原expiry | 无sleep10s，过期窗口绝不进入/返回journal | Store/Sql | PLAN-REQ-001/002/003；受控非PG |
| TEST-003 静态/人工 | tianji-service | `git diff --check`；旧SQL SHA比对；逐行diff/stage；17MC/10Rule | 唯一V13和§5路径 | oldSQL字节不变、无秘密/越界、检查有证据 | 所属文件 | 全部；静态 |
| TEST-004 optinPG | tianji-service | 同命令选择ToolExecutionJournalPostgresTest；仅显式传`-Dtianji.tool.journal.postgres.test.url=jdbc:postgresql://127.0.0.1:PORT/tianji_it_NAME`时可运行 | 自备空loopback库 | 默认编译/skip；提供合法库后才有真实目录/锁/SQL事务结果 | schema/Store | 真PG未运行不得计通过；不自动建/启DB |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-18-39-tool-journal-preparation-storage.md --strict` | metadata/文件序/追踪 | exit0 | 本文 | 文档结构不等于实现验收 |

只使用既有source-reactor/JUnit命令；不创建仓库scripts/work harness。外部审阅草案/机读合同只用于本步骤设计，最终持久事实是V13与仓库测试。临时文档生成脚本应在交付后删除，不提交到仓库。当前步骤不自动执行PG、不连接真实IAM/模型/MCP或启动长期进程。

## 9. Migration, Compatibility, Rollout, and Rollback

唯一新版本为`stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V13__add_tool_execution_journal.sql`。移除mutable review头，保留非自动预置和源码测试不等于运行证据的说明。旧V1-V12及所有其他仓库迁移保持字节不变；原独立JDBC/Flyway历史兼容，不引入MP/另一迁移框架/manifest。用户后续显式预置后，Store只读verify V13及完整catalog；未预置则默认不可用。没有启动迁移、schema修复、production写入或运行开关。

三表永久技术键不会因软删除释放；UNIQUE和审批收据索引属于幂等事实，不是假设可重建的业务实体key。schema的proof/audit/outcome约束只约束表示，不能替代可信来源、fresh权限或合法CAS；真实消费方以后必须复核。AWAITING_APPROVAL调用已结束但动作仍等待，UNKNOWN不授重派。未使用功能可回退消费者；已应用V13不能修改/删除/repair，需后续纠正版本。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07 | Step 1 | ToolCapabilityStore/Test、shared verifier | TEST-001/002/003 | 完整来源/descriptor/快照与epoch/expiry |
| PLAN-REQ-002 | §7/13/T3-07 | Step 1 | IntentStore/Test、Sql、port Javadoc | TEST-001/002/003 | 无tx当前PREPARE、双时钟、锁后actuallease |
| PLAN-REQ-003 | §13/T3-07 | Step 1 | IntentSql/Store/Test、V13 | TEST-001/002 | 完整owner/逻辑SHA重建、固定expiry、私密投影 |
| PLAN-REQ-004 | §13/T3-07 | Step 1 | V13、Verifier/Test、PG test | TEST-001/003/004 | 唯一加法schema和精确检查；PG另行证明 |
| PLAN-REQ-005 | §17 S07/S10/T3-07 | Step 1 | 三unit类/PG类、README/plan/evidence | TEST-001/002/003/004/005 | 真实结果/skip和未完成边界，不把局部当整项 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 原蓝图现有JDBC技术ledger与独立Flyway历史 | V13/Stores | 原蓝图及用户最新迁移不可变/新版本规则 | 主代理 | Closed：保持exact技术选型，不自行更换框架 |
| DEC-002 | 授权接管未跟踪Sql草稿 | ToolActionIntentSql | 用户明确允许完善并提交，原件先保留副本 | 主代理 | Closed：仅当前Step所有者修改 |
| RISK-001 | static canonical ANY/numeric/interval不是实际pg_get_expr | Verifier/V13 | 未连接PG，本测试默认skip | 用户/主代理 | Tracked：保留真实SQL/目录/锁验收待办 |
| RISK-002 | host ALLOW必须覆盖请求截止，构造值不能认证 | port/IntentStore | 现有authorizer仅Decision，不虚构签发/decisionId | 宿主集成方 | Tracked：当前port文档及受控callback合同；真实fresh Expectation以后接 |
| RISK-003 | 三表中后续receipt列尚无写入口 | V13 | 本步骤范围只cap和prepare/current | 主代理 | Tracked：审批消费/invocation/dispatch分步承接，不标T3-07完成 |

## 12. Review and Acceptance

本计划忠于原T3-07并保留用户保护现有工作、单一逻辑提交、旧迁移不可变及用户自行启动runtime的要求。schema/verifier/两个Store是一项同源存储逻辑切片，没有新网络交互、权限授予、业务引擎或抽象平台。scaffold只在同一步内支持可编译真实RED，最终提交不能含未完成功能骨架。前序提交保留不改写，后续执行只有此步骤commit并记录结果后才能开始。

### 12.5 Blocking Manual Check

以下是计划级可执行性审查，实施后必须在报告按实际源码/命令逐行重评。schema/PG真实验证未执行是显式运行边界，不能伪装为实际验证已通过。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原蓝图§6、既有AR技术JDBC library、§5路径 | 保持exact天机模块和同源技术账本，无新层/框架 | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 ledger、grant/codec、MemoryStores/verifier | 复用Spring/既有域/摘要/解析能力 | None |
| MC-DEP-001 | Applicable | PASS | §5无POM/build修改 | 无新依赖或工具引入 | None |
| MC-NAME-001 | Applicable | PASS | Store/Verifier/Test/PreparedActionResult | 职责明确，不新建模糊carrier | None |
| MC-VALID-001 | Applicable | PASS | 全部域重建/codec/schema/来源校验及正反测试 | 无新HTTP DTO；各现有私有表示/SQL handoff有具体验证 | None |
| MC-MODEL-001 | Applicable | PASS | 既有不可变grant/action、不可变projection；永久技术key | 非可变业务实体/软删除key；不复制领域模型 | None |
| MC-CONVERT-001 | Not applicable | N/A | §5无Converter或跨层DTO映射 | SQL行重建事实、codec表示不是绕过MapStruct的对象转换层 | None |
| MC-LOG-001 | Not applicable | N/A | 手工构造技术JDBC库，不增加业务服务/日志 | 沿现有MemoryStore职责，错误脱敏且不记录私密参数 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Spring Bean/注入配置变化 | 技术库显式source/ledger构造与原模式一致，DI规则无新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Spring/既有digest与contract解析 | 不新增utility库或重复摘要框架 | None |
| MC-JSON-001 | Applicable | PASS | ToolExecutionJsonCodec、闭合schema重建、private projection | 单Jackson栈，参数不回显/不当作授权 | None |
| MC-TIME-001 | Applicable | PASS | §4.9三组时间、TEST-002 | 微秒不向上延、实际DBlease、wall/mono双窗口具体可测 | None |
| MC-CONFIG-001 | Not applicable | N/A | §5无profile/properties/开关 | 配置键集合和默认工具拒绝不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 既有ToolExecutionAuthorization依赖倒置、JDBC Adapter/participant | 复用真实变化点，固定投影直接处理，不建第二引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | §5及显式commit paths、唯一V13 | 只当前存储slice，旧SQL/其他仓库/运行入口不动 | None |
| MC-TEST-001 | Applicable | PASS | §8真实RED/GREEN/静态/optin命令 | 实际结果进报告，默认skip不当PG通过 | None |
| MC-BLOCKER-001 | Applicable | PASS | §11范围/运行边界与各独立行 | 当前文件/API/schema设计已锁定，运行证明诚实保留 | None |

### 12.6 Final verdict

PASS — Ready for user review
