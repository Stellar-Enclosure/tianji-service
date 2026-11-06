# T4-01 A3 发布候选审阅源码证据

隔离有限证据初次取得于 7c83b4254d0988d400824268d6ce38ab8725b6c5。正式源码评审基线为 7fd8e37057686d1e9fc7d3544668dccd0bc2efcf，九个补丁路径的基线内容无变化。交付状态以实际执行记录为准。[Spec](../spec/2026-11-06-07-46-platform-release-candidate-review.md) · [Plan](../plan/2026-11-06-07-47-platform-release-candidate-review.md)

| Gate | Observed in isolated scratch | Boundary |
| --- | --- | --- |
| S0 | 基线工作树干净。九个源码/测试/SQL 路径的精确补丁通过 git apply --check。V11 raw SHA-256 434d1cf4947df39feb556f29ceda53ba0a36e36abc6d9a53d5d26dd85bceedfe；Flyway 逐行有符号 CRC32 -1411671896。 | 补丁检验为只读，未应用迁移。 |
| U1 | Java 25 编译草稿；Junit Platform 6.0.3 的四个类共 27 项，零失败、零跳过。 | 复用本地已缓存的依赖和既有 target 编译类；独立输出目录。无 Maven 运行。 |
| U2 | 已有 A2 签名和快照断言通过，新增 A3 测试覆盖 SERVICE/USER、当前授权与 trust、owner 读、精确重放、命令竞争、after-insert 到期回滚。 | A2 使用公开签名向量；PS 发布和 JDBC 使用 Mockito。 |
| U3 | V11 正确前缀和 catalog fixture、V10 Recovery 兼容与 exact drift 拒绝。两张表走 verifyPlatformCandidateReviews → verifyCaseRole(table,false,true) → 四参校验，拒绝列级 UPDATE 与 REFERENCES。 | 两张表的列级条件均由 assertColumnPrivilegeSafety 实际断言；这是生成 SQL/模拟目录证据。 |
| I1 | 待单独授权与验收。 | 真实 PostgreSQL Flyway V10→V11、目录类型/反解析、受限角色、两个实际连接的并发/回滚。 |
| E1 | 待实际 owner 装配和验收。 | 生产信任、真实人员职责与授权、签发/撤销、依赖 owner 和治理激活。 |

两个 append-only 表只由同源 PS JDBC 事务写入。唯一约束仲裁后用新的 READ COMMITTED 查询区分历史重放、同命令不同载荷和竞争命令。数据库时钟复核不等于全局强序撤销。未生成配置、角色授权、HTTP 或 PaC，也未启动应用或数据库。


## 所属模块与实际制品

- 基线验签 8 项和 schema 9 项通过。删除成功 INSERT 后的到期复核，原定向测试出现预期的 AccessDeniedException 缺失。恢复原候选后，验签 8 项、审阅服务 6 项、事务存储 2 项、schema 11 项在定向与干净 `verify` 各通过一次，零失败、错误或跳过。
- 实际 PS JAR 的 V11 与四个 PS 类字节一致。AS JAR 无 V11。两端实际 YAML 与权限清单语义一致，28 份既有迁移逐字节一致。V11 为本次唯一新增迁移。
- 从实际 PS JAR 显式调用原离线验签 CLI。合成向量成功输出与原 A2 逐字节相同，错误信任 pin 与篡改签名均退出 65，stdout 为空。
- 生产权限、配置和入口保持现有部署边界。A3 需显式同源装配和可信端口。I1 的真实 V11、受限角色和并发，以及激活／回滚均待独立验收。
