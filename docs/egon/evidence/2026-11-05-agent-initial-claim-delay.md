# Committed Agent initial claim timing evidence

Service baseline 410874c58d88b766e27706d80e639ebf2d734f4d. [Spec](../spec/2026-11-05-23-44-agent-initial-claim-delay.md) and [Plan](../plan/2026-11-05-23-50-agent-initial-claim-delay.md) pass strict validation.

The optional untagged Timer measures the initial successful queued-to-claimed wait from the existing persisted creation time and claim event time. Both are Agent database instants. It records only after the winning first claim commits. Later attempts, failed or rolled back claims and invalid durations produce no sample. It adds no storage query or migration.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure. Baseline did not expose the exact observation factory. |
| R2 | One expected assertion failure. Typed A committed without the expected measurement. |
| G1 | 21 passing tests across the initial-claim, usage and recovery suites. Zero failures, errors or skips. |
| P1 | Clean offline permission-producer verify repeated 21 passing tests. Actual AS and PS permission content match the baseline apart from build provenance. Packaged AS YAML matches the source and adds exactly one false observation flag. |
| S1 | Strict docs, exact eight paths, excluded tracked bytes and all 27 existing migrations checked. |

AS archive SHA-256 9b4e5b6771e30b17e07d520034817e7846618f328338a0cdccc61f451c6f19fc, raw PaC SHA-256 db5bfa6608662d69c14386358e978a630acd0cfde6f1a89bacea6924e909629b and YAML SHA-256 d1147b2d3d4fe693fc0379f6e17f7d9aefe81d47299fd4dc73c45cd243051464. The existing storage/model usage and recovery defaults remain intact. PS YAML matches baseline.

Manual and applicable literal checks cover the existing atomic claim and clock reuse, post-commit observation, bounded metric naming and no tags, configuration gate, exact build and scope. This samples committed first claims. Queue depth, live exporter, production SLA thresholds and S12 remain separately gated.
