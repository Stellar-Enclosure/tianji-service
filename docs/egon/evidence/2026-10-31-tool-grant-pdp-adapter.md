# T3-07 固定工具绑定与PDP适配执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [工具grant/PDP适配](../plan/2026-10-31-20-23-tool-grant-pdp-adapter.md)；strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/13/14、§19 T3-07 |
| 主设计实施前SHA-256 | `11c2240c7fb2bc7bc198637b4883bd02e3c6c11761feedc56d0aaa8848d1b71f` |
| Baseline | `ff53ff221fa93ac1cbf90b0b86e05bd0c909ec99` |
| 提交 | 主代理完成最终diff/stage门禁后产生；不预填尚未产生的hash，以Git及交付消息为准 |
| 结果 | 当前未接线技术库与受控协议回归通过；完整T3-07/平台仍Partial |

## 1. 实际行为与边界

`TianjiToolGrantBindings` 对注册工具和deployment mapping做非空/最多256项、防御复制、完整注册ref/hash、重复origin/definition/tool槽和同origin定义hash漂移校验。它复用`ToolRegistry.visible(snapshot)`，核stored run/authorization与固定definition/hash/policy，给每个visible工具要求恰好一个mapping；预算0/隐藏版本返回空集合，活跃缺mapping不能被当空权限忽略。SERVICE Subject只由既有`services.require(run,snapshot.processLink())`产生，USER不借用SERVICE来源。输出不可变`ResolvedToolResult`及原`ToolExecutionGrant.BindingVO`；配置值和解析值本身不授权限。

`TianjiToolGrantAuthorizer` 实现现有授权port，公开构造器一次性复用有限`TianjiExecutionGrantAuthorizer.clients/BoundClient`工厂；不按每次调用创建HTTPclient。SDK没有close接口，适配器只关闭自有executor。该executor使用非继承虚拟线程、SynchronousQueue无队列、并发1—8、timeout100ms—10s；每次check/authorizeRun只submit一次，wall与monotonic共享一个整体窗口，submit/get前后和内部步骤均重核，TraceContext显式传入/恢复。关闭、中断、迟到不能变为ALLOW；忽略中断的底层依赖在真正返回前仍占容量，测试覆盖这一边界。

活动`check`的三个ToolBoundary均要求可信stored material仍RUNNING；`authorizeRun(CLAIM)`只QUEUED，`authorizeRun(MODEL)`只RUNNING，取消/终态/其他状态UNAVAILABLE。Bindings.resolve只核材料与映射，不自行充当生命周期fence。后置比较的是不变authorization、固定snapshot definition/hash/effective policy/processLink及resolved binding，不比较整个RunDTO的revision/usage/event sequence；合法并行进度不会被误认授权漂移。实际lease owner/epoch/live expiry仍由后续owning Store在DB锁内检查，本适配器不制造lease或读取私有lease SQL。

适配器从同源ledger取exact material、解析固定工具、核请求ref/backend及read无intent/write有intent，再取独立capability证据。USER调用现有SDK `decide`，SERVICE调用`decideService`，Expectation绑定独立capabilityId、完整Binding SHA、purpose、AS token audience、`SESSION_REQUIRED=false`与原run deadline；禁止拿actionIntentId或请求backend hash充当PDP binding。SERVICE六元组clientId/appId/businessCode/applicationCode/environment/resourceUri均按原来源传入并有整体断言。

ALLOW响应grantId必须等于存储票据，decision和sealed evidence窗口都覆盖请求截止，decision expiry不超过sealed expiry。返回前复核issuer ACTIVE/TENANT/tenant及精确issuer/JWKS URI、SERVICE来源、固定material/resolved binding、完整Evidence.equals（含epoch/cipher/精确URI）、DBclock、wall/mono窗口和关闭/中断状态。明确PDP拒绝也只有后置来源未变才能形成DENY，否则UNAVAILABLE。已解析可信集合但ref/backend或intent类型不符为DENY；无法解析/缺mapping/来源失效为UNAVAILABLE。

`authorizeRun` 在一个整体窗口内遍历所有visible工具，每个只调用一次PDP，最后再统一复核此前全部ALLOW的binding和完整evidence，不重复发PDP。无工具时仍核exact source/lifecycle及Clock/DB run deadline；普通run IAM权限属于未来外层RunAuthorizer。该方法当前**没有接入真实run chain、submission或claim路径**，不能声称本提交已阻止实际claim。

可信ALLOW审计使用实际grantId、USER authVersion或SERVICE catalogVersion、policyVersion及本地UUID/checkedAt。evaluate产生的DENY/UNAVAILABLE审计不伪造上游字段。兼容澄清：early admission/closed/ambient/timeout等尚未产生可信检查的fallback可以返回UNAVAILABLE且audit=null；不会把未执行的检查伪造为Audit事实。localCheckId不是上游decisionId，typed值也不等于真实权限。

