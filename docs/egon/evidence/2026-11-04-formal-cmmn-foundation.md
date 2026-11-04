# Formal CMMN persistence foundation execution evidence

- Step: server Plan Step 1; original blueprint T3-08 remains incomplete.
- Baseline: `main@e6aa574de553d20b67f8d483d8a1e876bb48897e`.
- Spec: `2026-11-04-15-50-formal-cmmn-case-server.md`, SHA-256 `c4e6d4bb8e503b6a9ff2f386514bd64c975eaf83f65b363d838307c9244736e5`.
- Plan: same filename under `docs/egon/plan`, final reviewed SHA-256 `071a37e91d91fe9ced0569f1839e8fe37f54494adcdc86adf3d5c81acc25546d`.
- Sole new migration: `V8__add_formal_cmmn_case_support.sql`, 515 lines, SHA-256 `d2ef3198b53e681835239ed374e126d226cd40afef658819dbfbdd6372d82a44`; created once after the first test source. No old migration edited.
- Introducing commit: obtain with `git log -1 --format=%H -- stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V8__add_formal_cmmn_case_support.sql` after this focused commit; no self-referential commit hash is fabricated here.

## Behavior and design boundary

Three real JDBC participants retain all-Case owner/executor provenance, immutable accepted plan-item results, and pre-created linked completion delivery intent. CaseContext carries immutable current-operation facts. Native CMMN remains state authority; no business Case state machine is introduced. Shared Case scope advisory locking precedes future native task/plan-item fences, and immutable facts need SELECT/INSERT only. Existing command receipt ownership remains unchanged.

Seed22 plus Entry(seed, createdAt) maps every provenance column without duplicating fields. Inbox supports durable verified COMPLETED facts while PENDING, parent wait winning while the child remains ACTIVE, technical-failure accounting, and removed-registration discovery. Its internal AppliedCommand binds an already-APPLIED base receipt to exact request hash and actual Message execution resource; constructing it grants no native authority. Completion delivery belongs to Step3.

No model enum, release admission, controller, native factory, feature activation, real registration or runtime grant is added in this Step. New binary Case-off supports V7/V8; enabling Case requires full V8 verification. Old task nodes must be drained before a future V8 deployment because their strict V7 verifier rejects widened checks.

## Executed validation

All Maven commands used JDK25, the existing source reactor, isolated `/tmp/tianji-compat-fix/settings.xml` for both settings arguments, offline `-o`, capture false and all three OTel exporters none. No listener, application, engine, PostgreSQL, container or browser was started.

| Run | Selector | Actual result | Log |
| --- | --- | --- | --- |
| RED1 | Plan S1 six classes | exit1; 48 tests, 43 assertion failures, 0 errors, 0 skips; 27.495s, 2026-11-04 18:05:35 +08:00 | `/tmp/tianji-case-foundation-red.log` |
| RED2 | HumanTaskStorageVerifierTest,CaseStorageConfigurationTest | exit1; 15 tests, 9 assertion failures, 0 errors, 0 skips; 25.756s, 2026-11-04 18:10:26 +08:00 | `/tmp/tianji-case-schema-red2.log` |
| GREEN | Plan S1 six classes | exit0; 50 tests, 0 failures/errors/skips; 24.366s, 2026-11-04 18:15:16 +08:00 | `/tmp/tianji-case-foundation-green.log` |

RED1 Context3/3 failures, provenance7/7, plan receipts7/7, Inbox18/17, schema7/7, HumanTask6/2. One schema static-resource failure was a test regex omitting digits in column names; it is **not** counted as a valid behavior RED. RED2 fixed the fixture and added real V7/V8 verifier transaction paths: schema7/6 failures (static-resource case passed), HumanTask8/3 failures; V7 positive path passed while V8 acceptance still failed for the missing behavior. No compiler/environment failure was counted as RED.

## Manual Checks

Final execution gate PASS: Root read all changed production bodies and tests, strict Spec/Plan passed, exact16 paths matched, all197 excluded files (including23oldSQL) remained byte-identical, and final soleV8 hash matched. Final test counts are actual output above. After GREEN only added import order/constant placement changed; Root inspected that nonbehavioral delta and did not repeat tests.

