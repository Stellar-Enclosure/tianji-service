# Typed readonly MCP journal execution evidence

Plan: [2026-11-01-19-11-readonly-mcp-journal-recorder.md](../plan/2026-11-01-19-11-readonly-mcp-journal-recorder.md); Review under original user continuation authority, not individually user-approved design.
Baseline: main@24dfd9cf9e66405f223186b99baf1f9a979e19c5; Plan SHA-256 `7f19dd884afea8b3c598d5f5bd3d5514f56a17352db76d636cd8728b0aedd7cb`.
Effective blueprint SHA-256 `c2f6f6317f9aade1ab74242edaceada865192e7d53e39d5e1c9c5cf5a7b4a0a9`.

Initial lock record: Root fully read the 839-line draft plus all subsequent patches, passed strict/resource/start-checklist gates, inspected source/callers/23 SQL baseline. Independent Plan review closed after preserving the old DMN null-operation guard explicitly. RED-only GO permits both test matrices plus the new Recorder UOE compile declaration; Store/SQL behavior remains unchanged until both valid RED results. No behavior implementation GO yet.

## Requirements

- PLAN-REQ-001: PASS - Additive executeReadOnlyMcp reuses preparation/current capability/DISPATCH/BEGIN/nonce/completion/accounting; direct typed MCP projection, no fabricated DMN observation.
- PLAN-REQ-002: PASS - Store.dispatchCallDeadline is the single four-ceiling formula, conditionally floors only exact READ_ONLY MCP; identical PDP/Store/SQL/call deadline and nonfuture-floor rejection tested.
- PLAN-REQ-003: PASS - TOOL_ERROR known failure, DENIED rejection, explicit NOT_SENT known no-send, all uncertain post-consumption failures UNKNOWN; known facts precede accounting/outer suppression.
- PLAN-REQ-004: PASS - Old DMN guard/mappers and all write/access paths retained; 23 SQL files, dependencies, configuration and native mounting unchanged.
- PLAN-REQ-005: PASS - Independent valid Recorder/Store RED, focused GREEN, final eleven-class/225-case PASS, source review, exact eight-path scope and individual gate evidence.

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Five Java paths remain in existing AR infrastructure.persistence; no new layer/module | Existing Agent technical architecture retained | None |
| MC-REUSE-001 | Applicable | PASS | Existing Recorder/Store/SQL, capability/PDP, codec, V13, transactions and MCP port reused | One private transport-result variation; no parallel journal/auth engine | None |
| MC-DEP-001 | Applicable | PASS | Exact eight-path diff excludes POM/dependencies; JDK Function/ChronoUnit only | No dependency or framework introduced | None |
| MC-NAME-001 | Applicable | PASS | executeReadOnlyMcp/dispatchCallDeadline and typed private outcome methods; existing VO/result/outcome types | No new ambiguous carrier/type | None |
| MC-VALID-001 | Applicable | PASS | Entry guards, SERVICE executor/current capability/exact PDP audit, Store and SQL equality, typed outcome.validated | Positive/negative paths in Recorder30 and Store33 pass | None |
| MC-MODEL-001 | Not applicable | N/A | No POJO/record/entity/enum declaration changed; existing typed values reused | No model/Lombok construction concern introduced | None |
| MC-CONVERT-001 | Not applicable | N/A | Direct protocol observation to existing ToolExecutionOutcome; no cross-layer DTO/entity/Converter | No business mapping boundary requiring MapStruct/BaseConverter | None |
| MC-LOG-001 | Not applicable | N/A | No production logging statements or business class added; fixed safe exception style retained | No new business logging concern; privacy verified by fixed outcomes/causeless failures | None |
| MC-BEAN-001 | Not applicable | N/A | Existing classes remain manually constructed, no dependency fields/Bean/DI changed | No Bean naming, Qualifier or constructor-injection change | None |
| MC-UTIL-001 | Applicable | PASS | JDK Function/Instant/ChronoUnit; Store owns package-private pure formula | No duplicate helper class or utility dependency | None |
| MC-JSON-001 | Applicable | PASS | Existing codec writes same descriptor/outcome/audit schema; Codec21 tests pass | No new JSON mapper/enum ordinal/wire DTO | None |
| MC-TIME-001 | Applicable | PASS | Single min4/floor formula at Recorder/Store/SQL; actual Store/SQL fixtures cover raw/floor/past/replay/DMN/IDW | Exact MCP deadline and original wall/nano origin; live PostgreSQL remains unverified | None |
| MC-CONFIG-001 | Not applicable | N/A | No config/profile/key/property class changed | Profile parity/default feature state unaffected | None |
| MC-PATTERN-001 | Applicable | PASS | Private Function<Instant,ToolExecutionOutcome> Strategy; existing dispatchAndRecord stays single owner | Concrete transport result/SAM variation isolated; no public generic abstraction | None |
| MC-SCOPE-001 | Applicable | PASS | Three production/two test files plus README/Plan/Evidence; all 23 SQL hashes match | Exactly eight declared paths; no native/config/permission/runtime change | None |
| MC-TEST-001 | Applicable | PASS | Both valid RED matrices, focused30/33 GREEN, final eleven classes/225 cases exit0 | Zero failure/error/skip; only existing Components compiler warnings | None |
| MC-BLOCKER-001 | Applicable | PASS | Independent source review has no actionable finding; all required rows closed | No Step blocker; original platform goal and live/native boundaries remain unfinished | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Behavior/helper names and existing semantic VO/outcome/result types | Type/diff review and source compilation | No new ordinary POJO or ambiguous carrier | None |
| Rule 2 | Applicable | PASS | Exact risk/resource/cost/action/transaction/executor/capability/PDP/deadline/outcome handoffs | Recorder30 and Store33 positive/negative tests | Technical typed guards at every affected boundary; no new Spring DTO/proxy handoff | None |
| Rule 3 | Not applicable | N/A | No model/entity/record/enum/Converter declaration changed | Five Java path diff reviewed | Existing immutable values reused; Lombok/BaseConverter model/conversion trigger absent | None |
| Rule 4 | Not applicable | N/A | No new business class/log/Bean/DI/dependency fields | Source and constructor diff reviewed | Existing manual technical classes retain construction/logging roles | None |
| Rule 5 | Applicable | PASS | JDK Function/Instant/ChronoUnit and current Spring/runtime types | No POM diff; source reactor compiles | No new utility framework/dependency | None |
| Rule 6 | Applicable | PASS | Existing ToolExecutionJsonCodec and typed validated outcomes | Codec21 plus recorder/store compatibility tests | Same persisted JSON and enum coding; no ordinal | None |
| Rule 7 | Not applicable | N/A | No environment configuration path changed | Eight-path scope | Profile key structure/default enablement unchanged | None |
| Rule 9 | Applicable | PASS | Private deadline-aware Function Strategy in one dispatch core | DMN/MCP ordering/failure/replay tests and source review | Required variation isolated without duplicate orchestration | None |
| Rule 10 | Applicable | PASS | java.time Instant/Duration/ChronoUnit with original nano origin | Microfloor/nonfuture/near-key/DMN/IDW/late-fact tests | No Date/Calendar, round-up or restarted window | None |
| Rule 11 | Applicable | PASS | Existing Agent infrastructure.persistence; Components/MP transaction ownership unchanged | Source reactor, exact eight paths and all23 SQL hashes | No new layer/DDL/CRUD/Event; Agent generator excluded | None |

