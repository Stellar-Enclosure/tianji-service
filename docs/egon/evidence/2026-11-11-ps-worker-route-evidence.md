# T4-01 A6 PS 本地 Worker 路由证据

Service 基线 `ae8ad136e65a5924ebf236dcddda3274cd049696`。[Spec](../spec/2026-11-11-15-43-ps-worker-route-evidence.md) · [Plan](../plan/2026-11-11-15-44-ps-worker-route-evidence.md)

| 门禁 | 已执行或待执行 |
| --- | --- |
| R1 | 隔离 JDK 25 中，先选择第一个绑定及按其 actor 过滤注册的变异，在 `ProcessAgentLocalWorkerRouteEvidenceTest#two_valid_different_worker_actors_on_the_same_native_tuple_are_ambiguous` 出现唯一 `org.opentest4j.AssertionFailedError: expected: <true> but was: <false>`。隔离运行的其他 3 项通过，服务仓库定向 Maven 反证得到同一失败。 |
| G1 | 隔离 JDK 25 与服务仓库 Maven 各通过三个测试类 11 项，零失败、错误和跳过。 |
| P1 | 服务仓库离线 clean verify 复跑 11 项。核对 PS 成品的适配器及 Route 字节码，AS 成品无两类，两个包的配置、权限清单内容和迁移资源与基线一致。 |
| S1 | 严格 Spec／Plan 验证和五条路径核对通过，31 份既有 SQL 逐字节一致。 |
| I1 | 实际 PS/AS 部署、跨实例唯一、Worker 历史路由保留、授权与运行证明待独立验收。 |

固定注册摘要向量 `5c85e72f54d0730fd8f7a2990c5300b173aac743c3b17e4fad23d5f4fef3553c`。三元组唯一检查包含完整服务身份，并复用现有 OAuth key 校验。令牌值、client secret 和 credential version 均不在摘要输入。有限验证只使用隔离 fixture 与本地缓存。
