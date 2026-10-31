# T3-07 工具调用准备、可信审计与同事务预算

| Field | Value |
| --- | --- |
| Document | `2026-10-31-22-01-tool-invocation-preparation.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 22:01 Asia/Shanghai` |
| Updated | `2026-10-31 22:22 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | invocation准备/预算/严格audit持久表示；无dispatch/审批消费/native/SQL变更 |
| Source Requirement | 用户继续原plan未完成工作、分步验证提交；本轮明确内部延续授权 |
| Baseline Revision | `main@431dfb1f7d33ecf3c0704a8cb0d694ca4352500e`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户本轮明确授权剩余工作，原文无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `dc980761d6c60b0dfb803b0f3e4c26edf3f8b509314df7ddf138a6ff66a23eb8` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [预算锁序前置纠正](2026-10-31-22-06-budget-lock-order.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

已有独立工具权限/票据/动作意图，尚未把可信PREPARE审计、每次调用事实与原run工具预算原子提交。此切片新增显式未接线invocation准备库，复用冻结V13和原预算服务，不调用外部业务、不派发工具、不消费审批、不改native guard或配置。主代理/API审阅已确认精确合同；严格计划通过后才发生产GO。

## 2. Target Spec and Effective Design

主设计为原蓝图§7短事务/CAS/fencing、§12工具预算与幂等/未知副作用、§13可信权限/审计/预算、§19 T3-07。前序56f1cce已接同步owner签发和实际run授权链，当前431dfb1已完成budget run-first前置纠正；本次只补调用准备事实与预算，不把PREPARED当执行成功。共享同DataSource的owned10秒READ_COMMITTED事务，权限check在事务外；lock顺序run FOR UPDATE→capability→intent→invocation/budget。沿现有技术账本/Flyway不可变选择，V13字节不变。

## 3. Effective Requirements and Acceptance

PLAN-REQ为现有设计要求的局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/13/T3-07权限与fence | 必须真实typed check且Audit非null/ALLOW后才准备，事务内再核actualrun/cap来源 | 不把旧authorize-only ALLOW当permit；run/epoch/owner/cancel/deadline、独立cap与audit一致，事务外check | 新InvocationStore/Sql |
| PLAN-REQ-002 | §12/13/T3-07预算与receipt | 调用事实、可信audit和原TOOL预算同事务、幂等不重占 | host UUID→tool:UUID预算ID；同调用精确重放仅一inv/预算；失败回滚所有变化 | Sql/旧ledger.budgets复用 |
| PLAN-REQ-003 | §7/12/T3-07风险状态 | READ_ONLY可PREPARED；等待审批不得占执行资源或伪造副作用；旧终态/UNKNOWN禁止新调用 | AWAITING产生finished空typed outcome并settle(0,0,0)；动作terminal/dispatched/unknown/expired仅返回旧fact，不新inv/预算 | Store/Sql/旧intent参与者 |
| PLAN-REQ-004 | §13/T3-07审计秘密 | audit严格闭合十一键、4KiB、UTC Z、类型/空值明确且错误不泄秘密 | 读写都拒unknown/duplicate/coercion/noncanonical time/missing keys，保留typed版本槽，无cause/正文 | 旧codec/Test |
| PLAN-REQ-005 | §17/T3-07验证/边界 | 当前事实和预算有受控/opt-in证据，保持scope及native关闭 | 新unit和旧cap/codec回归；现有PG类加20同UUID及trigger回滚helper，默认compile/skip不当PG通过 | 测试/README/plan/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

最小编译声明之后各组件独立真RED→GREEN。严格codec先固定typed审计表示；capability仅加同事务内部参与入口，公共owned方法保持；新Store事务外check，Sql在已verified同源事务按固定顺序准备/复用intent、预算和invocation。最后扩既有opt-in PG helper、说明及报告，一个逻辑commit。精确新API与状态/窗口/幂等规则已锁在§6/7，不先于RED写功能。

### 4.2 Test-first strategy

正反验证typed check缺失/不匹配、capability参与者sameDS/actual transaction、run live fence与锁后时间、canonical参数/稳定UUID/重放内容冲突、TOOL预算一次、等待零结算、动作旧fact不新增、insert失败回滚及私密错误。codec使用真实AuditVO/JavaTimeModule、十一键/4KiB/严格UTC Z与原descriptor/outcome回归。所有RED必须缺失行为而非编译/fixture错误。

### 4.3 Sequential and parallel boundaries

只有一个Step；codec/cap参与者和新Store/Sql可在API锁定后按所有权独立RED→GREEN，全体最终一个commit。无dispatch/approvalconsume/AR native接线/AS配置/SQL或旧预算服务重构。

### 4.4 Commit boundaries

新增/修改12个唯一路径为同事务调用准备逻辑切片；显式stage、保留旧工作/历史、不push。严格锁定并由主代理核对后才作为生产GO。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| invocation+budget准备 | §7/12/13/T3-07 | 冻结V13已有invocation表，原AgentBudgetService可参与现有事务 | 新工具专用Store/Sql，不复制预算/执行器 | Implement |
| cap同事务入口 | §7同源原子性 | 旧cap.current拥有独立事务且cap read FOR UPDATE | package-private受verified同DS交易约束的参与方法 | Implement |
| auditcodec | §13审计与秘密 | 已有typedAuditVO及严格私有codec、已有JavaTime依赖 | 扩原codec，不新JSON层/库 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 闭合audit/cap参与 | PLAN-REQ-001/004 | codec与参与者负例 | 原Audit/Grant/codec/Store | 私有同事务/表示能力 | 新调用Store | Step 1 |
| 调用与预算原子准备 | PLAN-REQ-001/002/003 | 新Store受控事务/幂等/风险测试 | 最终API、原ledger.budgets/intent、上项 | 未派发invocation事实 | 后续真实执行 | Step 1 |
| 持久运行测试定义 | PLAN-REQ-005 | 既有PG helper编译/可复跑 | 现有自备空库URL/vector前置 | 并发/联合回滚测试 | 用户运行验收 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AR/infrastructure/persistence技术库、ledger.budgets、原V13 | 保持原边界和独立账本 | 新工具专用库/Sql；不改预算服务或造执行引擎 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Model/validation | typed Audit/Grant/ActionRequest及预算值 | 可信值严格匹配 | 新嵌套request/result不可变，primitive参数schema归一，metadata不认证 | Step1；MC-VALID-001、MC-MODEL-001 |
| JSON/time | Boot-managed Jackson/已存在JavaTimeModule、Instant/DB时间 | 闭合4KiB UTC审计 | 扩现有codec、canonical UTC Z、固定错误，无新库 | Step1；MC-JSON-001、MC-TIME-001 |
| Bean/pattern | 技术JDBC库manual构造、现有权限port依赖倒置 | 复用Adapter/transaction participant | 无新Bean/DI/config；预算同源加入，不建第二状态机 | Step1；MC-BEAN-001、MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 工具预算 | AgentBudgetService.reserveBudget/settleBudget | 无model context路径使用原TransactionTemplate，可参与owned ambient事务 | 足够，不改原预算API | 复用TOOL/known-zero settlement | None | Step1；MC-REUSE-001 |
| cap/intent | 原cap.current/read与ToolActionIntentSql | 锁source→cap、稳定intent/metadata已有 | 公共owned cap不能嵌套调用 | 只加package-private verified同DS参与入口 | None | Step1；MC-VALID-001 |
| audit JSON | 原codec、AuditVO、jackson-datatype-jsr310既有依赖 | 不缺框架，仅缺严格十一键表示 | 原codec扩展，不新schema/SDK | 复用JavaTimeModule和既有Jackson | None | Step1；MC-DEP-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Store/Sql/VO/Result/Test角色 | 声明→test→实现 | 嵌套不可变值有角色后缀；旧API不破坏 | 编译/声明审阅 | Step1 | PASS |
| Rule 2 | §13/T3-07 | 域/schema/SDK audit边界 | codec/cap/Store及tests | 每次check/audit/来源/参数/预算/fence精确匹配；无新HTTP传输模型 | 正反受控测试 | Step1 | PASS |
| Rule 3 | §13/T3-07 | 原值/新嵌套不可变records | 新Store | primitive map归一/防御复制，非实体/Converter | 构造/投影测试 | Step1 | PASS |
| Rule 4 | §6/T3-07 | 既有技术JDBC库manual构造 | Store/Sql/cap | 无新业务Bean/DI，私密错误脱敏 | 源码审阅 | Step1 | N/A：技术库无业务Bean |
| Rule 5 | §6/T3-07 | JDK/Spring/Jackson/原组件 | 全Java | 无工具库或新依赖 | POM/import审阅 | Step1 | PASS |
| Rule 6 | §13/T3-07 | 原严格Jackson/已存在JavaTime | codec/tests | exact十一键/类型/null/4KiB/Z/uuid、无cause；旧格式保留 | codec回归 | Step1 | PASS |
| Rule 7 | §6/T3-07 | 不改配置 | 文档 | 无新key/flag | diff | Step1 | N/A：配置不变 |
| Rule 9 | §7/13/T3-07 | 原权限port/transaction participant/预算 | Store/Sql | 依赖倒置+同源参与者，不造调度/执行器 | 原子性/顺序测试 | Step1 | PASS |
| Rule 10 | §7/13/T3-07 | Instant/DBclock/mono | codec/Store | UTC Z、微秒不向上延、locked live fence/等待结束时刻 | 时间边界测试 | Step1 | PASS |
| Rule 11 | §6/T3-07 | 原AR技术账本/V13固定 | 12路径 | 不改SQL/AS/native/配置/权限/deps | diff/冻结SQL | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | audit checkedAt/expiresAt | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时间 | Not applicable: 无UI | exact字段必须canonical UTC Z文本，拒numeric/offset/local；匹配请求/主体/窗口 | 原JavaTimeModule；不改原V13列 | 原AuditVO结构和十一键SQL约束保持 | Step 1；TEST-002：codec/覆盖/过期边界 |
| TIME-002 | invocation created/dispatch_not_after/finished、run live lease/deadline | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时间 | Not applicable: 无UI | actual DB锁后重核，等待无external effect即finished；原fact expiry不延 | DB clock_timestamp，既有微秒语义；不以旧Java hint作live值 | 既有source/预算/intent合同不变 | Step 1；TEST-002：锁后/等待/重放deadline |
| TIME-003 | prepare overall budget/owned tx/query timeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无展示 | 同一bounded window跨外部check与owned transaction，PREPARE整体窗口至多10s，读取E1后可按其expiry缩短但不得重置entry wall/nano | 既有Clock/nanoTime模式，RC tx10s | 无新增Clock/timeout框架 | Step 1；TEST-002：wall/mono/事务超时 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | 未接线invocation准备owner、嵌套值和owned事务 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | package-private同事务参与者 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java | 新owner/参与者/budget受控正反回归 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java | package-private currentInTransaction，不改公共owned行为 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java | 同DS/verified/transaction参与约束 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java | strict audit十一键/4KiB/UTC | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java | audit闭合/时间/秘密及原格式兼容 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | 原子prepareWithStatus结果，旧方法委托保持语义 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 原同URL/vector前置增加fresh run并发/trigger回滚helper | Step1 |
| MODIFY | README.md | 当前准备与native/真实环境边界 | Step1 |
| CREATE | docs/egon/evidence/2026-10-31-tool-invocation-preparation.md | 实际命令/17MC/10Rule/未验收 | Step1 |
| CREATE | docs/egon/plan/2026-10-31-22-01-tool-invocation-preparation.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline431dfb1，实施前clean；用户AGENTS为当前规则，其他工作保留。V13 SHA仍`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`，所有SQL/AS/config/native/permission/POM不改。没有独立SqlTest新文件；新Store测试覆盖内部Sql。旧PG类继续使用同一显式loopback空库URL/vector固定patch前置，不启动DB，默认仅编译/skip。新helper用独立fresh run（QUEUED cap reserve/attach→claim）避免复用旧测试已过期run；20个相同UUID准备核一inv/预算/计数和固定deadline，payload漂移冲突，注入inv insert失败核同事务intent+budget+counters回滚。

最终公共API：ToolInvocationStore(source,ledger,capabilities,authorization)，package-private另加Clock/LongSupplier；verifyStorage独立flag，usesDataSource/usesLedger/usesCapabilities/usesAuthorization；prepare(PreparationRequestVO):PreparationResult。request为不可变(binding,lease,descriptor,args,nullable action,UUID invocationId,nullable BigDecimal estimatedCost,nullable Instant intentExpiry)，run只来自binding.run。按descriptor.input归一args，write重建Action并核owner/ref/policy/args；READ_ONLY必须action/expiry=null，write必须两者非null且args精确一致。host UUID决定reservationId=tool:+UUID。

所有调用parametersSha256统一为OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_INVOCATION_PARAMETERS_V1",descriptor.key(),Long.toString(descriptor.version()),descriptor.reference().contentSha256()),normalizedArgs)，明确不同于action.requestSha256及其既有namespace。PreparationResult含nullable invocation与nullable PreparedActionResult，至少一个；READ_ONLY只invocation，write新建或同inv精确重放均带两者。PreparedInvocationResult十字段为runId、UUID invocationId、reservationId、state、revision、capabilityIssueEpoch、createdAt、dispatchNotAfter、finishedAt、newlyCreated；不含参数/cipher/audit payload。

外层entry window≤10s覆盖cap.current E1、独立authorization.check(PREPARE)、owned RC事务；E1取得后可缩到E1 expiry，但依据最初entry wall/nano计算，不能重置。check必须echo exact Request/boundary/decision、Audit非null/ALLOW，grant匹配E1、checkedAt在entry startedAt..当前wall、覆盖短请求截止且audit expiry≤E1/E2 expiry。随后owned≤10s事务按run FOR UPDATE→cap E2→intent→invocation/budget顺序，E1.equals(E2)含cipher/epoch，复核actual lease/cancel/deadline/snapshot/policy，事务内无RPC。

ToolActionIntentSql新增package-private prepareWithStatus(...):PreparationResultVO(Row row,boolean newlyCreated)，既有prepare委托.row，current及旧语义不变；status必须来自同advisory/row锁内的实际INSERT，禁止预读竞态。先读/验证exact现存invocation再重放（不重新reserve/改audit/deadline）。无inv且write status=false时，所有既有action状态都只返回原metadata事实，包括pending/AWAITING/terminal/UNKNOWN/DISPATCHING/expired，不新增inv或budget。新read/idempotent为PREPARED保留预算；新APPROVAL intent产生finished AWAITING_APPROVAL、空typed outcome/actualCost0，并在同事务settleBudget(0L,0L,ZERO)。新reserveBudget必须newlyCreated=true，旧孤立budget不得retrofit。

dispatchNotAfter由可信effectivePolicy.toolTimeout导出，取min(DBcreatedAt+toolTimeout,run.deadline,E2.expiresAt,write action.expiresAt)，写前截MICROS，必须>createdAt且>插入时DBnow；replay不重算/延长。短PREPARE audit只覆盖当前准备窗口，不承诺覆盖dispatchNotAfter，未来fresh DISPATCH check才覆盖真正调用截止。本步骤invocation写/重放parser只接受PREPARED/AWAITING_APPROVAL，dispatchEpoch0且nonce/executor/dispatched/receipt/error全null，完整严格核audit/cap/budget/outcome；未知后续state failclosed。既有ACTION仍可返回所有状态metadata，不建立推测的全状态inv parser。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 同事务准备工具调用、可信审计与预算

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 431dfb1预算run-first纠正已提交；前序typed Audit/PDP、cap/intent、严格codec和冻结V13。
- Baseline state: 尚无invocation生产writer；公共cap.current拥有独立事务；原intent prepare无newlyCreated状态；audit无严格持久表示。
- Observable outcome: 真实非null Audit ALLOW与E1/E2一致才可在单事务产生新inv+原TOOL预算；重放/旧action只返回固定事实。
- End state: 未接线prepare库及持久测试可用；仅PREPARED/AA parser，无dispatch/approval消费/native/AS/config/SQL改动。
- Test-first gate: Required — 新Store/Sql/value、codec/helper、intent status只加最小可编译声明，再各组件真实RED后完成；所有正向结果先assertNotNull，编译/fixture错误不算RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java`

