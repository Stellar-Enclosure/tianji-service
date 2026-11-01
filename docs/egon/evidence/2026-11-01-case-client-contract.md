# Step 35 — 固定 CMMN Case Java 客户端合同证据

- Baseline: `main@59c5fc96976d23d98f6fc41da6f39464f030851d`，实施前 clean。
- Source authority: 原用户授权继续蓝图未完成范围、分步提交并在完成后审查；本步骤只交付 engine-neutral Java Case contract，不宣称 transport/server/runtime 完成。
- [Spec](../spec/2026-11-01-23-20-case-client-contract.md)：V9 Review，SHA-256 `ccb79ef2c083c40237af4df86ecff64a3d07ce0f8c0e71052d567b5c9217b62f`。
- [Plan](../plan/2026-11-01-23-20-case-client-contract.md)：V5 Review，SHA-256 `3080942de8ee8a9772527743478fbf3d94d8f4d63c2bb42bd29054447cb89f77`。
- Root 已 full-read 两文档、修正字段类型/规则分组/异常风格/隐私 GREEN 要求、执行 strict validators 和 hash 后 GO；没有把 Review 冒充用户另行 Accepted。

## 实现与边界

C module 新增 exact 11 public Java types：`CaseClient`；六个 records `StartCaseCommand`、`CaseInstanceQuery`、`ActOnPlanItemCommand`、`CasePlanItemDTO`、`CaseInstanceDTO`、`CaseOperationResult`；四个 plain enums `CasePlanItemActionEnum`、`CaseInstanceStatusEnum`、`CasePlanItemStateEnum`、`CasePlanItemKindEnum`。三个 client methods 精确为 `start/get/actOnPlanItem`；接口无 fields/constants/default methods。

所有 records 复用 `OrchestrationChecks` 和 JDK compact constructors。合同不接受 tenant、actor/user、role IDs、assignee、variables/inputs/form、raw/native engine command 或 native engine deployment/definition IDs。`StartCaseCommand`、`ActOnPlanItemCommand` 和 `CaseOperationResult` 的 `toString()` 抑制 command、business key 和 opaque versions，不依赖 exact 输出格式。

`CasePlanItemDTO` 防御复制 actions，最多4个且唯一。HUMAN_TASK：AVAILABLE/COMPLETED/TERMINATED 无 actions；ENABLED 只允许 `DISABLE/START` 子集；DISABLED 只允许 `ENABLE`；ACTIVE 只允许 `TERMINATE`。MILESTONE 始终无 actions。

`CaseInstanceDTO` 防御复制最多16个 plan items，plan-item instance ID 和 definition key 各自唯一。ACTIVE 要求 `finishedAt=null`；COMPLETED/TERMINATED 要求非空。Terminal Case 不 advertise actions，但允许空/sparse list 和 AVAILABLE/ACTIVE 等 nonterminal historical states；按既有 `ProcessInstanceDTO` 习惯不强制 `finishedAt >= submittedAt`。

`CaseOperationResult` 只接受四个 transition：DISABLE `ENABLED→DISABLED`、ENABLE `DISABLED→ENABLED`、START `ENABLED→ACTIVE`、TERMINATE `ACTIVE→TERMINATED`；previous/resulting 版本均为 opaque 64位 lowercase hex values 且必须不同；构造不证明其生产来源。测试使用独立 golden `VALID_TRANSITIONS` literal set，而不是镜像生产 switch。

`submittedAt`、nullable `finishedAt` 和 `occurredAt` 均为 `java.time.Instant`，语义为 UTC occurred instant；本步骤无默认时区、Clock 生成、序列化、wire 或 persistence conversion。

## 实际验证

所有 Maven 命令在 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 使用 JDK25、现有 source reactor、`/tmp/tianji-compat-fix/settings.xml`，关闭 ADK span content 与 OTel 三 exporter；未启动应用或外部服务。

Focused selector: `CaseContractTest`。Final selector: `CaseContractTest,OrchestrationContractTest,HumanTaskContractTest,ApprovalContractTest`。

| Gate | Actual result | Time / log |
| --- | --- | --- |
| RED：exact enums/interface + raw six records | 11 tests，8 failures，0 errors，0 skips；validation/copy/privacy assertions 因缺少行为失败；shape、valid values、valid transitions 三项通过；exit1 | 12.068s，2026-11-01 23:30:38；`/tmp/tianji-case-client-contract-red.log` |
| GREEN focused | 11 tests，0 failures/errors/skips；exit0 | 23.949s，2026-11-01 23:31:57；`/tmp/tianji-case-client-contract-green.log` |
| Final four classes | 24 tests，0 failures/errors/skips；BUILD SUCCESS | 15.460s，2026-11-01 23:32:27；`/tmp/tianji-case-client-contract-final.log` |
| Strict documents | final Spec/Plan validators PASS | repository docs / hashes above |
| Static/scope | 15 paths all CREATE；all previously tracked files unchanged，`git diff` empty；baseline snapshot 35 paths逐 SHA unchanged，包含23 SQL、全部 POM、SafeModelXml、formal-case 与既有 release paths | Root static audit |

