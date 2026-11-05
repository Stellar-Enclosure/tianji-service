# S04 持久 Session 与 Memory 遗忘联合验收

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One existing opt-in integration test composes already-established production adapters; no new runtime mechanism |
| Created | 2026-11-06 03:24 Asia/Shanghai |
| Updated | 2026-11-06 03:24 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test file and three review documents |
| Change Surface | Independent opt-in integrated fixture and shared V9→V10 setup |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17.2 S04、§19 T2-01/T2-03/T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 16f0dcd5f8e6fb9abc537e86433911a7b085e1be |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S04 composition Plan](../plan/2026-11-06-03-25-s04-session-memory-composition.md) |

## 1. Summary

在原有一次性 PostgreSQL/pgvector 夹具中，把真实 CONFIG 执行器、持久 Session、V9 来源快照和 V10 Memory 联合起来。验证替换执行器继续第二轮时使用当前有权事实和已有历史。之后真实遗忘并重放原写入与 BUILD，第三轮因旧来源失效而在模型调用与预算预留前失败。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [S01–S12 acceptance matrix](../evidence/2026-11-05-release-acceptance-matrix.md) | S04 needs persistent replacement, owner/scope controls and no stale retrieval after forget. |
| EVD-002 | [MemoryPostgresTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/memory/MemoryPostgresTest.java) | Current isolated PG fixture builds V10 after V9 and verifies vector, forget, late build and receipt replay in separate cases. |
| EVD-003 | AdkAgentRunExecutorPostgresTest#persistent_session_executor_restores_prior_turn_and_releases_its_lease and AdkSessionPostgresTest#native_runner_restores_a_tool_exchange_and_prior_turn_for_the_next_run | Existing durable Session restoration has no composed real V10 Memory provider and forget step. |
| EVD-004 | AdkRunBudgetPlugin#revalidateHistory, AgentLedgerService#sessionContextHistory, AdkContextSnapshotProjector | Existing owner Run lease and V9 provenance gate runs before resolving current sources, reserving budget or calling a model. |
| EVD-005 | MemoryContextProvider#revalidateHistory, PostgresMemoryService#contextEntries/#forget, MemoryIndexWork#apply | Current provider requires live exact owner/scope revision; the real fact service and existing BUILD handler can test tombstone replay. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: One existing AR integration test, no generated example or business project.

## 3. Goals and Non-goals

Add the missing composed S04 operator scenario under an exact selector. Use real existing storage and Runner with deterministic Fake model/embedding and exact local fixture authority. External IAM, actual vendor, separate process restart, full S04 acceptance and production changes remain independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR existing PG test | Affected | EVD-001–005 | Add one exact opt-in and reuse fixture setup | Exact participants, assertions, sequencing and gates | §7/§8/§14/§15/§16 |
| V9 Session and V10 Memory | Context-only | EVD-002/004/005 | Observe existing behavior on isolated operator database | Explain scope, selected versions and proof | §7/§11 |
| Product API/config/PaC | Unchanged | EVD-003/004 | No production changes | Verify exact scope | §9/§16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Compose real Session, Runtime, Memory and index | Must | One real first CONFIG run persists V9 Memory provenance. A replacement executor and provider use the same durable Session and authorized original memory in a second prompt, with exact owner/native Session provenance and unchanged source digest. | S04 EVD-001–005 |
| REQ-002 | Prove tenant/scope isolation and forget safety | Must | Two tenants share subject, definition, Session and memory identifiers and keep separate facts. Another owner scope holds its own fact. Real forget followed by original UPSERT and BUILD replay leaves no target retrieval/vector. A stale-history run fails before provider/budget, and an independent fresh Session succeeds without the forgotten or foreign marker. | EVD-001/002/004/005 |
| REQ-003 | Isolate source and actual operator evidence | Must | Preserve default original Memory PG test and its guard. New exact method needs additional opt-in. G1 only three finite owner suites, G2 compiled method/guards and no PG report. Four exact paths and all 28 SQL preserved. | T4-05 and EVD-002 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Permission/context |
| --- | --- | --- |
| ACTOR-001 | Disposable-fixture operator | Explicit fresh loopback primary, pinned preinstalled pgvector and exact selected case |
| ACTOR-002 | Verified synthetic USER | Fixture-local exact Run and Memory decision; two tenants with same subject |
| ACTOR-003 | Source reviewer | Offline JDK25 finite owner checks, no PostgreSQL execution |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe authorized persisted second turn | ACTOR-001, ACTOR-002 | Guarded V9/V10 fixture, exact reviewed Memory binding | First run, replace executor/provider, restore same Session | No source from wrong tenant or scope | Current same provenance and first turn appear in second prompt | REQ-001/002 |
| UC-002 | Deny stale lineage after real forget and replay | ACTOR-001, ACTOR-002 | Persisted first and second turn Memory lineage | Forget, replay UPSERT and BUILD, start third and fresh controls | Stale run fails before provider/budget, foreign and sibling facts remain | Target unavailable, fresh same-owner Session succeeds | REQ-002 |
| UC-003 | Verify source delivery | ACTOR-003 | Exact reviewed baseline and source | Finite reactor, compiled guard and scope review | Missing/failed/skip/PG report invalidates source gate | Evidence separate from I1 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

