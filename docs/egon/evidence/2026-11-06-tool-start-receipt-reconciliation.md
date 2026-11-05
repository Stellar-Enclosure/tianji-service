# T4-03 历史 Tool START 正向对账证据

Service baseline `2c886ae84e01e01d095a108bcbe36af86b3a80e7`。[Spec](../spec/2026-11-06-05-28-tool-start-receipt-reconciliation.md) · [Plan](../plan/2026-11-06-05-29-tool-start-receipt-reconciliation.md)

受信宿主显式调用历史对账。原 owner、dispatch Run、V13 调用及动作、固定工具与 SERVICE 后端共同锁定原 Process 请求。在 GET 前后核对当前 AS 历史授权，按原 SERVICE 的独立当前 READ 凭据调用一次原 Process SDK。仅正向 APPLIED 证据可通过条件 INSERT 追加 V2/V5 收据。并发重放读取首份精确记录，保持原 Run、V13、V14、预算和 Session。旧执行随后已记 APPLIED 时，只允许重放既有且与原生回执匹配的对账证据。

| Gate | Observed result |
| --- | --- |
| R1 | Owning reactor failed exactly once when the recorded SERVICE identity comparison was removed. |
| R2 | Owning reactor failed exactly once when the post-GET current authorization check was bypassed. |
| R3 | Owning reactor failed exactly once when the invocation stamp equality was inverted. |
| G1 | 101 finite tests across eight suites, including 19 new tests and 43 existing START adapter cases, zero failures, errors or skips. |
| P1 | Clean permission-producer repeated all 101. Actual AS executable contains the SDK reader and nested AR reconciliation classes. Both packaged configurations and semantic PaC match baseline. No PostgreSQL or IT report was generated. |
| S1 | Strict Spec and Plan, 14 exact paths, and all 28 existing main migration files verified byte for byte. |

AS archive SHA-256 `773755781fbf415b4f38aebbbc1079ea106caa7c6242630d053d56198430bd73`. PS archive SHA-256 `12914e984cc1eb6232d47acc294b51776b7ac6780c8b3b1e01775200cd98c88f`.

当前历史授权提供者、原执行 SERVICE 独立 READ 配置、真实 PostgreSQL/HTTPS/IAM 与外部入口仍需独立交付或运行验收。
