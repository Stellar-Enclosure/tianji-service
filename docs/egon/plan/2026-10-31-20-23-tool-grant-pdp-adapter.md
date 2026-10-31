# T3-07 固定工具授权绑定与真实PDP客户端适配

| Field | Value |
| --- | --- |
| Document | `2026-10-31-20-23-tool-grant-pdp-adapter.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 20:23 Asia/Shanghai` |
| Updated | `2026-10-31 20:23 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | 两个AS技术适配库及测试；固定工具绑定、现有真实PDP client协议、未接线queued gate |
| Source Requirement | 用户继续原plan未完成工作、分步验证提交 |
| Baseline Revision | `main@ff53ff221fa93ac1cbf90b0b86e05bd0c909ec99`；实施前工作树clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户本轮明确授权剩余工作，源文件无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `11c2240c7fb2bc7bc198637b4883bd02e3c6c11761feedc56d0aaa8848d1b71f` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [兼容审计合同](2026-10-31-20-05-tool-authorization-audit-contract.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

原T3-07的逐工具权限需要由现有真实Shoubing/Jianshen协议核独立密封凭据，并在远程决策后复查本地来源。此切片新增固定deployment mapping及PDP适配库，复用前序ToolCapabilityStore/ToolExecutionGrant/typed AuditVO和既有client factory；不签发grant、不配置/注册Bean、不接实际run chain、submission、beforeTool或ADK。`authorizeRun`只提供queued gate方法，只有后续精确装配后才影响真实运行。

## 2. Target Spec and Effective Design

唯一设计源是原蓝图§6/7/13/14和§19 T3-07；基线ff53已有typed审计值，7005117已有独立工具凭据存储。实际复用点为AS的TianjiExecutionGrantAuthorizer.BoundClient/clients、TianjiExecutionAuthorityBindings、TianjiMemoryRunAuthorization有界调用模板与TianjiServiceExecutionBindings。不能复制Memory adapter的宽松逐项重置timeout，本适配器必须一个整体wall+monotonic预算并核完整后置证据。真实远端服务未运行，不把受控client合同测试当部署验收。

## 3. Effective Requirements and Acceptance

PLAN-REQ为原设计既有要求的局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07固定工具/策略 | 可信deployment mapping精确选择当前可见固定工具及独立授权purpose/resource | 每个visible工具恰好一mapping，缺失/重复/漂移拒绝；budget0/隐藏版本为空；SERVICE复用真实process来源 | Bindings/test |
| PLAN-REQ-002 | §13/T3-07当前独立权限 | PDP Expectation绑定独立capability，不拿actionIntent或backendHash充PDP binding | USER decide/SERVICE decideService核capabilityId/fullBindingHash/purpose/AS audience/false session/run deadline | Authorizer/test |
| PLAN-REQ-003 | §7/13/T3-07一致性与预算 | 远程决策返回后来源、信任、完整证据及时间仍有效 | 前后issuer ACTIVE/TENANT/tenant、SERVICE来源、material和Evidence相等；整体有界，迟到/中断/close不ALLOW；DENY也复查 | Authorizer/test |
| PLAN-REQ-004 | §13/T3-07审计 | 可信client实际版本形成typed本地审计，不伪造上游字段 | USER auth/SERVICE catalog/policy/grant来自真实client返回；local UUID/time；拒绝无上游字段；不写JSON/DB | typed AuditVO |
| PLAN-REQ-005 | §6/T3-07范围/反向集成 | 适配库先交付，不擅自启用运行能力 | 七路径、无issuer/config/hook/native/权限/API/依赖/SQL；queuedgate未接线且不伪造lease | README/plan/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先两个新类型最小声明供测试编译，再编写并观察真实行为RED，随后完成mapping与有界PDP适配，最后说明及证据。scaffold没有业务功能、不独立提交。最终签名及worker问题已由主代理闭合；本文Review并strict通过后才GO生产。

### 4.2 Test-first strategy

Bindings覆盖不可变列表、256上限、重复origin/definition/tool槽、注册hash、run/definition/snapshot漂移、visible缺mapping/隐藏/0budget、SERVICE来源。Authorizer用现有真实client类型的受控响应，核精确Expectations与grant/cipher，不发网络；覆盖USER/SERVICE、typed审计、错误namespace/source/trust/cipher/epoch/expiry/decision窗口、DENY后置漂移、有界无队列、并发容量、TraceContext、close/中断/迟到、queued多工具同整体预算及无工具快速ALLOW。

### 4.3 Sequential and parallel boundaries

一个Step；Bindings/test与Authorizer/test可在声明/接口锁定后按文件所有权并行实施，但全体GREEN/审计后仅一个提交。现有factory/issuer/config/run chain/Store/SQL不可改。

### 4.4 Commit boundaries

七条路径为一项“固定mapping+PDP adaptation”可审阅逻辑切片；显式路径提交，不push/改历史，commit后再进行签发/运行接线。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 固定mapping | §13/T3-07权限由可信host部署绑定 | ToolRegistry.visible、ToolExecutionGrant.BindingVO；动态模型参数无权限 | 两个不可变value嵌套在一个AS绑定库，不新建注册服务 | Implement |
| PDP适配 | §13/T3-07真实当前权限 | BoundClient/clients/Expectations、MemoryRunAuthorization模板 | 复用现有HTTP client factory，不复制身份/远端验证栈 | Implement |
| queuedgate方法 | T3-07提交时独立工具预备 | 原run authorizer未来需组合 | 方法先交付，明确未接线，不制造lease或改run服务 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 固定visible映射 | PLAN-REQ-001 | Binding解析正反断言 | 声明、ToolRegistry/Grant、SERVICE现有来源 | ResolvedToolResult | authorizer和后续issuer | Step 1 |
| 真实协议适配 | PLAN-REQ-002/003/004 | exact Expectation/后置/有界测试 | mapping、capabilityStore、现有BoundClient工厂 | check/authorize/authorizeRun | 后续运行接线 | Step 1 |
| 边界说明/证据 | PLAN-REQ-005 | diff/未接线审阅 | 实际GREEN | README/报告 | 用户审查 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AS/security技术库与原PDP客户端 | 保持原边界，不新模块/框架 | 两个手工构造技术Adapter，不新增业务Bean或DI | Step1；MC-ARCH-001、MC-REUSE-001、MC-BEAN-001 |
| Model/validation | ToolRegistry/Grant不可变值，ExecutionMaterial | deployment和stored source分离 | BindingVO/ResolvedToolResult不可变；每个现有值边界完整匹配，metadata不认证 | Step1；MC-VALID-001、MC-MODEL-001 |
| JSON/time/lifecycle | 已有Jackson client factory、Clock/TraceContext/bounded executor | 复用真实协议与资源所有权 | 不增JSONcodec；有限client maps一次构造，close只关自有executor | Step1；MC-JSON-001、MC-TIME-001 |
| Pattern | 既有ToolExecutionAuthorization port、PDP SDK | 依赖倒置/Adapter是真实变化点 | AS实现AR port，隔离身份协议，无第二引擎/策略层 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 客户端/信任 | TianjiExecutionGrantAuthorizer.BoundClient/clients、TrustedIssuerRegistry | package-visible有限factory；decide/decideService/Expectation | 足够；新独立工具映射/后置检查缺口 | 直接复用 | None | Step1；MC-REUSE-001、MC-DEP-001 |
| mapping/证据 | ToolRegistry.visible、ToolCapabilityStore.current、ToolExecutionGrant.BindingVO | 固定definition/工具与完整capability | 足够，不复制账本或注册服务 | 新AS组合适配，旧代码不改 | None | Step1；MC-VALID-001 |
| 有界执行 | MemoryRunAuthorization、JDK ThreadPoolExecutor/SynchronousQueue/virtual threads、TraceContext | 既有无队列/非继承线程模板 | 用10秒上限与共享monotonic预算加强当前切片 | 复用JDK模式 | None | Step1；MC-TIME-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Bindings/Authorizer、VO/Result | 声明→test→实现 | 明确技术角色与不可变值后缀 | 编译/声明审阅 | Step1 | PASS |
| Rule 2 | §13/T3-07 | 原Grant/Registry/client Expectation/Store | tests→适配库 | mapping/source/完整ticket/请求绑定及后置核验，无新HTTP传输层 | 正反合同测试 | Step1 | PASS |
| Rule 3 | §13/T3-07 | BindingVO/ResolvedToolResult为不可变值 | binding/test | defensively copy集合，不新增实体/Converter | immutable/匹配测试 | Step1 | PASS |
| Rule 4 | §6/T3-07 | 原MemoryRunAuthorization技术库manual constructor | 两技术库 | 不新增业务Bean/DI，固定脱敏 | 源码差异 | Step1 | N/A：无业务Bean |
| Rule 5 | §6/T3-07 | JDK/既有SDK/Trace | 两库/test | 无新工具库/依赖 | imports/POM审阅 | Step1 | PASS |
| Rule 6 | §13/T3-07 | 现有Jackson客户端factory | adapter/test | 复用现有协议，不写JSON/DB或新codec | exact typed Expectation测试 | Step1 | PASS |
| Rule 7 | §6/T3-07 | 无配置路径 | README/报告 | 不新增properties/flags | diff | Step1 | N/A：配置不变 |
| Rule 9 | §13/T3-07 | 既有授权port与真实PDP变化点 | adapter | 依赖倒置/Adapter，不建引擎或God接口 | 行为/结构测试 | Step1 | PASS |
| Rule 10 | §7/13/T3-07 | Clock/Instant/DBclock/mono | tests→adapter | 一个共享预算，前后proof窗口/DBtime，无旧hint缩窗 | TEST-002 | Step1 | PASS |
| Rule 11 | §6/T3-07 | AS/security原结构 | 七路径 | 不改AR/Store/SQL/issuer/config/native/run链 | regression/diff | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Request/run deadline、ticket/decision expiry、Audit checked/expiry | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时间比较 | Not applicable: 无UI | decision及evidence expiry覆盖Request.deadline，decision<=evidence；后置DBclock仍有效 | systemUTC/注入Clock，不新增DB舍入；本地checkedAt真实值 | 原SDK/Grant/typed AuditVO合同保持 | Step 1；TEST-002：短窗/未来/到期/后置时间 |
| TIME-002 | 单次check及authorizeRun全部工具的整体timeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无展示 | 100ms..10s配置，1..8并发，无队列；同一wall+mono窗口覆盖所有内部步骤 | System.nanoTime/注入LongSupplier，迟到中断close均无ALLOW | 不使用初次lease expiresAt hint缩窗口，锁下实际lease属后续Store | Step 1；TEST-002：wall回拨/mono推进/多工具总预算 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindings.java | 固定deployment mapping与visible解析 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindingsTest.java | mapping/来源/漂移/hidden测试 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizer.java | 真实SDK协议、有界检查/审计/queuedgate方法 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizerTest.java | exact expectation/后置/资源/预算/typed审计 | Step1 |
| MODIFY | README.md | 精确局部能力与未接线边界 | Step1 |
| CREATE | docs/egon/evidence/2026-10-31-tool-grant-pdp-adapter.md | 实际测试与17MC/10Rule报告 | Step1 |
| CREATE | docs/egon/plan/2026-10-31-20-23-tool-grant-pdp-adapter.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baselineff53，开始前clean，保留其他工作。JDK25/source-reactor与现有AS依赖可用，不添加SDK/HTTP库/线程框架。原V13 SHA固定`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`，所有旧SQL不动。actual复用类型名是TianjiExecutionGrantAuthorizer.BoundClient（没有TianjiAgentRunAuthorizer）；ToolPolicy.key为AR包私有，不扩其可见性，AS边界按现有identifier格式校验，resolve构造原Grant.BindingVO再次核完整约束。resource是AS token audience URI，不必是API路径；authorizationResourceKey是purpose部署契约中的真实API下界，当前协议不echo该key，不能宣称远程独立验证了它。

公开/测试构造参数及整体Window、返回Decision规则均已由主代理确认，按下方精确签名执行。既有SDK无close，不能关闭共享client；只关闭新Adapter自有executor。不把typed数据构造当认证，不虚构上游decisionId。运行chain/submission hook/native/issuer/config全部后续承接。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 为固定工具绑定适配真实PDP协议并保留运行接线关闭

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: ff53及现有ToolCapabilityStore/Grant/Registry、AS BoundClient/factories/ServiceBindings。
- Baseline state: 真实PDP基础SDK与typed审计存在；独立工具mapping/Adapter尚无实现。
- Observable outcome: 有限可信mapping选择visible工具，真实SDK Expectation核独立capability，并在有界窗口后置核来源形成typed结果。
- End state: 新技术库和受控测试可用；authorizeRun只是未接线方法，无签发/运行/dispatch改变。
- Test-first gate: Required — 先最小声明供编译，合法fixture后真实RED，再完成实现；不以编译/fixture错误或未跑真实服务作通过。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindings.java`

