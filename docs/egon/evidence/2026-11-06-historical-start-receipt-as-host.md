# T4-03 AS 历史 START 收据宿主

Service baseline `1d98cc93d68fbc736cf1d2ba5dc19aead0a6bf93`。[Spec](../spec/2026-11-06-06-06-historical-start-receipt-as-host.md) · [Plan](../plan/2026-11-06-06-07-historical-start-receipt-as-host.md)

显式宿主导入独立历史授权、原固定绑定和按后端 SHA-256 匹配的 READ 注册。注册必须覆盖所选保留绑定，并与保留的 START 注册隔离。无令牌预检校验 issuer、租户、原 SERVICE、受众和 READ 权限键。宿主复用普通账本的精确数据源，拥有独立的有界 reader 和禁止重定向的 HTTP 客户端。

| Gate | Observed result |
| --- | --- |
| R1 | Removing START registration exclusion produced one expected missing IllegalStateException in the owning reactor. |
| G1 | Five finite suites passed 96 tests, including eight new cases and the actual dormant AS shell. |
| P1 | Clean permission-producer repeated all 96 with zero errors, failures or skips. Actual AS archive contains the explicit host class, and the default application has no import. AS/PS packaged YAML and semantic permission manifests match baseline. |
| S1 | Strict Spec and Plan, eight exact paths and all 28 existing migration bytes. No PostgreSQL or IT report was generated. |

真实独立历史授权、原 SERVICE READ 注册及隔离 PostgreSQL／IAM／HTTPS 待接入和验收。
