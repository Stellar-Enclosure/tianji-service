# Default-off bounded research template — Step 53 / bounded Plan Step 2

- Baseline: `f6ff52088a7f7c7f6829cd495b9a3f5c3689dfae`; Step52 committed and verified clean before AS changes; 929 tracked files, 25 immutable SQL files.
- Effective Spec: [Bounded research task decomposition](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md), SHA-256 `2eb396ab89c0dbdd3dad18d4d586b92dadc76c632a9e7974a732b2ec706936bb`.
- Effective Plan: [Bounded task implementation](../plan/2026-11-05-09-44-bounded-research-task-decomposition.md), SHA-256 `57c1ae5a270579a2d779c2e131e3bda80009a86714bb0b4630c459ed43a83fc8`.
- Authorization: user master-plan execution request; Root full effective Spec/Plan and phased source/test review, independent strict validation, exact seven-path lock. Review status does not claim user acceptance.

The host adds one BoundedResearchAgentFactory to the existing Compiler Strategy Map. Native templates remain disabled by default. With native templates enabled, missing/false decomposition retains exactly the existing two factories; both flags true register exactly three. The same named immutable Map, qualified model and adapter are reused. No new model client, ordinary tools, Runner or runtime activation is created.

Properties append the fifth boolean with explicit canonical ConstructorBinding and preserve the public four-argument constructor delegating false. The class-level condition prevents even property binding while native templates are disabled. Each factory apply creates a fresh three-node Agent tree sharing one explicit BaseLlm; only the fixed roles, output keys and transfer flags are admitted. Published instructions define strict first-plan/frozen routing/current untrusted USER question and same-response child summary/parent transfer.

## Actual finite verification

All Maven commands use JDK25, `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, `./scripts/verify-source-reactor.sh`, offline `-o -B -ntp`, `-DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false`, and disabled OTel traces/metrics/log exporters. The clean package gate uses `STELLAR_VERIFY_GOAL=verify` and `clean`. ContextRunner tests create finite in-memory contexts without a server; scripted Runner tests use fake models. PostgreSQL/listener/provider tests are not selected.

The pre-pause assertion gates below were actually observed in this task. Their transient `/tmp` logs did not survive the pause. The final gate was rerun using credential-free persistent task settings with the same local Maven repository; current full logs, XML copies and artifact proof are retained under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/`. No historical missing log is represented as still available.

| Gate | Actual observed result |
| --- | --- |
| G14 presence/resource RED | 10 tests, 2 assertion failures, 0 errors/skips; 21.600 s; missing fifth constructor and YAML false key. |
| Properties A compile | Compiled; 10 tests, 1 expected YAML failure, 0 errors/skips; 21.171 s. |
| G15 typed host RED | 16 tests, 2 expected assertion failures, 0 errors/skips; 22.152 s; exact-three Map and resource key still absent. |
| G16 factory presence RED | 1 expected missing-type assertion failure, 0 errors/skips; 17.322 s. |
| Factory A compile | 1 passed, 0 failures/errors/skips; 24.812 s. |
| G16B typed factory RED | 7 tests, 6 expected assertion failures, 0 errors/skips; 20.339 s. |
| Factory B GREEN | 7 passed, 0 failures/errors/skips; 21.020 s. |
| Initial host GREEN | Five host classes / 39 passed; 0 failures/errors/skips; 22.755 s. |
| Exact G17 host GREEN | Six Plan-specified classes / 53 passed, 0 failures/errors/skips; 22.585 s, 2026-11-05 11:54:10 +08:00. Includes Compiler; persistent `step53-host-exact-green.log`. |
| G18 final clean verify | 20 classes / 231 passed (Components20, AR172, AS39), 0 failures/errors/skips; 01:09 min, 2026-11-05 11:55:36 +08:00. Persistent `step53-clean-verify.log`. |

The initial five-class host command omitted Compiler from the standalone G17 selector, although Compiler passed in the initial 20-class clean verification. Root corrected the exact selector, reran all six classes, then reran G18 in order without changing source. Final results below use the retained post-resume logs and artifacts.

Final selector: `NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,TianjiAgentRuntimeAutoConfigurationTest,ConditionalResearchAgentFactoryTest,SupervisorResearchAgentFactoryTest,TianjiNativeAgentTemplateConfigurationTest,TianjiAgentServerApplicationTest,AgentFlowAdapterRegressionTest,NativeTaskPlanVOTest,BoundedResearchAgentFactoryTest`.

## Actual artifact and scope proof

- Fresh AR JAR SHA-256: `3cb93115f856912667bb69754973a45fb4e96d5aef3b5e8166e2bfd1eabcc4d1`; five required main classes plus bounded value/policy/result classes checked against current target class bytes.
- Fresh AS JAR SHA-256: `8224e005b579b47829a373b0ccb23b8493c4f9def70933e17c72fe3a8008b3ed`; all five host template classes checked against current target class bytes.
- Packaged `application.yml` SHA-256: `8255f5b57e454ef19c52f77e5ce7faa157a509e2818fb2f3802f75d59a4c2e32`; byte-identical to source; native templates and decomposition both false.
- Actual JAR javap confirms both public four-argument and five-argument constructors; binding annotation and actual constructor choice are also covered by reflection and ContextRunner tests.
- Current 20 XML reports (231 tests), actual JAR copies, javap and SHA proof are retained in the persistent task directory (`step53-final-proof.json`, `step53-reports-final/`, `step53-artifacts/`, `step53-properties-javap.txt`). No build output is committed.

