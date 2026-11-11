# T4-01 A4/A5 PostgreSQL 与 Flowable 操作者夹具源码证据

| 项目 | 结果 |
| --- | --- |
| Service 基线 | `423b54b68081172dcc3d5c998ed7bd45f0995a77` |
| 产品基线 | A4 `e3e5603790b6e5ef51d0ec2f4583286463ad1ae8`，A5 `4313d3319ca228c2310d06dba9128dcb981d3f30` |
| 范围 | PS 两份测试源，Spec、Plan 和本证据 |
| I1 实际验收 | 待运行 |

## 有限源码门

- JDK 25 编译两份测试源。编译后的方法为 `PlatformActivationGovernedStartPostgresTest#rolls_back_the_pointer_and_keeps_each_native_start_pinned_to_its_revision`，类检查 PostgreSQL URL，方法检查 `tianji.process.postgres.activation-start=true`。
- JUnit 实际发现一个操作者方法。未提供 URL 与开关时跳过一次。仅提供 URL 属性时跳过一次。两个属性均设置并给非法 URL 时，测试在 JDBC 前得到精确的 `IllegalArgumentException`。这里未打开连接。
- 有限选择器为 `PlatformReleaseArtifactCandidateTest,PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseActivationServiceTest,PlatformReleaseActivationStoreTest,GovernedProcessStartServiceTest,ProcessApiSchemaVerifierTest,ProcessReleaseServiceTest`。Java 25 JUnit 实际发现 73 项，73 项通过，零失败，零跳过。
- 使用现有 ProcessModelValidator 离线校验原始 BPMN 和仅更改 `PT5M` 至 `PT6M` 的副本。原发布 manifest 算法、JDK Ed25519 和产品 A2 verifier 确认两份规范签名。测试运行时从真实 publish 结果提取定义哈希和发布 manifest。
- Spec 与 Plan 严格结构校验通过。既有 PS 原生发布验收的 `verifyReleaseTransactions` 已包含同模型资源键按不同 release 部署后比较不同 Flowable version 的断言。本夹具进一步检查版本连续、部署 ID、定义 ID 和内容摘要有别。

## 服务仓库复核

- Maven 在缺少专用开关时跳过一次，显式开关与非法 URL 的组合产生一项预期的 JDBC 前拒绝。定向回归与干净制品各通过 73 项，零失败、错误和跳过。
- JDK 25 从服务仓库测试输出复核精确方法和两个开关。实际 AS／PS 可执行包均不含新验收类，PS 包的四份 A4／A5 类和 V12／V13 迁移字节准确。31 份基线 SQL、两个包的配置和权限清单内容保持一致。

## 待执行 I1

按[实施计划的独立 I1](../plan/2026-11-11-15-52-platform-activation-governed-start-postgres.md#separate-operator-only-i1)选择方法。测试使用操作者提供的一次性空库、独立 runtime role 和原产品 Flowable/JDBC 服务。成功报告须显示实际执行一项且未跳过，并且内部所有断言通过。

- Process 历史为 V1–V13 的纯 SQL 记录。V11→V12→V13 逐项迁移，分别测试缺少追加权限时拒绝。Flowable 和既有 Outbox 由各自初始化入口预置。
- 真实发布、验签和独立审阅后，两个 A4 竞争只落一条新修订。旧观察、Worker 指纹不匹配和错误回滚目标被拒。
- `pg_locks` 对同一 tenant advisory key 观测共享 START 后端和被其阻塞的排它回滚后端。旧实例保持 v1/v2，回滚后新的 START 选择修订 3 下的 v1，精确重放沿用原实例。
- V13 触发器在同事务见到原生 runtime、history 和 APPLIED 收据后故意抛错，独立连接核对完整回滚。普通 START 沿既有路径重放。
- 该场景使用固定测试 actor、测试签名密钥和独立的测试权威接口。当前产品 IAM、正式 Agent 注册与部署权威、受保护 API 和完整 S07 需另外验收。
