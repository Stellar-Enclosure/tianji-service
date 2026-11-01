# 隔离工具权限 LazyBeans 测试夹具

| Field | Value |
| --- | --- |
| Document | `2026-11-01-21-29-isolate-authority-test-fixture.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 21:29 Asia/Shanghai` |
| Updated | `2026-11-01 21:29 Asia/Shanghai` |
| Owner | 原用户授权按蓝图继续未完成工作并分步提交；Root 负责本纠正切片 |
| Repository | `tianji-service` isolated detached baseline worktree |
| Scope | 一项既有 test fixture 隔离纠正；1 test source + Plan + Evidence 共 3 路径 |
| Source Requirement | Step32 final gate 暴露的既有 App negative regression；原用户 continuation authority |
| Baseline Revision | detached `HEAD@174851fd226d74a3adc653ed5ccca83ad39795ae` clean |
| Implements Spec | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；本 Plan 是 Root 的 source-grounded corrective Review，不冒充用户另行接受 API |
| Spec Revision | 2026-11-01 SHA-256 `ee3ba94453e491796786afdfc84dbea0a420a483fec300b8a0f1bfff17167bc5` |
| Effective Specs | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [工具权限签发链](2026-10-31-21-16-tool-authority-issuance-chain.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

未改动 baseline `174851f` 已真实复现 App negative test regression：`TianjiToolAuthorityConfigurationTest.LazyBeans` 是 ordinary nested `@Configuration`，被 `TianjiAgentServerApplication` component scan 自动带入 App test；其 static `BeanFactoryPostProcessor` 将除 tool verifier 外的全部 Bean 设 lazy，隐藏了 runtime 缺依赖应有的启动失败。最小纠正只删除该 nested fixture 的 `@Configuration` 注解和未使用 import；保留 static `@Bean` 以及三个显式 `withUserConfiguration(TianjiToolAuthorityConfiguration.class, LazyBeans.class)` 测试调用。无 production、App test、runtime expectation 或新测试变化。

本纠正先于 Step32 完成，使用独立 baseline worktree 和独立提交。证据是已观察 RED、双类 GREEN、精确三路径 diff、23 SQL/POM 不变；不运行应用、DB、网络或浏览器。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md)
- Status: legacy blueprint；测试可靠性/启动 fail-closed 是 §17 验收基础。
- Revision: SHA-256 `ee3ba94453e491796786afdfc84dbea0a420a483fec300b8a0f1bfff17167bc5`。
- Approval evidence: 原始 continuation authority；本 corrective Plan 在 Root strict/full-read 后才可实施。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) | Legacy / above SHA | §13/§17 | runtime missing-dependency negative gate 必须真实 fail closed |
| Dependency | [工具权限签发链](2026-10-31-21-16-tool-authority-issuance-chain.md) | committed；origin includes `56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963` | authority config tests | `LazyBeans` 起源与三个显式使用点 |

### 2.3 Superseded or excluded content

不改 `TianjiAgentServerApplicationTest`、production Configuration、Bean 行为、runtime dependency policy、Step32 MCP paths、依赖、配置、SQL、权限或历史。`@TestConfiguration` 被明确排除：这些 raw `ApplicationContextRunner` 调用不应依赖 Spring test filtering，fixture 必须通过显式 `withUserConfiguration` 注册。

## 3. Effective Requirements and Acceptance

| Requirement | Source | Effective statement | Observable acceptance | Impact |
| --- | --- | --- | --- | --- |
| `PLAN-REQ-001` | §17 fail-closed | test-only lazy fixture 不得被 App component scan 自动发现 | App missing-runtime-deps negative test 恢复失败预期 | 删除 annotation/import 两行 |
| `PLAN-REQ-002` | authority Plan explicit contexts | 三个 authority tests 仍可显式使用 static lazy postprocessor | AuthorityCfg 11 tests 保持通过 | 保留 class/Bean/三个 callsites |
| `PLAN-REQ-003` | stepwise evidence | 纠正必须独立、可回滚且不混 Step32 | 双类 GREEN、三路径单 commit | Plan/Evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

使用已执行的 unchanged-baseline RED，不新增 mirroring test。先锁定 Plan；实现时仅删除 import 和 annotation；随后以同一双类 selector 验证 App3 与 AuthorityCfg11。Evidence 记录 RED/GREEN 和独立 commit，再由 Root 返回 held Step32 final gate。无并行 source owner。