| Check ID | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing identity/execution/bridge/task packages; technical JDBC only | Exact16 paths, source-reactor compilation | Selected structure preserved | None; final diff and actual GREEN reviewed |
| MC-REUSE-001 | Applicable | PASS | ProcessCommandReceiptStore ownership, CaseOperationResult constructor, provenance participant and verifier reused | Call graph and imports | No duplicate task API or source authority | None; final diff and actual GREEN reviewed |
| MC-DEP-001 | Applicable | PASS | POMs untouched; JDK/Spring JDBC/Jackson existing stack | 197 frozen paths | No added dependency | None; final diff and actual GREEN reviewed |
| MC-NAME-001 | Applicable | PASS | CaseContext, Seed/Entry, technical stores and Inbox states | New type inventory | Semantic owners explicit | None; final diff and actual GREEN reviewed |
| MC-VALID-001 | Applicable | PASS | Owner/executor APPLIED/resource/hash/model joins, guarded sourceTX and deadline | Corruption/role/state/3VL test matrices | No authority from record construction | None; final diff and actual GREEN reviewed |
| MC-MODEL-001 | Applicable | PASS | Immutable records and role defensive copies, twelve-field result reused | Context copy and result replay tests | No competing wire model | None; final diff and actual GREEN reviewed |
| MC-CONVERT-001 | Not applicable | N/A | No PO/mapper/ORM conversion introduced | Source/import inventory | No conversion concern | None; final diff and actual GREEN reviewed |
| MC-LOG-001 | Not applicable | N/A | No logging added; exceptions and internal summaries are generic/redacted | Source log and summary review | No payload/credential logging | None; final diff and actual GREEN reviewed |
| MC-BEAN-001 | Not applicable | N/A | No new bean activation/factory in Step1; existing constructors preserved | Exact paths and compile | Assembly belongs Step2 | None; final diff and actual GREEN reviewed |
| MC-UTIL-001 | Applicable | PASS | Existing JdbcTemplate/TransactionTemplate/ProcessRequestDigest/Instant | Import and hash-domain review | No speculative utility layer | None; final diff and actual GREEN reviewed |
| MC-JSON-001 | Applicable | PASS | CaseOperationResult direct reuse; no JSON surface changed | C/CL main hashes frozen | Existing wire preserved | None; final diff and actual GREEN reviewed |
| MC-TIME-001 | Applicable | PASS | DB clock, timestamptz(6), Instant/Duration and microsecond-safe immutable results | Due/facts/deadline and precision tests | UTC instant semantics | None; final diff and actual GREEN reviewed |
| MC-CONFIG-001 | Not applicable | N/A | No application YAML/feature/permission change | Exact diff and excluded paths | No activation | None; final diff and actual GREEN reviewed |
| MC-PATTERN-001 | Applicable | PASS | Existing command participants; native state authority retained | SQL and caller obligations review | No business state machine | None; final diff and actual GREEN reviewed |
| MC-SCOPE-001 | Applicable | PASS | One final V8 plus exact16 declared paths | 23 old SQL hashes plus finalV8 and exact diff | No Step2 source begun | None; final diff and actual GREEN reviewed |
| MC-TEST-001 | Applicable | PASS | Actual per-store assertion RED and independent catalogue mutants | RED1/RED2 and final50GREEN | Runtime proof separate | None; final diff and actual GREEN reviewed |
| MC-BLOCKER-001 | Applicable | PASS | All Step1 schema fields and internal method signatures frozen | Strict Spec/Plan; final audit passed for this Step | Original S09 runtime remains required | None; final diff and actual GREEN reviewed |

## Literal Rule gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Semantic names for Case context/provenance/plan result/completion owner | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 2 | Applicable | PASS | Explicit boundary/row/source/role/deadline/state guards in current technical style | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 3 | Applicable | PASS | JDK immutable records and copies; no PO/mapper/Lombok conversion | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 4 | Applicable | PASS | Existing manual constructors/named-factory style preserved; no new logging or bean activation | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 5 | Applicable | PASS | Only existing JDK/Spring JDBC/core dependencies | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 6 | Applicable | PASS | Frozen C/SDK/OAS and direct CaseOperationResult constructor; no new serializer | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 7 | Applicable | PASS | No environment/YAML/security activation; Step1 configuration scope empty | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 9 | Applicable | PASS | Existing command/transaction participants, exact ownership and shared advisory scope | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 10 | Applicable | PASS | Instant/Duration and DB/native precision; no default-zone/calendar rule | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |
| Rule 11 | Applicable | PASS | Always applicable: preserve actual technical vertical/JDBC/Flyway structure; no new framework/layer | Root final diff, actual50GREEN, 197-path hash gate | Source obligation mapped | None; final diff and actual GREEN reviewed |

## Requirement and acceptance boundary

REQ-401 persistence/schema contract and the foundation portions of REQ-403/404/407/408/409/410/412/414/415 are this Step's source obligations. Provider/release/native/task/completion consumers remain the locked Step2/3 scope. Existing C/XML/semantic/SDK requirements are preserved dependencies, not reimplemented features.

Original REQ-416 / blueprint S09 actual native lifecycle and Case-to-BPMN acceptance is **Required / RuntimeUnverified**. Finite mocked JDBC tests, SQL resource inspection, source compilation and this commit cannot satisfy it. PostgreSQL/migration/native privileges, engine lifecycle, concurrency against a real DB, IAM and deployment are unexecuted. The original goal and T3-08 remain incomplete.

## Final source audit

- Root reviewed final source and all six test classes. A second agent reviewed plan receipts; the database specialist independently reviewed final Inbox/receipt SQL, state/proof binding and actual V8, finding no concrete blocker.
- Final six-class counts: Context3, provenance7, plan receipts7, Inbox18, Case schema7, HumanTask storage8; all passed.
- Strict Spec and Plan passed again at the commit gate. Exact16 declared paths and all197 frozen paths passed. Final V8 remains its once-created bytes.
- Original HumanTask check-signature body and all existing ProcessApiSchemaVerifier base/worker/agent methods are unchanged; only signature visibility and new V8 selection/helpers are added.
- No permanent raw/no-op/UOE implementation remains in the new production paths. No source from Step2/3 has begun.

| API review item | Applicability | Verdict | Evidence |
| --- | --- | --- | --- |
| Route/method/operation identity | Not applicable | N/A | No controller/SDK/OAS changed |
| Request/body/validation | Not applicable | N/A | No HTTP input change |
| Response schema/required fields | Not applicable | N/A | Frozen Case C/SDK/OAS hashes |
| Errors/status/media | Not applicable | N/A | No HTTP handler change |
| Authentication/resource authorization | Not applicable | N/A | No endpoint/PaC activation; context construction grants nothing |
| Time representation | Applicable | PASS | DB Instant/Duration/microsecond facts; no wire change |
| Idempotency/concurrency | Applicable | PASS | Immutable receipts/provenance, exact keys and shared scope caller contract; unit proof only |
| Consumer/provider compatibility | Applicable | PASS | All C/CL main bytes frozen; no admission changes |
| OpenAPI/document authority | Not applicable | N/A | Contract-first OAS unchanged, no generated provider claim |

Commit gate result: **Verified for Step1 source delivery**. Runtime acceptance remains **Required / RuntimeUnverified**, and original T3-08/global goal remain incomplete.
