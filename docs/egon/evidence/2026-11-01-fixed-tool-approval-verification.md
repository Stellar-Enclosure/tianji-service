# 固定工具审批来源SERVICE验证执行证据

| 项目 | 当前记录 |
| --- | --- |
| 执行计划 | [fixed tool approval verification](../plan/2026-11-01-11-13-fixed-tool-approval-verification.md)，Review |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§11.3/13/17、T3-07 |
| 主设计实施前SHA-256 | `9655341c65373a44a31ba93b7dc18b888839d91e207f7b61b4c359ca2e3d34aa` |
| Baseline | `901a57d80994c28d4c4aaaf2521d08a889a68417`，实施前clean |
| 原Source GO | root全文449行、strict/资源15md19refs与18md24refs/checklist/source与Spec门禁通过后锁SHA `4029bae434e5b7eb9532bfb79078ec025a07cfe4d92c4088fb33dbdebf22b763`发RED-only GO；当时checkpoint仅新PLAN未跟踪 |
| CLAR-002锁定 | root批准并读135–152/File5–6精确delta、strict/hash PASS，amended plan SHA `ad51c1b030d39d523a9489e72079a8fb77b185d1d9b0f07f918dcdb88f5eb67e`；生命周期反证先RED再3处最小source检查，无路径/API/window变化 |
| 最终结论 | **Verified（nonruntime，precommit snapshot）**；精确8类105PASS、0fail/error/skip；source/static/独审无openfinding |
| 提交 | PENDING；root最后文档/cached/staging/Git交付待执行，不预测hash；Review不伪Accepted |

## 1. 已验证范围与实现

精确9paths见plan§5/Step1：AR application/tool新FixedToolApprovalVerifier/Test；AS integration/process新TianjiFixedToolApprovalBindings/Test与TianjiFixedToolApprovalVerifier/Test；README、plan和本报告。3prod/3tests/3docs，root3/journal_design2/tool_audit2/doc2。root已完整source line review，独审对生命周期修复/17tests/README复审PASS；9scope、23SQL与readonly libraries检查通过，无operationalstub/TODO/System.out/宽泛Exception catch/GlobalTelemetry/新Bean。

port唯一withVerifiedApproval(Request,Callback<T>)；Request6固定run/lease/descriptor/action/receipt/deadline，重建fullAA START动作/owner/ref/policy/参数/intent/requestSHA，校verified非LEGACY owner、runleaseepoch/deadline。receipt为安全canonical1..255字符串，不假UUID；parametersSHA来自descriptor canonicalargs，独立fullactionSHA。ApprovedObservation7只typed source/binding/proof/consumer/expiry，脱敏；consumer必须非LEGACY SERVICE且等binding.consumer、sourceissuer重建actor相同，approvedtrue/workflow与时间结构一致。constructor/hash不是认证，不导出bearer/SDKclient/业务operation。

namespace用原TIANJI_TOOL_APPROVAL_SOURCE_V1+sourceId+tenant+issuer+resource，不加consumer/policy/tool/base/辅助alias，与privatecodec标签一致，port8测试含knownvector。canonical sourceId/任意别名一致性仍由host operator保证；源码不新增全局source introspection/签名。Observation expiry取consumer/proof min，consumerExpiry可早于proofexpiry。

Bindings各registry/list1..256、immutable/no duplicateVersion/slot/originDefHashdrift，Backend12与Binding9固定source/origin/definition/ref/approvalworkflow；fullDefSHA/ref/AA START schema/policy/consumer source-client核验。USER可不同subject同issuer/tenant，SERVICE requester self-only。审批backend独立业务START descriptor.policy.bindingSHA，未比较或新增fakeapprovalBackendSHA；issuer/resource HTTPS2048/512，base沿siblings HTTPS/loopbackHTTP、exactASCII URI equality，timeout100ms..30s。

AS verifier复用现sharedCredentialVerifier及actual HttpProcessClient strict18field/echo/size；独立TENANT SERVICE、同requestlocal bearer、actualconsumer exactbinding，scope ONLY VERIFY_SCOPE，单POST verifyApproval，无release GET/Sourceintrospection/START。actual新HTTP+当前PS SERVICEguard+requireApproved当前时刻建立本次来源；无verifiedAt>=callstart伪下界、detached签名/currentapproverRole/approverIDs/authVersion承诺。

