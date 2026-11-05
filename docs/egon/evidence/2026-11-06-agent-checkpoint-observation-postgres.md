# PostgreSQL checkpoint reader source evidence

Service baseline `49fb8d481435ca4b4b5d58291ffd9650ed0935f5`. [Spec](../spec/2026-11-06-02-28-agent-checkpoint-observation-postgres.md) and [Plan](../plan/2026-11-06-02-29-agent-checkpoint-observation-postgres.md).

The new isolated operator profile uses a distinct V16 SELECT-only login after the existing product process-death and reconciliation path. It expires the original Session lease with a guarded fixture update and checks the actual checkpoint observer. Both codec paths include the exact owner, same-tenant different-owner refusal, revoked V13 column permission, restoration, and Session revision drift and restoration.

| Gate | Observed result |
| --- | --- |
| G0 | The exact new operator method is absent at baseline. The original capture and product fork methods already exist. |
| G1 | Clean JDK 25 reactor passed 17 finite tests across the observation, storage and pipe protocol suites, zero failures/errors/skips. |
| G2 | Actual compiled fixture contains the original fork and new observer method, class and method opt-ins and reader-role property. The PostgreSQL fixture produced no report. |
| S1 | Strict Spec and Plan passed. One test source and three documents form the scope. All 28 existing migrations match baseline byte for byte. |

The operator selector in Plan §8 uses a new disposable primary and independent setup, writer and reader identities. Actual roles, query semantics, forced exit and integration results remain pending. The test makes no native continuation or Run takeover claim.
