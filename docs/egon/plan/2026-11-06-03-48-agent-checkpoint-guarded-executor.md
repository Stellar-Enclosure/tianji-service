# T4-03 守卫断点恢复执行器实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:48 Asia/Shanghai |
| Updated | 2026-11-06 03:48 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three production Java classes, two finite test classes and three review artifacts |
| Source Requirement | Master §10.2, §17.2 S11, §19 T4-03 |
| Baseline Revision | b64f3aae86082ac4095b65f0aefad493c6f344fd |
| Implements Spec | [Guarded executor Spec](../spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:47 Asia/Shanghai SHA-256 96ee10a8dd5a2a997f4370cf09b37bf46f03f543ab09e8e4f43a2db67a5f149b |
| Effective Specs | [Guarded executor Spec](../spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md) |
| Depends On Plans | [Guarded first full read](2026-11-06-03-43-agent-checkpoint-resume-first-read.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

把赢得接管的一次性结果接入现有 ADK 执行器。重用执行主体及原始 Session 和 invocation，模型准入检查当前 RESUME 和 MODEL，两份固定租约的有效期保持接管时的上限。

## 2. Target Spec and Effective Design

[Guarded executor Spec](../spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md) §§4/7/9/14 governs this source slice. Step 127 owns the first full Session read, while current host composition and live S11 remain independent.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Exact winning claim, same source and authority, one-use consumption and inert ACK replay |
| REQ-002 | §7/9 | Existing guarded Session and exact native invocation, no new input, narrow CONFIG eligibility |
| REQ-003 | §7/9/14 | Current RESUME and MODEL, either denial blocks budget and provider |
| REQ-004 | §6.3/7/11 | Fixed dual lease cap, cancellation and ownership without reacquire or renew |
| REQ-005 | §8/14/16 | Exact RED, 80 owner tests in G1 and P1, actual archives, eight paths and 28 immutable SQL |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock landed Step 127 baseline and exact source digests. Apply Files 1–5 with the typed R1 executor candidate. Run exact RED, restore B, then G1 and P1. Review source and archive scope before staging.

### 4.2 Test-first strategy

Typed A differs only in File 5. It returns the live RESUME decision directly when MODEL is requested, preserving all other code. The exact denied-MODEL fixture must fail with expected SUSPENDED but observed COMPLETED. Other failures, errors and skips invalidate R1.

### 4.3 Sequential and parallel boundaries

R1, restore, G1 and P1 run sequentially on the owning offline reactor. Strict document review is independent. Finite SDK Runner and Fake provider need no application, external model, database, browser or service start.

### 4.4 Commit boundaries

Commit exactly five Java paths and three review artifacts after actual owning P1, S1 and strict validators.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Winning capability | Required §7/9 | EVD-001/002 | Ordinary queue cannot consume the claimed result | Extend internal TakeoverResult with one-use view |
| Claimed body | Reuse §7 | EVD-003 | A second lifecycle risks divergent settlement and finish | Use one postclaim body |
| Current admissions | Required §7/9 | EVD-003/005 | RESUME only omits existing MODEL permission | Require both decisions on exact request |
| Fixed lease control | Required §6.3/7 | EVD-002/005 | Ordinary renewal exceeds the original window | Read owner state and fence, timer uses fixed limit |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Finite native fixture and capability controls | REQ-001–005 | Denied MODEL completes with typed A | 1–2 | Landed Step 127 and Components explicit bridge |
| Guarded handoff and exact callback | REQ-001–003 | One-use capability, native identity and exact auth request | 3–4 | Existing adapters |
| Shared claimed executor | REQ-002–004 | Live conjunction and fixed read-only control | 5 | 1–4, R1 before B |
| Scope and evidence | REQ-005 | Strict docs and observed owner gates | 6–8 | R1/G1/P1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Existing executor | AR AdkAgentRunExecutor | Reuse claimed execution and cleanup with narrow entry branches |
| Capability and Session | Step 127 binding and ledger request | Same primary, current owner and original first full view |
| Native admission | Existing ADK budget plugin, Spring AI model adapter and AgentRunAuthorization | Preserve independent current MODEL gate and exact context |
| Failure | Existing scoped Run transitions and session release | Existing authorization suspension, timeout and owned cancellation |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Consume committed claim | TakeoverResult, Launch and guarded Session | EVD-001/002 | Package launch alone cannot cross sibling package | Opaque one-use Execution | No dependency | G1 |
| Native continuation | AgentFlowSessionRunner | EVD-004 | Ordinary four-argument path introduces content | Explicit six-argument original invocation | Existing Components | G1 |
| Model and result lifecycle | AdkRunBudgetPlugin and ledger | EVD-003/005 | Need exact context and current RESUME | Preserve existing MODEL, add conjunctive guard | No dependency | R1/G1 |
| Fixed cap and cancel | Existing read, Session verification and timer | EVD-002/005 | Ordinary heartbeat renews | Read-only control and earlier cutoff | No dependency | G1/P1 |

### 4.8 User-mandated Java Rule Implementation Matrix

```text
1 类名规范，必须以 java的pojo规范命名。以dao po bo vo dto query command event等结尾
2 每层之间必须被 springboot-validation 校验，复用对象使用分组校验；基于原生和自定义约束注解、@Valid、@Validated 及 ConstraintValidator 扩展实现。ValidationUtils 只承担通用手工校验，不承载全部业务校验；电话号码复用 libphonenumber。
3 Java POJO 默认使用 class，注解为 @Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)，按构造目标选择 @Builder 或 @SuperBuilder；父类状态参与相等性时使用 @EqualsAndHashCode(callSuper = true)。只有不可变 value object 可以使用 record，并豁免 class 的 Lombok 规范；普通实体不能因简单而使用 record。使用 MapStruct、MapStructPlus 和 egon-cola-component-common-core 的 BaseConverter（双向）或 BaseForwardConverter（不可逆投影）进行转换。
4业务类必须使用@Slf4j注解注入log对象。如果业务类被spring管理，必须指定名称，如果是单例的情况下，参考@Service("userService")。如果需要依赖注入，必须@RequiredArgsConstructor进行修饰，不要代码中写。且属性必须被@qualify修饰。
5 工具类只允许使用jdk原生、Apache Commons(commons-lang3、commons-collections4、commons-io、commons-text、commons-codec、commons-beanutils)、Guava。针对Tika按需引入。
6 JSON 使用 Spring Boot Jackson；持久化枚举值使用 @EnumValue，向前端输出的枚举值使用 @JsonValue，禁止 ordinal 作为业务编码。
7 springboot 多环境配置文件，必须保持配置一致，但值不一定一致。
9 复杂业务必须引入设计模式，不允许硬编码
10 日期相关的必须使用java.time下的实体类，不允许使用java.util下的
11 只允许现有三层结构或当前 egon-cola-archetypes 的 DDD 结构，三层结构保持现状。必须尽量复用 egon-cola-components；持久化模块必须使用 Egon COLA MP Starter，DDL 统一由 egon-mp-sdj-ext-starter 分布式管理。业务唯一键必须组合业务列与 deletedAt（数据库 deleted_at），并验证 NULL、租户及重复软删除语义。采用 CQE（Command Query Event）；Event 必须经 egon-cola-component-transactional-outbox-starter 或 MQ 中间件投递。
```

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–5 | Use one claimed execution body and a narrow one-use takeover capability | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–5 | Check exact source, authority, owner, invocation, fixed cap and both live permissions | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–5 | Reuse existing immutable run authorization and scoped lease records | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–5 | Existing executor and direct Session adapter without new beans or logging | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–5 | Reuse existing Agent ledger, ADK bridge and Spring AI model adapter | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–5 | Existing run, Session and permission contracts without wire or JSON changes | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–5 | Existing settings without new profile, configuration or dependency | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–5 | Share postclaim execution and use existing adapters across guarded native boundaries | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–5 | Derive fixed bounds from database Instant and use monotonic scheduling for elapsed waits | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–5 | Existing Agent Runtime and owning Agent primary only | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Original Run deadline, successor lease expiry and resume authorization expiry | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Current time strictly precedes the common lease cap for execution and the exact permit must cover the cap. Distinguish cap expiry from actual Run deadline | Owner primary database clock and stored precision | Original deadline and successor lease rows are reused | Step 1 TEST-002/003/005 |
| TIME-002 | Remaining invocation cap and control interval | Duration | java.time.Duration and RxJava timer | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Resumed timer uses the earlier absolute limit translated once into remaining duration. Periodic control rechecks current DB facts | Nanosecond duration for resumed cutoff, one-second control poll | Ordinary heartbeat and timeout behavior preserved | Step 1 TEST-001/005/006 |


## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkCheckpointResumeExecutorTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-guarded-executor.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service baseline b64f3aae86082ac4095b65f0aefad493c6f344fd includes guarded first full read. Components explicit native Runner baseline is 6d26bf8724e8993b631db68c95b7238d7a93fb8c. Use existing JDK25 offline settings. Preserve 28 immutable production SQL files. No host wiring, IAM roles, permission artifacts, HTTP, YAML, dependency or migration changes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Execute a guarded checkpoint continuation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Landed Step 127 and existing native Runner bridge, model and ledger interfaces.
- Baseline state: A winning launch can bind the guarded Session. The ordinary executor still claims a queued Run.
- Observable outcome: One guarded resume uses the exact original invocation, both live permissions and fixed leases.
- End state: Three main Java classes, two finite test classes and three review artifacts.
- Test-first gate: Required. Typed A installs five Java files with only the MODEL conjunct omitted in File 5.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkCheckpointResumeExecutorTest.java`

- Purpose: Exercise the actual explicit native Runner with a finite Fake provider.
- Symbols: AdkCheckpointResumeExecutorTest.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Exercise the actual explicit native Runner with a finite Fake provider in this dependency order.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Cut ordinary producer after first user input, then run production executor with delegated in-memory Session, scoped test doubles and production Spring AI adapter.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
produceFirstInputWithOrdinaryRunner();
assertOriginalInvocationCompletesWithBothPermissionsAndSettles();
assertDeniedResumeAndDeniedModelDoNotReserveOrStart();
assertAckOnlyReplayHasNoWork();
assertFixedExpiryIsLeaseLoss();
assertPeriodicCancelStopsFakeUpstreamWithoutRenewal();
```

- Verification contribution: R1/G1/P1.
- After this file: Exact typed source candidate for the owner gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`

- Purpose: Prove exact binding and one-use winning capability.
- Symbols: AgentCheckpointTakeoverServiceTest.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Prove exact binding and one-use winning capability in this dependency order.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Use existing takeover fixture with winner and receipt replay. Check exact authority and DataSource identity, transaction boundary and single successful consumption.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyWrongSourceAndWrongAuthorityCannotConsume();
verifyAmbientTransactionCannotConsume();
assertWinningExactConsumptionAndFixedCap();
assertNoSecondExecutionFromWinnerOrReceiptReplay();
```

- Verification contribution: G1/P1.
- After this file: Exact typed source candidate for the owner gate.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`

- Purpose: Expose the scoped winning internal handoff.
- Symbols: AgentCheckpointTakeoverService.TakeoverResult#consumeForExecution and Execution.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Expose the scoped winning internal handoff in this dependency order.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Check exact identity and transaction scope before consuming the existing shared one-use launch. Bind the same-source guarded adapter and retain the exact claimed request.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
rejectAmbientTransactionAndWrongBinding();
var launch = takeLaunchOnce();
return launch.map(this::bindGuardedExecution);
verifyExactClaimedMaterialAndCurrentResumeDecisionAtDatabaseTime();
```

- Verification contribution: G1/P1.
- After this file: Exact typed source candidate for the owner gate.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`

- Purpose: Verify native model callback context on both sides.
- Symbols: AdkRunBudgetPlugin#bindResumeContext and #requireResumeContext.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Verify native model callback context on both sides in this dependency order.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Bind one exact user, Session and invocation tuple. On resumed before/after callbacks compare it before current authorization, reservation or settlement. Preserve ordinary unbound plugin behavior.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
bindExactContextOnce(userId, sessionId, originalInvocationId);
requireResumeContext(callbackContext);
checkExistingModelAuthorizationAndBudget();
requireResumeContext(afterModelContext);
settleExistingReservation();
```

- Verification contribution: G1/P1.
- After this file: Exact typed source candidate for the owner gate.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: Reuse claimed execution and add guarded fixed-cap continuation.
- Symbols: AdkAgentRunExecutor#executeResume and #runClaimed.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Reuse claimed execution and add guarded fixed-cap continuation in this dependency order.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Consume winner, validate persisted claim and CONFIG shape, pass bound Session and original invocation with null input, enforce both live permissions and read-only cap/cancel control.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var execution = consumeExactWinner();
verifyExactClaimAndSupportedRoot(execution);
var decision = checkCurrentResumeAtDatabaseTime();
return decision == ALLOW ? existingModelDecision() : decision;
runOriginalInvocationUsingGuardedSessionAndNoNewInput();
observeCancelAndFixedCapWithoutRenewal();
finishOwnedRunAndReleaseExactSession();
```

- Verification contribution: R1/G1/P1.
- After this file: Exact typed source candidate for the owner gate.

#### File 6 — `CREATE docs/egon/spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md`

- Purpose: Record exact source behavior.
- Symbols: REQ-001–005.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Record exact source behavior in this dependency order.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Document guarded source-only execution, model conjunction, fixed cap and existing result lifecycle.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact capability and explicit Runner contract.
Describe fixed cap and independent current permissions at MODEL admission.
Separate owning finite proof and actual archives from host and live S11.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available in the same commit.

#### File 7 — `CREATE docs/egon/plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md`

- Purpose: Lock ordered application and tests.
- Symbols: Step 1.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Lock ordered application and tests in this dependency order.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Lock Step 127 baseline and typed A/B evidence, exact selectors and owning validations.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Apply the five Java files with A differing only at MODEL conjunction.
Require one intended R1 assertion then restore B.
Stage exact eight paths after G1/P1 and scope review.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available in the same commit.

#### File 8 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-guarded-executor.md`

- Purpose: Capture observed evidence.
- Symbols: R1/G1/P1/S1.
- Repository evidence: EVD-001–005 and the effective Spec.
- Dependencies and consumers: Existing Agent Runtime, owning reactor and source reviewer.
- Why now: Capture observed evidence in this dependency order.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Record actual owning logs and checks. Identify source-only validation boundary.
- Error and edge behavior: Reject absent capability, mismatched identity, lost fence or denied authority at the relevant boundary.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact assertion and both 80-case owner test runs.
Compare actual nested AR and AS/PS artifacts; verify 28 SQL hashes.
Keep host, PostgreSQL and S11 runtime acceptance separate.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available in the same commit.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact offline R1, G1, P1 and S1.
- Expected result: R1 one intended SUSPENDED-versus-COMPLETED assertion. G1 and P1 each pass exact 80 without failure, error or skip.
- Failure returns to: File 5 for authorization/control, File 4 for callback context, File 3 for capability identity, Files 1–2 for a fixture failure.
- Completion criteria: Exact original invocation and independent permissions, fixed cap, shared completion, actual archive parity and eight-path scope.
- Rollback: Revert the single scoped source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkCheckpointResumeExecutorTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `docs/egon/spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md`, `docs/egon/plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-guarded-executor.md`
- Commit: feat(agent): execute guarded checkpoint resume.

## 8. Test, Validation, and Quality Gates

```sh
root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cd "$root"
test "$(git rev-parse HEAD)" = "b64f3aae86082ac4095b65f0aefad493c6f344fd"
red='AdkCheckpointResumeExecutorTest#denied_model_is_not_replaced_by_an_allowed_resume_permit'
core='AdkCheckpointResumeExecutorTest,AgentCheckpointTakeoverServiceTest,AdkCheckpointResumeFirstReadTest,AdkNativeAgentCompletionTest,AdkGovernedProcessLoopTest,AdkCheckpointCaptureTest'
"$runner" "$red"
"$runner" "$core"
STELLAR_VERIFY_GOAL=verify "$runner" "$core" clean -Ppermission-producer -Dpermission.commit=b64f3aae86082ac4095b65f0aefad493c6f344fd -Dpermission.buildId=LOCAL_UNCOMMITTED-guarded-resume-executor
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | Exactly one assertion failure in denied MODEL with allowed RESUME. Expected SUSPENDED, observed COMPLETED. No errors/skips. Restore File 5 from A to B before G1. |
| G1 | 6 resume executor, 11 takeover, 6 first read, 14 native completion, 37 governed process loop and 6 capture cases. Exactly 80 passes. |
| P1 | Repeat exactly 80 in clean permission-producer reactor. Compare actual AS/PS YAML and semantic permission manifests after excluding build identity. Verify three changed production classes in AS nested AR. Confirm no PostgreSQL test report and all 28 SQL hashes unchanged. |
| S1 | Strict validators, exact eight paths, production host/permission scope and staged/source/SQL check. |

## 9. Migration, Compatibility, Rollout, and Rollback

Existing schema, runtime wiring, profiles, YAML, permissions and dependencies are preserved. Revert the one focused commit to roll back.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–3,5 | G1/P1 |
| REQ-002 | Step 1 | 1,3–5 | G1/P1 |
| REQ-003 | Step 1 | 1,3–5 | R1/G1/P1 |
| REQ-004 | Step 1 | 1,3–5 | G1/P1 |
| REQ-005 | Step 1 | 1–8 | R1/G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Host composition and current operator authority, real PostgreSQL privileges and live S11 restart recovery remain separate. This Step's owning source proof uses finite tests and producer archives.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/11 | Agent Runtime internal owner and existing primary | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Existing completion, budget, guarded Session and explicit Runner | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Existing JDK25, Agent Runtime and pinned ADK | None |
| MC-NAME-001 | Applicable | PASS | §7/9/10 | TakeoverResult, scoped Execution and existing decisions | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | One-use winner, exact owner and both live boundaries | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Separate claimed execution data from callback identity guard | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing DTO and no new mapper | None |
| MC-LOG-001 | Applicable | PASS | §15 | Existing generic messages and no raw input or grants | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | Existing executor with no registration change | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse existing Reactor, RxJava, Session and ledger helpers | None |
| MC-JSON-001 | Not applicable | N/A | §10/16 | Existing native and wire formats | None |
| MC-TIME-001 | Applicable | PASS | §6.3/11 | Fixed lease cap, original deadline and separate error classification | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | Existing configuration and manifests | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | One-use capability and shared claimed execution | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact five Java paths and three review artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact MODEL RED and 80 finite cases in each owner run | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Separate host authority, PostgreSQL and runtime acceptance | None |

### 12.6 Final verdict

PASS — Ready for user review
