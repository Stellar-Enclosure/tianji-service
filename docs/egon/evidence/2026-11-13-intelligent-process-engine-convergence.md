# Tianji 智能流程引擎收敛验收

**验收环境结论 `GO`。** 源码、真实 PostgreSQL、独立部署夹具和本机浏览器门禁通过。最终候选以以下源提交和后文制品摘要定位。

| Repository | Verified source revision |
| --- | --- |
| tianji-service | `2d6c8c5b001b7c15cdc7020b844be566ea7436d0` |
| Stellar-Enclosure-Components | `c88fd8912c9f1a80caec3bf6baa2bae0df97870b` |
| tianji-web | `7a2e7eb45a799b6506f05bb00e47ec41d3ed27a2` |
| tianquan-jianshen-service | `671e20b6702fb69e5f84d206c1199923b773c09f` |

- 有效 Spec 为 `2026-11-11-18-55-intelligent-process-engine-convergence.md`，SHA-256 `37bd062fad65e6dfd38dab82997095ccf408527afda9db7e936d6d5a7c1af615`。执行 Plan 为 `2026-11-13-14-10-intelligent-process-engine-convergence.md`，SHA-256 `6156e9937bc998c836d43aa9e9249c5b23d0d94795c39a92892144e76fe5492a`。
- 使用 JDK 25.0.3、Maven 3.9.14、PostgreSQL 18.3、pgvector 0.8.1、Flowable 8.0.0、Node 24.14.1、npm 11.19.1 和 Chrome 157.0.8103.78。
- [持久验收脚本](../../../scripts/acceptance/verify-automation-convergence.sh) 的 SHA-256 为 `cb26e10ffa026df92f342838e40f05b9dd92e32fdf9d89d0b80b4409340c561f`，与 Step 46 配套脚本逐字节相同。最终文档提交承接上表实际测试的源提交。

验收使用独立的本机 PostgreSQL 和临时低权限角色。Process、Agent 和 Worker 在独立 JVM 中运行，通过真实 HTTPS、Flowable、Worker 租约、Outbox/Inbox 和持久回执观察提交及恢复。IAM、模型和外部业务目标为确定性的测试提供者。浏览器验收使用真实 Chromium 和本地网关夹具。生产范围包括运营方另行执行的制品登记、实际角色授权、在途盘点、部署和切换。

## 复跑入口

从 Service 根目录使用 JDK 25。先准备三个仅监听 loopback 的独立空库，分别配置 schema owner、应用运行角色及只读观察角色。Process 安装原生 Flowable 8 BPMN、CMMN、DMN 和 PS V1–V15，Agent 安装 pgvector 0.8.1、AR V1–V18，Worker 安装 W V1–V3。Process 和 Agent 使用各自的原 Outbox V1 历史。使用真实 Flyway 迁移并核对历史和 schema，运行角色不负责 DDL。准备两个租户的测试身份及签名、隔离 HTTPS 和目标服务设置。以 [独立运行验收测试](../../../stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/AutomationIndependentServicesIT.java) 及同包 manifest、harness 和测试入口为私有输入契约。

```sh
sh -n scripts/acceptance/verify-automation-convergence.sh
sh scripts/acceptance/verify-automation-convergence.sh prepare "$TIANJI_AUTOMATION_FINAL_PRIVATE_WORKER_CLASSPATH"
sh scripts/acceptance/verify-automation-convergence.sh run "$TIANJI_AUTOMATION_FINAL_PRIVATE_MANIFEST"
```

两个私有路径均须为绝对 canonical 路径，父目录 mode 0700。prepare 输出路径须尚未存在，run manifest 必须为同一操作者所有的 mode 0600 普通文件。prepare 从当前源码生成制品和 Worker 测试 classpath，核对三个测试类的 3/1/1 计数与制品内容。待实际迁移、私有授权配置和三个库所需连接通过预检，再用 run 执行七项独立场景。脚本从报告创建时间、测试数、失败、错误、跳过和制品清单执行门禁。完成后回收本轮 PID、库、角色以及临时授权材料，仅保留脱敏摘要。

## 实测结果

- **Java 主矩阵。** 59 个命令组，1,586 次实际测试执行。逐类和逐方法核对本轮 Surefire XML，失败、错误、跳过均为 0。同一类在不同 Step 或不同迁移基线复跑会分别计数。53 组从 `99d13fb3b898` 的首次结果采证。核对到 `4254a191577f` 的 Git 差异只有 `TianjiServiceExecutionGrantAuthorizerTest.java` 与 `ToolActionIntentStoreTest.java`，两类测试所在组和其他首轮未通过组均用该源提交重新运行。
- **首轮纠正。** Step 21 的授权器测试开始读取实际 linked evidence，验证授权前后完整链接和信任变化。Step 22 的工具回执测试使用生产 codec 生成固定快照和摘要，并在旧 SQL 断言规范化空白。两条独立测试提交是 `cd9f0be14c70`、`4254a191577f`。首轮另有四组旧测试明确传空密码。改用单独的 loopback 库，对所需低权限 runtime 身份与库的精确组合开放连接。owner 与其他库使用 SCRAM，核验跨库拒绝。
- **Java 补充。** Step 1 的 `./mvnw -pl stellar-enclosure-tianji-process-server -Ppermission-producer help:effective-pom -Doutput="$PRIVATE_EFFECTIVE_POM"` 证实配置处理器、Lombok、Lombok/MapStruct binding 和 MapStruct 仍参与构建，permission producer 同时包含精确 Jianshen 处理器。Effective POM 摘要 `1e2a42950b30f1139b41356b638591862c54113fecfbe22fba4ab3ee5d1bea93`。从 Service 根用源码 reactor 启动器按 `-Dtest=TianjiAgentResultDeliveryConfigurationTest,ProcessAgentBridgeConfigurationTest,ProcessAgentBridgePollerTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false` 另实跑 19 项。摘要 `cf04c6748749a2628fa7c30a09c922b34ceb8ced974a9fdc1d39e7278b5e7753`。
- **Web。** Web 根依次执行 `npm ci`、`npm run typecheck`、`npm run lint`、`npm test`，35 个文件的 417 项测试通过。使用锁定 `PAC_SDK_DIR`、`PAC_TOOLCHAIN_LOCK`、`PAC_CONTRACT_ARTIFACT` 和 `VITE_BASE_PATH=/console/`、`VITE_PERMISSION_NAMESPACE=default`、`VITE_PERMISSION_ENVIRONMENT=local` 执行 `npm run build`。实际 clean 浏览器候选和 PAC 摘要见制品节。
- **独立轮询纠正。** Step 52 首次独立验收中收到一次 API-008 回执查询的 503。OpenAPI 对当前依赖或授权证据暂不可核验规定了这一状态。只读证据 SHA-256 `e5672d35f6f29c8fccea2f5104b3334873d71070ecf7c0eb4c82f66060fb06b8` 证实 SIGNAL 原记录已 APPLIED、重试 0、订阅消费 1、原生流程已结案。观察到的闭合 503 未保留 IAM 子步骤的原始原因。Step 46 在 `2d6c8c5b001b` 让同一 API-008 GET 轮询复用既有单一有界等待，只对精确临时错误继续同键查询。先实际复跑该业务路径 1 项，随后用新提交重建并复跑七项。Git 校验 `4254a191577f` 至最终候选只涉及这一独立验收测试，Java 矩阵的选择器范围不含该测试。
- **本机 IAM 夹具。** 下一轮业务事件通过，草稿读回出现同一闭合 503。只读证据 SHA-256 `4fa518be947c5d876c9cdb32548e998d59df12ba17f2776bb06ed6e98e5100b6` 留存草稿修订 2 和一个 SAVE 回执，当前生效仍指向 r1，后续发布/切换数为 0。复位已校验的物理测试库，调整本机 IAM 夹具为每个连接在线程内完成 TLS 握手、HTTP/1.1 和仅传输分类的诊断，以同一已锁制品与角色再运行。
- **最终数据库读回。** 独立观察者封存的 SHA-256 为 `7731dcd34ed80a4e84adfc720b03358893c3cb1d36d0b1e947796e5d16feef78`。START APPLIED、SIGNAL APPLIED、原生订阅消费和预期 EVENT_WAIT 输出各 1。未完成/完成 SAVE 各 1，固定发布 2，生效历史 3，最后按追加修订回切 r1。运行子进程退出后回收三套临时数据库，验证旧监听端口均拒绝连接。
- **独立脚本。** 当前候选实际 prepare 执行 3/1/1 共 5 项，run 执行 7 项，失败、错误和跳过均为 0。三个角色观察到 9 个不同 JVM PID，覆盖启动的三个进程及六次故障后替代。业务表单和附件在业务库读取，表格中的 Effects / Requests 统计 Worker 外部业务执行计数。运行后关闭临时服务、数据库和私有 fixture。

实际验收报告于 2026-11-15T10:36:58.389140Z 封存。原报告 SHA-256 `f8d091f5cd59924be5de61db042f11db76aa55f47f9280e21f4a9517063765dd`，脱敏报告 SHA-256 `c15bae533b8b5247a9eea5e077040096f1abe4b73e333140550b7c54ddce3794`。

Worker 使用锁定的测试类与依赖 classpath，classpath 摘要 `d34c0c72c7bbdc3afea382bc443a8b101480d147d2205f59251f29b8a8be9953`。产品 Worker JAR 摘要单列于制品节。

