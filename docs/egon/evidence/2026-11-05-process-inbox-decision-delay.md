# Process Inbox committed decision delay — Source80

Effective [Spec](../spec/2026-11-05-18-43-process-inbox-decision-delay.md) and [Plan](../plan/2026-11-05-18-47-process-inbox-decision-delay.md), baseline `40ba8e1f0f968fd374cea389e9febed6bf952c7a`.

The optional Process owner-local Timer `tianji.process.agent.result.inbox.decision.delay` uses the receipt and decision times from the same existing winning Inbox row. It records only after the actual existing transaction reports `STATUS_COMMITTED`, for fixed outcomes `applied`, `ignored_late` and `dead`. Losing updates, replay, rollback, mismatched readback, invalid intervals and unavailable registry produce no extra sample or business change. There are no identifier tags, new query, SQL change, poller or exporter.

| Gate | Actual result |
| --- | --- |
| R1 | 1 intended absent-helper assertion failure; 0 errors/skips. |
| R2 | 1 intended typed missing-sample assertion failure; 0 errors/skips. |
| R3 | 1 intended absent-factory-seam assertion failure; 0 errors/skips. |
| G1 | 38 passed in four PS classes, zero failures/errors/skips, 19:02:50 +08:00. Exact 3 planned classes plus existing 7 correlation/legacy-constructor tests. |

G1 selector: `AgentResultInboxLagObservationTest,ProcessApiConfigurationTest,AgentDispatchStoreTest,AgentResultCorrelationServiceTest`. Offline JDK25 source reactor with `-DskipITs=true`, no exporter or content capture. Existing `AgentResultInboxPostgresTest.class` compiled and had no execution report. Source config is explicitly false. The actual first compilation found PS lacks Lombok, so the technical adapter uses the existing PS SLF4J LoggerFactory convention without a dependency or POM change. Logs and exact reports under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/`.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS Inbox owner and existing transaction callbacks. |
| MC-REUSE-001 | PASS | Existing winning UPDATE/readback, MeterRegistry and named factory. |
| MC-DEP-001 | PASS | Existing JDK/Spring/Micrometer/SLF4J only; no POM change. |
| MC-NAME-001 | PASS | Fixed metric/tag/observation names. |
| MC-VALID-001 | PASS | Exact winner, valid interval, committed-only and off/absent registry tests. |
| MC-MODEL-001 | PASS | Existing immutable receipt/Entry preserved; safe internal decision timestamp. |
| MC-CONVERT-001 | PASS | Existing exact JDBC row projection only. |
| MC-LOG-001 | PASS | Existing technical LoggerFactory and one fixed diagnostic, no IDs. |
| MC-BEAN-001 | PASS | Existing named factory only, default off avoids registry resolution. |
| MC-UTIL-001 | PASS | Existing Micrometer, JDK time and transaction sync. |
| MC-JSON-001 | N/A | Wire/receipt JSON unchanged. |
| MC-TIME-001 | PASS | Exact DB times and Duration, negative/overflow excluded. |
| MC-CONFIG-001 | PASS | Explicit false and original prerequisites retained. |
| MC-PATTERN-001 | PASS | Narrow optional observation on existing Inbox lifecycle. |
| MC-SCOPE-001 | PASS | Exact nine paths and 27 unchanged main SQL files. |
| MC-TEST-001 | PASS | Three actual RED and 38 finite green, PG compile only. |
| MC-BLOCKER-001 | PASS | Current source verified; exporter, actual DB and SLA remain pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing PS semantic names. |
| 2 | PASS | Exact committed winning-state and safe time predicates. |
| 3 | PASS | Existing immutable receipt and checked internal Entry. |
| 4 | PASS | Current technical SLF4J convention without new Lombok and same named factory. |
| 5 | PASS | Existing JDK/Spring/Micrometer/SLF4J only. |
| 6 | N/A | No JSON or public API change. |
| 7 | PASS | Exact default false; current prerequisites unchanged. |
| 9 | PASS | Existing transaction callback and optional observer, no new runtime pattern. |
| 10 | PASS | Typed DB Instant/Duration and exact boundary checks. |
| 11 | PASS | Existing owner/source integration, no persistence/schema/framework changes. |

This records committed receipt-to-decision latency, not current backlog age or an end-to-end SLA. Production thresholds, actual metrics, representative queue index/cost evidence and S12 remain separate. No application, exporter, listener or database was started.
