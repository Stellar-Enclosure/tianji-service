# T4-02 Process owner 只读错误信封一致性

| Field | Value |
| --- | --- |
| Document | 2026-11-06-07-02-process-owner-read-error-envelope.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Fix |
| Complexity | Simple |
| Complexity Drivers | Existing closed security envelope and three exact owner read routes |
| Created | 2026-11-06 07:02 Asia/Shanghai |
| Updated | 2026-11-06 07:02 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Align early Process IAM errors with shipped owner read OAS |
| Change Surface | Existing PS filter chain, exact method/path matching and finite evidence |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [Master T4-02 and S12](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 76f5f41d07695b8743fc037df6b3ea7f7486de98 |
| Amends | None |
| Supersedes | None |
| Depends On | [Process START receipt](2026-11-05-23-50-owned-process-start-receipt.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Exact envelope implementation](../plan/2026-11-06-07-03-process-owner-read-error-envelope.md) |

## 1. Summary

对齐 Process owner 查询在 IAM 提前返回时的错误形态。三条既有查询在各自启用条件下收到与包内 OpenAPI 一致的闭合 401、403、503 信封，HEAD 保留等价响应头并省略响应体。

## 2. Background and Current State

| Evidence | Repository fact | Use |
| --- | --- | --- |
| EVD-001 | Packaged process-instance-correlation-v1.json, process-agent-outcomes-v1.json and process-start-receipt-v1.json require code and null correlationId | Existing contract |
| EVD-002 | ProcessSecurityConfiguration generic short-circuit errors emit only code; WorkerRecoverySecurityErrors already wraps exact Worker operations | Reuse normalized envelope |
| EVD-003 | ProcessController, ProcessAgentOutcomesController and ProcessStartReceiptController own the exact API and feature predicates | Exact selection |
| EVD-004 | ProcessApiErrors uses OrchestrationErrorDTO with a null correlationId for these controlled errors | Match existing MVC wire |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Correct existing Process Server security wiring and finite tests.

## 3. Goals and Non-goals

Align selected owner reads with their existing documented protocol. Keep existing protected resource ownership and runtime gates as the selection authority. Live IAM and S12 remain operator validation.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS security filter | Affected | EVD-001–004 | Selected early errors use existing closed contract | Exact path/method gate, HEAD, propagation and scope | §7, §8, §14, §15, §16 |
| Packaged OpenAPI and authorization | Context-only | EVD-001/003 | Existing route metadata and contract define desired output | Artifact parity | §9, §14 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Closed error shape for enabled owner GET and HEAD | Must | Exact 401/403/503 code mapping, null correlationId, no-store, HEAD empty entity | EVD-001–004 |
| REQ-002 | Scoped compatibility and evidence | Must | Existing exact Worker operations and all unrelated methods, paths and statuses retain their behavior; finite and package proof | EVD-002–004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Evidence |
| --- | --- | --- |
| ACTOR-001 | Current caller of an existing owner read | EVD-001/003 |
| ACTOR-002 | Existing Process IAM and MVC | EVD-002/004 |

| ID | Use case/goal | Actor | Main outcome |
| --- | --- | --- | --- |
| UC-001 | Receive documented error on enabled owner read | ACTOR-001 | Closed error with safe metadata |
| UC-002 | Preserve exact operation and gate isolation | ACTOR-002 | Worker and unrelated behavior retain established scope |

## 5. Constraints, Assumptions, and Decisions

Use only Process API enabled paths, independent START receipt and Agent Inbox plus owner-outcomes gates, and the existing paired Worker flags. Continue using current IAM and MVC. Source proof is finite and offline.

## 6. Project Technology Context

Existing PS Spring Security Servlet chain, PathPatternRequestMatcher, ContentCachingResponseWrapper and MockMvc-compatible mocks.

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
| Rule 1 | Yes | EVD-001–004 | Name the wrapper for the closed Process security envelope and use exact existing route identifiers. | §7/8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–004 | Select existing feature flags and exact method and path before normalizing the response. | §7/8 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–004 | Reuse the existing static two-member OrchestrationErrorDTO wire representation. | §7/8 | TEST-001–004 | PASS |
| Rule 4 | Yes | EVD-001–004 | Keep filter ownership in the existing explicit security chain and retain diagnostics. | §7/8 | TEST-001–004 | PASS |
| Rule 5 | Yes | EVD-001–004 | Use existing JDK collection and Spring Security matchers with no dependency change. | §7/8 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–004 | Retain the fixed safe JSON envelope already specified by the packaged OpenAPI documents. | §7/8 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–004 | Read existing feature properties and preserve their packaged values. | §7/8 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–004 | Reuse the existing security response wrapper pattern for selected owner routes. | §7/8 | TEST-001–004 | PASS |
| Rule 10 | No | EVD-001–004 | Use no new dates, durations or timestamp transformations in this transport correction. | §7/8 | TEST-001–004 | N/A |
| Rule 11 | Yes | EVD-001–004 | Keep the existing Process Controller and IAM ownership and schema contracts. | §7/8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: ProcessSecurityConfiguration.ClosedProcessSecurityErrors handles HTTP status, response bytes and headers only. TEST-001/002 cover wire behavior; existing controller time fields are forwarded without modification.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Verdict |
| --- | --- | --- | --- | --- |
| Existing wrapper selection | Extend | REQ-001/002 | Generic error varies before MVC | Reuse one wrapper and exact route matchers |
| HEAD normalization | Add branch | REQ-001 | GET wrapper writes an entity | Emit equivalent GET content length and headers |

| Path | Network calls | Client states | Failure points | Verdict |
| --- | --- | --- | --- | --- |
| Selected owner read security response | Existing one call | Existing error states | Early IAM response | Match published contract |
| Existing Worker response | Existing one call | Existing states | Existing IAM response | Keep previous matchers and guards |

### 7.1 System Architecture Design

Process security owns the exact filter selection. Controller-owned visibility, data projection and permission checks remain separate stages.

### 7.2 High-Level Design

Under the Process API gate install a wrapper before the existing identity request context. Correlation GET and HEAD are included. Add START receipt and Agent outcome GET/HEAD only for their existing feature flags. Keep Worker GET failures and POST recoveries gated by both Worker flags.

### 7.3 Detailed Design

Build an immutable list of exact PathPattern method/route matchers at filter construction. Bypass response buffering for unmatched paths. For selected responses normalize only 401, 403 and 503, preserving unrelated headers and sanitized no-store policy. HEAD uses the GET-equivalent content length and an empty entity. Forward every other status and body through the existing copy path.

## 8. Package Structure and Code File Tree

- `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`
- `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`

## 9. Interface Definitions

Existing packaged Process OpenAPI 3.1 documents remain the contract authority for correlation, Agent outcomes and START receipt. The change aligns the security-layer error serialization with those closed schemas.

## 10. POJO and Data Model Design

Use the existing two-member error envelope and static sanitized code selection. Exact route matching uses Spring PathPatternRequestMatcher.

## 11. Database Design

Source verification compares all 28 existing migration bytes. No persistence participation in the filter.

## 12. Frontend Page Design

Existing consumers may decode the already-shipped error shape consistently.

## 13. Design Patterns and Architecture Principles

Reuse the current filter wrapper and conditional matcher composition to align transport behavior with the owning HTTP contract.

## 14. Test Design

| Test | Scope and expected evidence |
| --- | --- |
| TEST-001 | Exact enabled GET, HEAD and Process API installation even when Worker recovery is disabled |
| TEST-002 | Feature flags, neighboring paths/methods, existing Worker behavior and nonsecurity body passthrough |
| TEST-003 | Typed R1 removes the correlation matcher and produces the missing no-store header |
| TEST-004 | 49 finite tests, actual AS/PS package and 28 SQL checksums |

## 15. Non-functional and Cross-cutting Design

Use exact method/path matching, bounded existing owner response schemas, no-store and safe HTTP response metadata.

## 16. Compatibility, Migration, Rollout, and Rollback

Existing OAS and permission/configuration bytes are parity gates. Runtime deployment follows the existing owner-read enablement. Source rollback reverts the one focused commit.

## 17. Alternatives and Decisions

Reuse exact scoped normalization before IAM so both filter short-circuit and MVC errors reach the existing wire contract.

## 18. Risks and Open Questions

Actual deployed IAM and S12 acceptance remain independent runtime proof.

## 19. Traceability Matrix

| Requirement | Use case | Design and verification |
| --- | --- | --- |
| REQ-001 | UC-001 | §7 and TEST-001/003/004 |
| REQ-002 | UC-002 | §7 and TEST-002/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | Keep PS controller and filter ownership. | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | Reuse the existing scoped Worker error wrapper behavior. | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | Use existing Spring Security, JDK and Servlet test dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | ClosedProcessSecurityErrors reflects the scope. | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Require exact enabled method and path before normalizing a response. | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | Existing closed two-member wire model. | None |
| MC-CONVERT-001 | Applicable | PASS | §7/14 | Existing static envelope string contains fixed safe values. | None |
| MC-LOG-001 | Applicable | PASS | §7/14 | Do not expose downstream diagnostics. | None |
| MC-BEAN-001 | Applicable | PASS | §7/14 | Install within the existing enabled Process security chain. | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | Reuse PathPatternRequestMatcher and caching wrapper. | None |
| MC-JSON-001 | Applicable | PASS | §7/14 | Maintain existing closed JSON response contract. | None |
| MC-TIME-001 | Not applicable | N/A | §7/14 | Existing time fields are unaffected by status-only rewriting. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | Reuse existing flags and verify packaged parity. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | Use the existing scoped response wrapper. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | Two exact source paths, three documents and all 28 SQL checksums. | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | Typed matcher mutation, 49 finite cases and actual executable parity. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | Deployed IAM and S12 remain independent runtime proof. | None |

### 20.6 Final verdict

PASS — Ready for user review. Runtime acceptance remains independently gated.
