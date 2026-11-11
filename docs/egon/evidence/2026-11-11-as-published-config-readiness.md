# AS 已发布 CONFIG 本地就绪证据

Service 基线 `55c8c42df600cfd46c5f6a7a5eabfcf8299ba81d`，模型身份 API 的 Components 前置提交 `a9539edf1c420132fff04024a51ee7f6d4faff2a`，本步钉选 Components 提交 `c68730198f6345926c1a88e98b957781673310be` 的实际 JAR SHA-256 为 `c67a538fe22f862ba0f1392c6a28448b16ad6b386f8fd3141949d2bf33b6d447`，内建编译工厂源码 SHA-256 为 `d535829e40981b330d8457459a01a26b1d696769168ef12c868705676b089fbf`。 [Spec](../spec/2026-11-11-16-12-as-published-config-readiness.md) [Plan](../plan/2026-11-11-16-13-as-published-config-readiness.md)

AS 查询使用实际执行器的同一账本，核对已提交的精确发布摘要。使用普通执行共用的 CONFIG 解析、校验和编译。实际根模型、执行器模型表和已安装 JTokkit 核对同一对象，并核对路由、策略、币种及当前默认选项。结果只携带安全元数据。正例断言模型 call/stream 零调用，输出 JSON 省略配置及指令。

| Gate | Observed result |
| --- | --- |
| R1 | 移除候选摘要、编译后模型身份、估算器模型身份、内建工厂或内建校验器，各精确运行一项，均因未抛出预期的 AgentLedgerException 失败。服务仓库 Maven 复现编译后模型身份的反证。 |
| S0 finite | 隔离 JDK 25 编译并运行五个类，88 项通过。AgentPublishedDefinitionEvidenceTest 13，JTokkitChatModelBudgetEstimatorTest 13，AdkGovernedProcessLoopTest 37，AdkNativeAgentCompletionTest 17，AgentJsonCodecTest 8。 |
| S1 | 严格 Spec/Plan 和预期路径、文件摘要、原 31 份 SQL 核对。 |
| Owning reactor | 服务仓库源码构建与干净制品各通过 88 项，零失败、错误或跳过。实际 AS 的 AR 和 Components 嵌套 JAR 类字节匹配对应编译输出，javap 核对调用签名、模型实例比较与安全投影。两个制品的应用配置和权限清单内容、31 份 SQL 保持一致。 |

两级工厂和配置校验器必须使用精确内建类型。fixture 证明会正确委托的自定义工厂和校验器没有被调用，另一个独立的编译结果替换反证证明实际底层模型不同会被拒绝。受信宿主 `ChatModel#getOptions` 仅作本地元数据读取。本步验证未调用真实 provider、PostgreSQL 或 HTTP。真实部署权威、AS linked allowlist/issuer 和真实请求预算仍待独立验收。
