# T3-07 工具调用准备、审计与同事务预算执行证据

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-31，Asia/Shanghai |
| 执行计划 | [工具invocation准备](../plan/2026-10-31-22-01-tool-invocation-preparation.md)，Created22:01/Updated22:22；strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/12/13、T3-07 |
| 主设计实施前SHA-256 | `dc980761d6c60b0dfb803b0f3e4c26edf3f8b509314df7ddf138a6ff66a23eb8` |
| Baseline | `431dfb1f7d33ecf3c0704a8cb0d694ca4352500e`（预算run-first前置纠正已提交） |
| 提交 | 主代理完成最终12路径diff/stage门禁后产生；不预填hash，以Git/交付消息为准 |
| 结果 | 当前未接线准备库及相关非运行回归通过；真实PG/业务执行未验收，完整T3-07仍Partial |

## 1. 实际实现

新增`ToolInvocationStore`及package-private `ToolInvocationSql`，构造时要求ledger/capability使用同一DataSource和ledger对象，拥有独立verified flag；schema由原共享verifier只读验证。`PreparationRequestVO`复用原Binding/lease/descriptor，按固定input schema归一参数；READ_ONLY禁止action/intentExpiry，write要求完整匹配并重建原ToolActionRequest。host UUID生成`tool:UUID`预算ID。统一parameters SHA域为`TIANJI_TOOL_INVOCATION_PARAMETERS_V1`，绑定descriptor key/version/full ref hash及归一参数，明确不同于原action.requestSha256，不改变其业务幂等namespace。

整个入口以最初wall/nano开始至多10秒窗口：先事务外读取capability E1，再按E1 expiry缩短原窗口，不能重启预算；独立`authorization.check(PREPARE)`必须echo完整请求、边界和真实非null Audit ALLOW。grant匹配E1，checkedAt在entry start至当前wall内，audit覆盖短请求截止且不超过sealed expiry。旧authorize-only兼容ALLOW/audit=null不能准备调用。拒绝/未知/错proof不打开准备事务。

owned10秒READ_COMMITTED事务按run FOR UPDATE→capability E2→intent→invocation/budget顺序执行；工具SQL参与者设置5秒query timeout，原预算查询沿用外层事务的剩余时限。锁后及返回前重核actual live lease/owner/epoch/cancel/deadline、固定snapshot/tool/policy和DB时间；E1/E2完整相等包含cipher与issue epoch。原capability新增`currentInTransaction`只允许verified、actual transaction绑定exact DataSource；公共current仍拥有独立事务和原语义。事务内不发RPC。

原intent参与者新增`prepareWithStatus`，从同一advisory/row锁下的实际INSERT产生newlyCreated，旧prepare委托.row、旧current行为保持，避免预读竞态。exact现存invocation先重放，不再次reserve或重写audit/deadline；只有无inv时，write遇任何既有action状态都返回原metadata事实，不新inv/预算，包含pending/AWAITING/terminal/DISPATCHING/UNKNOWN/expired。first expiry和origin始终保持。

新调用使用原`ledger.budgets()`同事务预留TOOL预算，要求返回newlyCreated=true，不能给孤立旧budget补造invocation。READ_ONLY/新IDEMPOTENT调用保存PREPARED并持有预算；新APPROVAL调用保存已finished的AWAITING_APPROVAL、空typed outcome/actualCost0，并在同一事务settle(0L,0L,ZERO)，没有external call或审批消费。插入失败导致intent、预算、counters及inv共同rollback；受控Connection只证明rollback调用和顺序，真实数据库原子性另验。

持久`dispatchNotAfter`是DB createdAt加可信effectivePolicy.toolTimeout，再取run deadline、E2 expiry、write action expiry最小值，截MICROS，首次插入及提交前必须仍在DB当前时间之后。它独立于短PREPARE审计窗口；未来fresh DISPATCH才覆盖真正调用截止。重放保留原created/audit/action/deadline，即便原write intent或inv deadline已过，也只返回事实、不延期、不新预算。

