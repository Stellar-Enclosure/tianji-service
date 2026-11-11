# T4-03 S11 原 START 已提交后成功响应丢失切点证据

Service baseline `ac8384069257a457089bcf3d13b688b79ea9091b`。[Spec](../spec/2026-11-06-08-27-s11-start-response-loss-cut.md) · [Plan](../plan/2026-11-06-08-28-s11-start-response-loss-cut.md)

## 操作者准备

采用已审核的 S07 私有 manifest 结构，使用独立文件，填写 `suite: S11_HISTORICAL_START_CUT` 和显式授权字段。保留 S07 两个真实租户、原 USER、原执行 SERVICE、START/Decision 原注册、原 HTTPS Process 地址与原资源。新套件只对 manifest 第一个租户产生一次原 START。不要配置通用故障代理。AS/PS 保持各自现有角色和主库，生产权限声明和注册保持原样。

使用与后续历史观察兼容的精确一次性库 `tianji_it_historical_as_<suffix>`、`tianji_it_historical_ps_<suffix>`。独立 observer 角色对应 `tianji_it_historical_as_obs_<suffix>`、`tianji_it_historical_ps_obs_<suffix>`，无继承和角色成员身份，能在实际主库使用只读事务。Agent observer 需要原 V13 和 agent_run 观察，Process observer 需要原 START 收据和 Flowable 原生历史。父进程沿用 S07 已有的 Agent 预算和 Process Review 观察。原生 Review 必须保持稳定，隔离其他活动和历史清理。

只在操作者准备完毕并授权运行后，从 `tianji-service` 根使用 JDK 25、既有 Maven 配置及独立私有文件。

```sh
mvn -pl stellar-enclosure-tianji-process-server -am -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=S11HistoricalStartCutIT \
  "-Dtianji.s11.historical-start-cut.manifest=$PRIVATE_S11_MANIFEST" test
```

要求恰好 1 项非跳过成功。在私有 outputDirectory 保留同次报告及 `s11-start-cut-<runId>.txt`，权限 0600。该文件依次包含四行，dispatch Run ID、intent UUID、Process instance ID 和原请求 SHA-256。只使用同时具有成功报告的 marker。源测试确认原 V13 UNKNOWN、无返回收据、预算 UNKNOWN、终态和派发窗已过，且 Process 原 SERVICE 的唯一 APPLIED 与稳定 Review 对应。真实响应到达装饰器后，丢失发生在 AS JDK HTTP 的调用方 future，未改变 PS 的业务事务。

测试完成后沿用原 Driver 生命周期结束子进程。保留两主库与原绑定材料。后续受保护观察单独由操作者配置或启动 AS/PS，并保留原 SERVICE 的独立 READ 和当前 USER 专用历史权限。见[后续历史观察](2026-11-06-s11-protected-historical-start-receipt.md)。

## 本次有限验证

两个测试模块的源码在独立 scratch 通过 JDK 25 编译。独立 JUnit 运行 2 项有限测试通过。只移除一次性 CAS 的副本触发预期的重复调用类型差异，预期 IllegalStateException，实际 IOException。真实 IAM、HTTPS、子进程、PostgreSQL 和操作者 I1 均待运行。

所属离线 Maven reactor 复现移除一次性 CAS 后的定向反证。AS 10 项及 PS 11 项在定向和干净打包中各通过一次，零失败、错误或跳过。实际测试字节码确认独立 manifest 和原 HTTP 客户端限定名，生产 JAR 无新增夹具，两端配置与权限清单语义核对通过，29 份基线迁移逐字节一致。真实切点需操作者执行。
