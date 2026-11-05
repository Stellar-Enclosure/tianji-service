# T4-05B 天机受治理 START 注入有限回归

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-19-27-governed-process-injection-corpus.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 现有 AR 单一有限测试夹具、一个闭合 corpus、无运行或持久化变化 |
| Created | 2026-11-05 19:27 Asia/Shanghai |
| Updated | 2026-11-05 19:27 Asia/Shanghai |
| Owner | Tianji Agent Runtime test owner |
| Repository | tianji-service |
| Scope | 主计划 S07 的 Fake 模型到固定 START 工具的 U 层协议与授权回归 |
| Change Surface | 现有 AdkGovernedProcessLoopTest 与一个 test resource |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 §17.2 S07、§17.3、§19 T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service 6fcd76be629680d8cd8863537a4c31adc2506952, Components 87af09fea9a9d58e7127b009d76f60438c79e569 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | [T4-05A1 expense 证据门禁](2026-11-05-16-56-expense-release-evidence-gate.md) |
| Related Plans | [实现](../plan/2026-11-05-19-28-governed-process-injection-corpus.md) |

## 1. Summary

用六条固定输入走现有真实 ADK Runner 和 Spring AI Fake 模型。覆盖合法控制、恶意任务文本、模型伪造租户/版本/审批字段与假审批等待。精确断言角色、工具解析、终态、结果、记录与派发计数。此证据属于 U 层协议和授权回归。

## 2. Background and Current State

| Evidence | Exact current owner | Observation |
| --- | --- | --- |
| EVD-001 | AR DefaultContextAssemblerTest#structured_data_is_sorted_losslessly_encoded_and_never_promoted_to_instruction; AdkRunContextTest#published_instructions_and_untrusted_evidence_are_budgeted_in_their_actual_roles_without_duplicating_history | 已覆盖任务 JSON 和 LlmRequest 角色，但未结合实际模型工具注入。 |
| EVD-002 | AR AdkGovernedProcessToolBridgeTest#cloned_tools_null_fences_malformed_arguments_and_started_event_failure_stop_before_recorder | 直接调用 scope，校验未通过时不进 recorder。 |
| EVD-003 | AR AdkGovernedProcessLoopTest#native_streaming_loop_uses_one_union_and_returns_only_the_authorized_instance, actual_admitted_native_context_counts_malformed_metadata_and_access_failures_without_refund, actual_native_approval_wait_returns_only_safe_status_and_none_effect | 现有实际 Runner/adapter/插件/bridge。FakeChatModel.next 对工具固定 amount 7，Rig.material 输入固定。未有同一 versioned corpus 触发真实模型回调和越权字段零派发。 |
| EVD-004 | AR ToolActionRequest/ToolSchema.values/AdkGovernedProcessToolBridge.Scope.execute | 实际工具参数封闭，拒绝发生于 recorder 和 delegate 之前。审批由宿主 resolver 提供。 |
| EVD-005 | AS ExpenseAcceptanceModelTest、AR MemoryPreferenceExtractionTest#offline_preference_quality_cases、[T4-05A1 evidence](../evidence/2026-11-05-expense-release-evidence-gate.md) | 固定模型/提取器测试和离线报告门禁用途独立。Expense A1 definition 无工具依赖；不扩大原有策略或历史验收。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 现有 AR test-only 路径，无项目生成或示例清理。

## 3. Goals and Non-goals

