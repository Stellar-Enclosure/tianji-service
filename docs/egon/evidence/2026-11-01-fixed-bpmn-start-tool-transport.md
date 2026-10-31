# T3-07 固定BPMN START工具传输执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai；日志跨度03:46—04:19 |
| 执行计划 | [固定BPMN START显式传输库](../plan/2026-11-01-03-39-fixed-bpmn-start-tool-transport.md)，Review；root全文审阅/strict/resources后source GO |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§6/7/12/13、T3-07 |
| 主设计实施前SHA-256 | `7788b380510b949244e256c6876c7ce81a350fe458bf99d43679b33726210cf4` |
| Baseline | `1de8dda72b47e2691e5c1893a198ded1d4c72774` |
| 当前结果 | 最终11类125通过（C6+AR29+AS79+PS11），0 failures/Errors/skips；完整T3-07/平台Partial |
| 提交 | 主代理确认文档/tests及精确十五路径stage后产生，hash以Git/交付为准 |

## 1. 实际实现与边界

十五路径：新AR FixedBpmnStartToolTransport/Test；C ProcessClient/原OrchestrationContractTest；PS TianjiProcessPermissions；新AS FixedBpmnStartToolBindings/Test、FixedBpmnStartToolTransport/Test、包内TianjiProcessToolCredentialVerifier；原FixedDecisionToolTransport/Test；README、新plan和本报告。START_SCOPE与START_RESOURCE_KEY分别提取原tianji:process:start和tianji.api.process.instance.start，PS仅改引用，权限值/HTTP wire不变。

新AR Request严格核nonLEGACY、same run/lease/deadline、risk非READ_ONLY，重建ToolActionRequest验证owner/ref/policy/归一参数/actionKey/intent/requestSHA一致。只复用readonly port的中性VerifiedExecutor，原RO public API不改。StartObservation只含闭合tianji:ps-start:v1:加64小写hex的hostReceiptReference及原GetProcessInstanceQuery校验的instanceId，output不可变且仅instanceId。元数据构造不认证或批准，未来WriteRecorder仍须先COMMIT当前授权dispatch nonce并完成必要审批消费。

Bindings最多256、不可变、防重复slot/跨同origin definitionSHA漂移，精确匹配tenant/issuer/type/definition/full tool ref/backend。Backend16稳定字段加canonical schema使用TIANJI_TOOL_BPMN_START_BACKEND_V1，排除run/definition/tool refs及credential动态版本，backend→descriptor→definition→mapping无循环。URI ASCII HTTPS/normalize/no query-userinfo-fragment及上界/100ms..30s延续原模式。只允许IDEMPOTENT_WRITE或APPROVAL_REQUIRED及START resource，input exact类型/字段且全部required，output exact/required STRING instanceId。

实际固定GET release核manifest/exact一项BPMN definition/hash/inputschema，再进入callback。POST沿原HttpProcessClient发送StartProcessCommand：definition/release来自backend，businessKey来自action，commandId与correlationId均action.intentId，variables为action.arguments。原SDK核ACK definition/release/business/correlation，DTO核instance/status/times。businessVersion仅host intent String metadata，PS无独立objectVersion；非schema变量不能声称PS检查该版本。

出站使用独立真实TENANT SERVICE协议，仅release-read+process-start两scope，PS commandreceipt owner是实际SERVICE executor，非USER OBO。共享helper抽取原DMN的key/ACTIVE TENANT trust/同OAuth bearer proof/JWT/原converter/full principal source/scopes与current checks，TargetVO仅9source字段+immutable scopes，CredentialVO仅request/key/trust/actor/expiry；无token/window/future/percallctx。helper无宽泛catch或跨portFailure，predicate失败为固定无cause错误；两transport优先保ownFailure，再安全映射其他Runtime。helper返回先赋scope.credential再原current，OAuth与JWT各自timestamp不强制相等，expiry取min。