| Role | Generation | PID | Candidate SHA-256 | Observed at |
| --- | ---: | ---: | --- | --- |
| PROCESS | 1 | 52724 | `4a4f28593e41597d692d4bcaba43efda61ef84f8b035a27ec022e01c57c1bad7` | 2026-11-15T10:33:06.794Z |
| AGENT | 1 | 52745 | `51c1e502897870cd360d9a09aeea43fa4fd4598f2e99da2f7aeb37432db3257f` | 2026-11-15T10:33:14.734Z |
| WORKER | 1 | 52766 | `8d9d82d7893c154275dafbcf0b64aff6c03d8fd5827c73870221a859a9a8b6ec` | 2026-11-15T10:33:22.076Z |
| PROCESS | 2 | 54766 | `4a4f28593e41597d692d4bcaba43efda61ef84f8b035a27ec022e01c57c1bad7` | 2026-11-15T10:35:19.970Z |
| PROCESS | 3 | 54852 | `4a4f28593e41597d692d4bcaba43efda61ef84f8b035a27ec022e01c57c1bad7` | 2026-11-15T10:35:32.609Z |
| AGENT | 2 | 54962 | `51c1e502897870cd360d9a09aeea43fa4fd4598f2e99da2f7aeb37432db3257f` | 2026-11-15T10:35:45.680Z |
| AGENT | 3 | 55071 | `51c1e502897870cd360d9a09aeea43fa4fd4598f2e99da2f7aeb37432db3257f` | 2026-11-15T10:35:58.407Z |
| PROCESS | 4 | 55310 | `4a4f28593e41597d692d4bcaba43efda61ef84f8b035a27ec022e01c57c1bad7` | 2026-11-15T10:36:16.042Z |
| WORKER | 2 | 55669 | `8d9d82d7893c154275dafbcf0b64aff6c03d8fd5827c73870221a859a9a8b6ec` | 2026-11-15T10:36:42.877Z |

| Durable cut | Observed state | Stable reference SHA-256 | Observed at |
| --- | --- | --- | --- |
| PS_BEFORE_DISPATCH | PENDING | `591a24b643b53716d7d9c5d249a79e3d0622fa1191bb4142423982e5e6ae90b3` | 2026-11-15T10:35:19.734730Z |
| PS_AFTER_LINKED_ACCEPT_BEFORE_ACK | PENDING | `2d6a2986c9319757e2d40f2872c8bf868d17de9115d4383cfc01cdd1bbd9cbe8` | 2026-11-15T10:35:32.441725Z |
| AS_AFTER_RUN_PERSISTED_BEFORE_EXECUTION | QUEUED | `1e4edce996245ec85cda9adf242d096099874c35b1666446f061ea40d5fcace7` | 2026-11-15T10:35:45.422840Z |
| AS_AFTER_TERMINAL_BEFORE_DELIVERY | COMPLETED | `db984e018c7269b17ee55f512066d531a408fea3c547ca2532afa454317d986a` | 2026-11-15T10:35:58.181945Z |
| PS_AFTER_INBOX_COMMIT_BEFORE_APPLY | PENDING | `55a0cbcf08f45eb653dd88a0f4d154339aa00f3f47307927e25406f82d048ede` | 2026-11-15T10:36:15.837947Z |
| WORKER_AFTER_EXTERNAL_APPLIED_BEFORE_REPORT | STARTED | `1cbec5606ebe581a07fcfaa629cffaeff7979e2e8e36d5d6c7234e020d1f72d0` | 2026-11-15T10:36:27.874626Z |

| Scenario | Tenant alias | Release | Activation revision | Reported receipt SHA-256 | Effects / Requests |
| --- | --- | --- | ---: | --- | ---: |
| human_only | tenant-1 | fx-human-r1 | 1 | `653a4f8cd5f7043d4724cd7efda4f31bc75c13be511002bf6b6312ac71197673` | 0 / 0 |
| human_only | tenant-2 | fx-human-r1 | 1 | `ae96ad43fcafe68ec37cf61c0be037d4d93b905e6d8b2094197bef4efc53336a` | 0 / 0 |
| business_event_automation | tenant-1 | fx-business-r1 | 1 | `e27425d42be2942ee8d3ab7a3d96f618d6bafc1a59c0aee6367b09f5241e4746` | 2 / 2 |
| mixed_agent_routing | tenant-1 | fx-mixed-r1 | 1 | `67eda2d4e4b482ffdd284ac35b0fb707b28ee43c15c7c8256336d891d9ee24b6` | 0 / 0 |
| mixed_agent_routing | tenant-1 | fx-mixed-r1 | 1 | `bd4a2961bf396e614624180359733073fdf006982065e7edc6e8100898a97184` | 0 / 0 |
| mixed_agent_routing | tenant-1 | fx-mixed-r1 | 1 | `3f86462a564e4cea820005db7ccd86575fbfa26d1019cb4e2ddda1781fd9fc5a` | 0 / 0 |
| pure_agent_control_flow | tenant-1 | fx-chain-r1 | 1 | `c09dffc7a6f7bfab082f8b30619d09e00d0a36c6385ad86e524b2eeaba056b70` | 0 / 0 |
| pure_agent_control_flow | tenant-1 | fx-control-r1 | 1 | `532b7fdc514215394299ffa8c83dd00d4f8030bece4ce02cd588e55338c6194e` | 0 / 0 |
| published_and_active_versions_are_independent | tenant-1 | fx-version-r1 | 1 | `0b475b44ce9169bed454eba45f0cef90489f1393c920f89c657b895b0a8b80e7` | 0 / 0 |
| published_and_active_versions_are_independent | tenant-1 | fx-version-r2 | 2 | `856c2f25e09c2c0d021886a725de784fd47a0fff2f048f8e54585fdba64cb249` | 0 / 0 |
| published_and_active_versions_are_independent | tenant-1 | fx-version-r1 | 3 | `ea94423d6a1a9e6da7f4e0fc28e83f0ad494ad4eef95e79715d4058165ee446e` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-recover-r1 | 1 | `5bc4b96052f57ff05275983709c34dddb3f90d9dfb02acb1189530b8bb1c1d23` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-recover-r1 | 1 | `6c7e7b78cba1bf299a56852f83ed256eeba472485c811b552d4d2cded2f26b8f` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-recover-r1 | 1 | `ff7b0d53f1f75006c9c3f02ea3ab2a0ad01472633ab0323990b1f3a6edd19032` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-recover-r1 | 1 | `25230ce1997fe2ae8ede8311976d6cacf4b7da5b8dbb14067917a228d6336f22` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-recover-r1 | 1 | `55a0cbcf08f45eb653dd88a0f4d154339aa00f3f47307927e25406f82d048ede` | 0 / 0 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-external-r1 | 1 | `091ab730ad9e62fdf1620b7e9172a26072de6252dd70c03841cbe503612da862` | 1 / 1 |
| each_owner_recovers_at_persisted_cuts | tenant-1 | fx-external-r1 | 1 | `b879a97b06436df8936eab713f263b129fbed58834bd126f2f72a038faee3e65` | 1 / 2 |
| foreign_owner_and_late_results_cannot_apply | tenant-1 | fx-recover-r1 | 1 | `8c0ec02c802f6de64f29bda9445c95f73888b99719964f924d8dabd2c7e1ab59` | 0 / 0 |

TEST-007 的参与者使用真实 Flowable 命令和独立 JDBC 会话。每轮通过 PostgreSQL 阻塞链观察到另外两条会话在同一根锁后，随后核对一个终态、正确原回执、零替代作业及拒绝晚结果。微秒边界从实际 V14 的 `timestamptz(6)` 截止行取值，在测试时钟上分别重放截止前 1 µs 和等值，保留原写入与定时器的真实事务。前一微秒提交结果，等值拒绝新结果且原生硬定时器在等值结案。

TEST-017 的数据库附件读回同一个命令决定和一个租约，V14 原错误码为完整 128 字符，V4 使用 `V2_EFFECT_UNKNOWN`。只改末字符得到原幂等冲突。有效权威证明返回前另一个事务完成同根取消，结果为一个已取消的原 attempt、一个取消回执、零恢复回执、零新效果代次、零原生等待订阅。原 UNKNOWN 回执仍可按原请求重放。

## 合同、历史和制品

- Spec 严格校验通过。Plan 原命令将四处合法 Maven 注解处理器 XML 的 `<path><groupId>` 归为一个占位符诊断。仅在内存校验输入中将这四处开标签替换为 `<maven-path>` 后，严格结构、链接、文件归属和追溯检查通过。执行用 Plan 与 POM 保持实际 Maven 语法，另以 effective POM 复验合并行为。
- 原有 35 个生产 SQL 与 Git 基线逐字节相同。PS V14/V15、Worker V3、AR V17/V18 和 AF V1 的原文件、校验清单与打包资源相同。PS/AS/Worker 制品中验收测试类数量为 0。
- API-001 到 API-025 的 method、path、资源和主体与有效合同相符，PS/client 的 v2 OpenAPI 字节一致。两个可执行 JAR 的 OAS、权限产物和内置同一份 Jianshen 均校验。Jianshen 的三处 SERVICE scope 任选由精确资源配置启用并回归。
- 最终 Web 工作树干净，PAC 工具链模式为 `LOCAL_UNCOMMITTED`，Web manifest 和工件经过 canonical hash 校验。实际移除数量为 16 个资源、15 个调用引用、8 个权限码、16 个标签。剩余 33 个 CALLS 均指向产品的 USER-capable producer。Chrome 复验旧 Task/Memory 三条书签零认证/API 请求和标识泄露，Process 草稿返回阻塞及身份隔离、390 px 下键盘编辑、历史 Agent Run 列表与直启退出。

| Verified artifact | SHA-256 |
| --- | --- |
| Process Server exec JAR | `4a4f28593e41597d692d4bcaba43efda61ef84f8b035a27ec022e01c57c1bad7` |
| Agent Server exec JAR | `51c1e502897870cd360d9a09aeea43fa4fd4598f2e99da2f7aeb37432db3257f` |
| Worker starter JAR | `b16e3f46b2be6fa880132fe24370959906be4021682bfb4aebe60918bb176d0a` |
| PS/AS 共用的 Jianshen starter | `53e78aa4f5c81b32398ed5c3c82440eb5b4795a9e402f1ff516b097fdec66e10` |
| Web PAC manifest | `b05e154243d5784f6a71fe4ad494053f3fd4f07f6b3ceee1ecb512e7ea75e7f0` |
| Web artifact | `1e8485f254852c229ec27060503229c467739526b93a6200529aae31022e6cd0` |
| Web dist/permission-build.json | `a85dc7ef03dd0f3c0fce351a11565c0e1f1862347e45f7380807706d6bae2c38` |

