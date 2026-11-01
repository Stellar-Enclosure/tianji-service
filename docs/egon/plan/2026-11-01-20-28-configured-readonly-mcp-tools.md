# 配置化固定 readonly MCP 工具装配

| Field | Value |
| --- | --- |
| Document | `2026-11-01-20-28-configured-readonly-mcp-tools.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 20:28 Asia/Shanghai` |
| Updated | `2026-11-01 20:28 Asia/Shanghai` |
| Owner | 原用户授权按蓝图继续未完成工作并分步提交；Root 负责本派生设计、实现和验证 |
| Repository | `tianji-service` |
| Scope | Agent Server governed-process AS；Properties/Configuration/2 tests/YAML/README/Plan/Evidence 共 8 路径 |
| Source Requirement | 原用户继续蓝图剩余 MCP/T3-08..T4 的授权；蓝图 T3-07 / §11.3、§12、§13、§17 |
| Baseline Revision | `main@174851fd226d74a3adc653ed5ccca83ad39795ae` clean |
| Implements Spec | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本 Plan 是 Root 基于当前源码的 Review 设计，不冒充用户另行接受 API |
| Spec Revision | 2026-11-01 SHA-256 `ee3ba94453e491796786afdfc84dbea0a420a483fec300b8a0f1bfff17167bc5` |
| Effective Specs | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [Native readonly MCP](2026-11-01-19-44-native-readonly-mcp-tools.md)；[MCP Journal](2026-11-01-19-11-readonly-mcp-journal-recorder.md)；[MCP transport](2026-11-01-18-23-verified-readonly-mcp-transport.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本 Plan 将已提交的 native MCP library 作为 governed-process 的可选 MCP profile 列表（第六个 record 组件）接入现有 AS。Properties 保留旧4/5构造，新增 canonical6 `mcpBindings`；Configuration 新增两个 lazy named Beans，bridge 按 MCP/AA 四组合选择旧8/10或新9/11构造，eager verifier 只在 MCP configured 时证明实际 transport 六协作者与 bridge pointer。四处纯 metadata `requireMcpSlots` 阻止 lazy closure 绕过。无独立 mode flag、AR/API、依赖、DDL、权限或 runtime 启动。

一个 Step、八路径、一个提交。先 Properties RED/GREEN，再 Configuration RED；实现 Configuration 后添加 YAML leaf，再 Config GREEN；全部 Java 冻结后 Root 独占 final15。结果不能冒充真实 OAuth/HTTP/MCP/model/DB 验收。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md)
- Status: legacy blueprint；原用户只授权继续并分步提交；本 Step API/Bean 细节是 Root source-grounded Review 决策。
- Revision: SHA-256 `ee3ba94453e491796786afdfc84dbea0a420a483fec300b8a0f1bfff17167bc5`。
- Approval evidence: original continuation authority；implementation GO 需 Root full-read、strict/hash 和当前确认。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) | Legacy / above SHA | §11.3、§12、§13、§17 | fixed profiles、权限闭合、默认关闭、隐私/失败/验收 |
| Dependency | [Step31](2026-11-01-19-44-native-readonly-mcp-tools.md) | committed `174851f` | Process new9/11 + MCP affinity | AS bridge constructor target |
| Dependency | [Step29/30](2026-11-01-18-23-verified-readonly-mcp-transport.md) | committed | Backend19/transport/Journaling | reuse exact registry/transport contracts |

### 2.3 Superseded or excluded content

不增加 independent MCP mode、profile DTO、ToolSchema/Backend normalization、AR/Executor/Plugin、permission、dependency、DB/migration、API、model 或 runtime。Enabled 仍必须同时配置 Decision+START。

## 3. Effective Requirements and Acceptance

`PLAN-REQ-*` 仅追踪 legacy blueprint 要求。

