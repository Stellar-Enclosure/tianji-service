# S11 有界恢复派发 PostgreSQL 组合验收证据

Service baseline `db8f63d418c8dbbd382df8d53539575529ddb7a1`。[Spec](../spec/2026-11-06-04-46-agent-checkpoint-resume-dispatch-postgres.md) · [Plan](../plan/2026-11-06-04-47-agent-checkpoint-resume-dispatch-postgres.md)

独立操作者入口复用原产品 checkpoint、进程退出、reconciler 和恢复角色夹具。每种 codec 在宿主工作线程及队列都占满时要求无接管收据、无授权调用，且失联切点维持原值。腾出容量后，真实派发器在同一任务中接管和执行，核对 MODEL 边界可见原持久 ACK、原生输入和事件前缀、一次 Fake 模型调用、同 Run 和 attempt 的完成态、预算结算，以及命令重放。原直接调用入口继续单独检验一次性接管凭据。

| Gate | Observed result |
| --- | --- |
| G0 | New dispatcher operator method absent at baseline; original direct method present. |
| G1 | Clean owning JDK 25 reactor passed all 29 finite tests in four suites. Zero failures, errors and skips. |
| G2 | Actual compiled class exposes both exact operator methods and distinct flags, along with the existing fixture gate and recovery role. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan passed. One test source and three documents. All 28 existing migration files are byte-identical. |

实际 PostgreSQL、子进程退出和替代、两种 codec 的同 Run 续跑及 S11 完整验收仍由操作者执行。生产宿主和当前 RESUME 授权属于独立交付。
