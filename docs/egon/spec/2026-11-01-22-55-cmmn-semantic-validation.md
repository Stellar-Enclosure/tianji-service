# CMMN 正式案件语义验证边界

| Field | Value |
| --- | --- |
| Document | `2026-11-01-22-55-cmmn-semantic-validation.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Feature` |
| Complexity | `Simple` |
| Complexity Drivers | `None — 单一 package-private 固定语法验证器与不可变技术元数据；无发布、运行或持久化` |
| Created | `2026-11-01 22:55 Asia/Shanghai` |
| Updated | `2026-11-01 22:55 Asia/Shanghai` |
| Owner | `Root source-grounded design review；原用户继续范围授权，完成后审查` |
| Repository | `tianji-service` |
| Scope | `stellar-enclosure-tianji-process-server release package；Step 34 CMMN semantic prerequisite` |
| Change Surface | `新增 package-private CmmnModelValidator 与 immutable ValidatedCaseModel；只放宽既有 humanTask helper 的 package visibility；新增 focused test/fixture` |
| Affected Chapters | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Step 34：固定正式案件语法、治理元数据与拒绝边界` |
| Baseline Revision | `tianji-service main@7309dfda1e536a62e5eae3770efff6816e040093；检查时 clean` |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [CMMN Schema Boundary](2026-11-01-22-35-cmmn-schema-boundary.md) `§4 REQ-001–006, §7, §9, §14–16`；[Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§2.2, §10.2–10.3, §14.4, §17.3, T3-08`，SHA-256 `024dbee8a7e2b54bccc95f1878d54cade06f2a4f788e70e71efb9beb454d3b08` |
| Related Specs | `None` |
| Related Plans | [CMMN semantic validation Plan](../plan/2026-11-01-22-55-cmmn-semantic-validation.md) |

## 1. Summary

Step 33 只证明 CMMN XML 通过安全 XSD 边界；固定 XSD 允许缺少 `definitionRef`，也允许大量本平台未批准的 CMMN grammar。Step 34 在安全 DOM 之后增加独立、封闭、package-private 的 `CmmnModelValidator.validate(byte[], expectedKey)`，只接受一个固定正式案件拓扑和治理过的 ACTION HumanTask，并返回不可变 `ValidatedCaseModel`。

本步骤不使用 native `CmmnXmlConverter`，不在 `ProcessModelValidator` 增加 facade forward method，也不承认、发布、部署或运行 CMMN。`ValidatedCaseModel` 是技术验证结果；任意代码直接构造它不产生认证、授权、发布许可或运行权限。

## 2. Background and Current State

### 2.1 Business and user context

正式 Case 发布后续需要先从“schema-valid XML”收窄到平台允许的第一版案件模板：固定 primary/supplemental/milestone 拓扑、固定 sentry、受治理任务 lane/role 以及有限 operator action metadata。此步骤服务后续 release validator 实现者和审查者，不面向业务客户端。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact path/symbol/decision/command | Observed fact | Design significance | Verification limit/freshness |
| --- | --- | --- | --- | --- | --- |
| `EVD-101` | Static repository | `SafeModelXml.parseCmmn` | 已有 512 KiB、UTF-8、XXE、XSD 与结构边界 | 新 validator 必须先调用它 | 不提供语义发布许可 |
| `EVD-102` | Runtime/module evidence | Step33 Evidence | XSD 接受缺少 `definitionRef`；final 35 tests passed | strict validator 必须自行要求 exact refs | 不证明 native lifecycle |
| `EVD-103` | Static repository | `ProcessModelValidator.humanTask(Element)` | 已验证 kind/lane/positive Long roles/16 limit/form metadata，并生成 immutable HumanTask | strict CMMN gate 后复用，避免第二套治理 parser | 当前为 private，仅 visibility 需改 |
| `EVD-104` | Static repository | `ValidatedProcessModel.HumanTask` | nested record 深拷贝 lanes/fields | 可作为 CMMN technical metadata | 构造记录不等于权限 |
| `EVD-105` | Static repository | `formal-case.cmmn` | 固定拓扑但无 task namespace/governance | Step34 必须明确拒绝它 | read-only fixture |
| `EVD-106` | User/Root decision | Step34 scope | 精确八路径、no converter/facade/release/API/DB/POM | 固定停止边界 | Review，不伪造 Accepted |

### 2.3 Problem statement and gap

