# S10 Worker COMPLETE 成功响应丢失验收证据

Service baseline `b46a86a7f47dd029190c52092a5e4c644eb8caec`。[Spec](../spec/2026-11-06-04-40-s10-forwarded-complete-ack.md) · [Plan](../plan/2026-11-06-04-41-s10-forwarded-complete-ack.md)

恢复验收对两个租户各增加一个独立实例。故障 relay 先核对业务收据与当前租约，再转发精确 COMPLETE。Process Server 返回成功和匹配收据后，relay 丢弃该响应一次，只接受请求字节摘要一致的重试，并要求返回同一收据和决定时刻。验收核对唯一原租约、一次业务效果、一次原生推进，以及原业务收据。

| Gate | Observed result |
| --- | --- |
| R1 | Removing only the retry digest guard caused the one expected missing IllegalStateException on changed output. |
| G1 | 13 finite tests passed, two new relay cases and 11 existing harness cases. A separate no-network probe of HttpProcessClient captured byte-identical first and retry request bodies for the same command. |
| P1 | Clean permission-producer reactor repeated all 13, zero failures, errors or skips. The existing WorkerRecoveryIT class includes the exact new branch. No IT execution report exists. Both actual executables exclude fixtures and preserve semantic PaC and packaged configuration. |
| S1 | Strict Spec and Plan, exact six paths and all 28 existing migration bytes verified. |

既有恢复验收报告格式继续使用原有见证字段。新增分支通过现有 JUnit 选择器的断言验收。实际 HTTPS 断线、当前 IAM、PostgreSQL 与双租户 S10 E 待运行。
