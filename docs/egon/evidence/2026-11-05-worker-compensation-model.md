# Bounded Worker compensation model — Source54 / compensation Plan Step1

- Baseline: `b63fc8a8b1e20733127bc1516d8799ca262997ee`, clean; 932 tracked files; 25 immutable SQL files.
- Effective [Spec](../spec/2026-11-05-12-09-worker-compensation-fixture.md) SHA `de12e22b3aa322da4f0d7128aa3376b4d6c7aa76800502a7ee9c414556b58f0d` and [Plan](../plan/2026-11-05-12-44-worker-compensation-fixture.md) SHA `039d43b2806da6c45e084e1459d1d664c5d1300d37d0d7f3d8416ba51fd0458d`; both originally strict PASS before source GO; updated exact identities include the controlled Step2-only correction below and pass strict again. User master goal and Root full design/Plan review authorized exact7 paths. User acceptance not claimed.
- Current six relevant PS/W source files match baseline source evidence and business-error commit `b0cf55c8d4696fac5bea30875c24f169503957a5` byte-for-byte. The fresh per-file SHA/byte comparison is authoritative; the reconstructed aggregate digest is recorded separately and is not used to infer source drift.

The model step is complete at source level after the checks below. Business receipts and the optional source-only scenario belong to the separately committed Step2.

| Gate | Actual result | Persistent log |
| --- | --- | --- |
| G1 reflection/resource RED | 1 test, 1 expected assertion failure, 0 errors/skips; 19.050 s, 2026-11-05 12:53:52 +08:00 | `step54-g1-presence-red.log` |
| G2 model A / pure converter | 2 passed, 0 failures/errors/skips; 19.135 s, 12:55:52 +08:00. Exact XML validated by public InputStreamProvider converter and actual model getters; no engine. | `step54-g2-converter-a.log` |
| G3 typed RED | 12 tests, 4 expected new positive assertion failures, 0 errors/skips; 19.372 s, 13:03:39 +08:00. Existing metadata/converter/legacy/negative controls pass. | `step54-g3-typed-red.log` |
| G4 model GREEN | 132 passed in seven exact suites, 0 failures/errors/skips; 21.886 s, 13:04:52 +08:00. | `step54-g4-model-green.log` |

