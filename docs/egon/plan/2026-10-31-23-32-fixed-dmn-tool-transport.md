# T3-07 固定只读DMN工具的受验证单次传输

| Field | Value |
| --- | --- |
| Document | `2026-10-31-23-32-fixed-dmn-tool-transport.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 23:32 Asia/Shanghai` |
| Updated | `2026-10-31 23:32 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | 固定只读DMN后端/真实SDK传输与callback单次能力；无dispatch/native/配置启用 |
| Source Requirement | 用户按原plan继续未完成工作并分步提交 |
| Baseline Revision | `main@7345552c52806efd095b8db34ee3b1c5ec611c72`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本轮明确授权exact剩余工作，原文无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `60ae80ef1af353c5f239cecfd1ffac324097ffacde2ccafac225cb9ff8a1b405` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [调用准备/预算库](2026-10-31-22-01-tool-invocation-preparation.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

原T3-07先交付只读Process/DMN工具。本切片以deployment固定的DMN release/model/schema和独立真实SERVICE身份提供有界单次callback传输，复用现有OAuth/JWT验证与HttpProcessClient，不允许模型选择资源/地址/服务身份。没有直接evaluate Bean、native接线、dispatch状态写入、审批消费或配置开关；未来Recorder必须在callback中先保存nonce/结果。可信executor值构造不认证，真实验证发生在AS Adapter。

## 2. Target Spec and Effective Design

原蓝图§6/7/12/13、§19 T3-07为主设计。基线7345552已有prepare/原子预算和typed审计；本次只补固定只读DMN transport能力，不把它当执行治理已闭环。AR只声明JDK/domain callback port，AS复用既有身份Bean/SDK；C新增canonical常量，PS仅四个原常量改引用、literal/权限语义不变。Components、SQL/依赖/框架不动。

## 3. Effective Requirements and Acceptance

PLAN-REQ只追踪原设计已有要求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/T3-07固定只读工具 | host固定tenant/source/definition/tool/backend/release/model/schema，拒绝动态扩大 | boundedimmutable mappings、exact hash/schema、READ_ONLY/all inputs required、无循环digest | Bindings/Test |
| PLAN-REQ-002 | §13可信身份/最小scope | 下游实际身份须独立SERVICE，不能把Agent owner metadata当凭据 | exactOAuthrequest+same bearer JWT proof验证/转换、ACTIVE TENANT/key/trust前后核、固定两scope/source | AS transport/Test |
| PLAN-REQ-003 | §7/12/13单次/时限 | token/JWKS/release/callback/POST共享整体窗口，能力仅当前callback线程一次 | 无queue非继承有界executor、close/cancel/escaped/cross-thread/repeated/ambient TX失败；迟到不新发副作用 | port/transport/Test |
| PLAN-REQ-004 | §12/T3-07DMN语义与receipt | 固定release校验后最多一次evaluate，返回明确单命中/无命中，不猜第一行 | 0/0=NO_MATCH、1/1=typed MATCHED、其余非法；实际结构有效迟到Observation仍先回callback供记账 | port/SDK/Test |
| PLAN-REQ-005 | §6/17兼容/边界 | 旧参数hash/权限literal保持；验证不冒充真实网络/crypto/native闭环 | canonical常量/descriptor helper同bytes，旧store委托，14路径；Mock HttpClient非listener | C/PS/AR旧调用者及README/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先最小type/签名声明供测试编译，三个组件各自真实RED再实现；canonical常量/参数hash提取必须保持既有值/输出。先AR callback contract与固定backend mapping，再AS真实SDK验证和one-shot传输，最后原调用者/权限回归及报告。主代理/API审阅已闭合安全异常及全部合同，strict通过后才GO生产。

### 4.2 Test-first strategy

AR test覆盖readonly/nonlegacy/request/参数hash/requestId/Observation约束。Bindings test覆盖max256/immutable/duplicate/source/definition/fullref/schema/hash边界及backend不含definition/tool content hash形成可构建顺序。AS测试使用真实HttpProcessClient但MOCK caller-owned JDK HttpClient.sendAsync的byte[]响应（无HTTP监听），mock现有IdP manager/converter，核same bearer、fixed scopes/source/key/trust/iat-exp独立、release/DMN请求、结果、多次/跨线程/逃逸、关闭/取消/窗口及迟到Observation顺序。不得将mock crypto当真实验证。

### 4.3 Sequential and parallel boundaries

唯一Step，三个owner按已锁定API各自RED→GREEN，可独立并行；最终14路径一个commit。Constants先供编译，原literal不改；不提前修改Recorder/dispatch/native/Components/config或新增依赖。

### 4.4 Commit boundaries

14条唯一路径构成固定传输能力一个逻辑提交；显式stage、不push/改历史。后续Recorder的持久nonce/receipt与native使用是独立步骤，不伪装本次已接线。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Callback/one-shot port | §12/13工具安全执行 | AR已有准备事实但没有可信下游身份传输边界 | 最小JDK/domain port，callback内持久化、无bearer DTO | Implement |
| 固定backend | T3-07固定工具版本/后端 | 现有descriptor/definition hash链 | stable backend digest→descriptor→definition→mapping，避免循环 | Implement |
| 身份/HTTP | §13当前SERVICE/SDK复用 | 现OAuth授权key、authenticate/proof/converter、HttpProcessClient | 只组合现有Bean/SDK，不造JWT/HTTP框架 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| canonical常量/hash及port | PLAN-REQ-003/004/005 | AR contract/旧hash与权限回归 | 最小声明、原C/domain | 兼容统一请求语义 | AS transport/未来Recorder | Step 1 |
| 固定mapping | PLAN-REQ-001 | binding positive/negative/hash tests | 原descriptor/definition/source types | 无模型选择的BackendVO | 真实SERVICE transport | Step 1 |
| verified callback传输 | PLAN-REQ-002/003/004 | mock SDK/JWT/key/time/callback tests | 原SDK/身份Bean/callerHttp | 受验证一次只读观察 | 未来持久执行治理 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AR/application/tool port、AS/integration/process、原C/PS | 保持轻契约/实现隔离 | C只常量、PS原值引用；AR无SDK身份实现依赖；AS真实Adapter | Step1；MC-ARCH-001、MC-REUSE-001 |
| Model/validation | 原immutableRequest/ToolSchema/Process DTO | metadata不授身份/权限 | nested immutable request/backend/result，闭合primitive schema，禁止token字段 | Step1；MC-VALID-001、MC-MODEL-001 |
| JSON/identity/lifecycle | 既有Jackson SDK/manager/proof/converter/oauth key | 不自造验证器/HTTP栈 | 真SDK typed调用、samebearer proof核验，private callback-local token引用finally清理 | Step1；MC-JSON-001 |
| Bean/pattern | 手工构造技术Adapter/原授权port | 无新Bean/config | Adapter+动态one-shot能力，close仅own executor，Caller Http/SDK/identity不关 | Step1；MC-BEAN-001、MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| SERVICE identity | IdpServiceOAuth2Client.authorizationKey/authorizeBounded、TenantIssuerAuthenticationManagerComponent、VerifiedPrincipalConverter、TrustedIssuerRegistry | 原组件已提供token/JWT验证/转换 | 足够；不改Components | 组合现有实例与exact expected resource | None | Step1；MC-REUSE-001、MC-DEP-001 |
| release/DMN HTTP | HttpProcessClient/get/evaluate、caller-owned HttpClient | 原SDK校验typed DTO且不允许redirect | 足够；mock sendAsync无listener | 同token supplier/剩余预算复用 | None | Step1；MC-JSON-001 |
| 参数hash | 原Invocation Request.parametersSha256 | 原既有namespace/排序primitive hash | 提取到descriptor不变bytes | 原Store委托，port复用 | None | Step1；MC-REUSE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Transport/Bindings、Request/BackendVO/Observation | 声明→tests→实现 | 明确port/技术Adapter/不可变值职责 | 编译/命名审阅 | Step1 | PASS |
| Rule 2 | §12/13 | 原schema/SDK/proof与callback边界 | 三test/Adapter | fullsource/binding/schema/tokenproof/时限/动态scope验证，无新HTTP DTO入口 | 正反合同测试 | Step1 | PASS |
| Rule 3 | §13 | nested immutable values和集合 | port/Bindings | defensive copy，token不入字段，非实体/Converter | 构造/immutable/escape测试 | Step1 | PASS |
| Rule 4 | §6 | manual技术协议Adapter，无配置新增 | 新库 | 非新增业务Bean/DI，private token生命周期 | source审阅 | Step1 | N/A：无业务Bean面 |
| Rule 5 | §6 | JDK/原SDK/现有组件 | 全代码 | 不引验证/HTTP/线程新框架 | imports/POM | Step1 | PASS |
| Rule 6 | §12/13 | 既有Jackson HttpProcessClient | Adapter/tests | typed SDK结果/请求；无自制JSON协议/日志正文 | mock sendAsync/旧合同 | Step1 | PASS |
| Rule 7 | §6 | 不改配置/profile/flags | 文档 | 不自动启能力 | diff | Step1 | N/A：配置不变 |
| Rule 9 | §12/13 | port依赖倒置/真实身份SDK变化点 | Adapter/oneShot | Adapter+有界动态能力，不造第二执行/认证框架 | 生命周期/once测试 | Step1 | PASS |
| Rule 10 | §7/13 | Instant/Clock/mono/OAuth/JWT | port/Adapter | 单窗口，iat/exp不强等，callback有效截止取min，Observation后不掩盖已发生响应 | TEST-002 | Step1 | PASS |
| Rule 11 | §6 | C/PS/AR/AS原依赖方向、固定V13 | 14路径 | 不改SQL/Components/框架/native/审批/dispatch | diff/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | request/run deadline、OAuth/JWT expiry、VerifiedExecutor expiry | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时间 | Not applicable: 无UI | 有效callback窗口取min(token/JWT/request/window)，iat/exp无需彼此完全相等，前后trust/key/时间复核 | Clock.systemUTC/注入Clock，既有SDK类型不改 | 不把构造值当签名验证，也不延长run deadline | Step 1；TEST-002：短窗/不同iat-exp/回拨/迟到 |
| TIME-002 | entry timeout、token/JWKS/release/callback/POST | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无展示 | 单wall+mono窗口min(request/run/profile timeout100ms..30s)，outerFuture限制JWKS，操作前active gate | System.nanoTime/注入LongSupplier；不按阶段重置 | 无新auth框架/每call client或线程平台 | Step 1；TEST-002：cancel/close/capacity/late-before-effect |
| TIME-003 | DecisionObservation submittedAt/receivedAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对观测 | Not applicable: 无UI | 结构有效HTTP响应即便迟到/来源变仍先回callback，未来Recorder先commit后outer抑制输出 | submittedAt原SDK值、receivedAt本地Instant；不猜日期 | no business receipt/当前不入DB | Step 1；TEST-002：late response/recording order |

## 5. Change File Tree

| Operation | Repository-relative path | Step |
| --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/DecisionClient.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessReleaseClient.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolDescriptor.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransport.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransportTest.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindings.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindingsTest.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java` | Step 1 |
| MODIFY | `README.md` | Step 1 |
| CREATE | `docs/egon/plan/2026-10-31-23-32-fixed-dmn-tool-transport.md` | Step 1 |
| CREATE | `docs/egon/evidence/2026-10-31-fixed-dmn-tool-transport.md` | Step 1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Baseline7345552，只有本文允许写作；新功能先声明供测试编译再组件真RED。已核所有selector真实存在：ReadOnlyDecisionToolTransportTest（本步新建）、TianjiFixedDecisionToolBindingsTest（新建）、TianjiFixedDecisionToolTransportTest（新建）、ToolInvocationStoreTest、ToolExecutionContractTest、ToolExecutionGrantTest、ToolExecutionJsonCodecTest、C的OrchestrationContractTest、PS的ProcessPermissionIntegrationTest/ProcessControllerTest。JDK25/既有SDK/身份Bean足够，POM不改。V13固定`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`。

