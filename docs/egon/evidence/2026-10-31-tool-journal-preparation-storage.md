# T3-07 工具凭据与动作准备存储执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [工具journal准备存储](../plan/2026-10-31-18-39-tool-journal-preparation-storage.md)；严格结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/13/14、§19 T3-07 |
| 主设计实施前 SHA-256 | `e6a93f4bfd144066eefaa152a18523d70860c092c52a353e996c11c4e02ebb0e` |
| Step baseline | `2c0fc5e9cf19c3774ad086135b0c90eb30c02f86` |
| 本步骤提交 | 由主代理完成本报告后的显式路径提交；本报告不预填尚未产生的hash，最终hash以Git及交付消息为准 |
| 实现边界 | 一份V13、共享schema verifier、capability reserve/attach/current、当前host-authorized action prepare/current；纯库API，无运行装配 |
| 结果 | 本切片非运行验证通过；完整T3-07仍为Partial，真实PostgreSQL/权限/密文及工具执行未验收 |

## 1. 实际实现与关键约束

`ToolExecutionStorageVerifier` 验证V1—V13全部13条已知历史的script/type/checksum/success及三表精确合同。`agent_tool_capability`、`agent_tool_action_intent`、`agent_tool_invocation` 分别为27/43/34列、5/11/10个命名CHECK，共26个CHECK及14个索引；还检查PK/UNIQUE/FK、循环backlink、列型/default/C collation、索引谓词/opclass/collation、validated/enforced、table kind/inheritance、RLS/trigger/必要权限。校验器只发SELECT和会话局部设置，不执行DDL/DML或修复历史。它拥有15秒REPEATABLE_READ事务、5秒query timeout，并要求UTF8 primary/origin。

这里的“只读verifier”指查询行为，不是JDBC强制readOnly标志：实际代码显式`setReadOnly(false)`，测试确认不调用`Connection.setReadOnly(true)`，以保持既定主库语义。受控catalog不证明真实PostgreSQL的`pg_get_expr`渲染、SQL语法或锁行为。

`ToolCapabilityStore` 要求与`AgentLedgerService`使用同一DataSource对象、先完整verify、拒绝ambient事务。它拥有10秒READ_COMMITTED事务、5秒query timeout，按source行FOR SHARE→capability槽顺序，校验完整owner/run/source六元组、固定descriptor/ref/backend、snapshot tools、allowlist、tool预算与审批策略。reserve/attach仅QUEUED；current支持QUEUED/RUNNING。一个稳定槽保留不可变绑定，issue epoch阻断迟到签发结果，活票据阻止重复签发，存储前截断微秒并在锁后/返回前核DB时间。它只存结构化密封证据，不签发grant、不验证真实密文、不授工具权限。

`ToolActionIntentStore` 每次prepare和current均先在事务外调用现有`ToolExecutionAuthorization`的PREPARE。请求绑定完整run/lease、固定tool/backend、`action.requestSha256()`及稳定intent，窗口为`min(run.deadlineAt, Clock.now+10s)`；同时使用单调时钟预算，wall clock回拨不能延长。host必须独立验证真实工具权限，并保证ALLOW票据窗口覆盖请求deadline，否则返回UNAVAILABLE；本步骤提供这个端口合同及受控实现测试，没有提供真实签发/密文/Jianshen适配器。

其内部`ToolActionIntentSql`在同源owned事务中按source行FOR SHARE→稳定intent advisory→intent行顺序工作。锁后重新读取/检查实际`lease_expires_at`及DBclock、owner/epoch/cancel/run deadline和固定策略；Java lease的初次expiresAt只是可能已被heartbeat续期的hint，不能误当当前数据库值。结果是`PreparedActionResult`不可变元数据投影，不含arguments、descriptor或ciphertext，也不是dispatch permit。

业务逻辑身份SHA使用独立版本域与完整owner/action/businessKey/businessVersion；同时重建并比较原action UUID、完整身份/参数/工具/策略字段，避免截短UUID碰撞或改变内容形成第二事实。跨run同owner同intent可以返回原事实，原expiry和origin不变；即使事实过期也不会偷偷续期、自动改EXPIRED或取得重试权限。新expiry最长七天并在写前截到微秒。

唯一新SQL是`V13__add_tool_execution_journal.sql`，SHA-256：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

旧12份Agent迁移字节冻结测试通过；另将当前仓库全部21份已跟踪迁移逐一与baseline HEAD的Git内容作字节比较，均相同。schema一次承载后续调用/审批receipt关系，但当前没有invocation写入、审批消费、派发或取消/对账执行入口。AWAITING_APPROVAL调用已结束、零dispatch且无副作用，动作事实仍等待；UNKNOWN不授重派。永久技术幂等键不软删除释放，无bearer或SecurityContext持久化，无级联删除。原蓝图授权的独立JDBC/Flyway技术历史保持，没有新增迁移框架、依赖、配置、权限或endpoint。

