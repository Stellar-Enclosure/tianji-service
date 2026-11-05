# First-input checkpoint takeover source evidence

Service baseline `9849d535afa8c0e51aa65924760355a02fe3b1e6`. [Spec](../spec/2026-11-06-03-04-agent-checkpoint-takeover.md) and [Plan](../plan/2026-11-06-03-05-agent-checkpoint-takeover.md).

The internal service requires a current permit for the exact RESUME command and cut. It obtains the permit outside the transaction. The writer takes the Run, Session, Session lease and shared scope locks, then revalidates the cut at READ COMMITTED. It keeps the same Run and attempt and atomically commits both new epochs, the two logical status events and the exact V2/V5 acknowledgement. The writer rereads the durable acknowledgement before returning a one-use internal launch capability. Replays return the original acknowledgement only.

| Gate | Observed result |
| --- | --- |
| R1 | Omitting the post-lock equality guard let a stale cut proceed and produced exactly one expected assertion, with no errors or skips. |
| G1 | 30 tests across four finite suites passed, covering permission and expiry, drift, same connection Session lease, replay and rollback controls. |
| P1 | Clean permission-producer build repeated all 30 tests. Actual AS includes the AR claim class. Both AS/PS YAML and semantic permission manifests match the baseline. No host wiring or PostgreSQL report was found. |
| S1 | Strict Spec and Plan pass, eleven exact paths. All 28 existing migration files match the baseline byte for byte. |

AS archive SHA-256 `2d25e1622aeae8edb368fc25755031fe2f267ab94948ffcc32d3b923b238c058`, manifest SHA-256 `58b460391cb868a4bf62a2453a2ebc5c721b08479ca91b3f1b5157cc589e2dc4`. PS archive SHA-256 `f46385bfc082dc55684b1aaefc6f3eb7a8671ea95dc905b16c0ec469d408daa8`, manifest SHA-256 `f9a457f2a6db1cf22e02a821accf4cb136a4e742ca9f2f581d559a57b5a28ca5`.

Actual PostgreSQL role, locking and claim evidence remains pending. The production current-authority adapter, claimed Runner execution, first Session read revalidation and full S11 remain separate implementation and acceptance steps.
