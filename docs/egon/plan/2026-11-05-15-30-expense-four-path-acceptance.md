# Implement the T3-12 four-path expense acceptance sample

| Field | Value |
| --- | --- |
| Document | `2026-11-05-15-30-expense-four-path-acceptance.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 15:30 Asia/Shanghai` |
| Updated | `2026-11-05 15:40 Asia/Shanghai` |
| Owner | Root source review under the user master goal |
| Repository | `tianji-service` Steps 1–4; `tianji-web` Step 5 |
| Scope | Test-only four-path sample, finite evidence and opt-in E harness |
| Source Requirement | [Master Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) §17 and §19 T3-12; continue remaining work and commit by Step |
| Baseline Revision | Service `c0ff76b66e4f903e640f0cc2cfe661076c495b36` and Web `43c58a1069a50fb71b955540bae2d7e8526794b0`, clean; re-lock each prior committed Step |
| Implements Spec | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) |
| Spec Status | `Review` |
| Spec Revision | Root-reviewed exact SHA-256 `040d2e6e0e47fcfcb931d02ca9f1a6458eaffac7eaabac347fdcee89d05a0a34` |
| Effective Specs | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

Deliver the common expense release and portable fixture first, then the strict operator harness, runtime fixture entry points, four-path scenario source and Web consumer proof. The five Steps are source delivery and finite checks. E is deliberately separate and operator-controlled; neither a compiled IT nor a finite transport/model test is real end-to-end acceptance.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md).
- Status: Review.
- Revision: `040d2e6e0e47fcfcb931d02ca9f1a6458eaffac7eaabac347fdcee89d05a0a34` against the exact service/web baseline above.
- Approval evidence: Root reviewed current source and pure model feasibility under the user's existing master-Plan implementation authorization. This is source authorization, not a runtime approval or user acceptance claim.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) | Review, exact SHA above | REQ-001 through REQ-008 and §5–§16 | Governs only new test fixtures and verification |
| Dependency | [Worker compensation](../spec/2026-11-05-12-09-worker-compensation-fixture.md) | Current committed | §7, §11, §14 | Reuse exact Worker model/receipt/undo authority |
| Dependency | [Formal Case](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) | Current committed | Native Case/task and bridge constraints | Reuse current Case owner and delivery |
| Dependency | [Worker recovery](../spec/2026-11-05-13-41-process-worker-recovery.md) | Current committed | §7, §11, §14 | Existing recovery and schema history remain unchanged |

### 2.3 Superseded or excluded content

None superseded. This Plan creates no production service, API, DDL migration, IAM grant, runtime config, deployment, browser flow or provider traffic. Existing Recovery Step 5 and S10/S11 live gates remain separate.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | One pinned business sample uses real SDK START, DMN, Agent and notification | Native DMN rule and actual COMPLETED Agent, one BusinessDB notice and normal end | model+business scenarios |
| REQ-002 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Actual Agent model failure selects a distinct branch | Persisted FAILED and actual Outbox event, one Inbox application and one exact native compensation, no normal notice | model+failure driver |
| REQ-003 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Real governed human task and linked formal Case | Native Timer routes to review; authorized claim/complete and Case item/sentry/milestone; exact Case completion resumes BPMN and compensates | model+manual Case driver |
| REQ-004 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Genuine early/duplicate delivery, lost ACK and two independent restarts | Early Inbox pending before binding, real resend with same event, fresh Worker lease/one effect, new AS and PS PIDs and surviving persisted state | harness+faults+scenario driver |
| REQ-005 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Two trusted IAM tenants with exact owner/source scope | Reuse identical business/operation/command/user subject strings across tenants. Assert disjoint IDs/receipts and denied foreign authority. | manifest and verified SDK |
| REQ-006 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Correlate evidence and reconcile owned work | Link release/process/decision/run/task/Case/job/receipts, distinguish never reached from unproved, account for every owned run/work item | bounded immutable evidence |
| REQ-007 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Runnable fixture and bounded finite gates | Finite tests require no runtime. Explicit E fails incomplete/skipped or non-isolated runs. | finite and opt-in gates |
| REQ-008 | [Expense acceptance Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) §4 | Share the test contract with web | Existing web test decoders consume the same pinned neutral fixture with hash parity; browser separate | test JSON and existing Web decoder |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Commit exact model and fixture bytes after real validators first. Then implement the generic test operator boundaries before fixture-specific child boot and handler source. Compile actual independent scenario drivers last in service. Web copies the already committed contract bytes and runs existing decoder suites. Never start any JVM service, server, database, listener or browser to validate these Steps.

### 4.2 Test-first strategy

Each focused source Step records actual expected assertion RED before enabling positive behavior. Resource absence, unimplemented safety boundaries and decoder absence must fail as assertions; dependency, compile or discovery failures do not count. Scenario classes compile against earlier seams and are never selected during finite checks. If an opt-in E needs runtime verification it remains pending.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Current committed baselines | Read-only drafting | Later source changes | Locks inputs and exact fixture bytes |
| Step 2 | Step 1 | Read-only fixture and test drafting | Later source changes | Stabilizes shared harness and causal transport |
| Step 3 | Step 2 | Read-only scenario planning | Later source changes | Stabilizes fixture-specific launch/Worker seams |
| Step 4 | Step 3 | Read-only Web prep | Later source changes | Compiles complete real opt-in scenario source and records boundary |
| Step 5 | Steps 1–4 and unchanged Web baseline | No additional writes | Any service or production Web edits | Exact repo-local copy and typed decoding |

### 4.4 Commit boundaries

One exact path-limited local commit after each Step's gates. Steps 1–4 in service; Step 5 in Web. No cross-repo or mixed commit. Before every Step inspect current clean HEAD, pin excluded hashes, and revalidate allowed paths and SQL bytes before commit. No push or history rewrite.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Expense XML/fixture | Add §7/§8 | Current pure PS validators and separate fixture files | Separate tests cannot prove one release | Four bounded fixtures and one test | Implement |
| Process/Agent launches and relay | Add §7/§9 | Existing Boot exec JARs and linked/result clients | MockMvc/context recreation cannot prove real commit/restart | One test driver, two mains and a narrow relay | Implement |
| Business original/undo/notice | Reuse and add notice §11 | CompensationBusinessFixture and Worker Store | Technical receipt alone cannot prove real effect | One disposable test table only | Implement |
| Web contract | Add §12 | Existing task/Case decoders | Independent samples drift | One pinned test copy and one test | Implement |
| Production API/extra runtime abstractions | None §3 | Current SDK, IAM and transaction paths | Reuse directly | No added caller or data ownership | Already exists |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Common release and fixture | REQ-001/002/003/007/008 | Resource presence RED | Existing validators/DTOs | Exact test resources | All harness/consumer steps | Step 1 |
| Operator manifest, lifecycle and relay | REQ-004/005/006/007 | Missing seam and typed RED | Step 1, JDK/Servlet-compatible source paths | Typed bounded test harness | Native fixtures and scenario tests | Step 2 |
| Real fixture launch and Worker | REQ-001/002/003/004/007 | Test-only Fake and fixture behavior RED | Step 2, existing Boot and Worker | AS/PS child mains and scoped synthetic effects | E scenario source | Step 3 |
| Four path source | REQ-001/002/003/004/005/006/007 | Compile/presence and finite prior regression | Steps 1–3 | Runnable isolated opt-in tests | Operator E only | Step 4 |
| Web fixture parity | REQ-008 | Missing fixture RED | Exact Step 1 Git blob | Tested canonical Web copy | Consumer contract evidence | Step 5 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | PS/AS existing acceptance and test vertical; W transaction owner | Spec §5/§6 test-only profile | Keep src/test only and current dependency direction | 1–4; MC-ARCH-001 |
| Reuse/capability | Real validators, existing Boot exec, CL/W, current JDBC fixture | Spec §7/§11 | Use current owners with no added POM | 1–4; MC-REUSE-001, MC-DEP-001 |
| Naming/model/validation/conversion | Current C typed constructors, PS/AS test pattern | Spec §6/§9/§10 | Strict boundaries, immutable private evidence, no new public entity | 1–4; MC-NAME-001, MC-VALID-001, MC-MODEL-001 |
| Bean/logging/util/JSON/time/config | Named model seam, existing JDK/Jackson, native clocks | Spec §6/§15 | Explicit test-only Fake; no secrets, production profile or new time format | 2–4; MC-BEAN-001, MC-LOG-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001 |
| Business variation/pattern | Existing Worker strategy and separate exact fault windows | Spec §7/§13 | Test Scenario Orchestration plus current Worker participants | 2–4; MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Native validation | Existing PS validators and Flowable converters | ProcessModelValidator and exact probe | Whole release fits current grammar | Reuse | Three XML/test fixture resources | Step 1; MC-REUSE-001 |
| Actual process/transport | Existing Boot exec, JDK ProcessBuilder and TLS, existing linked/result SDK | Current AS/PS executable classes and transport bindings | Requires test-only scoped fault control | Reuse | No dependency; private test seam | Step 2; MC-DEP-001 |
| Business effects | CompensationBusinessFixture, JdbcWorkerExecutionStore | Existing local original/undo behavior | Real notice needs one business fixture oracle | Reuse | One test helper, no production schema | Step 3; MC-REUSE-001 |
| Web parsing | Current typed Task/Case decoders and Vitest | Existing Web test calls | Requires shared portable fixture | Reuse | One test and fixture | Step 5; MC-REUSE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2 | Current semantic test names | Model tests → DTOs → launch fixtures → ITs | Explicit evidence and fixture names | Selected compile and scope | Steps 1–5 as applicable | PASS |
| Rule 2 | Spec §6.2 | Current strict contracts | Test-first then strict manifest/matching handlers | Reject wrong identity/scope/paths and malformed input | Finite negative and positive fixtures | Steps 1–5 as applicable | PASS |
| Rule 3 | Spec §6.2 | Current C immutable values | Current C types and private DTOs | Immutable facts with explicit validated construction | Byte and decoding tests | Steps 1–5 as applicable | PASS |
| Rule 4 | Spec §6.2 | Current test model Bean seam | Named Fake after model test | No business logging/injection change; stable named test-only model | Finite Boot wiring without runtime | Steps 1–5 as applicable | PASS |
| Rule 5 | Spec §6.2 | Existing JDK/Jackson/JDBC/Worker | All Java test paths in §5 | Reuse approved tools, no new dependency | Compile and untouched POM | Steps 1–5 as applicable | PASS |
| Rule 6 | Spec §6.2 | Current typed JSON/Jackson and TS decoder | Shared resource → reader → web fixture | Exact field/type/closed shapes and safe envelope | Model, harness and Web tests | Steps 1–5 as applicable | PASS |
| Rule 7 | Spec §6.2 | Current environment properties | Test-only operator inputs | Keep existing profile keys/values unchanged | Exact scope and private config checks | Steps 1–5 as applicable | PASS |
| Rule 9 | Spec §6.2 | Native strategy and scenario predicates | Harness → fixture → scenarios | Causal scenario controller, no new production layer | Phase and branch tests | Steps 1–5 as applicable | PASS |
| Rule 10 | Spec §6.2 | Native DB/engine and Java clock | Fixture time → harness deadline → evidence | Instant/Duration and monotonic deadlines | TIME-001–003 in §4.9 | Steps 1–5 as applicable | PASS |
| Rule 11 | Spec §6.2 | Existing technical test vertical | Exact all five Step files | No prod layer/MP/generator or migration edits; existing Worker/Outbox/CQE | Per-step scope/source gate | Every Step | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Native due/lease and report observedAt/decision facts | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: technical instant | Not applicable: fixture report | Exact durable identity and before/after witness; no time-only causal inference | Existing DB/native clocks and precision | Preserve current engine/SDK times | Step 1, Step 2 and Step 4; TEST-001, TEST-002, TEST-004 |
| TIME-002 | Phase deadlines and shutdown budget | Duration | Duration, System.nanoTime | Not persisted | Internal | Not applicable: elapsed budget | Not applicable: elapsed budget | Absolute monotonic deadline; no wall clock, calendar or DST arithmetic | Monotonic clock, injectable finite supplier | Existing process and DB clocks unchanged | Step 2 and Step 4; TEST-002, TEST-004 |
| TIME-003 | Finite fixture time | Occurred instant | Instant, Clock | Not persisted | Internal | Not applicable: finite technical fixture | Not applicable: finite fixture | Exact fixed UTC reference; no runtime clock override | Fixed finite clock | No real DB/engine elapsed implication | Step 2 and Step 3; TEST-002, TEST-003 |