- Purpose: Specify closed canonical audit serialization without changing old descriptor/outcome contracts.
- Symbols: audit round-trip/11key/4096byte/UTC/type/null/enum/UUID/private error tests.
- Repository evidence: Existing strict codec tests and actual ToolExecutionAuthorization.AuditVO.
- Dependencies and consumers: Compile-only audit overload declarations; existing JavaTimeModule dependency.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: audit(AuditVO):String and audit(String):AuditVO only, private persisted representation.
- Input/output and state mapping: Require all11 exact keys even nullable refusal slots, canonical UTC Z Instant/UUID, typed integral/enum/string/null values, size4KiB.
- Error and edge behavior: Unknown/duplicate/missing/trailing/coercion/offset/date-number and private cause reject; preserve existing128KiB descriptor/args/outcome behavior.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void audit_is_closed_bounded_canonical_and_private_on_failure() {
    json = codec.audit(validTypedUserServiceOrNegativeAudit()); assertNotNull(json); assertExactElevenKeysAndCanonicalUtc();
    assertEquals(original,codec.audit(json));
    rejectUnknownDuplicateMissingTrailingCoercionOversizeOffsetNumericTimeAndNoncanonicalUuid(); assertFixedMessageAndNullCause();
    keepOriginalDescriptorArgumentsOutcomeAssertionsUnchanged();
}
```

- Verification contribution: TEST-001 codec true RED/GREEN; TEST-002 strict time/type/secret compatibility.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java`

