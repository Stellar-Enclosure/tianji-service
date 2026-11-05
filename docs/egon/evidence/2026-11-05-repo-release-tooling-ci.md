# Repo-only release tooling CI evidence

Service baseline 75d0c15a2ff9bef03be80e7cd7584b2bcbe8401e. [Spec](../spec/2026-11-05-23-09-repo-release-tooling-ci.md) and [Plan](../plan/2026-11-05-23-10-repo-release-tooling-ci.md) pass strict validation. Workflow SHA-256 36d138d91a5eec19a8bc896b4fe01c15228affbfbdb0afd0d3a3bf4733d88bbc.

The workflow is manual and limited to the exact Tianji Service commit in the current GitHub context. A job-issued contents-read credential is confined to the one own-repository fetch with redirects disabled. The workflow checks the commit, archives five allowlisted regular Git blobs and rehashes the extracted bytes. Its test step uses an empty environment and checks the exact 7 plus 6 suite counts. Empty, skipped or unsupported test runs fail.

| Gate | Actual observation |
| --- | --- |
| R1 | The exact baseline has no release-tooling workflow path. |
| G1 | Ruby Psych and Bash syntax passed. Five bad context fixtures rejected before transport. A local fetch fixture verified the exact Git arguments and transient authentication boundary. |
| G1 archive | Actual local Git archive and blob-byte checks passed for all five allowlisted files. Python 3.14 ran all 13 tests. The local 3.9 interpreter, empty suite and skipped case failed as expected. |
| S1 | Strict documents and exact four paths. Existing tooling and 27 main migration files unchanged. |

The local proof used Python 3.14 so its positive runner met the workflow minimum. The default local Python 3.9 attempt stopped at the interpreter gate and was retained as negative evidence. Manual checks cover immutable Git scope, sanitized credential handling, isolated subprocess execution and exact local verification. The hosted runner, real HTTPS transport, full Java U gate, integration and S12 remain pending.
