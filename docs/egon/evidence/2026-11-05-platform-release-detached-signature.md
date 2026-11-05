# Detached release candidate signature verification — Source75

Effective [Spec](../spec/2026-11-05-17-30-platform-release-detached-signature.md) and [Plan](../plan/2026-11-05-17-44-platform-release-detached-signature.md); clean baseline `17fee280792e95da938c68ba65a783b965ca63d8`.

The explicit PS offline subcommand verifies a separately pinned A1 candidate and a separately pinned public trust file. The detached Ed25519 claims bind both the semantic candidate digest and exact raw envelope bytes, source revision, tenant/channel, key, role/purpose and one UTC validity window. It returns `ARTIFACT_SIGNATURE_VERIFIED_OFFLINE` only after all checks. A1 `verify` behavior, JSON and digest remain unchanged.

Local preparation reproduced JDK 25 accepting a forged signature for the identity-point public key. The admitted-key boundary now rejects the five canonical low-order y-coordinates (both sign encodings) before relying on JDK Ed25519 verification. Tests preserve that negative and ordinary JDK-generated-key acceptance. See [RFC 8032 §5.1.7](https://www.rfc-editor.org/rfc/rfc8032.html#section-5.1.7) and [libsodium's verification guard](https://github.com/jedisct1/libsodium/blob/master/src/libsodium/crypto_sign/ed25519/ref10/open.c).

| Gate | Actual result |
| --- | --- |
| G0 existing A1 | 6 passed in the prior A1 suite. |
| R1/R2 old CLI | One expected assertion failure each for the old command's exit 64, zero errors/skips. |
| G1 exact finite | A2 4 + A1 6 = 10 passed, zero failures/errors/skips, 18:08:03 +08:00. |
| P1 clean profile and package | 22 passed in 3 named PS classes, zero failures/errors/skips, 18:11:47 +08:00. Real packaged offline subcommand exited 0, 65, 65 on expected proof, wrong trust pin and corrupted detached signature respectively. |

The actual PS exec archive SHA-256 is `657c3a7213e5d6d55c7e26a7a8a94b4396b0dd71ab95c348b4c2308310e8564b`. Original production Start-Class and root permission manifest remain present; raw manifest SHA-256 `971ef0804070388fc3c7aa4be0e7cb71fd7b63ab014992b5a305231c883188e9`. The packaged proof matched the exact expected public fixture bytes (proof SHA-256 `d694f50e1dfd994f1b156fdbb524309db073020db573958208959019af2dbc2f`), while both declines returned fixed stderr and empty stdout. Test keys were synthetic and ephemeral in memory; no private key was written or retained. Frozen A1 fixture artifact digests are distinct from the A2 verifier archive digest. Logs and extracted bounded package proof under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/`.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS release package, explicit offline main only. |
| MC-REUSE-001 | PASS | Shared exact A1 reader and canonical digest; JDK verifier, existing package tooling. |
| MC-DEP-001 | PASS | Existing JDK/Jackson/PS dependencies, no POM change. |
| MC-NAME-001 | PASS | Explicit signature verifier, trust/claims and safe proof vocabulary. |
| MC-VALID-001 | PASS | Independent digest pins, canonical strict scope/time/key and actual negative fixtures. |
| MC-MODEL-001 | PASS | Closed immutable values and exact output fields. |
| MC-CONVERT-001 | N/A | No entity or persistence mapper. |
| MC-LOG-001 | N/A | Fixed stderr only; no private data or log path. |
| MC-BEAN-001 | N/A | No application context or managed Bean. |
| MC-UTIL-001 | PASS | Current JDK crypto/file/time and A1 JSON helpers. |
| MC-JSON-001 | PASS | Exact canonical envelope/claims, independent byte proof and old A1 parity. |
| MC-TIME-001 | PASS | One UTC Clock sample and half-open nested windows. |
| MC-CONFIG-001 | N/A | No application property change, explicit offline argv only. |
| MC-PATTERN-001 | PASS | Thin offline adapter and direct independent verification. |
| MC-SCOPE-001 | PASS | Exact seven paths, 27 immutable SQL files unchanged. |
| MC-TEST-001 | PASS | Actual old/RED/finite and packaged positive/negative outcomes. |
| MC-BLOCKER-001 | PASS | Offline source verified; real signer/owner authority and activation remain pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Semantic release/verification and immutable proof names. |
| 2 | PASS | Exact bound trust, scope, time and signature validation. |
| 3 | PASS | Immutable input and result values, no entity. |
| 4 | N/A | No business logging or injection. |
| 5 | PASS | Existing JDK and Jackson; no new dependencies. |
| 6 | PASS | Exact canonical JSON, named fixed statuses. |
| 7 | N/A | Existing application profiles unchanged. |
| 9 | PASS | Direct fixed offline verification, no second runtime. |
| 10 | PASS | Typed UTC Instant with equality-at-expiry rejection. |
| 11 | PASS | Existing pure PS scope; no schema, execution owner or Event changes. |

No real signer, current owner/revocation, IAM grant, deployment, application, PostgreSQL, listener or activation was used. A2 does not establish release publication or operational readiness.