- Purpose: Specify package-private same-transaction participation while preserving the public owned API.
- Symbols: currentInTransaction verified/sameDS/active resource/order/unchanged public current tests.
- Repository evidence: Existing controlled JDBC and actual Spring DataSourceTransactionManager fixtures.
- Dependencies and consumers: Compile-only currentInTransaction(binding,descriptor) declaration.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Only a package-private entry; public current remains an owned transaction method.
- Input/output and state mapping: Require verified readiness plus actual transaction bound to this DataSource; lock/reconstruct exact source/cap evidence including cipher and epoch.
- Error and edge behavior: No ambient transaction or foreign DataSource binding fails before SQL; no early commit/nested owned transaction, failed reverification unavailable.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void internal_current_joins_only_the_verified_exact_source_transaction() {
    rejectWithoutActualTransactionOrWithOnlyAnotherDataSourceBound();
    inOwnedSameSourceTransaction(() -> assertExactEvidenceWithNoEarlyCommit());
    verifySourceThenCapabilityLockAndCompleteBindingReconstruction();
    assertPublicCurrentStillOwnsItsTransactionAndOriginalNegativeCasesRemain();
}
```

- Verification contribution: TEST-001 cap participant RED/GREEN and existing source/epoch regression.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`

- Purpose: Define real typed proof, atomic budget/invocation and replay/waiting boundaries before implementation.
- Symbols: prepare request/result, E1/audit/E2, scoped live fence, parameter SHA, exact replay/conflict, action-only, waiting zero settlement, rollback.
- Repository evidence: Existing ToolActionIntentStore/CapabilityStore controlled JDBC and budget/context transaction tests.
- Dependencies and consumers: Minimal new Store/Sql/value signatures and intent status declaration; real domain values/Clock/nanoTime, no database.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: PreparationRequestVO and ten-field PreparedInvocationResult as section6; no independent SqlTest file.
- Input/output and state mapping: Check outside transaction, capE1 then exact nonnull audited ALLOW; locked E2 equalsE1; fixed parameter SHA distinct actionSHA; existing inv replays before any reserve; old action all states yields facts only.
- Error and edge behavior: Reject invalid proof/args/owner/cipher/epoch/time/cancel, orphan budget/newlyCreatedfalse, unsupported invocation state/dispatch fields, incomplete persisted audit/budget/outcome; rollback intent+budget+inv on failure.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void typed_authority_and_atomic_preparation_never_create_duplicate_or_unapproved_work() {
    result = store.prepare(validHostRequest()); assertNotNull(result); assertExactlyOneToolBudgetAndPreparedInvocation();
    assertAuthorizationOutsideTransactionAndEntryWindowShrinksToE1WithoutRestart();
    assertE1EqualsLockedE2AndFullRunFenceAfterLocks();
    assertSameUuidReplayPreservesMetadataAuditDeadlineAndBudgetExceptNewlyCreatedFalse();
    assertExistingActionFactsAllStatesCreateNoInvocationOrBudget();
    assertNewApprovalWaitFinishesTypedEmptyOutcomeAndSettlesKnownZeroAtomically();
    assertPolicyToolTimeoutDeadlineBoundedByRunEvidenceActionAndDbInsertionTime();
    assertOrphanBudgetCannotBeRetrofittedAndAnyInsertFailureRollsBackAllChanges();
}
```

- Verification contribution: TEST-001 Store/Sql actual behavior RED/GREEN; TEST-002 windows/immutable deadlines/state/budget constraints.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java`

