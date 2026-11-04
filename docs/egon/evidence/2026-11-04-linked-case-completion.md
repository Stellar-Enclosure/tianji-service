# Linked CMMN Case source and BPMN completion execution evidence

- Baseline: `main@151c2802eb76ccf7c9048c39eca4b438e4db2784`, verified clean after Step2 commit and before Step3 source.
- Frozen server Spec SHA-256: `c4e6d4bb8e503b6a9ff2f386514bd64c975eaf83f65b363d838307c9244736e5`.
- Frozen three-Step Plan SHA-256: `071a37e91d91fe9ced0569f1839e8fe37f54494adcdc86adf3d5c81acc25546d`; this is Step3 exact30 paths.
- Frozen exclusion inventory: `/tmp/tianji-step39-baseline.json`,839 files including all24 SQL resources. No migration, immutable Inbox contract, C/SDK/OAS or public Case wire change is authorized.
- Current gate: **Verified for Step3 source delivery**. Introducing commit is obtained after this exact semantic commit via git log on the evidence path. Whole T3-08/native S09 runtime acceptance is not claimed complete.

## Implementation and authority

Closed literal BPMN CaseSource metadata is distinct from Agent Worker metadata. The validator derives a single following Message/Timer wait and requires a prior native wait on every incoming DAG path; it accepts a Timer barrier and the existing Agent result failure branch without forcing an immediate HumanTask. Parent START must actually be APPLIED at runtime. The fixed registry binds tenant/release/parent key+hash/source node/Case key+hash to a real locally registered TENANT SERVICE; current issuer/key metadata is checked without acquiring tokens or issuing HTTP/PDP while locked. Empty source configuration does not resolve any IdP/OAuth provider.

The native source Adapter captures and rechecks the actual unfinished ActivityInstance ID from the current BPMN command cache. It never uses the XML source ID as an occurrence or derives the later wait execution from that ID. `startLinked` participates in the same native/local transaction, inherits owner/business/correlation from the exact APPLIED parent, pins the same immutable release, and journals native Case, CASE_START, provenance and pre-created Inbox together. The SERVICE executor is distinct from the historical owner; no USER security context is restored. Audit source renaming cannot create a second Case for an accepted occurrence.

Completion holds CaseScope before Inbox/command/native row fences. It checks the normally ended same-ID source history and parent wait lifecycle before inspecting an ACTIVE child: Timer won, consumed Message or ended parent become IGNORED_LATE immediately. Only persisted Case COMPLETED/end time plus exact primary/supplemental/milestone plan facts and a real milestone occurrence can advance the independently queried Message execution. Whole termination or inconsistent facts never send success. Native Message, base APPLIED and Inbox APPLIED share one commit. ACTIVE waits do not consume failure budget or expire; orphan scanning remains possible with an empty registry, and withdrawn sources do not borrow another actor.

Adapter/Factory and existing JDBC command/Inbox participants are reused. The polling scheduler is single-threaded and serial; each discovery query is at most32 rows and configured totals1..100 use bounded chunks with seen-key deduplication. Construction does not start a thread, and shutdown owns only its scheduler.

## Test-first and actual execution

Root alone ran Maven with JDK25, isolated offline settings, ADK message capture false, all three OTel exporters none and explicit `-DskipITs=true`. No PG/native acceptance class was selected. Native integration sources were compiled only; no application, engine, PostgreSQL, listener, container, browser, registration, grant or deployment was initiated in this Step.

| Stage | Actual outcome | Log |
| --- | --- | --- |
| Initial literal source whitelist RED |12 tests,1 expected assertion failure,0 errors/skips;17.912s at21:01:35 +08:00; XSD assertion passed first | `/tmp/tianji-case-linked-whitelist-red.log` |
| Component compilation prerequisite | main compilation failed because pinned CaseTaskActivityBehavior has a no-arg constructor; subclass now keeps its own model field and calls super(); no behavior RED claimed | `/tmp/tianji-case-linked-components-red.log` |
| First component RED |97 tests,41 assertion failures,3 fixture errors,0 skips;24.331s at21:46:02; Registry8,Source6,Correlation12,Wait5,Poller6,Release3,config1 meaningful failures; Validator15 passed | `/tmp/tianji-case-linked-components-red2.log` |
| Corrected component/remaining RED |103 tests,19 assertion failures,0 errors/skips;34.706s at22:00:26; Correlation13,linked start5,typed factory1; all other classes passed | `/tmp/tianji-case-linked-components-red3.log` |
| First complete scoped verify | C7 + CL14 + PS281 =302 passed,0 failures/errors/skips;41.574s at22:16:39; permission profile aggregate/artifact verification also executed | `/tmp/tianji-case-linked-full-green1.log` |
| Owned missing-resource regression RED |14 tests,1 expected assertion failure,0 errors/skips;19.330s at22:22:22 | `/tmp/tianji-case-completion-owned-missing-resource-red.log` |
| Repeated local metadata failure RED |15 tests,1 expected assertion failure,0 errors/skips;19.539s at22:34:32 | `/tmp/tianji-case-completion-local-metadata-red.log` |

