# 固定readonly MCP合同与绑定执行证据

Plan: [2026-11-01-17-49-fixed-readonly-mcp-contracts.md](../plan/2026-11-01-17-49-fixed-readonly-mcp-contracts.md)，Review/原用户授权；baseline55ffe9d1b3216cc422defff3a305d1c98273c721；SpecSHAe0e9e5b5bd86c8209bc96ead36d125ef15f0d7f11e30872c07828c8708085789。

Verified，待本Step路径限定commit。Root全文/strict/resources/checklist与原蓝图授权，初始Plan6722ee8...、CLAR001后aa20d962...；final Plan SHA `e3d40025c153b375d3090741348c6a6ffa9371a9cf4d321312e5dc9e6e99c9dd`。Port/Bindings各有效RED后GREEN，最终六类48PASS0skip；Scope仅7paths metadata+port，不包含任何SDK/HTTP/credentials/journal/native/Bean/config启用。

## Requirements

- PLAN-REQ-001: PASS — Port7PASS：exactRO/MCPresource/runlease/nonlegacy/normalizedcopy/namespace/primitiveemptyerror/causelessfailure；boundedSAM仅接口契约，未冒充实际IOfence。
- PLAN-REQ-002: PASS — Backend19fields/nonBillable显式TRUE/scopes/URI/name/schema/timeout与fixedmode/protocol/limits全部fingerprint，immutableSnapshot通过。
- PLAN-REQ-003: PASS — Registry finite256/fullowner/type/issuer/tenant/definitionversionhash/fullref/entiredescriptor/schema/backendHash，重复与drift反例通过。
- PLAN-REQ-004: PASS — CanonicaltoolJSON keys排序/array顺序/null/typednumeric+raw512bits/depth8/nodes4096/container256/UTF8+serialized128KiB/hostileobjects/cycles/DAG硬界限均通过。
- PLAN-REQ-005: PASS — 最终六类48PASS/0fail/error/skip、23SQL不变、exact7paths/独审/17MC10Rules/strict/cached闭合，无SDK/网络/真实authority执行。

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR application/tool+AS integration/mcp，现技术层与metadata pattern | 无新业务层/CRUD/SQL，Agent既定exclusion保持 | None |
| MC-REUSE-001 | Applicable | PASS | ToolSchema.values/ToolDescriptor/Orchestrationhash/genericVerifiedExecutor/FixedDecisionpattern | 没有假DMN来源、新executor或通用框架 | None |
| MC-DEP-001 | Applicable | PASS | git7scope无POM；现Jackson/JDK/Root source reactorSUCCESS | 无新依赖/版本升级 | None |
| MC-NAME-001 | Applicable | PASS | Request/CallObservation/BackendVO/BindingVO/ResolvedBindingVO与technicalport/registry | 沿既有嵌套值对象及技术类型命名 | None |
| MC-VALID-001 | Applicable | PASS | constructor guards/ToolSchema.values/fullslot+hash+schema/digestdataBounds；7+14真实测试 | 每个新增handoff验证且不把metadata当认证 | None |
| MC-MODEL-001 | Applicable | PASS | immutable records+Map/Set/List copy；privatefinal routeMap | 值对象record豁免，无新普通实体/DTO转换 | None |
| MC-CONVERT-001 | Not applicable | N/A | 没有PO/Entity/业务DTO转换/Converter，schema/hash为metadata编码 | 未触MapStruct/BaseConverter边界 | None |
| MC-LOG-001 | Not applicable | N/A | 无新业务logger/日志语句，所有public值toString脱敏/错误固定noCause | 无新敏感日志面 | None |
| MC-BEAN-001 | Not applicable | N/A | 无新managedBean/DI构造/field injection | metadata普通构造，不需Service注入 | None |
| MC-UTIL-001 | Applicable | PASS | JDK URI/collections/BigDecimal+现Jackson/Orchestrationdigest | privatebounded规范化只服务MCPtool指纹，无custom工具框架 | None |
| MC-JSON-001 | Applicable | PASS | JsonMapper序列化已验证JSONtree；key/array/null/number domain+bytes测试 | 旧wire/enum未改，无ordinal/任意POJO转换 | None |
| MC-TIME-001 | Applicable | PASS | Request Instant≤run/metadata不过当前Clock；BackendDuration100ms..30s入hash | 明确未来µ/wall-mono执行fence未实现，无当前授权伪称 | None |
| MC-CONFIG-001 | Not applicable | N/A | 7paths无YAML/profile/启用；未挂载MCP | 现defaultoff与旧配置完全保持 | None |
| MC-PATTERN-001 | Applicable | PASS | 现fixed registry模式+port Adapter契约，后端/版本变化点 | 无新父绑定层/策略/引擎/调度器 | None |
| MC-SCOPE-001 | Applicable | PASS | exact7paths/23SQL哈希不变；无旧API/SDK/client/journal/native/POM | 保他人工作，无权限/运行动作 | None |
| MC-TEST-001 | Applicable | PASS | Port7RED→7GREEN/Bindings14RED→14GREEN/六类48PASS0skip，独审无发现 | 真实guard/hash/copy/namespace/bounds，非fixture/compile伪RED | None |
| MC-BLOCKER-001 | Applicable | PASS | 17MC/10Rules、strict/resources/checklist/diff/cached、无未闭合局部项 | 本Step库完成，平台执行与真实环境仍未完成 | None |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 嵌套Request/Observation/Backend-Binding-Resolved VO及既有技术类型模式 | source/compile/7scope | 命名符合实际职责 | None |
| Rule 2 | Applicable | PASS | typedconstructor/ToolSchema.values+精确metadata闭合+JSONbounded输入 | 7+14正反例与48final | 新增边界均验证，metadata不当authority | None |
| Rule 3 | Applicable | PASS | record immutableVO/deepcopy/privatefinal registry；无普通entity | mutation/construct/hash tests | record豁免，无Lombok或Converter冲突 | None |
| Rule 4 | Applicable | PASS | 无新Spring业务类/DI/logger，technicalmetadata与port沿现例 | scope/import/statement审查 | 不引Service/field注入或敏感日志 | None |
| Rule 5 | Applicable | PASS | JDK/SpringJackson与现Orchestrationhash，无pom | source reactor全部SUCCESS | 无新依赖或工具库 | None |
| Rule 6 | Applicable | PASS | Jackson已验证JSONtree/内部enum无wire/persist变化 | key/array/null/numeric/bytes/hash测试 | 旧JSON合同/编码保持，无ordinal | None |
| Rule 7 | Applicable | PASS | 无environment配置变化/新库无runtime行为 | exact7scope | 环境key parity不受影响 | None |
| Rule 9 | Applicable | PASS | 现fixed registry+port thinAdapter合同 | registry origin/hash/schema矩阵 | 复用具体变化点，无新框架 | None |
| Rule 10 | Applicable | PASS | Instant metadata上界/Duration bounds，noClock/noDB | time边界construct tests | java.time且不冒充实际传输fence | None |
| Rule 11 | Applicable | PASS | AR/AS原技术架构，23SQLimmutable，未触CRUD/Event | 7scope/reactor/static | 既有MP-SDJ/Outbox职责保持 | None |

