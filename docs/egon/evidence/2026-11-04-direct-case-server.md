# Governed direct CMMN Case server execution evidence

- Baseline: `main@cff1bf1a9c79fb8f32ea38f58f54118b7b766a2d` (foundation Step1 committed and clean before Step2 source).
- Frozen Spec: `2026-11-04-15-50-formal-cmmn-case-server.md`, SHA-256 `c4e6d4bb8e503b6a9ff2f386514bd64c975eaf83f65b363d838307c9244736e5`.
- Frozen Plan: same filename under `docs/egon/plan`, SHA-256 `071a37e91d91fe9ced0569f1839e8fe37f54494adcdc86adf3d5c81acc25546d`, Step2 exact48 paths.
- Current gate: **Verified for Step2 source delivery**. Introducing commit is obtained afterward with git log on this evidence path; runtime acceptance is not inferred.
- Migration: no SQL edit; all24 committed migrations including final V8 remain immutable. Frozen exclusion inventory: `/tmp/tianji-step38-baseline.json` (806 paths).

## Resulting behavior

The atomic Step2 combines all CMMN type consumers, fixed release deployment/readback, direct Case start/get/finite plan actions, governed native task factory and CASE HumanTask paths. It reuses the existing Case C/SDK/OAS and human-task API; there is no separate business Case state machine or new dependency. Stop-new-starts is distinct from Case enablement and preserves in-flight access and task assembly. Linked BPMN source/completion belongs to Step3.

The native Factory/Adapter is necessary because the native extension point otherwise evaluates assignment data before released policy can choose fixed candidate groups. Late installation copies initialized expression/class-delegate dependencies and updates both configuration and parser pointers; typed suppliers resolve application collaborators only during native execution. USER/current-role and SERVICE owner boundaries run before database work; no historical USER context is restored.

## Recorded test-first progression

Every command used JDK25, offline source-reactor, both isolated settings arguments, ADK capture false and three OTel exporters none. Root exclusively ran Maven. The scoped unit/MockMvc commands used no listener, application, native engine, PostgreSQL, container or browser. One initial verify invocation unintentionally reached inherited upstream Failsafe integration tests; that separate failed invocation is documented below. No catalog registration, role grant, deployment or push occurred.

| Stage | Actual result | Log / meaning |
| --- | --- | --- |
| Named release type RED | C7 tests/1 expected assertion failure/0 errors/skips; 18.724s, 18:23:31 +08:00 | `/tmp/tianji-case-release-enum-red.log` |
| SDK correlation RED | C7 passed; CL7/1 expected assertion failure/0 errors/skips; 22.442s, 19:16:35 | `/tmp/tianji-case-direct-release-api-red.log`; Maven stopped before PS, so no PS RED inferred |
| Provider/release RED | PS35/10 assertion failures/5 Mockito fixture errors/0 skips; 25.853s, 19:18:10 | `/tmp/tianji-case-provider-release-red.log`; Access4/API5/wrapper1 valid assertions; five nested-stubbing errors excluded |
| Native/task/release RED | PS85/37 assertion failures/1 fixture error/0 skips; 25.126s, 19:22:05 | `/tmp/tianji-case-native-task-release-red.log`; one invalid contract fixture and the nested-stubbing error excluded from behavioral evidence |
| Configuration compile prerequisites | Two invocations failed test compilation (wrong Flowable class package; package-private test constant access) | `/tmp/tianji-case-native-config-red2.log`, `...-fixed.log`; neither counted as RED |
| Corrected native/config RED | PS75/26 assertion failures/1 test-stubbing error/0 skips; 26.868s, 19:40:10 | `/tmp/tianji-case-native-config-red2-compiled.log`; native20, TaskNative1 and config5 valid; Access4/PaC10/ProcessController5 passed |
| First full S2 integration | C7 and CL14 passed; PS222/2 assertion failures/0 errors/skips; 29.202s, 19:54:17 | `/tmp/tianji-case-direct-server-green1.log`; bounded CMMN deployment-query assertion and actual converted marker selection still failed |
| Official native type/marker RED | PS26/2 assertion failures/3 unsupported-type errors/0 skips; 20.715s, 20:03:12 | `/tmp/tianji-case-native-literal-marker-red.log`; actual service assertion proves rejection of official humantask; three direct constructor errors are not claimed as assertion RED |
| Assignment and start-replay scope RED | PS27/2 assertion failures/0 errors/skips; 20.617s, 20:11:38 | `/tmp/tianji-case-assignment-replay-scope-red.log`; assignee was null instead of the actual native task assignee, and replay omitted CaseScope |
| CASE PI affinity and strict completion RED | PS19/2 assertion failures/0 errors/skips; 20.450s, 20:24:01 | `/tmp/tianji-case-task-affinity-postcondition-red.log`; missing active PI affinity SQL and a residual Task hidden by that filter were independently caught |