- Purpose: Compile-only declaration prerequisite for deployment mapping tests.
- Symbols: Constructor, BindingVO, ResolvedToolResult, resolve and identity helpers.
- Repository evidence: ToolRegistry.visible, ToolExecutionGrant.BindingVO, MemoryContextBinding source matching patterns.
- Dependencies and consumers: Collection<ToolDescriptor>, immutable mapping values, AS audience URI, optional existing TianjiServiceExecutionBindings.
- Why now: Typed tests must compile before real mapping behavior is written.
- Contract/signature changes: Constructor(Collection<ToolDescriptor> registrations,Collection<BindingVO> bindings,URI resource,TianjiServiceExecutionBindings services); resolve(ExecutionMaterial):List<ResolvedToolResult>; bindings()/hasServiceBindings()/usesServices/usesIssuerRegistry/usesResource.
- Input/output and state mapping: BindingVO(long tenantId,String issuerFingerprint,SubjectType principalType,String definitionKey,long definitionVersion,String definitionSha256,DefinitionResourceRef tool,String purpose,String authorizationResourceKey); ResolvedToolResult(ToolDescriptor descriptor,ToolExecutionGrant.BindingVO binding).
- Error and edge behavior: Only inert declarations/defaults initially; no permissive actual mapping, services registration or changed AR visibility.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
public final class TianjiToolGrantBindings {
    // Declare exact constructor, immutable BindingVO and ResolvedToolResult record fields from the signature above.
    // Declare resolve(material) and bindings/hasServiceBindings/identity helpers with compile-only defaults.
    // No real mapping resolution before File3 produces behavior RED; do not commit this scaffold.
}
```

- Verification contribution: Compilation prerequisite only, not mapping or authority proof.
- After this file: File3 can compile and observe missing resolution behavior.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizer.java`