## 5. Change File Tree

| Step | Repository | Exact ordered paths |
| --- | --- | --- |
| Step 1 | tianji-service | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseFixtureModelsTest.java`, `stellar-enclosure-tianji-process-server/src/test/resources/processes/expense-review.bpmn20.xml`, `stellar-enclosure-tianji-process-server/src/test/resources/decisions/expense-routing.dmn`, `stellar-enclosure-tianji-process-server/src/test/resources/cases/expense-exception.cmmn`, `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json`, `docs/egon/spec/2026-11-05-15-13-expense-four-path-acceptance.md`, `docs/egon/plan/2026-11-05-15-30-expense-four-path-acceptance.md`, `docs/egon/evidence/2026-11-05-expense-four-path-models.md` |
| Step 2 | tianji-service | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java` |
| Step 3 | tianji-service | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAcceptanceModelTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/ExpenseBusinessWorkerFixture.java` |
| Step 4 | tianji-service | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`, `docs/egon/evidence/2026-11-05-expense-four-path-acceptance.md` |
| Step 5 | tianji-web | `src/test/expenseFourPathContract.test.ts`, `src/test/fixtures/expense-four-path-contract.json` |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Service `c0ff76b66e4f903e640f0cc2cfe661076c495b36`, Web `43c58a1069a50fb71b955540bae2d7e8526794b0`, clean. The outer workspace is not a Git repository. Verify and preserve all 26 service main SQL bytes and current POMs/production profiles. Subsequent Step baseline is the preceding committed tree.

### 6.2 Build, test, and environment prerequisites

Existing JDK25, cached offline reactor and credential-free task Maven settings; current Web npm scripts. Real AS/PS, TLS/IAM and disposable PG/collector are runtime prerequisites only, with separately authorized operator configuration. Nothing in finite source execution provisions any of them.

### 6.3 Immutable constraints and approved decisions

Test-only source and resources, exact fixture SHA, no default connection or operator opt-in. No production SQL/API/dependency/permission/launch. Do not select either runtime IT during finite verification. Preserve real test timer/clock and owner contracts.

### 6.4 Plan Clarifications

Model exact feasibility was resolved before planning. The authorized test runner may launch only the exact locked artifacts and user-supplied isolated settings; the implementation agent never invokes that path. Compilation of runtime sources is evidence of API fit only.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Lock the expense model and shared fixture bytes

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-007, REQ-008.
- Dependencies: Effective T3-12 Spec and Plan approved for execution, exact service baseline `c0ff76b` and existing U1 selectors. This Step does not depend on T3-12 E authorization.
- Baseline state: In `tianji-service` the three exact expense model resources, canonical JSON and `ExpenseFixtureModelsTest` are absent. Existing BPMN/DMN/CMMN, Worker compensation and task model validators have finite tests and no need for application startup.
- Observable outcome: One three-model release validates against the existing native conversion and Tianji semantic validators, and its finite Task/Case projections match one byte-pinned canonical JSON.
- End state: Four test resources, one finite Java test, approved Spec/Plan and current model evidence are committed together in `tianji-service`. The canonical JSON is ready for an exact later web copy. No E path is claimed.
- Test-first gate: Required. Stage A adds the resource-presence assertion using only existing JUnit and classpath calls. Run the exact RED selector before adding any resource. After reviewed bytes are added, finish typed fixture assertions and require actual selected GREEN reports.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseFixtureModelsTest.java`

- Purpose: Verify the real existing model validators and the exact closed fixture before any integration harness is added.
- Symbols: `ExpenseFixtureModelsTest#modelResourcesExist`, `validatesPinnedReleaseAndNativeGraph`, `rejectsUnsafeModelChanges`, `canonicalFixtureMatchesModelsAndNeutralDtos`.
- Repository evidence: Existing same-module `release/ProcessModelValidatorTest`, `CmmnModelValidatorTest`, `WorkerCompensationModelTest`, `HumanTaskModelTest`, `HumanTaskTimerModelTest` are finite JUnit/model tests. The pure, off-repository `model-draft/proof/ExpenseModelProbe.java` already exercised the current pinned validators and converters on exact candidate model bytes.
- Dependencies and consumers: Existing `ProcessModelValidator#validateBpmn,validateDmn,validateCmmn,validateDecisionBinding,validateHumanTaskBindings,validateWorkerBindings`, `BpmnXMLConverter`, `CmmnXmlConverter`, `WorkerTopicRegistry`, `CaseSourceRegistry.BindingKey`, `PublishProcessReleaseCommand`, `HumanTaskDTO`, `CaseInstanceDTO` and Jackson 3. Later harness reads the same approved resources.
- Why now: Establish a compiled genuine missing-resource assertion before copying the model files. All final test types already exist.
- Contract/signature changes: Add only four package-local tests and private test helpers. Stage A may contain only `modelResourcesExist`; final test names are exact.
- Input/output and state mapping: Read the four named classpath resources as bytes, verify their SHA-256 and use their typed definitions and neutral DTOs. Exact expected maps include `operationId:STRING, amount:INTEGER`, DMN rules `smallExpense, largeExpense`, and one ACTION form field `finding → reviewFinding`. In-memory model strings preserve raw UTF-8 and synthetic role IDs, and do not read actual user roles.
- Error and edge behavior: Assertion RED for absent resources; on final gate reject the proven mutated compensation bypass, a DMN output lane with no configured lane, missing Worker `operationId` binding, and the wrong Case definition key. Reject fixture shape/digest/mismatched model keys, unused model SHA, inconsistent release and DTO task/Case fields. All negative XML variants live only in memory.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Reuse semantic C DTO construction and the current technical test profile. No Spring Beans, ORM, converter or new dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Use existing exact constructors and native validators, JDK SHA/UTF-8, Boot-compatible Jackson strict reads, Instant/Duration and existing package direction.
- Implementation pseudocode:

```java
@Test void modelResourcesExist() {
    assertAll each assertNotNull(getClass().getResource(exactPath), "required expense fixture " + exactPath)
        for /processes/expense-review.bpmn20.xml, /decisions/expense-routing.dmn,
            /cases/expense-exception.cmmn, /fixtures/expense-four-path-contract.json
}
@Test void validatesPinnedReleaseAndNativeGraph() {
    read exact UTF-8 resource bytes and independently pin all three hashes
    bpmn = validator.validateBpmn(bpmnBytes, "expenseReview")
    dmn = validator.validateDmn(dmnBytes, "expenseRouting")
    cmmn = validator.validateCmmn(cmmnBytes, "expenseException")
    validateDecisionBinding, validateHumanTaskBindings, validateWorkerBindings against START input types
    assert classifyExpense → expenseRouting, rules/lane schema, exact ACTION finding,
           Message/Timer pairs, compensated source/boundary/association/throw/handler
    build existing PublishProcessReleaseCommand("expense-v1", exact three typed ProcessModelResource)
    verify registry for tenants 20,21 and both CaseSourceRegistry.BindingKey hashes
    verify pure BPMN/CMMN converters retain one split + one merge, both event gateways,
        formal Case source, compensation boundary/association and Case sentry topology
}
@Test void rejectsUnsafeModelChanges() {
    use assertThrows IllegalArgumentException for four in-memory negative cases
    for compensation bypass reuse the proven three exact sequenceFlow attribute replacements
    retain the original file bytes and assert no shared asset mutation
}
@Test void canonicalFixtureMatchesModelsAndNeutralDtos() {
    parse byte-pinned fixture using existing Jackson 3 JsonMapper and exact field sets
    assert model hashes, keys and input maps against the three loaded assets
    assert all four scenario amounts and the exact rule/lane expectation for each
    decode humanTask and caseInstance through existing HumanTaskDTO/CaseInstanceDTO
    assert "expense-v1", ACTION+null approval, finding STRING, canonical UTC Instant,
        primary completed, supplemental enabled with DISABLE/START, milestone available without actions
}
```

- Verification contribution: `ExpenseFixtureModelsTest#modelResourcesExist` is the exact RED selector. Full class and existing U1 validators are GREEN after Files 2–5.
- After this file: Stage A compiles and the presence assertion fails. No input types or release/runtime source have changed.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/processes/expense-review.bpmn20.xml`

- Purpose: Supply the exact BPMN test graph shared by all four acceptance paths.
- Symbols: `expenseReview`, `classifyExpense`, `reserveExpense`, `agentStart`, `reviewExpense`, `caseSource`, `compensationJoin`, `throwUndo`, `undoReservation`, `normalEnd`, `caseTimeoutEnd`, `compensatedEnd`.
- Repository evidence: `ProcessModelValidator#validateBpmn,compensationProfile`, `fixtures/worker-compensation.bpmn20.xml`, `fixtures/formal-case-linked.bpmn20.xml`; pure probe accepts the exact off-repository candidate and rejects the native compensation bypass.
- Dependencies and consumers: `expenseRouting`, `expenseException`, existing Worker registry semantics, `ExpenseFixtureModelsTest` and later E driver.
- Why now: RED exists. BPMN is the entry graph whose referenced DMN and CMMN assets are populated next.
- Contract/signature changes: One test resource only. Reuse existing BPMN/Flowable/Worker/Task namespaces and accepted grammar.
- Input/output and state mapping: START → classify DMN → reserve original → internal agentStart → Message/Timer. Message COMPLETED selects notice+normal end, default failure selects compensation merge. Agent timeout selects ACTION review then linked Case; Case completion selects the same merge, Case timeout reaches distinct failed witness. Input `operationId:STRING` for reserve/notify/undo; only agentStart maps `amount→requestedAmount` and `runId→agentRunId`.
- Error and edge behavior: Preserve the one splitting exclusive gateway and two-input merge, successful original edge dominance, one noninterrupting compensation boundary/One association, and off-graph compensation handler. Keep case-timeout separate from compensation/normal success. The only later substitution candidates are exact declared role attributes, which currently contain synthetic finite slots.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Reuse existing semantics and literal `PT20S/PT3M` as Duration evidence, no deployment.
- Literal rule enforcement: Rule 2, Rule 10, Rule 11. Real model validation, checked literal durations, test resources in current PS module.
- Implementation pseudocode:

```xml
<!-- Copy exact reviewed fixture bytes from model-draft/processes/expense-review.bpmn20.xml. -->
<process id="expenseReview">
  <!-- classifyExpense uses expenseRouting, then exact reserveExpense, agentStart and agentWait -->
  <!-- agentStatusRouting is the sole split; onAgentSuccess uses agentStatus == 'COMPLETED' -->
  <!-- waitAgentTimeout PT20S => reviewExpense ACTION finding + linked expenseException -->
  <!-- waitCaseTimeout PT3M => caseTimeoutEnd; completion and default failure => compensationJoin -->
  <!-- throwUndo waits for completion, targets reserveExpense; undoReservation sits off normal graph -->
</process>
```

