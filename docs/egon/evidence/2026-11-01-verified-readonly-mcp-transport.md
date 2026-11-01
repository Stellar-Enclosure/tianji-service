# 可信readonly MCP安全JSON传输执行证据

Plan: [2026-11-01-18-23-verified-readonly-mcp-transport.md](../plan/2026-11-01-18-23-verified-readonly-mcp-transport.md)，Review/原用户授权；baseline81e72d896f7d87e93516750e8f6e913750aa576e；SpecSHAb91c77593168b717aa4fad350e491b5c58a8ca9a064ef0b98aadfffed605abe7。

本Step已验证：七类200项通过、零失败/跳过，逐条门禁与独立复核已闭合；完整平台仍有后续工作。下文保留实际RED/GREEN、失败修复与边界证据。

初始锁定记录：Root全文/strict/resources/checklist锁定Plan，source baseline81e72d8。SPI仅UOE编译声明+tests先获RED GO，wrapper当时仅仓库外准备；Root所有Java冻结后统一Maven。初始Plan4650...，trace常量精化3015...，CLAR001 raw工具错误/projection与分类优先级后SHA `759937d82aa7f212de051522950a9db09e6449fe961a05e834cf1e819e2580b4`。无新依赖/SQL/配置/journal/native；SDK隐私/实际SERVICE/window/bytes均由下文实际测试核验。

## Requirements

- PLAN-REQ-001: PASS - Official SDK retains lifecycle/IDs/correlation; opaque and minimal typed projections protect its logs (91 SPI cases).
- PLAN-REQ-002: PASS - Actual credential helper verifies fixed TENANT SERVICE target/scopes/source and current key/ACTIVE trust; helper changes only public visibility.
- PLAN-REQ-003: PASS - Owner/active/once guards consume before microsecond deadline validation; original wall/monotonic window, JDK byte limiting and clear-before-cancel are tested.
- PLAN-REQ-004: PASS - Protocol/server/session/full-tool hash/schema closed; known observation reaches callback before cleanup/current postchecks; no retry.
- PLAN-REQ-005: PASS - Exact eight paths, seven classes/200 tests passed; 23 immutable SQL files unchanged; no POM/config/journal/native/Bean/permission change.

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AS integration/mcp uses the existing AR port and official client lifecycle | No new business layer, Agent loop or protocol scheduler | None |
| MC-REUSE-001 | Applicable | PASS | Credential helper, BackendVO/hash, ToolSchema, TraceContext, SDK and JDK25 limiting reused | No parallel authentication or HTTP framework | None |
| MC-DEP-001 | Applicable | PASS | Eight-path diff has no POM; source reactor and 200 cases pass | No dependency added or upgraded | None |
| MC-NAME-001 | Applicable | PASS | Technical Transport types plus PreparedVO/OpaqueResultVO/WindowVO; existing TargetVO/CredentialVO visibility only | No new ordinary business POJO | None |
| MC-VALID-001 | Applicable | PASS | prepare/deliver/schema/callResult/current/narrowCallDeadline/exchange; 91 SPI and 20 wrapper cases | Wire, schema, credential, thread, transaction, window and byte boundaries closed | None |
| MC-MODEL-001 | Applicable | PASS | Three immutable private value records; CallScope is locked internal lifecycle state | No ordinary entity, business DTO or Converter added | None |
| MC-CONVERT-001 | Not applicable | N/A | Official SDK typed constructors perform protocol projection; no PO/entity/business DTO mapping | Business MapStruct/BaseConverter trigger absent | None |
| MC-LOG-001 | Applicable | PASS | Opaque toString, fixed JSONRPC errors/Failure and cleanup WARN; multilevel sentinel/throwable cases pass | No raw body/meta/instructions/bearer in SDK logs; no production global logger mutation | None |
| MC-BEAN-001 | Not applicable | N/A | Explicit manual constructors only; no Spring managed type/DI/Bean factory added | Bean naming/injection concern absent | None |
| MC-UTIL-001 | Applicable | PASS | JDK25, Boot Jackson3 and existing Reactor/SDK/IAM/TraceContext imports | No generic utility framework added | None |
| MC-JSON-001 | Applicable | PASS | Existing Jackson3 strict duplicate/trailing/depth/number guards and safe SDK projections | No ordinal or generic inbound reflection mapper | None |
| MC-TIME-001 | Applicable | PASS | Clock/Instant/Duration/ChronoUnit and original nano origin; rollback/microsecond/late-fact cases | UTC instants and elapsed budget never reset; cleanup cannot extend authority | None |
| MC-CONFIG-001 | Not applicable | N/A | No YAML/profile/feature flag path in exact scope | Environment keys and enabled state unchanged | None |
| MC-PATTERN-001 | Applicable | PASS | Official McpClientTransport Adapter plus private scoped capability/Exchange | Separates protocol privacy from credential/window ownership; official lifecycle retained | None |
| MC-SCOPE-001 | Applicable | PASS | Exact eight paths; helper equals HEAD after removing public modifiers; all 23 SQL hashes match | No journal/native/POM/config/permission or unrelated refactor | None |
| MC-TEST-001 | Applicable | PASS | Independent valid RED then GREEN; final exit0/20.194s, seven classes/200 cases and no skips | Native SDK, mock HTTP and real JDK BodySubscriber; no live startup | None |
| MC-BLOCKER-001 | Applicable | PASS | All 17 MC and 10 Rule rows checked; independent CLAR001 and bearer-lifetime findings resolved | No unresolved Step blocker; future platform/live boundaries retained | None |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Technical Transport names and immutable PreparedVO/OpaqueResultVO/WindowVO | Compile, type inventory and 200 cases | Semantic suffixes match their responsibilities | None |
| Rule 2 | Applicable | PASS | Strict JSON/schema/hash/session and credential/once/window/thread/transaction handoffs | 91 SPI and 20 wrapper cases with actual credential helper | Explicit technical protocol validation; no new business Bean Validation DTO | None |
| Rule 3 | Applicable | PASS | Immutable value records and private locked CallScope; no entity or Converter | Per-type source review and compilation | No ordinary business class model requiring Lombok or business conversion requiring BaseConverter | None |
| Rule 4 | Applicable | PASS | Manual technical transport uses existing SLF4J API for fixed WARN; no business Service/DI | Dependency review, log sentinels, close/cancel tests | Business-class Slf4j/Bean/Lombok-DI trigger absent; no Lombok dependency introduced | None |
| Rule 5 | Applicable | PASS | JDK25 and existing Boot Jackson/SDK/Reactor/IAM APIs; no generic utility class | No POM diff; source reactor compilation | Existing dependencies and standard APIs reused | None |
| Rule 6 | Applicable | PASS | Boot Jackson3 wire parsing and three minimal SDK DTO/opaque projections | Strict JSON/duplicate/trailing/numeric/log tests | No new persistent/frontend enum or ordinal encoding | None |
| Rule 7 | Not applicable | N/A | No YAML/profile/configuration keys changed | Exact eight-path scope | No environment configuration concern touched | None |
| Rule 9 | Applicable | PASS | Official SPI Adapter and request-local scoped capability/Exchange | Lifecycle/owner/once/current identity/cancel tests | Required variation isolated without a second execution engine | None |
| Rule 10 | Applicable | PASS | java.time Clock/Instant/Duration/ChronoUnit and System.nanoTime elapsed | Microsecond, wall rollback, original window and late-fact tests | No java.util date API or restarted budget | None |
| Rule 11 | Applicable | PASS | Existing AR/AS technical architecture and Components retained; 23 SQL unchanged | Source reactor, SQL hashes and eight-path review | No CREATE scaffolding, DDL or business Event delivery; no new layer or MP bypass | None |

