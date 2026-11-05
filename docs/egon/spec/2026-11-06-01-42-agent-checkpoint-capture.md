# T4-03 Agent 首条持久输入 checkpoint 写入

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One fixed technical capture in the existing Run and Session transaction boundary |
| Created | 2026-11-06 01:42 Asia/Shanghai |
| Updated | 2026-11-06 01:42 Asia/Shanghai |
| Owner | Tianji Agent Runtime and Agent Server |
| Repository | tianji-service |
| Scope | First durable input checkpoint writer, default off |
| Change Surface | AR existing runtime and Session append, one AS property, finite/operator test evidence |
| Affected Chapters | §7, §8, §11, §14, §15, §16 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | f262564e5cc575de25d301bd6dd452fbd00d3434 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [V16 存储](./2026-11-06-01-25-agent-checkpoint-storage.md), [双 JVM 恢复探针](./2026-11-06-00-31-adk-durable-resume-fork.md) |
| Related Plans | [Checkpoint capture](../plan/2026-11-06-01-43-agent-checkpoint-capture.md) |

## 1. Summary

为当前持久 Session 的首条真实用户输入保存一个有界 checkpoint。配置默认关闭。启用后只接受既有单 Agent 无工具流程，写入和原生 Session 输入共用现有主库事务。该记录证明历史截点，实际恢复与 Run 接管由独立步骤验证。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Agent storage | AgentDB V16 and AgentStorageVerifier#verifyCheckpointCapture | Existing exact typed table, closed owner, Run/native event FKs and append-only INSERT role gate |
| EVD-002 | Verified material | AgentLedgerService#executionMaterial and AgentRunAuthorization.Request | Same stored row binds verified complete actor, definition/version/hash, request and canonical snapshot digests and policy |
| EVD-003 | Existing normal runner | AdkAgentRunExecutor#executeScoped, #buildRoot, #requireGovernedTools, AgentFlowSessionRunner | Current root and inputs already validated. Actual ADK 1.13.0 and GenAI 1.77.0 bytecode emit original Content and author user, with role user on Content.fromParts |
| EVD-004 | Existing transaction | PostgresAdkSessionService#appendEvent/#append, JdbcAdkSessionRepository#lockRun/#liveFences/#lockScope | Current original Session event and revision share one transaction and an established lock order |
| EVD-005 | Current host wiring | TianjiAgentRuntimeAutoConfiguration#tianjiAdkAgentRunExecutor, AS application.yml | Existing runtime and same-source prerequisite, existing boot Flyway disabled and SQL init never |
| EVD-006 | Existing independent probes | AdkDurableResumeBoundaryPostgresTest and AgentModelUsagePostgresTest | V9 probe keeps original Run lease. Existing V15 profile and its grants stay independent |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extension to existing ordinary Agent infrastructure, with no generated business scaffold or example cleanup.

## 3. Goals and Non-goals

交付实际默认关闭的首条输入记录。外部对账、恢复读取和命令、旧 Run 接管、新授权、对部署数据库应用迁移、启动真实应用以及原生 resumability 仍是独立步骤。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR runtime and native append | Affected | EVD-002/003/004 | Exact admitted first durable event and atomic bounded capture | Ownership, transaction, fault and finite tests | §7/§8/§11/§14/§15 |
| AR host and AS default | Affected | EVD-001/005 | Explicit opt-in with V16 readiness, packaged false | Exact property, compatibility and activation gates | §7/§8/§14/§16 |
| Client, IAM and restore | Unchanged | EVD-003/006 | Existing wire, Run transitions, scope and native runner semantics | Preserved boundary | §9/§10/§12/§18 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Gate capture on explicit property and exact storage | Must | Default false never accesses V16; enabled owning runtime verifies exact V16 with same primary and fails closed if unavailable | EVD-001/005 and T4-03 |
| REQ-002 | Admit exactly the first durable USER input | Must | Current non-LEGACY USER, single tool-free CONFIG LlmAgent, exact original one text, empty local/shared/history, first attempt, valid fences | EVD-002/003/004 |
| REQ-003 | Write one checkpoint atomically with Session append | Must | Complete existing provenance, V16 typed first boundary, old/new native counters, zero receipts, same transaction, rollback on failure | EVD-001/002/004 |
| REQ-004 | Preserve existing execution/authority on all other paths | Must | Unsupported or duplicate append stays ordinary; no restore, new grants, new SQL version, production activation or changed Run lifecycle | EVD-003/005/006 |
| REQ-005 | Verify separately from actual integration | Must | Finite RED/GREEN, actual packaged false, unchanged artifacts, compile-only dedicated operator fixture for both codecs and rollback | T4-03 and EVD-006 |