Three component errors were fixture prerequisites, not RED: two nested Mockito completed-history creations and an unmocked current_schema JDBC lookup. They were corrected before the meaningful component run. All old constructor/PROCESS/direct Case tests remained effective.

The exception correction uses a private foreign-namespace marker only for a missing intent or mismatched executor tuple. The public caller still sees404 for that boundary and no foreign row is modified. Once ownership is established, a missing parent/model/fresh-wait resource follows the normal rollback/relock/bounded-failure path; it no longer escapes bookkeeping merely because its exception type is also404. Native Message remains forbidden on these failures. An additional local-metadata regression covers current registration lookup repeatedly throwing an unknown technical exception. After CaseScope/Inbox/provenance establish ownership, that failure now consumes the existing bounded technical budget; explicit false withdrawal still becomes SOURCE_UNAVAILABLE, and a foreign tuple remains exempt from writes. The catch covers only availability lookup, leaving persistence failures visible.

## Native acceptance source boundary

`FormalCasePostgresTest` is isolated, timeout-bounded and explicitly gated by `tianji.process.postgres.test.url`. It requires a fresh loopback `tianji_it_*` database and dedicated restricted runtime role; existing native provisioning and migration helpers are reused with the single immutable V8. Real Flowable engines/configurers/services/JDBC/history/Message are used in the scenarios; only local IAM metadata is synthetic. The source covers direct manual enable/disable/ABA, primary/supplemental task completion and sentries, milestone, supplemental termination/history handoff, namespaces, cross-Case PI affinity, role replay/expiry, plan/task contention, linked source identity, Message rollback/duplicate, Timer winning ACTIVE, whole termination, forced completion without required traces and withdrawn-source recovery.

These PG/native sources were **compiled, never executed**. They are not a substitute for the original S09 actual native Case-to-BPMN acceptance. That requirement remains **Required / RuntimeUnverified**; real IAM, lock races, SQL catalog/execution and runtime acceptance are unverified. No throughput/latency or production acceptance is fabricated.

## Final source and artifact gates

The final31-class scoped command used `STELLAR_VERIFY_GOAL=verify -Ppermission-producer -DskipTests=false -DskipITs=true` and completed BUILD SUCCESS in40.704s at2026-11-04 22:38:03 +08:00. C7 + CL14 + PS283 = **304 tests passed, zero failures/errors/skips**. Log: `/tmp/tianji-case-linked-final-bounded-verify.log`. Failsafe integration suites were explicitly skipped; no PG/native acceptance class was selected. The preceding owned-missing-resource RED had14 tests/1 expected assertion failure/0 errors/skips at22:22:22 (19.330s), followed by the private namespace correction.

Actual permission aggregate/artifact verification executed. The packaged executable JAR manifest was parsed and compared to the emitted manifest; artifact SHA-256 `c70ab0649f98e3f3663e43e37917a0e06183d09aa63c9ea962d35d487c60063e`, manifest digest `9796b877a5ceb18025d6f7764ac9308792a5eadda31ef5da14d17c411121b159`, policy digest `5a4df5d0e48f72a6167176167e19a6003fec50fa55218165fb1fb060c413082e`. Artifact metadata uses baseline `151c2802eb76ccf7c9048c39eca4b438e4db2784` and unique `LOCAL_UNCOMMITTED-case-linked-20261104-2205` identity. It is a precommit validation artifact, not a clean-source release, registration, activation or role grant. Target/build files remain ignored and uncommitted.

Root inspected all changed production diffs and native acceptance source. Independent review of closed grammar, registry/bundle binding and native source found no additional concrete issue. PG source review corrected a stale pre-claim plan token in the contention scenario and explicitly asserted the expected Message fault before rollback checks; those scenarios remain unexecuted. Strict frozen Spec/Plan checks passed. Exact30 paths and all839 excluded files, including all24 SQL resources, matched their hashes. No pending raw/UOE or reject-all implementation remains in production paths. The original goal stays active and later roadmap work remains.

