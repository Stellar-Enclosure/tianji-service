# T4-03 ADK 持久会话续跑边界验收探针

| Field | Value |
| --- | --- |
| Document | `2026-11-05-19-58-adk-durable-resume-probe.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Evidence` |
| Complexity | `Simple` |
| Complexity Drivers | `None` |
| Created | `2026-11-05 19:58 Asia/Shanghai` |
| Updated | `2026-11-05 19:58 Asia/Shanghai` |
| Owner | 天机平台决策人 |
| Repository | `tianji-service` |
| Scope | T4-03 隔离 PostgreSQL Session 边界验收探针源码，仅编译交付 |
| Change Surface | 一个 AR JUnit 验收类及其文档。现有实现、迁移、授权与公开合同不变 |
| Affected Chapters | `§7, §8, §13, §14, §15, §16, §17, §18` |
| Source Requirement | [整体蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) `§19 T4-03 / S11` 与 Root 精确 test-only compile-scope |
| Baseline Revision | `tianji-service 5b764dfcf867e0703fd76defcedc276c57bfd3f2` clean |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [原生 ADK 证据](2026-11-05-18-48-adk-native-resume-semantics.md) `§7, §14` |
| Related Specs | [Agent owner 只读处置](2026-11-05-16-52-agent-recovery-disposition.md) `§7, §16` |
| Related Plans | `None` |

## 1. Summary

增加一个明确 opt-in 的 AR 探针，供操作者日后在一次性 PostgreSQL 验证三件事。验证无 Tool 的 first-user-event/pre-model 候选停点、已提交本地读响应在更换 Session lease 后的同 invocation 行为，以及过期 Run 拒绝第二个 Run owner。该次交付只编译新类，并运行现有的有限相邻测试。无数据库、应用或真实供应商调用。

两个实际 Session lease owner 仍持有同一个原本有效的 Run lease。它能为 PostgreSQL 事件持久化、codec v1/v2、旧 Session fencing 和 pinned ADK 存储兼容性取证。产品 Run takeover、checkpoint、外部 Tool 幂等和 S11 不在此证据结论中。

## 2. Background and Current State

### 2.1 Business and user context

维护者需要在决定新 checkpoint/接管契约前，验证同一 native invocation 的恢复证据能否通过现有 PostgreSQL Session 写入和读回。当前 Source81 是 SDK 内存边界证据，无法直接用于证明生产 PG、重启或 Run 接管。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact source | Observed fact | Verification limit |
| --- | --- | --- | --- | --- |
| EVD-001 | Existing finite evidence | AR `AdkNativeResumeSemanticsTest` 和 [Source81 evidence](../evidence/2026-11-05-adk-native-resume-semantics.md) | 已保存读结果时同 ID 不重发；工具已执行但结果未保存会再次调用 | SDK 1.13.0、内存 Session、Fake only |
| EVD-002 | Static repository | AR `infrastructure/adk/session/PostgresAdkSessionService#acquireSessionLease,appendEvent,requireRunFence` 和 `JdbcAdkSessionRepository#acquire,release` | 当前 Run lease 有效时可取得后续 Session lease，owner/epoch 确保旧 adapter 失效。现有 v1/v2 codec | 无产品 checkpoint 或接管 |
| EVD-003 | Static repository | AR test `AdkSessionPostgresTest#appends_are_idempotent_and_old_session_and_run_leases_are_fenced,native_runner_restores_a_tool_exchange_and_prior_turn_for_the_next_run` 和 `PostgresTestDatabase` | 已有 PostgreSQL lease 转移与 fresh turn 恢复历史模式；已有 loopback/fresh schema/Flyway target9 防护 | 不复用旧 suite 的激活参数 |
| EVD-004 | Static repository | AR `AgentLedgerService#claim,suspendExpiredLease`、`JdbcAgentLedgerRepository#claim`、`AdkAgentRunExecutor#executeScoped` | claim only QUEUED；到期 Run 挂起和 fence。生产走 fresh Content overload | 同 Run 新 owner 和原生续跑目前不存在 |
| EVD-005 | Local compile | 仓库外 JDK25 javac 与 javap | 实际候选类三方法、唯一 probe property 及专用 URL 编译通过 | probe methods 未执行 |

### 2.3 Problem statement and gap

现有证据未组合 PostgreSQL Session 读写、真实 Session lease owner 更换和 native 同 ID 行为。测试能够检查这三个现有技术点的组合，并独立证明现有 Run takeover 拒绝。它不增加任何可运行产品能力。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current call chain | Effect |
| --- | --- | --- |
| durable event append | native Runner → bound PostgresAdkSessionService → owning Run/Session fences and V3/V5 SQL | committed event/local projection and codecs |
| new Session lease | live Run → acquireSessionLease → existing owner/epoch CAS | old Session lease fenced |
| expired Run | ledger suspendExpiredLease → Run CAS SUSPENDED → claim QUEUED-only | same Run owner takeover refused |
| production execution | AdkAgentRunExecutor → standard fresh Content | no product resume |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing AR test-only path with no business scaffold, archetype module, example or production code.