安全 XSD 只能证明 CMMN 1.1 结构合法，不能证明 required refs、固定拓扑、无可执行 grammar、任务治理或 operator action/role metadata。直接使用 native converter 会引入更宽语义与第二份解释，且不是本步骤授权边界。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current call chain | Data read/written | External dependency | Consumers | Evidence |
| --- | --- | --- | --- | --- | --- |
| safe CMMN bytes | caller → `SafeModelXml.parseCmmn` → DOM | caller-owned bytes / DOM；新 validator 自行先 clone | packaged XSD | Step34 validator | `EVD-101/102` |
| governed BPMN human task | `ProcessModelValidator.validateBpmn` → private `humanTask` | `HumanTask` metadata | none | BPMN release validation | `EVD-103/104` |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 这是既有 technical Process Server release package 的内部验证器/技术值/测试夹具，不是生成业务项目或 archetype 示例替换。

## 3. Goals and Non-goals

### 3.1 Goals

- 在 `SafeModelXml.parseCmmn` 后执行独立 closed DOM walk，接受且仅接受固定 v1 案件 grammar。
- 复用既有 HumanTask 治理 parser，先由 strict DOM gate 确认元素/命名空间/拓扑，再调用共享 parser 解析值，最后执行 ACTION/sole-lane/no-form 后置约束。
- 返回 defensive、deeply immutable 的 task/plan-item/action/role metadata。
- 用 governed fixture 和 mutation matrices 证明缺失 refs、宽 grammar、未治理任务及旧 fixture 均拒绝。

### 3.2 Non-goals

- 不使用 native CMMN converter；不新增 facade/forward method。
- 不改 public enum/type/contract/client/API、release admission/service、deploy/runtime、HumanTask service、Worker、permissions、DB/POM/config/SQL。
- 不支持 import、EL/condition、DI、callable、Flowable attributes、Forms、variables、approval、planning/discretionary/repetition 或任意模型。
- 不把 fixture role IDs 变成生产角色政策；发布输入以后仍须显式提供/审核。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| CMMN semantic validator | Affected | release package has none | 新 closed DOM walk | full internal contract/grammar/failure design | `§7, §8, §9, §13, §14, §15, §16, §17, §18` |
| Validated case metadata | Affected | no current case value | new immutable technical value | full model/immutability design | `§7, §8, §10, §13, §14, §15, §16, §17, §18` |
| BPMN HumanTask helper | Affected | `ProcessModelValidator.humanTask` private | package-static visibility only；body byte-identical | internal reuse/compatibility proof | `§7, §8, §9, §14, §16` |
| Focused test + governed fixture | Affected | current formal fixture ungoverned | new mutation/metadata proof | exact test/fixture design | `§8, §14, §15, §16, §18` |
| Step33 parser/formal fixture | Context-only | committed at baseline | parser reused；formal fixture remains read-only and rejected | regression boundary | `§14, §16` |
| Release/API/DB/config/permissions/runtime | Unchanged | current paths outside exact eight | zero behavior/path changes | scope/static gates | `§16` |
| Frontend | Not applicable | no frontend concern | none | N/A | `§12` |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| `REQ-101` | `CmmnModelValidator.validate(bytes,expectedKey)` 必须先安全解析，再独立 closed DOM walk；Case id 等于合法 expectedKey | Must | governed fixture returns model；null/invalid/mismatch key generic reject | Root Step34 |
| `REQ-102` | 只接受 exact 3 plan items、3 sentries、2 HumanTasks、1 milestone、全部首版 internal IDs/refs/criteria/rules/onParts 和 `casePlan`、autoComplete=false | Must | exact topology accepted；missing/extra/renamed/wrong ref/count/control/event rejected | Root Step34 |
| `REQ-103` | 所有 present IDs 合法且全局唯一，所有 attribute value ≤512，无 import/EL/condition/DI/callable/Flowable attr/其他 executable grammar | Must | mutation matrix rejects every forbidden construct with cause-free generic error | Root Step34 |
| `REQ-104` | 两 HumanTask 必须是 task namespace `ACTION + initialLane + exactly one taskLane`，sole lane、positive Long roles ≤16、无 fields/variables/approval | Must | fixture parses roles 7/8 and 8/9；invalid governance rejects | Root Step34 |
| `REQ-105` | 仅将 `ProcessModelValidator.humanTask(Element)` 从 private 改为 package-static，方法 body 和 BPMN behavior byte-identical | Must | visibility diff only；existing HumanTask/BPMN tests remain green | Root Step34 |
| `REQ-106` | `ValidatedCaseModel` 用 4-arg constructor 防御复制 XML/maps；nested Action/PlanItemPolicy immutable；operator roles derived union | Must | mutation attempts fail；returned XML clone；expected maps/actions/roles exact | Root Step34 |
| `REQ-107` | primary actions=`DISABLE,ENABLE,START`；supplemental=`START,TERMINATE`；milestone=empty；policy roles from task lanes | Must | exact plan item metadata and union `{7,8,9}` | Root Step34 |
| `REQ-108` | exact eight paths；old `formal-case.cmmn` read-only and rejected；no converter/facade/API/release/DB/POM/config changes | Must | path diff and final six-class selector；runtime boundary explicit | Root Step34 |

