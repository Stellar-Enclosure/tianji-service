# 普通Worker补偿模型与业务收据实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-12-44-worker-compensation-fixture.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 12:44 Asia/Shanghai` |
| Updated | `2026-11-05 13:12 Asia/Shanghai` |
| Owner | Root; exclusive Maven/staging/commit owner |
| Repository | `tianji-service` |
| Scope | Two sequential commits: PS closed compensation model; test-only BusinessDB receipt fixture |
| Source Requirement | User master-plan goal; Root full Spec and corrective-delta review, exact approved design for Plan |
| Baseline Revision | `main@b63fc8a8b1e20733127bc1516d8799ca262997ee`, clean; 932 tracked files and25 immutable main SQL |
| Implements Spec | [Worker compensation fixture Spec](../spec/2026-11-05-12-09-worker-compensation-fixture.md) |
| Spec Status | `Review` |
| Spec Revision | Linked effective SHA `de12e22b3aa322da4f0d7128aa3376b4d6c7aa76800502a7ee9c414556b58f0d`; normative model design remains 4504f960...; future fixture privilege DEC-711 corrected before Step1 commit |
| Effective Specs | [Primary compensation Spec](../spec/2026-11-05-12-09-worker-compensation-fixture.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Implement the exact-one ordinary compensation profile and an isolated test-only reserve/undo receipt example in two semantic commits. Step1 must be fully verified and committed before any Step2 repository edit. Root must review this complete Plan and lock current hashes before source GO; this draft itself authorizes no source or execution. No application, Flowable engine, PostgreSQL, provider, listener, container or browser is started. Every Maven selector is explicit and skipITs=true.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Primary Spec](../spec/2026-11-05-12-09-worker-compensation-fixture.md).
- Status: Review; Root accepted exact normative bytes for Plan, not user/runtime acceptance.
- Revision: SHA4504f960...; Root read initial970lines, strict195line delta, corrective182/83line deltas and63line no-logger correction; independently strict PASS. Root then reviewed the narrow fixture lock-privilege correction DEC-711; all Step1 model/fixture source and tests stayed byte-identical.
- Approval evidence: user master goal and Root recorded design decision; source GO remains a separate current-Step gate.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [compensation](../spec/2026-11-05-12-09-worker-compensation-fixture.md) | Review/current exact SHA above | All REQ701-718 | Only new design authority |
| Preservation context only | [fixed business error](../spec/2026-11-05-04-48-worker-business-error-routing.md) | Baseline delivered | §6/7/9/11/14 | Preserve error routing, async receipt semantics, ordinary registry and technical profile |
| Roadmap | [master](../../../../docs/plans/agent-framework-upgrade-plan.md) | Current T3-10 | T3-10/S10/S11 | Other recovery/T3-12/T4 remain Required |

### 2.3 Superseded or excluded content

No whole document supersession. Primary amends only the predecessor's compensation rejection for this exact profile. Lost /tmp drafts are not effective. No arbitrary compensation, second execution engine, production business schema, migration, API, POM, config, Worker main or frontend change.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-701 | Primary §4/7/14 | 仅准入一个固定 ordinary original/undo pair | 固定 compensation boundary/association/handler/throw 各一个，immutable metadata；旧模型空 map | Step1 |
| REQ-702 | Primary §4/7/14 | compensation boundary 非打断且只附着原动作 ordinary worker | 必须显式 cancelActivity=false、无 sequence edge；DMN/Human/Case/Agent wait 等拒绝 | Step1 |
| REQ-703 | Primary §4/7/14 | detached compensation handler 通过单向 association 关联 | isForCompensation=true、direction=One、非原动作自己、无 normal in/out/其他 boundary；保留 workerTasks | Step1 |
| REQ-704 | Primary §4/7/14 | throw 固定指向原动作并通往专用结束节点 | 显式 activityRef/original 与 waitForCompletion=true、exact1in/1out→独占 end；禁止广播/额外 definition | Step1 |
| REQ-705 | Primary §4/7/14 | throw 必须被 original 显式 normal-success EDGE 支配 | 所有实际 Error/Timer 隐式边参与 AND；original自 error 与绕过原动作的合流路径拒绝 | Step1 |
| REQ-706 | Primary §4/7/14 | 原动作与 undo 只读 initial operationId 输入映射且无 outputs | operationId→operationId STRING；不读 original output/模型变量推断业务收据 | Step1 |
| REQ-707 | Primary §4/7/14 | 无补偿语法时保留原验证/构造器/图限制 | 旧 fixtures/descriptor/worker bindings 不变；全局ID、DAG/path/traversal cap继续 | Step1 |
| REQ-708 | Primary §4/7/14 | 在任何 JDBC 前验证 BPMN scope 与完整 enrollment | caseId=null，source/tenant/process/business/correlation/input 与 expected 全等；original/undo topic分别固定reserveBusiness/undoReservation、schemaVersion=1、effectSafety=TRANSACTIONAL_RECEIPT；失败 SQL/connection 调用0 | Step2 |
| REQ-709 | Primary §4/7/14 | fixture 只接受现 Store 同绑定连接的 READ_COMMITTED TX | actual TX/ConnectionHolder/同DataSource；隔离不是 RC 时业务 SQL0，不改Store或借第二连接 | Step2 |
| REQ-710 | Primary §4/7/14 | 原动作 reserve、original receipt 与技术 receipt 同本地 TX | guarded version/reserved更新恰好1，insert immutable receipt，再由Store保存技术receipt；失败无成功返回 | Step2 |
| REQ-711 | Primary §4/7/14 | 原动作跨技术任务并发重放 | 首lookup为空→object锁→plain immutable receipt重读；已存在完整binding返回；不得反向 FOR UPDATE | Step2 |
| REQ-712 | Primary §4/7/14 | undo 从持久 original receipt 派生稳定 key | key binds namespace/source/tenant/originalReceiptId/UNDO_RESERVATION_V1，不信 caller receipt或新taskId | Step2 |
| REQ-713 | Primary §4/7/14 | undo replay 先于新对象版本检查 | 锁original、查prior comp；完整binding相同直接原receipt；重复执行/对象后续变更不再undo | Step2 |
| REQ-714 | Primary §4/7/14 | 首次 undo 受 object CAS 与版本单调约束 | 匹配 original after-state 后 reserved恢复before，version再+1；写compReceipt同TX；0row/overflow回滚 | Step2 |
| REQ-715 | Primary §4/7/14 | recorded_at 仅是 BusinessDB 写入时钟 | OffsetDateTime→Instant；UTC/microsecond；不用它推导真正commit时刻或跨库顺序 | Step2 |
| REQ-716 | Primary §4/7/14 | 技术接收/原生leave/subscription/undo业务完成分开取证 | explicit wait/getter/throw终止/ack均不单独证明undo receipt；SDK行为固定SHA证据 | Both |
| REQ-717 | Primary §4/7/14 | 新增业务 DDL 仅 test fixture，旧25SQL不变 | 三业务表只在缺失的loopback专用schema；不生产migration、不自动provision/drop未知schema | Both |
| REQ-718 | Primary §4/7/14 | 只做有限离线/编译与文档交付 | 明确selectors不选PG/listener；不设optin、不启动环境；源码不伪装真实S10/S11验收 | Both |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

First expose model value/getter and exact XML, prove the pure converter accepts its bytes, then lock typed validator behavior before extending the existing DOM/topological pass. Preserve every old constructor and graph fact. Only after that semantic commit introduce the test helper/guarded PG source. The latter adds no production class and executes no database.

### 4.2 Test-first strategy

G1 missing nested type/getter/resource reflection -> model A/resource -> converter-positive compile -> typed model RED -> validator B -> model GREEN/commit. Then G7 missing fixture type assertion -> fixture A signatures/values -> typed fixture RED -> fixture B -> unit GREEN -> scenario/hook compile-only -> final finite regression/commit. Only expected JUnit F with E=0/S=0 counts as RED. Compile, fixture, dependency, environment or timeout failures are repaired and rerun; never counted as behavior RED.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step1 | Current exact clean baseline/Root GO | Read-only review/outside-repo design | Any Step2 repository edit or concurrent Maven | Metadata/fixture/compiler prerequisites |
| Step2 | Exact Step1 semantic commit and fresh lock | Independent read-only review | Step1 edits, DB/runtime operations, concurrent Maven | Fixture cannot hide missing model behavior |

### 4.4 Commit boundaries

Exactly one path-limited semantic commit per Step, seven then six paths. Root stages exact declared files; no push/release/activation or history rewrite. Baseline main SQL remains immutable.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Existing validator/model | Add/Expand §7 | Current closed DOM and private FlowNode | Extend same pass | One metadata map/local edge fact; no network | Implement |
| Original/undo handlers | Add test-only §7/11/13 | Existing TransactionalWorkerHandler/Store | Two concrete Strategies on same interface | No new TM/connection/production class | Implement |
| Three business tables | Add test-only §11 | Technical task receipt lacks business operation identity | Isolated fixture-owned schema only | Three local rows/constraints, no production migration | Implement |
| Digest/validation/JDBC | Keep §6 | Existing public APIs | Reuse directly | No dependency or utility | Already exists |
| Generic REST/Graph/compensation service | Remove §7/17 | No slice consumer requirement | Fixed model/current Worker API | Extra calls/state/permissions not justified | Excluded |

