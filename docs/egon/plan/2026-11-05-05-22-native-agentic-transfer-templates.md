# Native Agentic transfer templates implementation plan

| Field | Value |
| --- | --- |
| Document | `2026-11-05-05-22-native-agentic-transfer-templates.md` |
| Template Version | `5` |
| Status | `Draft` |
| Created | `2026-11-05 05:22 Asia/Shanghai` |
| Updated | `2026-11-05 05:22 Asia/Shanghai` |
| Owner | `Root; only Root may run Maven, stage, commit, or authorize source execution` |
| Repository | `tianji-service` |
| Scope | `Three sequential source-only Steps: compiler/budget capability, native gate/runtime integration, then default-off AS templates` |
| Source Requirement | `Root full-read approval of the Review Spec for Plan drafting only; no source GO until Root fully reviews this Draft Plan` |
| Baseline Revision | `main@b0cf55c8d4696fac5bea30875c24f169503957a5 clean at Root inspection; native AR/AS capability-surface digest remains 10acddda5e6a4a63d1008f7f9d7f99d78ba158b500df4fac3f8aa18cbff03209` |
| Implements Spec | [Native Agentic transfer templates](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md) |
| Spec Status | `Review` |
| Spec Revision | `Updated 2026-11-05 05:22 Asia/Shanghai; Root-reviewed controlled corrections through explicit BaseLlm, exact snapshot, safe-fatal disposition and ISO-control harmonization; SHA-256 85c1538ed38c604005556148c6f019c52251e0941b188a46738019839fdfa369` |
| Effective Specs | [Primary native-template Spec](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md); [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§15.1, §17.3 T3-09, §17.4 S03` |
| Depends On Plans | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§15.1, §17.3 T3-09, §17.4 S03; normative remainder only` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

This Draft Plan implements the reviewed first T3-09 slice in exactly three sequential, path-limited commits. Step 1 adds the additive `NativeAgentCompilerStrategy` contract and per-execution `bindNative` model-budget capability, with no native execution or AS admission. Step 2 adds the `NativeAgentGate`, extends the existing plugin/executor for native callback governance, and proves real ADK Runner transfer/branch completion while no host template is registered. Step 3 supplies the two fixed default-off AS factories and host configuration, then runs the finite cross-module regression and inspects actual AR/AS JAR contents.

Every new behavior follows a genuine assertion RED -> compile seam A -> typed behavior RED -> minimum behavior B cycle. Root alone runs all Maven commands with `-DskipITs=true`, stages exact paths, commits once per Step and verifies the resulting tree. The optional PostgreSQL source test remains compile-only and is never selected. No application, listener, database, browser, provider credential or network-dependent model is started.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Native Agentic transfer templates](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md)
- Status: `Review`, intentionally not `Accepted` or `Implemented`.
- Revision: `Updated 2026-11-05 05:22 Asia/Shanghai`; Root-authorized current-role, explicit-BaseLlm, provider-ID, exact-snapshot, safe-fatal and ISO-control clarifications, EVENT-901 codec-test target-tree reconciliation and Draft Related Plan link; SHA-256 `85c1538ed38c604005556148c6f019c52251e0941b188a46738019839fdfa369`; clean execution baseline `b0cf55c8d4696fac5bea30875c24f169503957a5` with unchanged native surface digest.
- Approval evidence: Root read the full design and approved its A-E corrections, `PLAN-CLAR-901`, and the pre-budget current-expected-role clarification for Plan drafting only. This is the same original T3-09 goal, not user acceptance and not source execution authorization.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Native-template Spec](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md) | Review / SHA `85c1538ed38c604005556148c6f019c52251e0941b188a46738019839fdfa369` | all | Complete first-slice requirements, contracts, reconciled test tree, relationships, security, tests and rollout, including current-role pre-budget fencing |
| Normative roadmap dependency | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) | current source at planning inspection | §15.1; §17.3 T3-09; §17.4 S03 | Keeps full task decomposition/Graph remainder Required and fixes same-AgentRun acceptance boundary |

### 2.3 Superseded or excluded content

None is superseded. Excluded from implementation: dynamic task decomposition, Graph API/scheduler/DSL, AgentTool/nested Runner, same-run resume/checkpoint rehydration, signed artifact/factory-binary governance, public API/C/CL changes, POM/dependency changes, SQL/schema, provider credentials/network, frontend and live runtime acceptance.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-901` | §4/§7 | Function-compatible compiler strategy only enables native path | old Functions unchanged; strategy recognized | Step1 strategy; Step2 executor |
| `REQ-902` | §4/§7/§9 | bind canonical topology, actual Agent identity, explicit prebound BaseLlm identity/name, host ChatModel identity, route/policy and schema v1 | only `LlmAgent.model().flatMap(Model::model)` may unwrap; distinct wrappers around the same BaseLlm pass, absent/name-only/implicit or same-name different BaseLlm fails pre-model | Steps1-3 metadata/budget/tree |
| `REQ-903` | §4/§16 | AS default off and host-model-only | off zero factories; on exact two/no credentials | Step3 config/factories |
| `REQ-904` | §4/§7 | fixed CODE_FIRST definitions, empty config/tools/allowlist, maxToolCalls0 | invalid snapshot pre-model failure | Steps1-3 validation |
| `REQ-905` | §4/§7/§10 | conditional root -> one child -> root | quick/deep success; max2/one returned child | Steps2-3 policy/Runner |
| `REQ-906` | §4/§7/§10 | supervisor root/researcher/root/critic/root | exact order/max4/two unique returned children | Steps2-3 policy/Runner |
| `REQ-907` | §4/§7 | unique nonreserved output keys; handoff nonfinal | overlap rejected; child outputKey absent | Steps1-3 tree/Runner |
| `REQ-908` | §4/§7/§9 | exact pinned AgentTransfer FunctionTool only | all same-name/method/schema spoofs fail | Step2 Gate |
| `REQ-909` | §4/§7 | current MODEL/session/context/lease CAS before native action | no action/state on auth/CAS failure | Step2 plugin/gate/sink |
| `REQ-910` | §4/§7 | actual context+safe ToolContext call ID, absent/exact-empty versus safe-present provider ID, strict before single-use | absent/empty binds generated actual ID once; unsafe present/actual IDs reject; safe present matches exactly; repeated before fails | Step2 collector/gate |
| `REQ-911` | §4/§9 | exact authorization event payload and count | safe payload/order; no completion claim | Step2 event sink/codec |
| `REQ-912` | §4/§7 | tracking predicate includes native gate; actual Agent object must equal the Gate FSM's current expected role before budget admission | no-tool native context admitted; known critic/other child or stale child entry fails pre-budget; confirmed return-to-root continues | Step2 gate/plugin/context tests |
| `REQ-913` | §4/§7/§10 | current complete envelope, absent/exact-empty versus safe-present provider ID, and exact safe child-text bounds | ID class plus char/control/UTF8 boundaries; no stale scan | Step2 collector/Runner |
| `REQ-914` | §4/§7/§9 | before matches capture; after confirms actual action; identical after replay only | mismatch fatal; one output | Step2 gate callbacks |
| `REQ-915` | §4/§7 | sticky sanitized fatal token dominates later root-looking final without changing existing terminal classification | callback/signal race preserves SUSPENDED, current-run resolver, TIMED_OUT or safe FAILED and cannot complete | Step2 gate/control/executor |
| `REQ-916` | §4/§7 | genuine current root final + required current child outputs | exact agents Map/root text | Step2 executor/Runner |
| `REQ-917` | §4/§7/§9 | per-execution `bindNative` view bound to the exact snapshot object, shared model budget, zero tools | exact snapshot reference/model/profile; semantically equal copy and forged CONFIG reject; custom default deny | Steps1-2 estimator/plugin |
| `REQ-918` | §4/§7/§16 | one QUEUED claim, no resume; future rehydration gate | no checkpoint/table/resume path | Step2/3 static regression |
| `REQ-919` | §4/§16 | no route/DTO/schema/POM drift; legacy factories/tools compatible | focused + full finite regression/JAR audit | all Steps |
| `REQ-920` | §4/§18 | full T3-09 task decomposition remains Required | evidence never marks T3-09 complete | docs/evidence/all Steps |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

1. Publish compile-safe strategy metadata and budget binding first. The executor does not recognize the strategy yet, so no native execution/admission can occur. This isolates model-accounting authority and proves old CONFIG/plain CODE_FIRST behavior.
2. Add the native Gate and integrate it into the existing plugin/executor only after Step1 is committed. Synthetic test strategies drive callback and real Runner coverage; no AS factory exists, so production configuration cannot select a native template.
3. Add AS host properties, fixed factories and the default-off named map last. Both safe cores are already tested before exposure. Final finite regression and JAR inspection close source delivery.

No Step may overlap another Step: Step2 consumes the committed Step1 symbols and Step3 exposes the committed Step2 behavior. Disjoint workers may operate only inside the current Step under §4.3 and Root integrates/stages one sequential Step commit. Worker Error Step4 is already committed at `b0cf55c8d4696fac5bea30875c24f169503957a5`; the native surface digest is unchanged, and Root must still recompute it before Step1. Material drift returns to this Plan/Spec.

### 4.2 Test-first strategy

| Step | Initial assertion RED | Compile seam A | Typed behavior RED | Minimum behavior B |
| --- | --- | --- | --- | --- |
| 1 | reflection for absent Strategy and `bindNative` | interface/nested immutable signatures + default fail-closed method | topology/hash/model-binding/profile/legacy matrices | canonical TemplateSpec and JTokkit bound view |
| 2 | reflection for absent Gate/plugin native seams | Gate callback/result/fatal signatures and nullable native path | provenance/context/FSM/collector/Runner/completion/event matrices | Gate state policy + plugin/executor wiring |
| 3 | reflection/resource for absent properties/factories/map/config keys | properties/factory/config Bean signatures | tree/model/callback/off-on wiring matrices | two fixed factories and conditional named map/YAML |

Every RED command must fail only named JUnit assertions. A compile, dependency, fixture, cache, listener, database or network error is not RED and returns to the owning test/seam file.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Spec/Plan Root execution GO | Only disjoint workers inside Step1 after Root assigns nonoverlapping exact file ownership | No Step2/3 path, no shared file/staging scope | Defines consumed ABI/accounting before any runtime consumer |
| Step 2 | Step1 committed and path hashes verified | Only disjoint workers inside Step2, for example Gate/codec test versus plugin/executor test preparation, under the listed integration order | No Step3 path and no two workers on plugin/executor/Gate or one staged path | Consumes the committed strategy/bound estimator and integrates one atomic runtime result |
| Step 3 | Step2 committed and path hashes verified | Only disjoint workers inside Step3, such as the two factory test/implementation pairs, before Root integrates configuration/YAML | No earlier-Step edit and no shared configuration/YAML ownership | Exposes already-governed behavior only after runtime is committed |

The three Step commits remain strictly sequential: Step N+1 never edits, validates as complete, stages or commits before Step N's semantic commit exists. Within the current Step, Root may delegate nonoverlapping files in parallel; workers do not stage or commit, do not revert another worker, and must preserve the RED -> compile A -> typed RED -> minimum B -> GREEN integration order. Root alone reviews the combined diff, stages the exact Step paths and creates the one Step commit.

### 4.4 Commit boundaries

Exactly three commits, one per Step. Root runs/integrates RED/GREEN in the declared order, checks every Manual Check/Rule, stages only explicit Step paths, commits, records the hash/file list, and confirms unrelated concurrent work remains. Disjoint same-Step worker edits are allowed only under §4.3; shared files are single-owner and no file appears in more than one Step.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity/section | Current evidence | Direct/reuse alternative | Cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Compiler Strategy | §7.0/INTERNAL-901 | plain Function map lacks metadata marker | second registry or name convention | one subtype/file | Implement Step1 |
| Native model binding | §7.3/INTERNAL-906 | JTokkit CONFIG-only `require`; actual model identity matters | global relaxation/thread-local/forged snapshot unsafe | one default SPI + private view | Implement Step1 |
| Native Gate | §7.0/INTERNAL-902-905 | plugin lacks transfer policy/collector | name-only or ordinary tool path unsafe | one per-run object/state | Implement Step2 |
| Authorization event | EVENT-901 | existing append CAS and string event type | new table/tool journal unnecessary | one existing DB call per transfer | Implement Step2 |
| Two fixed factories | §7.0/§13 | no reviewed native factories | generic DSL/Graph over-scoped | two host files | Implement Step3 |
| Properties/config | §15/§16 | one base profile/default empty map | client configuration forbidden | one record/config/yml | Implement Step3 |
| New API/table/dependency | Spec Remove/N/A | existing contracts/storage sufficient | direct reuse | none | Do not implement |

No fetch-then-forward operation, client-supplied security/model/topology fact, speculative cache/layer or duplicate result/DTO is compiled into this Plan.

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Prerequisites | Produces | Consumers/unblocks | Step |
| --- | --- | --- | --- | --- | --- | --- |
| Strategy metadata/hash | 901-907 | NativeAgentCompilerStrategyTest | current Function ABI | public internal Strategy/TemplateSpec | executor/Gate/AS | 1 |
| Native budget binding | 902/917/919 | JTokkit test | current profiles/model map | bound estimator view | plugin/executor | 1 |
| Transfer gate/FSM | 905-915 | NativeAgentGateTest | Step1 spec/hash | exact callback policy/fatal signal | plugin/executor | 2 |
| Plugin context/collector | 909-917 | AdkRunContextTest/Runner | Gate | native callbacks/tracking | executor | 2 |
| Completion/event | 911/915-919 | completion/codec tests | Gate/plugin | current outputs/event | result consumers | 2 |
| Host templates | 902-907 | factory tests | Steps1-2 | two fixed strategies | AS config | 3 |
| Default-off admission | 903/919 | ContextRunner/config scan | factories/runtime map | exact named map/keys | deployment | 3 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | existing AR domain/application/infrastructure/autoconfigure + AS host configuration | preserve Root technical vertical | exact current packages; no module/layer | all; MC-ARCH |
| Reuse/dependency | Function map, SpringAi adapter, plugin, ledger CAS, estimator, Runner | reuse all; no dependency | no POM/Components change | all; MC-REUSE/DEP |
| Naming/model/validation | current behavior suffixes/immutable records/manual third-party callback checks | Strategy/Gate/Factory/Configuration/Properties + nested records | compact validation, no Converter | all; MC-NAME/VALID/MODEL |
| Bean/log/util/JSON/time/config | explicit @Bean/Qualifier config, existing logger/codec/Instant | no business Bean/logger; exact event Map/typed props/current time | named factory map, strict properties, safe event payload | 2-3; MC-BEAN/JSON/TIME/CONFIG |
| Complex pattern | no native transfer policy | Strategy + Gate state/two-phase confirmation | exact policy participants/tests | all; MC-PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Model adapter/identity | Spring AI/Agent Flow adapter + ADK explicit Model getter | Components `SpringAiModelAdapterFactory`; pinned `LlmAgent.model().flatMap(Model::model)`/`BaseLlm.model()` | adapter fit; lazy wrapper identity is not fit | reuse adapter; bind unwrapped BaseLlm identity only | none | Steps2-3/MC-REUSE |
| Factory registry | current named Function map | AR auto-config/executor | fit with subtype | reuse | one interface | Step1 |
| Auth/audit | tool PDP vs MODEL auth + ledger append | plugin/ledger | MODEL/CAS is correct owner | reuse | Gate policy only | Step2 |
| Branch output | outputKey/events vs current response/action | ADK bytecode/projector | existing sources alone insufficient | two-phase gate | no storage | Step2 |
| Budget | current estimator/JTokkit | CONFIG-only binding gap | math/profile fit; binding gap | additive `bindNative` | no dependency | Step1 |
| Topology | Graph/DSL vs fixed strategies | roadmap/Spec | generic options over-scoped | two fixed factories | no framework | Step3 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2/§8/§10 | existing role suffixes | tests -> Strategy/Gate/Factory/Configuration/Properties | exact roles; nested values local | compile/type inventory | 1-3 | PASS |
| Rule 2 | Spec §6.2/§7/§9 | third-party callbacks/direct internal entry | tests before compiler/gate/config | exact pre-mutation checks; no Controller-only validation | negative matrices | 1-3 | PASS |
| Rule 3 | Spec §6.2/§10 | immutable snapshots/records | typed constructor tests before records | defensive copies/identity/redaction; no converter | constructor tests | 1-3 | PASS |
| Rule 4 | Spec §6.2 | no new business Service/Bean; existing config style | AS config only | explicit named map and Qualifiers; no business DI/logger | context/static review | 3 | N/A for business classes; wiring checked |
| Rule 5 | Spec §6.2/§15 | JDK/current SDK sufficient | all Java paths | no utility/POM | import/POM scan | 1-3 | PASS |
| Rule 6 | Spec §6.2/§9 | existing AgentEventDTO/Jackson codec | codec test -> Gate event | exact type/payload/privacy/unknown compatibility | AgentJsonCodecTest | 2 | PASS |
| Rule 7 | Spec §6.2/§15 | one AS application.yml | properties/config test -> yml | complete four-key base profile | key scan/ContextRunner | 3 | PASS |
| Rule 9 | Spec §6.2/§13 | two topology variants/callback phases | Strategy -> Gate -> factories | no branch sprawl/dynamic DSL | state/topology tests | 1-3 | PASS |
| Rule 10 | Spec TIME-901 | existing Instant/Duration/DB clock | plugin/executor tests | no Date/new clock; current deadline/lease | time/forbidden import | 2 | PASS |
| Rule 11 | Spec §6.1/§8 | current technical vertical/Root exception | every target current package | no new module/layer | tree/POM audit | all | PASS |

### 4.9 Time semantics and conversion contract

Time contract: `Required`

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-901` | existing `deadlineAt`, `leaseExpiresAt`, event `occurredAt` | Occurred instant | `Instant` | UTC instant | UTC Z | Not applicable: elapsed run fencing has no business-zone calculation | Not applicable: no new UI or display conversion | Recheck deadline and lease before authorization/CAS; no local calendar or DST branch | Existing database clock and PostgreSQL precision; no new Clock | Existing UTC AgentRun and AgentEvent wire/storage fields remain unchanged | Step 2; `TEST-906`, `TEST-910`, `TEST-914` and the exact §8 S2 selector |

## 5. Change File Tree

