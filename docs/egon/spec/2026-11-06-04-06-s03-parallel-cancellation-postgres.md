# S03 并行取消 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-04-06-s03-parallel-cancellation-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One guarded operator test extends the existing CONFIG executor and PostgreSQL fixture |
| Created | 2026-11-06 04:06 Asia/Shanghai |
| Updated | 2026-11-06 04:06 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL fixture and three reviewer documents |
| Change Surface | Dedicated operator-only CONFIG Parallel cancellation and durable branch/budget proof |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §6.2、§17.2 S03、§19 T1-02/T3-09/T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 2cfb744b9b481d8500529c4d9dd2d230dc630a0d |
| Amends | None |
| Supersedes | None |
| Depends On | [Static Parallel completion §7.3](2026-11-06-03-41-s03-static-parallel-completion.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Parallel cancellation PostgreSQL proof](../plan/2026-11-06-04-07-s03-parallel-cancellation-postgres.md) |

## 1. Summary

在现有 PostgreSQL 执行器夹具中新增精确 S03 用例。实际 CONFIG Parallel 的 planner 完成后，等待首条分支输出持久化，确认父 Run 仍为 RUNNING、writer 仍处于订阅中，再用原所有者和准确 revision 取消。核对 writer 释放、持久取消终态、已完成输出以及各一笔 SETTLED 与 UNKNOWN 的 MODEL 预留。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AdkAgentRunExecutorPostgresTest#parallel_root_returns_both_outputs_without_inventing_a_single_final_text and #parallel_branches_wait_for_budget_capacity_instead_of_exceeding_it | Existing actual CONFIG Parallel, Fake model and budget fixture |
| EVD-002 | AdkAgentRunExecutorPostgresTest#persisted_cancellation_stops_the_active_model_and_records_a_terminal_result; AgentLedgerService#cancel | Existing owner/revision cancellation and persisted UNKNOWN settlement for interrupted work |
| EVD-003 | AdkAgentRunExecutor and AdkEventProjector; PostgresTestDatabase#dataSource | Existing Runner, ordered events and loopback disposable PostgreSQL fixture |
| EVD-004 | AdkEventProjectorTest, AdkNativeAgentCompletionTest and AdkExecutionTransactionBoundaryTest | Existing finite owner selectors for projection, native completion and transaction boundaries |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extend one existing AR operator fixture. This Step has no generated project or example scope.

## 3. Goals and Non-goals

Add one independently selected PostgreSQL acceptance method for owner cancellation after the first completed branch. Source review compiles the method and runs finite suites. Production IAM, a live model provider, deployment and the remaining S03 runtime scenarios are independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR operator test fixture | Affected | EVD-001–004 | One exact isolated CONFIG Parallel cancellation method | Bounded call chain and persisted witnesses | §7, §8, §14, §15, §16 |
| Production executor and storage | Context-only | EVD-002/003 | Existing owner cancellation, event, result and budget contracts | Sequence and isolation | §11/16 |
| Runtime, IAM and public contracts | Unchanged | EVD-001/004 | Existing fixture inputs and product behavior | Source/operator separation | §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Prove first completed branch remains nonterminal | Must | Real CONFIG Parallel starts planner and writer; committed planner AGENT_OUTPUT remains visible while writer is subscribed and parent Run is RUNNING | EVD-001/003 |
| REQ-002 | Prove owner cancellation disposes the sibling and persists evidence | Must | Exact owner/revision accepted, writer disposed, durable CANCELLED result and status, original planner output retained, exactly one SETTLED and one UNKNOWN scoped MODEL reservation | EVD-002/003 |
| REQ-003 | Separate source verification and actual operator evidence | Must | One test and three documents, 28 unchanged SQL files, G0 exact absence, 20 finite tests, actual compiled selector/guard, no PostgreSQL report | EVD-003/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized operator | Provisions one isolated fresh local database |
| ACTOR-002 | Agent Runtime fixture | Uses the original trusted Run owner, actual CONFIG executor and Fake model |
| ACTOR-003 | Source reviewer | Runs offline finite selectors and inspects class output |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify durable partial branch output and owner cancellation | ACTOR-001/002 | Existing fixture database guard and exact I1 method | Await writer subscription and committed planner output, read current revision, cancel, verify disposal and durable witnesses | Missing output, prior terminal status, stale revision, active sibling, wrong result or reservation states fails I1 | First output retained and cancelled Run is observable | REQ-001/002 |
| UC-002 | Verify scoped source delivery | ACTOR-003 | Exact source and baseline | G0/G1/G2/S1 in owning reactor | Wrong count, method, guard, scope or unexpected operator report fails | Compiled test and finite proof | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