### 4.2 Use-case analysis

| Actor ID | Actor | Evidence |
| --- | --- | --- |
| ACTOR-001 | Existing trusted Agent runtime | EVD-002/003/004 |
| ACTOR-002 | AgentDB provisioning and acceptance operator | EVD-001/005/006 |

| ID | Use case/goal | Actors | Main flow | Outcome |
| --- | --- | --- | --- | --- |
| UC-001 | Capture a bounded historical checkpoint | ACTOR-001 | Verify enabled host then persist newly admitted native input under the current owner and leases | One immutable row with the exact original source |
| UC-002 | Reject misconfiguration and prove rollback | ACTOR-001/ACTOR-002 | Leave default off, exercise explicit readiness and isolated JDBC fault | No unsafe checkpoint or model invocation on admitted insert failure |

## 5. Constraints, Assumptions, and Decisions

The existing AgentDB primary owns the checkpoint and existing model flow. V16 is already immutable. All 28 production SQL files and current permission metadata remain untouched in this Step. No new dependencies, constructors on Spring managed business services, async workflow or direct runtime activation.

ASM-001. The exact canonical snapshot hash comes from the verified execution material. Existing execution snapshot is immutable after submission; the same Run and immutable definition provenance are rechecked in the write SQL. The writer does not invent PostgreSQL JSON canonicalization or copy raw input into the checkpoint.

## 6. Project Technology Context

Existing traditional AR/AS infrastructure, Java 25, pinned ADK 1.13.0/GenAI 1.77.0, Spring JDBC/Tx, PostgreSQL. Existing native codecs remain authoritative.

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
| Rule 1 | Yes | EVD-001–006 | Explicit local immutable value holder and existing storage classes | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 2 | Yes | EVD-001–006 | Typed material validation plus exact identity, state and fences at storage boundary | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 3 | Yes | EVD-001–006 | Private immutable value data only and redacted representation | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 4 | Yes | EVD-001–006 | Reuse existing auto-configuration bean and fixed technical failure without payload logging | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 5 | Yes | EVD-001–006 | Existing Spring/JDBC, ADK and JDK only | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 6 | No | EVD-001–006 | Fixed SQL empty array; no external JSON schema | §7/§8/§11 | TEST-001–005 | N/A |
| Rule 7 | Yes | EVD-001–006 | Explicit false on authoritative packaged AS defaults | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 9 | No | EVD-001–006 | Direct use of existing transaction boundary for one fixed technical case | §7/§8/§11 | TEST-001–005 | N/A |
| Rule 10 | Yes | EVD-001–006 | Use existing UTC occurred instant representation and database clock | §7/§8/§11 | TEST-001–005 | PASS |
| Rule 11 | Yes | EVD-001–006 | Current AR/AS infrastructure and existing owning AgentDB | §7/§8/§11 | TEST-001–005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | agent_checkpoint.captured_at and original Run/Session lease checks | Occurred instant | PostgreSQL timestamptz(6), java.time.Instant for existing lease | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: internal only | Nonfinite capture rejected by V16, original lease/deadline equality still expires | Six fractional digits, primary PostgreSQL clock_timestamp(), no conversion to local date/time | No change to old clocks or timestamps | Step 1 TEST-002/004 and separate operator transaction round-trip |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Direct alternative | Verdict |
| --- | --- | --- | --- | --- |
| Existing normal runtime factory | Add one false default option | REQ-001 | Existing runtime alone cannot enforce optional V16 | Required |
| Existing executor and Session adapter | Bind verified exact first input to live lease | REQ-002/003 | A later callback or separate transaction leaves the native append and checkpoint inconsistent | Required |
| Existing JDBC Session repository | One typed INSERT | REQ-003 | Existing generic Session event cannot represent full provenance | Required |
| New service/port, restore state or API | None | REQ-004 | Existing layers and ownership suffice | Excluded |

| Path | Network calls | Client states | Authority | Decision |
| --- | --- | --- | --- | --- |
| Disabled or out of scope | Same existing execution | Same | Same existing Run identity and permission | Preserve |
| Enabled admitted capture | Existing DB transaction, no outbound change | Same | Same owning primary and both current fences | Add bounded write |
| Native resume and Run takeover | Independent future requirement | No product entry in this Step | Requires separately verified current policy and actual S11 | Separate |