原entry wall+mono minreq/run/entry+backendtimeout贯穿credential/SDK/callback，并cap proof/credential expiry；own无队列pool1..8/noninheritvirtual/Trace传播清理，callerHTTP必须NEVER且不close。close/cancel先原子清active/bearer，防非协作SDK晚回填/晚callback。callback可先COMMIT，随后postcheck/timeout只抑制return，不rollback/releaseapproval/授权STARTretry；helper仅LOCAL currentAuthKey+ACTIVEtrust，不轮询tokenrevocation/runCancel/lease/ownerPDP。

CLAR-002落实3处检查：worker helper前actualTX拒CREDENTIAL_UNAVAILABLE；current前actualTX拒CANCELLED（含postcallback），不rollback/unbind/cleanup foreignTX；Future.get后caller最终handoff再完整current key/trust/window而非remaining-only。6safe FailureKind无cause/privatebody，callbackLedger保code固定msg。SDK已判expired-invalid→APPROVAL_INVALID_RESPONSE，本地requireApproved才发现stale→APPROVAL_DENIED；false/localbinding mismatch/PS401/403/404为DENIED，其他remote unavailable，无retry推断。

无消费/业务START/Spring配置/flag/nativeAA/公开JSON/Store/Recorder/codec/SDK/PS/oldtransport/helper改动。原USER审批workflow仍需其正常PSaudience登录发sourceStart(action)，不是SERVICE冒充USER或OBO。root核全部23existing SQL与HEAD逐字节相同，V13 f0c642...26b3、V14 888768...3760a保持；Agent generator excluded，无CRUD/MP迁移/依赖或新引擎。

## 2. 实际验证与修订记录

| 日志/阶段 | 实际结果 | 时长/结束时间（2026-11-01 +08:00） | 原因/边界 |
| --- | --- | --- | --- |
| Source GO/编译前置 | 三组件各自RED-only GO | 初次Maven前 | plain Request6/Observation7、派生UOE/安全Failure6，Bindings12/9/2 ctor/resolve UOE、Verifier声明；不当功能证明 |
| `/tmp/tianji-fixed-tool-approval-values-red.log` | exit1；8=1PASS/4guard断言FAIL/3直接派生UOE ERROR/0skip | 17.067s；11:31:22 | compiler/正向fixture有效，root观察后才port GREEN GO |
| `/tmp/tianji-fixed-tool-approval-bindings-red.log` | exit1；21=8PASS/2FAIL/11直接UOE ERROR/0skip | 20.202s；11:33:32 | port8GREEN，Bindings13=2guard断言FAIL11 ctor/resolve UOE，非classinit/compiler/fixture错误，之后才journal GREEN GO |
| `/tmp/tianji-fixed-tool-approval-bindings-green.log` | exit0 BUILD SUCCESS；21PASS/0fail/error/skip | 17.155s；11:36:40 | Port8+Bindings13；原测试断言保留，Verifier测试先增强独立proof25/window30、OAuth4、HTTP非协作close/Trace/@Timeout |
| `/tmp/tianji-fixed-tool-approval-verifier-red.log` | exit1；14=0PASS/11断言FAIL/3直接方法UOE ERROR/0skip | 20.632s；11:38:27 | ctor guards/wrongtype/latch未进入/positive UOE均缺行为；上游GREEN/fixture合法，之后才Verifier功能GO |
| `/tmp/tianji-fixed-tool-approval-verifier-green.log` | exit1；35=34PASS/1FAIL/0ERROR/skip | 17.671s；11:40:50 | Verifier13PASS1路径fixture FAIL；既有SDK保留/base+ApprovalClient.PATH，测试只修literal，SDK/生产route未改，不伪称此轮GREEN |
| CLAR-002/独审 | root先读delta/strict并锁ad51，再批准实际反证后sourcefix | 两小修复前 | callerhandoff/currentworker TX两个缺口；SDK-invalid过期与本地stale分相位，不改SDK/parser/下界 |
| `/tmp/tianji-fixed-tool-approval-lifecycle-red.log` | exit1；17=15PASS/2预期断言FAIL/0ERROR/skip | 20.190s；11:48:03 | caller_handoff与callback_foreign_transaction实际复现，SDKexpired/pathfixture已PASS；root观察后才3处最小source检查 |
| `/tmp/tianji-fixed-tool-approval-final.log` | exit0 BUILD SUCCESS；105PASS/0fail/error/skip | 21.877s；11:48:56 | 精确8类完整source-reactor成功；PG未选中，0skip不是PG通过 |
| 最终source/static/独审 | PASS（precommit） | 最终snapshot | exact9paths/23SQL字节/readonlylibs/无stub及whitespace通过，tool_audit生命周期3checks/17tests/README复审PASS；最后cached/Git由root执行 |

