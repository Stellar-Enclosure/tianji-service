# Durable ADK Session boundary probe — Source86

Effective [Spec](../spec/2026-11-05-19-58-adk-durable-resume-probe.md) and [Plan](../plan/2026-11-05-20-01-adk-durable-resume-probe.md), Service baseline `5b764dfcf867e0703fd76defcedc276c57bfd3f2`. Both strict validators passed. The test source SHA-256 is `b3be09625bd12ebfc264db90feb12416a9f56a286c064c6812b97c1982e9c7cd`.

| Gate | Actual result |
| --- | --- |
| G1 | Clean offline JDK 25 source reactor passed, 20:08:51 +08:00. The new class compiled. The three selected existing suites passed: native resume 3, recovery disposition 5 and Session codec 6, zero failures/errors/skips. |
| G2 | Actual compiled test bytecode contains the three exact methods, class-level probe opt-in and dedicated disposable URL guard; generic Postgres property absent. No Surefire execution report for the new class. |
| S1 | Exact four paths, strict effective docs, all excluded tracked paths and all 27 existing main SQL bytes checked. |

The three new methods are **not executed**. They ask whether a pre-model cut with no tool, or a previously saved local read response, can cross a new PostgreSQL Session lease while the original Run lease remains live. They also check that an expired Run cannot be reclaimed by a replacement Run owner. The positive candidates cover the existing Session codec v1/v2 modes, exact same invocation and no repeated local read. A positive Session result would not establish a durable product checkpoint, a replacement Run lease or a product terminal result.

The Plan §8 contains the later operator-only command. It requires both the dedicated opt-in and a fresh isolated loopback database with the exact probe prefix and no Tianji schema. The probe alone provisions the existing Agent V1–V9 chain in that disposable database; there is no new SQL or change to existing scripts. No schema was touched in this delivery. No application, database, listener or external provider ran.

Manual and literal gate review: exact test owner and current JUnit/ADK/ledger/codec/Flyway reuse; bounded fresh-DB and UTC/lease fences; no production model, JSON, logging, bean, config, authority, dependency or migration change. The source gate uses only the three existing finite selectors, so a compiled or skipped probe cannot count as integration acceptance. Raw XML, bytecode and source logs are retained under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/checkpoint-proof/`.

S11 and product Run checkpoint, replacement and reconciliation remain separately open.
