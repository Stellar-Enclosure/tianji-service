# S07 Process START 授权组合验收证据

Service baseline `525f4afa9e0da01583811f292f21331ef9a77709`。[Spec](../spec/2026-11-06-04-32-s07-process-start-authorization.md) · [Plan](../plan/2026-11-06-04-33-s07-process-start-authorization.md)

共享用例经过实际身份过滤器、Jianshen 权限守卫和 Process HTTP handler。有限测试使用受控 verifier、PDP 与模拟的业务服务，校验授权 SERVICE、缺 release 或 START 资源权限、缺 START scope、跨租户、伪造 tenant、非受信 issuer 及同名 USER。隔离 PostgreSQL 分支接入实际 Process 服务和 Flowable，核对唯一实例、唯一待处理 review 任务及按完整 SERVICE owner、命令和请求摘要匹配的 APPLIED 收据。该分支使用独立 BPMN 定义。

| Gate | Observed result |
| --- | --- |
| G0 | New selector and PostgreSQL helper were absent at the locked baseline. |
| R1 | Clean owning Maven reactor failed exactly once with the expected HTTP 403 versus 200 assertion when the no-grant fixture was given START. Zero errors and skips. |
| G1 | Clean owning reactor passed the exact finite method. The supplementary full ProcessPermissionIntegrationTest passed 13 tests. Zero failures, errors and skips. |
| G2 | JDK 25 javap confirmed the exact finite method, shared probe, PostgreSQL helper and existing opt-in annotation in actual compiled classes. No PostgreSQL execution report was produced. |
| S1 | Strict Spec and Plan passed. Five exact paths, existing mock-path test bodies and all 28 existing migration files verified. |

实际 PostgreSQL 收据、真实 IAM 及 Agent → Process 的完整 S07 E 由独立环境验收。运行入口见 Plan §8。
