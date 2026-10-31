# T3-07 闭合审批证明与完整写意图历史投影

| Field | Value |
| --- | --- |
| Document | `2026-11-01-04-56-approval-proof-intent-history.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 04:56 Asia/Shanghai` |
| Updated | `2026-11-01 04:56 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | AR closed proof codec/shared outcome shape与package历史fullRow；9路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@d8786996f85a346cc63d98bfc29d59f48dffcecb`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户exact未完成工作执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `8f0d08781788d50ac92c04db262cc088122d520bbfa6ca706b6528246173bbb0` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定START library](2026-11-01-03-39-fixed-bpmn-start-tool-transport.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本步补齐V13已声明写intent的历史数据重建：共享codec闭合approval proof/outcome，原唯一Row parser核八state和origin/dispatch/inv事实一致。新增包内query-only历史入口，不查当前授权、不消费/派发，也不公开body或更SQL。九路径单Step/commit，旧public prepare/current与9metadata投影保持。

## 2. Target Spec and Effective Design

原蓝图§7持久事实/终态、§12稳定intent/审批/receipt、§13安全/审计及T3-07为唯一effective设计。root与codec/history owners已闭合API/状态/查询合同，用户原exact执行授权延续。构造/codec只证明结构与历史匹配，不证明remote来源/签名/currentPDP/approval消费。本计划Review，不新增权限、Readiness配置、网络或消费者。

## 3. Effective Requirements and Acceptance
PLAN-REQ只为既有要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | 私有proof/outcome完整闭合且可canonical重建 | proof各层exactkeys/scalars/UTF8/16KiB/UTCZ、trueonly与历史verifiedAt匹配；outcome五键 | Codec/Test |
| PLAN-REQ-002 | §7/12 | 单parser重建全历史写事实 | fullaction/owner/originimmutable/approvalrawgroup/8state/nullable cost严格，不重绑replayrun | Sql/Test |
| PLAN-REQ-003 | §7/12 | dispatch backlink/fact projection不能漂移 | 完整Owner/ref/risk/intent/generation/nonce/executor、独立参数SHA、nullable JSONB事实一致 | Sql/ActionTest |
| PLAN-REQ-004 | §7/13 | 包内历史query有界只读且不冒充新授权 | own10s RC primary sameDS、无live/PDP/旧expiry门禁或expire写；旧public projection兼容 | Store/Test/2fixture |
| PLAN-REQ-005 | §6/17 | 范围/旧合法编码及真实验证保持 | 新有效RED→GREEN、旧codec/准备/Recorder/PG默认skip；9paths无SQL/派发/消费/API扩大 | tests/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
最小nested值/方法/15fieldRow声明供编译，两个owner各自真实RED后实现。先proof/outcome schema，再唯一Row parser/history Store；同时仅修两直接Invocation Row fixtures成合法fullRow，不建弱9arg ctor。最后旧回归/docs，一个完整commit不留scaffold。
### 4.2 Test-first strategy
codec缺失proof/历史/五键类型真实RED；历史owner测八state/损坏groups/aliases/immutable material/技术时钟/lockfreshjoin。两组件可在各自RED后独立GREEN，不要求另一组件全测完才动。fixture/编译错误不能当RED；实际Errors如实记录。
### 4.3 Sequential and parallel boundaries
codec owner只2paths，history owner4paths(Sql/Store/ActionTest/InvTest)，rootREADME，docowner2paths；锁定nested schema/API后并行各组件RED→GREEN，root统一Maven。无sourceGO前写源码，无下一Step并行。
### 4.4 Commit boundaries
九路径为closed journal history一个逻辑commit，显式stage；不push/改历史/启动环境。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| proof与outcome shape | §12/13/现V13 | 原codec与ToolApprovalBinding/VerifiedApprovalDTO已有 | 扩唯一codec，非action专用JSONparser/新DTO库 | Implement |
| fullRow与backlinks | §7/12/现V13 lifecycle | 原Row只9fields，历史result/approval未解析 | 原row(ResultSet)扩展，不拷贝ToolInvocationSql七state全parser | Implement |
| 内部historical | §7历史事实 | 原Store只current-live metadata | package query-only入口，原ledger读immutable，无新public bodyAPI | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| closed codec | PLAN-REQ-001 | exact proof/outcome边界 | 最小nested值；原域验证 | canonical approval proof/SHA | fullRow parser | Step 1 |
| fullRow/history | PLAN-REQ-002/003/004 | 8state/joins/时间/事务正反 | 上项、原ledger/sameDS | 内部历史事实 | 后续写consumer，非本步 | Step 1 |
| 兼容/真实证据 | PLAN-REQ-005 | 原测试/2fixture/optin skip | 上项 | old public9/schema保持 | 用户审查 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | 原AR/persistence技术库/codec/transactionparticipant | 原profile保持 | 不新增层/权限/配置/DTO/重复parser | Step1；MC-ARCH-001、MC-REUSE-001 |
| Validation/model | 原immutable Action/ApprovalBinding/VerifiedApprovalDTO | 闭合不等于远端认证 | nested真值record/完整nullable groups/owner/source/状态校验 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time | 原manual技术构造/strict Jackson/JDK时钟 | 无业务Bean/日志面 | sanitized/redacted、UTCZ、canonical bytes SHA、historical clock分离 | Step1；MC-BEAN-001、MC-JSON-001、MC-TIME-001 |
| Pattern | 原single codec/row/participant | 复用变化点 | 强化原parser而非第二审批/状态机引擎 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| proof/domain | ToolApprovalBinding.requireApproved/VerifiedApprovalDTO/原codec | 已有18fields/历史时间/strictMapper | 缺closed持久格式 | 扩原codec | None | Step1；MC-REUSE-001 |
| source/history | ledger.executionMaterial/原Row/Store | 已有immutable快照验证/RC事务 | 缺fullrow/projected joins | 同parser/原ledger复用 | None | Step1；MC-DEP-001 |
| projection与schema | V13已43columns/8state/5key outcome | 旧SQL不可改 | 足够，无migration | 只读现schema | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/12/13/T3-07 | 包内VO/Row/codec/store命名 | tests→codec→Sql/Store | ApprovalProofVO唯一，职责与redacted明确 | 编译/source | Step1 | PASS |
| Rule 2 | §6/7/12/13/T3-07 | 闭合JSON/fullaction/groups/BooleanTRUE/ownerjoin | Codec/Action tests+Sql/Store | 每技术边界完整验证，历史不当当前许可 | 正反/损坏矩阵 | Step1 | PASS |
| Rule 3 | §6/7/12/13/T3-07 | immutable record与现域值复用 | codec nested/Row15 | 无弱9arg/实体record/复制Mapper层 | construct/fixture | Step1 | PASS |
| Rule 4 | §6/7/12/13/T3-07 | manual技术JDBC/codec无Bean/日志Service | 全scope | 无新增业务DI面，不伪称Lombok注入 | diff | Step1 | N/A：无业务Bean或配置面 |
| Rule 5 | §6/7/12/13/T3-07 | JDK/Jackson/Spring现库 | codec/Sql/Store | 不加utility/依赖，SHA/事务原生 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/12/13/T3-07 | 共享codec proof与outcome闭合 | CodecTest/codec | exactkeys/scalars/UTCZ/UTF8/重编码SHA，无新持久字段 | JSON boundary | Step1 | PASS |
| Rule 7 | §6/7/12/13/T3-07 | 无profile/flags/config | scope/docs | 不公开历史body/消费入口 | diff | Step1 | N/A：无业务Bean或配置面 |
| Rule 9 | §6/7/12/13/T3-07 | single codec/parser+transaction participant | Sql/Store/tests | 不复刻invocation/审批引擎，现变化点扩展 | 8state/顺序 | Step1 | PASS |
| Rule 10 | §6/7/12/13/T3-07 | Instant/Clock/mono/consume历史时间 | codec/Sql/Store | verifiedAt不用now；10s技术限不min旧expiry | 时间/late | Step1 | PASS |
| Rule 11 | §6/7/12/13/T3-07 | 原AR persistence/冻结V13 | 九路径 | 不新层/SQL/权限/API/Grant/消费 | scope/兼容 | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | proof occurred/verified/expires、consumed | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: 绝对历史事实 | Not applicable: 无UI | occurred<=verified<expiry且verify窗口<=30s，created<=consumed且verified<=consumed<expiry；historical requireApproved用proof.verifiedAt而非now | 原Instant.toString规范，DB现MICROS，不向上延长 | 历史已过proof/run/actionexpiry仍可读，不授当前权 | Step 1；TEST-001/002 |
| TIME-002 | action created/updated/expires/closed | Occurred instant | Instant | UTC instant | Internal | Not applicable: 绝对事实 | Not applicable: 无UI | updated>=created，expires>created且<=created+7d，closed>=created且updated>=closed；lateclosed/updated可超业务expiry | 原DBtime/精度不改 | 原firstexpiry/public9保持，无自动EXPIRED更新 | Step 1；TEST-002 late/history |
| TIME-003 | historical storageWindow | Duration | Duration | Not persisted | Internal | Not applicable: 技术elapsed | Not applicable: 无UI | 原entrywall+mono≤10s，RC/query5s primary sameDS；不min任何旧run/action/proofexpiry | 原Clock/LongSupplier/DBclock，不重启窗口 | publicprepare/current原独立授权/lease窗口保持 | Step 1；TEST-002 事务/回退时钟 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java | 先定义proof/source及既有outcome的严格边界 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java | 定义完整历史parser/查询与旧public兼容 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java | 仅两个直接Row fixture适应完整不变量 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java | 复用唯一codec存闭合审批历史proof与五键outcome | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | 扩原唯一fullRow parser与joined历史投影 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java | 包内历史事实只读入口与旧public投影兼容 | Step1 |
| MODIFY | README.md | 说明完整历史事实与消费/授权边界 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-approval-proof-intent-history.md | 真实日志及五别名/两矩阵报告 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-04-56-approval-proof-intent-history.md | strict锁定九路径与闭合codec/history合同 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline d878699 clean、blueprintSHA已核，writer18md24refs PASS；所有既有source/tests/selector已rg核，InvocationStoreTest恰两直接Row构造。九路径外不改SQL、ARnative、AS/PS/C、Grant/PDP/Budget、ToolInvocationSql/Store产线、配置/权限/依赖/API；V13 SHA保持f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3，禁止repair。

Codec nested package ApprovalSourceVO(String sourceId,long tenantId,String issuer,String resource)：sourceId stable key≤128/tenant>0，issuer/resource为exact ASCII normalized HTTPS String，issuer≤2048/resource≤512，无userinfo/query/fragment。sourceId是host registry稳定logical PS authority，不准用alias/consumer/policy/tool/model变体绕消费namespace。namespaceSha256=OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_APPROVAL_SOURCE_V1",sourceId,Long.toString(tenantId),issuer,resource),Map.of())；此为本步新encodingTag，不声称等于SQL已有标签。

唯一nested ApprovalProofVO(int schemaVersion,ApprovalSourceVO source,ToolApprovalBinding binding,VerifiedApprovalDTO approval)；root四keys={schemaVersion,source,binding,approval}，source四keys={sourceId,tenantId,issuer,resource}；binding七keys={consumer,policyRef,policyBindingSha256,releaseVersion,definitionKey,definitionSha256,taskDefinitionKey}；consumer四keys={tenantId,subjectType,subjectId,issuerFingerprint}；approval十八keys={policyRef,policyBindingSha256,requestSha256,approvalReceiptId,requesterType,requesterSubjectId,sourceStartCommandId,scopeId,taskId,releaseVersion,definitionKey,definitionSha256,taskDefinitionKey,sourceRequestSha256,approved,occurredAt,verifiedAt,expiresAt}。schemaVersion integral1，tenant integral long，enums/strings严格、approved显式JSON boolean true。false是合法SDK投影但不可形成consumed proof。所有objects exactkeys，无unknown/missing/duplicate/trailing/coercion/无效UTF8，总UTF8≤16384bytes，3times须canonical Instant.toString UTCZ，非numeric/offset/非规范格式。所有VO.toString固定redacted，构造仅intrinsic结构不认证。

方法package String approval(ApprovalProofVO,ToolActionRequest)、ApprovalProofVO approval(String json,String expectedSha256,ToolActionRequest)、String approvalSha256(ApprovalProofVO,ToolActionRequest)。writer/reader均核sourceTenant=ownerTenant=consumerTenant、SHA256(exact issuer UTF8)=consumer.issuerFingerprint，原binding.requireApproved(action,approval,approval.verifiedAt())核全action/query/sourcehash/model/task及owner/consumer原sameissuer规则，不用Clock.now。proofSHA为canonical排序properties/mapkeys/UTCZ重新encode的UTF8 SHA256，非原JSON字节或PGjsonb::text；字段序/空白不改变语义SHA。

本步同时补共享outcome decoder exact五keys={status,output,businessReceiptId,errorCode,actualCost}与类型闭合：status必须string合法enum，output必须object，receipt/error null或string，actualCost null或合法numeric再交原ToolExecutionOutcome.validated核bounded/scale与descriptor。缺失nullable key也拒绝，不新action专用JSONparser、不改所有旧合法编码。该shape自V13已明确强制，原mapper可能漏missingkey/标量强转，新CodecTest先真实RED再修。

Row精确15字段：ToolActionRequest action,ToolDescriptor descriptor,String originRunId,String originSnapshotSha256,String state,long revision,Instant createdAt,Instant updatedAt,Instant expiresAt,ApprovalConsumptionVO approval,DispatchVO dispatch,String sideEffectOutcome,ToolExecutionOutcome outcome,String lastErrorCode,Instant closedAt。旧九accessors与public PreparedActionResult九字段保持；不加弱旧9arg ctor。ApprovalConsumptionVO(ApprovalProofVO proof,String proofSha256,Instant consumedAt)，DispatchVO(String runId,UUID invocationId,long epoch,String nonceSha256,TrustedAgentActor executor)，均Sql嵌套package/redacted，dispatch executor为actual SERVICE/同tenant而非假设等于原owner。

原唯一row(ResultSet)重建Action，比较完整logical_identity SHA、intent UUID/requestSHA、descriptor/backend/policy/owner/risk/approval_policy_ref。origin保持first-run，另复用ledger.executionMaterial(owner,new GetAgentRunQuery(originRunId))核immutable owner/run/snapshotSHA、tool ref/allowed version/risk approval policy；dispatch material同核owner/run/descriptor policy。不是新snapshot JSONparser，不重绑当前replayrun，不查live/status/lease/deadline/currentcap/grant/issuer/PDP。origin/dispatch immutable material读取是额外ledger query；事实tuple使用下面同一joined statement，不伪称全流程只有一个SQL。

raw approval七列全null OR 全present+approved TRUE；后者decode proof并逐项核namespace/receipt/proofSHA/expiry/true与VO相等，created<=consumed、proof.verifiedAt<=consumed<proof.expiresAt。IDEMPOTENT_WRITE所有state approval必须null；APPROVAL_REQUIRED已positive dispatch必须完整proof。各state完整矩阵如下（generic chronology/revision与typed outcome域验证均必须成立）：

| action state | dispatch | approval | outcome/receipt/error | effect/closed |
| --- | --- | --- | --- | --- |
| PREPARED | absent/epoch0 | IDEMPOTENT_WRITE null；APPROVAL_REQUIRED complete | 均null | NONE/closednull |
| AWAITING_APPROVAL | absent/epoch0 | risk必须APPROVAL_REQUIRED且proofnull | 均null | NONE/closednull |
| DISPATCHING | complete positive | approvalrisk必须complete | 均null | UNKNOWN/closednull |
| APPLIED | complete positive | 同上规则 | typed SUCCEEDED/nonnull exactreceipt/errornull | CONFIRMED/closedpresent |
| REJECTED | absent OR complete | approvalrisk可null或complete；positive必须complete | typed REJECTED/receiptNULL/matchingnonnullerror | NONE/closedpresent |
| FAILED_KNOWN | absent OR complete | 同REJECTED | typed FAILED_KNOWN/receiptNULL/matchingnonnullerror | NONE/closedpresent |
| UNKNOWN | complete positive | approvalrisk必须complete | typed UNKNOWN/receiptNULL/matchingnonnullerror | UNKNOWN/closedpresent |
| EXPIRED | absent/epoch0 | approvalrisk可null或complete | outcomeNULL/receiptNULL/INTENT_EXPIRED | NONE/closedpresent |

nonSuccess typed output为空；费用保持原generic nullable/bounded，不全局强制0。lateclosed/updated可以晚于原run/action/proofexpiry；stored expiry仍firstfixed>created且≤7d，closed/updated正常chronology。

joined aliases：a=action_intent，o=origin agent_run(tenant+originrun)，d=dispatch agent_run(tenant+dispatchrun)，i=invocation(tenant+dispatchrun+UUID)。SELECT a.*加四Boolean：origin_owner_matches（o存在+fullowner一致）；dispatch_owner_matches（epoch0 true，否则d存在+fullowner）；dispatch_identity_matches（epoch0 true，否则i存在+fullowner/intent/toolkey/version/SHA/backend/risk/run/UUID/epoch/nonce/executor全等）；dispatch_fact_matches（epoch0 true，否则i.state=APPLIED?SUCCEEDED:a.state，a.result IS NOT DISTINCT FROM i.outcome，receipt/error同nullable比较，effect相等）。全部要求Boolean.TRUE，NULL不可绕过。scalar i.parameters_sha256 AS dispatch_parameters_sha256在Java比descriptor.parametersSha256(action.arguments())，绝非action.requestSHA。LEFT JOIN保留缺失/腐坏backlink以failclosed，而非误作无intent。只核投影一致，不声称复刻fullinv/audit/cap/budget parser，不改Invocation产线。

prepare lock=true先仅按exact tenant/type/issuer/id/intent SELECT intent_id FOR UPDATE；等待后再fresh joined SELECT，不能给outerjoin FORUPDATE或复用wait前joined snapshot。current/history lock=false使用single MVCC joined fact query。包内Store Optional<Row> historical(action,descriptor)须verified/noambient、fullactiondescriptor匹配、sameDS自有10s READ_COMMITTED query-only事务，readOnly=false以保持primary guard。Sql participant historical(action,descriptor,storageDeadline)须activeTXsameDS/primary/UTF8/origin/技术时限；原queryTimeout5s。wall+mono10s不min旧业务期限，无expire/update/PDP/网络。publicprepare/current仍原current authority与live lease/九metadata，不公开proof/result体。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 闭合审批证明并只读重建完整写intent历史
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交d878699与原V13/typed Action/ApprovalBinding/codec/Store/ledger。
- Baseline state: 原codec没有approval proof，outcome五键不完全显式；Row9只投影prepared metadata，不解析完整历史group。
- Observable outcome: 单codec/单Row parser严格还原八state事实，包内history无新授权/写动作，旧public元数据兼容。
- End state: closed proof与完整内部历史可供后续consumer审阅；派发/审批消费/新public体读取未交付。
- Test-first gate: Required — 最小类型/API声明先供编译，各owner有效新行为RED→GREEN；不增加弱9arg constructor或放宽fixture。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java`
- Purpose: 先定义proof/source及既有outcome的严格边界。
- Symbols: ToolExecutionJsonCodecTest.java；§6精确字段/方法。
- Repository evidence: 原Audit严格测试/descriptor/outcome回归。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: 最小nested类型/方法声明可先供编译；新行为真实RED后实现。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: closed所有层/18字段/trueonly/UTCZ/16KiB/UTF8/重复unknownmissingtrailingcoercion；历史已过expiry仍结构可读、source/consumer/action漂移拒绝。
- Error and edge behavior: canonical重编码SHA对原JSON空白/字段序无关；outcomeexact5keys/nullable类型；所有错误无私密cause。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
@Test approval_and_outcome_records_are_closed_canonical_historical_values() {
  assertAllNestedFieldSetsTypesAndTrueOnlyConsumptionProof(); assertUtcZUtf8LimitDuplicateUnknownMissingTrailingAndCoercionRejected();
  assertHistoricalVerifiedAtValidationAcceptsOldExpiryButRefusesActionSourceOrConsumerDrift();
  assertCanonicalReencodingShaIgnoresJsonWhitespaceOrder(); assertOutcomeExactlyFiveKeysAndCorrectNullableTypes();
}
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java`
- Purpose: 定义完整历史parser/查询与旧public兼容。
- Symbols: ToolActionIntentStoreTest.java；§6精确字段/方法。
- Repository evidence: 现受控JdbcTemplate/ResultSet/transaction/verifier/Clock fixtures。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: fullRow15/Consumed3/Dispatch5与historical最小声明先编译，再parser/history真实RED。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: 八state/approvalgroup/dispatch/link/projection一致性正反、origin不同run回放、late facts与genericcost；旧public9字段不泄新内容。
- Error and edge behavior: historical无PDP/live/expire写，10s wall+mono/primary/sameDS/ambient拒绝；prepare先action锁再freshjoin。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
@Test historical_facts_reconstruct_one_full_row_without_current_authority_or_mutation() {
  arrangeEightValidActionStatesAndProofDispatchGroupsUsingRealCodecAndParser(); assertCompleteHistoricalRowsAndUnchangedPublicMetadata();
  assertCorruptOwnersOriginSnapshotProofBacklinksJsonbAgreementAndIndependentParameterShaFailClosed();
  assertPrimaryOwnedTenSecondQueryOnlyTransactionWithNoPdpOrBusinessExpiryFence(); assertLockThenFreshJoinAfterWait();
}
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java`
- Purpose: 仅两个直接Row fixture适应完整不变量。
- Symbols: ToolInvocationStoreTest.java；§6精确字段/方法。
- Repository evidence: 已rg恰两处new ToolActionIntentSql.Row，原prepare/replay assertions。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: expanded15field声明后调整8state replay和新preparefixture，不能新增弱9arg ctor。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: 提供各state合法approval/dispatch/outcome/effect/error/closed群组，不删原replay/no-new-budget断言。
- Error and edge behavior: 仅test fixture，ToolInvocationStore/Sql产线不改，不假终态凑compile。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
fixtureRowsForInvocationPreparation() {
  replaceBothDirectRowConstructionsWithValidFifteenFieldRowsAndStateSpecificApprovalDispatchOutcomeGroups();
  preserveAllExistingReplayCreationBudgetAndExpiryAssertions(); rejectAnyWeakLegacyNineArgumentConstructorShortcut();
}
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java`
- Purpose: 复用唯一codec存闭合审批历史proof与五键outcome。
- Symbols: ToolExecutionJsonCodec.java；§6精确字段/方法。
- Repository evidence: 原JsonMapper/JavaTimeModule/strict audit/私密sanitized边界。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: ApprovalSourceVO/ApprovalProofVO及approval三方法；共享outcome reader exact5key验证，原合法编码保持。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: §6精确nestedschema、source namespace、historic requireApproved、canonical proofSHA；outcome类型闭合复用ToolExecutionOutcome.validated。
- Error and edge behavior: 无Clock/PDP/签名/消费许可；rootfalseapproved SDK合法但不能成为consumedproof；JSONerror固定无cause。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
ApprovalProofVO approval(String json,String expectedSha256,ToolActionRequest action) {
  requireUtf8AtMost16KiBAndExactNestedKeysAndScalarTypes(); value = reconstructExistingDomainAndVerifiedApprovalRecords();
  requireSourceConsumerOwnerAndIssuerShaMatch(); value.binding().requireApproved(action,value.approval(),value.approval().verifiedAt());
  requireExpectedShaEqualsSha256CanonicalReencodedUtf8(value)); return value;
}
ToolExecutionOutcome outcome(String json,ToolDescriptor descriptor) { requireExactlyFiveOutcomeKeysAndTypes(); returnExistingTypedValidatedOutcome(); }
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java`
- Purpose: 扩原唯一fullRow parser与joined历史投影。
- Symbols: ToolActionIntentSql.java；§6精确字段/方法。
- Repository evidence: 原row(ResultSet)/read/prepareWithStatus、V13四十三列及ledger.executionMaterial。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: Row15、ApprovalConsumptionVO3、DispatchVO5；package historical(action,descriptor,storageDeadline)，所有toString redacted。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: §6八state/owner/fullaction/proof/rawgroup/backlink/fact与parametersSHA校验；ledger额外immutable源读取不查live。
- Error and edge behavior: prepared lock只先锁action再freshjoin；current/history单fact MVCC joinedquery；不要copy invocation审计/cap/budget七state parser。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
Optional<Row> historical(action,descriptor,storageDeadline) {
  requireActiveSameDataSourcePrimaryUtf8OriginAndTechnicalDeadline(); row = readExactOwnerIntentWithJoinedAliasesWithoutLocks();
  reconstructFullActionAndVerifyOriginalImmutableMaterialWithExistingLedger(); requireApprovalAndStateGroupsAndDispatchProjectionAgreement(); return row;
}
prepareRead(lock) { if(lock) lockOnlyExactActionIntentForUpdate(); returnFreshJoinedSelectUsingTheSameSingleRowParser(); }
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java`
- Purpose: 包内历史事实只读入口与旧public投影兼容。
- Symbols: ToolActionIntentStore.java；§6精确字段/方法。
- Repository evidence: 原owned transaction/verified flag/Clock+nano/9metadata projection。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: package Optional<ToolActionIntentSql.Row> historical(ToolActionRequest,ToolDescriptor)，不增public读体。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: own bounded10s RC sameDS query-only flagfalse、独立technical window不min旧expiry；调用Sql历史parser。
- Error and edge behavior: rejectambient/unverified/changedaction；无freshPDP/live/lease/cap/issuer查询；不更新EXPIRED或授执行许可。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```java
Optional<ToolActionIntentSql.Row> historical(action,descriptor) {
  requireNoAmbientTransactionVerifiedStorageAndReconstructedActionDescriptorMatch(); window = technicalWindowFromOriginalWallAndNanoNowPlusTenSeconds();
  return transact(window,() -> intents.historical(action,descriptor,window.deadlineAt()));
}
publicPrepareAndCurrent() { preserveExistingCurrentAuthorizationLiveLeaseAndNineFieldMetadataProjection(); }
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 7 — `MODIFY README.md`
- Purpose: 说明完整历史事实与消费/授权边界。
- Symbols: README.md；§6精确字段/方法。
- Repository evidence: 原intent/codec/readonly描述。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: 明确package历史仅技术投影，canonical proof不验签/不consume，原public metadata保持。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: 不声称派发/approve/费用0、真实PG或新public detail API已交付。
- Error and edge behavior: 不声称history是当前授权/消费或新public读体API；真实PG与写运行未验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```text
Document closed approval proof encoding and full eight-state intent history projection.
Distinguish historical verified-time reconstruction from fresh authority, signature validation or approval consumption.
Keep public preparation metadata, immutable SQL and unimplemented dispatch/consumer/runtime boundaries explicit.
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-approval-proof-intent-history.md`
- Purpose: 真实日志及五别名/两矩阵报告。
- Symbols: 2026-11-01-approval-proof-intent-history.md；§6精确字段/方法。
- Repository evidence: 既有evidence格式。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: 实施前PENDING，实际后按17MC/10Rules/source/log逐项重评。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: fixture/stub错误分类；PG仅compile/缺optin skip不称运行；本步无网络/crypto/消费。
- Error and edge behavior: 未执行阶段PENDING，stub/fixture/编译错误分类，不预填真实PG或功能通过。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```text
Record actual component RED and selected GREEN exit counts and timestamps after root executes them.
Evaluate seventeen MC and ten literal Rule rows against the full source, historical state matrix and nine-path diff.
Trace five local aliases and preserve full T3-07 Partial and the unverified real-runtime boundary.
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

