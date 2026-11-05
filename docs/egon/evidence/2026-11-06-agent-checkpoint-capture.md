# Agent first durable input checkpoint evidence

Service baseline `f262564e5cc575de25d301bd6dd452fbd00d3434`. [Spec](../spec/2026-11-06-01-42-agent-checkpoint-capture.md) and [Plan](../plan/2026-11-06-01-43-agent-checkpoint-capture.md).

The optional writer records the verified first plain USER input for a tool-free CONFIG leaf in an empty durable Session. It compares the original and decoded native event with the exact input handed to the Runner. It writes the checkpoint between the native event and Session update and the final Run/Session fence check in the existing transaction. A busy shared-state lock or an ineligible run follows ordinary Session append. An admitted insert failure aborts the transaction. The stored row contains only bound identifiers, digests and state watermarks.

| Gate | Observed result |
| --- | --- |
| R1 | One expected mismatch assertion from the typed predecessor, zero errors or skips. |
| G1 | 25 tests across five finite suites passed, including both event codec paths, commit order, rollback, disabled mode and busy scope. The original eight public constructors still pass the existing assembly test. |
| P1 | Clean permission-producer build repeated all 25 tests. The AS archive contains the explicit false property, exact nested V16 and the actual ADK 1.13.0 engine marker verified by class-only load. AS and PS permission content match the prior artifacts after removing build identity fields. The exact PostgreSQL fault fixture compiled and produced no integration report. |
| S1 | Strict Spec and Plan passed, ten exact paths, all 28 migration files match the baseline. |

AS archive SHA-256 `15ed60ef151f3efd97a43c744a1213fdf07829d2f78399e763109665bfa60bfa` and manifest SHA-256 `231c223ba24e231726a86dc160e1c27c2a6ed1c10eee84b49a2f97cf9bff7ce7`. PS archive SHA-256 `98f07a1d29a86429d35b46357896f6e61b418d902e98d1cf57238ce201340c66` and manifest SHA-256 `8af7837671dad9677f46aa9e9a98cd9982be586cf94b5b6343c2dad031c3cfcf`.

The operator selector in Plan §8 exercises both codecs and actual checkpoint INSERT denial in an isolated PostgreSQL database. Real migration, grants, transaction and forced restart evidence are pending. The writer establishes a historical boundary. Product native continuation, expired Run takeover and full S11 require separate source work and operator evidence.
