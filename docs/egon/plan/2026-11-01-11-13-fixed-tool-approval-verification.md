# 固定工具审批来源的有界SERVICE验证

| Field | Value |
| --- | --- |
| Document | `2026-11-01-11-13-fixed-tool-approval-verification.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 11:13 Asia/Shanghai` |
| Updated | `2026-11-01 11:46 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AA fixedSTART approval provenance explicit library；9paths |
| Source Requirement | 按原蓝图继续未完成工作并分步提交；§11.3/13审批来源/服务身份/绑定与partial failure |
| Baseline Revision | `main@901a57d80994c28d4c4aaaf2521d08a889a68417`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact用户执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `9655341c65373a44a31ba93b7dc18b888839d91e207f7b61b4c359ca2e3d34aa` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [受控工具显式装配](2026-11-01-10-15-governed-process-tool-assembly.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
九路径一个explicitlibrary Step：为固定AA START动作经独立verified TENANT SERVICE调用现PS审批验证API，将新provenance在有界callback内交付。新增typedport、有限hostbindings、ASadapter，复用现credentialhelper/SDK strict18fields，不新Source introspection/JSON/消费/START/配置。callback可已commit，后续权限来源/时间失效只抑制返回不撤销facts或批准重试。单commit `feat(agent): verify fixed tool approvals with service provenance`，全Goal仍Partial。

## 2. Target Spec and Effective Design
蓝图§11.3/13.1要求审批与原动作参数/业务版本/可信source绑定，实际独立SERVICE不冒充USER，§13.4要求partialfailure不抹已提交事实。ToolApprovalBinding/Query/VerifiedDTO/ApprovalClient与SDK guard已存在；本步只固定source/consumer并交付新的SDK provenance，不消费审批/执行业务START、不授caller当前PDP权限。记录/hash构造非认证或可脱离使用的签名。Review沿原exact用户授权，不伪Accepted；root全文/strict/resources后才sourceGO，nativeAA/atomicConsume及真实环境后续。

## 3. Effective Requirements and Acceptance
PLAN-REQ仅原要求局部trace aliases。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/13.1 | 审批验证请求固定原AA动作，来源观察不被当新权限 | fullaction/AA START/owner/runlease/deadline/receipt结构，7fieldObservation/sourceNamespace与原codec同标签；constructor非认证 | ARport/Test |
| PLAN-REQ-002 | §11.3/13.2 | host固定审批source/workflow/consumer，模型不能选身份来源 | finiteexactorigin/def/ref/risk/output/approvalpolicy/selfSERVICE；approvalbackend与businessSTART bindingSHA分离 | Bindings/Test |
| PLAN-REQ-003 | §13.1/13.4 | 独立真实SERVICE通过PS现guard获取新审批provenance | 共享credentialhelper/VERIFY_SCOPE ONLY/samebearer/consumer exact、单SDK strict18 verifyHTTP/no release GET/false或mismatch安全拒 | ASVerifier/Test/旧SDK回归 |
| PLAN-REQ-004 | §7/13.4 | 有界callback与partial failure保真实事实/安全生命周期 | onewallmono/noqueue/Trace/close清token/拒latecallback；callback可已commit，post失效仅抑制return不rollback/retry；不承诺持续撤销 | ASVerifier/Test |
| PLAN-REQ-005 | §17/T3-07 | 最小库切片与兼容/证据真实 | 9paths/23SQL immutable/无consume START cfg SDK codec改动，三组件有效RED后8类回归；全GoalPartial | all/tests/README/docs |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
最小port/records/constructors/AS方法UOE与close声明可先供测试编译，不先实现guards/执行。values测试RED→port GREEN；Bindings测试RED→bindings GREEN；上游合法fixture后Verifier测试实际RED→adapter GREEN，最后README/旧8类回归/evidence。各实现文件的compile-only先行声明不等功能先行，不提交stub，无sourceGO前只本两docs。
### 4.2 Test-first strategy
三独立有效RED barrier，root各自观察再最小GREEN；其他owner可只读准备后续测试，root跑reactor前所有Java冻结。constructor结构guard负例与真实callback/SDK/并发正例必须合法，编译/fixture失败不当RED；新增publicvalues/toString只是结构而非permission。纯现helper/SDK复用只回归，不伪造其RED。
### 4.3 Sequential and parallel boundaries
root owns ARport/Test+README 3；journal_design owns ASBindings/Test 2；tool_audit owns ASVerifier/Test 2；docowner2，共9paths。不是独自在代码库，不回滚其他owner；root统一Maven，others不构建。本Step提交前不并行下一Step，首个READY后plan冻结，仅root明确澄清才改。
### 4.4 Commit boundaries
单Step9paths/semanticcommit `feat(agent): verify fixed tool approvals with service provenance`；实际17MC/10Rules、source/独审/static/cached后精确stage一次，不push/历史改写/服务启动。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| callback验证port | §11.3/13 provenance来源需trusted delivery | 已有Binding/DTO但constructor不认证，不能收model JSON当proof | 一个typedcallback，flat7 metadata不导出bearer/client/operation | Implement |
| fixed审批source bindings | §13 host固定身份/URL/workflow | 现fixedBinding exactorigin/有限registry风格可复用 | 独立approvalBackend12，不比较businessSTART hash，不造新introspection | Implement |
| bounded ASadapter | §13 SERVICE/currentPSguard/安全窗口 | shared credentialhelper与SDK verifyApproval strict18已有 | Adapter+现boundedpool/Trace/token局部作用域，不重写IAM/SDK | Implement |
| consume/nativeAA | 后续atomic消费/审批执行独立要求 | V13/privatecodec有结构，当前无安全消耗路径 | 本Step明确不接，不借verify当permit | Not in this Step |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| public结构合同 | PLAN-REQ-001 | PortTest | 最小interface/records与现domainDTO | callback/source/expiry/namespace typedvalues | ASBindings/Verifier | Step 1 |
| finiteapprovalprofile | PLAN-REQ-002 | BindingsTest | port合法values/新records+resolveUOE | fixedsource/workflow/consumer | ASVerifier | Step 1 |
| trustedSDK delivery | PLAN-REQ-003/004/005 | VerifierTest | 上两组件GREEN+最小Verifierctor/method declarations | 有界当前source callback | futureconsumer，未接线 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AR application/tool ports与AS integration手工技术adapter | Agent当前结构/库边界保留 | 3prod3tests3docs，不加Bean/配置/DDL/CRUD/依赖，23SQL immutable | Step1；MC-ARCH-001/MC-SCOPE-001 |
| Reuse | 原ToolApprovalBinding/Query/DTO18/HttpProcessClient/credentialhelper | 真实SERVICE+现PS guard与typed协议 | VERIFYonly/一次POST，无release/sourceIntrospection/二parser/OBO | Step1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | 原immutable VO/typedDTO/safeFailure/source标签 | 6/7/12/9field bounded值，no新增entity/转换层 | 完整action/URI/consumer/workflow/time闭合，构造不认证 | Step1；MC-NAME-001/MC-VALID-001/MC-MODEL-001 |
| Bean/log/JSON/time/config | manualctors/现Trace/boundedexecutor/JDKclock | 无新业务Bean/log/JSON/config，原namespace版本 | 固定safe errors/脱敏、单window、callerHTTP不close，历史record不授新权 | Step1；MC-BEAN-001/MC-JSON-001/MC-TIME-001 |
| Pattern | 当前fixedprofile/callback Adapter/bounded transport | 实际approval来源与生命周期变化点 | 复用现模式，无新认证/State/工作流/transport引擎 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 服务身份/当前本地来源 | existing CredentialVerifier.credentials/requireCurrent | 检签/tenant/client/app/source/scopes/currentkey+ACTIVEtrust已有 | 不复制/修改helper，VERIFYonly Target | reuse | None | Step1；MC-REUSE-001 |
| HTTP/DTO/绑定 | existing SDKclient.verifyApproval/Query/DTO/Binding.requireApproved | strict18key/echo与action/workflow/hash/expiry已有 | 实际SDK fakeJDKwire测试，不新parser/PS接口 | reuse | None | Step1；MC-DEP-001 |
| namespace/生命周期 | privatecodec source标签、FixedStart scope/boundedexecutor/Trace | sourcehashexactdomain与局部token清理模式 | 新port派生hash复用digest，不公开codec/改旧transport | reuse | None | Step1；MC-SCOPE-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §11.3/13/17 | 窄Verifier/Bindings/typedVO角色清楚 | port/3tests→3prod | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | 命名/source | Step1 | PASS |
| Rule 2 | §11.3/13/17 | AA/fullaction/source/workflow/consumer/URI/window/SDKecho校验 | values/Bindings/ASVerifier | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | 正反/failclosed | Step1 | PASS |
| Rule 3 | §11.3/13/17 | 不可变record profiles/observation，旧DTO/SQL不改 | 3prod/原typedcontracts | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | 结构/红acted/compat | Step1 | PASS |
| Rule 4 | §11.3/13/17 | manualtechnical库无新业务Bean/DI/log | scope | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | N/A | Step1 | N/A：无新业务Bean/config |
| Rule 5 | §11.3/13/17 | 现JDK/digest/SDK/credentialHelper/Trace复用 | imports/3prod | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | POM/无新utility | Step1 | PASS |
| Rule 6 | §11.3/13/17 | existingSDK strict18/codec无改，namespace同标签无publicJSON | ASVerifier/旧SDKtests | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | wire/无ordinal | Step1 | PASS |
| Rule 7 | §11.3/13/17 | 无配置/YAML/profile/flag/自动注册 | scope | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | N/A | Step1 | N/A：无新业务Bean/config |
| Rule 9 | §11.3/13/17 | 现callback Adapter/finiteprofile/ownedboundedexecutor | ASVerifier/Bindings | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | 身份/并发/partialfailure | Step1 | PASS |
| Rule 10 | §11.3/13/17 | Instant/Duration/Clock/mono/DTO30s，不reset/伪时间下界 | port/ASVerifier/tests | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | deadline/latecallback | Step1 | PASS |
| Rule 11 | §11.3/13/17 | 当前Agent结构/技术library/generatorexcluded/23SQL保留，原CQE不变 | 9paths | 本file及§6 exactprofile/typedproof/SERVICE/window/错误/partialfailure合同 | scope/bytefreeze | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Request.deadline/oneentrywindow | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对窗口 | Not applicable: 无UI | min(requestDeadline,runDeadline,originalStart+backendTimeout)，wall+mono不reset，SDK/credential/回调共预算；无Java lease expiryhint门禁 | package Clock/LongSupplier沿既有，defaultUTC/Systemnano | 不改原lease/Store/PDP期限规则，futureconsume独立校验 | Step 1；TEST-001/003 |
| TIME-002 | approval occurred/verified/expires/consumerExpiry | Occurred instant | Instant | Not persisted | UTC Z | Not applicable: server投影绝对时刻 | Not applicable: 无UI | 原DTO occurred<=verified<expires且<=30s；requireApproved用当前clock、无verifiedAt>=callstart下界；scope再min proof/credential expiry；Observation expiry取二者min | 保原SDK Instant精度，不加rounding/DB转换 | actual新HTTP+当前PSguard才fresh，不把保存值当未来权限；postcallback过期不能撤回facts | Step 1；TEST-001/003/004 |
| TIME-003 | backend.timeout | Duration | Duration | Not persisted | Internal | Not applicable: 技术duration | Not applicable: 无UI | host固定100ms..30s，所有stage共原预算，无retry/第二window | JDK Duration/no新框架 | 不添加globaltimeout/配置或continuousrevocation期限 | Step 1；TEST-002/003 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifierTest.java | 先锁定AA START request/扁平source观察值与namespace/安全异常结构 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifier.java | 显式approval验证callback窄port，不输出bearer/client/业务操作 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindingsTest.java | 先证明有限source/workflow绑定与业务START backendSHA严格分离 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindings.java | host固定approval来源/consumer/workflow的有限immutable registry | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifierTest.java | 实际shared credential helper+SDK字节协议验证bounded callback/错误/生命周期 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifier.java | 以独立verified TENANT SERVICE读取PS审批provenance并在有界callback中交付 | Step1 |
| MODIFY | README.md | 记录固定审批验证库/source可信边界与后续原子consume/AA未交付 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-11-13-fixed-tool-approval-verification.md | 锁定9paths/三组件真实RED/固定合同和手工门禁 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-fixed-tool-approval-verification.md | 真实执行PENDING骨架与后续逐项证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
baseline901a57dclean、SpecSHA9655341c65373a44a31ba93b7dc18b888839d91e207f7b61b4c359ca2e3d34aa与actualpaths已核。Created11:13为root请求草稿时刻；writing18md24refs/executing15md19refs资源通过。全部23SQL immutable；无codec/publicJSON/Store/Recorder/SDK/PS/oldtransport/helper/config/权限变动，Agent generatorexcluded、不新增CRUD/MP迁移/依赖或codegen日志。

**Public port。** FixedToolApprovalVerifier唯一<T>T withVerifiedApproval(Request,Callback<T>)；Callback<T>.apply(ApprovedObservation)一个参数。Request(AgentRunAuthorization.Request run,AgentRunLease lease,ToolDescriptor descriptor,ToolActionRequest action,String approvalReceiptId,Instant deadlineAt)：verified非LEGACY owner，AA+fixedSTART resource/requiredSTRINGinstanceId；以run.actor/descriptor/businesskey/version/normalizedargs重建action，比intentID/requestSHA/policy/fullref；runID==lease.runID/epoch>0/deadline<=run.deadline，时间liveness在AS，不因Java过期hint拒绝。receipt按现OrchestrationChecks.id同JDK规则canonical1..255/安全UTF8/无control，不假定UUID；parametersSha256=descriptor.parametersSha256(action.arguments)。无callerbinding/source/consumer/URL/nativeUUID/window字段，redacted。

ApprovedObservation(String sourceId,String sourceIssuer,String sourceResource,ToolApprovalBinding binding,VerifiedApprovalDTO approval,TrustedAgentActor consumer,Instant consumerExpiresAt)扁平7field/redacted。sourceId正则[A-Za-z][A-Za-z0-9_.:-]{0,127}；issuer/resource exactASCII normalizedHTTPS max2048/512、nouserinfo/query/fragment。actualconsumer verified非LEGACY SERVICE且equals binding.consumer；按sourceIssuer重新TrustedAgentActor.verified(tenant,SERVICE,consumer.subjectId,sourceIssuer)必须相等；approvedtrue；consumerExpiry非null且>proof.verifiedAt；bindingpolicyRef/policyBindingSHA/release/defoKey+SHA/taskKey与DTO结构一致。tenantId从consumer；expiresAt=min(consumerExpiresAt,approval.expiresAt)。sourceNamespaceSha256严格用既有标签TIANJI_TOOL_APPROVAL_SOURCE_V1 +sourceId+Longtenant+issuer+resource，OrchestrationRequestDigests.hash(lengthprefixList,Map.of())；不加入consumer/policy/tool/baseURI/辅助alias。sourceId须host canonical registry稳定ID，不能由模型/consumer/policy/tool任意改名绕来源，constructor无法识别全局任意别名，operator负责一致性。结构/hash/保存record都不证明认证、签名或未来许可；实际callback由trustedSDK来源交付，不导出bearer/client/OneShot。

FailureKind EXACT6：CREDENTIAL_UNAVAILABLE、APPROVAL_DENIED、APPROVAL_UNAVAILABLE、APPROVAL_INVALID_RESPONSE、CANCELLED、DEADLINE_EXCEEDED。final Failure extends RuntimeException，kind()，固定kind-only safe message/toString、nullcause，不含status/URI/body/token。callback AgentLedgerException保持code但固定安全msg/无私有cause；其他callbackRuntime安全unavailable，不推断拒绝/可重试。

**AS bindings。** ctor(Collection<ToolDescriptor> descriptors,Collection<BindingVO> bindings)，两者各1..256/immutable/no duplicateversion/slot与同origin-definition hashdrift；bindings()immutable，resolve(Request):ResolvedBindingVO(descriptor,binding)。BackendVO EXACT12(String sourceId,long tenantId,URI processIssuer,URI processBaseUri,URI processResourceUri,String registrationId,String expectedClientId,String expectedAppId,String expectedSourceBiz,String expectedSourceApp,String expectedEnvironment,Duration timeout)。issuer/resource同Observation限制；base HTTPS或loopbackHTTP沿siblings，exactASCII/normalized/无userinfo/query/fragment；timeout100ms..30s，identifier bounds沿siblings；URI equals/hashCode按ASCII。

BindingVO EXACT9(long tenantId,String ownerIssuerFingerprint,TrustedAgentActor.SubjectType principalType,String definitionKey,long definitionVersion,String definitionContentSha256,DefinitionResourceRef tool,ToolApprovalBinding approval,BackendVO backend)。slot=origin tenant/issuer/type/Agentdefkey-version/toolkey-version（hashdrift不另造slot）；lookup后比fullDefSHA/fullToolRef/content/AA START profile/outputexactrequiredSTRINGinstanceId。approval.policyRef==descriptor.policy.approvalPolicyRef；consumer tenant/issuer等于origin+backendprocessIssuer fingerprint，subjectId==expectedClientId。SERVICE requester self-only，通过approval.query(action,receipt)复用requirePolicy；USER可不同subject但同tenantissuer。

**关键分离：approval backend不是业务START backend，禁止将其与descriptor.policy.bindingSha256比较或添加fake approvalBackendSHA。** 原ToolApprovalBinding固定审批workflow/policyBindingSHA/release/definition/task，descriptor原business backend/hash与actionrequest保持；不造配置/Script/ProviderResolver。

**AS verifier与生命周期。** public final implements port,AutoCloseable；public ctor(bindings,oauth,authentication,converter,issuers,callerHttp,int concurrency)，package追加Clock/LongSupplier；六pure usesBindings/OAuth/Authentication/Converter/IssuerRegistry/HttpClient仅pointer；无SpringBean/storageflag。callerHttp要求Redirect.NEVER且永不close；concurrency1..8，ThreadPoolExecutor(0,max,SynchronousQueue,AbortPolicy)、noninheritingvirtual threads，TraceContext.capture/open/close；不继承SecurityContext/rawUser材料。只关自有trackedtask/pool，close/cancel/finally先原子清active/bearer再cancel future，publish短锁防非协作SDK晚回填；无token/client缓存或ThreadLocal泄漏。

nullrequest/callback/ambientTX在work前安全拒（沿siblinginvalid/Failure风格）；复用worker在credential helper前再次查actualTX并CREDENTIAL_UNAVAILABLE，current检查前若active TX则CANCELLED；noninherit不清普通ThreadLocal，不rollback/cleanup非本Verifier拥有的事务；closed/interrupted CANCELLED且保flag，busy CREDENTIAL_UNAVAILABLE。固定resolve后ONE originalentry wall+mono窗口min(req/run/entry+backendTimeout)，所有OAuth/JWKS/helper/SDK/callback共剩余预算，无reset/阶段重建。复用sharedCredentialVerifier.credentials/requireCurrent、Target scopes EXACT Set.of(ApprovalClient.VERIFY_SCOPE)，不加releaseREAD、不调用releaseClient/GET。helper验证TENANT SERVICE签名proof/ACTIVE issuer/client/app/source/env/tenant/唯一PSaudience/scopes/同请求bearer；credential.actor必须EXACT pinned approval.consumer（不是原USER）才发SDK。

actual HttpProcessClient(fixed backend base,callerHttp,requestlocal固定bearerSupplier,remainingDuration)仅一次verifyApproval(binding.query(action,receipt))，现SDK strict18field/echo/size不改。SDK前后localkey/trust/window检查，binding.requireApproved(action,proof,clock.instant())核current时刻与全绑定/approvedtrue；scope.remaining进一步cap proof expiry和credential expiry。Observation固定source来自backend，consumerExpiry用helper的min OAuth/JWT/entrywindow（可以早于proof expiry，PS不知本地100ms timeout）。callback once、worker内当前window前后核验；Future.get返回后caller最终handoff必须再current key/trust/window检查，不能只有scope.remaining；不提供public裸DTO验证返回API。fresh是实际新SDK HTTP/当前PS SERVICEguard+requireApproved，而非proof.verifiedAt>=entry（禁止新下界）。既有DTO<=30s短窗保持。

| Failure source | Safe result | Boundary |
| --- | --- | --- |
| false/localbinding-workflow-action mismatch、SDK已通过而本地requireApproved发现stale；PS401/403/404 | APPROVAL_DENIED | 不产生ApprovedObservation，不消费/重派 |
| SDK TIANJI_INVALID_RESPONSE/TIANJI_RESPONSE_TOO_LARGE（含queryecho及SDK已检测到的过期响应） | APPROVAL_INVALID_RESPONSE | 先遵SDK错误码，不将SDK invalid错误误映本地stale；不改SDK/二parser/时间下界 |
| token/actualconsumer/currentkey/ACTIVEtrust mismatch | CREDENTIAL_UNAVAILABLE | helper本地current检查，不承诺token持续撤销或currentrequesterPDP |
| 其他remote/error | APPROVAL_UNAVAILABLE | 固定安全错误，不解释为可retry |
| ownwindow过期；interrupt/close；current发现callback遗留TX | DEADLINE_EXCEEDED；CANCELLED | helper前发现worker遗留TX为CREDENTIAL_UNAVAILABLE；不清/回滚foreignTX；原预算/清token/保中断 |
| callbackLedger；其他callbackRuntime | 保code固定msg；APPROVAL_UNAVAILABLE | 不泄private信息，不将回调已commit事实rollback |

**CALLBACK MAY ALREADY COMMIT。** aftercallback key/trust/expiry或futuretimeout可抛错/抑制返回，但不撤销facts/释放approval或授权START重试。helper只检查LOCAL currentAuthKey+ACTIVEissuer，不轮询tokenrevocation/currentrun cancel/lease/ownerPDP，不宣称持续撤销监听、detached signedproof或currentapproverrole。PS HTTP当前SERVICEguard仍是真授权点；DTO是已记录approverRoleWitness/currentregistration投影，无approverIDs/authVersion字段，不新造。futureDBconsume/dispatch必须独立RequesterPDP/lease/atomicconsumption，未在本步交付。

Plan Clarification CLAR-001：sourceNamespace同privatecodec现标签但不公开/改codec，approvalBackend独立businessSHA；identifier安全规则用JDK与现contract一致。实际existing tests八类已rg核，ApprovalConsumerRegistryTest不存在不纳入。metadata仅结构；原USER审批workflow需其正常PSaudience登录执行sourceStart(action)，本Verifier只有独立SERVICE VERIFY_SCOPE，不做USER OBO/START/consume/config。

Plan Clarification CLAR-002（root独审后批准、两小源码fix之前先读本delta/strict）：worker的noninheritvirtual不保证普通Spring ThreadLocal事务已清，必须在credentialhelper前reject actualTX→CREDENTIAL_UNAVAILABLE，所有current前reject actualTX→CANCELLED（含postcallback）；不rollback/unbind/cleanup foreignTX。Future.get后的caller最终交付必须完整current key/trust/window复核，避免worker末次current与caller返回间来源变化漏检；不新增API/window。两项TEST ONLY反证先有效RED再最小sourcefix。错误分类明确：SDK自己判过期并给TIANJI_INVALID_RESPONSE时APPROVAL_INVALID_RESPONSE；SDK通过后local requireApproved时刻stale才APPROVAL_DENIED。首组合35=34PASS/1路径fixtureFAIL，现SDK保留base /base+ApprovalClient.PATH，测试仅修literal不改SDK/route；原GO plan4029与amendedSHA在evidence分别保留。9paths/Source Requirement/Created/原业务时间预算与所有协议保持，未最终验收。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 固定AA START审批来源的SERVICE验证callback
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交901a57d；现ToolApprovalBinding/Query/DTO/ApprovalClient/HttpProcessClient/sharedCredentialVerifier/Trace。
- Baseline state: 有typed审批结构与SDK PS验证接口，无固定hostsource绑定/独立可信callbackVerifier；无可安全消费/AA native链。
- Observable outcome: 新固定approval source通过actualSERVICE与现PSguard/SDK给出有界新provenance，错误安全且callback已提交facts不丢。
- End state: 显式library port/bindings/verifier与受控测试可用，不提供consume/START/Bean/flag/JSON/持久化或nativeAA。
- Test-first gate: Required — 最小records/ctor/method声明支持各自真实RED后GREEN，ASVerifier上游合法且自身UOE，编译/fixture不当RED，不提交stub。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifierTest.java`
- Purpose: 先锁定AA START request/扁平source观察值与namespace/安全异常结构。
- Symbols: Request/ApprovedObservation/Callback/FailureKind/Failure tests。
- Repository evidence: ToolActionRequest/ToolApprovalBinding/VerifiedApprovalDTO与private codec source标签已存在。
- Dependencies and consumers: 新port最小interface/records/UOE或暂空guards声明支持编译，既有合法typedfixture。
- Why now: values组件真实RED后才完整guards，不把编译失败当RED。
- Contract/signature changes: 6fieldRequest/7fieldObservation/exact6FailureKinds，所有toString脱敏。
- Input/output and state mapping: full action重建/AA+START/verifiedowner/runleaseepoch/deadline；receipt合法非UUID字符串；knownnamespace vector与expirymin。
- Error and edge behavior: sourceURI/ascii/consumerbinding/workflow/approvedtrue/时刻关系错拒；构造不认证，不新verifiedAt>=entry约束。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
assertRequestReconstructsExactActionAndRejectsWrongOwnerRefPolicyArgsRiskRunLeaseOrDeadline();
assertCanonicalNonUuidReceiptIsValidAndMalformedUtf8ControlWhitespaceIsRejected();
assertObservationSourceNamespaceMatchesExistingCodecDomainVectorAndExcludesConsumerPolicyToolBase();
assertStructuralConsumerWorkflowAndApprovedExpiryGuardsAndMinimumExpiry();
assertAllSixFailureKindsUseOnlySafeKindWithoutCauseAndAllValueToStringsAreRedacted();
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: publicvalues行为有效RED，合法fixture支持后续AS测试。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifier.java`
- Purpose: 显式approval验证callback窄port，不输出bearer/client/业务操作。
- Symbols: public interface withVerifiedApproval(Request,Callback<T>):T；nested Request/ApprovedObservation/Failure。
- Repository evidence: 现tool ports使用immutable record/callback/safeFailure；existingApprovalDTO18与sourceSHA语义。
- Dependencies and consumers: File1有效RED、原domain/contract/digest；ASadapter/未来consumer。
- Why now: 最小声明先行仅编译；完整values guards与派生方法在组件RED后实现。
- Contract/signature changes: §6 exactfields；Request.parametersSha256、Observation.tenantId/sourceNamespaceSha256/expiresAt；无Spring/JSON/消耗API。
- Input/output and state mapping: SourceNamespace固定TIANJI_TOOL_APPROVAL_SOURCE_V1+canonicalSourceID/tenant/issuer/resource；consumer==binding.consumer且源issuer重建actor一致，approvedtrue。
- Error and edge behavior: sourcehash/constructor只是结构；不授当前权限/持久后复用许可；Failure nullcause/safe kind，receipt不强制UUID。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
interface FixedToolApprovalVerifier { <T> T withVerifiedApproval(Request request, Callback<T> callback); }
record Request(run,lease,descriptor,action,approvalReceiptId,deadlineAt) { validateExactAaStartActionAndRunLease(); String parametersSha256(){ return descriptor.parametersSha256(action.arguments()); } }
record ApprovedObservation(sourceId,sourceIssuer,sourceResource,binding,approval,consumer,consumerExpiresAt) {
    validateClosedSourceConsumerBindingWorkflowAndApprovedTimeStructure();
    String sourceNamespaceSha256(){ return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_APPROVAL_SOURCE_V1",sourceId,Long.toString(tenantId()),sourceIssuer,sourceResource),Map.of()); }
    Instant expiresAt(){ return minimum(consumerExpiresAt,approval.expiresAt()); }
}
// Callback<T>.apply(ApprovedObservation), exact six FailureKind values; Failure(kind) super(kind-only-safe-message,null), redacted values.
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: port结构合同可用，不提供detached auth/消费/START或raw bearer。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindingsTest.java`
- Purpose: 先证明有限source/workflow绑定与业务START backendSHA严格分离。
- Symbols: BackendVO12/BindingVO9/ResolvedBindingVO/resolve tests。
- Repository evidence: 现fixedbinding风格与ToolApprovalBinding.requirePolicy/self-SERVICE约束。
- Dependencies and consumers: ARvalues合法；ASBindings最小记录/ctor与resolve UOE。
- Why now: binding组件真实RED后GREEN；不因businessHash不同错误拒approvalSource。
- Contract/signature changes: descriptors/bindings各1..256、immutable/no dupVersion/slot/originDefHashdrift。
- Input/output and state mapping: tenant/issuer/type/Agentdefslot+fullhash/toolref/AA START输出schema/approvalpolicy+consumer+backendissuerclient匹配。
- Error and edge behavior: USER可不同subject同tenantissuer；SERVICE必须self；exactASCII URI equals/hash；sourceId别名canonical由operator负责非constructor猜测。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
assertFiniteImmutableRegistrationsAndBindingsRejectDuplicateVersionsSlotsAndDefinitionHashDrift();
assertResolvePinsFullOriginDefinitionToolRefAaStartSchemaAndApprovalPolicyWorkflow();
assertApprovalBackendIsIndependentOfBusinessStartPolicyBindingShaAndNeverComparedAsFakeApprovalHash();
assertUserRequesterAndActualServiceConsumerAreSeparateButServiceRequesterMustBeSelf();
assertCanonicalSourceUrisBackendBaseLoopbackRuleAndExactAsciiEqualityBounds();
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: AS绑定有效RED，sourceprofile受控但metadata不等远端权限。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindings.java`
- Purpose: host固定approval来源/consumer/workflow的有限immutable registry。
- Symbols: ctor(descriptors,bindings)；bindings/resolve(Request):ResolvedBindingVO；BackendVO/BindingVO。
- Repository evidence: 现FixedBpmnStart/DecisionBindings有exactorigin/slot/fullref、URI规范/immutable风格。
- Dependencies and consumers: File3有效RED、原ToolRegistry/ToolApprovalBinding/typedport。
- Why now: binding组件有效RED后实现，不扩oldlibs或添approvalBackendSHA。
- Contract/signature changes: §6 exact12/9fields；no dupVersion/slot/hashdrift；immutable列表/脱敏，BackendURI equals/hashCode按ASCII。
- Input/output and state mapping: profileAA+ProcessClient.START_RESOURCE_KEY+exactrequiredinstanceId STRING；consumer源tenantissuer/client与Requesterorigin固定，resolve调用approval.query/requirePolicy验证action。
- Error and edge behavior: sourceissuer/resourceHTTPS2048/512；base HTTPS或loopbackHTTP同siblings，timeout100ms..30s；不引新Provider/Script/配置/descriptorhash循环。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
TianjiFixedToolApprovalBindings(descriptors,bindings) { validateBothFiniteOneTo256AndCopy(); rejectDuplicateToolVersionsSlotsAndOriginDefinitionHashDrift(); }
ResolvedBindingVO resolve(Request request) {
    var selected = exactOriginAndDefinitionKeyVersionSlotLookup(request);
    requireFullDefinitionShaToolRefRiskAaStartAndRequiredInstanceIdSchema(selected,request.descriptor());
    requireApprovalPolicyConsumerTenantIssuerClientAndBackendSource(selected); selected.approval().query(request.action(),request.approvalReceiptId());
    return new ResolvedBindingVO(request.descriptor(),selected);
}
// Do not compare descriptor.policy.bindingSha256 to the separate approval BackendVO and do not invent an approval backend digest.
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: 只有固定registry映射可选实际source/consumer，business backend独立。

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifierTest.java`
- Purpose: 实际shared credential helper+SDK字节协议验证bounded callback/错误/生命周期。
- Symbols: Verifier测试、mock JDK HttpClient bytewire/controlled IAM与Clock/nano。
- Repository evidence: FixedStartTransportTest有boundedrequest-localtoken/Trace/close模式，现HttpProcessClient strict18fields。
- Dependencies and consumers: AR/Bindings各GREEN、newVerifier最小ctor/helper/withVerifiedApproval UOE供本组件编译。
- Why now: 确保RED来自本Verifier缺行为而非未实现上游/坏fixture；root观察后才GREEN。
- Contract/signature changes: newTest，不改shared helper/SDK/PS/oldtransport；调用actualSDK一次verify POST。
- Input/output and state mapping: VERIFY_SCOPE ONLY/samebearer/exactactualSERVICEconsumer/namespace；false/echo/workflow/hash/keytrust/time/回调commit后postfailure；no release GET。
- Error and edge behavior: 无真实listener/IAM crypto；queuebusy/noninheritTracecleanup/close/interruption/latecallback/token清除；callerHTTP不close，error安全且callbackLedger保code。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
assertActualSdkVerifyApprovalPostsOneFixedQueryWithVerifyOnlyScopeAndSameValidatedServiceBearer();
assertFalseExpiredOrPinnedWorkflowMismatchDeniedAndInvalidSdkEchoOrOversizeMappedInvalidResponse();
assertLocalCurrentKeyTrustConsumerMismatchCredentialUnavailableWithoutClaimingContinuousRevocation();
assertOneOriginalWallMonoBudgetBusyCancellationCloseAndLateSdkCannotPublishBearerOrInvokeCallback();
assertCallbackOnceWithinWindowAndCommittedFactSurvivesPostCallbackExpiryTrustOrTimeoutSuppression();
assertTraceCleanupSafeLedgerCodePreservationAndCallerHttpNeverClosed();
assertFinalCallerKeyTrustDriftAfterWorkerSuccessIsRejectedBeforeHandoff();
assertReusedWorkerForeignTransactionStopsBeforeCredentialHelperAndPostCallbackCurrentWithoutCleanup();
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: 实际SDK/identityhelper边界有效RED，不把mockcrypto当真IAM。

