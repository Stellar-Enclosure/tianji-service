# T4-03 Agent checkpoint 恢复调度证据

Service baseline `697d50759a300c1a56ca81b1d411a653ee4a3185`。[Spec](../spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md) · [Plan](../plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md)

调度器在有界工作槽内以同一个任务完成接管和原 invocation 续跑。接管服务与执行器绑定同一数据源，调度、接管和凭据消费使用同一个 RESUME 授权实例。任务开始前的拒绝或关闭不会创建租约。接管已提交时，等待者收到原持久 ACK；恢复尝试的结果仍由既有账本和租约处置。关闭只移除该调度器尚未开始的任务，已开始的任务可排空。

| Gate | Observed result |
| --- | --- |
| R1 | Owning reactor produced the single expected missing IllegalArgumentException when the exact source and authority guard was removed. |
| R2 | Owning reactor produced the single expected nonempty-queue assertion when the rejection branch omitted removal after a stock ThreadPoolExecutor enqueued before its ThreadFactory rejected. |
| G1 | 29 finite tests passed across four owner suites. The new dispatcher suite contributed 10. Zero failures, errors and skips. |
| P1 | Clean permission-producer reactor repeated all 29 tests. The actual AS archive contains the matching dispatcher and task classes in AR. The PS archive has no AR. Packaged application configuration and semantic PaC matched the baseline for both. |
| S1 | Strict Spec and Plan passed. Seven exact paths and all 28 existing main SQL files checked against the baseline byte for byte. No PostgreSQL or Failsafe test report was generated. |

AS archive SHA-256 `55aa9b9716c476ad991c2656c69f23748cf8682189b806f1ac32bb01b43b0e0f`, manifest SHA-256 `60387ab2567ff593b33a495d6c23c81c607c06ebb1277373c70561260227a193`. PS archive SHA-256 `6323e81c932785826e1e16897da5c13bf8a518c9c6082c9eee62f58ba8698638`, manifest SHA-256 `798442e21a17850c3c840b364afccf7a03e4111bee071db35c93fc4c9fcc7799`.

这一步交付内部 Java 调度入口。生产宿主装配、独立的当前 RESUME 授权、恢复角色和完整 PostgreSQL/S11 验收待单独完成。
