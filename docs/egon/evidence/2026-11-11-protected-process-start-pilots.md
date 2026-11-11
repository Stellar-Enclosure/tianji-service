# T4-01 受保护 Process START 试点源码证据

| 项目 | 结果 |
| --- | --- |
| Service 基线 | `99740bb7c817979e396e8a34fd1054d591ea4486` |
| 生产前置 | A5 `4313d3319ca228c2310d06dba9128dcb981d3f30`。A6 的本地 Worker 证据是后续宿主证明的可复算输入。 |
| 变更范围 | 四份 PS Java、两份既有 PS 测试、三份文档。Components 不涉及本步。 |
| 默认 | 试点列表为空，现有受保护 POST 使用原显式 START 和构造器。 |
| I1 实际运行 | 待独立宿主和操作者。 |

## 有限门

- JDK 25 编译候选。六类 owning 选择器为 `GovernedProcessStartServiceTest,ProcessApiConfigurationTest,ProcessControllerTest,ProcessStartReceiptControllerTest,ProcessRequestDigestCompatibilityTest,TianjiProcessAccessTest`。隔离 JUnit、服务仓库源码构建与干净制品均通过 53 项，零失败、错误和跳过。
- 确定反证仅让试点请求回落到普通 START。在 `protected_start_refuses_missing_worker_without_falling_back_to_explicit_start` 观察唯一失败，JUnit 指出预期 `AccessDeniedException` 未抛出。服务仓库定向 Maven 复现同一失败，恢复实现后用例通过。
- 使用有限夹具调用实际 service 的正常 `start` 入口验证原 digest、A5 事务、显式 release 失配、Worker 缺失、激活不存在、当前授权拒绝和精确 tenant/definition。旧普通 APPLIED 和已有 V13 的相同命令都无需重新取得当前 Worker，回放无持久写入。
- 试点 channel 改为 canary 后，历史 stable 的精确请求仍需取得 stable 的当前授权。改动 release、内容或锁后历史证据均拒绝。已有普通 APPLIED 无 V13 的精确重放继续需要当前授权。
- 有限 servlet 装配验证空配置默认、唯一显式 authority、替换 service 的实际配置和同一 authority 实例。绑定最多 256 项，拒绝同 tenant 下重复 definition 和 channel。
- Spec 和 Plan 结构与精确文档哈希由严格工具检查。源码接入本身无需 A4/A5 操作者夹具新增的测试文件。

PS 成品的六个相关类匹配编译输出。javap 核对原 Controller 调用、试点分流、回放与启动装配。两个可执行包的配置和权限清单内容、31 份现有 SQL 及精确九路径均核对通过。

## 独立运行边界

实际环境保持默认关闭。启用需要可信宿主提供可核对的 caller 和跨所有 Worker 获取者的当前 proof，并在所有接受原 POST 的实例一致地启用试点。历史 Worker 及其原 channel 权威仍需保留。真实 HTTP、IAM 和 PostgreSQL／Flowable 没有在本源码门启动或验证。