Port精确签名为`<T> T withVerifiedCall(Request, Callback<T>)`。Request(run,lease,descriptor,args,UUID invocationId,Instant deadlineAt)只READ_ONLY/nonLEGACY、lease.runId匹配、args固定schema归一、deadline<=run；参数SHA委托descriptor。downstreamRequestId是canonical UUID String，length-prefixed namespace `TIANJI_TOOL_DMN_REQUEST_V1`+owner.identityFingerprint/runId/invocationUUID/toolkey/version/fullref；correlationId=runId，避免不同run复用host UUID碰撞。

VerifiedExecutor(actor,expiresAt)只不可变nonLEGACY SERVICE值，构造不认证。Callback<T>.apply(VerifiedExecutor,OneShotDecision)；OneShotDecision.evaluate返回DecisionObservation(Status status,String decisionExecutionId,Instant submittedAt,Instant receivedAt,Map<String,Object>output,String matchedRuleId)，Status是INTERFACE层MATCHED/NO_MATCH。executionId为canonical UUID文本；outputs闭合bounded canonical primitive不可变，adapter再按实际descriptor.output验证matched row；不要求远端DB submittedAt<=本地receivedAt。

嵌套FailureKind固定九项CREDENTIAL_UNAVAILABLE/RELEASE_UNAVAILABLE/RELEASE_CHANGED/OPERATION_DENIED/OPERATION_REJECTED/OPERATION_RESULT_UNKNOWN/OPERATION_INVALID_RESPONSE/CANCELLED/DEADLINE_EXCEEDED。final Failure extends RuntimeException，kind()，`super(kind.name(),null)`固定enum-only消息且不可附cause，不含body/status/URI/token。无queue拒绝为CREDENTIAL_UNAVAILABLE、closed/interrupted为CANCELLED、elapsed为DEADLINE_EXCEEDED；凭据/验证失败与release不可用/内容变化区分；POST401/403 denied，其他确定4xx rejected（408/429为unknown），timeout/I/O/5xx unknown，malformed2xx invalid。NO_MATCH不是异常。