parser只接受当前实际writer拥有的PREPARED/AWAITING_APPROVAL，严格核owner/run/host UUID、descriptor/hash/参数SHA/risk/intent/cap epoch、完整budget及audit/outcome。dispatch epoch必须0，nonce/executor/dispatched/receipt/error全null，side effect NONE；其他inv状态failclosed，未推测实现未来状态。结果只有十项调用metadata（含runId、newlyCreated）及可选旧action metadata，不返回args/cipher/audit payload，不是dispatch permit。

原`ToolExecutionJsonCodec`增加audit读写，复用既有JavaTimeModule依赖和Jackson：exact十一键、每个nullable slot也必须存在、4KiB、duplicate/unknown/trailing/coercion拒绝、canonical UUID及Instant UTC Z文本、严格数值/enum/null组，错误固定消息无正文/cause。原descriptor/args/outcome的128KiB合同保持。

没有修改迁移SQL、配置、AS装配、权限、依赖或native guard；新库未注册进业务执行入口，没有dispatch/approval消费。唯一V13摘要仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际验证与失败分类

| 阶段 | 实际结果 | 解释 |
| --- | --- | --- |
| codec/cap声明后的RED | 26项：22通过、0 assertion failure、4 Errors，exit1 | `/tmp/tianji-invocation-codec-cap-red.log`：Codec12中3项、Cap14中1项直接命中明确未实现stub；非编译/环境/fixture错误，不能写成0 Errors |
| Store声明后的RED | Store17：8 assertion failures、7 prepare-stub Errors、2通过；其余Cap14/旧Intent10通过 | `/tmp/tianji-invocation-store-red.log`；该轮Codec12另有1项标量fixture失败，不能把其结果算GREEN或有效实现RED；全轮53项9 failures/7 Errors |
| codec标量fixture修正 | 最终Codec12通过 | 测试用BigDecimal1.0构造节点后被writer正规化成JSON整数1，未真正送出预期非法标量；修fixture，不放宽production parser |
| 初始全组GREEN尝试 | AR96项：0 failures、1 Error、1 PG skip，exit1 | `/tmp/tianji-invocation-preparation-green.log`；mock查询忽略invocation UUID，给新ID误返回旧行；修fixture过滤，没有降低production重放校验 |
| 第一次完整GREEN | 114通过、0 failures/Errors、1 PG skip，exit0，20.613s | `/tmp/tianji-invocation-preparation-final.log`；尚未包含最后同方法内的fixture fidelity/过期事实断言 |
| 最终权威GREEN | 114通过、0 failures、0 Errors、1 PG skip；exit0、BUILD SUCCESS，19.828s，22:51:27结束 | `/tmp/tianji-invocation-preparation-verified.log`；包含mock按runId+UUID过滤及已过期write intent/inv deadline精确重放断言，仍17项Store测试，无production变化 |

最终复跑选择：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=ToolInvocationStoreTest,ToolCapabilityStoreTest,ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolExecutionStorageVerifierTest,ToolExecutionJournalPostgresTest,AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,ToolExecutionContractTest,ToolExecutionGrantTest,TianjiToolGrantAuthorizerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 模块/测试 | 实际结果 | 证明范围 |
| --- | --- | --- |
| AR新/扩展：InvocationStore17、CapabilityStore14、JsonCodec12 | 43通过 | prepare/同事务cap/audit严格表示和原兼容 |
| AR既有：IntentStore10、StorageVerifier13、BudgetLock8、ContextBudget6、Grant8、Contract7 | 52通过 | 旧source/intent/预算锁序/typed权限兼容 |
| AR：ToolExecutionJournalPostgresTest | 1跳过 | 新fresh-run helper已编译，不证明真实SQL/锁/并发/trigger联合回滚 |
| AS：ToolGrantAuthorizerTest | 19通过 | 真实typed工具授权port适配兼容，受控SDK |
| 合计 | 115项：114通过、1跳过 | 非整个仓库、真实服务或平台验收 |