## 3. Goals and Non-goals

### 3.1 Goals

为实际 PostgreSQL 验收准备一个编译可验证的唯一选定测试类。冻结两个 Session owner 在一个仍有效 Run lease 下的实验边界和当前 expired Run 的 negative control。

### 3.2 Non-goals

不运行 I、不连接数据库、不启动应用或 provider、不创建真实认证流、不写 checkpoint schema、不改变 Run/Session/预算/Tool 生产实现、不新增 runtime/HTTP/PaC/config。用例没有独立 JVM、真实 IAM、外部 Tool 或 S11 验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Delta or invariant | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR opt-in JUnit and source evidence | Affected | EVD-001/002/003/005 | one compile-only test, three named scenarios | exact test source, opt-ins and gates | §7, §8, §13, §14, §15, §16, §17, §18 |
| API/runtime owner | Context-only | EVD-004 | existing source and data authority | exact unchanged interface | §9 |
| PG schema | Unchanged | EVD-003 | existing V1–V9 read/use only in future I fixture | no migration change | §11 |
| Frontend | Not applicable | no Web code | no user surface | evidence-backed | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable source acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | prepare actual PG native Session handoff probe | Must | one test class compiles; first-user/no-tool and saved local readonly tool response cases encode two replacement Session owners on same live Run, codec v1/v2 | EVD-001/002/003 |
| REQ-002 | preserve same-Run takeover denial | Must | third test compiles for exact expiry/suspension and QUEUED-only replacement refusal | EVD-004 |
| REQ-003 | isolate runtime and source gates | Must | distinct opt-ins/URL guard, bounded fixture, actual new class only compiled, selected adjacent U only | EVD-003/005 |
| REQ-004 | record limits without claiming runtime | Must | exact pending three I methods and no product checkpoint or S11 claim | Root / Master |

### 4.2 Use-case analysis

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | AR maintainer | compile and audit candidate | source reactor | no DB property | EVD-005 |
| ACTOR-002 | future isolated operator | execute exact three I tests | explicit probe flag + private disposable DB | separate local test tenant and no user credentials | EVD-003 |

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | prepare source proof | ACTOR-001 | existing AR toolchain | reviewed exact source | no DB runtime | class and exact methods compile | fail on compile/setup or wrong selector | test remains unrun | REQ-001/002/003/004 | test class | TEST-001/002 |
| UC-002 | run exact future isolation witness | ACTOR-002 | isolated PG/ADK | explicit flag and validated URL | fresh prepared owner DB | three actual owning methods | missing/wrong URL or failure never counts | private proof only | REQ-001/002/003/004 | probe only | TEST-003 |

## 5. Constraints, Assumptions, and Decisions

DEC-001 Source only. Compile with existing reactor and finite selected regression. Do not select the new class in source execution. DEC-002 Only the distinct named flag and dedicated loopback URL can initialize the isolated fixture. DEC-003 Actual I is pending user action and must be reported separately, including any failure. ASM-001 No-tool native cut is a hypothesis until the actual PG probe passes.

## 6. Project Technology Context

Use existing AR JDK25, pinned ADK 1.13.0, RxJava, JUnit, JDBC and Flyway test wiring. No dependency, application context or production code.

### 6.1 Java architecture profile and capability baseline

Retain existing AR technical test package and source owner. Follow current ADK native and PostgreSQL Session test patterns. Scope-specific private isolation fixture is required because existing PostgresTestDatabase binds a different property shared by other tests.

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

Time contract: Not applicable
Reason: Existing lease expiry is exercised as a test-only fault injection using the existing DB clock. JUnit/Rx/JDBC timeouts bound the isolated fixture. No production timestamp, timezone, storage or wire semantics are changed.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| opt-in AR probe | Add | REQ-001–004 | Source81 in-memory and existing PG fresh turn tests | no persisted same-ID/lease transfer composition | one compiled test only, isolated I pending | Add |
| private fixture guard | Add | REQ-003 | existing shared URL helper | could activate unrelated suites when reused as the new explicit contract | same guard patterns, unique properties | Add |
| production checkpoint/runner | Exclude | REQ-004 | existing fences and no-op probe | separate authority and I | none | Remove |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| source gate | zero DB or provider | compiler + adjacent finite | existing production | fail on actual source/test compile | reliable prepared test |
| later probe | local PG only | two Session leases and one Run lease | existing data only in disposable DB | bounded timeout and fresh guard | exact native storage evidence if executed |

### 7.1 System Architecture Design

AR JUnit uses actual pinned Runner, local fake model, existing PostgresAdkSessionService and ledger over a private disposable PostgreSQL database. No Spring app/HTTP or provider. Candidate source validation never enters the three runtime methods.

### 7.2 High-Level Design