#### File 6 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifier.java`
- Purpose: 以独立verified TENANT SERVICE读取PS审批provenance并在有界callback中交付。
- Symbols: implements FixedToolApprovalVerifier,AutoCloseable；publicctor6deps+concurrency，package Clock/nano；6identityhelpers。
- Repository evidence: 现CredentialVerifier.credentials/requireCurrent与fixedtransport boundedexecutor/Trace/window/token生命周期可复用。
- Dependencies and consumers: File5有效RED；Bindings.resolve/原ApprovalClient.VERIFY_SCOPE/HttpProcessClient.verifyApproval。
- Why now: 自身有效RED后实现；不新认证/transport引擎、不改sharedhelper/SDK。
- Contract/signature changes: §6详细API/错误映射；ownpool0..concurrency/SynchronousQueue/noninheritvirtual，外部HttpClient必须NEVER且不close。
- Input/output and state mapping: resolvefixedprofile→sameentrywindow→worker TXguard/helper VERIFYonly→singleSDKverify→localproof/oncecallback→worker current→Future.get后caller current key/trust/window再handoff。
- Error and edge behavior: Null/ambient前拒；helper前workerTX拒CREDENTIAL_UNAVAILABLE，current遗留TX拒CANCELLED且不cleanup foreignTX；callerhandoff再完整current。SDK过期invalid→INVALID_RESPONSE，localstale→DENIED；其他原localtrust/partialcommit/token边界不变。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```java
withVerifiedApproval(request,callback) {
    rejectInvalidAmbientClosedOrInterruptedBeforeWork(); var fixed=bindings.resolve(request);
    var scope=originalWallMonoWindowMinimumRequestRunAndBackendTimeout();
    var future=submitUnqueuedNonInheritedWorkerWithCapturedTrace(() -> {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw failure(CREDENTIAL_UNAVAILABLE); // BEFORE helper; do not clear foreign TX.
        var credential=sharedVerifier.credentials(targetWithOnlyVerifyScope(fixed),scope.deadline(),scope::remaining,scope::publishBearer,scope::bearer);
        requireCredentialActorEqualsPinnedApprovalConsumer(); currentRejectsActiveTxThenKeyTrustWindow();
        var proof=new HttpProcessClient(fixedBase,callerHttp,requestLocalBearerSupplier,scope.remainingDuration()).verifyApproval(fixed.approval().query(request.action(),request.approvalReceiptId()));
        currentRejectsActiveTxThenKeyTrustWindow(); fixed.approval().requireApproved(request.action(),proof,clock.instant()); capRemainingByProofAndCredentialExpiry();
        var value=callback.apply(observationFromFixedSourceCredentialAndProof()); currentRejectsActiveTxThenKeyTrustWindow(); return value;
    });
    T value=future.get(scope.remaining(), NANOSECONDS);
    currentRejectsActiveTxThenKeyTrustWindow(); return value; // final CALLER handoff, not remaining-only.
    // Worker/caller finally still clear bearer before cancel; current activeTX is CANCELLED, no foreign transaction cleanup or budget reset.
}
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: 显式Verifier callback来源可信且有界，仍不消费审批/授权START或启动workflow。

#### File 7 — `MODIFY README.md`
- Purpose: 记录固定审批验证库/source可信边界与后续原子consume/AA未交付。
- Symbols: approval source验证/服务身份/有限窗口说明。
- Repository evidence: 现AA ToolApprovalBinding/privateproof与fixedSTART仅库，native仅IDW模式。
- Dependencies and consumers: 生产/tests actualGREEN后root据实现写。
- Why now: 不把验证来源读API误称approval消费/当前requester授权/已启nativeAA。
- Contract/signature changes: 无Bean/config/flag/endpoint/SDK变更。
- Input/output and state mapping: 真实SERVICEconsumer VERIFY_SCOPE，原USER需其PS audience正常登录启动sourceStart(action)，不是USER OBO。
- Error and edge behavior: SourceNamespace历史hash与constructor不是签名/未来许可；localkeytrust非连续撤销；callback已commit后失败不撤回facts。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Document the fixed library callback verifier, actual SERVICE consumer and canonical host-owned source namespace.
State that original USER approval workflow start requires its normal PS-audience authentication and that verification neither consumes approval nor authorizes or retries START.
Explain short provenance expiry, local-current key/trust limits, callback-after-commit suppression and pending native-AA/atomic-consumption/runtime work.
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: README只述实际explicitlib与权限/历史fact/后续边界。

#### File 8 — `CREATE docs/egon/plan/2026-11-01-11-13-fixed-tool-approval-verification.md`
- Purpose: 锁定9paths/三组件真实RED/固定合同和手工门禁。
- Symbols: Template5/Review/5aliases/17MC/10Rules/time。
- Repository evidence: 901a57d clean/Spec9655...34aa与root合同1–4/实际8selector已核。
- Dependencies and consumers: 写作/执行资源与原shared APIs。
- Why now: root全文/strict/resources后才sourceGO；第一次READY冻结。
- Contract/signature changes: Created11:13为root请求草稿时刻，无Accepted/sourceGO/count虚构。
- Input/output and state mapping: values→bindings→verifier各有效RED后GREEN，root统一Maven/一commit。
- Error and edge behavior: 不改23SQL/codec/Store/Recorder/SDK/helper/PS/config，禁止隐含源introspection或OBO。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Record the exact nine paths, authoritative interface/profile/credential/window/error contracts and source revision.
Bind three independent valid RED barriers and eight-class regression to provenance-only semantics and immutable old SQL/library scope.
Validate strictly before root full review and source GO, freeze after first READY, and leave actual execution conclusions pending.
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: strict/资源/全文后才源码，source事实在evidence后填。

#### File 9 — `CREATE docs/egon/evidence/2026-11-01-fixed-tool-approval-verification.md`
- Purpose: 真实执行PENDING骨架与后续逐项证据。
- Symbols: 5aliases/17MC/10Rules/实际log/Partial。
- Repository evidence: 本Step计划阶段无sourceGO/实际RED/GREEN。
- Dependencies and consumers: 计划与root真实日志/source/审计。
- Why now: plan先READY再PENDING，不替代执行证明。
- Contract/signature changes: 不预填PASS/count/真实crypto或未来commithash。
- Input/output and state mapping: SDK字节wire/受控IAM测试与真服务分开；proof.constructor不是认证。
- Error and edge behavior: 无repo harness/DB/浏览器/服务启动，docowner不source/README/build/commit。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；具体本块与§6，不新businessBean/转换层，N/A见§12。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守fixedsource/fullaction/typedSERVICE/sharedSDK/window/safeerror/partialfailure与无consume边界，Rule4/7无新业务Bean/config。
- Implementation pseudocode:
```text
Create independent pending rows for five aliases, seventeen manual checks and ten literal rules.
Preserve actual failures, direct stubs, fixtures and compilation separately, and close only source/test gates evidenced by root logs and review.
Retain no-consume/no-start/no-continuous-revocation/real-runtime limits and full-goal partial status; defer commit facts to Git delivery.
```
- Verification contribution: TEST-001/002/003/004/005按角色；actual日志后填，不以fakeJDK/IAM证明真实crypto/PS/PG/应用验收。
- After this file: evidence全PENDING，实际后逐项闭合。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace root。
- Verification command: §8 canonical source-reactor exact8类，root统一Maven，其他owner冻结Java；docowner不构建。
- Expected result: 三组件有效RED后完整GREEN，命名/namespace/fullaction/approvalBackend分离/VERIFYonly/SDK18/onewindow/Trace/close/partialfailure矩阵闭合，actualcount后填。
- Failure returns to: 值/namespace回port，origin/profile/consumer/businessSHA误耦合回Bindings，身份/SDK/error/window/latecallback回ASVerifier；fixture错先修再有效RED，需consume/新API/SDK/SQL/deps返回root不扩scope。
- Completion criteria: 9paths无operationalstub，17MC/10Rules实际逐项闭合，23SQL/未改helper-client-codec旧源、root全文/独审/diff/cached通过，无真环境假验收。
- Rollback: 可回退binary，现SQL/审批与工具facts留存，不repair/drop/delete/backfill/业务重派；无新DDL/配置需撤销。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifierTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedToolApprovalVerifier.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindingsTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalBindings.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifierTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedToolApprovalVerifier.java、README.md、docs/egon/plan/2026-11-01-11-13-fixed-tool-approval-verification.md、docs/egon/evidence/2026-11-01-fixed-tool-approval-verification.md
- Commit: `feat(agent): verify fixed tool approvals with service provenance`

## 8. Test, Validation, and Quality Gates
root执行以下计划命令，尚未运行；所有Javaowner先冻结：
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=FixedToolApprovalVerifierTest,TianjiFixedToolApprovalBindingsTest,TianjiFixedToolApprovalVerifierTest,ToolApprovalBindingTest,ToolExecutionJsonCodecTest,HttpProcessClientTest,ApprovalVerificationServiceTest,TianjiFixedBpmnStartToolTransportTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-fixed-tool-approval-final.log 2>&1
```

