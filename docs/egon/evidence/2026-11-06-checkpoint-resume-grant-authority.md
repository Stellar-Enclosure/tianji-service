# T4-03 checkpoint 当前 RESUME 授权适配器证据

Service baseline `3bab28b884d62f830b3ef32faaa7b24967774692`。[Spec](../spec/2026-11-06-06-11-checkpoint-resume-grant-authority.md) · [Plan](../plan/2026-11-06-06-12-checkpoint-resume-grant-authority.md)

显式 AS 授权适配器使用既有 Jianshen 客户端和单独的 RESUME 不透明票据。完整 owner、命令和 checkpoint cut 绑定派生固定 UUIDv8 intent。调用前后检查 issuer 信任、票据与当前有效期。权限拒绝和技术不可用分别处理。

| Gate | Observed result |
| --- | --- |
| R1 | Removing exact ticket binding produced one expected missing AgentLedgerException for a ticket from another cut. |
| G1 | Five finite suites passed 50 cases, including eight new authority tests. |
| P1 | Clean permission-producer repeated all 50. Actual AS archive contains the explicit adapter, both packaged configs and semantic permission manifests match baseline. No PostgreSQL or IT report. |
| S1 | Strict Spec and Plan, five exact paths, all 28 existing migration bytes verified. |

独立实际签发、受保护 RESUME 资源策略和隔离 IAM／S11 验收仍待接入和执行。
