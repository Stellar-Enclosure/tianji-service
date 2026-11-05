# T4-05A1 天机离线发布证据门禁

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-16-56-expense-release-evidence-gate.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | None |
| Created | 2026-11-05 16:56 Asia/Shanghai |
| Updated | 2026-11-05 18:08 Asia/Shanghai |
| Owner | Tianji release decision owner |
| Repository | tianji-service |
| Scope | T4-05 离线证据首切片，已有 expense U/I/E 流程的候选/版本/报告绑定 |
| Change Surface | 既有 scripts 目录中一个 Python 标准库查询、一个版本化规则文件和一组有限测试 |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 §17 和 §19/T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | tianji-service fafa874e460ca87a56ea652cd651c219a5028faa，A1 2f10711b152bbce7000ba7fb31d6f931e4f52927，A2 74369e760829d60e41d658b780681a54efcb22a0，Web e5d14225abacd4766aa21f9b59ce2364cd23bb3b |
| Amends | None |
| Supersedes | None |
| Depends On | [T3-12 expense 四路径](2026-11-05-15-13-expense-four-path-acceptance.md) §9/§14, [T4-01A1](2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) §9/§10, [T4-01A2 可选离线证明](2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) §9.2 |
| Related Specs | None |
| Related Plans | [T4-05 implementation](../plan/2026-11-05-18-08-expense-release-evidence-gate.md) |

## 1. Summary

交付一个只读查询。它把已审阅的 expense 候选、实际 AS/PS exec JAR、固定来源提交以及已有 U/I/E 原始证据关联起来，返回当前 scoped evidence 是否齐备。有限测试通过、编译、跳过、实际失败、未执行和过期分别呈现。结果给审阅者使用，不写发布状态或执行批准。

## 2. Background and Current State

### 2.1 Evidence and exact source ownership

| Evidence | Current source | What it establishes |
| --- | --- | --- |
| EVD-01 | [source reactor](../../../scripts/verify-source-reactor.sh) 只允许 test/verify，固定汇合 Tianji、Components、授柄、鉴身、天枢 | 当前存在真实构建入口，不存在同候选、同来源、同级别的统一证据门禁 |
| EVD-02 | [Source64 evidence](../evidence/2026-11-05-expense-four-path-acceptance.md) 和已保留 14 份原始 Surefire XML | 183 项 U 通过。两份 IT 已编译；它们没有执行报告，也没有真实 E 见证。保存的原始 XML 根为 testsuite，包含 name/tests/errors/skipped/failures，无可用于断定运行新鲜度的统一可信时间。 |
| EVD-03 | PS FormalCasePostgresTest 的 exact method 和 explicit tianji.process.postgres.test.url opt-in | 可独立识别一个真实 native Case I gate，和运行前的编译或缺配置跳过区别明确 |
| EVD-04 | PS BusinessProcessAgentAcceptanceIT/WorkerRecoveryIT、ExpenseAcceptanceHarness.ExpenseEvidenceDTO | 两份 E 运行有不同私有 suite manifest、实际 JUnit 结果以及业务见证 JSON。证据 DTO 只有 schemaVersion/runId/suite/passed/observedAt/children/exits/paths，候选制品和来源要从实际 manifest 另行关联。 |
| EVD-05 | Source64 step64-artifact-proof.json 包含 runtimeExecuted=false、精确 IT 类 SHA 和 AS/PS archive SHA | 这是编译和打包事实；不产生任何 IT PASS |
| EVD-06 | 已提交 [T4-01A1](2026-11-05-16-17-platform-release-artifact-candidate.md) 的 PlatformReleaseArtifactValidator 与 A1 proof | 读取真实规范候选外壳，核对 sourceRevision、candidateSha256、AS/PS archiveSha256 与 ARTIFACT_CHECKED_ONLY。 |
| EVD-07 | [T4-01A2](2026-11-05-17-30-platform-release-detached-signature.md) §9.2 的 PlatformReleaseSignatureVerifier 离线结果 | A2 已在 74369e760829d60e41d658b780681a54efcb22a0 合入并记录实际 PS exec 证明。可选地核对其独立钉选、精确类型和同候选关联。A2 结果没有可作为本门禁执行时间或当前权威的字段。 |

Source64 保存的 183 项报告属于其旧锁。当前 checkout 或重打包产生不同候选/归档 SHA 时，只能标记为其他版本的证据。盘点另有有限 Fake/规则质量材料，不能代替 L、性能阈值或完整 §17/S01–S12。

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 仅在既有 tianji-service scripts 目录中新增离线脚本和脚本测试，不生成业务项目、不改变 Java 业务模块、不接管任何 archetype 示例、数据或迁移。

## 3. Goals and Non-goals