### 7.1 System Architecture Design

One AgentDB primary. The existing AR runtime gate selects and binds the candidate. Session persistence remains responsible for both native history and this historical cut. AS exposes only a default false configuration property.

### 7.2 High-Level Design

Read `stellar.tianji.agent.recovery.checkpoint-capture.enabled` in existing runtime wiring, default false and explicitly false in the AS package. Existing host and operator fixture use the trusted static `AdkAgentRunExecutor.forRuntime(..., boolean checkpointCaptureEnabled)` factory. Its private canonical constructor requires the exact persistent owning primary, verifies the single bounded `google-adk` Maven package metadata at version 1.13.0 and runs V16 readiness on explicit true. All eight existing public constructors remain available with capture disabled. A raw option with runtime disabled does not start the runtime.

Only current non-LEGACY USER, persistent `tianji` Session, attempt 1, single compiled CONFIG LlmAgent with no subagents or output key, no tool declarations or allowed tool versions, zero maxToolCalls, no Process link, no context providers, snapshots or trimming, no prompt or skill dependency, no artifacts and no structured taskData. Accept one nonblank original USER_INPUT text. Root's existing tool/code/callback validation remains in force. Code First, Graph, process links and other modes execute normally without capture.

### 7.3 Detailed Design

Bind optional immutable source material to existing lease-scoped Session adapter. Validate the same owner, Run ID, Session ID, attempt and lease epoch using current execution material and claimed Run. Use original post-claim Run revision and product event sequence. Match the original and durable decoded native Event from either existing codec to the same exact single Content built for the original Run. Require author and role user, positive native timestamp, bounded nonblank printable ASCII IDs, empty actions, absent branch/model/Tool markers, prior Session revision and sequence zero and local/shared state empty. Exact Content equality rejects additional part kinds or changed text without retaining a second persisted input.

Reuse current order Run lock → Session lock → Session lease lock. To attest empty shared state, the opt-in candidate uses a nonblocking acquisition of the existing tenant/app scope fence and checks APP/USER state bounds while held. A busy scope or known ineligible state produces no checkpoint and preserves the normal append. Keep current duplicate branch before capture, so it does not create rows retroactively. Do not add a long wait or a new lock family.

For a newly admitted event, insert native Session event, update Session revision/sequence, insert exactly one V16 row and finish the existing live-fence check in the same write transaction. The insert selects the exact locked Run and published definition version. Match full owner, definition hash, request hash, original Run revision/event sequence, attempt/epoch and Session ID. Require no cancellation and zero earlier model/Tool reservations. Reference the event just inserted in this transaction through its V16 full-owner sequence FK and retain its actual native ID. The existing final dual-fence check preserves deadline and lease expiry handling. Use canonical snapshot SHA-256 from the existing verified execution material and exact empty JSON receipt array. The captured ADK version is `1.13.0`, tied to the actual pinned artifact.

An admitted storage failure, duplicate checkpoint key or unexpected zero-row INSERT fails before the model. Roll back native event, Session revision and checkpoint together. Keep original lease/deadline handling, error codes and normal terminal path; use a fixed redacted dependency failure for insert errors. Do not catch and continue after an admitted storage fault. Ineligible or disabled capture does not interfere with ordinary Session append. A captured historical row does not prove current eligibility after further state, tool, model, budget or projection changes.

## 8. Package Structure and Code File Tree

| Operation | Exact path | Scope |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java` | Focused finite capture regression |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java` | Single fenced checkpoint INSERT in current JDBC adapter |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java` | Add optional bound first-input capture to current Session append |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | Admit exact original run and input |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java` | Explicit runtime option and V16 startup validation |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Packaged default false |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` | Opt-in actual PostgreSQL and rollback fixture |
| CREATE | `docs/egon/spec/2026-11-06-01-42-agent-checkpoint-capture.md` | Effective design |
| CREATE | `docs/egon/plan/2026-11-06-01-43-agent-checkpoint-capture.md` | One source Step |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-capture.md` | Actual results and runtime boundary |

## 9. Interface Definitions

No public API, event, SDK or wire change. Internal optional Session binding and host setting follow §7.

## 10. POJO and Data Model Design

