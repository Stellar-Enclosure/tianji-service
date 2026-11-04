# Native compiler and per-execution budget binding — Step 49 / native Plan Step 1

- Baseline: `b0cf55c8d4696fac5bea30875c24f169503957a5`, clean; 906 tracked files; 25 existing main-classpath SQL files.
- Governing Spec: `../spec/2026-11-05-04-45-native-agentic-transfer-templates.md`, SHA-256 `85c1538ed38c604005556148c6f019c52251e0941b188a46738019839fdfa369`.
- Governing Plan: `../plan/2026-11-05-05-22-native-agentic-transfer-templates.md`, SHA-256 `9ebaee329ed5b964a7f75e793980d2b81a544ce68b284993053bc5788e9bd223`.
- Execution authority: user's master-plan implementation request; Root full review of the frozen slice and controlled corrections, strict Spec/Plan PASS, clean current-source capability digest preflight PASS. Review/Draft document status is preserved.
- Source surface preflight: ordered native AR/AS manifest SHA-256 `10acddda5e6a4a63d1008f7f9d7f99d78ba158b500df4fac3f8aa18cbff03209`.
- Requirements: REQ-901/902/904/907/917/919/920; remaining native Plan Steps 2–3 are required.

The additive compiler strategy extends the existing Function map. Immutable metadata owns only two fixed v1 topologies and canonical semantic hashing. Runtime Agent/model object identity remains a separate check for Step 2. The estimator adds a default-fail binding capability; the builtin private view reuses the existing calculations after binding the exact snapshot object, model instance and reviewed profile. CONFIG callers retain the existing admission path.

## Actual finite validation

