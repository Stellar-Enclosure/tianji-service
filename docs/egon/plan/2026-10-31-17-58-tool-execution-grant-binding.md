# T3-07 独立工具执行凭据绑定合同

| Field | Value |
| --- | --- |
| Document | `2026-10-31-17-58-tool-execution-grant-binding.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 17:58 Asia/Shanghai` |
| Updated | `2026-10-31 17:58 Asia/Shanghai` |
| Owner | 用户授权，主代理实施和审计 |
| Repository | `tianji-service` |
| Scope | T3-07 纯领域工具授权凭据绑定值；无存储、签发器、权限/API/运行装配 |
| Source Requirement | 用户要求继续原 plan 未完成工作，并按逻辑任务分步验证和提交 |
| Baseline Revision | `main@566a66526809cd7b99d9468912126609e3f24ff3` |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户本轮明确授权剩余工作，原文无规范状态字段 |
| Spec Revision | SHA-256 `1d1f706a9594a6fc3b2b9ca80cfce968f3db806a926164ec97bc8a7b391fc6a9`; 2026-10-31；本轮执行读取的 T3-07/§13/§14 设计 |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [codec 先行步骤](2026-10-31-17-47-tool-journal-codec-boundary.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

原 T3-07 要求每次工具动作具备独立当前权限、固定 tool/version/hash 与可信身份，不能拿原 run 的 CLAIM/MODEL 权限替代。此切片只建立可供后续签发/验证/存储复用的不可变绑定合同。USER/SERVICE 沿现有两种 grant 约束分开；构造值和摘要不是认证或授权。一个步骤一个提交，后续 journal、签发、审批消费、派发和 ADK callback 均继续待办。

## 2. Target Spec and Effective Design

唯一主设计是头部原蓝图 §6、§13、§14 和 §19 T3-07；当前域基线已有 `ToolExecutionAuthorization`、`ToolActionRequest`、`AgentExecutionGrant`、`AgentServiceExecutionGrant`。前一步 566a665 已完成私有 codec。本文仅细化剩余独立工具授权绑定，不替代原蓝图、不伪造其 Accepted 状态。旧蓝图中历史进度不回退实际已提交能力。

## 3. Effective Requirements and Acceptance

下列 PLAN-REQ 别名只追踪原文已有要求，不新增产品合同。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13、T3-07 逐工具授权 | 工具凭据须独立于 run 目的、绑定完整可信身份及固定工具资源 | USER/SERVICE 不混用；工具 purpose 不能使用 run purpose；完整 run/source/tool/resource 改变摘要 | BindingVO 与域测试 |
| PLAN-REQ-002 | §13、T3-07 固定 descriptor/version/hash | 固定绑定漂移必须可检测，不能产生第二份同槽授权事实 | 同 owner/run/tool key/version 的 capabilityId 固定；改变绑定内容只改摘要；跨 owner/run/version ID 不同 | 长度前缀版本化摘要 |
| PLAN-REQ-003 | §7.2、§13、T3-07 时限与凭据治理 | 凭据不得越过 run deadline 或混用身份类型；不保留 USER bearer | epoch>0；8/9 grantId 与 owner 类型相符；密文格式/长度正确；expiry不晚于run；toString脱敏 | ReservationVO、EvidenceVO |
| PLAN-REQ-004 | §6、T3-07 禁改范围 | 保持既有依赖与边界，合同不等于实际权限 | 无 POM/DDL/API/权限/开关/运行入口变更；文档保留真实验收边界 | 差异、README、证据 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先新增最小类型声明 scaffold，使真实领域测试可编译；派生方法暂返回 `null`。随后编写并运行测试，观察缺少绑定行为的真实 RED；最后完成同文件校验与派生值。scaffold 不独立提交，不把编译失败当 RED。

### 4.2 Test-first strategy

测试稳定槽 ID/绑定摘要、全部可信来源和工具字段、USER/SERVICE 混用、URI/UUID/legacy/purpose、票据类型/expiry/epoch/脱敏。合法 fixture 调用派生方法失败是预期 RED；完成实现后同断言 GREEN。复用现有 grant 和 digest，不新造工具框架。

### 4.3 Sequential and parallel boundaries

Step1 依赖 566a665；只读 schema 审阅可并行，任何 journal/签发/派发编辑不得混入。此步骤 commit 后才锁定下一步骤。

### 4.4 Commit boundaries

一个显式路径提交包含域合同、测试、README、本计划和报告；保留未跟踪 `ToolActionIntentSql.java`，不修改它。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 独立工具绑定 | §13/T3-07 不得复用run授权 | ToolExecutionAuthorization.Request 缺少可密封凭据的独立purpose/resource绑定 | 复用两种既有grant检查和digest；无额外服务/模型层 | Implement |
| capability槽 | T3-07 幂等与绑定漂移拒绝 | ToolActionRequest.intentId 是业务动作ID，不能作为工具凭据槽 | owner/run/tool版本派生，参数与业务intent不入槽 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 领域绑定/密封证据合同 | PLAN-REQ-001/002/003 | TEST-001 派生方法尚未实现 | 最小声明、既有grant和digest | BindingVO/ReservationVO/EvidenceVO | 后续独立工具凭据验证/Store | Step 1 |
| 范围与证据 | PLAN-REQ-004 | TEST-003 差异审阅 | 真实GREEN | README/报告 | 用户审查 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | 现有 AR/domain/tool 与原蓝图 §6 | 保持实际天机模块 | 仅纯域值合同；无新层 | Step1；MC-ARCH-001 |
| Model/validation | 既有grant的不可变Ticket/Reservation、域构造校验 | 可信值不可变 | 新records为值而非实体；复用来源校验，无新增层间DTO | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/config | 静态域合同容器，无Spring/JSON/配置 | 无新运行依赖 | 不创建Bean/Converter/日志/外部协议 | Step1；MC-BEAN-001、MC-JSON-001、MC-CONFIG-001 |
| Pattern | 固定值构造与摘要 | Simple直接实现 | 不为USER/SERVICE建策略层 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 来源与票据结构 | AgentExecutionGrant、AgentServiceExecutionGrant | fingerprint/Ticket；SERVICE Subject | 已有完整身份/URI/票据规则 | 直接复用 | None；仅域合同 | Step1；MC-REUSE-001、MC-DEP-001 |
| 摘要 | OrchestrationRequestDigests | hash(List,Map)长度前缀UTF8 | 足够，版本标签隔离新含义 | 复用，不新增digest工具 | None | Step1；MC-UTIL-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | grant/VO职责 | 声明→test→实现 | Grant容器、VO值、Test命名 | 编译/声明检查 | Step1 | PASS |
| Rule 2 | §13/T3-07 | 既有域构造校验 | test→构造校验 | 非HTTP纯值合同，无新跨层输入；来源/URI/hash/epoch/expiry校验 | 正反域测试 | Step1 | PASS |
| Rule 3 | §13/T3-07 | 邻近不可变grant records | BindingVO/ReservationVO/EvidenceVO | 真正不可变值；不新增实体/Converter | 结构/构造测试 | Step1 | PASS |
| Rule 4 | §6/T3-07 | 静态合同容器无业务Bean | 合同文件 | 无注入/业务日志 | 差异检查 | Step1 | N/A：无业务类 |
| Rule 5 | §6/T3-07 | JDK和原digest | 合同→test | 不新增工具库 | imports/POM检查 | Step1 | PASS |
| Rule 6 | §13/T3-07 | 无JSON/wire模型 | 合同文件 | 不新增序列化映射或枚举 | 差异检查 | Step1 | N/A：无JSON边界 |
| Rule 7 | §6/T3-07 | 无配置路径 | README/报告 | 配置键不变 | 路径检查 | Step1 | N/A：无配置 |
| Rule 9 | §13/T3-07 | 纯值约束与摘要 | 合同文件 | Simple直接实现，复用grant校验 | 控制流审阅 | Step1 | PASS |
| Rule 10 | §7.2/T3-07 | 既有run绝对deadline | EvidenceVO→test | Instant；expiry不得晚于run；不读取全局当前时间 | TEST-002 | Step1 | PASS |
| Rule 11 | §6/T3-07 | AR/domain/tool原结构 | 全部文件 | 不增DAO/迁移/事件发布/层 | source-reactor/diff | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | EvidenceVO.expiresAt、binding.run.deadlineAt | Occurred instant | Instant | Not persisted | Internal | Not applicable: 绝对时间比较无日历计算 | Not applicable: 无展示 | expiry<=run deadline；无本地时间/DST转换 | 保留Instant精度，不新增Clock/DB舍入 | 复用现有run/ticket的Instant合同 | Step 1；TEST-002，含等于/晚于deadline边界 |

ToolExecutionGrant adds `public static final boolean SESSION_REQUIRED=false`: independent tool tickets never grant Session access. The original `run.sessionRequired` remains part of the source fingerprint; tests assert the constant is false and changing the run flag changes bindingSha256.

## 5. Change File Tree

路径前缀 `M=stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/`，`T=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/`。

| Operation | Path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | M/ToolExecutionGrant.java | 唯一纯域合同容器，声明scaffold与完成实现是同文件两次阶段 | Step1 |
| CREATE | T/ToolExecutionGrantTest.java | 真实RED/GREEN | Step1 |
| MODIFY | README.md | 范围/未完成说明 | Step1 |
| CREATE | docs/egon/evidence/2026-10-31-tool-execution-grant.md | 实际验证和两个独立矩阵 | Step1 |
| CREATE | docs/egon/plan/2026-10-31-17-58-tool-execution-grant-binding.md | 本执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

baseline 566a665，用户AGENTS为适用规则。保留SQL草稿；仅本表路径暂存。JDK25、既有source-reactor/同级依赖可用；不新增依赖、不启动应用/DB/浏览器/容器。所有迁移不可变；本切片没有SQL。局部澄清：独立工具槽ID不等于业务actionIntent，槽稳定性只为后续Store检测同槽绑定漂移；实际权限须由fresh Jianshen exact Expectation及密文验证证明，构造器永远不能产生ALLOW。

PLAN-CLAR-003：既有 source fingerprint 使用 URI 的精确 ASCII 表示，保留 scheme/host 大小写；Java URI.equals 会忽略这些大小写。BindingVO 的值相等性和 hashCode 必须同时保留 audience 及 SERVICE source resource 的精确 ASCII 表示，避免“值相等但绑定摘要不同”。先加入这一边界的行为 RED，再在当前合同文件实现；不改写或归一化原 URI，也不改变既有 grant 字节协议。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 建立独立工具绑定与密封证据的纯域合同

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004
- Dependencies: 已提交566a665；既有run USER/SERVICE grant、工具合同、OrchestrationRequestDigests。
- Baseline state: 上述复用对象存在；新ToolExecutionGrant/其测试尚不存在。
- Observable outcome: 稳定工具槽和完整绑定摘要可验证，结构无效的凭据拒绝；构造值不会取得权限。
- End state: 纯域合同/test/文档可用；无签发、存储、当前鉴权、审批消费、工具派发或ADK挂载。
- Test-first gate: Required — 先最小声明供编译，派生方法返回null；真实测试因未实现行为RED，再完成同文件，不以编译失败作RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionGrant.java`

- Purpose: 仅新增可编译声明，派生值暂返回null，构造器暂不校验；完整实现等待File2行为RED。
- Symbols: ToolExecutionGrant静态容器；BindingVO、ReservationVO、EvidenceVO不可变records。
- Repository evidence: AgentExecutionGrant/AgentServiceExecutionGrant的静态合同容器与不可变值构造风格。
- Dependencies and consumers: run/tool ref、URI、SERVICE Subject；File2直接测试，无运行消费者。
- Why now: 声明是测试编译前提，scaffold不得独立提交。
- Contract/signature changes: BindingVO(run,tool,backendBindingSha256,purpose,resource,authorizationResourceKey,serviceSubject)；ReservationVO(binding,issueEpoch)；EvidenceVO(reservation,grantId,ciphertext,expiresAt)；BindingVO两个派生方法返回String。
- Input/output and state mapping: 仅固定不可变分量和签名，尚无身份校验、摘要或权限能力。
- Error and edge behavior: 不新增ALLOW/签发/存储方法，不在scaffold添加功能性实现。
- Standards impact: MC-NAME-001、MC-MODEL-001、MC-SCOPE-001；无实体/Bean/Converter。
- Literal rule enforcement: Rule 1、Rule 3、Rule 11；Grant/VO职责名和现有域目录。
- Implementation pseudocode:

```java
public final class ToolExecutionGrant {
    private ToolExecutionGrant() { }
    public static final boolean SESSION_REQUIRED = false;
    // Declare BindingVO(run,tool,backendBindingSha256,purpose,resource,authorizationResourceKey,serviceSubject).
    // Declare ReservationVO(binding,issueEpoch), EvidenceVO(reservation,grantId,ciphertext,expiresAt).
    // BindingVO.capabilityId() and bindingSha256() return null until the behavior RED is observed.
}
```

- Verification contribution: 仅提供File2编译前提，不是功能证明。
- After this file: 类型可编译，立刻编写测试；派生方法返回null供非空断言观察真实RED。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionGrantTest.java`

- Purpose: 先测试稳定槽/绑定漂移、来源、票据、时间和脱敏。
- Symbols: ToolExecutionGrantTest；stable_slot、binding_digest、source_type、evidence、deadline、redaction测试。
- Repository evidence: ToolExecutionContractTest采用JUnit5、固定Instant、真实域对象。
- Dependencies and consumers: File1和两种既有grant，无DB/HTTP/模型fixture。
- Why now: 声明可编译，合法fixture之后的派生非空/差异断言可真实RED。
- Contract/signature changes: 仅测试新值，无公开API或外部协议改动。
- Input/output and state mapping: 同槽metadata变→同ID不同digest；跨owner/run/tool版本→不同ID；完整run/source/tool/resource维度改→不同digest。
- Error and edge behavior: LEGACY/非canonicalUUID/不安全URI/错误source类型/hash/purpose拒绝；8/9票据混用、epoch<=0、expiry>deadline拒绝；过去expiry不被误当当前权限。
- Standards impact: MC-VALID-001、MC-MODEL-001、MC-TIME-001、MC-TEST-001；值构造与固定Instant。
- Literal rule enforcement: Rule 2、Rule 3、Rule 10、Rule 11；逐个约束和时限正反测试。
- Implementation pseudocode:

```java
@Test void fixed_slot_detects_binding_drift_without_authorizing_a_call() {
    BindingVO original = validUserBinding();
    assertNotNull(original.capabilityId()); // scaffold null is behavior RED after valid fixture setup
    assertNotNull(original.bindingSha256());
    assertEquals(original.capabilityId(), changedBackendBinding(original).capabilityId());
    assertNotEquals(original.bindingSha256(), changedBackendBinding(original).bindingSha256());
    // Mutate all source/run/tool fields, reject type/URI/purpose/ticket drift and test deadline equality.
}
```

- Verification contribution: TEST-001真实RED；TEST-002票据/deadline/脱敏。
- After this file: 测试编译并因缺少派生/约束行为失败，不能将fixture或编译错误算RED。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionGrant.java`

- Purpose: File2真实RED后回访File1同文件，完成全部纯值校验与摘要。
- Symbols: BindingVO构造、capabilityId、bindingSha256；ReservationVO/EvidenceVO构造；全部toString。
- Repository evidence: 两种旧grant的fingerprint/Ticket；OrchestrationRequestDigests.hash的长度前缀UTF8。
- Dependencies and consumers: 直接复用以上合同；未来authorizer/Store消费，本步不接线。
- Why now: RED已固定缺失行为，同一步完成scaffold而不另提交。
- Contract/signature changes: tool/authorizationResourceKey用ToolPolicy.key，backend为64小写hex，purpose为TIANJI_TOOL_[A-Z][A-Z0-9_]{0,83}（总长<=96）；USER无serviceSubject，SERVICE必有且clientId=ownerId。
- Input/output and state mapping: capabilityId仅domain标签+owner.identityFingerprint+runId+tool key/version→hash前128位canonicalUUID；bindingSha256独立标签+旧来源fingerprint+tool完整ref+backend+purpose+authorizationResourceKey→SHA256。resource是既有token audience URI，不必是HTTP路径；构造不注册远端policy。
- Error and edge behavior: 旧fingerprint核canonicalrunUUID/URI/来源；Reservation epoch>0；Evidence复用相应8/9 Ticket，expiry<=run deadline；所有toString固定脱敏；不读取当前时钟、不证明密文或当前权限。
- Standards impact: MC-REUSE-001、MC-VALID-001、MC-MODEL-001、MC-UTIL-001、MC-TIME-001；Simple直接复用，无新Bean/工具库。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 5、Rule 9、Rule 10、Rule 11；不可变值、来源分离、Instant绝对比较。
- Implementation pseudocode:

```java
BindingVO: require run/tool; reject LEGACY, invalid key/hash/authorizationResourceKey/purpose or source-type mismatch;
source = USER ? AgentExecutionGrant.fingerprint(run,resource) : AgentServiceExecutionGrant.fingerprint(run,resource,serviceSubject);
capabilityId = UUID(first128(hash(["TIANJI_TOOL_CAPABILITY_V1",owner.identityFingerprint(),runId,tool.key,tool.version],emptyMap)));
bindingSha256 = hash(["TIANJI_TOOL_BINDING_V1",source,tool.key,tool.version,tool.sha256,backendBindingSha256,purpose,authorizationResourceKey],emptyMap);
ReservationVO: require binding and issueEpoch>0; EvidenceVO: validate correct 8/9 Ticket and expiresAt<=binding.run.deadlineAt;
Override each toString with fixed redacted label; metadata alone never proves authority or ciphertext contents.
```

- Verification contribution: TEST-001/002全部GREEN，原工具合同回归不变。
- After this file: 纯域合同具备测试证据，仍无签发、当前权限检查、存储或执行入口。

#### File 4 — `MODIFY README.md`

- Purpose: 记录绑定合同和后续未完成边界。
- Symbols: 既有T3-07工具合同段。
- Repository evidence: README已说明独立authorizer/codec尚未接线。
- Dependencies and consumers: File3实际结果供使用者阅读。
- Why now: GREEN后才描述实现。
- Contract/signature changes: 无API/配置改变。
- Input/output and state mapping: 稳定slot、绑定digest、密封证据值→局部能力说明。
- Error and edge behavior: fresh exact Expectation/密文验证/签发/journal/派发仍待接；出站Process仍为独立真实SERVICE；不虚构decisionId。
- Standards impact: MC-SCOPE-001、MC-TEST-001；仅说明。
- Literal rule enforcement: Rule 11；保持模块及可信来源边界。
- Implementation pseudocode:

```text
Find the independent ToolExecutionAuthorization and codec paragraphs.
Append tested BindingVO/ReservationVO/EvidenceVO and stable slot versus changed binding facts.
State that metadata is not authority and fresh exact permission/ciphertext validation and execution remain pending.
```

- Verification contribution: TEST-003说明与实现核对。
- After this file: README只描述实际合同，不扩大真实IAM/数据库验收结论。

#### File 5 — `CREATE docs/egon/evidence/2026-10-31-tool-execution-grant.md`

- Purpose: 保存实际RED/GREEN与独立规范审计。
- Symbols: baseline、命令结果、17 MC、10 Rule、需求状态。
- Repository evidence: 前一步codec证据和执行skill逐行检查要求。
- Dependencies and consumers: 最终diff/实际测试，供用户审查。
- Why now: 验证后记录事实，不预填通过结果。
- Contract/signature changes: 无产品合同。
- Input/output and state mapping: 每条检查→具体文件/符号/命令证据；完整T3-07仍Partial。
- Error and edge behavior: 结构校验不当作真实签发/鉴权/DB证明；保留SQL草稿。
- Standards impact: MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001；两个矩阵逐行记录。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；分别重评。
- Implementation pseudocode:

```text
Record baseline 566a665, exact RED/GREEN commands and actual exits/counts.
Evaluate each MC and each Rule independently with concrete source/diff/test evidence and justified N/A.
Keep full T3-07 Partial, runtime/fresh authority verification pending; confirm no harness/runtime/push actions.
```

- Verification contribution: TEST-003最终审计/范围核验。
- After this file: 本步骤全部门禁有实际证据才能提交。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolExecutionGrantTest,ToolExecutionContractTest,ToolActionRequestTest,ToolApprovalBindingTest,ToolRegistryTest,ToolExecutionJsonCodecTest -Dsurefire.failIfNoSpecifiedTests=false`；`git diff --check`。
- Expected result: 先非空派生断言等行为RED，后所有命名测试GREEN；实际数量/跳过写入报告。
- Failure returns to: File2 fixture错误或File3行为；编译/依赖/环境失败独立报告，不作为RED。
- Completion criteria: 所有局部别名、17MC/10Rule均有通过/有效N/A证据，无已知失败和越界变更。
- Rollback: 未接线合同可显式revert本步骤提交；保留SQL草稿和既有提交，不重写历史。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionGrant.java`、`stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/domain/tool/ToolExecutionGrantTest.java`、`README.md`、`docs/egon/evidence/2026-10-31-tool-execution-grant.md`、`docs/egon/plan/2026-10-31-17-58-tool-execution-grant-binding.md`
- Commit: `feat(agent): bind independent per-tool execution grants`

## 8. Test, Validation, and Quality Gates

TEST-001：上方source-reactor命令先运行新域测试RED，再保持同断言GREEN。TEST-002：同命令包含原工具合同/codec回归；固定Instant测试expiry等于/晚于run deadline。TEST-003：`git diff --check`、逐行diff、显式stage清单、17MC/10Rule。TEST-004：在工作区根运行 `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-17-58-tool-execution-grant-binding.md --strict`。复用既有工具，无scripts/work临时harness，无真实IAM/DB/模型/浏览器验收。

## 9. Migration, Compatibility, Rollout, and Rollback

无DDL、迁移、配置、权限、API、事件、运行开关或框架修改；所有旧SQL不变。无需要部署的功能；未接线域合同不会增加工具能力。实际签发器、fresh权限验证、审批消费和journal由后续单独步骤承接。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13/T3-07 | Step 1 | Grant/Test | TEST-001 | source/purpose/resource/run绑定 |
| PLAN-REQ-002 | §13/T3-07 | Step 1 | Grant/Test | TEST-001 | 稳定slot与metadata漂移摘要 |
| PLAN-REQ-003 | §7.2/§13/T3-07 | Step 1 | Grant/Test | TEST-002 | 类型/epoch/expiry/脱敏 |
| PLAN-REQ-004 | §6/T3-07 | Step 1 | README/plan/evidence | TEST-003/004 | 无新运行能力，完整任务保持Partial |

## 11. Risks, Blockers, and User Decisions

原蓝图已获本轮用户执行授权，本文保持Review以准确反映legacy metadata。当前纯域切片无未决设计；独立工具签发/密文验证/DB journal仍是后续工作，不能用此值构造或摘要当授权。所有运行验收保持未执行，不新增用户许可请求。

## 12. Review and Acceptance

本文只细化原T3-07已有安全与绑定要求，不新增公共交互、框架或状态机。File1声明→File2行为RED→File3回访同文件GREEN只在本步骤内发生，禁止scaffold独立提交。§5路径清单唯一；实际验证、完整T3-07未完成及运行边界写入证据。

### 12.5 Blocking Manual Check

以下为计划级检查，实施后须在报告根据实际diff/输出逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | AR/domain/tool、蓝图§6 | 仅扩展现有域值合同 | None |
| MC-REUSE-001 | Applicable | PASS | 两种旧Grant、digest hash | 复用来源/Ticket/摘要能力 | None |
| MC-DEP-001 | Applicable | PASS | §5无POM变更 | 无新增依赖/工具 | None |
| MC-NAME-001 | Applicable | PASS | Grant容器及三个VO | 明确值职责，无模糊carrier | None |
| MC-VALID-001 | Applicable | PASS | File3构造与File2正反测试 | 纯域值校验，无新层间传输模型 | None |
| MC-MODEL-001 | Applicable | PASS | 不可变records嵌套不可变run/ref/subject | 值语义，不是实体/可变POJO | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层映射文件 | 不绕过MapStruct/BaseConverter | None |
| MC-LOG-001 | Not applicable | N/A | 静态域合同无业务服务 | 无业务日志，toString脱敏 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Spring stereotype/DI | 无Bean或Qualifier改动 | None |
| MC-UTIL-001 | Applicable | PASS | JDK、原OrchestrationRequestDigests | 无新工具库或digestUtils | None |
| MC-JSON-001 | Not applicable | N/A | 无JSON或wire协议 | 不新增序列化边界 | None |
| MC-TIME-001 | Applicable | PASS | §4.9、TEST-002 | Instant绝对deadline比较，无默认zone | None |
| MC-CONFIG-001 | Not applicable | N/A | §5无环境配置 | profile键集合不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 简单值构造/摘要 | Simple直接复用，避免策略层 | None |
| MC-SCOPE-001 | Applicable | PASS | §5五路径，SQL草稿保留 | 单一合同步骤，无DB/API/运行扩张 | None |
| MC-TEST-001 | Applicable | PASS | §8命名RED/GREEN/回归/静态命令 | 实施报告记录真实结果 | None |
| MC-BLOCKER-001 | Applicable | PASS | 本节独立行及§11 | 当前域切片无未决重大设计 | None |

### 12.6 Final verdict

PASS — Ready for user review