- Purpose: Implement the strict audit overloads using the existing managed Jackson/JavaTime stack.
- Symbols: audit(AuditVO), audit(String), MAX_AUDIT_BYTES4096, closed audit keys/type/canonical validation.
- Repository evidence: Existing private strict mapper and fixed sanitized invalid() boundary.
- Dependencies and consumers: Existing jackson-datatype-jsr310, AuditVO; no dependency/POM change.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Register JavaTimeModule and textual Instant serialization; preserve old signatures and 128KiB limit for other formats.
- Input/output and state mapping: Audit write/read both require exact11 present keys, canonical UUID/Z Instants and JSON types/null groups; construct actual AuditVO for original constraints.
- Error and edge behavior: Never use permissive record defaults to accept omitted null fields or implicit scalar coercion; no body/cause in errors.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
mapper: register existing JavaTimeModule and use textual timestamps, keep strict duplicate/unknown/trailing/coercion settings;
audit(value/json): require valid UTF8 and<=4096bytes; exact11 keys with every required nullable slot present;
validate primitive types, canonical UUID and Instant.toString-compatible UTC Z texts, then construct/serialize actual AuditVO;
map every parse/prevalidation failure to the existing fixed sanitized exception; keep other codec limits/contracts unchanged;
```

- Verification contribution: TEST-001 codec GREEN including all old formats.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java`

