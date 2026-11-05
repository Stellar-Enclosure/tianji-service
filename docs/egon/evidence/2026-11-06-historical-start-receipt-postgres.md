# T4-03 历史 START 收据 PostgreSQL 验收入口

Service baseline `e789021e4c57f25110f7cff3496fd58c5ec42a1c`。[Spec](../spec/2026-11-06-05-43-historical-start-receipt-postgres.md) · [Plan](../plan/2026-11-06-05-44-historical-start-receipt-postgres.md)

独立操作者用例复用真实 V13 dispatch 写入和 ledger 终结，准备 UNKNOWN 和 DISPATCHING。用受控历史授权及 Process 收据端口隔离外部 IAM/网络。用例核对拒绝、缺失和期限未过不追加，DISPATCHING 的两个并发调用仅追加一次，同证据返回原 ACK，矛盾正向证据拒绝覆盖，并逐项比较原 V13、Run 和预算记录。

| Gate | Observed result |
| --- | --- |
| G0 | New exact operator selector absent at baseline and original journal selector present. |
| G1 | Clean JDK25 source reactor passed the four scoped finite suites, 45 tests, zero failures, errors or skips. |
| G2 | Actual compiled class contains both exact operator methods, the new method-only switch, existing loopback/vector gates and the exact receipt operation. No PostgreSQL test report was generated. |
| S1 | Strict Spec and Plan, four exact paths and all 28 existing migration bytes verified. |

Actual PostgreSQL, concurrent unique-key behavior, and real external Process/IAM remain independently gated. The test only uses the existing V1–V13 migrations through the authorized fresh fixture path in Plan §8.