BackendVO字段固定tenantId、processIssuer、processBaseUri、processResourceUri、registrationId、expectedClientId、expectedAppId、expectedSourceBiz、expectedSourceApp、expectedEnvironment、releaseVersion、manifestSha256、decisionKey、decisionModelSha256、Map inputSchema、Duration timeout100ms..30s。digest域`TIANJI_TOOL_DMN_BACKEND_V1`只这些稳定字段及canonical schema，不含definition/tool/run refs、credentialVersion/tokenId/exp/trustVersion；无requiredInputs冗余字段。Backend→descriptor→definition→mapping可按此顺序构建，不得循环。

Bindings构造(Collection<ToolDescriptor>,Collection<BindingVO>)，boundedimmutable max256。BindingVO(tenantId,ownerIssuerFingerprint,principalType,definitionKey,definitionVersion,definitionContentSha256,DefinitionResourceRef tool,BackendVO backend)，ResolvedBindingVO(descriptor,binding)供run/tool精确选择；拒重复origin/type/defkeyver/toolkeyver槽，即使hash不同；同origin/type/definition key+version下多个tool mappings也必须共享唯一definition SHA，slot不含hash以拒绝漂移。descriptor必须READ_ONLY、完整registeredRef、backend digest等于policy.bindingSha、policy.executionResourceKey等于DecisionClient.EXECUTE_RESOURCE_KEY，input字段/类型与backend完全相等且全部required。所有URI必须ASCII HTTPS、无query/userinfo/fragment/normalize漂移，issuer/base最长2048，processResourceUri按IdpServiceTokenRequest最长512；Backend equals/hashCode使用URI ASCII文本。无redirect，模型不选任何资源/服务字段。

AS构造(bindings,oauthClient,tenantIssuerAuthenticationManagerComponent,verifiedPrincipalConverter,trustedIssuerRegistry,callerOwnedHttpClient,int concurrency1..8)，package版appendClock/LongSupplier。自有SynchronousQueue非继承virtualexecutor，跨worker只用既有TraceContext.capture/open/close，不传SecurityContext或USER凭据，MDC不作权限来源；只关闭自有executor/attempts，不关caller HTTP/SDK/身份Bean。OAuth实际API为authorizationKey(request)（捕获前后key）及authorizeBounded；使用exact固定IdpServiceTokenRequest TENANT/two scopes。manager.authenticate验证同一个bearer和固定PSresource，proof.validate且service类型、proof.jwt.tokenValue等于实际OAuth bearer，再经原converter生成principal，核exactissuer/tenant/client/app/resource/TENANT/source3/scopes。iat/exp不强等，effective executor expiry取min(token/principal/operation window/request deadline)。ACTIVE TENANT/key/trust以及URIASCII前后精确，秘密轮换改变key不改backend hash。

每请求single wall+mono窗口覆盖token/JWKS/release/callback/POST；outerFuture限制JWKS，不能另建验证框架。private request-local AtomicReference bearer，固定无I/O supplier只返回同token，worker/caller finally均清空，scope=false。token publish与active/clear用短同步锁防止忽略中断的迟到OAuth重新填token；timeout/cancel先撤active/clear bearer，再future.cancel；忽略中断也不能迟到发操作。OneShot只callback owner thread/dynamic scope/once/noDBtransaction/current deadline/key/trust允许；逃逸/跨线程/重复/关闭/取消失败。

