# PostgreSQL checkpoint resume executor source evidence

Service baseline `7b8faefa38cd95e7807c4b92e0077991800b77c7`. [Spec](../spec/2026-11-06-03-55-agent-checkpoint-resume-executor-postgres.md) and [Plan](../plan/2026-11-06-03-56-agent-checkpoint-resume-executor-postgres.md).

The separate operator branch uses the product fork, reconciler, actual takeover transaction and real guarded executor on one exact fixture role. Both codec paths check the original invocation and raw input prefix, current fixture RESUME and MODEL decisions, one Fake call, one settled MODEL reservation, and the final result on the same Run and attempt. Both consumed-handle and ACK-only replay preserve native and product history, budget and receipt.

| Gate | Observed result |
| --- | --- |
| G0 | The exact operator method is absent at baseline. |
| G1 | Clean JDK 25 reactor passed 28 finite tests across four suites, zero failures, errors or skips. |
| G2 | Actual class contains the precise method, both parent flags, explicit resume execution role property and the private child switch. No PostgreSQL fixture report was produced. |
| S1 | Strict Spec and Plan pass, one existing test source plus three documents. All 28 existing migration files match the baseline byte for byte. |

The exact operator method, isolated role, URL and fixture limits are in Plan §8. Actual PostgreSQL role checks, process exit, native continuation, MODEL settlement and replay remain operator acceptance. The fixture's two local decisions exercise the internal contracts; production IAM remains a separate gate.
