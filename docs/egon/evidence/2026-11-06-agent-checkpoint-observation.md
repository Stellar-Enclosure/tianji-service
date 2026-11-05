# Owned checkpoint observation evidence

Service baseline `6fa58d2ff7dd9f0a286ca9a149f9b7ee30f8de62`. [Spec](../spec/2026-11-06-02-18-agent-checkpoint-observation.md) and [Plan](../plan/2026-11-06-02-19-agent-checkpoint-observation.md).

The internal observer accepts an independently provisioned V16 SELECT-only primary connection. It rechecks that role, uses a bounded physical read-only snapshot, and validates the full owner, original definition and execution fingerprints, exact post-expiry Run and Session watermarks, native event and empty state. Observed progress or unavailable V13 tool evidence closes the matching branch. The result contains a status only.

| Gate | Observed result |
| --- | --- |
| R1 | Removing the V13 guard produced the single intended assertion. Expected `TOOL_EVIDENCE_UNAVAILABLE` and observed `BOUNDARY_OBSERVED`, zero errors or skips. |
| G1 | 33 tests across six finite suites passed, including both codec paths, ownership, fence and budget drift, current reader role and V13 denial. |
| P1 | Clean permission-producer build repeated all 33 tests. The observer is present in AR and the AR jar nested in AS. Actual AS and PS YAML and permission content match the baseline after excluding build identity fields. No host reference or PostgreSQL report was found. |
| S1 | Strict Spec and Plan pass, seven exact paths. All 28 existing migrations are byte-identical. |

AS archive SHA-256 `40f99347d974d472b4560d7a2785307bede91c0e0b08c6a9bcfb94cb73b8451b`, manifest SHA-256 `10e4a2f20890b2a54e3e489bad6836e8799b2b4dcc5a63b32e1a979a5d8a62d3`. PS archive SHA-256 `aa211ee3757eeb0d96f206a2742cab3fda100edddbb6723d7496f5d5fc6ff849`, manifest SHA-256 `dd92fbf2ebe68116d814edb6de0725cc2684467a172afaedeefd6ee34640015e`.

A matching result is a persisted observation at the snapshot instant. Real PostgreSQL reader provisioning and query execution, current execution authority, transactional takeover, native continuation and S11 remain separate acceptance or implementation work.
