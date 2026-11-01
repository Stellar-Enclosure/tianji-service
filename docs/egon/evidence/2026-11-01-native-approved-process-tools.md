# 原生审批型Process工具执行证据

Plan: [native approved tools](../plan/2026-11-01-14-18-native-approved-process-tools.md)，Review、原用户exact授权；baseline `e0523987cd0744ef93363965e90c4cd3b7b7db11` clean；Spec SHA `5128704d8bf4819fd575919d7162fb83f03bd32b9c4e8cb3f731ce7fb1b63373`。

当前 **Verified（nonruntime，PRECOMMIT）**。Root全文426行（1–149/150–289/290–end）、strict、execute资源15md19/writing18md24/checklist通过后SourceGO，原Plan SHA `68f0c1f28edb8f8ffa15fb16a1f27e76eaaaf9e00f77426c12943585957fa4f2`。兼容反例后Root先写/读CLAR-001+private resolve伪码、strict PASS，amended SHA `254096e858bda0a1442c2c683f616e85b7d175044290f7a73d86e39fb5d5032c`。10scope/5aliases/17MC10Rules闭合；最后cached/staging/Git待Root，不预测hash，不伪Accepted。

## 实际验证记录

| 日志/阶段 | 结果 | 时长/时间 | 解释 |
| --- | --- | --- | --- |
| SourceGO/编译前置 | 三test owners only；new10ctor/usesUOE/plainVO；旧Common/Access仍拒AA | Maven前 | 不先实现；allJava冻结后Root统一Maven |
| `/tmp/tianji-native-approved-process-red.log` | exit1；82=58oldPASS/2预期assertFAIL/22missingbehavior ERROR/0skip | 23.405s | Access26=18PASS/1positiveCtorFAIL/7正文riskctorERR；Bridge33=23PASS/1plainVO脱敏guardFAIL/9new10ctorUOE；Loop23=17PASS/5newctorUOE/1directWAIT event旧拒；无compiler/fixture/Telemetry/mock泄漏 |
| `/tmp/tianji-native-approved-process-green.log` | exit0 BUILD SUCCESS；82PASS/0fail/error/skip | 26.541s | Root与journal在各unit有效RED后才实现；三个unit全绿 |
| `/tmp/tianji-native-approved-process-final.log` | exit0 BUILD SUCCESS；167PASS/0fail/error/skip | 22.965s；14:33:33 | 八类首完整绿色，随后复审发现legacy observer状态兼容缺口，未据此提交 |
| CLAR-001与test-only | 原IDW/resultAccess应拒WAIT，新通用映射误接受 | fix前 | Root/独审同意；新反例先写，生产冻结，计划delta先读/strict |
| `/tmp/tianji-native-approved-observer-red.log` | exit1；34=33PASS/1预期assertFAIL/0ERROR/skip | 15.374s | only legacy_idempotent_observers…期待Ledger却未抛；然后才准最小allowWait guard |
| `/tmp/tianji-native-approved-process-final-fixed.log` | exit0 BUILD SUCCESS；168PASS/0fail/error/skip | 27.828s；2026-11-01T14:39:24+08:00 | 最终八类：Loop23/Context12/Bridge34/ActionApproval13/AccessStore26/AccessRecorder16/AAStore26/AARecorder18 |