Dedicated `tianji.postgres.resume.probe=true` is the sole JUnit opt-in. A separate `tianji.postgres.resume.probe.url` must match a numeric valid loopback port and `tianji_it_resume_` database name before connection. Prior generic `tianji.postgres.test.url` alone never enables or selects this class. Dedicated props leave other suites' conditions unchanged. Before Flyway require exact current primary and no Tianji schema, migrate existing V1–V9 with clean disabled, no retry and short DB bounds. Fixture state is per temporary database/test tenant.

### 7.3 Detailed Design

1. No-tool candidate cuts at actual pinned SDK beforeModel callback after stored original user event, with zero Fake model calls. Test continuation hypothesis by a replacement Runner and Session adapter after exact old Session expiry.
2. Saved-response candidate uses one local no-effect FunctionTool, waits for stored matching response, and checks same native invocation, no repeated Fake read, prefix byte digests, original user count and native terminal/no-op replay. Iterate codec v1/v2 for both candidates. Run lease remains original and live. Capture old/new Session opaque owner and incremented epoch, prove old release/append denied without logging tokens.
3. Negative case expires exact existing Run in test fixture, invokes existing suspendExpiredLease, and checks current second-owner claim and old lease acquisition/append are denied. No product resume is called.
4. Source validation compiles exact class and selects only separate finite U tests. Bytecode verifies three methods/unique condition/URL prefix. Actual I is pending, including no-tool feasibility.

## 8. Package Structure and Code File Tree

One AR test: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java`. Three documentation files in existing EGON Spec/Plan/evidence locations. No additional files.

## 9. Interface Definitions

Scope disposition: Context-only. Existing pinned ADK Runner, PostgresAdkSessionService and ledger claim/suspension APIs are reused in future I. No runtime endpoint, command or new product interface.

## 10. POJO and Data Model Design

Only test-local Fixture, LocalRead, ScriptModel and immutable redacted LeaseFact. No production DTO, schema or token output.

## 11. Database Design

Scope disposition: Unchanged. Future user-operated I provisions only existing V1–V9 in a separately named disposable empty database using the exact existing test pattern. No source execution, new migration, cleanup, repair or live database.

## 12. Frontend Page Design

Not applicable. AR test-only evidence.

## 13. Design Patterns and Architecture Principles

Direct native test composition. Reuse existing services, pinned Runner and finite test conventions. No extra abstraction or test-only production injection seam.

## 14. Test Design

| ID | Gate | Expected proof |
| --- | --- | --- |
| TEST-001 | safe source gate only | AR test compile and only existing AdkNativeResumeSemanticsTest, AgentRecoveryDispositionEvidenceTest and AdkSessionEventCodecTest execute |
| TEST-002 | static bytecode/scope | exact three methods and unique flag/URL, no generic PG property, only one test source plus docs, immutable main SQL |
| TEST-003 | independently authorized user I | exact probe class only. Three non-skipped methods, two codec modes in each positive, bounded actual PG proof. Failures or skips are not acceptance |

No assertion RED requiring runtime is authorized. Existing finite tests provide regression only. New class must not appear among executed source-gate reports.

## 15. Non-functional and Cross-cutting Design

Class-level 75s, per Rx 5s, explicit connection/statement/lock bounds and Flyway lock retry zero. Exact loopback and dedicated fresh primary. No provider calls, raw native event or lease owner output.

## 16. Compatibility, Migration, Rollout, and Rollback

Source commit is test/doc only. All 27 existing main SQL unchanged. No default execution. User independently prepares and operates disposable PG and tests exact class. Product runtime remains disabled and unchanged. Revert source one commit only.

## 17. Alternatives and Decisions

Compile-only probe selected. Running actual PG now is outside authority. Copying the current test fixture with generic property could activate another suite. Simulating a successful product Run takeover would invent behavior absent in the ledger and executor.

## 18. Risks and Open Questions

No-tool first-user-event cut may fail actual PG/native compatibility and remains a hypothesis. Saved-read case local Fake proves no local replay only. Product checkpoint needs real isolated proof and separate authorized atomic capture/consume, lease takeover CAS and grant design. S11 pending.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001, UC-002 | §7.3/8 | TEST-001/003 |
| REQ-002 | UC-001, UC-002 | §7.3 | TEST-001/003 |
| REQ-003 | UC-001, UC-002 | §7.2/14/16 | TEST-001/002/003 |
| REQ-004 | UC-001 | §14/16/18 | TEST-002 |

## 20. Review and Acceptance

### 20.1 Original-request fidelity

Test-only source, compile-only new probe, no DB execution. Exact safe later operator boundary.

### 20.2 Repository and technical fidelity

Current public AR APIs and existing PostgreSQL fixture pattern verified. Actual candidate standalone javac passed.

### 20.3 Cross-section consistency

One class, three explicit tests, distinct guard and no product behavior. Actual I independent.

### 20.4 Relationship and effective-design review

Source81 current finite proof remains intact and is an anchor, not merged with future I or S11 acceptance.

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

This verdict covers the compile-only source design. PostgreSQL probe execution and product recovery remain unverified.
