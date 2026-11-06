# S11 受保护的历史 START 对账验收

Service baseline `5052339f5d7cb3aa1b2510a28d849b7b6894456b`。[Spec](../spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md) · [Plan](../plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md)

## 操作者准备

- 提供已启动且隔离的真实 AS、PS 与独立 AgentDB、ProcessDB。两个 JVM HTTPS origin 都使用显式不同端口的 `https://127.0.0.1:<port>`，测试 JVM 信任证书的 IP SAN。JDBC URL 采用 `jdbc:postgresql://127.0.0.1:<port>/tianji_it_historical_as_<suffix>` 与 `jdbc:postgresql://127.0.0.1:<port>/tianji_it_historical_ps_<suffix>`，不包含凭据或选项。
- 使用真实隔离租户、原固定 START Tool descriptor/backend 和原执行 SERVICE。AS 需要启用 `stellar.tianji.agent.api.enabled`、`stellar.tianji.agent.storage.enabled`、`stellar.tianji.agent.recovery.historical-start-receipt.enabled`，显式导入 `TianjiProtectedHistoricalStartReceiptConfiguration` 并提供 `tianjiProtectedHistoricalStartReceiptBindings`。独立 READ 注册按原 backend 摘要绑定，保留原 SERVICE 的 tenant、issuer 和 client/subject。PS 启用 `stellar.tianji.process.api.enabled` 和 `stellar.tianji.process.api.start-receipt-read-enabled`。使用经批准的真实 IAM 配置。
- 为测试提供四份独立真实 bearer。原 USER READ 用于读取自己 Run，对账由同一 USER 的专用 `tianji:agent:tool-receipt:reconcile` 与 `TIANJI_AGENT_TOOL_RECEIPT_RECONCILE` 票据执行。不同 USER 具有同一租户的专用对账权限用于 owner 反例。原 SERVICE 的独立 `tianji:process:read` 与 `TIANJI_PROCESS_READ` bearer 只供测试独立读取原 START 收据。AS 在真正对账时自行使用部署配置的原 SERVICE READ 注册。
- 从真实工具普通调用入口准备独立正向切点。使用一次性业务键和有原生历史的 BPMN，完成原 START 的数据库提交后，阻断准确请求的原成功响应，保留真实 PS APPLIED START 与恰好一个相同 tenant/business key 的原生实例。允许原 AS Run 走到终态，或其租约过期后由既有 reconciler 进入 SUSPENDED/LEASE_EXPIRED。等待准确 dispatch window 到期，保留原 V13 action 和 invocation 的 UNKNOWN 或 DISPATCHING 状态、原预算和完整 owner/原 SERVICE 证据。一次运行只选择一个切点，两个 prior state 如都需验证则各准备新切点。
- 仓库现有 `ExpenseAcceptanceFaultRelay` 的丢失响应窗口覆盖 Worker Complete 和结果 202。这个 START 准备需要操作者另行配置授权的真实隔离故障触发，使 PS 已提交但原 AS 未收到成功响应。既有 AR PostgreSQL 历史收据测试使用受控 Process 投影，其数据不能作为这个 E 切点的证据。严禁用手工插入或改写业务事实代替真实原动作。
- AS、PS 分别使用 `tianji_it_historical_as_obs_<suffix>` 和 `tianji_it_historical_ps_obs_<suffix>` 两个独立登录角色。实际连接必须只读，具备必要 CONNECT、USAGE 和精确 SELECT，无这些表的写权限或高权限角色。AS 读取 `agent_tool_action_intent`、`agent_tool_invocation`、`agent_run`、`agent_budget_reservation`、`agent_run_command_receipt`。PS 读取 `tianji_process_command_receipt` 和 `act_hi_procinst`。将已提交的原生流程停在无触发条件的稳定等待点，或使用已完成实例。验收期间不推进同一实例，也不运行不相关的定时器、异步任务或历史维护，确保两次观察之间完整原生历史行稳定。
- 所有 bearer 与 observer 密码使用操作者本人所有、绝对路径、常规文件和 `0600`。内容允许一个末尾 LF。报告仅记录脱敏结果。

