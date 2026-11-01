# Native fixed readonly MCP execution evidence

Plan: [2026-11-01-19-44-native-readonly-mcp-tools.md](../plan/2026-11-01-19-44-native-readonly-mcp-tools.md); Review under the original user continuation instruction. Root design review is not separate user approval.
Baseline main@1ec81466592a8c08d5df330404b394493977fabc; Plan SHA-256 `7fd13b760a913d124c1ec51c9d0f7f1ed89b4f712a48b0832da34ce24ceee632`.
Effective blueprint SHA-256 `ae6e41f9ae5129703080bd39deb346b82a80ce9467d55edc2a5e787e892f4710`.

Initial lock record: Root fully read the complete strict-valid Plan plus every patch, source bridge/constructor/guard seams, start checklist and immutable SQL baseline. Independent Plan review found no actionable issue. RED-only GO permits three test matrices and new API UOE declarations, with all existing behavior unchanged. No behavior implementation GO until both actual RED commands are classified.

## Requirements

- PLAN-REQ-001: PASS - Actual Runner routes Decision/MCP/START through one existing scope/plugin, with shared quota and distinct host IDs.
- PLAN-REQ-002: PASS - Exact MCP execution key routes typed MCP; all other read-only resources including near keys remain Decision; missing MCP fails binding.
- PLAN-REQ-003: PASS - Real fence, closed check and exact one-shot bound follow controlled BEGIN; null remains null; safe held stop-code wins Recorder/event failures.
- PLAN-REQ-004: PASS - SETTLED/schema/identity/zero-cost/no-receipt checks retained; late scope closure suppresses MCP FINISHED/model output while facts persist; known tool error continues safely.
- PLAN-REQ-005: PASS - Old public/package APIs and DMN/START/approval behavior retained; final nine classes/181 tests pass with exact eight-path scope.

## Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Two existing infrastructure.adk bridges; no Executor/Plugin/Scope API/new bridge change | Same Agent architecture and single native lifecycle retained | None |
| MC-REUSE-001 | Applicable | PASS | Existing declaration/registry/identity map, scope/plugin/quota, typed transport/Recorder and envelopes | No duplicate loop, counter, toolset or journal | None |
| MC-DEP-001 | Applicable | PASS | No POM/dependency path; JDK AtomicReference and current APIs only | No dependency introduced | None |
| MC-NAME-001 | Applicable | PASS | Semantic bind/guard/execute/pointer helpers in existing types; TargetVO unchanged | No new carrier, route enum or derived kind | None |
| MC-VALID-001 | Applicable | PASS | Full material/ref/hash/current policy guards; exact key/null/fence/closed/result checks | Three changed classes97 cases plus compatibility pass | None |
| MC-MODEL-001 | Not applicable | N/A | No record/entity/POJO/enum declaration changed; existing TargetVO reused | No model/Lombok construction change | None |
| MC-CONVERT-001 | Not applicable | N/A | No entity/DTO/Converter mapping; existing validated outcome envelope reused | No business conversion boundary requiring MapStruct/BaseConverter | None |
| MC-LOG-001 | Not applicable | N/A | No production logger or business class/log statement added | No logging change; safe failures and event privacy tested | None |
| MC-BEAN-001 | Not applicable | N/A | Programmatic constructors only; no Spring Bean/DI/config changed | No managed naming/Qualifier/injection concern introduced | None |
| MC-UTIL-001 | Applicable | PASS | JDK AtomicReference plus existing typed APIs; no utility class/dependency | Existing capabilities reused | None |
| MC-JSON-001 | Applicable | PASS | Existing closed declarations, safe Map envelopes and event types | Only validated authorized output reaches model; no inputs/outputs in events | None |
| MC-TIME-001 | Applicable | PASS | Decorator forwards exact Step30 Instant; real fence/closed before delegate | No new Clock/math/precision conversion or network lock | None |
| MC-CONFIG-001 | Not applicable | N/A | No configuration/profile/property path changed | Activation/default state remains unchanged | None |
| MC-PATTERN-001 | Applicable | PASS | Existing Composite scope plus private MCP transport Decorator | Real admission variation isolated without a second loop or hierarchy | None |
| MC-SCOPE-001 | Applicable | PASS | Five declared Java files plus README/Plan/Evidence; 23 SQL unchanged | Exactly eight paths; no configuration/permissions/runtime expansion | None |
| MC-TEST-001 | Applicable | PASS | Valid bridge/Loop RED, focused97 GREEN, final nine181 exit0 | Zero failure/error/skip; actual finite Runner and real guarded one-shot exercised | None |
| MC-BLOCKER-001 | Applicable | PASS | Independent source review found no actionable defect; all rows closed | No Step blocker; AS assembly and live acceptance remain future work | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Existing bridge/TargetVO types and semantic package/helper/public constructor names | Source inventory/compile | No new ordinary POJO or ambiguous carrier | None |
| Rule 2 | Applicable | PASS | Native identity/full binding/resource/fence/null/result/accounting checks | Bridge68 and ProcessLoop29 positive/negative cases | Each affected technical handoff remains validated | None |
| Rule 3 | Not applicable | N/A | No DTO/record/entity/enum/Converter changed | TargetVO and event declaration diffs reviewed | No new model or business mapping requiring Lombok/BaseConverter | None |
| Rule 4 | Not applicable | N/A | Existing manually constructed technical bridges; no Bean/DI/log statement | Old/new constructor and source review | No Spring business class/injection/logging trigger | None |
| Rule 5 | Applicable | PASS | JDK/existing imports only; no utility class | No POM change; source reactor passes | Closed utility/dependency scope retained | None |
| Rule 6 | Applicable | PASS | Existing validated Map envelopes and event schema | Privacy/tool-error/accounting tests | No raw remote content or ordinal encoding introduced | None |
| Rule 7 | Not applicable | N/A | No profile/config path changed | Eight-path scope | Configuration keys/default enablement unaffected | None |
| Rule 9 | Applicable | PASS | Existing Composite plus private MCP Decorator | Fence/admission/native-loop tests and source review | Concrete variation reused without new loop/counter/framework | None |
| Rule 10 | Applicable | PASS | Exact Instant bound forwarded unchanged | Exact deadline/fence/closed admission tests | No Date/Calendar, recalculation or extended window | None |
| Rule 11 | Applicable | PASS | Existing Agent infrastructure.adk profile and Components retained | Source reactor, eight paths and SQL23 baseline | No new layer/DDL/Event/CRUD or generator action | None |