The existing class gate is `tianji.postgres.test.url`. The helper allows only `jdbc:postgresql://127.0.0.1:<port>/tianji_it_<suffix>` and requires a fresh database without the Tianji schema. It migrates and validates to V9 with clean disabled. Use existing local JDBC authentication and keep credentials out of the URL and evidence. Source validation omits the property. I1 uses only the exact method. No new fixture property or role is required.

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
| Rule 2 | Yes | EVD-001–004 | Reuse validated CONFIG definition, existing owner and guarded disposable database | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | No | EVD-001–004 | Reuse immutable framework and repository test values | §7/8/14 | TEST-001–003 | N/A |
| Rule 4 | No | EVD-001–004 | No production Bean, injection or logger changes | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse JDK concurrency, JUnit, Reactor, Awaitility and Spring JDBC | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | No | EVD-001–004 | Read existing Agent output and result DTOs | §7/8/14 | TEST-001–003 | N/A |
| Rule 7 | No | EVD-001–004 | Reuse the existing JVM test property | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–004 | Reuse CONFIG workflow Strategy and native Runner | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–004 | Explicit bounded waits and existing Duration policy | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Keep existing AR fixture package and scoped Agent primary | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required



| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Fixture policy, Awaitility, subscription latches and Future | Duration | java.time.Duration and JDK TimeUnit | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Observed persisted planner output before owner cancellation, bounded waits, test class @Timeout(90) | Existing fixture time limits and clocks | Existing PostgreSQL harness policies | Step 1 TEST-002/003 |



## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Exact method in existing AR PostgreSQL fixture | Extend | REQ-001/002 | Parallel happy path and separate single-model cancellation | No persisted first output/sibling cancellation conjunction | One bounded case, no new class | Use |
| Scoped persisted budget assertion | Reuse | REQ-002 | Current result alone | Result cannot distinguish finished and disposed MODEL reservations | One tenant/run/kind query | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 operator fixture | Isolated PostgreSQL only | Trusted owner reads and cancels current Run | Branch output, cancelled result and two reservation states | Ordering, incomplete disposal or wrong persisted state fails |
| G0/G1/G2/S1 | None | Source and bytecode | Owning finite XML only | Wrong scope, selector or count fails |

### 7.1 System Architecture Design

Reuse the Agent Runtime PostgreSQL fixture, its CONFIG workflow strategy and actual native Runner, the trusted owner and Agent ledger. Substitute fixture model responses for planner and writer.

### 7.2 High-Level Design

Give planner a deterministic completed response and writer a never-completing stream with subscription and disposal witnesses. The owner cancels after both the writer subscription and the planner output are observed, using a fresh persisted Run revision. Use the 25-second total deadline, 15-second MODEL idle policy and existing terminal controls.

### 7.3 Detailed Design

Activate `parallel_cancellation_preserves_the_first_branch_output_and_disposes_the_sibling` through the existing class guard. Publish the existing CONFIG Parallel fixture and start with the verified owner. Route planner to its existing finite Fake text stream and writer to a never stream. Execute through the actual queued CONFIG executor. Await writer subscription, then use ledger events scoped to the Run to observe the committed planner AGENT_OUTPUT with exact text PLAN done and verify that the Run is still RUNNING and has no terminal result.

