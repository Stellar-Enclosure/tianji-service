# S03 compiled Agent output keys evidence

Service baseline `3577cdd521271b65f431c2d28601175fec92c638`. [Spec](../spec/2026-11-06-02-50-s03-compiled-output-keys.md) and [Plan](../plan/2026-11-06-02-51-s03-compiled-output-keys.md).

The existing compiled name traversal now also checks exact configured output keys throughout the native tree before the Session and Runner. Repeated keys fail with `TIANJI_FEATURE_NOT_AVAILABLE`. The fixed diagnostic omits the key. Distinct keys and absent keys still allow the same parallel execution and both outputs.

| Gate | Observed result |
| --- | --- |
| R1 | The old executor completed the nested duplicate case and produced the single expected FAILED versus COMPLETED assertion. |
| G1 | 56 tests across three finite suites passed with no failures, errors or skips. The duplicate case used zero model calls and reservations and emitted no execution projection. |
| P1 | Clean permission-producer reactor repeated all 56 tests. Actual AS contains the compiled guard. Both AS/PS YAML and permission declarations match baseline after removing build identity fields. |
| S1 | Strict Spec and Plan pass, five exact paths and all 28 existing SQL files byte-identical. |

The isolated fixture runs the real native Runner with a Fake model and ledger facts. Actual PostgreSQL and the remaining S03 branch cancellation and failure matrix retain their own acceptance gates.