无fixture/compiler失败被冒RED；所有owner先冻结Java，Root实际JDK25/source-reactor/settings/literal ADKfalse/三OTel none：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=ToolResultAccessStoreTest,ToolResultAccessRecorderTest,AdkGovernedProcessToolBridgeTest,AdkGovernedProcessLoopTest,AdkRunContextTest,ToolApprovedStartRecorderTest,ToolApprovedInvocationStoreTest,ToolActionApprovalSqlTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-native-approved-process-final-fixed.log 2>&1
```

PG未选中，零skip不是PG通过；无app/DB/browser/容器/listener、模型网络或IAM调用。

## 已审实现

精确10paths：NativeBridge/CommonBridge/AccessStore/Sql四main、BridgeTest/LoopTest/AccessStoreTest三tests、README/plan/evidence。old8 IDW-only保持，new10末尾显式nonnull verifier+host metadata resolver；pure pointer helpers、不可变4field request闭合available definition/policy/refmembership与重建action并脱敏。Selector仅同步HOST metadata引用，无框架认证/有界I/O权限；actualVerifier才验证。STARTED/UNKNOWN后selector一次，open/native fences前后不双计entry；version128/TTL7d/DBµexpiry/原registry与完整binding规则保持。

双entry OneShot匿名wrapper都先fence/open/helderror再原样forward，bounded绝不fallback。Observed ownoutcome沿原gate、ownmetadata不猜费用；ownnull WAIT只safe status，FINISHEDepoch0/PENDING/errornull，不access或泄receipt/proof/args。AAcommitobserver WAIT才NONE；IDW与resultAccess observer（含AAknown3）遇WAIT在effects写前failclosed。Globaladvanced非known3不伪WAIT/none/驱动。close freezes effect，lateobserver/events不改terminal，knowntruth不因outererror/accountpending降级。

Access ctor只扩IDW/AA仍fixed START/exactinstance/fullowner-binding-action；outer Store与事务SQL重复当前AA approvalPolicyRef一致。AA用已有lockForApproval，IDW保lockForWrite，Action/codec不改。historicalproof可过期但freshRECONCILE/capE1E2/livelease/window每次重核；V14 source revision/digest/source outcome/fees/proof与原budget不动，只本access独立0budget结算。strict corrupt/crossproof拒绝由Action parser负责，access只传播失败不造第二parser。

Root全文读Native main5200token diff、Native new10/Loop new6/Access new8+fixtures及Common/Access tiny guards；独审Native/Common/tests与Access production均PASS。Root brace-aware比较旧23 Bridge、旧17 Loop、旧18 Access共58@Test正文逐字节与HEAD相同；当前34/23/26。23existing SQL逐字节未改，exact10scope/tracked+untracked whitespace PASS。CLAR-001最后private三个调用点已读：true仅AARecorder，false旧IDW和resultAccess；其余映射/时间/默认行为不变。

## Requirements

| Alias | Status | Evidence | Boundary |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | explicit ctor/VO/selector一次/fence/receipt规范、旧8与fullaction tests | 引用不是批准，selector不授IO或auth |
| PLAN-REQ-002 | Satisfied | actualRunner safeWAIT/PENDING0/noerror、oncequota、observer-only NONE/freeze | finiteFakeModel非真实模型/数据库验收 |
| PLAN-REQ-003 | Satisfied | exactbounded3fences/no legacyfallback、Observedrace/known3freshAccess/outermask | no secondLoop/no redrive，未知只安全失败 |
| PLAN-REQ-004 | Satisfied | expiredproof currentRECONCILE/双policyguard/known3/sourcefees+proof unchanged/own0settle | 构造或历史approval不授当前披露权 |
| PLAN-REQ-005 | Satisfied | 58oldbodies/23SQL/三有效RED+CLAR反例/168GREEN/独审 | 最后Git待Root；AS装配与其余Goal仍Partial |

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR adk/persistence架构、10scope | 无新层/引擎/CRUD，Agent generator excluded | None |
| MC-REUSE-001 | Applicable | PASS | 原Scope/Plugin/Recorder/Action locks/V14/observerfreeze | 不重造auth/transport/parser/计额/loop | None |
| MC-DEP-001 | Applicable | PASS | POM/SDK/构建不变，现JDK/ADK/Spring | 无新依赖/运行设施 | None |
| MC-NAME-001 | Applicable | PASS | nested ApprovalReceiptResolver/immutable RequestVO/现技术roles | hostmetadata职责清楚无实体漂移 | None |
| MC-VALID-001 | Applicable | PASS | definition/policy/membership/fullaction/receipt/fences/RECONCILE/cap/window | 当前权限双policy，WAIT observer模式closed | None |
| MC-MODEL-001 | Applicable | PASS | immutable4field request/原公共结果与closedWAIT metadata | 无新entity/宽泛identity DTO，状态不伪settlement | None |
| MC-CONVERT-001 | Not applicable | N/A | typed内部对象/primitive模型map，原codec不改 | 无PO/entity/Mapper转换或通用DTO层 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无业务日志新增，redactedVO/fixedsafeerror | 不泄receipt/proof/source/input/cause | 无日志面变化 |
| MC-BEAN-001 | Not applicable | N/A | manual additive ctor，未加Bean/DI | 最新Autowired/Lombok规则无新增Spring对象适用 | 无Bean面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK/原scope/helpers/ADK协议 | 无新executor/timer/通用工具 | None |
| MC-JSON-001 | Applicable | PASS | safeWAIT map/原codec-SDK-V14保留 | 无ordinal/新wire或私密body泄露 | None |
| MC-TIME-001 | Applicable | PASS | 原version/TTL/DBµ和bounded deadline同值forward/closefreeze | selector非IOtimer，expiredproof仅历史，旧window不reset | 实际环境待用户 |
| MC-CONFIG-001 | Not applicable | N/A | AS/YAML/profile/roles不改 | programmatic AA explicit，配置另步 | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | 原Adapter/Scope/Facade+UoW/access机制 | 解真实hostmetadata/部分失败变化点，保持singleLoop | None |
| MC-SCOPE-001 | Applicable | PASS | 10paths/23SQL/旧Executor-Plugin/AS-SDK-ports-codec不改 | no migration/层/权限扩展，CLAR同scope | cached最后Root核 |
| MC-TEST-001 | Applicable | PASS | 三unitRED/82GREEN/legacy单反例RED→168GREEN/58bodycompare/独审 | 无fixture/compiler/PG假验收，原真实Runtime边界保留 | runtime待用户 |
| MC-BLOCKER-001 | Applicable | PASS | 5REQ/17MC10Rules与兼容finding已先RED后关闭 | source/test无openfinding，Rule11PASS | 最后cached/stage/commit待Root |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | nestedtechnical resolver/RequestVO命名 | source/compile | 无普通实体新增 | None |
| Rule 2 | Applicable | PASS | typedfullaction/receipt/identity/currentpolicy/fences/closedwaitguards | new8/11/6tests与旧回归 | boundary不借metadata授新权 | None |
| Rule 3 | Applicable | PASS | immutable4field record，旧DTO不改 | fullaction/脱敏/旧body对比 | 无entity/Mapper/Lombok对象转换新增 | None |
| Rule 4 | Not applicable | N/A | 无新Bean/DI/log，manual ctor | exact10scope | 不加无关注解/日志/构造注入 | 沿现技术模式 |
| Rule 5 | Applicable | PASS | JDK/原ADK-Spring-helper | imports/POM/scope | 无新通用工具/依赖/执行器 | None |
| Rule 6 | Applicable | PASS | 原codec/SDK/V14/enum保持，safe模型status | disclosure/source digest tests | 无ordinal/新parser/receipt body格式 | None |
| Rule 7 | Not applicable | N/A | 无profile/YAML/flags | exactscope | 多环境配置不受影响 | 无配置面变化 |
| Rule 9 | Applicable | PASS | 原callback Adapter/Scope/Facade | wait/once/outermask/freeze矩阵 | 无第二loop/新engine/层 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/原bounded/DBµexpiry/freeze | historicalproof/currentwindow/deadlineforward | 无java.util时间/reset或假IO预算 | None |
| Rule 11 | Applicable | PASS | 现AR技术架构/23SQL/原CQE保持，Agent excluded | source/compile/10scope/bytefreeze | 无CRUD/MP迁移/软删/事件新层 | None |

## 结论与边界

本Step Verified（nonruntime，PRECOMMIT），5aliases Satisfied、17MC/10Rules逐项PASS或有据N/A。原GO68f0与CLAR001 amended2540分别记录；Root全文/strict/resources/checklist/独审完成。最后cached/staging/commit待Root，不预测hash、不伪Accepted。

未启动app/DB/容器/browser/GUI/listener或实际模型/IAM/网络，未apply/repair旧SQL/激活权限/授角色/push/PR/release。0skip并非PG通过，PG未选本Step。当前仅programmatic AA native；AS profile闭合/稳定qualified resolver+actual-chain readiness、MCP与其余蓝图后续，完整Goal Partial。用户COLA skill rename保持未改未提交。
