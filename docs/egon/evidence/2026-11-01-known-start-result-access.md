# T3-07 当前授权原START结果访问执行证据

| 项目 | 当前记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；Source GO 08:01，最终完整回归08:23:25 |
| 执行计划 | [knownSTART result access](../plan/2026-11-01-07-50-known-start-result-access.md)，Review |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§11.3/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `65e3b5eb40afe18ceb965dc78dd43d6af63d48007e7388d0d5bb080566a81df7` |
| Baseline | `7396596b41f3e36c6dd5a4e28a947269df7cc988` |
| Source GO | root于08:01全文审阅514行后发出RED-only GO；pre-GO plan SHA-256 `1143cb668e6da367392ce725eff331d2f60d5547418c3826245166cb267b7a21`；strict、executing resources15md19refs与step-start checklist通过 |
| 整步结论 | **StepVerified（nonruntime）**；完整11类175PASS+1PGskip，源码/独审/静态门禁已核；完整T3-07仍Partial |
| 提交 | 提交前Verified快照；root已完成全文/end-checklist和exact12paths cached检查；随后semantic提交，hash见Git与蓝图进度；Review非Spec Accepted |

## 1. 已验证范围与源码事实

十二paths：新Agent V14 SQL；原ToolExecutionStorageVerifier/Test；新ToolResultAccessSql/Store/Recorder及StoreTest/RecorderTest；原ToolExecutionJournalPostgresTest、README、新plan和本报告。schema owner3、budget owner3、root Facade/Test/README3、journal_design原PGhelper1、docowner2。root在原PG文件无自身待应用patch时作此所有权移交，已记plan §4.3；不扩大合同、路径或单Step/commit。

root完整审阅最终SQL后批准创建唯一V14，当前SHA-256实读为`88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a`。没有应用SQL。新表25个NOT NULL列、4CHECK、PK/UNIQUE/4FK及intent index，不复制body/receipt/nonce/executor/dispatchstate/source-dispatchpointer/cost。新optional verifyResultAccess复用原verifier；旧verify保留13/3table兼容，新Store独立readiness要求14。ACCESS只要求SELECT/INSERT，budget仍可UPDATE锁定。root最终核对全部22个旧migration SQL与baseline逐字节相同；V13 SHA仍为`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`。已存在的新V14也immutable，二进制回退保留additive表及源码migration，不Git删除/改写，不repair/backfill/drop。

最终源码已对照：新Store重建IDW/START完整request、owner/run/lease/ref/backend与required STRING instanceId；参数SHA来自descriptor.parametersSha256(action.arguments)，严格独立于full action.requestSHA。每次body读取执行fresh RECONCILE/E1/PDP/E2，审计与request精确匹配。窗口从原entry wall/mono派生，min(entry+10s、runDeadline、entry+effectiveToolTimeout、E1expiry)，不reset。original fullnano CheckResult/Audit用于当前权；仅在通过后另造floorMICROS历史AuditVO，JSON/scalar一致且checkedFloor<=DBcreated<expiryFloor，不从投影构造CheckResult。

Sql同源事务按run→cap→action→access/budget处理，exacthost在reserve前读取；quota满的exact replay不新计数，regularinv/orphan/cross-type冲突拒绝。源仅APPLIED→strictSTART SUCCEEDED、REJECTED/FAILED_KNOWN；每读重算source revision/canonical outcome SHA。原Outcome actualCost为null/非零均保持，仅localaccess预算0；不更新source action/cap/inv/写预算。历史firstaudit/epoch/grant不覆盖，也不用于当前授权。重复fresh decisions不分别新增durable auditrow，这是首hostfact-only限制。

Facade实际顺序为Store事实COMMIT→bestEffort只ownaccess0 settle→同freshwindow在account/中断后最终check→public原Outcome。PENDING仅在window仍有效时返回trusted caller；future native必须SETTLED才供model。超时保留fact与已作结算、不cleanup/refund，不改变原结果费用。三类新生产代码、verifier差异、最终SQL与测试已由root全文审阅；API独审分schema/verifier、Store/Sql/Facade及测试、PG静态helper三部分全部PASS，无finding。

## 2. 实际执行记录

