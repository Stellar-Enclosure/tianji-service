# T4-03 受保护的历史 START 收据对账入口

Service baseline `a082ab1d7b98cc524e9e5880075aba6defccfa78`。[Spec](../spec/2026-11-06-06-55-protected-historical-start-receipt.md) · [Plan](../plan/2026-11-06-06-56-protected-historical-start-receipt.md)

默认关闭的固定 USER POST 只接受原 dispatch Run 和 intent。请求线程的专用历史权限以同一已验证 owner 和 servlet 上下文为边界，在 Process 读取前后各刷新一次。配置核对对账宿主使用同一个授权实例。外部仍通过原执行 SERVICE 的独立 READ 读取精确收据，正向结果由既有 V2/V5 追加路径封存。

| Gate | Observed result |
| --- | --- |
| R1 | Bypassing fresh authority checks changed the expected post-read revocation response from 403 to 200 in one owning reactor case. |
| G1 | 34 finite tests passed across five suites. Zero failures, errors and skips. |
| P1 | Clean permission-producer repeated all 34. Actual AS archive adds exactly one protected USER POST, one independent permission, two labels, one scope mapping and a default-off flag. Existing declarations, including checkpoint RESUME, are exact. OAS bytes match source. PS packaged config and semantic PaC match baseline. |
| S1 | Strict Spec and Plan, 12 exact paths, all 28 existing main SQL bytes, no PostgreSQL or IT report. |

AS OpenAPI SHA-256 `a30c9ee37701465d61d122e680bd4389e08c6a2110ea9f6358ff16edb33df11d`. AS executable SHA-256 `229aad17f4b15a460084beac85243684d74be827290f6e61649b6bb2553e29ee`.

实际 IAM、独立原 SERVICE READ 注册、HTTPS 及 PostgreSQL 待隔离配置和验收。