callback前用真实HttpProcessClient GET固定release，核manifest、恰好一matching DMN/key/modelHash/inputschema。POST固定命令携derivedrequestId/run correlation/normalizedargs。0outputs/0rules=NO_MATCH；1/1=按descriptor output schema验证MATCHED；其他不猜首行。结构有效POST响应即使迟到或source变仍先返回Observation给callback（未来Recorder必须在callback内commit），outer只在callback结束后做postkey/trust/deadline并抑制model结果；当前不实现Recorder、dispatch nonce或native consumer。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 提供固定DMN后端的真实SERVICE受验证单次callback传输

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交7345552及现有domain/typed合同、OAuth/JWT组件、HttpProcessClient；无Components修改。
- Baseline state: 只有invocation准备事实，尚无固定DMN transport；C/PS literals分散，参数hash只在旧Request。
- Observable outcome: 可信固定SERVICE验证后callback获得一次固定DMN操作，不能借DTO/逃逸/迟到扩大能力；结果明确单匹配/无匹配。
- End state: 未注册技术port/Bindings/Adapter与受控SDK测试可用；无Recorder/dispatch/native/config/权限激活。
- Test-first gate: Required — 新types/signature scaffold只供编译，三个新test各自真RED后实现；常量同值引用/既有hash提取是机械兼容，原回归必须保持，不伪造新行为RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/DecisionClient.java`

- Purpose: Publish canonical existing decision scope/resource values without wire changes.
- Symbols: EXECUTE_SCOPE, EXECUTE_RESOURCE_KEY.
- Repository evidence: Existing PS DECISION_SCOPE/DECISION_API literals.
- Dependencies and consumers: C remains lightweight, no framework/SDK implementation dependency.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: String EXECUTE_SCOPE="tianji:decision:execute"; EXECUTE_RESOURCE_KEY="tianji.api.process.decision.evaluate".
- Input/output and state mapping: Literal compile-time constants only; evaluate signature unchanged.
- Error and edge behavior: No new permissions, scopes, role grants or endpoint.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Keep existing DecisionClient.evaluate(EvaluateDecisionCommand) untouched.
Add exact compile-time EXECUTE_SCOPE and EXECUTE_RESOURCE_KEY constants with the existing PS literal values.
Consume constants in the later PS reference-only edit and fixed Adapter metadata; do not add protocol fields or runtime logic.
```

- Verification contribution: TEST-003 C/PS existing permission/SDK contract regression.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 2 — `MODIFY stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessReleaseClient.java`

- Purpose: Publish canonical existing release-read scope/resource values.
- Symbols: READ_SCOPE, READ_RESOURCE_KEY.
- Repository evidence: Existing PS RELEASE_READ_SCOPE/RELEASE_GET_API literals.
- Dependencies and consumers: C library only; no implementation imports.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: READ_SCOPE="tianji:process:release:read"; READ_RESOURCE_KEY="tianji.api.process.release.get".
- Input/output and state mapping: publish/get signatures and all wire behavior unchanged.
- Error and edge behavior: No new permission resource/role or deployment activation.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Keep ProcessReleaseClient publish/get signatures and DTOs unchanged.
Add the exact existing release-read scope/resource literal constants as canonical contract values.
Use them later for reference-only PS constants and the fixed two-scope token request; no new capability is registered.
```

- Verification contribution: TEST-003 original orchestration and Process permission/controller tests.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Make four existing permission constants reference C while preserving every value.
- Symbols: RELEASE_GET_API, DECISION_API, RELEASE_READ_SCOPE, DECISION_SCOPE only.
- Repository evidence: Current existing literal values and permission annotations.
- Dependencies and consumers: New C constants, already a PS dependency.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Replace only RHS with ProcessReleaseClient.READ_RESOURCE_KEY/READ_SCOPE and DecisionClient.EXECUTE_RESOURCE_KEY/EXECUTE_SCOPE.
- Input/output and state mapping: Compile-time value equality preserves manifests, annotations, resource catalog and handlers.
- Error and edge behavior: No other PS constant/annotation/controller change or permission registration.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
RELEASE_GET_API = ProcessReleaseClient.READ_RESOURCE_KEY; RELEASE_READ_SCOPE = ProcessReleaseClient.READ_SCOPE;
DECISION_API = DecisionClient.EXECUTE_RESOURCE_KEY; DECISION_SCOPE = DecisionClient.EXECUTE_SCOPE;
Keep all previous literal values, permission annotations and remaining constants unchanged; add only necessary imports.
```

- Verification contribution: TEST-003 ProcessPermissionIntegrationTest/ProcessControllerTest; exact literal review.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolDescriptor.java`

- Purpose: Extract the existing canonical invocation parameter fingerprint into its single shared owner.
- Symbols: public parametersSha256(Map<String,Object>).
- Repository evidence: Current ToolInvocationStore.PreparationRequestVO uses the existing same hash namespace.
- Dependencies and consumers: Existing input.values and OrchestrationRequestDigests only.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Hash fields TIANJI_TOOL_INVOCATION_PARAMETERS_V1,key,version,reference.contentSha256 plus normalized input values.
- Input/output and state mapping: Same existing byte output for valid old callers; no action/capability namespace change.
- Error and edge behavior: Do not invent a new digest or include owner/run/backend in this existing parameter namespace.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
public String parametersSha256(Map<String,Object> values) {
    normalized = input.values(values);
    return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_INVOCATION_PARAMETERS_V1",key,Long.toString(version),reference().contentSha256()),normalized);
}
```

- Verification contribution: TEST-001 new port hash and TEST-003 original invocation/Grant/Contract regression.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`

- Purpose: Delegate the old parameter calculation without changing preparation behavior.
- Symbols: PreparationRequestVO.parametersSha256 only.
- Repository evidence: Existing method body hashes normalized arguments with the exact previous namespace.
- Dependencies and consumers: New descriptor helper; original fields, validation and transaction behavior unchanged.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: return descriptor.parametersSha256(arguments).
- Input/output and state mapping: No output/hash difference or new invocation semantic.
- Error and edge behavior: Do not refactor budget/lease/row parser or request validation.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
PreparationRequestVO.parametersSha256(): delegate to descriptor.parametersSha256(arguments).
Remove only imports made unused by this extraction; retain original normalized arguments and every other method.
Run original ToolInvocationStoreTest to preserve existing parameter/action distinction and bytes/behavior.
```