## 2. 实际验证

本轮复用仓库`./scripts/verify-source-reactor.sh`，在JDK25及既有同级源码依赖下编译并运行定向测试。可复跑的测试选择如下；机器仓库/settings参数由执行环境提供，不修改用户全局配置。

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=ToolExecutionStorageVerifierTest,ToolCapabilityStoreTest,ToolActionIntentStoreTest,ToolExecutionJournalPostgresTest,ToolExecutionGrantTest,ToolExecutionJsonCodecTest,ToolExecutionContractTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

最终日志`/tmp/tianji-tool-storage-final.log`实测`BUILD SUCCESS`、命令exit0；共64项：63通过、0失败、0错误、1项PostgreSQL测试类跳过。

| 测试类/范围 | 实际结果 | 证明与限制 |
| --- | --- | --- |
| ToolExecutionStorageVerifierTest | 13通过 | 三表合同、26个CHECK、全部历史/索引、漂移、布尔/namespace、query-only primary、冻结摘要；受控catalog |
| ToolCapabilityStoreTest | 12通过 | 同源/verified/ambient、epoch/绑定、QUEUED/RUNNING、审批策略、锁后expiry、微秒、SERVICE结构与私密错误 |
| ToolActionIntentStoreTest | 10通过 | 事务外当前PREPARE、双时钟、续期旧hint、跨run重放、漂移/过期事实、锁后租约/取消、rollback与私密source失败 |
| 原工具域/codec | 28通过 | Grant 8、ApprovalBinding 3、ExecutionContract 3、ActionRequest 4、Registry 5、JsonCodec 5；兼容回归 |
| ToolExecutionJournalPostgresTest | 1跳过 | 已编译，未提供显式数据库；不证明真实SQL/目录/原子性/并发 |

真实RED与修正记录保留，失败不冒充通过：

| 阶段 | 实际结果/日志 | 处理 |
| --- | --- | --- |
| 确认后的初始行为RED | 24项，23失败、0错误；`/tmp/tianji-tool-storage-red-confirmed.log` | 最小类型声明可编译后观察缺失行为。更早`red.log`有一项测试缺`assertNotNull`导致NPE，修正fixture后才形成此有效RED |
| source私密异常RED | 2项，2失败、0错误；`/tmp/tianji-tool-source-privacy-red.log` | malformed ledger snapshot的IllegalState/Jackson cause可能外溢；两个Store在source读取边界固定脱敏，最终回归通过 |
| 早期全GREEN尝试 | 62项，0失败、2错误、1跳过；`/tmp/tianji-tool-storage-green.log` | verifier正向fixture把真实PG NULL索引predicate写成空串，修正fixture而没有放宽production合同；不能称这一轮通过 |
| EXPIRED SQL-NULL边界RED | 12项，1失败、0错误；`/tmp/tianji-tool-verifier-expiry-red.log` | 暴露`last_error_code=NULL`可能因SQL UNKNOWN绕过EXPIRED组；V13冻结前增加显式IS NOT NULL，最终13项verifier测试通过 |
| 最终定向验证 | 64项，63通过、0失败、0错误、1跳过；`/tmp/tianji-tool-storage-final.log` | 上表最终结果，V13最终摘要固定后再通过 |

临时日志可能被系统清理；可复跑的长期证据是仓库中的测试、V13及其冻结SHA，而不是`/tmp`永久可用性。受控JDBC用真实Spring本地事务绑定及mock Connection验证commit/rollback请求和调用顺序，不能据此证明真实数据库执行/回滚。

## 3. 原设计符合性

| 要求 | 本切片状态 | 实现与验证证据 | 剩余边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 固定工具与凭据绑定 | Satisfied | ToolCapabilityStore及12项受控测试；Grant/codec回归 | 真实签发/密文/current Jianshen仍待集成 |
| PLAN-REQ-002 当前host授权与fencing | Satisfied | IntentStore/Sql、port Javadoc、事务外精确请求/双时钟/live lease测试 | ALLOW有效窗口是host合同；真实authority adapter不在本切片 |
| PLAN-REQ-003 稳定意图与安全事实重放 | Satisfied | logical SHA/完整action重建、元数据Result、跨run/内容冲突/原expiry测试 | 无审批消费、业务执行或重试许可 |
| PLAN-REQ-004 唯一schema和只读校验 | Satisfied | V13、共享verifier、13项静态/受控catalog测试、旧12冻结 | 真实SQL/catalog/锁验收为Runtime unverified |
| PLAN-REQ-005 测试与诚实运行边界 | Satisfied | 有效RED、最终63通过+1显式skip、PG类已编译 | 全平台S07/S10运行验收尚未完成 |
| 完整T3-07 | Partial | 本轮仅凭据/prepare/current库入口 | 真实grant签发/密文/fresh权限、审批消费、invocation写入、业务Process/DMN工具调用、dispatch/ADK挂载仍未交付 |
| 完整平台蓝图 | Partial | 只继续当前T3-07里程碑 | 后续阶段及真实环境验收保持原计划待办 |