#### File 9 — `CREATE docs/egon/plan/2026-11-01-04-56-approval-proof-intent-history.md`
- Purpose: strict锁定九路径与闭合codec/history合同。
- Symbols: 2026-11-01-04-56-approval-proof-intent-history.md；§6精确字段/方法。
- Repository evidence: 蓝图/当前实际代码/两owner最终决定。
- Dependencies and consumers: 原codec/域值/ledger/transaction与后续历史consumer，本步无新public body API。
- Why now: Review/legacyexact授权、同组件RED barrier、单Step/commit；无新依赖/API/SQL。
- Contract/signature changes: 本文件落实§6新增包内合同，旧public方法/9metadata/合法编码保持。
- Input/output and state mapping: 真正范围/合同冲突回root；不先source/伪造Accepted。
- Error and edge behavior: 真正合同/依赖/范围冲突回root，不默认扩路径或伪造Accepted/先写源码。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；技术库/immutable值复用。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；按以上closed历史/nullable/source/time/兼容职责执行，N/A面见§12。
- Implementation pseudocode:
```text
Lock baseline d878699, fresh blueprint hash, exact nine paths and the shared codec and historical-row APIs.
Check all eight state/proof/dispatch groups, immutable origin reads and query-only technical deadlines before implementation.
Run strict Plan validation and root full review before source GO without adding a public historical-body reader.
```
- Verification contribution: TEST-001/002/003；实际计数与错误类型由报告填写，不推导真实PG/身份验收。
- After this file: 本组件独立RED→GREEN/兼容或文档门禁闭合后推进；没有写消费或运行激活。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolInvocationStoreTest,ToolInvocationRecorderTest,ToolApprovalBindingTest,ToolActionRequestTest,ToolExecutionJournalPostgresTest,ToolExecutionStorageVerifierTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 组件有效RED后选定8类GREEN；PG类缺原optin只compile/skip，不启动DB；旧public/Invocation/Recorder兼容与完整history严格通过，实际计数以日志为准。
- Failure returns to: fixture/compile回对应Test；proof/JSON回Codec；group/join/immutable/time回Sql/Store；需SQL/publicAPI/PDP/消费扩展回root不自行扩大。
- Completion criteria: 五别名/17MC/10Rule实际证据、exact9paths、无弱fixture/重复parser、独立审阅无阻断与指定回归通过。
- Rollback: 仅九路径回退/forward-fix，保留现SQL/journal/已存事实，无migration/repair或删除历史。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStoreTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentStore.java、README.md、docs/egon/evidence/2026-11-01-approval-proof-intent-history.md、docs/egon/plan/2026-11-01-04-56-approval-proof-intent-history.md
- Commit: `feat(agent): validate approval proofs and intent history`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7命令focused Codec/ActionStore→完整8类 | proof/五键类型/8state/历史查询新行为 | 实际RED→GREEN，fixture/编译/stub错误分开 | root Maven only |
| TEST-002 history safety | tianji-service | Codec/ActionStore tests+两Inv fixtures | 源/owner/approvalrawgroup/joins/JSONB+paramSHA；late事实/10s wallmono/primary/锁后freshjoin | 历史expired可读不授新权；generic fee不强制0、noexpirewrite、public9不泄体 | fake JDBC，非真实PG/crypto/PDP |
| TEST-003 scope/compat | tianji-service | diffcheck/9paths/V13sha/原Invocation/Recorder/Approval/Action/Verifier回归 | 不改SQL/Invocation产线/PDP/Grant/配置；outcome合法编码保持 | exact5keys严格新负例，不放宽旧合法值；无新public接口 | 无服务/网络启动 |
| TEST-004 optinPG | tianji-service | 原ToolExecutionJournalPostgresTest随selector编译 | 既有PG fixture兼容 | 缺原显式URL/vector前置skip，不自动开库；不能声称SQL已跑 | PG unverified |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-04-56-approval-proof-intent-history.md --strict` | metadata/9files/trace/MC | exit0，非实现验收 | 文档owner |

## 9. Migration, Compatibility, Rollout, and Rollback
无migration/SQL/配置/权限/依赖变化，V13 immutable。旧prepare/current九metadata/firstexpiry/current授权保持；packagehistorical只trusted内部事实，不建立用户读体权限。所有旧合法codec编码兼容，新拒绝V13本已不允许的缺键/错类型/损坏group。无自动状态修复/过期写/派发/审批消费/真实网络。用户自备环境后续验收，不启动app/DB/GUI/浏览器。回滚九paths保留SQL/history，不repair。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13 | Step 1 | Codec/CodecTest | TEST-001/002 | closed proof/outcome与历史verifiedAt/canonical SHA，非签名/PDP |
| PLAN-REQ-002 | §7/12 | Step 1 | Row/parser/ActionTest | TEST-001/002 | 全action/immutable source/proof/state历史重建 |
| PLAN-REQ-003 | §7/12 | Step 1 | joined aliases/dispatch parametersSHA | TEST-002 | fullowner/link/fact投影agreement，不复制invfullparser |
| PLAN-REQ-004 | §7/13 | Step 1 | Store historical/事务/2fixtures | TEST-002/003 | internal query-only技术窗口，public9保持 |
| PLAN-REQ-005 | §6/17 | Step 1 | 全九路径/旧回归/optin/docs | TEST-003/004/005 | 无SQL/API/消费扩大、真实范围证据 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | 历史proof过期/namespace混淆 | 新encodingTag/source stable registry；requireApproved用原verifiedAt，rawconsume时序独立 | root/codecowner | Closed：不声称SQL旧tag/签名/current许可；唯一ApprovalProofVO |
| DEC-002 | fullinv重复parser/并发wait旧join | 单actionparser只投影agreement，先actionlock再freshjoin；immutableledger额外读 | root/historyowner | Closed：不改Invocation产线或新snapshotparser |
| DEC-003 | 五键outcome缺失/null类型 | V13已明确五键shape，共享codec先RED补严格closure | root | Closed：无新JSONparser/SQL/合法编码变化 |
| RISK-001 | 无真实PG/消费/权限验收 | 仅fakeJDBC及内部history，无currentPDP | 用户/root | Tracked：完整T3-07/平台Partial，后续consumer单独实现/验收 |

## 12. Review and Acceptance
本计划只闭合已存在journal schema的私有编码/历史重建；九路径、单codec/Row parser、原ledger immutable验证、8state/nullable群组和锁顺序明确。两个owner最终合同已闭合；新行为先真实RED，旧public与产线Invocation不变。计划保持Review，下面仅可执行性结论，实际实施由evidence/source/log逐项重评。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence技术库、九路径 | 无新层/AS/PS/C/native/SQL/产线Invocation改动 | None |
| MC-REUSE-001 | Applicable | PASS | 原唯一codec/row parser、ToolApprovalBinding/VerifiedApprovalDTO、ledger.executionMaterial | 不造审批/快照/七state invocation重复parser | None |
| MC-DEP-001 | Applicable | PASS | 现Jackson/JavaTime/JDK SHA/Spring JDBC/transaction | 无新依赖/utility/框架 | None |
| MC-NAME-001 | Applicable | PASS | ApprovalSourceVO/ApprovalProofVO/ApprovalConsumptionVO/DispatchVO/Row | 真不可变包内值、角色清楚；无第二Proposal DTO | None |
| MC-VALID-001 | Applicable | PASS | closed schema/exactaction/source/owner/8state/SQLnullable groups/joins | historical不等于fresh authority/签名或消费 | None |
| MC-MODEL-001 | Applicable | PASS | immutable records与原领域值、old9accessors/public9不变 | 无弱9arg constructor/可变proof/新实体或复制Mapper | None |
| MC-CONVERT-001 | Not applicable | N/A | 现DTO/域值重建与codec、本地Row metadata投影 | 不新建实体转换/Converter层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术JDBC/codec，无新业务日志Service | sanitized错误/固定redacted，不泄proof/参数/outcome | None |
| MC-BEAN-001 | Not applicable | N/A | 无新Bean/DI/config/public endpoint | 同源manual构造沿既有pattern | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK digest/Jackson/ledger/transaction | 无独立脚本/校验/身份框架 | None |
| MC-JSON-001 | Applicable | PASS | closed proof层级/16KiB与共享outcomeexact5keys | canonical重编码SHA，非PGjsonbtext；原合法编码不变 | None |
| MC-TIME-001 | Applicable | PASS | §4.9；verifiedAt历史校验/consume时间组/技术10s window | lateclosed可超业务expiry，不扩旧public授权窗口 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/flags/配置注册改动 | 历史内部入口未开放或自动消费 | None |
| MC-PATTERN-001 | Applicable | PASS | 原transaction participant+single codec/parser历史投影 | 复用实际变化点，不造第二状态机/审批引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 九路径、仅两InvocationStoreTest fixtures | SQL/Grant/PDP/Budget/Invocation产线不动，无派发/消费 | None |
| MC-TEST-001 | Applicable | PASS | §8 codec历史/腐坏/8state/joins/事务/旧兼容RED与回归 | PG class缺optin只编译skip，无服务/DB/网络验证伪称 | None |
| MC-BLOCKER-001 | Applicable | PASS | ApprovalProofVO名称/五键outcome/fullRow8state/joins/API已root与owner闭合 | 无未决合同或依赖；真实运行和写consumer后续 | None |

### 12.6 Final verdict
PASS — Ready for user review