`authorizationResourceKey`是与purpose配套的部署契约下界；现有远端协议不echo它，本步骤不声称PDP独立验证了该key。未新增JSONcodec或audit DB写入，没有issuer/config/权限/API/native/hook/运行chain接线。V13未改，SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际验证

本Step两个组件按已锁定接口和文件所有权，各自先观察真实RED再GREEN，最终共同一个逻辑提交；不要求等第二个组件RED后才实现第一个，也不提交未完成scaffold。

| 阶段 | 实际结果 | 证据 |
| --- | --- | --- |
| Bindings RED | 8项、8失败、0错误，命令exit1 | `/tmp/tianji-tool-bindings-red.log`；缺失映射行为的有效RED |
| Bindings GREEN | 8项全通过，命令exit0 | `/tmp/tianji-tool-bindings-green.log` |
| Authorizer RED | 19项、16失败、0错误，命令exit1；3个负向场景原已通过 | `/tmp/tianji-tool-pdp-red.log`；不是编译/fixture错误 |
| 最终选定整组GREEN | 84项全通过，0失败、0错误、0跳过；exit0、BUILD SUCCESS | `/tmp/tianji-tool-pdp-green.log` |

最终复用命令：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=TianjiToolGrantBindingsTest,TianjiToolGrantAuthorizerTest,TianjiExecutionGrantAuthorizerTest,TianjiServiceExecutionGrantAuthorizerTest,TianjiMemoryRunAuthorizationTest,ToolExecutionContractTest,ToolExecutionGrantTest,ToolRegistryTest,ToolCapabilityStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 模块/测试 | 实际通过数 | 证明范围 |
| --- | ---: | --- |
| AR：CapabilityStore 12、Grant 8、Contract 7、Registry 5 | 32 | 既有工具域/凭据兼容回归 |
| AS：新Bindings 8、新Authorizer 19 | 27 | 精确mapping/SDK Expectation/六元组、typed审计、postflight/Window/lifecycle/resource测试 |
| AS：MemoryRunAuthorization 8、RunAuthorizer 11、ServiceAuthorizer 6 | 25 | 既有AS授权调用者兼容 |
| 合计 | 84 | 本次选择的非运行测试；非整个仓库或全平台验收 |

已核当前真实源码及以上日志。测试观察现有真实SDK类型与Expectation，但SDK/client响应为受控替身，不访问真实HTTP/PDP，也不证明其网络传输、密文或部署权限正确。此次未选择PG测试，不把“0跳过”解释成PG已运行；前序数据库边界继续未验收。临时日志可被清理，长期证据是持久测试和实现。

## 3. 需求追踪

