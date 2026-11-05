# S03 编译后 Agent 输出键隔离

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-02-50-s03-compiled-output-keys.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Bug Fix |
| Complexity | Simple |
| Complexity Drivers | One existing compiled-topology traversal and one bounded Fake Runner regression |
| Created | 2026-11-06 02:50 Asia/Shanghai |
| Updated | 2026-11-06 02:50 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Exact compiled-root outputKey uniqueness, one AR source, one existing AR test and three review documents |
| Change Surface | In-process ADK topology validation before native Session execution |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17.2 S03、§19 T1-02/T3-09](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 3577cdd521271b65f431c2d28601175fec92c638 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S03 compiled key Plan](../plan/2026-11-06-02-51-s03-compiled-output-keys.md) |

## 1. Summary

同一个编译后的原生 Agent 子树内，多个 LlmAgent 使用同一个实际配置的 outputKey 会竞争原生 Session 的同一状态位置。统一在现有执行器第一次拿到真实 root 时核对精确键唯一性。冲突在 Session、Runner、模型及预算工作前按现有失败路径落账。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Master S03 coverage](../evidence/2026-11-05-release-acceptance-matrix.md) and master §17.2 | S03 requires outputKey conflict rejection or explicit behavior and separate composed runtime evidence. |
| EVD-002 | [AdkAgentRunExecutor](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java) #executeScoped, #buildRoot, #requireGovernedTools, #collectAgentNames | CONFIG uses AgentFlowConfigValidator. The generic trusted CODE_FIRST factory returns a native tree. The existing recursive check covers names and the separate tool check covers callbacks and tools. Native output keys on the returned generic tree are unchecked. |
| EVD-003 | AgentFlowConfigValidator#validate and NativeAgentCompilerStrategy#validateTopology; [native template evidence](../evidence/2026-11-05-native-agent-templates.md) | Reviewed CONFIG and fixed native template declarations already reject duplicate output keys. They do not cover every generic factory's actual returned topology. |
| EVD-004 | [AdkNativeAgentCompletionTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java) and AdkGovernedProcessLoopTest | Existing owner-scoped in-memory ledger and actual native Runner Fake fixtures cover known plain, fixed-native and governed behavior. |
| EVD-005 | AdkAgentRunExecutor#finishOrResolve and current AgentRun result contract | Existing AgentLedgerException mapping persists a FAILED result and its current domain error code through the claimed Run lifecycle. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: One existing AR ADK infrastructure adapter and one finite test. There is no generated project, example or schema work.

## 3. Goals and Non-goals

Reject repeated exact output keys across the compiled root and all declared descendants. Preserve accepted non-colliding graphs. Independent static branch-output completeness, S03 operator I, external IAM, deployment and full plan acceptance stay outside this Step.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR compiled native topology | Affected | EVD-002/003 | Every configured output key has one declared agent | Exact validation, order and failures | §7/§8/§14/§15/§16 |
| Runner and durable result | Context-only | EVD-002/004/005 | Existing current Run claim, Session handling and failure result mapping | Source integration and targeted evidence | §7/§9/§11 |
| Product API/config and PaC | Unchanged | EVD-004/005 | Existing production contracts, configuration and permission declarations | Verify artifact compatibility | §9/§16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Reject repeated exact configured outputKey in the compiled root | Must | Existing root traversal checks every LlmAgent's present outputKey including nested declared descendants using exact String equality. A distinct spelling remains distinct, and absent output keys remain allowed. | S03, EVD-002/003 |
| REQ-002 | Fail closed before native execution and preserve existing result semantics | Must | A generic trusted CODE_FIRST fixture with distinct agent names and one duplicate key yields FAILED and TIANJI_FEATURE_NOT_AVAILABLE. No model call, budget reservation, native event projection or raw output key is observable. Check precedes native Session create/acquire and Runner creation. | EVD-002/004/005 |
| REQ-003 | Verify exact scoped compatibility | Must | R1 fails for the duplicate-key gap, G1 and clean P1 pass current Runner/governed/capture controls. Exact five paths, all 28 SQL bytes identical, AS/PS effective PaC and configuration equal the baseline. | S03 and current source boundaries |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Trusted CODE_FIRST host | Supply one runnable native graph | Existing factory registry and claimed owner Run |
| ACTOR-002 | Runtime reviewer | Inspect failure and compatibility proof | Existing JDK25 offline source reactor |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Prevent ambiguous Session output state | ACTOR-001 | Current owner Run claimed, factory returns root | Walk root and declared descendants once; valid topology enters established path | Duplicate present key returns fixed terminal error without native invocation | Exact Run result and no model/budget/projection activity for rejected topology | REQ-001/002 |
| UC-002 | Verify the source fix | ACTOR-002 | Baseline and exact fixture available | Run R1, G1, P1, scope check | Incorrect RED, skips or artifact drift prevent commit | Scoped review evidence | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