- Purpose: Add a verified same-source transaction participant by reusing existing current logic.
- Symbols: package-private currentInTransaction(ToolExecutionGrant.BindingVO,ToolDescriptor).
- Repository evidence: Public current uses transact then source share/capability update locks and exact reconstruction.
- Dependencies and consumers: Existing source/ledger/readiness flag, actual TSM resource, original helpers.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Require actual transaction bound to the same DataSource; no new transaction/commit or public API.
- Input/output and state mapping: Move shared current-body checks into participant, public current delegates inside its owned transact; NewStore already holds run UPDATE then capability lock.
- Error and edge behavior: Wrong/absent resource/unverified fails; preserve original public owned/ambient-reject semantics and complete binding/cipher/epoch/expiry checks.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
currentInTransaction(binding,descriptor): requireDescriptor; require verified && actualTransaction && hasResource(source);
reuse existing lockSource/read/same/currentTime logic and return only currently live exact evidence;
recheck readiness/time before returning; never start or commit a transaction here;
public current(binding,descriptor): keep original owned transact and call this participant inside it;
```

- Verification contribution: TEST-001 participant/public compatibility GREEN.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`

- Purpose: Expose trustworthy creation status from the same advisory/row-locked prepare operation.
- Symbols: prepareWithStatus(...):PreparationResultVO(Row row,boolean newlyCreated); original prepare delegation.
- Repository evidence: Original participant already serializes stable action insertion/replay and checks full identity/expiry.
- Dependencies and consumers: Existing signatures/run authorityDeadline/codec/owner source; no new transaction or external authorization.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Package-private status method plus immutable result; original prepare returns prepareWithStatus(...).row(), current unchanged.
- Input/output and state mapping: Existing row -> false; actual successful first INSERT under the same locks -> true; retain exact source/hash/identity/replay checks.
- Error and edge behavior: No pre-read creation guess, no change to oldprepare/current facts or first expiry; failed insert cannot report new status.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
prepareWithStatus(args): execute the original scoped/advisory/row-locked prepare algorithm;
return PreparationResultVO(existingValidatedRow,false) on replay;
return PreparationResultVO(insertedAndRevalidatedRow,true) only after actual INSERT succeeds;
prepare(args): return prepareWithStatus(args).row(); keep current and all old behavior unchanged;
```

- Verification contribution: TEST-001 Store waiting/old-action/cross-run status proof plus oldIntentStore regression.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`

- Purpose: Implement private exact invocation parsing/insertion and original budget participation.
- Symbols: Same-transaction run/cap/intent/invocation/budget source helpers, prepare/read, immutable internal row.
- Repository evidence: Frozen V13 invocation columns/constraints and existing budget service after431dfb1 run-first fix.
- Dependencies and consumers: Verified owned Store transaction, cap participant, intent status, ledger.budgets, strict audit/outcome codecs.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Private technical participant only; current writer/parser supports PREPARED and AWAITING_APPROVAL, no dispatch transition.
- Input/output and state mapping: Run FOR UPDATE actual lease/owner/epoch/cancel/deadline/snapshot policy; capE2; intent status; exact inv read; old action facts; new TOOL reservation tool:UUID; inv INSERT and postDB checks.
- Error and edge behavior: Existing inv exact replay never reserves/recomputes/audit-overwrites; changed immutable input/cost/lease/cap fields conflict. New reserve must newlyCreatedtrue; orphan rejects. Unsupported invocation state/dispatchEpoch/nonce/executor/dispatched/receipt/error fail closed; old ACTION all states remain facts.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
within exact source transaction: lock actual RUNNING run FOR UPDATE, recheck captured live lease/epoch/owner/cancel/time and immutable policy;
lock/read capE2 and require fullE1 equality; write calls intent.prepareWithStatus; read/validate existing invocation and budget first, replay exact facts if found;
if no inv and write status.newlyCreated=false return only original action metadata, no new budget/inv;
otherwise reserveBudget kindTOOL/toolVersion/zero tokens/tool:UUID and require newlyCreated=true;
created=DBnow; deadline=truncateMicros(min(created+effectivePolicy.toolTimeout,run.deadline,E2.expiresAt,writeAction.expiresAt)); require deadline>created and currentDBnow;
insert PREPARED with RESERVED budget, or finished AWAITING_APPROVAL typedemptyoutcome/cost0 then settleBudget(0L,0L,ZERO) in same outer transaction;
parse full stored fields/audit/cap/budget/outcome strictly; perform final source/time checks; no business call or dispatch write;
```

- Verification contribution: TEST-001 atomic preparation/replay/risk/rollback GREEN; TEST-004 true PG only if explicitly supplied.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`

