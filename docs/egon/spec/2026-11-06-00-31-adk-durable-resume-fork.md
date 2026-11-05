# T4-03 ADK 持久 Session 双进程无工具恢复探针

| Field | Value |
| --- | --- |
| Document | 2026-11-06-00-31-adk-durable-resume-fork.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 既有 opt-in probe 增加一个受限 JVM 交接和闭合测试 pipe |
| Created | 2026-11-06 00:31 Asia/Shanghai |
| Updated | 2026-11-06 00:31 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | 三个 AR 测试文件，分进程验证候选无工具 pre-model 切点 |
| Change Surface | 原 opt-in probe、test-only handoff 和 finite protocol 测试 |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | [Master §19 T4-03 与 S11](../../../../docs/plans/agent-framework-upgrade-plan.md) 进程重建候选 |
| Baseline Revision | tianji-service 8fa8474a2880242c7ef6824a285eea15a0adca3f |
| Amends | None |
| Supersedes | None |
| Depends On | [Existing durable ADK resume probe](2026-11-05-19-58-adk-durable-resume-probe.md#7-architecture-design) |
| Related Specs | None |
| Related Plans | [Fork probe implementation](../plan/2026-11-06-00-32-adk-durable-resume-fork.md) |

## 1. Summary

既有隔离 PostgreSQL 探针增加一个操作者双开关场景。父测试启动 child A 保存原始无工具 invocation，在 Fake model 前切点确认落盘后杀死 child A。child B 使用同一个仍有效的原始 Run lease、不同 Session owner 和明确保存的 invocationId 测试续跑。交付编译和有限协议证据，实际 PostgreSQL/子进程场景由操作者另行运行。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | [Existing probe evidence](../evidence/2026-11-05-adk-durable-resume-probe.md) | Source86 compiled three in-process PostgreSQL candidates without running I. It keeps the same live Run and changes Session owners. |
| EVD-002 | [Current probe](../../../stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java) | Existing JUnit class owns disposable loopback source, V1–V9 setup, Fake model and explicit invocation. |
| EVD-003 | [ADK native semantics](../evidence/2026-11-05-adk-native-resume-semantics.md) | Fixed ADK 1.13.0 finite probe distinguishes committed response from potential replay. Existing compatibility claim is narrow. |
| EVD-004 | [Owner recovery evidence](../evidence/2026-11-05-agent-recovery-disposition.md) | Existing read-only disposition does not authorize Run takeover or resume. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR JUnit-only path, no new project or business scaffold.

## 3. Goals and Non-goals

Prepare an exact process-death experiment for the prior no-tool pre-model candidate. Existing three isolated in-process methods and production Run/Session behavior remain. New product checkpoint, lease takeover, effect reconciliation and S11 remain independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR opt-in test and finite pipe | Affected | EVD-001/002/003 | Existing test class adds one distinct child experiment and strict handoff | Protocol, lifecycle, limits and source proof | §7, §8, §10, §13, §14, §15, §16 |
| AR production and DB schema | Context-only | EVD-002/004 | Reuse existing store/leases and existing V1–V9 in later I | Exact reuse only | §9, §11 |
| Frontend and external API | Not applicable | EVD-001/004 | No consumer/contract | Scope only | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Isolated opt-in child experiment | Must | Parent requires both explicit flags and exact fresh local database. Child A stopped at committed pre-model and forcibly reaped before child B. | EVD-001/002 |
| REQ-002 | Exact same invocation under replacement Session lease | Must | Two codec modes; same original live Run, new Session owner and epoch, one fake model call, preserved prior digest prefix and no product Run result | EVD-002/003 |
| REQ-003 | Bounded test handoff and finite negative proof | Must | Strict 1024-byte pipe, local fixture URL, synthetic fields, controlled marker and finite rejects | EVD-002 |
| REQ-004 | Separate source and runtime evidence | Must | One actual presence RED, four protocol tests and existing adjacent finite green, child compiled only. No PG I or full S11 result claim. | EVD-001/004 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify finite protocol and compiled child code | Existing offline test build only |
| ACTOR-002 | Authorized disposable fixture operator | Independently attempt the two-process case | Explicit fresh loopback primary and both opt-ins |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify exact bounded test source | ACTOR-001 | Existing AR finite tests | Offline source gate | Reviewed source | RED, finite and compiled probe | Assertion or compile failure stops | I remains pending | REQ-003/004 | Internal test pipe | TEST-001–003 |
| UC-002 | Attempt same-invocation Session continuation after child death | ACTOR-002 | Disposable PostgreSQL and two JVMs | Explicit operator-only method | Both switches, correct fixture, live original Run | Child, lease and durable event assertions | Timeouts/invalid evidence fail | No product takeover | REQ-001/002/004 | JUnit probe | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Scope is AR test code only. Parent JUnit class requires tianji.postgres.resume.probe=true; the new method also requires tianji.postgres.resume.probe.fork=true. The existing tianji.postgres.resume.probe.url accepts only a credential-free 127.0.0.1 JDBC URL, valid port and exact disposable name prefix. Future I requires a fresh primary without tianji_agent, existing V1–V9 migrations, POSIX 0700 scratch and a local fixture authentication setup that works for both child JVMs. No credentials are put in child argv or pipe, and child environment is cleared. Current source gates never pass either opt-in.

## 6. Project Technology Context

Existing Java 25, JUnit, fixed ADK Java 1.13.0, DriverManagerDataSource, Flyway and PostgresAdkSessionService. No dependency/config or new database artifact.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | existing test class names | semantic test and immutable LeaseFact | one Java test | TEST-001 | PASS |
| Rule 2 | Yes | existing isolated fixture | explicit unique flag, strict URL, primary/fresh checks | private helper | TEST-002 | PASS |
| Rule 3 | Yes | existing value fixture style | record only opaque test facts | LeaseFact | TEST-001 | PASS |
| Rule 4 | No | no Spring bean | no new log/Bean | one test | diff | N/A |
| Rule 5 | Yes | existing Java/ADK/JDBC/Flyway | reuse current tools only | one test | TEST-001 | PASS |
| Rule 6 | No | existing Session codec | no writer/JSON change | test v1/v2 | diff | N/A |
| Rule 7 | No | no profile | dedicated test-only system props | one test | TEST-002 | N/A |
| Rule 9 | No | direct finite I fixture | no production logic | test | TEST-001 | N/A |
| Rule 10 | Yes | existing database lease/time semantics | time bounds and DB-only expiry fixture | test | TEST-001/003 | PASS |
| Rule 11 | Yes | existing AR technology | no new owner, SQL migration or write path | scope | TEST-001/002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | run.expiresAt, original event timestamp | Occurred instant | java.time.Instant | UTC instant | Internal | Explicit UTC Instant | No external display | No local-time conversion or new Run lease | Existing database/ADK clock and preserved payload prefix | Existing owner/lease unchanged | TEST-002/004 |
| TIME-002 | process and probe budgets | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed budgets | Not applicable: test only | 12s child wait, 75s JUnit timeout, 45s original Run fixture lease and 30s child Session lease | Monotonic wait for markers | Existing in-process methods remain | TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Forked probe method | Add | REQ-001/002 | Existing same-JVM replacement | Cannot observe death of original JVM | Two bounded child lifetimes per mode, only explicit I | Add |
| Test-only bounded handoff | Add | REQ-003 | Arbitrary child argv/env, or serializing state | No exact version, bounds or privacy | One tiny stdin frame; file marker limited | Add |
| Existing owner store and Fake | Keep | REQ-001/002 | New product checkpoint | Existing APIs sufficient for candidate test | No production change | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Existing in-process I | Local disposable DB only | Existing fixture | Existing Run and Session | Same JVM | Existing proof candidate |
| New fork I | Local disposable DB only | Capture, killed, replacement | Same original Run and replaced Session | Explicit persisted marker, bounded waits | Tests true JVM death at one narrow cut |

### 7.1 System Architecture Design

Opt-in parent JUnit provisions the existing disposable fixture. Each child is the same AR test class main, using local JDK/test classpath, exact bounded stdin, a private marker path and current primary read. No application or provider entrypoint.

### 7.2 High-Level Design

For V1 and V2 typed-write modes separately, use a fresh synthetic tenant, one existing original Run and one Session. Read the committed initial event in child A before blocking before the model. Parent checks marker/PID, force-kills/reaps A, then expires only the exact old fixture Session owner/epoch. Start child B and validate distinct PID and Session owner with epoch increment. Continue saved native invocation on the original live Run lease.

### 7.3 Detailed Design

Handoff format v1 contains bounded URL, synthetic tenant, codec mode, exact Run UUID and lease epoch/expiry, mode and optional invocationId/prior Session owner SHA. The fixed original owner is reconstructed; no credentials or persisted event content crosses. Strict booleans, full EOF, exact regex/size and safe failure. Child derives marker path from single argv, checks canonical absolute private directory and capture/resume filename. Uses JDK test classpath, cleared environment, discarded stdout/stderr. Parent bounds marker at 64 bytes and child waits at 12s, creates markers once and cleans after reaping children.

Child checks exact local database and primary plus exactly successful V1–V9 and existing Flyway validation. Capture requires one already persisted original event and zero Fake model calls. Replacement uses new Session owner/epoch, one original invocation and one Fake model call. Verify native terminal, then repeat exact invocation with zero output and no added model call. Parent compares persisted prefix by sequence, native ID, event timestamp and exact payload SHA and checks no product result. Existing original Run lease is passed in both handoffs and never replaced.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoffTest.java` |
| Create | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkResumeProbeHandoff.java` |
| Modify | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java` |
| Create | `docs/egon/spec/2026-11-06-00-31-adk-durable-resume-fork.md` |
| Create | `docs/egon/plan/2026-11-06-00-32-adk-durable-resume-fork.md` |
| Create | `docs/egon/evidence/2026-11-06-adk-durable-resume-fork.md` |

## 9. Interface Definitions

Context-only existing JUnit operator interface. The process entry and stdin are test-only; no public Java service, REST, SDK, PaC or runtime config.

## 10. POJO and Data Model Design

Test-only immutable Request record with redacted toString, format 1 and maximum 1024 bytes. Its DB address must be the single exact local fixture URL and all identity inputs are bounded synthetic values. Runtime Run/Session records and existing codecs remain owned by current storage.

## 11. Database Design

N/A for migration changes. Later isolated I provisions existing V1–V9 into a fresh disposable database only and expires one exact old Session lease row for test setup after the old child is dead. No current application database or migration edits.

## 12. Frontend Page Design

N/A. No UI or browser.

## 13. Design Patterns and Architecture Principles

Reuse the current test probe and separate finite protocol parser. Do not add production abstractions for a test-only handoff.

## 14. Test Design

| Test | Scope |
| --- | --- |
| TEST-001 | Presence RED, disposable_resume_handoff_protocol_exists |
| TEST-002 | Four pure handoff tests for exact round-trip, bounds and malformed cases |
| TEST-003 | Finite adjacent native semantics and recovery disposition, source compile and javap only |
| TEST-004 | Separate actual opt-in process death experiment with fresh disposable primary and both codec modes |

## 15. Non-functional and Cross-cutting Design

Finite test handoff with one private marker directory, bounded wait and read, no shell, no provider, no external token and no child argv credentials. Remove child processes and marker files. The disposable database lifecycle remains with the operator.

## 16. Compatibility, Migration, Rollout, and Rollback

Test-only source, existing three methods and production artifacts unaffected. Current source gate never launches PostgreSQL or the child; later opt-in method selected independently. Revert one commit.

## 17. Alternatives and Decisions

Same-JVM evidence remains useful and independent. The new bounded JVM fork is necessary to observe a real process death at the existing no-tool cut. A production takeover path would require separate source, authority and I.

## 18. Risks and Open Questions

An actual positive result still demonstrates only this no-tool cut on the same original Run, and leaves production checkpoint, external effect reconciliation, replacement Run owner and complete S11 open. Failure is diagnostic evidence, with no fallback to a fresh invocation.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-002 | §5/7/14 | TEST-003/004 |
| REQ-002 | UC-002 | §7/10 | TEST-004 |
| REQ-003 | UC-001/002 | §7/10 | TEST-001/002 |
| REQ-004 | UC-001 | §14/16 | TEST-001/002/003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | existing AR test owner | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002/003 | existing services/toolchain | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | no dependencies | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | semantic test/value names | None |
| MC-VALID-001 | Applicable | PASS | §7.2/14 | exact isolation guard | None |
| MC-MODEL-001 | Applicable | PASS | §10 | test-local immutable facts | None |
| MC-CONVERT-001 | Not applicable | N/A | §8/10 | no production mapper | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | no business logger | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | no Bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | existing JDK/JUnit/JDBC/Flyway | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | no contract/codec edit | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | bounded test and existing DB clock | None |
| MC-CONFIG-001 | Not applicable | N/A | §7.2/8 | test-only props, no profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | direct native probe | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | exact source-only test/docs | None |
| MC-TEST-001 | Applicable | PASS | §14 | compile/source vs actual I | None |
| MC-BLOCKER-001 | Applicable | PASS | §18/20 | source design ready, live pending | None |

### 20.6 Final verdict

PASS — Ready for user review