| Requirement | Source | Effective statement | Observable acceptance | Impact |
| --- | --- | --- | --- | --- |
| `PLAN-REQ-001` | §12/§13 | finite immutable optional MCP profiles，旧4/5兼容 | canonical6 only ConstructorBinding；null/empty/copy/256；strict nested rebind | Properties/test |
| `PLAN-REQ-002` | §12/§13 | active MCP grant/profile 必须完整双向闭合且不能被 lazy 绕过 | 四 boundaries exact seven-tuple/full ref equality | Configuration/test |
| `PLAN-REQ-003` | §12/§13 | empty optional profiles 不实例化 registry/transport 或强制六 providers | empty returns null before provider `getObject`；old bridge variants | lazy factories/tests |
| `PLAN-REQ-004` | §12/§13/§17 | configured actual graph 必须证明 MCP6+bridge+worker，且不启动 IO | eager actual-chain checks；same owned HTTP/private pool/borrowed IAM | verifier/lifecycle tests |
| `PLAN-REQ-005` | §17 | default-off、旧 behavior、one commit、truthful finite tests | YAML leaf、bean count15、final15、exact8 | docs/evidence/gates |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Phase1 先写 Properties tests；compile prerequisite 仅把 record 改 canonical6、旧4/5显式传 `List.of()` MCP、`@ConstructorBinding` 移到 canonical6，保留旧 normalization/validation，canonical6 的 MCP component 暂不 normalization/limit/strict-loop。该 raw canonical6 可因新 component 未默认化而暂时不等于 compatibility constructors，这是预期 RED，不得削弱原相等/空列表断言；有效 RED 后才实现。Phase2 先写 Config tests；compile prerequisite 仅新增两个 `@Lazy` named factory signatures throwing UOE，并扩展现 bridge/verifier signatures（provider placement exact），旧 bodies 忽略新参数且测试调用补 null providers；旧行为保持。有效 RED 后实现四 closure/factories/bridge/verifier，再添加 base YAML leaf，最后 Config focused GREEN。

### 4.2 Test-first strategy

| Behavior | RED reason | Minimum GREEN |
| --- | --- | --- |
| canonical MCP profiles/YAML schema | MCP component raw/unprocessed，无 strict loop | normalize/copy/limit + four nested rebinds |
| optional lazy factories/slots | new factories UOE，existing bodies ignore providers | null-before-six + registry/transport + four closures |
| bridge/readiness/lifecycle | no MCP reference/affinity/six proof | union refs、9/11 ctor、eager checks、close ownership |

Compiler、fixture 或无关 YAML 解析错误不计有效 RED。新增 base MCP leaf 缺失是 Phase2 明确声明的配置缺失行为，应独立记录；YAML 会在 Config GREEN 前补齐。

### 4.3 Sequential and parallel boundaries

| Unit | Depends on | Parallel | Must not overlap | Reason |
| --- | --- | --- | --- | --- |
| Properties RED/GREEN | baseline | none | Config behavior | establishes typed profiles |
| Config RED | Props GREEN | none | Config production | locks actual graph |
| Config implementation + YAML | valid RED | docs | test assertions | YAML required before GREEN |
| docs/final15 | all GREEN | none | Java frozen | truthful evidence |

### 4.4 Commit boundaries

One commit: `feat(agent): assemble fixed read-only MCP tools`。No signature/UOE/test-only partial commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Element | Necessity/evidence | Direct/reuse alternative | Decision |
| --- | --- | --- | --- |
| `mcpBindings` sixth component | native bridge exists；AS lacks profiles | reuse existing BindingVO | implement |
| new mode/Profile DTO | no independent lifecycle | governed-process optional leaf | exclude |
| two lazy Beans | registry rejects empty；transport owns pool | mirror AA pattern | implement |
| four slot checks | lazy/direct/eager bypass boundaries | pure helper reuse | implement |
| new HTTP/storage verifier | shared owned HTTP/current 3 storage verifiers sufficient | reuse | exclude |
| ToolSchema normalization | existing value contract exact | strict binder only | exclude |

### 4.6 Change-unit Dependency Matrix

