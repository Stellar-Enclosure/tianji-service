# T4-01 A3 PostgreSQL 候选审阅测试源码证据

评审基线 28ad7d522a214adf7544545c7fe9eedf3e645ae5，依赖 A3 提交 621fa10d0a4d52473368251accd93b14f3718719。[Spec](../spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md) · [Plan](../plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md)

| Gate | Actual source preparation |
| --- | --- |
| C1 | Java 25 使用既有缓存的 PS 编译类与依赖编译了两个新增测试源。javap 检查见确切测试方法、既有 PostgreSQL URL 门禁和专用 candidate-review 方法门禁。 |
| U1 | A2 签名、A3 service/store 和 Process schema 四个有限类共 27 项通过，零失败、零跳过。没有选中 PostgreSQL 方法。 |
| S1 | Spec/Plan 严格校验通过。精确五路径补丁通过 git apply --check。源码准备只写隔离草稿。 |
| I1 | 操作者测试尚未运行。实际 Flyway V10→V11、目录、角色、SQLSTATE 和双连接行为等待单独授权的隔离验证。 |

操作者测试只在明确选择的方法、合规的新 loopback tianji_it_ 数据库与预置临时 runtime role 下执行。夹具在隔离 schema 使用既有迁移和测试专用 grants。它使用公开合成签名和授权，通过实际 ProcessReleaseService 的 JDBC 查询读取固定夹具记录。没有部署模型、启动服务或使用真实 IAM。锁视图、等待后回滚、唯一赢家与历史回放的断言目前仅有编译证据。

所属离线 Maven reactor 及干净打包均通过 27 项有限回归。实际测试字节码包含专用开关、URL 门禁与双连接锁见证，PS 生产 JAR 无验收类。两端成品配置和权限清单语义核对通过，29 份基线迁移逐字节一致。真实 PostgreSQL 方法由操作者单独运行。