### 4.2 Test-first strategy

| Behavior | RED evidence | Minimum GREEN |
| --- | --- | --- |
| App runtime missing deps 必须启动失败 | `/tmp/tianji-application-fixture-baseline-red.log`；exit1；App3 one failure | nested fixture 不再 component-scanned |
| explicit lazy authority contexts 保持 | 同一 RED run AuthorityCfg11 all pass | 三个 explicit registrations 原样 |

RED 于 2026-11-01 21:27:31 在 clean detached baseline 实际执行：25.301s，14 tests，1 failure/0 error/0 skip；不是 compiler/fixture setup error，而是未隔离 fixture 的真实行为回归。

### 4.3 Sequential and parallel boundaries

| Unit | Depends on | Parallel | Must not overlap | Reason |
| --- | --- | --- | --- | --- |
| Plan/RED audit | clean baseline | none | source | lock cause/scope |
| two-line correction | Plan GO | none | Step32/main worktree | isolated semantic commit |
| GREEN/evidence/commit | correction | none | all other paths | exact attribution |

### 4.4 Commit boundaries

One corrective commit: `test(agent): isolate lazy authority fixture`。No Step32 path or empty/scaffold commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Element | Necessity/evidence | Direct/reuse alternative | Decision |
| --- | --- | --- | --- |
| remove nested `@Configuration` | actual component-scan leak | explicit existing registration already sufficient | implement |
| replace with `@TestConfiguration` | unnecessary/risky | raw runners explicitly register plain class | reject |
| modify App negative test | existing expectation correct | restore fixture isolation | reject |
| add duplicate regression | existing App negative exact | reuse selector | reject |

### 4.6 Change-unit Dependency Matrix

| Unit | Requirements | RED | Prerequisite | Produces | Step |
| --- | --- | --- | --- | --- | --- |
| fixture isolation | 001/002 | observed dual-class run | Plan GO | scan-safe explicit fixture | 1 |
| evidence/commit | 003 | GREEN/static | isolation | independent corrective commit | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Agent Server security test package | test-only correction | no production/layer change | Step1；ARCH/SCOPE |
| Reuse/capability | existing ApplicationTest + 3 explicit runners | reuse exact regression/fixture | no new test/config class | Step1；REUSE/DEP |
| Naming/model/validation/conversion | no type/model contract change | preserve `LazyBeans`/static Bean | annotation/import only | Step1；NAME/VALID/MODEL/CONVERT |
| Bean/logging/util/JSON/time/config | test `@Configuration` causes scan leak | remove stereotype only | explicit registrations remain | Step1；BEAN/CONFIG/others N/A |
| Business variation/pattern | direct fixture isolation | simple direct change | no pattern | Step1；PATTERN N/A |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| regression | App3 negative + AuthorityCfg11 | actual baseline RED log | exact fit | reuse | None | Step1；TEST |
| lazy fixture registration | three `withUserConfiguration(TianjiToolAuthorityConfiguration.class, LazyBeans.class)` calls at lines 198/201/212 | current test source | explicit registration sufficient | preserve | None | Step1；REUSE |
| scan isolation | remove ordinary stereotype | nested class/source cause | minimal fit | implement | no new type | Step1；BEAN/CONFIG |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | test reliability | no type rename/new carrier | test→docs | preserve class/method names | source/final selector | 1 | N/A |
| Rule 2 | test boundary | no layer input change | three paths | preserve explicit runner behavior | dual-class tests | 1 | N/A |
| Rule 3 | model policy | no POJO/converter | three paths | no model edit | diff | 1 | N/A |
| Rule 4 | Spring class policy | nested test stereotype is defect | test source first | remove only `@Configuration`/import；preserve static Bean | source/contexts | 1 | PASS |
| Rule 5 | utility policy | no utility/import addition | test source | remove unused Spring import | diff | 1 | PASS |
| Rule 6 | JSON policy | no JSON change | none | no wire/codec work | scope | 1 | N/A |
| Rule 7 | configuration policy | test fixture isolation only | test source | no application/profile key | App/Authority tests | 1 | PASS |
| Rule 9 | complexity | direct two-line fix | test source | no ceremonial pattern | review | 1 | N/A |
| Rule 10 | time policy | no time symbol change | none | no time work | scope | 1 | N/A |
| Rule 11 | architecture | exact Agent test profile | all 3 paths | no production/hybrid layer | path diff | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: no time field, calculation, storage, wire, zone, duration or precision symbol changes；MC-TIME-001 evidence-backed N/A。

