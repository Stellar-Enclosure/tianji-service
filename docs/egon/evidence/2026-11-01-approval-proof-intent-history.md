# T3-07 审批证明与写意图历史投影执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度05:03—05:21 |
| 执行计划 | [审批证明与intent历史](../plan/2026-11-01-04-56-approval-proof-intent-history.md)，Review；root全文审阅/strict/resources后source GO |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `8f0d08781788d50ac92c04db262cc088122d520bbfa6ca706b6528246173bbb0` |
| Baseline | `d8786996f85a346cc63d98bfc29d59f48dffcecb` |
| 当前结果 | 最终107项中106通过、1 PG skip，0 failures/Errors；完整T3-07/平台Partial |
| 提交 | 待root完整tests/文档与精确九路径stage后产生，hash以Git/交付为准 |

## 1. 实际实现与边界

九路径为原ToolExecutionJsonCodec/Test、ToolActionIntentSql、ToolActionIntentStore/Test、原ToolInvocationStoreTest仅两直接Row fixtures，以及README、新plan、本报告。没有SQL/V13、AS/PS/C、ARnative、Grant/PDP/Budget、ToolInvocationSql/Store产线、配置/权限/依赖或公开API变化。

Codec新增包内ApprovalSourceVO/唯一ApprovalProofVO及approval写/读/sha三方法。sourceId是host registry稳定logical PS authority的约定，不是模型alias/consumer/policy/tool名称；构造本身不证明registry或远端身份。exact ASCII HTTPS issuer/resource保留原文本并限制长度/normalize/no query-userinfo-fragment，source namespace以新encodingTag TIANJI_TOOL_APPROVAL_SOURCE_V1及sourceId/tenant/issuer/resource摘要，不声称该tag原先已存在于SQL。

proof各层root/source/binding/consumer/approval分别exact4/4/7/4/18 keys，schemaVersion integral1、tenant long、scalar/enum严格，approved仅显式boolean true，UTF8≤16KiB，三时间须canonical UTC Instant.toString，并保留原DTO occurred<=verified<expiry及verified窗口≤30s。unknown/missing/duplicate/trailing/coercion/无效Unicode与超界均封闭且无私密cause。false是合法SDK投影但不能构造consumed proof。writer/reader均核sourceTenant=ownerTenant=consumerTenant、SHA256(exact issuer UTF8)=consumer issuerFingerprint，原binding.requireApproved(action,approval,approval.verifiedAt())重建完整action/query/source/model/task历史匹配，不使用Clock.now。proof SHA取canonical排序重编码UTF8而非原JSON字节/PGjsonb::text；空白/字段顺序不改变语义摘要。

共享outcome decoder补原V13已要求的exact五keys/status/output/receipt/error/actualCost类型检查，含nullable key不得缺失、actualCost必须null或numeric。shape检查后直接decode原numeric token bytes为typed BigDecimal，避免经JsonNode重写造成合法小数rounding；继续使用原ToolExecutionOutcome.validated核descriptor与费用范围/scale。原合法编码保持，不新增action专用parser或全局actualCost=0规则。

唯一Sql.row parser扩为Row15，保留原九accessors与public PreparedActionResult九metadata；nullable ApprovalConsumptionVO(proof,SHA,consumedAt)、DispatchVO(run,UUID,epoch,nonceSHA,actual SERVICE executor)均不可变/redacted。重建完整ToolActionRequest核logical identity/fullrequest/intentUUID/descriptor/backend/policy/owner/risk。raw approval七列必须全null或完整approved TRUE；后者与proof namespace/receipt/SHA/expiry一致，created<=consumed且verified<=consumed<expiry。IDEMPOTENT_WRITE所有state无proof；审批risk的PREPARED与positive dispatch必须完整proof，AWAITING_APPROVAL强制无proof。

完整八state验证：PREPARED/AWAITING无dispatch/result/error/closed且effectNONE；DISPATCHING positive且无结果/关闭、effectUNKNOWN；APPLIED映射typed SUCCEEDED、exact nonnull receipt/无error、effectCONFIRMED；REJECTED/FAILED_KNOWN matching typed outcome/error、无receipt、effectNONE，可无dispatch或完整dispatch；UNKNOWN必须positive dispatch/typed UNKNOWN/error、无receipt、effectUNKNOWN；EXPIRED无dispatch/outcome/receipt、error=INTENT_EXPIRED、effectNONE。终态closed和普通created/updated/firstexpiry chronology严格，但lateclosed/updated晚于原run/action/proofexpiry合法。generic实际费用非零或nullable均按原域验证，不能把所有provider当免费。

