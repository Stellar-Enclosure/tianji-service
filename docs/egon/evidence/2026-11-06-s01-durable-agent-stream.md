# S01 durable HTTP stream source evidence

Service baseline `e5f6df84372774bac48bc7c6649fd56f0ed31520`. [Spec](../spec/2026-11-06-01-11-s01-durable-agent-stream.md) and [Plan](../plan/2026-11-06-01-12-s01-durable-agent-stream.md).

The existing operator PostgreSQL fixture now has one S01 scenario. Its single MockMvc POST creates a queued Run. The real local dispatcher and ADK Runner use a two-chunk Fake. The scenario checks the running state with no result, exact ordered SSE replay after the first delta, the final HTTP result and one Fake subscription. One owner-bound PostgreSQL query requires the terminal event and final Run row to share the same MVCC transaction ID and verifies the original idempotency key and stored result text.

| Gate | Observed result |
| --- | --- |
| G0 | The original test class and loopback opt-in exist. The exact S01 method is absent at the baseline. |
| G1 | Clean JDK 25 reactor passed 28 finite tests across four suites. An isolated finite run of the actual Fake through the ADK Runner and projector produced one subscription, the two exact deltas and one aggregated output. |
| G2 | The actual compiled class contains the exact new method and the original `tianji.http.postgres.test.url` condition. No PostgreSQL fixture report was produced. |
| S1 | Strict Spec and Plan pass. One test source and three documents are the exact scope. All 27 production migrations match baseline byte for byte. |

The operator can run the exact I1 selector in Plan §8 on a fresh isolated loopback PostgreSQL primary. This source evidence does not establish the actual PostgreSQL transaction witness, the production grant poller or deployed IAM.
