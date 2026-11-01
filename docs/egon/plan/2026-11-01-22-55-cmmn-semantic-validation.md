# CMMN 正式案件语义验证实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-01-22-55-cmmn-semantic-validation.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 22:55 Asia/Shanghai` |
| Updated | `2026-11-01 22:55 Asia/Shanghai` |
| Owner | `Root source-grounded design review；原用户继续范围授权，完成后审查` |
| Repository | `tianji-service` |
| Scope | `Step 34；process-server release package；精确八路径` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Step 34 semantic prerequisite` |
| Baseline Revision | `main@7309dfda1e536a62e5eae3770efff6816e040093；检查时 clean` |
| Implements Spec | [CMMN 正式案件语义验证边界](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-01；SHA-256 2718ef9663c54b6b0d0843aaaf50f6bf1089feebd080da0f28d7444b232207e8` |
| Effective Specs | [CMMN semantic validation](../spec/2026-11-01-22-55-cmmn-semantic-validation.md), [CMMN schema boundary](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) |
| Depends On Plans | [CMMN schema boundary Plan](2026-11-01-22-35-cmmn-schema-boundary.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本 Plan 以一个 Step 实施 Review Spec，并保护 Step33 `REQ-001–006`。先建立 final immutable metadata constructor、reject-all validator stub 和 helper visibility compile prerequisites；再加入 governed fixture 与 focused test，观察三个正向 acceptance methods 的 `assertDoesNotThrow` RED；随后只完成 closed DOM validator；最后运行 focused/final six-class selectors、strict docs 和 exact-eight-path gates，写实际 Evidence 后创建一个语义提交。

用户继续蓝图的授权覆盖本必要实现；Root full-read/strict/hash GO 后实施，完成后由用户审查。动态 test counts、failure counts、timing and hashes only belong in execution Evidence。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [CMMN semantic validation](../spec/2026-11-01-22-55-cmmn-semantic-validation.md)
- Status: `Review`
- Revision: `Updated 2026-11-01 22:55 Asia/Shanghai`；baseline `7309dfda1e536a62e5eae3770efff6816e040093`
- Approval evidence: 原用户已授权继续蓝图未完成范围并在完成后审查；Root full-read/strict/hash 后 GO。没有新 Accepted claim。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [semantic validation](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) | Review / timestamp above | all | Step34 exact design |
| Dependency | [schema boundary](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) | Review / implemented at baseline | `REQ-001–006, §7, §9, §14–16` | safe parser and exclusions remain normative |

### 2.3 Superseded or excluded content

None。Step34 does not reopen Step33 parser/XSD/fixture。Native converter、public enum、release admission、API、DB and runtime remain excluded。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-001` | Step33 §4 | safe package-private CMMN parse | prior parser tests stay green | regression only |
| `REQ-002` | Step33 §4 | preserve UTF-8/XXE/bounds/generic error | final selector retains prior security tests | regression only |
| `REQ-003` | Step33 §4 | preserve old BPMN/DMN parse and lazy holder | prior tests green | no parser edit |
| `REQ-004` | Step33 §4 | formal fixture fixed structure | read-only old fixture still parses but semantic validator rejects governance gap | negative test |
| `REQ-005` | Step33 §4 | prior focused boundary tests | included in final selector | regression |
| `REQ-006` | Step33 §4 | no admission/API/DB expansion | Step34 excludes parser/public/persistence changes | static gate |
| `REQ-101` | Primary §4 | safe parse + expected key + closed walk | governed fixture accepted；bad key rejected | validator |
| `REQ-102` | Primary §4 | exact topology/refs/controls/criteria/sentries | mutation matrix | validator/test |
| `REQ-103` | Primary §4 | unique IDs、512 attr、forbidden grammar | generic rejects | validator/test |
| `REQ-104` | Primary §4 | governed ACTION sole-lane tasks/roles | exact HumanTask metadata | validator/helper/fixture |
| `REQ-105` | Primary §4 | humanTask visibility only | body diff + old tests | one modifier edit |
| `REQ-106` | Primary §4 | immutable 4-arg model | defensive maps/sets/bytes | model/test |
| `REQ-107` | Primary §4 | exact actions/roles/union | metadata assertions | model/validator/test |
| `REQ-108` | Primary §4 | exact eight paths/no broader surface | scope/final selector/Evidence | all files |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

File 1 supplies the final immutable constructor/accessors；File 2 initially supplies a compile-ready reject-all `validate` stub；File 3 removes only `private` from the existing helper。Files 4–5 then compile and establish RED。After RED, executor returns to File 2 and adds only the Spec-defined closed walk/mapping。Files 6–8 are reviewed docs/evidence；only final GREEN form is committed。

### 4.2 Test-first strategy

| Behavior | RED point | Minimum GREEN |
| --- | --- | --- |
| governed case acceptance/metadata | `assertDoesNotThrow` calls reject-all stub | exact safe parse/walk/mapping |
| immutability | constructor compile prerequisite can be tested | defensive copies/unmodifiable values already final |
| invalid grammar/governance | reject-all may pass negative cases | remain pass after final implementation；packaged-schema initialization faults retain internal semantics |
| BPMN compatibility | helper visibility baseline | body unchanged + final selector |

The reviewed candidate includes three acceptance methods plus named rejection vectors；exact Surefire counts are recorded after execution。Evidence records actual RED/GREEN selectors、method/execution counts、failures、skips and warnings。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Step33 committed baseline；Root Review GO | None | any concurrent edit to exact eight paths | one atomic semantic slice |

### 4.4 Commit boundaries

One semantic commit after Evidence: `feat(process): validate governed CMMN model semantics`。No stub/scaffold/doc-only commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| CMMN validator | Add / Primary §7 | none | XSD/converter too wide | one package-local class | Implement |
| case model | Add / Primary §10 | BPMN model wrong semantics | Map return would hide contract | one immutable class | Implement |
| helper visibility | Expand / Primary §9 | exact parser exists private | duplicate parser drifts | one modifier | Implement |
| converter/facade/API/DB | Remove / Primary §3 | no requirement | direct package-local path | extra contracts/state | Do not implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| metadata type | `REQ-106/107` | immutability test | existing HumanTask type | constructor/accessors | validator/test | Step 1 |
| reject-all entry | `REQ-101` | positive assertDoesNotThrow fails | metadata type | callable RED symbol | focused tests | Step 1 |
| helper reuse | `REQ-104/105` | final compile/regression | visibility change | one governance authority | validator | Step 1 |
| closed semantics | `REQ-101–105/107` | replace stub after RED | fixture/tests | validated model | later release task | Step 1 |
| evidence/scope | `REQ-108`, Step33 constraints | strict/static/final selector | GREEN | auditable commit | Root/user | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | technical release package | preserve exact current layout | no layer/module/facade | Step1；ARCH/SCOPE |
| Reuse/capability | SafeModelXml + humanTask parser + immutable model style | reuse exact capabilities | zero dependency | Step1；REUSE/DEP |
| Naming/model/validation/conversion | explicit validator/model/test roles | technical immutable value；no mapping layer | final class/nested enum/record | Step1；NAME/MODEL |
| Bean/logging/util/JSON/time/config | none affected；JDK only | plain package-private Java | no Bean/log/JSON/time/config | Step1；UTIL |
| Pattern | fixed Simple security grammar | direct cohesive walker | no ceremonial Visitor/Strategy | Step1；PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| safe DOM | Step33 parser | current source/Evidence | exact fit | reuse | None | Step1；REUSE |
| task governance | `humanTask(Element)` | current body/tests | fit；visibility gap only | package visibility | None | Step1；REUSE/TEST |
| immutable metadata | JDK collections + nearby model | `ValidatedProcessModel` | case policy absent | add minimal technical value | no dependency | Step1；MODEL/DEP |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Primary §6.2/§8 | validator/model/test semantic roles | Files 1–4 | exact names；no ambiguous carriers | compile/inventory | 1 | PASS |
| Rule 2 | Primary §6.2 | no layer/DTO handoff | Files 2/4 | technical closed validation only | negative tests | 1 | N/A |
| Rule 3 | Primary §6.2/§10 | immutable technical value | File 1 | final class + defensive copy；nested record | immutability tests | 1 | PASS |
| Rule 4 | Primary §6.2 | no business/Spring Bean | none | no annotations/DI/log | diff | 1 | N/A |
| Rule 5 | Primary §6.2 | JDK/module-local APIs | Files 1–2 | no utility dependency | import/POM diff | 1 | PASS |
| Rule 6 | Primary §6.2 | no JSON/wire enum | none | internal enum only | scope | 1 | N/A |
| Rule 7 | Primary §6.2 | no config path/key | none | no profile changes | scope | 1 | N/A |
| Rule 9 | Primary §6.2/§13 | Simple fixed technical grammar | File 2 | direct closed walk | mutation tests/review | 1 | N/A |
| Rule 10 | Primary §6.2/§6.3 | no time symbol | none | no time work | imports | 1 | N/A |
| Rule 11 | Primary §6.1/§8 | exact release package | all eight paths | no third layer/ORM/CRUD/DDL | path/static gate | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: no affected field/API/storage/calculation uses time、zone、duration、precision or Clock。

## 5. Change File Tree

```text
stellar-enclosure-tianji-process-server
├── src/main/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidator.java            CREATE
├── src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java            CREATE
├── src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java         MODIFY visibility only
├── src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidatorTest.java        CREATE
└── src/test/resources/fixtures/governed-case.cmmn                                                        CREATE
docs/egon
├── spec/2026-11-01-22-55-cmmn-semantic-validation.md                                                    CREATE
├── plan/2026-11-01-22-55-cmmn-semantic-validation.md                                                    CREATE
└── evidence/2026-11-01-cmmn-semantic-validation.md                                                      CREATE
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java` | absent | final model + Action + Policy | immutable metadata | 1 | `REQ-106/107` | model tests |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidator.java` | absent | reject-all RED then closed GREEN | semantic gate | 1 | `REQ-101–104/107` | focused tests |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java` | private humanTask | package-static；body identical | reuse task parser | 1 | `REQ-105` | diff/regression |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidatorTest.java` | absent | focused tests | RED/GREEN/security | 1 | `REQ-101–108` | focused selector |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/governed-case.cmmn` | absent | governed fixed model | positive fixture | 1 | `REQ-102/104/107` | focused selector |
| CREATE | `docs/egon/spec/2026-11-01-22-55-cmmn-semantic-validation.md` | absent | V9 Review | design | 1 | `REQ-101–108` | strict/full-read |
| CREATE | `docs/egon/plan/2026-11-01-22-55-cmmn-semantic-validation.md` | absent | V5 Review | execution contract | 1 | all effective | strict/full-read |
| CREATE | `docs/egon/evidence/2026-11-01-cmmn-semantic-validation.md` | absent | actual evidence | results/scope/commit | 1 | `REQ-108` | Root review |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- `tianji-service main@7309dfda1e536a62e5eae3770efff6816e040093` clean at inspection。
- Shared workspace may change；executor rechecks exact eight paths and never reverts others。
- Step33 files/fixture are read-only except final tests consume them。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK/settings | JDK25 + `/tmp/tianji-compat-fix/settings.xml` | same proven source reactor | module tests only |
| Parser | Step33 source/evidence | committed and unchanged | static/module |
| Existing tests | six-class final selector in §8 | sources available | no app/DB |

### 6.3 Immutable constraints and approved decisions

- No converter in production/test；no facade forward method。
- Exact fixed IDs/refs/sentries/actions/governance from Spec。
- `formal-case.cmmn` read-only and must reject for missing governance。
- Metadata role IDs in governed fixture are test-only 7/8 and 8/9。
- No POM/SQL/config/API/enum/release/runtime path。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-101` | File2 is edited twice uncommitted: reject-all RED then final GREEN | compile-ready test-first requirement | final commit has only designed validator | RED must be captured before replacement |
| `PLAN-CLAR-102` | present non-fixed structural IDs are validated globally, while fixed item/task/sentry IDs use exact constants | Root wording + current identifier convention | only strict internal grammar detail | mutation tests expose mismatch |
| `PLAN-CLAR-103` | unused xmlns declarations are inert; expanded names used by elements/attributes remain closed | existing SafeModelXml StAX ordinary attribute bound and BPMN walker convention | retain parser byte-identical; no global resource-bound reinterpretation | no extra permission/runtime authority arises from a declaration |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 验证固定 governed CMMN 并产生不可变 policy metadata

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108`
- Dependencies: Step33 committed；Root full-read/strict/hash/GO。
- Baseline state: safe XSD parser exists；formal fixture is ungoverned；no semantic validator/case model。
- Observable outcome: exact governed fixture returns immutable fixed policy metadata；all wider/ungoverned variants reject；BPMN task behavior unchanged。
- End state: exact eight-path evidence-backed commit；no consumer/admission/API/runtime capability。
- Test-first gate: `Required — final metadata constructor + reject-all validator stub + helper visibility compile prerequisites precede focused tests；positive assertDoesNotThrow assertions establish RED before final validator body.`
- Manual Checks: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java`

- Purpose: provide compile-ready and final immutable semantic metadata without authority semantics.
- Symbols: `ValidatedCaseModel`, nested `Action`, nested `PlanItemPolicy`, 4-arg constructor, `definitionKey/xml/humanTasks/planItems/operatorRoleIds` accessors.
- Repository evidence: nearby `ValidatedProcessModel` clones XML and copies nested metadata；Root fixes exact constructor/policies.
- Dependencies and consumers: JDK collections；`ValidatedProcessModel.HumanTask`；File2/test。
- Why now: validator/test cannot compile without final return type and constructor.
- Contract/signature changes: new package-private technical type only；no public/wire symbol.
- Input/output and state mapping: constructor key/XML/task map/policy map → defensive immutable fields；operator roles derived as union of policy roles.
- Error and edge behavior: nulls rejected by JDK exact checks；mutation cannot affect stored state；arbitrary construction grants no auth/publish/runtime permission.
- Standards impact: `MC-ARCH-001, MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001` — Root-selected immutable technical representation following existing style under the original continuation authority, no business POJO/Lombok/ORM.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 5, Rule 11` — semantic type names、defensive final value、JDK only、current package.
- Implementation pseudocode:

```java
final class ValidatedCaseModel {
    enum Action { DISABLE, ENABLE, START, TERMINATE }
    record PlanItemPolicy(String definitionId, Set<Action> actions, Set<String> roleIds) {
        PlanItemPolicy { require values; actions = Set.copyOf(actions); roleIds = Set.copyOf(roleIds); }
    }
    ValidatedCaseModel(String definitionKey, byte[] xml,
        Map<String, ValidatedProcessModel.HumanTask> humanTasks,
        Map<String, PlanItemPolicy> planItems) {
        require all; this.definitionKey = definitionKey; this.xml = xml.clone();
        this.humanTasks = Map.copyOf(humanTasks); this.planItems = Map.copyOf(planItems);
        this.operatorRoleIds = immutable union of every policy.roleIds;
    }
    byte[] xml() { return xml.clone(); } // all other accessors return immutable values
}
```

- Verification contribution: positive metadata and mutation tests；compile prerequisite for RED.
- After this file: immutable return type is complete but no validator behavior exists。

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidator.java`

- Purpose: own the independent fixed CMMN DOM grammar and metadata derivation.
- Symbols: package-private final validator, instance `validate(byte[],String)`, namespace/pattern/constants, private walk/child/required/invalid helpers.
- Repository evidence: `ProcessModelValidator` closed DOM patterns and generic error；Step33 parser；Root forbids converter/facade.
- Dependencies and consumers: SafeModelXml、DOM、JDK collections/regex、File3 helper、File1 model；focused test only in Step34.
- Why now: reject-all stub supplies callable RED after File1；same file later gets minimum GREEN.
- Contract/signature changes: new package-private `validate(bytes,expectedKey)` only.
- Input/output and state mapping: cloned bytes/key → safe DOM → strict sets/refs/tasks → HumanTask maps → fixed PlanItemPolicy maps → model.
- Error and edge behavior: caller-controlled model errors use exact generic no-cause invalid；all present attr values≤512；IDs valid/unique；no fallback/converter/partial result；existing packaged-schema class-init fault remains internal.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-UTIL-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 5, Rule 9, Rule 11` — semantic validator、technical boundary validation、JDK only、fixed Simple grammar、current package.
- Implementation pseudocode:

```java
// RED phase only:
ValidatedCaseModel validate(byte[] source, String expectedKey) { throw invalid(); }

// GREEN phase:
ValidatedCaseModel validate(byte[] source, String expectedKey) {
    bytes = defensive copy after null/size/key-pattern checks;
    document = SafeModelXml.parseCmmn(bytes);
    root = require CMMN definitions; closedWalk(root, globalIds);
    caze = only case; require caze.id == expectedKey;
    plan = only casePlanModel; require autoComplete literal false;
    require exact primary/supplemental/milestone items and exact definitionRef values;
    require casePlan and every fixed rule/criterion/onPart id exactly as the Spec;
    require controls, entry criteria, 3 exact sentries/one onPart/no ifPart;
    require each standardEvent contains exactly one TEXT node with exact complete/terminate;
    require exactly primaryTask/supplementalTask/completionMilestone definitions;
    primaryTask = ProcessModelValidator.humanTask(primaryElement);
    supplementalTask = ProcessModelValidator.humanTask(supplementalElement);
    require ACTION, sole lane, initial lane, empty fields, null laneVariable/approvalVariable;
    build humanTasks keyed by definition ids;
    build policies: primary(DISABLE,ENABLE,START), supplemental(START,TERMINATE), milestone(empty);
    return new ValidatedCaseModel(expectedKey, bytes, humanTasks, policies);
}
closedWalk(element, ids) {
    require allowlisted namespace/localName/parent-child/attributes only; allow structural comments only;
    bound every attribute value to 512 and reject EL markers;
    validate every present id and global uniqueness; allow only whitespace/comment structural text;
}
```

- Verification contribution: converts focused positive RED to GREEN and preserves every negative matrix.
- After this file: initially callable reject-all RED；final state is complete closed semantic validator with no consumer。

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`

- Purpose: allow same-package reuse of the single governed HumanTask parser.
- Symbols: `humanTask(Element)` modifier only.
- Repository evidence: current private static body and HumanTaskModelTest govern exact behavior.
- Dependencies and consumers: existing BPMN validation remains caller；new File2 becomes second package caller.
- Why now: File2 final phase must compile without copying task governance.
- Contract/signature changes: `private static` → package-static；signature/body byte-identical；no facade method.
- Input/output and state mapping: unchanged Element → HumanTask mapping.
- Error and edge behavior: unchanged generic errors and bounds；strict CMMN caller prevents wider input.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001` — visibility-only reuse.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 5, Rule 11` — existing semantic method/body retained in current class/package.
- Implementation pseudocode:

```java
// before
private static HumanTask humanTask(Element task) { <existing body byte-for-byte> }
// after
static HumanTask humanTask(Element task) { <same existing body byte-for-byte> }
// add no validateCmmn/forwarder/facade and change no helper/body/constant
```

- Verification contribution: source diff proves modifier-only；existing HumanTask/BPMN tests protect behavior.
- After this file: CMMN validator can reuse helper；all old call paths remain source-compatible。

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidatorTest.java`

- Purpose: define positive metadata/immutability and focused strict rejection before GREEN.
- Symbols: governed acceptance、immutability、old-formal rejection、topology/ref mutation、forbidden grammar、governance mutation、key/error tests and fixture helpers.
- Repository evidence: current release tests use fixture mutation、assertDoesNotThrow/assertThrows、generic error checks and immutable collection assertions.
- Dependencies and consumers: Files1–3、File5、read-only formal-case；no converter/engine.
- Why now: positive assertions establish behavior RED against reject-all stub.
- Contract/signature changes: test-only；no production surface beyond Spec.
- Input/output and state mapping: governed bytes/key → exact maps/actions/roles；mutations → generic no-cause exception/no metadata.
- Error and edge behavior: missing definitionRef explicitly rejected although XSD accepts；renamed fixed internal IDs、long attr、duplicate IDs、imports/EL/DI/callables/Flowable attrs、CDATA/event-fragment/comment、invalid lanes/roles/forms/variables all covered.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-SCOPE-001, MC-TEST-001` — no converter/test runtime expansion.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 11` — exact technical types、immutability and fixed grammar proof.
- Implementation pseudocode:

```java
@Test governed_case_produces_exact_immutable_metadata() {
    model = assertDoesNotThrow(() -> validator.validate(fixture("governed-case.cmmn"), "governedCase"));
    assert key/xml defensive copy; humanTasks.keys == {primaryTask,supplementalTask};
    assert primary roles {7,8}, supplemental roles {8,9};
    assert planItems primary/supplemental/milestone and exact definitions/actions/roles;
    assert operatorRoleIds == {7,8,9}; assert maps/sets reject mutation;
}
@Test old_formal_case_and_missing_definition_refs_are_rejected() {
    assertDoesNotThrow(() -> SafeModelXml.parseCmmn(formalCaseBytes));
    assert generic semantic rejection for formal-case and each removed/wrong definitionRef;
}
@Test topology_and_sentry_mutations_are_rejected() { rename each pinned plan/rule/criterion/onPart/item/task/sentry id; mutate counts/controls/criteria/onParts/ifPart/events; assert generic; }
@Test executable_or_wide_grammar_is_rejected() { inject import/EL/condition/DI/callable/Flowable attr/unknown/long attr/duplicate id/CDATA/event split or comment; assert generic no cause; }
@Test human_task_governance_mutations_are_rejected() { mutate kind/initialLane/namespace/lanes/fields/roles/variables/approval; assert generic; }
@Test expected_key_and_constructor_are_not_authority() { invalid/mismatch key rejects; direct metadata remains only immutable data with no publish API; }
```

- Verification contribution: focused RED/GREEN and `TEST-101–106`; actual method/count inventory recorded in Evidence.
- After this file: tests compile with prerequisites；positive paths are intentionally RED under stub。

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/governed-case.cmmn`

- Purpose: supply exact positive fixed topology with test-only governed ACTION lanes/roles.
- Symbols: `governedCase`, fixed item/task/sentry/milestone IDs, sole lane key per task, roles 7/8 and 8/9.
- Repository evidence: formal-case topology + existing task namespace/extension syntax from governed BPMN fixture.
- Dependencies and consumers: File4 only；not auto-deployed/published.
- Why now: completes positive RED input after test contract.
- Contract/signature changes: resource only.
- Input/output and state mapping: XML governance → HumanTask/policy metadata；no runtime instance.
- Error and edge behavior: no forms/fields/variables/approval/EL/conditions/DI/callables/Flowable attrs；all rules empty；refs explicit.
- Standards impact: `MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 6, Rule 7, Rule 9, Rule 11` — no JSON/config/business code；current fixture path.
- Implementation pseudocode:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"
             xmlns:task="urn:stellar-enclosure:tianji:task:1"
             targetNamespace="https://stellar-enclosure.example/test">
    <case id="governedCase" name="Governed Case">
        <casePlanModel id="casePlan" name="Governed Case Plan" autoComplete="false">
            <planItem id="primary" name="Primary" definitionRef="primaryTask">
                <itemControl>
                    <requiredRule id="primaryRequired"/>
                    <manualActivationRule id="primaryManualActivation"/>
                </itemControl>
            </planItem>
            <planItem id="supplemental" name="Supplemental" definitionRef="supplementalTask">
                <itemControl>
                    <requiredRule id="supplementalRequired"/>
                    <manualActivationRule id="supplementalManualActivation"/>
                </itemControl>
                <entryCriterion id="supplementalEntry" sentryRef="primaryCompleted"/>
            </planItem>
            <planItem id="milestone" name="Milestone" definitionRef="completionMilestone">
                <itemControl>
                    <requiredRule id="milestoneRequired"/>
                </itemControl>
                <entryCriterion id="milestoneCompletedEntry" sentryRef="supplementalCompleted"/>
                <entryCriterion id="milestoneTerminatedEntry" sentryRef="supplementalTerminated"/>
            </planItem>

            <sentry id="primaryCompleted">
                <planItemOnPart id="primaryCompletedPart" sourceRef="primary">
                    <standardEvent>complete</standardEvent>
                </planItemOnPart>
            </sentry>
            <sentry id="supplementalCompleted">
                <planItemOnPart id="supplementalCompletedPart" sourceRef="supplemental">
                    <standardEvent>complete</standardEvent>
                </planItemOnPart>
            </sentry>
            <sentry id="supplementalTerminated">
                <planItemOnPart id="supplementalTerminatedPart" sourceRef="supplemental">
                    <standardEvent>terminate</standardEvent>
                </planItemOnPart>
            </sentry>

            <humanTask id="primaryTask" name="Primary Task"
                       task:kind="ACTION" task:initialLane="operator">
                <extensionElements>
                    <task:taskLane key="operator" roleIds="7,8"/>
                </extensionElements>
            </humanTask>
            <humanTask id="supplementalTask" name="Supplemental Task"
                       task:kind="ACTION" task:initialLane="operator">
                <extensionElements>
                    <task:taskLane key="operator" roleIds="8,9"/>
                </extensionElements>
            </humanTask>
            <milestone id="completionMilestone" name="Completion Milestone"/>
        </casePlanModel>
    </case>
</definitions>
```

- Verification contribution: exact positive data for governed acceptance/metadata tests.
- After this file: focused RED can run；executor then returns to File2 GREEN phase。

#### File 6 — `CREATE docs/egon/spec/2026-11-01-22-55-cmmn-semantic-validation.md`

- Purpose: persist V9 Review design and scope boundary.
- Symbols: `REQ-101–108`, `INTERNAL-101/102`, `TEST-101–107`, 17 MC, 10 Rules.
- Repository evidence: baseline source、Step33 Spec/Evidence、blueprint SHA、Root decisions.
- Dependencies and consumers: Root strict/full-read/hash；File7.
- Why now: design review must precede GO.
- Contract/signature changes: docs only.
- Input/output and state mapping: evidence → closed grammar/model/test contract.
- Error and edge behavior: strict/relationship/scope failure blocks；status remains Review.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — individual exact rows.
- Implementation pseudocode:

```text
Copy reviewed /tmp V9 candidate to exact Spec path.
Run strict validator after final relationships exist；repair structural defects only.
Full-read grammar、model、interfaces、tests、17 MC and 10 Rules；record SHA in Evidence；keep Review status.
```

- Verification contribution: strict/traceability/design gate.
- After this file: Review Spec exists；not a fabricated Accepted decision。

#### File 7 — `CREATE docs/egon/plan/2026-11-01-22-55-cmmn-semantic-validation.md`

- Purpose: persist this V5 one-Step exact-eight-path execution contract.
- Symbols: effective Step33+Step34 requirements、one Step、eight files、commands/checks.
- Repository evidence: File6 + current source/test commands + prior V5 structure.
- Dependencies and consumers: File6；Root GO/executor.
- Why now: prevents implementation choices outside reviewed grammar/order.
- Contract/signature changes: docs only.
- Input/output and state mapping: effective requirements → RED/GREEN/files/evidence/commit.
- Error and edge behavior: strict/link/status/drift failure blocks；no Ready/Accepted claim.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: all `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:

```text
Copy reviewed /tmp V5 candidate after File6 exists.
Run strict Plan validator with both effective Specs；ensure all effective REQ IDs appear in Step1/traceability.
Full-read exact eight paths、stub RED、GREEN、six-class regression、evidence and one commit；record SHA.
```

- Verification contribution: strict executable-plan gate.
- After this file: Root may issue GO；no code changes occur from documentation alone。

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-cmmn-semantic-validation.md`

- Purpose: record actual RED/GREEN/regression/static/scope/commit evidence and untested boundaries.
- Symbols: baseline、Spec/Plan hashes、actual commands/results/counts、visibility/body diff、eight paths、commit、limits.
- Repository evidence: actual outputs only；existing Evidence convention.
- Dependencies and consumers: final Files1–7；Root/user review.
- Why now: last, so no result is predicted/fabricated.
- Contract/signature changes: docs only.
- Input/output and state mapping: logs/diffs/hashes → bounded reproducible claims.
- Error and edge behavior: record failures/skips/warnings；no runtime/IAM/release claim；dynamic counts actual only.
- Standards impact: `MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` with actual applicability/evidence.
- Implementation pseudocode:

```markdown
Record baseline and final exact-eight-path status plus Spec/Plan SHA.
Record actual reject-all RED and final GREEN/final-six-class outputs without prefilled counts.
Record ProcessModelValidator modifier-only/body-identity proof、formal-case rejection、immutability/mutation results and excluded POM/SQL/config/API paths.
Record commit and explicit untested boundary: no app、engine、DB、container、Case transport、browser、publish、deploy、IAM or BPMN continuation.
```

- Verification contribution: closes effective scope/test/blocker requirements with actual evidence.
- After this file: one semantic commit is evidence-complete；full T3-08 remains unfinished。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: focused/final commands in §8, strict docs, body/scope/diff gates.
- Expected result: RED observed for positive behavior only and recorded actually；GREEN/final selectors exit0；helper body unchanged；exact eight paths；no excluded path diff.
- Failure returns to: File4/5 for test/fixture defect、File1 for immutability、File2 for grammar、File3 for visibility/body drift、File6/7 for docs、Spec/user on material scope change.
- Completion criteria: all effective requirements trace to actual evidence；17 MC close；one eight-path commit；runtime boundary explicit.
- Rollback: revert one commit path-limited；no data/config/runtime rollback.
- Commit paths: `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/governed-case.cmmn`, `docs/egon/spec/2026-11-01-22-55-cmmn-semantic-validation.md`, `docs/egon/plan/2026-11-01-22-55-cmmn-semantic-validation.md`, `docs/egon/evidence/2026-11-01-cmmn-semantic-validation.md`
- Commit: `feat(process): validate governed CMMN model semantics`

## 8. Test, Validation, and Quality Gates

Root runs these finite test commands only after full-read/strict/hash GO。

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false -Dtest=CmmnModelValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

Final selector replaces `-Dtest` with:

```text
CmmnModelValidatorTest,CmmnModelXmlTest,ProcessModelValidatorTest,HumanTaskModelTest,HumanTaskTimerModelTest,ProcessReleaseServiceTest
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| RED | `tianji-service` | focused command with reject-all stub | focused | positive behavior assertions fail for reject-all；negative paths may pass；actual counts recorded | Files1–5 | `REQ-101–108`; module |
| GREEN | `tianji-service` | focused command after File2 final | focused | exit0；actual counts/skips recorded | Files1–5 | Step34；no engine/DB |
| Final regression | `tianji-service` | same command with six-class selector | current + prior release tests | exit0；actual counts/skips recorded | Files2/3 | all effective REQs；module |
| Helper body | `tianji-service` | baseline/final body-normalized diff for `humanTask(Element)` | one method | modifier-only；body identical | File3 | `REQ-105`；static |
| Spec strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py tianji-service/docs/egon/spec/2026-11-01-22-55-cmmn-semantic-validation.md --strict` | V9 | PASS | File6 | docs |
| Plan strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-22-55-cmmn-semantic-validation.md --strict` | V5 | PASS | File7/Spec | docs |
| Scope | `tianji-service` | `git status --short`；`git diff --check`；baseline path/hash inventory | exact8/exclusions | no extra/POM/SQL/config/API/parser/formal-fixture diff | owner/stop | `REQ-108`; static |
| Live | user environment | not authorized | runtime/IAM/DB/network/browser | not run | future T3-08 | runtime unvalidated |

No `scripts/work` harness；existing JUnit/source-reactor and static methods cover all gates。

## 9. Migration, Compatibility, Rollout, and Rollback

N/A migration/DDL/data/config/permission/dependency rollout。Package helper visibility only；new types have no production caller。Rollback one commit；no forward data fix。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-001` | Step33 §4 | Step1 | prior parser unchanged | final selector | prior safe parse preserved |
| `REQ-002` | Step33 §4 | Step1 | prior parser unchanged | CmmnModelXmlTest | prior security preserved |
| `REQ-003` | Step33 §4 | Step1 | File3 only | final selector/body diff | old parsers unchanged |
| `REQ-004` | Step33 §4 | Step1 | old formal fixture read-only | rejection test | boundary explicit |
| `REQ-005` | Step33 §4 | Step1 | prior tests | final selector | prior tests green |
| `REQ-006` | Step33 §4 | Step1 | all eight/exclusions | scope | no expansion |
| `REQ-101` | Primary §4 | Step1 | Files2/4/5 | `TEST-101/106` | governed/key result |
| `REQ-102` | Primary §4 | Step1 | Files2/4/5 | `TEST-101/103` | topology result |
| `REQ-103` | Primary §4 | Step1 | Files2/4 | `TEST-103/104` | closed grammar |
| `REQ-104` | Primary §4 | Step1 | Files2–5 | `TEST-101/105` | governed tasks |
| `REQ-105` | Primary §4 | Step1 | File3 | body diff/final selector | visibility-only |
| `REQ-106` | Primary §4 | Step1 | Files1/4 | `TEST-101/102` | immutability |
| `REQ-107` | Primary §4 | Step1 | Files1/2/4/5 | `TEST-101` | actions/roles/union |
| `REQ-108` | Primary §4 | Step1 | all eight | scope/final/Evidence | exact commit |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `RISK-101` | reject-all stub committed | File2 | two-phase Plan | executor | Closed by GREEN/source review |
| `RISK-102` | helper body drift | File3 | modifier-only requirement | executor | Closed by body diff/final selector |
| `RISK-103` | metadata used as authority | Files1/2 | package-private/no consumer/Javadoc | later release owner | Closed by scope |
| `RISK-104` | shared workspace conflict | all | other agents active | executor | recheck/no revert |

No open design blocker。Original continuation authorization covers implementation after Root GO；Review status remains honest。

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Plan includes every fixed ID/ref/control/sentry/task/action/role/immutability/security/scope requirement and protects Step33。

### 12.2 Spec consistency

No converter、facade、public contract、DB、POM or runtime surface appears。All Plan clarifications are local and reversible。

### 12.3 Repository executability

Current source symbols、fixtures、source-reactor/settings and prior Evidence inspected；file order explains compile prerequisites and uncommitted RED/GREEN return。

### 12.4 Test and release completeness

Focused RED/GREEN、six-class regression、helper body diff、strict docs and exact scope gates defined；numeric results deferred to Evidence。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | Spec §6.1；exact tree | technical package preserved | None |
| `MC-REUSE-001` | Applicable | PASS | §4.7 ledger | parser/task helper/model style reused | None |
| `MC-DEP-001` | Applicable | PASS | exact paths/POM excluded | no dependency | None |
| `MC-NAME-001` | Applicable | PASS | Files1–4 inventory | explicit technical roles | None |
| `MC-VALID-001` | Not applicable | N/A | no layer/DTO boundary | technical closed XML validation | None |
| `MC-MODEL-001` | Applicable | PASS | File1/Spec §10 | immutable technical value per explicit scope | None |
| `MC-CONVERT-001` | Not applicable | N/A | no mapper/converter；native converter forbidden | no conversion boundary | None |
| `MC-LOG-001` | Not applicable | N/A | no business class/log | logging absent | None |
| `MC-BEAN-001` | Not applicable | N/A | no Spring Bean/DI | injection absent | None |
| `MC-UTIL-001` | Applicable | PASS | JDK/module types only | utility allowlist | None |
| `MC-JSON-001` | Not applicable | N/A | no JSON/wire enum | JSON absent | None |
| `MC-TIME-001` | Not applicable | N/A | §4.9 | time absent | None |
| `MC-CONFIG-001` | Not applicable | N/A | no config path | parity unchanged | None |
| `MC-PATTERN-001` | Not applicable | N/A | Simple fixed technical grammar | no complex business logic | None |
| `MC-SCOPE-001` | Applicable | PASS | §5/Step exact eight | no expansion | None |
| `MC-TEST-001` | Applicable | PASS | §7/§8 | exact future gates；actual counts deferred | Execute after GO |
| `MC-BLOCKER-001` | Applicable | PASS | §11/all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

Immutable technical validation value is not business DTO/PO/ORM；Root follows the existing immutable technical model style under the original task authority; no extra user acceptance of a representation is claimed。No persistence、Bean Validation layer、uniqueness/DDL、CQE/Event/outbox。Rule11 current technical package remains。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/catalog、generator、manifest、SQL journal or dependency introduction。Any generated/POM/SQL path blocks Step。
