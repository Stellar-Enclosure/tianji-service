# 固定审批消费与写调用账本原子准入

| Field | Value |
| --- | --- |
| Document | `2026-11-01-11-58-atomic-tool-approval-journal.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 11:58 Asia/Shanghai` |
| Updated | `2026-11-01 12:28 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR package-only AA consume+dispatch journal；9paths |
| Source Requirement | 原蓝图§7/11.3/12/13审批绑定/幂等/预算与账本一致性，继续未完成工作分步提交 |
| Baseline Revision | `main@a7a392e35eca4ac51a9076853669f4a13eacf3f9`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `5050f9ff8c68fc6932bf12c8081b74accd8e1e27fe5e512c24061948ac6f21ef` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定审批来源验证](2026-11-01-11-13-fixed-tool-approval-verification.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
本Step扩现AR三类，原子消费可信审批并为新host UUID建立新预算/派发调用。旧AWAITING invocation与SETTLED0预算保持历史终态；不增加Recorder/native/config或迁移。复杂一致性采用现Unit of Work+CAS，避免网络与数据库混合事务。

## 2. Target Spec and Effective Design
蓝图§7/11.3/12/13/17、T3-07为唯一有效设计；原用户明确授权继续该计划，无Accepted字段不伪造。前置a7a392e已提交来源验证库，当前V13有七审批列/唯一receipt/FK；源码缺消费写者。业务START transport/审批Verifier仅由未来Recorder在事务外组合，本Step不调用网络/PDP。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | 完整AA动作/source/当前权限/lease/时间绑定 | Store/Action/新两tests |
| PLAN-REQ-002 | §7/11.3/12/13/17 | 新host预算与inv+消费CAS同事务，旧wait不可复活 | Store/InvocationSql/新tests/PG |
| PLAN-REQ-003 | §7/11.3/12/13/17 | 原nonce历史完成、未知不重派与费用事实 | 三prod/新tests/PG |
| PLAN-REQ-004 | §7/11.3/12/13/17 | fullnano proof与索引保守µ期限兼容 | ActionSql/两新tests/PG |
| PLAN-REQ-005 | §7/11.3/12/13/17 | 旧合同/23SQL/范围/分步提交与验证诚实 | 全9paths/README/evidence |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
main a7a392e35eca4ac51a9076853669f4a13eacf3f9；除本Draft外clean，23既有SQL immutable。当前IDW/readonly是已验证依赖，不放宽其风险守卫。
### 4.2 Test-first strategy
仅编译所需的package方法/四字段record可先声明UOE/无guard；Action直接测试与Store/Invocation组合测试各自真实RED后才实现。编译、fixture、依赖失败不算RED。
### 4.3 Sequential and parallel boundaries
Root owns Store/newStoreTest/README三路径；journal owns ActionSql/newActionTest/PG三路径；tool_audit owns InvocationSql一路径；Root接管plan/evidence。各owner不是独自在代码库，不回滚他人；Root统一Maven前所有Java冻结。Step提交前不开始后续Step。
### 4.4 Commit boundaries
单逻辑Step，单commit `feat(agent): consume fixed tool approvals atomically`；手工门禁/严格校验/显式stage/cached审阅后提交，不push或历史改写。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Spec element | Necessity | Existing capability | Decision |
| --- | --- | --- | --- |
| 原子审批消费 | §11.3/12唯一消费/receipt | V13唯一索引与同源事务已有 | 扩三类，不新层/DDL |
| 新派发调用 | §7/13预算/历史 | 原wait已finished/settled | 新UUID直接INSERT，不复活 |
| 历史完成 | §13 partial failure | nonce/identity锁/fact helpers | 独立AA方法复用 |
| 时间精度 | 实际PS clock可nano/PGµ | 原JSON可nano，旧列比较exact | 保proof、列floorµ、parser比floor |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| Action CAS/history | PLAN-REQ-001/003/004 | ToolActionApprovalSqlTest | 最小方法声明/合法Row | AA动作转换 | InvocationSql | Step1 |
| Store+Invocation UoW | PLAN-REQ-001/002/003/005 | ToolApprovedInvocationStoreTest | 声明齐备/Action mock | 原子消费/attempt | futureRecorder | Step1 |
| PG/docs | PLAN-REQ-002/004/005 | Regression/compile | 两组件GREEN | 可显式验收夹具 | 用户runtime | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现AR persistence technical library | 当前结构/JDBC架构保持 | 无新层/CRUD/依赖/Bean/DDL | Step1；MC-ARCH-001 |
| Reuse | codec/Row/budget/transaction/nonce已有 | 复用现工具能力 | 不复制认证/JSON/调度框架 | Step1；MC-REUSE-001 |
| Naming/model/validation | 现不可变VO/typed audit/安全错误 | package typedmetadata，无实体新增 | fullaction/checkedrequest/proof/time/CAS守卫 | Step1；MC-VALID-001 |
| Pattern/time/JSON | UnitofWork/CAS/java.time/Jackson | 保历史与原窗口 | nano proof不改；µ列保守 | Step1；MC-PATTERN-001/MC-TIME-001 |
| Config/Bean/log | 无此面变化 | N/A有范围证据 | 不加无关注解/日志/配置 | Step1；MC-SCOPE-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 原子一致性 | TransactionTemplate/预算服务/CAS | Store.transact与Sql.beginIdempotentWrite | 已有同源10s模式 | reuse | None | Step1；MC-REUSE-001 |
| proof JSON/身份绑定 | Codec.ApprovalProofVO/Binding.requireApproved | 全字段/16KiB/hash/parser已有 | 缺AA写者，非缺parser | reuse | 三类窄扩展 | Step1；MC-JSON-001 |
| 迟到事实 | writeFact/identityRun/requireAttempt | 已有IDW历史nonce | 保留独立AA风险入口 | reuse | None | Step1；MC-TIME-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | 技术Store/Sql与metadata VO命名 | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | 层内技术边界与完整typed/domain guards | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | 仅不可变record，原DTO复用无entity转换 | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | 无业务Bean/log/DI | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 5 | §7/11.3/12/13/17 | JDK/原codec/Spring事务 | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 既有Jackson协议/hash不改，无ordinal | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 无配置/profile变化 | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 9 | §7/11.3/12/13/17 | 复用UnitofWork/CAS实际解决跨表原子性 | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | Instant/Duration/Clock/mono/µ floor | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | 既有AR结构/JDBC技术架构/23SQL不改，Agent generator excluded | 新tests→三prod→PG/docs | §6及逐文件合同 | source/tests/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | call/storage deadlines | Occurred instant | Instant | UTC instant | Internal | N/A绝对时刻 | N/A无UI | 原wall+mono，无reset；call先选µ精确再PDP，≤run/cap/executor/observation/action/toolTimeout | Clock+LongSupplier/DB now | 不用Java lease过期hint替DB | Step 1；TEST-001/002 |
| TIME-002 | proof times/index expiry/consume | Occurred instant | Instant | UTC instant | UTC Z | N/A绝对时刻 | N/A无UI | verified<=consume<expiry；floorExpiry>consume；consumer单独限制当前窗口 | 原proof/SHA不改，DB consumeµ | 旧µ历史保持；parser比较floor | Step 1；TEST-001/002/003 |
| TIME-003 | late completion | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | 历史nonce不要求当前lease/proof有效，不能重派 | 独立10s fact窗口 | 原IDW/readonly不变 | Step 1；TEST-001/002/003 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionApprovalSqlTest.java | 直接SQL participant 的审批CAS、历史与纳秒精度反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | 现action的独立AA锁/消费/完成，不放宽IDW | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java | 真实Store/InvocationSQL/Spring预算流的行为反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | package-only AA事务门面与结果值校验 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | 同源事务内新调用/预算/审批消费原子提交 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 追加真实PG显式验收夹具，保现V13→V14历史 | Step1 |
| MODIFY | README.md | 说明原子消费库、事实与未接线边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-11-58-atomic-tool-approval-journal.md | 锁定本Step精确合同、顺序、验证与门禁 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-atomic-tool-approval-journal.md | 真实执行记录与逐项结论 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Exact APIs.** Store package `consumeApprovalAndBeginDispatch(PreparationRequestVO,ApprovedObservation,EvidenceVO,CheckResult,VerifiedExecutor,String nonceSha256,DispatchWindowVO)` returns `ApprovedWriteDispatchBeginResultVO(action,invocation,budget,attempt)`；`completeApprovedWrite(request,attempt,plainNonceHex,outcome)` returns existing RecordedResultVO。Sql consume uses AuditVO and separate call/storage Instant；Action lockForApproval/action beginApprovedWrite/completeApprovedWrite signatures in File2。无public API变化。

**Admission.** Caller在Step21 positive callback内取得另一真实business SERVICE executor，再选择µ精确actual call deadline后fresh DISPATCH PDP。consumer/executor可以不同client/issuer，但均SERVICE同tenant；原requester不变。Store重建AA动作与完整checked request/boundary/audit/grant，check wall+mono/currentproof；构造Observation不是认证。PDP/Verifier/transport不在事务内运行。call≤run/cap/executor/observation/actionExpiry/effective toolTimeout，审计覆盖实际期限，不能取得决定后暗自round请求。

**Atomic sequence.** 同源10s事务run→capE2(full E1 equality)→完整action锁→own inv/budget。缺action安全unavailable；own UUID已存在只原metadata/budget无attempt，旧AWAITING finished/SETTLED0不可重用；advanced action+新UUID返回action且其余null。仅未过首次expiry、AA AWAITING/nullapproval/null dispatch可消费。新UUID reserve NEW TOOL budget，orphan拒绝；先INSERT新DISPATCHING invocation(epoch1/nonceSHA/actualexecutor/DISPATCH audit/UNKNOWN/nooutcome/no finished)，再action fullCAS写全部七审批列和dispatch；满足即时FK；双写后才fresh fullJOIN。receipt唯一索引已有，CAS0/duplicate安全conflict，失败整笔rollback预算/counters/inv/action。

**Proof/precision.** source+binding+DTO由现codec内部构造、内部SHA，不接受caller digest；namespace同原source域，proof JSON/SHA保fullnano。indexed approval_expires_at=proof.expiresAt.floorµ；parser比较floor并要求floor>consumed。DB µ now作为consume/dispatched/created，verifiedAt<=consume<actualexpiry且currentfullnano/consumer/mono窗口有效。不得修改DTO或仅拒合法nano。既有µ历史兼容，V13/V14不改。

**Completion.** 原identity run锁→AA action→own invocation；原nonce/epoch/executor/cap历史/audit全匹配，不核当前lease/cap/proof。known exact重放，UNKNOWN仅原nonce收敛不重派；先owninv事实，只有当前pointer同代才同步action，否则仅旧inv。保审批组与generic null/非零/超估算实际费用，不settle/伪造0/退款。外层postcheck失败不能撤事实或授权START retry。

**CLAR-002（Root/SQL owner独审批准，测试反证后才修复）.** own invocation若dispatchEpoch>0，action必须保留非空dispatch backlink，否则safe conflict；沿既有IDW begin相同守卫。不同非空current pointer可以是历史新代，不能错误要求same pointer。新17th回归先观察RED再一行guard；9paths/API/事务/费用/无runtime边界不变。

**CLAR-003（仅协作所有权）.** 组合30GREEN后，PG既有测试文件交journal负责新增helper，Root仍统一Maven/最终整合；不改变9路径、API、行为、顺序或验收范围。

**Scope.** 全23既有SQL immutable；旧IDW/readonly方法及风险守卫、SDK/Verifier21/codec/Recorder/native/config不改；无依赖/权限激活/rolegrant/app/DB/browser/push。真实PG仅显式optin夹具，默认compile+skip。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 原子消费审批与新写调用账本
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: a7a392e；V13七列/唯一索引/FK、原codec/预算/nonce helpers。
- Baseline state: 有wait/proof历史结构与来源Verifier，无原子消费写者。
- Observable outcome: 一个新host调用取得nonce attempt，其余只metadata；旧wait不变，late事实保留。
- End state: package-only AA库、受控测试/optin PG夹具；无业务START或native/config。
- Test-first gate: Required — 两组件方法UOE/值无guard供编译，实际RED后才GREEN；compiler/fixture错不算。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionApprovalSqlTest.java`
- Purpose: 直接SQL participant 的审批CAS、历史与纳秒精度反证。
- Symbols: lockForApproval/beginApprovedWrite/completeApprovedWrite 与完整Row parser。
- Repository evidence: 现ToolActionWriteSqlTest/CodecTest合法JDBC fixtures。
- Dependencies and consumers: Action方法先最小UOE声明，旧parser原样；本组件有效RED后功能实现。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: 直接源CAS及fullRow解析；无SQL引擎冒充真实PG。
- Error and edge behavior: 全七审批组/来源与动作/hash/expiryfloor/顺序、回执唯一冲突安全错误。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
assertNanoProofJsonAndShaRetainedWhileIndexedExpiryIsFloorMicros();
assertOnlyEmptyAwaitingActionCanConsumeWithFullCasAndSafeUniqueConflict();
assertHistoricalCompletionPreservesApprovalAndOriginalNonceFacts();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`
- Purpose: 现action的独立AA锁/消费/完成，不放宽IDW。
- Symbols: lockForApproval(action,descriptor,deadline):Row; beginApprovedWrite(Row,ApprovedObservation,DispatchVO,Instant,Instant):void; completeApprovedWrite(Row,Outcome,Instant,Instant):void。
- Repository evidence: 现read/Row/ApprovalConsumptionVO/codec与IDW CAS。
- Dependencies and consumers: File1有效RED；由InvocationSql同源事务调用。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: AA AWAITING→DISPATCHING七列proof+pointer；历史完成保审批组。
- Error and edge behavior: proof JSON/SHA fullnano；索引expiryfloorµ，floor>consume；CAS0/duplicate为safe conflict。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
lockForApproval = lockAndReadExactAaAction();
beginApprovedWrite { requireEmptyAwaitingAndCurrentObservation(); encodeOriginalProofAndInternalSha(); updateSevenApprovalColumnsAndDispatchWithFullCas(); }
completeApprovedWrite { requireHistoricalAaDispatch(); updateOutcomePreservingApprovalGroupWithFullCas(); }
parseApprovalExpiry = proof.expiresAt().truncatedTo(MICROS); // retain original proof JSON/SHA
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java`
- Purpose: 真实Store/InvocationSQL/Spring预算流的行为反证。
- Symbols: 新package consume/complete/四字段ResultVO；旧Rig可复用。
- Repository evidence: 现ToolWriteInvocationStoreTest.Rig与ToolInvocationStoreTest.Fixture。
- Dependencies and consumers: Store/SQL最小声明UOE支持编译，Action mock合法；本组合有效RED后实现。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: 旧wait UUID只metadata、新UUID独立预算；advanced新UUID action-only；完整PDP/cap/lease/window。
- Error and edge behavior: orphan/普通inv冲突、双写回滚、unknown/late/费用与旧generation；controlled JDBC非物理锁证明。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
assertOldFinishedWaitingInvocationAndSettledBudgetRemainImmutable();
assertNewHostUuidGetsFreshReservationAndInvocationBeforeActionCas();
assertOwnerPdpCapabilityLeaseProofAndDeadlineDriftFailClosed();
assertSecondWriteFailureRollsBackAllAndDuplicateReceiptCannotConsumeAgain();
assertOriginalNonceLateFactsAndUnknownRefinementPreserveActualCosts();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`
- Purpose: package-only AA事务门面与结果值校验。
- Symbols: consumeApprovalAndBeginDispatch(request,approval,evidence,checked,executor,nonceSha,window):ApprovedWriteDispatchBeginResultVO; completeApprovedWrite(request,attempt,plainNonce,outcome):RecordedResultVO。
- Repository evidence: 现transact/checkDispatch/writeFact/factWindow/nonce helpers及typed CheckResult。
- Dependencies and consumers: File3有效RED；Root拥有本路径及Test，调用File5；旧public API不变。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: AA重建、full checked request/boundary/audit/grant/current window；结果顺序action/inv/budget/attempt。
- Error and edge behavior: 无ambientTX；µ精确call先PDP后消费；consumer与executor分别SERVICE同tenant；费用不settle。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
consumeApprovalAndBeginDispatch { requireAaActionAndFullCheckResult(); requireExactMicroCallDeadlineAndOriginalWallMonoWindow(); requireCurrentObservation(); return transact(guard, () -> sql.consumeApprovalAndBeginDispatch(...)); }
completeApprovedWrite { requireAaActionAndNonceMatchesAttempt(); return writeFact(() -> transact(factGuard, () -> sql.completeApprovedWrite(...))); }
record ApprovedWriteDispatchBeginResultVO(action, invocation, budget, attempt) { requireAaMetadataAndPairedInvocationBudget(); requireAttemptMatchesBothDispatchMetadata(); }
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`
- Purpose: 同源事务内新调用/预算/审批消费原子提交。
- Symbols: consumeApprovalAndBeginDispatch(request,approval,evidence,AuditVO,executor,nonceSha,callDeadline,storageDeadline); completeApprovedWrite(request,attempt,outcome,storageDeadline)。
- Repository evidence: 现requireRun/currentEvidence/read/requireAttempt/sameDispatch/sameOutcome/identityRun。
- Dependencies and consumers: File3有效RED，Store/Action声明齐备；tool_audit独占本文件。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: run→cap→action→owninv/budget；新inv直接DISPATCHING INSERT，再ActionCAS，再fresh JOIN。
- Error and edge behavior: 缺action拒绝；旧wait UUID不复活；advanced newUUID无新预算；失败整体rollback；历史nonce完成不要求fresh lease/proof。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
var live=requireRun(...); var current=currentEvidence(...); var action=intents.lockForApproval(...);
var own=readOwnInvocation(...); if (own.present()) {
    if (own.dispatchEpoch()>0 && action.dispatch()==null) throw conflict();
    return metadataOnly(action,own);
}
if (!eligibleAwaiting(action)) return metadataOnly(action,null);
validateCallBoundsAndCurrentProofAtDatabaseNow(); reserveFreshToolBudgetOrConflict();
insertFreshDispatchingInvocationWithNonceExecutorAndDispatchAudit();
intents.beginApprovedWrite(action, approval, dispatch, databaseMicroNow, storageDeadline);
return attemptFromFreshJoinedActionAndInvocation();
completeApprovedWrite { identityRun(); lockAaActionThenOwnInvocation(); requireOriginalAttempt(); updateOwnFactFirst(); if (sameCurrentPointer) intents.completeApprovedWrite(...); freshJoinedRead(); }
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`
- Purpose: 追加真实PG显式验收夹具，保现V13→V14历史。
- Symbols: 新AA竞争、唯一receipt、第二写rollback与late receipt helper。
- Repository evidence: 现optin URL/vector patch/@Timeout120/两run IDW夹具。
- Dependencies and consumers: 上述组合GREEN后journal负责PG文件，Root统一编译；无自动DB启动。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: 两个合法run新host UUID同intent一个赢家；旧wait与预算不变；fullnano proof/source unique。
- Error and edge behavior: trigger回滚/原nonce迟到/微秒列与原JSON；无URL时原类skip，绝不记PG通过。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
approvedJournal { prepareWaitingFact(); raceFreshHostIdsAcrossTwoRuns(); assertOneWinnerAndOneFreshBudget(); assertProofNanoAndIndexedFloor(); failSecondWriteAndAssertWholeRollback(); completeLateOriginalNonceWithoutFreshLeaseOrProof(); }
assertTwoRunRaceHasOnlyOneAttemptAndKeepsOriginalWaitingBudget();
assertTriggerFailureLeavesNoFreshInvocationBudgetOrConsumedReceipt();
assertLateNonceCompletionSurvivesExpiredLeaseAndProofWithoutRedispatch();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 7 — `MODIFY README.md`
- Purpose: 说明原子消费库、事实与未接线边界。
- Symbols: 审批消费/新预算/历史完成说明。
- Repository evidence: 现工具journal/审批Verifier文档段落。
- Dependencies and consumers: 最终源码行为与实际日志后更新。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: 明确旧wait不复活，nonce晚fact保留；显式库无Recorder/native/config。
- Error and edge behavior: 真实PG/IAM/START/runtime未验收，来源构造非认证，不隐含retry。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
documentAtomicConsumeAndFreshInvocation(); documentImmutableWaitAndLateFacts(); documentRuntimeBoundary();
documentSevenColumnConsumptionAndFreshHostBudgetInOneTransaction();
documentNanoProofPreservationAndConservativeIndexedMicrosecondExpiry();
documentExplicitLibraryWithoutRecorderNativeConfigOrRealPostgresAcceptance();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 8 — `CREATE docs/egon/plan/2026-11-01-11-58-atomic-tool-approval-journal.md`
- Purpose: 锁定本Step精确合同、顺序、验证与门禁。
- Symbols: 本计划V5/五PLAN-REQ/17MC/10Rules。
- Repository evidence: 前一步a7a392e及已读蓝图/实际源码。
- Dependencies and consumers: 先root全文/strict/resources后才Source GO。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: Review沿原用户exact授权，不伪Accepted；一个commit。
- Error and edge behavior: 无未决API/依赖/范围；只允许机械澄清。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
lockExactBaselineAndNinePaths(); observeActualComponentRedBeforeGreen(); freezeBeforeRootMaven();
requireExactBaselineScopeAndFiveRequirementAliasesBeforeSourceGo();
requireActualTwoComponentRedAndStrictPlanBeforeFunctionalImplementation();
requireSeparateLiteralRulesManualChecksAndPathLimitedCommit();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

#### File 9 — `CREATE docs/egon/evidence/2026-11-01-atomic-tool-approval-journal.md`
- Purpose: 真实执行记录与逐项结论。
- Symbols: 五aliases/17MC/10Rules/REDGREEN/Git。
- Repository evidence: 本计划与实际命令输出/源码审阅。
- Dependencies and consumers: 先PENDING骨架，最终证据后才Verified并stage。
- Why now: 按本序锁定测试与实现；方法声明可预置，行为必须在本组件真实RED后。
- Contract/signature changes: §6 exact package API/元数据；无public protocol扩展。
- Input/output and state mapping: 逐条实际结论，不复制计划PASS为实施PASS。
- Error and edge behavior: skip/fixture/compiler错分类，完整Goal仍Partial。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项；技术库保现结构、类型与异常风格，适用MC均逐项查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；具体§4.8及本块。
- Implementation pseudocode:
```java
recordActualLogsAndManualFindings(); commitOnlyAfterAllApplicableChecksPass();
recordExactRedGreenCommandsExitCodesFailuresSkipsAndFixtureCorrections();
recordEachOfSeventeenManualChecksAndTenLiteralRulesWithSourceEvidence();
recordFinalNinePathScopeAndImmutableTwentyThreeSqlChecksBeforeCommit();
```
- Verification contribution: TEST-001/002/003/004，实际日志与限制写evidence。
- After this file: 完成本文件贡献且不把构造/compile/skip当runtime验收。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace。
- Verification command: §8精确10类canonical command；focused只选新2类，所有Java先冻结。
- Expected result: 两组件有效RED后GREEN，原合同回归通过，PG无URL显式skip并诚实分类。
- Failure returns to: action/precision回File1/2；UoW/metadata/nonce/window回File3/4/5；fixture先修再真实RED；范围/协议漂移回Root计划修订。
- Completion criteria: exact9paths、无stub、23SQL unchanged、17MC/10Rules逐项闭合及独审/source/diff/cached通过。
- Rollback: 回退binary保留已消费收据及历史账本；无schema回滚/drop/repair，不释放receipt/重派UNKNOWN。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionApprovalSqlTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java、README.md、docs/egon/plan/2026-11-01-11-58-atomic-tool-approval-journal.md、docs/egon/evidence/2026-11-01-atomic-tool-approval-journal.md
- Commit: `feat(agent): consume fixed tool approvals atomically`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Expected evidence |
| --- | --- | --- |
| TEST-001 | ToolActionApprovalSqlTest | 新action方法/precision真实RED后GREEN |
| TEST-002 | ToolApprovedInvocationStoreTest | Store+SQL UoW/值/nonce/window真实RED后GREEN |
| TEST-003 | 原optin PostgreSQL | 仅compile且无URL原类skip；真实锁/SQL不认通过 |
| TEST-004 | 旧8类+new2回归/strict/23SQL/cached/17MC10Rules | 全适用PASS后提交 |

Root canonical command（cwd tianji-service），focused只替换-Dtest为上述新类；最终命令：
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolApprovedInvocationStoreTest,ToolActionApprovalSqlTest,ToolWriteInvocationStoreTest,ToolActionWriteSqlTest,ToolInvocationStoreTest,ToolInvocationRecorderTest,ToolIdempotentStartRecorderTest,ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolExecutionJournalPostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-atomic-tool-approval-final.log 2>&1
```
严格校验：workspace `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-11-58-atomic-tool-approval-journal.md --strict`；双skill资源/checklist/source规范已读，最后diff/cached只9paths。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/apply/repair/drop，V13/V14及全部23SQL字节保持。原wait/firstexpiry/唯一收据历史不可删以释放重试；回退二进制保表/已消费事实，不能把UNKNOWN当可重派。显式库未注册runtime，旧public IDW/readonly兼容。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | Step 1 | Store/Action/新两tests | TEST-001/002/003/004 | 完整AA动作/source/当前权限/lease/时间绑定 |
| PLAN-REQ-002 | §7/11.3/12/13/17 | Step 1 | Store/InvocationSql/新tests/PG | TEST-001/002/003/004 | 新host预算与inv+消费CAS同事务，旧wait不可复活 |
| PLAN-REQ-003 | §7/11.3/12/13/17 | Step 1 | 三prod/新tests/PG | TEST-001/002/003/004 | 原nonce历史完成、未知不重派与费用事实 |
| PLAN-REQ-004 | §7/11.3/12/13/17 | Step 1 | ActionSql/两新tests/PG | TEST-001/002/003/004 | fullnano proof与索引保守µ期限兼容 |
| PLAN-REQ-005 | §7/11.3/12/13/17 | Step 1 | 全9paths/README/evidence | TEST-001/002/003/004 | 旧合同/23SQL/范围/分步提交与验证诚实 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| CLAR-001 | nano proof与PGµ列冲突 | JSON/SHA原样、索引floorµ/parser比较floor，真实反证先RED | Root | Approved |
| DEC-001 | 旧wait复活/预算复用 | 禁止；新UUID新budget与直接DISPATCH insert | Root | Closed |
| DEC-002 | consumer冒businessExecutor/当前PDP | 独立身份同tenant，PDP在实际deadline选定后事务外运行 | Root | Closed |
| RISK-001 | postcheck/late fact | 原nonce历史完成，facts不撤/UNKNOWN不重派 | Root | Tracked |
| RISK-002 | controlled JDBC非PG | optin夹具默认只编译/skip，用户实际环境验收 | 用户 | Tracked |

