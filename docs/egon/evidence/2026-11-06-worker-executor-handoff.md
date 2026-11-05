# Worker executor handoff evidence

Source baseline `4ff3f279ff302a8d7aef7c885bbadd952a908165`. [Spec](../spec/2026-11-06-00-40-worker-executor-handoff.md) and [Plan](../plan/2026-11-06-00-41-worker-executor-handoff.md).

The existing Worker poller now gives each scheduled submission one completion owner. If the executor reports rejection before a handed-off callback enters, the rejection path owns the release and a late callback cannot acquire a remote task. A callback that enters first retains the existing acquire and finally-release path.

| Gate | Observed result |
| --- | --- |
| R1 | Actual JDK 25 reactor reported one intended assertion failure, expected 0 acquisitions and observed 1, with zero errors and skips. |
| G1 | All 12 Worker poller tests and 1 dependency-boundary test passed with zero failures, errors or skips. |
| S1 | Strict Spec and Plan pass. The staged scope is two Worker Java paths and three documents. All 27 existing migrations match the baseline byte for byte. |

This is a finite host-executor fault regression. Real Worker lease, acknowledgement loss, business transaction and the S10 I/E gates remain operator acceptance items.