Existing owner, tools, ADK and Spring wiring govern the Run. Keys compare exactly as returned by the compiled native LlmAgent. No trim, case folding or new blank-key normalization is introduced. Check each present key and ignore absent Optional values. Local tool declaration binding can precede buildRoot because the current compiler needs the declarations. The topology guard precedes native Session creation or lease, Runner creation, budget and provider work. Keep existing duplicate-name protection.

## 6. Project Technology Context

The existing traditional AR infrastructure adapter invokes the pinned ADK native Runner on Java 25. It uses current Spring AI, ledger and declared tools. No new library, Bean, interface, DTO or database access is needed. JUnit and the existing Fake model provide the executable regression.

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
| Rule 1 | Yes | EVD-001–005 | Reuse executor, test and exact native SDK types | §7/§8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | Validate compiled topology before native Session/model entry | §7/§8 | TEST-001–004 | PASS |
| Rule 3 | No | EVD-001–005 | No new POJO, mapper or transfer contract | §7/§8 | TEST-001–004 | N/A |
| Rule 4 | No | EVD-001–005 | No Spring Bean or new logger | §7/§8 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | Reuse JDK Set, ADK and existing JUnit fixture | §7/§8 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–005 | No JSON mapping or persistent enum change | §7/§8 | TEST-001–004 | N/A |
| Rule 7 | No | EVD-001–005 | No profile or configuration change | §7/§8 | TEST-001–004 | N/A |
| Rule 9 | No | EVD-001–005 | One existing traversal directly covers the invariant | §7/§8 | TEST-001–004 | N/A |
| Rule 10 | No | EVD-001–005 | No time contract or new timeout | §7/§8 | TEST-001–004 | N/A |
| Rule 11 | Yes | EVD-001–005 | Stay inside existing AR adapter and preserve Agent ledger ownership | §7/§8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: The decision uses immutable string equality on the compiled topology. Existing Run deadline, lease, event and test time semantics remain the same.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Existing compiled root traversal | Expand | REQ-001/002 | CONFIG and reviewed template checks only | A generic factory can return undeclared same-key agents after compilation | One per-run Set, no I/O, fixed early failure | Extend |
| Existing in-memory ledger and Fake Runner | Reuse | REQ-002/003 | Only DTO-level validation | Does not show actual factory output or absence of execution | One exact test in existing fixture | Keep |
| Separate validator or API | None | REQ-003 | Current private traversal | Existing owner and no independent consumer | No new class, route or binding | Defer |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Existing valid topology | Existing | Existing | Current native graph and claimed Run | Same current lifecycle | Compatible source behavior |
| Duplicate present output key | No new | Existing terminal result | Same error field and durable status | Reject before native Session/model side effects | Prevent ambiguous state writes |
| Source verification | None | Existing test fixture | Fixed clean source/package gate | Runtime remains independent | Reviewable evidence |

### 7.1 System Architecture Design

Use the existing Agent Runtime native adapter and its already owned compiled BaseAgent. The existing ledger remains the only Run result writer.

### 7.2 High-Level Design

Immediately after buildRoot, collect names and present output keys across the declared native tree. Reuse the validated name set later for event projection and one separate Set for the exact keys. Keep existing tool governance and error mapping. This validates the actual returned graph for all current supported compilation paths, including generic trusted CODE_FIRST.

### 7.3 Detailed Design

