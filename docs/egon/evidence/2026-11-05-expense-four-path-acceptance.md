# T3-12 expense acceptance source and runtime handoff — Source64

Baseline `47818aa6cd3de6e67fe8279d044fcef8321ab2cd`, after [model](2026-11-05-expense-four-path-models.md), isolated harness and fixture commits. Effective [Spec](../spec/2026-11-05-15-13-expense-four-path-acceptance.md) and [Plan](../plan/2026-11-05-15-30-expense-four-path-acceptance.md).

The two opt-in test classes use the real SDKs, distinct test-launched AS/PS JVMs, ordinary authentication and the existing Worker Store/poller. The only model substitution is the deterministic existing adapter seam; relay faults delay/drop actual traffic after scoped durable evidence. Business cases require native DMN, Agent terminal delivery, native manual task and formal CMMN where reached, one durable business effect and exact compensation where selected. Recovery uses distinct lost-complete, early Inbox plus Agent replacement, and native wait plus Process replacement occurrences for each tenant. It verifies original lease expiry and fresh token in memory, old lease denial, same-event fingerprint resend, and retained native timer/subscription IDs. No token or hash is included in evidence. Foreign-tenant reads and distinct durable identities are checked using the same textual operation/business/command IDs.

| Source gate | Actual result |
| --- | --- |
| Step 1 model/JSON | Resource RED, then 112 passed in six finite classes. |
| Step 2 harness | Reflection RED 1F and typed RED 9F, then 111 passed in five finite classes. |
| Step 3 fixture | Reflection RED 1F and typed RED 2F, then 71 passed in eight finite classes; both current exec archives exclude test classes. |
| Step 4 G7 clean verify | 183 passed in 14 selected classes (W 11, AS 8, PS 164), zero failures/errors/skips, 1:02, 2026-11-05 16:20:13 +08:00. Both ITs compiled with exact test methods. Neither has a Surefire execution report. |

Offline JDK25 source reactor with `clean verify`, `-DskipITs=true`, isolated task settings, OTel exporters and ADK content capture off. See the exact G7 selector in Plan Step 4. Both actual exec archives retain production Start-Class and PropertiesLauncher, and exclude the compiled ITs, fixture mains and Business Worker fixture. This verifies the source/package boundary. No application, test child process, native engine, database, listener, browser, IAM endpoint or model provider was started. S12 actual exporter/collector behavior is not implied.

### Operator-run E

Use a fresh approved environment and two different private manifests with `suite` `BUSINESS` and `RECOVERY`, each with three fresh distinct databases. The closed shape is `ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO`: schema version 1, unique run ID, explicit `runAuthorized: true`, exact canonical checkout/private/output/fixture paths, five current source revisions, both exact exec JAR and test-class tree digests, two tenant bindings, three store bindings, TLS files, exact loopback endpoints and bounded phase limits. The manifest and referenced credential files are mode 0600 in an owner-only directory outside the checkout; the output directory is 0700. No secret belongs in Git, a command argument, or the evidence JSON.

- Tenants: distinct trusted issuers and tenant IDs with the same owner subject text, two distinct reviewers and exactly three pinned role IDs per tenant. Use real isolated IAM provisioning for the existing permissions, exact audiences/scopes and dedicated service registrations. Each private tenant properties file supplies `token.owner`, `token.agent-owner`, `token.reviewer-1`, `token.reviewer-2`, `token.worker`, required `identity.*` and `registration.*` keys consumed by the driver, plus existing isolated IAM property values where needed. Do not reuse production credentials or endpoints.
- Stores: AgentDB current V1–V14, ProcessDB V1–V10 and native Flowable 8, BusinessDB Worker V1/V2 in `tianji_worker`. Precreate the separate, empty `compensation_fixture` schema owned by the isolated setup role. Business tables and the technical receipt share one BusinessDB connection and local transaction. Verify pgvector, role/grants, exact separate Outbox histories and source identity before either child starts. Store files contain only `setup.password`, `runtime.password`, `observer.password`; TLS uses the exact pinned private stores. The operator supplies valid credentials and provisioned schemas; the finite build neither prepares nor runs them.
- Locks: use exact exec JAR SHA-256 and the test-class tree algorithm in `ExpenseAcceptanceHarness.classTreeSha256` (unsigned UTF-8 relative path order, four-byte big-endian path length, path bytes, then 32-byte file digest). Store history locks use `Driver.history`: ordered version/script/checksum records and the pinned Outbox/native extensions. The two suites must use the same reviewed source, fixture and executable artifacts with distinct fresh stores.

