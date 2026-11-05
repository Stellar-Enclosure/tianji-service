# T4-03 checkpoint RESUME 同步签发与密封凭据交接计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:21 Asia/Shanghai |
| Updated | 2026-11-06 06:21 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | Exact current servlet issue and opaque worker handoff |
| Source Requirement | [主计划 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 845346a6cd0f0bf533e86186f5b7bd64d857d271 |
| Implements Spec | [RESUME grant handoff Spec](../spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md) |
| Spec Status | Review |
| Spec Revision | SHA-256 bfddf620585abc5ada3422f934a640e73038b86c7534e786c6e69b08ee0f712c |
| Effective Specs | [RESUME grant handoff Spec](../spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md) |
| Depends On Plans | [Explicit checkpoint host](./2026-11-06-05-07-agent-checkpoint-as-resume-host.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Implement one exact internal synchronous issue and opaque handoff step. Existing worker and dedicated live IAM conditions retain their ownership.

## 2. Target Spec and Effective Design

The Spec fixes the existing read-only preview, current verified caller, exact worker/authority, deterministic intent, bounded slots and stage cleanup.

## 3. Effective Requirements and Acceptance

| Requirement | Outcome |
| --- | --- |
| REQ-001 | Exact cut or committed replay without writing or issuing during preview. |
| REQ-002 | Real existing synchronous issue, exact caller and guarded publication. |
| REQ-003 | Bounded sealed ticket and exact stage cleanup. |
| REQ-004 | Current worker decisions and durable replay retained. |
| REQ-005 | Typed red, finite green, actual archive and scope proof. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock exact baseline and Spec. Establish cleanup mutation, implement precise preview and handoff, verify finite and actual package before commit.

### 4.2 Test-first strategy

R1 removes only completion cleanup from the handoff. Original stage completion must reveal retained sealed evidence or an occupied slot.

### 4.3 Sequential and parallel boundaries

One source step and one commit. Live operator resource review and I/E remain separate.

### 4.4 Commit boundaries

Five Java files and three review artifacts only.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Delivery |
| --- | --- | --- | --- |
| Preview and handoff | Required §7 | EVD-001–005 | Files 1–5 |
| Protected live policy | Separate §18 | Existing catalog path | Operator gate |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies |
| --- | --- | --- | --- |
| Preview, host, issue and handoff | REQ-001–004 | R1/G1 | Existing worker and client |
| Source evidence | REQ-005 | G1/P1/S1 | Exact class artifacts |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Current USER proof | EVD-003 | Same servlet authentication and raw credential lifetime. |
| Opaque grant | EVD-004/005 | Existing contract and worker policy, dedicated purpose. |
| Storage and lifecycle | EVD-001/002 | Existing read-only/claim paths and original stage cleanup. |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Read-only preview | Checkpoint observation | EVD-001 | Exact cut available | Reuse |
| Synchronous issue | HttpExecutionGrantClient | EVD-004 | Existing independent purpose | Reuse |
| Opaque lookup | ResumeTicketSource | EVD-005 | Exact binding and current decisions | Reuse |

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
| Rule 1 | §6.2 | EVD-001–005 | Files 1–8 | Use current handoff and preview names for technical ownership. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–005 | Files 1–8 | Validate exact current verified USER, issuer, command, cut, host and source. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–005 | Files 1–8 | Reuse immutable request, acknowledgement, expectation and bound ticket. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–005 | Files 1–8 | Explicit technical construction, no new Spring bean or business logger. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–005 | Files 1–8 | Use existing JDK, Spring, identity and HTTP transport. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–005 | Files 1–8 | No JSON shape, persisted enum or wire contract change. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | EVD-001–005 | Files 1–8 | No YAML, property or auto-registration. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–005 | Files 1–8 | Use existing host facade and independent ticket-source adapter. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–005 | Files 1–8 | UTC expiry is bounded by current USER proof and Run deadline. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–005 | Files 1–8 | AS owns synchronous identity, AR retains exact checkpoint and CQE ownership. | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Authorized.expiresAt, ResumeRequest.execution.deadlineAt, Issued.expiresAt and Slot.expiresAt | Occurred instant | java.time.Instant | Not persisted | Internal | Not applicable: absolute instant | Not applicable: internal authority | Caller and issued proof strictly current. Slot reserved only before min of owner expiry and Run deadline. Published ticket expiry must fit inside that reservation. | Clock.systemUTC and existing Instant precision | Existing Shoubing deadline wire contract and AR worker lease remain independent | Step 1 TEST-002/003 |
| TIME-002 | Existing ResumeHost shutdown wait | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: internal host | Existing five-second drain, readiness closes immediately | Existing JDK wait boundary | No new scheduler or configured time | Step 1 TEST-003 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoffTest.java
MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java
MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java
MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoff.java
CREATE docs/egon/spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md
CREATE docs/egon/plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md
CREATE docs/egon/evidence/2026-11-06-checkpoint-resume-grant-handoff.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact source baseline `845346a6cd0f0bf533e86186f5b7bd64d857d271`, installed JDK 25 and existing offline reactor. Current independent protected role/resource, live IAM issuance and S11 are independent. No new route, POM, property, role, scope or migration.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Implement explicit RESUME issue and opaque handoff

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing checkpoint runtime, explicit AS host, current RESUME adapter and real issue client.
- Baseline state: Worker can consume independent proof but current servlet issue and exact opaque handoff are absent.
- Observable outcome: Trusted request supplies exact opaque ticket without passing bearer to worker.
- End state: Eight exact files in one source commit.
- Test-first gate: Required. R1 removes only exact completion cleanup and fails the scoped lifecycle test once.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoffTest.java`

- Purpose: Exercise synchronous issue, exact caller and opaque lifetime through the worker stage.
- Symbols: TianjiCheckpointResumeGrantHandoffTest.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Exercise synchronous issue, exact caller and opaque lifetime through the worker stage.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
bindExactVerifiedServletUserAndDedicatedHost();
assertOnlyOpaqueProofReachesWorker();
completeTheWorkerAndRequireReservationRemoved();
assertWrongOrAbsentAuthorizationDoesNotIssue();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`

- Purpose: Cover preview, stale cut and exact committed replay in existing AR test owner.
- Symbols: AgentCheckpointTakeoverServiceTest.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Cover preview, stale cut and exact committed replay in existing AR test owner.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertReadOnlyPreviewBindsCurrentCutAndExpectedRevision();
assertCommittedReplayReturnsNoGrantRequest();
assertChangedCutOrUnrelatedOwnerDoesNotPreview();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`

- Purpose: Preview exact resume request in a bounded read-only owner lookup.
- Symbols: AgentCheckpointTakeoverService.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Preview exact resume request in a bounded read-only owner lookup.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactVerifiedOwnerAndOutsideTransaction();
returnEmptyForExactCommittedReplay();
matchCurrentCutAndRevisionOrRefuse();
recheckExactReplayAfterPreviewRace();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java`

- Purpose: Expose existing takeover preview on the same explicitly constructed host.
- Symbols: TianjiAgentCheckpointResumeConfiguration.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Expose existing takeover preview on the same explicitly constructed host.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
storeTheSameTakeoverAndAuthorityUsedByTheDispatcher();
requireOpenHostAndCurrentReconcilerBeforePreviewOrDispatch();
compareTheAuthorityObjectIdentityForGrantHandoff();
closeAndDrainExistingOwnedPoolExactlyOnce();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoff.java`

- Purpose: Issue on the verified USER request and publish only exact sealed proof.
- Symbols: TianjiCheckpointResumeGrantHandoff.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Issue on the verified USER request and publish only exact sealed proof.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactServletUserResourceAndAuthoritySource();
previewAndReserveTheDerivedOpaqueBinding();
issueSynchronouslyAndRecheckIssuerAndExpiry();
publishSealedTicketAndReleaseOnOriginalStageCompletion();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 6 — `CREATE docs/egon/spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md`

- Purpose: Define the exact handoff design and remaining operator boundary.
- Symbols: 2026-11-06-06-20-checkpoint-resume-grant-handoff.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Define the exact handoff design and remaining operator boundary.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordCheckpointPreviewAndExactReplayConditions();
recordVerifiedUserAndOpaqueProofBoundary();
recordBoundedReservationAndExpiry();
recordIndependentRuntimeAcceptanceConditions();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 7 — `CREATE docs/egon/plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md`

- Purpose: Lock exact test owners, baseline and validation.
- Symbols: 2026-11-06-06-21-checkpoint-resume-grant-handoff.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Lock exact test owners, baseline and validation.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
lockExactReviewedSourceAndSpecDigest();
removeOnlyCompletionCleanupForTypedRed();
restoreTheFullCandidateBeforeScopedFiniteGreen();
inspectActualExecutableClassAndSqlParity();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

#### File 8 — `CREATE docs/egon/evidence/2026-11-06-checkpoint-resume-grant-handoff.md`

- Purpose: Record observed gates and unverified operator state.
- Symbols: 2026-11-06-checkpoint-resume-grant-handoff.
- Repository evidence: EVD-001–005 in the effective Spec and locked baseline.
- Dependencies and consumers: Existing AS, AR, Shoubing client and trusted host.
- Why now: Preserve verified request and opaque async ownership in the existing workflow.
- Contract/signature changes: Internal Java or review evidence only.
- Input/output and state mapping: Record observed gates and unverified operator state.
- Error and edge behavior: Fail closed on stale owner, unexpected path, unavailable proof or mismatched result.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordObservedOwningRedFailureAndFiniteReportCounts();
recordExecutableClassAndConfigurationParity();
verifyExactSourceAndMigrationScopeBeforeCommit();
separateLiveIamAndOperatorAcceptances();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the declared scope and applicable gate.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact R1/G1/P1/S1 in §8.
- Expected result: Exact one R1 assertion failure and 48 owning finite tests in G1/P1.
- Failure returns to: Current Step 1 and exact failing source path.
- Completion criteria: Eight paths, exact artifacts and 28 identical SQL files.
- Rollback: Revert the single focused source commit if unused.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoffTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiCheckpointResumeGrantHandoff.java`, `docs/egon/spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md`, `docs/egon/plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md`, `docs/egon/evidence/2026-11-06-checkpoint-resume-grant-handoff.md`.
- Commit: feat(agent): hand off independently issued checkpoint resume grants.

## 8. Test, Validation, and Quality Gates

```sh
resume_base=845346a6cd0f0bf533e86186f5b7bd64d857d271
resume_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
resume_red='TianjiCheckpointResumeGrantHandoffTest#clears_the_published_ticket_when_dispatch_completes'
resume_core='TianjiCheckpointResumeGrantHandoffTest,AgentCheckpointTakeoverServiceTest,TianjiCheckpointResumeGrantAuthorizerTest,TianjiAgentCheckpointResumeConfigurationTest,AgentCheckpointResumeDispatcherTest'
test "$(git rev-parse --verify HEAD)" = "$resume_base"
# R1 removes only the original stage whenComplete slot release. Restore before G1.
"$resume_runner" "$resume_red"
"$resume_runner" "$resume_core"
STELLAR_VERIFY_GOAL=verify "$resume_runner" "$resume_core" clean -Ppermission-producer -Dpermission.commit="$resume_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-checkpoint-resume-grant-handoff
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-20-checkpoint-resume-grant-handoff.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-21-checkpoint-resume-grant-handoff.md
git diff --check
git diff --cached --check
```

| Gate | Expected |
| --- | --- |
| R1 | One exact expected cleanup assertion `completion must release the opaque RESUME ticket ==> expected: <true> but was: <false>` after removing only `stage.whenComplete((acknowledgement, failure) -> release(expected, slot));`. |
| G1 | Handoff 8, takeover 14, current authorizer 8, explicit host 8, dispatcher 10. Total 48. |
| P1 | Same 48 finite tests. Actual AS handoff and nested AR preview, configuration/semantic PaC and all existing SQL parity. No PG or IT report. |
| S1 | Strict Spec/Plan, eight exact paths and 28 immutable main SQL files. |

## 9. Migration, Compatibility, Rollout, and Rollback

No schema or production configuration change. Remove the one focused commit if unused.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 2–4 | G1 |
| REQ-002 | Step 1 | 1/4/5 | G1 |
| REQ-003 | Step 1 | 1/5 | R1/G1 |
| REQ-004 | Step 1 | 1–5 | G1 |
| REQ-005 | Step 1 | 1–8 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

The actual dedicated protected RESUME endpoint/resource, active scope policy and live operator S11 remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | AS identity and AR checkpoint boundaries stay separate. | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | Existing client, trusted issuer registry, current servlet token carrier and owner snapshot. | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | Existing JDK, Spring and Shoubing dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | Technical preview and handoff names reflect ownership. | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | Verified USER request, exact source and current cut before issue. | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | Existing immutable types carry exact metadata and sealed evidence. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | No domain-to-wire conversion added. | None |
| MC-LOG-001 | Applicable | PASS | §7/8/14 | Opaque state and private bearer are excluded from diagnostics. | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14 | No new component or named bean. | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing Spring transaction checks and JDK synchronized reservation map. | None |
| MC-JSON-001 | Not applicable | N/A | §7/8/14 | Existing Shoubing contract owns serialization. | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | Absolute expiry never exceeds either exact caller or execution deadline. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/14 | Existing production flags and manifests keep byte and semantic parity. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Existing host facade and dedicated handoff adapter. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | Five exact Java paths and three documents. | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Typed cleanup R1 and finite G1/P1 gates. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Dedicated resource and actual IAM/S11 remain operator evidence. | None |

### 12.6 Final verdict

PASS — Ready for user review