- Verification contribution: TEST-003 old invocation tests unchanged.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransportTest.java`

- Purpose: Define JDK/domain port request, metadata, failure and observation invariants.
- Symbols: Request normalization/readonly/source/deadline, derived IDs/hash, VerifiedExecutor, Status/Observation/Failure contracts.
- Repository evidence: Existing domain immutable values and original C decision UUID/primitive conventions.
- Dependencies and consumers: Minimal port declarations and descriptor helper; no SDK/HTTP dependency.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Exact Request/Callback/OneShot/Observation/Failure signatures in section6.
- Input/output and state mapping: Different runs with same hostUUID derive distinct downstreamID; readonly/nonlegacy/lease/deadline/schema validated; MATCHED/NO_MATCH groups/primitive outputs immutable.
- Error and edge behavior: Constructed executor does not authenticate; do not impose remote submitted<=local received; Failure fixed enum-only/no attachable cause/private data.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void readonly_request_and_observation_are_bounded_values_not_authority() {
    assertNormalizedParametersUseExistingDescriptorHashAndDerivedRequestIdSeparatesRuns();
    assertReadOnlyNonlegacyLeaseAndDeadlineConstraintsAndImmutablePrimitiveOutputs();
    acceptRemoteSubmittedAtAfterLocalReceivedAtWithoutInventedClockOrdering();
    assertStatusGroupsCanonicalExecutionUuidAndSafeFailureWithoutCauseOrPrivateFields();
}
```

- Verification contribution: TEST-001 real port RED/GREEN, TEST-002 time/failure/value privacy.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindingsTest.java`

- Purpose: Define immutable fixed backend selection and noncircular fingerprints.
- Symbols: Backend digest, mapping/ref/schema/readonly/limits/duplicate and URI tests.
- Repository evidence: Existing ToolRegistry/Descriptor/definition selection, actual ProcessModel input schema.
- Dependencies and consumers: Minimal bindings types, public C constants; no network.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Exact BackendVO/BindingVO/ResolvedBindingVO/constructor in section6.
- Input/output and state mapping: Backend digest computed before descriptor, then definition and exact mapping; no definition/tool/run/credential version in digest.
- Error and edge behavior: Wrong tenant/origin/type/definition/fullref/hash/inputrequired/resourceKey/URI reject; duplicate slot/hash drift reject; no model-selected backend.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void stable_backend_precedes_descriptor_definition_and_exact_mapping() {
    backend = validFixedProfile(); digest = backend.digest(); assertNotNull(digest);
    buildDescriptorThenDefinitionThenMappingWithoutCircularHash(); assertExactRequestedSourceAndSchemaSelection();
    assertCredentialRotationDoesNotChangeStableBackendAndWrongRefsOrPartialInputsCannotResolve();
    assertBoundedImmutableMapsDuplicateSlotsAndHttpsNoRedirectProfileConstraints();
}
```

- Verification contribution: TEST-001 mapping true RED/GREEN and stable-field hash boundaries.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java`

- Purpose: Define actual SDK request/proof/one-shot/time/response semantics without a listener.
- Symbols: Mock HttpClient.sendAsync byte[] responses; controlled existing manager/converter/OAuth; release and decision/failure/lifecycle tests.
- Repository evidence: Existing HttpProcessClient protocol and real IdP service types/factories.
- Dependencies and consumers: Minimal transport constructor/signatures and actual C DTOs; no HTTP server/DB/crypto verification.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Constructor(bindings,oauth,manager,converter,issuers,callerHttp,concurrency); package appendClock/LongSupplier.
- Input/output and state mapping: Exact two-scope/token/key/service source, same bearer proof, different token/JWT iat-exp accepted with effective min; fixed release then at most one evaluate.
- Error and edge behavior: Escaped/crossThread/repeated/ambientTX/close/cancel/timeout gate fails before effects; valid late response reaches callback before outer suppresses model value; map safe Failure kinds with no body/cause.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
@Test void verified_service_and_single_dynamic_operation_use_one_overall_budget() {
    mockCallerHttpSendAsyncOnlyNoListener(); assertExactTokenRequestAndSameBearerJwtProofAndFullSignedSource();
    assertFixedReleaseManifestDmnSchemaThenDerivedCommandUsesSameTokenAndRemainingBudget();
    assertSingleMatchOrNoMatchAndRejectMultipleRowsWithoutGuessing();
    assertKeyTrustRotationCancelCloseEscapeCrossThreadRepeatedAndAmbientTxCannotOpenAnotherOperation();
    assertValidLateObservationReturnsToCallbackBeforeOuterPostcheckSuppressesValue();
    assertSafeErrorClassificationAndSharedResourcesNotClosed();
}
```

- Verification contribution: TEST-001/002 real adapter RED/GREEN with deterministic fake SDK/identity, no runtime claims.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 9 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransport.java`

- Purpose: Implement the minimal domain callback port and immutable request/observation/error values.
- Symbols: Generic withVerifiedCall; nested Request, VerifiedExecutor, Callback<T>, OneShotDecision, interface Status, DecisionObservation, FailureKind/Failure.
- Repository evidence: Existing immutable run/lease/schema and C canonical UUID-text conventions.
- Dependencies and consumers: Only JDK/domain types; no bearer, IdP, HttpClient, Flowable/ADK types in AR port.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Exact section6 signatures; decisionExecutionId String canonicalUUID, invocationId javaUUID; Status at interface level.
- Input/output and state mapping: Normalize readonly args; parameters helper; UUID downstream hash domain+owner/run/inv/toolfullref, correlation runId; executor SERVICE/nonlegacy values; bounded primitive immutable observation.
- Error and edge behavior: No actual authority from constructors; no remote/local timestamp ordering; safe Failure super(kind.name(),null) exposes only kind with no attachable cause.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
interface ReadOnlyDecisionToolTransport { <T>T withVerifiedCall(Request request,Callback<T> callback); }
Request: validate readonly/nonlegacy/leaseID/deadline<=run and normalize args; parametersSha256 delegates descriptor;
downstreamRequestId = canonicalUUID(first128(lengthPrefixedHash("TIANJI_TOOL_DMN_REQUEST_V1",owner.identityFingerprint,runId,invUUID,toolkey,version,fullref))); correlationId=runId;
Callback.apply(VerifiedExecutor,OneShotDecision); OneShotDecision.evaluate()->DecisionObservation(Status,...);
validate MATCHED/NO_MATCH bounded primitive groups and canonical UUID string, preserve both Instants without cross-clock ordering;
Failure(kind): fixedenum-only message with super(message,null), no transport secrets or attached cause;
```

