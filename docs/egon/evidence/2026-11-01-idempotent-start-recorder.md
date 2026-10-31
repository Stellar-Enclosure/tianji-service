# T3-07 固定BPMN START Recorder执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度06:54—06:58 |
| 执行计划 | [固定START Recorder](../plan/2026-11-01-06-36-idempotent-start-recorder.md)，Review；legacy exact用户授权，非Accepted |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/12/13、T3-07 |
| 主设计实施前SHA-256 | `1d3e717f18919c4bfd78b9a014e0bb97329079abec32807a473d751403424be5` |
| Baseline | `7c8a5e9c37693ce0d54ae7b8aa576aa6a5d01cdf` |
| 门禁 | root全文362行/strict/resources15 Markdown/19 refs与step-start checklist通过，06:45 RED-only GO；功能实现在有效RED后进行 |
| GO前计划SHA-256 | `f4887967bd1e9683e40dbf849fd07bf179d070481a9c47f8c895dcbccfc5fff7`；后续仅追加门禁事实 |
| 实施状态 | **Verified（非运行验证）**：最终175通过+原PG1skip，总176，0 failures/Errors；完整T3-07/平台Partial |
| 提交状态 | 本报告为**提交前Verified快照**：root已读全文/end-checklist，exact七路径cached门禁通过；随后提交，最终hash见Git及蓝图进度 |

## 1. 实际七路径与兼容

| Operation | Repository-relative path | owner |
| --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java | root |
| MODIFY | README.md | root |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java | budget_lock_tests |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java | budget_lock_tests；仅Rig一行visibility |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | journal_design；controlled transport helper |
| CREATE | docs/egon/plan/2026-11-01-06-36-idempotent-start-recorder.md | doc owner |
| CREATE | docs/egon/evidence/2026-11-01-idempotent-start-recorder.md | doc owner |

root逐行/difflib核原Recorder全部旧代码从baseline逐字保留，仅class Javadoc扩描述；旧executeReadOnly/RecordResult receiptNull/zero/check/errors/messages未改。Rig文件exact一行private→package，旧17断言不动。没有Store/SQL/domain/port/provider/contracts/config/native/AA/MCP/schema/deps/权限/factprefix/private terminal重放或settlementAPI变化。root静态核全部22旧SQL与baseline bytes相同，V13本次实测仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际行为与保守限制

现Recorder Facade新增executeIdempotentStart(transport,request)。入口只IDW/action/intentExpiry/estimatedCost0，核实际policy.executionResourceKey=ProcessClient.START_RESOURCE_KEY、exact required STRING instanceId output、noambient、ownverified与原affinities。已知0只固定内部nonbillable START，generic Store费用合同原样。

新IdempotentStartResultVO将actionIntentId canonicalUUID、七种IDW actionStateAtPreparation和正revision捕获一次，明确是prepare快照；ownAttempt只自身inv事实，不从complete合成全局APPLIED/新revision。WriteAttemptResultVO闭合run/UUID/state/revision/epoch/times/operationInvokedThisCall/accounting；body非null须positiveepoch/status匹配/actualCost exact0，SUCCEEDED须strict hostreceipt pattern及规范onlyinstanceId output，非成功empty/nullreceipt由原Outcome域规则保持。epoch0永远metadata/bodyNULL。operationInvoked只表示进入OneShot.start，非HTTP已发送。

无owninv只返回snapshot/null，无Transport/account/body。ownadvanced/nonPREPARED只metadata/bodyNULL，accounting仅依据prepare已见budget settledZero，否则PENDING，不resettle/redrive。ownPREPARED/globaladvanced调用Store.close supplied0后也只返回closedmetadata/bodyNULL/PENDING。所有begin !started同样仅metadata，不凭state/revision判断本call刚写0，不account；同UUID genericcaller在prepare后以fee.03关闭的反证测试证明既有费用保留且不伪造0Outcome/settle0。

fresh PREPARED/PREPARED才进入trusted FixedSTART callback，Request deadline exact原prepdeadline。actual executor必须nonLEGACY SERVICE/same tenant/operation非null；fresh E1 exactbinding，DISPATCH Request包含稳定intent/参数SHA/fullcallDeadline=minprep/run/executor/E1，original10s wall+mono不reset。ALLOW audit/echo/boundary/originalsubject/grant/checkedAt/expiry覆盖完整call，StoreE2再核。mint32byte local nonce只传SHA，begin确认commit/started后postcommit checkStart才消费一次；旧ROcheck不变，新checkStart用FixedSTART CANCELLED/DEADLINE及独立safe消息。

