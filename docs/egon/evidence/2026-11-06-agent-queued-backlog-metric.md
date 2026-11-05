# Bounded Agent pending queue gauge evidence

Source baseline `9a509342767d6b01dfa94e482b530cc69ccc5536`. [Spec](../spec/2026-11-06-00-24-agent-queued-backlog-metric.md) and [Plan](../plan/2026-11-06-00-25-agent-queued-backlog-metric.md).

After successful dispatch discovery the optional sampler reads the unexpired, uncancelled QUEUED population from the owning primary. The sample caps at 257, which represents at least 257 runs. The untagged Gauge reads the cache and reports NaN locally if the sample is absent, expired or invalidated.

| Gate | Observed result |
| --- | --- |
| R1 | Actual JDK 25 reactor produced exactly one assertion failure for 257 versus 0, with zero errors and skips. |
| G1 | 22 tests passed across AR reader (3), AS dispatch (11) and adjacent AS storage (8), with zero errors and skips. |
| P1 | Clean permission-producer reactor repeated all 22 passing tests. The actual AS YAML matched the source byte for byte, with the new property false. AS and PS manifest contents matched the preceding artifacts after removing only build identity fields. |
| S1 | Strict Spec and Plan, eleven exact paths, all 27 existing migration files byte-identical, with no PS or POM source diff. |

AS archive SHA-256 `70fb7f594bf5d332ff24029ce11e16611e42c5a06b702705f0baa3cd4990e73f` and manifest SHA-256 `c691eab03c1c8a720eebcc52046c90d17d9122d73da9e2c0e6a9091bf844c337`. Actual AS YAML SHA-256 `ecfac425a10d8ab30326f520c11c54c059c74c21525516981415bdb4dae873dc`. PS archive SHA-256 `a7e975e13f35e487b3682f6ffc2d87d3fda709f586e9c90fbe9f8821442a23c4` and manifest SHA-256 `68808bed57501534a85fd36cd3402f3433df8703e259f4753173d20349665e46`.

The bounded primary transaction, cache expiry and failure isolation were verified with finite fixtures. Real PostgreSQL query plans and pool behavior, exporter semantics across replicas, alert thresholds and S12 remain operator acceptance items.
