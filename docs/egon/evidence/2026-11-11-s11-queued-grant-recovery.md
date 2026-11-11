# T4-03 S11 原授权已持久化的 QUEUED Run 重建发现证据

Service 基线 `bc9e62f34752b78c305a53ffb349f150cdbcffa8`。[Spec](../spec/2026-11-11-15-50-s11-queued-grant-recovery.md) · [Plan](../plan/2026-11-11-15-51-s11-queued-grant-recovery.md)

原 detached 生产者通过产品 AgentLedger 和 V6 sealed grant store 提交首个 `QUEUED` Run。在已提交且首次 claim 前读取并强制回收原 JVM 后，父 JVM 创建独立数据源和新的原 AS poller、authorizer、dispatcher、worker。操作者应证明同一 Run 原 grant 保持，一次首次 claim、一次 Fake 模型调用、一次完成。缺失和过期 grant 不进入发现，当期显式 DENY 保持零 attempt。

| Gate | Evidence |
| --- | --- |
| R1 | 独立将专用库名约束放宽到普通 tianji_it_。`accepts_only_the_dedicated_loopback_queue_fixture` 按预期因缺少 IllegalArgumentException 失败，隔离运行的另一项 handoff 测试通过，服务仓库定向 Maven 反证得到同一失败。 |
| G1 | 服务仓库 Maven 和干净制品均复跑 2 项 handoff、11 项 poller、13 项 authorizer，共 26 项，零失败、错误或跳过。 |
| G2 | 四份测试源实际编译，服务仓库 javap 确认新方法和两个开关。两个 PostgreSQL 入口在隔离 JUnit 默认跳过，制品验证没有 PostgreSQL 执行报告。 |
| S1 | 严格 Spec／Plan 与七条路径核对通过，31 份既有 SQL、两个包的配置和权限清单内容保持一致。两个生产制品均不含新测试类和私有 handoff。 |
| I1 | 待独立 PostgreSQL 操作者。[Operator command](../plan/2026-11-11-15-51-s11-queued-grant-recovery.md#separate-operator-only-i1) |

未执行 PostgreSQL、原 producer JVM、真实模型、HTTP 或 IAM。这个测试使用实际 AS Java 组件进行新发现，原 producer 负责实际 Run/grant 提交。完整服务替换和 IAM 仍由 E 验收。
