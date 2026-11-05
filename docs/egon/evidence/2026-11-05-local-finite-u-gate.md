# Local finite U gate evidence

Service baseline 1b5700ae9ba409c948c4d1a261d7b002de2b4381. [Spec](../spec/2026-11-05-20-47-local-finite-u-gate.md) and [Plan](../plan/2026-11-05-20-50-local-finite-u-gate.md) pass strict validation.

The runner checks five exact clean source revisions, a pre-existing offline Maven Wrapper, JDK 25 and an explicit credential-free local Maven settings file. It runs the existing 17-class U allowlist. Each invocation has its own report suffix and nonce. The validator requires both, exact selectors, the exact normalized module directory and an actual successful test result for every owning report. The output contains only run identity, counts, source locks and digests. Raw reports remain in their originating Maven target directories with owner-only permissions.

| Gate | Actual observation |
| --- | --- |
| R1 | New test on A failed once as expected. The unimplemented gate returned unavailable instead of success. |
| G1 | 6 new tests passed on Python 3.9. 13 tests, including all 7 existing release-evidence tests, passed on Python 3.14. |
| G2 | Parsed 17 retained owner reports representing 220 executed tests. All fail the new run nonce requirement. |
| G3 | Actual offline Maven run on five clean locked revisions passed 17 classes and 220 tests. The validated run started 2026-11-05T13:23:27.145430Z and ended 2026-11-05T13:23:59.893801Z. Summary SHA-256 a16e9c978cc812ed7a1ed2ecd6500511f9699a2fa832d35f548cd90f63eb2a2e. |
| S1 | Six planned paths, strict validators, unchanged 27 main SQL files and no workflow/profile/permission/dependency delta. |

Actual G3 exposed two compatibility details before passing. The runner uses lstat for Python 3.9. The report owner check normalizes Maven's temporary reactor basedir before comparing the exact absolute module directory. The fixtures exercise this real report path format. The legacy expense tests use Path.hardlink_to and run under Python 3.14 for the combined gate.

Manual checks and applicable literal rules were reviewed against the diff. The runner reuses the current shell reactor and the existing bounded, strict XML parser. The shared reader keeps legacy semantics when invoked without the local U options. The only allowed duplicate test name belongs to the fixed six-case dynamic-test corpus reported by Surefire. Existing checkout locks and credential-free inputs stay explicit.

This is a reproducible local U entry point for CI integration. CI checkout authority, remote execution and I/E/L remain separate. No application, database, provider or browser was started.
