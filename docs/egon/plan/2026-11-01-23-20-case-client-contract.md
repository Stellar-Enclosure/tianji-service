# 固定 CMMN Case Java 客户端合同实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-01-23-20-case-client-contract.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 23:20 Asia/Shanghai` |
| Updated | `2026-11-01 23:20 Asia/Shanghai` |
| Owner | `Root source-grounded design review；原用户继续范围授权，完成后审查` |
| Repository | `tianji-service` |
| Scope | `Step 35；C orchestration package；exact 15 CREATE paths` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Case Java contract slice` |
| Baseline Revision | `main@59c5fc96976d23d98f6fc41da6f39464f030851d；检查时 clean` |
| Implements Spec | [固定 CMMN Case Java 客户端合同](../spec/2026-11-01-23-20-case-client-contract.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-01；SHA-256 ccb79ef2c083c40237af4df86ecff64a3d07ce0f8c0e71052d567b5c9217b62f` |
| Effective Specs | [Case client contract](../spec/2026-11-01-23-20-case-client-contract.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

一个 Step 创建 11 个 public Java contract types、`CaseContractTest`、Spec/Plan/Evidence。先建立未提交的 compile-ready local worktree state：四 enums 和 CaseClient 已是 final contract，六 records 暂为 raw compact constructors；focused test 的 negative/copy assertions 建立 RED。GREEN 在六 records 增加 frozen validation/copy invariants，并为 Start/Act/Result 增加下述脱敏 summaries；随后运行 focused + three existing core contract selectors、strict docs、exact15 scope，写 Evidence 后一个 commit。

原用户继续蓝图的授权覆盖本实现；Root full-read/strict/hash GO 后实施，完成后由用户审查。Candidate 11 test methods/140 invalid result pairs are unexecuted design inventory；actual counts only in Evidence。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Case contract Spec](../spec/2026-11-01-23-20-case-client-contract.md)
- Status: `Review`
- Revision: timestamp/baseline above
- Approval evidence: original continuation authority；Root strict/full-read/hash GO before code；no fake Accepted/new approval loop。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Case contract](../spec/2026-11-01-23-20-case-client-contract.md) | Review | all | sole implementation design |

### 2.3 Superseded or excluded content

None。Step34 is context only。No HTTP/client/server/permission/DB/release-enum implementation。

## 3. Effective Requirements and Acceptance

| Requirement | Source | Statement | Acceptance | Impact |
| --- | --- | --- | --- | --- |
| `REQ-201` | Spec §4 | exact 11 public types/methods/component order | reflection exact | all production files/test |
| `REQ-202` | Spec §4 | reuse checks；no identity/variables/raw fields | invalid/reflection tests | command/query records |
| `REQ-203` | Spec §4 | plan-item action bounds/compatibility | state-compatible subset cases | PlanItemDTO/enums |
| `REQ-204` | Spec §4 | Case list/status/time/historical rules | bounded/duplicate/sparse tests | CaseDTO |
| `REQ-205` | Spec §4 | exact result transitions/distinct versions | valid + 140 invalid pairs | OperationResult |
| `REQ-206` | Spec §4 | immutable copies/records | mutation tests | records |
| `REQ-207` | Spec §6.3 | Instant UTC/status correlation/no chronology | time tests | CaseDTO/result |
| `REQ-208` | Spec §14 | reflection/toString/negative safety | focused tests | all types |
| `REQ-209` | Spec §8/16 | exact15 all CREATE/no wider change | scope/core regression | all |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Create enums before dependent records；create raw records before interface/test；run RED after File12。Then revisit Files5–10 uncommitted to add compact-constructor checks/copies and the three declared safe toString summaries。Docs/evidence close the same Step。No knowingly incomplete commit。

### 4.2 Test-first strategy

| Behavior | RED | GREEN |
| --- | --- | --- |
| field/type vocabulary | exact enums/interface/raw records compile | unchanged final vocabulary |
| validation/copies | invalid/copy assertions fail on raw constructors | compact constructors reuse checks/List.copyOf |
| compatibility | existing core tests baseline | final four-class selector exit0 |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | Parallel | Must not overlap | Reason |
| --- | --- | --- | --- | --- |
| Step1 | Root GO | None | exact15 paths | one public API slice |

### 4.4 Commit boundaries