No fetch-then-forward API, caller-selected receipt/action, speculative cache, layer, mapper or production entity is introduced.

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| metadata/XML | 701/707 | G1 reflection | Existing model and converter | VO/getter/old descriptor+fixture | typed model | Step1 |
| closed grammar/edge proof | 701-707/716 | G3 typed | metadata/resource converter-positive | approved pair/all-edge success fact | release/Step2 fixture | Step1 |
| fixture guards/receipt protocol | 708-715 | G7/G8 | Step1 commit; existing W/JDBC/Validation | test-only strategies/rows | compile-only scenario | Step2 |
| future native scenario | 716-718 | compile boundary | Fixture unit GREEN/current PG harness | opt-in helper source/README | User-controlled future acceptance | Step2 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | PS existing technical release package/plain JDBC | Primary§6.1/DEC710 | Keep final/explicit technical values; no biz/module/MP conversion | Both; MC-ARCH |
| Reuse/capability | Existing validator, Store, ValidationUtils, digest | Primary§6.1 | Extend existing owners, no dependency | Both; MC-REUSE/DEP |
| Naming/model/validation/conversion | Adjacent immutable metadata/WorkerParameter | Primary§6/10 | VO values, Default annotations, stateful TX guards, no cross-layer mapper | Both; MC-NAME/VALID/MODEL |
| Bean/logging/util/JSON/time/config | Current PS has no Lombok compile dependency | PrimaryDEC710/TIME701/702 | No new Bean/logger/config/JSON; exact time semantics retained | Both; MC-LOG/BEAN/UTIL/TIME |
| Variation/pattern | Handler Strategy/Store lifecycle/private graph facts | Primary§13 | Reuse Strategy and lifecycle; local dataflow proof | Both; MC-PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Model safety | JDK DOM/current SafeModelXml | validator90-325/private FlowNode | Missing only compensation profile | Extend | Local helper/fact only | Step1 MC-REUSE |
| local atomicity | Spring JDBC/TX/current Store | Store.execute72-104 | Existing owns transaction | Reuse | No new TM/connection | Step2 MC-REUSE |
| bound RC proof | ConnectionHolder7.0.10 | public getConnection/getTransactionIsolation | Fixture must check actual isolation | Reuse | Local guard only | Step2 MC-VALID |
| receipt identity | ProcessRequestDigest.hash | delegates public OrchestrationRequestDigests | Existing framed hash | Reuse | No crypto helper | Step2 MC-UTIL |
| pure input constraints | Common ValidationUtils instance | validate returns value, Default | Existing | Reuse | No group framework | Step2 MC-VALID |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Semantic technical value/handler/test names; no entity scaffolding. | §8/each Step EVD | Both | PASS |
| Rule 2 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Validate closed XML and each real handler boundary; Default annotation metadata plus stateful TX checks. | §8/each Step EVD | Both | PASS |
| Rule 3 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Only immutable values use records; defensive copies; no entity or conversion bypass. | §8/each Step EVD | Both | PASS |
| Rule 4 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Spec DEC-710 exact technical test-helper/unchanged main profile; no new logger/Bean/Lombok/POM, not a general production waiver. | §8/each Step EVD | Both | N/A |
| Rule 5 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | JDK/current framework/common types only. | §8/each Step EVD | Both | PASS |
| Rule 6 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | No new external JSON/enum/DTO; existing contract unchanged. | §8/each Step EVD | Both | N/A |
| Rule 7 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | No profile/configuration key changes. | §8/each Step EVD | Both | N/A |
| Rule 9 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Existing Strategy+Store lifecycle for two actions; cohesive private graph proof. | §8/each Step EVD | Both | PASS |
| Rule 10 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Spec TIME701/702 UTC instant/write-time/deadline separation, no global-clock guarantee. | §8/each Step EVD | Both | PASS |
| Rule 11 | Primary§6.2 | Current technical profile/Spec evidence | §5/7 exact order | Preserve current approved technical profile/Components; no MP/DDLrunner/generator/business module. | §8/each Step EVD | Both | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-701 | ReceiptVO.recordedAt / original/comp recorded_at | Occurred instant | JDBC OffsetDateTime to Instant | UTC instant | Internal | Not applicable: row write fact | Not applicable: no UI | Not true commit or cross-DB order | timestamptz(6)/BusinessDB clock_timestamp | Existing technical committedAt unchanged | Step 2; TEST-716; G9/G10, future manual PG only |
| TIME-702 | WorkerExecutionContext.deadline/lock guard | Occurred instant | Instant | Not persisted | Internal | Not applicable: technical contract | Not applicable: no UI | BusinessDB now strictly before supplied deadline after locks; clock skew unmeasured | Store min(lease-safety, dbnow+maxWorkTime), same bound DB clock | No Store/time framework change or global-hard-deadline claim | Step 2; TEST-709/713/716; G9/G10 and source mapping |

## 5. Change File Tree

```text

CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/WorkerCompensationModelTest.java [Step 1]

MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java [Step 1]

CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-compensation.bpmn20.xml [Step 1]

MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java [Step 1]

CREATE docs/egon/spec/2026-11-05-12-09-worker-compensation-fixture.md [Step 1]

CREATE docs/egon/plan/2026-11-05-12-44-worker-compensation-fixture.md [Step 1]

CREATE docs/egon/evidence/2026-11-05-worker-compensation-model.md [Step 1]

CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixtureTest.java [Step 2]

CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixture.java [Step 2]

CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/WorkerCompensationFlowableScenarios.java [Step 2]

MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java [Step 2]

MODIFY README.md [Step 2]

CREATE docs/egon/evidence/2026-11-05-worker-compensation-fixture.md [Step 2]

```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/WorkerCompensationModelTest.java` | Current ProcessModelValidatorTest/HumanTaskModelTest direct validator and converter patterns. | WorkerCompensationModelTest; compensation_surface_present; converter_accepts_exact_fixture; closed_profile_and_edge_dominance | Prove missing model surface then final closed profile before validator implementation. | Step 1 | REQ-701-707/716-718 | Root named gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java` | ValidatedProcessModel existing final fields/Map.copyOf/xml.clone and constructor chain. | WorkerCompensationVO; workerCompensations; new terminal overload | Append immutable metadata while retaining every old constructor descriptor. | Step 1 | REQ-701-707/716-718 | Root named gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-compensation.bpmn20.xml` | Existing worker-business-error/mapping fixture namespace and ordinary Worker attributes. | workerCompensation/reserve/reserveCompBoundary/reserveCompAssociation/undoReserve/downstream/throwUndo/compEnd | Supply one exact valid original/undo error-triggered fixture. | Step 1 | REQ-701-707/716-718 | Root named gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java` | Existing90-325 parser/toposort; actual FlowNode fields657-669 and workerTask helpers. | validateBpmn; private IncomingEdge; FlowNode.incomingEdges/originalNormalBefore; private compensationProfile helper and private CompensationProfile(WorkerCompensationVO metadata,String originalNormalFlowId) | Extend existing validator with exact profile and all-edge normal-success proof. | Step 1 | REQ-701-707/716-718 | Root named gates |
| CREATE | `docs/egon/spec/2026-11-05-12-09-worker-compensation-fixture.md` | Root full1031line corrected design SHA4504... and independent strict PASS. | Primary Spec; RelatedPlans backlink | Publish accepted effective specification with only related-plan metadata change. | Step 1 | REQ-701-707/716-718 | Root named gates |
| CREATE | `docs/egon/plan/2026-11-05-12-44-worker-compensation-fixture.md` | Root authoring against current b63 source/Spec and peer checklist. | Two ordered Steps/13paths/G1-G12 | Publish this exact reviewed executable Plan. | Step 1 | REQ-701-707/716-718 | Root named gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-compensation-model.md` | Existing evidence documents distinguish source and runtime. | G1-G6; REQ701-707/716-718; 17MC/10Rules | Record actual model gates and current-Step conformance. | Step 1 | REQ-701-707/716-718 | Root named gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixtureTest.java` | Current JUnit/Mockito tests and Worker contract APIs; pure finite mocks. | CompensationBusinessFixtureTest; presence then typed guard/RC/original/undo/replay/time methods | Lock fixture guards/replay/CAS/time contract before its implementation. | Step 2 | REQ-708-718 | Root named gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixture.java` | Actual ValidationUtils instance; ConnectionHolder public API; existing Worker execution/digest/exception types. | CompensationBusinessFixture; ExpectedOperationVO/OriginalReceiptVO/CompensationReceiptVO; originalHandler/undoHandler; private guards/mappers; schema provision helper | Implement isolated test-only receipt protocol using existing Store transaction. | Step 2 | REQ-708-718 | Root named gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/WorkerCompensationFlowableScenarios.java` | Actual ProcessServerPostgresTest outer app/jdbc/runtimeUser scope; existing guarded Worker business helper. | public static verifyIfConfigured(ConfigurableApplicationContext,JdbcTemplate,String) throws Exception | Author future manual native/business scenario without auto execution. | Step 2 | REQ-708-718 | Root named gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java` | verifyWorkerAdapter(app,jdbc) at632; outer scopedUrl/runtimeUser declared in main test. | Existing main test app scope; WorkerCompensationFlowableScenarios call | Add guarded hook in the actual existing main PG test scope. | Step 2 | REQ-708-718 | Root named gates |
| MODIFY | `README.md` | Existing README testing/opt-in guidance. | README worker compensation test section | Explain exact finite commands and manual acceptance boundaries. | Step 2 | REQ-708-718 | Root named gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-compensation-fixture.md` | Existing evidence format; Step1 committed evidence. | G7-G12 reports; REQ708-718; aggregate13paths; 17MC/10Rules | Record actual full fixture and aggregate conformance. | Step 2 | REQ-708-718 | Root named gates |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Current b63fc8a clean; outer Stellar-Enclosure is not Git. Root records all tracked hashes, exact Step paths and25 old main SQL before GO. Inspect relevant user AGENTS/technical predecessor exceptions. Preserve unrelated changes, existing migrations and public signatures; no generated files or whole-directory staging.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK25 | /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home | Explicit JAVA_HOME | Compile/unit only |
| Source reactor | scripts/verify-source-reactor.sh | Adjacent Components/IAM/Tianshu source repos and cached dependencies present | Offline exact selectors |
| Settings | /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml | Credential-free localRepository /Users/mario/maven/repository | Never change global settings |
| Framework | Existing PS/W/Common dependencies | Actual InputStreamProvider/ConnectionHolder APIs | No Lombok project dependency assumed |
| Runtime | User owned | Never set PG opt-ins/start services here | RuntimeUnverified |