```text
stellar-enclosure-tianji-agent-runtime/
  src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/
    NativeAgentCompilerStrategy.java                         CREATE [Step1]
    AgentModelBudgetEstimator.java                           MODIFY [Step1]
    JTokkitChatModelBudgetEstimator.java                     MODIFY [Step1]
    NativeAgentGate.java                                     CREATE [Step2]
    AdkRunBudgetPlugin.java                                  MODIFY [Step2]
    AdkAgentRunExecutor.java                                 MODIFY [Step2]
  src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/
    NativeAgentCompilerStrategyTest.java                     CREATE [Step1]
    JTokkitChatModelBudgetEstimatorTest.java                 MODIFY [Step1]
    NativeAgentGateTest.java                                 CREATE [Step2]
    NativeAgentTransferRunnerTest.java                       CREATE [Step2]
    AdkNativeAgentCompletionTest.java                        CREATE [Step2]
    AdkRunContextTest.java                                   MODIFY [Step2]
  src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/
    AgentJsonCodecTest.java                                  MODIFY [Step2]
stellar-enclosure-tianji-agent-server/
  src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/
    NativeAgentTemplateProperties.java                       CREATE [Step3]
    ConditionalResearchAgentFactory.java                     CREATE [Step3]
    SupervisorResearchAgentFactory.java                      CREATE [Step3]
    TianjiNativeAgentTemplateConfiguration.java              CREATE [Step3]
  src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/
    ConditionalResearchAgentFactoryTest.java                 CREATE [Step3]
    SupervisorResearchAgentFactoryTest.java                  CREATE [Step3]
    TianjiNativeAgentTemplateConfigurationTest.java          CREATE [Step3]
  src/main/resources/application.yml                         MODIFY [Step3]
docs/egon/
  spec/2026-11-05-04-45-native-agentic-transfer-templates.md CREATE [Step1]
  plan/2026-11-05-05-22-native-agentic-transfer-templates.md CREATE [Step1]
  evidence/2026-11-05-native-agent-compiler.md               CREATE [Step1]
  evidence/2026-11-05-native-agent-gate.md                   CREATE [Step2]
  evidence/2026-11-05-native-agent-templates.md              CREATE [Step3]
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java` | absent; existing Code-First fixtures | ABI/metadata/hash tests | compiler contract RED/GREEN | 1 | 901-907/919 | S1 tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java` | CONFIG profile/math tests | native binding + legacy matrices | estimator authority tests | 1 | 902/917/919 | S1 tests |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java` | plain Function map only | Strategy/TemplateSpec/topologyHash | additive compiler metadata | 1 | 901-907 | S1 tests/JAR |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java` | five-method estimator SPI | default fail-closed `bindNative` + nested binding | backward-compatible budget seam | 1 | 902/917/919 | S1 tests/compile |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java` | CONFIG-only `require` | private exact bound execution view | native profile/model authority | 1 | 902/917/919 | S1 tests |
| CREATE | `docs/egon/spec/2026-11-05-04-45-native-agentic-transfer-templates.md` | reviewed `/tmp` artifact | Review Template9 governing Spec | design authority | 1 | 901-920 | strict Spec |
| CREATE | `docs/egon/plan/2026-11-05-05-22-native-agentic-transfer-templates.md` | this reviewed `/tmp` draft | Draft Template5 three-Step Plan | execution authority boundary | 1 | 901-920 | strict Plan |
| CREATE | `docs/egon/evidence/2026-11-05-native-agent-compiler.md` | absent | actual S1 evidence | honest Step1 proof | 1 | 901-907/917/919/920 | S1 outputs |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java` | absent | full provenance/FSM/collector tests | Gate RED/GREEN | 2 | 905-916/918 | S2 tests |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java` | no transfer state owner | bound Gate/state/audit/fatal/completion | secure native control | 2 | 905-918 | S2 tests/JAR |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java` | ordinary tool/context tests | native no-tool/current-role/order regressions | plugin boundary proof | 2 | 909/912/913/917 | S2 tests |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java` | no native Runner proof | scripted BaseLlm/real Runner transfers | pinned SDK behavior proof | 2 | 905-916 | S2 tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java` | toolScope-only tracking | native Gate callbacks/tracking/fatal | single plugin authority | 2 | 909-917 | S2 tests |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java` | embedded legacy output assembly | native selection/completion/control tests | executor RED/GREEN | 2 | 902/915-919 | S2 tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java` | generic Jackson/hash tests | EVENT-901/unknown-type regressions | additive event compatibility | 2 | 911/919 | S2 tests |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java` | plain factory/plugin/projector/output | strategy binding/Gate/control/native result | runtime orchestration | 2 | 901-919 | S2 tests/JAR |
| CREATE | `docs/egon/evidence/2026-11-05-native-agent-gate.md` | absent | actual S2 evidence | honest Gate/runtime proof | 2 | 905-920 | S2 outputs |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java` | absent | default-off/on/resource/map tests | AS wiring RED/GREEN | 3 | 902-904/919 | S3 tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java` | no native keys binder | strict four-key immutable Properties | host metadata validation | 3 | 902-904 | S3 tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactoryTest.java` | absent | exact conditional tree tests | factory RED/GREEN | 3 | 902/905/907 | S3 tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactory.java` | no fixed conditional strategy | root/quick/deep Strategy | conditional template | 3 | 902/905/907 | S3 tests/JAR |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactoryTest.java` | absent | exact supervisor tree tests | factory RED/GREEN | 3 | 902/906/907 | S3 tests |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactory.java` | no fixed supervisor strategy | root/researcher/critic Strategy | supervisor template | 3 | 902/906/907 | S3 tests/JAR |
| CREATE | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java` | runtime empty fallback map | conditional exact two-entry named map | default-off host assembly | 3 | 903/919 | S3 tests/JAR |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | one base profile, no native keys | four complete default-off keys | operator activation surface | 3 | 903/919 | S3 resource/JAR |
| CREATE | `docs/egon/evidence/2026-11-05-native-agent-templates.md` | absent | actual S3/final/JAR evidence | honest final proof | 3 | 901-920 | final outputs |

Every path appears in exactly one owning Step. POMs, Components, C/CL, Controllers, permissions, SQL/manifests, frontend and existing migrations are intentionally absent.

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- Current native baseline: clean `main@b0cf55c8d4696fac5bea30875c24f169503957a5` after Worker Error Step4 committed. Root confirmed that commit changed no planned native AR/AS input.
- The ordered AR/AS capability-surface digest remains `10acddda5e6a4a63d1008f7f9d7f99d78ba158b500df4fac3f8aa18cbff03209`, identical to the original `c731f3f` inspection. It is the SHA-256 of the textual `shasum -a 256` manifest, in this exact order: root/AR/AS POMs; runtime auto-config; executor; plugin; estimator SPI/JTokkit; Agent codec/ledger; RunContext/JTokkit/codec tests; AS application.yml; Components SpringAiModelAdapterFactory. Root recomputes this exact ordered manifest immediately before Step1. A later different HEAD with the same surface digest is allowed; a changed surface is reviewed path-by-path and any material contract drift returns to the Spec/Plan. HEAD equality alone is never the gate.
- After each Step, its evidence records SHA-256 for every committed Step path and the next Step verifies those path hashes before editing. This permits an unrelated Process-server commit without silently accepting AR/AS drift.
- Root alone runs Maven, validates, stages exact paths and commits. Preserve unrelated staged/unstaged/untracked work and never stage broad directories.
- Before each Step, recheck the prior Step commit, target paths, ADK/GenAI packaged JAR hashes and no overlapping agent-template implementation.

Root's exact pre-Step1 capability-surface command, run from `tianji-service`, is:

```bash
shasum -a 256 \
  pom.xml \
  stellar-enclosure-tianji-agent-runtime/pom.xml \
  stellar-enclosure-tianji-agent-server/pom.xml \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodec.java \
  stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentLedgerService.java \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java \
  stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java \
  stellar-enclosure-tianji-agent-server/src/main/resources/application.yml \
  ../Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/runtime/SpringAiModelAdapterFactory.java \
  | shasum -a 256
```

Expected result at the documented baseline is the digest above. A mismatch is not auto-accepted and does not authorize edits; Root prints the individual manifest, isolates changed paths and applies the material-drift rule.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK/build | JDK25 path + `./scripts/verify-source-reactor.sh` | source repositories present; no app start | compile/JUnit/package only |
| Offline settings | `/tmp/tianji-compat-fix/settings.xml` with `-o` | exists, no credentials/mirrors; missing cache is environment failure | no network |
| ADK primary API | packaged ADK 1.13 SHA `36f13af457b1657e1ef67cf5361f4eb59009a2b911bc5b0de61019f3cbea4e32`; GenAI 1.77 SHA `454ecb0de8009da6191599a198df93e1e669c5937b9b0b4873c622bc8490f8a5` | public Method/annotations/callbacks and exact `LlmAgent.model()->Model.model()->BaseLlm` chain match Spec; `resolvedModel()` prohibited for binding | javap/static + Runner |
| Runtime | no PostgreSQL/provider/listener/App/browser | optional PG test compiles but selectors exclude it | RuntimeUnverified |
| Validation owner | Root | full output/exit/test counts recorded | no delegated PASS claim |

### 6.3 Immutable constraints and approved decisions

- Spec SHA `85c1538ed38c604005556148c6f019c52251e0941b188a46738019839fdfa369`; Review status; three Steps only; no source GO yet.
- Existing Function map/constructors/routes/DTOs/event envelope/database/POMs remain compatible.
- Exact codeRefs, schema versions `"1"`, topology names/output keys/caps, transfer provenance, safe-text bounds, event payload and `bindNative` design are immutable for this Plan.
- No AgentTool/Graph/DSL/nested Runner/tool PDP/journal/new table/resume/provider credential.
- Maven commands always include `-DskipITs=true`; no PG/listener/provider test selector.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-901` | `TemplateSpec.topologyHash(String snapshotPolicyVersionRef)` computes the per-execution canonical hash; no stored/precomputed field or CompiledTemplate type | Root approval; Spec §7.3/§10 | Preserves exact semantic fields and audit hash while adding validated policy ref at execution | Method name/placement only; behavior unchanged |
| `PLAN-CLAR-902` | `NativeModelBinding` is a nested immutable value of `AgentModelBudgetEstimator`, not a new top-level file | Spec §9/§10 and current SPI style | Keeps public-internal surface/file count minimal | Nested type path changes only |
| `PLAN-CLAR-903` | `NativeAgentGate#requireExpectedAgent` is invoked in native `beforeModelCallback` before MODEL authorization and durable budget reservation, and again before admission is committed; it accepts only the exact Agent object for the FSM's current role | Root clarification; Spec REQ-912 and §7.3 callback rule 0; current plugin reserves only after its initial context checks | Tightens the already-selected Gate FSM/context fence without a new type, state store, API or legacy branch | If callback ordering or current-role ownership differs, source execution returns to the Spec before moving the check after budget spend |
| `PLAN-CLAR-904` | The pinned ADK 1.13 order is `afterModel(original LlmResponse)` -> build Event -> `Functions.populateClientFunctionCallId` -> build ToolContext -> `beforeTool` -> native Method -> `afterTool`; absent or exact-empty provider ID is therefore unbound and beforeTool binds the later generated/nonblank actual ID, while a safe present ID must match | Read-only packaged JAR bytecode at the Spec hashes: `BaseLlmFlow#handleAfterModelCallback`, Event construction, `Functions#populateClientFunctionCallId`, `Functions#postProcessFunctionResult` | Confirms the already-approved optional-ID contract and chooses no new identifier source | Any future JAR order/API drift blocks enablement and returns to the Spec |
| `PLAN-CLAR-905` | Native `onToolErrorCallback` sets sticky fatal and emits the Gate control signal; it returns `Maybe.empty()` when the incoming error already has the matching safe boundary classification so pinned ADK rethrows it, otherwise `Maybe.error(safeBoundaryFailure)` sanitizes a raw unknown failure. It never returns a Map/model-recoverable FunctionResponse | Pinned `Functions#processFunctionResult`: empty falls through to `Single.error(original)`, an error Maybe propagates that safe error, and only a nonempty Map becomes a FunctionResponse; Root selected no new error transport shape | Preserves fail-fast v1 policy, safe disposition and privacy while retaining defense in depth against future SDK/other callback conversion | Any future SDK change in callback semantics blocks enablement and returns to the Spec |
| `PLAN-CLAR-906` | `AgentJsonCodecTest.java` is an explicit Step2 target path | Spec EVENT-901/§14 requires codec and unknown-event compatibility; Root explicitly required that regression, while the original §8.2 tree omitted only the path | Reconciles an already-required test with the target tree; adds no production/API/C/CL/codec behavior | If the regression requires a production codec/contract edit, return to the Spec rather than expand this path |
| `PLAN-CLAR-907` | Gate model binding uses only `LlmAgent.model().flatMap(Model::model)` to obtain the explicit BaseLlm, compares that BaseLlm by object identity across root/children, and compares `BaseLlm.model()` to the pinned model name; it never calls `resolvedModel()` | Pinned ADK 1.13 `javap`: `resolvedModel()` returns a Model wrapper, while `Model.model()` owns the optional explicit BaseLlm; Root caught the invalid wrapper comparison during full Plan review | Corrects the getter/API mechanics while preserving the already-approved exact prebound-model identity contract | A future getter/API change blocks source execution and returns to the Spec |
| `PLAN-CLAR-908` | Provider FunctionCall ID absent or exactly empty is unbound because pinned ADK populates both; a present nonempty provider ID and the actual ToolContext ID must each be untrimmed, nonblank and ISO-control-free, and a retained provider ID must equal the actual ID | Pinned `Functions.populateClientFunctionCallId` handles missing/empty; Root clarification closes whitespace/control ambiguity | Makes the existing optional-ID/actual-ID decision executable without normalization or a second ID source | Different SDK population behavior or accepted unsafe IDs blocks enablement |
| `PLAN-CLAR-909` | The private JTokkit native view accepts only the exact `AgentExecutionSnapshot` object passed to `bindNative`; `candidateSnapshot != boundSnapshot` fails before all five calculations | Executor/plugin reuse one snapshot field; Root clarification forbids semantic-hash fallback and forged CONFIG copies | Prevents an equal-looking snapshot from borrowing a bound model/profile while adding no global/thread-local state | Any caller that copies snapshots must be redesigned in the Spec rather than weakening identity |
| `PLAN-CLAR-910` | Gate fatal state is a sanitized classification token, not a generic error or retained Throwable; callback and replayed control channels reconstruct the same authorization decision, ledger code, timeout cause or safe policy/unknown failure | Executor already distinguishes `AgentExecutionAuthorization.Failure`, revision/lease `AgentLedgerException`, timeout causes and generic failures; Root found the race where a generic Gate signal could win and change disposition | Preserves the Spec's existing SUSPENDED/current-run resolver/TIMED_OUT/FAILED semantics under either race winner and adds no error transport/API | Any failure that cannot be classified without retaining sensitive details blocks implementation and returns to the Spec |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Publish the native compiler and exact per-execution budget binding

- Requirements: `REQ-901`, `REQ-902`, `REQ-904`, `REQ-907`, `REQ-917`, `REQ-919`, `REQ-920`; documentation traces all requirements.
- Dependencies: Root source execution GO against a freshly audited clean baseline.
- Baseline state: Function map exists; no native marker/metadata; estimator CONFIG binding is exact and plain CODE_FIRST unsupported; no native execution/admission.
- Observable outcome: additive compiler metadata and exact native budget binding are source-complete and tested while executor/AS cannot use them.
- End state: Strategy/TemplateSpec and `bindNative` per-execution view exist; native Gate, plugin/executor branch and AS factories remain absent.
- Test-first gate: `Required` — reflection tests first fail for absent Strategy/TemplateSpec/`bindNative`; after compile seam A, typed tests fail exact metadata/hash/model-profile behavior before implementation B.
- Manual Checks: all 17 are evaluated separately in this Step: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`. Applicable and proven by Files1-5/8: ARCH, REUSE, DEP, NAME, VALID, MODEL, UTIL, PATTERN, SCOPE, TEST, BLOCKER. Evidence-backed N/A in this Step: CONVERT (no cross-layer projection), LOG/BEAN (no concrete Spring-managed business class), JSON (no wire/Event serialization change), TIME (no new/touched time field or boundary), CONFIG (no configuration key/profile change).
- Literal Rules: all ten are evaluated separately: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11`. Applicable: Rules 1, 2, 3, 5, 9, 11. Evidence-backed N/A in this Step: Rule 4 (no managed business class/injection), Rule 6 (no external JSON contract), Rule 7 (no profile key), Rule 10 (no new/touched date/time symbol). Rule 11 is blocking for every file.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java`

- Purpose: Establish assertion RED, then exact typed metadata/tree/hash compatibility.
- Symbols: presence/API reflection; `plain_function_is_not_native`; conditional/supervisor synthetic TemplateSpec constructors; canonical hash fields/order/policy input; object-identity exclusion; output key/depth/cap validation.
- Repository evidence: Code-First factory tests in `AdkAgentRunExecutorPostgresTest`; ADK tree builders; JUnit5/Mockito style.
- Dependencies and consumers: Step1 Files3; synthetic BaseAgent/ChatModel only; later executor/factories consume the contract.
- Why now: Locks the additive ABI and canonical hash before production types.
- Contract/signature changes: typed final assertions for `extends Function<Scheduler,BaseAgent>`, `templateSpec`, `usesChatModel`, nested topology/spec/agent metadata and `topologyHash(String)`.
- Input/output and state mapping: fixed semantic metadata + validated policy ref -> lower-hex64; same semantic objects with different Java identities -> same hash.
- Error and edge behavior: null/blank/duplicate/reserved keys, wrong depth/caps/tree edges/model/schema values reject; plain Function remains non-native.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-PATTERN-001`, `MC-ARCH-001`, `MC-SCOPE-001`, `MC-TEST-001` — semantic Strategy/value names, constructor validation, exact approved pattern/package and focused tests.
- Literal rule enforcement: `Rule 1, 2, 3, 5, 9, 11` — semantic Strategy and immutable local values; exact validation; no utility/layer.
- Implementation pseudocode:

```java
@Test missing_strategy_is_assertion_red() {
  assertDoesNotThrow(() -> Class.forName(NATIVE_STRATEGY_FQCN)) // initial RED
}
@Test canonical_hash_uses_only_semantic_fields_and_snapshot_policy() {
  first = templateSpec(fixedMetadataAndTreeObjectsA)
  second = templateSpec(equalMetadataAndDifferentObjectsB)
  policyOne = "policy@1#" + "a".repeat(64); policyTwo = "policy@2#" + "b".repeat(64)
  assertEquals(first.topologyHash(policyOne), second.topologyHash(policyOne))
  assertNotEquals(first.topologyHash(policyOne), first.topologyHash(policyTwo))
  assert rejected duplicate/reserved output keys and invalid topology caps
}
```

