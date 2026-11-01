# 固定START最终期限执行证据

Plan: [2026-11-01-12-50-bounded-start-one-shot-deadline.md](../plan/2026-11-01-12-50-bounded-start-one-shot-deadline.md)，Review，原用户exact授权；baseline `3548ed3687d295cafff385c91af7b7aa9961bc95`，Spec SHA `4d92207e63d1926925ed229478b7fa80a69432b9916e9db826731169f6a59d45`。

当前 **Verified（nonruntime，PRECOMMIT）**。Root全文1–180/181–end、strict、资源与checklist门禁通过后SOURCE GO；锁SHA `449be35e86f0d6fee99131af430dbb59c6db449e48328d5659ee8f2344dad0d9`。无本步提交。精确7paths见Plan；AARecorder/native/config/MCP与后续蓝图仍Partial。

## 实际记录

Root独审与journal plan语义快审PASS；exact7paths与baseline已核。旧executing-plan路径因用户并行更名ENOENT，定位现`.agents/skills/stellar-enclosure-coding-execute-plan`后资源15md19PASS，Root读新SKILL全文与checklist；六normative refs与旧COLA HEAD逐字节相同，writing资源18md24PASS。未修改或提交用户COLA rename工作。

Port first RED `/tmp/tianji-bounded-start-port-red.log` exit1，17.291s：8=7PASS/1预期assert FAIL/0ERROR/skip，bounded default期待safe Failure却为compile-only UOE；旧6与新SAM兼容测试均通过。Root观察后才将default改为Failure(OPERATION_NOT_SENT)，不delegate旧start。当时AS仍compile-only bounded UOE；各owner冻结后继续如下RED。

AS有效RED `/tmp/tianji-bounded-start-adapter-red.log` exit1，23.862s：AR8全GREEN；AS32=28PASS/2预期assert FAIL/2由bounded UOE被原外层错误分类转换的行为ERROR/0skip。旧26全通过；新positive POST/ACK因UOE返回NOT_SENT，共享位内缺行为和pending POST未进入均实证。无compiler/fixture/依赖错误；Root读新增6test diff和具体错误后才授权AS功能实现。

最终 `/tmp/tianji-bounded-start-one-shot-final.log` exit0 BUILD SUCCESS，20.968s，2026-11-01T12:56:59+08:00：五类98PASS/0fail/error/skip，AR66（port8、RunContext12、GovernedProcessBridge23、IdempotentStartRecorder23）+AS32。旧AR6与AS26测试正文逐字节与HEAD相同，分别只新增2/6测试；Root brace-aware比较实际PASS。所有Java先冻结，无额外源码变更后重复扩大测试。

Root全文读2prod与2test差异，独立journal审阅PASS，无openfinding。AS匿名共享execute、真实START在pre-send catch外、原publishLock内clip+original wall/mono remaining均确认；README单段准确说明default拒绝/AS同位/POST窗口/late facts/后续AA边界。23既有SQL逐字节与HEAD相同，exact7paths，tracked与untracked空白检查PASS。旧Recorder/native/SDK/helper/codec/配置/权限保持。没有真实IAM/网络/模型/PG或app启动。

## 实现与最终命令

AR新增default `OneShotStart.start(Instant)`只safe OPERATION_NOT_SENT，不delegate；原no-arg SAM、Callback/Request/DTO/九FailureKinds保持。Legacy host仍可用原调用；futureAA不得fallback。AS两入口共享owner/active/TX/once位；bounded先验证µ/future/noextension并收紧现scope，再current/client/command/beginOperation。POST timeout使用新remaining，GET保持旧窗口；原clock/nano起点不重置。invalid bounded use已spend AS位且不POST，retry/escape取消。ACK已交callback后过期只抑制return，事实/原nonce与取消清bearer规则保持；HTTP timeout不撤销远端提交。