### 4.1 Scenario matrix

| Scenario | Actor/trigger | Preconditions | Main path | Alternative/failure path | Data/state change | Observable result | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| governed model | maintainer supplies exact fixture/key | safe parser available | copy → safe DOM → strict walk → human helper → metadata | any invariant mismatch rejects | none | immutable model | `REQ-101/102/104/106/107` |
| schema-valid but ungoverned | old formal fixture or mutations | XSD may accept | strict walk detects missing governance/ref/wider grammar | generic no-cause error | none | rejected | `REQ-102/103/104/108` |
| BPMN regression | existing release tests | helper visibility changed only | old caller uses identical body | any behavior drift blocks | none | prior tests green | `REQ-105/108` |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| `ACTOR-101` | Model-release maintainer | obtain bounded, deterministic governed-case metadata for later admission work | package-private Java | no external auth in this Step | `EVD-101/106` |
| `ACTOR-102` | Security/release reviewer | prove broad or ungoverned CMMN cannot cross semantic gate | focused JUnit | local module test | `EVD-102/105` |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `UC-101` | Validate governed fixed case | `ACTOR-101` | safe parser + existing task metadata parser | validate bytes/key | exact grammar | immutable metadata returned | invalid key/topology/governance rejected | no publish/runtime effect | `REQ-101/102/104/106/107` | `INTERNAL-101/102` | `TEST-101/102` |
| `UC-102` | Reject wider CMMN | `ACTOR-102` | mutation fixtures | validation review | schema-valid or unsafe candidate | no model returned | generic, no-cause failure | no state | `REQ-102/103/104/108` | `INTERNAL-101` | `TEST-103–106` |
| `UC-103` | Preserve BPMN HumanTask parsing | `ACTOR-102` | existing tests | regression selector | visibility-only change | identical results | drift blocks commit | no contract change | `REQ-105/108` | `INTERNAL-102` | `TEST-107` |

## 5. Constraints, Assumptions, and Decisions

### 5.1 Confirmed constraints

- New validator/model/test remain package-private in the existing release package.
- Native converter is forbidden in production and tests for Step34.
- Errors use exact existing generic message and null cause；no rejected XML detail leaks.
- Fixture role IDs 7/8/9 are test data only；metadata constructor does not grant authority.

### 5.2 Small-gap assumptions

| ID | Inference | Repository evidence | Why locally reversible | Impact if wrong |
| --- | --- | --- | --- | --- |
| `ASM-101` | technical `ValidatedCaseModel` uses final class plus nested enum/record without Lombok | explicit Root decision；nearby `ValidatedProcessModel` immutable style | package-private, no wire/ORM | representation-only rework |
| `ASM-102` | policy roles equal union of the sole parsed lane's role sets | fixed sole-lane contract | deterministic derived metadata | later authorization still revalidates release/IAM |

### 5.3 Resolved decisions

| ID | Decision | Decision owner | Evidence and rationale | Requirements |
| --- | --- | --- | --- | --- |
| `DEC-101` | independent DOM walk, no converter | Root Review design | XSD/converter allow wider grammar；one authority | `REQ-101–103` |
| `DEC-102` | reuse humanTask helper after strict gate only | Root Review design | preserves existing governance parser without letting it define CMMN grammar | `REQ-104/105` |
| `DEC-103` | fixed actions are maximum metadata, not current-state permission | Root Review design | later native lifecycle/IAM checks remain owners | `REQ-106/107` |

### 5.4 Open major decisions

None. 原用户已授权继续蓝图范围并在完成后审查；Root full-read/strict/hash 后决定 GO。文档仍为 Review，不冒充新的 Accepted decision。

## 6. Project Technology Context

| Concern | Current choice | Repository evidence | Constraint on design |
| --- | --- | --- | --- |
| XML | Step33 JDK DOM safe parser | `SafeModelXml` | semantic validator consumes DOM only |
| Metadata | existing immutable nested records/maps | `ValidatedProcessModel` | defensive copies；no JSON/ORM |
| Task governance | existing humanTask parser | `ProcessModelValidator` | visibility only；reuse after strict gate |
| Tests | JUnit Jupiter/source reactor | release tests | no engine/DB/app |

### 6.1 Java architecture profile and capability baseline

| Architecture profile | Archetype/template or base package | Exact evidence and verifier | Existing deviations | Design action |
| --- | --- | --- | --- | --- |
| Existing technical Process Server structure, explicitly preserved by current user/Root scope | `top.egon.cola.platform.tianji.process.server.release` | current module tree, Step33 committed parser, existing validators/models/tests | technical release utility is not business CRUD/ORM | Rule11 Applicable PASS：add only package-local validator/value/test; no new layer/module |

