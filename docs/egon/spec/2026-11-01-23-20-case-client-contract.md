# 固定 CMMN Case Java 客户端合同

| Field | Value |
| --- | --- |
| Document | `2026-11-01-23-20-case-client-contract.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Feature` |
| Complexity | `Simple` |
| Complexity Drivers | `None — 一个纯 JDK Java 合同接口、六个不可变 record、四个有限 enum；无传输、服务或持久化` |
| Created | `2026-11-01 23:20 Asia/Shanghai` |
| Updated | `2026-11-01 23:20 Asia/Shanghai` |
| Owner | `Root source-grounded design review；原用户继续范围授权，完成后审查` |
| Repository | `tianji-service` |
| Scope | `stellar-enclosure-tianji-contract orchestration package；Step 35 engine-neutral Case Java contract` |
| Change Surface | `新增 CaseClient、六个 record、四个 enum 与一个 focused contract test；仅 C module` |
| Affected Chapters | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Step 35：固定 CMMN Case Java contract，HTTP/server later` |
| Baseline Revision | `tianji-service main@59c5fc96976d23d98f6fc41da6f39464f030851d；检查时 clean` |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§10.1–10.3, §11.1, §14.1, §14.4, §17.3, T3-08`；SHA-256 `3c087e7952d005f8a59f9b2ca3422d204eb14d27a91498bd8ad65140654d94f1` |
| Related Specs | [CMMN semantic validation](2026-11-01-22-55-cmmn-semantic-validation.md) |
| Related Plans | [Case client contract Plan](../plan/2026-11-01-23-20-case-client-contract.md) |

## 1. Summary

Step 35 在纯 JDK contract module 中定义原蓝图的 `CaseClient.start/get/actOnPlanItem`，固定 Case/plan-item 快照、有限操作、版本变化和 UTC Instant 语义。合同不接受 tenant、actor、role claims、任意 variables 或 raw engine command；这些事实以后由受信 server context 和正式 release metadata 决定。

本步骤只新增 11 个 public Java types 和 `CaseContractTest`。没有 HTTP/JSON codec、client implementation、server、route、permission、release enum/admission、数据库、POM 或依赖变化。

## 2. Background and Current State

### 2.1 Business and user context

业务应用需要一个不暴露 Flowable native type 的稳定 Java Case 合同，才能在后续步骤实现服务端授权、release binding 和原生 lifecycle。首版必须只描述固定模板可操作集合，不承诺任意 CMMN operation。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact path/symbol/decision | Observed fact | Design significance | Verification limit |
| --- | --- | --- | --- | --- | --- |
| `EVD-201` | Static repository | `ProcessClient`, `StartProcessCommand`, `ProcessInstanceDTO` | C module 使用 public interface + compact-constructor records/plain enums | Case 合同沿用当前风格 | 不代表 Case server exists |
| `EVD-202` | Static repository | `OrchestrationChecks` | JDK-only definition/release/id/command/hash checks and generic error | 新 records 复用，不加 validation dependency | 只验证合同值 |
| `EVD-203` | Static repository | `ProcessInstanceDTO` | `submittedAt/finishedAt` use `Instant`; terminal correlation without chronology check | Case time/status 对齐 | no persistence/wire proof |
| `EVD-204` | Static repository | `HumanTaskActionReceiptDTO` | previous/resulting hashes and occurredAt are explicit | Case operation result follows stable receipt style | no Case action execution |
| `EVD-205` | User/Root decision | frozen Step35 report | exact 11 public types/fields/enums/transitions/bounds | no remaining design choice | Review, not Accepted claim |
| `EVD-206` | Static repository | contract POM/tests | only JUnit test dependency; current core selectors named | no POM/dependency change | no HTTP/JSON runtime |

### 2.3 Problem statement and gap

Contract module has Process/Decision/HumanTask/Worker types but no `CaseClient` or Case snapshot/action vocabulary。Without a fixed contract, server/client work would invent fields, expose identity/engine state, or diverge on action transitions。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current call chain | Data read/written | External dependency | Consumers | Evidence |
| --- | --- | --- | --- | --- | --- |
| Process Java call | consumer → `ProcessClient` records | immutable in-memory values | none | future client/server | `EVD-201–204` |
| Case Java call | absent | none | none | later T3-08 steps | `EVD-205` |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 纯 contract module 的新增 engine-neutral types，不是生成业务项目、CRUD 或 archetype example replacement。

