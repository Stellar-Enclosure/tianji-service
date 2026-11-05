# T4-03 受保护的失联 checkpoint RESUME 入口

Service baseline `50f957a7cb3536d1aadce06403f98969d99c17f2`。[Spec](../spec/2026-11-06-06-48-protected-checkpoint-resume.md) · [Plan](../plan/2026-11-06-06-49-protected-checkpoint-resume.md)

默认关闭的固定 USER POST 通过专用 RESUME 权限和既有会话 READ 权限校验。请求体限定原 Run、命令和预期修订。显式部署绑定提供恢复连接池和独立签发源，经只读预览、同步签发和工作线程校验。响应返回原持久 ACK。等待上限 35 秒，超时不终止已接纳的任务。

| Gate | Observed result |
| --- | --- |
| R1 | Removing the explicit USER guard changed the expected 403 to 200 in one finite controller case. |
| R2 | Removing registration isolation caused the one expected missing IllegalStateException for reused ordinary START registration. |
| G1 | 32 finite tests passed across five suites. Zero failures, errors and skips. |
| P1 | Clean permission-producer repeated all 32. The actual AS archive adds exactly one protected USER POST, one independent permission and two label entries. Existing declarations are exact. Packaged YAML contains only the separate scope mapping and the default-off flag. Actual OAS matches source. The PS packaged config and semantic PaC match baseline. |
| S1 | Strict Spec and Plan, 13 exact paths, all 28 existing main SQL bytes and no PostgreSQL or IT report. |

AS OpenAPI SHA-256 `fa11ba3cf776b6dc98c683ce764860c8c186070cdc5ccf6da7b94b4744c2f5e9`. AS executable SHA-256 `c12bed7cf3c8357b513893505774bf7ae8ee2612dccfccac8aa2bfd6c633e260`.

实际角色池、显式签发绑定、Shoubing 独立 purpose 和 Jianshen 专用资源策略由部署配置提供。真实 IAM、PostgreSQL 及 S11 验收仍待运行。
