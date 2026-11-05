# T4-03 Checkpoint 接管续跑 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One guarded operator test extends the established product fork and calls existing takeover and resume executor |
| Created | 2026-11-06 03:55 Asia/Shanghai |
| Updated | 2026-11-06 03:55 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL fixture and three reviewer documents |
| Change Surface | Dedicated operator-only claimed resume, settlement and replay proof |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 7b8faefa38cd95e7807c4b92e0077991800b77c7 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [Guarded executor](2026-11-06-03-47-agent-checkpoint-guarded-executor.md), [Guarded Session first read](2026-11-06-03-42-agent-checkpoint-resume-first-read.md), [Takeover PostgreSQL fixture](2026-11-06-03-15-agent-checkpoint-takeover-postgres.md) |
| Related Plans | [Claimed resume PostgreSQL proof](../plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md) |

## 1. Summary

在已有产品 checkpoint 强制退出与协调夹具中加入独立的续跑验收方法。以同一 Run、attempt 和原 invocation 进入受控执行器，核对当前 RESUME 与 MODEL 决策、原始事件前缀、一次 Fake 流、预算结算、正常完成以及只读 ACK 重放。真实 PostgreSQL 留给操作者。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentCheckpointCapturePostgresTest#verifyProductCheckpointForks/#provision | Existing V16 capture, controlled process death, reconciliation, bounded two codec forks and restricted fixture identities |
| EVD-002 | AgentCheckpointTakeoverService#takeOver and AdkAgentRunExecutor#executeResume | Existing claim returns a one-use capability, and the executor consumes it under the same trusted RESUME authority |
| EVD-003 | Existing Session first-read guard, V9 snapshot lookup and Agent budget settlement | Existing ledger, native Session and MODEL reservation are authoritative persisted witnesses |
| EVD-004 | AdkCheckpointResumeExecutorTest, AdkCheckpointResumeFirstReadTest, AgentCheckpointTakeoverServiceTest and AgentCheckpointForkHandoffTest | Existing finite selectors prove scoped behavior without PG or children |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extend one existing AR operator integration fixture. No generated project or example code is in scope.

## 3. Goals and Non-goals

Create one independently selected PostgreSQL integration fixture for actual claimed continuation. Current source gates compile the test and run finite suites. Production IAM, a real model provider and application binding remain independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR operator test fixture | Affected | EVD-001–004 | New explicit isolated role and exact resume acceptance | Fixture call chain and evidence | §7, §8, §14, §15, §16 |
| Production executor and storage | Context-only | EVD-002/003 | Reuse existing code, schema and codecs | Scope and privilege context | §11/16 |
| Runtime, IAM and public contracts | Unchanged | EVD-001/004 | Operator test only | Scope gates | §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Isolate the operator fixture | Must | Exact loopback disposable database, three distinct identities, class and method flags, limited role and owned child bounds | EVD-001/002 |
| REQ-002 | Prove completed native resume and ACK-only replay | Must | Both codecs preserve one input, original invocation and raw prefix; fresh RESUME and MODEL before one Fake stream; normal output, released leases and one settled MODEL reservation; repeat and replay preserve all witnesses | EVD-002/003 |
| REQ-003 | Keep source and actual execution evidence separate | Must | One test path, three documents, 28 identical SQL files; G0 exact absence, 28 finite passes, compiled method/flags, no PG report | EVD-001/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized operator | Controls isolated local database and test logins |
| ACTOR-002 | Agent Runtime fixture | Reuses product fork, reconciler, takeover and resume executor |
| ACTOR-003 | Source reviewer | Runs exact offline finite and class checks |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify claimed original invocation end to end | ACTOR-001/002 | Independent exact I1 selector and fresh database | Reconcile controlled process death, claim, execute with both decisions, compare persisted completion and replay | Missing permission, drift, extra attempt/call or changed replay evidence fails | Actual evidence only after operator run | REQ-001/002 |
| UC-002 | Prove bounded source change | ACTOR-003 | Exact candidate and baseline | G0/G1/G2/S1 in owning reactor | Skip, unrelated failure or PG report rejects source gate | Compiled isolated fixture and finite evidence | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

Use the existing capture PostgreSQL URL, installed vector version and separately provisioned capture login. The resumed execution role matches `tianji_it_checkpoint_resume_executor_[a-z0-9_]{1,24}`. It is distinct from setup and capture identities. The loopback `tianji_it_checkpoint_` database must be fresh and have no Tianji schema. Credentials stay outside URLs. I1 selects only the exact new method because each fixture method requires a pristine schema. Existing app, database, provider and child processes are not started by source gates.

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
| Rule 10 | Yes | EVD-001–004 | Database-origin Instant permit and unchanged bounded child duration | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Keep existing AR fixture package and scoped Agent primary | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Fixture ResumePermit and original Run/Session deadline | Occurred instant | java.time.Instant and existing JDBC Timestamp | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Permit expires before original Run deadline; reauthorization returns the same bounded expiry | Existing database clock and PostgreSQL microseconds | Existing expiry contract and codec timestamps | Step 1 TEST-003 |
| TIME-002 | @Timeout and inherited child process bounds | Duration | JUnit Timeout and existing Java Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | One 150-second operator method and existing bounded owned children | Existing JUnit/child elapsed clocks | Prior fixture modes and limits preserved | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One method in existing product fork fixture | Extend | REQ-001/002 | Existing takeover and native-only methods | No integrated guarded execution and settled-budget replay | One operator selector; no new fixture class | Use |
| Distinct fixture execution role | Reuse/extend | REQ-001 | Existing reader and claim grants | Resume needs existing Session, event, result and budget columns | Fixture-only column DML | Use |
| Persisted replay witness | Add private test value | REQ-002 | ACK equality alone | Cannot detect duplicate work or mutation | Existing reads and digest | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 operator fixture | Isolated PostgreSQL only | Local capture/reconcile/claim/resume/replay | Existing AgentDB tables and native Session | Drift or duplicate work fails I1 |
| G0/G1/G2/S1 | None | Source and bytecode | Owning finite XML only | Wrong class or count fails source gate |

