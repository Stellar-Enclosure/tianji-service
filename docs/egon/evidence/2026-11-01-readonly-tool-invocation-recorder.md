# T3-07 只读工具调用持久记录执行证据

| 项目 | 记录 |
| --- | --- |
| 执行日期 | 2026-11-01，Asia/Shanghai |
| 执行计划 | [只读工具调用Recorder](../plan/2026-11-01-00-41-readonly-tool-invocation-recorder.md)，00:41创建，strict结构校验通过 |
| 主设计 | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md)，§7/12/13、T3-07 |
| 主设计实施前SHA-256 | `9bcfc5c22299a746ab537d9693b0ede2133aa6ebe44b1c737435058187f3cfce` |
| Baseline | `06219937f9c1f10b65a1a8e689e9ab56e71ec8a0` |
| 提交 | 主代理完成独立审阅、最终9路径diff/stage门禁后产生；hash以Git与交付消息为准 |
| 当前结果 | 选定非运行回归140项通过、1项PG跳过；主代理与独立实际代码审阅均无生产阻断，完整T3-07/平台仍Partial |

## 1. 实际实现与边界

本步9路径为：新增`ToolInvocationRecorder`及其测试；修改`ToolInvocationStore`、`ToolInvocationSql`、原Store测试和原`ToolExecutionJournalPostgresTest`；更新README、新计划和本报告。没有修改codec、BudgetService、transport、SQL迁移、配置、权限、依赖或run events。

Recorder为手工构造的技术协调库，使用原DataSource/ledger/preparations/capabilities/authorization并核exact指针，独立verified flag复用原shared storage verifier。唯一公开执行入口`executeReadOnly`只接受READ_ONLY、无action/intentExpiry、已知estimatedCost=0的固定非计费DMN请求，拒绝ambient事务。`RecordResult`不可变并脱敏，`operationInvoked`只表示本次调用实际进入`OneShotDecision.evaluate()`，不证明HTTP已发送或有业务效果。

Store共用一次`prepareInternal`，以LEGACY_PREPARATION与RECORDER_METADATA投影保持原public prepare仅PREPARED/AWAITING_APPROVAL的语义。Recorder metadata与budget来自同一PREPARE/live/E2事务，无额外PDP/query；投影没有outcome/audit/nonce/executor/args。`ToolInvocationSql.StoredInvocationRow`为唯一七状态解析路径，校验原identity、历史cap/dispatch/audit、outcome和budget；高级历史不错误绑定当前cap epoch/grant。当前metadata准备仍核live source/E1/E2，历史completion不核当前权限。

可信transport callback内重新为原requester执行DISPATCH check，RPC在事务外。`callDeadline=min(原dispatchNotAfter,run.deadline,VerifiedExecutor.expiresAt,E1.expiresAt)`；authorization.Request.deadlineAt为完整callDeadline，非null ALLOW audit必须精确匹配request/boundary/subject/grant/checkedAt并覆盖整个调用期限。本地beginWindow另取`min(阶段entry+10s,callDeadline)`，保留同一wall/monotonic起点，PDP之后不重置。

begin使用同源自有10秒READ_COMMITTED事务和原有查询超时，按run FOR UPDATE→当前E2→scoped invocation/budget锁核实际lease owner/epoch/live expiry、cancel/deadline/source/policy及完整E1/E2。只有PREPARED首次CAS才能保存DISPATCHING、dispatch epoch1、实际callback SERVICE executor及DISPATCH audit。Recorder局部SecureRandom生成32字节nonce，canonical 64小写hex明文不进入DTO/SQL/log/model，只持久SHA-256。Store确认COMMIT后先返回attempt ACK；Recorder再核原窗口，窗口已过则凭所持nonce写FAILED_KNOWN/DISPATCH_NOT_SENT，`operationInvoked=false`，不会丢失开始确认后留下无凭据状态。

重复DISPATCHING/UNKNOWN只返metadata，无第二次oneShot或主动结算；UNKNOWN已经SETTLED零的预算如实呈现SETTLED，否则PENDING。已知终态的私密outcome仍须当前requester DISPATCH权限、live fence、原期限未过才可读，拒绝或过期不泄旧output。`prepareOrLoadForRecorder`与begin的已提交metadata ACK可跨越提交后的窗口耗尽，ACK本身不授执行权限；旧public prepare保留原postcommit检查。

