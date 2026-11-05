# S04 durable Session and Memory composition evidence

Service baseline `16f0dcd5f8e6fb9abc537e86433911a7b085e1be`. [Spec](../spec/2026-11-06-03-24-s04-session-memory-composition.md) and [Plan](../plan/2026-11-06-03-25-s04-session-memory-composition.md).

The isolated operator test composes the real CONFIG executor, durable Session, V9 source lineage, the current Memory context provider and V10 pgvector. It checks the first and replacement executor turns under local exact fixture authorization, separate tenants with the same IDs, a sibling Memory scope, and the original UPSERT and BUILD replay after forget. The old Session must fail before a Fake model call or MODEL reservation. A fresh Session and unaffected owner scopes provide positive controls.

| Gate | Observed result |
| --- | --- |
| G0 | The exact new test method is absent at baseline. The original Memory PostgreSQL fixture exists. |
| G1 | Clean JDK 25 reactor passed 37 finite tests across the three provider, context and event-codec suites, zero failures/errors/skips. |
| G2 | The actual compiled fixture contains the exact new method, the existing URL guard and new Session-context opt-in. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan pass. One existing test source and three documents form the scope. All 28 existing migrations match baseline byte for byte. |

The exact I1 selector in Plan §8 requires a fresh isolated PostgreSQL and pgvector fixture. Actual model-history admission, vector visibility and redelivery remain operator acceptance. The local fixture authorizer is separate from production IAM.