| 阶段/日志 | exit、计数 | 时长/结束时间（+08:00） | 证据与限制 |
| --- | --- | --- | --- |
| Source GO | root 08:01发出；只先最小声明/测试 | 08:01 | 全文514行、pre-GO SHA及strict/resources/checklist如上；当时无功能实现，V14待SQL审阅 |
| `/tmp/tianji-result-access-red.log` | exit1；45 total：13PASS、16fail、15errors、1PGskip | 18.078s；08:07:31 | Store18=9fail/9err；Facade10=5fail/5err；Verifier16=原13PASS+新2fail/1err；有效缺行为RED，无编译、fixture或资源失败 |
| `/tmp/tianji-result-access-verifier-facade-green.log` | exit0 BUILD SUCCESS；Verifier17+Facade10=27PASS、1PGskip | 20.594s；08:10:15 | verifier第17项为RED后增加的V14 SHA静态freeze测试，不虚构其先前RED；原optin PG class skip |
| `/tmp/tianji-result-access-green.log` | exit0 BUILD SUCCESS；Store18+Facade10+Verifier17=45PASS、1PGskip，0fail/errors | 21.970s；08:13:42 | 全组件首次聚焦GREEN，尚未整步验收 |
| `/tmp/tianji-result-access-regression-red.log` | exit1；Store18中1fail、17PASS、0errors | 16.408s；08:16:09 | 审阅发现`FOR UPDATE OF a,b`会要求access UPDATE权限；先补SQL断言观察真实RED，未在PG运行 |
| `/tmp/tianji-result-access-fixed-green.log` | exit0 BUILD SUCCESS；45PASS、1PGskip，0fail/errors | 21.851s；08:17:23 | 生产仅改为`FOR UPDATE OF b`，依赖既有run序列化与budget锁，不锁需insert-only权限的access row |
| `/tmp/tianji-known-result-access-final.log` | exit0 BUILD SUCCESS；175PASS、1PGskip，176 total，0fail/errors | 23.343s；08:23:25 | 精确计划11类完整source-reactor成功；新增PG helper136行已编译，缺显式前置仅skip，未在PG运行 |
| 最终source/scope/旧SQL/独审 | PASS（precommit snapshot） | root最终静态审查 | 精确12paths、旧22SQL及原5核心实现逐字节不变；无operational stub，diffcheck/Review strict/resources通过；最终cached/Git仍由root执行 |

RED分类保留原始统计：Store的9fail包含7项expected Ledger却收到UOE、1项affinity恒false、1项VO缺校验，另9项直接UOE；Facade的5fail为3项expected Ledger却收到UOE与2项缺校验，另5项直接UOE；Verifier新3项为2项wrong-UOE failure与1项直接stub error。Store.verifyStorage在其自身有效RED时是compile-only no-op，测试合法fixture只为到达record stub，不能把它写成已实现readiness。之后功能实现才进入GREEN。instanceId空白假设不是生产bug：原OrchestrationChecks.id已经拒绝trim变化，补覆盖通过而无相关生产改动。

