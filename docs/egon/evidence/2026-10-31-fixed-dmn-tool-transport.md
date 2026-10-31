# T3-07 固定只读DMN工具传输执行证据

| 项目 | 记录 |
| --- | --- |
| 执行跨度 | 2026-10-31晚间—2026-11-01凌晨，Asia/Shanghai；文件沿用原计划创建日期命名 |
| 执行计划 | [固定DMN工具传输](../plan/2026-10-31-23-32-fixed-dmn-tool-transport.md)，2026-10-31 23:32创建；strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `60ae80ef1af353c5f239cecfd1ffac324097ffacde2ccafac225cb9ff8a1b405` |
| Baseline | `7345552c52806efd095b8db34ee3b1c5ec611c72` |
| 提交 | 主代理完成最终14路径diff/stage门禁后产生；不预填hash，以Git/交付消息为准 |
| 结果 | 当前未注册只读transport能力及相关合同回归通过；完整T3-07/平台仍Partial |

## 1. 实际实现与安全边界

C的DecisionClient新增EXECUTE_SCOPE/EXECUTE_RESOURCE_KEY，ProcessReleaseClient新增READ_SCOPE/READ_RESOURCE_KEY；PS只让四个现有常量引用它们，保留原`tianji:decision:execute`、`tianji.api.process.decision.evaluate`、`tianji:process:release:read`、`tianji.api.process.release.get`字面值。没有新权限、资源、角色或endpoint。原invocation参数hash提取为ToolDescriptor.parametersSha256，旧Store委托，namespace/bytes和descriptor JSON/reference保持。

AR新增`ReadOnlyDecisionToolTransport`：immutable Request只接受READ_ONLY、nonLEGACY、匹配run/lease、固定schema归一参数与不越run的deadline。downstream requestId使用独立`TIANJI_TOOL_DMN_REQUEST_V1`长度前缀hash绑定owner/run/host invocation UUID/tool fullref，返回canonical UUID文本，correlationId=runId。`VerifiedExecutor`只观察已验证SERVICE身份及有效expiry，其构造不认证。interface层Status为MATCHED/NO_MATCH；DecisionObservation输出bounded canonical primitive不可变map，executionId为canonical UUID文本，保留submittedAt/receivedAt，明确不强求远端DB时间早于本地wall时间。

`TianjiFixedDecisionToolBindings`按tenant/原issuer/type/固定definition key/version/hash及exact tool ref选唯一mapping，最多256，集合不可变、重复slot拒绝，同origin/definition多个工具共享唯一definition SHA。descriptor必须READ_ONLY、backend SHA匹配、执行resourceKey为原DMN execute key，input字段/类型和backend完全相同且全部required。

BackendVO的`bindingSha256()`只hash稳定PS source/resource/base/registration/expected service/release/model/input schema/timeout字段，域为`TIANJI_TOOL_DMN_BACKEND_V1`；不含definition/tool/run refs、凭据版本/tokenId/exp/trustVersion。可先backend→descriptor→definition→mapping，避免循环。URI使用ASCII HTTPS且无query/userinfo/fragment/normalize漂移，issuer/base限2048、resource限512；equals/hashCode按URI ASCII文本，不借URI默认宽松大小写等价放过漂移。

AS新增`TianjiFixedDecisionToolTransport`，复用现有OAuth/身份Bean和实际HttpProcessClient。固定IdpServiceTokenRequest为TENANT且仅原release-read+DMN-execute两scope；捕获OAuth authorizationKey与ACTIVE TENANT trust，再按剩余预算authorizeBounded。用同一个OAuth bearer和固定PS resource调用原manager.authenticate、proof.validate，检查SERVICE类型、proof.jwt.tokenValue等于实际bearer，经原VerifiedPrincipalConverter转换后核issuer/tenant/client/app/resource/TENANT/source3/scopes及token/proof一致性。OAuth与JWT各自的iat/exp不强制彼此相等；有效executor窗口取OAuth token/JWT principal/request/整体窗口最小值。当前key/trust及其URI ASCII前后精确复核；密钥轮换不改变稳定backend hash。