分类为SDK12；AR32（port8、codec21、ToolApprovalBinding3）；AS56（bindings13、verifier17、旧START26）；PS ApprovalVerificationService5，共105PASS。测试使用actualSDK/fake JDK bytewire/controlled JWTmanager与有限owned资源，未运行真实IAM/crypto/HTTP listener/PS/SQL/生产应用。root实际最终命令，cwd tianji-service，docowner未Maven：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=FixedToolApprovalVerifierTest,TianjiFixedToolApprovalBindingsTest,TianjiFixedToolApprovalVerifierTest,ToolApprovalBindingTest,ToolExecutionJsonCodecTest,HttpProcessClientTest,ApprovalVerificationServiceTest,TianjiFixedBpmnStartToolTransportTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-fixed-tool-approval-final.log 2>&1
```

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | port8 fullaction/AA START/receipt/consumer/workflow/namespace/expiry/安全value tests | constructor/保存record非认证或未来许可 |
| PLAN-REQ-002 | Satisfied | Bindings13有限registry/fullslot/hashdrift/URI/selfSERVICE/USER区别与businessSHA独立 | canonical sourceId/全局alias一致性operator负责 |
| PLAN-REQ-003 | Satisfied | verifier17 actualsharedhelper+SDK18/VERIFYonly/同token/consumer exact/单POST与failure分类，旧SDK/PS回归 | 只当前PS SERVICEguard，不授requesterPDP/消费/START |
| PLAN-REQ-004 | Satisfied | oneclockmono/proof和OAuth独立expiry/closeTrace/busy/latecallback/facts保留、caller与workerTX反证先RED后修 | 不连续撤销/foreignTX清理或rollback已提交facts |
| PLAN-REQ-005 | Satisfied | 各有效RED/路径fixture历史/CLAR锁/最终105PASS、9scope/23SQL/readonlylibs与独审 | 最后cached/Git待root，fullGoal仍Partial |
| 完整T3-07/Goal | Partial | 本步explicitlibrary来源验证已完成源码测试 | atomicconsume/nativeAA/MCP、剩余蓝图与真实环境后续 |

## 4. Blocking Manual Check

以下为已验证precommit snapshot，root最终文档/cached/commit尚待执行，不把受控测试当真实环境验收。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR application port/AS技术adapter与原SDK，9scope | 现结构/JDBC/Flyway保持，Agent excluded，无新认证/State/transport引擎 | None |
| MC-REUSE-001 | Applicable | PASS | 原Binding/Query/DTO/digest/sharedCredentialVerifier/HttpProcessClient | 复用现协议与身份验证，无Sourceintrospection/OBO/二parser | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Trace/SpringTX/SDK/IAM imports，POM无改 | 无新依赖/脚本/运行设施 | None |
| MC-NAME-001 | Applicable | PASS | Port/Bindings/Verifier与immutable Request/Observation/Backend/BindingVO | 技术职责明确，value toString脱敏 | None |
| MC-VALID-001 | Applicable | PASS | fullaction/source/workflow/consumer/URI/currenthandoff/workerTX/SDKecho tests | 构造/hash不冒认证，VERIFYonly/currentPS来源与businessSHA分开 | None |
| MC-MODEL-001 | Applicable | PASS | Request6/Observation7/Backend12/Binding9，旧DTO/SQL保持 | 只有typed provenance，无rawbody/bearer/client/operation或entity/state新增 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typedQuery/DTO/Binding直接复用 | 无PO/entity/Mapper或publicJSON/privatecodec改动 | 沿现模型边界 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志，safe kind/固定异常msg/脱敏values | 不泄URI/token/privatebody/cause | 无日志注入变动 |
| MC-BEAN-001 | Not applicable | N/A | 显式manual library constructors，无新SpringBean/config | 无业务Service/Lombok伪注入，callerHTTP外部owned | 现技术库模式 |
| MC-UTIL-001 | Applicable | PASS | JDK URI/Duration/Atomic与原digest/helper/Trace，ownedbounded模式 | 无新utility/认证框架，不改helper或替foreignTX cleanup | None |
| MC-JSON-001 | Applicable | PASS | existingSDK18字段/原codec标签与queryecho，旧Codec21/SDK12回归 | 无新JSONparser/ordinal/字段/wire，namespace非签名 | None |
| MC-TIME-001 | Applicable | PASS | 原wall+mono窗口/DTO<=30s、独立proof25与OAuth4边界/最终handoff | 不reset/verifiedAt伪下界，expired SDKinvalid与localDenied分相位，callbackfacts保留 | 真PS/PG时钟运行未验收 |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/flag/权限/Bean注册 | 固定hostmetadata库，不自动注册或源别名introspection | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | 现callback Adapter/finiteprofile/bounded executor与实际sharedhelper | 解决固定source/生命周期变化点，不新State/工作流/认证引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | root核9paths=3prod3tests3docs、23SQL/readonlylibs不变 | 无Store/Recorder/SDK/PS/codec/helper/oldtransport/消费START配置更改 | 最后cached范围root复核 |
| MC-TEST-001 | Applicable | PASS | 三组件有效RED、首35fixture失败、lifecycle2反证RED与105GREEN、独审 | 无编译/fixture伪RED，mockJWT非真crypto；0skip非PGproof | 真实环境待验收 |
| MC-BLOCKER-001 | Applicable | PASS | root source/static/9scope/23SQL核对及tool_audit最后复审PASS，无openfinding | 原两must-fix先复现后3checks修复，Goal仍Partial | 最后文档/cached/staging/commit待root |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 窄Verifier/Bindings/typedVO技术角色明确 | source/编译/构造guards | 命名沿现库模式，无普通entity漂移 | None |
| Rule 2 | Applicable | PASS | AA/fullaction/source/consumer/workflow/URI/window/SDKecho与TXguard | values8/Bindings13/verifier17正反 | 不借metadata授当前权限，域/technical边界闭合 | None |
| Rule 3 | Applicable | PASS | immutable record profiles/Observation与原typedDTO | source/脱敏/旧回归 | 无entity/Mapper/宽松DTO替代 | None |
| Rule 4 | Not applicable | N/A | 无新业务Bean/DI/log，显式manual技术constructor | 9scope/imports | 不伪造Lombok DI或无关日志 | 沿现技术模式 |
| Rule 5 | Applicable | PASS | JDK/原digest/SDK/helper/Trace复用 | POM/import/static | 无新utility/依赖/框架 | None |
| Rule 6 | Applicable | PASS | existingSDK strict18/原codec与namespace标签保持 | wire/SDK12/Codec21/旧START26 | 无ordinal/publicJSON/新parser | None |
| Rule 7 | Not applicable | N/A | 无配置/YAML/profile/flags/自动注册 | 精确9scope | 不激活权限或实际tools | None |
| Rule 9 | Applicable | PASS | callback Adapter/finiteprofiles/ownedbounded生命周期 | pool/Trace/cancel/latefacts/currenthandoff矩阵 | 不新认证/State/transport引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/mono/原DTO30s | 独立expiry/SDK-local阶段/无reset/latecallback | 无java.util时间/伪新下界，foreignTX不擅自清理 | None |
| Rule 11 | Applicable | PASS | 现Agent技术库/结构/generatorexcluded，23SQL与原CQE保持 | 9scope/bytecompare/readonlylibraries检查 | 无CRUD/MP迁移/新层/业务软删除或事件机制变化 | None |

## 6. 结论与未验证边界

本Step达到**Verified（nonruntime，PRECOMMIT）**：5aliases Satisfied，17MC/10Rules逐项PASS或有据N/A。原GO4029与root批准/读delta后的CLAR-002 ad51分别保留，当前plan ad51冻结；3处最小检查只在lifecycle2预期FAIL后实现，完整105PASS及最后独审/static无openfinding。最后root文档/cached/staging/commit待执行，不预测Git SHA。

验证只交付当前来源provenance，不消费/释放审批，不执行/重试业务START，不宣称detached签名/currentapproverRole或持续撤销。callback先commit后失败仅抑制返回，原facts不撤回；futureconsumer仍须独立RequesterPDP/实际lease/原子消费。sourceId canonical/alias一致性为host责任，原USER审批流程需正常PSaudience身份，SERVICE不是USER OBO。

仅测试生命周期中的actual SDK/fake bytewire/controlled JWTmanager与有限JVM/owned线程资源；未启动生产app/调度器/DB/容器/listener/collector/browser/GUI，无真实IAM/PDP/OAuth/JWT crypto/网络/LLM/PG验收，无DDLapply/repair/drop/backfill/权限激活/角色grant/push/PR/release。无repo scratch/scripts/work/harness新增，测试保留subsystem；/tmp仅日志/文档审阅备份。docowner仅定稿evidence，不改plan/source/tests/README、未构建或提交。完整蓝图/Goal仍**Partial**，atomicconsume/nativeAA/MCP及其余工作后续。
