# Exact owned Process START receipt evidence

Service baseline fd5ebbc329d3a4f78221fb03d3230483f49f77b1. [Spec](../spec/2026-11-05-23-50-owned-process-start-receipt.md) and [Plan](../plan/2026-11-05-23-51-owned-process-start-receipt.md) pass strict validation.

The default-off GET verifies the current protected Process read resource and exact full START owner before selecting one applied receipt with matching command ID and request SHA-256. The same primary snapshot returns only the committed instance reference, APPLIED status and existing receipt creation time. The time does not represent Process completion. Missing, non-APPLIED, foreign and differing-digest receipts share the same 404, with no inference that an effect is absent. The query never replays START.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure for the absent typed query. |
| R2 | One expected assertion failure for the typed inert read with no exact receipt. |
| R3 | One expected assertion failure for the baseline USER-only endpoint classification. |
| G1 | 60 tests across eight named suites passed, with no failures, errors or skips. |
| P1 | Clean offline permission-producer verify repeated 60 passes. Actual PS ZIP verifies one GET/HEAD with existing Process READ and scope, USER/SERVICE, one English label and one exact OAS. |
| S1 | Strict docs, exact 17 paths, excluded tracked bytes and all 27 existing migrations checked. |

PS archive SHA-256 e0c634e7f9ef913f132b6924c7e90ae47ab52897fcaa344131a8cae78f2b7496. Raw PS PaC SHA-256 4118e1efce240e8f8628bb6261d461207ba23e76fc3daff59c94992cc12f33bd. OpenAPI SHA-256 1ed3825b28fe16a699144e1ac5be8c19404c6fbc62e322f65d5d8208879a7934. Actual YAML adds the new false option in its previously absent api subtree and preserves preflight defaults. The prior exact PS manifest content was compared apart from provenance and the one resource/label. AS declaration content matches baseline. The scoped artifact inspection was aligned to the two-line new YAML subtree.

Manual and applicable literal checks cover existing verified owner and primary read reuse, closed exact input and minimal DTO, uniform absence and controlled failure, current protected resource, source and package scope. The original AS Tool transport scope and grants remain untouched. External reconciliation, replay decisions, real PostgreSQL/IAM and S11 remain separately gated.
