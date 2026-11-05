# T4-03 精确 Process START 收据 Java SDK 证据

Service baseline `52dd82be9b4831c5e3412bf63f0e2c279a0c7dfe`。[Spec](../spec/2026-11-06-05-05-process-start-receipt-sdk.md) · [Plan](../plan/2026-11-06-05-06-process-start-receipt-sdk.md)

独立 Java 查询使用调用者提供的当前 owner READ 凭据，向现有接口发送一次精确 GET。只有 200、JSON、no-store、完整且唯一的字段及 APPLIED 才返回正向收据。404 保持失败和无定论语义。请求和响应验证共享现有 SDK 的单次发送、限时、取消、禁止重定向及有界订阅器。

| Gate | Observed result |
| --- | --- |
| R1 | Removing duplicate detection in the owning reactor produced one expected missing ProcessClientException for duplicate receipt status. |
| G1 | Six finite suites passed 50 tests. Six new tests exercise the real Java SDK against in-memory JDK HTTP transport and the bounded subscriber. |
| P1 | Clean permission-producer reactor repeated all 50. Exact contract and SDK classes matched their standalone JARs. Actual AS/PS config and semantic PaC, and the Process OpenAPI at the executable archive root, matched baseline. |
| S1 | Strict Spec and Plan passed, seven exact paths and all 28 existing main SQL files byte-identical. |

Contract archive SHA-256 `267c80f4f1acd0d7aa51a09245f90286883ca05b6001359edfdbfb4f19a8ff40` and client archive SHA-256 `79f166ce32fd35d9120aa6e5197ab0c06b7375e54c2a56fe6575ef970b0b6189`.

真实 HTTPS、当前 IAM 与原执行 SERVICE 的 READ 凭据由隔离环境验收。历史 Tool 对账授权、证据持久化和向运维读面公开仍是后续步骤。