- Purpose: Own bounded external checked authority and one same-source preparation transaction.
- Symbols: Constructors, verifyStorage/usesDataSource/Ledger/Capabilities/Authorization, prepare, immutable nested request/results.
- Repository evidence: Existing owned technical stores, ToolExecutionAuthorization.check typed result, original grant/budget/domain values.
- Dependencies and consumers: Ctor(source,ledger,capabilities,authorization); package Clock/LongSupplier; shared verifier and strict exact affinity.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: prepare(PreparationRequestVO):PreparationResult; exact nested fields in section6; no AS/config registration.
- Input/output and state mapping: Normalize schema args/reconstruct write action, distinct invocation parameterSHA, host UUID/budgetID; entry wall+mono<=10s includes E1read and typed check before owned RC transaction.
- Error and edge behavior: Require own verified flag and exact affinity, no ambient RPC; after E1 shrink from original start to expiry, never reset. Audit echo/full fields/grant/checkedAt started..now/short deadline/expiry<=E1/E2; legacy/noAudit/DENY/unknown cannot prepare.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
prepare(request): validate immutable normalized args/action/descriptor/binding/run/lease/cost and compute independent INVOCATION_PARAMETERS_V1 hash;
start original wall+mono<=10s window; cap.current E1 outside transaction; shrink deadline/nano from original entry to E1expiry if necessary;
call authorization.check(exact Request,PREPARE) outside every DB transaction; require exact echo, nonnull Audit ALLOW/grant/time/coverage;
own <=10s READ_COMMITTED transaction: delegate private Sql in fixed run->cap->intent->inv/budget order and require E1==E2;
return PreparationResult with at least one metadata-only result; replays set newlyCreatedfalse and keep all first audit/deadline values;
verifyStorage owns its flag and shared schema verification; cap readiness remains independently required; no secret body/cipher/audit payload in results;
```

- Verification contribution: TEST-001/002 typed authority/source/budget/metadata GREEN; all old callers retain behavior.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`

- Purpose: Extend the existing explicit opt-in fixture with fresh-run invocation atomicity checks.
- Symbols: Fresh scoped run helper, twenty sameUUID prepares, drift conflict, trigger-failure rollback.
- Repository evidence: Existing class requires tianji.tool.journal.postgres.test.url and pinned preinstalled vector patch.
- Dependencies and consumers: Same disposable empty loopback DB; new Store and typed controlled authority, no new infrastructure/property.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Add helper before old run expiry or use a separate fresh run, retaining old tests/safety checks.
- Input/output and state mapping: Fresh run -> QUEUED capreserve/attach -> claim ->20sameUUID prepares; assert1inv/1budget/exactcounters, same replaydeadline with newlyCreated ignored; changed content conflicts; failed inv insertion rolls back intent/budget/counters/inv.
- Error and edge behavior: Do not reuse an already expired original run, start PG, change migration bytes or treat defaultskip as passed SQL/concurrency.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
helper(): create fresh scoped run, reserve/attach cap whileQUEUED, then claim and use audited controlled authority;
prepare same hostUUID concurrently20times and assert one invocation/budget/counters plus fixed replay deadline;
assert changed immutable content conflicts; install fixture-only failing insert trigger after readiness;
new invocation insert failure must leave no new intent/budget/inv/counter changes in a real rollback; cleanup fixture trigger;
without original explicit URL/vector patch, compile and skip rather than starting any database;
```

- Verification contribution: TEST-004 opt-in live SQL/concurrency definition, default compile/skip only.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 10 — `MODIFY README.md`

- Purpose: Describe unconnected invocation facts, budgets and audit representation accurately.
- Symbols: Existing T3-07 progress/storage/owner chain notes.
- Repository evidence: README distinguishes actual configured owner chain from native/dispatch boundaries.
- Dependencies and consumers: Actual final code/tests only.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: No new flags, endpoints or migration.
- Input/output and state mapping: Explain distinct parameterSHA, typed proof/E1E2, atomic budget/prepared or wait settlement, firstmetadata replay and oldaction facts.
- Error and edge behavior: Prepared facts are not permits; PREPARE short proof does not cover toolTimeout deadline; future freshDISPATCH required and current native/dispatch remain closed.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Append current invocation preparation and atomic budget/audit facts, including action-only replay and waiting known-zero settlement.
Explain trusted toolTimeout deadline bounded by run/cap/action and never extended on replay; PREPARE proof is not dispatch permission.
Retain no dispatch/approval consumption/native wiring and unexecuted real PG/IAM/crypto acceptance; no configuration or SQL activation.
```