| Migration | Source and packaged manifest SHA-256 |
| --- | --- |
| PS V14 | `8347e8909e893f7b83ea4adf5c9bcfd213ed5044e2419dd0d250da884fe9f4b7` |
| PS V15 | `b57d04a92bfe086810addf724ca889c943b383225c211be186de086cacb960bc` |
| Worker V3 | `919f17a54f5eb1fcbfa8b39f092fb61bad53b4f40662a5c40376e9a2c9775172` |
| AR V17 | `fd049bac03c6c9ead463b457d15bc4670a6609dfe1dff648ca64300baf3c75d9` |
| AR V18 | `40505bad4a8255293e22831d776dbe18049a6cdbf3b3286413a20dae949506c7` |
| AF V1 | `7aa621490c953b38a6231724e85d8fad4433e493d99d1bda37c8b749a6bc8654` |

独立运行前，以真实 Flyway 及原生 Flowable 安装三库。PS 记录 15 条应用迁移、AS 18 条、Worker 3 条。PS 和 AS 各有独立 Outbox V1，Flowable 具有四个 8.0.0.0 marker。AS 在 public 使用维度为 2 的测试 pgvector。每个库有精确权限和只读 observer，六次 runtime 跨库连接均被拒绝。

| E2E store | Observer migration history SHA-256 |
| --- | --- |
| PROCESS | `44524d3f8215fb7f4bb368e60be36cd175ec8a433b6d7b93ed5c9c1164c4af73` |
| AGENT | `575554b31e7911bdb742ac73b1a3fc8b46db5a95340a511a4bc4df3dcc61ab04` |
| WORKER | `312f222ba08e33a8e895cf99be6fc0390e9c08d2a39a96dc802923c8606801f0` |

### Java verification groups (59 commands, 1586 test executions)

Each row has zero failures, zero errors and zero skips. Counts include repeated regression selections across Steps.
Service, Components and Jianshen commands run at their repository roots. Each database flag used the isolated fixture named by its placeholder. Each group began with the required empty catalog or catalogs and its own fresh report window.