- Verification contribution: Exact SHA-256 `5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6`; model/graph/negative U1.
- After this file: BPMN presence succeeds but remaining resources are absent. No model is published or deployed.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/decisions/expense-routing.dmn`

- Purpose: Supply the fixed decision, rule IDs and governed task lane output for each expense path.
- Symbols: `expenseRouting`, `expenseRouteTable`, `smallExpense`, `largeExpense`, `reviewLane`.
- Repository evidence: `ProcessModelValidator#validateDmn,validateDecisionBinding,validateHumanTaskBindings`, `HumanTaskFlowableScenarios#verifyDmnRoutes`, existing DMN model tests. Pure probe verified exact input/output maps and ordered rule IDs.
- Dependencies and consumers: BPMN `classifyExpense` and `reviewExpense`, typed finite test and the later read-only E DMN witness.
- Why now: Satisfy the exact BPMN decision reference before testing the bundle.
- Contract/signature changes: One test resource using current DMN grammar and FIRST hit policy.
- Input/output and state mapping: `amount:INTEGER` maps `<= 1000` to `smallExpense/standard`, `> 1000` to `largeExpense/exception`; `reviewLane:STRING` is exactly the existing ACTION lane input.
- Error and edge behavior: No overlap or unknown output lanes. Boundary 1000/1001 is classified by model literals and checked without an engine claim. Replacing `exception` with a syntactically valid unconfigured lane must fail the combined validator.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001. Reuse accepted DMN binding and fixtures.
- Literal rule enforcement: Rule 2 and Rule 11. Exact typed input/output and same owner test-only package.
- Implementation pseudocode:

```xml
<!-- Copy exact reviewed fixture bytes from model-draft/decisions/expense-routing.dmn. -->
<decision id="expenseRouting">
  <decisionTable id="expenseRouteTable" hitPolicy="FIRST">
    <!-- amount numeric; reviewLane string -->
    <!-- smallExpense: &lt;= 1000 -> 'standard'; largeExpense: &gt; 1000 -> 'exception' -->
  </decisionTable>
</decision>
```

- Verification contribution: Exact SHA-256 `94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74`; binding and lane negative tests.
- After this file: BPMN and DMN can be checked together; linked Case resource and canonical JSON remain.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/cases/expense-exception.cmmn`

- Purpose: Supply the linked governed formal Case that has a bounded native completion path.
- Symbols: `expenseException`, `primary`, `supplemental`, `milestone`; `primaryTask`, `supplementalTask`, `completionMilestone`.
- Repository evidence: `CmmnModelValidator#validate`, existing `governed-case.cmmn`, `CaseFlowableScenarios#verifyLinkedCompletion`; pure converter proof reads all three items and exact sentry count.
- Dependencies and consumers: BPMN `caseSource` and exact `CaseSourceRegistry.BindingKey`, current Case SDK and governed task tests.
- Why now: Resolve the exact Case source before pinning all model identities in JSON.
- Contract/signature changes: One test resource using existing CMMN namespaces and accepted formal actions.
- Input/output and state mapping: Required manually activated primary; primary complete activates supplemental sentry; supplemental completion or permitted termination fires the milestone, allowing true native Case completion in E. `autoComplete=false`; same bounded governed role slots as the reviewed template.
- Error and edge behavior: Reject a different requested Case key, excessive roles, or changes that loosen sentries/actions. Whole-Case termination is outside this fixture. The CMMN validator/converter proof does not itself show actual completion.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001. Reuse native grammar; no application registration.
- Literal rule enforcement: Rule 2 and Rule 11. Exact release/Case linkage and existing technical test profile.
- Implementation pseudocode:

```xml
<!-- Copy exact reviewed fixture bytes from model-draft/cases/expense-exception.cmmn. -->
<case id="expenseException">
  <casePlanModel id="casePlan" autoComplete="false">
    <!-- primary -> primaryCompleted -> supplemental -->
    <!-- supplementalCompleted OR supplementalTerminated -> milestone -->
    <!-- primaryTask roles 502,602 and supplementalTask roles 503,603 are declared synthetic slots -->
  </casePlanModel>
</case>
```

- Verification contribution: Exact SHA-256 `c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1`; formal Case policy, native structure and binding-key tests.
- After this file: Exact XML set can pass standalone validation. Canonical JSON is the remaining presence gap.

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json`

- Purpose: Establish the canonical neutral fixture bytes for PS and later Web test consumers.
- Symbols: Root `schemaVersion, releaseVersion, models, startInputs, paths, humanTask, caseInstance`; exact SHA-256 `e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7`.
- Repository evidence: Existing C `HumanTaskDTO`, `CaseInstanceDTO`, `CasePlanItemDTO`; PS `HumanTaskSnapshot` and `CaseSnapshot#projection,availableActions`; matching Web consumer validators. The reviewed off-repo candidate is 3084 UTF-8 bytes, LF and one terminal newline.
- Dependencies and consumers: Files 2–4 exact hashes, File 1 neutral DTO decoding, later Steps' fixture expectations and Step 5 Web byte copy. This fixture is synthetic and carries no live evidence.
- Why now: Pin model keys/bytes only after they exist. This is the last resource needed to clear the presence gate.
- Contract/signature changes: Version 1 closed test fixture. Canonical file is a direct reviewed byte copy, not an independently regenerated JSON document.
- Input/output and state mapping: `expense-v1` with typed START schema, BPMN/DMN/CMMN exact keys/SHA; amounts/rules/lanes NORMAL 100/smallExpense/standard, AGENT_FAILURE 700/smallExpense/standard, MANUAL_CASE 1500/largeExpense/exception, RECOVERY 200/smallExpense/standard. ACTION Task `reviewExpense` in `exception` with one `finding:STRING`; Case active at primary completed, supplemental enabled, milestone available. DTO timestamps are synthetic UTC microsecond strings, versions are explicit safe opaque strings.
- Error and edge behavior: Pin full bytes. Any schema/key/digest drift fails the finite gate. Fixture contains no live credentials, role IDs, native internal diagnostics or fabricated E results.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 3, Rule 6, Rule 10, Rule 11. Exact validated immutable existing DTOs, named enums, explicit nulls and UTC, test-only.
- Implementation pseudocode:

```json
{
  "schemaVersion": 1,
  "releaseVersion": "expense-v1",
  "models": {
    "BPMN": {"definitionKey": "expenseReview", "sha256": "5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6"},
    "DMN": {"definitionKey": "expenseRouting", "sha256": "94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74"},
    "CMMN": {"definitionKey": "expenseException", "sha256": "c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1"}
  },
  "startInputs": {"operationId": "STRING", "amount": "INTEGER"},
  "paths": {
    "NORMAL": {"amount": 100, "ruleId": "smallExpense", "reviewLane": "standard"},
    "AGENT_FAILURE": {"amount": 700, "ruleId": "smallExpense", "reviewLane": "standard"},
    "MANUAL_CASE": {"amount": 1500, "ruleId": "largeExpense", "reviewLane": "exception"},
    "RECOVERY": {"amount": 200, "ruleId": "smallExpense", "reviewLane": "standard"}
  },
  "humanTask": {"kind": "ACTION", "taskDefinitionKey": "reviewExpense", "formSchema": {"finding": "STRING"}},
  "caseInstance": {"definitionKey": "expenseException", "status": "ACTIVE", "planItems": ["primaryTask", "supplementalTask", "completionMilestone"]}
}
```

- Verification contribution: The pseudocode describes required fields only. Copy the exact full reviewed `model-draft/fixtures/expense-four-path-contract.json`; its typed nested DTO projections, byte length and SHA are checked in File 1 and Step 5.
- After this file: Presence is GREEN. Complete File 1's final model and neutral DTO assertions and run U1.

#### File 6 — `CREATE docs/egon/spec/2026-11-05-15-13-expense-four-path-acceptance.md`

- Purpose: Preserve the accepted requirements and runtime verification boundaries in the owning repository.
- Symbols: Effective T3-12 Spec, REQ-001–008, U1–U4 and E0–E4.
- Repository evidence: Existing service Spec relationships and T3-10 recovery/compensation plus T3-08 formal Case. Root accepted normalization preserves the four-path semantics.
- Dependencies and consumers: Exact accepted off-repo bytes, source baselines and model/fixture digests; linked Plan File 7.
- Why now: Commit the accepted requirement authority beside the first source slice and its current evidence.
- Contract/signature changes: Documentation only. The Plan link may be the approved metadata-only addition.
- Input/output and state mapping: Effective accepted Spec and exact relationship anchors to the repository Spec path, with finite and runtime gates distinct.
- Error and edge behavior: Scope, status or substantive content drift returns to Root before staging. Draft claims or provisional runtime results do not qualify as accepted evidence.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Preserve test-only ownership and current immutable migrations.
- Implementation pseudocode:

```text
verify accepted Spec identity, exact target path and approved Related Plans link
copy reviewed Spec to service docs/egon/spec
run writing-spec validator in strict mode once linked Plan exists
```

- Verification contribution: Strict Spec structure and exact relationship/effective-design review.
- After this file: Requirement authority is in the worktree and awaits the linked Plan/evidence.

#### File 7 — `CREATE docs/egon/plan/2026-11-05-15-30-expense-four-path-acceptance.md`

- Purpose: Publish the accepted ordered Steps and precise repository scopes.
- Symbols: Effective T3-12 Plan, Steps 1–5, source and separately authorized E gates.
- Repository evidence: Existing service Plan/evidence conventions and current root scope of PS/AS test source plus Web two-path commit.
- Dependencies and consumers: File 6 and the exact reviewed Plan, later Step gates and repo-local commits.
- Why now: Freeze the reviewed execution sequence and dependency boundary in the same first service commit.
- Contract/signature changes: Documentation only, exact primary Spec and complete effective dependencies.
- Input/output and state mapping: Approved Plan bytes to exact service path. Runtime gating and status remain the actual evidence.
- Error and edge behavior: If path lists, selected commands or dependency hashes differ from approved Plan, halt before staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Keep both repository scopes and existing source/SQL owner boundaries explicit.
- Implementation pseudocode:

```text
verify approved Plan identity and Spec links
copy approved Plan to exact service docs/egon/plan path
run writing-plan validator --strict and inspect Step 1 owned paths
```

- Verification contribution: Strict Plan/Spec check and exact staged-scope audit.
- After this file: Approved design and sequence are linked locally; actual Step 1 execution evidence remains.

#### File 8 — `CREATE docs/egon/evidence/2026-11-05-expense-four-path-models.md`

- Purpose: Record Step 1's actual RED, U1 GREEN, four resource hashes and classpath/model boundaries.
- Symbols: Service baseline and source hashes, exact four resource paths, current selected reports and separate unexecuted E boundary.
- Repository evidence: Existing test fixture, Worker recovery and formal Case evidence separates pure/static source results from runtime.
- Dependencies and consumers: Actual current execution reports and strict validators, canonical lock consumed by Step 5 and later E fixture setup.
- Why now: Close the Step only after its actual finite results and provenance are known.
- Contract/signature changes: Documentation only. Record four exact raw-byte SHA-256 and selected test result counts.
- Input/output and state mapping: Actual current RED output, U1 report XML, reviewed hash/byte parity and scoped worktree audit to evidence. Link local source/Spec/Plan paths.
- Error and edge behavior: No guessed counts, historical substitutions or claim of actual engine deployment. Missing or skipped selected test or wrong digest blocks the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Preserve no-application and no-database boundary and all immutable migrations.
- Implementation pseudocode:

```text
record actual source baseline, RED and U1 commands/reports and exact approved fixture digests
record pure converter scope and E0–E4 pending separately
record staged path list and leave the full commit ID available for Step 5 source-copy provenance
```

- Verification contribution: Reproducible first-slice evidence and canonical fixture source-commit lock.
- After this file: Step 1 is ready for exact path-limited commit only after all verification gates pass.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Use the two exact selector phases below. Before GREEN, copy Files 2–5 in declared order and compare each candidate byte-for-byte. Verify their digests against the values in Files 2–5. Run the Spec and Plan validators from the sibling skill root and `git diff --check`, then inspect exact staged paths and `git diff --cached --check`.

