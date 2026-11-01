# 已提交工具副作用观察与截止租约代次隔离

| Field | Value |
| --- | --- |
| Document | `2026-11-01-08-51-committed-effect-deadline-fence.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 08:51 Asia/Shanghai` |
| Updated | `2026-11-01 08:51 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | AR status-only committed observer + exact lease-generation deadline CAS；12paths |
| Source Requirement | 用户按原蓝图继续未完成工作并分步验证提交；§7截止与sideEffectOutcome指向tool ledger |
| Baseline Revision | `main@b96f646fbad088b35e9514573ef42b7b784121a9`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact用户执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `853c0ec63235dbebabbb1d9fe62427f99c8f2d5898f5a18bc3887f87374be2aa` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [原START结果访问](2026-11-01-07-50-known-start-result-access.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
本Step提供后续native工具聚合所需的显式status-only事实观察与deadline currentgeneration CAS。事实先COMMIT，observer后、account与body前；observer失败保留事实，actualport包装不能掩盖其安全依赖失败。新deadline overload仅当前leaseowner/epoch/revision在DB截止后可冻结CONFIRMED/NONE/UNKNOWN，旧宽方法保持保守UNKNOWN。十二路径、一个逻辑Step/commit，不接Executor/plugin/bridge/native或新增schema/config。

## 2. Target Spec and Effective Design
蓝图§7.2要求每次claim epoch隔离旧worker，DB绝对deadline不可延长，TIMED_OUT不表示撤回已发副作用；sideEffectOutcome指向tool账本。§12/13要求治理、真实错误及partial failure一致。本步复用既有Recorder/Facade/UnitofWork/CAS，只开放后续host所需窄port及新overload，不声称已接native工具union/effectcounter/resolver。Review沿用户原exact执行授权，不虚构Accepted；root全文/strict/resources后才sourceGO，完整T3-07/Goal仍Partial。

## 3. Effective Requirements and Acceptance
PLAN-REQ仅为原要求局部trace aliases，不新增业务需求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7.2/12/13 | 已提交tool事实可供host观察，通知在account与模型输出前 | freshSTART实际known4 status一次；access已获权known3每次；metadata不通知 | port/2Recorder/2tests |
| PLAN-REQ-002 | §7.2/13.4 | partial failure不能抹去已提交事实或误归类 | observerthrow fixedDep无cause、无account/body/cleanup；actualport ACK后callbackLedger→UNKNOWN仍优先heldDep | START recorder/probe tests |
| PLAN-REQ-003 | §7.2 | 截止终态可携带可信冻结effect且旧worker不能覆盖新generation | DBdeadlineexpired+exactowner/run/rev/leaseowner/epoch/RUNNING/attempt>0 CAS；不要求livelease或cancel | Service/Repo/newunit/PG源码 |
| PLAN-REQ-004 | §7.2/13 | 兼容旧接口与同事务预算/事件语义 | 旧deadline attemptedrun UNKNOWN；新TIMED_OUT budgetUNKNOWN+原statusEvent/outbox同TX；旧Recorder方法/VO/RO保持 | Service/Repo/原回归 |
| PLAN-REQ-005 | §17/T3-07 | 边界与证据真实，不提前开启native | 12paths/23SQL bytes保持、每组件有效RED后GREEN、optinPG默认skip；无runtime验收虚报 | tests/README/plan/evidence |

## 4. Implementation Strategy and Dependency Order
### 4.1 Ordered strategy
先唯一observer SAM与最小新overload声明使测试编译，方法可直接UOE且不提前实现行为；在File5–8所在生产文件仅做这次compile-only先行声明，完整实现仍遵守下面各组件RED→GREEN。新START/access/deadline测试先真实RED，再最小生产实现，最后PG源码、README/证据及兼容回归。不提交scaffold；无sourceGO前不写生产/测试。
### 4.2 Test-first strategy
START/access owner分别证明通知时序、无通知分支与异常安全优先级；deadline owner以真实SQLcaptor/Service fixture证明full CAS与旧UNKNOWN控制。root独立观察各自有效RED后才给该组件GREEN；直接stub Error照实记，编译/无效fixture不是行为RED。纯SAM声明与旧方法delegate仅是编译/兼容前置，不伪造其RED。
### 4.3 Sequential and parallel boundaries
budget_lock_tests独占observer interface+2Recorder+2既有tests共5paths；root独占Service/Repo/newDeadlineTest/原GovernancePG/README共5；docowner2。两个组件可在自己有效RED门禁后并行GREEN，不重叠文件；root统一Maven时owners冻结。本Step提交前不并行下一Step；PG helper由root沿原类补，不新建测试kit。
### 4.4 Commit boundaries
单Step/12paths，semantic commit `feat(agent): preserve committed tool effects at deadlines`。root全文/全部适用MC与literalRule实际验证后精确stage/一commit，无push/重写历史。
### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| status-only observer | §7.2工具ledger effect在截止终态仍准确 | Recorder在callback内COMMIT，account/outerpostcheck可随后失败，host目前看不到commit点 | 一个SAM，不新增事件/DTO/缓存；旧方法NOOP兼容 | Implement |
| percall heldfailure | §13.4 partial failure不抹已知事实 | actual AS transport在POST后将callbackLedger转UNKNOWN，原outercatch会cleanup | 每次调用AtomicReference安全依赖异常优先；不改AS/port/异常框架 | Implement |
| currentgeneration deadline API | §7.2旧worker fencing与DB截止 | 原expireDeadline仅revision/status；claim/renew LEAST使lease不晚于deadline | 复用UPDATE赋值/Service UnitofWork，新增窄lease overload | Implement |
| native union/effectcounter | 后续native core独立范围 | 本步无Executor/plugin/bridge配置路径 | 本步不实现、不提前造resolver/strategy/engine | Already exists as later requirement; not this Step |
### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| status观察/错误优先 | PLAN-REQ-001/002 | START/AccessFacade tests | SAM/最小overload声明，现complete/account/transport | commit前后准确时序的兼容重载 | 后续host聚合，未接线 | Step 1 |
| fenced DBdeadline | PLAN-REQ-003/004 | 新AgentDeadlineEffectTest | 最小Service/Repo overload，旧mapper/事务/结果 | exactgeneration冻结effect终态 | 后续Executor终态协调，未接线 | Step 1 |
| compatibility/evidence | PLAN-REQ-005 | 原回归/optinPG compile/skip/静态 | 上两组件GREEN | 真实边界/精确commit | root最终交付 | Step 1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | AR application port/persistence技术库；已授权JDBC/独立Flyway | 现结构与Agent generator-excluded保持 | 无CRUD/新层/MP迁移/依赖；23SQL immutable | Step1；MC-ARCH-001/MC-SCOPE-001 |
| Reuse/capability | 原complete/account/UnitofWork/JdbcTemplate mapper/statusEvent/outbox | effect来自已提交fact，不造第二结果模型 | 复用TypedStatus/AgentExecutionResult；原CQE交付路径不变 | Step1；MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | 既有不可变VO/lease/domain guards；无新entity | 仅SAM+overloads | null早拒、effect闭合Set/fullSQLfence，原VO/JSON不改；无新Mapper | Step1；MC-NAME-001/MC-VALID-001/MC-MODEL-001 |
| Bean/logging/util/JSON/time/config | manual技术constructors、JDK AtomicReference/DBclock | 不新业务Bean/日志/配置/工具框架 | 固定safe异常无cause/私密信息；无ordinal/配置变动；原UTCJSON | Step1；MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001 |
| Business variation/pattern | 原Facade/UnitofWork/CAS；新postcommit host变化点 | Observer用于已提交status可选通知，CAS用于claim隔离 | 单SAM+每call holder；不引Strategy/State引擎、无异步事件承诺 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| commit通知 | 现Recorder complete/account与actualport callback边界 | 新overload可保持旧NOOP兼容 | 一个最小status回调足够，无需消息/新DTO | Observer窄port | None | Step1；MC-REUSE-001 |
| deadline与事务 | 现Repo expireDeadline/claim/renew，Service result/statusEvent | DBnow/同TX已有，缺lease owner/epoch窄谓词 | 复用旧SET与mapper，新增overload而非新repository/engine | existing UnitofWork+CAS | None | Step1；MC-DEP-001 |
| 测试/文档 | 原两个Recorder tests/PG类/Mock JDBC | 现fixtures可复用，新Deadline unit一个类 | 无repo scripts/work harness需要 | retained subsystem tests | None | Step1；MC-TEST-001 |
### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/12/13/17 | 明确接口角色/既有类型，不新POJO | Observer/Recorder/Service/Repo+tests | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | 命名/source | Step1 | PASS |
| Rule 2 | §7/12/13/17 | null/effect/run/rev/lease/actualfact与SQLfence校验 | tests先RED→5prod | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | 正反/source | Step1 | PASS |
| Rule 3 | §7/12/13/17 | 原VO/Outcome不可变合同不改，无新实体/Mapper | 原类型兼容/新port status-only | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | 旧VO回归 | Step1 | PASS |
| Rule 4 | §7/12/13/17 | manual技术库不增业务Bean/DI/日志 | scope | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | N/A | Step1 | N/A：无业务Bean/环境配置变化 |
| Rule 5 | §7/12/13/17 | JDK AtomicReference/现组件与codec，零新依赖 | Recorder/Service/Repo | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | imports/POM | Step1 | PASS |
| Rule 6 | §7/12/13/17 | 现Jackson结果/字符串state不变，无ordinal | Service+旧VO回归 | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | JSON/result | Step1 | PASS |
| Rule 7 | §7/12/13/17 | 无环境配置/flag变更，原PG前置保持 | scope/PGtest | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | N/A | Step1 | N/A：无业务Bean/环境配置变化 |
| Rule 9 | §7/12/13/17 | 既有Observer+Facade/UnitofWork/CAS适用postcommit/fence变化点 | 5prod | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | 时序/partialfailure | Step1 | PASS |
| Rule 10 | §7/12/13/17 | DBclock为harddeadline，原Instant/Duration不重置 | Repo/Service/tests | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | 截止边界 | Step1 | PASS |
| Rule 11 | §7/12/13/17 | 已授权Agent技术JDBC/generator-excluded；23SQL不改、无事件格式新建 | 12paths | §6完整时序/fence/兼容合同，不省略null/error/事务分支 | scope/bytefreeze | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | deadline_at/finished_at/current leasegeneration | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: DB绝对截止 | Not applicable: 无UI | deadline<=DBnow在UPDATE中；RUNNING/attempt>0/rev/owner/epoch精确匹配；无livelease/Java hint/cancel谓词 | 原DBclock/现Timestamp映射，不新增时钟或列 | claim/renew LEAST expiry<=deadline，故硬截止允许旧Java hint已过期；旧宽方法保持 | Step 1；TEST-002/003 |
| TIME-002 | commit→observer→account/outerpostcheck | Occurred instant | Instant | Not persisted | Internal | Not applicable: 本地回调顺序 | Not applicable: 无UI | 只实际COMMIT返回后通知；holder优先不延长原window，晚callback不能回溯替换已返回timeout | 原Clock/mono/windows保持；无新timeout/时间字段 | 原NOOP路径/Access finaldisclosure/START outerpostchecks保持 | Step 1；TEST-001/003 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ToolCommittedFactObserver.java | 最小status-only提交事实观察port，供后续host聚合而不泄露结果 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java | 先证明fresh START提交观察时序、失败保存和实际port包装边界 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java | 先证明每次当前获权access事实都在account/egress前通知 | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentDeadlineEffectTest.java | 先证明新Service/Repo过期effect与lease-generation CAS，并保留旧UNKNOWN语义 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java | 兼容新增START观察重载，在actual AS port异常转换后优先保留observer安全失败 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java | access兼容重载在Store事实后、localaccount/披露前通知status | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java | 以单条UPDATE原子执行full leasegeneration deadline CAS | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java | 新增trusted frozen-effect截止API，沿现同事务预算与status event更新 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunGovernancePostgresTest.java | 在现optin PG类补harddeadline exactfence/effect源码场景 | Step1 |
| MODIFY | README.md | 准确说明status观察和fenced deadline显式API及后续native边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-08-51-committed-effect-deadline-fence.md | 冻结本Step12文件与顺序/时序/权限/兼容门禁 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-committed-effect-deadline-fence.md | 保留真实实施/RED/GREEN/手工规则证据与未验收边界 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
已核baseline b96f646clean、SpecSHA853c0ec63235dbebabbb1d9fe62427f99c8f2d5898f5a18bc3887f87374be2aa与实际路径。writing18md24refs/executing15md19refs资源通过；Java/CQE/time/codegen/file-by-file gates沿现skill。Agent profile明确generator-excluded，现技术JDBC/独立Flyway不改。全部23migration SQL（V14+22旧）immutable，本步无新SQL/manifest/应用DDL/修复或删除。

**Observer合同。** public @FunctionalInterface ToolCommittedFactObserver只有void onCommitted(ToolExecutionOutcome.Status status)，无字段/常量/default/body/receipt/cost/error/nonce/id。两Recorder原方法签名/VO不变并delegate各自private NOOP。新executeIdempotentStart(transport,request,observer)及recordKnownStartResultAccess(request,observer)先拒nullobserver，使用既有START/access固定IAE，任何prep/store/transport前完成。freshSTART仅completeIdempotentWrite成功COMMIT后以返回的actual Outcome.status通知一次，且在account/outerpostcheck前；metadata/early/!started/failedcomplete不通知。Access每次已授权Store事实return（新/exactexisting）均通知actualknown3，先于accessaccount/最终egress。不是constructor权限证明或Event持久交付。

**实际port错误边界。** 新START每call独立AtomicReference<AgentLedgerException>，不放instance/sharedcache。wrapped observer catch Runtime→固定TIANJI_DEPENDENCY_UNAVAILABLE、无cause/observer私有message，compareAndSet(null,safe)再throw。withVerifiedCall正常return后及所有outer FixedFailure/Ledger/Runtime catch顶部首先查holder；有值直接throw，不cleanupStart/account/body/追加suppression/remap。actual AS POST后callbackLedger→OP_RESULT_UNKNOWN也不能掩盖held observer failure；holder空保持旧映射/cleanup语义。Access observer Runtime立即同safeDep，无account/egress/body/cleanup。Error不吞为成功；observer设置/抛出的interrupt flag必须保留后续可见。owner已返回timeout后latecallback才设置holder不能追溯替换旧error，但已提交journalfact保留，这是明确边界。observer失败可留accounting pending，不回滚/降级/重派。

**Repository exact API。** 原package expireDeadline(actor,StringrunId,longrevision,StringresultJson)原样。新增package Optional<StoredRun> expireDeadline(TrustedAgentActor actor,AgentRunLease lease,longexpectedRevision,StringresultJson)。SET完全沿旧TIMED_OUT：revision+1/eventseq+1/result/error/finished DBnow、leaseepoch+1/清owner与expiry/active counters清零；WHERE必须SQL包含exacttenant/type/issuer/id/run=lease.runId/revision/status RUNNING/attempt>0/lease_owner/lease_epoch/deadline_at<=clock_timestamp。无lease_expires_at>now，无Java expiryhint，无cancelpredicate，不额外attempt参数（AgentRunLease无attempt）；owner+epoch隔离claimgeneration。不能Java预读替代或no-row fallback旧宽方法。

**Service exact API。** 原public expireDeadline(actor,GetAgentRunQuery,longrevision)原样（attempt0 NONE，attempt>0 UNKNOWN）。新增public AgentRunDTO expireDeadline(TrustedAgentActor actor,AgentRunLease lease,GetAgentRunQuery query,longexpectedRevision,StringsideEffectOutcome)。所有非null/runmatch/rev>=0/effect exact CONFIRMED/NONE/UNKNOWN，否则沿现AgentLedgerService.invalid(String)抛固定TIANJI_INVALID_COMMAND/无cause，不引入IAE风格。构造同TIMED_OUT/error result、effect用可信caller冻结值，经新RepoCAS；无row sanitized TIANJI_REVISION_CONFLICT。markOutstandingBudgetUnknown与原appendStatusEvent/outbox仍同一transaction，不改event格式/引擎。不是新权限或跨scope授权；后续Executor重载/终态协调/保守fallback不在本步。

Plan Clarification CLAR-002：实际AgentLedgerService.invalid(String)返回AgentLedgerException(TIANJI_INVALID_COMMAND)，新deadline command校验复用此固定安全风格，observer null仍沿START/access既有IAE；不改旧方法/API字段/范围。

Plan Clarification CLAR-001：两个组件独立有效RED后可并行GREEN；interface/最小新方法UOE仅使真实测试编译，不先写功能。已有PG治理类使用tianji.postgres.test.url与Timeout90，不能套用tool PG的Timeout120；默认缺前置仅编译/skip。无open decisions/新依赖；source GO仍需root全文/strict/resources。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 提交事实观察与当前租约代次deadline effect隔离
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 已提交b96f646、既有Recorder/Store/actual port/ledger/typedOutcome/事务/event/outbox。
- Baseline state: START/access无commit observer；旧deadline按revision宽过期且attemptedrun保守UNKNOWN；23SQL已存在immutable。
- Observable outcome: actual committed status先于account可观察且失败安全；只有当前exactgeneration能在DBdeadline后冻结可信effect。
- End state: 两个兼容observer重载+窄Service/Repo CAS及受控测试可用；native/Executor聚合与运行验收未交付。
- Test-first gate: Required — 最小SAM/新方法UOE声明→组件真实行为RED→最小GREEN；编译/fixture错误不算RED，不提交stub。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ToolCommittedFactObserver.java`
- Purpose: 最小status-only提交事实观察port，供后续host聚合而不泄露结果。
- Symbols: ToolCommittedFactObserver.onCommitted(ToolExecutionOutcome.Status)。
- Repository evidence: 现application/tool有FixedBpmnStartToolTransport/ReadOnlyDecisionToolTransport，domain已拥有typed Status。
- Dependencies and consumers: 两Recorder import此port；后续native使用另步接线。
- Why now: 先声明唯一SAM使新overload与行为测试可编译；接口本身不包含生产副作用。
- Contract/signature changes: public @FunctionalInterface；void onCommitted(ToolExecutionOutcome.Status status)，无字段/常量/default/额外DTO。
- Input/output and state mapping: 仅传实际已提交Outcome.status；无body/receipt/cost/error/nonce/id。
- Error and edge behavior: 接口声明不认证、不持久化、不结算，不伪造Event/MQ交付。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
@FunctionalInterface
public interface ToolCommittedFactObserver {
    void onCommitted(ToolExecutionOutcome.Status status);
}
// Recorder alone invokes this status-only callback after its durable commit, before accounting.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: 新测试可引用SAM；其余新overload可用最小UOE声明支持真实RED。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java`
- Purpose: 先证明fresh START提交观察时序、失败保存和实际port包装边界。
- Symbols: 既有17测试保留；新增observer/ACK→Ledger→OP_RESULT_UNKNOWN probe。
- Repository evidence: 当前Rig/受控transport已测试freshnonce/metadata/outerlatefacts/费用.03 race。
- Dependencies and consumers: File1与Recorder最小3arg声明；原Recorders/Store/testRig均不扩路径。
- Why now: 先完整合法fixture→真实缺观察/优先级行为RED，root观察后才生产GREEN。
- Contract/signature changes: 增加新3arg重载测试；不改变旧2arg/VO/17断言。
- Input/output and state mapping: SUCCEEDED/REJECTED/FAILED_KNOWN/UNKNOWN实际complete返回status exact一次；complete<observer<account；metadata/early/!started/failedcomplete零次。
- Error and edge behavior: null在任何work前拒绝；throw observer保持已提交fact、无account/body/cleanup，安全Dep无cause/private文本；ACK后callbackLedger被probe包装UNKNOWN仍优先heldDep；lateouterfailure先观察；中断可见。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
assertNullObserverRejectedBeforePreparationOrTransport();
assertCommittedStatusesAndOrderExactlyOnce("complete", "observer", "account");
assertNoObservationForMetadataEarlyLoserOrFailedCompletion();
assertProbeAckThenCallbackLedgerWrappedUnknownStillReturnsHeldDependencyWithoutCleanup();
assertObserverFailureKeepsFactAndSkipsAccountBodyWhileOldTwoArgumentCasesRemainUnchanged();
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: 新observer矩阵有效RED已由root记录，无编译/fixture错误当行为证明。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java`
- Purpose: 先证明每次当前获权access事实都在account/egress前通知。
- Symbols: 原10测试保留；recordKnownStartResultAccess(request,observer)。
- Repository evidence: 现Facade先Store事实再ownaccess0结算/同freshwindow gate，受控Scene已覆盖Known3/null非零原费。
- Dependencies and consumers: File1与Facade最小重载声明、原Store package fact。
- Why now: 独立access组件真实RED，不需等START组件全部RED。
- Contract/signature changes: Known3/exactexisting重复每次通知；原1arg/7fieldVO无变。
- Input/output and state mapping: factreturn→observer(actual sourceOutcome.status)→accessaccount→finalwindow；新/既有access均fresh授权。
- Error and edge behavior: nullobserver在store前拒绝；Runtime变fixed Dep无cause，不account/egress/body/cleanup；中断保持，历史audit不作新权。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
assertNullObserverRejectedBeforeStore();
for (var sourceStatus : knownThree) assertOrder("fact", "observer:" + sourceStatus, "account", "egress");
assertExactExistingAuthorizedAccessNotifiesEachReadWithoutNewReservation();
assertThrowingObserverKeepsStoredFactAndSkipsAccountingAndBody();
assertOldOneArgumentResultAndSourceCostRemainUnchanged();
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: access组件有效RED；root可独立授权此组件最小GREEN。

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentDeadlineEffectTest.java`
- Purpose: 先证明新Service/Repo过期effect与lease-generation CAS，并保留旧UNKNOWN语义。
- Symbols: AgentDeadlineEffectTest；新expireDeadline overloads。
- Repository evidence: 旧Service构造TIMED_OUT后markUnknown/event同TX；旧Repo只有revision/status/deadline，不带leasegeneration。
- Dependencies and consumers: Service/Repo仅最小UOE新声明；现Mock/JdbcTemplate SQLcaptor和transaction helpers。
- Why now: 先capture真实SQL及Service行为RED，防只Java预读伪fence。
- Contract/signature changes: 新增测试，不造新runtime/DB/helper类。
- Input/output and state mapping: CONFIRMED/NONE/UNKNOWN输入原样进结果；exactactor/run/owner/epoch/rev/RUNNING/attempt>0/DBexpired SQL谓词；同TX预算UNKNOWN与既有事件。
- Error and edge behavior: 无livelease/current Java hint/cancel谓词；错owner/epoch/rev/no row不能改；null/runmismatch/rev<0/effect非法沿现Service.invalid固定TIANJI_INVALID_COMMAND；旧attemptedrun仍UNKNOWN。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
assertInvalidEffectRunLeaseRevisionUsesExistingTianjiInvalidCommandBeforeWriting();
assertSqlContainsExactOwnerRunRevisionRunningAttemptLeaseOwnerEpochAndDatabaseDeadline();
assertSqlOmitsLiveLeaseExpiryAndCancelPredicatesDespiteExpiredClaimHint();
for (String effect : List.of("CONFIRMED", "NONE", "UNKNOWN")) assertResultBudgetAndEventShareTransaction(effect);
assertMissingFenceIsRevisionConflictAndOldAttemptedDeadlineRemainsUnknown();
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: deadline组件真实RED，SQL捕获与数据fixture均有效。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java`
- Purpose: 兼容新增START观察重载，在actual AS port异常转换后优先保留observer安全失败。
- Symbols: executeIdempotentStart(transport,request,observer)、dispatchAndRecordStart、private NOOP/percall AtomicReference。
- Repository evidence: 当前fresh completeIdempotentWrite在callback内返回后直接account；outer三种catch会cleanup；实际AS callbackLedger可变OP_RESULT_UNKNOWN。
- Dependencies and consumers: File1/2有效RED；原Store/transport/nonce/account/bookkeeping不变。
- Why now: 自己的有效RED后最小实现，先避免已提交事实被cleanup或错误remap误处理。
- Contract/signature changes: 原2arg签名/VO全部保留且delegate private NOOP；新3arg同returntype；不改executeReadOnly。
- Input/output and state mapping: 入口nullobserver先拒；每call独立AtomicReference<AgentLedgerException>；complete COMMIT后wrappedObserver(actualstatus)一次再account；metadata/!started不通知。
- Error and edge behavior: observer Runtime安全Dep无cause/private文本compareAndSet→throw；withVerifiedCall正常return后和每个outer FixedFailure/Ledger/Runtime catch顶部先查holder，有值直接throw无cleanup/account/body/remap；holder为空沿旧逻辑；不捕Error为成功。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
public IdempotentStartResultVO executeIdempotentStart(transport, request) { return executeIdempotentStart(transport, request, NOOP); }
requireNonNullObserverBeforeAnyWork(observer);
AtomicReference<AgentLedgerException> observerFailure = new AtomicReference<>();
ToolCommittedFactObserver wrapped = status -> { try { observer.onCommitted(status); } catch (RuntimeException privateFailure) { var safe = fixedSafeDependencyWithoutCause(); observerFailure.compareAndSet(null, safe); throw observerFailure.get(); } };
// Fresh callback: recorded = completeIdempotentWrite(...); wrapped.onCommitted(recorded.outcome().status()); then account(...).
// After withVerifiedCall return AND first line of every outer catch: if (observerFailure.get() != null) throw observerFailure.get(); else retain previous logic.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: fresh已提交status可观察；失败事实保留，旧NOOP路径/VO/RO语义兼容。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java`
- Purpose: access兼容重载在Store事实后、localaccount/披露前通知status。
- Symbols: recordKnownStartResultAccess(request,observer)、private NOOP。
- Repository evidence: 既有Store返回fact+budget+window；Facade单独bestEffort account再checkDisclosureWindow。
- Dependencies and consumers: File1/3有效RED；原Store/VO/domain untouched。
- Why now: access独立RED后实现，不借transport/新增异常框架。
- Contract/signature changes: 原1arg delegate private NOOP；新2arg返回原StartResultAccessVO，原构造guards不变。
- Input/output and state mapping: Store factreturn后onCommitted(fact.sourceOutcome.status)每次一次，new/exactexisting均通知；然后原account/egress。
- Error and edge behavior: null先拒；observer Runtime直接fixed Dep无cause/private文本，不account/egress/body/cleanup；中断原flag留给后续既有gate；不吞Error。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
public StartResultAccessVO recordKnownStartResultAccess(request) { return recordKnownStartResultAccess(request, NOOP); }
requireNonNullObserverBeforeStore(observer);
var fact = store.recordKnownStartResultAccess(request);
try { observer.onCommitted(fact.sourceOutcome().status()); } catch (RuntimeException privateFailure) { throw fixedSafeDependencyWithoutCause(); }
// Keep existing budget identity validation, local-zero accounting, same-window disclosure and original VO construction unchanged.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: 受权访问事实每次可观察；firstaudit与原费用/本地0分离及body门禁保持。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`
- Purpose: 以单条UPDATE原子执行full leasegeneration deadline CAS。
- Symbols: package expireDeadline(actor,AgentRunLease,long,String):Optional<StoredRun>。
- Repository evidence: 旧expireDeadline SQL与claim/renew LEAST(expiry,deadline)真实存在；旧finishLeased要求live因此不能复用硬deadline。
- Dependencies and consumers: File4有效RED、原StoredRun mapper与JdbcTemplate；调用者File8。
- Why now: 先实现真正SQLfence，再Service使用；旧StringrunId overload原样保留。
- Contract/signature changes: 仅新增lease overload，无attempt参数或schema/state变更。
- Input/output and state mapping: TIMED_OUT/revision+1/eventseq+1/result/error/finishedDBnow/leaseepoch+1/clearlease+activecounters与旧相同；WHERE全owner/run/rev/RUNNING/attempt>0/owner+epoch/deadline<=DBnow。
- Error and edge behavior: 不要求lease_expires_at>now、不比较hint、不加cancel谓词；no row返回empty，旧worker不能覆盖新generation/终态；不fallback旧宽方法。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
Optional<StoredRun> expireDeadline(actor, AgentRunLease lease, long expectedRevision, String resultJson) {
    return jdbc.query("UPDATE tianji_agent.agent_run SET sameTimedOutAssignmentsAsExistingMethod WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=? AND revision=? AND status='RUNNING' AND attempt>0 AND lease_owner=? AND lease_epoch=? AND deadline_at<=clock_timestamp() RETURNING *",
        runMapper(), resultJson, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), lease.runId(), expectedRevision, lease.owner(), lease.epoch()).stream().findFirst();
}
// Existing String-runId overload is retained verbatim; there is no live-lease or cancellation predicate.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: 实际DBdeadline可过期的当前exactgeneration CAS可用，错误fence不写。

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java`
- Purpose: 新增trusted frozen-effect截止API，沿现同事务预算与status event更新。
- Symbols: expireDeadline(actor,lease,GetAgentRunQuery,long,String):AgentRunDTO。
- Repository evidence: 既有expireDeadline(actor,query,revision) attempt0NONE/attempt>0UNKNOWN与transaction/markOutstandingBudgetUnknown/appendStatusEvent。
- Dependencies and consumers: File4有效RED/File7；原AgentExecutionResult codec/Outbox/event路径。
- Why now: 复用已有UnitofWork，隔离新API且不接Executor。
- Contract/signature changes: 新增public5arg；actor/lease/query非null、run相同/rev>=0/effect仅CONFIRMED/NONE/UNKNOWN；非法沿现Service.invalid固定TIANJI_INVALID_COMMAND/无cause，旧3arg原样。
- Input/output and state mapping: 同TIMED_OUT/error/TIMED_OUT结果，sideEffectOutcome取显式trustedcaller frozen值；新Repo CAS→budgetUNKNOWN→原statusEvent/outbox同一TX。
- Error and edge behavior: CASabsent sanitized TIANJI_REVISION_CONFLICT；无broad-expire fallback、无Java-onlyfence、无新的body/event格式；不是新权限证明。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
validateActorLeaseQueryRevisionAndExactEffectSetOrExistingInvalidCommand(actor, lease, query, expectedRevision, effect);
return transaction.execute(status -> {
    var previous = requireRun(actor, query);
    var result = sameTimedOutResultWithFrozenEffect(query.runId(), effect);
    var updated = repository.expireDeadline(actor, lease, expectedRevision, json.write(result, AgentJsonCodec.MAX_RESULT_BYTES)).orElseThrow(() -> sanitizedRevisionConflict());
    repository.markOutstandingBudgetUnknown(actor.tenantId(), query.runId()); appendStatusEvent(actor, previous, updated, previous.run().status(), TIMED_OUT, null); return updated.run();
});
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: trusted frozeneffect API通过currentgeneration CAS写终态；旧API仍保守UNKNOWN，native尚未接线。

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunGovernancePostgresTest.java`
- Purpose: 在现optin PG类补harddeadline exactfence/effect源码场景。
- Symbols: AgentRunGovernancePostgresTest deadline helper/cases。
- Repository evidence: 真实前置tianji.postgres.test.url与Timeout90；PostgresTestDatabase既有helper，无toolPG Timeout120混用。
- Dependencies and consumers: File7/8 API；root统一编译/缺前置skip，不启动数据库。
- Why now: 生产API稳定后补source-level真实SQLfixture，并最终编译回归。
- Contract/signature changes: 同类追加，不改URLguard/Timeout90/建库或迁移机制。
- Input/output and state mapping: expiredDBdeadline且lease hint已过期当前generation允许CONFIRMED/NONE/UNKNOWN；wrongowner/epoch/rev拒绝；旧deadlineUNKNOWN control。
- Error and edge behavior: 无明确URL仅编译/skip，不称真实PG锁/CAS/transaction通过；不改任一23SQL。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```java
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
// Reuse existing setup; create fresh claimed run and make DB deadline expired, retaining exact generation.
assertCurrentGenerationExpiresWithEachFrozenEffectEvenWhenLeaseHintExpired();
assertWrongOwnerEpochOrRevisionCannotUpdateAndOldMethodRetainsUnknown();
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: optin源码覆盖exactDBfence场景，默认仅编译与skip。

#### File 10 — `MODIFY README.md`
- Purpose: 准确说明status观察和fenced deadline显式API及后续native边界。
- Symbols: Agent runtime/tool journal治理说明。
- Repository evidence: Step16/17 README已有显式库/结果访问与未接native限制。
- Dependencies and consumers: 本Step源码/test实际GREEN后root更新。
- Why now: 仅写最终实现事实，不先宣称整个工具链/runtime闭合。
- Contract/signature changes: 无配置/依赖/权限/APIendpoint变化。
- Input/output and state mapping: commit→observer→account，observerfailure fact保留/account可pending；新deadline exactgeneration effect，旧UNKNOWN保持。
- Error and edge behavior: late callback在owner已返回timeout后才设置holder不能回溯改已返回error；真实IAM/PG/模型未验收。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```text
Document status-only post-commit notification before accounting and body disclosure.
Explain safe observer failure retention and the current-generation database-deadline CAS overload.
Retain explicit native/core aggregation/config/approval/MCP and real-runtime acceptance as pending; do not claim automatic effect reconciliation.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: README与实际显式API、错误/时序及未接线边界一致。