| Gate | Working directory | Exact tests/command | Proof | Boundary | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-001 values | tianji-service | FixedToolApprovalVerifierTest，先RED后GREEN | fullaction/AAprofile/receipt/sourceURI/namespacevector/consumer/workflow/expiry/error/redacted | 构造/哈希非认证，不用未来time下界 | root |
| TEST-002 bindings | tianji-service | TianjiFixedToolApprovalBindingsTest，先RED后GREEN | finite/dup/origin-fullhash-ref/consumerUSER-vsSERVICE/URI/approvalBackend独立businessSHA | hostcanonicalsourceId/operator别名不自动识别 | journal/root |
| TEST-003 adapter | tianji-service | TianjiFixedToolApprovalVerifierTest，先RED后GREEN | actualhelper+actualSDK18字节wire/VERIFYonly/onePOST/noGET/samebearer/keytrust/TTL/window/Trace/pool/close/callbackfacts/errors | mockJDK HttpClient/controlledIAM，不开listener或真crypto/PS | tool_audit/root |
| TEST-004 compatibility | tianji-service | §8 exact8类 | 现ToolApprovalBinding/codec/HttpProcessClient/PSApprovalVerificationService/FixedSTART回归 | 老源码/协议不改，不声称原USER角色或连续撤销得到新实现 | root |
| TEST-005 static/docs | workspace root/service | strict/diffcheck/精确9paths/23SQLbytecompare/readonlylibs/独审 | v5/trace/17MC10Rules/time/无helper-client-codec变更 | untracked no-index/cached尾空白，no repo harness | root/doc |

