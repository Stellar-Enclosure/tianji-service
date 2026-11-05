# S11 受保护 checkpoint RESUME 验收准备

基线 `79779815f88b2b128363e27922ceac093157826b`。新增 AS 测试 `AgentCheckpointResumeHttpPostgresTest` 与离线对照 `AgentCheckpointResumeWitnessTest`。测试调用既有受保护 HTTPS 入口，连接已由操作者配置的主库只读 observer。默认跳过受保护验收。

[Spec](../spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md) · [Plan](../plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md)

## 前置条件

- 使用专用、一次性、仅本机可达的真实 AS 与 PostgreSQL。HTTPS 必须是带显式端口的 `https://127.0.0.1:<port>`，证书必须含该 IP 并被测试 JVM 信任。PostgreSQL 必须是 `jdbc:postgresql://127.0.0.1:<port>/tianji_it_checkpoint_<name>` 的可写主库，连接串只含该地址和库名。
- 由操作者配置普通和恢复两个独立连接角色与同库主写节点、V16、已批准的真实 Shoubing/Jianshen 策略、隔离租户、已准备的单模型无工具定义。启用 `stellar.tianji.agent.api.enabled=true`、`stellar.tianji.agent.recovery.checkpoint-capture.enabled=true` 与 `stellar.tianji.agent.checkpoint-resume.api.enabled=true`。显式提供 `tianjiCheckpointResumeDeployment` 及其独立签发配置。当前恢复宿主要求 `stellar.tianji.agent.storage.model-usage-enabled=false`。使用部署既有的原始 MODEL 授权。见既有受保护 RESUME 交付及 AS 宿主文档。验证真实签发方与决策方的当前策略，确认专用目的为 CHECKPOINT RESUME、独立于原 MODEL 授权，且 READ 与 RESUME 的受保护资源生效。
- 提供与真实同一 USER、tenant、issuer 对应的两个独立 bearer。第一份只具备 `tianji:agent:read` 及 `TIANJI_AGENT_READ`。第二份同时具备 READ 与 `tianji:agent:checkpoint-resume` 及 `TIANJI_AGENT_CHECKPOINT_RESUME`。两份 bearer 使用绝对路径的操作者本人所有常规文件，权限 `0600`，值可有一个末尾 LF。观察者密码用独立同规格文件。使用 JVM 私有信任配置和隔离环境提供凭据。
- 使用真实普通入口创建、授权和运行准确的隔离 run。原进程在首条原生输入与 V16 原子提交之后、调用模型或工具之前退出。由替换实例既有 reconciler 产生 `SUSPENDED/LEASE_EXPIRED`，等旧 Session 租约过期，保留至少 50 秒 run deadline。Session 需只有原始输入，必须具有相同 checkpoint、run、Session 和 owner 元组。每个 codec 使用独立的新 cut。
- AR 的 `AgentCheckpointCapturePostgresTest` 既有专用 fork 用例覆盖两个 codec 的强制退出、真实持久化、reconciler 与接续链路，使用控制的授权边界。它提供切点的独立证据。其固定合成 owner 和授权状态不能直接代替本项要求的真实已授权 AS cut。当前交付提供验收观察器，真实 IAM 与 AS cut 的准备需要操作者自己的隔离部署与故障触发方式。
- 使用单独的登录角色 `tianji_it_checkpoint_observer_<name>`。该角色不继承或加入其他角色，无创建、超级用户、复制或绕过行策略权限。除必要的数据库 CONNECT 和 schema USAGE，只需 `SELECT`：`agent_run`、`agent_checkpoint`、`agent_event`、`agent_session`、`agent_session_lease`、`agent_session_event`、`agent_run_command_receipt`、`agent_budget_reservation`。测试要求实际会话只读且在预期主库，拒绝上述表的 DML 权限。授权动作须由操作者独立完成。

## 精确执行与证据

从 `tianji-service` 根使用该仓既有 Maven 配置与 JDK 25。占位符均替换为该次隔离实例的私有输入。只在操作者准备完毕并明确授权运行后启用下列选择器。

```bash
mvn -pl stellar-enclosure-tianji-agent-server -am \
  -Dtest=AgentCheckpointResumeHttpPostgresTest -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtianji.postgres.checkpoint.protected-resume=true \
  -Dtianji.postgres.checkpoint.protected-resume.https-origin='<loopback-https-origin>' \
  -Dtianji.postgres.checkpoint.protected-resume.jdbc='<loopback-disposable-jdbc-url>' \
  -Dtianji.postgres.checkpoint.protected-resume.observer-role='<read-only-observer-role>' \
  -Dtianji.postgres.checkpoint.protected-resume.observer-password-file='<absolute-private-file>' \
  -Dtianji.postgres.checkpoint.protected-resume.read-token-file='<absolute-private-file>' \
  -Dtianji.postgres.checkpoint.protected-resume.resume-token-file='<absolute-private-file>' \
  -Dtianji.postgres.checkpoint.protected-resume.tenant='<tenant-id>' \
  -Dtianji.postgres.checkpoint.protected-resume.subject='<owner-subject>' \
  -Dtianji.postgres.checkpoint.protected-resume.issuer='<owner-issuer>' \
  -Dtianji.postgres.checkpoint.protected-resume.run-id='<prepared-run-uuid>' \
  -Dtianji.postgres.checkpoint.protected-resume.codec='<1-or-2>' test
```

- 检查实际报告为 **1 项执行，0 项跳过，0 项失败**。分别对 codec 1 与 2 运行。每次新建独立一次性切点。
- READ bearer 通过保护 GET 读取准确的同 owner run。对相同命令，未认证返回 401，仅 READ 返回 403，两次拒绝后持久证据一致。具备双权限的 USER 通过固定 POST 得到 200、`no-store` 和原始四字段 ACK。主库观测精确的原 RESUME 账本回执、同一 attempt、原 checkpoint 与原生事件前缀、同 codec 和原 invocation 延续、唯一 USER 输入、单次模型结算、最终完成。重放返回原 ACK，变更输入返回 409，主库证据保持一致。
- 由操作者记录该次真实 Shoubing 独立 purpose 签发、Jianshen 当前决策、两套原角色及进程退出的脱敏证据。HTTP/PG 观察器不会读取 IAM 审计或签发密文。准备与回执材料仅留在专用私有位置，报告只记录状态与摘要。
- 本切片只覆盖 S11 的首条输入已持久化、无模型或工具副作用的受保护 RESUME 窗口。工具成功前后、checkpoint 失败、Session 投影、定义变化等其他 S11 窗口分别验收。

## 本次有限验证

JDK 25 在独立 scratch 编译两类，复用既有 AS Surefire classpath。直接 JUnit Launcher 验证 2 项有限测试，真实 HTTP/PG operator 项按默认条件跳过。删掉精确 revision 校验的副本触发 `Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown`。本次真实 HTTP、IAM、PostgreSQL 和进程退出均待操作者执行。

所属离线 Maven reactor 复现同一个定向反证。原候选的 Witness 2 项、受保护 Controller 9 项和 Grant Handoff 8 项在定向及干净打包各通过一次，零失败、错误或跳过。实际测试字节码保留精确 opt-in，生产 AS JAR 无验收测试类，两端实际配置与权限清单语义一致，28 份既有迁移逐字节一致。
