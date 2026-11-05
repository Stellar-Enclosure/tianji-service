# T4-03 Checkpoint 有界恢复派发 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-04-46-agent-checkpoint-resume-dispatch-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One isolated operator selector reuses the existing fork and adds the real bounded dispatcher |
| Created | 2026-11-06 04:46 Asia/Shanghai |
| Updated | 2026-11-06 04:46 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL fixture and three reviewer documents |
| Change Surface | Operator-only bounded admission, actual dispatch, native completion and ACK-only replay |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | db8f63d418c8dbbd382df8d53539575529ddb7a1 |
| Amends | None |
| Supersedes | None |
| Depends On | [Internal dispatcher §7.3](2026-11-06-04-22-agent-checkpoint-resume-dispatch.md#73-detailed-design), [Direct executor PostgreSQL fixture §7.3](2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md#73-detailed-design) |
| Related Specs | [Guarded executor](2026-11-06-03-47-agent-checkpoint-guarded-executor.md), [Guarded Session first read](2026-11-06-03-42-agent-checkpoint-resume-first-read.md), [Takeover PostgreSQL fixture](2026-11-06-03-15-agent-checkpoint-takeover-postgres.md) |
| Related Plans | [Checkpoint resume dispatch PostgreSQL proof](../plan/2026-11-06-04-47-agent-checkpoint-resume-dispatch-postgres.md) |

## 1. Summary

复用已有双 codec checkpoint 叉进程与协调夹具。在实际 PostgreSQL 恢复路径中验证有界宿主先接纳任务，再接管并续跑。同一命令先在满载时被拒绝，之后完成一次原 invocation，并仅重放原持久 ACK。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentCheckpointCapturePostgresTest#verifyProductCheckpointForks/#provision | Existing V16 capture, owned process death, reconciliation, codec 1/2 and a restricted resume executor identity |
| EVD-002 | AgentCheckpointResumeDispatcher#dispatch/#close, AgentCheckpointTakeoverService#takeOver and AdkAgentRunExecutor#executeResume | Dispatcher admits first and passes one claim to the guarded executor under exact DataSource and RESUME authority identity |
| EVD-003 | Existing Session first-read guard, V9 snapshot lookup and Agent budget settlement | Existing ledger, native Session and MODEL reservation are authoritative persisted witnesses |
| EVD-004 | AgentCheckpointResumeDispatcherTest, AgentCheckpointTakeoverServiceTest, AdkCheckpointResumeExecutorTest and AgentRunDispatcherTest | Existing finite selectors prove scoped behavior without PG or children |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extend one existing AR operator integration fixture. No generated project or example code is in scope.

## 3. Goals and Non-goals

Add one exact PostgreSQL selector for the actual bounded dispatcher. The current source gate runs only existing finite suites and compiles the candidate. Production host binding, current IAM, external model providers and complete S11 are independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR operator test fixture | Affected | EVD-001–004 | New exact dispatcher selector using the existing isolated role | Fixture call chain and evidence | §7, §8, §14, §15, §16 |
| Production executor and storage | Context-only | EVD-002/003 | Reuse existing code, schema and codecs | Scope and privilege context | §11/16 |
| Runtime, IAM and public contracts | Unchanged | EVD-001/004 | Operator test only | Scope gates | §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Isolate the operator fixture | Must | Existing fresh loopback database, three distinct fixture identities, capture gate and new dispatch-only gate, both codecs and owned child bounds | EVD-001/002 |
| REQ-002 | Prove capacity-before-claim, actual native resume and ACK-only replay | Must | In each codec full capacity rejects with zero new receipts and preserved reconciled cut; admitted dispatch completes one native invocation and one settled MODEL reservation; exact ACK replay preserves all witnesses | EVD-002/003 |
| REQ-003 | Keep source and actual execution evidence separate | Must | One test path, three documents, 28 identical SQL files; G0 exact absence and preserved direct selector, 29 finite passes, compiled method/flags, no PG report | EVD-001/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized operator | Controls isolated local database and test logins |
| ACTOR-002 | Agent Runtime fixture | Reuses product fork, reconciler, bounded dispatcher, takeover and resume executor |
| ACTOR-003 | Source reviewer | Runs exact offline finite and class checks |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify actual dispatcher and original native continuation | ACTOR-001/002 | Independent exact I1 selector and fresh database | Reconcile owned process death, prove capacity rejection without claim, admit exact command, observe both decisions and compare persistence before and after ACK replay | Missing permission, drift, extra attempt/call or changed replay evidence fails | Actual evidence only after operator run | REQ-001/002 |
| UC-002 | Prove bounded source change | ACTOR-003 | Exact candidate and baseline | G0/G1/G2/S1 in owning reactor | Skip, unrelated failure or PG report rejects source gate | Compiled isolated fixture and finite evidence | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

Use the existing capture PostgreSQL URL, installed vector version and separately provisioned capture login. The resumed execution role matches `tianji_it_checkpoint_resume_executor_[a-z0-9_]{1,24}`. It is distinct from setup and capture identities. The loopback `tianji_it_checkpoint_` database must be fresh and have no Tianji schema. Credentials stay outside URLs. I1 selects only `admits_and_dispatches_reconciled_checkpoints_for_both_codecs` with `tianji.postgres.checkpoint.resume-dispatch=true`. The old method remains separately selectable with its original gate and pristine schema. Existing app, database, provider and child processes are not started by source gates.

## 6. Project Technology Context

Existing Agent Runtime test infrastructure uses JDK25, JUnit, Spring JDBC, Flyway, PostgreSQL and the pinned native ADK bridge.

### 6.2 User-mandated Java rule compliance

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

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–004 | Existing fixture and exact named operator selector | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Validate loopback database, exact role and current RESUME/MODEL request | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–004 | Reuse native types and private immutable test witness | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–004 | No production Bean, injection or logger changes | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse existing JDK, JUnit, JDBC, Flyway and ADK | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–004 | Read existing JSONB acknowledgements and native codec payloads | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–004 | Test-only opt-in properties; no profile changes | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–004 | Extend existing fork fixture through its bounded post-reconcile callback | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–004 | Existing database-origin permit, owned child and bounded host waits | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Keep existing AR fixture package and scoped Agent primary | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Fixture ResumePermit and original Run/Session deadline | Occurred instant | java.time.Instant and existing JDBC Timestamp | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Permit expires before original Run deadline; reauthorization returns the same bounded expiry | Existing database clock and PostgreSQL microseconds | Existing expiry contract and codec timestamps | Step 1 TEST-003 |
| TIME-002 | @Timeout, host capacity/ack/drain waits and inherited child process bounds | Duration | JUnit Timeout and existing Java Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Existing 150-second method; admission and replay 5s, winner 30s, drain 5s, original child bounds | Existing JUnit/child elapsed clocks | Prior fixture modes and limits preserved | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One method in existing product fork fixture | Extend | REQ-001/002 | Existing direct resume method and finite dispatcher mock test | No actual admitted dispatcher-to-PostgreSQL path | One operator selector; no new fixture class | Use |
| Existing fixture execution role | Reuse | REQ-001 | Existing executor fixture role | Dispatcher uses the same data source and privilege set | No added grants | Use |
| Persisted replay witness | Reuse existing test value | REQ-002 | ACK equality alone | Cannot detect duplicate work or mutation | Existing reads and digest | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 operator fixture | Isolated PostgreSQL only | Local capture/reconcile/capacity/admit/resume/replay | Existing AgentDB tables and native Session | Drift or duplicate work fails I1 |
| G0/G1/G2/S1 | None | Source and bytecode | Owning finite XML only | Wrong class or count fails source gate |

### 7.1 System Architecture Design

Reuse the existing two-codec fork, exact fixture role and guarded executor. Construct the real AgentCheckpointResumeDispatcher with a one-thread AbortPolicy pool, one-slot queue, exact execution DataSource and the same test-local RESUME authority instance as takeover.

### 7.2 High-Level Design

For each reconciled fixture, expire only its old Session lease and saturate the real host executor with one occupied thread and one queued fixture task. The real dispatcher must reject the exact command without invoking RESUME or creating a receipt. Drain fixture tasks, submit the original command, observe native completion and replay the exact command once.

### 7.3 Detailed Design

Add `admits_and_dispatches_reconciled_checkpoints_for_both_codecs` under the existing class capture gate and new `tianji.postgres.checkpoint.resume-dispatch`. Share the original role provisioner, column grants and V16 SELECT-only checks. The original `resumes_the_claimed_checkpoint_to_completion_and_replays_only_the_acknowledgement` keeps its gate, scoped pre-MODEL takeover witness, consumed-handle and direct ACK-only checks. Both modes use the same fixture role, fork, authority, Fake model and persisted completion helper.

In dispatch mode require typed capacity error and no RESUME receipt, unchanged reconciled cut and zero RESUME decisions. Release the blocker in finally and require both occupied and queued fixture futures to complete within bounds. The exact same command then enters the real dispatcher. Before the one MODEL authorization capture the durable receipt digest, while the second current RESUME and one MODEL decision still verify the exact Run and attempt. After the acknowledgement future resolves, require the completed Run and original native prefix/invocation, both takeover transitions, the same codec, one Fake stream, settled MODEL reservation and released leases. A committed ACK alone cannot satisfy completion.

Dispatch the same original command and revision again and require the exact historical ACK. Close and drain the dispatcher, confirming the borrowed pool is still host-owned. The fixture always requests immediate host shutdown in its own cleanup without adding an unbounded executor close wait. Compare the complete Run, result, events, native rows, budget, checkpoint and receipt with the pre-replay witnesses and verify the counters did not grow.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-04-46-agent-checkpoint-resume-dispatch-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-04-47-agent-checkpoint-resume-dispatch-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-dispatch-postgres.md` |

## 9. Interface Definitions

The operator-only Java test adds one method and a dedicated JVM gate. It retains the existing capture and role properties. Existing product executor, takeover, DTO and permission interfaces are unchanged.

## 10. POJO and Data Model Design

Reuse existing Run, receipt, codec and event types. Private ResumeExecutionWitness contains immutable copies of persisted event and native row lists, result, Run and budget digest. No production mapper or schema changes.

## 11. Database Design

Context-only. Use the existing guarded fresh database, V16 checkpoint reader and bounded resume executor role. The fixture calls the original grants, reads the primary receipt digest and retains the exact completion and replay witness queries. All 28 repository SQL migrations remain byte-identical.

## 12. Frontend Page Design

Context-only. This existing AR test fixture has no frontend or public route changes.

## 13. Design Patterns and Architecture Principles

Compose the existing product fork callback with the actual bounded dispatcher, takeover and guarded executor. Check persisted rows independently of in-memory fixture counters.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean offline G1 runs four existing finite selectors and requires 29 owning tests with no failures, errors or skips. |
| TEST-002 | G0 proves exact method absence. G2 inspects actual method, both opt-ins, role property and preserved private child entry with no PG report. S1 checks four paths and 28 SQL. |
| TEST-003 | Exact I1 proves bounded capacity denial and unchanged reconciled cut, actual native completion and ACK-only replay in both codecs with the same scope. The direct selector still checks consumed-handle behavior. |

## 15. Non-functional and Cross-cutting Design

Retain existing bounded child lifetime and cleanup. The new operator method uses the existing 150-second timeout pattern. Review output identifies flags, test counts and scope without publishing credentials, lease tokens or raw payloads.

## 16. Compatibility, Migration, Rollout, and Rollback

Keep prior capture, fork, observer, native resume and takeover modes. Existing native continuation assertions are shared while the original no-execution evidence remains on the native-only path. No production code, permissions, configuration, dependencies or SQL changes. Revert one test-only source commit to roll back.

## 17. Alternatives and Decisions

The existing direct-resume I fixture establishes the guarded executor contract, and Step 132 finite tests establish dispatcher admission with mocks. Reuse the same fork and persisted witness to cover their composition with one extra opt-in method.

## 18. Risks and Open Questions

Actual PostgreSQL identity and SQL permissions, forced process death, native continuation, budget settlement and replay need an authorized I1 run. Test-local decisions and Fake model do not establish production IAM, provider behavior, deployment or full S11.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/11/15 | TEST-002/003 |
| REQ-002 | UC-001 | §7/10/14 | TEST-003 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing product fork, actual dispatcher, takeover, guarded executor and codecs | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test and runtime dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | One named exact guarded integration method | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Disposable primary, distinct role, exact owner/current decisions | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable witness retains exact artifacts for replay comparison | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing native codecs and result data, no cross-layer mapper | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger, raw payload or credentials in reviewer evidence | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Explicit fixture construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing fixed fixture helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Verify existing ACK/status and codec envelopes | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database clock permit and bounded operator test | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test JVM properties only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing fork callback composes real bounded host, dispatcher, takeover and executor | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Production authority and actual operator results pending | None |

### 20.6 Final verdict

PASS — Ready for user review
