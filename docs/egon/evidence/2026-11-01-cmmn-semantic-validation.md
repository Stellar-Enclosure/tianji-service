# Step 34 — CMMN 正式案件语义验证证据

- Baseline: `main@7309dfda1e536a62e5eae3770efff6816e040093`，实施前 clean。
- Source authority: 原用户授权继续蓝图未完成范围、分步提交并在完成后审查；本步骤只交付 T3-08 的语义验证前置，不宣称完整 CMMN/Case 能力。
- [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md)：V9 Review，SHA-256 `2718ef9663c54b6b0d0843aaaf50f6bf1089feebd080da0f28d7444b232207e8`。
- [Plan](../plan/2026-11-01-22-55-cmmn-semantic-validation.md)：V5 Review，SHA-256 `0293ba5df15140d83b40ae157caa477419d0df51f26fc1e4a87501008cb36a9d`。
- Root 已 full-read 两文档、执行 strict validators、核对当前源码与 Step33 边界后 GO；没有把派生 Review 冒充用户另行 Accepted。

## 实现与安全边界

`CmmnModelValidator` 是 245 行 package-private closed DOM validator。它先调用 `SafeModelXml.parseCmmn`，再独立验证固定首版 grammar；生产和测试均不使用 native CMMN converter，也没有在 `ProcessModelValidator` 增加 facade/forward method。

验证器固定 `casePlan`、primary/supplemental/milestone 三 plan item、两 governed ACTION HumanTask、一 milestone、三 sentry 及其 rule/criterion/onPart IDs。每个 sentry 只有一个 onPart、没有 ifPart；事件必须由唯一 TEXT node 精确承载 `complete` 或 `terminate`。缺少 XSD-optional `definitionRef`、AND sentry、EL/condition、import、DI、callable、planning/repetition、Forms、Flowable attributes、CDATA/event fragment、重复 ID 和非白名单 grammar 均拒绝。

两 HumanTask 只允许 task namespace 的 `kind=ACTION`、`initialLane` 和单一 `taskLane`；复用既有 `ProcessModelValidator.humanTask(Element)` 解析 lane/role。两个调用均位于全部 topology checks 之后。该既有方法只删除 `private` modifier，normalized body 与 baseline 逐字节相同。

`ValidatedCaseModel` 是 43 行 package-private immutable technical value：4 参数 constructor 防御复制 XML/maps，nested `PlanItemPolicy` 复制 action/role sets，operator roles 从 policies 派生。Javadoc 明确任意构造既不认证调用者，也不授权发布或执行。primary actions 为 `DISABLE/ENABLE/START`，supplemental 为 `START/TERMINATE`，milestone 为空；test fixture roles 7/8 与 8/9 只属测试数据。

Namespace closure 校验实际使用的 element/attribute expanded names。未使用的 `xmlns` declaration 是惰性声明，不授予 grammar、权限或执行能力；保留 Step33 StAX 对 ordinary attributes 的现有计数语义。Root 拒绝全局重解释 namespace declarations 的提议；`SafeModelXml` body 与原 byte/depth/element/attribute bounds 不变，512 KiB 继续限制整体输入。

客户端可控的无效模型统一返回 exact generic `IllegalArgumentException`、null cause、无模型细节；打包 schema 的内部 class-initialization fault 保留内部失败语义，不伪装为客户端输入错误。

## 实际验证

所有 Maven 命令在 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 使用 JDK25、现有 source reactor、`/tmp/tianji-compat-fix/settings.xml`，并关闭 ADK span content 与 OTel 三 exporter；未启动应用或外部服务。

Focused selector: `CmmnModelValidatorTest`。Final selector: `CmmnModelValidatorTest,CmmnModelXmlTest,ProcessModelValidatorTest,HumanTaskModelTest,HumanTaskTimerModelTest,ProcessReleaseServiceTest`。

| Gate | Actual result | Time / log |
| --- | --- | --- |
| RED：compile-ready reject-all validator | 67 tests，3 failures，0 errors，0 skips；三个 positive acceptance 因缺少行为失败；64 rejection executions 通过，包含 12 个 SafeModelXml-permissive preconditions 和 bare formal fixture boundary；exit1 | 19.808s，2026-11-01 23:12:01；`/tmp/tianji-cmmn-semantic-validation-red.log` |
| GREEN focused | 67 tests，0 failures/errors/skips；exit0 | 17.561s，2026-11-01 23:12:55；`/tmp/tianji-cmmn-semantic-validation-green.log` |
| Final six classes | 102 tests，0 failures/errors/skips；BUILD SUCCESS | 17.352s，2026-11-01 23:13:23；`/tmp/tianji-cmmn-semantic-validation-final.log` |
| Strict documents | Spec 与 Plan strict validators 均 PASS | final repository documents / hashes above |
| Static/body/scope | `humanTask(Element)` normalized body unchanged；baseline snapshot 35 excluded paths逐 SHA unchanged，包含23 SQL、全部 POM、SafeModelXml、formal-case fixture 与既有 release paths；exact eight-path scope | Root static audit |

Final classes individually ran 67、7、10、5、5、8 tests。日志中的既有 Jackson deprecated/unchecked test source 与 JDK Mockito/ByteBuddy dynamic-agent warnings 未影响 exit0；没有新增依赖。

