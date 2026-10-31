# T3-07 通用IDW journal原子转换执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度05:51—06:18 |
| 执行计划 | [IDW原子journal](../plan/2026-11-01-05-41-idempotent-write-journal.md)，Review；legacy exact用户授权，未虚构Spec Accepted |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/12/13、T3-07 |
| 主设计实施前SHA-256 | `078e0fc999e6531783e134bd880675aad5ce90d7b801d1d1da972732f5370b80` |
| Baseline | `c155273186f93e43b5694ba34c999fc137635a37` |
| 门禁 | root全文审阅原404行plan、Review strict与executing15 Markdown/19 refs通过，05:49 RED-only GO；ownership/pattern/capacity delta已由root审阅 |
| 实施状态 | **Verified（非运行验证）**：最终148通过、1原PG skip，0 failures/Errors；完整T3-07/平台Partial |
| 提交状态 | 本报告为**提交前Verified快照**；root已完成全文/delta与显式九路径cached检查，随后单独提交，最终hash以Git及蓝图进度为准 |

## 1. 实际九路径与所有权

| Operation | Repository-relative path | 最终owner |
| --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionIntentSql.java | Action owner |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolActionWriteSqlTest.java | Action owner |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java | Action owner |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationStore.java | Budget owner |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationSql.java | Budget owner |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolWriteInvocationStoreTest.java | Budget owner |
| MODIFY | README.md | root |
| CREATE | docs/egon/plan/2026-11-01-05-41-idempotent-write-journal.md | doc owner |
| CREATE | docs/egon/evidence/2026-11-01-idempotent-write-journal.md | doc owner |

05:56在Action组件33项GREEN后，root将尚未编辑的PG helper从原owner明确释放后交Action owner；只调整内部所有权，九路径/合同/单逻辑commit不变。旧ToolInvocationStoreTest27源码未改，新增focused fixture留在新Test中。root最终静态核全部22个原migration SQL与baseline字节相同，V13本次实测摘要仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际实现与语义边界

新增四个package IDW入口，拒绝READ_ONLY/APPROVAL_REQUIRED且重建完整action/binding；保留原publicprepare/readonlyguards/contracts。WritePreparationResultVO仅metadata action+nullableinv/budget同presence；WriteDispatchBeginResultVO仅metadata action/inv+首winner attempt，无prepare/begin隐藏outcome或新私密终态读体API。

IDEMPOTENT_WRITE_METADATA沿原一次typed PREPAREcheck/E1/ownedTX/live当前run/E2。action仍unclaimed PREPARED且expiry未来时，新hostUUID/当前合法run可申请自身预算和inv，包括同owner不同run；firstorigin/firstexpiry保持，不锁oldorigin run、不改旧attempt。PREPARED不授独占firstattempt。advanced/wait/closed/expired仅事实metadata，无新reservation/inv；exact自身advanced可返metadata+budget。每freshattempt经原reserveBudget计自身TOOL调用与active capacity，loser关闭不是refund，不触旧run counters。旧public existingaction facts-only语义保持，orphan预算不被retrofit。

begin要求fresh DISPATCH的actionIntentId=稳定action.intentId，原requester/type/grant/audit checkedAt与完整callDeadline匹配，E1/E2完全相等，实际DB lease/cancel/deadline/source/policy在锁后重核。锁序currentrun→cap→action→owninv/budget；globalaction PREPARED/unclaimed且owninv PREPARED才CASwinner。先inv DISPATCHING后action DISPATCHING以满足V13即时backlink，epoch1/nonceSHA/actual SERVICE executor/links/audit/time/effectUNKNOWN一致；两次写后才fresh fullAction JOIN，确认COMMIT后仅winner有attempt。原callDeadline/shortbegin wall+mono窗口不reset，commit ACK可在提交后窗口耗尽时保留原attempt，非执行许可。

