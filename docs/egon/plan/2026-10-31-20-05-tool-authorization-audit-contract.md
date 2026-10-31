# T3-07 兼容的工具授权审计合同

| Field | Value |
| --- | --- |
| Document | `2026-10-31-20-05-tool-authorization-audit-contract.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 20:05 Asia/Shanghai` |
| Updated | `2026-10-31 20:05 Asia/Shanghai` |
| Owner | 用户授权，主代理实施和审计 |
| Repository | `tianji-service` |
| Scope | 仅现有ToolExecutionAuthorization的兼容审计值合同；无适配/签发/存储接线 |
| Source Requirement | 用户要求继续原plan未完成工作、按逻辑步骤验证提交 |
| Baseline Revision | `main@700511730e253228a4e0f856bca14ebab67f9778` |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户本轮明确授权剩余工作，原文无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `21d90599e2ed10053f43fe0eccea2b20cd4f4c40aaa74d749fd7043e70f2ae2c` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [同源journal准备存储](2026-10-31-18-39-tool-journal-preparation-storage.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

原T3-07要求独立逐工具权限与审计，不能从run权限或构造metadata推出执行授权。现有port只返回Decision，且既有host用SAM lambda；本切片兼容增加CheckResult/AuditVO与default check，保留唯一abstract authorize及原签名。它只提供审计值约束，不提供真实权限、AS适配、签发、JSONcodec、dispatch或接线。一个步骤一个聚焦提交。

## 2. Target Spec and Effective Design

唯一主设计为原蓝图§6/13/14、§19 T3-07。前序7005117已交付唯一V13和准备存储；此处不改变它们。当前USER decision有authVersion/policyVersion，SERVICE有catalogVersion/policyVersion，不虚构上游decisionId；localCheckId/checkedAt只表达本地检查。本文保持Review，沿用户明确执行授权细化原设计，不伪造原文Accepted或完整T3-07完成。

## 3. Effective Requirements and Acceptance

PLAN-REQ为原文现有要求的局部追踪别名，不新增业务能力。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/13/T3-07兼容边界 | 独立权限port扩展不破坏现有调用者 | @FunctionalInterface仍只有authorize一个abstract；default check恰好调用一次authorize，返回audit=null的兼容结果 | port/test |
| PLAN-REQ-002 | §13/T3-07可信审计 | 审计绑定明确主体类型、边界、结果及正确版本/票据槽 | USER/SERVICE成功证据不混用；拒绝/不可用不携带上游凭据；错误schema/version/type拒绝 | AuditVO/CheckResult |
| PLAN-REQ-003 | §7/13/T3-07时限与秘密 | 已审计ALLOW覆盖请求截止且不越run上限；私密内容不回显 | checkedAt<request deadline<=expiresAt<=run deadline；全部toString脱敏，metadata不认证 | 值校验/test |
| PLAN-REQ-004 | §6/T3-07范围与回退 | 不在合同切片启用真实工具或改既有账本 | 仅五条路径；V13字节不变；无AS/Store/客户端/配置/权限/JSON变更 | README/plan/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先在现有port添加两个record声明和default check的返回null最小scaffold，保持authorize唯一abstract；随后在原ToolExecutionContractTest编写真实行为RED，最后回访同文件实现校验和一次authorize委托。scaffold只供编译，不独立提交。文档和证据在GREEN后完成。

### 4.2 Test-first strategy

三组测试：SAM lambda/调用次数兼容；USER/SERVICE成功审计；错误schema、类型/版本槽、票据、expiry、boundary/actor mismatch、拒绝携带上游字段及脱敏。合法fixture建立后派生结果或拒绝断言失败才是RED，不接受编译/fixture错误作行为证明。

### 4.3 Sequential and parallel boundaries

仅Step1 In Progress；只读后续AS设计可并行，生产适配/签发/Store接线不得混入。此步骤commit后才开始后续步骤。

### 4.4 Commit boundaries

一次提交包含§5全部五条路径。保持旧authorize调用者与现有Store行为；显式路径暂存、不改历史、不push。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| typed check/audit | T3-07逐工具当前权限及审计 | ToolExecutionAuthorization已有SAM与三边界，V13已有十一键audit形状 | 在现有port嵌套不可变值/default方法，不新建服务/DTO层 | Implement |
| 真实权限/JSON入库 | 同T3-07后续接入 | 现有AS能力与V13冻结均需后续精确适配 | 当前只域合同，不造签发或codec | 留后续独立步骤 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 兼容值合同 | PLAN-REQ-001/002/003 | 原ContractTest新增真实RED | 最小嵌套声明，既有Request/Decision | CheckResult/AuditVO/default check | 后续真实authorizer/audit codec | Step 1 |
| 说明/证据 | PLAN-REQ-004 | 实际diff/回归 | GREEN | README/报告 | 用户审查 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | AR/domain/tool及既有port/ContractTest | 保持原模块与SAM | 扩展原port而非新接口/层；无依赖 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Model/validation | Request及旧grant都是不可变domain值 | 结构校验不认证 | CheckResult/AuditVO为真正不可变值；显式约束不创建业务实体/DTO/Converter | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/config | 纯接口及嵌套值，无Spring Bean或wire边界 | 不接运行能力 | 无Bean/DI/JSON/profile/权限变更 | Step1；MC-BEAN-001、MC-JSON-001、MC-CONFIG-001 |
| Pattern | 既有authorizer依赖倒置；default委托 | 保留已有扩展点 | 直接一次委托和固定值规则，不增加策略层 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 身份/边界/票据 | TrustedAgentActor.SubjectType、port三边界、原grant8/9 namespace | 现有domain类型/正则格式 | 足够；缺失typed审计值 | 复用类型/namespace，不引库 | None | Step1；MC-REUSE-001、MC-DEP-001 |
| 时限 | Request.deadlineAt、run.deadlineAt、Instant | 既有绝对deadline | 纯比较足够 | 不新增Clock/DB/时间工具 | None | Step1；MC-TIME-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | Result/VO职责 | port声明→test→port完成 | CheckResult/AuditVO；既有Request名兼容保持 | 编译/声明审阅 | Step1 | PASS |
| Rule 2 | §13/T3-07 | 既有domain紧构造 | test→record校验 | req/boundary/decision、类型/版本/票据/时间匹配；无新增跨层HTTP输入 | 正反域测试 | Step1 | PASS |
| Rule 3 | §13/T3-07 | 不可变domain值 | 两嵌套record | 只有不可变字段，不作为实体/认证对象 | 构造/脱敏 | Step1 | PASS |
| Rule 4 | §6/T3-07 | 接口与值，没有业务Bean | port/test | 无新业务服务/DI | 差异审阅 | Step1 | N/A：无业务Bean |
| Rule 5 | §6/T3-07 | JDK/原domain类型 | port/test | 无工具库依赖 | import/POM审阅 | Step1 | PASS |
| Rule 6 | §13/T3-07 | 无新wire/JSON/persistence合同 | port/test | 不加codec或JSON映射；V13十一键留后续 | 范围审阅 | Step1 | N/A：无JSON边界 |
| Rule 7 | §6/T3-07 | 无环境文件变化 | README/报告 | 配置结构不变 | 路径审阅 | Step1 | N/A：无配置 |
| Rule 9 | §13/T3-07 | 既有port依赖倒置 | default check/值构造 | 简单固定规则直接校验，保留SAM | lambda/一次调用测试 | Step1 | PASS |
| Rule 10 | §7/13/T3-07 | Instant/request/run deadline | records/test | 绝对比较，不取now/Clock | TEST-002 | Step1 | PASS |
| Rule 11 | §6/T3-07 | 原AR域边界/冻结V13 | 五条路径 | 无新层/SQL/Store/运行接线 | 回归/diff | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AuditVO.checkedAt/expiresAt、Request/run.deadlineAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时间比较无日历计算 | Not applicable: 无UI | ALLOW expiry>checked；auditedALLOW checked<request deadline<=expiry<=run deadline；拒绝expiry为null | 保留Instant精度，不读取now/Clock、不增加DB舍入 | 既有Request/run绝对deadline不变 | Step 1；TEST-002：等于/越过边界与拒绝空字段 |

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java | 兼容SAM/default方法及嵌套值 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionContractTest.java | 复用原测试，新增三组审计合同覆盖 | Step1 |
| MODIFY | README.md | 精确局部能力/未接线边界 | Step1 |
| CREATE | docs/egon/evidence/2026-10-31-tool-authorization-audit.md | 实际测试及17MC/10Rule报告 | Step1 |
| CREATE | docs/egon/plan/2026-10-31-20-05-tool-authorization-audit-contract.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline7005117；用户AGENTS适用，其他工作保留。JDK25/source-reactor及已有domain/JUnit依赖足够。唯一V13 SHA保持`f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3`，本步骤禁止改SQL/Grant/Outcome/Store/AS/RPC/权限/配置/JSONcodec。default legacy ALLOW可以带audit=null以兼容旧host，但不成为DISPATCH凭据；未来dispatch必须明确拒绝缺audit。localCheckId/checkedAt是本地元数据，不虚构上游decisionId或认证事实。未来十一键/4096byte JSON持久表示另行接线，本步骤无wire合同扩展。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 为现有SAM授权port增加兼容审计值合同

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004
- Dependencies: 已提交7005117、现有ToolExecutionAuthorization/Request、ContractTest。
- Baseline state: authorize是唯一abstract；原三项合同测试存在；check/审计值尚不存在。
- Observable outcome: 旧lambda仍可用，default check只委托一次；typed审计的subject/boundary/decision/版本/时限有明确值约束。
- End state: 纯域合同和回归可用；没有真实authorizer/签发/审计codec/dispatch/接线。
- Test-first gate: Required — 先类型声明和返回null的default scaffold供编译，再真实行为RED，最后回访原port完成；不以编译失败或fixture错误作RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java`

- Purpose: Add compile-only default/record declarations while retaining the original SAM.
- Symbols: @FunctionalInterface; authorize(Request,Boundary); default check; CheckResult and AuditVO.
- Repository evidence: Existing port has one abstract authorize, three Decision values and three Boundary values.
- Dependencies and consumers: Existing Request/TrustedAgentActor.SubjectType/UUID/Instant only; legacy Store lambdas remain valid.
- Why now: Tests must compile against exact new types before behavior is supplied.
- Contract/signature changes: CheckResult(Request,Boundary,Decision,AuditVO); AuditVO(int schemaVersion,UUID localCheckId,Boundary boundary,Decision decision,SubjectType subjectType,String grantId,Long authVersion,Long catalogVersion,Long policyVersion,Instant checkedAt,Instant expiresAt).
- Input/output and state mapping: Declare immutable record fields with no functional validation yet; default check returns null only until real RED.
- Error and edge behavior: Do not add an abstract method, change authorize/enum/Request or implement AS/codec/dispatch.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; pure immutable-domain/value changes, no Bean/JSON/config surface.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; retain the existing port/SAM, use immutable values and absolute Instant comparisons; document untouched concerns as separate N/A.

- Implementation pseudocode:

```text
@FunctionalInterface interface ToolExecutionAuthorization {
    Decision authorize(Request request, Boundary boundary); // unchanged sole abstract method
    default CheckResult check(Request request, Boundary boundary) { return null; } // compile-only RED prerequisite
    // Declare CheckResult and AuditVO with the exact immutable fields listed above, no validation yet.
}
```

- Verification contribution: Compilation prerequisite only; not a behavioral or authority pass.
- After this file: Type declarations compile; immediately execute File2 RED, never commit the scaffold.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionContractTest.java`

- Purpose: Define SAM compatibility, typed USER/SERVICE audits and invalid metadata boundaries.
- Symbols: Three added test groups in ToolExecutionContractTest; retain all original assertions.
- Repository evidence: Existing JUnit5 domain tests use real immutable objects and fixed Instant NOW.
- Dependencies and consumers: File1 declarations, existing Request/run/lease fixtures and AtomicInteger for call count.
- Why now: A valid lambda/fixture can now expose default-null and missing validation as real RED.
- Contract/signature changes: No production signature changes; use direct value construction, no JSON, network, DB or reflection workaround.
- Input/output and state mapping: Assert exactly one authorize call and audit-null default result; typed USER9/auth0/catalogNull and SERVICE8/authNull/catalogPositive successes.
- Error and edge behavior: Wrong schema/null base fields/type/version slot/grant namespace/expiry/boundary/actor mismatch and refusal with upstream fields all reject; each toString hides fields.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; pure immutable-domain/value changes, no Bean/JSON/config surface.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; retain the existing port/SAM, use immutable values and absolute Instant comparisons; document untouched concerns as separate N/A.

- Implementation pseudocode:

```text
@Test void legacy_sam_is_called_once_and_retains_audit_null_compatibility() {
    calls = new AtomicInteger(); ToolExecutionAuthorization host = (req,b) -> { calls.incrementAndGet(); return ALLOW; };
    result = host.check(validRequest(), PREPARE); assertNotNull(result); // real RED: default scaffold returns null
    assertEquals(1,calls.get()); assertEquals(ALLOW,result.decision()); assertNull(result.audit());
}
// Add successful USER/SERVICE audits plus invalid schema/slots/time/mismatch/denial-field and redaction assertions.
```

- Verification contribution: TEST-001 real behavior RED/GREEN; TEST-002 precise Instant/subject/version boundaries.
- After this file: Tests compile and fail for missing behavior rather than setup or compilation errors.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java`

- Purpose: Complete compatible delegation and exact immutable audit constraints after RED.
- Symbols: default check; CheckResult/AuditVO compact constructors; fixed redacted toString for both.
- Repository evidence: Existing source separates current tool authority from run permission; new values follow immutable-domain conventions.
- Dependencies and consumers: Existing authorize implementation remains the one authority port; no external adapter is supplied here.
- Why now: File2 establishes real missing behavior and compatibility obligations.
- Contract/signature changes: check calls authorize exactly once then returns new CheckResult(request,boundary,decision,null), preserving the original SAM and exceptions.
- Input/output and state mapping: AuditVO schema1, nonnull local UUID/boundary/decision/subject/checkedAt; ALLOW policy>=0, expiry>checked, USER9/auth>=0/catalogNull, SERVICE8/authNull/catalog>0; refusal all upstream fields/expiry null.
- Error and edge behavior: CheckResult nonnull request/boundary/decision; present audit must match boundary/decision/actor type; audited ALLOW checked<request.deadline<=expiry<=run.deadline; audit-null legacy ALLOW is metadata, not dispatch evidence.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; pure immutable-domain/value changes, no Bean/JSON/config surface.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; retain the existing port/SAM, use immutable values and absolute Instant comparisons; document untouched concerns as separate N/A.

- Implementation pseudocode:

```text
default CheckResult check(Request request, Boundary boundary) {
    Decision decision = authorize(request,boundary); return new CheckResult(request,boundary,decision,null);
}
AuditVO: require schemaVersion1/localCheckId/boundary/decision/subjectType/checkedAt; validate typed ALLOW grant/versions/time or all-null refusal evidence;
CheckResult: require request/boundary/decision; match present audit tuple; for audited ALLOW require checkedAt<request.deadlineAt<=expiresAt<=request.run.deadlineAt;
Override both toString values with fixed redacted labels; document local metadata is not authentication and future DISPATCH rejects missing audit.
```

- Verification contribution: TEST-001/002 GREEN plus existing tool/Store regression as specified in section8.
- After this file: Compatible domain contract is complete; no issuer, real IAM, codec or execution has been enabled.

#### File 4 — `MODIFY README.md`

- Purpose: Record only the compatible audit contract and its remaining integration boundary.
- Symbols: Existing ToolExecutionAuthorization/ToolExecutionGrant/journal progress paragraphs.
- Repository evidence: README already distinguishes metadata facts and independently verified execution authority.
- Dependencies and consumers: Final domain behavior and actual tests, no runtime configuration changes.
- Why now: Describe implemented behavior only after GREEN.
- Contract/signature changes: No API or deployment changes.
- Input/output and state mapping: Explain SAM/default compatibility, typed version slots, coverage window, localCheckId and redaction.
- Error and edge behavior: Audit-null ALLOW is compatible legacy metadata, never a dispatch credential; no upstream decisionId or cryptographic authenticity claim.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; pure immutable-domain/value changes, no Bean/JSON/config surface.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; retain the existing port/SAM, use immutable values and absolute Instant comparisons; document untouched concerns as separate N/A.

- Implementation pseudocode:

```text
Find the current independent tool-authorizer and journal progress paragraphs.
Add the tested default check and typed AuditVO/CheckResult facts, emphasizing exactly one legacy callback.
Retain real AS/grant/crypto/current-IAM/audit-codec/dispatch integration as pending; no SQL or running feature is activated.
```

- Verification contribution: TEST-003 documentation/source scope comparison.
- After this file: README remains accurate about domain-only delivery.

#### File 5 — `CREATE docs/egon/evidence/2026-10-31-tool-authorization-audit.md`

- Purpose: Persist actual RED/GREEN, compatibility and independent standards checks.
- Symbols: Baseline7005117, selected tests/results,17 MC/10 Rule rows, local traceability/full-T3-07 status.
- Repository evidence: Earlier grant/storage reports and the executing skill require evidence per check.
- Dependencies and consumers: Actual final diff, command exits and tests; no assumed runtime result.
- Why now: Fill evidence only after corresponding validation is run.
- Contract/signature changes: No product contract; source hashes and result boundaries only.
- Input/output and state mapping: Map each local requirement to code/test evidence; full T3-07 stays Partial with real IAM/crypto/dispatch pending.
- Error and edge behavior: Keep failed attempts/skips honest; preserve V13 SHA, no unreviewed scratch harness or runtime startup.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; pure immutable-domain/value changes, no Bean/JSON/config surface.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; retain the existing port/SAM, use immutable values and absolute Instant comparisons; document untouched concerns as separate N/A.

- Implementation pseudocode:

```text
Record baseline7005117 and actual real RED/GREEN commands, selected test counts and failures/skips if any.
Evaluate17 MC and10 Literal Rules independently with source/diff/test evidence and valid N/A reasons.
Record unchanged V13 and five-path scope; mark real authority/audit-persistence/dispatch and platform acceptance pending.
Confirm no automatic service/database/browser/push or undetermined scripts/work harness; commit only the declared paths.
```

- Verification contribution: TEST-003 final evidence and explicit commit scope, not a prefilled pass.
- After this file: All slice gates are recorded before the root stages and commits.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolExecutionContractTest,ToolExecutionGrantTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest,ToolExecutionJsonCodecTest -Dsurefire.failIfNoSpecifiedTests=false`; then the Store regression in section8 if compatibility concerns remain; `git diff --check`.
- Expected result: focused real RED then all selected contract/domain tests GREEN, unchanged legacy lambda behavior; report actual counts, not predicted totals.
- Failure returns to: File2 fixture/assertion or File3 contract/delegation; dependency/compilation issues are not behavior RED.
- Completion criteria: four local aliases have implementation evidence; all17MC/10Rules individually PASS or justified N/A; SAM compatibility, private output and unchanged V13 confirmed.
- Rollback: explicit revert of this domain-only commit; no schema/data/runtime migration to undo, preserve prior history and unrelated work.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionAuthorization.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionContractTest.java`, `README.md`, `docs/egon/evidence/2026-10-31-tool-authorization-audit.md`, `docs/egon/plan/2026-10-31-20-05-tool-authorization-audit-contract.md`
- Commit: `feat(agent): add compatible tool authorization audit contracts`

## 8. Test, Validation, and Quality Gates

TEST-001：先执行§7定向命令，仅新增合同断言真实RED后完成实现并GREEN，保留原测试。TEST-002：同命令覆盖subject/version/expiry及checkedAt/请求/run deadline边界；固定Instant，不读取Clock/now。TEST-003：`git diff --check`、五路径diff、SAM声明/调用次数、V13字节及17MC/10Rules。

若default/SAM对旧host有未解兼容疑点或必要编译影响，再执行原三个Store/verifier回归：`./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolExecutionStorageVerifierTest,ToolCapabilityStoreTest,ToolActionIntentStoreTest,ToolExecutionJournalPostgresTest,ToolExecutionContractTest,ToolExecutionGrantTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest,ToolExecutionJsonCodecTest -Dsurefire.failIfNoSpecifiedTests=false`。默认PG类跳过不能当运行通过，不为本纯域步骤主动提供DB或启动服务。

TEST-004：工作区根运行`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-20-05-tool-authorization-audit-contract.md --strict`。复用既有命令，不创建scripts/work harness；文档生成临时脚本完成后删除，不提交。

## 9. Migration, Compatibility, Rollout, and Rollback

没有迁移、数据回填、事件/JSON/HTTP/RPC/配置/权限变更。V13及已发布历史保持不变；两个record是纯域审计值，不写V13。唯一abstract authorize和原调用路径保持；default check委托一次，audit=null保留legacy语义但不授dispatch权限。真实adapter、codec和执行门禁后续独立交付；当前无需部署或启动。可直接revert本域合同提交，不改写历史或数据。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §6/13/T3-07 | Step 1 | port/ContractTest | TEST-001/003 | 单abstract、lambda兼容、一次调用 |
| PLAN-REQ-002 | §13/T3-07 | Step 1 | AuditVO/CheckResult、原test | TEST-001/002 | typed版本/票据/拒绝字段约束 |
| PLAN-REQ-003 | §7/13/T3-07 | Step 1 | 两record/原test | TEST-002 | 时限匹配、脱敏、metadata非认证 |
| PLAN-REQ-004 | §6/T3-07 | Step 1 | README/plan/evidence | TEST-003/004 | 五路径，无SQL/运行扩张；完整T3-07仍Partial |

## 11. Risks, Blockers, and User Decisions

原蓝图已获本轮执行授权，本文保持Review准确反映legacy状态。当前slice无未决设计；audit-null ALLOW只兼容旧host，后续DISPATCH必须显式拒绝缺audit。typed值构造不证明grant密文、真实当前权限或审计真实性；实际AS适配/签发/密文/currentIAM/JSON持久/执行仍待办。无理由在此切片启动服务、数据库或浏览器。

## 12. Review and Acceptance

此计划只细化原T3-07独立权限与审计的兼容值合同，不新增业务交互、框架、引擎或通用层。File1声明→File2真实RED→File3回访同文件完成在一个步骤内，禁止scaffold独立提交。五条提交路径明确，原迁移不可变，实际验证与未完成边界由报告记录。

### 12.5 Blocking Manual Check

以下为计划级可执行性检查；实施后在报告按实际结果逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/domain/tool及§5五路径 | 只扩原port，无层/模块变化 | None |
| MC-REUSE-001 | Applicable | PASS | 原Request/主体/边界/票据namespace | 不复制身份/授权框架 | None |
| MC-DEP-001 | Applicable | PASS | §5无POM或工具路径 | 无新依赖 | None |
| MC-NAME-001 | Applicable | PASS | CheckResult、AuditVO；原Request保留 | 角色清楚且保持兼容 | None |
| MC-VALID-001 | Applicable | PASS | record约束及三组正反测试 | 纯域值边界，无新HTTP/业务传输模型 | None |
| MC-MODEL-001 | Applicable | PASS | 全部字段为不可变值/标量 | 真值records，不是实体或可变POJO | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层映射 | 不绕过BaseConverter/MapStruct | None |
| MC-LOG-001 | Not applicable | N/A | 接口/值无业务服务或日志 | fixed toString避免私密内容 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/autoconfig | 无新注入/Qualifier面 | None |
| MC-UTIL-001 | Applicable | PASS | JDK/既有domain | 无工具库/重复utility | None |
| MC-JSON-001 | Not applicable | N/A | 本步骤无JSON/wire/入库codec | V13十一键及4096byte留后续，不假装JSON验收 | None |
| MC-TIME-001 | Applicable | PASS | §4.9、TEST-002 | Instant绝对比较，无Clock/默认zone | None |
| MC-CONFIG-001 | Not applicable | N/A | 不修改profile/properties | 配置结构不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 原authorizer依赖倒置及一次default委托 | Simple规则直接处理，不造策略层 | None |
| MC-SCOPE-001 | Applicable | PASS | §5/commit paths五文件 | 无SQL/Store/AS/Grant/权限扩张 | None |
| MC-TEST-001 | Applicable | PASS | §8真实RED/GREEN及必要兼容回归 | 实际输出写报告，不预填运行通过 | None |
| MC-BLOCKER-001 | Applicable | PASS | §11及其余独立行 | 当前域合同slice无未决设计，完整任务待办明确 | None |

### 12.6 Final verdict

PASS — Ready for user review