## Planned verification and boundary

Only Root runs Maven after every Java owner freezes. Exact separate Recorder RED, Store RED, focused GREEN and eleven-class final commands are in Plan section 8. Compilation or fixture failures are recorded separately and never called behavioral RED.

No application/server/database/container/browser or real IAM/MCP network process is started. No DDL, POM/dependency, config, Bean, native mounting, permission registration/activation/grants, push or PR is authorized in this Step. All 23 SQL files remain byte-exact.

## Pre-implementation review closure

Root corrected derivative wording that had confused internal design acceptance with user approval; authorization remains the original goal instruction and the document stays Review. Resource Time row and SQL hash command now accurately cover elapsed Duration and all 23 Agent/Process/Worker SQL files. Store RED contains no direct call to the absent helper. A future expiry below one microsecond must fail before final window/PDP/BEGIN with own MCP DEADLINE. Independent review required a DMN null-operation regression and explicit pre-lambda guard to preserve baseline no-BEGIN behavior. Root fully read the draft and every patch, reran strict, and locked this exact hash before RED-only GO.

## Recorder behavioral RED

/tmp/tianji-readonly-mcp-recorder-red.log finished 2026-11-01 19:23:23, 25.189s, exit1 BUILD FAILURE: 30 tests / 8 assertion failures / 6 errors / 0 skipped. Root parsed Surefire XML: all 14 new MCP tests fail from the compile-only executeReadOnlyMcp UnsupportedOperationException, with no compiler/fixture failure; 15 original tests plus the new DMN null-operation compatibility regression pass. Store/SQL production remains unchanged. GREEN is not authorized until the separate Store RED completes and is classified.

