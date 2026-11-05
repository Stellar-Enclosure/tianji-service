# V16 product-origin native continuation evidence

Service baseline `8126c32db1f303f858cbc317cf6db0db4d7ab1f1`. Components baseline `6d26bf8724e8993b631db68c95b7238d7a93fb8c`. [Spec](../spec/2026-11-06-02-56-agent-checkpoint-native-resume.md) and [Plan](../plan/2026-11-06-02-57-agent-checkpoint-native-resume.md).

The isolated operator branch uses the ordinary product Runner to save the first input and V16 checkpoint before budget reservation. After forced process death, the parent preserves the exact original live Run lease and expires only its captured Session lease. The second JVM verifies the cut, obtains a new Session lease, and uses the Components explicit bridge on the same invocation. Both codecs check the unchanged raw prefix and V16 row, one user input, one Fake call, completed repeat without further execution, and no product result, budget or tool work.

| Gate | Observed result |
| --- | --- |
| R1 | A native-continuation phase encoded as the reconciliation phase failed the exact assertion once, zero errors or skips. |
| G1 | The clean JDK 25 source reactor passed 14 finite tests across the two handoff and checkpoint capture suites. |
| G2 | Actual class contains the exact V16 operator method, both parent opt-ins and the private child entry. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan passed. Exactly three test sources and three documents. All 28 existing migration files match the baseline byte for byte. |

The exact operator selector and bounded PostgreSQL and child-process setup are in Plan §8. Actual process death and native continuation remain pending. The retained original Run lease is a condition of this experiment. Product expired-Run takeover has its own authority and persistence gates.