```sh
expense_u1_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
run_expense_u1() {
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$expense_u1_settings" -s "$expense_u1_settings" \
    -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
# First call after File 1 Stage A, before creating any resource.
run_expense_u1 'ExpenseFixtureModelsTest#modelResourcesExist'
# After observed RED, create the approved resource files in order, then verify exact bytes.
expense_candidate_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/four-path-inventory/model-draft
expense_test_resources=stellar-enclosure-tianji-process-server/src/test/resources
for expense_relative in processes/expense-review.bpmn20.xml decisions/expense-routing.dmn \
  cases/expense-exception.cmmn fixtures/expense-four-path-contract.json; do
  expense_dest="$expense_test_resources/$expense_relative"
  test ! -e "$expense_dest" && test ! -L "$expense_dest" || exit 1
  mkdir -p "$(dirname "$expense_dest")" || exit 1
  (set -C; cat "$expense_candidate_root/$expense_relative" > "$expense_dest") || exit 1
  cmp "$expense_candidate_root/$expense_relative" "$expense_dest" || exit 1
done
shasum -a 256 -c <<'SHA256'
5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6  stellar-enclosure-tianji-process-server/src/test/resources/processes/expense-review.bpmn20.xml
94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74  stellar-enclosure-tianji-process-server/src/test/resources/decisions/expense-routing.dmn
c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1  stellar-enclosure-tianji-process-server/src/test/resources/cases/expense-exception.cmmn
e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7  stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json
SHA256
# Separate call after Files 2–5 and full File 1 checks.
run_expense_u1 ExpenseFixtureModelsTest,ProcessModelValidatorTest,CmmnModelValidatorTest,WorkerCompensationModelTest,HumanTaskModelTest,HumanTaskTimerModelTest
# After publishing the approved docs in Files 6 and 7.
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-15-13-expense-four-path-acceptance.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-15-30-expense-four-path-acceptance.md --strict
git diff --check
```

- Expected result: RED actual selected test, 1 assertion failure, 0 errors, 0 skipped for absent fixture resources. GREEN all six named classes execute, including all four new methods, and no failures, errors or skips. Candidate/committed byte parity, strict docs and exact staged scope pass.
- Failure returns to: File 1 for compilation/fixture assertions. Files 2–5 for approved source bytes and combined bindings. File 6/7 for structural/link problems. Substantive model or contract mismatch returns to effective Spec review before changing locked bytes.
- Completion criteria: One model pack verifies with current validators and pure converters, neutral JSON verifies exact hashes and DTOs, and stage evidence has every selected observed result. U1 does not prove deployed BPMN/DMN/CMMN or E.
- Rollback: Revert only the Step 1 service commit if later work has not consumed the pinned fixture. Once consumers depend on it, coordinate forward-fix or ordered consumer revert across owners.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseFixtureModelsTest.java`, `stellar-enclosure-tianji-process-server/src/test/resources/processes/expense-review.bpmn20.xml`, `stellar-enclosure-tianji-process-server/src/test/resources/decisions/expense-routing.dmn`, `stellar-enclosure-tianji-process-server/src/test/resources/cases/expense-exception.cmmn`, `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json`, `docs/egon/spec/2026-11-05-15-13-expense-four-path-acceptance.md`, `docs/egon/plan/2026-11-05-15-30-expense-four-path-acceptance.md`, `docs/egon/evidence/2026-11-05-expense-four-path-models.md`.
- Commit: `test(process): pin expense acceptance models and fixture`.

### Step 2 — Enforce the operator boundary and causal test transport

- Requirements: REQ-004, REQ-005, REQ-006, REQ-007. Spec §5, §7.3, §9–11, §14 U2/E0.
- Dependencies: Step 1 model/contract commit and exact reviewed prerequisite SHAs.
- Baseline state: U1 has the shared model and contract. Existing ProcessServerPostgresTest uses MockMvc or substituted transports. There is no independent child harness, private manifest or causal TLS relay.
- Observable outcome: finite tests prove explicit manifest admission, causal fault transitions and owned-child cleanup using doubles; unauthorized/invalid inputs cannot begin real setup, traffic or launches.
- End state: exactly three PS test sources; U2 and affected U1 GREEN, no E run.
- Test-first gate: Required. First reflective presence RED. Compile signature-only A with fixed unavailable bodies and replace reflection with the typed assertion RED. Compile, setup and assumption failures do not count.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java`

- Purpose: specify U2 on finite JUnit without launching Spring, opening listeners or connecting to DB.
- Symbols: `missingAcceptanceHarnessSeams` (temporary), `acceptsOnlyExplicitOwnedManifestWithoutSideEffects`, `validatesExactArtifactsTwoTenantsAndIndependentStores`, `childArgumentsExposeOnlyPrivateConfigurationReferences`, `causalFaultsDoNotMatchForeignTrafficOrAdvanceOutOfOrder`, `timeoutAndCleanupAffectOnlyOwnedChildren`, `publishesCompleteRedactedEvidenceOnce`.
- Repository evidence: PS tests run JUnit with existing Jackson3; `ProcessServerPostgresTest` explicitly opts into runtime.
- Dependencies and consumers: JUnit, Jackson3, `@TempDir`, UTC Clock, monotonic supplier, fake launcher/transport/read-only witnesses; uses Files 2–3.
- Why now: tests establish the behavior before any lifecycle/relay implementation. Reflection compiles without new types; A declares closed types before typed tests.
- Contract/signature changes: test class only; type and method contract per Files 2–3. Test approval absent differs from explicitly present but invalid input.
- Input/output and state mapping: 0700 private root/0600 fixtures on supported host. Verify invalid input causes zero real actions. Exact sent witness and evidence limited to synthetic IDs.
- Error and edge behavior: duplicate/missing/unknown fields, oversized or malformed JSON, insecure/symlinked paths, duplicate tenants/ports/DB identities/roles, bad hashes or commits, unsafe host, incorrect phase/fingerprint, expiry, unrelated child and no-clobber report.
- Standards impact: MC-MODEL-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001 — test-only immutable values, exact resource and time validation, no Spring Bean.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 semantic records and strict boundary; 4–5 JUnit/JDK with fixed diagnostics; 6 existing Jackson; 7 production config untouched; 9 Scenario Orchestration; 10 Instant/Duration and fake monotonic time; 11 test-only paths.
- Implementation pseudocode:

```java
// Reflection-only RED, replaced after A
assertDoesNotThrow(() -> Class.forName(HARNESS_CLASS));
assertDoesNotThrow(() -> Class.forName(RELAY_CLASS));
// Typed RED then B
assertEquals(Set.of(tenantA, tenantB), harness.readManifest(validFile, fakeTargets).tenantIds());
assertThrows(IllegalArgumentException.class, () -> harness.readManifest(wrongSha, fakeTargets));
assertEquals(0, fakeLauncher.starts());
assertFalse(harness.childCommand(AGENT, lockedArtifact, privateConfig).containsSecret());
assertEquals(PASS_THROUGH, relay.decide(unrelatedAuthorizedExchange, monotonicNow));
assertThrows(IllegalStateException.class, () -> phase.advance(OUTBOX_RETRY_CONFIRMED));
```

- Verification contribution: exact U2 class reflection RED, typed RED, GREEN; own reports must execute tests.
- After this file: reflection can fail on clean baseline; typed tests activate only once A compiles.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: reusable test driver for manifest, causal phase clock, verified child lifecycle, bounded witnesses and redacted report.
- Symbols: package-local `ExpenseAcceptanceHarness implements AutoCloseable`; `readManifest`, `open`, `startChild`, `replaceChild`, `await`, `publishEvidence`, `close`; nested immutable `ExpenseAcceptanceManifestDTO`, `ArtifactLockDTO`, `TenantBindingDTO`, `StoreBindingDTO`, `ChildIdentityDTO`, `ExpensePathResultDTO`, `ExpenseEvidenceDTO` and closed phase/witness enums. Nest small fake launcher/clock seams; expose only lifecycle, scoped observation and reports to ITs.
- Repository evidence: AS/PS Boot POM `exec` classifiers, source-reactor wrapper, `ProcessServerPostgresTest` catalog/role guard; README documents separately provisioned Agent/Process/Worker stores. PS V1/V3/V5/V6/V7/V8/V10 and AR V1/V7 are existing witness sources.
- Dependencies and consumers: JDK ProcessBuilder, Java 25, Jackson3, existing JDBC/TLS support; later ITs call; PS has no AS build dependency. Locate each locked module `target/test-classes` and matching exec JAR from manifest. No runtime work during finite selector.
- Why now: verified input and ownership must precede HTTPS/child source and all business fixture effects.
- Contract/signature changes: absolute `tianji.expense.e2e.manifest` input (≤64 KiB), closed schemaVersion=1, unique runId, runAuthorized=true, exact revision/artifact paths/SHA, two distinct scoped tenants and role IDs/credential-file refs, three disposable stores with separate setup/runtime/read-only roles, TLS file refs and fixed isolated endpoints, approved collector, bounded phase durations, owner-private output directory. Missing property is a missing opt-in only in explicit IT; defined empty/invalid/false hard-fails before external effects.
- Input/output and state mapping: validate shape, POSIX owner/private permissions within designated private root, and all path ancestors with no symlinks; restrict credentials/config/evidence under private root and build artifacts under locked checkout; SHA class trees by sorted UTF-8 relative path length plus per-file digest. No manifest arbitrary SQL or proxy destination. Verify actual separate DB server identity/port/database, catalog versions, native/migration histories, role capabilities and empty run scope before child startup. Process V1–V10 plus native Flowable/Outbox, Agent V1–V14 with pinned pgvector and separate Outbox, Worker V1/V2 are operator-provisioned before E. Only Step 3 fixture-specific setup writes are automatic in authorized E. Distinct source schema/versions/authority binding must survive replacement.
- Error and edge behavior: construct CREATE_NEW 0600 private child property file with only existing keys, a path passed via `--spring.config.additional-location=file:...`. Clear inherited Java/Spring overrides, proxy and secrets. Actual ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false in child env, no secret values in args. Keep raw process output away from committed/public artifacts and bound/discard it. Child readiness proves real HTTPS plus authenticated existing read and distinct live OS PID/start instant. Close only handles spawned here, with graceful 10 s plus 5 s bounded escalation. No broad kill, environment deletion or fallback default. Publish a completed private temp sibling atomically without overwriting final (same-FS hard link to completed CREATE_NEW temp, unlink temp), failure remains explicit non-success.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001 — test-only immutable metadata/evidence and closed enum, strict role/route/config affinity, bounded diagnostic output.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 semantic immutable values, trusted context and explicit validation; 4 fixed diagnostics; 5 approved JDK/JDBC/JSON; 6 strict Jackson; 7 private config only; 9 causal phases; 10 monotonic deadline and source UTC witnesses; 11 no production layers, DDL or new Event.
- Implementation pseudocode:

```java
var permit = parseStrict64KiB(readOwnerPrivateNoFollow(manifest));
requireAuthorizedTwoTenantThreeStoreLock(permit);
verifySourceCatalogAndMigrationsReadOnly(permit);
var argv = List.of(lockedJava, "-Dloader.main=" + roleMain, "-Dloader.path=" + exactTestClasses,
    "-cp", exactExecJar, "org.springframework.boot.loader.launch.PropertiesLauncher",
    "--spring.main.web-application-type=servlet", "--spring.config.additional-location=" + privateFileUri);
var child = ownedLauncher.start(argv, cleanEnv, privateRunRoot);
await("readiness", Duration.ofSeconds(60), exactTlsAndAuthenticatedRead);
var replacement = replaceChild(role, child.pid(), child.startInstant());
requireDistinctNewPidAndStartInstant(child, replacement);
publishAtomicallyNoReplace(checkedReport); // PROVED/NOT_REACHED/UNAVAILABLE/PENDING remain distinct
```