action已advanced时仅关闭该自身仍PREPARED inv，保存caller给定FAILED_KNOWN/empty/nullreceipt/ACTION_NOT_DISPATCHABLE的Outcome；winningaction与其他旧attempt不变。自身advanced只metadata，无重新dispatch UNKNOWN/APPLIED/terminal。cleanup同样action先锁、只本PREPARED inv，error为原八allowlist加ACTION_NOT_DISPATCHABLE，advanced不覆盖；absence可empty，primary failure不误作absence。

所有新loser/cleanup费用均由caller supplied Outcome带入，nullable/非零原样保留，不硬编码0、不自动settle/refund/释放capacity。generic completion允许真实actualCost>estimate，不因成本超估丢掉事实；原SETTLED预算cost>estimate parser guard保留。新unit Rig.budgetUnknown及PG helper在ledger.finish之后安排的UNKNOWN预算是controlled seed，用来核真实outcome与已有UNKNOWN预算历史可读；**没有实际调用BudgetService执行overrun settlement，不构成accounting闭环证明**。Store没有新增IDW settle，未来fixedSTART Recorder才可明确提供known0。

Action lowerparticipants复用exactowner锁后freshJOIN与sameAction；beginWrite/completeWrite仅IDW CAS、无mutator内read。完整tenant/type/issuer/id/request/ref/risk/revision/epoch/nonce/executor/immutable时间字段保护，影响行数必须1。timestamp必须MICROS exact且≤DBnow，begin>=updated且<actionexpiry，complete>=updated但不要求旧expiry仍有效；MAXrevision拒绝。Unit of Work由现TransactionTemplate+transaction participants/CAS承载两表变化，任一Action CAS/trigger失败回滚先前inv更新，不新增pattern类、Handler/Stateengine。

completion持原nonce preimage/attempt/epoch/executor/audit/caphistory与fulltuple，只identity run→action→owninv/budget，不查currentPDP/live/lease/currentcapepoch/grant/oldexpiry。private boundedfact helper DB前清Thread.interrupted、finally恢复；prepare/begin仍拒中断。current action exact同tuple则inv+action原子完成，SUCCEEDED→APPLIED/CONFIRMED+receipt、rejected/failed→NONE、unknown→UNKNOWN；后freshJOIN。若完整有效current action已指向另一positive generation/nonce/run/inv，仅完成认证过的旧inv并保current action；unlinked/epoch0 action与已dispatch owninv矛盾则拒绝。本步没有新generation/redrive API。已知同outcome幂等/改变冲突、UNKNOWN同原nonce可收敛known，没有自动重试。

共享requireAttempt约12行原证明从readonly complete逐字抽出供IDW复用，原RO字段/比较/失败语义不变。GREEN阶段将新测试programming-invalid risk/code/VO断言纠正为原既有IllegalArgumentException；授权/租约/evidence不可用/冲突仍为AgentLedgerException，没有新异常类型或放宽成any Runtime，最初真实RED计数保持。README两段由root说明generic IDW journal而非actual write execution。

## 3. 实际RED/GREEN与验证限制

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据与分类 |
| --- | --- | --- |
| Action有效RED | 05:51:45；10项6 failures/4直接Unsupported stub Errors/0 skips；exit1，16.407s | `/tmp/tianji-idw-action-red.log`；真实缺失行为，无fixture/compile错误，不伪写0 Errors |
| Action聚焦GREEN | 05:54:51；新Action10+旧ActionStore23=33通过、0 failures/Errors/skips；exit0，20.523s | `/tmp/tianji-idw-action-green.log`；root全文与独立审阅通过 |
| Outer有效RED | 06:07:52；28总：新Outer17项3 failures/14 Errors，Action10通过、原PG1skip；exit1，15.521s | `/tmp/tianji-idw-outer-red.log`；2项wrong-exception因stub、1项VO invariant未拒绝，14直接缺操作stub Errors；无fixture/compiler失败。expiredPREPARED/VO两场景已在RED前加入，15→17 |
| 新组件聚焦GREEN | 06:18:27；新Write17+Action10=27通过、原PG1skip，总28，0 failures/Errors；exit0、BUILD SUCCESS，21.797s | `/tmp/tianji-idw-focused-green.log` |
| 最终exact计划selector GREEN | 06:18:56；148通过+原PG1skip，总149，0 failures/Errors；exit0、BUILD SUCCESS，18.653s | `/tmp/tianji-idw-journal-final.log`；完整source-reactor成功，非全仓库测试全部执行 |

