# S02 continuous delta deadline evidence

Service baseline `539cd2b58027299a0693170f42404d17f9b23b8b`. [Spec](../spec/2026-11-06-02-03-s02-continuous-deadline.md) and [Plan](../plan/2026-11-06-02-04-s02-continuous-deadline.md).

The isolated PostgreSQL operator fixture uses the product CONFIG executor, one nonfinal Fake stream, a five-second total deadline and a two-second idle window. The test requires a durable delta within the final idle window, one cancelled subscription, the original deadline, the hard-timeout result and a terminal status as the final durable event. Repeating execution must retain the same terminal result and event history.

| Gate | Observed result |
| --- | --- |
| G0 | The operator class and exact loopback fixture condition already exist. The new method was absent at baseline. |
| G1 | Clean JDK 25 source reactor passed 15 finite tests in three selected suites, zero failures/errors/skips. |
| G2 | Actual compiled class contains the exact S02 method and original `tianji.postgres.test.url` condition. The PostgreSQL suite produced no report. |
| S1 | Strict Spec and Plan passed. One test source and three documents form the scope. All 28 existing migrations match the baseline byte for byte. |

The exact operator selector in Plan §8 requires a fresh isolated PostgreSQL database. The actual timed database run and end-to-end S02 acceptance remain pending.
