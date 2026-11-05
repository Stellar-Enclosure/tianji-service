# S03 静态原生并行分支完成判定

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Bug Fix |
| Complexity | Simple |
| Complexity Drivers | One post-stream required-output check and an existing Fake Runner fixture |
| Created | 2026-11-06 03:41 Asia/Shanghai |
| Updated | 2026-11-06 03:41 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Proved static Parallel branch completion, two existing Java files and three review documents |
| Change Surface | Existing claimed Agent execution between native stream completion and durable success |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §6.2、§17.2 S03、§19 T1-02/T3-09](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 816b159a41b6b1312b2861c73a39088d5c2bd508 |
| Amends | None |
| Supersedes | None |
| Depends On | [S03 compiled key uniqueness §7.3](2026-11-06-02-50-s03-compiled-output-keys.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Static parallel completion](../plan/2026-11-06-03-42-s03-static-parallel-completion.md) |

## 1. Summary

在无工具、无动态委派的静态原生编译树中，根流正常结束仍须核对每个必需并行分支的终点输出。缺少任一分支输出走现有终态失败落账。已有动态模板及工具治理继续执行各自的规则。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Master S03](../../../../docs/plans/agent-framework-upgrade-plan.md) §6.2/§17.2 and [acceptance matrix](../evidence/2026-11-05-release-acceptance-matrix.md) | Native root success requires every required parallel branch output. |
| EVD-002 | [AdkAgentRunExecutor](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java) after native stream, validateAgentTopology and finalAgent | Ordinary completion accepts any nonempty projected output map. Native Gate has its own completion rules. Names are unique across the compiled tree. |
| EVD-003 | AdkEventProjector and local pinned ADK 1.13.0 ParallelAgent, LlmAgent and ExitLoopTool bytecode | Blank final text has no AGENT_OUTPUT. Parallel merges child streams and can cut on escalate. Leaf LlmAgent under a native workflow parent has no implicit transfer to parent or peers. ExitLoopTool can produce escalate. |
| EVD-004 | [AdkNativeAgentCompletionTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java) existing actual Runner Fake fixture and [native template evidence](../evidence/2026-11-05-native-agent-templates.md) | Bounded Fake tests can reproduce missing branch output and preserve delegated and fixed-template behavior. |
| EVD-005 | AdkAgentRunExecutor catch and finishOrResolve | Existing claimed Run error mapping writes FAILED without a success payload or raw branch identities. Already persisted projected outputs are retained. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: This Step updates an existing infrastructure adapter and its finite test.

## 3. Goals and Non-goals

Require each proven static Parallel branch terminal output after normal native stream completion and before writing COMPLETED. Leave native Gate, dynamic LLM transfer, loops, governed tools, custom subclasses and callback-bearing trees on their established completion paths. Operator S03 I, runtime provider/IAM and deployment remain independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR static native completion | Affected | EVD-002/003 | A static Parallel branch must provide its terminal output | Define admitted topology, failure and controls | §7/§8/§14/§15/§16 |
| Run ledger and dynamic execution | Context-only | EVD-002/004/005 | Existing fenced event and failure writes, dynamic completion rules | Source integration and exact evidence | §7/§9/§11 |
| Product API/config and PaC | Unchanged | EVD-005 | Existing status/error schema and permission declarations | Artifact comparison | §9/§16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Prove required outputs for a static compiled workflow | Must | Exact built-in Parallel and Sequential composites with nonempty children and childless LlmAgent leaves, all with empty agent hooks and no tool scope or Native Gate. Each Parallel contributes the terminal output of every declared branch. A Sequential branch contributes its last child's output and still checks earlier nested Parallel nodes. | EVD-001–003 |
| REQ-002 | Reject a missing required branch before durable success | Must | After normal stream completion a blank terminal branch with a usable sibling produces FAILED and TIANJI_DEPENDENCY_UNAVAILABLE and an empty success payload. Already projected diagnostics remain. Error omits branch names and raw output. | EVD-002/003/005 |
| REQ-003 | Preserve complete and independently governed execution | Must | Complete static branches succeed, a sequential intermediate blank can precede a usable terminal branch, and delegated and fixed native Gate controls stay accepted. The three finite suites and clean package pass with 28 identical SQL bytes and equal AS/PS application and PaC content. | EVD-004/005 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Trusted Agent host | Receive a complete static Parallel result | Existing verified definition and owned claimed Run |
| ACTOR-002 | Runtime reviewer | Verify actual finite Runner behavior and artifact parity | Existing JDK25 offline source reactor |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Finalize static Parallel | ACTOR-001 | Native root finishes normally, static tool-free shape | Collect required terminal names from actual compiled tree, check projected complete outputs, use existing success path | Missing output goes through existing fenced FAILED path; unproved topology remains on its established path | Complete success payload or fixed FAILED result | REQ-001/002/003 |
| UC-002 | Verify scoped correction | ACTOR-002 | Exact source and local dependencies available | Run R1/G1/P1 and compare artifact scope | Unexpected failure, skip or changed artifact declarations stops the Step | Reviewable source evidence | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