- Purpose: Compile-only prerequisite for real-SDK adapter behavior tests.
- Symbols: ToolExecutionAuthorization/AutoCloseable implementation; authorize/check/authorizeRun/identity helpers; two constructors.
- Repository evidence: TianjiMemoryRunAuthorization manual technical Adapter and TianjiExecutionGrantAuthorizer.clients/BoundClient.
- Dependencies and consumers: Existing ledger/capability store/bindings/SDK/Trace/JDK bounded executor, no new client framework.
- Why now: Tests need exact public/package signatures without reflection or compilation RED.
- Contract/signature changes: Public ctor(AgentLedgerService ledger,ToolCapabilityStore store,TianjiToolGrantBindings bindings,IdpStarterProperties starter,IdentityAuthorityProperties authority,TrustedIssuerRegistry issuers,IdpServiceOAuth2Client oauth,ObjectMapper mapper,Rbac3StarterProperties rbac,Duration timeout,int concurrency). Package ctor(ledger,store,bindings,URI resource,String systemCode,issuers,Map<String,TianjiExecutionGrantAuthorizer.BoundClient> users,Map<String,TianjiExecutionGrantAuthorizer.BoundClient> clients,Duration timeout,int concurrency,Clock clock,LongSupplier nanoTime).
- Input/output and state mapping: authorize(Request,Boundary):Tool Decision; check:CheckResult; authorizeRun(AgentRunAuthorization.Request,AgentRunAuthorization.Boundary):AgentRunAuthorization.Decision; usesLedger/usesBindings(pointer)/usesIssuerRegistry/usesResource exactASCII.
- Error and edge behavior: No real SDK calls, executor or functional ALLOW during scaffold; no issuer/config/hook/native changes.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
public final class TianjiToolGrantAuthorizer implements ToolExecutionAuthorization, AutoCloseable {
    // Declare the exact public/package constructors, authorize/check/authorizeRun and identity helpers.
    // check/resolve-dependent methods return compile-only null/default values before File4 behavior RED.
    // Preserve all existing factories/classes; complete real resources and SDK calls only after RED.
}
```

- Verification contribution: Only typed compilation prerequisite; not a source or PDP validation pass.
- After this file: File4 fixtures compile without operational behavior.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindingsTest.java`