| Unit | Requirements | RED | Prerequisite | Produces | Step |
| --- | --- | --- | --- | --- | --- |
| Properties graph | 001/005 | PropertiesTest | canonical6 signature | finite MCP profiles | 1 |
| slot/factories | 002/003 | ConfigTest | Props GREEN | optional Beans/closure | 1 |
| bridge/readiness | 004/005 | ConfigTest/context | Step31 bridge | actual chain proof | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | existing Agent Server `integration.process` AS | preserve exact profile | only Properties/Configuration/YAML | Step1；MC-ARCH-001/SCOPE |
| Reuse/capability | AA lazy pattern、MCP Backend19/transport、shared HTTP | reuse；no new infra | two analogous optional Beans | Step1；MC-REUSE-001/DEP |
| Naming/model/validation/conversion | typed BindingVO/ToolSchema/record | canonical6 + strict Binder | no DTO/converter/manual map | Step1；MC-NAME-001/VALID/MODEL/CONVERT |
| Bean/logging/util/JSON/time/config | named factories/ObjectProvider/current YAML | stable names、lazy、no IO | same HTTP/lifecycle/error codes | Step1；MC-BEAN/LOG/UTIL/JSON/TIME/CONFIG |
| Business variation/pattern | existing Factory + eager verifier | optional Factory pattern | no new mode/loop | Step1；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| MCP registry | `TianjiFixedReadOnlyMcpToolBindings` | Backend19/full hash/schema validations | exact fit；empty rejected | lazy null-before-construct | no dependency | Step1；REUSE/DEP |
| MCP transport | `TianjiFixedReadOnlyMcpToolTransport` | six affinity methods/own close | exact fit | lazy factory | no dependency | Step1；REUSE |
| slot closure | existing `expectedSlots/slot/requireApprovalSlots` | seven-tuple pattern | MCP overload missing | one pure helper | no new type | Step1；VALID |
| HTTP/lifecycle | `tianjiGovernedProcessHttpClient` | NEVER/3s/shutdownNow | exact fit | share | no Bean beyond two | Step1；BEAN |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §12/§13 | existing semantic Properties/Configuration types | tests→sources | no carrier/type proliferation | source/final15 | 1 | PASS |
| Rule 2 | §12/§13/§17 | Binder/factory/bridge/verifier handoffs | tests→sources | strict nested/group closure/null providers | positive/negative tests | 1 | PASS |
| Rule 3 | §12/§13 | record/BindingVO/ToolSchema existing | Props test→source | immutable lists；no converter | reflection/copy tests | 1 | PASS |
| Rule 4 | §12/§13 | `@Configuration` factory methods | Config test→source | stable names/qualifiers；no new DI class | context tests | 1 | PASS |
| Rule 5 | §13 | JDK/Spring/existing types | two sources | no utility/dependency | import/POM review | 1 | PASS |
| Rule 6 | §13 | no new JSON；MCP schema typed | tests | no raw/private output | privacy tests | 1 | PASS |
| Rule 7 | §13 | sole base YAML | Config test→source→YAML | add exactly `mcp-bindings: []` | YAML parity test | 1 | PASS |
| Rule 9 | §12/§13 | existing optional Factory/eager chain | Config source/test | reuse pattern；no mode switch framework | context tests | 1 | PASS |
| Rule 10 | §13 | existing Duration backend/HTTP contracts unchanged | no new time field | preserve values | final regression | 1 | PASS |
| Rule 11 | §11.3/§12 | exact AS profile | every path | no AR/API/DB layer | path/static | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | `mcpBindings[].backend.timeout` configuration handoff | Duration | `Duration` | Not persisted | Duration | Not applicable: technical elapsed timeout | Not applicable: no UI | existing BackendVO accepts 100 ms through 30 s; bind explicit unit/ISO duration and preserve value; no calendar/DST | existing Boot Duration binding and transport nano/wall budget; no new Clock or rounding | existing BackendVO range and transport deadline contract unchanged | Step 1; TEST-001 Binder value equality/bounds and TEST-003 existing transport regression |

## 5. Change File Tree

| Op | Path | Current → final responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java` | 5-component profiles → MCP canonical/YAML strict tests | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java` | Decision/START/AA → optional MCP list/rebind | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java` | 13 Beans/old bridge → 15 Beans/four combinations/readiness | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java` | current AS → optional MCP factories/closure/actual chain | 1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | three profile lists → add fourth empty MCP profile list | 1 |
| MODIFY | `README.md` | native MCP library → default-off configured boundary | 1 |
| CREATE | `docs/egon/plan/2026-11-01-20-28-configured-readonly-mcp-tools.md` | strict V5 contract | 1 |
| CREATE | `docs/egon/evidence/2026-11-01-configured-readonly-mcp-tools.md` | actual evidence/limits | 1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Baseline

Tianji repo `main@174851fd226d74a3adc653ed5ccca83ad39795ae` clean；blueprint outside target Git；preserve unrelated/COLA changes；23 SQL immutable。

### 6.2 Build/test prerequisites

JDK25 exact path；`./scripts/verify-source-reactor.sh`；both `/tmp/tianji-compat-fix/settings.xml`；ADK content spans false；OTel exporters none。Root alone runs Maven after all Java freezes。

### 6.3 Immutable decisions and exact signatures

- enabled still requires Decision+START；MCP optional；no separate flag。
- canonical6 list order ends with approval then MCP；only canonical6 ConstructorBinding/no Autowired。
- Bridge adds MCP providers immediately after existing Decision/Start bindings。
- Eager verifier places the two qualified MCP providers immediately after its existing Decision/Start bindings inputs；only existing `TianjiToolAuthorityProperties authority` is inserted immediately before `TianjiGovernedProcessToolProperties properties`；no new Bean。
- Empty transport factory returns before six providers；configured path proves six + bridge pointer；borrowed IAM never closed。