- Verification contribution: TEST-003 documentation/source fidelity review.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

#### File 11 — `CREATE docs/egon/evidence/2026-10-31-tool-invocation-preparation.md`

- Purpose: Record actual component RED/GREEN, atomicity scope and all standards/runtime limits.
- Symbols: Baseline431dfb1,12paths, exact commands/results,17MC/10Rule, local aliases and fullT3-07Partial.
- Repository evidence: Prior persistent per-Step evidence and execution gates.
- Dependencies and consumers: Final source/diff/test outputs; no prefilled passes.
- Why now: Tests first define true missing behavior; final production portion completes only after its owning component RED. Compile-only signatures may precede tests as section7 specifies.
- Contract/signature changes: Pure evidence document, no product contract.
- Input/output and state mapping: Trace actual typed audit/cap/budget/intent/inv rules, old compatibility and PG compile/skip; record pending native/dispatch proof.
- Error and edge behavior: No logs/payload/cipher leakage, speculative all-state parser, runtime claim from controlled JDBC, or orphan script.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; technical JDBC/value/strict representation only, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve immutable values/role names, exact source/proof/schema validation, Jackson/UTC and original budget participant; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Record baseline431dfb1, actual valid RED and final GREEN commands/counts/exits/skips and exact12path diff.
Evaluate17 Manual Checks and10 Literal Rules independently with source/test evidence and valid N/A.
Record frozenV13, original budgets reused, PREPARED/AA-only parser and all unexecuted real PG/IAM/crypto/native/dispatch boundaries.
Confirm no scripts/work harness or automatic runtime/push; actual commit only after final root review and explicit staging.
```

- Verification contribution: TEST-003 final actual audit/commit scope, no presumed runtime acceptance.
- After this file: Its declared proof is ready for final focused validation; no later dispatch/native capability is enabled.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolInvocationStoreTest,ToolCapabilityStoreTest,ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolExecutionStorageVerifierTest,ToolExecutionJournalPostgresTest,AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,ToolExecutionContractTest,ToolExecutionGrantTest,TianjiToolGrantAuthorizerTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: valid focused behavior RED before owning functionality, then named nonruntime tests GREEN; old opt-in PG compiles/skips without explicit URL/vector patch, actual counts reported.
- Failure returns to: owning fixture for invalid RED, exact codec/cap/intent/Store/Sql for behavior; no speculative schema/native/config or budget rework.
- Completion criteria: five local aliases have source/test proof,12paths only and unchangedV13;17MC/10Rules separately PASS or evidenced N/A; real PG/authority/dispatch gaps remain explicit.
- Rollback: stop consuming the new unregistered library; explicit revert of this slice if needed, preserve stored facts/schema/history and do not turn fact-only replay into retry permission.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolCapabilityStore.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`, `README.md`, `docs/egon/evidence/2026-10-31-tool-invocation-preparation.md`, `docs/egon/plan/2026-10-31-22-01-tool-invocation-preparation.md`
- Commit: `feat(agent): prepare tool invocations with atomic budgets and checked audit`

## 8. Test, Validation, and Quality Gates

TEST-001：§7 source-reactor选择各owner独立真RED，再完整选定codec/cap/intent/inv/budget/PDP兼容组GREEN；最小声明不是功能实现，正向先assertNotNull避免fixture NPE误当RED。TEST-002：同组确定Clock/nanoTime/DBtime、Audit短窗口、E1/E2全证据、trusted toolTimeout和run/cap/action最小deadline、精确重放、AA known-zero、孤立预算/插入回滚测试。

TEST-003：`git diff --check`、12路径/无SQL配置依赖/native/AS改动、V13固定SHA、17MC/10Rule逐行审阅。TEST-004：旧ToolExecutionJournalPostgresTest按既有显式URL及预装vector patch条件运行；新增fresh run helper定义20同UUID一inv/预算/counters、drift及inv-trigger失败联合回滚，无输入只编译/skip，不自动启动PG。TEST-005：工作区根运行`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-22-01-tool-invocation-preparation.md --strict`。

使用既有source-reactor/JUnit，不建仓库scripts/work harness；临时文档生成脚本完成后删除。受控JDBC/调用顺序不证明真实SQL/锁/事务，默认PG skip不计为运行通过。

## 9. Migration, Compatibility, Rollout, and Rollback

没有新SQL/schema/配置/依赖/AS/native/权限/endpoint；V13及旧迁移不可变，新writer消费已存在三表。旧cap.public current仍own transaction，旧intent prepare/current语义保持。现有audit之外descriptor/args/outcome的128KiB闭合格式保留；新增audit4KiB只有精确十一键，不放宽旧codec。

新库未注册/接native，PREPARED是持久事实而非dispatch permit；PREPARE audit只覆盖短准备窗口，未来必须fresh DISPATCH覆盖真实call截止。仅PREPARED/AA invocation可写/重放；后续state在此parserfailclosed。原ACTION所有状态仍可返回旧metadata，不能因此重试或创建新预算。AA结束当前调用且zero-settle，不占外部调用资源；未消费任何审批receipt。