Strict：`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-11-13-fixed-tool-approval-verification.md --strict`。new3tests按scope创建，既有5类真实path已核；无ApprovalConsumerRegistryTest，不加新test infra或PG/runtime selector，不自动启动任何项目/DB/collector/browser。

## 9. Migration, Compatibility, Rollout, and Rollback
全部23SQL immutable，无新版本/DDL应用；V13 f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3、V14 88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a保持。privatecodec/SDK/PS/helper/oldtransports/Stores/Recorders/config/权限无改；新library未注册/启用nativeAA/consume或workflow。binary可回退，现SQL/approvalfacts不删改/repair/backfill，不自动START/retry。Agent generatorexcluded，不产CRUD/MP迁移/codegen日志或新依赖。SourceId由hostcanonicalregistry保证，constructor不能自动查全球alias或信任modelJSON；真实IAM/crypto/PS/PG运行验收后续。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §11.3/13.1 | Step 1 | ARport/Test | TEST-001/002/003/004 | fullaction/AA START/owner/runlease/deadline/receipt结构，7fieldObservation/sourceNamespace与原codec同标签；constructor非认证 |
| PLAN-REQ-002 | §11.3/13.2 | Step 1 | Bindings/Test | TEST-001/002/003/004 | finiteexactorigin/def/ref/risk/output/approvalpolicy/selfSERVICE；approvalbackend与businessSTART bindingSHA分离 |
| PLAN-REQ-003 | §13.1/13.4 | Step 1 | ASVerifier/Test/旧SDK回归 | TEST-001/002/003/004 | 共享credentialhelper/VERIFY_SCOPE ONLY/samebearer/consumer exact、单SDK strict18 verifyHTTP/no release GET/false或mismatch安全拒 |
| PLAN-REQ-004 | §7/13.4 | Step 1 | ASVerifier/Test | TEST-001/002/003/004 | onewallmono/noqueue/Trace/close清token/拒latecallback；callback可已commit，post失效仅抑制return不rollback/retry；不承诺持续撤销 |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | all/tests/README/docs | TEST-004/005 | 9paths/23SQL immutable/无consume START cfg SDK codec改动，三组件有效RED后8类回归；全GoalPartial |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | businessSTART backend与approvalsource hash错误耦合 | 审批workflow由ToolApprovalBinding固定，Backend12独立，禁止descriptor businessSHA比较/fakeapprovalHash | root | Closed，反证测试 |
| DEC-002 | 保存projection或constructor冒当前权限 | callbackactualSERVICE/PSguard+newHTTP/requireApproved；无consume/START/currentrequesterPDP实现 | root | Closed，metadata非auth与source-consumer区分 |
| CLAR-002 | 最终caller来源检查与复用worker事务漏检 | Future.get后再current，helper/current前TXguard；SDKinvalid过期与本地stale分相位 | root/独审 | Approved；反证有效RED后两小fix，无新API/路径/预算 |
| DEC-003 | 短proof时间被加伪下界/预算reset | 原DTO<=30s/verified<=now/expiryfuture，无verified>=callstart，ONEwallmono含callback | root | Closed，边界/latecallback测试 |
| RISK-001 | callback已commit后postcheck/timeout失败 | 只抑制返回，不rollback/releaseapproval/授权STARTretry；facts由futureconsumer独立管理 | root | Tracked，partialfailure测试与README |
| RISK-002 | localcurrent被误称连续撤销/approvercurrentRole | helper只localkey+ACTIVEtrust；PS请求时SERVICEguard，18DTO无approverIDs/authVersion | root | Tracked，No新增监听/introspection/签名proof |
| RISK-003 | sourceId别名/全平台未完成 | hostcanonicalregistration一致性operator负责；AA atomicconsume/native/MCP/真实环境后续 | 用户/root | Tracked，全GoalPartial，不自动运行 |