## 未验证边界

未进行SDK调用/HTTP/模型/真实IAM/DB/容器/browser/GUI，不授角色/激活权限/push/PR。MCP完整执行及T3-08..T4仍Partial。

## Port实际RED/GREEN

`/tmp/tianji-fixed-readonly-mcp-port-red.log` 2026-11-01 17:55:48/38.115s exit1，7tests/4fail/2error/0skip；6缺行为为readonly/resource/namespace/hash、输入输出copy/错误状态私密数据、固定Failure，仅新UOE编译声明或缺guards，非compiler/fixture。`/tmp/tianji-fixed-readonly-mcp-port-green.log` 17:56:39/19.491s exit0，**7PASS0fail/error/skip**。新port不执行网络/当前authority/真实时间fence，onlyvalue合同；requiredboundedSAM的执行边界留后续实现。

Root已保存23SQL hash `/tmp/tianji-step28-sql-baseline.json`，sourcebaseline55ffe9d/原Spec e0e9... unchanged。独立Planreview无blocking发现，numeric preguard建议已按CLAR001明确；随后执行Bindings有效RED，结果见下一节，7scope无其他source更改。

## Bindings实际RED

`/tmp/tianji-fixed-readonly-mcp-bindings-red.log` 2026-11-01 17:59:45/14.799s exit1，14tests/4fail/10error/0skip。URI/identity/nonbillable guard缺失未throw，hash/resolve/doc fingerprint为允许的compile-only UOE，全部缺行为而非compiler/fixture错误。Root确认后授权最小实现，该轮worker仅2paths；CLAR001 rawBigDecimal512bit限制已明确且纳backend fingerprint，未添加任意rawprecision/scale256限制。

## Bindings GREEN与独审

`/tmp/tianji-fixed-readonly-mcp-bindings-green.log` 2026-11-01 18:02:20/15.205s exit0，**14PASS/0failure/0error/0skip**。全19fields/constants、schema requiredness、slot/identity/drift、numeric/depth/node/container/UTF8/serialized expansion/cycle/DAG与redaction均通过。独立mcp_scope_plan_review只读审Port/Bindings及7+14测试未发现blocking correctness/security/resource/compat/fixture缺陷；未以其静态审阅冒充Maven或完整MCP能力。Root已逐行读全部新production/test源码，README已准确注明库与后续IO/authority边界。随后完成六类回归，见最终小节。

## 最终精确source回归与边界

`/tmp/tianji-fixed-readonly-mcp-contracts-final.log` 2026-11-01 18:04:48/15.817s exit0，**六类48PASS/0failure/0error/0skip**：AR新Port7+旧DecisionPort8+ActionRequest4+Registry5=24；AS新McpBindings14+旧DecisionBindings10=24。Plan§8 JDK25/ADKenvfalse/三个OTelexporter全none，现verify-source-reactor完成所有模块SUCCESS。仅未触Components RPC三条既有Jackson2弃用编译警告，无新WARN或构建错误。源代码不涉及MCP SDK/client/网络/credential或实际PDP。

独立mcp_scope_plan_review先审exactPlan及原模式无blocking发现；rawBigDecimal512bit资源建议已CLAR001明确；再审四新production/test文件确认无correctness/security/resource/compat/fixture缺陷，包括精确节点与文字预算、数值极端scale/zero/巨尾零、完整slot/字段/requiredness/drift与redaction。该review为static，Maven由Root执行。Root完整逐行复核源码/测试/README/Plan实际scope，17MC/10Rules逐项如上。

Root重新全文读取末尾checklist、完成strict/resources/exact7scope/23SQL/新untracked whitespace及cached检查后立即提交本Step，不预填hash；真实commit记原蓝图与continuation。临时authoring/prep helper在commit后删除，无repo scripts/work未定harness。MCP SDK安全传输、真实credential、journal/native/AS config及T3-08..T4继续，不能将本metadata库视为全目标完成。