正常Observation→SUCCEEDED/hostreceipt+instanceId/0；DENIED→REJECTED/DOWNSTREAM_PERMISSION_DENIED；NOT_SENT或operation内preSDK CANCELLED/DEADLINE→FAILED_KNOWN/DISPATCH_NOT_SENT；invalid→UNKNOWN/OUTCOME_INVALID_RESPONSE；uncertain/runtime及op内意外sourcekind→UNKNOWN/OUTCOME_UNKNOWN，均fixed internalcost0。begincommit后窗口过期但未进入operation，凭本noncecomplete knownNoSend且invoked=false。未知业务效果不代表此固定内部API费用未知，也不授任何自动POST重试。

只有本call started+nonce认证的fresh complete确认事实COMMIT后，才允许将outcome暴露到新VO并独立bestEffort account own0。账务失败只PENDING，不能回滚或降级已知事实。lateACK callback内先记事实，outer postcheck失败仍安全抛错/抑制返回私密body，cleanup不得改advanced。无新private terminal body replay。

outer/precallback cleanup只Store.close自身PREPARED supplied0，bookkeeping清/恢复中断，NOaccount/settle；metadata不能证明newlyClosed，已有fee.03不得覆盖。closedprepared metadata只加sanitized accounting-not-confirmed suppressed，Optional.empty不虚构欠费。race loser/cleanup预算可留RESERVED/PENDING，runfinish可转UNKNOWN；这是明确保守限制，不声称即时资源结算或actual overrun accounting闭环。模式复用原Facade+Store UnitofWork+TransportAdapter，无新Strategy/抽象层/engine。

README记录显式库、prepare快照、成本保守及native/config/AA仍pending。公开typed值构造是结构检查，不是remote签名/身份/PDP许可；实际身份仍来自trusted transport，元数据不会替代其验证。

## 3. 实际RED/GREEN与命令

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据与分类 |
| --- | --- | --- |
| 有效RED | 06:54:24；新17项9 failures/8 Errors，旧RO15通过、原PG1skip，总33；exit1，18.059s | `/tmp/tianji-start-recorder-red.log`；8 wrong-exception UOE断言+8直接stub Errors+1 VO未验证，共17缺失行为；无fixture/compiler问题 |
| 首次聚焦GREEN | 06:57:20；新17+旧RO15=32通过、原PG1skip，总33，0fail/error；exit0、BUILD SUCCESS，20.252s | `/tmp/tianji-start-recorder-green.log`；功能实现在有效RED后进行 |
| 最终exact计划selector GREEN | 06:58:27；AR149通过+PG1skip、AS26通过=175通过/1skip，总176，0fail/error；exit0、BUILD SUCCESS，22.323s | `/tmp/tianji-start-recorder-final.log`；source-reactor成功，非全仓库测试全部执行 |