- Purpose: Fix finite immutable deployment mappings and visible exact-source resolution.
- Symbols: Constructor limits/duplicates/hashes, immutable helpers, resolve drift/missing/hidden/zero budget and SERVICE source tests.
- Repository evidence: Memory source binding/service source tests plus real ToolRegistry and immutable ToolExecutionGrant values.
- Dependencies and consumers: File1 declarations, actual ExecutionMaterial/snapshot/domain and existing SERVICE bindings fixtures.
- Why now: This is real mapping RED before implementation.
- Contract/signature changes: No new production APIs beyond File1; fake material is test data, not authentication.
- Input/output and state mapping: Nonempty registrations/mappings max256; duplicate origin+definition key/version+tool slot rejected even if hash/purpose differs; exact registered ref hash and source tuple.
- Error and edge behavior: Changed stored definition/run/snapshot, visible tool missing mapping, wrong tenant/issuer/type/tool hash must reject; zero budget/hidden versions empty without requiring inactive mapping.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void fixed_visible_tools_resolve_once_and_drift_never_becomes_empty_authority() {
    registry = bindingsForPinnedUserAndServiceDefinitions();
    result = registry.resolve(validExecutionMaterial()); assertNotNull(result); assertExactDescriptorAndIndependentBinding(result);
    assertActiveMissingMappingAndSameSlotHashDriftRejected(); assertHiddenOrZeroBudgetReturnsEmpty();
    assertImmutableBindingsAndExactAsciiResourceAndServiceRegistryIdentity(); assertServiceTupleComesOnlyFromServicesRequire();
}
```

- Verification contribution: TEST-001 mapping behavior RED/GREEN and source/visibility safety.
- After this file: Tests compile and fail on missing resolution/validation rather than fixture errors.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizerTest.java`