## 未验证边界

不启动真实IAM/MCP/HTTP listener/app/DB/容器/browser，不授角色/激活权限/push/PR；完整journal/native/AS config及T3-08..T4仍后续。

## 初始SPI行为RED与合同精化

`/tmp/tianji-mcp-json-rpc-red.log` 2026-11-01 18:32:04/17.689s exit1，1test/0failure/1error/0skip，官方McpSyncClient.build在新protocolVersions UOE声明失败；非compiler/fixture错误。Root要求行为实现前扩展所有failure/privacy日志用例，当前仍无SPI实现GO。

独立Planreview指出raw isErrortrue与SDK安全投影易混淆：实际Yuheng允许非空privatecontent/structuredContent，必须bounded解析后丢弃，SDK只见empty/null，callback见known TOOL_ERROR空output；已CLAR001明确。日志sentinel断言包括ERROR。TraceContext.REQUEST_ID_HEADER实际x-egon-request-id，复用source常量而非另造header；没有public语义/依赖变化。

## SPI完整matrix RED

`/tmp/tianji-mcp-json-rpc-matrix-red.log` 2026-11-01 18:38:44/15.954s exit1，82参数化用例/0failure/82error/0skip。Root解析Surefire XML：全部82异常均java.lang.UnsupportedOperationException，79在protocolVersions、3在connect新声明，非compiler/fixture；覆盖真实官方client、raw边界/投影/日志/错误/session/lifecycle路径的tests已先写，然后才授权SPI最小实现。当前wrapper仍仓库外准备，helper尚无visibility改动。

## SPI首轮实现校验

