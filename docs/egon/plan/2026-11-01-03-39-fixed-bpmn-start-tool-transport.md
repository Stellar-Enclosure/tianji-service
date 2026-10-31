# T3-07 固定BPMN START工具的显式传输库

| Field | Value |
| --- | --- |
| Document | `2026-11-01-03-39-fixed-bpmn-start-tool-transport.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 03:39 Asia/Shanghai` |
| Updated | `2026-11-01 03:39 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | explicit固定BPMN START port/metadata/transport及DMN敏感校验复用；15路径 |
| Source Requirement | 用户继续原plan未完成工作并分步验证提交 |
| Baseline Revision | `main@1de8dda72b47e2691e5c1893a198ded1d4c72774`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户exact未完成工作执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `7788b380510b949244e256c6876c7ce81a350fe458bf99d43679b33726210cf4` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [只读AS装配](2026-11-01-02-41-default-off-readonly-dmn-assembly.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本步仅交付固定BPMN START显式library callback port/transport及其可信ACK receipt引用，C/PS提取同值常量。敏感独立SERVICE校验从现DMN抽取包内共享helper，原DMN业务不变。十五路径一个逻辑commit；不注册Spring配置/native/WriteRecorder/审批consume，不改SQL/依赖或启用写功能。

## 2. Target Spec and Effective Design

唯一effective设计为原蓝图§6模块/单执行所有权、§7可靠性、§12 intent/receipt/UNKNOWN及§13身份/权限/预算，T3-07。root/APIagent按用户原exact执行授权闭合本库合同。固定实际SERVICE执行PS START，模型不能选择source/backend/release/身份；原requester USER不因此变成PS USER OBO。未来WriteRecorder必须先持久nonce/当前工具权及必要approval消费后才调用该能力，本步没有该consumer。Review不伪称Accepted或运行验收。

## 3. Effective Requirements and Acceptance

PLAN-REQ只是既有要求局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/12/T3-07 | fixed write port绑定真实稳定action与不可变backend | nonRO/action fullmatch、fixed origin/ref/schema/BPMN release、单次callback | ARport/ASBindings/tests |
| PLAN-REQ-002 | §13身份与秘密 | 独立真实SERVICE同bearer访问固定PS，仅两scope | key/trust/proof/converter/source/scope/current核验，token仅percall，helper与DMN同语义 | helper/两Transport/tests |
| PLAN-REQ-003 | §7/12 receipt | matching2xx才生成真实PS receipt namespace的host引用 | 稳定PK+requesthash+ACK摘要，非instanceId/上游UUID/signature；UNKNOWN无ref不redo | STARTTransport/Test |
| PLAN-REQ-004 | §7/12 UNKNOWN | SDK后保守未知、有效lateACK优先callback | 仅401/403knownDenied，其他POST失败UNKNOWN；SDK前明确NOT_SENT；lateACK先交callback后outer抑制 | Transport/test |
| PLAN-REQ-005 | §6/17兼容与边界 | 复用校验且原DMN/C/PS兼容，真实验证不扩大能力 | DMN19 baseline→after，常量同值、受控SDK/no环境；15paths无activation/WriteRecorder/consume/SQL | 所有路径/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy

先最小public声明/常量声明使新测试编译，组件真实RED后实现port/bindings/START。共享敏感helper按现DMN逐句抽取，用原19已GREEN baseline→after保行为回归，不伪造纯抽取RED；新START场景补另一scope/source负例。同值C/PS常量为兼容提取。root统一Maven，最终一个commit不留scaffold。
### 4.2 Test-first strategy

port+Bindings owner先各真实RED→GREEN；另一owner新STARTTransport自身真实RED后实现helper/委托/START并核旧DMN19。初始类型可暂缺行为以支持编译，fixture/编译错误不算RED。constant assertions保护原wire；无新helperTest路径，通过新START/旧DMN测试覆盖helper。
### 4.3 Sequential and parallel boundaries

两个owner仅在锁定port/Backend/helper签名后各自RED→GREEN并行，shared helper/两Transport由同owner独占。root负责C/ContractTest/PS引用与README，docowner只plan/evidence。无并行下一Step。
### 4.4 Commit boundaries

§5十五路径为固定START library一个逻辑commit，显式stage，不push/改历史/启动系统。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Write callback/metadata | §12写intent/receipt | 原ROport不具write语义，原ToolActionRequest已有稳定intent | 独立小port只复用中性executor，不污染readonly API | Implement |
| PS START/host reference | T3-07现BPMN start能力 | 原SDK匹配ACK/PS command receipt，无上游receiptUUID或lookup API | 用可信matchingACK派生真实namespace引用，无新endpoint | Implement |
| credential helper | §13不可复制敏感验证 | 原DMN完整OAuth/key/trust/principal代码可复用 | 包内无状态helper，不新public认证层/percallctx | Implement |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| port/bindings/constants | PLAN-REQ-001/005 | 新port/metadata测试 | 最小声明、现Action/Schema/Start DTO | fixed write request/profile | STARTTransport | Step 1 |
| helper/START/DMN兼容 | PLAN-REQ-002/003/004 | 新START wire/receipt/failure RED；旧DMN19 | 上项/原SDK/IAM | 显式能力，无执行consumer | 未来WriteRecorder/approval工作 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | C轻合同、AR port、AS Adapter、PS常量引用 | 原模块结构不变 | 小库无新层/框架/端点；helper包内 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Validation/model | Action/ToolSchema/原immutable Backend模式 | 构造不认证、fixed source/schema | immutable值/Scopes copy、fullrequest核验，无实体/DTO副本 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/log/JSON | 原manual技术SDK与Jackson DTO | 无新Bean/配置/日志Service | 固定Failure无cause/toString脱敏；原Converter复用 | Step1；MC-BEAN-001、MC-JSON-001 |
| Pattern/time | 原callback capability/SDK Adapter/Clock/mono | 单次动态scope及历史事实优先 | helper不接管percallstate；不造WriteRecorder/第二loop | Step1；MC-PATTERN-001、MC-TIME-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| START wire/ACK/hash | ProcessClient/HttpProcessClient/StartProcessCommand/OrchestrationRequestDigests | 原POST匹配release/definition/business/correlation | 足够，无新PS接口 | reuse | None | Step1；MC-REUSE-001 |
| 身份/取消/秘密 | 原DMN credentials/current/CallScope | 现samebearer/source/clearbeforecancel已测 | 敏感predicate可共享，scope不可共享 | extract helper/reuse scope模式 | None | Step1；MC-DEP-001 |
| stable业务intent | ToolActionRequest/ToolApprovalBinding | 原intent不随run/nativeID改变 | 足够，无新业务主键 | reuse | None | Step1；MC-REUSE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/7/12/13/T3-07 | 技术port/Bindings/Verifier/VO/Observation命名 | 新tests→最小实现/同值C/PS | receipt语义准确、所有private值脱敏 | 编译/source | Step1 | PASS |
| Rule 2 | §6/7/12/13/T3-07 | action/schema/source/scopes/ACK/exactURI | port/Bindings/helper/STARTTransport | 本域边界逐项验证，不拿constructor或业务参数认证 | 正反/wire | Step1 | PASS |
| Rule 3 | §6/7/12/13/T3-07 | immutable值/原ToolActionRequest复用 | 新值+helper | defensive copy/ScopesSet，无实体record或重复Converter层 | construct/equality | Step1 | PASS |
| Rule 4 | §6/7/12/13/T3-07 | manual技术Adapter/helper | AS库/docs | 无新businessBean/DI/logService，不伪造Lombok注入 | scope/noSecret | Step1 | N/A：无业务Bean/配置面 |
| Rule 5 | §6/7/12/13/T3-07 | JDK/现SDK/hash/Trace | 所有source | 不引新工具库/线程/认证框架 | imports/POM | Step1 | PASS |
| Rule 6 | §6/7/12/13/T3-07 | Spring Jackson原typed SDK | START/DMN/Contracts | 原Start/ACK/JSON不改；PS常量同值 | bytewire/兼容 | Step1 | PASS |
| Rule 7 | §6/7/12/13/T3-07 | 无配置/profile/flags | README/scope | explicit库不注册，不自行开启写 | diff | Step1 | N/A：无业务Bean/配置面 |
| Rule 9 | §6/7/12/13/T3-07 | SDK Adapter/callback+helper抽取 | AS/port | 只变化点复用，单次scope/保守unknown/无重发 | once/late/failure | Step1 | PASS |
| Rule 10 | §6/7/12/13/T3-07 | Instant/Duration/Clock/mono | 两Transport/helper/tests | 原预算不重置，validlateACK先callback，不猜时间顺序 | time/cancel | Step1 | PASS |
| Rule 11 | §6/7/12/13/T3-07 | 原C/AR/AS/PS边界 | 十五路径 | 无新层/SQL/配置/native/权限/deps/APIactivation | scope/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | request/run/backend/callback窗口及credential expiry | Occurred instant | Instant | Not persisted | Internal | Not applicable: 原绝对权限期限 | Not applicable: 无UI | 单entrywall+mono min(request,run,now+backendtimeout)，effectiveExecutor expiry取token/JWT/window最小；PDP不在本port新引入 | 原Clock/LongSupplier，100ms..30s | OAuth/JWT各自iat/exp无需相同，proof/principal自身一致；DMN行为保持 | Step 1；TEST-002 time/cancel/source |
| TIME-002 | lateACK/PS status/submittedAt/finishedAt | Occurred instant | Instant | Not persisted | UTC Z | Not applicable: 已返回事实 | Not applicable: 无UI | ACK结构有效先生成Observation交callback，outer再postcheck；receipt排除status/timestamps，不比较远端DB与本地时间顺序 | 原SDK/Jackson/Clock不改 | 无新增SQL/长期事实存储，本步不实现WriteRecorder | Step 1；TEST-002 late/reference矩阵 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java | 定义write port/Observation/安全Failure | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindingsTest.java | 定义fixed BPMN mapping/backend digest | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java | 真实SDK/mock JDK bytewire证明一次START与保守receipt语义 | Step1 |
| MODIFY | stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java | 保持原wire合同并定义canonical START两常量 | Step1 |
| MODIFY | stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessClient.java | 统一同值START scope/resource符号 | Step1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java | PS仅引用canonical同值常量 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java | 独立fixed START callback port，复用中性executor值 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java | host固定BPMN release/model/source映射 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java | 只抽取共享敏感SERVICE凭据验证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java | 现DMN仅纯抽取委托共享helper | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java | 原DMN行为保持性回归与shared helper覆盖 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java | 固定独立SERVICE START library transport | Step1 |
| MODIFY | README.md | 说明explicit library/receipt及仍关闭写能力 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-fixed-bpmn-start-tool-transport.md | 真实日志/局部合同与17MC10Rule报告 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-03-39-fixed-bpmn-start-tool-transport.md | strict可执行十五路径计划 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline1de8dda clean及blueprint SHA已核；writer18md24refs PASS。PS permission/ContractTest与§8既有test名字均rg核存在；新3Tests仅按scope创建。十五路径外不改config/native/WriteRecorder/approvalconsume/SQL/APIendpoint/权限/依赖/Components；原readonly publicport与业务保持。旧V13 SHA固定f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3，不迁移/repair。

AR port为FixedBpmnStartToolTransport.<T>withVerifiedCall(Request,Callback<T>)。Request(run,lease,descriptor,ToolActionRequest action,UUID invocationId,Instant deadlineAt)核nonLEGACY、same run/lease、deadline≤run、risk!=READ_ONLY、action owner/ref/policy/normalizedargs完整一致。Callback.apply复用RO.VerifiedExecutor中性SERVICE actor+expiry并给OneShotStart.start()；新StartObservation(hostReceiptReference,instanceId)不可变、闭合格式/长度、toString脱敏，output只有immutable instanceId。构造不认证，RO API不变，未来caller必须COMMIT dispatch nonce且完成当前权/必要approval后才消费。

新FailureKind精确九项：CREDENTIAL_UNAVAILABLE、RELEASE_UNAVAILABLE、RELEASE_CHANGED、OPERATION_NOT_SENT、OPERATION_DENIED、OPERATION_RESULT_UNKNOWN、OPERATION_INVALID_RESPONSE、CANCELLED、DEADLINE_EXCEEDED；INVALID_RESPONSE同属UNKNOWN但保留结构无效code，绝不增加泛4xx OPERATION_REJECTED。Failure消息仅kind、super(kind.name(),null)、无attachable cause/body/status/URI/token。UNKNOWN没有Observation/receipt引用，不新增lookup/reconcile/自动POST redo。

Bindings的BackendVO16字段顺序：long tenantId；URI processIssuer/processBaseUri/processResourceUri；String registrationId/expectedClientId/expectedAppId/expectedSourceBiz/expectedSourceApp/expectedEnvironment/releaseVersion/manifestSha256/processDefinitionKey/processModelSha256；Map<String,ProcessValueTypeEnum> inputSchema；Duration timeout。域TIANJI_TOOL_BPMN_START_BACKEND_V1覆盖稳定16fields+canonical schema，不含run/definition/tool refs或credential/token/trust动态版本，backend→descriptor→definition→mapping无循环。沿原ASCII HTTPS/noquery/noUserinfo/noFragment/normalize不漂移，resource≤512 issuer/base≤2048、URIASCII equality/hash、100ms..30s、max256/无重复slot与同origin definitionSHA一致。BindingVO8与DMN相同origin/definition/fulltoolref/backend shape；Resolved允许IDEMPOTENT_WRITE/APPROVAL_REQUIRED，executionResourceKey=ProcessClient.START_RESOURCE_KEY，input字段/类型exactbackend且全部required，output exact/required STRING instanceId。

STARTTransport publicctor(bindings,IdpServiceOAuth2Client oauth,TenantIssuerAuthenticationManagerComponent authentication,VerifiedPrincipalConverter converter,TrustedIssuerRegistry issuers,callerOwned HttpClient,int concurrency1..8)，package ctor追加Clock/LongSupplier；AutoCloseable只关ownpool。另public usesBindings/usesOAuth/usesAuthentication/usesConverter/usesIssuerRegistry/usesHttpClient六observer均candidate==原constructorfield，same/null/foreign测试不得触发调用，不新增Bean。固定TENANT SERVICE token仅ProcessReleaseClient.READ_SCOPE+ProcessClient.START_SCOPE，actualexecutor可与原USER/SERVICE requester不同；key/trust ACTIVE TENANT、sameOAuthbearer authenticate(fixedPSaudience)→proof.validate/service+same jwt tokenValue→原converter→issuer/tenant/client/app/resource/TENANT/source3/scopes/proof/principal核验。OAuth与JWT timestamps不强制相等，effectiveexpiry=min token/JWT/window；current key/trust exactURIASCII前后核。PS实际owner是此SERVICE，不是USER OBO。

每transport各自一组request-local bearer AtomicReference/publishLock/future/active/window/cancel/Trace与1..8 SynchronousQueue无queue非继承virtualexecutor。只传播Trace，不继承USER SecurityContext；cancel/close清active+token在cancelFuture之前，短publish锁防迟到OAuth refill；worker/caller finally均clear。helper不存token不共享percallctx，不关闭caller HttpClient/IAM。

共享helper包内ctor(oauth,authentication,converter,issuers,Clock)。TargetVO(long tenantId,URI issuer,URI resource,String registrationId,String expectedClientId,String expectedAppId,String expectedSourceBiz,String expectedSourceApp,String expectedEnvironment,Set<String> scopes)，Set.copyOf并redacted，不含model/base/release。CredentialVO(IdpServiceTokenRequest request,ServiceAuthorizationKey key,TrustedIssuerRegistry.TrustedIssuerVO trust,TrustedAgentActor actor,Instant expiresAt)无token、redacted。credentials(TargetVO,Instant deadlineAt,LongSupplier remainingNanos,Consumer<String> publishBearer,Supplier<String> currentBearer)；requireCurrent(TargetVO,CredentialVO)。helper无broadcatch/跨portFailure，predicate失败只固定IllegalStateException无cause；transports先catch ownFailure保留kind，再Runtime→CREDENTIAL_UNAVAILABLE，scope callback CANCELLED/DEADLINE不被抹掉。helper返回先赋scope.credential再原current，继续由ownscope核minexpiry/originalwallmono。原DMN六final依赖/==helpers/SCOPES/hash/输出/Failure语义不改，既有19 baseline→after回归，无伪RED。

实际wire复用原GET /api/tianji/v1/process-releases/{releaseVersion}，核manifest/exact1个BPMN definition/hash/inputschema，然后进入callback；OneShot必须ownerthread/dynamicscope/once/noDB事务，scope/current/client构造与command验证在SDK前完成可安全判NOT_SENT。POST /api/tianji/v1/process-instances的StartProcessCommand用fixed processDefinitionKey/releaseVersion、action.businessKey、commandId=action.intentId、correlationId=同intent、variables=action.arguments。businessVersion只是host intent String metadata，PS无独立objectVersion；非schema变量不得声称PS核对象版本。

一旦调用ProcessClient.start，仅401/403为known OPERATION_DENIED；其他400/404/409/408/429/5xx/status0/I/O/timeout/runtime都OPERATION_RESULT_UNKNOWN，invalid/mismatched/oversize2xx为OPERATION_INVALID_RESPONSE但同属UNKNOWN，均无reference；不从status0猜未发送。precallback凭据/release/close/deadline分别失败不调用；OneShot SDK前明确scope/current/client构造失败为OPERATION_NOT_SENT。wrongthread/escape/secondcall可CANCELLED安全拒绝，不据此猜已发生首次调用状态。outer callback Runtime按consumed标记区分UNKNOWN与NOT_SENT；outer cancel/deadline未知只抑制返回值，不覆盖callback已记录事实。有效late matching2xx ACK先交callback，Observation生成前不加新expiry/PDP检查；future Recorder负责先commit事实再outer抑制。

只有可信matching2xx后newASTransport的package-private static String hostReceiptReference(...)生成：prefix tianji:ps-start:v1: + OrchestrationRequestDigests.hash(List.of("TIANJI_PS_START_RECEIPT_REFERENCE_V1",processIssuerASCII,processResourceASCII,actualExecutorTenant,"SERVICE",executorIssuerFingerprint,executorSubjectId,"START",command.commandId,OrchestrationRequestDigests.start(command),ACK.instanceId,ACK.definitionKey,ACK.releaseVersion,ACK.businessKey,ACK.correlationId),Map.of())。此机械可见性澄清替代先前private措辞，同包AS test直接独立digest向量、不用反射或新public API。它引用实际PS commandreceipt PK namespace+requestSHA+stableACK，非instanceId/上游UUID/签名，方法本身不认证。排除baseURI/timeout/token/credentialversion/原vars/status/timestamps/run/hostInv；同receipt状态时间变不变引用，namespace/stablefield变则引用变。未知绝无ref，无commandID readonly lookup/自动POST重发。

审批START应由原USER以真实PSaudience登录自行调用ToolApprovalBinding.sourceStart(action)，之后独立SERVICE verifyReceipt/consume是后续工作；本步APPROVAL_REQUIRED可描述但不审批、不派发consumer、不绕过。固定PS START仅内部knownnonbillable合同，不泛化unknown provider费用或实现退款/计费。

Plan Clarification PLAN-CLAR-001：写入阶段必须覆盖caller与worker的取消竞争。新START CallScope保留request-local operationStarted/callbackCompleted标记；在调用SDK前于publishLock内复核remaining后标记started，与cancel线性化，锁不跨HTTP。callback成功返回后才标记completed。started且未completed时，outer interrupt/timeout/cancellation、outer剩余窗口异常或未识别worker异常均为OPERATION_RESULT_UNKNOWN；SDK实际401/403仍保留OPERATION_DENIED。callback AgentLedgerException只在started前保留code，started后为UNKNOWN；completed后的outer source/deadline检查仍可按原种类抑制模型值，因为callback已经完成事实处理。四个受控POST中断/截止/close及ACK后ledger失败场景先实际RED再修复，未知无receipt返回且不重发。本澄清只落实既定stage/late事实合同，十五路径与原DMN行为不变。

取消分类的线性化细节：每个outer提前退出分支先在publishLock内cancel/clear，再读取stage标记分类；不能先读started=false再仅靠finally取消，否则worker可能在两者之间启动SDK。beginOperation使用同一锁，先remaining核验后标记started。ExecutionException只保留实际operation的DENIED或已经UNKNOWN的两种kind；started未completed期间其他lifecycle/source/preflight异常转为UNKNOWN。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 提供固定START一次能力与可信ACK引用并保持DMN兼容
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交1de8dda现DMN/readonly装配、ToolActionRequest、原PS START/SDK/receipt合同。
- Baseline state: 原DMN19已GREEN，PS START/receipt已存在；无fixed write port/Bindings/STARTTransport，共享校验尚在DMN内。
- Observable outcome: explicit fixedSERVICE START能力只返回可信ACK派生receipt，SDK后保守UNKNOWN，旧DMN无行为变化。
- End state: 新库可由未来WriteRecorder消费；无Spring/native/WriteRecorder/审批消费或运行激活。
- Test-first gate: Required — 新组件最小声明供编译后真实RED；纯敏感校验抽取以旧19 baseline→after行为保持回归，无伪RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java`
- Purpose: 定义write port/Observation/安全Failure。
- Symbols: FixedBpmnStartToolTransportTest.java；§6精确API/字段。
- Repository evidence: 原ReadOnlyDecisionToolTransportTest/ToolActionRequest模式。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 最小Request/Callback/OneShotStart/Observation/9Failure声明后真RED。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: exactrun/lease/ref/policy/owner/action规范参数/非RO与deadline；Observation闭合receipt reference/instanceId。
- Error and edge behavior: constructed元数据不认证；redacted/no cause；不改ROport。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
@Test write_request_and_observation_are_closed_host_bound_values() {
  assertNonLegacyRunLeaseOwnerToolPolicyNormalizedActionAndDeadlineMatch(); assertReadOnlyOrDriftRejected();
  assertObservationCarriesOnlyCanonicalHostReceiptReferenceAndBoundedInstanceId(); assertAllValuesRedactedAndFailureSafe();
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindingsTest.java`
- Purpose: 定义fixed BPMN mapping/backend digest。
- Symbols: TianjiFixedBpmnStartToolBindingsTest.java；§6精确API/字段。
- Repository evidence: 原FixedDecisionToolBindingsTest、ToolDescriptor/Registry。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 新Bindings/BackendVO最小声明后实际hash/schema/resolve RED。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 16稳定fields+canonical输入schema，origin8与definition/fullref；风险write/approval、STARTresource、output必填STRING instanceId。
- Error and edge behavior: 禁止循环hash/模型选target；namespace/URIASCII/duplicates/bounds/全required验证。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
@Test stable_backend_then_descriptor_then_definition_has_no_digest_cycle() {
  assertSixteenFieldNamespacedDigestAndCanonicalSchema(); assertExcludedRunToolDefinitionCredentialFields();
  assertExactOriginDefinitionReferenceBackendWriteRiskAndStartResource(); assertAllInputRequiredAndOnlyRequiredInstanceIdOutput();
  assertUriAsciiBoundsDuplicateSlotsAndCrossToolDefinitionDriftFailClosed();
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java`
- Purpose: 真实SDK/mock JDK bytewire证明一次START与保守receipt语义。
- Symbols: TianjiFixedBpmnStartToolTransportTest.java；§6精确API/字段。
- Repository evidence: 原DMN实际SDK测试、ProcessClient.start/ACK匹配。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 新Transport最小ctor/withVerifiedCall stub后真RED；共享helper不新增独立Test。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: fixed command/scopes/samebearer/releaseBPMN、SERVICE身份、receipt deterministic/change/exclusion矩阵、lateACK先callback。
- Error and edge behavior: ownerthread/dynamicscope/once/noTX/cancel/close/late publish；POST仅401/403known，INVALID_RESPONSE同属UNKNOWN；SDK前NOT_SENT、outer Runtime consumed区分；六observer同/null/foreign无调用。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
@Test trusted_matching_ack_is_the_only_source_of_a_start_receipt_reference() {
  driveActualHttpProcessClientWithMockJdkByteResponsesAndControlledIdentity(); assertFixedStartCommandAndSameServiceBearer();
  assertReceiptNamespaceRequestHashAndStableAckBindTheReferenceButStatusTimeAndHostRunDoNot();
  assertOnly401And403AreDeniedAndAllOtherPostSdkFailuresAreUnknownWithNoReferenceOrResend();
  assertPreSdkNotSentOneShotScopeAndValidLateAckReachesCallbackBeforeOuterSuppression();
  assertPackagePrivateReceiptHelperAgainstIndependentDigestVectorsAndSixObserverPointersWithoutCalls();
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 4 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`
- Purpose: 保持原wire合同并定义canonical START两常量。
- Symbols: OrchestrationContractTest.java；§6精确API/字段。
- Repository evidence: 已核实际同名ContractTest及StartProcessCommand。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 最小常量声明供编译；固定值断言/原Start DTO/hash回归后root落实同值常量。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: START_SCOPE=tianji:process:start、START_RESOURCE_KEY=tianji.api.process.instance.start；wire/fields不变。
- Error and edge behavior: 不新增HTTP API/权限/DTO字段，不把businessVersion变PS objectVersion。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
@Test canonical_process_start_values_keep_the_existing_wire_contract() {
  assertEquals("tianji:process:start",ProcessClient.START_SCOPE); assertEquals("tianji.api.process.instance.start",ProcessClient.START_RESOURCE_KEY);
  assertExistingStartCommandFieldsAndCanonicalDigestRemainUnchanged(); assertNoTenantOrBusinessVersionWireFieldAdded();
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 5 — `MODIFY stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessClient.java`
- Purpose: 统一同值START scope/resource符号。
- Symbols: ProcessClient.java；§6精确API/字段。
- Repository evidence: 原接口只有start/get；PS已有相同字面值。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: additive String START_SCOPE/START_RESOURCE_KEY；原start/get签名不变。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 无协议版本/JSON/endpoint变更，值由现PS常量直接提取。
- Error and edge behavior: public常量只元数据不授权限。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
public interface ProcessClient {
  String START_SCOPE = "tianji:process:start"; String START_RESOURCE_KEY = "tianji.api.process.instance.start";
  ProcessInstanceDTO start(StartProcessCommand command); ProcessInstanceDTO get(GetProcessInstanceQuery query);
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`
- Purpose: PS仅引用canonical同值常量。
- Symbols: TianjiProcessPermissions.java；§6精确API/字段。
- Repository evidence: 实际START_API/START_SCOPE已有原值。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: START_API=ProcessClient.START_RESOURCE_KEY；START_SCOPE=ProcessClient.START_SCOPE，其余不动。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: registration/annotation消费值完全不变。
- Error and edge behavior: 不注册/激活权限、授角色或更PS接口。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
TianjiProcessPermissions.START_API = ProcessClient.START_RESOURCE_KEY;
TianjiProcessPermissions.START_SCOPE = ProcessClient.START_SCOPE;
Keep every existing permission code, resource declaration, annotation value and endpoint behavior unchanged.
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 7 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java`
- Purpose: 独立fixed START callback port，复用中性executor值。
- Symbols: FixedBpmnStartToolTransport.java；§6精确API/字段。
- Repository evidence: 原RO port/ToolActionRequest/ToolSchema。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: public interface+immutable Request/Observation+typed callback/9Failure；精确合同§6。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: action持稳定intent和归一参数；Observation只hostReceiptReference/instanceId；output immutable只instanceId。
- Error and edge behavior: 非RO但metadata不证明approval/auth；future WriteRecorder先commitnonce；no secret DTO/readonly API变更。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
public interface FixedBpmnStartToolTransport {
  <T> T withVerifiedCall(Request request, Callback<T> callback);
  record Request(AgentRunAuthorization.Request run,AgentRunLease lease,ToolDescriptor descriptor,ToolActionRequest action,UUID invocationId,Instant deadlineAt) { requireExactActionAndNonReadOnlyBinding(); }
  interface Callback<T> { T apply(ReadOnlyDecisionToolTransport.VerifiedExecutor executor,OneShotStart operation); }
  interface OneShotStart { StartObservation start(); } record StartObservation(String hostReceiptReference,String instanceId) { requireClosedCanonicalValues(); }
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 8 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java`
- Purpose: host固定BPMN release/model/source映射。
- Symbols: TianjiFixedBpmnStartToolBindings.java；§6精确API/字段。
- Repository evidence: 沿现DMN Bindings record/URIASCII/registry模式。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: constructor(Collection<ToolDescriptor>,Collection<BindingVO>)、BackendVO16/BindingVO8/ResolvedBindingVO/resolve(Request)。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 域TIANJI_TOOL_BPMN_START_BACKEND_V1；tenant/origin/type/definition/fullref/backend匹配；write风险/input/output严格。
- Error and edge behavior: max256/no duplicate slots、sameorigin definitionSHA统一、URIbound/resource<=512 issuer/base<=2048、redacted。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
ResolvedBindingVO resolve(FixedBpmnStartToolTransport.Request request) {
  selected = fixedOriginTypeDefinitionKeyVersionAndToolKeyVersionSlot(request); requireExactDefinitionAndToolReferenceAndBackendDigest();
  requireWriteOrApprovalRiskAndProcessStartResourceAndExactAllRequiredInputSchema(); requireOnlyRequiredStringInstanceIdOutput(); return selected;
}
BackendVO.bindingSha256() { return OrchestrationRequestDigests.hash(stableSixteenBpmnFieldsWithDomainAndCanonicalInputSchema); }
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 9 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java`
- Purpose: 只抽取共享敏感SERVICE凭据验证。
- Symbols: TianjiProcessToolCredentialVerifier.java；§6精确API/字段。
- Repository evidence: 原DMN credentials/requireKey/trust/sameKey/sameTrust及owner闭合API。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 包内ctor/credentials/requireCurrent；TargetVO/CredentialVO精确§6。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 无token/window/future/事务/percallctx；复用原samebearer/JWT/registry/key/source/scopes验证。
- Error and edge behavior: 无broad Runtime catch/跨portFailure；predicate固定IllegalStateException无cause；scope CANCELLED/DEADLINE保留。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
CredentialVO credentials(TargetVO target,Instant deadlineAt,LongSupplier remainingNanos,Consumer<String> publishBearer,Supplier<String> currentBearer) {
  remainingNanos.getAsLong(); requireOriginalExactAuthorizationKeyAndActiveTenantTrust(); obtainOriginalBoundedTokenThenPublishThroughCaller();
  authenticateSameBearerAndValidateProofConverterPrincipalAndFullTargetScopesSource(); returnRedactedRequestKeyTrustActorExpiryWithoutToken();
}
void requireCurrent(TargetVO target,CredentialVO credential) { requireExactCurrentKeyTrustAndAsciiUrisUsingOriginalChecks(); }
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java`
- Purpose: 现DMN仅纯抽取委托共享helper。
- Symbols: TianjiFixedDecisionToolTransport.java；§6精确API/字段。
- Repository evidence: 基线DMN19 tests已GREEN；现六finalfields/uses*与scopes保持。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 用同实例/Clock构造helper；credentials/来源current委托；返回先scope.credential=result再原current。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 保留DMN publicport/hash/SCOPES/ACK/Failure映射/remaining/cancel/publish/window/Trace/ownedpool所有行为。
- Error and edge behavior: 先catch本port Failure保留kind，再Runtime→CREDENTIAL_UNAVAILABLE；不把纯抽取伪造新RED。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
credentials(backend,scope) {
  try { result = verifier.credentials(targetFromOriginalDmnBackendAndScopes,scope.window.deadlineAt(),scope::remaining,scope::publishBearer,scope.bearer::get); scope.credential=result; current(scope,backend); return result; }
  catch (Failure rejected) { throw failure(rejected.kind()); } catch (RuntimeException privateFailure) { throw failure(CREDENTIAL_UNAVAILABLE); }
}
current(scope,backend) { preserveRemainingAndNoTransactionChecks(); verifier.requireCurrent(target,scope.credential); preserveRemainingAfterCheck(); }
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 11 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java`
- Purpose: 原DMN行为保持性回归与shared helper覆盖。
- Symbols: TianjiFixedDecisionToolTransportTest.java；§6精确API/字段。
- Repository evidence: 基线19实际通过，6identity与SDK/context/取消/late tests。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 保留全部原断言；只必要fixture/type适配，不人为制造纯抽取RED。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: before baseline→after同组GREEN，新START测试覆盖helper另一scope集。
- Error and edge behavior: 不削弱source/时间/URI/samebearer/executor/noSecrets断言。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
@Test existing_dmn_behavior_remains_identical_after_credential_extraction() {
  preserveAllNineteenExistingTestsAndSixIdentityAssertions();
  assertOriginalScopeSetSameBearerKeyTrustExpiryAndSourceChecksRemainUnchanged(); assertCancelDeadlineKindsAndPrivateTokenCleanupStillHold();
}
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 12 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java`
- Purpose: 固定独立SERVICE START library transport。
- Symbols: TianjiFixedBpmnStartToolTransport.java；§6精确API/字段。
- Repository evidence: 原DMN有界callback/HttpProcessClient；新shared credentialhelper。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: publicctor(bindings,oauth,authentication,converter,issuers,HttpClient,int concurrency)，package appendClock/LongSupplier；AutoCloseable只ownpool；六uses*純identity與package-private receipt helper按§6。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: samebearer releaseGET→exactBPMNmanifest/model/schema→privatecallback oneShot→fixedcommandSTART→stableACK reference；§6完整hash。
- Error and edge behavior: 每transport独立scope/token/publishLock/future/window/Trace；SDK前NOT_SENT与POSTUNKNOWN分开，latevalidACK先callback，no自动POST重发。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```java
<T> T withVerifiedCall(Request request,Callback<T> callback) {
  resolveFixedBackend(); enterOwnBoundedWallMonoScopeAndNonInheritedNoQueueVirtualWorker(); verifyServiceWithSharedHelperAndReleaseBpmn();
  operation = ownerThreadDynamicOnceNoTransactionStartCapability(); value = callback.apply(executor,operation);
  performOuterCurrentTimeTrustChecksAfterCallback(); finallyClearBearerBeforeCancelAndCloseOnlyOwnPool();
}
StartObservation start() { buildFixedCommandBeforeSdkOrThrowNotSent(); ack = callSdkStartWithConservativePostFailureMapping(); returnObservationWithPrivateHostReceiptReferenceBeforeNewExpiryOrPdpChecks(); }
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 13 — `MODIFY README.md`
- Purpose: 说明explicit library/receipt及仍关闭写能力。
- Symbols: README.md；§6精确API/字段。
- Repository evidence: 现readonly native/AS assembly描述。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 写库未配置/未native/未WriteRecorder/未审批consume；receipt引用非signature/instanceId、UNKNOWN不redo。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: USER审批必须原USER以PSaudience登录start sourceStart；businessVersion非PS独立对象版本；固定内部nonbillable不泛化费用。
- Error and edge behavior: 不声称Spring/native/WriteRecorder/审批消费已接，不误称instanceId为receipt或泛化内部零费用。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```text
Document explicit fixed BPMN START library transport and independent SERVICE receipt owner.
Document host-derived stable ACK receipt reference and conservative post-SDK UNKNOWN/no-resend semantics.
Keep Spring wiring/native/WriteRecorder/approval-consume/real IAM-PG-model acceptance pending; no activation claim.
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 14 — `CREATE docs/egon/evidence/2026-11-01-fixed-bpmn-start-tool-transport.md`
- Purpose: 真实日志/局部合同与17MC10Rule报告。
- Symbols: 2026-11-01-fixed-bpmn-start-tool-transport.md；§6精确API/字段。
- Repository evidence: 既有evidence格式。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: 实际RED/GREEN/classcounts/commands/receipt/source/no-runtime；五别名/fullPartial。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 未执行阶段PENDING，stub/fixture错误分开；不预填真实crypto/PG/费用或批准。
- Error and edge behavior: 未执行时PENDING；fixture/编译/直接stub错误分类，不预填真实crypto/PG/运行验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```text
Record actual component RED and selected GREEN logs after root executes them.
Evaluate seventeen MC and ten literal Rule rows independently against source, tests and fifteen-path scope.
Trace five local aliases and retain unimplemented write recorder, approval consumption and real-runtime boundaries.
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

#### File 15 — `CREATE docs/egon/plan/2026-11-01-03-39-fixed-bpmn-start-tool-transport.md`
- Purpose: strict可执行十五路径计划。
- Symbols: 2026-11-01-03-39-fixed-bpmn-start-tool-transport.md；§6精确API/字段。
- Repository evidence: 原blueprint/actualpaths与两个owner闭合合同。
- Dependencies and consumers: 原typedSDK/域值/IAM及本步相邻文件；未来WriteRecorder而非当前native消费。
- Why now: Review/legacyexact授权、单Step/commit、组件RED barrier/旧DMN纯抽取回归。
- Contract/signature changes: §6本文件精确新增合同；原非目标业务/public API保持。
- Input/output and state mapping: 不先source/不虚构Accepted/不新框架、APIendpoint、SQL或配置。
- Error and edge behavior: 真正合同/依赖/范围冲突回主代理，不默认扩路径或伪造Spec Accepted。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；manual技术Adapter/immutable值，无新业务Bean。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；落实以上exact boundary/typed协议/time/secret/兼容，N/A面见§12。
- Implementation pseudocode:
```text
Lock baseline1de8dda, fresh blueprint hash and fifteen exact paths before production edits.
Check receipt hash inputs, conservative post-SDK outcomes and exact shared-verifier API without adding hidden consumers.
Validate strict Plan structure, all MC/Rules and root review before source GO.
```
- Verification contribution: TEST-001/002/003；实际证明范围/计数由evidence记录，不替代真实服务验收。
- After this file: 本组件有效RED→GREEN或既有回归/静态门禁闭合后推进依赖链，未启用写runtime。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service；strict从workspace root。
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=FixedBpmnStartToolTransportTest,TianjiFixedBpmnStartToolBindingsTest,TianjiFixedBpmnStartToolTransportTest,TianjiFixedDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,OrchestrationContractTest,ReadOnlyDecisionToolTransportTest,ToolInvocationRecorderTest,TianjiReadOnlyDecisionToolConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none`；`git diff --check`。
- Expected result: 新3Tests有效RED后所选11类GREEN，旧DMN19不变、同值常量兼容；UNKNOWN无ref/no resend、latevalidACK先callback；实际计数由日志填写。
- Failure returns to: fixture/compile回对应Test；receipt/source/once/error失败回Bindings/STARTTransport/helper；需扩SQL/config/WriteRecorder/权限/deps则回root，不自行扩scope。
- Completion criteria: 五别名、17MC/10Rule逐项实际证据、十五路径范围与独立审阅无阻断；无未定scratch。
- Rollback: 只十五路径回退/forward-fix，不删改任何旧SQL/journal/receipt；explicit库未配置，无数据回滚或repair。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindingsTest.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java、stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java、stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessClient.java、stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolBindings.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java、README.md、docs/egon/evidence/2026-11-01-fixed-bpmn-start-tool-transport.md、docs/egon/plan/2026-11-01-03-39-fixed-bpmn-start-tool-transport.md
- Commit: `feat(agent): add fixed BPMN start transport capability`

## 8. Test, Validation, and Quality Gates
| Test ID | Working directory | Exact command/source | Proof | Required result | Boundary |
| --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 focused新3Tests→完整selector | port/binding/START缺失行为 | 真实RED→GREEN，stub/fixture/compile分类；DMN19纯抽取baseline→after不伪RED | root Maven only |
| TEST-002 identity/receipt/failure | tianji-service | 新TransportTest/旧DMNTest | samebearer/两scope/source/key/trust/once/cancel/late；receipthash稳定变化矩阵；401/403 vs其余未知 | 无UNKNOWN ref/重发/instanceId假receipt；validlate优先callback，SDK前NOT_SENT明确 | actual SDK/mock JDK bytewire，controlledidentity无listener/网络/crypto/PG/model |
| TEST-003 static/compat | tianji-service | diffcheck/15paths/V13sha/imports；Contract/PS/Step12Cfg/readonly回归 | C/PS同值、旧RO/hash/DMN语义、未接write | 无新config/native/WriteRecorder/consume/SQL/API/权限/deps，helper无token/percallstate | 非运行证明 |
| TEST-004 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-03-39-fixed-bpmn-start-tool-transport.md --strict` | metadata/15files/trace/MC | exit0，不代表实现测试通过 | 文档owner |

## 9. Migration, Compatibility, Rollout, and Rollback
无SQL迁移/配置/flag/Bean/native/API endpoint/权限变化，V13 immutable；C两常量additive同值、PS只引用、原DMN/ROpublic API保留。库不自动运行，未来WriteRecorder/审批consume需单独实现和验证；UNKNOWN不自动重发。真实IAM/PG/模型/PSreceipt运行验收由后续环境提供，不自动启动服务/GUI/容器。回滚本15paths保留原数据/迁移/journal，禁止repair或回滚receipt事实。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/12 | Step 1 | ARport/Bindings/constants/新tests | TEST-001/002 | fixed write/action/schema/backend，不授执行权 |
| PLAN-REQ-002 | §13 | Step 1 | sharedVerifier/两Transport/DMNTest | TEST-001/002 | 独立真实SERVICE protocol与percallsecret，非USER OBO |
| PLAN-REQ-003 | §7/12 | Step 1 | package-private hostReceiptReference/Observation/Test | TEST-002 | 可信matchingACK canonical引用，无新lookup |
| PLAN-REQ-004 | §7/12 | Step 1 | OneShotStart/错误映射/latecallback | TEST-002 | 仅401/403known、其余POSTUNKNOWN/no redo |
| PLAN-REQ-005 | §6/17 | Step 1 | C/PS/DMN/旧回归/docs | TEST-003/004 | fifteenpaths与既有兼容，runtime/write审批仍pending |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | ACK不提供receiptUUID | 原PS commandreceipt/SDKmatchingACK，root/APIagent最终hash inputs锁定 | root | Closed：host引用PKnamespace+requestSHA+stableACK，非签名/instanceId |
| DEC-002 | 4xx或status0可能已生效 | root保守裁决：SDK后仅401/403known，其余UNKNOWN；SDK前可明确NOT_SENT | root | Closed：无自动POST重发或假reconcile |
| DEC-003 | 敏感验证复制/异常kind丢失 | helperowner精确API闭合，无token/state/broadcatch，ownFailure优先 | root/owner | Closed：DMN19 baseline→after，scope各自保留 |
| RISK-001 | USER审批/对象版本/费用误称 | PS执行owner是SERVICE；businessVersion非PS objectVersion，approvalconsume未实现；nonbillable仅固定内部START | root/后续 | Tracked：不声称OBO/PS版本校验/泛化未知费用退款 |
| RISK-002 | 实际运行/WriteRecorder未交付 | 只显式library、controlledSDK tests | 用户/root | Tracked：完整T3-07/平台Partial，后续独立consumer/环境验收 |

## 12. Review and Acceptance
十五路径只交付固定START能力与敏感校验复用，既有PS原生能力/身份/receipt均按实际source使用；无新引擎/配置/consumer。receipt、错误/late事实、helper/秘密所有权与纯抽取回归合同闭合。当前用户授权允许继续，计划保持Review；下面只证明计划可执行，evidence按实际source/log逐项重评。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | C同值常量/PS引用、AR独立port、AS固定Adapter/包内helper | 原模块边界，无新层/组件改造 | None |
| MC-REUSE-001 | Applicable | PASS | 原HttpProcessClient/startACK/hash/ToolActionRequest/RO中性executor/DMNcredential协议 | 只复用真实变化点，helper抽取敏感验证不复制身份引擎 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/SDK/ADK上下文/Trace/Spring/Jackson | 无新POM/dep/framework | None |
| MC-NAME-001 | Applicable | PASS | Start port/Bindings/BackendVO/Observation/TargetVO/CredentialVO/Verifier/Test | 技术角色与不可变值明确，receipt不称上游UUID | None |
| MC-VALID-001 | Applicable | PASS | exact action/run/lease/policy/schema；fixedBPMN/source/scopes/ACK；receipt hash | native/技术边界本域验证，无新HTTP transportDTO | None |
| MC-MODEL-001 | Applicable | PASS | immutable Request/Observation/Backend/Target/Credential与Set.copyOf | 无token字段/模型target输入/新实体或DTO副本 | None |
| MC-CONVERT-001 | Applicable | PASS | 原VerifiedPrincipalConverter复用；其余原typed值 | 不手工替代身份Converter，不造第二Mapper层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Adapter/helper无新增业务日志Service | Failure固定kind无cause，所有敏感VO脱敏 | None |
| MC-BEAN-001 | Not applicable | N/A | explicit library无Spring注册/config/factory新Bean | callerownedclient/sharedIAM不接管，不新增DI面 | None |
| MC-UTIL-001 | Applicable | PASS | 原hash/SDK/JDK/Trace、同源credential helper | 无新utility/thread/认证平台 | None |
| MC-JSON-001 | Applicable | PASS | 原HttpProcessClient typedStart/ACK与Jackson/schema | 不新PS字段/endpoint/JSONcodec；ACK严格原匹配 | None |
| MC-TIME-001 | Applicable | PASS | §4.9 wall/mono/Backendtimeout/expiry min | OAuth/JWT timestamp不互等；lateACK先callback，原DMN时间保持 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无properties/YAML/flags/wiring变更 | explicit library未激活，配置不扩大 | None |
| MC-PATTERN-001 | Applicable | PASS | 原SDK Adapter/callback capability+包内敏感校验抽取 | 单次动态scope，无第二loop/新WriteRecorder引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 十五路径、无SQL/native/config/permissions/deps/API activation | 仅固定START library，审批consume/费用泛化不实现 | None |
| MC-TEST-001 | Applicable | PASS | 新3测试真实RED/旧DMN19 baseline→after回归、ACK/receipt/failure矩阵 | 受控SDK bytewire，无listener/真实PG/crypto/model伪报 | None |
| MC-BLOCKER-001 | Applicable | PASS | root/APIagent/helperowner最终API/receipt/error闭合，paths已核 | 无未决业务合同或依赖，后续写执行限制显式 | None |

### 12.6 Final verdict
PASS — Ready for user review
