# T4-05 天机单仓发布工具 CI

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One manual job, one own-repository fetch, two existing stdlib suites |
| Created | 2026-11-05 23:09 Asia/Shanghai |
| Updated | 2026-11-05 23:09 Asia/Shanghai |
| Owner | Tianji Service release tooling |
| Repository | tianji-service |
| Scope | 单仓发布证据工具的可选 CI 源码 |
| Change Surface | 一份手动 workflow 和审阅文档 |
| Affected Chapters | §7, §8, §9, §14, §15, §16 |
| Source Requirement | [主计划 §19 T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 75d0c15a2ff9bef03be80e7cd7584b2bcbe8401e |
| Amends | None |
| Supersedes | None |
| Depends On | [Local finite U gate](2026-11-05-20-47-local-finite-u-gate.md#9-interface-definitions) |
| Related Specs | None |
| Related Plans | [Repository tooling CI](../plan/2026-11-05-23-10-repo-release-tooling-ci.md) |

## 1. Summary

为当前天机 Service 的两个发布证据工具测试提供一个手动 GitHub Actions 工作流。仅从本仓当前事件 SHA 取五个现有脚本与策略文件，在空环境里运行 13 个 Python 标准库测试。Java U 的五仓锁定和真实 I/E/L 各自保留现有验收入口。

## 2. Background and Current State

| Evidence ID | Classification | Exact source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Existing source | scripts/test_verify_release_evidence.py, scripts/verify-release-evidence.py, scripts/release-evidence-policy-v1.json | 7 个标准库测试，用合成策略证据覆盖错误、陈旧、缺失、跳过、隔离与候选绑定。 |
| EVD-002 | Existing source | scripts/test_verify_local_release_u.py, scripts/verify-local-release-u.py | 6 个标准库测试，通过合成 XML、fake Git/runner 验证五仓锁与本次报告绑定。 |
| EVD-003 | Local metadata | Service 75d0c15a, Web a5e7c154; Components 87af09fe, Shoubing e423428e, Jianshen 3afec386, Tianshu b1c607f4 | Service/Web 没有记录的 remote refs，四个 sibling HEAD 均不在记录的 remote refs。未读取远端发布或可见性。 |
| EVD-004 | Local repository | tianji-service remote.origin URL and git ls-files .github | 配置归属 GitHub.com/Stellar-Enclosure/tianji-service，当前没有已跟踪 workflow。 |
| EVD-005 | Local isolated proof | Git archive at EVD-003 and CPython 3.14.6 | 恰好五文件，无 .git 或 sibling，env -i 和 Python isolated mode 下 7+6 测试实际通过。 |
| EVD-006 | Existing acceptance | [Local finite U evidence](../evidence/2026-11-05-local-finite-u-gate.md) | 已通过的本地 17 类 Java U 需要五仓、JDK25 与 Maven 缓存。与本次工具自测作用不同。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 当前仓库新增独立手动工作流。无 Maven 或 Archetype 生成、示例或模块变化。

## 3. Goals and Non-goals

提供标准 GitHub 作业可运行的发布工具回归。保持既有发布门禁、Java U、I/E/L、S12 和部署授权边界。托管执行、分支保护、发布状态和远程 sibling refs 有独立证据要求。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Manual repo workflow | Affected | EVD-004/005 | Add exact own-source job, two tooling suites | Activation, security boundary and deterministic tests | §7, §8, §9, §14, §15, §16 |
| Existing Python tools | Context-only | EVD-001/002 | Read exact tracked bytes and run existing fixtures | Preserve behavior and policy bytes | §8/§14 |
| Maven, App, DB, credentials, CI authority | Context-only | EVD-003/006 | Full Java U and runtime paths stay under current owner gates | Document separate authority | §11/§16/§18 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Bind a manually chosen own-repo commit | Must | Exact event, host, repository and SHA40 verified before network; own authenticated fetch once and exact commit afterward | EVD-003/004 |
| REQ-002 | Limit checkout authority and staged source | Must | Job-issued contents:read token only for one HTTPS fetch, redirects disabled, no credential persisted, exactly five regular Git blobs and byte match | EVD-001–005 |
| REQ-003 | Run only exact nonzero tooling suites | Must | Python 3.10+, env -i and isolated no-bytecode mode, exactly 7 and 6 successfully executed, no skips/failures | EVD-001/002/005 |
| REQ-004 | Keep this lane independent from real release acceptance | Must | No sibling fetch, dependencies, Maven, private evidence or app, no new workflow triggers or publication. Source-local and hosted evidence clearly separated | EVD-003/006 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | Current repository operator | Dispatch reviewed source tooling check | Existing repository Actions UI/CLI | GitHub repository permission, no Tianji runtime identity | EVD-004 |
| ACTOR-002 | GitHub-hosted job | Fetch exact commit and run two suites | Workflow event context | Existing contents:read job token, no custom secret | EVD-004/005 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Validate release tooling from own source | ACTOR-001 | ACTOR-002 | workflow_dispatch | Own repository workflow enabled, ephemeral runner | 13 successful tooling fixture tests | Wrong context/archive/test closes job | No publication or source changes | REQ-001–004 | CI-001 | TEST-001–004 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | Manual workflow_dispatch only, no push/PR/deployment trigger. |
| DEC-002 | GitHub.com/Stellar-Enclosure/tianji-service and exact event GITHUB_SHA. No mutable upstream branch input. |
| DEC-003 | One built-in job token with contents:read for fetch. Use no custom secrets, credential store, third-party action, upload or dependency installation. |
| DEC-004 | Keep explicit suite counts 7 and 6; intentional future fixture-count changes update this gate with their own review. |
| ASM-001 | Actual repository visibility, workflow publication, hosted runner availability and contents access require separate owner verification. The job assumes the declared Ubuntu runner provides Bash, Git, tar and Python 3.10+. |

## 6. Project Technology Context

GitHub Actions YAML, runner Bash and Git, tar and CPython 3.10+ standard library. Current local version is 3.14.6. No Java scope or new dependencies. The earliest CPython requirement follows existing Path.hardlink_to in release evidence fixtures.

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | No | Workflow only | No Java names | §8 | TEST-001 | N/A |
| Rule 2 | No | Workflow context | Existing boundary checks in Bash/Python | §9 | TEST-001 | N/A |
| Rule 3 | No | No Java POJO | Preserve existing | §10 | TEST-001 | N/A |
| Rule 4 | No | No Spring | Safe local diagnostics | §15 | TEST-001 | N/A |
| Rule 5 | No | Python stdlib | No Java utility | §6 | TEST-003 | N/A |
| Rule 6 | No | Existing Python fixtures | No JSON contract change | §10 | TEST-003 | N/A |
| Rule 7 | No | No Spring profiles | Workflow only | §8 | TEST-001 | N/A |
| Rule 9 | No | Simple local workflow | Direct composition | §13 | TEST-001 | N/A |
| Rule 10 | No | No business times | Existing evidence scripts retained | §6.3 | TEST-003 | N/A |
| Rule 11 | Yes | Existing Service tooling | Preserve current architecture and ownership | §8 | TEST-004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: The hosted job timeout is an infrastructure limit. Existing release evidence audit windows and timestamps keep their contract. No affected application, wire or persisted time field.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Manual workflow | New | REQ-001–004 | Execute two commands locally | No repository job can execute the tools today | One manual job and temporary files | Add |
| Existing tooling tests and policy | Keep | REQ-003 | Add new helper/package | Existing finite fixtures already prove verifier behavior | No change | Keep |
| Native exact fetch and archive | Add | REQ-001/002 | General checkout or sibling reactor | Need one own commit with only five regular files passed to Python | One HTTPS fetch and one local archive | Add |
| Cross-repo Java U and publication | Keep | REQ-004 | Add current sibling refs to workflow | Local evidence cannot establish publication/credential compatibility | Independent future review | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct local | 0 new | Local tests | Existing scripts | Local environment | Current self-test |
| Selected design | One own Git fetch | Manual action status | Git source commit and fixed tests | Fail closed on checkout, archive or tests | Repo-local automation source |

### 7.1 System Architecture Design

Manual event → one Git fetch of own SHA → archive five regular file blobs → empty-env Python 7 + 6 → standard job result.

### 7.2 High-Level Design

A single five-minute job on ubuntu-24.04 keeps authentication, extraction and test steps separate. It reuses current scripts without running their real Maven or deployment pathways.

### 7.3 Detailed Design

1. Require exact workflow_dispatch event, server URL https://github.com, owner/repository Stellar-Enclosure/tianji-service, lowercase SHA40 and nonempty job token. Create a temporary repo with an empty Git template and disabled global/system Git configuration. Clear inherited Git override variables and token aliases. Pass the job token's masked Basic header only via command-scoped Git configuration to one exact HTTPS fetch. Disable redirect/submodule/tag expansion. No persistent remote or credential configuration. Verify FETCH_HEAD commit equals the event SHA.
2. Validate file mode regular and type blob for each of five exact paths. Archive that commit and paths to an isolated root. Reject absent/symlink files or Git blob digest mismatch. Credentials are outside archive and no token env is assigned to this step.
3. Start Python with env -i, minimal PATH/locale, -I and -B. Require Python ≥3.10, exact suite filenames and counts (7, 6), valid test discovery and exact non-skipped run totals with no expected failure, error or failure. Print only suite status and fixed success. No install or raw private evidence collection.
4. No cross-repo checkout, no .git in test source, no Maven reactor, no settings/cache restore, no artifact upload or hosted run in source review.

## 8. Package Structure and Code File Tree

| Action | Exact path | Responsibility |
| --- | --- | --- |
| Create | .github/workflows/release-tooling.yml | One manual, exact same-repo tooling check |
| Create | docs/egon/spec/2026-11-05-23-09-repo-release-tooling-ci.md | Scope and security contract |
| Create | docs/egon/plan/2026-11-05-23-10-repo-release-tooling-ci.md | Source-first finite plan |
| Create | docs/egon/evidence/2026-11-05-repo-release-tooling-ci.md | Local evidence and hosted boundary |

Archive path allowlist: scripts/release-evidence-policy-v1.json, scripts/verify-release-evidence.py, scripts/verify-local-release-u.py, scripts/test_verify_release_evidence.py, scripts/test_verify_local_release_u.py. All five are existing files.

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Owner | Requirements |
| --- | --- | --- | --- | --- |
| CI-001 | Existing GitHub manual workflow protocol | release-tooling | Tianji Service | REQ-001–004 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CI-001 — Repository release tooling

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | One local source-defined GitHub workflow |
| Independent consumer goal | Detect evidence-tooling regressions before making real release evidence |
| Parameter ownership and derivation | GitHub supplies exact owned workflow context, no workflow inputs |
| Direct/no-new-interface alternative | Current scripts can be run manually, existing repo lacks a job |
| Caller use of result | Read check result scoped to tooling fixtures |
| Round trips and failure points | One own commit fetch and two local Python runs |
| Verdict | Add exactly one manual lane |

##### Identity and purpose

Job release-tooling in workflow Tianji release tooling. No new application API or authority.

##### Request parameters

GitHub-generated GITHUB_EVENT_NAME, GITHUB_SERVER_URL, GITHUB_REPOSITORY and GITHUB_SHA. No workflow input or cross-repository target. Job-issued contents:read token only for single fetch.

##### Success response

Native job successful after exactly 13 actual finite tests. Standard test counts and fixed success summary. No release, upload, deployment or branch mutation.

##### Error responses

Job failure on invalid context, wrong SHA, missing/unsafe archive, Python version, test discovery, failures or skips. Fetch emits fixed failure without raw Git diagnostics. No valid source proof on error.

##### Interface logic for frontend and consumers

Current repository Actions interface only. Repository operator separately decides if and when to publish/run. No Tianji frontend.

##### Compatibility and verification

Existing tests/policy and Java U unchanged. Local structural/simulated checkout proof and actual archive test execution are separate from a hosted Actions result.

## 10. POJO and Data Model Design

No Java or persisted types. Existing synthetic Python fixtures keep their schemas.

## 11. Database Design

No database operations, access, data, credentials or schema in the workflow.

## 12. Frontend Page Design

No app or browser change. Existing GitHub operator interface triggers the manual job only when available.

## 13. Design Patterns and Architecture Principles

Use direct composition of platform checkout, content verification and existing fixture tests. Separate process environments allow the tooling test to run without checkout credentials or cross-repo inputs.

## 14. Test Design

| Test ID | Exact proof | Expected observation |
| --- | --- | --- |
| TEST-001 | Local Ruby Psych workflow parse, Bash syntax, simulated Git transport with real local commit | Wrong event/host/repo/SHA/token rejected before transport, one exact fetch call, no persisted header |
| TEST-002 | Actual Git archive of five tracked blobs, blob checks | Exact source and file modes, no .git/siblings |
| TEST-003 | Python 3.14.6 exact existing suites and synthetic negative fixtures | 7+6, empty/skipped fails, Python 3.9 fails preflight |
| TEST-004 | Source inspection and tracked-scope comparison | Four proposed paths and no hosted run, script/fixture/migration/dependency changes |

## 15. Non-functional and Cross-cutting Design

Five-minute bounded job. Checkout auth variables cleared after use; Python inherits neither runner tokens nor arbitrary launcher settings. Logs may contain current source SHA and synthetic suite results only. No raw private report data.

## 16. Compatibility, Migration, Rollout, and Rollback

No migration. Source change makes a workflow candidate reviewable. GitHub visibility, enablement, publication and manual dispatch remain separate. Removing the one workflow removes this automation path.

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| Five-repository U workflow today | Deferred | No local remote-ref evidence for exact four sibling HEADs and no verified hosted cache/checkout authority |
| Third-party checkout/action install | Rejected | Native Git and runner tools suffice |
| Always run on pushes and pull requests | Deferred | Current scope is explicit operator dispatch |
| Manual own-source gate | Selected | Verifies existing evidence tools independently |

## 18. Risks and Open Questions

| ID | Status | Disposition |
| --- | --- | --- |
| OPEN-001 | Real GitHub hosted execution unverified | Requires source publication and operator-approved manual dispatch |
| OPEN-002 | Full remote Java U unverified | Exact sibling revisions, authorities and offline runner cache separate |
| OPEN-003 | Ubuntu installed tools/environment unverified on hosted runner | Declare runner and fail closed on unsupported interpreter |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/CI-001 | §5/§7/§9 | TEST-001 |
| REQ-002 | UC-001/CI-001 | §7/§8/§15 | TEST-001/002 |
| REQ-003 | UC-001/CI-001 | §6/§7/§9 | TEST-003 |
| REQ-004 | UC-001/CI-001 | §11/§16/§18 | TEST-004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/8 | existing Service source/tooling owner | None |
| MC-REUSE-001 | Applicable | PASS | §7/8 | existing Python fixtures | None |
| MC-DEP-001 | Applicable | PASS | §6 | no dependency | None |
| MC-NAME-001 | Not applicable | N/A | §8 | no Java | None |
| MC-VALID-001 | Applicable | PASS | §7.3/9 | exact Git context/source/test count | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | no Java model | None |
| MC-CONVERT-001 | Not applicable | N/A | §8 | no converter | None |
| MC-LOG-001 | Applicable | PASS | §9/15 | fixed auth/checkout failure, synthetic fixture logs only | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | no Spring | None |
| MC-UTIL-001 | Not applicable | N/A | §6 | no Java utility | None |
| MC-JSON-001 | Not applicable | N/A | §9/10 | no evidence contract change | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | no affected time contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §8 | no runtime profiles | None |
| MC-PATTERN-001 | Applicable | PASS | §7/13 | direct reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8 | one self-contained repo workflow | None |
| MC-TEST-001 | Applicable | PASS | §14 | exact finite and live separation | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | ready for review | None |

### 20.6 Final verdict

PASS — Ready for user review

源内门禁证据有限于本地测试。GitHub 权限、托管执行、Java U、真实运行和发布由独立证据确认。