## 3. Goals and Non-goals

### 3.1 Goals

- 固定 `CaseClient` 三操作及 exact record component order/enum values。
- 构造时限制 IDs、版本、列表、actions、states、terminal time and operation transitions。
- 防御复制 lists，保留 sparse/historical Case snapshot，同时禁止 terminal Case advertise actions。
- 明确 Instant UTC semantics、nullable `finishedAt` 和 no chronology rule。

### 3.2 Non-goals

- 不新增 HTTP、JSON codec/Jackson annotations、OpenAPI、client/server implementation、routes、permissions/OAuth、DB/migration 或 POM dependency。
- 不改 `ProcessModelTypeEnum`、release contract/admission、CMMN validator/runtime 或任何 existing type。
- 不携带 tenant、actor/user、role IDs、variables、form、raw native command/service/transition name。
- 不决定 supplemental-only termination at contract layer；server later enforces fixed released policy and current native state。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Public Java Case contract | Affected | orchestration package lacks Case types | 11 new public types/3 methods | full fields/invariants/interface design | `§7, §8, §9, §10, §13, §14, §15, §16, §17, §18` |
| Contract tests | Affected | current core contract tests | new `CaseContractTest` | RED/GREEN/compatibility design | `§8, §14, §15, §16, §18` |
| Existing orchestration contracts | Unchanged | current Process/HumanTask/etc files | zero edit/behavior change | regression selectors only | `§14, §16` |
| HTTP/JSON/client/server/permissions | Not applicable | no target implementation/path | deferred | N/A | `§9, §16` |
| Database/config/frontend | Not applicable | no persistence/config/page scope | none | N/A | `§11, §12, §16` |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| `REQ-201` | 新增 exact 11 public types，`CaseClient` 只含 start/get/actOnPlanItem 和 frozen record component order | Must | reflection/method assertions exact；no extra type/field/method | Root Step35 |
| `REQ-202` | Commands/queries reuse definition/release/id(255)/command/hash checks，no tenant/actor/roles/variables/raw command | Must | invalid values reject；reflection proves forbidden fields absent | Root/API report |
| `REQ-203` | `CasePlanItemDTO` copies ≤4 unique actions and enforces kind/state-compatible subsets；MILESTONE empty | Must | state-compatible subset examples and milestone rejection cover advertised actions | Root/API report |
| `REQ-204` | `CaseInstanceDTO` copies ≤16 planItems with unique instance IDs and definition keys；ACTIVE iff no finishedAt，terminal iff nonnull | Must | duplicates/oversize/time mismatch/actions in terminal reject；empty/sparse/historical states accepted | Root/API report |
| `REQ-205` | `CaseOperationResult` enforces exact four transitions and distinct valid previous/resulting hashes | Must | DISABLE ENABLED→DISABLED；ENABLE DISABLED→ENABLED；START ENABLED→ACTIVE；TERMINATE ACTIVE→TERMINATED；all other pairs reject | Root/API report |
| `REQ-206` | All records are immutable JDK records/plain enums，lists copied；no mapper/DTO-PO conversion | Must | source list mutation has no effect；returned list unmodifiable | existing C style |
| `REQ-207` | submittedAt/occurredAt required Instant；finishedAt nullable only for ACTIVE；no default-zone or chronology rule | Must | UTC Instant semantics documented；constructor permits finishedAt before submittedAt as existing DTO does | Root + existing DTO |
| `REQ-208` | toString/reflection/negative tests ensure no secret identity/engine/variable surface and enum names are exact | Must | CaseContractTest covers components, copies, sparse history and invalid matrix | Root/API report |
| `REQ-209` | exact 15 CREATE paths；no existing edit/POM/dependency/transport/server/DB/release-enum change | Must | path diff and core contract regression selector | Root Step35 |

### 4.1 Scenario matrix