## 12. Review and Acceptance
root合同1–4/9paths/fields/error/time/source与8selector均闭合，当前无待决协议/依赖。以下PASS仅计划可执行性，Review沿原exact授权，不伪Accepted；首次READY冻结，root全文/strict/resources后才sourceGO。执行evidence先PENDING，actualRED/GREEN/手工规则/Git按真实结果填，不声称运行或全Goal完成。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR application port/AS技术adapter/contract SDK，9paths | 保当前结构/JDBC/Flyway，Agent excluded；无认证/状态/transport引擎新层 | None |
| MC-REUSE-001 | Applicable | PASS | ToolApprovalBinding/Query/DTO/digest、sharedCredentialVerifier/actualHttpProcessClient | 精确复用现协议/身份校验，无新Sourceintrospection/OBO/二parser | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/TraceContext/SpringTX/SDK/IAM classes | 不加依赖/构建脚本/运行设施 | None |
| MC-NAME-001 | Applicable | PASS | FixedToolApprovalVerifier/ASBindings/ASVerifier与typedRequest/Observation/Backend/BindingVO | 技术角色明确，records不可变，values脱敏 | None |
| MC-VALID-001 | Applicable | PASS | AA START/fullaction、consumer/source/workflow、exactURI/currentkeytrust/SDK18echo/短窗 | 构造/hash不是认证；实际helper与新HTTP/currentPSguard才交callback，不新PDP证明 | None |
| MC-MODEL-001 | Applicable | PASS | 6fieldRequest/7fieldObservation与12/9fieldhost profiles | 无body/Bearer/client/operation逃逸，无新entity/SQL/state/软删除模型 | None |
| MC-CONVERT-001 | Not applicable | N/A | 复用typedQuery/DTO/Binding与本域Observation | 无PO/entity/通用DTO转换/Mapper；不改privatecodec/公开JSON | 沿现模型边界 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志/Service，safe kind/脱敏值/固定callback异常 | 不露URI/privatebody/token/cause/observer消息 | 无日志注入变化 |
| MC-BEAN-001 | Not applicable | N/A | 手工constructor显式库，无SpringBean/config/DI新注册 | 无业务Service/Lombok伪注入，SDK/callerHTTP外部owner | 现技术library风格 |
| MC-UTIL-001 | Applicable | PASS | JDK URI/Duration/Atomic与现digest/credentialhelper/Trace | 无新通用工具/框架，pool按现bounded模式owned，不改helper | None |
| MC-JSON-001 | Applicable | PASS | existingSDK strict18fields/原codec与sourceNamespace标签 | 不加JSONparser/字段/ordinal或改18field wire；namespace非签名 | None |
| MC-TIME-001 | Applicable | PASS | 原entry Clock/mono/req-run-backend min，helper/PS expiry与DTO<=30s | no reset/verifiedAt>=start伪下界；callback后expiry/key/trust失败不抹已提交facts | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/YAML/flags/Bean/权限配置变化 | 仅explicitlibrary，canonical sourceId/operator别名一致性非自动注册 | 无配置面 |
| MC-PATTERN-001 | Applicable | PASS | existingcallback Adapter/固定profile registry与bounded transport风格 | 实际source验证变化点复用既有，无新认证/State/工作流engine | None |
| MC-SCOPE-001 | Applicable | PASS | 9paths=3prod3tests3docs，23SQL frozen | 无Store/Recorder/codec/SDK/PS/oldtransports/helper/consume/start/config/AA native接线 | None |
| MC-TEST-001 | Applicable | PASS | values/bindings/verifier三有效RED barriers，exact8类/actualSDK假JDKwire | 合法fixtures/错误分类，fakeJWTmanager非真crypto/真实PS/SQL验收 | actualcount执行后填evidence |
| MC-BLOCKER-001 | Applicable | PASS | root合同1–4/API/fields/error/source/time与实际路径8selector已核，baseline/hash | 无协议未决，root全文/strict/resources后才sourceGO | None |

### 12.6 Final verdict
PASS — Ready for user review
