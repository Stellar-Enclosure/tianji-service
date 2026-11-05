# T3-12 expense model pack — Source61 / Step 1

Baseline `c0ff76b66e4f903e640f0cc2cfe661076c495b36`, Web unchanged at `43c58a1069a50fb71b955540bae2d7e8526794b0`. Effective [Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) and [Plan](../plan/2026-11-05-15-30-expense-four-path-acceptance.md).

One pinned expense sample binds native DMN classification, transactional reserve, Agent result/Timer, governed review and formal Case, notice or exact compensation. The original normal-success edge dominates the compensation throw. One shared JSON carries four branch inputs and synthetic Task/Case projections using the current contracts. There are no production resource or runtime changes.

| Resource | SHA-256 |
| --- | --- |
| `processes/expense-review.bpmn20.xml` | `5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6` |
| `decisions/expense-routing.dmn` | `94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74` |
| `cases/expense-exception.cmmn` | `c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1` |
| `fixtures/expense-four-path-contract.json` | `e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7` |

G1: `ExpenseFixtureModelsTest#modelResourcesExist` ran with 1 expected assertion failure, 0 errors/skips. Then the four reviewed byte sequences were copied exclusively and checked against the hashes. G2: 112 passed across six actual PS classes, zero failures/errors/skips, 2026-11-05 15:51:06 +08:00. The exact offline source reactor selector was `ExpenseFixtureModelsTest,ProcessModelValidatorTest,CmmnModelValidatorTest,WorkerCompensationModelTest,HumanTaskModelTest,HumanTaskTimerModelTest` with JDK 25, `-DskipITs=true`, existing task settings, ADK message capture and OTel exporters off. Reports are preserved under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/four-path-inventory/`.

The finite test calls the actual pure validators, bindings and native XML converters. It rejects a reserve bypass, unconfigured DMN lane, missing ordinary Worker input and wrong Case binding. It also checks exact JSON/model hashes and uses current neutral Task/Case record parsing. No real application, engine, database, listener, browser or model provider was started.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS release/acceptance test boundary; no production module or layer. |
| MC-REUSE-001 | PASS | Actual Flowable converters, PS validators, Worker registry, current Case binding and C DTOs. |
| MC-DEP-001 | PASS | Existing JUnit, Flowable and Jackson; no POM or dependencies changed. |
| MC-NAME-001 | PASS | Explicit test class, model keys, resource names and current DTOs. |
| MC-VALID-001 | PASS | Actual model and cross-model binding checks, finite negative graph/schema variants. |
| MC-MODEL-001 | PASS | Exact immutable model files and closed synthetic JSON/record values. |
| MC-CONVERT-001 | N/A | Reuses native converters; no new cross-layer mapping or entity. |
| MC-LOG-001 | N/A | No business or logging path introduced. |
| MC-BEAN-001 | N/A | No managed bean or runtime dependency injection. |
| MC-UTIL-001 | PASS | Current JDK/Jackson/validator APIs only. |
| MC-JSON-001 | PASS | Exact fixture fields and SHA; current neutral DTO parsing. |
| MC-TIME-001 | PASS | Native timer durations and synthetic fixed UTC record timestamps, no runtime clock change. |
| MC-CONFIG-001 | N/A | No application/profile/runtime property changes. |
| MC-PATTERN-001 | PASS | Existing native message/timer, task, Case and compensation grammar; no second framework. |
| MC-SCOPE-001 | PASS | Exact eight additions; 968 excluded hashes and all 26 main SQL unchanged. |
| MC-TEST-001 | PASS | Actual resource absence RED, pinned byte verification and named finite G2. |
| MC-BLOCKER-001 | PASS | Current source gates closed; actual deployment/independent E remains pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Current test names and semantic neutral DTO names. |
| 2 | PASS | Existing release/model/binding/contract validation; actual four negative cases. |
| 3 | PASS | Exact closed fixture and current immutable records. |
| 4 | N/A | No managed business class, factory or logging. |
| 5 | PASS | Existing JDK/Flowable/Jackson utilities. |
| 6 | PASS | Closed fixture and typed JSON from existing contracts. |
| 7 | N/A | No application or environment profile touched. |
| 9 | PASS | Reuses established native model patterns rather than new runtime code. |
| 10 | PASS | Fixed UTC and native elapsed timer values; no date API change. |
| 11 | PASS | Existing test-only technical profile, no production DDL/module/generator. |

Model feasibility and finite source verification do not establish publication or end-to-end behavior. Harness, actual fixture drivers, Web parity and E remain separate.