completion是另一短事务，只凭原nonce preimage重算hash、dispatch epoch、实际executor、不可变tool/backend/parameters、历史cap issue epoch及原DISPATCH audit grant核验。数据库已有hash本身不是凭据。允许原run终态/取消、新lease fence、cap reissue或线程中断后提交已返回事实；不改变run状态或发独立run event。DISPATCHING可完成，UNKNOWN可用同一原nonce收敛已知；已知同outcome幂等，改变则冲突。私有bookkeeping阶段暂清并恢复中断，以便有界地记事实。

MATCHED记录SUCCEEDED/schema output；NO_MATCH记录REJECTED/DECISION_NO_MATCH；确定下游拒绝分别映射DOWNSTREAM_PERMISSION_DENIED或DOWNSTREAM_REJECTED；port保证pre-HTTP的CANCELLED/DEADLINE记录FAILED_KNOWN/DISPATCH_NOT_SENT；无效响应或不确定异常记录UNKNOWN及固定安全code。非成功output为空、readonly businessReceipt=null。固定DMN Recorder的actualCost始终是已知零；通用Store保留UNKNOWN历史事实中actualCost=null的保守语义，不把未定价provider当作免费。

outcome先独立COMMIT，再best-effort调用原budget settle(0,0,0)。结算失败只返回AccountingState.PENDING，不回滚或降级已知事实。callback前失败只允许固定八种cleanup code、strict fullrequest/originallease匹配且仍PREPARED/epoch0/无nonce/executor的行关闭；可覆盖prepare commit后ACK丢失，advanced行不覆盖、不返回私密outcome。callback已经记下事实后outer transport postcheck失败仍向调用者抛安全Failure并抑制输出，保留原事实。

现V13没有PS executionId/rule/submittedAt/receivedAt的合法字段。本步没有借businessReceipt/audit/error/output存它们；只提供durable typed outcome及可重建的deterministic downstreamRequestId/run correlation，不声称持久PS规则级溯源。V13实测摘要仍为：

```text
f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3
```

## 2. 实际RED/GREEN

| 阶段 | 实际结果（+08:00） | 证据与分类 |
| --- | --- | --- |
| 初次运行 | 01:14:45；42项，5 failures/20 Errors/0 skips | `/tmp/tianji-readonly-recorder-red.log`；20 Errors中有2项positive fixture未先assertNotNull导致NPE，不能把这两项当有效RED |
| 有效RED复跑 | 01:16:35；42项，7 failures/18直接stub Errors/0 skips，exit1 | `/tmp/tianji-readonly-recorder-valid-red.log`；修fixture后真实缺失行为，原17项通过；断言失败与直接未实现错误分别记录，不伪写为0 Errors |
| 聚焦GREEN | Store27+Recorder15共42项全部通过 | `/tmp/tianji-readonly-recorder-green.log`；最终结果以下列完整selector为准 |
| 最终完整GREEN | 01:30:01；140通过、0 failures/Errors、1 PG skip；exit0、BUILD SUCCESS，20.229s | `/tmp/tianji-readonly-recorder-final.log`；AR103通过+1跳过，AS37通过 |

最终选择命令：

