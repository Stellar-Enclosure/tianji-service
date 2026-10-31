# T3-07 独立工具凭据绑定合同执行证据

- 日期：2026-10-31（Asia/Shanghai）。
- 执行细化：[tool-execution-grant-binding](../plan/2026-10-31-17-58-tool-execution-grant-binding.md)，Step 1；有效设计是[原蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) §6/§13/§14/T3-07，执行前 SHA-256 `1d1f706a9594a6fc3b2b9ca80cfce968f3db806a926164ec97bc8a7b391fc6a9`。
- baseline：`566a66526809cd7b99d9468912126609e3f24ff3`；上一 codec 步骤五条提交路径已经核对，之后才新增本步骤代码。
- 提交：包含本报告的 `feat(agent): bind independent per-tool execution grants`；由本文件 Git 历史定位，不改写历史回填 hash。

## 实现与关键边界

`ToolExecutionGrant.BindingVO` 是不可变绑定值，覆盖完整 run 来源、固定 tool ref、backend、独立 `TIANJI_TOOL_*` purpose、AS audience、authorization resource key 与原 SERVICE 六字段来源。`capabilityId()` 只绑定 owner/run/tool key/version；相同槽改变其他元数据保持 UUID，但改变 `bindingSha256()`，后续 Store 必须拒绝冲突。两种原 grant 的 fingerprint 仅用于复用完整来源字节和输入约束，未复用 Run 的票据或执行权限。新摘要由已有 `OrchestrationRequestDigests` 的独立版本标签隔离。

USER 不接受 SERVICE subject；SERVICE 必须有与原 owner 匹配的 clientId。SERVICE 自己的 source resource 与进入 AS 的 token audience 可以不同。工具交换固定 `SESSION_REQUIRED=false`，原 run 的 session requirement 仍进入来源摘要。`ReservationVO` epoch 必须正数；`EvidenceVO` 复用既有 USER9/SERVICE8 Ticket 格式和大小校验，expiry 不得晚于 run deadline。

值构造不解密、不验证签发来源、不产生 ALLOW；过去的票据也可以表示历史值。实际签发与 fresh `Expectation/ServiceExpectation`、当前状态/时钟、存储 fence 仍待接入。出站 Process 业务身份仍必须为真实 SERVICE，不能由这些原请求者字段冒充 USER。简单 USER/SERVICE 决定没有上游 decisionId，本步骤没有虚构该字段。

实现期间发现 Java `URI.equals` 会忽略 scheme/host 大小写，而既有来源摘要保留精确 ASCII 文本。按 PLAN-CLAR-003 先复现行为 RED，再仅在 BindingVO 覆盖 equals/hashCode，比较 audience 及 SERVICE source resource 的精确 ASCII；不重写原 URI 或改变旧 grant 字节。三个 VO 的 toString 均固定脱敏，所有分量为不可变值，无新实体、Bean、Converter 或工具框架。

## 实际验证

共同命令在 `tianji-service` 执行，以表中 selector 替换 `SELECTOR`：

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false -Dtest=SELECTOR \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

| 阶段 | Selector | 实际结果 | 本机日志 |
| --- | --- | --- | --- |
| scaffold 后真实行为 RED | ToolExecutionGrantTest | exit 1；7 tests、7 failures、0 errors/skipped；派生 null/缺少构造校验断言失败，编译与 fixture 正常 | `/tmp/tianji-tool-grant-red.log` |
| 初次 GREEN | ToolExecutionGrantTest,ToolExecutionContractTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest,ToolExecutionJsonCodecTest | exit 0；27 tests，0 failures/errors/skipped | `/tmp/tianji-tool-grant-green.log` |
| URI 相等性 RED | ToolExecutionGrantTest | exit 1；8 tests、1 failure、0 errors/skipped；相等 URI 文本不同导致两个 BindingVO 错误相等 | `/tmp/tianji-tool-grant-uri-red.log` |
| 最终 GREEN | 同初次 GREEN | exit 0；28 tests，0 failures/errors/skipped：grant 8、codec 5、approval 3、contract 3、action 4、registry 5 | `/tmp/tianji-tool-grant-final.log` |

独立 Python `hashlib/struct` 按 UTF-8/大端长度前缀计算测试向量：`capabilityId=8cbbd6c3-6400-b6b9-7d32-8a3c35de6a54`，`bindingSha256=ec5be3e0168140db26c380412b379e6492ba0cc0f66f089b888684ab04aaefa2`；已固定在测试中。计算以一次性内联命令执行，未创建脚本或依赖。其余测试逐项改变 owner/run/definition/request/snapshot/policy/session/deadline、tool ref、backend、purpose/resource/key 和 SERVICE source；校验类型、UUID、URI、namespace、票据大小、epoch、期限及脱敏。

计划 strict validator、`git diff --check`、`git diff --cached --check` 均 exit 0，逐行复核最终源代码与提交清单。复用既有 source-reactor，无新 `scripts/work/` harness；其临时 POM 由原脚本清理。未修改用户全局 Maven settings。日志只是本机辅助，长期证据是提交的测试/合同与本报告。

## Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原 AR/domain/tool、原蓝图§6；五条提交路径 | 保持现有模块和依赖方向 | None |
| MC-REUSE-001 | Applicable | PASS | 两种原 Grant fingerprint/Ticket、OrchestrationRequestDigests.hash | 复用完整来源、票据与摘要，不造第二工具库 | None |
| MC-DEP-001 | Applicable | PASS | 无 POM/构建工具编辑 | 无新增依赖 | None |
| MC-NAME-001 | Applicable | PASS | Grant 行为容器、BindingVO/ReservationVO/EvidenceVO、Test | 值职责和语义后缀明确 | None |
| MC-VALID-001 | Applicable | PASS | 三个 compact constructor、旧 fingerprint/Ticket | 输入边界约束实际执行；无新增层间 DTO | None |
| MC-MODEL-001 | Applicable | PASS | 三个 records 仅包含不可变分量，URI equality 单独校正 | 真正值语义，非实体生命周期对象 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无 Converter/DTO 映射 | 无 MapStruct 旁路 | None |
| MC-LOG-001 | Not applicable | N/A | 静态合同无业务 Service/Handler/日志 | diagnostics 固定脱敏 | None |
| MC-BEAN-001 | Not applicable | N/A | 无 Spring Bean/DI | 不涉及构造注入或 Qualifier | None |
| MC-UTIL-001 | Applicable | PASS | 仅 JDK 与已有域/digest 类 | 无重复 Utils 或工具依赖 | None |
| MC-JSON-001 | Not applicable | N/A | 没有 JSON/wire 边界或新枚举 | 无序列化合同改动 | None |
| MC-TIME-001 | Applicable | PASS | EvidenceVO Instant expiry<=run.deadlineAt，等于/前后纳秒测试 | 绝对时刻、保留精度，无当前时钟或默认时区依赖 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无 Properties/profile/配置键编辑 | 环境结构不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 简单值验证/派生，直接复用现有 grant | 无复杂业务分派；无需策略层 | None |
| MC-SCOPE-001 | Applicable | PASS | 计划五路径，SQL 草稿未改/未暂存 | 无迁移/权限/接口/执行入口扩张 | None |
| MC-TEST-001 | Applicable | PASS | 两次真实 RED、最终 28 项、独立向量和静态检查 | 关键正反行为有实际证明 | None |
| MC-BLOCKER-001 | Applicable | PASS | 每行独立复核，URI equality 缺口已关闭 | 本切片无已知失败，后续能力仍单列 | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Grant/VO/Test 声明 | 编译与名称审查 | 语义职责明确 | None |
| Rule 2 | Applicable | PASS | Binding/Reservation/Evidence 构造校验与原 source/Ticket | 8 项正反域测试 | 纯值合同；无新 HTTP/Service/DAO handoff | None |
| Rule 3 | Applicable | PASS | records 分量不可变，精确 equals/hashCode | equality、digest、结构测试 | 无实体、可变 POJO 或 Converter | None |
| Rule 4 | Not applicable | N/A | 无业务 Bean/注入/日志 | 路径/import 检查 | 静态数据合同沿既有 Grant 风格 | None |
| Rule 5 | Applicable | PASS | JDK 与原 OrchestrationRequestDigests | imports/POM 差异 | 无新增 utility 库 | None |
| Rule 6 | Not applicable | N/A | 无 JSON/前端/持久枚举编辑 | 文件检查 | 无新增序列化边界 | None |
| Rule 7 | Not applicable | N/A | 无配置路径 | 提交清单 | 配置不变 | None |
| Rule 9 | Applicable | PASS | Simple 固定值验证/摘要，复用 source 校验 | 控制流逐行检查 | 没有需要模式的复杂编排 | None |
| Rule 10 | Applicable | PASS | Instant expiry/deadline；无 java.util 日期 | 边界测试与 imports | 不读 OS 时区/全局时钟 | None |
| Rule 11 | Applicable | PASS | 仅既有 domain/tool；无持久/事件/DDL/新层 | source reactor 编译、diff | 保持用户授权的原模块边界 | None |

## 要求与验收边界

| Requirement | Status | Evidence/remaining action |
| --- | --- | --- |
| PLAN-REQ-001 | Satisfied | 独立 purpose/来源/hash、USER/SERVICE namespace/source 负向测试 |
| PLAN-REQ-002 | Satisfied | 稳定 slot、绑定漂移、完整字段与独立编码向量、URI equality |
| PLAN-REQ-003 | Satisfied | epoch/8或9票据/大小/expiry<=deadline/脱敏；无 bearer |
| PLAN-REQ-004 | Satisfied | 五条提交路径，无 DDL/POM/API/权限/开关变更 |
| 完整 T3-07 | Partial | 实际签发、fresh当前权限、持久journal、审批消费、传输与ADK挂载仍待实现 |
| 跨服务/IAM/DB/模型/MCP | Runtime unverified | 本步骤只编译/运行纯域和codec测试，没有外部环境证明 |

本步骤未启动应用、服务器、数据库、容器或浏览器；未注册/激活权限、授予角色、push、创建PR、发布或部署。scaffold 已全部完成，无 null 派生实现残留；未接线 SQL 原始内容继续保留。完整平台结论仍为 **PARTIAL — Spec requirements are unmet or unverified**。