### 6.3 Immutable constraints and approved decisions

All25 current main db SQL immutable; no new production migration. Test-only DDL stays in Java helper and runs only future user opt-in after fresh-loopback/absent-schema guards. Preserve old Error/Timer/case/Agent/registry and metadata descriptors. Fixture is technical test helper without logger/Bean/POM. Row recordedAt is DB write time, not true commit; no native wait/liveness/cross-DB atomicity proof.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-701 | Spec's before-any-JDBC applies to fixture invocation, not preexisting Store technical lookup | Spec swimlane; Store.execute runs its own lookup before handler | Fixture guards precede all its supplied JDBC/DataSource/Connection access; Store main unchanged | Return to Spec if global chain-zero-SQL intended |
| PLAN-CLAR-702 | Hook uses outer main app/jdbc/runtimeUser scope, not verifyWorkerAdapter locals | PS test632/914 | Exact3arg helper avoids invented harness/missing variable | Repair test hook before compilation |
| PLAN-CLAR-703 | Fresh fixture schema applies existing Worker V1/V2 migrations in future opt-in only | Existing W schema/Store intent checks | No existing migration edited; three new business tables remain test-only | Compilation/runtime acceptance remains separate |
| PLAN-CLAR-704 | Implement Primary DEC-711 test-only lock privilege and immutable trigger | EVD-715 official PostgreSQL SELECT/CREATE TRIGGER | Required original row lock and immutability both preserved; only future fixture provisioning/role probe changes | Actual dedicated-role PG-701 proof remains unexecuted |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Validate the exact compensation model

- Requirements: REQ-701, REQ-702, REQ-703, REQ-704, REQ-705, REQ-706, REQ-707, REQ-716, REQ-717, REQ-718
- Dependencies: Current clean baseline, accepted Spec/Plan and Root current-Step GO
- Baseline state: No compensation syntax/map/fixture; old models and W contracts unchanged
- Observable outcome: Exactly one approved pair, immutable metadata, all-edge success proof with old models preserved
- End state: Exact7 owned files, named finite tests GREEN, docs/17MC/10Rules/excluded/25SQL audit and one commit.
- Test-first gate: Required — missing surface assertion then typed missing behavior; onlyJUnitF/E0/S0 accepted.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — every row independently closed; Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/WorkerCompensationModelTest.java`

- Purpose: Prove missing model surface then final closed profile before validator implementation.
- Symbols: WorkerCompensationModelTest; compensation_surface_present; converter_accepts_exact_fixture; closed_profile_and_edge_dominance
- Repository evidence: Current ProcessModelValidatorTest/HumanTaskModelTest direct validator and converter patterns.
- Dependencies and consumers: Existing model/converter first; File2/3 enable typed phase; File4 remains unchanged until genuine typed RED.
- Why now: Lock expected missing surface before either metadata or resource exists.
- Contract/signature changes: Reflection presence avoids new-type compile dependency; typed output equals six-field VO and legacy constructors remain empty.
- Input/output and state mapping: Classpath XML -> assertDoesNotThrow converter/validator -> immutable approved metadata / exact maps.
- Error and edge behavior: Malformed/partial/multiple profile, self-error, bypass, unknown edges/flags/input/output fail safely; preserve old fixtures.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11 — named model tests, actual SDK/closed grammar checks and immutable metadata under existing technical profile.
- Implementation pseudocode:

```java
// Phase0 before File2/3:
assertDoesNotThrow(() -> Class.forName(ValidatedProcessModel.class.getName() + "$WorkerCompensationVO"));
assertDoesNotThrow(() -> ValidatedProcessModel.class.getDeclaredMethod("workerCompensations"));
assertNotNull(getClass().getResource("/fixtures/worker-compensation.bpmn20.xml"));
// After model A/resource, first prove converter with actual API:
BpmnModel nativeModel = assertDoesNotThrow(() -> new BpmnXMLConverter()
    .convertToBpmnModel(() -> new ByteArrayInputStream(xml), true, true));
var process = nativeModel.getMainProcess();
var original = assertInstanceOf(ServiceTask.class,process.getFlowElement("reserve"));
var handler = assertInstanceOf(ServiceTask.class,process.getFlowElement("undoReserve"));
assertFalse(original.isForCompensation()); assertTrue(handler.isForCompensation());
var boundary = assertInstanceOf(BoundaryEvent.class,process.getFlowElement("reserveCompBoundary"));
assertEquals("reserve",boundary.getAttachedToRefId()); assertFalse(boundary.isCancelActivity());
var association = assertInstanceOf(Association.class,process.getArtifact("reserveCompAssociation"));
assertEquals(AssociationDirection.ONE,association.getAssociationDirection());
assertEquals("reserveCompBoundary",association.getSourceRef()); assertEquals("undoReserve",association.getTargetRef());
var thrown = assertInstanceOf(ThrowEvent.class,process.getFlowElement("throwUndo"));
var definition = assertInstanceOf(CompensateEventDefinition.class,thrown.getEventDefinitions().getFirst());
assertEquals("reserve",definition.getActivityRef()); assertTrue(definition.isWaitForCompletion());
// Then full typed RED before validator B:
ValidatedProcessModel model = assertDoesNotThrow(() -> validator.validateBpmn(xml,"workerCompensation"));
assertNotNull(model);
assertEquals(Map.of("reserve",new WorkerCompensationVO("reserve","reserveCompBoundary",
    "reserveCompAssociation","undoReserve","throwUndo","compEnd")),model.workerCompensations());
assertEquals(Set.of("reserve","undoReserve","downstream"),model.workerTasks().keySet());
assertThrows(UnsupportedOperationException.class,() -> model.workerCompensations().clear());
assertThrows(IllegalArgumentException.class,() -> validator.validateBpmn(selfErrorXml,"workerCompensation"));
assertThrows(IllegalArgumentException.class,() -> validator.validateBpmn(bypassMergeXml,"workerCompensation"));
// Self-error mutation moves the existing downstream Error attachment to reserve with all other shapes valid.
// Bypass matrix uses a governed-human Timer alternative and existing exclusive merge shape:
// positive control reserve -> human -> downstream; Timer and downstream Error both reach merge -> throw.
// move the same human before reserve: Timer bypasses original normal edge; reject despite valid arities/refs.
// Matrix: explicitfalse/One/true attrs; unknown/missing/multi syntax; recursive/disconnected/cycle/caps;
// original/handler mappings, ordinary boundaries, old constructors/xml defensive copy and old models.
```

- Verification contribution: G1 presence RED; G2 converter compile-positive; G3 typed RED; G4 model GREEN
- After this file: G1 must be expected reflection/resource F, E0/S0; no production file or fixture changed yet.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`

- Purpose: Append immutable metadata while retaining every old constructor descriptor.
- Symbols: WorkerCompensationVO; workerCompensations; new terminal overload
- Repository evidence: ValidatedProcessModel existing final fields/Map.copyOf/xml.clone and constructor chain.
- Dependencies and consumers: File1 reflection RED; no validator behavior yet.
- Why now: Type/getter presence is established by a real assertion RED; this is compile A only.
- Contract/signature changes: New package terminal takes extra Map; old terminal delegates Map.of(); public getter; record grants no authority.
- Input/output and state mapping: Validator later supplies originalId->sixIDs; all prior constructors yield empty compensation map.
- Error and edge behavior: Retain existing copy/null behavior; no new general constructor domain-validation layer.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 3, Rule 5, Rule 11 — semantic VO, copy map/XML and preserve all constructor descriptors; no entity/mapper.
- Implementation pseudocode:

```java
private final Map<String,WorkerCompensationVO> workerCompensations;
public record WorkerCompensationVO(String originalWorkerId,String boundaryEventId,
    String associationId,String compensationWorkerId,String throwEventId,String endEventId) { }
public Map<String,WorkerCompensationVO> workerCompensations() { return workerCompensations; }
// Preserve every old descriptor. Old terminal delegates the new terminal with Map.of().
// New terminal copies all original assignments exactly and then:
this.workerCompensations = Map.copyOf(workerCompensations);
// xml() still returns clone; no metadata constructor becomes release approval.
```