| Scenario | Actor/trigger | Preconditions | Main path | Alternative/failure path | State change | Observable result | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| start/get snapshot | Java consumer constructs command/query | trusted context later supplies identity | validate stable business refs → client method | invalid field/list/status rejects locally | none in C | immutable contract | `REQ-201/202/204/206/207` |
| plan-item action | consumer submits action/version | server later owns authorization/state | fixed action + expected version | illegal contract transition/result pair rejects | none in C | operation contract | `REQ-203/205` |
| historical terminal snapshot | consumer reads terminal Case | finishedAt present | allow sparse/nonterminal historical item states but no advertised actions | any advertised terminal action rejects | none | readable history | `REQ-204/207` |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| `ACTOR-201` | Business Java SDK consumer | start/get a Case without engine types | `CaseClient` | identity/tenant not caller fields | blueprint/Root |
| `ACTOR-202` | Authorized Case operator consumer | request one allowed plan-item action with version | `CaseClient` | server later validates actor/policy/state | blueprint/Root |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `UC-201` | Start governed Case | `ACTOR-201` | future server | call start | valid release/business refs | Case snapshot returned | invalid local value/server failure later | contract immutable | `REQ-201/202` | `INTERNAL-201` | `TEST-201/202` |
| `UC-202` | Get Case snapshot/history | `ACTOR-201` | future server | call get | valid caseInstanceId | bounded snapshot returned | not found/auth later | no C state | `REQ-201/204/207` | `INTERNAL-202` | `TEST-203/204` |
| `UC-203` | Act on plan item | `ACTOR-202` | future server | call act | valid command/action/version | transition receipt returned | stale/forbidden/native mismatch later | no C state | `REQ-201/203/205` | `INTERNAL-203` | `TEST-205–207` |

## 5. Constraints, Assumptions, and Decisions

### 5.1 Confirmed constraints

- Exactly 11 new public types and no other public/private production helper。
- C module remains JDK-only；all validation via compact constructors and existing `OrchestrationChecks`。
- Enum names are stable Java contract names but no JSON codec/annotation is introduced。
- Available actions are advertised possibilities, not authority；server revalidates release policy and native current state。

### 5.2 Small-gap assumptions

| ID | Inference | Repository evidence | Why locally reversible | Impact if wrong |
| --- | --- | --- | --- | --- |
| `ASM-201` | `CaseInstanceQuery` uses only `OrchestrationChecks.id(255)` | frozen API report；no HTTP path yet | compact-constructor detail | later HTTP adapter may add path-specific checks |
| `ASM-202` | action order in list is preserved while membership is validated as a set | current DTO List conventions | wire codec absent；copy is stable | later codec may document ordering |

### 5.3 Resolved decisions

| ID | Decision | Decision owner | Evidence and rationale | Requirements |
| --- | --- | --- | --- | --- |
| `DEC-201` | records/plain enums follow current C style | Root Review | pure immutable Java contract；no business entity/ORM | `REQ-201/206` |
| `DEC-202` | terminal snapshot may contain sparse/nonterminal historical states but zero actions | Root/API design | read projection is not live-state completion proof | `REQ-204` |
| `DEC-203` | contract exposes generic TERMINATE；server later restricts supplemental | Root/API design | C contract lacks release/native authority | `REQ-203/205` |

### 5.4 Open major decisions

None. 原用户 continuation authority covers implementation after Root full-read/strict/hash GO；Review status remains honest and no extra approval loop is invented。

## 6. Project Technology Context

| Concern | Current choice | Repository evidence | Constraint on design |
| --- | --- | --- | --- |
| Language | public Java records/interfaces/enums | current C package | JDK-only, no Spring annotations |
| Validation | compact constructors + OrchestrationChecks | existing records | invalid scalar/invariant: IllegalArgumentException；required references/lists: NullPointerException |
| Time | `java.time.Instant` | ProcessInstanceDTO/receipts | UTC occurred-instant semantics |
| Tests | JUnit Jupiter | contract POM/tests | focused + 3 core regression classes |

### 6.1 Java architecture profile and capability baseline

| Architecture profile | Archetype/template or base package | Exact evidence and verifier | Existing deviations | Design action |
| --- | --- | --- | --- | --- |
| Existing pure contract module, explicitly preserved by user/current repository style | `top.egon.cola.platform.tianji.contract.orchestration` | POM + existing public records/interfaces/tests | no business Bean/layer/ORM | Rule11 PASS：add sibling contract types only；no layer/module |