| 局部要求 | 状态 | 实现/验证 | 未扩大的边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 固定mapping | Satisfied | Bindings、8项构造/visible/来源/漂移测试 | 部署metadata不自行授权限 |
| PLAN-REQ-002 独立PDP协议 | Satisfied | capId/fullBinding/purpose/AS audience/false-session/run deadline、USER/SERVICE及六元组断言 | 真实远端/密文未运行 |
| PLAN-REQ-003 后置/整体预算 | Satisfied | 生命周期、整证据/issuer/source、合法revision、batch终核、关闭/中断/Trace/容量/time测试 | 实际lease仍后续Store fence，真实run chain未接 |
| PLAN-REQ-004 typed审计 | Satisfied | 实际client版本槽和local UUID/time、拒绝无上游字段 | early未检查fallback允许audit=null；无audit codec/DB写入 |
| PLAN-REQ-005 七路径范围 | Satisfied | 两新AS库/两test、README/plan/report；V13摘要保持 | 无issuer/config/hooks/native/权限/依赖扩张 |
| 完整T3-07/平台蓝图 | Partial | 当前为未注册技术库的实际SDK协议适配 | 签发、配置、运行chain/submission/beforeTool、invocation/审批消费/dispatch/ADK及真实环境验收未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AS/security技术库；AR授权port由AS实现 | exact既有模块/依赖方向保持，无新层/引擎 | None |
| MC-REUSE-001 | Applicable | PASS | ToolRegistry/Grant/CapabilityStore、BoundClient/clients/SDK、SERVICE bindings、TraceContext | 复用现有领域/客户端/来源和生命周期模板 | None |
| MC-DEP-001 | Applicable | PASS | 七路径无POM/build变化，JDK/既有组件足够 | 无依赖/线程/HTTP框架新增 | None |
| MC-NAME-001 | Applicable | PASS | Bindings/Authorizer、BindingVO/ResolvedToolResult、WindowVO/SourceResult/VerifiedToolResult | 行为/不可变值命名明确，无模糊carrier | None |
| MC-VALID-001 | Applicable | PASS | constructor/resolve、exact material/SDK Expectation、完整后置与typed审计约束 | 每个现有source/grant/client handoff有正反验证，无新增HTTP DTO | None |
| MC-MODEL-001 | Applicable | PASS | 不可变records及Map/List防御复制；原Grant值复用 | 无普通可变实体/POJO层复制 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter或跨层DTO映射；既有domain/SDK值构造 | 未绕过MapStruct/BaseConverter创建映射层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术HTTP适配库，沿MemoryRunAuthorization，无业务日志 | 非新增业务Spring服务；错误/值不输出密文或参数 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/autoconfig接线；两个技术库显式构造 | 无新增Bean/Qualifier注入面，不把库交付当启用 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/原SDK/Trace，未新增utility依赖 | 无重复HTTP/摘要/线程平台 | None |
| MC-JSON-001 | Applicable | PASS | 公开ctor复用原ObjectMapper和client factory，typed协议值 | 没有新JSON栈/codec/DB写入，远端未echo key不伪报已验证 | None |
| MC-TIME-001 | Applicable | PASS | Clock.systemUTC/System.nanoTime、DBclock、共享Window；19项Adapter测试中的窗口/回拨/多工具场景 | UTC绝对截止和elapsed预算分开；后置仍有效，旧lease hint不缩窗 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无properties/profile/flag改动 | 环境键不变，库未注册/启用 | None |
| MC-PATTERN-001 | Applicable | PASS | 既有ToolExecutionAuthorization依赖倒置+AS真实PDP Adapter | 实际协议变化点被隔离，没有新调度/执行引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 七条计划路径；旧core/factory/issuer/config/SQL不变 | 只当前mapping/协议库切片，运行接线仍关闭 | None |
| MC-TEST-001 | Applicable | PASS | 两组件独立有效RED→GREEN；最终84通过/0失败/0错误/0跳过 | 实际SDK/来源/后置/资源合同有受控证据，不宣称真实PDP/PG | None |
| MC-BLOCKER-001 | Applicable | PASS | 独立行无未解释失败，独立实现审阅无阻断；运行边界显式保留 | 当前非运行slice具备提交证据，完整T3-07保持Partial | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Bindings/Authorizer及VO/Result/Test职责名 | 当前类型/调用者编译与84项GREEN | 技术角色和值命名一致 | None |
| Rule 2 | Applicable | PASS | mapping constructor/resolve、grant/SDK Expectation、postflight和typed AuditVO | 27项新AS正反测试与原57项回归 | 复用域/协议边界验证，不新增HTTP/业务DTO层 | None |
| Rule 3 | Applicable | PASS | 不可变BindingVO/ResolvedToolResult/内部结果值、集合防御复制 | immutable/source/evidence/URI/漂移测试及编译 | 真值records而非实体，无额外Converter或可变POJO | None |
| Rule 4 | Not applicable | N/A | 沿既有MemoryRunAuthorization的manual技术库，不注册Bean/DI | 无业务Service/Bean/autoconfig修改 | 无新增业务日志/注入面，不能据此声称已启用 | None |
| Rule 5 | Applicable | PASS | JDK、原SDK/Trace/领域组件，无POM变化 | source reactor编译和import范围审阅 | 未新增工具库/框架 | None |
| Rule 6 | Applicable | PASS | 原Jackson client factory复用、typed SDK协议 | exact Expectation/真实版本槽测试 | 无新codec/序列化入库/JSON转换技巧，不伪造远端key回显 | None |
| Rule 7 | Not applicable | N/A | 七路径无profile/properties配置 | 当前范围审阅 | 配置键结构不变 | None |
| Rule 9 | Applicable | PASS | 原授权port依赖倒置与AS Adapter、shared有界执行 | SDK/后置/生命周期/资源测试 | 使用真实变化点，不造策略层/第二引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration、Clock/systemnano/DB时间、整体Window | 共享预算/回拨/到期/关闭/迟到测试 | 无默认zone、java.util日期或伪造lease时间 | None |
| Rule 11 | Applicable | PASS | AS实现AR port，复用既有客户端/来源/存储；旧类与V13不动 | V13仍f0c642…26b3，相关域/AS回归全通过 | exact架构保持，无SQL/依赖/运行入口扩张 | None |

## 6. 未执行事项与处置

没有运行真实HTTP/PDP/Shoubing/Jianshen/crypto/issuer、数据库或模型/MCP验收。没有实际接入RunChain；authorizeRun只是可供后续组合的方法，当前没有改变真实claim/submission。grant签发、配置、native/beforeTool hook、audit持久codec、invocation/审批消费/业务dispatch和ADK接线仍待后续。

没有新增scratch harness或仓库scripts/work脚本；测试保留在对应持久AS测试类，计划文档生成脚本已清理。未自动启动服务、数据库、容器、浏览器/GUI、长期runtime；未注册/激活权限、授角色、部署、push、PR或release。临时日志不是唯一长期证据。

本报告作者只创建该证据文件，不改README/plan/代码/SQL，不提交。主代理在最终diff与显式七路径stage审查后完成单一逻辑提交，hash以Git结果为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前固定绑定与真实SDK协议Adapter切片通过非运行验证；完整T3-07和平台验收尚未完成。