同一joined fact query以a/o/d/i明确aliases核origin/dispatch run/inv全owner、intent/tool key/version/SHA/backend/risk、generation/nonce/executor及run/UUID。四Boolean均须Boolean.TRUE，NULL不可绕过；LEFT JOIN保留缺失/损坏backlink以失败，不误判为无intent。APPLIED→SUCCEEDED及result/outcome、receipt/error以IS NOT DISTINCT FROM比较，effect一致；i.parameters_sha256另比descriptor.parametersSha256(action.arguments)，不是action.requestSHA。这里只证明投影agreement，未复制fullinv audit/cap/budget parser。

origin始终first run，额外复用ledger.executionMaterial核immutable owner/run/definition/snapshot/toolref/allowed version/审批policy；dispatch material同核，不绑定replayrun，不新snapshot JSONparser。joined fact tuple为single MVCC statement，另有这些原ledger immutable读取，不能误称整个历史流程只有单SQL。无历史live/status/lease/currentcap/grant/issuer/PDP或旧expiry-now门禁。

prepare先仅exact action intent_id FOR UPDATE，wait后fresh joined SELECT，避免wait前outerjoin旧tuple；current/history fact无锁。package Store historical(action,descriptor)要求verified/noambient/fullactiondescriptor匹配，自有sameDS primary READ_COMMITTED query-only事务、readOnly=false以满足原primary guard，10s wall+mono/5s query技术限，不min旧run/action/proofexpiry、不expire/update或触授权网络。旧public prepare/current仍核current PREPARE authority/live lease，只返回九metadata、不泄新proof/body。两InvocationStoreTest直接Row fixture改成各state完整合法15字段，没有弱9arg ctor或原断言削减。

这些是历史结构/来源不可变匹配，不是fresh签名/PDP、审批消费、dispatch许可或新public读体API。README两段说明此边界。V13本次实际SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据与分类 |
| --- | --- | --- |
| Codec有效RED | 05:03:44；21项3 failures/6直接Unsupported stub Errors/0 skips；exit1，15.809s | `/tmp/tianji-approval-proof-codec-red.log`；原12通过，新9缺失行为；不把直接stub Errors伪写为0 |
| Codec聚焦GREEN | 05:06:49；21通过、0 failures/Errors/skips；exit0，18.829s | `/tmp/tianji-approval-proof-codec-green.log` |
| 历史有效RED | 05:12:16；71项8 failures/5直接historical stub Errors/0 skips；exit1，15.465s | `/tmp/tianji-tool-intent-history-red.log`；Codec21/Inv27通过；Action23缺新历史/group/queryshape行为。old renewedLease matcher已按计划改a.alias而旧SQL尚未join，属预期queryshape缺失，非lease语义回归或fixture错误；firstexpiry/fullowner参数断言保留 |
| 历史聚焦GREEN | 05:19:19；Codec21+Action23+Inv27共71通过、0 failures/Errors/skips；exit0，20.692s | `/tmp/tianji-tool-intent-history-green.log` |
| 最终完整GREEN | 05:21:31；107项=106通过+1 PG skip，0 failures/Errors；exit0、BUILD SUCCESS，18.151s | `/tmp/tianji-tool-intent-history-final.log`；8类选择回归，原PG类缺optin跳过 |

