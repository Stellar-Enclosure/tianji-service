# T4-03 S11 本地 START 成功收据提交后原执行进程退出证据

Service 基线 `ec9dc02f8ab00ec788028581e8ab61ae2b5df47b`。[Spec](../spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md) · [Plan](../plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md)

原 JVM 使用真实 V13 recorder 与原受控 START transport，先检查 DISPATCHING/空收据，再于本地成功 receipt 提交后的现有 observer 停驻。父 JVM 应在真实 PostgreSQL 看到同一个 SUCCEEDED invocation、APPLIED intent 和仍 RESERVED 的 V2 预算，强制回收原 JVM。新 ledger 单次 reconcile，原 receipt 完整，悬空预算 UNKNOWN，旧租约与新的 claim 均拒绝，owner 观察到完整本地 receipt。

| Gate | Evidence |
| --- | --- |
| R1 | 删除 scratch marker 对 invocation UUID 的规范检查，`rejects_noncanonical_or_ambiguous_receipt_marker_fields` 因缺少预期 IllegalArgumentException 失败。服务仓库定向 Maven 反证复现同一失败。 |
| G1 | 服务仓库定向和干净制品各通过 2 项 handoff、30 项 recorder、5 项 disposition，共 37 项，零失败、错误或跳过。 |
| G2 | 服务仓库编译三份测试，javap 核对精确入口与子进程属性。隔离运行默认跳过三项 PostgreSQL，干净制品没有实库执行报告。 |
| S1 | 六条路径、严格 Spec／Plan 和 31 份既有 SQL 均核对通过。两个生产包均未纳入夹具或 handoff，配置与权限清单内容一致。 |
| I1 | 待操作者实际运行。[Operator command](../plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md#separate-operator-only-i1) |

本次未运行 PostgreSQL、原 recorder 子进程、真实 PS HTTP 或 IAM。既有 PS 提交后响应丢失产生本地 UNKNOWN，属于不同切点。
