# Default-off Worker recovery API — Source60 / recovery Plan Step 5

Baseline `8c4c5d6541fa27c8390154578980cdc9dd6b2ea4`; effective [Spec](../spec/2026-11-05-13-41-process-worker-recovery.md) and [Plan](../plan/2026-11-05-14-27-process-worker-recovery.md). See earlier [contract](2026-11-05-worker-recovery-contract.md), [SDK](2026-11-05-worker-recovery-sdk.md), [storage](2026-11-05-worker-recovery-storage.md) and [native service](2026-11-05-worker-recovery-service.md) gates.

The two provider routes require the enabled servlet Process and Worker APIs and the independent recovery flag. Both carry exact READ and WORKER_RECOVER requirements for USER and SERVICE; current access and exact START ownership are enforced again for replay. The named factory uses the owning Process engine, source and manager, verifies V10/native readiness independently of cancellation, and does not register another Control native-access bean. POST requires strict UTF-8 JSON with exactly five strings and caps both declared and streamed request bytes at 8 KiB; cancellation keeps its 4 KiB bound. MVC and the existing IAM filters emit a consistent safe recovery envelope. A route-and-method-scoped wrapper before IAM normalizes its synchronous 401/403/503 short-circuit bodies while preserving status and challenge/security headers. Other routes, redirects and successful bytes are preserved.

| Gate | Actual result |
| --- | --- |
| Provider presence/flags RED | 3 expected assertion failures for the missing controller and flag behavior, 0 errors/skips. |
| G17 typed RED | 16 tests, 9 expected assertion failures with provider declarations only, 0 errors/skips. |
| G18 exact provider regression | 55 passed in 7 PS classes, 0 failures/errors/skips. |
| G19 clean verify and artifacts | 255 passed in 29 classes (C 31, CL 30, PS 194), 0 failures/errors/skips, 1:33, 2026-11-05 15:23:54 +08:00. Actual packaged evidence verified. |
| G20 conformance/scope | Strict Spec and Plan passed; exact 10 Step 5 paths and 43 aggregate recovery paths. All 25 original SQL files and Step 3 V10 bytes unchanged. |

G19 used the exact Plan selector with offline JDK 25 source reactor, `clean verify`, existing `permission-producer` profile, credential-free task settings, `-DskipITs=true`, OTel exporters off and ADK content capture disabled. The baseline manifest was generated and artifact-verified before new provider source edits. The final artifact preserves all 22 old resources, 17 permission definitions, 39 messages and existing application/producer metadata; adds exactly 2 resources, 1 permission and their 3 labels. The GET resource has the same framework-implied protected HEAD metadata as the existing Control GET. These local artifact builds do not register a manifest, grant a role, or enable recovery.

Actual PS executable JAR matches source `application.yml` (SHA-256 `f31d614810af0a037ef883cfb57da26e9db7928ae3c5830e816bd5fb78ccdcf8`, recovery false) and immutable V10. Its embedded manifest SHA-256 is `d427d8e88f7bc1dc5ab52e01928a2d73c2003e61762e2e8ffb08a10173cb7dfc`. Both actual CL OpenAPI members match source; old Control is unchanged. Finite logs, XML reports, extracted members and exact artifact comparison are in `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/recovery/`.

| Requirement | Evidence and boundary |
| --- | --- |
| REQ-001 | Separate bounded observation route and exact record/SDK/OpenAPI; shared decision preserves historical and native distinctions. |
| REQ-002 | Current READ and RECOVER metadata, exact owner/source guards, authorization and flag negatives. Live IAM unverified. |
| REQ-003 | Composed ordinary READ_ONLY admission, exact current proof, bounded histories and version; absent or unsafe proof fails closed. |
| REQ-004 | Scoped native lock, one move and readback, same local transaction and rollback requests verified in finite fixtures. Real Flowable/PostgreSQL unverified. |
| REQ-005 | Exact immutable V10 audit, one accepted native ID, same-command result replay, safe distinction of conflicts. Live privileges/atomicity unverified. |
| REQ-006 | Strict 8 KiB provider, closed SDK/OAS/status matrix, packaged resource/manifest metadata and default-off YAML. |
| REQ-007 | Finite preceding Steps, compatibility and old byte checks; separately authorized runtime S10/S11 still required. |