- Purpose: Fix exact SDK Expectations, typed audits, postflight source integrity and shared bounded lifecycle.
- Symbols: USER/SERVICE allow/deny/unavailable; cap/fullhash/purpose/audience; postflight drift; queue/capacity/Trace/close/interrupt/late; one-window queued gate.
- Repository evidence: Existing MemoryRunAuthorizationTest and actual HttpExecutionAuthorizationClient typed SDK with controlled client responses.
- Dependencies and consumers: File2 scaffold; fixed Clock and LongSupplier, actual typed grants/audits and mock ledger/store/trust; no external PDP.
- Why now: This is adapter behavior RED before functionality/resources are implemented.
- Contract/signature changes: check(null/boundary-null) illegal; authorize invalid/closed/ambient/interrupted unavailable; exact input ref/backend or read-intent/write-no-intent mismatch denied only after source resolves.
- Input/output and state mapping: Expectation uses independent capabilityId/fullBindingSha/purpose/AS resource/false Session/original run deadline, never actionIntent or request backend hash as PDP binding.
- Error and edge behavior: Missing mapping/source/evidence/trust/window -> UNAVAILABLE; explicit PDP denial needs unchanged postflight source before DENY; allow grantId must equal ticket, decision expiry covers request and <=sealed expiry.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
@Test void current_pdp_uses_independent_capability_and_rechecks_complete_evidence() {
    result = adapter.check(validRequest(),PREPARE); assertNotNull(result); assertExactRealSdkExpectationAndUserOrServiceMethod();
    assertTypedAuditUsesActualGrantAndVersionSlotsAndLocalUuidTime();
    mutateIssuerOrServiceTupleOrLedgerOrTicketEpochCipherAfterClientCall(); assertUnavailableNotAllowOrDeny();
    assertRequestRefBackendOrIntentKindMismatchDeniedOnlyAfterTrustedResolution();
    assertSharedWallAndMonotonicBudgetNoQueueTraceCloseInterruptAndLateResponseRules();
    assertQueuedGateSubmitsOnceAndChecksAllVisibleToolsWithinOneWindowWithoutFakeLease();
    assertReplacingFirstTicketWhileCheckingSecondIsCaughtByFinalAllEvidencePostcheckWithoutSecondPdp();
}
```

- Verification contribution: TEST-001/002 real missing behavior RED and deterministic concurrency/time safety.
- After this file: No real network/authority proof is claimed; fixtures reach valid branches before assertions fail.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindings.java`

- Purpose: Complete finite fixed mapping resolution from immutable stored material.
- Symbols: Constructor validation, BindingVO/ResolvedToolResult compact checks, resolve and immutable identity helpers.
- Repository evidence: Actual ToolRegistry.visible(snapshot), original domain grant/source contracts and SERVICE bindings.require(run,processLink).
- Dependencies and consumers: No model-controlled registration or AR private API changes; AS local boundary formats follow existing identifier rules.
- Why now: File3 RED now fixes exact mapping behavior.
- Contract/signature changes: Defensively copy nonempty <=256 lists; mapping ref must equal registered descriptor full ref; reject duplicate origin+definition+tool slot; SERVICE mapping requires existing services.
- Input/output and state mapping: Resolve validates material.run/authorization/snapshot fixed definition/hash identity, selects ToolRegistry.visible, exactly one mapping per visible tool; derives SERVICE Subject with services.require; constructs full ToolExecutionGrant.BindingVO.
- Error and edge behavior: Never silently ignore an active missing mapping or hash drift; zero-budget/hidden versions remain empty; immutable helper lists and ASCII resource/source identity; metadata is not permission.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
constructor: build existing ToolRegistry, copy mappings, require nonempty<=256 and exact registered descriptor hash, reject duplicate source+definition+tool slot;
resolve(material): validate stored run/authorization/snapshot fixed definition; visible = registry.visible(snapshot);
for each visible descriptor: require exactly one matching deployment mapping; derive SERVICE subject only from services.require(run,snapshot.processLink);
construct original ToolExecutionGrant.BindingVO(run,descriptor.ref,descriptor.policy.bindingSha,purpose,ASresource,authorizationResourceKey,subject);
return immutable ResolvedToolResult list; preserve empty hidden/zero-budget semantics and pointer/ASCII identity helpers.
```

- Verification contribution: TEST-001 mapping tests GREEN and unchanged original registry/grant regression.
- After this file: A trusted deployment mapping can be resolved; no runtime configuration or run hook is added.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizer.java`

