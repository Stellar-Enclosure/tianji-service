# T4-03 Java SDK 受保护的 Agent 恢复命令规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Two protected POST commands, durable acknowledgement semantics and bounded Java transport |
| Created | 2026-11-06 07:26 Asia/Shanghai |
| Updated | 2026-11-06 07:26 Asia/Shanghai |
| Owner | Tianji Java SDK |
| Repository | tianji-service |
| Scope | Optional command interface, existing Agent Adapter, one strict codec and one finite test class |
| Change Surface | Contract/client and review evidence |
| Affected Chapters | §7, §8, §9, §14 |
| Source Requirement | [主计划 §19 T4-03 和 §20 S11](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 7c83b4254d0988d400824268d6ce38ab8725b6c5 |
| Amends | None |
| Supersedes | None |
| Depends On | [Resume](2026-11-06-06-48-protected-checkpoint-resume.md#73-detailed-design), [Historical receipt](2026-11-06-06-55-protected-historical-start-receipt.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Agent recovery SDK](../plan/2026-11-06-07-27-agent-recovery-command-sdk.md) |

## 1. Summary

Java SDK 为已发布的 checkpoint RESUME 和历史 START 收据对账提供两个有类型的 USER POST，验证精确回显和有界响应。每次调用只发送一个请求。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentCheckpointResumeController and agent-checkpoint-resume-v1.json | Existing protected RESUME returns original durable ACK including expectedRevision + 2. |
| EVD-002 | AgentHistoricalStartReceiptController and agent-historical-start-receipt-v1.json | Existing protected reconciliation returns only exact positive receipt or a bounded failure. |
| EVD-003 | HttpAgentClient, OwnerObservationResponseCodec and BoundedBodySubscriber | Existing Agent transport handles bearer, deadlines, bounded response and exact GETs. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing Java SDK and contract modules receive a scoped capability.

## 3. Goals and Non-goals

Expose the existing protected commands through Java and preserve the distinction between durable takeover acknowledgement and completed run, and between positive historical evidence and an unknown outcome.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Java contract | Affected | EVD-001–003 | Optional typed capability | Command and result signatures | §7, §8, §9 |
| Java HTTP SDK | Affected | EVD-001–003 | Bounded POST and closed decoding | Wire and failures | §7, §9, §14 |
| Server permissions and storage | Context-only | EVD-001/002 | Published services retain existing responsibility | Existing scope | §9/16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Two typed USER commands | Must | One exact POST with current bearer and closed request per call | EVD-001/002 |
| REQ-002 | Strict bounded response | Must | Verify exact status, headers, ACK revision and receipt identity, clocks and errors | EVD-001–003 |
| REQ-003 | Preserve existing Java SDK | Must | Ordinary and owner observations and START receipt tests pass | EVD-003 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Java caller | Existing protected USER authority |
| ACTOR-002 | Java SDK reviewer | Finite fake transport |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Request takeover | ACTOR-001 | Exact checkpoint revision and command | One POST, validate original ACK | Transport failure leaves outcome uncertain | Validated durable acknowledgement | REQ-001/002 |
| UC-002 | Reconcile historical START | ACTOR-001 | Exact run and intent | One POST, decode positive evidence | Published unknown remains visible | Closed response or sanitized error | REQ-001/002 |
| UC-003 | Verify transport | ACTOR-002 | Locked source | Exact R1 and G1 | Mismatched ACK fails | Finite source evidence | REQ-002/003 |

## 5. Constraints, Assumptions, and Decisions

Use the existing secure normalized origin check, caller-supplied bearer and deadline. A timeout makes an outcome uncertain. Automatic retries and redirects are disabled at this transport boundary.

## 6. Project Technology Context

JDK 25, Jackson 3, the current engine-neutral contract and JUnit.

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
| Rule 1 | Yes | EVD-001–003 | Use existing Agent Java SDK package names and command types. | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–003 | Validate request identifiers, fixed ACK revision, receipt echoes and bounded JSON. | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–003 | Use immutable command and response records. | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–003 | Outside the SDK change | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–003 | Use JDK URI, charset, UUID and java.time with existing SDK helpers. | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–003 | Reject unexpected fields, duplicate keys, trailing tokens and malformed UTF-8. | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–003 | Outside the SDK change | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–003 | Reuse the HTTP Adapter with a package-private closed codec. | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–003 | Use UTC occurrence instants for independent AS and PS clocks. | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–003 | Keep engine-neutral records and the existing JDK HTTP Adapter boundary. | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | recordedAt, observedAt | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Reject out-of-range clock fields and offsets. No cross-clock order requirement. | Up to nanoseconds. Independent AS and PS clocks. | Existing historical response fields | Step 1 TEST-002 |
| TIME-002 | Existing request timeout | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Existing deadline and cancellation | JDK HTTP | Existing client policy | Step 1 TEST-001 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| AgentRecoveryCommandClient | Add | REQ-001/003 | Existing AgentClient | Separate protected commands absent | One optional interface | Use |
| Existing HTTP Adapter and one codec | Modify and add | REQ-001/002 | Generic decoder | Cannot bind route-specific receipt and ACK | One closed decoder | Use |
| Existing finite transport tests | Extend | REQ-002/003 | Current owner observations | POST and uncertain response coverage missing | Seven cases | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| R1/G1 | None | Actual SDK with fake HttpClient | Test reports | Closed response predicates |
| Independent I1 | Existing protected AS endpoints | Operator-provided owner | Actual AS/PS and database proof | IAM, HTTPS or stored outcome |

### 7.1 System Architecture Design

HttpAgentClient implements AgentRecoveryCommandClient and selects the package-private codec for these two POST commands.

### 7.2 High-Level Design

Validate input and origin, encode the fixed body, perform one bounded request, validate headers and JSON and construct an immutable result.

### 7.3 Detailed Design

Bound serialized RESUME to 4096 bytes and historical receipt request to 512 bytes. Bound the streamed response to 16384 bytes. Require exact JSON fields and types, strict UTF-8, duplicate and trailing-token rejection, HTTP 200 on success and no-store. Bind ACK revision to the input and validate the echoed run and intent. Parse UTC instants with independent AS and PS clocks. Report only known route-specific error codes and the received status.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryCommandClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentRecoveryCommandResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-recovery-command-sdk.md` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Capability | Java operation | Existing POST | Exact inputs | Output |
| --- | --- | --- | --- | --- | --- |
| INTERNAL-001 | AgentRecoveryCommandClient | resumeCheckpoint | /api/tianji/v1/agent-checkpoints/resume | runId, commandId, expectedRevision | CheckpointResumeAcknowledgement |
| INTERNAL-002 | AgentRecoveryCommandClient | reconcileHistoricalStart | /api/tianji/v1/agent-checkpoints/reconcile-start-receipt | runId, intentId | HistoricalStartReceipt |

### 9.2 Per-interface Detailed Contracts

#### INTERNAL-001 — Checkpoint resume

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a Java consumer method for an existing protected USER POST. |
| Independent consumer goal | Checkpoint resume. |
| Parameter ownership and derivation | Typed explicit command with server-derived owner. |
| Direct/no-new-interface alternative | Ordinary AgentClient has no equivalent command. |
| Caller use of result | Preserve the exact command identity and expected revision for an explicit replay when the caller resolves a lost response. Acknowledgement reports the durable takeover. |
| Round trips and failure points | One bounded POST. A transport failure can leave the outcome uncertain. |
| Verdict | Extend the existing HTTP Adapter through an additive typed capability. |

##### Identity and purpose

AgentRecoveryCommandClient#resumeCheckpoint(CheckpointResumeCommand) consumes `POST /api/tianji/v1/agent-checkpoints/resume` using the current caller bearer and the published dedicated permission.

##### Request parameters

runId, commandId and expectedRevision. Bound expectedRevision to the nonnegative range that can add two.

##### Success response

Require HTTP 200, application/json and no-store. Return the published closed response. An exact runId echo, RUNNING, attempt 1 and revision equal to expectedRevision + 2.

##### Error responses

Only this route recognizes the published HTTP 429 capacity response. Malformed envelopes retain the HTTP status with TIANJI_INVALID_RESPONSE and omit raw server text.

##### Interface logic for frontend and consumers

Preserve the exact command identity and expected revision for an explicit replay when the caller resolves a lost response. Acknowledgement reports the durable takeover.

##### Compatibility and verification

The optional capability retains the existing AgentClient surface. TEST-001–003 verify the actual SDK without a listener.

#### INTERNAL-002 — Historical START receipt

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a Java consumer method for an existing protected USER POST. |
| Independent consumer goal | Historical START receipt. |
| Parameter ownership and derivation | Typed explicit command with server-derived owner. |
| Direct/no-new-interface alternative | Ordinary AgentClient has no equivalent command. |
| Caller use of result | Treat only the typed success as positive evidence. Surface the published HTTP 409 TIANJI_SIDE_EFFECT_UNKNOWN to the caller. |
| Round trips and failure points | One bounded POST. A transport failure can leave the outcome uncertain. |
| Verdict | Extend the existing HTTP Adapter through an additive typed capability. |

##### Identity and purpose

AgentRecoveryCommandClient#reconcileHistoricalStart(HistoricalStartReceiptCommand) consumes `POST /api/tianji/v1/agent-checkpoints/reconcile-start-receipt` using the current caller bearer and the published dedicated permission.

##### Request parameters

runId and canonical typed intentId UUID.

##### Success response

Require HTTP 200, application/json and no-store. Return the published closed response. An exact dispatchRunId and intentId echo, valid instanceId, and UTC recordedAt and observedAt instants.

##### Error responses

This route recognizes the historical HTTP 409 unknown outcome with its exact error code. Malformed envelopes retain the HTTP status with TIANJI_INVALID_RESPONSE and omit raw server text.

##### Interface logic for frontend and consumers

Treat only the typed success as positive evidence. Surface the published HTTP 409 TIANJI_SIDE_EFFECT_UNKNOWN to the caller.

##### Compatibility and verification

The optional capability retains the existing AgentClient surface. TEST-001–003 verify the actual SDK without a listener.

## 10. POJO and Data Model Design

Two immutable command records and two result records validate identifiers, revision, status and required values. Their string representations redact command and receipt data.

## 11. Database Design

Relational model change: No. The deployed AS and PS remain responsible for durable evidence.

## 12. Frontend Page Design

Java SDK scope. Existing frontend contracts apply independently.

## 13. Design Patterns and Architecture Principles

Reuse the existing HTTP Adapter to centralize the bearer, deadline and bounded streaming. A direct closed codec validates the distinct result contracts. The typed capability is additive to existing Java consumers.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | FakeHttpClient exercises exact POSTs, bearer, timeout with one request, origin and body cap. |
| TEST-002 | Reject malformed results, wrong ACK revision, receipt identity and invalid times. Preserve known UNKNOWN. Removing only exact revision equality exposes the ACK regression. |
| TEST-003 | Preserve all existing owner observations and the six Process START receipt tests. |

## 15. Non-functional and Cross-cutting Design

Bound request and response size. Propagate only sanitized status/code data on an invalid or rejected command. A timeout leaves resolution to the caller.

## 16. Compatibility, Migration, Rollout, and Rollback

Add one optional Java capability. Existing service permissions, migrations and wire contracts remain compatible. Revert the focused SDK commit to remove the capability.

## 17. Alternatives and Decisions

Existing generic response decoding lacks exact request binding. The package-private command codec provides this boundary while the transport stays shared.

## 18. Risks and Open Questions

SDK validation is finite. Independent operator acceptance must exercise actual permissions, HTTPS and durable effects. The acknowledged attempt is a persisted takeover and later state requires an independent read.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001, UC-002 | §7/9 | TEST-001 |
| REQ-002 | UC-001/002/003 | §7/9/14 | TEST-001/002 |
| REQ-003 | UC-003 | §8/16 | TEST-003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/18 | Keep engine-neutral records and the existing JDK HTTP Adapter boundary. | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/18 | Reuse existing USER routes, bearer supplier, deadline and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/18 | Reuse JDK, Jackson 3 and JUnit. | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/18 | Use existing Agent Java SDK package names and command types. | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/18 | Validate request identifiers, fixed ACK revision, receipt echoes and bounded JSON. | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/18 | Use immutable command and response records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/18 | Use JDK URI, charset, UUID and java.time with existing SDK helpers. | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/18 | Reject unexpected fields, duplicate keys, trailing tokens and malformed UTF-8. | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/18 | Use UTC occurrence instants for independent AS and PS clocks. | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/18 | Reuse the HTTP Adapter with a package-private closed codec. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/18 | Keep changes to four source files and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/18 | Run a revision counterexample and finite SDK regression. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/18 | Keep live IAM, protected AS/PS and PostgreSQL proof pending. | None |

### 20.6 Final verdict

PASS — Ready for user review