One commit after GREEN/evidence: `feat(contract): define fixed CMMN case operations`。No raw/scaffold/doc-only commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Element | Necessity | Evidence | Alternative | Cost | Decision |
| --- | --- | --- | --- | --- | --- |
| 11 public types | Spec §7–10 | no Case contract | maps/native types unsafe | additive Java API | Implement |
| CaseContractTest | Spec §14 | invariant matrix | no proof otherwise | test-only | Implement |
| HTTP/server/DB | Spec non-goals | no current need | later steps | would expand 4 surfaces | Do not implement |

### 4.6 Change-unit Dependency Matrix

| Unit | Requirements | RED point | Prerequisite | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| vocab/interface | 201 | reflection | enums/records | exact API | future impl | 1 |
| constructors | 202–207 | negative/copy tests | raw compile | valid values | interface | 1 |
| evidence | 208–209 | focused/core/static | GREEN | commit proof | Root/user | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | existing C orchestration | preserve sibling public types | no layer/Bean | ARCH/SCOPE |
| Reuse | OrchestrationChecks/JDK records | reuse | no dependency | REUSE/DEP |
| Model | current immutable records | records/plain enums | no mapper | NAME/MODEL |
| Time | current Instant DTOs | explicit UTC semantics | no Date/zone | TIME |
| Pattern | Simple finite contracts | none | direct constructors | PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| checks | OrchestrationChecks | current C source | exact fit | reuse | None | 1/REUSE |
| immutable list | JDK List.copyOf | current records | exact fit | reuse | None | 1/MODEL |
| time | Instant | Process DTO/receipt | exact fit | reuse | None | 1/TIME |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/8 | exact roles | Files1–12 | semantic suffixes | reflection | 1 | PASS |
| Rule 2 | §6.2 | no Spring layer | Files5–10 | compact constructor current style | negatives | 1 | N/A |
| Rule 3 | §6.2/10 | immutable contracts | Files5–10 | records/List.copyOf | copies | 1 | PASS |
| Rule 4 | §6.2 | no Bean/business class | none | no DI/log | scope | 1 | N/A |
| Rule 5 | §6.2 | JDK/checks | Files1–10 | no utility dependency | POM/import | 1 | PASS |
| Rule 6 | §6.2 | no JSON codec | Files1–4 | no Jackson | scope | 1 | N/A |
| Rule 7 | §6.2 | no config | none | no keys | scope | 1 | N/A |
| Rule 9 | §13 | Simple | all | no pattern | review | 1 | N/A |
| Rule 10 | §6.3 | Instant fields | Files9–10 | no java.util time | time tests | 1 | PASS |
| Rule 11 | §6.1/8 | exact C package | all15 | no layer/ORM/DDL | path gate | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-201 | `CaseInstanceDTO.submittedAt`, `CaseOperationResult.occurredAt` | Occurred instant | `Instant` | Not persisted | Internal | Not applicable: no calendar computation | Not applicable: no frontend | no local-zone/DST conversion | caller precision preserved；no Clock generation | existing Process DTO/receipt；wire/store deferred | `Step 1; TEST-204, TEST-205, TEST-207` |
| TIME-202 | `CaseInstanceDTO.finishedAt` | Occurred instant | nullable `Instant` | Not persisted | Internal | Not applicable: status correlation only | Not applicable: no frontend | ACTIVE null；terminal nonnull；no chronology rule | caller precision preserved；no Clock generation | existing no-ordering convention；wire/store deferred | `Step 1; TEST-204` |

## 5. Change File Tree

```text
contract orchestration main: CaseClient + 6 records + 4 enums                     CREATE ×11
contract orchestration test: CaseContractTest                                    CREATE
docs/egon/spec/2026-11-01-23-20-case-client-contract.md                          CREATE
docs/egon/plan/2026-11-01-23-20-case-client-contract.md                          CREATE
docs/egon/evidence/2026-11-01-case-client-contract.md                            CREATE
```

| Operation | Path group | Final symbols | Step | Requirements | Validation |
| --- | --- | --- | --- | --- | --- |
| CREATE | 11 exact main Java paths named in File1–11 | public contract types | 1 | 201–208 | focused compile/test |
| CREATE | `.../CaseContractTest.java` | 11 intended methods/matrix | 1 | 201–209 | focused RED/GREEN |
| CREATE | Spec/Plan/Evidence exact paths | Review design/actual proof | 1 | 209 | strict/static |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- `main@59c5fc96976d23d98f6fc41da6f39464f030851d` clean at inspection。
- Shared workspace rechecked before edits；no existing file edit/stage/revert。
- Exact 15 all CREATE paths；POM and every existing contract immutable。