## Blocking Manual Checks

| Check ID | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing release/bridge/execution/config packages and dependency direction | Exact30,839 frozen paths | Technical structure retained | None for Step3 source |
| MC-REUSE-001 | Applicable | PASS | Existing local SERVICE metadata, command receipts, final Inbox and native extension hooks | No SQL/POM/new dependency and304 tests | No duplicate Case state authority | None for Step3 source |
| MC-DEP-001 | Applicable | PASS | Existing JDK/Spring/Jackson/Flowable only | All dependency manifests frozen | No dependency addition | None for Step3 source |
| MC-NAME-001 | Applicable | PASS | Explicit CaseSource/WaitInspector/Correlation/Poller and source facts | Type and exact path review | Semantic owners clear | None for Step3 source |
| MC-VALID-001 | Applicable | PASS | Closed grammar/all-path wait, exact native AI/Case/source/current SERVICE and positive facts | Model/registry/namespace/trace/rollback tests | Constructed facts/metadata do not authorize execution | None for Step3 source |
| MC-MODEL-001 | Applicable | PASS | Immutable source properties/metadata, copied maps/sets and typed observations | Copy/bounds/unknown-field tests | No competing wire model | None for Step3 source |
| MC-CONVERT-001 | Not applicable | N/A | Existing Case/public task contracts unchanged | No PO/mapper added | No conversion concern | None for Step3 source |
| MC-LOG-001 | Applicable | PASS | Existing type-only failure/count logging; ACTIVE does not emit payload info | Source/log review | No identity/token/body logging | None for Step3 source |
| MC-BEAN-001 | Applicable | PASS | Lazy typed collaborators, actual poller readiness and empty registry short circuit | Config/factory/context tests | No eager service-engine cycle or credential lookup for empty sources | None for Step3 source |
| MC-UTIL-001 | Applicable | PASS | JDK digest/time/concurrency and existing JDBC/native services | Imports and bounded scheduler tests | No speculative framework/utility layer | None for Step3 source |
| MC-JSON-001 | Applicable | PASS | C/SDK/OAS frozen; no new public endpoint/payload | 839 exclusions and actual304 tests | Published consumer contract retained | None for Step3 source |
| MC-TIME-001 | Applicable | PASS | Instant/Duration, native Date boundary, DB time/due/failure accounting | Timer/ACTIVE/terminal/deadline finite tests | No absolute Case lifetime or default-zone rule | None for Step3 source |
| MC-CONFIG-001 | Applicable | PASS | Two existing flags plus empty sources and bounded1s/1s/32/10 maintenance | Strict binding/range and disabled/direct assembly tests | No real tenant/client credentials/grants configured | None for Step3 source |
| MC-PATTERN-001 | Applicable | PASS | Native Adapter/Factory plus existing local command/Inbox transaction | Actual converter type, exact occurrence and default callback exclusion tests | No business Case state machine or new scheduler authority | None for Step3 source |
| MC-SCOPE-001 | Applicable | PASS | One30-path semantic Step and no extra migration | 839 frozen paths including24 SQL | No unrelated refactor or Step4 source | None for Step3 source |
| MC-TEST-001 | Applicable | PASS | Actual RED/fixture distinctions,304PASS and real opt-in acceptance source | Logs/compile/artifact source review | PG/native acceptance is still unexecuted | None for Step3 source |
| MC-BLOCKER-001 | Applicable | PASS | No unfinished source behavior or known placeholder in this Step | Final review/gates and owned error regression | Original native S09 remains required | None for Step3 source |

## Literal Rule gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic registry/source/native wait/completion names and closed enums | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 2 | Applicable | PASS | Explicit strict settings/model/current registration/native source/history/namespace guards | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 3 | Applicable | PASS | JDK immutable records, copied collections and existing wire DTOs; no PO/mapper conversion | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 4 | Applicable | PASS | Existing named factories/manual constructors/safe logging; typed lazy collaborators | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 5 | Applicable | PASS | Existing pinned libraries only, POMs unchanged | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 6 | Applicable | PASS | No public JSON/wire serializer change; real permission artifact retained | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 7 | Applicable | PASS | Base YAML necessary bounded keys/defaults only, no credentials or additional flags | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 9 | Applicable | PASS | Native Adapter/Factory and local command/Inbox participants match actual variation; no business state machine | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 10 | Applicable | PASS | Instant/Duration and explicit native Date.toInstant boundaries; DB-clock due/terminal semantics | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |
| Rule 11 | Applicable | PASS | Always applicable: actual vertical technical/JDBC/Flyway structure preserved, all24 SQL immutable | Final diff,304PASS, artifact and839-path hash gate | Effective Spec obligation preserved | None for Step3 source |