```java
public record TianjiGovernedProcessToolProperties(
    boolean enabled, int concurrency,
    List<TianjiFixedDecisionToolBindings.BindingVO> decisionBindings,
    List<TianjiFixedBpmnStartToolBindings.BindingVO> startBindings,
    List<TianjiFixedToolApprovalBindings.BindingVO> approvalBindings,
    List<TianjiFixedReadOnlyMcpToolBindings.BindingVO> mcpBindings) { }

@Bean("tianjiGovernedProcessMcpBindings") @Lazy
TianjiFixedReadOnlyMcpToolBindings tianjiGovernedProcessMcpBindings(
    TianjiToolAuthorityProperties authority, TianjiGovernedProcessToolProperties properties,
    @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings grants, Environment environment);

@Bean(name="tianjiGovernedProcessMcpTransport", destroyMethod="close") @Lazy
TianjiFixedReadOnlyMcpToolTransport tianjiGovernedProcessMcpTransport(
    TianjiGovernedProcessToolProperties properties,
    @Qualifier("tianjiGovernedProcessMcpBindings") ObjectProvider<TianjiFixedReadOnlyMcpToolBindings> bindings,
    @Qualifier("idpServiceOAuth2Client") ObjectProvider<IdpServiceOAuth2Client> oauth,
    @Qualifier("tenantIssuerAuthenticationManagerComponent") ObjectProvider<TenantIssuerAuthenticationManagerComponent> authentication,
    @Qualifier("verifiedPrincipalConverter") ObjectProvider<VerifiedPrincipalConverter> converter,
    @Qualifier("trustedIssuerRegistry") ObjectProvider<TrustedIssuerRegistry> issuers,
    @Qualifier("tianjiGovernedProcessHttpClient") ObjectProvider<HttpClient> http,
    Environment environment);
```

Bridge adds after `decisions, starts`:

```java
@Qualifier("tianjiGovernedProcessMcpBindings") ObjectProvider<TianjiFixedReadOnlyMcpToolBindings> mcpBindings,
@Qualifier("tianjiGovernedProcessMcpTransport") ObjectProvider<TianjiFixedReadOnlyMcpToolTransport> mcpTransports
```

Verifier adds the same providers after `decisions, starts`, and adds:

```java
TianjiToolAuthorityProperties authority,
TianjiGovernedProcessToolProperties properties
```

in that order at the existing properties position。

### 6.4 Plan Clarifications

| ID | Root Review decision | Evidence | Boundary |
| --- | --- | --- | --- |
| CLAR-001 | four `requireMcpSlots` boundaries | lazy/direct/eager paths | pure metadata only |
| CLAR-002 | in-memory YAML explicit empty schema test | ToolSchema nonnull components | no ToolSchema change |
| CLAR-003 | configured bridge chooses old8/10 or new9/11 | Step31 API | no overload/Bean duplication |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 将 fixed readonly MCP 接入 governed-process AS