| Need | Spring/JDK candidate | Spring Boot Starter candidate | Egon-COLA/module candidate | Proven gap | Decision/dependency impact |
| --- | --- | --- | --- | --- | --- |
| safe DOM | Step33 `SafeModelXml` | none | module-local existing | none | reuse |
| task governance | existing static helper/body | none | module-local existing | private visibility | visibility-only reuse |
| immutable metadata | JDK final class/enum/record/collections | none | nearby immutable model | no case model | add package-private technical value；no dependency |

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | new validator/model/test behavior/value roles | explicit technical names；no ambiguous carrier | three Java paths | compile/type inventory | PASS |
| Rule 2 | No | no layer-to-layer DTO/Bean boundary | closed technical XML validation owns input | validator | negative tests | N/A |
| Rule 3 | Yes | immutable technical value, no ordinary business POJO/ORM | final class + defensive copy；nested immutable record | `ValidatedCaseModel` | immutability tests | PASS |
| Rule 4 | No | no business class/Spring Bean/DI | package-private plain Java | none | source diff | N/A |
| Rule 5 | Yes | only JDK collections/DOM/regex plus existing module types | no utility dependency | validator/model | import/POM diff | PASS |
| Rule 6 | No | no JSON/external enum | Action is internal only | none | scope diff | N/A |
| Rule 7 | No | no config path/key | no profile change | none | scope diff | N/A |
| Rule 9 | No | fixed technical grammar, no business variation | direct closed walk；no ceremonial pattern | validator | mutation tests | N/A |
| Rule 10 | No | no time symbol | no time work | none | import diff | N/A |
| Rule 11 | Yes | exact existing release package | preserve current structure；no layers/ORM/CRUD/DDL | all source/test paths | path/static gate | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: XML bytes、IDs、roles、actions and immutable maps contain no date/time field、calculation、storage、wire、zone、duration、precision or Clock。

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| `CmmnModelValidator` | New | `REQ-101–105` | extend ProcessModelValidator facade | violates no-facade and mixes grammars | one package-local validator | Add |
| `ValidatedCaseModel` | New | `REQ-106/107` | reuse BPMN model | wrong type/state semantics | one immutable technical value | Add |
| helper visibility | Expand | `REQ-104/105` | duplicate parser | governance drift | one modifier removal | Expand |
| governed fixture/test | New | `REQ-102–108` | mutate formal only | no positive governed case | test-only | Add |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| XSD-only baseline | 0 | 0 | DOM only | wider grammar accepted | insufficient for admission prerequisite |
| Selected design | 0 | 0 | package-local validated metadata | local generic validation failure | deterministic closed metadata |

### 7.1 System Architecture Design

No decorative full-system diagram for this Simple slice. `SafeModelXml` owns XML safety；`CmmnModelValidator` owns fixed CMMN grammar；existing `humanTask` owns governed task metadata；`ValidatedCaseModel` owns immutable projection。No component publishes or authorizes the model。

#### 7.1.1 Boundary and responsibility table

| Module/component | Capability and data owned | Inputs/outputs | Allowed dependencies | Forbidden responsibility | Requirements |
| --- | --- | --- | --- | --- | --- |
| `CmmnModelValidator` | fixed grammar validation | bytes/key → case model | DOM + existing task helper | converter/release/runtime/IAM | `REQ-101–105` |
| `ValidatedCaseModel` | immutable validation projection | key/xml/maps | JDK collections | authorization/public wire | `REQ-106/107` |
| `ProcessModelValidator.humanTask` | task governance parsing | strict task Element → HumanTask | existing helpers | CMMN topology | `REQ-104/105` |

### 7.2 High-Level Design

Validator clones input, validates expected key syntax, invokes `SafeModelXml.parseCmmn`, performs one recursive closed walk for namespaces/elements/attributes/text/ID uniqueness, then verifies the exact named topology. Only after the gate does it call the existing task parser for the two task definitions and derive immutable task/plan policies。

### 7.3 Detailed Design

#### 7.3.1 Closed grammar and topology

| Area | Required rule | Rejection |
| --- | --- | --- |
| root/case | one CMMN definitions → one case；case id=`expectedKey` valid identifier | imports/process/decision/DI/extra cases/mismatch |
| plan | one `casePlanModel`, `autoComplete="false"` | missing/true/expression/extra grammar |
| items | exact IDs `primary`,`supplemental`,`milestone` and required refs `primaryTask`,`supplementalTask`,`completionMilestone` | missing ref, duplicate/wrong/extra item |
| controls | primary/supplemental: empty required+manual only；milestone: empty required only | condition/repetition/extra/missing rule |
| criteria | primary 0；supplemental exactly 1→`primaryCompleted`；milestone exactly 2 OR→completed/terminated | exit criteria、wrong/duplicate/missing ref |
| sentries | exact three IDs；each exactly one planItemOnPart, no ifPart；fixed source/event | extra onPart、condition、wrong source/event |
| definitions | exact two governed HumanTask + one milestone | callable/case/process/decision/extra definition |
| attributes/text | value≤512、no `${`/`#{`、present IDs valid+unique、only allowlisted attrs/namespaces；structural comments allowed；event literal has exactly one TEXT node with exact `complete`/`terminate` | Flowable attr、EL、unknown namespace、CDATA fragment、event comment/split/extra text、other nonblank text |

