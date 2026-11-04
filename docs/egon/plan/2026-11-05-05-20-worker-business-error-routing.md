# Governed Worker business-error routing implementation plan

| Field | Value |
| --- | --- |
| Document | `2026-11-05-05-20-worker-business-error-routing.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 05:20 Asia/Shanghai` |
| Updated | `2026-11-05 06:06 Asia/Shanghai` |
| Owner | `Root implementation review; source execution remains gated by a later explicit GO` |
| Repository | `tianji-service` |
| Scope | `One atomic T3-10 feature Step: fixed released business-error routing for ordinary BPMN External Workers` |
| Source Requirement | `Root-approved reviewed Spec and original goal; one focused commit per logical task` |
| Baseline Revision | `main@c731f3fda692d24115c6aaff36f19192500e537a; clean worktree at Plan inspection; executor must recheck immediately before GO` |
| Implements Spec | [Governed Worker business-error routing](../spec/2026-11-05-04-48-worker-business-error-routing.md) |
| Spec Status | `Review` |
| Spec Revision | `Updated 2026-11-05 06:06 Asia/Shanghai; frozen temporary draft SHA-256 c5d9dbaa02b737e8395a7a9df90a85f0c1f60eb4f075719a5db536607cf3b802` |
| Effective Specs | [Governed Worker business-error routing](../spec/2026-11-05-04-48-worker-business-error-routing.md); [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§11.2, §12.2, §17.3 T3-10/S10/S11` |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

This Plan implements the reviewed business-error Spec in one atomic, path-limited Step and one commit. The model grammar, immutable release metadata and runtime routing are inseparable: committing only the profile would publish a route the backend cannot honor, while committing only runtime logic would have no released authority. The Step therefore uses one uncommitted progression—presence RED, compile prerequisite A, typed RED, minimum B, GREEN, bounded regression, documentation/evidence—then commits the complete slice.

Only three Process Server production files change. Focused PS/W tests and one fixture prove the parser, Flowable converter, existing public Worker compatibility, terminal replay, every current fence, ID-only postconditions, quiet-no-op detection and native/V4 rollback. The opt-in PostgreSQL scenario is source/compile coverage only and is never selected. Completion evidence is finite source/module proof; real PostgreSQL, native async propagation, IAM, business compensation, failed/dead-letter recovery and S10/S11 remain Unverified/Required.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Governed Worker business-error routing](../spec/2026-11-05-04-48-worker-business-error-routing.md)
- Status: `Review`
- Revision: `2026-11-05 06:06 Asia/Shanghai`; SHA-256 `c5d9dbaa02b737e8395a7a9df90a85f0c1f60eb4f075719a5db536607cf3b802`; implementation baseline `c731f3fda692d24115c6aaff36f19192500e537a`
- Approval evidence: Root full-read the 843-line current Spec and approved Plan drafting under the original goal. That approval is not user acceptance and is not source GO.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Worker business-error Spec](../spec/2026-11-05-04-48-worker-business-error-routing.md) | Review; SHA above | All sections | Exact behavior, scope, files, tests and runtime limits |
| Normative legacy dependency | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) | Current SHA `3cbb9a6a8850c074f4048f4e7093793876f9c5ea2b3df0f2af9d0a0fdd26ca59` | `§11.2`, `§12.2`, `§17.3 T3-10/S10/S11` | Business-vs-technical failure, Worker receipt and unfinished compensation/recovery authority |

### 2.3 Superseded or excluded content

None. This Plan neither supersedes [the related cancellation Spec](../spec/2026-11-04-23-22-process-control-cancellation.md) nor imports its REQ-501-515; it cites only that document's already-approved technical-vertical/no-runtime context. The Plan does not complete wider T3-10 recovery/compensation work, and the legacy roadmap's suggested future recovery classes are not implementation scope here.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-601` | §4/§7.3.1 | at most one fixed error profile per ordinary worker | valid fixture yields one immutable entry; ambiguous/wide forms reject | model + validator + tests |
| `REQ-602` | §4/§7.3.1 | current DAG proves attached-worker dominance and one legal outgoing path | implicit edge participates in current caps; disconnected/cyclic/0/2-out rejects | validator graph tests |
| `REQ-603` | §4/§7.3.1 | no Agent/DMN/Human/Case/subprocess/compensation/multi-error profile | parser rejects message-wait attachment; unchanged release preparation proves bidirectional registry-internal classification; controlled internal runtime entry remains manual | validator/service tests + release regression |
| `REQ-604` | §4/§7.3.2 | released model code routes; caller code is diagnostic/hash only | `bpmnError` receives only model code | service tests/implementation |
| `REQ-605` | §4/§7.3.2 | retain actor/source/job/owner/expiry/model/schema/input fences and terminal replay order | drift/stale cases never call native; replay is native-free | service test fixture |
| `REQ-606` | §4/§7.3.2 | ID-only bounded old/new queries prove actual conversion, then new fields validate independently | external list empty; exactly one full-affinity executable error job | service postcheck/tests |
| `REQ-607` | §4/§7.3.4 | quiet no-op/missing/wrong proof is an exact dependency failure after verified owner/lease | cause-free fixed-message ISE -> existing 503; no V4 terminal receipt; rollback; old 404 helpers unchanged | service tests |
| `REQ-608` | §4/§7.3.3 | native conversion and V4 finish share one local TX | receipt failure restores external/V4 state | mock TX; optional PG source |
| `REQ-609` | §4/§7.3.3 | same failure replays; changed diagnostic conflicts | original receipt/time; zero second native calls | service replay tests |
| `REQ-610` | §4/§10.3 | old retry/unknown/undeclared/Agent behavior unchanged | exact old builders/outcomes remain | service regression |
| `REQ-611` | §4/§16 | C/CL/W main, Worker business V1/V2, V4/V1-V9, digests, timers, graph caps, config/permissions/POM unchanged | exact three PS main files; old tests/hashes pass | scope/static/regression gates |
| `REQ-612` | §4/§7.3/§9 | receipt means accepted/scheduled, not propagated/completed/compensated | tests/evidence use honest wording; async job remains Flowable-owned | service/consumer/docs |
| `REQ-613` | §4/§15 | bounded codes and no private payload/log leakage | handler config has model code only; no variables/new logs/raw details | service/static tests |
| `REQ-614` | §4/§14 | finite unit/converter/mock proof; PG source compile-only | exact selectors pass; no DB/app/engine/listener/browser starts | validation plan |
| `REQ-615` | §4/§16/§18 | compensation/original receipt/recovery/S10/S11/T3-12 remain Required | final evidence never marks T3-10 complete | evidence/scope audit |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

One Step deliberately contains four uncommitted gates:

1. Add only a reflection/resource/API presence test and observe assertion RED while all existing tests compile.
2. Add the final immutable metadata type/getter/empty defaults plus the final positive XML fixture as compile prerequisites; rerun the presence method GREEN. No route behavior exists yet and nothing is committed.
3. Promote the model test and add service/consumer/optional-PG source tests. Observe typed assertion RED from the missing validator grammar and missing business-error runtime branch, with zero compile/fixture errors.
4. Implement the validator and service B, run focused GREEN and bounded predecessor regression, then add the reviewed Spec/Plan and actual evidence. Stage exact paths once and create one semantic commit.

This ordering prevents a publishable intermediate model profile, preserves test-first proof despite a new compile-time type, and keeps the only commit coherent.

### 4.2 Test-first strategy

| Behavior | Presence/RED | Compile A | Typed RED | Minimum B/GREEN |
| --- | --- | --- | --- | --- |
| metadata/fixture/API availability | reflection/resource test fails | nested record/map/getter + fixture | typed model assertions compile | validator fills exact map |
| closed grammar/graph/converter | resource missing initially | fixture exists, parser unchanged | valid fixture rejected; metadata/converter assertions fail | whitelist/error graph implemented |
| declared business rejection | no new public service method needed | model metadata type available | current service calls failure builder/returns manual; native assertions fail | model-code bpmnError + exact postcheck + V4 finish |
| replay/fences/old branches | current behavior mostly passes | none | new negative assertions expose only missing declared branch/postcheck | existing order preserved; focused regression GREEN |
| public compatibility | existing contract supports outcome | none | MVC/W receipt tests may already pass and act as controls | remain GREEN without main C/CL/W change |

Expected RED is assertion failure only. A compile error, fixture initialization error, mock setup exception, dependency resolution error or unexpected existing-test failure is not accepted as RED and returns to the owning test/compile prerequisite before production B.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Spec frozen/reviewed; clean execution baseline; presence RED and compile-A stable | after A, disjoint model/runtime/compatibility subtasks within this same Step | two workers on one file; Maven/staging during inconsistent writes; any other Plan/source writer on these paths | one atomic commit is preserved while nonoverlapping file ownership reduces latency safely |

There is still only one implementation Step and one commit. Before A is stable, the model owner alone owns Files 1-3/2 and Root alone runs presence/compile-A Maven. After Root confirms A stable, disjoint workers may proceed concurrently: model owner = ValidatedProcessModel, ProcessModelValidator, ProcessModelValidatorTest and fixture; runtime owner = FlowableWorkerService and FlowableWorkerServiceTest; compatibility owner = WorkerControllerTest, RemoteWorkerPollerTest and ProcessServerPostgresTest source. Root owns all three docs, integration/review, every Maven call, exact staging and commit. No worker runs Maven while another owned path is inconsistent, and no two workers edit the same file.

### 4.4 Commit boundaries

Exactly one commit is planned. Grammar, metadata and runtime cannot be safely split into separately publishable commits. Tests, fixture and evidence belong to the same observable feature. There is no migration/config/permission commit.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| `WorkerBusinessError` nested value/map | Add, Spec §7.0/§10 | current model has parallel immutable maps but no error profile | parse XML in fail | one map/constructor chain; avoids runtime parsing | Implement |
| validator grammar | Expand, §7.3.1 | current closed whitelist/DAG | arbitrary Flowable BPMN | bounded branches/tests vs unsafe surface | Implement |
| declared service branch | Expand, §7.3.2 | current fail uses failure builder only | retain manual outcome | one native call/two queries; required business path | Implement |
| ID-only postcheck | Add, §7.3.2 | pinned native insert can return false | trust void return | two bounded queries; closes false success | Implement |
| V4 receipt/replay | Keep, §7.0/§11 | store/schema already support state | new table/store | no new element | Already exists/reuse |
| external API/client/starter main | Keep, §9/§16 | existing enums and validReceipt accept both outcomes | add endpoint/DTO | unnecessary public failure points | Already exists/no change |
| release/registry internal classification | Keep, §7.3.1 | `ProcessReleaseService#prepare`, `WorkerTopicRegistry#acceptsModel/#isInternalAgentDispatch` are already bidirectional | infer ordinary status from parser map | parser cannot prove operator registry kind | Already exists/reuse unchanged |
| new framework/Strategy/Event/recovery | Remove, §7.0/§17 | one fixed profile; roadmap not resolved | current Command/State/Specification | would add files/state/ops | Do not implement |

No fetch-then-forward API, caller-derived tenant/route, duplicate model, cache, table, job, dependency, layer or speculative handler survives this audit.

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| metadata compile seam | 601-604/611 | presence reflection/resource | current model constructors | nested record/map/getter | typed validator/service tests | Step 1 |
| closed model profile | 601-603/611 | ProcessModelValidatorTest typed RED | metadata + fixture | validated fixed profile | service runtime authority | Step 1 |
| declared runtime branch | 604-610/612/613 | FlowableWorkerServiceTest typed RED | metadata; current service API | native schedule/proof/V4 outcome | existing HTTP/W consumers | Step 1 |
| compatibility/runtime limits | 611-615 | consumer tests + optional PG source + final regression | complete B | finite evidence/docs | commit/review | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | existing `release`/`worker` technical vertical; predecessor exception | Spec §6.1 | exact packages/files only; no new layer/module | Step 1; MC-ARCH-001 |
| Reuse/capability | current validator/model/service/V4; Flowable 8 public builder/queries | §6.1/§7.0 | reuse all; no dependency/helper/store | Step 1; MC-REUSE/DEP |
| Naming/model/validation/conversion | nested immutable records/maps; C compact validation; no converter boundary | §6.2/§10 | one nested `WorkerBusinessError` record + Map.copyOf; current handoff checks; no converter | Step 1; MC-NAME/VALID/MODEL/CONVERT |
| Bean/logging/util/JSON/time/config | service constructor/factory unchanged; no logger; JDK/Spring only; JSON/config unchanged; V4 Instants | §6.2/§6.3/§15 | no Bean/log/config/JSON changes; no new log; preserve DB-clock expiry/receipt | Step 1; MC-LOG/BEAN/UTIL/JSON/TIME/CONFIG |
| Business variation/pattern | fail Command+V4 replay; Flowable job State; closed validator Specification | §13 | extend existing participants; no ceremonial strategy | Step 1; MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| immutable profile | JDK Map/record, current model maps | ValidatedProcessModel | fits; field absent | reuse pattern | nested value only; no dependency | Step 1; REUSE/DEP/MODEL |
| grammar/graph | current DOM whitelist/DAG | ProcessModelValidator | fits; error nodes absent | extend same parser | none | Step 1; REUSE/VALID/PATTERN |
| native scheduling | failure builder vs completion builder | ManagementService/ExternalWorkerCompletionBuilder 8.0.0 | completion builder exact; failure builder lacks error | use public bpmnError | none | Step 1; REUSE/DEP |
| postcondition | public Job queries vs JDBC ACT write | Job/ExternalWorkerJobQuery | all needed fields available | ID-only queries + independent compare | private method only | Step 1; REUSE/SCOPE |
| receipt/replay | V4/WorkerLeaseStore | current Finish/state/hash | full fit | reuse unchanged | none | Step 1; REUSE/TEST |
| external compatibility | C/CL/W current code | enums/client validReceipt | full fit | no main changes | tests only | Step 1; SCOPE/JSON |
| ordinary/internal classification | ProcessReleaseService.prepare + WorkerTopicRegistry + requireMode | exact current bidirectional publication/runtime gates | full fit; parser proves only graph exclusion | preserve and regression-test | None | Step 1; REUSE/VALID/SCOPE |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2/§8/§10 | current behavior/nested value names | tests -> ValidatedProcessModel -> consumers | exact WorkerBusinessError role; no ambiguous carrier | type inventory/compile | Step 1 | PASS under exception |
| Rule 2 | Spec §6.2/§7/§10 | C compact validation + validator/service fences | typed tests -> validator -> service | exact XML and runtime handoff validation/order; no phone/groups | negative tests | Step 1 | PASS under boundary |
| Rule 3 | Spec §6.2/§10 | immutable nested records/maps, no converter | model A before typed tests | record + Map.copyOf; no duplicate mapper | immutability/constructor tests | Step 1 | PASS under exception |
| Rule 4 | Spec §6.2/§7/§13 | existing final classes/explicit factories, no logger | service only after tests | preserve constructor/wiring; add no log; privacy static review | compile/config regression | Step 1 | PASS under Root exception |
| Rule 5 | Spec §6.2/§15 | JDK/Spring/Flowable imports | all source files | no new utility/dependency | POM/import diff | Step 1 | PASS |
| Rule 6 | Spec §6.2/§9/§10 | C/Controller/CL/W JSON unchanged | consumer tests only | no Jackson/main contract edit | MVC/W regression | Step 1 | N/A for production change |
| Rule 7 | Spec §6.2/§15 | no configuration scope | none | no key/profile edit | path diff | Step 1 | N/A |
| Rule 9 | Spec §6.2/§13 | existing Command/State/Specification participants | tests -> model/validator/service | extend selected patterns; no handler hierarchy | branch/state tests | Step 1 | PASS |
| Rule 10 | Spec §6.2/§6.3 | Instant/timestamptz/DB clock existing | service/store context + tests | no new time type; preserve expiry/decidedAt | time/replay + forbidden import search | Step 1 | PASS |
| Rule 11 | Spec §6.1/§6.2/§8 | approved technical vertical | every path exact | no third layer/module/MP/generator | path/POM/static gate | Step 1 | PASS under Root boundary |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-601` | NativeJob/V4/WorkerTask lease expiry | Occurred instant | `Instant` | UTC instant | UTC Z | Not applicable: technical lease | Not applicable: no business display rule | existing DB-clock strict `>`; no DST/calendar | existing PG/Flowable precision | current order/fence unchanged | Step 1; TEST-605/610 focused GREEN |
| `TIME-602` | V4 `decided_at`, WorkerReceiptDTO.decidedAt | Occurred instant | `Instant` | UTC instant | UTC Z | Not applicable: receipt occurrence | existing consumer display only | no cross-clock rule | existing clock_timestamp/timestamptz; replay preserves | same field/new reachable outcome | Step 1; TEST-603/609/MVC |

## 5. Change File Tree

```text
tianji-service
├── stellar-enclosure-tianji-process-server
│   ├── src/main/java/top/egon/cola/platform/tianji/process/server
│   │   ├── release/ValidatedProcessModel.java                  MODIFY
│   │   ├── release/ProcessModelValidator.java                  MODIFY
│   │   └── worker/FlowableWorkerService.java                   MODIFY
│   ├── src/test/java/top/egon/cola/platform/tianji/process/server
│   │   ├── release/ProcessModelValidatorTest.java              MODIFY
│   │   ├── worker/FlowableWorkerServiceTest.java               MODIFY
│   │   ├── api/WorkerControllerTest.java                       MODIFY
│   │   └── ProcessServerPostgresTest.java                      MODIFY (source only; never selected)
│   └── src/test/resources/fixtures/worker-business-error.bpmn20.xml CREATE
├── stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java MODIFY
└── docs/egon
    ├── spec/2026-11-05-04-48-worker-business-error-routing.md CREATE
    ├── plan/2026-11-05-05-20-worker-business-error-routing.md CREATE
    └── evidence/2026-11-05-worker-business-error-routing.md  CREATE
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java` | current closed parser/converter matrices | presence + typed profile/negative/converter tests | model proof | 1 | 601-603/611/614 | focused test |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java` | immutable maps/constructor chain | WorkerBusinessError/map/getter/empty defaults | compile/model authority | 1 | 601-604/611 | compile+tests |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-business-error.bpmn20.xml` | absent | one ordinary fixed error route | positive XML authority | 1 | 601-603 | parser/converter |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerServiceTest.java` | availability-only test | controlled service/TX/native matrix | runtime RED/GREEN | 1 | 604-610/612-614 | focused test |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/WorkerControllerTest.java` | existing fail JSON test | BUSINESS_REJECTED unchanged-wire assertion | API compatibility | 1 | 611/612 | MVC test |
| MODIFY | `stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java` | existing retry/manual receipt cases | positive business-rejected receipt case | W compatibility | 1 | 611/612 | W unit test |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java` | opt-in fresh-loopback class | optional business-error success/rollback source method | future runtime acceptance source | 1 | 606-608/612/614 | testCompile only now |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java` | whitelist/DAG/worker metadata | closed error grammar/implicit edge/map | release authority | 1 | 601-603/611 | model GREEN |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java` | existing fail/replay/fences | declared branch/public ID-only proof/V4 outcome | runtime orchestration | 1 | 604-610/612/613 | service GREEN |
| CREATE | `docs/egon/spec/2026-11-05-04-48-worker-business-error-routing.md` | frozen `/tmp` reviewed Spec | exact frozen reviewed design | design authority | 1 | all | strict validator/hash |
| CREATE | `docs/egon/plan/2026-11-05-05-20-worker-business-error-routing.md` | this `/tmp` Plan | reviewed executable Plan | execution authority | 1 | all | strict validator/hash |
| CREATE | `docs/egon/evidence/2026-11-05-worker-business-error-routing.md` | absent | actual RED/GREEN/static/runtime limits | evidence | 1 | 614/615 | final audit |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- Root must confirm `git rev-parse HEAD` is still `c731f3fda692d24115c6aaff36f19192500e537a` and `git status --short` is clean immediately before source GO. Any drift in the 12 paths requires re-audit; unrelated dirty paths must remain unstaged.
- Root alone runs Maven, stages exact commit paths and commits. No worker may revert or format another worker's path.
- All 25 existing classpath SQL files, C/CL/W production, POMs, YAML, permissions and current docs outside the three new docs are immutable for this Step.
- Generator is unsupported/not applicable to this approved technical vertical; no generator command or DDL journal entry occurs.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK | `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` | Java 25 available | compile/test only |
| Maven | `./mvnw` through `./scripts/verify-source-reactor.sh` | offline dependencies in `/Users/mario/maven/repository`; temporary settings `/tmp/tianji-compat-fix/settings.xml` | no network |
| Flowable | resolved 8.0.0 jars and javap evidence | exact version unchanged | API/bytecode static; no engine start |
| Tests | JUnit5/Mockito/MockMvc/converter | no PG system property; `DskipITs=true` | source/module only |
| Docs | skill validators under `.agents/skills` | Spec/Plan at final repo paths | static structure/links |

### 6.3 Immutable constraints and approved decisions

- Model code alone selects the route; diagnostic stays result-hash/V4 only. Parser metadata is not ordinary-worker authority by itself.
- Preserve `ProcessReleaseService#prepare`: every worker passes `acceptsModel`; every message wait is registered internal; every registered-internal worker requires enabled matching message wait. Preserve runtime `requireMode`, `linkedWait == registration.internalAgentDispatch()` and `registry.acceptsModel` checks.
- Both public postcheck queries use trusted job ID only with `listPage(0,2)`; old must be empty, new exactly one then independently match every field/type/config.
- Do not filter old-row absence by affinity, use `FOR UPDATE` in postcheck, write ACT tables, pass variables, or call `executeJob`.
- Terminal V4 replay precedes every native read. Existing fences/order and all nondeclared branches remain exact.
- No DB/app/native engine/listener/browser/network is started and no permission/config is registered or enabled.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-001` | Positive fixture is the complete `worker-mapping` shape plus one declared error, interrupting boundary and rejection review path | current `worker-mapping.bpmn20.xml`; Spec fixed graph | only replaces abbreviated pseudocode with valid approved XML | fixture would fail parser/converter before behavior RED |
| `PLAN-CLAR-002` | Converter assertion uses the exact existing `new BpmnXMLConverter().convertToBpmnModel(InputStreamProvider,true,true)` API and real model getters | current ProcessModelValidatorTest and pinned converter 8.0.0 | no contract/behavior decision changes | invented test API would cause compile/setup failure |
| `PLAN-CLAR-003` | After compile-A, disjoint file owners may work within the same atomic Step while Root serializes Maven/staging | exact nonoverlapping paths in §4.3/§5 | no Step/commit/behavior/scope change | overlapping edits or Maven on inconsistent tree would invalidate RED/GREEN evidence |
| `PLAN-CLAR-004` | New native postcheck failures use private `businessErrorUnavailable()` returning fixed-message cause-free `IllegalStateException`, not existing `unavailable()` | `FlowableWorkerService#unavailable` returns masked ProcessResourceNotFoundException/404; existing ProcessApiErrors maps ISE to dependency 503 | only corrects classification for the new proof after owner/lease verification; no new class/code/wire or old mapping change | reusing old helper would mislabel engine proof failure as hidden resource 404 |
| `PLAN-CLAR-005` | Converter test expects `BoundaryEvent.isCancelActivity()==false` for ErrorEventDefinition while source validator still requires literal XML true | pinned Flowable 8 converter/engine bytecode and hashes in Spec EVD-608A | records upstream normalization; runtime ErrorEventDefinitionParseHandler still creates interrupting behavior | asserting true would fail the fixed converter, while relaxing XML would admit an unreviewed profile |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Route declared ordinary Worker business rejection through the fixed BPMN Error profile

- Requirements: `REQ-601`, `REQ-602`, `REQ-603`, `REQ-604`, `REQ-605`, `REQ-606`, `REQ-607`, `REQ-608`, `REQ-609`, `REQ-610`, `REQ-611`, `REQ-612`, `REQ-613`, `REQ-614`, `REQ-615`
- Dependencies: frozen reviewed primary Spec; baseline recheck; no prior implementation Step
- Baseline state: C/CL/W/V4 already define business rejection, but validator rejects BPMN error elements, model has no metadata and service deadletters every business rejection as manual.
- Observable outcome: one released ordinary worker can schedule its fixed model BPMN Error atomically with an exact replayable `BUSINESS_REJECTED` receipt; every other branch stays unchanged.
- End state: exact three PS production files, scoped tests/fixture and docs/evidence are complete; no public/schema/config/dependency change; native PG/async/compensation/recovery remain Unverified/Required.
- Test-first gate: `Required` — presence test must first fail assertions for missing getter/nested type/resource; after compile A, typed tests must fail assertions for rejected grammar and missing runtime branch/postcheck, never compile/setup/environment errors.
- Manual Checks: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`
- Literal Rules: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`

- Purpose: Establish presence RED before compile A, then own the complete closed model/converter contract after Files 2-3.
- Symbols: `worker_business_error_symbols_and_fixture_are_present`; `fixed_worker_business_error_is_graph_derived_immutable_and_converter_safe`; invalid grammar/attachment/graph matrix.
- Repository evidence: current test already owns packaged XML, immutable metadata, converter and broad negative grammar matrices.
- Dependencies and consumers: initially reflection/resource strings only; after A imports nested `WorkerBusinessError`, BpmnXMLConverter, BoundaryEvent/ErrorEventDefinition.
- Why now: only this existing cohesive test can prove missing capability without adding a test-only class. It is intentionally revisited inside the same uncommitted Step.
- Contract/signature changes: tests expect public `workerBusinessErrors()` and nested public value plus fixture; no public protocol change.
- Input/output and state mapping: fixture worker ID -> `WorkerBusinessError(errorId,modelCode,boundaryId,targetId)`; native converter errors map ID->code and boundary errorRef; old fixtures -> empty map and exact old timer/case/worker metadata.
- Error and edge behavior: missing/unknown/unused/duplicate ref, invalid/dynamic code, multi-error, noninterrupting, explicit incoming, 0/2 outgoing, cycle/disconnected, wrong attachment, message-wait worker, subprocess/compensation and widened attributes all reject. Parser proves model graph only; existing release/registry checks remain the bidirectional internal-kind authority.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact validation/model/scope proof; no external JSON/config/DB change.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — semantic nested type, closed validation, immutable record, JDK/Flowable only, unchanged JSON/config/time, Specification pattern and existing package.
- Implementation pseudocode:

```java
// Pass P, before any production/resource edit
@Test worker_business_error_symbols_and_fixture_are_present() {
  assertAll(
    () -> assertDoesNotThrow(() -> ValidatedProcessModel.class.getDeclaredMethod("workerBusinessErrors")),
    () -> assertDoesNotThrow(() -> Class.forName(ValidatedProcessModel.class.getName()+"$WorkerBusinessError")),
    () -> assertNotNull(getResource("/fixtures/worker-business-error.bpmn20.xml")),
    () -> assertDoesNotThrow(() -> ExternalWorkerCompletionBuilder.class.getMethod("bpmnError", String.class))
  )
}

// Pass T, only after Files 2-3 and compile-A GREEN
model = validator.validateBpmn(fixture, "workerBusinessError")
assertEquals(Map.of("worker", new WorkerBusinessError("businessError","ORDER_REJECTED","workerRejected","rejectionReview")), model.workerBusinessErrors())
assertThrows(UnsupportedOperationException, () -> model.workerBusinessErrors().clear())
assert old fixtures have empty map and unchanged timers/workers/cases
xml = fixture("worker-business-error.bpmn20.xml")
nativeModel = new BpmnXMLConverter().convertToBpmnModel(
    () -> new ByteArrayInputStream(xml), true, true)
assertEquals("ORDER_REJECTED", nativeModel.getErrors().get("businessError"))
boundary = assertInstanceOf(BoundaryEvent.class,
    nativeModel.getMainProcess().getFlowElement("workerRejected"))
errorDefinition = assertInstanceOf(ErrorEventDefinition.class,
    boundary.getEventDefinitions().getFirst())
// Flowable 8 converter normalizes ErrorEventDefinition boundary getter false;
// pinned ErrorEventDefinitionParseHandler still hardcodes interrupting runtime behavior.
assertFalse(boundary.isCancelActivity())
assertEquals("worker", boundary.getAttachedToRefId())
assertEquals("businessError", errorDefinition.getErrorCode())
assertEquals(1, boundary.getOutgoingFlows().size())
for each named exact invalid XML mutation:
  assertNotEquals(originalXml, mutatedXml, mutationName)
  assertThrows(IllegalArgumentException, mutationName)
assert source cancelActivity=false mutation rejects even though converter getter normalization is false
```

- Verification contribution: presence RED/A gate and typed model RED/GREEN; proves TEST-601-603.
- After this file: Pass P is one genuine assertion RED; final Pass T is typed and red until File 8, with old tests still expected green.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`

- Purpose: Provide the final compile-time immutable release metadata without enabling parser/runtime behavior.
- Symbols: nested `WorkerBusinessError`, field/map getter, final constructor parameter and empty defaults in every older overload.
- Repository evidence: current type owns immutable worker/wait/human/case/timer maps and package-private constructor chain.
- Dependencies and consumers: validator produces; worker service consumes; tests inspect. No Jackson/persistence/Bean consumer.
- Why now: typed tests need the exact final type after presence RED; this is the only intentional compile prerequisite A.
- Contract/signature changes: add `public record WorkerBusinessError(String errorId,String errorCode,String boundaryEventId,String targetElementId)` and `public Map<String,WorkerBusinessError> workerBusinessErrors()`; package-private final constructor adds the map last; old overloads delegate `Map.of()`.
- Input/output and state mapping: validator-owned identifiers/code -> copied immutable map; `xml()`/model digest bytes unchanged; null key/value/map fails through current immutable construction.
- Error and edge behavior: no caller construction grants execution authority; old BPMN/DMN/CMMN objects expose empty map; mutation fails.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — immutable value record under approved technical exception; no converter/JSON/time change.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` — exact semantic name, validator-only construction, record+Map.copyOf, JDK only, no JSON/time change, same package.
- Implementation pseudocode:

```java
private final Map<String, WorkerBusinessError> workerBusinessErrors;
all existing constructors -> final constructor(..., timerWaits, Map.of())
final constructor(..., Map<String,Duration> timerWaits, Map<String,WorkerBusinessError> errors) {
  preserve every current assignment and xml clone
  this.workerBusinessErrors = Map.copyOf(errors)
}
public Map<String,WorkerBusinessError> workerBusinessErrors() -> field
public record WorkerBusinessError(errorId,errorCode,boundaryEventId,targetElementId) {}
```

- Verification contribution: makes presence type/getter assertions compile/pass and enables typed tests; does not make behavior GREEN.
- After this file: production compiles with empty metadata for all current parser outputs; fixture is still absent until File 3.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-business-error.bpmn20.xml`

- Purpose: Provide one canonical ordinary-worker fixed business-error model for parser/converter/service/optional-PG tests.
- Symbols: process `workerBusinessError`; error `businessError` code `ORDER_REJECTED`; worker `worker`; boundary `workerRejected`; normal review/end and rejectionReview/rejectionEnd paths.
- Repository evidence: `worker-mapping.bpmn20.xml` owns the same external-worker attributes/mappings; fixtures use bounded literal XML.
- Dependencies and consumers: File 1 parser/converter, File 4 service model, File 7 optional PG.
- Why now: completes presence compile A before typed tests; no production grammar is changed yet.
- Contract/signature changes: test resource only.
- Input/output and state mapping: one definitions error/ref; interrupting boundary attached to ordinary worker; exactly one error outgoing target; no agent/message/subprocess/compensation.
- Error and edge behavior: uses only valid existing worker mapping and one newly approved error shape; no real credentials/data.
- Standards impact: `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact closed fixture; no runtime/config/DDL.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 9`, `Rule 11` — no script/dynamic values, exercises Specification/State flow, test resources only.
- Implementation pseudocode:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:tianji="urn:stellar-enclosure:tianji:worker:1"
             targetNamespace="https://stellar-enclosure.example/test">
    <error id="businessError" errorCode="ORDER_REJECTED"/>
    <process id="workerBusinessError" isExecutable="true">
        <startEvent id="start"/>
        <sequenceFlow id="toWorker" sourceRef="start" targetRef="worker"/>
        <serviceTask id="worker" flowable:type="external-worker" flowable:topic="riskCheck:1"
                     flowable:doNotIncludeVariables="true" tianji:topic="riskCheck"
                     tianji:schemaVersion="1" tianji:effectSafety="TRANSACTIONAL_RECEIPT">
            <extensionElements>
                <flowable:externalWorkerInParameter source="amount" target="requestedAmount"
                                                    tianji:type="INTEGER"/>
                <flowable:externalWorkerOutParameter source="approved" target="approved"
                                                     tianji:type="BOOLEAN"/>
            </extensionElements>
        </serviceTask>
        <sequenceFlow id="toReview" sourceRef="worker" targetRef="review"/>
        <userTask id="review"/>
        <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
        <endEvent id="end"/>
        <boundaryEvent id="workerRejected" attachedToRef="worker" cancelActivity="true">
            <errorEventDefinition errorRef="businessError"/>
        </boundaryEvent>
        <sequenceFlow id="toRejectionReview" sourceRef="workerRejected" targetRef="rejectionReview"/>
        <userTask id="rejectionReview"/>
        <sequenceFlow id="toRejectionEnd" sourceRef="rejectionReview" targetRef="rejectionEnd"/>
        <endEvent id="rejectionEnd"/>
    </process>
</definitions>
```

- Verification contribution: presence resource assertion and TEST-601-603/optional PG.
- After this file: presence method is GREEN with File 2; validator still rejects the fixture in typed RED.

**Compile-A gate before further test edits:** run the exact presence method. It must exit 0. A failure returns to Files 1-3; do not add typed tests or production B until the seam/fixture is valid.

**Return to File 1, Pass T:** append the typed parser/converter/negative matrix described above now that its final types compile. This is a second uncommitted edit to the same owned path, not a second file or commit.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerServiceTest.java`

- Purpose: Define the runtime transaction, fixed-code, postcheck, replay/fence and unchanged-branch behavior before service B.
- Symbols: constructor/fixture plus `declared_business_rejection_uses_model_code_and_finishes_atomically`; ID-only postcheck counterexamples; replay/hash; fence matrix; old branch matrix.
- Repository evidence: class currently only tests native availability, so controlled Mockito construction/TM fixture must follow existing ProcessControlServiceTest patterns without real DS/engine.
- Dependencies and consumers: public `FlowableWorkerService#fail`; mocked WorkerLeaseStore, ProcessReleaseService, VerifiedProcessModels, registry, JdbcTemplate/connection, ManagementService builders/queries.
- Why now: typed runtime RED must exist before File 9.
- Contract/signature changes: no public signature; tests compile against File 2 metadata getter.
- Input/output and state mapping: exact actor/lease/native/release/model/input -> fixed metadata -> native call -> ID-only old/new rows -> Finish BUSINESS_REJECTED diagnostic/hash.
- Error and edge behavior: terminal replay before native; changed diagnostic conflict; expired/foreign/drifted fences; old external present even wrong affinity; new wrong/multiple rows; quiet no-op; receipt failure rollback; undeclared/Agent/UNKNOWN/retry exact.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — controlled transaction/pattern/time/privacy proof; mocks do not fabricate runtime acceptance.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — reuse final types and handoffs, no new Bean/log/utility/JSON/config, Command+State patterns, Instant fields, same profile.
- Implementation pseudocode:

```java
fixture constructs service with mocked engine/TM and mockConstruction for internal current collaborators
actual TransactionTemplate callback uses controlled connection or equivalent rollback-observable fixture
mock model.workerBusinessErrors() -> {"worker": error("ORDER_REJECTED")}
mock exact live Lease, NativeJob, release/model/registry/input hash authority

@Test declared_business_rejection... {
  command.kind=BUSINESS_REJECTED; command.errorCode="CUSTOMER_INELIGIBLE"
  service.fail(ACTOR, command)
  verify(completionBuilder).bpmnError("ORDER_REJECTED")
  verify(completionBuilder,never).variables(any())
  verify externalQuery.jobId(JOB).listPage(0,2)
  verify jobQuery.jobId(JOB).listPage(0,2)
  verify leases.finish(... Finish(BUSINESS_REJECTED, expectedHash, null, BUSINESS_REJECTED, "CUSTOMER_INELIGIBLE"))
  assert commit and receipt BUSINESS_REJECTED
}

@Test old_external_wrong_affinity_is_still_presence() {
  old ID-only query returns same ID with wrong tenant/instance/definition/execution/element
  failure = assertThrowsExactly(IllegalStateException, service.fail(...))
  assert fixed safe message and null cause; verify no leases.finish; assert rollback
}

@Test new_executable_must_match_every_field() {
  for each wrong/missing/duplicate ID/tenant/instance/definition/execution/element/handler/config:
    assert same exact cause-free ISE (existing Advice => 503), never ProcessResourceNotFound; assert rollback
}

@Test terminal_exact_replay_precedes_native_and_changed_diagnostic_conflicts()
@Test expired_or_drifted_fences_never_call_bpmnError()
@Test undeclared_business_unknown_and_agent_remain_manual_and_retryable_is_unchanged() {
  controlled internal failAgentDispatch fixture may expose inconsistent nonnull business metadata
  requireMode/agentDispatch guard still calls legacy failure builder and finishes NEEDS_MANUAL
  verify never bpmnError
}
@Test receipt_failure_after_valid_native_conversion_rolls_back()
```

- Verification contribution: typed RED from current failure-builder/manual behavior, then TEST-604-610 GREEN.
- After this file: test compiles after A and fails assertions for missing B only; no real native/DB call.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/WorkerControllerTest.java`

- Purpose: Prove the existing REST JSON accepts the newly reachable existing receipt outcome without route/controller changes.
- Symbols: extend worker operation test with mocked `workers.fail` returning BUSINESS_REJECTED.
- Repository evidence: current standalone MockMvc test already sends fail and asserts NEEDS_MANUAL.
- Dependencies and consumers: unchanged WorkerController/C records/Jackson/advice/errors.
- Why now: compatibility control accompanies typed service RED.
- Contract/signature changes: none.
- Input/output and state mapping: unchanged Fail command -> mocked BUSINESS_REJECTED receipt -> HTTP 200/no-store JSON outcome and same IDs/time.
- Error and edge behavior: token remains absent from response/error; no extra field/permission/status.
- Standards impact: `MC-VALID-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — no main JSON type change.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` — existing validation/Jackson/Instant/profile only.
- Implementation pseudocode:

```java
when(workers.fail(ACTOR, businessCommand)).thenReturn(receipt(BUSINESS_REJECTED, DECIDED))
POST existing /worker-tasks/fail with existing JSON
expect 200, Cache-Control no-store, exact task/occurrence/outcome/UTC time
assert response excludes lease token and no new fields
```

- Verification contribution: TEST-611; expected to stay GREEN during typed RED.
- After this file: existing external contract compatibility is explicit; production Controller unchanged.

#### File 6 — `MODIFY stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java`

- Purpose: Prove the existing W consumer reports and accepts the exact business-rejected receipt without main changes or repeated business execution.
- Symbols: add `business_rejection_uses_bounded_diagnostic_and_accepts_terminal_receipt` or extend failure classification test.
- Repository evidence: current poller catches WorkerHandlerFailureException, builds Fail command and `validReceipt` already accepts BUSINESS_REJECTED/NEEDS_MANUAL.
- Dependencies and consumers: mocks WorkerClient/WorkerExecutionStore/scheduler; no HTTP/listener.
- Why now: closes repository consumer compatibility before B.
- Contract/signature changes: none.
- Input/output and state mapping: handler throws BUSINESS_REJECTED + diagnostic -> exact Fail command; client returns BUSINESS_REJECTED -> one acknowledged terminal path.
- Error and edge behavior: diagnostic regex exact; no retry after valid receipt; no raw exception/token logging assertion where current test hooks allow.
- Standards impact: `MC-VALID-001`, `MC-LOG-001`, `MC-JSON-001`, `MC-PATTERN-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — no Worker main code.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11` — existing contract validation, utilities, Jackson-free mock boundary, Command behavior/time/profile preserved.
- Implementation pseudocode:

```java
store.execute(...) throws WorkerHandlerFailureException(BUSINESS_REJECTED,"CUSTOMER_INELIGIBLE")
client.fail(capture()) returns receipt(BUSINESS_REJECTED)
run one captured poll callback
assert captured kind/code and exactly one fail acknowledgement
verify business store executes once; no completion call
```

- Verification contribution: TEST-611; expected GREEN before/after B.
- After this file: W main remains unchanged with focused positive compatibility proof.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`

- Purpose: Add future opt-in native/PG success and rollback acceptance source without executing it in this Step.
- Symbols: `verifyWorkerBusinessErrorRouting` invoked only inside the already `@EnabledIfSystemProperty` class.
- Repository evidence: existing fresh-loopback test owns release/start/acquire/fail, injected V4 trigger rollback and native job assertions.
- Dependencies and consumers: new fixture/model/service; actual PG/Flowable only when a user later supplies the existing property.
- Why now: source compiles with final APIs and documents the runtime acceptance path; it is never selected now.
- Contract/signature changes: test only.
- Input/output and state mapping: publish profile -> start/acquire -> fail -> V4 BUSINESS_REJECTED; separately await actual error branch; injected V4 update failure -> external job/LEASED restored.
- Error and edge behavior: no assumption that receipt alone proves branch; no automatic clean/start; loopback/fresh schema guard remains exact.
- Standards impact: `MC-VALID-001`, `MC-UTIL-001`, `MC-PATTERN-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — explicitly RuntimeUnverified.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 9`, `Rule 10`, `Rule 11` — real future handoff/transaction, existing APIs, State/Command pattern, Instant, existing test package.
- Implementation pseudocode:

```java
private verifyWorkerBusinessErrorRouting(...) {
  publish validated fixture under fixed release; start and acquire ordinary worker
  receipt = service.fail(BUSINESS_REJECTED,"CUSTOMER_INELIGIBLE")
  assert V4 row BUSINESS_REJECTED with diagnostic/result hash
  independently await reviewed rejection-path native fact; do not derive it from receipt
  for second run install existing-style V4 update failure trigger
  assert service.fail throws; assert external job still exists and V4 state LEASED; remove trigger
}
```

- Verification contribution: PG-601 source/testCompile only; future runtime gate remains user-controlled.
- After this file: optional scenario compiles; no PG/app/engine has run and typed RED still belongs to Files 1/4.

**Typed RED gate before production B:** run exact finite selectors excluding ProcessServerPostgresTest. Expected nonzero only from new ProcessModelValidatorTest/FlowableWorkerServiceTest assertions; WorkerController/W controls and all old selected methods pass. Any error or unrelated failure returns to Files 1-7/A, not production.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`

- Purpose: Implement the reviewed closed fixed-error grammar and derive immutable metadata using the existing graph proof.
- Symbols: BPMN child/attribute maps; error declarations/refs/boundaries; implicit edge; final constructor call.
- Repository evidence: current parser already owns definitions/messages, boundary timers, worker/Agent classification and DAG/path caps.
- Dependencies and consumers: File 2 output, File 3 XML, File 1 tests, FlowableWorkerService runtime.
- Why now: typed model RED fixes every accepted/rejected shape.
- Contract/signature changes: no public validator method; validateBpmn output map becomes populated for approved profile.
- Input/output and state mapping: error XML -> `WorkerBusinessError`; XML bytes/hash remain authority; old models -> empty map.
- Error and edge behavior: exact Spec §7.3.1 matrix; duplicate codes across different workers allowed, but declarations/refs one-to-one and one boundary per worker; message-wait worker rejected after message classification. Do not claim parser proves registry kind; unchanged ProcessReleaseService/WorkerTopicRegistry gates do.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001` — no Bean/JSON/config/DB.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — exact type, closed validator, immutable output, existing APIs, no wire/config/time, Specification pattern and package.
- Implementation pseudocode:

```java
BPMN_CHILDREN definitions += error; boundaryEvent accepts timerEventDefinition or errorEventDefinition
BPMN_ATTRIBUTES error={id,errorCode}; errorEventDefinition={id,errorRef}
parse <=16 declarations: identifier id, closed errorCode, unique id; collect refs
first node pass:
  boundary must cancelActivity=true and exactly one definition
  timer branch unchanged
  error branch records boundaryId, attachedToRef, errorRef
existing sequence flow/node shape validation unchanged (boundary explicit incoming=0,outgoing=1)
after message/case wait classification:
  require every error declaration referenced exactly once
  require attachment in workers and not waitingWorkers/internal Agent
  require one error boundary per worker
  target = boundary.next.getFirst.id
  add implicit worker.next(boundary); boundary.previous(worker); boundary.incoming++
  metadata[workerId] = WorkerBusinessError(errorId, code, boundaryId, target)
run existing topological traversal/path caps; reject cycle/disconnected/excess
return ValidatedProcessModel(... timers, Map.copyOf(metadata))
```

- Verification contribution: makes TEST-601-603 model/converter matrix GREEN.
- After this file: profile publication is valid in the uncommitted tree, but runtime remains RED until File 9; do not commit yet.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java`

- Purpose: Implement the declared ordinary-worker branch, exact public postcondition and atomic existing V4 outcome.
- Symbols: `fail`, `verifyJob`, private postcheck, private `VerifiedJob` record field.
- Repository evidence: current fail already owns replay/fences/TX and all old branches; resolved Flowable 8 API/query fields are sufficient.
- Dependencies and consumers: current WorkerController/internal Agent seam; registry/releases/models/leases/ManagementService; Files 4-7 tests.
- Why now: validator metadata is authoritative and typed runtime RED fixes ordering/outcomes.
- Contract/signature changes: public methods unchanged; private VerifiedJob adds nullable WorkerBusinessError; private helper only.
- Input/output and state mapping: model code -> native handler config; diagnostic -> existing result hash/V4 failure_code; exact V4 outcome BUSINESS_REJECTED; no variables.
- Error and edge behavior: replay first; current fences exact; declared branch only when kind BUSINESS_REJECTED, `agentDispatch=false`, `registration.internalAgentDispatch()=false`, metadata nonnull; `requireMode`, `registry.acceptsModel` and linked-wait/internal equality remain before selection; two ID-only listPage(0,2) queries. New postcheck mismatch/no-op calls only private `businessErrorUnavailable()` (fixed safe message, no cause, ISE -> existing 503). It never calls old `unavailable()`/404. Receipt/native exceptions keep existing mappings; every failure rolls back; old branches are byte-semantic unchanged.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — reuses Command/State patterns; no dependency/Bean/log/JSON/config/DDL.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — existing types/fences, no new carrier/converter/DI/log/utility/wire/config/time, selected patterns, same worker package.
- Implementation pseudocode:

```java
VerifiedJob verifyJob(...) {
  preserve all current release/model/registry/process/correlation/input validation
  preserve registry.acceptsModel and linkedWait == registration.internalAgentDispatch()
  error = model.workerBusinessErrors().get(job.elementId())
  return VerifiedJob(existing fields, inputs, error)
}

fail transaction preserves find -> registry/mode -> occurrence -> terminal replay -> lockNative -> leases.lock -> second replay -> verifyLease -> inputHash
requireMode(registration, agentDispatch) remains authoritative
if command.kind == BUSINESS_REJECTED && !agentDispatch && !registration.internalAgentDispatch() && verified.businessError != null:
  management.createExternalWorkerCompletionBuilder(job.id, job.owner)
            .bpmnError(verified.businessError.errorCode) // no variables()
  oldRows = management.createExternalWorkerJobQuery().jobId(job.id).listPage(0,2)
  if !oldRows.isEmpty(): throw businessErrorUnavailable()
  newRows = management.createJobQuery().jobId(job.id).listPage(0,2)
  require size==1
  require new row independently equals trusted id/tenant/instance/definition/execution/element
  require handlerType=="external-worker-complete" && config=="error:"+modelCode
  any failed new proof -> throw businessErrorUnavailable()
  finish = Finish(BUSINESS_REJECTED,resultHash,null,BUSINESS_REJECTED,command.errorCode)
else:
  execute current retry/failure-builder logic exactly
return receipt(leases.finish(...finish))

private static IllegalStateException businessErrorUnavailable() {
  return new IllegalStateException("Worker business error scheduling is unavailable") // no cause
}
```

- Verification contribution: makes TEST-604-610 GREEN; consumer tests remain GREEN; PG source compiles.
- After this file: complete source behavior exists in the uncommitted tree; proceed to focused GREEN before documentation/evidence.

#### File 10 — `CREATE docs/egon/spec/2026-11-05-04-48-worker-business-error-routing.md`

- Purpose: Install the exact Root-reviewed primary design as repository authority.
- Symbols: Template 9 chapters, REQ-601-615, SHA authority.
- Repository evidence: frozen `/tmp/tianji-worker-business-error-spec.md` SHA `c5d9db...b802`; current spec convention.
- Dependencies and consumers: this Plan, implementation/evidence, later roadmap.
- Why now: copy only after B/GREEN proves no execution discovery invalidated the Spec; semantic drift returns to Root before copy.
- Contract/signature changes: documentation only; Status Review, not Accepted.
- Input/output and state mapping: byte-exact frozen draft -> final path.
- Error and edge behavior: no hidden acceptance/runtime claim; links resolve from final path.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — full traceability/scope/blocker evidence.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — exact matrix retained.
- Implementation pseudocode:

```text
copy frozen reviewed Spec bytes to the exact final path
recompute SHA-256 and compare the reviewed value
run strict validator; do not rewrite approved decisions during execution
```
- Verification contribution: strict Spec validator and hash check.
- After this file: primary Spec exists at its governed path, still Review.

#### File 11 — `CREATE docs/egon/plan/2026-11-05-05-20-worker-business-error-routing.md`

- Purpose: Install the Root-reviewed executable one-Step Plan.
- Symbols: Template 5, one Step, exact paths/commands/checks.
- Repository evidence: this `/tmp` Plan and current plan convention.
- Dependencies and consumers: Root execution/final audit only.
- Why now: final bytes can include only reviewed execution discoveries; source behavior already GREEN.
- Contract/signature changes: documentation only; Review, not source GO by itself.
- Input/output and state mapping: reviewed temporary Plan -> final path.
- Error and edge behavior: any material deviation returns to Spec/Plan review.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — all 17 checks retained.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — exact Step/file obligations retained.
- Implementation pseudocode:

```text
copy reviewed Plan bytes to the exact final path
update only metadata explicitly approved by Root
strict-validate final-path links, files, requirements, Rules and Manual Checks
```
- Verification contribution: strict Plan validator.
- After this file: Plan is repository-visible and reviewable, not implementation evidence.

#### File 12 — `CREATE docs/egon/evidence/2026-11-05-worker-business-error-routing.md`

- Purpose: Record actual presence/typed RED, compile A, GREEN, regression, path/hash and unverified runtime boundaries before commit.
- Symbols: exact commands/log times/results, test selectors/counts, changed paths, immutable migrations, Spec/Plan hashes.
- Repository evidence: existing evidence documents distinguish source/static/skip/runtime.
- Dependencies and consumers: actual Root-run gates only; final audit/user review.
- Why now: evidence must report observed results, never predictions.
- Contract/signature changes: none.
- Input/output and state mapping: command outputs/diffs/hashes -> factual Markdown.
- Error and edge behavior: record failed dependency/fixture gates honestly; never convert missing PG/property skip into acceptance.
- Standards impact: `MC-LOG-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` and REQ-612/614/615.
- Literal rule enforcement: `Rule 5`, `Rule 6`, `Rule 7`, `Rule 10`, `Rule 11` — no secrets/raw bodies, exact static scope/time/runtime boundary/profile.
- Implementation pseudocode:

```text
record actual RED method and assertion-only failure; compile-A PASS; typed RED exact missing behavior
record focused/final selectors, exit codes, test pass/fail/skip counts and timestamps
record no ProcessServerPostgresTest/HttpProcessClientTest/listener/DB/app/browser/native executor
record exact 12 paths; no POM/YAML/permission/SQL/C/CL/W main change
record all 25 existing SQL hashes unchanged and no V10
record Spec/Plan/evidence SHA and HEAD/commit after commit
state PG/native async/IAM/deployment RuntimeUnverified; compensation/recovery/S10/S11/T3-12 Required
```

- Verification contribution: final scope/manual-check evidence.
- After this file: all Step paths are ready for exact validation, staging and one commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` for Java/tests; `/Users/mario/SelfProject/Stellar-Enclosure` for document validators.
- Verification command: exact ladder in §8; Root runs each sequentially and never selects native PG or listener tests.
- Expected result: presence RED and typed RED fail only for stated assertions; compile A/focused GREEN/final regression/docs/static gates exit 0; no unexpected skip in selected non-PG tests.
- Failure returns to: File 1/2/3 for presence/compile; Files 1/4-7 for typed fixture errors; File 8 for grammar/converter failures; File 9 for service/TX/native failures; Files 10-12 for docs/evidence; Spec review for semantic drift.
- Completion criteria: every REQ has GREEN/static evidence, all old bounded regressions pass, exact scope/hashes match, evidence retains runtime gaps, and one commit contains only declared paths.
- Rollback: before commit revert only the uncommitted 12 paths; after commit use a normal path-limited revert commit. No database/config rollback exists. Stop publishing the new fixture/profile if source is reverted; native jobs already scheduled remain Flowable-owned.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`, `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-business-error.bpmn20.xml`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/WorkerControllerTest.java`, `stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java`, `docs/egon/spec/2026-11-05-04-48-worker-business-error-routing.md`, `docs/egon/plan/2026-11-05-05-20-worker-business-error-routing.md`, `docs/egon/evidence/2026-11-05-worker-business-error-routing.md`.
- Commit: `feat(process): route fixed worker business errors`

## 8. Test, Validation, and Quality Gates

Use this common command prefix from the `tianji-service` root:

```text
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| 0 Baseline | tianji-service | `git rev-parse HEAD`; `git status --short`; exact path/hash inventory | baseline | expected HEAD/clean; 25 SQL hashes captured | stop/re-audit | all; static |
| 1 Presence RED | tianji-service | common prefix + `'-Dtest=ProcessModelValidatorTest#worker_business_error_symbols_and_fixture_are_present'` | one test | nonzero, exactly missing getter/type/resource assertion failure; no compile/error | File 1 | 601/614; static module |
| 2 Compile-A | tianji-service | same one-method command after Files 2-3 | seam/fixture | exit 0; one method passes; all tests compile | Files 1-3 | 601-604/614; static module |
| 3 Typed RED | tianji-service | common prefix + `-Dtest=ProcessModelValidatorTest,FlowableWorkerServiceTest,WorkerControllerTest,RemoteWorkerPollerTest` | typed feature/controls | nonzero only new model/service assertion failures; zero errors/fixture failures; controls pass | Files 1/4-7/A | 601-614; module mocks/converter |
| 4 Focused GREEN | tianji-service | same four selectors after Files 8-9 | feature | exit 0; every selected test passes; no PG/listener | owning main/test file | 601-614; module |
| 5 Contract/static consumer | tianji-service | common prefix + `-Dtest=OrchestrationContractTest,WorkerControllerTest,RemoteWorkerPollerTest,ProcessModelValidatorTest,FlowableWorkerServiceTest` | C/PS/W | exit 0; unchanged contract/consumer + feature tests pass | Step 1 | 601-614; module |
| 6 Bounded predecessor regression | tianji-service | common prefix + `-Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,ProcessControlContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,ProcessExecutionServiceTest,FlowableHumanTaskServiceTest,CaseControllerTest,CaseStorageConfigurationTest,ProcessReleaseServiceTest,FlowableAgentWaitInspectorTest,ProcessModelValidatorTest,FlowableWorkerServiceTest,WorkerControllerTest,RemoteWorkerPollerTest` | cancellation/Case/task/release/worker | exit 0; named tests pass; no HttpProcessClientTest or PG selector | Step 1 owning files | 610-615; bounded regression |
| 7 Docs strict | Stellar-Enclosure outer root | `python3 .agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py tianji-service/docs/egon/spec/2026-11-05-04-48-worker-business-error-routing.md --strict`; analogous writing-plan `validate_plan.py ... --strict` | Spec/Plan | both PASS, links/trace exact | Files 10-11 | all; static |
| 8 Static scope | tianji-service | `git diff --check`; inspect `git diff --name-only`; compare all existing SQL SHA-256; search forbidden POM/YAML/permission/C/CL/W-main/ACT-write/executeJob/log changes | exact paths | zero whitespace errors; only 12 paths; old SQL unchanged; forbidden changes absent | Step 1 or Spec | 611-615; static |
| 9 Optional runtime | user-controlled future | do not run in this Step; later explicit loopback PG property may select ProcessServerPostgresTest | PG/Flowable async | Unverified now; if later run, separate actual receipt/scheduled/propagated/rollback facts | future acceptance | 606-608/612/614; runtime |

`ProcessServerPostgresTest` is compiled by test compilation but never named in a selector. `HttpProcessClientTest` is never selected because its current harness opens a loopback listener. No command starts an application, server, native executor, database, browser or network client.

## 9. Migration, Compatibility, Rollout, and Rollback

- Migration/schema: N/A — Spec §11 proves no relational/schema/key/index change. Do not create V10, edit V1-V9, use generator or append a DDL journal.
- Contract: exact C/CL/W production and JSON remain. New server outcome was already allowed; finite MVC/W tests prove it.
- Model rollout: only releases published with the new fixed profile route business rejection. Old releases retain empty metadata/manual semantics. Deploy compatible PS code before publishing such a release.
- Deployment/config: no key/permission/feature activation. This Plan does not register/enable anything.
- Application rollback: normal source revert is safe before new profiles. After profiles/native jobs exist, keep a compatible binary or stop new instances and handle in-flight jobs explicitly; do not call them manually compensated.
- Runtime acceptance: PG/native async propagation is still required and separate. Evidence must state default-skip/compile-only honestly.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-601` | §4/§7.3.1 | Step 1 | 1-3,8 | TEST-601/602; gates 1-6 | exact one profile |
| `REQ-602` | §4/§7.3.1 | Step 1 | 1,3,8 | TEST-602/603 | graph/converter proof |
| `REQ-603` | §4/§7.3.1 | Step 1 | 1,4,8,9 | TEST-602/610 + ProcessReleaseServiceTest | prohibited profiles reject/manual; registry gate preserved |
| `REQ-604` | §4/§7.3.2 | Step 1 | 2,4,9 | TEST-604 | model code only |
| `REQ-605` | §4/§7.3.2 | Step 1 | 4,9 | TEST-605/609 | replay/fence order |
| `REQ-606` | §4/§7.3.2 | Step 1 | 4,7,9 | TEST-606/607/PG-601 | ID-only actual conversion proof |
| `REQ-607` | §4/§7.3.4 | Step 1 | 4,9 | TEST-606/607 | quiet no-op fails closed |
| `REQ-608` | §4/§7.3.3 | Step 1 | 4,7,9 | TEST-608/PG-601 | atomic rollback |
| `REQ-609` | §4/§7.3.3 | Step 1 | 4,9 | TEST-609 | exact replay/conflict |
| `REQ-610` | §4/§10.3 | Step 1 | 4,9 | TEST-610 + regression | old branches exact |
| `REQ-611` | §4/§16 | Step 1 | all scoped paths | gates 5/6/8 | no main/schema/config drift |
| `REQ-612` | §4/§7.3/§9 | Step 1 | 4-7,9,12 | TEST-611/PG source/evidence | accepted/scheduled wording |
| `REQ-613` | §4/§15 | Step 1 | 4,9,12 | TEST-604/607/static | privacy/routing split |
| `REQ-614` | §4/§14 | Step 1 | tests/fixture/docs | gates 1-9 | finite proof/runtime boundary |
| `REQ-615` | §4/§16/§18 | Step 1 | 10-12 | docs/static | roadmap remains Required |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `RISK-601` | native void call can quietly no-op | File 9 | pinned DefaultJobManager bytecode | Step 1 | Closed by ID-only postcheck/tests |
| `RISK-602` | async propagation later fails | File 7/12 | handler executes later | Flowable/operator | Tracked nonblocking runtime follow-up; no receipt rewrite |
| `RISK-603` | current baseline drifts before GO | all | Plan drafted at c731f3f | Root | Closed by mandatory prerequisite recheck; drift stops execution |
| `RISK-604` | mock/source evidence overstated as PG acceptance | Files 7/12 | PG never selected | Root | Closed by gate/evidence wording |
| `RISK-605` | full compensation/recovery remains absent | docs | roadmap T3-10 | Product/Root | Tracked Required follow-up; explicitly not a Plan blocker |

No open Spec/user decision blocks Plan review. Source execution remains blocked only by the explicit later Root source GO, not by a design ambiguity.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

The one Step covers every reviewed fixed-profile, diagnostic separation, fence/replay, public ID-only proof, atomic V4, compatibility, no-runtime and future-roadmap requirement. Exclusions remain literal.

### 12.2 Spec consistency

The Plan uses exactly the Spec's files/elements and adds no behavior, contract, schema, config, dependency, permission, layer, recovery or runtime claim. The necessity audit found no fetch-then-forward or overdesign. `PLAN-CLAR-001` through `PLAN-CLAR-003` only replace abbreviated fixture/test syntax and assign disjoint execution ownership; they preserve all approved semantics and the single Step/commit.

### 12.3 Repository executability

All paths/symbols/APIs/build selectors exist at c731f3f. The presence/A/typed/B sequence is explicit, the one repeated test path is owned within the same uncommitted Step, commands have working directories/results/return points, and the commit is exact/path-limited.

### 12.4 Test and release completeness

Presence and typed RED precede behavior; focused GREEN precedes bounded regression/docs/evidence. PG/listener/runtime are excluded. No migration/activation exists. Rollback and compatible model publication order are explicit.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | §4.7/5, predecessor exception | exact technical vertical only | None |
| `MC-REUSE-001` | Applicable | PASS | reuse ledger | current model/validator/service/V4/Flowable public API reused | None |
| `MC-DEP-001` | Applicable | PASS | POM/tree and §4.5 | no dependency/custom replacement | None |
| `MC-NAME-001` | Applicable | PASS | Files 1-2/8 | WorkerBusinessError exact immutable role; no ambiguous type | None |
| `MC-VALID-001` | Applicable | PASS | Files 1/4/8/9 | every model/runtime handoff/fence/negative test exact | Root technical validation boundary already approved |
| `MC-MODEL-001` | Applicable | PASS | File 2 | nested immutable record/map and constructor compatibility | Root value exception already effective |
| `MC-CONVERT-001` | Not applicable | N/A | §4.7/File 2 | no cross-layer conversion/Converter | None |
| `MC-LOG-001` | Applicable | PASS | Files 4/6/9/12 | no new log; no code/body/token/detail exposure | Root technical-class exception already effective |
| `MC-BEAN-001` | Not applicable | N/A | constructor/factory/config untouched | no Bean/injection/wiring change | None |
| `MC-UTIL-001` | Applicable | PASS | POM/import plan | JDK/current Spring/Flowable only | None |
| `MC-JSON-001` | Not applicable | N/A | File 5/Spec §9 | external JSON unchanged; regression only | None |
| `MC-TIME-001` | Applicable | PASS | §4.9/Files 4/7/9 | existing Instant/DB-clock expiry/decidedAt preserved | PG precision runtime Unverified |
| `MC-CONFIG-001` | Not applicable | N/A | change tree/static gate | no config/profile key | None |
| `MC-PATTERN-001` | Applicable | PASS | §4.7/Files 8-9 | existing Specification + Command/durable receipt + Flowable State | None |
| `MC-SCOPE-001` | Applicable | PASS | §5/Step commit paths | 3 PS main + scoped tests/docs only | None |
| `MC-TEST-001` | Applicable | PASS | §7-8 | genuine presence/A/typed/B/GREEN plus bounded regression | None |
| `MC-BLOCKER-001` | Applicable | PASS | §6/11/all MC rows | no unknown/failed/open design gate | source GO remains separate approval |

### 12.6 Final verdict

PASS — Ready for user review

This Review Plan is internally executable but is not Accepted and does not authorize source edits, Maven, database, application, engine, listener, browser, network, staging or commit until Root explicitly gives source GO.