Fixtures were repaired independently: nested Mockito getters were evaluated before stubbing; immutable command/source hashes and the literal version oracle replaced invalid constructed values; request-handler exception iteration uses doThrow; test-only inaccessible constants reuse the existing attribute-name convention. No prerequisite/fixture failure was relabeled as passing behavior.

## Native evidence corrections

Pinned Flowable8 `PlanItemDefinitionType.HUMAN_TASK` is `humantask`, distinct from the XML element spelling. Tests use the official constant; the independent length-prefixed24-field runtime-version oracle is `3f3515ee8dc9633744bb2204b5c091035dbbc295bc3ed6f0ae8c2709771f7150`. History uses a distinct read-only domain and ignores historical REV.

A finite real CmmnXmlConverter observation (no engine/DB) showed that custom task attributes are absent on the converted HumanTask while `taskLane` extension elements retain exact namespace `urn:stellar-enclosure:tianji:task:1`. Factory selection therefore recognizes either a retained kind attribute or that reserved namespace/element marker. Other namespaces and an unmarked legacy task with the same generic task ID stay native. Full authoritative role/policy data is still re-read from the immutable released XML before superclass execution; a marker is not authorization.

The reviewed CMMN deployment query is bounded to listPage(0,2); old BPMN/DMN query bodies stay unchanged. Start replay must hold CaseScope before its multi-statement READ_COMMITTED observation; direct get remains REPEATABLE_READ. An ACTIVE native human task must contribute its actual assignee and task REV, so plan termination cannot bypass another user's claim.

The pinned native CompleteTaskCmd loads its plan item by Task.subScopeId and the native Task table has no FK that binds that field to the same Case/definition/tenant. CASE task reads therefore explicitly join the active, non-ended native human plan item and match task definition, Case, definition and tenant. This is a SQL construction/contract proof, not a PostgreSQL wrong-row filtering claim. After CASE COMPLETE, a direct owning task-service lookup verifies physical deletion so an inactive-affinity filter cannot hide a residual task. Real cross-Case corruption/concurrency acceptance remains in the opt-in runtime obligation.

## Final gate

Root reviewed the final production diffs and the relevant test additions. Independent cross-reviews covered configuration/security, replay/lock order and task/source affinity; concrete findings were repaired test-first. Strict Spec/Plan checks passed; exact48 paths and all806 excluded files (including24immutable SQL resources) matched their hashes. No unfinished raw/UOE placeholder remains in the delivered paths. Original S09 native Case-to-BPMN acceptance is **Required / RuntimeUnverified**. The original goal and T3-08 remain incomplete.

## Verify lifecycle correction

The first `STELLAR_VERIFY_GOAL=verify` invocation omitted `-DskipITs=true` and reached the unchanged upstream Outbox Failsafe suite before Tianji. It failed with32 integration results,3 errors and15 skips (BusinessStateMachineIntegrationTest cleanup null ValidatorFactory and2 missing outboxMpStorageProperties fixture beans), exit1/32.216s at20:29:49. Log: `/tmp/tianji-case-direct-server-final-verify.log`. Its HTTP integration fixture started transient WireMock listeners and closed them in AfterEach. This was outside the intended finite validation boundary and is not claimed as Case/native/PostgreSQL acceptance; no upstream source was changed.

The corrected source-reactor command explicitly used `-DskipITs=true` and kept `-DskipTests=false` with the full26-class S2 selector. It completed BUILD SUCCESS in35.969s at20:34:31, with C7 + CL14 + PS226 = **247 tests passed, zero failures/errors/skips**. Log: `/tmp/tianji-case-direct-server-final-scoped-verify.log`; both inherited Failsafe goals explicitly reported skipped. These skipped integration suites are distinct from the247 executed unit/MockMvc assertions.

Permission profile verification uses `-Ppermission-producer -DskipTests=true -DskipITs=true`, baseline commit metadata `cff1bf1a9c79fb8f32ea38f58f54118b7b766a2d` and unique build identity `LOCAL_UNCOMMITTED-case-direct-20261104-2034`. It is a precommit artifact/annotation validation, not an exact clean-source release artifact or live registration. The already successful247 tests are not repeated by this packaging-only profile run. The first profile attempt failed aggregation at20:39:44 (16.331s), because incremental compilation reported Nothing to compile and had not emitted the Process module permission fragment. Log: `/tmp/tianji-case-direct-permission-artifact-verify.log`. No test failure was claimed. Updating only the owned CaseController source mtime, without changing any source byte, triggered fresh annotation processing. The same profile then completed BUILD SUCCESS in19.003s at20:42:58; log `/tmp/tianji-case-direct-permission-artifact-fresh-verify.log`. No unit or integration test was rerun in this packaging-only attempt.