- Verification contribution: TEST-001 port GREEN, metadata/normalization/error behavior explicit.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 10 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindings.java`

- Purpose: Implement fixed HTTPS backend profiles with stable digest and exact source/tool selection.
- Symbols: BackendVO.digest, immutable BindingVO/ResolvedBindingVO, constructor and requested run/tool resolution.
- Repository evidence: Existing descriptor fullhash and deployment matching patterns; C canonical constants/model schema.
- Dependencies and consumers: Section6 field list and registered ToolDescriptors only, no credentials inside profile.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Collection constructor max256, no duplicate source/type/defkeyver/toolkeyver slot even with hash drift.
- Input/output and state mapping: Digest stable backend fields+canonical input schema under TIANJI_TOOL_DMN_BACKEND_V1; then descriptor→definition→mapping; exact readonly/backendSHA/resourceKey/inputtypes+allrequired.
- Error and edge behavior: Exclude definition/tool/run refs and credential/trust versions from backend digest; no fake authentication from mapping, no model overrides or circular hash.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
BackendVO: validate bounded tenant/source/HTTPSresource/base/registration/expectedservice/release/model/schema/timeout100ms..30s;
digest = lengthPrefixedHash(TIANJI_TOOL_DMN_BACKEND_V1,stableBackendFields,canonicalInputSchema), excluding every definition/tool/run/credential-version reference;
constructor: copy registry/mappings(max256); require exact registered fullref, readonly, backend digest==policy.bindingSHA, Cdecision resource and exact all-required input schema;
require all tool mappings for the same source/type/definition key+version share one definitionSHA even across distinct tool slots;
resolve requestedRun/tool: require unique exact tenant/origin/type/definition key/version/hash/ref mapping and return immutable descriptor/binding;
```

- Verification contribution: TEST-001 fixed binding GREEN; no runtime selection or credential validation claim.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 11 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java`

- Purpose: Implement existing real identity/SDK composition with bounded dynamic one-shot call scope.
- Symbols: withVerifiedCall, owned executor/gate, fixed token supplier, release verification/evaluate, safe Failure mapping and close.
- Repository evidence: Existing OAuth.authorizationKey/authorizeBounded, manager.authenticate/proof/converter, TrustedIssuerRegistry and HttpProcessClient.
- Dependencies and consumers: Caller-owned HttpClient Redirect.NEVER; existing identity objects; no new framework or Bean.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: One submit/entry window min(request/run/backendtimeout), concurrency1..8 noqueue/noninheritvirtual; close only own executor/attempts.
- Input/output and state mapping: Capture exact key+ACTIVE TENANT trust; authorizeBounded remaining; authenticate exact token fixedPSresource, proof.validate/service/same jwt tokenValue; converter+full source/scopes; effective expiry=minOAuth/JWT/window/request.
- Error and edge behavior: Finally clear private request-local bearer/active; revoke before cancel; operation only owner callback thread/once/noTX/preHTTP current gate/key/trust. Release comparefixed manifest/exactoneDMN/hash/schema. POST0/0 noMatch,1/1 matchedschema, else invalid. Valid late/sourcechanged response must reach callback Observation before outerpostcheck suppresses returned value.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
withVerifiedCall: create one wall+mono Window and private active/token references; capture existing TraceContext; submit once on bounded noninherit executor, open/close TraceContext around worker only; never inherit SecurityContext or USER credentials;
validate fixed mapping; capture OAuth authorizationKey and ACTIVE TENANT trust; obtain token within remaining budget; manager.authenticate(sameBearer,fixedPSresource); validate service proof/token equality and converter principal exactsource/scopes;
verify fixedrelease via real HttpProcessClient with same fixed supplier and remainingbudget; check manifest/exactDMN/model/schema;
invoke callback on ownerworker with VerifiedExecutor(minexpiry) and oneShot whose evaluate checks dynamic scope/once/noTX/gate/window/key/trust before POST;
POST fixed command with derived requestId/run correlation/normalizedargs; return structurally valid single/no-match Observation to callback even if source/time changed after response;
after callback require key/trust/deadline/current gate then return model value; caller timeout/close revokes active+clears token before future.cancel, both finally clear;
map credential/release/HTTP failure kinds exactly section6, never attach cause/body/URI/token; close only own executor, not sharedHTTP/SDK/identity;
```

- Verification contribution: TEST-001/002 SDK/identity/lifecycle/response GREEN; not real network or crypto acceptance.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 12 — `MODIFY README.md`

- Purpose: Record fixed readonly capability and its explicit unconnected receipt boundary.
- Symbols: Current T3-07 progress/testing notes.
- Repository evidence: README differentiates preparation from dispatch/native and real runtime proof.
- Dependencies and consumers: Final implemented code/tests only.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: No config/API or automatic activation; explain actual SDK/identity composition and bounded callback.
- Input/output and state mapping: Stable backend→descriptor→definition mapping, realSERVICE bearerproof, oneShot single/no-match and safe failure values.
- Error and edge behavior: Future Recorder must commit inside callback before outer postchecks; no direct evaluateBean/native/dispatch/approval/realcrypto claim.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Append fixed readonly DMN profile, stable noncircular digest and actual OAuth/JWT/SDK callback composition facts.
Explain one shared token/remaining-budget gate, single-match/no-match semantics and callback-local receipt-before-outer-check requirement.
Retain no Recorder/native/dispatch/config/permission activation and unexecuted real IAM/crypto/network acceptance; tests use mocked sendAsync, not listeners.
```

- Verification contribution: TEST-003 source/README scope fidelity.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 13 — `CREATE docs/egon/evidence/2026-10-31-fixed-dmn-tool-transport.md`

- Purpose: Record actual independent RED/GREEN, exact constants/hash compatibility and runtime boundaries.
- Symbols: Baseline7345552,14paths, actual counts/exits,17MC/10Rules, five aliases/fullT3Partial.
- Repository evidence: Prior Step evidence and executing skill gates.
- Dependencies and consumers: Actual final code/test outputs/diff; no prefilling passes.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Pure evidence, no external protocol or privilege change.
- Input/output and state mapping: Trace fixed mapping/identity/key/trust/oneShot/window/lateObservation/failure and oldC/PS/AR regressions.
- Error and edge behavior: No mock validation promoted to crypto/network/runtime; no secrets or undeclared scripts/harness.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Record actual component RED/GREEN, final test selection/counts/exits and exact14path diff/constants/hash proof.
Evaluate17 MC and10 Literal Rules independently using source/test evidence and justified N/A.
Retain no listener/realcrypto/SQL/native/dispatch/Recorder acceptance; record owned executor/shared resources and temporary harness disposition.
Commit only after root final diff/stage gates, with fullT3-07Partial rather than platformPASS.
```

