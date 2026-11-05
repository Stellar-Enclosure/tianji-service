# Bounded research task runtime — Step 52 / bounded Plan Step 1

- Baseline: `237e1d25d860adbaf6752d1cdc5b4aac8bedae65`; 924 tracked files and 25 immutable main-classpath SQL files.
- Effective Spec: [Bounded research task decomposition](../spec/2026-11-05-08-47-bounded-research-task-decomposition.md), SHA-256 `2eb396ab89c0dbdd3dad18d4d586b92dadc76c632a9e7974a732b2ec706936bb`.
- Effective Plan: [Bounded task implementation](../plan/2026-11-05-09-44-bounded-research-task-decomposition.md), SHA-256 `57c1ae5a270579a2d779c2e131e3bda80009a86714bb0b4630c459ed43a83fc8`.
- Authorization: user master-plan execution request; Root reviewed the full effective Spec/Plan, independently validated their exact linked mirrors and locked this Step's 15 paths. Review/Draft status does not claim user acceptance.

Source52 is complete at source level. The new bounded compiler/Gate is implemented; host registration remains the separately committed Step53. Live acceptance is unexecuted.

## Actual finite verification

All commands use JDK 25, `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, `./scripts/verify-source-reactor.sh`, the existing credential-free `/tmp/tianji-compat-fix/settings.xml` for both `-gs` and `-s`, offline `-o -B -ntp`, `-DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false`, and disabled OTel traces/metrics/log exporters. Explicit finite selectors exclude PostgreSQL/HTTP-listener/provider tests. No project/server/database/container/browser is started.

| Gate | Actual result | Log |
| --- | --- | --- |
| G1 parser presence RED | 1 assertion failure, 0 errors/skips; 21.059 s | `/tmp/tianji-bounded-step52-parser-presence-red.log` |
| Parser A compile | 1 pass; 33.631 s | `/tmp/tianji-bounded-step52-parser-compile-a.log` |
| G2 parser typed RED | 12 tests / 8 expected assertion failures / 0 errors/skips; 22.963 s | `/tmp/tianji-bounded-step52-parser-typed-red.log` |
| G3 parser GREEN | 12 passed, 0 failures/errors/skips; 41.925 s, 10:31:20 +08:00 | `/tmp/tianji-bounded-step52-parser-green.log` |
| G4 compiler enum presence RED | 9 tests / 1 assertion failure / 0 errors/skips; 25.247 s | `/tmp/tianji-bounded-step52-compiler-presence-red.log` |
| Paired enum/Gate A compile | Compiled; 21 tests / 1 old exact-two enum-list assertion failure / 0 errors/skips. The expected enum list was extended to three in the typed tests; this run is not bounded behavior RED | `/tmp/tianji-bounded-step52-paired-compile-a.log` |
| G5 fixture correction | Initial overloaded method references caused test compilation failure; two references were changed to explicit no-argument lambdas. Compilation failure is not counted as RED | `/tmp/tianji-bounded-step52-compiler-typed-red.log` |
| G5 compiler typed RED | 26 tests / 3 bounded-positive assertion failures / 0 errors/skips; 23.240 s | `/tmp/tianji-bounded-step52-compiler-typed-red-fixed.log` |
| G6 compiler/parser GREEN | 26 passed, 0 failures/errors/skips; 26.725 s, 10:46:43 +08:00 | `/tmp/tianji-bounded-step52-compiler-green.log` |
| G7 Gate RED | 22 tests / 5 expected assertion failures / 0 errors/skips; 21.167 s, 10:51:23 +08:00; old 17 pass | `/tmp/tianji-bounded-step52-gate-red.log` |
| G8 Gate GREEN | 22 passed, 0 failures/errors/skips; 26.585 s, 10:55:29 +08:00 | `/tmp/tianji-bounded-step52-gate-green.log` |
| G9 Plugin/Runner RED | 56 tests / 5 expected assertion failures / 0 errors/skips; 23.736 s, 10:57:30 +08:00 | `/tmp/tianji-bounded-step52-plugin-red.log` |
| G10 Plugin/Runner GREEN | 56 passed, 0 failures/errors/skips; 28.328 s, 10:58:56 +08:00 | `/tmp/tianji-bounded-step52-plugin-green.log` |
| G11 Executor RED | 20 tests / 2 expected assertion failures / 0 errors/skips; 19.052 s; only missing tasks and new event type fail, codec 8 and cancellation control pass | `/tmp/tianji-bounded-step52-executor-red.log` |
| G12 full core GREEN | 181 passed across 15 explicitly selected classes (AR 172 / AS 9), 0 failures/errors/skips; 39.775 s, 2026-11-05 11:01:02 +08:00 | `/tmp/tianji-bounded-step52-core-green.log` |

Independent Gate review found no blocking implementation defect. A separate fresh-rig test covers old actual InvocationContext plus fresh request Builder after task-generation advancement, independently of stale-Builder rejection; duplicate confirmed callbacks retain their original window. Completion/codec draft review also corrected cancellation fixtures to exercise RUNNING plus cancelRequested through the existing finishCancellation resolver.

The strict parser validates all constructors, freezes 1..8 ordered tasks, preserves question strings and hashes semantic framed fields. The existing Compiler Strategy admits only the new bounded 1/2 tuple alongside unchanged legacy 1/1 tuples and golden hashes. The cohesive per-invocation State policy binds exact context generations, injects one untrusted USER question before the existing snapshot/estimate path, and requires current response plus confirmed native action. Complete known usage is settled before semantic failure; partial semantic envelopes are checked before idle touch. Root final text requires a current one-shot candidate consumed by the genuine final event. No Session/outputKey/history value becomes task authority.

The Executor selects the bounded-only audit type and projects ordered task records to immutable JSON-native exact-three-key maps before constructing the existing DTO. Existing agents/text output remains; repeated-role agents keep the last summary while tasks preserve each task independently. The generic production codec, Contract/Client APIs, SQL, POMs and configuration remain unchanged. Independent final review found no blocking Executor/Completion/codec issue, including RUNNING plus cancelRequested resolution and no completed finish after failed current CAS.

Final selector: `NativeTaskPlanVOTest,NativeAgentCompilerStrategyTest,NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,TianjiAgentRuntimeAutoConfigurationTest,TianjiNativeAgentTemplateConfigurationTest`.

Exact 15 paths were reviewed. All 914 excluded tracked paths and all 25 old SQL files remain baseline-identical by SHA-256. Strict effective Spec/Plan validation and whitespace checks passed before the focused commit. This Step does not claim a clean package/JAR rebuild; that artifact gate belongs to Step53.

## Requirement conformance

| Requirement | Current-Step source evidence |
| --- | --- |
| REQ-931 | Exact bounded compiler tuple/schema; old golden values and Function ABI preserved. |
| REQ-932 | First complete root response contains the strict plan and exactly one transfer to task1.agent before freezing. |
| REQ-933 | Closed schema-one plan with 1..8 ordered unique IDs and fixed pool; duplicate/unknown/null/type/coercion/trailing input rejected. |
| REQ-934 | Exact ASCII ID, Unicode/control and raw/decoded size bounds; original question preserved without trim or normalization. |
| REQ-935 | Frozen plan, zero-text next-task routing and no replan/skip/reorder; final JSON answer is not parsed as another plan. |
| REQ-936 | CAS-confirmed NEW/DISPATCHED and native-action-confirmed RETURNED; exact duplicate confirmation no-op, changed replay fatal. |
| REQ-937 | Actual-context ordinal/generation isolation, stale context and builder rejection, independent repeated-role summaries. |
| REQ-938 | USER task question enters after history validation and before snapshot/estimate; retries/trim keep it once. |
| REQ-939 | Same run/Runner, shared lease and budget; eight tasks use 17 model reservations and no ordinary tool reservation. |
| REQ-940 | One-shot final candidate; ordered immutable tasks, repeated-role last summary and JSON-native DTO projection. |
| REQ-941 | Bounded-only audit type and exact six safe keys; raw question/task ID excluded. |
| REQ-942 | Plan semantic framed hash binds exact ordered validated values without normalization. |
| REQ-945 | Old two templates, generic codec, result/event envelopes and schema-one behavior regressions pass. |
| REQ-946 | Sticky safe failure signals and current revision/cancel/lease/deadline resolution remain in force. |
| REQ-947 | Only finite scripted Runner/context/JDBC mocks; no app/PG/provider/browser start or resume/production acceptance claim. |

REQ-943/REQ-944 host flag/factory registration remain Step53. The core is usable only through an explicitly supplied reviewed compiler; no third host factory is registered in this Step.

## Blocking Manual Checks

| Check | Applicability | Status | Evidence and finding |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing AR technical vertical, one AgentRun/Runner and existing Compiler/Gate/Plugin/Executor seams; no additional orchestration layer. |
| MC-REUSE-001 | Applicable | PASS | Pinned ADK native transfer, current run-event CAS, budget/snapshot/context/failure paths and generic codec reused. |
| MC-DEP-001 | Applicable | PASS | POMs unchanged; only existing JDK/Jackson/ADK/Spring types. |
| MC-NAME-001 | Applicable | PASS | Semantic NativeTaskPlanVO/TaskVO/TaskResultVO and private task-window/state names; no business entity. |
| MC-VALID-001 | Applicable | PASS | Strict constructor/parser, semantic envelope, exact tuple/lineage/window/args/action/final guards; positive and negative tests pass. |
| MC-MODEL-001 | Applicable | PASS | Immutable defensive task values/maps/lists; synchronized per-invocation state and redacted value strings. |
| MC-CONVERT-001 | Not applicable | N/A | Same-package technical record-to-JSON map projection; no cross-layer Converter or entity mapping introduced. |
| MC-LOG-001 | Not applicable | N/A | No new business logger; boundary failures/toString omit private payload/cause; existing privacy regressions pass. |
| MC-BEAN-001 | Not applicable | N/A | No new Spring bean or DI surface; existing technical constructor ABI retained. |
| MC-UTIL-001 | Applicable | PASS | Existing semantic fingerprint helper, JDK collections/UTF-8 and existing Jackson parser reused. |
| MC-JSON-001 | Applicable | PASS | Strict closed input, exact-six audit and exact-three task maps; existing generic codec roundtrip and raw-record rejection pass. |
| MC-TIME-001 | Applicable | PASS | Existing java.time/current deadline, lease and cancel resolvers preserved; finite callback timeout/current cancellation tests pass, live DB time unexecuted. |
| MC-CONFIG-001 | Not applicable | N/A | All AS configuration/profile paths remain unchanged; old exact-two map verified by nine configuration tests. |
| MC-PATTERN-001 | Applicable | PASS | Existing Compiler Strategy plus cohesive bounded State policy and two-phase response/action confirmation; no extra registry/pipeline/DSL. |
| MC-SCOPE-001 | Applicable | PASS | Exact 15 paths; 914 excluded tracked SHA values and 25 immutable SQL hashes unchanged; no generated files included. |
| MC-TEST-001 | Applicable | PASS | Real assertion RED gates followed by finite 181-test GREEN; compilation fixture correction is disclosed separately. |
| MC-BLOCKER-001 | Applicable | PASS | Current-Step source and validation complete; host/artifact and user-owned runtime acceptance remain explicitly separate pending work. |

## Literal Rule Gate

| Rule | Applicability | Status | Independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic technical value/state/strategy names and explicit role ownership. |
| Rule 2 | Applicable | PASS | Validation at parser/SDK/model/tool/action/event/DTO boundaries with fail-closed tests. |
| Rule 3 | Applicable | PASS | Defensive immutable values and explicit same-package JSON projection; no entity conversion or Lombok conflict. |
| Rule 4 | Applicable | PASS | Existing technical explicit constructors and class-only safe logger conventions retained; no new managed business class or field injection. |
| Rule 5 | Applicable | PASS | JDK and existing approved Jackson/ADK/framework utilities; no new dependency. |
| Rule 6 | Applicable | PASS | Existing Jackson generic codec and JSON-native Map/List contract; string audit type, no ordinal persistence. |
| Rule 7 | Not applicable | N/A | All environment/profile configuration hashes unchanged; next host Step owns the new false flag. |
| Rule 9 | Applicable | PASS | Approved existing Strategy plus bounded State policy isolates task-generation variation without extra public abstractions. |
| Rule 10 | Applicable | PASS | Existing java.time deadlines/leases and timeout routing preserved; no Date/Calendar values added. |
| Rule 11 | Applicable | PASS | Current technical plain-JDBC module/profile and Components reuse retained; no new persistence, DDL, business Event/CRUD, generator or alternate SDK. |

Source52Complete=true; ASBoundedFactoryPresent=false; DefaultFactoryMapUnchanged=true; RuntimeUnverified=true; ProductionAccepted=false. Finite scripted Runner/ledger mocks do not prove provider, PostgreSQL, IAM, browser, deployment, restart durability or production behavior. Required work continues with Step53 and the remaining master-plan roadmap.