| Manual check | Status | Actual evidence |
| --- | --- | --- |
| MC-ARCH-001 | PASS | Existing PS adapter, named factory and exact same-source collaborators; no alternative runtime layer. |
| MC-REUSE-001 | PASS | Current access, IAM chain, body advice, error adapter, factory style and existing manifest tooling. |
| MC-DEP-001 | PASS | Existing Servlet/Spring/Jackson/Flowable/JDK; all POMs unchanged. |
| MC-NAME-001 | PASS | Exact route, feature, permission, DTO and factory names match C and OAS. |
| MC-VALID-001 | PASS | MockMvc, context and source tests cover strict bytes/JSON, ownership, scope, conditions and replay. |
| MC-MODEL-001 | PASS | Closed immutable DTOs and fixed error envelope; no provider model fork. |
| MC-CONVERT-001 | PASS | Existing converter and exact input tree/type check; explicit no-store DTO response. |
| MC-LOG-001 | PASS | Existing safe error logger and safe service outcome; no credential, request body or native diagnostics. |
| MC-BEAN-001 | PASS | Named conditional same-source factory and qualifiers, no competing native collaborator bean. |
| MC-UTIL-001 | PASS | Reuse existing bounded body reader and Spring request matcher/cache wrapper; no extra utility dependency. |
| MC-JSON-001 | PASS | Duplicate/trailing/type/unknown/UTF-8 negatives, no-store and exact packaged CL schemas. |
| MC-TIME-001 | PASS | Existing UTC contract/DB Instant semantics and finite exact serialized timestamps. |
| MC-CONFIG-001 | PASS | Explicit false and exact new USER mapping verified in actual JAR; other profile keys unchanged. |
| MC-PATTERN-001 | PASS | Thin adapter and scoped Servlet decorator over existing IAM; one composed Specification in service. |
| MC-SCOPE-001 | PASS | Exact 10 / aggregate 43, unchanged POMs and 26 immutable SQL; no app/runtime/grant changes. |
| MC-TEST-001 | PASS | Actual provider RED and GREEN; exact aggregate finite package plus generated artifact comparisons. |
| MC-BLOCKER-001 | PASS | Source checks closed; live acceptance remains independently open and is not inferred. |

| Rule | Status | Independent evidence |
| --- | --- | --- |
| 1 | PASS | Existing PS naming and stable public route/command/authority vocabulary. |
| 2 | PASS | Strict provider shape and auth/prerequisite fences; original cancellation bound preserved. |
| 3 | PASS | Immutable C command/results and narrow adapter, no persistence conversion at provider boundary. |
| 4 | PASS | Existing named/qualified technical factory and safe error logging only. |
| 5 | PASS | Existing Spring/JDK/Jackson and manifest tools; no added dependency. |
| 6 | PASS | Closed JSON, safe status/code and exact SDK/OpenAPI artifact evidence. |
| 7 | PASS | Default false and exact current scope metadata; packaged source parity. |
| 9 | PASS | Thin HTTP adapter, scope-only Servlet wrapper and existing shared recovery policy. |
| 10 | PASS | Typed UTC Instant contract and actual finite wire assertions. |
| 11 | PASS | Current technical PS profile; no new main layer, direct ACT DML, generator or SQL changes here. |

All five recovery source Steps are complete. Deployment, catalog deparse, live IAM/privilege/Flowable movement, business READ_ONLY policy and S10/S11 runtime acceptance remain unverified. No application, database, listener or browser was started.
