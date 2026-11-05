# Guarded checkpoint executor source evidence

Service baseline `b64f3aae86082ac4095b65f0aefad493c6f344fd`. [Spec](../spec/2026-11-06-03-47-agent-checkpoint-guarded-executor.md) and [Plan](../plan/2026-11-06-03-48-agent-checkpoint-guarded-executor.md).

The internal executor consumes a winning handle once, using the exact issuing primary and RESUME authority. It rechecks the claimed Run, loads the guarded original Session, and continues the same native invocation. Each model boundary requires both current RESUME and MODEL decisions and the exact native user, Session and invocation. The existing reservation, settlement, projection and terminal handling are shared. Resume control observes the original fixed lease cap and cancels the native model without renewal.

| Gate | Observed result |
| --- | --- |
| R1 | Omitting only MODEL authorization led to one expected `SUSPENDED` versus `COMPLETED` assertion on the actual native Runner, zero errors or skips. |
| G1 | 80 finite tests across six suites passed. Actual Runner/Fake controls include both authority denials, one native continuation, ACK-only replay, fixed expiry and periodic cancellation without renewal. |
| P1 | Clean permission-producer build repeated all 80 tests. Actual AS contains the new guarded AR methods. AS/PS YAML and semantic permission manifests match the baseline. The only production source reference to the resume entry is its declaration. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan pass, eight exact paths and all 28 migration files byte-identical. |

AS archive SHA-256 `0a0b3291a866bc32d39bd774efdec2afd6b6695ccb257b877a5f8c1f22cf0316`, manifest SHA-256 `d24052baef88ef56f796e259393433a34887741a29c1019000ee13de463d4b3f`. PS archive SHA-256 `5941d123724b00c5cec5c22c4c8abc8b196c052fc0935ea116b149736349cee9`, manifest SHA-256 `fef0af5bd577641a6637942af7b2516723dc53a993a6b7713043b5023a14c699`.

The current-authority host adapter, operator PostgreSQL execution and end-to-end S11 remain independent gates.