## 5. Change File Tree

| Op | Path | Current → final responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java` | auto-scanned nested config → explicit-only lazy fixture | 1 |
| CREATE | `docs/egon/plan/2026-11-01-21-29-isolate-authority-test-fixture.md` | strict corrective contract | 1 |
| CREATE | `docs/egon/evidence/2026-11-01-isolate-authority-test-fixture.md` | RED/GREEN/scope/commit evidence | 1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Baseline

Isolated worktree `/Users/mario/SelfProject/Stellar-Enclosure/.tianji-app-gate-baseline-174851f`，detached `174851fd226d74a3adc653ed5ccca83ad39795ae`，pre-Plan clean。Main Step32 dirty worktree is out of scope and must remain untouched。23 SQL/POM/config/production immutable。

### 6.2 Build/test prerequisites

JDK25 exact path；source reactor；both `/tmp/tianji-compat-fix/settings.xml`；ADK content spans false；OTel exporters none。Root alone runs GREEN after source edit。

### 6.3 Immutable decisions

- Remove exactly `org.springframework.context.annotation.Configuration` import and `@Configuration(proxyBeanMethods = false)` annotation。
- Keep `static class LazyBeans`、static `@Bean lazyDefinitions()` body and three explicit callsites unchanged。
- No `@TestConfiguration`、AppTest change、production expectation or new regression test。
- Existing RED is accepted test-first evidence；do not rerun RED by reverting after fix。

### 6.4 Plan Clarifications

| ID | Root Review decision | Evidence | Boundary |
| --- | --- | --- | --- |
| CLAR-001 | separate correction before Step32 finish | origin `56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963` + baseline RED | independent commit |
| CLAR-002 | use existing App negative test | exact failure already observed | no duplicate test |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 隔离 LazyBeans 测试夹具并恢复 App fail-closed gate

- Requirements: `PLAN-REQ-001, PLAN-REQ-002, PLAN-REQ-003`
- Dependencies: observed RED log；strict-valid Plan/GO。
- Baseline state: nested ordinary Configuration is component-scanned；App3 has one behavioral failure；AuthorityCfg11 pass。
- Observable outcome: App negative gate fails startup for missing runtime deps while explicit authority lazy contexts still pass。
- End state: isolated three-path corrective commit；Step32 main worktree unchanged。
- Test-first gate: Required — already satisfied by actual clean-baseline RED described in §4.2。
- Manual Checks: `MC-ARCH-001,MC-REUSE-001,MC-DEP-001,MC-NAME-001,MC-VALID-001,MC-MODEL-001,MC-CONVERT-001,MC-LOG-001,MC-BEAN-001,MC-UTIL-001,MC-JSON-001,MC-TIME-001,MC-CONFIG-001,MC-PATTERN-001,MC-SCOPE-001,MC-TEST-001,MC-BLOCKER-001`
- Literal Rules: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java`
- Purpose: prevent automatic App component discovery while retaining explicit lazy test contexts。
- Symbols: Configuration import/annotation；`LazyBeans` class/static Bean；three explicit runner callsites。
- Repository evidence: lines 198/201/212 explicit registration；line222 ordinary annotation；baseline RED/App cause。
- Dependencies and consumers: App component scan and authority context runners。
- Why now: Step32 final gate cannot complete over a known preexisting regression。
- Contract/signature changes: none；two source lines removed only。
- Input/output and state mapping: App scan no longer registers fixture；explicit runner registration still installs identical postprocessor。
- Error and edge behavior: missing runtime deps remain startup failure；lazy authority contexts preserve eager verifier tests；no hidden test filter dependence。
- Standards impact: `MC-ARCH-001/MC-REUSE-001/MC-BEAN-001/MC-CONFIG-001/MC-SCOPE-001/MC-TEST-001`。
- Literal rule enforcement: `Rule 4,Rule 5,Rule 7,Rule 11`。
- Implementation pseudocode:
```java
remove import org.springframework.context.annotation.Configuration
remove @Configuration(proxyBeanMethods = false) from nested LazyBeans
preserve static class, static @Bean body, and all three explicit withUserConfiguration callsites byte-for-byte
```
- Verification contribution: dual-class GREEN and source diff。
- After this file: App fixture is scan-isolated；explicit authority contexts remain runnable。