After separate authorization, run from the service repository using the already built matching artifacts and the existing source-reactor wrapper:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
STELLAR_VERIFY_GOAL=test ./scripts/verify-source-reactor.sh \
  -gs "$settings" -s "$settings" -o -B -ntp -DskipITs=true -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=BusinessProcessAgentAcceptanceIT \
  "-Dtianji.expense.e2e.manifest=$expense_business_manifest"
STELLAR_VERIFY_GOAL=test ./scripts/verify-source-reactor.sh \
  -gs "$settings" -s "$settings" -o -B -ntp -DskipITs=true -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=WorkerRecoveryIT \
  "-Dtianji.expense.e2e.manifest=$expense_recovery_manifest"
```

Require actual nonzero reports with zero failures/errors/skips, six BUSINESS and two RECOVERY tenant/path records, the three separate recovery witnesses per tenant, actual distinct child/replacement identities and exact durable result references. Missing prerequisites, an expired timer, an unavailable witness or a skipped run is not an E pass. Retain only private redacted evidence on failure.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Test-only PS orchestration, actual existing AS/PS/Worker owners retained. |
| MC-REUSE-001 | PASS | Exact current SDK, relay/driver, native witnesses, Boot executables and Worker transaction. |
| MC-DEP-001 | PASS | No dependency/POM change, actual offline package. |
| MC-NAME-001 | PASS | Exact IT and fixture witness names, no new production type. |
| MC-VALID-001 | PASS | Strict operator admission, exact source/owner and checked causal stage predicates in compiled source. |
| MC-MODEL-001 | PASS | Closed immutable fixture/evidence types and current contract DTOs. |
| MC-CONVERT-001 | N/A | No production mapping; same-layer read-only evidence projection. |
| MC-LOG-001 | PASS | Fixed failure reasons and scoped redacted evidence, no payload/credential logging. |
| MC-BEAN-001 | PASS | Earlier named test-only Fake; no new managed production Bean here. |
| MC-UTIL-001 | PASS | Existing JDK/Spring/Jackson/JDBC and current SDKs. |
| MC-JSON-001 | PASS | Current exact contracts, fixture digest, safe operator/evidence shapes. |
| MC-TIME-001 | PASS | Instant/monotonic bounds and actual durable ordering checks; no DB/engine time changes. |
| MC-CONFIG-001 | PASS | No checked-in runtime config; strict private locked manifest only. |
| MC-PATTERN-001 | PASS | Scenario Orchestration with fixed causal windows and existing Worker Strategy. |
| MC-SCOPE-001 | PASS | Three paths, 983 excluded tracked hashes and all 26 main SQL unchanged. |
| MC-TEST-001 | PASS | Exact G7 compile/packaged finite results, no IT execution or skip used as evidence. |
| MC-BLOCKER-001 | PASS | Current source gates closed; all E acceptance remains pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing test suffixes and named exact witness/identity types. |
| 2 | PASS | Current validated commands, exact source and fail-closed admission. |
| 3 | PASS | Immutable facts and DTOs, no entity/mapper fork. |
| 4 | PASS | Existing managed and private test boundaries, safe redacted reporting. |
| 5 | PASS | Current SDK, JDBC, Spring/Jackson and JDK only. |
| 6 | PASS | Exact existing JSON plus bounded fixture/evidence shapes. |
| 7 | PASS | Private test input only, production profile bytes unchanged. |
| 9 | PASS | Explicit Scenario Orchestration and real existing Worker Strategy/Store. |
| 10 | PASS | UTC instants, monotonic phase limits and no synthetic native clocks. |
| 11 | PASS | Existing test-only profile, no new production layer/dependency/DDL or CQE owner. |

The service source slice is verified. Web fixture parity is its own Step. Live T3-12, IAM, PostgreSQL, true restart and T3-10 native Worker recovery acceptance remain unexecuted.