Namespace closure applies to element/attribute expanded names actually used. Unused `xmlns` declarations remain inert and follow the existing parser/DOM-walk convention; StAX MAX_ATTRIBUTES counts ordinary attributes separately from declarations. The original 512KiB/depth/element bounds remain unchanged; this Step does not reinterpret the established global bound or edit SafeModelXml.

The first template pins every internal ID: plan `casePlan`; rules `primaryRequired`, `supplementalRequired`, `milestoneRequired`, `primaryManualActivation`, `supplementalManualActivation`; criteria `supplementalEntry`, `milestoneCompletedEntry`, `milestoneTerminatedEntry`; onParts `primaryCompletedPart`, `supplementalCompletedPart`, `supplementalTerminatedPart`.

#### 7.3.2 Governed HumanTask

Each task must have CMMN `id` and an optional bounded `name`, task namespace `kind="ACTION"` and `initialLane`, exactly one standard `extensionElements`, exactly one task namespace `taskLane`, and no `taskField`。The existing helper then enforces valid lane key, exact initial-lane membership, unique positive Long roles, maximum 16, no variables/approval and immutable metadata。

#### 7.3.3 Metadata mapping

| Map key | Definition/policy | Actions | roleIds |
| --- | --- | --- | --- |
| `humanTasks.primaryTask` | existing HumanTask ACTION | N/A | sole lane roles |
| `humanTasks.supplementalTask` | existing HumanTask ACTION | N/A | sole lane roles |
| `planItems.primary` | `primaryTask` | `DISABLE, ENABLE, START` | primary roles |
| `planItems.supplemental` | `supplementalTask` | `START, TERMINATE` | supplemental roles |
| `planItems.milestone` | `completionMilestone` | empty | empty |

`operatorRoleIds()` is the immutable union of all policy role IDs。No transaction、persistence、retry、shared mutable state、logging or external effect exists。

## 8. Package Structure and Code File Tree

### 8.1 Current relevant tree

```text
.../release/SafeModelXml.java
.../release/ProcessModelValidator.java
.../release/ValidatedProcessModel.java
.../release/CmmnModelXmlTest.java
.../fixtures/formal-case.cmmn
```

### 8.2 Target tree

```text
.../release/CmmnModelValidator.java          CREATE
.../release/ValidatedCaseModel.java          CREATE
.../release/ProcessModelValidator.java       MODIFY visibility only
.../release/CmmnModelValidatorTest.java      CREATE
.../fixtures/governed-case.cmmn              CREATE
```

### 8.3 Package and file responsibilities

| Operation | Path/package | Symbols | Responsibility | Dependencies | Requirements |
| --- | --- | --- | --- | --- | --- |
| Create | `.../release/CmmnModelValidator.java` | `validate` + private walk helpers | closed semantic gate | safe DOM/task helper | `REQ-101–105` |
| Create | `.../release/ValidatedCaseModel.java` | model/Action/PlanItemPolicy | immutable projection | JDK + HumanTask | `REQ-106/107` |
| Modify | `.../release/ProcessModelValidator.java` | `humanTask(Element)` | package reuse visibility | body unchanged | `REQ-105` |
| Create | `.../release/CmmnModelValidatorTest.java` | focused positive/negative tests | RED/GREEN/security proof | JUnit | `REQ-101–108` |
| Create | `.../fixtures/governed-case.cmmn` | fixed governed case | positive test data | CMMN/task namespace | `REQ-102/104/107` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Change/necessity verdict | Name/purpose | Kind | Consumer | Owner | Symbol | Input | Output | Error model | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `INTERNAL-101` | New/Add | validate governed fixed CMMN | package-private instance Java | same-package future release admission/tests | release package | `CmmnModelValidator.validate(byte[],String)` | bytes/key | `ValidatedCaseModel` | generic no-cause invalid | `REQ-101–104` |
| `INTERNAL-102` | Existing/Expand visibility | reuse task governance parser | package-static Java | BPMN validator + CMMN validator | `ProcessModelValidator` | `humanTask(Element)` | strict task Element | HumanTask metadata | existing generic invalid | `REQ-104/105` |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-101 — Validate governed CMMN

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New package-private operation |
| Independent consumer goal | later release work needs deterministic metadata, not merely XSD validity |
| Parameter ownership and derivation | caller supplies model bytes/expected released key；validator owns all structure/policy derivation |
| Direct/no-new-interface alternative | converter/XSD cannot enforce closed platform grammar |
| Caller use of result | later separately approved admission may consume immutable metadata；Step34 tests inspect it |
| Round trips and failure points | zero network；one local semantic failure boundary |
| Verdict | Add separate validator；no facade/public method |

