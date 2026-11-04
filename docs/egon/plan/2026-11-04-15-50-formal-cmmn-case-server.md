# 正式 CMMN Case 服务端、人工处理与 BPMN 回接实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-04-15-50-formal-cmmn-case-server.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-04 15:50 Asia/Shanghai` |
| Updated | `2026-11-04 15:50 Asia/Shanghai` |
| Owner | `Root reviewed original continuation authority；Plan-only design review，尚无coding GO` |
| Repository | `tianji-service` |
| Scope | `完整剩余T3-08 backend；exact3 sequential semantic implementation Steps，single future V8 in Step1` |
| Source Requirement | `原蓝图T3-08/S09 + frozen reviewed server Spec；不是新的isolatedlibrary` |
| Baseline Revision | `main@e6aa574de553d20b67f8d483d8a1e876bb48897e；仅canonical Spec untracked，其余clean` |
| Implements Spec | [Formal CMMN server](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) |
| Spec Status | `Review` |
| Spec Revision | `Root full-read + strict PASS；SHA-256 c4e6d4bb8e503b6a9ff2f386514bd64c975eaf83f65b363d838307c9244736e5` |
| Effective Specs | [Formal CMMN server](../spec/2026-11-04-15-50-formal-cmmn-case-server.md), [Case C](../spec/2026-11-01-23-20-case-client-contract.md), [Case SDK](../spec/2026-11-01-23-45-case-http-client.md), [CMMN semantic](../spec/2026-11-01-22-55-cmmn-semantic-validation.md), [CMMN XML](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

三个顺序语义提交交付完整剩余backend源码：Step1最终V8/stores/verifiers/CaseContext；Step2同一提交原子引入全部CMMNenum/release消费者与真实native/API/CASEHumanTask；Step3受限BPMNCaseSource/registeredSERVICE/completion recovery/精确Message与正式验收源码。Step1不提前接受CMMN模型，所有新enum/admission与安全nativefactory一起在Step2完成。

原S09实际native Case生命周期并让等待BPMN继续的验收义务保留。当前不授权PG/引擎/应用启动；finite tests、MockMvc和编译只能证明源码交付门禁，未运行真实S09时必须记`RuntimeUnverified`，不能宣布T3-08或原整体目标完成。此计划本身不修改代码/DDL、不运行Maven，Root独占后续Maven执行。

## 2. Target Spec and Effective Design

### 2.1 Primary target

唯一primary如header，Root已全文审阅并strict/hash。原用户继续整体目标与分步提交授权持续有效；Spec/Plan仍Review，不伪造Accepted，也不增加额外用户批准循环。只有Root full-read最终Plan、strict/hash后才有coding GO。

### 2.2 Effective Spec set

| Role | Exact Spec | Effective sections | State/meaning |
| --- | --- | --- | --- |
| Primary | ../spec/2026-11-04-15-50-formal-cmmn-case-server.md | all；Root corrected §4REQ416/§16grouping/CASEreplay | Review，current frozen hash above |
| Contract dependency | ../spec/2026-11-01-23-20-case-client-contract.md | §4/9/10/14 invariant set | delivered C types reused, no change |
| Wire dependency | ../spec/2026-11-01-23-45-case-http-client.md | §4/6.3/9/14 | delivered SDK/OAS immutable |
| Semantic dependency | ../spec/2026-11-01-22-55-cmmn-semantic-validation.md | §4/7/9/14 | closed native model metadata |
| XML dependency | ../spec/2026-11-01-22-35-cmmn-schema-boundary.md | §4/7/9/14 | parser/XSD/security boundary unchanged |

### 2.3 Superseded or excluded content

先前REQ-006/108/209/312的exact6/8/15路径/noHTTP/noenum等是已经交付的历史Step边界，不禁止本primary授权的后续server工作；其实际parser/CaseC/SDKwire/oldSQL不变。旧蓝图示例application/infrastructure层路径只是建议，当前真实PS verticalpackages与Root保留JDBC/Flyway/beanfactory的明确指示优先；不生成MP/COLA层、不新增依赖。

## 3. Effective Requirements and Acceptance

所有source IDs独立列出；dependency约束标为preserve/reuse，不能把历史Step scope重做为本次交付，也不能省略原生S09义务。

| Requirement | Exact source | Effective statement | Observable acceptance | Owning Step |
| --- | --- | --- | --- | --- |
| REQ-401 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | 一份未来V8冻结3技术表及release/V7checks；保23旧SQL | schema/checksum/privilege tests；Step2/3无需migration | Step 1, Step 2, Step 3 |
| REQ-402 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | release enum/validation/deploy/verify穷尽BPMN/CMMN/DMN；CMMN exactid/hash/tenant | 3waytests、same-release rejection、legacydigestgolden | Step 2, Step 3 |
| REQ-403 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | direct owner=verified executor USER/SERVICE；atomic CASE_START+generalprovenance | directreplay/rollback/executorFK/resourcechecks | Step 2, Step 3 |
| REQ-404 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | linkedregisteredSERVICE执行，同release父START owner/business/correlation继承，不恢复历史USER | rename/duplicate/parentPENDING/inlineprior-waitbarrier/crossrelease/rollback tests | Step 2, Step 3 |
| REQ-405 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | 三Case routes遵守immutableSDK/OAS/security/body/error合同 | MockMvc+packagedOAS+SDKdrift | Step 2, Step 3 |
| REQ-406 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | get允许exactowner或freshUSERreleasedoperatorunion，同ownerrealm；SERVICEowneronly含terminal | roleexpiry/tenant/issuer/terminalmatrix | Step 2, Step 3 |
| REQ-407 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | planitemUSERonly，freshresource+role+nativeversion；fixedactions/assigneeprotection | primary/supplemental/milestone/claimed-other denial | Step 2, Step 3 |
| REQ-408 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | nativeCMMN runtimeREV防ABA；includeEnded/historyprojection；historyREV不作mutationfence | enabled-disable-enable staleversion rejects；source/historical versionsdistinct | Step 2, Step 3 |
| REQ-409 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | CASEHumanTask复用现policy/receipt、ACTION/solelane/emptyform、requester=owner | claim/complete/read/replay+linkedV7joins+PROCESSregression | Step 2, Step 3 |
| REQ-410 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Caseplan/HumanTask共享scopeadvisory先于任何nativelock；noUPDATEgrantimmutablefacts | lock-orderunit+concurrencyPGsource、privilegeverifier | Step 1, Step 2, Step 3 |
| REQ-411 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | governedCMMNfactory在native表达式前验证，固定rolegroups，legacyoff不变 | cfg/parseridentity、dependencyhooks、interceptor/migration rejection、nativehistorytests | Step 2, Step 3 |
| REQ-412 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | linkedstart同TX预建Inbox，只有persistedCOMPLETED+endTime成功Message | ACTIVE不耗budget；TERMINATEDmanual/DEAD；duplicate/late/rollback | Step 2, Step 3 |
| REQ-413 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | BPMNCaseSource只literalCasekey，自定义nativeCaseTaskbehavior exactid启动并leave至releasedMessage/Timerwait | defaultcallback从未调用；exactwait/subscription/occurrenceproof | Step 2, Step 3 |
| REQ-414 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | caseEnabled/newStartsEnabled独立defaultoff；停新start保inflight处理/完成恢复 | off/on/stopnewflagsmatrix、V7/V8newbinarycompat | Step 1, Step 2, Step 3 |
| REQ-415 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | sourceSERVICE与completion同一固定registration，currentlocalactiveissuer重核、TX无PDP/remoteIO | noUSERcontext/noOAuthHTTPinsideTX/registrationrevocationfake | Step 2, Step 3 |
| REQ-416 | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | 保留原S09实际native验收义务，并交付正式fixture/opt-inPGsource覆盖正常/失败/并发/回接 | source与finiteunit/MockMvc只是交付门禁；原S09必须实际native Case完成并让BPMN继续；PG未获授权执行时标RuntimeUnverified，不能宣称T3-08完成 | Step 1, Step 2, Step 3 |
| REQ-201 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | 新增 exact 11 public types，`CaseClient` 只含 start/get/actOnPlanItem 和 frozen record component order | reflection/method assertions exact；no extra type/field/method | Step 1, Step 2, Step 3 |
| REQ-202 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Commands/queries reuse definition/release/id(255)/command/hash checks，no tenant/actor/roles/variables/raw command | invalid values reject；reflection proves forbidden fields absent | Step 1, Step 2, Step 3 |
| REQ-203 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | CasePlanItemDTO` copies ≤4 unique actions and enforces kind/state-compatible subsets；MILESTONE empty | state-compatible subset examples and milestone rejection cover advertised actions | Step 1, Step 2, Step 3 |
| REQ-204 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | CaseInstanceDTO` copies ≤16 planItems with unique instance IDs and definition keys；ACTIVE iff no finishedAt，terminal iff nonnull | duplicates/oversize/time mismatch/actions in terminal reject；empty/sparse/historical states accepted | Step 1, Step 2, Step 3 |
| REQ-205 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | CaseOperationResult` enforces exact four transitions and distinct valid previous/resulting hashes | DISABLE ENABLED→DISABLED；ENABLE DISABLED→ENABLED；START ENABLED→ACTIVE；TERMINATE ACTIVE→TERMINATED；all other pairs reject | Step 1, Step 2, Step 3 |
| REQ-206 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | All records are immutable JDK records/plain enums，lists copied；no mapper/DTO-PO conversion | source list mutation has no effect；returned list unmodifiable | Step 1, Step 2, Step 3 |
| REQ-207 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | submittedAt/occurredAt required Instant；finishedAt nullable only for ACTIVE；no default-zone or chronology rule | UTC Instant semantics documented；constructor permits finishedAt before submittedAt as existing DTO does | Step 1, Step 2, Step 3 |
| REQ-208 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | toString/reflection/negative tests ensure no secret identity/engine/variable surface and enum names are exact | CaseContractTest covers components, copies, sparse history and invalid matrix | Step 1, Step 2, Step 3 |
| REQ-209 | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | exact 15 CREATE paths；no existing edit/POM/dependency/transport/server/DB/release-enum change | path diff and core contract regression selector | Step 1, Step 2, Step 3 |
| REQ-301 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | HttpProcessClient implements CaseClient with exact 3 POST paths and request/response binding | fake transport sees exact paths/bodies and bound results | Step 1, Step 2, Step 3 |
| REQ-302 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Case guard requires normalized HTTPS or loopback before token；redirect NEVER；Bearer/Accept/Content-Type/no-store request headers | guard/token/order/header tests | Step 1, Step 2, Step 3 |
| REQ-303 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | each method sends once；existing async timeout cancels future；Case response cap 128KiB using real subscriber；no auto retry | fake future/subscription tests | Step 1, Step 2, Step 3 |
| REQ-304 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Case success exact 200 and JSON/+json；strict closed decoder requires all fields/explicit nullable finishedAt | codec/media/status/required-field tests | Step 1, Step 2, Step 3 |
| REQ-305 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | decoder rejects duplicate/unknown/trailing/coercion/numeric enum/numeric Instant and bounds items/actions | strict mutation tests | Step 1, Step 2, Step 3 |
| REQ-306 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | request/result correlation checks bind every consumer-owned field while accepting ACTIVE or terminal start replay/sparse history | mismatch matrix and terminal replay | Step 1, Step 2, Step 3 |
| REQ-307 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | only nine trusted error code/status pairs；safe status fallback；correlation/body/cause/token never exposed；Retry-After bounded metadata | error matrix/privacy tests | Step 1, Step 2, Step 3 |
| REQ-308 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Case sync sendAsync RuntimeException masked as dependency unavailable；legacy sync path rethrows unchanged | paired fake test | Step 1, Step 2, Step 3 |
| REQ-309 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | packaged contract-first OpenAPI3.1 defines exact 3 operations, 11 schemas, bearer security and 200+8 error statuses | artifact parse/reflection/local-ref test | Step 1, Step 2, Step 3 |
| REQ-310 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | absolute offset textual Instants decode to UTC Instant preserving nanos；numeric/naive invalid；no chronology/freshness generation | codec time tests | Step 1, Step 2, Step 3 |
| REQ-311 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | old Process/Decision/Human controlled fake smoke and old limits/error method remain unchanged | smoke/source diff | Step 1, Step 2, Step 3 |
| REQ-312 | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | exact 8 paths；no C/PS/POM/DB/permission/schema edit | path/static checks | Step 1, Step 2, Step 3 |
| REQ-101 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | CmmnModelValidator.validate(bytes,expectedKey)` 必须先安全解析，再独立 closed DOM walk；Case id 等于合法 expectedKey | governed fixture returns model；null/invalid/mismatch key generic reject | Step 1, Step 2, Step 3 |
| REQ-102 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | 只接受 exact 3 plan items、3 sentries、2 HumanTasks、1 milestone、全部首版 internal IDs/refs/criteria/rules/onParts 和 `casePlan`、autoComplete=false | exact topology accepted；missing/extra/renamed/wrong ref/count/control/event rejected | Step 1, Step 2, Step 3 |
| REQ-103 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | 所有 present IDs 合法且全局唯一，所有 attribute value ≤512，无 import/EL/condition/DI/callable/Flowable attr/其他 executable grammar | mutation matrix rejects every forbidden construct with cause-free generic error | Step 1, Step 2, Step 3 |
| REQ-104 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | 两 HumanTask 必须是 task namespace `ACTION + initialLane + exactly one taskLane`，sole lane、positive Long roles ≤16、无 fields/variables/approval | fixture parses roles 7/8 and 8/9；invalid governance rejects | Step 1, Step 2, Step 3 |
| REQ-105 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | 仅将 `ProcessModelValidator.humanTask(Element)` 从 private 改为 package-static，方法 body 和 BPMN behavior byte-identical | visibility diff only；existing HumanTask/BPMN tests remain green | Step 1, Step 2, Step 3 |
| REQ-106 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | ValidatedCaseModel` 用 4-arg constructor 防御复制 XML/maps；nested Action/PlanItemPolicy immutable；operator roles derived union | mutation attempts fail；returned XML clone；expected maps/actions/roles exact | Step 1, Step 2, Step 3 |
| REQ-107 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | primary actions=`DISABLE,ENABLE,START`；supplemental=`START,TERMINATE`；milestone=empty；policy roles from task lanes | exact plan item metadata and union `{7,8,9} | Step 1, Step 2, Step 3 |
| REQ-108 | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | exact eight paths；old `formal-case.cmmn` read-only and rejected；no converter/facade/API/release/DB/POM/config changes | path diff and final six-class selector；runtime boundary explicit | Step 1, Step 2, Step 3 |
| REQ-001 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | 增加 package-private `SafeModelXml.parseCmmn(byte[])`，只验证 Flowable 8 打包的 CMMN 1.1 schema | 正式 fixture 返回 namespace-aware `Document`；错误 schema 被拒绝 | Step 1, Step 2, Step 3 |
| REQ-002 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | CMMN 路径必须复用现有字节上限、严格 UTF-8、scan/DOM XXE 防护、外部资源禁止和统一泛化异常 | empty/oversize/non-UTF8/DOCTYPE/错误 namespace 均抛同一 generic `IllegalArgumentException` 且不泄漏 cause/content | Step 1, Step 2, Step 3 |
| REQ-003 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | 现有 `parse(byte[],boolean)` 行为及 BPMN/DMN schema 惰性初始化保持 | 旧签名不变；新 CMMN holder 不被旧选择提前触发；既有回归保持 | Step 1, Step 2, Step 3 |
| REQ-004 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | 正式中立 fixture 必须含两个 manual HumanTask、primary-complete sentry、supplemental complete/terminate 两个 OR entry criteria milestone、required primary/milestone、`autoComplete=false | XSD 和 fixed converter 都保留三 plan item、三 sentry、两人任务、一个 milestone；无 repetition/EL/custom code/Form | Step 1, Step 2, Step 3 |
| REQ-005 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | focused test 的两个模型正向场景都先调用 `parseCmmn`，四项 parser 上限各自验证“精确边界接受、越界拒绝”，然后仅结构场景调用固定 `CmmnXmlConverter | 7 个 focused tests：6 个 acceptance tests 在 stub RED，1 个纯拒绝矩阵仍通过；不启动 engine/DB | Step 1, Step 2, Step 3 |
| REQ-006 | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | 实现与提交范围严格限制为六路径且无新依赖 | 精确 path diff；POM/SQL/config/API/enum/release admission 均无变化 | Step 1, Step 2, Step 3 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Step1无public/internal model enum或releaseadmission改动，真实完整stores/verifiers支持Caseoff V7/V8；Step2一次把所有三路type consumers与native安全readiness/API/task装配闭合，避免半步enum落入DMN或不安全nativefactory；Step3才放行literalCaseSource并接localcompletion。每Step代码GREEN/静态核对后立即单提交，再开始下一Step的任何源码。

### 4.2 Test-first strategy

每个真实store与native/API行为有自己的assertion RED/GREEN。先写focused tests，必要时补compile-only signatures/UOE/raw bodies，使测试能运行；这些仅在未提交的同Step内存在，Root只把行为assertion失败计RED，编译/fixture/缺resource/缺依赖失败不算。随后实现最小final行为并GREEN；commit前逐字审查，禁止提交constant-false readiness、reject-all codec/store、UOE或未实现入口作为完成特性。V8从冻结Spec一次写完整，不以不完整migration制造RED。

### 4.3 Sequential and parallel boundaries

各Step内可并行准备互不重叠的测试/只读review，但共享config/release/task源码由该Step一个owner集成。禁止多worker同时写相同文件，禁止下一Step代码提前。Root专属Maven与提交；工作者不启动DB/app/listener/browser，不回退他人编辑。

Step1 independent participant implementations may run in parallel only after their test sources, shared immutable signatures and meaningful assertion RED are fixed; this does not permit Step2 code. Root serializes Maven/commits and resolves dependencies without overlapping owned paths.

### 4.4 Commit boundaries

- Step 1: `feat(process): add formal Case persistence foundation`。
- Step 2: `feat(process): implement governed direct CMMN Case operations`。
- Step 3: `feat(process): correlate linked Case completion with BPMN`。

每commit精确路径在对应Step列出；Spec/Plan随Step1真实实现入库，无docs-only commit。Evidence三路径分别为2026-11-04-formal-cmmn-foundation.md、2026-11-04-direct-case-server.md、2026-11-04-linked-case-completion.md。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity/evidence | Direct alternative | Decision/cost |
| --- | --- | --- | --- |
| three technical tables | primary§7/11 | base START/runtime alone cannot retain owner/executor/result/recovery | exactsingleV8；no fourth table/businessSM |
| Case adapters/factories | native8 exact hook audit | default key/latest/callback advances on termination | typednativeAdapter/Factory reuse |
| CASE HumanTask branch | existing task service/rules/receipts | duplicating task API/businessrules | narrow explicitscope overloads, PROCESS preserved |
| linked Inbox/poller/orphan scan | precreatedintent/rootreview | eventlistener gap/currentregistration-only scan | boundedlocalrecovery/indexes, no remoteMQ |
| new frameworks/dependencies | currentPOM already sufficient | genericbusinessskill | rejected; currentJDBC/Flyway/technicalstyle |

### 4.6 Change-unit Dependency Matrix

| Unit | Prerequisites | Produces | Consumer | Step |
| --- | --- | --- | --- | --- |
| finalV8/context/stores/verifiers | frozen§11 + old23SQLhash | fullpersistentcontract + versionawarelegacy | direct/native/source/completion | 1 |
| enums/release/native/API/task | Step1 committed/clean | safeoperableCMMNprovider + directlifecycle | SDK/operator/linkedCase | 2 |
| source/registry/completion | Step2 committed/clean | exactBPMNbacklink/recovery + nativeacceptancesource | originalS09 | 3 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

保持PS api/task/release/execution/bridge/security packages、plaintechnicalclasses、explicitnamed@Bean factories、qualifiedexistingownership、JDKimmutablecontexts、既有logger风格。C只纯JDK enum增加CMMN，Case11types不改；CL仅releasecorrelation分支，Casecodec/OAS/transport不改。外部JSON用现Jackson3，native/Outbox内部Jackson2不替换；不增加Lombok/validation/MapStruct/MP/DDLrunner依赖。

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | PS actual api/task/release/execution/bridge/security vertical packages and plain JDBC | Primary §6.2 keeps technical framework style | Extend these packages; no archetype/layers/ORM conversion | Steps 1–3; MC-ARCH-001 |
| Reuse/capability | ProcessCommandReceiptStore, HumanTaskReceiptStore, existing native factories | Primary §7/11 retains command owner and exact native authority | Dedicated missing Case facts only; existing C and task API reused | Steps 1–3; MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | HumanTaskContext, existing immutable C records and strict advice | Same explicit technical constructors and boundary validation | CaseContext/Snapshot, defensive copies; no PO/mapper | Steps 1–3; MC-NAME-001/MC-MODEL-001/MC-VALID-001 |
| Bean/logging/util/JSON/time/config | ProcessSecurityConfiguration, ProcessStorageConfiguration, Jackson3, Instant, base YAML | Primary §6.2 and §15 | Named factories/lazy typed collaborators; two default-off flags, UTC instants and bounded durations | Steps 1–3; MC-BEAN-001/MC-JSON-001/MC-TIME-001/MC-CONFIG-001 |
| Business variation/pattern | Native factory variation and existing local command receipts | Primary §7 Adapter/Factory and command participants | Reuse native engine authority; no business Case state machine | Steps 1–3; MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| current actor/roles | TianjiProcessAccess, TianjiHumanTaskAccess | Existing require and fresh snapshot/deadline methods | Fits current boundary; Case permits SERVICE owner reads too | Reuse identity/PDP; project CaseContext | No dependency; necessary Case adapter | Step 2; MC-REUSE-001 |
| persistence/recovery | Existing command/task stores and AgentResultInbox | JDBC source/TM/schema/receipt lifecycle and due scan | Missing immutable Case owner/executor/results | Three frozen technical tables/participants | No dependency; exact §11 custom code | Steps 1/3; MC-DEP-001 |
| native behavior | PinnedDecisionActivityBehaviorFactory, local Flowable8 API audit | Native capsule §1–12, exact factory/parser lifecycle | Default Case callback unsafe for governed completion | Exact native adapters/factories | No dependency; necessary adapters | Steps 2/3; MC-PATTERN-001 |
| tests | JUnit/Mockito/MockMvc/source-reactor already in POM | Existing task/API/config tests | Finite static/behavior proof fits; native PG remains separate | Existing stack only | None | Steps 1–3; MC-TEST-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 2 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 3 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 4 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 5 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 6 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 7 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 9 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 10 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |
| Rule 11 | primary §6.2/§13 | current technical vertical style + explicitRootscope | exactper-fileblocksbelow | perfile literal enforcement; immutablewire/JSON/time/owner/role/factory obligations as applicable | perStep independent MC/Rule tables + focused/static | Step 1, Step 2, Step 3 | PASS; no generic-framework conversion |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-401 | provenance.created_at, result.occurred_at, Inbox.created_at/terminal_at/decided_at; CaseDTO timestamps | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: no calendar business rule | Not applicable: no frontend | Timestamp.from/toInstant；offset->sameinstant；noCasechronology/freshness | PGtimestamptz(6)/DBclock; nativeDate.toInstant actualmilliseconds；response/replay samepersistedprecision | C/SDKnanos preserved; do not inventsubmicrofacts | Step 1, Step 2, Step 3; TEST-402, TEST-407, TEST-409; §8 commands |
| TIME-402 | CaseContext.deadlineAt/currentidentityexpiry | Occurred instant | Instant/Clock.systemUTC | Not persisted | Internal | Not applicable: authwindow | Not applicable: no display | freshcontextbeforeTX；DBclock strictdeadline before/aftermutation | min(identity/snapshot expiry,existing30s window); no deadlineextension | currentUSERsnapshotroles only | Step 1, Step 2, Step 3; TEST-403, TEST-405, TEST-406 |
| TIME-403 | pollInterval/retryDelay/txTimeout | Duration | Duration | Not persisted | Internal | Not applicable: elapsedtechnicaldelay | Not applicable: no UI | noDST/calendar；ACTIVEreschedule nofailureburn | defaults1s/1s/10s; boundedmillisecondprecision | currentpollerstyle | Step 1, Step 3; TEST-408, TEST-409 |
| TIME-404 | Inbox.next_attempt_at | Occurred instant | Instant | UTC instant | Internal | Not applicable: technicaldueinstant | Not applicable: no UI | DBclock+Duration；no absolute Caseexpiry | timestamptz(6) | existingInboxdueconcept, orphan scan mustprogress | Step 1, Step 3; TEST-408, TEST-409 |

## 5. Change File Tree

完整exact路径库存等同§7 File headers/对应Commit paths；同一路径只在本库存出现一次，后续Step MODIFY只添加该Step批准的作用域。没有POM、旧SQL、C Case11types、CaseResponseCodec、OAS或新增框架路径。完整库存如下，每行对应实际路径：

| Exact path | Ordered operations |
| --- | --- |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java` | Step 1 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V8__add_formal_cmmn_case_support.sql` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/identity/CaseContextTest.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/identity/CaseContext.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStoreTest.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStore.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStoreTest.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStore.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInboxTest.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInbox.java` | Step 1 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseStorageConfigurationTest.java` | Step 1 CREATE, Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java` | Step 1 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifier.java` | Step 1 MODIFY |
| `docs/egon/spec/2026-11-04-15-50-formal-cmmn-case-server.md` | Step 1 CREATE |
| `docs/egon/plan/2026-11-04-15-50-formal-cmmn-case-server.md` | Step 1 CREATE |
| `docs/egon/evidence/2026-11-04-formal-cmmn-foundation.md` | Step 1 CREATE |
| `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessModelTypeEnum.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/VerifiedProcessModels.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccessTest.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshotTest.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseServiceTest.java` | Step 2 CREATE, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccess.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshot.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseService.java` | Step 2 CREATE, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehaviorTest.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehavior.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseActivityBehaviorFactory.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccessTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshotTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResultTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStoreTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccess.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResult.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStore.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccessTest.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccess.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/CaseControllerTest.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseRequestBodyAdvice.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseController.java` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java` | Step 2 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | Step 2 MODIFY, Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Step 2 MODIFY, Step 3 MODIFY |
| `docs/egon/evidence/2026-11-04-direct-case-server.md` | Step 2 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case-linked.bpmn20.xml` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistryTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceProperties.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistry.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehaviorTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehavior.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/decision/PinnedDecisionActivityBehaviorFactory.java` | Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspectorTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspector.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationServiceTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationService.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPollerTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPoller.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseFlowableScenarios.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/FormalCasePostgresTest.java` | Step 3 CREATE |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java` | Step 3 MODIFY |
| `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/HumanTaskFlowableScenarios.java` | Step 3 MODIFY |
| `docs/egon/evidence/2026-11-04-linked-case-completion.md` | Step 3 CREATE |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository baseline

currentmain e6aa574 + 未跟踪的reviewedSpec；实施前Root再次检查Git状态和Spec/Planhash。所有工作者不是独占workspace，不revert他人修改。Step1 V8若已存在/版本被占则停止该SQL创建并返回Root，不覆盖；保留all23oldSQL/SafeModelXml/CaseC/SDKOAS逐SHA快照。

### 6.2 Build/test prerequisites

JDK25 `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`；真实`./scripts/verify-source-reactor.sh`；隔离`/tmp/tianji-compat-fix/settings.xml`（onlylocalrepo /Users/mario/maven/repository + noninteractive，no credentials/mirrorscopied）；所有Maven先`-o`。cache/compile/fixture失败不计RED，先独立修复/报告，不能伪造behaviorred。Root alone Maven；delegates不得额外跑相同suite。

CaseCompletionInbox finish needs an internal AppliedCommand(requestSha256,resourceId) proof because immutable Inbox rows deliberately do not persist current wait execution/request hash; the completion service derives these from verified native wait/model facts and the store compares them to its exact APPLIED base command. This clarifies an internal signature only, adding no table/column/endpoint or dependency.

### 6.3 Immutable constraints

C Case fields/methods/enums+SDKCasewire+packagedOAS immutable；old23SQL neveredit；one finalnewV8 onlyStep1，Step2/3零DDL。no realDB/listener/app/browser/network/deploy/push。RuntimePGtestsopt-in source only；originalS09 acceptance仍Required/RuntimeUnverified，不缩为编译或Mockpass。

### 6.4 Plan Clarifications

| ID | Local inference / Rootdecision | Evidence | Why same semantics | Impact |
| --- | --- | --- | --- | --- |
| CLAR-401 | RootB grouping：allmodelenum/releaseadmission edits moved atomicallyStep2；CaseContextprereqStep1 | canonicalSpec§16 corrected | preserves3logicaloutcomes、安全admission | no halfenum/false readinesscommit |
| CLAR-402 | compile-only UOE/raw bodies alloweduncommitted beforeassertionRED | RootexplicitTDDclarification | cannot becommitted ascompletedfeature | finaldiff rejectsplaceholders |
| CLAR-403 | store method/nestedrecord names below are local symbol choices | existingJDBCstores + frozen§11fields | no new externalmodel/table/policy | executor follows exactmappings |
| CLAR-404 | CmmnTaskService candidate links useadd/deleteGroupIdentityLink(id,group,"candidate") | local8 exactAPIaudit | samefixedrole groups asSpec | do not copy nonexistentBPMNconvenience methods |
| CLAR-405 | CASE replay oldrole is historicalaudit; currentANYreleasedlane roleallows | Rootcorrection/actualHumanTaskAccessRules | preservescurrentcandidate semantics | testwithin-lane switch vsallrevoked |
| CLAR-406 | CmmnModelValidator bodyneedsnochange beyond alreadypublicwrapper/metadataaccess | currentsamepackagewrappercaninvokeit | frozenclosedgrammar preserved | Alreadyexists; no mechanicaledit |

Release check name is source-derived PostgreSQL default `tianji_process_release_definition_model_type_check`; V8 uses strict DROP, so a different deployed catalog fails closed. Operator confirms catalog before migration deployment. This is not a prerequisite to authoring the final source migration and does not authorize a DB connection.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付最终单V8、真实Case技术stores与version-aware验证

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-201, REQ-202, REQ-203, REQ-204, REQ-205, REQ-206, REQ-207, REQ-208, REQ-209, REQ-301, REQ-302, REQ-303, REQ-304, REQ-305, REQ-306, REQ-307, REQ-308, REQ-309, REQ-310, REQ-311, REQ-312, REQ-401, REQ-403, REQ-404, REQ-407, REQ-408, REQ-409, REQ-410, REQ-412, REQ-414, REQ-415, REQ-416
- Dependencies: Root finalPlan full-read/strict/hash coding GO；currentbaseline cleanexceptreviewedSpec。
- Baseline state: V1–V7、CaseC/SDK存在；noCaseV8/stores/CaseContext；modelenum仍BPMN/DMN。
- Observable outcome: 完整singleV8技术存储与V7/V8兼容校验可用，Caseoff旧BPMN/DMN/task继续，不提前开启CMMNadmission。
- End state: finalschema/threefunctionalstores/context/verifiers+tests/docEvidence一commit，零modelenum/releaseconsumer/PaCactivation。
- Test-first gate: Required — 每个新store测试先写，compile-only prereq后用assertion RED证明缺失owner/base/state/replay/锁规则；不得计编译/fixture失败。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11
- Ownership: Step1 persistence worker只拥有本Step exact路径；不是独占workspace，不revert其他改动；Root合并验证和commit。
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java`
- Purpose: 先定义V7/V8二进制兼容RED，旧verifier不识别宽checks。
- Symbols: HumanTaskStorageVerifierTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: HumanTaskStorageVerifierTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
arrange valid V8 catalog/check descriptors with unchanged V7 checksum;
assertDoesNotThrow(verify);
mutate each widened check/history/hash/privilege and assert rejection;
retain original V7 cases.;
```
- Verification contribution: HumanTaskStorageVerifierTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V8__add_formal_cmmn_case_support.sql`
- Purpose: 一次写入冻结的最终V8，不做后续DDL迭代。
- Symbols: three tables; widened release/V7 checks; two Inbox due indexes。
- Repository evidence: highest existing ProcessDB version is V7; all23 existing SQL immutable; primary Spec §11 exact columns/constraints。
- Dependencies and consumers: ProcessApiSchemaVerifier.verifyCase/HumanTaskStorageVerifier/new stores; external Flyway provisioner。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: three tables; widened release/V7 checks; two Inbox due indexes；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: Spec §11逐列原样：provenance双receipt/same release/occurrence uniqueness；Result12+audit；precreated Inbox with varchar128 completion FK。
- Error and edge behavior: no engine FK/DELETE grant/CaseStateMachine; no speculative CASE_START resource index; runtime grants stay operator-owned。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```sql
before CREATE recheck no V8 and all23 SQL hashes;
author all columns, checks, PK/FKs and required indexes from frozen §11 in one file;
completion command ID varchar(128) COLLATE C +64hex check;
source occurrence unique excludes source_id;
calculate SHA only after final bytes exist;
never edit old migrations or reserve a partial V8.;
```
- Verification contribution: CaseStorageConfigurationTest; oldSQL hash gate。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/identity/CaseContextTest.java`
- Purpose: 锁定真实immutable context合同。
- Symbols: CaseContextTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: CaseContextTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
assert actor/resource/deadline required;
copy role set;
positive bounded role IDs/versions;
SERVICE has no USER-role path;
redacted toString;
no auth is granted merely by construction.;
```
- Verification contribution: CaseContextTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/identity/CaseContext.java`
- Purpose: 为真实store提供immutable current-operation facts。
- Symbols: record CaseContext(ProcessActor,String,Set<String>,Instant,long,long)。
- Repository evidence: HumanTaskContext is the exact current immutable technical pattern; no new C carrier。
- Dependencies and consumers: new stores now; TianjiCaseAccess/FlowableCaseService in Step2。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: record CaseContext(ProcessActor,String,Set<String>,Instant,long,long)；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: defensive role copy; actor/resource/deadline/authVersion/policyVersion; private fixed resource validation if necessary, no premature PaC registration。
- Error and edge behavior: USER/SERVICE kept distinct; invalid values fail generic; no clock generation or historical-user restoration。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
record with compact constructor: copy roles, validate tenant/nonLEGACY actor, bounded positive roles, resource and deadline;
retain exact owner/executor meaning;
no Spring/JSON dependency in C and no new dependency in PS.;
```
- Verification contribution: CaseContextTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStoreTest.java`
- Purpose: 每个provenance查询/写入/锁的真实RED/GREEN。
- Symbols: CaseStartProvenanceStoreTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: CaseStartProvenanceStoreTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
valid direct/linked JDBC rows pass;
corrupt owner/executor APPLIED/hash/resource/release/issuer or linked group rejects;
rename cannot bypass occurrence key;
capture advisory SQL/order;
assert zero UPDATE/FOR UPDATE on immutable table.;
```
- Verification contribution: CaseStartProvenanceStoreTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStore.java`
- Purpose: allCase来源唯一技术owner，提供共享CaseScope锁。
- Symbols: constructor(DataSource,String); insert; find; byExecutor; byLinkedOccurrence; lockScope; nested immutable Seed/Entry。
- Repository evidence: ProcessCommandReceiptStore/HumanTaskReceiptStore JDBC, hash/advisory and row mapping patterns。
- Dependencies and consumers: CaseNative/Case service, CASE HumanTask, completion; ordinary JDBC participant。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: constructor(DataSource,String); insert; find; byExecutor; byLinkedOccurrence; lockScope; nested immutable Seed/Entry；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: all Spec §11.2.3 fields; joins separate owner+executor receipt and exact model binding; PK tenant/Case; caseScope hash domain tenant+CaseID。
- Error and edge behavior: direct owner=executor CASE_START; linked owner START resource=parentPID, executor CASE_START resource=CaseID; same release; no UPDATE privilege。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
implement exact §11.2.3 INSERT/SELECT joins and row validation;
lockScope hashes TIANJI_CASE_SCOPE_LOCK_V1+tenant+caseId -> first8bytes signed long -> pg_advisory_xact_lock;
require owning transaction/source;
use String closed model types until Step2 model enums exist;
no fourth table or grant.;
```
- Verification contribution: CaseStartProvenanceStoreTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 7 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStoreTest.java`
- Purpose: Result12、fresh-role audit和base receipt一致性的真实RED/GREEN。
- Symbols: CasePlanItemReceiptStoreTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: CasePlanItemReceiptStoreTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
write/read all12 public fields;
reject corrupt base state/hash/resource/realm/model/transition/version/audit;
allow repeated enable-disable commands for same PI;
return identical stored result after native disappearance.;
```
- Verification contribution: CasePlanItemReceiptStoreTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStore.java`
- Purpose: immutable accepted plan command result participant。
- Symbols: constructor(DataSource,String); insert; find; nested Entry audit/result。
- Repository evidence: HumanTaskReceiptStore exact JDBC participant; CaseOperationResult already committed。
- Dependencies and consumers: Step2 FlowableCaseService; command base reservation/application remains existing ProcessCommandReceiptStore owner。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: constructor(DataSource,String); insert; find; nested Entry audit/result；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: Result.commandId uses PK command_id; releaseVersion->release_key; previous RUNTIME version; resulting RUNTIME or exact terminal HISTORY handoff; positive role/current audit。
- Error and edge behavior: full command PK join; no Case+PI unique; no history token accepted as mutation fence; immutable SELECT/INSERT。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
insert/read exact §11.2.4 columns;
parse Result12 through existing C constructor;
check base/provenance/definition/hash/resource/owner realm, all4 transitions and unequal hashes;
keep historical audit versions, never impersonate historical user or regenerate result from native current state.;
```
- Verification contribution: CasePlanItemReceiptStoreTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 9 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInboxTest.java`
- Purpose: 预建Inbox、状态/claim/orphan与command FK的真实RED/GREEN。
- Symbols: CaseCompletionInboxTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: CaseCompletionInboxTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
PENDING preparation only linked;
APPLIED requires COMPLETED+command;
TERMINATED cannot APPLIED;
ACTIVE reschedule leaves failure count;
recordCompletion preserves verified COMPLETED TerminalFacts while PENDING;
due and global orphan scans bounded;
terminal rows cannot revive;
assert APPLIED null code/type, DEAD null code, CASE_TERMINATED null status are rejected;
assert both absent/present COMPLETED IGNORED_LATE forms;
capture scope-before-Inbox caller obligations.;
```
- Verification contribution: CaseCompletionInboxTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInbox.java`
- Purpose: 冻结完整linked delivery store，业务apply留Step3。
- Symbols: constructor(DataSource,DataSourceTransactionManager,String); prepare; due; orphanDue; lock; reschedule; rescheduleFailure; find; finish; databaseNow; nested DueKey/Entry/TerminalFacts。
- Repository evidence: AgentResultInbox JDBC methods/state checks and bounded SKIP LOCKED pattern。
- Dependencies and consumers: Step3 poller/correlation; Step1 only functional store, no running poller。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: constructor(DataSource,DataSourceTransactionManager,String); prepare; due; orphanDue; lock; reschedule; rescheduleFailure; find; finish; databaseNow; nested DueKey/Entry/TerminalFacts；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: Spec §11.2.5 exact fields; completion ID deterministic64hex stored varchar128; terminal fact hash can bind full positive native trace set。
- Error and edge behavior: PENDING can retain verified COMPLETED fact; ACTIVE no failure increment/short expiry; explicit removed source remains discoverable by global orphan index; no native Message in store。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
write SQL for linked prepare, exact registration due claim and global anti-join orphan claim (<=256 bound tuples, limit<=32), commit discovery before apply;
lock/read/finish under caller CaseScope;
implement terminal/status/command consistency and DB-time/failure rules;
no table/column left for later migration.;
```
- Verification contribution: CaseCompletionInboxTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

Inbox signatures frozen for Step3 consumers (all participant reads/mutations require the actual owning source transaction; discovery methods own their short transaction). Caller discovers CaseID with `find` before acquiring CaseScope, then re-reads with `lock`; unlocked discovery is never authorization:

```java
public Entry prepare(CaseStartProvenanceStore.Entry provenance);
public Optional<Entry> find(DueKey key);
public List<DueKey> due(RegistrationKey registration, int limit, Duration claimInterval);
public List<DueKey> orphanDue(Set<RegistrationKey> activeRegistrations, int limit, Duration claimInterval);
public Entry lock(DueKey key);
public Entry reschedule(Entry expected, Duration delay);
public Entry reschedule(Entry expected, TerminalFacts facts, Duration delay);
public Entry rescheduleFailure(Entry expected, Duration delay, int maxFailures);
public Entry finish(Entry expected, State state, DecisionCode code);
public Entry finish(Entry expected, State state, DecisionCode code, TerminalFacts facts);
public Entry finish(Entry expected, State state, DecisionCode code, TerminalFacts facts, AppliedCommand applied);
record AppliedCommand(String requestSha256, String resourceId) {}
public Instant databaseNow();
record RegistrationKey(long tenantId, String serviceIssuer, String serviceSubject, String sourceId) {}
record DueKey(long tenantId, String completionCommandId) {}
enum State { PENDING, APPLIED, IGNORED_LATE, DEAD }
enum DecisionCode { MESSAGE_APPLIED, WAIT_ENDED, MESSAGE_CONSUMED, CASE_TERMINATED, RETRY_EXHAUSTED, SOURCE_UNAVAILABLE, INCONSISTENT_NATIVE_FACT }
enum TerminalStatus { COMPLETED, TERMINATED }
record TerminalFacts(TerminalStatus status, String sha256, Instant terminalAt) {}
record Entry(DueKey key, String caseInstanceId, RegistrationKey registration, State state,
             TerminalFacts terminalFacts, Instant createdAt, Instant nextAttemptAt, int failureCount,
             Instant decidedAt, DecisionCode decisionCode, String appliedCommandType) {}
```

`prepare` only LINKED_BPMN/exact executor SERVICE; registration tuple copies immutable provenance. `reschedule(expected, delay)` preserves facts and failure count. Its TerminalFacts overload adds only verified COMPLETED facts while PENDING, requiring prior facts null or equal. `rescheduleFailure` alone increments technical failures and sets DEAD/RETRY_EXHAUSTED at threshold. `finish` preserves prior facts unless the explicit facts are equal/new; APPLIED requires COMPLETED/MESSAGE_APPLIED plus explicit AppliedCommand from the owning completion service, derives CASE_COMPLETION_SERVICE internally, and validates exact APPLIED command full tuple/hash/release/correlation/resource before update; three/four-argument finish rejects APPLIED without this proof, and non-APPLIED forbids the proof; proof construction does not authorize native Message delivery and the service must still verify/fence the actual current wait; IGNORED_LATE allows null or COMPLETED; DEAD/CASE_TERMINATED requires TERMINATED. All other terminal mismatches reject. Entry reader rejects partial terminal groups, missing codes and forbidden applied type; tests include SQL UNKNOWN counterexamples and both late shapes. Terminal rows never revive. Public nested technical types/records expose only the frozen internal contract; summaries redact identities.

#### File 11 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseStorageConfigurationTest.java`
- Purpose: 锁住完整V8 resource/descriptor/privilege与两due索引。
- Symbols: CaseStorageConfigurationTest。
- Repository evidence: existing JUnit/Mockito/JDBC fake or MockMvc conventions in adjacent owner tests; CREATE means absent at baseline。
- Dependencies and consumers: exact production owner named in assertions; existing test dependencies only。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: CaseStorageConfigurationTest；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: synthetic inputs/native/JDBC rows -> public result, SQL/binding/lock trace and negative assertions。
- Error and edge behavior: finite execution; no network/listener/DB; compiler/fixture failures do not count as RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
parse actual final V8 resource;
verify all columns/checks/FKs/indexes/collations and computed hash;
mutate one descriptor/check boolean grouping at a time, including CMMN ACTION and nullable-terminal 3VL checks;
keep V7 Caseoff/BPMN behavior;
no native CMMN enum/bean activated.;
```
- Verification contribution: CaseStorageConfigurationTest。
- After this file: test source written; compile-prerequisite bodies may still be needed before assertion RED。

#### File 12 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`
- Purpose: 新增verifyCase完整schema verification而不扩大旧verify。
- Symbols: verifyCase(); private descriptor/helpers only as complexity requires。
- Repository evidence: existing source,schema constructor and verify/Worker/Agent methods; existing exact catalog verifier。
- Dependencies and consumers: stores/config in Step2+3; old Release/Worker/Agent callers unchanged。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: verifyCase(); private descriptor/helpers only as complexity requires；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: actual final V8 resource SHA/Flyway successful checksum; three tables and widened checks; primary DB/UTF8/origin/schema。
- Error and edge behavior: SELECT/INSERT immutable tables; Inbox SELECT/INSERT/UPDATE; no DELETE/owner/superuser/RLS/unexpected trigger; preserve old START partial index。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
add verifyCase chaining base verify and exact V8 descriptors;
check both Inbox due indexes and exact varchar128 FK;
compute/record only new resource checksum after authoring, preserve V1-V7 checksums and old methods/constructor semantics;
no model enum change.;
```
- Verification contribution: CaseStorageConfigurationTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 13 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifier.java`
- Purpose: 让新binary在Caseoff下严格支持V7或V8。
- Symbols: verify(); successful V8 selection; existing constructor。
- Repository evidence: V7 FILE/hash and exact check verifier currently rejects widened checks。
- Dependencies and consumers: HumanTaskReceiptStore.verifyStorage; existing two bean factories; ReleaseService callers。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: verify(); successful V8 selection; existing constructor；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: without successfulV8 -> original V7 descriptors; withV8 -> original V7 checksum plus V8 resource/hash and widened check descriptors。
- Error and edge behavior: failed/unknown V8/checksum/partial catalogs reject; no accepting arbitrary future versions; legacy BPMN fields/indexes unchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```java
detect successful exact V8 history;
choose exact old or widened check set;
share existing descriptor facilities;
actual column/native type/collation/default/privilege validations remain strict;
never change V7 source bytes or require a CMMN model enum.;
```
- Verification contribution: HumanTaskStorageVerifierTest; CaseStorageConfigurationTest。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 14 — `CREATE docs/egon/spec/2026-11-04-15-50-formal-cmmn-case-server.md`
- Purpose: 把Root full-read/strict/hash的Review Spec纳入首个代码提交。
- Symbols: unchanged frozen Spec。
- Repository evidence: already untracked at current baseline; canonical SHA c4e6d4bb8e503b6a9ff2f386514bd64c975eaf83f65b363d838307c9244736e5。
- Dependencies and consumers: this Plan and later workers。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: unchanged frozen Spec；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: scopeB and originalS09 acceptance remain exact; no false Accepted。
- Error and edge behavior: do not rewrite immutable design or claim PG/native success。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```text
stage exact reviewed bytes with Step1 implementation;
verify primary SHA and strict validator;
no docs-only commit and no metadata churn that invalidates approved hash.;
```
- Verification contribution: strict Spec/hash。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 15 — `CREATE docs/egon/plan/2026-11-04-15-50-formal-cmmn-case-server.md`
- Purpose: 保存本V5 three-Step可执行计划。
- Symbols: this Plan metadata/file blocks。
- Repository evidence: currently tmp draft only; Root reviews before coding。
- Dependencies and consumers: Root executor/Step owners。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: this Plan metadata/file blocks；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: Spec->exact files/RED/GREEN/commit boundaries。
- Error and edge behavior: Review only, no fake Ready/Accepted; each Step commit before next code。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```text
copy exact reviewed final Plan;
run strict/hash;
keep actual execution results in three separate Evidence files rather than changing planned expected counts into claims.;
```
- Verification contribution: strict Plan/hash。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

#### File 16 — `CREATE docs/egon/evidence/2026-11-04-formal-cmmn-foundation.md`
- Purpose: 记录Step1真实store RED/GREEN、schema/static及单commit。
- Symbols: baseline/spec/plan/V8 hashes; actual test logs; exact scope。
- Repository evidence: actual Root outputs only。
- Dependencies and consumers: reviewer/user/later Step2。
- Why now: Step 1 dependency order；tests precede corresponding behavior，contracts/resources precede test execution。
- Contract/signature changes: baseline/spec/plan/V8 hashes; actual test logs; exact scope；preserve existing overloads/callers named in §4/该file。
- Input/output and state mapping: all3 stores separate meaningful RED/GREEN, V7/V8 verification, old23 SQL hashes, new V8 checksum。
- Error and edge behavior: no prefilled self commit hash; no DB/migration/runtime claim。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001；按primary§6.2既有technicalstyle适用，无新依赖/层。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；semanticname/immutablecopies/strictboundary/JDK-Jackson/time/namedexistingfactory，inapplicable annotation/mapper concern按本Step逐项table说明，不强加框架。
- Implementation pseudocode:
```text
record actual counts/exit/log path;
stub-free final diff;
V8 authored once;
no enum/admission edits;
capture introducing commit via git log after commit and clean checkpoint before Step2.;
```
- Verification contribution: Step1 evidence gate。
- After this file: 该文件达到冻结Spec的final职责；同Step GREEN与无占位检查后才可commit。

##### Step-specific Manual Check gate

| Check ID | Applicability | Status | Evidence / required execution proof |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | existingverticalJDBC/sourcepathonly；执行结果只进Evidence，当前designgate |
| MC-REUSE-001 | Applicable | PASS | existingreceipt/verifier/JDBC/hashpatterns；执行结果只进Evidence，当前designgate |
| MC-DEP-001 | Applicable | PASS | POMunchanged；执行结果只进Evidence，当前designgate |
| MC-NAME-001 | Applicable | PASS | CaseContext/Store/Inboxsemantictypes；执行结果只进Evidence，当前designgate |
| MC-VALID-001 | Applicable | PASS | strictrow/context/owner/base/statechecks；执行结果只进Evidence，当前designgate |
| MC-MODEL-001 | Applicable | PASS | immutableSeed/Entry/Result12copies；执行结果只进Evidence，当前designgate |
| MC-CONVERT-001 | Not applicable | N/A | noPO/mapperboundary；执行结果只进Evidence，当前designgate |
| MC-LOG-001 | Not applicable | N/A | no new businesslogging; no payloadlogging；执行结果只进Evidence，当前designgate |
| MC-BEAN-001 | Not applicable | N/A | no newCaseactivation; existingctors retained；执行结果只进Evidence，当前designgate |
| MC-UTIL-001 | Applicable | PASS | JDK/Jackson/JDBCexisting；执行结果只进Evidence，当前designgate |
| MC-JSON-001 | Applicable | PASS | Result12 usesexistingC model; no competingJSON；执行结果只进Evidence，当前designgate |
| MC-TIME-001 | Applicable | PASS | DBprecision/deadline/dueDurationfields；执行结果只进Evidence，当前designgate |
| MC-CONFIG-001 | Not applicable | N/A | no runtimeflags/PaCactivation inStep1；执行结果只进Evidence，当前designgate |
| MC-PATTERN-001 | Applicable | PASS | technicalCommandreceipts/sharedscopeparticipant；执行结果只进Evidence，当前designgate |
| MC-SCOPE-001 | Applicable | PASS | onefinalV8;23oldSQLhash;noenum/admission；执行结果只进Evidence，当前designgate |
| MC-TEST-001 | Applicable | PASS | eachrealstoreassertionRED/GREEN+V7/V8/schema；执行结果只进Evidence，当前designgate |
| MC-BLOCKER-001 | Applicable | PASS | all§11schemafieldsfrozen; nativeS09stillRequired；执行结果只进Evidence，当前designgate |

##### Step-specific Literal Rule gate

| Literal rule | Applicability | Status | Concrete obligation / proof |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | primary§6.2 Rule 1 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 2 | Applicable | PASS | primary§6.2 Rule 2 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 3 | Applicable | PASS | primary§6.2 Rule 3 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 4 | Applicable | PASS | primary§6.2 Rule 4 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 5 | Applicable | PASS | primary§6.2 Rule 5 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 6 | Applicable | PASS | primary§6.2 Rule 6 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 7 | Applicable | PASS | primary§6.2 Rule 7 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 9 | Applicable | PASS | primary§6.2 Rule 9 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 10 | Applicable | PASS | primary§6.2 Rule 10 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |
| Rule 11 | Applicable | PASS | primary§6.2 Rule 11 exactscope；本Step每file的semantictypes/strictvalidation/immutablevalues/现@Beanstyle/既有依赖/Jackson/java.time/config/Adapter/verticalboundary逐项执行；Rule11始终适用，无新层/MP/Flyway替换 |

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: §8 S1 selector；每store独立RED/GREEN日志，之后S1全selector；strictdocs/static/oldSQLhash。
- Expected result: eachstore assertion RED followedby GREEN；最终selector exit0，V8descriptor完整、V7/V8Caseoffstrict、无占位；实际counts只Evidence。
- Failure returns to: owningstore/DDLdescriptor/verifier/testfixture；schema/protocol设计偏差先返回Spec/Root，不增第二migration。
- Completion criteria: 全部Step1files完成、每store有效RED/GREEN、all23oldSQLbyteunchanged、唯一新V8checksum与schema一致、noenum/admissionedit；Scope+17MC+10Rules actualEvidence。
- Rollback: 未部署前revert本Stepcommit；部署后不可编辑已存在V8，Caseoff新binary兼容V7/V8；旧V7binary不能回连宽checks。
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V8__add_formal_cmmn_case_support.sql`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/identity/CaseContextTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/identity/CaseContext.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStoreTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseStartProvenanceStore.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStoreTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CasePlanItemReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInboxTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionInbox.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifier.java`, `docs/egon/spec/2026-11-04-15-50-formal-cmmn-case-server.md`, `docs/egon/plan/2026-11-04-15-50-formal-cmmn-case-server.md`, `docs/egon/evidence/2026-11-04-formal-cmmn-foundation.md`
- Commit: `feat(process): add formal Case persistence foundation`。
- Checkpoint: Root commits immediately，`git log -1 --format=%H`+exactcommitpaths+`git status --short` clean；未提交前不得开始Step2代码。

### Step 2 — 原子交付CMMN发布与完整Direct Case/native/CASEHumanTask

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-201, REQ-202, REQ-203, REQ-204, REQ-205, REQ-206, REQ-207, REQ-208, REQ-209, REQ-301, REQ-302, REQ-303, REQ-304, REQ-305, REQ-306, REQ-307, REQ-308, REQ-309, REQ-310, REQ-311, REQ-312, REQ-401, REQ-402, REQ-403, REQ-405, REQ-406, REQ-407, REQ-408, REQ-409, REQ-410, REQ-411, REQ-414, REQ-416
- Dependencies: Step1真实commit+cleancheckpoint；frozenV8hash已记录，禁止任何DDL。
- Baseline state: fullstores/verifiers/context可用，modelenum/admission仍旧BPMN/DMN，CaseAPI/nativeCasefactory absent。
- Observable outcome: 同一提交中全部CMMNenum/consumer/deploy/load与safeoperablefactory/nativeCase/API/CASEHumanTask闭合，真实directCasebackend源码可用。
- End state: defaultoffprovider和CASEtasks完整，literalBPMNCaseSource仍未admit；Step3才完成linked部分。
- Test-first gate: Required — enum/currentassembly/release positiveassertion RED先行；newtypes compile-only UOE仅uncommitted，使真正behavior assertionsRED；thenfinalGREEN，no placeholdercommit。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11
- Ownership: Step2 direct/native/API owner独占本Step共享release/config/task文件；如分配test子worker只给互不重叠paths，Root整合Maven/commit。不是独占workspace，不revert他人内容。
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`
- Purpose: 先定义新release enum acceptance RED。
- Symbols: OrchestrationContractTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: OrchestrationContractTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
assertDoesNotThrow(() -> Enum.valueOf(ProcessModelTypeEnum.class,"CMMN"));
retain old enum names/value-object checks;
do not touch CaseContractTest or Case11types.;
```
- Verification contribution: OrchestrationContractTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`
- Purpose: 装配行为RED与完整新constructors兼容。
- Symbols: ProcessApiConfigurationTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessApiConfigurationTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
with mocked owning3engineconfigs/identity/permission filters + cases flags, assert realCase beans/route present onlywhenready;
assert false defaults/unsupportedflag combinations;
old factories/constructors remain exact;
first RED is missing behavior, not missingfixture.;
```
- Verification contribution: ProcessApiConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 3 — `MODIFY stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java`
- Purpose: 在既有fake transport测试CMMN releasecorrelation。
- Symbols: HttpCaseClientTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: HttpCaseClientTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
reuse FakeHttpClient (no HttpServer): publish CMMN with empty inputs;
require matching type/key/hash/empty schema;
preserve old BPMN/DMN checks and all existing Casewire/cancellation/error tests.;
```
- Verification contribution: HttpCaseClientTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 4 — `MODIFY stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessModelTypeEnum.java`
- Purpose: 原子三路发布合同的一部分。
- Symbols: enum values BPMN,CMMN,DMN。
- Repository evidence: existing plain JDK enum, serialized names only。
- Dependencies and consumers: ProcessController mapping/SDK publish/AS explicit BPMN or DMN filters。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: enum values BPMN,CMMN,DMN；已有constructors/defaults不变。
- Input/output and state mapping: add CMMN; all11 Case types and their enum orders unchanged。
- Error and edge behavior: no ordinal/Jackson/validation/Lombok dependency; no acceptance half-commit。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add CMMN by name;
inspect all consumers;
keep AS filters unchanged because they explicitly select BPMN or DMN;
run C contract regression.;
```
- Verification contribution: OrchestrationContractTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 5 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`
- Purpose: 只扩release publish三路关联校验。
- Symbols: publish(PublishProcessReleaseCommand)。
- Repository evidence: current condition BPMN || !inputs.empty; CaseSDKbranch already committed。
- Dependencies and consumers: existing release client; no change to Case codec/3methods/shared transport。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: publish(PublishProcessReleaseCommand)；已有constructors/defaults不变。
- Input/output and state mapping: BPMN exactrequestedinputs; DMN existing optionalrequestedinputs behavior; CMMN both requested/published inputs empty。
- Error and edge behavior: exhaustive switch, no nonBPMN->DMN; all oldlimits/errors/ctor/helpers and Casewire bytepreserved。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
switch requested.type(): BPMN require same inputs;
DMN preserve existing input inference rule;
CMMN require empty inputs and exact hash/type/key;
retain fullmodels count/correlation;
no new networkcall.;
```
- Verification contribution: HttpCaseClientTest; CaseResponseCodecTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`
- Purpose: 锁三路prepare/deploy/load与oldconstructor/hash。
- Symbols: ProcessReleaseServiceTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessReleaseServiceTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CMMN emptyinputs/closedmodel/exacttenant-id-hash/deployment accept onlyready;
invalidflag/nativefactory/sharedTM reject;
BPMN/DMN manifest golden unchanged;
call every old3..8arg overload;
no CaseSource admission untilStep3.;
```
- Verification contribution: ProcessReleaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`
- Purpose: CMMN publicdelegate与BPMN/DMN不变。
- Symbols: ProcessModelValidatorTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessModelValidatorTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
validateCmmn delegates closed semanticmodel;
add visibility/use assertions while all existingBPMN/DMN/worker/human grammar tests retain;
caseSource stillrejected in thisStep.;
```
- Verification contribution: ProcessModelValidatorTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java`
- Purpose: 仅暴露跨现vertical所需immutable metadata。
- Symbols: public class/accessors/nested Action/PlanItemPolicy asneeded。
- Repository evidence: currently package-private immutable bytes/maps/sets and derivedoperatorunion。
- Dependencies and consumers: Case service/snapshot/governedCMMNtask。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: public class/accessors/nested Action/PlanItemPolicy asneeded；已有constructors/defaults不变。
- Input/output and state mapping: same4arg constructor/closedmetadata/copies; no grammar/role-policy change。
- Error and edge behavior: do not replace model with generic Map/newcarrier or changeexistingfixtureIDs。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
make required type/read methods/nested policy visible;
preserve constructor/copies/actions/operators exactly;
CmmnModelValidator body remains untouched (no mechanical edit).;
```
- Verification contribution: CmmnModelValidatorTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`
- Purpose: public/internalmodel三路合同。
- Symbols: Type adds CMMN; oldconstructors preserved。
- Repository evidence: currently BPMN/DMN + existingmetadata getters。
- Dependencies and consumers: Release Definition/ModelResource; all oldBPMN constructors。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: Type adds CMMN; oldconstructors preserved；已有constructors/defaults不变。
- Input/output and state mapping: add CMMN type only now; CaseSource metadata comesStep3, not premature incomplete admission。
- Error and edge behavior: do not misrepresent CMMN as DMN or duplicate Casepolicy。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add enum CMMN;
keep oldconstructors/getters exactly;
release PreparedModel will explicitly carry either validatedProcessModel or validatedCaseModel;
nobusinessState.;
```
- Verification contribution: ProcessReleaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`
- Purpose: 外部模块可调用closed CMMN验证。
- Symbols: validateCmmn(byte[],String)->ValidatedCaseModel。
- Repository evidence: samepackage CmmnModelValidator.validate already exists。
- Dependencies and consumers: release verifier/native policy reader。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: validateCmmn(byte[],String)->ValidatedCaseModel；已有constructors/defaults不变。
- Input/output and state mapping: delegate exact unchanged safeXML+semanticwalker; samegenericinputerror。
- Error and edge behavior: no new EL/Form/import/historyoverride grammar; preserve humanTask(Element) body。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add public delegate new CmmnModelValidator().validate(bytes,key);
keep allBPMN/DMN entrybehavior and humanTask parser unchanged;
do not introduce sourcegrammar beforeStep3.;
```
- Verification contribution: CmmnModelXmlTest; CmmnModelValidatorTest; ProcessModelValidatorTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 11 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/VerifiedProcessModels.java`
- Purpose: 精确CMMN原生definition与bytes核验。
- Symbols: old3arg ctor; CMMN-aware overload; caseModel(tenant,Definition)。
- Repository evidence: existing process/runningProcess/decision hash-recheck pattern。
- Dependencies and consumers: ReleaseService/Case service/nativeCaseHumanTask; oldProcess/Decision/Worker callers。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: old3arg ctor; CMMN-aware overload; caseModel(tenant,Definition)；已有constructors/defaults不变。
- Input/output and state mapping: verify typeCMMN/id/deployment/version/key/tenant/resource SHA then validateCmmn。
- Error and edge behavior: old3arg callers mustnot need CMMN; notfound/mismatched model safeexception; bounded512KiB。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add explicit cmmn cfg overload and exactCMMNrepository query/resource read;
use existing checkedBytes/hash;
return closedmetadata;
oldconstructors/paths preserve.;
```
- Verification contribution: ProcessReleaseServiceTest; FlowableCaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 12 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`
- Purpose: 一次完成可安全执行CMMN的发布链。
- Symbols: preserve3/4/5/6/7/8arg ctors; new3engine canonicaloverload; prepare/deploy/load/manifest。
- Repository evidence: current process+dmn fields; oldcallers Execution/Decision/Worker/Pinned factories/HumanTask/tests。
- Dependencies and consumers: ProcessSecurityConfiguration factory newoverload; oldcallers unchanged。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve3/4/5/6/7/8arg ctors; new3engine canonicaloverload; prepare/deploy/load/manifest；已有constructors/defaults不变。
- Input/output and state mapping: PreparedModel carries explicit Type/key/bytes/hash/inputs and mutuallyexclusive BPMN/DMN vsCase metadata; CMMNinputs empty。
- Error and edge behavior: requireCase+humanpublish+true nativefactory/sharedDS/TM/schema/history readiness; no constantfalse stub; allacceptedmodels operable; no Source yet。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
switch all model-type branches BPMN/CMMN/DMN;
CMMN deploy exactbytes and exacttenant thenqueryone matchingkey/id/version/deployment;
preserve oldmanifest ordering/field encoding/digest;
new full ctor verifies CMMN actualengine;
oldctors delegate casesdisabled while retaining old semantics;
no model enum halfstep.;
```
- Verification contribution: ProcessReleaseServiceTest; ProcessControllerTest; digestgolden。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 13 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`
- Purpose: 新增Case commandkind但保持旧ownerSTART查询。
- Symbols: Kind CASE_START,CASE_PLAN_ITEM,CASE_COMPLETION_SERVICE。
- Repository evidence: existing insert/lock/apply/fullPK/decisionresult specialcase。
- Dependencies and consumers: FlowableCaseService and latercompletion; oldProcess/Decision/Agent/task unchanged。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: Kind CASE_START,CASE_PLAN_ITEM,CASE_COMPLETION_SERVICE；已有constructors/defaults不变。
- Input/output and state mapping: newKinds use existing nondecisionreceipt lifecycle; no generalized STARTresource scan forCase。
- Error and edge behavior: legacy START remainsBPMN-only; taskOrigin existingPROCESS semantics unchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add exact3Kinds;
preserve insert/lock/apply and DECISIONresult condition;
newCase app uses provenance lookup forCaseID/executor command, never overload startByInstance/taskOrigin incorrectly.;
```
- Verification contribution: ProcessTaskOriginTest; FlowableCaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 14 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccessTest.java`
- Purpose: 定义CaseNativeAccessTest的真实行为RED/GREEN。
- Symbols: CaseNativeAccessTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CaseNativeAccessTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
assert exact sharedDS/TM/schema/native scope;
includeEnded before history;
runtime REV/native FOR UPDATE fence;
unsupportedstate/kind rejects;
no direct native UPDATE;
missing/ambiguous history fails.;
```
- Verification contribution: CaseNativeAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 15 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshotTest.java`
- Purpose: 定义CaseSnapshotTest的真实行为RED/GREEN。
- Symbols: CaseSnapshotTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CaseSnapshotTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
golden RUNTIME/HISTORY domain versions;
same-state ABA has different REV/version;
exhaustive sixPI/threeCase states, milestone occurredTime mapping;
terminal sparse zeroactions;
Result handoff after runtime deletion;
no naive/numeric time acceptance.;
```
- Verification contribution: CaseSnapshotTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 16 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseServiceTest.java`
- Purpose: 定义FlowableCaseServiceTest的真实行为RED/GREEN。
- Symbols: FlowableCaseServiceTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: FlowableCaseServiceTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
direct USER/SERVICE owner=executor;
guarded start/get/actions, hashreplay, perfieldbinding, sameownerrealm/currentoperatorunion and serviceowneronly;
command->scope->native order;
terminalHISTORY resultingVersion allowed but rejected asfuturefence;
anotherclaimedUSER cannotSUPPterminate;
rollback allfacts.;
```
- Verification contribution: FlowableCaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 17 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccess.java`
- Purpose: 精确scope原生读与row fence。
- Symbols: constructor(DataSource,SpringCmmnEngineConfiguration); runtime/history observations; planItem fence。
- Repository evidence: HumanTaskNativeAccess + pinned Flowable8 query/REV evidence。
- Dependencies and consumers: CaseSnapshot/FlowableCaseService; no externalAPI。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: constructor(DataSource,SpringCmmnEngineConfiguration); runtime/history observations; planItem fence；已有constructors/defaults不变。
- Input/output and state mapping: tenant/Case/definition/PIID/element/definitionID + runtimeEntityREV; includeEnded then exacthistory。
- Error and edge behavior: no historyREVmutationfence; unsupportedstates503; no nativeSQLUPDATE; lockscopebeforeTask/PI。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
query exactCase tenant/def;
planItemInstanceQuery.includeEnded();
historyfilters exacttenant/Case/definition;
fence SELECT ... act_cmmn_ru_plan_item_inst ... FOR UPDATE OF p only after sharedCaseScope;
use CmmnRuntimeService finiteaction APIs;
refresh runtime or exactterminalhistory after mutation.;
```
- Verification contribution: CaseNativeAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 18 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshot.java`
- Purpose: nativefacts到immutableC投影/opaqueversion。
- Symbols: CaseSnapshot immutable construction/version/projection。
- Repository evidence: HumanTaskSnapshot + frozenSpec §7 domains/state table。
- Dependencies and consumers: FlowableCaseService and role-limited get/action results。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CaseSnapshot immutable construction/version/projection；已有constructors/defaults不变。
- Input/output and state mapping: definitionID vs planItemelementID distinct; nativecase3/publicstates, nativePI6; runtimeREV/hash/domain; historyreadonly。
- Error and edge behavior: ACTIVEfinishedAtnull; terminalnonnull/noactions; unknown/suspended statesreject; milestonecompleted needsoccurredTime; neverclaimAUTOcause。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
validate provenance/model/native allrefs;
hash length-prefixed exact runtime domain including REV;
history distinctdomain;
availableActions=state∩fixedmetadata∩freshUSERrole and assigneeprotection;
preserve publicCconstructor invariants and actualInstantprecision.;
```
- Verification contribution: CaseSnapshotTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 19 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseService.java`
- Purpose: direct Case真实本地应用服务。
- Symbols: ctor(process,cmmn,dmn,validator,provenance,planReceipts); start/get/actOnPlanItem。
- Repository evidence: existing ProcessExecutionService/FlowableHumanTaskService ownedTransaction patterns。
- Dependencies and consumers: CaseController; nativepolicy reader; Step3 lateradds internal linkedentry。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ctor(process,cmmn,dmn,validator,provenance,planReceipts); start/get/actOnPlanItem；已有constructors/defaults不变。
- Input/output and state mapping: CaseContext+existingCcommands -> exactC DTO/Result12; sameSource/TM/synchronousAUDIT。
- Error and edge behavior: publicownsTX/noambient; currentcontext before/aftermutation; replaystillfreshauth; zero native aftervalidreplay; no newstarts blocksappliedreplay。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
start lockCASE_START, comparehash, exactrelease, nativecaseDefinitionId/tenant/business start, baseAPPLIED+provenance atomic;
get safeprovenance owner/operatorrealm;
act USER/roles command->scope->fence/version/action/assignee->native mapped action->freshRUNTIMEor terminalHISTORYresult->immutable+APPLIED;
no linked method stub inStep2.;
```
- Verification contribution: FlowableCaseServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 20 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehaviorTest.java`
- Purpose: 真实factory/execute3/candidate阶段RED。
- Symbols: PinnedCaseHumanTaskActivityBehaviorTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: PinnedCaseHumanTaskActivityBehaviorTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
markedHumanTask getsgovernedbehavior;
unmarked legacy remainsnative;
before super reject migration/interceptor/rawassignments/unsupportedEL/modelsource;
addfixed groups noEL at exact candidate hook;
cfg/parser/delgates identity;
rollback assertions viafake/native-source boundary.;
```
- Verification contribution: PinnedCaseHumanTaskActivityBehaviorTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 21 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehavior.java`
- Purpose: 原生CMMN受管人工任务Adapter。
- Symbols: extends HumanTaskActivityBehavior(HumanTask); execute(CommandContext,PlanItemInstanceEntity,MigrationContext); handleCandidateGroups exact6args。
- Repository evidence: local8 exacthooks; native rawassignee/owner evaluation precedes insert; candidateGroups afterinsert/historybefore。
- Dependencies and consumers: PinnedCaseActivityBehaviorFactory; existing CmmnTaskService/identityhistory。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: extends HumanTaskActivityBehavior(HumanTask); execute(CommandContext,PlanItemInstanceEntity,MigrationContext); handleCandidateGroups exact6args；已有constructors/defaults不变。
- Input/output and state mapping: exactcurrentcfg/tenant/Case/definition/metadata; ACTIONsolelaneemptyform -> role:<id> groups。
- Error and edge behavior: prevalidate BEFORE super: migrationnull/interceptornull/blockingtrue/rawclaims/listeners/taskIdVariable/ELabsent; nohistoryUSERrestore。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
execute3 verifies exactnativeContext/releasedmodel/provenance, then super;
candidate hook verifies populatedTask scope and adds fixed role groups without super/ExpressionManager;
nativepolicy reads participants in currentTX, never publicCaseget/no-ambient entry.;
```
- Verification contribution: PinnedCaseHumanTaskActivityBehaviorTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 22 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseActivityBehaviorFactory.java`
- Purpose: 只替换markedCMMNHumanTask创建钩子。
- Symbols: extends DefaultCmmnActivityBehaviorFactory; createHumanTaskActivityBehavior(PlanItem,HumanTask)。
- Repository evidence: native8 factory/parser lifecycle and existingBPMNfactory pattern。
- Dependencies and consumers: ProcessStorageConfiguration after-init installer。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: extends DefaultCmmnActivityBehaviorFactory; createHumanTaskActivityBehavior(PlanItem,HumanTask)；已有constructors/defaults不变。
- Input/output and state mapping: marker present->governedbehavior; absent->super; remainingfactory methods inherited。
- Error and edge behavior: ExpressionManager/ClassDelegateFactory must explicitly injected; unmarkedlegacyoff unchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
return marked ? new PinnedCaseHumanTaskActivityBehavior(task) : super.createHumanTaskActivityBehavior(planItem,task);
retain othernativebehaviors and initializeddependencies;
no circular eager appservice lookup.;
```
- Verification contribution: PinnedCaseHumanTaskActivityBehaviorTest; CaseStorageConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 23 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccessTest.java`
- Purpose: CASE真实服务回归与RED。
- Symbols: HumanTaskNativeAccessTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: HumanTaskNativeAccessTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASE native scope cmmn/scopeId/def/subscope/taskDefKey;
sourceownerrealm join;
noTasklockbeforescope;
PROCESS SQL unchanged.;
```
- Verification contribution: HumanTaskNativeAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 24 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshotTest.java`
- Purpose: CASE真实服务回归与RED。
- Symbols: HumanTaskSnapshotTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: HumanTaskSnapshotTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASE model/ownerproof/scope and version bind immutable provenance, exact native cmmn scope ID and runtime task REV;
assert sole lane ACTION with empty form and exact HumanTask definition ID distinct from plan item element ID;
retain original PROCESS constructor and golden version hash unchanged;
```
- Verification contribution: HumanTaskSnapshotTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 25 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResultTest.java`
- Purpose: CASE真实服务回归与RED。
- Symbols: HumanTaskActionResultTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: HumanTaskActionResultTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASE Result scope/release/correlation and ownerreceipt distinct;
alloldPROCESS constructors/defaults stable;
emptyCASEform only.;
```
- Verification contribution: HumanTaskActionResultTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 26 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStoreTest.java`
- Purpose: CASE真实服务回归与RED。
- Symbols: HumanTaskReceiptStoreTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: HumanTaskReceiptStoreTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASEwriter/parser ownerSTARTorCASE_START + independentexecutor;
linkedownerresource=parentPID;
replay currentANYreleasedlane role (role7->role8 allowed, allrevoked denied);
oldselectedrole historicalaudit only.;
```
- Verification contribution: HumanTaskReceiptStoreTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 27 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java`
- Purpose: CASE真实服务回归与RED。
- Symbols: FlowableHumanTaskServiceTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: FlowableHumanTaskServiceTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASE claim/unclaim/transfer/complete use CmmnTaskService and sharedscopebeforenativelock;
replaycannotearlyreturn, storedscope/source/assignee+freshroles checked;
PROCESS behavior unchanged;
anotherUSERclaim protection.;
```
- Verification contribution: FlowableHumanTaskServiceTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 28 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccess.java`
- Purpose: 增加explicitCASEscope和CMMNfacade。
- Symbols: preserve(DataSource,SpringProcessEngineConfiguration); CMMN-aware overload; find/candidates。
- Repository evidence: current PROCESS SQL scope and rowfence are strict。
- Dependencies and consumers: FlowableHumanTaskService; Step1provenance sharedscope。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve(DataSource,SpringProcessEngineConfiguration); CMMN-aware overload; find/candidates；已有constructors/defaults不变。
- Input/output and state mapping: CASE scope_type=cmmn/scope_id=CaseID/scope_definition_id=CaseDef/sub_scope_id=PIID/task_def_key=HumanTaskdefinitionID。
- Error and edge behavior: firstunlockedsource discovery only; acquireCaseScope before any FOR UPDATE; reread source/task under lock; PROCESS SQL unchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
dispatch explicitscope;
CASEquery joinsprovenance+owner/executor/release and uses currentownerrealm, CmmnTaskService query;
preserve cursorC order/limit<=50 and candidate/currentassignee rules;
source discovery isnotauth.;
```
- Verification contribution: HumanTaskNativeAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 29 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java`
- Purpose: 复用现Task快照承载CASEowner/source。
- Symbols: preserve original constructor; explicitCASE overload/immutableorigin proof。
- Repository evidence: current constructor(Task,links,TaskOrigin,Definition,ValidatedProcessModel)。
- Dependencies and consumers: task service/receipt/action result; existingtests oldctor。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve original constructor; explicitCASE overload/immutableorigin proof；已有constructors/defaults不变。
- Input/output and state mapping: scope/model/startkind/sourceownerresource separated fromCaseID; policy ACTIONsolelane; actualTaskEntityREV。
- Error and edge behavior: ownerrequester!=SERVICEexecutor; no rawnativeauthority fromvariables; preserve oldPROCESS hash/domain。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add explicitCase snapshot constructor/factory taking immutableprovenance+ValidatedCaseModel and exactCMMNnativeTask;
samepolicytype for existingAccessRules;
copy allfields/identitylinks and include exactCase/provenance/REV source inCaseversion;
oldconstructor delegates originalPROCESSdefaults unchanged.;
```
- Verification contribution: HumanTaskSnapshotTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 30 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResult.java`
- Purpose: CASEtask结果/审计不再硬编码PROCESS。
- Symbols: preserve applied/formTargets public usages; Case-aware overload。
- Repository evidence: current static applied() and Entry used by existingtests。
- Dependencies and consumers: HumanTaskReceiptStore/FlowableHumanTaskService。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve applied/formTargets public usages; Case-aware overload；已有constructors/defaults不变。
- Input/output and state mapping: scopeCASE/releaseCase/correlationCase; requesterownerstartref; currentrole selection anyreleasedlane, historical selectedrole stored。
- Error and edge behavior: CASEformempty/APPROVALnotallowed; oldPROCESSdefaults preserved。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
emit existingHumanTaskActionReceiptDTO with CASEscope;
pass exactorigin/provenance modeltype/startkind instead of assumingBPMNSTART;
preserve assignee audit and currentauth/policy versions;
oldapplied overload retainsPROCESS.;
```
- Verification contribution: HumanTaskActionResultTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 31 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStore.java`
- Purpose: CASE写入与双来源安全replayjoin。
- Symbols: preserve constructor(DataSource,String); reserve/apply/joined/entry/lockCommand CASEbranches。
- Repository evidence: existing hardcodedBPMN/START/ownerresource=scope iswronglinked。
- Dependencies and consumers: taskservice/approvalverification; oldtwoBeanfactorycallers。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve constructor(DataSource,String); reserve/apply/joined/entry/lockCommand CASEbranches；已有constructors/defaults不变。
- Input/output and state mapping: CMMN model/startkind from immutableCaseproof; requester remainsOWNER; exactCase release/correlation; executorCASE_START checked independently。
- Error and edge behavior: CASEreplay must acquireCaseScope and recheck fresh realm/role/deadline before return; historical role must belongreleasedlane butcurrentANYeligiblelane role suffices; PROCESSshortcutunchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
split explicitPROCESSvsCASEjoin onlywhere needed;
linkedownerSTARTresource=parentPID andexecutorCASE_STARTresource=CaseID;
checkbase/result/provenance/hash/assignee audit;
return storedCASEreceipt onlyaftercaller validates currentscope policy;
approval remainsBPMN-only.;
```
- Verification contribution: HumanTaskReceiptStoreTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 32 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java`
- Purpose: CASEHumanTask复用现规则/服务而非平行API。
- Symbols: preserve4arg ctor; Case-aware overload; query/load/mutate。
- Repository evidence: current PROCESS-only query/load and early replayreturn。
- Dependencies and consumers: existingHumanTaskController; newrealconfig factory。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: preserve4arg ctor; Case-aware overload; query/load/mutate；已有constructors/defaults不变。
- Input/output and state mapping: scope selectsBPMN TaskService or CmmnTaskService; Caseowner/provenance -> releasedACTIONpolicy。
- Error and edge behavior: CASEreplay commanddiscovery->CaseScope->provenance/release/realm/currentroles/deadline before storedreturn; no native dependency oncomplete replay; PROCESSunchanged。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
CASEclaim/unclaim/complete via exactCmmnTaskService APIs;
transfer delete/addGroupIdentityLink(taskId,roleGroup,"candidate"), then TaskEntity.forceUpdate+saveTask;
CASEemptyform/solelane;
publicresource guardoutsideTX;
scope lock beforeanyTask/PI fence.;
```
- Verification contribution: FlowableHumanTaskServiceTest; HumanTaskAccessRulesTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 33 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccessTest.java`
- Purpose: 真正resource/currentactor/freshroles边界RED。
- Symbols: TianjiCaseAccessTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: TianjiCaseAccessTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
assert access.require currentPDP/identitybeforeTX;
USERsnapshot tenant/issuer/app/catalog/deadline/auth/policy;
SERVICEonlystart/get with rolesempty;
no actor swap/roleclaims;
expiry and ambientTX reject.;
```
- Verification contribution: TianjiCaseAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 34 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccess.java`
- Purpose: Case操作currentcontext adapter。
- Symbols: require(String)->CaseContext。
- Repository evidence: TianjiProcessAccess realguard and TianjiHumanTaskAccess freshsnapshot code。
- Dependencies and consumers: CaseController; no DB/native service。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: require(String)->CaseContext；已有constructors/defaults不变。
- Input/output and state mapping: USERfreshrole/context; SERVICE identitydeadline butnoUSERroles; sameownerrealm enforced lateragainstprovenance。
- Error and edge behavior: no ambientTX; no historicalUSERcontext restore orPDP insideDB; sameidentity/token/issuer freshness。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
call existing access.require(resource);
verify current principal/snapshot like taskadapter, project boundedroleIDs/authVersion/policyVersion/deadline;
service uses verifiedcurrentidentity andemptyroles;
no permission grantedbyconstructingContext.;
```
- Verification contribution: TianjiCaseAccessTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 35 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`
- Purpose: 完整声明必要Casepublic资源/权限。
- Symbols: CASE_START/READ/PLAN_ITEM_ACT, exact APIkeys/scopes and annotations/messages。
- Repository evidence: existing PaC PermissionDefinition/PermissionMessage pattern。
- Dependencies and consumers: CaseController/methodsecurity/user-scopepolicy/permissionartifact。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CASE_START/READ/PLAN_ITEM_ACT, exact APIkeys/scopes and annotations/messages；已有constructors/defaults不变。
- Input/output and state mapping: tianji.api.process.case.start/get/actPlanItem; tianji:case:start/read/act。
- Error and edge behavior: start/get USER+SERVICE; actUSERonly; registration notgrant; no realrole/client activation。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add exactpermissiondefinitions/localizedmessages/constants;
preserve oldkeys/scopes;
do not edit externaltargets or register/activate/grant actualcatalog.;
```
- Verification contribution: ProcessPermissionIntegrationTest; CaseControllerTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 36 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/CaseControllerTest.java`
- Purpose: 三route+actualprovider/OAS/strictbody/error矩阵RED。
- Symbols: CaseControllerTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CaseControllerTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
standaloneMockMvc actualCaseController/CaseRequestBodyAdvice/ProcessApiErrors+syntheticguard;
exact200/no-store/requiredfields/UTCZ/result12, all9code-statuspairs, malformed/unknown/duplicate/numeric/oversize/role/realm/service checks;
parse immutable packagedOAS;
no listener.;
```
- Verification contribution: CaseControllerTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 37 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseRequestBodyAdvice.java`
- Purpose: 严格boundedCase input。
- Symbols: RequestBodyAdvice supports exact3Ccommands; nestedTooLarge。
- Repository evidence: HumanTaskRequestBodyAdvice + ProcessRequestBodyAdvice existingJackson3/readNBytespattern。
- Dependencies and consumers: CaseController existingCrecords; ProcessApiErrors413。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: RequestBodyAdvice supports exact3Ccommands; nestedTooLarge；已有constructors/defaults不变。
- Input/output and state mapping: max64KiB fixed/streamed; exactmember sets; scalar textual/finiteaction; requirednonnull。
- Error and edge behavior: unknown/duplicate/trailing/coercion/numericenum rejects; depth8/string4096/token256; no identityfields。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
parseboundedbytes with strictJackson3 and closedproperty sets fromexistingCcommands;
replay bytes toexistingconverter;
require correcttext shape;
throw existinggenericinvalid or CaseTooLarge;
no competingJSON ornewannotations in C.;
```
- Verification contribution: CaseControllerTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 38 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseController.java`
- Purpose: 完成immutableSDK已定义的3POSTprovider。
- Symbols: start/get/actOnPlanItem; conditionalCaseflag; PermissionResource/RequiresPermissions。
- Repository evidence: ProcessController/HumanTaskController realstyle; CaseSDK/OAS exactpaths。
- Dependencies and consumers: CaseAccess+FlowableCaseService; no fetchforwardAPI。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: start/get/actOnPlanItem; conditionalCaseflag; PermissionResource/RequiresPermissions；已有constructors/defaults不变。
- Input/output and state mapping: existingCcommands directly -> existingC DTO/Result12; exactpaths/operationidentity/no-store。
- Error and edge behavior: identity/roles before serviceTX; start/getUSERorSERVICE, actUSERonly; no extra claims/body/fields。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
map POST /api/tianji/v1/cases/start,get,act-plan-item;
call CaseAccess.require(exactresource) then realservice;
return ResponseEntity.ok/noStore/applicationJson;
no wrapper or extraendpoint.;
```
- Verification contribution: CaseControllerTest; ProcessPermissionIntegrationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 39 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`
- Purpose: Case意外异常safe503、旧endpoint500不变。
- Symbols: TooLarge set; unexpected(RuntimeException,HttpServletRequest)。
- Repository evidence: currentglobaltyped exceptionhandlers+no-store/nullcorrelation。
- Dependencies and consumers: CaseController and oldAPIcontrollers。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: TooLarge set; unexpected(RuntimeException,HttpServletRequest)；已有constructors/defaults不变。
- Input/output and state mapping: matchingHandler HandlerMethod.beanType CaseController ->503 dependency; others old500。
- Error and edge behavior: do not add localRuntimeException handler swallowing specific404/409/401/403; no rawcause/body leaks。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
include CaseAdvice.TooLarge in413;
inunexpected inspect HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE HandlerMethod (not untrustedbody) and chooseCase503;
preserve alltypedhandlers/codepairs andoldendpointbehavior.;
```
- Verification contribution: CaseControllerTest; ProcessControllerTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 40 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseStorageConfigurationTest.java`
- Purpose: 配置/权限/发布实际集成静态与Mock回归。
- Symbols: CaseStorageConfigurationTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: CaseStorageConfigurationTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
realCMMNcfg sameDS/TM/schema/emptybeans/synchronousAUDIT;
afterInit installer wires expression/classdelegate and BOTH cfg/parserfactory pointers;
Caseoff noinstaller;
unexpectedcustomfactory/interceptor fails.;
```
- Verification contribution: CaseStorageConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 41 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`
- Purpose: 配置/权限/发布实际集成静态与Mock回归。
- Symbols: ProcessStorageConfigurationTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessStorageConfigurationTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
cases/newstarts/APIservlet/humanTask flags implications;
actualafterInit factory registration and priority;
no engine/app startup.;
```
- Verification contribution: ProcessStorageConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 42 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`
- Purpose: 配置/权限/发布实际集成静态与Mock回归。
- Symbols: ProcessSecurityConfigurationTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessSecurityConfigurationTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
Casepaths protectedonlywhenenabled;
USER/SERVICEprincipal classification and actUSERonly;
legacydenyalloff/health unchanged.;
```
- Verification contribution: ProcessSecurityConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 43 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`
- Purpose: 配置/权限/发布实际集成静态与Mock回归。
- Symbols: ProcessPermissionIntegrationTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessPermissionIntegrationTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
assert Case PaC API resource keys and permission constants;
assert start/get support USER and SERVICE while act is USER-only;
assert scopes match base YAML mappings without registering or granting a live catalog.;
```
- Verification contribution: ProcessPermissionIntegrationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 44 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java`
- Purpose: 配置/权限/发布实际集成静态与Mock回归。
- Symbols: ProcessControllerTest。
- Repository evidence: existing adjacent JUnit/Mockito/MockMvc fixtures; CREATE absent baseline。
- Dependencies and consumers: named production owner + existing test stack。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: ProcessControllerTest；已有constructors/defaults不变。
- Input/output and state mapping: synthetic actor/native/JDBC/HTTP facts -> exact result and rejected side effects。
- Error and edge behavior: finite; no listener/DB; compiler/fixture errors never RED。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
publish CMMN and assert model type/key/hash response;
read same release and assert empty CMMN inputs and exact binding;
retain BPMN/DMN schema/digest/status/header regression assertions.;
```
- Verification contribution: ProcessControllerTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 45 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`
- Purpose: 真实after-init nativefactory安装与flagguard。
- Symbols: caseStorageGuard; workerWebBoundaryGuard; existing processguard。
- Repository evidence: realfactories here, no ProcessApiConfiguration class。
- Dependencies and consumers: CmmnSpringengine/parser + OutboxsharedDS/TM; Config tests。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: caseStorageGuard; workerWebBoundaryGuard; existing processguard；已有constructors/defaults不变。
- Input/output and state mapping: Caseon exactstorage/nativeAUDIT andEngineConfigurator(afterInit,maxpriority); offlegacyunchanged。
- Error and edge behavior: verify expecteddefaultfactory/parseridentity; explicitExpressionManager/ClassDelegateFactory; BOTH setters; rejectnonnullcustominterceptor forgovernedpath, no clearinglegacy。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
add case flags implication;
register EngineConfigurator onlyCaseon;
configure-afterInit constructPinnedCasefactory, setdependencies, set cfg+parser and assertsame;
leave processFactory existingnoarg untilStep3;
avoideager service->engine cycle via typedObjectProvider where needed.;
```
- Verification contribution: CaseStorageConfigurationTest; ProcessStorageConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 46 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`
- Purpose: 把真实release/Case/task服务接到现factory。
- Symbols: processReleaseService explicit3enginector; nestedCaseConfiguration namedbeans; CASE-aware taskfactory。
- Repository evidence: actualexisting root/nested @Bean factories + old overload callers。
- Dependencies and consumers: SpringProcess/Cmmn/Dmn engines/currentguards/verifiedstores。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: processReleaseService explicit3enginector; nestedCaseConfiguration namedbeans; CASE-aware taskfactory；已有constructors/defaults不变。
- Input/output and state mapping: flags defaultoff; CaseenabledV8ready; newstarts=>Case+humanTasks; publication=>humanpublish/nativefactory。
- Error and edge behavior: no eagercycle, missingcollaborators/readiness failclosed; no futureRegistry/Source stub; oldCaseoffworksV7/V8。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```java
wire CaseContext access/service/stores and CMMN-aware release/models/task constructors fromexactengineconfigs;
add protectedCasepaths andendpointauthenticationpolicy;
methodPaC limitsactUSER;
preserve twoexistingreceiptBeanfactorysharing andalloldconstructors.;
```
- Verification contribution: ProcessApiConfigurationTest; CaseStorageConfigurationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 47 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`
- Purpose: 必要新flags与Caseuser-scopepolicy默认值。
- Symbols: stellar.tianji.process.cases.enabled/new-starts-enabled; Casepermissionscope mappings。
- Repository evidence: only actualPSbaseyaml; existinghumanTask flags alreadyfalse。
- Dependencies and consumers: Environment/Caseconfig; Step3lateradds source/pollerfields。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: stellar.tianji.process.cases.enabled/new-starts-enabled; Casepermissionscope mappings；已有constructors/defaults不变。
- Input/output and state mapping: bothCaseflagsfalse; no realtenant/roles/source/client configuration。
- Error and edge behavior: no new profiles or activation/grants; keep alloldkeys。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```yaml
add only twofalseflags and necessary permission->scope mappings;
use existinguser-scope-policy structure;
parsekeyparity/disabledassembly;
Step3appendempty sources/boundedmaintenance settings tosamegroup.;
```
- Verification contribution: ProcessStorageConfigurationTest; ProcessPermissionIntegrationTest。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

#### File 48 — `CREATE docs/egon/evidence/2026-11-04-direct-case-server.md`
- Purpose: 记录完整Step2语义与source/native边界。
- Symbols: actualRED/GREEN, constructorcallers, enumconsumer audit, OAS/permission/config proof。
- Repository evidence: actualRoot outputs only。
- Dependencies and consumers: Rootreview/Step3precondition。
- Why now: tests先于对应finalbehavior，Step1契约已commit；本Step完整三路admission/nativewiring一起GREEN。
- Contract/signature changes: actualRED/GREEN, constructorcallers, enumconsumer audit, OAS/permission/config proof；已有constructors/defaults不变。
- Input/output and state mapping: directprovider/nativeadapter/source-test results; no genuinePGclaim。
- Error and edge behavior: no omittedCasewire changes, no laterDDL, no selfcommit hash prefill。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；root现technicalstyle，无newframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；逐项执行本Step矩阵，语义命名/显式guards/immutablevalues/现@Bean注入/JDKJackson/java.time/flags/Adapter；noMPconversion。
- Implementation pseudocode:
```text
recordrealcounts/logs/23oldSQL+newV8hashunchanged/immutableCaseOASchecks;
comparealltypeconsumers/oldctors;
nativePG/S09 RuntimeUnverified;
introducingcommitlookup afterexactcommit.;
```
- Verification contribution: Step2 evidence。
- After this file: 该file依赖契约明确；uncommittedcompile-only bodies不得作为final；Step2全部focusedGREEN与静态审查后commit。

##### Step-specific Manual Check gate

| Check ID | Applicability | Status | Evidence / required execution proof |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | exactexistingapi/security/release/task/execution/configpaths；actualresults recordedonlyEvidence |
| MC-REUSE-001 | Applicable | PASS | CaseC/SDK/OAS/role/task/nativefactory reused；actualresults recordedonlyEvidence |
| MC-DEP-001 | Applicable | PASS | POM/JDKC unchangeddependencies；actualresults recordedonlyEvidence |
| MC-NAME-001 | Applicable | PASS | exactService/Context/Snapshot/NativeAccess/Behavior names；actualresults recordedonlyEvidence |
| MC-VALID-001 | Applicable | PASS | strictJSON/currentresource/freshroles/ownerrealm/deadline/runtimefences；actualresults recordedonlyEvidence |
| MC-MODEL-001 | Applicable | PASS | immutableC/CaseContext/Snapshot copies；actualresults recordedonlyEvidence |
| MC-CONVERT-001 | Not applicable | N/A | noPO/ORMconversion；actualresults recordedonlyEvidence |
| MC-LOG-001 | Applicable | PASS | safeexistingloggerstyle/no credential/body；actualresults recordedonlyEvidence |
| MC-BEAN-001 | Applicable | PASS | realProcessSecurity/Storage namedfactory+lazytypedprovider；actualresults recordedonlyEvidence |
| MC-UTIL-001 | Applicable | PASS | JDK/Jackson/nativeexistingonly；actualresults recordedonlyEvidence |
| MC-JSON-001 | Applicable | PASS | actualMockMvc/OAS/SDK9pairs/requiredtimefields；actualresults recordedonlyEvidence |
| MC-TIME-001 | Applicable | PASS | UTCZ/precision/runtimeREV/historyhandoff/deadline；actualresults recordedonlyEvidence |
| MC-CONFIG-001 | Applicable | PASS | falseflags/humanTaskimplication/exactscopepolicy；actualresults recordedonlyEvidence |
| MC-PATTERN-001 | Applicable | PASS | Adapter/Factory/Command noCaseSM；actualresults recordedonlyEvidence |
| MC-SCOPE-001 | Applicable | PASS | allmodelconsumersatomic, zeroSQL edits；actualresults recordedonlyEvidence |
| MC-TEST-001 | Applicable | PASS | meaningfulRED/GREEN + finiteconfig/legacy/PaC；actualresults recordedonlyEvidence |
| MC-BLOCKER-001 | Applicable | PASS | sourcebackendgatesclosed; originalnativeS09 remainsRuntimeUnverified；actualresults recordedonlyEvidence |

##### Step-specific Literal Rule gate

| Literal rule | Applicability | Status | Concrete obligation / proof |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | semantictypes/roles asfileinventory; noambiguouscarrier |
| Rule 2 | Applicable | PASS | Cconstructor+strictadvice+freshcontext+nativeguards usingexistingtechnicalstyle; noaddedvalidationdep |
| Rule 3 | Applicable | PASS | immutablevalueContext/Snapshot/copies; noPO/Lombok/mapperconversion |
| Rule 4 | Applicable | PASS | preserve explicitnamed@Bean factory/manualtechnicalctors/loggerstyle perRoot; no broadannotationrefactor |
| Rule 5 | Applicable | PASS | only existingJDK/Jackson/native/JDBC deps |
| Rule 6 | Applicable | PASS | Jackson3HTTP/nameenums/UTCZ/OASdrift; no newJSONlib |
| Rule 7 | Applicable | PASS | PSbasekeyparity/defaultoff/humanTaskreadiness and Casepermissionscope matrix |
| Rule 9 | Applicable | PASS | nativeAdapter/Factory/Command; no extrastate or registry |
| Rule 10 | Applicable | PASS | Instant/Duration/nativeDate.toInstant boundary and runtime/historyversiontests |
| Rule 11 | Applicable | PASS | ALWAYS applicable: currentverticaltechnicalJDBC/Flyway/noMP/newlayer; alloldconstructorusers compile |

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: §8 S2 focusedRED/GREEN selector，then S2 permission-producer source-reactor verify；RootaloneMaven/offline/noPG/listener。
- Expected result: assertionREDforactualmissingdirect/admission/taskbehavior，then allnamedfocusedclassesexit0；PaCartifactchecksactualifexecuted，no livecatalogregistration；oldconstructors/hash/Casewire preserved。
- Failure returns to: exactfileowner/knownnativehook/constructorconsumer；anyprotocoldesigngap returnsRootSpec，neverpatchCaseC/OASoraddmigration。
- Completion criteria: enums+allconsumers+realfactory+API/taskconfig wholeatomic；everynewbehaviorrealGREEN；V8/oldSQLunchanged；noCaseSourceadmissionyet；runtimePG/S09 stillRequired/Unverified。
- Rollback: revertStep2sourcecommit beforeactivation；afterCMMNdata preservecompatiblebinary/tasks andstopnewstarts；noDDLreverse/oldbinaryrollback。
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/OrchestrationContractTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessModelTypeEnum.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedCaseModel.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/VerifiedProcessModels.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshotTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseNativeAccess.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/CaseSnapshot.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehaviorTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseHumanTaskActivityBehavior.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/PinnedCaseActivityBehaviorFactory.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshotTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResultTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStoreTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskNativeAccess.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskSnapshot.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskActionResult.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/task/FlowableHumanTaskService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccessTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiCaseAccess.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/CaseControllerTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseRequestBodyAdvice.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/CaseController.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControllerTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-04-direct-case-server.md`
- Commit: `feat(process): implement governed direct CMMN Case operations`。
- Checkpoint: Rootcommit now，verifyintroducingSHA/exactpaths/cleanstatus；onlythenStep3code。

### Step 3 — 交付受限Linked BPMN来源、同SERVICE完成恢复与真正S09验收源码

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-201, REQ-202, REQ-203, REQ-204, REQ-205, REQ-206, REQ-207, REQ-208, REQ-209, REQ-301, REQ-302, REQ-303, REQ-304, REQ-305, REQ-306, REQ-307, REQ-308, REQ-309, REQ-310, REQ-311, REQ-312, REQ-401, REQ-402, REQ-403, REQ-404, REQ-405, REQ-406, REQ-407, REQ-408, REQ-409, REQ-410, REQ-411, REQ-412, REQ-413, REQ-414, REQ-415, REQ-416
- Dependencies: Step2singlecommit+cleancheckpoint；realdirect/release/CMMNfactory/taskbackendcomplete；V8hash不变。
- Baseline state: no admittedBPMNCaseSource/no registry/apply/poller；alltechnicalstoragealreadyfinal；noadditionalmigrationallowed。
- Observable outcome: registeredSERVICE经actualnativeoccurrence固定same-releaseCase，precreatedInbox恢复exactparentMessage/Timer生命周期，nativeS09fixture/source完整。
- End state: threeStepbackendsourcecomplete/defaultoff；原S09实际native Case→BPMN验收仍Required，当前RuntimeUnverified，整体目标不能因此标完成。
- Test-first gate: Required — fixturepresentfirst，oldvalidatorunsupportedliteralSource positiveassertionRED；newbehaviorcompileonlyprereq后真实FakeassertionRED→fullGREEN，nativePGsourceonlycompile。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11
- Ownership: Step3 linked/source/completion owner负责exactpaths；sharedrelease/config文件只本Stepowner写，delegates只非重叠tests/read-onlyreview；不是独占workspace。RootaloneMaven/commit。
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case-linked.bpmn20.xml`
- Purpose: 先提供真实固定linked模型fixture，避免缺fixtureRED。
- Symbols: governed ACTION prior-wait -> literalCaseSource -> Message/Timer gateway -> namedends。
- Repository evidence: existing governed-human-task/agent-wait fixtures + frozenSpec §14。
- Dependencies and consumers: ProcessModelValidatorTest/nativeCaseFlowableScenarios。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: governed ACTION prior-wait -> literalCaseSource -> Message/Timer gateway -> namedends；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: caseDefinitionKey literal; source elementID distinct from actualnativeoccurrence and laterwaitexecution。
- Error and edge behavior: acyclic/no loop/multiinstance/async/callback/EL; same-release governedCase; synthetictestroles only。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```xml
author validfixture with required ACTION wait before CaseSource, exact namedmessage andtimer branches;
no new caseSourceRef/workerRef identity claims;
maintain CMMN governed-case fixture unchanged;
CaseSource branch deriveswaitmetadata fromvalidatedgraph.;
```
- Verification contribution: ProcessModelValidatorTest; native source compilation。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`
- Purpose: literalCaseSource whitelist/graphbarrier真实RED。
- Symbols: ProcessModelValidatorTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: ProcessModelValidatorTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
first positive source fixture fails oldunsupportedCaseSource assertion;
add all-path prior-wait dominance (Human/Message/Timer/knownexternalWorker barriers), inline path bypass/cycle/EL/latest/callback/IO/unknownattrs reject;
accept AgentWorker->resultMessage->exclusiveFailure->CaseSource;
distinct source nodes metadata.;
```
- Verification contribution: ProcessModelValidatorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`
- Purpose: same-release/source-readiness完整发布RED。
- Symbols: ProcessReleaseServiceTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: ProcessReleaseServiceTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
require exactCasekey in sameimmutablebundle/release and matchingregistry/modelhash/sourceElement;
reject missing/cross-release/ambiguousbinding or absentlivecompletion;
noCaseSourceadmissionwhenflags/registryunready;
oldconstructors/goldendigest unchanged.;
```
- Verification contribution: ProcessReleaseServiceTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`
- Purpose: 增加不可变CaseSource metadata而不重用worker语义。
- Symbols: caseSources map keyedsourceElement; nested immutable CaseSource policy; oldctors。
- Repository evidence: existing maps/messagewait records; Step2 Type already3way。
- Dependencies and consumers: validator/ReleaseService/nativeSource/waitinspector。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: caseSources map keyedsourceElement; nested immutable CaseSource policy; oldctors；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: literalcasekey + validated waitactivity/message/timer/duration; exactmodelbytes outsidepolicy。
- Error and edge behavior: copy maps/sets; no registry/actor/release frommodel; preserve oldconstructors defaultempty。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
add CaseSource metadata and getter;
copy inputs;
keep oldconstructors delegating emptycaseSources;
do not overload MessageWait.workerElementId or loosen Agent grammar.;
```
- Verification contribution: ProcessModelValidatorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`
- Purpose: 只放行固定nativeCaseSource+graphderivedwait。
- Symbols: validateBpmn CaseServiceTask branch; prior-wait dataflow; CaseSource metadata。
- Repository evidence: currentserviceTask assumes extensionElements and onlydmn/externalworker。
- Dependencies and consumers: ReleaseService/VerifiedProcessModels/runtimeAdapters。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: validateBpmn CaseServiceTask branch; prior-wait dataflow; CaseSource metadata；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: flowable:type=case and literalcaseDefinitionKey; sourceXMLID -> exactderivedmessage/timerwait; nativeaiID neverfromXML。
- Error and edge behavior: explicittypeswitch; noextension/rawIO/callback/latest/EL; preserve DMN/worker/human parsers and Agentwaitrequirements。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
branch type before requiring DMN/worker extension;
validate closedCaseattrs;
DAGtopological all-path seen-prior-wait dataflow, require eachSource true;
derive solefollowingeventBasedwait/message/timer and matchclosedgraph;
do not require immediatepreviousHumanTask;
no new grammar elsewhere.;
```
- Verification contribution: ProcessModelValidatorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistryTest.java`
- Purpose: 固定SERVICE/精确source selection与currentkey失效RED。
- Symbols: CaseSourceRegistryTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: CaseSourceRegistryTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
binding key includes tenant/release/BPMNkey+SHA/sourceElement/Casekey+SHA;
rejectduplicates/ambiguity;
sourceId onlyaudit;
localregisteredclient/currentACTIVEtenantissuer match;
noPDP/OAuthHTTP insideTX;
emptyregistry validdirectmode;
rename cannot change nativeoccurrenceidentity;
test strict unknown fields/defaults/bounds, active key versions and removal.;
```
- Verification contribution: CaseSourceRegistryTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 7 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceProperties.java`
- Purpose: 现cases group真实strict immutable配置。
- Symbols: flags; sources list<=256; boundedpoll/retry/batch/failures; nested Source values。
- Repository evidence: existing Properties record/strictBinder conventions and primary§15。
- Dependencies and consumers: CaseSourceRegistry/config factories/poller; existingtwoCaseflags preserved。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: flags; sources list<=256; boundedpoll/retry/batch/failures; nested Source values；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: sourceId,tenant,releaseKey,parentkey/SHA,sourceElement,Casekey/SHA,issuerURI,registrationId,serviceSubject/clientId,app/audience/resourceURI。
- Error and edge behavior: defaults empty/off; positive IDs/validhex/literalkeys/ASCII normalizedissuer; no realproductionbindings。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
bind exactexistingcasesprefix;
validate defaults poll1s/retry1s/batch32/max10 and frozenbounds;
defensivecopy;
unknownfields reject;
do not introduce additional flags/permissions or embed secrets.;
```
- Verification contribution: CaseSourceRegistryTest; ProcessApiConfigurationTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistry.java`
- Purpose: 只从现registered localSERVICE选固定executor。
- Symbols: constructor typedlocalmetadata/TrustedIssuerRegistry; require(bindingKey); current(registration); activeDueKeys。
- Repository evidence: ProcessAgentBridgeConfiguration validateBindings/authKey is exactlocalpattern。
- Dependencies and consumers: nativeSource/CaseService linkedstart/completion/poller。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: constructor typedlocalmetadata/TrustedIssuerRegistry; require(bindingKey); current(registration); activeDueKeys；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: canonicalRegistration contains verifiedSERVICEactor and exactsourcebinding; issuerfingerprint derived notclaimed。
- Error and edge behavior: currentACTIVEtenantissuer/clientmetadata rechecked; explicitabsent/revoked mismatch nofallback/newSERVICE; noUSERSecurityContext restoration。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
build finiteimmutable indexes by exactbinding and tenant+sourceId;
validate IdpStarter registeredSERVICE and oauth.authorizationKey metadata withoutfetchingtoken;
requirecanonicalcurrentregistration;
emptySources avoidscredentialproviderlookup;
currentchecks local only before/afterapply.;
```
- Verification contribution: CaseSourceRegistryTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 9 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehaviorTest.java`
- Purpose: nativeCaseTask override/actualoccurrence/sourceownerRED。
- Symbols: PinnedCaseSourceActivityBehaviorTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: PinnedCaseSourceActivityBehaviorTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
actualnativeExecutionEntity+current ActivityInstanceEntity frommanager;
requireexactsource/endTime null;
parentSTARTAPPLIED;
same-release/business/correlation;
no default execute/callback/latest, nohistoricUSERcontext;
sourceIdrename dedup;
leave only validatedwait.;
```
- Verification contribution: PinnedCaseSourceActivityBehaviorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehavior.java`
- Purpose: pin应用exactid启动并安全leave。
- Symbols: extends CaseTaskActivityBehavior(CaseServiceTask); execute(DelegateExecution); typedlazyproviders。
- Repository evidence: native8 factory returnsconcreteCaseTaskActivityBehavior; publicinheritedleave。
- Dependencies and consumers: PinnedDecisionActivityBehaviorFactory/FlowableCaseService internalentry。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: extends CaseTaskActivityBehavior(CaseServiceTask); execute(DelegateExecution); typedlazyproviders；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: currentExecutionEntity -> actual unfinished ActivityInstanceEntity.id; exactnativeparent/def/tenant/sourceElement/releasedSHA。
- Error and edge behavior: do not guess waitexecution orcall super.execute/defaultcallback; requireAPPLIEDparentSTART and canonicalregisteredSERVICE。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
resolve typedservice/registry lazily;
CommandContextUtil.getActivityInstanceEntityManager(commandCtx).findUnfinishedActivityInstance(executionEntity), validate allIDs/endTime;
loadexactrelease/sourcepolicy;
invokeinternalregisteredlinkedstart sameTM;
leave(execution) towardvalidatedMessage/Timer;
nativecontextmissing failsclosed.;
```
- Verification contribution: PinnedCaseSourceActivityBehaviorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 11 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/decision/PinnedDecisionActivityBehaviorFactory.java`
- Purpose: 给真实BPMNfactory增加exactCase hook。
- Symbols: preserve noarg; typedlazyprovider overload; createCaseTaskBehavior(CaseServiceTask)。
- Repository evidence: existingprocessfactory used by StorageConfig andoldDMN/HumanTask tests。
- Dependencies and consumers: newpinnedCaseSourcebehavior; oldDMN/User behavior unchanged。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: preserve noarg; typedlazyprovider overload; createCaseTaskBehavior(CaseServiceTask)；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: newCaseSource chooses customconcretebehavior, not nativekey/latestcallback。
- Error and edge behavior: no eagerengine/servicecycle; feature/readiness missing formalSourcefailclosed; no globalApplicationContext/ELbeanlookup。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
override exactfactorysignature returning newPinnedCaseSourceActivityBehavior with typedSuppliers;
keep noarg constructor and oldtwoDMN/User hooks bytebehavior;
no defaultcallbacksuccess path forformalCase.;
```
- Verification contribution: PinnedCaseSourceActivityBehaviorTest; PinnedHumanTaskActivityBehaviorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 12 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseService.java`
- Purpose: 增加真正linked内部native事务入口，无public协议扩张。
- Symbols: startLinked(canonicalRegistration,exactNativeSourceFacts); explicitnewfullctor preservesStep2directctor。
- Repository evidence: Step2realdirectservice + Step1stores/Inbox。
- Dependencies and consumers: pinnednativeSource/canonicalRegistry; noextraHTTProute。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: startLinked(canonicalRegistration,exactNativeSourceFacts); explicitnewfullctor preservesStep2directctor；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: executorSERVICE,ownerparentAPPLIEDSTART; sameCase/parentrelease; inheritedbusiness/correlation; stablecommandhashnativeAI independent sourceId。
- Error and edge behavior: requirecurrentowningnativeCommand/sharedTM; participate originalTX,noREQUIRES_NEW/publicown-noambientcall; no USERimpersonation; rollbackincludesCase/provenance/Inbox。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
understableoccurrencecommandlock recheck currentregistration/localissuer, parentAPPLIEDSTART andsameimmutablemodels;
Case start exactdefinitionId withoutnativecallback;
applyCASE_START, insertlinkedprovenance+prepareInbox atomically;
replayvalidatesimmutableoccurrence/executor/owner/bindings anddoesnotrestart;
publicowner-onlygetrules notmisused asinternalexecutor permission.;
```
- Verification contribution: FlowableCaseServiceTest; PinnedCaseSourceActivityBehaviorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 13 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseServiceTest.java`
- Purpose: linked入口真实transaction/duplicate失败矩阵。
- Symbols: FlowableCaseServiceTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: FlowableCaseServiceTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
assert sourcecalling publicdirectownTX isnotused;
parentPENDING rejects;
sameoccurrence/sourceRename no secondnativeCase;
inheritedexactbusiness/correlation/release/owner;
executorSERVICEreceipt;
currentregistrationrevocation/failureafterstart rollsbackallfacts.;
```
- Verification contribution: FlowableCaseServiceTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 14 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`
- Purpose: 把CaseSourceadmission与actualregistered/livecompletion闭合。
- Symbols: explicitfullctor overload with typedregistry/maintenance predicate; prepareCaseSourceBindings。
- Repository evidence: Step2CMMN3way safe releasechain complete。
- Dependencies and consumers: ProcessSecurityConfiguration; source/nativewaitinspectors。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: explicitfullctor overload with typedregistry/maintenance predicate; prepareCaseSourceBindings；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: matchregistry exacttenant/release/BPMNkey+hash/sourceElement/Casekey+hash forsamebundle。
- Error and edge behavior: oldconstructors stillrejectunconfigurednewsource, notconstantfalseplaceholder; linkedpublication requires livepoller+newstartallowed asfrozenflags。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
afterprepare allmodels, validateeveryCaseSource same-bundleCMMN andregistry exactbinding;
requireactualCase/readiness/humanpublication/currentmaintenancerunning;
preserveexistingAgentmaintenance andoldmanifestfieldbytes/order;
model cannot chooseactor/sourceId/releaseversion.;
```
- Verification contribution: ProcessReleaseServiceTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 15 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspectorTest.java`
- Purpose: source已正常结束与目标wait独立proof RED。
- Symbols: FlowableCaseWaitInspectorTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: FlowableCaseWaitInspectorTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
source actualAIID->sameID historicactivity endTime!=null/deleteReasonnull;
exactreleasedsourceElement/hash/parentrefs;
derivecurrentmessage/timer frommetadata;
subscriptionExecution freshlyquery independent sourceAI;
timer/message/process-ended classifications evenchildACTIVE.;
```
- Verification contribution: FlowableCaseWaitInspectorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 16 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspector.java`
- Purpose: 读取精确父BPMNsource/wait生命周期。
- Symbols: constructor(process,releases,models,validator); inspect(provenance)。
- Repository evidence: FlowableAgentWaitInspector exactqueries/history rule; separateclass keepsoldcallers。
- Dependencies and consumers: CaseCompletionCorrelationService; no Agentpayloadreuse。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: constructor(process,releases,models,validator); inspect(provenance)；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: sourcehistoricAIID proof separate from Message EventSubscription.executionId。
- Error and edge behavior: no guessed/cached waitexecution; require normallyendedsourcedhistory; losingbranchdeleteReason notsuccess; no currentnativeTask mutation。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
derive soleCaseSourcewait fromsameimmutableBPMNmetadata andsourceElement;
verify sourcehistory exactAI/PID/def/tenant/normalend;
classify PROCESS_ENDED/TIMER_WON/MESSAGE_CONSUMED beforechildstate;
queryexactcurrentMessage subscription listPage0,2 thenexactExecution query, return WAITING/ADVANCING/SUSPENDED + currentexecution only.;
```
- Verification contribution: FlowableCaseWaitInspectorTest; FlowableAgentWaitInspectorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 17 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationServiceTest.java`
- Purpose: 每个completion/recovery分支真实RED。
- Symbols: CaseCompletionCorrelationServiceTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: CaseCompletionCorrelationServiceTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
parentlate first evenchildACTIVE;
ACTIVE nofailureburn;
eligibleCOMPLETED requiresprimarycomplete/suppcompleteorterminate/milestonepositive+PIoccurred/end+caseend;
forcecomplete missingtraces manual;
TERMINATEDnoMessage;
exactSERVICE/currentissuer;
scope->Inbox->command->freshBPMN;
rollback/duplicate/orphanremovedsource.;
```
- Verification contribution: CaseCompletionCorrelationServiceTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 18 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationService.java`
- Purpose: 同一localcommit中精确Message与APPLIED。
- Symbols: apply(registration,completionId); applyOrphan; reconcile; ownedshortTX。
- Repository evidence: AgentResultCorrelationService style + frozenCase-specificlock/stateorder。
- Dependencies and consumers: Inbox/Registry/WaitInspector/CaseNativeAccess/ProcessCommandReceiptStore。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: apply(registration,completionId); applyOrphan; reconcile; ownedshortTX；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: parentlifecycle first; nativeCasehistory/traces -> terminal_fact SHA; CASE_COMPLETION_SERVICErequestHash -> exactMessageexecution。
- Error and edge behavior: ACTIVEresched nofailures; TERMINATEDmanual; missing/ambiguousrequiredfacts DEAD; normalcompletion≠AUTOcauseclaim; noOAuth/PDPinsideTX。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
ownTX CaseScope->Inbox->localregistration/provenance/releasedsource/waitinspection;
confirmedparentlate IGNORE immediately;
CaseACTIVEpending;
eligibleCOMPLETED requireexactprimaryPI completedTime/suppend/milestonehistory elementId=milestone +PIoccurredTime and caseend;
thencommandreceipt->freshnativewaitfence/messageEventReceived(exactmessage,actualexecution)->APPLIED+Inbox samecommit;
failure rollbackthen scopesameprefixreschedule;
orphan absent/revoked onlyDEADmanual, neverMessage orborrowSERVICE.;
```
- Verification contribution: CaseCompletionCorrelationServiceTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 19 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPollerTest.java`
- Purpose: bounded discovery/emptyregistry orphan recovery/lifecycle RED。
- Symbols: CaseCompletionPollerTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: CaseCompletionPollerTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
normalduescan+globalorphananti-join progress;
discoverycommit beforeapplyCaseScope;
sourcesempty stillorphanmaintenance;
no implicitgrant/nativecall fororphans;
terminal/noexpiry/failurebudget;
boundedexecutor shutdown onlyownedresources.;
```
- Verification contribution: CaseCompletionPollerTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 20 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPoller.java`
- Purpose: 只运行bounded本地maintenance，不增加remoteclient。
- Symbols: SmartLifecycle/close or existingpollerlifecycle pattern; runningreadiness。
- Repository evidence: ProcessAgentBridgePoller boundedschedulers/executors/currentpattern。
- Dependencies and consumers: CaseSourceRegistry/currentkeys+Inbox+Correlation; release/start readiness。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: SmartLifecycle/close or existingpollerlifecycle pattern; runningreadiness；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: batch<=32 defaults/finiteinterval; peritemownTX; sourcesemptyorphan path。
- Error and edge behavior: no unboundedthread/queue; no perACTIVEinfo noise; stopnewstarts doesnotstoppoller; shutdown closesowned scheduler only。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
scan currentlocalvalidregistration tuples normally plus globalorphan due anti-join (bounded<=256binds/<=32rows);
release discoverylocks beforeapply;
canonicalcurrentregistration check peritem;
track safe counts;
start/readiness onlyafterverifiedschema/services;
interruption/reschedule bounded.;
```
- Verification contribution: CaseCompletionPollerTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 21 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`
- Purpose: actual装配/配置与旧路径回归。
- Symbols: ProcessApiConfigurationTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: ProcessApiConfigurationTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
realtypedproperties/localregistry/service/Inbox/poller wiring;
disableddoesnotresolvecredentials;
directemptySource mode;
missingsource/livepoller rejectslinkedadmission;
existingoldfactories unaffected.;
```
- Verification contribution: ProcessApiConfigurationTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 22 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`
- Purpose: actual装配/配置与旧路径回归。
- Symbols: ProcessStorageConfigurationTest。
- Repository evidence: existing release/native/bridge unit test conventions; CREATE absent baseline。
- Dependencies and consumers: exact owner under test; no new test dependencies。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: ProcessStorageConfigurationTest；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: synthetic released/native/registration facts -> result, side effect/lock trace。
- Error and edge behavior: finiteunit; no listener/PG; type/assertion RED not fixture/compiler failure。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
typedlazyCaseSourceproviders preventengine/serviceeagercycle;
Caseoff legacyCMMNfactoryunchanged;
registeredservice/currentnativecfg isactualsingleton;
noSource or appstartup.;
```
- Verification contribution: ProcessStorageConfigurationTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 23 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`
- Purpose: 给既有BPMNfactory接typedlazyproviders。
- Symbols: processStorageGuard typedObjectProvider overload path。
- Repository evidence: factory currentlynewnoarg, afterStep2CMMNinstallerexists。
- Dependencies and consumers: PinnedDecisionActivityBehaviorFactory/CaseService/Registry。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: processStorageGuard typedObjectProvider overload path；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: resolve app collaborators onlywhenformalnativeexecute, neverduringengineinit。
- Error and edge behavior: preserve noargcallers/oldDMN/Userfactory and CMMNcfg/parser guards; no globalcontext。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
pass Supplier from ObjectProvider into newfactory onlyforCase-capableassembly;
no getObject duringconfig;
formalSourcefeature/readiness ischecked byrealbehavior, notdefaultkeycallback.;
```
- Verification contribution: ProcessStorageConfigurationTest; PinnedCaseSourceActivityBehaviorTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 24 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`
- Purpose: 完整linked注册/Inboxapply/poller工厂。
- Symbols: existingCaseConfiguration namedBeans/typedproperties; fullrelease/Case overloads。
- Repository evidence: Step2realCaseconfig + realroot/nestedfactories。
- Dependencies and consumers: Registry/Properties/TrustedIssuer/Idpservicekeymeta/Inbox/WaitInspector/Correlation/Poller。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: existingCaseConfiguration namedBeans/typedproperties; fullrelease/Case overloads；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: samefixedSERVICEstart+completion; sharedactualDS/TM/schema; CaseenabledmaintenanceevenemptySources。
- Error and edge behavior: newstartoff retainsget/tasks/poller; no extraflags/rights/livebindings; missinglocalsourceproof failsclosed。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
enable typedCaseSourceProperties;
buildfiniteRegistry (skipcredentialproviderlookup whenempty);
wireexistingInbox/newWaitInspector/Correlation/Poller;
addcanonicalcurrentregistration/serviceproof checks;
release/nativeCasefullconstructors gettypedcollabs;
preserveoldoverloads andnofeatureoffactivation.;
```
- Verification contribution: ProcessApiConfigurationTest; CaseCompletionPollerTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 25 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`
- Purpose: 追加冻结的empty/boundedsource配置，不激活功能。
- Symbols: cases.sources=[]/poll-interval/retry-delay/batch-size/max-failures。
- Repository evidence: twofalseflags fromStep2, existingbaseonly。
- Dependencies and consumers: CaseSourceProperties/registry/poller。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: cases.sources=[]/poll-interval/retry-delay/batch-size/max-failures；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: emptySources,1s,1s,32,10; sameCase/newstartflagsremainfalse。
- Error and edge behavior: no realtenant/role/client/issuer/configgrant; no extraenableflags。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```yaml
appendonlyapprovedempty/boundedkeys undercases;
strictBinder/keyparitytests;
do notchangerealregistration/security/OAuthcredentials oractivatecatalog.;
```
- Verification contribution: CaseSourceRegistryTest; ProcessApiConfigurationTest。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 26 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseFlowableScenarios.java`
- Purpose: 真实nativeS09 helper（仅编译，当前不运行）。
- Symbols: bounded nativeCase/linked scenario methods。
- Repository evidence: currentHumanTaskFlowableScenarios isolatesrealresponsibility。
- Dependencies and consumers: FormalCasePostgresTest; actual3enginecfg/services/productionadapters。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: bounded nativeCase/linked scenario methods；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: actualCaseprimary/supp/milestone/history+parentMessage；notstaticBPMNsubstitute。
- Error and edge behavior: no fake nativehistory/REV/Messageacceptance; syntheticidentitydoesnotproveIAM; boundedtimeouts。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
implement realpinnedrelease/start/disable-enable ABA/planSTART/taskclaimcomplete/sentry/suppcompleteorterminate/milestone/caseend;
require actualhistory and fixedcandidates;
linked actualAI sourceid/historysameid/exactwaitMessage;
wholeTERMINATED/forcecomplete-missingtrace/duplicate/rollback/roleexpiry/tenant/claim races;
parentTimerwhilechildACTIVE late;
twoCaseScopeworkers finite.;
```
- Verification contribution: testCompile only; runtime Required/Unverified。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 27 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/FormalCasePostgresTest.java`
- Purpose: 保留原S09真正native验收入口，不缩为Mock。
- Symbols: @EnabledIfSystemProperty tianji.process.postgres.test.url; finite @Timeout。
- Repository evidence: currentProcessServerPostgresTest explicitloopbackfreshDB/runtimeUser guards。
- Dependencies and consumers: CaseFlowableScenarios/realproductionconfiguration/Flywaytest dependencies alreadypresent。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: @EnabledIfSystemProperty tianji.process.postgres.test.url; finite @Timeout；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: freshloopbacktianji_it_* DB + externallyspecifiedruntimeuser -> actualS09 assertions。
- Error and edge behavior: currenttaskmustnotrun; exclusivefreshDB, notconcurrentoldPGclasses; noautomaticcontainers/app/listener launch。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
reuseexactexisting optinproperty/URL/runtimeuser/freshschema safety style;
applyexistingnative/provisionedmigrations + oneV8 onlyinside explicitfutureoptinrun;
buildreal3engineowningcontext and helpercalls;
noFakeDB/native mocks foractualacceptance;
testscompileinRootfinitereactor but are neverselected now.;
```
- Verification contribution: testCompile only; originalREQ416 RuntimeUnverified untilactuallyrun。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 28 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`
- Purpose: 让既有optin provisioning认识oneV8及新版verifier。
- Symbols: migrationversion/expectedchecks/scenario references only。
- Repository evidence: existingactualPG/nativebaseline class remainsoptin。
- Dependencies and consumers: futureusercontrolledexistingnativeintegration。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: migrationversion/expectedchecks/scenario references only；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: V1–V7hashes unchanged; V8 additive; oldBPMN/DMN scenarios preserved。
- Error and edge behavior: no broadtestbootstraprefactor; no runningPGclass now。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
includeonefinalV8 whereexistingprovisioner explicitlyenumeratesresources;
alignnewbinaryV7/V8catalog assertions;
reuseexistinglegacy cases andboundedhelpers;
do not copyfullbootstraporstartanotherappasvalidation.;
```
- Verification contribution: testCompile only; noPGselector。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 29 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/HumanTaskFlowableScenarios.java`
- Purpose: 保留原PROCESS native回归的真实构造兼容。
- Symbols: existinglegacyconstructor/scenario calls。
- Repository evidence: oldhelper uses 4arg task service and 3..8argreleasectors。
- Dependencies and consumers: ProcessServerPostgresTest/CaseFlowableScenarios boundaries。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: existinglegacyconstructor/scenario calls；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: oldPROCESSorigin/hash/assignment/formapproval behavior unchanged。
- Error and edge behavior: modifyonlynecessarycompile/sharedfixture hooks; no Casepolicy duplicated。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```java
retainoldconstructors asregression inputs;
addexplicitCase-awarefixture linkonlyifcalledbyformalhelper;
no mechanicalsplit or broadcleanup;
actualnativeexecution remainsoptin.;
```
- Verification contribution: testCompile only; legacyconstructoraudit。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

#### File 30 — `CREATE docs/egon/evidence/2026-11-04-linked-case-completion.md`
- Purpose: 记录三Step源码闭合与原S09未验收边界。
- Symbols: actualRED/GREEN/static/permissionartifact/optinsource/commit lookup。
- Repository evidence: Root actual logs/diff/hashes only。
- Dependencies and consumers: originalgoal/user/finalconformance audit。
- Why now: fixedfixture/metadata→tests→typedregistry/nativebehavior→app/inspector/correlation/poller→actualwiring→nativeacceptancesource。
- Contract/signature changes: actualRED/GREEN/static/permissionartifact/optinsource/commit lookup；preserveoldoverloads/Casewire，无新publicroute。
- Input/output and state mapping: linkedsource/provenance/owner/terminalfacts/late/orphan/exactMessage evidence；nativeRuntimeUnverified ifnotrun。
- Error and edge behavior: cannotclaimT3-08donefromMock/compile; no selfhashprefill/push/deployclaim。
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001；singlefrozenV8/no extraframework。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11；samecurrenttechnicalstyle/strictcontexts/immutablefacts/localnativeAdapter/java.time/qualifiedfactoryownership/noMPconversion。
- Implementation pseudocode:
```text
record currentSpec/Plan/V8/old23/OAShashes, finalfinitecounts and livegaps;
allREQ401..416trace, API9/MC17/Rule10 individually;
introducingcommit via gitlog;
sourcecomplete vs nativeS09Required/Unverified explicit.;
```
- Verification contribution: Step3 evidence/finalaudit。
- After this file: 该职责与frozenSpec一致；finalfiniteGREEN/static/zeroDDL审查后才commit，nativePG验收仍独立Required。

##### Step-specific Manual Check gate

| Check ID | Applicability | Status | Evidence / required execution proof |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | existingbridge/config/release/executionpaths；actualEvidenceonlyafterexecution |
| MC-REUSE-001 | Applicable | PASS | Agentwait/poller/localkeymetadata + exactnativefactoryhooks；actualEvidenceonlyafterexecution |
| MC-DEP-001 | Applicable | PASS | no newMQ/client/dependency；actualEvidenceonlyafterexecution |
| MC-NAME-001 | Applicable | PASS | SourceRegistry/Behavior/WaitInspector/Correlation/Pollersemantic；actualEvidenceonlyafterexecution |
| MC-VALID-001 | Applicable | PASS | exactbinding/nativeAI/normalhistory/currentSERVICE/positivecompletionchecks；actualEvidenceonlyafterexecution |
| MC-MODEL-001 | Applicable | PASS | immutableSource/Registration/TerminalFacts copies；actualEvidenceonlyafterexecution |
| MC-CONVERT-001 | Not applicable | N/A | noPO/ORMmapperboundary；actualEvidenceonlyafterexecution |
| MC-LOG-001 | Applicable | PASS | safeboundedcounts/typename;no highfreqACTIVEpayload；actualEvidenceonlyafterexecution |
| MC-BEAN-001 | Applicable | PASS | typedlazyproviders/currentlocalregistration/ownedshutdown；actualEvidenceonlyafterexecution |
| MC-UTIL-001 | Applicable | PASS | JDK/native/JDBCexisting；actualEvidenceonlyafterexecution |
| MC-JSON-001 | Applicable | PASS | immutableCase/OASunchanged/noadditionaleventpayload；actualEvidenceonlyafterexecution |
| MC-TIME-001 | Applicable | PASS | DBdue/terminalprecision/ACTIVEfailurebudget/noCaseexpiry；actualEvidenceonlyafterexecution |
| MC-CONFIG-001 | Applicable | PASS | same2flags+emptyfixedsources/boundedmaintenancevalues；actualEvidenceonlyafterexecution |
| MC-PATTERN-001 | Applicable | PASS | nativeAdapter/Factory/localCommandInbox noCaseSM；actualEvidenceonlyafterexecution |
| MC-SCOPE-001 | Applicable | PASS | noV9/noSQLedits/no inventedproductionIDs；actualEvidenceonlyafterexecution |
| MC-TEST-001 | Applicable | PASS | eachcomponentmeaningfulRED/GREEN + nativeoptincompiledunexecuted；actualEvidenceonlyafterexecution |
| MC-BLOCKER-001 | Applicable | PASS | sourceproofclosescurrentStep; actualS09stillRequired/RuntimeUnverified；actualEvidenceonlyafterexecution |

##### Step-specific Literal Rule gate

| Literal rule | Applicability | Status | Concrete obligation / proof |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | exactsemanticbridge/registry/service/behavior names; no newambiguouscarrier |
| Rule 2 | Applicable | PASS | strictBinder/currentcanonicalregistration/nativecontext/immutableprovenance/history/freshrole checks |
| Rule 3 | Applicable | PASS | immutableSource/Registration/TerminalFacts/copiedmaps; noPO/mapper |
| Rule 4 | Applicable | PASS | realnamed@Bean factory/typedlazyproviders/safeexistingloggerstyle; noApplicationContext/globallookup |
| Rule 5 | Applicable | PASS | only existingJDK/Jackson/JDBC/native dependencies |
| Rule 6 | Applicable | PASS | CaseJSON/OAS byteunchanged, no remoteeventJSONsurface ornewserializer |
| Rule 7 | Applicable | PASS | sameCaseflagsfalse, sourcesempty, boundedvalues; no realtenant/roles/clients/activation |
| Rule 9 | Applicable | PASS | nativeAdapter/Factory+localCommand/Inbox, no parallelCaseStateMachine |
| Rule 10 | Applicable | PASS | java.time/DBclock_due/historyactualprecision; nofreshnessguess/defaultzone |
| Rule 11 | Applicable | PASS | ALWAYS applicable: existingverticaltechnicalprofile/sameJDBC/Flyway;noDDLafterStep1/no newlayer |

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。
- Verification command: §8 S3focusedRED/GREEN + finalfinitepermission/sourceverify，Rootalone；doNOTselectFormalCasePostgresTest/ProcessServerPostgresTest/nativehelpers。
- Expected result: realassertionRED forsource/recoveryrules then finiteGREEN; allnativePGsourcescompile, actualnativeS09notrun/RuntimeUnverified；no additionalDDL。
- Failure returns to: exactsource/registry/wait/history/inbox/wiring/testowner；unprovennativeassumption returnsRootSpec, never guess executionIDs/restoreUSER/defaultcallback。
- Completion criteria: fullremainingbackendsource/fixtures compiled, finalfinitegates/PaCartifact/static/17MC10Rules individuallyrecorded；originalREQ416nativeacceptance remainsRequired ifunexecuted，cannot claimT3-08complete。
- Rollback: stopnewstarts only，retainCase/task/completioncompatiblebinary；orphanwithdrawnsource manual/DEAD withnoforeignSERVICE; schema/factsneverreverse/delete；no deployedactioninthisworkflow。
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/resources/fixtures/formal-case-linked.bpmn20.xml`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistryTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceProperties.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseSourceRegistry.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehaviorTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/PinnedCaseSourceActivityBehavior.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/decision/PinnedDecisionActivityBehaviorFactory.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/FlowableCaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspectorTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/FlowableCaseWaitInspector.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionCorrelationService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPollerTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/CaseCompletionPoller.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/CaseFlowableScenarios.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/FormalCasePostgresTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/HumanTaskFlowableScenarios.java`, `docs/egon/evidence/2026-11-04-linked-case-completion.md`
- Commit: `feat(process): correlate linked Case completion with BPMN`。
- Checkpoint: Rootsinglecommit immediatelyafterGREEN/static，verifyexactpaths/introducingSHA/cleanstatus；finalconformanceaudit distinguishes source delivery from originalnativeS09RuntimeUnverified。


## 8. Test, Validation, and Quality Gates

All commands run from `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; Root alone runs Maven. Settings are the isolated offline local-repository settings from §6.2. The same selector runs RED then GREEN; an individual store selector can replace S1's list for its own required behavior RED/GREEN. No PG class, HttpProcessClientTest (real listener), application, browser or container is selected.

### S1 finite command

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipTests=false \
  -Dtest=HumanTaskStorageVerifierTest,CaseContextTest,CaseStartProvenanceStoreTest,CasePlanItemReceiptStoreTest,CaseCompletionInboxTest,CaseStorageConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

### S2 finite command

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipTests=false \
  -Dtest=OrchestrationContractTest,ProcessApiConfigurationTest,HttpCaseClientTest,CaseResponseCodecTest,ProcessReleaseServiceTest,ProcessModelValidatorTest,CmmnModelValidatorTest,CmmnModelXmlTest,CaseNativeAccessTest,CaseSnapshotTest,FlowableCaseServiceTest,PinnedCaseHumanTaskActivityBehaviorTest,HumanTaskNativeAccessTest,HumanTaskSnapshotTest,HumanTaskActionResultTest,HumanTaskReceiptStoreTest,FlowableHumanTaskServiceTest,HumanTaskAccessRulesTest,TianjiCaseAccessTest,CaseControllerTest,CaseStorageConfigurationTest,ProcessStorageConfigurationTest,ProcessSecurityConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,ProcessTaskOriginTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

### S3 finite command

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipTests=false \
  -Dtest=ProcessModelValidatorTest,ProcessReleaseServiceTest,CaseSourceRegistryTest,PinnedCaseSourceActivityBehaviorTest,FlowableCaseServiceTest,FlowableCaseWaitInspectorTest,FlowableAgentWaitInspectorTest,CaseCompletionCorrelationServiceTest,CaseCompletionPollerTest,ProcessApiConfigurationTest,ProcessStorageConfigurationTest,PinnedHumanTaskActivityBehaviorTest,CaseContextTest,CaseStartProvenanceStoreTest,CasePlanItemReceiptStoreTest,CaseCompletionInboxTest,CaseStorageConfigurationTest,CaseControllerTest,HttpCaseClientTest,CaseResponseCodecTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

Permission producer artifacts: after S2/S3 focused GREEN, run the same S2/S3 command with `verify` lifecycle as supported by the existing source-reactor script, and verify actual generated PaC resources without any live registration. Inspect the script's real invocation before choosing its lifecycle option; record the exact executed command. No production manifest targets are edited.

| Gate | Expected evidence | Failure return | Scope boundary |
| --- | --- | --- | --- |
| RED | Expected missing-behavior assertion, exact command/log/exit | Owning test and compile-only prerequisites | Compile/fixture/cache errors are not RED |
| GREEN | Same focused command exit0; actual counts in Evidence | Owning source/test | Finite JVM/MockMvc only |
| strict documents | Both frozen Spec/Plan validators --strict PASS | Root design owner | No invented Accepted status |
| frozen paths | `/tmp/tianji-step37-baseline.json` all197 exclusions unchanged in Step1; later only approved release enum/client changes | Owning Step | All23 old SQL and finalV8 byte hashes remain immutable |
| scope | `git diff --check`, exact Step path set, stub search and final source review | Owning source | No POM/build output/environment/credentials |
| native S09 | Actual native lifecycle, histories and waiting BPMN advances | Future explicit user runtime test | Required, RuntimeUnverified now |

## 9. Migration, Compatibility, Rollout, and Rollback

Only Step1 creates the final ProcessDB V8 once. It widens the source-named release/V7 checks, adds three complete technical tables and required indexes, and changes no old SQL bytes. No GRANT/role/schema/owner/repair/backfill or DB operation is performed by this task. Catalog verification and migration execution remain future runtime actions.

New binary supports V7 Case-off and V8 Case-off/on; Case-on requires exact V8. Old V7 task verifier rejects widened V8 checks, so deployment must drain old task nodes before migration and retain a compatible binary afterward. Stop new starts independently; in-flight reads/tasks/completion maintenance continue. No destructive schema rollback, fact deletion or historical USER restoration. No runtime acceptance follows from source compilation.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Source | Step | Concrete proof | Evidence |
| --- | --- | --- | --- | --- |
| `REQ-401` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 1, Step 2, Step 3 | schema/checksum/privilege tests；Step2/3无需migration | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-402` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | 3waytests、same-release rejection、legacydigestgolden | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-403` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | directreplay/rollback/executorFK/resourcechecks | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-404` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | rename/duplicate/parentPENDING/inlineprior-waitbarrier/crossrelease/rollback tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-405` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | MockMvc+packagedOAS+SDKdrift | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-406` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | roleexpiry/tenant/issuer/terminalmatrix | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-407` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | primary/supplemental/milestone/claimed-other denial | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-408` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | enabled-disable-enable staleversion rejects；source/historical versionsdistinct | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-409` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | claim/complete/read/replay+linkedV7joins+PROCESSregression | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-410` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 1, Step 2, Step 3 | lock-orderunit+concurrencyPGsource、privilegeverifier | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-411` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | cfg/parseridentity、dependencyhooks、interceptor/migration rejection、nativehistorytests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-412` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | ACTIVE不耗budget；TERMINATEDmanual/DEAD；duplicate/late/rollback | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-413` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | defaultcallback从未调用；exactwait/subscription/occurrenceproof | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-414` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 1, Step 2, Step 3 | off/on/stopnewflagsmatrix、V7/V8newbinarycompat | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-415` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 2, Step 3 | noUSERcontext/noOAuthHTTPinsideTX/registrationrevocationfake | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-416` | [Spec](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) §4 | Step 1, Step 2, Step 3 | source与finiteunit/MockMvc只是交付门禁；原S09必须实际native Case完成并让BPMN继续；PG未获授权执行时标RuntimeUnverified，不能宣称T3-08完成 | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-201` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | reflection/method assertions exact；no extra type/field/method | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-202` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | invalid values reject；reflection proves forbidden fields absent | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-203` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | state-compatible subset examples and milestone rejection cover advertised actions | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-204` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | duplicates/oversize/time mismatch/actions in terminal reject；empty/sparse/historical states accepted | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-205` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | DISABLE ENABLED→DISABLED；ENABLE DISABLED→ENABLED；START ENABLED→ACTIVE；TERMINATE ACTIVE→TERMINATED；all other pairs reject | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-206` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | source list mutation has no effect；returned list unmodifiable | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-207` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | UTC Instant semantics documented；constructor permits finishedAt before submittedAt as existing DTO does | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-208` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | CaseContractTest covers components, copies, sparse history and invalid matrix | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-209` | [Spec](../spec/2026-11-01-23-20-case-client-contract.md) §4 | Step 1, Step 2, Step 3 | path diff and core contract regression selector | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-301` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | fake transport sees exact paths/bodies and bound results | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-302` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | guard/token/order/header tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-303` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | fake future/subscription tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-304` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | codec/media/status/required-field tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-305` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | strict mutation tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-306` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | mismatch matrix and terminal replay | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-307` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | error matrix/privacy tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-308` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | paired fake test | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-309` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | artifact parse/reflection/local-ref test | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-310` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | codec time tests | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-311` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | smoke/source diff | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-312` | [Spec](../spec/2026-11-01-23-45-case-http-client.md) §4 | Step 1, Step 2, Step 3 | path/static checks | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-101` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | governed fixture returns model；null/invalid/mismatch key generic reject | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-102` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | exact topology accepted；missing/extra/renamed/wrong ref/count/control/event rejected | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-103` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | mutation matrix rejects every forbidden construct with cause-free generic error | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-104` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | fixture parses roles 7/8 and 8/9；invalid governance rejects | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-105` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | visibility diff only；existing HumanTask/BPMN tests remain green | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-106` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | mutation attempts fail；returned XML clone；expected maps/actions/roles exact | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-107` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | exact plan item metadata and union `{7,8,9} | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-108` | [Spec](../spec/2026-11-01-22-55-cmmn-semantic-validation.md) §4 | Step 1, Step 2, Step 3 | path diff and final six-class selector；runtime boundary explicit | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-001` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | 正式 fixture 返回 namespace-aware `Document`；错误 schema 被拒绝 | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-002` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | empty/oversize/non-UTF8/DOCTYPE/错误 namespace 均抛同一 generic `IllegalArgumentException` 且不泄漏 cause/content | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-003` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | 旧签名不变；新 CMMN holder 不被旧选择提前触发；既有回归保持 | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-004` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | XSD 和 fixed converter 都保留三 plan item、三 sentry、两人任务、一个 milestone；无 repetition/EL/custom code/Form | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-005` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | 7 个 focused tests：6 个 acceptance tests 在 stub RED，1 个纯拒绝矩阵仍通过；不启动 engine/DB | Owning Step Evidence; dependency scope preserved under §2.3 |
| `REQ-006` | [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md) §4 | Step 1, Step 2, Step 3 | 精确 path diff；POM/SQL/config/API/enum/release admission 均无变化 | Owning Step Evidence; dependency scope preserved under §2.3 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Decision / owner | State |
| --- | --- | --- | --- |
| R-401 | Premature CMMN enum/admission | Root grouping B keeps all consumers and real factory atomic Step2 | Design closed |
| R-402 | Owner/executor or source occurrence confusion | Dual APPLIED joins, single release, rename-independent native occurrence uniqueness | Required tests |
| R-403 | SQL three-valued logic and immutable-table UPDATE privileges | Positive null guards, immutable advisory scope and exact descriptor tests | Required tests |
| R-404 | Native factory/parser or ABA mismatch | Exact local8 API evidence; both factory pointers; runtime REV domain | Required finite tests; native acceptance remains open |
| R-405 | Source removed while Inbox pending | Global bounded orphan anti-join and manual DEAD without borrowed SERVICE | Required tests |
| R-406 | Original S09 not executable under current no-runtime authority | Deliver true opt-in source and report RuntimeUnverified, retain original goal | Unverified runtime boundary, not source-design waiver |
| R-407 | Workspace or offline cache drift | Root rechecks Git/hashes and separates prerequisite failures | Executor action |

No unresolved source-design decision requires a new user approval. The original /goal authorizes necessary implementation and focused commits. Native acceptance cannot be declared complete from mocks or compilation.

## 12. Review and Acceptance

### 12.1–12.4 Fidelity and executability

Primary REQ-401–416, all dependency IDs, immutable SDK/OAS, exact source packages, single migration and all three sequential semantic commits are mapped. Each Step has independent 17 Manual Check and ten literal Rule rows; all execution statuses/counts are recorded later in Evidence. The Plan verdict is a design review result, not a claim tests have run.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-REUSE-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-DEP-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-NAME-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-VALID-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-MODEL-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-CONVERT-001 | Not applicable | N/A | §4.7/4.8 and exact Step-specific gate | No PO/mapper conversion | Execute and refresh actual evidence before each commit |
| MC-LOG-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-BEAN-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-UTIL-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-JSON-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-TIME-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-CONFIG-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-PATTERN-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-SCOPE-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-TEST-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |
| MC-BLOCKER-001 | Applicable | PASS | §4.7/4.8 and exact Step-specific gate | Concrete planned obligations and validation mapped | Execute and refresh actual evidence before each commit |

### 12.6 Final verdict

`PASS — Ready for user review`

Root full-read plus strict/hash unlocks only Step1 source work under existing continuation authority. Step2/3 remain locked until each predecessor is committed and its exact Git checkpoint verified. Native S09 and remaining original roadmap stay active.
