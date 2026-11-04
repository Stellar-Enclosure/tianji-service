# Process control contract delivery evidence

## Step 43 / Plan Step 1 — Verified for focused commit

- Baseline: clean `tianji-service main@cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d`; 869 tracked file SHA-256 values frozen in `/tmp/tianji-step43-baseline.json` before any owned edit.
- Exact twelve authorized paths: `/tmp/tianji-step43-paths.json`, matching Plan Step 1. Existing ProcessClient, ProcessInstanceDTO, POMs, all 24 SQL files, all server/client/worker/runtime code are excluded.
- Root fully read the final Spec/Plan and all native/privilege corrections; independent pinned-API/source review closed the inactive parallel-execution, timer/message API, empty affinity batch and cancellation-only provenance readiness findings before execution.
- Frozen Spec: `docs/egon/spec/2026-11-04-23-22-process-control-cancellation.md`, SHA-256 `0ad59a990b0474ed4a00198acb0f91ee71c78084fe06ade250b783e8094a8e22`, Review.
- Frozen Plan: `docs/egon/plan/2026-11-05-00-25-process-control-cancellation-implementation.md`, SHA-256 `f465012a4180d9441fbef7a31cf632d9474f8cd88fed0b33529e46c03c80ce06`, Draft under original continuation authorization and Root execution GO. Both formal repository documents passed strict validation before test/source work.
- Prerequisite web Step 42: committed `43c58a1069a50fb71b955540bae2d7e8526794b0`, clean, final 203 finite tests/static/PAC passed; S08/S09 runtime acceptance remains unverified.
- All 17 Manual Check and ten Literal Rule obligations are recorded independently in `/tmp/tianji-step43-lock.json`. The pure C module reuses JDK/OrchestrationChecks and immutable-value compact-constructor conventions under the approved technical-profile boundary, with no new dependency, Bean or mapper.
- Initial tests must compile against current C using explicit reflection-presence assertions. Root must observe genuine JUnit assertion RED before creating any of the eight production types. Missing imports/compile, fixture and offline-resolution errors do not count as RED. Root alone runs tests/Maven/staging/commits; next Step waits for this commit.

No runtime, listener, database, browser, model call, migration application, permission registration/activation/grant, push or deployment is authorized here. Native cancellation, V9, HTTP capability and compensation/dead-letter recovery are later steps; none is delivered by a C-only commit.

Initial RED completed with the exact isolated/offline Plan command: `/tmp/tianji-process-control-contract-red.log`, exit 1, total 12.667 s, finished 2026-11-05T01:46:10+08:00. C ran 2 tests: 1 expected assertion failure (eight required types absent inside assertAll/assertDoesNotThrow), 1 existing ProcessClient/ProcessInstanceDTO compatibility pass, zero errors/skips. No compile, fixture or dependency failure is counted as RED. Root then created only the eight minimal compile-only C signatures/enum constants/interface; value validation, copies and redaction are intentionally absent until the forthcoming typed behavior RED. No such seam will be committed.

## Typed behavior RED and final GREEN

| Log | Actual result | Meaning |
| --- | --- | --- |
| `/tmp/tianji-process-control-contract-red2.log` | 19 tests, 10 assertion failures / 9 passed, zero errors/skips; 11.890 s, finished 2026-11-05T01:55:47+08:00 | Compile-only signatures expose missing validation/copies/redaction. Root noticed nine scalar-null assertions incorrectly expected NPE and corrected those before B, preserving the current OrchestrationChecks convention. |
| `/tmp/tianji-process-control-contract-red3.log` | 19 tests, 10 expected assertion failures / 9 passed, zero errors/skips; 11.197 s, finished 01:57:32+08:00 | Final fixture-correct behavior RED: invalid values accepted, mutable collections retained, invalid matrix/order/caps accepted, audit reason exposed by the temporary record toString. No compile/cache/fixture failure. |
| `/tmp/tianji-process-control-contract-green.log` | **26 passed**: 19 ProcessControlContractTest + 7 OrchestrationContractTest; zero failures/errors/skips; BUILD SUCCESS, 24.839 s, finished 01:59:36+08:00 | Final C contract and existing compatibility gate. Every command used the exact JDK25/ADK_CAPTURE=false, isolated -gs/-s settings, offline, skipITs=true, explicit selectors and disabled OTel exporter flags from the Plan. |

Root implemented only the four value compact constructors over the compile-only signatures: shared OrchestrationChecks scalar grammar, JDK non-null values, immutable collection copies, canonical kind-name/activity ordering, unique waits and both 256 caps, lifecycle/block/action matrix, terminal/result closure invariants, independent Instant values and redacted command/result representations. The three closed enums and separate interface retain exact approved symbols. `ProcessControlClient` declares six corresponding path/scope/resource constants; these local names instantiate the Spec's already required constants without changing any existing interface.

The new interface is separate from ProcessClient; no default unsupported mutation, transport, server Bean or endpoint is introduced. Interface segregation provides a truthful optional capability. C carries immutable Query/Command values; complex durable receipt/native orchestration belongs to later Steps 3/4. Simple value invariants remain direct, consistent with existing C records; no ceremonial pattern hierarchy is added.

The C module still has only its existing test-scoped JUnit dependency. It does not gain Jackson/Validation/MapStruct/Lombok/Spring just to annotate pure JDK values. The effective Spec records this technical-contract boundary. This step verifies record components and explicit wire enum names; actual Jackson/HTTP/OAS behavior is a Step 2 gate, not a claimed C-only serialization result.