##### Identity and purpose

`ValidatedCaseModel validate(byte[] source, String expectedKey)`；package-private, synchronous, deterministic, no tenant/auth context。

##### Request parameters

`source` nonnull/nonempty/≤SafeModelXml limit；`expectedKey` matches existing identifier pattern and must equal root Case id。

##### Success response

Returns immutable key/XML/task/policy metadata only after all closed grammar and governance checks。

##### Error responses

Caller-controlled invalid input/topology/governance/attribute/ref yields `IllegalArgumentException("Process model is invalid or not supported")` with no cause or model detail。An internal packaged-schema class-initialization failure from the already-effective safe parser retains its internal failure semantics and is not misreported as a client model error。

##### Interface logic for frontend and consumers

clone → safe parse → strict walk → fixed topology checks → existing task metadata parse → immutable projection。No frontend/retry/publish action。

##### Compatibility and verification

New package-private symbol；no old caller。Mutation tests and exact final selector prove behavior；no runtime claim。

#### 9.2.2 INTERNAL-102 — Parse governed HumanTask metadata

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Existing method visibility only |
| Independent consumer goal | CMMN validator reuses the one task-governance authority after strict gate |
| Parameter ownership and derivation | strict validator owns Element shape；helper owns task metadata parsing |
| Direct/no-new-interface alternative | duplicate parser would drift |
| Caller use of result | constructs HumanTask and plan policy metadata |
| Round trips and failure points | local call only；existing generic failures |
| Verdict | remove `private` only；no forwarding facade |

##### Identity and purpose

`static ValidatedProcessModel.HumanTask humanTask(Element task)` remains on `ProcessModelValidator` in the same package。

##### Request parameters

Element must already pass the CMMN strict structural gate；BPMN caller behavior remains unchanged。

##### Success response

Same immutable HumanTask record as baseline。

##### Error responses

Existing body and generic invalid behavior remain byte-identical。

##### Interface logic for frontend and consumers

CMMN caller invokes directly after strict validation；no facade、reflection or converter。

##### Compatibility and verification

Visibility expands only within package；source-body diff and existing BPMN tests protect compatibility。

## 10. POJO and Data Model Design

### 10.1 POJO role classification and class necessity

| Object/path | Selected role | Owner/boundary and consumers | Why distinct/reuse safe | Mapping owner | Requirements |
| --- | --- | --- | --- | --- | --- |
| `ValidatedCaseModel` | immutable technical validation value | release package only | CMMN policy differs from BPMN model | validator constructs directly | `REQ-106/107` |
| `Action` | internal enum | PlanItemPolicy only | closed operator metadata vocabulary | none | `REQ-107` |
| `PlanItemPolicy` | immutable nested value record | model planItems map | exact definition/actions/roles tuple | none | `REQ-106/107` |
| existing `HumanTask` | immutable nested value record | both validators | exact governance semantics already fit | existing helper | `REQ-104/105` |

### 10.2 Representation and lifecycle

`ValidatedCaseModel` is a package-private final class with a four-argument constructor `(String definitionKey, byte[] xml, Map<String,HumanTask> humanTasks, Map<String,PlanItemPolicy> planItems)`。Constructor clones bytes and copies maps；accessor `xml()` clones again；maps/sets are immutable。`PlanItemPolicy` compact constructor copies actions/roles。No Lombok、MapStruct、JSON、ORM or persistence annotation applies。

Direct construction is metadata construction only；Javadoc must state it does not authenticate、authorize、publish or approve a model。

## 11. Database Design

Unchanged. No table、schema、transaction、migration、SQL、index or persistence model changes；existing ProcessDB history remains byte-identical。

## 12. Frontend Page Design

N/A. No frontend route、contract、state or user interaction exists in this package-private prerequisite。

## 13. Design Patterns and Architecture Principles

No business design pattern。The validator is a fixed security grammar with no runtime variation point；direct cohesive walk/helpers are smaller than Strategy/Visitor/Factory types。Composition is implicit through direct reuse of the safe parser and task metadata helper；no inheritance/facade。

## 14. Test Design

### 14.1 Unit tests

