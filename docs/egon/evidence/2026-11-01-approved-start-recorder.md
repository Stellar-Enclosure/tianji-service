# 审批型固定START Recorder执行证据

Plan: [approved START Recorder](../plan/2026-11-01-13-15-approved-start-recorder.md)，Review、原用户exact继续授权；baseline `5fd1267534e8d4b5df7940e209eba27a05c5411b` clean，Spec SHA `5f5a589e5725d3c30b94d77af61946d6b9c67092f5963de05417db44fdd1ca5e`。

当前 **Verified（nonruntime，PRECOMMIT）**。Root全文409行（1–139/140–274/275–end）、strict、execute资源15md19/writing18md24、current execute-plan checklist、source/baseline门禁通过后SourceGO，Plan SHA `268e707cbdb22c868a8a1d6ddfc3e0e6f66404bbbe8795738be7d51f0421dc10`。精确9paths/5aliases/17MC10Rules闭合；最后cached/stage/Git待Root，不预测hash，不伪Accepted。AA native/config/MCP与其余蓝图仍Partial。

## 实际执行记录

| 阶段/日志 | 实际结果 | 时长 | 解释 |
| --- | --- | --- | --- |
| Source GO/声明 | wrapper directUOE+新enum；Recorder两方法UOE/四字段plain record；tests先写 | Maven前 | 无功能实现；每组件实际RED后才GREEN |
| `/tmp/tianji-approved-preparation-red.log` | exit1；26=17旧PASS/5预期UOE类型断言FAIL/4直接UOE ERROR/0skip | 18.161s | new9合法，fixture/compiler无错；Root全文读new9/helper diff后才准备GREEN GO |
| `/tmp/tianji-approved-preparation-green.log` | exit0 BUILD SUCCESS；26PASS/0fail/error/skip | 21.047s | private bounded overload与AA projection真实绿色，旧17主体保持 |
| `/tmp/tianji-approved-start-recorder-red.log` | exit1；18=3PASS/6FAIL/9ERROR/0skip | 16.011s | 8直接UOE及guard缺失断言；1检查尚未进入approval阶段后直接解引用null造成断言代码NPE，不当独立行为RED |
| `/tmp/tianji-approved-start-recorder-valid-red.log` | exit1；18=3PASS/7预期assert FAIL/8直接UOE ERROR/0skip | 15.650s | 测试仅加assertNotNull前置，使缺callback成为明确断言；无fixture/compiler错误，Root观察后才实现Recorder/VO guards |
| `/tmp/tianji-approved-start-recorder-green.log` | exit0 BUILD SUCCESS；44PASS/0fail/error/skip | 21.349s | Store26+Recorder18，真实Store/SQL/Spring预算与controlled ports组合 |
| `/tmp/tianji-approved-start-recorder-final.log` | exit0 BUILD SUCCESS；148 total=147PASS+原PG类1SKIP，0fail/error | 21.439s；2026-11-01T13:51:43+08:00 | 九类完整source reactor成功；PG84新行只编译，0失败不代表物理SQL通过 |