- Verification contribution: Step1 RED/GREEN; TEST-901/902; proves no identityHashCode/toString input.
- After this file: reflection RED initially; after File3 typed matrix compiles and remains behavior RED until File3 B.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java`

- Purpose: Define default fail-closed native binding and exact builtin per-execution view.
- Symbols: reflection RED for `bindNative`; exact binding success; exact bound snapshot reference; semantically equal copied snapshot and forged CONFIG snapshot denial; CONFIG/plain CODE_FIRST compatibility; custom default denial; alias bean A/bean B same model name different identity; route/policy/currency/default drift; all five bound-view methods.
- Repository evidence: existing descriptor/profile/window/fingerprint/current-default tests.
- Dependencies and consumers: Files4-5; uses existing ModelBudgetProperties/ChatModel fakes, no provider.
- Why now: Budget authority must be fixed before any native executor branch.
- Contract/signature changes: final typed `bindNative(snapshot, NativeModelBinding)`; no actualUsage overload and no snapshot copy.
- Input/output and state mapping: CODE_FIRST empty config + trusted binding + snapshot route/policy -> private view closed over exact Bound and the exact snapshot object; each of estimate/actualUsage/contextWindow/modelAdapterFingerprint/contextPolicy first checks candidate snapshot reference identity.
- Error and edge behavior: default SPI throws feature unavailable; any snapshot object other than the bound reference—including a semantically equal copy and forged CONFIG snapshot—fails; wrong codeRef/bean/name/model identity/route/policy/currency/default/config fail; old CONFIG and unsupported plain CODE_FIRST assertions unchanged.
- Standards impact: `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — reuse existing profiles/math, add no dependency, validate exact binding and preserve current time/scope behavior.
- Literal rule enforcement: `Rule 2, 3, 5, 10, 11` — immutable binding, exact boundary validation, current java.time/architecture.
- Implementation pseudocode:

```java
assert reflection RED for AgentModelBudgetEstimator.bindNative
snapshot = codeFirstSnapshot(emptyConfig, exactRoutePolicy)
binding = new NativeModelBinding(CODE_REF, "modelBeanA", MODEL_NAME, exactChatModelA)
bound = jtokkit.bindNative(snapshot, binding)
assert bound.estimate(snapshot,...)/actualUsage(snapshot,...)/contextWindow(snapshot,...)
  /modelAdapterFingerprint(snapshot,...)/contextPolicy(snapshot,...) equal existing profile math
equalCopy = semanticallyEqualNewSnapshotObject(snapshot)
for each of the five methods assert candidate equalCopy throws TIANJI_DEPENDENCY_UNAVAILABLE
assert forged CONFIG snapshot and original global estimator CODE_FIRST calls remain unsupported
assert bindNative(snapshot, bindingWithBeanBOrDifferentModelIdentity) throws safe unavailable
assert existing CONFIG tests remain byte/behavior equivalent; plain CODE_FIRST through old methods remains unsupported
```

- Verification contribution: TEST-902/914 and legacy budget compatibility.
- After this file: initial method RED; typed behavior RED after File4 seam; GREEN after File5.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java`

- Purpose: Add the Function-compatible marker and immutable canonical topology metadata.
- Symbols: public interface; `templateSpec()`, `usesChatModel(ChatModel)`, inherited `apply`; nested `TransferTopologyEnum`, `AgentSpec`, `TemplateSpec#topologyHash(String)`.
- Repository evidence: executor Function map and existing immutable record patterns/`AdkSemanticJsonDigest`.
- Dependencies and consumers: File1; Step2 executor/Gate; Step3 factories; ADK/Spring AI already resolved.
- Why now: Compile prerequisite and minimum B for File1.
- Contract/signature changes: additive public-internal type only; existing map generic and executor constructors unchanged.
- Input/output and state mapping: fixed codeRef/model bean/name/route/schema versions/agent names-parent edges-transfer flags-output keys-caps-order + snapshot policy ref -> canonical framed SHA-256.
- Error and edge behavior: exact two topology enums; unique safe names/keys; reject ADK reserved prefixes, duplicate edges, depth/cap/output mismatch; `usesChatModel` remains identity proof.
- Standards impact: `MC-NAME-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-PATTERN-001`, `MC-ARCH-001`, `MC-DEP-001` — semantic Strategy, immutable records, JDK-only canonical hashing, approved existing package and no dependency.
- Literal rule enforcement: `Rule 1, 2, 3, 5, 9, 11`.
- Implementation pseudocode:

```java
public interface NativeAgentCompilerStrategy extends Function<Scheduler, BaseAgent> {
  TemplateSpec templateSpec();
  boolean usesChatModel(ChatModel candidate);
  enum TransferTopologyEnum { CONDITIONAL_RESEARCH_V1, SUPERVISOR_RESEARCH_V1 }
  record AgentSpec(String name, String parentName, String outputKey,
      boolean disallowTransferToParent, boolean disallowTransferToPeers) { compactValidate(); }
  record TemplateSpec(String codeRef, TransferTopologyEnum topology,
      String modelBeanName, String modelName, String modelRouteRef,
      String inputSchemaVersion, String outputSchemaVersion,
      String rootAgentName, List<AgentSpec> agents, int maxDepth,
      int maxTransfers, List<String> requiredChildOrder) {
    compact constructor copies lists and validates exact v1 codeRef/topology names, edges,
      parent/peer flags, unique nonreserved output keys, schema "1", depth/caps/order
    String topologyHash(String snapshotPolicyVersionRef) {
      validate versioned policy ref
      return AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_AGENT_TOPOLOGY_V1",
        canonical ordered codeRef/topology/model bean+name+route/policy/schema/root/Agent rows/caps/order fields)
    }
  }
}
```

- Verification contribution: makes compiler typed tests GREEN; supplies Step2/3 compile authority.
- After this file: compiler metadata is usable but no executor recognizes it; native execution remains impossible.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java`

- Purpose: Add a backward-compatible, default-fail native model binding seam.
- Symbols: nested `NativeModelBinding`; default `bindNative(AgentExecutionSnapshot, NativeModelBinding)`.
- Repository evidence: current SPI default methods fail closed for unavailable capabilities.
- Dependencies and consumers: File2/5; Step2 executor passes returned view to existing plugin; custom estimators compile unchanged.
- Why now: Typed budget tests require the exact signature before builtin implementation.
- Contract/signature changes: one default method; no changes to five existing methods.
- Input/output and state mapping: exact codeRef/bean/name/ChatModel identity -> per-execution estimator; redacted binding representation; route/policy remain snapshot authority.
- Error and edge behavior: default throws `TIANJI_FEATURE_NOT_AVAILABLE`/safe unsupported; non-native callers never invoke; null/unsafe fields reject.
- Standards impact: `MC-ARCH-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-SCOPE-001` — additive SPI/nested binding, exact safe validation and no consumer/architecture break.
- Literal rule enforcement: `Rule 1, 2, 3, 5, 11`.
- Implementation pseudocode:

```java
default AgentModelBudgetEstimator bindNative(AgentExecutionSnapshot snapshot, NativeModelBinding binding) {
  throw featureNotAvailable();
}
record NativeModelBinding(String codeRef, String modelBeanName, String modelName, ChatModel model) {
  compactValidate(); override toString() => redacted
}
```

- Verification contribution: converts reflection RED to typed behavior RED without relaxing old ports.
- After this file: A seam compiles; only builtin File5 can enable native binding.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`

- Purpose: Implement exact native binding through a private per-execution Bound view.
- Symbols: `bindNative`; private bound estimator implementation; existing CONFIG `require/current/cost` reused unchanged.
- Repository evidence: current `Key(modelName,route,policy)`, `Bound(profile,model,encoding,defaults,descriptor)` and five calculations.
- Dependencies and consumers: Files2/4; Step2 executor/plugin; no global registry/thread-local.
- Why now: Files2/4 fix contract and negative cases.
- Contract/signature changes: override one method; old methods/signatures/CONFIG branches remain.
- Input/output and state mapping: validate CODE_FIRST+empty config+codeRef; lookup snapshot route/policy/modelName; require profile bean name, `Bound.model == binding.model`, currency/current defaults; return view closed over Bound and the exact snapshot reference.
- Error and edge behavior: every alias/identity/route/policy/default drift fails pre-model; every view method requires `candidateSnapshot == boundSnapshot` before current-model/default checks; no equality/hash fallback and no forged CONFIG snapshot.
- Standards impact: `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — reuse all five current calculations, no dependency/global state, exact binding failure and regression proof.
- Literal rule enforcement: `Rule 2, 3, 5, 9, 10, 11`.
- Implementation pseudocode:

```java
bindNative(snapshot,binding):
  require snapshot.mode==CODE_FIRST && snapshot.configuration empty && codeRef exact
  bound = profiles[Key(binding.modelName, snapshot.routeRef, snapshot.policyRef)]
  require bound.profile.modelBeanName==binding.modelBeanName && bound.model===binding.model
  require currency/current defaults; return new BoundEstimator(snapshot,binding,bound)
BoundEstimator every method:
  require candidateSnapshot == boundSnapshot; never compare snapshot equals/hash/semantic hash
  require request modelName exact where a request exists; current(bound)
  delegate existing estimate/usage/window/fingerprint/contextPolicy formulas
```

- Verification contribution: Step1 behavior GREEN and shared accounting authority for Step2.
- After this file: compiler/budget core GREEN; still no native Gate/executor/AS admission.

#### File 6 — `CREATE docs/egon/spec/2026-11-05-04-45-native-agentic-transfer-templates.md`

- Purpose: Commit the Root-reviewed Review Spec and exact relationship/hash authority.
- Symbols: Template9; Review; T3-09 dependency; 20 requirements; clarification wording.
- Repository evidence: `/tmp/tianji-native-agentic-spec.md` plus the final delivery manifest emitted after strict validation; do not self-embed a mutable document SHA in this file block.
- Dependencies and consumers: Plan/execution/final audit.
- Why now: First source commit must carry its governing design.
- Contract/signature changes: documentation only; no Accepted/Implemented claim.
- Input/output and state mapping: exact reviewed temporary bytes -> repository path.
- Error and edge behavior: hash/strict/link/status mismatch blocks commit.
- Standards impact: `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — preserves exact design scope, strict Spec validation and Review/source-GO boundary.
- Literal rule enforcement: `Rule 11` — preserves exact technical vertical and exclusions.
- Implementation pseudocode:

```text
copy the exact Root-reviewed /tmp Spec bytes to the declared repository path
retain Status=Review and relationship/revision evidence; record final bytes/SHA only in the external frozen delivery manifest
run validate_spec.py on the repository path with --strict; record exit/SHA and block the commit on any warning or broken link
```
- Verification contribution: strict Spec gate.
- After this file: repository has Review Spec; source execution remains governed by Plan Step.

#### File 7 — `CREATE docs/egon/plan/2026-11-05-05-22-native-agentic-transfer-templates.md`

- Purpose: Commit this Root-reviewed Draft Plan without upgrading status or execution authority.
- Symbols: Template5; three Steps; exact commands/scopes/gates.
- Repository evidence: `/tmp/tianji-native-agentic-plan.md` and strict validator.
- Dependencies and consumers: Root execution workflow/evidence.
- Why now: Same governance commit as compiler ABI.
- Contract/signature changes: documentation only.
- Input/output and state mapping: reviewed temporary bytes -> repository Plan.
- Error and edge behavior: link/trace/manual-check/status failure blocks commit.
- Standards impact: `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — exact Template5 scope, validator/trace gates and no implicit execution authority.
- Literal rule enforcement: `Rule 11`.
- Implementation pseudocode:

```text
copy the exact Root-reviewed /tmp Plan bytes to the declared repository path
verify Implements Spec resolves once, all three Steps/file scopes/commits are unchanged, and Status remains Draft
run validate_plan.py on the repository path with --strict; block the commit on any warning, trace gap or Manual Check mismatch
```
- Verification contribution: strict Plan gate.
- After this file: Plan is reviewable/executable only after Root GO.

#### File 8 — `CREATE docs/egon/evidence/2026-11-05-native-agent-compiler.md`

- Purpose: Record actual Step1 RED/A/RED2/GREEN/docs/diff/hash evidence.
- Symbols: baseline/commit paths/test counts/JAR API hashes/runtime exclusions.
- Repository evidence: existing per-Step evidence convention.
- Dependencies and consumers: actual Root outputs only; final conformance audit.
- Why now: Closes Step1 honestly after behavior GREEN.
- Contract/signature changes: none.
- Input/output and state mapping: actual commands/exits/counts/hashes; remaining Steps2-3/T3-09 listed.
- Error and edge behavior: compile/cache/assertion failures separated; no prefilled PASS.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — records every applicable PASS or evidence-backed N/A from actual Step1 commands.
- Literal rule enforcement: `Rule 1,2,3,5,9,10,11` evidence rows.
- Implementation pseudocode:

```text
record the actual baseline, RED/A/typed-RED/GREEN commands, exits, named Surefire reports/counts and artifact hashes
classify cache/compile/fixture failures separately and never convert them into behavioral RED or PASS
state NativeExecutionRegistered=false, ASFactoryPresent=false, RuntimeUnverified=true and list Steps2-3 plus T3-09 as remaining
```
- Verification contribution: Step1 completion evidence.
- After this file: Step1 is auditable for path-limited commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- RED command: common offline source-reactor command in §8 with `-Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest`; expected nonzero only named missing Strategy/`bindNative` assertions.
- Compile-seam A command: same selector; tests compile and fail typed missing-behavior assertions, zero fixture/dependency errors.
- GREEN command: common command with `-Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest`; expected exit 0.
- Verification command: the exact §8 S1 RED/A/GREEN commands plus strict Spec/Plan validators and Step1 static path gates.
- Expected result: focused S1 reports exist with zero failure/error/skip at GREEN; both validators exit 0; no native Gate/executor/AS admission exists; only the eight declared paths are staged.
- Failure returns to: File1 metadata/hash; File2 binding fixtures; File3 ABI; File4 SPI; File5 builtin view; File6/7 strict docs.
- Completion criteria: exact compiler/hash and bound estimator tests GREEN; legacy CONFIG/plain CODE_FIRST behavior preserved; no native executor/factory exists.
- Rollback: revert only Step1 paths; no data/runtime effect.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategyTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimatorTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentCompilerStrategy.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AgentModelBudgetEstimator.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/JTokkitChatModelBudgetEstimator.java`, `docs/egon/spec/2026-11-05-04-45-native-agentic-transfer-templates.md`, `docs/egon/plan/2026-11-05-05-22-native-agentic-transfer-templates.md`, `docs/egon/evidence/2026-11-05-native-agent-compiler.md`.
- Commit: `feat(agent): define native compiler and budget binding`

### Step 2 — Govern native transfer callbacks and require truthful branch completion

- Requirements: `REQ-901`, `REQ-902`, `REQ-904`, `REQ-905`, `REQ-906`, `REQ-907`, `REQ-908`, `REQ-909`, `REQ-910`, `REQ-911`, `REQ-912`, `REQ-913`, `REQ-914`, `REQ-915`, `REQ-916`, `REQ-917`, `REQ-918`, `REQ-919`, `REQ-920`.
- Dependencies: Step1 is committed and its exact strategy/budget tests are GREEN. No AS `NativeAgentCompilerStrategy` is registered.
- Baseline state: Step1 exposes immutable template metadata and a bound estimator view, but the executor still treats every map value as a plain Function; the plugin tracks only ordinary tool scopes; no native callback is admitted.
- Observable outcome: one synthetic reviewed strategy can execute the two fixed transition policies through the pinned ADK transfer function, with pre-budget current-role fencing, current MODEL/session/run authority, durable authorization audit, two-phase child collection, sticky fatal control, genuine root completion and unchanged legacy/tool behavior.
- End state: AR contains the Gate and complete runtime integration plus fake-SDK/real-Runner evidence; no AS factory or production configuration can select it yet.
- Test-first gate: `Required` — Gate reflection assertions fail first; after its minimal signatures compile, typed Gate behavior fails; plugin reflection/constructor tests and real Runner scripts then fail for absent native routing; executor completion tests fail until the native branch is wired. Codec unknown-type checks are compatibility regressions expected to remain GREEN before and after production changes.
- Manual Checks: all 17 are evaluated separately: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`. Applicable: ARCH, REUSE, DEP, NAME, VALID, MODEL, UTIL, JSON, TIME, PATTERN, SCOPE, TEST, BLOCKER. Evidence-backed N/A: CONVERT (no new projection/Converter), LOG/BEAN (only established technical classes, no new managed business Bean or injection surface), CONFIG (no key/profile file in this Step).
- Literal Rules: all ten are evaluated separately: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11`. Applicable: Rules 1, 2, 3, 5, 6, 9, 10, 11. Evidence-backed N/A: Rule 4 (no new Spring-managed business class and existing technical logger style is preserved), Rule 7 (no profile key changes). Rule 11 is blocking for every file.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java`

- Purpose: Establish the Gate assertion RED and then exhaustively define provenance, identity, FSM, collector, audit, replay, fatal and completion behavior before runtime wiring.
- Symbols: `native_gate_type_and_callback_contract_exist`; `accepts_only_pinned_agent_transfer_method_and_exact_schema`; `binds_only_explicit_shared_base_llm_identity`; `rejects_absent_name_only_implicit_or_same_name_different_base_llm`; `accepts_distinct_model_wrappers_around_same_base_llm`; `conditional_quick_and_deep_are_mutually_exclusive`; `supervisor_requires_researcher_then_critic`; `known_but_unexpected_agent_fails_before_budget`; `normalizes_only_absent_or_exact_empty_provider_id`; `rejects_unsafe_present_or_actual_call_id`; `enforces_exact_child_text_bounds_without_normalization`; `append_failure_does_not_consume_transition`; `repeated_before_is_fatal_and_identical_after_is_idempotent`; `auth_ledger_timeout_and_policy_failures_replay_the_same_safe_disposition`; `tool_error_or_mismatch_dominates_later_root_final`; `requires_genuine_root_and_exact_current_outputs`.
- Repository evidence: `AdkRunContextTest` uses identity-distinct callback fixtures and exact ToolContext IDs; `AdkEventProjectorTest` builds thought/text/function parts; the packaged ADK 1.13 `FunctionTool#func`, `ToolContext#functionCallId`, `Context#eventActions` and `AgentTransfer` Method are public and pinned by the Spec.
- Dependencies and consumers: begins with string/reflection assertions so it compiles before File2; after File2 seam A it directly drives package-private Gate methods using synthetic Step1 TemplateSpecs, actual LlmAgent trees, CallbackContext/ToolContext identity fixtures and an in-memory authorization appender. Files4-8 consume the same contract.
- Why now: every later plugin/executor change must implement a fixed, independently testable security/state owner rather than distribute topology branches across callbacks.
- Contract/signature changes: fixes the planned Gate surface: `bind(TemplateSpec, BaseAgent, String, AuthorizationAppender)`, `requireExpectedAgent(CallbackContext)`, `observeResponse(CallbackContext,LlmResponse)`, `beforeTool(BaseTool,Map<String,Object>,ToolContext)`, `afterTool(BaseTool,Map<String,Object>,ToolContext,Map<String,Object>)`, `RuntimeException onToolError(BaseTool,Map<String,Object>,ToolContext,Throwable)`, `observeEvent(Event,Optional<AdkEventProjector.Projection>)`, `failureSignal()`, `requireCompletedOutputs()`, `isNativeTransfer(BaseTool)`, and `close()`; names may not be replaced by a generic registry/DSL.
- Input/output and state mapping: actual tree identities + explicit root/child BaseLlm identity + canonical hash -> bound gate; one complete current response -> one pending envelope; actual context/call ID + exact tool -> one authorization append/ordinal; matching EventActions -> one state transition/child summary; root event -> immutable output Map.
- Error and edge behavior: covers absent/name-only/implicit model bindings, distinct Model wrappers around one BaseLlm, same-name different BaseLlm, null/foreign/equal-looking context, any known but out-of-order Agent, stale child on new invocation, unsupported mid-child continuation, normal confirmed return to root, spoof tools/methods/declarations/args, unknown/self/peer/second branch, caps, partial/error/multiple calls, provider ID absent/exact-empty/safe-present/whitespace/control/conflict, unsafe actual ID, repeated before, changed after replay, append failure, authorization DENY/UNAVAILABLE, ledger revision/lease/budget codes, known timeout, unknown/policy failure, controls/surrogates/char-byte boundaries, missing/extra/child final and sticky fatal. Tests assert no original Throwable/message/cause is retained.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — semantic Gate/state values, exact third-party boundary validation, safe event payload, existing deadline semantics and complete Complex-flow matrix.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11` — tests semantic behavior names, immutable facts, JDK UTF-8/identity primitives, exact JSON-safe payload, current `java.time` fences and the approved Strategy/State/two-phase design in the existing package.
- Implementation pseudocode:

```java
@Test void known_but_unexpected_agent_fails_before_budget() {
  gate = bindSupervisor(actualRoot, researcher, critic)
  assertDoesNotThrow(() -> gate.requireExpectedAgent(callback(actualRoot)))
  assertThrows(AgentLedgerException.class, () -> gate.requireExpectedAgent(callback(critic)))
  assertEquals(0, appender.calls())
  // new Gate for the same persisted Session still starts at actualRoot; stale researcher/critic fails.
}