每请求只submit一次，token/JWKS/release/callback/POST共享一个wall+monotonic窗口，取request/run/backend timeout最小值（profile100ms—30s）。自有executor为无队列、1—8并发、非继承虚拟线程；只传播TraceContext，不继承SecurityContext或USER凭据。bearer仅存在request-local AtomicReference，同token supplier无I/O；短publish/clear锁让timeout/close先撤active并清token、再cancel，迟到OAuth即使忽略中断也不能重新填引用。worker/caller finally都清理，Bean只跟踪调用取消句柄/Futures，不缓存或跨请求复用token。close不关闭caller HttpClient、共享SDK或身份Bean。

callback前实际SDK GET固定release并核manifest、exact一项DMN key/modelHash/input schema。OneShot只能当前callback owner thread、dynamic scope、一次、无DB事务、current deadline/key/trust允许时发POST。模型不能选backend/资源/服务身份；逃逸、跨线程、重复、取消/关闭均无第二次操作。POST使用derived requestId/run correlation/归一参数。0outputs/0rules返回NO_MATCH，1/1按descriptor.output校验MATCHED；其他拒绝，不猜首行、不伪造business receipt。

结构有效POST响应即使迟到、source/key/trust变化，仍先作为Observation返回callback；未来Recorder必须在callback内先提交事实再返回。outer callback结束后再postcheck并抑制model value，不提前抹掉已获得的响应。当前没有Recorder/持久nonce/dispatch/native consumer，也没有直接evaluate Bean或配置激活。

传输Failure暴露九个固定kind，enum-only消息、无可附cause/body/status/URI/token。precallback凭据与release不可用/变化分开；POST401/403为DENIED，确定其他4xx为REJECTED（408/429为UNKNOWN），I/O/timeout/5xx为UNKNOWN，malformed2xx为INVALID_RESPONSE；NO_MATCH不是异常。closed/interrupted为CANCELLED，elapsed为DEADLINE_EXCEEDED，无队列拒绝为CREDENTIAL_UNAVAILABLE。callback的既有AgentLedgerException只保留业务code并换固定安全消息/无cause，不泄原私密异常。

所有迁移SQL/配置/权限/依赖/Components/native未改，V13 SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN（跨午夜）

| 阶段 | 实际结果及结束时间（+08:00） | 证据与分类 |
| --- | --- | --- |
| Port RED | 2026-10-31 23:51:22；8项、5 failures、0 Errors | `/tmp/tianji-fixed-dmn-port-bindings-red.log`；reactor在AR失败后停止，AS Bindings未执行，不能重复计入 |
| Bindings独立RED | 2026-10-31 23:53:05；10项、8 failures、2直接stub Errors | `/tmp/tianji-fixed-dmn-bindings-red.log`；真实缺失行为，无编译/环境/fixture错误；不能写成0 Errors |
| Transport RED | 2026-10-31 23:57:28；Transport18项10 failures/6直接CREDENTIAL_UNAVAILABLE stub Errors/2通过；Port8+Bindings10已通过 | `/tmp/tianji-fixed-dmn-transport-red.log`；组件独立RED后实现，真实stub缺失与断言失败如实区分 |
| 首次完整GREEN | 2026-11-01 00:03:43；96通过、0 failures/Errors/skips，exit0，23.585s | `/tmp/tianji-fixed-dmn-transport-green.log` |
| 最终权威GREEN | 2026-11-01 00:09:43；96通过、0 failures/Errors/skips，exit0、BUILD SUCCESS，21.064s | `/tmp/tianji-fixed-dmn-transport-final.log`；包含最后outer RuntimeException安全映射及closed-admission分类修正，测试数量不变 |

最终验证选择：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=ReadOnlyDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,TianjiFixedDecisionToolTransportTest,ToolInvocationStoreTest,ToolExecutionContractTest,ToolExecutionGrantTest,ToolExecutionJsonCodecTest,OrchestrationContractTest,ProcessPermissionIntegrationTest,ProcessControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 模块/测试 | 通过数 | 实际证明范围 |
| --- | ---: | --- |
| C：OrchestrationContract | 5 | 原轻契约兼容 |
| AR：新Port8、旧JsonCodec12/InvocationStore17/Grant8/Contract7 | 52 | 请求/Observation/Failure、旧descriptor JSON/reference/参数hash与准备事实兼容 |
| AS：新Bindings10、新Transport18 | 28 | 实际SDK wire请求/响应解析、受控OAuth/proof转换、固定mapping/oneShot/窗口/取消/迟到与共享资源 |
| PS：PermissionIntegration8、Controller3 | 11 | 四常量引用保持原资源/scope/行为 |
| 合计 | 96 | 当前10类非运行回归，不是整个仓库或平台运行验收 |

