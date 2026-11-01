# 原生审批型工具等待与当前授权结果访问

| Field | Value |
| --- | --- |
| Document | `2026-11-01-14-18-native-approved-process-tools.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 14:18 Asia/Shanghai` |
| Updated | `2026-11-01 14:37 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | native AA core+current-authorized AA known result access；10paths |
| Source Requirement | 原蓝图§7/11.3/12/13原生审批工具治理、幂等/结果权限与部分失败，分步提交 |
| Baseline Revision | `main@e0523987cd0744ef93363965e90c4cd3b7b7db11`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `5128704d8bf4819fd575919d7162fb83f03bd32b9c4e8cb3f731ce7fb1b63373` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [AA Recorder](2026-11-01-13-15-approved-start-recorder.md)；[native Process](2026-11-01-09-05-governed-native-process-tools.md)；[known result access](2026-11-01-07-50-known-start-result-access.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
在现programmatic native Process bridge加入显式AA能力，等待只返回安全状态；不以model receipt或历史proof替当前权限。复用同一Scope/Plugin计额与freeze，bounded one-shot两入口完整转发；终态AA访问沿V14/current RECONCILE，只补风险路由与当前approval policy闭合。无AS装配、迁移、新层或执行器。

## 2. Target Spec and Effective Design
有效蓝图§7/11.3/12/13/17、T3-07；原用户exact继续授权。e052已提交AA Recorder但native旧8构造拒AA，event未接WAIT，Access ctor/Action lock仍IDW-only且无AA policyref当前核验。所有补点有实际源码依据；本Step不把host引用视为批准，不配置角色或启动环境。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | 显式AA构造/同步host receipt metadata/完整action与fence | Bridge/Test |
| PLAN-REQ-002 | §7/11.3/12/13/17 | WAIT安全model/event/observer-only NONE/onceentry/freeze | CommonBridge/Loop/BridgeTest |
| PLAN-REQ-003 | §7/11.3/12/13/17 | 两入口bounded转发/Observed race/known access/no duplicate START | Bridge/Test/Loop |
| PLAN-REQ-004 | §7/11.3/12/13/17 | AA known history fresh RECONCILE/currentpolicy/费用与V14原事实保持 | AccessStore/Sql/Test |
| PLAN-REQ-005 | §7/11.3/12/13/17 | 旧8/IDW/loop兼容、10scope/23SQL/三RED/分步提交 | all10paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
maine0523987cd0744ef93363965e90c4cd3b7b7db11 clean；Spec SHA5128704d8bf4819fd575919d7162fb83f03bd32b9c4e8cb3f731ce7fb1b63373，23SQL immutable。
### 4.2 Test-first strategy
Access现AA正例由旧ctor拒绝即反证；native新10ctor/RequestVO/useshelpers仅最小UOE/plain声明供编译，旧8先保持；Common现WAIT ctor拒绝由directcase反证。三个unit有效RED后才各实现，compiler/fixture/Telemetry env失败不计RED；AArequest延后到test正文避免mock ctor泄漏。
### 4.3 Sequential and parallel boundaries
Root owns CommonBridge+AccessStore/Sql三prod与README/plan/evidence三docs共6；journal owns ProcessBridge+BridgeTest2；tool_audit owns LoopTest1；worker aa_result_access_tests owns AccessStoreTest1。不是独自在代码库，保留他人；Root统一Maven前所有Java冻结。
### 4.4 Commit boundaries
十路径一Step/commit `feat(agent): govern native approval-required process tools`；手工门禁/独审/diff/cached后精确stage，不push/改历史/runtime启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Element | Source necessity | Current capability | Decision |
| --- | --- | --- | --- |
| host receipt choice | §11.3模型不供可信审批 | immutable snapshot/action现有 | nested同步selector，实际Verifier认证，不新网络层 |
| nativeAA等待 | §7/12唯一loop/风险/计额 | sharedScope/Plugin/observer已有 | 显式ctor+窄AA分支，不第二Loop |
| WAIT event account | 必须不伪费用/结算 | publicWAIT ownnull可无当前reservation | 强制PENDING+epoch0/noerror，model仅status |
| AA result disclosure | §11.3原结果+§13当前权限 | V14/state/digest/fees已generic | AA lock分支+双policyRef校验，不新表/parser/POST |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| currentAA access | PLAN-REQ-004/005 | AccessStoreTest | 现ctor拒AA/合法AAhistory | fresh授权终态body | NativeAA | Step1 |
| explicitnativeAA | PLAN-REQ-001/003 | BridgeTest | newctor/VO声明/Recorder已提交 | guardedScope AA | Loop | Step1 |
| WAITable/Loop | PLAN-REQ-002/005 | LoopTest directevent+FakeRunner | native声明/Common旧拒WAIT | safe model/event/effect | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现AR adk/persistence | 保现结构/技术架构 | 无新层/Bean/DDL/CRUD | Step1；MC-ARCH-001 |
| Reuse | Recorder/Scope/Plugin/ActionAA locks/V14 | 同一执行与当前权限边界 | no newloop/timer/parser/authority | Step1；MC-REUSE-001 |
| Model/validation | 原immutable VO/typed refs | 新4field hostselector RequestVO+closedWAIT metadata | fullaction/available defidentity/nullablecanonicalref，构造非授权 | Step1；MC-VALID-001 |
| Pattern/time | callback Adapter+Scope+Facade | metadata变化点/latefact freeze | 只复用既有机制/原deadline | Step1；MC-PATTERN-001/MC-TIME-001 |
| Bean/config/log | 无此面变化 | 有据N/A | 不加无关注解/YAML/日志 | Step1；MC-SCOPE-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 原生准入/计额/停止 | commonScope/Plugin/Executor | sharedentrycounter/nativeidentity/session/freeze已有 | 新AA分支接入，无源Plugin改动 | reuse | None | Step1；MC-REUSE-001 |
| 审批/START | e052Recorder/5fdOneShot | 实际服务身份/nonce/deadline已有 | bridge双入口fence+hostreference | reuse | None | Step1；MC-DEP-001 |
| 终态访问 | resultAccess/V14/lockForApproval | genericrisk/digest/fees，唯一剩IDW guard | 窄允许AA+当前policy闭合 | reuse | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | nested Resolver/RequestVO技术命名 | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | typedfullaction/receipt/fence/PDP/policy闭合 | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | 仅不可变4field record，旧实体/DTO保持 | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | 无新Bean/DI/log | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 5 | §7/11.3/12/13/17 | JDK/原ADK-Spring-SDK-helper复用 | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 原codec/SDK/V14保持，safe status无ordinal | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 无配置/profile改变 | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 9 | §7/11.3/12/13/17 | 原Adapter/Scope/Facade解变化点，唯一Loop | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | Instant/Duration/原deadline/freeze | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | 现AR技术架构/23SQL保留，无CRUD/MP迁移，Agent excluded | tests→access/native/common→docs | §6/逐文件 | source/tests/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | intentTTL/businessVersion/boundedstart | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | 原hostVersion≤128/TTL≤7d/DBµexpiry保持，guarded bounded重载同Instant转发不reset | 原DB time/Recorder clock | old8/IDW不改时间语义 | Step 1；TEST-002/003 |
| TIME-002 | access window/historicalapproval | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | freshRECONCILE每次/原wallmono≤10与Tooltip；proof按历史verified/consumed可过期，不以旧proof授新权 | 原nonce/JSONnano/indexfloorµ保持 | V14 digest/sourcecost不变 | Step 1；TEST-001 |
| TIME-003 | selector/scopefreeze/latefacts | Duration | Duration | Not persisted | Internal | Not applicable: hostmetadata选择非IOtimer | Not applicable: 无UI | selector同步metadata无框架IObudget或authority；fence/open前后，close冻结effect，lateobserver不改终态 | 原Scope锁/Recorder预算，无新timer/executor | 原停止语义保持 | Step 1；TEST-002/003 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java | AA历史结果的当前访问反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java | 允许固定AA known result/current policy closure | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java | AA action独立锁路由与事务内policy核验 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java | 显式AA native ctor/host resolver/guard/observed/effect反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java | 复用Scope加入显式AA能力/host receipt选择/结果分流 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java | actual Runner/FakeChatModel AA等待/entryquota/forward/freeze反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java | 闭合WAIT FINISHED安全元数据 | Step1 |
| MODIFY | README.md | 原生AA显式host能力/等待/结果权限边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-14-18-native-approved-process-tools.md | 锁定10paths/API/三行为unit/17MC10Rules | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-native-approved-process-tools.md | 真实执行与边界证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Exact native API.** nested `@FunctionalInterface ApprovalReceiptResolver { String resolve(ApprovalReceiptRequestVO request); }` nullable ref；new public immutable record四字段 `(AgentRunAuthorization.Request run,AgentExecutionSnapshot snapshot,ToolDescriptor descriptor,ToolActionRequest action)`，definition key/version/contentSHA/policyRef与descriptor membership匹配，重建完整action owner/tool/policy/normalized args/intent/requestSHA，toString脱敏。它是同步trusted HOST metadata selector，无框架认证/有界I/O权限，无model可选tenant/source/consumer；自然host从immutable snapshot选receiptRef，实际Verifier才鉴别批准。不得凭record/hash构造宣称auth。

**Compatibility.** 原8arg构造IDW-only/原行为保留；新增10arg在末尾追加FixedToolApprovalVerifier与ApprovalReceiptResolver，二者nonnull。usesApprovalVerifier/usesApprovalReceiptResolver纯精确实例检查，old模式对nonnull candidate为false。原Registry/一次hostBindings/声明顺序/fullrefs/StartContextResolver不变，AA只支持fixed START与exact required STRING instanceId；businessKey≤255、version≤128、TTL≤7d与DBµexpiry沿原host context，不从model覆盖。

**AA entry.** canonical action/request已建后STARTED→markEntered UNKNOWN→requireOpen+native callFence→resolveonce→nullablecanonical UTF8 text1..255→callFence+requireOpen→newRecorder.executeApprovedStart。selector不在bind时运行；任何selector/fence错误safeUNAVAILABLE/UNKNOWN，不直接NONE。两fences不再增加Plugin entry；现beforeToolCallback统一计额（畸形/wait/metadata/error不退款）。不得增加第二Loop/hiddencallbacks/toolsets。Selector不是可由模型启动的任意I/O适配器。

**Bounded/facts.** guarded transport由lambda改匿名OneShot两个入口，各先相同native/session fence/open/heldFence再delegate原start()或原start(bound)，绝不丢重载/降级；heldFence优先于外层翻译。commitObserver AAWAIT→NONE，SUC→CONFIRMED，knownNegative→NONE，UNKNOWN→UNKNOWN；WAIT返回字段自身不授NONE。保持scopeclose freeze/latefacts/events封口/knownbusiness truth不因accountpending或outer failure降级。

**Result routing.** ownoutcome沿原own/model validation；ownmetadata无body沿原metadata，不猜费用。ownnull+ObservedAAWAIT只返回 `{status:AWAITING_APPROVAL}`，无receipt/proof/source/consumer/args/version或业务body，不调用access。FINISHEDAAWAIT event严格epoch0、accountingPENDING、errornull；不许SETTLED/nullaccount/null或nonzeroepoch/error，因为freshhost观察globalwait可无任何本entry reservation，public结果无法确认其settlement。safeWAIT model不走普通业务body必须SETTLED的gate。ownnull+Observed APPLIED/REJECTED/FAILED_KNOWN才freshaccess；DISPATCHING/UNKNOWN/PREPARED/EXPIRED沿safe unavailable，不access/驱动/伪WAIT/NONE。Observed是实际快照非持续当前状态。

**AA known access.** Request ctor只扩risk集合IDW/AA，保持原5fields/fixedschema/fullaction/grant/owner。outer Store material与SQL requireRun均额外AA current effectivePolicy.approvalPolicyRef==descriptor.ref，否则deny。SQL AA分支调用现lockForApproval，IDW仍lockForWrite，不能改Action风险守卫；其余V14fact/digest/revision/cap RECONCILE/window/idempotence/own0budget/fees保持。historicalproof可过期，只按原verified/consumed解析；无freshapprovalVerifier/START/receipt释放/源结果或费用/预算改写。corrupt/missing/crossproof由严格Action parser拒绝，tests不制造结构不可达非法Row来冒覆盖。

**CLAR-001（Root与独审批准，反证RED后才最小修复）.** WAIT仅显式AA Recorder observer允许；oldIDW及resultAccess（含AA终态访问只known3）observer继续拒AAWAIT，不得将baseline UNKNOWN改NONE。新增legacyIDW wrong-WAIT回归先有效RED再private allowWait guard，reject在effects写前；无需新API/路径/状态模型。

**Scope.** 10paths；23SQL/AS-config/Executor/Plugin源码/SDK/ports/codec只读，无migration/依赖/新层/Bean/rolegrant/app/DB/browser启动。actualRunner/FakeChatModel仅有限unit；不是实际IAM/模型/PG接受。AS approvalprofiles/稳定qualifiedreceipt resolver factory+eager actual-chain readiness另一步。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 原生审批型工具等待与当前授权结果访问
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交e052/5fd/3548/a7与现Scope/Plugin/V14/Action parser。
- Baseline state: old8 native仍拒AA，Event拒WAIT，Access仅IDW锁/ctor。
- Observable outcome: 显式AA scope可等待/受控派发/当前获权结果访问，model无敏感approval数据且effect与quota正确。
- End state: programmatic nativeAA core/受控Runner回归，AS配置与真实runtime未启用。
- Test-first gate: Required — 三unit实际RED后功能GREEN，newctor/VO onlycompile scaffolds；无fixture/compiler伪RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java`
- Purpose: AA历史结果的当前访问反证。
- Symbols: AA profile/history/RECONCILE/policy drift/source digest/费用 tests。
- Repository evidence: 现Scene/JDBC与sharedAAFixture/ActionApprovalSqlTest。
- Dependencies and consumers: test-first；现AA ctor拒绝，不先改生产；AArequest在test正文构造避免fixture泄漏。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: known3/expired proof/null-nonzero-overestimate fee、own0budget/idempotence。
- Error and edge behavior: policy outer/inTX、PDP/cap/lease/unknown-history/source-lock错拒；原IDW主体不变。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
assertApprovedFixedStartRequestAndExpiredHistoricalProofNeedFreshReconcile();
assertKnownThreeAndOriginalProofOutcomeCostAreUnchangedWhileOnlyAccessBudgetSettlesZero();
assertOuterAndTransactionalApprovalPolicyMismatchDenyDisclosure();
assertReplayOneFactBudgetButFreshAuthorityAndAllNonterminalOrSourceLockFailuresDeny();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java`
- Purpose: 允许固定AA known result/current policy closure。
- Symbols: StartResultAccessRequestVO risk membership/current material policy guard。
- Repository evidence: 现fixed START/fullaction/request/window/PDP chain。
- Dependencies and consumers: File1有效RED后实现。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: 仅IDW/AA，原五字段shape不改；AA当前approvalPolicyRef匹配。
- Error and edge behavior: 历史approval不替当前权限；不fresh Verify/START/改sourcecost。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
requireRiskIn(IDEMPOTENT_WRITE,APPROVAL_REQUIRED);
requireOriginalFixedStartSchemaAndFullBindingActionIdentity();
if (risk==APPROVAL_REQUIRED && !equal(currentPolicy.approvalPolicyRef,descriptor.approvalPolicyRef)) deny();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java`
- Purpose: AA action独立锁路由与事务内policy核验。
- Symbols: recordKnownStartResultAccess action lock/requireRun。
- Repository evidence: 现V14 generic risk/source digest/0accessbudget/read parser。
- Dependencies and consumers: File1有效RED后实现，Store同源事务。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: AA→lockForApproval；IDW→lockForWrite，不放宽Action守卫。
- Error and edge behavior: current policy再次核AAref；historicalproof可过期，known3/outcome费用保持。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
var action = risk==APPROVAL_REQUIRED ? intents.lockForApproval(...) : intents.lockForWrite(...);
requireCurrentRunLeaseAndExactAaApprovalPolicyUnderRunLock();
reuseExistingKnownOutcomeSourceDigestAccessFactAndIndependentBudgetWithoutPost();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java`
- Purpose: 显式AA native ctor/host resolver/guard/observed/effect反证。
- Symbols: 10arg ctor/ApprovalReceiptResolver+RequestVO/executeAA/forwardbounded。
- Repository evidence: 现23 nativeBridge tests/已提交AA Recorder。
- Dependencies and consumers: new10ctor+VO/useshelpers仅compile UOE/plain声明；原8模式不改；自身有效RED后实现。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: old8IDW-only，新10显式AA，resolver一次且onlyhostmetadata/前后fence。
- Error and edge behavior: WAIT safe map/PENDING epoch0/NONE仅observer；observedrace/resultaccess/outermask/freeze。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
assertLegacyEightConstructorAndIdwBodiesRemain();
assertExplicitAaCapabilitiesAndFullRebuiltActionReachHostSelectorOnce();
assertBoundedOverloadForwardsSameDeadlineAndFenceWithoutLegacyFallback();
assertWaitNoPrivateBodyAndObserverOnlyNoneAndKnownAaFreshAccess();
assertAdvancedObservedLoserNotFalseWaitAndLateFactsCannotChangeFrozenScope();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java`
- Purpose: 复用Scope加入显式AA能力/host receipt选择/结果分流。
- Symbols: old8/new10 ctor; nested ApprovalReceiptResolver/ApprovalReceiptRequestVO; usesApprovalVerifier/usesApprovalReceiptResolver。
- Repository evidence: 现registry/bindScope/StartContext/execute/guardedTransport/effects/freeze。
- Dependencies and consumers: File4有效RED后实现，RootCommonWAIT元数据/File7支持。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: old8仍IDW-only；new10末尾verifier/resolver均nonnull；AA仅fixed START exact instanceId。
- Error and edge behavior: resolver纯同步metadata无框架IO授权/新timer；markUNKNOWN先；后续actualVerifier授本次来源。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
oldConstructorKeepsIdwOnly(); newConstructorRequiresExplicitApprovalVerifierAndReceiptResolver();
requestVoValidatesDefinitionVersionHashPolicyRefAndDescriptorMembershipThenRebuiltActionOwnerRefPolicyArgsIntentHash();
executeAa { rebuildAction(); appendStarted(); markEnteredUnknown(); requireOpenAndFence(); var receipt=resolveOnceFromTrustedSnapshot(); validateNullableCanonicalReceipt(); fenceAndRequireOpen();
    var result=recorder.executeApprovedStart(verifier,guardedDualEntryTransport,request,receipt,committedObserver);
    if (ownOutcome) reuseOwnValidationAndModelGate(); else if (ownMetadata) reuseMetadataHandling();
    else if (observedAAWAIT) returnSafeWaitWithPendingEpochZeroEvent(); else if (observedKnownThree) freshReconcileAccessBeforeBody(); else failUnavailable();
}
OneShotStart guarded { start()=fenceOpenThenDelegateStart(); start(bound)=sameFenceOpenThenDelegateStart(bound); }
resolveCommittedStatus(id,status,allowWait) { if (status==AWAITING_APPROVAL && !allowWait) throw dependencyBeforeEffectMutation(); AWAITING_APPROVAL -> NONE; SUCCEEDED -> CONFIRMED; knownNegative -> NONE; UNKNOWN -> UNKNOWN; }
closeStillFreezesEffectBeforeReadingAndRejectsLateEvents();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java`
- Purpose: actual Runner/FakeChatModel AA等待/entryquota/forward/freeze反证。
- Symbols: direct ToolEventVO cases + native AA scenarios。
- Repository evidence: 现IDW Loop rig/Plugin counter/原model budget/session guards。
- Dependencies and consumers: Bridge new10最小声明可编译；Common现拒WAIT供真实RED，旧Loop主体保持。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: WAIT safe prompt/status+effectNONE、oneentry/no secondloop、known replay、outermask。
- Error and edge behavior: PENDING不是SETTLED；None须observer；无真实IAM/模型/PG宣称。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
assertFinishedWaitEventRequiresZeroEpochPendingAccountingNoError();
runFakeModelWaitAndAssertOnlySafeStatusNoReceiptProofArgsConsumer();
assertSecondToolEntryOverQuotaStopsBeforeSelectorOrRecorder();
assertBoundedDeadlineAndSessionFenceForwardAndKnownAaAccess();
assertOuterPostcheckSuppressesValueButRetainsCommittedEffectAndCloseFreezesLateFacts();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java`
- Purpose: 闭合WAIT FINISHED安全元数据。
- Symbols: ToolEventVO constructor AA wait branch。
- Repository evidence: 现STARTED/UNAVAILABLE/terminal checks。
- Dependencies and consumers: File6 directcase有效RED后最小branch。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: 仅FINISHED AWAITING_APPROVAL/epoch0/PENDING/errornull。
- Error and edge behavior: reject SETTLED/null accounting/null/nonzero epoch/error；旧状态不变。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
if (phase==FINISHED && state==AWAITING_APPROVAL) {
    require(dispatchEpoch==0L && accountingState==PENDING && errorCode==null);
} else reuseAllExistingEventValidationUnchanged();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 8 — `MODIFY README.md`
- Purpose: 原生AA显式host能力/等待/结果权限边界。
- Symbols: nativeAA core说明/旧pending必要更新。
- Repository evidence: 现AA Recorder/Bridge/Access说明。
- Dependencies and consumers: 最终source/log后更新。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: 构造/metadata非authority，AS配置readiness另步。
- Error and edge behavior: 不宣称selector有认证IO或PG/IAM/模型验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
documentAdditiveExplicitAaBridgeAndHostOnlyMetadataSelector();
documentWaitPendingEventObserverEffectAndFreshTerminalDisclosure();
documentUnconnectedAsConfigurationAndUserRuntimeAcceptanceBoundary();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 9 — `CREATE docs/egon/plan/2026-11-01-14-18-native-approved-process-tools.md`
- Purpose: 锁定10paths/API/三行为unit/17MC10Rules。
- Symbols: V5/5aliases/约束与顺序。
- Repository evidence: 蓝图/当前native与access实际源码/两独审。
- Dependencies and consumers: Root全文/strict/resources/checklist/hash后GO。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: Review原授权，不伪Accepted。
- Error and edge behavior: 无未决Accounting：WAIT强制PENDING不虚构settledbudget。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
lockBaselineE052AndTenPathsWithExplicitOwnership();
requireActualAccessNativeAndEventRedBeforeGreen();
requireManualChecksRulesAndPathLimitedCommitBeforeNextStep();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

