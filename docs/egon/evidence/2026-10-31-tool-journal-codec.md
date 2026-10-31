# T3-07 工具持久记录 JSON 边界执行证据

- 日期：2026-10-31，Asia/Shanghai。
- 执行细化：[tool-journal-codec-boundary](../plan/2026-10-31-17-47-tool-journal-codec-boundary.md)，Step 1。
- 有效设计：[原平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) §6、§7.2、§13、§19 T3-07；执行前 SHA-256 `577ce2722cd94d4ae905d44ad6bef6817f903bbfc6b640576ba8ba216cd7b27b`。
- baseline：`b9ee90072fd043b50dce045d1884ebaf80002684`，`main`。
- 用户明确允许继续完善和提交原有三个未跟踪文件。原始副本保存在仓库外 `/tmp/tianji-tool-journal-original-b9ee900/`，该临时目录不作为长期测试证据。本步骤只纳入 codec 与其测试；`ToolActionIntentSql.java` 尚未提交，原内容保留。
- 提交：包含本报告、执行细化、README、codec 和其测试的 `fix(agent): close tool journal JSON encoding failures`；可通过本文件的 Git 历史定位，不为回填 hash 修改已提交历史。

## 实际变更与设计

包私有 `ToolExecutionJsonCodec` 使用现有 Boot 管理的 Jackson 2 及 `ToolSchema.values`、`ToolDescriptor.reference`、`ToolExecutionOutcome.validated`，保存闭合、受限的私有 JSON。它拒绝未知/重复字段、尾随文档、标量强制转换、无效 Unicode、超过 128 KiB 的内容及 descriptor hash 漂移；整数参数归一化为 Long。成功写动作必须有业务 receipt，UNKNOWN 不产生虚构 receipt 或零费用。

本次补正编码侧边界：null descriptor 不再被写成 JSON null；参数/结果预校验异常纳入固定错误边界。读写异常均为 `IllegalStateException("Stored tool execution content is invalid or changed")`，无 payload-bearing cause。编解码是简单的顺序验证，不增加策略层或转换 DTO；后续工具传输变化仍属于原蓝图的 Adapter 边界。

## 验证

在 `tianji-service` 执行以下共同命令，以 `SELECTOR` 替换实际测试列表：

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false -Dtest=SELECTOR \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

临时 settings 仅用于既有仓库访问，未修改用户全局 settings；源码 reactor 使用同级 Components、授柄、鉴身和天枢。日志路径便于本机追查，持久证据是仓库内测试和下面的实际结果。

| 阶段 | SELECTOR | 实际结果 | 本机日志 |
| --- | --- | --- | --- |
| 原草稿 baseline | ToolExecutionJsonCodecTest | exit 0；4 tests，0 failures/errors/skipped | `/tmp/tianji-tool-codec-baseline-current.log` |
| RED，仅新增 writer 测试 | ToolExecutionJsonCodecTest | exit 1；5 tests，1 failure，0 errors/skipped；null descriptor 未抛预期异常，原四项仍通过 | `/tmp/tianji-tool-codec-red.log` |
| GREEN | ToolExecutionJsonCodecTest,ToolExecutionContractTest,ToolRegistryTest,ToolActionRequestTest | exit 0；17 tests，0 failures/errors/skipped | `/tmp/tianji-tool-codec-green.log` |
| 最终读侧错误/Unicode 回归 | 同 GREEN | exit 0；17 tests，0 failures/errors/skipped；codec 5、contract 3、registry 5、action 4 | `/tmp/tianji-tool-codec-final.log` |

最终测试包含所有 writer 的 null/非法 schema/无效 Unicode/缺 receipt/错误 output，以及 reader 的原始或转义孤立代理项、固定错误消息和 null cause。测试不连接 PostgreSQL、模型、IAM 或 MCP，不启动应用。

文档结构由工作区根运行以下命令验证，exit 0：

```sh
python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  tianji-service/docs/egon/plan/2026-10-31-17-47-tool-journal-codec-boundary.md --strict
```

逐行复核本步骤代码及 staged diff；`git diff --check` 与 `git diff --cached --check` 均 exit 0。其他仓库和旧迁移未纳入提交。未创建验证脚本或 `scripts/work/` harness；临时 reactor POM 由已有脚本创建并清理。

## Manual Check