- Purpose: Complete one-budget real-PDP protocol Adapter with typed audit and postflight consistency.
- Symbols: Constructors/check/authorize/authorizeRun, common Window and evaluator, owned bounded executor/close and identity helpers.
- Repository evidence: Existing BoundClient/client factories/SDK Expectations/TraceContext; no modifications to factories or shared clients.
- Dependencies and consumers: Verified store/ledger/bindings identity; finite USER/SERVICE client maps selected by issuer hash; configured AS resource/systemCode.
- Why now: File4 RED and mapping GREEN now permit minimal actual Adapter implementation.
- Contract/signature changes: timeout100ms..10s, concurrency1..8; one SynchronousQueue bounded non-inheriting virtual executor, one submission per check or authorizeRun. Public ctor builds finite clients once; package ctor injects maps/Clock/nanoTime; close owns only executor.
- Input/output and state mapping: Load exact current material, resolve visible descriptor, match request ref/backend and intent kind, load store.current; use full grant BindingVO capabilityId/bindingSha/purpose/AS audience/false session/run deadline in actual USER decide or SERVICE decideService.
- Error and edge behavior: Pre/post ACTIVE/TENANT/tenant/current issuer; unchanged exact material, resolved binding, SERVICE tuple and full Evidence.equals including epoch/cipher; DB/wall/mono/time/cancellation/closed checks after remote response; no ALLOW or DENY from changed sources.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
check(request,boundary): reject nulls; otherwise one submit with one overall wall+mono Window and captured TraceContext;
evaluate: reject invalid lifecycle/ambient/source; load exact material and resolve mappings; ref/backend or read-with-intent/write-without-intent mismatch -> DENY; resolve failure -> UNAVAILABLE;
evidence = store.current(fullBinding,descriptor); expected = SDK Expectation(issuer,tenant,owner,ASresource,purpose,capabilityId,fullBindingSha,false,run.deadline);
call USER decide or SERVICE decideService; require actual grantId==ticket, decision/evidence expiry>=request.deadline and decision expiry<=sealed expiry;
recheck issuer ACTIVE/TENANT/tenant, SERVICE source, exact material/resolved binding/full evidence including epoch/cipher, DBclock, wall+mono Window and closed/interrupted state;
form AuditVO with local UUID/checkedAt and actual USERauth or SERVICEcatalog plus policyVersion; denial/unavailable has null upstream fields;
authorizeRun: one submit/common Window checks all visible tools, then rechecks every previously ALLOWed binding/full evidence once more without a second PDP call;
no tools: validate exact ledger material/source, Clock/DB run deadline and lifecycle; ordinary IAM is the future outer RunAuthorizer responsibility; no fake lease/request;
check closed/interrupted/same mono Window before and after every submit/get; actual live lease is checked only later by the owning Store DB fence;
authorize valid input delegates to check.decision; invalid lifecycle returns UNAVAILABLE; close cancels own executor only, never shared SDK clients.
```

- Verification contribution: TEST-001/002 Adapter GREEN; original domain/AS authorizer regressions remain as section8.
- After this file: Real SDK protocol path is implemented as an unregistered library; actual issuance/config/run-chain/native/dispatch remain untouched.

#### File 7 — `MODIFY README.md`

- Purpose: Describe current Adapter behavior and the exact unconnected boundary.
- Symbols: Existing T3-07 progress sections and authorization notes.
- Repository evidence: README already distinguishes typed metadata/current host port from actual grants and execution.
- Dependencies and consumers: Final current code and tests only.
- Why now: Describe after actual GREEN, without claiming service deployment.
- Contract/signature changes: No runtime flags/API changes; document fixed mappings, finite SDK clients, full postflight checks and owned executor.
- Input/output and state mapping: Explain actual SDK method use and typed audit; resourceKey is purpose deployment contract, not a remotely echoed verified field.
- Error and edge behavior: authorizeRun is only an unconnected method; no real grant issuer, configuration, run chain/submission/beforeTool or ADK hooks, no real PDP acceptance.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
Append the fixed tool mapping and real USER/SERVICE SDK Expectation Adapter facts after existing T3-07 notes.
Explain one shared bounded window, complete postflight evidence/trust checks and owned executor lifecycle.
Explicitly retain issuer/config/run-chain/submission/native/dispatch integration and deployed IAM/crypto/provider acceptance as pending; no automatic activation.
```

- Verification contribution: TEST-003 implementation-to-documentation scope audit.
- After this file: Consumers can distinguish implemented SDK adaptation from activated tool execution.

#### File 8 — `CREATE docs/egon/evidence/2026-10-31-tool-grant-pdp-adapter.md`

- Purpose: Record actual RED/GREEN, source fidelity, standards and runtime gaps.
- Symbols: Baselineff53, client/mapping tests, 17MC/10Rule, local aliases/full T3-07 status, unchanged SQL.
- Repository evidence: Prior audit/storage evidence and executing skill per-Step gates.
- Dependencies and consumers: Final current source/test outputs/diff/staged scope; no prefilled runtime success.
- Why now: Evidence can only be recorded after actual validation.
- Contract/signature changes: No product contract; map current seven-path slice and actual exits/counts.
- Input/output and state mapping: Each requirement/check gets exact symbol/command evidence; full T3-07 remains Partial, actual deployed IAM and issuance/runtime unverified.
- Error and edge behavior: Keep failures/skips/resources/cancellation limits honest; no secrets, unreviewed scratch harness or automatic runtime.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; manual technical-library Adapter, no Bean/config/dependency expansion.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; reuse immutable domain/SDK contracts, exact source validation and bounded Adapter pattern, evidence-backed N/A for untouched business Bean/config boundaries.