Independent source review found no blocking defect in the constructor/binding, default-off matrix, qualified immutable consumer, shared explicit model or absence of provider calls. Strict Spec/Plan validators and whitespace checks pass. The reviewed scope comprises exactly seven paths; 925 excluded tracked files and all 25 SQL files remain baseline-identical. Across Steps52/53 the bounded slice spans 22 unique paths. No POM, API, codec production, SQL, permission, provider credentials or frontend file changes.

## Requirement conformance

| Requirement | Source evidence |
| --- | --- |
| REQ-931 | New fixed compiler tuple remains the Step52 implementation; final compiler regression passes. |
| REQ-939 | Host contributes only an existing-ABI factory; original run/Runner/policy/budget/session/cancel paths remain. |
| REQ-943 | One additional factory uses the same qualified model/adapter, fresh exact-three trees and no ordinary tools/provider calls. |
| REQ-944 | Fifth boolean defaults false, old four-argument descriptor preserved, real Binder and exact-two/three/off-context tests pass. |
| REQ-945 | Existing templates, schema-one, generic codec and Function Map ABI remain covered by final regression. |
| REQ-946 | Existing bounded sticky safe-failure/current-context tests remain in the final finite regression. |
| REQ-947 | Finite tests and actual package proof only; no app/PG/provider/browser start or resume/production claim. |

REQ-932–938/940–942 source evidence remains in [Step52 runtime evidence](2026-11-05-bounded-task-runtime.md); all owning suites are rerun in G18. Against master T3-09, the source now supplies conditional routing, Supervisor and bounded ordered task decomposition through the same Code-First/API path. Fixed depth2, task count1..8 and transfer cap16 prevent expansion; dynamic tools/Agent creation/DSL/Graph remain forbidden. S03 real-provider/IAM/PG/deployment acceptance is unexecuted, so neither T3-09 runtime acceptance nor the overall master goal is marked complete.

## Blocking Manual Checks

| Check | Applicability | Status | Evidence and finding |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing AS technical configuration and Compiler Strategy; no new layer or execution path. |
| MC-REUSE-001 | Applicable | PASS | Existing named Function Map, qualified ChatModel/SpringAiModelAdapterFactory, tuple and runtime reused. |
| MC-DEP-001 | Applicable | PASS | POMs and dependencies unchanged; JDK/existing ADK/Spring AI/Boot types only. |
| MC-NAME-001 | Applicable | PASS | BoundedResearchAgentFactory and semantic Properties/Configuration/test names follow adjacent factories. |
| MC-VALID-001 | Applicable | PASS | Strict properties/model/callback guards, exact flags/tree/identity tests and real Binder matrix. |
| MC-MODEL-001 | Applicable | PASS | Immutable configuration record, final factory fields, immutable metadata/Map and fresh trees. |
| MC-CONVERT-001 | Not applicable | N/A | No cross-layer conversion or entity mapping; only host configuration values. |
| MC-LOG-001 | Not applicable | N/A | No new business logger; fixed safe configuration errors, no model payload logging. |
| MC-BEAN-001 | Applicable | PASS | Explicit existing Bean/Qualifier method injection; annotated five-argument binding and retained four-argument constructor. |
| MC-UTIL-001 | Applicable | PASS | Existing adapter and framework/JDK utilities; no new utility dependency. |
| MC-JSON-001 | Applicable | PASS | Fixed metadata/schema tuple and original JSON result/event/codec regressions pass. |
| MC-TIME-001 | Not applicable | N/A | No time symbol or deadline implementation changed; original finite boundary regressions retained. |
| MC-CONFIG-001 | Applicable | PASS | Sole AS application.yml adds only decomposition-enabled:false; packaged bytes equal source and both flags false. |
| MC-PATTERN-001 | Applicable | PASS | Existing Strategy plus one concrete factory and simple configuration branch; no second registry. |
| MC-SCOPE-001 | Applicable | PASS | Exact seven paths, 925 excluded tracked values and all 25 old SQL hashes unchanged. |
| MC-TEST-001 | Applicable | PASS | Observed assertion RED/A/B gates, exact G17, final 20-class clean verification and actual Jar checks; missing transient logs disclosed. |
| MC-BLOCKER-001 | Applicable | PASS | Current Step checks closed; live acceptance and remaining master roadmap explicitly pending. |

## Literal Rule Gate

| Rule | Applicability | Status | Independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic factory/configuration/immutable metadata roles and existing package conventions. |
| Rule 2 | Applicable | PASS | Strict host boundaries, disabled nonbinding, invalid flags/options/callbacks and model identity tests. |
| Rule 3 | Applicable | PASS | Immutable configuration record/final values, fresh trees and no entity conversion. |
| Rule 4 | Applicable | PASS | Existing explicit Bean/Qualifier configuration; correct ConstructorBinding canonical and unannotated legacy constructor; no business DI/logger change. |
| Rule 5 | Applicable | PASS | JDK and existing approved framework/adapter APIs; no dependency change. |
| Rule 6 | Applicable | PASS | Fixed string metadata/schema and original Jackson JSON contract; no ordinal persistence. |
| Rule 7 | Applicable | PASS | Single AS profile gains one false key; actual packaged YAML matches source and preserves all previous defaults. |
| Rule 9 | Applicable | PASS | Existing Compiler Strategy concrete factory addresses the known template variation without parallel registry/executor. |
| Rule 10 | Not applicable | N/A | No new date/time model or behavior; existing java.time regressions remain. |
| Rule 11 | Applicable | PASS | Current technical module/profile and Components reuse retained; no persistence/DDL/business Event/CRUD/generator introduced. |

SourceBoundedSliceComplete=true; NativeTemplatesEnabledByDefault=false; DecompositionEnabledByDefault=false; RuntimeUnverified=true; ProductionAccepted=false. User-owned runtime testing is unexecuted. Required master-plan work continues with compensation, recovery and later T3/T4 items.