首切片对固定 expense profile 同时核对一份 A1 候选、两个打包制品和四个已有验证组。需要的环境实际执行仍由原 owner 和既有运行入口负责。后续质量 corpus、真实模型 L、负载/SLO、完整 S01–S12、UI、CI 自动审批和 live 激活分别设计。该工具不启动服务、不调用 Maven、不联系 CI/数据库/IAM/模型、不读凭据内容，也不发出发布命令。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Local evidence query and closed files | Affected | scripts/verify-source-reactor.sh, Source64 raw reports, harness evidence DTO, T4-01A1 contract | One offline verdict, fixed policy, real bytes and provenance | Exact CLI, file layout, statuses, tests | §7, §8, §9, §10, §14, §15, §16 |
| Runner, authority, packaging | Context-only | Exact I and E classes and A1 candidate producer | Existing execution and package owners stay in control | Cite reused facts | §7, §9 |
| Runtime Java, database, UI, CI | Unchanged | Current modules and migrations | No product or automation change | Concise scope | §11, §12 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Pin the candidate, actual artifacts and checkout | Must | Candidate file byte hash and explicit candidate SHA, service HEAD plus four sibling revisions, actual AS/PS archive byte SHAs and all per-run locks match. If supplied, a separately pinned A2 output also matches exact candidate and scope | T4-05 version/environment, A1 |
| REQ-002 | Verify actual raw reports from exact owning classes | Must | Whole-class selector and module/FQCN fit fixed policy, counts and testcase facts agree, nonzero executed, F/E/S=0 for a PASS | Source64, I/E gates |
| REQ-003 | Classify incomplete or stale evidence distinctly | Must | FAILED, SKIPPED, COMPILED_ONLY, NOT_EXECUTED, STALE and INVALID_EVIDENCE cannot satisfy a required group | Inventory, T4-05 |
| REQ-004 | Require actual E witnesses and isolated provenance | Must | Exact BUSINESS and RECOVERY runs, corresponding hashes/manifest/JSON, distinct environments and children, six and two exact path results and required durable references | T3-12 §9/§14 |
| REQ-005 | Bound scope and retain private evidence | Must | Redacted deterministic one-line summary and safe errors, no process except optional-lock-free Git metadata checks, no write outside stdout, no credential or environment disclosure | User constraints |
| REQ-006 | Preserve the separation of scoped review and platform acceptance | Must | Success yields EXPENSE_EVIDENCE_READY_FOR_REVIEW only. Full S01–S12, live authority and L/SLO remain separately outstanding. | Master §17/§19 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| ACTOR-001 | Evidence preparer | Retain exact existing run and build artifacts | Private local stage | Current owner; authorized isolated runs only | Source64 and A1 |
| ACTOR-002 | Release reviewer | Independently verify scoped evidence | CLI-001 | Current local owner and separately reviewed expected locks | T4-05 |
| ACTOR-003 | Isolated runtime operator | Execute existing E only with independent approval | Existing two private manifests and E tests | Exact two IAM tenants and disposable stores | T3-12 §9/§14 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Prepare verifiable artifact evidence | ACTOR-001 | Existing build and A1 owners | Release evidence requested | Clean exact candidate and approved local artifacts | Private raw refs and immutable run metadata | Partial or prior evidence retained for diagnostics | Staging causes no release or test execution | REQ-001/002 | CLI-001 | TEST-001/006 |
| UC-002 | Verify scoped readiness | ACTOR-002 | ACTOR-001 | Explicit CLI invocation | Approved exact candidate/revision | One scoped ready or blocked verdict | Fixed per-group causes for absence/stale/failed/skip | Reviewer knows what actual evidence remains due | REQ-001–006 | CLI-001 | TEST-001–006 |
| UC-003 | Provide isolated runtime evidence | ACTOR-003 | Existing T3-12 owners | Separate approval and operator invocation | Fresh independent stores and exact runtime identities | Actual E reports plus scoped durable witness | Missing/failed or wrong provenance remains blocked | Runtime owner retains authority and private evidence | REQ-003/004/006 | Existing E invocation, CLI-001 consumption | TEST-005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or assumption | Consequence |
| --- | --- | --- |
| DEC-001 | 固定 profile expense-release-evidence-v1。只判断 expense 发布证据包，不把全矩阵裁剪成已完成。 | T4-05 全量资格门禁及 T4-01 live activation 均未开放 |
| DEC-002 | 仓库内固定策略由当前工具版本拥有；输入无权降低 required level、删 selector、声明 N/A 或忽略失败。 | 规则变更经源码审阅；不会通过修改私有描述放行 |
| DEC-003 | 复用实际报告和既有 harness 契约，显式单向组合。 | 不建测试执行器、收集服务、签名系统或 CI 工作流 |
| DEC-004 | 已审阅候选/来源是独立 argv 期望；来源及档案字节复核。运行证明使用操作者在执行时记录的只读采集锁。 | 可检测错配和篡改后的文件，无法从任意 XML 反推真实部署或构建供应链 |
| DEC-005 | 操作者在独立 argv 声明一个闭合审计上下文的 UTC 起止时间。时间窗口采用下界含、上界不含，对实际 finishedAt/capturedAt 作判定，上界不得超过本次一次采样的 UTC now。 | 所有 U/I/E 共用同一审计窗口。没有默认天数、环境默认值或生产 SLO 含义。 |
| DEC-006 | A2 是可选引用，给出文件时必须独立钉选原始字节 SHA，关联 A1 语义摘要、原始外壳摘要、sourceRevision、tenant/channel。 | 缺席仍可评审。匹配只表示读取的离线证明引用一致，既不重新验签也不验证实时权威。 |
| ASM-002 | 支持当前源反应堆的五个同级仓库与现有 Surefire schema，不接受任意仓库名、聚合报告或可插拔解析。 | 初始范围小。外部构建提供同版本 clean checkout；其他 provider 后续评审。 |