- Requirements: `PLAN-REQ-001, PLAN-REQ-002, PLAN-REQ-003, PLAN-REQ-004, PLAN-REQ-005`
- Dependencies: committed Step29–31；current AS/AA lazy pattern。
- Baseline state: governed-process has Decision/START/AA only，13 Beans。
- Observable outcome: optional MCP profiles assemble a verified actual bridge/transport chain；empty remains old behavior。
- End state: default-off 15-Bean AS library；no runtime/permission activation。
- Test-first gate: Required — two signature-only compile prerequisite phases then behavior RED。
- Manual Checks: `MC-ARCH-001,MC-REUSE-001,MC-DEP-001,MC-NAME-001,MC-VALID-001,MC-MODEL-001,MC-CONVERT-001,MC-LOG-001,MC-BEAN-001,MC-UTIL-001,MC-JSON-001,MC-TIME-001,MC-CONFIG-001,MC-PATTERN-001,MC-SCOPE-001,MC-TEST-001,MC-BLOCKER-001`
- Literal Rules: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java`
- Purpose: define canonical6、strict MCP graph and YAML schema RED。
- Symbols: constructor reflection/copy/limit；MCP fixture；`YamlPropertySourceLoader` + in-memory `ByteArrayResource` positives/negatives；existing default compatibility only（base MCP leaf assertion belongs File3）。
- Repository evidence: existing Binder/Yaml loader/second-index/AA strict fixtures。
- Dependencies and consumers: existing MCP BindingVO/BackendVO/ToolSchema；observes File2。
- Why now: Phase1 test contract before MCP normalization/loop。
- Contract/signature changes: canonical6 only ConstructorBinding；old4/5 exact compatibility。
- Input/output and state mapping: YAML/profile lists → immutable typed graph。
- Error and edge behavior: null/257/null element；omitted/bare/missing fields/required reject during initial nested construction without requiring the outer fixed code；unknown children reaching the strict EnvironmentAware rebind use the existing fixed configuration error；no private sentinel leakage。
- Standards impact: `MC-VALID-001/MC-MODEL-001/MC-JSON-001/MC-CONFIG-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 5,Rule 6,Rule 7,Rule 11`。
- Implementation pseudocode:
```java
add canonical6 and old4/5 reflection/copy/limit assertions
load in-memory YAML with fields:{} and required:[] for both schemas; assert exact binding
remove whole/bare/fields/required; assert initial binding/startup rejection and no private sentinel
add unknown nested child at index1 that reaches setEnvironment; assert exact governed fixed invalid error
```
- Verification contribution: Props RED/GREEN logs。
- After this file: with signature-only File2 it fails for unprocessed MCP behavior, not compile/setup。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java`
- Purpose: own finite immutable MCP profiles and strict nested binding。
- Symbols: canonical6、old4/5、MCP loop/imports。
- Repository evidence: current canonical5/strict Decision/START/AA loops。
- Dependencies and consumers: File1/Configuration。
- Why now: minimum Phase1 GREEN。
- Contract/signature changes: add final list component；canonical annotation only。
- Input/output and state mapping: null→empty，copy，≤256；enabled rule unchanged；backend/tool/input/output exact equals。
- Error and edge behavior: every bind/equals/private exception → existing configuration invalid。
- Standards impact: `MC-ARCH-001/MC-VALID-001/MC-MODEL-001/MC-UTIL-001/MC-CONFIG-001/MC-SCOPE-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 7,Rule 11`。
- Implementation pseudocode:
```java
old4 delegates canonical6 with approval=null,mcp=List.of(); old5 with mcp=List.of()
canonical6 normalizes all lists and enforces independent bounds/current enabled rule
for each MCP index strict-bind backend,tool,backend.input-schema,backend.output-schema; require equality
```
- Verification contribution: Properties focused GREEN/final15。
- After this file: typed MCP config graph is GREEN；no Bean exists yet。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java`
- Purpose: define factories、four closures、four constructor combinations、actual readiness/lifecycle RED。
- Symbols: MCP fixtures/providers/Chain；15 Bean count；base YAML `mcp-bindings: []` assertion；existing three storage-verifier construction count；all existing calls extended with null providers/authority。
- Repository evidence: current Fixture/ApprovedFixture/Chain/globalLazy/qualifier/prototype/lifecycle tests。
- Dependencies and consumers: Props GREEN；Step31 bridge；observes Files4/5。
- Why now: Phase2 contract before factory bodies。
- Contract/signature changes: two new Bean signatures；bridge/verifier extra providers；verifier authority before properties。
- Input/output and state mapping: authority/grants/profiles → fixed registry/transport/selected refs/bridge/eager proof。
- Error and edge behavior: empty before six；seven-tuple drift；four combinations；seven broken affinities；no startup IO；close ownership。
- Standards impact: all `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: all `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```java
compile with UOE new factories and ignored new providers; prove old tests stay green
test empty providers never getObject and active grant/profile exact closure at four boundaries
test old8/10,new9/11 pointers; mutate MCP6+bridge; actual lazy context close/no IO; assert 15 Beans
```
- Verification contribution: Config RED/GREEN/final15。
- After this file: valid RED for UOE/missing closure/readiness and the declared missing base MCP leaf. File5 supplies that leaf before Config GREEN; unrelated YAML parse/setup errors remain invalid RED。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java`
- Purpose: assemble optional MCP registry/transport/bridge and prove actual chain。
- Symbols: two Beans；requireMcpSlots/slot overload；modified Decision/bridge/verifier signatures/bodies。
- Repository evidence: current AA lazy factories、expectedSlots、actual chain verifier、shared HTTP。
- Dependencies and consumers: Files2/3，Step29/31 APIs。
- Why now: minimum Phase2 GREEN after valid Config RED。
- Contract/signature changes: exact §6.3 provider placement；no same-name overload/new Bean beyond two。
- Input/output and state mapping: profiles/grants exact slots → registry → transport → refs → constructor → verifier。
- Error and edge behavior: prerequisites first；empty null before six；four closure calls；configured MCP6+bridge；empty no unused proof；fixed errors。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```java
requireMcpSlots(authority, properties, grants) {
    registered = registered(authority); // existing full-reference map
    expected = expectedSlots(authority, grants, ToolPolicy.Risk.READ_ONLY,
        ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY);
    configured = new HashSet<List<Object>>();
    for (var binding : properties.mcpBindings()) {
        descriptor = registered.get(binding.tool());
        require descriptor != null && descriptor.policy().risk() == READ_ONLY;
        require descriptor.policy().executionResourceKey().equals(ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY);
        require configured.add(slot(binding)); // exact new MCP BindingVO overload, reject duplicates
    }
    require expected.equals(configured);
}
slot(TianjiFixedReadOnlyMcpToolBindings.BindingVO binding) = List.of(
    binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(),
    binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.tool());
Decision factory always calls requireMcpSlots; MCP bindings calls it before empty return
transport returns null before six providers or constructs with shared HTTP/concurrency
bridge checks slots before providers, unions MCP refs, chooses old8/10 or new9/11
eager verifier receives authority before properties, checks slots, worker/bridge, and configured MCP6+pointer
```
- Verification contribution: Config focused GREEN/final15/static Bean/lifecycle review。
- After this file: code behavior ready；base YAML assertion awaits File5。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Purpose: expose sole default-off empty MCP leaf。
- Symbols: `stellar.tianji.agent.tools.governed-process.mcp-bindings: []`。
- Repository evidence: sole base profile contains Decision/START/AA empty lists。
- Dependencies and consumers: Config test base-YAML assertion/Properties binder。
- Why now: after Config implementation but before Config GREEN。
- Contract/signature changes: one empty key，no values/enablement。
- Input/output and state mapping: absent operator profiles → immutable empty MCP list。
- Error and edge behavior: no MCP Bean/provider/IO activation。
- Standards impact: `MC-CONFIG-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 6,Rule 7,Rule 11`。
- Implementation pseudocode:
```yaml
stellar:
  tianji:
    agent:
      tools:
        governed-process:
          enabled: false
          concurrency: 4
          decision-bindings: []
          start-bindings: []
          approval-bindings: []
          mcp-bindings: []
```
- Verification contribution: Config GREEN/base defaults/final15。
- After this file: base config and code are key-parity complete。