- Verification contribution: U2 operates on synthetic temp files, no DB/child/HTTPS; future E0 uses real assets and secure probes. Poll delays are pacing only, causal witnesses decide progress.
- After this file: lifecycle and manifest are implemented; relay and application fixtures complete remaining seams.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`

- Purpose: exact scoped HTTPS relay with causal faults around existing client calls.
- Symbols: `ExpenseAcceptanceFaultRelay implements AutoCloseable`; nested immutable `FaultWindowDTO`/`ObservedExchangeDTO`; `arm`, `decide`, `awaitObserved`, `release`. States DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT, HOLD_LINKED_SUCCESS, DEFER_RESULT_UNTIL_TIMER_OR_RESTART, DROP_COMMITTED_RESULT_202, OBSERVE_REPLACEMENT_RESEND.
- Repository evidence: CL `HttpProcessClient` Worker acquire/complete/fail at `POST /api/tianji/v1/worker-tasks/{acquire,complete,fail}`; CL `HttpLinkedAgentClient` and AS linked controller use `POST /api/tianji/v1/agent-process-dispatches/{submit,resolve,close}`; `HttpAgentResultClient.receive` uses `POST /api/tianji/v1/agent-result-inbox`; production `AgentResultDeliveryHandler.deliver` already retries uncertain 202.
- Dependencies and consumers: JDK HttpsServer/HttpClient and approved SSLContext, limited executor/queue, exact W→PS/PS→AS/AS→PS listener/upstream pairs; E calls preserve real endpoint IAM. No new production endpoint.
- Why now: finite phase guards precede actual TLS wiring; shared transport drives all causal recovery windows.
- Contract/signature changes: exact POST allowlist per direction, raw path and fixed upstream, strict bounded JSON inspection for allowed scope/operation/event only. Forward required Authorization/tenant/content headers and original payload bytes unchanged; no query, redirect, arbitrary headers or trust-all. Scope header is relay correlation only, never authorization.
- Input/output and state mapping: enforce existing per-route caps (linked submit ≤2 MiB, linked queries ≤16 KiB, Worker ≤256 KiB, terminal envelope ≤16 KiB per owner delivery handler) and bounded response (existing SDK cap); unselected valid requests pass; forbidden route/malformed inputs fail without forwarding. Fault consumes one explicit causal arm. Use read-only exact durable SQL witness from harness, and in-memory comparison only for actual Worker leases.
- Error and edge behavior: drop complete before any upstream call, only after observed matching BusinessDB commit; hold original linked 202 after exact committed AS run, release within ≤10 s and before HTTP/lease; suppress exact terminal attempt recoverably until named Timer/restart without fake 202; discard actual Inbox 202 only after read-only committed-row witness; exact later same event ID/fingerprint from normal Outbox, no relay resend. Unknown/wrong/duplicate arm and timeout fail phase. Keep headers, tokens and bodies out of evidence.
- Standards impact: MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001 — bounded test adapter and actual TLS preserved, no alternate success path or retry policy.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 immutable closed scope/phase; 4 safe fixed logs; 5 JDK TLS/Jackson; 6 validate selected metadata while forwarding original bytes; 7 no profile edits; 9 atomic causal orchestration; 10 source DB clock and elapsed deadlines; 11 test-only relay.
- Implementation pseudocode:

```java
var key = checkedMethodRawPathScopeAndBoundedBody(exchange);
if (!arm.matches(key)) return forwardOnceOriginalBytes();
switch (arm.phase()) {
    case DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT -> { requireCommittedBusinessReceipt(key); claimOnce(arm); closeBeforeUpstream(); }
    case HOLD_LINKED_SUCCESS -> { var reply = forwardOnceOriginalBytes(); requireActual202AndCommittedAgentRun(reply, key); awaitRelease(arm); return reply; }
    case DEFER_RESULT_UNTIL_TIMER_OR_RESTART -> deferRecoverablyWithoutSuccess(arm);
    case DROP_COMMITTED_RESULT_202 -> { var reply = forwardOnceOriginalBytes(); requireActual202AndCommittedInbox(reply, key); claimOnce(arm); closeWithoutResponse(); }
    case OBSERVE_REPLACEMENT_RESEND -> { verifySameEventAndFingerprint(); return forwardOnceOriginalBytes(); }
}
```

- Verification contribution: U2 verifies fault selection/phase/timeout with doubles. E4 alone proves real HTTPS/ACK lifecycle.
- After this file: U2 completed, fixture mains and E choreography follow.
- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: `t3_12_finite ExpenseAcceptanceHarnessTest` for reflection RED, typed RED, then GREEN, followed by `t3_12_finite ExpenseAcceptanceHarnessTest,ExpenseFixtureModelsTest,ProcessModelValidatorTest,CmmnModelValidatorTest,WorkerCompensationModelTest`.
- Expected result: intentional F>0/E=0/S=0 at RED, exact owning reports N>0 and F/E/S=0 at GREEN, no E selector or listener.
- Failure returns to: File 1 for fixture/discovery, File 2 for manifest/ownership and File 3 for relay behavior. Test-compilation failure is not RED.
- Completion criteria: exact path diff and finite reports, no secret or local runtime artifacts staged.
- Rollback: focused revert of the one test-source commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`.
- Commit: `test(process): add isolated expense acceptance harness`

### Step 3 — Bind isolated Boot and transactional Worker fixtures

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007. Spec §5, §8–11, §14 U3/E0.
- Dependencies: exact Steps 1 and 2; no AS import from PS.
- Baseline state: private harness exists without app-specific child mains or notice effect.
- Observable outcome: bounded deterministic model through actual assembly/adapter and two isolated test mains plus real Worker fixture compile using current dependencies.
- End state: four test sources, U3 GREEN, both exec archive boundaries checked, E unexecuted.
- Test-first gate: Required. Reflection RED for AS main, compile A, typed positive model RED, then behavior. Package verification checks PS and Worker; actual E ownership remains Step 4.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAcceptanceModelTest.java`

- Purpose: finite Fake outcome contract through actual assembler and adapter.
- Symbols: `acceptanceAgentMainExists` (temporary), `fixedRequestedAmountRoutesThroughActualAssemblerAndAdapter`, `duplicateAndUnsupportedMarkersFailClosed`.
- Repository evidence: AS `TianjiGrantDispatchPostgresTest` fixed CONFIG and usage; AR `DefaultContextAssembler.assemble` emits a distinct structured USER JSON part; Components `SpringAiModelAdapterFactory` and native adapter regressions.
- Dependencies and consumers: current JUnit/Spring AI/ADK, C AgentInputDTO, actual assembler/adapter and File 2 factory. No context, PG or IAM.
- Why now: positive Fake behavior asserted before child wiring; reflection first, typed after A.
- Contract/signature changes: test only; exact one structured USER schemaVersion=1/taskData.requestedAmount integer.
- Input/output and state mapping: 100/1500/200 fixed streaming text and usage, 700 deterministic adapter failure. Reject duplicate, fractional/string, unknown, oversized or prose-only marker.
- Error and edge behavior: finite subscription deadline; no fabricated Agent terminal or authenticated HTTP success.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-TEST-001 — immutable test values and current framework input validation.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 closed semantic input, 4 no Bean, 5–6 existing JDK/Jackson/adapter, 7 no profile, 9 fixed scenario seam, 10 Duration, 11 test-only.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName(AGENT_TEST_MAIN)); // initial RED only
var input = new AgentInputDTO("fixture", List.of(), Map.of("requestedAmount", amount));
var assembled = new DefaultContextAssembler(List.of()).assemble(input);
var stream = realSpringAiAdapter(expenseFixtureModel()).generateContent(toAdkRequest(assembled), true);
assertOutcomeUnderDeadline(stream, amount != 700);
assertRejected(duplicateStructuredPart, unknownAmount, markerOnlyInProse);
```

- Verification contribution: U3 genuine reflective/typed assertion RED and GREEN.
- After this file: reflective test runs before A, then typed tests activate.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: real isolated AS Boot, substituting only the deterministic ChatModel.
- Symbols: `ExpenseAgentAcceptanceMain.main(String[])`, nested explicit `@TestConfiguration(proxyBeanMethods=false)`, `@Bean("expenseFixtureModel") ChatModel expenseFixtureModel()`, fixed CONFIG `expenseAnalysis`/version 1, provider `expense-fixture-model`, `AgentLedgerService.publish`.
- Repository evidence: `TianjiAgentServerApplication`, module exec classifier, `TianjiGrantDispatchPostgresTest`, existing runtime/grant/linked guards.
- Dependencies and consumers: AS test classes only, Step 2 PropertiesLauncher; explicit test config prevents ordinary scan. No Fake IAM, ledger, dispatcher or result endpoint.
- Why now: A declares safe failing model factory; B implements after typed RED.
- Contract/signature changes: use private tested property reference and existing `stellar.tianji.agent.storage/api/runtime/reconciliation/dispatch/linked/service-execution/terminal-events/result-delivery` plus actual trust/grant keys. Existing model-budgets profile or scoped estimator SPI only if verified necessary; retain quota/grant policy.
- Input/output and state mapping: exact structured amount, fixed Spring AI Generation/ChatResponse stop and usage (10,5,15), 700 fixed publisher failure. Seed immutable definition via existing ledger once per owned tenant under approved bootstrap actor; restart compares existing exact definition. Actual authorization and ledger source required. Both child wrappers load an approved private truststore before existing outbound clients initialize. Do not pass truststore passwords on arguments or replace hostname verification.
- Error and edge behavior: unsafe source/config, changed definition, unknown mode/budget or unavailable real grant hard-fails. No provider network, content capture or synthetic result.
- Standards impact: MC-ARCH-001, MC-BEAN-001, MC-CONFIG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001 — named test Bean, existing platform factories, fixed diagnostics.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 strict model/config/values; 4 named test-only Bean; 5–6 existing Spring AI/Jackson; 7 private config; 9 fixed model strategy; 10 real DB clock in E; 11 no production files/events.
- Implementation pseudocode:

```java
requirePrivateVerifiedChildInputs(args, AGENT);
installDefaultTlsTrustFromApprovedPrivateFileBeforeBoot();
var app = new SpringApplicationBuilder(TianjiAgentServerApplication.class, FixtureConfiguration.class)
    .web(WebApplicationType.SERVLET).run(args);
for (var tenant : approvedTenantBindings()) {
    verifyExistingTrustGrantAndLedgerSource(app, tenant);
    publishExactFixtureDefinitionOnce(app.getBean(AgentLedgerService.class), tenant);
}
returnFixtureStreamFromStrictStructuredRequestedAmount();
```

- Verification contribution: U3 actual assembler/adapter; Boot archive/test classes checked without startup.
- After this file: AS fixture finite green; actual AS startup belongs to E0.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`

- Purpose: real Process Boot with exact isolated configuration and existing authorities.
- Symbols: `ExpenseProcessAcceptanceMain.main(String[])`.
- Repository evidence: `TianjiProcessServerApplication`, `ProcessStorageConfiguration`, Worker/bridge/Inbox/Case existing configuration types.
- Dependencies and consumers: test child through PropertiesLauncher; actual ProcessDB and TLS/IAM, no AS imports.
- Why now: launch seam follows safe private launcher and approved model/AS fixture.
- Contract/signature changes: existing `stellar.tianji.process.api/worker/human-tasks/agent-bridge/agent-results/cases` keys. Enable existing flowable.async-executor-activate for native timers and completion. Disable automatic schema update, Spring SQL init and Flyway. Exact tenant-topic registry reserveBusiness/undoReservation/notifyExpense version 1 TRANSACTIONAL_RECEIPT, operationId STRING/no outputs; retain agentStart EXTERNAL_IDEMPOTENT with requestedAmount INTEGER/runId STRING. Two exact issuer/subject/source/release/rendered definition/Case bindings; render before boot so Case source and release configuration pin the actual model SHA.
- Input/output and state mapping: private approved config, existing application, actual HTTPS/authorized read readiness. Replacements use same target/config with new identity.
- Error and edge behavior: startup fails on ownership, role, DDL guard or wrong version; no grants or auto-migration. Fixed safe diagnostics.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-BEAN-001, MC-CONFIG-001, MC-SCOPE-001 — existing Bean/authority boundary and private profile overrides only.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 verified binding values; 4 existing named wiring; 5–6 Spring/JDK; 7 generated private input; 9 native orchestration; 10 native timer clocks; 11 test-only.
- Implementation pseudocode:

```java
requirePrivateVerifiedChildInputs(args, PROCESS);
installDefaultTlsTrustFromApprovedPrivateFileBeforeBoot();
requireNoAutomaticDdlAndExactRuntimeBindings(args);
new SpringApplicationBuilder(TianjiProcessServerApplication.class).web(WebApplicationType.SERVLET).run(args);
```

- Verification contribution: exact class/archive proof only. E0 owns runtime startup.
- After this file: both mains compile, production start classes unchanged.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/ExpenseBusinessWorkerFixture.java`

