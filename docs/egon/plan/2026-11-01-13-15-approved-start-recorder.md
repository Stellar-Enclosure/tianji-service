# 审批型固定START Recorder与原始期限准备

| Field | Value |
| --- | --- |
| Document | `2026-11-01-13-15-approved-start-recorder.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 13:15 Asia/Shanghai` |
| Updated | `2026-11-01 13:15 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR AA Recorder/原entry bounded preparation；9paths |
| Source Requirement | 原蓝图§7/11.3/12/13审批执行、幂等与partialfailure，继续分步提交 |
| Baseline Revision | `main@5fd1267534e8d4b5df7940e209eba27a05c5411b`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `5f5a589e5725d3c30b94d77af61946d6b9c67092f5963de05417db44fdd1ca5e` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [bounded START](2026-11-01-12-50-bounded-start-one-shot-deadline.md)；[atomic approval](2026-11-01-11-58-atomic-tool-approval-journal.md)；[approval verification](2026-11-01-11-13-fixed-tool-approval-verification.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
将已提交来源Verifier、原子消费journal和bounded START组合成显式AA Recorder。复用同一prepare/PREPARE链新增窄metadata projection，原t0/n0覆盖准备与后续调用。旧等待调用不复活；只新host UUID+可信host receipt才消费并START一次。复杂一致性沿现Facade/callback Adapter/UoW-CAS，不新增引擎或层。

## 2. Target Spec and Effective Design
有效蓝图§7/11.3/12/13/17、T3-07；用户原exact继续执行授权持续。baseline5fd已提供新bounded one-shot，3548提供consume/complete，a7提供可信来源callback。现IDW Recorder与DTO拒AA/等待，旧public prepare拒先进own或受旧epoch/deadline重新准入，10s准备window也不接受更短原toolTimeout；本Step只补必要窄路径。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | 同一prepare链上的bounded AA metadata，旧wait不复活 | Store/Sql/StoreTest |
| PLAN-REQ-002 | §7/11.3/12/13/17 | fixed AA host-only receipt/独立SERVICE/原始call/E0E1E2/freshPDP | Recorder/newTest |
| PLAN-REQ-003 | §7/11.3/12/13/17 | atomic consume后bounded一次START与原nonce事实，observed快照准确 | Recorder/newTest/PG |
| PLAN-REQ-004 | §7/11.3/12/13/17 | observer/outer postcheck/accounting partialfailure保事实，无cleanup或重派 | Recorder/newTest/PG |
| PLAN-REQ-005 | §7/11.3/12/13/17 | 旧API/费用/23SQL兼容、真实RED与验证分步提交 | 全部9paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
main5fd1267534e8d4b5df7940e209eba27a05c5411b clean；Spec SHA5f5a589e5725d3c30b94d77af61946d6b9c67092f5963de05417db44fdd1ca5e，23SQL immutable。
### 4.2 Test-first strategy
准备wrapper UOE与新enum声明仅供编译，StoreTest真实RED后准备实现；Recorder方法UOE/record无guards供自身RED，上游GREEN后再运行其合法fixture。compiler/fixture错误不当RED，旧17 Store tests与旧IDW/RO测试主体保持。
### 4.3 Sequential and parallel boundaries
Root owns Recorder/newRecorderTest+README/plan/evidence五路径；journal owns Store/Sql/既有StoreTest三路径；tool_audit owns原PG test一路径且只在core GREEN后写。不是独自在代码库，保他人改动；Root统一Maven前所有Java冻结。
### 4.4 Commit boundaries
九路径一个逻辑Step/commit `feat(agent): record approval-required fixed START calls`；提交前全部手工门禁、独审/cached；不push/改历史/启动runtime。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Element | Necessity | Existing capability | Decision |
| --- | --- | --- | --- |
| AA preparation view | 原wait终态/advanced metadata/原期限 | 原prepareInternal/PREPARE/预算事务 | 窄projection+private bounded overload，不旁路 |
| AA Recorder | §11.3审批后执行一次/§13事实 | 三已提交库与现IDW helpers | 新方法/四字段VO，不放宽旧DTO |
| observed快照 | consume race可能先进且ownnull | prepare.action与begin.action已返回 | 使用实际来源快照，不合成最终状态/新查询 |
| generic费用 | 不能把竞争事实猜0 | Store保null/非零与原metadata | 仅本次实际fixedPS fact可known0 account |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| bounded AA preparation | PLAN-REQ-001/005 | StoreTest | wrapper/enum declarations | safe metadata/原window | Recorder | Step1 |
| AA composition | PLAN-REQ-002/003/004 | newRecorderTest | preparation GREEN/ports committed | fixedAA once/facts | futureNative | Step1 |
| PG/docs/regression | PLAN-REQ-005 | compile/explicit optin | coreGREEN | reviewable9pathcommit | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现AR persistence facade/Store/SQL | 保现结构/JDBC技术架构 | 无新层/Bean/DDL/CRUD | Step1；MC-ARCH-001 |
| Reuse | prepareInternal/ports/nonce/account/codec | 沿原权限与事实机制 | 不新auth/parser/transport/engine | Step1；MC-REUSE-001 |
| Model/validation | 原immutable DTO/guards | 新四字段VO/hostreceipt与fixedAA边界 | 无新实体/泛型转换，旧DTO guards保持 | Step1；MC-VALID-001 |
| Pattern/time | Facade+callback Adapter/UoW-CAS | 原entry+两网络callback部分失败 | typedholder/原nonce/observed快照 | Step1；MC-PATTERN-001/MC-TIME-001 |
| Bean/log/config | 本Step无此面 | 有据N/A | 不加无关Service/日志/YAML | Step1；MC-SCOPE-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 当前准备/预算 | Store.prepareInternal/Sql.prepare | PREPARE/证据/同源事务已有 | wrapper+projection+bound参数 | reuse | None | Step1；MC-REUSE-001 |
| source/business once | Verifier21/Store22/OneShot23 | 可信SERVICE与nonce/actualHTTP bound已有 | 窄Recorder组合，非新协议 | reuse | None | Step1；MC-DEP-001 |
| facts/账务/观察 | 原Recorder bookkeeping/startObservation/account | ACK先fact再observer/account，pending安全 | 新AA分支共享helpers，不走IDW cleanup | reuse | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | ApprovedStartResultVO后缀/现技术roles | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | typed/domain guards与完整source/PDP/nonce/window | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | immutable四字段record，无实体/Mapper转换 | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | 无新Bean/DI/log | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 5 | §7/11.3/12/13/17 | JDK/现SDK/helper/codec | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 原JSON/DTO/receipt协议不改，无ordinal | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 无配置/profile变化 | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 9 | §7/11.3/12/13/17 | 沿现Facade/Adapter/UoW-CAS | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | Instant/Duration/Clock/原nano起点 | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | 现AR结构/JDBC技术架构，23SQL原样，无CRUD/MP迁移，Agent excluded | tests→Store/Sql→Recorder→PG/docs | §6/逐文件 | source/tests/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | entry t0/call/prepare deadline | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | t0/n0在material读取前；call≤t0+effectiveTooltip/run；prepareTech≤t0+10/call，guard贯穿全部prepare/TX | 原Clock/nano双限，不重取起点 | 旧public/IDW/RO window保持 | Step 1；TEST-001/002 |
| TIME-002 | final DISPATCH/OneShot deadline | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | E0早取并cap；E1==E0，StoreE2==E1；final min原entry/action/cap/proof/consumer/executor，先floorµ再fresh PDP；同值传bounded START | 不延长、不在PDP后round | 原proof JSON/SHA/fullnano与索引floor保持 | Step 1；TEST-002/003 |
| TIME-003 | auth/begin与late completion | Duration | Duration | Not persisted | Internal | Not applicable: 技术窗口 | Not applicable: 无UI | freshauth≤10s但actualcall不重加Tooltip；fact原nonce独立窗口不受已过期proof阻止 | 原factClock/bookkeeping恢复中断 | latefact/UNKNOWN不重派 | Step 1；TEST-001/002/003 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java | bounded AA准备projection反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | 同一准备链上的bounded AA门面 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | 窄AA metadata projection，无重新准入旧wait | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedStartRecorderTest.java | 审批型固定START facade行为反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java | 组合fresh approval/业务SERVICE/原子consume/一次START | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | 显式PG Recorder helper，保原迁移/前置 | Step1 |
| MODIFY | README.md | 记录AA Recorder/host receipt/observed metadata与时间边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-13-15-approved-start-recorder.md | 锁定9paths/合同/两RED/17MC10Rules | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-approved-start-recorder.md | 记录实证与限制 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Exact public API.** `ApprovedStartResultVO executeApprovedStart(FixedToolApprovalVerifier verifier,FixedBpmnStartToolTransport transport,PreparationRequestVO request,String hostApprovalReceiptId)` 与追加ToolCommittedFactObserver overload。new public record `ApprovedStartResultVO(String actionIntentId,String actionStateObserved,long actionRevisionObserved,WriteAttemptResultVO ownAttempt)`；own nullable，等待无own。canonical UUID/八actionstates/positive revision/脱敏校验。旧IdempotentStartResultVO/WriteAttemptResultVO等待守卫和所有旧方法保持。

**Observed语义.** early return使用真实prepare.action；调用consume后使用真实begin.action，winner可DISPATCHING+later ownfact，loser先进state+ownnull。Observed只表示一次实际读取，不合成完成后的全局APPLIED，不新增查询/flag。futureNative优先ownoutcome，否则只按observed保守处理；竞争败者不能错误返回AAWAIT/NONE。

**Preparation.** package `ApprovedWriteDispatchBeginResultVO prepareOrLoadApprovedWrite(PreparationRequestVO,DispatchWindowVO entryWindow)`复用22四字段值、attempt恒null。新Projection.APPROVED_RECORDER_METADATA走SAME prepareInternal/fresh PREPARE/currentrun/E1E2/budget chain，private bounded overload贯穿原entry guard。firstaction照原AAWAIT finished/SETTLED0；即使receipt存在也先returnWAIT。samewait UUID只metadata、不以旧cap epoch/dispatch deadline重新准入；advanced own可安全metadata。完整request/owner/lease/budget epoch历史解析保持，不放宽到新lease；已派发own缺action pointer conflict。已有globalaction+newUUID返回action-only不建inv/budget。旧public/IDW/RO behavior保持。

**Entry与身份.** fixedAA START resource+exact instanceId schema+estimated0；host receipt nullable、非null safe canonical UTF8 text1..255，禁止来自model arguments。参数缺失/ambientTX/unverified storage拒绝。t0/n0捕获在ledger.executionMaterial前，material.authorization须等于完整request.run，effective toolTimeout固定；overall实际call≤t0+Tooltip/run。prepare local≤10s，用原起点。metadata advanced/oldwait不调用ports或结算；只确实返回WAIT时、准备TX完成后notify AWAITING_APPROVAL；继续核验分支不能先notifyNONE。

**Fresh dispatch.** 新UUID、action仍AAWAIT、hostreceipt才继续。取E0并核binding，cap原overallcall by action expiry/E0且floorµ；ApprovalRequest用该deadline。positive callback cap proof/consumer，BusinessRequest沿同一originaldeadline；actual SERVICE executor与approval consumer各自认证且同tenant，不要求同client/issuer。business callback重新E1且完整equals(E0)，选最终µcall=min(original/action/E0/approved/executor)，然后fresh DISPATCH PDP exact request/allow audit。单独fresh≤10s auth/begin window但其actualcall保持原bound；Store22在TX核E2==E1/nonce/atomicconsume。only first attempt invokes `operation.start(finalCall)`，绝不fallback旧start。

**Facts/partial failure.** preconsume缺/假/过期receipt、Verifier或transport错误保AAwait，不调用IDW cleanup。consume后任何notSent/cancel/deadline都以原nonce记FAILED_KNOWN，known denial REJECTED，unknown UNKNOWN，ACK SUCCEEDED；映射沿现startObservation/startOperationFailure。完成fact先存holder再notify observer，再本次实际fixedPS known0 account；observer异常holder优先，跳过account/body。外层任一Verifier/START postcheck可隐藏return，不能cleanup/释放receipt/退款/重派或降级已观察effect。metadata/historical/竞争generic null或非零费用不得autoaccount猜0；只本调用实际fixedPS completion可用既有0费用model。UNKNOWN绝不重驱；晚fact不改terminal run。

**Scope/默认.** 9paths；23SQL/ports/SDK/codec/AS/native/config/权限只读，无依赖/DDL/Bean/newengine。PG仅原显式optin夹具，默认compile+skip，不启动app/DB/浏览器。AA native/host receipt resolver/config readiness与MCP后续，不宣称全Goal完成。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 审批型固定START Recorder与原始期限准备
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交5fd/3548/a7；现Recorder/Store/Sql/currentcap/typedPDP/事务。
- Baseline state: 来源/consume/boundedstart库已实现，无完整AA Recorder；旧准备/结果类型不含所需wait/advanced路径。
- Observable outcome: 新host receipt经两可信SERVICE callbacks、freshPDP/原子consume后START一次，结果与真实observed快照可读；旧wait不复活。
- End state: explicit AR库/metadata preparation，可受控验证，无native/config/真实环境启用。
- Test-first gate: Required — 两组件编译前置UOE/record无guard声明，实际合法RED后GREEN，不以compiler/fixture失败替代。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java`
- Purpose: bounded AA准备projection反证。
- Symbols: prepareOrLoadApprovedWrite/request-entryWindow/metadata。
- Repository evidence: 原17tests+AA Rig/Fixture。
- Dependencies and consumers: 新package wrapper UOE声明支持自身RED，旧17正文保持。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: firstwait/samewait/advanced/newhost metadata；original短Tooltip/mono。
- Error and edge behavior: cap epoch/旧dispatch期限不重新准入；fulllease/budgetepoch/owner仍严格。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
assertFirstAaPreparationCommitsFinishedWaitAndSettledZero();
assertRepeatedWaitAndAdvancedOwnMetadataNeverReserveOrRevive();
assertExistingGlobalActionNewHostReturnsActionOnly();
assertOriginalShortEntryDeadlineAndWallRollbackMonoGuardEveryPrepareStageAndTransaction();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`
- Purpose: 同一准备链上的bounded AA门面。
- Symbols: package ApprovedWriteDispatchBeginResultVO prepareOrLoadApprovedWrite(PreparationRequestVO,DispatchWindowVO)。
- Repository evidence: 原prepareInternal/PREPARE/currentcap/TransactionTemplate。
- Dependencies and consumers: File1有效RED后实现，旧public prepare与IDW/RO不变。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: 返回22四字段结果，attempt恒null；private bounded overload复用原pipeline。
- Error and edge behavior: 原t0/n0/entry call cap贯穿各stage与TX，不能只return后检查。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
prepareOrLoadApprovedWrite(request, entry) { requireAaAndEntryBinding(); var rows=prepareInternal(request,APPROVED_RECORDER_METADATA,entry); return metadataWithoutAttempt(rows); }
prepareInternal(request,projection) { return prepareInternal(request,projection,null); }
prepareInternal(request,projection,entry) { reuseSamePipelineWithOriginalStartAndMinimumRunEntryCapabilityDeadline(); guardBeforeAfterCapabilityPdpAndInsideTransaction(); }
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java`
- Purpose: 窄AA metadata projection，无重新准入旧wait。
- Symbols: Projection.APPROVED_RECORDER_METADATA/prepare分支。
- Repository evidence: 原LEGACY/RECORDER/IDW prepare与完整row parser。
- Dependencies and consumers: File1有效RED后功能，Store依赖新enum。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: first action原样创建AAWAIT finished/settled0；已有action新UUID action-only。
- Error and edge behavior: 旧wait/advanced own不查旧capEpoch/dispatch expiry活性；epoch>0但action无dispatch conflict。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
if (projection==APPROVED_RECORDER_METADATA && ownExists) { preserveCompleteIdentityLeaseBudgetParsing(); skipOnlyOldPreparationReadmission(); requireDispatchBacklinkForDispatchedOwn(); returnMetadata(); }
if (existingGlobalActionAndNewHost) returnActionOnlyWithoutBudget();
if (newGlobalAction) reuseOriginalAwaitingInsertAndZeroSettlement();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedStartRecorderTest.java`
- Purpose: 审批型固定START facade行为反证。
- Symbols: executeApprovedStart两overload/ApprovedStartResultVO。
- Repository evidence: 原IDW Recorder tests/realtyped metadata与新bounded ports。
- Dependencies and consumers: Store projection GREEN+Recorder最小UOE/record无guards支持编译；自身有效RED才实现。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: first/samewait/advanced metadata、fresh receipt执行、E0=E1=E2/最终µdeadline。
- Error and edge behavior: consume race observed state/旧费用保留/late facts/observer优先与accountPending。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
assertFirstAndRepeatedWaitNeverVerifyConsumeOrSend();
assertFreshHostReceiptComposesVerifierBusinessTransportPdpAndAtomicDispatchOnce();
assertE0E1EqualityAndOriginalEntryBudgetAndExactBoundedCallDeadline();
assertConsumeRaceUsesActualObservedSnapshotWithoutFalseAwaiting();
assertNotSentDeniedUnknownSuccessFactsAndNoApprovalReleaseOrCleanup();
assertAckAndObserverTruthSurviveBothOuterPostchecksAndAccountingFailures();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`
- Purpose: 组合fresh approval/业务SERVICE/原子consume/一次START。
- Symbols: executeApprovedStart(verifier,transport,request,nullable receipt)[+observer]; public ApprovedStartResultVO(actionIntentId,actionStateObserved,actionRevisionObserved,nullable ownAttempt)。
- Repository evidence: 现5arg constructor/pointers、nonce/bookkeeping/startObservation/startOperationFailure/account。
- Dependencies and consumers: File4自身有效RED后功能；保旧IDW/RO方法与DTO guards。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: 同一entry originalcall，等待或advanced metadata返回；freshhost+receipt才执行。
- Error and edge behavior: preconsume不cleanup；postconsume原noncefact必记；holder/observerfailure先于outer包装。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
captureOriginalEntryBeforeMaterialRead(); requireMatchingRunAndEffectiveToolTimeout();
var prepared=store.prepareOrLoadApprovedWrite(request,entryWindow);
if (mustReturnMetadataOrWait(prepared,receipt)) return observedResultAndNotifyWaitOnlyIfActuallyWaiting();
var e0=currentCapability(); capOriginalCallByActionAndE0ThenFloorMicros();
return approvalVerifier.withVerifiedApproval(fixedRequest, approved -> transport.withVerifiedCall(businessRequestCappedByApproval, (executor,operation) -> {
    var e1=currentCapability(); requireEquals(e0,e1); var finalCall=floorMicros(min(originalCall,approved.expiry,executor.expiry));
    var checked=freshDispatchPdp(finalCall); var nonce=secureNonce();
    var begin=store.consumeApprovalAndBeginDispatch(request,approved,e1,checked,executor,hash(nonce),newAuthWindowWithSameFinalCall);
    observedSnapshotHolder.set(begin.action()); if (!begin.started()) return metadataWithoutDispatchOrSettlement(begin);
    var outcome=invokeBoundedOnceOrClassifyNotSentDeniedUnknown(operation,finalCall);
    var fact=bookkeeping(() -> store.completeApprovedWrite(request,begin.attempt(),nonce,outcome)); committedFactHolder.set(fact);
    observer.onCommitted(fact.status()); return ownResult(fact,knownFixedPsZeroAccountingOnly);
}));
// All catches preserve observerFailure first, sanitize private errors, never cleanup/release/refund/retry AA facts.
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`
- Purpose: 显式PG Recorder helper，保原迁移/前置。
- Symbols: AA Recorder finite helper/controlled verifier/business ports。
- Repository evidence: 原ApprovalFixture/2runs/optinURL/vector/@Timeout120/V13→V14。
- Dependencies and consumers: Core两组件GREEN后tool_audit追加，Root统一编译默认skip。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: wait原UUID/新host boundstart/0account/facts与outer mask。
- Error and edge behavior: 不调用真实HTTP/IAM；default1skip非PG验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
prepareAndReplayDurableWaitWithoutNetworkOrConsumption();
executeNewHostThroughControlledApprovalAndBoundedBusinessPortsWithExactDeadline();
assertObservedSnapshotAndZeroAccountingAndNoSecondStart();
maskOuterReturnAfterCommittedFactAndAssertNoCleanupOrRedrive();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 7 — `MODIFY README.md`
- Purpose: 记录AA Recorder/host receipt/observed metadata与时间边界。
- Symbols: 审批型Recorder段落/旧pending声明必要更新。
- Repository evidence: 现审批Verifier/journal/boundedStart说明。
- Dependencies and consumers: 源码与实际结果后更新。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: explicit库，native/config另步；原USER与SERVICE分离。
- Error and edge behavior: 不称metadata当前authority，不把late failure当rollback/retry。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
documentTrustedHostReceiptAndIndependentServiceIdentities();
documentObservedSnapshotOriginalDeadlineAndOneShotFactOrdering();
documentExplicitRecorderAndRemainingNativeRuntimeAcceptanceBoundary();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 8 — `CREATE docs/egon/plan/2026-11-01-13-15-approved-start-recorder.md`
- Purpose: 锁定9paths/合同/两RED/17MC10Rules。
- Symbols: V5/sourcegate/requirements。
- Repository evidence: 当前蓝图/实际Recorder/Store/SQL/已提交ports。
- Dependencies and consumers: 先Root全文/strict/resources/hash后GO。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: Review沿原用户授权，不伪Accepted。
- Error and edge behavior: 精确Observed fields与原time/cap/partialfailure合同先闭合。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
lockBaseline5fdAndNinePaths();
observePreparationAndRecorderComponentRedBeforeGreen();
requireManualRulesScopeAndSemanticCommitBeforeNextStep();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

#### File 9 — `CREATE docs/egon/evidence/2026-11-01-approved-start-recorder.md`
- Purpose: 记录实证与限制。
- Symbols: 5REQ/17MC/10Rules/REDGREEN/Git。
- Repository evidence: 本plan/实际日志与源码审阅。
- Dependencies and consumers: 先PENDING skeleton，最终逐项闭合。
- Why now: 真实tests先于行为，最小声明仅供编译，按本组件RED后实现。
- Contract/signature changes: §6 exact API/Observed语义，旧合同保持。
- Input/output and state mapping: 如实记录fixtures/failures/skip/runtime未验收。
- Error and edge behavior: 不复制计划PASS或预测提交hash。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块的身份/时间/事实/兼容义务。
- Implementation pseudocode:
```java
recordObservedRedAndGreenWithActualCommandsAndExitCodes();
recordEachManualCheckLiteralRuleAndRequirementAgainstFinalDiff();
recordOptinPostgresCompileOnlyAndExactScopedCommit();
```
- Verification contribution: TEST-001/002/003/004，实际日志/skip/限制写evidence。
- After this file: 本贡献完成，compile/metadata不冒充runtime或权限。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace。
- Verification command: §8 canonical九类；focused选StoreTest或newRecorderTest，所有Java先冻结。
- Expected result: 两有效RED后GREEN；旧方法/旧17test body与回归保持，PG原1skip诚实分类。
- Failure returns to: projection/entryguard回File1/2/3；callbacks/time/observer/fee/metadata回File4/5；PGfixture回File6；范围漂移先Root修计划。
- Completion criteria: exact9paths/23SQL、无stub、17MC10Rules/独审/source/cached闭合后提交。
- Rollback: binary回退保消费与nonce事实；不删/repair账本、不释放receipt或重派UNKNOWN，无schema回滚。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedInvocationStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolApprovedStartRecorderTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java、README.md、docs/egon/plan/2026-11-01-13-15-approved-start-recorder.md、docs/egon/evidence/2026-11-01-approved-start-recorder.md
- Commit: `feat(agent): record approval-required fixed START calls`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Evidence |
| --- | --- | --- |
| TEST-001 | existing StoreTest + 新准备反例 | bounded entry/metadata真实RED→GREEN，原17正文保持 |
| TEST-002 | new ToolApprovedStartRecorderTest | 合法source/ports/state/time/observer/fee fixtures真实RED→GREEN |
| TEST-003 | 原PG helper | default仅compile/原class skip；真实SQL/锁/回滚不认通过 |
| TEST-004 | 九类回归/strict/9scope/23SQL/17MC10Rules | 全适用闭合后单提交 |

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolApprovedInvocationStoreTest,ToolApprovedStartRecorderTest,ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolInvocationStoreTest,ToolWriteInvocationStoreTest,ToolActionApprovalSqlTest,FixedBpmnStartToolTransportTest,ToolExecutionJournalPostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-approved-start-recorder-final.log 2>&1
```
Strict从workspace运行writing-plan validate_plan.py本文件 --strict；current execute-plan资源15md19、writing18md24与Step checklist均按门禁读取；不使用已失效executing-plan路径，不改用户COLA并行rename。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/config/rolegrant，全部23SQL/旧ports与JSON保持。旧RO/IDW public与DTO guards不放宽；private bounded prepare只新projection使用。新方法手工调用，AS/native另步，无自动业务运行。保wait/receipt/nonce历史；回退不能释放审批/把UNKNOWN当可重派。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | Step 1 | Store/Sql/StoreTest | TEST-001/002/003/004 | 同一prepare链上的bounded AA metadata，旧wait不复活 |
| PLAN-REQ-002 | §7/11.3/12/13/17 | Step 1 | Recorder/newTest | TEST-001/002/003/004 | fixed AA host-only receipt/独立SERVICE/原始call/E0E1E2/freshPDP |
| PLAN-REQ-003 | §7/11.3/12/13/17 | Step 1 | Recorder/newTest/PG | TEST-001/002/003/004 | atomic consume后bounded一次START与原nonce事实，observed快照准确 |
| PLAN-REQ-004 | §7/11.3/12/13/17 | Step 1 | Recorder/newTest/PG | TEST-001/002/003/004 | observer/outer postcheck/accounting partialfailure保事实，无cleanup或重派 |
| PLAN-REQ-005 | §7/11.3/12/13/17 | Step 1 | 全部9paths | TEST-001/002/003/004 | 旧API/费用/23SQL兼容、真实RED与验证分步提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | 初次/同UUID wait复活 | finished/settled0元数据，freshhost仅existing action时可consume | Root | Closed |
| DEC-002 | consume race旧快照误WAIT | 新Observed字段用实际prepare或begin来源，不合成全局终态 | Root | Closed |
| DEC-003 | 原entry期限reset/实际HTTP过宽 | E0/E1/E2、原t0/n0、最终µ先PDP并传boundedstart | Root | Closed |
| RISK-001 | nested postcheck抹commit/observertruth | holder优先、原nonce事实，无cleanup/release/retry | Root | Tracked |
| RISK-002 | generic费用/metadata猜0 | 仅本次fixedPS fact0account，othersmetadata不结算 | Root | Tracked |
| RISK-003 | source或PG模拟冒验收 | controlled callbacks/SQL编译skip明确，实际环境用户验证 | 用户 | Tracked |