测试使用MOCK JDK HttpClient.sendAsync的byte[]响应，由实际HttpProcessClient解析/校验；没有HTTP listener。OAuth及JWT manager/converter为受控替身，不能声称签名、JWKS网络、IdP/PDP或真实密文已验收。未选择数据库测试，0跳过不表示PG运行过。已核当前source、日志、V13摘要及独立实际API审阅，无其他production阻断；临时日志可清理，持久测试/实现是长期证据。

## 3. 局部要求与未完成边界

| 要求 | 状态 | 本步骤证据 | 未扩大范围 |
| --- | --- | --- | --- |
| PLAN-REQ-001 固定只读mapping | Satisfied | stable Backend无循环、exact ref/source/schema/readonly、immutable/URI ASCII及跨tool definition hash测试 | metadata不授身份/权限 |
| PLAN-REQ-002 实际SERVICE协议组合 | Satisfied | 原OAuth/JWT proof/converter、samebearer、完整signed source/two scopes/key/trust/有效expiry测试 | 真实crypto/网络/当前PS撤销权限未验收 |
| PLAN-REQ-003 单次/动态范围/预算 | Satisfied | single window、Trace/noinherit、once/owner thread/noTX、clear-before-cancel/late publish/close测试 | 没有自动native调用或token缓存 |
| PLAN-REQ-004 DMN结果/Observation | Satisfied | 固定release/model/schema、1/1 MATCHED/0/0 NO_MATCH、多行拒绝、latevalid先callback、安全错误 | Recorder必须后续实现callback内commit，当前不假装已存receipt |
| PLAN-REQ-005 兼容及范围 | Satisfied | C/PS四常量同值、descriptor/旧Store hash及Codec回归、14路径 | 无SQL/config/权限/Components/deps/native扩大 |
| 完整T3-07/平台蓝图 | Partial | 当前为未注册固定DMN transport能力 | Recorder/dispatch nonce与receipt、native/审批消费、真实服务/PG/crypto及后续阶段未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | C常量、PS引用、AR JDK/domain port、AS实现 | 轻契约/实现依赖隔离保持，无新模块/框架或Components改变 | None |
| MC-REUSE-001 | Applicable | PASS | 原OAuth key/bounded SDK、manager/proof/converter/registry、HttpProcessClient、schema/hash | 不复制认证/HTTP/摘要基础设施 | None |
| MC-DEP-001 | Applicable | PASS | 14路径无POM/build修改 | 原JDK/SDK/组件足够，无新依赖 | None |
| MC-NAME-001 | Applicable | PASS | Transport/Bindings、Request/BackendVO/ResolvedBindingVO/DecisionObservation/Failure/Test | 技术port/不可变值/安全异常角色明确 | None |
| MC-VALID-001 | Applicable | PASS | fixed mapping/fullref/schema、samebearer proof/full source、key/trust/window/dynamic scope和response | 每个既有身份/SDK/工具边界有正反测试，无模型指定target/身份 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变records、primitive output归一、集合防御复制、Backend URIASCII equality/hash | 真值而非实体，不把VerifiedExecutor构造当认证 | None |
| MC-CONVERT-001 | Applicable | PASS | 直接复用既有VerifiedPrincipalConverter转换已验证JWT | 原转换器拥有身份投影，未创建手工替代Converter层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Adapter/port，无新业务日志Service | Failure固定安全消息，bearer仅局部引用且finally清理 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config注册，caller-owned资源构造注入 | 不改共享SDK/HTTP/identity生命周期，无Qualifier新面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK及原SDK/schema/hash/Trace组件 | 无utility/认证/HTTP/线程框架新增 | None |
| MC-JSON-001 | Applicable | PASS | 实际Jackson HttpProcessClient与typed DTO、mock仅byte[] wire | 单/无命中及schema验证，不用JSON构造假身份或新协议栈 | None |
| MC-TIME-001 | Applicable | PASS | wall/mono min窗口、token/JWT expiry独立取min、remote/localObservation不强排序 | latevalid先callback、outer才抑制输出，取消先clear且迟到OAuth不重填 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/flags | 新库未注册/自动启用，环境键不变 | None |
| MC-PATTERN-001 | Applicable | PASS | AR port依赖倒置、AS原身份/SDK Adapter、动态oneShot能力 | 复用真实变化点，不造第二执行/验证引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 14路径、V13固定、C/PS同值、Components/native不改 | 无权限/SQL/config/dispatch扩大 | None |
| MC-TEST-001 | Applicable | PASS | 三有效RED如实含stub Errors；最终96项通过，旧C/PS/AR兼容 | 非运行证明完成，无listener/真实crypto/PG伪报 | None |
| MC-BLOCKER-001 | Applicable | PASS | 最终source/log及独立API审阅无其他production阻断 | 当前transport slice可提交，完整T3-07/运行边界保留 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | port/Transport/Bindings和VO/Request/Observation/Failure/Test | source/调用者编译及96项GREEN | 技术角色/值/异常命名明确 | None |
| Rule 2 | Applicable | PASS | readonly/schema/source/URI/profile、exact actual proof/principal/scopes、oneShot/window/SDK结果 | 新Port8/Bindings10/Transport18及原60项回归 | 私有安全/协议hand-off具体验证，未新设不受控HTTP输入 | None |
| Rule 3 | Applicable | PASS | 真不可变request/backend/observation/executor及primitive map；原converter复用 | immutable/hash/URI/canonical/状态组测试 | 非实体/可变POJO，无重复模型或手工Converter层 | None |
| Rule 4 | Not applicable | N/A | manual技术SDK Adapter和port，无新业务Bean/DI/config | 当前14路径/原生命周期核对 | 不伪造业务Service/Lombok注入验收 | None |
| Rule 5 | Applicable | PASS | JDK/现SDK/schema/hash/Trace，无POM改动 | imports/实际编译及范围审阅 | 无工具库或框架新增 | None |
| Rule 6 | Applicable | PASS | 原Jackson SDK解析实际typed bytewire，C只常量同值 | 旧Codec12/Store17/C/PS回归与新SDK负例 | 不造JSON认证/协议栈，未改变原descriptor reference/hashbytes | None |
| Rule 7 | Not applicable | N/A | 无配置/profile/启用flag改变 | 14路径审阅 | 配置结构保持，新能力未自动注册 | None |
| Rule 9 | Applicable | PASS | port依赖倒置/SDK Adapter+callback动态单次能力 | 生命周期/once/secret cleanup/结果顺序测试 | 使用实际变化点，无新通用执行或认证层 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/mono、expiry min、跨源Observation保留 | 不同OAuth/JWT timestamps/late/cancel/deadline测试 | 不猜remote/local时间排序，不用java.util日期或默认zone | None |
| Rule 11 | Applicable | PASS | 原C/PS/AR/AS边界、同值四常量、冻结V13/Components不动 | 96项相关回归，V13仍f0c642…26b3 | 无schema/deps/权限/native/dispatch架构扩张 | None |

## 6. 未执行事项与处置

没有真实HTTP listener、IdP/PDP/JWKS网络或crypto验证、数据库、业务服务或模型/MCP运行验收。当前PS当前撤销权限仍由实际服务端执行；本地proof只是已验证身份，不是替代PS当前权限决策。没有Recorder持久nonce/receipt、dispatch/native/审批消费或配置consumer；有效Observation先交callback不等于已实际提交业务receipt。

没有新仓库scripts/work或未定scratch harness，文档生成脚本已清理，测试保留在对应持久类。未自动启动应用/数据库/容器/浏览器/GUI/native scheduler/长期runtime；未激活配置、注册/激活权限、授角色、部署、push、PR或release。

本报告作者只创建该证据文件，不改代码/README/plan/SQL，不运行build或提交。主代理完成最终14路径diff和显式stage审查后产生本逻辑commit，hash以Git为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前固定只读DMN传输能力通过非运行验证；完整T3-07和平台验收仍未完成。
