# 固定只读MCP请求合同与HOST版本绑定

| Field | Value |
| --- | --- |
| Document | `2026-11-01-17-49-fixed-readonly-mcp-contracts.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 17:49 Asia/Shanghai` |
| Updated | `2026-11-01 18:06 Asia/Shanghai` |
| Owner | 原用户授权；Root实施与验证 |
| Repository | `tianji-service` |
| Scope | 7paths；MCP port/immutable registry/fingerprints，无网络consumer |
| Source Requirement | 原蓝图T3-07及§12.1/13.1/13.2/17 |
| Baseline Revision | `main@55ffe9d1b3216cc422defff3a305d1c98273c721` clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原用户明确继续授权，非Accepted文档 |
| Spec Revision | 2026-11-01；SHA-256 `e0e9e5b5bd86c8209bc96ead36d125ef15f0d7f11e30872c07828c8708085789` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [AA实际装配](2026-11-01-14-51-approved-process-tool-assembly.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
为后续安全MCP原生传输先提供稳定、可验证的值合同与固定HOST映射。沿用原ToolDescriptor/ToolSchema、generic VerifiedExecutor与OrchestrationRequestDigests；不以DMN观察冒充MCP。元数据显式固定非计费readonly、scope/身份/endpoint/工具文档/本地schema与版本。新增库不自动挂载工具或网络；独立可验证的guards/hash行为一Step一commit。

## 2. Target Spec and Effective Design
原蓝图§12.1统一工具描述/原生薄Adapter、MCP身份与schema变更须验证重发布；§13.1身份不来自模型；§13.2run固定版本；§17伪造与跨tenant拒绝。现Tianji仅Process/DMN ports，现Yuheng d2682b4已补真实SERVICE scopeconsumer。直接复用DecisionObservation会伪造DMN provenance；新MCP值合同必要。SDK日志/HTTP字节与私密响应边界已源审，不纳本Step实现，不宣称MCP执行ready。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/17 | 独立readonly MCP port，无DMN provenance；请求合法化/namespace/安全结果/必需bounded SAM | §7对应7paths |
| PLAN-REQ-002 | §12/13/17 | finite不可变HOST backend显式nonBillable=true、Stable JSON身份与local schemas/pin完整 | §7对应7paths |
| PLAN-REQ-003 | §12/13/17 | 完整owner/definition/fullToolref/hash/risk/resource/schema闭合，重复与drift failclosed | §7对应7paths |
| PLAN-REQ-004 | §12/13/17 | 远端完整tool JSON文档规范指纹有深度/节点/UTF8/数值/字节界限，非授权凭据 | §7对应7paths |
| PLAN-REQ-005 | §12/13/17 | 无SDK调用/网络/凭证/DB/journal/native/AS启用；7scope验证分步单提交 | §7对应7paths |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
Tianji55ffe9d clean、23SQL immutable；Yuheng d2682b4 clean/69source tests。现port/registry模式、ToolSchema/Orchestrationhash/API已全文查。无新增依赖/层/表/权限。
### 4.2 Test-first strategy
仅新interface/record/signature/UOE允许供编译；Port直接guards/namespace/output实际RED后GREEN，再Bindings有效RED后GREEN。编译/fixture失败不计RED，不因新库未挂载而伪造执行。
### 4.3 Sequential and parallel boundaries
Root ownsPort/Test和README/Plan/EVI；worker ownsBindings/Test2paths，PortGREEN后才编辑，之前可仓库外准备。不是独自在代码库，保他人修改，Root统一Maven前全部Java冻结，无owner自建服务或commit。
### 4.4 Commit boundaries
一个值合同与HOST pinning语义切片7paths，全部门禁后立即commit `feat(agent): pin fixed read-only MCP tool contracts`，不提前写实际HTTP或journal。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Element | Source necessity | Existing capability | Direct/reuse alternative | Decision |
| --- | --- | --- | --- | --- |
| 独立port | MCP原生/无DMN假来源 | generic VerifiedExecutor已由START复用 | 复用identity observation，新MCP自身结果 | Implement无新executor类型 |
| immutable backend | 版本/远端工具不可动态扩大 | FixedDecisionBindings完整origin/hash | 同形态新MCP profile无父绑定抽象 | Implement必要metadata |
| 指纹算法 | Remote完整tool/schema drift | JDK/现Jackson/Orchestrationhash | private bounded canonical tree+现digest | reuse无通用hash框架 |
| 网络/凭证/SDK/registry新层 | 后续执行，不属metadata | SDK SPI/credential helper已有 | 本Step不实现/不新增deps | 无过度consumer/无新表 |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| port values | PLAN-REQ-001 | PortTest | 编译声明 | validated Request/Observation | registry/后续transport | Step1 |
| HOST registry/hash | PLAN-REQ-002/003/004 | BindingsTest | portGREEN | immutable pinnedmetadata | 后续SDK transport | Step1 |
| docs/gates | PLAN-REQ-005 | static/回归 | 两GREEN | reviewable7pathcommit | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现AR application/tool +AS integration/process patterns | 现技术结构保持 | 新AS integration/mcp同层，不增业务层/CRUD | Step1 MC-ARCH-001 |
| Model/validation | immutable record/constructor guards/ToolSchema.values | 值对象record与所有新handoff严格验证 | 请求/输出/后端/JSON界限+copy；无普通实体 | Step1 MC-VALID-001/MC-MODEL-001 |
| Bean/log/conversion | 普通技术port/registry无Spring业务Bean/Converter | 不新增注入/日志/映射层 | redacted toString/fixedcauseless错误 | Step1 MC-BEAN-001/MC-LOG-001 |
| JSON/time/pattern | 现Jackson/JDK/length-delimiteddigest/FactoryAdapter契约 | canonicalJSON为fingerprint非凭证 | 基于现registry模式，UTC Instant only | Step1 MC-JSON-001/MC-TIME-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 身份observation | Decision.VerifiedExecutor/START Callback | actor+expiry仅通用SERVICE标识 | 可直接复用不假造DMN | reuse | None | Step1 MC-REUSE-001 |
| bounded schema | ToolSchema fields/required/values | closed16primitive/UTF8/integer guards | 无public hash，localdigest可由public字段投影 | reuse | None | Step1 MC-DEP-001 |
| metadatafingerprint | OrchestrationRequestDigests.hash | 稳定type+UTF8长度前缀/Map排序 | private JSON规范化后用现hash | reuse | None | Step1 MC-UTIL-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §12/13/17 | Request/CallObservation/BackendVO/BindingVO/ResolvedBindingVO语义类型，port和technicalregistry沿现模式 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 2 | §12/13/17 | ToolSchema.values闭合input/output、constructor invariants、完整slot/hash/schema重核与JSON硬边界 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 3 | §12/13/17 | 所有新数据为immutable value record/deepcopy；无普通实体或对象Converter | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 4 | §12/13/17 | 无新Spring业务Bean/DI/logger；普通技术port与profile构造、redacted/fixederror | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 5 | §12/13/17 | JDK集合/URI/Duration/BigDecimal、现Jackson与Orchestrationdigest，无新依赖 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 6 | §12/13/17 | 新JSON规范指纹使用Jackson已归一化树；枚举仅内部无persist/前端/ordinal | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 7 | §12/13/17 | 任何YAML/profile/启用均不改，新增库无默认运行行为 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 9 | §12/13/17 | 现fixed Registry+port Adapter合同，固定后端variation，无新父绑定抽象/引擎 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 10 | §12/13/17 | Request Instant upperbound与Backend Duration100ms..30s，未来oneShotµ合同明确未执行 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |
| Rule 11 | §12/13/17 | 既有AR application/tool与AS integration同层，23SQL/旧MP-SDJ/Outbox/CRUD均不动 | §7 exact7paths | §6/逐文件完整合同 | source/REDGREEN/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Request.deadlineAt/run.deadlineAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: 技术时刻 | Not applicable: 无UI | metadata仅deadline≤run；不构造当前authority | 原Instant精度，不引Clock/不floor | 不改旧port | Step 1 TEST-001 |
| TIME-002 | Backend.timeout | Duration | Duration | Not persisted | Internal | Not applicable: 技术duration | Not applicable: 无UI | 100ms..30s，hash包括规范Duration.toString | java.time Duration | 后续transport不得重启wall+mono预算 | Step 1 TEST-002 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransportTest.java | 独立MCP请求/结果/失败合同RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransport.java | 不含SDK/认证实现的只读MCP port值合同 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindingsTest.java | HOST绑定、全部版本维度与文档指纹RED | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindings.java | 有限不可变MCP后端与独立远端工具文档指纹 | Step1 |
| MODIFY | README.md | 准确说明metadata/port可用与真实传输后续边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-17-49-fixed-readonly-mcp-contracts.md | 锁定7路径/依赖/值合同/门禁 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-fixed-readonly-mcp-contracts.md | 真实RED/GREEN与局部完成界限 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Port exact.** 新ReadOnlyMcpToolTransport：`EXECUTE_RESOURCE_KEY="tianji.mcp.tools.call"`；`<T>T withVerifiedCall(Request,Callback<T>)`。Request六字段(run,lease,descriptor,Map arguments,UUID invocationId,Instant deadlineAt)，RO/资源key精确、nonlegacy、run/lease匹配、deadline≤run，参数用descriptor.input.values规范化/copy。parametersSha256复用descriptor；downstreamRequestId仿现Decision用SHA前16bytes UUID但domain为TIANJI_TOOL_MCP_REQUEST_V1，含owner identity fingerprint/run/hostUUID/fulltoolref；correlationId=runId。无Clock与DB/真实authority。Callback复用generic Decision.VerifiedExecutor，仅同scope identity observation，不认证。OneShotCall是必需 `CallObservation call(Instant callDeadlineAt)` SAM，无noarg；后续实现必须strict future/µ/noextension originalwall+mono/credential-window，并在Recorder BEGIN后才执行整个MCP lifecycle；本Step只定义该接口契约，不声称已实现时间/IO fence。

CallObservation(Status status,Map output)，Status=SUCCESS/TOOL_ERROR/UNKNOWN；success只bounded primitive map，错误/unknown必须empty，copy+redacted。复用ToolSchema推导字段类型后values验证，拒强转浮点/超界/非法Unicode。FailureKind=CREDENTIAL_UNAVAILABLE/PROFILE_UNAVAILABLE/PROFILE_CHANGED/OPERATION_DENIED/OPERATION_NOT_SENT/OPERATION_RESULT_UNKNOWN/OPERATION_INVALID_RESPONSE/CANCELLED/DEADLINE_EXCEEDED；Failure仅kind固定message/noCause，不携rawSDK异常。所有record request/observation redacted。

**Backend exact19.** BackendVO(long tenantId, URI issuer, URI endpoint, URI resourceUri, String registrationId, String expectedClientId, String expectedAppId, String expectedSourceBiz, String expectedSourceApp, String expectedEnvironment, Set<String> businessScopes, String expectedServerName, String expectedServerVersion, String remoteToolName, String remoteToolDocumentSha256, ToolSchema inputSchema, ToolSchema outputSchema, Boolean nonBillable, Duration timeout)。issuer/endpoint≤2048、resource≤512，host/ASCII原样/已normalize/无userinfo-query-fragment；issuer/resource必须HTTPS，endpoint可HTTPS或仅loopbackHTTP（localhost/127.0.0.1/::1按现Start helper），不followredirect。所有identity text按现profile规范trim原样/UTF8/无控制/≤128；serverName/version文本非空canonical≤128；remoteToolName精确SDK允许 `[A-Za-z0-9_.-]{1,128}` 不调用会打印rawname的SDK validator。businessScopes1..64原样 `[A-Za-z0-9._:-]{1,128}`，拒identity scopes六项，copy；remoteToolDocumentSha64lowerhex；schemas非null复用ToolSchema；nonBillable必须Boolean.TRUE（null/false拒，不从hints/args推断）；timeout100ms..30s。URI equals/hashCode明确toASCIIString，所有toString脱敏。

Mode/protocol是固定常量 `TRANSPORT_MODE="YUHENG_STABLE_JSON_POST"`/`PROTOCOL_VERSION="2025-11-25"`，不是多余可配置策略；MAX_RESPONSE_BYTES=1048576、MAX_REMOTE_TOOL_DOCUMENT_BYTES=131072、JSON depth8/nodes4096/container256、rawNumberBits512/normalizedNumberPrecision128/normalizedScale128也入backend fingerprint。bindingSha256复用OrchestrationRequestDigests，`TIANJI_TOOL_MCP_READ_ONLY_BACKEND_V1`域、全部19fields+常量；scopes按排序primitive map表达，localinput/output schema分别由fields+每fieldrequired boolean做`TIANJI_TOOL_MCP_SCHEMA_V1`独立hash。不把ToolDescriptor.contentSha写进backend hash（descriptor已含policy.bindingHash，避免环）。字段顺序/类别稳定，hash不是authority。

**Registry exact.** constructor(Collection descriptors,Collection BindingVO)均1..256；按现FixedDecision pattern拒null/重复key@version、重复完整origin(toolkey/version)slot、同origin定义hash drift。BindingVO八字段tenantId/ownerIssuerFingerprint/principalType/definitionKey/version/contentSha/toolRef/backend，tenant必须等backendtenant，hash/key/text校验同现profile。ResolvedBindingVO核fullref、entiredescriptor、RO、exactMCPresource、input/output完整equals、backendSha一致。resolve(Request)按完整actor(namespace含USER/SERVICE/issuer/tenant)/definition/toolslot与当前run hash重核。ownerIssuer可与businessIssuer不同，仍需sameTenant；映射只是HOST metadata不是当前PDP、真实SERVICE或sealedcapability。

**Remote tool fingerprint exact.** public static `remoteToolDocumentSha256(Map<String,Object> document)` accepts JSON object data only，不解析网络/不接受任意POJO或调用其toString。规范化object keys按TreeMap递归排序、arrays保序、String/Boolean/null保持；所有JDK integral/BigInteger/BigDecimal及finiteFloat/Double规范为stripTrailingZeros BigDecimal（以其十进制文本，precision≤128/abs(scale)≤128，BigInteger及BigDecimal.unscaledValue()都先bitLength≤512防巨数转换/去零放大）；非finite/未知Number/Object拒。深度≤8（root=1）、总节点≤4096（keys+values/containers都计）、每container≤256、全text UTF8累计≤131072且每String≤131072chars/UTF8可编码，异常UTF8 surrogate拒；序列化Jackson后UTF8文档≤131072。Jackson不写BigDecimal plain展开；Map/list深copy无需保留caller数据，rejectcycle经depth/nodebound。用OrchestrationRequestDigests.hash([TIANJI_MCP_REMOTE_TOOL_V1, canonicalJson],emptyMap)，noCause固定错误。只产生指纹，不把未验证metadata当认证，也不声称实现通用JSONschema验证。全文tool doc pin涵盖input/output存在性、annotations/meta/title/description变化；wire缺outputSchema不能伪造远端声明，本地output约束另存。

**Non-goals/compat.** 不引SDK调用/HTTP/凭证helper可见性/日志全局修改/Bean配置/journal/native/权限/DDL；这些随后按已固定合同实现。现23SQL/POM/旧Process ports与现所有调用者不改。SDK2.0 raw日志需后续安全SPI/opaque投影，当前metadata不能被称为传输privacy proof。YuhengStable只普通JSON subset可后续接，SSE/RC/tasks等不在本合同支持声明。无generator-owned CRUD变更，架构保既有技术profile。

**CLAR-001 raw数值资源界限。** 512-bit raw magnitude限制同时用于BigInteger和BigDecimal.unscaledValue，在stripTrailingZeros前检查；否则巨大尾零即使最终规范为1仍可能导致无界工作。明确拒绝这种raw表示，非增加随意rawprecision/scale限制；小unscaled值的极端scale按规范后直接[-128,128]判断，zero可规范成0。测试包含巨尾零、scale MIN/MAX、极端scale zero和普通等值数值。该界限参与backend固定指纹，未扩授权或公开执行能力。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 固定readonly MCP值合同与HOST完整版本指纹
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: Tianji55ffe9d/Yuhengd2682b4均已commit/clean；现ToolSchema/Orchestrationhash/VerifiedExecutor。
- Baseline state: 只有DMN/START ports与fixed profiles；MCP无独立metadata合同。
- Observable outcome: MCP Request/Observation/Failure与精确HOST registry guards/hash可被实际单测证明，不依赖SDK/网络。
- End state: 后续nativeSDK transport可基于固定接口构建，现runtime仍未启用MCP。
- Test-first gate: Required — Port与Bindings各真实行为RED后GREEN。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransportTest.java`
- Purpose: 独立MCP请求/结果/失败合同RED。
- Symbols: Request/CallObservation/Failure。
- Repository evidence: 现ReadOnlyDecisionToolTransportTest与其Request/VerifiedExecutor模式。
- Dependencies and consumers: compile-only interface/record/UOE后有效直接构造RED。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
assertRequestRequiresReadOnlyMcpResourceAndExactRunLeaseNonlegacyActor();
assertArgumentsAreClosedNormalizedAndCopiedAndDigestIsStable();
assertMcpRequestUuidNamespaceDiffersFromDmnAndOwnerNamespaces();
assertObservationBoundsAndEmptyErrorOutputsAndRedactedFailure();
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransport.java`
- Purpose: 不含SDK/认证实现的只读MCP port值合同。
- Symbols: Request/Callback/OneShotCall.call(Instant)/CallObservation/Status/FailureKind。
- Repository evidence: 现Decision port通用VerifiedExecutor已被START复用，无DMN来源字段适合复用。
- Dependencies and consumers: File1有效RED后guards/hash最小GREEN。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
interface ReadOnlyMcpToolTransport { withVerifiedCall(Request, Callback); }
Request { validateNonlegacyRunLeaseReadOnlyResourceDeadline(); arguments=descriptor.input().values(arguments); }
downstreamRequestId = domainSeparatedUuid(ownerFingerprint,runId,invocationId,fullToolRef,"TIANJI_TOOL_MCP_REQUEST_V1");
CallObservation { normalizeBoundedPrimitiveOutput(); requireEmptyOutputForTOOL_ERROROrUNKNOWN(); }
OneShotCall.call(Instant deadlineAt); // required bounded SAM; implementation follows in later Step
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindingsTest.java`
- Purpose: HOST绑定、全部版本维度与文档指纹RED。
- Symbols: BackendVO/BindingVO/ResolvedBindingVO/remoteToolDocumentSha256。
- Repository evidence: 现FixedDecision/Start/Approval Bindings成熟finite+origin+hash模式。
- Dependencies and consumers: portGREEN后新binding signatures/UOE compile only，合法metadata fixture先RED。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
assertFiniteRegistryFullOwnerDefinitionToolAndSchemaClosure();
assertBackendRejectsMissingNonbillableAndUnsafeUriScopesNamesBounds();
assertEveryBackendFieldAndConstantsEnterStableNoncyclicFingerprint();
assertCanonicalToolDocumentHashOrdersObjectsPreservesArraysTypesAndBounds();
assertMutableCollectionsCannotChangePinnedIdentityAndToStringIsRedacted();
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindings.java`
- Purpose: 有限不可变MCP后端与独立远端工具文档指纹。
- Symbols: BackendVO19fields/BindingVO8fields/ResolvedBindingVO/resolve/bindingSha256。
- Repository evidence: 实际FixedDecisionBindings30–122 origin/version map、URI ASCII equality、OrchestrationRequestDigests。
- Dependencies and consumers: File3有效RED后实现；不实例化SDK/HTTP或凭证。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
constructFiniteDescriptorAndBindingMapsRejectDuplicateVersionsSlotsAndOriginHashDrift();
resolve(Request) { requireFullNonlegacyOwnerDefinitionAndExactDescriptorRef(); requireReadOnlyMcpResourceSchemasAndBackendHash(); }
BackendVO { requireExplicitNonBillableTrueAndFixedStableProtocolIdentity(); validateExactUrisBusinessScopesNamesSchemasAndTimeout(); copyAllState(); }
bindingSha256 { hashMcpDomainConstantsAndAll19FieldsWithIndependentLocalSchemaAndSortedScopeDigests(); }
remoteToolDocumentSha256(Map) { normalizeBoundedJsonTree(); serializeCanonicalTreeWithJackson(); return OrchestrationRequestDigests.hash(domainAndCanonicalJson,emptyMap); }
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 5 — `MODIFY README.md`
- Purpose: 准确说明metadata/port可用与真实传输后续边界。
- Symbols: MCP合同/指纹/非计费声明/无运行启用。
- Repository evidence: 当前README已说明Process/AA consumer且MCP仍pending。
- Dependencies and consumers: 实际源码与日志后写，只记录当前库。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
documentFixedMcpContractAndAllBackendIdentityDimensions();
documentRemoteDocumentFingerprintIsNotAuthenticationOrSchemaAttestation();
documentNoNetworkSdkJournalNativeOrConfigurationActivationInThisStep();
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 6 — `CREATE docs/egon/plan/2026-11-01-17-49-fixed-readonly-mcp-contracts.md`
- Purpose: 锁定7路径/依赖/值合同/门禁。
- Symbols: V5/5aliases/17MC10Rules。
- Repository evidence: 原蓝图§12/13/17与已提交Tianji55ffe9d/Yuhengd2682b4。
- Dependencies and consumers: Root全文/strict/resources/checklist/hash后SourceGO。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
lockMetadataAndPortScopeAt55ffe9d();
requireSeparatePortThenRegistryActualRedGreen();
requireSevenPathCommitBeforeAnyNetworkTransportSource();
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

#### File 7 — `CREATE docs/egon/evidence/2026-11-01-fixed-readonly-mcp-contracts.md`
- Purpose: 真实RED/GREEN与局部完成界限。
- Symbols: 5requirements/17MC10Rules。
- Repository evidence: 真实source/stdout，PENDING初始。
- Dependencies and consumers: 实际验证后填写，不预填结果。
- Why now: §4.6依赖与§6 exact合同；新行为实际RED后GREEN，不提前执行网络。
- Contract/signature changes: §6完整新port/Backend19/Registry/7scope合同；不改旧public API。
- Input/output and state mapping: §6 exact规范化/字段/namespace/side-effect-free metadata；输出脱敏。
- Error and edge behavior: null/重复/版本drift/unsafeURI/scope/schema/JSON界限固定failclosed，noCause/rawbody，metadata非auth。
- Standards impact: MC-ARCH-001/MC-REUSE-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-SCOPE-001/MC-TEST-001，逐项§12.5。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§6 immutableVO/typedguards/Jackson/JDK/现registry模式，不引Bean/实体/转换层。
- Implementation pseudocode:
```java
recordActualBehaviorRedAndCompletedGreen();
EvaluateEachManualAndLiteralRuleOnFrozenDiff();
RecordSevenPathCommitAndUnperformedRuntimeBoundary();
```
- Verification contribution: TEST-001/002/003实际source/log，编译/fixture错误不算RED。
- After this file: 本贡献可review，后续SDK/journal/native仍不声称已实现。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`，strict从workspace。
- Verification command: §8 exact source reactor focused+regression。
- Expected result: 所有命名测试PASS、zero skip；metadata拒绝边界与兼容明确。
- Failure returns to: Port回File1/2，Registry/hash回File3/4，文档回File5；新范围先修Plan。
- Completion criteria: 7scope/23SQL/17MC10Rules/独审/strict/resources/checklist/cached通过后commit。
- Rollback: 本新库无持久/网络副作用；停用后续consumer，保原Process/DMN，旧history不可删改。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransportTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ReadOnlyMcpToolTransport.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindingsTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/mcp/TianjiFixedReadOnlyMcpToolBindings.java、README.md、docs/egon/plan/2026-11-01-17-49-fixed-readonly-mcp-contracts.md、docs/egon/evidence/2026-11-01-fixed-readonly-mcp-contracts.md
- Commit: `feat(agent): pin fixed read-only MCP tool contracts`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Expected evidence |
| --- | --- | --- |
| TEST-001 | newPortTest | validRequest/guards/namespace/primitiveoutput/failures实际RED→GREEN |
| TEST-002 | newBindingsTest | exactallfields/origin/hash/nonbillable/canonicalJSONbounds实际RED→GREEN |
| TEST-003 | 现相关metadata兼容+7scope/23SQL/17MC10Rules/strict | source全部PASS，非SDK/网络/IAM验收 |
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
 -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
 -B -ntp -DskipTests=false \
 -Dtest=ReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolBindingsTest,ReadOnlyDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,ToolActionRequestTest,ToolRegistryTest \
 -Dsurefire.failIfNoSpecifiedTests=false \
 -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
 > /tmp/tianji-fixed-readonly-mcp-contracts-final.log 2>&1
```
每unit先focused selector/独立log，Root统一Maven/所有Java冻结。不新建runtime/harness；用现wrapper/verify script与JUnit断言覆盖。新named tests存在后才选，现testclass名须source preflight核对。保存23SQL hash/exact7paths/unknownsource-body无数据输出，strict/resources/checklist/独立review。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/SQL/权限/角色/配置启用/旧API变化；新增metadata库不自动生效。不复制DMN provenance，不承诺billing/一般MCP兼容。实际transport/journal/native后续Step独立测试/提交；不可在本Step预写。原source角色边界和用户其他工作保持。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §12/13/17 | Step 1 | §7 exact7paths | TEST-001/002/003 | 独立readonly MCP port，无DMN provenance；请求合法化/namespace/安全结果/必需bounded SAM |
| PLAN-REQ-002 | §12/13/17 | Step 1 | §7 exact7paths | TEST-001/002/003 | finite不可变HOST backend显式nonBillable=true、Stable JSON身份与local schemas/pin完整 |
| PLAN-REQ-003 | §12/13/17 | Step 1 | §7 exact7paths | TEST-001/002/003 | 完整owner/definition/fullToolref/hash/risk/resource/schema闭合，重复与drift failclosed |
| PLAN-REQ-004 | §12/13/17 | Step 1 | §7 exact7paths | TEST-001/002/003 | 远端完整tool JSON文档规范指纹有深度/节点/UTF8/数值/字节界限，非授权凭据 |
| PLAN-REQ-005 | §12/13/17 | Step 1 | §7 exact7paths | TEST-001/002/003 | 无SDK调用/网络/凭证/DB/journal/native/AS启用；7scope验证分步单提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Evidence/resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | DMN provenance不能复用 | 新MCP自身结果，generic identity可复用已有START先例 | Root | Closed |
| DEC-002 | backend↔descriptor循环hash | remote完整tool doc独立hash/local schemas独立hash，无descriptorSHA输入 | Root | Closed |
| DEC-003 | SDK日志/远端缺outputschema | 本Step仅metadata，后续安全SPI；output本地约束非远端证明 | Root | Closed界限 |
| RISK-001 | HOST metadata被当authority | 所有值/映射文档及redaction明确，实际credentials/PDP后续 | Root | Tracked tests |
| RISK-002 | JSON递归/巨数/非法对象导致资源放大 | §6精确node/depth/bytes/number界限，不反射任意对象 | Root | Tracked tests |

## 12. Review and Acceptance
### 12.1 Original requirement fidelity
原蓝图授权内固定MCP工具身份/schema/风险政策，分步提交；不是新网络框架或身份机制。
### 12.2 Spec consistency
只必要readonly合同/固定metadata，复用原模式，无fetch-forward API/新层/表/Bean，未将部分库当完整MCP。
### 12.3 Repository executability
相邻port/registry/Schema/hash已查，7paths新文件已核absence；Root全文/strict/source符号preflight后GO，tests编译前只最小声明。
### 12.4 Test and release completeness
仅source unit回归，无真实SDK/network/IAM/SQL启动/权限激活/角色grant/push/PR；23旧SQL原样。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR application/tool+AS integration，新增mcp同技术层无CRUD；§4.7 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-REUSE-001 | Applicable | PASS | Decision.VerifiedExecutor/START复用先例、ToolSchema.values、FixedDecisionRegistry、Orchestrationhash；§4.7 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-DEP-001 | Applicable | PASS | 现POM中Jackson/JDK/Framework充分，新库无新artifact/SDK引入 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-NAME-001 | Applicable | PASS | 新ReadOnlyMcp port与Backend/Binding/Resolved VO，显式字段/函数名见§6 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-VALID-001 | Applicable | PASS | new请求/结果/后端/完整origin、Schema以及canonicalJSON depth/node/byte/number invariants；§6 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-MODEL-001 | Applicable | PASS | immutable record/Map-Set-List defensivecopy，不增普通实体或Lombok冲突 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无实体投影/Converter，metadata索引非业务对象转换 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-LOG-001 | Not applicable | N/A | 新技术port/registry无业务日志，redacted/fixederror | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-BEAN-001 | Not applicable | N/A | 无新managedBean/DI，普通metadata构造 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/SpringJackson现工具；bounded私有规范化服务于单一MCP指纹，不增utils框架 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-JSON-001 | Applicable | PASS | Jackson规范tree指纹/现length-delimitedhash；不改旧wire/enumencoding | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-TIME-001 | Applicable | PASS | TIME001/002与§6明确metadataInstant/Duration，当前无Clock/DB/IO、不伪造执行时间fence | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-CONFIG-001 | Not applicable | N/A | 不改任何YAML/profile/启用；只新库 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-PATTERN-001 | Applicable | PASS | 当前fixed registry+port Adapter契约复用，后端/版本选择具体变化点；无新层 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-SCOPE-001 | Applicable | PASS | exact7paths/23旧SQL/POM/AS配置/journal/native均保持只读 | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-TEST-001 | Applicable | PASS | Port与Bindings有效RED、六类命名回归/源码静态检查见§8，fixture失败不冒RED | 计划合同闭合，实施后fresh source逐项重核 | None |
| MC-BLOCKER-001 | Applicable | PASS | hash循环/JSON资源放大/metadata authority边界均明确，所有决策§6/11闭合 | 计划合同闭合，实施后fresh source逐项重核 | None |

### 12.6 Final verdict
PASS — Ready for user review

实际执行：Port7/Bindings14有效RED后GREEN；最终六类48PASS0fail/error/skip。23SQL字节不变、7scope/独审与全部门禁见evidence；本Step只metadata值合同，不声明MCP执行ready。