#### File 10 — `CREATE docs/egon/evidence/2026-11-01-native-approved-process-tools.md`
- Purpose: 真实执行与边界证据。
- Symbols: 5aliases/17MC10Rules/REDGREEN/Git。
- Repository evidence: 本plan与实际源码/日志。
- Dependencies and consumers: 先PENDING，实际结果后逐项闭合。
- Why now: 真实tests先于功能，必要compile声明后观察本unit实际RED才实现。
- Contract/signature changes: §6 exact additive API/WAIT-PENDING/AA risk policy契约。
- Input/output and state mapping: 记录oldbody/失效路径/skip/fixtures分类。
- Error and edge behavior: controlledRunner不冒真实模型/IAM/PG，Selector不是authority。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本块的身份/窗口/计额/快照/事实义务。
- Implementation pseudocode:
```java
recordActualRedGreenAndOldBehaviorCompatibilityEvidence();
recordSourceIdentityTimeQuotaWaitAndFactFreezeManualFindings();
recordExactTenPathCommitAndRemainingRuntimeConfigurationBoundaries();
```
- Verification contribution: TEST-001/002/003/004，实际日志/限制记evidence。
- After this file: 本贡献完成，不以metadata/compile冒runtime权限或模型验收。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace。
- Verification command: §8 canonical八类；focused分选Access/Bridge/Loop，RootMaven前全部Java冻结。
- Expected result: 三有效RED后GREEN，旧测试兼容，singleLoop/hostfence/WAITPENDING/NONE与freshAAaccess/sourcefees矩阵闭合。
- Failure returns to: accesspolicy/history回File1/2/3；host/bridge/bounded/effect回File4/5；event/nativeLoop回File6/7；fixtures先修再有效RED，范围漂移先Root修计划。
- Completion criteria: exact10paths/23SQL/无stub/旧兼容/17MC10Rules/独审/cached通过后单提交。
- Rollback: 可退binary保已消费proof/nonce/V14facts，停用新scope入口；不删历史、不repair/drop/retryUNKNOWN。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessStore.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessSql.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridgeTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessToolBridge.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedToolBridge.java、README.md、docs/egon/plan/2026-11-01-14-18-native-approved-process-tools.md、docs/egon/evidence/2026-11-01-native-approved-process-tools.md
- Commit: `feat(agent): govern native approval-required process tools`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Evidence |
| --- | --- | --- |
| TEST-001 | AccessStoreTest AA新增反例 | ctor/AAlock/currentpolicy/historicalproof/fee/window/idempotence RED→GREEN |
| TEST-002 | BridgeTest | explicitAA/old8/hostselector/fence/Observed/effect/forward RED→GREEN |
| TEST-003 | LoopTest directVO+Runner | WAIT PENDING/model/quota/latefact/outermask RED→GREEN |
| TEST-004 | 八类回归/23SQL/10scope/strict/17MC10Rules | 全适用PASS后commit；无实际IAM/PG/网络 |

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolResultAccessStoreTest,ToolResultAccessRecorderTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,AdkRunContextTest,ToolApprovedStartRecorderTest,ToolApprovedInvocationStoreTest,ToolActionApprovalSqlTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-native-approved-process-final.log 2>&1
```
Strict从workspace用writing-plan validate_plan.py本文件 --strict；current execute-plan资源15md19、writing18md24/checklist按门禁复核。无真实SQL/HTTP/模型/crypto proof，旧physical PG fixture本Step不扩。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/配置/角色激活，23SQL不可变。old8 ctor拒AA/IDW路径与原模式保持，新10显式optin；新WAIT metadata闭合无privatebody。V14源fact/费用与digest保持，currentapprovalPolicyRef是现在权限closure非刷新旧approval。AS装配仍未开放AA，单nativeLoop/原freeze/隐私gate保持。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | Step 1 | Bridge/Test | TEST-001/002/003/004 | 显式AA构造/同步host receipt metadata/完整action与fence |
| PLAN-REQ-002 | §7/11.3/12/13/17 | Step 1 | CommonBridge/Loop/BridgeTest | TEST-001/002/003/004 | WAIT安全model/event/observer-only NONE/onceentry/freeze |
| PLAN-REQ-003 | §7/11.3/12/13/17 | Step 1 | Bridge/Test/Loop | TEST-001/002/003/004 | 两入口bounded转发/Observed race/known access/no duplicate START |
| PLAN-REQ-004 | §7/11.3/12/13/17 | Step 1 | AccessStore/Sql/Test | TEST-001/002/003/004 | AA known history fresh RECONCILE/currentpolicy/费用与V14原事实保持 |
| PLAN-REQ-005 | §7/11.3/12/13/17 | Step 1 | all10paths | TEST-001/002/003/004 | 旧8/IDW/loop兼容、10scope/23SQL/三RED/分步提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | model伪receipt/source | onlyhost同步metadata ref，actualVerifier认证，原action闭合 | Root | Closed |
| DEC-002 | WAIT假settlement/NONE | eventPENDING/epoch0/noerror，NONE仅commitobserver | Root | Closed |
| DEC-003 | bounded重载被lambda丢掉 | 双入口显式同fence转发，samepluginentry无双计数 | Root | Closed |
| DEC-004 | 旧approval冒当前结果权限 | freshRECONCILE/双AApolicyRef/lease-cap-window，histproof只来源 | Root | Closed |
| RISK-001 | selector变任意IO/实际环境 | contract纯metadata，不新timer/authority；host实现需守边界 | host/用户 | Tracked |
| RISK-002 | latefact/outererror改冻结effect | 原scopefreeze/heldfailure/observertruth，tests封口 | Root | Tracked |

## 12. Review and Acceptance
Root与agents只读设计已闭合；原用户授权下Review不伪Accepted。Root全文/strict/resources/checklist/hash后SourceGO，evidence先PENDING。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR native bridge/Scope与persistence结构，Agent excluded；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-REUSE-001 | Applicable | PASS | 原Recorder/Plugin/shared declarations/Action locks/V14 access/observerfreeze；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/ADK/SDK/Spring，无新依赖或构建步骤；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-NAME-001 | Applicable | PASS | nested ApprovalReceiptResolver/immutable RequestVO与现技术roles；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-VALID-001 | Applicable | PASS | full action/snapshot definition identity/hostreceipt/nativefences/currentRECONCILE/policy；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变4fieldRequestVO/现metadata与closed status，旧DTO不改；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无PO/entity/Mapper转换，typed内部对象/primitive model map；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无业务日志新增，redacted VO/safe errors/no receiptproof；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-BEAN-001 | Not applicable | N/A | manual additive ctor/no新Spring Bean或DI；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK/原scope/helper，不新timer/executor/general utility；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-JSON-001 | Applicable | PASS | 原SDK/codec/V14原格式保持，safe status model map无ordinal；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TIME-001 | Applicable | PASS | 保既有TTL/原call deadline/guard/freeze；selector同步metadata非IObudget授权；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONFIG-001 | Not applicable | N/A | AS/YAML/profile/角色激活不改，显式core能力；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-PATTERN-001 | Applicable | PASS | 原callback Adapter/Facade/Scope复用，保持single native Loop；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-SCOPE-001 | Applicable | PASS | exact10paths/23SQL/AS/Executor/Plugin源不改；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TEST-001 | Applicable | PASS | Access/Bridge/Common WAIT真实RED→GREEN，旧tests/nativeFakeRunner回归；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-BLOCKER-001 | Applicable | PASS | API/risk/currentpolicy/waitPENDING/observerfrozen安全合同已锁；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |

### 12.6 Final verdict
PASS — Ready for user review
