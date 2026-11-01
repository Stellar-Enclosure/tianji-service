# CMMN 1.1 安全 Schema 边界

| Field | Value |
| --- | --- |
| Document | `2026-11-01-22-35-cmmn-schema-boundary.md` |
| Template Version | `9` |
| Archetype Cleanup | `Not applicable` |
| Status | `Review` |
| Type | `Feature` |
| Complexity | `Simple` |
| Complexity Drivers | `None — 单一 package-private XML 安全解析入口、单一固定夹具、无运行态或持久化` |
| Created | `2026-11-01 22:35 Asia/Shanghai` |
| Updated | `2026-11-01 22:35 Asia/Shanghai` |
| Owner | `原用户已授权继续蓝图并分步提交；Root 负责派生设计、实现和验证；Review 不冒充另行 Accepted` |
| Repository | `tianji-service` |
| Scope | `stellar-enclosure-tianji-process-server release package；Step 33 前置安全解析切片` |
| Change Surface | `SafeModelXml 增加 package-private parseCmmn；新增一个 package-private 单测和一个正式中立 CMMN fixture；不接入发布或运行` |
| Affected Chapters | `§7, §8, §9, §13, §14, §15, §16, §17, §18` |
| Source Requirement | `原始 continuation goal 下 Root 划定的 Step 33：只建立 CMMN 1.1 schema/parser prerequisite` |
| Baseline Revision | `tianji-service main@5881106ca80a9bb109f2c85e596bd4943b715845；检查时 clean` |
| Amends | `None` |
| Supersedes | `None` |
| Depends On | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§2.2, §10.2–10.3, §17.3, T3-08`；SHA-256 `d32645482733236f6026b922853ca20a3484cb44ee311fcd1f52f28c802960f5` |
| Related Specs | `None` |
| Related Plans | [CMMN schema boundary implementation Plan](../plan/2026-11-01-22-35-cmmn-schema-boundary.md) |

## 1. Summary

当前 `SafeModelXml` 已为 BPMN/DMN 提供 512 KiB、严格 UTF-8、深度/元素/属性上限、禁 DTD/实体/外部 schema 及打包 XSD 白名单验证，但没有 CMMN 入口。Step 33 只在相同 release package 增加 `SafeModelXml.parseCmmn(byte[])`，复用原安全管线并对 Flowable 8 打包的 CMMN 1.1 XSD 做封闭解析。

本切片不承认、发布、部署或运行 CMMN，不修改 model enum、API、数据库、权限、配置或旧 BPMN/DMN 行为。一个中立夹具和一个固定 converter 测试仅证明：资源被打包、XSD 依赖封闭、fixture 结构可被固定 Flowable 8 converter 保留。

## 2. Background and Current State

### 2.1 Business and user context

T3-08 后续正式案件能力必须先有与 BPMN/DMN 同等级的输入安全边界。本步骤的直接用户是后续 release-validator 实现者和代码审查者；业务 Case 用户、HTTP 客户端和运行引擎均不进入本步骤。

### 2.2 Repository evidence

| Evidence ID | Classification | Exact path/symbol/decision/command | Observed fact | Design significance | Verification limit/freshness |
| --- | --- | --- | --- | --- | --- |
| `EVD-001` | Static repository | `.../release/SafeModelXml.java#parse(byte[],boolean)` | BPMN/DMN 共享 bounded UTF-8、XXE 禁止、DOM 与 XSD 验证，失败统一为无 cause 的 `IllegalArgumentException` | CMMN 必须复用此管线 | 静态源码，不证明运行发布 |
| `EVD-002` | Static repository | `SafeModelXml.Schemas` | BPMN/DMN schema 按内部 holder 惰性初始化 | 新 CMMN 不得使旧两条路径提前初始化 CMMN schema | 源码核对 holder 隔离；旧模型回归不声称覆盖缺失 CMMN jar 场景 |
| `EVD-003` | Static repository | `stellar-enclosure-tianji-process-server/pom.xml` | 已有 Flowable 8 CMMN starter/configurator；无须新增依赖 | 复用当前 classpath | 不证明生产 CMMN 已启用 |
| `EVD-004` | Static local artifact | `/Users/mario/maven/repository/org/flowable/flowable-cmmn-converter/8.0.0/flowable-cmmn-converter-8.0.0.jar` | 包含 `CMMN11.xsd`、`CMMN11CaseModel.xsd`、`CMMNDI11.xsd`、`DC.xsd`、`DI.xsd` 与 `CmmnXmlConverter` | 确定打包路径、白名单和 converter 名称 | 本地 artifact 是当前 baseline 证据，不替代构建产物检查 |
| `EVD-005` | Static repository | `src/test/resources/fixtures/baseline.cmmn` | 只有一个 HumanTask，不能覆盖正式模板结构 | 新增独立中立 fixture，不改 baseline | 不代表完整 T3-08 |
| `EVD-006` | User/Root decision | Step 33 scope | 只改 parser/test/fixture 和 Spec/Plan/Evidence 六路径；无 admission/release/deploy/API/DB | 固定停止边界 | Review 设计，不冒充 Accepted |
| `EVD-007` | Primary upstream reference | [Flowable 8 CMMN11.xsd](https://raw.githubusercontent.com/flowable/flowable-engine/flowable-8.0.0/modules/flowable-cmmn-converter/src/main/resources/org/flowable/impl/cmmn/parser/CMMN11.xsd), [CMMN11CaseModel.xsd](https://raw.githubusercontent.com/flowable/flowable-engine/flowable-8.0.0/modules/flowable-cmmn-converter/src/main/resources/org/flowable/impl/cmmn/parser/CMMN11CaseModel.xsd) | 固定 tag 的 schema 根包含 CaseModel 与 DI 依赖，CaseModel 定义 plan item/sentry/control 结构 | 支持 fixture/XSD 设计；本地 jar 仍是执行权威 | Root 已读取固定 tag 的官方 XSD；设计阶段未运行 converter |

### 2.3 Problem statement and gap

`SafeModelXml` 无法对 CMMN 1.1 字节执行与 BPMN/DMN 等价的安全解析。直接调用 Flowable converter 会绕开现有 bounded UTF-8/XXE 前置边界；把 CMMN schema 加入旧 `Schemas` holder 又会扩大旧路径初始化风险。

### 2.4 Evidence and current-chain map

| Entry/trigger | Current call chain | Data read/written | External dependency | Consumers | Evidence |
| --- | --- | --- | --- | --- | --- |
| BPMN/DMN parse | caller → `SafeModelXml.parse(bytes,bpmn)` → scan/DOM → packaged schema | 内存 `Document`；无写入 | JDK XML + Flowable packaged XSD | `ProcessModelValidator` | `EVD-001/002` |
| CMMN fixture review | 当前无 safe CMMN entry | 无 | Flowable converter artifact | 后续 release validator | `EVD-003/005/006` |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 本任务是既有 technical Process Server 的一个 package-private XML utility 与测试夹具切片，不是生成业务项目的首个业务实现，不创建/替换 archetype 示例链，也不改变任何业务模块。

## 3. Goals and Non-goals

### 3.1 Goals

- 为 CMMN 1.1 增加与现有 BPMN/DMN 等价的 bounded UTF-8、XXE-safe、packaged-XSD-only 入口。
- 保持旧 `parse(byte[],boolean)` 签名、错误和 schema 初始化时机。
- 提供无业务字段、无表达式、无自定义代码的正式中立 fixture，并以固定 Flowable 8 converter 验证结构。

### 3.2 Non-goals

- 不新增 CMMN enum、model admission、发布、部署、Case API、引擎执行、数据库、权限、配置或启动路径。
- 不实现完整 T3-08 生命周期，不声称 fixture 已由引擎运行。
- 不增加 Forms、planning table、discretionary/repetition、EL、Java delegate、Worker 或商业功能。
- 不添加依赖、Spring Bean、DTO/POJO、converter wrapper、业务层、ORM、CRUD、DDL 或迁移。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| XML parser utility | Affected | `SafeModelXml.java` | 新增 package-private CMMN schema selection；旧 BPMN/DMN 保持 | 完整内部合同/失败/兼容设计 | `§7, §8, §9, §13, §14, §15, §16, §17, §18` |
| Focused test + fixture | Affected | release test package；fixtures directory | 新增 static/converter proof | 精确 fixture 与测试设计 | `§7, §8, §14, §15, §16, §17, §18` |
| `ProcessModelValidator` and release admission | Unchanged | `ProcessModelValidator.java` only calls BPMN/DMN parse | 无 CMMN validator/admission | 边界回归说明 | `§14` |
| Public contract/client/API | Unchanged | contract/client/API currently lack Case admission in this slice | 无 wire/public symbol | scope search | `§16` |
| Persistence/DDL/config/permissions | Unchanged | ProcessDB V1–V7 and current config/manifests | 零修改 | byte/path diff | `§16` |
| Frontend/runtime/DB | Not applicable | 无目标路径或运行授权 | 不启动、不连接 | N/A | `§12` |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| `REQ-001` | 增加 package-private `SafeModelXml.parseCmmn(byte[])`，只验证 Flowable 8 打包的 CMMN 1.1 schema | Must | 正式 fixture 返回 namespace-aware `Document`；错误 schema 被拒绝 | Root Step 33 |
| `REQ-002` | CMMN 路径必须复用现有字节上限、严格 UTF-8、scan/DOM XXE 防护、外部资源禁止和统一泛化异常 | Must | empty/oversize/non-UTF8/DOCTYPE/错误 namespace 均抛同一 generic `IllegalArgumentException` 且不泄漏 cause/content | Root + `SafeModelXml` baseline |
| `REQ-003` | 现有 `parse(byte[],boolean)` 行为及 BPMN/DMN schema 惰性初始化保持 | Must | 旧签名不变；新 CMMN holder 不被旧选择提前触发；既有回归保持 | Root clarification |
| `REQ-004` | 正式中立 fixture 必须含两个 manual HumanTask、primary-complete sentry、supplemental complete/terminate 两个 OR entry criteria milestone、required primary/milestone、`autoComplete=false` | Must | XSD 和 fixed converter 都保留三 plan item、三 sentry、两人任务、一个 milestone；无 repetition/EL/custom code/Form | Root/native reconciliation |
| `REQ-005` | focused test 的两个模型正向场景都先调用 `parseCmmn`，四项 parser 上限各自验证“精确边界接受、越界拒绝”，然后仅结构场景调用固定 `CmmnXmlConverter` | Must | 7 个 focused tests：6 个 acceptance tests 在 stub RED，1 个纯拒绝矩阵仍通过；不启动 engine/DB | Root Step 33 |
| `REQ-006` | 实现与提交范围严格限制为六路径且无新依赖 | Must | 精确 path diff；POM/SQL/config/API/enum/release admission 均无变化 | Root Step 33 |

### 4.1 Scenario matrix

| Scenario | Actor/trigger | Preconditions | Main path | Alternative/failure path | Data/state change | Observable result | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Valid schema | model maintainer supplies fixture bytes | Flowable 8 resource present | bounds → UTF-8 → scan → DOM → CMMN XSD | 缺失或异常的打包 schema 保留既有内部初始化失败，不伪装为客户端输入错误 | none | `Document` returned | `REQ-001/002/003` |
| Unsafe/invalid XML | reviewer supplies XXE, bad UTF-8, oversize or wrong namespace | none | reject before external resolution | no content/cause leakage | none | same generic exception | `REQ-002` |
| Fixed structure | test reads formal fixture | safe parse succeeds | parse first, then fixed converter | converter loss/mismatch fails focused assertion | none | exact neutral model retained | `REQ-004/005` |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor/role | Goal and responsibility | Entry/channel | Permission/tenant context | Evidence |
| --- | --- | --- | --- | --- | --- |
| `ACTOR-001` | Process-model maintainer | 获得安全、确定的 CMMN XML prerequisite | package-private Java call | 无外部授权边界 | `EVD-001/006` |
| `ACTOR-002` | Implementation reviewer/test executor | 证明资源、边界与 fixture 结构而不启动 runtime | JUnit source test | 本地静态/module test | `EVD-003/004` |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `UC-001` | 安全解析受支持 CMMN | `ACTOR-001` | JDK XML、packaged XSD | 调用 `parseCmmn` | bounded bytes | 返回已验证 DOM | 非法输入统一拒绝 | 无持久副作用 | `REQ-001/002/003` | `INTERNAL-001` | `TEST-001/003–007` |
| `UC-002` | 审核正式中立模型结构 | `ACTOR-002` | fixed Flowable converter | focused test | `UC-001` 成功 | 三 plan item/三 sentry 结构匹配 | schema/converter mismatch 失败 | 无运行实例 | `REQ-004/005` | `INTERNAL-001` | `TEST-001/002` |
| `UC-003` | 保持旧解析边界 | `ACTOR-002` | existing tests | module regression | baseline unchanged | BPMN/DMN 继续按旧入口解析 | 回归阻断 Step | 无旧合同变化 | `REQ-003/006` | existing `parse(bytes,boolean)` | `TEST-008` |

## 5. Constraints, Assumptions, and Decisions

### 5.1 Confirmed constraints

- `SafeModelXml`、新测试均保持 package-private；不创建公共 facade。
- schema root/path 固定为 `org/flowable/impl/cmmn/parser/CMMN11.xsd`，资源依赖白名单固定五个 XSD。
- `parseCmmn` 不等于 model admission；任何后续 validator/release 仍需独立 Spec。

### 5.2 Small-gap assumptions

| ID | Inference | Repository evidence | Why locally reversible | Impact if wrong |
| --- | --- | --- | --- | --- |
| `ASM-001` | 使用 JDK `Supplier<Schema>` 隔离 schema 选择和初始化 | 当前两个 schema 仅在 holder 中选择；Root clarification | 私有重构且签名不外泄 | 可改成等价私有 selector，但必须重证初始化时机 |
| `ASM-002` | 测试名 `CmmnModelXmlTest` 和 fixture 名 `formal-case.cmmn` 跟随现有 release/fixtures 约定 | 当前 test/resource tree | 仅测试内部名称 | 无业务兼容影响 |

### 5.3 Resolved decisions

| ID | Decision | Decision owner | Evidence and rationale | Requirements |
| --- | --- | --- | --- | --- |
| `DEC-001` | 复用一个私有 parse body；旧入口与新入口只提供 lazy schema supplier | Root Review design | 最少重复且保持旧签名/时机 | `REQ-001/003` |
| `DEC-002` | 独立 `CmmnSchema` holder，不扩展旧 `Schemas` holder | Root Review design | 避免旧 BPMN/DMN 路径初始化 CMMN | `REQ-003` |
| `DEC-003` | 预声明 supplemental task，不引入 planning table/discretionary/repetition | Root/native reconciliation | 最小足以证明 sentry 和 OR milestone | `REQ-004` |

### 5.4 Open major decisions

None. 用户已授权继续实现原蓝图并在完成后审查；Root 对本派生设计执行 full-read/strict/hash 后 GO。Review 不等于用户另行 Accepted。

## 6. Project Technology Context

| Concern | Current choice | Repository evidence | Constraint on design |
| --- | --- | --- | --- |
| Language/runtime | Java 25 project | parent POM/current source | 只用 JDK `Supplier`/XML API |
| XML | JDK DOM/StAX/Schema | `SafeModelXml` | 复用安全属性和 generic error |
| CMMN | Flowable 8.0.0 | process-server POM/local jar | 使用打包 XSD 和 `CmmnXmlConverter` |
| Tests | JUnit Jupiter via Boot test | existing release tests | package-private focused test；无 engine/DB |

### 6.1 Java architecture profile and capability baseline

| Architecture profile | Archetype/template or base package | Exact evidence and verifier | Existing deviations | Design action |
| --- | --- | --- | --- | --- |
| Existing technical Process Server structure, explicitly preserved by current user/Root scope | `top.egon.cola.platform.tianji.process.server.release` | current module/package tree and `SafeModelXml` placement | 不把 technical utility 强制转换为 business `biz.*`/CRUD layers | Rule 11 Applicable PASS：只扩展现有 release parser/test package，不创建第三层次或新模块 |

| Need | Spring/JDK candidate | Spring Boot Starter candidate | Egon-COLA/module candidate | Proven gap | Decision/dependency impact |
| --- | --- | --- | --- | --- | --- |
| bounded secure XML | JDK DOM/StAX/Schema | None needed | existing `SafeModelXml` | only CMMN schema selector absent | reuse existing class；no dependency |
| CMMN schema/model conversion | packaged Flowable XSD/`CmmnXmlConverter` | existing CMMN dependencies | current process-server POM | no gap | reuse exact 8.0.0 artifact |

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | new `CmmnModelXmlTest`; existing `SafeModelXml` | semantic `*Test` behavior suffix；no carrier | two Java paths | type inventory/compile | PASS |
| Rule 2 | No | no layer handoff/DTO; raw bytes are validated inside existing technical boundary | no Bean Validation layer invented | parser only | focused invalid-input tests | N/A |
| Rule 3 | No | no POJO/value carrier/conversion | no Lombok/MapStruct type | none | path diff | N/A |
| Rule 4 | No | no business class or Spring Bean/DI | static package-private utility remains | none | source review | N/A |
| Rule 5 | Yes | current utility uses JDK; only new import is `java.util.function.Supplier` | JDK-only | `SafeModelXml` | import/dependency review | PASS |
| Rule 6 | No | no JSON/wire enum | no JSON change | none | scope diff | N/A |
| Rule 7 | No | no configuration key/file | no profile change | none | scope diff | N/A |
| Rule 9 | No | one fixed schema selection, Simple | direct private selector; no ceremonial pattern | parser | focused branch tests | N/A |
| Rule 10 | No | no time field/calculation | no time import | none | import review | N/A |
| Rule 11 | Yes | existing technical release package and explicit task decision | preserve exact module/package; no new business layers/ORM/CRUD/DDL | all three source/test/resource paths | path/static gate | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: affected symbols contain only XML bytes, DOM/schema objects, static bounds and fixture structure; no time field, persistence, wire value, calculation, clock, duration, zone or precision contract changes.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| `parseCmmn(byte[])` | Expand existing utility | `REQ-001/002` | call Flowable converter directly | bypasses existing bounded/XXE boundary | one package-private method; no state | Add |
| private supplier selector | Expand internals | `REQ-003` | copy entire parse method | duplication can drift | one JDK interface; no public coupling | Add |
| `CmmnSchema` holder | New private nested holder | `REQ-003` | add field to old holder | can initialize CMMN on old path | one lazy static | Add |
| test/fixture | New test assets | `REQ-004/005` | reuse one-task baseline | cannot prove sentry/milestone | test-only | Add |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 | 0 | direct converter only | unsafe input boundary absent | insufficient |
| Selected design | 0 | 0 | one package-private method | same existing generic invalid boundary | prerequisite safety proof |

### 7.1 System Architecture Design

No full-system diagram: this Simple change stays inside one existing package. `SafeModelXml` owns byte/XML safety; Flowable packaged XSD is read-only classpath data; `CmmnXmlConverter` is test-only structural corroboration.

#### 7.1.1 Boundary and responsibility table

| Module/component | Capability and data owned | Inputs/outputs | Allowed dependencies | Forbidden responsibility | Requirements |
| --- | --- | --- | --- | --- | --- |
| `SafeModelXml` | bounded safe parsing | `byte[] -> Document` | JDK XML, packaged XSD | admission/deploy/runtime | `REQ-001/002/003` |
| `CmmnModelXmlTest` | static contract proof | fixture bytes/assertions | JUnit, fixed converter | engine/DB/runtime | `REQ-004/005` |

### 7.2 High-Level Design

Both public-to-package entry paths delegate to one private parse body with a lazy `Supplier<Schema>`. The body rejects bounds before supplier access, decodes strict UTF-8, scans, builds a hardened DOM, then resolves the selected schema and validates. 客户端 XML/验证异常映射到既有 generic invalid；打包 schema 缺失等初始化故障保留既有内部配置失败语义。

### 7.3 Detailed Design

#### 7.3.1 Detailed component collaboration

| Step | Caller -> callee | Contract/symbol | Input/output mapping | State/data effect | Failure behavior | Requirements |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | old caller → old entry | `parse(bytes,boolean)` | boolean → BPMN/DMN supplier | none | unchanged generic invalid | `REQ-003` |
| 2 | future validator/test → new entry | `parseCmmn(bytes)` | fixed CMMN supplier | none | generic invalid | `REQ-001/002` |
| 3 | private parse → schema holder | `Supplier<Schema>#get` after bounds/decode/scan/DOM | selector → lazy schema | one JVM static schema cache | 缺失打包依赖保留内部初始化失败 | `REQ-002/003` |

The CMMN holder calls existing `schema(...)` with root `CMMN11.xsd`, directory `org/flowable/impl/cmmn/parser/`, and exact allowed set `CMMN11.xsd`, `CMMN11CaseModel.xsd`, `CMMNDI11.xsd`, `DC.xsd`, `DI.xsd`.

Transactions, persistence, business concurrency, idempotency and observability are N/A: all behavior is local deterministic parsing with no mutable business state, log, metric, database or external call. Each invocation creates its own Validator/DOM/StAX parser; only immutable compiled Schema is shared through JVM class initialization.

## 8. Package Structure and Code File Tree

### 8.1 Current relevant tree

```text
stellar-enclosure-tianji-process-server
├── src/main/java/.../release/SafeModelXml.java
└── src/test/resources/fixtures/baseline.cmmn
```

### 8.2 Target tree

```text
stellar-enclosure-tianji-process-server
├── src/main/java/.../release/SafeModelXml.java                       MODIFY
├── src/test/java/.../release/CmmnModelXmlTest.java                  CREATE
└── src/test/resources/fixtures/formal-case.cmmn                     CREATE
```

### 8.3 Package and file responsibilities

| Operation | Path/package | Symbols | Responsibility | Dependencies | Requirements |
| --- | --- | --- | --- | --- | --- |
| Modify | `.../release/SafeModelXml.java` | `parseCmmn`, private supplier parse, `CmmnSchema` | secure schema boundary | JDK/packaged XSD | `REQ-001/002/003` |
| Create | `.../release/CmmnModelXmlTest.java` | seven focused tests | RED/GREEN, exact-boundary and converter proof | JUnit/Flowable converter | `REQ-001–005` |
| Create | `.../fixtures/formal-case.cmmn` | fixed neutral case | schema/model fixture | CMMN 1.1 | `REQ-004` |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Change/necessity verdict | Name/purpose | Kind | Consumer | Owner | Symbol | Input | Output | Error model | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `INTERNAL-001` | New/Add after necessity audit | Safe CMMN XML parse | package-private static Java | same-package future validator/tests | process-server release package | `SafeModelXml.parseCmmn(byte[])` | raw bytes | namespace-aware validated `Document` | generic `IllegalArgumentException` | `REQ-001/002/003` |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 INTERNAL-001 — SafeModelXml.parseCmmn

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | Add one package-private parser entry; no public API or remote operation. |
| Independent consumer goal | Same-package validator/tests need a bounded DOM validated against packaged CMMN XSD. |
| Parameter ownership and derivation | Caller supplies raw bytes; parser owns fixed schema selection and every safety bound. |
| Direct/no-new-interface alternative | Direct converter bypasses the platform parser envelope; copying the entire parser risks security drift. |
| Caller use of result | Consumer inspects DOM; it is not an opaque fetch-then-forward token. |
| Round trips and failure points | Zero remote round trips or persistent state; one local parsing/validation boundary. |
| Verdict | Add the smallest package-private method on the existing utility; retain old entry. |

##### Identity and purpose

`static Document parseCmmn(byte[] bytes)` is package-private, synchronous, in-memory, and has no identity/tenant/permission responsibility.

##### Request parameters

`bytes` is required, nonempty, at most `MAX_BYTES`, strict UTF-8, XML 1.0, and subject to current element/depth/attribute and XXE restrictions.

##### Success response

Returns the namespace-aware DOM only after packaged CMMN 1.1 schema validation. It does not assert platform feature admission.

##### Error responses

Every client null/empty/oversize/encoding/DTD/entity/schema-validation failure returns `IllegalArgumentException("Process model is invalid or not supported")` with no exposed cause. Packaged-schema initialization failures retain the existing internal configuration-error behavior; the parser does not catch JVM Errors.

##### Interface logic for frontend and consumers

Bounds execute before schema supplier selection; decode/scan/DOM hardening stays in current order; only then does the private parser obtain the CMMN schema and validate. There is no frontend or retry behavior.

##### Compatibility and verification

Old `parse(byte[],boolean)` remains source/binary compatible and delegates through the same body. `TEST-001–008` prove new behavior and preserved old tests; no external API documentation applies.

## 10. POJO and Data Model Design

Unchanged. No carrier, POJO, enum, state model, mapping or converter is added; `Document` and `Schema` remain JDK types.

## 11. Database Design

Unchanged. No table, schema, transaction, migration or DDL is read or modified; ProcessDB V1–V7 remain byte-identical.

## 12. Frontend Page Design

N/A. No frontend path, route, state or contract participates in this package-private parser prerequisite.

## 13. Design Patterns and Architecture Principles

No business design pattern is selected. The flow is Simple and fixed; a Strategy/Factory/Adapter would add types without a present variation point. The private schema supplier is a JDK lazy-selection mechanism inside the existing utility, not a new architectural layer.

## 14. Test Design

### 14.1 Unit tests

| ID | Level | Target | Scenario/input | Expected assertion | Test double/data | Tool/path | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `TEST-001` | Unit | `parseCmmn` | `formal-case.cmmn` | valid DOM and CMMN namespace/root | fixture | `CmmnModelXmlTest` | `REQ-001/004/005` |
| `TEST-002` | Unit | fixed converter | same bytes after `parseCmmn` | 3 plan items, 2 manual humans, 3 sentries, required primary/milestone, OR milestone criteria, no repetition/expressions/forms | real converter | same | `REQ-004/005` |
| `TEST-003` | Unit | `parseCmmn` | null/empty/malformed UTF-8/DOCTYPE/XXE/wrong namespace/missing required targetNamespace | generic rejection, no cause/content leak | mutated fixture/bytes | same | `REQ-002` |
| `TEST-004` | Unit | byte limit | exact `MAX_BYTES` valid XML then `MAX_BYTES+1` | exact accepted；over rejected | padded fixture | same | `REQ-002/005` |
| `TEST-005` | Unit | depth limit | exact `MAX_DEPTH` then one deeper | exact accepted；over rejected | generated CMMN wrappers | same | `REQ-002/005` |
| `TEST-006` | Unit | element limit | exact `MAX_ELEMENTS` then one more | exact accepted；over rejected | schema-valid unreferenced humanTask definitions | same | `REQ-002/005` |
| `TEST-007` | Unit | attribute limit | exact `MAX_ATTRIBUTES` then one more | exact accepted；over rejected | allowed foreign namespace attributes | same | `REQ-002/005` |
| `TEST-008` | Regression | old parser | existing release tests | existing BPMN/DMN tests remain green | existing fixtures | existing release test selector | `REQ-003/006` |

RED is intentionally compile-ready: first add a temporary `parseCmmn` stub that throws the same generic exception. The two model-positive tests and four exact-boundary acceptance tests fail for missing positive behavior; the pure rejection matrix passes. GREEN replaces only that stub path with the supplier-based implementation. Actual counts belong in Evidence after execution.

### 14.2 Integration, contract, persistence, component, and end-to-end tests

N/A for this step. No engine, Spring context, DB, network, service, application or browser is started.

## 15. Non-functional and Cross-cutting Design

- Security: retain both StAX and DOM XXE defenses, empty external access properties, closed schema resolver and generic error.
- Capacity: retain `MAX_BYTES`, `MAX_ELEMENTS`, `MAX_DEPTH`, `MAX_ATTRIBUTES` before schema resolution.
- Maintainability: one private parse body; one separate lazy CMMN schema holder; no duplicated security pipeline.
- Privacy/observability: no payload logging or error cause exposure; no new telemetry.

## 16. Compatibility, Migration, Rollout, and Rollback

No migration, configuration, permission, deployment ordering or public compatibility change. Existing parser callers and release admission remain unchanged. Rollback is a path-limited revert of the six-path Step; there is no data rollback.

## 17. Alternatives and Decisions

| Option | New elements and interactions | Advantages | Disadvantages/risks | Repository fit | Decision and rationale |
| --- | --- | --- | --- | --- | --- |
| A — shared private parser + separate holder | one method, one supplier, one nested holder | preserves security and lazy timing | small private indirection | exact fit | Selected |
| B — duplicate CMMN parser body | copied security pipeline | no supplier | drift/security divergence | poor | Rejected |
| C — call converter directly | no parser change | shortest code | bypasses platform bounds/XXE/XSD boundary | contradicts requirement | Rejected |

## 18. Risks and Open Questions

| ID | Risk/question | Probability | Impact | Mitigation or decision owner | Status |
| --- | --- | --- | --- | --- | --- |
| `RISK-001` | upstream XSD transitive dependency omitted from whitelist | Low | schema init failure | exact five-resource jar inventory + positive test | Closed by design |
| `RISK-002` | supplier refactor changes old initialization/error behavior | Low | BPMN/DMN regression | separate CMMN holder + existing regression | Closed by `TEST-008` |
| `RISK-003` | fixture mistaken for runtime acceptance | Medium | false T3-08 claim | explicit non-goals/evidence wording | Closed by scope |

## 19. Traceability Matrix

| Requirement | Use case | Affected area/chapter | Context-only or unchanged boundary | Interface/model/database/frontend | Tests | Acceptance evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `REQ-001` | `UC-001` | parser `§7–9` | release admission unchanged | `INTERNAL-001` | `TEST-001/003–007` | valid/reject behavior |
| `REQ-002` | `UC-001` | security `§7,§9,§15` | no external resolver | `INTERNAL-001` | `TEST-003–007` | generic bounded rejection |
| `REQ-003` | `UC-003` | compatibility `§7,§16` | old callers unchanged | old internal entry | `TEST-008` | regression |
| `REQ-004` | `UC-002` | fixture `§8,§14` | runtime unchanged | CMMN model only | `TEST-001/002` | exact structure |
| `REQ-005` | `UC-002` | test `§14` | engine/DB N/A | test-only converter | `TEST-001/002` | parse-first assertions |
| `REQ-006` | `UC-003` | scope `§8,§16` | API/DB/config unchanged | none | `TEST-008` + diff | exact six paths |

## 20. Review and Acceptance

### 20.1 Original-request fidelity

All Step 33 requirements map to `REQ-001–006`; exclusions explicitly prohibit later T3-08 surfaces.

### 20.2 Repository and technical fidelity

Paths, symbols, Flowable class/resource names, current POM capability and parser behavior were read from baseline/local artifact. No runtime claim is made.

### 20.3 Cross-section consistency

The same three source/test/resource paths, one internal contract, fixed fixture semantics, generic error and no-admission boundary appear in requirements, design, tests and traceability.

### 20.4 Relationship and effective-design review

The legacy blueprint is a normative dependency only for the safety/non-goal boundary; this focused Review neither amends nor claims completion of T3-08.

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | `§6.1`, existing release package, explicit current scope | technical Process Server placement preserved | None |
| `MC-REUSE-001` | Applicable | PASS | `§6.1` ledger | JDK/existing utility/Flowable artifact reused | None |
| `MC-DEP-001` | Applicable | PASS | POM + six-path boundary | no dependency/POM addition | None |
| `MC-NAME-001` | Applicable | PASS | `SafeModelXml`, `CmmnModelXmlTest` | existing utility name retained; test suffix semantic | None |
| `MC-VALID-001` | Not applicable | N/A | no layer/DTO handoff; internal bytes use existing parser validation | Bean Validation concern absent | None |
| `MC-MODEL-001` | Not applicable | N/A | no POJO/carrier/entity | model-construction rule absent | None |
| `MC-CONVERT-001` | Not applicable | N/A | no cross-layer mapping; upstream converter is test oracle | no project converter | None |
| `MC-LOG-001` | Not applicable | N/A | no business class/log | logging concern absent | None |
| `MC-BEAN-001` | Not applicable | N/A | no Spring Bean/DI | injection concern absent | None |
| `MC-UTIL-001` | Applicable | PASS | JDK XML + `Supplier` only | closed utility allowlist satisfied | None |
| `MC-JSON-001` | Not applicable | N/A | no JSON/wire enum | JSON concern absent | None |
| `MC-TIME-001` | Not applicable | N/A | `§6.3` | no time semantics | None |
| `MC-CONFIG-001` | Not applicable | N/A | no configuration path/key | profile parity unaffected | None |
| `MC-PATTERN-001` | Not applicable | N/A | Simple fixed parser selection | direct implementation; no business complexity | None |
| `MC-SCOPE-001` | Applicable | PASS | `§3.3`, exact six-path decision | no scope expansion | None |
| `MC-TEST-001` | Applicable | PASS | `TEST-001–008` design | focused RED/GREEN/exact-boundary and regression gates defined | Execute after review |
| `MC-BLOCKER-001` | Applicable | PASS | `§5.4`, all rows above | no open design blocker; original continuation authority applies | None |

### 20.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

POJO/value/enum、MP/persistence、Bean Validation、uniqueness/DDL、CQE/Event 均不受影响。Rule 11 按用户最新显式范围保留既有 technical Process Server release package；不把本 utility 切片转换成业务三层或新增 COLA 层。

## Backend generation and dependency evidence

N/A：无 DDL/CRUD/catalog、无 generator、无 dependency introduction、无 SQL journal。现有 Flowable 8 dependency 已足够且 POM 不修改。