#### File 2 — `CREATE docs/egon/plan/2026-11-01-21-29-isolate-authority-test-fixture.md`
- Purpose: save strict V5 corrective contract and actual RED boundary。
- Symbols: requirements、3 paths、commands、17MC/10Rules。
- Repository evidence: baseline/source/origin Plan/RED log。
- Dependencies and consumers: Root full-read/strict/hash before source edit。
- Why now: separate correction requires explicit review and commit scope。
- Contract/signature changes: docs only。
- Input/output and state mapping: observed defect → two-line implementation/evidence。
- Error and edge behavior: drift/strict failure blocks；no user-approval fabrication。
- Standards impact: all `MC-ARCH-001/MC-REUSE-001/MC-DEP-001/MC-NAME-001/MC-VALID-001/MC-MODEL-001/MC-CONVERT-001/MC-LOG-001/MC-BEAN-001/MC-UTIL-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001/MC-PATTERN-001/MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: all `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```text
Full-read baseline source, origin Plan and observed RED log.
Run strict validator on exact destination; repair without widening scope; record SHA.
Permit the two-line source edit only after Root Review GO.
```
- Verification contribution: strict PASS/SHA。
- After this file: executable corrective Review Plan。

#### File 3 — `CREATE docs/egon/evidence/2026-11-01-isolate-authority-test-fixture.md`
- Purpose: record actual RED/GREEN、two-line diff、scope and corrective commit。
- Symbols: baseline/origin/PlanSHA/log/counts/path/SQL/POM/runtime limits。
- Repository evidence: existing Evidence convention and actual baseline log。
- Dependencies and consumers: actual GREEN/static/commit outputs；Root/user review。
- Why now: Step32 must distinguish preexisting fix from MCP change evidence。
- Contract/signature changes: docs only。
- Input/output and state mapping: commands/logs/diff → reproducible evidence。
- Error and edge behavior: no fabricated rerun、counts or runtime claims；compiler/setup failures separate。
- Standards impact: `MC-SCOPE-001/MC-TEST-001/MC-BLOCKER-001`。
- Literal rule enforcement: `Rule 1,Rule 2,Rule 3,Rule 4,Rule 5,Rule 6,Rule 7,Rule 9,Rule 10,Rule 11`。
- Implementation pseudocode:
```markdown
Record detached baseline, origin commit, Plan SHA and observed RED command/log/counts.
Record GREEN command/log/counts, exact removed lines, three-path status and immutable SQL/POM checks.
Record corrective commit and state that no app, DB, network, browser or Step32 source ran here.
```
- Verification contribution: requirement/MC closure and Step32 handoff boundary。
- After this file: independent corrective evidence is complete。

- Validation working directory: isolated baseline worktree。
- Verification command: exact dual-class command in §8。
- Expected result: 14 tests pass；0 failure/error/skip；App3 and AuthorityCfg11；no other path change。
- Failure returns to: File1 for behavior；File2 for Plan；File3 for evidence；user only on material scope drift。
- Completion criteria: strict PASS、GREEN、three paths、two source-line deletion、23 SQL/POM unchanged、separate commit。
- Rollback: revert three-path corrective commit；no data/config migration。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/security/TianjiToolAuthorityConfigurationTest.java`, `docs/egon/plan/2026-11-01-21-29-isolate-authority-test-fixture.md`, `docs/egon/evidence/2026-11-01-isolate-authority-test-fixture.md`。
- Commit: `test(agent): isolate lazy authority fixture`

## 8. Test, Validation, and Quality Gates

| Test ID | Scope |
| --- | --- |
| `TEST-001` | already-observed unchanged-baseline RED |
| `TEST-002` | corrective dual-class GREEN |
| `TEST-003` | strict/path/diff/SQL/POM/commit boundary |

Canonical RED command already executed and logged at `/tmp/tianji-application-fixture-baseline-red.log`; GREEN uses the same selector with a distinct log:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -B -ntp -DskipTests=false -Dtest=TianjiAgentServerApplicationTest,TianjiToolAuthorityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none > /tmp/tianji-application-fixture-isolation-green.log 2>&1
```