@Test void explicit_model_binding_uses_base_llm_identity_not_model_wrapper_identity() {
  shared = scriptedBaseLlm("fixture-model")
  root = LlmAgent.builder().name(ROOT).model(shared).subAgents(
      LlmAgent.builder().name(CHILD).model(shared).build()).build()
  assertNotSame(root.model().orElseThrow(), child.model().orElseThrow())
  assertDoesNotThrow(() -> NativeAgentGate.bind(spec, root, HASH, appender))
  reject root/child built with different BaseLlm objects that both return "fixture-model"
  reject child.model() empty or child built with only model("fixture-model"); never call resolvedModel()
}

@Test void provider_id_normalization_is_exact() {
  for captured id in Optional.empty and Optional.of(""):
    observe response; beforeTool with generated safe actual id succeeds and binds once
  for captured id in " ", " call-1", "call-1\t", "call-1\u0000": observeResponse is fatal
  captured safe "call-1" requires exact actual "call-1"; mismatch is fatal
  actual ToolContext id blank, trimmed-different or ISO-control-bearing is fatal before audit
}

@Test void fatal_signal_preserves_safe_boundary_disposition() {
  for failure in [new AgentExecutionAuthorization.Failure(DENY), Failure(UNAVAILABLE),
      ledgerError(REVISION_CONFLICT), ledgerError(LEASE_EXPIRED), ledgerError(BUDGET_EXCEEDED),
      wrapper(new TimeoutException()), new IllegalStateException("private-sentinel")]:
    callbackError = gate.markFatal(failure)
    signalError = gate.failureSignal().test().assertError(...).errors().getFirst()
    assert same authorization decision / ledger code / timeout cause / safe dependency class
    assert neither error nor Gate fields/log capture contain "private-sentinel"
  first token remains authoritative when a second differently classified failure arrives
}

@Test void child_text_and_call_are_committed_only_after_exact_native_action() {
  gate.observeResponse(childContext, response(text(" summary\n"), transferCall(optionalProviderId, ROOT)))
  gate.beforeTool(exactAgentTransferTool(), Map.of("agent_name", ROOT), toolContext(actualCallId))
  assertTrue(gate.confirmedOutputs().isEmpty())
  gate.afterTool(exactTool, exactArgs, toolContext(actions.transferToAgent(ROOT)), Map.of())
  assertEquals(" summary\n", gate.confirmedOutputs().get(CHILD))
  repeat identical after => no-op; changed target/result/context => sticky fatal
}

@ParameterizedTest boundaries() {
  accept 8192 Java chars when UTF8 <= 32768; accept tab/LF/CR unchanged
  reject 8193 chars, 32769 UTF8 bytes, NUL, DEL/C0/C1 controls except tab/LF/CR,
         high/low unpaired surrogate, blank child text; assert no trim/normalize/truncate
}
```

- Verification contribution: initial reflection RED, Gate typed RED/GREEN, `TEST-904` through `TEST-910`, `TEST-913`, and the REQ-912 current-role clarification.
- After this file: before File2, only the missing Gate assertion fails; after File2 seam/behavior cycles, Gate unit semantics are GREEN without plugin/executor admission.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java`

- Purpose: Own one invocation's fixed topology policy, exact native-transfer provenance, two-phase correlation, safe authorization event, confirmed summaries, expected Agent role and sticky fatal signal.
- Symbols: package-private final `NativeAgentGate`; nested `AuthorizationAppender`, `TransferTaskStateEnum`, `NativeFailureKindEnum`, `NativeFailureToken`, `PendingTransfer`, `ConfirmedTransfer` and topology-policy state; pinned `Method TRANSFER_METHOD`; `markFatal`, `markPolicyFatal`, `safeBoundaryFailure`, `incomingAlreadySafe`; callback/completion methods fixed by File1.
- Repository evidence: package-private technical collaborators beside `AdkRunBudgetPlugin`; pinned ADK getters prove only `LlmAgent.model().flatMap(Model::model)` exposes an explicit BaseLlm while `resolvedModel()` is a lazy wrapper; `AdkSemanticJsonDigest`/JDK identity maps; current RxJava `ReplayProcessor` control signal pattern in the plugin; no separate registry/store exists or is needed.
- Dependencies and consumers: consumes Step1 `TemplateSpec`, actual ADK Agent/Callback/FunctionTool/Event types and a synchronous appender supplied later by the executor; File4 delegates callbacks; File8 consumes health/completion/failure signal.
- Why now: supplies compile seam A and then minimum behavior B for File1 before modifying stable plugin/executor code.
- Contract/signature changes: new internal class only. It creates no DTO/API/table/thread/scheduler. The appender accepts known `author` plus the exact payload and must return normally only after the existing ledger CAS commits.
- Input/output and state mapping: identity-walk the actual tree and bind each exact LlmAgent object to one TemplateSpec AgentSpec; unwrap each explicit model only through `llm.model().flatMap(Model::model)`, require every unwrapped BaseLlm object to be the same root object and require `rootModel.model().equals(TemplateSpec.modelName())`; initialize expected role to root; transition expected role only after matching afterTool action; count successful appends as ordinals; store summaries only on confirmed child-to-root action; store root text only from a genuine root final projection. Executor separately proves the trusted host ChatModel identity through `usesChatModel`/`NativeModelBinding`.
- Error and edge behavior: every invalid callback calls synchronized `markFatal(Throwable)`, which immediately classifies but never stores the Throwable/text/args/message/cause. The first immutable token wins and records only AUTHORIZATION with DENY/UNAVAILABLE, LEDGER with exact Agent error code, TIMEOUT, or POLICY/UNKNOWN. `safeBoundaryFailure(token)` reconstructs constant-message errors compatible with the executor's existing branches; callback and replay signal return the same classification. `beforeTool` previews without mutation, invokes appender, then consumes the key/advances; appender failure leaves counters/task state unchanged but makes the run fatal. Identical after replay checks context identity/call/target/result-action and no-ops; every other replay fails.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001` — one cohesive internal State/Policy owner, JDK/ADK/RxJava already present, no dependency or storage.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11` — Gate behavior suffix; private immutable records/enums; exact callback validation; JDK collections/UTF-8; JSON-safe string/int Map; no new time type; approved state/two-phase pattern in the established AR package.
- Implementation pseudocode:

```java
final class NativeAgentGate implements AutoCloseable {
  static final Method TRANSFER_METHOD = AgentTransfer.class.getMethod(
      "transferToAgent", String.class, ToolContext.class);
  private final IdentityHashMap<BaseAgent, AgentSpec> agents;
  private final IdentityHashMap<InvocationContext, PendingTransfer> pending;
  private final IdentityHashMap<InvocationContext, Map<String, ConfirmedTransfer>> confirmedCalls;
  enum NativeFailureKindEnum { AUTHORIZATION, LEDGER, TIMEOUT, POLICY, UNKNOWN }
  record NativeFailureToken(NativeFailureKindEnum kind,
      AgentRunAuthorization.Decision decision, AgentErrorCodeEnum ledgerCode) { validate safe combinations; }
  private BaseAgent expectedAgent; private int authorized; private NativeFailureToken fatal;
  private boolean complete, closed;

  static NativeAgentGate bind(TemplateSpec spec, BaseAgent root, String topologyHash,
      AuthorizationAppender appender) {
    identity-walk exact root/children against AgentSpec parent edges, names, keys and transfer flags
    rootLlm = require root instanceof LlmAgent
    BaseLlm rootModel = rootLlm.model().flatMap(Model::model).orElseThrow(NativeAgentGate::safeFailure)
    require rootModel.model().equals(spec.modelName())
    for every child LlmAgent:
      BaseLlm childModel = child.model().flatMap(Model::model).orElseThrow(NativeAgentGate::safeFailure)
      require childModel == rootModel
    never call resolvedModel(); name-only/implicit model wrappers fail before resolution/provider creation
    return new Gate with expectedAgent = exact root and empty per-invocation state
  }

  synchronized void requireExpectedAgent(CallbackContext callback) {
    require healthy/open, exact invocationContext identity and exact bound LlmAgent object;
    require callback.invocationContext().agent() == expectedAgent; // before any budget spend
  }
  synchronized void observeResponse(CallbackContext callback, LlmResponse response) {
    requireExpected/admitted context; require complete nonpartial nonerror response;
    extract exactly zero or one semantic FunctionCall; if zero return;
    require name transfer_to_agent and args exactly {agent_name: String}; validate policy transition;
    providerId = functionCall.id(); if present and exactly empty map to Optional.empty();
    otherwise require untrimmed nonblank ISO-control-free providerId and retain it exactly;
    root->child requires no branch text; child->root requires exact safe text;
    save one PendingTransfer(context identity, optional provider id, source identity, target, args, text);
  }
  synchronized void beforeTool(BaseTool tool, Map<String,Object> args, ToolContext context) {
    try {
      require exact FunctionTool, TRANSFER_METHOD, public/static method, name and declaration schema;
      require matching pending context/source/target/args and actual nonblank ToolContext.functionCallId;
      require actual id untrimmed and ISO-control-free; if safe provider id retained require exact equality;
      reject used/repeated call and preview exact FSM/cap;
      ordinal = authorized + 1;
      appender.append(sourceName, Map.of("to", target, "ordinal", ordinal,
          "topologyHash", topologyHash, "state", "AUTHORIZED")); // current ledger CAS
      authorized = ordinal; consume before key; mark transfer DISPATCHED; // only after append returns
    } catch (RuntimeException failure) { throw markFatal(failure); }
  }
  synchronized void afterTool(BaseTool tool, Map<String,Object> arguments,
      ToolContext context, Map<String,Object> result) {
    require same identity/call/target and context.eventActions().transferToAgent()==target;
    if exact confirmation already recorded return;
    commit child text only for child->root; advance fixed policy; expectedAgent = actualTargetAgent;
    clear pending and record exact confirmation;
  }
  synchronized RuntimeException onToolError(BaseTool tool, Map<String,Object> arguments,
      ToolContext context, Throwable failure) {
    discard pending; return markFatal(failure); // retain only sanitized token, never Throwable/message/args
  }
  synchronized void observeEvent(Event event, Optional<Projection> projection) {
    reject child final/unknown author/function-bearing root terminal;
    for genuine expected root final require policy outputs complete, projection AGENT_OUTPUT,
      capture exact root text, mark COMPLETE;
  }
  synchronized RuntimeException markFatal(Throwable failure) {
    token = fatal != null ? fatal : classify bounded cause chain as AUTH decision,
      known TimeoutException, known AgentLedgerException code, or UNKNOWN;
    internal topology/provenance/order guards call markPolicyFatal() to create POLICY directly
    if fatal was null: fatal=token; failureProcessor.onNext(token)
    return safeBoundaryFailure(token)
  }
  RuntimeException safeBoundaryFailure(token):
    AUTHORIZATION -> new AgentExecutionAuthorization.Failure(token.decision)
    LEDGER -> new AgentLedgerException(token.ledgerCode, "Native Agent boundary failed")
    TIMEOUT -> new RuntimeException("Native Agent boundary timed out", new TimeoutException("Native Agent boundary timed out"))
    POLICY or UNKNOWN -> new AgentLedgerException(TIANJI_DEPENDENCY_UNAVAILABLE, "Native Agent boundary failed")
  boolean incomingAlreadySafe(Throwable failure):
    true only for matching AgentExecutionAuthorization.Failure, known AgentLedgerException code,
      known timeout cause, or a Gate-reconstructed safe policy error; false for raw UNKNOWN
  Flowable<Long> failureSignal() = replayed token -> Flowable.error(safeBoundaryFailure(token));
  synchronized Map<String,String> requireCompletedOutputs() = exact immutable known-agent map or throw;
}
```

- Verification contribution: turns File1 typed matrices GREEN; supplies failure signal and completion authority for Files4/8.
- After this file: Gate behavior is unit-GREEN and unreachable from production plugin/executor; legacy runtime is unchanged.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`

- Purpose: Define plugin-native tracking, current-role pre-budget fencing, settlement-before-capture, exact callback delegation and zero ordinary-tool accounting while preserving every current tool/context test.
- Symbols: `native_gate_tracks_without_tool_scope`; `known_unexpected_agent_fails_before_authorization_estimation_or_reservation`; `confirmed_return_to_root_allows_next_model`; `stale_child_session_state_cannot_resume`; `settles_before_observing_complete_response`; `native_before_tool_reauthorizes_and_refences_session`; `native_callbacks_never_increment_tool_entries`; reflection for final constructor/native callback overrides.
- Repository evidence: current class already owns model admission identity, durable session fence, budget reserve/settle ordering and ordinary tool callback fixtures; it is the smallest regression owner.
- Dependencies and consumers: uses File2 Gate fake/appender plus existing mocked ledger/budget/authorization/session context; File5 implements the assertions. It does not instantiate AS or PostgreSQL.
- Why now: establishes plugin RED after the Gate contract is compile-safe and before modifying the plugin.
- Contract/signature changes: old constructors remain; one package-private terminal overload adds nullable `NativeAgentGate`. Public Plugin signatures must exactly match ADK 1.13 for before/after/onToolError.
- Input/output and state mapping: native Gate nonnull and toolScope null -> tracking true; expected role/context checks -> no budget on failure; budget settlement -> Gate response observation; native callbacks -> Gate only; ordinary tool counters/scope unchanged.
- Error and edge behavior: auth denial, session fence failure, foreign context, known wrong role, post-close and callback mismatch set Gate fatal and do not append/execute; capacity retry preserves one response context; repeated before cannot reserve/audit twice.
- Standards impact: `MC-VALID-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — proves every internal re-entry handoff, current `Instant` deadline/session fence, and strict legacy separation.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 9`, `Rule 10`, `Rule 11` — exact internal validation order and current architecture; no new utility/time mechanism.
- Implementation pseudocode:

```java
@Test void known_unexpected_agent_fails_before_any_budget_work() {
  plugin = plugin(toolScope=null, nativeGate=supervisorGateExpecting(root));
  callback = modelContext(actualCritic, "critic-first");
  assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet());
  verifyNoInteractions(estimator, budgets)
  verify(authorizer, never()).authorize(any(), eq(AgentRunAuthorization.Boundary.MODEL))
  assertFatal(nativeGate);
}

@Test void complete_native_response_is_observed_only_after_durable_settlement() {
  plugin.beforeModelCallback(rootContext, request).blockingGet();
  plugin.afterModelCallback(rootContext, transferResponse).blockingGet();
  InOrder order = inOrder(budgets, nativeGate);
  order.verify(budgets).settleBudget(eq(actor), eq(lease), eq("call-one"), any(AgentBudgetSettlement.class))
  order.verify(nativeGate).observeResponse(same(rootContext), same(transferResponse))
  verify no ordinary tool-scope execute and no tool budget increment;
}
```

- Verification contribution: typed behavior RED/GREEN for `REQ-909`, `REQ-912`, `REQ-913`, `REQ-917`, `TIME-901`; protects all existing context/tool tests.
- After this file: tests compile against File2 and fail only because File5 lacks native tracking/delegation.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java`

- Purpose: Prove the public ADK 1.13 callback/tool ordering and native transfer semantics with a real `Runner`, scripted `BaseLlm`, in-memory Session and the Tianji plugin/Gate; no `App`, Spring context, provider or database.
- Symbols: `conditional_transfer_with_safe_provider_id_preserves_text_and_call`; `conditional_transfer_without_provider_id_binds_sdk_id`; `conditional_transfer_with_empty_provider_id_binds_sdk_id`; `supervisor_round_trip_is_researcher_then_critic`; `child_handoff_is_nonfinal_and_does_not_write_output_key`; `callback_failure_rethrows_and_sticky_state_rejects_simulated_later_root`.
- Repository evidence: `AdkSessionPostgresTest.ScriptModel` shows repository-native scripted BaseLlm/Runner fixtures; packaged JAR proves FunctionCall ID optional and ToolContext ID/action public; Spec requires Fake SDK proof instead of a live provider.
- Dependencies and consumers: constructs a fixed actual LlmAgent tree with the exact ADK native transfer AutoFlow, `Runner.builder()` + `InMemorySessionService`, File2 Gate and File5 plugin. Scripted responses cover safe-present, absent and exact-empty provider IDs plus final root response.
- Why now: this test must exist before plugin integration and protects assumptions that unit mocks cannot prove.
- Contract/signature changes: none external; the fixture consumes exact Plugin callback signatures and current Runner API.
- Input/output and state mapping: scripted root transfer -> actual FunctionTool before/after callbacks -> EventActions target -> child transfer response text+call -> confirmed summary -> later genuine root final; inspect emitted Events and Session state.
- Error and edge behavior: a callback/CAS failure records one sanitized disposition token. If the incoming error is already a matching safe authorization/ledger/timeout/policy boundary, native `onToolError` returns empty and pinned ADK rethrows it; a raw unknown error is replaced with `Maybe.error` carrying the token's safe dependency failure. The Gate control stream reconstructs the same classification and terminates the Runner. A separate direct callback/final-health regression simulates future SDK/other-callback conversion and proves a later plausible root response still cannot succeed. Absent/exact-empty provider ID is populated by pinned ADK after afterModel and accepted only when the later actual ToolContext ID binds once.
- Standards impact: `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-JSON-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — uses actual pinned public SDK with no new fake production layer/dependency/network.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 11` — exercises exact callback inputs/event content through existing SDK/Jackson-compatible models in the current test package.
- Implementation pseudocode:

