# 预算run优先锁顺序纠正执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [budget锁顺序纠正](../plan/2026-10-31-22-06-budget-lock-order.md)，strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/12/13、T3-07 |
| 主设计实施前SHA-256 | `42d44a5b85e35d218005d7c7a22808c890217c1f21aaf9e930618ee2091dfcb6` |
| Baseline | `56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963` |
| 提交 | 主代理完成八路径diff/stage门禁后产生；不预填hash，以Git和交付消息为准 |
| 结果 | 此前置纠正的源代码/受控回归通过；真实PG锁竞争未执行，完整T3-07/平台仍Partial |

## 1. 纠正内容与兼容性

推进工具invocation准备前，审阅发现既有reserveBudget先INSERT/冲突等待budget再UPDATE run，与run→budget路径存在反向等待风险。本步骤先独立纠正原预算方法，没有实施invocation。

`JdbcAgentLedgerRepository`新增package-private `lockRunForBudget(actor,runId)`：按tenant_id、subject_type、subject_issuer、subject_id、run_id五项完整namespace执行`SELECT run_id ... FOR UPDATE`，恰好一行才成功。它只锁身份，SQL没有status、lease owner/epoch/expiry、deadline或cancel谓词。

`AgentBudgetService.reserveBudget`和`settleBudget`进入各自原TransactionTemplate callback后的第一步都调用该锁；缺行按原notFound错误返回，不能继续读私密policy或budget。此后原有效性/成本/额度/context/lease/epoch/unknown/refund/rollback逻辑保持，公共API和事务传播不变。已SETTLED或UNKNOWN的sameSettlement事实重放不因run终态被新身份锁拒绝；身份锁本身不构成live授权。

production差异只有上述repository方法和两个callback首句。原AgentContextBudgetServiceTest只补成功身份锁stub，既有六项断言不放宽。新增LockOrdering8项覆盖reserve新建/重放、settle首锁、SETTLED/UNKNOWN重放、原content/epoch限制、缺身份短路、同源ambient事务加入及exact SQL/no-live。

既有AgentBudgetPostgresTest增加一个20线程同ID混合reserve replay/settle场景，定义一条reservation、精确counters、无死锁/意外SQL错误、run完成后settled replay的检查；该场景只编译而未执行。原测试属性`tianji.postgres.test.url`及安全fixture不变，不加基础设施。

所有SQL文件、配置、依赖、transport和公开预算接口未改。V13 SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际验证

| 阶段 | 实际结果 | 证据 |
| --- | --- | --- |
| 有效RED | exit1；LockOrdering8项全部失败、Context6项通过；0错误、0跳过 | `/tmp/tianji-budget-lock-red.log`；最小repository声明供编译后，失败是缺失run-first锁/SQL行为，不是fixture错误 |
| 最终GREEN | exit0、BUILD SUCCESS，22.932s；72通过、0失败、0错误、7跳过 | `/tmp/tianji-budget-lock-green.log`；全部7项skip属于未提供显式数据库的PG类 |
| 源码及独立审阅 | 两production最小diff、原语义检查；无阻断 | 当前source/实际日志已核；主代理完成all-writers独立审阅及最终门禁 |

实际最终命令：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,AgentBudgetPostgresTest,ToolActionIntentStoreTest,ToolCapabilityStoreTest,TianjiToolGrantIssuerTest,TianjiToolGrantAuthorizerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 模块/测试 | 实际结果 | 证明与限制 |
| --- | --- | --- |
| AR：LockOrdering8、Context6、IntentStore10、CapabilityStore12 | 36通过 | 新首锁/source/终态重放、原context/工具存储兼容；受控事务/JDBC |
| AR：AgentBudgetPostgresTest | 7跳过 | 已编译，未提供现有显式PG属性；不证明真实混合并发、锁等待或数据库回滚 |
| AS：ToolIssuer17、ToolPDP19 | 36通过 | 工具owner签发/PDP适配调用者兼容，受控SDK |
| 合计 | 79项：72通过、7跳过 | 0 failure/error；非整个仓库或平台运行验收 |

日志中既有rpc-tianshu的3项Jackson2 deprecation与Mockito动态agent提示不是本改动引入，未被伪报为零warning。受控Connection/Spring事务证明调用顺序、commit/rollback请求及共享事务行为，不能替代真实PostgreSQL原子性或死锁实验。临时日志可能清理，长期证据是持久测试/实现。

## 3. 要求追踪与后续边界