## 6. Project Technology Context

沿用 Python 3 标准库和既有 Git CLI。source reactor 已用 Python 3 写临时聚合 POM。首切片使用 json/hashlib/pathlib/stat/ElementTree/datetime/subprocess/unittest，不引入 Java 依赖、包安装、Maven profile 或新软件服务。Git 子进程只读 HEAD/工作树元数据，固定参数、关闭 optional locks、有超时且不输出原始诊断。

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | No | scripts | 不增改 Java 类型 | §8 Python | TEST-001 scope | N/A |
| Rule 2 | No | 当前模型/HTTP 合同 | 不改 Java 边界，Python 文件严格校验 | §9 | TEST-002 | N/A |
| Rule 3 | No | 原有 Java DTO 未改 | 只读取原 DTO JSON | §10 | TEST-005 | N/A |
| Rule 4 | No | 无 Spring/Bean 或业务日志 | 固定 stderr | §9 | TEST-002 | N/A |
| Rule 5 | No | 无 Java 工具依赖变更 | Python 标准库 | §8 | TEST-001 | N/A |
| Rule 6 | No | 原 Boot Jackson 未改 | Python 本地文件合同独立 | §9 | TEST-002 | N/A |
| Rule 7 | No | 无 Spring profile 修改 | 已有隔离运行 manifest 只读 | §11 | TEST-005 | N/A |
| Rule 9 | No | 无新 Java 业务流程 | 本地判定表直接实现 | §13 | TEST-004 | N/A |
| Rule 10 | No | 无 Java 时间变更 | Python UTC 证据时限 | §6.3 | TEST-004 | N/A |
| Rule 11 | No | 无生产领域、事件或迁移 | 本地只读脚本 | §11 | TEST-001 | N/A |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | run.startedAt/finishedAt, compileProof.capturedAt, E observedAt and children.startedAt | Occurred instant | Python UTC epoch nanoseconds integer | Not persisted | UTC Z | Not applicable: operational instant | Not applicable: local CLI | strict UTC, start≤witness≤finish≤now; finishedAt/capturedAt in audit window | 0–9 fractional digits, no rounding, one time.time_ns sample | existing raw reports without UTC use explicit capture record | TEST-004/005 |
| TIME-002 | argv auditFrom/auditUntil, output auditWindow | Occurred instant | Python UTC epoch nanoseconds integer | Not persisted | UTC Z | Not applicable: auditor UTC input | Not applicable: local CLI | fromInclusive < untilExclusive ≤ now; half-open capture interval | 0–9 fractional digits, preserve exact input strings in output | New offline scope | TEST-004 |
| TIME-003 | optional A2 issuedAt/expiresAt | Occurred instant | Python UTC epoch nanoseconds integer | Not persisted | UTC Z | Not applicable: signed UTC text | Not applicable: omitted from gate output | A2 exact seconds, issuedAt < expiresAt; historical reference only | No rounding, not used for freshness | Existing A2 proof fields | TEST-006 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Fixed policy + local checker | New | REQ-001–006 | Manually inspect existing reports and A1 archive output | Existing readers do not join source/candidate/execution/required level or distinguish missing I/E | One local query, bounded files, no state or external call | Add |
| Existing A1, Surefire, E manifest/evidence | Keep | REQ-001/002/004 | Reuse as-is | New report format would duplicate proof owners | Capture immutable raw file refs and actual lock | Keep |
| CI runner, evaluator, signature/activation workflow | Keep | REQ-006 | Existing owner phases | No new consumer authority in this slice | No added coupling | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 for local review | Manual comparison | Existing artifacts | Human can mix revisions and mistake skipped for pass | Existing evidence remains inspectable |
| Selected design | 0 | Ready or blocked, fixed causes | None | Local ref/race/parse fail closed | Reviewers can reproduce exact scoped verdict |

### 7.1 System Architecture Design

Existing operator/CI owner produces artifacts and runs tests using the current tools. A private evidence folder holds read-only raw snapshots, the recorded run envelope and exact candidate/archive refs. The checker reads fixed local policy and worktree metadata, validates provenance and evidence, and emits one safe JSON summary. No path back to a runtime writer.

### 7.2 High-Level Design

Fixed policy contains exactly four required groups. The pilot checks already existing expense-specific evidence for relevant parts of S05/S06/S08/S09/S10/S11. It does not claim full §17 coverage.

