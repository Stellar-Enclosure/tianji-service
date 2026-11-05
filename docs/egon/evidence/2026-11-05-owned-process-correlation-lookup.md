# Owned Process correlation lookup evidence

Service baseline d5ccd8225da3c8f27adffdee04cb9d1d787ed7f2. Master §19 T4-02. [Spec](../spec/2026-11-05-20-36-owned-process-correlation-lookup.md) and [Plan](../plan/2026-11-05-20-37-owned-process-correlation-lookup.md) both pass strict validation. SHA-256 d98a298bb4bc2c95edb03d0b7f44f4e91e79da182e6a0b382dbc58ee69ce0adc and f1fe05688a7d0f18100090558bf945a514b5bd55a5117b3ea67e003286e497e5 respectively.

The endpoint requires current Process READ, its existing OAuth scope and its own resource policy. The server resolves all four owner components before parsing query values. A two-second repeatable read selects APPLIED START receipts on the Process primary, after setting and checking physical read-only mode. The fixed 21-row limit identifies continuation for a 20-item page. Cursor replay must match the authenticated owner, exact correlation and UTC window, and resolve an anchor in that same scope. Each receipt ID can be used for the separately authorized Process detail GET. Receipt references do not represent the current native process state.

| Gate | Actual observation |
| --- | --- |
| R1 | Missing route returned 404 instead of expected 200. One assertion failure, no errors or skips. |
| R2 | Typed empty result returned 0 instead of expected 20. One assertion failure, no errors or skips. |
| R3 | Typed A query accepted 31 days plus one microsecond. One assertion failure, no errors or skips. |
| G1 | OrchestrationContractTest 9, ProcessCorrelationLookupTest 3, ProcessControllerTest 7, ProcessPermissionIntegrationTest 12, ProcessExecutionServiceTest 4, ProcessStorageConfigurationTest 11. Total 46 passed with no failures, errors or skips. |
| P1 | Offline clean permission-producer verify repeated the same 46 passes, using local build ID LOCAL_UNCOMMITTED-t4-02-process-correlation-20261105. Actual ZIP root contains one OpenAPI file equal to source. The inspection path was corrected in the Plan after checking the repackage layout. |
| S1 | Strict documents, exact 15 paths, 1043 excluded tracked files and all 27 existing SQL files verified by content. |

The PS exec archive SHA-256 is 962bfd793883597f09736aa4d773829c014d2430acab7731acc6d3ff8fe1891f. Its OpenAPI SHA-256 is 80bd174998a1dd6b3188bbd3931447c4e7eb79e055bb5046cd3a35e87f090b0a. Compared with the retained verified PS producer, the new manifest adds one protected GET and implicit HEAD resource with the existing READ permission and scope, plus one English label. Existing resource definitions, permission definitions, references and application YAML match. AS permissions also match outside build metadata.

Manual checks and independent rules were reviewed against the exact diff. Architecture, reuse, dependency, naming, validation, model, conversion, bean, utility, JSON, time, pattern, scope, test and blocker checks use the existing technical Process receipt boundary and verified source gates. Logging and configuration checks found no change to values or registration. This introduces no mapper, business workflow, new dependency, database migration or new runtime configuration. Existing flow, release, command, trace and artifact policies are preserved except the declared new API resource.

Actual PostgreSQL query plan/cardinality and runtime IAM remain unverified. The query uses the existing owner key and lacks a dedicated correlation/time index. It is time bounded, and a timeout is a dependency failure rather than an empty result. SDK, Web, direct business-key discovery, cross-owner correlation and full S12 remain separate.