```bash
./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false \
  -Dtest=ToolInvocationRecorderTest,ToolInvocationStoreTest,ToolExecutionJournalPostgresTest,ToolCapabilityStoreTest,ToolExecutionJsonCodecTest,ToolExecutionStorageVerifierTest,AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,ReadOnlyDecisionToolTransportTest,TianjiFixedDecisionToolTransportTest,TianjiToolGrantAuthorizerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| 选定测试 | 通过 | 跳过 | 证明范围 |
| --- | ---: | ---: | --- |
| ToolInvocationStoreTest | 27 | 0 | 原17兼容及metadata/shared parser、begin/ACK/window、nonce/history、私密读取/cleanup |
| ToolInvocationRecorderTest | 15 | 0 | first-only/commit顺序、fresh权限、结果映射、no-resend、late/中断、cleanup、结算/outer失败 |
| ToolCapabilityStoreTest、JsonCodecTest、StorageVerifierTest | 39 | 0 | 原cap14/codec12/verifier13回归 |
| AgentBudgetLockOrderingTest、AgentContextBudgetServiceTest | 14 | 0 | 原run-first预算及context事务兼容 |
| ReadOnlyDecisionToolTransportTest | 8 | 0 | 原port请求/Observation/安全Failure合同 |
| ToolExecutionJournalPostgresTest | 0 | 1 | 新helper已编译；无显式原URL/vector前置，整类跳过 |
| AS ToolGrantAuthorizerTest、FixedDecisionToolTransportTest | 37 | 0 | 原PDP19和固定transport18回归 |
| 合计 | 140 | 1 | 当前11类选择回归，非全仓库或运行验收 |

受控JDBC/transaction替身测试观察真实协调代码及SQL参数/顺序、提交边界和失败映射；不能据此声称真实PostgreSQL锁/回滚已经验证。原opt-in PG fixture已增加20个同invocation一次operation、completion trigger rollback、独立budget trigger失败及run终态/cap epoch变化后late completion，当前仅编译而未执行。OAuth/JWT/PDP替身及实际SDK/mock JDK bytewire回归不证明真实密码学、网络或远端当前权限。

## 3. 局部要求追踪

| 要求别名 | 状态 | 实际证据 | 保留边界 |
| --- | --- | --- | --- |
| PLAN-REQ-001 当前权限与单次执行 | Satisfied（本步） | fresh原requester DISPATCH覆盖callDeadline；E1/E2/live fence；CAS/hash-only nonce/actual SERVICE executor；begin commit后一次oneShot | 未注册native consumer，不证明真实IAM/PG |
| PLAN-REQ-002 历史事实与UNKNOWN | Satisfied（本步） | 原nonce完整tuple、late/cap reissue/terminal/interrupt、UNKNOWN收敛、已知幂等/冲突测试 | 不赋予UNKNOWN重试许可，无PS规则级持久provenance |
| PLAN-REQ-003 预算一致性 | Satisfied（本步） | outcome commit先、另事务settle；failure=PENDING；UNKNOWN/DISPATCHING不重结算 | 零成本仅限固定非计费DMN，通用历史unknown可保留null成本 |
| PLAN-REQ-004 兼容/投影/cleanup | Satisfied（本步） | 原17通过；one prepare flow/common parser；metadata-only、fresh私密读取、ACK丢失cleanup及advanced不覆盖 | 不开放公开cleanup/nonce/dispatch API |
| PLAN-REQ-005 范围与真实验证 | Satisfied（本步） | 9声明路径、140通过+PG1skip、V13固定、未改SQL/codec/BudgetService/transport/config/events | 最终Git/独立审阅门禁由主代理完成，不宣称运行验收 |
| 完整T3-07/平台蓝图 | Partial | 当前只读Recorder局部能力 | native接线、写工具dispatch/审批消费、run事件协调、真实IAM/crypto/PG与后续阶段未完成 |

## 4. Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 仅AR persistence Recorder/Store/Sql及对应测试文档 | 复用原AR port/同源事务，未加架构层或AS/native依赖 | None |
| MC-REUSE-001 | Applicable | PASS | 原preparations/capabilities/authorization/shared verifier/ledger budget/transport | ONE准备流与ONE stored-row parser，无重复验证/执行引擎 | None |
| MC-DEP-001 | Applicable | PASS | JDK SecureRandom/MessageDigest、既有Spring/codec | 无POM/build/依赖修改 | None |
| MC-NAME-001 | Applicable | PASS | Recorder、RecordResult、AccountingState、包内metadata/attempt/window及Sql参与者 | 技术角色和值/阶段命名明确 | None |
| MC-VALID-001 | Applicable | PASS | readonly/known0、exact source/lease/E1E2/audit/window、nonce+epoch+executor/history、严格cleanup | 开始/私密读取核当前权，历史completion只核原proof；正反测试通过 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变record、metadata去私密内容、RecordResult/attempt脱敏 | plaintext nonce只局部；operationInvoked与HTTP事实区分 | None |
| MC-CONVERT-001 | Not applicable | N/A | 直接使用原typed port/descriptor/ToolExecutionOutcome，既有codec读写 | 无实体/DTO转换需求或新Converter层 | None |
| MC-LOG-001 | Not applicable | N/A | 技术JDBC协调库无新业务日志 | 安全固定异常、结果toString脱敏，不记录nonce/token/output | None |
| MC-BEAN-001 | Not applicable | N/A | manual constructors，未创建业务Bean或配置注册 | exact pointer同源核验；未新增DI/Qualifier面 | None |
| MC-UTIL-001 | Applicable | PASS | 原schema/hash/codec/事务/授权接口及JDK密码摘要 | 无新utility框架、provider抽象或通用manager | None |
| MC-JSON-001 | Applicable | PASS | 原ToolExecutionJsonCodec持久audit/outcome，shared parser校验交叉字段 | codec未改，12项原回归；不借字段保存PS provenance | None |
| MC-TIME-001 | Applicable | PASS | Instant/DBclock/MICROS、同源wall+mono、完整callDeadline与短beginWindow分离 | ACK先返回、oneShot前再核、late fact有界事务及interrupt恢复测试 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无配置/profile/启用键修改 | Recorder无自动Bean/native激活 | None |
| MC-PATTERN-001 | Applicable | PASS | 原port依赖倒置；Recorder按现有协调方式组合准备/可信callback/结果/结算 | 隔离真实变化点，不引入第二状态机框架 | None |
| MC-SCOPE-001 | Applicable | PASS | 9路径声明、当前V13实测f0c642…26b3 | SQL/codec/BudgetService/transport/config/权限/events均不扩大 | None |
| MC-TEST-001 | Applicable | PASS | 有效RED7fail/18stub Errors；最终140pass/PG1skip；原17兼容 | fixture NPE如实排除；PG/helper仅编译，不伪报真实并发 | None |
| MC-BLOCKER-001 | Applicable | PASS | 主代理已读全量差异/最终日志，独立实际代码审阅无生产阻断；声明9路径已逐项核对 | 当前非运行slice门禁闭合，真实PG/运行接线和完整任务仍待办 | None |

## 5. Literal Rule

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Recorder/Store/Sql、VO/Result/AccountingState、对应Test | 编译及140项通过 | 角色/不可变值/技术参与者命名保持原风格 | None |
| Rule 2 | Applicable | PASS | readonly/known0、current proof/live fence、original nonce tuple、cleanup allowlist | Store27/Recorder15及原授权回归 | 公私边界精确验证，不把metadata或DBhash当许可 | None |
| Rule 3 | Applicable | PASS | record投影、private attempt无plaintext、原typed outcome/port复用 | metadata/private read/nonce/redaction断言 | 无实体膨胀或手工重复Converter | None |
| Rule 4 | Not applicable | N/A | manual技术JDBC库，无新增业务Bean/Service/config | 9路径及constructors审阅 | 不虚构业务Service/Lombok注入验收 | None |
| Rule 5 | Applicable | PASS | 既有Spring/codec/授权/预算/transport及JDK SecureRandom/SHA-256 | source-reactor编译，无POM修改 | 无新工具库/框架/依赖 | None |
| Rule 6 | Applicable | PASS | 原codec存typed audit/outcome，ONE common parser验证七state | Codec12与Store/Recorder正反例 | 不改JSON协议、不借output/audit存PS执行provenance | None |
| Rule 7 | Not applicable | N/A | 无配置/profile/property/flag修改 | 9路径及未自动注册边界 | 单原配置语义保持，未激活运行能力 | None |
| Rule 9 | Applicable | PASS | 依赖倒置port与现有协调方式，组合commit/oneShot/completion/accounting | commit顺序/失败/outer抑制/幂等测试 | 真实变化点直接组合，无通用执行层 | None |
| Rule 10 | Applicable | PASS | UTC Instant/Clock/DBtime/mono；原deadline不延长；audit覆盖整call | window/late/ACK/cap reissue/interrupt断言 | 当前授权期限与历史事实完成职责分开 | None |
| Rule 11 | Applicable | PASS | 原AR persistence边界；V13/codec/BudgetService/transport/config/events不改 | 140相关回归，V13实测固定 | 无SQL/权限/native/依赖架构扩张 | None |

## 6. 未验证与最终门禁

真实PostgreSQL SQL/锁/trigger/concurrency与连接行为未执行；原opt-in类本次跳过。真实HTTP/IdP/PDP/JWKS/crypto、远端当前权限/撤销、业务服务及模型执行未验收。没有native/runtime consumer接线、写工具dispatch/approval消费、独立run events或PS规则级durable provenance。本步显式技术入口可记录固定只读结果，不等于平台已上线。

未自动启动应用、数据库、容器、浏览器/GUI、native scheduler或其他长期runtime；未注册/激活权限、授角色、部署、push、PR或release。没有新增仓库scripts/work/scratch harness。临时日志可清理，持久测试和实现为长期证据。

本报告作者只创建本证据文件，未修改代码/README/plan/SQL，未运行构建或提交。主代理已完成全量差异与独立审阅结论核对；显式stage的精确9路径及Git hash以最终Git结果为准。

**PARTIAL — Spec requirements are unmet or unverified**：当前只读Recorder选定非运行回归通过；真实数据库/身份协议、运行接线及完整T3-07/平台验收仍未完成。
