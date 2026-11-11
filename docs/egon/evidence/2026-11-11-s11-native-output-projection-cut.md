# T4-03 S11 原生 Session 输出已提交而账本投影未提交证据

Service 基线 `4313d3319ca228c2310d06dba9128dcb981d3f30`。[Spec](../spec/2026-11-11-15-33-s11-native-output-projection-cut.md) · [Plan](../plan/2026-11-11-15-34-s11-native-output-projection-cut.md)

原产品子 JVM 在真实持久 Session 输出已提交后进入精确 `AGENT_OUTPUT` 投影等待。操作者应观察独立提交的原生行和仍未提交的账本投影，在释放锁之前强制回收原 JVM。现有 reconciler 使 Run 进入 `SUSPENDED / LEASE_EXPIRED`。两种 codec 均保留原生输出，首条输入恢复预览和接管冲突，零授权和 RESUME 回执。

| Gate | Evidence |
| --- | --- |
| R1 | Java 25 独立 scratch 副本仅移除新阶段摘要守卫。`round_trips_output_projection_as_a_distinct_launch_without_a_digest` 因预期的 IllegalArgumentException 未抛而失败，隔离运行的原五项通过，服务仓库定向 Maven 反证得到同一失败。 |
| G1 | 服务仓库与干净制品分别通过 6 项 handoff、7 项 observation、14 项 takeover，共 27 项，零失败、错误或跳过。 |
| G2 | 服务仓库 JDK 25 编译测试，javap 确认精确方法和专用属性。隔离 JUnit 默认跳过 8 个 PostgreSQL 用例，制品验证没有 PostgreSQL 执行报告。 |
| S1 | 严格 Spec／Plan 校验与六条路径一致。31 份基线 SQL、AS／PS 打包配置和权限清单内容核对通过，两个可执行包均未包含测试类和 fork handoff。 |
| I1 | 待授权操作者执行。[Operator command](../plan/2026-11-11-15-34-s11-native-output-projection-cut.md#separate-operator-only-i1) |

本步验证未启动 PostgreSQL、原 worker JVM、真实模型、HTTP 或 IAM。真实触发器、数据库可见性、进程退出及两种 codec 的运行结果以独立 I1 报告为准。
