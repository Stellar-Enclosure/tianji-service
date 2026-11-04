# Process control HTTP client delivery evidence

## Step 44 / Plan Step 2 — Verified for focused commit

- Step 43 committed `4b1d79d9563c82d22d825a58a769af3b2fecc2ff`, exact twelve paths, clean worktree, C 26 tests passed before any Step 2 edit.
- Baseline: 881 tracked file SHA-256 values in `/tmp/tianji-step44-baseline.json`; exact six authorized paths in `/tmp/tianji-step44-paths.json`.
- All 17 Manual Check and ten Literal Rule obligations are independently locked in `/tmp/tianji-step44-lock.json`. Existing C/CL technical profile and Adapter extension are preserved; no dependency, old contract, SQL, POM, server/config/permission or frontend change is authorized.
- Frozen primary Spec and Plan from Step 43 remain authoritative and unchanged. Root alone runs isolated offline Maven/test/strict/scope/commit gates, with skipITs=true and exact listener-free selectors.
- Test author owns only new HttpProcessControlClientTest and ProcessControlOpenApiContractTest. Root owns existing HttpProcessClient, new closed codec/OAS and this evidence. Initial interface/method/resource presence assertions must fail genuinely before implementation; no concrete final-class cast/missing import/fixture/dependency error counts as RED.
- No HttpProcessClientTest, listener, socket, database, application, browser/GUI or runtime provider is selected. CL transport uses the existing FakeHttpClient precedent; source/static contract proof is separate from native and IAM acceptance.

Initial Step 2 RED: `/tmp/tianji-process-control-client-red.log`, exit 1, 2 tests/2 expected assertion failures, zero errors/skips, 11.816 s, finished 2026-11-05T02:11:17+08:00. One test asserts the missing optional interface/control/cancel methods; the other asserts the missing packaged resource. Root afterward created only compile-prerequisite null methods, a default-mapper codec and a minimal empty-path OAS. Strict behavior and complete OAS remain intentionally absent pending typed fake/OAS assertion RED; these seams must never be committed.

## Typed behavior RED, scoped-error correction and GREEN

| Log | Actual result | Meaning |
| --- | --- | --- |
| `/tmp/tianji-process-control-client-red2.log` | 16 tests, 15 expected assertion failures / 1 passed, zero errors/skips; 12.365 s, finished 2026-11-05T02:28:11+08:00 | Compile-only null methods/default codec/empty OAS fail the full fake transport, strict parsing and semantic artifact assertions. No fixture/compile/cache failure counted as RED. |
| `/tmp/tianji-process-control-client-green1.log` | C19 + CL30 = 49 passed, zero failures/errors/skips; BUILD SUCCESS 21.016 s, finished 02:33:24+08:00 | First complete consumer passed its tests; independent review then identified operation-specific error allowlist coverage missing. |
| `/tmp/tianji-process-control-client-error-scope-red.log` | 17 tests, 1 expected assertion failure / 16 passed, zero errors/skips; 11.677 s, finished 02:37:51+08:00 | GET control incorrectly surfaced cancellation-only idempotency conflict. Root added cross-operation transport assertions before correcting production. |
| `/tmp/tianji-process-control-client-green2.log` | **51 passed**: C19 + CL32; zero failures/errors/skips; BUILD SUCCESS 20.492 s, finished 02:40:49+08:00 | Final error parsing is scoped per control/cancellation. GET409 fallback is tenant-context; cancel409 is idempotency; GET413 and all undeclared429 fall to dependency-unavailable. No old-family behavior changed. |
| `/tmp/tianji-process-control-client-artifact-verify.log` | Same selected **51 passed**, zero failures/errors/skips; BUILD SUCCESS 26.308 s, finished 02:43:16+08:00 | Explicit STELLAR_VERIFY_GOAL=verify completed real packaging so Root could open the emitted CL JAR. ITs remain disabled and the listener test is never selected. |

Every Maven command used the frozen Plan's JDK25, ADK_CAPTURE=false, isolated global/user settings, offline mode, skipITs=true, explicit finite selectors and disabled OTel exporters. Actual classes were ProcessControlContractTest19, HttpProcessControlClientTest15, ProcessControlOpenApiContractTest3, HttpCaseClientTest7 and CaseResponseCodecTest7. The latter two are real existing listener-free tests. `HttpProcessClientTest` was neither selected nor run; no HTTP server fixture was introduced.

## Actual implementation/artifact proof

The existing HttpProcessClient now implements the separate capability using its original call/URI/credential/bounded-body/timeout machinery. New methods send exactly one GET or POST, bind response instance/command identity, request no-store and require exact200. Only the new result-type family masks synchronous dispatch failure and uses the new closed, operation-specific error maps. Existing general Process/Decision/Worker/HumanTask/Case branches, constructor and URI policies are preserved; in particular the general Process2MiB policy is not replaced with Case128KiB/HTTPS-only rules.

ProcessControlResponseCodec uses existing Jackson3 with explicit depth16, string1024, name128, number32, token4096 and byte2MiB bounds; rejects duplicate/unknown/trailing/scalar coercion/float-int/numeric-enum/wrong-primitive input; requires nullable members to be present and booleans to be real booleans; rejects raw duplicate actions before Set conversion; uses C constructors for canonical order/caps/state invariants. Both independent native/DB time orders and nanos remain valid. Error correlation/body/reason/credentials/cause are never returned or retained in safe exceptions. Error429/cancellation-only pairs are not accepted as control provider semantics.

The OAS3.1 artifact has exactly two paths and ten closed model/enum schemas, all required/null/time/bounds and local refs, no time-order relation, exact operation-specific safe errors, no-store, principal/permission/resource metadata and one OAuth2 requirement carrying BOTH read/cancel scopes. Synthetic identity.example URLs describe the caller's existing OAuth flows; no real credential/client/registration is created. The artifact explicitly records its consumer-only/current provider-pending Step4 boundary and default-off feature; it is not provider acceptance.

