# Agent result receive Span Links — Source84

Effective [Spec](../spec/2026-11-05-19-19-agent-result-receive-span-links.md) and [Plan](../plan/2026-11-05-19-20-agent-result-receive-span-links.md), baseline `af26473216d259049a4edac8b2d42b236d3a5fa7`. Both strict validators passed. DEC-001 explicitly approves the two exact Process Server dependency declarations and the one existing-route private header. The approval covers source only.

With both tracing controls enabled on the sender, AS may send the current local recording and sampled delivery Span as one closed diagnostic header. CL attaches it only to HTTPS and raw `POST /api/tianji/v1/agent-result-inbox`; existing prefixed or cleartext requests keep their old headerless behavior. PS completes its original authorization and receipt path before reading the header. Only the exact POST and a single valid value can create a Link. The PS span retains its existing ambient parent. Invalid or unavailable diagnostics do not change an existing result, error, receipt or business transaction. Event bytes, fingerprint, Outbox payload, replay and application permissions remain unchanged. The local transport validator and observer reuse the current owner boundaries; no additional propagation or storage is introduced.

| Gate | Actual result |
| --- | --- |
| R1 | 1 expected false-versus-absent configuration assertion. |
| R2 | 1 expected private-header-missing assertion after the exact existing POST. |
| R3 | 1 expected SDK Span count 1 versus 0 after mocked successful receipt. |
| R4 | 1 expected Mockito call assertion: old one-argument delivery rather than the Span-derived overload. |
| G1 | 24 passed across 5 CL/AS/PS suites, zero failures/errors/skips. |
| P1 | Clean offline JDK 25 permission-producer build, 47 passed across 7 suites, zero failures/errors/skips; finished 19:45:26 +08:00. |
| S1 | Exact 16 paths, 13 source/config/test files byte-equivalent to the reviewed patch, 27 existing SQL files unchanged. |

Each R gate executed one test with zero errors/skips. Exact selectors and commands are in the Plan. Raw reports and actual archive proof are under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/`.

| Actual packaged artifact | SHA-256 | Inspected facts |
| --- | --- | --- |
| AS exec JAR | `7af162bc8bd16988d66b43db33ea10983c67e07118c32825abd65313c8fd1864` | New cross-process flag false, existing local-link flag false, existing OTel library inventory unchanged. |
| PS exec JAR | `7456d610e233ed13609b8eac56a786e8201a4390145b985c86dda3e77f16f810` | Receive flag false, existing lag and migration defaults preserved; only OTel API, Context and Common 1.62.0 in runtime. No test Trace SDK, tracing bridge or exporter was added to the PS archive. |

Both root PaC manifests have identical application, producer, resources, references, definitions and messages to their respective baseline, outside build artifact metadata. No permission was activated or granted.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing AS deliver, CL transport and PS receive owners; no new HTTP method. |
| MC-REUSE-001 | PASS | Existing Span and transport, committed receipt, unique optional PS provider. |
| MC-DEP-001 | PASS | User-approved BOM API and test-only SDK; actual PS archive exact runtime delta. |
| MC-NAME-001 | PASS | One fixed header, exact method/path, fixed span/scope names. |
| MC-VALID-001 | PASS | Closed sampled format, exact path, duplicate/rejected requests excluded. |
| MC-MODEL-001 | PASS | Current event/receipt and public C method preserved. |
| MC-CONVERT-001 | PASS | Only closed context ID/flag conversion. |
| MC-LOG-001 | PASS | No header, tenant, owner, run, token or body added to logging or attributes. |
| MC-BEAN-001 | PASS | Existing configuration, disabled path bypasses provider and header lookup. |
| MC-UTIL-001 | PASS | Existing JDK and OTel native Link, no tracing proxy or other bridge. |
| MC-JSON-001 | PASS | Old/new CL captured JSON and fingerprint identical. |
| MC-TIME-001 | PASS | Existing timeouts/deadlines retained, no new clock or deadline. |
| MC-CONFIG-001 | PASS | Explicit false in both actual archives. |
| MC-PATTERN-001 | PASS | Narrow observer at an existing lifecycle edge; no new business abstraction. |
| MC-SCOPE-001 | PASS | Exact 16 paths, 27 SQL and both PaC contents unchanged. |
| MC-TEST-001 | PASS | Four RED, 24 finite and 47 clean package passes. |
| MC-BLOCKER-001 | PASS | Runtime provider, forwarding, exporter and S12 remain independently gated. |

Rules 1–7 and 9–11 were checked against the fixed diagnostic identifiers, validated boundary, preserved event and receipt types, existing configuration, named flags, unchanged JSON and timing contracts, narrow observer and exact dependency/profile delta.

The static build and finite tests did not start an application, listener, database, telemetry collector or provider endpoint. Real proxy/TLS, current IAM, PS provider, sampling, nonblocking export and listener compatibility remain prerequisites for separate enablement. There is no persisted Link for delayed application after restart; Worker and S12 end-to-end evidence remain separate.