### 7.1 System Architecture Design

Keep the existing Agent Runtime PostgreSQL fixture and its owned child processes. Supply a distinct test-local execution identity and call the actual takeover and resume executor; only model and authority responses are fixture-controlled.

### 7.2 High-Level Design

Reuse the existing forced-death capture and reconciliation fork for both codecs. The bounded callback expires the exact old Session lease, claims once and drives the guarded executor using its winning capability. Compare persisted evidence before and after consumed-handle and ACK-only replay calls.

### 7.3 Detailed Design

Activate `resumes_the_claimed_checkpoint_to_completion_and_replays_only_the_acknowledgement` only with the capture and dedicated resume-executor gates. Use provision(role), assertCheckpointReaderPrivileges, grantCheckpointTakeover and the exact execution column grants. Check V16 SELECT-only and existing Tool/scope read boundaries.

In each RECONCILE callback, expire the exact reconciled Session lease and retain the native prefix and checkpoint digest. Bind the first RESUME decision to the verified actor, exact command, execution snapshot and cut; fixture authority revalidates the suspended Run before claim. Claim returns the same Run/attempt ACK and a winning capability. Reuse the same authority instance in executeResume. Require a second current exact RESUME decision on the claimed Run and one current MODEL decision before the Fake stream. Verify one original user input in the prompt, one Fake stream and zero sync model calls.

Completion retains the same Run and attempt, keeps the original raw first native event and V16 checkpoint, appends a suffix under the same invocation and fixture codec, emits ordinary Agent output and terminal status, releases both leases and settles exactly one MODEL reservation at the claimed epoch. No Tool invocation or action intent is added. Consume the winning capability only once. Repeating it or executing exact historical ACK returns empty execution. Compare the full completed Run, result, ledger events, native rows, budget digest, checkpoint and receipt digest after replay. Verify decision and Fake counts are unchanged.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-executor-postgres.md` |

## 9. Interface Definitions

The operator-only Java test adds one method and a dedicated JVM gate and role property. Existing product executor, takeover, DTO and permission interfaces are unchanged.

## 10. POJO and Data Model Design

Reuse existing Run, receipt, codec and event types. Private ResumeExecutionWitness contains immutable copies of persisted event and native row lists, result, Run and budget digest. No production mapper or schema changes.

## 11. Database Design

Context-only. The disposable setup keeps the existing V1–V15 then V16 fixture and exact PostgreSQL vector version. The distinct executor login reuses checkpoint reader and takeover grants and adds V9 context snapshot SELECT, explicit Run result/budget UPDATE columns, Agent event author/branch/tool_call_id INSERT columns, Session state/codec/watermark/updated_at UPDATE columns, native Session event INSERT columns and MODEL reservation INSERT/settlement UPDATE columns. V16 is SELECT-only. Tool access is column SELECT-only and scope state is read-only. The inherited takeover role grants Session revision UPDATE for row locking; PostgreSQL requires UPDATE on at least one locked table column in addition to SELECT. [PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)

## 12. Frontend Page Design

Context-only. This existing AR test fixture has no frontend or public route changes.

## 13. Design Patterns and Architecture Principles

Compose the existing product fork callback with the actual guarded claim and execution services. Check persisted rows independently of in-memory fixture counters.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean offline G1 runs four existing finite selectors and requires 28 owning tests with no failures, errors or skips. |
| TEST-002 | G0 proves exact method absence. G2 inspects actual method, both opt-ins, role property and preserved private child entry with no PG report. S1 checks four paths and 28 SQL. |
| TEST-003 | Exact operator I1 runs both codecs with scoped identity, completed original invocation, one settled MODEL reservation and unchanged witnesses on consumed capability and ACK replay. |

## 15. Non-functional and Cross-cutting Design

Retain existing bounded child lifetime and cleanup. The new operator method uses the existing 150-second timeout pattern. Review output identifies flags, test counts and scope without publishing credentials, lease tokens or raw payloads.

## 16. Compatibility, Migration, Rollout, and Rollback

Keep prior capture, fork, observer, native resume and takeover modes. Existing native continuation assertions are shared while the original no-execution evidence remains on the native-only path. No production code, permissions, configuration, dependencies or SQL changes. Revert one test-only source commit to roll back.

## 17. Alternatives and Decisions

The takeover-only and native-only fixtures verify separate boundaries. Reuse their underlying product fork and native persistence assertions and add the claimed resume execution witness in the same test class.

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
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing product fork, takeover, guarded executor and codecs | None |
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
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing fork callback composes actual takeover and executor | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Production authority and actual operator results pending | None |

### 20.6 Final verdict

PASS — Ready for user review
