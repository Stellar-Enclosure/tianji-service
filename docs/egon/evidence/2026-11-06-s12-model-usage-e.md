# S12 Fake 模型已定价用量源码证据

Service 基线 `621fa10d0a4d52473368251accd93b14f3718719`。[Spec](../spec/2026-11-06-07-58-s12-model-usage-e.md) · [Plan](../plan/2026-11-06-07-59-s12-model-usage-e.md)

独立 E 夹具使用现有 Fake CONFIG 和两条正常 workflow。以原桥接 Run 和预算槽位精确核对一次 V15 已定价用量和 V2 结算。

| Gate | Observed result |
| --- | --- |
| R1 | 隔离 JDK 25 变体删除精确 Run ID 比较，`S12ModelUsageEvidenceTest#rejects_a_priced_result_from_another_bridge_run` 发生一个预期 AssertionFailedError，零跳过。 |
| G1 | 两模块候选和 E 入口均隔离编译。AS 实际 Adapter 有限用例 3 项，PS 证据与相邻回归 19 项，全部通过。 |
| P1 | 所属 reactor 干净包与成品检查待 owning gate。 |
| S1 | 严格 Spec/Plan、七 Java 路径、三文档及基线 patch 检查通过。 |
| E1 | 待操作者执行实际 V15、HTTPS、IAM、Worker 和隔离 PostgreSQL。 |

E1 只验证上述 Fake 用量源事实。受保护的用量 API、真实供应商费用、晚到帧和完整 S12 分开验收。


所属离线 Maven reactor 复现精确 Run ID 校验的反证。AS 3 项、PS 19 项在定向和干净打包各通过一次，零失败、错误或跳过。实际 PS 字节码包含独立操作者入口，生产 JAR 无验收类。AS JAR 的 V15 资源与源码逐字节一致，配置仍为 false 和空列表，两端权限清单语义与基线一致。基线 29 份 SQL 全部保持原字节。真实 E1 待操作者执行。