### 6.2 Build, test, and environment prerequisites

JDK25、source reactor、`/tmp/tianji-compat-fix/settings.xml`；no app/DB/network。

### 6.3 Immutable constraints and approved decisions

No transport/codec/server/permission/DB/release enum。No tenant/actor/roles/variables/raw commands。No chronology check。Terminal history may be sparse/nonterminal but actions empty。

### 6.4 Plan Clarifications

| ID | Inference | Evidence | Semantics unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-201` | Files5–10 have raw RED then final GREEN phase | explicit test-first instruction | one uncommitted Step | capture RED first |
| `PLAN-CLAR-202` | List order retained, compatibility uses membership | frozen report/current List style | no codec exists | later codec documents order |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 定义固定 CMMN Case Java operations 与不可变快照

- Requirements: `REQ-201, REQ-202, REQ-203, REQ-204, REQ-205, REQ-206, REQ-207, REQ-208, REQ-209`
- Dependencies: Root strict/full-read/hash GO。
- Baseline state: C has no Case types；existing tests pass。
- Observable outcome: exact Java API constructs only valid bounded snapshots/receipts and rejects invalid matrices。
- End state: exact15 one commit；no implementation/transport/persistence。
- Test-first gate: `Required — exact enums/interface and raw record constructors compile first；negative/copy tests RED；then compact constructors GREEN.`
- Manual Checks: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemActionEnum.java`
- Purpose: finite operation vocabulary.
- Symbols: public enum `DISABLE, ENABLE, START, TERMINATE`.
- Repository evidence: plain sibling enums serialize by name when codec later exists.
- Dependencies and consumers: Files7/8/10/test.
- Why now: compile prerequisite.
- Contract/signature changes: exact four values, no methods/annotations.
- Input/output and state mapping: enum identity only.
- Error and edge behavior: unknown values impossible in Java; JSON deferred.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 11`.
- Implementation pseudocode:
```java
public enum CasePlanItemActionEnum {
    DISABLE, ENABLE, START, TERMINATE
}
// no code/ordinal/Jackson behavior
// CasePlanItemDTO and CaseOperationResult own compatibility checks
```
- Verification contribution: reflection/exact enum test.
- After this file: action vocabulary compiles.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceStatusEnum.java`
- Purpose: finite Case lifecycle projection.
- Symbols: `ACTIVE, COMPLETED, TERMINATED`.
- Repository evidence: Process status sibling.
- Dependencies and consumers: CaseInstanceDTO/test.
- Why now: DTO prerequisite.
- Contract/signature changes: exact three values.
- Input/output and state mapping: status identity only.
- Error and edge behavior: terminal correlation enforced in DTO, not enum.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Implementation pseudocode:
```java
public enum CaseInstanceStatusEnum {
    ACTIVE, COMPLETED, TERMINATED
}
// constructor rules remain in CaseInstanceDTO
// no cancellation/suspension value is invented in Step35
```
- Verification contribution: enum/reflection/status tests.
- After this file: Case status compiles.

