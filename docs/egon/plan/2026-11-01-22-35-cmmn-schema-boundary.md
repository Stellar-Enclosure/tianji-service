# CMMN 1.1 安全 Schema 边界实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-01-22-35-cmmn-schema-boundary.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 22:35 Asia/Shanghai` |
| Updated | `2026-11-01 22:35 Asia/Shanghai` |
| Owner | `原用户已授权继续蓝图并分步提交；Root 负责派生设计、实现和验证；Review 不冒充另行 Accepted` |
| Repository | `tianji-service` |
| Scope | `Step 33；process-server release package 的 CMMN schema prerequisite；精确六路径` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Step 33` |
| Baseline Revision | `main@5881106ca80a9bb109f2c85e596bd4943b715845；检查时 clean` |
| Implements Spec | [CMMN 1.1 安全 Schema 边界](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-01；SHA-256 5f73d81cae7ff61c3b257227326aafe2841483fddd3a3467dd9d0b0380c01429` |
| Effective Specs | [CMMN 1.1 安全 Schema 边界](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本 Plan 实施同名 Review Spec，只含一个独立可验证 Step、六个精确路径和一个语义提交。先在 `SafeModelXml` 增加 compile-ready generic-reject stub，使新测试可编译；加入 7 个 focused tests 与中立 fixture，预期 stub RED 为 6 个 acceptance failures、1 个纯拒绝测试通过；再以 supplier-based CMMN schema implementation 达到 GREEN；最后写 Evidence 并做旧解析回归和精确路径检查。实际计数只在执行后写入 Evidence。

该 Plan 是 Root 的 source-grounded Review，不冒充 Spec `Accepted` ；执行授权来自原用户继续蓝图并分步提交的请求。实现前仍须 Root full-read/strict/hash GO。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [CMMN 1.1 安全 Schema 边界](../spec/2026-11-01-22-35-cmmn-schema-boundary.md)
- Status: `Review`
- Revision: `Updated 2026-11-01 22:35 Asia/Shanghai`，baseline `5881106ca80a9bb109f2c85e596bd4943b715845`
- Approval evidence: 原用户要求继续蓝图未完成工作并分步提交、完成后由用户审查；Root 负责本内部派生设计的 full-read/strict/hash 和实施 GO，不冒充用户另行接受 Spec/API。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [CMMN schema boundary](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) | Review / timestamp above | all | 唯一、聚焦且足以实施 Step 33 |

### 2.3 Superseded or excluded content

None. Legacy blueprint only underlies the Spec；本 Plan 不实施完整 T3-08。Model enum/admission/release/deploy/API/DB/permissions/config/runtime remain excluded。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-001` | Spec §4 | package-private CMMN 1.1 safe parse | valid fixture returns DOM；wrong schema rejected | `SafeModelXml`, test |
| `REQ-002` | Spec §4 | preserve bounds/UTF-8/XXE/generic error | unsafe byte/XML cases reject without cause leak | parser + negative tests |
| `REQ-003` | Spec §4 | preserve old signature/behavior/lazy timing | old BPMN/DMN tests remain green；separate holder | parser + regression |
| `REQ-004` | Spec §4 | fixed neutral 3-plan-item model | converter retains exact manual/sentry/milestone structure | fixture + positive test |
| `REQ-005` | Spec §4 | two model positives parse first；four exact-limit tests accept boundary/reject over | six acceptance tests RED on stub；seven focused GREEN | focused test |
| `REQ-006` | Spec §4 | six paths, no dependencies or wider surfaces | exact diff and immutable excluded paths | docs/evidence/static gate |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

One uncommitted Step owns all six paths. File 1 is staged twice inside that Step: first a compile prerequisite stub `parseCmmn(byte[])` that always throws the existing generic invalid exception；after Files 2–3 are present and RED is observed, the same method is replaced by the final shared-parser implementation。It is listed once and committed only in final GREEN form。Spec/Plan remain Review documentation；Evidence is written only from actual commands/diffs。

### 4.2 Test-first strategy

| Behavior | RED point | Minimum GREEN |
| --- | --- | --- |
| valid CMMN XSD | positive schema test calls stub | shared private parse with CMMN schema supplier |
| fixed neutral structure | converter test first calls stub | safe parse succeeds, then real `CmmnXmlConverter` retains structure |
| exact four limits | exact byte/depth/element/attribute boundary calls stub | exact boundary parses；one-over rejects |
| strict invalid matrix | invalid cases call stub | remains generic rejection under final implementation |
| old BPMN/DMN compatibility | existing suite is baseline | old entry delegates through same body；separate CMMN holder |

Expected RED shape is 6 acceptance failures and one rejection-matrix pass, but it is unexecuted planning intent. Evidence records actual observed counts/output rather than pre-filling them。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Root full-read/strict/hash/GO of Review docs under original user continuation authority | None | any other edits to the six paths | one atomic RED/GREEN slice and one commit |

### 4.4 Commit boundaries

One semantic commit after GREEN and evidence: `feat(process): add safe CMMN schema boundary`。No stub-only commit、doc-only scaffold commit or unrelated staging。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| `parseCmmn` | Add / Spec §7.0 | no safe CMMN entry | direct converter bypasses platform gate | one package-private method | Implement |
| private supplier parser | Add / Spec §7.0 | current parse body shared by BPMN/DMN | copy body risks drift | one JDK `Supplier` | Implement |
| separate holder | Add / Spec §7.0 | current `Schemas` owns old lazy fields | adding CMMN there changes timing | one nested private holder | Implement |
| fixture/test | Add / Spec §7.0 | baseline fixture lacks sentry/milestone | cannot prove structure/limits | test-only | Implement |
| public API/table/config/dependency | Remove / Spec §3.2 | no requirement | none needed | would expand contracts/ops | Do not implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| compile-safe entry | `REQ-001/002` | stub lets test compile | existing `SafeModelXml` | RED callable symbol | focused test | Step 1 |
| formal schema/model/limit proof | `REQ-004/005` | 6 acceptance failures | fixture + existing Flowable dependency | exact expectations | GREEN implementation | Step 1 |
| secure final parser | `REQ-001/002/003` | replace stub after RED | XSD resource inventory | safe DOM | future separate validator task | Step 1 |
| evidence/scope | `REQ-006` | static/docs gates | GREEN outputs | auditable commit | Root/user review | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | existing technical `process.server.release` package | explicit preserve-current placement | no new business/DDD layer | Step 1；`MC-ARCH-001` |
| Reuse/capability | existing `SafeModelXml`, Flowable 8 CMMN jar/POM | reuse JDK/packaged resources | no dependency/custom parser framework | Step 1；`MC-REUSE-001/MC-DEP-001` |
| Naming/model/validation/conversion | utility + `*Test`; no carrier | no POJO/mapper/layer validation | retain class；semantic test suffix | Step 1；`MC-NAME-001` |
| Bean/logging/util/JSON/time/config | no Bean/log/JSON/time/config；JDK utility only | no new cross-cutting surface | only `java.util.function.Supplier` | Step 1；`MC-UTIL-001` |
| Business variation/pattern | fixed Simple schema selection | direct private method | no ceremonial Strategy/Factory | Step 1；`MC-PATTERN-001` |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| bounded XML parse | JDK + module utility | `SafeModelXml` existing pipeline | exact fit except selector | extend/reuse | one private overload only | Step 1；REUSE |
| schema loading | existing `schema(...)` resolver | existing BPMN/DMN use | exact fit | reuse with five-item whitelist | None | Step 1；DEP/UTIL |
| structure oracle | `CmmnXmlConverter` 8.0.0 | current POM/local jar `javap` | exact test fit | reuse fixed converter | None | Step 1；TEST |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2/§8 | existing `SafeModelXml`；new `CmmnModelXmlTest` | Files 1–2 | retain utility name；semantic `*Test` | compile/type review | 1 | PASS |
| Rule 2 | Spec §6.2 | no layer/DTO handoff | Files 1–2 | preserve technical byte validation；no fake Bean Validation | invalid-input tests | 1 | N/A |
| Rule 3 | Spec §6.2/§10 | no POJO/converter | none | no Lombok/MapStruct additions | diff | 1 | N/A |
| Rule 4 | Spec §6.2 | no business/Spring Bean | none | no Bean/log/DI | diff | 1 | N/A |
| Rule 5 | Spec §6.2 | JDK utility policy | File 1 | only JDK `Supplier` and existing XML APIs | import/POM diff | 1 | PASS |
| Rule 6 | Spec §6.2 | no JSON contract | none | no Jackson changes | diff | 1 | N/A |
| Rule 7 | Spec §6.2 | no config key/path | none | no profile changes | diff | 1 | N/A |
| Rule 9 | Spec §6.2/§13 | Simple fixed logic | File 1 | direct selector；no ceremonial abstraction | review/tests | 1 | N/A |
| Rule 10 | Spec §6.2/§6.3 | no time symbol | none | no time work | import diff | 1 | N/A |
| Rule 11 | Spec §6.1/§6.2/§8 | exact technical release/test/resource tree | all six paths | preserve current module/package；no new layer/ORM/CRUD/DDL | path/static gate | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: no date/time field、calculation、storage、wire、zone、duration、precision or Clock changes exist in the six paths。

## 5. Change File Tree

```text
stellar-enclosure-tianji-process-server
├── src/main/java/top/egon/cola/platform/tianji/process/server/release/SafeModelXml.java                 MODIFY
├── src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelXmlTest.java            CREATE
└── src/test/resources/fixtures/formal-case.cmmn                                                        CREATE
docs/egon
├── spec/2026-11-01-22-35-cmmn-schema-boundary.md                                                      CREATE
├── plan/2026-11-01-22-35-cmmn-schema-boundary.md                                                      CREATE
└── evidence/2026-11-01-cmmn-schema-boundary.md                                                        CREATE
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/SafeModelXml.java` | current `parse(bytes,boolean)` | `parseCmmn`, private supplier parse, `CmmnSchema` | security boundary | 1 | `REQ-001/002/003` | focused/regression |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelXmlTest.java` | absent；nearby JUnit style | 7 tests | RED/GREEN/exact-limit/converter proof | 1 | `REQ-001–005` | focused test |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case.cmmn` | absent；baseline fixture nearby | fixed neutral model | XSD/converter input | 1 | `REQ-004` | focused test |
| CREATE | `docs/egon/spec/2026-11-01-22-35-cmmn-schema-boundary.md` | absent | V9 Review Spec | design authority under review | 1 | `REQ-001–006` | strict validator/full-read |
| CREATE | `docs/egon/plan/2026-11-01-22-35-cmmn-schema-boundary.md` | absent | V5 Review Plan | one-Step execution contract | 1 | `REQ-001–006` | strict validator/full-read |
| CREATE | `docs/egon/evidence/2026-11-01-cmmn-schema-boundary.md` | absent | actual-only evidence | RED/GREEN/scope/commit record | 1 | `REQ-006` | Root review |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- `tianji-service main@5881106ca80a9bb109f2c85e596bd4943b715845` clean at inspection。
- Other agents may edit shared workspace；executor rechecks six paths and does not revert others。
- At most one focused commit after actual validation；no source edit before Root GO。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK | `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` | JDK 25 | compile/test only |
| Maven settings | `/tmp/tianji-compat-fix/settings.xml` | local repository `/Users/mario/maven/repository` | no dependency download claim |
| Reactor | `./scripts/verify-source-reactor.sh` | sibling source repos present | no service startup |
| Flowable artifact | local `flowable-cmmn-converter-8.0.0.jar` | five XSD + converter present | classpath/static/module only |

### 6.3 Immutable constraints and approved decisions

- Exact CMMN directory/whitelist: `org/flowable/impl/cmmn/parser/` and five XSD names from Spec。
- `parse(byte[],boolean)` remains package-private and signature-compatible。
- No POM、model enum、release validator/service、API、DB、SQL、config、permission、runtime or existing fixture edit。
- Both model-positive tests invoke `parseCmmn` before any converter assertion；four limit tests exercise their own exact boundary。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-001` | File 1 has an uncommitted stub phase then final phase | new method must exist for compile-ready RED | test-first sequencing only；final commit contains no stub | executor must capture RED before replacement |
| `PLAN-CLAR-002` | private parse receives `Supplier<Schema>` | Root design + old holder pattern | no external signature/state | equivalent private selector allowed only with same lazy timing proof |
| `PLAN-CLAR-003` | 第一轮 GREEN 发现 XSD 的 definitionRef 可选；纯 schema 负例改为缺少必需 targetNamespace，并给 vectors 加索引诊断 | 固定8.0.0 tPlanItem 无 use=required，tDefinitions.targetNamespace 为 required；GREEN1 7/1fail | 不弱化 parser；未解析引用仍属后续 semantic validator | 复跑 focused 和 final；保留 GREEN1 失败证据 |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 建立安全 CMMN schema prerequisite 并证明固定中立模型

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006`
- Dependencies: Root full-read/strict/hash/GO of Review Spec and Plan；no coding predecessor.
- Baseline state: `SafeModelXml` safely parses BPMN/DMN only；baseline CMMN fixture has one task；no `parseCmmn` or focused test.
- Observable outcome: valid formal CMMN passes the same safety gate and fixed converter retains the designed neutral structure；unsafe inputs remain generic rejects；exact four limits accept boundary/reject one-over；old parsers remain unchanged.
- End state: exact six paths form one evidence-backed commit；no admission/release/deploy/API/DB/runtime capability is added.
- Test-first gate: `Required — add a compile-ready generic-reject stub, then observe the planned six acceptance failures and one rejection-matrix pass before replacing the stub with final implementation.`
- Manual Checks: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/SafeModelXml.java`

- Purpose: add the minimum package-private CMMN entry while keeping one hardened parser body and old lazy schema behavior.
- Symbols: `parse(byte[],boolean)`, new `parseCmmn(byte[])`, new private `parse(byte[],Supplier<Schema>)`, private `CmmnSchema.CMMN`, existing `Schemas`.
- Repository evidence: current class already owns all bounds、UTF-8、StAX/DOM and packaged-schema behavior；current `Schemas` is the lazy BPMN/DMN holder.
- Dependencies and consumers: package-local release tests/future separately approved validator；JDK `Supplier`；existing `schema(...)`；Flowable packaged resources.
- Why now: a callable stub is the compile prerequisite for the RED tests；the same file later becomes minimum GREEN without a separate commit.
- Contract/signature changes: add `static Document parseCmmn(byte[] bytes)` only；preserve `static Document parse(byte[] bytes, boolean bpmn)` exactly for callers.
- Input/output and state mapping: bytes → strict UTF-8 String → bounded scan → hardened namespace DOM → selected schema validation → `Document`；only cached state is the chosen private static `Schema`.
- Error and edge behavior: bounds execute before `Supplier#get`；client XML/validation failures become current generic invalid exception with no cause; packaged-schema initialization faults retain existing internal error behavior；resolver accepts only five exact relative system IDs；no external DTD/schema/entity.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001` — existing technical utility and JDK-only policy are preserved.
- Literal rule enforcement: `Rule 1, Rule 5, Rule 9, Rule 11` — no carrier/ambiguous name、JDK-only import、Simple direct logic、exact current package.
- Implementation pseudocode:

```java
// RED compile prerequisite, kept only until focused RED is captured:
static Document parseCmmn(byte[] bytes) { throw invalid(); }

// GREEN final state after Files 2-3 establish RED:
static Document parse(byte[] bytes, boolean bpmn) {
    return parse(bytes, () -> bpmn ? Schemas.BPMN : Schemas.DMN);
}
static Document parseCmmn(byte[] bytes) { return parse(bytes, () -> CmmnSchema.CMMN); }
private static Document parse(byte[] bytes, Supplier<Schema> schemaSupplier) {
    try {
        reject null/empty/length > MAX_BYTES before schemaSupplier.get()
        preserve exact decode -> scan -> hardened DOM construction and encoding check
        validator = schemaSupplier.get().newValidator()
        preserve external-access properties, ERRORS, DOM validation and generic catch
    } catch (Exception failure) { throw invalid(); }
}
private static final class CmmnSchema {
    static final Schema CMMN = schema("CMMN11.xsd", "org/flowable/impl/cmmn/parser/",
        Set.of("CMMN11.xsd", "CMMN11CaseModel.xsd", "CMMNDI11.xsd", "DC.xsd", "DI.xsd"));
}
```

- Verification contribution: makes tests compile under stub、produces intended focused RED、then makes all focused tests GREEN while old tests protect compatibility.
- After this file: temporarily RED after Files 2–3；final uncommitted state is GREEN-capable and contains no stub-only behavior.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelXmlTest.java`

- Purpose: prove packaged XSD acceptance/security、all four exact parser limits and fixed converter structure without engine or DB.
- Symbols: 7 focused tests: formal schema、fixed converter、strict invalid matrix、exact byte/depth/element/attribute boundaries；plus fixture/builder helpers.
- Repository evidence: same package uses JUnit 5、`getResourceAsStream`、`ByteArrayInputStream`、static assertions and real Flowable converters in `ProcessModelValidatorTest`.
- Dependencies and consumers: calls package-private `SafeModelXml`；reads File 3；uses existing `CmmnXmlConverter` only after safe parse.
- Why now: defines exact RED behavior before final File 1 implementation.
- Contract/signature changes: test-only；expects `parseCmmn(byte[])` and no other production symbol.
- Input/output and state mapping: fixture bytes → DOM；same bytes → fixed converter model；generated schema-valid boundary documents → exact accept/one-over reject；mutated unsafe bytes → generic exception.
- Error and edge behavior: 6 acceptance tests fail under stub；pure rejection matrix passes；GREEN asserts no cause/content leak and rejects only one-over limits.
- Standards impact: `MC-NAME-001, MC-REUSE-001, MC-DEP-001, MC-SCOPE-001, MC-TEST-001` — semantic test name and current JUnit/Flowable dependencies only.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 5, Rule 9, Rule 11` — focused technical validation、JDK/JUnit APIs、direct assertions、current test package.
- Implementation pseudocode:

```java
@Test accepts_formal_case_against_packaged_schema() {
    bytes = fixture("formal-case.cmmn"); document = assertDoesNotThrow(() -> SafeModelXml.parseCmmn(bytes));
    assert root localName == "definitions" and namespace == CMMN_11_MODEL;
}
@Test fixed_converter_retains_formal_case_structure() {
    bytes = fixture("formal-case.cmmn"); assertDoesNotThrow(() -> SafeModelXml.parseCmmn(bytes));
    model = new CmmnXmlConverter().convertToCmmnModel(() -> new ByteArrayInputStream(bytes), true, true);
    assert case/plan ids, autoComplete false, 3 plan items, 2 manual humans, 3 sentries, required primary/milestone;
    assert primary:complete -> supplemental entry and supplemental:complete OR terminate -> milestone entries;
    assert no repetition or custom extension; inspect the fixed fixture for absent expressions/forms;
}
@Test strict_invalid_matrix_rejects_generically() {
    assert null, empty, malformed UTF-8, DOCTYPE/XXE and wrong namespace throw same no-cause invalid exception;
}
@Test exact_byte_limit_accepts_and_one_over_rejects() { assert assertDoesNotThrow(schema-valid MAX_BYTES parse); MAX_BYTES+1 rejects; }
@Test exact_depth_limit_accepts_and_one_over_rejects() { assertDoesNotThrow(MAX_DEPTH parse); MAX_DEPTH+1 rejects; }
@Test exact_element_limit_accepts_and_one_over_rejects() { assertDoesNotThrow(MAX_ELEMENTS parse); MAX_ELEMENTS+1 rejects; }
@Test exact_attribute_limit_accepts_and_one_over_rejects() { assertDoesNotThrow(MAX_ATTRIBUTES parse); MAX_ATTRIBUTES+1 rejects; }
```

- Verification contribution: exact `CmmnModelXmlTest` selector covers Spec `TEST-001–007`; planned stub RED is 6 acceptance failures plus 1 rejection pass, actual outcome recorded later.
- After this file: test source compiles with stub；behavior remains intentionally RED until File 3 exists and File 1 final phase is applied.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case.cmmn`

- Purpose: provide one neutral schema-valid model proving manual tasks、sentry chaining and OR milestone criteria without business fields or executable extensions.
- Symbols: case `formalCase`；plan `casePlan`；plan items `primary`、`supplemental`、`milestone`；definitions `primaryTask`、`supplementalTask`、`completionMilestone`；three sentries.
- Repository evidence: Flowable 8 `CMMN11CaseModel.xsd` requires planItems then sentries then planItemDefinitions；native reconciliation fixed required/manual controls and separate OR entry criteria.
- Dependencies and consumers: File 2 only in this Step；future use requires a separate source-grounded Step under the original continuation scope.
- Why now: completes RED fixture after test contract is written.
- Contract/signature changes: test resource only；no runtime auto-deployment/admission.
- Input/output and state mapping: primary completion sentry enables supplemental entry；supplemental complete or terminate independently enters required milestone；no runtime state is created here.
- Error and edge behavior: `autoComplete=false`；primary and milestone required；no repetition/planning table/condition/EL/form/custom extension/class/code.
- Standards impact: `MC-SCOPE-001, MC-TEST-001` — fixed, non-executable test data only.
- Literal rule enforcement: `Rule 6, Rule 7, Rule 9, Rule 11` — no JSON/config/business branching；resource remains in current fixture tree.
- Implementation pseudocode:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"
             targetNamespace="https://stellar-enclosure.example/test">
    <case id="formalCase" name="Formal Case">
        <casePlanModel id="casePlan" name="Formal Case Plan" autoComplete="false">
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

            <humanTask id="primaryTask" name="Primary Task"/>
            <humanTask id="supplementalTask" name="Supplemental Task"/>
            <milestone id="completionMilestone" name="Completion Milestone"/>
        </casePlanModel>
    </case>
</definitions>
```

- Verification contribution: supplies exact XSD/converter artifact for `TEST-001/002`.
- After this file: focused suite runs with stub and should show planned RED；executor then returns to File 1 final phase.

#### File 4 — `CREATE docs/egon/spec/2026-11-01-22-35-cmmn-schema-boundary.md`

- Purpose: preserve the V9 source-grounded Review design and no-expansion boundary.
- Symbols: `REQ-001–006`, `INTERNAL-001`, `TEST-001–008`, 17 Manual Checks, 10 literal rules.
- Repository evidence: blueprint SHA、baseline symbols、local jar inventory and official fixed-tag XSD sources.
- Dependencies and consumers: Root full-read/strict/hash；File 5 implements this Review Spec.
- Why now: design authority must be reviewable before GO and evidence/commit.
- Contract/signature changes: documentation only.
- Input/output and state mapping: evidence/decisions → exact parser/test/fixture contract.
- Error and edge behavior: any strict failure、open major decision or scope drift blocks source edits；status remains Review.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — individual evidence rows, no weakening.
- Implementation pseudocode:

```text
Copy the reviewed /tmp V9 candidate to the exact Spec path without changing normative scope.
Run the strict Spec validator at the final filename；repair structural defects only.
Full-read requirements、change surface、internal contract、fixture semantics、17 checks and 10 rules；record SHA-256 for Evidence.
```

- Verification contribution: strict structural/traceability PASS plus Root full-read review.
- After this file: a Review Spec exists；it is not Accepted and does not independently authorize source work.

#### File 5 — `CREATE docs/egon/plan/2026-11-01-22-35-cmmn-schema-boundary.md`

- Purpose: preserve this V5 one-Step, six-path RED/GREEN/commit contract.
- Symbols: one Step、six File blocks、exact commands、all rules/checks and requirement traceability.
- Repository evidence: current build script/settings、source paths、Review Spec and prior V5 plan structure.
- Dependencies and consumers: depends on File 4；Root GO/executor consumes it.
- Why now: implementation must not invent order、stub semantics、fixture or validation scope.
- Contract/signature changes: documentation only.
- Input/output and state mapping: Spec requirements → ordered files → tests/evidence/commit.
- Error and edge behavior: strict/link/spec-status failure blocks；Plan stays Review and never claims user approval.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — exact per-file obligations and final matrix.
- Implementation pseudocode:

```text
Copy the reviewed /tmp V5 candidate to the exact Plan path after File 4 exists.
Run strict Plan validation so Implements Spec resolves to the one Review Spec and every requirement appears in Step 1.
Full-read exact six paths、RED stub timing、GREEN implementation、commands、evidence boundaries and one commit；record SHA-256.
```

- Verification contribution: strict Plan PASS、link resolution and executable file-order proof.
- After this file: Root can issue an explicit implementation GO；no source action has occurred merely because the Plan passes review.

#### File 6 — `CREATE docs/egon/evidence/2026-11-01-cmmn-schema-boundary.md`

- Purpose: record actual, non-fabricated RED/GREEN/static/scope/commit evidence and untested boundaries.
- Symbols: baseline、Spec/Plan SHA、commands/logs、actual test outcomes、exact diff、XSD inventory、commit and limits.
- Repository evidence: existing `docs/egon/evidence` convention and actual execution artifacts only.
- Dependencies and consumers: Files 1–5 final state and commands in §8；Root/user review.
- Why now: evidence is last because results must not be predicted or backfilled.
- Contract/signature changes: documentation only.
- Input/output and state mapping: real terminal outputs/diffs/hashes → reproducible claims.
- Error and edge behavior: record failures/skips/warnings exactly；do not call static test engine/runtime/DB acceptance；no invented counts.
- Standards impact: `MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001` — actual proof and limitations close the Step.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11` — evidence separately states applicable/N/A results without generalization.
- Implementation pseudocode:

```markdown
Record baseline and final six-path status plus Spec/Plan SHA-256.
Record actual compile-ready RED command/log and whether it matched the planned 6 acceptance failures / 1 rejection pass.
Record actual GREEN focused/regression commands、results、XSD/resource checks、diff-check and POM/SQL/config/API exclusion checks.
Record one commit id and state explicitly: no app、engine、DB、container、Case transport、browser、release、deployment or Case runtime was executed.
```

- Verification contribution: closes `REQ-006`, `MC-TEST-001` and `MC-BLOCKER-001` from actual evidence.
- After this file: one six-path commit is evidence-complete；full T3-08 remains unfinished.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: focused RED/GREEN and regression commands in §8, followed by strict document validators and path/static checks.
- Expected result: RED actual result is recorded and should isolate the six acceptance tests；GREEN named focused/regression selectors pass；exact six paths only；no POM/SQL/config/API/enum/admission diff.
- Failure returns to: File 2/3 for fixture/assertion defects、File 1 for parser/security/compatibility defects、File 4/5 for document defects、or Spec/user for material scope/contract drift.
- Completion criteria: every `REQ-001–006` has actual evidence、all Manual Checks close、Evidence states runtime boundary、and one semantic commit contains exactly the six paths.
- Rollback: path-limited revert of the one commit；no data/config/permission migration or runtime rollback.
- Commit paths: `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/SafeModelXml.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/CmmnModelXmlTest.java`, `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case.cmmn`, `docs/egon/spec/2026-11-01-22-35-cmmn-schema-boundary.md`, `docs/egon/plan/2026-11-01-22-35-cmmn-schema-boundary.md`, `docs/egon/evidence/2026-11-01-cmmn-schema-boundary.md`
- Commit: `feat(process): add safe CMMN schema boundary`

## 8. Test, Validation, and Quality Gates

Execution uses the existing source reactor；Root alone runs the bounded test commands after GO。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml \
  -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=CmmnModelXmlTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Regression selector after GREEN:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml \
  -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false \
  -Dtest=CmmnModelXmlTest,ProcessModelValidatorTest,HumanTaskModelTest,HumanTaskTimerModelTest,ProcessReleaseServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| RED | `tianji-service` | first command with generic stub | 7 focused tests | actual result recorded；planned shape 6 acceptance failures/1 rejection pass, no compiler/fixture error | Files 1–3 | `REQ-001/004/005`; module static |
| GREEN | `tianji-service` | first command after final File 1 | focused | exit 0；all 7 focused tests pass | Files 1–3 | `REQ-001–005`; no engine/DB |
| Regression | `tianji-service` | second command | named release/model tests | exit 0；all named tests pass, with actual counts/skips recorded | File 1 | `REQ-003/006`; module |
| Spec strict | repository root | `python3 .agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py tianji-service/docs/egon/spec/2026-11-01-22-35-cmmn-schema-boundary.md --strict` | V9 | PASS | File 4 | docs/static |
| Plan strict | repository root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-22-35-cmmn-schema-boundary.md --strict` | V5 | PASS | File 5/Spec | docs/static |
| Scope | `tianji-service` | `git status --short`；`git diff --check`；path-limited diff against baseline | exact six | no extra path/whitespace error；POM/SQL/config/API/enum/admission unchanged | owning file/stop | `REQ-006`; static |
| Live | user-controlled environment | not authorized in this Step | app/engine/DB/network/browser | not run and explicitly recorded | later T3-08 | runtime unvalidated |

No `scripts/work` harness is required；existing source-reactor and focused JUnit selectors cover the gates。

## 9. Migration, Compatibility, Rollout, and Rollback

N/A migration/DDL/data/config/permission/dependency rollout。The old package-private signature stays。New method has no consumer outside test in this Step。Rollback reverts one commit；no engine schema or ProcessDB migration exists。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-001` | Spec §4/§9 | Step 1 | Files 1–2 | focused GREEN | Evidence parser result |
| `REQ-002` | Spec §4/§15 | Step 1 | Files 1–2 | invalid matrix + four limits | generic/boundary result |
| `REQ-003` | Spec §4/§16 | Step 1 | Files 1–2 | regression selector | old suite result |
| `REQ-004` | Spec §4/§14 | Step 1 | Files 2–3 | converter test | exact model assertions |
| `REQ-005` | Spec §4/§14 | Step 1 | File 2 | 7-test RED/GREEN | actual focused outcomes |
| `REQ-006` | Spec §3/§16 | Step 1 | all six | strict/scope/commit | path diff + Evidence |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `RISK-001` | Review Spec/Plan mistaken for approval | Files 4–5 | status/wording | Root/user | Closed：remain Review；explicit GO required |
| `RISK-002` | RED stub accidentally committed | File 1 | staged two-phase plan | executor | Closed：GREEN/source review before commit |
| `RISK-003` | fixture treated as runtime proof | Files 2,3,6 | no engine/DB gate | Root | Closed：Evidence boundary mandatory |
| `RISK-004` | concurrent shared-workspace edit | all | other agents active | executor | Closed by pre-edit path recheck/no revert |

No open major blocker。原用户的 continuation authorization 覆盖本必要前置实现；用户将在完成后审查。Root full-read/strict/hash GO 是本 Step 的实施门禁，无需新增用户批准环节。

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

The Plan covers only Step 33 parser prerequisite、exact neutral fixture、7-test RED/GREEN discipline、six paths and explicit exclusions。

### 12.2 Spec consistency

Every file、symbol、XSD name、fixture control and test maps to the Review Spec。No public contract、persistence、runtime or dependency is introduced。

### 12.3 Repository executability

Paths/symbols/build script/local artifact/settings were inspected。File 1's uncommitted stub phase is the only clarified sequencing detail；exact final paths and one commit are fixed。

### 12.4 Test and release completeness

Focused RED、focused GREEN、named release regression、strict docs and scope diff are ordered。Engine/DB/app/browser/runtime remain explicitly unvalidated。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | Spec §6.1；exact release tree | current technical placement preserved | None |
| `MC-REUSE-001` | Applicable | PASS | §4.7 ledger | JDK/existing parser/Flowable reused | None |
| `MC-DEP-001` | Applicable | PASS | POM and exact six paths | no dependency added | None |
| `MC-NAME-001` | Applicable | PASS | File 1/2 inventory | utility retained；new test semantic | None |
| `MC-VALID-001` | Not applicable | N/A | no layer/DTO handoff | technical byte validation only | None |
| `MC-MODEL-001` | Not applicable | N/A | no POJO/entity/carrier | modeling rule absent | None |
| `MC-CONVERT-001` | Not applicable | N/A | no project mapping；upstream converter test-only | mapping rule absent | None |
| `MC-LOG-001` | Not applicable | N/A | no business class/log | logging absent | None |
| `MC-BEAN-001` | Not applicable | N/A | no Spring Bean/DI | DI absent | None |
| `MC-UTIL-001` | Applicable | PASS | File 1 imports | JDK-only utility | None |
| `MC-JSON-001` | Not applicable | N/A | no JSON contract | JSON absent | None |
| `MC-TIME-001` | Not applicable | N/A | §4.9 | time absent | None |
| `MC-CONFIG-001` | Not applicable | N/A | no config path/key | profile parity unchanged | None |
| `MC-PATTERN-001` | Not applicable | N/A | Simple fixed selector | no complex business flow | None |
| `MC-SCOPE-001` | Applicable | PASS | §5/Step exact six | no extra surface | None |
| `MC-TEST-001` | Applicable | PASS | §7/§8 RED/GREEN/regression | future gates exact and bounded | Execute after GO |
| `MC-BLOCKER-001` | Applicable | PASS | §11/all rows | no open design blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

No POJO/value/enum、MP/persistence、Bean Validation、business uniqueness/DDL、Command/Query/Event/outbox work。Rule 11 is Applicable PASS under user's latest explicit instruction：preserve existing technical Process Server release package and do not introduce business layers、ORM、CRUD or DDL。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/catalog generation、manifest、SQL journal or dependency introduction。Existing Flowable 8 artifacts and JDK APIs are sufficient。
