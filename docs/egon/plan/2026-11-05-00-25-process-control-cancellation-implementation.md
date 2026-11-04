# BPMN Process control and bounded cancellation implementation plan

| Field | Value |
| --- | --- |
| Document | `2026-11-05-00-25-process-control-cancellation-implementation.md` |
| Template Version | `5` |
| Status | `Draft` |
| Created | `2026-11-05 00:25 Asia/Shanghai` |
| Updated | `2026-11-05 01:40 Asia/Shanghai` |
| Owner | `Root; only Root may run Maven/tests, stage, commit, or authorize execution` |
| Repository | `tianji-service` |
| Scope | `Four sequential source-only Steps: C contract, CL HTTP capability, sole final V9/storage primitives, then PS control/cancel API` |
| Source Requirement | `Root full-read approval of the reviewed cancellation Spec; web Step 42 committed at 43c58a1069a50fb71b955540bae2d7e8526794b0; Draft Plan still awaits Root full read and explicit GO` |
| Baseline Revision | `main@cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d; clean; ProcessDB V8 latest; 8 Process SQL and 24 repository SQL; no V9` |
| Implements Spec | [BPMN Process control snapshot and bounded cancellation](../spec/2026-11-04-23-22-process-control-cancellation.md) |
| Spec Status | `Review` |
| Spec Revision | `Updated 2026-11-05 01:40 Asia/Shanghai; temporary reviewed SHA-256 0ad59a990b0474ed4a00198acb0f91ee71c78084fe06ade250b783e8094a8e22` |
| Effective Specs | [Primary cancellation Spec](../spec/2026-11-04-23-22-process-control-cancellation.md); [Formal CMMN Case server](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) `§5.1, §7.3, §11.4, §16`; [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) `§10, §11.2, §14.4, §17.3 T3-10/S10/S11` |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

This Draft Plan implements the reviewed cancellation Spec in exactly four semantic, sequential commits. Step 1 publishes the separate engine-neutral C capability and finite value tests plus reviewed Spec/Plan/evidence. Step 2 adds the strict CL HTTP implementation, packaged OAS3.1 and listener-free fake transport tests. Step 3 freezes the sole complete V9 and implements only storage/verifier/Agent-closure primitives—no API admission. Step 4 adds the useful control snapshot, root-fenced native cancellation, error/security/default-off wiring and runtime-free final audit.

Every behavior is test-first with a true assertion RED; reflection/resource assertions are used where a missing new type/file would otherwise cause a compile-only failure. Root alone may run Maven, tests, verification, staging and commits. All Maven verification commands include `-DskipITs=true`; `HttpProcessClientTest`, PostgreSQL/environment-gated tests, listeners and application/runtime startup are never selected. Mock/source/static evidence does not prove PostgreSQL atomicity, native Flowable concurrency or production IAM.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [BPMN Process control snapshot and bounded cancellation](../spec/2026-11-04-23-22-process-control-cancellation.md)
- Status: `Review`, intentionally not `Accepted`.
- Revision: `Updated 2026-11-05 01:40 Asia/Shanghai`; reviewed temporary SHA-256 `0ad59a990b0474ed4a00198acb0f91ee71c78084fe06ade250b783e8094a8e22`; baseline `cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d`.
- Approval evidence: Root read the entire 1,616-line design including route/OAS/security, time-domain, 256 safety-cap and exact validated-dispatch-key corrections; Root approved the design for original-goal continuation and explicitly authorized only this Draft Plan. No Plan/source GO exists.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Cancellation Spec](../spec/2026-11-04-23-22-process-control-cancellation.md) | Review/updated 2026-11-05 01:40 | all | authoritative contracts, schema, security, transaction, tests and rollout |
| Normative dependency | [Formal CMMN Case server](../spec/2026-11-04-15-50-formal-cmmn-case-server.md) | Review/implemented source baseline through `cfffb764` | §5.1, §7.3, §11.4, §16 | approved technical vertical, immutable V8/provenance grants and external pre-provisioned migration model |
| Legacy architecture/roadmap dependency | [Agent Framework Upgrade Plan](../../../../docs/plans/agent-framework-upgrade-plan.md) | current SHA `b6aebf5e5059bb94da801ba29ccdfa39ecacf095fe7af67425d844b634cb5b39`; T3-10 normative content unchanged | §10, §11.2, §14.4, §17.3 T3-10/S10/S11 | cancellation/Agent boundary and explicitly unfinished compensation/dead-letter recovery |

### 2.3 Superseded or excluded content

None is superseded. Excluded from this Plan: current frozen T3-11 task/Case UI, future T4-04 Process-owner UI, compensation, original-action business receipts, failed/dead-letter job operations, manual recovery, admin cancellation, Case cancellation, PG/native runtime execution and permission activation/grants.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-501` | §4 | separate ProcessControlClient; preserve ProcessClient/ProcessInstanceDTO | old reflection/JSON unchanged; new capability compiles | C/CL contracts/tests |
| `REQ-502` | §4/§7.3 | useful control snapshot with exact identity, known released waits, action/block/version/time | deterministic snapshot; unknown/257th occurrence -> 503; no raw IDs | C DTO, validator metadata, PS assembler/API |
| `REQ-503` | §4/§7/§9 | current USER/SERVICE auth before TX/replay; exact APPLIED START owner; no admin | cross tuple hidden 404; permission not ownership | permissions/controller/service/tests |
| `REQ-504` | §4/§9 | exact path/body, command ID, version and reason; stable same-hash replay | invalid 400; mismatch 409; unknown response safe replay | C/CL/API/generic receipt |
| `REQ-505` | §4/§7.3/§10 | terminal-before-cancel is audited accepted=false before live-version comparison | actual terminal/status/time; no duplicate delete | service/V9/result tests |
| `REQ-506` | §4/§7.3 | root-only fence; useful version recheck; any caseSources blocks; provenance plain SELECT | stale 409; Case 409; no provenance lock/grant change | native access/service/tests |
| `REQ-507` | §4/§7/§11 | native delete, generic receipt, V9 and exact validated dispatch CLOSING are atomic | injected failure leaves no partial platform state | V9/store/service/dispatch tests |
| `REQ-508` | §4/§7/§11 | remote Agent close remains existing maintenance outside TX | durable CLOSING, scheduled flag, no rollback claim | AgentDispatchStore/poller regressions |
| `REQ-509` | §4/§7.3 | races escape TX; exact outer classification | Flowable optimistic/not-found and 40001/40P01/55P03 -> 409; 57014/08xxx/unknown -> 503 | service/errors/tests |
| `REQ-510` | §4/§11/§16 | exactly one future complete V9 table+index; V1-V8 immutable | final bytes/checksum frozen in Step 3; no later DDL edit | SQL/verifier/static tests |
| `REQ-511` | §4/§11.4 | exact V8/V9 verifier compatibility across Case/task/release/storage consumers | V8 and V8+V9 accepted as designed; unknown future fails | verifier/tests; HumanTask main unchanged if boolean contract suffices |
| `REQ-512` | §4/§9/§16 | default-off, new cancel permission/resources/scopes; registration grants nothing | no beans/routes off; enabled requires API/servlet/V9; PaC exact | permissions/config/wiring/tests |
| `REQ-513` | §4/§9/§14 | strict bounded server/client JSON and fake HTTP only | exact fields/media/status/errors; no listener/auto retry | advice/codec/OAS/fake tests |
| `REQ-514` | §4/§18 | compensation/dead-letter/S10/S11 remain Required | docs/evidence never mark T3-10 complete | scope/evidence/final audit |
| `REQ-515` | §4/§6.3/§9-11 | preserve actual native finishedAt and DB decidedAt independently; UTC Z; no chronology | both valid non-null instants with either ordering; no synthesis/check | C/codec/OAS/V9/tests |
| `REQ-401` | Formal Case Spec §4 | one immutable V8 and old SQL preservation | Step 3/4 regression confirms V8 authority unchanged | compatibility only; no new implementation |
| `REQ-402` | Formal Case Spec §4 | explicit BPMN/CMMN/DMN release semantics | release/model regression stays GREEN | Step 4 regression |
| `REQ-403` | Formal Case Spec §4 | direct Case exact owner/executor/provenance | Case source/store regression unchanged | Step 3/4 regression |
| `REQ-404` | Formal Case Spec §4 | linked Case registered SERVICE/same release/owner inheritance | linked behavior remains unchanged | Step 4 regression/source audit |
| `REQ-405` | Formal Case Spec §4 | fixed Case provider contract | Case controller/client/OAS unchanged | Step 4 regression |
| `REQ-406` | Formal Case Spec §4 | Case read owner/operator realm | no cancel admin path or Case read drift | Step 4 source/regression |
| `REQ-407` | Formal Case Spec §4 | USER-only released plan-item actions | unchanged | Step 4 regression |
| `REQ-408` | Formal Case Spec §4 | native CMMN runtime version/history projection | unchanged | Step 4 regression |
| `REQ-409` | Formal Case Spec §4 | CASE HumanTask policy/receipt | unchanged | Step 3/4 regression |
| `REQ-410` | Formal Case Spec §4 | Case scope lock and immutable provenance grants | cancel uses plain SELECT and no provenance privilege change | Step 3/4 static/regression |
| `REQ-411` | Formal Case Spec §4 | governed CMMN native factory | unchanged | Step 4 regression |
| `REQ-412` | Formal Case Spec §4 | precreated Case completion Inbox/positive completion | unchanged | Step 4 regression |
| `REQ-413` | Formal Case Spec §4 | fixed Case source/message/timer continuation | models containing Case source are blocked by cancellation | Step 4 model/regression |
| `REQ-414` | Formal Case Spec §4 | independent default-off Case flags/inflight maintenance | cancellation adds a separate flag only | Step 4 config regression |
| `REQ-415` | Formal Case Spec §4 | current registered SERVICE, no remote IAM inside Case TX | cancellation does not borrow identity or change Case path | Step 4 source audit |
| `REQ-416` | Formal Case Spec §4 | original native S09 remains required/runtime-unverified | final evidence preserves RuntimeUnverified | Step 4 evidence only |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

1. Contract first: create immutable C values and separate capability so CL and PS compile against one engine-neutral authority. Commit the reviewed Spec/Plan with the contract evidence.
2. Transport second: implement only CL/OAS against Step 1. This proves the unreleased wire independently and avoids contaminating server admission.
3. Persistence third: freeze the sole complete V9 and all plain-JDBC primitives/verifiers. Nothing in this Step registers a route/Bean or enables cancellation; after its commit V9 bytes are immutable.
4. Server last: assemble model-known waits, exact-owner control, root-fenced cancel, errors, PaC and default-off wiring over committed contracts/storage, then perform the final source-only conformance audit.

This order is compilable, prevents a route from existing before durable audit/readiness, and ensures each commit has one semantic outcome. Web Step 42 is now committed at `43c58a1069a50fb71b955540bae2d7e8526794b0`; no backend Step may start until Root completes the remaining full Plan review and gives explicit GO.

### 4.2 Test-first strategy

| Step | First RED file/assertion | True RED reason | Minimum GREEN |
| --- | --- | --- | --- |
| 1 | new `ProcessControlContractTest` initially uses reflection/resource-independent assertions | `ProcessControlClient` and values are absent; assertion fails, not compilation/fixture | exact records/enums/interface/constructors/constants |
| 2 | fake `HttpProcessControlClientTest` and packaged-resource test use reflection/fake JDK HTTP | HttpProcessClient lacks capability/codec and OAS resource is absent | codec + client methods + static OAS |
| 3 | SQL/verifier/store/dispatch tests start with resource/reflection/behavior assertions | V9/store/method/history compatibility absent; no PG required | final V9 + generic/detail/verifier/dispatch primitive |
| 4 | control/native/service/controller/config tests begin with reflection/context/string Bean assertions | server classes/routes/beans/metadata are absent; assertions fail without compile break | useful Query and bounded Command path/default-off wiring |

After the missing symbol exists, each RED test is converted/extended to typed assertions before the Step GREEN commit. A failed compile, missing fixture setup, listener or database connection is not accepted as RED evidence.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | web Step 42 commit `43c58a1069a50fb71b955540bae2d7e8526794b0` + Root execution GO | no backend Step; planning only may coexist with further review | any C/docs path modified by another worker | web dependency is satisfied; Root GO remains |
| Step 2 | Step 1 committed | none | HttpProcessClient/client resources/tests | consumes committed C types |
| Step 3 | Step 2 committed | none | Process SQL/verifier/receipt/dispatch paths | freezes sole V9 before API admission |
| Step 4 | Step 3 committed | none | PS release/execution/API/security/config/tests | consumes final V9/storage; final source audit |

### 4.4 Commit boundaries

Exactly four commits, one per Step. Root alone inspects the diff, stages exact Step paths, runs all listed verification, commits once, and verifies clean/path-limited status. Shared files are intentionally sequential: Spec/Plan are created only in Step 1; no later Step edits them. Evidence uses one new Step-specific file per commit. V9 is created and frozen only in Step 3 and is forbidden from Step 4.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| ProcessControlClient + useful Query | §7.0 Add | ProcessClient only start/get; DTO fixed | extending old interface/basic GET insufficient | new interface/GET/DTOs | Implement Steps 1/2/4 |
| version-only preflight | §7.0 Remove | none | useful Query | needless RTT | Do not implement |
| cancel Command | §7.0 Add | no lifecycle mutation | DELETE/generic engine endpoint insufficient | POST/audit/permission | Implement Steps 1-4 |
| service/native/store vertical | §7.0 Add | current Process/Case native/store patterns | expanding ProcessExecutionService destabilizes start/get | three focused technical classes | Implement Steps 3/4 |
| one V9 + dispatch index | §7.0 Add | generic receipt lacks result; V5 index wrong prefix | delete reason/tenant scan insufficient | one immutable migration/rollout | Implement Step 3 only |
| child ACT locks/global fence | §7.0 Remove | human/worker/timer child-first paths differ | root fence + rollback classifier | invasive engine coupling | Do not implement |
| new dependency/framework/Event/job/UI | §7.0 Remove/§3 non-goals | current JDK/Spring/Flowable/JDBC/poller sufficient | reuse existing | zero new runtime | Do not implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| C capability/value contract | 501/502/504/505/515 | ProcessControlContractTest | C/JDK only | interface/DTO/enums/Command/Result | CL/PS | Step 1 |
| CL transport/OAS | 501/502/504/513/515 | fake client + OAS tests | Step 1 | strict codec/client/static contract | PS/provider consumers | Step 2 |
| V9 audit and verifier | 504/505/510/511/515 | SQL/verifier/store tests | Step 1 types; V8 latest | final V9/store/readiness | Step 4 | Step 3 |
| dispatch closure primitive | 507/508 | AgentDispatchStoreTest | V5 + Step 3 index contract | bounded exact-key CLOSING | Step 4 | Step 3 |
| useful control/native cancel API | 502/503/506/507/509/512/513/514 | service/native/controller/config tests | Steps 1-3 | default-off complete backend slice | future T4-04 UI | Step 4 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | current C/CL/PS technical vertical and formal Case Spec exception | preserve explicit approved non-business technical profile | no MP/Lombok/biz/COLA migration; current packages/factories/JDBC only | all; MC-ARCH-001, MC-SCOPE-001 |
| Reuse/capability | ProcessClient/HttpProcessClient, Flowable, receipts, AgentDispatchStore/poller, Jackson | reuse; no dependency | extend exact symbols only | all; MC-REUSE-001, MC-DEP-001 |
| Naming/model/validation/conversion | immutable C records, compact constructors, strict advice/codecs, direct exact construction | approved technical exceptions preserved | semantic suffixes; records/defensive copies; no duplicate converter | Steps 1/2/4; MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001 |
| Bean/logging/util/JSON/time/config | explicit `@Bean` factories/plain final services/LoggerFactory, one application.yml | preserve exception; safe logs/Jackson/Instant/default-off | no Lombok refactor; exact Bean methods; reason/SQL omitted | Steps 2-4; MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001 |
| Business variation/pattern | existing Command + durable receipt | Command pattern; Flowable state authority; direct closed classifier | no strategy/state hierarchy; explicit transaction branches/tests | Steps 3/4; MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| contract validation | OrchestrationChecks/records | C orchestration package | hash/id/command fit; reason needs exact safe-text helper or record-local check | reuse/targeted helper only | no dependency | Step 1; MC-REUSE-001, MC-VALID-001 |
| HTTP | JDK HttpClient/HttpProcessClient/Case codec | CL source/tests | transport/bounds fit; process-control strict codec absent | extend current client/add codec | no dependency | Step 2 |
| JSON/OAS | Jackson3 + case-v1 static artifact | CL POM/resources | exact precedent | reuse | no dependency | Step 2 |
| lifecycle/history | Flowable Runtime/HistoryService | ProcessExecutionService | APIs/TM fit; cancellation boundary absent | new native access/service | no dependency | Step 4 |
| idempotency/audit | ProcessCommandReceiptStore | PS execution | generic lifecycle fits; detailed result absent | reuse + one store/table | no dependency | Step 3 |
| Agent close | AgentDispatchStore/worker/poller | PS bridge | CLOSING/remote reconciliation fit; scope method/index absent | extend store/index | no dependency/job | Step 3 |
| permissions/config | TianjiProcessAccess/PaC/config guards | PS security/config | fit; cancel metadata/flag absent | extend | no dependency | Step 4 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2/§8/§10 | current suffix conventions | C types -> codec/store/service/API/errors/tests | exact DTO/Command/Result/Enum/Client/Service/Store/Access/Codec/Exception names | compile + type inventory | 1-4 | PASS |
| Rule 2 | Spec §6.2/§9/§10 | compact constructors + strict advice/service source checks | contract -> client codec -> store -> API/service | exact validation at each handoff; no phone/groups apply under exception | negative contract/codec/MockMvc/store tests | 1-4 | PASS under approved technical exception |
| Rule 3 | Spec §6.2/§10 | immutable contract records/direct technical mapping | C records before consumers; nested records in stores/access | defensive copies/cross-field rules; no converter because no semantic mapping | constructor/JSON/store mapping tests | 1-4 | PASS under approved exception |
| Rule 4 | Spec §6.2/§8 | explicit factories/plain final classes/no lombok.config | service/access/store then named config methods | preserve explicit constructor/LoggerFactory exception; safe logs | compile/wiring/log search | 3/4 | PASS under Root exception |
| Rule 5 | Spec §6.2/§15 | JDK/Spring/Jackson/JDBC only | every production file | no unapproved utility/helper library | POM/import diff | 1-4 | PASS |
| Rule 6 | Spec §6.2/§9 | Boot/CL Jackson3 | C fields -> codec/OAS -> advice/API | exact names/null/enums/UTC/unknown-field semantics | fake/serialization/OAS/MockMvc | 1/2/4 | PASS |
| Rule 7 | Spec §6.2/§15 | one application.yml/no profiles | Step 4 config and parity test | one default-off key/scope mapping; no invented profiles/properties class | config tree/key test | 4 | PASS |
| Rule 9 | Spec §6.2/§13 | existing receipt pattern/complex races | generic/detail receipt before service | Command + durable receipt; no parallel state machine | replay/branch/race tests | 3/4 | PASS |
| Rule 10 | Spec §6.3/§10/§11 | Instant/timestamptz/current clocks | C -> codec/OAS -> V9/store -> service/tests | actual independent finished/decided instants; no chronology; Duration bounds | time/forbidden import/static SQL tests | 1-4 | PASS |
| Rule 11 | Spec §6.1/§6.2/§8 | approved technical vertical exception | every target path in §5 | no third/hybrid/new layer/MP conversion | tree/POM/diff scope | Every Step | PASS under Root boundary |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-501` | Snapshot.observedAt | Occurred instant | Instant | Not persisted | UTC Z | Not applicable: technical observation | future consumer display; no frontend in Plan | no local/DST input | DB clock/effective microseconds; excluded from version | new | Step 1, Step 2, Step 4; TEST-503/513 |
| `TIME-502` | Result.finishedAt/V9 result_finished_at | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: native occurrence | future display | preserve actual Flowable engine/JVM instant; no DB comparison | native precision -> timestamptz(6) audit | existing finishedAt unchanged | Step 1, Step 2, Step 3, Step 4; TEST-508/513 |
| `TIME-503` | Result.decidedAt/V9 decided_at | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: audit occurrence | future display | preserve actual DB instant; no native ordering | DB clock/timestamptz(6) | new/no chronology | Step 1, Step 2, Step 3, Step 4; TEST-507/513 |
| `TIME-504` | transaction/lock bounds | Duration | Duration/DB setting | Not persisted | Internal | Not applicable: elapsed technical bound | Not applicable: no UI | no calendar/DST | 10s TX/5s lock; exact SQLSTATE | current style | Step 4; TEST-510 |

