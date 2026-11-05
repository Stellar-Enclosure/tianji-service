# T4-03 Agent owner 恢复处置观察

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-14-agent-owner-recovery-disposition.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 既有只读 AR 查询上的一个默认关闭 AS GET 和中立闭合 DTO |
| Created | 2026-11-05 23:14 Asia/Shanghai |
| Updated | 2026-11-05 23:14 Asia/Shanghai |
| Owner | Tianji Agent |
| Repository | tianji-service |
| Scope | Agent 当前 owner 恢复处置的本地证据 |
| Change Surface | C 只读 DTO，AS 单独受保护 GET、PaC 和静态 OpenAPI |
| Affected Chapters | §7, §8, §9, §10, §13, §14, §15, §16 |
| Source Requirement | [Master §19 T4-03 and S11](../../../../docs/plans/agent-framework-upgrade-plan.md) 恢复处置与人工核对 |
| Baseline Revision | tianji-service 1ae891f2aca338cceb80fc0d8517c100a53ae55b |
| Amends | None |
| Supersedes | None |
| Depends On | [Internal Agent recovery disposition](2026-11-05-16-52-agent-recovery-disposition.md#7-architecture-design) |
| Related Specs | None |
| Related Plans | [Owner recovery implementation](../plan/2026-11-05-23-15-agent-owner-recovery-disposition.md) |

## 1. Summary

当前授权 Agent owner 可显式读取一个运行的本地恢复处置证据。它区分当前活动、待核对、已终止、不可验证和有限 Tool 覆盖度。运行级外部效果、Tool 证据和不确定性保持独立。

## 2. Background and Current State

| Evidence ID | Source | Observed fact |
| --- | --- | --- |
| EVD-001 | [AgentLedgerService inspectRecovery](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java#L150) · [Source74 evidence](../evidence/2026-11-05-agent-recovery-disposition.md) | AR 已有准确四部分 owner 的 primary RR 只读快照和 V13 观察，现无生产 HTTP 调用者。 |
| EVD-002 | [JdbcAgentLedgerRepository](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java#L56) | 查询按确切运行验证 V13，只投影闭合行状态、风险和本地 receipt 有无，64+1 上限；不能据此推出无外部影响。 |
| EVD-003 | [AgentRunController](../../../stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRunController.java#L116) · [TianjiAgentAccess](../../../stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentAccess.java#L34) | 现有 owner GET 采用同一 API 权限、scope 和当前 actor，public DTO 不含恢复读证据。 |
| EVD-004 | [Current AS technical read](../../../stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentContextSnapshotController.java#L22) · [S11 evidence](../evidence/2026-11-05-adk-durable-resume-probe.md) | 已有独立默认关闭的 Agent 证据 GET 模式。原生恢复探针仅编译，不能开放接管或推导安全重放。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 在既有 C/AS 技术读路径上增加一个明确的只读消费入口，无 archetype、示例域或清理范围。

## 3. Goals and Non-goals

现有 Agent 运行详情或授权 API 调用者可查一个已知 owner 运行的处置状态，区分待人工核对和证据不可用。原生 checkpoint、takeover、权限启用、回执补写、跨 owner 或 Tool/模型访问另行授权。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| C and AS owner API | Affected | EVD-001/003/004 | One default-off current owner read and closed output | Exact Query, permission, state projection and producer | §7, §8, §9, §10, §13, §14, §15, §16 |
| AR reader / V13 / Agent storage | Context-only | EVD-001/002 | Reuse unchanged current physical snapshot and rows | Current provenance and uncertainty | §7, §11 |
| Web/CL and actual activation | Not applicable | EVD-003/004 | Separate consumer work | Boundary only | §12/16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Existing owner scope under new exact resource | Must | Current USER/SERVICE and existing READ/scope. No lookup on denied authority. Foreign/absent 404. | EVD-003 |
| REQ-002 | Reuse current observed recovery disposition | Must | One unchanged owner AR reader call, exact run affinity, closed enum/UTC projection, malformed source fails 503 | EVD-001 |
| REQ-003 | Preserve independent effect and uncertain Tool evidence | Must | Tri-state coverage and exact conflict; no per-tool or provider IDs, action or eligibility | EVD-001/002/004 |
| REQ-004 | Explicit default off and compatibility | Must | AND servlet/storage/API/own flag, no new schema/write/dependency, actual AS PaC and static contract | EVD-003/004 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Current Agent run owner (USER/SERVICE) | Inspect local recovery evidence for a known run | Verified exact owner and dedicated current resource under Agent READ/scope |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Inspect one owned run recovery disposition | ACTOR-001 | Existing AR reader | Explicit GET | Source enabled, current resource and exact owner | Closed observed evidence | Denied, not found, dependency unavailable or partial local evidence | No mutation or recovery action | REQ-001–004 | API-001 | TEST-001–004 |

## 5. Constraints, Assumptions, and Decisions

用户或 API caller 通过原 Agent 运行页面或已授权运行 GET 取得已知 runId。新资源独立检查权限，数据权限仍是完全相同 owner。新只读源码交付不配置真实 grant 或开启 flag。现有 AR 状态表、Tool V13 和两秒 primary RR 查询保持。AS 不连接 Tool provider、Process 服务或 Session 存储。

## 6. Project Technology Context

Neutral C JDK25 values, existing Boot MVC/Jackson, AS IdP/Jianshen PaC, original AR JdbcTemplate/TransactionTemplate. No POM or dependency change.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-003 C/AS | AgentRecoveryDispositionDTO and exact controller | §8/10 | TEST-001/002 | PASS |
| Rule 2 | Yes | Existing current access and neutral C | Guard, strict exact path/query and C record invariants | §7/9 | TEST-002 | PASS |
| Rule 3 | Yes | Existing neutral C values | Immutable record, no entity or converter | §10 | TEST-001/002 | PASS |
| Rule 4 | Yes | Existing AgentContextSnapshotController | Same current technical MVC constructor and no business log | §8 | TEST-002/003 | PASS |
| Rule 5 | Yes | EVD-001/003 | Existing JDK, Spring and AR reader | §6/7 | TEST-002 | PASS |
| Rule 6 | Yes | Existing Agent JSON closed records | Enum names, strict UTC, exact OpenAPI | §9 | TEST-002/004 | PASS |
| Rule 7 | Yes | Existing AS configuration | One default-false setting, current profiles retained | §16 | TEST-003/004 | PASS |
| Rule 9 | Yes | Existing read-only adapter precedent | Direct Query projection to actual HTTP consumer | §13 | TEST-002 | PASS |
| Rule 10 | Yes | Existing DB UTC observation | Exact Instant to wire UTC Z | §6.3 | TEST-002 | PASS |
| Rule 11 | Yes | Existing owner-local AR read | No new storage path, table, writer, event or layer | §7/11 | TEST-004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | java.time.Instant | Not persisted | UTC Z | Explicit UTC Instant | Wire UTC Z | Epoch through 9999-12-31T23:59:59.999999Z, no local clock interpretation | Existing owning PostgreSQL clock, microsecond precision | No time used for authorization or resumed deadline | TEST-002 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Agent owner recovery endpoint | Add | REQ-001–004 | Existing GET/events/results | Existing DTO and historical Tool events cannot provide current bounded V13 evidence | One explicit no-store read, no stored state | Add |
| Exact C projection | Add | REQ-002/003 | Return AR internal record or Map | Internal shape carries revision/attempt and is unvalidated for wire | Closed neutral immutable values, no new layer | Add |
| AR inspectRecovery and current guard | Keep | REQ-001–004 | Add another store or private diagnostics facade | Current query already verifies facts; private facade alone has no caller | Same primary snapshot and permission guard | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Current Agent detail/SSE | Existing | Run/result/historical events | Existing only | Partial or stale events | Existing execution view |
| Dedicated evidence Query | One explicit GET | Local snapshot or controlled refusal | One closed DTO, no stored state | Current auth and scoped read snapshot | Independent uncertainty and evidence coverage |

### 7.1 System Architecture Design

Existing owner caller → current AS authorization → AgentRecoveryDispositionController → AgentLedgerService.inspectRecovery → existing owning AgentDB snapshot. No network calls after the existing permission check.

### 7.2 High-Level Design

The controller verifies the dedicated current resource and authenticates the exact actor before strict input parsing. Call the existing AR read exactly once. Only build a public DTO when the returned run matches the requested run and all closed invariants hold.

### 7.3 Detailed Design

Controller requires servlet, api.enabled, storage.enabled and recovery-diagnostics.enabled. Reject unknown/repeated query names by requiring an empty parameter map. Reuse GetAgentRunQuery and reject a raw identifier that changes on normalization. AR independently enforces verified owner, 64-character input and current same-primary read-only repeatable read. Source absence returns existing owner 404, dependency failure returns existing 503. Projection verifies exact response runId and maps only known enums and finite UTC. Tool flags are all nonnull for COMPLETE, true or null for TRUNCATED, and all null for UNAVAILABLE. A positive unresolved write implies a positive dispatching or unknown result witness. For nonterminal lifecycle Agent effect is UNVERIFIED. evidenceConflict equals RECORDED_NONE plus hasUnresolvedWrite true.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionControllerTest.java` |
| Create | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryDispositionDTO.java` |
| Modify | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java` |
| Create | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionController.java` |
| Modify | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` |
| Create | `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json` |
| Create | `docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md` |
| Create | `docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md` |
| Create | `docs/egon/evidence/2026-11-05-agent-owner-recovery-disposition.md` |

## 9. Interface Definitions

### 9.0 API protocol and documentation governance

| Concern | Decision/evidence |
| --- | --- |
| Protocol selection | Existing Agent HTTP JSON, one exact read resource |
| CQE application level | Read-only Query |
| REST source of truth | C records, AS annotations and Contract-first packaged OpenAPI 3.1 |
| GraphQL source of truth | No GraphQL surface |
| Springdoc/OpenAPI compatibility | Static existing package procedure and no runtime dependency |
| Legacy Swagger/Springfox status | No legacy code generator |
| Security and documentation exposure | Existing AS security and new protected resource, static OAS |
| Contract publication and drift gate | Actual owning exec ZIP and exact PaC delta |

### 9.1 Interface Inventory

| ID | Change/necessity verdict | Name/purpose | Kind | API style/CQE role | Consumer | Owner | Method + URL / GraphQL field / symbol / topic | Operation ID/schema source | Input | Output | Auth/tenant | Error model | Idempotency/version | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| API-001 | Add current owner read | Observe local recovery evidence | HTTP | REST Query | Current Agent owner | AS | GET /api/tianji/v1/agent-runs/{runId}/recovery-disposition | getOwnedAgentRecoveryDisposition from C and OAS | Exact runId only | Closed disposition | Existing Agent READ/scope and new exact resource | Existing safe AS errors | Read-only | REQ-001–004 |

### 9.2 Per-interface Detailed Contracts

#### API-001 — Get current Agent owner recovery evidence

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add a protected current owner REST read |
| Independent consumer goal | Distinguish local uncertainty and evidence coverage for a known run |
| Parameter ownership and derivation | Caller provides exact run ID; current owner derived only from AS authorization |
| Direct/no-new-interface alternative | Current Run GET and history are separate facts |
| Caller use of result | View read-only local evidence, no recovery action |
| Round trips and failure points | One explicit request, one existing owner snapshot |
| Verdict | One default-off endpoint on existing reader |

##### API style and CQE semantics

| Concern | Decision/evidence |
| --- | --- |
| Protocol style | REST Query over JSON |
| CQE role | Query only |
| Resource/task semantics | Exact owner run local disposition |
| Read/write and side effects | Same existing AR SELECT; no business effects |
| Consistency and idempotency | One observation per request |
| Why this style | Existing protected Agent API pattern and known consumer |

##### Identity and purpose

| Concern | Definition |
| --- | --- |
| Route | GET /api/tianji/v1/agent-runs/{runId}/recovery-disposition |
| Resource | tianji.api.agent.recovery.disposition with label api.agent.recoveryDisposition |
| Operation | getOwnedAgentRecoveryDisposition |
| Authorization | USER or SERVICE, TIANJI_AGENT_READ, tianji:agent:read, exact current policy and same full AR owner |
| Cache | no-store success and controlled error |
| HEAD | Implicit same resource policy, no body |

##### Request parameters

| Name | Location | Type and validation |
| --- | --- | --- |
| runId | required path | Exact validated 1–64 safe characters, no normalization, reuse GetAgentRunQuery and AR bound |
| X-Egon-Tianji-Tenant | existing optional header | Assert verified current tenant, no tenant selection |
| Authentication | existing bearer header | Current scoped identity and resource |

No query keys, body, scope/owner selector or mutation option.

##### Success response

200 JSON and Cache-Control no-store. These fields are always present. Nullable Tool booleans carry exact §7.3 uncertainty.

```jsonc
{
  "runId": "run-01", // Exact owned runtime identity
  "observedAt": "2026-11-05T10:00:00Z", // Existing Agent primary UTC observation
  "lifecycle": "TERMINAL", // Finite recorded lifecycle
  "reason": "TERMINAL_RECORDED", // Finite local explanation
  "agentEffect": "UNKNOWN", // Independent effect state
  "toolEvidence": { // Local bounded V13 evidence
    "coverage": "UNAVAILABLE", // No trusted complete or partial read
    "hasDispatching": null, // Unknown
    "hasUnknownResult": null, // Unknown
    "hasUnresolvedWrite": null, // Unknown
    "hasLocalReceipt": null // Unknown
  },
  "evidenceConflict": false // True only for recorded NONE versus an observed unresolved write
}
```

Enums are exact C names. Lifecycle QUEUED/ACTIVE/STALLED/TERMINAL/UNVERIFIED. Reasons QUEUE_DISCOVERY/CURRENT_LEASE/CANCEL_REQUESTED/DEADLINE_DUE/LEASE_MISSING/LEASE_EXPIRED/AUTHORIZATION_DENIED/AUTHORIZATION_UNAVAILABLE/FENCE_UNVERIFIED/SUSPENSION_UNVERIFIED/LEGACY_WAIT_UNVERIFIED/TERMINAL_RECORDED/SOURCE_UNAVAILABLE. Effects RECORDED_NONE/RECORDED_CONFIRMED/UNKNOWN/UNVERIFIED. Tool coverage COMPLETE/TRUNCATED/UNAVAILABLE. COMPLETE covers only that run's checked V13 invocation metadata, never external state. No revision, attempt, internal IDs, credentials or payload. A false conflict flag does not mean effects are absent.

##### Error responses

| HTTP | Code | Condition |
| --- | --- | --- |
| 400 | TIANJI_INVALID_COMMAND | Invalid path/query |
| 401 | TIANJI_UNAUTHENTICATED | Current auth missing or invalid |
| 403 | TIANJI_PERMISSION_DENIED | Resource or READ scope unavailable to actor |
| 404 | TIANJI_RESOURCE_NOT_FOUND | No exact owned run |
| 409 | TIANJI_TENANT_CONTEXT_CHANGED | Existing tenant assertion mismatch |
| 503 | TIANJI_DEPENDENCY_UNAVAILABLE | Source unavailable or invalid evidence projection |

Use existing no-store AgentApiErrors and security envelope, no private diagnostic text.

```jsonc
{
  "code": "TIANJI_RESOURCE_NOT_FOUND", // Existing stable error
  "message": "TIANJI_RESOURCE_NOT_FOUND", // Safe known code only
  "correlationId": null, // No identity echo
  "decisionId": null, // No identity echo
  "retryAfter": null // No time suggestion
}
```

##### Interface logic for frontend and consumers

1. Obtain current actor and dedicated Agent resource authorization.
2. Use one exact known owner run ID.
3. Send explicit GET with no query or body.
4. Check exact runId and closed typed response.
5. Display lifecycle and evidence uncertainty independently.
6. Drop stale responses on identity, tenant or run changes and distinguish 404/503 from unavailable Tool coverage.
7. Keep further Tool evidence or actions behind their own authorization. Do not infer takeover or retry.

##### Documentation contract

| Concern | Decision/evidence |
| --- | --- |
| Documentation authority | Contract-first packaged AS OpenAPI 3.1 and neutral C |
| REST OpenAPI operation / GraphQL SDL operation | operationId getOwnedAgentRecoveryDisposition |
| Annotation/mapping ownership | AgentRecoveryDispositionController and PaC definitions |
| Generated schema elements | Generated OAS effect is one exact protected GET, bounded schema and known errors |
| Compatibility and drift proof | Actual owning Boot ZIP, PaC and focused MockMvc |

##### Compatibility and verification

Existing Agent GET, LIST, result and SSE shapes and permissions remain. Actual PS and AS runtime IAM, enabling the route, external reconciliation and S11 remain separate verification.

### 9.3 OpenAPI 3 and springdoc annotation plan

| Target | Required annotation/configuration | Exact values/source | Generated OAS effect | Verification |
| --- | --- | --- | --- | --- |
| AS endpoint | Contract-first static OpenAPI 3.1, @GetMapping, existing permission annotations | API-001 and agent-recovery-disposition-v1.json | One exact GET and closed response | TEST-002/004 |
| Docs runtime | Existing static resource only | No new Springdoc dependency or route | Existing behavior | TEST-004 |

### 9.4 API contract generation and blocking gate

| Gate ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| API-GATE-001 | Applicable | PASS | §9/14 | Exact owner discovery necessity | None |
| API-GATE-002 | Applicable | PASS | §9/14 | REST Query and no event | None |
| API-GATE-003 | Applicable | PASS | §9/14 | Complete method/query/success/error contract | None |
| API-GATE-004 | Not applicable | N/A | §9/14 | GraphQL absent | None |
| API-GATE-005 | Applicable | PASS | §9/14 | Closed bounded C DTO | None |
| API-GATE-006 | Applicable | PASS | §9/14 | Current four-part authority and exact path | None |
| API-GATE-007 | Applicable | PASS | §9/14 | Static contract-first 3.1 | None |
| API-GATE-008 | Applicable | PASS | §9/14 | Actual producer drift and manifest checks | None |
| API-GATE-009 | Applicable | PASS | §9/14 | Traceability complete and current SQL scope | None |

## 10. POJO and Data Model Design

One immutable neutral AgentRecoveryDispositionDTO with nested ToolEvidenceDTO and four closed enum sets. Existing AR result stays internal. Constructor checks exact IDs, UTC observedAt, lifecycle/reason, tool tri-state and conflict. No mutable data or persistence model.

## 11. Database Design

N/A. Existing owner AR SELECT, tables, migrations, locks and writer behavior remain; no new SQL for this slice.

## 12. Frontend Page Design

N/A for this source Step. Existing run detail is the concrete named consumer under separate Web scope.

## 13. Design Patterns and Architecture Principles

Use existing owner Query adapter and a direct C projection. A separate private service or recovery strategy would create unused abstraction for one existing read.

## 14. Test Design

| ID | Scope | Proof |
| --- | --- | --- |
| TEST-001 | R1 presence | recovery_disposition_route_exists genuine absence |
| TEST-002 | R2 typed source and B | exposes_only_same_owner_closed_recovery_evidence, exact strict/auth/error/tri-state tests |
| TEST-003 | Runtime conditions and compatibility | Conditional servlet with three opt-in flags, existing Agent C/AR/AS tests |
| TEST-004 | Actual package | Source OAS exact, new protected PaC resource plus label, YAML false, prior bytes preserved |

## 15. Non-functional and Cross-cutting Design

No new provider call, scheduler, DB write or mutation. Existing primary two-second bound unchanged. Response carries exact finite facts and rejects inconsistent source output.

## 16. Compatibility, Migration, Rollout, and Rollback

One false flag; existing scopes and deployments stay unchanged. Policy admission, runtime enablement and independent S11 gate are outside. Revert one source commit if needed.

## 17. Alternatives and Decisions

Keep no-consumer diagnostics private or add an actual explicit owner Query. Choose the latter for existing detail consumers. Defer checkpoint/takeover and provider reconciliation. Existing V14 known-result access is a separately authorized writer and does not resolve UNKNOWN.

## 18. Risks and Open Questions

The DTO reports evidence only within the existing V13 observation. Effects can remain unknown or contradictory. No current runtime eligibility or grant is inferred. Real IAM and database acceptance remain separate.

## 19. Traceability Matrix

| Requirement | Use case | Design | API | Tests |
| --- | --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9 | API-001 | TEST-001/002/004 |
| REQ-002 | UC-001 | §7/10 | API-001 | TEST-002/003 |
| REQ-003 | UC-001 | §7/9/10 | API-001 | TEST-002 |
| REQ-004 | UC-001 | §8/14/16 | API-001 | TEST-003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

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
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Current technical MVC constructor and existing guard | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing JDK and length-safe digest | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 | Closed wire/OAS and exact errors | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | UTC microsecond/DB clock | None |
| MC-CONFIG-001 | Applicable | PASS | §8/16 | One owner-only default-false property, existing settings remain | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Existing owner query adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | One exact route, zero schema/trace scope | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Finite plus actual package and separate I | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Source review, runtime separate | None |

### 20.6 Final verdict

PASS — Ready for user review