- Verification contribution: G2 compile; G4 constructor/immutability regressions
- After this file: New VO/map/getter and old descriptors compile; resource missing is still expected until File3.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-compensation.bpmn20.xml`

- Purpose: Supply one exact valid original/undo error-triggered fixture.
- Symbols: workerCompensation/reserve/reserveCompBoundary/reserveCompAssociation/undoReserve/downstream/throwUndo/compEnd
- Repository evidence: Existing worker-business-error/mapping fixture namespace and ordinary Worker attributes.
- Dependencies and consumers: Model A then actual converter-positive gate; no validator whitelist change yet.
- Why now: The metadata descriptor is now available; pure converter proof isolates XML errors before validator behavior RED.
- Contract/signature changes: Only initial operationId STRING; no output; ordinary effect registry semantics retained.
- Input/output and state mapping: start->reserve->downstream->normalEnd; downstream Error->throwUndo->compEnd; detached handler associated to original.
- Error and edge behavior: Comp boundary is process sibling; no normal flow to boundary/handler; dedicated end only from throw.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 2, Rule 5, Rule 11 — fixed grammar/namespace/initial mapping, no expression or new dependency.
- Implementation pseudocode:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             xmlns:tianji="urn:stellar-enclosure:tianji:worker:1"
             targetNamespace="https://stellar-enclosure.example/test">
    <error id="downstreamError" errorCode="RESERVATION_REJECTED"/>
    <process id="workerCompensation" isExecutable="true">
        <startEvent id="start"/>
        <sequenceFlow id="toReserve" sourceRef="start" targetRef="reserve"/>
        <serviceTask id="reserve" flowable:type="external-worker" flowable:topic="reserveBusiness:1"
                     flowable:doNotIncludeVariables="true" tianji:topic="reserveBusiness"
                     tianji:schemaVersion="1" tianji:effectSafety="TRANSACTIONAL_RECEIPT">
            <extensionElements>
                <flowable:externalWorkerInParameter source="operationId" target="operationId" tianji:type="STRING"/>
            </extensionElements>
        </serviceTask>
        <boundaryEvent id="reserveCompBoundary" attachedToRef="reserve" cancelActivity="false">
            <compensateEventDefinition/>
        </boundaryEvent>
        <sequenceFlow id="reserveNormal" sourceRef="reserve" targetRef="downstream"/>
        <serviceTask id="downstream" flowable:type="external-worker" flowable:topic="downstreamRejectable:1"
                     flowable:doNotIncludeVariables="true" tianji:topic="downstreamRejectable"
                     tianji:schemaVersion="1" tianji:effectSafety="TRANSACTIONAL_RECEIPT">
            <extensionElements>
                <flowable:externalWorkerInParameter source="operationId" target="operationId" tianji:type="STRING"/>
            </extensionElements>
        </serviceTask>
        <sequenceFlow id="downstreamNormal" sourceRef="downstream" targetRef="normalEnd"/>
        <endEvent id="normalEnd"/>
        <boundaryEvent id="downstreamRejected" attachedToRef="downstream" cancelActivity="true">
            <errorEventDefinition errorRef="downstreamError"/>
        </boundaryEvent>
        <sequenceFlow id="toThrowUndo" sourceRef="downstreamRejected" targetRef="throwUndo"/>
        <intermediateThrowEvent id="throwUndo">
            <compensateEventDefinition activityRef="reserve" waitForCompletion="true"/>
        </intermediateThrowEvent>
        <sequenceFlow id="toCompEnd" sourceRef="throwUndo" targetRef="compEnd"/>
        <endEvent id="compEnd"/>
        <serviceTask id="undoReserve" isForCompensation="true" flowable:type="external-worker"
                     flowable:topic="undoReservation:1" flowable:doNotIncludeVariables="true"
                     tianji:topic="undoReservation" tianji:schemaVersion="1"
                     tianji:effectSafety="TRANSACTIONAL_RECEIPT">
            <extensionElements>
                <flowable:externalWorkerInParameter source="operationId" target="operationId" tianji:type="STRING"/>
            </extensionElements>
        </serviceTask>
        <association id="reserveCompAssociation" sourceRef="reserveCompBoundary"
                     targetRef="undoReserve" associationDirection="One"/>
    </process>
</definitions>
```

- Verification contribution: G2 real pure converter positive; G3/G4 validator typed matrix
- After this file: Presence and real converter-positive checks pass; typed validator behavior remains absent and yields G3 expected F.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`

- Purpose: Extend existing validator with exact profile and all-edge normal-success proof.
- Symbols: validateBpmn; private IncomingEdge; FlowNode.incomingEdges/originalNormalBefore; private compensationProfile helper and private CompensationProfile(WorkerCompensationVO metadata,String originalNormalFlowId)
- Repository evidence: Existing90-325 parser/toposort; actual FlowNode fields657-669 and workerTask helpers.
- Dependencies and consumers: G3 genuine assertion RED; File2 metadata and File3 converter already positive.
- Why now: Only after expected typed failures may the whitelist and existing graph proof expand.
- Contract/signature changes: Public validateBpmn unchanged; only local private seams plus final compensation map.
- Input/output and state mapping: XML closed shape -> complete pair -> exclude exactly2 validated detached nodes -> preserve existing topo -> approved VO.
- Error and edge behavior: No relaxation of old models/implicit edges/paths/caps; error remains current safe invalid().
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11 — local exact grammar and graph dataflow, old Timer facts and current technical profile preserved.
- Implementation pseudocode:

```java
// Add only closed process association/intermediateThrowEvent, compensate definition and approved attrs.
// Parse associations separately, never as FlowNode; require globally unique id.
// serviceTask isForCompensation if present must literaltrue on external-worker; original absent.
// Comp boundary requires false and one empty compensate definition; no activityRef/wait attributes on it. Old Error/Timer still true.
// Capture each explicit incoming edge (predecessor, sequenceFlowId); implicit Error/Timer edges use null.
record IncomingEdge(FlowNode previous,String explicitSequenceFlowId) { }
// New private profile resolves exactlyone boundary/association/handler/throw, distinct ordinary workers,
// pair inputs List.of(new WorkerParameter("operationId","operationId",ValueType.STRING)), no outputs,
// handler no normal edges/own boundary/message wait; One source/target; throw exactref/true/1in1out/end.
// Preserve parsed cap128nodes/256flows before exclusion and workers<=16 etc.
// Capture original's unique explicit normal outgoing sequence-flow id BEFORE implicit alternatives.
CompensationProfile profile = compensationProfile(nodes,workers,associations,
    compensationBoundaryAttachments,compensationWorkerIds,compensationThrowIds,
    errorBoundaryAttachments,humanTimers); // null only when no compensation syntax
