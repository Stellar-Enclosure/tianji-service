# T3-07 独立工具签发、提交及排队授权链

| Field | Value |
| --- | --- |
| Document | `2026-10-31-21-16-tool-authority-issuance-chain.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 21:16 Asia/Shanghai` |
| Updated | `2026-10-31 21:16 Asia/Shanghai` |
| Owner | 用户授权，主代理实施/审计 |
| Repository | `tianji-service` |
| Scope | 真实owner签发、既有提交/queued授权链、default-off配置及启动接链证明；仅22路径 |
| Source Requirement | 用户继续原plan未完成工作，按逻辑步骤验证和提交 |
| Baseline Revision | `main@23a04ed3b8bbf8268c3446b34c22287f6f7cc9ba`；实施前工作树clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本轮明确授权执行剩余工作，原文无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `535f507b0a00205ba8ef6e28899ad68b14849ebd2f03327f486ec4254e335ca3` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [固定工具/PDP适配](2026-10-31-20-23-tool-grant-pdp-adapter.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

前序工具PDP只是未接线库；本切片必须实际让已有USER/SERVICE提交路径同步准备独立工具票据，并把真实base run authorizer接入工具queued/model gate，消除仅创建unused工具Bean的假集成。默认关闭，启用时缺依赖/断链必须启动失败。保持全部旧构造器、旧Memory/Preference顺序与flag=false路径；不放开native beforeTool、不新增工具dispatch/审批消费/SQL/权限/依赖。

## 2. Target Spec and Effective Design

原蓝图§6/7/13/14、§19 T3-07的可信身份、独立逐工具权限、幂等/时限和反向集成为唯一设计源。基线23a04ed已有fixed bindings/真实PDP协议适配；本次复用既有owner SDK/factory/current token链和capability存储，不复制认证框架。current USER必须是stockAgentAccess采用的Rbac3AuthenticationToken/Rbac3UserDetails.identity，不能加入native IdP USER旁路。SERVICE仍使用原LinkedSubmit verified filter和TENANT服务身份。

## 3. Effective Requirements and Acceptance

局部PLAN-REQ别名只追踪原文已有要求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07独立工具凭据 | 当前真实owner同步签发与固定来源/工具绑定的独立票据 | USER真实Rbac3；SERVICE真实linked carrier/六元组；capID/fullhash/purpose/falseSession/run deadline，事务外SDK，后置同源/epoch attach | 新ToolIssuer/Test |
| PLAN-REQ-002 | §7/13/T3-07预算/幂等 | 重放复用活票据，取消/claimed/迟到/过期不得重签或挂错epoch | 全工具一个min(now30s,Authorized expiry,run deadline)wall+mono窗口；HTTP issueTimeout100ms..5s，不足不发新call | ToolIssuer与原两issuer链 |
| PLAN-REQ-003 | §13/T3-07提交与执行门禁 | 真提交路径准备tool，真实run授权链在ordinary/memory之后检查tool | 原runTicket复用也调用tools.ensure；base authorizer ordinary+memory ALLOW后tools.authorizeRun；缺tool不claim；兼容旧构造器 | 两issuer/base authorizer及三factory |
| PLAN-REQ-004 | §6/T3-07受控启用 | default-off明确配置、可靠ready且证明真实链已接上 | 严格不可变Properties；四prereq flags，不要求runtime；V13两Store verify；@Lazy(false) singleton callback核真实root usesTool指针 | Props/CFG/Test/YAML |
| PLAN-REQ-005 | §13/T3-07SERVICE来源 | 不能凭mapping或任意SERVICE冒充原请求方 | SERVICE mapping静态匹配既有source+linked definition；实际由services.require；sourceResource不误等AS audience | CFG/Issuer/Test |
| PLAN-REQ-006 | §17/T3-07验证/兼容/边界 | 精确接线有受控集成证据且不越界启native能力 | 真实factory/nonweb context、不启BootServer/DB；flag=false不触DS/新provider；旧memory/preferences索引和顺序保留 | 新/改测试及README/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

单一逻辑feature包含owner签发、真实提交/授权链和启用证明，不能只交付unusedBean。先为新类型/新增overload/factory最后provider添加最小可编译声明，并对三个既有直调fixture只补末尾null provider；这些都是编译前提，不加功能。各组件在已锁定API下独立测试真实RED后才完成自己功能；最后统一真实factory/context验证和一个提交。旧ctor参数中memory/preferences保持原index7/8，tools仅append新overload，所有旧ctor继续委托tools=null。

### 4.2 Test-first strategy

分别验证ToolIssuer、Properties、CFG与旧两issuer/base gate。每个owner只在自己组件合法fixture后观察行为RED，不用编译/fixture错误代替；已锁定API下组件可独立RED→GREEN并行，不要求等待全部组件同时RED。Default-off context不触DataSource；enabled context用实际factory和受控底层存储/SDK验证no-cycle、缺prereq/断链失败、queue-only可用、两Store readiness、所有真实root指针。

### 4.3 Sequential and parallel boundaries

只有Step1 In Progress。issuer owner、新CFG/Props owner和主代理旧root链owner按精确文件分工；共享声明先对齐，各自独立RED barrier，最终全体GREEN且一个commit。无后续native/dispatch或未声明文件编辑。

### 4.4 Commit boundaries

22条路径形成一项实际可启用的“工具签发+queued链”feature。显式路径stage，提交后记录hash再开始下一步；不push、改历史或先提交scaffold。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| ToolIssuer | T3-07真实owner工具权限 | 前序capStore/bindings/PDP；MemoryGrantIssuer已提供有限SDK factory | 复用SDK/source/capability，不造签发服务或后台补签 | Implement |
| Root hook/gate | T3-07不能绕过工具预备 | 已有两issuer ensure、base authorizer ordinary+memory链 | append可选工具参数/调用，保留旧ctors和顺序 | Implement |
| CFG/chain verifier | T3-07可靠装配 | Spring @Bean、ObjectProvider、非web runner | 无额外manager；@Lazy(false) callback证明actual roots引用同一对象 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| owner工具签发 | PLAN-REQ-001/002/005 | 新IssuerTest | 前序capStore/bindings、当前principal与SDK | 同步工具grant准备 | 原两root issuer | Step 1 |
| 根链与factory | PLAN-REQ-003/006 | 两issuer/base authorizer测试 | 可选overloads+新ToolIssuer/PDP类型 | 真实submission与CLAIM/MODEL gate | 启用CFG | Step 1 |
| properties/装配/证明 | PLAN-REQ-004/005/006 | props/真实CFG context测试 | root usesTool helpers、providers、旧source metadata | 默认关闭且启用failclosed | 操作方后续启用 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AS/security owner adapters、root factories、AR cap/PDP库 | 保持原架构/source affinity | 新技术协议Issuer，不改Controller/AR/native，不造新引擎 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Value/validation | 既有不可变Properties records、domain/SDK/source | 不信任模型或metadata | Strict properties、不可变集合；真实USER/SVC身份和carrier双核、后置来源 | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/DI/lifecycle | 既有@Configuration/@Bean/ObjectProvider与明确Qualifiers | 技术库manual构造保持，factory负责DI | 六个稳定Bean；provider只在相应flagtrue取；callback延迟解析roots防cycle；close只关自有PDP executor | Step1；MC-BEAN-001 |
| JSON/time/config | 原Jackson SDK/YAML Binder；base单profile | 不增协议/DDL、默认关闭 | YAML一组键；分别PDP与issue预算；不保留bearer；见§4.9 | Step1；MC-JSON-001、MC-TIME-001、MC-CONFIG-001 |
| Pattern | 原owner SDK Adapter、已有ensure/run auth链 | 复用真实扩展点 | Adapter+可选链装饰，ordinary/memory先行，不新增Activity执行器 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| issue/client | MemoryGrantIssuer.clients、HttpExecutionGrantClient、current USER/SVC carrier | 既有USER/SERVICE issue scopes与同步SDK | 足够；缺工具用途/来源组合 | 复用有限factory，no percall client | None | Step1；MC-REUSE-001、MC-DEP-001 |
| mapping/store/PDP | 前序ToolGrantBindings/CapabilityStore/Authorizer/IntentStore | source/ref/fullhash/epoch/typed PDP已有 | 仅装配与真链缺口 | 直接复用，两Store各verify | None | Step1；MC-REUSE-001 |
| 启用证明 | Spring ObjectProvider/SmartInitializingSingleton/nonweb ContextRunner | 现有factory/provider模式 | 可证明实际root，不需额外层 | callback解析现成root并比较usesTool指针 | None | Step1；MC-BEAN-001、MC-TEST-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Issuer/Properties/Configuration/Test角色 | 声明→test→功能→文档 | 明确行为/不可变配置职责；旧类型名保留 | 编译/类型审阅 | Step1 | PASS |
| Rule 2 | §13/T3-07 | domain/SDK/source/stockAgentAccess边界 | Issuer/CFG及测试 | 真实principal/carrier/registry/版本/窗口/绑定，Strict YAML、不增HTTP DTO | 正反/真实factory context | Step1 | PASS |
| Rule 3 | §13/T3-07 | 真不可变config record/domain值 | Props/Issuer | 防御复制列表，复用旧值；不新实体/Converter | 构造/Binder/immutable测试 | Step1 | PASS |
| Rule 4 | §6/T3-07 | @Bean factories和manual技术Adapter | CFG/三个旧factory | 六Bean稳定名、已知外部依赖Qualifier、ObjectProvider；配置无字段注入，技术Issuer非新业务Bean构造模型 | context/身份指针检查 | Step1 | PASS |
| Rule 5 | §6/T3-07 | JDK/已有SDK/Spring | 全代码 | 不增工具/HTTP/线程依赖 | POM/import审阅 | Step1 | PASS |
| Rule 6 | §13/T3-07 | 原Jackson SDK和YAML Binder | Issuer/Props/Test | 原协议issue字段精确复用，不造JSON/载token | SDK/Binder合同测试 | Step1 | PASS |
| Rule 7 | §6/T3-07 | AS仅base application.yml | YAML/Props/Test | default false/5s/4/空列表，Strict未知键和边界一致 | keys/default/Binder测试 | Step1 | PASS |
| Rule 9 | §13/T3-07 | owner Adapter与现root调用链 | Issuer/root/factory | 真实Adapter+可选链gate；不建立新调度/泛化层 | 顺序/重复票据/queued gate | Step1 | PASS |
| Rule 10 | §7/13/T3-07 | Clock/Instant、nanoTime、SDK timeout | Issuer/tests | 30s整体wall+mono、principal/token/source后核、迟到不attach | TEST-002 | Step1 | PASS |
| Rule 11 | §6/T3-07 | AS现包和实际root链、冻结V13 | 22路径 | 不改Controller/AR/native/SQL/权限/依赖，source ASissue→ASdecide | diff/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Authorized/principal/token expiry、run deadline、issue receipt expiresAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时间 | Not applicable: 无UI | 同步窗口min(now30s,Authorized expiry,run deadline)，issue回后来源/票据/window再核，不向旧epoch attach | Clock.systemUTC/注入Clock，沿capStore微秒语义，不增DB舍入 | 原SDK独立purpose+capID/fullhash/falseSession/原run deadline | Step 1；TEST-002：expiry/迟到/claimed/cancel/重放 |
| TIME-002 | 全工具issue budget与每次HTTP issueTimeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无展示 | 一个wall+mono窗口，剩余不足下一HTTP预算不发；issueTimeout100ms..5s | System.nanoTime/LongSupplier；不在loop重置，不睡30秒测试 | 独立于PDP Properties.timeout100ms..10s，SDK factory原有timeout复用 | Step 1；TEST-002：多工具/回拨/短窗口 |
| TIME-003 | Properties.timeout与queued/PDP gate | Duration | Duration | Not persisted | Internal | Not applicable: elapsed预算 | Not applicable: 无UI | PDP默认5s，配置100ms..10s、concurrency默认4范围1..8 | 原23a Adapter自身Clock/mono/DB检查复用，不改旧时间合同 | queue-only启用不要求runtime，native guard保持关闭 | Step 1；TEST-003：Binder/装配/gate |

## 5. Change File Tree

下方AS security路径前缀为`stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/`，测试同module的`src/test/java/.../security/`。共22个唯一提交路径；旧source/core不扩展。

| Operation | Repository-relative path | Step |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuer.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityProperties.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfiguration.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuer.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuer.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizer.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantConfiguration.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfiguration.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionAuthorizationConfiguration.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuerTest.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityPropertiesTest.java` | Step 1 |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuerTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuerTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizerTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryGrantIssuerTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryRunAuthorizationTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfigurationTest.java` | Step 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Step 1 |
| MODIFY | `README.md` | Step 1 |
| CREATE | `docs/egon/plan/2026-10-31-21-16-tool-authority-issuance-chain.md` | Step 1 |
| CREATE | `docs/egon/evidence/2026-10-31-tool-authority-issuance-chain.md` | Step 1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

JDK25与既有source-reactor/SDK/Spring测试能力足够，不引依赖。base只有一份application.yml，新键默认false/5s/4/空列表。唯一V13保持`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`，不自动应用DDL；两个Store各自verifyStorage，测试用受控底层，不启动DB。

编译声明阶段只允许：三个新class/record及精确public/package签名、root新增append-tools overload/helper、三factory末尾ObjectProvider参数；旧test直调点只补最后null provider使flag=false原fixture编译。方法可空/default或委托旧实现，不能先实现新功能。随后按下方每组件test先RED再回访其声明完成，原API全部保留。单一步内的声明→test→回访不是独立提交。

USER owner仅真实Rbac3AuthenticationToken/Rbac3UserDetails.identity，核current identity/tenant/subject/expiry/ASaudience、permissionSnapshot issuer与Authorized，verified SCOPE_集合/tokenId/current carrier前后一致；不新增硬编码START scope，不支持native IdP USER旁路。SERVICE仅IdpAuthenticationToken/ServiceIdentityPrincipal TENANT，currentServiceToken必须来自verified LinkedSubmit digest filter，要求linked-submit scope及current token/expiry/issuer/ASaudience；client/app/business/application/environment五字段对应原Subject，第六sourceResource保留既有services来源，不误等AS audience。bearer只在同步方法局部送owner SDK，不存字段、日志或后台补签。

Issuer先读owned/coherent material；非QUEUED或visible empty直接返回latest DTO，不取token/reserve。visible非空即使活票据可复用，也先核真实current principal/ACTIVE trust/carrier，复用前后来源仍匹配；源取消/claimed/迟到不重签/错挂，mutable revision/event不作全DTO相等比较。

新CFG无需构造依赖，避免new ToolIssuer/PDP factory注入base issuer/auth。六Bean名字固定：`toolCapabilityStore`、`toolActionIntentStore`、`tianjiToolGrantBindings`、`tianjiToolGrantIssuer`、`tianjiToolGrantAuthorizer`、`tianjiToolAuthorityChainVerifier`。@Lazy(false) SmartInitializingSingleton只在callback解析现有USER/SVC/base run roots并比对public usesToolIssuer/usesToolAuthorization指针，必须证明actual chain，不能仅检测Bean存在。SERVICE mapping静态要求至少一个匹配Source/Linked definition，不造fakeRun/link/Subject调用services.require；实际run仍用原require。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 实际接入工具owner签发与排队授权链并证明装配

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005、PLAN-REQ-006
- Dependencies: 已提交23a04ed及现有ToolBindings/PDP/capability/IntentStore、owner SDK、stock principal/source链。
- Baseline state: 工具库已实现但未接真实issuer/root run链；现有两issuer、三factory和direct fixtures存在。
- Observable outcome: feature true时真实提交准备工具票据、base run gate检查工具，启动验证actual root指针；feature false保留原行为。
- End state: 默认关闭但完整装配路径可受控测试；native tool guard/dispatch/审批消费仍关闭，真实环境未验收。
- Test-first gate: Required — §6最小声明与仅兼容参数适配后，各组件合法fixture真实RED再独立GREEN；不以编译或fixture错误作RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuerTest.java`

- Purpose: Define real owner issuance, replay/epoch and one-window invariants.
- Symbols: USER Rbac3 / SERVICE linked carrier, exact issue fields, current principal/source/scope, before/after token checks, expiry/late/claimed tests.
- Repository evidence: Existing MemoryGrantIssuer/ServiceExecutionGrantIssuer tests and actual domain/SDK types.
- Dependencies and consumers: Compile-only new ToolIssuer declarations, controlled ledger/capStore/SDK/security context; no HTTP or DB.
- Why now: Issuer component behavior RED before functional issue code.
- Contract/signature changes: No new external API; public/package ctor and ensure(Authorized,RunDTO):RunDTO are typed.
- Input/output and state mapping: Expected purpose/capabilityId/fullBindingHash/falseSession/original deadline; USER verified carrier or SERVICE current linked filter token with original six-tuple.
- Error and edge behavior: Missing/native USER/wrong identity/token/SCOPE drift, SERVICE partial tuple, inactive trust, time/cancel/claimed, late epoch must not issue/attach; active ticket reuse still verifies owner.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void actual_owner_and_independent_ticket_are_bound_before_and_after_issue() {
    useRealRbac3UserDetailsIdentityOrLinkedServicePrincipalAndVerifiedCarrier();
    assertIndependentSdkRequestAndOneOverallWindowAcrossAllVisibleTools();
    assertNoIssueForNonqueuedOrNoVisibleAndNoTokenReadOnThosePaths();
    assertLiveTicketReuseStillChecksPrincipalAndTrust(); assertPostIssueTokenScopeSourceDriftOrExpiredEpochNeverAttaches();
    assertSourceResourceRemainsOriginalServiceResourceAndBearerIsNotRetained();
}
```

- Verification contribution: TEST-001 issuer real RED/GREEN; TEST-002 deterministic source/time/SDK boundaries.
- After this file: Valid fixtures compile; missing behavior rather than setup errors is observed.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityPropertiesTest.java`

- Purpose: Define strict immutable configuration defaults/limits and Binder contract.
- Symbols: Defaults false/5s/4/empty, unknown keys, duration/concurrency/list limits, immutable nested values.
- Repository evidence: Existing immutable ServiceExecutionProperties and Spring Binder tests.
- Dependencies and consumers: New compile-only record and existing ToolDescriptor/BindingVO values.
- Why now: Properties RED precedes actual validation/defaulting.
- Contract/signature changes: prefix stellar.tianji.agent.tools.authority, ignoreUnknownFields=false.
- Input/output and state mapping: timeout null->5s in[100ms,10s], concurrency0->4 in[1,8], null lists->empty, max256, enabled requires nonempty.
- Error and edge behavior: Reject unknown keys, invalid ranges/enabled empty, list mutation/null elements; do not treat PDP timeout as issuer HTTP timeout.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void strict_properties_keep_disabled_defaults_and_immutable_bounds() {
    assertDefaultDisabledFiveSecondsFourWorkersAndEmptyLists();
    bindRealYamlAndRejectUnknownFieldsAndOutOfRangeDurationsConcurrencyLists();
    assertEnabledRequiresDescriptorsAndMappings(); assertCopiedListsCannotBeMutated();
}
```

- Verification contribution: TEST-001/003 properties RED/GREEN and config shape checks.
- After this file: Binder/value behavior fixed without starting an application.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java`

- Purpose: Prove actual enabled chain, prerequisites and default-off without database/server startup.
- Symbols: Nonweb ContextRunner actual factories, stores verify, no cycle, actual root identity, service metadata, lazy callback/readiness.
- Repository evidence: Existing nonweb Spring configuration tests, ObjectProvider and mocked SDK/catalog primitives.
- Dependencies and consumers: New declarations plus real three old root factories; controlled DataSource/ledger/client/store verification boundaries.
- Why now: CFG component RED before functional Bean registration/chain proof.
- Contract/signature changes: Six exact stable Beans and enabled switch; no dependency on runtime flag.
- Input/output and state mapping: Default false touches no DS/new providers; true requires API/storage/run grants/run authorization; SERVICE static Source/Linked metadata exact; two stores verify; callback resolves actual roots.
- Error and edge behavior: Missing/partial prerequisites/provider, unusedToolBeans or old roots with null/different tool must fail startup; refuse fake SERVICE probe, crosssource/resource/registry; close only owned PDP executor.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void real_factories_and_eager_singleton_callback_prove_the_root_chain() {
    runNonwebContextWithActualToolAndUserServiceRunFactoriesAndControlledStorageClients();
    assertDefaultOffNeverTouchesDataSourceOrNewProviders(); assertQueueOnlyNeedsNoRuntime();
    assertPrerequisiteAndServiceMetadataOrStoreReadinessFailuresFailClosed();
    assertActualRootIssuersUseSameToolIssuerAndActualRunAuthorizerUsesSameToolPdp();
    assertDisconnectedOrDifferentRootFailsEvenWhenUnusedToolBeansExistAndGlobalLazyIsOn();
}
```

- Verification contribution: TEST-001/003 actual factory/context RED/GREEN, not BootServer/DB acceptance.
- After this file: Integration assertions establish missing full chain before enabling production factories.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuerTest.java`

- Purpose: USER root preserves all ctor compatibility and executes tool preparation after memory/preferences, including reused run tickets.
- Symbols: TianjiExecutionGrantIssuerTest new tool-chain assertions; retain original tests.
- Repository evidence: Existing root tests and append-only constructor APIs.
- Dependencies and consumers: Compile-only tool overload/helpers and existing test fixtures.
- Why now: Root component RED before implementing new optional chain.
- Contract/signature changes: Keep old constructors, source/memory/preference behavior and parameter positions.
- Input/output and state mapping: Only optional tool stage is added; verify call order, source identity, failure/decision propagation and replay.
- Error and edge behavior: Do not relax existing host permissions, silently skip missing enabled tool, or lose old fixture assertions.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void existing_user_ticket_replay_still_prepares_tools_after_existing_stages() {
    constructEveryOldOverloadAndTheNewAppendToolsOverload();
    reuseCurrentRunGrant(); root.ensure(authorized,submitted);
    assertOrderedMemoryThenPreferencesThenToolsAndSameLatestRun(); assertToolFailureCannotBeReportedAsSuccessfulPreparation();
}
```

- Verification contribution: TEST-001 root hook/gate RED/GREEN and old source compatibility.
- After this file: Missing hook/gate is observed independently of the new issuer component.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuerTest.java`

- Purpose: SERVICE root preserves old constructors and invokes tools after existing memory on both new and reused run grants.
- Symbols: TianjiServiceExecutionGrantIssuerTest new tool-chain assertions; retain original tests.
- Repository evidence: Existing root tests and append-only constructor APIs.
- Dependencies and consumers: Compile-only tool overload/helpers and existing test fixtures.
- Why now: Root component RED before implementing new optional chain.
- Contract/signature changes: Keep old constructors, source/memory/preference behavior and parameter positions.
- Input/output and state mapping: Only optional tool stage is added; verify call order, source identity, failure/decision propagation and replay.
- Error and edge behavior: Do not relax existing host permissions, silently skip missing enabled tool, or lose old fixture assertions.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void linked_service_submission_prepares_tool_grants_after_memory() {
    retainOldServiceConstructorParametersAndAppendToolsOnlyInNewOverload();
    root.ensure(authorized,actualPrincipal,existingBinding,command,submitted);
    assertMemoryThenToolsEvenWhenTheRunTicketWasReused(); assertUsesToolIssuerIdentityAndWrongSourceRejection();
}
```

- Verification contribution: TEST-001 root hook/gate RED/GREEN and old source compatibility.
- After this file: Missing hook/gate is observed independently of the new issuer component.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizerTest.java`

- Purpose: Base actual run chain gates ordinary/memory before tools without changing existing source or creating fake lease.
- Symbols: TianjiExecutionGrantAuthorizerTest new tool-chain assertions; retain original tests.
- Repository evidence: Existing root tests and append-only constructor APIs.
- Dependencies and consumers: Compile-only tool overload/helpers and existing test fixtures.
- Why now: Root component RED before implementing new optional chain.
- Contract/signature changes: Keep old constructors, source/memory/preference behavior and parameter positions.
- Input/output and state mapping: Only optional tool stage is added; verify call order, source identity, failure/decision propagation and replay.
- Error and edge behavior: Do not relax existing host permissions, silently skip missing enabled tool, or lose old fixture assertions.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
@Test void ordinary_and_memory_must_allow_before_tools_gate_claim_or_model() {
    constructOldAndAppendToolsAuthorizers(); assertOldPathsUnchanged();
    assertNoToolCallUnlessOrdinaryAndMemoryAllow();
    assertMissingToolEvidenceBlocksQueuedClaimAndToolsAuthorizeRunReceivesRealRunBoundaryWithoutFakeLease();
    assertUsesToolAuthorizationPointerAndSourceResourceRegistryMismatchRefused();
}
```

- Verification contribution: TEST-001 root hook/gate RED/GREEN and old source compatibility.
- After this file: Missing hook/gate is observed independently of the new issuer component.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryGrantIssuerTest.java`

- Purpose: Adapt the known direct factory call to its last provider parameter without changing unrelated fixtures.
- Symbols: TianjiMemoryGrantIssuerTest direct factory invocation(s).
- Repository evidence: Current tests call the existing named factory directly.
- Dependencies and consumers: Only the declared appended ObjectProvider parameter; flag remains false in legacy fixtures.
- Why now: Compilation prerequisite adaptation is allowed before behavior RED; retain all old behavioral checks.
- Contract/signature changes: Append last null tool provider at direct calls; no earlier argument/order changes.
- Input/output and state mapping: Flag=false factory must never dereference the new provider; new enabled integration tests are in new CFG suite.
- Error and edge behavior: Do not edit unrelated assertions or make old tests green by weakening memory/service security.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Locate existing direct calls to TianjiExecutionGrantConfiguration.tianjiExecutionGrantIssuer.
Append exactly the final null tool ObjectProvider argument for legacy tools-disabled fixtures.
Retain every prior assertion and argument position; confirm no new provider is dereferenced while disabled.
```

- Verification contribution: TEST-003 existing memory/service fixture regression and compile gate.
- After this file: Legacy direct callers compile with unchanged semantics.

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryRunAuthorizationTest.java`

- Purpose: Adapt the known direct factory call to its last provider parameter without changing unrelated fixtures.
- Symbols: TianjiMemoryRunAuthorizationTest direct factory invocation(s).
- Repository evidence: Current tests call the existing named factory directly.
- Dependencies and consumers: Only the declared appended ObjectProvider parameter; flag remains false in legacy fixtures.
- Why now: Compilation prerequisite adaptation is allowed before behavior RED; retain all old behavioral checks.
- Contract/signature changes: Append last null tool provider at direct calls; no earlier argument/order changes.
- Input/output and state mapping: Flag=false factory must never dereference the new provider; new enabled integration tests are in new CFG suite.
- Error and edge behavior: Do not edit unrelated assertions or make old tests green by weakening memory/service security.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Locate existing direct calls to TianjiExecutionAuthorizationConfiguration.tianjiExecutionGrantAuthorizer.
Append exactly the final null tool ObjectProvider argument for legacy tools-disabled fixtures.
Retain every prior assertion and argument position; confirm no new provider is dereferenced while disabled.
```

- Verification contribution: TEST-003 existing memory/service fixture regression and compile gate.
- After this file: Legacy direct callers compile with unchanged semantics.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfigurationTest.java`

- Purpose: Adapt the known direct factory call to its last provider parameter without changing unrelated fixtures.
- Symbols: TianjiServiceExecutionConfigurationTest direct factory invocation(s).
- Repository evidence: Current tests call the existing named factory directly.
- Dependencies and consumers: Only the declared appended ObjectProvider parameter; flag remains false in legacy fixtures.
- Why now: Compilation prerequisite adaptation is allowed before behavior RED; retain all old behavioral checks.
- Contract/signature changes: Append last null tool provider at direct calls; no earlier argument/order changes.
- Input/output and state mapping: Flag=false factory must never dereference the new provider; new enabled integration tests are in new CFG suite.
- Error and edge behavior: Do not edit unrelated assertions or make old tests green by weakening memory/service security.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Locate existing direct calls to TianjiServiceExecutionConfiguration.tianjiServiceExecutionGrantIssuer.
Append exactly the final null tool ObjectProvider argument for legacy tools-disabled fixtures.
Retain every prior assertion and argument position; confirm no new provider is dereferenced while disabled.
```

- Verification contribution: TEST-003 existing memory/service fixture regression and compile gate.
- After this file: Legacy direct callers compile with unchanged semantics.

#### File 10 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuer.java`

- Purpose: Implement synchronous owner-bound independent tool issuance, no background minting.
- Symbols: Public/package ctors, ensure(Authorized,RunDTO):RunDTO, usesLedger/Bindings/IssuerRegistry/Resource/Services.
- Repository evidence: MemoryGrantIssuer.clients for existing USER/SERVICE issue scopes and stock AgentAccess/current-token boundaries.
- Dependencies and consumers: Ledger/ToolCapabilityStore/TianjiToolGrantBindings; finite HttpExecutionGrantClient maps; actual current SecurityContext.
- Why now: Complete only after its focused behavior RED; old root hooks consume it.
- Contract/signature changes: Public ctor(ledger,store,bindings,starter,authority,issuers,oauth,mapper); package ctor(ledger,store,bindings,URI resource,issuers,Map<URI,HttpExecutionGrantClient>users,Map<URI,HttpExecutionGrantClient>clients,Duration issueTimeout,Clock,LongSupplier nanoTime).
- Input/output and state mapping: Owned coherent material first; nonQUEUED/empty visible returns latest before token/reserve. Visible requires current real owner/ACTIVE trust/carrier even on live-ticket reuse. Independently issue USER or SERVICE purpose/capID/fullbinding/falseSession/original deadline.
- Error and edge behavior: One overall min(now30s,Authorized expiry,run deadline)wall+mono budget; issueTimeout[100ms,5s], insufficient remaining never starts next SDK call; after call recheck principal/tokenId/verified SCOPE_/carrier, exact immutable source/resolved binding/trust/window then epoch attach. No bearer fields/logs/background retry; late/claimed/cancel no resubmit.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
ensure(): load coherent owned executionMaterial; resolve fixed tools; return latest DTO immediately if notQUEUED or no visible tools;
validate actual USER Rbac3 identity/snapshot issuer/AS audience/carrier or actual TENANT SERVICE linked-submit digest/principal/source tuple;
start one wall+mono window min(now30s,Authorized expiry,run deadline); live cap may be reused only with the same current principal/trust/source;
for missing cap: require full HTTP issueTimeout budget, reserve epoch, leave DB transaction, call actual owner SDK issue/issueService with independent binding and original run deadline;
recheck current principal tokenId/verified scopes/carrier, ACTIVE issuer, immutable source/resolved binding and budget; attach exact epoch only while source remains QUEUED;
return current latest DTO or a safe preparation failure; do not retain USER bearer or asynchronously mint a missing grant.
```

- Verification contribution: TEST-001/002 new issuer GREEN plus root submission integration.
- After this file: Actual owner SDK issuance path is usable synchronously, but not native tool execution.

#### File 11 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityProperties.java`

- Purpose: Implement strict immutable default-off feature configuration.
- Symbols: @ConfigurationProperties and record(enabled,timeout,concurrency,descriptors,bindings).
- Repository evidence: Existing ServiceExecutionProperties immutable record conventions.
- Dependencies and consumers: Existing domain descriptor and deployment BindingVO, Spring Binder only.
- Why now: Complete after Properties RED, consumed by new CFG.
- Contract/signature changes: Prefix stellar.tianji.agent.tools.authority; ignoreUnknownFields=false; exact record(boolean enabled,Duration timeout,int concurrency,List<ToolDescriptor> descriptors,List<TianjiToolGrantBindings.BindingVO> bindings).
- Input/output and state mapping: null timeout=>5s and0 concurrency=>4; copy null lists as empty; max256; enabled nonempty; validate all ranges.
- Error and edge behavior: No mutable config model or new Clock/JSON dependency; disable does not imply enabling other engine/tool capability.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
public record TianjiToolAuthorityProperties(boolean enabled, Duration timeout, int concurrency,
        List<ToolDescriptor> descriptors, List<TianjiToolGrantBindings.BindingVO> bindings) {
    // Strict prefix annotation; default timeout5s/concurrency4; bounds100ms..10s and1..8.
    // List.copyOf null-as-empty, max256 each; enabled requires both nonempty, immutable nested domain values.
}
```

- Verification contribution: TEST-001/003 Binder/default/immutability GREEN.
- After this file: Validated immutable configuration supports explicit controlled enablement.

#### File 12 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfiguration.java`

- Purpose: Create verified shared components and prove actual root chains before lifecycle activation.
- Symbols: Six fixed @Bean names; conditional feature/EnableConfigurationProperties; @Lazy(false) SmartInitializingSingleton callback.
- Repository evidence: Existing root factories/ObjectProvider and current ToolBindings/PDP/capability/IntentStore.
- Dependencies and consumers: No base issuer/auth dependency in tool object factories; providers to roots resolved only by final callback.
- Why now: Complete after CFG RED and locked tool/root helper signatures.
- Contract/signature changes: Require api/storage/execution-grants/execution-authorization flags; do not require runtime. SERVICE mappings additionally require service-execution+Source/Linked enabled and existing exact service bindings.
- Input/output and state mapping: Create verified toolCapabilityStore, bindings, issuer, PDP(close own executor), independently verified toolActionIntentStore. Static SERVICE metadata matches source issuerHash/tenant, linked same issuer/tenant/client/source and exact definition; never fake source objects.
- Error and edge behavior: Callback must resolve actual USER issuer, applicable SERVICE issuer and base run authorizer and compare public usesToolIssuer/usesToolAuthorization pointers to exact tool objects; missing/unconnected chain fails even if unusedBeans exist/global lazy. No cycles or shared SDK close.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
conditional tools.authority.enabled: require four prerequisite flags, validated props and qualified existing identity/SDK/resource dependencies;
@Bean toolCapabilityStore: exact ledger DataSource and verifyStorage; @Bean tianjiToolGrantBindings: fixed descriptors/mappings, static SERVICE source/link metadata only;
@Bean tianjiToolGrantIssuer and tianjiToolGrantAuthorizer: no dependency on old root issuers/auth; shared store/bindings/registry/resource; PDP destroyMethod close;
@Bean toolActionIntentStore: use exact PDP and verifyStorage independently;
@Bean @Lazy(false) tianjiToolAuthorityChainVerifier: SmartInitializingSingleton callback resolves ObjectProvider roots after singleton construction;
require actual UserIssuer/SvcIssuer.usesToolIssuer(toolIssuer) and BaseRunAuth.usesToolAuthorization(toolPdp), else fail startup before lifecycle.
```

- Verification contribution: TEST-001/003 actual factory/nonweb context readiness and identity GREEN.
- After this file: Feature=true has actual chain proof; default=false creates no tool/DS side effects.

#### File 13 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuer.java`

- Purpose: USER ensure invokes tools after old memory/preferences, including run grant reuse.
- Symbols: TianjiExecutionGrantIssuer appended constructor overload/field/usesTool helper and ensure/evaluate hook.
- Repository evidence: Exact existing class/constructors and current memory/preference/ordinary stages.
- Dependencies and consumers: New ToolIssuer or existing ToolPDP type; existing source/exception contracts remain.
- Why now: Complete after owning root hook test RED; optional old path compatibility stays.
- Contract/signature changes: All old signatures preserved, tools appended only; current identity checks extended to same tool component.
- Input/output and state mapping: Tool stage is always outer to runTicket reuse and later than prior memory/preferences or ordinary/memory decisions.
- Error and edge behavior: Do not insert a field/parameter ahead of old memory/preferences, bypass old denied state, swallow tool failure or change SERVICE identity.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Retain every old public/package constructor; add new overload with TianjiToolGrantIssuer appended after memory/preferences.
Keep USER memory/preferences parameter positions7/8 and delegates old constructors with tools=null; validate same ledger/issuer registry/AS resource.
ensure(): current=ensureInternal; current=memory.ensure if configured; current=preferences.ensure if configured; then tools.ensure if configured.
Expose public usesToolIssuer by exact pointer; do not let ensureInternal reused-ticket return skip the outer tool stage.
```

- Verification contribution: TEST-001 root hook/gate GREEN and TEST-003 old fixtures/context.
- After this file: Actual configured root path consumes tools; unconfigured old callers stay unchanged.

#### File 14 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuer.java`

- Purpose: SERVICE ensure invokes tools after old memory without altering linked admission contract.
- Symbols: TianjiServiceExecutionGrantIssuer appended constructor overload/field/usesTool helper and ensure/evaluate hook.
- Repository evidence: Exact existing class/constructors and current memory/preference/ordinary stages.
- Dependencies and consumers: New ToolIssuer or existing ToolPDP type; existing source/exception contracts remain.
- Why now: Complete after owning root hook test RED; optional old path compatibility stays.
- Contract/signature changes: All old signatures preserved, tools appended only; current identity checks extended to same tool component.
- Input/output and state mapping: Tool stage is always outer to runTicket reuse and later than prior memory/preferences or ordinary/memory decisions.
- Error and edge behavior: Do not insert a field/parameter ahead of old memory/preferences, bypass old denied state, swallow tool failure or change SERVICE identity.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Retain old constructors and append optional TianjiToolGrantIssuer only in a new overload; validate ledger/registry/resource/services identity.
ensure(authorized,principal,binding,command,run): retain original linked/service ensureInternal and error mapping.
After memory.ensure, call tools.ensure even if the old run grant was already current; no background retry or fabricated SERVICE source.
Expose public usesToolIssuer by exact pointer; preserve old method signature and constructor behavior with tools=null.
```

- Verification contribution: TEST-001 root hook/gate GREEN and TEST-003 old fixtures/context.
- After this file: Actual configured root path consumes tools; unconfigured old callers stay unchanged.

#### File 15 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizer.java`

- Purpose: Actual run chain checks tool scope after ordinary and memory authorization.
- Symbols: TianjiExecutionGrantAuthorizer appended constructor overload/field/usesTool helper and ensure/evaluate hook.
- Repository evidence: Exact existing class/constructors and current memory/preference/ordinary stages.
- Dependencies and consumers: New ToolIssuer or existing ToolPDP type; existing source/exception contracts remain.
- Why now: Complete after owning root hook test RED; optional old path compatibility stays.
- Contract/signature changes: All old signatures preserved, tools appended only; current identity checks extended to same tool component.
- Input/output and state mapping: Tool stage is always outer to runTicket reuse and later than prior memory/preferences or ordinary/memory decisions.
- Error and edge behavior: Do not insert a field/parameter ahead of old memory/preferences, bypass old denied state, swallow tool failure or change SERVICE identity.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Retain all old constructors and append optional TianjiToolGrantAuthorizer overload; old constructors delegate tools=null.
Validate exact ledger/registry/AS resource and expose public usesToolAuthorization pointer identity.
evaluate(request,boundary): evaluate ordinary USER/SERVICE first, then memory gate if configured; only ALLOW proceeds to tools.authorizeRun(request,boundary).
Propagate DENY/UNAVAILABLE correctly; no fake ToolRequest/lease and no direct claim before the missing-tool gate can reject.
```

- Verification contribution: TEST-001 root hook/gate GREEN and TEST-003 old fixtures/context.
- After this file: Actual configured root path consumes tools; unconfigured old callers stay unchanged.

#### File 16 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantConfiguration.java`

- Purpose: Wire the feature-enabled tool dependency into the actual existing root factory.
- Symbols: tianjiExecutionGrantIssuer signature appends ObjectProvider<TianjiToolGrantIssuer> as last parameter.
- Repository evidence: Existing @Bean factory method and flag-controlled memory/service providers.
- Dependencies and consumers: New configured tool Bean; original qualifiers/environment/arguments retain order.
- Why now: Complete after owning root and actual CFG integration tests RED.
- Contract/signature changes: Append final provider only, retain bean name and old arguments; no new constructor dependency in Configuration.
- Input/output and state mapping: If tools.authority.enabled true resolve provider and require exact same ledger/registry/resource (plus SERVICE source where applicable), construct append-tools overload.
- Error and edge behavior: If flagfalse do not dereference the provider, including null in direct legacy fixtures; if true missing provider/object fails closed, not unusedBean success.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Append the final ObjectProvider<TianjiToolGrantIssuer> parameter without reordering any prior argument.
Read stellar.tianji.agent.tools.authority.enabled; when false leave tools=null and do not access the provider at all.
When true require the provider/component, validate exact source identities and pass it into the new append-tools root constructor.
Preserve original memory/preferences/service selection and stable @Bean name; new chain verifier later proves actual usesToolIssuer identity.
```

- Verification contribution: TEST-003 direct fixture/actual nonweb context GREEN.
- After this file: The old root @Bean actually references the tool feature and enabled missing tool is failclosed.

#### File 17 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfiguration.java`

- Purpose: Wire the feature-enabled tool dependency into the actual existing root factory.
- Symbols: tianjiServiceExecutionGrantIssuer signature appends ObjectProvider<TianjiToolGrantIssuer> as last parameter.
- Repository evidence: Existing @Bean factory method and flag-controlled memory/service providers.
- Dependencies and consumers: New configured tool Bean; original qualifiers/environment/arguments retain order.
- Why now: Complete after owning root and actual CFG integration tests RED.
- Contract/signature changes: Append final provider only, retain bean name and old arguments; no new constructor dependency in Configuration.
- Input/output and state mapping: If tools.authority.enabled true resolve provider and require exact same ledger/registry/resource (plus SERVICE source where applicable), construct append-tools overload.
- Error and edge behavior: If flagfalse do not dereference the provider, including null in direct legacy fixtures; if true missing provider/object fails closed, not unusedBean success.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Append the final ObjectProvider<TianjiToolGrantIssuer> parameter without reordering any prior argument.
Read stellar.tianji.agent.tools.authority.enabled; when false leave tools=null and do not access the provider at all.
When true require the provider/component, validate exact source identities and pass it into the new append-tools root constructor.
Preserve original memory/preferences/service selection and stable @Bean name; new chain verifier later proves actual usesToolIssuer identity.
```

- Verification contribution: TEST-003 direct fixture/actual nonweb context GREEN.
- After this file: The old root @Bean actually references the tool feature and enabled missing tool is failclosed.

#### File 18 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionAuthorizationConfiguration.java`

- Purpose: Wire the feature-enabled tool dependency into the actual existing root factory.
- Symbols: tianjiExecutionGrantAuthorizer signature appends ObjectProvider<TianjiToolGrantAuthorizer> as last parameter.
- Repository evidence: Existing @Bean factory method and flag-controlled memory/service providers.
- Dependencies and consumers: New configured tool Bean; original qualifiers/environment/arguments retain order.
- Why now: Complete after owning root and actual CFG integration tests RED.
- Contract/signature changes: Append final provider only, retain bean name and old arguments; no new constructor dependency in Configuration.
- Input/output and state mapping: If tools.authority.enabled true resolve provider and require exact same ledger/registry/resource (plus SERVICE source where applicable), construct append-tools overload.
- Error and edge behavior: If flagfalse do not dereference the provider, including null in direct legacy fixtures; if true missing provider/object fails closed, not unusedBean success.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Append the final ObjectProvider<TianjiToolGrantAuthorizer> parameter without reordering any prior argument.
Read stellar.tianji.agent.tools.authority.enabled; when false leave tools=null and do not access the provider at all.
When true require the provider/component, validate exact source identities and pass it into the new append-tools root constructor.
Preserve original memory/preferences/service selection and stable @Bean name; new chain verifier later proves actual usesToolAuthorization identity.
```

- Verification contribution: TEST-003 direct fixture/actual nonweb context GREEN.
- After this file: The old root @Bean actually references the tool feature and enabled missing tool is failclosed.

#### File 19 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Declare the single-profile default-off feature settings without activating runtime.
- Symbols: stellar.tianji.agent.tools.authority enabled/timeout/concurrency/descriptors/bindings.
- Repository evidence: Current base application.yml keeps explicit default-off feature blocks; no environment profile variants.
- Dependencies and consumers: Strict new Properties; no changes to old keys/values.
- Why now: Only after properties and context gates are implemented.
- Contract/signature changes: Add authority.enabled:false, timeout:PT5S, concurrency:4, descriptors:[], bindings:[] under tools.
- Input/output and state mapping: The YAML defaults bind exactly to the immutable record and do not enable storage/runtime/native/identity scopes.
- Error and edge behavior: No secrets, example real mapping, production issuer/source registration, permission or automatic schema creation.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
stellar.tianji.agent.tools.authority:
  enabled: false
  timeout: PT5S
  concurrency: 4
  descriptors: []
  bindings: []
# Default false creates no tool Beans or storage access; operator mappings and four prerequisites are required before enablement.
```

- Verification contribution: TEST-003 actual YAML Binder/default-off/context checks and key comparison.
- After this file: A disabled installation preserves old behavior and touches no tool DataSource.

#### File 20 — `MODIFY README.md`

- Purpose: Describe real owner submission/gate wiring and precise enablement/runtime boundaries.
- Symbols: Current T3-07 progress and Agent feature settings/testing notes.
- Repository evidence: README already describes unconnected previous PDP adapter.
- Dependencies and consumers: Final implemented code/tests, not a deployment action.
- Why now: Update only after real chain tests GREEN so prior unconnected statement is replaced accurately.
- Contract/signature changes: Document four prerequisites, optional SERVICE metadata requirements, default-off settings, V13 preprovisioning/readiness, current owner-only issue and queued gate.
- Input/output and state mapping: Actual existing USER/SERVICE root factories invoke tools and base authorization consumes PDP; queue-only feature can be enabled without runtime.
- Error and edge behavior: Do not imply grants/permissions registered, actual IAM/crypto verified, native guards opened or tool dispatch/approval implemented.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Update prior unconnected-only wording to the actual configured submission/queued authorization chain established by this commit.
Document default-off property defaults, four prerequisite flags, additional SERVICE mapping/source rules and current owner/no-background-issuance policy.
Explain both stores readiness and eager actual-root chain proof; retain real IAM/DB/native/dispatch acceptance as pending.
```

- Verification contribution: TEST-003 source/README fidelity review.
- After this file: Users can distinguish real chain wiring from default activation and native execution.

#### File 21 — `CREATE docs/egon/evidence/2026-10-31-tool-authority-issuance-chain.md`

- Purpose: Record actual independent RED/GREEN, config-chain proof and remaining runtime boundaries.
- Symbols: Baseline23a,22-path scope, exact outputs/counts,17MC/10Rule rows and local aliases.
- Repository evidence: Prior Step evidence and per-Step execution gates.
- Dependencies and consumers: Actual final source/diff/commands; no prefilling execution success.
- Why now: After all component and actual factory/context verification.
- Contract/signature changes: No product contract, only concrete evidence and unresolved acceptance states.
- Input/output and state mapping: Record owner token/source/clock semantics, root hook order, same component pointers, defaults/strict Binder and actual disabled DS behavior.
- Error and edge behavior: Failures/skips honest; final full T3-07 still Partial, unchanged V13, no real IAM/crypto/DB/native/dispatch acceptance.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; touched technical Adapter/factory/value scope, no new dependencies or native capability.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve role names/immutable values, actual source validation, stable Bean/qualified factory inputs, separate time budgets, optional chain Adapter and exact architecture.

- Implementation pseudocode:

```text
Record baseline23a and every meaningful per-component RED/GREEN command with actual counts/exit status.
Record final actual-root configuration/identity/readiness/default-off tests and all22 paths, constructor compatibility and immutable V13.
Evaluate17 MC and10 Literal Rules independently with exact source/test evidence and justified N/A; track local requirements and fullT3-07Partial.
Confirm no new scripts/work harness, automatic service/database/browser/push or unrelated edits; commit only the declared logical slice.
```

- Verification contribution: TEST-003 final audit plus actual nonruntime test evidence.
- After this file: Full nonruntime logical feature has reviewable evidence before one semantic commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=TianjiToolGrantIssuerTest,TianjiToolAuthorityPropertiesTest,TianjiToolAuthorityConfigurationTest,TianjiExecutionGrantIssuerTest,TianjiServiceExecutionGrantIssuerTest,TianjiExecutionGrantAuthorizerTest,TianjiMemoryGrantIssuerTest,TianjiMemoryRunAuthorizationTest,TianjiServiceExecutionConfigurationTest,TianjiToolGrantBindingsTest,TianjiToolGrantAuthorizerTest,TianjiServiceExecutionBindingsTest,ToolCapabilityStoreTest,ToolActionIntentStoreTest,ToolExecutionStorageVerifierTest,ToolExecutionContractTest,ToolExecutionGrantTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: independent component real RED followed by full focused/root/context/domain GREEN; actual counts/errors/skips recorded, no automatic PG or BootServer.
- Failure returns to: owning test fixture if invalid RED, exact issuer/root/config/value file if missing behavior; undeclared dependency/core/native changes stop scope expansion.
- Completion criteria: all six local aliases have source/test proof, actual root chain verified, old constructors/default-off preserved,17MC/10Rules individually PASS or justified N/A, exactly22 paths and unchanged V13.
- Rollback: disable tools.authority.enabled and close own PDP resources; retain stored sealed receipts/history, no implicit grant deletion or SQL change. Revert only this semantic commit if appropriate; do not rewrite old history.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuer.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityProperties.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuer.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuer.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizer.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionAuthorizationConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolGrantIssuerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityPropertiesTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantIssuerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionGrantIssuerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiExecutionGrantAuthorizerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryGrantIssuerTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiMemoryRunAuthorizationTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiServiceExecutionConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `README.md`, `docs/egon/plan/2026-10-31-21-16-tool-authority-issuance-chain.md`, `docs/egon/evidence/2026-10-31-tool-authority-issuance-chain.md`
- Commit: `feat(agent): wire owner tool grants into submission and queued authorization`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 各组件RED/GREEN | tianji-service | §7 source-reactor选择各owner测试，最后选定整组 | 新issuer/props/cfg及旧root hooks | 合法fixture真实行为RED，再GREEN；旧断言保留 | 所属组件/test | 六别名；受控SDK/非运行 |
| TEST-002 身份/时间/重放 | tianji-service | 新IssuerTest的current SecurityContext/SDK/Clock/nanoTime断言 | USER/Rbac3、SERVICE/filter、scope/token/source窗口、epoch | 不保留bearer；复用也核owner；不足预算不issue；迟到不attach | 新Issuer | PLAN-REQ-001/002/005 |
| TEST-003 真链配置/兼容 | tianji-service | 新CFG/Props测试及旧direct fixtures/root tests，真实factory+非web ContextRunner | 四prereq、queue-only、双Store verify、static SERVICE、lazy callback实际指针 | default-off不触DS/provider；断链fail startup；旧ctor/参数/Memory顺序不变 | root/factory/cfg/YAML | PLAN-REQ-003/004/005/006；不启BootServer/DB |
| TEST-004 静态/规范 | tianji-service | `git diff --check`；22路径与V13摘要；17MC/10Rule逐行 | 当前全部差异 | 无Controller/AR/native/SQL/权限/依赖修改，secret/carrier不泄漏 | 所属文件 | 全部；静态 |
| TEST-005 Plan | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-21-16-tool-authority-issuance-chain.md --strict` | 格式/文件序/追踪 | exit0 | 本文 | 文档结构不等于实现或运行验收 |

测试使用实际SDK类型和factory、受控响应/存储/registry/当前安全上下文；没有真实HTTP/IdP/PDP/密文/PG验收。默认feature=false不启动服务、不建库、不读DS。复用现有命令，不新建scripts/work harness，文档生成脚本在完成后删除。扩大验证仅为实际失败/兼容疑点，不为本feature自动启动环境。

## 9. Migration, Compatibility, Rollout, and Rollback

不添加/修改SQL、迁移、manifest、生产数据或权限资源。V13必须由操作方先独立预置；启用只verify，绝不apply/repair。单base YAML新增authority默认false/5s/4/空列表，其他键原值不变。启用四项prereq API/storage/run grants/run auth，SERVICE映射额外依赖已启用service/linked来源；queue-only不要求runtime。policy注册/激活/角色grant由操作方另行管理，本步骤不执行。

旧公开/package构造器全部保留，新overload只append工具；USER memory/preferences维持index7/8；旧flag=false factory不访问新provider兼容原nullfixtures。new tool object factories不依赖base issuers/auth，最后非lazy singleton callback才解析roots，避免cycle并证明实际接入。关闭feature回退旧链及资源生命周期，保留已有sealed receipts/历史，不自动删票据或隐式重签。真实工具native guard仍拒绝，不能把签发+PDP门禁接链当工具已能执行。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07 | Step 1 | 新ToolIssuer/Test | TEST-001/002 | 真实USER/SVC carrier、独立purpose/cap/fullhash/falseSession、后置身份 |
| PLAN-REQ-002 | §7/13/T3-07 | Step 1 | ToolIssuer、原两issuer及tests | TEST-001/002 | 活票据复用、single30sWindow、HTTP预算、epoch/claimed/迟到保护 |
| PLAN-REQ-003 | §13/T3-07 | Step 1 | 原两issuer/baseAuth/三factory及tests | TEST-001/003 | 真实提交outer工具stage、ordinary/memory后tool gate、actual root身份 |
| PLAN-REQ-004 | §6/T3-07 | Step 1 | Props/CFG/Test/YAML | TEST-001/003/004 | strict defaults/prereq/twoStoreverify/@Lazyfalse实际chainproof |
| PLAN-REQ-005 | §13/T3-07 | Step 1 | 新Issuer/CFG及tests | TEST-002/003 | 静态已知SERVICE source/linked exactdefinition、实际require、源resource保留 |
| PLAN-REQ-006 | §17/T3-07 | Step 1 | 全测试/README/plan/evidence | TEST-001/003/004/005 | 构造器/default-off/真实factory兼容，native与真实环境边界诚实保留 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 真实USER只能stock Rbac3，SERVICE走verified linked carrier | ToolIssuer/Test | 主代理/owner已核原source路径与SDK | 主代理 | Closed：不加native IdP USER或后台补签旁路 |
| DEC-002 | root ctor/provider兼容与Bean无cycle | roots/factories/CFG | 已锁append参数/usesTool公开helper、工具factory不依赖roots、callback晚解析 | 主代理/CFG owner | Closed：精确22路径按独立RED barrier实施 |
| DEC-003 | 两种超时不同语义 | Props/Issuer | PDP100ms..10s、issue100ms..5s和整体30s窗口 | 主代理/issuer owner | Closed：不把props.timeout误作owner SDK预算 |
| RISK-001 | 真实身份/密文/SQL与部署权限未运行验收 | 后续验收 | 本次mockSDK/非web context，不启动环境 | 用户/主代理 | Tracked：保留runtime unverified，资源注册/激活/角色grant不自动做 |
| RISK-002 | 签发与gate已接不等于native工具可执行 | README/后续工作 | AR/native/dispatch不在本scope | 主代理 | Tracked：当前guard仍closed，后续独立交付 |

## 12. Review and Acceptance

本计划直接落实原T3-07尚缺的同步owner签发与真实排队链，保持现source affinity、构造器、Memory/Preference顺序和默认关闭。与前序unused库不同，actual roots身份必须在启动时被验证；这一差异是本逻辑feature的必要结果，不引入额外层或引擎。所有声明/兼容参数阶段只为可编译，各owner独立真RED后完成，最终一个22路径提交。实际结果、跳过/失败及完整T3-07 Partial由报告记录，不能预填真实环境通过。

### 12.5 Blocking Manual Check

以下是计划级可执行性评估，实施后必须按实际source/output逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AS/security技术库/root factories；22路径 | 只复用原submission/授权链，无Controller/AR/native/模块改变 | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 SDK/source/Registry/cap/PDP/Spring callbacks | 不复制认证/签发/账本/DI框架 | None |
| MC-DEP-001 | Applicable | PASS | 22路径无POM/build依赖变更 | 原JDK/SDK/Spring能力足够，无新依赖 | None |
| MC-NAME-001 | Applicable | PASS | Issuer/Properties/Configuration/Test与原types | 技术/配置/值职责明确，旧API名保留 | None |
| MC-VALID-001 | Applicable | PASS | stockprincipal/carrier/registry/immutableSource、StrictBinder、后置SDK | 每个现有源/协议/配置handoff有明确正反校验，无新HTTP DTO | None |
| MC-MODEL-001 | Applicable | PASS | 真不可变Properties record及List.copyOf、原domain复用 | 不新增实体/可变POJO或重复模型层 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter或跨层DTO映射 | 原domain/SDK构造与配置绑定，不建绕过MapStruct的转换层 | None |
| MC-LOG-001 | Not applicable | N/A | 原模式technical协议Adapter/config，无新增业务日志Service | bearer仅同步局部，不日志/持久化，不伪造业务Bean注解验收 | None |
| MC-BEAN-001 | Applicable | PASS | 六固定@Bean名、已知Qualifier、ObjectProviders、@Lazyfalse callback | factoryDI与技术manual构造分开，无字段注入/构造cycle，真实root指针可测 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/原SDK/Spring | 无新utility或HTTP/线程框架 | None |
| MC-JSON-001 | Applicable | PASS | 原Jackson owner SDK、真实typed requests、YAML Binder | 无新JSON栈/codec，秘密不落字段/日志 | None |
| MC-TIME-001 | Applicable | PASS | §4.9/TEST-002 | 一个30s wall+mono整体预算，issue/PDP超时分离，迟到不attach | None |
| MC-CONFIG-001 | Applicable | PASS | 单base YAML+strict Properties/Binding tests | default false/5s/4，旧键保持，无其他profile不一致 | None |
| MC-PATTERN-001 | Applicable | PASS | SDK Adapter+原可选ensure/run授权链装饰 | 实际变化点复用，不建第二调度/Activity引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确22路径、原V13/core/native不改 | 只当前逻辑feature，无scope扩张或启原native能力 | None |
| MC-TEST-001 | Applicable | PASS | §8独立RED/GREEN+真实factory/nonwebcontext+兼容fixture | 可证明actual chain，运行/身份/PG仍另验收 | None |
| MC-BLOCKER-001 | Applicable | PASS | 三项设计确认及全部独立行 | 当前API/身份/Beangraph已锁，无未决重大设计；真实运行边界保留 | None |

### 12.6 Final verdict

PASS — Ready for user review