回退停止新库消费并显式revert当前提交，保留已有事实/预算/历史，不删表、不将UNKNOWN当零成本、不自动重派。预算前置431dfb1保留，不因回退此步骤撤销其锁序纠正。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/13/T3-07 | Step 1 | InvocationStore/Sql/Test、cap participant | TEST-001/002 | actual Audit非nullALLOW、E1/check/E2、actual run fence及同源TX |
| PLAN-REQ-002 | §12/13/T3-07 | Step 1 | Sql/Store/Test、PG helper | TEST-001/002/004 | 一hostUUID一budget/inv、newlyCreated强约束、同事务rollback |
| PLAN-REQ-003 | §7/12/T3-07 | Step 1 | intent status/Invocation Sql/Test | TEST-001/002 | oldaction facts-only、新AA finished/zero-settle、PREPARED holds原预算 |
| PLAN-REQ-004 | §13/T3-07 | Step 1 | 原codec/Test、新Store/Sql | TEST-001/002 | 十一键/4KiB/UTC Z/无coercion和私密cause |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | 全tests/README/plan/evidence、12路径 | TEST-003/004/005 | 精确范围、既有兼容、PG未跑明确保留 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 预算run-first纠正必须先提交 | 新Store预算交互 | 前置431dfb1已提交且八路径验证完成 | 主代理 | Closed：当前baseline已更新，不改预算代码 |
| DEC-002 | 精确新request/result/原子intent status | Store/Sql/intent | 主代理/API agent最终确认：参数SHA独立域、ten-field结果、status来自实际锁内INSERT | 主代理 | Closed：§6/7精确合同无未决签名 |
| DEC-003 | prepare与dispatch窗口分离、最小deadline与有限parser | Store/Sql | trusted toolTimeout，run/cap/action截止；短Audit；只PREPARED/AA | 主代理 | Closed：不强制所有工具10s，不预建全state/dispatch逻辑 |
| RISK-001 | typed metadata不等于真实权限，PG未执行 | 后续运行验收 | 只复用可信port和受控JDBC；现有optin默认skip | 用户/主代理 | Tracked：真实IAM/crypto/SQL/锁/回滚验收另行，不启动环境 |
| RISK-002 | 未接线prepare不能宣称工具执行完成 | README/后续步骤 | 无native/AS/config/dispatch/approval消费 | 主代理 | Tracked：完整T3-07及平台仍Partial |

## 12. Review and Acceptance

原设计要求的调用事实/预算/审计同事务以现有数据表、预算服务和可信port实现；不复制预算、引擎或消息平台。所有新API/invariants已锁定，12路径、声明→独立真RED→实现→回归→一个commit明确。已有行为仅增加内部参与/status接口且旧公共语义保持；真实权限/PG/native/dispatch未验收仍须报告，不能将准备事实当业务成功或重试授权。

### 12.5 Blocking Manual Check

以下为计划级可执行性结论，实际source/命令结果在报告分别重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence技术库、12路径、现有三表 | exact模块/同源账本保持，无新层/引擎 | None |
| MC-REUSE-001 | Applicable | PASS | 原budget服务/Grant/codec/cap/intent与权限port | 不复制预算/身份/SQL框架 | None |
| MC-DEP-001 | Applicable | PASS | JavaTimeModule/runtime已有依赖，POM不改 | 无新库/框架或依赖 | None |
| MC-NAME-001 | Applicable | PASS | Store/Sql、PreparationRequestVO/Result/PreparedInvocationResult、Test | 角色清楚，无模糊carrier | None |
| MC-VALID-001 | Applicable | PASS | schema args/action重建、typed check echo/audit、E1/E2、strict storedcap/budget/outcome | 每个私有源/协议/SQL边界正反验证，无新HTTP DTO | None |
| MC-MODEL-001 | Applicable | PASS | 新嵌套真不可变records/primitive map归一、原值复用 | 非可变实体或重复模型层；metadata-only结果 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层DTO转换 | 私有codec及行重建验证，不另建映射层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术JDBC库，无业务日志/Service新增 | 错误固定脱敏、结果无参数/cipher/audit payload | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/AS/config注册 | 技术库原构造模式保持，无Qualifier新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Spring/原Jackson/摘要与budget | 无新utility/执行框架 | None |
| MC-JSON-001 | Applicable | PASS | 原codec+JavaTimeModule、exact十一键/4KiB | Jackson单栈、严格UTC/type/null/UUID；旧格式不弱化 | None |
| MC-TIME-001 | Applicable | PASS | §4.9/§6最终window/deadline规则 | 原entry wall+mono不重置、locked DBtime、独立toolTimeout、原fact不延长 | None |
| MC-CONFIG-001 | Not applicable | N/A | 12路径无profile/properties/flags | 配置结构不变，未接线库不自动启用 | None |
| MC-PATTERN-001 | Applicable | PASS | 权限依赖倒置+同源transaction participant+原budget | 真实变化点复用，不造State/调度/执行层 | None |
| MC-SCOPE-001 | Applicable | PASS | 12路径、V13固定、仅原PG类扩helper | 不改预算前置/AS/native/SQL/权限/deps，功能边界有限 | None |
| MC-TEST-001 | Applicable | PASS | §8真实RED/GREEN/codec/cap/atomicity/optin合同命令 | 需要的非运行证明可执行，PG skip不冒充通过 | None |
| MC-BLOCKER-001 | Applicable | PASS | 三项已闭合决定及所有独立行 | 当前合同/来源/状态/预算顺序无未决设计，运行边界保留 | None |

### 12.6 Final verdict

PASS — Ready for user review