Map<String,FlowNode> normalNodes = profile == null ? nodes : new LinkedHashMap<>(nodes);
// Remove only fully validated compensation boundary and handler; keep handler in workers metadata.
// Run existing shape/gateway/message/Case checks, preserving waiting-worker/internal release obligations.
// After existing waitingWorkers mapping, reject original/handler in that set; keep internal registry gate.
// Preserve existing Error and Timer next/previous/incoming increments and additionally record edge identity.
// In the SAME topological pass, add the fact only for a validated compensation profile:
if (profile != null) {
  node.originalNormalBefore = !node.incomingEdges.isEmpty() && node.incomingEdges.stream().allMatch(edge ->
      edge.previous().originalNormalBefore || profile.originalNormalFlowId().equals(edge.explicitSequenceFlowId()));
}
// A no-compensation model never evaluates a null normal-edge ID; its old graph behavior remains.
// Keep decisionBefore, waitBefore, paths128, traversals256, visitedInputs and disconnected rejection.
// visited == normalNodes.size; require throw.originalNormalBefore when profile present.
// New terminal gets one compensation map; no syntax means empty map and old behavior.
```

- Verification contribution: G4 seven owning model suites; explicit Error/Timer, self-error/bypass/old data regressions
- After this file: G4 seven named suites pass with old model preservation; current source logic complete, governance/commit pending.

#### File 5 — `CREATE docs/egon/spec/2026-11-05-12-09-worker-compensation-fixture.md`

- Purpose: Publish accepted effective specification with only related-plan metadata change.
- Symbols: Primary Spec; RelatedPlans backlink
- Repository evidence: Root full1031line corrected design SHA4504... and independent strict PASS.
- Dependencies and consumers: All core source GREEN; Root records normative and linked SHA.
- Why now: Source GREEN and exact linked identity make the effective governance artifact reviewable with this commit.
- Contract/signature changes: No normative mutation, preserve Review and runtime boundaries.
- Input/output and state mapping: Exact accepted bytes -> repository Spec path.
- Error and edge behavior: Any material design change returns to Spec before source further edits.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 11 — publish exact reviewed technical profile; other rules remain independently mapped in the Spec.
- Implementation pseudocode:

```text
read accepted normative Spec and verify SHA4504...
assert diff of linked copy only Updated and Related Plans
copy reviewed linked bytes to exact repository Spec path
run strict Spec validator; record actual result
```

- Verification contribution: Two strict validators; SHA/delta review
- After this file: Effective Spec at real path matches exact linked SHA; remaining Plan/EVD and source gate still pending.

#### File 6 — `CREATE docs/egon/plan/2026-11-05-12-44-worker-compensation-fixture.md`

- Purpose: Publish this exact reviewed executable Plan.
- Symbols: Two ordered Steps/13paths/G1-G12
- Repository evidence: Root authoring against current b63 source/Spec and peer checklist.
- Dependencies and consumers: All Step1 source GREEN, full Root review and actual command audit.
- Why now: The Plan is already reviewed; commit its exact bytes only with current-Step source evidence.
- Contract/signature changes: Review status preserved; completed execution facts are EVD, not fake prior acceptance.
- Input/output and state mapping: Exact reviewed Plan -> repository governance artifact.
- Error and edge behavior: No new scope or posthoc weakened test criteria.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 11 — consume approved technical profile without new module/schema/generator.
- Implementation pseudocode:

```text
read exact Root-reviewed Plan and verify SHA
copy bytes to declared Plan path without changing normative requirements
validate strict Plan and Spec against real paths
verify ordered file list and source scope before staging
```

- Verification contribution: Strict Plan/Spec and exact path review
- After this file: Both effective docs present, strict pass and exact current change scope known; EVD pending.

#### File 7 — `CREATE docs/egon/evidence/2026-11-05-worker-compensation-model.md`

- Purpose: Record actual model gates and current-Step conformance.
- Symbols: G1-G6; REQ701-707/716-718; 17MC/10Rules
- Repository evidence: Existing evidence documents distinguish source and runtime.
- Dependencies and consumers: Actual G4 GREEN/reports/strict/hash checks.
- Why now: Actual reports and source hashes now exist, so the evidence can report observations instead of expectations.
- Contract/signature changes: No runtime claim; report actual counts and failed fixture/compile corrections honestly.
- Input/output and state mapping: Current commands/reports + excluded paths/SQL SHA -> auditable evidence.
- Error and edge behavior: No expected test count fabricated; missing reports/skips block commit.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11 — record actual per-rule proof; Rule 4, Rule 6, Rule 7 record exact scoped N/A.
- Implementation pseudocode:

```text
read actual G1-G4 logs and current named XML reports
record assertion failures/compile corrections distinctly, actual GREEN totals
compare exact7 paths, excluded tracked hashes and25 old SQL
record every Manual Check and literal Rule with actual evidence
retain runtime-unverified and Stage2 remaining boundary
```

- Verification contribution: Root final staged-tree/7path check
- After this file: Actual 17MC/10Rules and 7-path/25SQL audit close; Root makes one Step1 commit before Step2 edits.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: §8 exact gated commands; final selector `WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest`.
- Expected result: Every named owning report present, 0failures/errors/skips; actual test total recorded, not prefilled.
- Failure returns to: Owning File/test fixture/API seam; material design mismatch returns to Spec, never weaken assertion.
- Completion criteria: Every row below closed, strict docs/whitespace/exact scope/old SQL checks and one path-limited commit.
- Rollback: Revert only this semantic commit or forward-fix; no shared history rewrite/data repair.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/WorkerCompensationModelTest.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`; `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/worker-compensation.bpmn20.xml`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`; `docs/egon/spec/2026-11-05-12-09-worker-compensation-fixture.md`; `docs/egon/plan/2026-11-05-12-44-worker-compensation-fixture.md`; `docs/egon/evidence/2026-11-05-worker-compensation-model.md`
- Commit: `feat(process): validate bounded worker compensation`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing technical PS vertical; exactly two production release classes, same module/profile. Actual evidence required; failure blocks commit. |
| MC-REUSE-001 | Applicable | Existing DOM/toposort/model/Store/handler/ValidationUtils/digest/JDBC reused. Actual evidence required; failure blocks commit. |
| MC-DEP-001 | Applicable | No POM/dependency addition; PS has no Lombok project dependency and no new logger is introduced. Actual evidence required; failure blocks commit. |
| MC-NAME-001 | Applicable | Semantic WorkerCompensationVO, ExpectedOperationVO/ReceiptVO and focused test/helper names. Actual evidence required; failure blocks commit. |
| MC-VALID-001 | Applicable | Closed DOM, immutable approved output, annotation Default and fixture-local before-any-JDBC scope/topic/schema/effect guards. Actual evidence required; failure blocks commit. |
| MC-MODEL-001 | Applicable | Defensive metadata/row snapshots, same-package mapping and all old constructor descriptors. Actual evidence required; failure blocks commit. |
| MC-CONVERT-001 | Not applicable | No cross-layer PO/DTO conversion; same-package row snapshots only. Actual evidence required; failure blocks commit. |
| MC-LOG-001 | Not applicable | Exact Root DEC-710 technical test-helper scope; no new business class/Bean/logger. Future production business rules remain. Actual evidence required; failure blocks commit. |
| MC-BEAN-001 | Not applicable | No new Spring Bean or DI surface; test value constructor is not injected. Actual evidence required; failure blocks commit. |
| MC-UTIL-001 | Applicable | Existing framework/JDK/Common helpers; no custom utility library. Actual evidence required; failure blocks commit. |
| MC-JSON-001 | Not applicable | Existing WorkerBusinessResultDTO with empty output reused; no new wire JSON/enum. Actual evidence required; failure blocks commit. |
| MC-TIME-001 | Applicable | No new time field in this Step; verify preservation with existing Timer regressions; new time boundary belongs Step 2. Actual evidence required; failure blocks commit. |
| MC-CONFIG-001 | Not applicable | All profile/default configuration files unchanged. Actual evidence required; failure blocks commit. |
| MC-PATTERN-001 | Applicable | Existing handler Strategy and Store-owned transaction lifecycle; local graph dataflow, no new scheduler/registry. Actual evidence required; failure blocks commit. |
| MC-SCOPE-001 | Applicable | Exact7/6 paths, excluded tracked SHA and all25 SQL immutable. Actual evidence required; failure blocks commit. |
| MC-TEST-001 | Applicable | Real assertion RED/A/typedRED/B/GREEN, exact named reports and explicit no-runtime boundary. Actual evidence required; failure blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before commit; future runtime acceptance not claimed. Actual evidence required; failure blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Semantic technical value/handler/test names; no entity scaffolding. Record actual PASS or scoped N/A independently. |
| Rule 2 | Applicable | Validate closed XML and each real handler boundary; Default annotation metadata plus stateful TX checks. Record actual PASS or scoped N/A independently. |
| Rule 3 | Applicable | Only immutable values use records; defensive copies; no entity or conversion bypass. Record actual PASS or scoped N/A independently. |
| Rule 4 | Not applicable | Spec DEC-710 exact technical test-helper/unchanged main profile; no new logger/Bean/Lombok/POM, not a general production waiver. Record actual PASS or scoped N/A independently. |
| Rule 5 | Applicable | JDK/current framework/common types only. Record actual PASS or scoped N/A independently. |
| Rule 6 | Not applicable | No new external JSON/enum/DTO; existing contract unchanged. Record actual PASS or scoped N/A independently. |
| Rule 7 | Not applicable | No profile/configuration key changes. Record actual PASS or scoped N/A independently. |
| Rule 9 | Applicable | Existing Strategy+Store lifecycle for two actions; cohesive private graph proof. Record actual PASS or scoped N/A independently. |
| Rule 10 | Applicable | Preserve existing java.time Timer behavior in Step 1; new TIME701/702 mapping belongs Step 2. Record actual PASS or scoped N/A independently. |
| Rule 11 | Applicable | Preserve current approved technical profile/Components; no MP/DDLrunner/generator/business module. Record actual PASS or scoped N/A independently. |

### Step 2 — Prove the test-only business receipt protocol

- Requirements: REQ-708, REQ-709, REQ-710, REQ-711, REQ-712, REQ-713, REQ-714, REQ-715, REQ-716, REQ-717, REQ-718
- Dependencies: Exact Step1 semantic commit, clean tree and fresh baseline lock
- Baseline state: Closed model and metadata committed; no BusinessFixture or scenario hook exists
- Observable outcome: Finite guard/replay/CAS/time protocol proved; future opt-in source compiles without execution
- End state: Exact6 owned files, named finite tests GREEN, docs/17MC/10Rules/excluded/25SQL audit and one commit.
- Test-first gate: Required — missing surface assertion then typed missing behavior; onlyJUnitF/E0/S0 accepted.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — every row independently closed; Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixtureTest.java`

- Purpose: Lock fixture guards/replay/CAS/time contract before its implementation.
- Symbols: CompensationBusinessFixtureTest; presence then typed guard/RC/original/undo/replay/time methods
- Repository evidence: Current JUnit/Mockito tests and Worker contract APIs; pure finite mocks.
- Dependencies and consumers: Step1 committed; initial reflection has no new fixture compile dependency.
- Why now: Step1 is committed; first expose the missing test fixture surface without a compiler dependency.
- Contract/signature changes: Presence RED -> File2 A signatures -> positive assertDoesNotThrow typed RED.
- Input/output and state mapping: Expected enrollment/context/same bound RC connection and controlled rows -> exact JDBC sequence/result/errors.
- Error and edge behavior: Fresh rig per failure; scope/topic/schema/effect failure JDBC/DataSource/Connection0; no claim mock SQL proves ACID.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11 — finite enrollment/RC/TX/replay/time coverage; no unrelated framework.
- Implementation pseudocode:

```java
// Phase0 Class.forName fixture assertion -> G7 missing-type F.
// After File2 A: each positive setup/strategy acquisition uses assertDoesNotThrow and nonnull assertions.
// Bind actual TransactionSynchronizationManager resource to mock JdbcTemplate DataSource/ConnectionHolder.
// Guard matrix: BPMN/nullcase/source/tenant/process/business/corr/input/topic/schema/effect mismatch -> zero JDBC/connection.
// Wrong/no bound TX/DS/holder/nonRC -> business SQL0; actual bound Connection used, no borrowed connection.
// Original: existing FOR UPDATE replay; absent -> object FOR UPDATE -> DB deadline -> plain receipt reread.
// Controlled reread now returns original: assert no CAS/insert/no reverse FOR UPDATE.
// First reserve exact checked version/reserved CAS then original insert; failures/overflow throw/no success.
// Undo: original FOR UPDATE -> deadline -> prior lookup; prior replay -> zero object query even after version evolves.
// Firstundo stable original-derived digest, object lock/deadline/CAS restore and version+1/comp insert.
// Time OffsetDateTime different offsets -> same Instant, microseconds, equality deadline rejects.
// Missing/binding-invalid rows/0update/insert exceptions all fail; no fabricated receipt.
```

