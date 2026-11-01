# 可信固定readonly MCP的安全JSON原生传输

| Field | Value |
| --- | --- |
| Document | `2026-11-01-18-23-verified-readonly-mcp-transport.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 18:23 Asia/Shanghai` |
| Updated | `2026-11-01 18:23 Asia/Shanghai` |
| Owner | 原用户授权，Root交付/验证 |
| Repository | `tianji-service` |
| Scope | 8paths；MCP SPI privacy+实际SERVICE scoped transport，无journal/native/config |
| Source Requirement | 原蓝图T3-07 §12.1/13.1/13.2/13.4/17 |
| Baseline Revision | `main@81e72d896f7d87e93516750e8f6e913750aa576e` clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint，用户明确继续执行，非Accepted文档 |
| Spec Revision | 2026-11-01 SHA-256 `b91c77593168b717aa4fad350e491b5c58a8ca9a064ef0b98aadfffed605abe7` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [MCP合同/pinning](2026-11-01-17-49-fixed-readonly-mcp-contracts.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
基于已提交readonly非计费metadata/port，实现实际SERVICE verified callback与官方McpSyncClient。SDK raw响应日志必须在其前方受控：用公共McpClientTransport SPI/opaque holder，保官方lifecycle/ID/pending correlation，不新造Agent/MCP执行引擎。JDK25现limiting handler在聚合前截断bytes，原wall+mono窗口贯穿所有阶段。只支持YuhengStable2025-11-25普通JSON POST子集；无自动挂载/授角色或journal。

## 2. Target Spec and Effective Design
原§12.1要求原生薄Adapter/Decorator与统一Policy/audit、无双loop；§13可信SERVICE、固定版本/有限预算/日志隐私、未知不重做；§17失败/取消/注入验证。现官方SDK2.0已在classpath，private payload会被stock transport/session/lifecycle于DEBUG/INFO/WARN直接记录，公开logging consumer不能替换。源审已证明custom SPI opaque result可保护日志且保官方客户端；credential helper已有generic scope/resource，只需同层public visibility。Yuheng d2682b4当前SOURCE peer普通POST均JSON，tools/list缺outputSchema且hints为String，因此不假造远端output证明，本地schema验实际body。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/17 | 官方SDK拥有lifecycle/requestId/correlation，固定JSON SPI在SDK日志前完成opaque/safe projections | §7对应8paths |
| PLAN-REQ-002 | §12/13/17 | 复用实际credential helper、完整固定SERVICE target/scopes、原window和当次currentkey/trust，绝不模拟USER | §7对应8paths |
| PLAN-REQ-003 | §12/13/17 | callback/owner/once/µ不扩窗，所有协议IO在消费后，HTTP boundedbyte提交与cancel清bearer原子 | §7对应8paths |
| PLAN-REQ-004 | §12/13/17 | fulltool SHA/schema/server/protocol/session/JSON shape闭合；knownfact先回callback再outerpostcheck/cleanup，no retry | §7对应8paths |
| PLAN-REQ-005 | §12/13/17 | 8scope/旧helper仅visibility/no deps/DDL/config/journal/native；finiteSDK+FakeHttp验证与独立门禁提交 | §7对应8paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
81e72d8 clean；23SQL immutable；metadata/port6类48PASS，Yuheng d2682b4 69PASS。SDK2.0 source cache/JDK25 publicAPI已源审，不升级artifact。
### 4.2 Test-first strategy
SPI signatures/Exchange/UOE供编译→nativeSDK+FakeExchange有效RED→GREEN；旧helper仅public modifiers供跨包调用；wrapper signatures/UOE与实际helper+FakeHttp tests有效RED→GREEN。未新增行为前不可补guards假装先过；compiler/fixture不是RED。Root所有Java冻结后统一Maven。
### 4.3 Sequential and parallel boundaries
worker1 owns SPI/Test2paths；worker2 owns wrapper/Test+helper3paths，只在SPI公开内部signature锁定后编译声明，行为按独立RED；Root其余README/Plan/EVI3paths。不是独自在代码库，保他人改动，不自行build/commit/runtime，Root统一。
### 4.4 Commit boundaries
8paths一个standaloneMCP传输语义commit `feat(agent): verify bounded private MCP tool calls`；actualjournal/native/Bean/config随后独立Step，禁止提前写。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Element | Source necessity | Existing capability | Direct/reuse alternative | Decision |
| --- | --- | --- | --- | --- |
| private data beforeSDKlogs | §13日志默认无tool正文/凭据 | stockSDK本身rawlogs且consumer additive | 公共SPI opaque/error投影+官方client | Implement必要Adapter不全局mute |
| 字节limit | §13预算/输出限制 | JDK25 BodyHandlers.limiting generic | 直接existingAPI，不customsubscriber/升级SDK | reuse |
| actualSERVICEcredential | §13不冒USER | generichelper已有完整验证但packageprivate | 同文件public modifiers | reuse无新auth框架 |
| nativecaller/SDKprotocol loop | §12无双loop | McpSyncClient lifecycle/ids已实现 | 继续官方client，SPI只HTTP/privacy | 不造第二client/sessionengine |
| newBean/DDL/ScopeGrant | 后续consumer不属库 | 原框架已有 | 本Step无新增 | 不扩scope |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| safeJSON SPI | PLAN-REQ-001/004 | SPItest | compiledecl+既有Backend19 | opaque/sanitized SDK DTO | scopedwrapper | Step1 |
| credential wrapper | PLAN-REQ-002/003/004 | wrappertest | SPIGREEN/helperpublic | verifiedoneShot+realboundedHTTP | 未来Recorder | Step1 |
| docs/gates | PLAN-REQ-005 | regression/static | 两GREEN | reviewable8pathcommit | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | 现AS integration固定transports/ARport/helper | 同技术层/官方SPI | 无业务层/新engine/auth registry | Step1 MC-ARCH-001/MC-REUSE-001 |
| Validation/model | Backend19/ToolSchema/record/scope现模式 | 所有handoff严格typed guard | metadata不auth，opaque/private state不入API | Step1 MC-VALID-001/MC-MODEL-001 |
| Bean/log/JSON | 普通manualFactory/既有Jackson/TraceContext | 无新Bean/DI；fixedsafe日志 | 不改全局logger，不泄rawbody/cause，type-use规则保持 | Step1 MC-BEAN-001/MC-LOG-001/MC-JSON-001 |
| Pattern/time | 原Adapter/ownedCallScope/SDKlifecycle | Adapter+scopedcapability无新调度层 | 原wall+mono/µ窗口/事实先于presentation | Step1 MC-PATTERN-001/MC-TIME-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| MCPnativeprivateDTO | SDK公共McpClientTransport/TypeRef/JSONRPCResponse | pending先注册、resultObject直接unmarshal、recordtoString可控 | 可exact3types手工投影 | reuse | None | Step1 MC-REUSE-001 |
| boundedHTTP | JDK25 BodyHandlers.limiting | 接收batch超过cap前取消，不交给下游 | 直接调用，无builderwrapper | reuse | None | Step1 MC-DEP-001 |
| credential/window | TianjiProcessToolCredentialVerifier/FixedStart | genericTarget/key/trust/scopes/单次调用原window已实现 | publicvisibility+同pattern | reuse | None | Step1 MC-UTIL-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §12/13/17 | 新技术transport/opaqueVO与现Request/Backend值命名 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 2 | §12/13/17 | rawJSON/ID/type/session/schema/pin/credential/currentwindow/事务/线程/once每边界 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 3 | §12/13/17 | immutableVO/privatefinal state+callback scope同原，未新增普通entity/Converter | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 4 | §12/13/17 | 无新SpringService/DI，manualtransport ctor沿原；必要cleanup日志fixednoCause | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 5 | §12/13/17 | JDK25/SpringJackson/Reactor/既有SDK+IAM，无新依赖 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 6 | §12/13/17 | SpringJackson严格rawJSON，SDKDTO手工typed构造/opaque内部，旧wire不改 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 7 | §12/13/17 | 无YAML/profile/启用改动，仅库 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 9 | §12/13/17 | 官方SPI Adapter/ownedcapability+Factory，实际protocol/credential隔离变点 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 10 | §12/13/17 | java.time Clock/Instant/Duration+Systemnano原起点，不扩窗 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 11 | §12/13/17 | 现AS integration技术结构/23SQL/Outbox与业务CRUD不动 | §7 exact8paths | §6/逐文件具体合同 | source/REDGREEN/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | originalCeiling/credentialExpiry/oneShotdeadline | Occurred instant | Instant/Clock | Not persisted | Internal | Not applicable: 技术时刻 | Not applicable: 无UI | min(req/run/start+timeout/cred)，callbackexpiry floorµ，callfuture/µ/noextension，clockrollback不扩mono | 原t0/n0，不重开budget | oldports/helper验证body不改 | Step 1 TEST-002 |
| TIME-002 | HTTPtimeout/cleanup | Duration | Duration | Not persisted | Internal | Not applicable: IO预算 | Not applicable: 无UI | 每HTTP使用originalremaining；cleanup不刷新window，skipTTL fallback | Duration nanos/同origin | 不承诺remoteundo/持续撤销 | Step 1 TEST-001/002 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransportTest.java | 原生SDK真实生命周期/opaque隐私边界RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransport.java | 借标准SDK transport SPI做固定JSON协议/私密数据投影 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java | 使同一实际凭证校验可供同层MCP consumer复用 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransportTest.java | 真实credential helper+官方SDK+FakeHttp完整bounded oneShot RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransport.java | 私有SERVICE scope与原wall+mono窗口控制所有MCP HTTP提交 | Step1 |
| MODIFY | README.md | 准确描述固定Stable JSON subset与独立runtime边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-18-23-verified-readonly-mcp-transport.md | 锁定8路径/字段/SPI接口/状态与安全行为 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-verified-readonly-mcp-transport.md | 真实验证与限制 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**SPI exactsignature.** package-private final `TianjiMcpJsonRpcTransport implements McpClientTransport`，constructor `(TianjiFixedReadOnlyMcpToolBindings.BackendVO backend, Exchange exchange)`，复用Backend不新建重复ProfileVO。nested package-private `@FunctionalInterface Exchange { CompletableFuture<HttpResponse<byte[]>> exchange(HttpRequest requestWithoutAuthorization, boolean toolsCall); }`。方法connect(handler)/sendMessage(JSONRPCMessage)/unmarshalFrom(Object,TypeRef<T>)/protocolVersions()/closeGracefully()按SDK公共SPI，另`Mono<Void> deleteOwnedSession()`。connect只安装一次handler无IO；closeGracefully仅本地幂等、无DELETE、无全局logger/metadata mutation。Exchange为必要局部IOvariation，外层独占credentials/window/actualsubmission，SPI独占协议/opaque，非新auth层。

**Wire/lifecycle exact.** 支持固定YuhengStable2025-11-25 ordinaryJSON POST。出站仅initialize→notifications/initialized→tools/list→tools/call，每种一次/按序，最多一个pendingexchange；reject额外方法/重入/重复/非官方requestid类型。原生SDK负责ID/pending/生命周期，SPI不得自己生成替代SDK请求ID。所有JSONRPCjsonrpc=2.0，固定endpoint，不从响应取URL；prepare请求不含Authorization/Cookie/任意hostMeta。Content-Type application/json UTF8、Accept application/json,text/event-stream（但响应SSE明确unsupported），初始化携fixedversion/no旧session；200 JSON(mediaType允许charset=UTF-8或缺省)，exactbody protocol/serverInfo name+version/tools cap存在；captured唯一canonical UUID Mcp-Session-Id（Yuheng生成UUID）。initialized通知无id，同version/session，empty202无正文；list/call同headers JSON200；list onepage，reject非空cursor/nextCursor。

JSON raw strict duplicate/trailing拒，root只允许jsonrpc/id/result/error四keys，objectroot/no batch/no inbound method/notification/request，exactoutboundid及类型；result/error必须恰有一个key且其value非null（另一key即使null也不得存在），不以DTOdefault空值接受缺失。depth≤8/rawbody≤MAX_RESPONSE_BYTES beforeaggregation，parser string/number/body受既有常量界限；不使用SDK deserializeJsonRpcMessage打印raw数据。HTTP/parser/async故障转固定noCause Failure的Mono.error；SDK真实sendRequest errorconsumer会移pending并立即error，不造syntheticresponses/lastError registry，不发McpTransportSessionNotFoundException或触发自动reinitialize。

**Opaque/SDK projections.** JSONRPCResponse result为private nonnullholder `(Class<?> type,Object value)`，explicit constanttoString，绝不rawMap。只构建并接受InitializeResult/ListToolsResult/CallToolResult exacttype+kind，其他type failfixed。校验在handler前完成，unmarshalFrom只返回已验证最小DTO，不做泛型inbound反射mapper。Init只pinnedpublicname/version/proto、确实存在的tools最小cap，其他advertisedcaps不使用/剥离，不因peer有resources/prompts/experimental就拒；instructions/meta/title/desc/icons网站剥离。List找fixedTool恰一次，hash ENTIRE rawtargetJSON对象（不是仅四knownfields，任何outputSchema/meta等存在性都入81e72d8完整docfingerprint）；exacthash否则PROFILE_CHANGED。return仅fixedsafe name、sanitizedschema，无rawannotations/title/desc/meta/icons。SDKTool.outputSchema在wire缺失时保持null，不能捏造peer证明；若出现则要求同固定primitive outputShape匹配后仍只投影安全部分。

**Input/output subset exact.** input必须object，property集合/type与backend.inputSchema完整一致，required是无重复String数组/缺省empty且完全等localrequired。properties缺省empty仅localfieldsEmpty时；root允许type/properties/required/additionalProperties/title/description/$schema，$schema若有仅JSONSchema2020-12 URI或draft07 URI；leaf仅type/title/description，primitive string/integer/boolean。unsupportedrefs/compositions/nested/array/number/constraints/default/format/enum等拒，不假装通用JSONschema。additionalProperties若有仅Boolean；localinputs始终closed，比remote default/open更窄，wholeDoc hash仍pin其变化，不扩大模型输入。metadata title/description仅hash不进入SDK/模型。工具annotations可String/Boolean等纯JSON，忽略其authority，wholeDocpins。call出站固定name+backend.inputSchema.values(args)，wire_meta必须absent/null并在发送省略；不转发hostToolContext、tenant/actor/approval/bearer到arguments或meta。

Call result仅普通content/isError/structuredContent/_meta shape；task/augmentation/unknownpayload拒。isError必须literalBoolean（固定peer总返回，避免弱DTOdefaults）；true允许实际peer非空private content/structuredContent：按共同JSON/数组object形状与body/depth限验证，structuredContent可任意已解析JSON值且_meta若有须object/null，随后全部丢弃；仅SDK投影content[]/structurednull/metanull并保TOOL_ERROR，不对error套success outputSchema，不打印servererror；false必须structuredContent object、复用ToolSchema推导boundedprimitive，再外层backend.outputSchema.values严格本地验证，绝不从缺remoteoutputSchema推导。content若存在必须JSON数组且每项为object，其他具体content语义不解释、全部丢弃；不声称验证被忽略的MCP content子协议，不反射转换，正文不影响错误标志。JSONRPCerror code必须int32整数，message固定“TIANJI_MCP_REMOTE_ERROR”、data=null，原message/data/cause/suppressed不得留给SDK日志。raw响应/headers/解析异常都不记录或attachcause；DEBUG/INFO/WARN/ERROR/Throwable tests用实际peer错误shape的sentinels覆盖。

**Credentialhelper visibility exact.** 原AS integration.process.TianjiProcessToolCredentialVerifier保持包名、所有验证body、privatehelpers与redactedtoString，只有class/ctor/credentials/requireCurrent/TargetVO+explicit compactctor/CredentialVO改public。不移动/rename/复制auth算法，不改旧3调用者。CredentialVO本身无bearer，key/trust是非secretidentity；真实令牌由scope私有持有。

**Wrapper exact.** public final `TianjiFixedReadOnlyMcpToolTransport implements ReadOnlyMcpToolTransport,AutoCloseable`；public7ctor `(bindings,oauth,authentication,converter,issuers,HttpClient,int concurrency)`；package9ctor再Clock/LongSupplier，六pure usesBindings/OAuth/Authentication/Converter/IssuerRegistry/HttpClient helpers为以后actualchain验证。拒null/并发非1..8/redirect非NEVER/cookieHandler或authenticator存在；borrowedHTTP/IAM从不close，owned virtual ThreadPoolExecutor0..concurrency/SynchronousQueue/无继承inheritableTL，TraceContext捕获/恢复沿现FixedDecision；scope保捕获的TraceContext，在HTTP提交时仅用现trace.inject写traceparent/tracestate/x-egon-request-id（TraceContext.REQUEST_ID_HEADER原常量）（hasTrace时），不接受prepared请求自带trace或其他身份头。entry先拒closed/interrupted/foreignTxn，t0/n0在bindings解析前抓取。originalCeiling=min(req.deadline,run.deadline,t0+backend.timeout)，所有remaining同时wall+mono，credentials与回调均该origin；更早credentialExpiry收紧不重置。callback executor expiry=floorµ(mincred/originalCeiling)，这是scopedcapability ceiling不是伪造rawJWTexpiry。

OneShot只有callback ownerthread/active+无foreignTxn能consumeCAS一次，CAS先于deadline合法化，因此null/nonµ/past/extension也花掉，重试不能恢复。deadline必须future/µ/≤scopedceiling、收紧同t0/n0；整个官方client新建initialize/initialized/listTools(null)/callTool在此之后（未来Recorder必须先commit BEGIN）。explicitemptyClientCapabilities、fixedpublicclientInfo（name=tianji-readonly-mcp/version=1.0，仅本adapter公开标识非authority）、enableCallToolSchemaCaching(false)，SDKtimeoutes≤首次currentremaining，HTTPeachremainingOriginalWindow。只标tools/call POST可能送出，初始化/发现等metadata阶段不把oneShotConsumed误当businessSent。

Exchange在短publishLock内current(active/interrupted/Txn/window/key+ACTIVEtrust)、exactfixedURI/允许POST或ownedDELETE/headerallowlist仅Content-Type/Accept/Mcp-Protocol-Version/Mcp-Session-Id、request无自带Authorization/Cookie/Proxy-Authorization/trace，复制请求注入scope当前SERVICE bearer和remainingtimeout；在调用JDKsendAsync前仅toolsCall标possiblySent，使用BodyHandlers.limiting(ofByteArray(),MAX_RESPONSE_BYTES)，返回原future，future注册同lock完成。cancel同lock inactive+bearer=null FIRST，再cancel所有HTTP/worker futures；lateOAuth不能重publish。SDK asynccompletion只处理bounded/sanitized协议，source/state不得重开已关闭scope；不清理/rollback外来transaction。

**Knownfact/cleanup/error ordering.** tools/call已知响应完成schema验证后立即CallObservation回callback，不在此间加取消/过期/key postcheck遮蔽可记事实。callback记录后，关闭operation，尝试仍在同原window/currentcredentials/已捕获session/endpoint的DELETE（固定204empty，不刷新OAuth/window，不送新tool）。无法清理则本地关闭+peerTTL fallback；cleanup失败用固定lowcardinality安全提示不附rawcause，不吞business事实/不重做，不把cleanup错误转成原call UNKNOWN。顺序callbackfact机会→boundedcleanup→localSDK/transportclose→最终current检查→return；callerFuture.get返回后还current一次。callback失败则保其原safe失败、取消/本地close，不为cleanup开新authority。外层postcheck能mask presentation但不能撤事实/释放预算/自动retry。closed退出不closeborrowedHttp，pool仅自身。

分类：credential/bootstrap失败CREDENTIAL_UNAVAILABLE；init不可用PROFILE_UNAVAILABLE，server/tool/doc/schema drift PROFILE_CHANGED；已分类的PROFILE_UNAVAILABLE/PROFILE_CHANGED/CREDENTIAL_UNAVAILABLE/OPERATION_DENIED/CANCELLED/DEADLINE_EXCEEDED优先保留；除此raw IO在consumed但tool未送出时OPERATION_NOT_SENT；validated call JSONRPCerror或HTTP401/403 OPERATION_DENIED；已可能call但malformedoutput OPERATION_INVALID_RESPONSE；IO/timeout/non-auth HTTP aftertoolpossiblysent OPERATION_RESULT_UNKNOWN；cancel/interrupt/close CANCELLED；originalwall/mono expiry DEADLINE_EXCEEDED。每Failure固定kind/noCause，callback若AgentLedgerException保原code但固定safe message；不让私密body/原Throwable进入SDK/模型日志。任何阶段无自动retry/remoteundo承诺。

**Boundaries.** 只固定JSON子集；不支持SSE GET/replay、inboundnotification/serverrequests、RC/legacySSE、durabletasks、remote-mount/genericoutputs，不声称普通MCP全兼容。Sourcepeer支持DELETE且身份binding，实际TTL默认30min是代码边界未现场验收；无法有界cleanup可能留session/response到TTL，明确说明。ActorPDP/cap/BEGIN/journal/native/pluginentry/ASBean wiring仍后续；portmatching不是原requester工具授权。现23SQL/POM/permissions/配置/旧DTO不动，新canonicalhashhelper81e72d8不改。

**CLAR-001 actual工具错误与投影。** Yuheng McpResultBinder即使isError=true仍带非空content/structuredContent；合同中的empty/null限定是SDK安全投影，不是拒绝peer原文。明确先bounded raw校验再丢弃；error不经success schema，保已知TOOL_ERROR空结果供callback记事实。日志断言显式覆盖ERROR，所有raw sentinel不可出现。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 固定readonly MCP的private nativeSDK与真实SERVICE scoped transport
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 81e72d8已commit/clean；SDK2.0/JDK25/现IAMhelper；Yuheng peer d2682b4源审。
- Baseline state: readonlyport/backend库已有，无真实MCPtransport或credentials consumer。
- Observable outcome: 实际nativeSDK+真实helper受固定schema/身份/原时限/私密数据边界控制，onlyoneShot。
- End state: 可供后续Recorder调用的library，无自动运行或framework挂载。
- Test-first gate: Required — SPI与wrapper分别validRED后GREEN。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransportTest.java`
- Purpose: 原生SDK真实生命周期/opaque隐私边界RED。
- Symbols: SPI/wholeToolPin/sanitizedDTO/ownedSession。
- Repository evidence: cachedMCP2.0已源审；nativeSession先registersink后send且直接unmarshal opaque。
- Dependencies and consumers: 最小Backend/Exchange/class signatures与UOE供编译，先真实native SDK+FakeExchange RED。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
assertNativeSdkInitializeNotifyListCallAndOwnedDeleteWithoutListener();
assertFullToolHashAndExactInputAndStructuredOutputBeforeSdkConsumers();
assertOpaqueResponseAndSanitizedInitErrorsNamesNeverLogSentinelAtDebugInfoWarn();
assertWrongIdInboundNotificationUnsupportedContentBatchesDuplicatesDepthErrorsFailFastNoPrivateCause();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransport.java`
- Purpose: 借标准SDK transport SPI做固定JSON协议/私密数据投影。
- Symbols: BackendVO/Exchange/connect/sendMessage/unmarshalFrom/deleteOwnedSession/closeGracefully。
- Repository evidence: McpClientTransport公共SPI与McpClientSession244–279，JDK/Jackson现依赖。
- Dependencies and consumers: File1有效RED后实现，不包含任何rawbearer/credentials库。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
final class TianjiMcpJsonRpcTransport implements McpClientTransport { ctor(BackendVO backend,Exchange exchange); }
connect(handler) { installExactlyOnceWithoutIo(); }
sendMessage { allowOnlyFixedLifecycleOutboundMethods(); buildNoAuthRequest(); return exchangeThenValidateWireAndDispatchOpaqueResultOrSanitizedError(); }
unmarshalFrom { requireOwnOpaqueHolderAndExactExpectedSdkType(); returnAlreadyValidatedMinimalDto(); }
deleteOwnedSession { sameEndpointVersionCapturedSessionOnly(); }
closeGracefully { localOnlyIdempotentNoNetwork(); }
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java`
- Purpose: 使同一实际凭证校验可供同层MCP consumer复用。
- Symbols: public class/ctor/credentials/requireCurrent/TargetVO/CredentialVO。
- Repository evidence: 已查generic TargetVO scopes/resource/helper，无ProcessAction；3既有调用者。
- Dependencies and consumers: SPI GREEN后wrapper编译前仅public additive，既有逻辑和包原样。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
makeExistingClassCtorCredentialsRequireCurrentAndTwoRecordsPublic();
makeExistingTargetCompactConstructorPublic();
keepAllValidationBodiesAndRedactedDiagnosticsExactlyUnchanged();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransportTest.java`
- Purpose: 真实credential helper+官方SDK+FakeHttp完整bounded oneShot RED。
- Symbols: withVerifiedCall/µdeadline/nonce-freeknownFact/cancellation/privacy。
- Repository evidence: 现FixedDecision/Start/Approval fixture与源库已存在，JDK25limiting可直接FakeSubscriber检验。
- Dependencies and consumers: wrapper仅compile declarations/UOE后有效RED，helper仅visibility改动。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
assertRealCredentialHelperExactTargetAndNativeLifecycleInsideConsumedCallback();
assertScopedExecutorExpiryOriginalWindowMicroNarrowingOwnerThreadAndOnceConsumption();
assertHttpByteLimiterCookieRedirectAndAuthShapeAndClearBeforeFutureCancel();
assertWallRollbackKeyTrustRotationForeignTxnCloseAndLateOAuthCannotReopen();
assertKnownObservationReachesCallbackBeforeCleanupAndOuterSuppression();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransport.java`
- Purpose: 私有SERVICE scope与原wall+mono窗口控制所有MCP HTTP提交。
- Symbols: public7ctor/package9ctor/uses6helpers/withVerifiedCall/CallScope/close。
- Repository evidence: 同现FixedDecision bounded virtualpool、helper、TraceContext与Start narrowCallDeadline模式。
- Dependencies and consumers: File4有效RED后实现；SPI已GREEN只负责协议/隐私。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
withVerifiedCall { captureOriginalWallMonoBeforeBindings(); rejectForeignTxn(); resolveFixedProfile(); verifyCredentialsOnlyBeforeCallback(); }
callback(executorFloorMinExpiry,oneShot) { oneShot.call(finalMicroDeadline) executesOwnerActiveOnceThenNarrowsWithoutReset(); initializeNotifyListPinCallUsingFreshNativeClient(); returnValidatedObservationWithoutPostcheckBeforeFactOpportunity(); }
Exchange { publishLockCurrentTxnKeyTrustExactEndpointAndShape(); addPrivateBearerAndRemainingTimeout(); markToolsCallBeforeAsyncSubmit(); sendAsync(limiting(ofByteArray,MAX_RESPONSE_BYTES)); registerFutureBeforeUnlock(); }
afterCallback { closeOperation(); boundedOwnedDeleteIfStillAuthorized(); localSdkClose(); finalCurrentChecksBeforeReturn(); }
cancel { sameLockDeactivateClearBearerFirst(); cancelHttpAndWorkerFutures(); }
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 6 — `MODIFY README.md`
- Purpose: 准确描述固定Stable JSON subset与独立runtime边界。
- Symbols: MCP transport/credentials/cleanup/privacy/window说明。
- Repository evidence: 现metadata/port段落明确后续实现，需更新该处实际能力。
- Dependencies and consumers: 真实源码/log后写，不声称journal/native/config ready。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
documentOfficialSdkLifecycleWithCustomPrivateJsonTransport();
documentByteBoundsSchemaPinNonbillableWindowAndCleanupTtlFallback();
documentUnsupportedSseRcTasksAndStillPendingJournalNativeConfigRuntime();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 7 — `CREATE docs/egon/plan/2026-11-01-18-23-verified-readonly-mcp-transport.md`
- Purpose: 锁定8路径/字段/SPI接口/状态与安全行为。
- Symbols: V5/5aliases/17MC10Rules。
- Repository evidence: 原蓝图§12/13/17与已提交81e72d8/d2682b4。
- Dependencies and consumers: Root全文/strict/resources/checklist/hash后GO。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
lockExactEightPathsAndNoAdditionalDepsOrAuthFramework();
requireSpiThenCredentialWrapperActualRedGreen();
requireScopedCommitBeforeJournalOrNativeSource();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-verified-readonly-mcp-transport.md`
- Purpose: 真实验证与限制。
- Symbols: RED/GREEN/17MC10Rules/8scope。
- Repository evidence: 真实stdout与source，初始PENDING。
- Dependencies and consumers: 实际执行后独立逐项闭合。
- Why now: §4.6/§6精确依赖，tests先于行为，未实现新signature仅编译声明/UOE。
- Contract/signature changes: §6 exactSPI/Exchange/public7+package9ctor/六helper/helpervisibility；不改旧API语义。
- Input/output and state mapping: §6所有privatebounded data与safeSDKDTO/原window/callbackfact顺序，无DB改动。
- Error and edge behavior: §6 fixedKind/noCause、身份/版本/shape/current/cancel/cleanup边界，未实现不得宣称通过。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-LOG-001/MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001；§12.5独立检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；typedguards/immutablemetadata/现manualFactory-Adapter/固定日志/Jackson/java.time/旧结构具体义务。
- Implementation pseudocode:
```java
recordNativeSdkFakeExchangeAndActualHttpBodySubscriberProof();
recordNoPrivateLogSentinelsAndKnownFactOrdering();
recordOnlyEightPathCommitAndUnexecutedLiveBoundaries();
```
- Verification contribution: TEST-001/002/003；真实SDK只FakeExchange/FakeHTTP无listener，log/source验证记录evidence。
- After this file: 本贡献可review，journal/native/config与liveIAM/network仍未交付。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`，strict从workspace。
- Verification command: §8精确selectors；每unit先focused独立log，Root所有Java冻结后统一。
- Expected result: tests真实privacy/byte/window/actor/fact顺序全PASS/zero skip，无原私密数据泄漏，无旧helper行为改变。
- Failure returns to: SPI回File1/2，helper/wrapper回File3/4/5，docs回File6；compiler/fixture先修，不算RED。
- Completion criteria: exact8scope/23SQL/noPOM/17MC10Rules/独审/strict/resources/checklist/cached后立即commit。
- Rollback: 停新consumer/当前library尚不自动启用；保known事实与旧DB/权限，不重做UNKNOWN。
- Commit paths: stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransportTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiMcpJsonRpcTransport.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiProcessToolCredentialVerifier.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransportTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolTransport.java、README.md、docs/egon/plan/2026-11-01-18-23-verified-readonly-mcp-transport.md、docs/egon/evidence/2026-11-01-verified-readonly-mcp-transport.md
- Commit: `feat(agent): verify bounded private MCP tool calls`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Expected evidence |
| --- | --- | --- |
| TEST-001 | newSPI nativeSDK+FakeExchange tests | lifecycle/header/session/hash/opaque/log/noCause/DELETE/unsupportedshape有效RED→GREEN |
| TEST-002 | newWrapper真实helper+FakeHTTP/JDKsubscriber | 原window/µ/once/cancel/keytrust/bytes/knownfact/no privateleak有效RED→GREEN |
| TEST-003 | exactbelowcompat与8scope/23SQL/17MC10Rules | 全PASS无liveacceptance预填 |
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=TianjiMcpJsonRpcTransportTest,TianjiFixedReadOnlyMcpToolTransportTest,ReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolBindingsTest,TianjiFixedDecisionToolTransportTest,TianjiFixedBpmnStartToolTransportTest,TianjiFixedToolApprovalVerifierTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-verified-readonly-mcp-transport-final.log 2>&1
```
现testclass/source符号preflight核对；newtests存在后才选。loggingtest只测试隔离局部logger保存/恢复level/appender，不改生产全局state；无HTTP listener/containers/apps/浏览器，FakeHTTP实际驱动JDKlimiting BodySubscriber取消/聚合前界限。Maven源代码无新依赖；harness用现JUnit/verify脚本，不新scripts/work。Source字节/shape/async风险由可执行测试证明，无法验证的live边界明确记录。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/pom/config/权限/角色/sourceactivation/业务DTO变化。旧helper仅public可见性不改验证body。借用客户端不close/不用用户token；新ASlibrary未成为Bean或运行消费者。后续接journal/native时才加入ownpipeline权限/预算；不能把本库当ActorPDP授权。cleanup尽力原window，超界TTL，不承诺取消远端operation或exactlyonce。Default现Process/AA模式保持。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/17 | Step 1 | §7 exact8paths | TEST-001/002/003 | 官方SDK拥有lifecycle/requestId/correlation，固定JSON SPI在SDK日志前完成opaque/safe projections |
| PLAN-REQ-002 | §12/13/17 | Step 1 | §7 exact8paths | TEST-001/002/003 | 复用实际credential helper、完整固定SERVICE target/scopes、原window和当次currentkey/trust，绝不模拟USER |
| PLAN-REQ-003 | §12/13/17 | Step 1 | §7 exact8paths | TEST-001/002/003 | callback/owner/once/µ不扩窗，所有协议IO在消费后，HTTP boundedbyte提交与cancel清bearer原子 |
| PLAN-REQ-004 | §12/13/17 | Step 1 | §7 exact8paths | TEST-001/002/003 | fulltool SHA/schema/server/protocol/session/JSON shape闭合；knownfact先回callback再outerpostcheck/cleanup，no retry |
| PLAN-REQ-005 | §12/13/17 | Step 1 | §7 exact8paths | TEST-001/002/003 | 8scope/旧helper仅visibility/no deps/DDL/config/journal/native；finiteSDK+FakeHttp验证与独立门禁提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Evidence/resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | stockSDK无法perclient禁private日志 | 公共SPI+opaque+typedprojection已源证明，tests验证，不全局mute | Root | Closed设计 |
| DEC-002 | actorwindow与协议IO职责 | 必要packageprivateExchange；outerlock独占bearer/submit，SPI无auth | Root | Closed |
| DEC-003 | peer缺outputschema/其他caps/annotationstrings | wholetoolpin+strict本地schema，stripmeta而不假造peer证明 | Root | Closed |
| RISK-001 | cleanup覆盖knownfact/扩authoritywindow | callback先记事实、原windowDELETE或TTL、本地close与safe提示 | Root | Trackedtests |
| RISK-002 | 超时误称远端未执行 | 只分consumed/toolpossiblySent，无重试/undo，capturefuturecancel界限 | Root | Trackedtests |
| RISK-003 | malformed私密payload被SDKlog | 过滤前opaque、全部causeless、记录多level sentinels断言 | Root | Trackedtests |

## 12. Review and Acceptance
### 12.1 Original requirement fidelity
原blueprint原生薄Adapter/可信SERVICE/预算/日志/固定schema与分步提交，不auto运行/授角色。
### 12.2 Spec consistency
必要SPI privacy边界源证明替代unsafe stocktransport，不新client引擎/权限机制；原实际helper复用，官方SDK仍唯一lifecycle。
### 12.3 Repository executability
已查metadata81e72d8/peerd2682b4/publicSDKinterfaces/真实helperbody/JDK25limiting；8source顺序/ownership/可执行RED明确。
### 12.4 Test and release completeness
finiteSDK与fakeHTTP，no actualIAM/crypto/SQL/network/TLS acceptance；23SQL原样，无remote模型费用调用。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AS integration/port技术profile与官方SPI，无新增层/engine/CRUD | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-REUSE-001 | Applicable | PASS | 原credential helper/SDK lifecycle/JDKlimiting/TraceContext/metadatahash，全已有能力 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-DEP-001 | Applicable | PASS | SDK2.0与Java25均现dependency/source，无POM变动 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-NAME-001 | Applicable | PASS | TianjiMcpJsonRpcTransport/FixedReadOnlyMcpTransport/opaqueVO，现technical命名 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-VALID-001 | Applicable | PASS | §6每个wire/actor/window/thread/session/schema/pin/cancelhandoff guard | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-MODEL-001 | Applicable | PASS | 现BackendVO immutable，privatecallbackscope状态必要；无普通entity/Converter | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无PO/entity/业务DTO转换；SDK投影协议adapter非businessMapper | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-LOG-001 | Applicable | PASS | 生产日志仅必要cleanup固定低基数无cause，所有raw数据SDK前脱敏 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-BEAN-001 | Not applicable | N/A | 无新managedBean/DI；manual构造/六purepointer helpers，旧helper可见性only | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-UTIL-001 | Applicable | PASS | 现JDK25/BootJackson/SDK/Reactor/IAM，不造工具框架 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-JSON-001 | Applicable | PASS | 严格BootJackson入站树/typedSDK DTO/opaque，旧wire/ordinal不改 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-TIME-001 | Applicable | PASS | TIME001/002原wall+mono/µ/credential expiry/cleanup不扩窗，no remoteundo | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/启用动作，只有manual library | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-PATTERN-001 | Applicable | PASS | 官方SPI thinAdapter+private scopedcapability+Factory，具体privacy/auth变点 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-SCOPE-001 | Applicable | PASS | exact8paths helper仅publicmodifiers，无SQL/POM/journal/native/config/role动作 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-TEST-001 | Applicable | PASS | SPI及wrapper独立REDGREEN、sevenclasscompat、JDKsubscriber+logsentinels/cancelrace | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-BLOCKER-001 | Applicable | PASS | 所有设计/ownership/错误分类具体锁定，最终实际门禁逐行闭合 | 计划合同闭合，实施后fresh source逐项重核 | None |

### 12.6 Final verdict
PASS — Ready for user review
