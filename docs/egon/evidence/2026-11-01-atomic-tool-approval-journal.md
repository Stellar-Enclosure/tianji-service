# 原子审批消费journal执行证据

Plan: [Step22](../plan/2026-11-01-11-58-atomic-tool-approval-journal.md)，Review，原用户授权；baseline `a7a392e35eca4ac51a9076853669f4a13eacf3f9`；Spec SHA `5050f9ff8c68fc6932bf12c8081b74accd8e1e27fe5e512c24061948ac6f21ef`。

当前为 **Verified（nonruntime，PRECOMMIT）**。Root接管文档，plan草稿Created11:58、Updated12:15；root全文（1–140/141–280/281–end，补176–221）、strict/双资源/checklist通过，GO SHA `d9faf9658ac95331464999782c5dfe5dd5592a24b1d02efc28a9030d5db6938d` 后发两组件RED-only GO。无本步提交。精确9路径见Plan。完整Goal Partial，AA Recorder/native/config、MCP及后续蓝图尚未完成。

## 实际记录

首次 `/tmp/tianji-atomic-tool-approval-red.log` exit1，22.353s，29 tests=1PASS/8FAIL/20ERROR/0skip。Action13=1PASS/7期望UOE断言FAIL/4直接UOE ERROR/1现历史parser对合法nano proof+floorµ索引拒绝；该组件有效RED，root全文读新test后才给Action GREEN GO。

Store16=1值guard断言FAIL/15 fixture ERROR；复用旧IDW Rig的prepareWithStatus硬编码PREPARED，AA Row构造失败，随后未关闭构造mock造成后续fixture连锁异常。这些15项不计行为RED。Root仅新StoreTest override真实AA AWAITING准备形状，并在初始化失败时close；旧生产/旧测试不作绕过。Store和InvocationSql仍UOE冻结，等待合法fixture重跑再GREEN GO。

第二轮 `/tmp/tianji-atomic-tool-approval-store-red.log` exit1，21.245s：Action13全部GREEN；Store16仍1值guard FAIL/15fixture ERROR。Root覆盖既有Mockito answer使用when触发旧answer的null参数NPE，初始化尚未进入行为路径；这些仍不计行为RED。仅改新test为doAnswer安全覆盖，Store/Invocation保持UOE，重跑focused Store。

合法Store RED `/tmp/tianji-atomic-tool-approval-store-valid-red.log` exit1，20.668s：16=8预期assert FAIL/8直接Store UOE ERROR/0skip，无fixture/compiler/Mockito初始化错误；root观察后才给Store/Invocation组合功能GREEN GO。Action13实际GREEN及独立source/test审查PASS；Root Store/ResultVO guards已实现，InvocationSql owner实现中，当时尚无组合GREEN；随后记录见下文。

首次组合GREEN trial `/tmp/tianji-atomic-tool-approval-green.log` exit1，20.883s：29=28PASS/1fixture FAIL/0ERROR/skip。wall测试在移动clock后才构造CheckResult，原typed contract正确拒绝 checkedAt>callDeadline；Root仅调整测试先构造合法决定再移动clock，不放宽生产时间校验。

Root/SQL owner独审发现 own dispatched invocation 与 action null dispatch backlink 不一致仍返回metadata。先新增第17项反证，原源码冻结：`/tmp/tianji-atomic-tool-approval-backlink-red.log` exit1，23.200s：17=16PASS/1预期FAIL/0ERROR/skip，只有 missing-backlink 未拒绝；前述wall fixture已PASS。Root在source fix前写/读CLAR-002与File5精确delta，strict PASS，新SHA `b49d77f2346971e40aac6caf49ed876abfe5904aa18832dfe1d90443713f5be9`，原GO d9faf另留。随后才授权一行同IDW guard，保不同非空later pointer历史合法。

聚焦GREEN `/tmp/tianji-atomic-tool-approval-focused-green.log` exit0 BUILD SUCCESS，26.667s：30PASS/0fail/error/skip，Action13+Store17。Root全文读三prod diff与全部新tests；独审Action source/tests与Root Store/tests均PASS。23既有SQL逐字节与HEAD一致；Store/Invocation原行无删除，Action仅旧expiry compare改floor+strict>consumed，原IDW/readonly方法不动。

CLAR-003仅协作所有权调整：组合GREEN后PG文件交journal owner，Root统一构建与最终整合；exact9scope/API/顺序不变，Root读delta/strict PASS final plan SHA `6e6acb023c48593e834ff2a7049ac3d9bf05e176bfa4ac60ac16b6daa7a412bc`。当时PG helper准备中，不把30聚焦GREEN当整步Verified；最终完整门禁见下文。

首次命令选新两类，canonical JDK25/source-reactor/settings/ADK false/OTel none；无编译/依赖失败。最终完整结果如下；最后cached/staging/Git待Root执行。不得把controlled JDBC或PG skip称真实SQL/锁验收。

## 最终实现与验证