- Verification contribution: G7/G8 genuine RED; G9 finite fixture GREEN
- After this file: G7 reflection assertion fails for missing fixture; no source fixture/scenario exists yet.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixture.java`

- Purpose: Implement isolated test-only receipt protocol using existing Store transaction.
- Symbols: CompensationBusinessFixture; ExpectedOperationVO/OriginalReceiptVO/CompensationReceiptVO; originalHandler/undoHandler; private guards/mappers; schema provision helper
- Repository evidence: Actual ValidationUtils instance; ConnectionHolder public API; existing Worker execution/digest/exception types.
- Dependencies and consumers: G7 A: expose values/signatures safely; G8 typed assertion RED before B.
- Why now: Reflection RED fixes the absence, then A enables typed behavior assertions before receipt logic.
- Contract/signature changes: No production/Bean/logger/API/Store/POM change; existing TransactionalWorkerHandler and WorkerBusinessResultDTO only.
- Input/output and state mapping: Guarded enrollment -> same supplied JdbcTemplate/RC -> immutable persisted receipt -> empty-output DTO.
- Error and edge behavior: No second connection/TM/manual commit/remote effect; no reverse lock; unchanged technical receipt owned by Store.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11 — immutable values/Default validation, existing Handler Strategy/Store TX and java.time; Rule 4 exact test-helper N/A.
- Implementation pseudocode:

```java
// A: final test helper/types/signatures; positive ctor shape, handler getters fail safely until typed RED.
// B:
ExpectedOperationVO expected = validationUtils.validate(input); // same Default annotation metadata
// sourceId regex [A-Za-z0-9][A-Za-z0-9_.:-]{0,127}; @Positive tenant/amount, required UUID/string IDs,
// @PositiveOrZero expectedVersion/expectedReserved; no normalization.
void requireContextBeforeJdbc(WorkerExecutionContext c,String expectedTopic) {
  // c nonnull, BPMN, null case, exact source/tenant/process/business/correlation/input operation UUIDcanonical;
  // expectedTopic reserveBusiness or undoReservation; schema1; TRANSACTIONAL_RECEIPT.
}
JdbcTemplate requireBoundRc(JdbcOperations operations) {
  // require supplied JdbcTemplate, actual TX, same DataSource bound ConnectionHolder;
  // holder.getConnection().getTransactionIsolation()==Connection.TRANSACTION_READ_COMMITTED;
  // no DataSource.getConnection, no setIsolation/begin/commit. Reject safely, no business SQL before proof.
}
void deadlineAfterLock(JdbcOperations jdbc,Instant deadline) {
  Instant now = jdbc.queryForObject("SELECT clock_timestamp()",OffsetDateTime.class).toInstant();
  if (!now.isBefore(deadline)) throw new WorkerExecutionDeadlineException();
}
TransactionalWorkerHandler originalHandler() { return (c,jdbcOps) -> {
  requireContextBeforeJdbc(c,RESERVE_TOPIC); JdbcTemplate jdbc=requireBoundRc(jdbcOps);
  // original_receipt WHERE source/tenant/operation FOR UPDATE;
  // existing -> deadline + full binding/arithmetic -> persisted result.
  // absent -> business_object exact FOR UPDATE -> deadline -> plain SELECT immutable original receipt;
  // visible -> full binding replay BEFORE comparing object version; never reverse FOR UPDATE.
  // still absent -> checked add version/reserve; guarded UPDATE RETURNING exactly1;
  // INSERT original_receipt RETURNING recorded_at; return businessReceiptId and Map.of().
}; }
TransactionalWorkerHandler undoHandler() { return (c,jdbcOps) -> {
  requireContextBeforeJdbc(c,UNDO_TOPIC); JdbcTemplate jdbc=requireBoundRc(jdbcOps);
  // original FOR UPDATE -> deadline -> full original binding/arithmetic;
  // key=ProcessRequestDigest.hash(List.of("worker-compensation-v1",source,Long.toString(tenant),
  //     originalReceiptId,"UNDO_RESERVATION_V1"),Map.of());
  // prior compensation lookup+full validation -> original result BEFORE object query;
  // none -> object FOR UPDATE -> deadline -> exact original after-state;
  // restore original.beforeReserved, version=current+1; CAS1; INSERT comp receipt; output Map.of().
}; }
// SQL DDL: schema compensation_fixture, exact three tables/complete columns+PK/UK/FK/checks in Spec§11.
// Strings use COLLATE "C"; timestamptz(6) DEFAULT clock_timestamp(); all bindings parameterized.
// original_receipt PK(source,tenant,operation), UK(source,tenant,originalReceiptId), FK object;
// compensation PK(source,tenant,key), UK(source,tenant,originalReceiptId,undo_action), FK original;
// checks positive/monotonic versions, nonnegative reserve, fixedaction and64lowerhex; no receipt UPDATE/DELETE.
// Provision only future guarded absent loopback schema; apply existing W V1/V2 migrations unchanged.
// After the three tables, bootstrap installs the test-only original-receipt write-once guard:
// CREATE FUNCTION compensation_fixture.reject_original_receipt_update() RETURNS trigger
//   LANGUAGE plpgsql SET search_path TO pg_catalog AS $$
//   BEGIN RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='Original compensation receipt is immutable'; END; $$;
// CREATE TRIGGER original_receipt_immutable_update BEFORE UPDATE ON compensation_fixture.original_receipt
//   FOR EACH STATEMENT EXECUTE FUNCTION compensation_fixture.reject_original_receipt_update();
// ALTER TABLE compensation_fixture.original_receipt ENABLE ALWAYS TRIGGER original_receipt_immutable_update;
// No SECURITY DEFINER/RLS/new table; apply before granting role privileges. Row-lock SELECT is not UPDATE.
// Runtime grants: object SELECT/UPDATE; both business receipts SELECT/INSERT; original only UPDATE(recorded_at)
// for row-lock privilege, no other UPDATE/DELETE/TRUNCATE/TRIGGER. Dedicated role non-owner/no privileged membership.
// tianji_worker_execution_receipt SELECT/INSERT; tianji_worker_nontransactional_intent SELECT;
// tianji_worker_schema_history SELECT only. No runtime schema CREATE/history mutation.
```

- Verification contribution: G9 unit assertions; G10 source compile; no real ACID/runtime claim
- After this file: After A/G8/B, G9 proves finite guard/replay/CAS/RC/time; scenario/PG still untouched.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/WorkerCompensationFlowableScenarios.java`

- Purpose: Author future manual native/business scenario without auto execution.
- Symbols: public static verifyIfConfigured(ConfigurableApplicationContext,JdbcTemplate,String) throws Exception
- Repository evidence: Actual ProcessServerPostgresTest outer app/jdbc/runtimeUser scope; existing guarded Worker business helper.
- Dependencies and consumers: Fixture G9 GREEN; current released model after Step1.
- Why now: Finite receipt/guard behavior is GREEN before adding the optional source-only runtime consumer.
- Contract/signature changes: Public test helper across packages, no @Test; no production entry.
- Input/output and state mapping: Explicit user PG process/business URL+roles -> owned absent schema and actual fixture handlers -> separately observable native/business facts.
- Error and edge behavior: No URL means return without provisioning; invalid URL/role/existing schema fails closed; no unknown cleanup/adoption.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 2, Rule 5, Rule 9, Rule 10, Rule 11 — explicit opt-in guard and existing transaction/strategy owners; no new production component.
- Implementation pseudocode:

```java
public static void verifyIfConfigured(ConfigurableApplicationContext app,
    JdbcTemplate processBootstrap,String processRuntimeUser) throws Exception {
  // Existing process URL+runtime role guard already passed; require exact owning app/source boundary.
  // if business URL absent return; else require literal fresh loopback URL regex from existing test;
  // require business role tianji_it_worker_[0-9a-f]{12}, process role tianji_it_runtime_[0-9a-f]{12}.
  // No additional URL params/unknown schemas; compensation_fixture absent; track schema creation ownership.
  // Register exact ordinary reserveBusiness/undoReservation/downstreamRejectable schema1 via existing Registration.
  // inputs {operationId:STRING}, outputs {}, inputSources {operationId:{operationId}}, no internalAgentDispatch.
  var process = app.getBean(SpringProcessEngineConfiguration.class);
  var dmn = app.getBean(SpringDmnEngineConfiguration.class);
  var validator = new ProcessModelValidator();
  var releases = new ProcessReleaseService(process,dmn,validator,false,registry);
  var runs = new ProcessExecutionService(process,dmn,validator);
  var workers = new FlowableWorkerService(process,dmn,validator,registry);
  // Use existing worker registrations/owning Source patterns; never assume optional Worker beans exist.
  // Provision owned schema/three business tables and unchanged Worker V1/V2, restricted runtime role plus original-receipt trigger/column grant.
  // Run future original business commit -> accepted native complete -> actual normalleave/subscription;
  // FailWorkerTaskCommand with WorkerFailureKindEnum.BUSINESS_REJECTED -> fixed native error;
  // wait bounded for actual undo task; execute through JdbcWorkerExecutionStore.execute(task,descriptor,handler).
  // Return/ack only real WorkerExecutionReceipt for actual leased native tasks.
  // Separate local store concurrency probes may use synthetic different technical task/occurrence/key with the
  // same enrolled scope; never submit synthetic capabilities or acknowledgements to Process Server.
  // On original_receipt: no table-level UPDATE, recorded_at column UPDATE only, no DELETE/TRUNCATE/TRIGGER/ownership.
  // After original is persisted, run actual scoped SELECT FOR UPDATE in rollback/read-write probe;
  // direct recorded_at UPDATE must hit SQLSTATE23514, not mere permission denial; always roll back probe,
  // assert persisted value unchanged. Keep role ownership/membership guards and trigger ALWAYS status.
  // Assert original/comp/technical receipts, row version/reserve, replay after ackloss/newtask;
  // RC concurrent original window, compensation duplicate, technical insert-failure rollback and persistence.
  // Observe native outcome separately; never infer undo success from wait getter/throw end/ack.
  // Cleanup only this invocation's proven owned schema/resources; no user/unknown schema adoption/drop.
}
// This source is only compiled here. Root never configures URL or selects its caller.
```

