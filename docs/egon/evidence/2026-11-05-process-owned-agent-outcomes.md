# Process-owned Agent outcomes evidence

Service baseline 279d8858e46981c7930c838479bf0970fb32d0c9. [Spec](../spec/2026-11-05-22-41-process-owned-agent-outcomes.md) and [Plan](../plan/2026-11-05-22-42-process-owned-agent-outcomes.md) pass strict validation.

The optional read verifies the complete Process START owner, the current single configured Process source and the exact V5 dispatch and V6 Inbox relation on one physically read-only primary snapshot. A terminal status comes from an exact locally recorded Agent event. An APPLIED result also requires its existing committed Process application receipt. Observations show the authored activity, local dispatch and Inbox states and their timestamps. The cursor carries a scoped digest rather than native occurrence identity. This phase supports up to 256 occurrences in the selected source with a 20-item page. Larger sets return unavailable.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure. The controller class did not exist at baseline. |
| R2 | One expected assertion failure. The typed inert read returned zero rather than the expected 20 rows. |
| G1 | 69 tests passed across eight named suites, zero failures/errors/skips. |
| P1 | Clean offline permission-producer verify repeated 69 passes. Actual AS and PS packages checked. The PS OpenAPI bytes match source and one new protected GET/HEAD with the existing Process READ and scope. |
| S1 | Strict documents, exact 16 paths, excluded files checked and all 27 existing migration bytes preserved. |

Actual PS archive SHA-256 da0fb659b90334dc9a1eb087ac8fe2c5c0444fd6092f21920d7428aa5b8392d4. Raw PS PaC SHA-256 cc85967f2c627e7afbf5f06564751cf82f9218dc4b0dc093babc5f61ad361f70. OpenAPI SHA-256 f1fa0bee4a842fb6210e4f6baac99697d9f309506b07e9ee07251ce4de70d58b. YAML retains the existing SLA default and adds one false owner-outcomes flag. The prior and actual PS manifests differ only by provenance and one resource/English label. The existing permission definitions, AS PaC content and Process correlation OAS remain intact. The packaged proof used Python 3.14 for hashlib.file_digest.

Manual and applicable literal checks cover original storage and identity ownership, exact registry and persisted evidence, bounded pagination, existing error mapping, closed UTC/DTO fields and optional configuration, staged scope and real package. This owner view uses PS evidence only. Live Process database query-plan, real IAM, Agent-side Tool/Model traversal and S12 remain separate.