```java
ScriptModel responses = script(
  root: modelContent(functionCall(id optional, "transfer_to_agent", {agent_name:"quick_research"})),
  quick: modelContent(text("current summary\n"), functionCall(id optional, "transfer_to_agent", {agent_name:"conditional_root"})),
  root: modelContent(text("genuine root final")));
Runner runner = Runner.builder().agent(actualTree(responses)).appName(APP)
    .sessionService(new InMemorySessionService()).plugins(pluginWithGate).build();
events = runner.runAsync(USER, SESSION, userContent, SSE_MAX_CALLS).toList().blockingGet();
assert handoff response/event is !finalResponse and retains full text plus function call;
assert session.state lacks quick_research_output;
assert Gate confirms quick text once and accepts only later root final;
assert safe-present provider ID matches the actual ToolContext ID;
assert both absent and exact-empty provider IDs receive/bind the generated nonblank ADK ToolContext ID;
force a known safe native before/tool failure; assert pinned Runner rethrows after plugin onToolError returns empty;
force a raw unknown tool failure; assert onToolError emits only the sanitized same-classification error;
then directly feed a simulated converted FunctionResponse/later root projection and assert Gate remains fatal/incomplete;
```

- Verification contribution: `TEST-911`, `TEST-912` and the exact SDK-ordering risk `RISK-901`; ensures an implementation does not use mocked call IDs as proof.
- After this file: real Runner tests are RED for absent File5 native callback integration; no listener/provider/database starts.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`

- Purpose: Extend the existing one-plugin boundary to track native no-tool contexts, enforce current expected role before budget spend, settle before capture, and route exact native callbacks while keeping ordinary tools byte/behavior compatible.
- Symbols: nullable final `nativeGate`; terminal constructor overload; `tracking()`; modified `beforeModelCallback`, `afterModelCallback`, `beforeToolCallback`, `requireNativeContext`, `close`; new exact `afterToolCallback` and `onToolErrorCallback` overrides.
- Repository evidence: existing plugin already owns MODEL authorization, session fence, budget CAS, admitted-context identity, idle signal and ordinary tool entry; adding another Plugin would split ordering/authority and violate the Spec.
- Dependencies and consumers: File2 Gate; Files3-4 tests; File8 constructs the terminal overload and merges Gate failure signal. Existing constructors delegate with `nativeGate=null`.
- Why now: tests define exact ordering and legacy invariants; this is the minimum callback integration B before executor completion wiring.
- Contract/signature changes: no public external or old constructor change. `tracking = toolScope != null || nativeGate != null`. Native path never calls `toolScope`, increments `toolEntries`, consults Tool PDP/journal or returns a replacement tool result.
- Input/output and state mapping: beforeModel context -> Gate expected-role check before authorization/estimation/reserve and again immediately before admission; afterModel successful settlement -> Gate capture; beforeTool -> current MODEL auth + session fence + Gate; after/error -> Gate confirmation plus sanitized fatal token; close -> revoke contexts/Gate.
- Error and edge behavior: all native callback failures call the Gate-specific classifier rather than the plugin's generic estimator/ledger sanitizer. Authorization decision, Agent ledger code and timeout cause are reconstructed identically on callback and Gate-signal channels; unknown failures become safe dependency errors. Native `onToolError` stores/logs no Throwable/provider text and returns `Maybe.empty()` only for an already-safe matching boundary, otherwise `Maybe.error(safeBoundaryFailure)`. Partial responses only refresh idle after admitted context validation. Error/interrupted model responses retain current settlement/safe response behavior. Ordinary toolScope branch remains exactly current including its `safeErrors`, scheduler, UUID, maxToolCalls and side-effect accounting.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001` — extends the correct existing boundary, reuses its scheduler/fences and adds no dependency/Bean/thread.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 9`, `Rule 10`, `Rule 11` — exact third-party callback validation at the internal entry, approved State/Strategy routing, current `Duration`/deadline semantics and no architecture change.
- Implementation pseudocode:

```java
boolean tracking() { return toolScope != null || nativeGate != null; }

beforeModelCallback(context, request):
  if tracking: requireNativeContext(context,false)
  if nativeGate != null: nativeGate.requireExpectedAgent(context) // before MODEL auth/budget
  authorization.require(MODEL); requireSessionFence()
  resolve/revalidate existing sources and context; build exact LlmRequest
  estimate = estimator.estimate(snapshot, modelRequest); window = estimator.contextWindow(snapshot, modelRequest)
  validate route/policy/window/output limit; reserve ledger budget with the existing request/optional context snapshot path
  synchronized admittedContexts:
    if tracking: requireNativeContext(context,false)
    if nativeGate != null: nativeGate.requireExpectedAgent(context)
    bind native session/invocation; admittedContexts.add(context.invocationContext())

afterModelCallback(context,response):
  if tracking: requireNativeContext(context,true)
  preserve partial/error branches; settle successful budget first
  if nativeGate != null: nativeGate.observeResponse(context,response)

beforeToolCallback(tool,args,context):
  if nativeGate != null:
    return Maybe.fromAction(() -> {
      requireNativeContext(context,true); nativeGate.requireExpectedAgent(context)
      authorization.require(MODEL); requireSessionFence()
      nativeGate.beforeTool(tool,args,context)
    }).toMaybe()
      .onErrorResumeNext(failure -> Maybe.error(nativeGate.markFatal(failure)))
      .subscribeOn(AgentFlowSchedulers.contextAwareIo())
  else execute the existing toolScope branch unchanged

afterToolCallback(BaseTool tool, Map<String,Object> args, ToolContext context, Map<String,Object> result):
  if nativeGate==null return Maybe.empty()
  return Maybe.fromAction(() -> nativeGate.afterTool(tool,args,context,result)).toMaybe()
    .onErrorResumeNext(failure -> Maybe.error(nativeGate.markFatal(failure)))
onToolErrorCallback(BaseTool tool, Map<String,Object> args, ToolContext context, Throwable failure):
  if nativeGate==null return Maybe.empty()
  safe = nativeGate.onToolError(tool,args,context,failure)
  return nativeGate.incomingAlreadySafe(failure) ? Maybe.empty() : Maybe.error(safe)
close(): revoke admitted contexts/idle guards; nativeGate.close(); no new scheduler
```

- Verification contribution: makes Files3-4 GREEN for tracking, ordering, real callbacks and zero tool entries; existing `AdkRunContextTest` ordinary cases remain GREEN.
- After this file: native callbacks work in an explicitly constructed Runner/plugin fixture, but production executor still never constructs a Gate.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`

- Purpose: Define executor selection/admission/output/fatal/control semantics without PostgreSQL and before File8 changes.
- Symbols: `plain_function_keeps_legacy_output_and_never_binds_native`; `native_strategy_validates_snapshot_tree_explicit_base_llm_and_schema_before_runner`; `native_accepts_distinct_model_wrappers_over_one_base_llm`; `native_rejects_absent_name_only_implicit_or_same_name_different_base_llm`; `native_uses_exact_snapshot_bound_estimator_view`; `native_failure_signal_joins_deadline_heartbeat_idle_control`; `native_authorization_deny_and_unavailable_remain_suspended`; `native_revision_and_lease_signal_use_current_run_resolver`; `native_timeout_signal_remains_timed_out`; `native_policy_signal_remains_safe_failed`; `exact_native_outputs_have_known_agents_and_root_text`; `missing_child_root_extra_or_fatal_cannot_complete`; `native_pre_model_failure_remains_unknown_side_effect`.
- Repository evidence: current executor embeds factory lookup, Runner stream, projector and output assembly; `AdkEventProjectorTest` proves legacy event projection; existing constructor-count regression must remain eight.
- Dependencies and consumers: uses Step1 synthetic strategies/bound estimator mocks, File2 Gate fixtures and package-private completion helper/seams planned in File8; no ledger/database execution is required for output/control unit cases.
- Why now: fixes production selection and completion behavior before modifying the 633-line executor.
- Contract/signature changes: no public constructor/API change. Permits package-private static `completedOutput(BaseAgent,Map<String,String>,NativeAgentGate)` only as INTERNAL-905 test seam; explicitly prohibits a CompiledTemplate class.
- Input/output and state mapping: plain Function -> legacy `agentOutputs`; strategy -> exact snapshot/actual tree/model checks, per-execution estimator, Gate; gate-completed outputs -> existing `agents` Map plus root-only `text`; fatal callback/control token -> the same existing authorization, ledger, timeout or generic terminal branch whichever channel wins.
- Error and edge behavior: wrong codeRef/mode/config/schema/tools/maxToolCalls/route/policy/explicit BaseLlm identity or name/tree/output keys/callbacks fails before model; no lazy model resolution/provider call is permitted during binding; DENY/UNAVAILABLE remains SUSPENDED, revision/lease remains current-run resolution, known timeout remains TIMED_OUT, native policy/unknown remains safe FAILED; native pre-model and post-audit failures keep `sideEffectOutcome=UNKNOWN`; legacy NONE/UNKNOWN behavior is unchanged; child final/stale session/output key/history never satisfies completion.
- Standards impact: `MC-VALID-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact admission and result mapping over unchanged public JSON/time/lifecycle.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11` — tests internal entry/state mapping, existing JSON result shape and control timers without a new layer.
- Implementation pseudocode:

```java
@Test void completed_native_output_uses_only_gate_facts_and_genuine_root() {
  gate = confirmedConditionalGate(currentChild="summary", currentRoot="final")
  output = AdkAgentRunExecutor.completedOutput(actualRoot, Map.of("stale","forbidden"), gate)
  assertEquals(Map.of("conditional_root","final","quick_research","summary"), output.get("agents"))
  assertEquals("final", output.get("text")); assertFalse(output.toString().contains("stale"))
}

@Test void native_pre_model_failure_never_claims_none_side_effect() {
  select a native strategy then force snapshot/tree binding failure before Runner subscription
  assert failed AgentExecutionResult.sideEffectOutcome()=="UNKNOWN"
  repeat with legacy plain Function and assert its existing outcome is unchanged
}

@ParameterizedTest void either_callback_or_gate_signal_preserves_terminal_disposition(FailureCase c) {
  run native beforeTool path with c injected at MODEL authorization, session fence, append CAS or policy guard
  race callback Maybe error against gate.failureSignal() in both deterministic subscription orders
  assert DENY/UNAVAILABLE -> existing suspendForAuthorizationOrResolve
  assert REVISION_CONFLICT/LEASE_EXPIRED -> existing latest-run cancel/deadline/terminal resolver
  assert TimeoutException cause -> TIMED_OUT; policy/unknown -> FAILED with safe dependency code
  assert no raw private message in result, event or captured WARN logs
}
```

- Verification contribution: typed RED/GREEN for `TEST-902`, `TEST-910`, `TEST-913`, `TEST-914`, and compatibility `TEST-915`.
- After this file: tests fail only for missing File8 strategy selection, Gate construction/control merge and output assembly.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java`

- Purpose: Lock additive event JSON/privacy and unknown-type compatibility over the unchanged envelope/codec.
- Symbols: `native_transfer_authorization_event_round_trips_exact_safe_payload`; `event_codec_preserves_unknown_future_type_without_enum_narrowing`.
- Repository evidence: `AgentEventDTO.type` is a validated String, not enum; `AgentJsonCodec#write/readObject` is the current canonical Jackson boundary; no C/CL decoder change is required.
- Dependencies and consumers: constructs existing AgentEventDTO values only; observes codec JSON Object maps. Gate payload assertions in File1 provide the producer half.
- Why now: proves EVENT-901 is additive before executor begins emitting it and ensures no implementation narrows event types.
- Contract/signature changes: test only; no codec or contract production path changes.
- Input/output and state mapping: exact type/payload -> Jackson JSON -> generic object with unchanged `type`, `payloadSchemaVersion="1"`, `to`, numeric ordinal, topologyHash, AUTHORIZED; arbitrary future string type also survives.
- Error and edge behavior: assert serialized form omits child text, args, call ID, actor subject, prompt, token, model and error; existing size limit remains.
- Standards impact: `MC-JSON-001`, `MC-SCOPE-001`, `MC-TEST-001` — Spring Boot Jackson only, additive String type and no C/CL/API edit.
- Literal rule enforcement: `Rule 6`, `Rule 11` — validates the existing Jackson envelope and current persistence test package.
- Implementation pseudocode:

```java
event = new AgentEventDTO("event-1", "run-1", 1, "NATIVE_TRANSFER_AUTHORIZED",
    "conditional_root", null, null, NOW, "trace-1", "1",
    Map.of("to","quick_research","ordinal",1,"topologyHash",HEX64,"state","AUTHORIZED"));
json = codec.write(event, AgentJsonCodec.MAX_EVENT_BYTES);
decoded = codec.readObject(json);
assert exact safe payload and no forbidden keys/values;
future = same event with type "FUTURE_NATIVE_EVENT";
assertEquals("FUTURE_NATIVE_EVENT",
    codec.readObject(codec.write(future, AgentJsonCodec.MAX_EVENT_BYTES)).get("type"));
```

- Verification contribution: additive EVENT-901 and unknown-consumer regression; expected GREEN both before and after File8.
- After this file: codec compatibility is explicitly proven; no runtime producer is wired yet.

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`

- Purpose: Select and validate the Strategy, bind the exact model budget view/Gate, merge fatal control, emit authorization audit through the existing CAS sink and assemble truthful native completion without changing legacy constructors/paths.
- Symbols: `executeScoped`; `buildRoot`/factory selection; native snapshot/tree/model validation; `completedOutput`; native `RunEventSink` appender; existing catch/finally; no new constructor.
- Repository evidence: current `executeScoped` owns one QUEUED claim, actual factory map, Runner/plugin, deadline/heartbeat/idle merge, projector, event sink, result and safe terminal resolution; moving ownership elsewhere would add a layer and weaken fencing.
- Dependencies and consumers: Step1 Strategy/bound estimator; Files2/5 Gate/plugin; Files6-7 tests; existing ledger/authorization/session/tool/projector consumers unchanged. Step3 later contributes actual strategies through the same map.
- Why now: all lower-level contracts and SDK behavior are fixed/GREEN; this is the final minimum production path before host exposure.
- Contract/signature changes: public constructors/map generic/routes/results remain. Internal selection retains the selected Function locally—no `CompiledTemplate`; strategy is detected by `instanceof`. Add a package-private output helper only for INTERNAL-905 testing. `RunEventSink` gains one private native append method over existing `append`.
- Input/output and state mapping: selected map value + snapshot -> plain root or exact native root; strategy metadata + actual tree/host model -> topologyHash/Gate/NativeModelBinding; bound estimator -> plugin; Gate failure Flowable -> existing control; Gate outputs/root -> existing AgentExecutionResult.
- Error and edge behavior: any native snapshot/tree/model/schema/tool/config/callback drift fails before model. Append CAS failure prevents Gate mutation/native action. Fatal is checked before completion and emits a sanitized error with the same authorization decision, ledger code, timeout cause or safe policy/unknown classification as the callback channel, so the existing catch order produces the same SUSPENDED/current-run resolver/TIMED_OUT/FAILED result under either race winner. Native selected failures use UNKNOWN side effect even pre-model; legacy result/error/side-effect logic remains byte/behavior equivalent. No resume/rehydration is added.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001` — reuses exact existing orchestration, CAS and result owners; no constructor/API/schema/dependency drift.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11` — exact snapshot/callback validation, existing Jackson-compatible Map, current Duration/Instant control and Strategy/State collaboration in the same technical vertical.
- Implementation pseudocode:

```java
selectedFactory = select existing map value once for CODE_FIRST; // null for CONFIG
root = buildRoot(definition, tools, selectedFactory)
NativeAgentCompilerStrategy strategy = selectedFactory instanceof NativeAgentCompilerStrategy s ? s : null
nativeSelected = strategy != null
if (strategy != null) {
  spec = strategy.templateSpec()
  require map key == definition.codeRef == spec.codeRef; mode CODE_FIRST; schemas "1";
  require configuration/tools/allowedToolVersions empty and maxToolCalls==0;
  require route/spec route and validated snapshot policy; reject raw callbacks/tools as existing guard does;
  actualModel = chatModels.get(spec.modelBeanName); require actualModel != null && strategy.usesChatModel(actualModel)
  topologyHash = spec.topologyHash(snapshot.definition().dependencies().policyVersionRef())
  gate = NativeAgentGate.bind(spec, root, topologyHash,
      (author,payload) -> eventSink.append("NATIVE_TRANSFER_AUTHORIZED", author, payload))
  executionEstimator = budgetEstimator.bindNative(snapshot,
      new NativeModelBinding(spec.codeRef, spec.modelBeanName, spec.modelName, actualModel))
} else executionEstimator = budgetEstimator

budget = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, executionEstimator,
    authorization, Schedulers.from(controlExecutor), controlExecutor, contextAssembler,
    material.authorization(), contextOptions, toolScope, gate)
controlSignals = gate == null
    ? Flowable.mergeArray(deadline, heartbeatControl, budget.idleTimeout())
    : Flowable.mergeArray(deadline, heartbeatControl, budget.idleTimeout(), gate.failureSignal())
runner.runAsync(userId, sessionId,
    Content.fromParts(input.parts().stream().map(Part::fromText).toArray(Part[]::new)), config)
  .takeUntil(controlSignals).blockingForEach(event -> {
  projection = projector.project(event, agentNames)
  if (gate == null) preserve current complete-agent map behavior
  else gate.observeEvent(event, projection)
  projection.ifPresent(p -> eventSink.append(p.type(),p.author(),p.payload()))
})
sideEffect = sealAndFreeze(eventSink, toolScope, "UNKNOWN")
output = completedOutput(root, legacyAgentOutputs, gate) // native calls requireHealthy/complete first
finish existing result; for any nativeSelected failure force UNKNOWN, otherwise preserve existing legacy expression
catch order remains: AgentExecutionAuthorization.Failure -> suspend;
  REVISION_CONFLICT/LEASE_EXPIRED -> current-run resolver; CancellationSignal -> cancel;
  ModelIdleTimeout/TimeoutException cause -> timed out; remaining safe error -> failed