| Gate/order | Working directory | Command/method | Scope | Expected result | Failure returns to | Boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 | isolated worktree | recorded baseline command/log | dual class | exit1；14 tests/1 failure | source cause | observed module RED |
| TEST-002 | isolated worktree | exact command above | dual class | exit0；14/0/0/0 | File1 | module GREEN |
| TEST-003 | isolated worktree | `git status --short`; `git diff --check`; strict validator | exact3 | PASS/no extras | stop | static/docs |
| immutable | isolated worktree | diff baseline for `*.sql`, `pom.xml`, `application.yml` | excluded paths | empty | stop | static |
| live | user-controlled | not authorized | runtime | not run | later | live |

No `scripts/work` harness；existing regression is exact。Do not rerun RED after fix by restoring the defect。

## 9. Migration, Compatibility, Rollout, and Rollback

N/A migration/dependency/config/permission。Test-only stereotype removal has no production artifact/API/runtime rollout。Explicit raw runners preserve behavior。Rollback separate commit only；do not edit history or mix Step32。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step/files | Tests/evidence |
| --- | --- | --- |
| PLAN-REQ-001 | 1；File1 | App RED/GREEN |
| PLAN-REQ-002 | 1；File1 | AuthorityCfg11 GREEN/source callsites |
| PLAN-REQ-003 | 1；all3 | strict/static/commit Evidence |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Evidence/action | Status |
| --- | --- | --- | --- |
| R1 | removing annotation could break raw runners | three explicit registrations + AuthorityCfg11 | closed by GREEN |
| R2 | `@TestConfiguration` filter mismatch | explicitly rejected | closed |
| R3 | Step32 contamination | isolated worktree/exact3 | closed |
| B1 | baseline drift | detached exact SHA | closed |

No open design blocker。This correction is Root-derived Review under original continuation authority。

## 12. Review and Acceptance

### 12.1–12.4 Fidelity/executability

Plan preserves correct App negative expectation and all authority fixture code except the auto-scan stereotype。Normal explicit lazy context remains；failure App context again sees missing deps；edge raw runner avoids test-filter semantics。No production/runtime changes。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | test package/exact3 | production architecture unchanged | None |
| MC-REUSE-001 | Applicable | PASS | existing dual-class tests/explicit runners | no duplicate test | None |
| MC-DEP-001 | Not applicable | N/A | no POM/dependency path | dependency surface remains unchanged | None |
| MC-NAME-001 | Not applicable | N/A | no symbol rename | names unchanged | None |
| MC-VALID-001 | Not applicable | N/A | no input boundary | unchanged | None |
| MC-MODEL-001 | Not applicable | N/A | no model | unchanged | None |
| MC-CONVERT-001 | Not applicable | N/A | no conversion | unchanged | None |
| MC-LOG-001 | Not applicable | N/A | no logging | unchanged | None |
| MC-BEAN-001 | Applicable | PASS | annotation removed/static Bean explicit | fixture isolated | None |
| MC-UTIL-001 | Applicable | PASS | unused import removed | no utility | None |
| MC-JSON-001 | Not applicable | N/A | no JSON | unchanged | None |
| MC-TIME-001 | Not applicable | N/A | §4.9 reason | no time | None |
| MC-CONFIG-001 | Applicable | PASS | App/runner context tests | test config isolation exact | None |
| MC-PATTERN-001 | Not applicable | N/A | simple two-line fix | no pattern | None |
| MC-SCOPE-001 | Applicable | PASS | exact3/two source lines | minimum change | None |
| MC-TEST-001 | Applicable | PASS | actual RED + planned GREEN | exact regression | execute GREEN |
| MC-BLOCKER-001 | Applicable | PASS | §11/all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

Planning PASS only。Root must strict/hash/full-read and confirm edit GO。

## Java / CQE contract review

No model、validation、converter、persistence、DDL、uniqueness、CQE/Event change。Exact Agent test architecture preserved。

## Backend generation and dependency evidence

No DDL/CRUD/dependency/profile；generator/manifest/SQL journal N/A。Any production/POM/SQL/config path blocks commit。