Root opened the actual emitted `stellar-enclosure-tianji-client/target/stellar-enclosure-tianji-client-0.0.1-SNAPSHOT.jar` and verified:

- JAR SHA-256: `24bf6888a8f41dc44aea8c9da3e22294e19bd7f7270d0645ace9d6e97cfe02ef` (local packaging proof, not a clean release pin).
- Packaged `META-INF/tianji/openapi/process-control-v1.json` SHA-256: `3ce2e3c7293723d9a8321863c1a21aafd705d58d0f1f14ba41101506f70b77a6`, bytes identical to reviewed source, two paths/ten schemas.
- New ProcessControlResponseCodec bytecode is present; JDK25 javap confirms the actual JAR implements ProcessControlClient and exposes control/cancel while retaining existing constructor/method signatures.

Independent final review found no remaining actionable issue. Exact scope is six paths; **880 excluded tracked files match baseline SHA-256**, including all committed C types/tests, frozen Spec/Plan, POMs, existing Case OAS and all24 SQL. No V9/server/controller/permission/config/frontend change occurred. Target JARs/reports/build outputs are ignored and not committed. No scratch harness was added under repository scripts/work.

## Final Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing CL package/HttpProcessClient extended; exact six paths | Current adapter/module boundary preserved | None |
| MC-REUSE-001 | Applicable | PASS | Existing call, segment, subscriber, timeout/retry-after, Jackson and fake patterns | No alternate transport/JSON/test framework | None |
| MC-DEP-001 | Applicable | PASS | All POM/dependency SHA unchanged | Zero new dependency | None |
| MC-NAME-001 | Applicable | PASS | ProcessControlResponseCodec behavior suffix, exact C types reused | No duplicate carrier/layer | None |
| MC-VALID-001 | Applicable | PASS | Typed RED/final strict/affinity/error-scope tests | Every new request/response/error boundary validated | None |
| MC-MODEL-001 | Applicable | PASS | Immutable existing C models; JSON explicitly mapped through their constructors | No mutable entity/POJO introduced | None |
| MC-CONVERT-001 | Not applicable | N/A | Wire codec only, no distinct semantic Java conversion/Mapper | No MapStruct bypass or cross-layer carrier duplication | None |
| MC-LOG-001 | Not applicable | N/A | No business logger added; safe boundary exceptions and no raw logs | No logging class/Bean scope | None |
| MC-BEAN-001 | Not applicable | N/A | No Spring managed class/DI/config change | Existing explicit client constructor preserved | None |
| MC-UTIL-001 | Applicable | PASS | JDK/current Jackson only | Approved utilities, no new helper dependency | None |
| MC-JSON-001 | Applicable | PASS | Closed Jackson3/required/duplicate/coercion tests; actual OAS JAR byte proof | Wire authority and packaging agree | None |
| MC-TIME-001 | Applicable | PASS | Instant parse/nanos/both-order tests; OAS date-time fields | No cross-clock comparison/synthesis | None |
| MC-CONFIG-001 | Not applicable | N/A | No env/profile/feature key change | Provider flag remains a later step | None |
| MC-PATTERN-001 | Applicable | PASS | Existing HTTP Adapter extended via decoder function and separate capability | Concrete reuse, no new registry/hierarchy | None |
| MC-SCOPE-001 | Applicable | PASS | Six exact paths; 880 excluded SHA/all24 SQL unchanged | Consumer-only scope preserved | None |
| MC-TEST-001 | Applicable | PASS | Assertion REDs, final51 GREEN + actual verify/JAR inspection | Finite gates completed without listener/provider runtime | None |
| MC-BLOCKER-001 | Applicable | PASS | All checks and final independent review | No unresolved CL-source blocker; storage/provider remain required | None |

## Final Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path evidence | Validation evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Codec/Client names, existing C records | Compile/signature/OAS tests | Semantic roles preserved | None |
| Rule 2 | Applicable | PASS | New request null/path checks and closed response/error boundary | Strict fake/codec negatives | Approved technical contract convention; no unrelated validation dependency | None |
| Rule 3 | Applicable | PASS | Existing immutable C constructor mapping | State/null/shape tests | No ordinary POJO/entity/converter introduced | None |
| Rule 4 | Not applicable | N/A | No Spring/business logger/DI target | Scope/import review | Existing library client/codec convention preserved | None |
| Rule 5 | Applicable | PASS | JDK/current Jackson APIs only | POM/import/excluded SHA checks | Utility scope respected | None |
| Rule 6 | Applicable | PASS | Jackson3 strict codec and deliberate names/enums | OAS/fake/malformed/raw-set tests and actual JAR | No alternate JSON/ordinal or accidental field mapping | None |
| Rule 7 | Not applicable | N/A | No configuration/profile target | Excluded SHA checks | No key divergence | None |
| Rule 9 | Applicable | PASS | Actual existing HTTP Adapter + decoder extension | One-send/timeout/error/affinity cases | Known variation isolated without extra hierarchy | None |
| Rule 10 | Applicable | PASS | java.time.Instant/Duration existing transport semantics | Both-order/nanos/timeout tests | No java.util time introduction | None |
| Rule 11 | Applicable | PASS | Existing C→CL dependency and technical package | Compile/verify/scope/JAR proof | No new layer or persistence/Event responsibility | None |

Step2 is verified for one focused commit. Native cancellation, V9/storage/readiness, default-off provider admission, IAM and runtime acceptance remain later work. The original T3-10 compensation/dead-letter recovery and overall roadmap remain incomplete.