## 5. Change File Tree

```text
stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlContractTest.java CREATE [Step 1]
stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/{ProcessControlClient,ProcessControlSnapshotDTO,ProcessActiveWaitDTO,ProcessActiveWaitKindEnum,ProcessControlActionEnum,ProcessControlBlockReasonEnum,CancelProcessCommand,CancelProcessResult}.java CREATE [Step 1]
stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/{HttpProcessControlClientTest,ProcessControlOpenApiContractTest}.java CREATE [Step 2]
stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessControlResponseCodec.java CREATE [Step 2]
stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java MODIFY [Step 2]
stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-control-v1.json CREATE [Step 2]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/{ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest}.java CREATE [Step 3]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCancellationReceiptStoreTest.java CREATE [Step 3]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStoreTest.java MODIFY [Step 3]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java MODIFY [Step 3]
stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V9__add_process_cancellation_receipts.sql CREATE [Step 3, immutable after commit]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/{ProcessCommandReceiptStore,ProcessCancellationReceiptStore}.java MODIFY/CREATE [Step 3]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java MODIFY [Step 3]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java MODIFY [Step 3]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/{ProcessControlNativeAccessTest,ProcessControlServiceTest}.java CREATE [Step 4]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlControllerTest.java CREATE [Step 4]
stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/{ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest}.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/{ValidatedProcessModel,ProcessModelValidator}.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/{ProcessControlNativeAccess,ProcessControlService}.java CREATE [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/{ProcessVersionConflictException,ProcessCancellationBlockedException}.java CREATE [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlController.java CREATE [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/{ProcessRequestBodyAdvice,ProcessApiErrors}.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/{ProcessSecurityConfiguration,ProcessStorageConfiguration}.java MODIFY [Step 4]
stellar-enclosure-tianji-process-server/src/main/resources/application.yml MODIFY [Step 4]
docs/egon/
  spec/2026-11-04-23-22-process-control-cancellation.md                             CREATE [Step 1]
  plan/2026-11-05-00-25-process-control-cancellation-implementation.md             CREATE [Step 1]
  evidence/2026-11-05-process-control-contract.md                                  CREATE [Step 1]
  evidence/2026-11-05-process-control-http-client.md                               CREATE [Step 2]
  evidence/2026-11-05-process-cancellation-storage.md                              CREATE [Step 3]
  evidence/2026-11-05-bounded-process-cancellation.md                              CREATE [Step 4]
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | C `ProcessControlContractTest.java` | sibling `OrchestrationContractTest` | finite RED/GREEN contract matrix | contract proof | 1 | 501/502/504/505/515 | Step 1 |
| CREATE | C eight production files listed above | absent; current orchestration records/interfaces | separate immutable control/cancel contract | public engine-neutral authority | 1 | 501/502/504/505/515 | Step 1 |
| CREATE | Spec/Plan/contract evidence paths | reviewed `/tmp` drafts; evidence absent | repository review artifacts + actual Step result | governance/evidence | 1 | all | Step 1 |
| CREATE | CL two tests | Case fake/OAS patterns | no-listener transport/OAS proof | RED/GREEN | 2 | 501/502/504/513/515 | Step 2 |
| CREATE/MODIFY | CL codec/client/OAS | HttpProcessClient/Case codec precedent | strict ProcessControlClient implementation/static OAS | transport/docs | 2 | 501/502/504/513/515 | Step 2 |
| CREATE | storage five test changes | current verifier/store tests; no cancellation tests | V9/history/store/exact-key regression | RED/GREEN | 3 | 504/505/507/508/510/511/515 | Step 3 |
| CREATE | Process V9 SQL | V8 latest SHA pinned | complete immutable cancellation table + Agent discovery index | sole migration | 3 | 510/511/515 | Step 3 |
| CREATE/MODIFY | command/detail/dispatch/verifier production | current generic/V5/V8 code | PROCESS_CANCEL, immutable result, exact-key CLOSING, exact V8+V9 readiness | storage foundation | 3 | 504/505/507/508/510/511 | Step 3 |
| CREATE/MODIFY | Step 4 eight tests | current Process/Case tests | useful Query/cancel/security/config/race proof | RED/GREEN/final audit | 4 | 502/503/506/507/509/512/513/514/515 | Step 4 |
| CREATE/MODIFY | Step 4 model/native/service/error/API/config production | current release/vertical factories/routes | default-off complete backend path | application/API | 4 | 502/503/506/507/509/512/513/515 | Step 4 |
| CREATE | evidence files per Step | absent | actual commands/counts/hashes/limits only after Root execution | honest proof | 1-4 | all | each Step |

Every path appears in one owning Step. `HumanTaskStorageVerifier.java`, `ProcessExecutionService.java`, `ProcessClient.java`, `ProcessInstanceDTO.java`, `HttpProcessClientTest.java`, V1-V8, POMs and frontend files are intentionally absent from the write tree.

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- Repository root: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Baseline: clean `main@cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d`; remote tracking is gone and irrelevant to local commits.
- User-provided AGENTS rules apply; no on-disk AGENTS.md was found.
- Web Step 42 is committed at `43c58a1069a50fb71b955540bae2d7e8526794b0` and that repository was reported clean. Backend execution remains forbidden until Root finishes the full Plan review and explicitly approves execution.
- Root alone runs Maven/tests, stages exact declared paths, commits once per Step and verifies status. Preserve concurrent/unrelated work; never stage broad directories when exact paths are available.
- Before every Step, recheck HEAD, clean/owned paths, latest Process migration and no competing V9. Drift in contract/schema/security/transaction semantics returns to the Spec.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK | `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` | Java 25 available | compile/test only |
| Isolated Maven settings | `-gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o` | Root reverified file contains only localRepository and interactiveMode=false; no credentials/mirror | offline cache only; never edit/use global settings or network |
| Reactor | `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o` | sibling Components/Shoubing/Jianshen/Tianshu POMs and cached dependencies present | source reactor; does not start app |
| Maven gates | every test command adds `-B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none` plus exact `-Dtest` | Root-only execution | explicit selected unit/MockMvc/fake/static tests |
| Offline resolution failure | Maven reports missing cached artifact/plugin while `-o` is active | classify separately from test RED/failure | stop owning Step; do not use network, change settings, add dependency or modify repository to hide the cache gap |
| Forbidden selector | never select `HttpProcessClientTest`, `*PostgresTest`, `ProcessServerPostgresTest`, `FormalCasePostgresTest` | no listener/DB/environment runtime | enforced by selector review |
| Runtime | no credentials/context/database/browser | not required for this Plan | native atomicity/concurrency/IAM remain Unverified |
| Git | `git diff --check`, `git status --short`, then `git add --` followed by the owning Step's literal Commit paths line; one commit | Root-only | path/scope proof |

### 6.3 Immutable constraints and approved decisions

- Preserve ProcessClient/ProcessInstanceDTO and existing routes/JSON; separate capability only.
- Exact current START owner USER/SERVICE, current auth before TX/replay, no admin.
- Control Query is useful; version excludes all clock-now fields. Active/native and dispatch caps are explicit safety caps, not model/runtime cardinality proof.
- Any model Case source blocks. V8 provenance remains SELECT/INSERT only and plain-read in this slice.
- Root-only ACT fence; no child ACT locks/direct writes. Native race classification occurs outside rolled-back TX.
- V9 is one complete final script in Step 3. V1-V8 are immutable. Step 4 must not alter V9 or add DDL.
- Dispatch discovery is scope-indexed; CLOSING updates target only prelocked/validated full keys and require exact affected counts.
- Preserve actual independent Flowable `finishedAt` and DB `decidedAt`; no chronology check/synthesis.
- Default off; permission registration is not activation/grant. No remote I/O inside TX.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-501` | Add immutable `timerWaits()` metadata to `ValidatedProcessModel` using the parser's existing timer map | validator already parses every supported timer; Spec §7.3 approves timer metadata | internal read projection only; no model admission change | exact internal field name/test location changes |
| `PLAN-CLAR-502` | Keep `HumanTaskStorageVerifier.java` unchanged; update its test because unchanged `hasCaseMigration()` boolean can become V8+V9 aware inside ProcessApiSchemaVerifier | current HumanTask verifier calls the boolean only | no contract/schema behavior redesign | if implementation requires signature change, return to Plan review before touching main file |
| `PLAN-CLAR-503` | Use nested immutable `AgentDispatchStore.ProcessClosureScope` rather than a new top-level carrier | Spec INTERNAL-503 and current nested Key/Seed/Intent pattern | same fields/visibility, fewer classes | only source placement changes |
| `PLAN-CLAR-504` | Use one dedicated static SQL descriptor test and mocked verifier tests; do not author/compile a new opt-in PG scenario | Spec requires runtime-free finite proof; existing PG paths are environment-gated | preserves validation boundary | PG/runtime atomicity remains explicitly Unverified |

No business, public-contract, schema or security clarification is introduced.

## 7. Ordered File-by-file Implementation Steps

> Execution gate for every Step: web Step 42 is committed at `43c58a1069a50fb71b955540bae2d7e8526794b0`; Root has not yet approved the full Plan. Root alone may later authorize and perform commands/commits. Until explicit GO this chapter is instruction only.

### Step 1 — Publish the separate Process control contract

- Requirements: `REQ-501`, `REQ-502`, `REQ-504`, `REQ-505`, `REQ-515`; documentation trace for all requirements.
- Dependencies: clean `cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d`; Root execution gate.
- Baseline state: C has ProcessClient start/get and existing ProcessInstanceDTO only; reviewed Spec/Plan exist only under `/tmp`; no cancellation types/tests.
- Observable outcome: engine-neutral immutable control/cancel Java capability exists without changing ProcessClient/DTO/wire; finite contract suite is GREEN; reviewed documents/evidence are committed.
- End state: C artifact supplies all Step 2/4 types. No HTTP implementation, V9, storage or server route exists.
- Test-first gate: `Required` — reflection-based `ProcessControlContractTest` compiles against current C and fails JUnit assertions because the approved classes/interface are absent; missing class is caught and asserted, never allowed to abort as compile/fixture error.
- Manual Checks: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`.
- Literal Rules: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11`.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlContractTest.java`

- Purpose: Establish true RED then typed final contract invariants.
- Symbols: reflection tests for eight absent types; final tests for exact components, constants, immutability, safe reason/hash/IDs, wait/action/block matrices, terminal/no-chronology result invariants and unchanged ProcessClient/DTO reflection.
- Repository evidence: sibling `OrchestrationContractTest` uses JUnit5, record-component and immutable-value assertions.
- Dependencies and consumers: test-only C/JDK/JUnit; final production files 2-9.
- Why now: fixes contract behavior before production types without a compile-only RED.
- Contract/signature changes: initial `Class.forName`/`assertDoesNotThrow` assertions; after GREEN use direct types and retain reflection compatibility assertions.
- Input/output and state mapping: constructor inputs -> exact record components/defensive copies; two independent Instant orderings -> valid CancelProcessResult; object/list/enum/Instant nulls follow existing Objects.requireNonNull/List.copyOf NullPointerException behavior; scalar grammar and cross-field failures throw IllegalArgumentException.
- Error and edge behavior: reject controls/unpaired surrogates/trim changes, count outside 1..256, duplicate wait key, action/block mismatch, accepted non-CANCELLED, accepted=false closure flag; never assert finishedAt/decidedAt chronology.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-TEST-001; no converter or Bean.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11 — exact semantic records/enums, compact validation, defensive copy, Jackson-default field names, java.time and current package.
- Implementation pseudocode:

```java
@Test missing_contract_is_a_true_assertion_red() {
  assertDoesNotThrow(() -> Class.forName(CONTROL_CLIENT_FQCN)); // fails assertion before files 2-9
}
@Test final_contract_matrix() {
  wait = new ProcessActiveWaitDTO("review", HUMAN_TASK, 1)
  snapshot = new ProcessControlSnapshotDTO(existingProcess, List.of(wait), Set.of(CANCEL), NONE, HASH, OBSERVED)
  assert immutable and exact record names including cancellationBlockReason
  assert valid results for finishedAt < decidedAt and finishedAt > decidedAt
  assert invalid cross-field/status/action/block/reason/count cases
  assert ProcessClient methods and ProcessInstanceDTO components equal baseline
}
```

- Verification contribution: Step 1 RED/GREEN selector; TEST-501/502/515.
- After this file: initial RED is assertion-based; final file is typed and GREEN after File 9.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessActiveWaitKindEnum.java`