- Verification contribution: G10 test-source compilation; future PG-701 remains user-owned/unexecuted
- After this file: Test-only future helper compiles against real current APIs; no caller runs it.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`

- Purpose: Add guarded hook in the actual existing main PG test scope.
- Symbols: Existing main test app scope; WorkerCompensationFlowableScenarios call
- Repository evidence: verifyWorkerAdapter(app,jdbc) at632; outer scopedUrl/runtimeUser declared in main test.
- Dependencies and consumers: Scenario helper exists and compiles; never selected in this task.
- Why now: The helper exists first; only then connect it at the actual guarded caller.
- Contract/signature changes: No new @Test/optin/default server behavior; existing optional Worker helper unchanged.
- Input/output and state mapping: When existing process guard passed and business URL nonnull, call exact3args.
- Error and edge behavior: Do not refer runtimeUser inside verifyWorkerAdapter(app,bootstrap), where absent.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 2, Rule 5, Rule 11 — existing test guards and helper call only; no configuration/source runtime change.
- Implementation pseudocode:

```java
// Inside existing outer try(app=context(...scopedUrl,runtimeUser)), near current verifyWorkerAdapter(app,jdbc):
if (System.getProperty("tianji.worker.business.postgres.test.url") != null) {
    WorkerCompensationFlowableScenarios.verifyIfConfigured(app,jdbc,runtimeUser);
}
// Preserve prior worker/error/case/expiry tests, runtime guards and all existing migrations.
// No default property, unconditional setup, listener or new test selection.
```

- Verification contribution: G10 compile all test source; scope audit shows PG caller not run
- After this file: Existing PG class and helper compile in G10; none of the PG tests/opt-ins executed.

#### File 5 — `MODIFY README.md`

- Purpose: Explain exact finite commands and manual acceptance boundaries.
- Symbols: README worker compensation test section
- Repository evidence: Existing README testing/opt-in guidance.
- Dependencies and consumers: Final source and exact command results available.
- Why now: The complete source path and exact commands are now known for reproducibility.
- Contract/signature changes: No claim installed/live acceptance; no new runtime defaults.
- Input/output and state mapping: Users can reproduce finite suite; future isolated PG requires deliberate explicit env.
- Error and edge behavior: Never show skipped or compiled source as passed; no real payments/email side effects.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 11 — document exact existing technical profile and test-only scope; no generated/production schema action.
- Implementation pseudocode:

```text
add finite command using exact selector and skipITs
identify explicit future isolated process/business URLs and role guards
explain source-only PG fixture and separate native/business receipt observations
state live acceptance unexecuted; do not imply default application startup
```

- Verification contribution: README consistency/source scope
- After this file: User can locate exact finite commands and deliberate future PG prerequisites; EVD and commit remain.

#### File 6 — `CREATE docs/egon/evidence/2026-11-05-worker-compensation-fixture.md`

- Purpose: Record actual full fixture and aggregate conformance.
- Symbols: G7-G12 reports; REQ708-718; aggregate13paths; 17MC/10Rules
- Repository evidence: Existing evidence format; Step1 committed evidence.
- Dependencies and consumers: Final finite reports/strict/excluded hash audit.
- Why now: Only actual final finite/compile/scope evidence may be written into the closing EVD.
- Contract/signature changes: All claims bind actual named reports; no runtime acceptance.
- Input/output and state mapping: Actual guard/replay/row-order results plus immutable schema/POM/config proof.
- Error and edge behavior: Compile-only PG source not a skip/pass; disclose corrections and remaining runtime work.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; N/A rows use Spec exact technical scope.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11 — actual per-rule proof; Rule 4, Rule 6, Rule 7 record exact scoped N/A.
- Implementation pseudocode:

```text
read actual G7-G10 log and named XML reports
record fixture guards, RC/order/replay/CAS/time results with actual counts
check exact6 and aggregate13 paths, excluded tracked hashes and25 immutable SQL
record each Manual Check and literal Rule independently
close source-only scope; list live/remaining master requirements unexecuted
```

- Verification contribution: Root exact6-path staging/semantic commit
- After this file: G11 closes all checks; Root commits exact6, verifies aggregate13 and keeps runtime unverified.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: §8 exact gated commands; final selector `CompensationBusinessFixtureTest,WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,RemoteWorkerPollerTest,WorkerDependencyBoundaryTest`.
- Expected result: Every named owning report present, 0failures/errors/skips; actual test total recorded, not prefilled.
- Failure returns to: Owning File/test fixture/API seam; material design mismatch returns to Spec, never weaken assertion.
- Completion criteria: Every row below closed, strict docs/whitespace/exact scope/old SQL checks and one path-limited commit.
- Rollback: Revert only this semantic commit or forward-fix; no shared history rewrite/data repair.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixtureTest.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/CompensationBusinessFixture.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/compensation/WorkerCompensationFlowableScenarios.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`; `README.md`; `docs/egon/evidence/2026-11-05-worker-compensation-fixture.md`
- Commit: `test(process): add transactional compensation receipts`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing technical PS vertical; exactly two production release classes, same module/profile. Actual evidence required; failure blocks commit. |
| MC-REUSE-001 | Applicable | Existing DOM/toposort/model/Store/handler/ValidationUtils/digest/JDBC reused. Actual evidence required; failure blocks commit. |
| MC-DEP-001 | Applicable | No POM/dependency addition; PS has no Lombok project dependency and no new logger is introduced. Actual evidence required; failure blocks commit. |
| MC-NAME-001 | Applicable | Semantic WorkerCompensationVO, ExpectedOperationVO/ReceiptVO and focused test/helper names. Actual evidence required; failure blocks commit. |
| MC-VALID-001 | Applicable | Closed DOM, immutable approved output, annotation Default and fixture-local before-any-JDBC scope/topic/schema/effect guards. Actual evidence required; failure blocks commit. |
| MC-MODEL-001 | Applicable | Defensive metadata/row snapshots, same-package mapping and all old constructor descriptors. Actual evidence required; failure blocks commit. |
| MC-CONVERT-001 | Not applicable | No cross-layer PO/DTO conversion; same-package row snapshots only. Actual evidence required; failure blocks commit. |
| MC-LOG-001 | Not applicable | Exact Root DEC-710 technical test-helper scope; no new business class/Bean/logger. Future production business rules remain. Actual evidence required; failure blocks commit. |
| MC-BEAN-001 | Not applicable | No new Spring Bean or DI surface; test value constructor is not injected. Actual evidence required; failure blocks commit. |
| MC-UTIL-001 | Applicable | Existing framework/JDK/Common helpers; no custom utility library. Actual evidence required; failure blocks commit. |
| MC-JSON-001 | Not applicable | Existing WorkerBusinessResultDTO with empty output reused; no new wire JSON/enum. Actual evidence required; failure blocks commit. |
| MC-TIME-001 | Applicable | Fixture DB write time and contract deadline per Spec TIME701/702; real skew/ACID unexecuted. Actual evidence required; failure blocks commit. |
| MC-CONFIG-001 | Not applicable | All profile/default configuration files unchanged. Actual evidence required; failure blocks commit. |
| MC-PATTERN-001 | Applicable | Existing handler Strategy and Store-owned transaction lifecycle; local graph dataflow, no new scheduler/registry. Actual evidence required; failure blocks commit. |
| MC-SCOPE-001 | Applicable | Exact7/6 paths, excluded tracked SHA and all25 SQL immutable. Actual evidence required; failure blocks commit. |
| MC-TEST-001 | Applicable | Real assertion RED/A/typedRED/B/GREEN, exact named reports and explicit no-runtime boundary. Actual evidence required; failure blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before commit; future runtime acceptance not claimed. Actual evidence required; failure blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Semantic technical value/handler/test names; no entity scaffolding. Record actual PASS or scoped N/A independently. |
| Rule 2 | Applicable | Validate closed XML and each real handler boundary; Default annotation metadata plus stateful TX checks. Record actual PASS or scoped N/A independently. |
| Rule 3 | Applicable | Only immutable values use records; defensive copies; no entity or conversion bypass. Record actual PASS or scoped N/A independently. |
| Rule 4 | Not applicable | Spec DEC-710 exact technical test-helper/unchanged main profile; no new logger/Bean/Lombok/POM, not a general production waiver. Record actual PASS or scoped N/A independently. |
| Rule 5 | Applicable | JDK/current framework/common types only. Record actual PASS or scoped N/A independently. |
| Rule 6 | Not applicable | No new external JSON/enum/DTO; existing contract unchanged. Record actual PASS or scoped N/A independently. |
| Rule 7 | Not applicable | No profile/configuration key changes. Record actual PASS or scoped N/A independently. |
| Rule 9 | Applicable | Existing Strategy+Store lifecycle for two actions; cohesive private graph proof. Record actual PASS or scoped N/A independently. |
| Rule 10 | Applicable | Spec TIME701/702 UTC instant/write-time/deadline separation, no global-clock guarantee. Record actual PASS or scoped N/A independently. |
| Rule 11 | Applicable | Preserve current approved technical profile/Components; no MP/DDLrunner/generator/business module. Record actual PASS or scoped N/A independently. |

## 8. Test, Validation, and Quality Gates

Root alone executes these commands after source GO; this Plan has not run them. Base options:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=WorkerCompensationModelTest
```

Only replace the exact selector as listed; no wildcard, PG class, HttpProcessClientTest or property opt-in. Existing script appends test; future explicit verify is not needed for this source-only slice. All test sources still compile. Ordinary finite ProcessReleaseServiceTest uses mocks/parser objects, no engine startup.

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1 presence RED | service root | base selector WorkerCompensationModelTest, reflection phase | Missing map/type/resource | JUnit assertionF>0/E0/S0 | S1F1 | 701/707; no runtime |
| G2 compile A/converter | same | same after S1F2/3 and converter-positive phase | Metadata/fixture/public API | Presence+converter pass before typed validator assertions | S1F1-3 | 701-704/716 |
| G3 typed model RED | same | same full typed phase, validator unchanged | Closed profile/edge proof | New positive assertions fail; old cases green, E0/S0 | S1F1/4 | 701-707 |
| G4 model GREEN | same | base selector WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest | Seven exact suites | All named reports0F/E/S | S1F1-4 | 701-707/716/718 |
| G5 docs/scope | service/mirror | both strict validators, gitdiff/hash/17MC10Rules | S1exact7/25SQL | Actual PASS; no placeholders/extra paths | S1F5-7 | 717/718 |
| G6 Step1 commit | service root | Root exact7 stage/check/commit | Current Step complete | Commit blobs match reviewed hashes; clean | S1 | No S2edit beforecommit |
| G7 fixture presence RED | service root | base selector CompensationBusinessFixtureTest, reflection | Missing helper | Expected assertionF/E0/S0 | S2F1 | 708-714 |
| G8 A/typed RED | same | same after S2F2 A then typed tests | Guard/RC/receipt protocol | A compiles; positive typed assertionsF/E0/S0 | S2F1/2 | 708-715 |
| G9 fixture GREEN | same | same after S2F2 B | Finite mocks/ordering |0F/E/S; not real ACID | S2F1/2 | 708-715 |
| G10 source compile/regression | same | base selector CompensationBusinessFixtureTest,WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,RemoteWorkerPollerTest,WorkerDependencyBoundaryTest | Twelve explicit suites + allPGsource compilation | Named reports0F/E/S; PG not selected/no optin | S2F1-4 | 701-718/sourceonly |
| G11 docs/audit | actual repo | strictSpec/Plan, exact6/13aggregate/excluded/25SQL checks | README/EVD/all checks | Actual PASS/each17MC10Rule closed | S2F5/6 | 717/718 |
| G12 Step2 commit | service root | Root exact6 stage/check/commit | Full slice source | Commit tree SHA verified/clean | S2 | RuntimeUnverified |
| Future manual PG | User-owned environment | PG-701 Spec§14, explicit isolated URLs/roles | Actual native/BusinessDB/runtime | Separately observe all receipts/subscription/handler/restart/RC concurrency | Separate acceptance | Never run here |

Final source-only selector, verbatim command:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=CompensationBusinessFixtureTest,WorkerCompensationModelTest,ProcessModelValidatorTest,ProcessReleaseServiceTest,HumanTaskModelTest,HumanTaskTimerModelTest,CmmnModelValidatorTest,CmmnModelXmlTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,RemoteWorkerPollerTest,WorkerDependencyBoundaryTest
```

