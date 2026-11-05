# T4-02 Process owner 读取安全错误格式

Service baseline `76f5f41d07695b8743fc037df6b3ea7f7486de98`。[Spec](../spec/2026-11-06-07-02-process-owner-read-error-envelope.md) · [Plan](../plan/2026-11-06-07-03-process-owner-read-error-envelope.md)

复用 PS 已有的安全错误封装，为启用的三个精确 owner-read GET／HEAD 规范化 IAM 提前返回的 401、403 和 503，保留 HTTP 状态、no-store 及 OpenAPI 规定的闭合错误体。HEAD 保留对应响应元数据与空响应体。既有 Worker 精确匹配、其他路径和成功响应保留原行为。

| Gate | Observed result |
| --- | --- |
| R1 | Removing correlation matching produced the one expected missing no-store assertion. |
| G1 | Six finite suites passed 49 tests. Zero failures, errors and skips. |
| P1 | Clean permission-producer repeated all 49. The exact filter binary is in the actual PS archive. Both AS and PS packaged YAML and semantic permission manifests match baseline. |
| S1 | Strict Spec and Plan, five exact paths, all 28 existing SQL bytes and no PostgreSQL/IT report. |

真实 IAM 和 HTTPS 的保护查询仍待隔离 S12 验收。