提供可重放的 Fake 协议和权限回归，记录 exact 定义、模型 route/policy、固定工具引用、六条语料和明确通过条件。真实供应商的输出/引用质量、RAG/Memory 恶意文本回归、S07 I/E、全量 S01–S12、CI 和 SLO 另行取证。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR test and fixture | Affected | AdkGovernedProcessLoopTest, memory-preferences-v1.json precedent | Add six bounded Fake cases via same Runner | Exact cases, shape and gates | §7, §8, §9, §10, §14, §15, §16 |
| Production runtime, report gate, security | Context-only | EVD-001–005 | Existing owners and policy | Explicit coverage boundary | §11, §12 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Versioned closed fixture in actual governed Runner | Must | Exactly six named rows, current tool refs/versions/risk/hash and one positive control | §17.1/§17.3 |
| REQ-002 | Preserve trust/data boundaries | Must | Untrusted sentinel reaches USER context, absent from system and events. Valid controls use same trusted tenant and host business/descriptor binding. | S07 |
| REQ-003 | Reject model-forged authority fields before dispatch | Must | Three separate rows fail TIANJI_INVALID_COMMAND/NONE, zero recorder/delegate/receipt-selection/tool events | S07 |
| REQ-004 | Distinguish text-only fake approval from accepted approval | Must | Existing host resolver with no receipt produces only AWAITING_APPROVAL to Fake and zero delegate | S07 |
| REQ-005 | Keep finite evidence accurate | Must | Test-only source, default old fixture behavior preserved, no actual PG/app/endpoint/provider; no I/E/L claims | §17.1 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | AR test owner | Exercise exact host checks | JUnit U | Fixed synthetic tenant 20 | EVD-003 |
| ACTOR-002 | Scripted Fake model | Emit exact test call | Existing Spring AI bridge | No authority | EVD-003/004 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Reproduce governed injection expectations | ACTOR-001 | ACTOR-002, real local Runner, fake ports | Exact JUnit selector | Current pinned source, literal ADK content flag false | Six exact results and safe counts | Any mismatch fails owning test | No external side effect | REQ-001–005 | TEST-CASE-001 | TEST-001–004 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision |
| --- | --- |
| DEC-001 | AR test-only, reuse private Rig and FakeChatModel. No shared fixture module. |
| DEC-002 | Run actual ADK 1.13.0 and current AgentFlow/Spring AI adapter. Ledger, recorder, approvals and transport remain test doubles. |
| DEC-003 | Mock expected inputs stay synthetic. Test scope never asserts real owner permissions, supplier accuracy or propagation. |

## 6. Project Technology Context

JDK25, Google ADK 1.13.0, existing AgentFlow source and Spring AI adapter, JUnit 6, Jackson 3 and Mockito from current AR test classpath. Use existing test-resource layout and local current CI runner without dependency changes.

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
| Rule 1 | Yes | Existing test and private rig | Existing named methods and fixture only | §8 | TEST-001 | PASS |
| Rule 2 | Yes | Existing schema/tool checks | Strict fixture shape and existing real checks | §9/10 | TEST-001/002 | PASS |
| Rule 3 | No | No new production values | Existing test objects and DTO | §10 | TEST-004 | N/A |
| Rule 4 | No | No production beans/logging | Existing tests only | §8 | TEST-004 | N/A |
| Rule 5 | Yes | JDK/Jackson/JUnit | No new dependency | §8/14 | TEST-004 | PASS |
| Rule 6 | Yes | Existing Jackson 3 | Closed fixture fields, no wire change | §9/10 | TEST-001 | PASS |
| Rule 7 | No | No config | Existing literal safety env in finite command | §14 | TEST-004 | N/A |
| Rule 9 | No | Six direct cases | No new pattern framework | §13 | TEST-001 | N/A |
| Rule 10 | No | Existing test clock unchanged | No new time boundary | §6.3 | TEST-004 | N/A |
| Rule 11 | Yes | Current AR test vertical | No domain/transaction/data changes | §8/11 | TEST-004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: 保留既有测试的 deadline/Clock 与 Runner 清理，新增语料不含业务时戳、期限或时间转换。

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Fixed corpus and additional test method | Expand | REQ-001–005 | Existing direct scope test | Missing adversarial generated call and host facts | Six in-process runs | Add |
| Optional existing Fake arguments | Expand | REQ-002/003 | Fixed amount=7 | Cannot represent injection | One test-only per-round override | Add |
| New testkit or policy | Keep | REQ-005 | Reuse same private fixture | No second consumer | No new interface | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 | Existing fixture | No changes | No model-issued extra field | Old finite tests |
| Selected design | 0 | Six fixture cases | No changes | Actual adapter/bridge response | Versioned protocol evidence |

