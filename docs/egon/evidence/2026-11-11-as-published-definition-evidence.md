# AS 已发布定义的所有者证据

本步为 T4-01 的发布兼容性校验准备 AS 内部读取能力。基线为 `fb89f4c6d96b1aeee325ddb59937c2a9ef366c4a`。

`AgentLedgerService#publishedDefinitionEvidence` 要求调用方已经对当前租户完成发布证据授权，并持有经过 issuer 绑定的身份。方法以该身份的租户、精确 key 和版本读取 AS 的 `agent_definition_version`。独立事务复用既有主库校验、数据库观察时刻和 2 秒 SQL 限制。数据库查询为只读，已有事务内调用会被拒绝，以免读到同一事务中尚未提交的定义。

读取后复用 `AgentJsonCodec#definitionHash` 重算原有发布内容摘要。因为内容摘要不含 key 和版本，方法单独检查 JSON 中的 key 与版本。返回值包含租户、定义版本、内容摘要、执行模式、输入和输出契约版本、已声明模型路由和策略引用、可选依赖是否存在、数据库观察时刻。输出省略定义配置、策略正文、Prompt、Skill、Tool 引用及正文。缺失记录返回当前租户范围内的资源不可用，源库故障或摘要不符返回脱敏的证据不可用。

## 有限验证

JDK 25 在隔离目录编译改动类和测试，使用缓存的 AR 测试依赖运行 JUnit。新增 6 项通过。加跑既有 `AgentJsonCodecTest`、`AgentRunCorrelationLookupTest`、`JTokkitChatModelBudgetEstimatorTest` 后共 34 项通过。用 Mockito JDBC fixture 核对精确 SQL 条件、只读事务指令、主库检查、拒绝路径及脱敏。使用精确方法选择器分别移除摘要、内嵌 key、内嵌版本校验，三次各运行一项，均因未抛出预期的 `AgentLedgerException` 失败。

服务仓库 Maven 复现摘要守卫的定向反证，恢复后有限回归与干净制品各通过 34 项，零失败、错误或跳过。AS 成品内嵌 AR 的实际账本及新证据 DTO 字节码，PS 成品未引入 AR。两个制品的应用配置和权限清单内容、31 份既有 SQL 均核对一致。

## 后续接入

模型路由及策略引用来自已提交的定义。独立的运行时就绪校验可从 `AdkAgentRunExecutor` 的实际 CONFIG 装配和 `JTokkitChatModelBudgetEstimator` 的精确绑定及当前选项校验提取公共逻辑，对已配置的估算器执行同一校验。自定义估算器需要等效的所有者证明。

完整的激活证据还需要绑定部署中的 AS/PS 归档、权限清单条目、原始定义及契约制品摘要、实际 Worker 路由，以及当前授权和签名撤销来源。该内部读取目前没有 HTTP、远程接入或授权登记。本步未运行实际 PostgreSQL、启动服务或修改配置、数据库、依赖。

2 秒限值配置在事务和 PostgreSQL SQL 语句上。连接获取、Java 摘要重算及事务清理仍待端到端时限实测。身份对象供已授权的内部调用者传入，调用者需要在调用前独立验证当前租户及精确发布版本权限。