## 4. Blocking Manual Check 最终矩阵

以下均按当前切片实际源码和上述最终定向验证评估；PASS不扩展为真实运行验收。证据中的文件名位于同模块`infrastructure/persistence`，除明确的域port与测试类外。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原蓝图§6、实际AR包边界；两个Store/participant/verifier均在既有基础设施 | 技术账本保持同源库架构，不新增执行引擎/层 | None |
| MC-REUSE-001 | Applicable | PASS | AgentLedgerService、ToolExecutionGrant/ActionRequest、既有codec/digest、MemoryStorageVerifier.contract/booleanSignature/checksum | 复用已有来源/JSON/摘要/事务/解析能力 | None |
| MC-DEP-001 | Applicable | PASS | 本切片无POM或build manifest变化；只用既有JDK/Spring/Jackson/PG测试依赖 | 未添加依赖/工具/框架 | None |
| MC-NAME-001 | Applicable | PASS | ToolCapabilityStore、ToolActionIntentStore/Sql、ToolExecutionStorageVerifier、PreparedActionResult、WindowVO、对应Test | 名称按存储/校验/事实值职责，未新增模糊carrier | None |
| MC-VALID-001 | Applicable | PASS | 域值构造、descriptor/schema/hash重建、完整owner/source/snapshot、SQL合同校验；35项新受控测试 | 技术库及私有SQL表示边界有正反验证，无新增HTTP传输模型或业务层handoff | None |
| MC-MODEL-001 | Applicable | PASS | 现有不可变grant/action；PreparedActionResult/Row/WindowVO不可变值投影；永久技术dedup键 | 无普通可变实体建模；技术键不会软删除释放，无重复POJO层 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无新增Converter/跨层DTO映射；复用既有ToolExecutionJsonCodec | 行重建验证及私有JSON持久表示不是替代MapStruct的跨层转换层 | None |
| MC-LOG-001 | Not applicable | N/A | 新类是手工构造技术JDBC库，沿既有MemoryStores，无新增业务日志 | 非业务Spring服务；异常固定脱敏，不记录参数/cipher/token | None |
| MC-BEAN-001 | Not applicable | N/A | 无stereotype/Bean/autoconfig/DI配置；技术库manual constructor及sameDS检查 | 不增加业务Bean，故无新Bean命名/Qualifier/注入面 | None |
| MC-UTIL-001 | Applicable | PASS | imports仅既有JDK/Spring/领域组件；OrchestrationRequestDigests复用 | 无重复digestUtils/第三方工具库 | None |
| MC-JSON-001 | Applicable | PASS | ToolExecutionJsonCodec复用；V13闭合表示；Result无body；两个source-privacy回归最终通过 | Jackson单栈，无法把proof/audit表示当认证，错误无私密cause | None |
| MC-TIME-001 | Applicable | PASS | Instant/Timestamp/MICROS；wall+mono预算；DB锁后actual lease；clock回拨/续期/expiry测试 | 绝对UTC语义、微秒不向上延、旧hint与live DB值分离，无默认区域计算 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无环境profile/properties/config key/开关修改 | 原配置键和默认运行拒绝保持 | None |
| MC-PATTERN-001 | Applicable | PASS | 现有ToolExecutionAuthorization依赖倒置隔离host当前权限；JDBC Adapter/participant与ledger复用 | 真实变化点沿现有port处理；固定投影直接实现，未新增State/调度层 | None |
| MC-SCOPE-001 | Applicable | PASS | 执行计划13条路径；只当前AR代码/测试/V13/port文档及README/plan/evidence | 不碰AS/ADK/其他仓库，单一逻辑步骤；原SQL草稿授权接管 | None |
| MC-TEST-001 | Applicable | PASS | 最终BUILD SUCCESS，63通过/1显式PG skip；13/12/10新回归及28既有测试；旧SQL冻结 | 所需非运行证据完成，PG/完整S07/S10不伪报 | None |
| MC-BLOCKER-001 | Applicable | PASS | 上述独立行无未解释失败；运行边界在§3/§6明确 | 本切片非运行交付可提交；完整T3-07及真实环境未完成 | None |