## 独立 I1 命令

从 `tianji-service` 根使用仓库 Maven 配置及 JDK 25。将变量设为实际隔离环境的值，凭据变量只包含绝对路径。授权后仅执行此 selector。

```sh
history_prefix=tianji.postgres.historical-start.protected
mvn -pl stellar-enclosure-tianji-agent-server -am \
  -Dtest=AgentHistoricalStartReceiptHttpPostgresTest -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  "-D$history_prefix=true" \
  "-D$history_prefix.agent-origin=$AS_LOOPBACK_HTTPS" "-D$history_prefix.process-origin=$PS_LOOPBACK_HTTPS" \
  "-D$history_prefix.as-jdbc=$AS_DISPOSABLE_JDBC" "-D$history_prefix.ps-jdbc=$PS_DISPOSABLE_JDBC" \
  "-D$history_prefix.as-observer-role=$AS_OBSERVER_ROLE" "-D$history_prefix.ps-observer-role=$PS_OBSERVER_ROLE" \
  "-D$history_prefix.as-observer-password-file=$AS_OBSERVER_PASSWORD_FILE" \
  "-D$history_prefix.ps-observer-password-file=$PS_OBSERVER_PASSWORD_FILE" \
  "-D$history_prefix.tenant=$OWNER_TENANT" "-D$history_prefix.owner-subject=$OWNER_SUBJECT" \
  "-D$history_prefix.owner-issuer=$OWNER_ISSUER" "-D$history_prefix.service-subject=$SERVICE_SUBJECT" \
  "-D$history_prefix.service-issuer=$SERVICE_ISSUER" "-D$history_prefix.run-id=$DISPATCH_RUN_ID" \
  "-D$history_prefix.intent-id=$ORIGINAL_INTENT_ID" "-D$history_prefix.read-token-file=$READ_USER_BEARER_FILE" \
  "-D$history_prefix.reconcile-token-file=$RECONCILE_USER_BEARER_FILE" \
  "-D$history_prefix.foreign-token-file=$FOREIGN_RECONCILE_BEARER_FILE" \
  "-D$history_prefix.service-read-token-file=$ORIGINAL_SERVICE_READ_BEARER_FILE" test
```

要求实际 1 项执行、0 跳过、0 失败。证人读取真实 PS 精确 START，原 SERVICE 的公开 GET 返回相同收据。仅 READ 的原 USER 对账返回 403，异 owner 的专用 USER 返回 404，两库证据相同。正向受保护 POST 追加一条 V2/V5 历史观察，保持原 V13、origin/dispatch Run、预算和真实 PS 单实例，重放得到相同收据。分别按原 DB 时钟比对 recordedAt 和 observedAt。

操作者另外保留经过脱敏的真实故障触发、当前 USER 两次授权和 AS 原 SERVICE READ 的实际 IAM/PS 审计证据。直接 SERVICE GET 证明相同 owner 的独立读取，不能代替 AS 出站授权审计。真实缺失收据的反向 E 切点保持独立。有限 AR 测试与受控来源 PostgreSQL 夹具提供现有反向证据。


## 本次有限验证

JDK 25 编译两类，独立 JUnit 1 项有限通过，1 项 operator 默认跳过。移除原 PS recordedAt 比较的副本触发预期的 `IllegalArgumentException` 缺失断言。实际 AS、PS、IAM、PostgreSQL、故障注入待授权的操作者运行。

所属离线 Maven reactor 复现跨库记录时间反证。原候选的 Witness 1 项、受保护 Controller 9 项和固定 Process START Receipt Reader 10 项，在定向与干净打包各通过一次，零失败、错误或跳过。实际测试字节码保留独立 opt-in 与两个 SDK 调用，生产 AS JAR 无验收类。两端成品配置与权限清单语义一致，基线 29 份 SQL 逐字节一致。真实 I1 待操作者准备并运行。