DMN纯抽取保留六原依赖字段/identity helpers、scope集/hash/公开port、结果/错误/时间/关闭语义，19项原测试baseline→after保持通过，新增interrupt/late断言未改变方法计数。不制造纯抽取RED。两个transport各自拥有request-local bearer/active/publishLock/future/window/cancel与无queue、非继承virtual pool，1..8并发，仅传TraceContext。取消/close先撤active并清token，再cancel；publish同锁阻止迟到OAuth refill。worker/caller finally均clear，close只ownpool，不关闭caller HttpClient或shared IAM。START也提供六pure== observer，同/null/foreign无调用。

只有trusted matching2xx ACK路径调用包内static hostReceiptReference，生成tianji:ps-start:v1:加OrchestrationRequestDigests摘要。摘要域TIANJI_PS_START_RECEIPT_REFERENCE_V1，绑定PS issuer/resource ASCII、实际executor tenant/SERVICE/issuer fingerprint/subject、START/commandId、完整start(command)请求SHA及stable ACK instanceId/definition/release/business/correlation。raw variables不直接拼接，但通过请求SHA受绑定；排除baseURI/timeout/token/credentialVersion、status/timestamps/run/hostInv。独立digest向量验证namespace/request/stableACK变化与投影变化稳定性。这是实际PS commandreceipt namespace+request+ACK的host引用，非instanceId/上游receipt UUID或签名，helper自身无认证效力。

九Failure为固定安全kind，无cause/body/token。OneShot限定ownerthread/dynamic scope/once/noTX；SDK前明确scope/current/client/command失败为NOT_SENT；一旦调用SDK仅401/403 known DENIED，其他4xx/409/408/429/5xx/status0/I/O/timeout/runtime为UNKNOWN，invalid/mismatch/oversize2xx保留INVALID_RESPONSE但同属UNKNOWN。UNKNOWN Failure不携ref，不向caller返回receipt，无自动POST redo、commandID lookup或假reconcile。valid late ACK先生成Observation交callback，outer随后才postcheck抑制返回值；本步没有WriteRecorder或持久完成consumer。

独立审阅发现POST已进入时outer cancel/deadline/close和ACK后callback ledger错误仍可能带已知失败种类。新增4场景实际RED后，scope.beginOperation在publishLock内先remaining再标operationStarted；cancel/clear与其同锁，outer退出先cancel再读stage，锁不跨HTTP。callback成功return后才callbackCompleted。started且未completed时outer lifecycle/未知worker错误与callback AgentLedgerException转UNKNOWN；实际SDK DENIED/INVALID_RESPONSE/UNKNOWN仍保留。started前ledger code可安全保留；callbackCompleted后的outer source/time检查可按原kind抑制模型值，因为callback已完成事实处理。该phase线性化没有共享全局状态/重发，也未修改DMN错误语义。

库未Spring注册/配置/native挂载，未实现WriteRecorder、approval verify/consume或新的PS endpoint。原USER审批应自行用真实PSaudience登录start ToolApprovalBinding.sourceStart(action)，独立SERVICE核验/消费仍后续。固定内部PS START仅声明known nonbillable，不泛化未知provider费用或实现计费/退款。旧V13实测SHA仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN

