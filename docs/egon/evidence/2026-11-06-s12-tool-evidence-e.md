# S12 原始所有者 Tool 事件源码证据

基线 Service 71488f428a738b709095586b34f5864373526005。[Spec](../spec/2026-11-06-08-20-s12-tool-evidence-e.md) · [Plan](../plan/2026-11-06-08-21-s12-tool-evidence-e.md)

两个测试套件共用验收入口和私有清单装配，各自拥有独立模式。既有 S07 工具写入及两租户原 USER 保护 SSE 各自保持原权限。验证两条精确 Tool 事件对、native START 回执引用和两次交换 Run 后的 404。

| Gate | Observed result |
| --- | --- |
| R1 | 隔离 JDK 25 编译后删除精确 invocation 比较。`S12ToolEvidenceTest#rejects_other_invocation_under_the_same_run_and_tool_version` 产生一个预期 AssertionFailedError，零跳过。 |
| G1 | 隔离 JDK 25 同时编译合并后的 S11 和 S12 操作者入口。5 个新有限用例与 21 项相邻回归全部通过。 |
| P1 | 所属离线 Maven reactor 复现精确调用 ID 反证。26 项在定向和干净打包各通过一次，零失败、错误或跳过。实际测试字节码保留两套独立入口，生产 JAR 无验收类。成品配置与权限清单语义及 29 份 SQL 核对通过。 |
| S1 | 严格 Spec/Plan、精确八路径与 S11 接入后基线 patch 检查通过。 |
| E1 | 待操作者实际验证 IAM、HTTPS、隔离 PostgreSQL 和真实 Tool 路径。 |

独立 E 覆盖原 USER 的 Tool 观察，完整 S12 仍需组合其余证据。
