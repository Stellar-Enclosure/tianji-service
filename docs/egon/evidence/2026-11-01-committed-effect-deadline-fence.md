# 已提交工具副作用观察与截止租约代次隔离执行证据

| 项目 | 当前记录 |
| --- | --- |
| 执行计划 | [committed effect / deadline fence](../plan/2026-11-01-08-51-committed-effect-deadline-fence.md)，Review |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7.2/12/13/17、T3-07 |
| 主设计实施前SHA-256 | `853c0ec63235dbebabbb1d9fe62427f99c8f2d5898f5a18bc3887f87374be2aa` |
| Baseline | `b96f646fbad088b35e9514573ef42b7b784121a9`，实施前clean |
| 计划门禁 | root全文审阅489行，strict PASS、executing15md19refs/writing18md24refs资源与step-start checklist通过，并重读Spec §7.2/12/13；Source GO时plan SHA-256 `e900d38800850bed516c61d0c3b028ec0ca841c64ed7abfb2bf64461e8efc423` |
| Source GO | root已实际发出RED-only GO；当时baseline b96f646仅两份新文档未跟踪，冻结12paths，不等同Accepted或实现验收 |
| 当前阶段 | **Verified（nonruntime，precommit snapshot）**；精确8类105PASS、11 Governance PG skip、0fail/error；真实环境未验收 |
| 提交 | PENDING；root最终精确12paths/cached/Git交付为准，不预填未来commit hash |

## 1. 已验证范围与源码事实

本报告记录已验证precommit snapshot，不把源码/受控测试当runtime验收。精确12paths见计划§5/Step1：新ToolCommittedFactObserver；原ToolInvocationRecorder、ToolResultAccessRecorder、AgentLedgerService、JdbcAgentLedgerRepository；原ToolIdempotentStartRecorderTest、ToolResultAccessRecorderTest、新AgentDeadlineEffectTest、原AgentRunGovernancePostgresTest；README、新plan与本报告。budget owner5、root5、docowner2。

源码确认status-only SAM只有onCommitted(Outcome.Status)，无字段/常量/default/body/receipt/cost/error/nonce/id。旧START两参数/access单参数方法与VO均兼容NOOP，新重载nullobserver必须任何work前沿既有固定IAE拒绝。fresh START仅完整COMMIT返回后，以actualreturnedstatus exact一次通知，再account/outerpostcheck；metadata/early/!started/failedcomplete不通知。Access每次当前获权Store事实return（新/exactexisting）均通知known3，先于account/finalegress。不是业务Event持久投递或权限证明。

源码确认START percall AtomicReference持安全observerFailure。observer Runtime映fixed TIANJI_DEPENDENCY_UNAVAILABLE无cause/private文本，正常withVerifiedCall return后和每outer FixedFailure/Ledger/Runtime catch顶部优先heldfailure，禁止cleanup/account/body/suppression/remap。actual AS port在POST后callbackLedger→UNKNOWN也保留held dependency；holder为空保留旧行为。Access observer失败同安全Dep，无account/egress/body/cleanup。Error不吞为成功、interrupt flag保留。latecallback在owner已返回timeout后才失败不能追溯替换旧error，已提交journalfact仍保留；account可能PENDING，不回滚/降级/重派。

源码确认新deadline Repo overload只在UPDATE WHERE包含exacttenant/type/issuer/id/run/rev/RUNNING/attempt>0/leaseowner/epoch/deadline<=DBnow时生效，无liveleaseexpiry/Java hint/cancel谓词。claim/renew LEAST使lease<=deadline，不能因硬截止hint已过期拒绝当前generation。旧StringrunId overload原样，无row不fallback宽过期。新Service验证非null/runmatch/rev>=0/effect exactCONFIRMED/NONE/UNKNOWN，非法沿现invalid(String)→TIANJI_INVALID_COMMAND/固定安全消息/无cause；不同于observer null的既有IAE。新effect结果、预算UNKNOWN、原statusEvent/outbox在同一TX，CAS缺失为安全REVISION_CONFLICT；旧attemptedrun宽方法仍UNKNOWN。宽reconciler可能先赢CAS并写终态UNKNOWN；新API仅在自身CAS获胜时写入exact effect，不提供终态refinement，也不覆盖先提交终态。