Reuse existing immutable material and lease. The internal capture binding may hold exact input in memory only until the Session adapter is released. It has a redacted representation and no serialization contract. The actual checkpoint contains only the V16 typed provenance fields, not text or content.

## 11. Database Design

### 11.1 Table Inventory

| Table | Existing/new | Purpose and owner | Read/write paths | Change | DDL script | Requirements |
| --- | --- | --- | --- | --- | --- | --- |
| tianji_agent.agent_checkpoint | Existing | AR bounded historical cut | First-input INSERT only | Activate internal conditional writer | Existing V16 | REQ-001/002/003 |

Current Run, Session, scope, event and budget tables are supporting reads/locks or the existing append path. Schema and existing write interfaces are preserved.

### 11.2 Per-table Detailed Design

#### 11.2.1 tianji_agent.agent_checkpoint

##### Purpose, ownership, and lifecycle

Same owning primary and exact V16. Write one append-only row for an admitted first native input. No later operation rewrites it. Existing parent CASCADE and independent retention requirements remain from V16.

##### Complete column design

Use the exact 29 column types, nullability, widths, collations and constraints of [the locked V16 design](./2026-11-06-01-25-agent-checkpoint-storage.md). The new mapping is:

| Column | Native type | Source / change |
| --- | --- | --- |
| tenant_id, subject_type, subject_issuer, subject_id | bigint and existing bounded varchar | Same verified owner and exact JDBC scoped keys |
| run_id, attempt, checkpoint_no, run_lease_epoch, run_revision, run_event_sequence | varchar, integer and bigint | Exact locked Run, attempt/ordinal 1, post-claim revision/sequence |
| app_name, session_id, session_lease_epoch | existing varchar and bigint | Same exact Session namespace, identity and lease |
| prior_session_revision, session_revision, prior_session_event_sequence, session_event_sequence | bigint | Existing native append and exact prior/new counters 0→1 |
| native_invocation_id, native_event_id | existing varchar | Actual durable decoded native event |
| definition_key, definition_version, definition_sha256, request_sha256, snapshot_sha256, policy_version_ref | varchar, bigint and char | Current immutable verified material |
| engine_version, safe_boundary, tool_receipts | existing varchar and jsonb | 1.13.0, FIRST_DURABLE_INPUT_V1, exact [] |
| captured_at | timestamptz(6) | Primary database clock, UTC occurred instant |

##### Keys, relationships, and constraints

Exact existing V16 PK and both CASCADE FKs. INSERT never replaces a row. Owner, actual native event and immutable definition checks are in the current physical write transaction.

##### Index inventory and per-index justification

| Index | Type/unique | Ordered columns/expressions | Predicate/include | Query and operation | Cardinality/selectivity | Sort/coverage role | Write/storage cost | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| agent_checkpoint_pkey | Existing B-tree unique | tenant_id, run_id, attempt, checkpoint_no | None | First-cut identity and existing Run cascade | At most one | Existing identity | One existing entry per write | Preserve |
| agent_checkpoint_session_event_idx | Existing B-tree nonunique | Full native owner/app/session/sequence tuple | None | Session event cascade | Exact existing event | Existing owner deletion | One existing entry per write | Preserve |

##### Access patterns and SQL shape

| Operation | Caller | Predicate/join/order | Expected rows | Index/constraint | Lock/isolation | Failure/idempotency |
| --- | --- | --- | --- | --- | --- | --- |
| Fenced insert | Existing JDBC Session repository | Exact owned Run and native event, immutable provenance and capture conditions | One after admission | V16 PK and existing FKs | Same native append transaction and locks | Unexpected zero, conflict or DB failure rolls back append |

##### Migration and historical-data handling

No SQL edits or additional migration. No old Session or Run backfill. Disabled runtime works with old admitted schema. Existing V15 usage provisioning remains independent.

##### Transaction, consistency, and recovery

Existing JDBC primary write transaction. Exact first durable input, checkpoint and Session revision all commit or roll back together. A later operator run tests successful commit and checkpoint insert failure. Future restore requires distinct state and authority proof.

### 11.3 Entity-relationship diagram

Relational model change: No. Both FK targets, indexes and all types are exactly the existing V16 contract.

## 12. Frontend Page Design

No frontend surface or new state displayed.

## 13. Design Patterns and Architecture Principles

Direct implementation in the existing native Session transaction. One fixed technical capture condition has no independently variable business behavior. A separate Strategy or service would increase dependency and transaction risk without a second owner.

## 14. Test Design