- Implementation pseudocode:

```text
Record baselineff53, exact real behavior RED/GREEN commands and final counts/exits; map all seven paths and unchanged V13.
Evaluate17 Manual Checks and10 Literal Rules independently with source/test evidence and justified N/A for technical-library Bean/config boundaries.
Report real SDK protocol implementation separately from unexecuted actual IAM/crypto/issuer/run-chain/native/dispatch boundaries.
Confirm no new scripts/work harness, runtime/GUI/push actions or unrelated files; commit only declared paths after review.
```

- Verification contribution: TEST-003 final actual evidence and scoped review.
- After this file: All nonruntime slice gates have evidence before explicit staging/commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=TianjiToolGrantBindingsTest,TianjiToolGrantAuthorizerTest,TianjiExecutionGrantAuthorizerTest,TianjiServiceExecutionGrantAuthorizerTest,TianjiMemoryRunAuthorizationTest,ToolExecutionContractTest,ToolExecutionGrantTest,ToolRegistryTest,ToolCapabilityStoreTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: meaningful new behavior RED before implementation, then named new/affected tests GREEN; exact counts/skips recorded in evidence, no real-service acceptance inferred.
- Failure returns to: owning Binding/Adapter implementation or test fixture if invalid RED; undeclared dependency/API/config needs stop scope expansion rather than silently modify existing infrastructure.
- Completion criteria: all five local aliases have source/test proof;17MC/10Rules separately PASS or valid N/A; client construction bounded, source/postflight/Window/cancellation rules verified; seven-path scope and unchanged V13.
- Rollback: close the unregistered Adapter and revert only this semantic commit if needed; no schema/data/runtime migration or activated capability to undo; preserve prior history.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindings.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizer.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantBindingsTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantAuthorizerTest.java`, `README.md`, `docs/egon/evidence/2026-10-31-tool-grant-pdp-adapter.md`, `docs/egon/plan/2026-10-31-20-23-tool-grant-pdp-adapter.md`
- Commit: `feat(agent): adapt independent tool grants to current PDP checks`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | §7 source-reactor command，先两个新test，再全组选定回归 | mapping/SDK Adapter与原调用者 | 真行为RED后GREEN；实际数量写报告 | 所属test/实现 | 五条局部要求；受控客户端 |
| TEST-002 budget/lifecycle | tianji-service | 同组Clock/nanoTime、latch/容量/Trace/close/interrupt测试 | one-submit/commonWindow、前后来源与整证据、queued全部ALLOW最终统一postcheck | wall回拨不延长、迟到不ALLOW、多工具不重置预算 | Adapter/test | PLAN-REQ-002/003/004；非真实IAM |
| TEST-003 静态/人工 | tianji-service | `git diff --check`、七路径diff、V13 SHA、17MC/10Rule | 全部当前路径，无旧factory修改 | 无SQL/依赖/config/hook/秘密扩张 | 所属文件 | 全部；静态 |
| TEST-004 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-20-23-tool-grant-pdp-adapter.md --strict` | 文档/追踪/文件序 | exit0 | 本文 | 结构不等于实现或运行验收 |

测试复用现有source-reactor/JUnit/Mockito。真实HttpExecutionAuthorizationClient类型及其协议Expectation被受控替身观察，不连接部署IAM；不得写成真实签发/密文/网络验收通过。未影响的全平台/PG测试不为本切片自动启动；有新兼容疑点时仅补必要相关回归。没有仓库scripts/work临时harness；文档生成脚本结束即删，不提交。

## 9. Migration, Compatibility, Rollout, and Rollback

