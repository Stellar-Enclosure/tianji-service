# Fixed Worker business-error routing evidence

## Step 48 / Plan Step 1 — Verified for atomic feature commit

- Baseline c731f3fda692d24115c6aaff36f19192500e537a, clean902 tracked files before first test edit. Exact12 paths and25 existing SQL are locked under /tmp/tianji-step48-{paths,baseline,lock}.json; all17 Manual Checks/ten Literal Rules independently recorded.
- Root fully reviewed the843-line Spec plus controlled clarifications and the796-line Plan plus valid XML/converter/disjoint-owner clarifications before source GO. Latest frozen Spec c5d9dbaa02b737e8395a7a9df90a85f0c1f60eb4f075719a5db536607cf3b802 and Plan0d88ab951f7f9fa963dd402b158462029ec991c232a0add2e1d0332127d7631d retain the same15 requirements and one atomic feature commit.
- Existing Process technical profile, plain JDBC/shared Flowable transaction, immutable model values and explicit constructors remain. Backend generator unsupported/not invoked; no dependency/framework/module/DDL/MP/Outbox rework, no C/CL/W main or configuration/permission change.
- Source-only validation uses JDK25, isolated offline settings, skipITs=true, finite selectors and disabled OTel exporters. ProcessServerPostgresTest compiles only and is never named; HttpProcessClientTest/listeners, database, engine/App startup, network/provider, browser and GUI are not executed.
- Presence RED: /tmp/tianji-worker-business-error-presence-red.log,1 test/one assertion failure/zero errors or skips,20.073 s,finished2026-11-05T05:43:45+08:00; missing getter/type/resource only, existing public native API check passed.
- Compile-A: /tmp/tianji-worker-business-error-compile-a.log,1 passed/zero failures/errors/skips,18.985 s,finished05:46:16+08:00. Immutable carrier/getter/default-map/fixture existed; parser/runtime behavior still absent.
- Typed model RED: /tmp/tianji-worker-business-error-model-red.log,W11+PS23=34 tests/one genuine model assertion failure/33 passed/zero errors or skips,21.919 s,finished05:55:28+08:00. Runtime production B remained blocked until its own RED.
- Typed runtime RED: /tmp/tianji-worker-business-error-runtime-red.log, runtime15/6 genuine assertion failures/9 controls passed/zero errors; total25.739 s,finished06:01:46+08:00. Model had two separately investigated fixture assumptions, not counted as new behavior RED. Runtime B was released from its own valid class-specific proof.
- Model fixture corrections: duplicate-boundary exact whitespace replacement had been a no-op; named independent mutations now assert changed XML before validation. Pinned Flowable converter normalizes Error boundary cancelActivity=false while engine parser hardcodes interrupting behavior true; original XML true admission remains mandatory and false remains a negative case. These are static library/fixture corrections, not model-profile relaxation.
- Native exception preservation RED: /tmp/tianji-worker-business-error-native-exception-red.log,W11+PS39=50 tests/one genuine identity/type assertion failure/49 passed/zero errors or skips,23.363 s,finished06:09:27+08:00. Root removed only an unnecessary broad catch afterward: explicit new proof mismatches stay fixed cause-free ISE/503, native/query/receipt errors preserve original type/cause/current mappings.
- Focused contract GREEN: /tmp/tianji-worker-business-error-contract-green.log,C7+W11+PS39=57 passed/zero failures/errors/skips,24.735 s,finished06:12:14+08:00. Real mocked-connection commit/rollback and public acquire-derived input/lease hashes are verified without mirroring private hash logic.

Final26-class regression and static/manual gates are recorded below.

## Final regression and source evidence