#### File 6 — `MODIFY README.md`
- Purpose: document default-off MCP profile/factory/closure/ownership and live boundary。
- Symbols: governed-process MCP assembly/evidence paragraphs。
- Repository evidence: README currently stops at programmatic native MCP。
- Dependencies and consumers: actual GREEN/evidence；operators/developers。
- Why now: document only implemented behavior。
- Contract/signature changes: describe canonical6/new Beans/four combinations without activation promise。
- Input/output and state mapping: operator profile → registry/transport/native bridge metadata only。
- Error and edge behavior: empty no providers；configured no startup IO；borrowed/owned lifecycle；no permission grant。
- Standards impact: `MC-CONFIG-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 6,Rule 7,Rule 10,Rule 11`。
- Implementation pseudocode:
```markdown
Document exact empty/configured graph, slot closure, Bean names and constructor selection.
Document shared HTTP/private pool/borrowed IAM and no startup IO.
Link evidence and list IAM/network/model/DB/runtime as unverified.
```
- Verification contribution: source/README/Evidence consistency。
- After this file: docs match default-off assembly。

#### File 7 — `CREATE docs/egon/plan/2026-11-01-20-28-configured-readonly-mcp-tools.md`
- Purpose: save strict V5 contract。
- Symbols: requirements/clarifications/8 paths/gates/17MC/10Rules。
- Repository evidence: baseline/blueprint/predecessor Plans。
- Dependencies and consumers: Root full-read/strict/hash before GO。
- Why now: implementation requires reviewed source of truth。
- Contract/signature changes: docs only。
- Input/output and state mapping: design → ordered RED/GREEN/evidence。
- Error and edge behavior: strict/drift blocks；no user-acceptance fabrication。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```text
Copy to exact destination; full-read baseline/design/exclusions.
Run strict validator; repair without semantic drift; record SHA.
Permit RED only after Root Review GO.
```
- Verification contribution: strict PASS/SHA。
- After this file: executable Review Plan。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-configured-readonly-mcp-tools.md`
- Purpose: record actual RED/GREEN/final/static/commit/live limits。
- Symbols: baseline/PlanSHA/logs/counts/Bean/slot/privacy/lifecycle/path evidence。
- Repository evidence: predecessor Evidence format。
- Dependencies and consumers: only actual outputs；Root/user review。
- Why now: acceptance needs reproducible facts。
- Contract/signature changes: docs only。
- Input/output and state mapping: commands/diffs/context observations → evidence。
- Error and edge behavior: compiler/fixture failures separate；no live PASS prefill。
- Standards impact: `MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```markdown
Record Plan SHA and distinct Props RED/GREEN, Config RED/GREEN and final15 logs/counts.
Record four slot boundaries, provider noninteraction, Bean count, affinity mutations and lifecycle.
Record exact8/SQL/POM/config checks, commit result and all unverified live boundaries.
```
- Verification contribution: all requirements/MC closure。
- After this file: truthful acceptance artifact。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: exact five commands in §8。
- Expected result: valid Props/Config RED；focused GREEN；final15 exit0；15 Beans；no unexpected failures/errors/skips；static/path checks pass。
- Failure returns to: File1/2 Properties；File3/4 Config；File5 YAML；File6 docs；Spec/user only on material drift。
- Completion criteria: exact8、strict PASS、four closures、old behavior、no startup IO、final15 and Evidence complete。
- Rollback: eight-path source/config revert；no data/permission migration。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolPropertiesTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolProperties.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiGovernedProcessToolConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `README.md`, `docs/egon/plan/2026-11-01-20-28-configured-readonly-mcp-tools.md`, `docs/egon/evidence/2026-11-01-configured-readonly-mcp-tools.md`。
- Commit: `feat(agent): assemble fixed read-only MCP tools`