| 阶段 | 实际结果（2026-11-01 +08:00） | 证据与分类 |
| --- | --- | --- |
| C/AR合同RED | 03:46:53；C6通过，AR新Port6项4 failures/0 Errors/0 skips；exit1，18.302s | `/tmp/tianji-bpmn-start-contracts-red.log`；reactor在AR失败，AS Bindings尚未执行，不能把两组件合计作RED |
| Bindings独立RED | 03:47:50；11项10 failures/1直接Unsupported resolve stub Error/0 skips；exit1，14.433s | `/tmp/tianji-bpmn-start-bindings-red.log`；真实缺失行为，无fixture错误，不写成0 Errors |
| STARTTransport RED | 03:50:27；跨模块64项，新START22项14 failures/6直接CREDENTIAL stub Errors；C6/Port6/Bindings11/原DMN19通过；exit1，24.069s | `/tmp/tianji-bpmn-start-transport-red.log`；AS summary52加C6/AR6为总64；DMN此时尚未抽取，是原行为baseline |
| 首次聚焦GREEN | 03:57:00；跨模块64通过，0fail/error/skip；exit0，20.397s | `/tmp/tianji-bpmn-start-transport-green.log`；命令误写-Dotel.logs/exporter=none，功能结果有效，但不声称三个canonical exporter flag齐全或作telemetry证明 |
| 首轮完整功能GREEN | 03:58:23；121通过、0fail/error/skip；exit0，23.472s | `/tmp/tianji-bpmn-start-transport-final.log`；尚未新增4个阶段反证，不能冒充最终接受；本轮exporter flag已纠正 |
| POST阶段补充有效RED | 04:12:53；START26项4 failures/0 Errors/0 skips，旧22通过；exit1，17.010s | `/tmp/tianji-bpmn-start-post-operation-red.log`；实际中断/截止/close during POST与validACK后ledger失败均复现，非fixture错误 |
| 阶段修复聚焦GREEN | 04:17:12；START26+DMN19共45通过、0fail/error/skip；exit0，17.538s | `/tmp/tianji-bpmn-start-post-operation-green.log` |
| 最终权威GREEN | 04:19:09；125通过（C6+AR29+AS79+PS11）、0fail/error/skip；exit0、BUILD SUCCESS，25.172s | `/tmp/tianji-bpmn-start-transport-verified.log`；包含4阶段回归与cancel/begin同锁线性化 |

