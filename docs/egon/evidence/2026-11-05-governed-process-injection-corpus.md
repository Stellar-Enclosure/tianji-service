# Governed Process injection corpus — Source83

Effective [Spec](../spec/2026-11-05-19-27-governed-process-injection-corpus.md) and [Plan](../plan/2026-11-05-19-28-governed-process-injection-corpus.md), baseline `6fcd76be629680d8cd8863537a4c31adc2506952`. Both strict validators passed.

The existing AR governed Process loop fixture runs actual ADK Runner, the Spring AI adapter, and the existing tool bridge with a scripted Fake model and in-process recording doubles. The versioned corpus pins the definition/tool references, route, policy and host scope. Its SHA-256 is `725704a7f3982caa6853892f0e0b6aa989045ca81f4eea29af9348650c7944c0`. There is no production behavior, configuration, API, database or dependency change.

| Vector | Actual assertion result |
| --- | --- |
| Trusted control and hostile task text with otherwise valid tool arguments | Only the exact trusted tenant/business/tool binding dispatches once; task evidence remains in the user role. |
| Fake tenant, release, or approval argument | Each produces `TIANJI_INVALID_COMMAND`, no recorded write, approval lookup, tool event or delegate; effect remains `NONE`. |
| Approval claimed only in user text | User text remains untrusted; actual host returns `AWAITING_APPROVAL`, with no verified approval or delegate and effect `NONE`. |

| Gate | Actual result |
| --- | --- |
| M1 | 1 missing-resource assertion failure, zero errors/skips, 19:33:57 +08:00. |
| M2 | 6 ran against the inert Fake override, three controls passed and three intended FAILED-vs-COMPLETED assertions failed, zero errors/skips, 19:35:06 +08:00. |
| M3 focused | All six named vectors passed, zero failures/errors/skips, 19:36:08 +08:00. |
| M3 owner | All 36 owning tests passed, zero failures/errors/skips, 19:36:33 +08:00. |
| M4 | Strict effective docs, exact five paths, unchanged excluded files and original 27 SQL hashes. |

The JDK 25 offline source reactor used the exact Plan selectors with integration tests skipped and telemetry exporters disabled. Raw Surefire reports are retained under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-05/fake-governed-injection/reactor-reports/`.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing AR test owner and native adapter chain. |
| MC-REUSE-001 | PASS | Existing Rig, Mockito doubles, JUnit and Fake model. |
| MC-DEP-001 | PASS | No POM or dependency change. |
| MC-NAME-001 | PASS | Closed six named vectors and pinned v1 fixture. |
| MC-VALID-001 | PASS | Strict fixture keys/schema, exact host reference and denied forged fields. |
| MC-MODEL-001 | PASS | Existing tool/AgentInputDTO and result types. |
| MC-CONVERT-001 | PASS | Existing JSON converter and exact test fixture data. |
| MC-LOG-001 | N/A | No added logging. |
| MC-BEAN-001 | N/A | No Spring or application wiring. |
| MC-UTIL-001 | PASS | Existing test utilities and test resource conventions. |
| MC-JSON-001 | PASS | Strict test fixture parser only, existing wire unchanged. |
| MC-TIME-001 | N/A | No production timing or clock change. |
| MC-CONFIG-001 | N/A | No runtime or environment configuration change. |
| MC-PATTERN-001 | PASS | Existing test Rig and scripted Fake extension; no production abstraction. |
| MC-SCOPE-001 | PASS | Two test paths plus three documents, 27 SQL files unchanged. |
| MC-TEST-001 | PASS | Two assertion REDs, six targeted and 36 owning passes. |
| MC-BLOCKER-001 | PASS | U-only scope and remaining independent gates explicitly recorded. |

Rules 1, 2, 3, 5, 6, 9 and 11 were checked against the typed existing fixture, exact values, closed parser, test-only extension and unchanged production paths. Rules 4, 7 and 10 have no changed logging/bean, config or time contract respectively.

This is S07 U-layer protocol and authorization evidence. It does not measure how often a real model resists injection, prove a real downstream permission decision, or pass S07 I/E. No application, provider, listener or database was started.
