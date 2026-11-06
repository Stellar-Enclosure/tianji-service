# T4-03 S11 Checkpoint 原定义版本与摘要故障验收证据

Service baseline `e42281d4e08120755ac9afbc9d8c8bce09d3b1f0`。[Spec](../spec/2026-11-06-07-52-s11-checkpoint-definition-pin.md) · [Plan](../plan/2026-11-06-07-53-s11-checkpoint-definition-pin.md)

现有两种 codec 的 PostgreSQL checkpoint 接管用例在真实发布 v2 后，先检查原 v1 预览绑定，再分别注入仅版本错误和仅摘要错误。每次通过原 V16 行精确 CAS 复原。预览与接管均应在受控授权之前冲突，零回执和原始 cut 保持。随后原有正向接管与 ACK 重放继续验证。

| Gate | Scratch evidence |
| --- | --- |
| R1 version | 独立删掉原版本比较的 scratch 副本触发 typed `expected: <SOURCE_UNAVAILABLE> but was: <BOUNDARY_OBSERVED>`。 |
| R1 digest | 独立删掉原摘要比较的 scratch 副本触发同一 typed mismatch。 |
| G1 | 独立 JDK 25 与 JUnit 观察套件 7 项成功。所属 Maven reactor 的 Observation 7 项、Takeover 14 项和 Fork Handoff 5 项，在定向及干净打包各通过一次，零失败、错误或跳过。原计划把 Takeover 按方法计作 10 项，实际报告含 14 个测试实例。 |
| G2 | 两份源实际编译，既有 operator 选择器保持。默认 7 个 PostgreSQL 方法跳过。 |
| S1 | 严格文档和准确补丁范围。 |
| I1 | 待操作者在隔离 PostgreSQL 上授权执行原接管入口。 |

I1 沿用[既有专用 PostgreSQL 命令](../plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md#separate-operator-only-i1)。这一步没有启动 PostgreSQL、AS、子进程或 IAM。真实故障注入、角色、回执及双 codec 结果以独立报告为准。

实际 JDK 25 测试字节码含原双 codec 接管入口和新增独立注入路径，未生成 PostgreSQL 操作者报告。干净包的 AS/PS 配置与权限清单语义一致，28 份既有迁移逐字节一致。