- Purpose: real isolated BusinessDB reserve/undo/notice with existing transactional Worker.
- Symbols: `ExpenseBusinessWorkerFixture implements AutoCloseable`, `prepareDisposableFixtureTables`, `registerExpectedOperation`, `openPoller`, `noticeHandler`, `readEffects`; same-package reuse `CompensationBusinessFixture.ExpectedOperationVO/originalHandler/undoHandler/createBusinessTables`.
- Repository evidence: `WorkerCompensationFlowableScenarios.provision/newStore/registration/seed`; PS `ProcessServerPostgresTest` uses actual `JdbcWorkerExecutionStore` and `RemoteWorkerPoller`.
- Dependencies and consumers: current W/CL/PS, authorized setup owner, limited same-source runtime manager, distinct evidence role; Step 4 ITs only.
- Why now: isolated runtime bindings and authenticated START exist before fixture setup/acquisition.
- Contract/signature changes: disposable `compensation_fixture.expense_notice` only, no migration. PK (source_id,tenant_id,operation_id), FK (source_id,tenant_id,original_receipt_id) to original receipt, restricted update/delete. Exact Spec fields, positive tenant and lower-hex IDs, DB UTC timestamptz(6). Runtime SELECT/INSERT notice only plus existing compensation grants. W V1/V2 remain unchanged.
- Input/output and state mapping: authenticated START→register exact source/tenant/UUID/instance/business/correlation and synthetic object before allowing acquisition. Real HttpProcessClient, JdbcWorkerExecutionStore, WorkerPollingProperties, RemoteWorkerPoller. Notice verifies active same-source READ_COMMITTED, deadline and exact original receipt, computes scoped digest and inserts/replays once in same Store transaction. Recovery sets existing acknowledgementAttempts=1 so lost complete waits native lease expiry. Expected reserve/notice/undo 1/1/0 or 1/0/1.
- Error and edge behavior: wrong/unregistered context, unowned role, conflict and expired deadline refuse before effect. No network/payment/mail in handler, no fixture drop or unowned shutdown.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001 — current Worker technical transaction and same-package handler reuse, immutable proof.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 exact source/expected values; 4 fixed diagnostics; 5–6 existing JDBC/DTO/hash; 7 test config; 9 finite phases; 10 DB clock; 11 test-only DDL.
- Implementation pseudocode:

```java
CompensationBusinessFixture.createBusinessTables(setupJdbc);
executeFixedExpenseNoticeDdlAndLimitedGrants(setupJdbc);
seedAndRegisterBeforeAcquisition(exactExpectedOperation(authenticatedStart, fixtureSeed));
TransactionalWorkerHandler notice = (context, jdbc) -> {
    requireExactContextAndBoundReadCommitted(context, jdbc);
    var original = readExactOriginalReceiptAndVerifyDbDeadline(context, jdbc);
    var id = digestScopedNotice(original, context);
    insertOnceOrVerifyExactReplay(jdbc, original, id);
    return new WorkerBusinessResultDTO(id, Map.of());
};
new RemoteWorkerPoller(actualHttpSdk, actualJdbcStore, exactRegistrations, ownedScheduler,
    boundedExecutor, workerPollingProperties, Clock.systemUTC()).start(); // authorized E only
```

- Verification contribution: source compile, existing U3 Worker/compensation regressions; actual receipt and effects belong to Step 4.
- After this file: fixture APIs compile for IT source; E remains unexecuted.
- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: `t3_12_finite ExpenseAcceptanceModelTest` reflection RED, typed RED, GREEN; final `t3_12_finite ExpenseAcceptanceModelTest,ExpenseAcceptanceHarnessTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,CompensationBusinessFixtureTest,AgentStartWorkerTest,AgentResultDeliveryHandlerTest,RemoteWorkerPollerTest`. Verify both test entry class files and exact production Boot archive manifest, no main invocation.
- Expected result: intentional F>0/E=0/S=0 RED and exact N>0/F=E=S=0 GREEN. Test mains absent from BOOT-INF/classes and production Start-Class stable.
- Failure returns to: File 1 fixture, File 2 model, File 3 PS guard, File 4 Worker transaction; real startup remains E0.
- Completion criteria: four exact paths, finite XML and package proof, no POM/production configuration/SQL migration.
- Rollback: revert this single fixture-source commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAcceptanceModelTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/ExpenseBusinessWorkerFixture.java`.
- Commit: `test(tianji): add real expense acceptance fixtures`

### Step 4 — Compile all four owned runtime scenarios and lock the separate E invocation

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007. Spec §7.2–3, §9–11, §14 E0–E4.
- Dependencies: exact Step 1 models, Step 2 harness/relay, Step 3 real fixture commits; no production or web edit in this Step.
- Baseline state: all model/fixture/harness U gates GREEN, no E scenario source.
- Observable outcome: compile complete scenario orchestration for all four paths/two tenants, preserve explicit opt-in, and prove neither E class executed during source gates.
- End state: both exact IT test-class artifacts and scenario evidence contract; runtime acceptance unexecuted unless separately authorized after this Step.
- Test-first gate: Not applicable for runtime behavior in this source-only Step because Spec §14 expressly separates E and prohibits the owner-specific runtime prerequisites during finite gates. Record a real source/artifact presence assertion RED before new paths, then verify exact testCompile/packaging and existing finite selectors. Do not label that behavioral RED or any opt-in skip a pass. Existing U1–U3 behavior gates cover shared code independently.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: actual E1/E2/E3 scenario assertions, no lifecycle duplication.
- Symbols: `BusinessProcessAgentAcceptanceIT`, `normalWorkflowAcrossBothTenants`, `failedAgentCompensatesAcrossBothTenants`, `manualCaseAndAuthorizedReviewAcrossBothTenants`. Share operator-approved lifecycle and one immutable release per tenant between methods with ordered E fixture setup. Keep all actual assertions in this class, not detached report post-processing.
- Repository evidence: existing C ProcessReleaseClient/ProcessClient/HumanTaskClient/CaseClient and CL HttpProcessClient, PS `FormalCasePostgresTest`/CMMN scope and `ProcessServerPostgresTest`; S1 model sources, S2 causal driver, S3 actual processes.
- Dependencies and consumers: real child mains, exact manifest, reusable fixture/SDK, separate owner/reviewer/foreign tokens from private file references, scoped read-only probes. No use of MockMvc, mocked IAM or fake Flowable in E.
- Why now: positive/failure/manual paths compose independently before recovery-specific state windows.
- Contract/signature changes: `tianji.expense.e2e.manifest` missing → assumption only when explicitly E-selected, malformed/present unauthorized → fail. Exact finite class never selected. Source does not exercise E without explicit independent provisioning and runtime authorization.
- Input/output and state mapping: loop through exactly two approved tenants with equal textual business/operation/command IDs for each matching scenario and distinct tenant state. Publish same rendered release bytes through actual SDK and verify SHA/ref plus native decision result/rule. Use amounts 100/700/1500 respectively; register Worker expectation after exact authenticated START and before poll. Exact START replay returns original instance; changed payload under same command yields stable conflict. Observe root history, original BusinessDB effect/technical receipt, Agent linked IDs/status and same Outbox/Inbox application; after each scenario reconcile owned open work. Reports use exact witness categories and immutable IDs.
- Error and edge behavior: normal requires real COMPLETED, native normalEnd, 1 reserve/1 notice/0 undo. Actual 700 model error requires FAILED persisted, actual terminal Outbox and one Inbox application, native compensatedEnd and 1/0/1. For manual, defer exact genuine result until native Agent timer wins, assert review task lane from DMN, unauthorized/wrong-tenant denial before mutation, two eligible reviewers race same task/version and exactly one succeeds, winner completes/replays with a changed-payload conflict. Observe linked Case provenance; use allowed native plan-item operations, primary completion enables supplemental via sentry; tenant A completes supplemental and tenant B uses authorized TERMINATE; actual milestone and completed Case, exact Case completion Inbox applies once, parent compensates and has 1/0/1. Case timeout or result on wrong branch fails. Query foreign IDs as each real tenant, deny without visibility, prove owned receipts are disjoint. Source mismatch/unavailable or missing required witness never counts as passed.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001 — test-only call through actual authority, closed scope/model/receipt, safe evidence without original payload.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 immutable typed C calls/evidence and exact per-tenant context; 4 fixed diagnostics; 5–6 existing SDK/Jackson; 7 manifest-only; 9 bounded scenario; 10 native timers and elapsed deadline; 11 no production writes beyond existing authorized contracts and test fixture.
- Implementation pseudocode:

```java
var manifest = requireExplicitManifestOrSkipMissingOnly();
try (var harness = openVerifiedHarness(manifest); var business = prepareOwnedBusinessFixture(harness)) {
    launchRealChildrenAndVerifyReadiness(harness);
    publishSameVerifiedReleaseBytesThroughRealSdkPerTenant();
    for (var tenant : manifest.tenants()) {
        var instance = startAndVerifyExactReplayAndConflict(tenant, selectedScenario);
        business.registerExpectedOperation(instance, ownedSeed(tenant, selectedScenario));
        business.openPoller(tenant);
        var decision = awaitExactNativeDmnRule(instance, selectedScenario);
        switch (selectedScenario) {
            case NORMAL -> awaitActualAgentAndOneInboxApplicationThenNativeNormalEnd();
            case AGENT_FAILURE -> awaitActualFailedAgentAndOneInboxApplicationThenNativeCompensation();
            case MANUAL_CASE -> {
                awaitNativeTimerAndAuthorizedTaskRaceAndCompletionReplay();
                driveAuthorizedLinkedCaseAndAwaitMilestoneCompletion();
                awaitExactCaseInboxApplicationAndNativeCompensation();
            }
        }
        assertOwnedBusinessCountsAndDisjointForeignDenial();
        reconcileOwnedWorkAndAppendSafeEvidence();
    }
    publishEvidenceOnceAfterAllRequiredProved();
}
```

- Verification contribution: real E1–E3 only when separately authorized. Finite verifies class/method discovery from compiled metadata, no E execution.
- After this file: E1–E3 source compiles and unresolved E0 environment remains explicit.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`