## Locked boundaries

Exact eight paths: two existing bridges, three existing tests, README/Plan/Evidence. Exact MCP key only; all other READ_ONLY resources retain Decision routing. No enum/derived target kind, new bridge, public readonly API, Runner/Plugin/counter or toolset. Old readonly public4/bind7/package execute4 and Process public8/10 remain; new Process9/11 and bind8 are additive.

MCP null operation remains null; actual real fence and closed check precede the exact one-shot deadline call. A held safe stop error has presentation precedence. Already admitted bounded IO may complete after close; committed facts persist while late model/events are suppressed, with no network lock or undo promise. Existing DMN ordering remains isolated.

Finite actual ADK Runner with controlled Spring AI model is allowed; no application/server/DB/container/browser, real IAM/MCP network, Bean/config activation, permission registration/grants, push/PR/release. SQL23 baseline retained at /tmp/tianji-step31-sql-baseline.json; all must remain unchanged.

## Combined bridge behavioral RED

/tmp/tianji-native-mcp-bridges-red.log finished 2026-11-01 19:59:07, 20.564s, exit1 BUILD FAILURE. ReadonlyBridge30: 15 old pass, 15 new UOE failures. ProcessBridge38: 34 old pass, four new UOE errors. Total68 / 1 assertion failure / 18 errors / 0 skipped. Root parsed both Surefire XML files: every failed case contains the new compile-only UnsupportedOperationException; no compiler/fixture failure. Existing behavior remains unchanged. Loop RED is running separately; GREEN is not authorized until that result is classified.

## Actual native Loop behavioral RED and GREEN authorization

/tmp/tianji-native-mcp-loop-red.log finished 2026-11-01 20:00:09, 18.128s, exit1 BUILD FAILURE: 29 tests / 0 assertion failures / 6 errors / 0 skipped. Root parsed XML: the six new native MCP tests all fail from the new compile-only constructor UnsupportedOperationException; all 23 existing native-loop tests pass. No compiler/fixture error. Both actual RED commands now exist; only now Root authorized the two owners to implement the minimum two-bridge source changes. All three test matrices remain unchanged; no Maven until both owners freeze.

## Focused GREEN

/tmp/tianji-native-mcp-focused-green.log finished 2026-11-01 20:03:45, 28.403s, exit0 BUILD SUCCESS. ReadonlyBridge30, ProcessBridge38, actual ProcessLoop29: 97 tests total, zero failure/error/skip. All tests unchanged since valid RED. Root reviewed the complete two-file production diff: exact key/default Decision preserved; same TargetVO/declarations/Registry/Plugin; optional Process constructors/pointer only; null forwarding and exact-bound MCP guard; held error and late suppression MCP-only; old DMN and START/AA paths retained. All Java remains frozen for final nine-class canonical regression; README updated only after focused GREEN.

## Final nine-class verification and Step verdict

/tmp/tianji-native-readonly-mcp-tools-final.log finished 2026-11-01 20:06:01, 29.451s, exit0 BUILD SUCCESS. Nine classes / 181 cases / zero failure,error,skip: AR port7, ReadonlyBridge30, ProcessLoop29, ReadonlyLoop10, TelemetryPrivacy3, ProcessBridge38, Recorder30 (AR147); AS MCP transport20/bindings14 (AS34). Existing three Components Jackson2AutoConfiguration removal warnings remain; no new compile/test failure. All Java remained frozen.

Independent review of both production files and all changed tests found no actionable defect. The actual Runner fixture invokes the supplied guarded MCP transport, enters its verified callback and calls its one-shot; routing-only fabricated return values are not used as fence proof. Old DMN append/catch ordering and START/approval/effect behavior are unchanged. Package no-fence MCP fails before STARTED; null remains null; held original safe error wins; exact deadline and borrowed ownership are preserved. Late already-admitted IO may finish and persist a fact, but no late MCP model/FINISHED publication is allowed after observed scope closure. No network lock or remote undo guarantee is claimed.

Root full-read/strict/resource/start/end checklist and exact cached scope/whitespace checks close this Step; immutable SQL comparison covers all23 Agent/Process/Worker files, independently against both the saved JSON and committed baseline. No scripts/work harness exists. Test/Plan source candidates under /tmp are deleted after commit; logs/hash data remain for review.

Step Verified pending its exact eight-path commit and full hash/path recording before the next source change. This is a programmatic native library, with no AS configuration/Bean activation or permission registration/grants. Real IAM/cryptographic identity, PostgreSQL, TLS/network/deployed MCP and application runtime acceptance remain unverified. No application/server/database/container/browser, push, PR or release was performed. The original goal remains active: default-off MCP assembly and later T3/T4 work are not complete.