- Purpose: Freeze five wire wait categories.
- Symbols: `HUMAN_TASK`, `EXTERNAL_WORKER`, `AGENT_RESULT_WAIT`, `CASE_COMPLETION_WAIT`, `TIMER`.
- Repository evidence: existing `*Enum` values use explicit closed names.
- Dependencies and consumers: ProcessActiveWaitDTO, codec/OAS/server assembler.
- Why now: DTO compile prerequisite.
- Contract/signature changes: new enum only; no persistence ordinal or custom code.
- Input/output and state mapping: exact `name()` is wire value; unknown/null rejected at strict boundaries.
- Error and edge behavior: no `OTHER`; server returns 503 for unknown mapping.
- Standards impact: MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-DEP-001.
- Literal rule enforcement: Rule 1, Rule 3, Rule 5, Rule 6, Rule 11 — semantic enum, no ordinal/custom dependency.
- Implementation pseudocode:

```java
public enum ProcessActiveWaitKindEnum {
    HUMAN_TASK, EXTERNAL_WORKER, AGENT_RESULT_WAIT, CASE_COMPLETION_WAIT, TIMER
}
// Verify exact name-based wire values and reject any unknown value at the strict codec boundary.
```
- Verification contribution: enum/golden JSON matrix.
- After this file: enum compiles; RED remains until the complete capability exists.

#### File 3 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlActionEnum.java`

- Purpose: Freeze the sole first-profile control action.
- Symbols: `CANCEL`.
- Repository evidence: Case/action enums are closed protocol values.
- Dependencies and consumers: snapshot/codec/OAS/UI handoff.
- Why now: snapshot prerequisite.
- Contract/signature changes: new enum only.
- Input/output and state mapping: exact JSON string `CANCEL`.
- Error and edge behavior: no admin/retry/compensate actions.
- Standards impact: MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 3, Rule 6, Rule 11.
- Implementation pseudocode:

```java
public enum ProcessControlActionEnum {
    CANCEL
}
// The control snapshot compact constructor permits only empty or singleton CANCEL action sets.
```
- Verification contribution: allowed-actions invariant tests.
- After this file: action type compiles; no behavior exposed.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlBlockReasonEnum.java`

- Purpose: Freeze explicit cancellation block reasons.
- Symbols: `NONE`, `PROCESS_TERMINAL`, `MODEL_CONTAINS_CASE_SOURCE`.
- Repository evidence: Spec §9 and existing closed enums.
- Dependencies and consumers: snapshot/codec/OAS/server service.
- Why now: snapshot prerequisite.
- Contract/signature changes: new enum only.
- Input/output and state mapping: exact names; terminal takes precedence over Case model in read snapshot.
- Error and edge behavior: unknown values rejected; no dependency error encoded as a block reason.
- Standards impact: MC-NAME-001, MC-MODEL-001, MC-JSON-001.
- Literal rule enforcement: Rule 1, Rule 3, Rule 6, Rule 11.
- Implementation pseudocode:

```text
public enum ProcessControlBlockReasonEnum {
    NONE, PROCESS_TERMINAL, MODEL_CONTAINS_CASE_SOURCE
}
ProcessControlSnapshotDTO validates NONE only with singleton CANCEL, and both block values only with no actions.
```
- Verification contribution: block/action/status matrix.
- After this file: block type compiles.

#### File 5 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessActiveWaitDTO.java`

- Purpose: Carry one released grouped active-wait summary.
- Symbols: record `(String activityKey, ProcessActiveWaitKindEnum kind, int occurrences)`.
- Repository evidence: C uses immutable records with compact constructors/OrchestrationChecks.
- Dependencies and consumers: Files 2/6; CL/PS.
- Why now: snapshot nested type prerequisite.
- Contract/signature changes: definition-key validation and occurrences 1..256.
- Input/output and state mapping: released activity key/kind/native grouped count -> wire fields.
- Error and edge behavior: reject null/invalid key/kind/count; no raw native ID.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001; immutable record exception.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 11.
- Implementation pseudocode:

```text
record ProcessActiveWaitDTO(String activityKey, ProcessActiveWaitKindEnum kind, int occurrences) {
  activityKey = OrchestrationChecks.definition(activityKey)
  kind = Objects.requireNonNull(kind, "kind")
  if occurrences < 1 or occurrences > 256 then throw OrchestrationChecks.invalid()
}
```
- Verification contribution: constructor and JSON field tests.
- After this file: nested DTO compiles and is immutable.

#### File 6 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlSnapshotDTO.java`

- Purpose: Publish the independently useful control Query result.
- Symbols: record `(ProcessInstanceDTO process, List<ProcessActiveWaitDTO> activeWaits, Set<ProcessControlActionEnum> allowedActions, ProcessControlBlockReasonEnum cancellationBlockReason, String version, Instant observedAt)`.
- Repository evidence: ProcessInstanceDTO and Case DTO defensive-copy/cross-field style.
- Dependencies and consumers: Files 2-5; ProcessControlClient/CL/PS.
- Why now: complete Query value before interface.
- Contract/signature changes: exact field name `cancellationBlockReason`; defensive immutable sorted/unique waits/actions; hash/Instant validation.
- Input/output and state mapping: terminal -> empty waits/actions + PROCESS_TERMINAL; live Case model -> empty actions + MODEL_CONTAINS_CASE_SOURCE; eligible -> CANCEL+NONE.
- Error and edge behavior: reject duplicate/unsorted waits, aggregate >256, action/block/status mismatch; observedAt excluded from version by consumer/service contract.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
record ProcessControlSnapshotDTO(process, activeWaits, allowedActions, cancellationBlockReason, version, observedAt) {
  requireNonNull(process, cancellationBlockReason, observedAt); activeWaits=List.copyOf(activeWaits); allowedActions=Set.copyOf(allowedActions)
  reject duplicate (kind,activityKey), noncanonical kind.name/activityKey order, or sum(occurrences)>256
  require terminal => waits/actions empty and PROCESS_TERMINAL; eligible live => singleton CANCEL and NONE; Case block => actions empty
  version = OrchestrationChecks.hash(version) // observedAt is validated but never contributes to this supplied opaque token
}
```
- Verification contribution: snapshot matrix/immutability/time tests.
- After this file: Query result compiles; no client method yet.

#### File 7 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CancelProcessCommand.java`

- Purpose: Carry one stable cancellation intent.
- Symbols: record `(String instanceId, String commandId, String expectedVersion, String reason)`.
- Repository evidence: Start/Agent cancel commands and OrchestrationChecks id/command/hash/safe text.
- Dependencies and consumers: interface/CL/PS/request advice/V9 digest.
- Why now: Command prerequisite.
- Contract/signature changes: exact four members; reason exact 1..512, no trim/control/unpaired surrogate.
- Input/output and state mapping: route target + idempotency + snapshot token + audit reason.
- Error and edge behavior: reject missing/null/blank/unsafe; no tenant/actor/release fields; toString must not expose reason.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11.
- Implementation pseudocode:

```text
record CancelProcessCommand(instanceId, commandId, expectedVersion, reason) {
  instanceId = OrchestrationChecks.id(instanceId,255); commandId=OrchestrationChecks.command(commandId)
  expectedVersion = OrchestrationChecks.hash(expectedVersion); reason=OrchestrationChecks.id(reason,512)
  // id() already rejects blank, trim changes, controls and malformed surrogate pairs; do not add a second helper
  toString returns "CancelProcessCommand[redacted]"
}
```
- Verification contribution: command grammar/redaction tests.
- After this file: Command compiles.

#### File 8 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CancelProcessResult.java`

- Purpose: Publish stable accepted/replayed/terminal-no-op outcome.
- Symbols: record `(String instanceId,String commandId,boolean accepted,ProcessInstanceStatusEnum status,String resultingVersion,Instant finishedAt,Instant decidedAt,boolean linkedAgentClosureScheduled)`.
- Repository evidence: existing result records and corrected Spec time contract.
- Dependencies and consumers: interface/codec/V9/service.
- Why now: Command result prerequisite.
- Contract/signature changes: terminal statuses only; accepted=>CANCELLED; accepted=false=>closureScheduled false; both instants nonnull/individually valid with no ordering.
- Input/output and state mapping: native history + DB decision + dispatch schedule -> exact fields.
- Error and edge behavior: no chronology check/synthesis; redacted representation.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
record CancelProcessResult(instanceId,commandId,accepted,status,resultingVersion,finishedAt,decidedAt,linkedAgentClosureScheduled) {
  validate id/command/hash; require status in COMPLETED,CANCELLED and both Instants nonnull
  if accepted and status != CANCELLED then invalid; if !accepted and linkedAgentClosureScheduled then invalid
  deliberately do not compare finishedAt with decidedAt because their clock domains are independent
  toString omits no sensitive request reason because the result never carries it
}
```
- Verification contribution: terminal/no-op/time ordering tests.
- After this file: result compiles.

#### File 9 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlClient.java`

- Purpose: Expose separate Java Query/Command capability and stable security constants.
- Symbols: `control(GetProcessInstanceQuery)`, `cancel(CancelProcessCommand)`, control/cancel path/resource and `CANCEL_SCOPE` constants.
- Repository evidence: ProcessClient/CaseClient/DecisionClient small interfaces and constants.
- Dependencies and consumers: Files 6-8; HttpProcessClient/future server contract.
- Why now: types are complete; closes production contract and makes File 1 typed GREEN.
- Contract/signature changes: new interface only; exact resource strings from Spec; no ProcessClient modification/default.
- Input/output and state mapping: Query -> snapshot; Command -> result; no engine/security type.
- Error and edge behavior: implementation-defined transport exceptions; null inputs rejected by implementations/records.
- Standards impact: MC-ARCH-001, MC-NAME-001, MC-SCOPE-001; interface segregation.
- Literal rule enforcement: Rule 1, Rule 9, Rule 11.
- Implementation pseudocode:

```text
interface ProcessControlClient {
  String CANCEL_SCOPE="tianji:process:cancel"; String CONTROL_RESOURCE_KEY="tianji.api.process.instance.control"
  String CANCEL_RESOURCE_KEY="tianji.api.process.instance.cancel"
  ProcessControlSnapshotDTO control(GetProcessInstanceQuery query)
  CancelProcessResult cancel(CancelProcessCommand command)
}
```
- Verification contribution: reflection/method/constants compatibility tests.
- After this file: Step 1 production/tests compile and GREEN.

#### File 10 — `CREATE docs/egon/spec/2026-11-04-23-22-process-control-cancellation.md`

- Purpose: Commit the fully reviewed authoritative Spec with current field fix/backlink.
- Symbols: Template V9, Updated 2026-11-05, Related Plans link, cancellationBlockReason.
- Repository evidence: `/tmp/process-cancel-spec.md`, strict SHA recorded in execution evidence.
- Dependencies and consumers: Plan/execution/Root review.
- Why now: contract commit must carry its authority.
- Contract/signature changes: documentation only; no redesign/Accepted claim.
- Input/output and state mapping: reviewed temporary bytes -> repository artifact.
- Error and edge behavior: strict validator failure blocks commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 — records approved framework exception/scope.
- Implementation pseudocode:

```text
copy /tmp/process-cancel-spec.md to the exact repository path without rewriting normative sections
verify Document/Updated/Related Plans and Snapshot.cancellationBlockReason match the reviewed temporary artifact
run validate_spec.py on the repository path with --strict; record its SHA-256 and keep Status=Review
```
- Verification contribution: strict Spec gate/hash.
- After this file: repository has Review Spec, not coding acceptance.

#### File 11 — `CREATE docs/egon/plan/2026-11-05-00-25-process-control-cancellation-implementation.md`

- Purpose: Commit this Root-reviewed Draft Plan without changing status to Ready/Accepted.
- Symbols: Template V5/four Steps/Root-only gates.
- Repository evidence: `/tmp/process-cancel-plan.md` and strict validator.
- Dependencies and consumers: execution workflow.
- Why now: same governance commit as contract.
- Contract/signature changes: documentation only.
- Input/output and state mapping: reviewed temporary bytes -> repository Plan.
- Error and edge behavior: link/status/trace/manual-check failure blocks commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 — exact technical profile throughout.
- Implementation pseudocode:

```text
copy /tmp/process-cancel-plan.md to the exact repository path
verify Implements Spec resolves to one Review Spec and all four Steps/commit paths remain unchanged
run validate_plan.py --strict and retain Status=Draft until Root separately authorizes execution
```
- Verification contribution: strict Plan gate/hash.
- After this file: Plan is reviewable, not executable without Root GO.

#### File 12 — `CREATE docs/egon/evidence/2026-11-05-process-control-contract.md`

- Purpose: Record actual Step 1 RED/GREEN/docs/hash/diff evidence after Root execution.
- Symbols: baseline/commit paths/test selectors/results/Spec+Plan SHA/runtime limits.
- Repository evidence: existing per-Step evidence convention.
- Dependencies and consumers: Root final audit; no production code reads it.
- Why now: close Step without fabricating future results.
- Contract/signature changes: none.
- Input/output and state mapping: actual command outputs/counts/hashes only.
- Error and edge behavior: dependency resolution/compile/assertion failures reported distinctly; never prefill PASS.
- Standards impact: MC-TEST-001, MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 evidence mapping.
- Implementation pseudocode:

```text
write baseline cfffb764, exact RED selector/exit/assertion, GREEN selector/exit/test counts and final C path list
record Spec/Plan SHA-256, strict validator PASS outputs and git diff --check result
state no Maven runtime/DB/listener/browser and list Step2-4 plus compensation/dead-letter work as remaining
```
- Verification contribution: completion evidence.
- After this file: Step evidence is honest and final for commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest,OrchestrationContractTest` followed by strict Spec/Plan validators and `git diff --check`.
- Expected result: exit 0; named C tests pass; both strict document validators print PASS; only the twelve Step 1 paths differ.
- RED command (Root only): `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest`; expected nonzero with named missing-contract assertion, no compile/fixture failure.
- GREEN command (Root only): `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest,OrchestrationContractTest`; expected exit 0 and named tests pass.
- Documentation commands: strict Spec/Plan validators at their skill absolute paths plus `git diff --check`; expected PASS/exit 0.
- Failure returns to: File 1 for RED/contract assertions; Files 2-9 for invariant failure; File 10/11 for strict validation; an offline missing-cache resolution error returns to §6.2 environment handling and is not RED or permission to change dependencies/settings/network.
- Completion criteria: all C values/methods/constants pass; ProcessClient/DTO golden unchanged; reviewed docs strict-pass; evidence records actual results.
- Rollback: revert only Step 1 paths; no data/schema/runtime effect.
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlContractTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessActiveWaitKindEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlActionEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlBlockReasonEnum.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessActiveWaitDTO.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlSnapshotDTO.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CancelProcessCommand.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/CancelProcessResult.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessControlClient.java`, `docs/egon/spec/2026-11-04-23-22-process-control-cancellation.md`, `docs/egon/plan/2026-11-05-00-25-process-control-cancellation-implementation.md`, `docs/egon/evidence/2026-11-05-process-control-contract.md`.
- Commit: `feat(contract): define bounded process control operations`.

### Step 2 — Add the strict listener-free HTTP capability and packaged contract

