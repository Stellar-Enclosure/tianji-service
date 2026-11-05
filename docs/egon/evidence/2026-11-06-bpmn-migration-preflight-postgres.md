# T4-01 BPMN 迁移预检 PostgreSQL 验收入口

Service baseline `4a090b111b7bc27e2954586548f5b0461208354d`。[Spec](../spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md) · [Plan](../plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md)

独立操作者用例将同一临时 Process 主库上的两份固定 BPMN 发布、原生 Flowable Timer 与只读迁移预检结合。实际运行时，用例检查结构校验结果、错误发布摘要、同名异 issuer 及多余变量拒绝，并比较执行、定时任务、变量和 START 收据的完整前后快照。

| Gate | Observed result |
| --- | --- |
| G0 | Exact operator test class absent at baseline. |
| G1 | Clean owning JDK25 reactor passed all 21 finite tests across three suites, zero failures, errors or skips. |
| G2 | Actual compiled class contains the exact method and opt-in property. No PostgreSQL test report was generated. |
| S1 | Strict Spec and Plan passed, four exact paths and all 28 existing main SQL files verified byte for byte. |

真实 PostgreSQL、Flowable Timer 和只读事务仍待运行验收，使用 Plan §8 的独立操作者选择器。