实际最终命令（root执行），cwd=tianji-service：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=FixedBpmnStartToolTransportTest,TianjiFixedBpmnStartToolBindingsTest,TianjiFixedBpmnStartToolTransportTest,TianjiFixedDecisionToolTransportTest,TianjiFixedDecisionToolBindingsTest,OrchestrationContractTest,ReadOnlyDecisionToolTransportTest,ToolInvocationRecorderTest,TianjiReadOnlyDecisionToolConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-bpmn-start-transport-verified.log 2>&1
```

| 最终测试组 | 通过数 | 证明范围 |
| --- | ---: | --- |
| C OrchestrationContract | 6 | 两START常量同值与原wire/hash合同 |
| AR新Port6/旧ReadonlyPort8/Recorder15 | 29 | 新write值合同、原readonly/public接口与持久消费回归 |
| AS新Bindings11/新START26/旧DMN19/旧DMNBindings10/原Step12Config13 | 79 | fixed source/schema、真实SDK mock wire、receipt向量、phase/once/秘密、纯抽取/实际root兼容 |
| PS PermissionIntegration8/Controller3 | 11 | 同值权限引用与原HTTP权限行为，in-process WebContext/MockMvc |
| 合计 | 125 | 11类选择回归，非全仓库或平台运行验收 |

本步没有实际child JVM probes，不能沿用前步7 probes数字；也没有PG selector、真实OAuth/JWT crypto/IAM、HTTP网络/listener或模型运行。测试为实际HttpProcessClient/mock JDK bytewire、controlled身份/时间及PS in-process WebContext/MockMvc。0skip不表示PG验证。root已全文核生产和新phase差异，两独立审阅修复后无其他阻断；tests/文档/stage由root最终核对。临时日志可清理，长期证据是提交中的测试/实现。

## 3. 局部要求追踪

| 要求 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 fixed write/action/backend | Satisfied（本步） | Port6/Bindings11：exact action/source/ref/policy/schema、stable backend无循环、output仅instanceId | metadata不授当前权/approval，未接consumer |
| PLAN-REQ-002 独立SERVICE与秘密复用 | Satisfied（本步） | helper/START26/DMN19：samebearer/fullsource/two scopes/current/cleanup、old semantics基线保持 | 非USER OBO、无token DTO或共享percallctx；真实crypto未验收 |
| PLAN-REQ-003 trustedACK host receipt | Satisfied（本步） | 实际SDK ACK匹配+独立canonical digest变化矩阵，UNKNOWN Failure无ref/no redo | 非上游UUID/signature/instanceID；无lookup/reconcile或持久WriteRecorder |
| PLAN-REQ-004 UNKNOWN与late事实 | Satisfied（本步） | 四新phase反证RED→GREEN、begin/cancel同锁、started/completed阶段、validlate先callback | 真实迟到事实存储仍需后续WriteRecorder，未知不自动重派 |
| PLAN-REQ-005 兼容/范围/真实验证 | Satisfied（本步） | C/PS同值、DMN19纯抽取baseline→after、125相关回归、十五路径/V13固定 | 无config/native/审批消费/SQL/APIactivation/权限/deps扩大 |
| 完整T3-07/平台蓝图 | Partial | 当前显式START library能力 | WriteRecorder/approvalconsume/native/config/lookup及真实IAM/PG/模型和平台后续未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | C轻合同/PS同值引用、AR独立port、AS技术Adapter/helper，十五路径 | 原模块边界不变，无新层/Components/消费者配置 | None |
| MC-REUSE-001 | Applicable | PASS | 原HttpProcessClient/StartCommand/ACK/hash/ToolActionRequest/RO中性executor及原Converter | 敏感协议抽取包内helper，未复制认证/HTTP/执行引擎 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/Jackson/Trace/SDK；imports与POM范围 | 无新依赖/框架/工具或build步骤 | None |
| MC-NAME-001 | Applicable | PASS | Port/Bindings/BackendVO/StartObservation/TargetVO/CredentialVO/Verifier/Test | hostReceiptReference准确区别上游UUID/instanceId/signature，值脱敏 | None |
| MC-VALID-001 | Applicable | PASS | exact action/run/lease/ref/policy/args，BPMN/source/scopes/ACK/schema与phase | Port6/Bindings11/START26正反/独立digest/once/取消；构造不认证 | None |
| MC-MODEL-001 | Applicable | PASS | immutable records/Scopes Set.copyOf/固定schema/URIASCII equality | 无token DTO/模型target输入；helper无percall ctx/实体副本 | None |
| MC-CONVERT-001 | Applicable | PASS | helper复用现VerifiedPrincipalConverter及原typed SDK DTO | 不手工替代身份Converter/重复Mapper层 | None |
| MC-LOG-001 | Not applicable | N/A | manual技术port/Adapter/helper，无新业务日志Service | Failure固定kind无cause/私密正文，所有敏感VO.toString脱敏 | None |
| MC-BEAN-001 | Not applicable | N/A | explicit library无新Bean/config/factory | close仅own pool，不接管caller HttpClient/sharedIAM | None |
| MC-UTIL-001 | Applicable | PASS | 原OrchestrationRequestDigests/SDK/JDK/Trace/包内helper | 无新utility/认证平台/线程框架，scope仍各transport独有 | None |
| MC-JSON-001 | Applicable | PASS | 原Jackson typed Start/ACK、HTTP SDK匹配与closed primitive schema | wire/字段不改，invalid/mismatch/oversize2xx保守UNKNOWN | None |
| MC-TIME-001 | Applicable | PASS | Clock/Instant/Duration+original wall/mono、expiry min、有效lateACK先callback | beginOperation/cancel同锁线性化；已started未completed所有outer退出保守未知；DMN时间保持 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无properties/YAML/flag或Spring/native registration变化 | 库未自动激活，无权限注册/激活或新APIendpoint | None |
| MC-PATTERN-001 | Applicable | PASS | 原SDK Adapter/callback capability+包内credential复用 | ownerthread/dynamicscope/once/noTX，无第二loop/WriteRecorder/重试层 | None |
| MC-SCOPE-001 | Applicable | PASS | 当前十五声明路径、V13实测固定、C/PS同值 | 无SQL/配置/native/WriteRecorder/approvalconsume/APIactivation/权限/deps扩大 | None |
| MC-TEST-001 | Applicable | PASS | 三组件真实RED+新4phase有效RED；最终125通过；DMN19 baseline→after | stub Errors如实记录，早期误otel flag不作telemetry证明；本步无child/PG/crypto/model | None |
| MC-BLOCKER-001 | Applicable | PASS | root全文核对生产代码、全部新增测试、原DMN差异、README与本报告；两组独审及phase复审无剩余阻断；125项通过、十五路径/V13摘要已核 | 所有适用MC与Literal Rule闭合；按显式十五路径清单提交，不把后续消费者或真实运行认作已完成 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 新port/Bindings/Verifier/VO/Observation、九Failure与六uses* | 编译与125项回归 | 技术角色/不可变值命名清楚，receipt非上游UUID | None |
| Rule 2 | Applicable | PASS | action/fullsource/schema/exactURI/scopes/ACK与SDK前后phase | Port6/Bindings11/START26含新4反证 | 只trusted ACK出ref，meta不认证；已started未知不误记未发送 | None |
| Rule 3 | Applicable | PASS | immutable Request/Observation/Backend/Target/Credential、Scopes copy | 构造/URI/hash/immutability/redaction/独立向量 | 真value records，无token字段/重复实体或Converter层 | None |
| Rule 4 | Not applicable | N/A | manual技术库，无新业务Bean/Service/日志面 | 实际十五路径与owned lifecycle审阅 | 不伪造Lombok业务DI/Bean注入验收 | None |
| Rule 5 | Applicable | PASS | JDK/现SDK/hash/Trace/包内helper | imports/POM/实际编译 | 无新utility/线程/认证框架 | None |
| Rule 6 | Applicable | PASS | 原Spring Jackson typed Start/ACK及schema；同值C/PS | C6/PS11与实际SDK mock bytewire | 无wire/codec/PS DTO字段改变，不把新enum ordinal作业务码 | None |
| Rule 7 | Not applicable | N/A | 无profiles/config/flags新增修改 | 十五路径审阅，未注册START Bean/native consumer | explicit library不激活运行能力 | None |
| Rule 9 | Applicable | PASS | SDK Adapter+callback能力、敏感helper与phase-aware退出 | once/noTX/cancel/late/4新phase测试及DMN19 | 复用变化点，无第二loop/WriteRecorder/自动POST redo | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/mono与原expiry/ACK先callback | deadline/interrupt/close/late/原DMN回归 | 窗口不reset，远端status/time不入receipt，不强排远本地时间 | None |
| Rule 11 | Applicable | PASS | 原C/AR/AS/PS边界；十五路径/V13冻结 | 125选择回归与scope审阅 | 无新层/SQL/native/config/endpoint/权限/deps或业务消费者扩大 | None |

## 6. 未验证与最终门禁

当前只显式library transport，不代表写执行已在平台开放。WriteRecorder/审批消费/native/config/command lookup仍未实现。真实PS receipt数据库持久链、IAM/IdP/PDP/JWKS crypto、网络、PostgreSQL、LLM与完整平台运行未验收；内部nonbillable声明不泛化未知费用。

未自动启动应用、数据库、HTTP listener、容器、浏览器/GUI、native scheduler或长期runtime；未注册/激活权限、授角色、部署、push、PR或release。无新增SQL/依赖/仓库scratch/scripts/work脚本。本步没有实际JVM child probes。

报告作者只编辑本证据，未改source/README/plan，未构建或提交，保留其他owner修改。root已完成测试、README与本报告核对；精确十五路径stage/commit在本报告定稿后执行，hash以Git记录为准，不预填已提交。

**PARTIAL — Spec requirements are unmet or unverified**：本步11类125项相关回归通过；实际运行与完整T3-07/平台仍未完成。