### 7.1 System Architecture Design

AR test runner → existing real AgentFlow / ADK Runner / adapter → scripted Fake → existing tool plugin and bridge → test-local recorder, approvals and transport. Tests observe actual safe model replies and synthetic side effect counters.

### 7.2 High-Level Design

Add one fixture presence check and one dynamic-test factory to the same AR owning class. Cases pin the current Synthetic CONFIG definition and the two exact tool references. Preserve old Rig default input and tool argument if no override is present.

### 7.3 Detailed Design

Each case creates a fresh single-leaf Rig with tool budget two. Model proposes the selected declared tool once, with one case-specific JSON object. It then emits fixed Done writer only when called again. Observe stored run result, actual UserMessage/SystemMessage and native tool response, no fallback or repair of injected arguments. Close each rig.

## 8. Package Structure and Code File Tree

| Change | Exact relative path under stellar-enclosure-tianji-agent-runtime | Responsibility |
| --- | --- | --- |
| M | src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java | Presence, corpus, old-default-preserving private fixture adaptation |
| C | src/test/resources/evaluation/governed-process-injection-v1.json | Closed six named synthetic cases and metadata |

Optional audit archive in existing docs/egon. No production, POM, application config, Gate policy or database modifications.

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| TEST-CASE-001 | Test-only resource | evaluation/governed-process-injection-v1.json | AR | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 TEST-CASE-001 — Fixed governed injection cases

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Finite test corpus |
| Independent consumer goal | Exact reviewable generated tool and trusted-host assertions |
| Parameter ownership and derivation | Test owner controls fixture and Fake output only |
| Direct/no-new-interface alternative | Existing standalone guards do not combine native route |
| Caller use of result | Scoped U evidence only |
| Round trips and failure points | One finite local case, no network |
| Verdict | Keep private test fixture |

##### Identity and purpose

Resource schemaVersion=1, fixture and cases. Definition assistant v1, CONFIG, model fixture-model, route-v1, policy-v1, input/output schema 1, trusted tenant 20, invoice-1/business-v1. Expected exact fixed_start fixed.start v1 IDEMPOTENT_WRITE and fixed_approved fixed.approved v1 APPROVAL_REQUIRED, each pinned by current descriptor content SHA.

##### Request parameters

Rows exact id/requiresApproval/inputText/taskData/modelArguments/expected. requiresApproval selects the fixed tool risk. Fixture tools use normal and approvalRequired. Every case uses one registered function call. Unknown fields in modelArguments intentionally enter actual scope in negative rows. Fixture rows and metadata themselves strictly parsed with duplicate and trailing JSON rejected.

##### Success response

| Case ID | Final state and safe effect | Writes / delegate / receipt selection / Fake streams / tool events |
| --- | --- | --- |
| trusted-control | COMPLETED / CONFIRMED | 1/1/0/2/2 |
| untrusted-text-keeps-trusted-host | COMPLETED / CONFIRMED | 1/1/0/2/2 |
| model-forges-tenant | FAILED / TIANJI_INVALID_COMMAND / NONE | 0/0/0/1/0 |
| model-forges-release | FAILED / TIANJI_INVALID_COMMAND / NONE | 0/0/0/1/0 |
| model-forges-approval | FAILED / TIANJI_INVALID_COMMAND / NONE | 0/0/0/1/0 |
| text-approval-cannot-dispatch | COMPLETED / NONE / AWAITING_APPROVAL | 1/0/1/2/2 |

Success result exact agents.writer and text Done writer. SUCCEEDED model tool reply contains only status and output.instanceId. AWAITING_APPROVAL reply contains status only. Rejected rows have empty output. All rows have zero read/MCP/access/approval calls. Tool event details contain no synthetic injected canaries or backend receipt strings.