The actual executable JAR manifest was parsed and byte/package equality checked. Three protected POST resources have exact paths, Case permission codes and scopes; start/get allow USER+SERVICE, actPlanItem allows USER only. Actual artifact digest is `18ce963eb729860381a46e110d0aa7ce51d4ad41cc2eea442e862973c6e0c2dc`, manifest digest `f3d4b9056d7d48f2e94643c0d05b3de04a789ca8cb146c34941e70bc93004b5f`, and policy digest `5a4df5d0e48f72a6167176167e19a6003fec50fa55218165fb1fb060c413082e`. Build outputs remain ignored/uncommitted; ACTIVE definition metadata is not a live catalog activation or permission grant. This local validation artifact carries baseline commit metadata plus its explicit LOCAL_UNCOMMITTED build identity.

## Blocking Manual Checks

| Check ID | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing PS api/security/execution/task/release packages and C/CL dependency boundaries | Exact48 path gate, all POMs frozen | Technical vertical architecture retained | None for Step2 source |
| MC-REUSE-001 | Applicable | PASS | Case C/SDK/OAS, existing task API/rules/receipts and native Factory hooks | 247 focused tests plus import/caller review | No duplicate task service API or Case state machine | None for Step2 source |
| MC-DEP-001 | Applicable | PASS | Only existing Flowable8/JDK/Spring/Jackson dependencies | All806 exclusions incl POM hashes | No dependency/build-step addition | None for Step2 source |
| MC-NAME-001 | Applicable | PASS | CaseContext/NativeAccess/Snapshot/service and typed metadata | Affected public/internal type inventory | Semantic names follow existing technical style | None for Step2 source |
| MC-VALID-001 | Applicable | PASS | Strict bounded request advice, current PDP/identity/deadline, owner realm, exact native/PI affinity | Body/auth/role/replay/affinity/corruption tests | Invalid state fails closed at its boundary | None for Step2 source |
| MC-MODEL-001 | Applicable | PASS | Existing immutable wire records and copied policy/native snapshots | Copies, closed state/action and constructor tests | No new competing wire model/PO | None for Step2 source |
| MC-CONVERT-001 | Not applicable | N/A | Direct existing C DTOs and technical snapshots | No PO/mapper introduced | No mapper conversion concern | None for Step2 source |
| MC-LOG-001 | Applicable | PASS | Existing safe exception type logs; no new token/body/identity payload log | Error privacy tests and log statements inspected | Case503/429 and old500 do not expose cause text | None for Step2 source |
| MC-BEAN-001 | Applicable | PASS | Existing named configuration factories, optional typed engine providers and lazy native supplier | Caseon/off/stopnew assembly and parser-pointer tests | No eager engine/service cycle or global bean lookup | None for Step2 source |
| MC-UTIL-001 | Applicable | PASS | JDK, existing ProcessRequestDigest, JDBC and Jackson only | Imports and independent version oracle | No speculative utility layer | None for Step2 source |
| MC-JSON-001 | Applicable | PASS | Strict Jackson3 advice, static OAS authority and unchanged SDK Case codec | Actual MockMvc shapes/errors and frozen wire hashes | Required nullable finishedAt and exact nine error pairs | None for Step2 source |
| MC-TIME-001 | Applicable | PASS | Instant/Duration and explicit native Date.toInstant boundary | 247 tests include precision/deadline/UTC strings; opaque domains | No default-zone/calendar rule or historical REV fence | None for Step2 source |
| MC-CONFIG-001 | Applicable | PASS | Two false Case flags and three required permission-scope entries | Feature matrices, YAML diff and actual PaC JAR | No real source bindings/role IDs/credentials/activation | None for Step2 source |
| MC-PATTERN-001 | Applicable | PASS | Native Adapter/Factory and existing local command participants | Factory and before-super tests; role/replay/lock order | Concrete native variation; no second Case state machine | None for Step2 source |
| MC-SCOPE-001 | Applicable | PASS | All CMMN consumers/native/API/task support in one Step2 commit; no Step3 source | Exact48 and806 exclusions including24SQL | No migration edit or unrelated cleanup | None for Step2 source |
| MC-TEST-001 | Applicable | PASS | Recorded assertion REDs, explicit fixture exclusions, final247PASS and producer artifact | Exact logs/counts and emitted JAR parsed | Runtime and upstream Failsafe results reported separately | None for Step2 source |
| MC-BLOCKER-001 | Applicable | PASS | No known unfinished Step2 source behavior or placeholder | Cross-review fixes verified; all final gates | Original S09 runtime/linked completion remains outside this Step | None for Step2 source |

