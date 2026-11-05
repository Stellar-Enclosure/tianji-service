# Governed native transfer callbacks — Step 50 / native Plan Step 2

- Baseline: `919ddc3dde63324315ba49c54761f65416db32fe`, clean; 911 tracked files and 25 existing main-classpath SQL files. Step 1 path hashes were verified before this Step.
- Effective Spec: [Native Agentic transfer templates](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md), SHA-256 `03a94fa8730193f0d0cbc887374fdb618e4795196913d91cf2d1f8ced8a67270`.
- Effective Plan: [Native template implementation](../plan/2026-11-05-05-22-native-agentic-transfer-templates.md), SHA-256 `c4fe5575a4603b65d040aa1181123d84f10e4a8ab247f240f1eb89a30efd98d2`.
- Authorization: the user's master-plan execution request; Root reviewed the full slice, controlled SDK-fact correction and exact 11-path scope, then independently passed both strict validators. Review/Draft status is retained; user acceptance is not claimed.

The Gate binds the exact two LlmAgent trees and explicitly shared BaseLlm, pins Session identity and invocation ID while allowing distinct per-agent InvocationContexts, validates the native transfer Method/schema/current role, and captures bounded child text only from the current response. BeforeTool rechecks current MODEL/session authority and durably appends the existing run-event CAS before dispatch. The exact context/call-ID key is single-use even after confirmation. Child states enforce NEW → DISPATCHED → RETURNED; matching afterTool confirms the actual transfer action before committing the summary. First sanitized fatal tokens preserve authorization, ledger and timeout disposition on callback and control channels. The executor uses the exact-snapshot budget view, requires current root final plus exact confirmed children, and keeps the existing result/API and conservative UNKNOWN native side-effect outcome. A native callback timeout before the overall DB deadline uses the existing active-lease finish; an actually expired DB deadline still uses expiration. Legacy timeout routing is unchanged.

## SDK fact corrected during validation

Pinned ADK `1.13.0` SHA-256 `36f13af457b1657e1ef67cf5361f4eb59009a2b911bc5b0de61019f3cbea4e32`; GenAI `1.77.0` SHA-256 `454ecb0de8009da6191599a198df93e1e669c5937b9b0b4873c622bc8490f8a5`.

`LlmAgent.runAsyncImpl` attaches `maybeSaveOutputToState` to the complete transfer-following publisher. The save callback checks final-response/content/text, without checking event author. A child handoff Event is non-final and has no child-key stateDelta, but the later root final can propagate through the child wrapper and place root-final text in its outputKey. Finite Runner snapshots reproduce this; the Gate still returns the independent child summary. No Session/outputKey/history value is branch authority. `PLAN-CLAR-911` corrects this fact and adds only the two existing governance documents to Step 2 (9 → 11 paths; 26 unique paths and three sequential commits remain).

The original boundary-external unknown-error spy allowed the SDK PluginManager to log a controlled private sentinel before onToolError. The final fixture injects the unknown failure inside the authorization appender: Gate/Plugin sanitize it before SDK handling; SDK receives an already-safe error and empty-rethrows it. A direct Plugin unit regression separately proves rawunknown onToolError becomes a safe cause-free error. This does not claim control of arbitrary external plugins or live provider logging.

## Actual finite verification