| Group | Level | Exact expected sources | What it demonstrates |
| --- | --- | --- | --- |
| U_SOURCE | U | Source64's exact 14 owned classes from locked G7, listed below | Finite model/contract/harness/Fake/Worker baseline. No I/E substitution. |
| I_CASE | I | PS FormalCasePostgresTest#native_case_lifecycle_and_exact_bpmn_continuation_are_atomic_and_recoverable | Existing explicitly provisioned native Case and exact continuation fixture |
| E_BUSINESS | E | PS BusinessProcessAgentAcceptanceIT with exact normalWorkflowAcrossBothTenants, failedAgentCompensatesAcrossBothTenants, manualCaseAndAuthorizedReviewAcrossBothTenants | Actual runtime three paths and six tenant/path rows |
| E_RECOVERY | E | PS WorkerRecoveryIT#recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants | Actual runtime two tenant recovery rows and required distinct fault witnesses |

Other S01–S12 obligations stay visible outside this pilot: S01–S04, S07 and S12 have no required row here. S05 needs its independent native decision behavior, S08/S09 retain all separate approval/termination requirements, S10 retains unsupported-effect UNKNOWN and S11 retains AR QUEUED/RUNNING/tool checkpoint boundaries. E expense witnesses cover only the selected subcases. L and numeric SLO are independently reviewed and have no eligible input in this profile.

U owning artifact IDs use existing module suffixes AS/PS/W. Exact short names plus counts from Source64 are AS ExpenseAcceptanceModelTest=2, AgentResultDeliveryHandlerTest=6; PS ExpenseAcceptanceHarnessTest=9, ExpenseFixtureModelsTest=4, AgentStartWorkerTest=10, CompensationBusinessFixtureTest=15, CmmnModelValidatorTest=67, HumanTaskModelTest=5, HumanTaskTimerModelTest=5, ProcessModelValidatorTest=19, WorkerCompensationModelTest=12, FlowableWorkerServiceTest=16, WorkerTopicRegistryTest=2; W RemoteWorkerPollerTest=11. The policy stores FQCNs from the archived owning XML and count minima, with exact whole-class selector names. It does not pin names of parameterized U cases as invented source identity. I/E methods are exact, with expected count 1/3/1 and no scope expansion through a broader selector.

### 7.3 Detailed Design

1. Parse exact argv and resolve the approved private root and local checkout. Git HEAD, clean status and four sibling revisions are captured without remote access. Match independent expected service SHA and candidate SHA, bind raw A1 candidate and exact AS/PS files. Candidate's ARTIFACT_CHECKED_ONLY state stays unchanged. Optional A2 proof is checked against its independent pin and exact A1 identity.
2. Parse a strict collection index, bind each group to at most one real run. Every run records exact whole-class selector set, level, unique run ID, start/end UTC, actual command exit status, candidate SHA, source lock and archive set as observed. Validate every file reference digest before parsing. The source owner records metadata when collecting, not after discovering which tests failed. Default no alternative file search.
3. Classify owning raw Surefire XML. Root testsuite name/FQCN, filename, associated module, actual testcase records, owning names, exact count and mutually exclusive failure/error/skipped children must agree. XML property test must match the required selector set, rejecting method filtering for U. Its single basedir property must identify the owning module by normalized final module segment, allowing Source64's reactor /../ prefix and staging relocation. Do not follow or echo the XML's path. For I/E use one explicitly selected class and exact required methods. No property outside that selection check is copied out.
4. E additionally joins actual private harness input and write-once evidence JSON by runId/suite. Compare locked revision and AS/PS exec hashes, the same exact test-class-tree digests across suites, and distinct three-store run environments. The release checker reads no credential file references. Verify required result/child states below. I requires its explicit isolated PG18/Flowable8 capture marker with the approved native run lock. A generic component or synthetic evidence file does not satisfy I/E.
5. Render one safe deterministic JSON record. Every required row must be PASS for the fixed scoped outcome. Status precedence and safe output are in §9/§10.

## 8. Package Structure and Code File Tree

| Proposed path in tianji-service | Responsibility |
| --- | --- |
| scripts/verify-release-evidence.py | One local Python 3 command and finite parsing/classification |
| scripts/release-evidence-policy-v1.json | Fixed version/profile, owning FQCNs, exact selectors, minima, runtime witness requirements, required declared UTC window mode |
| scripts/test_verify_release_evidence.py | Standard library finite tests with temp folders, fake UTC and Git seams, small raw-report/manifest examples |