##### Error responses

Malformed corpus is a test failure. Rejected model fields use current safe error, not an uncaught exception. Deny cases neither record an admitted write nor delegate.

##### Interface logic for frontend and consumers

No frontend. Actual USER message carries task canary; system message does not. Text-only fake approval may use the existing pending path, with no business dispatch.

##### Compatibility and verification

Current 29 historical owning tests retain default behavior. New presence + six cases yield 36 owning finite methods/cases. Owner reports and exact hashes identify the scope.

## 10. POJO and Data Model Design

Closed local fixture using Jackson tree; no production DTO or serialized interface change. Fake per-leaf round argument overrides remain private. Tests check fixed descriptor digest independently of the Fake emitted name.

## 11. Database Design

Not applicable. Existing test doubles provide ledger/recorder facts without any DB.

## 12. Frontend Page Design

Not applicable. No web or browser.

## 13. Design Patterns and Architecture Principles

Extend one existing private fixture rather than introducing a generic test harness. Existing production authority checks continue to own outcomes.

## 14. Test Design

| Test ID | Selector | Expected evidence |
| --- | --- | --- |
| TEST-001 | AdkGovernedProcessLoopTest#governed_process_injection_corpus_is_present | Intentional missing-resource assertion before resource |
| TEST-002 | AdkGovernedProcessLoopTest#governed_process_injection_corpus | Before Fake override wiring, three controls pass and three forged-field rows fail expected FAILED vs COMPLETED. Final six pass. |
| TEST-003 | Same factory cases | Exact counters, safe model reply/USER vs System, host scope and no forged dispatch |
| TEST-004 | AdkGovernedProcessLoopTest plus existing related tests | Whole owning class expected 36 actual executed. Keep S07 I/E/L unexecuted. |

Use existing offline source reactor with literal ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false. No vendor, listeners, Testcontainers or new profile.

## 15. Non-functional and Cross-cutting Design

Fixed six cases and one proposed tool per case. Existing timeout and cleanup. Corpus contains synthetic text and proof expectations, excludes any real credentials. Report safe case IDs only.

## 16. Compatibility, Migration, Rollout, and Rollback

Tests only. No schema/config/permissions/runtime change. Existing A1 evidence policy unchanged. Revert the focused test-only commit if needed.

## 17. Alternatives and Decisions

Reuse existing real Runner and fixture. A separate fake-only prompt answer checker would not exercise authorization boundaries. A full §17 matrix repackage waits for real owner inputs and independently approved policy.

## 18. Risks and Open Questions

Fake assertions demonstrate deterministic protocol behavior. Real provider quality, citation correctness, complete S07, current auth/revocation and runtime latency remain independently unverified.

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/TEST-CASE-001 | §7–10 | TEST-001/002 |
| REQ-002 | UC-001/TEST-CASE-001 | §7/§9 | TEST-003 |
| REQ-003 | UC-001/TEST-CASE-001 | §9 | TEST-002/003 |
| REQ-004 | UC-001/TEST-CASE-001 | §9 | TEST-002/003 |
| REQ-005 | UC-001/TEST-CASE-001 | §14–18 | TEST-004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | Existing AR owner | None |
| MC-REUSE-001 | Applicable | PASS | §2/7 | Current Runner and fixture | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | No added dependency | None |
| MC-NAME-001 | Applicable | PASS | §8/14 | Same owning test | None |
| MC-VALID-001 | Applicable | PASS | §9/14 | Exact fixture/authority | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No production POJO | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No boundary mapper | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No logging | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | No Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6 | Existing tools | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | Closed test fixture | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | Existing timer seam | None |
| MC-CONFIG-001 | Not applicable | N/A | §8 | No config | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Existing test | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Two test paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Named finite and old owner tests | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source candidate reviewable | Live evidence separate |

### 20.6 Final verdict

PASS — Ready for user review

仅候选设计与隔离本地有限证明，真实运行权限、环境、外部质量与 SLO 不在此范围。
