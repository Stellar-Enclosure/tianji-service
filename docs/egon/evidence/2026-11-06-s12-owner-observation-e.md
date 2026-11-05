# S12 原始所有者保护读取源码证据

Service 基线 `9013b635dcc950968a8c100b10d6725bc5543924`。[Spec](../spec/2026-11-06-07-30-s12-owner-observation-e.md) · [Plan](../plan/2026-11-06-07-31-s12-owner-observation-e.md)

本步骤为现有双租户正常路径增加独立操作者验收。两个租户共用同一个 correlation，各用原 PS USER 和 AS bridge SERVICE 的精确 READ，核对同一所有者的三项证据和跨租户实例的两次 404。

| Gate | Observed result |
| --- | --- |
| R1 | 隔离 JDK 25 和所属离线 Maven reactor 分别删除精确 AS Run ID 比较的变体后，`S12OwnerObservationEvidenceTest#rejects_foreign_owner_ids_even_when_both_tenants_share_correlation` 在外租户 AS Run 注入断言处发生一次预期 AssertionFailedError，零跳过。 |
| G1 | 所属离线 Maven reactor 编译六个测试路径及 E 入口，4 项新增有限用例和 19 项相邻回归通过，零失败、错误或跳过。编译字节码核对确认 reconcile 在各自路径内调用，跨租户保护读取阶段不复用前一路径的时限。 |
| P1 | 干净打包再次通过 23 项。实际 PS 测试字节码保留独立入口与私有属性，两个生产 JAR 无验收夹具，配置与权限清单语义一致，28 份既有迁移逐字节一致。 |
| S1 | 精确六个 Java 测试路径和三份审查文档。 |
| E1 | 待操作者验证真实 Shoubing/Jianshen、隔离 PostgreSQL、HTTPS 与固定 Collector。 |

E1 的确切选择器、权限资源、私有输入和报告要求见 Plan §8。源代码门禁只编译 E 入口。