Use the existing test-class guard for fresh loopback tianji_it_ database and pinned preinstalled vector patch. Retain migrations V1–V9 followed by V10 with two dimensions, clean disabled and pre-existing history checksum comparison. Add exact method guard tianji.memory.postgres.session-context.enabled=true. Select the single new method only in a fresh DB; do not select both methods into one setup. Real DB/extension provisioning is an operator action. The fixture-local trusted callbacks verify owner and current Run authority against the ledger, with exact scope and SEARCH/RETRIEVE only for model-context paths. No real IAM claim or vendor request.

## 6. Project Technology Context

Java 25, Spring JDBC/Tx, pinned ADK and Spring AI, JUnit and Mockito. Existing AR Memory integration test owns the composed path. Real PostgresMemoryService, MemoryIndexWork, PgVectorMemoryIndex, DefaultContextAssembler, MemoryContextProvider, AgentRunExecutor and PostgresAdkSessionService remain authoritative.

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
| Rule 1 | Yes | EVD-001–005 | Use existing MemoryPostgresTest and exact semantic method name | §7/§8/§10 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | Use guarded fixture and exact owner, run, memory and outcome checks | §7/§8/§10 | TEST-001–004 | PASS |
| Rule 3 | No | EVD-001–005 | Existing values and private test resource owner, no production DTO | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 4 | No | EVD-001–005 | No Bean or production logger | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | Reuse current ADK, Spring AI, JDK, Mockito, JUnit and JDBC | §7/§8/§10 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–005 | No JSON mapping or enum code change | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 7 | No | EVD-001–005 | Add only a private JUnit property guard | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 9 | No | EVD-001–005 | Direct existing fixture reuse, no production abstraction | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 10 | No | EVD-001–005 | Existing fixture duration and clock semantics | §7/§8/§10 | TEST-001–004 | N/A |
| Rule 11 | Yes | EVD-001–005 | Keep existing AR PostgreSQL test, schema ownership and current CQE boundaries | §7/§8/§10 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: No production deadline, timestamp, scheduling or conversion changes. The fixture reuses current bounded Run and Memory durations and the existing class timeout.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Direct alternative | Concrete inadequacy | Added cost/failure | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One composed test method | Add | REQ-001/002 | Existing isolated Session, vector and forget cases | They do not prove replacement Runtime with actual durable Memory lineage and post-forget provider fence | One exact method and existing bounded fake/local authority | Add |
| Shared V9→V10 test setup | Reuse | REQ-003 | Duplicate setup and vector/publication binding | Duplicating fixture scope and migration guard risks divergence | Test-local lifetime only | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure/TOCTOU | Value |
| --- | --- | --- | --- | --- | --- |
| Existing V10 test | Existing local PostgreSQL only when selected | Existing | Existing test semantics and migration targets | Current preprovision guard | Original scope |
| Composed operator test | Explicit loopback database only | Independent exact method | Actual existing Run, Session, metadata and Memory state | Verify tombstone and stale history before provider | Joint S04 proof |
| Source gate | No actual DB | None | Finite tests and compiled method | Cannot credit skipped/unexecuted I1 | Safe offline evidence |

### 7.1 System Architecture Design

Use one existing isolated PostgreSQL fixture and pgvector index. Compose the real owning Agent executor and Session store with the same-source Memory service, exact MemoryContextProvider bindings and V9 snapshots. Deterministic Fake model/embedding and verified fixture-local callbacks stay local to the test.

### 7.2 High-Level Design

Two tenants share subject alice, definition key/version, Session ID and memory ID but differ in verified issuer and tenant. Add an indexed sibling scope fact with the same owner. The first and replacement second runs use the same persisted owner Session and exact currently authorized memory fact. Then actual forget and replay preserve the tombstone, remove target vector visibility and cause stale lineage to reject before provider/budget. Foreign tenant, sibling scope and fresh same-owner Session are positive controls.