精确9paths：三现有prod、两新focused tests、原PG test、README、plan/evidence。Root完成三prod diff与全部新测试/PG180行逐行审阅；Action、Store与各测试有独立只读审查。旧Store/Invocation无原行删除；Action仅原expiry比较改为proof floorµ且floor>consume；旧IDW/readonly方法原样。全部23既有SQL与HEAD逐字节相同，无SDK/Verifier/codec/配置/权限/依赖/公开API变化。

`/tmp/tianji-atomic-tool-approval-final.log` exit0 BUILD SUCCESS，22.098s：精确10类167 total=166PASS+原PG类1SKIP，0fail/error。分项ActionIntentStore23、ActionApproval13、ActionWrite10、Codec21、IdempotentStart23、InvocationStore27、WriteInvocation17、InvocationRecorder15、ApprovedInvocation17、PG1skip。所有owner先冻结，root统一canonical命令，实际JDK25/settings/literal ADKfalse/三OTel exporter none；无服务或数据库启动。

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolApprovedInvocationStoreTest,ToolActionApprovalSqlTest,ToolWriteInvocationStoreTest,ToolActionWriteSqlTest,ToolInvocationStoreTest,ToolInvocationRecorderTest,ToolIdempotentStartRecorderTest,ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolExecutionJournalPostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-atomic-tool-approval-final.log 2>&1
```

package consume入口要求完整AA动作、实际µcall deadline对应fresh DISPATCH checked request/audit、E1/E2、current run/lease/策略与各身份窗口。consumer与business executor独立SERVICE同tenant；record本身不是认证，未来可信Recorder负责从实际Verifier/transport callback传递。新host预算与direct DISPATCHING invocation先写，七审批列+action pointer后CAS，fresh fullJOIN后返回attempt；原wait finished/SETTLED0不复活，orphan不adopt，advanced新UUID只metadata，缺backlink拒绝。全局receipt unique与第二写失败整体rollback。

proof JSON/SHA保fullnano，索引expiry floorµ并严格>consume；历史parser只核历史，不把过期proof当当前权限。complete只认原nonce+完整历史tuple，不核当前lease/cap/proof，保null/非零/超估算费用，不settle；UNKNOWN不重派，当前pointer不同只写原inv事实。

PG追加180行在原optin URL/vector/@Timeout120与V13→V14流程后调用：两合法run/20新UUID唯一赢家/旧wait和预算不变、source unique跨coherent controlled action、第二写trigger回滚/同UUID随后成功、nano proof/SHA与floor列、终态/清lease/cap刷新后原nonce非零费用，以及独立3s proof/最多6s等待后UNKNOWN→known费用。这里只编译/skip，不能宣称SQL、FK、物理锁、真实回滚或重启验收通过。没有HTTP/IAM/业务START。

## Requirement closure

| Alias | Status | Actual evidence | Boundary |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | fullaction/checked request/audit/grant/lease/E1E2/identity windows 与13+17新测试 | constructed observation非认证；actual callback为futureRecorder职责 |
| PLAN-REQ-002 | Satisfied | 新UUID/新budget/inv-first/action-CAS/freshJOIN/oldwaitimmutable/rollback/unique与PG夹具 | controlled JDBC不是实际PG；PG只编译 |
| PLAN-REQ-003 | Satisfied | originalnonce/late afterexpiry/known replay/UNKNOWN refine/futurepointer/null与非零费用 | 无settle/业务重派，不能改终态run |
| PLAN-REQ-004 | Satisfied | 旧parser真实nano反例RED→floorµ/保JSONSHA/legacyµ与floor==consume拒绝 | 原DTO未改；actual DB precision验收待用户 |
| PLAN-REQ-005 | Satisfied | 9scope/23SQL/旧路径回归/RED与fixture错误如实分类/166PASS1skip | 最后Git待Root，完整Goal仍Partial |

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 三现有AR persistence类/9scope，Agent generator excluded | 保现结构/JDBC/Flyway技术架构，无新层/CRUD/引擎 | None |
| MC-REUSE-001 | Applicable | PASS | 现codec/Row/Binding/Observation/CheckResult/预算/事务/nonce helpers | 扩独立AA路径，未放宽IDW/RO入口或重造认证/JSON | None |
| MC-DEP-001 | Applicable | PASS | POM/构建脚本/SDK无改，仅现JDK/Spring/JDBC | 无新依赖/框架/运行设施 | None |
| MC-NAME-001 | Applicable | PASS | 现Store/Sql技术职责与immutable ApprovedWriteDispatchBeginResultVO | package窄方法/类型沿现库命名，无实体漂移 | None |
| MC-VALID-001 | Applicable | PASS | 原构造/domain guards+实际PDP request/audit/lease/E1E2/window/nonce/CAS与30新tests | 审批不代替PDP；record不是认证，actualconsumer与executor独立同tenant | None |
| MC-MODEL-001 | Applicable | PASS | 新四字段metadata record/成对inv-budget/attempt完整一致 | 无新实体/公开DTO/敏感proof或结果body返回 | None |
| MC-CONVERT-001 | Not applicable | N/A | typed Observation直接用现private codec转换proof/source | 无PO/entity/Mapper或通用DTO映射新增 | 沿现typed边界 |
| MC-LOG-001 | Not applicable | N/A | 无业务日志新增；固定safe code/msg无cause | 不泄receipt/source/body/token/JDBC私密错误 | 无日志面变化 |
| MC-BEAN-001 | Not applicable | N/A | 现constructor/manual library，未加Bean/DI/config | 不引业务Service或伪Lombok注入 | 无Bean面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK java.time/原nonce/digest/Jackson及Spring事务 | 无新通用utility/second parser/transport | None |
| MC-JSON-001 | Applicable | PASS | codec源不变、原16KiB/canonical SHA、fullnano proof原样 | 无ordinal/格式/SDK或publicJSON变化 | None |
| MC-TIME-001 | Applicable | PASS | µactualcall先PDP、原wallmono/window、DBconsume/proof floor、historicalnonce tests | 保原nano证明，floor严格>consume，no budget reset/freshproof误门禁 | 真实PG/时钟未验收 |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/flag/权限或自动装配改动 | explicit package库，无自动激活/角色grant | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | 复用Unit of Work+CAS，inv-first/action-second+freshJOIN | 解决预算/审批/指针原子性，不新增调度/State/Activity层 | None |
| MC-SCOPE-001 | Applicable | PASS | 9paths/23SQL bytefreeze，旧IDW/RO主体无删改 | 唯一旧parser变更为已批准精度修正，无旁路改动 | cached范围由Root最后复核 |
| MC-TEST-001 | Applicable | PASS | Action有效RED、合法Store RED、backlink RED；完整166PASS+PG1skip/独审 | 前两fixture失败未冒行为RED；PG只编译不冒验收 | 真实环境待用户 |
| MC-BLOCKER-001 | Applicable | PASS | source/tests/diff/独审与5aliases/17MC/10Rules闭合 | 两组件与精度/backlink修复均实证，无open source finding | 最后cached/stage/commit待Root |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Store/Sql角色及metadata VO后缀 | source/compile/值guard | 沿现技术库命名，无普通实体新增 | None |
| Rule 2 | Applicable | PASS | typed/domain guards与fullaction/PDP/source/lease/窗口/nonce边界 | 13Action+17Store新tests/旧回归 | 审批不替当前权限，层内technical handoff闭合 | None |
| Rule 3 | Applicable | PASS | 仅immutable四字段record，typed原DTO复用 | 成对关系/脱敏/compile | 无entity/Mapper转换需求 | None |
| Rule 4 | Not applicable | N/A | 无新业务Bean/DI/log | 9scope/imports | 不加无关日志/注解/容器层 | 沿现技术库模式 |
| Rule 5 | Applicable | PASS | JDK/Spring/JDBC与原codec/digest/nonce | imports/POM scope | 无新通用工具/依赖 | None |
| Rule 6 | Applicable | PASS | 原Jackson codec与canonicalJSON/SHA复用 | oldCodec21/新精度与receipt测试 | 无ordinal/公开JSON或协议变化 | None |
| Rule 7 | Not applicable | N/A | 无profile/YAML/flag变更 | 精确9scope | 不影响多环境键/值 | 无配置面变化 |
| Rule 9 | Applicable | PASS | 原UnitofWork/CAS参与者 | 顺序/回滚/竞争夹具与缺backlink反证 | 实际复杂原子性需求，无新引擎/层 | None |
| Rule 10 | Applicable | PASS | Instant/Clock/Duration/mono/DBµ | 原nano/floor/lease/proofexpiry/迟到事实 | 无java.util时间/伪时间下界/reset | None |
| Rule 11 | Applicable | PASS | 当前AR技术架构/23SQL immutable/原CQE保持，Agent generator excluded | compile/source/9scope/bytefreeze | 无CRUD/MP迁移/业务软删除/事件层变更 | None |

## 结论与未验证边界

本Step **Verified（nonruntime，PRECOMMIT）**，五aliases Satisfied、17MC/10Rules逐项PASS或有据N/A；原GO d9faf、CLAR002 b49、CLAR003 final6e6修订分别留证。最后cached/staging/commit由Root执行，不预测hash，Review不伪Accepted。生产源与测试/文档范围已审，PG180行夹具源码与README已由独立agent只读复审PASS；真实PG未运行。

不自动启动应用/DB/容器/browser/GUI/监听器，不apply/repair/drop既有迁移、不激活权限/授角色、不push/PR/release。PG仅显式前置夹具默认skip，真实IAM/crypto/网络/模型/数据库验收待用户环境。原wait终态/旧SETTLED0预算不可复活，已消费收据不可释放，UNKNOWN不可重派。此库未接AA Recorder/native/config；MCP与其余蓝图继续，完整Goal仍Partial。