## 5. Literal Rule 最终矩阵

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Store/Sql/Verifier、PreparedActionResult/WindowVO与对应Test | 当前类型声明/调用方编译，最终定向BUILD SUCCESS | 技术行为类型和不可变值有明确职责名 | None |
| Rule 2 | Applicable | PASS | domain binding重建、codec schema校验、完整source/lease/policy/SQL shape | 35项新正反受控测试及既有域/codec28项 | 沿既有纯域/技术库边界验证；没有新增HTTP/业务DTO/代理层handoff | None |
| Rule 3 | Applicable | PASS | 新PreparedActionResult/Row/WindowVO为真正不可变值；旧grant/action复用 | 构造/投影/脱敏/反例及编译 | 无普通可变实体、无手写跨层Converter，无需新增Lombok POJO | None |
| Rule 4 | Not applicable | N/A | 手工构造技术JDBC库，非新增业务Bean/Service；原authorizer仅Javadoc | 无Bean/DI/autoconfig变化，源码职责核对 | 沿现有MemoryStore技术库模式；不伪造业务Bean注解合规证明 | None |
| Rule 5 | Applicable | PASS | JDK、既有Spring/Jackson、原domain/digest/parser | imports/POM范围核对，source reactor编译 | 无工具库/框架依赖新增 | None |
| Rule 6 | Applicable | PASS | 复用Boot-managed Jackson codec；SQL状态为固定受约束字符串，不用ordinal | codec5项、source隐私、closed schema测试 | 无Gson/Fastjson或对象映射JSON技巧；proof表示不授权限 | None |
| Rule 7 | Not applicable | N/A | 不编辑任何配置/profile文件 | 当前计划/变更路径核对 | 环境配置结构没有改变 | None |
| Rule 9 | Applicable | PASS | ToolExecutionAuthorization现有依赖倒置、JDBC Adapter/participant | 事务外host调用与同源参与者受控测试 | 沿已有实际变化点，未新增通用执行器/状态层 | None |
| Rule 10 | Applicable | PASS | Instant、Duration、Timestamp边界、Clock/LongSupplier、DBclock/MICROS | wall回拨+mono10s、续期旧hint、锁后expiry、微秒测试 | 无java.util日期类/隐式business zone，保留run绝对deadline | None |
| Rule 11 | Applicable | PASS | 原蓝图已授权天机技术ledger架构、唯一V13、既有source复用 | 旧12迁移freeze通过；shared verifier/all-history合同；无层/POM/事件/启动变化 | 保留用户明确Flyway不可变及exact同源JDBC选择；本任务不声称迁移成MP-SDJ | None |

## 6. 未完成与运行验收边界

- `ToolExecutionJournalPostgresTest`只编译并默认跳过。实际执行需要操作方预先提供独立空loopback `tianji_it_*`数据库及预装pgvector，显式传入`tianji.tool.journal.postgres.test.url`和固定patch的`tianji.tool.journal.postgres.vector.version`；测试不启动数据库。真实V12→V13、catalog渲染、SQL NULL/CHECK行为、长UTF8键、联合回滚、并发重放和锁竞争尚未证明。
- host授权回调在受控测试中提供。真实Shoubing/Jianshen独立工具grant签发、密文解封与exact Expectation/current权限窗口没有交付，不能把metadata、结构化票据或构造Result当认证。
- 没有invocation写API、approval receipt消费、预算结算/执行回执、真实Process/DMN业务工具、dispatch/reconcile或ADK工具挂载。现有默认工具拒绝没有放开。
- 没有自动启动应用、数据库、容器、浏览器、长期runtime或真实模型/MCP；没有权限注册/激活/角色授予、部署、push、PR、merge或release。

## 7. 原始文件与验证harness处置

用户授权的三个原始未跟踪文件已备份到仓库外`/tmp/tianji-tool-journal-original-b9ee900`，继续保留为原始工作保护；它不是测试harness，也不提交。前两份codec文件已随前序步骤归档，当前Sql草稿在本步骤完善。

临时rootScopeParsedProbe的源码/classes已由其创建方以tempfile生命周期清理。文档生成脚本已删除；没有在仓库`scripts/work/`新增验证脚本。外部review draft/contract的设计已提升为最终V13与仓库持久schema/冻结测试，属于已promoted的审阅输入，外部副本只保留为临时设计参考且不纳入提交，不是待定验证脚本或唯一证据。受控测试helpers属于各自持久测试类，不新增testkit。

交付使用计划列出的13条显式路径；提交前核对全体diff、旧SQL字节、staged文件清单和格式，提交后以Git记录核对hash和文件列表。

**PARTIAL — Spec requirements are unmet or unverified**。该结论针对完整蓝图和T3-07；当前准备存储切片的源代码、受控验证及兼容回归已具备提交证据。