All commands ran from `tianji-service` with JDK 25, `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, offline source reactor, the existing temporary credential-free settings, `-DskipITs=true`, explicit selectors and all OTel exporters disabled. No application, provider, listener, database or browser was started by this Step.

| Gate | Selector | Actual result | Log |
| --- | --- | --- | --- |
| Missing-contract RED | NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest | 9 tests, 3 expected assertion failures, 0 errors/skips; 17.928 s, 06:44:13 +08:00 | `/tmp/tianji-native-step49-presence-red.log` |
| Compile seam A | same | 9 passed, 0 failures/errors/skips; 22.401 s, 06:45:47 +08:00 | `/tmp/tianji-native-step49-compile-a.log` |
| Initial typed run | same | 20 tests, 6 failures and 2 errors; two positive missing-bind failures escaped assertions; this run does not satisfy the zero-error gate | `/tmp/tianji-native-step49-budget-typed-red.log` |
| Corrected typed RED | same | 20 tests, 8 expected assertion failures, 0 errors/skips; 17.257 s, 06:51:51 +08:00 | `/tmp/tianji-native-step49-typed-red2.log` |
| Focused GREEN | NativeAgentCompilerStrategyTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest | 26 passed (Strategy 8, budget 13, context-window 5), 0 failures/errors/skips; 23.691 s, 06:55:32 +08:00 | `/tmp/tianji-native-step49-green.log` |

The original six budget test method bodies were checked verbatim against the baseline. Typed RED separately proves absent defensive copies/validation/hash and absent safe/default/native model binding. Test setup was corrected before implementation: positive bind uses assertDoesNotThrow, the reserved output value is constructed inside its rejection assertion, and test codeRef matches the fixed template.

## Scope and remaining acceptance

- NativeExecutionRegistered=false; ASFactoryPresent=false; RuntimeUnverified=true; ProductionAccepted=false.
- This Step does not introduce a Gate, executor admission, factory configuration, tool path, API, POM or SQL change.
- Required next work: native Gate/plugin/executor integration (Step 2), default-off AS factories (Step 3), full bounded task decomposition and the rest of T3-09. The overall framework plan remains active.
- Provider/IAM/PostgreSQL/live AgentRun/browser and production acceptance remain unexecuted.

## Blocking Manual Checks

| Check | Applicability | Status | Evidence and finding |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing AR technical package and Function map retained; no new module/layer/storage. |
| MC-REUSE-001 | Applicable | PASS | AdkSemanticJsonDigest fingerprint, ModelBudgetProperties and five existing calculator bodies reused; no parallel accounting authority. |
| MC-DEP-001 | Applicable | PASS | All POM/dependency files baseline-identical; existing ADK, Spring AI and JTokkit only. |
| MC-NAME-001 | Applicable | PASS | NativeAgentCompilerStrategy behavior; AgentSpec/TemplateSpec/NativeModelBinding immutable technical values; private NativeBoundEstimator behavior. |
| MC-VALID-001 | Applicable | PASS | Compact constructor validation, exact topology, versioned references; bindNative profile and model identity; every view method requires exact snapshot reference first. Typed RED/GREEN covers positive and negative boundaries. |
| MC-MODEL-001 | Applicable | PASS | Immutable value records, defensive List.copyOf and exact ChatModel identity binding; no entity introduced. |
| MC-CONVERT-001 | Not applicable | N/A | No cross-layer projection, DTO mapping or Converter added; calculation helpers operate on the existing Bound. |
| MC-LOG-001 | Not applicable | N/A | No managed business class or log statement; safe constant error messages and redacted model binding. |
| MC-BEAN-001 | Not applicable | N/A | No Spring bean/injection/configuration added in this Step. |
| MC-UTIL-001 | Applicable | PASS | New implementation uses JDK collections/validation and existing digest; no new helper dependency. |
| MC-JSON-001 | Not applicable | N/A | No wire/Event/DTO serialization or JSON codec change; existing framed digest receives semantic strings only. |
| MC-TIME-001 | Not applicable | N/A | No production time field, clock or boundary change; test fixtures retain java.time.Duration. |
| MC-CONFIG-001 | Not applicable | N/A | No profile/configuration key change; all YAML baseline-identical. |
| MC-PATTERN-001 | Applicable | PASS | Strategy preserves factory variation point; private per-execution bound view isolates CODE_FIRST authority while sharing calculator helpers. No second registry/thread-local/global binding. |
| MC-SCOPE-001 | Applicable | PASS | Exact eight Step paths; 903 excluded tracked files and all 25 SQL baseline-identical; git diff --check PASS. |
| MC-TEST-001 | Applicable | PASS | Actual missing-contract RED, compile A, zero-error typed RED and 26-test GREEN; original six budget tests verbatim; strict Spec/Plan and exact scope checks. |
| MC-BLOCKER-001 | Applicable | PASS | All current-Step concerns are PASS or evidence-backed N/A. Runtime exclusions are explicit; required Steps 2–3 remain pending. |

## Literal Rule Gate

| Rule | Applicability | Status | Independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Technical Strategy/Estimator behavior names and immutable Spec/Binding value roles; no business POJO added. |
| Rule 2 | Applicable | PASS | AgentSpec/TemplateSpec/NativeModelBinding constructors and bindNative/view enforce each affected internal handoff; positive/negative tests pass. |
| Rule 3 | Applicable | PASS | New records are immutable internal values; copied lists; no ordinary entity or Converter bypass. |
| Rule 4 | Not applicable | N/A | No Spring-managed business class/injection/logger added; established technical constructors retained. |
| Rule 5 | Applicable | PASS | JDK collections and existing digest utility only; POM unchanged. |
| Rule 6 | Not applicable | N/A | No external JSON contract, persistence enum or frontend output changed. |
| Rule 7 | Not applicable | N/A | No environment profile/key changes. |
| Rule 9 | Applicable | PASS | Approved Strategy and per-execution bound-view patterns use the existing factory/estimator seams; no redundant abstraction. |
| Rule 10 | Not applicable | N/A | No production date/time representation changed; test fixtures use java.time. |
| Rule 11 | Applicable | PASS | Existing technical vertical and module boundaries preserved; Spring/Components adapter and budget infrastructure reused; no persistence/DDL/Event transport change, no new module/POM/generator. |

No supported CREATE/CRUD scaffold is introduced; generator remains outside this technical Step. No generated artifact, credentials, local configuration, schema migration, C/CL/API change or runtime acceptance is committed.
