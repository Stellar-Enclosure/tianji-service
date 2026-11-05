# Agent owner recovery disposition evidence

Service baseline 1ae891f2aca338cceb80fc0d8517c100a53ae55b. [Spec](../spec/2026-11-05-23-14-agent-owner-recovery-disposition.md) and [Plan](../plan/2026-11-05-23-15-agent-owner-recovery-disposition.md) pass strict validation.

The default-off GET authorizes its own current resource and uses the exact authenticated Agent run owner. It projects the existing ledger observation into finite lifecycle, reason, recorded effect and bounded tri-state Tool evidence. Truncated or unavailable evidence remains distinct from a negative observation. It leaves execution, ownership and recovery actions to their existing boundaries.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure. The controller class was absent at baseline. |
| R2 | One expected assertion failure. The typed controller returned 503 instead of the required owner observation. |
| G1 | 41 tests passed across six owning and adjacent suites, with zero failures, errors or skips. |
| P1 | Clean offline permission-producer verify repeated 41 passes. Actual archive contains the exact OAS and default-off YAML. One protected GET/HEAD and one English label were added, using the existing Agent READ and OAuth scope. |
| S1 | Strict docs, exact nine paths, excluded tracked bytes and all 27 existing migrations checked. |

AS executable SHA-256 76016c1d5140cccc707e9e020b457cc5946e854fa2c0c32e5580d76567bae333. Raw AS PaC SHA-256 a762fbe7316bdb46c01a98438056b3277228b7eb7b45b04bb4dca23720723a26. OAS SHA-256 c03760cd3bb0b4bee3dba9c11b707e7a3d8de4406cedce6f89c76969e0e9eb0c. PS permission contents match the prior artifact. The first package check found an incorrect inherited LIST resource key and pagination marker in the OAS. These were corrected and the clean build and both actual ZIP checks passed. The package helper used Python 3.14 for hashlib.file_digest.

Manual and applicable literal checks cover existing full-owner storage and access, exact neutral DTO validation, closed public route and authorization, existing Spring conditions, source and package validation and byte preservation. Live database/authorization, external receipt reconciliation, persisted checkpoint and Run takeover, and S11 remain separate.
