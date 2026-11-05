# T4-05 固定 Agent 质量评估

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-21-fixed-agent-quality-evaluation.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Five scripted paths in one existing in-process test rig |
| Created | 2026-11-06 01:21 Asia/Shanghai |
| Updated | 2026-11-06 01:21 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR test, one versioned synthetic fixture and three documents |
| Change Surface | Test-only quality vectors, measured assertions and sanitized Surefire summary |
| Affected Chapters | §7, §8, §10, §14, §15, §16 |
| Source Requirement | [Master T4-05 and S07](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service 1605f9aebf25445076d611d661b86038ea5e18d8 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [Fixed quality source plan](../plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md) |

## 1. Summary

固定五条合成模型样本，在实际 ADK Runner、现有受治理工具和运行预算插件内比较结构化结果、工具决策和可重复的调用/记账证据。通过一次独立选择器输出版本化、脱敏统计。真实模型评估和生产 SLO 另行验收。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Prior injection corpus](../evidence/2026-11-05-governed-process-injection-corpus.md) | Fixed S07 unit evidence has six adversarial vectors. It lacks one versioned quality sample report across output, tool and budget outcomes. |
| EVD-002 | [AdkGovernedProcessLoopTest](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java) | Private Rig already invokes the real AdkAgentRunExecutor and local Runner with FakeChatModel and mock ledger/tool transport. |
| EVD-003 | [AdkRunBudgetPlugin](../../../stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java) | Existing real plugin reserves and settles on the model lifecycle and checks the model context window before dispatch. |
| EVD-004 | [Current injection fixture](../../../stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/governed-process-injection-v1.json) | Existing test-only versioned JSON uses wholly synthetic inputs. It remains an independent corpus. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR JUnit path and one test resource; no module generation or scaffolding.

## 3. Goals and Non-goals

Provide reproducible, fixed U-level evidence for structured output, policy denials and budget limits across five cases. Capture local observed duration as metadata. Real-provider quality, correctness of citations, real policy decisions and live latency SLO have independent evidence needs.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR test and test JSON | Affected | EVD-001–004 | Add five cases and one bounded summary | Case matrix, assertions, reports and safety | §7, §8, §10, §14, §15, §16 |
| Runtime Runner, budget and transport | Context-only | EVD-002/003 | Existing code and lifecycle reused | Record exact boundaries | §9, §11 |
| Database and UI | Not applicable | EVD-002 | Mock ports; no actual database, server or UI | Scope only | §11, §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Fixed versioned five-case input/output matrix | Must | Exact fixture and digest identify no-tool, allowed read, forged START argument, shared tool-entry cap and actual model context-window cap. Expected status and structured output are fixed. | Master T4-05, EVD-001/004 |
| REQ-002 | Reuse actual Runner and local governance boundaries | Must | Each case runs the real executor/Runner and applicable budget plugin; compare tool dispatch/denial, model and ledger-port reservation/settlement counts plus captured deterministic budget values. | Master T4-05/S07, EVD-002/003 |
| REQ-003 | Safe versioned test report | Must | One stable marker records fixture SHA, definition/schema/model/prompt fingerprint/tool identities, aggregate counts and local observed duration. Citation correctness is NOT_AVAILABLE and real model is NOT_RUN. | Master T4-05, EVD-004 |
| REQ-004 | Isolated verified test-only scope | Must | Exact RED altered expectation rejects; exact finite green passes with existing six injection cases and adjacent native completion tests. Preserve all production paths, SQL and dependency declarations. | Master T4-05, EVD-001/002 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Reproduce fixed quality evidence | Existing offline Maven/JUnit runner |
| ACTOR-002 | Scripted Fake ChatModel | Emit deterministic output and tool proposals | Test-only, no authority or provider |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Execute five fixed samples | ACTOR-001 | ACTOR-002, actual Runner, mocked ports | Exact JUnit selector | Fixed source and fixture | Expected structure, policy and budget facts observed | Any mismatch fails; no partial green report | No external side effect | REQ-001/002 | Private JUnit case | TEST-001/002 |
| UC-002 | Verify stable evidence boundary | ACTOR-001 | Surefire, local JSON resource | Finite evidence review | Successful UC-001 | One redacted record with versions, counts and limits | Missing/malformed marker or unexpected raw content fails review | Reproducible U summary | REQ-003/004 | Owning Surefire XML | TEST-002/003 |

## 5. Constraints, Assumptions, and Decisions