#### File 3 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemStateEnum.java`
- Purpose: finite plan-item snapshot states.
- Symbols: `AVAILABLE, ENABLED, DISABLED, ACTIVE, COMPLETED, TERMINATED`.
- Repository evidence: frozen report/native-neutral projection.
- Dependencies and consumers: PlanItemDTO/OperationResult/test.
- Why now: records need state type.
- Contract/signature changes: exact six values.
- Input/output and state mapping: state identity only.
- Error and edge behavior: compatibility in records.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Implementation pseudocode:
```java
public enum CasePlanItemStateEnum {
    AVAILABLE, ENABLED, DISABLED, ACTIVE, COMPLETED, TERMINATED
}
// no engine-native type exposure
```
- Verification contribution: complete state/action matrix.
- After this file: state vocabulary compiles.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemKindEnum.java`
- Purpose: distinguish actionable task from milestone.
- Symbols: `HUMAN_TASK, MILESTONE`.
- Repository evidence: frozen contract design.
- Dependencies and consumers: PlanItemDTO/test.
- Why now: DTO prerequisite.
- Contract/signature changes: exact two values.
- Input/output and state mapping: kind identity only.
- Error and edge behavior: milestone action prohibition in DTO.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Implementation pseudocode:
```java
public enum CasePlanItemKindEnum {
    HUMAN_TASK, MILESTONE
}
// no additional extensibility value
// CasePlanItemDTO enforces milestone actions are always empty
```
- Verification contribution: milestone matrix.
- After this file: kind compiles.

#### File 5 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/StartCaseCommand.java`
- Purpose: release-bound Case start intent without identity/variables.
- Symbols: record exact five components.
- Repository evidence: StartProcessCommand checks/style.
- Dependencies and consumers: OrchestrationChecks/CaseClient/test.
- Why now: interface input prerequisite.
- Contract/signature changes: raw RED constructor then final compact checks.
- Input/output and state mapping: definition/release/business/command/correlation preserved.
- Error and edge behavior: null/invalid checks; no map copy needed.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 11`.
- Implementation pseudocode:
```java
public record StartCaseCommand(String definitionKey, String releaseVersion, String businessKey,
                               String commandId, String correlationId) {
  public StartCaseCommand { definitionKey=definition(...); releaseVersion=release(...);
    businessKey=id(...,255); commandId=command(...); correlationId=command(...); }
}
```
- Safe summary: Add fixed redacted toString for StartCaseCommand; expose no command/business refs.
- Verification contribution: valid/invalid/reflection tests.
- After this file: start command GREEN after final phase.

#### File 6 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceQuery.java`
- Purpose: bounded Case lookup key.
- Symbols: record `caseInstanceId`.
- Repository evidence: GetProcessInstanceQuery/id check.
- Dependencies and consumers: OrchestrationChecks/CaseClient/test.
- Why now: get method prerequisite.
- Contract/signature changes: raw then `id(255)` compact check.
- Input/output and state mapping: preserve ID only.
- Error and edge behavior: null/blank/control/oversize reject.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 11`.
- Implementation pseudocode:
```java
public record CaseInstanceQuery(String caseInstanceId) {
  public CaseInstanceQuery {
    caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
  }
}
```
- Verification contribution: query negative/reflection test.
- After this file: query GREEN after final phase.

#### File 7 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ActOnPlanItemCommand.java`
- Purpose: version-bound finite action intent.
- Symbols: five exact components.
- Repository evidence: HumanTask commands/checks.
- Dependencies and consumers: Action enum/checks/CaseClient/test.
- Why now: operation input prerequisite.
- Contract/signature changes: raw then checks/non-null action.
- Input/output and state mapping: identifiers/version/action only.
- Error and edge behavior: invalid hash/action rejected; no actor/raw command.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 11`.
- Implementation pseudocode:
```java
public record ActOnPlanItemCommand(String commandId, String caseInstanceId,
 String planItemInstanceId, String expectedVersion, CasePlanItemActionEnum action) {
  public ActOnPlanItemCommand { commandId=command(...); caseInstanceId=id(...,255);
    planItemInstanceId=id(...,255); expectedVersion=hash(...); action=requireNonNull(action); }
}
```
- Safe summary: Add fixed redacted toString for ActOnPlanItemCommand; expose no command/version.
- Verification contribution: command/reflection/forbidden fields.
- After this file: action command GREEN after final phase.

#### File 8 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemDTO.java`
- Purpose: immutable bounded plan-item snapshot and advertised actions.
- Symbols: six exact components + compact compatibility check.
- Repository evidence: DTO records/List.copyOf conventions.
- Dependencies and consumers: enums/checks/CaseInstanceDTO/test.
- Why now: Case DTO prerequisite.
- Contract/signature changes: raw RED then final checks/copy.
- Input/output and state mapping: IDs/kind/state/version/actions preserved in order.
- Error and edge behavior: ≤4 unique; compatible subset; milestone empty.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 11`.
- Implementation pseudocode:
```java
validate ids/definition/hash and non-null kind/state/actions; actions = List.copyOf(actions);
reject size>4 or duplicate actions; derive allowed set by state:
ENABLED->{DISABLE,START}, DISABLED->{ENABLE}, ACTIVE->{TERMINATE}, others->empty;
reject MILESTONE nonempty or any action outside allowed;
```
- Verification contribution: state-compatible subset cases/copy tests.
- After this file: plan item DTO GREEN after final phase.

#### File 9 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceDTO.java`
- Purpose: bounded current/historical Case projection.
- Symbols: nine exact components + status/list checks.
- Repository evidence: ProcessInstanceDTO/records.
- Dependencies and consumers: status/PlanItemDTO/checks/Instant/CaseClient.
- Why now: interface output prerequisite.
- Contract/signature changes: raw then final compact constructor.
- Input/output and state mapping: refs/status/times/list；no ordering normalization.
- Error and edge behavior: ≤16 unique item+definition keys；terminal finishedAt/action rules；sparse allowed.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 5, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
validate case/definition/release/business/correlation; require status/submittedAt; planItems=List.copyOf(...);
require ACTIVE == (finishedAt==null); terminal statuses require finishedAt;
reject >16/null/duplicate planItemInstanceId or planItemDefinitionKey;
if terminal reject any item with nonempty availableActions; do not require terminal item states or time ordering;
```
- Verification contribution: bounded/sparse/history/time/copy tests.
- After this file: Case DTO GREEN after final phase.

#### File 10 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseOperationResult.java`
- Purpose: immutable exact action transition receipt.
- Symbols: 12 components + transition/version checks.
- Repository evidence: HumanTaskActionReceiptDTO style.
- Dependencies and consumers: enums/checks/Instant/CaseClient/test.
- Why now: operation output prerequisite.
- Contract/signature changes: raw then final compact constructor.
- Input/output and state mapping: command/scope/definition/release/action/states/versions/time.
- Error and edge behavior: exact 4 transitions; hashes valid and different; occurredAt required.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 5, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
validate command/case/item ids, definition keys, release and non-null action/states/occurredAt;
previousVersion=hash(previousVersion); resultingVersion=hash(resultingVersion); require !equal;
require action/state tuple exactly one of DISABLE E->D, ENABLE D->E, START E->A, TERMINATE A->T;
otherwise throw OrchestrationChecks.invalid(); no chronology or engine call;
```
- Safe summary: Add safe toString showing only case/item/action; omit both versions and command id.
- Verification contribution: 4 valid + 140 invalid pair matrix/time/version tests.
- After this file: operation result GREEN after final phase.

#### File 11 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseClient.java`
- Purpose: expose exact three engine-neutral Java operations.
- Symbols: `start`, `get`, `actOnPlanItem`.
- Repository evidence: ProcessClient interface style.
- Dependencies and consumers: Files5–10；future implementation.
- Why now: all signature types compile.
- Contract/signature changes: new interface only, no constants/transport.
- Input/output and state mapping: typed method signatures.
- Error and edge behavior: no default implementation/retry.
- Standards impact: `MC-ARCH-001, MC-NAME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Implementation pseudocode:
```java
public interface CaseClient {
  CaseInstanceDTO start(StartCaseCommand command);
  CaseInstanceDTO get(CaseInstanceQuery query);
  CaseOperationResult actOnPlanItem(ActOnPlanItemCommand command);
}
```
- Verification contribution: reflection/exact method test.
- After this file: complete Java API compiles.

#### File 12 — `CREATE stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/CaseContractTest.java`
- Purpose: establish RED/GREEN for all field/invariant/copy/time/security contracts.
- Symbols: 11 intended test methods with an independent golden transition-tuple matrix.
- Repository evidence: current contract test style.
- Dependencies and consumers: Files1–11/JUnit.
- Why now: raw records establish validation/copy RED.
- Contract/signature changes: test only.
- Input/output and state mapping: constructors/reflection → values/errors/mutation assertions.
- Error and edge behavior: 140 invalid result pairs, list bounds/duplicates, forbidden fields, sparse terminal history.
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
assert exact CaseClient methods, record component order and enum values; reflect no tenant/actor/variables/raw fields;
construct valid commands/snapshots/results; mutate source lists and assert defensive/unmodifiable values;
check state-compatible action subsets; enumerate action×previous×result states against independent literal golden tuples; assert only Spec combinations pass;
assert terminal/sparse/time/no-chronology/toString boundaries and established scalar/invariant IllegalArgumentException versus required-reference NullPointerException behavior;
```
- Verification contribution: focused RED/GREEN `TEST-201–206`.
- After this file: RED under raw constructors, GREEN after Files5–10 final phase.

