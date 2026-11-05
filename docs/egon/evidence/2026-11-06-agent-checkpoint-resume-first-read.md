# Checkpoint-bound first Session view evidence

Service baseline `76fd92c6fb855a540d9711e180062360501e3cb1`. [Spec](../spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md) and [Plan](../plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md).

The winning claim binds the exact primary, owner and successor Run and Session leases. The first complete Session read verifies the recorded V16 fingerprint against the original raw Session and event data while holding the existing Run, Session, lease and shared-state locks. It returns that same checked view to the SDK. Access to subsequent append and read operations is opened only after the transaction returns successfully. The resumed Session retains its fixed lease cap.

| Gate | Observed result |
| --- | --- |
| R1 | Omitting raw digest comparison produced exactly one expected missing-exception assertion for a changed raw event under the same identifiers, zero errors or skips. |
| G1 | 29 finite tests across four suites passed, including both codecs, owner and lease binding, blocked early access, drift and rollback. |
| P1 | Clean permission-producer build repeated all 29 tests. Actual AS contains the scoped AR path; both configurations and semantic permission manifests match the baseline. No PostgreSQL report was produced. |
| S1 | Strict Spec and Plan pass. Eight exact paths and all 28 existing migrations byte-identical. |

AS archive SHA-256 `037915704514ae3b582161ac9dec0ed0d759c763c1f8840ec37fcf82e682ab79`, manifest SHA-256 `92d5a3ca3a4a642116b8cf181376798b0806da711f6c34888961a2471499aa03`. PS archive SHA-256 `5fb35694a37851190f82c9372e2c19b1128a79b0a95d0870c7fb869b794e893a`, manifest SHA-256 `37ea308e604119152c1478f261574f070d4aa5db6446a28807d4c9dbe93bcc47`.

Actual PostgreSQL locking, current authority and the product executor connection remain independent gates.