交付时同一 commit 归档本 Spec、对应 Plan 和 docs/egon/evidence/2026-11-05-expense-release-evidence-gate.md 的有限验收记录。现有 source-reactor、POM、CI、Java 测试与制品生成器维持。

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Change/necessity verdict | Name/purpose | Kind | API style/CQE role | Consumer | Owner | Method + URL / GraphQL field / symbol / topic | Operation ID/schema source | Input | Output | Auth/tenant | Error model | Idempotency/version | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CLI-001 | New/Add | Verify fixed expense release evidence | Local CLI | Query | ACTOR-002 | tianji-service | scripts/verify-release-evidence.py verify | v1 policy/index/output below | Explicit private index, checkout and expected locks | One safe JSON summary | Current local POSIX owner; exact candidate scope | Fixed exit/diagnostics | Read-only, deterministic for same evidence and evaluation UTC | REQ-001–006 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CLI-001 — Local evidence query

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New offline CLI Query |
| Independent consumer goal | Reviewer can verify exact local expense evidence before any approval |
| Parameter ownership and derivation | Reviewer independently pins candidate/revision. The checker derives actual artifact hashes and compares exact raw run facts. |
| Direct/no-new-interface alternative | Manually compare A1 and report totals; this lacks a reproducible fail-closed join. |
| Caller use of result | Review a scoped verdict and exact block categories. It is never forwarded as an activation permit. |
| Round trips and failure points | One local call, no network, bounded files/read-only Git, fixed parse and I/O failures. |
| Verdict | Add the one CLI Query; no runtime endpoint or automation. |

##### Identity and purpose

Exact module-local scripts/verify-release-evidence.py verify for the fixed expense profile.

##### Request parameters

```sh
python3 -B scripts/verify-release-evidence.py verify \
  --root "$private_root" --index evidence-v1.json \
  --checkout "$clean_tianji_checkout" \
  --expect-revision "$expected_tianji_commit" \
  --expect-candidate-sha256 "$expected_a1_candidate" \
  --audit-from "$utc_from_inclusive" --audit-until "$utc_until_exclusive"
```

以上七项均必传且各出现一次，顺序任意。仅当 index 提供 signatureProof 时，另传 --expect-signature-proof-sha256 64 位小写十六进制且只一次。root/checkout 是不经过 symlink 的规范绝对目录，index 是根下相对路径。expected revision 为 40 位小写十六进制，candidate pin 为 64 位。policy 文件固定与脚本相邻。auditFrom/auditUntil 使用严格 UTC Z 和至多九位小数。from < until ≤ 一次采样的 now，实际采集时间按 [from, until) 判定。 Read-only Git with GIT_OPTIONAL_LOCKS=0 and timeout 3 s per command, no refresh/pull/fetch/status write. Fixed Git argv checks canonical top-level path, HEAD and whole clean status, with optional-lock-free metadata, no shell or caller Git variables/config, and core.fsmonitor=false. Require exact locked siblings Components/Shoubing/Jianshen/Tianshu under the current conventional sibling layout. Source builds captured elsewhere require an equivalent local exact checkout. The CLI cannot target live services or start tests.

Collection index exact schemaVersion=1, profileId, policySha256, candidate FileRef, revisions, archives, testClasses, compileProofs, runs, with the single optional member signatureProof FileRef. FileRef exact path and sha256. Revisions exact tianjiService/components/shoubing/jianshen/tianshu full commit SHA fields. Archives exact agent/process FileRef. TestClasses exact agent/process tree SHA declared at build capture and independently checked against E manifest. CompileProofs optional per required IT as exact module, className, classFile FileRef, candidateSha256, revisions, archiveShas and capturedAt. No ambient file discovery. A compile proof matches this build, actual class magic and previously inspected class SHA. Existing Source64 captured class digests can be used as expected references with retained class bytes. It does not load the class or infer method execution. A compile-only fact never upgrades execution.

Each runs entry has exact groupId, runId, level, startedAt, finishedAt, exitCode, candidateSha256, revisions, archiveShas, selectors, environment, reports. Reports are exact module, className, file FileRef tuples. Environment is a closed discriminated object. U has kind=OFFLINE_FAKE. I has kind=ISOLATED_NATIVE_PG, postgresVersion, flowableVersion, environmentSha256; for a pass require PostgreSQL 18.x and Flowable 8.0.0. E has kind=EXPENSE_ISOLATED_E, manifest FileRef or null, witness FileRef or null. I and E may use environment=null to record a real failed/skipped/unexecuted attempt that lacked an environment, but never a PASS. E uses actual harness DTO bytes. A fresh failed/skipped raw report may be classified before a missing manifest/witness; malformed supplied evidence remains INVALID_EVIDENCE. Any group without execution evidence may be absent. An explicit zero-test XML or skipped testcase is not a successful run. Do not derive execution from Maven flags alone: the current E owner explicitly selects its Surefire IT class even with skipITs=true to suppress unrelated Failsafe runs. Closed scopes reject free-form commands, credentials, user-supplied Git/Maven args and authority fields.