Existing Rig and test resources stay in AR. Mutate one expected dispatch count in an off-repo A phase solely to prove the fixed test catches a quality mismatch, then use the exact reviewed B fixture. Reuse existing pure selectors with ADK message capture false and exporters off. Do not enable app, provider, PostgreSQL, listener, collector or real credentials. Real-provider quality and performance require a separately authorized operator run.

## 6. Project Technology Context

Java 25, JUnit Jupiter, Mockito, Jackson 3, Spring AI and pinned ADK Java. The existing AR test vertical owns the Runner and governance plugin. Test uses current production types and mock boundaries only. No new dependency or architecture element.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-002 | Existing test owner and exact semantic method | One test | TEST-002 | PASS |
| Rule 2 | Yes | EVD-002/004 | Validate fixed resource and route/case expectations with existing boundaries | Existing Rig | TEST-001/002 | PASS |
| Rule 3 | No | EVD-002 | No business POJO or mapping change | Test values only | Scope | N/A |
| Rule 4 | No | EVD-002 | One test evidence marker without business logging or Bean | Existing JUnit | Scope | N/A |
| Rule 5 | Yes | EVD-002/004 | Existing APIs and JDK fingerprint/timing only | Test | TEST-002 | PASS |
| Rule 6 | Yes | EVD-004 | Jackson reads fixed fixture; no public JSON or ordinal change | Test resource and summary | TEST-002/003 | PASS |
| Rule 7 | No | EVD-002 | No application profile change | None | Scope | N/A |
| Rule 9 | No | EVD-002 | Existing one-rig fixture reuse | No new business design | Scope | N/A |
| Rule 10 | Yes | EVD-002 | Monotonic elapsed observation as Duration | Test summary | TEST-003 | PASS |
| Rule 11 | Yes | EVD-002/003 | Existing AR test ownership and mock ports only | All scope | Scope | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Local observed evaluation duration | Duration | java.time.Duration | Not persisted | Internal | Elapsed time has no zone | Surefire observation only | Sum of the five measured local executor intervals; no SLO threshold | Monotonic nanoTime mapped to reported duration | Existing clocks and contracts | TEST-003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Fixed resource and one method | Add | REQ-001/003 | Existing injection tests alone | No stable sample identity or quality result record | Five local scripted executions and one report | Add |
| Rig and real executor/plugin | Keep | REQ-002 | New harness/module | Existing Rig already exposes actual local boundaries | Mocked ports and current callbacks only | Keep |
| Existing test-report path | Keep | REQ-003/004 | A new CLI or report writer | Surefire retains test-local output | One marker with sanitized values | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Existing injection cases | None | Six scripted test inputs | Real runner, mock boundaries | Deterministic expected verdicts | Preserve S07 regressions |
| Fixed quality suite | None | Five fixed samples | Same runtime and mock ports | Exact report only after all expected facts | Versioned multi-dimension unit evidence |

### 7.1 System Architecture Design

Fixed JSON is read by AdkGovernedProcessLoopTest using existing Jackson. Its dedicated method executes five scenarios through the existing Rig, actual AdkAgentRunExecutor/ADK Runner and applicable AdkRunBudgetPlugin. Model, datastore and remote tool ports remain isolated. Surefire captures one summary after assertions pass.

### 7.2 High-Level Design

Evaluate no-tool completion, permitted read, forged START input denied before transport, shared tool-entry cap and an actual context-window rejection. Use exact result structures and status, compare policy/transport/model and reserve/settle evidence against expected fixture values. A small actual window in the fifth case is compared inside existing budget logic. Reuse all existing tests.

| Sample | Expected result | Reads | Model calls |
| --- | --- | --- | --- |
| no-tool | COMPLETED with exact writer output | 0 | 1 |
| authorized-read | COMPLETED with exact writer output | 1 | 2 |
| forged-start | FAILED, TIANJI_INVALID_COMMAND, empty output | 0 | 1 |
| tool-entry-cap | FAILED, TIANJI_BUDGET_EXCEEDED, empty output | 1 | 2 |
| model-context-cap | FAILED, TIANJI_BUDGET_EXCEEDED, empty output | 0 | 0 |

The model-context vector uses a window of 19 tokens, safety 5, expected input 10 and output cap 5. The actual plugin rejects before reservation and model subscription. An admitted call reserves 10 input/5 output tokens and settles 10 input/3 output tokens against the corresponding reservation. The allowed read uses the exact ToolDescriptor and canonical amount=7L.