实际最终命令，cwd=tianji-service（root执行；docowner未运行Maven）：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolWriteInvocationStoreTest,ToolInvocationStoreTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest,ToolExecutionJsonCodecTest,ToolExecutionStorageVerifierTest,ToolExecutionJournalPostgresTest,FixedBpmnStartToolTransportTest,TianjiFixedBpmnStartToolTransportTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-start-recorder-final.log 2>&1
```

| 最终测试组 | 通过 | 跳过 | 实际范围 |
| --- | ---: | ---: | --- |
| 新StartRecorder17、旧RORecorder15 | 32 | 0 | 新2VO/profile/nonce/once/commit/account/race/late/pending与旧RO兼容 |
| IDWWriteStore17/InvStore27/ActionSql10/ActionStore23 | 77 | 0 | 现Store/SQL/current与历史事务/动作合同回归；旧17断言未改 |
| Codec21/Storage13/FixedPort6 | 40 | 0 | 原编码/schema/typedSTART合同兼容 |
| AS固定STARTTransport26 | 26 | 0 | 原SDK/stage/once/receipt/samebearer语义回归，mock JDK wire/受控IAM |
| 原PG class | 0 | 1 | 新Recorder helper约125行已编译/独立静态审阅，缺原optin未运行 |
| 合计 | 175 | 1 | 11类选择回归，真实PG/IAM/模型未验收 |

新17用真实IDW Store/Invocation SQL/Spring协调与controlled JDBC/Action participant，账务stub仅新Test局部；PGhelper只controlled START transport，不涉及真实IAM/HTTP。没有实际SQL/FK/trigger/concurrency/PG运行证明、真实OAuth/crypto/HTTP网络/listener或模型；本步无child JVM probes。upstream既有三项Jackson2 deprecation警告保留，无本步依赖升级。

root已全文审约170行新增产线/新17约400行/PG125/README相关说明，独审Recorder/Test/Rig及PG静态均PASS无发现；root最终exact7/旧22SQLbytes/无operational stub/diffcheck/Reviewstrict/resources闭合。最终end-Step checklist、本文/delta与cached stage检查由root在提交前执行；若失败回报告修复不commit，当前没有提交hash。

## 4. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 profile/结果真实 | Satisfied（本步） | 新17核IDW/STARTresource/output/est0、2VO/snapshot/strictreceipt；旧ROResult仍拒receipt | snapshot不代表完成后global state/revision，值构造不认证 |
| PLAN-REQ-002 当前权/nonce/once | Satisfied（本步） | freshSERVICE/E1/check fullcall/E2、beginACK后once/postwindow；旧AS26 stage回归 | 只有本noncefreshcomplete可body/account，无自动重派 |
| PLAN-REQ-003 事实/late/账务故障 | Satisfied（本步） | commit-before-account、完整mapping、lateACK先事实/outer抑制/账务PENDING不降级 | 非private terminal body replay或真实PG/计费验收 |
| PLAN-REQ-004 metadata race保守 | Satisfied（本步） | fee.03 genericrace、!started/early/outercleanup均无body/noaccount，advanced不resettle | cleanup预算可RESERVED/PENDING→finish UNKNOWN，不是即时资源账务闭环 |
| PLAN-REQ-005 兼容/真实范围 | Satisfied（本步） | 175pass/PGskip、Rig一行/旧17/RO逐字保持、exact7/V13固定 | native/config/AA/MCP/StoreSQL/schema/依赖与真实环境仍后续 |
| 完整T3-07/平台蓝图 | Partial | 当前显式固定START Recorder能力 | native/审批消费/MCP配置、真实IAM/PG/模型及平台后续未完成 |

## 5. Blocking Manual Check

以下是本步非运行实现验证，不将计划PASS搬成runtime接受；最终Git/cached门禁单独记录。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR Recorder Facade与七paths；Store/SQL/domain/port/provider未改 | 无新层/模块/配置/native/AA/MCP或消费者自动启用 | None |
| MC-REUSE-001 | Applicable | PASS | nonce/window/account/settledZero/bookkeeping/earlier、IDW Store、FixedSTART Adapter | 扩现Facade/UnitofWork，未新建Strategy/执行/认证/预算层 | None |
| MC-DEP-001 | Applicable | PASS | 既有JDK/Spring/codec/typedports/ledger与实际imports | 无新POM/依赖/utility框架 | None |
| MC-NAME-001 | Applicable | PASS | IdempotentStartResultVO/WriteAttemptResultVO及明确AtPreparation字段 | snapshot与ownfact命名清楚，newSTART诊断不称Read-only，值脱敏 | None |
| MC-VALID-001 | Applicable | PASS | IDW/profile/action/expiry/est0/executionResourceKey/outputschema、E1/check/E2/fullcall/receipt/VO | 新17与ports/AS26回归；nonce事实和metadata race严格区分，fee.03不伪0 | None |
| MC-MODEL-001 | Applicable | PASS | 两public immutable/redacted closed VO、nullable ownAttempt与原typedOutcome | outcome必须positiveepoch/同state/0；不合成postcomplete全局state/revision | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed domain/port/Store结果与固定Outcome语义映射 | 无实体/DTO Converter或Mapper层新增 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Facade，无新业务日志Service | 固定安全错误/无私密cause，nonce/参数/receipt/body不写toString | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config/provider新增 | 原constructors/ownverifyStorage/affinities保持 | None |
| MC-UTIL-001 | Applicable | PASS | JDK SecureRandom/原Store/helpers/时间/账务API | 无新utility/线程/认证/调度或scratch平台 | None |
| MC-JSON-001 | Applicable | PASS | 原StartObservation/ToolExecutionOutcome/schema/codec复用 | strict onlyinstanceId/hostreceipt；旧ROreceiptNull与encoder不变 | None |
| MC-TIME-001 | Applicable | PASS | exactprepdeadline/fullcall/min、originalwallmono、独立checkStart/latefact | postcommit过期本nonceknownNoSend、outer拒绝不擦已存事实；旧ROcheck原样 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/flag/native/MCP/AA配置或权限变化 | 显式库方法未自动挂到写消费者 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Recorder Facade+Store UnitofWork+TransportAdapter | commitbegin→once→factcommit→ownaccount；缺provenance保守metadata，无新Strategy/抽象层 | None |
| MC-SCOPE-001 | Applicable | PASS | root exact7paths、全部22旧SQL bytes相同/V13固定；Rig仅一行、旧17不变 | 无StoreSQL/域/ports/provider/contracts/schema/deps/私密terminal重读或新prefix扩大 | None |
| MC-TEST-001 | Applicable | PASS | 有效RED17/9fail8err→32focusedpass+PGskip→175pass+PGskip；新17完整/独审PASS | 编译/fixture无误；PG125行只compiledskip，实际crypto/网络/模型未验证 | None |
| MC-BLOCKER-001 | Applicable | PASS | root全新增约170行/新17约400行/PG125/README审阅、独立final全部无发现，回归/静态/strict/resources闭合 | 非运行实现无未决阻断，Git提交尚未发生 | None；root已读最终报告/end-Step checklist，exact七路径cached check/stat/name通过；Git hash提交后记录 |

## 6. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 两VO与AtPreparation字段、新execute/checkStart/startsafe角色 | 实际编译/175相关通过 | 名称区分snapshot/ownfact、START诊断独立且redacted | None |
| Rule 2 | Applicable | PASS | fixedprofile/current proof/E1E2/nonce/receipt/VO、metadata无provenance | 新17含fee.03并发反证、once/late/账务失败 | 只本noncefreshcomplete有outcome/account，不从state/revision猜0 | None |
| Rule 3 | Applicable | PASS | true immutable 2VO/原typed值、closedstate/time/positiveepochbody | 构造/receipt/规范output/原ROResult拒receipt测试 | 无fake全局state/revision、实体副本或可变结果 | None |
| Rule 4 | Not applicable | N/A | manual技术Facade，无业务Bean/DI/日志面新增 | 七路径/固定错误与原构造审阅 | 不伪造Lombok业务注入验收 | None |
| Rule 5 | Applicable | PASS | JDK与原Store/ports/helpers/ledger | imports/POM/编译 | 无新工具库/utility/线程或策略引擎 | None |
| Rule 6 | Applicable | PASS | 原Observation/Outcome/schema/codec，receipt仅新writeVO | FixedPort6/AS26/Codec21/新17 | 无新JSON/事实前缀，旧ROreceiptNull与合法编码保持 | None |
| Rule 7 | Not applicable | N/A | 无config/profile/flag修改 | root scope/README | 不自动注册native/写配置或权限 | None |
| Rule 9 | Applicable | PASS | 现Facade+UnitofWork+Adapter与本nonce-only account | 先commit/once/late/account故障与race测试 | 复用具体变化点，未引Strategy/State/新抽象 | None |
| Rule 10 | Applicable | PASS | Instant/Clock/mono/原deadline+checkStart | fullcall/窗口不reset/commit后expiry/lateACK测试 | 事实先存、账务后独立；旧ROcheck/kinds/messages不改 | None |
| Rule 11 | Applicable | PASS | 原AR/七paths/旧22SQL与V13固定 | 175pass+PGskip/独审/一行Rig/旧RO逐字保留 | 无StoreSQL/domain/port/provider/config/schema/deps/APIactivation扩大 | None |

## 7. 未验证与提交门禁

显式固定START Recorder不等于native/config/AA/MCP或写权限已开放。真实IAM/PDP/签名/crypto、HTTP/PG/SQL/模型与平台运行未验收；没有actual overrun settlement证明/新事实前缀/私密终态重读。known0只固定内部START鲜活本nonce事实，generic Store费用不改，元数据race仍保守PENDING。

无新repo scripts/work或scratch harness，测试留在subsystem；未启动app/DB/HTTP listener/容器/浏览器/GUI/native scheduler/长期runtime，未部署/push/PR/release/开启flag/注册激活权限或角色grant。

docowner仅编辑本报告，plan保持Review/原GO事实无新澄清；未改source/tests/README、未Maven或提交，保留其他owner修改。root已读全文报告/end-checklist并显式stage七路径，cached check/stat/name已通过；本报告为提交前Verified快照，随后执行单独semantic commit，不填自身未来hash。

**PARTIAL — Spec requirements are unmet or unverified**：本步非运行实现Verified、175项相关通过/1项PG跳过；root提交前文档与cached门禁已完成；完整T3-07/平台与真实运行仍未完成。