## 12. Review and Acceptance
以上合同Root与两agent只读设计闭合；原用户exact授权下Review，不伪Accepted。Root全文/strict/resources/hash/checklist后才SourceGO，evidence初始PENDING。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 既有AR facade/Store/Sql结构，Agent generator excluded；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-REUSE-001 | Applicable | PASS | 现Verifier/Start ports、prepareInternal/codec/nonce/account与事务；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/SDK，无新依赖或构建步骤；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-NAME-001 | Applicable | PASS | ApprovedStartResultVO与现技术方法/Projection职责；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-VALID-001 | Applicable | PASS | host receipt/完整run/action/PDP/E0E1E2/原window/observed metadata guards；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-MODEL-001 | Applicable | PASS | 仅immutable新4字段record，旧DTO/普通实体不改；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-CONVERT-001 | Not applicable | N/A | 复用现typed ports/DTO与private codec，无PO/entity映射；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无业务日志新增，safe errors与脱敏VO；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | 无此面变化 |
| MC-BEAN-001 | Not applicable | N/A | 现5arg manual constructor/DI不改，无新Bean；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | 无此面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK java.time/SecureRandom/Atomic与原helpers复用；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-JSON-001 | Applicable | PASS | 无public HTTP JSON或SDK/codec协议变化，无ordinal；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-TIME-001 | Applicable | PASS | t0/n0在material前、Tooltip/run/cap/proof/executor原bounds、最终µ先PDP；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/flag/权限激活变化；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | 无此面变化 |
| MC-PATTERN-001 | Applicable | PASS | 沿现Facade+callback Adapter+UoW/CAS，编排与事实分离；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确9paths，23SQL/旧ports/native/config只读；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-TEST-001 | Applicable | PASS | 准备与Recorder真实RED、旧路径回归、PG只编译默认skip；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |
| MC-BLOCKER-001 | Applicable | PASS | 合同/身份/时间/费用/部分失败与observed语义先锁，最终门禁后提交；§4/6/逐文件/tests | 计划合同闭合，实际实施后逐项核验 | None |

### 12.6 Final verdict
PASS — Ready for user review
