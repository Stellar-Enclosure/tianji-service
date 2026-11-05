# T4-03 精确 Process START 收据 Java SDK 规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One Java Query using the existing closed, owner-scoped REST contract |
| Created | 2026-11-06 05:05 Asia/Shanghai |
| Updated | 2026-11-06 05:05 Asia/Shanghai |
| Owner | Tianji contract and client |
| Repository | tianji-service |
| Scope | Four Java files and three review documents |
| Change Surface | Independent receipt client port, exact GET adapter and finite protocol test |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [Master §17 S11 and §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 52dd82be9b4831c5e3412bf63f0e2c279a0c7dfe |
| Amends | None |
| Supersedes | None |
| Depends On | [Existing Process receipt protocol §9.2](2026-11-05-23-50-owned-process-start-receipt.md#92-per-interface-detailed-contracts) |
| Related Specs | [Existing Process receipt](2026-11-05-23-50-owned-process-start-receipt.md) |
| Related Plans | [Java SDK plan](../plan/2026-11-06-05-06-process-start-receipt-sdk.md) |

## 1. Summary

为 Java 宿主补上现有 Process START 精确收据查询。由调用者提供当前收据 owner 的既有 Process READ 凭据，按原命令 ID 和独立请求摘要读取已应用证据。未匹配收据保留为无定论，不能据此再次提交 START。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ProcessStartReceiptController#receipt and ProcessExecutionService#getStartReceipt | Existing default-off exact GET uses current Process READ and full owner; exact applied rows yield a closed DTO. |
| EVD-002 | process-start-receipt-v1.json, GetProcessStartReceiptQuery and ProcessStartReceiptDTO | Existing OAS defines two query values, exact statuses, no-store and UTC micros. |
| EVD-003 | HttpProcessClient#call, BoundedBodySubscriber and ProcessWorkerRecoveryResponseCodec | SDK already has bounded single-call mechanics and a closed response precedent; owned receipt has no Java adapter. |
| EVD-004 | TianjiFixedBpmnStartToolTransport and ToolResultAccessStore | START credential has release READ plus START. Local RECONCILE disclosure requires an already known result and cannot establish an external receipt after lease loss. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Add a port and transport to established modules. No archetype or generated example is in scope.

## 3. Goals and Non-goals

Add a callable existing-owner Java receipt transport. Its caller supplies authentication. Production Agent reconciliation, current historical action policy, receipt promotion and real IAM validation are separate steps.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Contract and Java SDK | Affected | EVD-002/003 | Receipt-specific port and exact HTTP read | Design, wire evidence and finite tests | §7, §8, §9, §10, §14, §15, §16 |
| Process API and ledger | Context-only | EVD-001/002 | Existing owner and applied receipt are the authority | Existing contract and persistence context | §9/11 |
| Agent execution grants and frontend | Unchanged | EVD-004 | Existing AS credential and Web path | Scope boundaries | §12/16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Call the exact owned receipt Query | Must | Single GET and no body; encoded original command and lowercase hash only; supplied Bearer and no-store; normalized HTTPS or loopback fixture | EVD-001/002/003 |
| REQ-002 | Require closed positive evidence | Must | Exact 200 JSON and no-store; APPLIED with valid instance and UTC microsecond time; extra, duplicate, invalid or oversized content fails closed | EVD-002 |
| REQ-003 | Preserve inconclusive failure and scope | Must | Exact documented error pairs keep status and safe code; malformed status/body combinations fail with raw status and INVALID_RESPONSE; no resend, fallback, state change or new grant | EVD-001–004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Trusted Java caller | Supplies current existing Process READ credential of the original owner and exact digest |
| ACTOR-002 | Process Server | Existing guard and durable APPLIED receipt owner |
| ACTOR-003 | Source reviewer | Runs offline finite tests, wire and package audit |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Check one exact START | ACTOR-001/002 | Current owner credential and matching known original digest | Send one exact GET, validate positive evidence | 404, auth errors and malformed replies produce no positive result | Existing receipt only, no action mutation | REQ-001/002/003 |
| UC-002 | Review SDK transport | ACTOR-003 | Exact source revision and local JDK25 | Typed RED, finite GREEN, package boundary and exact scope | Missing or unrelated assertion fails gate | Source-level evidence | REQ-001/002/003 |

## 5. Constraints, Assumptions, and Decisions

Use the existing pinned Process endpoint and OAS. Caller provides a current Bearer under the existing owner/READ contract. A validated query or digest never grants authority. The ordinary AS START credential remains unchanged. Use 4 KiB response buffering and the existing timeout, cancellation and redirect boundaries.

## 6. Project Technology Context

JDK25 HTTP client, the existing engine-neutral contract, Jackson 3, JUnit and existing Process REST OAS. The production Process endpoint is an independently enabled owner-specific read.

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
| Rule 1 | Yes | EVD-001–004 | Reuse existing Query and DTO with a named client port. | §7/8/9 | TEST-001/002 | PASS |
| Rule 2 | Yes | EVD-001–004 | Validate closed request and response through existing records and wire checks. | §7/8/9 | TEST-001/002 | PASS |
| Rule 3 | Yes | EVD-001–004 | Reuse immutable value records without introducing a mutable model or converter. | §7/8/9 | TEST-001/002 | PASS |
| Rule 4 | No | EVD-001–004 | No Spring Bean, injected business dependency or logging. | §7/8/9 | TEST-001/002 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse JDK HTTP and the current Jackson transport library. | §7/8/9 | TEST-001/002 | PASS |
| Rule 6 | Yes | EVD-001–004 | Require exact scalar JSON and the APPLIED enum token. | §7/8/9 | TEST-001/002 | PASS |
| Rule 7 | No | EVD-001–004 | No runtime property or profile changes. | §7/8/9 | TEST-001/002 | N/A |
| Rule 9 | Yes | EVD-001–004 | Reuse the existing HTTP Adapter and one exact Query. | §7/8/9 | TEST-001/002 | PASS |
| Rule 10 | Yes | EVD-001–004 | Decode OAS UTC microseconds to the existing Instant field. | §7/8/9 | TEST-001/002 | PASS |
| Rule 11 | Yes | EVD-001–004 | Keep contract and client ownership, with no persistence or event change. | §7/8/9 | TEST-001/002 | PASS |


### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ProcessStartReceiptDTO.recordedAt | Occurred instant | java.time.OffsetDateTime and Instant | UTC instant | UTC Z | Explicit UTC | No SDK display | Gregorian year 0001–9999; no offset, leap second or 24:00 coercion | Up to six fractional digits from PS receipt, no local clock substituted | Existing exact receipt creation instant; no commit-clock claim | Step 1 TEST-001/002 |
| TIME-002 | HttpProcessClient.timeout | Duration | java.time.Duration and JDK timed Future | Not persisted | Internal | Not applicable: elapsed client timeout | Not applicable: no SDK display | Existing positive request deadline and timeout cancellation | Nanosecond elapsed bound | Existing client timeout semantics | Step 1 TEST-001 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy | Added cost and failure | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Independent receipt port | Add | REQ-001 | ProcessClient already supports START and instance read | Neither method can prove exact APPLIED receipt; adding a method there burdens other implementations | One interface with one Query | Use |
| HttpProcessClient and bounded subscriber | Extend/reuse | REQ-001/003 | Separate network client | Existing no-redirect, request and cancellation mechanics already fit | One closed receipt branch only | Use |
| Receipt codec | Add | REQ-002/003 | Generic mapper | Loose binding cannot enforce all exact fields, UTC grammar and error evidence | One package-private decoder | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| Receipt lookup | One existing GET | APPLIED or typed failure | Existing exact owner receipt | Invalid or absent yields no positive proof |
| Source gate | None | JDK fake transport | No DB mutations | Unexpected or skipped tests fail gate |

### 7.1 System Architecture Design

Existing contract values flow from caller through HttpProcessClient to the existing authenticated Process receipt route. Only the response codec converts the transport JSON to the existing immutable DTO. No AS runtime dependency is introduced.

### 7.2 High-Level Design

Implement a separate interface to preserve compatibility for existing ProcessClient implementations. Use a scoped branch in the existing adapter, keeping older response families on their existing policies.

### 7.3 Detailed Design

Use one GET through the caller's trusted base and current Bearer. Reject insecure non-loopback and non-normalized bases before reading credentials, and refuse any redirecting JDK HttpClient at construction. Request cache-control is no-store. Accept one application/json content type and exactly one no-store response value. Limit network body buffering to 4096 bytes and reject strict invalid UTF-8, duplicate keys, extra fields, trailing tokens and malformed values. The existing timeout cancels a pending request.

Only status 200 plus the exact three-field receipt returns a result. Status enum is APPLIED. Parse UTC using OAS year and fractional limits plus date/time validation. Errors accept only the OAS pairs with null correlation and no raw details. Missing or malformed evidence remains a safe exception preserving HTTP status where available.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptHttpTest.java` |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-process-start-receipt-sdk.md` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Purpose | Kind | Method | Owner |
| --- | --- | --- | --- | --- |
| INTERNAL-001 | Read exact applied Process START evidence from Java | Java Query | ProcessStartReceiptClient#getStartReceipt | Contract/client |

### 9.2 Per-interface Detailed Contracts

#### INTERNAL-001 — Owned Process START receipt Java Query

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add one Java consumer interface for an existing server Query |
| Independent consumer goal | Inspect positive durable evidence without invoking START |
| Parameter ownership and derivation | Original command and expected digest are bound by the trusted caller |
| Direct/no-new-interface alternative | Existing START and instance methods cannot prove exact applied receipt |
| Caller use of result | Present positive evidence to an independently authorized caller |
| Round trips and failure points | One bounded GET, typed response or fail closed |
| Verdict | Reuse the existing HTTP Adapter behind the independent port |

##### Identity and purpose

`ProcessStartReceiptClient#getStartReceipt(GetProcessStartReceiptQuery)` returns `ProcessStartReceiptDTO` or fails through the existing `ProcessClientException` semantics. It consumes the already shipped GET /api/tianji/v1/process-start-receipts and `getOwnedProcessStartReceipt` contract defined by [Process receipt protocol §9.2](2026-11-05-23-50-owned-process-start-receipt.md#92-per-interface-detailed-contracts). The SDK carries no new endpoint, request fields, response fields or auth claims.

##### Request parameters

Query names are only commandId and requestSha256. Credential identity is supplied outside the query from the original Process owner with current existing READ.

##### Success response

A verified APPLIED receipt reports instanceId, status and recordedAt. Exact HTTP 200, JSON and no-store are required before accepting the closed immutable DTO.

##### Error responses

Recognized error pairs are 400/TIANJI_INVALID_COMMAND, 401/TIANJI_UNAUTHENTICATED, 403/TIANJI_PERMISSION_DENIED, 404/TIANJI_RESOURCE_NOT_FOUND, 409/TIANJI_TENANT_CONTEXT_CHANGED and 503/TIANJI_DEPENDENCY_UNAVAILABLE. If an upstream filter provides different headers or payload, the HTTP status remains observable with TIANJI_INVALID_RESPONSE. Exact 404 is inconclusive and cannot trigger POST.

##### Interface logic for frontend and consumers

Call the Java Query once with current existing READ and the independently expected digest. Distinguish positive proof from every other response without triggering mutation or an alternate lookup.

##### Compatibility and verification

Use an additive interface and the existing Process adapter. The no-network test checks the exact encoded GET and returned evidence, and the existing Java callers retain their earlier response families.

## 10. POJO and Data Model Design

Reuse immutable GetProcessStartReceiptQuery and ProcessStartReceiptDTO. Constructor validation is authoritative for existing command, instance reference and Instant range. No new application or persisted value object.

## 11. Database Design

Context-only. Process owns APPLIED receipt storage. The Java SDK opens no transaction and writes no data.

## 12. Frontend Page Design

Context-only. Existing Web receipt lookup remains separate from this Java SDK.

## 13. Design Patterns and Architecture Principles

Extend the existing JDK HTTP Adapter for one Query. A package-private response codec owns closed decoding, using the same division as Worker Recovery. No parallel generic HTTP stack.

## 14. Test Design

| ID | Test/gate | Exact proof |
| --- | --- | --- |
| TEST-001 | ProcessStartReceiptHttpTest | Fake JDK HTTP uses exact URI, credential and body subscriber; 200, transport limits, closed shape, status failures and timeout |
| TEST-002 | Typed R1 and current neighboring suites | Removing only duplicate-key detection causes the exact missing failure; existing contract, control, worker, case and PS receipt behavior still passes |
| TEST-003 | Producer and staged scope | No POM/YAML/SQL, permission change or server contract drift; artifact contains the intended client classes |

## 15. Non-functional and Cross-cutting Design

Bound the body, timeout and one attempted request. Preserve safe status and allowlisted error code only. Request contents and supplied bearer are absent from errors and logs. A well-formed 404 leaves external effects unresolved.

## 16. Compatibility, Migration, Rollout, and Rollback

Add a Java capability to the existing client without adding methods to ProcessClient or changing pre-existing behavior. No SDK is wired by default. No dependency, migration, Process OAS, AS/PS permission or config change. Revert the focused commit to remove this SDK addition.

## 17. Alternatives and Decisions

Use the exact owner receipt Query. Instance or correlation lookup does not bind the original command payload. A retry of the mutating START can change business state on a genuine miss. Reuse the existing HTTP adapter and scope-bound supplied credential.

## 18. Risks and Open Questions

Product reconciliation still needs an independently reviewed current authority over historical intent and a current READ credential for the original executor. The first-party SDK protocol gate does not establish those permissions, deployed HTTPS or real S11.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 and UC-002 | §7/8/9 | TEST-001/002 |
| REQ-002 | UC-001 and UC-002 | §6.3/7/9/10 | TEST-001/002 |
| REQ-003 | UC-001 and UC-002 | §5/7/9/15/16/18 | TEST-001/002/003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/16 | Existing engine-neutral contract and client packages. | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/16 | Reuse exact Process route, DTOs, HttpProcessClient and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/16 | Use current JDK, Jackson 3 and JUnit without POM changes. | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/16 | Use one named receipt client port and private wire codec. | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/16 | Keep constructor query invariants and closed wire evidence. | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/16 | Retain existing immutable query and receipt records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/16 | No business model or persistence conversion. | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/16 | No new logging, response detail, query material or token in error outputs. | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/16 | The SDK port and adapter are explicitly constructed. | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/16 | Use existing JDK HTTP, URI encoding and Jackson primitives. | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/16 | Check exact closed fields, duplicate keys and typed error pairs. | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/16 | Preserve occurred UTC instant at microsecond precision. | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/16 | No product flags, defaults or profiles change. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/16 | Extend the existing HTTP Adapter for one Query. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/16 | Four Java files and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/16 | Separate finite network-free gates from external IAM acceptance. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/16 | External reconciliation policy and runtime remain separate from this SDK. | None |

### 20.6 Final verdict

PASS — Ready for user review