- Purpose: true correlated recovery windows without synthetic terminal events, lease/ACT mutation or combined incompatible snapshots.
- Symbols: `WorkerRecoveryIT`, `recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants`, narrow private exact witnesses per window.
- Repository evidence: PS `AgentDispatchStore`/`AgentResultInbox`, actual AS `AgentResultDeliveryHandler`/Outbox route, `FlowableWorkerService` and W store/poller; existing lost-ACK test proves only substituted transport. Spec §7.3 requires separate windows where early application and live native wait cannot coexist.
- Dependencies and consumers: fresh database set/manifests different from File 1, same approved model SHA and two verified tenants. Use same lifecycle/harness and E0 prereqs. Exact explicit runtime selector only after external approval.
- Why now: requires all test transport and business fixture prerequisites, builds on E1 exact native success as outcome.
- Contract/signature changes: no extra prod endpoint. Record one RECOVERY category per tenant with scoped ID bundles for lost Worker complete, early Agent result/AS restart, and PS live native wait restart. Each bundle has separate own instance/occurrence unless coexistence actually witnessed; equal textual command/operation IDs across tenants within matching subwindow, unique across subwindows.
- Input/output and state mapping: W drops only one completion before upstream after actual durable BusinessDB commit, no synthesized HTTP acknowledgement; observe DB lease expiry, same job/occurrence/intent reacquire with fresh lease, exact old lease denied through existing SDK, same durable technical/business receipt and one effect. Hold real AS linked 202 after exact AS commit so genuine terminal arrives while PS not ACKNOWLEDGED; observe owned Inbox PENDING. Drop first real committed Inbox 202. Observe actual retryable Outbox state before AS stop; capture current PID/start, stop only owned AS then replace, require new PID/start and persisted same-ID/fingerprint resend by new AS. Let original linked response through within its own bound. Assert one Inbox/native application despite resend. Use separate PS restart window with genuine result deferred until actual ACKNOWLEDGED plus exact native Message subscription and Timer/deadline, capture old IDs, stop/replace PS while still in window, prove IDs/deadline retained, release result, assert one normal advance/notice. Report distinct OS identities and closed/settled/reconciled work per window and tenant.
- Error and edge behavior: absence or early advancement of PS wait fails that window; duplicate event mismatch, lost expiry or insufficient time slack fail. Original business effect single despite re-acquire; repeat stale ack cannot mutate state. Never alter database clocks, ACT/lease/Outbox rows or inject body/event, never permit trace-only proof. A prior process observed retry is not evidence for replacement resend. If actual early event application and live wait are incompatible, separate occurrences are mandatory. No event consumer replacement.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001 — actual auth/lease/store ownership, no token artifacts or simulation of delivery.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11. 1–3 exact existing DTOs/scoped witnesses; 4 only safe reports; 5–6 existing SDK/Jackson; 7 isolated config; 9 explicit causal phases; 10 Instant/native/monotonic; 11 reuse existing transaction and delivery path.
- Implementation pseudocode:

```java
for (var tenant : approvedTwoTenants()) {
    var lost = startRecoveryOccurrence(tenant, LOST_WORKER_ACK, 200);
    armDropCompleteAfterOwnedBusinessCommit(lost);
    awaitNativeLeaseExpiryAndFreshSameOccurrence(lost);
    assertStaleLeaseDeniedAndOnlyOneBusinessEffect(lost);
    var early = startRecoveryOccurrence(tenant, EARLY_RESULT_AND_AS_RESTART, 200);
    armHoldRealLinkedSuccessAndDropCommittedInbox202(early);
    awaitExactEarlyInboxPendingBeforeAcknowledged(early);
    releaseOriginalLinkedResponseWithinItsDeadline(early);
    awaitDurableOutboxRetry(early);
    replaceOwnedAgentAndObserveSameEventFingerprintResend(early);
    assertOnlyOneNativeApplication(early);
    var wait = startRecoveryOccurrence(tenant, PROCESS_RESTART_WITH_NATIVE_WAIT, 200);
    deferGenuineResultUntilVerifiedNativeWaitSurvivesReplacement(wait);
    replaceOwnedProcessAndCompareActualMessageTimerIdsAndDeadline(wait);
    releaseRealTerminalDeliveryAndAssertSingleNativeAdvance(wait);
    assertOwnedEndAndReceiptCountsForAllWindows();
    reconcileAndAppendThreeExactSafeWitnessBundles();
}
```

- Verification contribution: E4 only in separately approved E run. Finite proves source/testCompile/package and nonexecution.
- After this file: complete exact IT classes available, no E reports claimed by source validation.
#### File 3 — `CREATE docs/egon/evidence/2026-11-05-expense-four-path-acceptance.md`

- Purpose: Preserve exact source checks, class/package hashes, future E instructions and explicit limits for review.
- Symbols: Evidence table with test selectors, actual counts, scope proof and runtime requirements.
- Repository evidence: Existing recovery and compensation evidence records distinguish finite source and opt-in runtime.
- Dependencies and consumers: Files 1–2 compile and earlier Steps pass; user reviews the result.
- Why now: Only actual completed gates may be marked passed.
- Contract/signature changes: None.
- Input/output and state mapping: Finite reports and static proof become exact source evidence. E0–E4 are explicitly pending.
- Error and edge behavior: Missing or stale report blocks source verification; a skip never substitutes for an E success.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 exact test-only scope; Rule 10 report UTC versus elapsed duration.
- Implementation pseudocode:

```text
for each exact finite gate record command, cwd, actual result and durable report
record both compiled ITs and prove neither selected
record nonexecution of applications, PostgreSQL, real IAM and browser
list exact runtime operator prerequisites and unresolved E checks
```

- Verification contribution: Exact three-path Step scope plus immutable SQL and actual output hashes.
- After this file: Complete the focused service source commit, then Web fixture Step 5.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: `t3_12_finite ExpenseFixtureModelsTest,ExpenseAcceptanceHarnessTest,ExpenseAcceptanceModelTest,ProcessModelValidatorTest,CmmnModelValidatorTest,WorkerCompensationModelTest,HumanTaskModelTest,HumanTaskTimerModelTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,CompensationBusinessFixtureTest,AgentStartWorkerTest,AgentResultDeliveryHandlerTest,RemoteWorkerPollerTest`, then archive/class inspection below. Do not select either IT in finite validation.
- Expected result: every exact finite class actually runs with F/E/S=0, both IT classes compile and show exact @Test methods through bytecode annotation, fresh finite Surefire report set has neither IT, both main classes only in test-class tree. No manifest property/connection/listener.
- Failure returns to: File 1 if normal/fail/manual source or compilation, File 2 for recovery source or compilation, Steps 1–3 for violated unchanged prerequisites, E gate for actual runtime.
- Completion criteria: exact test-only path diff, finite reports and positive bytecode/archive evidence, explicit E unexecuted status. No runtime assumptions claimed as a pass.
- Rollback: focused revert of this test choreography commit. No environment deletion or legacy behavior changes.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`, `docs/egon/evidence/2026-11-05-expense-four-path-acceptance.md`.
- Commit: `test(tianji): cover expense four-path acceptance scenarios`

### Finite command and artifact gate shared by Steps 2–4

Use a fresh report baseline for every run, preserve phase reports outside Git, and require selected reports after the phase start. JDK comes from explicitly verified Java 25, local Maven settings from prior locked source work. The source reactor forwards all flags and permits no-selected-tests only in unrelated modules.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false
t3_12_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
t3_12_finite() {
  STELLAR_VERIFY_GOAL=verify ./scripts/verify-source-reactor.sh \
    -gs "$t3_12_settings" -s "$t3_12_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
    "-Dtest=$1"
}
```

The final fresh finite verify may use the approved clean reactor argument to clear stale targets before rebuilding. Independently assert exact PS test-class files for `BusinessProcessAgentAcceptanceIT`, `WorkerRecoveryIT` and `ExpenseProcessAcceptanceMain` plus AS `ExpenseAgentAcceptanceMain`, SHA-256 each, use JDK `javap -v -classpath <own target/test-classes> <FQCN>` for actual test/main signatures. Use `jar tf` and `unzip -p <exec.jar> META-INF/MANIFEST.MF` on each exact packaged module archive to prove BOOT-INF/classes has neither acceptance nor test fixture classes, and native Start-Class remains TianjiAgentServerApplication / TianjiProcessServerApplication. Assert both current class names absent from fresh finite Surefire XML as execution targets. Never invoke `java` on the mains for source validation.

Future **separately authorized E only** uses distinct owner-private manifests and fresh store sets with the same revision/model/exec locks, exact `-Dtest=BusinessProcessAgentAcceptanceIT` then `-Dtest=WorkerRecoveryIT` under the existing wrapper with `STELLAR_VERIFY_GOAL=test` and `-Dtianji.expense.e2e.manifest=<exact approved absolute path>`. The isolated collector is not disabled for E. Require exact reports with nonzero runs and zero failures/errors/skips and all eight tenant/path results plus three separate RECOVERY witness bundles per tenant. A missing/invalid/unauthorized manifest, no report, skipped E, expired timer, unproven child or incomplete evidence blocks runtime closure, never source GREEN. No new script: durable capability is the two test entries plus existing source reactor; no scratch files added to the repository.

### Step 5 — Verify the canonical fixture through existing Web decoders

- Requirements: REQ-007, REQ-008.
- Dependencies: Step 1 canonical service fixture commit is recorded and exact hash proven; Steps 2–4 test-source and evidence delivery are committed in the service repository. No E or browser acceptance is needed for U4.
- Baseline state: `tianji-web` `43c58a1` has current Task/Case typed clients and mocked-fetch tests. Both planned test paths are absent; the sibling service repository is an explicit one-time copy source.
- Observable outcome: The local web fixture is byte-identical to the recorded service commit and actual existing current-user clients accept and validate the two governed synthetic projections.
- End state: Exactly two test paths committed in `tianji-web`, with the canonical fixture lock and U4 evidence available. Runtime pages, resource manifest and APIs are outside this Step.
- Test-first gate: Required. Add and select a test using Node `existsSync` inside the test body so missing JSON produces an actual assertion failure. Do not use a static JSON import that fails during discovery.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 2, Rule 5, Rule 6, Rule 10, Rule 11. This Step has no Java change; the existing owner contract and UTC fixture remain authoritative.
- Ordered files:

#### File 1 — `CREATE src/test/expenseFourPathContract.test.ts`

- Purpose: Assert asset presence and fixed digest and exercise the actual Task/Case client parsing path offline.
- Symbols: `it('contains the canonical expense contract fixture')`, `it('decodes the expense ACTION task and formal Case using current clients')`; private `readFixture` only if both tests need byte/shape reuse.
- Repository evidence: `src/api/humanTasks.test.ts`, `src/api/cases.test.ts`, `src/api/client.ts#requestJson`, existing `gatewayAuth.csrf` mock and fetch stubs. `tsconfig.app.json` already includes Node types, Vitest and ES2022.
- Dependencies and consumers: `humanTasksApi.get`, `casesApi.get`, `gatewayAuth`, `setAuthFailureHandler`, `node:fs`, `node:crypto`, `node:url`, Vitest. Only the local test fixture is read at test runtime.
- Why now: The first exact assertion supplies RED prior to introducing the copied asset. Client checks can compile with `unknown` JSON and real clients, with no imported missing fixture.
- Contract/signature changes: Add a test file only. Store exact approved SHA-256 `e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7`. New parser is unnecessary; existing clients own validation of both projections.
- Input/output and state mapping: Resolve relative file path via Node `fileURLToPath(import.meta.url)` and `dirname/resolve`, read raw Buffer and verify SHA before JSON decode. Inspect closed root `schemaVersion, releaseVersion, models, startInputs, paths, humanTask, caseInstance`. Stubs return parsed `humanTask` and `caseInstance` as unknown values via real JSON `Response`, then existing clients decode them. Assert ACTION, null approval, `finding:STRING`, exception lane, exact synthetic UTC, Case key and closed item/action states; also assert parsed projections equal fixture objects and `expense-v1`.
- Error and edge behavior: Missing/changed asset must fail assertion; malformed or mismatching DTO is rejected by actual consumers. No network, real credentials, native IDs or cross-repo reads during a standalone test. Restore mocks and stubs, reset auth failure handler and invalidate gateway test context after each test.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Existing Vitest/jsdom and current decoder route, no export of a new parser, no package or runtime change.
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 10, Rule 11. Verify exact current contract with built-in Node utilities, retain wire Instant text and existing Web test structure.
- Implementation pseudocode:

```typescript
const path = resolve(dirname(fileURLToPath(import.meta.url)), 'fixtures/expense-four-path-contract.json')
it('contains the canonical expense contract fixture', () => {
  expect(existsSync(path), 'committed canonical expense fixture').toBe(true) // genuine RED first
  expect(createHash('sha256').update(readFileSync(path)).digest('hex')).toBe(LOCKED_SHA)
})
it('decodes the expense ACTION task and formal Case using current clients', async () => {
  // Read local bytes, check closed fixture shape and locked digest, preserve exact UTC input strings.
  // Invalidate current context; mock gatewayAuth.csrf; stub two fetch Responses with application/json.
  const task = await humanTasksApi.get('20', {taskId: 'expense-review-task-fixture'})
  const formalCase = await casesApi.get('20', {caseInstanceId: 'expense-case-fixture'})
  // Assert equality to the corresponding JSON projections, exact role-free ACTION schema,
  // Case item state/actions, UTC fields, and two exact current-user POST paths and request IDs.
  // Assert tenant header=20, cookies included, test CSRF, no Authorization header.
})
```

- Verification contribution: Presence-only RED then the U4 test file and nearby consumer regressions.
- After this file: The exact presence selector fails on the missing copy. No unresolved import or discoverability error.

#### File 2 — `CREATE src/test/fixtures/expense-four-path-contract.json`

- Purpose: Give Web a local fixture whose bytes match the locked service commit.
- Symbols: `schemaVersion=1`, `expense-v1`, exact canonical SHA-256 `e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7`.
- Repository evidence: Step 1 PS `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json` and its typed model/DTO validation. `src/test/fixtures.ts` is a separate existing file.
- Dependencies and consumers: Recorded Step 1 service commit and File 1. Web does not resolve sibling repository resources at test runtime.
- Why now: Clear the missing local fixture assertion by copying the approved file once.
- Contract/signature changes: Exact byte copy only, with 3084 UTF-8/LF bytes and terminal newline. No JSON reserialization or application import.
- Input/output and state mapping: Source Git blob at approved Step 1 commit to exactly the local test resource path; fully preserves the seven root keys and both complete DTO examples.
- Error and edge behavior: Stop on missing/wrong Step 1 commit, existing destination, digest mismatch or `cmp` discrepancy. Do not overwrite either source or destination.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 6, Rule 10, Rule 11. Exact existing model and JSON identity, including UTC and closed null semantics.
- Implementation pseudocode:

```sh
# The operator supplies EXPENSE_FIXTURE_COMMIT as the exact full recorded Step 1 SHA.
set -eu
test "$(git -C /Users/mario/SelfProject/Stellar-Enclosure/tianji-service rev-parse --verify "${EXPENSE_FIXTURE_COMMIT:?Step 1 SHA}^{commit}")" = "$EXPENSE_FIXTURE_COMMIT"
expense_fixture_copy="$(mktemp "${TMPDIR:-/tmp}/expense-contract.XXXXXX")"
trap 'rm -f "$expense_fixture_copy"' EXIT
git -C /Users/mario/SelfProject/Stellar-Enclosure/tianji-service show "${EXPENSE_FIXTURE_COMMIT}:stellar-enclosure-tianji-process-server/src/test/resources/fixtures/expense-four-path-contract.json" > "$expense_fixture_copy"
test "$(shasum -a 256 "$expense_fixture_copy" | cut -d ' ' -f 1)" = e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7
mkdir -p src/test/fixtures
test ! -e src/test/fixtures/expense-four-path-contract.json && test ! -L src/test/fixtures/expense-four-path-contract.json
(set -C; cat "$expense_fixture_copy" > src/test/fixtures/expense-four-path-contract.json)
cmp "$expense_fixture_copy" src/test/fixtures/expense-four-path-contract.json
```

- Verification contribution: Copy-time cross-repo `cmp`, standalone local hash and real decoder U4.
- After this file: Presence and digest can pass. Run exact U4 before committing.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-web`.
- Verification command: RED `npm test -- src/test/expenseFourPathContract.test.ts -t 'contains the canonical expense contract fixture'` after File 1 only. GREEN `npm test -- src/test/expenseFourPathContract.test.ts`, then `npm test -- src/api/humanTasks.test.ts src/api/cases.test.ts`, `npm run typecheck` and `git diff --check`.
- Expected result: RED one selected assertion failure for missing fixture. GREEN two new tests pass, existing two consumer suites and typecheck pass, cross-repo blob `cmp` and local digest equal. No lint/build/deployment gate is required for these two test paths.
- Failure returns to: Return to File 1 for test discovery, shape or transport expectations. For parity fail before staging, re-read the recorded service commit and exact File 2 source bytes. A product contract discrepancy must return to the Spec/owner.
- Completion criteria: Record actual U4 command exit/results, exact service source commit and equal SHA. A separate browser or runtime report is not implied.
- Rollback: Revert the focused web commit. The canonical service fixture and evidence remain available for service tests.
- Commit paths: `src/test/expenseFourPathContract.test.ts` and `src/test/fixtures/expense-four-path-contract.json` only.
- Commit: `test(tianji-web): verify shared expense acceptance contract`.

## 8. Test, Validation, and Quality Gates

Use the exact Step selectors and assertions. Source-reactor helper below is the existing offline runner with isolated task settings; no auth or runtime opt-ins. For Step 4 use the same named finite regressions, verify both IT classes compiled and neither appears in current Surefire XML. Step 5 uses the existing Web scripts and independent exact Git-blob comparison.

```sh
t3_12_finite() {
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ./scripts/verify-source-reactor.sh \
    -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml \
    -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml \
    -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
    "-Dtest=$1"
}
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1/G2 | Service | Step 1 exact RED and U1 | Model and JSON | Assertion RED then finite GREEN | Model resource/test | REQ-001/002/003/007/008, no engine |
| G3/G4 | Service | Step 2 exact selectors | Operator and relay finite safety | Assertion RED then finite GREEN | Harness/relay | REQ-004/005/006/007, no process/listener |
| G5/G6 | Service | Step 3 exact selectors | Fake/provider fixture and compile | Assertion RED then finite GREEN | Model/child/Worker fixture | REQ-001–007, no app/database |
| G7 | Service | Step 4 exact source regression and class-file verification | Full runtime source compile only | Actual finite reports plus both IT class files | Scenario code/earlier seams | REQ-001–007, E not executed |
| G8/G9 | Web | Step 5 exact npm commands, Git blob SHA and cmp | Portable fixture and Web decoders | Assertion RED then finite GREEN and typecheck | Canonical copy/test | REQ-008, no browser |
| E0–E4 | Operator environment | Spec §14 only after independent E authorization | Both real process services and all tenant/path witnesses | Eight primary results and required recovery subwindows, no skip | Isolated runtime diagnostics | Future runtime only |

No scripts/work helper is needed; the existing source reactor, npm and the durable test classes cover the checks. Use `git diff --check`, exact staged scope and baseline hashes before every commit.

## 9. Migration, Compatibility, Rollout, and Rollback

No production migration, schema repair or deployment. The user-controlled E test uses independently prepared disposable stores, current immutable migration bytes and the named test-only notice table. Preserve prior applied SQL and current API/OAS/manifest/defaults. On failure stop only the spawned child handles and retain private evidence. No production data, feature activation or environment changes are part of this Plan.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | Spec §4/§14 | Steps 1/3/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | model+business scenarios; no runtime claim |
| REQ-002 | Spec §4/§14 | Steps 1/3/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | model+failure driver; no runtime claim |
| REQ-003 | Spec §4/§14 | Steps 1/3/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | model+manual Case driver; no runtime claim |
| REQ-004 | Spec §4/§14 | Steps 1/2/3/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | harness+faults+scenario driver; no runtime claim |
| REQ-005 | Spec §4/§14 | Steps 2/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | manifest and verified SDK; no runtime claim |
| REQ-006 | Spec §4/§14 | Steps 2/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | bounded immutable evidence; no runtime claim |
| REQ-007 | Spec §4/§14 | Steps 1/2/3/4 | Exact §5/§7 paths | G1–G7 and future E where applicable | finite and opt-in gates; no runtime claim |
| REQ-008 | Spec §4/§14 | Steps 1/5 | Exact §5/§7 paths | G1/G2/G8/G9 | test JSON and existing Web decoder; no runtime claim |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| R1 | Composed native models vs runtime | Steps 1 and 4 | Real pure validators/converters passed candidate; actual deployment unknown | Root source/user runtime | Source design resolved; E remains pending |
| R2 | Isolated IAM/PG/TLS/collector | E0–E4 only | Current repo has no authorized E endpoint | Operator | Runtime prerequisite, no source blocker or automatic launch |
| R3 | Incompatible early and restart snapshots | Step 4 | Early Inbox can apply after ACK | Scenario owner | Separate causal windows and evidence bundles |
| R4 | Partial or private evidence | Steps 2–4 | Finite tests alone cannot prove effect | Root source | Fail closed; no unavailable witness counts as E pass |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Source covers the single shared business sample, both native service owners, two verified tenants, all four paths and recovery subwindows, a reproducible report and the finite Web contract. Real E remains an explicit separate user gate.

### 12.2 Spec consistency

No production contract/schema/profile changes. Reuses existing SDK, Worker and server source; new code only at the test boundaries in the approved design. Causal checkpoints are evidence queries, not internal state injection.

### 12.3 Repository executability

Both baselines are known and each Step's exact file list and finite commands are declared. All XMLs passed pure pinned verification. Existing source-reactor and Web commands preserve no-start validation and compile the intended opt-in sources separately.

### 12.4 Test and release completeness

Strict Spec/Plan, exact RED/GREEN where behavior is introduced, immutable checksum parity, actual compile/report checks and a no-runtime evidence split. No release or DB operation is required for a source commit.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Exact §5 existing test architecture | Source design matches Spec | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 exact current validators/SDK/Store | Source design matches Spec | None |
| MC-DEP-001 | Applicable | PASS | No planned dependency/POM changes | Source design matches Spec | None |
| MC-NAME-001 | Applicable | PASS | §7 exact test class and file names | Source design matches Spec | None |
| MC-VALID-001 | Applicable | PASS | §7 assertion-first strict actor/path/input gates | Source design matches Spec | None |
| MC-MODEL-001 | Applicable | PASS | Existing C DTOs and named immutable fixture evidence | Source design matches Spec | None |
| MC-CONVERT-001 | Not applicable | N/A | No new production cross-layer conversion | Source design matches Spec | None |
| MC-LOG-001 | Applicable | PASS | Private logs and redacted evidence; no business logger | Source design matches Spec | None |
| MC-BEAN-001 | Applicable | PASS | Existing explicit named test-only model seam | Source design matches Spec | None |
| MC-UTIL-001 | Applicable | PASS | Existing JDK/Jackson/Spring and Worker utilities | Source design matches Spec | None |
| MC-JSON-001 | Applicable | PASS | Closed operator/fixture JSON and actual current Web decoders | Source design matches Spec | None |
| MC-TIME-001 | Applicable | PASS | §4.9 UTC, DB witnesses and monotonic deadlines | Source design matches Spec | None |
| MC-CONFIG-001 | Applicable | PASS | Exact test-only config and existing profiles unchanged | Source design matches Spec | None |
| MC-PATTERN-001 | Applicable | PASS | Fixed causal Scenario Orchestration with existing Worker strategy | Source design matches Spec | None |
| MC-SCOPE-001 | Applicable | PASS | §5 exact Step paths, preserved 26 main SQL files | Source design matches Spec | None |
| MC-TEST-001 | Applicable | PASS | §8 exact finite checks and no E selection | Source design matches Spec | Recheck source and retain runtime-unverified status |
| MC-BLOCKER-001 | Applicable | PASS | §11 source gates resolved and E independently gated | Source design matches Spec | Recheck source and retain runtime-unverified status |

### 12.6 Final verdict

**PASS — Ready for user review**

## Java / CQE contract review

Exact separate Java rule gates in §4.8. Existing production Outbox, Inbox and Worker transaction owners provide the actual behavior. The fixture injects no substitute terminal event or native row and does not create a CQE layer.

## Backend generation and dependency evidence

Not applicable to generation: existing test suites and current modules only. No dependency, project generator, production DDL, public endpoint or profile changes.
