# T3-07 工具owner签发与真实排队授权链执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [工具签发与queued链](../plan/2026-10-31-21-16-tool-authority-issuance-chain.md)，strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/13/14、§19 T3-07 |
| 主设计实施前SHA-256 | `535f507b0a00205ba8ef6e28899ad68b14849ebd2f03327f486ec4254e335ca3` |
| Baseline | `23a04ed3b8bbf8268c3446b34c22287f6f7cc9ba` |
| 提交 | 主代理完成最终22路径diff/stage门禁后产生；不预填hash，以Git/交付消息为准 |
| 结果 | 本步骤签发、实际提交/queued门禁与装配的非运行验证通过；完整T3-07/平台仍Partial |

## 1. 实际实现

新增`TianjiToolGrantIssuer`同步处理当前真实owner的独立工具票据，复用有限现有owner SDK/client factory、capability Store与固定Bindings。先读owned/coherent material；非QUEUED或无visible工具直接返回latest DTO，不取token/reserve。visible非空时，即使活票据可复用也必须验证current owner/ACTIVE TENANT issuer/carrier。USER仅接受stockAgentAccess采用的真实Rbac3AuthenticationToken/Rbac3UserDetails.identity，核tenant/subject/IdentityPrincipal expiry/AS audience、permissionSnapshot issuer与Authorized，以及前后tokenId和verified SCOPE_集合。身份expiry遵循原AgentAccess，不把permission snapshot TTL当token expiry，也不新增硬编码START scope。

USER carrier严格读取`VerifiedUserTokenCarrier.current(servlet)`，HTTP请求不能借用残留RPC token；不支持native IdP USER旁路。SERVICE仅接受IdpAuthenticationToken/ServiceIdentityPrincipal TENANT、linked-submit scope和verified LinkedSubmit digest filter currentServiceToken；核client/app/business/application/environment五字段，保留既有services给出的第六sourceResource，不误等AS audience。bearer只存在同步方法局部和owner SDK请求，不存字段、日志、JSON或后台补签。

全工具共享`min(now+30s, Authorized expiry, run deadline)`的wall+monotonic窗口；owner HTTP issueTimeout独立约束100ms—5s，剩余不足完整下一次SDK预算就不发。cap活票据复用；否则reserve epoch、在事务外调用USER issue或SERVICE issueService，使用独立purpose/capabilityId/fullBindingHash/false Session/原run deadline。回应后重新核身份/tokenId/verified scopes/carrier、trust、immutable source/resolved binding、窗口和epoch再attach；迟到或源取消/claimed不重签、不挂错代。正常revision/event进展不要求全DTO相等。受控SDK的密文只证明结构，不证明真实密文真实性。

原USER/SERVICE两base issuer保留全部旧构造器，仅新增append-tools overload/helper。USER仍先普通run ticket，再memory、preferences、tools；SERVICE仍先原linked/run ticket，再memory、tools。普通run ticket复用也不能绕过outer tools.ensure。原base run authorizer在ordinary和memory均ALLOW后才调用真实tools.authorizeRun(CLAIM/MODEL)，不构造假lease。三个既有factory在feature=true时要求工具provider，在false时不dereference（包括原直接fixture的null provider）。

新增strict不可变Properties，prefix为`stellar.tianji.agent.tools.authority`、ignoreUnknownFields=false；timeout null→5s、concurrency0→4，分别100ms—10s/1—8，两个列表不可变且最多256，enabled要求非空。唯一base application.yml增加默认`false/PT5S/4/[]/[]`，其余旧键保持；这是PDP预算，不混同owner issueTimeout。

新CFG要求API/storage/execution-grants/execution-authorization四项，不要求runtime，可queue-only启用。SERVICE mapping静态匹配已有Source的issuerHash/tenant及Linked.Binding的同client/source与exact definition key/version/hash，不造fakeRun/link/Subject；actual run仍经services.require。USER-only工具映射与已启用旧SERVICE execution兼容：只有工具确有SERVICE mappings才额外要求同一services指针，ledger/issuer registry/resource始终校验。