### 7.3 Detailed Design

Retain the existing v10_fact_receipt_index_visibility_forget_concurrency_grants_and_catalog_fences method and move only common guarded V9→V10 setup into openV10Fixture. Existing historical V11/V12 stages remain owned by the original case. New I1 selects only one exact method. Seed scoped sentinel facts and index each via the original scoped BUILD event. Build a CONFIG root with no tools and enable existing context snapshot capture for this test executor. First and replacement executors/providers have separate instances and Fake model captures; assert the second prompt includes first turn history and current authorized fact. Compare exact V9 owner, native Session and Memory provenance/hash across both successful runs. Foreign same-ID Session includes only its own marker.

Forget the actual owner fact, replay the exact original UPSERT command and original BUILD event, retain original receipt and absence of recreated vector or retrieval. Third run on the old Session fails TIANJI_DEPENDENCY_UNAVAILABLE, leaves no model subscription, durable MODEL reservation or generated output. Fixture authority records only a history RETRIEVE for the blocked Run. Actual retrieval of the foreign and sibling scope still succeeds. A fresh same-owner Session runs and excludes all forgotten/foreign markers.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/memory/MemoryPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s04-session-memory-composition.md` |

## 9. Interface Definitions

Context-only. Invoke current local Java adapters and existing Run/Memory contracts. No new public API, transport or producer declaration.

## 10. POJO and Data Model Design

Reuse existing definition, native events, context metadata, Memory contracts and result codes. One private test resource owner and one fake model are bounded to the existing test class.

## 11. Database Design

Context-only. Use the original guarded V9 then V10 setup and actual current owner/vector/Session/snapshot queries. Preserve all 28 production SQL bytes. New method never applies V11–V16. Existing test owns its old subsequent stages.

## 12. Frontend Page Design

Not applicable. No browser, UI or production transport.

## 13. Design Patterns and Architecture Principles

Compose existing real product adapters in one opt-in fixture. Reuse the same setup and lifetime to avoid a parallel DB harness. Current verification interfaces remain the authority boundary; the fixture callbacks do not replace production IAM.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | G0 verifies exact baseline, old method and absent new method. New method is a test-only integration proof, so no synthetic RED. |
| TEST-002 | Exact finite G1 exercises three adjacent owner suites. G2 compiles owning test class, sees exact method/guards and no PostgreSQL report. |
| TEST-003 | Operator I1 runs only exact method on disposable PG with preinstalled pinned vector. Require real first/replacement source lineage, same-ID tenant/scope isolation, tombstone and no provider/budget after replay. |
| TEST-004 | Strict docs, exact four paths, all 28 SQL unchanged. No configuration, production source or deployment action. |

## 15. Non-functional and Cross-cutting Design

Use existing bounded fixture timeout, local deterministic providers and synthetic markers. Show failure without raw marker values in fixture assertion messages. No background process, listener or vendor egress.

## 16. Compatibility, Migration, Rollout, and Rollback

Existing default test selection, original V10/V11/V12 case behavior and production contracts remain. Added test requires explicit method switch. No schema changes. Revert one source commit to remove the composed test.

## 17. Alternatives and Decisions

Do not duplicate PG setup or merely add another mocked MemoryContextProvider case. The exact integration method reuses one isolated schema while crossing the real existing gates.

## 18. Risks and Open Questions

Actual PostgreSQL/pgvector, the local test authority execution and complete S04 integration remain pending until I1 executes. Operator role, deployed IAM, native tool exchange and true process restart have separate evidence.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/11 | TEST-002/003 |
| REQ-002 | UC-001, UC-002 | §7/14 | TEST-003 |
| REQ-003 | UC-003 | §8/14/16 | TEST-001/002/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR integration fixture owns composed proof | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse V9/V10 fixture, CONFIG executor, session and Memory adapters | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK/ADK/JDBC/JUnit | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | Exact owner test and method selector | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Guard disposable PG plus exact Memory/Run authority and two owner scopes | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Current typed values and private test fixture only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No production DTO mapping | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No logger or new exported content | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | Test-local construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Current JDK, Mockito and ADK Fixture | None |
| MC-JSON-001 | Not applicable | N/A | §10 | Existing native context and fact codecs | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No changed time contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing PG properties and additional test-only method guard | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Compose existing real adapters and bounded fakes in exact test owner | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One Java test plus three docs, all 28 SQL unchanged | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and compiled/source proof separately from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | No live PG needed for the source gate | None |

### 20.6 Final verdict

PASS — Ready for user review