实际最终命令，cwd=tianji-service（root执行，报告作者未运行）：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolExecutionJsonCodecTest,ToolActionIntentStoreTest,ToolInvocationStoreTest,ToolInvocationRecorderTest,ToolApprovalBindingTest,ToolActionRequestTest,ToolExecutionJournalPostgresTest,ToolExecutionStorageVerifierTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-tool-intent-history-final.log 2>&1
```

| 最终实际测试 | 通过 | 跳过 | 证明范围 |
| --- | ---: | ---: | --- |
| Codec21 | 21 | 0 | 原12+新9：proof closed/历史校验/SHA/来源漂移与五键outcome/raw BigDecimal |
| ActionStore23 | 23 | 0 | 原10+新13：fullrow八state、groups/joins/source/owner/time、query-only与锁后freshjoin |
| InvocationStore27、Recorder15 | 42 | 0 | 原准备/预算/readonly事实兼容及两合法fullRow fixtures |
| StorageVerifier13、ApprovalBinding3、ActionRequest4 | 20 | 0 | 原schema/history/source/action域合同兼容 |
| 原ToolExecutionJournalPostgresTest | 0 | 1 | 本步未新增PG测试；原opt-in fixture仅编译，缺前置不执行 |
| 合计 | 106 | 1 | 8类相关回归，非全仓库/平台运行验收 |

历史验证使用fake JDBC/ResultSet、受控Clock与真实codec/domain/事务协调。没有启动任何数据库或运行真实SQL/锁/并发/PG catalog；不能将编译/skip当成PG通过。没有真实IAM/PDP、签名/crypto、审批消费、HTTP网络或模型运行。本步没有child JVM probes。Codec与历史产线/测试独审均无剩余阻断，root已核对新增测试、两处fixture与本报告；最终状态见MC-BLOCKER。

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 closed proof/outcome | Satisfied（本步） | Codec21：exact4/4/7/4/18、16KiB/UTF8/UTCZ/trueonly、historic verifiedAt、canonicalSHA、五键types/rawnumeric | 结构编码非remote签名/当前许可；新encodingTag非旧SQL标签 |
| PLAN-REQ-002 完整历史Row | Satisfied（本步） | Action23：fullaction/originimmutable/proofrawgroups/8state/lateclosed、generic nonzero/nullcost | 不重绑replayrun、不查当前live/PDP |
| PLAN-REQ-003 dispatch projection一致 | Satisfied（本步） | fullowner/backlinks/四BooleanTRUE/独立paramsSHA/nullableJSONB fact/source材料正反 | 仅投影agreement，不声称fullinv cap/audit/budget parser |
| PLAN-REQ-004 内部query-only/兼容 | Satisfied（本步） | own10sRC primary flagfalse、noauthority/expirewrite、public9/live保持、Inv27两fixture | 无fresh权/approvalconsume/dispatch/newpublicbody |
| PLAN-REQ-005 范围/真实验证 | Satisfied（本步） | 有效RED→最终106pass/PG1skip、九路径/V13固定/旧合法编码保持 | 无SQL/其他模块/配置/API/权限/消费者扩大；PG未运行 |
| 完整T3-07/平台蓝图 | Partial | 当前私有codec与历史事实能力 | writeDispatcher/approvalConsume/nativeMCP、真实IAM/crypto/PG/模型及平台后续未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR persistence三产线文件/三测试与README/plan/evidence九路径 | 无其他模块/native/Invocation产线/新架构层改动 | None |
| MC-REUSE-001 | Applicable | PASS | 唯一codec/row parser、原ToolApprovalBinding/VerifiedApprovalDTO/ledger.executionMaterial | 无审批/快照/fullinv parser复制或新引擎 | None |
| MC-DEP-001 | Applicable | PASS | 现Jackson/JavaTime/JDK SHA/Spring JDBC/transaction | 无POM/依赖/utility框架新增 | None |
| MC-NAME-001 | Applicable | PASS | ApprovalSourceVO/唯一ApprovalProofVO/ConsumptionVO/DispatchVO/Row15与包内historical | 技术角色/immutable值明确，无第二Proposal DTO | None |
| MC-VALID-001 | Applicable | PASS | exact proof/outcome keys/scalars/fullaction/rawgroups/8state/BooleanTRUE joins | Codec21/Action23正反、nullable/digest/source/time/backlink损坏均拒绝 | None |
| MC-MODEL-001 | Applicable | PASS | immutable record/原domain值，full15 constructor/old9accessors/public9 | 无弱9arg ctor；仅两Inv fixtures合法化，无实体/Mapper副本 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed DTO/domain重建与metadata投影 | 无新实体转换或Converter层，不绕过原身份转换 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术codec/JDBC，无新业务日志Service | 固定sanitized无私密cause，proof/结果/参数不进入toString | None |
| MC-BEAN-001 | Not applicable | N/A | 无新Bean/DI/config/公开读体入口 | 原technical constructor和同源helpers保持 | None |
| MC-UTIL-001 | Applicable | PASS | 原JDK digest/Jackson/ledger/transaction | 无新utility/身份/线程/验证框架或scratch脚本 | None |
| MC-JSON-001 | Applicable | PASS | proof4/4/7/4/18闭合与16KiB/trueonly、outcomeexact5 | canonical重编码SHA非jsonbtext；raw numeric bytes保BigDecimal，旧合法编码保持 | None |
| MC-TIME-001 | Applicable | PASS | 历史verifiedAt/consume组与created/updated/expiry/closed；技术10s wall+mono | lateclosed可超旧expiry，history不min业务deadline；public授权/lease时间保持 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/properties/flag或权限注册改动 | 内部history未公开/自动消费，无运行启用 | None |
| MC-PATTERN-001 | Applicable | PASS | single codec/Row parser+原transaction participant及immutable源复用 | 只投影agreement，不复制fullinv audit/cap/budget parser或审批状态引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 九路径、仅两直接Inv Row fixtures、V13实测固定 | SQL/AS/PS/C/Grant/PDP/Budget/native/消费者/Invocation产线未改 | None |
| MC-TEST-001 | Applicable | PASS | 有效Codec/history RED→GREEN，最终106通过+PG1skip | stub Errors如实；a.alias预期queryshape缺失非fixture/lease回归；PG仅编译跳过 | None |
| MC-BLOCKER-001 | Applicable | PASS | root全文核对产线差异、新增测试、两处合法Row fixture、README与本报告；两组独审无剩余阻断；106通过/PG1skip及九路径/V13摘要已核 | 所有适用MC与Literal Rule闭合；按显式九路径清单提交，不将内部历史事实或未验收SQL当作执行许可 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 包内ApprovalSource/Proof/Consumption/Dispatch VO、Row/codec/Store | 实际编译与106项回归 | 语义角色清楚，值/结果脱敏 | None |
| Rule 2 | Applicable | PASS | closed schemas/action/groups/owner/4Boolean/独立paramsSHA/sourceimmutable | Codec21/Action23的8state/损坏/NULL/时序/锁顺序正反 | 历史结构非fresh许可，原public权边界保持 | None |
| Rule 3 | Applicable | PASS | true immutable records/原DTO/domain复用/Row15 | 合法fullRow两fixture/old9兼容测试 | 无弱9arg ctor/可变proof/重复实体或Mapper层 | None |
| Rule 4 | Not applicable | N/A | manual技术codec/JDBC，无业务Bean/DI/日志新增 | 九路径及安全错误审阅 | 不伪造Lombok业务注入验收 | None |
| Rule 5 | Applicable | PASS | JDK SHA/Jackson/Spring/原ledger | imports/POM/source | 无新utility/dependency/认证平台 | None |
| Rule 6 | Applicable | PASS | proof层级闭合16KiB/UTCZ/trueonly、shared outcome五键/types | JSON边界/SHA空白序/BigDecimal/rawtoken与旧编码回归 | 非PGjsonb::text hash，无新action专用outcome parser或外部wire协议 | None |
| Rule 7 | Not applicable | N/A | 无profiles/config/flags/publicbody接口 | 九路径审阅 | 不启用运行或审批消费 | None |
| Rule 9 | Applicable | PASS | 原single codec/Row/participant与freshjoined projection | 8state/alias/lock先行/同源事务测试 | 不新建状态机/审批/fullinv验证引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Clock/mono/历史verifiedAt与consume时序 | expired/late/非零nullable fee/技术窗口回退时钟测试 | 不以now拒历史、不延长旧业务期限/当前授权 | None |
| Rule 11 | Applicable | PASS | 原AR persistence、V13冻结、九路径 | 106相关通过+PGskip/source范围 | 无SQL/API/权限/其他模块/消费或派发扩张 | None |

## 6. 未验证与最终门禁

历史fullRow/proof只能供内部事实审查，不是当前授权、审批consume、dispatch许可或面向用户的body读取。writeDispatcher/approvalConsume/nativeMCP与公开业务消费者尚未交付；真实IAM/IdP/PDP/签名/crypto、网络、PostgreSQL/SQL和模型/平台运行未验收。

未自动启动应用、数据库、容器、HTTP listener、浏览器/GUI、native scheduler或长期runtime；未部署、push、PR/release、注册/激活权限或授角色。无新依赖/SQL/config/仓库scratch/scripts/work脚本。原PG类缺optin明确skip，本步无新PG测试或child probes。

报告作者仅编辑本证据，未改source/README/plan，未构建或提交，保留其他owner修改。root已完成测试、独审和本报告核对；精确九路径stage/commit在本报告定稿后执行，hash以Git记录为准，不预填已提交事实。

**PARTIAL — Spec requirements are unmet or unverified**：本步106项相关回归通过、1项PG跳过；真实运行与完整T3-07/平台仍未完成。