## Literal Rule gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic Case/native/task/service/DTO names and explicit enum types | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 2 | Applicable | PASS | Existing technical explicit validation at JSON/identity/native/replay boundaries | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 3 | Applicable | PASS | Immutable C records, model copies and native observations; no PO/mapper/Lombok conversion | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 4 | Applicable | PASS | Existing manual constructors, named factories, lazy typed supplier and safe type-only logging | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 5 | Applicable | PASS | All dependency manifests frozen; only already available libraries | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 6 | Applicable | PASS | Jackson3 HTTP, exact C fields/enums and static OAS; native/legacy serializers preserved | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 7 | Applicable | PASS | Base YAML Case flags false and exact scope mappings; no other environment profile or live binding | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 9 | Applicable | PASS | Adapter/Factory address actual native variation; command participants and scope fence reuse existing architecture | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 10 | Applicable | PASS | Instant/Duration throughout, explicit native Date conversion and DB microsecond precision | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |
| Rule 11 | Applicable | PASS | Always applicable: exact existing vertical technical structure/JDBC/Flyway; no archetype or ORM migration | Final diff,247PASS, actual PaC JAR and806-path byte gate | Effective Spec rule preserved | None for Step2 source |

## API review

| API concern | Verdict | Evidence |
| --- | --- | --- |
| Route/method/operation identity | PASS | Three exact POST mappings and packaged OAS comparison |
| Request fields/validation | PASS | Existing C commands, required string member sets,64KiB cap and mutation tests |
| Success schema/required members | PASS | Actual MockMvc9/6/12 fields, explicit nullable finishedAt and direct DTOs |
| Error/status/media | PASS | Nine exact pairs, no-store/JSON, typed handlers preserved, old Process500 regression |
| Authentication/resource authorization | PASS | Actual guard refresh, USER/SERVICE classification, scope/permission revocation and owner realm |
| Time/precision | PASS | UTC textual Instant+nanos on wire; actual DB/native precision preserved |
| Idempotency/concurrency | PASS for source/finite tests | Actor command namespaces, immutable result replay, CaseScope and native REV/assignee; no PG race claim |
| Consumer compatibility | PASS | Case C/codec/OAS/transport byte-frozen; only explicit CMMN publish correlation added |
| Documentation/artifact authority | PASS | Static contract-first OAS; actual permission JAR resources/digests parsed; no generated /v3/api-docs claim |

## Effective requirement conformance for this Step

| Requirement | Source result | Boundary |
| --- | --- | --- |
| REQ-401 | Preserved foundation | V8 and all other23 SQL bytes unchanged |
| REQ-402 | Satisfied for Step2 | All CMMN release consumers, deploy/readback, SDK correlation and old digest preserved |
| REQ-403 | Satisfied for direct entry | Current USER/SERVICE owner=executor, atomic start/replay provenance |
| REQ-404 | Deferred to Step3 | Linked parent/source registration not yet admitted |
| REQ-405 | Satisfied for provider source | Three exact operations/body/response/error/PaC contracts |
| REQ-406 | Satisfied for direct/read source | Exact owner or fresh same-realm USER operator; SERVICE owner only |
| REQ-407 | Satisfied for source | Finite roles/actions/version/assignee protection |
| REQ-408 | Satisfied for source | Runtime REV/independent history domain, bounded observations and terminal handoff |
| REQ-409 | Satisfied for source | Existing CASE task API/rules/receipts; linked proof representation ready |
| REQ-410 | Satisfied for source | Shared CaseScope before native fences; start replay scope; immutable facts have no FOR UPDATE |
| REQ-411 | Satisfied for source | Real configured late factory and pre-super policy hook; actual converter marker verified |
| REQ-412 | Deferred consumer to Step3 | Inbox foundation preserved; no completion runtime consumer yet |
| REQ-413 | Deferred to Step3 | BPMN literal CaseSource stays rejected |
| REQ-414 | Satisfied for Step2 | Two flags, default off, independent stop-new and Caseon/off assembly |
| REQ-415 | Deferred linked executor to Step3 | Current public SERVICE guard complete; no registered source executor invented |
| REQ-416 | Required / RuntimeUnverified | No actual native Case-to-BPMN/PG/IAM acceptance; opt-in source is Step3 |

Prior Case C, SDK, safe XML and closed semantic requirements remain effective preserved dependencies. All806 excluded files were checked against the Step1 baseline; unchanged Case C/codec/OAS/transport and old parser/semantic bytes are not reclassified as new features.

Step2 source conformance result: **Verified**. Whole T3-08 and the original goal remain incomplete; Step3 must begin only after this exact48-path semantic commit and clean Git checkpoint. No push or runtime activation is part of this delivery.