Raw paths are relative ASCII components beneath owner-private root (max 512 bytes and 16 components, no dot/parent/backslash/symlink). Read only regular files, same owner, no group/other write, verify raw SHA and observed file identity before/after read. Candidate/index/manifest and witness JSON cap 512 KiB each, one XML cap 2 MiB, aggregate XML cap 32 MiB, one exec archive 512 MiB streaming. Duplicate paths, file identities, run IDs or group records are invalid. The A1 envelope must be the exact two fields candidateSha256/candidate and its expected declared ARTIFACT_CHECKED_ONLY state. Independently recompute the existing A1 candidate digest using the A1 domain and canonical JSON of the bounded candidate payload with closed string/integer/array/object output values. Python sorted keys and compact UTF-8 reproduce the A1 form for this shape, rejecting surrogate and control characters. Match both argv and the envelope, plus raw candidate byte SHA from index; require the two actual archive bytes to match the A1 payload. A1 bytes must be the exact canonical envelope plus one LF, with all nested output shapes from A1 §9.2/§10. For optional A2, compare raw FileRef and independently supplied expected SHA. Require exact A2 result fields/constants/pendingAuthority from A2 §9.2, matching candidateSha256, raw candidateEnvelopeSha256, sourceRevision, tenantId and channel. Validate keyId/trustSha256 and strict A2 time text with issuedAt < expiresAt. Historical A2 reference validation does not reinterpret issuer time or verify new trust. Missing file/pin counterpart or a supplied wrong reference is invalid top-level input. UTF-8 without BOM, duplicate JSON keys, excessive nesting (>32), trailing tokens, XML DTD/ENTITY and nonfinite JSON scalars are rejected. Read-only archive digest, never load or extract classes. Use captured XML/Surefire format only.

| Status | Exact rule after structural validation |
| --- | --- |
| INVALID_EVIDENCE | Digest/path/schema/owner/selector mismatch, report duplication, inconsistent counts, wrong witness form or ambiguous/missing proof for a claimed E PASS |
| STALE | Well-formed report/compile binding from another candidate/source/archive/environment or finished or captured outside declared audit window; never reuse old success |
| FAILED | Fresh matching actual run nonzero exit, explicit structurally valid E passed=false, or any actual testcase failure/error including flaky/rerun failure/error elements |
| SKIPPED | Fresh matching XML contains skipped cases with no failure/error, even when other cases ran |
| COMPILED_ONLY | Valid matching compiled IT class proof and no owning execution report |
| NOT_EXECUTED | No qualifying report or compile proof, a genuine zero-test report, or an incomplete otherwise-green required report set. Use REQUIRED_REPORT_MISSING for partial group coverage; fresh failures/skips take precedence over a missing sibling report. |
| PASS | Exact group, verified fresh metadata, actual nonzero executed rows, no failures/errors/skips or flaky-rerun tags, required witness/provenance all checked |

##### Success response

A well-formed passing bundle returns exit 0 and the exact one-line scoped JSON envelope in §10. A well-formed blocked bundle keeps the same envelope and uses exit 1. A2 presence is not a readiness gate.

##### Error responses

Malformed top-level input produces exit 65 and TIANJI_RELEASE_EVIDENCE_INVALID on stderr with no stdout. Invalid argv returns 64 and TIANJI_RELEASE_EVIDENCE_USAGE. Well-formed bundle produces one JSON summary and no stderr, exit 0 if scoped ready, 1 if blocked (including per-group invalid evidence). Exit 66 with TIANJI_RELEASE_EVIDENCE_IO_UNAVAILABLE for top-level I/O or stdout failure. Each diagnostic has exactly one LF. Wrong independent A1 or checkout lock is invalid input, while a valid unrelated per-run record is stale. No result is named APPROVED, RELEASED or ACTIVE. Output caps 32 KiB.

##### Interface logic for frontend and consumers

The operator stages raw files, preserves the independently reviewed candidate and source locks, runs the checker once and reviews each gate. No frontend is involved. Exit 1 remains an auditable non-success.

##### Compatibility and verification

This pilot preserves current A1/Surefire/E output formats. Exact selectors, optional compile facts and actual report observations are verified by §14.

## 10. POJO and Data Model Design

No Java types. One input shape and one closed JSON result. Output fields schemaVersion, profileId, policySha256, candidateSha256, sourceRevision, archiveShas (exact agent/process SHA), auditWindow (exact fromInclusive/untilExclusive UTC text), signatureProof (NOT_SUPPLIED or REFERENCE_MATCHED), verdict, gates. Verdict EXPENSE_EVIDENCE_READY_FOR_REVIEW or BLOCKED. gates ordered U_SOURCE/I_CASE/E_BUSINESS/E_RECOVERY. Each exact id/level/status/reason/tests/executed/failures/errors/skipped, nullable counts if no trustworthy raw report. reason 使用以下封闭文字量。PASS 对应 VERIFIED。STALE 对应 SOURCE_LOCK_MISMATCH 或 AUDIT_WINDOW_MISS。FAILED 对应 RUN_EXIT_NONZERO、TEST_FAILURE 或 WITNESS_FAILED。SKIPPED 对应 TEST_SKIPPED。COMPILED_ONLY 对应 CLASS_COMPILED。NOT_EXECUTED 对应 NO_RUN、NO_EXECUTED_TESTS 或 REQUIRED_REPORT_MISSING。INVALID_EVIDENCE 对应 INVALID_REPORT、INVALID_COMPILE_PROOF、INVALID_ENVIRONMENT、INVALID_WITNESS 或 WITNESS_MISSING。先验证结构与文件归属，再 stale、失败、skip、缺报告，最后检查 pass 所需运行见证。report 有 failure/error/flaky 时用 TEST_FAILURE 优先于 RUN_EXIT_NONZERO。部分报告的可信计数只表示实际读到的拥有者。 The output contains no raw XML, properties, commands, tokens, file paths, role/user IDs, case/result IDs, hashes beyond declared build identifiers, stack or configuration. It is deterministically sorted compact UTF-8 and one LF, with no derived authority.

