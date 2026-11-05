# Owned Agent correlation filter evidence

Service baseline af504a47f5251bfd96528bab7568983ca3a02516. [Spec](../spec/2026-11-05-21-52-owned-agent-correlation-filter.md) and [Plan](../plan/2026-11-05-21-53-owned-agent-correlation-filter.md) pass strict validation.

The existing GET and its LIST authority are reused. The filter branch requires one exact correlation plus both UTC bounds and optionally one cursor. The original unfiltered branch keeps its original limit and cursor behavior. The bounded filtered branch reads only the current four-part owner in one read-only primary snapshot. The scope digest binds the cursor to the exact identity, correlation and normalized time window. The server verifies anchor membership again in that same scope. A page returns up to 20 existing AgentRunDTO items. These are current owner runs sharing the specified correlation, with no inferred link to any Process instance or service-owned run.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure for absent typed query. |
| R2 | One expected assertion failure for the inert empty page versus the owned result. |
| R3 | One expected assertion failure for old HTTP 200 versus required 400 on an incomplete filter. |
| G1 | 55 tests across 6 owning suites passed. The standalone MockMvc permission assertion was corrected to check the AccessDenied cause and zero ledger calls. The existing actual security-chain suite covers HTTP mapping. |
| P1 | Clean offline permission-producer build repeated 55 passes. Actual AS and PS permission contents preserved outside expected build provenance. One AS root OpenAPI entry matches source. |
| S1 | Strict docs, exact 11 paths and 27 unchanged main SQL files. |

P1 counts are AgentContractTest 8, AgentRunCorrelationLookupTest 7, AgentRecoveryDispositionEvidenceTest 5, AgentRunHttpTest 12, TianjiPermissionIntegrationTest 16, and AgentModelUsageDiagnosticsTest 7. Failures, errors and skips are zero. The AS archive SHA-256 is f4cf3380bb22929bad18ff198d0e85b7cc67b7b5d061d5ac6a17f29187e4b1b4. The raw AS PaC SHA-256 is 1042f3e1b48fe202b3bb17a8c551b1b6eaafa22352e9c96fd12806582dd0e971. OpenAPI SHA-256 is 718db475744eab746ad41f9307a8263381a0582ec6682bbb953f70ec6e4b3b6a. Existing application configuration matches baseline. No dependencies or new permissions.

Manual and applicable literal checks used existing owner persistence, read transaction and access patterns, strict input/DTO bounds, C collation and UTC OffsetDateTime binding, fixed error codes and exact scope. Existing model usage and recovery tests pass alongside the new read. Actual PostgreSQL roles, planner/cardinality, real gateway and deployed S12 remain separately gated.
