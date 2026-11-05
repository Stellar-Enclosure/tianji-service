# ADK durable process death probe evidence

Source baseline `8fa8474a2880242c7ef6824a285eea15a0adca3f`. [Spec](../spec/2026-11-06-00-31-adk-durable-resume-fork.md) and [Plan](../plan/2026-11-06-00-32-adk-durable-resume-fork.md).

The operator probe uses two independently launched test JVMs. Its parent kills the original JVM at a committed pre-model cut. The replacement receives the original Run lease and native invocation over a bounded stdin frame, then attempts native continuation under a new Session lease. Both fixture codec modes use the original input history and verify its durable byte prefix. The probe's scope is Session continuation during a valid original Run lease.

| Gate | Observed result |
| --- | --- |
| R1 | Actual reactor returned exactly one wrapped ClassNotFoundException assertion failure with zero errors and skips. |
| G1 | Clean JDK 25 source reactor passed 4 pipe-protocol, 3 native in-memory resume and 5 recovery evidence tests with zero failures, errors or skips. |
| G2 | The actual compiled PostgreSQL fixture contains the fourth test method, both opt-in flags, the private child flag and child main. The PostgreSQL fixture produced no Surefire report. |
| S1 | Strict Spec and Plan pass. The six paths include three test source files and three documents. All 27 existing migration files match the baseline byte for byte. |

The operator procedure in Plan §8 requires a fresh, authorized, disposable loopback PostgreSQL primary. This source gate launched neither the database fixture nor either child JVM. Real process death, lease replacement and continuation, product Run takeover, supported checkpoint modes and full S11 remain separate evidence.
