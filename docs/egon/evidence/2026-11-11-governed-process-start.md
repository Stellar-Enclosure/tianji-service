# T4-01 A5 治理 START 源码证据

Service 基线 `e3e5603790b6e5ef51d0ec2f4583286463ad1ae8`。[Spec](../spec/2026-11-11-15-28-governed-process-start.md) · [Plan](../plan/2026-11-11-15-29-governed-process-start.md)

| 门禁 | 结果 |
| --- | --- |
| R1/G1/P1 | 所属离线 Maven reactor 复现提交前到期反证。五类 52 项定向及干净打包均通过，零失败、错误或跳过。 |
| I1 | 实际主库、授权、原生实例和真实指针切换待独立验收。 |


- 实际 PS JAR 核对 V13 和六个生产类。成品默认装配使用原三参数构造，显式治理入口和来源写入字节码均存在。
- 两端实际配置、权限清单语义和 30 份基线迁移逐字节核对通过。唯一新增 V13，SHA-256 为 `e58af97ce341c41f79a8d79a1aa7b9da430e8b72cbe244ae1508c034180c6e21`。
- 当前独立 IAM 和 Worker 权威接入、真实 Flowable/PG 事务和并发、受保护治理入口仍待后续步骤与验收。