- Final predecessor regression: /tmp/tianji-worker-business-error-regression.log, C19+CL18+W11+PS218 = **266 passed**, zero failures/errors/skips; BUILD SUCCESS38.596 s,finished2026-11-05T06:15:13+08:00. Exactly the26 reviewed selectors ran; no PG/listener selector.
- New feature coverage: ModelValidator19, WorkerService16, WorkerController4, RemoteWorkerPoller11. The separate focused C Orchestration7 control passed in the57-test contract gate. Cancellation/Case/HumanTask/release/Agent-wait/permission/storage regressions remain GREEN.
- Independent final review maps every REQ-601 through615 to source or finite proof without a remaining source blocker. Real PostgreSQL, native asynchronous propagation and live IAM remain RuntimeUnverified.
- Exact scope:12 paths, only3 PS production files,894 excluded tracked SHA values unchanged, all25 existing SQL byte-identical. No C/CL/W main, POM, configuration, permission, migration, generated source or unrelated formatting change.
- The unchanged bidirectional release registry and runtime requireMode/linked-wait/acceptsModel gates remain authoritative. Metadata alone cannot turn internal Agent dispatch into an ordinary Worker. Even controlled inconsistent internal metadata uses the old manual branch.
- The model now admits only one fixed declaration/ref/interrupting boundary per ordinary Worker; raw source XML must say cancelActivity=true. Error virtual edges enter the same bounded DAG traversal. Old XML bytes/hash inputs, timer/Case/Worker metadata and constructor signatures remain.
- The new service branch uses only model code; caller diagnostic remains the existing result hash/V4 field. A void native return is accepted only after two trusted-ID-only bounded queries prove actual old-row absence and exact executable job affinity/handler/config.
- Native scheduling and V4 finish remain in the existing local transaction. Quiet no-op or wrong proof produces the existing safe dependency503 via one fixed cause-free ISE; original native/query/receipt exceptions retain identity/context and old mappings. Exact replay precedes all native access and never schedules twice.
- No claim is made that bpmnError is synchronous: receipt means accepted/scheduled native work. A separate optional PG source case observes later rejectionReview and injected V4 rollback; it is compiled only, never selected or run in this Step.

## Pinned converter/engine interpretation

Static javap on Flowable8.0.0 showed BoundaryEventXMLConverter.convertXMLToElement normalizes the ErrorEventDefinition model getter to false after child parsing. ErrorEventDefinitionParseHandler independently hard-passes true to the activity behavior factory; BoundaryEventParseHandler returns without applying the getter again; BoundaryEventActivityBehavior reads its own interrupting field. Source admission remains literal XMLtrue, while the converter test asserts its actual normalized false result. This is pinned static evidence, not executed error propagation.

- Converter JAR SHA-256: b3af6e161f2ed5153acf5cff3d2e29c2741dd1edc1baed21a72aa2408a8d6826.
- Engine JAR SHA-256: e81615c4c2f7332c184f31d362e34a807c6c745e6b9da745e73b2d3adb4f5850.

## Requirement conformance

| Requirement | Source / finite evidence | Result / boundary |
| --- | --- | --- |
| REQ-601/602 | declaration/ref/worker uniqueness, XMLtrue gate, virtual edge/DAG/128-256 caps and converter matrix | Source satisfied |
| REQ-603 | message-wait exclusion + unchanged registry bidirectional classification + explicit ordinary runtime guard | Source satisfied |
| REQ-604 | model-code versus different caller diagnostic test | Source satisfied |
| REQ-605/609 | public acquire-derived lease/hash, authority mutations, terminal replay before native, changed diagnostic conflict | Source satisfied |
| REQ-606/607 | ID-only bounded old/new queries, old wrong-affinity presence, each new field/duplicate/missing proof and exact cause-free ISE | Source satisfied |
| REQ-608 | real mocked-connection TX commit/rollback and receipt-failure participant ordering | Source satisfied; real PG atomicity unverified |
| REQ-610/611 | old retry/manual/unknown/internal/undeclared controls;266 regressions; unchanged contracts/V4/config/POM/SQL | Preserved |
| REQ-612/613 | asynchronous receipt wording, no variables/new logs/private payload, fixed bounded native code | Source satisfied |
| REQ-614 | finite selectors and guarded compile-only PG source | Source evidence satisfied; native async/PG/IAM unverified |
| REQ-615 | no original-business-receipt/compensation/deadletter/recovery implementation claim | Remaining roadmap Required |

