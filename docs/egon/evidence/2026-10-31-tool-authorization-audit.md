# T3-07 兼容工具授权审计合同执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [兼容审计合同](../plan/2026-10-31-20-05-tool-authorization-audit-contract.md)；strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/13/14、§19 T3-07 |
| 主设计实施前SHA-256 | `21d90599e2ed10053f43fe0eccea2b20cd4f4c40aaa74d749fd7043e70f2ae2c` |
| Baseline | `700511730e253228a4e0f856bca14ebab67f9778` |
| 提交 | 主代理完成最终diff及显式路径审查后提交；不预填尚未产生的hash，最终hash以Git和交付消息为准 |
| 结果 | 纯域兼容审计合同验证通过；完整T3-07仍Partial，真实authority与执行未交付 |

## 1. 已实现行为与范围

现有`ToolExecutionAuthorization`保持`@FunctionalInterface`，`authorize(Request,Boundary)`仍是唯一abstract方法。新增default `check`对null request/boundary先拒绝；合法输入恰好调用一次旧`authorize`，返回`CheckResult(request,boundary,decision,null)`。旧lambda可继续使用，旧ALLOW可以返回无audit的兼容结果；该结果不是dispatch凭据，未来dispatcher必须显式拒绝缺少可信审计的ALLOW。

嵌套不可变`AuditVO`保留十一项字段：schemaVersion、UUID localCheckId、boundary、decision、subjectType、grantId、authVersion、catalogVersion、policyVersion、Instant checkedAt、expiresAt。schemaVersion固定1，基础字段非null。ALLOW要求policyVersion>=0且expiry>checkedAt；USER必须9前缀63位grantId、authVersion>=0、catalogVersion=null；SERVICE必须8前缀63位grantId、authVersion=null、catalogVersion>=1。DENY/UNAVAILABLE的grantId、各上游版本及expiry全部为null。

`CheckResult`要求request/boundary/decision非null；若audit存在，必须匹配boundary、decision、run actor类型；audited ALLOW还要求`checkedAt < request.deadlineAt <= expiresAt <= run.deadlineAt`。两个新增值的toString均固定脱敏。localCheckId/checkedAt表达本地观测，不声称存在上游decisionId；值构造和类型校验不认证grant密文、不查询真实权限、不授予执行能力。

本切片仅五条计划路径：原domain port、原`ToolExecutionContractTest`、README、本计划、本报告。无SQL、Grant、Outcome、Store、AS、RPC/HTTP、权限、配置、Bean或JSONcodec改动。未读取当前Clock/now，只比较显式Instant。V13仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际验证

复用仓库source-reactor和现有JDK25/同级源码依赖；本轮最小合同/工具回归命令为：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=ToolExecutionContractTest,ToolExecutionGrantTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest,ToolExecutionJsonCodecTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 阶段 | 真实结果 | 证据与解释 |
| --- | --- | --- |
| 最小scaffold后合同RED | 命令exit1；7项、4失败、0错误、0跳过 | `/tmp/tianji-tool-audit-contract-red.log`；原3项仍通过，新4项观察缺失委托/审计约束；不是编译或fixture错误 |
| 最终GREEN | 命令exit0、BUILD SUCCESS；32项全通过，0失败、0错误、0跳过 | `/tmp/tianji-tool-audit-contract-green.log`；Contract 7、Grant 8、ApprovalBinding 3、ActionRequest 4、Registry 5、JsonCodec 5 |
| 源码/摘要检查 | 已核唯一abstract、一次委托、固定脱敏及V13 SHA | 本报告依据当前真实source/log编写；最终全diff/stage审查由主代理提交门禁完成 |

新四项测试覆盖SAM调用次数、USER/SERVICE成功元数据、schema/null/票据/版本槽反例、边界/结果/actor不匹配、expiry覆盖及脱敏。没有执行Store数据库回归或真实PG，本纯域改动未改变Store代码/事务路径；前序结果不冒充本轮重新执行。临时日志可被系统清理，长期证据是原测试类中的持久断言和Git实现。

## 3. 设计追踪与未完成边界

