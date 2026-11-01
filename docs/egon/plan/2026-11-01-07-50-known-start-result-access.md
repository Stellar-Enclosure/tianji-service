# T3-07 当前授权下原START结果的持久访问

| Field | Value |
| --- | --- |
| Document | `2026-11-01-07-50-known-start-result-access.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 07:50 Asia/Shanghai` |
| Updated | `2026-11-01 08:25 Asia/Shanghai` |
| Source GO | root于`2026-11-01 08:01 Asia/Shanghai`全文审阅514行后发出RED-only GO；pre-GO SHA-256 `1143cb668e6da367392ce725eff331d2f60d5547418c3826245166cb267b7a21`，strict/resources15md19refs/step-start checklist通过；不是Accepted或实现验收 |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | explicit knownSTART result access/25col companionV14/optionalreadiness；12paths |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交；蓝图§11.3同intent返回原结果 |
| Baseline Revision | `main@7396596b41f3e36c6dd5a4e28a947269df7cc988`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `65e3b5eb40afe18ceb965dc78dd43d6af63d48007e7388d0d5bb080566a81df7` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定START Recorder](2026-11-01-06-36-idempotent-start-recorder.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
落实§11.3同intent原结果的显式当前授权访问：每body读取fresh RECONCILE/E1E2，原known Outcome/费用原样返回，访问自身TOOL预算与首access audit同TX持久化，再独立local0结算与同freshwindow最终body门禁。单一additive AgentV14 companion无body/receipt/nonce/dispatch/cost复制，旧verify13与旧流程保持。十二路径一个逻辑Step/commit，无native/config/AA/MCP或自动DDL。

## 2. Target Spec and Effective Design
原蓝图§11.3明确相同意图重复调用返回原结果，§7短事务/一致性与§13当前权限/预算/审计共同约束。原START Recorder不变，本步新显式访问Facade/Store/Sql供后续native组合。firstaccess历史audit不是当前权限，每次重复仍fresh proof；原结果费用与localaccess费0分开。Review沿原exact用户授权，不伪Accepted；root已于08:01完成全文/strict/resources门禁并发出RED-only source GO，实际实施进展见evidence，完整T3-07/运行仍Partial。

## 3. Effective Requirements and Acceptance
PLAN-REQ为原要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/12 | 已知原结果可在明确源绑定下读取 | APPLIED→strictSTART SUCCEEDED、REJECTED/FAILED_KNOWN typed结果，null/nonzerocost不改；source revision/SHA漂移拒绝 | Store/Sql/Facade/tests |
| PLAN-REQ-002 | §13 | 每次body新当前权与完整freshwindow | RECONCILE exactecho/actionID/paramSHA/E1E2/currentRole；fullnano proof与historicalµ分开、account后最后gate | Store/Facade/tests |
| PLAN-REQ-003 | §7/13 | 访问预算/审计原子，host去重不扩额度 | 20samehost1fact1budget；满quota exactreplay不新计数；regularinv/orphan/cross-type拒绝、insertrollback | Sql/Store/tests |
| PLAN-REQ-004 | §6/7/17 | additive存储兼容与独立readiness | finalV14一次创建25col/4checks，旧13verify不require14，新optional须14，旧SQL不动 | SQL/verifier/tests |
| PLAN-REQ-005 | §13/17 | 结算/egress/验证边界真实 | 仅ownaccess0 bestEffort，原cost不改；Pending可trusted返回但window过期/中断无body；12paths/真实证据 | Facade/tests/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
SQL owner在root SQL审阅后一次创建FINAL V14作为静态contract前置，无DDL应用；最小新verifier/types/method declarations支持各组件真实RED。先schema/verifier测试→新Store/Sql测试→Facade测试，各自有效RED后最小GREEN；最后原PGhelper/docs/相关回归。旧SQL不编辑，不把SQL资源/编译当PG通过。
### 4.2 Test-first strategy
schema/verifier owner先受控catalog/history/privilege行为RED；budget owner新StoreTest对source/freshproof/去重/collision/rollback/时间RED；root新FacadeTest对commit/account/egress/保原Outcome RED。正向fixtures需有效affinity/source；编译/fixture错误不算RED，直接stub Errors照实记录，不提交scaffold。
### 4.3 Sequential and parallel boundaries
replay_access_schema负责V14/verifier/Test3paths；budget_lock_tests负责AccessSql/Store/StoreTest3；root负责AccessRecorder/RecorderTest/README3；journal_design负责原PGhelper1；docowner2，共12。Root在PG文件尚无diff/无待应用patch时将其单独移交journal_design；这是内部所有权调整，合同、12路径、单Step/commit及验证范围不变。合同锁定后各组件在自身RED后可并行GREEN，root统一Maven时owners冻结；本Step提交前不并行下一Step。
### 4.4 Commit boundaries
12paths单Step/semanticcommit `feat(agent): audit authorized START result access`；仅显式stage，无push/历史改写/DDL应用/服务启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| access companion/预算审计 | §11.3/13原结果读取也受当前权与预算约束 | V13无合法readaccess列，旧inv写状态不可借用 | 独立25col insert-only fact，无body/新write状态/覆盖旧row | Implement |
| Facade/Store/Sql | §7/13访问一致性/egress | 原PDP/cap/Actionfullparser/budget/codec已有 | 单用途Facade+原UnitofWork，无transport/重复业务engine | Implement |
| optionalreadiness | §6/17老host兼容 | 旧verify13/3table与严格cataloghelpers | 同verifier复用helpers，新method选14，旧method不变 | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| V14/可选schema验证 | PLAN-REQ-004 | verifierTest新history/table负例 | reviewed FINAL资源/最小方法声明 | 四表/history14独立readiness | 新AccessStore | Step 1 |
| current授权/atomicfact | PLAN-REQ-001/002/003 | StoreTest source/proof/dedup/fault/time | 上项+原Action/ledger/codec/cap/PDP | package RecordedAccess+freshwindow | Facade | Step 1 |
| account/egress/兼容 | PLAN-REQ-005 | FacadeTest+旧regressions/PG源码 | 上项/原budget API | trusted当前授权原body结果 | 后续native另步 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/DDL | 已授权Agent技术JDBC+独立Flyway；backend-codegen明确Agent excluded | 不转CRUD/新MP迁移/框架 | 手工technical participant，permanent insert-only dedup非softdelete业务entity，无generator产物/日志 | Step1；MC-ARCH-001、MC-SCOPE-001 |
| Validation/model | 原immutable binding/action/Outcome/Audit与fullActionRow | 每body当前权/源originalresult | Request/Result真value、exactidentity/schema/sourcehash/窗口校验；无正文入access表 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time | manual库/closedcodec/Clock/mono/DBtime | 无新factory/config，原cost与local0分离 | fullnano只作currentproof，projectedµ只存history；原codec不改/值脱敏 | Step1；MC-BEAN-001、MC-JSON-001、MC-TIME-001 |
| Pattern | 现Facade/UnitofWork/SQLparticipant | atomic预算访问与egress有明确变化点 | 复用当前事务风格，无新State/调度/认证引擎 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 源结果/授权 | Action.lockForWrite/fullRow、cap/PDP、ledger material | 当前knownbody未公开、严格域验证已有 | 足够，不改原parser/port | reuse | None | Step1；MC-REUSE-001 |
| 预算/hash | ledger.budgets/reserveBudget/OrchestrationRequestDigests/codec | run-first锁与canonicaltyped encoding已有 | 新访问fact-only participant | reuse | None | Step1；MC-DEP-001 |
| schema/readiness | MemoryStorageVerifier contract/checksum与Tool verifier helpers | 旧13成功未知version可忽略、失败history仍拒 | 一个optional14分支 | reuse，无新verifierclass | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/11.3/13/17 | 3新技术类与typedVO/25col names | tests→schema/verifier→Sql/Store/Facade | roles/resultaccess命名/redacted明确 | 编译/source | Step1 | PASS |
| Rule 2 | §6/7/11.3/13/17 | currentRECONCILE/fullaction/paramSHA/E1E2/knownsource/egress | 新tests/Store/Sql/Facade | 每次body新权，完整owner/source/collision/time校验 | 正反/并发 | Step1 | PASS |
| Rule 3 | §6/7/11.3/13/17 | immutable records/技术insert-onlyfact | Request/Result/newSQL | permanentdedup无deleted_at，源body费用不复制，不新Mapper实体层 | construct/schema | Step1 | PASS |
| Rule 4 | §6/7/11.3/13/17 | manual技术类，无业务Bean/日志新增 | scope | exactaffinity+safe errors，不伪造Lombok注入 | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 5 | §6/7/11.3/13/17 | JDK/现ledger/codec/digest/verifier | 新类/复用 | 不加utility/依赖/认证/DDL平台 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/11.3/13/17 | 原11keyAudit/5keyOutcome codec | Store/Sql/tests | 原fullnano proof与µ历史分开、canonicalSourceSHA、sourceCost原值 | JSON/兼容 | Step1 | PASS |
| Rule 7 | §6/7/11.3/13/17 | 无profile/flag/配置激活 | README/scope | 新增V14不自动apply，no newconsumer | diff | Step1 | N/A：无业务Bean/config面 |
| Rule 9 | §6/7/11.3/13/17 | Facade/UnitofWork+SQLparticipant | 3新类 | fact+budget同TX、dedup-first/afteraccount finalgate | 原子/故障 | Step1 | PASS |
| Rule 10 | §6/7/11.3/13/17 | Instant/Duration/Clock/mono/µfloor | Store/Sql/Facade | 不reset/roundup，每次freshwindow；历史firstaudit非当前许可 | 时间矩阵 | Step1 | PASS |
| Rule 11 | §6/7/11.3/13/17 | 原Agent技术JDBC/generator-excluded | 12paths/新V14 | 仅一次additiveDDL不改旧SQL；非业务softdelete，无架构转换 | scope/oldcompat | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | 当前entry/window/PDPdeadline | Occurred instant | Instant | Not persisted | Internal | Not applicable: 当前权限绝对窗 | Not applicable: 无UI | 初始min(start+10s,runDeadline)，final=min(start+effectiveToolTimeout,start+10s,runDeadline,E1expiry)，sameentrywall+mono不reset；currentaudit checked>=entry且<=Clocknow、expiry覆盖final<=E1 | 原Clock/LongSupplier/fullnano proof，actualDBlease不取旧hint | 每duplicate新窗/E1PDP/E2，不用stored firstaudit/window授新权 | Step 1；TEST-001/002 |
| TIME-002 | historical audit JSON/scalars/created | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: 首access历史事实 | Not applicable: 无UI | validated originalAudit后另造同UUID/grant/versions projectedAudit checked/expires floorMICROS；checkedFloor<=DBcreated<expiryFloor，collapsed/expiredfloor拒绝；不由projection造CheckResult | MICROS向下截断，JSON/scalar一致不roundup | currentfullnano唯一权限证明；oldhistoricalaudit可已过期但immutable，不作freshgate | Step 1；TEST-001/003 µ边界 |
| TIME-003 | account后egress/disclosure | Occurred instant | Instant | Not persisted | Internal | Not applicable: 当前read期限 | Not applicable: 无UI | Store postCOMMIT body gate→ownaccess account→同freshwindow最终Clock/mono/interruption check；过期不body、不清fact/退款 | 原时钟/原window不重建 | PENDING只有window有效可trusted返回，futuremodel须SETTLED | Step 1；TEST-002 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V14__add_tool_result_access_journal.sql | 一次新增最终insert-only companion journal，不应用DDL | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java | 可选V14 readiness与旧13兼容先RED | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java | 复用同一verifier加optional V14四表readiness | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java | 当前授权+原子访问预算/审计/去重源码先真实RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java | Facade factcommit→own0account→fresh egress gate先RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java | 同源事务访问fact/预算准入和原结果读取participant | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java | 独立V14 readiness/当前RECONCILE/技术窗口与私有body门禁 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java | 显式current-authorized访问Facade，原结果与本地access费分离 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 原optin helper编译验证13→14前置与新访问场景源码 | Step1 |
| MODIFY | README.md | 说明同intent原结果访问显式库与限制 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-07-50-known-start-result-access.md | strict12路径执行细化，供root全文/资源后sourceGO | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-known-start-result-access.md | at-lock PENDING骨架及实际后证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline7396596clean、SpecSHA65e3b5eb40afe18ceb965dc78dd43d6af63d48007e7388d0d5bb080566a81df7已核，writing18md24refs/executing15md19refs资源通过。§8既有selector已核、新2Test按scope创建。严格12paths；旧22SQL/V1–13字节保留，V13 SHA=f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3。唯一新V14文件须SQL review后一次创建FINAL，不在classpath放draft/改旧文件/多版本同逻辑。Agent明确generator-excluded，不跑CRUD、不新依赖/MP迁移体系、不产codegen consumption日志；永久technical dedup不加deleted_at，不delete/repair/backfill。没有SQL应用。

Store public ctor(source,ledger,capabilities,authorization)，package追加Clock/LongSupplier，usesDataSource/usesLedger/usesCapabilities/usesAuthorization精确affinity。verifyStorage独立AtomicBoolean，new ToolExecutionStorageVerifier(source).verifyResultAccess()成功才ready。public nested immutable/redacted StartResultAccessRequestVO(BindingVO binding,AgentRunLease lease,ToolDescriptor descriptor,ToolActionRequest action,UUID accessId)：重建exactaction/owner/run/lease/ref/backend/IDW/STARTresource及output exact required {instanceId:STRING}，无transport/callerexpiry/timeout/cost/modelID/body字段；reservationId=tool:+accessId，parametersSha256=descriptor.parametersSha256(action.arguments)，STRICTLY非full action.requestSHA。

Store package RecordedAccessVO recordKnownStartResultAccess(request)、void checkDisclosureWindow(DisclosureWindowVO)。package immutable/redacted RecordedAccessVO(String runId,UUID accessId,String actionIntentId,long sourceActionRevision,String sourceResultSha256,ToolExecutionOutcome sourceOutcome,AgentBudgetReservation accessBudget,DisclosureWindowVO window)；DisclosureWindowVO(Instant deadlineAt,long nanoDeadline)只用于THIS fresh read。Sql package ctor(source,ledger,capabilities)，recordKnownStartResultAccess(request,currentE1,originalAudit,Duration expectedToolTimeout,DisclosureWindowVO window):RecordedAccessVO；内部helper符号可机械适配，不加public Store bodyAPI。

Facade public ToolResultAccessRecorder(Store,ledger)，constructor核store.usesLedger精确相同；usesStore/usesLedger。public StartResultAccessVO recordKnownStartResultAccess(request)。public nested immutable/redacted StartResultAccessVO(String runId,UUID accessId,String actionIntentId,long sourceActionRevision,String sourceResultSha256,ToolExecutionOutcome sourceOutcome,existing Recorder.AccountingState accessAccountingState)：7fields闭合IDs/hash/revision>=1/Known3，原Outcome包括actualCost null/非零 EXACT保留，不改旧Recorder/VO。Facade先Store事实COMMIT，再bestEffort只request.reservationId访问预算settle(0,0,0)，不碰source写预算；account后含interruption必须用同freshwindow最后check再body。PENDING只要window有效可trusted返回，future native必须SETTLED才供model；egress过期保fact/不cleanup/refund。exactretry可重试own0settle，不重新reserve/覆盖firstaudit。

权限/window：noambient/unverified拒绝，capture entrywall+nano初始min10s/run；读exact currentrun immutable authorization/snapshot/effectivePolicy/toolTimeout，再E1exactbinding，final window=min(originalstart+toolTimeout,start+10s,runDeadline,E1expiry)不reset/不caller指定。RECONCILE Request exactrun/lease/tool/backend/independentparamSHA/stableintent/deadline，CheckResult echo/boundary/ALLOW/audit非null、原actor/type/grant/currentversions、checkedAt>=originalentry且<=Clocknow，auditexpiry覆盖deadline且<=E1expiry。**只有original fullnano CheckResult/Audit作当前权**。validated后另造historicalAudit checked/expires向下MICROS（其余fields完全相同），不构造projected CheckResult；persist JSON+scalar同µ，checkedFloor<=DBcreated<expiryFloor，collapse/expiredfloorfail。每重复host read仍freshE1/PDP+E2/currentRole；stored firstaudit/epoch/grant可与当前cap reissue不同，不授权也不覆盖。

TX锁序run FOR UPDATE（exactfullowner/RUNNING/liveactuallease owner/epoch/expiry/cancel/deadline，锁后DBclock重核而非Java expiryhint）→cap E2 fullE1 equality→action lockForWrite（原fullhistoryparser/sameAction）→exactaccess/collision→budget+insert。锁下重核fullimmutable material/toolallowlist/policy expectedToolTimeout、primary/UTF8/origin及freshClock/mono；RPC在TX外，Store before/afterSQL与postCOMMIT privatebody gate。可在run锁后/action锁前作nonlocking access identityprobe，保证同host改target而source缺失也按callerdrift冲突，不能倒置锁。

exactaccess在reserve之前lookup；核firstrowimmutable/sourceRevision/SHA/ownzeroaccessbudget，不新count，quota满仍可exact重复。absentaccess须拒同run/UUID regularinv；reserve TOOL descriptor.versionKey tokens0/0/estimated0且必须newlyCreated，否则orphan/cross-type冲突，绝不reinterpret WRITE budget。原run-budget PK锁序串行化跨类型同host竞争；预算/access fact同DS TX，insert失败回滚budget/counters。caller同access改action/request/tool/sourceidentity为幂等冲突；currentSource revision/digest意外漂移为safeUnavailable/no body。不得更新action/cap/inv/sourcebudget或accessfirstrow。

源只接受APPLIED→strictSUCCEEDED（原StartObservation hostreceipt+canonicalonlyinstanceId）、REJECTED/FAILED_KNOWN typedempty/error。原cost null/nonzero不改；PREPARED/AWAITING_APPROVAL/DISPATCHING/UNKNOWN/EXPIRED/missing/badsourcefailclosed。原ActionRow先验证origin/dispatch/result/receipt/effect，再每读重算sourceSHA=OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_RESULT_ACCESS_SOURCE_V1",intentId,Long.toString(sourceRevision),action.requestSha256(),codec.outcome(descriptor,sourceOutcome)),Map.of())。原codec排序/strict5key/rawBigDecimal不改。不新codec/hash工具，当前known终态由既有API不可变，future未批准source变化被既有access指纹拒绝。

V14表agent_tool_result_access精确25列如下；全部NOT NULL，无额外body/cost/state/default/generated/identity列，类型长度/字符Ccollation复用V13对应字段：

| Column | SQL type |
| --- | --- |
| tenant_id | bigint |
| run_id | varchar(64) |
| access_id | uuid |
| reservation_id | varchar(128) COLLATE C |
| owner_type | varchar(16) COLLATE C |
| owner_issuer | char(64) COLLATE C |
| owner_id | varchar(256) COLLATE C |
| capability_id | uuid |
| capability_binding_sha256 | char(64) COLLATE C |
| capability_issue_epoch | bigint |
| lease_epoch | bigint |
| tool_key | varchar(128) COLLATE C |
| tool_version | bigint |
| tool_sha256 | char(64) COLLATE C |
| backend_binding_sha256 | char(64) COLLATE C |
| parameters_sha256 | char(64) COLLATE C |
| intent_id | uuid |
| request_sha256 | char(64) COLLATE C |
| risk | varchar(32) COLLATE C |
| source_action_revision | bigint |
| source_result_sha256 | char(64) COLLATE C |
| authorization_audit | jsonb |
| authorization_checked_at | timestamptz |
| authorization_expires_at | timestamptz |
| created_at | timestamptz |

keys精确：agent_tool_result_access_pkey PK(tenant_id,run_id,access_id)；agent_tool_result_access_budget_key UNIQUE(tenant_id,run_id,reservation_id)；_run_fkey(tenant_id,run_id)→agent_run同key；_budget_fkey(tenant_id,run_id,reservation_id)→agent_budget_reservation同key；_capability_fkey本地(tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256)→agent_tool_capability(tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256)；_intent_fkey本地(tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,risk)→agent_tool_action_intent对应列，backend映binding_sha256。FK名称完整前缀均agent_tool_result_access；NO ACTION/MATCH SIMPLE沿V13，全部nonnull，无body/receipt/nonce/executor/dispatch/status/source-dispatchpointer/cost复制。

4 CHECK names为tool_result_access_identity/source/authorization/time：identity/source核合法文本/hash、正tenant/epoch/revision/version、risk='IDEMPOTENT_WRITE'、reservation_id='tool:'||access_id::text。authorization沿closed11keyAudit ALLOW/RECONCILE/originalsubject/USER9+auth>=0/catalognull或SERVICE8+catalog>0/authnull、policy>=0，严格JSON类型/大小及JSON/scalarµ相等；time核checked<=created<expires。唯一额外非unique index agent_tool_result_access_intent_idx(tenant_id,intent_id,created_at,run_id,access_id)，加PK/UNIQUE对应indexes。SELECT/INSERT-only实现，不delete/UPDATE首audit。

Verifier旧FILE=V13/migration()/contract(String)/verify()保持3tables/history13，未知成功14忽略但任何history success=false仍拒绝，额外表不进入旧验证。新RESULT_ACCESS_FILE=V14__add_tool_result_access_journal.sql，verifyResultAccess合旧3+new1/history14，同现15s RR primary readOnly=false ownedquery-onlyTX；resultAccessContract(sql)只oneTable25col4checks。复用columns/checks/keys/indexes和checksum/history机制，加ACCESS分支；privileges(table,updateRequired)旧3最少SELECTINSERTUPDATE，ACCESS最少SELECTINSERT、不要求UPDATE（非声称禁止额外UPDATE权限）。不新verifierclass，不在activeTX嵌套调用publicverify。新Store require14，旧流程13继续work；schema-first apply仅用户未来控制，binary rollback留additivetable，用后forwardfix，不drop/backfill/repair。

历史firsthostfact只存第一次audit；重复fresh decision不另增durable auditrow，必须诚实说明此限制。current permission/bodygate每次都新检查，不能用firstaudit/window充权。没有native/config/AA/MCP/host-versionmetadata/inputquota/effectaggregation改动，也不触发transport或业务重派。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 原子审计当前授权的knownSTART原结果访问
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交7396596及原fullAction/codec/PDP/cap/budget/verifier；root闭合25col schema/API。
- Baseline state: 原START方法重复只metadata、V13无合法resultaccessfact；旧verify只13/3；无当前授权原body访问Facade。
- Observable outcome: 每bodyfresh授权，单host访问1budget/firstaudit，原Known3 Outcome不改，account后同freshwindow才disclose。
- End state: 显式访问库/optionalV14readiness可用，无DDL应用/native/AA/MCP/配置或旧schema改变。
- Test-first gate: Required — reviewed FINAL SQL是静态前置，最小新声明后各Java组件有效RED→GREEN；无fixture/compiler伪RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V14__add_tool_result_access_journal.sql`
- Purpose: 一次新增最终insert-only companion journal，不应用DDL。
- Symbols: V14__add_tool_result_access_journal.sql；§6精确合同/字段。
- Repository evidence: root/DB闭合25列/4CHECK/6keys/1额外index；现Agent V13及独立Flyway。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: SQL review通过后仅创建一次此FINAL V14；无classpath草稿、V13 ALTER、manifest/生成器或额外版本。
- Input/output and state mapping: §6完整25 NOTNULL columns/keys/checks，budget/access audit同TX；无结果正文/receipt/nonce/dispatch/sourcepointer/cost。
- Error and edge behavior: 永久technical dedup非soft-delete业务实体；old22/V1–13字节不动，不apply/repair/delete/backfill。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```sql
CREATE TABLE tianji_agent.agent_tool_result_access (
  defineExactlyTheTwentyFiveNotNullColumnsAndFourGroupedChecksFromSectionSix();
  defineNamedPrimaryUniqueAndFourImmediateForeignKeysWithoutBodyOrMutableLifecycleColumns();
);
CREATE INDEX agent_tool_result_access_intent_idx ON tianji_agent.agent_tool_result_access (tenant_id,intent_id,created_at,run_id,access_id);
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java`
- Purpose: 可选V14 readiness与旧13兼容先RED。
- Symbols: ToolExecutionStorageVerifierTest.java；§6精确合同/字段。
- Repository evidence: 原contract/catalog/history/privilege受控测试与实际SQL资源。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: 最小RESULT_ACCESS_FILE/resultAccessContract/verifyResultAccess声明供编译；finalSQL是已审静态输入前置。
- Input/output and state mapping: oldverify对13与13+成功14均保持；新verifyResultAccess须14/newtable25/4checks/keys/indexes；ACCESS不要求UPDATE。
- Error and edge behavior: 任何失败history仍拒；unknown成功版旧语义保持；drift/null/permission/primary/ambient拒绝，不实际跑PG。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
@Test optional_result_access_readiness_keeps_old_history_compatibility() {
  assertFinalV14ContractHasOneTableTwentyFiveColumnsFourChecksExactKeysAndIndexes();
  assertOldVerifyWorksWithThirteenAndSuccessfulFourteenthHistoryButAnyFailedEntryStillRejects();
  assertNewVerifyRequiresFourteenAndAccessSelectInsertWithoutRequiringUpdate(); assertAllCatalogAndPrivilegeDriftFailClosed();
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java`
- Purpose: 复用同一verifier加optional V14四表readiness。
- Symbols: ToolExecutionStorageVerifier.java；§6精确合同/字段。
- Repository evidence: 旧FILE/migration()/contract()/verify三表/history13与query-only15sRR primaryflagfalse。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: RESULT_ACCESS_FILE exactV14；public verifyResultAccess()；package resultAccessContract(String sql) oneTable25col4checks。
- Input/output and state mapping: 新方法合旧3+new1/history14；复用columns/checks/keys/indexes，ACCESS新case；privileges(table,updateRequired)。
- Error and edge behavior: 旧常量/contract与verify仍只13/3；旧3需要SELECTINSERTUPDATE，ACCESS最少SELECTINSERT；无新verifier/DDL动作。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
verifyResultAccess() {
  mergeOriginalThreeTableContractWithResultAccessContract(); verifyFourteenHistoryAndFourTablesInOwnPrimaryRepeatableReadFifteenSecondTransaction();
  reuseCatalogKeyIndexChecks(); privileges(oldTables,true); privileges(resultAccess,false);
}
verify() { retainOriginalThirteenHistoryAndThreeTableSemanticsIncludingAnyFailedHistoryRejection(); }
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java`
- Purpose: 当前授权+原子访问预算/审计/去重源码先真实RED。
- Symbols: ToolResultAccessStoreTest.java；§6精确合同/字段。
- Repository evidence: 现controlledJDBC/primary/Clock+mono/typedcheck/cap/ActionfullRow模式。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: 最小Request/DisclosureWindow/RecordedAccess/Store/Sql declarations后有效RED。
- Input/output and state mapping: Known3/原费用0-null-nonzero；freshRECONCILE每读/E1E2；sameaccess满quota不计数、newquota/collision/orphan/rollback/sourceDrift。
- Error and edge behavior: 独立paramSHA非actionSHA；fullnano→floorµ历史非新CheckResult；timeout<10/postcommit/interruption；source/action/cap不写。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
@Test every_body_read_is_freshly_authorized_and_one_host_access_commits_one_budget_and_first_audit() {
  assertKnownOriginalOutcomeAndSourceDigestPreserveNullNonzeroCost(); assertFreshReconcileOnEveryDuplicateReadAndCapReissueKeepsFirstHistoricalAudit();
  assertExactAccessAtQuotaFullDoesNotReserveAgainAndNewAccessOrCollidingInvocationOrOrphanBudgetFails();
  assertInsertFailureRollsBackBudgetCountersAndFact(); assertRequestDriftConflictAndSourceRevisionDigestDriftUnavailable();
  assertFullNanoCurrentProofAndMicrosHistoricalProjectionNeverRoundUpOrResetDisclosureWindow();
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 5 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java`
- Purpose: Facade factcommit→own0account→fresh egress gate先RED。
- Symbols: ToolResultAccessRecorderTest.java；§6精确合同/字段。
- Repository evidence: 现Recorder/ledger预算测试风格与新Store package结果。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: 最小Facade/7field public VO声明；合法mock affinity/数据准备后真实RED。
- Input/output and state mapping: 仅访问reservation结算0、sourceOutcome原样/PENDING可trusted返回；重复可重试ownsettle无新count；最后同freshwindow核。
- Error and edge behavior: post-account过期/中断不returnbody、不cleanup/refund，facts留下；不改source写预算/不调用transport。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
@Test original_result_is_disclosed_only_after_fact_commit_own_access_accounting_and_same_fresh_window_check() {
  assertStoreCommitBeforeOnlyAccessReservationZeroSettlementAndFinalDisclosureGate(); assertOriginalOutcomeCostAndReceiptRemainUnchanged();
  assertAccountingFailureReturnsPendingOnlyWhileWindowStillValidAndExactRetryDoesNotReserveAgain();
  assertPostAccountingDeadlineOrInterruptionSuppressesBodyWithoutCleanupRefundOrSourceBudgetMutation(); assertClosedSevenFieldResultAndExactLedgerAffinity();
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 6 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java`
- Purpose: 同源事务访问fact/预算准入和原结果读取participant。
- Symbols: ToolResultAccessSql.java；§6精确合同/字段。
- Repository evidence: 原run FORUPDATE/cap.currentInTransaction/Action.lockForWrite/fullhistory/codec/ledger预算。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: package ctor(source,ledger,capabilities)，recordKnownStartResultAccess(request,E1,originalAudit,Duration expectedToolTimeout,DisclosureWindowVO):RecordedAccessVO。
- Input/output and state mapping: §6锁序/identityprobe/knownsource hash/immutablefirst row/zeroaccess budget/INSERT；现codec无改。
- Error and edge behavior: 不网络/transport/更新action/cap/inv/sourcebudget/accessrow；原结果cost不改，insert-only历史audit不当权限。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
RecordedAccessVO recordKnownStartResultAccess(request,e1,originalAudit,expectedToolTimeout,window) {
  lockCurrentRunAndRecheckActualLeaseImmutableMaterialPolicyTimeout(); requireFullCurrentE2EqualsE1();
  probeExistingAccessIdentityWithoutLocksThenLockExactActionViaExistingFullParser(); requireKnownSourceAndRecomputeCanonicalSourceDigest();
  if (exactExistingAccess) verifyImmutableFirstFactAndItsZeroBudgetAndReturnOriginalOutcomeWithThisFreshWindow();
  rejectRegularInvocationSameUuid(); reserveOwnToolZeroBudgetAndRequireNewlyCreated(); insertProjectedHistoricalAuditAndAccessFactAtomically(); returnRecordedFact();
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java`
- Purpose: 独立V14 readiness/当前RECONCILE/技术窗口与私有body门禁。
- Symbols: ToolResultAccessStore.java；§6精确合同/字段。
- Repository evidence: 原Store sameDS/AtomicBoolean/Clock+nano/typedcheck/独立事务模式。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: public ctor+testClock/nano ctor/4uses*/verifyStorage→verifyResultAccess；nested公共Request与package窗口/结果、两个package方法§6。
- Input/output and state mapping: 从entry min10s/run到policy.timeout/E1expiry finalwindow不reset；PDPoutsideTX/fullnanoaudit→distinctfloorhistory；ownRC10s+postCOMMIT gate。
- Error and edge behavior: ambient/unverified/坏actor/source/nonce字段不在Request；freshPDP每重复读，historicalfirstaudit不授新权；无新public Store bodyAPI。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
RecordedAccessVO recordKnownStartResultAccess(request) {
  requireNoAmbientVerifiedV14AndExactReconstructedStartRequest(); captureOriginalEntryWallNanoAndCurrentMaterialToolTimeout();
  e1 = currentExactCapability(); window = minOriginalEntryToolTimeoutTenSecondsRunAndE1Expiry();
  checkExactFreshReconcileIncludingIndependentParametersShaAndStableIntent(); validateOriginalNanoAuditOnlyAsCurrentProof();
  result = ownTransaction(() -> sql.recordKnownStartResultAccess(request,e1,originalAudit,toolTimeout,window));
  checkDisclosureWindow(window); return result;
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java`
- Purpose: 显式current-authorized访问Facade，原结果与本地access费分离。
- Symbols: ToolResultAccessRecorder.java；§6精确合同/字段。
- Repository evidence: 原技术Facade/ledger budget/API语义；新Store包内事实。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: public ctor(store,ledger) exactusesLedger；usesStore/usesLedger；public recordKnownStartResultAccess(Request):StartResultAccessVO(7fields)。
- Input/output and state mapping: Store事实COMMIT→bestEffort只access own0settle→samefreshWindow在account/interrupt后核→public原Outcome。
- Error and edge behavior: 原null/nonzero cost与receipt/output不替换；无transport/newfactory/config；PENDING仅trustedcaller，futuremodel需SETTLED。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
public StartResultAccessVO recordKnownStartResultAccess(request) {
  fact = store.recordKnownStartResultAccess(request); accounting = bestEffortSettleOnlyOwnAccessReservationAtZeroAfterCommit();
  store.checkDisclosureWindow(fact.window()); returnClosedSevenFieldVoWithUnmodifiedSourceOutcome(fact,accounting);
}
ToolResultAccessRecorder(store,ledger) { requireExactStoreLedgerAffinity(); }
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`
- Purpose: 原optin helper编译验证13→14前置与新访问场景源码。
- Symbols: ToolExecutionJournalPostgresTest.java；§6精确合同/字段。
- Repository evidence: 原URL/vector/Timeout120和迁移/recorded known结果helper。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: sameclass新增V14/historyprefix与Access cases，不新PGclass/infra。
- Input/output and state mapping: 先旧verify13成功/newrequires14，再显式fixture加载新14；20sameaccess1fact1budget/duplicatequotafull/collision/rollback/read/audit/knownfees。
- Error and edge behavior: 只有自备optin才跑，默认compile/skip不开DB；controlled sourcecallback无IAM/真实网络；不修改旧SQL。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```java
optInResultAccessHelpers() {
  assertOldThirteenReadinessBeforeV14AndOptionalResultAccessRequiresReviewedFourteenthMigration();
  exerciseKnownSourcesDuplicateTwentyReadersQuotaCollisionAndInsertRollbackWithControlledSourceCallbacks();
  preserveExplicitUrlPinnedVectorTimeoutAndDefaultSkipWithoutStartingDatabaseOrRemoteIdentity();
}
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 10 — `MODIFY README.md`
- Purpose: 说明同intent原结果访问显式库与限制。
- Symbols: README.md；§6精确合同/字段。
- Repository evidence: 原§11.3原结果语义、现START Recorder metadata-only说明。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: 三新技术类/optionalreadiness/V14一新增/独立accessfee，未native接线。
- Input/output and state mapping: 每body freshPDP+E1E2/immutablefirstaudit、原费用保留/只local0、quota/dedup、finalwindow/PENDING。
- Error and edge behavior: 重复决策不另存auditrow诚实限制；无状态UNKNOWN正文/transportredo/自动DDL/配置/AA/MCP或真实运行声明。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```text
Document current-authorized known-result access and original outcome preservation separately from local zero access cost.
Document additive V14 readiness, immutable first-host audit, fresh authorization on every duplicate and quota-safe deduplication.
Retain native/host metadata/input quota/effect aggregation/approval/MCP and real IAM-PG-model acceptance as pending.
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 11 — `CREATE docs/egon/plan/2026-11-01-07-50-known-start-result-access.md`
- Purpose: strict12路径执行细化，供root全文/资源后sourceGO。
- Symbols: 2026-11-01-07-50-known-start-result-access.md；§6精确合同/字段。
- Repository evidence: 蓝图/Agent排除生成器/当前verifier与codec/root闭合合同。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: Review/V5/单Step/commit/5aliases/17MC10Rules与本步精确合同。
- Input/output and state mapping: schema final一次创建/oldSQLimmutable、componentRED+compiledecl、原13verify兼容、原结果费用不改。
- Error and edge behavior: 不扩新协议/依赖/路径或先source，不伪造Accepted/实施结果。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```text
Lock baseline7396596, exact twelve paths and the reviewed V14 contract before any source editing.
Check current-nano authorization versus historical micros projection, atomic access-budget deduplication and final disclosure gate.
Run strict Plan validation and wait for root full read/resource gate; preserve Review and legacy exact authorization.
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

#### File 12 — `CREATE docs/egon/evidence/2026-11-01-known-start-result-access.md`
- Purpose: at-lock PENDING骨架及实际后证据。
- Symbols: 2026-11-01-known-start-result-access.md；§6精确合同/字段。
- Repository evidence: 原evidence格式与subsystem tests。
- Dependencies and consumers: 原ledger/cap/PDP/Actionfullparser/budget/codec；本Step相邻组件，无nativeconsumer。
- Why now: 按reviewed schema/minimalcompile→各组件有效RED→最小实现/兼容/文档；无rootGO前source。
- Contract/signature changes: 5aliases/17MC10Rules、实际RED/GREEN/skip/count/source/schema/兼容。
- Input/output and state mapping: newV14只静态/源码/编译，无默认SQL应用；originalsourceOutcome/localaccess0分离，所有body读新当前权。
- Error and edge behavior: 不猜PASS/count或真实PG/crypto/model，host重复只firstaudit限制/oldflows13兼容诚实记录。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；Agent技术库/immutable值，旧DDL保持。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本角色当前权/原事实/成本/µ历史/事务/兼容严格按§6，N/A见§12。
- Implementation pseudocode:
```text
Record pending implementation and execution conclusions at lock without guessed counts or fabricated PASS.
After actual logs evaluate seventeen MC and ten literal Rule rows independently from source, schema and test evidence.
Trace five local aliases while keeping full T3-07 Partial and additive DDL not applied/runtime unverified.
```
- Verification contribution: TEST-001/002/003/004；实际结果执行后填写，不从编译/skip推真实PG或身份验收。
- After this file: 对应review/minimalcompile/有效RED→GREEN/兼容或文档门禁后推进，不应用DDL/自动启用。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=ToolResultAccessStoreTest,ToolResultAccessRecorderTest,ToolExecutionStorageVerifierTest,ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolWriteInvocationStoreTest,ToolInvocationStoreTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest,ToolExecutionJsonCodecTest,ToolExecutionJournalPostgresTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 各组件有效RED后exact11类GREEN，原PG缺optin只compile/skip；old13/new14/源cost/freshproof/dedup/rollback/µ/egress等矩阵闭合，实际count由日志填。
- Failure returns to: fixture/compiler回对应Test；schema兼容回SQL/verifier；current/source/budget/time回Store/Sql，account/egress回Facade；需扩native/旧DDL/deps/权限回root，不自行加路径。
- Completion criteria: 五别名/17MC10Rules逐项实际证据，finalV14一次创建/旧22bytes保留、12paths与独审无阻断、指定回归通过。
- Rollback: 可回退已部署应用binary以兼容旧13，已additiveV14表和源码migration文件都留存；不Git删除/改写V14。使用后forwardfix，不drop/backfill/delete事实、repair或业务重派。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V14__add_tool_result_access_journal.sql、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifierTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionStorageVerifier.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java、README.md、docs/egon/plan/2026-11-01-07-50-known-start-result-access.md、docs/egon/evidence/2026-11-01-known-start-result-access.md
- Commit: `feat(agent): audit authorized START result access`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7focused Verifier/Store/Facade→exactselector | 新25col/optionalreadiness/currentread/egress缺失行为 | 每组件有效RED→GREEN，stub/fixture/compiler分类 | root Maven only |
| TEST-002 access safety | tianji-service | 新Store/Recorder Tests | Known3/costnull-nonzero/sourceSHA/freshduplicatePDP/capreissue/quota/collision/rollback/paramSHA/shortTimeout/µ投影/afteraccount截止中断 | 原body不改/currentproof每次新、首audit不覆盖、sourcebudget/action不写、Pending仅freshwindow trusted返回 | controlledJDBC/IAM/Clock/ledger，无网络 |
| TEST-003 schema/compat | tianji-service | VerifierTest/旧Recorder/codec/IDW回归、diffcheck/12paths/旧SQLbytes | oldverify13在成功14存在仍兼容/newrequire14/ACCESS不用UPDATE、旧VO不变 | oneFINAL14/no generator/deps/softdelete/旧SQL修改，无源正文进入新table | 非SQL应用/真实PG验收 |
| TEST-004 optinPG | tianji-service | 原ToolExecutionJournalPostgresTest显式URL/vector/Timeout120 | 13→14prefix/readcases/20samehost/rollback等源码 | 缺前置compile/skip，不启动DB、不默认应用新SQL | PG runtime unverified |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-07-50-known-start-result-access.md --strict` | header/12files/trace/MC | exit0，Review供root全文/资源门禁，非实现PASS | docowner |

## 9. Migration, Compatibility, Rollout, and Rollback
唯一newV14 FINAL经SQL review后创建，旧V1–13/22SQL bytes immutable，新V14一旦存在也不得改写/删除。Agent generator不支持本profile，不跑CRUD/新依赖/MP迁移体系，不建立softdelete业务实体或codegen日志；原技术JDBC/独立Flyway保留，未声称其他框架迁移差距被本步修复。新Store需14，旧verify/流程13保持且成功14不破坏；用户未来schema-first apply，无本次DDL应用/repair/backfill/drop。二进制回滚保留additivetable及源码SQL文件，用后forwardfix。无native/config/AA/MCP/hostmetadata/权限激活或实际模型/网络启动。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/12 | Step 1 | AccessSql/Store/Facade/newTests | TEST-001/002 | explicitKnown3原结果/cost完整保留，非业务重派 |
| PLAN-REQ-002 | §13 | Step 1 | freshwindow/RECONCILE/E1E2/historicAudit/egress | TEST-002 | 每body新权，µ历史不替代fullnano proof |
| PLAN-REQ-003 | §7/13 | Step 1 | lookup/reserve/INSERT/collision/budget | TEST-002/004 | 1host1fact1budget/quotafull duplicate安全、firstaudit immutable |
| PLAN-REQ-004 | §6/7/17 | Step 1 | V14/verifier/Test/PGprefix | TEST-003/004 | 唯一新版本、old13compatible/new14required，不apply |
| PLAN-REQ-005 | §13/17 | Step 1 | Facadeaccount/finalgate/docs/回归 | TEST-002/003/005 | ownaccess0与原费用分离、PENDING窗口/限制诚实 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | 旧结果费用被新读取0覆盖 | sourceOutcome exact/null/nonzero保持；onlylocalaccess0settle，不碰sourcebudget | root | Closed：7fieldVO/测试矩阵 |
| DEC-002 | nano权限与µ历史roundup | originalproof唯一当前证据，另造floorAudit不造CheckResult；checkedFloor<=DBcreated<expiryFloor | root | Closed：collapse/expiryfail/JSONscalarµ一致 |
| DEC-003 | 重复host首次audit被当新权/超quota | 每call freshE1PDP/E2/新window；firstrow/auditimmutable，exactlookup在reserve前 | root | Closed：fullquota/capreissue/重复deny/无新count |
| DEC-004 | 旧13流程被强制14 | 同verifieroptional方法，旧3/history13原样，新require14 | root | Closed：成功未知14旧兼容、任何failedhistory旧拒绝保持 |
| RISK-001 | repeated决策未逐次durable日志/真实环境未验收 | 单hostfact只首audit；library/controlledtests，PGoptin skip | 用户/root | Tracked：不假称每次durableaudit；fullT3-07/native/AA/MCP与环境后续 |

## 12. Review and Acceptance
本计划以闭合12path/APIs/schema/time合同落实§11.3显式原结果访问，不改原结果/旧VO或借旧inv状态记读访问。当前权每次新、预算审计原子、首history不作许可、最后egress门禁与费用分离明确。Review沿legacy原exact授权；root已于08:01完成全文/strict/resources门禁并发出RED-only source GO。下面是计划可执行性而非实现/运行PASS；evidence已记录有效RED/权限回归修复、完整175PASS+1PGskip、新PGhelper编译及逐项17MC/10Rules源码验证结论；真实运行仍未验收，最终文档/cached stage/Git门禁由root执行。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原Agent AR persistence技术JDBC/独立Flyway+12paths | Agent generator excluded，无CRUD/新层/MP迁移或框架变更；永久dedup非softdelete业务实体 | None |
| MC-REUSE-001 | Applicable | PASS | 原Actionfullparser/ledger/cap/typedRECONCILE/budget/codec/verifier cataloghelpers | 不复制审批/快照/身份/JSON引擎，3新类是当前访问Facade/UnitofWork participant | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/JDBC/Jackson/原组件 | 无新依赖/生成器/脚本工具 | None |
| MC-NAME-001 | Applicable | PASS | Request/DisclosureWindow/RecordedAccess/StartResultAccessVO、Facade/Store/Sql | 真不可变值、元数据角色清楚，所有敏感结果redacted | None |
| MC-VALID-001 | Applicable | PASS | fullaction/paramSHA/sourcehash/currentproof/owner/livelease/E1E2/known3/schema/auditµ | 每body新权，不把firstaudit/projection或constructor当认证；source漂移失败 | None |
| MC-MODEL-001 | Applicable | PASS | immutable Request/7fieldResult/25col永久技术fact | 无sourcebody/receipt/cost复制、无softdelete/可变state或新实体Mapper | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed domain/codec/DTO重建与本域结果直接复用 | 不新实体Converter层，原结果包括BigDecimal成本原样 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Facade/Store/Sql，无新业务日志Service | fixed安全异常/脱敏，无正文/receipt/nonce/token日志 | None |
| MC-BEAN-001 | Not applicable | N/A | 无factory/config/DI注册，manualctors有exactaffinity | 不新增publicHTTP/native consumer/provider | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK/OrchestrationRequestDigests/codec/事务/ledger | 无新utility/身份/线程/数据库框架 | None |
| MC-JSON-001 | Applicable | PASS | 原strict11key audit与5keyOutcome codec不改 | historical projectedµ JSON+scalar严格一致，源digest canonical编码，非jsonbtext | None |
| MC-TIME-001 | Applicable | PASS | §4.9 originalwallmono/currentfullnano/历史floorµ/finalegress | 不roundup或reset、每重复读freshwindow，不用历史audit过期充权限 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/flags/启用配置/权限更改 | 新V14仅源码不apply，显式访问库未native接线 | None |
| MC-PATTERN-001 | Applicable | PASS | Facade+Store UnitofWork/SQLparticipant+原verifier复用 | atomic access+budget/insert-onlyfirstfact、账户后最后门禁，无新workflowengine | None |
| MC-SCOPE-001 | Applicable | PASS | 12paths/唯一V14/旧22SQL immutable、oldverify13/3保持 | 无V13 ALTER/旧VOstate/Recorder/port/provider/native/AA/MCP/依赖权限扩大 | None |
| MC-TEST-001 | Applicable | PASS | 每组件RED/old13兼容/new14必需/源费用/freshread/quota/collision/rollback/µ/time/egress矩阵 | 受控fixture与原optinPG compile/skip，无真实SQL/服务证明伪报，实际计数evidence填写 | None |
| MC-BLOCKER-001 | Applicable | PASS | root精确25cols/4checks/keys/APIs/时间/费用/重复审计界限闭合，基线/hash已核；08:01全文/strict/resources后sourceGO | 当前无待决协议/依赖；仅计划可执行性通过，整步最终证据与运行未验收 | None |

### 12.6 Final verdict
PASS — Ready for user review