| 要求 | 状态 | 本步骤证据 | 未扩大范围 |
| --- | --- | --- | --- |
| PLAN-REQ-001 run→budget统一顺序 | Satisfied | 两callback第一repository动作、scoped FOR UPDATE、新8项顺序测试 | 不建立锁管理器或新执行引擎 |
| PLAN-REQ-002 完整身份/no-live/重放 | Satisfied | 五namespace参数、缺行短路、SETTLED/UNKNOWN replay与原epoch/content限制测试 | 身份锁不替代原live权限 |
| PLAN-REQ-003 原预算语义和验证 | Satisfied | 原Context6及工具caller回归、新PG场景已编译 | 真实PG concurrency为Runtime unverified |
| PLAN-REQ-004 聚焦纠正与证据 | Satisfied | 八条路径，无SQL/config/deps/transport/invocation生产修改 | 仅前置纠正提交 |
| 完整T3-07/平台蓝图 | Partial | 关闭invocation推进前的既有锁序缺口 | invocation/audit-budget准备、native/dispatch/approval及真实环境验收尚未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence Service/Repository、八路径 | 保持原技术账本/事务结构，无新层 | None |
| MC-REUSE-001 | Applicable | PASS | 原JdbcTemplate/TransactionTemplate/actor/budget值和notFound映射 | 最小复用现有row锁能力，不复制基础设施 | None |
| MC-DEP-001 | Applicable | PASS | 无POM/build变化，原JDK/Spring/测试依赖 | 未增加工具或依赖 | None |
| MC-NAME-001 | Applicable | PASS | lockRunForBudget、AgentBudgetLockOrderingTest | 方法和测试职责明确，旧类型不变 | None |
| MC-VALID-001 | Applicable | PASS | exact owner SQL、缺行先notFound、后续原validLease/epoch/bounds保持 | 身份锁非权限；原输入/业务有效性不弱化 | None |
| MC-MODEL-001 | Not applicable | N/A | 未改模型/实体/DTO/record | 无Lombok/值对象分类变化 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层映射 | 不绕过MapStruct/BaseConverter或新增模型层 | None |
| MC-LOG-001 | Not applicable | N/A | 原技术JDBC类，无日志改变 | 不新增业务日志或私密输出 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/构造器/config变化 | 无Qualifier或注入新适用面 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK/Spring/JUnit/Mockito | 无utility库或锁框架新增 | None |
| MC-JSON-001 | Not applicable | N/A | 无JSON/codec/协议/enum改动 | 序列化/持久编码不变 | None |
| MC-TIME-001 | Not applicable | N/A | 身份SQL无时间谓词；原时间类型/期限/精度不变 | 不重定义deadline/lease/Clock/zone，原live检查保持 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/flag修改 | 配置键不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 简单scoped row锁和两个首句，原repository复用 | Simple直接实现，不造锁服务/策略层 | None |
| MC-SCOPE-001 | Applicable | PASS | 八路径、V13摘要不变、invocation草案仓库外 | 专门纠正提交，无业务功能/SQL/transport扩张 | None |
| MC-TEST-001 | Applicable | PASS | 有效RED8失败、最终72通过/7明确PG skip，原context/caller回归 | 非运行门禁完成，真实PG不伪报通过 | None |
| MC-BLOCKER-001 | Applicable | PASS | 各独立行、最小实际diff与独立审阅无阻断 | 当前纠正可提交，后续invocation/运行验收仍待办 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | lockRunForBudget、新LockOrderingTest；原Service/Repository | 声明/调用者编译，8项新unit | 语义命名，无新carrier | None |
| Rule 2 | Applicable | PASS | 全namespace身份锁、原域值/lease/budget验证继续保留 | 缺身份/原content/epoch/old context正反测试 | 无新HTTP或模型handoff，旧校验不弱化 | None |
| Rule 3 | Not applicable | N/A | 未修改POJO/record/实体/Converter | 最小production diff | 无模型或转换改变 | None |
| Rule 4 | Not applicable | N/A | 原manual技术JDBC类，无业务Bean/DI/日志修改 | 当前scope/diff | 不伪造新增业务Service/Lombok DI合规声明 | None |
| Rule 5 | Applicable | PASS | 原JDK/Spring/测试imports，无POM变化 | source reactor编译和范围审阅 | 无工具库/框架引入 | None |
| Rule 6 | Not applicable | N/A | 无JSON/协议/枚举持久编码修改 | 实际source diff | Jackson/原格式保持 | None |
| Rule 7 | Not applicable | N/A | 无配置/profile文件变化 | 八路径审阅 | 配置键结构不变 | None |
| Rule 9 | Applicable | PASS | 一条身份锁/两个事务首句 | reserve/settle first-order及caller回归 | Simple直接复用，不增锁抽象层 | None |
| Rule 10 | Not applicable | N/A | 不新增时间字段/谓词/Clock/转换 | Repo SQL no-live断言及原规则保留 | 期限/精度/时区合同不变 | None |
| Rule 11 | Applicable | PASS | 原AR结构/事务传播/SQL资源保持 | 相关72项通过、V13仍f0c642…26b3 | 不扩schema/框架/模块或invocation实现 | None |

## 6. 处置与未执行事项

Pending invocation草案已移至`/tmp/tianji-invocation-preparation-plan-draft.md`，不是本纠正提交路径；本提交后才恢复并更新baseline/精确API。没有任何invocation生产实现混入。

未新增scratch harness或仓库scripts/work脚本；文档生成脚本已删除，持久回归留在现有/新增测试类。未自动启动应用、数据库、容器、浏览器/GUI、native scheduler或长期runtime；未运行真实HTTP/IdP/PDP/crypto/PG环境、部署、push、PR或release。真实PG死锁场景既未作为RED复现，也未作为GREEN验收。

本报告作者只创建该证据文件，不改代码/README/plan/SQL、不提交。主代理完成最终八路径stage检查后产生单一纠正commit；旧历史保留，不rewrite。

**PARTIAL — Spec requirements are unmet or unverified**：当前预算锁序纠正通过非运行验证，完整T3-07及平台运行验收仍未完成。