实际最终命令，cwd=tianji-service（root执行；报告作者未运行Maven）：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolWriteInvocationStoreTest,ToolActionWriteSqlTest,ToolInvocationStoreTest,ToolActionIntentStoreTest,ToolInvocationRecorderTest,ToolExecutionJsonCodecTest,ToolCapabilityStoreTest,AgentBudgetLockOrderingTest,ToolExecutionJournalPostgresTest,ToolExecutionStorageVerifierTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-idw-journal-final.log 2>&1
```

| 最终测试组 | 通过 | 跳过 | 实际证明范围 |
| --- | ---: | ---: | --- |
| 新ToolWriteInvocationStore17、新ToolActionWriteSql10 | 27 | 0 | IDW metadata/准入/唯一claim/atomic次序与故障/nonce/late/futurepointer/成本/interrupt/cleanup |
| 旧ActionStore23、InvocationStore27、Recorder15 | 65 | 0 | 原准备/历史/publicmetadata/readonly与nonce事实语义，旧27源码不变 |
| Codec21、Capability14、BudgetLock8、StorageVerifier13 | 56 | 0 | 原closed JSON/cap/预算锁顺序/schema合同兼容 |
| 原ToolExecutionJournalPostgresTest | 0 | 1 | 新PG helper约215行已编译；默认URL/pinnedvector/Timeout120原前置未改，缺optin未运行 |
| 合计 | 148 | 1 | 10类选择回归，不是平台运行验收 |

PG helper源码含20contenders/单winner、begin+completion Action trigger rollback、run终态/cap刷新后的late事实与futureepoch2 pointer旧inv-only；root静态/API审阅通过，**只有源码和编译证据，未执行真实PG/SQL/锁/trigger/concurrency**。unit为controlled JDBC/Clock/事务及UNKNOWN预算seed；没有真实IAM/PDP/crypto、业务网络/listener、模型或实际accounting闭环。本步没有child JVM probes。既有upstream Jackson2/deprecated API和测试JVM类warning保留，未引入本步依赖升级。

root已全文读3产线diff、新17约560行/Action10/PG helper/README，独立审阅覆盖全部3块(Action、PG静态、Outer)均PASS无发现；无额外build，最终无operational stubs。root已重读end-Step checklist，source/test/README/manual/static审查无新finding；新ToolWriteInvocationStoreTest仅移除一个EOF空行，不改测试逻辑，不重复功能测试。root静态核exact9paths、旧22SQL bytes/V13、Review strict/resources与diffcheck通过。本报告/delta和最终cached检查须root在commit前最后确认；若失败回报告修复而不提交，不预填已提交事实。

## 4. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 非独占action准入 | Satisfied（本步） | 新17：跨run/UUID独立预算/inv、origin/firstexpiry保持、expiredPREPARED无attempt/VO metadata一致、旧legacy不变 | PREPARED不授exclusive permit，不touch旧attempt/旧run counters |
| PLAN-REQ-002 唯一claim/双表开始 | Satisfied（本步） | Action10+新17：fresh exactaction audit/fullcall/E1E2、inv先action/onlywinner/loser只owninv、rollback/commitACK | 仅journal，无实际business dispatch/AAconsume |
| PLAN-REQ-003 原nonce历史完成 | Satisfied（本步） | samepointer pair、futurepointer旧inv-only、nonce/generation/executor/audit、known幂等/UNKNOWN收敛/中断恢复 | 无新generation/redrive/private terminal body replay |
| PLAN-REQ-004 generic费用/cleanup | Satisfied（本步） | supplied nullable/nonzero failurecost、actual>estimate fact+controlledUNKNOWNbudget、原guard/no settle/loser非refund | 非实际BudgetService overrun settlement/accounting闭环；未来fixedSTART可explicit0 |
| PLAN-REQ-005 范围与真实验证 | Satisfied（本步） | 148pass/PG1skip、独审/9paths/旧22SQL固定/旧27源码不改、原RO保持 | Recorder/native/AA/config/publicAPI/migration/settlement及真实运行未交付 |
| 完整T3-07/平台蓝图 | Partial | 当前generic IDW journal能力 | 写Recorder/审批消费/native配置/真实IAM/PG/模型及平台后续未完成 |

## 5. Blocking Manual Check

以下为本步非运行实现验证结论；commit前root文档/cached审查与实际Git提交单独记录，不等于整个蓝图完成。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR persistence三产线与九明确paths；旧public/RO源码边界 | 无新层/模块/Recorder/native/config/API/迁移 | None |
| MC-REUSE-001 | Applicable | PASS | 现Unit of Work=TransactionTemplate+participants/CAS，Projection/commonRow/ActionRow/ledger/cap/typedcheck | 同变化点扩展；requireAttempt逐字复用原RO证明，无第二执行/认证/预算平台 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/Jackson/JDBC/Clock；root最终依赖/scope审查 | 无新POM/dependency/utility/framework | None |
| MC-NAME-001 | Applicable | PASS | WritePreparationResultVO/WriteDispatchBeginResultVO、原metadata/attempt/result、Action participants | package immutable值职责清楚，nonce明文只caller局部，toString脱敏 | None |
| MC-VALID-001 | Applicable | PASS | IDW/fullaction/E1E2/typed audit/actionID/fullcall/nonce/history/actionpointer/CAS | 新17+10正反/故障，programming invalid精确IAE，authority/lease/evidence/conflict保Ledger | None |
| MC-MODEL-001 | Applicable | PASS | nullable inv+budget成对、无隐藏outcome的VO、原typed域值 | 新增VO metadata不变量真实RED后闭合，无实体/Mapper副本 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed domain/codec/metadata投影直接使用 | 无跨层实体转换/Converter新增 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术Store/SQL，无新业务日志Service | 固定安全错误/redacted，不记录nonce/参数/结果body | None |
| MC-BEAN-001 | Not applicable | N/A | 无Spring Bean/DI/config/public consumer新增 | 原manual技术构造/helpers保持 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK nonce SHA、codec、ledger与事务API | 无新utility/线程/认证平台或scratch harness | None |
| MC-JSON-001 | Applicable | PASS | 原closed codec/audit/typedOutcome，未改schema或encoder | null/nonzero/overestimate事实保留，原RO零费/SETTLED预算guard保持 | None |
| MC-TIME-001 | Applicable | PASS | fullcall与original短窗、独立fact10s、MICROS≤DBnow/maxrevision、interrupt清恢复 | 晚事实不被current live/旧expiry抹掉，begin/current检查仍在 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/property/flag/permission注册修改 | IDW仅内部journal能力，未自动派发/启用 | None |
| MC-PATTERN-001 | Applicable | PASS | 现Unit of Work/participants/CAS+single parser | 受控证明同pointer原子pair/旧inv-only保护futurepointer，不造Handler/Stateengine/Recorder | None |
| MC-SCOPE-001 | Applicable | PASS | root exact9paths、全部22原SQL byte-identical、V13固定、旧27源码未改 | AA/Recorder/native/config/API/settlement/费用猜0均未扩大 | None |
| MC-TEST-001 | Applicable | PASS | Action有效RED→33pass；Outer有效RED→27focusedpass；全selector148pass+PG1skip | 精确分类/旧regressions/独审全3块PASS；controlled UNKNOWN seed非actual settlement | None |
| MC-BLOCKER-001 | Applicable | PASS | root全生产/新17/Action10/PGhelper/README审阅及独立全3块审阅无发现；最终回归/strict/resources闭合 | 非运行实现验证无未决阻断；提交尚未发生 | None；root已读最终报告/delta，exact九路径cached check/stat/name检查通过；Git hash按提交后记录 |

## 6. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 新Write VO/package API与Action SQL参与者、原attempt/result | 编译及148相关通过 | 角色/metadata/attempt命名明确、全部redacted | None |
| Rule 2 | Applicable | PASS | fullaction/current audit/E1E2/nonce/backlink/CAS与failure shape | 新Store17/Action10、旧23/27及故障/时间/成本断言 | 当前权与历史proof分离，invalid类型保持既有精确异常 | None |
| Rule 3 | Applicable | PASS | immutable metadata-only DTO/原ActionRow/typedOutcome | 成对nullable/VO matching真实RED→GREEN | 无私密终态读体、可变结果或重复实体/Mapper | None |
| Rule 4 | Not applicable | N/A | manual技术Store/SQL，无业务Bean/DI/日志Service新增 | 九路径/日志安全审阅 | 不伪造Lombok业务注入/Bean验收 | None |
| Rule 5 | Applicable | PASS | JDK/JDBC/Spring/原ledger/hash/codec | imports/POM与实际编译 | 无新utility、线程、认证或依赖框架 | None |
| Rule 6 | Applicable | PASS | 原JSON codec/audit/outcome闭合保持 | Codec21/类型/成本/原readonly回归 | 不改JSONschema，generic真费用不猜零 | None |
| Rule 7 | Not applicable | N/A | 无config/flag/profile更改 | root scope/旧config保持 | 不自动注册写执行或扩大权限 | None |
| Rule 9 | Applicable | PASS | 现Unit of Work viaTransactionTemplate+participants/CAS/commonparser | 同TX inv先action/freshJOIN、rollback/oldfact/futurepointer测试 | 具体并发/部分失败变化点复用，无新pattern类/引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Clock/mono/MICROS/DBtime/interruptbookkeeping | 短窗/ACK、late/中断恢复、futurepointer/时间MAX正反 | callDeadline完整覆盖、fact无旧live门禁、时间不round-up | None |
| Rule 11 | Applicable | PASS | 原AR persistence/九paths/全部22旧迁移固定 | 148pass+PGskip/旧27源码不动/独审 | 无新层/SQL/权限/API/native/AA/Recorder/settlement扩大 | None |

## 7. 未验证、harness处置与提交门禁

Store/SQL-only不等于写执行已经开放。Recorder/native/AAconsume/private terminal output replay/config/publicAPI/migration/settlement均未交付；真实PG/SQL、IAM/签名/crypto、网络/模型与平台运行未验收。费用UNKNOWN seed与事实cost测试不声称实际结算/退款。

没有新建仓库scripts/work或临时harness文件；检查保留在对应subsystem Test/原optin PG类，未留下待定scratch。不自动启动app/DB/HTTP listener/容器/浏览器或GUI/native scheduler/长期runtime；未注册激活权限/授角色/开flag/部署/push/PR/release。

文档作者只修改本报告与计划机械澄清，未改source/tests/README、未Maven构建或提交，保留其他owner工作。root已完成最终报告/delta审阅、显式stage九路径与cached check/stat/name核对；本报告记录提交前Verified快照，随后执行单独semantic commit，实际hash见Git与蓝图进度。不填自身未来hash，不amend历史。

**PARTIAL — Spec requirements are unmet or unverified**：本步非运行实现已Verified、148项相关通过/1项PG跳过；root提交前文档与cached门禁已完成；完整T3-07/平台与真实运行仍未完成。