| 局部要求 | 状态 | 证据 | 未扩大的边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 SAM兼容 | Satisfied | 原唯一abstract、default、lambda/调用次数测试 | 旧调用者不被强制提供audit |
| PLAN-REQ-002 typed审计 | Satisfied | AuditVO/CheckResult及两类主体/拒绝字段/版本槽测试 | metadata不等于真实authority |
| PLAN-REQ-003 时限与秘密 | Satisfied | Instant覆盖关系、全类型/匹配检查、两个固定toString | 不读取now，不认证票据或当前权限 |
| PLAN-REQ-004 有界范围 | Satisfied | 五条路径、现有V13摘要、无新接线 | JSON十一键/4096byte写入合同以后接 |
| 完整T3-07及平台蓝图 | Partial | 本轮只是兼容审计值合同 | AS/PDP适配、grant签发/密文/current权限、audit codec与入库、invocation/审批消费/dispatch/ADK接线未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/domain/tool接口及原测试路径 | 只扩已有port，无模块/层迁移 | None |
| MC-REUSE-001 | Applicable | PASS | 原Request、SubjectType、Boundary/Decision、8/9 namespace | 复用现有领域合同，不造身份/权限框架 | None |
| MC-DEP-001 | Applicable | PASS | 既有JDK/JUnit/domain；无POM变更 | 无依赖/工具新增 | None |
| MC-NAME-001 | Applicable | PASS | CheckResult、AuditVO；既有Request保留 | 明确结果/审计值职责，旧公开类型兼容 | None |
| MC-VALID-001 | Applicable | PASS | record紧构造约束、default null前置拒绝、4项新增合同测试 | 纯域值边界，无新增HTTP/业务传输handoff | None |
| MC-MODEL-001 | Applicable | PASS | record字段仅不可变Request/UUID/enum/Long/Instant/String | 真正不可变值，不是实体/可变POJO | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter或跨层模型映射 | 不绕过MapStruct/BaseConverter | None |
| MC-LOG-001 | Not applicable | N/A | 接口/值，无业务日志服务；toString固定脱敏 | 无新增业务日志或私密错误输出 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Spring Bean/DI/autoconfig变化 | 无Bean命名/Qualifier新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK Objects/UUID/Instant与原domain类型 | 无工具库/重复utility | None |
| MC-JSON-001 | Not applicable | N/A | 本轮未加JSON codec/外部序列化或入库 | 不把纯record构造当JSON/持久验收 | None |
| MC-TIME-001 | Applicable | PASS | checked/expiry/request/run deadline的Instant比较及正反测试 | 无Clock/DB/default zone；覆盖截止且不越run上限 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/config key变更 | 环境配置结构保持 | None |
| MC-PATTERN-001 | Applicable | PASS | 原authorizer依赖倒置扩展点，default一次委托 | 固定值规则直接处理，无新策略/执行层 | None |
| MC-SCOPE-001 | Applicable | PASS | 计划五路径、V13冻结摘要、无Store/AS/运行修改 | 保持本域合同逻辑切片，完整任务仍Partial | None |
| MC-TEST-001 | Applicable | PASS | 真实RED 7/4失败/0错误；GREEN 32全通过 | 兼容/新行为有证据，不称真实authority或DB通过 | None |
| MC-BLOCKER-001 | Applicable | PASS | 上述独立行、实际source/log与明确待办 | 本合同切片无未解释失败；运行/真实适配继续待办 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | CheckResult/AuditVO与原port/Request | source/编译/7项Contract测试 | 结果和值职责清晰，保持原类型兼容 | None |
| Rule 2 | Applicable | PASS | 紧构造校验类型/版本/票据/边界/decision/actor/时限 | 新4项正反覆盖，default null前置拒绝 | 纯域值约束，无新跨层HTTP输入/DTO | None |
| Rule 3 | Applicable | PASS | 两record仅不可变字段，无实体/Converter | 构造、匹配、脱敏测试和编译 | 值对象record适用，不需可变POJO注解 | None |
| Rule 4 | Not applicable | N/A | 纯接口与嵌套值，无业务Bean/DI | 未引入Service/Bean/注入配置 | 无新增业务日志或注入规则适用面 | None |
| Rule 5 | Applicable | PASS | JDK与既有domain/JUnit | imports/无POM变更、32项GREEN | 无工具库/框架新增 | None |
| Rule 6 | Not applicable | N/A | 无JSON/持久化/wire改动，旧枚举不变 | 当前范围与V13摘要核对 | 新审计metadata尚未序列化入库 | None |
| Rule 7 | Not applicable | N/A | 无配置/profile文件 | 五条计划路径 | 配置结构不变 | None |
| Rule 9 | Applicable | PASS | 原port依赖倒置、default一次委托和固定值校验 | SAM/一次调用/版本时限测试 | Simple直接处理，未造额外策略层 | None |
| Rule 10 | Applicable | PASS | Instant绝对比较，无now/Clock/日期工具 | checked<deadline<=expiry<=run deadline反例/等值覆盖 | 无默认zone/DB舍入/新时间依赖 | None |
| Rule 11 | Applicable | PASS | 原AR域边界、无SQL/Store/引擎/消息变化 | V13仍f0c642…26b3；编译/工具域回归 | exact现有架构保持，无新层或伪装执行能力 | None |

## 6. 验收边界与处置

没有创建scratch验证harness或仓库scripts/work脚本；计划文档生成脚本已删除。测试保留在原ContractTest中，不增加testkit。未启动应用、数据库、容器、浏览器/GUI、长期runtime或真实模型/MCP；未注册/激活权限、授角色、部署、push、PR或release。源代码、编译及测试不证明真实Shoubing/Jianshen/PDP/issuer/密文或dispatch安全。

本证据文档作者只创建本文件，不改README/plan/代码/SQL，不提交。主代理须按五条显式路径完成最终diff与提交检查；真实提交hash以该步骤Git结果为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前兼容审计合同已通过非运行验证，完整T3-07及平台验收仍未完成。