无Executor/plugin/bridge/AS/config/native/effectcounter/resolver/AA/MCP或schema变化；此步仅后续native core先行library seam，不声称Executor已接线。全部23SQL immutable，无新版本/应用DDL/repair/backfill/drop；V13 SHA仍为`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`，V14仍为`88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a`；root最终核23个tracked SQL与baseline逐字节相同。Agent generator-excluded，沿现技术JDBC/独立Flyway，无新依赖/CRUD/MP迁移或新引擎。

root全文阅读5生产文件、4测试文件及README，并分别审阅17MC/10Rules；独立tool_audit_contract对deadline与observer审计均PASS，无实质finding。新API只接收可信caller冻结effect，不自行计算tool账本，后续Executor聚合尚未接线。

## 2. 实际执行记录

| 阶段 | 状态 | 后续真实证据 |
| --- | --- | --- |
| plan结构/资源 | root已PASS并锁定489行 | strict/resources/checklist及Spec重读已完成；08:51文件名与Created一致，原08:50请求为pre-lock机械更正 |
| root全文/source GO | RED-only GO已实际发出 | GO时locked SHA如上；当时只有deadline与Recorder直接UOE/SAM及测试准备，无有效RED或功能实现结论 |
| START/deadline有效RED | 已观察；`/tmp/tianji-committed-effect-deadline-red.log` exit1 | 20.433s，2026-11-01T08:57:50+08:00；31 tests=18PASS、7FAIL、6ERROR、0skip |
| START RED分类 | ToolIdempotentStartRecorderTest 23 tests | 原17PASS；新增3断言FAIL、3直接UOE ERROR，均因最小新overload缺行为，无fixture/compiler/environment问题 |
| deadline RED分类 | AgentDeadlineEffectTest 8 tests | 旧control1PASS；新增4断言FAIL、3直接UOE ERROR，均因最小新overload缺行为；13项missing-behavior failures/errors未与fixture错误混记 |
| 组件GREEN GO | root分别观察有效RED后给START/deadline及Access GREEN GO | 当时才允许START/access实现；root的deadline8定向GREEN，整步结果以后续完整日志为准 |
| Access有效RED/deadline定向GREEN | `/tmp/tianji-committed-effect-access-red.log` exit1；24=18PASS、4FAIL、2ERROR、0skip | 19.632s，2026-11-01T08:59:09+08:00；Access16=原10PASS+新增4断言FAIL/2直接UOE ERROR；deadline8全部GREEN |
| 最终完整GREEN | `/tmp/tianji-committed-effect-deadline-final.log` exit0 BUILD SUCCESS | 25.986s，2026-11-01T09:00:46+08:00；116 total=105PASS、11 Governance PG skip，0fail/error，完整source-reactor成功 |
| 原Governance PG新增2项源码编译 | 第二轮编译时未选中；最终8类selector选中且11项全skip | 原tianji.postgres.test.url/Timeout90保持，缺前置未运行；非真实SQL/锁/事务/并发证明，不混为1skip |
| source/独审/23SQL/12scope | PASS（precommit） | root全文/逐项审查与独审无openblock，23SQL字节相同，strict/resources/diffcheck PASS；最后文档/cached/commit仍由root执行 |

首轮已执行canonical selector为`AgentDeadlineEffectTest,ToolIdempotentStartRecorderTest`，第二轮为`AgentDeadlineEffectTest,ToolResultAccessRecorderTest`；JDK25/settings/literal ADK envfalse/三OTel exporter none/NoSpecifiedFalse沿计划，root运行且docowner未构建。第二轮Access新增缺行为均为stub引发，deadline8定向GREEN；无编译/fixture问题。随后最终精确计划selector已执行：

```text
AgentDeadlineEffectTest,ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest,ToolInvocationRecorderTest,ToolResultAccessStoreTest,ToolExecutionStorageVerifierTest,AgentBudgetLockOrderingTest,AgentRunGovernancePostgresTest
```