#### File 13 — `CREATE docs/egon/spec/2026-11-01-23-20-case-client-contract.md`
- Purpose: persist V9 Review design.
- Symbols: REQ-201–209, INTERNAL-201–203, TEST-201–207, 17 MC/10 Rules.
- Repository evidence: baseline/API report/blueprint.
- Dependencies and consumers: Root/Plan.
- Why now: design gate before GO.
- Contract/signature changes: docs only.
- Input/output and state mapping: evidence to exact contract.
- Error and edge behavior: strict/drift blocks; Review only.
- Standards impact: all 17 `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```text
Copy reviewed V9 draft to exact path; resolve blueprint/related links.
Run strict validator and full-read exact fields/time/15 paths/17 MC/10 Rules.
Record final SHA for Evidence; retain Review status and no Accepted claim.
```
- Verification contribution: strict Spec gate.
- After this file: reviewable design exists.

#### File 14 — `CREATE docs/egon/plan/2026-11-01-23-20-case-client-contract.md`
- Purpose: persist this V5 one-Step contract.
- Symbols: exact15/file order/RED-GREEN/commands/checks.
- Repository evidence: File13/current scripts/tests.
- Dependencies and consumers: Root/executor.
- Why now: execution gate.
- Contract/signature changes: docs only.
- Input/output and state mapping: requirements to files/evidence.
- Error and edge behavior: strict/link failure blocks.
- Standards impact: all 17 `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```text
Copy reviewed V5 draft after Spec exists; run strict link/coverage validation.
Full-read raw RED/final GREEN, exact15, time rules, core selectors and one commit.
Record SHA; do not implement as Plan side effect.
```
- Verification contribution: strict Plan gate.
- After this file: Root may GO.