## Store behavioral RED and GREEN authorization

/tmp/tianji-readonly-mcp-store-red.log finished 2026-11-01 19:23:52, 17.056s, exit1 BUILD FAILURE: 33 tests / 2 assertion failures / 2 errors / 0 skipped. Exact missing behaviors: PROFILE_* cleanup not accepted; floored MCP BEGIN rejected; unfloored BEGIN incorrectly allowed; future raw deadline that floors into the past incorrectly allowed. Root parsed Surefire XML; no compiler/fixture failure. The 27 original tests and two new actual DMN/near-resource/IDW precision compatibility tests pass. Both valid RED matrices now exist. Only now Root authorized minimal Recorder and Store/SQL implementation, preserving all tests and file ownership; all owners must freeze before Root's separate focused GREEN commands.

## Recorder focused GREEN

/tmp/tianji-readonly-mcp-recorder-green.log finished 2026-11-01 19:26:54, 29.120s, exit0 BUILD SUCCESS: all 30 Recorder tests pass, zero failure/error/skip. Tests unchanged after behavioral RED; implementation uses the private deadline-aware Function, preserves the explicit old DMN null-operation guard, and projects MCP outcomes directly. All Java stays frozen while Root runs separate Store GREEN and the final compatibility selector.

## Store focused GREEN and independent source review

/tmp/tianji-readonly-mcp-store-green.log finished 2026-11-01 19:27:26, 22.709s, exit0 BUILD SUCCESS: all 33 Store tests pass, zero failure/error/skip. Six new tests and all old tests are unchanged after RED. Independent review of the frozen three production files and tests found no actionable defect: DMN null guard and old mappers retained; one MCP-only floor formula reused at three boundaries; nonfuture floor rejected before PDP/BEGIN; post-BEGIN no-send fence and typed callback translation retained; unknown after consumption conservative; known facts survive outer suppression and accounting. README now describes the actual programmatic Recorder and remaining native/runtime boundary. All Java frozen for final eleven-class canonical run.

## Final verification and bounded Step verdict

/tmp/tianji-readonly-mcp-journal-recorder-final.log finished 2026-11-01 19:28:49, 28.775s, exit0 BUILD SUCCESS. Eleven classes, 225 tests, zero failure/error/skip: AR port7, codec21, IDW Recorder23, Store33, WriteStore17, Recorder30, Access16, ApprovedStore26, ApprovedRecorder18; AS MCP transport20 and bindings14. Only the three preexisting Components Jackson2AutoConfiguration deprecated-for-removal compiler warnings remain.

Root inspected the complete three-file production diff and both test additions. Old DMN null-operation is explicitly guarded before Function creation; original DMN observation/failure mappers and START/approval bodies remain unchanged. Independent source review found no actionable defect. Private Strategy reuses stable journal orchestration and only varies the typed operation outcome. No new public generic abstraction, model, JSON mapper, transaction, dependency, SQL resource or configuration was introduced.

Strict Plan/resource/start/end checklist and cached scope/whitespace gates are required immediately before the single commit. Full historical SQL before/after comparison covers all 23 Agent/Process/Worker SQL files; the JSON baseline independently confirms the same file set and hashes. No new repository scratch harness exists; temporary Plan/test/source candidates are deleted after commit, while logs and hash data remain in /tmp for review.

Step implementation is Verified, pending its exact eight-path commit and full hash/path recording before any next Step source edit. The original goal remains active: native MCP mounting/configuration and the later T3/T4 work are incomplete. Real PostgreSQL precision/locking/rollback, IAM/cryptographic identity, TLS/network/deployed MCP and native runtime are unverified. No application/database/container/browser, permission registration/activation/grant, push, PR or release was performed.