- Verification contribution: TEST-003 actual audit; no assumed runtime pass.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

#### File 14 — `CREATE docs/egon/plan/2026-10-31-23-32-fixed-dmn-tool-transport.md`

- Purpose: Lock and validate this exact executable Step boundary before production GO.
- Symbols: Metadata/baseline/effective Spec/API/file order/RED/gates/commit scope.
- Repository evidence: Original legacy authorization plus current repository-grounded Step and closed API review.
- Dependencies and consumers: All section6 signatures and14paths; no extra design/behavior.
- Why now: Minimal declarations may precede typed tests; new functionality completes only after owning component true RED. Mechanical constants/hash extraction retains old behavior and original regression gates.
- Contract/signature changes: Review Plan remains exact legacy authorization, not a fabricated Accepted Spec status.
- Input/output and state mapping: Structural strict validation plus actual root source/requirement review permits implementation GO.
- Error and edge behavior: Document is not proof of code/runtime; no silent new dependencies/permissions or partial implementation marked complete.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; lightweight port/value or manual technical Adapter, no new Bean/config/dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; immutable role values, exact identity/schema/lifetime validation, existing SDK/UTC, Adapter pattern and original architecture; separate N/A for untouched business Bean/config.

- Implementation pseudocode:

```text
Complete exact final APIs, fixed Failure contract, noncircular backend and current-time/callback observation rules.
Run the installed strict Plan validator on this file, resolve only document defects, inspect14unique paths and source constants.
Hand the verified Plan to root before any production functionality; implementation results later belong in the separate evidence report.
```

