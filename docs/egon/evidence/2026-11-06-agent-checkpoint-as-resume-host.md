# T4-03 AS checkpoint 恢复宿主装配证据

Service baseline `fe545cbcfcdbc184b7f475e85b7542db48432a35`。[Spec](../spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md) · [Plan](../plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md)

受信宿主通过显式导入提供专用恢复连接池、预期登录角色和独立 RESUME 权威。装配使用普通、恢复两条事务连接检查独立登录角色和同一可写主库的事务 advisory lock 竞争，两个探针都回滚。后续复用现有 V16 SELECT-only、双租约接管和同一恢复数据源上的受控 ADK 执行。MODEL 继续由现有当前授权器验证，普通账本的 reconciler 未运行时拒绝新派发。宿主关闭先停止接纳，再最多等待五秒，随后按需中断本地线程。

| Gate | Observed result |
| --- | --- |
| R1 | Owning Maven reactor produced the single expected missing startup failure when the recovery-side advisory lock result was ignored. |
| G1 | 56 finite tests passed across six suites, including explicit import, default absence, current reconciler, exact login, masked cleanup failure and current MODEL guards. |
| P1 | Clean permission-producer repeated all 56. Actual AS executable includes the host configuration and the verifier in nested AR, with no default application import. AS and PS packaged YAML and semantic permission declarations match baseline. |
| S1 | Strict Spec and Plan, six exact paths, zero PostgreSQL/IT reports and all 28 existing migration files byte-identical. |

AS archive SHA-256 `183e537343d25f7325197ee4e87888596e0bb28736d9bfb704813aa912cea161`. PS archive SHA-256 `f7173102b67a830795a7a596a88c73b05c30d6bc52be032dc688358beff74c47`.

此范围要求关闭 V15 模型用量记账，避免恢复池缺少对应账本权限时漏记。真实数据库角色、固定连接池、独立当前 RESUME 权威、宿主调用入口和完整 S11 仍待验收或接入。