finally seal/close scope/Runner/Session as today; Gate is closed by Plugin/Runner close
```

- Verification contribution: makes File6 native completion/control tests GREEN; Files1/3/4/7 remain GREEN; old projector/factory/tool/context tests prove compatibility.
- After this file: AR native capability is complete and finite-testable, but production AS still supplies no native strategy; marker-only default runtime remains safe.

#### File 9 — `CREATE docs/egon/evidence/2026-11-05-native-agent-gate.md`

- Purpose: Record actual Step2 assertion RED, Gate A/B, plugin/Runner RED/GREEN, completion GREEN, event regression, static/JAR/API facts and runtime exclusions.
- Symbols: baseline/Step1 commit; exact commands/exits/Surefire counts; ADK/GenAI hashes; event payload/privacy; no AS factory; remaining Step3/T3-09.
- Repository evidence: established append-only per-Step evidence convention and Step1 evidence.
- Dependencies and consumers: only actual Root outputs after Files1-8 freeze; final conformance audit consumes it.
- Why now: prevents a source-only or mocked proof from being mislabeled real provider/runtime acceptance.
- Contract/signature changes: documentation only.
- Input/output and state mapping: actual logs/reports/diff/hashes -> bounded evidence; no prefilled PASS.
- Error and edge behavior: distinguish assertion RED from compile/cache/fixture failure; record optional PG class as unselected; explicitly state no listener/App/provider/database/browser.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — records every Step2 PASS/N/A with concrete test/static/JAR-API evidence and remaining runtime boundary.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — records applicable enforcement and evidence-backed Rule4/7 N/A without weakening blocking Rule11.
- Implementation pseudocode:

```text
record actual Gate RED/A/B, typed failure-disposition races, plugin/Runner RED/GREEN, completion/event regression commands, exits and owning Surefire reports
record pinned ADK/GenAI hashes and distinguish mocked unit facts from real in-memory Runner facts and unexecuted provider/DB behavior
set RuntimeUnverified=true, ASFactoriesRegistered=false, T3_09Complete=false; list Step3 and dynamic decomposition as remaining
```
- Verification contribution: Step2 audit/commit gate.
- After this file: Step2 has honest, reproducible evidence and is ready for one path-limited commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Assertion RED command (Root only): the §8 common offline source-reactor base plus `-Dtest=NativeAgentGateTest`; expected nonzero only for the named missing Gate assertion.
- Gate compile A / typed RED command: the same base plus `-Dtest=NativeAgentGateTest`; tests compile and fail only the named provenance/FSM/collector assertions.
- Plugin/Runner RED command: the same base plus `-Dtest=NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest`; expected nonzero only for missing native plugin routing/current-role/SDK behavior.
- Focused GREEN command: the same base plus `-Dtest=NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,TianjiAgentRuntimeAutoConfigurationTest`; expected exit 0; all named owning-module reports exist with zero failure/error/unexpected skip; no `*PostgresTest`, listener or provider selector.
- Static gates: exact Step2 path diff; no POM/C/CL/API/SQL/Components/frontend/config path; no `AgentTool`, Graph/DSL/nested Runner, new scheduler/thread, `java.util.Date/Calendar/SimpleDateFormat`, unsafe event field or `HttpProcessClientTest` selector; `git diff --check`.
- Verification command: the exact §8 S2 Gate RED/A, plugin/Runner RED and focused GREEN selectors plus Step2 static path/rule checks.
- Expected result: all named S2/legacy reports exist and pass with zero failure/error/unexpected skip; no AS strategy is registered; no PG/listener/provider path runs.
- Failure returns to: File1/2 for Gate contract/state; File3/5 for plugin order/context; File4 for real SDK fixture/order; File6/8 for selection/control/completion; File7 for Jackson/unknown-type compatibility; Step1 if bound estimator/metadata contract is wrong.
- Completion criteria: all allowed/failure transitions, explicit BaseLlm binding, current-role pre-budget cases, exact native Method, audit-before-action, safe actual call ID, identical after replay, sticky fatal with preserved SUSPENDED/current-run resolver/TIMED_OUT/FAILED disposition, genuine root outputs, exact-snapshot shared model budgets/zero tools and legacy regressions are GREEN; AS still registers nothing.
- Rollback: revert only Step2 paths; no data/config/runtime effect. Authorization event rows cannot exist because no runtime/database is started by execution validation.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGateTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentGate.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunContextTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/NativeAgentTransferRunnerTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkRunBudgetPlugin.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkNativeAgentCompletionTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentJsonCodecTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/AdkAgentRunExecutor.java`, `docs/egon/evidence/2026-11-05-native-agent-gate.md`.
- Commit: `feat(agent): govern native transfer callbacks`

### Step 3 — Register two fixed templates behind default-off host configuration

- Requirements: `REQ-901`, `REQ-902`, `REQ-903`, `REQ-904`, `REQ-905`, `REQ-906`, `REQ-907`, `REQ-917`, `REQ-918`, `REQ-919`, `REQ-920`; final regression also re-proves `REQ-908`, `REQ-909`, `REQ-910`, `REQ-911`, `REQ-912`, `REQ-913`, `REQ-914`, `REQ-915`, `REQ-916` without changing their Step2 ownership.
- Dependencies: Step2 committed; AR capability and real Runner tests GREEN; actual current AR/AS target paths show no material concurrent drift.
- Baseline state: runtime can govern an explicitly supplied synthetic native strategy, but AS contributes none and `application.yml` has no native-template keys.
- Observable outcome: when and only when the four host keys enable a valid existing model binding, AS contributes exactly `conditional-research-v1` and `supervisor-research-v1` to the unchanged named Function map; default configuration remains inert.
- End state: the complete first fixed-template slice is source/package complete, default off, finite-test/JAR verified, and explicitly not live/provider/database/T3-09 acceptance.
- Test-first gate: `Required` — configuration/resource reflection assertions fail for missing types/keys; each factory test first fails for its missing class, then compiles against the minimum signature and fails its typed tree/metadata assertions; context wiring remains RED until the configuration and YAML are complete.
- Manual Checks: all 17 are evaluated separately: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`. Applicable: ARCH, REUSE, DEP, NAME, VALID, MODEL, BEAN, UTIL, CONFIG, PATTERN, SCOPE, TEST, BLOCKER. Evidence-backed N/A: CONVERT (no cross-layer projection), LOG (plain technical factories/config only; no concrete business Service), JSON (no new wire/event shape), TIME (no new/touched time symbol).
- Literal Rules: all ten are evaluated separately: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11`. Applicable: Rules 1, 2, 3, 4 (explicit named `@Bean`/qualified method injection only), 5, 7, 9, 11. Evidence-backed N/A: Rule 6 (no JSON contract changed), Rule 10 (no date/time symbol). Rule 11 is blocking for every file.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java`

- Purpose: Establish the AS type/resource assertion RED and later prove default-off, strict enable-time validation, exact named map collection and no provider/credential creation.
- Symbols: `native_template_types_and_four_resource_keys_exist`; `disabled_configuration_contributes_no_factory_map`; `enabled_configuration_requires_exact_existing_model_and_adapter`; `enabled_configuration_contributes_exactly_two_function_assignable_strategies`; `callback_bearing_or_model_name_mismatch_fails_closed`; `base_yaml_is_default_off_and_has_complete_key_set`.
- Repository evidence: `RagContextConfigurationTest` and process-tool configuration tests use ApplicationContextRunner/strict properties; runtime auto-config owns fallback bean name `tianjiAgentCodeFirstFactories`; Agent Flow owns qualified `agentFlowChatModels` and `springAiModelAdapterFactory`.
- Dependencies and consumers: starts with `Class.forName` plus `YamlPropertySourceLoader`/resource assertions; after Files2/4/6/7 it uses typed properties/context. File8 supplies the resource keys. No runtime auto-config, DataSource or application listener is started.
- Why now: prevents production factory/config creation before the default-off and host-only contract is fixed.
- Contract/signature changes: fixes prefix `stellar.tianji.agent.native-templates`, keys `enabled`, `model-bean-name`, `model-name`, `model-route-ref`, bean name `tianjiAgentCodeFirstFactories`, exact map ABI and two codeRefs.
- Input/output and state mapping: off/missing property -> no native map contribution; enabled properties + existing named ChatModel + existing adapter -> immutable map of two Strategy values; no credential/network/model call occurs.
- Error and edge behavior: blank/trimmed/control/invalid route ref, missing map key/model options/name, duplicate/extra factory, callback-bearing `ToolCallingChatOptions`, wrong Bean qualifier or unknown property fails context; empty/off values in base YAML do not instantiate properties.
- Standards impact: `MC-VALID-001`, `MC-BEAN-001`, `MC-CONFIG-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact strict properties, named Bean/Qualifiers and sole-profile key parity/default-off proof.
- Literal rule enforcement: `Rule 2`, `Rule 4`, `Rule 5`, `Rule 7`, `Rule 11` — validates configuration handoff, explicit named map/qualified dependencies, existing Spring/JDK utilities and full base-profile key set in the current AS package.
- Implementation pseudocode:

```java
@Test void disabled_configuration_contributes_no_native_map() {
  runner.withUserConfiguration(TianjiNativeAgentTemplateConfiguration.class)
    .withBean("agentFlowChatModels", Map.class, () -> Map.of())
    .withBean("springAiModelAdapterFactory", SpringAiModelAdapterFactory.class, SpringAiModelAdapterFactory::new)
    .run(ctx -> assert no startup failure and no bean named tianjiAgentCodeFirstFactories);
}
@Test void exact_on_contributes_only_two_function_assignable_strategies() {
  ctx = configured(valid properties, exact named ChatModel/options, existing adapter)
  map = bean("tianjiAgentCodeFirstFactories")
  assertEquals(Set.of("conditional-research-v1","supervisor-research-v1"), map.keySet())
  assert every value instanceof NativeAgentCompilerStrategy && Function<Scheduler,? extends BaseAgent>
  verify(model, atLeastOnce()).getOptions(); // legitimate metadata/callback inspection
  verify(model, never()).call(any(Prompt.class));
  verify(model, never()).stream(any(Prompt.class)); // no provider request during configuration
}
```

- Verification contribution: Step3 reflection/resource RED, typed config GREEN, TEST-903 and default-off/JAR resource gates.
- After this file: reflection/resource assertions are RED until Files2-8; no production config exists.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java`

- Purpose: Bind and validate the four host-owned default-off properties without adding request/definition fields.
- Symbols: strict `@ConfigurationProperties(prefix="stellar.tianji.agent.native-templates", ignoreUnknownFields=false)` immutable record; compact constructor.
- Repository evidence: existing AS `*Properties` records use strict binding and compact validation; the native Configuration is conditional, so this record is instantiated only when enabled.
- Dependencies and consumers: Spring Boot existing binder; File7 configuration. No Validation group/HTTP DTO/Converter is created.
- Why now: compile seam A for the configuration test and exact property contract before factories/wiring.
- Contract/signature changes: new server-only record fields `boolean enabled`, `String modelBeanName`, `String modelName`, `String modelRouteRef`; no external API/JSON metadata.
- Input/output and state mapping: enabled property values -> exact immutable strings; preserve case/bytes and reject leading/trailing/control characters. Route ref must be a current versioned `key@positiveVersion#lowerHex64` reference. Disabled base YAML is excluded by File7's condition rather than normalized into fake values.
- Error and edge behavior: enabled false when directly instantiated, null/blank/oversize/trim drift, unsafe controls or invalid route ref throws one safe configuration error; unknown fields fail binding.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-CONFIG-001` — semantic Properties immutable value, JDK validation and strict four-key contract.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 7`, `Rule 11` — semantic suffix, immutable record compact validation, JDK-only validation, exact sole-profile keys and current AS package.
- Implementation pseudocode:

```java
@ConfigurationProperties(prefix="stellar.tianji.agent.native-templates", ignoreUnknownFields=false)
public record NativeAgentTemplateProperties(boolean enabled, String modelBeanName,
    String modelName, String modelRouteRef) {
  public NativeAgentTemplateProperties {
    require enabled; require exactSafe(modelBeanName,256); require exactSafe(modelName,512);
    require modelRouteRef matches VERSIONED_REF and is exact/untrimmed;
  }
}
```

- Verification contribution: converts property reflection RED to typed validation RED/GREEN; config remains absent.
- After this file: properties compile and validate independently; no factory/map is registered.

#### File 3 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactoryTest.java`

- Purpose: Define the exact conditional tree/metadata/model-identity and absence of tools/callbacks before the factory exists.
- Symbols: `conditional_factory_type_exists`; `builds_root_quick_deep_with_exact_edges_keys_flags_and_caps`; `all_agents_explicitly_wrap_the_same_base_llm`; `uses_only_exact_chat_model_identity`; `each_apply_builds_fresh_tree_with_same_semantic_hash`; `contains_no_tools_callbacks_code_executor_or_dynamic_input`.
- Repository evidence: Step1 Strategy test defines TemplateSpec ABI; existing ADK tests inspect LlmAgent names/subAgents/model/tools/callback lists; adapter factory is already available.
- Dependencies and consumers: reflection first; after File4 constructs the plain factory with mocked ChatModel exact options and existing adapter, invokes `apply(Schedulers.trampoline())`, inspects actual root/children and metadata.
- Why now: tree behavior is fixed before writing trusted host code.
- Contract/signature changes: fixes final class implementing Strategy and constructor `(ChatModel,String modelBeanName,String modelName,String modelRouteRef,SpringAiModelAdapterFactory)`; no Spring stereotype.
- Input/output and state mapping: trusted model/metadata -> fresh actual LlmAgent root with quick/deep children and stable TemplateSpec; model object identity -> `usesChatModel`.
- Error and edge behavior: null scheduler/model/adapter/metadata, changed model identity, peer/parent flags, output-key overlap, callback/tool/toolset/code executor or unexpected child fails tests/constructor.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 9`, `Rule 11` — semantic Factory behavior type, exact internal validation/immutable metadata, JDK/existing adapter and fixed Strategy topology.
- Implementation pseudocode:

```java
root = factory.apply(Schedulers.trampoline())
assert root.name=="conditional_root" && root.outputKey=="conditional_root_output"
assert exact children [quick_research/quick_research_output, deep_research/deep_research_output]
assert children allow parent and disallow peers; root owns exactly those children
rootModel = root.model().flatMap(Model::model).orElseThrow()
assert every child.model().flatMap(Model::model).orElseThrow() isSameAs(rootModel)
assert rootModel.model()==MODEL_NAME; do not call resolvedModel()
assert TemplateSpec codeRef/schema/caps/order == conditional-research-v1/1/1/depth2/transfers2/child1
assert no tools/toolsets/callbacks/codeExecutor and factory.usesChatModel(model) by identity only
```

- Verification contribution: factory assertion/typed RED and TEST-902/conditional half of TEST-911.
- After this file: reflection RED until File4; then exact tree tests GREEN without Spring registration.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactory.java`

- Purpose: Implement the fixed conditional router with one quick/deep choice and return to root, using one trusted model binding and no tools.
- Symbols: final `ConditionalResearchAgentFactory implements NativeAgentCompilerStrategy`; exact codeRef/name/output/instruction constants; constructor; `apply`, `templateSpec`, `usesChatModel`.
- Repository evidence: existing Code-First Function ABI and `SpringAiModelAdapterFactory#create`; ADK LlmAgent builder supports subAgents/outputKey/transfer flags; no generic graph builder is selected.
- Dependencies and consumers: File2 properties values are passed later by File7; File3 test; AR executor sees only Strategy/Function.
- Why now: minimum B for the conditional factory after its tests.
- Contract/signature changes: internal host class only; returns a fresh tree each apply; TemplateSpec is immutable and stable.
- Input/output and state mapping: one adapter instance per tree shared by root/children; fixed instructions permit only root choice, child research summary and transfer back; scheduler is required nonnull but creates no thread/Runner.
- Error and edge behavior: constructor validates exact metadata; factory never reads requests/config/session/env or model output; any semantic change requires a new codeRef/definition version per Spec.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-PATTERN-001`, `MC-SCOPE-001` — reuses adapter/Strategy, fixed topology, no dependency/Bean/DSL.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 9`, `Rule 11`.
- Implementation pseudocode:

```java
apply(scheduler):
  require scheduler != null
  BaseLlm adapted = adapter.create(chatModel, modelName)
  quick = LlmAgent.builder().name("quick_research").outputKey("quick_research_output")
      .model(adapted).instruction(FIXED_QUICK_SUMMARY_THEN_PARENT_TRANSFER)
      .disallowTransferToParent(false).disallowTransferToPeers(true).build()
  deep = LlmAgent.builder().name("deep_research").outputKey("deep_research_output")
      .model(adapted).instruction(FIXED_DEEP_SUMMARY_THEN_PARENT_TRANSFER)
      .disallowTransferToParent(false).disallowTransferToPeers(true).build()
  return LlmAgent.builder().name("conditional_root").outputKey("conditional_root_output")
      .model(adapted).instruction(FIXED_CHOOSE_EXACTLY_ONE_CHILD_THEN_FINAL)
      .subAgents(quick,deep).disallowTransferToParent(true).disallowTransferToPeers(true).build()
usesChatModel(candidate) = candidate == chatModel
```

- Verification contribution: File3 GREEN; supplies one of two map values.
- After this file: conditional factory is constructible directly but not a Bean/map member.

#### File 5 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactoryTest.java`

