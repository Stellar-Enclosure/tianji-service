# Fixed Agent quality evaluation evidence

Service baseline `1605f9aebf25445076d611d661b86038ea5e18d8`. [Spec](../spec/2026-11-06-01-21-fixed-agent-quality-evaluation.md) and [Plan](../plan/2026-11-06-01-22-fixed-agent-quality-evaluation.md).

The five fixed cases use the real ADK Runner, the governed fixed tool bridge and the existing budget plugin with Fake inputs. They compare the structured output, exact approved or denied tool dispatch, and each observed reservation and settlement. The fixture identifies the exact synthetic definition, inline instruction hash, model, tool and policy revisions.

| Gate | Observed result |
| --- | --- |
| R1 | The owner reactor rejected the single authorized-read mutation with exactly one expected assertion (0 versus 1 dispatch). No evaluation marker was produced. |
| G1 | 49 tests passed across the existing governed and native completion suites with zero failures, errors or skips. The six existing injection cases still ran. |
| G2 | One exact `TIANJI_AGENT_QUALITY_V1` Surefire marker matched the copied fixture SHA-256 `13859e47ba042e921db911b5e2a7caa2a1e0df9e06c16c99b55fb3374a336e19`. Each match category was 5/5, with six actual Fake model calls, six reservation calls and six matching settlement calls. |
| S1 | Strict Spec and Plan passed, five exact paths, 27 existing SQL files byte-identical. |

The matched cases include expected refusals. Reserve and settle values come from the configured Fake estimator and verify budget plugin forwarding, not live settlement. The marker records citation quality as `NOT_AVAILABLE` and real-model quality as `NOT_RUN`. Its local duration is a test observation. Real citation evaluation, provider quality, PostgreSQL accounting and environment-qualified latency remain independent acceptance items.
