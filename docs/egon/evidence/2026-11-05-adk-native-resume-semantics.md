# Native ADK BaseAgent resume semantics — Source81

Effective [Spec](../spec/2026-11-05-18-48-adk-native-resume-semantics.md) and [Plan](../plan/2026-11-05-18-49-adk-native-resume-evidence.md). Baseline `6e4997d2f352bba04e53bc404306079aab9d4ae0`.

The finite test uses pinned ADK Java 1.13.0's actual BaseAgent/LlmAgent Runner, a local Fake model and real SDK InMemorySessionService, with no raw injected Session Event. The original and replacement Runner share the saved native evidence. The test cuts at a controlled callback boundary, then observes the exact invocation, tool calls and persisted input under bounded waits.

| Native window | Actual finite observation |
| --- | --- |
| Exact saved ordinary tool response | Same invocation resumes with no tool replay or duplicate user message; tool call count remains 1. |
| Tool actually ran but response was not saved | Same-ID null-input resume dispatches the tool again; tool call count reaches 2. |
| Completed, unknown/off, long-running response or fresh input | Completed no-input is inert; unknown/off and mismatched response refuse; exact matching function response continues same invocation; fresh ordinary text creates a new invocation. |

Actual G1 `AdkNativeResumeSemanticsTest`: 3 passed, 0 failures/errors/skips, 2026-11-05 19:06:34 +08:00. Offline JDK25 source reactor, `-DskipITs=true`, no exporters or ADK content capture. The owning XML is under `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/native-resume-probe/`. The earlier independent JDK standalone and isolated JUnit results reproduce the same mechanisms. TDD is not applicable to this evidence-only source change; there is no new product implementation.

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | One existing AR native test path, no runtime change. |
| MC-REUSE-001 | PASS | Actual pinned SDK Runner/SessionService and existing finite tooling. |
| MC-DEP-001 | PASS | All POMs unchanged. |
| MC-NAME-001 | PASS | Explicit native resume semantics test. |
| MC-VALID-001 | PASS | Exact persisted callback cut, count and same/fresh invocation checks. |
| MC-MODEL-001 | PASS | Current SDK native facts, no new production state. |
| MC-CONVERT-001 | N/A | No mapper or persistence changes. |
| MC-LOG-001 | N/A | No business logging or input/output exposure. |
| MC-BEAN-001 | N/A | No application or managed component. |
| MC-UTIL-001 | PASS | Existing JDK/JUnit/ADK only. |
| MC-JSON-001 | N/A | No external JSON contract change. |
| MC-TIME-001 | PASS | Finite bounded waits and deterministic evidence. |
| MC-CONFIG-001 | N/A | No feature or profile change. |
| MC-PATTERN-001 | PASS | Direct actual native regression, no parallel production mechanism. |
| MC-SCOPE-001 | PASS | Exact four files and 27 immutable main SQL. |
| MC-TEST-001 | PASS | Actual owning test suite, three tests and no skip. |
| MC-BLOCKER-001 | PASS | Finite evidence complete; production checkpoint and S11 separately pending. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing semantic test and Fake evidence names. |
| 2 | PASS | Exact native call/response identity assertions. |
| 3 | PASS | Native immutable test facts, no entity. |
| 4 | N/A | No business logger or managed Bean. |
| 5 | PASS | Existing JDK/ADK/JUnit only. |
| 6 | N/A | No public JSON changes. |
| 7 | N/A | No configuration/profile edits. |
| 9 | PASS | Real native behavior regression, no new scheduler. |
| 10 | PASS | Explicit finite time bounds. |
| 11 | PASS | Existing test-only AR technical scope and no schema or Event change. |

Current Tianji execution remains unchanged; this test does not authorize a resume or a Tool replay. PostgreSQL Session/lease recovery, terminal checkpoint, S11 and any production read-only resume mode remain unverified. No application, listener, database, browser or real model provider ran.
