# T4-03 RESUME 票据签发和派发交接

Service baseline `845346a6cd0f0bf533e86186f5b7bd64d857d271`。[Spec](../spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md) · [Plan](../plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md)

显式宿主入口先只读核对原 checkpoint cut。对新命令，仅在同一当前 USER servlet 请求内向精确配置的 issuer 客户端签发独立 RESUME 票据。工作线程以同一绑定从有界内存窗口读取不透明票据。精确重放无需签发，重复活跃命令拒绝再次签发。原派发阶段结束或有效期失效后清除票据，调用方停止等待不会提前清除工作票据。

| Gate | Observed result |
| --- | --- |
| R1 | Removing completion cleanup produced one expected assertion that the opaque RESUME ticket was still present. |
| G1 | Five finite suites passed 48 tests including eight AS handoff and three new AR preview cases. |
| P1 | Clean permission-producer repeated all 48. Actual AS archive contains the handoff and nested AR contains the matching preview. Both packaged configs and semantic PaC match baseline. |
| S1 | Strict Spec and Plan, eight exact paths, all 28 existing migration bytes and zero PostgreSQL/IT reports. |

受保护 RESUME 资源与实际 IAM、部署装配和 S11 仍待独立交付及验收。
