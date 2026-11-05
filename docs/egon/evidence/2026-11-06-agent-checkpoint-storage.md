# Agent checkpoint storage contract evidence

Service baseline `98ccd8fa189aecf54641d8e55af5a7a02060610d`. [Spec](../spec/2026-11-06-01-25-agent-checkpoint-storage.md) and [Plan](../plan/2026-11-06-01-26-agent-checkpoint-storage.md).

V16 adds a typed checkpoint table for the first durable input boundary. It binds the owner, Run and Session fences, native event, definition and request digests and the engine version. The two cascade references follow Run and native Session event deletion. The explicit readiness check verifies the primary catalog and an INSERT-only role. It checks the triggers on both sides of the foreign keys by constraint OID. [PostgreSQL trigger catalog](https://www.postgresql.org/docs/18/catalog-pg-trigger.html)

| Gate | Observed result |
| --- | --- |
| R1 | Base-only typed predecessor produced one intended missing-history assertion failure, zero errors or skips. |
| G1 | Five checkpoint and four V15 regression tests passed with no errors or skips. This includes simulated catalog drift and role refusal. |
| P1 | Clean permission-producer build repeated all nine tests. V16 was byte-identical in AR output, its archive and the AR archive nested in AS. PS contained no V16. Both actual configurations and permission manifest content matched the baseline. |
| S1 | Strict Spec and Plan passed, six exact paths. All 27 earlier migration files were byte-identical. The production source has one declaration and zero calls to the readiness method. |

V16 is 4053 bytes, SHA-256 `8521cd49e7ae4401f7dd9847b27918eeeca26b2ef926aa3d43821d900ed2ca36`, Flyway checksum `-415542202`. AR archive SHA-256 `cc6a019e43a3fe07526e7e76fc000df30fe5b6367fd9cd1ba2a8b23013c2f898`. AS archive SHA-256 `245d1e8fb911a19674b2142e317f34a65a0bafebcdf9a99c11604f2b68b37506` and PS archive SHA-256 `4aad54708a1bb035cb0ed00b7d62e57b8f2d010dfd9b5c99a198c957de7f90fa`.

V16 application, actual database permissions and cascade behavior require an isolated operator database. The Session transaction writer, default-off wiring and process-death probe are subsequent implementation and acceptance steps.
