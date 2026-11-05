# Platform release Process snapshot comparison evidence

Service baseline 8de3e76184d4812ed2b1bac982b7c6889e4c49c0. [Spec](../spec/2026-11-05-21-50-platform-release-process-snapshot.md) and [Plan](../plan/2026-11-05-21-51-platform-release-process-snapshot.md) pass strict validation.

The explicit offline command authenticates the existing A2 candidate first, then checks a separate operator-pinned Process release DTO byte stream. It compares release version, manifest SHA-256, the complete model identity/content digest set and the effective input maps. The success status is PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE. The proof retains pending signer authority, owner release evidence, revocation and activation. A supplied snapshot is not a substitute for those checks.

| Gate | Actual observation |
| --- | --- |
| G0 | 10 passing existing A1/A2 tests. |
| R1 | One assertion failed as expected, old exit 64 versus required 0. No errors or skips. |
| G1 | 14 passing A1/A2 and snapshot tests. |
| P1 | Clean offline permission-producer build, 26 passing tests. Actual exec JAR offline exits 0, 0, 65, 65, 65, 65, 66, 64, 0. |
| S1 | Six exact paths, strict documents, excluded tracked bytes and all 27 main SQL files preserved. |

The actual exec archive SHA-256 is 13cd0b9baef4a197bcdb014271b8ebd556901d849022586f4001090a0ff55a3a. The same pinned snapshot replayed the same proof bytes with SHA-256 11ba30acfa267bed3aef4472ceaca9c467a3139802511b12dc0edc47a6836551. Original A2 proof bytes retained SHA-256 d694f50e1dfd994f1b156fdbb524309db073020db573958208959019af2dbc2f. Compared with the last verified PS root PaC, only expected build ID and commit provenance changed. The retained raw digest was 7dff4a536948c260304fb92552391acf6cc1b74d658bea3e88f137f1ec62ce90 and the new raw digest is 9ae1522bfc8b20632f3eaa8653bec2445dfaf4cb4ca9ab26f04d9c5a55a745d9. The boot entrypoint is unchanged. The packaged proof command used Python 3.14 for hashlib.file_digest.

Manual checks and applicable rules cover current archive/CLI reuse, exact pinned input validation, strict DTO and JSON mapping, UTC parsing, sanitized exit/error paths, actual package execution and staged scope. No new dependency, persisted state, permission, profile or lifecycle transition. Packaged verification executed only the alternate offline CLI. Actual source owner trust, live publication, revocation and formal review/activation remain pending.
