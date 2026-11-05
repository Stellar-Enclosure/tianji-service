# Owned Process migration preflight API evidence

Service baseline d9bec9c34bdbaa337cb5bd8eb776e2383bac97c6. [Spec](../spec/2026-11-05-23-44-process-migration-preflight-api.md) and [Plan](../plan/2026-11-05-23-45-process-migration-preflight-api.md) pass strict validation.

The new protected GET is separately default-off. It uses the existing Process READ and release READ permissions and scopes. The exact caller on the new resource must match the caller on both existing guards before any storage query. The instance START owner and the pinned target release remain verified by the existing same-source internal preflight. The result is one closed structural observation and has no execution effect.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure. Baseline controller class was missing. |
| R2 | One expected assertion failure. The typed A service delegated without denying a mismatched route actor. |
| R3 | One expected assertion failure. The typed A controller returned 503 rather than the expected 200. |
| G1 | 50 tests across six suites passed. Failures, errors and skips were zero. |
| P1 | Clean offline permission-producer verify repeated 50 passes. Both actual ZIP inspections passed for the corrected OAS and exact resource, both existing scopes/codes, label and two false switches. |
| S1 | Strict docs, 11 exact paths, excluded tracked bytes and all 27 existing migrations checked. |

PS executable SHA-256 ae9affef951006ab4ef741913488e6350750e5303957d159637cc4c8d13fe9fe. Raw PS PaC SHA-256 2997f98b0a33d964465fecc143009131b24d249f502fc8c1a82437af1dda0edd. OAS SHA-256 1fbc18706416b4f2c6bdb3ddb3f7e6ba96cb75b2617f12fd4264d8036b1f54e0. First archive check rejected an inherited unrelated resource key and one-permission OAS declaration. Source OAS was corrected, then a clean build and both actual package checks passed. Python 3.14 was used for the package helper's file digest. Existing PS declarations and AS content were checked against the prior artifact.

Manual and applicable literal checks cover existing owner/readiness reuse, exact caller and source release evidence, bounded request, closed DTO and HTTP contract, independent opt-in, precise no-write route and actual package. Real PostgreSQL/Flowable, live IAM, formal migration execution and release activation remain separately gated.