原opt-in PG类扩展独立fresh run：QUEUED cap reserve/attach→claim→20相同host UUID prepare，定义一条inv/预算/精确counters、replay deadline固定、drift冲突，以及fixture trigger强制inv INSERT失败后intent/budget/inv/counters联合rollback。仍沿原显式tool URL和固定预装vector patch前置，未提供数据库所以跳过；没有启动PG。

已核当前实际源码、上述日志和V13摘要；独立实际API审阅无production阻断。临时日志可被系统清理，持久测试/实现是长期证据。受控SDK/JDBC不证明真实IAM、密文或数据库事务。

## 3. 局部要求与未完成边界

| 要求 | 状态 | 本步骤证据 | 未扩大的边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 typed权限/fence | Satisfied | exact check/Audit ALLOW、E1/E2完整比较、事务外RPC与锁后run来源测试 | metadata不认证，真实IAM/crypto另验 |
| PLAN-REQ-002 原子预算/receipt | Satisfied | host UUID、newlyCreated budget强约束、sameTX、精确重放/rollback受控测试 | 真PG atomicity为Runtime unverified |
| PLAN-REQ-003 风险/旧事实 | Satisfied | PREPARED holds预算、新AA finished/known-zero、所有旧action facts-only、过期精确重放 | 无审批消费、dispatch或自动retry |
| PLAN-REQ-004 闭合审计 | Satisfied | exact11key/4KiB/canonical UTC Z/type/null/UUID/秘密测试 | 未放宽旧格式或真实证明audit来源 |
| PLAN-REQ-005 测试/范围 | Satisfied | 最终114通过+1显式PG skip、12路径、V13固定 | native/AS/config/权限不启用 |
| 完整T3-07/平台蓝图 | Partial | 当前为未接线调用准备与预算事实 | 真实业务调用、审批消费、dispatch/native、PG/IAM/crypto及后续阶段未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence库、12路径、原三表/预算API | exact边界保持，不新增执行引擎/层 | None |
| MC-REUSE-001 | Applicable | PASS | 原ledger.budgets、cap/intent/Grant/typed check、原Jackson | 不复制预算/认证/序列化基础设施，保留431dfb1锁序前置 | None |
| MC-DEP-001 | Applicable | PASS | JavaTimeModule已在runtime依赖，POM未改 | 无新库或框架 | None |
| MC-NAME-001 | Applicable | PASS | Store/Sql、PreparationRequestVO/Result/PreparedInvocationResult、Test | 技术角色/不可变值命名明确 | None |
| MC-VALID-001 | Applicable | PASS | schema归一/action重建、exact Audit/grant/window、E1/E2、strict stored row/budget/outcome | 现有私有源/协议/SQL handoff正反验证，无新HTTP DTO | None |
| MC-MODEL-001 | Applicable | PASS | 嵌套records及primitive immutable map、原域值复用 | 真不可变值，非实体/可变POJO；结果metadata-only | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层DTO映射 | codec和行重建为私有持久表示，不另建绕过BaseConverter的映射层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术JDBC库，无新业务日志Service | 错误固定脱敏，结果不泄私密body/cipher/audit payload | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/AS/config注册 | 原技术库manual构造模式保持，无Qualifier新面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Spring/原摘要/预算/Jackson | 未新增utility/线程/执行框架 | None |
| MC-JSON-001 | Applicable | PASS | 原codec+既有JavaTimeModule、exact11keys/4KiB | duplicate/unknown/coercion/trailing/UTC/UUID/null严格，旧格式兼容 | None |
| MC-TIME-001 | Applicable | PASS | 原entry wall+mono缩窗不reset、DB锁后live lease、trusted toolTimeout/run/E2/action min、MICROS | 准备proof与真实调用deadline分离；已过期replay不延原fact | None |
| MC-CONFIG-001 | Not applicable | N/A | 12路径无profile/properties/flags | 未接线库不自动启用、配置结构不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 权限依赖倒置和同源transaction participant、原budget复用 | 实际变化点隔离，不造State/调度/第二执行器 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确12路径、V13固定、仅原PG类扩helper | 无AS/native/SQL/权限/依赖扩张；有限PREPARED/AA parser | None |
| MC-TEST-001 | Applicable | PASS | 真实stub/断言RED如实分类、最终114通过+1PG skip、fixture fidelity和过期重放断言 | 非运行证明完成，真实PG不伪报 | None |
| MC-BLOCKER-001 | Applicable | PASS | 最终源码/日志及独立审阅无production阻断、各行完整 | 当前库slice可提交，完整任务和runtime待办明确 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | InvocationStore/Sql、各VO/Result、原codec/cap/intent/Test | 类型/调用者编译和最终定向GREEN | 职责后缀清楚，旧公共方法保持 | None |
| Rule 2 | Applicable | PASS | request schema/Action重建、typed proof/E1E2/owner/lease/预算shape | 新Store17/Cap14/Codec12及旧52回归 | 精确私有domain/协议/SQL边界校验，无新HTTP传输模型 | None |
| Rule 3 | Applicable | PASS | 新不可变request/results/内部状态值，primitive map归一复制 | 构造/错误输入/metadata投影/重放测试 | 真值records而非实体，无额外Converter层 | None |
| Rule 4 | Not applicable | N/A | 新/改类为原模式manual技术JDBC/codec库，无业务Bean/DI变化 | 当前12路径及source审阅 | 不伪造业务Service/Lombok注入合规声明 | None |
| Rule 5 | Applicable | PASS | JDK/Spring/原Jackson/摘要/预算，POM不变 | imports/源码编译/范围检查 | 无工具库或依赖新增 | None |
| Rule 6 | Applicable | PASS | Boot-managed Jackson与既有JavaTimeModule；固定受约束状态字符串 | audit12项整体codec、strict row/outcome校验 | 无Gson/新JSON栈/ordinal编码/手工跨层JSON映射 | None |
| Rule 7 | Not applicable | N/A | 不改配置/profile/flag | 当前范围审阅 | 配置键结构保持 | None |
| Rule 9 | Applicable | PASS | 原权限port依赖倒置、SQL transaction participants、原budget | RPC在TX外、run-first/cap/intent/inv/budget、原子性测试 | 复用真实变化点，不加引擎/管理器层 | None |
| Rule 10 | Applicable | PASS | Instant/Duration、Clock/nano/DBtime、canonical UTC Z、微秒deadline | audit/短窗/lease/独立toolTimeout/过期replay边界 | 无默认zone/java.util日期，原fact期限不延长 | None |
| Rule 11 | Applicable | PASS | 原AR边界和冻结V13/原预算服务；有限状态parser | 相关114项通过，V13仍f0c642…26b3 | 无schema/AS/native/permission/dependency扩张 | None |

## 6. 未执行事项与处置

新准备库未接AS配置/native工具入口，当前没有dispatch方法、业务transport、审批receipt消费或后续invocation状态writer。PREPARED/AA是事实，不能由调用者将其当当前dispatch授权；未来必须fresh DISPATCH覆盖真实call deadline。真实PG语法/catalog、并发/锁、trigger联合rollback、IAM/crypto和外部业务结果均未验收。

没有新仓库scripts/work或未定验证harness；文档生成脚本已清理，测试留在对应持久类/原opt-in类。没有自动启动应用、数据库、容器、浏览器/GUI、native scheduler或长期runtime；未激活配置/权限/角色、部署、push、PR或release。

本报告作者只创建该证据文件，不改代码/README/plan/SQL，不运行构建或提交。主代理完成最终12路径diff和显式stage审查后提交，hash以Git结果为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前调用准备/审计/同事务预算库通过非运行验证，完整T3-07及平台验收仍未完成。
