# T4-02 Java owner 关联与结果读取 SDK

Service baseline `a0b7884ef1a8574f219101d4b66f0f8c36a0c52a`。[Spec](../spec/2026-11-06-07-04-java-owner-correlation-sdk.md) · [Plan](../plan/2026-11-06-07-05-java-owner-correlation-sdk.md)

Java 客户端为现有三条保护 GET 提供类型化读取。AS 按原 owner 精确 correlation 和 UTC 窗口查询，PS 读取 START 回执关联和单实例 Agent outcome。客户端沿用既有 HTTP 层，校验体积、响应字段、游标、排序、时间窗和适用回显。Process 闭合错误沿用服务端合同，畸形中间层响应仍保留 HTTP 状态并以无效响应处理。

| Gate | Observed result |
| --- | --- |
| R1 | Removing the Process correlation echo guard produced one expected missing ProcessClientException in the owning reactor. |
| G1 | Ten new SDK tests and six existing START receipt tests passed, zero failures, errors or skips. |
| P1 | Clean permission-producer repeated all 16. Actual standalone contract and client JAR classes match compiled classes. AS/PS packaged configuration and semantic PaC match baseline. No operator integration report. |
| S1 | Strict Spec and Plan, nine exact paths and all 28 existing main SQL bytes verified. |

Contract JAR SHA-256 `a983798f6d2431664afc7d51b9e346adce57718216cc0cacefe7a8d02a48405e`. Client JAR SHA-256 `aa16917c17272b3d1633ba85e0857fde9c8bbc33634267ce41a4b14a2aafe701`.

实际 S12 保护读取需使用各自的真实 owner READ 凭据，HTTPS、IAM、PostgreSQL 和 Collector 待隔离验收。