以下检查仅覆盖本步骤，不能扩展成整个平台验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原蓝图 §6；codec 位于既有 AR infrastructure/persistence | 无层/模块/方向变化 | None |
| MC-REUSE-001 | Applicable | PASS | AgentContextJsonCodec、ToolSchema.values、ToolExecutionOutcome.validated、现有 Jackson | 复用实际已有能力 | None |
| MC-DEP-001 | Applicable | PASS | 五条提交路径无 POM/构建配置 | 无新依赖/框架 | None |
| MC-NAME-001 | Applicable | PASS | ToolExecutionJsonCodec、ToolExecutionJsonCodecTest | 职责名沿用同包 codec；无新 carrier | None |
| MC-VALID-001 | Applicable | PASS | codec 所有读写入口、17 项测试 | 固定领域/schema 验证在持久表示边界实际调用 | None |
| MC-MODEL-001 | Applicable | PASS | ToolDescriptor/Schema/Outcome 既有不可变值，round-trip equality | 无实体或可变 POJO 编辑 | None |
| MC-CONVERT-001 | Not applicable | N/A | 本步骤无 Converter 或跨层 DTO 映射 | JSON 是私有持久格式，无对象映射旁路 | None |
| MC-LOG-001 | Not applicable | N/A | 包私有技术 codec，无业务 Service/Handler 或日志 | 固定异常无正文/cause | None |
| MC-BEAN-001 | Not applicable | N/A | codec 非 Spring Bean，无注入依赖 | 不涉及构造注入/Qualifier | None |
| MC-UTIL-001 | Applicable | PASS | JDK + 既有 Jackson imports | 无新 utility 库/Utils | None |
| MC-JSON-001 | Applicable | PASS | strict mapper、hash/schema、5 项 codec 测试 | 闭合类型、大小及读写脱敏通过 | None |
| MC-TIME-001 | Not applicable | N/A | codec 值无时间字段，本步骤不编辑 lease/SQL | 无时间边界变更 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无 profile/Properties/配置键编辑 | 环境结构不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 直接顺序编解码和既有值校验 | Simple；模式层没有收益 | None |
| MC-SCOPE-001 | Applicable | PASS | 计划五条路径；SQL 原始备份与保留 | 仅授权 codec 切片 | None |
| MC-TEST-001 | Applicable | PASS | baseline 4、真实 RED、最终 17 全通过，格式检查 | 行为与失败边界有实际证明 | None |
| MC-BLOCKER-001 | Applicable | PASS | 上述逐条检查、无未解释失败 | 本切片无阻断；后续 journal 明确待办 | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Codec/Test 职责类型，无新 POJO | 编译、声明检查 | 命名与同包惯例一致 | None |
| Rule 2 | Applicable | PASS | descriptor 构造、ToolSchema.values、Outcome.validated；无新增层间 DTO | 正反读写用例 | 现有领域规则用于私有格式边界 | None |
| Rule 3 | Applicable | PASS | 复用已有不可变值；无模型/Converter 编辑 | equality/round-trip，编译 | 无可变实体或手工跨层转换 | None |
| Rule 4 | Not applicable | N/A | 无业务行为类、Spring Bean、注入或日志 | 五条路径检查 | 技术 codec 采用同包既有模式 | None |
| Rule 5 | Applicable | PASS | JDK Objects/Map/UTF-8 与既有 Jackson | import/POM 差异检查 | 无新增工具依赖 | None |
| Rule 6 | Applicable | PASS | Jackson mapper、strict fields/coercion/Unicode/receipt | 5 项 codec、17 项定向测试 | JSON 合同封闭，拒绝正文 cause | None |
| Rule 7 | Not applicable | N/A | 无任何环境配置编辑 | 提交路径检查 | 配置不变 | None |
| Rule 9 | Applicable | PASS | 简单的验证→编码/解码→验证，无业务分派 | 控制流逐行审查 | 不需要新增模式 | None |
| Rule 10 | Not applicable | N/A | 无时间字段或日期 API 编辑 | import/diff 检查 | 无时间变更 | None |
| Rule 11 | Applicable | PASS | 保持原蓝图现有 AR 技术边界；无持久/事件/DDL 新入口 | 源码 reactor 编译与范围检查 | 无新架构/迁移体系 | None |

## 要求映射与未完成边界

| Requirement | Status | Evidence/remaining action |
| --- | --- | --- |
| PLAN-REQ-001 | Satisfied | 固定 reference、参数归一化、schema/hash/JSON 拒绝测试 |
| PLAN-REQ-002 | Satisfied | receipt/UNKNOWN 往返及无效写 receipt 拒绝 |
| PLAN-REQ-003 | Satisfied | 读写全部固定消息/null cause；Unicode/私密值拒绝 |
| PLAN-REQ-004 | Satisfied | 无 POM/SQL/API/开关/运行调用变化；原 SQL 文件保留 |
| 完整 T3-07 | Partial | 独立当前权限实现、durable journal、审批消费、业务传输、ADK 挂载尚未交付 |
| S07/S10 的跨服务验收 | Runtime unverified | 未运行实际 IAM、DB、模型/MCP 或跨服务副作用场景 |
| 其他原蓝图剩余阶段 | Partial | 本步骤不覆盖，仍按原计划继续 |

未启动项目、服务器、数据库、容器或浏览器；未注册/激活权限或授予角色；未 push、创建 PR、发布或部署。本 codec 切片已验证；完整平台结论保持 **PARTIAL — Spec requirements are unmet or unverified**。
