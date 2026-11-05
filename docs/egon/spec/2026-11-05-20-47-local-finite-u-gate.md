# 天机本地有限 U 门禁

| Field | Value |
| --- | --- |
| Document | `2026-11-05-20-47-local-finite-u-gate.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Tooling` |
| Complexity | `Simple` |
| Complexity Drivers | `None` |
| Created | `2026-11-05 20:47 Asia/Shanghai` |
| Updated | `2026-11-05 20:47 Asia/Shanghai` |
| Owner | 天机平台决策人 |
| Repository | `tianji-service` |
| Scope | T4-05 local finite Service U source gate |
| Change Surface | 一个 Python 本地门禁、一个有限单测和已有严格 Surefire helper 的最小共享 |
| Affected Chapters | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Source Requirement | [验收矩阵和操作者手册](../evidence/2026-11-05-release-acceptance-matrix.md) 的精确 17 类与 CI 就绪要求 |
| Baseline Revision | `tianji-service 1b5700ae9ba409c948c4d1a261d7b002de2b4381` |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [验收矩阵](../evidence/2026-11-05-release-acceptance-matrix.md#安全离线复跑) 安全离线复跑；[离线证据门禁](2026-11-05-16-56-expense-release-evidence-gate.md) §7.3/§14 的现有锁/报告语义 |
| Related Specs | `None` |
| Related Plans | `None` |

## 1. Summary

用现有 source reactor 在已锁定的五个干净本地仓库上执行手册列出的 17 个有限测试类。通过每次新 nonce 和固定 Surefire 文件名/属性验证本次报告。仅输出脱敏摘要和 SHA。没有远程工作流、真实数据库、监听器、供应商或发布权限。

## 2. Background and Current State

### 2.1 Business and user context

操作者需要能独立复跑有限 U 并证明报告归属本次执行。手册已有精确选择器和实测历史报告，但依靠人工判断新鲜度。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact source | Observed fact | Verification limit |
| --- | --- | --- | --- | --- |
| EVD-001 | Source and retained proof | [operator runbook](../evidence/2026-11-05-release-acceptance-matrix.md), existing `verify-source-reactor.sh` | current 17 selectors and historical 220 passed. Wrapper assembles five local repositories | wrapper lacks source lock/report freshness |
| EVD-002 | Actual historical XML and pinned artifact | 17 existing Surefire 3.2.5 reports, `AbstractSurefireMojo/SurefireProperties/StatelessXmlReporter/WrappedReportEntry` bytecode | all existing user -D flags in properties. Filename suffix `-nonce`, XML owner `FQCN(nonce)`. Current factory reports six same-named dynamic rows | new run nonce awaits actual integration proof |
| EVD-003 | Static source | `scripts/verify-release-evidence.py#verify_checkout,run_git,strict_json,parse_xml,summarize_report` | existing bounded parser, report validation and exact clean five-repo Git lock | expense bundle contract is separate and must stay strict |
| EVD-004 | Static source | `mvnw/.mvn/wrapper/maven-wrapper.properties` and actual local settings shape | wrapper cached distribution preflight needed before -o, settings Maven namespace and localRepository/interactiveMode only | no credential/remote config or cache fetching authorized |
| EVD-005 | Local scratch | Python pure tests and retained historical XML in-place audit | 6 new plus 7 legacy pass. All 17 historical XMLs valid for historical shape but fail required nonce | no Maven/real gate executed |

### 2.3 Problem statement and gap

旧 XML 计数正确仍可能属于不同运行。门禁需要一条固定调用、可核对的 clean source lock 和本次报告证据。不同于现有 expense evidence verifier，该工具运行本地有限测试，随后只报告本次 U。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current chain | Data owner | Boundary |
| --- | --- | --- | --- |
| existing U run | operator → source reactor → maven wrapper → Surefire | five local source owners | no prior digest binding |
| existing offline verifier | private expense index → strict Git/XML/report checks | existing local artifact reader | I/E bundle contract remains |
| desired local U | fixed flags+nonce → exact reports → redacted summary | local U tool | no remote or release claim |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing Service scripts only, no new Java project or archetype scope.

## 3. Goals and Non-goals

### 3.1 Goals

固定本地 17 类 U 命令、五个 Git HEAD 锁、本次 nonce 及 owner XML 检查。保护日志/报告内容并生成可复核摘要。

### 3.2 Non-goals

无远程 CI/workflow、密钥、联网 bootstrap、任意 Maven 参数、数据库、监听器、应用、供应商、浏览器、I/E/L、生产 runtime 或发布签发。无复制 raw Surefire XML 或输出完整 Maven log。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Delta/invariant | Required treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| local CLI, orchestration and report reader | Affected | EVD-001–005 | fixed source, settings, nonce and summary | exact CLI and tests | §7, §8, §9, §10, §13, §14, §15, §16, §17, §18 |
| Maven wrapper/runtime/CI | Unchanged | existing reactor/cached wrapper | use existing approved binaries | context | §8 |
| DB | Unchanged | no runtime selector | no schema change | scope only | §11 |
| Web | Not applicable | Service-only | no UI | scope only | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | fixed local 17 safe classes | Must | exact allowlist and no arbitrary Maven args | EVD-001 |
| REQ-002 | clean five-repo and offline toolchain | Must | pre/post exact full HEADs, read-only inputs, JDK25, credential-free settings and cached wrapper | EVD-003/004 |
| REQ-003 | fresh strict owner reports | Must | nonce filename+properties, exact owner and selectors, actual nonzero count, no fail/error/skip/flaky, exact known DynamicTest multiplicity | EVD-002/003 |
| REQ-004 | redacted source-only summary | Must | digest/count/identity only, no raw paths or report content, no release/remote claim | EVD-005 |

### 4.2 Use-case analysis

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | operator of locked local source | verify finite U | local CLI | approved checked-out source and local cached dependencies | EVD-001/004 |

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | execute and bind current U | ACTOR-001 | local Maven/Surefire | explicit CLI | five exact clean HEADs, cache/settings | 17 fresh reports verified | dirty, wrong lock/cache/report, reactor failure | redacted summary only | REQ-001–004 | CLI-001 | TEST-001/002/003 |

## 5. Constraints, Assumptions, and Decisions

DEC-001 reuse only current exact Service U list. DEC-002 named 128-bit random nonce in both CLI property and Surefire suffix. DEC-003 separate credential-free offline inputs, no inherited Maven/Java test flags. ASM-001 operator maintains trusted installed wrapper/cache contents during the run. Missing wrapper cache fails preflight. The goal is reproducible local verification, not hostile local checkout isolation.

## 6. Project Technology Context

Python stdlib existing tools, POSIX, JDK25 and pinned current Surefire 3.2.5 behavior. No dependency and no Java source.

### 6.1 Java architecture profile and capability baseline

No Java target. Preserve existing source reactor and original evidence CLI. Share only strict report helper/parser and read-only Git lock.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | No | Python only | no Java names | scripts | scope | N/A |
| Rule 2 | No | existing Java | exact Python CLI/inputs | CLI-001 | TEST-001 | N/A |
| Rule 3 | No | no Java POJO | no change | scripts | scope | N/A |
| Rule 4 | No | no Spring | fixed safe diagnostics | CLI | TEST-001 | N/A |
| Rule 5 | No | Python stdlib | no new Java utility/dependency | scripts | scope | N/A |
| Rule 6 | No | existing parser | no Java wire | CLI JSON | TEST-001 | N/A |
| Rule 7 | No | no profile edits | explicit private settings path | CLI | TEST-001 | N/A |
| Rule 9 | No | direct local tool | reuse strict helper | scripts | TEST-001 | N/A |
| Rule 10 | No | UTC Python datetime | observed start/end only | §6.3 | TEST-001 | N/A |
| Rule 11 | Yes | existing scripts and source owner | no persistence, event or runtime layer | §8 | TEST-003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | summary startedAt/finishedAt | Occurred instant | Python aware datetime | Not persisted | UTC Z | Not applicable: no business calendar | explicit UTC | observed execution window, no timezone fallback | microseconds, actual UTC wall clock, nonce independently proves freshness | no existing contract change | TEST-001 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| current reactor | Keep | REQ-001 | direct run | source lock/report missing | same process | Keep |
| local U wrapper | Add | REQ-001–004 | manual validation | report replay, scope and raw output | one fixed subprocess and 17 reads | Add |
| strict XML helper | Reuse | REQ-003 | new parser | duplication | existing default behavior preserved | Keep |
| remote workflow/artifact upload | Exclude | REQ-004 | local source gate | missing authority | none | Remove |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| manual U | zero expected | operator-managed | current local Surefire | stale output/identity mix | current test execution |
| selected U | zero expected, cache required | locked/verified | current local Surefire | exact nonce, expected owner and pre/post HEADs | redacted current-source proof |

### 7.1 System Architecture Design

Local CLI → existing clean Git check and cache/setting verification → fixed existing reactor → expected Surefire XMLs → existing strict shared validator → redacted JSON.

### 7.2 High-Level Design

Five explicit checkout lock SHAs, source root, settings/JDK/wrapper-cache paths. Build a closed environment, fixed command, fresh private report suffix and nonce. No output until every report and final source check succeeds.

### 7.3 Detailed Design

Reuse existing five-repo lock validator before and after. Validate exact settings namespace and allowed leaf structure (absolute existing localRepository and optional false interactiveMode). Verify JDK25 and cached wrapper distribution required by tracked Maven config before invoking the fixed offline command. No install/download step. New process inherits only explicit safe variables, uses owner-only umask and discards raw stdout/stderr. Check existing target paths are real, no report collision. Verify 17 exact module/FQCN names, exact suffix and nonce properties, test selector/flags, exact basedir and JDK25. Admit only the known six DynamicTest rows for the exact factory and owner. Validate remaining counts strictly. No raw report copy, only digests and counts.

## 8. Package Structure and Code File Tree

| Operation | Exact path | Purpose |
| --- | --- | --- |
| Create | `scripts/verify-local-release-u.py` | closed local runner, lock/cache validation, exact report proof and output |
| Create | `scripts/test_verify_local_release_u.py` | pure mock subprocess/Git and strict report scenarios |
| Modify | `scripts/verify-release-evidence.py` | share existing report parser/count validation with opt-in suffix/properties/repetitions, preserve old callers |

Three canonical documents, six total paths. No runner wrapper, POM, CI, dependency, runbook, DB or policy edit.

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Change/necessity verdict | Name/purpose | Kind | API style/CQE role | Consumer | Owner | Method + URL / GraphQL field / symbol / topic | Operation ID/schema source | Input | Output | Auth/tenant | Error model | Idempotency/version | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CLI-001 | New/Add | run and verify finite U | CLI | local operational read/proof | ACTOR-001 | Service scripts | `python3 -B scripts/verify-local-release-u.py` | local fixed profile | five required path flags | redacted JSON | owner-local source/toolchain | fixed exit+diagnostic | fresh run nonce | REQ-001–004 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CLI-001 — Local finite U

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New local tool |
| Independent consumer goal | Run and verify exactly current finite Service U |
| Parameter ownership and derivation | operator chooses explicit lock and installed tools, source selects fixed classes and generates nonce |
| Direct/no-new-interface alternative | manual source wrapper lacks lock and current report verification |
| Caller use of result | review sanitized actual summary, no release activation |
| Round trips and failure points | one bounded offline Maven invocation and 17 stable file reads |
| Verdict | Add for REQ-001–004 |

##### Identity and purpose

Standalone Python CLI in existing Service scripts. It is not a release policy verifier or remote CI.

##### Request parameters

| Flag | Exact value |
| --- | --- |
| --checkout | absolute canonical Service root, fixed four siblings resolved from parent |
| --source-lock | absolute regular JSON file, exactly tianjiService/components/shoubing/jianshen/tianshu SHA40 keys |
| --settings | absolute regular credential-free Maven XML, optional current namespace, localRepository and optional interactiveMode=false only |
| --java-home | absolute existing JDK25 |
| --maven-user-home | absolute existing Maven cache including the tracked wrapper distribution |

All five required once. No other flags, user nonce, test selector, environment override or Maven args.

##### Success response

Exit 0, one JSON line. Fixed profile/status, fresh runId, observed UTC start/finish, five sourceHeads, sourceLockSha256, settingsSha256, class/test totals, and 17 entries with module/className/tests/sha256. No path, test-case names, raw reports or subprocess output.

##### Error responses

Exit 64 usage, 65 rejected input/lock/report, 66 unavailable file/process, or 1 fixed reactor failure. Stderr exact fixed short code. No partial stdout or underlying message/path/value.

##### Interface logic for frontend and consumers

No frontend. Operator supplies reviewed current inputs, reads exact summary after complete current U, and independently controls deployment or I/E/L.

##### Compatibility and verification

Existing expense verifier legacy caller remains strict. TEST-001 pure coverage, TEST-002 retained read-only check, TEST-003 authorized local actual run.

## 10. POJO and Data Model Design

No Java model. Lock is exact five-head JSON. Report tree validated from stable bounded bytes. Dynamic exception is only six rows of `governed_process_injection_corpus` in exact AdkGovernedProcessLoopTest. Summary as §9.

## 11. Database Design

Unchanged. No DB test selector, migration, schema or runtime.

## 12. Frontend Page Design

Not applicable. Local Service tool.

## 13. Design Patterns and Architecture Principles

Direct local runner and existing strict verifier helpers. No new workflow, testkit, daemon or cache.

## 14. Test Design

| ID | Gate | Proof |
| --- | --- | --- |
| TEST-001 | pure new and legacy unittest | exact 17/220 fixture, redaction, source drift, bad settings/cache, nonce/owner/duplicates/flaky, all seven existing expense tests |
| TEST-002 | read-only retained actual XML | all 17 exact owners and 220 legacy counts. Each fails with new required nonce. No report copies |
| TEST-003 | Root-authorized local actual execution | clean exact five heads, fresh 17 suffixed reports with current nonce, actual counts and safe summary. No other runtime |

## 15. Non-functional and Cross-cutting Design

Bound JSON/settings/XML, command time and stable filesystem reads. Suppress raw subprocess and XML. Short exact status. No network bootstrap if approved cached distribution is present, reject missing cache before invocation.

## 16. Compatibility, Migration, Rollout, and Rollback

No migrations or live changes. Existing expense CLI unchanged for its default strict caller. Gate usable locally when approved source, cache and settings available. Single source commit reversible.

## 17. Alternatives and Decisions

Reject manual mtime-only proof because XML lacks a reliable start timestamp. Reject clean/deletion for freshness because fresh nonce filenames preserve old artifacts. Reject remote workflow/automatic installation.

## 18. Risks and Open Questions

The fresh actual gate must validate suffix/property behavior. Historical evidence proves 17/220 only for its own run. Stable local installed cache and checked-out source are prerequisites. This gate does not claim full T4-05 release approval.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9 | TEST-001/003 |
| REQ-002 | UC-001 | §7.3/9 | TEST-001/003 |
| REQ-003 | UC-001 | §7.3/10 | TEST-001/002/003 |
| REQ-004 | UC-001 | §9/15 | TEST-001/003 |

## 20. Review and Acceptance

### 20.1 Original-request fidelity

Exact local finite U only.

### 20.2 Repository and technical fidelity

Actual source, reports and pinned artifact verified. Pure scratch tests completed, actual new gate pending.

### 20.3 Cross-section consistency

Fixed inputs and selectors, current owner proof, redacted output and independent runtime.

### 20.4 Relationship and effective-design review

Existing operator matrix and expense verifier remain effective.

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | existing scripts owner | None |
| MC-REUSE-001 | Applicable | PASS | §7/8 | existing strict parser/reactor | None |
| MC-DEP-001 | Applicable | PASS | §6 | no dependency | None |
| MC-NAME-001 | Not applicable | N/A | §8 | no Java | None |
| MC-VALID-001 | Applicable | PASS | §7.3/9 | strict input/report | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | no Java model | None |
| MC-CONVERT-001 | Not applicable | N/A | §8 | no converter | None |
| MC-LOG-001 | Applicable | PASS | §9/15 | redacted only | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | no Spring | None |
| MC-UTIL-001 | Not applicable | N/A | §6 | no Java utility | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | closed inputs/output | None |
| MC-TIME-001 | Applicable | PASS | TIME-001 | UTC observations | None |
| MC-CONFIG-001 | Not applicable | N/A | §8 | no profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §7/13 | direct reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8 | local only | None |
| MC-TEST-001 | Applicable | PASS | §14 | exact finite and live separation | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | ready for review | None |

### 20.6 Final verdict

PASS — Ready for user review

Actual new local source-reactor gate and remote/deployment acceptance have not been executed in this review.