| Group | Repository | Executed command with private inputs omitted | Tests | UTC start | Service source | Canonical fresh counts SHA-256 |
| --- | --- | --- | ---: | --- | --- | --- |
| step01-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationBuildContractTest,ClientDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 5 | 2026-11-15T09:20:40Z | `99d13fb3b898` | `5b91cd1abae43ee4263f0e045b6a8797421ac97772f781b10587eb79909d6c57` |
| step02-01 | Stellar-Enclosure-Components | `./mvnw` `-pl` `stellar-enclosure-component-agent-flow-starter` `-am` `-Dtest=AgentExecutorConformanceTest,AgentFlowComponentContractTest,AgentFlowAdapterRegressionTest,AgentFlowPropertiesBindingTest,AgentFlowAutoConfigurationTest,ComponentContractGovernanceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `test` | 74 | 2026-11-15T09:21:28Z | `99d13fb3b898` | `a236e469895fb9a0486d7fda0f2d8a8c76f7acdb9d87b0f8fae9d366c184786e` |
| step03-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationTypedContractTest,OrchestrationContractTest,AgentTerminalEventTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 24 | 2026-11-15T09:21:46Z | `99d13fb3b898` | `a6e1b03b5d2affdcb3e27e2b4ffa3008b2c7a28734afeb83bdf25ac58235f191` |
| step04-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ToolExecutionStorageVerifierTest,ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolResultAccessStoreTest,AgentCheckpointStorageVerifierTest,TianjiToolAuthorityConfigurationTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 113 | 2026-11-15T10:03:49Z | `4254a191577f` | `99a1566a3a4f1aa35fa05a2cbcc2240e46a0ba9561842414cff7f595f71213f1` |
| step05-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationDraftContractTest,AutomationTypedContractTest,OrchestrationContractTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 31 | 2026-11-15T09:23:11Z | `99d13fb3b898` | `f389a6de8968e3fa55716baba04282b072ab4eaa5713deb8462534ab239bb2a0` |
| step06-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationExecutionContractTest,ProcessControlContractTest,HumanTaskContractTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 36 | 2026-11-15T09:23:39Z | `99d13fb3b898` | `57b3cd78958139879fd4005514e998584bae59af68722912630f8c10d3134ca8` |
| step07-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationWorkerContractTest,AgentRuntimeV2ContractTest,AgentTerminalEventTest,OrchestrationContractTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 27 | 2026-11-15T09:24:10Z | `99d13fb3b898` | `ba2efe1fb54568b9d6da440524efadd575787c7c92d1c13f9e3dc768db693558` |
| step08-01 | Stellar-Enclosure-Components | `./mvnw` `-pl` `stellar-enclosure-component-agent-flow-starter` `-am` `-Dtest=AgenticPlanTest,AdkAgentExecutorAdapterTest,McpToolAdapterTest,AgentFlowAdapterRegressionTest,AgentFlowComponentContractTest,ComponentContractGovernanceTest,AgentExecutorConformanceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `test` | 84 | 2026-11-15T09:24:35Z | `99d13fb3b898` | `cf3b98ed1d11be98c1017fcd2dd92edfbfd034669ba39a0aeb3b94f0e017c8e6` |
| step09-01 | Stellar-Enclosure-Components | `./mvnw` `-pl` `stellar-enclosure-component-agent-flow-starter` `-am` `-Dtest=MemoryStoreConformanceTest,AgentExecutorConformanceTest,ComponentContractGovernanceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `test` | 41 | 2026-11-15T09:24:57Z | `99d13fb3b898` | `edf7f4c6a8c1b2414194c7bf90a87a983af143ee330134bd720c8f598f18847b` |
| step10-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationOpenApiContractTest,AgentRuntimeOpenApiContractTest,AgentResultInboxOpenApiContractTest,ProcessControlOpenApiContractTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 23 | 2026-11-15T09:25:04Z | `99d13fb3b898` | `ce6ca4c052ae679c52ea60b237c1b947dd8c9654172012e7cd8596eab7a12c5c` |
| step11-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=RuntimeCompatibilityIT,RuntimeSafetyReceiptIT,ToolExecutionJournalPostgresTest#opt_in_tool_journal_contract_and_atomicity,AgentCheckpointStorageVerifierTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.runtime.safety.postgres.compatibility.url=${TIANJI_AR_COMPAT_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.receipt.url=${TIANJI_AR_RECEIPT_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` `-Dtianji.tool.journal.postgres.test.url=${TIANJI_TOOL_TEST_JDBC_URL:?required}` `-Dtianji.tool.journal.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 21 | 2026-11-15T09:26:02Z | `99d13fb3b898` | `6231b51aa7e38b017e10d336ac0b50e9b6e42ba487b10bd991ff02441a5f645d` |
| step12-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ProcessApiSchemaVerifierTest,ProcessStorageConfigurationTest,AutomationProcessSchemaPostgresTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 37 | 2026-11-15T09:26:56Z | `99d13fb3b898` | `d568983a4f8f1ad8e116ae851be83f6060b87ec2ea783cfa23f7d7159626c799` |
| step13-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=WorkerEffectReceiptIT,AutomationWorkerEffectModesIT,WorkerDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.worker.business.postgres.test.url=${TIANJI_WORKER_TX_TEST_JDBC_URL:?disposable-worker-PG-required}` `-Dtianji.worker.nontransactional.postgres.test.url=${TIANJI_WORKER_EXTERNAL_TEST_JDBC_URL:?separate-worker-PG-required}` `-Dtianji.worker.business.postgres.test.runtime-user=${TIANJI_WORKER_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 10 | 2026-11-15T09:27:30Z | `99d13fb3b898` | `1d807310a194dd1af092f8982dafba6acd3c6815d5dfc2a4aa1f620b18d78fd9` |
| step13-02 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=NonTransactionalWorkerPostgresTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.worker.nontransactional.postgres.test.url=${TIANJI_LEGACY_WORKER_PG_URL:?disposable-legacy-worker-PG-required}` `-Dtianji.worker.business.postgres.test.runtime-user=${TIANJI_WORKER_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:27:59Z | `99d13fb3b898` | `c1d55368d4944a98f95bb63c88f1dd975ff655de0885f546d0cc1f072b8d79f8` |
| step14-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationModelCompilerTest,ProcessModelValidatorTest,ProcessReleaseServiceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 53 | 2026-11-15T09:28:29Z | `99d13fb3b898` | `fb5e247a509ea982bf322fb701bf723a03af4a20eb88593b7db8e824864dc769` |
| step15-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=HttpProcessDraftClientTest,HttpProcessActivationClientTest,ClientDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 16 | 2026-11-15T09:28:59Z | `99d13fb3b898` | `c3b0330ffcdf4bb2da8f2be035b27896140ec51b5f44270345573e597e12f879` |
| step16-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=HttpProcessAutomationClientTest,HttpProcessEventClientTest,ProcessStartReceiptHttpTest,HttpProcessClientTest,ClientDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 30 | 2026-11-15T09:29:29Z | `99d13fb3b898` | `a512147cf1350b9b4501b1f66ad51f6d275febb4c0a6105f35225897c97067fb` |
| step17-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=HttpAutomationWorkerClientTest,HttpLinkedAgentClientTest,HttpAgentResultClientTest,HttpAgentResultTraceLinkTest,ClientDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 28 | 2026-11-15T09:29:59Z | `99d13fb3b898` | `10713f379934fdf73142f24f9f527f32e26894c5e7abd4fb18f2a2d74b0cd824` |
| step18-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ProcessDraftLifecycleIT,ProcessDraftControllerTest,ProcessSecurityConfigurationTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 15 | 2026-11-15T09:30:30Z | `99d13fb3b898` | `9abfe64d23695f226f69d8ad42bfefbb7cdfcebd28b2970b752fb226757859e9` |
| step19-01 | Stellar-Enclosure-Components | `./mvnw` `-pl` `stellar-enclosure-component-agent-flow-starter` `-am` `-Dtest=OptionalClasspathTest,MemoryStoreConformanceTest,JdbcMemoryStorePostgresTest,AgentFlowComponentContractTest,AgentFlowPropertiesBindingTest,ComponentContractGovernanceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Degon.agent.memory.postgres.test.url=${EGON_MEMORY_TEST_JDBC_URL:?required}` `-Degon.agent.memory.postgres.test.runtime-user=${EGON_MEMORY_TEST_RUNTIME_USER:?required}` `test` | 54 | 2026-11-15T10:05:08Z | `4254a191577f` | `ccadf02d2e37470635637b01a93f266971e2d39f259bc4b5a4073ebc503a512b` |
| step20-01-AutomationPublicationActivationIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationPublicationActivationIT,ProcessAutomationControllerTest,ProcessReleaseServiceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 33 | 2026-11-15T09:31:21Z | `99d13fb3b898` | `743eaa23cd12e0014ae6bca0cd1d0d7ae1fd1707872dbca984b4cb399ca3c34d` |
| step20-01-ProcessDraftLifecycleIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ProcessDraftLifecycleIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:32:32Z | `99d13fb3b898` | `f257a51470b8d5125a1542eca022112904cb01f5898df0a7df4e0f8d752d1116` |
| step21-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentRuntimeExecutionServiceTest,RuntimeCompatibilityIT,RuntimeSafetyReceiptIT,AdkCheckpointResumeExecutorTest,AgentRunDispatcherTest,TianjiServiceExecutionFlowTest,TianjiServiceExecutionGrantAuthorizerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.runtime.safety.postgres.compatibility.url=${TIANJI_AR_COMPAT_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.receipt.url=${TIANJI_AR_RECEIPT_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 42 | 2026-11-15T10:04:23Z | `4254a191577f` | `1d62c57f8eb11a90972fe201e714fddb4f95b46beba4f6acb45200eee70d0ee6` |
| step22-01 | tianji-service | `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false` `scripts/verify-source-reactor.sh` `-Dtest=ToolActionProfileIT,ToolActionApprovalSqlTest,ToolExecutionJournalPostgresTest#opt_in_tool_journal_contract_and_atomicity,ToolResultAccessStoreTest,AgentRuntimeExecutionServiceTest,TianjiToolGrantBindingsTest,TianjiToolGrantAuthorizerTest,TianjiGovernedProcessToolConfigurationTest,TianjiFixedBpmnStartToolTransportTest,ToolStartReceiptReconciliationServiceTest,ToolStartReceiptReconciliationSqlTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.tool.profile.postgres.test.url=${TIANJI_TOOL_PROFILE_TEST_JDBC_URL:?required}` `-Dtianji.tool.profile.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` `-Dtianji.tool.journal.postgres.test.url=${TIANJI_TOOL_TEST_JDBC_URL:?required}` `-Dtianji.tool.journal.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 177 | 2026-11-15T09:33:38Z | `99d13fb3b898` | `f7683bb393bc6a9f6a1da5ef022742536596122f662adf9c8d0b7b267b977c73` |
| step23-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentResultArtifactVerifierTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 7 | 2026-11-15T09:34:30Z | `99d13fb3b898` | `9acf6be76ef10f4b47654da4df317d75f61bb78ba64c679e250b4f62d414d8e1` |
| step24-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationPublicationActivationIT,ProcessActivationControllerTest,PlatformReleaseSignatureVerifierTest,PlatformReleaseActivationServiceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 21 | 2026-11-15T09:34:57Z | `99d13fb3b898` | `56555e55f652749e2374c733496edb2a645ceb8840660ff12b53d355b5bb6b26` |
| step25-jianshen-scope | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ApiRequirementEnforcementTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 10 | 2026-11-15T09:35:36Z | `99d13fb3b898` | `9c822b5d238fcdc2e7d3b0331772b94146f8db8d361b4f6d0a4781b349d11fb2` |
| step25-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationVersionIsolationIT,ProcessExecutionServiceTest,ProcessAutomationControllerTest,ProcessStartReceiptControllerTest,ProcessRequestDigestCompatibilityTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 24 | 2026-11-15T09:36:01Z | `99d13fb3b898` | `cd0c60b8cfb7f5c03782db2132152d1b078eeca03974ef69340daf708e5cc533` |
| step26-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationConcurrencyIT,EngineTaskControllerTest,ProcessNodeExecutionControllerTest,FlowableHumanTaskServiceTest,ProcessStorageConfigurationTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 50 | 2026-11-15T09:36:37Z | `99d13fb3b898` | `d6e7b9f3e3592af1770583efabab74a828fcdbfd6126aa075689db5383abaf93` |
| step26-version-compatibility | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationVersionIsolationIT,AutomationOpenApiContractTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?required}` | 11 | 2026-11-15T09:37:56Z | `99d13fb3b898` | `791679bbb11663411414198e7280b95e39d80de8d44af170269c1216d634eef8` |
| step26-migration-preflight | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ProcessMigrationPreflightPostgresTest,ProcessMigrationPreflightServiceTest,ProcessMigrationPreflightControllerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?required}` `-Dtianji.process.postgres.migration-preflight=true` `-Duser.timezone=UTC` | 11 | 2026-11-15T09:38:28Z | `99d13fb3b898` | `e1a4a0def76b41c5e35e9d6b38235a2e615aa05bc9a13bf2cf2d67473f856b71` |
| step27-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=RemoteWorkerPollerTest,WorkerDependencyBoundaryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 21 | 2026-11-15T09:39:01Z | `99d13fb3b898` | `3a728b30ce92167207af5113fb3072c5b02862c0db8afd57be0aac9b1f1cab88` |
| step28-01-AutomationWorkerReportingIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationWorkerReportingIT,AutomationWorkerControllerTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 32 | 2026-11-15T09:39:29Z | `99d13fb3b898` | `a294ac3529675ae213f9080fc714d13cf7ce67e78a457fcc3d354c40068f138b` |
| step28-01-AutomationControlFlowIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationControlFlowIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 13 | 2026-11-15T09:40:12Z | `99d13fb3b898` | `75dc5db28326fc7ca1421104e0db88e9a2f8e78395ed7ad969732a5ce2611e9e` |
| step29-01-recovery | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationWorkerRecoveryIT,ProcessNodeExecutionControllerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` `-Dtianji.worker.business.postgres.test.url=${TIANJI_WORKER_TX_TEST_JDBC_URL:?disposable-worker-PG-required}` `-Dtianji.worker.nontransactional.postgres.test.url=${TIANJI_WORKER_EXTERNAL_TEST_JDBC_URL:?separate-worker-PG-required}` `-Dtianji.worker.business.postgres.test.runtime-user=${TIANJI_WORKER_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 15 | 2026-11-15T09:41:08Z | `99d13fb3b898` | `4bc2430609f7154c78f206b58425af484b5305d223375f1ece15beaf6dd776f4` |
| step29-01-worker-modes | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationWorkerEffectModesIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.worker.business.postgres.test.url=${TIANJI_WORKER_TX_TEST_JDBC_URL:?disposable-worker-PG-required}` `-Dtianji.worker.nontransactional.postgres.test.url=${TIANJI_WORKER_EXTERNAL_TEST_JDBC_URL:?separate-worker-PG-required}` `-Dtianji.worker.business.postgres.test.runtime-user=${TIANJI_WORKER_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 3 | 2026-11-15T09:41:50Z | `99d13fb3b898` | `ec6c513fd2b01d4a1606d20c29395af4a12e64205c27311eda7c604bc7f1eb8e` |
| step30-01-AutomationControlFlowIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationControlFlowIT,ProcessDecisionServiceTest,AutomationModelCompilerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 32 | 2026-11-15T09:42:17Z | `99d13fb3b898` | `6b72a62f541732d77351ddc79c43a2ea4f03a6db690b828ba9099b786bceb48d` |
| step30-01-AutomationConcurrencyIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationConcurrencyIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:43:32Z | `99d13fb3b898` | `e906ff2d6f69c00ca458396af1455021a7581e43786389e262b31d3b69001c85` |
| step31-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationProcessSchemaPostgresTest,ProcessApiSchemaVerifierTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 19 | 2026-11-15T09:44:59Z | `99d13fb3b898` | `58ca3594355daa95042d61af330021d42ec2828ec9658ed94c8e8c2a1542480f` |
| step32-01-AutomationEventAdmissionIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationEventAdmissionIT,ProcessEventControllerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 9 | 2026-11-15T09:45:30Z | `99d13fb3b898` | `76f58c5bbe40408c0732011f7e8a651bd29e7ff60d92bcac16d63bd69a93f922` |
| step32-01-AutomationVersionIsolationIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationVersionIsolationIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:46:14Z | `99d13fb3b898` | `4e54015350ff4a75f8bba18bbe715c2d956a38b66a78f09bd3f12bf43948d597` |
| step33-01-ProcessEventIntegrationTest | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=ProcessEventIntegrationTest,ProcessStorageConfigurationTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 20 | 2026-11-15T09:46:47Z | `99d13fb3b898` | `53a959b3c3b6df8543d21750b6b6b5e0f71f5b551ae9b12dd000c5adeb1d468d` |
| step33-01-AutomationEventAdmissionIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationEventAdmissionIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:47:26Z | `99d13fb3b898` | `e8acd0f13db1977d1ba8da11d16d210ea9b7facafae87dce8667a10375c58a9e` |
| step33-01-AutomationConcurrencyIT | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AutomationConcurrencyIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 1 | 2026-11-15T09:48:10Z | `99d13fb3b898` | `e906ff2d6f69c00ca458396af1455021a7581e43786389e262b31d3b69001c85` |
| step34-01-legacy | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentResultInboxControllerTest,AgentResultInboxTest#keeps_v1_and_v2_receipts_durable_without_acknowledging_a_claimed_run,AgentResultCorrelationServiceTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.inbox.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` | 19 | 2026-11-15T10:05:23Z | `4254a191577f` | `dc3b4a2a06f82dccf8ae254cd6821771be941b1fb3a9e41027b5cc64b8cf487c` |
| step34-typed-native | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentResultInboxTest#applies_typed_terminal_at_the_compiled_native_wait` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.agent-typed.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?required}` | 1 | 2026-11-15T09:49:54Z | `99d13fb3b898` | `07c86c087363f817e02f37e2a71a660d99c805d86c4a71a390b1171428e2fee4` |
| step34-postgres-v5-to-v6 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentResultInboxPostgresTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.inbox.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?required}` | 1 | 2026-11-15T10:06:24Z | `4254a191577f` | `420e43d0f5cb42a08fdc2dadbc2f73dc314d9c1c2c42c9220baf55af67237e4d` |
| step35-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentRuntimeTemplateControllerTest,LinkedAgentRuntimeControllerTest,LinkedAgentDispatchControllerTest,VerifiedAgentIssuerFilterTest,TianjiSecurityConfigurationTest,TianjiServiceExecutionFlowTest,TianjiToolGrantIssuerTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` | 44 | 2026-11-15T09:50:53Z | `99d13fb3b898` | `a3dc5ad6637ee314405d9cb8563a1290bfcdc790d1b79445684ab82747b3504a` |
| step35-02 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=RuntimeSafetyReceiptIT` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.runtime.safety.postgres.receipt.url=${TIANJI_AR_RECEIPT_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_AR_PGVECTOR_TEST_VERSION:?pgvector-patch-required}` | 12 | 2026-11-15T09:51:26Z | `99d13fb3b898` | `23080c867991e22356e03c03fd6bc9475fe6ecdffc036dd0467a77a6ec458b9f` |
| step36-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentResultArtifactVerifierTest,OutboxAgentTerminalEventPublisherTest,AgentResultDeliveryHandlerTest,AgentTerminalOutboxPostgresTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.agent.outbox.postgres.test.url=${TIANJI_OUTBOX_TEST_JDBC_URL:?legacy-loopback-required}` `-Dtianji.agent.outbox.runtime-v2.postgres.test.url=${TIANJI_OUTBOX_RUNTIME_V2_TEST_JDBC_URL:?v2-loopback-required}` `-Dtianji.agent.outbox.postgres.test.runtime-user=${TIANJI_OUTBOX_RUNTIME_TEST_USER:?dedicated-role-required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_AR_PGVECTOR_TEST_VERSION:?pgvector-patch-required}` | 27 | 2026-11-15T09:52:04Z | `99d13fb3b898` | `85ab116ebc8c8d43b76db3a1e863f7a73b5421c0a75f30b02f412e0d1dc7452b` |
| step37-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentStartWorkerTest,ProcessAgentFusionIT,AgentTaskSnapshotIT,AutomationConcurrencyIT,AgentResultInboxTest` `-B` `-o` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.process.postgres.test.url=${TIANJI_PS_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.postgres.test.runtime-user=${TIANJI_PS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` `-Dtianji.process.agent-fusion.postgres.test.url=${TIANJI_PS_FUSION_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.agent-snapshot.postgres.test.url=${TIANJI_PS_SNAPSHOT_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.agent.fusion.postgres.test.url=${TIANJI_AS_FUSION_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.agent.fusion.snapshot.postgres.test.url=${TIANJI_AS_SNAPSHOT_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.agent.outbox.postgres.test.runtime-user=${TIANJI_AS_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_AS_TEST_VECTOR_VERSION:?exact-installed-vector-version-required}` `-Dtianji.process.agent-typed.postgres.test.url=${TIANJI_PS_TYPED_INBOX_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.process.inbox.postgres.test.url=${TIANJI_PS_LEGACY_INBOX_JDBC_URL:?disposable-loopback-PG-required}` | 17 | 2026-11-15T10:06:56Z | `4254a191577f` | `5c058058c22f043dcee01cd84e87c2a1e58ded8015bdfcd7c313676e307ecd04` |
| step38-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=PostgresMemoryAuthorizationTest,MemoryRetentionWorkTest,MemoryIndexWorkTest,TianjiMemoryConfigurationTest,MemoryHostAdapterTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.host.postgres.test.url=${TIANJI_HOST_MEMORY_TEST_JDBC_URL:?disposable-loopback-PG-required}` `-Dtianji.memory.host.postgres.test.runtime-user=${TIANJI_HOST_MEMORY_RUNTIME_TEST_USER:?dedicated-runtime-role-required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?exact-installed-vector-version-required}` | 20 | 2026-11-15T09:54:31Z | `99d13fb3b898` | `ecfb21ec980caa6848d6c21aadaf77becf4abd3125c3528fcf03dac569eeb9a3` |
| step38-receipt-epoch | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=LegacyMemoryReceiptEpochPostgresTest,RuntimeCompatibilityIT,MemoryStorageVerifierTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.receipt-epoch.postgres.test.url=${TIANJI_MEMORY_RECEIPT_EPOCH_TEST_URL:?required}` `-Dtianji.runtime.safety.postgres.compatibility.url=${TIANJI_RUNTIME_COMPAT_TEST_URL:?required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 7 | 2026-11-15T09:55:04Z | `99d13fb3b898` | `6a8cae1e638c4b9eb384b3987cf6a72cfab08d7e86c983c5f597323adcf7dec5` |
| step38-memory-index-outbox | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=MemoryIndexProtocolTest,TianjiMemoryConfigurationTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.index.outbox.postgres.test.url=${TIANJI_MEMORY_INDEX_OUTBOX_TEST_URL:?required}` `-Dtianji.memory.index.outbox.postgres.test.runtime-user=${TIANJI_MEMORY_INDEX_OUTBOX_TEST_USER:?required}` | 9 | 2026-11-15T09:55:33Z | `99d13fb3b898` | `d60e1d2bfce3bbaaab9f49829655ad7e72d55c61079f260b1fa1261b82f8bc91` |
| step39-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=MemoryMigrationIT,MemoryHostAdapterTest,MemoryIndexWorkTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.migration.postgres.test.url=${TIANJI_MEMORY_MIGRATION_TEST_JDBC_URL:?required}` `-Dtianji.memory.migration.postgres.test.runtime-user=${TIANJI_MEMORY_MIGRATION_RUNTIME_USER:?required}` `-Dtianji.memory.migration.postgres.test.migration-user=${TIANJI_MEMORY_MIGRATION_USER:?required}` `-Dtianji.memory.migration.postgres.test.maintenance-user=${TIANJI_MEMORY_MAINTENANCE_TEST_USER:?required}` `-Dtianji.memory.migration.postgres.test.outbox-ddl=${TIANJI_OUTBOX_V1_SOURCE_FILE:?required}` `-Dtianji.memory.host.postgres.test.url=${TIANJI_MEMORY_HOST_TEST_JDBC_URL:?required}` `-Dtianji.memory.host.postgres.test.runtime-user=${TIANJI_MEMORY_HOST_TEST_USER:?required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 6 | 2026-11-15T09:56:12Z | `99d13fb3b898` | `b67dac784f969e26476fde04c204107e7c4cf8e1c1c403b9d1e1833eb073c9f2` |
| step39-02 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=MemoryPostgresTest#v10_fact_receipt_index_visibility_forget_concurrency_grants_and_catalog_fences` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.postgres.test.url=${TIANJI_MEMORY_LEGACY_TEST_JDBC_URL:?required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` | 1 | 2026-11-15T09:56:52Z | `99d13fb3b898` | `22112c7b3117b427dbaf11be7ed3ecadd614b1bc730b8e70f73a222ac56b8115` |
| step39-03 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=MemoryPostgresTest#replacement_executor_revalidates_owned_durable_memory_history_and_blocks_forgotten_sources` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.memory.postgres.test.url=${TIANJI_MEMORY_SESSION_TEST_JDBC_URL:?required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` `-Dtianji.memory.postgres.session-context.enabled=true` | 1 | 2026-11-15T09:57:25Z | `99d13fb3b898` | `22112c7b3117b427dbaf11be7ed3ecadd614b1bc730b8e70f73a222ac56b8115` |
| step47-01 | tianji-service | `scripts/verify-source-reactor.sh` `-Dtest=AgentRunHttpTest,AgentRunHttpPostgresTest,MemoryControllerTest,MemoryPreferenceControllerTest,TianjiNativeAgentTemplateConfigurationTest,RuntimeCompatibilityIT,MemoryMigrationIT,MemoryPreferenceAdmissionTest,NativeAgentGateTest` `-Dsurefire.failIfNoSpecifiedTests=false` `-DskipTests=false` `-Dtianji.http.postgres.test.url=${TIANJI_HTTP_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.compatibility.url=${TIANJI_AR_COMPAT_TEST_JDBC_URL:?required}` `-Dtianji.runtime.safety.postgres.vector.version=${TIANJI_TEST_VECTOR_VERSION:?required}` `-Dtianji.memory.migration.postgres.test.url=${TIANJI_MEMORY_TEST_JDBC_URL:?required}` `-Dtianji.memory.migration.postgres.test.runtime-user=${TIANJI_MEMORY_TEST_RUNTIME_USER:?required}` `-Dtianji.memory.migration.postgres.test.maintenance-user=${TIANJI_MEMORY_TEST_MAINTENANCE_USER:?required}` `-Dtianji.memory.migration.postgres.test.migration-user=${TIANJI_MEMORY_TEST_MIGRATION_USER:?required}` `-Dtianji.memory.migration.postgres.test.outbox-ddl=${TIANJI_MEMORY_TEST_OUTBOX_DDL:?required}` `-Dtianji.memory.postgres.vector.version=${TIANJI_MEMORY_TEST_VECTOR_VERSION:?required}` | 77 | 2026-11-15T09:57:57Z | `99d13fb3b898` | `81db07172f994824c3904f8914be7a9567eb9a9516e2e322c65b3883839b166c` |
| step52-01 | Stellar-Enclosure-Components | `./mvnw` `-B` `-ntp` `-pl` `stellar-enclosure-component-agent-flow-starter` `-am` `-DskipTests=false` `-Dsurefire.failIfNoSpecifiedTests=false` `-Dtest=AgentExecutorConformanceTest,OptionalClasspathTest,MemoryStoreConformanceTest,AgentFlowComponentContractTest,ComponentContractGovernanceTest,AgentFlowPropertiesBindingTest` `test` | 66 | 2026-11-15T09:58:37Z | `99d13fb3b898` | `bda89bd04635a6d38700eb2a6546f99f4c18763d3e63a697d3b5b1724a785f75` |

## 需求追溯

需求追溯以有效 Spec §19 为基准，使用 Plan §10 已复验的用例边。下节列各用例的执行见证。

| Requirement | Accepted contract | Executed test references |
| --- | --- | --- |
| REQ-001 | 统一 Process 入口/实例。三类流程由同一发布/start/instance contract 管理，Agent 为节点类型 | TEST-001、TEST-002、TEST-003、TEST-006、TEST-014 |
| REQ-002 | 通用自动化控制流。Service→规则→条件→并行/汇合、有界循环/多实例、子流程输入输出可运行 | TEST-001、TEST-003、TEST-007 |
| REQ-003 | 每次执行与作用域。loop occurrence、parallel branch、retry attempt 各自准确关联；旧结果不能命中新一轮 | TEST-001、TEST-003、TEST-006、TEST-007 |
| REQ-004 | Service Task 系统对接。复用 Worker，合同固定版本，可靠幂等和未知副作用处置 | TEST-002、TEST-003、TEST-004、TEST-017 |
| REQ-005 | 固定规则执行。多个 DMN 或注册规则节点按固定版本执行，输出用于网关 | TEST-001、TEST-003 |
| REQ-006 | 通用事件驱动。受权事件启动/唤醒/发出，去重、早到预约、迟到和跨 tenant 可验证 | TEST-005、TEST-016 |
| REQ-007 | 通用 Agent Task。单节点 typed input/output，可靠异步，失败/超时/取消按流程策略 | TEST-002、TEST-006、TEST-008 |
| REQ-008 | 实例 Agent 配置。已发布模板精确版本与声明参数可覆盖，START 固定整个实例 manifest，恢复/重试保持 | TEST-008、TEST-012 |
| REQ-009 | Runtime/Context 收敛。AS 保持运行和可信 scope，通用执行经 AF；Context 不能跨实例/用户/tenant 越界 | TEST-008、TEST-009、TEST-011 |
| REQ-010 | Agent 组件独立。普通 Spring 应用可执行/流式/取消，公共契约无 ADK 或 Tianji 类型 | TEST-009 |
| REQ-011 | Memory/RAG/Agentic 下沉。内存实现可独立用；可选存储/检索加载；宿主提供授权/来源 | TEST-009、TEST-010 |
| REQ-012 | 预算职责退出。新模式费用表零写/零授权依赖，安全收据替代 budget FK 用途，旧字段/运行可读可完成 | TEST-011、TEST-012 |
| REQ-013 | 人工任务通用化。业务平台提交 schema 校验结果，任务授权/claim/complete 可用，新 profile 无审批政策 | TEST-006、TEST-013、TEST-014 |
| REQ-014 | Web 引擎控制台。纯 Process 权限可进入；设计发布、实例图/执行、事件与恢复可操作 | TEST-002、TEST-013 |
| REQ-015 | 业务入口与历史退役。MOVE/REMOVE 逐项完成消费者替代、兼容和页面/权限清理 | TEST-010、TEST-012、TEST-013、TEST-014 |
| REQ-016 | 可靠性和运行安全。两库无共享事务；去重、取消、lease、权限、并发、deadline、未知副作用有测试 | TEST-004、TEST-005、TEST-006、TEST-007、TEST-010、TEST-011、TEST-013、TEST-017 |
| REQ-017 | 历史和版本迁移。旧 release/SQL/hash/快照不改写，在途与历史回调可验证，新旧 profile 明确 | TEST-002、TEST-010、TEST-012、TEST-016 |
| REQ-018 | 三仓最终验收。自动化/混合/纯 Agent/组件独立场景及故障矩阵通过 | TEST-014 |
| REQ-019 | 已发布版编辑与持久草稿。从v2已发布版复制源设计；未完成内容可暂存；刷新/关闭后恢复；owner隔离、revision并发不覆盖 | TEST-015、TEST-018 |
| REQ-020 | 固定修订发布与独立生效。草稿修订冻结为不可变release；发布不自动改指针；发布并生效分步结果与原键恢复可见 | TEST-015、TEST-016、TEST-018 |
| REQ-021 | 默认新版发起与在途旧版隔离。正式START/新事件由服务端选择当前activation；旧实例/原命令/已接纳事件固定旧版；切换竞争有明确事务边界 | TEST-016、TEST-017、TEST-018 |

## 验收用例

| Test | Executable evidence | Observable result |
| --- | --- | --- |
| TEST-001 | AutomationModelCompilerTest、ProcessModelValidatorTest、ProcessReleaseServiceTest、AutomationPublicationActivationIT | 发布前检查图、固定模型和 typed schema。合法图独立生成部署，非法源在部署之前被拒绝。 |
| TEST-002 | AutomationTypedContractTest、AutomationDraftContractTest、AutomationExecutionContractTest、AutomationWorkerContractTest、AgentRuntimeV2ContractTest、四组 OpenAPI 合同测试及 Web API tests | 旧 wire/黄金字节与新闭合字段分别验证，包含未知字段、摘要、长度、精度和调用资源键。 |
| TEST-003 | AutomationControlFlowIT、ProcessDecisionServiceTest、AutomationConcurrencyIT | 原生条件、固定 DMN、并行合并、循环、多实例和子流程以实际作用域及结果推进。 |
| TEST-004 | WorkerEffectReceiptIT、AutomationWorkerEffectModesIT、RemoteWorkerPollerTest、AutomationWorkerRecoveryIT | Worker 的登记、派发、结果、重领和权威回查按原效果键关联。工具支持的事务模式及外部效果的各自失败方实测。 |
| TEST-005 | ProcessEventIntegrationTest、AutomationEventAdmissionIT、ProcessEventControllerTest | 真正的 START/SIGNAL 持久准入及预约，覆盖提前事件、旧固定版本、重复、晚到与原回执。 |
| TEST-006 | AgentResultInboxTest、AgentResultInboxPostgresTest、AgentTerminalOutboxPostgresTest、ProcessAgentFusionIT | 原终态字节重投及完整身份关联。真实 Flowable WAIT 接收 typed Agent 结果、保留旧回执并推进一次。 |
| TEST-007 | AutomationConcurrencyIT | 真实 PostgreSQL 观测一百轮共享实例根锁的三个独立会话。34 次取消、33 次人工完成、33 次定时器先取得锁，死锁与重复推进均为 0。 |
| TEST-008 | AgentTaskSnapshotIT、AutomationVersionIsolationIT、RuntimeSafetyReceiptIT、AgentRuntimeTemplateControllerTest | 固定模板、发布版本、快照和允许的 override 在恢复和换版后仍可根据原证据读取。 |
| TEST-009 | AgentExecutorConformanceTest、OptionalClasspathTest、AgentFlowComponentContractTest、AgenticPlanTest、AdkAgentExecutorAdapterTest、McpToolAdapterTest | 组件中立协议、取消、背压、额度、模型 idle、实际工具发送和缺可选类验证。 |
| TEST-010 | MemoryStoreConformanceTest、JdbcMemoryStorePostgresTest、LegacyMemoryReceiptEpochPostgresTest、MemoryHostAdapterTest、MemoryMigrationIT、MemoryIndexProtocolTest、两个旧 Memory Postgres 方法 | 双实现合同及单一写权威。迁移、切换前恢复、多主体授权、1,000 个应用事实、原 epoch、物理遗忘及真实 Outbox 原身份通过。 |
| TEST-011 | RuntimeSafetyReceiptIT、ToolActionProfileIT、ToolExecutionJournalPostgresTest、ToolActionIntentStoreTest、TianjiServiceExecutionGrantAuthorizerTest | v2 技术调用与效果证据配合当前授权，覆盖预算角色隔离、旧工具回执、批准和新旧提交过渡。 |
| TEST-012 | RuntimeCompatibilityIT、ProcessMigrationPreflightPostgresTest、AutomationProcessSchemaPostgresTest、AgentRunHttpPostgresTest、ToolExecutionStorageVerifierTest | 真实迁移后严格读回旧固定数据，覆盖 V1–V10 预检、原 PS/AR 尾版本、历史差异拒绝和原直启回执。 |
| TEST-013 | Web 全量 Vitest、clean PAC build 与实际 Chromium | 资源、租户 epoch、DATA_READ 分立、原键恢复、旧书签零请求、角色切换、390px 键盘编辑及历史 Agent 列表通过。 |
| TEST-014 | AutomationIndependentServicesIT 和 durable prepare/run | 三个 JVM 和三座库完成四种业务路径、换版、六个持久故障切点及外部身份/晚结果拒绝。 |
| TEST-015 | ProcessDraftLifecycleIT、ProcessDraftControllerTest、ProcessDraftEditorPage.test.tsx | 不完整草稿可保存和恢复。owner、修订 CAS 和原命令回执分别生效，保存时的部署计数为 0。 |
| TEST-016 | AutomationVersionIsolationIT、AutomationPublicationActivationIT、AutomationEventAdmissionIT 和独立版本路径 | 当前生效指针控制新准入。原实例、原命令、原已接纳事件保持其绑定，覆盖无生效和切换竞争。 |
| TEST-017 | AutomationWorkerReportingIT、AutomationWorkerRecoveryIT、AutomationWorkerEffectModesIT、RemoteWorkerPollerTest 和独立恢复路径 | 同租约同键并发返回同一决定，只写一次。长错误码、摘要冲突、旧证明、UNKNOWN 与丢 ACK 均按持久事实验证。 |
| TEST-018 | AutomationPublicationActivationIT、ProcessActivationPanel.test.tsx、ProcessDraftEditorPage.test.tsx 和独立版本路径 | publication 和 activation 分别授权、CAS 与读回。前端和服务端恢复原键，支持当前和历史互相独立的读取。 |

## 责任和消费者

| Ownership | Delivered boundary | Evidence |
| --- | --- | --- |
| OWN-01 | Process 和 Flowable 持有根实例、节点迁移和原生并发状态 | TEST-003/007、独立 Process 切点 |
| OWN-02 | Worker 持有本地效果和原回执。Process 负责固定规则/DMN 和事件准入 | TEST-003/004/005/017 |
| OWN-03 | AS/AR 持有 AgentRun、当前授权、Context 和终态。AF 执行中立合同 | TEST-006/008/009/011 |
| OWN-04 | Process 持有草稿、immutable release、生效历史和正式 START | TEST-015/016/018 |
| MOVE-01 | 业务消费者存表单、意见、附件引用和真实附件字节。Process 接受 typed 结果引用 | 独立 human-only 路径用两个真实授权租户。业务库和节点输出分别读回，节点及根输出均只含 `resultRef` |
| MOVE-02 | 业务消费者持有审批策略和组织选择，Process 保留通用候选、受领和结果提交 | 实际当前候选角色和跨租户拒绝，API-009/010 USER 主体与开放接口合同 |
| MOVE-03 | 业务任务中心由 Process Client 读取候选，提交 claim/complete 和消费任务事件 | 候选查询、原 claim/complete 回执、原生任务历史和旧 Task 浏览器/PAC 闭包 |
| MOVE-04 | AF MemoryStore 和显式宿主适配持有新 Memory，AR 迁移并恢复旧源 | TEST-010/012、旧 Memory 回执、遗忘后物理读回、索引 Outbox |
| MOVE-05 | 可选宿主持有向量、RAG 与 embedding 能力 | OptionalClasspathTest、MemoryStoreConformanceTest、真实 JDBC，默认组件边界按依赖缺类验证 |
| MOVE-06 | AF 提供中立 LLM/MCP、Agentic Plan 和执行。AS/AR 持有当前授权和账本 | TEST-009/011、公开模块边界，实际模型和工具派发测试 |
| MOVE-07 | 业务模板在业务消费者或受控固定模板目录中维护 | 独立 fixture 使用固定 v2 泛化模板，旧模板开关及存量继续读回 |
| REMOVE-01 | 新运行和写调用使用 V17 技术回执，与旧预算/预留数据分立 | RuntimeSafetyReceiptIT 的受限角色与旧预算流程读回 |
| REMOVE-02 | 新业务准入经 Process。Agent linked 调度和已提交历史入口继续按原键 | 两版 Linked Execute、Step47 实库、Browser/PAC 退役闭包 |
| REMOVE-03 | 跨节点执行由 Process 编排。局部工具遵循授权和效果栅栏 | ToolActionProfileIT、业务效果恢复及独立 E2E |
| KEEP-01 | 旧数据和回执读取、已接纳执行、真实服务来源继续由原有契约承接 | API-025 owner、原 V1/v2 terminal push、同命令摘要重放 |

Service 的 [Automation v2 integration](../../../README.md) 说明业务调用和原回执恢复。Components Agent Flow starter 的英文和中文 README 说明独立宿主、可选 Memory/RAG。Web 的 Process workspace 说明汇总设计、发布、生效、启动和权限客户端行为。独立验收中的业务消费者提供 API 可执行例子和持久见证。

## 升级与回退边界

- PS 安装 V14/V15 后再开启自动化发布及准入。旧 BPMN 版本和在途实例继续从原绑定读取。发布与生效独立，生效到已验证的旧 release 会增加一条生效历史。提交后使用原键及精确请求回查。启用前盘点现存原生 job、旧 release 和回执来源。
- Worker 先校验可信来源、固定处理器和 V3 持久效果，再允许 v2 领取。已有租约、提交回执和业务效果键需继续可查。UNKNOWN 等待权威证明，核对当前实例修订、原 attempt、有效期和代次后才允许后续操作。缺少证据时保持等待。
- AR 先使用兼容 V17/V18 的 reader，再安装 V18，存量回执的未知 epoch 保持 NULL。处理在途、grant、旧模板工厂和 Outbox 后，再按 scope 冻结和复制旧 Memory。切换前可恢复旧权威，生效后保持 COMPONENT 唯一权威，依前向修复恢复一致性。保留旧准确回执读法和实际业务事实。
- Agent 旧直启下线前盘点已受理但 grant 缺失或失效的 USER Run，以及仍需原固定模板和处理器的在途调用。关闭新准入后，已授权原主体以原键、原摘要读取已提交结果。旧 Run 查询、SSE、取消、已下发 grant 和所需结果回查依原合同处理。服务上线清单由运行环境操作者执行。
- Step 52 需要撤回时只 revert 本证据所在提交，恢复 Step 46 的原脚本位置。业务迁移和生效历史按各自权威及已完成效果进行前向处置。

## 人工门禁

| Manual check | Verified evidence |
| --- | --- |
| MC-ARCH-001 | Process/Flowable 的表达与推进、Worker 的本地效果、AS/AR 的账本和 AF 中立调用各自可从源码和 3 JVM 实际记录定位。 |
| MC-REUSE-001 | 原 Flowable native store、MP-SDJ/Flyway 历史、Worker lease、Outbox/Inbox 和原授权入口由现有契约复跑。 |
| MC-DEP-001 | ClientDependencyBoundaryTest、WorkerDependencyBoundaryTest、AgentFlowComponentContractTest、OptionalClasspathTest 和制品检查。 |
| MC-NAME-001 | ProcessNodeExecutionBO、AutomationWorkerCompletionCommand、WorkerEffectRecoveryVO 和 RuntimeProfileEnum 各按内部事实、外部请求、证据或固定枚举命名。 |
| MC-VALID-001 | typed DTO 的 Jakarta 约束和闭合 JSON 在控制器及受权 service 边界复验，schema、原键或来源漂移的测试保持拒绝。 |
| MC-MODEL-001 | ProcessNodeExecutionBO 使用 Lombok 可变值装配，不可变的 LinkedRunEvidenceVO 固定读出的 Run、link 与授权事实。 |
| MC-CONVERT-001 | ProcessNodeExecutionConverter、AutomationInstanceConverter、AgenticTemplateConverter 和 LegacyMemoryFactConverter 复用 MapStruct 及 Egon 单向投影。Step 1 effective POM 验证实际注解处理器。 |
| MC-LOG-001 | ProcessNodeExecutionBO 对 input、output 和 proof 启用 ToString.Exclude。运行证据收集身份替代标记、摘要、状态、时间和计数。 |
| MC-BEAN-001 | 原有完整 Qualifier 传播配置及 Spring 装配回归。Runtime 执行、Process 原生协调和 Agent terminal delivery 由实际入口装配。 |
| MC-UTIL-001 | 本轮最终纠正复用 JDK、Mockito、现有 Spring JDBC 以及实际生产 codec。 |
| MC-JSON-001 | 合同 JSON、Worker V1/V2、发布 schema 及 Memory V1 事件在 Jackson、原 codec 和真实 Outbox 上验证。 |
| MC-TIME-001 | 数据库 UTC 时钟、timestamptz(6) 等值与 1 µs 分界、已有有效期和原回执持续一致。 |
| MC-CONFIG-001 | Profile 的默认行为、可选依赖及真实启动数据源由 AgentFlowPropertiesBindingTest、各仓配置测试和独立服务验证。 |
| MC-PATTERN-001 | Flowable 信号和命令复用原生路径，InstanceGuard 固定更新顺序，租约、原回执和事务 outbox 的故障恢复经实库与真实切点核对。 |
| MC-SCOPE-001 | 下节逐 Step 核对已提交路径，最终脚本内容与原 blob 完全相同。 |
| MC-TEST-001 | 当前正向、负向、隔离权限、迁移继承和原回执分别有可定位的非零报告。 |
| MC-BLOCKER-001 | 首轮失败及恢复动作有归属和复验，最终通过以对应实测行和最终制品为凭。 |

| Literal rule | Audit evidence |
| --- | --- |
| Rule 1 | BO、DTO、Command、VO 和 Enum 按实际责任命名，当前纠正只修改既有测试。 |
| Rule 2 | typed 输入、租户授权与跨层契约按 Jakarta/Spring 验证，双版按各自 schema 和已固定字段验证。 |
| Rule 3 | 普通 ProcessNodeExecutionBO 采用 Data、NoArgsConstructor、AllArgsConstructor、Accessors 等基线，record 用于不可变证据和外部已验证的值。投影通过实际 MapStruct/Egon converter。 |
| Rule 4 | AgentRuntimeExecutionService 展示 Slf4j、具名装配、Lombok 构造注入和显式 Qualifier，Spring 启动与编译测试共同验证。 |
| Rule 5 | 事务、JSON、查询、时间与工具边界复用既有 Spring/JDK/组件实现和依赖。 |
| Rule 6 | 受控 Jackson 及 action/result profile codec 保持 wire 语义，数据持久列、枚举和旧历史经严格查验。 |
| Rule 7 | 固定配置、明确 profile、scope、时间和 runtime 可选性经 binding、原路径与实际 fixture 检查。 |
| Rule 9 | 组件与业务各执其责。当前身份与数据库事务边界分别核验，原 RemoteWorker/Poller 和终态交付行为逐组复跑。 |
| Rule 10 | 并发流程的原生 guard、版本锁和同事务回执用真实执行链验证，包括阻塞、恢复和零重复效果。 |
| Rule 11 | 所有更改含前置和纠正各在实际所有仓库路径。Step 52 范围为持久脚本、旧脚本路径及验收记录。 |

## 提交追溯

Steps 1–51 的提交路径与各自确认范围逐项相同。Step 52 由本证据所在的提交记录。

| Step | Repository | Commit | Paths | Subject |
| --- | --- | --- | ---: | --- |
| Step 1 | tianji-service | `5fe75885bde5` | 4 | build(tianji): verify annotation processing and constructor qualifiers |
| Step 2 | Stellar-Enclosure-Components | `60aeb2596183` | 25 | feat(agent-flow): add neutral execution context and sessions |
| Step 3 | tianji-service | `2f5070327984` | 10 | feat(contract): freeze typed automation schema |
| Step 4 | tianji-service | `83a8577133c8` | 9 | feat(tianji-agent): read both tool journal schemas |
| Step 5 | tianji-service | `72fe65f30da6` | 21 | feat(contract): define draft publication and activation wire |
| Step 6 | tianji-service | `74e7d72e8cf4` | 15 | feat(contract): define process execution and event wire |
| Step 7 | tianji-service | `97a8c65e9cf6` | 20 | feat(contract): define typed worker and agent bridge v2 |
| Step 8 | Stellar-Enclosure-Components | `c654f2fee97d` | 13 | feat(agent-flow): adapt bounded agentic and tool execution |
| Step 9 | Stellar-Enclosure-Components | `937ec7e6651e` | 15 | feat(agent-flow): add scoped typed in-memory memory |
| Step 10 | tianji-service | `0010a39e7fa5` | 8 | docs(contract): publish automation and linked agent openapi |
| Step 11 | tianji-service | `4939429c2915` | 9 | feat(tianji-agent): persist versioned runtime safety receipts |
| Step 12 | tianji-service | `46f30635a524` | 7 | feat(process): verify automation v14 storage |
| Step 13 | tianji-service | `ec3963e5757a` | 19 | feat(worker): persist stable typed effect receipts |
| Step 14 | tianji-service | `e18fd83419da` | 7 | feat(process): compile controlled automation models |
| Step 15 | tianji-service | `76ebdc13af34` | 6 | feat(client): add process draft and activation clients |
| Step 16 | tianji-service | `9a03918b7cf7` | 5 | feat(client): add typed process and event clients |
| Step 17 | tianji-service | `b3b7b05cc7fb` | 7 | feat(client): add typed worker and agent delivery adapters |
| Step 18 | tianji-service | `845cd8a35e63` | 17 | feat(process): persist owner-scoped process drafts |
| Step 19 | Stellar-Enclosure-Components | `7b6a2a92e350` | 15 | feat(agent-flow): add optional JDBC memory and retrieval adapters |
| Step 20 | tianji-service | `20412bff5fe9` | 11 | feat(process): publish immutable draft revisions |
| Step 21 | tianji-service | `88911ecc44a5` | 19 | feat(tianji-agent): execute linked runs by persisted profile |
| Step 22 | tianji-service | `ca6465e1d9f3` | 26 | feat(tianji-agent): share tool effect fences across profiles |
| Step 23 | tianji-service | `282038656e2a` | 5 | feat(tianji-agent): define trusted terminal artifact verification |
| Step 24 | tianji-service | `630ee3b30beb` | 17 | feat(process): activate immutable automation releases |
| Step 25 | tianji-service | `c8d51925c9dc` | 21 | feat(process): pin automation starts and recover original receipts |
| Step 26 | tianji-service | `a6ec6907bec5` | 28 | feat(process): guard automation node execution |
| Step 27 | tianji-service | `b8bb79052381` | 5 | feat(worker): poll typed tasks through the existing worker |
| Step 28 | tianji-service | `3537c6acf746` | 18 | feat(process): complete typed worker reports idempotently |
| Step 29 | tianji-service | `242eeced692a` | 16 | feat(process): recover worker effects with frozen evidence |
| Step 30 | tianji-service | `462217532bd2` | 10 | feat(process): execute scoped automation control flow |
| Step 31 | tianji-service | `147000de420d` | 6 | feat(process): verify event v15 storage |
| Step 32 | tianji-service | `6ecbdefb85c3` | 12 | feat(process): pin event start admission |
| Step 33 | tianji-service | `bb23e7fccdd9` | 15 | feat(process): signal reserved waits and publish lifecycle events |
| Step 34 | tianji-service | `eef7b7afdf3c` | 14 | feat(process): read both agent terminal profiles |
| Step 35 | tianji-service | `ab11e5749de4` | 18 | feat(tianji-agent-server): expose governed runtime template and linked submission |
| Step 36 | tianji-service | `63f64f5ecb2a` | 12 | feat(tianji-agent): deliver verified terminal outcomes across profiles |
| Step 37 | tianji-service | `9ffe7b46d90b` | 13 | feat(process): correlate typed agent task outcomes |
| Step 38 | tianji-service | `89e0f261cd76` | 22 | refactor(tianji-agent): fence legacy memory through the component scope |
| Step 39 | tianji-service | `343f7a85a0b6` | 7 | feat(tianji-agent): migrate verified memory history by scope |
| Step 40 | tianji-web | `734940394a82` | 11 | feat(tianji-web): 增加严格流程v2客户端 |
| Step 41 | tianji-web | `aeec3a156202` | 2 | feat(tianji-web): 增加所有者草稿工作区 |
| Step 42 | tianji-web | `5a7d69544c17` | 17 | feat(tianji-web): 增加流程实例观察 |
| Step 43 | tianji-web | `1fe40c7b9e38` | 8 | feat(tianji-web): 增加生效面板与受控技术启动 |
| Step 44 | tianji-web | `712a3b897c71` | 4 | feat(tianji-web): 增加修订感知草稿编辑器 |
| Step 45 | tianji-web | `9da41f397eff` | 9 | feat(tianji-web): 接入流程控制台路由与权限 |
| Step 46 | tianji-service | `9cca87b14b1b` | 9 | test(tianji): verify automation across independent process agent and worker services |
| Step 47 | tianji-service | `3970f8aa565c` | 16 | refactor(tianji-agent-server): retire superseded business write entry points |
| Step 48 | tianji-web | `845a50aeb6aa` | 46 | refactor(tianji-web): 退役Task与Memory业务控制台 |
| Step 49 | tianji-service | `2bfa627eae91` | 1 | docs(tianji): describe automation v2 consumer and recovery contracts |
| Step 50 | Stellar-Enclosure-Components | `c88fd8912c9f` | 2 | docs(agent-flow): explain standalone execution and optional memory |
| Step 51 | tianji-web | `7a2e7eb45a79` | 1 | docs(tianji-web): describe process workspace and command recovery |
| Step 52 | tianji-service | 本证据所在提交 | 3 | docs(tianji): record convergence evidence and preserve repeatable acceptance |

独立前置、兼容与证据纠正留存如下。

| Repository | Commit | Subject |
| --- | --- | --- |
| tianji-service | `407b76f09117` | fix(tianji-agent): accept PostgreSQL tool check rendering |
| tianji-service | `00f73b1e43a4` | fix(tianji): align automation worker capability keys |
| tianji-service | `57be8de8ebdf` | fix(openapi): align draft and activation definition keys |
| tianji-service | `b9b577c5ddb5` | fix(openapi): align worker reports and node cursor syntax |
| tianji-service | `615efa7bc3a6` | fix(client): preserve process tenant-context conflicts |
| tianji-service | `4d69044921be` | fix(process): expose verified automation deployment archive |
| tianji-service | `869f92dd4f67` | feat(tianji-agent): preserve legacy memory receipt authority epochs |
| tianji-service | `627b3ab71c09` | fix(tianji-agent): fit memory index transport identifiers in outbox |
| tianji-service | `86aae0308e6c` | fix(tianji-process): resolve the identity mapper alongside outbox |
| tianji-service | `e63e28364fde` | fix(tianji-agent): wire identity beans for terminal delivery |
| tianji-service | `32f387deeb58` | fix(tianji-agent): expose the memory fallback mapper to identity |
| tianji-service | `db0d65d36e33` | fix(tianji-process): start the Agent bridge after Flowable |
| tianji-service | `0624e56a7b7a` | fix(tianji-process): route event API bearer identities by contract |
| tianji-service | `b97ba0a4ddde` | fix(tianji-process): skip closed Agent dispatch candidates |
| tianji-service | `6eb5d3cfa581` | test(tianji): isolate independent service fixture configuration |
| tianji-service | `3b6255561189` | test(tianji): verify business-owned human task data |
| tianji-service | `1354ce009d73` | test(process): prove native deadline and terminal concurrency |
| tianji-service | `57843014426d` | test(process): verify concurrent report and error projection |
| tianji-service | `99d13fb3b898` | test(process): reject recovery proof after concurrent cancellation |
| tianji-service | `cd9f0be14c70` | test(agent-server): verify service grants with linked run evidence |
| tianji-service | `4254a191577f` | test(agent-runtime): align tool intent fixtures with runtime evidence |
| tianji-service | `2d6c8c5b001b` | test(tianji): bound retries for event receipt polling |
| Stellar-Enclosure-Components | `e01775273578` | fix(agent-flow): govern model dispatch and outcomes |
| Stellar-Enclosure-Components | `c9e0e991ee6d` | fix(agent-flow): observe governed tool dispatch at send |
| Stellar-Enclosure-Components | `e2b0103913f7` | fix(agent-flow): close tool callback when host returns |
| Stellar-Enclosure-Components | `f237ca02bdd9` | feat(agent-flow): expose exact JDBC memory storage identity |
| Stellar-Enclosure-Components | `abadeef8474a` | fix(agent-flow): check memory storage through validation proxies |
| tianquan-jianshen-service | `671e20b6702f` | fix(jianshen): allow opted-in service scope alternatives |