Logs are stored under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/compensation/`. Commands use JDK25, source-reactor script, persistent credential-free settings, offline `-o -B -ntp`, explicit selectors, `-DskipITs=true`, no OTel exporters and ADK message capture disabled. No app, engine, database, provider, listener or browser is started.

Fixed Flowable 8.0.0 local bytecode independently shows normal Worker leave creates the compensation subscription; completion/error commands first accept later executable work. The throw behavior does not consume the wait flag and may leave without a subscription. XML `waitForCompletion=true` remains exact grammar, not proof of native waiting, liveness or a committed undo receipt.

The existing DOM and topological pass now admit one complete fixed ordinary Worker pair and retain the handler in workerTasks. Only the two fully validated detached compensation nodes are removed from normal traversal. Every actual incoming edge participates in the AND proof; only the original's unique explicit normal outgoing edge can establish success. The valid downstream Error and post-original Timer/merge controls pass. Original self-error and the same-shape pre-original Timer bypass reject; corresponding legacy graph projections pass ordinary shape validation. Existing Error/Timer edges, decisionBefore/waitBefore, capacities and visited proof are preserved.

The added immutable six-field value/map is structural metadata; existing constructor descriptors, XML defensive copies and empty old-model maps remain. Existing ordinary effect enum and registry contracts remain; the new XML example uses transactional receipts without forcing that effect mode onto all production models. No new API, POM, SQL, runtime defaults, permission, Worker main class or model deployment is included.

Before this commit, independent fixture review found the future PG role cannot perform row-lock SELECT with only SELECT/INSERT. Primary DEC-711 now specifies the minimum original-receipt recorded_at column grant plus an always-enabled rejecting UPDATE trigger in the owned test schema and a future exact-role probe. The official PostgreSQL SELECT and CREATE TRIGGER rules were verified; no SQL/DB was run. The correction changes only Step2 permission/setup/probe design; the Step1 Java/XML/test bytes and all 132 results are unchanged.

Exact G4 selector: `WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest`. Current Surefire XML copies are retained in `compensation/step54-reports/`. Strict documentation, exact scope and current-Step closure passed before staging. The evidence does not claim business receipts, real PostgreSQL/engine behavior, runtime durability, S10/S11 or the whole T3-10 goal complete.

## Requirement conformance

| Requirement | Current-Step evidence |
| --- | --- |
| REQ-701 | One complete pair and immutable exact six-ID map; old metadata empty. |
| REQ-702 | Explicit noninterrupting compensation boundary, ordinary original and no normal boundary edges. |
| REQ-703 | Detached marked ordinary handler, exact One association and registry-visible workerTasks. |
| REQ-704 | Explicit original activityRef and wait flag, only one in/out and dedicated end. |
| REQ-705 | All-edge AND proof, original self-error and Timer bypass rejected with valid-shape controls. |
| REQ-706 | Exactly initial operationId->operationId STRING and no outputs on pair; binding tests. |
| REQ-707 | Old constructors/XML/Worker/Error/Timer/Human/DMN/Case contracts and graph limits preserved. |
| REQ-716 | Actual converter metadata plus pinned bytecode boundaries; no liveness or business undo inference. |
| REQ-717 | All 25 old SQL unchanged; no production migration or DDL here. |
| REQ-718 | Finite scoped tests/compile only; no PG opt-ins, engine, listener, provider or browser. |

## Blocking Manual Checks

| Check | Applicability | Status | Actual evidence |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing two PS release classes and same technical vertical; no extra execution layer. |
| MC-REUSE-001 | Applicable | PASS | SafeModelXml, existing topology/Worker binding/metadata/Flowable model converter reused. |
| MC-DEP-001 | Applicable | PASS | All POMs unchanged; existing JDK/DOM/Flowable/JUnit only. |
| MC-NAME-001 | Applicable | PASS | Semantic WorkerCompensationVO/private IncomingEdge and focused model test. |
| MC-VALID-001 | Applicable | PASS | Exact closed syntax, complete pair, arities/affinity and actual normal-success edge proof; valid controls and adversarial variants. |
| MC-MODEL-001 | Applicable | PASS | Immutable map and XML copy; seven legacy descriptors and pure value metadata. |
| MC-CONVERT-001 | Not applicable | N/A | No cross-layer DTO conversion or production mapper; pure test converter only. |
| MC-LOG-001 | Not applicable | N/A | No new business class/logger; existing safe model error and technical style retained. |
| MC-BEAN-001 | Not applicable | N/A | No new Bean/injection; package-private metadata constructors and old descriptors retained. |
| MC-UTIL-001 | Applicable | PASS | JDK collections, existing DOM helpers and current SDK; no utility dependency. |
| MC-JSON-001 | Not applicable | N/A | No new wire JSON/enum or client/server DTO. |
| MC-TIME-001 | Applicable | PASS | Existing java.time Timer semantics preserved; actual HumanTaskTimerModelTest five pass, no new deadline/recordedAt behavior yet. |
| MC-CONFIG-001 | Not applicable | N/A | All application/profile/runtime flags unchanged. |
| MC-PATTERN-001 | Applicable | PASS | Local graph dataflow in same topological pass; no second scheduler/graph pipeline. |
| MC-SCOPE-001 | Applicable | PASS | Exact seven declared paths; 930 excluded tracked paths and 25 old SQL bytes checked. |
| MC-TEST-001 | Applicable | PASS | Actual G1/G3 expected assertion RED and G2/G4 GREEN, current 132 reports, no runtime selection. |
| MC-BLOCKER-001 | Applicable | PASS | Current-Step checks closed; Step2 and runtime/recovery remain explicit pending scope. |

## Literal Rule Gate

| Rule | Applicability | Status | Actual independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic immutable structural VO and private edge fact names. |
| Rule 2 | Applicable | PASS | Safe closed XML, actual native model getters and graph/control validation. |
| Rule 3 | Applicable | PASS | Defensive copies and pure immutable values; old descriptor compatibility. |
| Rule 4 | Not applicable | N/A | Existing technical main style, no new business class/logger/Bean; scoped N/A. |
| Rule 5 | Applicable | PASS | Existing DOM/Flowable/JDK and repository helpers only. |
| Rule 6 | Not applicable | N/A | No JSON/wire changes; old APIs remain. |
| Rule 7 | Not applicable | N/A | No environment/configuration keys modified. |
| Rule 9 | Applicable | PASS | Current topology pass extended with one private success fact; no new graph framework. |
| Rule 10 | Applicable | PASS | Existing Duration/Timer semantics and exact finite Timer regression retained. |
| Rule 11 | Applicable | PASS | Same approved technical module/profile; no business entity, MP/DDL runner/generator, second runtime or dependency. |

Source54ModelComplete=true; Source55BusinessFixtureComplete=false; RuntimeUnverified=true; ProductionAccepted=false. This Step does not execute the native engine or prove asynchronous compensation completion.