Use exact SDK type identity, declared descendants and existing name uniqueness. Preserve configured output keys and selection. A childless LlmAgent under the admitted native workflows cannot implicitly transfer to its parent or peers because those targets are only added for a LlmAgent parent. Exclude before and after agent callbacks, loops, tool scopes, unknown subclasses and LlmAgent subagents. The pinned ADK ExitLoopTool writes escalate and Parallel may end early on that action. These dynamic paths remain on established governance. A passed native stream can still be missing text, so check the already-projected terminal text authors before success.

## 6. Project Technology Context

The existing AR native adapter uses Java 25, pinned ADK and the current Spring AI and Agent ledger. No new dependency or storage contract. The existing JUnit Fake fixture runs the actual native Runner and ledger double.

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
| Rule 1 | Yes | EVD-001–005 | Existing executor, test and pinned native SDK types | §7/§8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | Validate exact expected outputs before writing a successful result | §7/§8 | TEST-001–004 | PASS |
| Rule 3 | No | EVD-001–005 | No new POJO, mapper or transfer contract | §7/§8 | TEST-001–004 | N/A |
| Rule 4 | No | EVD-001–005 | No Spring Bean or new logger | §7/§8 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK Set plus pinned ADK and existing JUnit fixture | §7/§8 | TEST-001–004 | PASS |
| Rule 6 | No | EVD-001–005 | No JSON mapping or persisted enum change | §7/§8 | TEST-001–004 | N/A |
| Rule 7 | No | EVD-001–005 | No profile or configuration change | §7/§8 | TEST-001–004 | N/A |
| Rule 9 | No | EVD-001–005 | Direct scoped recursive invariant, without a new orchestration path | §7/§8 | TEST-001–004 | N/A |
| Rule 10 | No | EVD-001–005 | No timestamp, deadline or conversion change | §7/§8 | TEST-001–004 | N/A |
| Rule 11 | Yes | EVD-001–005 | Existing AR native adapter and Agent ledger ownership | §7/§8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: The completion decision uses compiled topology and projected author names. Existing Run deadline, lease, event and test time semantics remain the same.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Private compiled-tree completion check | Add | REQ-001/002 | Current nonempty map check | A usable sibling can mask a missing branch | One traversal and local name set, no I/O | Required |
| Existing actual Runner tests | Extend | REQ-002/003 | Pure graph assertions | Do not show native blank output projection or persisted result | One existing test class | Required |
| New policy class or port | None | REQ-003 | Existing executor owns the terminal rule | No independent caller | No new element | Defer |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Complete static Parallel | Existing | Existing | Current Run success and outputs | Same native stream and fence | Explicit all-branch output check |
| Missing static branch | None additional | Existing FAILED | Existing failure result | No success with missing mandatory output | Reliable outcome |
| Dynamic execution | Existing | Existing | Current Gate and tools | Existing behavior | Compatibility |

### 7.1 System Architecture Design

Retain the AR executor as the owner of native result projection and the Agent ledger as the only Run result writer.

### 7.2 High-Level Design