## Final Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Exact existing release/worker technical vertical and3 production files | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-REUSE-001 | Applicable | PASS | Current model/validator/service/V4/Flowable public APIs reused | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-DEP-001 | Applicable | PASS | All POM/dependency SHA values unchanged | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-NAME-001 | Applicable | PASS | Nested immutable WorkerBusinessError in current Model; no new layer/carrier | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-VALID-001 | Applicable | PASS | Closed XML/graph and current actor/native/lease/model/input/registry fences; exact postcheck | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-MODEL-001 | Applicable | PASS | Map.copyOf/record/old constructors and XML digest inputs preserved | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-CONVERT-001 | Not applicable | N/A | No cross-layer semantic carrier or Converter added | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-LOG-001 | Applicable | PASS | No new log; code/body/token/exception details stay out of new output | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-BEAN-001 | Not applicable | N/A | No constructor/Bean/injection/configuration change | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-UTIL-001 | Applicable | PASS | JDK/current Spring/Flowable only | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-JSON-001 | Not applicable | N/A | Existing C/CL/W DTO/enums and JSON unchanged; compatibility controls pass | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-TIME-001 | Applicable | PASS | Existing Instant and DB-clock strict lease/receipt/replay time preserved | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-CONFIG-001 | Not applicable | N/A | No environment/profile/property/permission change | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-PATTERN-001 | Applicable | PASS | Existing Command+receipt, native State and closed validator Specification extended | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-SCOPE-001 | Applicable | PASS | 12 exact paths/894 excluded SHA/25 immutable SQL | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-TEST-001 | Applicable | PASS | Genuine presence/model/runtime/exception RED and57+266 finite successful gates | Reviewed scope preserved | Existing technical profile; runtime unverified |
| MC-BLOCKER-001 | Applicable | PASS | Strict docs and independent source review; no remaining source blocker | Reviewed scope preserved | Existing technical profile; runtime unverified |

## Literal Rules

| Rule | Applicability | Status | Evidence / approved disposition |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Current behavior names and semantic immutable nested value |
| Rule 2 | Applicable | PASS | Current C constructors, XML/metadata validation and runtime source/fence boundary retained |
| Rule 3 | Applicable | PASS | Immutable value record/map and existing technical constructors; no duplicate DTO/converter |
| Rule 4 | Applicable | PASS | Approved final-class/plain-JDBC/explicit-factory technical profile retained; no new logger |
| Rule 5 | Applicable | PASS | JDK/current libraries only |
| Rule 6 | Not applicable | N/A | No production JSON/enum representation change; already-declared outcome reused |
| Rule 7 | Not applicable | N/A | No configuration/profile change |
| Rule 9 | Applicable | PASS | Existing Command/receipt and native State/validator Specification; no new Strategy hierarchy |
| Rule 10 | Applicable | PASS | java.time values retained; no new Date/clock or chronology claim |
| Rule 11 | Applicable | PASS | Effective Spec preserves established PS technical vertical; no CRUD/entity/soft-delete/event/MP/DDL redesign |

Strict final-path Spec/Plan and diff checks passed. The optional PostgreSQL class was not selected; compilation cannot prove its fixture behavior, transactional rollback or asynchronous propagation. No database, engine/App, listener, provider network, browser or GUI was started. No permission/catalog activation, deployment, publication, credential setup or real business side effect occurred.

This is one bounded business-error source slice. Original-action business receipt verification/compensation, failed/dead-letter query and governed recovery, S10/S11, T3-12, the T3-09 native templates/task decomposition and T4 remain Required. It does not complete T3-10 or the overall upgrade goal.