Walk root plus recursive subAgents. For a LlmAgent with present outputKey, add the exact returned String. A repeated key raises existing AgentLedgerException with TIANJI_FEATURE_NOT_AVAILABLE and a fixed message. Do not include the key, owner, agent names or graph metadata in the message. No key comparison crosses Runs. Existing duplicate-name failure remains enforced. Later event filtering uses the already-validated Set of names. No provider, Session or projected event is entered for the rejected graph. The existing exception path handles FAILED without adding public result fields.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` |
| CREATE | `docs/egon/spec/2026-11-06-02-50-s03-compiled-output-keys.md` |
| CREATE | `docs/egon/plan/2026-11-06-02-51-s03-compiled-output-keys.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s03-compiled-output-keys.md` |

## 9. Interface Definitions

Context-only. Existing executeQueued and persisted Agent result contracts own the behavior. Their signature, schemas and authorization are unchanged. No new REST, OpenAPI, event interface or internal service interface.

## 10. POJO and Data Model Design

Reuse BaseAgent, LlmAgent, Set<String>, AgentLedgerException and existing Run result types. A per-run local set is discarded with the invocation. No DTO, JSON mapping or persisted enum addition.

## 11. Database Design

Context-only. Reuse the current failure write after claim, through the existing Run lifecycle. There is no query, transaction ownership, migration or data model change.

## 12. Frontend Page Design

Not applicable. Existing FAILED results and error codes remain authoritative.

## 13. Design Patterns and Architecture Principles

Extend the existing native topology validation in place. One fixed per-run invariant needs no new Strategy, policy abstraction or cross-layer component.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Exact R1 adds only the existing-class Fake fixture regression for a generic CODE_FIRST graph with distinct names and a duplicate output key. Expect the existing behavior to violate the FAILED assertion, with exactly one failure and no errors or skips. |
| TEST-002 | Exact R1 method becomes green. Verify FAILED/TIANJI_FEATURE_NOT_AVAILABLE, zero provider calls, no budget reservation, no projected event or output, and no raw key exposed. The second test accepts distinct exact key spellings and a declared leaf without a key. |
| TEST-003 | Existing AdkNativeAgentCompletionTest, AdkGovernedProcessLoopTest and AdkCheckpointCaptureTest pass in the owning reactor, including existing ordinary and governed controls. |
| TEST-004 | Clean package checks the actual AR class in AS, exact unchanged AS/PS PaC and application config, and all 28 migration bytes. Both strict validators and exact stage pass. |

## 15. Non-functional and Cross-cutting Design

Reuse one traversal over the declared tree and exact String equality. The extra per-run Set contains configured keys only. Existing trusted factory, ledger and provider boundaries remain. Error text is fixed and carries no raw key. No runtime startup is part of source verification.

## 16. Compatibility, Migration, Rollout, and Rollback

Existing allowed distinct keys and missing keys remain valid. Duplicate compiled keys fail closed through the existing code. Preserve API, POM, permissions, configs and migrations. Revert the one focused source commit if required. Actual product startup or provider use is a separately authorized gate.

## 17. Alternatives and Decisions

CONFIG-only validation misses generic factory output. Rejecting at Session write permits earlier model work. Checking the already compiled native tree before Session/Runner work is the direct authoritative check.

## 18. Risks and Open Questions

The scope covers declared topology keys. Dynamic output completeness, independent S03 runtime acceptance, provider behavior and deployed permission validation have separate evidence gates.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7 | TEST-001/002 |
| REQ-002 | UC-001 | §7/9/11/15 | TEST-001/002 |
| REQ-003 | UC-002 | §8/14/16/18 | TEST-003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing Agent Runtime infrastructure adapter | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002/003 | Extend compiled-root traversal and existing Fake Runner fixture | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Current JDK ADK and JUnit only | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Existing executor and semantic exact test name | None |
| MC-VALID-001 | Applicable | PASS | §7.3/14 | Check all declared native subagents before entering Session or provider | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No new carrier | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing execution material and native topology | None |
| MC-LOG-001 | Applicable | PASS | §7.3/15 | Fixed sanitized domain failure, no key in durable result | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No Spring bean change | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK exact String and Set semantics | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing result codec and error enum | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No deadline, timestamp or time conversion changes | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration or enablement | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Single existing traversal covers one compiled topology | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Five exact paths, PaC, YAML, 28 SQL protected | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite R1 and G1 plus P1 artifact check | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Scoped source gate has no runtime prerequisite | None |

### 20.6 Final verdict

PASS — Ready for user review