## Scope and delivery checks

All **869 pre-existing tracked files match their original SHA-256**, including ProcessClient, ProcessInstanceDTO, all POMs and all **24 existing SQL files**. Only the twelve new declared Step 1 paths exist. No V9 has been created and no existing Flyway file changed. Both frozen repository Spec/Plan still pass strict validation and retain the hashes above. Independent final review of all eight production types and 19 typed tests reports no actionable finding. No repository scratch harness was created; only existing reactor/JUnit/document validators and inline temporary checks were used.

## Final Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Eight types remain in existing C orchestration package; POM unchanged | Pure engine-neutral contract boundary preserved | None |
| MC-REUSE-001 | Applicable | PASS | OrchestrationChecks, ProcessInstanceDTO/GetProcessInstanceQuery, JDK collections/time and sibling JUnit style | Existing capability reused; no parallel framework | None |
| MC-DEP-001 | Applicable | PASS | All old POM SHA values unchanged; imports JDK/JUnit only | Zero dependency introduction | None |
| MC-NAME-001 | Applicable | PASS | Exact DTO/Command/Result/Enum/Client declarations and component reflection | Semantic roles and exact paths preserved | None |
| MC-VALID-001 | Applicable | PASS | Compact constructors plus typed RED3/GREEN | Scalars/shared grammar, null semantics, order/caps/matrix validated under explicit pure-C contract convention | None |
| MC-MODEL-001 | Applicable | PASS | Four immutable value records; defensive List/Set copies and immutable element fields | No ordinary entity or mutable POJO disguised as record | None |
| MC-CONVERT-001 | Not applicable | N/A | No layer projection/mapper/Converter introduced | No semantically distinct conversion requiring MapStruct | None |
| MC-LOG-001 | Not applicable | N/A | No concrete business Bean or logging class; command/result toString redact | No business logger scope or sensitive log | None |
| MC-BEAN-001 | Not applicable | N/A | C remains JDK-only; no Spring managed class/DI | Bean naming/injection scope absent | None |
| MC-UTIL-001 | Applicable | PASS | Shared checks plus JDK Objects/Comparator/List/Set/Instant | Approved utilities only; no new helper library | None |
| MC-JSON-001 | Applicable | PASS | Exact record field/type and enum-name reflection; existing contract unchanged | Deliberate wire names preserved; HTTP serialization proof remains Step 2 | None |
| MC-TIME-001 | Applicable | PASS | Instant fields, nano-preservation/both-order tests, no java.util time | Independent native/DB clock values retain semantics; no chronology invented | None |
| MC-CONFIG-001 | Not applicable | N/A | No environment/profile/property target; all old SHA values unchanged | No Spring configuration divergence | None |
| MC-PATTERN-001 | Applicable | PASS | Separate capability/interface and Query/Command value contracts | Interface segregation applied; no complex C orchestration or fake receipt claim | None |
| MC-SCOPE-001 | Applicable | PASS | Twelve exact new paths; all 869 old SHA/24 SQL unchanged | Current step only; no CL/server/storage/admission | None |
| MC-TEST-001 | Applicable | PASS | Presence RED, fixture-correct typed RED3, 26 GREEN, strict docs and diff checks | Required finite gate completed without runtime | None |
| MC-BLOCKER-001 | Applicable | PASS | All preceding checks + independent code review | No unresolved C-source blocker; later Steps remain required | None |

## Final Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path evidence | Validation evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Eight exact semantic DTO/Command/Result/Enum/Client names | Record/interface reflection and compile | Existing naming convention preserved | None |
| Rule 2 | Applicable | PASS | All public value inputs validated in compact constructors/shared checks | Typed null/scalar/cross-field negative matrix | Approved JDK-only technical contract convention; no business layer or annotation dependency introduced | None |
| Rule 3 | Applicable | PASS | Immutable value records and defensive immutable collections | Copy/mutation/null-member tests | Record value semantics proven; no mutable entity/converter | None |
| Rule 4 | Not applicable | N/A | No business Bean/logger/DI target | Exact path/import inventory | Pure C value/interface scope | None |
| Rule 5 | Applicable | PASS | JDK Objects/Comparator/collections/time plus existing checks | Import/POM SHA review | Utility allowlist respected | None |
| Rule 6 | Applicable | PASS | Exact deliberate enum names/record fields, no ordinal or alternative JSON stack | Contract reflection; CL serialization gate explicitly later | Existing pure-C wire/default convention preserved | None |
| Rule 7 | Not applicable | N/A | No profile/environment file changes | All old config SHA values unchanged | Configuration scope absent | None |
| Rule 9 | Applicable | PASS | Interface segregation and immutable Command/Query boundary | Separate interface/method tests | Actual contract pattern applied; complex durable flow remains future | None |
| Rule 10 | Applicable | PASS | java.time.Instant only | Nano/both-clock-order tests and forbidden import search | Time semantics preserved | None |
| Rule 11 | Applicable | PASS | Existing C module/package and dependency direction | Full reactor compile, 869 excluded SHA checks | Existing technical architecture preserved; no persistence/Event layer | None |

Step 1 is verified for one focused commit. CL/OAS, V9/storage and default-off PS control/native cancellation are not yet implemented; no T3-10 completion or live PostgreSQL/Flowable/IAM acceptance is claimed. The complete original roadmap remains active.