#### File 11 — `CREATE docs/egon/plan/2026-11-01-08-51-committed-effect-deadline-fence.md`
- Purpose: 冻结本Step12文件与顺序/时序/权限/兼容门禁。
- Symbols: Template5/Review；单Step/五aliases/17MC/10Rules。
- Repository evidence: 当前blueprint SHA与baseline clean；前17step已提交。
- Dependencies and consumers: root闭合合同/真实paths/skill资源。
- Why now: source前root全文/strict/resources，文档不能代替功能验收。
- Contract/signature changes: 不伪造Accepted/SourceGO/测试计数。
- Input/output and state mapping: 严格路径/owner/RED→GREEN/旧23SQL immutable/无nativeconsumer。
- Error and edge behavior: root源码GO前任何source/test修改都不启动；后续native另步。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```text
Record the exact reviewed baseline, effective blueprint SHA, twelve paths and two independent RED barriers.
Bind observer ordering and safe failure precedence to the frozen deadline-generation SQL and transaction gates.
Validate this Review plan strictly before root full review and any source GO; leave actual execution conclusions to evidence.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: strict与root全文/资源通过后才允许source GO。

#### File 12 — `CREATE docs/egon/evidence/2026-11-01-committed-effect-deadline-fence.md`
- Purpose: 保留真实实施/RED/GREEN/手工规则证据与未验收边界。
- Symbols: 5aliases/17MC/10Rules/PENDING骨架。
- Repository evidence: 本步开始尚无实际sourceGO/RED/GREEN日志。
- Dependencies and consumers: 计划/后续root真实source/log/static/audits。
- Why now: 计划交root后可创建PENDING；实际结果到达再逐项闭合。
- Contract/signature changes: 不预填PASS/count/runtime或未来commithash。
- Input/output and state mapping: 有效stub error/fixture错误分类；PG编译skip非运行；全T3-07Partial。
- Error and edge behavior: docowner无build/README/source/commit，不把observer callback当durableEventdelivery。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；本文件具体合同见本块及§6，其他N/A有§12依据。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；本文件遵守status-only/实际fact、safe错误、fullSQLfence、旧VO/23SQL兼容，Rule4/7无业务Bean/配置变化，独立证据见§4.8/12。
- Implementation pseudocode:
```text
Create independent pending rows for five local aliases, seventeen manual checks and ten literal rules.
When root supplies actual logs, preserve valid RED failures and direct stub errors separately from invalid fixtures.
Close only proven source/test gates, retain real PostgreSQL/IAM/model and native integration limits, and record the final root commit after Git delivery rather than predicting it.
```
- Verification contribution: TEST-001/002/003/004/005按角色验证；实际结果仅执行后填evidence，不以静态或skip推PG运行。
- After this file: 执行证据PENDING；实际后逐项闭合，未填整步实现PASS。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；skill strict从workspace root执行。
- Verification command: §8 canonical JDK25/source-reactor exact8类；root统一运行，docowner不Maven。
- Expected result: 新组件有效RED后聚焦/完整GREEN；PG缺显式前置skip，旧NOOP/VO/RO/deadlineUNKNOWN兼容；具体计数执行后如实填。
- Failure returns to: 任何observer时序/holder优先/SQLfence/事务/兼容失败回到owner当前组件，不弱化断言/新增catch掩盖；无效fixture先修再重跑有效RED。
- Completion criteria: 全部12path内实现无stub；17MC/10Rules实际独立闭合、旧23SQL字节相同、root全文/独审/静态/diff/cached通过；无runtime验收虚报。
- Rollback: 可回退应用binary，原NOOP/旧deadline接口继续；不改删任一现有migration/事实，不repair/drop/backfill/重派。无新DDL需要撤销。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/ToolCommittedFactObserver.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolIdempotentStartRecorderTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorderTest.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentDeadlineEffectTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolInvocationRecorder.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolResultAccessRecorder.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java、stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentRunGovernancePostgresTest.java、README.md、docs/egon/plan/2026-11-01-08-51-committed-effect-deadline-fence.md、docs/egon/evidence/2026-11-01-committed-effect-deadline-fence.md
- Commit: `feat(agent): preserve committed tool effects at deadlines`

## 8. Test, Validation, and Quality Gates

root统一Maven；source GO和每组件真实RED前不得提前功能实现。以下全测试命令是计划验证，并非声称已运行：
```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=AgentDeadlineEffectTest,ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest,ToolInvocationRecorderTest,ToolResultAccessStoreTest,ToolExecutionStorageVerifierTest,AgentBudgetLockOrderingTest,AgentRunGovernancePostgresTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  > /tmp/tianji-committed-effect-deadline-final.log 2>&1
```

| Gate | Working directory | Exact command/classes | Proof | Expected boundary | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-001 observer | tianji-service | ToolIdempotentStartRecorderTest,ToolResultAccessRecorderTest；先RED subset后GREEN | null-beforework/known4/known3/order/exactcallback/metadata0/throwfactretained/ACK→Ledger→UNKNOWN holder/interruption/oldNOOP | controlledtransport/ledger，无真实AS HTTP或crypto | budget/root |
| TEST-002 deadline | tianji-service | AgentDeadlineEffectTest；先RED后GREEN | exactSQL owner/run/rev/owner+epoch/RUNNING/attempt/DBdeadline；no live/cancel；effectclosedSet/预算UNKNOWN+event同TX/旧UNKNOWN | JDBCcaptor/Service fixture非真实PG | root |
| TEST-003 compatibility | tianji-service | §8 exact8类完整selector | 新oldRecorder/ResultAccessStore/StorageVerifier/BudgetLock/治理源码回归 | 不新增/修改VO/SQL/AS/native/config，actualcount记evidence | root |
| TEST-004 optinPG | tianji-service | AgentRunGovernancePostgresTest，原tianji.postgres.test.url/Timeout90 | actual expireddeadline currentgeneration effect与wrongfence/旧控制源码 | 无URL仅compile/skip，不启动DB/应用SQL，不称PG通过 | root |
| TEST-005 static/plan | workspace root/service | writing validator --strict；git diff --check/显式12paths/旧23SQL bytecompare | header/trace/17MC/10Rules/范围/frozenSQL | plan可执行不等于实现PASS；source与文档最终手工审查 | root/doc |

Strict command：`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-08-51-committed-effect-deadline-fence.md --strict`。source/test目录所有既有selector已rg核，新AgentDeadlineEffectTest按scope创建；无新增repo脚本/harness，保留测试在subsystem。回归只在新失败/源码变化需要时追加，不启动项目。

## 9. Migration, Compatibility, Rollout, and Rollback
无新migration或SQL变更；全部23现migration immutable，V13 SHA=f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3、V14 SHA=88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a保留。Agent generator excluded，不产CRUD/MP迁移/codegen consumption日志。旧Recorder签名/VO/executeReadOnly与旧deadlineUNKNOWN不变；新Observer无body/authority、显式library未native接线。应用binary可回退，现SQL与历史facts留存，任何后续存储修正仅独立批准forwardfix，不repair/drop/重派。本步不启动任何服务/DB/浏览器或激活配置/权限。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files/symbols | Validation | Completion boundary |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7.2/12/13 | Step 1 | port/2Recorder/2tests | TEST-001/002/003 | freshSTART实际known4 status一次；access已获权known3每次；metadata不通知 |
| PLAN-REQ-002 | §7.2/13.4 | Step 1 | START recorder/probe tests | TEST-001/002/003 | observerthrow fixedDep无cause、无account/body/cleanup；actualport ACK后callbackLedger→UNKNOWN仍优先heldDep |
| PLAN-REQ-003 | §7.2 | Step 1 | Service/Repo/newunit/PG源码 | TEST-002/004 | DBdeadlineexpired+exactowner/run/rev/leaseowner/epoch/RUNNING/attempt>0 CAS；不要求livelease或cancel |
| PLAN-REQ-004 | §7.2/13 | Step 1 | Service/Repo/原回归 | TEST-001/002/003 | 旧deadline attemptedrun UNKNOWN；新TIMED_OUT budgetUNKNOWN+原statusEvent/outbox同TX；旧Recorder方法/VO/RO保持 |
| PLAN-REQ-005 | §17/T3-07 | Step 1 | tests/README/plan/evidence | TEST-003/004/005 | 12paths/23SQL bytes保持、每组件有效RED后GREEN、optinPG默认skip；无runtime验收虚报 |

## 11. Risks, Blockers, and User Decisions
| ID | Concern | Decision/evidence | Owner | Disposition |
| --- | --- | --- | --- | --- |
| DEC-001 | actual AS把POST后callbackLedger变UNKNOWN | percall holder在return及每outercatch顶部优先；无cleanup/account/body/remap | root | Closed，probe真实边界先RED |
| DEC-002 | harddeadline时lease一定已到期导致CAS误拒 | claim/renew LEAST，SQL精确owner/epoch/rev/DBdeadline，不加liveexpiry/cancel | root | Closed，unit SQL捕获+optinPG源码 |
| DEC-003 | observerfail后commit事实被抹或费用虚假闭合 | 只通知已提交actualstatus；safeDep无cause，account可PENDING，不rollback/downgrade/redispatch | root | Closed，旧NOOP/VO保持 |
| RISK-001 | latecallback在owner已返回timeout后才失败 | holder不能追溯替换已返回error，journalfact仍保留 | root | Tracked，限制在README/evidence诚实记录 |
| RISK-002 | 未接native/runtime验收 | 本步仅prerequisite library APIs；后续Executor聚合/core与真实IAM/PG/模型另验 | 用户/root | Tracked，fullT3-07/Goal Partial，无自动运行 |

## 12. Review and Acceptance
已以当前source/闭合合同核对12paths和全部门禁，当前无协议/依赖未决点。以下PASS仅计划可执行性，Review沿原exact用户授权，无fakeAccepted；root尚须全文/strict/resources后发sourceGO。实际evidence先PENDING，RED/GREEN/17MC/10Rules与最终commit按真实执行填，不把计划当runtime验收。

### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原Agent application port/persistence技术库与现JDBC/独立Flyway | 无层/模块/引擎变更，Agent generator-excluded，无DDL | None |
| MC-REUSE-001 | Applicable | PASS | 现Facade/typedOutcome/complete/account/UnitofWork/CAS/event/outbox | 仅statusObserver一个实际postcommit变化点，复用既有事务/mapper | None |
| MC-DEP-001 | Applicable | PASS | JDK AtomicReference/现Spring/JDBC/domain | 不新增依赖/构建脚本/通用工具 | None |
| MC-NAME-001 | Applicable | PASS | ToolCommittedFactObserver与现Recorder/Service/Repo方法名 | 接口为单SAM技术port；无新POJO/字段常量/默认方法 | None |
| MC-VALID-001 | Applicable | PASS | nullobserver early、actualfact status、effect closedSet/run/lease/rev/SQLownerepoch | observer不是auth；DBfence不借Java检查/过期hint作判据 | None |
| MC-MODEL-001 | Applicable | PASS | 原所有VO/Outcome不变，observer仅Status | 无新实体/可变状态/constructor宽松化/正文元数据 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无PO/entity转换、新字段或DTO | 复用原AgentExecutionResult codec与既有结果 | 无需新Mapper |
| MC-LOG-001 | Not applicable | N/A | 无新增业务日志/Service Bean；固定safe exception | 不泄漏observer文本/cause或body/receipt/nonce | 无日志注入变更 |
| MC-BEAN-001 | Not applicable | N/A | 现manual技术Recorder/ledger，不改factory/config | 无新增业务DI/Bean/Qualifier注册 | 沿既有technical构造模式 |
| MC-UTIL-001 | Applicable | PASS | 现JDK/Spring/typedport/codec/UnitofWork | AtomicReference为percall不缓存；无新utility/线程机制 | None |
| MC-JSON-001 | Applicable | PASS | 原TIMED_OUT result codec/VO/closedeffect字符串 | 只已有sideEffectOutcome值，无JSON/enum编码格式改动 | None |
| MC-TIME-001 | Applicable | PASS | DBclock deadline_at<=clock_timestamp与claim/renew LEAST | 不用Java expiryhint/livelease门禁，精确当前generation；晚fact观察不改期限 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无profiles/flags/config/权限变化 | 原PG显式URL/Timeout90守卫保持 | 无配置面改动 |
| MC-PATTERN-001 | Applicable | PASS | Observer/Facade/UnitofWork/CAS已有风格 | status-only实际变化点，单SQL generation fence，无新Strategy/引擎 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确12paths/5prod/4tests/3docs，原23SQL immutable | 无Executor/plugin/bridge/AS/native/AA/MCP/schema/新counter | None |
| MC-TEST-001 | Applicable | PASS | 每组件真实RED→GREEN；probe actual AS wrapping/SQLcaptor/原8类selector | 错误fixture/编译不当RED，optinPG只compile/skip | 实际结果后填evidence |
| MC-BLOCKER-001 | Applicable | PASS | root闭合observer异常优先级/全SQLfence与12paths；baseline/hash已核 | 当前无协议待决；sourceGO仍需root全文/strict/resources | None |

### 12.6 Final verdict
PASS — Ready for user review
