# T4-02 Process owner Agent 回执观察实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-22-42-process-owned-agent-outcomes.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 22:42 Asia/Shanghai |
| Updated | 2026-11-05 22:42 Asia/Shanghai |
| Owner | Tianji Process |
| Repository | tianji-service |
| Scope | 单一默认关闭的 PS owner GET、C 合同、明确同源读和成品权限 |
| Source Requirement | Master §19 T4-02 and S12 persistent outcome correlation source slice |
| Baseline Revision | Service 279d8858e46981c7930c838479bf0970fb32d0c9 |
| Implements Spec | [Process owner Agent outcomes](../spec/2026-11-05-22-41-process-owned-agent-outcomes.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 22:41 Asia/Shanghai SHA-256 be416b5b885a9dafbe66dfd1d0063daaee899d4ae9e1087fb339452d7e82517e |
| Effective Specs | [Process owner Agent outcomes](../spec/2026-11-05-22-41-process-owned-agent-outcomes.md), [Process correlation read design](../spec/2026-11-05-20-36-owned-process-correlation-lookup.md#7-architecture-design) |
| Depends On Plans | [Process correlation](2026-11-05-20-37-owned-process-correlation-lookup.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

以同一 ProcessDB 的 immutable START、V5 派发和 V6 Inbox 及 V1 APPLIED 回执提供 owner-local read。单 Step 固定 C、REST、JDBC/flags、实际 OAS/PaC 和有限证据。所有外部身份及底层 ID 保持内部。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Spec](../spec/2026-11-05-22-41-process-owned-agent-outcomes.md) §4、§7.3、§9 API-001 和 §14 是本源码切片的合同。Root 确认本机离线源与 finite/package 验证，部署与 I/E 保持另批。

### 2.2 Effective Spec set

| Spec | Sections | Applicability |
| --- | --- | --- |
| Outcomes | §4–§9/§14 | Source semantics, closed wire, safe owner evidence |
| Owned Process correlation | §7/§9 | Prior owner authorization and physical primary read pattern; its existing output stays stable |

### 2.3 Superseded or excluded content

无旧 Spec 替代。S12、AS 详情授权、Web/SDK、跨 owner dereference、运行环境不属于此 Step。

## 3. Effective Requirements and Acceptance

| Requirement | Exact Spec source | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/9 | Dedicated current resource, full START owner and default-off, no shared-correlation lookup | C/PS/PaC |
| REQ-002 | §4/7.3/9 | Exact V5/V6 plus proven APPLIED V1 witness, structured no-evidence or controlled error | PS reader |
| REQ-003 | §4/7.3/9 | Owned digest anchor, order, 256 hard scope, page max20 and no raw IDs | C/PS reader |
| REQ-004 | §4/14/16 | Physical primary RR and read-only 2s, fresh finite and exact package, unchanged SQL/POM | PS wiring/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

真实 HTTP presence RED → C typed contract → PS read typed-A RED → exact helpers and query B → route and factory B → finite G1 → actual package P1 → exact documentation and single commit.

### 4.2 Test-first strategy

R1 only adds reflection-safe endpoint test against original tree. R2 uses typed C+B test and empty query A; expected 20 vs 0. The query test validates SQL shape, actor fields, transaction ordering and state evidence, not merely DTO presence. No behavior RED is inferred from a compile failure.

### 4.3 Sequential and parallel boundaries

C and PS/REST drafts may be prepared independently, but own one commit. Apply Phase B query only after the existing idempotency/Inbox helper visibility changes. Copy final controller test after its exact controller. Current SLA and this Step both touch configuration/YAML. Integrate this Step only on clean completed SLA baseline by rebasing the two minimal hunks and verifying all SLA bytes outside their hunks.

### 4.4 Commit boundaries

One focused 16-path source/docs commit. No generated files, open API drafts outside the selected path or fixture tooling in Git.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Implementation handling |
| --- | --- | --- | --- |
| Dedicated protected GET | Add / §7.0 | Existing closed ProcessInstanceDTO and current security | Sibling route; one exact resource plus existing READ |
| Outcome read transaction | Add / §7.0 | V1/V5/V6 source facts, existing bridge writers lock/write | Same-source reader, no engine or AS call |
| Existing helpers | Keep / §7.0 | Exact owner receipt, canonical digest/Inbox decoder and current registry | Narrow technical visibility reuse, no changed writer semantics |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| REST presence and typed contract | REQ-001/003 | R1 | Existing C/PS |
| Query owner evidence | REQ-002/003/004 | R2 | Existing V1–V6 and C |
| Wiring and actual package | REQ-001/004 | G1/P1 | C and PS query B, existing PaC producer |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture, naming and Bean | Existing PS query and named conditional factory | §6/7/8 | One existing technical package, named query, qualified injection | G1 |
| Strict validation and JSON | Current Process owner auth/C validation | §7/9/10 | Guard first, exact C types, safe error and closed OAS | R1/G1/P1 |
| Time, transaction and utility | Process correlation primary reader and ProcessRequestDigest | §6.3/7.3 | Same manager, physical read-only, UTC, digest no raw cursor | R2/G1 |
| Persistence and config | Existing owner schema + Inbox flags | §11/16 | No writer, DDL or dependency change; one false property | P1/S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Owner START | ProcessCommandReceiptStore | readStartByInstance | Exact four-part + instance | Keep | No code change | Step 1, MC-REUSE-001 |
| Dispatch/Inbox | AgentDispatchStore, AgentResultInbox, current registry | Existing key/decoder/require methods | Existing writes use strict proof | Reuse | Narrow sharing, separate safe SELECT | Step 1, MC-MODEL-001 |
| Time, hash and routing | Existing Java/JDBC/PaC | ProcessRequestDigest and same source template | Fits | Keep | No dependency | Step 1, MC-TIME-001 |

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
| Rule 1 | §6/8/10 | C/PS names | Files 2–8 | Exact semantic Query/DTO/VO | G1 | Step 1 | PASS |
| Rule 2 | §6/9 | C closed validation + current auth | Files 1–6/9–11 | Validate after trusted authority | R1/G1 | Step 1 | PASS |
| Rule 3 | §6/10 | Immutable existing value records | Files 3/4/6 | Immutable values; no new ordinary PO | G1 | Step 1 | PASS |
| Rule 4 | §6/8 | Existing technical named factory | Files 10/11 | Exact named qualified query | G1/P1 | Step 1 | PASS |
| Rule 5 | §6/7 | Existing JDK/ProcessRequestDigest | Files 6–8 | Existing helpers, no added dependency | G1 | Step 1 | PASS |
| Rule 6 | §6/9 | Existing Jackson and neutral enum | Files 4/11/13 | Exact closed named wire schema | G1/P1 | Step 1 | PASS |
| Rule 7 | §6/16 | PS one application.yml | Files 10/12 | Default false and AND prerequisite | G1/P1 | Step 1 | PASS |
| Rule 9 | §7/13 | Current Process Query boundary | Files 6/11 | Direct authority and reader composition | G1 | Step 1 | PASS |
| Rule 10 | §6.3 | Current DB/JDK UTC | Files 4–6 | Typed Instant/OffsetDateTime and decision times | G1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing Process technical store | Files 5–8 | Read existing scope only, no new persistence | G1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt, dispatchRecordedAt, receivedAt, decidedAt | Occurred instant | java.time.Instant and OffsetDateTime | UTC instant | UTC Z | Explicit UTC Instant | Wire UTC Z | Same-source timestamps and observation | DB timestamptz/clock_timestamp, micros | No AS event clock exposed | Step 1 G1 TEST-001/002 |

## 5. Change File Tree

| Operation | Exact path | File | Gate |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesControllerTest.java` | 1 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java` | 2 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessAgentOutcomesQuery.java` | 3 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessAgentOutcomePageDTO.java` | 4 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQueryTest.java` | 5 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQuery.java` | 6 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java` | 7 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java` | 8 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | 9 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | 10 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesController.java` | 11 | R1/R2/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | 12 | R1/R2/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-agent-outcomes-v1.json` | 13 | R1/R2/G1/P1 |
| CREATE | `docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md` | 14 | S1 |
| CREATE | `docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md` | 15 | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-process-owned-agent-outcomes.md` | 16 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

The owning Step locks clean 279d8858e46981c7930c838479bf0970fb32d0c9. The two overlaps in ProcessSecurityConfiguration and application.yml are rebased to the exact SLA source. Lock all source/Spec/Plan SHA and paths.

### 6.2 Build, test, and environment prerequisites

Existing offline source-reactor, isolated settings, JDK25, Python 3.11+ for static archive proof. Capture actual previous PS PaC from a verified package including the prerequisite SLA step, before mutating paths.

### 6.3 Immutable constraints and approved decisions

No live runtime, listener, DB, network, UI, publisher or migration. No AS/AR/CL/Web paths, existing migration bytes, POM, grants or dependency.

### 6.4 Plan Clarifications

| ID | Choice | Evidence and consequences |
| --- | --- | --- |
| PC-001 | Opaque SHA cursor with 257 bounded key query | Existing prefix covers exact single source. Only up to 256 occurrences is admitted. No internal ID in cursor and no 257th result. |
| PC-002 | C item uses dispatchRecordedAt | V5 clock marks intent creation, distinct from dispatch-to-AS timing. |
| PC-003 | Registry reuse requires one source per configured tenant | Multiple/absent source is fail-closed, no guessed provenance or V9 open-only scan. |
| PC-004 | Old status scope and no cross-owner navigation | Existing Agent run owner differs from Process actor; current AS/CL stays independent. |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付 Process owner Agent 回执读取

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Reviewed Spec/Plan, prerequisite SLA committed, exact source lock, existing secured PS and V1–V6.
- Baseline state: No agent-outcomes route/contract. Original bridge and Process correlation owner reads already delivered.
- Observable outcome: Current authorized owner sees bounded proved local results and can follow opaque digest pages.
- End state: Exactly 16 source/docs paths in one commit, finite RED/GREEN and actual static package.
- Test-first gate: Required. R1 actual absent route assertion. R2 typed empty returns 0 instead of expected 20. Phases before production.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesControllerTest.java`

- Purpose: Freeze exact protected route and HTTP output.
- Symbols: process_agent_outcomes_route_exists; publishes_only_process_local_outcomes_under_the_current_actor; rejects_unapproved_parameters_and_authority_before_querying; missing_owner_or_unavailable_evidence_are_not_successful_empty_pages.
- Repository evidence: Existing ProcessControllerTest uses standalone MockMvc with fixed safe Process errors.
- Dependencies and consumers: Tracked baseline for R1; B Controller, C DTO and query for G1.
- Why now: First assert a real absent route before production file.
- Contract/signature changes: Reflection-safe RED test first, then real owner actor, closed response and transport tests.
- Input/output and state mapping: Reflection-safe RED test first, then real owner actor, closed response and transport tests.
- Error and edge behavior: Uniform fixed errors; invalid inputs denied after guard, no query on denied actor.
- Standards impact: MC-SCOPE-001 and owning R1 ClassNotFound assertion; G1 four owning tests.
- Literal rule enforcement: Rule 1, Rule 2, Rule 6, Rule 11.
- Implementation pseudocode:

```java
@Test void process_agent_outcomes_route_exists() {
    var type = assertDoesNotThrow(() -> Class.forName(CONTROLLER_FQN));
    assertExactGetAndAndFlags(type, "/{instanceId}/agent-outcomes");
}
assertMockMvcNoStoreAndClosedProcessOutcomeJson();
assertNoQueryOnDeniedActorOrRepeatedAndUnknownParameters();
assertResourceNotFoundAndUnavailableUseSafeEnvelope();
```

- Verification contribution: R1 ClassNotFound assertion; G1 four owning tests.
- After this file: R1 test compiles alone; replace with B only after controller and query exist.

#### File 2 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`

- Purpose: Lock C neutral immutable success and query bounds.
- Symbols: exposes_only_bounded_process_local_agent_outcome_evidence.
- Repository evidence: Existing OrchestrationContractTest covers closed Process value records.
- Dependencies and consumers: Files 3–4 and old Process C tests.
- Why now: Test shape before opening REST.
- Contract/signature changes: Add one typed test with exact page, nullable state and strict cursor cases.
- Input/output and state mapping: Add one typed test with exact page, nullable state and strict cursor cases.
- Error and edge behavior: Reject incompatible phases or excess items, preserve old tests.
- Standards impact: MC-SCOPE-001 and owning G1 C ten.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10.
- Implementation pseudocode:

```java
var query = new GetProcessAgentOutcomesQuery("instance", "v1." + "a".repeat(64));
assertThrows(IllegalArgumentException.class, () -> new GetProcessAgentOutcomesQuery("instance", "v1.invalid"));
assertClosedPageAndNullableReceiptSemantics(query.instanceId());
assertExistingNeutralProcessContractTestsRemain();
```

- Verification contribution: G1 C ten.
- After this file: Compile with Files 3 and 4.

#### File 3 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessAgentOutcomesQuery.java`

- Purpose: Define exact owned instance and nullable opaque cursor.
- Symbols: GetProcessAgentOutcomesQuery.
- Repository evidence: Existing GetProcessInstanceQuery validates safe ID.
- Dependencies and consumers: File 2 and future controller/service.
- Why now: Fix typed query before R2.
- Contract/signature changes: New immutable neutral record with instanceId and cursor only.
- Input/output and state mapping: New immutable neutral record with instanceId and cursor only.
- Error and edge behavior: Strict instance validation and v1 plus 64 lowercase hex.
- Standards impact: MC-SCOPE-001 and owning G1 C bounds, used in typed R2.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3.
- Implementation pseudocode:

```java
public record GetProcessAgentOutcomesQuery(String instanceId, String cursor) {
    public GetProcessAgentOutcomesQuery {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        if (cursor != null && !cursor.matches("v1\\.[0-9a-f]{64}")) throw OrchestrationChecks.invalid();
    }
}
```

- Verification contribution: G1 C bounds, used in typed R2.
- After this file: One immutable input seam exists.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessAgentOutcomePageDTO.java`

- Purpose: Define only Process-local bounded closed output.
- Symbols: ProcessAgentOutcomePageDTO, ItemDTO, DispatchState, InboxState.
- Repository evidence: Existing Process correlation receipt page and neutral enums.
- Dependencies and consumers: Files 2/3 and PS read/HTTP.
- Why now: C result seam required before typed B tests.
- Contract/signature changes: Max20 immutable items, exact times, optional digest cursor, four terminal states only.
- Input/output and state mapping: Max20 immutable items, exact times, optional digest cursor, four terminal states only.
- Error and edge behavior: Null checks for each phase, reject future or inverted observed evidence.
- Standards impact: MC-SCOPE-001 and owning G1 C and PS projection; R2 typed empty DTO.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10.
- Implementation pseudocode:

```java
public record ProcessAgentOutcomePageDTO(String instanceId, Instant observedAt,
                                        List<ItemDTO> items, String nextCursor) {
    // Constructor checks max20, full continuation page, query ID/grammar and times not after observedAt.
    public record ItemDTO(String activityId, DispatchState dispatchState, InboxState inboxState,
                          AgentRunStatusEnum terminalStatus, Instant dispatchRecordedAt,
                          Instant receivedAt, Instant decidedAt) { }
}
// Constructor freezes the list and checks phase, valid terminal enum subset and UTC chronology.
```

- Verification contribution: G1 C and PS projection; R2 typed empty DTO.
- After this file: C contract compiles.

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQueryTest.java`

- Purpose: Prove bounded same-source owner SQL and local state projection.
- Symbols: returns_only_proven_local_outcomes_and_opaque_owner_pages; does_not_promote_early_or_mismatched_agent_events; rejects_foreign_or_changed_anchors_and_above_bound_scope; requires_exact_owner_before_dispatch_and_fails_closed_on_corrupt_evidence.
- Repository evidence: ProcessCorrelationLookupTest mock JdbcTemplate uses actual manager and verified primary transaction setup.
- Dependencies and consumers: Files 3/4 and typed query A/B.
- Why now: Read predicate before implementation.
- Contract/signature changes: Use real existing fingerprints, canonical events, current registry and deliberate wrong paths.
- Input/output and state mapping: Use real existing fingerprints, canonical events, current registry and deliberate wrong paths.
- Error and edge behavior: Actor/Cursor mismatch, 257 cap, early multiple run, malformed row, duplicate join, missing APPLIED audit.
- Standards impact: MC-SCOPE-001 and owning R2 true RED and G1 four query methods.
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
fixture.append(21);
fixture.rows.getFirst().confirm("private-run");
fixture.rows.getFirst().receive("private-run", "APPLIED", "MESSAGE_APPLIED", "expenseAgent");
var first = fixture.query.list(OWNER, query(INSTANCE, null));
assertEquals(20, first.items().size()); // R2 sees 0.
fixture.assertFirstPageSql(20);
assertExactTerminalAndOpaqueCursor(first);
assertForeignAnchorsAndDuplicateJoinFailClosed();
```

- Verification contribution: R2 true RED and G1 four query methods.
- After this file: Typed test ready; introduce A before source behavior.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQuery.java`

- Purpose: Own primary physical read-only outcome transaction.
- Symbols: ProcessLinkedAgentOutcomeQuery#list, beginObservation, readPage, received, private scope and projection values.
- Repository evidence: V1/V3 exact START, V5 PK, V6 FK+unique, current registered producer, V1 applied audit.
- Dependencies and consumers: Files 3–5 and shared helpers Files 7–8 for B.
- Why now: A establishes typed seam; B after focused failure and helpers.
- Contract/signature changes: One stable scoped anchor digest and distinct selected page join; return only Process-local DTO.
- Input/output and state mapping: One stable scoped anchor digest and distinct selected page join; return only Process-local DTO.
- Error and edge behavior: Untrusted/missing owner 404, malformed anchor 400, inconsistent/current dependencies 503, no sensitive SQL projection.
- Standards impact: MC-SCOPE-001 and owning R2 stub returns empty, B finite SQL/state proof.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
public ProcessAgentOutcomePageDTO list(ProcessActor actor, GetProcessAgentOutcomesQuery query) {
    requireVerifiedAndNoAmbientTransaction(actor);
    return read.execute(status -> {
        Instant observedAt = beginObservation(); // Physical SET READ ONLY, primary RR, timeout.
        TaskOrigin start = receipts.readStartByInstance(actor, query.instanceId());
        List<String> ids = selectV5ScopeKeysAtMost257(start, verifiedRegistrySource);
        var page = resolveOwnedDigestAnchorAndSelectTwenty(ids, query.cursor());
        var evidence = readV5V6AndV1AuditForExactPage(start, page, observedAt);
        return new ProcessAgentOutcomePageDTO(start.instanceId(), observedAt, evidence.items(), page.nextCursor());
    });
}
// B requires exact released BPMN, service owner/source/confirmed run and canonical V6; APPLIED checks same V1 receipt.
```

- Verification contribution: R2 stub returns empty, B finite SQL/state proof.
- After this file: A only at R2. Replace with B after File 7/8, before final wiring.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java`

- Purpose: Reuse existing dispatch identity digest without reading seed payload.
- Symbols: AgentDispatchStore.idempotencyKey(Key) and Seed#idempotencyKey.
- Repository evidence: Existing domain process-agent-occurrence-v1.
- Dependencies and consumers: File 6 B and unchanged bridge writer.
- Why now: Shared exact helper avoids copied digest or private input reads.
- Contract/signature changes: Move identical domain/hash field ordering into package-local helper.
- Input/output and state mapping: Move identical domain/hash field ordering into package-local helper.
- Error and edge behavior: No changed writer ids, fingerprint, SQL or behavior.
- Standards impact: MC-SCOPE-001 and owning G1 old AgentDispatchStoreTest and new read tests.
- Literal rule enforcement: Rule 5, Rule 11.
- Implementation pseudocode:

```java
static String idempotencyKey(Key key) {
    return "ad-" + ProcessRequestDigest.hash(List.of("process-agent-occurrence-v1", Long.toString(key.actor().tenantId()),
            key.actor().issuerFingerprint(), key.actor().subjectId(), key.sourceId(), key.instanceId(), key.occurrenceId()), Map.of());
}
// Existing Seed delegates with its already validated key.
```

- Verification contribution: G1 old AgentDispatchStoreTest and new read tests.
- After this file: No wire change.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java`

- Purpose: Reuse immutable Inbox row validation.
- Symbols: AgentResultInbox#read(ResultSet).
- Repository evidence: Existing private decoder verifies full canonical event and persisted metadata.
- Dependencies and consumers: File 6 B and old Inbox/apply callers.
- Why now: Read-only outcome path needs the exact decoder without locking.
- Contract/signature changes: Make existing static decoder package-local only, use exact selected aliased columns.
- Input/output and state mapping: Make existing static decoder package-local only, use exact selected aliased columns.
- Error and edge behavior: All original write/replay/lag behavior preserved; read maps mismatch safely.
- Standards impact: MC-SCOPE-001 and owning G1 old Inbox/correlation and new canonical mismatch.
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 11.
- Implementation pseudocode:

```java
static Entry read(ResultSet rs) throws SQLException {
    AgentTerminalEvent event = decode(rs.getString("event_payload"));
    // Existing exact SQL columns and canonical event fingerprint are compared.
    return existingValidatedReceiptEntry(event, rs);
}
```

- Verification contribution: G1 old Inbox/correlation and new canonical mismatch.
- After this file: Only visibility changed.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Declare dedicated permission resource key and localized label.
- Symbols: TianjiProcessPermissions.AGENT_OUTCOMES_API and api.agent.outcomes.
- Repository evidence: Current Process READ and PaC annotations.
- Dependencies and consumers: Final controller and actual PS producer.
- Why now: Keep current READ meaning, add only dedicated key/label.
- Contract/signature changes: One fixed label, current permission code/scope unchanged.
- Input/output and state mapping: One fixed label, current permission code/scope unchanged.
- Error and edge behavior: No grants or modified old resource.
- Standards impact: MC-SCOPE-001 and owning P1 one resource plus one label.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11.
- Implementation pseudocode:

```java
public static final String AGENT_OUTCOMES_API = "tianji.api.process.agent.outcomes";
// @PermissionMessage uses pageCode=tianji.process.permissions, key=api.agent.outcomes,
// en-US label "Read owned process Agent outcomes"; retain existing READ code and scope.
```

- Verification contribution: P1 one resource plus one label.
- After this file: Permission key exists.

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Wire the query only with same owning V1–V6 storage.
- Symbols: AgentResultConfiguration#processLinkedAgentOutcomeQuery, processSecurityFilterChain.
- Repository evidence: Existing Inbox condition and same manager/schema helpers.
- Dependencies and consumers: Files 6–9 and current post-SLA config.
- Why now: Named factory is exact typed controller dependency.
- Contract/signature changes: Reuse current AgentDispatchStore, AgentResultInbox, AgentResultProducerRegistry and props.
- Input/output and state mapping: Reuse current AgentDispatchStore, AgentResultInbox, AgentResultProducerRegistry and props.
- Error and edge behavior: Fail startup for outcomes enabled without Inbox; require exact same owning source and active V1–V6 schema.
- Standards impact: MC-SCOPE-001 and owning G1 existing and new flags, P1 false preserved.
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11.
- Implementation pseudocode:

```java
@Bean("processLinkedAgentOutcomeQuery")
@ConditionalOnProperty(prefix = "stellar.tianji.process.agent-results", name = "owner-outcomes-enabled", havingValue = "true")
ProcessLinkedAgentOutcomeQuery processLinkedAgentOutcomeQuery(ProcessEngine engine, AgentDispatchStore dispatches,
        AgentResultInbox inbox, AgentResultProducerRegistry producers, AgentResultInboxProperties properties) {
    var configuration = processConfiguration(engine);
    var source = caseDataSource(configuration);
    new ProcessApiSchemaVerifier(source, configuration.getDatabaseSchema()).verifyAgentResultInbox();
    return new ProcessLinkedAgentOutcomeQuery(source, owningManager(configuration), configuration.getDatabaseSchema(),
            dispatches, inbox, producers, properties.registrations());
}
```

- Verification contribution: G1 existing and new flags, P1 false preserved.
- After this file: Preserve prerequisite SLA changes from exact current source.

#### File 11 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesController.java`

- Purpose: Expose independent exact GET under current Process auth.
- Symbols: ProcessAgentOutcomesController#outcomes.
- Repository evidence: Existing Process current authorization and no-store pattern.
- Dependencies and consumers: Files 3–10 and final File 1 test.
- Why now: Register only after owner query and PaC key available.
- Contract/signature changes: One optional cursor and typed instance input; PS protected response only.
- Input/output and state mapping: One optional cursor and typed instance input; PS protected response only.
- Error and edge behavior: Auth before input and query, closed path/query, existing fixed errors.
- Standards impact: MC-SCOPE-001 and owning R1→G1 exact route/MockMvc, P1 PaC.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 6, Rule 9, Rule 11.
- Implementation pseudocode:

```java
@GetMapping("/{instanceId}/agent-outcomes")
ResponseEntity<ProcessAgentOutcomePageDTO> outcomes(String instanceId, MultiValueMap<String,String> parameters) {
    var actor = access.require(TianjiProcessPermissions.AGENT_OUTCOMES_API);
    requireOnlyOptionalSingleCursor(parameters);
    var page = query.list(actor, new GetProcessAgentOutcomesQuery(instanceId, parameters.getFirst("cursor")));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
}
// Controller requires api.enabled, agent-results.enabled and owner-outcomes-enabled; existing PROCESS_READ/scope.
```

- Verification contribution: R1→G1 exact route/MockMvc, P1 PaC.
- After this file: B controller enables full finite suite.

#### File 12 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Declare default-false PS setting without changing SLA or Inbox defaults.
- Symbols: stellar.tianji.process.agent-results.owner-outcomes-enabled.
- Repository evidence: Current exact PS application.yml post-SLA.
- Dependencies and consumers: Files 10–11 conditional config.
- Why now: Make opt-in explicit in packaged source.
- Contract/signature changes: One false key under existing agent-results; all other entries stay byte-compatible.
- Input/output and state mapping: One false key under existing agent-results; all other entries stay byte-compatible.
- Error and edge behavior: Flag false when absent; no env/grant/config profile creation.
- Standards impact: MC-SCOPE-001 and owning P1 actual BOOT-INF/classes/application.yml and old config regression.
- Literal rule enforcement: Rule 7, Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    process:
      agent-results:
        owner-outcomes-enabled: false
        # Existing receive-span-links and lag defaults are preserved.
```

- Verification contribution: P1 actual BOOT-INF/classes/application.yml and old config regression.
- After this file: Configuration readiness established.

#### File 13 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-agent-outcomes-v1.json`

- Purpose: Package contract-first exact OpenAPI.
- Symbols: getOwnedProcessAgentOutcomes, Page, Item, Error.
- Repository evidence: Existing static root Process OAS packaging.
- Dependencies and consumers: Files 3/4/9–12.
- Why now: Publish exact neutral fields and existing safe errors.
- Contract/signature changes: One GET path, OAuth scope, closed DTO/nullable enums, finite status set.
- Input/output and state mapping: One GET path, OAuth scope, closed DTO/nullable enums, finite status set.
- Error and edge behavior: No runtime documentation endpoint or old OpenAPI changes.
- Standards impact: MC-SCOPE-001 and owning P1 byte-exact root entry and semantic contract.
- Literal rule enforcement: Rule 6, Rule 11.
- Implementation pseudocode:

```json
{
  "paths": {"/api/tianji/v1/process-instances/{instanceId}/agent-outcomes": {
    "get": {"operationId": "getOwnedProcessAgentOutcomes", "security": [{"oauth2": ["tianji:process:read"]}]}
  }},
  "components": {"schemas": {"Page": {"additionalProperties": false}, "Item": {"additionalProperties": false}}}
}
```

- Verification contribution: P1 byte-exact root entry and semantic contract.
- After this file: Static OAS proof ready.

#### File 14 — `CREATE docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md`

- Purpose: Archive exact reviewed Spec.
- Symbols: Canonical Spec and reciprocal document links.
- Repository evidence: Existing versioned Spec/Plan conventions.
- Dependencies and consumers: Reviewed scratch bytes and exact approval digest.
- Why now: Keep governing contract with one source commit.
- Contract/signature changes: Exact copy to selected canonical path.
- Input/output and state mapping: Exact copy to selected canonical path.
- Error and edge behavior: Occupied path or changed reviewed bytes blocks source.
- Standards impact: MC-SCOPE-001 and owning S1 strict structure and hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md
printf '%s  %s\n' "$OUTCOMES_APPROVED_SPEC_SHA" "$OUTCOMES_APPROVED_SPEC" | shasum -a 256 -c -
cp -n "$OUTCOMES_APPROVED_SPEC" docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md
cmp "$OUTCOMES_APPROVED_SPEC" docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md
```

- Verification contribution: S1 strict structure and hash.
- After this file: Source docs archived without scope expansion.

#### File 15 — `CREATE docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md`

- Purpose: Archive exact reviewed Plan.
- Symbols: Canonical Plan and reciprocal document links.
- Repository evidence: Existing versioned Spec/Plan conventions.
- Dependencies and consumers: Reviewed scratch bytes and exact approval digest.
- Why now: Keep governing contract with one source commit.
- Contract/signature changes: Exact copy to selected canonical path.
- Input/output and state mapping: Exact copy to selected canonical path.
- Error and edge behavior: Occupied path or changed reviewed bytes blocks source.
- Standards impact: MC-SCOPE-001 and owning S1 strict structure and hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md
printf '%s  %s\n' "$OUTCOMES_APPROVED_PLAN_SHA" "$OUTCOMES_APPROVED_PLAN" | shasum -a 256 -c -
cp -n "$OUTCOMES_APPROVED_PLAN" docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md
cmp "$OUTCOMES_APPROVED_PLAN" docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md
```

- Verification contribution: S1 strict structure and hash.
- After this file: Source docs archived without scope expansion.

#### File 16 — `CREATE docs/egon/evidence/2026-11-05-process-owned-agent-outcomes.md`

- Purpose: Record actual finite and package evidence.
- Symbols: R1/R2/G1/P1/S1 plus exact source lock.
- Repository evidence: Existing Process correlation package evidence.
- Dependencies and consumers: Current actual owning reports and archive.
- Why now: Only after gates record objective results.
- Contract/signature changes: Finite XML, PaC/OAS and exact staged scope with immutable bytes.
- Input/output and state mapping: Finite XML, PaC/OAS and exact staged scope with immutable bytes.
- Error and edge behavior: Missing or stale result blocks a completed evidence claim.
- Standards impact: MC-SCOPE-001 and owning S1 source/test/producer provenance.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the actual R1 and R2 exception types, assertion values and fresh owning report counts.
Record actual G1/P1 selectors, archive SHA, raw PaC SHA and exact new static OpenAPI bytes.
Verify unchanged migration content, scoped commit and unexecuted runtime, then archive evidence.
```

- Verification contribution: S1 source/test/producer provenance.
- After this file: Stage exact 16 paths, one commit only.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/R2/G1/P1/S1 in §8.
- Expected result: Two exact intentional assertion failures, finite selectors with zero errors/skips/failures, actual packaged source OAS/PaC/default, no unrelated change.
- Failure returns to: Files 1–5 for test phases and C, Files 6–8 for evidence, Files 9–13 for wiring/package.
- Completion criteria: Exact route/policy, closed output, stable cursor and same-source authority prove the source slice; actual P1 archive matches scope.
- Rollback: Revert only the one focused commit, no data rollback.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesControllerTest.java`, `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessAgentOutcomesQuery.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessAgentOutcomePageDTO.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQueryTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQuery.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesController.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-agent-outcomes-v1.json`, `docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md`, `docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md`, `docs/egon/evidence/2026-11-05-process-owned-agent-outcomes.md`.
- Commit: feat(process): read owned Agent outcome evidence.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | Root red test only, existing tracked baseline | ProcessAgentOutcomesControllerTest#process_agent_outcomes_route_exists assertion fails on ClassNotFoundException, one failure, no errors/skips |
| R2 | C B Query/Page plus typed PS query A and new PS test | ProcessLinkedAgentOutcomeQueryTest#returns_only_proven_local_outcomes_and_opaque_owner_pages assertion 20 expected vs 0, one failure and no errors/skips |
| G1 | All exact B source, final controller test | 10 C + 4 outcome query + 4 controller + 12 existing permission + 16 post-SLA config + 12 dispatch + 7 correlation + 4 Inbox observation = 69 finite tests, zero errors/failures/skips |
| P1 | Clean same finite selector, static archive after actual permission-producer | Exact current source proof, one new API resource and label, new OAS source bytes and flag false, no permission definition change |
| S1 | Strict docs, exact changed/staged scope and immutable scope bytes | One focused commit, no I/E claims |

Apply File 1 red only for R1. For R2 use exact C types B, PS File 6 A and B/RED query test. Then copy helper and production B phases. Copy already reviewed post-SLA B configuration and YAML; confirm SLA hunks remain byte-compatible. Run each gate separately and retain fresh owning XML/exit. No source gate accepts skipped tests.

```sh
outcomes_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-owned-agent-outcomes
outcomes_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
outcomes_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
outcomes_base=279d8858e46981c7930c838479bf0970fb32d0c9
test "$(git rev-parse --verify HEAD)" = "$outcomes_base"
outcomes_core=OrchestrationContractTest,ProcessLinkedAgentOutcomeQueryTest,ProcessAgentOutcomesControllerTest,ProcessPermissionIntegrationTest,ProcessApiConfigurationTest,AgentDispatchStoreTest,AgentResultCorrelationServiceTest,AgentResultInboxLagObservationTest
outcomes_finite() {
  JAVA_HOME="$outcomes_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$outcomes_settings" -s "$outcomes_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
outcomes_finite 'ProcessAgentOutcomesControllerTest#process_agent_outcomes_route_exists'
outcomes_finite 'ProcessLinkedAgentOutcomeQueryTest#returns_only_proven_local_outcomes_and_opaque_owner_pages'
outcomes_finite "$outcomes_core"
test "$(git rev-parse --verify HEAD)" = "$outcomes_base"
JAVA_HOME="$outcomes_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$outcomes_settings" -s "$outcomes_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$outcomes_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-process-owned-agent-outcomes-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$outcomes_core"
outcomes_ps=stellar-enclosure-tianji-process-server
printf '%s  %s\n' '18249596bade8cae37e6b13e493f50be6127ac22082ea6024dfada2e573f9653' "$outcomes_root/packaged-proof.py" | shasum -a 256 -c -
python3.14 "$outcomes_root/packaged-proof.py" "$outcomes_ps/target/stellar-enclosure-tianji-process-server-exec.jar" \
  "/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/task-completion-sla/root-process-server-manifest.json" "$outcomes_ps/src/main/resources/META-INF/tianji/openapi/process-agent-outcomes-v1.json" "$outcomes_base"
```

The exact prior raw PaC 02a9063486470118001be900491d44c07d24e4fee7b0cb33ddd2a575e0040f74 is captured by the completed SLA artifact before this source Step. The new actual ZIP checks current archive digest, unique root PaC and OAS, exact closed operation and unchanged prior Process correlation OAS presence, default false YAML, one protected GET/HEAD with existing READ and scope, one exact label and no other change except current artifact build metadata. No application is started. After S1, keep actual evidence without packaging the scratch scripts or reports.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git add -- \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesControllerTest.java \
  stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java \
  stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/GetProcessAgentOutcomesQuery.java \
  stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessAgentOutcomePageDTO.java \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQueryTest.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/ProcessLinkedAgentOutcomeQuery.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentResultInbox.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessAgentOutcomesController.java \
  stellar-enclosure-tianji-process-server/src/main/resources/application.yml \
  stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-agent-outcomes-v1.json \
  docs/egon/spec/2026-11-05-22-41-process-owned-agent-outcomes.md \
  docs/egon/plan/2026-11-05-22-42-process-owned-agent-outcomes.md \
  docs/egon/evidence/2026-11-05-process-owned-agent-outcomes.md
git diff --cached --check
git diff --cached --name-status
```

The immutable scope command prints nothing. Review each source diff, exact 16 paths and checked provenance, then commit once and verify resulting path list and clean state.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration, runtime grant or existing contract change. Explicit enable requires Process API/Inbox flags and current bindings/readiness. Scope is default false and source can revert independently.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/9 | Step 1 | 1–4/9–13 | R1/G1/P1 |
| REQ-002 | §4/7.3 | Step 1 | 5–8 | R2/G1 |
| REQ-003 | §4/7.3/9 | Step 1 | 2–6 | R2/G1 |
| REQ-004 | §4/14/16 | Step 1 | 6/9–16 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Large scope and missing known bindings explicitly refuse. Concurrent inserts use fresh snapshots and exact anchor membership. Closed proof omits remote identity and text. New grant/current source rollout, real I/E, cross-owner navigation and S12 remain separately authorized.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Source answers one bounded current Process-owner question using durable evidence independent of sampling.

### 12.2 Spec consistency

Existing persisted identities, one server-owned source, scope cap, full event/audit match and existing error contracts trace to code and tests.

### 12.3 Repository executability

Exact file paths, real owning tests, offline build and static archive checks. Use the two reviewed standalone configuration hunks rebased to the prerequisite SLA commit.

### 12.4 Test and release completeness

R1/R2 precede B. G1 and P1 verify exact finite data and actual producer shape. S1 records immutable and selected scopes.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | Existing C/PS boundary | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | Existing permission/JDBC/receipt adapters | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | Exact Query/DTO/adapter symbols | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | Current authority, query, exact cursor | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | Immutable C value DTOs | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | No mapping layer or PO | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | No new log/diagnostic echo | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Named owner query and qualified technical injection | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing JDK and length-safe digest | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 | Closed wire/OAS and exact errors | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | UTC microsecond/DB clock | None |
| MC-CONFIG-001 | Applicable | PASS | §8/16 | One owner-only default-false property, existing settings remain | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Existing owner query adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | One exact route, zero schema/trace scope | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Finite plus actual package and separate I | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Source review, runtime separate | None |

### 12.6 Final verdict

PASS — Ready for user review