Strict documentation commands (run against eventual-path mirror before promotion and actual paths after):

```bash
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py docs/egon/spec/2026-11-05-12-09-worker-compensation-fixture.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-05-12-44-worker-compensation-fixture.md --strict
git diff --check
```

Each expected suite must appear in Surefire reports and current command output; failIfNoSpecifiedTests=false only permits non-owner reactor modules, never a missing owning selector. No new work harness is needed: current reactor plus focused tests cover all authorized finite behavior. Scripts/work disposition: Not applicable, no harness created. Future runtime source remains compiled but unexecuted, not reported as skipped or passed.

## 9. Migration, Compatibility, Rollout, and Rollback

Production migration: Not applicable (Primary§11/16); all25 existing SQL immutable, no generator/DDLrunner/repair. Existing Worker V1/V2 are only consumed unchanged by future explicitly owned test schema. No production database, app, activation, permissions or model deployment is performed. Old XML/schema/metadata constructor/Worker and C/CL contracts remain; new profile applies only to newly published XML. Reverting source cannot prove old native instances undone; runtime rollout/rollback belongs to later separate acceptance.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-701 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-702 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-703 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-704 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-705 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-706 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-707 | Primary§4/7/14 | Step1 | §5/7 owning exact paths | G1-G6 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-708 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-709 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-710 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-711 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-712 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-713 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-714 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-715 | Primary§4/7/14 | Step2 | §5/7 owning exact paths | G7-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-716 | Primary§4/7/14 | Both | §5/7 owning exact paths | G1-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-717 | Primary§4/7/14 | Both | §5/7 owning exact paths | G1-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |
| REQ-718 | Primary§4/7/14 | Both | §5/7 owning exact paths | G1-G12 | Actual named reports/EVD and scoped source hashes; runtimeunverified |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| RISK-701 | Native wait flag not business completion | Both/scenario | Fixed8.0.0 bytecode | Root/user runtime owner | Closed design; runtime remains unverified |
| RISK-702 | RC dependency or clock skew | Fixture | Actual Store isolation default/deadline source | Fixture owner | Check actual boundRC; no global hard deadline claim |
| RISK-703 | New annotation cannot compile from processor path alone | Fixture | PS no Lombok dependency/SpecDEC710 | Root | Closed: no new logger/Slf4j/POM, exact technical helper scope |
| RISK-704 | Unknown schema/data touched | Scenario | Existing loopback guards | Future test operator | Absent-owned-schema only; no execution here |
| RISK-705 | Test fake hides actual ACID/liveness | Fixture/scenario | Mock vs real boundary | Root/user | Explicit finite evidence; PG source only, user future acceptance |

No unresolved major design decision. This draft still requires strict validation and independent review before source GO. Source54/55 do not complete governed recovery, T3-12 or T4/whole goal.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

All18 primary requirements map to Steps and current source boundaries; full master goal remains intact. No runtime prohibition is traded away for acceptance. Declared slice is not whole T3-10 completion.

### 12.2 Spec consistency

Consume accepted exact-one grammar, normal-success edge AND proof, original receipt/object/plain-reread RC protocol and undo replay-before-object. No new API/table owner/production schema/logging layer. Only local implementation clarifications in§6.4; fixture privilege correction was resolved in Primary DEC-711 first, not invented in Plan. Material drift returns to Spec.

### 12.3 Repository executability

Every path is exact; per-file symbols/evidence/dependencies/pseudocode/errors and gate state are declared. Actual converter uses InputStreamProvider, actual model type ThrowEvent, actual FlowNode fields, ValidationUtils instance, boundConnection and outer3arg PG hook. Plan has not compiled/executed those future changes.

### 12.4 Test and release completeness

Assertion RED/compileA/converter/typedRED/minB/GREEN order and one semantic commit per Step. Explicit selectors avoid PG/listener/engine; old25SQL and all excluded tracked paths are hashed. Actual counts recorded after commands only. No runtime release/permission activation or network side effect.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Primary/§4/7/8 | Existing technical PS vertical; exactly two production release classes, same module/profile. | None; actual execution closure still required |
| MC-REUSE-001 | Applicable | PASS | Primary/§4/7/8 | Existing DOM/toposort/model/Store/handler/ValidationUtils/digest/JDBC reused. | None; actual execution closure still required |
| MC-DEP-001 | Applicable | PASS | Primary/§4/7/8 | No POM/dependency addition; PS has no Lombok project dependency and no new logger is introduced. | None; actual execution closure still required |
| MC-NAME-001 | Applicable | PASS | Primary/§4/7/8 | Semantic WorkerCompensationVO, ExpectedOperationVO/ReceiptVO and focused test/helper names. | None; actual execution closure still required |
| MC-VALID-001 | Applicable | PASS | Primary/§4/7/8 | Closed DOM, immutable approved output, annotation Default and fixture-local before-any-JDBC scope/topic/schema/effect guards. | None; actual execution closure still required |
| MC-MODEL-001 | Applicable | PASS | Primary/§4/7/8 | Defensive metadata/row snapshots, same-package mapping and all old constructor descriptors. | None; actual execution closure still required |
| MC-CONVERT-001 | Not applicable | N/A | Primary/§4/7/8 | No cross-layer PO/DTO conversion; same-package row snapshots only. | None; actual execution closure still required |
| MC-LOG-001 | Not applicable | N/A | Primary/§4/7/8 | Exact Root DEC-710 technical test-helper scope; no new business class/Bean/logger. Future production business rules remain. | None; actual execution closure still required |
| MC-BEAN-001 | Not applicable | N/A | Primary/§4/7/8 | No new Spring Bean or DI surface; test value constructor is not injected. | None; actual execution closure still required |
| MC-UTIL-001 | Applicable | PASS | Primary/§4/7/8 | Existing framework/JDK/Common helpers; no custom utility library. | None; actual execution closure still required |
| MC-JSON-001 | Not applicable | N/A | Primary/§4/7/8 | Existing WorkerBusinessResultDTO with empty output reused; no new wire JSON/enum. | None; actual execution closure still required |
| MC-TIME-001 | Applicable | PASS | Primary/§4/7/8 | Fixture DB write time and contract deadline per Spec TIME701/702; real skew/ACID unexecuted. | None; actual execution closure still required |
| MC-CONFIG-001 | Not applicable | N/A | Primary/§4/7/8 | All profile/default configuration files unchanged. | None; actual execution closure still required |
| MC-PATTERN-001 | Applicable | PASS | Primary/§4/7/8 | Existing handler Strategy and Store-owned transaction lifecycle; local graph dataflow, no new scheduler/registry. | None; actual execution closure still required |
| MC-SCOPE-001 | Applicable | PASS | Primary/§4/7/8 | Exact7/6 paths, excluded tracked SHA and all25 SQL immutable. | None; actual execution closure still required |
| MC-TEST-001 | Applicable | PASS | Primary/§4/7/8 | Real assertion RED/A/typedRED/B/GREEN, exact named reports and explicit no-runtime boundary. | None; actual execution closure still required |
| MC-BLOCKER-001 | Applicable | PASS | Primary/§4/7/8 | All current-Step checks closed before commit; future runtime acceptance not claimed. | None; actual execution closure still required |

### 12.6 Final verdict

PASS — Ready for user review

Design/Plan review readiness only, not user acceptance or source GO. Root must still perform exact text/strict/current-Step lock before implementation.

## Java / CQE contract review

Values/enum: only immutable metadata/enrollment/row snapshots, no ordinary entity record or ordinal wire storage. Validation: Default annotation metadata at test enrollment, stateful scope/rows under Store TX. Components/MP: current technical JDBC profile reused; no MP/CRUD/generator. Business columns: test-only source/tenant composite identities and immutable receipts, no softdelete lifecycle/NULL uniqueness issue. Command/Query/Event: existing Worker interface only, no new domain Event/Outbox/MQ. Architecture: preserve technical vertical and caller/owner boundaries, no hybrid layers. Actual proof per Step uses corresponding MC rows.

## Backend generation and dependency evidence

No affected production DDL/CRUD/catalog; generator action and classpath SQL journal: Not applicable under Primary§6/11/16 technical/test-only scope. No dependency introduction; existing Spring/Flowable/JDBC/Common APIs grounded in current source/JAR evidence. Test-only future schema consumes immutable existing W migrations; no main SQL or generated source is added. Current Source54/55 non-goals and runtime limits remain explicit.
