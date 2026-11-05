# S03 parallel owner cancellation source evidence

Service baseline `2cfb744b9b481d8500529c4d9dd2d230dc630a0d`. [Spec](../spec/2026-11-06-04-06-s03-parallel-cancellation-postgres.md) and [Plan](../plan/2026-11-06-04-07-s03-parallel-cancellation-postgres.md).

The isolated operator test starts the real CONFIG parallel Runner with a finite planner and a blocked writer. It waits for the exact planner output in the durable event stream, verifies the parent is still running, and submits cancellation with the current owner revision. The expected observations are writer subscription disposal, retained planner output, a final CANCELLED event and result, and one SETTLED and one UNKNOWN MODEL reservation.

| Gate | Observed result |
| --- | --- |
| G0 | The existing PostgreSQL fixture is present. The exact new method is absent at baseline. |
| G1 | Clean JDK 25 reactor passed 20 finite tests across three suites, zero failures, errors or skips. |
| G2 | The actual compiled fixture contains the exact new method and original loopback URL guard. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan pass. One existing test source and three documents, with all 28 migration files byte-identical. |

The exact I1 method in Plan §8 requires a separate fresh isolated PostgreSQL database. Actual concurrent cancellation, durable event order and reservation states remain operator acceptance.