Root统一canonical JDK25/settings/literal ADKfalse/OTel none；所有owner先冻结Java。最终之后仅新增测试文件的词法空白及新import位置整理，不改逻辑或字符串，无额外行为变化。

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolApprovedInvocationStoreTest,ToolApprovedStartRecorderTest,ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolInvocationStoreTest,ToolWriteInvocationStoreTest,ToolActionApprovalSqlTest,FixedBpmnStartToolTransportTest,ToolExecutionJournalPostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-approved-start-recorder-final.log 2>&1
```

分项：AAStore26、AARecorder18、IDWRecorder23、RORecorder15、Store27、WriteStore17、ActionApproval13、StartPort8、PG1skip。没有app/DB/容器/browser/GUI/listener或实际IAM/HTTP/模型启动。

## 已审实现

三现有prod+新RecorderTest/原StoreTest/原PGTest+README/plan/evidence共9paths。bounded AA prepare同原prepareInternal/PREPARE/currentrun/E1E2/预算事务链，只新projection采用原entry t0/n0；完成旧wait/advanced own只metadata，不重核旧cap issue epoch/dispatch期限活性，完整owner/request/lease/budget epoch与backlink仍严。firstwait finished/SETTLED0，即使receipt也先返回；existingglobal+freshUUID不另reserve准备预算。旧public/IDW/RO逻辑保持。

Recorder fixedAA/instanceId/estimated0+canonical nullableHOST receipt，原entry在material读前捕获；有效Tooltip/run界，prepare技术10s与后续actual call分开但不reset。E0先于approval，E1全等且consume核E2；actual consumer与business executor独立SERVICE同tenant。最终µ期限先确定再fresh DISPATCH PDP，并同值传bounded start。仅首nonce attempt发送一次。preconsume失败无cleanup；consume后分类并用原nonce记事实，fact holder先于observer，observerFailure优先于两个port外层包装，之后才本次fixedPS known0账务。metadata/generic非零竞争不结算或猜0，UNKNOWN不重驱。

new ApprovedStartResultVO四字段为actionIntentId/actionStateObserved/actionRevisionObserved/nullable ownAttempt；early用prepare.action，consume后用实际begin.action，loser先进状态不伪WAIT/NONE，winner可Observed DISPATCHING+later own SUCCEEDED，不合成全局终态。只有实际返回wait才notify AWAITING；continuing不提前NONE。

Root全文审阅3prod差异、新18tests与PG84行，独立journal审阅Recorder/tests、PG与README PASS；其发现的一处旧pending说明已作同scope文档修正。PG controlled ports核真实DB DISPATCHING/同µdispatch_not_after、sameUUID wait、freshUUID SETTLED0、Observed与observer、外层mask后fact/budget保留；旧URL/vector/V13→V14/@Timeout120保持，仅compiled+skip。23existing SQL逐字节未改，旧ports/SDK/codec/AS/native/config/权限/依赖保持。原17Store测试正文逐字节保持；测试fixture只加explicit tool/init/cost参数，旧默认.20保持，Recorder场景显式ZERO而非按schema猜费用。

## Requirement closure

| Alias | Status | Evidence | Boundary |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | AA wrapper/projection原授权事务链、new9反例+26GREEN | 完整历史lease/budget身份仍严格，metadata非permission |
| PLAN-REQ-002 | Satisfied | fixedAA/HOSTreceipt、原entry/E0E1E2/freshPDP/同µboundedstart，18新tests | controlled ports非真实crypto/网络 |
| PLAN-REQ-003 | Satisfied | nonce原子consume→once→fact，Observed用真实prepare/begin快照 | 非连续global current状态，旧body访问独立权限 |
| PLAN-REQ-004 | Satisfied | 双outermask/observerFailure/0accountPending/generic .03 metadata/known与UNKNOWN tests | facts不撤、不release/retry，不影响terminal run |
| PLAN-REQ-005 | Satisfied | 9scope/23SQL/旧路径回归、实际RED→147PASS1skip/独审 | cached/Git最后Root执行；全Goal仍Partial |

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR Recorder/Store/SQL技术架构，9scope | 无新层/引擎/CRUD，Agent generator excluded | None |
| MC-REUSE-001 | Applicable | PASS | 现ports/prepareInternal/nonce/account/codec/UoW-CAS | 无认证/transport/JSON/状态引擎重造，AA不旁路PREPARE | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/SDK与POM无改 | 无新依赖/构建步骤 | None |
| MC-NAME-001 | Applicable | PASS | ApprovedStartResultVO与现technical facade/projection | Observed命名准确，不冒连续当前状态 | None |
| MC-VALID-001 | Applicable | PASS | fixedprofile/HOSTreceipt/fullrun/entry/E0E1E2/audit/nonce/bounded deadline | 当前权限/typed guards闭合；constructed observation非认证 | None |
| MC-MODEL-001 | Applicable | PASS | immutable四字段record/旧VO guards保持 | 无新entity/宽松DTO，等待ownnull且观测快照真实 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed ports/DTO/codec直接复用 | 无PO/entity/Mapper或publicJSON转换 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志，固定safe errors/脱敏VO | 不泄hostreceipt/input/token/JDBC或observer私密cause | 无日志面变化 |
| MC-BEAN-001 | Not applicable | N/A | 原5arg constructor/pointers不改，无Bean/DI新增 | Autowired/Lombok构造修订不新增适用对象 | 无Bean面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK java.time/SecureRandom/Atomic/UTF8与现helpers | 无新通用工具/依赖/框架 | None |
| MC-JSON-001 | Applicable | PASS | 原codec/ports/SDK/HTTP合同不改，旧enum无ordinal持久化 | 无wire字段漂移或second parser | None |
| MC-TIME-001 | Applicable | PASS | material前t0/n0、prepare≤10/actualTooltip分离、E0/µ先PDP/原deadline到HTTP | 不reset/隐式扩窗；latefact原nonce独立窗口 | 真IAM/DB时钟待用户 |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/YAML/flags/权限注册改变 | explicit库，native/config另步 | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | 现Facade+callback Adapter+UoW-CAS，fact/observer/account顺序 | 解决真实部分失败/竞争变化点，无新执行器或层 | None |
| MC-SCOPE-001 | Applicable | PASS | 9paths/23SQL/旧ports-AS-native-config readonly | 仅必要bounded prepare与AA Recorder/tests/docs | 最后cached范围Root核 |
| MC-TEST-001 | Applicable | PASS | prep有效RED26、Recorder有效RED18、44/147GREEN、PG1skip/独审 | 1测试断言NPE改明确assert后重跑，未冒fixture或PG通过 | runtime待用户 |
| MC-BLOCKER-001 | Applicable | PASS | 5aliases/17MC10Rules/source独审无openfinding | 身份/时间/费用/Observed/事实合同闭合 | 最后cached/stage/commit待Root |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 新VO后缀/既有technical facade/Store/Sql | source/compile | 无实体命名漂移 | None |
| Rule 2 | Applicable | PASS | typed/domain/HOST receipt/run/entry/nonce/current authority guards | new9/18正反与旧回归 | 内部technical handoff完整，旧入口不弱化 | None |
| Rule 3 | Applicable | PASS | 仅immutable新4字段record，旧DTO守卫保持 | 值guards/Observed与fee tests | 无entity/Lombok/Mapper对象转换需求 | None |
| Rule 4 | Not applicable | N/A | 无新Bean/DI/log，原constructor不动 | 9scope | 不加无关Service/日志/注入注解 | 沿现技术模式 |
| Rule 5 | Applicable | PASS | JDK/现helpers/ports/codec复用 | imports/POM | 无新通用工具/依赖 | None |
| Rule 6 | Applicable | PASS | 原JSON/SDK/receipt不动，新projection内部enum无ordinal | oldports/codec bytefreeze/compile | 无外部协议改变 | None |
| Rule 7 | Not applicable | N/A | 无YAML/profile/flag变化 | exact9scope | 多环境配置不受影响 | 无配置面变化 |
| Rule 9 | Applicable | PASS | Facade/Adapter/UoW-CAS原模式 | nestedcallbacks/竞争/latefacts矩阵 | 不新建工作流/状态/调度引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/ChronoUnit/原nano | beforematerial/phasebudget/µdeadline/latefact tests | 无java.util时间/原预算reset/假当前时刻 | None |
| Rule 11 | Applicable | PASS | 现AR技术架构/23SQL immutable/原CQE保持，Agent excluded | source/compile/scope/bytefreeze | 无CRUD/MP迁移/软删除业务或事件新层 | None |

## 结论与未验证边界

本Step Verified（nonruntime，PRECOMMIT）；5aliases Satisfied，17MC/10Rules逐项PASS或有据N/A，Rule11PASS。Root全文/strict/resources/checklist与source/独审完成，最后cached/staging/commit待Root，不预测hash，不伪Accepted。

未自动启动app/DB/容器/browser/GUI/listener，未apply/repair旧迁移、激活权限/授角色、push/PR/release。原23SQL/AS/native/config/SDK/ports不改。147PASS+1PGskip仅source/controlled测试，真实IAM/crypto/网络/模型/数据库仍需用户环境。AA native/hostreceipt resolver/config readiness、MCP与后续蓝图继续，完整Goal仍Partial。用户COLA skill rename保持未改未提交，使用现execute-plan路径。