root最终执行精确计划11类，工作目录`tianji-service`；docowner未运行Maven：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolResultAccessStoreTest,ToolResultAccessRecorderTest,ToolExecutionStorageVerifierTest,ToolIdempotentStartRecorderTest,ToolInvocationRecorderTest,ToolWriteInvocationStoreTest,ToolInvocationStoreTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest,ToolExecutionJsonCodecTest,ToolExecutionJournalPostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-known-result-access-final.log 2>&1
```

最终分类：ActionStore23、AccessStore18、ActionSql10、Codec21、StartRecorder17、InvocationStore27、WriteStore17、RORecorder15、AccessFacade10、StorageVerifier17，共175PASS；原PG class1SKIP。新增PG136行覆盖13→14/history、known3、同host并发/预算及原子回滚等场景的源码已编译；原URL/vector/Timeout120保持。PG源费用使用0，null/非零原费用保持由受控unit验证，不声称PG已验证这些费用或SQL/锁/并发行为。

Agent generator excluded，无CRUD/新依赖/MP迁移体系；永久technical dedup非softdelete业务entity。旧5核心实现ToolInvocationRecorder、ToolInvocationSql、ToolInvocationStore、ToolActionIntentSql、ToolExecutionJsonCodec均与baseline原样；新访问不更改旧执行语义。

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 原known结果 | Satisfied | AccessStore18/Facade10验证Known3、sourceSHA/revision及原null/非零费用；完整回归通过 | 非业务重派，不用新读取0覆盖原结果费 |
| PLAN-REQ-002 每次fresh权/window | Satisfied | 每次RECONCILE/E1E2、独立paramSHA、fullnano→µ历史、短policytimeout、account后同窗门禁 | 首audit不授新权，重复决定仅首hostfact持久审计 |
| PLAN-REQ-003 原子预算/访问去重 | Satisfied | quota/exactretry/collision/orphan/rollback及权限回归先RED后GREEN；不更新source预算 | 并发/SQL真实PG未运行，PGhelper只编译 |
| PLAN-REQ-004 additive存储兼容 | Satisfied | V14一次创建/hash冻结、Verifier17、旧13兼容/新14必需、ACCESS无UPDATE要求；旧22SQL相同 | 新SQL未应用，二进制回退仍保留表与migration |
| PLAN-REQ-005 account/egress/真实验证 | Satisfied | Facade10及完整175PASS+PG1skip；ownaccess0、Pending/截止/中断和sourcecost分离 | trusted caller可Pending，future native须SETTLED；12path提交由root执行 |
| 完整T3-07/平台蓝图 | Partial | 本步显式库/source验证完成 | native/hostmetadata/inputquota/effectaggregation/AA/MCP及真实IAM/PG/模型后续 |

## 4. Blocking Manual Check

以下为已验证precommit snapshot逐项结论；源码/测试/独审已闭合，最终文档与cached stage/Git门禁由root执行，不虚构提交或runtime验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR技术JDBC/独立Flyway、3新访问类与V14 | Agent generator excluded；无新层/MP迁移或引擎，永久dedup非softdelete实体 | None |
| MC-REUSE-001 | Applicable | PASS | Action.lockForWrite/fullRow、cap/ledger/PDP/budget/codec/verifier helpers | 复用现扩展点，无复制身份/快照/JSON/状态引擎 | None |
| MC-DEP-001 | Applicable | PASS | root精确12paths/POM无改；新imports为现依赖/JDK | 无新依赖/生成器/构建步骤 | None |
| MC-NAME-001 | Applicable | PASS | Request/RecordedAccess/DisclosureWindow/StartResultAccessVO及3类职责 | 不可变record与技术类命名明确，toString脱敏 | None |
| MC-VALID-001 | Applicable | PASS | AccessStore18/Facade10；owner/action/lease/E1E2/typedAudit/source/collision/window | 每次body新权；known3/source漂移/错误边界失败闭合 | None |
| MC-MODEL-001 | Applicable | PASS | exact25列insert-only事实、7field结果，sourceOutcome原值 | 不复制源body/receipt/cost，不新增softdelete/可变state实体 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed domain/codec与本域projection直接复用 | 无新PO/entity映射层；源null/nonzero BigDecimal值保留 | 无需新增Mapper |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志或Service；固定安全异常/脱敏值 | 不记录正文/receipt/nonce/token，未用日志替代错误处理 | 无日志注入变更 |
| MC-BEAN-001 | Not applicable | N/A | manual技术库constructor+exactaffinity | 无Bean/factory/DI配置、HTTP/native consumer或provider新增 | 沿现技术库构造模式 |
| MC-UTIL-001 | Applicable | PASS | 原JDK/OrchestrationRequestDigests/codec/事务/ledger | 无自建通用工具/认证或线程框架 | None |
| MC-JSON-001 | Applicable | PASS | 原strict11key audit/5key outcome codec不改；sourceSHA用canonical encoding | fullnano currentproof与µ历史JSON/scalar分开；非jsonb text哈希 | None |
| MC-TIME-001 | Applicable | PASS | Store18/Facade10含nano/MICROS/短timeout/回拨/account截止与中断 | 原entry wall+mono不reset、不roundup；锁后actualDBlease；无过期body | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/flag/配置/权限/自动装配路径改动 | V14只源码未apply，显式库未native接线 | 无配置面变更 |
| MC-PATTERN-001 | Applicable | PASS | 现Facade+UnitofWork/SQLparticipant、verifier复用 | fact+budget同TX、先dedup再reserve、account后最终gate，直接职责划分无新抽象 | None |
| MC-SCOPE-001 | Applicable | PASS | root核12paths/旧22SQL及原5核心实现不变、V14一次hash冻结 | 无V13 ALTER/旧VOstate/Recorder/port/provider/native/AA/MCP/依赖权限扩大 | None；root已核exact12 cached路径/check/stat/name |
| MC-TEST-001 | Applicable | PASS | 有效RED/权限regression RED与修复、完整175PASS+PG1skip | 新PG136行已编译未运行；静态SHA项非运行证明 | 真实环境留待用户验收 |
| MC-BLOCKER-001 | Applicable | PASS | root全文+独审3部分无finding；source/tests/static/strict/resources闭合 | 当前source门禁无未决问题；Review不伪Accepted，全平台Partial | None；root已读最终报告/end-checklist，exact12 cached检查通过；Git hash按提交后记录 |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 3技术类与明确VO名称/25列 | 编译+Request/Result guards与redacted测试 | 名称与实际角色一致 | None |
| Rule 2 | Applicable | PASS | current RECONCILE/fullaction/paramSHA/lease/E1E2/source与egress | AccessStore18/Facade10 | 技术边界复用域构造与exact校验，非HTTP DTO层，无新增校验框架 | None |
| Rule 3 | Applicable | PASS | 真immutable records、permanent技术fact，不新entity/Mapper | VO/schema/static与sourcecost断言 | record豁免适用于不可变值；不造普通实体record | None |
| Rule 4 | Not applicable | N/A | 无业务Spring Bean/日志Service；manual技术constructors精确同源 | affinity/ambient测试，配置无变动 | 不伪造Lombok DI/新增日志 | 沿现技术库模式 |
| Rule 5 | Applicable | PASS | 原组件/JDK/codec/digest复用 | imports/12path检查，无POM改动 | 无新utility或依赖 | None |
| Rule 6 | Applicable | PASS | 现Jackson codec/字符串状态编码，不改原codec/enum | 旧Codec21与历史µ/sourceSHA测试 | 无ordinal、原Outcome费用精度保持 | None |
| Rule 7 | Not applicable | N/A | 无环境配置文件/flag改动 | 精确12paths静态检查 | 无配置key parity变化或自动激活 | None |
| Rule 9 | Applicable | PASS | 现Facade/UnitofWork/SQLparticipant | atomic/quota/collision/rollback/account/egress矩阵 | 具体并发与事实披露变化点复用既有模式，无新State/handler引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/LongSupplier/MICROS | fullnano proof、历史floor、mono/DBclock/account后窗口测试 | 无java.util时间实体、不用历史过期audit当当前许可 | None |
| Rule 11 | Applicable | PASS | 已授权Agent technical JDBC/独立Flyway、generator-excluded、唯一V14 | 旧22SQL字节相同、old13/new14与最小privileges测试 | 不扩大架构；permanent dedup无deleted_at；无CQE事件新增 | None |

## 6. 结论与未验证边界

本Step达到**StepVerified（nonruntime）**：5aliases Satisfied，17MC/10Rules逐项PASS或证据支持N/A；原结果费用保持、每次当前授权、独立访问预算/首审计原子与最终body门禁已在源码/受控测试验证。root最终cached范围与文档门禁已通过；本报告记录提交前Verified快照，不填自身未来commit hash。

全部22旧SQL字节保留，V14 SHA冻结且未应用；原5核心实现不变。没有新增public Store正文API或transport/POST重发，不触源写预算。PENDING可向当前获权trusted caller返回，future native须SETTLED；重复fresh decisions只保首hostfact audit，不声称逐次durable决策日志。原null/非零源费用unit已验，PG source0/known3仅编译，非真实SQL/锁/并发验证。

无真实IAM/PDP/crypto/HTTP网络/LLM/PG验收，无app/DB/listener/容器/浏览器/GUI/native scheduler启动、DDL apply/repair/backfill/drop、权限激活/角色grant、push/PR/release。无新repo scratch/scripts/work/harness；测试保留subsystem。完整T3-07/Goal仍**Partial**，native/hostmetadata/inputquota/effectaggregation/AA/MCP及运行验收后续。docowner仅更新两docs，未改source/tests/README、未构建或提交。