最终分类：AccessStore18、BudgetLock8、START23、OldRORecorder15、AccessFacade16、Verifier17、Deadline8，共105PASS；Governance PG11SKIP。原Recorder签名/VO/RO与旧deadlineUNKNOWN保持，新增PG源码未实际运行。root统一canonical命令如下，docowner未构建；有效RED后才实现，不提交stub，无fixture/compiler伪RED：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=AgentDeadlineEffectTest,ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest,ToolInvocationRecorderTest,ToolResultAccessStoreTest,ToolExecutionStorageVerifierTest,AgentBudgetLockOrderingTest,AgentRunGovernancePostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-committed-effect-deadline-final.log 2>&1
```

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | START23/Access16验证actualknown4/known3、exact通知顺序与metadata无通知；旧NOOP兼容 | 本地status观察不是持久Event交付/native接线 |
| PLAN-REQ-002 | Satisfied | observerthrow保留fact/noaccount/body/cleanup；ACK→Ledger→UNKNOWN probe优先heldDep；中断/外层错误矩阵 | latecallback不能追溯改已返回timeout，account可能pending |
| PLAN-REQ-003 | Satisfied | Deadline8捕获fullowner/run/rev/owner+epoch/RUNNING/attempt/DBdeadline SQL，错fence无写，无live/cancel谓词 | 真实PG未运行；宽reconciler先赢可UNKNOWN，不终态refinement |
| PLAN-REQ-004 | Satisfied | 新frozeneffect/预算UNKNOWN/statusEvent同TX、event失败rollback、旧attemptedUNKNOWN及旧VO/RO回归 | 不新增事件格式/自动effect聚合或权限 |
| PLAN-REQ-005 | Satisfied | 有效RED→GREEN、精确8类105PASS+11PGskip，23SQL/12paths/静态/独审 | root最后cached/Git待执行；无runtime验收 |
| 完整T3-07/Goal | Partial | 本步显式先行API已验证 | native core/host聚合、AA/MCP及真实环境后续 |

## 4. Blocking Manual Check

以下逐项为已验证precommit snapshot；source/test/独审/static无openblock，最后文档/cached/Git由root交付，不把源码验证等同runtime验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR application port/原persistence技术类，12paths | 保持JDBC/独立Flyway、Agent generator-excluded；无层/模块/引擎扩展 | None |
| MC-REUSE-001 | Applicable | PASS | 原typedOutcome/complete/account/UnitofWork/CAS/mapper/statusEvent/outbox | 一个最小status观察变化点，复用现事务/结果，不另造模型/引擎 | None |
| MC-DEP-001 | Applicable | PASS | JDK AtomicReference与现Spring/JDBC/domain imports，POM无改 | 无新依赖/构建步骤/脚本 | None |
| MC-NAME-001 | Applicable | PASS | ToolCommittedFactObserver单SAM，现Recorder/Service/Repo兼容overloads | 无字段/常量/default/新DTO，命名对应实际角色 | None |
| MC-VALID-001 | Applicable | PASS | START/Access null-beforework；Deadline effect/run/rev/SQLfullfence tests | actualfactstatus非假成功；invalid沿现COMMAND风格，observer不是auth | None |
| MC-MODEL-001 | Applicable | PASS | 原VO/Outcome不变、status-only接口，既有result仅冻结effect | 无新实体/可变状态/constructor宽松化/正文元数据 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原AgentExecutionResult codec与既有VO复用 | 无PO/entity/DTO转换层或字段新建 | 无需新Mapper |
| MC-LOG-001 | Not applicable | N/A | 无新增业务日志；fixed safe异常无cause/private文本 | 不泄漏body/receipt/nonce，不用日志代错误处理 | 无日志注入变化 |
| MC-BEAN-001 | Not applicable | N/A | manual技术Recorder/ledger，factory/config不动 | 无新业务DI/Bean/Qualifier注册 | 沿现技术库模式 |
| MC-UTIL-001 | Applicable | PASS | JDK percall AtomicReference、现codec/事务/mapper | holder无instance共享缓存，无新utility/线程机制 | None |
| MC-JSON-001 | Applicable | PASS | 原TIMED_OUT result codec/VO与closed effect字符串 | 无JSON/enum编码格式或ordinal变化，只既有效应值 | None |
| MC-TIME-001 | Applicable | PASS | SQLcaptor完整DBdeadline谓词/claim-renew LEAST，原Clock/window保持 | 不借Java hint/livelease判据、不延deadline；latefact保留 | 真实PG映射/锁未验收 |
| MC-CONFIG-001 | Not applicable | N/A | 无profiles/flags/权限变化，PG URL/Timeout90保持 | 无自动装配/功能激活 | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | Observer+Facade/UnitofWork/CAS | commit→通知→account，明确postcommit与claim隔离变化点，无新Strategy/State引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | root精确12paths与23tracked SQL bytecompare | 无Executor/plugin/bridge/AS/native/AA/MCP/schema/newcounter | root最后cached范围复核 |
| MC-TEST-001 | Applicable | PASS | 两轮有效RED完整分类、105PASS/11PGskip、独审 | 无fixture/compiler伪RED，PG编译skip非真实SQL证明 | 环境运行待用户验收 |
| MC-BLOCKER-001 | Applicable | PASS | root全文/逐项17MC10Rules、deadline+observer独审无实质finding | source/test/static无openblock；Review不伪Accepted，全Goal Partial | 最后docs/cached/commit由root执行 |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | status-only接口与现方法overload角色清楚 | 接口/guards/编译 | 无新普通POJO风格漂移 | None |
| Rule 2 | Applicable | PASS | null早拒、effect/run/rev与SQLfullowner/epoch fence | START23/Access16/Deadline8正反 | 现技术库域/Service guards，无新HTTP DTO层 | None |
| Rule 3 | Applicable | PASS | 原不可变VO/Outcome/结果constructor保持 | 旧START/Access/RO回归 | 不造实体record或新Mapper、宽松DTO | None |
| Rule 4 | Not applicable | N/A | 无新增业务Spring Bean/DI/日志，现manual技术库 | root范围/import检查 | 不伪造Lombok DI/无关日志注入 | 沿现模式 |
| Rule 5 | Applicable | PASS | JDK AtomicReference/现Spring/JDBC/codec | imports/POM无改 | 无新utility/依赖/框架 | None |
| Rule 6 | Applicable | PASS | 原Jackson/字符串状态/既有效应值 | 旧result/VO/效应测试 | 无ordinal或JSON格式变化 | None |
| Rule 7 | Not applicable | N/A | 无环境配置/flag改动 | 12paths及原PG前置 | 无key parity或自动激活变化 | None |
| Rule 9 | Applicable | PASS | Observer/Facade/UnitofWork/CAS | 顺序/throw/port包装/rollback/错fence矩阵 | 复用现模式，不额外建策略/状态引擎 | None |
| Rule 10 | Applicable | PASS | 原Instant/Duration/DBclock绝对deadline | SQL predicates/expiredhint/旧window | 无java.util时间实体/reset，hint不代DB判据 | None |
| Rule 11 | Applicable | PASS | 已授权Agent技术JDBC/独立Flyway、原CQE/outbox路径 | 23SQL字节相同、同TX预算/event rollback、12paths | 无CRUD/新层/MP迁移/依赖/软删除或事件格式变化 | None |

## 6. 结论与未验证边界

本Step达到**Verified（nonruntime，待root最后precommit/cached/commit）**：5aliases Satisfied，17MC/10Rules逐项PASS或证据支持N/A；source/test/独审/static已闭合。计划Review语义保持；Source GO锁定SHA为`e900d38800850bed516c61d0c3b028ec0ca841c64ed7abfb2bf64461e8efc423`。root批准仅对plan/evidence作末尾空行trim，最终plan SHA-256为`ccaa380eb54470376113b496959aeabe4ade540aeadc55f37b36b78decf6db1f`，不伪称最终字节与GO版本相同；源码与23SQL未改，无需功能重测，不预测Git hash。

已提交工具事实可在account/输出失败前被host观察，新deadline只在自身当前generation CAS获胜时写可信冻结effect。无native聚合/Executor接线；宽reconciler先写UNKNOWN时不终态refinement。observerfail可留accounting pending，latecallback无法追溯改变owner已返回timeout；不回滚已提交事实或自动重派。

无真实PG/IAM/PDP/crypto/HTTP网络/模型/runtime验收，无app/server/DB/容器/listener/浏览器/GUI/native scheduler启动、DDL apply/repair/backfill/drop、权限激活/角色grant、push/PR/release。无新repo scratch/scripts/work/harness，测试留在subsystem。docowner仅更新evidence及root批准的plan末尾空行，未改source/tests/README，未构建或提交；完整T3-07/Goal仍**Partial**。