### 7.3 Detailed Design

`fixed_fake_agent_quality_v1` opens `/evaluation/agent-run-quality-v1.json`, derives the SHA-256 from original bytes, and evaluates five distinct locked vectors. For each vector use an independent Rig, assert the final status and expected output shape/content, the observed tool response or refusal, trusted transport counts, model stream counts and relevant reserve/settle inputs and calls. Context-window denial must be observed before the Fake subscription/reservation as enforced by the actual plugin. Aggregates count only verified cases. One `TIANJI_AGENT_QUALITY_V1` marker records approved fixed identifiers, tool refs, fixture SHA, prompt fingerprint and numeric aggregates including elapsed time. Use NOT_AVAILABLE for citation correctness because the fixed output has no citations. Use NOT_RUN for real model. No raw input, output, owner, token, tool argument or reasoning is included.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkGovernedProcessLoopTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/resources/evaluation/agent-run-quality-v1.json` |
| Create | `docs/egon/spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md` |
| Create | `docs/egon/plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md` |
| Create | `docs/egon/evidence/2026-11-06-fixed-agent-quality-evaluation.md` |

## 9. Interface Definitions

Context-only. Reuse current Runner, budget plugin and tool scope under the existing private Rig. Surefire output is evidence for this test and introduces no production API or new CI artifact protocol.

## 10. POJO and Data Model Design

Test fixture uses a versioned, fixed JSON shape with five distinct scenario identities, synthetic inputs and per-case expected facts. Existing AgentExecutionResult and framework DTOs supply observed values. Any local Java carriers hold immutable test facts. No production model or persistence representation changes.

## 11. Database Design

Not applicable to the new fixture. Mockito observes existing ledger/budget calls. This supplies local policy and interaction evidence without actual SQL execution or transaction claims.

## 12. Frontend Page Design

Not applicable. There are no user-facing pages or interface changes.

## 13. Design Patterns and Architecture Principles

Considered a general evaluation pipeline. Existing Rig and direct fixed cases satisfy current repeatable test needs. A general registry, new lifecycle, tool abstraction and report command would introduce unused variation and state.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Exact method with one deliberately wrong expected tool dispatch count fails at that assertion, with no other failure or skip. |
| TEST-002 | Correct fixed fixture; exact class plus AdkNativeAgentCompletionTest; all five vectors, existing six injection cases and adjacent tests pass. |
| TEST-003 | Check exactly one marker in owning Surefire XML, known fixture SHA/version/fingerprints and verified totals. Real model NOT_RUN, citations NOT_AVAILABLE, local duration only. |

## 15. Non-functional and Cross-cutting Design

Use existing isolated Rig lifetimes, fixed data, no secrets, literal ADK content capture off and no exporters. Keep marker bounded to fixed identifiers, hashes and measured aggregate counts. Do not score model accuracy or infer latency SLO from this observed local duration.

## 16. Compatibility, Migration, Rollout, and Rollback

Only two test paths and three documents. Existing six injection vectors remain independently selectable. Revert the one test commit to remove this U evaluation. Operator real model, live S07 I/E and L remain separate.

## 17. Alternatives and Decisions

Independent runtime quality requires provider facts and approved isolated samples. The fixed Fake is suitable for repeatable expected-output and control-boundary evaluation now. No production config or external endpoint is introduced.

## 18. Risks and Open Questions

A passing fixed Fake corpus cannot establish provider quality, citations, IAM behavior, transaction correctness, live tool side effects or an SLO. Treat those items as pending until separately authorized runs yield their own evidence.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/10 | TEST-001/002 |
| REQ-002 | UC-001 | §7/11 | TEST-001/002 |
| REQ-003 | UC-002 | §7/14/15 | TEST-003 |
| REQ-004 | UC-001/002 | §8/14/16 | TEST-001/002/003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | Existing AR test owner | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003 | Existing Rig and real Runner | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing dependencies only | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact method and resource | None |
| MC-VALID-001 | Applicable | PASS | §7/10/14 | Exact vectors and expected assertions | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs and test values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No production mapping | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | One sanitized Surefire marker | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing Jackson/JUnit/JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Closed test resource and limited evidence | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Local elapsed duration only | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | No profile | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct local Rig | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Five paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact RED and finite regression | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator follow-up separate | None |

### 20.6 Final verdict

PASS — Ready for user review