六个稳定Bean为`toolCapabilityStore`、`toolActionIntentStore`、`tianjiToolGrantBindings`、`tianjiToolGrantIssuer`、`tianjiToolGrantAuthorizer`、`tianjiToolAuthorityChainVerifier`。两Store各自verifyStorage；tool factories不依赖base roots。`@Lazy(false)` SmartInitializingSingleton只在callback解析actual USER/SERVICE issuers和base run authorizer，核usesToolIssuer/usesToolAuthorization同实例，避免创建unused Bean冒充接链。它显式依赖独立IntentStore，global-lazy下也必须构造并完成两个readiness；没有DI cycle。PDP close只关自有executor，不关共享SDK。

签发及queued/model gate现在确实进入已有链，但默认feature仍关闭；本提交不放开AR/native工具guard，不实现dispatch、审批消费或invocation写入。Controller/filter/AR/native/SQL/权限/依赖未改；V13 SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN与回归

组件按已锁定声明/API和所有权独立真实RED→GREEN，最终共同一个逻辑提交；未把编译/fixture错误算作RED。

| 阶段 | 实际结果 | 证据与修正 |
| --- | --- | --- |
| 初始链/配置RED | 50项、18失败、0错误；exit1 | `/tmp/tianji-tool-chain-red.log`：Props6/5失败、CFG9/7、USER root8/2、SERVICE root14/2、base auth13/2 |
| Issuer及global-lazy RED | 59项、17失败、0错误；exit1 | `/tmp/tianji-tool-issuer-and-lazy-red.log`：Issuer16全部失败；global-lazy新增1项失败（预期两Store verify、实际仅1）；Props7及原三root35项已通过 |
| global-lazy修正 | 最终CFG11全部通过 | chainVerifier显式依赖独立IntentStore，不能只创建cap/PDP而遗漏第二readiness |
| USER-only与旧SERVICE兼容RED | 1项、1失败、0错误；exit1 | `/tmp/tianji-tool-service-optional-red.log`；SERVICE root仅在工具确有SERVICE mappings时要求usesServices同指针，其余identity检查保持；最终SERVICE root15全部通过 |
| Servlet carrier隔离 | Issuer第17项最终通过 | 持久测试`an_http_request_without_its_carrier_cannot_borrow_a_residual_rpc_user_token`；使用Servlet current而非RPC fallback |
| 最终选定全组GREEN | AR50+AS121=171通过，0失败、0错误、0跳过；exit0、BUILD SUCCESS | `/tmp/tianji-tool-chain-green.log`；下表为实际类计数 |

| 模块/类 | 通过数 |
| --- | ---: |
| AR：IntentStore10、CapabilityStore12、StorageVerifier13、Grant8、Contract7 | 50 |
| AS新增：ToolIssuer17、ToolAuthorityProperties7、ToolAuthorityConfiguration11 | 35 |
| AS实际root：UserIssuer8、ServiceIssuer15、base RunAuthorizer13 | 36 |
| AS兼容：MemoryIssuer8、MemoryRunAuthorization8、ServiceConfiguration3、ServiceBindings4 | 23 |
| AS前序工具库：ToolBindings8、ToolAuthorizer19 | 27 |
| 合计 | 171 |

复用JDK25和仓库source-reactor的最终选择命令：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=TianjiToolGrantIssuerTest,TianjiToolAuthorityPropertiesTest,TianjiToolAuthorityConfigurationTest,TianjiExecutionGrantIssuerTest,TianjiServiceExecutionGrantIssuerTest,TianjiExecutionGrantAuthorizerTest,TianjiMemoryGrantIssuerTest,TianjiMemoryRunAuthorizationTest,TianjiServiceExecutionConfigurationTest,TianjiServiceExecutionBindingsTest,TianjiToolGrantBindingsTest,TianjiToolGrantAuthorizerTest,ToolActionIntentStoreTest,ToolCapabilityStoreTest,ToolExecutionStorageVerifierTest,ToolExecutionGrantTest,ToolExecutionContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

配置集成使用实际factory及非web ContextRunner，底层存储/SDK为受控替身，没有启动BootServer或数据库。Properties测试实际绑定嵌套domain records、拒绝未知键/非法值，并核base YAML defaults。default-off不触DataSource，新provider不在旧false路径被访问；enabled缺前提、readiness失败、实际root断链/global-lazy等负场景按预期拒绝，不能把预期context错误日志当未解释测试失败。