Focused coverage also proves：

- governed case projects only immutable task/policy metadata，source/returned bytes and maps/sets cannot mutate stored state；operator role union is `{7,8,9}`；
- alternate valid case key/lane and 16 positive Long roles are accepted；maximum `9223372036854775807` is valid；
- `formal-case.cmmn` first passes Step33 safe XSD parsing, then is rejected by semantic validation because it lacks task governance；
- 62 parameterized mutation cases plus ordinary boundary/positive cases cover exact fixed IDs/refs/rules/criteria/onParts、topology、sentry OR 结构、task governance、role zero/duplicate/overflow/whitespace/>16、attribute length and prohibited executable grammar；
- each semantic rejection checks exact generic message、null cause and no private-detail leak。

## Blocking Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | exact release package/八路径 | technical validator/model 保持现有模块；无新层/facade | None |
| MC-REUSE-001 | Applicable | PASS | SafeModelXml + existing humanTask helper | safe parse/task governance均复用 | None |
| MC-DEP-001 | Applicable | PASS | 全 POM SHA unchanged；JDK/module types only | 零新依赖 | None |
| MC-NAME-001 | Applicable | PASS | CmmnModelValidator/ValidatedCaseModel/PlanItemPolicy/Test | 技术角色清晰；无模糊 carrier | None |
| MC-VALID-001 | Not applicable | N/A | 无层间 DTO/Bean Validation handoff | closed XML technical boundary owns validation | None |
| MC-MODEL-001 | Applicable | PASS | 43-line final immutable technical value + tests | defensive class/nested record 符合显式范围 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无项目 mapper/native converter | 无 MapStruct/BaseConverter boundary | None |
| MC-LOG-001 | Not applicable | N/A | 无业务类/log | 无模型内容日志 | None |
| MC-BEAN-001 | Not applicable | N/A | 无 Spring Bean/DI | Bean/injection concern absent | None |
| MC-UTIL-001 | Applicable | PASS | JDK DOM/collections/regex + existing helpers | 无 duplicate utility/dependency | None |
| MC-JSON-001 | Not applicable | N/A | 无 JSON/wire enum | Action internal only | None |
| MC-TIME-001 | Not applicable | N/A | 无时间字段/运算/import | time boundary absent | None |
| MC-CONFIG-001 | Not applicable | N/A | 无 config path/key | profile parity unchanged | None |
| MC-PATTERN-001 | Not applicable | N/A | Simple fixed technical grammar | direct cohesive walk；无复杂业务 variation | None |
| MC-SCOPE-001 | Applicable | PASS | exact八路径 + 35-path SHA snapshot | 无 parser/API/DB/POM/formal fixture scope expansion | None |
| MC-TEST-001 | Applicable | PASS | actual RED67/GREEN67/final102 + strict/static | semantic/security/immutability/old regression均实测 | None |
| MC-BLOCKER-001 | Applicable | PASS | 全部当前步骤 MC closed | Step34 无剩余 blocker；完整T3-08继续 | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | validator/model/policy/test names | 技术角色明确 | None |
| Rule 2 | Not applicable | N/A | 无层间 DTO boundary | XML technical validation only | None |
| Rule 3 | Applicable | PASS | final class/nested immutable record + defensive tests | 非 business POJO/ORM；无需 Lombok/MapStruct | None |
| Rule 4 | Not applicable | N/A | 无 business/Spring Bean/DI | annotation/injection absent | None |
| Rule 5 | Applicable | PASS | JDK/module-local APIs only | utility allowlist satisfied | None |
| Rule 6 | Not applicable | N/A | 无 JSON/external enum | wire unchanged | None |
| Rule 7 | Not applicable | N/A | 无 configuration | profile unchanged | None |
| Rule 9 | Not applicable | N/A | Simple fixed technical grammar | 无 complex business logic/pattern requirement | None |
| Rule 10 | Not applicable | N/A | 无 time symbol | time unchanged | None |
| Rule 11 | Applicable | PASS | existing technical Process Server release package | 无新层/ORM/CRUD/DDL | None |

## 提交与未验证边界

精确八路径：`CmmnModelValidator.java`、`ValidatedCaseModel.java`、`ProcessModelValidator.java` visibility-only change、`CmmnModelValidatorTest.java`、`governed-case.cmmn`、本 Spec、Plan、Evidence。计划单一提交 `feat(process): validate governed CMMN model semantics`；引入本 Evidence 的 commit 由提交后命令 `git log -1 --format=%H -- docs/egon/evidence/2026-11-01-cmmn-semantic-validation.md` 唯一取得，不在提交前自写 hash。

未启动应用、CMMN/BPMN engine、DB、容器、browser、HTTP listener 或 Case 网络协议；未执行 public enum/contract/API、model admission、release publish/deploy、native lifecycle、HumanTask/planItem runtime action、IAM role registration/activation/grant、BPMN continuation、Inbox/transaction 或 PostgreSQL。`ValidatedCaseModel` 不是权限或发布凭据。后续仍需正式 Case contract/admission/execution、CASE HumanTask 权限、completion correlation 和最多一个后续 ProcessDB migration；本步骤既有23 SQL逐字不变。