- Requirements: `REQ-501`, `REQ-502`, `REQ-504`, `REQ-513`, `REQ-515`.
- Dependencies: Step 1 committed.
- Baseline state: ProcessControlClient/types exist; HttpProcessClient does not implement it; no codec/OAS/tests.
- Observable outcome: CL performs exact control GET and once-only cancellation POST with strict request/result affinity and packaged OAS3.1, proven without a listener.
- End state: C+CL consumer side is complete. No PS route or V9 exists.
- Test-first gate: `Required` — reflection assertions prove HttpProcessClient lacks the interface/methods and resource assertion proves OAS absent; fake JDK HTTP receives no request until implementation. All RED failures are JUnit assertions.
- Manual Checks: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`.
- Literal Rules: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11`.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpProcessControlClientTest.java`

- Purpose: True RED and final listener-free transport/codec contract.
- Symbols: missing-interface reflection RED; final `control_uses_exact_get`, `cancel_posts_once`, strict invalid-response matrix, safe errors, timeout/credential/redaction checks.
- Repository evidence: private `HttpCaseClientTest.FakeHttpClient` pattern; `HttpProcessClientTest` proves general semantics but starts a forbidden listener.
- Dependencies and consumers: Step 1 C types; HttpProcessClient/codec; JUnit/JDK fake only.
- Why now: fixes client behavior before production changes.
- Contract/signature changes: final typed client cast to ProcessControlClient; exact routes/methods/body/no-store Accept/Auth behavior.
- Input/output and state mapping: Query -> path -> snapshot; Command -> path+JSON -> result; instance/command/result invariants checked.
- Error and edge behavior: one POST, no redirect/retry, invalid local command before credential, non-200 stable code allowlist, unknown/oversized/media/extra/missing/type/time/enums -> safe ProcessClientException; no cause/body/reason/token.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-TEST-001, MC-SCOPE-001; listener absence asserted.
- Literal rule enforcement: Rule 2, Rule 3, Rule 6, Rule 10, Rule 11 — exact immutable contract/Jackson/Instant and current CL package.
- Implementation pseudocode:

```java
@Test red_missing_capability_is_assertion() {
  assertTrue(ProcessControlClient.class.isAssignableFrom(HttpProcessClient.class)) // initially false
}
@Test cancel_posts_once_and_binds_result() {
  fake.enqueue(200, exactCancelJson)
  result = ((ProcessControlClient) client).cancel(command)
  assert request method/path/body/auth; assert fake.calls()==1; assert result fields
}
for each malformed snapshot/result/error: assert TIANJI_INVALID_RESPONSE or safe mapped code; assert zero listener/socket
```

- Verification contribution: Step 2 RED/GREEN TEST-513 and transport parts of TEST-501/502/504/515.
- After this file: initial reflection RED; final fake suite GREEN after Files 3-4.

#### File 2 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessControlOpenApiContractTest.java`

- Purpose: Pin the contract-first OAS resource and all local refs/operations/schemas/errors/security.
- Symbols: resource-missing RED then parse/required/property/enum/status/operationId/path checks.
- Repository evidence: Case packaged OAS contract tests/resources.
- Dependencies and consumers: File 5; Jackson test parser only.
- Why now: OAS is authority, not an afterthought.
- Contract/signature changes: exact two operations `getProcessControl` and `cancelProcessInstance`.
- Input/output and state mapping: OAS components correspond exactly to Step 1 records and full error pairs.
- Error and edge behavior: fail unresolved refs, extra route/field, wrong required/null/time/status/scope, any chronology constraint or `/cancel` drift.
- Standards impact: MC-JSON-001, MC-TIME-001, MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
resource = ProcessControlOpenApiContractTest.class.getResource("/META-INF/tianji/openapi/process-control-v1.json")
assertNotNull(resource, "process-control-v1.json must be packaged") // genuine RED while the approved resource is absent
parse final resource with JsonMapper; assert exactly GET /control and POST /cancellations with operationIds
resolve every local $ref and compare required fields/enums/status codes/READ+CANCEL security to Step1 records
assert finishedAt and decidedAt are independent date-time fields with no chronology schema extension
```
- Verification contribution: OAS drift gate/API-GATE coverage.
- After this file: RED until File 5 exists; final contract GREEN.

#### File 3 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessControlResponseCodec.java`

- Purpose: Strictly decode only approved control/result/error shapes.
- Symbols: `snapshot(byte[])`, `cancellation(byte[])`, safe error-code parser; bounded tree/depth/string/token settings consistent with current strict Case codec.
- Repository evidence: `CaseResponseCodec` strict Jackson3 implementation.
- Dependencies and consumers: Step 1 types; HttpProcessClient.
- Why now: transport methods need trusted decoded values.
- Contract/signature changes: package-private codec; no public wrapper.
- Input/output and state mapping: exact JSON members -> compact constructors; wait/action ordering and command affinity remain client checks.
- Error and edge behavior: duplicate/unknown/missing/null/wrong scalar/unknown enum/invalid Instant/extra data rejected; both time orderings accepted; raw body never retained.
- Standards impact: MC-NAME-001, MC-REUSE-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-UTIL-001; current Jackson only.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
snapshot(bytes): strictTree(exact keys process,activeWaits,allowedActions,cancellationBlockReason,version,observedAt)
parse nested ProcessInstanceDTO, each wait DTO, enums and Instant strings; construct ProcessControlSnapshotDTO
cancellation(bytes): require exact eight result keys, construct CancelProcessResult without comparing its two Instants
catch Jackson/constructor failures and return ProcessClientException(TIANJI_INVALID_RESPONSE) without retaining body/cause
```
- Verification contribution: malformed response matrix.
- After this file: codec compiles; client not yet wired.

#### File 4 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`

- Purpose: Implement ProcessControlClient with exact GET/POST and once-only behavior.
- Symbols: implements list; `control`, `cancel`; codec field; control error allowlist/affinity checks.
- Repository evidence: current start/get/Case/worker call helpers, token redaction, no redirect and timeout behavior.
- Dependencies and consumers: Files 1/3; existing constructor/general call method.
- Why now: codec and tests define behavior.
- Contract/signature changes: additive implemented interface/methods; constructor/ProcessClient methods unchanged.
- Input/output and state mapping: control path segment -> strict snapshot matching instance; cancel command serialized once -> strict result matching instance+command+version/result invariants.
- Error and edge behavior: validate before token, no POST retry, preserve 400/401/403/404/409/413/500/503 codes safely; two new 409 codes recognized; 2MiB existing non-Case response cap retained unless current helper is narrower; no new transport policy.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001; no dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
snapshot = call(GET, "process-instances/"+segment(id)+"/control", null, codec::snapshot)
require snapshot.process.instanceId == query.instanceId
result = call(POST, "process-instances/"+segment(command.instanceId)+"/cancellations", command, codec::cancellation)
require result.instanceId/commandId == command; never compare finishedAt and decidedAt
```

- Verification contribution: makes File 1 GREEN; old methods regression remains.
- After this file: Java SDK capability works against fake transport; OAS still RED until File 5.

#### File 5 — `CREATE stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-control-v1.json`

- Purpose: Publish contract-first OpenAPI 3.1 for exactly two unreleased operations.
- Symbols: paths, operationIds, bearer/OAuth scopes, request/response/error components.
- Repository evidence: `case-v1.json` packaging/style.
- Dependencies and consumers: Step 1 fields, ProcessControlOpenApiContractTest, future server/owner UI.
- Why now: runtime client shape is complete; freeze docs before provider.
- Contract/signature changes: additive resource only; no springdoc/runtime endpoint.
- Input/output and state mapping: full required schemas, UTC Instant formats, enums, no chronology relation, exact status/code pairs.
- Error and edge behavior: no `/cancel`, admin scope, optional command fields, default response, GraphQL or UI promise.
- Standards impact: MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
openapi=3.1.0; define bearer/OAuth security and schemas matching every Step1 record component
GET control has path instanceId, 200 snapshot and standard safe errors; POST cancellations has exact four-field body and 200 result
enumerate both cancellation 409 codes separately; require READ+CANCEL; use synthetic values only
link all schemas through local $ref values verified by ProcessControlOpenApiContractTest
```
- Verification contribution: File 2 GREEN/static publication.
- After this file: consumer contract/transport complete.

#### File 6 — `CREATE docs/egon/evidence/2026-11-05-process-control-http-client.md`

- Purpose: Record actual Step 2 RED/GREEN/static OAS/diff evidence and listener exclusion.
- Symbols: selectors/counts/hash/resource checks/no socket/no runtime.
- Repository evidence: per-Step evidence convention.
- Dependencies and consumers: Files 1-5 actual outputs.
- Why now: closes commit honestly.
- Contract/signature changes: none.
- Input/output and state mapping: actual results only.
- Error and edge behavior: distinguish assertion/compile/dependency failures; never claim provider/runtime.
- Standards impact: MC-TEST-001, MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11 evidence.
- Implementation pseudocode:

```text
record the reflection/resource RED failures and final fake-client/OAS GREEN command with actual counts
record process-control OAS SHA-256, exact request methods/paths and one-send/redaction assertions
list selected tests and explicitly prove HttpProcessClientTest, listeners, sockets, DB and provider runtime were absent
```
- Verification contribution: completion evidence.
- After this file: Step 2 evidence final.

- Validation working directory: repository root.
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,HttpCaseClientTest,CaseResponseCodecTest` followed by `git diff --check`.
- Expected result: exit 0; all named fake/static tests pass; no `HttpProcessClientTest`, listener, network or server is invoked; only six Step 2 paths differ.
- RED command: Root runs `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=HttpProcessControlClientTest,ProcessControlOpenApiContractTest`; expected nonzero only from named missing capability/resource assertions.
- GREEN command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,HttpCaseClientTest,CaseResponseCodecTest`; expected exit 0. `HttpProcessClientTest` is absent by design.
- Failure returns to: tests for contract mismatch; codec for strict parsing; client for path/one-send; OAS for drift; an offline missing-cache resolution error returns to §6.2 and is not a product failure or repository-change instruction.
- Completion criteria: fake-only transport and OAS GREEN; old C/Case regression GREEN; no listener/network/runtime.
- Rollback: revert Step 2 paths only.
- Commit paths: `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpProcessControlClientTest.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessControlOpenApiContractTest.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessControlResponseCodec.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`, `stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-control-v1.json`, `docs/egon/evidence/2026-11-05-process-control-http-client.md`.
- Commit: `feat(client): add strict process control HTTP capability`.

### Step 3 — Freeze the sole V9 and deliver storage-only cancellation primitives

- Requirements: `REQ-504`, `REQ-505`, `REQ-507`, `REQ-508`, `REQ-510`, `REQ-511`, `REQ-515`.
- Dependencies: Step 2 committed; V8 still latest/no V9; all V1-V8 hashes match baseline.
- Baseline state: generic receipt lacks PROCESS_CANCEL/start lock; no detail store/table; Agent store lacks bounded scope discovery/exact-key closure; verifier rejects V8+V9; no API route/admission.
- Observable outcome: one complete immutable V9 and all storage/verifier primitives exist and pass runtime-free tests, while no Spring Bean/controller/feature flag exposes cancellation.
- End state: Step 4 can consume final schema/store contracts. V9 bytes are frozen forever after this commit; no later Step may edit/add cancellation DDL.
- Test-first gate: `Required` — resource/reflection/mock assertions fail because V9/store/method/verifyCancellation and V8+V9 acceptance are absent; no test connects to PostgreSQL.
- Manual Checks: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`.
- Literal Rules: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 9`, `Rule 10`, `Rule 11`.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessCancellationSqlTest.java`

- Purpose: True RED then pin complete final V9 bytes/DDL semantics and V1-V8 immutability.
- Symbols: resource-missing RED; final SHA/CRC, table/columns/checks/FKs/index/privilege expectations and old SQL hash matrix.
- Repository evidence: existing static SQL/verifier tests and V8 resource pinning.
- Dependencies and consumers: future V9 resource; no JDBC connection.
- Why now: migration authority precedes implementation.
- Contract/signature changes: exact V9 filename only.
- Input/output and state mapping: SQL bytes -> one table, three FKs, accepted partial UK, dispatch discovery index; no chronology check, old ALTER or grant hardcoding.
- Error and edge behavior: fail extra/missing DDL, V1-V8 hash drift, broad dispatch update SQL, V9 mutation after freeze.
- Standards impact: MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; technical table no soft delete/MP.
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11 — exact native types/time/no generator/profile change.
- Implementation pseudocode:

```text
resource = ProcessCancellationSqlTest.class.getResource("/db/tianji-process/V9__add_process_cancellation_receipts.sql")
assertNotNull(resource, "V9 cancellation migration must be packaged") // genuine RED while V9 is absent
read final V9 bytes; assert one CREATE TABLE, three named FKs, accepted partial unique index and dispatch discovery index
assert every required column/check from Spec §11 exists and no chronology CHECK, GRANT, ACT write or old-table ALTER exists
hash V1-V8 and compare exact baseline SHA values; compute and record final V9 SHA/CRC
```
- Verification contribution: TEST-514/static migration gate.
- After this file: assertion RED until File 6; final pins immutable bytes.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`

- Purpose: Define exact V7/V8/V8+V9/unknown history and cancellation catalog/role behavior.
- Symbols: reflection RED for `verifyCancellation`; mocked JDBC/history/catalog tests.
- Repository evidence: ProcessApiSchemaVerifier and CaseStorageConfiguration mocked verifier patterns.
- Dependencies and consumers: Files 6/10; Mockito/JdbcTemplate mocks only.
- Why now: prevents V9 from breaking existing Case/task/readiness consumers.
- Contract/signature changes: public verifier method plus unchanged `hasCaseMigration()` boolean semantics.
- Input/output and state mapping: history rows/scripts/checksums -> false/exact true/fail; V9 catalog/privileges -> ready/fail.
- Error and edge behavior: failed/duplicate/missing checksum/unknown future/version without V8/wrong role/index/trigger/RLS fail closed.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> ProcessApiSchemaVerifier.class.getDeclaredMethod("verifyCancellation")) // genuine RED while method is absent
drive mocked history rows for none-after-V7, exact V8, exact V8+V9, V9-only, failed duplicate and unknown future
assert hasCaseMigration returns false/true only for allowed sets and throws for every drift set
mock cancellation catalog/role rows: provenance SELECT with no Case INSERT/inbox UPDATE must pass; missing provenance SELECT or structural drift fails; standard V8 S/I role also passes
spy that verifyCancellation never calls full verifyCase; existing verifyCase still requires its full three-table Case activation grants
assert exact V9 audit table/index/check/FK plus mandatory SELECT+INSERT/no UPDATE-DELETE and existing dispatch S/U/index checks
```
- Verification contribution: TEST-514/REQ-511.
- After this file: RED until verifier/V9; final compatibility matrix GREEN.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCancellationReceiptStoreTest.java`

- Purpose: Define immutable detailed audit/replay mapping without a database.
- Symbols: class/method reflection RED then mocked DataSource/JdbcTemplate behavior for insert/read/affinity/invariants.
- Repository evidence: CaseStartProvenanceStoreTest/CasePlanItemReceiptStoreTest plain-JDBC mock style.
- Dependencies and consumers: Step 1 C types, Files 6-8/10.
- Why now: store contract before production.
- Contract/signature changes: constructor/source/schema, nested immutable Entry/Record, lock/insert/usesDataSource.
- Input/output and state mapping: exact actor/START/release/model/request/reason/result/time/closure fields <-> 26 V9 columns.
- Error and edge behavior: APPLIED generic missing/detail missing/hash/source/resource mismatch, chronology-independent times, accepted/status/closure mismatch and ambiguous rows fail; no UPDATE/DELETE.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-TEST-001; no converter/Bean.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.execution.ProcessCancellationReceiptStore")) // genuine RED while class is absent
mock one joined result row with generic PROCESS_CANCEL, exact START and release definition authority; read immutable Entry
verify insert binds all 26 columns once and uses no UPDATE/DELETE/upsert SQL
test malformed/ambiguous/mismatched rows and both finishedAt/decidedAt orders; assert reason absent from toString
```
- Verification contribution: TEST-507/508/511/514.
- After this file: RED until File 8; final store mapping GREEN.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStoreTest.java`

- Purpose: Define bounded validated full-key CLOSING primitive and phantom safety.
- Symbols: reflection RED for nested ProcessClosureScope/method; final zero/mixed/foreign/257/phantom/count-mismatch/repeat/rollback tests.
- Repository evidence: current AgentDispatchStore fixture/state/SQL mock tests.
- Dependencies and consumers: File 9; existing Key/Seed/fingerprint semantics.
- Why now: fixes exact safe write contract before store change.
- Contract/signature changes: internal scope record and method only.
- Input/output and state mapping: trusted owner process facts + candidate rows -> validated key list -> exact VALUES batch -> scheduled boolean/count.
- Error and edge behavior: row 257, foreign Seed/source/release/model/business/correlation, affected count mismatch fail; phantom inserted after SELECT untouched; no scope-only UPDATE.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 9, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> AgentDispatchStore.class.getDeclaredMethod("scheduleProcessClosure", Class.forName("top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore$ProcessClosureScope"))) // genuine RED while symbols are absent
return candidate rows for zero, mixed, foreign, 257, phantom-after-select and count-mismatch cases
capture candidate SELECT predicate tenant+BPMN+scope and exact VALUES UPDATE keys tenant+issuer+subject+source+BPMN+instance+occurrence
assert only validated keys change to CLOSING, affected count equals key count, and every invalid/overflow case rolls back
```
- Verification contribution: TEST-512.
- After this file: RED until File 9; final primitive GREEN.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java`