All Maven commands used JDK 25, `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, `./scripts/verify-source-reactor.sh`, existing credential-free `/tmp/tianji-compat-fix/settings.xml` for both `-gs` and `-s`, offline `-o -B -ntp`, `-DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false`, and disabled OTel traces/metrics/log exporters. Selectors were explicit; PostgreSQL, HTTP-listener, provider and App tests were excluded. No project/server/database/browser was started by this Step.

| Gate | Observed result | Log |
| --- | --- | --- |
| Gate presence RED / compile A | 1 expected assertion failure, then 1 pass; 15.669 s / 25.414 s | `/tmp/tianji-native-step50-gate-presence-red.log`, `/tmp/tianji-native-step50-gate-compile-a.log` |
| Gate typed RED | Initial GenAI Type-wrapper compilation mistake was corrected; then 10 tests / 9 expected failures / 0 errors or skips, 16.165 s | `/tmp/tianji-native-step50-gate-typed-red2.log` |
| Gate implementation feedback | 10 tests / 1 error exposed the root-key completion bug; boundary review then had 2 expected assertion failures plus that known implementation error. These mixed runs were not claimed as zero-error RED gates | `/tmp/tianji-native-step50-gate-green1.log`, `/tmp/tianji-native-step50-gate-boundary-red.log` |
| Gate unit GREEN | 12 passed, 0 failures/errors/skips; 21.600 s | `/tmp/tianji-native-step50-gate-green2.log` |
| Plugin contract RED | Initial stale RunScope mock helper caused 3 fixture errors; corrected helper runs the fence then delegates mocked execute4. Corrected run: 42 tests / 13 expected failures / 0 errors/skips; 16.898 s | `/tmp/tianji-native-step50-plugin-contract-red2.log` |
| Plugin typed RED | 42 tests / 13 expected failures / 0 errors/skips; 19.020 s | `/tmp/tianji-native-step50-plugin-typed-red.log` |
| SDK outputKey diagnostics | First integration had five final-outputKey absence assertion failures; two focused diagnostics then passed, 24.047 s, proving the corrected event/state/collector facts | `/tmp/tianji-native-step50-plugin-green.log`, `/tmp/tianji-native-step50-sdk-outputkey-diagnostic.log` |
| Plugin/Runner/Gate GREEN | 42 passed, 0 failures/errors/skips; 20.920 s | `/tmp/tianji-native-step50-plugin-green2.log` |
| Executor and confirmed-ID RED | 31 tests / 8 expected assertion failures / 0 errors/skips; 16.328 s | `/tmp/tianji-native-step50-executor-replay-red.log` |
| Executor feedback | 30 passed; one overly strict close-once verification failed because Gate close is idempotent across Executor/Plugin. Assertion now requires cleanup at least once | `/tmp/tianji-native-step50-executor-replay-green.log` |
| Initial full regression | 127 passed across 12 named classes, 0 failures/errors/skips; 30.800 s | `/tmp/tianji-native-step50-regression.log` |
| Callback timeout / DB expiry boundary RED | Mock corrected to enforce existing SQL deadline predicate; 9 tests / 2 expected assertion failures / 0 errors/skips; 16.090 s | `/tmp/tianji-native-step50-callback-timeout-red.log` |
| Final required regression | 128 passed across 12 named classes, 0 failures/errors/skips; 33.427 s, 2026-11-05 08:13:05 +08:00 | `/tmp/tianji-native-step50-regression-final.log` |

Final selector: `NativeAgentGateTest,AdkRunContextTest,NativeAgentTransferRunnerTest,AdkNativeAgentCompletionTest,AgentJsonCodecTest,AdkEventProjectorTest,JTokkitChatModelBudgetEstimatorTest,AdkContextWindowPlanTest,AdkReadOnlyDecisionLoopTest,AdkGovernedProcessLoopTest,AdkToolTelemetryPrivacyTest,TianjiAgentRuntimeAutoConfigurationTest`.

Independent cross-review found no blocking Executor issue, including the native callback-timeout correction. Static review of `AgentLedgerService.finish` and `JdbcAgentLedgerRepository.finishLeased/expireDeadline` distinguishes active-lease/future-deadline completion from DB-time expiration; no SQL or ledger implementation was changed. The exact 11 paths were inspected; 905 excluded tracked files and all 25 SQL files remain baseline-identical. No POM/dependency/API/C/CL/Components/configuration/frontend change or generated output is included. Strict Spec/Plan validation and `git diff --check` passed.

## Blocking Manual Checks

| Check | Applicability | Status | Evidence and finding |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing AR technical vertical and one Runner/AgentRun; current factory/ledger/plugin/executor seams reused. |
| MC-REUSE-001 | Applicable | PASS | Pinned AgentTransfer, current MODEL authorization, session fences, run-event CAS, budget view/projector/codec and control executor reused. |
| MC-DEP-001 | Applicable | PASS | All POMs baseline-identical; only existing ADK/RxJava/Jackson/Spring AI types. |
| MC-NAME-001 | Applicable | PASS | Gate behavior, private immutable transfer/failure facts and explicit TransferTaskStateEnum/NativeFailureKindEnum; no business entity introduced. |
| MC-VALID-001 | Applicable | PASS | Exact model/tree/parent edges, role/context/session/invocation/call/schema/args/text/FSM/replay and root completion guards; positive and negative tests. |
| MC-MODEL-001 | Applicable | PASS | Immutable records and copied maps for facts, private synchronized state and no entity conversion; state is one invocation only. |
| MC-CONVERT-001 | Not applicable | N/A | No new DTO projection or Converter; existing event projector/result envelope reused. |
| MC-LOG-001 | Not applicable | N/A | No new managed business logger; safe boundary errors omit raw causes and Plugin keeps existing class-only logging. SDK/external-plugin limit is explicit. |
| MC-BEAN-001 | Not applicable | N/A | No new Spring Bean/injection surface; existing constructor ABI retained. |
| MC-UTIL-001 | Applicable | PASS | JDK collections/reflection/UTF-8 and existing RxJava/SDK utilities; no dependency added. |
| MC-JSON-001 | Applicable | PASS | Exact four-field authorization payload; existing Jackson codec and additive unknown-type round trip pass; no ordinal encoding. |
| MC-TIME-001 | Applicable | PASS | Existing java.time deadline/lease/session fences retained; native pre-deadline callback timeout uses leased finish and real DB deadline expiry remains fenced; live time/PG acceptance remains unexecuted. |
| MC-CONFIG-001 | Not applicable | N/A | No configuration/profile key changed; AS factory registration remains Step 3. |
| MC-PATTERN-001 | Applicable | PASS | Existing Strategy marker plus cohesive per-invocation State/Gate and two-phase response/action confirmation; no scheduler/registry/DSL added. |
| MC-SCOPE-001 | Applicable | PASS | Exact 11-path corrected scope; 905 excluded tracked SHA checks and 25 immutable SQL; whitespace review clean. |
| MC-TEST-001 | Applicable | PASS | Actual assertion RED/A/behavior RED/finite GREEN evidence, SDK diagnostic correction, final 128 tests and strict docs; historical failed runs disclosed. |
| MC-BLOCKER-001 | Applicable | PASS | Every current-Step row closed; Step 3 and remaining roadmap work are explicitly pending. |

## Literal Rule Gate

| Rule | Applicability | Status | Independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Technical Gate/Plugin/Executor behavior and immutable private transfer/token roles; enum suffixes retained. |
| Rule 2 | Applicable | PASS | Each affected internal boundary validates tree/model, exact callback identity/lineage, current authority, bounded data and final output. |
| Rule 3 | Applicable | PASS | Immutable internal value records with defensive maps; no ordinary entity, Lombok conflict or Converter bypass. |
| Rule 4 | Not applicable | N/A | No new managed business class/injection/logger; established technical explicit constructors preserved. |
| Rule 5 | Applicable | PASS | JDK utilities plus existing approved framework types; no utility/dependency introduction. |
| Rule 6 | Applicable | PASS | Existing Spring/Jackson codec; string event type and safe Map payload; private enums are neither persisted nor frontend codes. |
| Rule 7 | Not applicable | N/A | All environment/profile/configuration files remain unchanged. |
| Rule 9 | Applicable | PASS | Approved Strategy/State/Gate/two-phase design owns complex transitions without another pipeline or registry. |
| Rule 10 | Applicable | PASS | Existing java.time deadline/lease values and JDK timeout types; no java.util date entity. |
| Rule 11 | Applicable | PASS | Current technical module profile preserved; Components/runtime/ledger reused; no new persistence/DDL/business Event/CRUD or generator. |

ASFactoryPresent=false; DefaultFactoryMapUnchanged=true; RuntimeUnverified=true; ProductionAccepted=false. Required next work is default-off AS factories (native Plan Step 3), full bounded task decomposition and the remaining T3-09/T3-10/T3-12/T4 roadmap. The finite Runner/ledger mocks do not prove live provider, PostgreSQL, IAM, browser, deployment or production behavior.
