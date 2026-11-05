# T4-03 断点续跑的宿主派发实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 04:23 Asia/Shanghai |
| Updated | 2026-11-06 04:23 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three production Java classes, one finite test class and three review artifacts |
| Source Requirement | Master §10.2, §17.2 S11, §19 T4-03, implementation Step 132 |
| Baseline Revision | 697d50759a300c1a56ca81b1d411a653ee4a3185 |
| Implements Spec | [Host dispatch Spec](../spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 04:22 Asia/Shanghai SHA-256 df08b473aba738caaccde8bec817d4f2a4c932f0b97cedb02e1474deb9b69427 |
| Effective Specs | [Host dispatch Spec](../spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md) |
| Depends On Plans | [Guarded executor Plan](2026-11-06-03-48-agent-checkpoint-guarded-executor.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在有界宿主任务内部先接管，再用现有受控执行器续跑。使用同一数据源和权限实例，关闭时取消未开始的命令，已开始的工作有界等待。只返回持久 ACK。

## 2. Target Spec and Effective Design

[Host dispatch Spec](../spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md) §§4/7/9/14 governs master Step 132. The landed guarded executor owns eligibility, current MODEL and RESUME verification, and fixed Run/Session leases. Real operator authority and S11 remain independently gated.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/9 | Exact source and authority and bounded AbortPolicy before takeover |
| REQ-002 | §7/9/13 | One worker and one submission, opaque launch, ACK-only replay |
| REQ-003 | §7/9/15 | Preserve ACK after postclaim failure without a second attempt |
| REQ-004 | §6.3/7/9 | Close gates unstarted work, caller waits are independent and started work drains |
| REQ-005 | §8/14/16 | Exact typed R1/R2, 29 finite owner tests each for G1/P1, real archives and 28 unchanged migrations |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the exact service baseline and four Java candidates. Stage each independent typed R1/R2 scratch candidate before switching to B for G1 and P1. Review semantic archive and exact source scope.

### 4.2 Test-first strategy

The R1 candidate removes only constructor source and authority binding checks. The exact test method must report the missing IllegalArgumentException. Restore the guard before R2. In R2 remove only executor.remove(task) from the RejectedExecutionException catch. A stock ThreadPoolExecutor with zero core threads, a bounded queue and a rejecting ThreadFactory must leave an unexpected queue tombstone. Restore candidate B before G1.

### 4.3 Sequential and parallel boundaries

Independent source and package inspections may run after B. Apply the exact R1 and R2 independently first, then G1, then P1 and S1. No other Step enters this commit.

### 4.4 Commit boundaries

Commit the four Java paths and three review documents as one master Step after all gates pass.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Bounded host dispatch | Use §7.0 | Ordinary run dispatcher reclaims and coalesces | Keep existing queued entry intact | One standalone coordinator |
| Binding predicates | Use §7.0 | Existing services own private exact references | Check only after claim | Verify before admission |
| Extra auth or retries | Omit §7.0 | Existing opaque result and current decisions | Reuse guarded worker | No second submission, bean or permission |

### 4.6 Change-unit Dependency Matrix

| Change unit | Depends on | Blocks | Order |
| --- | --- | --- | --- |
| Finite dispatcher test | Existing owner mocks and real pool | R1/R2 and G1 | 1 |
| Binding helpers and dispatch | Current claim and guarded execution | R1/R2, G1 and P1 | 2–4 |
| Review artifacts | Spec, baseline and observed owner gates | S1 and commit | 5–7 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Gate |
| --- | --- | --- | --- |
| Architecture and validation | AR application/run and existing trusted command | Standalone internal coordinator with exact binding, no Spring registration | R1/R2/S1 |
| Concurrency and state | Existing AgentRunDispatcher and host AbortPolicy | One Runnable Command, separate durable ACK and drain | G1/P1 |
| Logging and binding | AgentRunReconciler uses SLF4J and AR has no Lombok dependency | Explicit host-only constructor and exception type only | S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Owner and durable receipt | AgentCheckpointTakeoverService | EVD-002 | Existing claim needs prior local admission | Reuse exact takeOver and one-use result | One identity predicate | Step 1 R1/R2/G1 |
| ADK continuation | AdkAgentRunExecutor | EVD-003 | Exact durable source needed before claim | Reuse executeResume, original Run and invocation | One identity predicate | Step 1 G1/P1 |
| Host lifecycle | AgentRunDispatcher, JDK executor and futures | EVD-001/004 | Ordinary path coalesces by Run | Follow borrowed pool conventions with a separate coordinator | One bounded dispatcher | Step 1 G1/P1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–4 | Dispatcher denotes technical lifecycle, and command/ACK keep established names | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–4 | Existing immutable command validation and owner trust boundary, exact source/authority and transaction guards | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–4 | Reuse actor, command and ACK value objects; the private task contains technical synchronization state | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–4 | Technical host coordinator has no Spring bean, business service or Lombok in AR; uses existing SLF4J facade and explicit constructor | R1/R2/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–4 | Use JDK pool, queue, futures, UUID, and Spring transaction inspection already in AR | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–4 | No serialization, persistence enum, or JSON model changes | R1/R2/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–4 | No new dependency, property, profile or default assembly | R1/R2/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–4 | Command pattern in one Runnable binds takeover and execution to one admitted host task | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–4 | Use java.time.Duration and bounded elapsed future wait without changing database lease clock | R1/R2/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–4 | Keep the established Agent Runtime ownership and exact Agent primary; reuse existing persisted commands and events | R1/R2/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | awaitQuiescence timeout | Duration | java.time.Duration and TimeUnit.NANOSECONDS | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Positive and at most Long.MAX_VALUE nanoseconds. Timeout or interruption returns false, with interruption restored | JDK elapsed timed wait | Existing lease deadlines and database clock unchanged | Step 1 TEST-004 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcherTest.java
MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java
MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcher.java
CREATE docs/egon/spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md
CREATE docs/egon/plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md
CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-resume-dispatch.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

One unchanged Agent Runtime and pinned Maven/ADK baseline. The exact recovery role must have existing lifecycle access and V16 read access. Host composition, production permission issuance, database changes, and live E need separate scope.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Dispatch one expired-lease checkpoint command

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Landed guarded executor and existing takeover/observation storage.
- Baseline state: Current exact takeover and executor exist; no host coordinator combines them.
- Observable outcome: No claim without capacity. One admitted task claims and invokes the existing guarded executor.
- End state: Four Java paths and three review artifacts on the exact service baseline.
- Test-first gate: Required. In typed R1 only the constructor source/authority validation is disabled. In typed R2 only rejected-queue removal is disabled.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcherTest.java`

- Purpose: Exercise ownership, one-task admission, durable ACK, and close races against a finite host pool.
- Symbols: AgentCheckpointResumeDispatcherTest
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Exercise ownership, one-task admission, durable ACK, and close races against a finite host pool.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Mocks bind the exact collaborators, while real pool tasks exercise ordering and shutdown without a database.
- Error and edge behavior: Refused work has no takeover, and a canceled wait does not own native execution.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertWrongBindingsBeforeClaim();
assertNoClaimWhenHostCapacityIsUnavailable();
assertNoQueuedTombstoneAfterWorkerFactoryRejects();
assertSameOccupiedWorkerThenDurableAck();
assertCloseFencesUnstartedCommandsAndDrainsStartedWork();
```

- Verification contribution: R1/R2/G1/P1.
- After this file: Mocks bind the exact collaborators, while real pool tasks exercise ordering and shutdown without a database.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`

- Purpose: Expose a narrow object-identity predicate for explicit composition.
- Symbols: AgentCheckpointTakeoverService#usesSourceAndAuthority
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Expose a narrow object-identity predicate for explicit composition.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Compare the exact DataSource and ResumeAuthorization references already stored by the service.
- Error and edge behavior: A foreign source or authority returns false without checking the database or obtaining a grant.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
boolean usesSourceAndAuthority(DataSource sourceCandidate, ResumeAuthorization authorityCandidate) {
    return source == sourceCandidate
        && authorization == authorityCandidate;
}
```

- Verification contribution: R1/R2/G1/P1.
- After this file: Compare the exact DataSource and ResumeAuthorization references already stored by the service.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: Allow the host to verify that the executor owns the exact durable source.
- Symbols: AdkAgentRunExecutor#usesSessionDataSource
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Allow the host to verify that the executor owns the exact durable source.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Compare the existing sessionDataSource reference and require a nonnull candidate.
- Error and edge behavior: A worker without a durable source cannot be bound to the checkpoint coordinator.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
boolean usesSessionDataSource(DataSource candidate) {
    if (candidate == null) return false;
    return sessionDataSource == candidate;
}
// The constructor already binds a non-null session source to the same ledger.
```

- Verification contribution: R1/R2/G1/P1.
- After this file: Compare the existing sessionDataSource reference and require a nonnull candidate.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcher.java`

- Purpose: Reserve capacity before claim and continue within the same submitted command.
- Symbols: AgentCheckpointResumeDispatcher and private ResumeTask
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Reserve capacity before claim and continue within the same submitted command.
- Contract/signature changes: Internal Java only.
- Input/output and state mapping: Pass the opaque takeover result and the same authority to the worker and return only the durable ACK.
- Error and edge behavior: Reject invalid binding, ambient transactions or capacity. Fence unstarted work on close, track started drain, and retain ACK on postclaim failure.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireBoundedAbortPoolAndExactBinding();
executeOneHostTask(() -> {
    requireStartGate();
    var claimed = takeover.takeOver(actor, command, workerId);
    worker.executeResume(claimed, authority);
    publishDrainBeforeDurableAck(claimed.acknowledgement());
});
removeRejectedTaskIfPoolQueuedBeforeFactoryFailure();
removeOwnedUnstartedTasksBeforeNotifyingClosedWaiters();
```

- Verification contribution: R1/R2/G1/P1.
- After this file: Pass the opaque takeover result and the same authority to the worker and return only the durable ACK.

#### File 5 — `CREATE docs/egon/spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md`

- Purpose: Record exact source requirements, internal contract and review boundaries.
- Symbols: Spec §§4/7/9/14
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Record exact source requirements, internal contract and review boundaries.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Document trusted host, source/authority identity, task lifetime, and one-use ACK replay.
- Error and edge behavior: Separate absent production activation and live acceptance from finite source proof.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record the exact host constructor and command boundary.
Specify same-task takeover and native invocation with one-use capability.
Keep caller cancellation, host shutdown and durable result independent.
```

- Verification contribution: S1.
- After this file: Document trusted host, source/authority identity, task lifetime, and one-use ACK replay.

#### File 6 — `CREATE docs/egon/plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md`

- Purpose: Specify ordered seven-file execution, exact RED mutations, and owner acceptance commands.
- Symbols: Plan Step 1 / master Step 132
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Specify ordered seven-file execution, exact RED mutations, and owner acceptance commands.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Bind the exact Spec hash, baseline and review outputs to the one logical commit.
- Error and edge behavior: Unexpected RED, failed tests, packaging differences or an extra path stop the gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Install all four Java paths with only the typed constructor binding guard disabled for R1.
Restore the guard, then remove only the rejected-queue cleanup for R2.
Restore the final source and run four owner suites plus clean archive verification.
Stage only the seven reviewed source, test and document paths.
```

- Verification contribution: S1.
- After this file: Bind the exact Spec hash, baseline and review outputs to the one logical commit.

#### File 7 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-resume-dispatch.md`

- Purpose: Record actual counts, immutable paths, artifact witness and open acceptance.
- Symbols: R1/R2/G1/P1/S1 evidence
- Repository evidence: EVD-001–005 in the effective Spec, and the locked source baseline.
- Dependencies and consumers: Existing Agent Runtime, isolated owner reactor and source reviewers.
- Why now: Record actual counts, immutable paths, artifact witness and open acceptance.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Capture exact focused assertion, reactor summaries and archive/migration comparisons.
- Error and edge behavior: Distinguish the actual two archive proof from any database, IAM or E claim.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record exact exception mismatch, queue assertion mismatch and finite G1/P1 counts.
Verify actual nested AR dispatcher and unchanged AS/PS configuration and manifests.
Record all 28 migration hashes and the still-pending real operator role and E.
```

- Verification contribution: S1.
- After this file: Capture exact focused assertion, reactor summaries and archive/migration comparisons.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact offline R1/R2, G1, P1 and S1.
- Expected result: R1 missing IllegalArgumentException, R2 queue empty assertion expected <true> but was <false>, and exactly 29 G1/P1 passes each.
- Failure returns to: Files 2–4 for binding, host, or dispatch behavior and File 1 for the exact fixture.
- Completion criteria: One admitted task and original durable ACK, correct close/drain, unchanged artifacts and seven-path scope.
- Rollback: Revert the single scoped source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcherTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/application/run/AgentCheckpointResumeDispatcher.java`, `docs/egon/spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md`, `docs/egon/plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-dispatch.md`
- Commit: feat(agent): dispatch checkpoint resume on bounded host.

## 8. Test, Validation, and Quality Gates

```sh
root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cd "$root"
test "$(git rev-parse HEAD)" = "697d50759a300c1a56ca81b1d411a653ee4a3185"
red1='AgentCheckpointResumeDispatcherTest#rejects_wrong_source_or_authority_before_claiming'
red2='AgentCheckpointResumeDispatcherTest#rejects_and_removes_a_task_when_the_thread_factory_rejects_after_enqueue'
core='AgentCheckpointResumeDispatcherTest,AgentCheckpointTakeoverServiceTest,AdkCheckpointResumeExecutorTest,AgentRunDispatcherTest'
# Apply only the typed constructor-guard mutation for R1.
"$runner" "$red1"
# Restore B. Apply only the typed rejection-queue-removal mutation for R2.
"$runner" "$red2"
# Restore B before G1.
"$runner" "$core"
STELLAR_VERIFY_GOAL=verify "$runner" "$core" clean -Ppermission-producer -Dpermission.commit=697d50759a300c1a56ca81b1d411a653ee4a3185 -Dpermission.buildId=LOCAL_UNCOMMITTED-agent-checkpoint-resume-dispatch
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-04-22-agent-checkpoint-resume-dispatch.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-04-23-agent-checkpoint-resume-dispatch.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | One exact assertion that IllegalArgumentException was expected but no exception was raised. No compilation error. |
| R2 | One exact failed empty-queue assertion, expected <true> but was <false>. No compilation error. |
| G1 | 10 dispatch, 11 takeover, 6 guarded executor and 2 ordinary dispatcher tests. Exactly 29 passes without failures, errors or skips. |
| P1 | Repeat 29 in clean permission producer. Inspect actual AS/PS archives, actual nested AR and semantic manifest/YAML parity; verify the existing 28 SQL files byte-for-byte. |
| S1 | Strict Spec/Plan structure, exact seven paths and source scope with independent runtime acceptance. |

## 9. Migration, Compatibility, Rollout, and Rollback

Existing SQL, default configuration, host registration, endpoints, permissions, and dependency set are preserved. Revert the one scoped commit to roll back.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–4 | R1/R2/G1/P1 |
| REQ-002 | Step 1 | 1–4 | G1/P1 |
| REQ-003 | Step 1 | 1,4 | G1/P1 |
| REQ-004 | Step 1 | 1,4 | G1/P1 |
| REQ-005 | Step 1 | 1–7 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Keep actual operator role, production current RESUME authority, activation and PostgreSQL/S11 acceptance open. This Step proves optional internal source with no activated caller or service launch.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing Agent Runtime owner and standalone technical coordinator | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Existing takeover, owner, authority, ADK and ledger behavior | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Existing JDK, Spring transaction support and SLF4J | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Technical dispatcher and reused immutable command and acknowledgement | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Exact identity, existing validated command, bounded pool, transaction and close guards | None |
| MC-MODEL-001 | Applicable | PASS | §10 | No new business data model; private task lifetime is separate from ACK | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No mapper or DTO projection introduced | None |
| MC-LOG-001 | Applicable | PASS | §15 | Existing AR facade with failure type only, no token, owner, run or command | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | No Spring bean, component scanning, auto-configuration or host registration | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JDK pool/future primitives and existing transaction inspection | None |
| MC-JSON-001 | Not applicable | N/A | §10/16 | No JSON or serialization changes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Bounded Duration and separate unchanged database lease times | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No runtime configuration or profiles modified | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Runnable Command encapsulates one host attempt | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact four Java files, three review documents and existing migrations | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact R1/R2 and scoped owner suites with isolated clean packaging | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Owner role, production authorization and live acceptance remain separate | None |

### 12.6 Final verdict

PASS — Ready for user review