Root实际最终命令，cwd tianji-service：
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=FixedBpmnStartToolTransportTest,TianjiFixedBpmnStartToolTransportTest,ToolIdempotentStartRecorderTest,AdkGovernedProcessToolBridgeTest,AdkRunContextTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-bounded-start-one-shot-final.log 2>&1
```

## Requirement closure

| Alias | Status | Evidence | Boundary |
| --- | --- | --- | --- |
| PLAN-REQ-001 | Satisfied | port8：default不delegate、legacy方法引用/SAM、原6body unchanged | default unsupported不是实际发送实现，AA不得fallback |
| PLAN-REQ-002 | Satisfied | AS实际SDK bytewire POST timeout2s/GET5s，µ/future/caps/mono反例 | 固定clock/mockHTTP非部署时间验收 |
| PLAN-REQ-003 | Satisfied | sharedbit/invalid spends/escape/ACKpostexpiry/pendinginterrupt-close | 不撤业务提交、不重派，caller资源仍外部owner |
| PLAN-REQ-004 | Satisfied | old6/26 bodies原样、IDW/native/Context回归，23SQL/旧callers保持 | AARecorder/native/config尚未接新重载 |
| PLAN-REQ-005 | Satisfied | 两有效RED→98GREEN/7scope/17MC10Rules/独审 | 最后cached/staging/Git待Root；全Goal Partial |

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR port/AS技术adapter，7scope | 保现结构，无新层/引擎/CRUD，Agent excluded | None |
| MC-REUSE-001 | Applicable | PASS | 现publishLock/window/credential/consumed/SDK/helper | 共用原scope/once，无新timer/auth/parser | None |
| MC-DEP-001 | Applicable | PASS | POM/SDK/build无改，现JDK/Spring | 无新依赖/运行设施 | None |
| MC-NAME-001 | Applicable | PASS | 现OneShotStart/CallScope与typed Instant | 技术职责明确，无实体/DTO新增 | None |
| MC-VALID-001 | Applicable | PASS | µ/future/noextension/owner/active/TX/sharedonce+原Request guards | default不delegate，ASactualclient在clip之后 | None |
| MC-MODEL-001 | Applicable | PASS | 无新record/entity/JSON；原Request/Observation保持 | additive方法参数无宽松对象/新层 | None |
| MC-CONVERT-001 | Not applicable | N/A | 原typed SDK/Request/Observation直接复用 | 无PO/entity/Mapper转换 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志，固定safe Failure/no cause | 不泄token/URI/SDK私密错误 | 无日志面变化 |
| MC-BEAN-001 | Not applicable | N/A | 原manual技术对象，无Bean/DI改动 | Autowired/Lombok注入修订无新增适用对象 | 无Bean面变化 |
| MC-UTIL-001 | Applicable | PASS | JDK Instant/Duration/Atomic与原scope/helpers | 无通用utility/transport引擎新增 | None |
| MC-JSON-001 | Applicable | PASS | 原SDK/JSON/receipt/errorKinds无改 | 无ordinal/新wire字段或parser | None |
| MC-TIME-001 | Applicable | PASS | originalwallmono+volatilebound/publishLock/真实POST timeout与回拨反例 | 不floor不reset不extend；ACK事实保留 | 无真实网络时钟验收 |
| MC-CONFIG-001 | Not applicable | N/A | 无profile/YAML/flags/权限注册改动 | 显式库API，未自动激活AA | 无配置面变化 |
| MC-PATTERN-001 | Applicable | PASS | callback Adapter/requestlocal scope原模式 | 解决回调后确定deadline变化点，未引新层 | None |
| MC-SCOPE-001 | Applicable | PASS | 7paths/23SQL immutable/旧Recorder-native-SDK-helper保持 | 仅必要additive API与ASclip、tests/docs | 最后cached范围Root核 |
| MC-TEST-001 | Applicable | PASS | port有效RED/AS有效RED/98GREEN、old6/26 byte compare/独审 | 无fixture/compiler假RED；controlledSDK非真实IAM/网络 | runtime待用户 |
| MC-BLOCKER-001 | Applicable | PASS | 5aliases/17MC10Rules/source独审无openfinding | exactdeadline实际POST边界与兼容闭合 | 最后cached/stage/commit待Root |

## Literal Rules

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 现port/scope技术角色，Instant typed参数 | source/compile | 无普通entity命名漂移 | None |
| Rule 2 | Applicable | PASS | typed µ/future/noextension/dynamic callback guards | port8/AS32正反+旧native | 内部technical handoff闭合，旧Request校验保持 | None |
| Rule 3 | Applicable | PASS | 原records/DTO不变，无新entity/model | source/oldtests | 无Mapper/Lombok对象转换需求 | None |
| Rule 4 | Not applicable | N/A | 无新Bean/DI/log | 精确7scope | 不引无关注解/日志或构造注入变更 | 沿现技术模式 |
| Rule 5 | Applicable | PASS | JDK java.time/Atomic/现SDK/helper复用 | imports/POM | 无新通用工具/依赖 | None |
| Rule 6 | Applicable | PASS | 原JSON/receipt/nineFailureKinds不改 | wire/旧合同回归 | 无ordinal/parser/外部字段变化 | None |
| Rule 7 | Not applicable | N/A | 无YAML/profile/flags | 7scope | 环境配置无改动 | 无配置面变化 |
| Rule 9 | Applicable | PASS | 现Adapter+requestscope+sameonce | bounded实际timeout/partialfailure矩阵 | 变化点显式复用，无新执行引擎 | None |
| Rule 10 | Applicable | PASS | Instant/Duration/Clock/原nano起点 | 回拨/expiry/actualPOSTtimeout | 无java.util时间/预算reset/隐式round | None |
| Rule 11 | Applicable | PASS | 当前AR/AS技术架构/23SQL/原CQE保持，Agent excluded | source/static/compile/7scope | 无CRUD/MP迁移/新层或业务软删除/事件机制改变 | None |

## 结论与未验证边界

本Step **Verified（nonruntime，PRECOMMIT）**，5aliases Satisfied、17MC/10Rules逐项PASS或有据N/A，Rule11PASS。Plan SHA449be…dad0d9保持；Root全文/strict/resources/checklist与独审完成。最后cached/staging/commit待Root，不预测hash，Review不伪Accepted。

不自动启动app/DB/容器/browser/GUI/HTTP listener，不apply/repair既有SQL、不注册激活权限/授角色、不push/PR/release。23SQL/旧Recorder/native/SDK/helper/配置保持，实际IAM/crypto/网络/模型/PG待用户环境；timeout不撤销真实副作用，AARecorder另步，不宣称全Goal完成。用户并行COLA执行skill rename已发现并按新路径读取，未修改/暂存/提交该用户工作。
