# Product checkpoint process-death source evidence

Service baseline `7013768227b778ce762ca18f72c4cb24b5facd3f`. [Spec](../spec/2026-11-06-02-11-agent-checkpoint-product-fork.md) and [Plan](../plan/2026-11-06-02-12-agent-checkpoint-product-fork.md).

The new operator-only fixture uses the product executor and V16 writer. A first test JVM parks before budget reservation after the checkpoint and first native Session event have committed. The parent observes the same checkpoint digest, forcibly ends and reaps that JVM, and expires only the original Run lease in the disposable database. A second JVM observes the same evidence and uses the production reconciler to verify exactly one `SUSPENDED / LEASE_EXPIRED` transition. The test checks the old epoch fence, retained Session history and absence of model, budget, tool and terminal-result evidence. Both existing codec modes are included.

| Gate | Observed result |
| --- | --- |
| R1 | The actual reactor reports one intended missing-class assertion, zero errors and skips. |
| G1 | Clean JDK 25 reactor passed 18 finite tests across both pipe protocols, checkpoint capture and V16 storage. |
| G2 | Actual compiled fixture contains the existing fault case, the new process-death method, both opt-ins and the private child main. No PostgreSQL fixture report was produced. |
| S1 | Strict Spec and Plan pass. Exactly three test sources and three documents. All 28 existing migrations match the baseline byte for byte. |

The exact I1 operator run uses a fresh isolated loopback database and the two explicit parent switches. Actual process exit, durable checkpoint survival and reconciliation remain unverified until that run. Native continuation, product Run takeover and complete S11 require separate acceptance.