| ID | Level | Target | Scenario/input | Expected assertion | Test data | Requirements |
| --- | --- | --- | --- | --- | --- | --- |
| `TEST-101` | Unit | validate/model | governed fixture/key | assertDoesNotThrow；exact maps/actions/roles/union/XML copy | governed-case | `REQ-101/102/104/106/107` |
| `TEST-102` | Unit | immutability | mutate source/returned collections/bytes | source mutation irrelevant；returned mutations fail/no effect | validator-produced model | `REQ-106` |
| `TEST-103` | Unit | strict refs/topology | missing definitionRef、renamed fixed internal IDs、wrong refs/counts/criteria/controls/sentries | generic reject | mutated governed fixture | `REQ-102/103` |
| `TEST-104` | Unit | forbidden grammar | import/EL/condition/DI/callables/Flowable attrs/unknown elements/long attr/duplicate IDs | generic no-cause reject | mutation matrix | `REQ-103` |
| `TEST-105` | Unit | task governance | wrong/missing kind/lane/namespace、extra lane/field、bad/>16 roles、variables/approval | generic reject | mutation matrix | `REQ-104/105` |
| `TEST-106` | Unit | permissive-XSD boundary/key | first assert safe parser accepts formal-case, then semantic validator rejects its missing governance；invalid/mismatch expectedKey rejects | boundary is observable without converter | read-only old fixture | `REQ-101/108` |
| `TEST-107` | Regression | existing BPMN parser | final six-class selector | existing tests unchanged | existing fixtures | `REQ-105/108` |

RED compile prerequisites are the final immutable metadata type, helper visibility-only change, and a `CmmnModelValidator.validate` reject-all stub。The reviewed candidate has three acceptance methods and a named parameterized rejection matrix; exact counts are recorded only after execution。Three positive acceptance methods use `assertDoesNotThrow` so reject-all is observable RED；actual counts/failures belong only in Evidence。

### 14.2 Integration, contract, persistence, component, and end-to-end tests

N/A. No Spring context、engine、DB、network、service、container or browser runs。

## 15. Non-functional and Cross-cutting Design

- Security: safe XML first；closed namespaces/elements/attributes/text/refs；generic no-cause error；no converter fallback。
- Capacity: retain 512 KiB/XML structural limits；additional attribute-value bound 512；roles max16。
- Integrity: defensive copies and immutable collections；all present IDs globally unique。
- Authorization: metadata/actions/roles are descriptive maxima only；no credential、IAM or release authority。
- Observability: no model content logging or new telemetry。

## 16. Compatibility, Migration, Rollout, and Rollback

No public compatibility、migration、config、permission or deployment change。Only helper visibility expands within package, with body unchanged。New classes have no production consumer yet。Rollback is one eight-path source/docs revert；no data rollback。

## 17. Alternatives and Decisions

| Option | New elements and interactions | Advantages | Disadvantages/risks | Repository fit | Decision and rationale |
| --- | --- | --- | --- | --- | --- |
| A — independent closed DOM validator | two package-private types + helper reuse | one strict authority；no converter | explicit grammar code | exact fit | Selected |
| B — native converter model inspection | converter dependency path | convenient object graph | accepts wider grammar/second interpretation | forbidden | Rejected |
| C — add method to ProcessModelValidator | facade method | one entry object | couples BPMN/CMMN and violates scope | poor | Rejected |
| D — duplicate task governance parser | no visibility change | isolated text | drift/security duplication | poor | Rejected |

## 18. Risks and Open Questions

| ID | Risk/question | Probability | Impact | Mitigation or decision owner | Status |
| --- | --- | --- | --- | --- | --- |
| `RISK-101` | XSD-valid missing ref crosses gate | Medium | unsafe policy metadata | require exact refs；negative test | Closed by design |
| `RISK-102` | helper visibility change alters BPMN body | Low | regression | modifier-only diff + six-class selector | Closed by design |
| `RISK-103` | metadata mistaken for permission/release | Medium | authorization bypass | package-private/Javadoc/no consumer/no API | Closed by scope |
| `RISK-104` | fixed grammar overclaimed as full T3-08 | Medium | false readiness | Evidence explicitly states no runtime/admission | Closed by scope |

## 19. Traceability Matrix