Use the same owner and current exact revision in `CancelAgentRunCommand`. Require the command to be accepted, the executor to return CANCELLED and the writer subscription to receive cancellation. Re-read the durable result with CANCELLED status and empty aggregate output. Require planner to be the only AGENT_OUTPUT author and the last event to be RUN_STATUS_CHANGED/CANCELLED. Query MODEL reservations under the exact tenant and Run. Require one SETTLED for completed work and one UNKNOWN for disposed work. Bound subscription and Future waits with the existing fixture timeout discipline.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkAgentRunExecutorPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-04-06-s03-parallel-cancellation-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-04-07-s03-parallel-cancellation-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s03-parallel-cancellation-postgres.md` |

## 9. Interface Definitions

The only new executable entry is the test method. The existing JVM URL property and owner cancellation command are reused.

## 10. POJO and Data Model Design

Reuse existing Run, event and result DTOs, scoped reservation states and fixture-local latches. No production mapper or data model is introduced.

## 11. Database Design

Context-only. PostgresTestDatabase prepares one fresh loopback database using existing V1–V9 migrations. The test observes authoritative Agent output and result through AgentLedgerService and reads only MODEL reservation states scoped to its tenant and Run. All 28 repository migration files are byte-preserved.

## 12. Frontend Page Design

Context-only. This existing AR test fixture has no frontend or public route changes.

## 13. Design Patterns and Architecture Principles

Use the existing CONFIG Workflow Strategy and native Runner. Compare persisted witnesses independently of the Fake subscription signals. Use direct fixture sequencing for the single cancellation scenario.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean offline G1 runs AdkEventProjectorTest, AdkNativeAgentCompletionTest and AdkExecutionTransactionBoundaryTest. Require exactly 2, 17 and 1 passing owning cases. |
| TEST-002 | G0 checks exact baseline method absence. G2 inspects compiled method, JUnit test marker and existing loopback fixture gate, with no PostgreSQL XML. S1 verifies the four paths and immutable SQL. |
| TEST-003 | Exact operator I1 verifies real CONFIG Parallel ordering, owner cancellation, writer disposal, durable cancellation/result, first output retention and SETTLED plus UNKNOWN MODEL reservations. |

## 15. Non-functional and Cross-cutting Design

Reuse the class 90-second guard and bounded test awaits. Retain the fixture thread pool cleanup. Evidence records method/flag/counts without publishing credentials or raw output content.

## 16. Compatibility, Migration, Rollout, and Rollback

One operator test extends the existing test class. Revert one focused test-only commit to remove the fixture.

## 17. Alternatives and Decisions

Existing parallel success and single-model cancellation cases remain useful independent checks. The new single method exercises their overlap against one Run, using the existing Fake and PostgreSQL helpers.

## 18. Risks and Open Questions

Actual PostgreSQL ordering, owner cancellation, sibling disposal, durable result and reservation settlement require an authorized I1 run. The fixture uses Fake model and local owner credentials. Full S03 provider, IAM and deployment acceptance are separate.

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
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing CONFIG factory, native Runner, owner cancellation and Agent ledger | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test and runtime dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | One named exact integration method | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Disposable primary, trusted owner and latest exact revision | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing events, result and reservation states | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing result DTO and scoped SQL | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger or credentials in reviewer evidence | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Existing test fixture construction | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JUnit, Awaitility, Reactor and JDK concurrency | None |
| MC-JSON-001 | Not applicable | N/A | §7/11 | Existing event payload field reads | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Bounded latches, Future and existing fixture policies | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing PostgreSQL test JVM opt-in | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing CONFIG workflow Strategy and durable ledger | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual operator result remains pending | None |

### 20.6 Final verdict

PASS — Ready for user review