## API review

| Concern | Verdict | Evidence / boundary |
| --- | --- | --- |
| Route/method/operation | PASS preserved | No new public endpoint; existing Case and task mappings frozen |
| Request fields/validation | PASS preserved | Existing C/SDK/advice bytes frozen; internal native facts are not caller identity |
| Success schema | PASS preserved | Existing DTOs and OAS untouched; regression selectors passed |
| Errors/status/media | PASS preserved | Public error boundary unchanged; internal foreign tuple vs owned failure classification now explicit |
| Auth/tenant/resource | PASS for source | Current local TENANT SERVICE metadata, exact owner/executor namespaces and current revoked-key checks; no live IAM claim |
| Time | PASS for source | Actual occurred/due Instant semantics and bounded Duration; no fabricated Case expiry |
| Idempotency/consistency | PASS for source/finite tests | Stable occurrence independent of sourceId, same transaction Inbox/Case/Message and immutable terminal facts; no PG concurrency claim |
| Consumer compatibility | PASS preserved | C/CL/OAS and previous constructor semantics retained |
| OpenAPI/artifact authority | PASS preserved | Static Case OAS unchanged; actual packaged PaC manifest validated, not activated |

## Primary requirement conformance

| Requirement | Verdict | Scope / proof |
| --- | --- | --- |
| REQ-401 | Preserved | All24 migration files/final schema and immutable stores unchanged |
| REQ-402 | Satisfied for backend source | Same-bundle CMMN binding and release/node/hash registry enforcement |
| REQ-403 | Preserved | Prior direct Case entry plus old regression tests unchanged |
| REQ-404 | Satisfied for source | Current fixed SERVICE, parent APPLIED owner, actual native occurrence and atomic linked start |
| REQ-405 | Preserved | Case provider and public wire unchanged |
| REQ-406 | Preserved | Prior owner/operator/SERVICE access rules and regression tests |
| REQ-407 | Preserved | Prior finite actions, fresh roles, assignee protection and cases tests |
| REQ-408 | Preserved | Prior runtime REV/history domains and terminal handoff |
| REQ-409 | Preserved | Existing CASE task API/receipt implementation; native opt-in regression source included |
| REQ-410 | Satisfied for source | Native start participants and scope-before-Inbox/command/native apply/retry ordering |
| REQ-411 | Satisfied for source | Fixed BPMN Case hook and actual parser/supplier readiness; CMMN governed factory frozen |
| REQ-412 | Satisfied for source | Precreated intent, positive persisted completion facts, no terminated success, bounded orphan recovery |
| REQ-413 | Satisfied for source | Closed CaseSource/graph wait metadata, prior wait dominance and actual source AI; independent current Message execution |
| REQ-414 | Satisfied for source | Empty/direct mode, independent stop-new flag and persistent completion maintenance |
| REQ-415 | Satisfied for source | Fixed registered local SERVICE before/after apply; no HTTP/PDP or historical USER restoration |
| REQ-416 | Required / RuntimeUnverified | Actual native S09 Case-to-BPMN/PG/IAM acceptance still must be run; source/compile is not a waiver |

## Effective dependency requirement preservation

Historical exact-path/no-provider bounds are scoped by effective Plan §2.3; their delivered invariants remain preserved dependencies. Each ID is retained independently:

| Requirement | Verdict | Current evidence |
| --- | --- | --- |
| REQ-201 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-202 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-203 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-204 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-205 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-206 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-207 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-208 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-209 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-301 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-302 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-303 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-304 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-305 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-306 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-307 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-308 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-309 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-310 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-311 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-312 | Preserved dependency | 839 frozen paths cover unchanged C records/tests and SDK/OAS/transport; codec/HTTP fake regressions passed |
| REQ-101 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-102 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-103 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-104 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-105 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-106 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-107 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-108 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-001 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-002 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-003 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-004 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-005 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |
| REQ-006 | Preserved dependency | 839 frozen paths; unchanged safe XML/closed CMMN metadata grammar; focused existing tests passed |

Final source conformance: **Verified for the remaining T3-08 backend source only**. Runtime S09 is still Required / RuntimeUnverified; the whole original goal is not complete. The planned single Step3 commit must be verified clean before the next roadmap task. No runtime activation, catalog registration, grant or push is included.
