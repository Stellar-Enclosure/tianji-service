# S12 receive-link replay source evidence

Service baseline `c07b8eddbb8459c04c7fd26953933a02ef0b347c`. [Spec](../spec/2026-11-06-02-30-s12-receive-link-replay.md) and [Plan](../plan/2026-11-06-02-31-s12-receive-link-replay.md).

The isolated replay mode composes a committed result response loss, the original event fingerprint and the replacement AS attempt with one native Process advance. Only the two exact test relay RESULT phases accept the private diagnostic carrier. The collector predicate joins two distinct PS Receive Links through their original and replacement deliveries to the same durable enqueue. The replacement delivery must start at or after the replacement JVM start instant.

| Gate | Observed result |
| --- | --- |
| R1 | The actual collector stub caused exactly one intended positive-case assertion failure, with zero errors or skips. |
| G1 | 19 finite tests across three suites passed, including the existing fixture modes, replay relay admission and collector negatives. |
| P1 | Clean permission-producer build repeated all 19 tests. Actual AS/PS executables exclude fixture and collector resources. Both production YAML and manifest contents match the previous artifacts after excluding build identity fields. The ordinary and replay E classes compiled; no E report was produced. |
| S1 | Strict Spec and Plan pass. Exactly eight PS test sources and three documents. All 28 production migrations match baseline byte for byte. |

AS archive SHA-256 `d279282d480c0b287f553cf3658478e62f17398eb912e47d3b306b92186a12e8`, manifest SHA-256 `7f9b7f21530f2e354ef95c0f29b8d4dc1c94226cb573c2d7591fad52ebdbbdfb`. PS archive SHA-256 `4a677c9f62f9a8ae3c0151afa0a146591e3842b933ec28ca3c6553895519eddf`, manifest SHA-256 `e810792b03e723a05bd781c7017a50d27326025f6643aeb119ce1ba97a6b5e9a`.

The exact E1 selector, separate manifest and Collector locks are in Plan §8. Actual HTTPS, the dropped committed response, replacement AS and raw Collector replay evidence remain operator acceptance. This scope is one replay path within S12.
