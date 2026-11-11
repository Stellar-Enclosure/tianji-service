# T4-02 S12 Worker 持久回执与 Process 确认重放源码证据

Service 基线 `2c72a6ef2f22c460dcae8cd04591237a2642e101`。[Spec](../spec/2026-11-11-16-02-s12-worker-receipt-e.md) · [Plan](../plan/2026-11-11-16-03-s12-worker-receipt-e.md)

| 门禁 | 当前状态 |
| --- | --- |
| R1 | 隔离 Java 25：删除原 Worker 证人的 tenant 相等检查后，`S12WorkerReceiptCorrelationEvidenceTest#another_tenants_worker_row_and_another_process_worker_are_rejected` 唯一出现 `org.opentest4j.AssertionFailedError: Expected java.lang.AssertionError to be thrown, but nothing was thrown.`。隔离运行的同类另两项通过，服务仓库定向 Maven 反证复现同一失败。 |
| G1 | 服务仓库编译五个测试路径，定向和干净制品各通过 16 项，零失败、错误或跳过。 |
| P1 | 实际字节码含独立入口、专用 manifest 属性及关联检查。两个可执行包均未纳入验收类，配置与权限清单内容一致。 |
| S1 | 严格 Spec／Plan 和八条路径核对通过，31 份既有 SQL 逐字节一致。 |
| I1 | 真实独立双租户 Worker 业务库、PS 原生确认与受保护 HTTPS 未执行。 |

W V1 和 PS V4 独立核对 `worker-business-result-v1` 与 `worker-complete-v1`。固定结果只报数量，两个数据库的时刻不作顺序比较。实际 Worker Span Link、collector 和跨重启载体仍是独立工作。
