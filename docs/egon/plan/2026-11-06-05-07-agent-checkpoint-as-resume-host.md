# T4-03 Agent Server checkpoint 恢复宿主实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:07 Asia/Shanghai |
| Updated | 2026-11-06 05:07 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | Two production Java classes, one finite test and three review artifacts |
| Source Requirement | Master §10.2, §17.2 S11 and §19 T4-03 |
| Baseline Revision | fe545cbcfcdbc184b7f475e85b7542db48432a35 |
| Implements Spec | [Host Spec](../spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:06 Asia/Shanghai SHA-256 a087443301f90889f3e17e77e9fd8f55e39c6ed70fca5e077b3eb57bffbc2f07 |
| Effective Specs | [Host Spec](../spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md) |
| Depends On Plans | [Internal dispatcher Plan](2026-11-06-04-23-agent-checkpoint-resume-dispatch.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

为已落地的首输入 checkpoint 恢复增加一个默认未装配的 Agent Server 宿主。受信扩展必须显式导入配置并提供准确命名的恢复绑定。启动时核对两连接共享同一主库，之后在同一个恢复数据源上组装接管、续跑和专用派发。MODEL 复用已有当前授权，RESUME 由独立提供者负责。

## 2. Target Spec and Effective Design

[Host Spec](../spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md) §§4/7/9/14 locks source and exact authority. Existing guarded executor and dispatcher remain responsible for admission, V16 proof, takeover, lease fencing and original invocation.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/9 | Ordinary AS shell has no resume host. Explicit import needs exact qualified binding. |
| REQ-002 | §7/11 | Distinct writable direct logins on same physical primary database, private lock challenge and rollback, V16 verification. |
| REQ-003 | §7/9/13 | Same recovery source, separate RESUME and ordinary MODEL, V15 exclusion and exact enabled ordinary reconciler. |
| REQ-004 | §6.3/7/9 | Dedicated one-thread no-queue AbortPolicy, bounded drain and owned-only shutdown. |
| REQ-005 | §8/14/16 | Typed R1, 56 finite tests, actual clean executables and 28 existing SQL files. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Pin exact Service baseline. Assemble the finite fixture and typed candidate in declared order, verify R1, restore the exact lock contention check for G1/P1, and inspect the artifacts before staging.

### 4.2 Test-first strategy

Use a mock recovery connection on a separately locked database of the same name. Its private lock succeeds despite the held ordinary key. Remove only the rejection of a successful recovery lock for R1. The scoped test fails at the missing startup failure. Restore the guard before G1.

### 4.3 Sequential and parallel boundaries

Run R1, G1 and P1 sequentially in the source reactor. Static review of the same pinned paths and packaged outputs follows. Do not combine other work in this Step.

### 4.4 Commit boundaries

One focused commit after exact scoped stages and final review.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Explicit import and binding | Use §7.0 | AS scans agent.server and uses ordinary candidates | Wrap recovery resources internally | One technical host bean |
| Same primary verifier | Use §7.0 | Existing V16 checks the recovery connection only | Use transaction advisory contention with safe SQL timeout | Separate AR verifier |
| Security, storage and executor | Reuse §7.0 | Existing guarded model, takeover and dispatcher | Avoid parallel implementation | Compose exact existing services |

### 4.6 Change-unit Dependency Matrix

| Change unit | Depends on | Blocks | Order |
| --- | --- | --- | --- |
| Finite context test | Current AS and AR collaborators | R1/G1 | 1 |
| Primary verifier and host | Existing storage, authorization and dispatch | G1/P1 | 2–3 |
| Review artifacts | Exact baseline, Spec and gate results | S1 | 4–6 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Gate |
| --- | --- | --- | --- |
| Architecture and validation | Existing AR JDBC technical infrastructure, AS host composition | Source verifier in AR and sole named host bean outside default AS scan | R1/G1/S1 |
| Concurrency and ownership | Existing dispatcher and host pools | Owned fixed worker, explicit close/drain and borrowed control | G1/P1 |
| Authority | Existing ordinary model and one-time guarded resume | Separate authority roles and exact resume source identity | G1/P1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Durable authority and resume | Existing takeover and guarded executor | EVD-002–004 | Need qualified host assembly | Reuse | No dependency | Step 1 G1/P1 |
| Physical pairing | Existing V16 verifier | EVD-003 | One-source evidence does not pair two connections | New bounded advisory probe plus existing V16 proof | JDK/JDBC only | Step 1 R1/G1 |
| Host lifetime | Existing dispatcher and AS pool convention | EVD-001/005 | Need independent pool | One dedicated host façade with existing close/drain | No dependency | Step 1 G1/P1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–3 | Use verifier and configuration names for infrastructure; retain existing command and acknowledgement names. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–3 | Validate explicit role, exact trusted binding, two primary sessions and existing command before work. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–3 | ResumeBinding and Login are immutable identity values; reuse existing command and acknowledgement. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–3 | Use one named technical @Bean and qualified host resources. No business Bean, logging or Lombok additions. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–3 | Use only existing Spring/JDBC APIs and JDK SQL, SecureRandom and executors. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–3 | The Java host boundary adds no JSON or persistence enum. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–3 | Explicit Spring import supplies activation; no production property, YAML or profile change. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–3 | Composition Root and lifecycle façade reuse the existing one-command dispatcher; physical identity verification stays in AR infrastructure. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–3 | Use java.time.Duration for drain and bounded elapsed SQL statements without changing lease clock. | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–3 | Keep AR storage ownership and AS host wiring. Reuse existing eligible Agent CQE path, with no new DDL or event contract. | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Primary verifier SQL statement timeout and ResumeHost drain | Duration | JDK JDBC query timeout and java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Each probe SQL statement is bounded to two seconds and the host drain waits at most five seconds after close. Host pools supply finite borrow/connect/socket timeout. | JDK elapsed clock and PostgreSQL statement timeout | Existing durable lease expiry and database clock remain independent | Step 1 TEST-002/004 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfigurationTest.java
CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointResumePrimaryVerifier.java
CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java
CREATE docs/egon/spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md
CREATE docs/egon/plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md
CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-as-resume-host.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Use Service fe545cbcfcdbc184b7f475e85b7542db48432a35, existing JDK 25 and offline settings. No production activation. Host role/DML, fixed pools and current external RESUME are separately provisioned; reuse ordinary MODEL only. No live services, PostgreSQL or browser.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Compose the dormant AS checkpoint resume host

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Existing AR dispatcher, guarded executor, V16 storage, AS ordinary primary, current MODEL grant authorizer and reconciler.
- Baseline state: Exact resume can run through the AR operator fixture; AS has no explicit resume host composition.
- Observable outcome: Explicitly imported, exact qualified host composes guarded resume; ordinary AS remains dormant and a different primary is rejected.
- End state: Three Java paths and three review artifacts in one scoped source commit.
- Test-first gate: Required. Exact typed R1 fails for a missing startup failure after omitting the second connection contention guard.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfigurationTest.java`

- Purpose: Finite non-web Spring wiring and two PostgreSQL protocol stubs.
- Symbols: TianjiAgentCheckpointResumeConfigurationTest.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Finite non-web Spring wiring and two PostgreSQL protocol stubs.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Assert dormant shell, named binding, distinct identity, lock contention, rollback, V15, V16 and borrowed control.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertDefaultShellHasNoResumeHost();
assertSeparatePrimaryFailsBeforeHost();
assertBothProbeTransactionsRolledBack();
assertStoppedReconcilerRefusesDispatch();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointResumePrimaryVerifier.java`

- Purpose: Verify fixed host primary and exact login roles without durable writes.
- Symbols: AgentCheckpointResumePrimaryVerifier#verify.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Verify fixed host primary and exact login roles without durable writes.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Reject an ambient transaction, same DataSource, different database, role or lock namespace; always roll back the owned probes.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyBothWritableLoginRoles();
requireDistinctSourcesAndLoginRoles();
requireOrdinaryTryLockAndRecoveryContention();
rollbackAndRestoreBothOwnedConnections();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java`

- Purpose: Compose the optional host and bounded lifecycle outside the default AS scan.
- Symbols: TianjiAgentCheckpointResumeConfiguration, ResumeBinding, ResumeHost.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Compose the optional host and bounded lifecycle outside the default AS scan.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Require existing current MODEL authorizer, keep its ledger/source exact, guard reconciliation and V15, use the same recovery source and independent RESUME instance for claim and execution.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyOrdinaryModelAndReconcilerBindings();
verifyHostAndRecoveryBinding();
assembleExistingTakeoverWorkerAndDispatcher();
closeAndDrainOnlyOwnedHostExecutor();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md`

- Purpose: Pin exact host and authority semantics.
- Symbols: REQ-001 to REQ-005.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Pin exact host and authority semantics.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Separate dormant source delivery and actual approval/operator activation.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifySpecRequirementsAgainstExactSources();
recordExplicitImportAndPrimaryProof();
recordOperatorAcceptanceBoundary();
verifySpecTraceability();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md`

- Purpose: Pin baseline, gate sequence, source scope and Spec digest.
- Symbols: Step 1, R1, G1, P1, S1.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Pin baseline, gate sequence, source scope and Spec digest.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Keep the same reviewed baseline and verify actual report owner and archive.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyDeclaredFilesAndFiniteGate();
verifyTypedRedBeforeGreen();
verifyActualArchiveScopeBeforeCommit();
validatePlanStructureAndPathOrder();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-as-resume-host.md`

- Purpose: Record actual gate outcomes and remaining operator conditions.
- Symbols: R1, G1, P1, S1.
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Agent Runtime, Agent Server and source reviewers.
- Why now: Record actual gate outcomes and remaining operator conditions.
- Contract/signature changes: Internal Java or review artifact only.
- Input/output and state mapping: Treat finite and artifact proof separately from current IAM and PostgreSQL acceptance.
- Error and edge behavior: Reject identity mismatch, unexpected assertion or artifact drift at the owning gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
recordObservedRedAndFiniteProof();
recordOwnedArchiveParity();
recordObservedGateAndRuntimeBoundary();
attachExactReportOwnerAndScope();
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Verify the exact declared source and behavior.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run exact R1/G1/P1/S1 commands in §8.
- Expected result: One intended typed RED assertion and 56 finite tests per G1/P1 with no runtime starts.
- Failure returns to: Current Step 1 test or owning path. Preserve baseline and unrelated work.
- Completion criteria: Exact six paths, strict documents, actual archive and migration parity, and gated operator scope.
- Rollback: Revert the single focused source commit or omit the explicit external import.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfigurationTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointResumePrimaryVerifier.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/recovery/TianjiAgentCheckpointResumeConfiguration.java`, `docs/egon/spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md`, `docs/egon/plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-as-resume-host.md`.
- Commit: feat(agent): compose explicit checkpoint resume host.

## 8. Test, Validation, and Quality Gates

```sh
host_root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
host_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cd "$host_root"
test "$(git rev-parse HEAD)" = "fe545cbcfcdbc184b7f475e85b7542db48432a35"
host_red='TianjiAgentCheckpointResumeConfigurationTest#a_same_named_database_without_shared_lock_is_rejected'
host_core='TianjiAgentCheckpointResumeConfigurationTest,AgentCheckpointResumeDispatcherTest,AgentCheckpointTakeoverServiceTest,AdkCheckpointResumeExecutorTest,TianjiExecutionGrantAuthorizerTest,TianjiAgentRuntimeAutoConfigurationTest'
# Apply the isolated typed R1 candidate before this selector; restore B immediately afterwards.
"$host_runner" "$host_red"
"$host_runner" "$host_core"
STELLAR_VERIFY_GOAL=verify "$host_runner" "$host_core" clean -Ppermission-producer -Dpermission.commit=fe545cbcfcdbc184b7f475e85b7542db48432a35 -Dpermission.buildId=LOCAL_UNCOMMITTED-agent-checkpoint-resume-host
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-06-agent-checkpoint-as-resume-host.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-07-agent-checkpoint-as-resume-host.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | One intended missing startup failure when only the second connection lock-contention guard is bypassed. |
| G1 | 8 new AS host, 10 AR dispatcher, 11 takeover, 6 guarded executor, 13 AS MODEL authorization and 8 AR autoconfiguration tests. Exactly 56 with zero failures, errors or skips. |
| P1 | Repeat the same 56 in clean permission-producer. AS executable contains host class and verifier in nested AR. PS has no AR. Existing YAML, semantic permission manifests and 28 SQL files match. |
| S1 | Strict Spec/Plan and exact six paths; no activated runtime. |

## 9. Migration, Compatibility, Rollout, and Rollback

Existing SQL, config, API, deployment and authority policy are unchanged. Revert the focused commit or remove the explicit host import.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1,3 | G1/P1 |
| REQ-002 | Step 1 | 1–3 | R1/G1/P1 |
| REQ-003 | Step 1 | 1–3 | G1/P1 |
| REQ-004 | Step 1 | 1,3 | G1/P1 |
| REQ-005 | Step 1 | 1–6 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Production RESUME provider, recovery role privileges and fixed pool topology, activation and PostgreSQL/S11 are external operator acceptance gates. V15-enabled recovery needs a separately reviewed compatible role and audit path.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | AR owns technical database proof and AS owns explicit Spring composition | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Existing takeover, dispatcher, execution and MODEL policy are used | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Existing JDK, JDBC and Spring types | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Existing command and acknowledgement and new technical binding names | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Exact login, transaction, capability and dormant import validation | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Technical host references and immutable binding values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No DTO mapper or domain projection | None |
| MC-LOG-001 | Applicable | PASS | §15 | Private binding is redacted and startup SQL details stay out of failure | None |
| MC-BEAN-001 | Applicable | PASS | §7/8 | Single named host bean outside the ordinary default scan | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JDK primitives and existing Spring transaction inspection | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | No JSON or serialization contract | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Duration and finite statement timeout | None |
| MC-CONFIG-001 | Applicable | PASS | §7/16 | Explicit import and byte parity of all existing production configuration | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Composition Root and lifecycle façade reuse the command dispatcher | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact six paths, no production declaration changes, 28 SQL unchanged | None |
| MC-TEST-001 | Applicable | PASS | §14 | Typed R1, owning finite G1 and actual P1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator authority, privileges and runtime activation independently gated | None |

### 12.6 Final verdict

PASS — Ready for user review