| Test ID | Gate | Required evidence |
| --- | --- | --- |
| TEST-001 | R1/G1 | Typed finite admission RED followed by valid exact native input and rejected wrong author/role/content/action, stale/history or disabled cases |
| TEST-002 | G1 | Existing finite session/codec and runtime wiring regression, both native codec input representations and source boundaries |
| TEST-003 | P1/S1 | Actual AS configuration false and V16 unchanged, same PaC and old SQL bytes, exact commit paths |
| TEST-004 | Compile only then separate operator I | On exact V16 with isolated roles run real executor and Fake in both codecs. One committed row only. Revoke only checkpoint INSERT after verified startup and prove native input/revision and checkpoint rollback with zero model calls. |
| TEST-005 | Separate operator S11 | Forced stop after committed capture and before budget/model, new process reads unchanged facts and ordinary expiry fence. Later actual native resume/takeover separately gated. |

The code and test artifact can be compiled without starting applications, database or a runtime child. The existing V9 native probe and V15 fixture keep their exact environments.

## 15. Non-functional and Cross-cutting Design

One extra insert and bounded scope probe only for a new eligible first input. Existing scopes and current primary ownership. Capture content is never logged, copied to V16 or returned. Use ordinary failure resolution without additional retry or scheduler.

## 16. Compatibility, Migration, Rollout, and Rollback

Explicit packaged false. Existing eight public constructor signatures and their disabled behavior stay compatible. Only the trusted static factory exposes the explicit option. No existing permission, event wire, migration or state transition changes. Activate only after actual independently authorized V16/role, both codec and rollback acceptance. Disable capture without altering already persisted checkpoint rows or Flyway history.

## 17. Alternatives and Decisions

Store in the current transaction rather than an after-commit listener. Use the already persisted native input as the reference. Keep the ordinary Runner App with its current settings. Switching to native `resumable(true)` is unnecessary for historical capture and affects a different compatibility contract.

## 18. Risks and Open Questions

Actual PostgreSQL role and transaction, kill/restart behavior, current authorization at resume, run epoch takeover and complete S11 remain independent. Unknown side effects keep current manual reconciliation boundaries.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/UC-002 | §7/§16 | TEST-002/003/004 |
| REQ-002 | UC-001 | §7/§11 | TEST-001/002/004 |
| REQ-003 | UC-001/UC-002 | §7/§11 | TEST-002/004/005 |
| REQ-004 | UC-002 | §7/§16/§17 | TEST-001/002/003 |
| REQ-005 | UC-002 | §14/§16/§18 | TEST-003/004/005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Same existing AR/AS infrastructure and owning storage | None |
| MC-REUSE-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Current leases, execution material, Session transaction and V16 | None |
| MC-DEP-001 | Applicable | PASS | §6/§7/§11/§14/§16 | No added dependency or build | None |
| MC-NAME-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Existing types and one immutable internal capture value | None |
| MC-VALID-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Exact original native input and fenced provenance | None |
| MC-MODEL-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Same original Run and native Session identities | None |
| MC-CONVERT-001 | Not applicable | N/A | §6/§7/§11/§14/§16 | No API or persistence object mapping conversion | None |
| MC-LOG-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Fixed sanitized failure; raw input stays private | None |
| MC-BEAN-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Existing runtime factory owns one default-off decision | None |
| MC-UTIL-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Current JDBC, JDK and pinned ADK APIs | None |
| MC-JSON-001 | Not applicable | N/A | §6/§7/§11/§14/§16 | No public JSON or mapper change | None |
| MC-TIME-001 | Applicable | PASS | §6/§7/§11/§14/§16 | UTC capture instant and database fences | None |
| MC-CONFIG-001 | Applicable | PASS | §6/§7/§11/§14/§16 | One explicit default-false AS property | None |
| MC-PATTERN-001 | Not applicable | N/A | §6/§7/§11/§14/§16 | One fixed technical admission and no variable business behavior | None |
| MC-SCOPE-001 | Applicable | PASS | §6/§7/§11/§14/§16 | No Run transition, resume, authority or schema change | None |
| MC-TEST-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Exact finite gates and independently gated operator fixture | None |
| MC-BLOCKER-001 | Applicable | PASS | §6/§7/§11/§14/§16 | Runtime evidence and native resume remain separately gated | None |

### 20.6 Final verdict

PASS — Ready for user review

Actual operator evidence is independent of this source design.