`/tmp/tianji-mcp-json-rpc-green.log` 2026-11-01 18:44:58/15.806s BUILD FAILURE，82 cases/13 assertion failures/0errors/0skips。13项均为官方SDK initialize将safe Failure包入固定RuntimeException；不修改官方lifecycle，修正tests核对安全cause chain。Root另审出error structuredContent不应套success object guard；null completion、wholeDoc hash异常与raw IO分类补tests后再实现。尚未满足GREEN，不提交/不推进wrapper。当前Plan SHA `31b225957915a68516d821bfb229b7b4387d4527cf80abbde9eb5c04d3debe3e`。

## SPI追加RED与GREEN

`/tmp/tianji-mcp-json-rpc-additional-red.log` 18:49:00/16.678s exit1，88 cases/3failures/2errors/0skips。5项均精确缺失行为：null future导致native超时、hashhelper异常错分profile、init raw IO错分profile、error scalar/list被success-object guard拒。修正官方SDK包装断言后原13项通过；非编译/fixture RED。随后最小实现并扩error integer/boolean与DEBUG/INFO/WARN/ERROR捕获，同一错误分支包括null/scalar/list/integer/boolean。

`/tmp/tianji-mcp-json-rpc-green-v2.log` 18:50:00/15.964s exit0，90 cases/0failures/0errors/0skips，BUILD SUCCESS。独立source review唯一个CLAR001问题已按上述RED/GREEN修复。SPI source/tests冻结，下一声明顺序仅helper public visibility、wrapper完整tests与UOE编译声明，观察其有效RED后才允许实现。

## wrapper与cleanup有效RED

`/tmp/tianji-mcp-wrapper-and-delete-red.log` 18:51:29/16.380s exit1，111 cases/1failure/20errors/0skips。Root解析XML：wrapper20项全为new ctor UnsupportedOperationException，非compiler/fixture；SPI91项唯一个null-completed DELETE未抛固定UNKNOWN。helper diff仅七处public可见性，无body改变。之后才允许wrapper最小行为与DELETE switchIfEmpty修复；tests保持。README按锁定合同记录库与后续journal/native/config边界，所有Java冻结后统一GREEN。

## 两实现GREEN

`/tmp/tianji-mcp-wrapper-green.log` 18:55:31/17.861s exit0，wrapper20+SPI91共111 cases/0failures/0errors/0skips，BUILD SUCCESS。所有wrapper tests自有效RED后未改，SPI新增nullDELETE项已通过。Root源码复核原window/µ/owner消费、每HTTP当前key/trust、JDK limiting、knownfact先callback、DELETE与本地close顺序；日志改为既有Lombok @Slf4j机械规范，下一完整七类回归覆盖。独立source review进行中，不预填完成。

## 提交前源码复核与一次编译失败

独立review确认SDK close链仅关闭initializer/session与本地SPI，无DELETE/OAuth/HTTP；要求localClose在publishLock内先清bearer再终止SDK pending状态，作为最小secret-lifetime收紧，normal DELETE仍先完成。未发现其他race/authority/window/knownfact/privacy问题。

Root误将仅针对业务类的Lombok日志要求扩到该手动构造technical transport，改成@Slf4j后，`/tmp/tianji-verified-readonly-mcp-transport-lombok-compile-failure.log` 18:58:02/12.340s exit1：AS无Lombok依赖，AR7项通过后AS编译3处missing lombok；不是行为RED。恢复既有SLF4J API直接技术日志，不新增依赖/POM；该类不属于Spring业务Service/普通entity/DI，Rule4按实际适用范围核验，不能用规则扩张添加依赖。修复后重新执行完整七类回归，不沿用之前结果。

## 最终七类回归与Step结论

`/tmp/tianji-verified-readonly-mcp-transport-final.log` 2026-11-01 18:59:34/20.194s exit0，BUILD SUCCESS。AR ReadOnlyMcpToolTransportTest 7；AS wrapper20、bindings14、SPI91、approval17、START32、DMN19，共七类200项，零failure/error/skip。仅三个既有Components Jackson2AutoConfiguration deprecated-for-removal编译警告；新代码无编译/测试失败。完整日志与Surefire各类计数分别复核。

Plan SHA保持31b225957915a68516d821bfb229b7b4387d4527cf80abbde9eb5c04d3debe3e；strict Plan、execute资源完整性与git diff --check通过。所有23个SQL与本Step锁定baseline逐字节一致。localClose在同publishLock中先清bearer再关SDK；final current仍复核CredentialVO/key/trust/window。helper仅七处public，无验证body变化。

本Step Verified，提交限定README、Plan、EVI、两个transport/两个tests及helper共8paths；正式commit与实际路径写入外部主蓝图/本任务checkpoint后才能开始下一Step。该传输库不自动启用MCP、不提交BEGIN或授予调用者工具权限。真实IAM/签名/TLS/network、部署MCP、PostgreSQL、journal/native/config以及T3-08..T4仍未完成。未创建新的仓库scratch harness；临时Plan编写器提交后删除，保留/tmp日志/数据用于审查。