## 12. Review and Acceptance
无未决协议/范围/依赖；以下是计划可执行性门禁，实施evidence初始PENDING。Root全文/strict/hash/checklist后才Source GO；Review沿原用户授权，不伪Accepted。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR现persistence结构/JDBC/Flyway技术库，无新层/CRUD，Agent generator excluded；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-REUSE-001 | Applicable | PASS | 现codec/Row/transaction/budget/nonce/CheckResult/ApprovedObservation复用；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-DEP-001 | Applicable | PASS | 现Spring/JDK/JDBC，无依赖或构建步骤新增；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-NAME-001 | Applicable | PASS | 现Store/Sql技术类型、不可变ApprovedWriteDispatchBeginResultVO；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-VALID-001 | Applicable | PASS | fullaction/source/PDP/capE1E2/lease/window、nonce与CAS边界逐层核验；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-MODEL-001 | Applicable | PASS | 仅不可变metadata record，无新entity/外部DTO；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-CONVERT-001 | Not applicable | N/A | 复用typed Observation与原private codec；无PO/entity/Mapper转换；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无业务日志/Service新增；safe code与无body/cause异常；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | 无此面变化 |
| MC-BEAN-001 | Not applicable | N/A | 技术库手工constructor与现对象不变，无SpringBean/DI新增；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | 无此面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK java.time/原digest与Spring事务，非自建通用工具；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-JSON-001 | Applicable | PASS | 复用Jackson codec且不改其协议，proof JSON/SHA原样；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-TIME-001 | Applicable | PASS | µ精确call先PDP/fullnano proof+floor索引/DBconsume与原mono窗口；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/flag/权限变化；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | 无此面变化 |
| MC-PATTERN-001 | Applicable | PASS | 复用Unit of Work与CAS，事务内原子消费、事务外身份/PDP；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确9paths，23SQL bytefreeze，旧IDW/RO/Verifier/SDK不改；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-TEST-001 | Applicable | PASS | 两个真实RED后GREEN，旧8类回归；PG仅显式前置否则skip；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |
| MC-BLOCKER-001 | Applicable | PASS | 无未决合同；全适用手工规则与测试通过后才提交；§4/6/逐文件与tests | 计划合同闭合，实际证据后评 | None |

### 12.6 Final verdict
PASS — Ready for user review