已核当前实现和上述日志；独立实现审阅无阻断。没有声称执行额外的身份expiry/permission TTL组合测试。此次未选择真实PG类，0跳过不表示PG已经运行；原真实SQL/catalog/并发边界继续未验收。临时日志可被清理，长期证据是持久测试、实现和冻结SQL。

## 3. 需求追踪与验收边界

| 局部要求 | 状态 | 实现/验证证据 | 仍未宣称 |
| --- | --- | --- | --- |
| PLAN-REQ-001 当前owner独立签发 | Satisfied | 实际Rbac3/Servlet carrier、SERVICE filter/source、exact SDK请求及后置；Issuer17项 | 真实IdP/PDP/密文部署验收 |
| PLAN-REQ-002 幂等与整体窗口 | Satisfied | 活票据复用、single wall+mono30s、HTTP预算、epoch/迟到/claimed/取消测试 | 后台补签或任意重试许可 |
| PLAN-REQ-003 actual提交/授权链 | Satisfied | 两issuer和base auth真实hooks、三个实际factory、普通票据复用/顺序/指针测试 | native工具已经可执行 |
| PLAN-REQ-004 default-off完整装配 | Satisfied | strict Props7、CFG11、双Store verify、global-lazy actual roots、queue-only | 自动启用或自动DDL |
| PLAN-REQ-005 SERVICE来源一致 | Satisfied | static Source/Linked exact definition、actual services.require、原sourceResource、USER-only兼容 | metadata自行认证或PS被AS冒充 |
| PLAN-REQ-006 兼容/验证/边界 | Satisfied | 所有旧ctors保留、memory/preferences参数与顺序、provider false、171项GREEN、22路径 | 全系统运行/生产权限验收 |
| 完整T3-07/平台蓝图 | Partial | 当前签发+queued链已实际接入 | 真实IAM/crypto/PG、native beforeTool/dispatch/approval/invocation写入及其他后续阶段未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AS/security技术Adapter、root chains/factories、22路径 | exact现有结构保持，无Controller/AR/native/新层改变 | None |
| MC-REUSE-001 | Applicable | PASS | MemoryGrantIssuer.clients、owner SDK、stock carrier/principal、前序Bindings/cap/PDP、Spring callbacks | 不复制认证/签发/账本/DI基础设施 | None |
| MC-DEP-001 | Applicable | PASS | 当前路径无POM/build变更；JDK/Spring/既有SDK可用 | 无新增依赖/HTTP/线程框架 | None |
| MC-NAME-001 | Applicable | PASS | Issuer/Properties/Configuration/Test与原root helpers | 技术行为/配置/不可变值职责清晰，旧API名保持 | None |
| MC-VALID-001 | Applicable | PASS | actual principal/carrier/verified scopes/source/SDK/后置、strict Binder和真实factory负例 | 每个现有安全/协议/配置handoff具体验证，无新HTTP DTO | None |
| MC-MODEL-001 | Applicable | PASS | Properties真不可变record、List.copyOf，原domain值复用 | 无新增普通实体或可变POJO层，配置集合不可外部篡改 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层DTO映射；原SDK/domain构造与Binder | 不绕过MapStruct/BaseConverter建转换层 | None |
| MC-LOG-001 | Not applicable | N/A | technical协议Adapter/config，沿原MemoryGrantIssuer模式，无新增业务Service日志 | bearer不存字段/日志；异常固定脱敏 | None |
| MC-BEAN-001 | Applicable | PASS | 六显式@Bean名、现有依赖Qualifier、ObjectProvider、@Lazy(false) callback与destroyMethod | factory DI与manual技术构造分开，无字段注入/cycle，actual root指针和双readiness已测 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/既有SDK/Spring | 无utility库或重复认证/调度框架 | None |
| MC-JSON-001 | Applicable | PASS | 原Jackson owner SDK、typed requests、实际YAML Binder | 无新JSON栈/codec，raw carrier仅同步局部，不持久化 | None |
| MC-TIME-001 | Applicable | PASS | Clock/systemnano/DBclock、single30s窗口、独立issue100ms..5s与PDP100ms..10s | 不重置loop预算、迟到不attach、expiry来源与stock guard一致 | None |
| MC-CONFIG-001 | Applicable | PASS | 仅base application.yml；Props7含实际嵌套Binder及defaults测试 | 新键default false/PT5s/4/空列表，旧键保持，无其他profile需同步 | None |
| MC-PATTERN-001 | Applicable | PASS | owner SDK Adapter+原可选ensure/run授权链装饰+晚解析callback | 真实变化点复用，不新建Activity/调度/执行引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 22条计划路径、V13不变；原Controller/filter/AR/native未改 | 单一真实签发/queued链feature，无权限/依赖/SQL扩张 | None |
| MC-TEST-001 | Applicable | PASS | 多组件有效RED、global-lazy/兼容安全RED、最终171项GREEN | 非web真实factory链有证据；未伪报真实IAM/PG/native验收 | None |
| MC-BLOCKER-001 | Applicable | PASS | 全部独立行、最终source/log与独立审阅无阻断 | 当前非运行逻辑feature具备提交证据，完整任务/运行边界保留 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | ToolGrantIssuer/AuthorityProperties/Configuration与Test、旧usesTool helpers | 类型/调用者编译及171项GREEN | 职责命名清楚，旧公开类型/构造器保留 | None |
| Rule 2 | Applicable | PASS | stock真实身份/Servlet carrier/verified scopes/原source、SDK和strict配置边界 | Issuer17/Props7/CFG11及root负例 | 验证现有安全/协议/配置handoff，不新增未校验HTTP模型 | None |
| Rule 3 | Applicable | PASS | 真不可变Properties record和列表防御复制、原domain证据复用 | Binder/不可变/default/来源/重放测试 | 非实体/普通可变POJO，无重复Converter或模型层 | None |
| Rule 4 | Applicable | PASS | 六显式@Bean名、Qualifier factory入参、providers、技术库manual constructors | actual factory/nonweb context、同指针、关闭/lazy/缺项测试 | 按原技术协议Adapter构造；无新增业务Service，不伪造Lombok DI或Slf4j验收 | None |
| Rule 5 | Applicable | PASS | JDK/Spring/既有owner SDK与组件，无POM改动 | imports/源码编译/范围审阅 | 未引新工具库/框架 | None |
| Rule 6 | Applicable | PASS | 原Jackson SDK、typed IssueRequest、Strict YAML Binder | exact request/secret-free/Binder测试 | 无新JSON栈/codec或token持久化 | None |
| Rule 7 | Applicable | PASS | 只有一份base YAML，新增工具authority默认键 | 实际Binder/unknown字段/边界/default测试 | 单profile结构一致，旧配置语义保持 | None |
| Rule 9 | Applicable | PASS | owner SDK Adapter、可选root链装饰及actual chain callback | ensure顺序、ordinary/memory→tools、reuse/断链测试 | 使用真实扩展点，没有新通用执行器/引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration、Clock/systemnano/DB边界与分离预算 | 回拨/多工具/短预算/迟到/源改变测试 | 无java.util日期或默认zone，不延长原deadline | None |
| Rule 11 | Applicable | PASS | 原AS包/依赖方向、root/factory增量；V13仍f0c642…26b3 | 171项相关回归，既有ctor/旧配置路径保留 | 无新层/SQL/权限/deps/native扩张，实际链在默认关闭下受控接入 | None |

## 6. 未完成事项与处置

本步骤实现真实签发与提交/queued链的源代码和装配；测试中的owner SDK/票据为受控响应，只证明结构/调用和一致性规则。真实Shoubing/Jianshen/PDP、grant密文、PostgreSQL SQL/catalog/锁/联合事务、真实用户/服务旅程均未验收。权限/policy注册、激活、角色grant没有自动执行。native guard保持closed，工具dispatch、invocation写入、approval消费及原计划后续工作继续待办。

没有新scratch harness或仓库scripts/work脚本；文档生成脚本已删除，测试保留在对应持久类。未启动服务、BootServer、数据库、容器、浏览器/GUI或native scheduler/长期runtime；未部署、push、PR或release。0跳过不代表运行测试通过。

本报告作者只创建该证据文档，不改代码/README/plan/SQL，不提交。主代理完成最终22路径diff和显式stage审查后产生单一逻辑提交，hash以Git结果为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前实际owner签发与排队链feature通过非运行验证；完整T3-07和平台验收仍未完成。