## 8. Test, Validation, and Quality Gates

| Test ID | Scope |
| --- | --- |
| `TEST-001` | Properties RED/GREEN canonical/binding/YAML |
| `TEST-002` | Configuration RED/GREEN factories/slots/bridge/readiness/lifecycle |
| `TEST-003` | final15 + static/path/strict |

Exact commands from Tianji root:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiGovernedProcessToolPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-configured-mcp-properties-red.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiGovernedProcessToolPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-configured-mcp-properties-green.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiGovernedProcessToolConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-configured-mcp-configuration-red.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiGovernedProcessToolConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-configured-mcp-configuration-green.log 2>&1
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiGovernedProcessToolPropertiesTest,TianjiGovernedProcessToolConfigurationTest,TianjiReadOnlyDecisionToolPropertiesTest,TianjiReadOnlyDecisionToolConfigurationTest,TianjiToolAuthorityPropertiesTest,TianjiToolAuthorityConfigurationTest,TianjiServiceExecutionBindingsTest,TianjiAgentServerApplicationTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,ToolInvocationRecorderTest,ReadOnlyMcpToolTransportTest,TianjiFixedReadOnlyMcpToolBindingsTest,TianjiFixedReadOnlyMcpToolTransportTest,TianjiMcpJsonRpcTransportTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-configured-readonly-mcp-tools-final.log 2>&1
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | Tianji | first/second | Properties | valid RED then exit0 | Files1/2 | module |
| TEST-002 RED/GREEN | Tianji | third/fourth | Configuration | valid RED then exit0 | Files3–5 | module/context |
| TEST-003 | Tianji | fifth after Java freeze | exact15 | exit0/counts | owner | source reactor |
| static/path | Tianji | `rg`, `git status`, `git diff --check` | exact8/23SQL/POM/YAML | no extra/stub/secret/SQL/POM | stop | static |
| Plan | Tianji | strict validator exact destination | V5 | PASS+SHA | File7 | docs |
| live | user-controlled | not authorized | runtime | not run | later | live |

No `scripts/work` harness；existing tests/source reactor/static tools suffice。

Exact immutable SQL check from the Tianji root, covering all Agent/Process/Worker files:

```bash
python3 - <<'PY'
from pathlib import Path
import hashlib, subprocess
baseline = '174851fd226d74a3adc653ed5ccca83ad39795ae'
paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', baseline], text=True).splitlines()
paths = [p for p in paths if '/src/main/resources/db/' in p and p.endswith('.sql')]
before = {p: hashlib.sha256(subprocess.check_output(['git', 'show', baseline + ':' + p])).hexdigest() for p in paths}
after = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('.').glob('**/src/main/resources/db/**/*.sql')}
assert len(before) == 23 and before == after
print('PASS: all 23 SQL files match the baseline byte-for-byte')
PY
```

## 9. Migration, Compatibility, Rollout, and Rollback

