# 固定START一次性操作的最终调用期限

| Field | Value |
| --- | --- |
| Document | `2026-11-01-12-50-bounded-start-one-shot-deadline.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 12:50 Asia/Shanghai` |
| Updated | `2026-11-01 12:50 Asia/Shanghai` |
| Owner | 用户授权，root实施与审计 |
| Repository | `tianji-service` |
| Scope | 固定START additive bounded OneShot；7paths |
| Source Requirement | 原蓝图§7/11.3/12/13工具期限/幂等/partialfailure，继续未完成工作分步提交 |
| Baseline Revision | `main@3548ed3687d295cafff385c91af7b7aa9961bc95`；实施前clean |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；原exact执行授权，无Accepted字段 |
| Spec Revision | 2026-11-01；SHA-256 `4d92207e63d1926925ed229478b7fa80a69432b9916e9db826731169f6a59d45` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [原子审批消费](2026-11-01-11-58-atomic-tool-approval-journal.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary
为固定START增加可选择最终调用期限的一次性重载。现start()的HTTP timeout固定于回调前scope，不能兑现回调内最终选定的µ DISPATCH deadline；新增显式重载是AA Recorder前置。保持旧SAM与旧调用，default新方法safe OPERATION_NOT_SENT，绝不降级到unbounded。AS复用原请求scope与消费位，不新timer/transport engine。

## 2. Target Spec and Effective Design
有效蓝图§7/11.3/12/13/17、T3-07要求当前工具执行/期限/副作用事实。用户原exact执行授权持续有效；baseline3548已提交原子审批journal。当前port无deadline参数，AS client仅取scope.remaining，独立源码审查确认实际差距。实现此必要兼容前置，AA Recorder另步；不是审批/START/角色授权。

## 3. Effective Requirements and Acceptance
| ID | Source | Acceptance | Files |
| --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | 旧SAM/source/binary调用兼容，新bounded默认safe拒绝 | AR port/Test |
| PLAN-REQ-002 | §7/11.3/12/13/17 | 最终µ期限真正限制实际POST，原wall+mono不重置 | AS transport/Test |
| PLAN-REQ-003 | §7/11.3/12/13/17 | sameonce/callback scope/取消/ACK事实与错误分类不变 | AS transport/Test |
| PLAN-REQ-004 | §7/11.3/12/13/17 | 旧调用/SDK/helper/JSON/23SQL/配置与权限保持 | 全7paths/旧回归 |
| PLAN-REQ-005 | §7/11.3/12/13/17 | 分组件有效RED、实证回归/手工规则/单提交 | tests/README/plan/evidence |

## 4. Implementation Strategy and Dependency Order
### 4.1 Repository baseline
main3548ed3687d295cafff385c91af7b7aa9961bc95 clean；Spec SHA4d92207e63d1926925ed229478b7fa80a69432b9916e9db826731169f6a59d45，23SQL immutable。
### 4.2 Test-first strategy
port default UOE/newASbounded UOE只供编译；两个组件分别有效RED才GREEN，不把compiler/fixture错误当RED。旧tests正文保持；允许必要fixture新增但不得放宽旧断言。
### 4.3 Sequential and parallel boundaries
Root owns ARport/Test、README、plan/evidence五路径；tool_audit owns AStransport/Test两路径。不是独自在代码库，保留其他改动；Root统一Maven前所有Java冻结。
### 4.4 Commit boundaries
一Step七文件一个commit `feat(agent): bound fixed START one-shot deadlines`；不push/改历史/启动app或DB。
### 4.5 Spec Simplicity and Implementation-necessity Audit
| Spec element | Source necessity | Existing capability | Decision |
| --- | --- | --- | --- |
| 最终deadline传实际HTTP | §7/12/13 | 现port无参数，client只读旧scope | additive重载+scope clip，不新engine |
| 旧host兼容 | 保原接口与调用 | SAM lambda旧start | default安全拒绝新功能，旧path保留 |
| partial failure | §13.4事实不能抹 | 原operationStarted/callbackCompleted分类 | 原机制沿用，clip后过期仅抑制return |
### 4.6 Change-unit Dependency Matrix
| Unit | Requirements | RED | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| additive port | PLAN-REQ-001/004 | PortTest | default UOE declaration | safe bounded contract | AS/futureAA Recorder | Step1 |
| actual HTTP clip | PLAN-REQ-002/003 | AS Test | port GREEN/ASbounded UOE | actualsame-scope POST bound | futureAA Recorder | Step1 |
| compatibility/docs | PLAN-REQ-004/005 | Regression | bothGREEN | reviewable7pathcommit | 用户 | Step1 |
### 4.7 Java, Spring, and Egon-COLA Implementation Standards
| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | AR port/AS existingtechnical Adapter | 保现结构 | 无新层/Bean/DDL/CRUD | Step1；MC-ARCH-001 |
| Reuse | CallScope/publishLock/once/helper/SDK | 原机制继续 | 不造timer/auth/JSON引擎 | Step1；MC-REUSE-001 |
| Model/validation | 原immutable Request/Observation | bounded是typed Instant参数 | no newDTO/explicit futureµ/noextension guards | Step1；MC-VALID-001 |
| Pattern/time | callback Adapter/requestscope | 延迟确定期限实际变化点 | originalwallmono+shortlock clip | Step1；MC-TIME-001/MC-PATTERN-001 |
| Bean/config/log | 无此面变化 | 有据N/A | 不增加无关Service/日志/YAML | Step1；MC-SCOPE-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 实际HTTP期限 | OneShotStart/AS client(scope.remaining) | callback后计算bound无法传入旧start | 一重载+原scope field/helper足够 | reuse | None | Step1；MC-REUSE-001 |
| 取消/并发/事实 | publishLock/consumed/operationStarted/callbackCompleted | 已有bounded lifecycle与safeerror | 复用原状态，不新执行器 | reuse | None | Step1；MC-SCOPE-001 |
| SDK/身份 | HttpProcessClient/credentialhelper | 签名/tenant/realSERVICE与strictSDK已有 | 不改SDK/helper/协议 | reuse | None | Step1；MC-DEP-001 |
### 4.8 User-mandated Java Rule Implementation Matrix
| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §7/11.3/12/13/17 | 现技术port/scope职责，无新实体 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 2 | §7/11.3/12/13/17 | typed deadline与dynamic owner/TX/sharedonce校验 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 3 | §7/11.3/12/13/17 | 原record与DTO不变，无转换层 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 4 | §7/11.3/12/13/17 | 无业务Bean/DI/log | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 5 | §7/11.3/12/13/17 | JDK与现helper/SDK复用 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 6 | §7/11.3/12/13/17 | 原JSON/receipt不改，无ordinal | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 7 | §7/11.3/12/13/17 | 无配置/profile变化 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | N/A：无此面变化 |
| Rule 9 | §7/11.3/12/13/17 | 复用callback Adapter/requestscope | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 10 | §7/11.3/12/13/17 | Instant/Clock/Duration/nano原起点 | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |
| Rule 11 | §7/11.3/12/13/17 | 现Agent技术架构/23SQL保留，无CRUD/MP迁移，generator excluded | tests→port/AS→docs | §6/逐文件 | compile/source/tests/manual | Step1 | PASS |

### 4.9 Time semantics and conversion contract
Time contract: Required
| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | start(callDeadlineAt)/scope narrowedDeadline | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时刻 | Not applicable: 无UI | nonnull/future/µexact≤min(originalwindow,credential)；不round/noextension | 原Clock/LongSupplier，原t0/n0 | 不改旧start默认范围 | Step 1；TEST-001/002 |
| TIME-002 | remaining/HTTP timeout/late ACK | Duration | Duration | Not persisted | Internal | Not applicable: 技术窗口 | Not applicable: 无UI | originalStartedNano+duration(originalStartedAt,min(all deadlines))-nowNano，wall回拨不延长；ACK事实保留 | nano与wall双限，无reset | 旧SDK/分类/lifecycle不变 | Step 1；TEST-002/003 |

## 5. Change File Tree
| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java | 旧SAM与bounded默认安全拒绝反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java | additive bounded重载，保旧SAM | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java | 真实SDK请求期限与同一次性位/生命周期反证 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java | AS覆写新重载，实际HTTP使用最终期限 | Step1 |
| MODIFY | README.md | 说明新重载/旧调用兼容/实际HTTP期限边界 | Step1 |
| CREATE | docs/egon/plan/2026-11-01-12-50-bounded-start-one-shot-deadline.md | 锁定7paths/API/RED/手工门禁 | Step1 |
| CREATE | docs/egon/evidence/2026-11-01-bounded-start-one-shot-deadline.md | 真实日志/错误/结论与提交证据 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications
**Port.** 仅新增 `default StartObservation OneShotStart.start(Instant callDeadlineAt)`，默认固定safe Failure(OPERATION_NOT_SENT)，无cause，不调用旧start。旧abstract `start()`/Callback/Request/DTO/九FailureKinds不变，SAM仍唯一；旧host构造/调用兼容，futureAA明确调用新重载且不得fallback。

**AS exact behavior.** anonymous OneShotStart实现两入口，共享原 owner/callbackActive/TX/consumed.compareAndSet。一次执行在owner/active/TX/once守卫后，bounded=true先安装deadline再current/client/command/beginOperation；旧start bounded=false不进入nullbound校验。narrow在现publishLock内：先原remaining检查active/close/interrupt，再验证非null/future/µexact且不晚于当前min(window,credential)，不round；写volatile narrowed deadline，原remaining再核。remaining将新bound加入原min，以原entry wall/nano起点计算，无重建budget/extension/newtimer。SDK POST timeout来自新remaining，release GET仍原窗口。不能在catch中把真实post/remote错误误映notSent；仅pre-send范围沿现OPERATION_NOT_SENT，spent bit不释放。

**Lifecycle.** invalidbound noPOST且已消费samebit；重试旧或新method/跨线程/离开callback沿原CANCELLED。原operationStarted/callbackCompleted规则不变：已ACK并callback记录后新期限过期，外层可拒return但不能抹事实/虚报UNKNOWN/重派。cancel/close先清bearer再取消future，callerHTTP/IAM不close。timeout不撤销远端业务副作用；历史事实依旧由Recorder原nonce记账。新期限只约束本地等待/准入，不能承诺远端栈被撤回。

**Scope.** 新API必要性已有两独立源码设计一致；7paths，旧Recorder/native/SDK/helper/codec/权限/配置/23SQL readonly。无依赖/迁移/Bean/角色grant/runtime启用。AARecorder在后续Step使用，现native仍拒AA，未来guard须显式转发新重载。

## 7. Ordered File-by-file Implementation Steps
### Step 1 — 固定START一次性操作的最终期限
- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004、PLAN-REQ-005
- Dependencies: 3548ed3及原FixedSTART/SDK/helper/三类historical journal。
- Baseline state: OneShot无期限参数，HTTP只取回调前scope。
- Observable outcome: callback选定的µ期限实际收紧POST timeout，旧SAM保持，默认unsupported安全不发送。
- End state: additive库能力与受控回归，无AARecorder/native/配置启用。
- Test-first gate: Required — port与AS各有效RED，最小UOE仅编译，fixture/compiler错误不算RED。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java`
- Purpose: 旧SAM与bounded默认安全拒绝反证。
- Symbols: OneShotStart.start(Instant)/原6tests。
- Repository evidence: 原port/test与@FunctionalInterface。
- Dependencies and consumers: port先default UOE编译；真实RED后实现default。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: 旧lambda仍可调用start()；bounded不调用旧method。
- Error and edge behavior: unsupported/null/validdeadline均safe OPERATION_NOT_SENT无旧delegate/cause。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
assertLegacySamRemainsOneAbstractMethod();
assertBoundedDefaultFailsNotSentWithoutDelegatingOrSending();
assertLegacyNoArgumentOperationAndAllExistingRequestObservationFailureContractsRemain();
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java`
- Purpose: additive bounded重载，保旧SAM。
- Symbols: default StartObservation start(Instant callDeadlineAt)。
- Repository evidence: 现OneShotStart唯一abstract start()。
- Dependencies and consumers: File1有效RED；未来AARecorder消费新method。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: 旧method签名和默认行为不变，新默认failclosed。
- Error and edge behavior: 不能default delegate到旧start；无新FailureKind/JSON/API路由。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
@FunctionalInterface interface OneShotStart {
    StartObservation start();
    default StartObservation start(Instant callDeadlineAt) { throw new Failure(OPERATION_NOT_SENT); }
}
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java`
- Purpose: 真实SDK请求期限与同一次性位/生命周期反证。
- Symbols: 新增bounded test matrix；旧26tests正文保持。
- Repository evidence: 原mock JDK bytewire/Clock+nano/close/intr fixtures。
- Dependencies and consumers: port GREEN，AS新bounded override UOE供自身RED。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: GET原窗口、POST最终µ期限；samebit/escapes/Mono/afterACK事实。
- Error and edge behavior: invalid不POST且spend；clockrollback不重置；outerexpiry不抹ACK/误UNKNOWN。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
assertBoundedPostTimeoutIsNarrowedAndReleaseGetKeepsOriginalWindow();
assertNullNonMicroExpiredAndOverScopeOrCredentialBoundAreNotSent();
assertInvalidBoundSpendsSameOneShotAndRetriesOrEscapesAreCancelled();
assertNarrowingUsesOriginalMonotonicOriginDuringWallRollback();
assertAckCapturedBeforeNarrowedDeadlinePostcheckIsNotLostOrRedispatched();
assertBoundedPendingCallKeepsCancelBearerAndCallerResourceOwnership();
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java`
- Purpose: AS覆写新重载，实际HTTP使用最终期限。
- Symbols: anonymous OneShotStart两入口/shared execute；CallScope.narrowCallDeadline/remaining。
- Repository evidence: 原dynamic owner/callbackActive/consumed/publishLock/credential/window/client(scope.remaining)。
- Dependencies and consumers: File3自身有效RED后实现，复用原SDK/helper。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: sameusedbit，先clip后current/client/command/beginOperation；scope原wall+mono。
- Error and edge behavior: deadline非null/µexact/future≤effectivewindow与credential；无reset/extension/重试；oldstart不走nullbounded校验。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
OneShotStart operation = new OneShotStart() {
    public StartObservation start() { return execute(false,null); }
    public StartObservation start(Instant deadline) { return execute(true,deadline); }
    private StartObservation execute(boolean bounded, Instant deadline) {
        requireOwnerActiveNoTransactionAndConsumeSameBitOnce();
        HttpProcessClient client; StartProcessCommand command;
        try { if (bounded) scope.narrowCallDeadline(deadline); current(scope,backend); client=client(backend,scope); command=fixedCommand(request); scope.beginOperation(); }
        catch (RuntimeException notSent) { throw failure(OPERATION_NOT_SENT); }
        return actualStart(client,command); // OUTSIDE pre-send catch; preserve actual remote error mapping.
    }
};
synchronized(publishLock) { remaining(); validateMicroFutureAndNoExtensionAgainstCurrentEffectiveWindow(deadline); narrowedDeadline=deadline; remaining(); }
remaining = originalStartedNanos + duration(originalStartedAt,min(originalWindow,credential,narrowedDeadline)) - currentNano;
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 5 — `MODIFY README.md`
- Purpose: 说明新重载/旧调用兼容/实际HTTP期限边界。
- Symbols: fixedSTART bounded method说明。
- Repository evidence: 原fixedtransport/approval journal段落。
- Dependencies and consumers: 实际源码与日志后更新。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: 新调用必须显式选期限，legacy default安全拒不降级。
- Error and edge behavior: 不宣称远端HTTP timeout撤销业务；ACK/late facts依旧保存。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
documentAdditiveBoundedStartAndLegacySafeRefusal();
documentOriginalWallMonoBudgetAndActualPostTimeout();
documentLateFactAndUnconnectedAaRecorderBoundary();
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 6 — `CREATE docs/egon/plan/2026-11-01-12-50-bounded-start-one-shot-deadline.md`
- Purpose: 锁定7paths/API/RED/手工门禁。
- Symbols: V5/5aliases/17MC/10Rules。
- Repository evidence: 原蓝图/实际port与AS closure/time审查。
- Dependencies and consumers: 先strict/全文/资源/哈希后SourceGO。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: Review原用户exact授权，不伪Accepted。
- Error and edge behavior: 无未决协议；API变更必要且additive。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
lockBaseline3548AndSevenPaths();
requirePortAndAdapterActualRedBeforeBehavior();
requireSeparateRulesManualChecksAndFocusedSemanticCommit();
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

#### File 7 — `CREATE docs/egon/evidence/2026-11-01-bounded-start-one-shot-deadline.md`
- Purpose: 真实日志/错误/结论与提交证据。
- Symbols: 5aliases/17MC/10Rules/REDGREEN/Git。
- Repository evidence: 本plan与实际stdout/source diff。
- Dependencies and consumers: 先PENDING，再实际逐项闭合。
- Why now: tests先于行为，必要方法声明UOE支持编译，各组件有效RED才GREEN。
- Contract/signature changes: §6 additive bounded overload，旧SAM/DTO/接口保持。
- Input/output and state mapping: 不把compile/bytewire当真实IAM/HTTP或数据库验收。
- Error and edge behavior: 保持23SQL与旧callers，无sourcego前实现。
- Standards impact: MC-ARCH-001、MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-JSON-001、MC-TIME-001、MC-SCOPE-001、MC-TEST-001；§4.7/12.5逐项。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；§4.8与本块具体期限/兼容义务。
- Implementation pseudocode:
```java
recordComponentRedFixtureClassificationAndObservedGreen();
recordSourceIdentityLifecycleTimeAndCompatibilityEvidence();
recordExactSevenPathCommitAndHonestRuntimeBoundary();
```
- Verification contribution: TEST-001/002/003，实际结果与限制写evidence。
- After this file: 本贡献完成，不以metadata/compile代替实际行为验收。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；strict从workspace。
- Verification command: §8 canonical五类，focused分选port/AS；全部Java先冻结，Root统一Maven。
- Expected result: 两有效RED后GREEN；旧6/26正文及其余compat回归通过，实际count记录。
- Failure returns to: port默认fallback回File1/2；SDKtimeout/once/lifecycle回File3/4；fixture先修再有效RED；范围漂移返回Root修计划。
- Completion criteria: 7paths/23SQL/旧callers保持，无stub；17MC10Rules/独审/source/cached通过后提交。
- Rollback: binary回退保历史facts/SQL；无schema或runtime启用需回滚，不重派UNKNOWN。
- Commit paths: stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransportTest.java、stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/tool/FixedBpmnStartToolTransport.java、stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransportTest.java、stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiFixedBpmnStartToolTransport.java、README.md、docs/egon/plan/2026-11-01-12-50-bounded-start-one-shot-deadline.md、docs/egon/evidence/2026-11-01-bounded-start-one-shot-deadline.md
- Commit: `feat(agent): bound fixed START one-shot deadlines`

## 8. Test, Validation, and Quality Gates
| ID | Gate | Expected evidence |
| --- | --- | --- |
| TEST-001 | PortTest | default拒绝/不delegate与旧SAM有效RED→GREEN |
| TEST-002 | AS Test | 真实SDK timeout/invalid/once/mono/latefact/close有效RED→GREEN |
| TEST-003 | 五类完整回归/7scope/23SQL/17MC10Rules/strict | actual counts/无operationalstub/兼容证明 |

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
Strict从workspace执行writing-plan validate_plan.py本文件 --strict；资源15md19/18md24及checklist按Step门禁复核。无真实IAM/网络/模型/PG测试或app启动。

## 9. Migration, Compatibility, Rollout, and Rollback
无DDL/配置/权限/注册与新外部API路径，23SQL immutable。additive default保旧SAM与旧start，不支持新能力的legacyhost安全notSent，AAcaller不得fallback。AS借用资源owner不变；timeout不是业务撤销或receipt release。

## 10. Requirement-to-Step Traceability Matrix
| Requirement | Source | Step | Files | Verification | Acceptance |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/11.3/12/13/17 | Step 1 | AR port/Test | TEST-001/002/003 | 旧SAM/source/binary调用兼容，新bounded默认safe拒绝 |
| PLAN-REQ-002 | §7/11.3/12/13/17 | Step 1 | AS transport/Test | TEST-001/002/003 | 最终µ期限真正限制实际POST，原wall+mono不重置 |
| PLAN-REQ-003 | §7/11.3/12/13/17 | Step 1 | AS transport/Test | TEST-001/002/003 | sameonce/callback scope/取消/ACK事实与错误分类不变 |
| PLAN-REQ-004 | §7/11.3/12/13/17 | Step 1 | 全7paths/旧回归 | TEST-001/002/003 | 旧调用/SDK/helper/JSON/23SQL/配置与权限保持 |
| PLAN-REQ-005 | §7/11.3/12/13/17 | Step 1 | tests/README/plan/evidence | TEST-001/002/003 | 分组件有效RED、实证回归/手工规则/单提交 |

## 11. Risks, Blockers, and User Decisions
| ID | Risk/decision | Resolution | Owner | Status |
| --- | --- | --- | --- | --- |
| DEC-001 | 旧SAM兼容与无法收紧HTTP | default新重载安全拒+AS显式clip，绝不默认delegate | Root | Closed |
| DEC-002 | callback只check无法限制阻塞HTTP | clip在client timeout创建前，原mono起点 | Root | Closed |
| RISK-001 | timeout后真实副作用 | 不撤事实/释放receipt/重派，沿原nonce路径 | Root | Tracked |
| RISK-002 | fake bytewire非部署验收 | actual SDK/mockHTTP/受控identity只证明source边界 | 用户 | Tracked |

## 12. Review and Acceptance
原用户exact授权下Review，不伪Accepted。计划合同已闭合，实际evidence先PENDING；Root全文/strict/资源/hash/checklist后SourceGO。
### 12.5 Blocking Manual Check
| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 现AR port/AS技术Adapter结构，Agent generator excluded；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-REUSE-001 | Applicable | PASS | 现scope/publishLock/SDK/helper/oncebit与Clock/nano复用；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-DEP-001 | Applicable | PASS | 现JDK/Spring/SDK，无新增依赖/构建步骤；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-NAME-001 | Applicable | PASS | OneShotStart/CallScope技术语义，未新增实体；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-VALID-001 | Applicable | PASS | bounded µ/future/noextension与owner/active/TX/sharedonce边界；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-MODEL-001 | Applicable | PASS | 无新entity/DTO；原record与request字段保持；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无PO/entity/Mapper转换，原typed SDK复用；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-LOG-001 | Not applicable | N/A | 无新业务日志，safe Failure/no cause；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-BEAN-001 | Not applicable | N/A | 无新Bean/DI/配置，原manual transport对象；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-UTIL-001 | Applicable | PASS | 仅JDK java.time/原bounded实现，无自建通用工具；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-JSON-001 | Applicable | PASS | 原Request/SDK/receipt/JSON字段不变，无ordinal；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TIME-001 | Applicable | PASS | 原entry wall+mono；narroweddeadline在client构造前安装；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-CONFIG-001 | Not applicable | N/A | 无YAML/profile/flags/权限变化；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | 无此面变化 |
| MC-PATTERN-001 | Applicable | PASS | 复用callback Adapter与requestlocal scope，解决延迟确定期限变化点；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-SCOPE-001 | Applicable | PASS | 7paths、23SQL/旧Recorder/native/SDK/helper readonly；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-TEST-001 | Applicable | PASS | port/AS各有效RED，旧6/26兼容与实际SDK bytewire回归；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |
| MC-BLOCKER-001 | Applicable | PASS | exactAPI/实际HTTP期限/兼容闭合，最终门禁后提交；§4/6/逐文件/tests | 计划合同闭合，实施后实际逐项核验 | None |

### 12.6 Final verdict
PASS — Ready for user review
