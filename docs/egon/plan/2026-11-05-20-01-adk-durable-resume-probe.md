# ADK 持久会话续跑探针实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-20-01-adk-durable-resume-probe.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 20:01 Asia/Shanghai` |
| Updated | `2026-11-05 20:01 Asia/Shanghai` |
| Owner | 天机平台决策人、Root |
| Repository | `tianji-service` |
| Scope | T4-03 单 AR 测试源码及三份文档，仅编译新探针 |
| Source Requirement | 主 Spec REQ-001–004，Root test-only compile-scope |
| Baseline Revision | `tianji-service 5b764dfcf867e0703fd76defcedc276c57bfd3f2` clean |
| Implements Spec | [ADK 持久会话续跑探针](../spec/2026-11-05-19-58-adk-durable-resume-probe.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 19:58 Asia/Shanghai` SHA-256 `78536cfdb07598d8f44c1f6d8f886227a45d427bcec9550a63e852faa0fd3539` |
| Effective Specs | [主 Spec](../spec/2026-11-05-19-58-adk-durable-resume-probe.md)，[Source81](../spec/2026-11-05-18-48-adk-native-resume-semantics.md) `§7, §14` preserved evidence |
| Depends On Plans | [Source81](2026-11-05-18-49-adk-native-resume-evidence.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

一个 AR commit 交付明确 opt-in 的 PostgreSQL Session/native 边界探针。用现有 source reactor 编译新类，仅执行三个现有纯有限相邻类，检查 actual class/guard/selector evidence。三个 PostgreSQL 方法全部留待操作者独立运行。无生产改动或 resume 承诺。

## 2. Target Spec and Effective Design

### 2.1 Primary target

Root 批准当前 5b764df 上的 compile-only 探针源码、现有 schema fixture 和静态验证。正式 source 前锁定 Spec/Plan/候选测试实际 SHA。主 Spec 与 Plan 保持 Review，I 未授权于当前 scope。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [durable probe](../spec/2026-11-05-19-58-adk-durable-resume-probe.md) | Review, exact SHA | all, REQ-001–004 | new test-only scope |
| Existing authority | [Source81](../spec/2026-11-05-18-48-adk-native-resume-semantics.md) | existing committed finite | §7/14 | actual native safety boundary, no replacement of in-memory assertions |

### 2.3 Superseded or excluded content

No supersession. New PostgreSQL test execution, real IAM, provider, process/engine startup, product checkpoint/schema, Run takeover, budget/tool mutation or HTTP/PaC remain out of scope.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/7 | prepare two same-Run Session handoff cases | methods compile and encode exact expectations, no actual PG acceptance | new test |
| REQ-002 | §4/7 | existing expired Run owner denial | method compiles and existing fence categories explicitly asserted | new test |
| REQ-003 | §7/14 | dedicated opt-in and isolated scope | bytecode exact guard/URL, only adjacent finite classes selected | test and source gate |
| REQ-004 | §14/16/18 | honest independent runtime boundary | docs report no new executed I, no product checkpoint or S11 | evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Review exact current baseline, compile prepared test. Stage one AR test and exact canonical docs after actual finite and static proof. No transition to an execution Step.

### 4.2 Test-first strategy

Assertion RED is not applicable. The sole code deliverable is itself an opt-in future I probe. Forcing a failure by selecting it would either skip under its guard or initiate unauthorized database activity. Verify actual compilation, private isolation guard and selected adjacent finite reports. Do not claim tested probe behavior.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | clean 5b764df and reviewed exact candidate | read-only review only | runtime, other edits to same paths | exact scope and test selectors |

### 4.4 Commit boundaries

One service repository commit with four exact paths. No main/config/migration/pom/runner/CI edits or compiled artifacts.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| one future PG probe | Add §7 | Source81 actual native + existing PG tests | current tests do not compose persistent same-ID/lease replacement | one class only | Implement |
| distinct private guard | Add §7.2 | generic PostgresTestDatabase URL | shared test property could activate other cases | dedicated flags and prior safeguards | Implement |
| product recovery | Remove §3.2 | current QUEUED-only claim | no safe substitute | none | Excluded |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| isolated candidate | REQ-001/002/003 | G1 compile, RED N/A | actual pinned AR/JUnit/current source | three compiled methods | future user I | Step 1 |
| static/document evidence | REQ-003/004 | G2/S1 | actual artifacts/reports and reviewed paths | distinct source and pending runtime | maintainer | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| architecture | existing AR PostgreSQL test package | one test, no production profile changes | test only | Step 1 MC-ARCH-001 |
| reuse | actual ADK, Session service, Flyway/current JUnit | existing technical fixtures with distinct gate | same dependencies | Step 1 MC-REUSE-001 |
| model/time | exact current lease and Event APIs | bounded local fixture and redacted facts | no new persistence model | Step 1 MC-MODEL-001/MC-TIME-001 |
| business variation | evidence, no runtime design | direct fixture | no new pattern/framework | Step 1 MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| native Runner | existing Source81 | same pinned SDK App/callback/local fake | fits | reuse direct pattern | none | Step 1 MC-REUSE-001 |
| persisted Session | current AR PostgreSQL fixture | exact fences, V1–V9 | fits | same services | one test | Step 1 MC-ARCH-001 |
| explicit database isolation | PostgresTestDatabase | loopback/fresh/Flyway but shared property | isolated variant needed | same checks with unique local props | private helper only | Step 1 MC-VALID-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | AR test names | File 1 | semantic test and LeaseFact | G1/G2 | Step 1 | PASS |
| Rule 2 | §6.2/7 | existing test validation | File 1 | unique flags and strict safe loopback | G2 | Step 1 | PASS |
| Rule 3 | §6.2/10 | existing record fixtures | File 1 | immutable redacted fact only | G1 | Step 1 | PASS |
| Rule 4 | §6.2 | no Spring Bean | all | no production logging | S1 | Step 1 | N/A |
| Rule 5 | §6.2 | current JDK/ADK/JUnit/JDBC/Flyway | File 1 | no added dependency | G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/11 | existing codec untouched | all | no serializer edits | S1 | Step 1 | N/A |
| Rule 7 | §6.2 | test props only | File 1 | no Spring profiles | G2/S1 | Step 1 | N/A |
| Rule 9 | §6.2/13 | direct evidence fixture | File 1 | no production logic | S1 | Step 1 | N/A |
| Rule 10 | §6.2/15 | existing time semantics | File 1 | finite waits and DB-only fixture expiry | G1/G2 | Step 1 | PASS |
| Rule 11 | §6.1/8 | existing AR technical package | all | no production or SQL changes | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable
Reason: Exact fixture timeout and existing DB lease expiry are test-only. No production timestamp, storage, timezone or wire change.

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java` | existing native and PG suites | isolated exact three-method probe | compiled test only | Step 1 | REQ-001–004 | G1/G2 |
| CREATE | `docs/egon/spec/2026-11-05-19-58-adk-durable-resume-probe.md` | reviewed design | exact authority | Spec | Step 1 | REQ-001–004 | S1 |
| CREATE | `docs/egon/plan/2026-11-05-20-01-adk-durable-resume-probe.md` | reviewed Plan | exact order | Plan | Step 1 | REQ-001–004 | S1 |
| CREATE | `docs/egon/evidence/2026-11-05-adk-durable-resume-probe.md` | actual reports | compile-only and pending I | evidence | Step 1 | REQ-003/004 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Clean exact Service 5b764df. Freeze existing 27 main SQL hashes and exact allowed paths. Web independent. Exact candidate SHA `b3be09625bd12ebfc264db90feb12416a9f56a286c064c6812b97c1982e9c7cd`.

### 6.2 Build, test, and environment prerequisites

Existing JDK25 offline source reactor settings. No PostgreSQL property in source gate. No app/provider/browser. Verify actual selected finite suite reports and compiled class.

### 6.3 Immutable constraints and approved decisions

Do not select the new probe or any existing PostgreSQL test. Existing product source, migrations, behavior and auth remain. No valid I from compilation, mocks or disabled/skip.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | exact custom URL prefix and unique flag | existing disposable PG helper | separate test isolation, no deployment config | reject nonmatching before connection |
| PLAN-CLAR-002 | javap and fresh selected XML | existing source reactor compiles all AR tests | check true compile while leaving I untouched | missing/skipped finite proof stops source commit |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Compile the isolated durable Session probe without executing it

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: clean 5b764df and exact reviewed Spec/Plan/Java.
- Baseline state: Source81 actual in-memory evidence and current PostgreSQL fixture patterns; no probe.
- Observable outcome: exact JUnit class and unique opt-in are compiled and checked; existing finite adjacent classes pass.
- End state: test-only source and three docs, actual probe/PG/S11 pending.
- Test-first gate: Not applicable. The deliverable itself is an opt-in acceptance test. Runtime RED would violate the approved compile-only scope.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11. Other rules assessed in §4.8.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java`

- Purpose: Prepare exact future persisted native/storage compatibility cases.
- Symbols: proposed_no_tool_pre_model_cut_can_be_tested_under_replacement_session_lease, committed_local_read_response_can_cross_a_replacement_session_lease_without_replay, an_expired_run_cannot_be_reclaimed_by_a_second_run_owner, isolatedSource.
- Repository evidence: AdkNativeResumeSemanticsTest and AdkSessionPostgresTest/current SessionService/ledger.
- Dependencies and consumers: existing JUnit, ADK 1.13.0, JdbcTemplate/Flyway and future isolated operator.
- Why now: the sole candidate code is compiled before evidence and is never executed in this source Step.
- Contract/signature changes: three test methods; JUnit unique probe flag and independent disposable URL.
- Input/output and state mapping: live Run + two Session owners + actual SDK events, same-ID local fake, v1/v2, and stale Run rejection. No runtime output now.
- Error and edge behavior: malformed/non-loopback/incorrect URL fails before connection. Actual test rejects stale lease, preserves persisted prefix (size checked before subList), current Run claim conflict and no false same-Run outcome.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11. Exact test shapes, scoped fixture and current owner.
- Implementation pseudocode:

```java
@EnabledIfSystemProperty(named = "tianji.postgres.resume.probe", matches = "true")
verify unique loopback tianji_it_resume_ URL and fresh primary before applying existing V1–V9
cut after native original user event before fake model, and separately after local response commit
expire exact old Session lease only, acquire new Session lease with unchanged live Run
assert native same ID, no duplicate user or local read, old Session lease fenced, prefix intact
separately expire Run and assert existing SUSPENDED plus replacement claim conflict
compile class and never select it in current source command
```

- Verification contribution: G1 exact actual compile and finite adjacent proof. G2 exact bytecode and isolation.
- After this file: Three distinct I methods compiled and pending user-operated run.

#### File 2 — `CREATE docs/egon/spec/2026-11-05-19-58-adk-durable-resume-probe.md`

- Purpose: Persist the exact reviewed test-only scope.
- Symbols: primary Spec, REQ-001–004.
- Repository evidence: current EGON Source81 and reviewed off-repo bytes.
- Dependencies and consumers: reviewed hash, source proof, canonical Plan.
- Why now: commit exact design alongside exact source.
- Contract/signature changes: docs only.
- Input/output and state mapping: canonical path with existing Source81 predecessor and pending I.
- Error and edge behavior: hash drift or scope change stops stage.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Preserve current AR test-only ownership.
- Implementation pseudocode:

```text
verify the exact reviewed Spec SHA and existing Source81 predecessor
create canonical Spec from exact bytes without adding product authority
validate links and requirement coverage in the final repository layout
```

- Verification contribution: S1 strict Spec.
- After this file: exact review authority in scope.

#### File 3 — `CREATE docs/egon/plan/2026-11-05-20-01-adk-durable-resume-probe.md`

- Purpose: Persist exact reviewed one-step gates.
- Symbols: G1/G2/S1 and separate I.
- Repository evidence: Source81 Plan and current official validators.
- Dependencies and consumers: canonical Spec and locked path set.
- Why now: fixed one commit with scope and source proof.
- Contract/signature changes: docs only.
- Input/output and state mapping: exact one Step, three future I methods and compile-only gate.
- Error and edge behavior: any runtime selector/source change returns to review.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Keep no production or DDL change.
- Implementation pseudocode:

```text
create canonical Plan from reviewed exact bytes and verify its primary Spec hash
run existing strict validators with approved paths and compile-only test selectors
compare staged file set to the four documented paths before commit
```

- Verification contribution: S1 strict Plan.
- After this file: scope lock available.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-adk-durable-resume-probe.md`

- Purpose: Record only actual source and static facts.
- Symbols: exact compile artifact, current finite owning reports and pending I.
- Repository evidence: previous AR native evidence convention.
- Dependencies and consumers: actual G1/G2 results and strict docs.
- Why now: after validation before commit.
- Contract/signature changes: docs only.
- Input/output and state mapping: actual counts and class proof, actual nonexecution evidence, independent future user command.
- Error and edge behavior: missing/skipped finite report or unsubstantiated runtime claim blocks stage.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Evidence stays separate from product/runtime.
- Implementation pseudocode:

```text
record actual compile and fresh selected finite reports, with test counts and command
record bytecode methods, isolated property guard, exact staged paths and immutable SQL
mark all three PostgreSQL methods pending, no current S11 or product recovery acceptance
```

- Verification contribution: S1 actual source review.
- After this file: one source commit ready after all checks.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 G1/G2/S1 only.
- Expected result: actual compiled test, three adjacent finite reports without skip, no probe report, exact four paths.
- Failure returns to: File 1 or fixture setup. Do not execute I to repair a source gate.
- Completion criteria: reviewed source and actual compile/static/finite proof; I remains pending.
- Rollback: one source-only revert.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AdkDurableResumeBoundaryPostgresTest.java`, `docs/egon/spec/2026-11-05-19-58-adk-durable-resume-probe.md`, `docs/egon/plan/2026-11-05-20-01-adk-durable-resume-probe.md`, `docs/egon/evidence/2026-11-05-adk-durable-resume-probe.md`.
- Commit: `test(agent): prepare durable ADK Session boundary probe`.

## 8. Test, Validation, and Quality Gates

G1 compiles all AR test source through the existing offline source reactor and selects only the existing finite classes below. No PostgreSQL, application, listener, provider or probe runtime. Clean before test to avoid stale compiled classes/reports.

```bash
resume_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
resume_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
JAVA_HOME="$resume_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
./scripts/verify-source-reactor.sh -gs "$resume_settings" -s "$resume_settings" -o clean -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=AdkNativeResumeSemanticsTest,AgentRecoveryDispositionEvidenceTest,AdkSessionEventCodecTest
```

G2 proves actual compiled bytecode without running the class.

```bash
ar_target=stellar-enclosure-tianji-agent-runtime/target
class=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AdkDurableResumeBoundaryPostgresTest
"$resume_jdk/bin/javap" -p -v -classpath "$ar_target/test-classes" "$class"
test ! -e "$ar_target/surefire-reports/TEST-$class.xml"
```

Inspect javap for all three exact test methods, exact class-level opt-in, dedicated URL key/prefix, and no shared generic PG system property. Inspect actual owning fresh XML for the three selected existing classes, actual nonzero executed and 0 failures/errors/skips. Report inherited ADK deprecation notes separately. The prepared standalone javac evidence is supplemental, not a source-reactor pass.

S1 run both existing strict docs validators, `git diff --check`, verify all 27 original main SQL and exact four paths, then one reviewed commit. Preserve test class source SHA and proof outside Git.

### User-only later I command, pending

Use a newly created disposable primary database with exact loopback name `tianji_it_resume_<suffix>` and no Tianji schema. User independently supplies the URL through a private variable. Source fixture provisions existing V1–V9 only in that validated isolated DB. Run exactly this class, not a broad suite. The current task does not execute this block.

```bash
: "${TIANJI_RESUME_PROBE_URL:?Provide the approved disposable loopback probe URL}"
JAVA_HOME="$resume_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
./scripts/verify-source-reactor.sh -gs "$resume_settings" -s "$resume_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=AdkDurableResumeBoundaryPostgresTest -Dtianji.postgres.resume.probe=true \
  "-Dtianji.postgres.resume.probe.url=$TIANJI_RESUME_PROBE_URL"
```

No generic test database property. Require actual owning XML with exactly three methods executed and 0 failures/errors/skips, two codec modes per positive method. Failure is diagnostic evidence and leaves the candidate hypothesis open. No real user/provider/Run recovery or S11 proof.

## 9. Migration, Compatibility, Rollout, and Rollback

No production migrations, configurations, code or runtime. Later isolated I applies unchanged existing migrations to its own disposable database only. Revert one source commit. Current production does not change.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | §7.3 | Step 1 | 1 | G1/G2 | compiled native candidate methods |
| REQ-002 | §7.3 | Step 1 | 1 | G1/G2 | compiled expired-Run denial |
| REQ-003 | §7.2/14 | Step 1 | 1–4 | G1/G2/S1 | unique guard and no execution |
| REQ-004 | §14/16/18 | Step 1 | 2–4 | S1 | actual scope, pending I |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- |
| DEC-001 | test-only compile scope | Step 1 | Root review | Root | exact approved lock |
| RISK-001 | no-tool native cut is not yet proved | future I | real probe pending | User | run isolated or defer |
| RISK-002 | generic PG property could activate other suites | File 1/I | separate flag and URL | User | exact selector only |
| RISK-003 | two Session owners mistaken for Run takeover | docs | actual current Run lease invariant | Root | no product/S11 claim |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Prepared exact opt-in test source only. Actual runtime explicitly pending.

### 12.2 Spec consistency

Three named methods, exact guard and no production scope.

### 12.3 Repository executability

One Step and four paths. Existing source reactor and javap. Current gate does not select probe.

### 12.4 Test and release completeness

Actual source compile and selected finite existing reports. Later operator I separate, no assertion RED that would trigger DB.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/5 | existing AR test owner | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | existing ADK/JDBC | None |
| MC-DEP-001 | Applicable | PASS | §5 | no dependency | None |
| MC-NAME-001 | Applicable | PASS | File 1 | semantic exact methods | None |
| MC-VALID-001 | Applicable | PASS | File 1/G2 | exact isolation | None |
| MC-MODEL-001 | Applicable | PASS | File 1 | test-local immutable facts | None |
| MC-CONVERT-001 | Not applicable | N/A | §5 | no production mapper | None |
| MC-LOG-001 | Not applicable | N/A | File 1 | no logger | None |
| MC-BEAN-001 | Not applicable | N/A | §5 | no Bean | None |
| MC-UTIL-001 | Applicable | PASS | §4 | existing JDK/Flyway/JUnit | None |
| MC-JSON-001 | Not applicable | N/A | §5 | no codec change | None |
| MC-TIME-001 | Applicable | PASS | §4.9/File 1 | finite test time | None |
| MC-CONFIG-001 | Not applicable | N/A | §5 | no production profile | None |
| MC-PATTERN-001 | Applicable | PASS | §4 | direct native test | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7 | exactly 4 paths | None |
| MC-TEST-001 | Applicable | PASS | §8/10 | no execution of new I | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | source-only review ready | None |

### 12.6 Final verdict

PASS — Ready for user review

This is a source verification Plan. All actual PostgreSQL methods are still pending.
