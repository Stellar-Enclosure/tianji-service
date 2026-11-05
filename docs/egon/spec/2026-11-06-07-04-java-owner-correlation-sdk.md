# T4-02 Java SDK 所有者关联观察规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Three existing bounded owned GET response contracts and a non-listening Java transport test |
| Created | 2026-11-06 07:04 Asia/Shanghai |
| Updated | 2026-11-06 07:04 Asia/Shanghai |
| Owner | Tianji Java SDK |
| Repository | tianji-service |
| Scope | Two Java contract capabilities, one codec, two existing adapters and one finite test |
| Change Surface | Contract/client and review evidence |
| Affected Chapters | §7, §8, §9, §14 |
| Source Requirement | [主计划 §19 T4-02 和 §20 S12](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | a0b7884ef1a8574f219101d4b66f0f8c36a0c52a |
| Amends | None |
| Supersedes | None |
| Depends On | [Agent correlation](2026-11-05-21-52-owned-agent-correlation-filter.md#73-detailed-design), [Process correlation](2026-11-05-20-36-owned-process-correlation-lookup.md#73-detailed-design), [Process Agent outcomes](2026-11-05-22-41-process-owned-agent-outcomes.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Owner observation SDK](../plan/2026-11-06-07-05-java-owner-correlation-sdk.md) |

## 1. Summary

Java SDK 复用已发布的 Agent 精确关联 LIST、Process START 回执关联 LIST 和单实例 Agent outcome GET，直接返回现有有限 DTO。三个读取只接受当前服务凭据和已发布的封闭响应。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentRunController#list, AgentLedgerService#correlationPage and agent-run-correlation-v1.json | Existing Agent LIST has separate exact correlation branch, four allowed query keys and at most 20 owned runs |
| EVD-002 | ProcessController#correlations, ProcessExecutionService#listByCorrelation and process-instance-correlation-v1.json | Existing Process receipt lookup repeats the exact correlation and reports a primary observation time |
| EVD-003 | ProcessAgentOutcomesController#outcomes, ProcessLinkedAgentOutcomeQuery#list and process-agent-outcomes-v1.json | Existing Process owner instance path returns local outcomes; one activity can represent multiple occurrences |
| EVD-004 | HttpAgentClient#list, HttpProcessClient#getStartReceipt and BoundedBodySubscriber | Current Java clients provide bearer transport and exact specialized decoding but expose none of these three observation methods |
| EVD-005 | AgentApiErrors, ProcessApiErrors and ProcessSecurityConfiguration | Current MVC and exact protected Process filter errors carry null correlationId. Code-only or malformed intermediary responses fall outside the two Process OAS error objects |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing Java SDK and contract modules receive a scoped client addition.

## 3. Goals and Non-goals

Enable typed owner-scoped observations for an S12 operator using the current service APIs. Live identity, grants, HTTPS, stores and telemetry remain independent operator proof.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Java contract | Affected | EVD-001–004 | Two opt-in typed capability interfaces using existing query and page records | Signatures | §7, §9 |
| Java HTTP SDK | Affected | EVD-001–005 | Existing adapters expose three closed GET paths | Wire and failure contract | §7, §8, §9, §14 |
| AS/PS wire APIs and access | Context-only | EVD-001–005 | Existing service routes, permissions and contracts | Source grounding | §9/16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Three exact typed owned GET calls | Must | Existing request records and DTOs, exact encoded path and query, optional cursor, bearer, GET/no body | EVD-001–004 |
| REQ-002 | Bounded strict observation decode | Must | Exact HTTP and JSON, matching Process echo, fixed page cap and half-open time, closed sanitized error | EVD-001–005 |
| REQ-003 | Preserve existing clients and separate operator proof | Must | Ordinary Agent list and previous Process START receipt remain covered; no live acceptance implied | EVD-004/005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | S12 operator | Existing current service credentials and configured exact owners |
| ACTOR-002 | Java SDK reviewer | In-process fake transport with shipped contract fixtures |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Observe owner evidence | ACTOR-001 | Exact correlation/window or Process instance | Typed SDK sends the exact GET and returns a closed bounded page | Scope, shape or transport failure returns a sanitized error | Existing owner state read only | REQ-001/002 |
| UC-002 | Review SDK changes | ACTOR-002 | Locked baseline | R1 and G1 exercise only finite code | Inexact results fail the source gate | Source evidence stays separate from I1 | REQ-002/003 |

## 5. Constraints, Assumptions, and Decisions

Use normalized HTTPS or explicit loopback fixture, one existing bearer supplier per request, no redirects and no retries. The current server derives ownership. Do not expose raw error fields or use an empty page or 404 as authority to write.

## 6. Project Technology Context

JDK 25 HTTP client, the current Jackson 3 dependency and JUnit. The engine-neutral contract continues to expose immutable Query and DTO records.

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
| Rule 1 | Yes | EVD-001–005 | Use the existing contract and client packages and semantic capability/codec names. | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–005 | Reuse typed query/DTO constructor boundaries and add exact wire validation at transport. | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–005 | Reuse immutable DTO records. New types are transport capabilities and one non-model codec. | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–005 | No new business class, Spring bean, injection or logging. | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–005 | Reuse JDK URI/charset/time and existing bounded body subscriber. | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–005 | Use existing Jackson 3 and enum text with exact scalar and object shape. | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–005 | No environment configuration changes. | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–005 | Reuse the HTTP Adapter and direct closed codec. | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–005 | Use Instant and a precise half-open UTC window, with Duration for existing timeout. | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–005 | Retain the established contract/client split. No database or Event path is affected. | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive, createdAt, submittedAt and Process correlation observedAt | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Inclusive lower, exclusive upper; maximum 31-day query | Microseconds; caller supplies bounds; Process clock witnesses observedAt | Reuse existing correlation queries and page | Step 1 TEST-001/002 |
| TIME-002 | Agent deadlineAt/finishedAt and Process outcome timestamps | Occurred instant | java.time.Instant and OffsetDateTime decode | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Existing Process outcome temporal order via DTO constructor | Up to nanoseconds within the shipped format | Existing DTO and owner projection preserved | Step 1 TEST-001/002 |
| TIME-003 | Existing request timeout | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Existing request deadline and cancellation | Existing JDK transport | Existing constructor policy preserved | Step 1 TEST-001 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Two capability interfaces | Add | REQ-001/003 | Existing AgentClient and ProcessClient | They expose no exact owner observations | Two small interfaces | Use |
| Existing HTTP adapters and one codec | Modify and add | REQ-001/002 | Ordinary generic client decode | Cannot validate the owner routes' closed shapes and echoes | One scoped codec and local routing | Use |
| One in-memory transport test | Add | REQ-002/003 | Current Process receipt regression | No transport witness for three methods | One finite class | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| R1/G1 | None | Actual SDK with fake HTTP client | Finite reports only | Invalid response or failed assertion |
| Independent I1 | Existing AS/PS endpoints | Operator-provided current owners and service endpoints | Actual protected calls | Any missing owner, denied scope or invalid response |

### 7.1 System Architecture Design

HttpAgentClient implements AgentRunCorrelationClient. HttpProcessClient implements ProcessOwnerObservationClient. Both reuse their existing request/response transport and select the owner codec only for these reads.

### 7.2 High-Level Design

Encode exact path and query values, bound and validate the response, then return the existing DTO. Keep the Agent response's lack of correlation echo and the Process outcomes projection's lack of occurrence identifier.

### 7.3 Detailed Design

Require one application/json content type and Cache-Control no-store. Accept HTTP 200 for page responses. Decode strict UTF-8 and closed JSON with a 262144-byte streaming limit, nesting/field/string/number/token bounds, duplicate key and trailing token rejection. Cap items at 20 and validate continuation shape and progress. Correlation pages require half-open window and descending time/ID, with PostgreSQL C UTF-8 byte tie-breaking. Process pages must echo the correlation or instance exactly. Preserve repeated outcome activity rows. Validate known status/error code pairs and return sanitized exception data, with received HTTP status on malformed envelopes.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRunCorrelationClient.java` |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessOwnerObservationClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/OwnerObservationResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-java-owner-correlation-sdk.md` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Capability | Java operation | Existing GET | Exact inputs | Output |
| --- | --- | --- | --- | --- | --- |
| INTERNAL-001 | AgentRunCorrelationClient | listByCorrelation(ListAgentRunsByCorrelationQuery) | /api/tianji/v1/agent-runs | correlationId, fromInclusive, toExclusive, optional cursor | AgentRunPageDTO |
| INTERNAL-002 | ProcessOwnerObservationClient | listByCorrelation(ListProcessInstancesByCorrelationQuery) | /api/tianji/v1/process-instance-correlations | correlationId, fromInclusive, toExclusive, optional cursor | ProcessInstanceCorrelationPageDTO |
| INTERNAL-003 | ProcessOwnerObservationClient | getAgentOutcomes(GetProcessAgentOutcomesQuery) | /api/tianji/v1/process-instances/{instanceId}/agent-outcomes | instanceId path and optional cursor | ProcessAgentOutcomePageDTO |

### 9.2 Per-interface Detailed Contracts

#### INTERNAL-001 — Agent exact correlation

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a Java consumer method for an existing owner read. |
| Independent consumer goal | Agent exact correlation in one owner observation. |
| Parameter ownership and derivation | Explicit typed caller inputs and service-derived owner. |
| Direct/no-new-interface alternative | Ordinary SDK methods do not provide this exact projection. |
| Caller use of result | Read only the explicit window and follow an opaque cursor with the same filters. |
| Round trips and failure points | One bounded GET and one strict response decode. |
| Verdict | Extend the existing HTTP Adapter with an additive typed capability. |

##### Identity and purpose

AgentRunCorrelationClient#listByCorrelation consumes the existing Agent LIST correlation branch, with its exact existing permission and OAS.

##### Request parameters

correlationId, fromInclusive, toExclusive and optional cursor. The server supplies current owner.

##### Success response

Require HTTP 200, application/json and no-store. Return AgentRunPageDTO with at most 20 summaries in the requested half-open window. The response has no correlation echo.

##### Error responses

Recognize the shipped status and code pairs. The outcomes route also recognizes its exact owned 404. Invalid responses retain the HTTP status and return TIANJI_INVALID_RESPONSE.

##### Interface logic for frontend and consumers

Read only the explicit window and follow an opaque cursor with the same filters.

##### Compatibility and verification

The additive capability leaves the existing Java interfaces unchanged. TEST-001 and TEST-002 verify this exact request and closed response with the actual Java Adapter.

#### INTERNAL-002 — Process exact correlation

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a Java consumer method for an existing owner read. |
| Independent consumer goal | Process exact correlation in one owner observation. |
| Parameter ownership and derivation | Explicit typed caller inputs and service-derived owner. |
| Direct/no-new-interface alternative | Ordinary SDK methods do not provide this exact projection. |
| Caller use of result | Use the receipt references only as evidence from the owning Process source. |
| Round trips and failure points | One bounded GET and one strict response decode. |
| Verdict | Extend the existing HTTP Adapter with an additive typed capability. |

##### Identity and purpose

ProcessOwnerObservationClient#listByCorrelation consumes the existing Process START receipt correlation GET, with its exact existing permission and OAS.

##### Request parameters

correlationId, fromInclusive, toExclusive and optional cursor. The server supplies current owner.

##### Success response

Require HTTP 200, application/json and no-store. Return ProcessInstanceCorrelationPageDTO with exact echo, covering observation time and descending receipt order.

##### Error responses

Recognize the shipped status and code pairs. The outcomes route also recognizes its exact owned 404. Invalid responses retain the HTTP status and return TIANJI_INVALID_RESPONSE.

##### Interface logic for frontend and consumers

Use the receipt references only as evidence from the owning Process source.

##### Compatibility and verification

The additive capability leaves the existing Java interfaces unchanged. TEST-001 and TEST-002 verify this exact request and closed response with the actual Java Adapter.

#### INTERNAL-003 — Process Agent outcomes

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a Java consumer method for an existing owner read. |
| Independent consumer goal | Process Agent outcomes in one owner observation. |
| Parameter ownership and derivation | Explicit typed caller inputs and service-derived owner. |
| Direct/no-new-interface alternative | Ordinary SDK methods do not provide this exact projection. |
| Caller use of result | Interpret dispatch and Inbox states with the observed terminal status. Keep missing or denied evidence inconclusive. |
| Round trips and failure points | One bounded GET and one strict response decode. |
| Verdict | Extend the existing HTTP Adapter with an additive typed capability. |

##### Identity and purpose

ProcessOwnerObservationClient#getAgentOutcomes consumes the existing Process instance Agent outcomes GET, with its exact existing permission and OAS.

##### Request parameters

An encoded exact instanceId path segment and optional cursor. The server supplies current owner.

##### Success response

Require HTTP 200, application/json and no-store. Return ProcessAgentOutcomePageDTO with exact instance echo. Preserve repeated activity identifiers and normalize offset date-times to Instant.

##### Error responses

Recognize the shipped status and code pairs. The outcomes route also recognizes its exact owned 404. Invalid responses retain the HTTP status and return TIANJI_INVALID_RESPONSE.

##### Interface logic for frontend and consumers

Interpret dispatch and Inbox states with the observed terminal status. Keep missing or denied evidence inconclusive.

##### Compatibility and verification

The additive capability leaves the existing Java interfaces unchanged. TEST-001 and TEST-002 verify this exact request and closed response with the actual Java Adapter.

## 10. POJO and Data Model Design

Use the existing exact query/page record models and enum values. One package-private codec validates types before constructing those records.

## 11. Database Design

Relational model change: No. Java SDK reads existing server evidence through its published GET.

## 12. Frontend Page Design

Java SDK scope. Existing frontend clients continue to use their own contracts.

## 13. Design Patterns and Architecture Principles

Reuse the existing HTTP Adapter for authorization transport, deadline and bounded streaming. A shared closed codec holds only the wire boundary the three protected GETs have in common. Avoid a new service layer or any change to existing client interfaces.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | In-memory FakeHttpClient proves exact routes, params and headers, typed normal pages, full cursors, streaming cap, timeout cancellation and no redirect. |
| TEST-002 | Wrong Process echo, invalid windows, shape, scalar type, UTF-8, duplicates, order and errors reject. Remove only the correlation echo predicate to obtain the expected RED. |
| TEST-003 | Ordinary Agent LIST works via its original path; all six ProcessStartReceiptHttpTest cases remain green. |

## 15. Non-functional and Cross-cutting Design

No raw wire error, correlation, credential or retry detail is propagated through these specialized exceptions. The SDK performs one bounded read in each call.

## 16. Compatibility, Migration, Rollout, and Rollback

Capability interfaces are additive. The ordinary Agent and Process methods and all server OpenAPI, permissions, migration and configuration files remain compatible. Revert one focused commit to remove the three Java read methods.

## 17. Alternatives and Decisions

The existing generic HTTP decoders do not enforce these fixed projections. A dedicated shared codec on the existing transports preserves strict fields and ordinary client behavior.

## 18. Risks and Open Questions

Process OAS specifies the two-field error object. Current exact Process owner-read routes normalize their security fallback. A malformed code-only 401/403 from an intermediary retains its status as TIANJI_INVALID_RESPONSE in this SDK. Actual protected IAM/HTTPS, owner primary observations and Collector evidence are separate S12 acceptance.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9 | TEST-001 |
| REQ-002 | UC-001/002 | §5/7/14 | Step 1 TEST-001/002 |
| REQ-003 | UC-002 | §8/16/18 | TEST-003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Keep contract interfaces and the existing JDK HTTP client module boundary. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Use existing routes, typed queries, DTO constructors, bearer supplier and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use existing JDK, Jackson 3 and JUnit dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Use transport capability interfaces, a package-local codec and an exact test class. | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Use query constructors and a closed media, header, JSON, owner echo and time validation boundary. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Return the existing immutable records and enum values. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No new layer mapping or business Converter. The codec decodes an exact wire object. | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new log or retained remote error data. | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | No new Spring bean or dependency injection. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Use JDK URI, UTF-8, java.time and existing Jackson codecs. | None |
| MC-JSON-001 | Applicable | PASS | §7/9/14 | Use exact shape, type, depth, number, token and byte bounds. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Use java.time UTC microsecond correlation windows with half-open boundary. | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration file or feature flag change. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse existing Adapter and direct validation of these three closed responses. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Only six source paths and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Test fake transport behavior and Process correlation echo mutation. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual protected S12 runtime evidence is independently pending. | None |

### 20.6 Final verdict

PASS — Ready for user review