- Purpose: Prove unchanged HumanTask verifier selects V8 contract under exact V8+V9 history.
- Symbols: add V8+V9 case-aware scenario; retain V7/V8 corruption checks.
- Repository evidence: current construction-mocked ProcessApiSchemaVerifier boolean branch.
- Dependencies and consumers: File 10; HumanTaskStorageVerifier main unchanged.
- Why now: compatibility proof before verifier change.
- Contract/signature changes: test only.
- Input/output and state mapping: `hasCaseMigration=true` under V8+V9 -> existing V8 task checks.
- Error and edge behavior: unknown history/corrupt V8 still fail; no new PROCESS_CANCEL human-task source type.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 11.
- Implementation pseudocode:

```text
configure exact V8 and exact V8+V9 history fixtures and assertDoesNotThrow(() -> HumanTaskStorageVerifier.verify()) for both; old exact-version handling makes V8+V9 assertion RED
assert contractV8 retains the three exact V8 authority checks after verification
run existing V7/V8 corruption rows and assert they still fail; assert PROCESS_CANCEL never enters start_command_type checks
```
- Verification contribution: TEST-514 compatibility.
- After this file: RED with old exact-history verifier fixture; GREEN after File 10.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V9__add_process_cancellation_receipts.sql`

- Purpose: Freeze complete sole V9 table/constraints/FKs/indexes with no backfill/old migration edit.
- Symbols: `tianji_process_cancel_receipt`, PK, three FKs, checks, accepted partial UK, `tianji_agent_dispatch_scope_open_idx`.
- Repository evidence: V1-V8 PostgreSQL/Flyway naming, constraints/collation/timestamptz(6), formal Case Spec.
- Dependencies and consumers: Files 1/8-10; external provisioning later; Step 4 readiness only.
- Why now: RED DDL authority is fixed; all later storage code consumes final bytes.
- Contract/signature changes: exactly one new version; no grant statements/ACT DDL/old ALTER/HumanTask widening.
- Input/output and state mapping: 26 detailed fields, nonnull independent result/decision instants, PROCESS_CANCEL/START/BPMN constants, accepted/state/closure checks; discovery-only Agent index.
- Error and edge behavior: at most one accepted per tenant/process; multiple no-op commands; no chronology check; runtime role later verified S/I only on new table.
- Standards impact: MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001; approved technical Flyway exception.
- Literal rule enforcement: Rule 2, Rule 3, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```sql
CREATE TABLE tianji_process_cancel_receipt (
  tenant_id bigint NOT NULL, subject_type varchar(16) NOT NULL, subject_issuer varchar(64) COLLATE "C" NOT NULL, subject_id varchar(256) COLLATE "C" NOT NULL,
  command_type varchar(32) COLLATE "C" NOT NULL, command_id varchar(128) COLLATE "C" NOT NULL, request_sha256 char(64) COLLATE "C" NOT NULL,
  process_instance_id varchar(255) COLLATE "C" NOT NULL, start_command_type varchar(32) COLLATE "C" NOT NULL, start_command_id varchar(128) COLLATE "C" NOT NULL,
  start_request_sha256 char(64) COLLATE "C" NOT NULL, release_key varchar(128) COLLATE "C" NOT NULL, model_type varchar(8) COLLATE "C" NOT NULL,
  definition_key varchar(128) COLLATE "C" NOT NULL, engine_definition_id varchar(255) COLLATE "C" NOT NULL, definition_sha256 char(64) COLLATE "C" NOT NULL,
  business_key varchar(255) COLLATE "C" NOT NULL, correlation_id varchar(128) COLLATE "C" NOT NULL, expected_version char(64) COLLATE "C" NOT NULL,
  reason varchar(512) COLLATE "C" NOT NULL, accepted boolean NOT NULL, result_status varchar(16) COLLATE "C" NOT NULL, result_version char(64) COLLATE "C" NOT NULL,
  result_finished_at timestamptz(6) NOT NULL, decided_at timestamptz(6) NOT NULL, agent_closure_scheduled boolean NOT NULL,
  PRIMARY KEY(tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id),
  FOREIGN KEY(tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id) REFERENCES tianji_process_command_receipt(tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id),
  FOREIGN KEY(tenant_id,subject_type,subject_issuer,subject_id,start_command_type,start_command_id) REFERENCES tianji_process_command_receipt(tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id),
  FOREIGN KEY(tenant_id,release_key,model_type,definition_key) REFERENCES tianji_process_release_definition(tenant_id,release_key,model_type,definition_key),
  CHECK(tenant_id>0 AND subject_type IN ('USER','SERVICE') AND subject_issuer ~ '^[0-9a-f]{64}$'),
  CHECK(subject_id<>'' AND subject_id=btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'),
  CHECK(process_instance_id<>'' AND process_instance_id=btrim(process_instance_id) AND process_instance_id !~ '[[:cntrl:]]'),
  CHECK(engine_definition_id<>'' AND engine_definition_id=btrim(engine_definition_id) AND engine_definition_id !~ '[[:cntrl:]]'),
  CHECK(business_key<>'' AND business_key=btrim(business_key) AND business_key !~ '[[:cntrl:]]'),
  CHECK(command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND request_sha256 ~ '^[0-9a-f]{64}$' AND start_request_sha256 ~ '^[0-9a-f]{64}$'),
  CHECK(start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'),
  CHECK(definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$' AND correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
  CHECK(definition_sha256 ~ '^[0-9a-f]{64}$' AND expected_version ~ '^[0-9a-f]{64}$' AND result_version ~ '^[0-9a-f]{64}$'),
  CHECK(reason<>'' AND reason=btrim(reason) AND reason !~ '[[:cntrl:]]' AND octet_length(reason)<=2048),
  CHECK(command_type='PROCESS_CANCEL' AND start_command_type='START' AND model_type='BPMN'),
  CHECK(result_status IN ('COMPLETED','CANCELLED') AND (NOT accepted OR result_status='CANCELLED')),
  CHECK(NOT agent_closure_scheduled OR accepted)); -- deliberately no result_finished_at/decided_at ordering CHECK
CREATE UNIQUE INDEX tianji_process_cancel_accepted_idx ON tianji_process_cancel_receipt(tenant_id,process_instance_id) WHERE accepted;
CREATE INDEX tianji_agent_dispatch_scope_open_idx
  ON tianji_process_agent_dispatch(tenant_id,scope_type,scope_id,occurrence_id)
  WHERE state <> 'CLOSED';
```

- Verification contribution: File 1 static GREEN and verifier resource authority.
- After this file: final V9 bytes exist; they must never change after Step 3 commit.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`

- Purpose: Add generic cancellation kind and exact owner START serialization/read contract.
- Symbols: `Kind.PROCESS_CANCEL`; `lockStartByInstance(ProcessActor,String)` returning exact Receipt/TaskOrigin-equivalent source facts.
- Repository evidence: current insert/lock/startByInstance/taskOrigin and generic non-DECISION apply.
- Dependencies and consumers: Files 8 and Step 4 service.
- Why now: V9 exists; detail/store need generic source/idempotency authority.
- Contract/signature changes: additive enum/method only; start/get callers unchanged.
- Input/output and state mapping: actor+instance -> APPLIED START exact tuple/request/release/correlation/createdAt under FOR UPDATE; cancel PENDING/APPLIED uses null decision JSON.
- Error and edge behavior: none/multiple/non-APPLIED -> hidden/not-found or invariant; no source actor borrowing.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001; no new dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 11.
- Implementation pseudocode:

```text
add Kind.PROCESS_CANCEL without changing decision_result handling or existing Kind values
lockStartByInstance(actor,instanceId): SELECT exact actor tuple, command_type START, state APPLIED, resource_id instance FOR UPDATE
map request/release/correlation/resource/createdAt into the existing immutable source record and require exactly one row
retain startByInstance read behavior and every existing caller unchanged
```
- Verification contribution: store/service tests.
- After this file: generic authority available; no API/wiring.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCancellationReceiptStore.java`

- Purpose: Own V9 immutable insert/read/replay and exact schema affinity.
- Symbols: constructor, nested `Entry`/`Record`, `lock/read`, `insert`, `usesDataSource`, safe `toString`.
- Repository evidence: plain final Case receipt/provenance stores.
- Dependencies and consumers: V9, File 7, ProcessApiSchemaVerifier; Step 4 service.
- Why now: generic source exists and RED mapping is fixed.
- Contract/signature changes: internal store only.
- Input/output and state mapping: exact Spec columns; use Timestamp/Instant conversion preserving actual values; validate generic/detail/source/release/engine/hash/business/correlation.
- Error and edge behavior: one row exactly; no UPDATE/DELETE/upsert; malformed/ambiguous/incomplete APPLIED -> unavailable; reason never toString/log.
- Standards impact: MC-NAME-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-TIME-001; plain constructor/profile exception.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
constructor verifies DataSource/schema and cancellation schema readiness; every method requires the owning transaction resource
lock(actor,commandId) joins detail to APPLIED generic cancel, exact owner START and BPMN release definition, then validates every hash/resource field
insert(Record) writes all 26 final values once and requires affectedRows==1; expose no update/delete method
map Timestamp values independently to finishedAt/decidedAt Instants and never compare, clamp or synthesize them
```
- Verification contribution: File 3 GREEN/Step 4 replay.
- After this file: immutable detail persistence ready but unwired.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java`

- Purpose: Add bounded discovery/validation and exact-full-key closure scheduling.
- Symbols: nested `ProcessClosureScope`; `scheduleProcessClosure`.
- Repository evidence: current Seed/read fingerprint, full where key, transaction guard and CLOSING semantics.
- Dependencies and consumers: V9 index; Step 4 service; existing poller unchanged.
- Why now: V9 index final and tests RED.
- Contract/signature changes: internal additive method/record only.
- Input/output and state mapping: scope -> SELECT up to 257 FOR UPDATE -> read/validate candidates -> exact VALUES full-key batch -> affected count; return scheduled.
- Error and edge behavior: overflow/foreign/phantom/count mismatch rolls back; phantom not in validated values untouched; native deletion reasoning never authorizes broad UPDATE.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; no remote I/O.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 11.
- Implementation pseudocode:

```text
scheduleProcessClosure(scope) requires current transaction and SELECTs non-CLOSED tenant+BPMN+scope rows FOR UPDATE LIMIT 257
read each row through existing Seed fingerprint/idempotency validation and compare release, definitionId, modelSHA, businessKey and correlationId
reject row 257 or any mismatch; build immutable ProcessClosureKey(tenant,issuer,subject,sourceId,BPMN,instance,occurrence)
UPDATE through VALUES containing only those full keys, set CLOSING/DB now, and require affectedRows==validatedKeys.size
```
- Verification contribution: File 4 GREEN.
- After this file: closure primitive ready, existing refresh/due/poller unchanged.

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`

- Purpose: Pin V9 and make all exact history consumers V8+V9 compatible.
- Symbols: V9 file/SHA constants, `verifyCancellation`, version-history parser; unchanged boolean `hasCaseMigration` contract.
- Repository evidence: current V1-V8 checksum/catalog/role descriptors and callers.
- Dependencies and consumers: V9; ProcessCancellationReceiptStore/Step 4 configuration; Case/HumanTask/release/worker/Agent existing callers.
- Why now: final migration bytes known.
- Contract/signature changes: additive verify method; hasCaseMigration accepts no >V7, exact V8, exact V8+V9 only; unknown fails.
- Input/output and state mapping: packaged bytes/history/catalog/role -> readiness; compute exact SHA after File 6 final bytes.
- Error and edge behavior: V9-only, failed/duplicate/unknown/CRC/SHA/catalog/privilege/trigger/RLS drift fail; V8 checks unchanged.
- Standards impact: MC-REUSE-001, MC-DEP-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001; no schema install/repair.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```text
load successful SQL history versions after 7 in order; allow empty, exact V8, or exact V8 followed by exact V9 only
verify packaged script name/checksum/SHA for every present version and preserve hasCaseMigration boolean meaning
verifyCancellation calls base verification and exact V8/V9 history, then private verifyCancellationProvenanceRead; never call full verifyCase or construct CaseStartProvenanceStore
reuse caseContracts(caseMigration()).get("tianji_case_start_provenance") and existing verifyCaseColumns/Checks/Keys/Indexes plus release model-authority checks; require provenance SELECT/schema USAGE and existing immutable/nonowner/no-RLS/no-trigger guards, permit optional INSERT, reject UPDATE/DELETE/TRUNCATE/TRIGGER/REFERENCES
do not inspect or require plan-item receipt or completion-inbox grants; leave full verifyCase activation semantics unchanged
verify exact V9 audit table columns/checks/PK/FKs/indexes and mandatory SELECT+INSERT/no UPDATE-DELETE; verify existing Agent dispatch SELECT+UPDATE/index separately
```
- Verification contribution: Files 1/2/5 GREEN; readiness for Step 4.
- After this file: storage/verifier foundation complete, still no API admission.

#### File 11 — `CREATE docs/egon/evidence/2026-11-05-process-cancellation-storage.md`

- Purpose: Record actual V9 bytes/SHA/CRC, old SQL hashes, RED/GREEN selectors and runtime-unverified limits.
- Symbols: final migration identity, verifier matrix, no API/Bean, no PG.
- Repository evidence: evidence convention and immutable migration rule.
- Dependencies and consumers: all Step 3 actual outputs.
- Why now: freezes DDL/commit evidence.
- Contract/signature changes: none.
- Input/output and state mapping: actual hashes/counts only.
- Error and edge behavior: no claim of applied migration/catalog/atomicity/concurrency.
- Standards impact: MC-TEST-001, MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11 evidence.
- Implementation pseudocode:

```text
record each RED assertion and GREEN selector/count with exit codes and dependency-resolution notes
record SHA-256 for all 24 old SQL and final V9 plus verifier resource constants and exact Step path diff
state no Controller/Bean/config/API, no PostgreSQL/Flyway execution and native transaction/concurrency RuntimeUnverified
```
- Verification contribution: Step completion/immutability evidence.
- After this file: Step 3 ready for exact commit.

- Validation working directory: repository root.
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,CaseStorageConfigurationTest,CaseStartProvenanceStoreTest` followed by all old-SQL SHA checks and `git diff --check`.
- Expected result: exit 0; named static/mock tests pass; exactly one new V9 exists, all V1-V8 hashes match, no API/config path differs, and no PG test runs.
- RED command: Root runs `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest`; expected named assertion failures only.
- GREEN command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,CaseStorageConfigurationTest,CaseStartProvenanceStoreTest`; expected exit 0, no PG class selected.
- Static gate: hash all 24 old SQL before/after, verify only new V9, `git diff --check`, search no Controller/permission/config change in Step paths.
- Failure returns to: File 6 for DDL descriptor; File 7/8 for source/replay; File 9 for dispatch; File 10 for history/catalog; an offline missing-cache resolution error returns to §6.2 without network/global-settings/dependency changes.
- Completion criteria: V9 final complete/immutable, all storage tests GREEN, no API admission/runtime claim.
- Rollback: before commit revert Step 3 paths; after V9 is externally applied, source rollback follows Spec forward-fix/feature-off boundary and never edits V9.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessCancellationSqlTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCancellationReceiptStoreTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStoreTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/task/HumanTaskStorageVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V9__add_process_cancellation_receipts.sql`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCancellationReceiptStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/bridge/AgentDispatchStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`, `docs/egon/evidence/2026-11-05-process-cancellation-storage.md`.
- Commit: `feat(process): add cancellation audit storage foundation`.

### Step 4 — Expose the default-off useful control and bounded native cancellation path

- Requirements: `REQ-502`, `REQ-503`, `REQ-506`, `REQ-507`, `REQ-509`, `REQ-512`, `REQ-513`, `REQ-514`, `REQ-515`; predecessor compatibility `REQ-401`, `REQ-402`, `REQ-403`, `REQ-404`, `REQ-405`, `REQ-406`, `REQ-407`, `REQ-408`, `REQ-409`, `REQ-410`, `REQ-411`, `REQ-412`, `REQ-413`, `REQ-414`, `REQ-415`, `REQ-416`.
- Dependencies: Steps 1-3 committed; final V9 bytes/store/verifier fixed; Root execution gate.
- Baseline state: consumer contract/transport and storage primitives exist, but no PS control/native service/errors/routes/permission/config. Feature cannot be admitted.
- Observable outcome: when explicitly enabled over exact V9, current exact owner can read useful control and issue root-fenced cancellation with fully atomic audit/validated Agent closure; off remains absent/fail-closed.
- End state: source-complete first T3-10 slice and final evidence. Compensation/dead-letter/manual recovery and all runtime acceptance remain Required/Unverified.
- Test-first gate: `Required` — reflection/context/string-key assertions fail for absent model metadata/native/service/controller/beans/resources/config, not compilation; final files convert/extend to typed unit/MockMvc tests.
- Manual Checks: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001`.
- Literal Rules: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11`.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`

- Purpose: Define immutable timer-wait metadata without changing model admission.
- Symbols: reflection RED for `timerWaits`; final standalone/event-gateway/boundary timer metadata and old invalid-model matrix.
- Repository evidence: current validator tests and parsed timer graph fixtures.
- Dependencies and consumers: Files 9-10/11.
- Why now: active-wait assembler needs released authority first.
- Contract/signature changes: internal getter only.
- Input/output and state mapping: existing parsed timer activity ID/duration -> immutable map; no new accepted XML/limit.
- Error and edge behavior: duplicate/unsupported model remains rejected; mutation impossible.
- Standards impact: MC-MODEL-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 3, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> ValidatedProcessModel.class.getDeclaredMethod("timerWaits")) // genuine RED while method is absent
validate baseline standalone timer, Agent event-gateway timer, Case event-gateway timer and human-task boundary timer fixtures
assert timer activity ID -> exact Duration map is immutable and existing worker/message/case/human maps are unchanged
rerun every current invalid XML/semantic case to prove no production model admission expansion
```
- Verification contribution: TEST-503/504/model compatibility.
- After this file: RED until Files 9-10.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java`

- Purpose: Define repeatable read facts, unique actual wait occurrence assembly, root fence and history verification.
- Symbols: reflection RED; final observe/lock/history tests, 257 overflow, artifact dedup, zero-artifact live state, valid inactive parallel join, unknown artifact mapping, no child locks/direct ACT writes.
- Repository evidence: CaseNativeAccess/HumanTaskNativeAccess/Flowable wait inspector tests.
- Dependencies and consumers: Files 9-11; Flowable/JDBC mocks.
- Why now: native boundary behavior before implementation.
- Contract/signature changes: internal nested facts/occurrence keys only.
- Input/output and state mapping: live task/external-job/timer-Job/message-EventSubscription artifacts mapped by unique actual execution+activity occurrence, then released kind; same artifact-backed occurrence seen through multiple supporting artifacts counted once; executions are affinity context only.
- Error and edge behavior: incompatible source facts, unknown/closed released kind, 257th actual occurrence, root affinity/history/delete reason mismatch -> unavailable; no runtime cardinality claim.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-PATTERN-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess")) // genuine RED while class is absent
tasks = taskService.createTaskQuery().taskTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // no active filter
externalJobs = managementService.createExternalWorkerJobQuery().jobTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257)
timers = managementService.createTimerJobQuery().timers().jobTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // List<Job>
messages = runtimeService.createEventSubscriptionQuery().eventType("message").tenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // List<EventSubscription>
derive OccurrenceKey(executionId,activityId) only from Task.executionId/taskDefinitionKey, Job.executionId/elementId and EventSubscription.executionId/activityId
collect at most 256 distinct artifact execution IDs; if empty return no wait summaries without issuing IN () SQL
read one quoted-schema JdbcTemplate SELECT id_,act_id_,proc_inst_id_,proc_def_id_,tenant_id_ FROM act_ru_execution WHERE tenant_id_=? AND proc_inst_id_=? AND proc_def_id_=? AND id_ IN (one bound placeholder per artifact ID), without FOR UPDATE
require exactly one matching-affinity execution row per ID and exact activity binding; structural/root/scope/inactive parallel-join rows without artifacts never create waits
merge supporting artifact IDs for each key once; conflicting or unknown artifact-backed kinds/missing execution/257th unique key -> unavailable
classify only verified artifact-backed keys through released human/worker/message/case/timer metadata and group sorted public summaries
assert valid parallel fork/join with one task + unrelated inactive join yields one HUMAN_TASK; zero-artifact live instance yields empty summaries; wrong/missing artifact affinity fails
lock root with SELECT id_,rev_,proc_def_id_,business_key_,suspension_state_ FROM act_ru_execution WHERE id_=:instance AND proc_inst_id_=:instance AND tenant_id_=:tenant AND parent_id_ IS NULL FOR UPDATE
verify history status/end/deleteReason without synthesizing time
```

- Verification contribution: TEST-503/504/508/509.
- After this file: RED until File 11; final native boundary GREEN under mocks only.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlServiceTest.java`

- Purpose: Define useful Query, exact transaction order, replay/no-op/live cancel, rollback and SQLSTATE classification.
- Symbols: reflection RED; final control/cancel tests covering TEST-503-512.
- Repository evidence: ProcessExecutionServiceTest/FlowableCaseServiceTest transaction and mock patterns.
- Dependencies and consumers: Files 11-12, Step 3 stores; no Spring context/DB.
- Why now: central Command pattern behavior fixed before service.
- Contract/signature changes: `control(ProcessActor,GetProcessInstanceQuery)`, `cancel(ProcessActor,CancelProcessCommand)`.
- Input/output and state mapping: exact START/release/native -> snapshot/version; cancel -> generic/V9/history/dispatch/result.
- Error and edge behavior: auth is precondition from controller; replay still exact owner; terminal before live version; Case block; all clock exclusion/no chronology; race exceptions escape write TX then exact outer mapping; reason never logs.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-PATTERN-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.execution.ProcessControlService")) // genuine RED while class is absent
control: own REPEATABLE_READ -> exact START -> release/model -> native.observe -> deterministic snapshot/version
cancel: exact current actor already provided -> own write TX
  lock exact START -> lock/replay generic PROCESS_CANCEL -> if replay validate V9 and return
  root=lockRoot; if absent verify terminal -> V9 no-op -> generic APPLIED
  else compare clock-free version -> reject caseSources/provenance mismatch -> reserve
       Flowable delete -> verify exact history -> dispatch exact-key schedule
       insert V9 -> generic APPLIED -> commit
outside execute: classify Flowable marker or SQLSTATE 40001/40P01/55P03 as version conflict;
57014/08xxx/unclassified as unavailable
```

- Verification contribution: core Step GREEN/rollback/call-order evidence.
- After this file: RED until service/native/errors; final application behavior GREEN.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlControllerTest.java`

- Purpose: Define both REST operations, current-auth-before-service, strict body and safe errors without listener.
- Symbols: reflection RED; final standalone MockMvc GET/POST matrices.
- Repository evidence: ProcessControllerTest/CaseControllerTest standalone MockMvc/advice.
- Dependencies and consumers: Files 13-17; C types.
- Why now: external provider contract before implementation.
- Contract/signature changes: exact `/control` GET and plural `/cancellations` POST.
- Input/output and state mapping: path/body/security actor -> service -> direct DTO/result/no-store JSON.
- Error and edge behavior: path mismatch/unknown/duplicate/null/4KiB, current auth invoked on replay, status/code pairs, both time orders, no private cause.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.api.ProcessControlController")) // genuine RED while class is absent
build standalone MockMvc with mocked TianjiProcessAccess/ProcessControlService and ProcessApiErrors/RequestBodyAdvice
GET control: verify access.require(CONTROL_API) precedes service.control and assert exact JSON/no-store
POST cancellations: verify path/body equality, access.require(CANCEL_API) before service.cancel, strict body/status/error matrices and both time orders
```
- Verification contribution: TEST-504/505/513/515.
- After this file: RED until API/error/advice; final route contract GREEN.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`

- Purpose: Prove default-off bean/route absence and exact-V9 conditional admission.
- Symbols: string-name/context RED for `processControlService`/controller; final off/on/mock verifier matrix.
- Repository evidence: current capability configuration tests.
- Dependencies and consumers: Files 18-20/21.
- Why now: wiring behavior fixed before configuration.
- Contract/signature changes: test only.
- Input/output and state mapping: property/API/servlet/verifier -> beans or fail closed.
- Error and edge behavior: off no beans; on without V9/API/servlet fails; no DDL install.
- Standards impact: MC-BEAN-001, MC-CONFIG-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11.
- Implementation pseudocode:

```text
with cancellation=true and all mocked prerequisites assertTrue(context.containsBean("processControlService")) // genuine RED while capability wiring is absent
with cancellation flag absent/false assert no processControlNativeAccess/processCancellationReceiptStore/processControlService/controller Beans
with flag true and mocked exact V9 verifier assert all named collaborators bind to the same DataSourceTransactionManager/schema
with missing API, servlet, history or V9 readiness assert context startup fails closed and creates no partial control Bean
```
- Verification contribution: TEST-515.
- After this file: RED until configuration files.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`

- Purpose: Pin protected wildcard/current access and explicit cancellation Bean dependencies without changing old auth paths.
- Symbols: new property matrix/Bean names; existing endpoint policy assertions.
- Repository evidence: current security configuration tests.
- Dependencies and consumers: File 19.
- Why now: security wiring before implementation.
- Contract/signature changes: test only.
- Input/output and state mapping: enabled property + current guards -> named service/native/store collaborators.
- Error and edge behavior: no anonymous/admin bypass; existing endpoints unchanged.
- Standards impact: MC-ARCH-001, MC-BEAN-001, MC-CONFIG-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11.
- Implementation pseudocode:

```text
cancelConfig = assertDoesNotThrow(() -> Arrays.stream(ProcessSecurityConfiguration.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("CancellationConfiguration")).findFirst().orElseThrow())
assertDoesNotThrow(() -> cancelConfig.getDeclaredMethod("processControlService", ProcessEngine.class, DmnEngine.class, ProcessModelValidator.class, ProcessReleaseService.class, ProcessControlNativeAccess.class, ProcessCancellationReceiptStore.class, ObjectProvider.class)) // genuine RED while nested config is absent
instantiate final configuration with mocked ProcessEngine/release/verifier/ObjectProvider and assert exact collaborator object identity
assert existing processEndpointAuthenticationPolicy already protects process-instances wildcard and no admin/public path is added
```
- Verification contribution: REQ-503/512.
- After this file: RED until File 19.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`

- Purpose: Define cancellation enabled => API+servlet readiness guard and off compatibility.
- Symbols: workerWebBoundaryGuard property cases.
- Repository evidence: existing Case/HumanTask/Worker boundary tests.
- Dependencies and consumers: File 20/application.yml.
- Why now: startup guard before source.
- Contract/signature changes: test only.
- Input/output and state mapping: environment booleans/context kind -> pass/fail.
- Error and edge behavior: no production Flyway/engine DDL enablement.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 7, Rule 11.
- Implementation pseudocode:

```text
evaluate workerWebBoundaryGuard with cancellation=false and assert existing configurations pass unchanged
set cancellation=true with api=false and assert the stable prerequisite exception
set cancellation=true with api=true but non-WebApplicationContext and assert failure
set cancellation=true with api=true and WebApplicationContext and assert guard acceptance without starting an application
```
- Verification contribution: TEST-515.
- After this file: RED until File 20.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`

- Purpose: Pin PaC permission/resources/scopes/principals and both route guards without live registration.
- Symbols: expected manifest/resource count/digest additions; MockMvc permission matrices.
- Repository evidence: existing permission processor/integration test.
- Dependencies and consumers: Files 13/18/19/21.
- Why now: authorization contract before metadata/controller source.
- Contract/signature changes: new permission/resource constants only.
- Input/output and state mapping: USER/SERVICE scopes -> allow; missing READ/CANCEL -> deny; exact owner still service-level 404.
- Error and edge behavior: registration not grant; no admin principal/resource.
- Standards impact: MC-VALID-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 6, Rule 7, Rule 11.
- Implementation pseudocode:

```text
assertDoesNotThrow(() -> TianjiProcessPermissions.class.getField("CANCEL"))
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.api.ProcessControlController")) // genuine RED while constants/controller are absent
inspect final annotations/processor output for TIANJI_PROCESS_CANCEL, control/cancel resources, READ+CANCEL and USER+SERVICE principals
MockMvc authorized/missing-scope cases assert 200/403 while service exact-owner failure remains hidden 404
assert generated manifest is local artifact evidence only and no registration/grant client is invoked
```
- Verification contribution: REQ-503/512/API gates.
- After this file: RED until Controller/permissions/config.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`

- Purpose: Retain immutable timer-wait metadata already parsed from released XML.
- Symbols: field/getter/constructor chain `timerWaits` (`Map<String,Duration>`).
- Repository evidence: current immutable maps and constructor overload chain.
- Dependencies and consumers: File 10/11.
- Why now: test fixed exact internal metadata.
- Contract/signature changes: internal additive map; old constructors delegate empty/parsed values; public external model unchanged.
- Input/output and state mapping: timer activity ID -> exact Duration defensive map.
- Error and edge behavior: no model admission/digest/wire change; null/mutation rejected.
- Standards impact: MC-MODEL-001, MC-TIME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 3, Rule 10, Rule 11.
- Implementation pseudocode:

```text
add final Map<String,Duration> timerWaits and public timerWaits() returning the immutable map
extend the terminal constructor with timerWaits; every older overload delegates with Map.of() or its existing metadata
assign this.timerWaits=Map.copyOf(timerWaits) alongside existing maps; do not alter XML bytes, hashes or public wire types
```
- Verification contribution: File 1 GREEN/native classifier authority.
- After this file: model compiles; validator still must populate.

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`

- Purpose: Populate timer metadata from existing parsed timers without admission expansion.
- Symbols: final ValidatedProcessModel construction.
- Repository evidence: current `timers`, humanTimers, waits and 256 traversal parser.
- Dependencies and consumers: File 9/11.
- Why now: model holder exists.
- Contract/signature changes: internal metadata only.
- Input/output and state mapping: current supported timer nodes -> immutable map; no inferred runtime count.
- Error and edge behavior: all existing syntax/semantic checks unchanged.
- Standards impact: MC-REUSE-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
retain the existing LinkedHashMap timers populated by intermediate/boundary timer parsing and all validation branches
at the final BPMN ValidatedProcessModel construction pass Map.copyOf(timers) into the new timerWaits parameter
do not change node/traversal limits, allowed elements/attributes, Case barrier logic, model bytes or manifest digest inputs
```
- Verification contribution: ProcessModelValidatorTest GREEN.
- After this file: released model supplies all known wait kinds.

#### File 11 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`

- Purpose: Own bounded repeatable native observation, root lock and verified history facts; never direct ACT writes.
- Symbols: nested `OccurrenceKey`, `ControlFacts`, `LockedRoot`, `TerminalFacts`; observe/lock/history methods.
- Repository evidence: ProcessExecutionService/CaseNativeAccess/HumanTaskNativeAccess/worker native queries.
- Dependencies and consumers: engine/runtime/history/task/management services, JdbcTemplate, model/release; service.
- Why now: metadata complete and RED native tests fixed.
- Contract/signature changes: internal class only.
- Input/output and state mapping: exact tenant/source/model -> current status/unique wait summaries/root revision/history times/delete category.
- Error and edge behavior: repeatable read owned by caller; page 257 overflow; dedup exact occurrence; incompatible duplicate/unknown released kind/root/history mismatch unavailable; root SQL only FOR UPDATE; no child lock.
- Standards impact: MC-NAME-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-PATTERN-001, MC-TIME-001, MC-SCOPE-001; explicit constructor/profile exception.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
constructor unwraps TransactionAwareDataSourceProxy, requires exact DataSourceTransactionManager/schema/runtime/history/task/management services
observe(source,definition,model): use these exact source-filtered bounded queries:
tasks = taskService.createTaskQuery().taskTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // no active filter
externalJobs = managementService.createExternalWorkerJobQuery().jobTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257)
timers = managementService.createTimerJobQuery().timers().jobTenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // List<Job>
messages = runtimeService.createEventSubscriptionQuery().eventType("message").tenantId(tenant).processInstanceId(instance).processDefinitionId(definition).listPage(0,257) // List<EventSubscription>
normalize Task.executionId/taskDefinitionKey, Job.executionId/elementId and EventSubscription.executionId/activityId into artifact-backed OccurrenceKey values only
collect <=256 distinct artifact execution IDs; empty set returns empty wait summaries without SQL; otherwise one bound-placeholder JdbcTemplate SELECT of id_,act_id_,proc_inst_id_,proc_def_id_,tenant_id_ verifies all IDs under exact tenant/instance/definition, with no FOR UPDATE
require one execution row per artifact ID/activity and reject missing/wrong affinity; do not query/count unrelated structural/root/scope/inactive gateway executions
store artifact IDs as supporting evidence, merge identical keys once, classify through released maps and reject conflicting/unknown artifact kinds or the 257th unique key
never use nonexistent public TimerJob/MessageSubscription types, ExecutionQuery.active()/executionIds(), or Execution.isActive()
lockRoot executes exact ACT_RU_EXECUTION root SELECT FOR UPDATE; terminalHistory verifies tenant/definition/business/end/delete category and returns actual Instant
```
- Verification contribution: NativeAccessTest GREEN/service dependency.
- After this file: native facts available; no use case/wiring yet.

#### File 12 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlService.java`

- Purpose: Implement Query + Command pattern, owned transactions, deterministic versions and outer race classifier.
- Symbols: constructor, `control`, `cancel`, private snapshot/version/digest/delete-stage marker/SQLSTATE classifier.
- Repository evidence: ProcessExecutionService/FlowableCaseService/receipt/store patterns.
- Dependencies and consumers: release service/model validator/native access/command+cancel stores/dispatch; Controller.
- Why now: all storage/native collaborators exist.
- Contract/signature changes: internal public service methods only; no Spring stereotype.
- Input/output and state mapping: exact Spec §7.3, including REPEATABLE_READ Query, clock-free version, terminal precedence and exact transaction order.
- Error and edge behavior: require no ambient TX; 10s write/5s local lock; current auth assumed pre-TX; same-command replay revalidates START; Case block/provenance plain read; delete constant reason; history/dispatch/V9/APPLIED order; exact SQLSTATE outside execute; safe hashed log no reason/SQL/subject.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-PATTERN-001, MC-TIME-001, MC-SCOPE-001; Command+receipt; explicit class exception.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
control(actor,query): reject ambient TX; REPEATABLE_READ loads exact START/release/model/native facts, then builds sorted DTO and clock-free ProcessRequestDigest version
cancel(actor,command): write.execute locks START then generic PROCESS_CANCEL; replay validates detail; absent root writes audited terminal no-op before live-version comparison
live root compares version, rejects caseSources/provenance mismatch, calls RuntimeService.deleteProcessInstance, verifies history, schedules exact dispatch keys, inserts V9, applies generic receipt
outside write.execute walk at most 16 causes: Flowable optimistic/delete marker or 40001/40P01/55P03 -> ProcessVersionConflictException; 57014/08xxx/unknown -> unavailable
```
- Verification contribution: ProcessControlServiceTest GREEN/transaction audit.
- After this file: complete behavior exists but not mapped/wired.

#### File 13 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessVersionConflictException.java`

- Purpose: Distinguish stale/native transient conflict from idempotency.
- Symbols: final RuntimeException type, no sensitive message/cause exposure.
- Repository evidence: existing typed ProcessConflict/WorkerLease exceptions.
- Dependencies and consumers: service/errors.
- Why now: service classifier needs exact outcome.
- Contract/signature changes: internal error type.
- Input/output and state mapping: rolled-back race -> 409 code.
- Error and edge behavior: constructed only after rollback; no SQLSTATE on wire.
- Standards impact: MC-NAME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 5, Rule 11.
- Implementation pseudocode:

```text
public final class ProcessVersionConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  public ProcessVersionConflictException() { super("Process version or native state changed"); }
}
Do not attach SQLException, SQLSTATE, reason or native identifiers as cause/message fields.
```
- Verification contribution: classifier/API error tests.
- After this file: version conflict is distinct.

#### File 14 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessCancellationBlockedException.java`

- Purpose: Distinguish formal Case-model block from all other conflicts.
- Symbols: final RuntimeException type.
- Repository evidence: typed local error pattern.
- Dependencies and consumers: service/errors.
- Why now: service branch needs stable 409 code.
- Contract/signature changes: internal type only.
- Input/output and state mapping: nonempty caseSources -> blocked.
- Error and edge behavior: no Case ID/details exposed.
- Standards impact: MC-NAME-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 5, Rule 11.
- Implementation pseudocode:

```text
public final class ProcessCancellationBlockedException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  public ProcessCancellationBlockedException() { super("Process cancellation is blocked by its released model"); }
}
Do not expose Case instance/source details or add an administrator override.
```
- Verification contribution: block/API tests.
- After this file: blocked outcome distinct.

#### File 15 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlController.java`

- Purpose: Expose exact two default-off private operations with current authorization before service/replay.
- Symbols: `control`, `cancel`; PaC annotations; class mapping/conditional property.
- Repository evidence: ProcessController/CaseController patterns and existing process-instance auth wildcard.
- Dependencies and consumers: TianjiProcessAccess/ProcessControlService/C contracts.
- Why now: service/errors exist.
- Contract/signature changes: exact GET/POST routes; path/body equality; no-store 200 direct bodies.
- Input/output and state mapping: current actor -> service; no tenant/actor/release inputs.
- Error and edge behavior: access.require first for both operations including replay; mismatched path 400; no admin.
- Standards impact: MC-VALID-001, MC-BEAN-001, MC-JSON-001, MC-SCOPE-001; explicit constructor/factory exception.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 6, Rule 11.
- Implementation pseudocode:

```text
@RestController @RequestMapping("/api/tianji/v1/process-instances") @ConditionalOnProperty(cancellation.enabled=true)
control(id): @PermissionResource(CONTROL_API,USER+SERVICE,READ_SCOPE+CANCEL_SCOPE) @RequiresPermissions(READ,CANCEL); actor=access.require(CONTROL_API); return noStore(service.control(actor,new GetProcessInstanceQuery(id)))
cancel(pathId,command): same principal/permission set with CANCEL_API; validate pathId.equals(command.instanceId) before service; actor=access.require(CANCEL_API); return noStore(service.cancel(actor,command))
No method reads tenant/actor/roles from headers/body or returns a wrapper different from the C contract.
```
- Verification contribution: Controller/permission tests.
- After this file: controller class exists but conditional wiring/config incomplete.

#### File 16 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java`

- Purpose: Strictly bound/validate CancelProcessCommand body before Jackson/service.
- Symbols: 4KiB constant, strict Jackson3 mapper, supports/validate cancellation branch.
- Repository evidence: existing worker/Agent strict body validators and bounds.
- Dependencies and consumers: API-502/ProcessApiErrors.
- Why now: Controller exists.
- Contract/signature changes: exact four-member body only; old body handling unchanged.
- Input/output and state mapping: bytes -> validated unchanged stream -> C constructor.
- Error and edge behavior: length/depth/name/string/token/duplicate/unknown/trailing/scalar/null rejection; TooLarge existing mapping.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-UTIL-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 11.
- Implementation pseudocode:

```text
supports adds CancelProcessCommand.class without changing existing branches
beforeBodyRead selects MAX_PROCESS_CANCEL_BYTES=4096, reads max+1, throws TooLarge on overflow
STRICT_CANCEL_JSON requires object with exactly instanceId,commandId,expectedVersion,reason; duplicate/unknown/trailing/nonstring/null fail IllegalArgumentException
return ByteArrayInputStream of the unchanged validated bytes; never log or retain reason
```
- Verification contribution: Controller strict body tests/old advice regression.
- After this file: input boundary complete.

#### File 17 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`

- Purpose: Map typed block/version errors and preserve existing envelope/status behavior.
- Symbols: two handlers/codes; existing generic handlers unchanged.
- Repository evidence: current error advice mappings.
- Dependencies and consumers: Files 13/14/Controller.
- Why now: typed exceptions exist.
- Contract/signature changes: 409 `TIANJI_PROCESS_VERSION_CONFLICT`, 409 `TIANJI_PROCESS_CANCEL_BLOCKED`.
- Input/output and state mapping: typed exceptions -> safe OrchestrationErrorDTO/null correlation.
- Error and edge behavior: SQLSTATE/reason/cause not exposed; idempotency conflict remains distinct; 57014/08xxx/unclassified service converts unavailable before advice.
- Standards impact: MC-JSON-001, MC-LOG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 6, Rule 11.
- Implementation pseudocode:

```text
@ExceptionHandler(ProcessVersionConflictException) returns error(CONFLICT,"TIANJI_PROCESS_VERSION_CONFLICT")
@ExceptionHandler(ProcessCancellationBlockedException) returns error(CONFLICT,"TIANJI_PROCESS_CANCEL_BLOCKED")
retain ProcessConflictException -> TIANJI_IDEMPOTENCY_CONFLICT and every auth/not-found/tenant/too-large/unavailable handler unchanged
error body remains OrchestrationErrorDTO(code,null) with application/json and no-store
```
- Verification contribution: Controller/service error tests.
- After this file: provider errors match OAS.

#### File 18 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Add one cancel permission, two resources/messages and exact constants/scopes.
- Symbols: `CANCEL`, `CONTROL_API`, `CANCEL_API`, `CANCEL_SCOPE`; annotations/messages.
- Repository evidence: existing process/Case PaC definitions.
- Dependencies and consumers: Controller/application permission scope mapping/processor.
- Why now: API class/error ready.
- Contract/signature changes: additive metadata only; registration no grant.
- Input/output and state mapping: constants match ProcessControlClient exactly.
- Error and edge behavior: USER/SERVICE only at resources; no admin permission.
- Standards impact: MC-NAME-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 6, Rule 7, Rule 11.
- Implementation pseudocode:

```text
add @PermissionDefinition code TIANJI_PROCESS_CANCEL and localized permission/control/cancel messages
define CANCEL, CONTROL_API="tianji.api.process.instance.control", CANCEL_API="tianji.api.process.instance.cancel", CANCEL_SCOPE="tianji:process:cancel"
bind ProcessControlClient constants where specified; do not add role IDs, client IDs, administrator resources or grant calls
```
- Verification contribution: permission integration/producer artifact.
- After this file: PaC source complete.

#### File 19 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Wire cancellation-only native/store/service/controller dependencies under exact V9 readiness without exposing when off.
- Symbols: nested `CancellationConfiguration`; named Beans for native access, receipt store, service; AgentDispatchStore reuse/fallback exact instance.
- Repository evidence: current explicit `@Bean` factories/nested capability configurations/ObjectProvider patterns.
- Dependencies and consumers: Process engine/release/verifier/current stores/Controller.
- Why now: all production collaborators exist.
- Contract/signature changes: conditional on API+cancellation; calls `verifyCancellation`; no existing ProcessExecutionService constructor change.
- Input/output and state mapping: exact source/TM/schema -> collaborators; existing AgentDispatchStore or same-source private instance.
- Error and edge behavior: missing TM/history/V9/affinity fails startup; no remote/poller requirement and no schema install.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-BEAN-001, MC-CONFIG-001, MC-SCOPE-001; explicit Root factory exception, stable method names.
- Literal rule enforcement: Rule 4, Rule 5, Rule 7, Rule 11.
- Implementation pseudocode:

```text
@Configuration conditional on api.enabled+cancellation.enabled defines explicit processControlNativeAccess/processCancellationReceiptStore/processControlService Bean methods
unwrap process engine DataSource/TM/schema, call existing ProcessApiSchemaVerifier.verifyCancellation before constructing any service
obtain AgentDispatchStore from ObjectProvider or construct same-source instance; require storage affinity and inject release/native/command/detail/dispatch collaborators
leave ProcessExecutionService/Case/HumanTask/Worker Bean signatures and existing conditions unchanged
```
- Verification contribution: API/Security configuration tests.
- After this file: beans can exist only under property/readiness.

#### File 20 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`

- Purpose: Fail closed when cancellation is enabled without servlet Process API.
- Symbols: workerWebBoundaryGuard cancellation branch.
- Repository evidence: existing cases/tasks/worker/agent-results guards.
- Dependencies and consumers: environment/context.
- Why now: wiring property defined.
- Contract/signature changes: one additive guard.
- Input/output and state mapping: property booleans/context -> pass/fail.
- Error and edge behavior: no mutation/startup side effect besides fail closed.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 7, Rule 11.
- Implementation pseudocode:

```text
read boolean stellar.tianji.process.cancellation.enabled in workerWebBoundaryGuard
if true and stellar.tianji.process.api.enabled is false then throw IllegalStateException("Process cancellation requires an enabled servlet Process API")
if true and ApplicationContext is not WebApplicationContext then throw the same fail-closed prerequisite error
do not mutate any Flowable/Flyway/Outbox property or start a component
```
- Verification contribution: ProcessStorageConfigurationTest.
- After this file: boundary guard complete.

#### File 21 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Add default-off capability and permission-scope mapping with base-key parity.
- Symbols: `stellar.tianji.process.cancellation.enabled: false`; `TIANJI_PROCESS_CANCEL: ["tianji:process:cancel"]`.
- Repository evidence: one base config and current process permission scopes/capability keys.
- Dependencies and consumers: config guards/Jianshen user scope policy.
- Why now: all code exists and remains off by default.
- Contract/signature changes: additive keys only; no credentials/role grants/production enable.
- Input/output and state mapping: external override may enable only after runtime provisioning; base stays false.
- Error and edge behavior: missing external prerequisites fail via guards/verifier.
- Standards impact: MC-CONFIG-001, MC-JSON-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 6, Rule 7, Rule 11.
- Implementation pseudocode:

```text
egon.cola.platform.tianquan.jianshen.user-scope-policy.permission-scopes.TIANJI_PROCESS_CANCEL = ["tianji:process:cancel"]
stellar.tianji.process.cancellation.enabled = false
retain all existing key/value hierarchy exactly; there are no checked-in application-profile YAML files to mirror
```
- Verification contribution: config/permission tests.
- After this file: default-off source wiring complete.

#### File 22 — `CREATE docs/egon/evidence/2026-11-05-bounded-process-cancellation.md`

- Purpose: Record actual final RED/GREEN/source/PaC/JAR/hash/diff evidence and explicit runtime gaps.
- Symbols: all requirement/test/manual-check results; final V9/old SQL hashes; no T3-10 completion claim.
- Repository evidence: formal Case evidence style.
- Dependencies and consumers: all Steps and actual Root commands.
- Why now: final source delivery audit only after tests.
- Contract/signature changes: none.
- Input/output and state mapping: actual results, no prefilled PASS.
- Error and edge behavior: record offline/dependency/compile/test distinction; mocks not PG/native concurrency; permission artifact not registration/grant.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001`, `MC-PATTERN-001`, `MC-SCOPE-001`, `MC-TEST-001`, `MC-BLOCKER-001` — final evidence records each result separately.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 7`, `Rule 9`, `Rule 10`, `Rule 11` — actual evidence and approved technical-profile exceptions remain explicit.
- Implementation pseudocode:

```text
record every Step commit/path/hash and actual focused/regression/permission artifact command with counts and exit codes
map REQ-501..515 and predecessor REQ-401..416 to actual evidence; record all 17 MC and ten literal-rule results
verify V9 hash equals Step3, V1-V8/POM/old contracts/frontend unchanged, default false and PaC artifact exact
state PostgreSQL/native concurrency/IAM/deployment/browser RuntimeUnverified and T3-10 compensation/dead-letter work Required
```
- Verification contribution: final completion evidence.
- After this file: Step 4 is auditable for Root commit; runtime work remains separate.

- Validation working directory: repository root.
- Verification command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessModelValidatorTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest,ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ProcessControlContractTest` followed by the selected regression, permission artifact verify, strict docs and static scope gates below.
- Expected result: all selected finite commands exit 0; exact PaC artifact contains the new resources; old SQL/contracts/POM/frontend stay unchanged; evidence marks all live runtime boundaries Unverified.
- RED command: Root runs `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessModelValidatorTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest`; expected named missing-behavior assertions only.
- Focused GREEN command: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessModelValidatorTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest,ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ProcessControlContractTest`; expected exit 0.
- Final finite regression: `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,ProcessModelValidatorTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,ProcessExecutionServiceTest,FlowableHumanTaskServiceTest,FlowableWorkerServiceTest,CaseControllerTest,CaseStorageConfigurationTest,ProcessReleaseServiceTest,FlowableAgentWaitInspectorTest`; no listener/PG selector.
- Permission artifact verify: first set `task_permission_baseline=$(git rev-parse HEAD)` and require it matches `^[0-9a-f]{40}$`; then run `STELLAR_VERIFY_GOAL=verify ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o clean -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Ppermission-producer -Dpermission.commit="$task_permission_baseline" -Dpermission.buildId=LOCAL_UNCOMMITTED-process-control-20261105-step4 -Dtest=ProcessControlControllerTest,ProcessPermissionIntegrationTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest`; explicit `STELLAR_VERIFY_GOAL=verify` plus `clean` prevents incremental annotation-processing omission without changing source meaning. Inspect generated JAR manifest/resources only; no live registration.
- Static final gate: strict Spec/Plan validators, `git diff --check`, exact file inventory, old SQL hashes unchanged, V9 equals Step 3 hash, POMs/ProcessClient/ProcessInstanceDTO/HttpProcessClientTest/frontend untouched, no operational stub/search hit.
- Failure returns to: model tests -> Files 9/10; native/service -> Files 11/12/Step 3; API -> Files 13-18; wiring -> Files 19-21; any V9 defect returns to a new Spec/forward migration decision, never editing committed V9; offline missing-cache resolution returns to §6.2 only.
- Completion criteria: all finite gates exit 0; default-off/security/OAS/JAR/source audit exact; evidence marks PG/native concurrency/IAM/deployment/browser runtime Unverified and T3-10 remainder Required.
- Rollback: source paths Step 4 may revert while V9 remains; disable flag; never revert to pre-V9 binary after schema application.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidatorTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlControllerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ValidatedProcessModel.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessModelValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessVersionConflictException.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessCancellationBlockedException.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessControlController.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-05-bounded-process-cancellation.md`.
- Commit: `feat(process): add bounded owner process cancellation`.

## 8. Test, Validation, and Quality Gates

All rows are future Root-only instructions. No command has been executed by this Plan. Every Maven/source-reactor command includes `-DskipITs=true`; no selector includes `HttpProcessClientTest`, any `*PostgresTest`, application startup or environment runtime.

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| S1 RED | repository root | `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessControlContractTest` | C reflection RED | nonzero only named missing-contract assertion | Step1 File1 | 501/502/504/505/515; JVM only |
| S1 GREEN | repository root | same with `-Dtest=ProcessControlContractTest,OrchestrationContractTest` | C contract | exit0; named tests pass | Step1 Files2-9 | same; JVM only |
| S2 RED | repository root | source reactor with required common flags and `-Dtest=HttpProcessControlClientTest,ProcessControlOpenApiContractTest` | CL fake/resource RED | nonzero named missing interface/resource assertions | Step2 Files1/2 | 501/502/504/513/515; no listener |
| S2 GREEN | repository root | source reactor flags plus `-Dtest=ProcessControlContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,HttpCaseClientTest,CaseResponseCodecTest` | C/CL | exit0; fake/static tests pass | Step2 Files3-5 | no listener/network |
| S3 RED | repository root | source reactor flags plus `-Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest` | migration/storage reflection/static RED | nonzero named assertions, no PG attempt | Step3 Files1-5 | 504/505/507/508/510/511/515; static/mock |
| S3 GREEN | repository root | source reactor flags plus `-Dtest=ProcessCancellationSqlTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,AgentDispatchStoreTest,HumanTaskStorageVerifierTest,CaseStorageConfigurationTest,CaseStartProvenanceStoreTest` | migration/storage | exit0; old hash/V8+V9/exact-key tests pass | Step3 Files6-10 | no DB; runtime unverified |
| S4 RED | repository root | source reactor flags plus `-Dtest=ProcessModelValidatorTest,ProcessControlNativeAccessTest,ProcessControlServiceTest,ProcessControlControllerTest,ProcessApiConfigurationTest,ProcessSecurityConfigurationTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest` | PS reflection/context RED | nonzero named missing-behavior assertions | Step4 Files1-8 | source/MockMvc only |
| S4 focused GREEN | repository root | exact Step4 focused GREEN command in §7 | whole slice | exit0; selected tests pass | Step4 owning files | all requirements; no runtime |
| S4 regression | repository root | exact Step4 regression command in §7 | existing Process/Case/HumanTask/Worker compatibility | exit0; selected tests pass, ITs skipped by property/selector | owning Step | compatibility; not PG/native acceptance |
| Permission artifact | repository root | literal full `STELLAR_VERIFY_GOAL=verify` command under Step 4 validation in §7 | generated PaC/JAR resources | exit0; exact permission/resources/scopes/principals in artifact | Step4 Files15/18/19/21 | no registration/activation/grant |
| Document strict | repository root | Spec skill validates `docs/egon/spec/2026-11-04-23-22-process-control-cancellation.md --strict`; Plan skill validates `docs/egon/plan/2026-11-05-00-25-process-control-cancellation-implementation.md --strict` | committed governance docs | PASS | Step1 docs | no Accepted/Ready inference |
| Static/scope | repository root | `git diff --check`; exact path/hashes/import/config/search review | each Step/final | exit0; only Step paths; V1-V8/POM/old contracts untouched | owning Step | static |
| Runtime/manual | user-controlled future environment | no command in this Plan | PostgreSQL migration/native races/IAM/deployment/UI | Required but `RuntimeUnverified` | future reviewed work | not completion evidence here |

Common source-reactor flags referenced above mean the literal base command `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none` plus the exact `-Dtest` selector shown in §7. The settings file is local-repository/interactive-only with no credentials or mirrors. Root records actual exit, named tests/counts and classifies offline cache resolution separately; an unexpected skip is not a pass and no failure authorizes network/global-settings/dependency/repository changes.

No scratch harness is planned: existing JUnit/Mockito/MockMvc/fake HttpClient/static resource tests, source-reactor, permission producer and document validators cover every authorized gate. If an implementation discovery proves otherwise, stop and amend the Plan before creating anything under `scripts/work/`.

## 9. Migration, Compatibility, Rollout, and Rollback

1. Step 1 and Step 2 are additive source contracts only; no server route exists.
2. Step 3 rechecks latest migration and all 24 existing SQL hashes, creates the only V9 once, embeds its final SHA/CRC in verifier authority, and commits it immutable. There is no application/DB execution.
3. Step 4 ships V9-aware code and the capability default-off. Old ProcessClient/DTO/start/get/Case/HumanTask/Worker behavior remains.
4. Future deployment, under separate authority: drain old Process nodes; deploy V9-aware binary with cancellation off; externally apply V9; verify catalog/history/privileges; build/register/activate PaC resources and explicit grants; only then enable the cancellation flag.
5. After V9 application, never run pre-V9 binary where exact history verifiers reject V9. Rollback means flag off and compatible binary retained; correction is a newly reviewed future migration, never V9 edit/repair/drop.
6. Agent CLOSING rows continue through existing maintenance even when new cancellation admission is disabled. Remote closure is not a rollout prerequisite for the local cancellation commit.
7. No backfill: existing cancelled history without V9 remains terminal but is not claimed as API-accepted audit.

Compatibility checks per Step: Step 1 reflection pins old Java/JSON; Step 2 old Case codec transport; Step 3 exact V1-V8 hashes and V8/V9 consumers; Step 4 old Process/Case/HumanTask/Worker selected regression and permission artifact. No frontend change occurs; future T4-04 consumer work is separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-501` | §4/§7/§9 | 1/2 | C client/types; CL client/codec/OAS | S1/S2 | separate capability and old golden |
| `REQ-502` | §4/§7.3 | 1/2/4 | snapshot/wait, codec/OAS, model/native/service/API | TEST-502-504/513 | useful deterministic Query |
| `REQ-503` | §4/§7/§9 | 4 | controller/access/service/permission/config | controller/permission/config | current exact owner only |
| `REQ-504` | §4/§9 | 1-4 | command/client/generic+detail/controller | contract/fake/store/API | stable request/replay/conflict |
| `REQ-505` | §4/§7/§10 | 1/3/4 | result/V9/store/service | TEST-506-508 | terminal precedence/no-op audit |
| `REQ-506` | §4/§7.3 | 4 | model/native/service | TEST-504-506 | root/version/Case/provenance rules |
| `REQ-507` | §4/§7/§11 | 3/4 | V9/stores/native/service | TEST-508/511/512 | atomic participant call/rollback proof, runtime unverified |
| `REQ-508` | §4/§7/§11 | 3/4 | AgentDispatchStore/service/evidence | TEST-512 | durable exact-key CLOSING; poller unchanged |
| `REQ-509` | §4/§7.3 | 4 | native/service/errors/API | TEST-509/510 | exact outer classifier |
| `REQ-510` | §4/§11/§16 | 3 | V9/SQL test/verifier/evidence | S3/static hash | sole immutable final V9 |
| `REQ-511` | §4/§11.4 | 3/4 | verifier/tests/config readiness | TEST-514/515 | exact V8+V9 consumers |
| `REQ-512` | §4/§9/§16 | 4 | permissions/controller/config/application.yml | TEST-515/PaC | default-off/no implicit grant |
| `REQ-513` | §4/§9/§14 | 2/4 | codec/OAS/client/advice/controller | S2/S4 | strict bounded fake/MockMvc |
| `REQ-514` | §4/§18 | 1/4 | docs/evidence/scope audit | strict/final static | no T3-10 completion claim |
| `REQ-515` | §4/§6.3/§9-11 | 1-4 | C/codec/OAS/V9/store/service/tests | TEST-503/507/508/513 | actual independent UTC instants/no chronology |
| `REQ-401`, `REQ-402`, `REQ-403`, `REQ-404` | Formal Case §4 | 3/4 compatibility | V8/verifiers/Case stores/release/model remain | S3/S4 selected regressions + hashes | no V8/direct/linked Case regression |
| `REQ-405`, `REQ-406`, `REQ-407`, `REQ-408` | Formal Case §4 | 4 compatibility | Case API/read/actions/native versions unchanged | Case/model/controller selected regression/source audit | cancellation does not widen Case authority |
| `REQ-409`, `REQ-410`, `REQ-411`, `REQ-412` | Formal Case §4 | 3/4 compatibility | HumanTask/provenance/factory/Inbox unchanged | storage/config/Case selected regressions | no privilege/lock/completion drift |
| `REQ-413`, `REQ-414`, `REQ-415`, `REQ-416` | Formal Case §4 | 4 compatibility/evidence | source/timer flags/SERVICE boundary/native gap | model/config/source audit/final evidence | Case-source block and S09 RuntimeUnverified preserved |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `EXEC-GATE-501` | Root full Plan review and explicit execution GO remain required; the web dependency is already satisfied | all | web Step 42 commit `43c58a1069a50fb71b955540bae2d7e8526794b0`; Root instruction | Root | Open execution gate; continue Plan review only |
| `RISK-501` | Mock/source tests cannot prove PostgreSQL atomicity/deadlock/native timers | 3/4 | Spec §18 | Root/future runtime owner | Explicit RuntimeUnverified; no claim/block to source Plan review |
| `RISK-502` | Native wait/dispatch count exceeds safety cap | 4 | Spec §7/§18 | Process owner | fail 503; no truncation/admission widening |
| `RISK-503` | Competing V9 appears before Step 3 | 3 | immutable migration rule | Root | stop; return to Spec/Plan, never overwrite |
| `RISK-504` | Old binary cannot accept V9 exact history | 3/4/deploy | current hasCaseMigration | deployment owner | coordinated drain/new binary first |
| `RISK-505` | Agent maintenance unavailable | 3/4 | current CLOSING/poller | operator | durable pending; no remote closure claim |
| `RISK-506` | Full T3-10 recovery/compensation absent | final evidence | roadmap | Root/product | remains Required follow-up |

No unresolved design decision exists. Web Step 42 no longer blocks; `EXEC-GATE-501` now represents only Root's unfinished full Plan review/GO and prevents execution, not strict Draft-Plan review. Any implementation discovery changing contract/schema/security/transaction/route returns to the Spec and blocks coding.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Exactly four Steps match Root's sequence. V9 is sole/final in Step 3; Step 4 cannot edit it. Tests are true assertion RED then minimum GREEN. No new dependency, old SQL edit, admin/Case/compensation/UI scope, listener/PG/runtime validation or non-Root command/commit is planned.

### 12.2 Spec consistency

The Plan preserves the reviewed useful Query, noun `/cancellations`, READ+CANCEL audience, exact owner/current auth, clock-free version, independent time domains, terminal precedence, model-level Case block, root-only native fence, exact SQLSTATE mapping, exact validated dispatch-key batch, default-off rollout and remaining roadmap. Necessity audit found no fetch-then-forward or speculative element.

### 12.3 Repository executability

All paths/symbols were rechecked at `cfffb7649af7a1cb429ac935f2bc2c1f88ac3d8d`; V8 is latest and 24 existing SQL files are present. File order follows compilation and admission dependencies. Every Step has exact RED/GREEN, working directory, rollback, paths and commit. Root rechecks concurrent work before execution.

### 12.4 Test and release completeness

Finite C/CL/PS unit, fake HTTP, MockMvc, static SQL/OAS/docs, permission artifact and selected regressions cover source delivery. Every Maven command carries `-DskipITs=true`; runtime environments remain absent and honestly Unverified. Deployment/migration/permission grants are future separately authorized work.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | Applicable | PASS | §4.7/§5 and approved Spec exception | current C/CL/PS technical vertical preserved | None |
| `MC-REUSE-001` | Applicable | PASS | capability ledger/Step files | current JDK/Spring/Flowable/JDBC/Jackson/receipt/poller reused | None |
| `MC-DEP-001` | Applicable | PASS | POM baseline/§4.7/Steps | zero dependency/POM change | None |
| `MC-NAME-001` | Applicable | PASS | §5/Step type inventory | semantic DTO/Command/Result/Enum/behavior suffixes | None |
| `MC-VALID-001` | Applicable | PASS | Steps1-4 handoff tests/advice/store checks | exact compact/strict/current/native validation under approved exception | None |
| `MC-MODEL-001` | Applicable | PASS | Step1 records/Step3 nested rows/Step4 facts | immutable value records/defensive copies; no entity | None |
| `MC-CONVERT-001` | Not applicable | N/A | Spec §10.4/Plan §4.8 | no distinct conversion; approved direct exact technical construction | None |
| `MC-LOG-001` | Applicable | PASS | Step3/4 store/service/evidence | safe existing logger style; reason/token/SQL/subject excluded | None |
| `MC-BEAN-001` | Applicable | PASS | Step4 explicit config factories/Spec exception | stable named Bean methods; no Lombok refactor under Root exception | None |
| `MC-UTIL-001` | Applicable | PASS | reuse ledger/POM/import gates | JDK/current dependencies only; no Utils | None |
| `MC-JSON-001` | Applicable | PASS | Step1/2/4/OAS gates | Jackson-only exact strict contract | None |
| `MC-TIME-001` | Applicable | PASS | §4.9/Steps1-4 | Instant/UTC Z/timestamptz(6); independent clocks/no chronology | None |
| `MC-CONFIG-001` | Applicable | PASS | Step4 one base application.yml/tests | default false/scope key; no profiles to diverge | None |
| `MC-PATTERN-001` | Applicable | PASS | Command+receipt Step3/4 | complex replay/transaction uses selected pattern; Flowable owns state | None |
| `MC-SCOPE-001` | Applicable | PASS | four Step scopes/§5/forbidden list | no POM/old SQL/old contract/frontend/runtime expansion | None |
| `MC-TEST-001` | Applicable | PASS | §7-§8 selectors/gates | true assertion RED; finite GREEN; runtime limits explicit | None |
| `MC-BLOCKER-001` | Applicable | PASS | §11/all rows | no design blocker; execution gate explicit and owned by Root | None for Draft review; Root GO required before execution |

### 12.6 Final verdict

PASS — Ready for user review

Plan status remains `Draft`; PASS means internally complete for Root review, not approved or executable. Despite the completed web dependency, Root must finish reading and explicitly authorize the full Plan before any source/test/SQL/Maven/Git action.

## Java / CQE contract review

- POJO/value/enum: immutable C records/enums and nested technical facts only; no entity/PO.
- Component/MP: current Process technical JDBC/Flowable exception is preserved; no MP/DDL-runner/POM change.
- Validation: compact constructors, strict codec/advice, current auth, source/native/store checks and negative tests are ordered per Step.
- Uniqueness/DDL: technical PK/partial accepted uniqueness; no soft delete/business key; one final V9 with V1-V8 immutable.
- CQE: control is Query, cancellation is Command; no new Event/outbox/MQ. Existing Agent technical maintenance is reused.
- Architecture: exact current technical vertical; no business archetype/biz layer/hybrid.

## Backend generation and dependency evidence

The backend generator is not used: this is later technical DDL in the approved existing profile, not initial native Light/Web/Service CRUD scaffolding. V9 and affected technical Java are agent-owned only after Root GO. No generator plan/apply, manifest, SQL consumption journal, dependency or generated catalog path is planned.