Final breakdown：Case 11、Orchestration 6、HumanTask 4、Approval 3；无 skips。日志中的既有 deprecated/unchecked compiler warnings 未影响 exit0；没有新增依赖。

Focused evidence includes：

- reflection locks exact three methods、record component order、enum order and absence of authority/engine fields；
- ID/release/command/hash primitive boundaries use existing exception style；null enums/lists use JDK null failure where current style does；
- action lists and Case item lists are copied/unmodifiable；duplicate/oversize/null/invalid items reject；
- complete 4×6×6 result space rejects 140 non-golden action/state tuples；四个 golden transitions accept；
- terminal sparse/history projections and finishedAt-before-submittedAt are accepted exactly as designed，terminal advertised actions reject；
- privacy assertions cover StartCaseCommand、ActOnPlanItemCommand and CaseOperationResult without pinning an exact `toString()` format；
- no intermediate `IntItems`/fixture wrapper remains in final test source；latest `CaseContractTest` has 11 methods and 369 lines。

## Blocking Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | exact C orchestration package/15 CREATE paths | current pure contract style preserved | None |
| MC-REUSE-001 | Applicable | PASS | OrchestrationChecks/JDK records/Instant | no duplicate capability | None |
| MC-DEP-001 | Applicable | PASS | all POM SHA unchanged | zero dependency | None |
| MC-NAME-001 | Applicable | PASS | exact interface/Command/Query/DTO/Result/Enum inventory | semantic public names | None |
| MC-VALID-001 | Not applicable | N/A | no Spring/layer Bean Validation boundary | JDK compact constructors match existing C style | None |
| MC-MODEL-001 | Applicable | PASS | six records/four enums + copy tests | immutable technical contracts | None |
| MC-CONVERT-001 | Not applicable | N/A | no PO/BO/DTO mapping | no MapStruct/BaseConverter boundary | None |
| MC-LOG-001 | Not applicable | N/A | no business class/log | logging absent | None |
| MC-BEAN-001 | Not applicable | N/A | no Spring Bean/DI | injection absent | None |
| MC-UTIL-001 | Applicable | PASS | JDK/existing checks only | utility allowlist satisfied | None |
| MC-JSON-001 | Not applicable | N/A | no JSON codec/annotations | wire deferred | None |
| MC-TIME-001 | Applicable | PASS | Instant fields + status/time/no-chronology tests | required UTC semantics verified at Java boundary | None |
| MC-CONFIG-001 | Not applicable | N/A | no configuration path/key | profile parity unchanged | None |
| MC-PATTERN-001 | Not applicable | N/A | Simple finite contracts | no complex business flow | None |
| MC-SCOPE-001 | Applicable | PASS | exact15 all CREATE；tracked diff empty；35-path SHA snapshot | no existing/POM/HTTP API/server/DB edit | None |
| MC-TEST-001 | Applicable | PASS | actual RED11/GREEN11/final24 + strict/static | fields/matrices/copies/privacy/time/core regressions passed | None |
| MC-BLOCKER-001 | Applicable | PASS | all current Step MC closed | no remaining Step35 blocker；T3-08 continues | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | exact public type inventory | semantic contract suffixes | None |
| Rule 2 | Not applicable | N/A | no Spring/layer boundary | pure JDK constructor validation | None |
| Rule 3 | Applicable | PASS | immutable records/List.copyOf/tests | no mapper or mutable carrier | None |
| Rule 4 | Not applicable | N/A | no business Bean/DI/log | absent | None |
| Rule 5 | Applicable | PASS | JDK/OrchestrationChecks only | allowlist satisfied | None |
| Rule 6 | Not applicable | N/A | no JSON codec/annotation | plain enums only，wire deferred | None |
| Rule 7 | Not applicable | N/A | no config | unchanged | None |
| Rule 9 | Not applicable | N/A | Simple finite contract invariants | no pattern required | None |
| Rule 10 | Applicable | PASS | `Instant` only；no `java.util` time | UTC time contract satisfied | None |
| Rule 11 | Applicable | PASS | existing C module/package | no new layer/ORM/CRUD/DDL | None |

## 提交与未验证边界

精确15路径：11个 main Java types、`CaseContractTest.java`、本 Spec、Plan、Evidence，全部 CREATE；没有 existing tracked source edit。计划单一提交 `feat(contract): define fixed CMMN case operations`；引入本 Evidence 的 commit 由提交后命令 `git log -1 --format=%H -- docs/egon/evidence/2026-11-01-case-client-contract.md` 唯一取得，不在提交前自写 hash。

未实现或验证 HTTP/JSON codec、client implementation、server/controller、API route、OAuth/permission/IAM、CMMN release enum/admission、DB/migration、native engine lifecycle、Case task authorization、BPMN completion correlation、network listener、browser 或部署。Java contract 的 advertised actions 不是授权；后续 server 必须按 trusted tenant/actor、fixed release policy、current native state 和 expected version 再验证。