没有SQL、迁移、配置、权限、endpoint、API/RPC、native、issuer或现有运行chain修改，V13 SHA保持f0c642…26b3。两个库在程序中可手工构造，但未注册Bean或改变启用条件。公开ctor一次性复用现有factory有限client maps，不按调用创建HTTPclient；SDK无close，不关闭共享client，只关自有无队列executor。未来把authorizeRun接实际run链、签发/submission/beforeTool/native属于后续步骤，不假装本次已影响生产。回滚关闭新Adapter并revert本提交，无数据迁移或权限回滚。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07 | Step 1 | Bindings/Test | TEST-001 | 精确source/definition/hash/visible集合和唯一mapping |
| PLAN-REQ-002 | §13/T3-07 | Step 1 | Authorizer/Test | TEST-001/002 | 真SDK Expectation独立capability/purpose/fullhash，主体方法分开 |
| PLAN-REQ-003 | §7/13/T3-07 | Step 1 | Authorizer/Test | TEST-001/002 | 完整后置来源/evidence及sharedWindow/资源关闭 |
| PLAN-REQ-004 | §13/T3-07 | Step 1 | Authorizer/Test | TEST-001/002 | 实际版本typed审计、本地ID/time、拒绝元数据不伪造 |
| PLAN-REQ-005 | §6/T3-07 | Step 1 | README/plan/evidence及七路径 | TEST-003/004 | 无真实签发/运行接线启用，完整任务Partial |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 精确公开/package ctor及返回类型 | Authorizer/Test | 主代理最终确认参数、AgentRunAuthorization.Decision返回、uses* identity | 主代理 | Closed：按§7精确签名实施 |
| DEC-002 | worker只读问题与来源/时间语义 | 两库/Test | 主代理确认private AR key不改、SERVICE复用、PDP fullhash、postflight完整evidence与单Window | 主代理 | Closed：只七路径，现有factory不改 |
| RISK-001 | authorizationResourceKey不由远端协议echo | Bindings/Adapter说明 | 现有SDK协议只核purpose/binding/audience等 | 部署集成方 | Tracked：purpose部署契约负责真实API下界，不声称远端独立验证key |
| RISK-002 | 真实身份/密文/授权和运行chain未验证/未接线 | 后续适配与部署 | 当前仅受控SDK响应及未注册库 | 主代理/用户 | Tracked：保留独立runtime验收，不启服务 |

## 12. Review and Acceptance

原设计要求的独立逐工具权限通过既有port和真实协议Adapter实现，新增类各有固定mapping/有界调用的必要职责，没有另造身份框架、通用调度或权限后台。本文保留exact source边界、用户禁止自动启动/GUI、无依赖/广泛refactor与按逻辑提交要求。所有参数/worker问题已闭合；File1/2最小声明→File3/4真RED→File5/6完成同一步，仅全GREEN/审计后提交。完整T3-07和真实运行验收仍保持Partial。

### 12.5 Blocking Manual Check

以下为计划级可执行性结论，实施报告必须以实际源码/测试逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AS/security技术库、七条路径 | 只沿已有port/SDK边界扩展，无新层/模块 | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 ledger、Registry/Grant/Store、BoundClient/factory、SERVICE来源、Trace | 不复制身份/HTTP/账本基础设施 | None |
| MC-DEP-001 | Applicable | PASS | 七路径无POM/build变更 | 所需JDK/既有组件可复用，无新增依赖 | None |
| MC-NAME-001 | Applicable | PASS | Bindings/Authorizer、BindingVO/ResolvedToolResult/Test | 技术行为和值职责明确 | None |
| MC-VALID-001 | Applicable | PASS | constructor/resolve、SDK Expectation、完整postflight测试 | 无新HTTP DTO；每个现有source/grant/SDK handoff精确验证 | None |
| MC-MODEL-001 | Applicable | PASS | 两嵌套不可变值、Map/List防御复制、现有Grant重用 | 不新增可变实体或重复POJO层 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter或跨层DTO投影层 | 复用域值构造/SDK expectation，不绕过BaseConverter映射 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术HTTP/JDBC适配库，沿MemoryRunAuthorization，无新增业务日志 | 错误/元数据脱敏，不新增业务Spring服务 | None |
| MC-BEAN-001 | Not applicable | N/A | 不注册Bean/DI/autoconfig | 技术库manual constructor与原模式一致，无Qualifier新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/原SDK/TraceContext | 不增utility库或HTTP/线程框架 | None |
| MC-JSON-001 | Applicable | PASS | 公共ctor复用原ObjectMapper/client工厂；typed SDK值 | 无新JSON栈/codec/DB写入，既有协议不变 | None |
| MC-TIME-001 | Applicable | PASS | §4.9、TEST-002 | UTC Instant+DBtime+mono共享预算，迟到不ALLOW，无旧hint缩窗 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无配置/profile/开关路径 | 环境配置结构不变，库不被自动启用 | None |
| MC-PATTERN-001 | Applicable | PASS | 原ToolExecutionAuthorization依赖倒置和PDP Adapter | 真实协议变化点有隔离，不造第二引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 七条路径、无issuer/factory/config/core/SQL修改 | 当前库切片与后续运行接线明确分离 | None |
| MC-TEST-001 | Applicable | PASS | §8真RED/GREEN/exact客户端/后置/预算/资源回归命令 | 有明确可执行证明，实际结果由报告填入 | None |
| MC-BLOCKER-001 | Applicable | PASS | 最终API确认、§11及其余独立行 | 当前设计无未决重大问题，真实运行边界诚实保留 | None |

### 12.6 Final verdict

PASS — Ready for user review