#### File 15 — `CREATE docs/egon/evidence/2026-11-01-case-client-contract.md`
- Purpose: record actual RED/GREEN/core/static/commit and untested boundaries.
- Symbols: baseline/hashes/logs/counts/path inventory/MC/Rules/commit lookup.
- Repository evidence: actual outputs only.
- Dependencies and consumers: Files1–14 final.
- Why now: last; no fabricated results.
- Contract/signature changes: docs only.
- Input/output and state mapping: logs/diff/hashes to claims.
- Error and edge behavior: actual counts only; no HTTP/server/DB claim.
- Standards impact: `MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```markdown
Record baseline, final Spec/Plan hashes, actual raw RED and GREEN/core selector results.
Record exact15 all CREATE and unchanged POM/existing sources; record reflection/time/matrix/copy evidence.
Record introducing commit via post-commit git log command and state no HTTP/server/DB/runtime/IAM/codec validation.
```
- Verification contribution: closes scope/test/blocker.
- After this file: evidence-complete one commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: §8 focused/core/strict/static gates.
- Expected result: RED failures only missing validation/copy；GREEN/core exit0；exact15 all CREATE。
- Failure returns to: owning record/test/doc or Spec on material change.
- Completion criteria: REQ-201–209 actual evidence、17 MC closed、one commit、runtime boundary explicit。
- Rollback: revert one additive commit before consumers；no data rollback。
- Commit paths: `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseClient.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/StartCaseCommand.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceQuery.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ActOnPlanItemCommand.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceDTO.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemDTO.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseOperationResult.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemActionEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CaseInstanceStatusEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemStateEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CasePlanItemKindEnum.java`, `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/CaseContractTest.java`, `docs/egon/spec/2026-11-01-23-20-case-client-contract.md`, `docs/egon/plan/2026-11-01-23-20-case-client-contract.md`, `docs/egon/evidence/2026-11-01-case-client-contract.md`。
- Commit: `feat(contract): define fixed CMMN case operations`

## 8. Test, Validation, and Quality Gates

Future execution only；actual counts Evidence-only。

Root runs the finite source-reactor command after GO:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false -Dtest=CaseContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

Focused selector: `CaseContractTest`。Final selector: `CaseContractTest,OrchestrationContractTest,HumanTaskContractTest,ApprovalContractTest`。

| Gate | Working directory | Command/method | Scope | Expected | Failure return | Boundary |
| --- | --- | --- | --- | --- | --- | --- |
| RED | tianji-service | source reactor with focused selector/raw constructors | new contract | validation/copy negatives fail；valid/reflection compile | Files5–12 | module |
| GREEN | tianji-service | same focused selector final constructors | new contract | exit0；actual counts recorded | Files5–12 | module |
| Core regression | tianji-service | source reactor final selector | C core | exit0 | owning file | no runtime |
| Spec strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py tianji-service/docs/egon/spec/2026-11-01-23-20-case-client-contract.md --strict` | V9 | PASS | File13 | docs |
| Plan strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-23-20-case-client-contract.md --strict` | V5 | PASS | File14 | docs |
| Scope | tianji-service | status/diff-check/baseline path hashes | exact15 | no existing/POM extra | owner/stop | static |
| Live | user-controlled | not authorized | transport/server/DB | not run | later | unvalidated |

No scratch harness needed；existing source reactor/JUnit/static reflection suffice。

## 9. Migration, Compatibility, Rollout, and Rollback

Additive Java source only；no migration/config/permissions/dependency/wire rollout。Rollback removes one commit before implementation consumers exist。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates | Evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-201` | §4/9 | 1 | 1–12 | reflection | exact API |
| `REQ-202` | §4/10 | 1 | 5–7/12 | negatives | no forbidden fields |
| `REQ-203` | §4/10 | 1 | 1/3/4/8/12 | matrix | subsets |
| `REQ-204` | §4/10 | 1 | 2/8/9/12 | DTO tests | bounds/history |
| `REQ-205` | §4/10 | 1 | 1/3/10/12 | 4+140 matrix | transitions |
| `REQ-206` | §4/10 | 1 | 5–10/12 | copies | immutable |
| `REQ-207` | §6.3 | 1 | 9/10/12 | time | Instant/status |
| `REQ-208` | §14/15 | 1 | 1–12 | reflection/toString | safety |
| `REQ-209` | §8/16 | 1 | all15 | scope/core | additive only |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Files | Evidence/action | Status |
| --- | --- | --- | --- | --- |
| R1 raw constructors committed | 5–10 | two-phase Plan | closed by GREEN review |
| R2 additive public API drift | 1–11 | reflection exact | closed |
| R3 actions treated as authority | 7–10 | docs/no server | closed boundary |
| R4 shared workspace conflict | all | recheck/no revert | executor action |