| Need | JDK candidate | Starter candidate | module candidate | Proven gap | Decision/dependency impact |
| --- | --- | --- | --- | --- | --- |
| identifiers/bounds | existing checks | none | OrchestrationChecks | none | reuse |
| immutable lists | List.copyOf/JDK | none | current record style | none | reuse |
| time | Instant | none | existing DTO style | none | reuse |

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | exact interface/Command/Query/DTO/Result/Enum roles | all names semantic | 11 types/test | reflection/compile | PASS |
| Rule 2 | No | no Spring/layer handoff | compact constructors preserve C boundary style | records | negative tests | N/A |
| Rule 3 | Yes | immutable public contract records | records + defensive lists；no mapping boundary | 6 records | copy tests | PASS |
| Rule 4 | No | no business/Spring Bean/DI | plain contract only | none | scope | N/A |
| Rule 5 | Yes | JDK + existing checks only | no utility/dependency | records | import/POM diff | PASS |
| Rule 6 | No | no JSON codec/annotation | plain enum names only | enums | reflection | N/A |
| Rule 7 | No | no config | no profile changes | none | scope | N/A |
| Rule 9 | No | Simple fixed contracts | no pattern | all | review | N/A |
| Rule 10 | Yes | Instant fields | UTC semantic contract，no `java.util.Date` | DTO/result | time tests/review | PASS |
| Rule 11 | Yes | existing contract package | no new layer/ORM/CRUD/DDL | all paths | path gate | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-201` | `CaseInstanceDTO.submittedAt`, `CaseOperationResult.occurredAt` | Occurred instant | `Instant` | Not persisted | Internal | Not applicable: no calendar rule | Not applicable: no frontend | no local-zone/DST conversion | preserve caller Instant precision；no Clock generation | aligns existing Process DTO/receipt；persistence/wire deferred | `TEST-204`, `TEST-207` |
| `TIME-202` | `CaseInstanceDTO.finishedAt` | Occurred instant | nullable `Instant` | Not persisted | Internal | Not applicable: status correlation only | Not applicable: no frontend | ACTIVE=null；COMPLETED/TERMINATED=nonnull；no chronology check | preserve caller precision；no Clock generation | matches existing no-ordering convention；persistence/wire deferred | `TEST-204` |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Inadequacy | Added cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| CaseClient | New | `REQ-201` | ProcessClient | wrong scope/types | 3 Java methods | Add |
| 6 records | New | `REQ-202–207` | maps/raw engine objects | unsafe/unversioned | 6 immutable values | Add |
| 4 enums | New | `REQ-203–205` | strings | unbounded/typos | finite vocabulary | Add |
| HTTP/client/server/DB | None | `REQ-209` | defer | not needed for contract proof | avoided | Remove |

| Path | Network calls | Client states | Server contracts/state | Failure points | Value |
| --- | --- | --- | --- | --- | --- |
| Current | 0 | none | no Case contract | consumers invent shapes | insufficient |
| Selected | 0 | immutable Java values | interface only | constructor rejection | stable handoff |

### 7.1 System Architecture Design

Simple library slice：future consumer depends on C `CaseClient` and records；implementation remains absent。Identity、tenant、authorization、release/native engine and persistence stay outside and cannot be supplied through contract fields。

#### 7.1.1 Boundary and responsibility table

| Component | Owns | Inputs/outputs | Allowed dependencies | Forbidden responsibility | Requirements |
| --- | --- | --- | --- | --- | --- |
| CaseClient | engine-neutral method surface | commands/query → DTO/result | C types only | HTTP/auth/engine | `REQ-201` |
| records/enums | value invariants | JDK values | OrchestrationChecks | persistence/permission | `REQ-202–207` |

### 7.2 High-Level Design

Compact constructors validate local shape only。Server later derives tenant/actor, resolves release/native IDs, checks authorization/current state and constructs trusted snapshots/results。C never exposes raw variables or engine commands。

### 7.3 Detailed Design

#### 7.3.1 Record inventory and invariants

| Record | Exact fields in order | Principal invariants |
| --- | --- | --- |
| StartCaseCommand | definitionKey, releaseVersion, businessKey, commandId, correlationId | existing definition/release/id/command checks |
| CaseInstanceQuery | caseInstanceId | id(255) |
| ActOnPlanItemCommand | commandId, caseInstanceId, planItemInstanceId, expectedVersion, action | command/id/hash/non-null action |
| CasePlanItemDTO | planItemInstanceId, planItemDefinitionKey, kind, state, version, availableActions | ids/kind/state/hash；≤4 copied unique compatible actions；milestone empty |
| CaseInstanceDTO | caseInstanceId, definitionKey, releaseVersion, businessKey, correlationId, status, submittedAt, finishedAt, planItems | ≤16 copied unique instance+definition keys；status/time correlation；terminal zero actions |
| CaseOperationResult | commandId, caseInstanceId, planItemInstanceId, planItemDefinitionKey, action, releaseVersion, definitionKey, previousState, previousVersion, resultingState, resultingVersion, occurredAt | exact transition；distinct hashes；required Instant |

#### 7.3.2 State/action compatibility

| Kind/state | Permitted advertised action subset |
| --- | --- |
| MILESTONE / any | empty |
| HUMAN_TASK AVAILABLE, COMPLETED, TERMINATED | empty |
| HUMAN_TASK ENABLED | subset of `DISABLE, START` |
| HUMAN_TASK DISABLED | subset of `ENABLE` |
| HUMAN_TASK ACTIVE | subset of `TERMINATE` |

OperationResult requires one exact pair: DISABLE `ENABLED→DISABLED`; ENABLE `DISABLED→ENABLED`; START `ENABLED→ACTIVE`; TERMINATE `ACTIVE→TERMINATED`。Both versions are lowercase 64-hex and unequal。

## 8. Package Structure and Code File Tree

### 8.1 Current relevant tree

```text
stellar-enclosure-tianji-contract/.../orchestration
├── ProcessClient.java
├── StartProcessCommand.java
├── ProcessInstanceDTO.java
├── HumanTaskActionReceiptDTO.java
└── OrchestrationChecks.java
```

### 8.2 Target tree

```text
.../orchestration/
├── CaseClient.java CREATE
├── StartCaseCommand.java CREATE
├── CaseInstanceQuery.java CREATE
├── ActOnPlanItemCommand.java CREATE
├── CaseInstanceDTO.java CREATE
├── CasePlanItemDTO.java CREATE
├── CaseOperationResult.java CREATE
├── CasePlanItemActionEnum.java CREATE
├── CaseInstanceStatusEnum.java CREATE
├── CasePlanItemStateEnum.java CREATE
└── CasePlanItemKindEnum.java CREATE
.../test/.../orchestration/CaseContractTest.java CREATE
```

### 8.3 Package and file responsibilities

All 12 Java paths above are CREATE under existing orchestration source/test packages。Spec、Plan、Evidence add three docs paths。No existing source or manifest is modified。

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Change/necessity | Purpose | Kind | Consumer | Owner | Symbol | Input | Output | Error model | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `INTERNAL-201` | New/Add | start Case | public Java | future SDK/server | C | `CaseClient.start(StartCaseCommand)` | command | CaseInstanceDTO | implementation later | `REQ-201/202` |
| `INTERNAL-202` | New/Add | get Case | public Java | future SDK/server | C | `CaseClient.get(CaseInstanceQuery)` | query | CaseInstanceDTO | implementation later | `REQ-201/204` |
| `INTERNAL-203` | New/Add | act plan item | public Java | future SDK/server | C | `CaseClient.actOnPlanItem(ActOnPlanItemCommand)` | command | CaseOperationResult | implementation later | `REQ-201/203/205` |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-201 — start

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New Java operation |
| Independent consumer goal | start one release-bound Case |
| Parameter ownership and derivation | consumer supplies business/release refs；server later derives identity/tenant/native ID |
| Direct/no-new-interface alternative | ProcessClient cannot represent Case lifecycle without conflating instance kinds and states |
| Caller use of result | receives a bounded Case snapshot for subsequent display or workflow decisions |
| Round trips and failure points | transport is unspecified；this Java interface adds no network call by itself |
| Verdict | Add exact method；no HTTP |

##### Identity and purpose
Engine-neutral Java start contract for one release-bound Case；it carries no caller identity or native engine object。

##### Request parameters
Exact `StartCaseCommand` fields；no variables/identity/roles。

##### Success response
Returns the bounded immutable `CaseInstanceDTO` snapshot supplied by a future authorized implementation。

##### Error responses
Constructor rejects invalid local values；server error contract deferred。

##### Interface logic for frontend and consumers
No implementation in Step35；future server performs authorization/idempotency/release resolution。

##### Compatibility and verification
New interface；reflection/focused tests prove exact method/components。

#### 9.2.2 INTERNAL-202 — get

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New Java query |
| Independent consumer goal | read bounded current/historical Case snapshot |
| Parameter ownership and derivation | caller knows caseInstanceId；tenant/visibility server-derived later |
| Direct/no-new-interface alternative | no existing query can return Case-specific plan item state/actions without changing Process semantics |
| Caller use of result | displays current or historical Case state and uses advertised actions as hints only |
| Round trips and failure points | transport remains unspecified；the Java query adds no network behavior in this Step |
| Verdict | Add exact method |

##### Identity and purpose
Read-only engine-neutral Java query identified solely by bounded `caseInstanceId`；tenant visibility remains server-owned。

##### Request parameters
One nonblank safe-text `caseInstanceId` bounded to 255 characters through `OrchestrationChecks.id`。

##### Success response
Bounded `CaseInstanceDTO`；terminal snapshots may be sparse historical projections。

##### Error responses
Local invalid query rejected；not-found/permission deferred。

##### Interface logic for frontend and consumers
No frontend or implementation；availableActions remain server-projected, not client authority。

##### Compatibility and verification
New interface；reflection and DTO matrix tests。

#### 9.2.3 INTERNAL-203 — actOnPlanItem

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | New Java command |
| Independent consumer goal | request one versioned finite plan-item action |
| Parameter ownership and derivation | caller supplies stable IDs/version/action；server derives actor/policy/native transition |
| Direct/no-new-interface alternative | raw engine command is forbidden and HumanTask command does not cover CMMN plan-item transitions |
| Caller use of result | observes exact previous/resulting state and changed opaque versions in a stable receipt |
| Round trips and failure points | transport unspecified；stale version, authorization and native-state failures belong to later implementation |
| Verdict | Add exact finite command/result |

##### Identity and purpose
Version-bound finite action intent；the action enum is a request vocabulary and never a permission grant。

##### Request parameters
command/case/planItem IDs，expected hash，one of four actions。

##### Success response
CaseOperationResult with exact transition and changed versions。

##### Error responses
Local invalid fields/result transition reject；server conflicts deferred。

##### Interface logic for frontend and consumers
No runtime action here；server later enforces supplemental-only terminate and current state。

##### Compatibility and verification
Complete transition matrix/reflection tests；no REST/OAS applies。

## 10. POJO and Data Model Design

Six public records are immutable technical transport values consistent with existing C contracts；four enums are finite stable vocabularies。No PO/BO/ORM or mapper exists。Compact constructors normalize/check/copy only；no business authorization。

| Field group | Java type / null/default | Exact rule |
| --- | --- | --- |
| definitionKey; planItemDefinitionKey | String, required, no default | existing definition: `[A-Za-z_][A-Za-z0-9_.-]{0,127}` |
| releaseVersion | String, required, no default | existing release: `[A-Za-z0-9][A-Za-z0-9_.-]{0,127}` |
| businessKey; caseInstanceId; planItemInstanceId | String, required, no default | existing id(255): nonblank, unchanged by trim, no control/NUL/unpaired surrogate; preserve Unicode |
| commandId; correlationId | String, required, no default | existing command: `[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}` |
| expectedVersion; version; previousVersion; resultingVersion | String, required, no default | existing hash: `[0-9a-f]{64}`; Result before/after hashes must differ |
| action; kind; state; status; previousState; resultingState | corresponding closed enum, required | Objects.requireNonNull; exact values below; state/action matrix §7.3.2 |
| availableActions | List<CasePlanItemActionEnum>, required, no null item | List.copyOf, ≤4 distinct values; state-compatible subset; milestone empty |
| planItems | List<CasePlanItemDTO>, required, no null item | List.copyOf, ≤16, unique instance IDs and definition keys; empty allowed; terminal items advertise no action |
| submittedAt; occurredAt | Instant, required, no default | passed absolute instant, preserved precision; no clock/zone/chronology generation |
| finishedAt | Instant, nullable only ACTIVE | terminal status requires value; no ordering relation to submittedAt is imposed |

Scalar/invariant failures reuse the existing generic OrchestrationChecks.invalid exception. Objects.requireNonNull and List.copyOf intentionally retain the existing JDK NullPointerException behavior for null references/lists/elements. No HTTP error mapping is promised in this Java-only Step.

Enum values：Action `DISABLE, ENABLE, START, TERMINATE`; Status `ACTIVE, COMPLETED, TERMINATED`; State `AVAILABLE, ENABLED, DISABLED, ACTIVE, COMPLETED, TERMINATED`; Kind `HUMAN_TASK, MILESTONE`。

## 11. Database Design

N/A. No persistence、table、migration、transaction or DDL。

## 12. Frontend Page Design

N/A. No route/page/JSON/HTTP client。

## 13. Design Patterns and Architecture Principles

No pattern。Simple finite contract invariants remain in compact constructors；factory/strategy/state-machine classes would be ceremonial。No mapping abstraction because no semantic object conversion exists。

## 14. Test Design

| ID | Level | Target | Scenario | Expected | Requirements |
| --- | --- | --- | --- | --- | --- |
| `TEST-201` | Unit | CaseClient/types | reflection exact methods/components/enums/forbidden fields | exact 11 public types | `REQ-201/202/208` |
| `TEST-202` | Unit | commands/query | valid/invalid IDs/version/action | current generic error | `REQ-202` |
| `TEST-203` | Unit | plan item DTO | state-compatible subset cases/list copies | only compatible subsets | `REQ-203/206` |
| `TEST-204` | Unit | Case DTO/time | ≤16 unique items、status/finishedAt、sparse terminal history | exact acceptance/rejection | `REQ-204/207` |
| `TEST-205` | Unit | operation result | four valid transitions + 140 invalid action/state pairs | exact matrix | `REQ-205` |
| `TEST-206` | Unit | immutability/toString | source mutation、unmodifiable lists、no sensitive/raw fields | safe values | `REQ-206/208` |
| `TEST-207` | Regression | existing C core | Orchestration/HumanTask/Approval tests | unchanged | `REQ-209` |

Compile-ready RED uses exact enums/interface and raw record constructors without validation/copies。New negative/copy tests fail while valid construction compiles；GREEN adds only compact-constructor invariants。Candidate inventory has 11 test methods and a 4×6×6 result matrix；actual counts belong only in Evidence。

## 15. Non-functional and Cross-cutting Design

- Security: no tenant/actor/role/variables/raw command fields；actions are not permission grants。
- Capacity: planItems≤16，availableActions≤4，IDs≤255/128 by reused checks。
- Immutability: List.copyOf and records；no mutable map payloads。
- Time: Instant only；no default zone、serialization or chronology inference。
- Privacy: Start/Act mutation summaries redact their fields；operation-result summary omits versions and shows only case/item/action。Other DTO/query record summaries follow existing C conventions; no form/variables exist。

## 16. Compatibility, Migration, Rollout, and Rollback

Additive source-only Java contract。No existing type/POM/config/schema changes。No transport compatibility claim until later codec/client work。Rollback removes exact 15 new paths before consumers exist。

## 17. Alternatives and Decisions

| Option | Elements | Advantage | Risk | Fit | Decision |
| --- | --- | --- | --- | --- | --- |
| A fixed records/enums | 11 types | explicit, bounded, current style | additive API | exact | Selected |
| raw Map/variables | fewer types | flexible | unsafe/unversioned | forbidden | Rejected |
| native Flowable types | no projection | direct | engine leak | forbidden | Rejected |
| REST now | adapters/docs/security | transport ready | premature 4+ surfaces | poor | Deferred |

## 18. Risks and Open Questions

| ID | Risk/question | Probability | Impact | Mitigation | Status |
| --- | --- | --- | --- | --- | --- |
| `RISK-201` | action advertised as authority | Medium | unauthorized operation | docs + future server revalidation | Closed by contract boundary |
| `RISK-202` | terminal history over-constrained | Low | unreadable old snapshot | allow sparse/nonterminal states, require no actions | Closed |
| `RISK-203` | Instant mistaken for implemented wire/storage | Medium | false guarantee | internal-only time contract | Closed |

## 19. Traceability Matrix

| Requirement | Use case | Affected area | Boundary | Contract/model | Tests | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| `REQ-201` | `UC-201–203` | §7–10 | no implementation | CaseClient/types | `TEST-201` | reflection |
| `REQ-202` | `UC-201/203` | §9/10 | identity server later | commands/query | `TEST-201/202` | field checks |
| `REQ-203` | `UC-203` | §10 | policy server later | PlanItemDTO/enums | `TEST-203` | matrix |
| `REQ-204` | `UC-202` | §10/15 | no persistence | CaseInstanceDTO | `TEST-204` | list/status |
| `REQ-205` | `UC-203` | §10 | no native execution | OperationResult | `TEST-205` | transitions |
| `REQ-206` | all | §10 | no mapper | records | `TEST-203/204/206` | copies |
| `REQ-207` | `UC-202/203` | §6.3/15 | no wire/store | Instant fields | `TEST-204/205` | time correlation |
| `REQ-208` | all | §14/15 | no raw fields | all | `TEST-201/206` | reflection/toString |
| `REQ-209` | all | §8/16 | existing sources unchanged | none | `TEST-207` + diff | exact15 |

## 20. Review and Acceptance

### 20.1 Original-request fidelity

All exact types、fields、enum values、bounds、transitions、time and exclusions map to REQ-201–209。

### 20.2 Repository and technical fidelity

Existing C records/checks/POM/core tests were inspected；no runtime result claimed。

### 20.3 Cross-section consistency

Three methods、six records、four enums、15 paths and test matrices agree across requirements/design/files/tests。

### 20.4 Relationship and effective-design review

Blueprint remains authority；Step34 is related context only and is not modified or implemented here。

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | §6.1 exact C package | current contract style preserved | None |
| `MC-REUSE-001` | Applicable | PASS | checks/JDK ledger | existing capabilities reused | None |
| `MC-DEP-001` | Applicable | PASS | POM excluded | no dependency | None |
| `MC-NAME-001` | Applicable | PASS | exact 11 type inventory | semantic suffixes | None |
| `MC-VALID-001` | Not applicable | N/A | pure JDK contract, no Spring layer | compact constructors match current C | None |
| `MC-MODEL-001` | Applicable | PASS | six immutable records/four enums | existing record convention | None |
| `MC-CONVERT-001` | Not applicable | N/A | no object mapping | no converter needed | None |
| `MC-LOG-001` | Not applicable | N/A | no business class/log | absent | None |
| `MC-BEAN-001` | Not applicable | N/A | no Bean/DI | absent | None |
| `MC-UTIL-001` | Applicable | PASS | JDK/existing checks | allowlist satisfied | None |
| `MC-JSON-001` | Not applicable | N/A | no JSON codec/annotations | deferred | None |
| `MC-TIME-001` | Applicable | PASS | §6.3 + Instant fields/tests | UTC occurred semantics explicit | None |
| `MC-CONFIG-001` | Not applicable | N/A | no config | unchanged | None |
| `MC-PATTERN-001` | Not applicable | N/A | Simple finite contracts | no business complexity | None |
| `MC-SCOPE-001` | Applicable | PASS | exact15 all CREATE | no existing edit/scope expansion | None |
| `MC-TEST-001` | Applicable | PASS | TEST-201–207 | RED/GREEN/core regression defined | Execute after GO |
| `MC-BLOCKER-001` | Applicable | PASS | §5.4/§18/all rows | no open blocker | None |

### 20.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

Public C records/enums are immutable transport values under existing repository convention；no PO/ORM、mapping、persistence、Bean Validation layer、DDL、Command/Event delivery or outbox。`StartCaseCommand`/`ActOnPlanItemCommand` are intent names only, not CQE bus/handler architecture。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/catalog、generator、manifest、SQL journal or dependency introduction。