After the root stream ends and existing empty output and Native Gate handling run, only ordinary tool-free execution considers static completeness. Traverse the compiled tree once for this check, collecting required output names while classifying the entire graph. Discard the candidate set for any unsupported node.

### 7.3 Detailed Design

The root starts with no required terminal. An exact leaf contributes its name only when a caller requires its terminal. Exact Parallel propagates terminal-required to every child. Exact Sequential propagates the parent terminal requirement to its last child only, traversing all children to enforce nested Parallel groups. Hooks, dynamic branches, loops, custom types and empty workflow wrappers return unproved. Only a fully proved tree whose projected author keys lack an expected name raises the existing fixed dependency exception. Existing lifecycle writes FAILED and keeps projected output events.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-41-s03-static-parallel-completion.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-42-s03-static-parallel-completion.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s03-static-parallel-completion.md` |

## 9. Interface Definitions

Context-only. Existing executeQueued, stream and Agent result signatures, authorization and serialization are unchanged.

## 10. POJO and Data Model Design

Reuse BaseAgent, existing SDK native classes, Set<String>, existing projected author keys and AgentLedgerException. No new DTO, mapper, field or persistent enum.

## 11. Database Design

Context-only. Existing fenced event and Run failure paths own persistence. No new SQL, transaction ownership, schema or migration.

## 12. Frontend Page Design

Not applicable. The existing FAILED result and error contract apply.

## 13. Design Patterns and Architecture Principles

A bounded compiled-tree invariant belongs directly in the existing completion path. This Simple check requires no separate Strategy or service.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Exact R1 on test-only state. A usable sibling and blank terminal branch yield old COMPLETED versus expected FAILED. Require one failure and no errors or skips. |
| TEST-002 | Exact R1 becomes green for both a Parallel root and a Parallel nested in Sequential. Both provider branches execute, useful outputs remain projected, and FAILED has no success payload. |
| TEST-003 | Static positive with a blank sequential intermediate and usable terminal, plus delegated leaf bypass and existing native Gate controls. Exact three owning finite suites. |
| TEST-004 | Clean package proves the actual guard in AR nested in AS, unchanged AS/PS effective PaC and config, all 28 SQL bytes, exact stage and strict validators. |

## 15. Non-functional and Cross-cutting Design

Use one completion traversal and one per-run required-author set. No external I/O or shared state. Failure text is fixed, and existing trace, budget and cancellation paths own their behavior.

## 16. Compatibility, Migration, Rollout, and Rollback

Static complete runs and existing dynamic execution retain their paths. Restore the previous behavior by reverting the single focused source commit. No runtime startup or release action belongs to this Step.

## 17. Alternatives and Decisions

A blanket requirement for every declared agent name incorrectly counts intermediate sequential nodes and optional dynamic agents. Native Gate remains authoritative for its own templates. The exact admitted static-tree rule covers the observed bug without recasting dynamic native semantics.

## 18. Risks and Open Questions

S03 actual PostgreSQL, native branch cancellation and failure under operator conditions, provider behavior and deployed permission acceptance remain separate.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7 | TEST-001/002/003 |
| REQ-002 | UC-001 | §7/9/11/15 | TEST-001/002 |
| REQ-003 | UC-001, UC-002 | §8/14/16/18 | TEST-003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing Agent Runtime infrastructure adapter | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002–005 | Existing native projected outputs and existing Runner fixture | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Current JDK, ADK and JUnit | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Existing executor and exact semantic tests | None |
| MC-VALID-001 | Applicable | PASS | §7.3/14 | Guard only proved static required branch terminal outputs | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No new carrier | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Reuse native projection and existing result | None |
| MC-LOG-001 | Applicable | PASS | §7.3/15 | Fixed failure diagnostic, no branch identifiers | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No Spring Bean changes | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JDK Set and exact ADK node semantics | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | Existing output contract and error enum | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No time change | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No profile or enablement | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Small invariant in existing runner completion path | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Five exact paths and 28 protected SQL files | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite R1 and G1 plus package gate | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Bounded source gate, independent runtime acceptance | None |

### 20.6 Final verdict

PASS — Ready for user review