E witness reader checks schemaVersion/runId/suite/observedAt/passed, exact tenant set from private manifest, unique path rows and exact shapes. BUSINESS has NORMAL, AGENT_FAILURE, MANUAL_CASE per tenant. RECOVERY has one RECOVERY per tenant, with WORKER_REACQUIRED, EARLY_INBOX_PENDING, OUTBOX_RETRY_CONFIRMED, PROCESS_WAIT_RETAINED required. 现有 BASE witness 精确取 ExpenseAcceptanceHarness.WitnessKind 的 START_COMMAND 到 ORIGINAL_RECEIPT。NORMAL 增 NOTICE_RECEIPT；AGENT_FAILURE 增 UNDO_RECEIPT；MANUAL_CASE 增 UNDO_RECEIPT、HUMAN_TASK、CASE_INSTANCE、CASE_COMPLETION；RECOVERY 增 NOTICE_RECEIPT 和四个恢复见证。 A required witness must be PROVED with safe bounded reference. AGENT_STATUS/PROCESS_END 对 NORMAL、RECOVERY 为 COMPLETED/normalEnd，AGENT_FAILURE 为 FAILED/compensatedEnd，MANUAL_CASE 为 COMPLETED/compensatedEnd。决策规则从已提交 expense fixture 的路径合同核对，MANUAL_CASE 为 largeExpense，其余 smallExpense。 Per scenario, same operation/business/correlation text across tenants accompanies distinct process/run/original-receipt references. Existing IT owns validation of the recovery subwindow IDs; the checker verifies required distinct named witness fields and does not attempt to reconstruct engine rows from their safe references. A branch not reached must have NOT_REACHED/null; PENDING and UNAVAILABLE are not passing. Children require both roles, unique PID per child and across the two passing suites, generation sequence 1..n and strictly increasing start per role, every start within run start/observation, and one exact identity-matching exit per child. Exit code is a recorded signed integer, not a second test result; normal process shutdown or controlled replacement can return nonzero. RECOVERY requires replacement identities for each role. Both eligible E suites use the same two isolated tenant IDs and distinct three-database sets, run IDs and child identities. Failed E evidence remains FAILED or INVALID_EVIDENCE depending on structure. Run and observation instants must be ordered and not after current UTC. The auditor’s half-open window uses actual run finish/compile capture. It never uses file mtimes.

The verifier treats the private collection index as an operator-controlled record. It checks binding and bytes with independent expected inputs and clean local source. It cannot cryptographically attest that a self-created XML or capture envelope came from a trusted runner; future signed attestation and activation remain separate.

## 11. Database Design

Not applicable. The checker reads staged report bytes and checkout metadata only. It never opens PostgreSQL connections, reads credential files or changes any SQL.

## 12. Frontend Page Design

Unchanged. Web task/Case consumer tests and browser acceptance stay independent. No Web script, page, provider or selector changes in this slice.

## 13. Design Patterns and Architecture Principles

A fixed classification table and direct checks are sufficient. No plugin registry, report aggregator service, retry engine or second evidence pipeline. Existing source and runtime owners continue producing the facts they own.

## 14. Test Design

| ID | Finite test using standard library and private temp paths | Exact observable result |
| --- | --- | --- |
| TEST-001 | Build a coherent fixture with pinned policy, private artifacts, real-shape owner XML and matching I/E witness fixtures | Exit 0 only when all four required groups pass; safe one-line result, no writes or invoked runtime |
| TEST-002 | Invalid argv, file root/path, symlink, digest, duplicates, non-UTF8/DTD, schema/over-limit or mixed module/class | Exact invalid gate/input outcome, fixed redaction, no path/XML/secret in output |
| TEST-003 | Staged Source64-style U reports plus IT class proofs and no I/E XML | U can pass on same lock; I NOT_EXECUTED, E COMPILED_ONLY, verdict BLOCKED |
| TEST-004 | Fresh nonzero run, failure/error testcase, true skipped case, zero execution, stale SHA/old window, duplicate report, inclusive audit start, exclusive audit end and nanosecond boundary | Distinct fixed statuses with real counts; only PASS satisfies required group |
| TEST-005 | E exact valid six/two rows vs same run ID/store sets, wrong artifact/role/generation, missing witness, mismatching input or Fake summary without report | Exact provenance/witness failures block. Raw properties/credential refs never appear in JSON. |
| TEST-006 | Wrong checkout/dirty sibling, stale candidate, mismatched executable hash, detached expected commit, legitimate current artifact, optional A2 exact output, pin mismatch and missing counterpart | Gate cannot promote unrelated build. Git seam proves read-only fixed args and timeout. |