| Requirement | Use case | Affected area/chapter | Context-only or unchanged boundary | Interface/model/database/frontend | Tests | Acceptance evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `REQ-001` | `UC-103` | Step33 prerequisite `§2/§16` | safe CMMN parser unchanged | existing `SafeModelXml.parseCmmn` | final selector | prior valid parse preserved |
| `REQ-002` | `UC-102/103` | Step33 security prerequisite `§15/§16` | UTF-8/XXE/bounds unchanged | existing parser | `CmmnModelXmlTest` | prior rejection boundary preserved |
| `REQ-003` | `UC-103` | Step33 compatibility `§16` | BPMN/DMN parser unchanged | existing parser | final selector | old behavior green |
| `REQ-004` | `UC-102` | Step33 formal fixture context `§14/§16` | fixture read-only | no new model claim | `TEST-106` | XSD accepts, semantic gate rejects |
| `REQ-005` | `UC-103` | Step33 test prerequisite `§14` | prior tests unchanged | none | final selector | prior suite included |
| `REQ-006` | `UC-103` | Step33 no-expansion boundary `§16` | parser/API/DB unchanged | none | scope diff | prerequisite exclusions preserved |
| `REQ-101` | `UC-101/102` | validator `§7–9` | safe parser preserved | `INTERNAL-101` | `TEST-101/106` | key/safe parse |
| `REQ-102` | `UC-101/102` | grammar `§7/14` | runtime unchanged | validator | `TEST-101/103` | fixed topology |
| `REQ-103` | `UC-102` | security `§7/15` | no converter | validator | `TEST-103/104` | forbidden grammar rejects |
| `REQ-104` | `UC-101/102` | task governance `§7/9` | IAM unchanged | `INTERNAL-102` | `TEST-101/105` | sole lane/roles |
| `REQ-105` | `UC-103` | helper `§8/9/16` | BPMN logic preserved | `INTERNAL-102` | `TEST-107` | visibility-only diff |
| `REQ-106` | `UC-101` | model `§10` | no public DTO | `ValidatedCaseModel` | `TEST-101/102` | defensive immutable |
| `REQ-107` | `UC-101` | policy `§7/10` | runtime permission unchanged | Action/Policy | `TEST-101` | exact actions/union |
| `REQ-108` | `UC-102/103` | scope `§8/16` | release/API/DB unchanged | none | `TEST-106/107` + diff | exact eight paths |

## 20. Review and Acceptance

### 20.1 Original-request fidelity

All explicit topology、governance、metadata、reuse、negative and scope requirements map to `REQ-101–108`；no broader T3-08 capability is implied。

### 20.2 Repository and technical fidelity

Baseline parser、helper body、nested HumanTask model、Step33 fixture/evidence and package/test conventions were inspected。No unexecuted runtime result is claimed。

### 20.3 Cross-section consistency

Eight paths、two internal contracts、four-arg model、fixed grammar、actions/roles、test matrices and no-admission boundary agree across all chapters。

### 20.4 Relationship and effective-design review

Step33 safe-parser requirements remain effective prerequisites and are regression-protected；this document adds only semantic validation and does not rewrite Step33。

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | `§6.1`, exact release tree | existing technical placement preserved | None |
| `MC-REUSE-001` | Applicable | PASS | capability ledger/helper evidence | safe parser/task metadata reused | None |
| `MC-DEP-001` | Applicable | PASS | POM excluded；JDK/module types only | no dependency added | None |
| `MC-NAME-001` | Applicable | PASS | validator/model/test/type inventory | explicit technical roles；no ambiguous carrier | None |
| `MC-VALID-001` | Not applicable | N/A | no layer/DTO Bean Validation handoff | technical XML gate owns validation | None |
| `MC-MODEL-001` | Applicable | PASS | §10 + explicit Root decision | immutable technical final class/record；no business POJO/ORM | None |
| `MC-CONVERT-001` | Not applicable | N/A | no mapping/converter；native converter forbidden | no BaseConverter concern | None |
| `MC-LOG-001` | Not applicable | N/A | no business class/log | no sensitive logging | None |
| `MC-BEAN-001` | Not applicable | N/A | no Spring Bean/DI | injection absent | None |
| `MC-UTIL-001` | Applicable | PASS | JDK/module-local helpers only | closed utility policy | None |
| `MC-JSON-001` | Not applicable | N/A | no JSON/external enum | wire absent | None |
| `MC-TIME-001` | Not applicable | N/A | §6.3 | time absent | None |
| `MC-CONFIG-001` | Not applicable | N/A | no config path/key | profile parity unchanged | None |
| `MC-PATTERN-001` | Not applicable | N/A | Simple fixed technical grammar | no complex business flow | None |
| `MC-SCOPE-001` | Applicable | PASS | §3.3/§8 exact eight | no wider path/surface | None |
| `MC-TEST-001` | Applicable | PASS | `TEST-101–107` design | RED/GREEN/security/regression gates defined | Execute after GO |
| `MC-BLOCKER-001` | Applicable | PASS | §5.4/§18/all rows | no open design blocker | None |

### 20.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

Immutable technical model is not business DTO/PO/ORM；Root 在用户要求继续任务并保持既有风格的授权下选择邻近 immutable 技术模型的 final class/nested record；不是用户另行批准具体表示法。No persistence、validation layer、business uniqueness/DDL、CQE/Event/outbox work。Rule11 preserves the current technical release package and adds no layer/module。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/catalog、generator、manifest、SQL journal or dependency introduction。Any POM/SQL/config/generated path change violates scope。