No open design blocker。Original continuation authority + Root GO applies；Review remains non-Accepted lifecycle state。

## 12. Review and Acceptance

### 12.1 Original requirement fidelity
Exact fields/enums/transitions/time/exclusions/15 paths covered。

### 12.2 Spec consistency
No behavior or surface absent from Spec；clarifications are local。

### 12.3 Repository executability
Paths/checks/tests/POM/source reactor inspected；file order defines compile-ready RED。

### 12.4 Test and release completeness
Focused/core/reflection/time/matrix/strict/scope gates defined；counts deferred。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | exact C package | structure preserved | None |
| MC-REUSE-001 | Applicable | PASS | ledger | checks/JDK reused | None |
| MC-DEP-001 | Applicable | PASS | POM excluded | no dependency | None |
| MC-NAME-001 | Applicable | PASS | 11 types | semantic names | None |
| MC-VALID-001 | Not applicable | N/A | no Spring layer | compact C constructors | None |
| MC-MODEL-001 | Applicable | PASS | six records/four enums | immutable contracts | None |
| MC-CONVERT-001 | Not applicable | N/A | no mapping | absent | None |
| MC-LOG-001 | Not applicable | N/A | no business/log | absent | None |
| MC-BEAN-001 | Not applicable | N/A | no Bean/DI | absent | None |
| MC-UTIL-001 | Applicable | PASS | JDK/checks | allowlist | None |
| MC-JSON-001 | Not applicable | N/A | no codec | deferred | None |
| MC-TIME-001 | Applicable | PASS | §4.9/Files9–10 | Instant semantics | None |
| MC-CONFIG-001 | Not applicable | N/A | no config | unchanged | None |
| MC-PATTERN-001 | Not applicable | N/A | Simple | no pattern | None |
| MC-SCOPE-001 | Applicable | PASS | exact15 | all CREATE | None |
| MC-TEST-001 | Applicable | PASS | §7/8 | gates exact | Execute after GO |
| MC-BLOCKER-001 | Applicable | PASS | §11/all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

Records/enums are pure immutable C contracts，not PO/ORM/business Bean；no mapping、persistence、DDL、CQE bus/Event/outbox。Command/Query suffixes express intent only。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/catalog/generator/dependency。
