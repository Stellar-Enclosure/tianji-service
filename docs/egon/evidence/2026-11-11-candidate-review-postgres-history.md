# T4-01 A3 PostgreSQL 历史初始化修正证据

基线 `13b59a7dda2aeb013eff1c101d4f3f9705cba316`。[Spec](../spec/2026-11-11-15-05-candidate-review-postgres-history.md) · [Plan](../plan/2026-11-11-15-06-candidate-review-postgres-history.md)

| 门禁 | 观察 |
| --- | --- |
| 基线反证 | 原用例调用 `baseline()`，配置 `baselineVersion("0")`。生产 `laterMigrations` 包含版本 0 和 NULL，要求首项为版本 8 的成功 SQL。Flyway 11.15.0 的 `DbSchemas` 字节码包含 `SchemaHistory.addSchemasMarker` 调用。 |
| 修正源码 | guard 后由 owner 建立空 schema，显式关闭 Flyway 隐式建 schema 与自动 baseline。V10、V11 分别检查完整历史，V11 后继续校验之前历史快照。 |
| C1 | 待所属模块记录实际 Java 25 编译、有限回归和字节码检查。 |
| S1 | 独立补丁和严格文档待本次提交前复核。 |
| I1 | 实际 PostgreSQL 历史和原用例的后续物理断言由操作者验证。 |

所属离线 Maven reactor 与干净打包各通过 27 项有限回归。编译字节码确认显式创建空 schema、关闭隐式 Flyway 初始化、顺序断言 SQL 历史和专用 opt-in。两个实际可执行制品的配置与权限清单语义核对通过，29 份基线迁移逐字节一致。真实 PostgreSQL 验收由操作者运行。