- Verification contribution: TEST-004 strict structure/source review; no runtime or implementation proof.
- After this file: Declared proof is ready for final focused verification; no native/dispatch capability is enabled.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ReadOnlyDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,TianjiFixedDecisionToolTransportTest,ToolInvocationStoreTest,ToolExecutionContractTest,ToolExecutionGrantTest,ToolExecutionJsonCodecTest,OrchestrationContractTest,ProcessPermissionIntegrationTest,ProcessControllerTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: new component real behavior RED then selected port/SDK/source/identity/lifetime and old contract/permission tests GREEN; counts/exits actual only, no listeners/crypto/DB/runtime starts.
- Failure returns to: owning fixture if invalid RED, exact port/Bindings/Adapter or mechanical constants/hash file for defects; no Components/framework/SQL/native/config scope expansion.
- Completion criteria: all5 aliases source/test evidence,14paths and original literals/hash bytes preserved;17MC/10Rules individually PASS or valid N/A, actual bearer/scope/key/time/once boundaries proven with controlled dependencies.
- Rollback: close only owned transport resources and stop consuming new unregistered port; explicit revert if needed, no schema/config/privilege migration or caller resource closure. Existing preparation/receipt facts remain, no retry permission implied.
- Commit paths: `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/DecisionClient.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessReleaseClient.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolDescriptor.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransport.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyDecisionToolTransportTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindings.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolBindingsTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransport.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedDecisionToolTransportTest.java`, `README.md`, `docs/egon/plan/2026-10-31-23-32-fixed-dmn-tool-transport.md`, `docs/egon/evidence/2026-10-31-fixed-dmn-tool-transport.md`
- Commit: `feat(agent): add verified fixed read-only DMN tool transport`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 source-reactor，先各新组件，再选定整组 | port/Bindings/真实SDK Adapter | 合法fixture真实RED后GREEN；实际数量写报告 | 所属test/实现 | 五别名；受控依赖 |
| TEST-002 安全/时限 | tianji-service | 同test的OAuth/JWT proof/key/trust/window/oneShot/lateObservation断言 | single window/同bearer/固定source/release/单操作 | 取消先clear再cancel、publish race不重填，逃逸/跨线程/多次不POST，latevalid先callback | Adapter/test | 无listener/真实crypto/DB |
| TEST-003 兼容/范围 | tianji-service | 原C OrchestrationContract、PS permission/controller、AR invocation/Grant/Contract/JsonCodec（新增descriptor方法/hash委托必须保持旧JSON/reference）；`git diff --check`/14paths/17MC/10Rule/V13 SHA | 四literal、原参数hash、所有原依赖边界 | 值/bytes/权限语义不变，无SQL/config/native/Components变化 | 所属机械改动 | 原权限未注册/激活/授角色 |
| TEST-004 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-23-32-fixed-dmn-tool-transport.md --strict` | 文档/追踪/文件顺序 | exit0 | 本文 | 结构不等于运行验收 |

Mock caller-owned JDK HttpClient.sendAsync返回byte[] HTTP响应，由实际HttpProcessClient解析/验证；不启动HTTP listener、应用或数据库。原IdP manager/converter受控，不宣称真实签名/JWKS网络验证通过。复用现有验证命令，无scripts/work harness；文档生成脚本结束删除。

## 9. Migration, Compatibility, Rollout, and Rollback

不新增/修改SQL、配置、权限manifest、依赖、Components或公开HTTP端点。C只增原值常量，PS只四项引用，旧权限scope/resource字面值保持；descriptor参数hash提取保留原namespace与bytes。AR port不含IdP/HTTP实现类型，AS手工构造Adapter无evaluate Bean或native运行consumer。

profile后端先digest→descriptor→definition→mapping，credential/trust轮换不改稳定hash，当前key/trust仍必须前后复核。close只关自有executor与per-call cleanup句柄/Futures，不关闭caller HttpClient、共享SDK/identity Bean；per-call token引用不是跨请求token cache。

当前没有Recorder的持久dispatch nonce/receipt。未来必须在withVerifiedCall callback内先记nonce、消费oneShot、提交有效Observation或失败事实再返回；outer postcheck可以抑制model value但不能先丢掉已结构有效的操作响应。本切片不声称这些记录/dispatch/native已实现。回退关闭新未注册transport/停止消费者并显式revert本提交，无schema/权限迁移或共享资源关闭；no retry权限隐含。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/T3-07 | Step 1 | FixedBindings/Test、descriptor/C | TEST-001/003 | 固定source/ref/无循环backend/hash/schema/readonly映射 |
| PLAN-REQ-002 | §13/T3-07 | Step 1 | FixedTransport/Test | TEST-001/002 | 原OAuth/JWT proof同bearer、服务字段/two scopes/key/trust/expiry |
| PLAN-REQ-003 | §7/12/13/T3-07 | Step 1 | AR port/Test、AS Adapter/Test | TEST-001/002 | singleWindow、动态ownerthread/once/noTX、cancel/clear/publish/close |
| PLAN-REQ-004 | §12/T3-07 | Step 1 | port/Adapter/SDK tests | TEST-001/002 | 固定release、单/无命中、latevalid先Observation、safe Failure |
| PLAN-REQ-005 | §6/17/T3-07 | Step 1 | C/PS/AR机械改动、全测试、README/plan/evidence | TEST-003/004 | 原值/hash兼容、14paths、无运行/权限/Components扩大 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | Callback/oneShot与safe Failure精确合同 | port/Adapter/tests | 主代理/API审阅最终确认，Status在interface，executionId String、Failure无cause | 主代理 | Closed：§6/7无未决签名 |
| DEC-002 | 后端digest无definition/tool循环、源/资源及scope有限 | Bindings/Test | 已锁stable16字段/输入schema、descriptor后再definition和mapping | 主代理/owner | Closed：不含凭据版本，URIASCII及512/2048上界 |
| DEC-003 | validLateObservation与outer阻断分离 | Adapter/未来Recorder | 主代理确认callback必须先拿到结构有效response，future receipt先commit | 主代理 | Closed：本次只transport能力，不伪称Recorder已交付 |
| RISK-001 | 实际crypto/PDP/HTTP/数据库未运行 | 后续运行验收 | 当前仅mock JDK sendAsync和现身份Bean受控响应，无listener | 用户/主代理 | Tracked：运行证据另行，不启动环境 |
| RISK-002 | 新port可用不等于完整工具执行 | 后续Recorder/native | 无dispatch/nonce/approval/配置consumer | 主代理 | Tracked：完整T3-07与平台继续Partial |

## 12. Review and Acceptance

本步骤以固定部署/真实SERVICE验证/原SDK提供只读DMN callback能力，保持用户禁止自动启动/GUI、旧SQL不可变、无新依赖/架构层和按逻辑提交约束。14条唯一路径及14个文件marker精确；最小声明后各组件真RED再实现，机械常量/hash改动以旧回归保证同值。原request、source、proof、资源生命周期和异常合同均已闭合，不要求上游/本地时钟或OAuth/JWT timestamps完全相同。实际结果/失败/未验收在报告逐行记录，不用mock冒充真实身份或运行通过。

### 12.5 Blocking Manual Check

以下为计划级可执行性结论，实施后按实际source/output逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | C常量/PS引用、AR port、AS实现的原边界 | 契约/实现隔离，无新模块/引擎/Components依赖反向 | None |
| MC-REUSE-001 | Applicable | PASS | 原OAuth key/client、manager/proof/converter/registry、HttpProcessClient、domain schema/hash | 不复制身份/HTTP/摘要平台 | None |
| MC-DEP-001 | Applicable | PASS | 14paths无POM/build变更 | 既有JDK/SDK/组件足够，无新依赖 | None |
| MC-NAME-001 | Applicable | PASS | Transport/Bindings、Request/BackendVO/ResolvedBindingVO/DecisionObservation/Failure | 技术port/值/安全异常职责明确 | None |
| MC-VALID-001 | Applicable | PASS | fixedmapping/schema/fullrefs、samebearer proof/source/key/trust、dynamic scope/response | 现有协议/身份/工具边界完整正反验证，无新不受控HTTP输入 | None |
| MC-MODEL-001 | Applicable | PASS | 真不可变values/primitive outputs/防御复制和Backend URIASCII equality | 非实体/可变POJO，不把executor metadata当认证 | None |
| MC-CONVERT-001 | Applicable | PASS | 复用现有VerifiedPrincipalConverter和实际签名proof；无新增Converter或映射层 | 原身份转换器继续拥有真实principal转换，不新建手工替代层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Adapter/port，无新增业务日志Service | token只局部引用，Failure仅enum消息/无cause | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config注册；caller资源只注入构造 | 不修改共享对象生命周期，无Qualifier新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/原SDK/schema/digest | 无新工具/认证/HTTP/线程框架 | None |
| MC-JSON-001 | Applicable | PASS | 原Jackson HttpProcessClient解析typed DTO，mock仅byte[]响应 | 无新协议栈/JSON转换技巧；单/无命中严格验证 | None |
| MC-TIME-001 | Applicable | PASS | §4.9 singleWall+mono/min expiry、remote/local observation不排序 | 迟到有效response先callback，outer才抑制value；取消先clear再interrupt | None |
| MC-CONFIG-001 | Not applicable | N/A | 14paths无profile/properties/flags | 新库未自动注册/启用，配置结构保持 | None |
| MC-PATTERN-001 | Applicable | PASS | AR port依赖倒置/AS Adapter+动态oneShot能力 | 实际变化点隔离，不造第二执行/认证引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 14paths、原V13/Components/config/native不动 | C/PS同值、AR原参数hash兼容，无权限/dispatch扩大 | None |
| MC-TEST-001 | Applicable | PASS | §8真实RED/GREEN/mock SDK/旧C/PS/AR回归与static命令 | 无listener/crypto伪证明，实际输出进报告 | None |
| MC-BLOCKER-001 | Applicable | PASS | 三项闭合决定及所有独立行 | 当前API/安全/错误/时间/资源合同无未决设计，runtime边界保留 | None |

### 12.6 Final verdict

PASS — Ready for user review