- Purpose: Define exact supervisor tree/metadata, ordered researcher/critic roles and model identity before production creation.
- Symbols: `supervisor_factory_type_exists`; `builds_supervisor_researcher_critic_with_exact_keys_flags_caps_order`; `all_agents_explicitly_wrap_the_same_base_llm`; `instructions_encode_only_fixed_round_trip`; `no_tools_callbacks_dynamic_agents_or_model_substitution`.
- Repository evidence: same Strategy/ADK builder conventions as File3; Spec fixes max four transfers and two children in researcher-then-critic order.
- Dependencies and consumers: reflection first; after File6 constructs and inspects actual LlmAgent tree/metadata with existing adapter.
- Why now: independent RED for the second fixed semantic unit.
- Contract/signature changes: final Strategy constructor matches File4; exact codeRef `supervisor-research-v1`.
- Input/output and state mapping: model/metadata -> root supervisor with researcher and critic children; TemplateSpec required order `[researcher,critic]` and caps depth2/transfers4/children2.
- Error and edge behavior: peer transfers, reversed order, duplicate/unknown child, model identity drift, output overlap/reserved keys or any tool/callback fails.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact supervisor behavior/type/tree metadata and fixed Strategy-order proof.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 9`, `Rule 11`.
- Implementation pseudocode:

```java
factory = new SupervisorResearchAgentFactory(exactModel, BEAN, MODEL, ROUTE, adapter)
root = factory.apply(Schedulers.trampoline())
assert exact root supervisor/supervisor_root_output and children researcher/researcher_output, critic/critic_output
rootModel = root.model().flatMap(Model::model).orElseThrow()
assert researcher.model().flatMap(Model::model).orElseThrow() isSameAs(rootModel)
assert critic.model().flatMap(Model::model).orElseThrow() isSameAs(rootModel)
assert rootModel.model()==MODEL_NAME; do not call resolvedModel()
assert TemplateSpec required order researcher then critic, depth=2, maxTransfers=4, requiredChildren=2
assert every Agent has the exact transfer flags and no tools/toolsets/callbacks/codeExecutor; usesChatModel is identity-only
```
- Verification contribution: factory assertion/typed RED and TEST-902/supervisor half of TEST-912.
- After this file: reflection RED until File6; then exact tree tests GREEN.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactory.java`

- Purpose: Implement the fixed supervisor -> researcher -> supervisor -> critic -> supervisor tree without dynamic task creation.
- Symbols: final Strategy class; exact codeRef/name/output/instruction constants; constructor; `apply`, `templateSpec`, `usesChatModel`.
- Repository evidence: File4 pattern and ADK LlmAgent public builder; no reusable generic factory is needed for only two fixed reviewed topologies.
- Dependencies and consumers: File5; File7 map; AR Strategy/executor.
- Why now: minimum B after its dedicated tests and before common configuration.
- Contract/signature changes: internal host class only, same trusted binding signature as conditional.
- Input/output and state mapping: one adapted BaseLlm -> three fresh LlmAgents; root owns both children; children return only to parent; fixed root instruction orders researcher then critic then final.
- Error and edge behavior: no request/config topology, sibling transfer, repeated Agent, tool/callback or dynamic task count; semantic change requires new codeRef.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-PATTERN-001`, `MC-SCOPE-001` — reuses adapter/Strategy in the existing host package, fixed ordered topology and no dependency/Bean/DSL.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 9`, `Rule 11`.
- Implementation pseudocode:

```java
apply(scheduler):
  require scheduler != null
  BaseLlm adapted = adapter.create(chatModel, modelName)
  researcher = LlmAgent.builder().name("researcher").outputKey("researcher_output")
      .model(adapted).instruction(FIXED_RESEARCH_THEN_PARENT)
      .disallowTransferToParent(false).disallowTransferToPeers(true).build()
  critic = LlmAgent.builder().name("critic").outputKey("critic_output")
      .model(adapted).instruction(FIXED_CRITIQUE_THEN_PARENT)
      .disallowTransferToParent(false).disallowTransferToPeers(true).build()
  return LlmAgent.builder().name("supervisor").outputKey("supervisor_root_output")
      .model(adapted).instruction(FIXED_RESEARCHER_THEN_CRITIC_THEN_FINAL)
      .subAgents(researcher, critic)
      .disallowTransferToParent(true).disallowTransferToPeers(true).build()
templateSpec.requiredChildOrder = List.of("researcher", "critic")
templateSpec.maxDepth=2; templateSpec.maxTransfers=4; templateSpec.requiredChildren=2
usesChatModel(candidate) = candidate == chatModel
```

- Verification contribution: File5 GREEN; supplies second map value.
- After this file: both direct factories are GREEN and still unregistered.

#### File 7 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java`

- Purpose: Conditionally collect the two fixed strategies into the exact existing named Function map using an existing host ChatModel and adapter only.
- Symbols: `@Configuration(proxyBeanMethods=false)`, `@ConditionalOnProperty`, `@EnableConfigurationProperties`; named `@Bean("tianjiAgentCodeFirstFactories")` method.
- Repository evidence: runtime auto-config provides the same named empty fallback only when missing; Agent Flow auto-config provides qualified model map/adapter; AS configuration classes use explicit `@Bean` method injection.
- Dependencies and consumers: Files2/4/6; `@Qualifier("agentFlowChatModels") Map<String,ChatModel>` and `@Qualifier("springAiModelAdapterFactory") SpringAiModelAdapterFactory`; runtime auto-config consumes the produced map unchanged.
- Why now: factories and strict properties are complete, so wiring cannot expose an incomplete capability.
- Contract/signature changes: one host Bean with the existing ABI and stable name. No Bean per factory, no second registry, credentials/client/Runner/definition publication.
- Input/output and state mapping: exact properties modelBeanName -> existing map identity; require `model.getOptions().getModel()==modelName`; reject nonempty ToolCallingChatOptions callbacks; instantiate two factories; return immutable two-entry Map typed as existing Function ABI.
- Error and edge behavior: missing/extra/blank model, null/options mismatch, callback-bearing options, duplicate keys or missing adapter fails context. Disabled property omits this entire configuration and leaves runtime fallback behavior.
- Standards impact: `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-BEAN-001`, `MC-CONFIG-001`, `MC-SCOPE-001` — exact existing Bean names/Qualifiers, no field injection/Lombok constructor need, no dependency/provider.
- Literal rule enforcement: `Rule 2`, `Rule 4`, `Rule 5`, `Rule 7`, `Rule 9`, `Rule 11` — explicit named Bean and every injected method parameter qualified; fixed Strategy selection; current AS structure.
- Implementation pseudocode:

```java
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix=PREFIX,name="enabled",havingValue="true")
@EnableConfigurationProperties(NativeAgentTemplateProperties.class)
class TianjiNativeAgentTemplateConfiguration {
 @Bean("tianjiAgentCodeFirstFactories")
 Map<String,Function<Scheduler,? extends BaseAgent>> factories(
   NativeAgentTemplateProperties p,
   @Qualifier("agentFlowChatModels") Map<String,ChatModel> models,
   @Qualifier("springAiModelAdapterFactory") SpringAiModelAdapterFactory adapter) {
   model = require exact models.get(p.modelBeanName)
   require model.options != null && p.modelName.equals(model.options.model)
   if options instanceof ToolCallingChatOptions require callbacks null/empty
   conditional = new ConditionalResearchAgentFactory(model, p.modelBeanName(),
       p.modelName(), p.modelRouteRef(), adapter)
   supervisor = new SupervisorResearchAgentFactory(model, p.modelBeanName(),
       p.modelName(), p.modelRouteRef(), adapter)
   return Map.of(conditional.templateSpec().codeRef(), conditional,
                 supervisor.templateSpec().codeRef(), supervisor)
 }
}
```

- Verification contribution: makes File1 off/on/mismatch/map tests GREEN except resource key assertions pending File8.
- After this file: enabling through test properties yields exactly two strategies; base application remains off until File8.

#### File 8 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Declare the complete sole-profile default-off operator key set without credentials or model defaults that could accidentally enable execution.
- Symbols: `stellar.tianji.agent.native-templates.enabled`, `model-bean-name`, `model-name`, `model-route-ref`.
- Repository evidence: one base application.yml and no environment-specific profile files; adjacent Agent features are explicitly default off.
- Dependencies and consumers: File2 binder/File7 condition; operator overrides at deployment; File1 resource test.
- Why now: all typed validation/wiring exists; resource exposure is last.
- Contract/signature changes: four server configuration keys only; default enabled false and three metadata values empty strings (ignored because configuration is not instantiated while off).
- Input/output and state mapping: base config -> no native Bean/map contribution; explicit deployment override of all four -> strict File2/File7 validation.
- Error and edge behavior: partial enable fails at startup; no fallback model/route, credential, provider URL, prompt or graph value.
- Standards impact: `MC-CONFIG-001`, `MC-SCOPE-001`, `MC-TEST-001` — sole profile has one complete key set and safe values.
- Literal rule enforcement: `Rule 7`, `Rule 11` — configuration parity is complete for the only AS profile and remains in current resource path.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    agent:
      native-templates:
        enabled: false
        model-bean-name: ""
        model-name: ""
        model-route-ref: ""
# File placement: add beside the other stellar.tianji.agent default-off feature blocks.
# Runtime meaning: while enabled=false, the conditional Configuration and strict Properties record are not instantiated.
# Enablement requires an operator to override all three empty metadata values; no credential/model/client is synthesized.
```

- Verification contribution: closes resource RED/default-off/config parity; packaged AS JAR inspection must show these exact values.
- After this file: source wiring is complete and default off; no runtime starts automatically.

#### File 9 — `CREATE docs/egon/evidence/2026-11-05-native-agent-templates.md`

- Purpose: Record actual Step3 RED/A/B/GREEN, full finite regression, scope/static gates and clean AR/AS JAR contents while preserving runtime/non-goal boundaries.
- Symbols: prior commit hashes; exact test reports/counts; YAML/map/tree evidence; AR/AS JAR SHA/class/resource inventory; no live acceptance; remaining T3-09.
- Repository evidence: Step1/2 evidence convention and actual package outputs.
- Dependencies and consumers: actual Root commands only after Files1-8 freeze; final Spec conformance review.
- Why now: closes source/package delivery without upgrading the Review Spec to Accepted/Implemented or claiming deployment.
- Contract/signature changes: documentation only.
- Input/output and state mapping: actual finite outputs/diffs/JAR hashes -> evidence; no fabricated pass/count/runtime state.
- Error and edge behavior: offline cache/compile/test/JAR mismatch blocks commit; unexpected skips are not pass; stale artifact is prevented by clean verify; no optional PG/listener/provider selector.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — records all final PASS/N/A evidence, fresh JAR contents and explicit runtime limits.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — records Step3 enforcement plus Rule6/10 N/A and the full-regression JSON/time proof.
- Implementation pseudocode:

```text
record actual Step3 reflection/A/typed-RED/GREEN and final clean-verify commands, exits and every owning Surefire report/count
record AR/AS JAR paths, SHA-256 values, exact class entries and packaged default-off YAML excerpt from fresh artifacts
set FeatureDefaultOff=true, RuntimeUnverified=true, ProductionAccepted=false and DynamicTaskDecompositionImplemented=false
```
- Verification contribution: Step3/final audit and commit gate.
- After this file: three-Step source delivery is reviewable and auditable; Root stops without starting the application.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Initial reflection/resource RED command: §8 common base plus `-Dtest=TianjiNativeAgentTemplateConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest`; expected nonzero only named missing type/key assertions.
- Properties/factory compile A and typed RED commands: run each owning test after its minimum production signature; expected compile success and named metadata/tree/wiring assertion failures only.
- Focused GREEN command: common base plus `-Dtest=TianjiNativeAgentTemplateConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,NativeAgentCompilerStrategyTest,NativeAgentGateTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,JTokkitChatModelBudgetEstimatorTest,AdkRunContextTest,AgentJsonCodecTest,TianjiAgentRuntimeAutoConfigurationTest,TianjiAgentServerApplicationTest`; expected exit 0 and all owning reports present.
- Final clean finite verify/JAR gate: §8 final command with `STELLAR_VERIFY_GOAL=verify` and `clean`; then exact `jar tf`/`unzip -p`/SHA commands in §8 prove the new AR classes, AS classes and default-off YAML are in the freshly built artifacts.
- Static gates: exact Step3 path diff and all three-Step aggregate path audit; no POM/C/CL/API/Controller/permission/SQL/Components/frontend path; no credentials/provider URLs/AgentTool/Graph/DSL/nested Runner/new listener; no `HttpProcessClientTest` or `*PostgresTest` selector; `git diff --check`.
- Verification command: the exact §8 S3 RED/GREEN, final clean finite verify, fresh AR/AS JAR inspection, strict docs and aggregate static scope gates.
- Expected result: exactly two strategies are present only when enabled; all named reports pass without unexpected skip; fresh JARs contain the exact classes/default-off YAML; aggregate diff contains only declared paths.
- Failure returns to: File1/2/7/8 for binding/default-off; File3/4 for conditional tree; File5/6 for supervisor tree; Step1 for metadata/budget; Step2 for Gate/runtime; Spec if the pinned SDK or current source contract drifted materially.
- Completion criteria: off contributes no native strategies; on contributes exactly two fixed correct strategies using exact model identity; all focused/full finite tests and artifact checks pass; evidence states no runtime/T3-09 overclaim.
- Rollback: disable flag is operational rollback for future deployments; source rollback is the exact Step3 commit. No data/API migration exists. In-flight behavior remains explicitly unsupported/no resume.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfigurationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/NativeAgentTemplateProperties.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactoryTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/ConditionalResearchAgentFactory.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactoryTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/SupervisorResearchAgentFactory.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/agenttemplate/TianjiNativeAgentTemplateConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-05-native-agent-templates.md`.
- Commit: `feat(agent-server): add default-off native templates`

## 8. Test, Validation, and Quality Gates

The literal common offline source-reactor base used by every Maven row is:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml \
  -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp \
  -DskipITs=true \
  -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none
```

Each row appends exactly the shown `-Dtest=...` selector before the script-supplied `test` goal. `/tmp/tianji-compat-fix/settings.xml` is an existing no-credential/no-mirror local-cache settings file. Missing JDK/settings/source/cache, compilation, fixture setup, listener, database or network errors are environment/implementation failures—not behavioral RED. Root records the actual exit and every owning-module Surefire report; `surefire.failIfNoSpecifiedTests=false` only permits unrelated reactor modules to have no selected test and never permits a named owning test to be missing or skipped.

| Gate/order | Working directory | Exact command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| S1 assertion RED | `tianji-service` | common base + `-Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest` | absent Strategy/SPI reflection | nonzero only named missing-type/method assertions | Step1 Files1-2 | 901/917; JVM only |
| S1 compile A / typed RED | same | same selector after Files3-4 signatures | typed metadata/binding | compile succeeds; nonzero only named semantic assertions | Step1 Files1-4 | 901-907/917 |
| S1 GREEN | same | common base + `-Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest` | compiler/budget/legacy | exit 0; every named report zero fail/error/skip | Step1 Files1-5 | 901-907/917/919; no native execution |
| S1 docs | off-repo eventual-path mirror described below, then repository after GO | strict Spec + Plan validators | Template9/Template5 links/trace/rules | both exit 0; no warning | Step1 Files6-7 | all; docs only |
| S2 Gate assertion RED | `tianji-service` | common base + `-Dtest=NativeAgentGateTest` | absent Gate | nonzero only named missing Gate assertion | Step2 File1 | 905-916 |
| S2 Gate A / typed RED | same | same selector after Gate signatures | provenance/FSM/collector | compile succeeds; only named behavior assertions fail | Step2 Files1-2 | 905-916 |
| S2 plugin/Runner RED | same | common base + `-Dtest=NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest` | native tracking and real SDK order | nonzero only missing native plugin/current-role behavior | Step2 Files3-5 | 908-917; in-memory Runner only |
| S2 GREEN/regression | same | common base + `-Dtest=NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,TianjiAgentRuntimeAutoConfigurationTest` | Gate/plugin/executor/event/legacy tools | exit 0; named owning reports present and zero fail/error/skip | Step2 Files1-8 or Step1 | 901-919; no PG/listener/provider |
| S3 reflection/resource RED | same | common base + `-Dtest=TianjiNativeAgentTemplateConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest` | absent AS types/YAML | nonzero only named missing type/resource assertions | Step3 Files1/3/5 | 902-907 |
| S3 typed GREEN | same | common base + `-Dtest=TianjiNativeAgentTemplateConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,NativeAgentCompilerStrategyTest,NativeAgentGateTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,JTokkitChatModelBudgetEstimatorTest,AdkRunContextTest,AgentJsonCodecTest,TianjiAgentRuntimeAutoConfigurationTest,TianjiAgentServerApplicationTest` | factories/default-off/wiring + core | exit 0; named owning reports present and zero fail/error/skip | Step3 Files1-8 | 901-920; contexts only, no server |
| Final clean finite verify | same | exact command below | Components + AR + AS selected source reactor | exit 0; exact finite tests only, fresh JARs produced | owning Step; material drift to Spec | all; static/JUnit/package only |
| Artifact content | same | exact `jar tf`, `unzip -p`, `shasum` commands below | actual fresh AR/AS JARs | exact classes/resource/default false present; hashes recorded | Step3 Files2/4/6-8 | 903/919; package proof, not installed runtime |
| Static scope/rules | same | exact `git diff`, `rg`, `shasum` review below, Root only | all changed paths/imports/non-goals | only declared paths; no forbidden construct/path; clean whitespace | owning file/Spec | 919/920; static |
| Manual/runtime | user/deployment environment | user separately configures an actual model/profile/definition and starts service if desired | provider/IAM/PostgreSQL/network/live AgentRun | intentionally not executed by this Plan/implementation workflow | separate acceptance task | `RuntimeUnverified`, `ProductionAccepted=false` |

The exact final clean finite verify command is:

```bash
STELLAR_VERIFY_GOAL=verify \
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml \
  -s /tmp/tianji-compat-fix/settings.xml \
  -o clean -B -ntp \
  -DskipITs=true \
  -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none \
  -Dtest=NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,TianjiAgentRuntimeAutoConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,TianjiNativeAgentTemplateConfigurationTest,TianjiAgentServerApplicationTest,AgentFlowAdapterRegressionTest
```

Explicitly forbidden selectors are `*PostgresTest`, any native `App`/listener test, `HttpProcessClientTest`, provider/network tests and any unbounded wildcard suite. Root verifies the new owning reports exist under AR/AS `target/surefire-reports` and are not skipped; unrelated modules with no selected class are allowed only because the script spans a source reactor.

The exact fresh-artifact checks after that command are:

```bash
test -f stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar
test -f stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar
for task_class in NativeAgentCompilerStrategy NativeAgentGate AdkRunBudgetPlugin AdkAgentRunExecutor; do
  jar tf stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar \
    | rg -x "top/egon/cola/platform/tianji/agent/infrastructure/adk/${task_class}\\.class"
done
for task_class in NativeAgentTemplateProperties ConditionalResearchAgentFactory SupervisorResearchAgentFactory TianjiNativeAgentTemplateConfiguration; do
  jar tf stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar \
    | rg -x "top/egon/cola/platform/tianji/agent/server/agenttemplate/${task_class}\\.class"
done
jar tf stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar | rg -x 'application\.yml'
unzip -p stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar application.yml \
  | rg -U 'native-templates:\n[[:space:]]+enabled: false\n[[:space:]]+model-bean-name: ""\n[[:space:]]+model-name: ""\n[[:space:]]+model-route-ref: ""'
