# PostgreSQL checkpoint takeover source evidence

Service baseline `41050e26f35af6b2ca98d33c6a63e26b9df387d2`. [Spec](../spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md) and [Plan](../plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md).

The isolated operator fixture uses the existing product checkpoint fork and reconciler. It expires the original Session lease with an exact fixture CAS, then calls the actual takeover service with a fixture permit and a distinct scoped login. Both codecs cover owner and revision rejection, expired-permit rollback, new Run and Session epochs under the same attempt, ordered status events, and replay of the persisted acknowledgement. The fixture's one-column Session UPDATE grant supports the PostgreSQL row lock. [PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)

| Gate | Observed result |
| --- | --- |
| G0 | The exact new operator method was absent at baseline. |
| G1 | Clean JDK 25 reactor passed 22 finite tests across takeover, observation and the pipe protocol. |
| G2 | Actual compiled fixture contains the exact operator method, both parent switches, the separate role property and the private child entry. No PostgreSQL test report was produced. |
| S1 | Strict Spec and Plan pass. One existing test source and three documents form the scope. All 28 existing migration files match the baseline byte for byte. |

The exact I1 selector and role setup are in Plan §8. Actual PostgreSQL, process exit, permissions, transaction rollback and replay remain operator acceptance. Current production authority and native execution after the claim are independent steps.