Future finite execution from the exact tianji-service checkout is python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py. Reuse captured XML as private fixture-shape verification where available, excluding paths and raw properties from committed test data. Do not invoke source-reactor, Maven, native engine, App, PostgreSQL or browser in checker tests. Future E commands remain the T3-12 owner’s separately authorized runs. Same source passing U/Surefire is not a genuine E witness.

## 15. Non-functional and Cross-cutting Design

Bound file count/bytes, XML and JSON depth, and Git timeout. Private files may contain test system properties or credential references, so parse only necessary fields and never serialize them. All diagnostics use fixed codes. No network requests, permission grants, retrying tests, directory scanning or publication. Files changing during read or non-POSIX private scope fail closed.

## 16. Compatibility, Migration, Rollout, and Rollback

Ship the checked-in rule file and verifier after review. Existing source build and runtime entrypoints are untouched. Operator stages exact already-collected artifacts in an owner-private folder. Source64 archive/report facts can demonstrate blocked/missing E at their own old lock. Current candidate requires a fresh source/record lock. A later scope/version change uses a reviewed policy version, not an input override. Rollback removes the checker/policy source commit, with no database, Git history, signing or activation side effect.

## 17. Alternatives and Decisions

A CI-only decision would couple this slice to deployment permissions and still need stable evidence predicates. Manually tallying report totals loses module, scope and artifact identity. A new testkit and run store would duplicate existing source reactor and harness output. Fixed local policy and direct reuse are sufficient for the pilot.

## 18. Risks and Open Questions

- 操作者独立声明本次审计 UTC 窗口。窗口政策由离线审查拥有，生产负载和 SLO 独立。
- 当前 A1/A2 合同已有源码与制品证明，同组合的真实 I/E 尚未观察到。GO 前重锁实际五仓 HEAD，缺少 I/E 的输入应产生 BLOCKED。
- Operator capture metadata and raw XML are not signed attestations. A later governed activation design may require independent signed build/owner evidence.
- Full S01–S12 evaluation, real model quality/versions, Prompt/Tool injection, telemetry S12 and numeric latency/concurrency remain independent. This pilot cannot satisfy them.

## 19. Traceability Matrix

| Requirement | Use cases | Design | Validation |
| --- | --- | --- | --- |
| REQ-001 | UC-001/UC-002 | §7.3/§9 | TEST-002/006 |
| REQ-002 | UC-001/UC-002 | §7.2/§9 | TEST-001/003/004 |
| REQ-003 | UC-002/UC-003 | §9/§10 | TEST-003/004 |
| REQ-004 | UC-002/UC-003 | §7.3/§10 | TEST-005 |
| REQ-005 | UC-002 | §9/§15 | TEST-002/005 |
| REQ-006 | UC-002/UC-003 | §3/§16/§18 | TEST-003 plus scope review |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/§8 | Local standard-library tool only | None |
| MC-REUSE-001 | Applicable | PASS | §2/§7 | Reuses current A1/Surefire/E owner artifacts | None |
| MC-DEP-001 | Applicable | PASS | §6 | Existing Python/Git only | None |
| MC-NAME-001 | Not applicable | N/A | §8 | No Java type added | None |
| MC-VALID-001 | Applicable | PASS | §9/§14 | Closed input and exact recorded owner scope | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | No Java model/persistence carrier | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No Java converter, private read projection | None |
| MC-LOG-001 | Applicable | PASS | §9/§15 | Fixed diagnostics, private raw reports | None |
| MC-BEAN-001 | Not applicable | N/A | §8 | No Spring wiring | None |
| MC-UTIL-001 | Applicable | PASS | §6 | Python standard library and read-only Git | None |
| MC-JSON-001 | Applicable | PASS | §9/§10 | Bounded exact input and safe output | None |
| MC-TIME-001 | Applicable | PASS | §6.3/§9 | Single UTC clock, no raw report mtime | None |
| MC-CONFIG-001 | Applicable | PASS | §5/§8/§16 | Fixed profile, explicit auditor UTC window, no runtime config | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Simple local classifier | None |
| MC-SCOPE-001 | Applicable | PASS | §3/§8/§16 | No release activation, app, DB, provider or CI | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite test design with current artifact limits | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/§18 | Offline design scoped for review | Lock actual A2 source and five repository revisions before source execution |

### 20.6 Final verdict

PASS — Ready for user review

This is a Spec for review only. Source/report shapes were inspected offline. No repository file, Maven action, database, app, CI or runtime was changed or executed during authoring.