shasum -a 256 \
  stellar-enclosure-tianji-agent-runtime/target/stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar \
  stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server.jar
```

Before repository document creation is authorized, validate the persistent `/tmp` artifacts at their eventual relative paths without touching the repository:

```bash
task_validation_root=$(mktemp -d /tmp/tianji-native-agentic-validation.XXXXXX)
mkdir -p \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/spec" \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/plan" \
  "$task_validation_root/Stellar-Enclosure/docs/plans"
cp /tmp/tianji-native-agentic-spec.md \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/spec/2026-11-05-04-45-native-agentic-transfer-templates.md"
cp /tmp/tianji-native-agentic-plan.md \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/plan/2026-11-05-05-22-native-agentic-transfer-templates.md"
cp /Users/mario/SelfProject/Stellar-Enclosure/docs/plans/agent-framework-upgrade-plan.md \
  "$task_validation_root/Stellar-Enclosure/docs/plans/agent-framework-upgrade-plan.md"
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/spec/2026-11-05-04-45-native-agentic-transfer-templates.md" --strict
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  "$task_validation_root/Stellar-Enclosure/tianji-service/docs/egon/plan/2026-11-05-05-22-native-agentic-transfer-templates.md" --strict
```

After Root authorizes Step1 document creation, rerun both validators on the real repository paths. The off-repository mirror is a small disposable copy tree under the exact printed `mktemp -d` path; this Plan does not prescribe automatic deletion and never targets a workspace/root directory.

Root performs these static reviews after every Step and on the aggregate final diff:

```bash
git diff --check
git diff --name-only --cached
rg -n 'java\.util\.(Date|Calendar)|SimpleDateFormat|AgentTool|GraphAgent|nested Runner|HttpProcessClientTest' \
  stellar-enclosure-tianji-agent-runtime/src/main/java \
  stellar-enclosure-tianji-agent-server/src/main/java \
  docs/egon/evidence/2026-11-05-native-agent-*.md
```

The expected `rg` result is no prohibited import/type/test-selector addition in the changed lines; mentions in evidence that explicitly say a non-goal are reviewed manually rather than treated as code. Root compares `git diff --name-only --cached` to the exact current Step commit paths and rejects any POM, C, CL, Controller/API, permission, SQL/manifest, Components, frontend, generated file, credential or unrelated formatting path. No `scripts/work` harness is planned: existing JUnit, validators, source-reactor, JAR tools, `rg`, SHA and Git path review cover every gate, so there is no harness disposition debt.

## 9. Migration, Compatibility, Rollout, and Rollback

### 9.1 Migration and generated-code disposition

N/A for data/schema/generated code. The Spec §11 and §16 explicitly require no table, column, index, SQL version, DDL manifest, Flyway/MP-SDJ execution, backfill, data repair, code generator, POM or dependency change. Existing migration files and history remain byte-for-byte untouched. The backend generator is inapplicable to the established Agent technical vertical and no classpath SQL journal entry is created.

### 9.2 Compatibility matrix

| Producer/consumer | Old binary/config with new counterpart | Required behavior | Gate |
| --- | --- | --- | --- |
| Existing plain Function factories | new AR | selected through same map; `instanceof` false; no Gate/`bindNative`; existing outputs/errors/side-effect semantics | Step1/2 legacy tests |
| Existing CONFIG definitions | new AR | current `require`, adapter, tools, projector and budget behavior unchanged | JTokkit/context/tool regressions |
| Custom `AgentModelBudgetEstimator` | new AR | compiles because `bindNative` is default fail-closed; never called for non-native | Step1 reflection/compatibility |
| Native AS disabled | new AR/AS | AS contributes no map; runtime fallback remains empty; no model/client/credential creation | Step3 ContextRunner/YAML/JAR |
| Existing Agent HTTP/SDK/event/result consumer | new AR/AS | routes/fields/envelope unchanged; additive String type `NATIVE_TRANSFER_AUTHORIZED` remains decodable as unknown | AgentJsonCodec/static path audit |
| Existing database | new AR/AS | same append/result tables and transaction contracts; no migration | path/manifest audit |
| In-flight legacy run | new binary | current behavior unchanged | regression only; no live claim |
| In-flight future native run during disable/rollback | factory may be unavailable | fail safely/operator handles; no resume promise | documented operational limit |

### 9.3 Rollout order

1. Finish and separately commit Steps1-3 only after Root accepts the effective Spec/Plan and gives source GO.
2. Deploy compatible AR/AS binaries with `stellar.tianji.agent.native-templates.enabled=false`.
3. Configure an already-existing host ChatModel and matching existing JTokkit profile/route/policy; do not add credentials/clients through this feature.
4. Publish new immutable definitions using the exact v1 codeRefs, CODE_FIRST, schema versions `"1"`, empty configuration/tools/allowed tools and `maxToolCalls=0`.
5. In a separately authorized environment, enable all four keys and run live IAM/model/network/PostgreSQL acceptance. Source/JAR proof alone is not this gate.
6. Monitor `NATIVE_TRANSFER_AUTHORIZED` only as authorization audit; correlate with terminal result and never report it as completed transfer.

### 9.4 Rollback and forward-fix

- Operational rollback is setting `enabled=false`, which stops contributing factories for new runs without changing data or public contracts.
- Source rollback is one path-limited revert per semantic Step in reverse order. Step3 can be reverted independently; Step2 then Step1 follow only after no native factory consumes them.
- No SQL rollback/repair exists. Existing authorization events/results remain readable because the envelope is unchanged.
- In-flight native runs have no checkpoint/resume contract; disable/redeploy may cause a bounded safe failure and operator follow-up. Do not invent rehydration or replay.
- Any ADK/GenAI JAR hash/public Method/callback/outputKey drift, material concurrent AR/AS change, or live provider ordering difference returns to the Spec before a forward fix.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-901` | §4/INTERNAL-901 | 1-2 | Strategy, executor | Strategy/Completion tests; final JAR | subtype ABI; old Function unchanged |
| `REQ-902` | §4/§7/§10 | 1-3 | Strategy, estimator, Gate, executor, factories/config | metadata plus explicit BaseLlm getter/identity/factory drift matrices | exact semantic hash/tree/explicit model/host identity/snapshot |
| `REQ-903` | §4/§16 | 3 | Properties/config/YAML | ContextRunner/resource/JAR | off zero; on exact two; no credentials |
| `REQ-904` | §4/§7 | 1-3 | TemplateSpec, executor, config | snapshot/admission negatives | fixed CODE_FIRST/v1/empty/zero-tool |
| `REQ-905` | §4/§7/§10 | 2-3 | Gate/conditional factory/executor | Gate + conditional Runner/factory | one quick/deep, max2, one returned |
| `REQ-906` | §4/§7/§10 | 2-3 | Gate/supervisor factory/executor | Gate + supervisor Runner/factory | researcher then critic, max4 |
| `REQ-907` | §4/§7 | 1-3 | Strategy/Gate/factories | key/tree/Runner outputKey tests | unique keys; child handoff nonfinal/absent state |
| `REQ-908` | §4/INTERNAL-903 | 2 | Gate/plugin | provenance/spoof matrix | exact public static Method/name/schema only |
| `REQ-909` | §4/§7 | 2 | Gate/plugin/executor sink | auth/session/CAS order tests | current authority and CAS before action |
| `REQ-910` | §4/§7 | 2 | Gate/plugin | safe-present/absent/exact-empty/unsafe ID; repeated-before; Runner | actual context+safe SDK ID, strict single use |
| `REQ-911` | §4/EVENT-901 | 2 | Gate/executor/codec test | payload/order/privacy/unknown type | exact AUTHORIZED event, bounded ordinal |
| `REQ-912` | §4/§7 rule0 | 2 | Gate/plugin/context test | known wrong role/stale child/confirmed root tests | tracking OR; current expected object pre-budget |
| `REQ-913` | §4/INTERNAL-902 | 2 | Gate/plugin/Runner | text/control/surrogate/byte matrices | current complete response; no normalization |
| `REQ-914` | §4/INTERNAL-903/904 | 2 | Gate/plugin | match/action/duplicate after tests | two-phase exact confirmation |
| `REQ-915` | §4/§7 | 2 | Gate/plugin/executor | authorization/ledger/timeout/policy race matrix + simulated later root | sticky fatal preserves existing safe disposition and dominates completion |
| `REQ-916` | §4/INTERNAL-905 | 2-3 | Gate/executor/factories | completion + Runner matrices | genuine root + exact current children/output |
| `REQ-917` | §4/INTERNAL-906 | 1-2 | estimator/JTokkit/plugin/executor | exact-snapshot bound-view math; equal-copy/forged-CONFIG drift; budget/tool-zero | shared exact model budget; zero ordinary tools |
| `REQ-918` | §4/§16 | 2-3 | Gate/executor/evidence | stale child/no rehydrate/path audit | one QUEUED claim; no resume/checkpoint |
| `REQ-919` | §4/§16 | 1-3 | all declared paths | legacy/full/static/JAR gates | no API/C/CL/POM/SQL/tool regression |
| `REQ-920` | §4/§18 | 1-3 | Spec/Plan/evidence | wording/static final audit | fixed slice only; T3-09 remainder Required |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `GATE-901` | Review Spec/Plan are not user-Accepted and source GO is absent | all source/evidence paths | metadata/Root instruction | Root/user | Closed for Plan drafting; blocking only execution. Root must explicitly accept and authorize Step1 before repository writes/Maven |
| `RISK-901` | Real provider emits absent/empty/unsafe or changed call IDs despite pinned SDK ordering | Step2 Gate/plugin/Runner | pinned bytecode plus fake BaseLlm | Root/runtime owner | SDK order closed; Gate tests every ID class; real provider remains separate live gate |
| `RISK-902` | A later unrelated commit changes a planned native path after the clean Worker Error baseline | AR/AS drift preflight | Worker Error Step4 already committed at `b0cf55c8d4696fac5bea30875c24f169503957a5`; native digest unchanged | Root | Recompute exact surface digest immediately before Step1; material path drift returns to Spec/Plan |
| `RISK-903` | Authorization event can commit before native action fails | Gate/event evidence | Spec EVENT-901 | Operator | Closed by AUTHORIZED semantics; correlate terminal result, never call completed |
| `RISK-904` | New current-role check exposes stale/mid-child Session state | Gate/plugin context | Root clarification/REQ-912 | Runtime owner | Intentional fail-closed; no resume. Confirmed child-to-root same-session continuation expects root and remains valid |
| `RISK-905` | Base YAML empty metadata could be mistaken as valid off-state binding | Properties/config/YAML | conditional config design | Step3 implementer | Config not instantiated while off; enabling requires all strict values; tests/JAR verify |
| `RISK-906` | Fixed prompts/codeRefs could be changed without T4 signed-artifact governance | factories | Spec non-goal | Definition/release owner | Semantic code change requires new codeRef/definition; T4 remains future work |
| `RISK-907` | Finite offline build may have stale/missing cache or skipped tests | all validation | source-reactor/settings | Root | Classify environment failures separately; final clean verify + report existence; no network/global-settings mutation |
| `RISK-908` | Full dynamic task decomposition could be overclaimed | evidence/rollout | REQ-920 | Root | Every evidence/final note says fixed first slice only; T3-09 remains incomplete |
| `DEC-901` | Exactly three sequential semantic commits and no shared-file overlap | all | Root instruction/§4.3 | Root | Closed; one commit per Step, exact paths only |

There is no unresolved product/architecture decision inside this Plan. `GATE-901` is an execution authorization gate, not permission to downgrade the complete Plan or write source. Any material SDK/repository drift converts it into a Spec blocker before implementation.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

The Plan covers the reviewed first T3-09 fixed conditional/supervisor slice in three Steps; preserves the Function map, one Runner/AgentRun, exact native Method, current MODEL/session/lease/revision/deadline/cancel authority, authorization audit, absent/exact-empty/safe-present provider ID to actual-ID ordering, strict callback replay, bounded child text, typed sanitized fatal disposition, genuine root output, exact-snapshot budgets and default-off host model. It excludes every forbidden C/CL/API/POM/SQL/Components/frontend/provider/listener/browser/runtime/resume/Graph/DSL/AgentTool scope and keeps full task decomposition Required.

### 12.2 Spec consistency

Every target source/test/config path matches reconciled Spec §8. The only documentation/evidence additions are required Plan workflow artifacts. `PLAN-CLAR-901` keeps policy-dependent hashing in `TemplateSpec`; `PLAN-CLAR-902` nests the model binding; `PLAN-CLAR-903` places the Root-authorized current-role check before budget spend; `PLAN-CLAR-904`/`905` pin actual SDK ordering and fail-fast error semantics; `PLAN-CLAR-906` makes the already-required EVENT-901 codec regression explicit; `PLAN-CLAR-907`/`908` correct the pinned model-getter and call-ID mechanics; `PLAN-CLAR-909` requires exact snapshot object identity; `PLAN-CLAR-910` preserves existing terminal disposition across callback/control races. None changes public behavior beyond the reviewed Spec. The simplicity audit found no fetch-then-forward API, client-derived authority, duplicate registry/store/result type or speculative layer.

### 12.3 Repository executability

The Plan uses clean `b0cf55c8d4696fac5bea30875c24f169503957a5` with the native capability digest unchanged from the original `c731f3f` inspection, current Plugin/Runner/FunctionTool/ToolContext/EventActions APIs, the exact `LlmAgent.model()->Model.model()->BaseLlm` getter chain, existing Agent Flow Beans/adapter, one base YAML, source-reactor command and exact artifact names. Every file has one owning Step and exact consumers/mapping/errors/verification/after-state. Root recomputes the surface digest before Step1 and preserves unrelated work; material drift stops execution.

### 12.4 Test and release completeness

Each new component has reflection/resource assertion RED, minimal compile A, typed behavior RED, minimum B and focused GREEN. Step2 adds real ADK Runner/Fake BaseLlm proof; Step3 adds clean source-reactor verify and actual AR/AS JAR inspection. All runtime/provider/IAM/PostgreSQL/deployment acceptance is explicitly unexecuted. There is no migration, backfill, permission publication, browser or service-start gate in scope.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | Spec §6.1/§8; Plan §4.7/§5/Steps1-3 | existing Root-approved AR technical vertical and AS host package preserved; no third layer/module | None |
| `MC-REUSE-001` | Applicable | PASS | §4.5/§4.7 ledger; exact Function map, adapter, plugin, ledger, estimator, Runner | existing capabilities reused; only proven Gate/metadata gap filled | None |
| `MC-DEP-001` | Applicable | PASS | POM/path audit; every Step static gate | no dependency/POM/plugin/tool download introduced | None |
| `MC-NAME-001` | Applicable | PASS | §5 tree and every Java file Symbols | semantic Strategy/Gate/Factory/Configuration/Properties/Test names; no ambiguous carrier | None |
| `MC-VALID-001` | Applicable | PASS | Steps1-3 callback/snapshot/config handoff matrices; REQ-912 clarification | exact pre-mutation and pre-budget validation, negative tests and safe errors | None |
| `MC-MODEL-001` | Applicable | PASS | Step1 nested immutable specs/binding; Step2 private facts; Step3 Properties | immutable records/enums with copies/identity; no ordinary entity/POJO | None |
| `MC-CONVERT-001` | Not applicable | N/A | Spec §10.4; Plan §§4.7/7 | no cross-layer DTO/domain/persistence conversion or new Converter | None |
| `MC-LOG-001` | Not applicable | N/A | target classes are technical; existing executor/plugin logger owners unchanged | no new concrete business Service; no sensitive text/args logged | None |
| `MC-BEAN-001` | Applicable | PASS | Step3 File7; existing qualified Agent Flow/runtime Beans | stable `tianjiAgentCodeFirstFactories`; qualified method parameters; no field/manual constructor injection | None under approved technical configuration pattern |
| `MC-UTIL-001` | Applicable | PASS | all pseudocode/import/static gates | JDK/existing ADK/RxJava/Spring only; no helper/dependency library | None |
| `MC-JSON-001` | Applicable | PASS | Step2 Files1/7/8; EVENT-901; AgentJsonCodec unknown type | existing Jackson envelope/string type; exact safe payload; no Gson/Fastjson/C/CL drift | None |
| `MC-TIME-001` | Applicable | PASS | TIME-901; Step2 plugin/executor tests | existing UTC Instant/Duration/DB clock and deadline/lease fences preserved; no DST/local time | None; live DB precision unverified is explicit |
| `MC-CONFIG-001` | Applicable | PASS | Step3 Files1/2/7/8; sole `application.yml` | complete four-key default-off structure; no profile drift/credential fallback | None |
| `MC-PATTERN-001` | Applicable | PASS | Strategy + Gate State/Policy + two-phase confirmation in §§4/7 | Complex variation has concrete participants; no hard-coded executor branch sprawl/DSL | None |
| `MC-SCOPE-001` | Applicable | PASS | §5 exact tree; Step static gates; §9 | only AR/AS/test/config/docs/evidence paths; forbidden surfaces absent | None |
| `MC-TEST-001` | Applicable | PASS | §7 RED/A/RED/B/GREEN; §8 finite commands/JAR/static gates | every applicable standard/requirement has exact future proof; no runtime claim | None |
| `MC-BLOCKER-001` | Applicable | PASS | §11; every row above; Spec has no open major decision | Plan is internally complete; source execution remains separately gated by Root acceptance/GO | None for Plan review; do not execute without GO |

### 12.6 Final verdict

`PASS — Ready for user review`

This verdict means the Draft Plan is internally complete and strictly reviewable. It does not mark the Review Spec Accepted, does not authorize implementation, and does not claim runtime/production acceptance.

## Java / CQE contract review

- POJO/value/enum: only immutable internal/config values and private state enums are introduced; ordinary persistence/business POJOs are absent.
- Component reuse: existing Agent Flow adapter/Function map/Runner, Tianji plugin/ledger/model estimator/result codec are reused; no MP/DDL/component dependency applies.
- Validation: actual third-party callback, snapshot, tree/model and host-config boundaries validate before mutation; current expected Agent validates before MODEL authorization/budget reservation.
- Business uniqueness/DDL: N/A; no persistence model, SQL or business unique key changes.
- CQE: existing start/result Commands/Queries remain. EVENT-901 is an occurred authorization fact in the existing durable ledger/event channel, not a local listener or completed-transfer claim.
- Exact architecture: the current Root-approved Tianji technical vertical remains; no generic traditional/Archetype migration or hybrid layer is introduced.

## Backend generation and dependency evidence

N/A. No new module, CRUD catalog, DDL/manifest, generated source, dependency or generator action exists. All target files are deliberate AR technical runtime, AS host configuration/factory, tests or governance/evidence documents. No classpath SQL journal entry is required.
