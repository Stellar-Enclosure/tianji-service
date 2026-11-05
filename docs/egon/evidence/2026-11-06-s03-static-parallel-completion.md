# S03 static parallel completion evidence

Service baseline `816b159a41b6b1312b2861c73a39088d5c2bd508`. [Spec](../spec/2026-11-06-03-41-s03-static-parallel-completion.md) and [Plan](../plan/2026-11-06-03-42-s03-static-parallel-completion.md).

The executor checks required outputs after a static tool-free parallel tree completes. Each static parallel branch needs its terminal output. Within a sequential branch, the last child supplies the branch output, while earlier nested parallel groups still require their own branches. Dynamic delegation, callbacks, native gates and governed tool scopes retain their existing completion rules.

| Gate | Observed result |
| --- | --- |
| R1 | The old executor completed a nested static run with a missing required branch, producing exactly one expected FAILED versus COMPLETED assertion. |
| G1 | 60 finite tests across three suites passed, including missing output, nested branches, allowed missing intermediate output and delegated controls. |
| P1 | Clean permission-producer reactor repeated all 60 tests. Actual nested AR contains the guard. Both AS/PS configurations and semantic PaC match baseline. |
| S1 | Strict Spec and Plan pass, five exact paths and all 28 existing migration files byte-identical. |

AS archive SHA-256 `506c23be5e784824455a8f3772b6aa9406af950d9ca36b5e51a885562f44847a`, manifest SHA-256 `254dc5aff3c6f735f164362ec765d06b3494dade07ff3894f2e8a34b4e091b2f`. PS archive SHA-256 `eac4ea8ce030a63b4d3808efbdeaedc8236d7b2323eeb681ac9af811b1463be8`, manifest SHA-256 `49a318f6bbc96439b1275d0cb2d869dd413125c3a505de26f009555cfdd8655d`.

The tests use the real native Runner with Fake model and ledger facts. The PostgreSQL branch cancellation and other integration scenarios remain separate acceptance.