N/A DDL/dependency/permission。Old4/5 Properties and old bridge paths remain；new profiles default empty。No application/IO activation。Rollback exact8；no data migration。Later real IAM/MCP/deployment acceptance separate。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step/files | Tests/evidence |
| --- | --- | --- |
| PLAN-REQ-001 | 1；Files1/2/5 | TEST-001/final15 |
| PLAN-REQ-002 | 1；Files3/4 | slot drift/final15 |
| PLAN-REQ-003 | 1；Files3/4 | provider noninteraction/context |
| PLAN-REQ-004 | 1；Files3/4 | MCP6+bridge/lifecycle |
| PLAN-REQ-005 | 1；all8 | YAML/final15/static/strict |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Evidence/action | Status |
| --- | --- | --- | --- |
| R1 | empty YAML schema Boot behavior uncertain | actual in-memory positive/negative before implementation | closed by gate |
| R2 | lazy profiles bypass closure | four always-on boundaries | closed |
| R3 | optional providers force IAM/IO | empty-before-six tests | closed |
| R4 | global-lazy hides broken actual chain | eager MCP6+bridge+worker mutations | closed |
| B1 | source drift | recheck baseline/signatures | closed at baseline |

No open Step32 design blocker；API remains derivative Review，not user-accepted Spec。

Pre-GO source review clarification: exact MCP slot selection excludes every unrelated Decision/query READ_ONLY grant. Tests must prove grant-without-profile and profile-without-grant fail, unrelated registered READ_ONLY families are ignored, an ungranted/unprofiled MCP descriptor is allowed, and duplicate/full-reference drift fails. Malformed nested schema construction fails before the outer Properties EnvironmentAware hook; those initial failures require rejection and no private sentinel, not an unreachable governed outer error code. Exact fixed-code assertions belong to a strict-rebind failure that reaches that hook. No ToolSchema/Backend/DTO/normalizer expansion is authorized.

## 12. Review and Acceptance

### 12.1–12.4 Fidelity/executability

Plan preserves original continuation, old modes/constructors/validation, exact8 and one commit。Normal: full profile→slot→registry→transport→bridge→eager proof。Failure: active MCP grant + empty profile fails mandatory Decision factory。Edge: empty profile returns before six providers；explicit empty schema binds while bare/omitted fails。Live IO/runtime unverified。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AS exact2 Java/YAML | profile preserved | None |
| MC-REUSE-001 | Applicable | PASS | existing registry/transport/HTTP/pattern | no duplicate infra | None |
| MC-DEP-001 | Applicable | PASS | no POM/dependency | none added | None |
| MC-NAME-001 | Applicable | PASS | semantic Bean/component names | no carrier | None |
| MC-VALID-001 | Applicable | PASS | strict Binder/four slots/providers | closed handoffs | None |
| MC-MODEL-001 | Applicable | PASS | canonical record/list semantics | immutable MCP list | None |
| MC-CONVERT-001 | Not applicable | N/A | no conversion boundary | converter unnecessary | None |
| MC-LOG-001 | Not applicable | N/A | no logging change | unchanged | None |
| MC-BEAN-001 | Applicable | PASS | exact names/qualifiers/lazy/destroy | 15 coherent Beans | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Spring existing only | allowlist | None |
| MC-JSON-001 | Applicable | PASS | typed schema/no raw JSON output | privacy retained | None |
| MC-TIME-001 | Applicable | PASS | TIME-001; Binder Duration equality/bounds; existing transport regression | existing timeout value/precision preserved without new Clock or conversion | actual execution evidence required |
| MC-CONFIG-001 | Applicable | PASS | canonical6 + base leaf/YAML tests | key parity/default-off | None |
| MC-PATTERN-001 | Applicable | PASS | existing Factory/eager chain | no new mode/loop | None |
| MC-SCOPE-001 | Applicable | PASS | exact8/exclusions | minimum safe change | None |
| MC-TEST-001 | Applicable | PASS | TEST-001..003/final15 | normal/failure/edge | execute actual |
| MC-BLOCKER-001 | Applicable | PASS | §11/all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

Planning PASS only。Root must copy、full-read、strict/hash、review before RED GO。

## Java / CQE contract review

Canonical record/list and internal typed validation apply；no PO/enum/converter/DDL/uniqueness/Event。Existing Agent AS architecture and Journal CQE remain；no generator/MP action。

## Backend generation and dependency evidence

Generator reference reviewed；Agent profile/no DDL/CRUD/dependency means generator/manifest/SQL journal N/A。Any POM/SQL/generated path blocks commit。
