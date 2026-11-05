# T4-03 Java SDK 受保护的 Agent 恢复命令证据

Service 基线 `7c83b4254d0988d400824268d6ce38ab8725b6c5`。[Spec](../spec/2026-11-06-07-26-agent-recovery-command-sdk.md) · [Plan](../plan/2026-11-06-07-27-agent-recovery-command-sdk.md)

| 门禁 | 结果 |
| --- | --- |
| R1 | 隔离 JDK 25 和所属 Maven reactor 各自删除 ACK 的 `expectedRevision + 2` 精确等值校验后，错误修订用例出现预期的断言失败。 |
| G1 | 所属离线 Maven reactor 的 `OwnerObservationHttpTest` 17 项、`ProcessStartReceiptHttpTest` 6 项通过，零失败、错误或跳过。 |
| P1 | 干净 `verify` 再次通过同 23 项。实际 Contract 和 Client JAR 分别核对 5 个、3 个类字节。AS/PS 配置及权限清单语义一致，28 份既有迁移逐字节一致。 |
| I1 | 实际 IAM、受保护 HTTPS 和 PostgreSQL 证据由操作者单独验收。 |
