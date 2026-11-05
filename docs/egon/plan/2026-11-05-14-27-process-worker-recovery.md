# 受控 Worker 失败查询与死信恢复实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-05-14-27-process-worker-recovery.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 14:27 Asia/Shanghai` |
| Updated | `2026-11-05 14:35 Asia/Shanghai` |
| Owner | Root; exclusive Maven, staging, commit and current-Step Source GO |
| Repository | `tianji-service` |
| Scope | Five sequential C/CL/PS Steps, 43 exact paths and one additive V10; no live activation |
| Source Requirement | User master T3-10 execution goal; Root fully reviewed and accepted exact Spec for Plan |
| Baseline Revision | `main@415a2814cf4a025bd868543c48b22e32e54e41ff` clean, 941 tracked files, 25 immutable main SQL |
| Implements Spec | [Worker recovery Spec](../spec/2026-11-05-13-41-process-worker-recovery.md) |
| Spec Status | `Review` |
| Spec Revision | Root-approved normative SHA `a3ffa627fd9fdb3623cd87f3e9b8b6f60965f76d6f08402534c47050f538468d`, linked editorial-only copy SHA `bae688fe7a85e86dc47e07127aeada60adc2b5bcf9494f920b6378ec3c0b588b` |
| Effective Specs | [Primary recovery Spec](../spec/2026-11-05-13-41-process-worker-recovery.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Implement a separately governed, bounded BPMN Worker failure/deadletter read and one reasoned safe READ_ONLY restore in five logical commits: neutral C, CL SDK/OAS, immutable V10/storage readiness, PS native/service and default-off provider/artifacts. The entire plan and actual current-Step lock require Root review before source. No app/native engine/PG/listener/provider/browser, live IAM permission registration, deployment or migration is run. Existing cancellation/compensation behavior remains in force.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Worker recovery Spec](../spec/2026-11-05-13-41-process-worker-recovery.md).
- Status: Review; exact normative SHA and full corrections reviewed by Root and independent strict PASS, accepted for Plan only.
- Revision: a3ffa627fd9fdb3623cd87f3e9b8b6f60965f76d6f08402534c47050f538468d; linked copy changes only Updated and Related Plans metadata and removes one stray blank-line space.
- Approval evidence: user master goal and persistent Root design review; no user acceptance or source GO is claimed by this draft.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [Recovery Spec](../spec/2026-11-05-13-41-process-worker-recovery.md) | Exact reviewed a3ffa627 and linked copy | All REQ001–007 | Only new design authority |
| Preservation context | [Cancellation](../spec/2026-11-04-23-22-process-control-cancellation.md), [Worker business error](../spec/2026-11-05-04-48-worker-business-error-routing.md), [Compensation](../spec/2026-11-05-12-09-worker-compensation-fixture.md) | Existing committed behavior | Old authority/wire/metadata/evidence preserved | Not reimplementation |
| Roadmap | [Master](../../../../docs/plans/agent-framework-upgrade-plan.md) | Current | T3-10/S10/S11/T3-12/T4 | Full goal and runtime boundaries |

### 2.3 Superseded or excluded content

None superseded. Existing Control wire and old APIs unchanged. No direct ACT writes, general retry-all, arbitrary jump, transactional/external/Case/Agent restore without proof, production data repair, new W main/dependencies/POM/web, SQL edits or live runtime verification. Unsupported states remain visible and manual; T3-12 and T4 are not dropped.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | Primary §4/7/9/11/14 | Bound failure/deadletter observation by BPMN START owner, source, instance and per-source limits | One row per scoped job, native presence separate from V4 evidence, meaningful manual statuses, deterministic order and safe rejection on instance overflow | Exact owning paths and gates below |
| REQ-002 | Primary §4/7/9/11/14 | Admit both operations only under current exact authority and default-off prerequisites | Enabled-only routes, verified READ + WORKER_RECOVER USER/SERVICE exact owner for request and replay, deny malformed/foreign access and unsafe wiring | Exact owning paths and gates below |
| REQ-003 | Primary §4/7/9/11/14 | Recover exactly one proved ordinary READ_ONLY exhausted RETRYABLE occurrence | All §7.3 predicates hold, unsupported or contradictory records stay manual | Exact owning paths and gates below |
| REQ-004 | Primary §4/7/9/11/14 | Serialize exact native mutation and recheck semantic version | Same-source lock order and outermost native call with retries 1; native/audit/command data roll back on any failed invariant or blocked request | Exact owning paths and gates below |
| REQ-005 | Primary §4/7/9/11/14 | Persist immutable reasoned decision and stable replay atomically | One accepted restore per native job in owning source; exact replay after current authority succeeds without move; changed payload conflicts | Exact owning paths and gates below |
| REQ-006 | Primary §4/7/9/11/14 | Keep old contracts and migrations compatible with additive V10 | Old Control wire, closed DTO/codec/OAS and permission behavior preserved; all 25 old main SQL unchanged; V9 and exact V10 work for old readiness; recovery independently requires V10 | Exact owning paths and gates below |
| REQ-007 | Primary §4/7/9/11/14 | Verify finite outcomes and preserve runtime and wider milestones | Finite contract/native-mock/schema evidence is separate from real PG/engine/identity races, S10/S11, T3-12 and T4 | Exact owning paths and gates below |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

C defines independent immutable contracts without affecting existing implementers. CL publishes new closed SDK/OpenAPI and preserves old Control. PS storage next provides exact command/audit/known-history readiness with one immutable V10 reviewed outside the repo before creation. PS service can then depend on that durable store and current native/Worker binding. The provider is last, guarded by existing API and Worker prerequisites and explicit recovery flag false. No later Step repository edits before previous semantic commit. Each Step is complete and compile-verifiable within its limited capability.

### 4.2 Test-first strategy

Reflection/resource assertions without unresolved type references; compile A/signatures; typed behavior RED before B, all expected JUnit F with E0/S0. Preserve old suites. Fix compiler/fixture/dependency/environment problems separately. Each new model/SDK/store/native/provider path has explicit RED and GREEN below. No code that exposes successful restore survives A; no temporary seam survives a commit.

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Clean baseline, full Root review and current-step GO | Read-only/drafts outside repo | Any later source or concurrent Maven | Neutral ABI/type proof first |
| Step 2 | Exact Step1 commit | Read-only/drafts | Any later source | C contracts available, transport isolated |
| Step 3 | Exact Step2 commit and reviewed V10 bytes | Static review | Any other SQL create or later source | Immutable migration/readiness and audit before command |
| Step 4 | Exact Step3 commit | Read-only independent review | Provider source | Native service before exposing route |
| Step 5 | Exact Step4 commit | Read-only artifact review | Deployment/live validation | Default-off wiring and actual local artifacts last |

### 4.4 Commit boundaries

Root owns all Maven/staging/commits. Each Step one semantic exact-path commit. Review staged diff and hashes; never include generated reports/jars, unrelated work or old SQL changes. No push or activation.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Separate recovery C/CL/HTTP/OAS | Add §7/9 | Closed Control enum/codec/static OAS and exact API | Reuse same transport/access and neutral existing query | Two operations/one capability, no duplicate transport layer | Implement |
| Native scoped read/lock/move | Expand §7 | Existing ProcessControlNativeAccess/Flowable shared source; public deadletter query not locking/no REV | Reuse owned bound JDBC read lock and outermost native move | One exact scoped job; bounded readbacks | Implement |
| Shared Specification/Worker proof | Add §7/13 | Current Worker digest and source binding | Reuse current private validation narrowly, one decision for GET/POST | One owner-local policy, no new runtime scheduler | Implement |
| V10 audit and exact verifier | Add §11 | V1 generic lacks reason/result, V2 decision only, V9 cancel only | Existing generic command plus one detail | One immutable table/index; existing prefix check extension | Implement |
| Generic retry/graph/bulk/remote proof | Remove §7/17 | No safe BusinessDB/provider authority | Manual state in bounded read | No unnecessary mutation or fetch-forward | Excluded |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Neutral typed values | 001–007 | G1/G3 | C existing checks | Closed interface/DTOs | CL and PS | Step1 |
| SDK/OAS | 001/002/005/006 | G5/G6/G8 | C commit | Strict one-send/parity | SDK callers and provider contract | Step2 |
| V10/receipt/verifier | 004–007 | G10/G12 | Current schema and exact immutable bytes | Audit/replay/source-ready storage | Native service | Step3 |
| Eligibility and restore | 001–007 | G14/G15 | Exact current storage and Worker model | Same-source guarded service | Provider | Step4 |
| Provider and package | 001–007 | G17/G18 | Prior service and existing IAM boundary | Default-off routes/artifacts | Later user acceptance | Step5 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | C/CL neutral, PS technical current vertical | Primary§6.1/6.2 explicit Root exception | Existing plain JDBC/Flyway/native TX and packages; no domain archetype conversion | All MC-ARCH |
| Reuse/capability | Existing DTO checks, Jackson3, HttpClient, models/Source/TM, V1/V9 | Primary§6/7/9/11 | No extra framework, dependency, schema runner or actor source | All MC-REUSE/DEP |
| Naming/model/validation/conversion | Existing immutable orchestration values and JdbcTemplate stores | Primary§8–11 | Exact records/enums, defensive values, strict closed boundary, exact technical projection | All MC-NAME/VALID/MODEL/CONVERT |
| Bean/logging/util/JSON/time/config | Existing named factory and safe error/profile pattern | Primary§6/9/15/16 | Exact source factory, no new logger, strict Jackson, java.time, one explicit false config | Scoped MC-BEAN/LOG/JSON/TIME/CONFIG |
| Business variation/pattern | Actual same-source evidence gates | Primary§13 | One shared eligibility Specification; reuse native/store lifecycle | Step4 MC-PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| strict exact shapes | C checks, CL tools.jackson, PS Advice | Control precedent/Primary§9 | Separate new contract required | Reuse | No dependency | Steps1/2/5 |
| owner and transaction | START Store, Control native access, Flowable SDK | Primary EVD002/005 | Missing deadletter lock/version only | Extend scoped owned read/move | No second manager/engine | Step4 |
| current Worker proof | FlowableWorkerService/WorkerTopicRegistry/leases | Primary EVD003/004 | Need token-free historical/read-only path | Reuse existing binding/hash | Local technical seams only | Step4 |
| detailed audit | V1/V2/V3/V9 | Primary EVD006 | Generic hash cannot replay reason/result | Add exact V10 | One immutable SQL file | Step3 |
| permission metadata | Existing PS producer/controller | Primary EVD007 | Separate permission and resource keys | Extend | No new processor/roles | Step5 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Primary§6.2 | Current exact technical profile | §5/7 | Exact semantic type/DTO/Command/Result/Enum names. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 2 | Primary§6.2 | Current exact technical profile | §5/7 | Strict actual boundary, authorized context and exact proof, existing technical validation. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 3 | Primary§6.2 | Current exact technical profile | §5/7 | Immutable records and defensive copies; no entity conversion bypass. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 4 | Primary§6.2 | Current exact technical profile | §5/7 | Existing named technical factory, qualified owner-bound wiring and safe error handling; exact approved profile exception. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 5 | Primary§6.2 | Current exact technical profile | §5/7 | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 6 | Primary§6.2 | Current exact technical profile | §5/7 | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 7 | Primary§6.2 | Current exact technical profile | §5/7 | Existing profile keys with single new false flag and scope mapping; packaged parity. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 9 | Primary§6.2 | Current exact technical profile | §5/7 | One shared Specification for complex read/write decisions, same native owner, no new pipeline. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 10 | Primary§6.2 | Current exact technical profile | §5/7 | Existing java.time/DB precision, decision/source time, no global clock or execution claim. | §8 and per-Step EVD | All scoped Steps | PASS |
| Rule 11 | Primary§6.2 | Current exact technical profile | §5/7 | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. | §8 and per-Step EVD | All scoped Steps | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | Instant | Not persisted | UTC Z | Not applicable: operational | Not applicable: no UI | Exact instant; exclude from version | ProcessDB clock_timestamp, microseconds | New only | Step 1/2/4/5, TEST-003/007 |
| TIME-002 | decidedAt | Occurred instant | Instant | UTC instant | UTC Z | Not applicable: decision | Not applicable: no UI | No execution/commit ordering claim; replay exact | ProcessDB clock_timestamp, timestamptz(6) | New only | Step 3/4/5, TEST-003/006/007 |
| TIME-003 | V4 leased/expiry/decision and V10 failure_decided_at | Occurred instant | Instant | UTC instant | Internal | Not applicable: persisted fact | Not applicable: private | Clock ambiguity fails closed; exact selected V4 copy | Existing DB microseconds | V4 unchanged | Step 3/4, TEST-002/007 |

## 5. Change File Tree

```text

CREATE stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryContractTest.java [Step 1]
CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerCommand.java [Step 1]
CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailureDTO.java [Step 1]
CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailuresDTO.java [Step 1]
CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerResult.java [Step 1]
CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryClient.java [Step 1]
CREATE docs/egon/spec/2026-11-05-13-41-process-worker-recovery.md [Step 1]
CREATE docs/egon/plan/2026-11-05-14-27-process-worker-recovery.md [Step 1]
CREATE docs/egon/evidence/2026-11-05-worker-recovery-contract.md [Step 1]
CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryHttpTest.java [Step 2]
CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryResponseCodec.java [Step 2]
MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java [Step 2]
CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryOpenApiContractTest.java [Step 2]
CREATE stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-worker-recovery-v1.json [Step 2]
CREATE docs/egon/evidence/2026-11-05-worker-recovery-sdk.md [Step 2]
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStoreTest.java [Step 3]
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java [Step 3]
CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql [Step 3]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java [Step 3]
CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStore.java [Step 3]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java [Step 3]
CREATE docs/egon/evidence/2026-11-05-worker-recovery-storage.md [Step 3]
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecificationTest.java [Step 4]
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryServiceTest.java [Step 4]
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java [Step 4]
CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessWorkerRecoveryBlockedException.java [Step 4]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java [Step 4]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerLeaseStore.java [Step 4]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerTopicRegistry.java [Step 4]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java [Step 4]
CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecification.java [Step 4]
CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryService.java [Step 4]
CREATE docs/egon/evidence/2026-11-05-worker-recovery-service.md [Step 4]
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryControllerTest.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java [Step 5]
CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryController.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java [Step 5]
MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml [Step 5]
CREATE docs/egon/evidence/2026-11-05-worker-recovery-provider.md [Step 5]

```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryContractTest.java` | `ProcessControlContractTest` and `OrchestrationContractTest` use JUnit assertions in this package; same-package records have public canonical constructors and annotation-free fields. | `recovery_capability_and_records_have_exact_shape`, `command_rejects_invalid_fields_and_preserves_exact_safe_reason`, `failure_row_enforces_closed_dispositions_and_safe_activity`, `observation_enforces_unsigned_utf8_order_uniqueness_cap_and_copy`, `result_enforces_exact_accepted_scheduling_identity`. | Freeze the public surface and supply independently observed constructor RED before behavior changes. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerCommand.java` | `CancelProcessCommand` already redacts its representation and uses `OrchestrationChecks.id/command/hash`; `GetProcessInstanceQuery` owns dot-segment rejection. | `RecoverProcessWorkerCommand(String instanceId, String jobId, String commandId, String expectedVersion, String reason)`, compact constructor, `toString()`. | Carry the exact caller-chosen recovery target, replay key, version and reason. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailureDTO.java` | Existing `ProcessActiveWaitDTO` and `ProcessControlSnapshotDTO` enforce constructor invariants and exact enum facts without adding runtime dependencies. | `ProcessWorkerFailureDTO(String jobId, String activityId, boolean nativeDeadletter, StatusEnum status, ReasonEnum reason, ReportedFailureEnum reportedFailure, String expectedVersion)`; three nested enum types and compact constructor. | Validate a single scoped failure observation independently of provider eligibility computation. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailuresDTO.java` | `ProcessControlSnapshotDTO` validates order and copies values; `HttpProcessClient#compareTaskIds` already uses unsigned UTF-8 byte comparison for stable client order. | `ProcessWorkerFailuresDTO(String instanceId, Instant observedAt, List<ProcessWorkerFailureDTO> failures)`, compact constructor. | Return one complete bounded observed list for the exact process. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerResult.java` | `CancelProcessResult` already composes compact affinity/state validation and java.time; recovery has its own fixed decision. | `RecoverProcessWorkerResult(String instanceId, String jobId, String occurrenceId, String commandId, StatusEnum status, int retries, Instant decidedAt)` and nested `StatusEnum.RETRY_SCHEDULED`. | Express one accepted scheduling decision and replay result. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryClient.java` | `ProcessControlClient` publishes explicit path/scope/resource constants and separate typed methods. | `ProcessWorkerRecoveryClient#workerFailures(GetProcessInstanceQuery)`, `#recoverWorker(RecoverProcessWorkerCommand)`, `WORKER_FAILURES_PATH`, `WORKER_RECOVERIES_PATH`, `WORKER_FAILURES_RESOURCE_KEY`, `WORKER_RECOVERIES_RESOURCE_KEY`, `WORKER_RECOVER_SCOPE`. | Publish the independent neutral L0 Query/Command capability. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/spec/2026-11-05-13-41-process-worker-recovery.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Publish the exact approved primary Spec with metadata-only Plan backlink. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/plan/2026-11-05-14-27-process-worker-recovery.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Publish this exact reviewed five-Step Plan. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-recovery-contract.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Record actual neutral contract checks. | Step 1 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryHttpTest.java` | `HttpProcessControlClientTest#FakeHttpClient` is private and feeds a real `BodyHandler` through fake `HttpResponse.ResponseInfo` and chunked subscriber. Use the same bounded in-memory approach inside this exact new test path. | `recovery_capability_is_available`, `observes_exact_path_and_maps_closed_rows`, `posts_exact_command_once_and_explicit_replay_preserves_result`, `rejects_unsafe_transport_before_fetching_credentials`, `maps_only_the_documented_json_status_code_pairs`, `rejects_malformed_success_and_wrong_affinity`, `bounds_body_and_does_not_resend_on_transport_failure`; private fake `HttpClient`, reply and subscriber fixtures. | Specify SDK dispatch, one-send, body validation and error boundaries in an in-memory transport. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryResponseCodec.java` | `ProcessControlResponseCodec` uses `tools.jackson.core.JsonFactory`, `StreamReadConstraints/StreamReadFeature`, `tools.jackson.databind.JsonNode` and `json.JsonMapper`; `CaseResponseCodec` demonstrates operation-specific finite status allowlists. Existing CL POM supplies Jackson 3. | Package-private `ProcessWorkerRecoveryResponseCodec`, `failures(byte[])`, `recovery(byte[])`, `errorCode(byte[], int, boolean)`, private strict tree, exact keys, text and UTC helpers. | Decode the new operation family with strict shape and error-pair isolation. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` | `control` and `cancel` show strict decoder and request/response affinity. `call` owns `segment`, normalized base URI checks for Case/Task, bearer validation, `HttpClient.Redirect.NEVER`, one `sendAsync`, `BoundedBodySubscriber` and timeout cancellation. | `implements ProcessWorkerRecoveryClient`, `workerFailures(GetProcessInstanceQuery)`, `recoverWorker(RecoverProcessWorkerCommand)`, private recovery codec field, recovery-only branches in generic `call`. | Connect the new neutral capability to existing bounded one-send transport. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryOpenApiContractTest.java` | `ProcessControlOpenApiContractTest` uses classloader resource lookup, Jackson tree and record component reflection, validates local refs and OAuth scopes, and needs no Spring runtime. | `openapi_publishes_exact_paths_security_and_status_pairs`, `openapi_schemas_match_closed_records_enums_and_bounds`, `every_local_reference_resolves`; private resource read and comparison helpers. | Freeze the separately packaged OpenAPI 3.1 surface and prove semantic parity with C and the SDK's documented response matrix. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-worker-recovery-v1.json` | CL packages `process-control-v1.json` and `case-v1.json` from this same resource directory. Control documents exact path, optional header, required scopes, no-store and status-specific code restrictions; reuse the packaging and contract style. | OpenAPI 3.1.0, JSON Schema 2020-12, GET `getProcessWorkerFailures`, POST `recoverProcessWorker`, exact local `components.schemas`, `oauth2` authorizationCode/clientCredentials examples. | Package the independent Worker recovery REST consumer contract. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-recovery-sdk.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Record actual SDK/OAS results and packaged bytes. | Step 2 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStoreTest.java` | Existing `execution/ProcessCancellationReceiptStoreTest` fake `DataSource`/JDBC binding captures SQL and parameters without starting PostgreSQL; `ProcessApiSchemaVerifierTest#resource` reads packaged UTF-8 resources. Existing V9 establishes immutable audit precedent. | `ProcessWorkerRecoveryReceiptStoreTest#requiresImmutableRecoveryMigrationResource`, `rejectsWrongOrAmbiguousAuditAndReplaysExactStoredResult`, `probesAcceptedRestoreByExactJobAndOwner`, `insertsExactEvidenceAndDistinguishesTargetConflict`, `requiresVerifiedSameSourceTransaction`, `readsPlainStartOnlyForExactOwner`. | Pin the V10 schema contract and exact audit-write/replay behavior before changing storage. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java` | `checkHistory(List<Map<String,Object>>)`, `history`, `checksum`, `Catalog`, `verifyCancellation`, `full_case_activation_retains_its_three_table_write_grants` already simulate history, catalog and grant drift via fake JDBC. | Rename `history_accepts_only_none_exact_v8_or_exact_v8_followed_by_v9` to `history_accepts_only_exact_v8_v9_v10_prefixes`; add `v10_keeps_existing_readiness_without_recovery_grants`, `recovery_requires_exact_v10_catalog_and_role`, `recovery_verifier_pins_the_frozen_v10_resource_identity`. | Fix supported migration history and independent recovery privilege checks in finite fake catalog tests. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql` | V1/V2/V3 generic receipt and definition PK/JSON contracts; V8 and V9 immutable history; V9 physical audit key and `ProcessApiSchemaVerifier#tableContracts` named constraint/index parsing. The existing generic command kind storage has no closed kind CHECK. V2 already enforces decision JSON only for DECISION. | `tianji_process_worker_recovery_receipt`, `tianji_process_worker_recovery_receipt_pkey`, `tianji_process_worker_recovery_command_fk`, `tianji_process_worker_recovery_start_fk`, `tianji_process_worker_recovery_definition_fk`, `tianji_process_worker_recovery_job_uk`. | Add the empty, immutable accepted-decision relation in the existing owned ProcessDB schema. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java` | `startByInstance` does a plain owner read but omits command ID. `lockStartByInstance` already reads the full `TaskOrigin` under the exact tuple and owns the write-side lock. `taskOrigin` has wider task approver semantics and cannot substitute for owner recovery. | `Kind.PROCESS_WORKER_RECOVER`, `public TaskOrigin readStartByInstance(ProcessActor actor, String instanceId)`, existing `lockStartByInstance` and `TaskOrigin`. | Enable one new generic receipt kind and an exact, plain read of the full applied START evidence for read-only recovery observation. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStore.java` | `ProcessCancellationReceiptStore#usesDataSource,verifyStorage,find,insert,current,requireTransaction` demonstrate a scoped, caller-transaction-bound store. `ProcessCommandReceiptStore#apply` enforces affected row count 1. Recovery uses no raw native table write, cancellation role, standalone transaction or alternate data source. | `ProcessWorkerRecoveryReceiptStore(DataSource source, String schema)`, `boolean usesDataSource(DataSource candidate)`, `void verifyStorage()`, `RecoverProcessWorkerResult find(ProcessActor actor, TaskOrigin start, Receipt receipt, RecoverProcessWorkerCommand command, String requestHash)`, `boolean hasAccepted(TaskOrigin start, String jobId)`, `Instant decisionTime()`, `boolean insert(Entry entry)`, immutable `Entry`. | Validate, insert and replay one accepted immutable recovery audit in the existing owned JDBC transaction. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java` | Existing `laterMigrations()` uses `ORDER BY installed_rank` and exact V8/V9 version/type/script/success/checksum. Existing byte-pinned migration loaders, V9 table parser and immutable role branch cover required catalog shapes. Existing `verifyWorker()` and the source-verified Worker instance remain separate prerequisites. | `RECOVERY_FILE`, `RECOVERY_SHA256`, `RECOVERY_RECEIPT`, `public void verifyWorkerRecovery()`, `recoveryMigration()`, existing `laterMigrations()`, `hasCaseMigration()`, `verifyCancellation()`, `checksum(String)`, `tableContracts(String, Map<String,Integer>)` and `verifyCaseColumns/Checks/Keys/Indexes/Role`. | Accept the exact new known history prefix in prior readiness and independently verify V10's immutable table, key/index and effective privileges before recovery activation. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-recovery-storage.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Record actual V10 immutability, audit and verifier evidence. | Step 3 | REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecificationTest.java` | `ProcessControlServiceTest` uses JUnit 5 and fixed actor, native and UTC fixtures. `FlowableWorkerServiceTest` verifies the existing V4 failure hash and Worker binding branches. Accepted Spec §7.3.2 and pinned recovery worker-failure evidence define the complete fixture matrix. | `WorkerRecoverySpecificationTest` with `missingSpecificationSeam` (temporary reflection gate), `dispositionPrecedenceAndReportedFailureAreIndependent`, `admitsExactlyOneExhaustedReadOnlyOccurrence`, `rejectsIncompleteContradictoryAndUnorderedHistory`, `versionBindsExactOwnerNativeAndEvidenceButNotClock`. | Fix the closed decision table and semantic version before implementing recovery. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryServiceTest.java` | `ProcessControlServiceTest` mocks transaction manager/store/native services and exercises command order and rollback; `FlowableWorkerServiceTest` supplies real registry plus mocked native/release fixtures. | `ProcessWorkerRecoveryServiceTest` with temporary `missingServiceSeam`, then `readsOneBoundedRepeatableSnapshot`, `rejectsEachCandidateOverflowButKeepsPerJobHistoryOverflowManual`, `provesBindingUsingCurrentExactReporterAndWorkerInputDigest`, `readsV4SqlWithoutSecretsOrPrematureStateFilters`, `restoresWithExactLocksReadbackAndAudit`, `replaysExactCommandBeforeAnyNativeRead`, `rechecksWinnerAndRejectsConflictingOrPendingReplay`, `rollsBackBlockedStaleAndEveryPostMoveFailure`, `classifiesOnlyRecognizedRacesAfterRollback`. | Freeze the Query/Command entry points, same-source transaction order, replay, bounded reads and failure ownership. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java` | Existing class uses mocked `JdbcTemplate`, `RowMapper` and `ResultSet`, fake Spring resource binding and mock Flowable services. Pinned DDL/mappers and native-lock evidence document the actual tables and command flush. | Existing `ProcessControlNativeAccessTest`; add `observesAllScopedKindsWithSentinel`, `locksOnlyExactOrdinaryDeadletter`, `comparesFailureCodeWithoutReadingExceptionText`, `probesAllRuntimeTablesById`, `requiresExactFlushedExternalReadback`. | Verify actual SQL projection, scopes, bounds, locks, collision detection and native move readback at the seam that owns them. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessWorkerRecoveryBlockedException.java` | `ProcessCancellationBlockedException` provides the existing dedicated no-arg runtime exception pattern; recovery has a distinct fixed code in accepted Spec §9.2. | `ProcessWorkerRecoveryBlockedException`, public no-arg constructor. | Carry one fixed safe recovery refusal across rollback to the API error adapter. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java` | Constructor checks exact configured DataSource/proxy/TM/schema/prefix/history/services, initializes five-second JDBC timeout; `readRoot(Scope, boolean)`, `requireTransaction()`, `now()` and execution affinity are live reusable seams. Pinned Flowable 8 DDL and evidence show no persisted deadletter retry/lock fields, BPMN process fields with nullable native scope, exact Job move and post-flush external table. | `verifyRecoveryTransaction()`, `readRecoveryRoot(TaskOrigin)`, `recoveryHistoryDefinitionId(TaskOrigin)`, `findRecoveryDeadletters(TaskOrigin)`, `lockRecoveryDeadletter(TaskOrigin, Definition, String)`, `readRecoveryExecution(TaskOrigin, Definition, RecoveryDeadletter)`, `hasRecoveryCollision(String)`, `recoveryFailureCodeMatches(RecoveryDeadletter, String)`, `moveAndVerifyRecovery(RecoveryDeadletter)`; immutable `RecoveryDeadletter` and `RecoveryExecution` facts. | Own bounded source-bound native facts, fences and post-move proof for recovery. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerLeaseStore.java` | Current store verifies DataSource/TM/schema and already binds application SQL to the actual owning transaction. V4 has `UNIQUE(tenant_id,job_id,native_owner)`, distinct LEASED-only index, and exact nullable state rules. Token-scoped `find/lock/finish` return the full token-bearing `Lease` and cannot serve the owner history read. | `findFailureJobIds(long tenantId, String instanceId)`, `readRecoveryHistory(long tenantId, String jobId)`, immutable `RecoveryLeaseEvidence`. | Read bounded, secret-free V4 candidate IDs and full same-job recovery history in the existing source-bound store. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerTopicRegistry.java` | Constructor indexes registrations by tenant, verified issuer fingerprint, exact subject, topic and schema. `require` enforces verified SERVICE and uses that key. Per-reporter concurrency/duration can differ even for a shared model schema. | Package-local `findRecoveryRegistration(long tenantId, String issuerFingerprint, String subjectId, String topic, String schemaVersion)`, existing `Key` and `require(ProcessActor, String, String)`. | Reuse the exact current registration key for evidence without granting another actor’s Worker authority. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java` | `acquire` computes `worker-occurrence-v1` and `worker-input-v2`; `verifyJob` checks exact model, topic, release, business key, current START correlation, linked-wait mode, DMN/mapping validation and mapped exact execution variables. `WorkerLeaseStore` and its manager already belong to this service. | `usesRecoveryStorage(SpringProcessEngineConfiguration, DataSource)`, `findFailureJobIds(long, String)`, `readRecoveryHistory(long, String)`, `readRecoveryProvenance(TaskOrigin, Definition, RecoveryDeadletter)`, `verifyRecoveryBinding(TaskOrigin, Definition, RecoveryDeadletter, RecoveryProvenance, RecoveryLeaseEvidence)`, existing `verifyJob`, `inputHash`, existing occurrence-key derivation and immutable binding projection. | Verify the current released ordinary Worker binding and reconstruct the original input digest without acquiring, reporting or executing a task. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecification.java` | `ProcessRequestDigest.hash(List<String>, Map<String,Object>)` delegates to current length-delimited hashing. V4 shape comes from immutable V4 SQL and `FlowableWorkerService.fail`; the full canonical decision table and digest fields are accepted Spec §7.3.2. | Package-local `WorkerRecoverySpecification`, `evaluate(Facts)`, immutable nested `Facts` / `Decision`, `READ_ONLY_EXHAUSTED_RETRY_V1`, shared exact evidence and version framing. | Apply one composed Specification to scoped technical facts for both observation and locked admission. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryService.java` | `ProcessControlService` owns explicit transaction templates, fixed timeouts, START/root fences, exact release discovery and safe log/classifier conventions. `ProcessCommandReceiptStore.insert/lockIfPresent/lock/apply` already implement current command storage; `apply` enforces one-row transition. Existing `TianjiProcessAccess.require` and permission guard remain the authority-producing boundary in Step 5. | `ProcessWorkerRecoveryService`, `workerFailures(ProcessActor, GetProcessInstanceQuery)`, `recoverWorker(ProcessActor, RecoverProcessWorkerCommand)`, private shared decision, exact replay and rollback classification. | Own the two bounded transactions, exact replay and atomic one-job restoration. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-recovery-service.md` | Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions. | Exact reviewed design/plan identity or actual source evidence for this Step. | Record actual native and shared eligibility finite proof. | Step 4 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryControllerTest.java` | `ProcessControlControllerTest` uses standalone MockMvc, current `ProcessRequestBodyAdvice` and `ProcessApiErrors` with no listener. `TianjiProcessAccess.require(String)` consumes an API resource key, not a varargs permission list. | `ProcessWorkerRecoveryControllerTest`, `recovery_controller_is_present`, exact methods/annotations, strict request cap/body, affinity and permission cases. | Test the new guarded provider wire path before introducing route behavior. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java` | Existing cancellation guard test directly invokes `workerWebBoundaryGuard(MockEnvironment,ApplicationContext).afterSingletonsInstantiated()` with mock WebApplicationContext. | Existing class, new `worker_recovery_requires_api_worker_and_servlet`, YAML flag/scope test. | Lock fail-closed feature prerequisites and both default-off keys/mapping without starting an application. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryController.java` | Current `ProcessControlController` route, annotations, `access.require(resourceKey)`, no-store and path/body affinity pattern. | `ProcessWorkerRecoveryController`, exact constructor, `failures`, `recover`, private `noStore`. | Expose two separate closed recovery operations through existing MVC/IAM wiring. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java` | Existing Jackson3 `STRICT_PROCESS_CANCEL_JSON`, exact key/string validation, `readNBytes(max+1)`, TooLarge. | `supports`, `beforeBodyRead`, `MAX_PROCESS_WORKER_RECOVERY_BYTES`, private `validateProcessWorkerRecovery`. | Enforce the exact five-string bounded recovery body while preserving all current command admission. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java` | Existing explicit `ProcessVersionConflictException`, `TooLarge` and no-store safe `OrchestrationErrorDTO` handlers. | New `@ExceptionHandler(ProcessWorkerRecoveryBlockedException.class)` method; existing `error(HttpStatus,String)`. | Map only the new typed blocked exception to its declared safe envelope. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` | Existing `@PermissionDefinition/@PermissionMessage/@LocalizedText`, Control API/scope aliases and existing tianji-process-java producer. | `WORKER_RECOVER`, `WORKER_RECOVER_SCOPE`, `WORKER_FAILURES_API`, `WORKER_RECOVERIES_API`; one PermissionDefinition plus en-US labels. | Declare one new permission and two exact recovery resource/scope aliases for static artifact generation. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java` | Same current guard already requires enabled servlet API for Worker/cancel/case/human features. | Existing `workerWebBoundaryGuard(Environment,ApplicationContext)` only. | Reject unsafe recovery flag combinations at the existing web boundary guard. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java` | `WorkerConfiguration` line375 and `CancellationConfiguration` line426, `processConfiguration`, `caseDataSource`, existing IAM filterchain and wildcard endpoint auth classification. | Nested `WorkerRecoveryConfiguration`, `@Bean("processWorkerRecoveryService")`, exact factory participants. | Wire one verified source-owned recovery service beside the existing Worker/cancellation configurations. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Sole PS application.yml has cancellation.enabled:false and TIANJI_PROCESS_CANCEL mapping at current lines56/71. | `stellar.tianji.process.worker-recovery.enabled`, existing user-scope-policy `TIANJI_PROCESS_WORKER_RECOVER` mapping. | Add explicit default false and only the new USER permission-to-scope mapping. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |
| CREATE | `docs/egon/evidence/2026-11-05-worker-recovery-provider.md` | Existing cancellation artifact evidence distinguishes LOCAL_UNCOMMITTED packaging from live IAM/runtime. | Actual RED/A/typedRED/GREEN, clean artifact manifest, YAML bytes, requirement and checks closure. | Close exact current Step and aggregate source conformance with actual finite and packaged evidence. | Step 5 | REQ-001/REQ-002/REQ-003/REQ-004/REQ-005/REQ-006/REQ-007 | Root exact gates |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Independent service Git repo current clean 415a281; outer workspace not Git. User AGENTS apply, particularly only necessary source, no automatic runtime and immutable Flyway. Verify exact current tracked hashes, immutable25SQL and Step-owned paths before each GO. Preserve unrelated local work. Scope is C/CL/PS and declared governance evidence only.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| Java25 | /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home | Explicit JAVA_HOME | Compile/test only |
| Maven | ./mvnw for isolated C/CL; scripts/verify-source-reactor.sh for PS/aggregate | Cached dependencies and actual adjacent source modules | Offline named tests |
| Settings | /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml | Credential-free local repo, no global change | Offline source |
| Runtime | User-owned later | No engine/PG/provider/listener/app/browser/opt-ins, no grants | Unexecuted |

### 6.3 Immutable constraints and approved decisions

All25 old SQL byte-identical; exactly one V10 prepared off-repo then immutable from creation. Old source authority/wire and migration prefix preserve behavior. Schema rollout compatible binary first, no automatic migration. New capability default off; no native DML. Source uses exact current technical profile, no dependency or production business model.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | Governance Spec/Plan and one EVD per Step are declared alongside source; exact old native/config tests get scoped new assertions | Primary§8/14 and existing test owner classes | Needed review/verification only, no new behavior or runtime surface | Re-lock paths before edits |
| PLAN-CLAR-002 | Optional tenant assertion stays provider/OAS; existing SDK has no tenant-supplier parameter | HttpProcessClient actual ctor | No new SDK ABI/request identity source | Preserve old and new exact methods |
| PLAN-CLAR-003 | Storage insert returns false on targeted job conflict, then service immediately throws typed blocked before APPLY in same TX | Primary§7/11 and Step dependency | No native/accepted outcome changes, compile order remains acyclic | Test rollback request/zero rows |
| PLAN-CLAR-004 | Actual factory beside CancellationConfiguration in Security; web guard in Storage | Current lines375/426 and Storage59 | Own source verification and flag behavior unchanged | Exact wiring review |
| PLAN-CLAR-005 | For manual disposition precedence, retained final failure evidence includes a known final UNKNOWN/BUSINESS_REJECTED row even when not eligible | Primary §7.3.2 ordered reasons and V4 classification | HISTORY_INCOMPLETE only when missing/over-cap, not when final negative facts are proved; restore policy unchanged | Assert explicit manual reason and reportedFailure |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Establish closed neutral recovery contracts

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007
- Dependencies: Current exact clean baseline, approved Spec/Plan and Root source GO
- Baseline state: No recovery capability, service clean with existing Control/Worker behavior.
- Observable outcome: Establish closed neutral recovery contracts, with current contract and old behavior preserved.
- End state: Exact 9 paths; real finite assertion gates and strict/source/SQL checks, one semantic commit.
- Test-first gate: Required — expected missing type/resource/policy assertion RED, compile A, typed behavior RED, minimal B and GREEN. Failures count only as actual intended JUnit F with E0/S0.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — independently close each scoped row, Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryContractTest.java`

- Purpose: Freeze the public surface and supply independently observed constructor RED before behavior changes.
- Symbols: `recovery_capability_and_records_have_exact_shape`, `command_rejects_invalid_fields_and_preserves_exact_safe_reason`, `failure_row_enforces_closed_dispositions_and_safe_activity`, `observation_enforces_unsigned_utf8_order_uniqueness_cap_and_copy`, `result_enforces_exact_accepted_scheduling_identity`.
- Repository evidence: `ProcessControlContractTest` and `OrchestrationContractTest` use JUnit assertions in this package; same-package records have public canonical constructors and annotation-free fields.
- Dependencies and consumers: Reflection initially uses package-qualified class names and existing `GetProcessInstanceQuery`; typed pass imports the five new types after A. Old Control fixtures stay in their original class.
- Why now: The first RED compiles on the baseline and proves absence, while typed RED proves actual behavior independently of structural compilation.
- Contract/signature changes: Test the exact two declared method signatures, record component names/types/order, and named enum membership. Assert recovery stays separate from `ProcessControlClient`.
- Input/output and state mapping: Explicit fixture strings, enum constants, defensive-copy mutable input list, and exact UTC Instants only.
- Error and edge behavior: Exercise null/blank/C0/DEL/lone high/lone low, safe supplementary and exact UTF-16 boundaries, leading/trailing ASCII space, C1 inside valid text, embedded Unicode exact equality and redaction; reject unsafe dot path, wrong hash/command pattern, every invalid status-row invariant, unordered/duplicate/oversized/nullable list and wrong result affinity, retry count or missing required value.
- Standards impact: `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-TEST-001` exercise neutral compact validation, enum names and exact Instant.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` through JUnit/JDK only, record reflection, exact values, and separate L0 capability.
- Implementation pseudocode:

```java
@Test void recovery_capability_and_records_have_exact_shape() throws Exception {
    var capability = assertDoesNotThrow(() -> Class.forName(GetProcessInstanceQuery.class.getPackageName() + ".ProcessWorkerRecoveryClient"));
    assertNotNull(capability);
    assertEquals(Set.of("workerFailures", "recoverWorker"), declaredMethodNames(capability));
    assertEquals("ProcessWorkerFailuresDTO", capability.getDeclaredMethod("workerFailures", GetProcessInstanceQuery.class).getReturnType().getSimpleName());
    var command = Class.forName(capability.getPackageName() + ".RecoverProcessWorkerCommand");
    var row = Class.forName(capability.getPackageName() + ".ProcessWorkerFailureDTO");
    var snapshot = Class.forName(capability.getPackageName() + ".ProcessWorkerFailuresDTO");
    var result = Class.forName(capability.getPackageName() + ".RecoverProcessWorkerResult");
    assertEquals(result, capability.getDeclaredMethod("recoverWorker", command).getReturnType());
    assertRecordShape(command, "instanceId", "jobId", "commandId", "expectedVersion", "reason");
    assertRecordShape(row, "jobId", "activityId", "nativeDeadletter", "status", "reason", "reportedFailure", "expectedVersion");
    assertRecordShape(snapshot, "instanceId", "observedAt", "failures");
    assertRecordShape(result, "instanceId", "jobId", "occurrenceId", "commandId", "status", "retries", "decidedAt");
    // Assert each component type from Files 2–5 and exact nested Enum.name() sets.
    assertFalse(ProcessControlClient.class.isAssignableFrom(capability));
}
// Add typed methods after Pass A compiles.
assertThrows(IllegalArgumentException.class, () -> command(".", "job", commandId, version, "safe"));
assertEquals(exactReason, command("process", "job", commandId, version, exactReason).reason());
assertEquals("RecoverProcessWorkerCommand[redacted]", command("process", "job", commandId, version, exactReason).toString());
assertThrows(IllegalArgumentException.class, () -> row("j", null, true, RETRY_AVAILABLE, NONE, RETRYABLE, version));
assertThrows(IllegalArgumentException.class, () -> row("j", "act", true, MANUAL_REVIEW, NO_NATIVE_DEADLETTER, UNVERIFIED, null));
assertThrows(IllegalArgumentException.class, () -> row("j", null, false, HISTORICAL_FAILURE, UNKNOWN_OUTCOME, BUSINESS_REJECTED, null));
var input = new ArrayList<>(List.of(rowFor("\uE000"), rowFor("\uD800\uDC00")));
var observation = new ProcessWorkerFailuresDTO("process", observationTime, input);
input.clear();
assertEquals(2, observation.failures().size());
assertThrows(UnsupportedOperationException.class, () -> observation.failures().clear());
assertThrows(IllegalArgumentException.class, () -> observationWithRowsInReverseOrderOrDuplicateJob());
assertThrows(IllegalArgumentException.class, () -> observationWith129DistinctSortedJobs());
assertEquals(128, observationWith128DistinctSortedJobs().failures().size());
assertTrue(new ProcessWorkerFailuresDTO("process", observationTime, List.of()).failures().isEmpty());
assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
        "process", "job", "another", commandId, RETRY_SCHEDULED, 1, decisionTime));
assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
        "process", "job", "job", commandId, RETRY_SCHEDULED, 2, decisionTime));
```

- Verification contribution: Reflection method only produces missing-type RED and later shape GREEN; the whole class produces behavioral RED after Pass A and GREEN after B.
- After this file: First invocation fails by an assertion wrapping the missing capability ClassNotFoundException and the baseline production build remains compilable. The second visit adds typed assertions and leaves at least the explicit invalid command/row/result checks RED.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerCommand.java`

- Purpose: Carry the exact caller-chosen recovery target, replay key, version and reason.
- Symbols: `RecoverProcessWorkerCommand(String instanceId, String jobId, String commandId, String expectedVersion, String reason)`, compact constructor, `toString()`.
- Repository evidence: `CancelProcessCommand` already redacts its representation and uses `OrchestrationChecks.id/command/hash`; `GetProcessInstanceQuery` owns dot-segment rejection.
- Dependencies and consumers: New capability, CL request encoder, PS strict body advice and command hash; reuses existing same-package validators.
- Why now: Independent leaf record establishes the typed POST input before the capability.
- Contract/signature changes: Exact five String record components, all nonnull, and constant redacted representation.
- Input/output and state mapping: Validate and preserve every original string. Use the existing path ID bound 255 plus dot rejection, job safe-ID bound 255, existing exact ASCII command syntax, 64 lowercase hex, and safe reason bound 512 Java UTF-16 units. Exact input flows into replay hash in PS.
- Error and edge behavior: Existing `id` uses `isBlank()`, equality with `trim()`, C0/DEL exclusion and paired-surrogate validation. Preserve legitimate C1, supplementary scalars, original case and spacing inside a valid reason. A generic constructor error carries no input.
- Standards impact: `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-UTIL-001`, `MC-JSON-001` reuse strict C validators and direct immutable construction under §6.2.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 11` as a neutral immutable Command record with exact string fields.
- Implementation pseudocode:

```java
// A: record shape and constant redaction. B: add compact validation.
public record RecoverProcessWorkerCommand(String instanceId, String jobId, String commandId,
                                          String expectedVersion, String reason) {
    public RecoverProcessWorkerCommand {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        jobId = OrchestrationChecks.id(jobId, 255);
        commandId = OrchestrationChecks.command(commandId);
        expectedVersion = OrchestrationChecks.hash(expectedVersion);
        reason = OrchestrationChecks.id(reason, 512);
    }
    @Override public String toString() { return "RecoverProcessWorkerCommand[redacted]"; }
}
```

- Verification contribution: Typed constructor field/length/path/preservation/redaction cases.
- After this file: A supplies five components; B makes command validation GREEN.

#### File 3 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailureDTO.java`

- Purpose: Validate a single scoped failure observation independently of provider eligibility computation.
- Symbols: `ProcessWorkerFailureDTO(String jobId, String activityId, boolean nativeDeadletter, StatusEnum status, ReasonEnum reason, ReportedFailureEnum reportedFailure, String expectedVersion)`; three nested enum types and compact constructor.
- Repository evidence: Existing `ProcessActiveWaitDTO` and `ProcessControlSnapshotDTO` enforce constructor invariants and exact enum facts without adding runtime dependencies.
- Dependencies and consumers: `OrchestrationChecks`, observation list, CL strict codec, PS Specification and bounded projection.
- Why now: The aggregate record in File 4 requires the row type and can delegate row invariants here.
- Contract/signature changes: Nested `StatusEnum` values `RETRY_AVAILABLE, MANUAL_REVIEW, HISTORICAL_FAILURE`; `ReasonEnum` values `NONE, NO_NATIVE_DEADLETTER, UNKNOWN_OUTCOME, BUSINESS_REJECTED, RESTORE_LIMIT_REACHED, UNSUPPORTED_NATIVE_JOB, PROCESS_INACTIVE, EVIDENCE_CONFLICT, ACTIVE_LEASE, BINDING_UNVERIFIED, HISTORY_INCOMPLETE, UNSUPPORTED_EFFECT`; `ReportedFailureEnum` values `NONE, RETRYABLE, BUSINESS_REJECTED, UNKNOWN, UNVERIFIED`.
- Input/output and state mapping: Safe 255-unit job ID, safe nullable 1–128-unit activity ID, required native boolean and enum values. Nullable opaque lower-hex version is present only in the retryable row. Reported failure remains independently explainable subject to fixed reason affinity.
- Error and edge behavior: No `PERMANENT` or ordinal; invalid null, reason/status/report combinations and invalid textual facts fail locally without payload.
- Standards impact: `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-JSON-001`; close transport facts and enum names under the neutral annotation-free contract decision.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 11`; immutable DTO, native boolean, fixed state checks and stable named string codes.
- Implementation pseudocode:

```java
// A: declare record components and enum constants. B: compact constructor.
jobId = OrchestrationChecks.id(jobId, 255);
if (activityId != null) activityId = OrchestrationChecks.id(activityId, 128);
Objects.requireNonNull(status, "status");
Objects.requireNonNull(reason, "reason");
Objects.requireNonNull(reportedFailure, "reportedFailure");
if (reason == UNKNOWN_OUTCOME && reportedFailure != UNKNOWN
        || reason == ReasonEnum.BUSINESS_REJECTED && reportedFailure != ReportedFailureEnum.BUSINESS_REJECTED) {
    throw OrchestrationChecks.invalid();
}
switch (status) {
    case RETRY_AVAILABLE -> {
        if (!nativeDeadletter || activityId == null || reason != NONE || reportedFailure != RETRYABLE)
            throw OrchestrationChecks.invalid();
        expectedVersion = OrchestrationChecks.hash(expectedVersion);
    }
    case MANUAL_REVIEW -> {
        if (!nativeDeadletter || expectedVersion != null || reason == NONE || reason == NO_NATIVE_DEADLETTER)
            throw OrchestrationChecks.invalid();
    }
    case HISTORICAL_FAILURE -> {
        if (nativeDeadletter || expectedVersion != null || !EnumSet.of(NO_NATIVE_DEADLETTER,
                UNKNOWN_OUTCOME, ReasonEnum.BUSINESS_REJECTED, HISTORY_INCOMPLETE).contains(reason))
            throw OrchestrationChecks.invalid();
    }
}
```

- Verification contribution: Typed tests exhaust reason membership per status and assert bad activity, version, native flag and reported failure.
- After this file: A exposes the exact DTO/enum shape; B closes row invariants for all legal dispositions.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailuresDTO.java`

- Purpose: Return one complete bounded observed list for the exact process.
- Symbols: `ProcessWorkerFailuresDTO(String instanceId, Instant observedAt, List<ProcessWorkerFailureDTO> failures)`, compact constructor.
- Repository evidence: `ProcessControlSnapshotDTO` validates order and copies values; `HttpProcessClient#compareTaskIds` already uses unsigned UTF-8 byte comparison for stable client order.
- Dependencies and consumers: File 3 row, `GetProcessInstanceQuery`, JDK `Instant/List/Arrays/StandardCharsets`, CL decoder and PS read.
- Why now: Requires the row type and provides GET result before the capability is declared.
- Contract/signature changes: All three components required; exactly one observation instant and immutable list, zero to 128 rows.
- Input/output and state mapping: Preserve given row order and exact IDs. Require strict increasing unsigned UTF-8 encoded job ID order and reject duplicates by equality; copy the input list defensively.
- Error and edge behavior: Reject invalid instance IDs, null time/list/entry, 129th row and duplicate/unsorted IDs. Empty list is a valid complete observation, and no order auto-correction occurs.
- Standards impact: `MC-MODEL-001`, `MC-VALID-001`, `MC-UTIL-001`, `MC-TIME-001` with defensive copy and native `Instant`.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 10`, `Rule 11` through immutable DTO and exact JDK byte/time semantics.
- Implementation pseudocode:

```java
// A: record shape. B: compact constructor.
instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
observedAt = Objects.requireNonNull(observedAt, "observedAt");
if (failures == null || failures.size() > 128) throw OrchestrationChecks.invalid();
failures = List.copyOf(failures);
byte[] previous = null;
for (var row : failures) {
    var next = row.jobId().getBytes(StandardCharsets.UTF_8);
    if (previous != null && Arrays.compareUnsigned(previous, next) >= 0) throw OrchestrationChecks.invalid();
    previous = next;
}
```

- Verification contribution: Typed tests use BMP U+E000 and supplementary U+10000 to distinguish Java String order from unsigned UTF-8; assert copy, cap, order and null checks.
- After this file: A supplies the GET result shape; B preserves complete ordered output and rejects invalid input before callers consume it.

#### File 5 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerResult.java`

- Purpose: Express one accepted scheduling decision and replay result.
- Symbols: `RecoverProcessWorkerResult(String instanceId, String jobId, String occurrenceId, String commandId, StatusEnum status, int retries, Instant decidedAt)` and nested `StatusEnum.RETRY_SCHEDULED`.
- Repository evidence: `CancelProcessResult` already composes compact affinity/state validation and java.time; recovery has its own fixed decision.
- Dependencies and consumers: Existing C ID/command validation, `GetProcessInstanceQuery`, new capability, CL decoder and PS audit construction.
- Why now: Independent POST leaf return type required for the final interface.
- Contract/signature changes: Seven components required, `status=RETRY_SCHEDULED`, `retries=1`, exact job/occurrence equality and nonnull `decidedAt`.
- Input/output and state mapping: Preserve exact accepted instance, job, occurrence, replay command ID and original decision time. Status is a closed string on wire and retries an integral primitive.
- Error and edge behavior: Reject null/unsafe IDs, mismatched occurrence, bad command ID, non-one retries and null status/time. No inference of Worker execution completion.
- Standards impact: `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-JSON-001`, `MC-TIME-001` apply to the direct immutable decision projection.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11`; neutral result and closed status with JDK `Instant`.
- Implementation pseudocode:

```java
// A: record and enum shapes. B: compact constructor.
instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
jobId = OrchestrationChecks.id(jobId, 255);
occurrenceId = OrchestrationChecks.id(occurrenceId, 255);
commandId = OrchestrationChecks.command(commandId);
decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
if (!jobId.equals(occurrenceId) || status != StatusEnum.RETRY_SCHEDULED || retries != 1)
    throw OrchestrationChecks.invalid();
```

- Verification contribution: Typed exact identity and result invariant assertions.
- After this file: A completes the leaf result shape; B makes scheduling result validation GREEN.

#### File 6 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryClient.java`

- Purpose: Publish the independent neutral L0 Query/Command capability.
- Symbols: `ProcessWorkerRecoveryClient#workerFailures(GetProcessInstanceQuery)`, `#recoverWorker(RecoverProcessWorkerCommand)`, `WORKER_FAILURES_PATH`, `WORKER_RECOVERIES_PATH`, `WORKER_FAILURES_RESOURCE_KEY`, `WORKER_RECOVERIES_RESOURCE_KEY`, `WORKER_RECOVER_SCOPE`.
- Repository evidence: `ProcessControlClient` publishes explicit path/scope/resource constants and separate typed methods.
- Dependencies and consumers: Files 2/4/5 and existing Query; Step 2 `HttpProcessClient`; C has no transport, Jackson or provider implementation.
- Why now: All method arguments/returns exist so A can compile and reflection can validate the exact operation surface.
- Contract/signature changes: Two methods below, shared constants mirror Spec §9 and §15. No default bridge, inheritance, tenant/actor parameter or Event.
- Input/output and state mapping: Query to bounded DTO; Command to exact accepted Result. HTTP identity remains trusted downstream transport.
- Error and edge behavior: Invalid DTO cannot be constructed in ordinary callers after Pass B. Operation authorization and exception translation belong to the consumer transport/provider.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-NAME-001`, `MC-DEP-001`, `MC-SCOPE-001` keep C neutral and preserve independent Control.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 11`; expose typed CQE inputs and results, with no new business layer.
- Implementation pseudocode:

```java
public interface ProcessWorkerRecoveryClient {
    String WORKER_FAILURES_PATH = "/api/tianji/v1/process-instances/{instanceId}/worker-failures";
    String WORKER_RECOVERIES_PATH = "/api/tianji/v1/process-instances/{instanceId}/worker-recoveries";
    String WORKER_FAILURES_RESOURCE_KEY = "tianji.api.process.instance.worker-failures";
    String WORKER_RECOVERIES_RESOURCE_KEY = "tianji.api.process.instance.worker-recoveries";
    String WORKER_RECOVER_SCOPE = "tianji:process:worker-recover";
    ProcessWorkerFailuresDTO workerFailures(GetProcessInstanceQuery query);
    RecoverProcessWorkerResult recoverWorker(RecoverProcessWorkerCommand command);
}
```

- Verification contribution: Reflection asserts declared signatures and constant values plus no Control inheritance. B adds no change to this file.
- After this file: A now compiles and File 1 reflection is GREEN. After File 1 typed RED and B, the committed C boundary is available for CL and PS.

#### File 7 — `CREATE docs/egon/spec/2026-11-05-13-41-process-worker-recovery.md`

- Purpose: Publish the exact approved primary Spec with metadata-only Plan backlink.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

#### File 8 — `CREATE docs/egon/plan/2026-11-05-14-27-process-worker-recovery.md`

- Purpose: Publish this exact reviewed five-Step Plan.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

#### File 9 — `CREATE docs/egon/evidence/2026-11-05-worker-recovery-contract.md`

- Purpose: Record actual neutral contract checks.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact phases, final selected Step suites `ProcessWorkerRecoveryContractTest,ProcessControlContractTest,OrchestrationContractTest`.
- Expected result: Every owning report exists; current-Step GREEN zero failures/errors/skips. A compile or environmental failure is not RED.
- Failure returns to: Exact owning file/assertion; design drift returns to effective Spec, not weaker tests. V10 mismatch stops under immutable rules.
- Completion criteria: Current-Step named reports, strict docs, exact paths/staged review, unchanged old SQL/excluded hashes and separate manual/literal closure all pass.
- Rollback: Only focused revert/forward fix; no history rewrite or deletion/repair/edit of existing migration.
- Commit paths: `stellar-enclosure-tianji-contract/src/test/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryContractTest.java`; `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerCommand.java`; `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailureDTO.java`; `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerFailuresDTO.java`; `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/RecoverProcessWorkerResult.java`; `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessWorkerRecoveryClient.java`; `docs/egon/spec/2026-11-05-13-41-process-worker-recovery.md`; `docs/egon/plan/2026-11-05-14-27-process-worker-recovery.md`; `docs/egon/evidence/2026-11-05-worker-recovery-contract.md`
- Commit: `feat(tianji-contract): define Worker recovery capability`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing C/CL/PS technical vertical, exact named owner and no new layer. Actual EVD required; failed check blocks commit. |
| MC-REUSE-001 | Applicable | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. Actual EVD required; failed check blocks commit. |
| MC-DEP-001 | Applicable | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. Actual EVD required; failed check blocks commit. |
| MC-NAME-001 | Applicable | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. Actual EVD required; failed check blocks commit. |
| MC-VALID-001 | Applicable | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. Actual EVD required; failed check blocks commit. |
| MC-MODEL-001 | Applicable | Immutable record values and defensive copies; no mutable business entity. Actual EVD required; failed check blocks commit. |
| MC-CONVERT-001 | Applicable | Exact technical projection under approved profile; no cross-layer mapper or business entity. Actual EVD required; failed check blocks commit. |
| MC-LOG-001 | Not applicable | No logging path change in this Step; existing controls remain. Actual EVD required; failed check blocks commit. |
| MC-BEAN-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-UTIL-001 | Applicable | Current JDK/framework/digest helpers only. Actual EVD required; failed check blocks commit. |
| MC-JSON-001 | Applicable | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. Actual EVD required; failed check blocks commit. |
| MC-TIME-001 | Applicable | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. Actual EVD required; failed check blocks commit. |
| MC-CONFIG-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-PATTERN-001 | Not applicable | No new eligibility workflow in this Step; known shared policy belongs Step4. Actual EVD required; failed check blocks commit. |
| MC-SCOPE-001 | Applicable | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. Actual EVD required; failed check blocks commit. |
| MC-TEST-001 | Applicable | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. Actual EVD required; failed check blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. Actual EVD required; failed check blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Exact semantic type/DTO/Command/Result/Enum names. Record actual evidence or scoped N/A separately. |
| Rule 2 | Applicable | Strict actual boundary, authorized context and exact proof, existing technical validation. Record actual evidence or scoped N/A separately. |
| Rule 3 | Applicable | Immutable records and defensive copies; no entity conversion bypass. Record actual evidence or scoped N/A separately. |
| Rule 4 | Not applicable | No managed business class/logger/factory wiring in this Step; exact technical profile unchanged. Record actual evidence or scoped N/A separately. |
| Rule 5 | Applicable | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. Record actual evidence or scoped N/A separately. |
| Rule 6 | Applicable | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. Record actual evidence or scoped N/A separately. |
| Rule 7 | Not applicable | No profile/config key touched in this Step. Record actual evidence or scoped N/A separately. |
| Rule 9 | Not applicable | No new policy/complex runtime selection in this Step. Record actual evidence or scoped N/A separately. |
| Rule 10 | Applicable | Existing java.time/DB precision, decision/source time, no global clock or execution claim. Record actual evidence or scoped N/A separately. |
| Rule 11 | Applicable | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. Record actual evidence or scoped N/A separately. |

### Step 2 — Implement bounded SDK and package OpenAPI

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007
- Dependencies: Exact Step1 semantic commit and fresh Root current-Step lock
- Baseline state: Steps1–1 verified committed; current-Step behavior absent.
- Observable outcome: Implement bounded SDK and package OpenAPI, with current contract and old behavior preserved.
- End state: Exact 6 paths; real finite assertion gates and strict/source/SQL checks, one semantic commit.
- Test-first gate: Required — expected missing type/resource/policy assertion RED, compile A, typed behavior RED, minimal B and GREEN. Failures count only as actual intended JUnit F with E0/S0.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — independently close each scoped row, Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryHttpTest.java`

- Purpose: Specify SDK dispatch, one-send, body validation and error boundaries in an in-memory transport.
- Symbols: `recovery_capability_is_available`, `observes_exact_path_and_maps_closed_rows`, `posts_exact_command_once_and_explicit_replay_preserves_result`, `rejects_unsafe_transport_before_fetching_credentials`, `maps_only_the_documented_json_status_code_pairs`, `rejects_malformed_success_and_wrong_affinity`, `bounds_body_and_does_not_resend_on_transport_failure`; private fake `HttpClient`, reply and subscriber fixtures.
- Repository evidence: `HttpProcessControlClientTest#FakeHttpClient` is private and feeds a real `BodyHandler` through fake `HttpResponse.ResponseInfo` and chunked subscriber. Use the same bounded in-memory approach inside this exact new test path.
- Dependencies and consumers: Existing public `HttpProcessClient(URI,HttpClient,Supplier<String>,Duration)`, committed C capability, real `BoundedBodySubscriber`, JUnit, Jackson tree for request key checks.
- Why now: No CL implementation changes are needed to compile the typed fixture. An `assertInstanceOf` on the existing class yields the baseline's intended adapter assertion failure.
- Contract/signature changes: Test new operations only through the public neutral capability. Assert the existing constructor policy still rejects `Redirect.ALWAYS` and `Redirect.NORMAL`. Observe request timeout and optional-header absence in the existing constructor path.
- Input/output and state mapping: Fake records exact URI raw path, method, headers and body publisher bytes. GET uses no body; POST carries five string keys and exact reason, no tenant override. Replies travel through the real subscriber and codec; assert equality of exact replay plus UTC Instant.
- Error and edge behavior: Table all GET/POST allowed status/code pairs and conflicting status, code or operation; reject redirects, all other 2xx including 201/204, 415, wrong or missing media type, wrong field shape or affinity, extra/null/missing/wrong-typed fields, invalid enums, overflow, escaped unpaired surrogate, noncanonical UTC (offset, lowercase z, noncanonical fractional precision), duplicate keys and trailing JSON. Test timeout cancels pending future, interruption restores interrupt flag, synchronous dispatch/exceptional future map to fixed safe errors, and cap crosses 2 MiB through two chunks. Verify bearer/reason/foreign IDs/dependency body are absent from exceptions and one send per invocation. Separate explicit same-input replay is two caller invocations and two sends.
- Standards impact: `MC-VALID-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-REUSE-001`, `MC-TEST-001` test the genuine JDK transport boundary without opening sockets.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` through typed C fixtures, real SDK parsing and finite tests.
- Implementation pseudocode:

```java
@Test void recovery_capability_is_available() {
    var client = new HttpProcessClient(URI.create("https://process.example/context/"), fake, () -> "private-token", timeout);
    assertInstanceOf(ProcessWorkerRecoveryClient.class,client); // Missing-adapter assertion RED, not ClassCastException error.
}
@Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
        HttpResponse.BodyHandler<T> handler) {
    capture(request, request.bodyPublisher()); // Subscribe to publisher in memory.
    var reply = queuedReply.removeFirst();
    var subscriber = handler.apply(responseInfo(reply.status(), reply.headers()));
    subscriber.onSubscribe(trackingSubscription);
    reply.chunks().forEach(bytes -> subscriber.onNext(List.of(ByteBuffer.wrap(bytes))));
    subscriber.onComplete();
    return subscriber.getBody().toCompletableFuture().thenApply(body -> fakeResponse(reply, request, body));
}
assertEquals("/context/api/tianji/v1/process-instances/space%20%2F%2B/worker-failures", capturedRawPath());
assertRequest("GET", "Bearer private-token", "application/json", "no-store", timeout, emptyBody());
assertRequest("POST", "Bearer private-token", "application/json", "no-store", timeout, exactFiveFieldBody(command));
assertThrows(IllegalArgumentException.class, () -> clientFor("http://process.example").recoverWorker(command));
assertEquals(0, rejectedFixture.tokenCalls() + rejectedFixture.sendCalls());
assertSingleSendFor(recovery::workerFailures, boundedAndMalformedGetFixtures());
assertSingleSendFor(recovery::recoverWorker, boundedAndMalformedPostFixtures());
assertEquals(recovery.recoverWorker(command), recovery.recoverWorker(command));
```

- Verification contribution: Exact method selector gives missing-adapter assertion RED; File2/3 A exposes codec/client signatures and safely refuses, then typed suite must have actual intended F/E0/S0 before B. After B same exact suite proves one send, route/media/shape/error/replay GREEN.
- After this file: Existing CL production compiles. Recovery fake suite fails because `HttpProcessClient` has no new capability, with no provider or listener involved.

#### File 2 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryResponseCodec.java`

- Purpose: Decode the new operation family with strict shape and error-pair isolation.
- Symbols: Package-private `ProcessWorkerRecoveryResponseCodec`, `failures(byte[])`, `recovery(byte[])`, `errorCode(byte[], int, boolean)`, private strict tree, exact keys, text and UTC helpers.
- Repository evidence: `ProcessControlResponseCodec` uses `tools.jackson.core.JsonFactory`, `StreamReadConstraints/StreamReadFeature`, `tools.jackson.databind.JsonNode` and `json.JsonMapper`; `CaseResponseCodec` demonstrates operation-specific finite status allowlists. Existing CL POM supplies Jackson 3.
- Dependencies and consumers: C recovery records/enums, JDK UTF-8/`Instant`, File 3 `HttpProcessClient#call`; private decoder helpers remain in this codec.
- Why now: Strict parsing is a collaborator for the SDK entry points. File 1 has already demonstrated missing dispatch.
- Contract/signature changes: `ProcessWorkerFailuresDTO failures(byte[] body)`, `RecoverProcessWorkerResult recovery(byte[] body)`; `String errorCode(byte[] body, int status, boolean recoveryCommand)` returns an allowlisted literal or null for any mismatch.
- Input/output and state mapping: Decode exactly one UTF-8 JSON object with strict duplicate and trailing-token handling, separate exact field sets for observation/list row/result/error, explicit null for the two nullable row fields and correlation. Require real Boolean for `nativeDeadletter`, real integral node equal to 1 for retries and exact enum names. Use C constructors to close row invariants and list order. Check `Instant.parse(text).toString().equals(text)` to enforce UTC canonical text. The transport still verifies request/response affinity.
- Error and edge behavior: Reject empty/over-cap, malformed UTF-8, wrong token type, over-cap array, unknown JSON fields or scalar names, and absent/null required nonnullable fields. The error object must have only `code,correlationId`, with explicit null correlation. Allowlisted base pairs are 400 invalid command, 401 unauthenticated, 403 permission denied, 404 resource not found, 409 tenant context changed, 500 internal error, 503 dependency unavailable. POST alone also permits 409 idempotency conflict/version conflict/recovery blocked and 413 request too large. Each spelling is the exact `TIANJI_...` Spec §9.2 string. Exceptions have a fixed safe code and no parser cause/body.
- Standards impact: `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-CONVERT-001`, `MC-JSON-001`, `MC-TIME-001` reuse the current Jackson stack and constructor-based projection under Spec §6.2.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` as a package-local transport codec with exact immutable shapes, named enum codes and UTC instants.
- Implementation pseudocode:

```java
private static final Map<Integer, Set<String>> COMMON_ERRORS = Map.of(
        400, Set.of("TIANJI_INVALID_COMMAND"),
        401, Set.of("TIANJI_UNAUTHENTICATED"),
        403, Set.of("TIANJI_PERMISSION_DENIED"),
        404, Set.of("TIANJI_RESOURCE_NOT_FOUND"),
        409, Set.of("TIANJI_TENANT_CONTEXT_CHANGED"),
        500, Set.of("TIANJI_INTERNAL_ERROR"),
        503, Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
private static final Map<Integer, Set<String>> POST_ONLY_ERRORS = Map.of(
        409, Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED"),
        413, Set.of("TIANJI_REQUEST_TOO_LARGE"));
private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(1024)
                .maxNameLength(128).maxNumberLength(32).maxTokenCount(4096).build()).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
JsonNode tree(byte[] bytes) {
    // Bound 1..2_097_152 and decode with StandardCharsets.UTF_8.newDecoder() in REPORT mode.
    return exactObject(mapper.readTree(decodedUtf8)); // Map decode/parser failure to fixed invalid response.
}
ProcessWorkerFailuresDTO failures(byte[] bytes) {
    var node = exactKeys(tree(bytes), "instanceId", "observedAt", "failures");
    var failures = node.path("failures");
    require(failures.isArray() && failures.size() <= 128);
    for (var row : failures) {
        exactKeys(row, "jobId", "activityId", "nativeDeadletter", "status", "reason", "reportedFailure", "expectedVersion");
        require(row.path("nativeDeadletter").isBoolean());
        // text requires isString; nullableText permits only explicit null or String.
        rows.add(new ProcessWorkerFailureDTO(text(row, "jobId"), nullableText(row, "activityId"),
                row.path("nativeDeadletter").asBoolean(), StatusEnum.valueOf(text(row, "status")),
                ReasonEnum.valueOf(text(row, "reason")), ReportedFailureEnum.valueOf(text(row, "reportedFailure")),
                nullableText(row, "expectedVersion")));
    }
    return new ProcessWorkerFailuresDTO(text(node, "instanceId"), canonicalInstant(text(node, "observedAt")), rows);
}
RecoverProcessWorkerResult recovery(byte[] bytes) {
    var node = exactKeys(tree(bytes), "instanceId", "jobId", "occurrenceId", "commandId", "status", "retries", "decidedAt");
    require(node.path("retries").isIntegralNumber() && BigInteger.ONE.equals(node.path("retries").bigIntegerValue()));
    return new RecoverProcessWorkerResult(text(node, "instanceId"), text(node, "jobId"), text(node, "occurrenceId"),
            text(node, "commandId"), RecoverProcessWorkerResult.StatusEnum.valueOf(text(node, "status")),
            1, canonicalInstant(text(node, "decidedAt")));
}
String errorCode(byte[] bytes, int status, boolean recoveryCommand) {
    try {
        var node = exactKeys(tree(bytes), "code", "correlationId");
        if (!node.path("correlationId").isNull()) return null;
        String code = text(node, "code");
        if (COMMON_ERRORS.getOrDefault(status, Set.of()).contains(code)) return code;
        if (recoveryCommand && POST_ONLY_ERRORS.getOrDefault(status, Set.of()).contains(code)) return code;
        return null;
    } catch (RuntimeException malformed) {
        return null;
    }
}
```

- Verification contribution: File 1 fixture matrix exercises both typed decoders and per-operation error matrix through the transport; File 4 independently checks the documented enum/schema correspondence.
- After this file: Codec compiles against C. File 1 remains RED until the HTTP entry points connect.

#### File 3 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`

- Purpose: Connect the new neutral capability to existing bounded one-send transport.
- Symbols: `implements ProcessWorkerRecoveryClient`, `workerFailures(GetProcessInstanceQuery)`, `recoverWorker(RecoverProcessWorkerCommand)`, private recovery codec field, recovery-only branches in generic `call`.
- Repository evidence: `control` and `cancel` show strict decoder and request/response affinity. `call` owns `segment`, normalized base URI checks for Case/Task, bearer validation, `HttpClient.Redirect.NEVER`, one `sendAsync`, `BoundedBodySubscriber` and timeout cancellation.
- Dependencies and consumers: C records, File 2 codec, same injected JDK client and token supplier, existing safe `ProcessClientException`; direct C/CL consumers.
- Why now: Wires the tested shape and strict decoder into the only implementation file allowed to change for SDK dispatch.
- Contract/signature changes: Add the two public C methods. Preserve public constructor, legacy methods, legacy codec branches and status semantics. Use existing prefix/segment behavior and exactly `worker-failures` / `worker-recoveries` suffixes.
- Input/output and state mapping: Recovery-only type dispatch identifies both new return classes. Before token lookup require normalized base URI and HTTPS or literal loopback `http` host `localhost`, `127.0.0.1`, `[::1]` or `::1`; compare case-insensitively, without DNS-based trust. Reuse current bearer and timeout, set request `Cache-Control: no-store` for GET and POST. GET has no body or Content-Type; POST uses existing Jackson encoder, exact command, application/json and defensive 8192-byte ceiling. No tenant selector is added. Both require HTTP 200 and application/json media type, then exact expected IDs. Provider no-store response header is checked in the provider/OAS gates.
- Error and edge behavior: No retry or redirect path. Use the exact recovery error branch ahead of old error fallbacks; off-matrix status, malformed envelope and non-JSON media return `TIANJI_INVALID_RESPONSE` with safe status. Map synchronous `sendAsync` runtime failure to fixed dependency unavailable for recovery and retain existing timeout/interruption/too-large behavior. Retain no body, credentials, reason, Retry-After value or foreign affinity in recovery errors.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-DEP-001`, `MC-VALID-001`, `MC-JSON-001`, `MC-SCOPE-001` preserve the constructor/SDK style, reuse one existing transport, and gate the new behavior by output type.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 11` through typed CQE calls, constructor invariants, JDK transport and current Jackson. The complex eligibility Specification is server-side.
- Implementation pseudocode:

```java
@Override public ProcessWorkerFailuresDTO workerFailures(GetProcessInstanceQuery query) {
    Objects.requireNonNull(query, "query");
    var result = call("GET", "process-instances/" + segment(query.instanceId()) + "/worker-failures",
            null, ProcessWorkerFailuresDTO.class, null, recoveryResponses::failures);
    if (!query.instanceId().equals(result.instanceId())) throw invalidResponse();
    return result;
}
@Override public RecoverProcessWorkerResult recoverWorker(RecoverProcessWorkerCommand command) {
    Objects.requireNonNull(command, "command");
    var result = call("POST", "process-instances/" + segment(command.instanceId()) + "/worker-recoveries",
            command, RecoverProcessWorkerResult.class, null, recoveryResponses::recovery);
    if (!command.instanceId().equals(result.instanceId()) || !command.jobId().equals(result.jobId())
            || !command.jobId().equals(result.occurrenceId()) || !command.commandId().equals(result.commandId()))
        throw invalidResponse();
    return result;
}
// In call(..., Class<T> type, ..., Function<byte[],T> decoder):
boolean recoveryCommand = type == RecoverProcessWorkerResult.class;
boolean recoveryOperation = recoveryCommand || type == ProcessWorkerFailuresDTO.class;
// Before encoding/token retrieval, enforce normalized HTTPS-or-literal-loopback for recoveryOperation.
// Existing general encoder and MAX_REQUEST_BYTES; apply 8192 for recoveryCommand.
// Add recoveryOperation to existing request no-store and synchronous transport-error gates.
// Reuse exact single sendAsync and MAX_RESPONSE_BYTES for recovery.
if (recoveryOperation) {
    if (!hasExactApplicationJsonMediaType(response)) throw invalidForStatus(response.statusCode());
    if (response.statusCode() != 200) {
        String code = recoveryResponses.errorCode(response.body(), response.statusCode(), recoveryCommand);
        throw new ProcessClientException(response.statusCode(), code == null ? "TIANJI_INVALID_RESPONSE" : code,
                null, null);
    }
}
// Keep existing case/control/general branches and safe decoder catch; reach decoder only on valid recovery success.
```

- Verification contribution: File 1 goes GREEN with one send per call and exact bounded routes; `HttpProcessControlClientTest` continues to assert its old transport/error/affinity behavior.
- After this file: End-to-end SDK behavior is available through the finite fake transport. The separately packaged contract is still pending Files 4–5.

#### File 4 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryOpenApiContractTest.java`

- Purpose: Freeze the separately packaged OpenAPI 3.1 surface and prove semantic parity with C and the SDK's documented response matrix.
- Symbols: `openapi_publishes_exact_paths_security_and_status_pairs`, `openapi_schemas_match_closed_records_enums_and_bounds`, `every_local_reference_resolves`; private resource read and comparison helpers.
- Repository evidence: `ProcessControlOpenApiContractTest` uses classloader resource lookup, Jackson tree and record component reflection, validates local refs and OAuth scopes, and needs no Spring runtime.
- Dependencies and consumers: C records and capability metadata constants, existing `ProcessControlClient.READ_SCOPE`, JUnit/Jackson, File 5 resource, finite provider proof in later Step.
- Why now: The artifact contract test runs and fails for the missing resource before File 5 is created.
- Contract/signature changes: Verify exact two path/method/operationId combinations, separate resource keys, one OAuth alternative requiring both scopes, expected permissions and USER/SERVICE, independent success and error schemas, no-store in all listed responses.
- Input/output and state mapping: Reflect exact fields from four C record types. Compare local enum schemas to exact `Enum.name()`, required keys/nulls/constants/conditionals/bounds and purpose-specific codes to fixed Spec fixtures. Validate the optional tenant assertion is a positive decimal long, distinct from bearer-derived identity.
- Error and edge behavior: Fail on missing artifact, unknown/ref escape, empty alternative security, extra route/status/schema field, incorrect error codes/nullable flags, missing bounds/extensions or draft mismatch. Check old Control resource byte equality separately via the gate command.
- Standards impact: `MC-JSON-001`, `MC-VALID-001`, `MC-TIME-001`, `MC-TEST-001` prove artifact structure and normative constraints using existing test utilities.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11`; reflect the actual C values and named wire enums, with no new dependency or docs server.
- Implementation pseudocode:

```java
var url = ProcessWorkerRecoveryOpenApiContractTest.class.getResource("/META-INF/tianji/openapi/process-worker-recovery-v1.json");
assertNotNull(url, "packaged Worker recovery contract");
var root = mapper.readTree(resourceBytes(url));
assertEquals("3.1.0", root.path("openapi").asText());
assertEquals(Set.of(WORKER_FAILURES_PATH, WORKER_RECOVERIES_PATH), names(root.path("paths")));
assertOperation(root, WORKER_FAILURES_PATH, "get", "getProcessWorkerFailures",
        WORKER_FAILURES_RESOURCE_KEY, "ProcessWorkerFailuresDTO", exactGetErrorPairs);
assertOperation(root, WORKER_RECOVERIES_PATH, "post", "recoverProcessWorker",
        WORKER_RECOVERIES_RESOURCE_KEY, "RecoverProcessWorkerResult", exactPostErrorPairs);
assertEquals(Set.of(ProcessControlClient.READ_SCOPE, WORKER_RECOVER_SCOPE), soleOAuthRequirementScopes(operation));
assertRecordSchema(root, ProcessWorkerFailureDTO.class, Set.of("activityId", "expectedVersion"));
assertRecordSchema(root, ProcessWorkerFailuresDTO.class, Set.of());
assertRecordSchema(root, RecoverProcessWorkerCommand.class, Set.of());
assertRecordSchema(root, RecoverProcessWorkerResult.class, Set.of());
// Assert every property required, additionalProperties false, error correlation exactly null,
// closed dispositions/conditions, cap/sort/unique extensions, UTC and occurrence equality contract.
assertAllReferencesResolveBelowComponentsSchemas(root);
```

- Verification contribution: Class-only selector proves missing-resource RED and packaged semantic GREEN. A later provider step compares actual permission producer metadata with the same fixed authority.
- After this file: The artifact-specific RED is observable by finite test execution.

#### File 5 — `CREATE stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-worker-recovery-v1.json`

- Purpose: Package the independent Worker recovery REST consumer contract.
- Symbols: OpenAPI 3.1.0, JSON Schema 2020-12, GET `getProcessWorkerFailures`, POST `recoverProcessWorker`, exact local `components.schemas`, `oauth2` authorizationCode/clientCredentials examples.
- Repository evidence: CL packages `process-control-v1.json` and `case-v1.json` from this same resource directory. Control documents exact path, optional header, required scopes, no-store and status-specific code restrictions; reuse the packaging and contract style.
- Dependencies and consumers: New C records/metadata, codec matrix, File 4 semantic proof, PS controller/conformance gates. This is a local resource from CL's existing Maven lifecycle.
- Why now: File 4 has locked the artifact checks before its resource is authored.
- Contract/signature changes: Two exact Spec §9.2 routes. GET has no request body and POST requires `application/json` closed five-field body with `x-max-request-bytes: 8192`. Required `instanceId` path parameter excludes dot segments; optional `X-Egon-Tianji-Tenant` is a positive long assertion. OAuth security uses a single object with both scopes and exact permission/principal metadata per operation.
- Input/output and state mapping: Use all-fields-required and `additionalProperties:false` for command, row, observation, result and safe error. Model Java UTF-16 maxima with `x-java-utf16-max-length` plus JSON Schema bounds and safe-text pattern following Control; exact versions and command ID patterns, explicit nullable activity/version, closed named enum schemas, conditional native/version/required activity/reason/reported-failure constraints. Observe the 128 cap, sorted unique by job ID with extensions for unsigned UTF-8 order and job-key uniqueness. Both Instants are canonical UTC `Z` with date-time format; `status` const `RETRY_SCHEDULED`, retries integer const 1 and occurrence equality described in an extension. Error schema requires exact two fields, `correlationId` null and per-status code enum. `Cache-Control` header const `no-store` for success and all documented errors.
- Error and edge behavior: Exact status matrices in File 2; framework 415 remains outside the application JSON envelope. Documentation states provider is disabled by default and runtime acceptance is separate. OAuth URLs use the existing `identity.example` illustrative pattern.
- Standards impact: `MC-VALID-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` publish the closed artifact without springdoc or new build dependencies.
- Literal rule enforcement: `Rule 2`, `Rule 6`, `Rule 10`, `Rule 11`; define exact JSON types, bounds, canonical instants and independent L0 Query/Command.
- Implementation pseudocode:

```text
openapi = "3.1.0"; jsonSchemaDialect = "https://json-schema.org/draft/2020-12/schema"
paths[WORKER_FAILURES_PATH].get  = getProcessWorkerFailures / 200 -> ProcessWorkerFailuresDTO / fixed GET errors
paths[WORKER_RECOVERIES_PATH].post = recoverProcessWorker / required RecoverProcessWorkerCommand / 200 -> RecoverProcessWorkerResult / fixed POST errors
both.operations.security = [{ oauth2: ["tianji:process:read", "tianji:process:worker-recover"] }]
both.operations.x-required-permissions = ["TIANJI_PROCESS_READ", "TIANJI_PROCESS_WORKER_RECOVER"]
both.operations.x-allowed-principals = ["USER", "SERVICE"]
each.operations.x-resource-key = exact corresponding ProcessWorkerRecoveryClient constant
schemas.ProcessWorkerFailureDTO.allOf:
  if status is RETRY_AVAILABLE then nativeDeadletter true, nonnull activityId and version, reason NONE, reportedFailure RETRYABLE
  if status is MANUAL_REVIEW then nativeDeadletter true, version null, reason in the closed ReasonEnum except NONE and NO_NATIVE_DEADLETTER
  if status is HISTORICAL_FAILURE then nativeDeadletter false, version null, reason in NO_NATIVE_DEADLETTER/UNKNOWN_OUTCOME/BUSINESS_REJECTED/HISTORY_INCOMPLETE
  if reason is UNKNOWN_OUTCOME then reportedFailure UNKNOWN
  if reason is BUSINESS_REJECTED then reportedFailure BUSINESS_REJECTED
schemas.ProcessWorkerFailuresDTO.failures = array(maxItems:128, uniqueItems:true, x-unique-by:"jobId", x-sort-order:"unsigned-utf8-jobId")
schemas.RecoverProcessWorkerResult.occurrenceId.x-equals-field = "jobId"
responses[errorStatus].content.application/json.schema = local safe error ref + code enum for the exact pair set
```

- Verification contribution: File 4 exact classloader schema pass and CL JAR byte comparison prove the independently packaged artifact.
- After this file: SDK/OAS slice is complete; Step 2 verification and commit gate can run.

#### File 6 — `CREATE docs/egon/evidence/2026-11-05-worker-recovery-sdk.md`

- Purpose: Record actual SDK/OAS results and packaged bytes.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact phases, final selected Step suites `ProcessWorkerRecoveryContractTest,ProcessWorkerRecoveryHttpTest,ProcessWorkerRecoveryOpenApiContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ClientDependencyBoundaryTest`.
- Expected result: Every owning report exists; current-Step GREEN zero failures/errors/skips. A compile or environmental failure is not RED.
- Failure returns to: Exact owning file/assertion; design drift returns to effective Spec, not weaker tests. V10 mismatch stops under immutable rules.
- Completion criteria: Current-Step named reports, strict docs, exact paths/staged review, unchanged old SQL/excluded hashes and separate manual/literal closure all pass.
- Rollback: Only focused revert/forward fix; no history rewrite or deletion/repair/edit of existing migration.
- Commit paths: `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryHttpTest.java`; `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryResponseCodec.java`; `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`; `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessWorkerRecoveryOpenApiContractTest.java`; `stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-worker-recovery-v1.json`; `docs/egon/evidence/2026-11-05-worker-recovery-sdk.md`
- Commit: `feat(tianji-client): expose bounded Worker recovery transport`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing C/CL/PS technical vertical, exact named owner and no new layer. Actual EVD required; failed check blocks commit. |
| MC-REUSE-001 | Applicable | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. Actual EVD required; failed check blocks commit. |
| MC-DEP-001 | Applicable | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. Actual EVD required; failed check blocks commit. |
| MC-NAME-001 | Applicable | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. Actual EVD required; failed check blocks commit. |
| MC-VALID-001 | Applicable | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. Actual EVD required; failed check blocks commit. |
| MC-MODEL-001 | Applicable | Immutable record values and defensive copies; no mutable business entity. Actual EVD required; failed check blocks commit. |
| MC-CONVERT-001 | Applicable | Exact technical projection under approved profile; no cross-layer mapper or business entity. Actual EVD required; failed check blocks commit. |
| MC-LOG-001 | Not applicable | No logging path change in this Step; existing controls remain. Actual EVD required; failed check blocks commit. |
| MC-BEAN-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-UTIL-001 | Applicable | Current JDK/framework/digest helpers only. Actual EVD required; failed check blocks commit. |
| MC-JSON-001 | Applicable | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. Actual EVD required; failed check blocks commit. |
| MC-TIME-001 | Applicable | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. Actual EVD required; failed check blocks commit. |
| MC-CONFIG-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-PATTERN-001 | Not applicable | No new eligibility workflow in this Step; known shared policy belongs Step4. Actual EVD required; failed check blocks commit. |
| MC-SCOPE-001 | Applicable | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. Actual EVD required; failed check blocks commit. |
| MC-TEST-001 | Applicable | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. Actual EVD required; failed check blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. Actual EVD required; failed check blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Exact semantic type/DTO/Command/Result/Enum names. Record actual evidence or scoped N/A separately. |
| Rule 2 | Applicable | Strict actual boundary, authorized context and exact proof, existing technical validation. Record actual evidence or scoped N/A separately. |
| Rule 3 | Applicable | Immutable records and defensive copies; no entity conversion bypass. Record actual evidence or scoped N/A separately. |
| Rule 4 | Not applicable | No managed business class/logger/factory wiring in this Step; exact technical profile unchanged. Record actual evidence or scoped N/A separately. |
| Rule 5 | Applicable | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. Record actual evidence or scoped N/A separately. |
| Rule 6 | Applicable | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. Record actual evidence or scoped N/A separately. |
| Rule 7 | Not applicable | No profile/config key touched in this Step. Record actual evidence or scoped N/A separately. |
| Rule 9 | Not applicable | No new policy/complex runtime selection in this Step. Record actual evidence or scoped N/A separately. |
| Rule 10 | Applicable | Existing java.time/DB precision, decision/source time, no global clock or execution claim. Record actual evidence or scoped N/A separately. |
| Rule 11 | Applicable | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. Record actual evidence or scoped N/A separately. |

### Step 3 — Add immutable V10 audit and exact readiness

- Requirements: REQ-004, REQ-005, REQ-006, REQ-007
- Dependencies: Exact Step2 semantic commit and fresh Root current-Step lock
- Baseline state: Steps1–2 verified committed; current-Step behavior absent.
- Observable outcome: Add immutable V10 audit and exact readiness, with current contract and old behavior preserved.
- End state: Exact 7 paths; real finite assertion gates and strict/source/SQL checks, one semantic commit.
- Test-first gate: Required — expected missing type/resource/policy assertion RED, compile A, typed behavior RED, minimal B and GREEN. Failures count only as actual intended JUnit F with E0/S0.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — independently close each scoped row, Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStoreTest.java`

- Purpose: Pin the V10 schema contract and exact audit-write/replay behavior before changing storage.
- Symbols: `ProcessWorkerRecoveryReceiptStoreTest#requiresImmutableRecoveryMigrationResource`, `rejectsWrongOrAmbiguousAuditAndReplaysExactStoredResult`, `probesAcceptedRestoreByExactJobAndOwner`, `insertsExactEvidenceAndDistinguishesTargetConflict`, `requiresVerifiedSameSourceTransaction`, `readsPlainStartOnlyForExactOwner`.
- Repository evidence: Existing `execution/ProcessCancellationReceiptStoreTest` fake `DataSource`/JDBC binding captures SQL and parameters without starting PostgreSQL; `ProcessApiSchemaVerifierTest#resource` reads packaged UTF-8 resources. Existing V9 establishes immutable audit precedent.
- Dependencies and consumers: JUnit, AssertJ, existing fake JDBC idiom, V10 classpath resource, Step 1 recovery contract values, Files 4–5 receipt stores.
- Why now: Stage A compiles using existing test libraries and only looks up the exact V10 resource path, so missing V10 fails an assertion. Stage B, after Files 4–6 exist, adds the typed fakes with all behavior assertions before the Store behavior is filled in.
- Contract/signature changes: Add the listed package-local finite tests. No public test helper or runtime fixture.
- Input/output and state mapping: Schema fixture independently asserts 32 named columns, three NO ACTION FKs, PK, source-global unique job index and exact reason/time/failure checks. JDBC fixtures project request, owner, applied START, definition, pre-move revision, evidence digest and selected failure facts to one immutable Result. Use different exact V4 and restore UTC instants at microsecond precision.
- Error and edge behavior: Same job conflict result 0 returns the blocking outcome; arbitrary integrity error is propagated. Missing, duplicate, wrong-owner, wrong-instance, wrong-job, malformed time/state/hash and joined-authority mismatch fail closed. Repeated same input returns original decidedAt/reason-derived command state. No query before validated source and transaction, no mutation on replay/probe.
- Standards impact: `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact contract constructors, SQL bind assertions and UTC round-trip; fake JDBC uses existing libraries.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` — semantic test names, exact domain/authority and typed JDBC projection, JDK/assertions, name-based statuses, Instant values, same technical package.
- Implementation pseudocode:

```java
// Stage A; use only JDK/classpath and existing test APIs.
@Test void requiresImmutableRecoveryMigrationResource() {
    try (var input = getClass().getResourceAsStream("/db/tianji-process/V10__add_process_worker_recovery_receipts.sql")) {
        assertThat(input).as("V10 recovery migration resource").isNotNull()  // assertion RED
        assert independently expected ordered columns/types, keys, index and checks from reviewed fixture
    }
}
// Stage B; run against compile-only new store seam before filling production behavior.
@Test void insertsExactEvidenceAndDistinguishesTargetConflict() {
    fake verified exact bound source; return valid PENDING/START/definition facts
    fake INSERT results [1, 0, 2, unexpected integrity exception] in isolated invocations
    assert first true, second false, third safe IllegalStateException, fourth propagated original DataAccessException; capture 32 ordered parameters
    assert INSERT targets "(job_id)" and uses DO NOTHING with no alternate mutation SQL
    assert exact failure code and Instant versus independently bound restore Instant
}
@Test void rejectsWrongOrAmbiguousAuditAndReplaysExactStoredResult() {
    arrange a locked APPLIED generic receipt, exact locked START and joined immutable audit/definition
    assert find(...) equals the fixture Result, twice, with original decidedAt and no JDBC update
    inject zero/duplicate audit rows, changed owner/instance/job, PENDING, wrong definition SHA,
        changed exact reason despite same hash, invalid result retry/status, missing or non-microsecond instant
    assert each fails as unavailable; check no unscoped or native fallback query
}
@Test void probesAcceptedRestoreByExactJobAndOwner() {
    capture SELECT job_id by exact job and tenant/type/issuer/subject/instance, LIMIT 2
    assert one row true, zero false, two unavailable; verify no mutation or reason projection
}
@Test void requiresVerifiedSameSourceTransaction() {
    use existing mockConstruction pattern for the schema verifier and fake JDBC
    assert verifier receives the exact source/schema and successful verification is required
    assert no audit SQL with no binding, foreign binding, interruption, or false primary/UTF8/origin
    assert insert refuses a read-only transaction; clear synthetic thread bindings in finally
}
@Test void readsPlainStartOnlyForExactOwner() {
    assertDoesNotThrow full exact START read with USER and SERVICE fixtures
    assert bound tuple and no FOR UPDATE, then invoke existing lockStartByInstance and assert FOR UPDATE
    assert missing/duplicate/row identity mismatch fail with ProcessResourceNotFoundException
    verify generic lock/insert/apply handle PROCESS_WORKER_RECOVER using job resource and null decision JSON
}
```

- Verification contribution: `requiresImmutableRecoveryMigrationResource` gives the Stage A assertion RED; Stage B fake cases prove `TEST-007` store predicates and no destructive SQL.
- After this file: Stage A compiles and is intentionally RED until the V10 resource exists; Stage B cases land once the new Java seams compile and prove missing behavior with assertions.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`

- Purpose: Fix supported migration history and independent recovery privilege checks in finite fake catalog tests.
- Symbols: Rename `history_accepts_only_none_exact_v8_or_exact_v8_followed_by_v9` to `history_accepts_only_exact_v8_v9_v10_prefixes`; add `v10_keeps_existing_readiness_without_recovery_grants`, `recovery_requires_exact_v10_catalog_and_role`, `recovery_verifier_pins_the_frozen_v10_resource_identity`.
- Repository evidence: `checkHistory(List<Map<String,Object>>)`, `history`, `checksum`, `Catalog`, `verifyCancellation`, `full_case_activation_retains_its_three_table_write_grants` already simulate history, catalog and grant drift via fake JDBC.
- Dependencies and consumers: Existing `hasCaseMigration()` and `verifyCancellation()` entry points in A; `verifyWorkerRecovery()` and exact V10 in B; existing V8/V9 fixtures and behavior assertions retained.
- Why now: A pins the legacy verifier's failure on a legitimate third migration before changing the verifier. B adds the new verifier's fake role/catalog acceptance and rejection cases once its declaration compiles.
- Contract/signature changes: Extend only test-local fixture support for V10 rows, static resource identity, independent recovery descriptor and query log; preserve existing Case/cancellation fixture semantics.
- Input/output and state mapping: Exact ordered prefixes [], [8], [8,9], [8,9,10] accepted for history. V10 readback includes checked constraints, three FK relationships and two exact indexes. Legacy fake rejects any recovery-catalog/role query to prove the independence.
- Error and edge behavior: Reject gap, duplicate, unknown, reordered, failed, wrong file/type/checksum or repeatable history, missing/extra/wrong columns or checks, incorrect FK/index, missing SELECT/INSERT, role ownership, effective UPDATE/DELETE/TRUNCATE/TRIGGER/REFERENCES, RLS or unsafe trigger; old feature fakes need no V10 role. Valid V8 and V9 regressions remain.
- Standards impact: `MC-VALID-001`, `MC-UTIL-001`, `MC-SCOPE-001`, `MC-TEST-001` — use finite existing catalog fixture, verify actual SQL/schema and authorization gates.
- Literal rule enforcement: `Rule 2`, `Rule 5`, `Rule 11` — shape and role rejection, JDK fixture utilities, established verifier package and no new dependencies.
- Implementation pseudocode:

```java
// Stage A: no call to new Java methods or requirement for a real V10 resource in this focused case.
@Test void history_accepts_only_exact_v8_v9_v10_prefixes() {
    v8 = history("8", V8, true, checksum(V8))
    v9 = history("9", V9, true, checksum(V9))
    v10 = history("10", V10, true, 0) // Stage A fixture only; replace with exact checksum in B
    assertTrue(assertDoesNotThrow(() -> checkHistory(List.of(v8, v9, v10))), "exact V8/V9/V10 prefix")
}
// B makes this fixture use checksum(V10), retains all old negative cases and adds:
// [8,10], [10], [8,10,9], duplicate 10, wrong filename/type/checksum/null checksum,
// failed 10, appended repeatable and unknown version; each assertThatThrownBy uses safe IllegalStateException.
@Test void v10_keeps_existing_readiness_without_recovery_grants() {
    fake exact V8,V9,V10 and old catalog; fail on any V10 catalog/privilege query
    assert old hasCaseMigration/verifyCase/verifyCancellation readiness under their existing grants
}
@Test void recovery_requires_exact_v10_catalog_and_role() {
    assertDoesNotThrow verifyWorkerRecovery against exact history and independently expected V10 catalog
    assert expected recovery catalog queries, exact audit/native role checks, no cancellation/provenance/dispatch queries
    mutate one schema, key, index or role property at a time; assert unavailable
}
@Test void recovery_verifier_pins_the_frozen_v10_resource_identity() {
    assert SHA-256(resource V10 raw bytes) equals independently recorded off-repository review digest
    assert exact pinned production RECOVERY_SHA256 equals that review digest
    assert Flyway CRC follows the same existing checksum(V10) line algorithm
}
```

- Verification contribution: A targets existing public history API, B runs the full verifier suite including independent recovery and old readiness.
- After this file: A compiles. The named history test must fail for existing rejection of length three. Other tests referencing new behavior enter in B after minimal compile prerequisites.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql`

- Purpose: Add the empty, immutable accepted-decision relation in the existing owned ProcessDB schema.
- Symbols: `tianji_process_worker_recovery_receipt`, `tianji_process_worker_recovery_receipt_pkey`, `tianji_process_worker_recovery_command_fk`, `tianji_process_worker_recovery_start_fk`, `tianji_process_worker_recovery_definition_fk`, `tianji_process_worker_recovery_job_uk`.
- Repository evidence: V1/V2/V3 generic receipt and definition PK/JSON contracts; V8 and V9 immutable history; V9 physical audit key and `ProcessApiSchemaVerifier#tableContracts` named constraint/index parsing. The existing generic command kind storage has no closed kind CHECK. V2 already enforces decision JSON only for DECISION.
- Dependencies and consumers: External Flyway migration runner; exact V1/V3 receipt PK, V1 release-definition PK, selected V4 facts copied as values, File 5 DML, File 6 immutable resource verifier, File 1 shape assertion.
- Why now: A REDs have run. Draft and review final SQL bytes outside the repository, agree checksum and source catalog expectations, then create V10 once at the specified path. File 6 needs the reviewed immutable bytes before it can pin SHA-256 and calculate the existing Flyway CRC.
- Contract/signature changes: Exactly one new 32-column permanent table, one six-column PK, three immediate same-schema FKs with default NO ACTION, and one standalone unconditional unique btree index over `job_id`. Leave old resource bytes intact.
- Input/output and state mapping: All 32 columns have NOT NULL, no defaults, identities or generated fields. All char/varchar columns explicitly use `COLLATE "C"`. V4 selected failure decision and restore decision are distinct UTC timestamp(6) values with no ordering CHECK. Accepted rows retain exact command reason, definition facts, job/occurrence, native revision, evidence digest and fixed policy.
- Error and edge behavior: Duplicate jobs must target the standalone unique index; the general command PK and all FK, constraint or unrelated integrity errors remain ordinary failures. No data backfill, alterations, grants, triggers, soft deletion or additional index. Disable feature or forward-fix after apply while retaining audit and history.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-VALID-001`, `MC-TIME-001`, `MC-SCOPE-001`, `MC-TEST-001` — exact Root technical-profile JDBC/Flyway exception, UTC microseconds, immutable V10 review.
- Literal rule enforcement: `Rule 2`, `Rule 6`, `Rule 10`, `Rule 11` — SQL verifies exact fixed technical values, named statuses/kinds and UTC timestamp precision. Root-confirmed immutable audit uses source-global native ID uniqueness for its physical lifetime and keeps Flyway ownership external.
- Implementation pseudocode:

```sql
-- Outside-repository reviewed descriptor. Use the established four-space columns, named CHECKs
-- and CREATE UNIQUE INDEX syntax consumed by ProcessApiSchemaVerifier#tableContracts.
CREATE TABLE tianji_process_worker_recovery_receipt (
    tenant_id bigint,
    subject_type varchar(16), subject_issuer varchar(64), subject_id varchar(256),
    command_type varchar(32), command_id varchar(128), request_sha256 char(64),
    process_instance_id varchar(255),
    start_command_type varchar(32), start_command_id varchar(128), start_request_sha256 char(64),
    release_key varchar(128), model_type varchar(8), definition_key varchar(128),
    engine_definition_id varchar(255), definition_sha256 char(64), business_key varchar(255),
    correlation_id varchar(128), job_id varchar(255), occurrence_id varchar(255),
    execution_id varchar(255), element_id varchar(128), deadletter_revision integer,
    expected_version char(64), evidence_sha256 char(64), admission_policy varchar(32),
    failure_code varchar(64), failure_decided_at timestamptz(6), reason varchar(512),
    result_status varchar(24), retries integer, decided_at timestamptz(6),
    -- Expand every column onto its own line with the collation/null clauses above.
    CONSTRAINT tianji_process_worker_recovery_receipt_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_command_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_start_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, start_command_type, start_command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_definition_fk
        FOREIGN KEY (tenant_id, release_key, model_type, definition_key)
        REFERENCES tianji_process_release_definition (tenant_id, release_key, model_type, definition_key)
    -- Named CHECKs (prefix tianji_process_worker_recovery; each safe ID uses value<>'',
    -- value=btrim(value), and value !~ '[[:cntrl:]]' under its column C collation):
    -- _tenant: tenant_id > 0
    -- _owner: subject_type = ANY(ARRAY['USER', 'SERVICE']), subject_issuer ^[0-9a-f]{64}$, nonempty exact safe subject_id
    -- _kind: command_type PROCESS_WORKER_RECOVER, start_command_type START, model_type BPMN
    -- _command: command_id, start_command_id, correlation_id ^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$
    -- _hashes: request_sha256, start_request_sha256, definition_sha256, expected_version, evidence_sha256 ^[0-9a-f]{64}$
    -- _release: release_key ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$, definition_key ^[A-Za-z_][A-Za-z0-9_.-]{0,127}$
    -- _instance: safe nonempty process_instance_id AND process_instance_id <> '.' AND process_instance_id <> '..'
    -- _affinity: safe nonempty engine_definition_id, business_key, job_id, execution_id, element_id
    --            AND char_length(job_id) <= 64 AND occurrence_id=job_id AND deadletter_revision>0
    -- _evidence: admission_policy='READ_ONLY_EXHAUSTED_RETRY_V1' AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
    -- _reason: reason<>'' AND reason=btrim(reason) AND octet_length(reason)<=2048
    --          AND translate(reason, chr(1) || chr(2) || ... || chr(31) || chr(127), '')=reason
    -- _result: result_status='RETRY_SCHEDULED' AND retries=1
);
CREATE UNIQUE INDEX tianji_process_worker_recovery_job_uk
    ON tianji_process_worker_recovery_receipt (job_id);
```

- Verification contribution: File 1 validates exactly 32 column names/types, collations and CHECKs, exactly three keyed FKs, two indexes and SQL text invariants. File 2 validates catalog correspondence and immutable hash.
- After this file: Classpath resource assertion is GREEN. The pre-V10 verifier still rejects the exact prefix. Resource is immutable. No SQL has been applied by this Step.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`

- Purpose: Enable one new generic receipt kind and an exact, plain read of the full applied START evidence for read-only recovery observation.
- Symbols: `Kind.PROCESS_WORKER_RECOVER`, `public TaskOrigin readStartByInstance(ProcessActor actor, String instanceId)`, existing `lockStartByInstance` and `TaskOrigin`.
- Repository evidence: `startByInstance` does a plain owner read but omits command ID. `lockStartByInstance` already reads the full `TaskOrigin` under the exact tuple and owns the write-side lock. `taskOrigin` has wider task approver semantics and cannot substitute for owner recovery.
- Dependencies and consumers: `ProcessActor`, existing `JdbcTemplate`, `TaskOrigin` and `ProcessResourceNotFoundException`; Step 4 recovery service uses the new plain read for GET and existing locked read for POST. `insert`, `lockIfPresent`, `apply` handle the new kind under existing generic APIs.
- Why now: Depends on the A REDs and gives File 5 and the new B tests exact compiled receipt types without requiring the service.
- Contract/signature changes: Append the kind. Add the exact plain owner read, reusing the existing full START projection/mapping where it preserves the locking query, binds and behavior. Preserve existing methods/visibility/return shapes. DECISION remains the only JSON result kind.
- Input/output and state mapping: Exact positive tenant, USER or SERVICE, non-LEGACY issuer and subject plus instance map to START APPLIED. Row maps stored command ID, request SHA, release, correlation, instance and original `created_at` to the existing immutable `TaskOrigin` with exact `Instant`.
- Error and edge behavior: No matching row, ambiguous row, post-map owner/instance mismatch produce the existing safe not-found error. Untrusted shape uses existing validation boundary; a query never widens owner matching or locks during GET. Existing POST lock placement and other callers remain on current APIs.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001` — existing technical store and immutable mapping, no DI/JSON change.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` — semantic receipt kind, validated owner tuple, existing immutable record and JDBC/JDK conversion, enum name only, UTC Instant, no layering change.
- Implementation pseudocode:

```java
enum Kind { /* existing values in existing order */, PROCESS_WORKER_RECOVER }
public TaskOrigin readStartByInstance(ProcessActor actor, String instanceId) {
    require same valid non-LEGACY exact owner boundary as lockStartByInstance
    // Caller already validates process segment via existing GetProcessInstanceQuery.
    rows = jdbc.query(existing full START projection
        + exact tenant/type/issuer/subject equality
        + " AND command_type='START' AND state='APPLIED' AND resource_id=?",
        existing full TaskOrigin row mapping, exact owner and instance binds)
    require exactly one row and requester/instance equal to input, otherwise notFound
    return only row
}
```

- Verification contribution: File 1 B captures SELECT, owner parameters, absence of `FOR UPDATE` on plain read and existing locking behavior, generic kind non-DECISION semantics and row count checks. Existing Control and cancellation suites protect prior callers.
- After this file: Compile prerequisite is present. Complete the plain read implementation after the B assertion RED against the minimum signature, then run both new and old caller tests.

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStore.java`

- Purpose: Validate, insert and replay one accepted immutable recovery audit in the existing owned JDBC transaction.
- Symbols: `ProcessWorkerRecoveryReceiptStore(DataSource source, String schema)`, `boolean usesDataSource(DataSource candidate)`, `void verifyStorage()`, `RecoverProcessWorkerResult find(ProcessActor actor, TaskOrigin start, Receipt receipt, RecoverProcessWorkerCommand command, String requestHash)`, `boolean hasAccepted(TaskOrigin start, String jobId)`, `Instant decisionTime()`, `boolean insert(Entry entry)`, immutable `Entry`.
- Repository evidence: `ProcessCancellationReceiptStore#usesDataSource,verifyStorage,find,insert,current,requireTransaction` demonstrate a scoped, caller-transaction-bound store. `ProcessCommandReceiptStore#apply` enforces affected row count 1. Recovery uses no raw native table write, cancellation role, standalone transaction or alternate data source.
- Dependencies and consumers: Existing Spring JDBC/TransactionSynchronizationManager, `ProcessApiSchemaVerifier#verifyWorkerRecovery` (File 6), Step 1 contract values, File 4 generic receipt and `TaskOrigin`, Step 4 recovery service and Step 5 explicit named owned-source factory.
- Why now: Defines only the audit/storage seam once V10 and generic receipt kind are known. Complete implementation after B tests go assertion RED. The actual command service and DI factory are subsequent Steps.
- Contract/signature changes: `public record Entry(ProcessActor actor, String requestHash, RecoverProcessWorkerCommand command, TaskOrigin start, String definitionKey, String engineDefinitionId, String definitionSha256, String businessKey, String occurrenceId, String executionId, String elementId, int deadletterRevision, String evidenceSha256, String admissionPolicy, String failureCode, Instant failureDecidedAt, RecoverProcessWorkerResult result)` validates its exact shape and redacts `toString`. `find` accepts an already locked generic APPLIED receipt and locked current START; success is one exact typed stored result. `insert` returns false solely for update count 0 from targeted job conflict. The Step 4 service immediately throws `ProcessWorkerRecoveryBlockedException` within the same transaction before generic APPLY.
- Input/output and state mapping: Explicit audit insert/read projections follow V10 column order. Owner tuple from actor; generic kind literal and exact command ID/request SHA; instance, expected version and reason from command; START kind literal, command/hash/release/correlation from the exact locked origin; BPMN and released definition key/ID/model SHA; actual pre-move business key/job/occurrence/execution/element/revision; fixed policy, evidence SHA and exact selected V4 code/Instant; result status name, one retry and exact ProcessDB decision Instant. Reconstruct exact seven-field Result from stored instance/job/occurrence/command/status/retries/decidedAt. The stored reason is validated and compared, and stays private. The Entry constructor validates exact owner/START/command/result affinity, existing release/definition/command/correlation/hash formats, safe nonblank identifiers with paired surrogate checks and declared limits, job native length and occurrence equality, positive revision, fixed policy, safe failure code and microsecond precision. Restore and V4 decision instants remain distinct fields. Construct the existing `ProcessActor` and Step 1 Command/Result to reuse their validation. Catch and sanitize runtime invariant/constructor errors only at JDBC mapping, preserving actual SQL failures for the outer transaction classifier.
- Error and edge behavior: Unverified or wrong transaction/source, interruption, nonprimary/non-UTF8/non-origin session, malformed or ambiguous authority, missing exact row or forged/corrupt detail fail unavailable. Existing hash mismatch is the caller's typed idempotency conflict after current START authorization. For exact hash replay, a different stored reason/command or mismatching joined authority fails unavailable. Insert only with matched PENDING row and exact START/definition proof. Propagate DataAccessException to the outer classifier and use no catch-by-message, generic conflict suppression, retry or log of protected fields. No comparison implies ordering between the failure Instant and restore Instant.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001`, `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-SCOPE-001` — existing technical profile uses named factory and explicit exact projection from owned JDBC; immutable record, no business logger or public reason.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 3`, `Rule 4`, `Rule 5`, `Rule 6`, `Rule 10`, `Rule 11` — semantic name, exact boundary validation, immutable projection, explicit technical-source wiring under Root exception, existing JDK/JDBC facilities, typed status name, UTC Instants, same physical schema/transaction.
- Implementation pseudocode:

```java
ProcessWorkerRecoveryReceiptStore(DataSource source, String schema) {
    validate source and simple owned schema; retain exact source identity
    create existing JdbcTemplate(source); setQueryTimeout(5)
    form qualified constant table names using validated quoted schema only
}
void verifyStorage() {
    clear verified; reject ambient transaction
    new ProcessApiSchemaVerifier(source, schema).verifyWorkerRecovery()
    mark verified
}
requireBoundTransaction() {
    require verified && actual Spring transaction && exact bound source resource && !interrupted
    require query("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'"
                  + " AND current_setting('session_replication_role')='origin'") == true
}
Instant decisionTime() {
    requireBoundTransaction()
    return required jdbc clock_timestamp as Timestamp.toInstant(); require nanos modulo 1000 == 0
}
RecoverProcessWorkerResult find(ProcessActor actor, TaskOrigin start, Receipt receipt,
                                RecoverProcessWorkerCommand command, String requestHash) {
    requireBoundTransaction()
    require exact actor equals start.requester, command instance equals start.instanceId
    require receipt APPLIED, resourceId == command.jobId, hash/release/correlation match
    query at most two by exact recovery PK with LIMIT 2; join APPLIED generic, exact same-owner APPLIED START
        on all shared identity, command ID and hash fields, and exact BPMN release definition
    require one row; compare generic stored state/resource/hash/release/correlation and current locked START
        on owner, command ID/hash, resource, release, correlation and original createdAt
    reconstruct validated Entry using explicit safe projection and prove current definition ID/SHA
    require entry actor, exact five-field command and request SHA equal caller context
    return entry.result
}
boolean hasAccepted(TaskOrigin start, String jobId) {
    requireBoundTransaction()
    query SELECT job_id by exact job_id + tenant/type/issuer/subject + process_instance_id LIMIT 2
    return one row; zero false; ambiguous unavailable
}
boolean insert(Entry entry) {
    requireBoundTransaction(); require writable transaction
    verify exactly one generic row under caller-held lock still PENDING with null resource and exact hash/release/correlation
    verify one current APPLIED START identical to entry.start and exact released BPMN definition ID/SHA
    bind both UTC instants via Timestamp.from after validating microsecond precision
    n = jdbc.update("INSERT INTO <qualified audit> (<exact 32 named columns>) VALUES (<32 binds>)"
                    + " ON CONFLICT (job_id) DO NOTHING", exact ordered mapped fields)
    if (n == 0) return false
    if (n != 1) throw safe unavailable
    return true
}
```

- Verification contribution: File 1 B captures exact query predicates and insert args; drives replay success/independent corruption and conditional failure branches, role/source and timestamp proofs. Step 4 test asserts same-transaction zero-row rollback request before APPLY.
- After this file: B compile-only seams exist after File 6 signature is added. Complete audit behavior after focused B RED and then run finite Store tests, including former V9 store regression.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`

- Purpose: Accept the exact new known history prefix in prior readiness and independently verify V10's immutable table, key/index and effective privileges before recovery activation.
- Symbols: `RECOVERY_FILE`, `RECOVERY_SHA256`, `RECOVERY_RECEIPT`, `public void verifyWorkerRecovery()`, `recoveryMigration()`, existing `laterMigrations()`, `hasCaseMigration()`, `verifyCancellation()`, `checksum(String)`, `tableContracts(String, Map<String,Integer>)` and `verifyCaseColumns/Checks/Keys/Indexes/Role`.
- Repository evidence: Existing `laterMigrations()` uses `ORDER BY installed_rank` and exact V8/V9 version/type/script/success/checksum. Existing byte-pinned migration loaders, V9 table parser and immutable role branch cover required catalog shapes. Existing `verifyWorker()` and the source-verified Worker instance remain separate prerequisites.
- Dependencies and consumers: Existing JDBC/catalog and hash logic, immutable File 3, File 5 store startup, existing `CaseStartProvenanceStore` and `ProcessReleaseService` through `verifyCase()`, `HumanTaskStorageVerifier#verify` through `hasCaseMigration()`, existing cancellation readiness through `verifyCancellation()`, Step 4 native readiness and Step 5 recovery factory.
- Why now: Once the committed-to-be resource bytes are fixed, use their SHA and existing CRC, and support the File 5 compile-only seam. Finish independent V10 readiness and old prefix compatibility after B assertion RED.
- Contract/signature changes: Extend known list by one exact version. `hasCaseMigration()` still interprets any recognized nonempty prefix as V8 present; cancellation requires prefix size 2 or 3; recovery requires exact size 3 and independently verifies V10. Existing SQL/role checks for V1–V9 remain applicable under their previous entry points.
- Input/output and state mapping: Rows are ordered by installed rank and checked positionally against ordered [8,9,10] descriptors. Immutable classpath bytes are SHA-256 verified before catalog derivation. `tableContracts` derives exactly one 32-column contract with C collations, null/defaults, CHECK signatures, one PK, three default immediate same-schema FK keys, PK index and exact standalone job unique index. Queries are startup read-only.
- Error and edge behavior: Reject unknown/versionless/repeatable, missing prefix, out-of-order, duplicate, failed, wrong type/filename/CRC, bad resource SHA, changed column or constraint, unexpected trigger/RLS/table/role drift. Recovery requires table-level SELECT and INSERT; deny effective UPDATE (including column-only), DELETE, TRUNCATE, TRIGGER and REFERENCES (including column-only), owner-role membership and privileged role per existing verifier. The recovery-only role predicate also denies membership of the owned schema role with `NOT pg_has_role(n.nspowner,'MEMBER')`, as that role can drop the audit via schema cascade. Existing feature predicates retain their existing exact behavior. Old valid V10 readiness never reads V10 table catalog or requests V10 grants; recovery never uses V9-specific readiness or grants. Use fixed safe startup error without database payload.
- Standards impact: `MC-ARCH-001`, `MC-REUSE-001`, `MC-NAME-001`, `MC-VALID-001`, `MC-UTIL-001`, `MC-SCOPE-001`, `MC-TEST-001` — preserve technical verifier and reuse existing JDK hash, role and catalog shape verification.
- Literal rule enforcement: `Rule 1`, `Rule 2`, `Rule 5`, `Rule 11` — explicit semantic readiness/validation, JDK/Spring JDBC reuse, same resource ownership and no auto-migration.
- Implementation pseudocode:

```java
private List<CaseVersion> laterMigrations() {
    rows = existing query ignoring only 1..7, including versionless, ordered by installed_rank
    require rows.size <= 3
    for (position, row) {
        require exact ("8", CASE_FILE), ("9", CANCEL_FILE), ("10", RECOVERY_FILE) at that position
        require type "SQL", success, nonnull exact checksum(file)
        byte-verify corresponding existing caseMigration/cancellationMigration or new recoveryMigration
    }
    return rows
}
public void verifyCancellation() {
    retain existing V4/V5/primary/UTF8/origin, V8 provenance and V9 catalog and role checks
    require laterMigrations().size() >= 2 // allowed maximum is already bounded to exactly 3
}
public void verifyWorkerRecovery() {
    jdbc.setQueryTimeout(5)
    require primary, UTF8 and session_replication_role='origin'
    require laterMigrations().size() == 3
    contract = tableContracts(recoveryMigration(), Map.of(RECOVERY_RECEIPT, 32)).get(RECOVERY_RECEIPT)
    verifyCaseColumns(RECOVERY_RECEIPT, contract.columns())
    verifyCaseChecks(RECOVERY_RECEIPT, contract.checks(), false)
    verifyCaseKeys(RECOVERY_RECEIPT, contract.keys())
    verifyCaseIndexes(RECOVERY_RECEIPT, contract.indexes())
    reuse immutable role predicate with INSERT required and column UPDATE/REFERENCES denied
    add " AND NOT pg_has_role(n.nspowner,'MEMBER')" only to the recovery table role predicate
    // Enabled recovery only: verify required native catalog projections in exact schema.
    // SELECT presence/field shape on act_ru_execution, act_ru_deadletter_job, act_ru_external_job,
    // act_ru_job, act_ru_timer_job, act_ru_suspended_job; match pinned required REV_/affinity/handler fields.
    // Require existing SELECT and at least one UPDATE-column privilege for each locked root/deadletter table;
    // require existing DELETE deadletter and INSERT external native engine privileges. No grants, data writes or row locks in verifier.
    // Old readiness calls exact history only and never consults these recovery-specific catalog/role probes.
}
private static String recoveryMigration() {
    read fixed "/db/tianji-process/" + RECOVERY_FILE as bytes
    compare SHA-256 to RECOVERY_SHA256 reviewed from exactly those bytes
    return UTF-8 text; fail unavailable for missing/altered/unreadable resource
}
```

- Verification contribution: File 2 checks byte identity and exact prefix, proves legacy V10 success without recovery grant or catalog calls, and verifies full catalog and role drift matrix for recovery. File 1 proves parsed descriptor agrees with independently enumerated physical V10 layout.
- After this file: Independent recovery readiness API compiles; after B RED, complete Files 4–6 and run focused GREEN. The feature is still unavailable by default and has no activation wiring in this Step.

#### File 7 — `CREATE docs/egon/evidence/2026-11-05-worker-recovery-storage.md`

- Purpose: Record actual V10 immutability, audit and verifier evidence.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact phases, final selected Step suites `ProcessWorkerRecoveryReceiptStoreTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,ProcessControlServiceTest,ProcessControlNativeAccessTest`.
- Expected result: Every owning report exists; current-Step GREEN zero failures/errors/skips. A compile or environmental failure is not RED.
- Failure returns to: Exact owning file/assertion; design drift returns to effective Spec, not weaker tests. V10 mismatch stops under immutable rules.
- Completion criteria: Current-Step named reports, strict docs, exact paths/staged review, unchanged old SQL/excluded hashes and separate manual/literal closure all pass.
- Rollback: Only focused revert/forward fix; no history rewrite or deletion/repair/edit of existing migration.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStoreTest.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`; `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessCommandReceiptStore.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryReceiptStore.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`; `docs/egon/evidence/2026-11-05-worker-recovery-storage.md`
- Commit: `feat(process): add immutable Worker recovery audit`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing C/CL/PS technical vertical, exact named owner and no new layer. Actual EVD required; failed check blocks commit. |
| MC-REUSE-001 | Applicable | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. Actual EVD required; failed check blocks commit. |
| MC-DEP-001 | Applicable | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. Actual EVD required; failed check blocks commit. |
| MC-NAME-001 | Applicable | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. Actual EVD required; failed check blocks commit. |
| MC-VALID-001 | Applicable | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. Actual EVD required; failed check blocks commit. |
| MC-MODEL-001 | Applicable | Immutable record values and defensive copies; no mutable business entity. Actual EVD required; failed check blocks commit. |
| MC-CONVERT-001 | Applicable | Exact technical projection under approved profile; no cross-layer mapper or business entity. Actual EVD required; failed check blocks commit. |
| MC-LOG-001 | Not applicable | No logging path change in this Step; existing controls remain. Actual EVD required; failed check blocks commit. |
| MC-BEAN-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-UTIL-001 | Applicable | Current JDK/framework/digest helpers only. Actual EVD required; failed check blocks commit. |
| MC-JSON-001 | Applicable | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. Actual EVD required; failed check blocks commit. |
| MC-TIME-001 | Applicable | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. Actual EVD required; failed check blocks commit. |
| MC-CONFIG-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-PATTERN-001 | Not applicable | No new eligibility workflow in this Step; known shared policy belongs Step4. Actual EVD required; failed check blocks commit. |
| MC-SCOPE-001 | Applicable | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. Actual EVD required; failed check blocks commit. |
| MC-TEST-001 | Applicable | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. Actual EVD required; failed check blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. Actual EVD required; failed check blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Exact semantic type/DTO/Command/Result/Enum names. Record actual evidence or scoped N/A separately. |
| Rule 2 | Applicable | Strict actual boundary, authorized context and exact proof, existing technical validation. Record actual evidence or scoped N/A separately. |
| Rule 3 | Applicable | Immutable records and defensive copies; no entity conversion bypass. Record actual evidence or scoped N/A separately. |
| Rule 4 | Not applicable | No managed business class/logger/factory wiring in this Step; exact technical profile unchanged. Record actual evidence or scoped N/A separately. |
| Rule 5 | Applicable | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. Record actual evidence or scoped N/A separately. |
| Rule 6 | Applicable | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. Record actual evidence or scoped N/A separately. |
| Rule 7 | Not applicable | No profile/config key touched in this Step. Record actual evidence or scoped N/A separately. |
| Rule 9 | Not applicable | No new policy/complex runtime selection in this Step. Record actual evidence or scoped N/A separately. |
| Rule 10 | Applicable | Existing java.time/DB precision, decision/source time, no global clock or execution claim. Record actual evidence or scoped N/A separately. |
| Rule 11 | Applicable | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. Record actual evidence or scoped N/A separately. |

### Step 4 — Implement same-source native recovery and shared eligibility

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007
- Dependencies: Exact Step3 semantic commit and fresh Root current-Step lock
- Baseline state: Steps1–3 verified committed; current-Step behavior absent.
- Observable outcome: Implement same-source native recovery and shared eligibility, with current contract and old behavior preserved.
- End state: Exact 11 paths; real finite assertion gates and strict/source/SQL checks, one semantic commit.
- Test-first gate: Required — expected missing type/resource/policy assertion RED, compile A, typed behavior RED, minimal B and GREEN. Failures count only as actual intended JUnit F with E0/S0.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — independently close each scoped row, Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecificationTest.java`

- Purpose: Fix the closed decision table and semantic version before implementing recovery.
- Symbols: `WorkerRecoverySpecificationTest` with `missingSpecificationSeam` (temporary reflection gate), `dispositionPrecedenceAndReportedFailureAreIndependent`, `admitsExactlyOneExhaustedReadOnlyOccurrence`, `rejectsIncompleteContradictoryAndUnorderedHistory`, `versionBindsExactOwnerNativeAndEvidenceButNotClock`.
- Repository evidence: `ProcessControlServiceTest` uses JUnit 5 and fixed actor, native and UTC fixtures. `FlowableWorkerServiceTest` verifies the existing V4 failure hash and Worker binding branches. Accepted Spec §7.3.2 and pinned recovery worker-failure evidence define the complete fixture matrix.
- Dependencies and consumers: Earlier contract DTOs, existing `TaskOrigin`, `Definition`, digest and Worker enums, File 9 immutable facts/decision, test-only JUnit 5 fixtures.
- Why now: The first gate fixes the missing Specification API and the later typed tests constrain the shared policy independently of transaction orchestration.
- Contract/signature changes: Test package-private `WorkerRecoverySpecification.evaluate(Facts)` and its `Decision` output. Reflection uses the exact class name as text and asserts expected presence; once phase A declares the compile surface, replace the reflection probe with the typed test. Test fixtures name actual facts and construct correct hashes from existing `ProcessRequestDigest`.
- Input/output and state mapping: Table-drive exact status, reason, separately reported failure, safe activity and version nullability. Include earlier EXPIRED and RETRY_SCHEDULED rows before the last NEEDS_MANUAL / RETRYABLE. Reverse input order and vary DB observation time while keeping canonical evidence constant. Independently vary bound owner kind/issuer/subject, START hash, definition SHA, root/native revision, execution, native scope/topic/correlation, and selected/prior evidence. Assert a version changes whenever the otherwise eligible canonical fact changes, using a consistent companion fixture for dependent facts.
- Error and edge behavior: Cover V4-only historical, native-only, unsupported kind/handler, Case/Agent provenance, collision, suspended or missing root, affinity/timestamps (ties, equal boundary, inverted, overlapping), wrong safe failure message, live/clock-expired LEASED, invalid state fields, COMPLETED, earlier UNKNOWN and business rejection, prior manual retry, absent/drifted binding, unsupported effect, cap 32 versus 33. Test UNKNOWN > BUSINESS_REJECTED > RETRYABLE on complete state-valid evidence and UNVERIFIED for incomplete or contradictory affinity. Verify earlier high-priority manual reasons remain stable even if a failure can still be safely explained.
- Standards impact: MC-NAME-001, MC-MODEL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001. Model named immutable fixtures with UTC `Instant`; shared Specification drives the same closed decisions in both paths.
- Literal rule enforcement: Rule 5 JDK/existing test tools, Rule 9 predicate combinations and precedence, Rule 10 fixed UTC values, Rule 11 existing technical module and finite checks.
- Implementation pseudocode:

```java
@Test void admitsExactlyOneExhaustedReadOnlyOccurrence() {
    var facts = ordinaryBpmnFacts(history(expiredAtLeaseEnd(), earlierRetryable(), latestNeedsManualRetryable()));
    var decision = specification.evaluate(facts);
    assertEquals(RETRY_AVAILABLE, decision.status());
    assertEquals(NONE, decision.reason());
    assertEquals(RETRYABLE, decision.reportedFailure());
    assertTrue(decision.version().matches("[0-9a-f]{64}"));
    assertEquals(latestNeedsManualRetryable().decidedAt(), decision.qualifying().decidedAt());
}
@Test void rejectsIncompleteContradictoryAndUnorderedHistory() {
    for (var example : fixedDecisionCases()) {
        var actual = specification.evaluate(example.facts());
        assertEquals(example.expectedDisposition(), actual.disposition());
        assertNull(actual.version());
    }
}
```

- Verification contribution: `WorkerRecoverySpecificationTest` reflection RED expects the missing class. Typed RED expects the explicit incomplete phase-A implementation or a safe-negative branch on the positive fixture. Typed GREEN requires all matrix, shared digest and redaction assertions to pass.
- After this file: Reflection source compiles without target types. Typed fixture work can be prepared with the phase A interfaces and is activated before phase B behavior edits.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryServiceTest.java`

- Purpose: Freeze the Query/Command entry points, same-source transaction order, replay, bounded reads and failure ownership.
- Symbols: `ProcessWorkerRecoveryServiceTest` with temporary `missingServiceSeam`, then `readsOneBoundedRepeatableSnapshot`, `rejectsEachCandidateOverflowButKeepsPerJobHistoryOverflowManual`, `provesBindingUsingCurrentExactReporterAndWorkerInputDigest`, `readsV4SqlWithoutSecretsOrPrematureStateFilters`, `restoresWithExactLocksReadbackAndAudit`, `replaysExactCommandBeforeAnyNativeRead`, `rechecksWinnerAndRejectsConflictingOrPendingReplay`, `rollsBackBlockedStaleAndEveryPostMoveFailure`, `classifiesOnlyRecognizedRacesAfterRollback`.
- Repository evidence: `ProcessControlServiceTest` mocks transaction manager/store/native services and exercises command order and rollback; `FlowableWorkerServiceTest` supplies real registry plus mocked native/release fixtures.
- Dependencies and consumers: File 10, Step 3 audit store and receipt methods, File 5 native access, real immutable earlier Step 1 contracts, mocked `DataSourceTransactionManager` and `ManagementService`.
- Why now: Tests prevent a superficially successful native call from bypassing fences, old state, or transaction rollback.
- Contract/signature changes: Test public `workerFailures(ProcessActor, GetProcessInstanceQuery)` and `recoverWorker(ProcessActor, RecoverProcessWorkerCommand)` directly. Test a real shared Worker binding seam with controlled model, release, registry and variable fixtures. Use reflection only for the initial absence RED.
- Input/output and state mapping: Verify transaction definitions read-only REPEATABLE_READ/read-write READ_COMMITTED and timeouts 10/5, prerequisite before business SQL, exact USER/SERVICE and missing/wrong START, ambient Spring or Flowable refusal, source mismatch, DB UTC observation, bounded disjoint/overlapping sets including non-ASCII UTF-8 order and no leaked diagnostics. In write tests check START → command lock/replay or reservation → root → deadletter → full history → current binding → move(…,1) → readback → DB time → detail → generic apply → commit. Compare original acceptance and exact replay with a progressed job, change each reason/version/target and exercise concurrent reservation winner.
- Error and edge behavior: Inject no root/deadletter, ineligible/stale, native null/affinity mismatch, bad readback, audit false, unrelated insert/SQL integrity, APPLY count failure, commit failure and dependency/time failures. Mocked transaction verifies rollback for callback failures and no return on failed commit; commit uncertainty is resolved by later exact replay. Verify only native stage disappearance/optimistic conflicts and states 40001/40P01/55P03 become version conflicts, 57014/class 08/unrelated errors become unavailable, and no automatic retry. For wrong job use a foreign native row fixture and verify no non-scoped lock or native move.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001. Verify separate boundary actor from selected reporter, immutable result construction, shared Specification and DB-clock semantics.
- Literal rule enforcement: Rule 2 validated CQE inputs and rechecked authority, Rule 5 existing framework mocks/JDK, Rule 9 same shared decision on both operations, Rule 10 UTC fixtures, Rule 11 current technical Query/Command and one transaction.
- Implementation pseudocode:

```java
@Test void restoresWithExactLocksReadbackAndAudit() {
    var command = sameVersionAsObservation();
    var result = service.recoverWorker(owner, command);
    var ordered = inOrder(receipts, nativeAccess, worker, audit, transactionManager);
    ordered.verify(receipts).lockStartByInstance(owner, command.instanceId());
    ordered.verify(receipts).lockIfPresent(owner, PROCESS_WORKER_RECOVER, command.commandId());
    ordered.verify(receipts).insert(owner, PROCESS_WORKER_RECOVER, command.commandId(), commandHash, releaseKey, correlationId);
    ordered.verify(nativeAccess).lockRoot(start);
    ordered.verify(nativeAccess).lockRecoveryDeadletter(start, definition, command.jobId());
    ordered.verify(worker).readRecoveryHistory(owner.tenantId(), command.jobId());
    ordered.verify(nativeAccess).moveAndVerifyRecovery(fencedDeadletter); // File 3 verifies ManagementService retry=1 and readback
    ordered.verify(audit).decisionTime();
    ordered.verify(audit).insert(expectedAudit);
    ordered.verify(receipts).apply(owner, PROCESS_WORKER_RECOVER, commandId, jobId, null);
    ordered.verify(transactionManager).commit(any());
    assertEquals(new RecoverProcessWorkerResult(instanceId, jobId, jobId, commandId, RETRY_SCHEDULED, 1, decisionTime), result);
}
@Test void rollsBackBlockedStaleAndEveryPostMoveFailure() {
    when(audit.insert(any())).thenReturn(false);
    assertThrows(ProcessWorkerRecoveryBlockedException.class, () -> service.recoverWorker(owner, eligibleCommand));
    verify(transactionManager).rollback(any());
    verify(receipts, never()).apply(any(), any(), any(), any(), any());
    // Repeat using failed readback, SQLState and generic apply fixtures.
}
```

- Verification contribution: `ProcessWorkerRecoveryServiceTest` reflection RED expects missing public service seam. Typed RED requires positive acceptance/read test failure against phase A. Typed GREEN covers TEST-002/004/006 with JDBC/engine doubles only.
- After this file: Reflection runs on baseline. Typed verification awaits phase A declarations; no listener, database, scheduler or real engine is created by fixtures.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java`

- Purpose: Verify actual SQL projection, scopes, bounds, locks, collision detection and native move readback at the seam that owns them.
- Symbols: Existing `ProcessControlNativeAccessTest`; add `observesAllScopedKindsWithSentinel`, `locksOnlyExactOrdinaryDeadletter`, `comparesFailureCodeWithoutReadingExceptionText`, `probesAllRuntimeTablesById`, `requiresExactFlushedExternalReadback`.
- Repository evidence: Existing class uses mocked `JdbcTemplate`, `RowMapper` and `ResultSet`, fake Spring resource binding and mock Flowable services. Pinned DDL/mappers and native-lock evidence document the actual tables and command flush.
- Dependencies and consumers: File 5 and the service integration test. Temporary reflection can assert `lockRecoveryDeadletter(TaskOrigin, Definition, String)` is missing without referencing new projections; typed tests replace reflection after A.
- Why now: Native SQL contracts are proved before relying on higher-level mocks. Existing Control test cases stay in the same selector.
- Contract/signature changes: Add assertions against bounded SQL shape and mapped results through the File 5 methods. Test transaction/source/Flowable context, native return mismatch and postconditions independently.
- Input/output and state mapping: Capture prepared statement text and exact argument order. Assert discovery tenant+instance only and limit 129; lock includes job/tenant/instance/definition/type and `FOR UPDATE`. Assert comparison reads only boolean `octet_length` + C equality, including trailing space, null and unequal cases. Check all five runtime tables via ID-only PK probes, no arbitrary schema; old native-only positive row and unsupported anomaly remain distinguishable. Readback checks identical scope/affinity/type/handler/topic, positive revision, retries 1, null locks, and the spec’s correlation and create-time rules.
- Error and edge behavior: Foreign target never reaches an unscoped lock. All five tables can produce a collision. Reject before native invocation in existing Flowable command context. A public return without flushed move, old deadletter still present, missing external, mismatched identity, extra runtime row or nonnull lock fails the seam.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Proves SQL invariants with finite fixtures, keeps existing Control native behavior verified.
- Literal rule enforcement: Rule 5 existing JDBC/Mockito/JDK, Rule 10 database timestamp fixtures, Rule 11 select-only native fence in the existing module.
- Implementation pseudocode:

```java
@Test void locksOnlyExactOrdinaryDeadletter() {
    try (var transaction = bindMockSource()) {
        assertTrue(access.lockRecoveryDeadletter(start, definition, "foreign-job").isEmpty());
        assertQueryBinds("id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?",
                         "foreign-job", tenantText, instanceId, definition.engineDefinitionId());
        assertQueryIncludes("type_='externalWorker'", "FOR UPDATE");
        assertNoSelectOf("exception_msg_", "exception_stack_id_", "retries_", "lock_owner_", "lock_exp_time_");
    }
}
@Test void requiresExactFlushedExternalReadback() {
    var old = scopedDeadletter();
    access.moveAndVerifyRecovery(old);
    verify(management).moveDeadLetterJobToExecutableJob(old.jobId(), 1);
    assertReadbackUsesBoundSourceAndIdOnlyRuntimeProbes();
}
```

- Verification contribution: `ProcessControlNativeAccessTest` protects prior Control plus new TEST-002/006 SQL shape and fail-closed readback; phase A positive tests remain RED until SQL behavior exists.
- After this file: Existing tests remain selected. New reflection or typed missing-behavior RED is isolated from datasource/runtime startup.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessWorkerRecoveryBlockedException.java`

- Purpose: Carry one fixed safe recovery refusal across rollback to the API error adapter.
- Symbols: `ProcessWorkerRecoveryBlockedException`, public no-arg constructor.
- Repository evidence: `ProcessCancellationBlockedException` provides the existing dedicated no-arg runtime exception pattern; recovery has a distinct fixed code in accepted Spec §9.2.
- Dependencies and consumers: File 10 catches or propagates this specific type, Step 5 `ProcessApiErrors` maps it. Step 3 remains compile-independent and returns targeted insert false.
- Why now: Typed service RED and the audit conflict branch need the fixed exception before orchestration.
- Contract/signature changes: Public final RuntimeException with fixed non-sensitive text and no dynamic fields, cause or formatting.
- Input/output and state mapping: Ineligible/absent scoped current job or consumed job audit → same type → rollback → fixed 409 handler.
- Error and edge behavior: SQL/commit/readback/dependency failures keep separate handling and never use this type by convenience.
- Standards impact: MC-NAME-001, MC-SCOPE-001, MC-TEST-001. Exceptions use the existing semantic suffix and no Spring/JSON annotation.
- Literal rule enforcement: Rule 5 JDK RuntimeException, Rule 11 existing error package and no new delivery layer.
- Implementation pseudocode:

```java
public final class ProcessWorkerRecoveryBlockedException extends RuntimeException {
    public ProcessWorkerRecoveryBlockedException() {
        super("Process Worker recovery is blocked");
    }
}
```

- Verification contribution: Service negative branch assertions and subsequent Step 5 safe error mapping. No body reflects exception text.
- After this file: Typed exceptions can compile before the service body and the API adapter remain to be implemented.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`

- Purpose: Own bounded source-bound native facts, fences and post-move proof for recovery.
- Symbols: `verifyRecoveryTransaction()`, `readRecoveryRoot(TaskOrigin)`, `recoveryHistoryDefinitionId(TaskOrigin)`, `findRecoveryDeadletters(TaskOrigin)`, `lockRecoveryDeadletter(TaskOrigin, Definition, String)`, `readRecoveryExecution(TaskOrigin, Definition, RecoveryDeadletter)`, `hasRecoveryCollision(String)`, `recoveryFailureCodeMatches(RecoveryDeadletter, String)`, `moveAndVerifyRecovery(RecoveryDeadletter)`; immutable `RecoveryDeadletter` and `RecoveryExecution` facts.
- Repository evidence: Constructor checks exact configured DataSource/proxy/TM/schema/prefix/history/services, initializes five-second JDBC timeout; `readRoot(Scope, boolean)`, `requireTransaction()`, `now()` and execution affinity are live reusable seams. Pinned Flowable 8 DDL and evidence show no persisted deadletter retry/lock fields, BPMN process fields with nullable native scope, exact Job move and post-flush external table.
- Dependencies and consumers: Existing owned Spring Flowable configuration, standard Flowable management/runtime/history, JDBC and File 10. Existing cancellation native APIs and wait/cancel classification continue their current path. Source prerequisites are checked per recovery transaction before application reads and again at the mutation boundary.
- Why now: Establish native facts and the only mutation seam before building admission and command orchestration.
- Contract/signature changes: Add owner-local scoped technical queries and checked move; derive only quoted schema from constructor-verified engine config. `RecoveryDeadletter` carries ID, nullable/positive-checkable revision, type, tenant, process, definition, execution, element, scope ID/sub-scope ID/type/definition, correlation, handler and topic. No exposed raw exception, byte pointer, variables or lease fields. Read-time malformed routing facts remain typed negative where a safe scoped row can be projected; unsafe exposed job IDs/row shapes yield bounded unavailable.
- Input/output and state mapping: Current root by existing readRoot(false)/lockRoot(start), exact historic BPMN instance tenant/ID/end time to definition ID when no live root. Discovery reads every scoped deadletter kind; lock uses target PK with owner/definition/type predicates. Exact execution tenant/process/definition/ACT_ID must match. Discover all same-ID runtime table presence with fixed PK EXISTS probes on `act_ru_deadletter_job`, `act_ru_external_job`, `act_ru_job`, `act_ru_timer_job`, and `act_ru_suspended_job`; count table presence without affinity predicates so drift is visible.
- Error and edge behavior: Source bound, actual transaction, interrupt, primary/UTF8/origin and exact current schema required before recovery SQL; reject Flowable ambient context at service entry and immediately before move. Scoped null/missing target is blocked upstream; SQL/invariant failures stay unavailable. Bytewise code compare requires candidate’s exact scope/revision, safe V4 code, database equality under C and equal UTF-8 byte length. For postconditions allow create time to vary, preserved positive revision (no increment assumption) and native allocation of safe correlation only when original was null; reject every other drift, nonnull locks, wrong retries, absence or collision. Recognized target disappearance is typed only around this exact fenced move.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Fixed JDBC/Flowable seams with immutable facts; existing constructor checks and error convention retained, no new registration or libraries.
- Literal rule enforcement: Rule 2 validate native scope/parameters, Rule 3 immutable facts, Rule 5 JDBC/JDK and pinned Flowable APIs, Rule 10 java.time, Rule 11 exact source and technical read/lock SQL with Flowable-owned ACT writes.
- Implementation pseudocode:

```java
List<RecoveryDeadletter> findRecoveryDeadletters(TaskOrigin start) {
    requireTransaction();  // existing primary/UTF8/origin and resource guard
    return jdbc.query("""
        SELECT id_, rev_, type_, tenant_id_, process_instance_id_, proc_def_id_,
               execution_id_, element_id_, scope_id_, sub_scope_id_, scope_type_,
               scope_definition_id_, correlation_id_, handler_type_, handler_cfg_
        FROM <verified quoted schema>.act_ru_deadletter_job
        WHERE tenant_id_=? AND process_instance_id_=?
        ORDER BY id_ COLLATE "C" LIMIT 129
        """, secretFreeDeadletterRowMapper, tenantText(start), start.instanceId());
}
Optional<RecoveryDeadletter> lockRecoveryDeadletter(TaskOrigin start, Definition definition, String jobId) {
    requireTransaction();
    return checkedAtMostOne(jdbc.query(deadletterProjection + """
        WHERE id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?
          AND type_='externalWorker' FOR UPDATE
        """, secretFreeDeadletterRowMapper, jobId, tenantText(start), start.instanceId(),
        definition.engineDefinitionId()));
}
boolean recoveryFailureCodeMatches(RecoveryDeadletter job, String code) {
    requireTransaction();
    requireSafeFailureCode(code);
    return exactlyOneBoolean("""
        SELECT COALESCE(octet_length(exception_msg_) = octet_length(CAST(? AS text))
            AND exception_msg_ COLLATE "C" = CAST(? AS text) COLLATE "C", false)
        FROM <verified quoted schema>.act_ru_deadletter_job
        WHERE id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?
          AND type_='externalWorker' AND rev_=?
        """, code, code, job.jobId(), job.tenant(), job.instanceId(), job.definitionId(), job.revision());
}
void moveAndVerifyRecovery(RecoveryDeadletter fenced) {
    verifyRecoveryTransaction();
    requireNoAmbientFlowableContext(); // existing org.flowable.common.engine.impl.context.Context
    Job moved;
    try {
        moved = management.moveDeadLetterJobToExecutableJob(fenced.jobId(), 1);
    } catch (JobNotFoundException missing) {
        if (fenced.jobId().equals(missing.getJobId())) throw new ProcessVersionConflictException();
        throw unavailable();
    }
    requireMatchingPublicJobIdentityAndAffinity(moved, fenced); // public Job/JobInfo getters, never diagnostics
    // Outermost command has returned and flushed. Probe all five tables by ID on the same source.
    requireOnlyExternalRuntimeRow(fenced.jobId());
    requireExactExternalReadback(fenced, 1); // ACT_RU_EXTERNAL_JOB plus retries_,lock_owner_,lock_exp_time_
}
```

- Verification contribution: File 3 covers SQL, schema-qualified prepared parameters, stored projection, lock ordering seam, readback and all collision probes; File 2 covers whole transactional orchestration.
- After this file: Phase A exposes compilable facts/signatures; phase B enables verified queries and exact native move. No ACT insert/update/delete, engine command wrapper or competing source is introduced.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerLeaseStore.java`

- Purpose: Read bounded, secret-free V4 candidate IDs and full same-job recovery history in the existing source-bound store.
- Symbols: `findFailureJobIds(long tenantId, String instanceId)`, `readRecoveryHistory(long tenantId, String jobId)`, immutable `RecoveryLeaseEvidence`.
- Repository evidence: Current store verifies DataSource/TM/schema and already binds application SQL to the actual owning transaction. V4 has `UNIQUE(tenant_id,job_id,native_owner)`, distinct LEASED-only index, and exact nullable state rules. Token-scoped `find/lock/finish` return the full token-bearing `Lease` and cannot serve the owner history read.
- Dependencies and consumers: Existing store is owned by `FlowableWorkerService`, which delegates only these two read methods. Files 9 and 10 consume the secret-free projection. V4 DDL and all acquire/fail/complete methods are preserved.
- Why now: Gather all possibly contradictory rows before the Specification is allowed to reason about eligibility.
- Contract/signature changes: Candidate IDs returned in C order with a 129th sentinel. Per-job list returns up to 33 all-state rows, with tenant, reporter issuer/subject, topic/schema, job/occurrence, scope, release/definition/element, intent/model/input/result hashes, state/decision/failure and timestamps; project only a business-receipt presence flag needed to validate state shape, and never project token/hash/nativeOwner or raw JSON.
- Input/output and state mapping: Full history WHERE tenant+job only. Read scope and state without prefilter and preserve malformed/unknown values for negative proof. SQL may order by `leased_at` for bounded results; only File 9 orders complete history canonically after rejecting equal timestamps. Convert timestamptz explicitly to UTC `Instant`.
- Error and edge behavior: Validate input and require owning transaction; duplicates or over-cap data are never truncated into an eligible proof. Malformed fields fail closed, per-job over-cap becomes manual, source/dependency failures remain unavailable. No new index, row lock, state change or lease reopening.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. Named immutable read facts in existing technical persistence; current V4 row write rules remain authoritative.
- Literal rule enforcement: Rule 2 owner-scope inputs and revalidation, Rule 3 immutable facts, Rule 5 existing JDBC/JDK, Rule 10 explicit Instant, Rule 11 local technical persistence with unchanged schema.
- Implementation pseudocode:

```java
List<String> findFailureJobIds(long tenantId, String instanceId) {
    requireTransaction();
    return jdbc.queryForList("""
        SELECT job_id FROM <verified lease table>
        WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND failure_kind IS NOT NULL
        GROUP BY job_id ORDER BY job_id COLLATE "C" LIMIT 129
        """, String.class, tenantId, instanceId);
}
List<RecoveryLeaseEvidence> readRecoveryHistory(long tenantId, String jobId) {
    requireTransaction();
    return jdbc.query("""
        SELECT tenant_id,subject_issuer,subject_id,topic,schema_version,job_id,occurrence_id,
               scope_type,scope_id,release_key,definition_id,element_id,idempotency_key,
               model_sha256,input_sha256,result_sha256,state,decided_at,failure_kind,failure_code,
               leased_at,lease_expires_at,business_receipt_id IS NOT NULL AS has_business_receipt
        FROM <verified lease table> WHERE tenant_id=? AND job_id=? LIMIT 33
        """, secretFreeEvidenceMapper, tenantId, jobId);
}
```

- Verification contribution: File 2 exercises the actual WorkerLeaseStore with mocked JDBC/result rows under a bound test source and captures the exact SQL predicate shape/caps, forbidden columns and contradiction retention, with existing `FlowableWorkerServiceTest` protecting writes. No new database-executing selector.
- After this file: Phase A facts and read methods compile; phase B provides bounded evidence used only by owner-local service.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerTopicRegistry.java`

- Purpose: Reuse the exact current registration key for evidence without granting another actor’s Worker authority.
- Symbols: Package-local `findRecoveryRegistration(long tenantId, String issuerFingerprint, String subjectId, String topic, String schemaVersion)`, existing `Key` and `require(ProcessActor, String, String)`.
- Repository evidence: Constructor indexes registrations by tenant, verified issuer fingerprint, exact subject, topic and schema. `require` enforces verified SERVICE and uses that key. Per-reporter concurrency/duration can differ even for a shared model schema.
- Dependencies and consumers: File 8 only; existing `WorkerController`, acquire/complete/fail and model validation stay on their original authorization paths.
- Why now: File 8 needs a read-only exact lookup before it can reuse the input digest safely.
- Contract/signature changes: Optional package-local lookup on the existing key map. Reuse key construction with the existing authority-bearing path while retaining its SERVICE/LEGACY checks and public signature.
- Input/output and state mapping: Persisted exact reporter tuple and supported topic/schema → same `Registration` value as for that reporter’s current configured key. No derived bearer/actor is sent to a mutation path.
- Error and edge behavior: Malformed tuple or no exact current registration yields unverified binding. No fallback to same issuer, another subject, wildcard topic, older schema, tenant default or same policy from another reporter.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001. Shared registry identity key and already-configured Bean reuse; no new config.
- Literal rule enforcement: Rule 2 exact validated inputs and read-only separation, Rule 5 JDK existing collections, Rule 11 reuse within the existing worker package.
- Implementation pseudocode:

```java
Optional<Registration> findRecoveryRegistration(long tenantId, String issuerFingerprint,
        String subjectId, String topic, String schemaVersion) {
    if (!safeVerifiedReporterTuple(tenantId, issuerFingerprint, subjectId)) return Optional.empty();
    // Validate exact current topic/schema with ProcessModelValidator.nativeWorkerTopic.
    return Optional.ofNullable(registrations.get(new Key(tenantId, issuerFingerprint, subjectId, topic, schemaVersion)));
}
```

- Verification contribution: File 2 actual Worker registry fixture and finite `WorkerTopicRegistryTest`; same issuer/different subject, same topic/different version and wrong tenant must miss.
- After this file: Exact registration can be read without a Worker token. Existing public authority validation is retained.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java`

- Purpose: Verify the current released ordinary Worker binding and reconstruct the original input digest without acquiring, reporting or executing a task.
- Symbols: `usesRecoveryStorage(SpringProcessEngineConfiguration, DataSource)`, `findFailureJobIds(long, String)`, `readRecoveryHistory(long, String)`, `readRecoveryProvenance(TaskOrigin, Definition, RecoveryDeadletter)`, `verifyRecoveryBinding(TaskOrigin, Definition, RecoveryDeadletter, RecoveryProvenance, RecoveryLeaseEvidence)`, existing `verifyJob`, `inputHash`, existing occurrence-key derivation and immutable binding projection.
- Repository evidence: `acquire` computes `worker-occurrence-v1` and `worker-input-v2`; `verifyJob` checks exact model, topic, release, business key, current START correlation, linked-wait mode, DMN/mapping validation and mapped exact execution variables. `WorkerLeaseStore` and its manager already belong to this service.
- Dependencies and consumers: Files 5–7; existing `ProcessReleaseService.getForProcessDefinition`, `VerifiedProcessModels.runningProcess`, `ProcessValues.inputs`, `ProcessModelValidator`, `WorkerTopicRegistry`. File 9 consumes only current ordinary/effect/verified digest facts. File 10 uses the two guarded V4 reads without another store Bean.
- Why now: Establish the single reusable Worker proof before the policy and command can consume it.
- Contract/signature changes: Add an owner-local metadata-only `RecoveryProvenance` value and read-only selected-evidence verification. The value keeps the already-verified released model for this occurrence inside the Worker service, with only ordinary/unsupported/unverified checks used by the caller. Narrowly extract shared job identity input (no fake lock owner/expiry/live fields for deadletter). Reuse `worker-occurrence-v1` as a static pure derivation called by acquire and recovery evidence checks. Preserve existing public Worker contracts.
- Input/output and state mapping: Exact historical reporter → current registry entry; START + native ID/instance/execution/definition/activity/tenant → one exact released BPMN definition and verified metadata. Check model worker task and ordinary provenance for this occurrence, reject Agent-linked wait/internal registration or Case-specific native/model provenance. Recompute current Worker input hash by shared verifyJob/inputHash and compare selected V4 model/input/release/topic/schema/occurrence/intent/START/root affinity, exposing only effect and verified facts. For no selected V4 row the released exact element can still establish or refute ordinary provenance for the observation precedence; return no proof of selected reporter/input.
- Error and edge behavior: Missing or changed current release/registration/model/mappings/inputs is unverified binding. Known unsupported provenance is separate. Native data/source/SQL timeout failures propagate to the outer transaction boundary; only expected binding mismatches map to a policy fact. Prior rows retain shape and shared affinity checks without falsely applying the selected reporter's current digest. No network, Worker token, response diagnostic, DB writes, fallback mode, or alternate digest.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001. Keep existing named Worker factory and owned source, add immutable digest facts with no raw variable leakage. State policy stays in File 9.
- Literal rule enforcement: Rule 2 recheck exact metadata and source, Rule 3 immutable technical facts, Rule 5 reuse current framework/JDK, Rule 9 supply current binding to shared Specification, Rule 10 existing Duration/Instant, Rule 11 current technical package and CQE boundary.
- Implementation pseudocode:

```java
RecoveryProvenance readRecoveryProvenance(TaskOrigin start, Definition definition, RecoveryDeadletter job) {
    requireOwningBoundTransaction();
    // Share this exact released-model lookup and validation with existing verifyJob.
    var released = verifiedJobModel(tenant(start), job.identity());
    requireOneExactDefinitionAndStartRelease(released, definition, start);
    if (knownCaseOrAgentProvenance(released, job)) return RecoveryProvenance.unsupported();
    if (!provedOrdinaryReleasedWorker(released, job)) return RecoveryProvenance.unverified();
    return RecoveryProvenance.ordinary(released);
}
RecoveryBinding verifyRecoveryBinding(TaskOrigin start, Definition definition, RecoveryDeadletter job,
                                      RecoveryProvenance provenance, RecoveryLeaseEvidence selected) {
    requireOwningBoundTransaction();
    if (!provenance.ordinary()) return RecoveryBinding.unverified();
    var reporter = registry.findRecoveryRegistration(selected.tenantId(), selected.subjectIssuer(),
        selected.subjectId(), selected.topic(), selected.schemaVersion());
    if (reporter.isEmpty()) return RecoveryBinding.unverified();
    var registration = reporter.get();
    var verified = verifyJob(tenant(start), job.identity(), registration, provenance.released()); // share existing checks after release selection
    if (!matchesExactStartReleaseDefinitionAndSelectedEvidence(verified, start, definition, job, selected)
            || !selected.inputSha256().equals(inputHash(job.identity(), registration, verified.inputs()))
            || !selected.idempotencyKey().equals(workerOccurrenceKey(tenant(start), job.jobId()))) {
        return RecoveryBinding.unverified();
    }
    return RecoveryBinding.verified(registration.effectSafety(), verified.businessKey());
}
```

- Verification contribution: File 2 uses the existing Worker fixture style with actual Worker binding and registry and mocked repository/runtime/JDBC seams. It exercises actual shared digest, exact reporter, wrong releases/native inputs and effect drift. `FlowableWorkerServiceTest` and `WorkerTopicRegistryTest` guard behavior of existing Worker flows.
- After this file: Phase A type surface compiles. Phase B enables read-only same-source proof using the existing Worker logic and secret-free lease reads. Existing release read templates join the already-owned JDBC transaction through the exact manager; preserve outer isolation and reject an incompatible manager rather than opening a second transaction.

#### File 9 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecification.java`

- Purpose: Apply one composed Specification to scoped technical facts for both observation and locked admission.
- Symbols: Package-local `WorkerRecoverySpecification`, `evaluate(Facts)`, immutable nested `Facts` / `Decision`, `READ_ONLY_EXHAUSTED_RETRY_V1`, shared exact evidence and version framing.
- Repository evidence: `ProcessRequestDigest.hash(List<String>, Map<String,Object>)` delegates to current length-delimited hashing. V4 shape comes from immutable V4 SQL and `FlowableWorkerService.fail`; the full canonical decision table and digest fields are accepted Spec §7.3.2.
- Dependencies and consumers: Earlier closed DTO and enums, Files 5–8 technical facts and current read-only proof, File 10 both paths. No Bean lookup or dynamic policy registration. Callbacks for candidate code equality/current Worker proof are narrow existing seam calls on the caller’s already bound source and evaluated only once as needed. Keep them internal; no I/O beyond same-source reads.
- Why now: Establish one shared precedence/canonicalization implementation before enabling transaction orchestration.
- Contract/signature changes: Facts carry verified START/definition, optional root and current scoped deadletter/execution, collision/audit flags, bounded complete or over-cap V4 evidence, and exact ordinary/native code/binding proofs. Decision carries public status/reason/reportedFailure/activityId/version and internal qualifying V4 row and evidence digest only when eligible.
- Input/output and state mapping: Independently validate reported failure via complete exact-affinity state-valid facts, with UNKNOWN > BUSINESS_REJECTED > RETRYABLE; distinguish empty NONE and over-cap or contradictory UNVERIFIED. Prove native activity from safe scoped exact definition/execution facts or use only one safe historical element from complete unambiguous exact-affinity V4. Apply in order historical / prior accepted / unsupported native or provenance / root inactive / structural conflict / active lease / binding unverified / incomplete / UNKNOWN / BUSINESS_REJECTED / verified unsupported effect / eligible. Lack of retained final failure evidence or over-cap is incomplete; a known final UNKNOWN/BUSINESS_REJECTED row is present evidence and keeps its explicit manual reason. Do not infer binding drift solely from unavailability of a selected row. Keep reportedFailure independent of structural and native failure reasons.
- Error and edge behavior: Current proof requires positive native revision, exact known root/activity/execution/tenant/definition and no conflicting scope, each row shares all Spec affinity, valid required/null fields and recomputed RETRYABLE hash. Reject COMPLETED as a contrary state, every LEASED even after expiry, duplicate/equal/reversed/overlapping timestamps, extra manual rows and any state outside the sole positive sequence. EXPIRED requires decidedAt at or after expiry; other decisions require strict interior; every earlier leasedAt and decidedAt strictly precedes next leasedAt. Select the final sole NEEDS_MANUAL / RETRYABLE only after canonical ordering. Invalid selected failure code or mismatching DB boolean disqualifies. No noneligible result has a version, regardless of whether it has independently explainable failure.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001. Composed Specification with named predicates and fixed evaluation sequence; no parallel query/write rule implementation. No mapper framework invocation for private immutable technical facts or explicit invariant-aware public constructor projection.
- Literal rule enforcement: Rule 2 validate every factual boundary before projection; Rule 3 immutable named records; Rule 5 current digest/JDK; Rule 9 one approved composed Specification; Rule 10 strict Instant comparisons and UTC encoding; Rule 11 existing execution component with no new layer.
- Implementation pseudocode:

```java
Decision evaluate(Facts facts) {
    var history = inspectBoundedEvidence(facts); // exact affinity, states, clocks, all retry hashes
    var reported = reportedFailure(history); // independent complete state-valid facts
    var activityId = safeActivityProjection(facts, history);
    if (facts.deadletter() == null) return historical(history, reported, activityId);
    if (facts.alreadyAccepted()) return manual(RESTORE_LIMIT_REACHED, reported, activityId);
    if (unsupportedNativeKindOrScope(facts)) return manual(UNSUPPORTED_NATIVE_JOB, reported, activityId);
    var selected = history.soleLastNeedsManualRetryableCandidate(); // candidate, never an acceptance
    var provenance = facts.ordinaryReleasedProvenance().get();
    if (provenance.provesUnsupported()) return manual(UNSUPPORTED_NATIVE_JOB, reported, activityId);
    if (absentOrSuspended(facts.root())) return manual(PROCESS_INACTIVE, reported, activityId);
    if (conflictingNativeScopeExecutionOrHistory(facts, history)
            || selected != null && !facts.failureCodeMatches().test(selected)) {
        return manual(EVIDENCE_CONFLICT, reported, activityId);
    }
    if (history.hasLeased()) return manual(ACTIVE_LEASE, reported, activityId);
    var binding = currentBindingIfApplicable(facts, provenance, selected);
    if (provenance.provesUnverified() || binding.provesUnverified()
            || binding.verified() && !rootBusinessKeyMatches(facts, binding)) return manual(BINDING_UNVERIFIED, reported, activityId);
    if (history.overCap() || !history.hasRetainedFinalFailureEvidence()) return manual(HISTORY_INCOMPLETE, reported, activityId);
    if (reported == ReportedFailureEnum.UNKNOWN) return manual(UNKNOWN_OUTCOME, reported, activityId);
    if (reported == ReportedFailureEnum.BUSINESS_REJECTED) return manual(ReasonEnum.BUSINESS_REJECTED, reported, activityId);
    if (!strictPositiveSequence(history, selected)) return manual(EVIDENCE_CONFLICT, reported, activityId);
    if (!binding.verified()) return manual(BINDING_UNVERIFIED, reported, activityId);
    if (binding.effect() != WorkerEffect.READ_ONLY) return manual(UNSUPPORTED_EFFECT, reported, activityId);

    var evidenceHash = ProcessRequestDigest.hash(evidenceFields("TIANJI_WORKER_RECOVERY_EVIDENCE_V1",
            "READ_ONLY_EXHAUSTED_RETRY_V1", history.byStrictLeasedAt()), Map.of());
    var version = ProcessRequestDigest.hash(versionFields("TIANJI_WORKER_RECOVERY_VERSION_V1", facts, evidenceHash), Map.of());
    return eligible(activityId, reported, version, evidenceHash, selected);
}
```

  Implement the first-applicable order in the mapping above even when several predicates are true. Resolve known unsupported model/registry provenance ahead of root/structural categories. Treat a failed exception comparison as structural before active lease or unverified binding when a qualifying candidate exists. Explainable UNKNOWN/rejection history without a positive sequence remains separately reported. For the historical branch over-cap/incomplete precedes explanation; when current native exists, `HISTORY_INCOMPLETE` applies to over-cap or absent final failure evidence, not an already known final UNKNOWN/BUSINESS_REJECTED and independently known negative facts still populate `reportedFailure`.

  Canonical version fields in order: owner decimal tenant / kind / issuer SHA / exact subject; START command / request hash / release / instance / correlation; released definition key / exact engine definition ID / model SHA / business key; root ID / revision / suspension and exact execution ID / activity / tenant / instance / definition; native job ID / revision / type / tenant / instance / definition / execution / element / scope ID / sub-scope / scope type / scope definition / handler / topic / correlation; evidence digest. Canonical evidence fields: domain, policy, decimal count, then each row in strict ascending leasedAt with exact tenant/job/occurrence/scope/release/definition/element/topic/schema/intent, model/input/result digests, state and nullable safe failure kind/code, reporter issuer and exact subject, leasedAt/leaseExpiresAt/decidedAt. Encode nullable values as explicit presence tag followed by exact value only when present; use stable UTF-8/decimal/Instant strings. Exclude raw tokens and hashes, native owner/raw exception, input values, observation time, and engine create time.

- Verification contribution: File 1 defines typed behavioral RED before production body and GREEN only when precedence, canonical framing, strict clocks and wrong-state outcomes all pass. File 2 proves both entry points use this decision.
- After this file: Phase A declarations compile; phase B produces an eligible version only for complete admitted evidence.

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryService.java`

- Purpose: Own the two bounded transactions, exact replay and atomic one-job restoration.
- Symbols: `ProcessWorkerRecoveryService`, `workerFailures(ProcessActor, GetProcessInstanceQuery)`, `recoverWorker(ProcessActor, RecoverProcessWorkerCommand)`, private shared decision, exact replay and rollback classification.
- Repository evidence: `ProcessControlService` owns explicit transaction templates, fixed timeouts, START/root fences, exact release discovery and safe log/classifier conventions. `ProcessCommandReceiptStore.insert/lockIfPresent/lock/apply` already implement current command storage; `apply` enforces one-row transition. Existing `TianjiProcessAccess.require` and permission guard remain the authority-producing boundary in Step 5.
- Dependencies and consumers: Construct from `SpringProcessEngineConfiguration`, `ProcessReleaseService`, `ProcessCommandReceiptStore`, `ProcessControlNativeAccess`, `ProcessWorkerRecoveryReceiptStore`, `FlowableWorkerService`, all from one verified Step 5 owner-local factory. Reuse current engine manager/source and quoted schema, validate collaborator identity where exposed and ensure factory creates generic/release collaborators on that owner. Factory supplies already-authorized verified USER/SERVICE through Step 5, with both exact grants and no identity switch. File 9 is private owner-local decision composition.
- Why now: Native facts, binding, receipts and typed errors compile independently. Connect one bounded Query and one bounded Command to complete the tested service.
- Contract/signature changes: Public service methods match existing technical Process style with validated input record and verified `ProcessActor`. Reject LEGACY, null/unsafe call inputs and ambient Spring transaction, bound synchronization/source resource or ambient Flowable command context before entering owned templates. Validate root/START/source/definition exact agreement; no caller controls SQL schema or native route.
- Input/output and state mapping: Query owns read-only REPEATABLE_READ, timeout 10 seconds and local statement_timeout 5 seconds, one exact owner START snapshot, root or exact retained historical BPMN instance, one verified release/definition, cap+1 independent lists and unsigned UTF-8 byte ordering of distinct safe job IDs. 129 from either source or union >128 aborts entirely. For each candidate inspect full bounded history, exact current scope, V10 accepted, collision and shared facts, returning only safe public row fields. Sample DB `clock_timestamp()` in same transaction for response. Command owns write READ_COMMITTED, timeout 10 seconds and local lock/statement timeout 5 seconds. After exact START, replay or reserve same namespace, then root and exact deadletter fence. Resolve one active BPMN release from START and actual root definition, recheck exact release binding with `getForProcessDefinition`. Current ineligible is blocked before comparing version. An eligible same-version occurrence moves once with retries 1 and exact readback. Persist selected safe code, qualifying V4 decision Instant and fixed policy alongside immutable evidence hash/detail, then apply the generic row `resource_id = jobId`, null decision JSON. Return after transaction commit.
- Error and edge behavior: Exact current owner absence/foreign returns same 404. Retained inconsistent authority is unavailable. A hash mismatch for an APPLIED exact replay namespace is idempotency conflict; exact APPLIED request checks job/resource, release/correlation, null generic decision and exact immutable V10 detail under current START owner. A committed PENDING or inconsistent replay is 503. In a concurrent reservation loss, lock the winner and use the same exact replay validator. No locked current candidate, unsafe or prior restore gives typed blocked, including targeted audit unique-job conflict after native move. Classify only after rollback. Pass established fixed public exceptions; native optimistic/target disappearance from move stage and SQLSTATE 40001/40P01/55P03 are version conflicts for Command; 57014, class 08 and unrelated SQL/integrity/readback/commit/dependency failures are bounded unavailable. No text parsing, auto retry, V4 mutation or successful no-op.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001. Use the accepted technical-profile SLF4J `LoggerFactory.getLogger(ProcessWorkerRecoveryService.class)` pattern from `ProcessControlService`; log only safe outcome/tenant and bounded hashed identifiers where needed, with no reason, raw diagnostics, subject or exception data. The PS baseline has no Lombok compile dependency. Use explicit named Step 5 factory with exact qualifier parameters; owning constructor binds/validates dependencies/templates like current Process Control. Contract validation comes from earlier strict immutable input and verified access; private invariant-dependent projections are explicit closed DTO constructors.
- Literal rule enforcement: Rule 2 current request and cross-component invariants, Rule 3 immutable fact/result records, Rule 4 named factory/qualifiers and safe business log, Rule 5 current Spring/JDK/JDBC, Rule 9 one shared Specification, Rule 10 DB-clock Instant and timeout durations, Rule 11 existing technical Spring Command/Query boundary, no Event emitted.
- Implementation pseudocode:

```java
public ProcessWorkerFailuresDTO workerFailures(ProcessActor actor, GetProcessInstanceQuery query) {
    validateActorRequestAndNoAmbientContext(actor, query);
    return read.execute(status -> {
        nativeAccess.verifyRecoveryTransaction();
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        var start = requireExactStart(receipts.readStartByInstance(actor, query.instanceId()), actor, query.instanceId());
        var root = nativeAccess.readRecoveryRoot(start);
        var definition = exactReleasedBpmn(start, root.map(LockedRoot::engineDefinitionId)
                .orElseGet(() -> nativeAccess.recoveryHistoryDefinitionId(start)));
        var deadletters = nativeAccess.findRecoveryDeadletters(start);
        var failureIds = workers.findFailureJobIds(actor.tenantId(), start.instanceId());
        var ids = checkedDistinctUtf8Order(deadletters, failureIds, 128);
        var rows = new ArrayList<ProcessWorkerFailureDTO>();
        for (String jobId : ids) {
            var scoped = deadletterFor(jobId, deadletters);
            var facts = recoveryFacts(start, definition, root, scoped, jobId);
            rows.add(toClosedFailureRow(jobId, scoped.isPresent(), specification.evaluate(facts)));
        }
        return new ProcessWorkerFailuresDTO(start.instanceId(), nativeAccess.now(), rows);
    }); // normalize dependency/limit failures outside the transaction
}

public RecoverProcessWorkerResult recoverWorker(ProcessActor actor, RecoverProcessWorkerCommand command) {
    validateActorRequestAndNoAmbientContext(actor, command);
    String requestHash = ProcessRequestDigest.hash(List.of("TIANJI_WORKER_RECOVERY_COMMAND_V1",
            command.instanceId(), command.jobId(), command.commandId(), command.expectedVersion(), command.reason()), Map.of());
    try {
        return write.execute(status -> {
            nativeAccess.verifyRecoveryTransaction();
            jdbc.execute("SET LOCAL statement_timeout='5s'");
            jdbc.execute("SET LOCAL lock_timeout='5s'");
            var start = requireExactStart(receipts.lockStartByInstance(actor, command.instanceId()), actor, command.instanceId());
            var prior = receipts.lockIfPresent(actor, PROCESS_WORKER_RECOVER, command.commandId());
            if (prior.isPresent()) return exactReplay(prior.get(), actor, start, command, requestHash);
            if (!receipts.insert(actor, PROCESS_WORKER_RECOVER, command.commandId(), requestHash, start.releaseKey(), start.correlationId())) {
                var winner = receipts.lockIfPresent(actor, PROCESS_WORKER_RECOVER, command.commandId())
                        .orElseThrow(ProcessWorkerRecoveryService::unavailable);
                return exactReplay(winner, actor, start, command, requestHash);
            }

            var root = nativeAccess.lockRoot(start).orElseThrow(ProcessWorkerRecoveryBlockedException::new);
            requireUnsuspendedRoot(root);
            var definition = exactReleasedBpmn(start, root.engineDefinitionId());
            var deadletter = nativeAccess.lockRecoveryDeadletter(start, definition, command.jobId())
                    .orElseThrow(ProcessWorkerRecoveryBlockedException::new);
            var decision = specification.evaluate(recoveryFacts(start, definition, Optional.of(root), Optional.of(deadletter), command.jobId()));
            if (decision.status() != RETRY_AVAILABLE) throw new ProcessWorkerRecoveryBlockedException();
            if (!command.expectedVersion().equals(decision.version())) throw new ProcessVersionConflictException();

            nativeAccess.moveAndVerifyRecovery(deadletter);
            var decidedAt = recoveryReceipts.decisionTime();
            var result = new RecoverProcessWorkerResult(start.instanceId(), command.jobId(), decision.qualifying().occurrenceId(),
                    command.commandId(), RETRY_SCHEDULED, 1, decidedAt);
            var entry = new ProcessWorkerRecoveryReceiptStore.Entry(actor, requestHash, command, start,
                    definition.definitionKey(), definition.engineDefinitionId(), definition.modelSha256(), root.businessKey(),
                    decision.qualifying().occurrenceId(), deadletter.executionId(), deadletter.elementId(), deadletter.revision(),
                    decision.evidenceSha256(), "READ_ONLY_EXHAUSTED_RETRY_V1", decision.qualifying().failureCode(),
                    decision.qualifying().decidedAt(), result);
            if (!recoveryReceipts.insert(entry)) throw new ProcessWorkerRecoveryBlockedException();
            receipts.apply(actor, PROCESS_WORKER_RECOVER, command.commandId(), command.jobId(), null);
            return result;
        });
    } catch (RuntimeException failure) {
        throw classifyAfterRollback(failure);
    }
}
```

  `recoveryFacts` calls `workers.readRecoveryHistory` after current native fence on Command, rechecks row scope, calls `recoveryReceipts.hasAccepted`, collision and execution reads when native present, and supplies the exact DB exception-equality, metadata-only `workers.readRecoveryProvenance` and selected-evidence `workers.verifyRecoveryBinding` functions to File 9. `exactReplay` requires APPLIED state, compares exact hash, then verifies generic affinity and `recoveryReceipts.find(actor, start, generic, command, requestHash)` and returns the immutable result. It does not read native state or invoke mutation.

- Verification contribution: File 2 tests same-source bounds, exact ordering, result and every failure surface, File 1 verifies shared eligibility and File 3 verifies native SQL. Follow reflection RED → phase A declarations → typed behavior RED → phase B bodies → focused and finite existing regressions.
- After this file: Service is ready for Step 5 factory and route wiring, with bounded native/readback behavior finite-tested. Live locks, flush/rollback, identity and reacquisition remain outside the test claim.

#### File 11 — `CREATE docs/egon/evidence/2026-11-05-worker-recovery-service.md`

- Purpose: Record actual native and shared eligibility finite proof.
- Symbols: Exact reviewed design/plan identity or actual source evidence for this Step.
- Repository evidence: Existing cancellation and compensation evidence preserve exact path/finite/runtime distinctions.
- Dependencies and consumers: Actual current Step GREEN/strict/hash checks, Root review and exact locked files.
- Why now: Publish only after actual current source evidence is available.
- Contract/signature changes: No runtime or schema behavior added by this file.
- Input/output and state mapping: Reviewed bytes or actual logs/reports/SHA -> exact governance artifact.
- Error and edge behavior: Missing/changed bytes, unexpected scopes or skipped selected reports block the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001; other applicable checks independently recorded.
- Literal rule enforcement: Rule 11 preserves the exact existing technical profile; document other Rules individually with evidence or scoped N/A.
- Implementation pseudocode:

```text
read exact approved identity and actual current-Step results
copy reviewed bytes or record actual finite observations without placeholders
validate strict Spec/Plan, expected path and immutable SQL evidence
preserve user-owned runtime acceptance and remaining goal scope
```

- Verification contribution: Exact SHA/strict document/staged tree gate for this Step.
- After this file: Root verifies current Step before its one semantic commit; no next-Step repository edit precedes it.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact phases, final selected Step suites `WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest,ProcessWorkerRecoveryReceiptStoreTest,ProcessControlServiceTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,ProcessTaskOriginTest,FlowableCaseServiceTest,FlowableHumanTaskServiceTest,CaseNativeAccessTest,HumanTaskNativeAccessTest`.
- Expected result: Every owning report exists; current-Step GREEN zero failures/errors/skips. A compile or environmental failure is not RED.
- Failure returns to: Exact owning file/assertion; design drift returns to effective Spec, not weaker tests. V10 mismatch stops under immutable rules.
- Completion criteria: Current-Step named reports, strict docs, exact paths/staged review, unchanged old SQL/excluded hashes and separate manual/literal closure all pass.
- Rollback: Only focused revert/forward fix; no history rewrite or deletion/repair/edit of existing migration.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecificationTest.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryServiceTest.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccessTest.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/error/ProcessWorkerRecoveryBlockedException.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessControlNativeAccess.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerLeaseStore.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/WorkerTopicRegistry.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/worker/FlowableWorkerService.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/WorkerRecoverySpecification.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessWorkerRecoveryService.java`; `docs/egon/evidence/2026-11-05-worker-recovery-service.md`
- Commit: `feat(process): govern bounded native Worker restore`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing C/CL/PS technical vertical, exact named owner and no new layer. Actual EVD required; failed check blocks commit. |
| MC-REUSE-001 | Applicable | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. Actual EVD required; failed check blocks commit. |
| MC-DEP-001 | Applicable | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. Actual EVD required; failed check blocks commit. |
| MC-NAME-001 | Applicable | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. Actual EVD required; failed check blocks commit. |
| MC-VALID-001 | Applicable | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. Actual EVD required; failed check blocks commit. |
| MC-MODEL-001 | Applicable | Immutable record values and defensive copies; no mutable business entity. Actual EVD required; failed check blocks commit. |
| MC-CONVERT-001 | Applicable | Exact technical projection under approved profile; no cross-layer mapper or business entity. Actual EVD required; failed check blocks commit. |
| MC-LOG-001 | Applicable | Safe existing error boundary; no reason/token/diagnostic logging; immutable DB audit. Actual EVD required; failed check blocks commit. |
| MC-BEAN-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-UTIL-001 | Applicable | Current JDK/framework/digest helpers only. Actual EVD required; failed check blocks commit. |
| MC-JSON-001 | Applicable | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. Actual EVD required; failed check blocks commit. |
| MC-TIME-001 | Applicable | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. Actual EVD required; failed check blocks commit. |
| MC-CONFIG-001 | Not applicable | No new config/managed wiring in this Step; scope/hashes prove existing keys/Beans unchanged. Actual EVD required; failed check blocks commit. |
| MC-PATTERN-001 | Applicable | One shared eligibility Specification and existing native/transaction/command ownership, no registry/scheduler. Actual EVD required; failed check blocks commit. |
| MC-SCOPE-001 | Applicable | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. Actual EVD required; failed check blocks commit. |
| MC-TEST-001 | Applicable | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. Actual EVD required; failed check blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. Actual EVD required; failed check blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Exact semantic type/DTO/Command/Result/Enum names. Record actual evidence or scoped N/A separately. |
| Rule 2 | Applicable | Strict actual boundary, authorized context and exact proof, existing technical validation. Record actual evidence or scoped N/A separately. |
| Rule 3 | Applicable | Immutable records and defensive copies; no entity conversion bypass. Record actual evidence or scoped N/A separately. |
| Rule 4 | Applicable | Existing named technical factory, qualified owner-bound wiring and safe error handling; exact approved profile exception. Record actual evidence or scoped N/A separately. |
| Rule 5 | Applicable | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. Record actual evidence or scoped N/A separately. |
| Rule 6 | Applicable | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. Record actual evidence or scoped N/A separately. |
| Rule 7 | Not applicable | No profile/config key touched in this Step. Record actual evidence or scoped N/A separately. |
| Rule 9 | Applicable | One shared Specification for complex read/write decisions, same native owner, no new pipeline. Record actual evidence or scoped N/A separately. |
| Rule 10 | Applicable | Existing java.time/DB precision, decision/source time, no global clock or execution claim. Record actual evidence or scoped N/A separately. |
| Rule 11 | Applicable | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. Record actual evidence or scoped N/A separately. |

### Step 5 — Expose default-off governed recovery provider

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007
- Dependencies: Exact Step4 semantic commit and fresh Root current-Step lock
- Baseline state: Steps1–4 verified committed; current-Step behavior absent.
- Observable outcome: Expose default-off governed recovery provider, with current contract and old behavior preserved.
- End state: Exact 10 paths; real finite assertion gates and strict/source/SQL checks, one semantic commit.
- Test-first gate: Required — expected missing type/resource/policy assertion RED, compile A, typed behavior RED, minimal B and GREEN. Failures count only as actual intended JUnit F with E0/S0.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — independently close each scoped row, Rule11 always Applicable.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryControllerTest.java`

- Purpose: Test the new guarded provider wire path before introducing route behavior.
- Symbols: `ProcessWorkerRecoveryControllerTest`, `recovery_controller_is_present`, exact methods/annotations, strict request cap/body, affinity and permission cases.
- Repository evidence: `ProcessControlControllerTest` uses standalone MockMvc, current `ProcessRequestBodyAdvice` and `ProcessApiErrors` with no listener. `TianjiProcessAccess.require(String)` consumes an API resource key, not a varargs permission list.
- Dependencies and consumers: Steps1–4 committed; test class initially reflection only; File3 A supplies typed controller before behavior assertions.
- Why now: Establish a genuine missing-controller assertion before adding any route or policy metadata.
- Contract/signature changes: Exact GET/POST paths; constructor `(ProcessWorkerRecoveryService,TianjiProcessAccess)`; `failures(String)` and `recover(String,RecoverProcessWorkerCommand)`.
- Input/output and state mapping: Mock owned actor/queries/commands -> exact C DTOs, `application/json`, `no-store`, stable error envelope, no premature DB access.
- Error and edge behavior: Denied/mismatched tenant or path/body, malformed UTF-8/JSON, wrong field type, duplicates, trailing, noncanonical/over-8192; blocked/version/idempotency/unavailable statuses and old cancel bounds stay distinct.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-BEAN-001, MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 6, Rule 7, Rule 11 — verify actual request/actor/annotation and strict wire shape; technical profile, no new provider dependencies.
- Implementation pseudocode:

```java
// Presence phase: no new class import until compile A.
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.api.ProcessWorkerRecoveryController"));
// Typed phase after File3 A, before provider B:
var controller = assertDoesNotThrow(() -> new ProcessWorkerRecoveryController(mockService, mockAccess));
assertNotNull(controller);
var mvc = MockMvcBuilders.standaloneSetup(controller)
    .setControllerAdvice(new ProcessApiErrors(),new ProcessRequestBodyAdvice()).build();
// Assert @PermissionResource keys/scopes/USER+SERVICE and @RequiresPermissions READ/RECOVER;
// assert annotation nonnull before dereference; expected codes from contract/literals before PS constants exist.
// GET exact query, POST exact validated command, current access.require(resourceKey), exact DTO/no-store/200.
// Path != body ->400 and no service call. Access denial/tenant assertion -> scoped safe errors.
// Bound length both declared and streamed; exact five strings; duplicate/trailing/unknown/null/coercion reject.
// Unit-inject blocked, ProcessVersionConflictException, existing idempotency/unavailable and verify safe status/code.
// Preserve old 4096 cancellation cap through existing tests; no listener, PG, engine, real IAM or auto resend.
```

- Verification contribution: S5 presence RED, A compile, typed HTTP RED and final provider finite GREEN.
- After this file: Initially real missing-type assertion F/E0/S0; later typed shape tests compile against A and fail for missing policy/route behavior.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`

- Purpose: Lock fail-closed feature prerequisites and both default-off keys/mapping without starting an application.
- Symbols: Existing class, new `worker_recovery_requires_api_worker_and_servlet`, YAML flag/scope test.
- Repository evidence: Existing cancellation guard test directly invokes `workerWebBoundaryGuard(MockEnvironment,ApplicationContext).afterSingletonsInstantiated()` with mock WebApplicationContext.
- Dependencies and consumers: Existing storage guard and sole PS application.yml; Files7/9 supply behavior.
- Why now: Lock the recovery-specific prerequisite and missing default resource before production edits.
- Contract/signature changes: Existing guard method unchanged; flag `stellar.tianji.process.worker-recovery.enabled` and prerequisites exactly API/Worker/servlet.
- Input/output and state mapping: Mock flags/context -> pass disabled/valid servlet or throw fixed unsafe-combo IllegalStateException.
- Error and edge behavior: Native feature true + API true + Worker false is a real expected assertion F before guard. Recovery false never demands V10/recovery privileges merely to preserve old startup.
- Standards impact: MC-CONFIG-001, MC-BEAN-001, MC-VALID-001, MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11 — actual configuration guard and existing technical wiring, no full server.
- Implementation pseudocode:

```java
var configuration = new ProcessStorageConfiguration();
var env = new MockEnvironment().withProperty("stellar.tianji.process.worker-recovery.enabled","true")
    .withProperty("stellar.tianji.process.api.enabled","true");
assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(env, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
env.setProperty("stellar.tianji.process.worker.enabled","true");
assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(env,mock(ApplicationContext.class)).afterSingletonsInstantiated());
assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(env,mock(WebApplicationContext.class)).afterSingletonsInstantiated());
// Test absent/false recovery and exact PS YAML default boolean false plus USER mapping.
// Retain all current cancellation/Case/worker/task guard test bodies.
```

- Verification contribution: Genuine flag/default resource RED and final finite config GREEN; no dataSource context startup needed.
- After this file: Existing non-recovery guard tests preserved; new missing prerequisite/default assertions fail until Files7/9.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryController.java`

- Purpose: Expose two separate closed recovery operations through existing MVC/IAM wiring.
- Symbols: `ProcessWorkerRecoveryController`, exact constructor, `failures`, `recover`, private `noStore`.
- Repository evidence: Current `ProcessControlController` route, annotations, `access.require(resourceKey)`, no-store and path/body affinity pattern.
- Dependencies and consumers: Steps1–4 C and service committed; File1 reflection first; A exposes signatures then typed RED; B uses File6 exact permission constants.
- Why now: Create compile A so MockMvc typed test compiles; do not install final provider behavior before expected F/E0/S0.
- Contract/signature changes: Add only independent GET `/{instanceId}/worker-failures` and POST `/{instanceId}/worker-recoveries` under existing instance prefix; no change to old Control Controller.
- Input/output and state mapping: Current actor from exact API resource key and path/body -> `workerFailures(actor,query)` / `recoverWorker(actor,command)` -> exact no-store DTO.
- Error and edge behavior: Fail closed when any flag absent, malformed scope or denied authority; path/body mismatch rejected before service. Exact current authority checked on every retry.
- Standards impact: MC-ARCH-001, MC-BEAN-001, MC-VALID-001, MC-JSON-001, MC-CONFIG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 4, Rule 6, Rule 7, Rule 11 — exact stable typed MVC/permission and no extra transport layers.
- Implementation pseudocode:

```java
// A: actual class/constructor/typed signatures and route mappings; safe fixed rejection only.
// After typed RED, B uses File6 constants; apply both before Maven (no missing-constant compile gate).
@RestController
@RequestMapping(path="/api/tianji/v1/process-instances", produces=MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix="stellar.tianji.process", name={"api.enabled","worker.enabled","worker-recovery.enabled"}, havingValue="true")
class ProcessWorkerRecoveryController {
  // existing explicit technical ctor with nonnull service/access; no new business class logger.
  // GET PermissionResource WORKER_FAILURES_API, USER/SERVICE, READ_SCOPE/WORKER_RECOVER_SCOPE;
  // RequiresPermissions {READ,WORKER_RECOVER}, no method scopes inferred from URL wildcard.
  @GetMapping("/{instanceId}/worker-failures")
  public ResponseEntity<ProcessWorkerFailuresDTO> failures(@PathVariable("instanceId") String instanceId) {
    var actor=access.require(TianjiProcessPermissions.WORKER_FAILURES_API);
    return noStore(service.workerFailures(actor,new GetProcessInstanceQuery(instanceId)));
  }
  // POST PermissionResource WORKER_RECOVERIES_API with same scopes/codes, consumes application/json.
  @PostMapping(path="/{instanceId}/worker-recoveries",consumes=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RecoverProcessWorkerResult> recover(@PathVariable("instanceId") String instanceId, @RequestBody RecoverProcessWorkerCommand cmd) {
    var actor=access.require(TianjiProcessPermissions.WORKER_RECOVERIES_API);
    if(!instanceId.equals(cmd.instanceId())) throw new IllegalArgumentException("Process Worker recovery target is invalid");
    return noStore(service.recoverWorker(actor,cmd));
  }
}
```

- Verification contribution: Presence/A/typedRED/provider GREEN and actual permission resource artifacts.
- After this file: A compiles and safely rejects; B with File6 makes exact REST/authority path available only when all flags true.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java`

- Purpose: Enforce the exact five-string bounded recovery body while preserving all current command admission.
- Symbols: `supports`, `beforeBodyRead`, `MAX_PROCESS_WORKER_RECOVERY_BYTES`, private `validateProcessWorkerRecovery`.
- Repository evidence: Existing Jackson3 `STRICT_PROCESS_CANCEL_JSON`, exact key/string validation, `readNBytes(max+1)`, TooLarge.
- Dependencies and consumers: Existing new C command and typed provider tests; shares current mapper constraints and errors.
- Why now: Add behavior only after typed malformed/bounded body assertions expose missing admission.
- Contract/signature changes: New exact Type branch; 8192 bytes only for recovery; old cancel4096 and all other limits unchanged.
- Input/output and state mapping: Existing safe raw request bytes -> strict object exact keys/types -> current converter receives same bytes and constructor validates semantics.
- Error and edge behavior: ContentLength/chunked overflow => existing TooLarge; duplicate/trailing/coercion/nonstring/unsafe fields fail with fixed invalid command, no raw text echo.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 11 — reuse actual tools.jackson mapper and checks; no old branch rewrite.
- Implementation pseudocode:

```java
static final int MAX_PROCESS_WORKER_RECOVERY_BYTES=8192;
// supports: add type==RecoverProcessWorkerCommand.class, preserve existing conditions.
// beforeBodyRead maxBytes selects this exact type first; old cancel/worker/result/default unchanged.
// Current readNBytes(maxBytes+1) and existing TooLarge reused.
void validateProcessWorkerRecovery(byte[] body) {
  var fields=Set.of("instanceId","jobId","commandId","expectedVersion","reason");
  var root=STRICT_PROCESS_CANCEL_JSON.readTree(body); // actual Jackson3; constraints suffice for five bounded strings
  require exact object/propertyNames and strings(root,fields);
  STRICT_PROCESS_CANCEL_JSON.readValue(body,RecoverProcessWorkerCommand.class);
  // Normalize RuntimeException to fixed safe IllegalArgumentException; no raw payload/log/cause output.
}
```

- Verification contribution: Exact new body limit/error cases and existing cancellation/body regressions.
- After this file: New input branch rejects unsafe JSON/bounds, all old branches byte behavior preserved.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`

- Purpose: Map only the new typed blocked exception to its declared safe envelope.
- Symbols: New `@ExceptionHandler(ProcessWorkerRecoveryBlockedException.class)` method; existing `error(HttpStatus,String)`.
- Repository evidence: Existing explicit `ProcessVersionConflictException`, `TooLarge` and no-store safe `OrchestrationErrorDTO` handlers.
- Dependencies and consumers: Step4 actual typed blocked exception; C/CL/OAS fixed status/code contract.
- Why now: Map known service exception before final guarded endpoint is enabled.
- Contract/signature changes: New 409 `TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED`; no remapping existing kinds.
- Input/output and state mapping: Typed blocked exception -> current fixed OrchestrationErrorDTO(code,null) and no-store.
- Error and edge behavior: Do not parse or echo SQL/error/reason text; existing version/idempotency/503 classification stays.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-LOG-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 6, Rule 11 — exact enum/code/HTTP mapping and existing safe technical error boundary.
- Implementation pseudocode:

```java
@ExceptionHandler(ProcessWorkerRecoveryBlockedException.class)
ResponseEntity<OrchestrationErrorDTO> recoveryBlocked() {
  return error(HttpStatus.CONFLICT,"TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED");
}
// Retain every existing handler/status/code and error(...) behavior.
```

- Verification contribution: Typed blocked/no-store 409, known status/code matrix, old errors unaffected.
- After this file: Provider maps recovery blocked consistently with new CL/OAS without new logger.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Declare one new permission and two exact recovery resource/scope aliases for static artifact generation.
- Symbols: `WORKER_RECOVER`, `WORKER_RECOVER_SCOPE`, `WORKER_FAILURES_API`, `WORKER_RECOVERIES_API`; one PermissionDefinition plus en-US labels.
- Repository evidence: Existing `@PermissionDefinition/@PermissionMessage/@LocalizedText`, Control API/scope aliases and existing tianji-process-java producer.
- Dependencies and consumers: C neutral constants from Step1; File3 B uses these exact declarations.
- Why now: Complete both controller/permission halves before Maven; no compile-failure-as-RED.
- Contract/signature changes: Add one permission/2 route labels and exact resource keys; old READ/CANCEL definitions unchanged.
- Input/output and state mapping: Existing annotation processor consumes added metadata -> packaged local manifest only.
- Error and edge behavior: Never register/grant/activate; existing manifest semantics and user/service principals preserved.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 7, Rule 11 — exact permission and scope values, existing producer/profile.
- Implementation pseudocode:

```java
@PermissionDefinition(code=TianjiProcessPermissions.WORKER_RECOVER, meaning="Recover an eligible Process Worker failure",
    labelPage="tianji.process.permissions",labelKey="permission.process.workerRecover")
// Add matching PermissionMessage for permission.process.workerRecover and API labels
// api.process.workerFailures, api.process.workerRecoveries with existing en-US convention.
public static final String WORKER_RECOVER="TIANJI_PROCESS_WORKER_RECOVER";
public static final String WORKER_RECOVER_SCOPE=ProcessWorkerRecoveryClient.WORKER_RECOVER_SCOPE;
public static final String WORKER_FAILURES_API=ProcessWorkerRecoveryClient.WORKER_FAILURES_RESOURCE_KEY;
public static final String WORKER_RECOVERIES_API=ProcessWorkerRecoveryClient.WORKER_RECOVERIES_RESOURCE_KEY;
```

- Verification contribution: Actual producer artifact has two new API resources/one new permission and exact READ+RECOVER scopes.
- After this file: Controller B and permission declarations compile together; old metadata unchanged.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`

- Purpose: Reject unsafe recovery flag combinations at the existing web boundary guard.
- Symbols: Existing `workerWebBoundaryGuard(Environment,ApplicationContext)` only.
- Repository evidence: Same current guard already requires enabled servlet API for Worker/cancel/case/human features.
- Dependencies and consumers: File2 finite flag RED; File8 runtime owner/source prerequisites still independent.
- Why now: Establish early fail-closed boundary before enabled-only provider factory creation.
- Contract/signature changes: Same guard method, one additive property branch.
- Input/output and state mapping: Actual env worker-recovery enabled -> require api.enabled, worker.enabled and WebApplicationContext.
- Error and edge behavior: Missing prerequisite throws fixed safe IllegalStateException, default off doesn't query V10 or change old readiness.
- Standards impact: MC-CONFIG-001, MC-VALID-001, MC-BEAN-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 7, Rule 11 — existing named guard and actual source flag semantics.
- Implementation pseudocode:

```java
if (environment.getProperty("stellar.tianji.process.worker-recovery.enabled",Boolean.class,false)
    && (!environment.getProperty("stellar.tianji.process.api.enabled",Boolean.class,false)
       || !environment.getProperty("stellar.tianji.process.worker.enabled",Boolean.class,false)
       || !(context instanceof WebApplicationContext))) {
  throw new IllegalStateException("Process Worker recovery requires the enabled servlet Process Worker API");
}
// Preserve every existing guard; this check alone never opens storage.
```

- Verification contribution: Existing direct MockEnvironment/mock servlet tests and new flag matrix.
- After this file: Unsupported combinations fail closed before any actual recovery operation; all old flags unchanged.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`

- Purpose: Wire one verified source-owned recovery service beside the existing Worker/cancellation configurations.
- Symbols: Nested `WorkerRecoveryConfiguration`, `@Bean("processWorkerRecoveryService")`, exact factory participants.
- Repository evidence: `WorkerConfiguration` line375 and `CancellationConfiguration` line426, `processConfiguration`, `caseDataSource`, existing IAM filterchain and wildcard endpoint auth classification.
- Dependencies and consumers: Steps3/4 storage/service committed; existing ProcessEngine, ProcessReleaseService and FlowableWorkerService; File7 flag safety and File3 controller.
- Why now: Enable only after full service/provenance/TX/schema and role contract exists; no speculative bean or source adoption.
- Contract/signature changes: One explicit factory under actual triple flag; reuse current verified IAM guards. No cancellation privilege requirement and no competing native-access Bean.
- Input/output and state mapping: Exact configured engine, schema/source/TM, existing Worker -> independently verified V10 + same-owner local collaborators -> service.
- Error and edge behavior: Missing/foreign participant or bad history/role fail closed; no override-based fallback, no PG/engine startup in tests.
- Standards impact: MC-ARCH-001, MC-BEAN-001, MC-CONFIG-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 2, Rule 4, Rule 5, Rule 7, Rule 11 — explicit stable technical Bean/source and current security reuse.
- Implementation pseudocode:

```java
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="stellar.tianji.process",name={"api.enabled","worker.enabled","worker-recovery.enabled"},havingValue="true")
static class WorkerRecoveryConfiguration {
  @Bean("processWorkerRecoveryService")
  ProcessWorkerRecoveryService processWorkerRecoveryService(ProcessEngine process,
      ProcessReleaseService releases, FlowableWorkerService workers) {
    var engine=processConfiguration(process);
    DataSource source=caseDataSource(engine); // existing verified exact owning source/TM
    String schema=engine.getDatabaseSchema();
    var nativeAccess=new ProcessControlNativeAccess(source,engine); // local collaborator, not competing Bean
    var commands=new ProcessCommandReceiptStore(new JdbcTemplate(source),schema);
    var audit=new ProcessWorkerRecoveryReceiptStore(source,schema);
    audit.verifyStorage(); // independent V10/native readiness verifier, no cancel grants
    return new ProcessWorkerRecoveryService(engine,releases,commands,nativeAccess,audit,workers);
  }
}
// Service constructor verifies all exact source/engine participants; preserve existing API IAM/filter prerequisite.
```

- Verification contribution: Finite guard/config/authority tests and current native/service ownership mocks; actual packaged metadata independent of activation.
- After this file: Enabled provider can be constructed only with exact verified participants; no runtime executed here.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Add explicit default false and only the new USER permission-to-scope mapping.
- Symbols: `stellar.tianji.process.worker-recovery.enabled`, existing user-scope-policy `TIANJI_PROCESS_WORKER_RECOVER` mapping.
- Repository evidence: Sole PS application.yml has cancellation.enabled:false and TIANJI_PROCESS_CANCEL mapping at current lines56/71.
- Dependencies and consumers: Current feature/permission declarations and File2 resource assertion; artifact JAR byte comparison.
- Why now: Source behavior and gates exist; set actual packaged default-off as final configuration change.
- Contract/signature changes: One new flag and one mapping; no previous permission or profile key changed.
- Input/output and state mapping: Native app default recovery=false; mapping records approved scope metadata and conveys no grant.
- Error and edge behavior: No environment/user credential defaults, new roles or live activation.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 11 — exact base config/default and actual packaged YAML; preserve technical profile.
- Implementation pseudocode:

```yaml
# Under existing user-scope-policy.permission-scopes:
TIANJI_PROCESS_WORKER_RECOVER: ["tianji:process:worker-recover"]
# Under existing stellar.tianji.process:
worker-recovery:
  enabled: false
```

- Verification contribution: Resource/default-off assertions, actual packaged YAML comparison and scope metadata.
- After this file: Provider is complete source and default-off; exact finite/artifact verification and EVD still required before commit.

#### File 10 — `CREATE docs/egon/evidence/2026-11-05-worker-recovery-provider.md`

- Purpose: Close exact current Step and aggregate source conformance with actual finite and packaged evidence.
- Symbols: Actual RED/A/typedRED/GREEN, clean artifact manifest, YAML bytes, requirement and checks closure.
- Repository evidence: Existing cancellation artifact evidence distinguishes LOCAL_UNCOMMITTED packaging from live IAM/runtime.
- Dependencies and consumers: Every selected test report and fresh actual PS/CL JAR proof available after final gates.
- Why now: Observed results are known; no placeholder test counts or live claims.
- Contract/signature changes: Document exact Step5 scope and Steps1–5 evidence, unchanged old SQL, runtime unexecuted.
- Input/output and state mapping: Actual logs/XML/classpath and JAR artifacts -> auditable record with exact resource/scopes/permission counts.
- Error and edge behavior: Missing reports/artifacts or unexpected differences block commit. Never count compile-only PG as executed.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001 and independent 17MC/10Rule closure.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11 — actual independent evidence per applicable rule and justified N/A.
- Implementation pseudocode:

```text
inspect all actual named finite reports with zero failures/errors/skips
read fresh PS permission manifest and compare exact old entries plus 2 APIs/1 permission
inspect CL packaged OpenAPI, old artifact bytes, PS packaged application.yml and false flag
verify all prior25 SQL plus newly committed V10 immutable, exact paths/excluded SHA and17MC/10Rules
record no live IAM/PG/native/listener/deployment/activation acceptance; exact path-limited Step commit
```

- Verification contribution: Final strict source/artifact audit and one focused commit.
- After this file: Verify exact ten paths and committed blob hashes, clean service; T3-10 real acceptance, T3-12 and T4 remain required.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact phases, final selected Step suites `ProcessWorkerRecoveryControllerTest,ProcessStorageConfigurationTest,ProcessControlControllerTest,TianjiProcessAccessTest,ProcessPermissionIntegrationTest,ProcessSecurityConfigurationTest,ProcessApiConfigurationTest`.
- Expected result: Every owning report exists; current-Step GREEN zero failures/errors/skips. A compile or environmental failure is not RED.
- Failure returns to: Exact owning file/assertion; design drift returns to effective Spec, not weaker tests. V10 mismatch stops under immutable rules.
- Completion criteria: Current-Step named reports, strict docs, exact paths/staged review, unchanged old SQL/excluded hashes and separate manual/literal closure all pass.
- Rollback: Only focused revert/forward fix; no history rewrite or deletion/repair/edit of existing migration.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryControllerTest.java`; `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfigurationTest.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessWorkerRecoveryController.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessRequestBodyAdvice.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessApiErrors.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessStorageConfiguration.java`; `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessSecurityConfiguration.java`; `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`; `docs/egon/evidence/2026-11-05-worker-recovery-provider.md`
- Commit: `feat(process-server): expose default-off Worker recovery API`

##### Step blocking Manual Check closure

| Check ID | Applicability | Completion proof required before commit |
| --- | --- | --- |
| MC-ARCH-001 | Applicable | Existing C/CL/PS technical vertical, exact named owner and no new layer. Actual EVD required; failed check blocks commit. |
| MC-REUSE-001 | Applicable | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. Actual EVD required; failed check blocks commit. |
| MC-DEP-001 | Applicable | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. Actual EVD required; failed check blocks commit. |
| MC-NAME-001 | Applicable | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. Actual EVD required; failed check blocks commit. |
| MC-VALID-001 | Applicable | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. Actual EVD required; failed check blocks commit. |
| MC-MODEL-001 | Applicable | Immutable record values and defensive copies; no mutable business entity. Actual EVD required; failed check blocks commit. |
| MC-CONVERT-001 | Applicable | Exact technical projection under approved profile; no cross-layer mapper or business entity. Actual EVD required; failed check blocks commit. |
| MC-LOG-001 | Applicable | Safe existing error boundary; no reason/token/diagnostic logging; immutable DB audit. Actual EVD required; failed check blocks commit. |
| MC-BEAN-001 | Applicable | Explicit named technical service factory and exact verified source, no business injection change. Actual EVD required; failed check blocks commit. |
| MC-UTIL-001 | Applicable | Current JDK/framework/digest helpers only. Actual EVD required; failed check blocks commit. |
| MC-JSON-001 | Applicable | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. Actual EVD required; failed check blocks commit. |
| MC-TIME-001 | Applicable | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. Actual EVD required; failed check blocks commit. |
| MC-CONFIG-001 | Applicable | Default-off profile and explicit permission-scope mapping, older readiness works after V10. Actual EVD required; failed check blocks commit. |
| MC-PATTERN-001 | Applicable | One shared eligibility Specification and existing native/transaction/command ownership, no registry/scheduler. Actual EVD required; failed check blocks commit. |
| MC-SCOPE-001 | Applicable | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. Actual EVD required; failed check blocks commit. |
| MC-TEST-001 | Applicable | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. Actual EVD required; failed check blocks commit. |
| MC-BLOCKER-001 | Applicable | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. Actual EVD required; failed check blocks commit. |

##### Step literal Rule closure

| Rule | Applicability | Required independent evidence |
| --- | --- | --- |
| Rule 1 | Applicable | Exact semantic type/DTO/Command/Result/Enum names. Record actual evidence or scoped N/A separately. |
| Rule 2 | Applicable | Strict actual boundary, authorized context and exact proof, existing technical validation. Record actual evidence or scoped N/A separately. |
| Rule 3 | Applicable | Immutable records and defensive copies; no entity conversion bypass. Record actual evidence or scoped N/A separately. |
| Rule 4 | Applicable | Existing named technical factory, qualified owner-bound wiring and safe error handling; exact approved profile exception. Record actual evidence or scoped N/A separately. |
| Rule 5 | Applicable | Existing approved JDK/Jackson/Spring/Flowable/digest utilities; no dependency. Record actual evidence or scoped N/A separately. |
| Rule 6 | Applicable | Exact JSON wire/code/enums/UTC; no ordinal; existing byte contract preserved. Record actual evidence or scoped N/A separately. |
| Rule 7 | Applicable | Existing profile keys with single new false flag and scope mapping; packaged parity. Record actual evidence or scoped N/A separately. |
| Rule 9 | Applicable | One shared Specification for complex read/write decisions, same native owner, no new pipeline. Record actual evidence or scoped N/A separately. |
| Rule 10 | Applicable | Existing java.time/DB precision, decision/source time, no global clock or execution claim. Record actual evidence or scoped N/A separately. |
| Rule 11 | Applicable | Current technical module/profile, current components, plain JDBC/Flyway exact approved decision; no MP/DDL runner/generator or Event. Record actual evidence or scoped N/A separately. |

## 8. Test, Validation, and Quality Gates

Root only after current Step GO. Do not select `*PostgresTest`, `HttpProcessClientTest`, any listener/provider/app or wildcard; do not set opt-in props. Source compilation is not execution. Every owning report must exist and run.

Common source reactor base for PS and aggregate:

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dtest=ProcessWorkerRecoveryReceiptStoreTest
```

The table uses base with only its named selector substituted. Step1/2 use the narrower explicit Maven commands below, with the same JDK/offline settings and skipITs. Clean packaging uses `STELLAR_VERIFY_GOAL=verify` before the base and `clean` before its test goal.

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1 C presence RED | service | C exact method selector below | missing C types | assertionF/E0/S0 | S1F1 | REQ-001/002/006 |
| G2 C compile A | service | C test-compile and presence rerun | final declarations only | compile and presence GREEN | S1F1–6 | REQ-001/006 |
| G3 C typed RED | service | C test exact new class | missing validation behavior | genuine assertionF/E0/S0 | S1F1–6 | REQ-001–006 |
| G4 C GREEN/docs/commit | service | C regression below, strict docs and exact9 path audit | C + governance | all owning reports GREEN, one commit | S1F1–9 | REQ-006/007 |
| G5 CL presence RED | service | CL exact missing-adapter method | SDK missing capability | assertionF/E0/S0 | S2F1 | REQ-001/006 |
| G6 CL compile A + typedRED | service | CL new Http test after A signatures; current bodies safely unavailable | strict transport/codec | compile and actual intended behavior F/E0/S0 | S2F1–3 | REQ-001/002/005/006 |
| G7 CL transport GREEN | service | CL new Http test after B | one send/exact response | actual GREEN | S2F1–3 | REQ-001/005/006 |
| G8 OAS RED | service | CL exact missing-resource method | missing new OAS | assertionF/E0/S0 | S2F4 | REQ-006 |
| G9 CL artifact GREEN/commit | service | CL package selector and resource cmp below | SDK + actual CL JAR | 0F/E/S, exact6 paths, commit | S2F1–6 | REQ-006/007 |
| G10 storage RED | service | exact Stage A selector in storage gate below | absent V10 and unknown prefix | intended assertion F/E0/S0 | S3F1/2 | REQ-005/006 |
| G11 immutable V10 | service | off-repo shape/hash review, exclusive create, parity and presence selector | one V10 only | exact immutable reviewed bytes | S3F3 | REQ-006/007; no migration |
| G12 storage A+typedRED | service | storage Stage B exact selector | missing receipt/readiness behavior | compile, expected F/E0/S0 | S3F1–6 | REQ-005/006 |
| G13 storage GREEN/commit | service | base `ProcessWorkerRecoveryReceiptStoreTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,ProcessControlServiceTest,ProcessControlNativeAccessTest` | store/old readiness | 0F/E/S and exact7 commit | S3F1–7 | no PG |
| G14 service reflection RED/A | service | base WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest | new symbols/SQL fences | true RED, then A compiles | S4F1–10 | REQ-001/003/004 |
| G15 service typedRED→GREEN/commit | service | same focused selector before B expected F then after B GREEN; base `WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest,ProcessWorkerRecoveryReceiptStoreTest,ProcessControlServiceTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,ProcessTaskOriginTest,FlowableCaseServiceTest,FlowableHumanTaskServiceTest,CaseNativeAccessTest,HumanTaskNativeAccessTest` regression | exact native/source/eligibility/replay | 0E/S at RED, all actual GREEN and exact11 commit | S4F1–11 | no native runtime |
| G16 provider reflection/flags RED | service | base ProcessWorkerRecoveryControllerTest,ProcessStorageConfigurationTest | missing class/prerequisite/default key | intended assertion F/E0/S0 | S5F1/2 | REQ-002/006 |
| G17 provider A and typedRED | service | same selector after F3 A and typed tests | actual missing route/advice/permission/config behavior | compile and expected F/E0/S0 | S5F1–3 | no server/listener |
| G18 provider GREEN | service | base `ProcessWorkerRecoveryControllerTest,ProcessStorageConfigurationTest,ProcessControlControllerTest,TianjiProcessAccessTest,ProcessPermissionIntegrationTest,ProcessSecurityConfigurationTest,ProcessApiConfigurationTest` | exact guarded provider + old endpoints | all actual 0F/E/S | S5F1–9 | REQ-001–007 |
| G19 final clean verify/artifacts | service | full command below, actual CL/PS resources/YAML/permission manifest | all five Steps finite and packaging | all named reports, exact old entries +2 API/1 permission, false flag | S5F1–9 | local artifacts, no live grants |
| G20 scope/EVD/commit | service | strict validators/exact10+aggregate43 paths/26 immutable SQL after V10 | source conformance | actual proof, clean commit blobs | S5F10 | runtimeunverified |
| Future manual | User-owned | Primary§14/18 S10/S11 native PG/roles/IAM | Actual lock/rollback/reacquire | Separately report live outcomes | Later acceptance | not executed here |

### Exact C/CL and storage/native commands

The following retained per-Step runner/gate details are part of the Plan. Any source changes are only in the stated Step.

#### C phase commands

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
recovery_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-contract -DskipITs=true -DskipTests=false '-Dtest=ProcessWorkerRecoveryContractTest#recovery_capability_and_records_have_exact_shape' test
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-contract -DskipITs=true -DskipTests=false test-compile
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-contract -DskipITs=true -DskipTests=false -Dtest=ProcessWorkerRecoveryContractTest test
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-contract -DskipITs=true -DskipTests=false -Dtest=ProcessWorkerRecoveryContractTest,ProcessControlContractTest,OrchestrationContractTest test
```

- Expected result: Run the first command before A for missing capability RED and again after A compile for reflection GREEN. Run command three after adding typed tests and before B for behavioral RED. Run the final selector after B for nonzero executed tests in all three classes, zero failures/errors/skips.
- Failure returns to: File 1 when failure is discovery or test setup, Files 2–6 for the relevant constructor/shape, or Chapter 6 for wrapper/dependency/environment failure. A compilation failure during either RED does not count as behavioral evidence.
- Completion criteria: Exact five C shapes and one test committed after both RED categories and GREEN, prior C contract tests pass, repository path audit is clean outside the authorized Step scope.

#### CL phase commands

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
recovery_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-client -am -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=ProcessWorkerRecoveryHttpTest#recovery_capability_is_available' test
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-client -am -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProcessWorkerRecoveryHttpTest test
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-client -am -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=ProcessWorkerRecoveryOpenApiContractTest#openapi_publishes_exact_paths_security_and_status_pairs' test
./mvnw -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp -pl :stellar-enclosure-tianji-client -am -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProcessWorkerRecoveryContractTest,ProcessWorkerRecoveryHttpTest,ProcessWorkerRecoveryOpenApiContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ClientDependencyBoundaryTest package
unzip -p stellar-enclosure-tianji-client/target/stellar-enclosure-tianji-client-0.0.1-SNAPSHOT.jar META-INF/tianji/openapi/process-worker-recovery-v1.json | cmp - stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-worker-recovery-v1.json
unzip -p stellar-enclosure-tianji-client/target/stellar-enclosure-tianji-client-0.0.1-SNAPSHOT.jar META-INF/tianji/openapi/process-control-v1.json | cmp - stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-control-v1.json
git diff --exit-code 415a2814cf4a025bd868543c48b22e32e54e41ff -- stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/process-control-v1.json stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessControlResponseCodec.java
```

- Expected result: Command one produces missing-adapter RED after File 1; command two runs once after File2/3 A for typed assertion RED and again after B for GREEN. Command three produces missing-resource RED after File 4; command four GREEN after File 5 with nonzero execution and zero failures/errors/skips in each named class. Both byte comparisons and Control source diff exit 0. Check the Surefire XML test counts because upstream `-am` allows no matching tests in unrelated reactor modules for focused commands.
- Failure returns to: File 1 for fake or selector mismatch, File 2 for malformed codec/matrix handling, File 3 for route/transport/no-resend/wire regression, Files 4–5 for artifact differences, or Chapter 6 for environment/wrapper failure. A build/fixture failure during RED does not count as proof.
- Completion criteria: Two operation-specific recovery routes and immutable result mapping, finite transport and schema proof, and packaged new artifact plus byte-identical old Control resource with preserved Control finite test results.

#### Storage phase commands

**Storage phase gates.** Run from `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` using the full finite source reactor command in Chapter 8, appending the exact `-Dtest` selector below.

- Stage A RED, after File 2 and before any V10 or Java production edit. Run `'-Dtest=ProcessWorkerRecoveryReceiptStoreTest#requiresImmutableRecoveryMigrationResource,ProcessApiSchemaVerifierTest#history_accepts_only_exact_v8_v9_v10_prefixes'`. Require both selected tests compiled and ran and failed by assertions for the absent V10 and the existing 2-row maximum, respectively. Wrap the legacy prefix invocation in `assertDoesNotThrow` so old rejection is an assertion failure. A build, fixture or test-discovery error is not RED.
- Off-repository immutable SQL gate, before File 3. Set `recovery_sql_draft="$(mktemp -d "${TMPDIR:-/tmp}/tianji-v10.XXXXXX")/V10__add_process_worker_recovery_receipts.sql"`; record final exact bytes there after shape review and run `shasum -a 256 "$recovery_sql_draft"`. Use the same independent File 1 expected shape inventory in review. Create the target with exclusive no-clobber (`(set -C; cat "$recovery_sql_draft" > stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql)`), then `cmp "$recovery_sql_draft" stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql`. Exact matches only. Run `'-Dtest=ProcessWorkerRecoveryReceiptStoreTest#requiresImmutableRecoveryMigrationResource'`; expected exit 0. If review or parity fails, stop before create or commit, respectively. A checksum mismatch is not permission to edit an existing V10.
- Stage B compile-only prerequisite. After File 3, introduce only Files 4–6 final signatures/constructors/immutable Entry shape and required constants to compile; no new SQL behavior yet. Temporary unimplemented outcomes for the new methods must be observable as an assertion failure (e.g. `assertDoesNotThrow` on ready execution or equality to an expected successful result); retain baseline `laterMigrations` and cancellation behavior. Finish the named tests and swap the fixture checksum to `checksum(V10)`. Run `'-Dtest=ProcessWorkerRecoveryReceiptStoreTest#insertsExactEvidenceAndDistinguishesTargetConflict+readsPlainStartOnlyForExactOwner,ProcessApiSchemaVerifierTest#recovery_requires_exact_v10_catalog_and_role+v10_keeps_existing_readiness_without_recovery_grants'`. Require finite JUnit assertion failures at ready positive assertions for each missing branch, not unresolved Java imports or exceptions from catalog fixture setup. Finish only minimum Files 4–6 needed by those tests; no temporary outcomes may survive.
- GREEN and regressions. Run `'-Dtest=ProcessWorkerRecoveryReceiptStoreTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,ProcessControlServiceTest,ProcessControlNativeAccessTest'`. Require exit 0 and actual execution of every selected class. Check existing migration bytes with `git diff --exit-code 415a2814cf4a025bd868543c48b22e32e54e41ff -- ':(glob)**/src/main/resources/db/**/*.sql' ':(exclude)stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V10__add_process_worker_recovery_receipts.sql'`, require success and `git ls-tree -r --name-only 415a2814cf4a025bd868543c48b22e32e54e41ff | rg '/src/main/resources/db/.*\.sql$' | wc -l` reporting 25. `git diff --check` and the Step's exact path inspection must pass. Return to Files 1–2 for a fixture mismatch, Files 4–6 for behavior, or the immutable migration gate for a reviewed-bytes discrepancy. Finite fakes demonstrate decisions, query shape and drift detection; real PostgreSQL DDL/catalog compatibility, privilege behavior, index conflict, transaction rollback and engine acceptance remain separately gated.

#### Service phase commands

**Step 4 finite execution sequence** (for the enclosing Step gate and Chapter 8; the Plan authorship does not run these commands). Workdir `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.

```sh
# Reuse the existing approved offline source-reactor runner and its isolated settings.
run_recovery_service_tests() {
    ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
    JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
    ./scripts/verify-source-reactor.sh \
        -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml \
        -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml \
        -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
        -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
        "-Dtest=$1"
}
# Reflection RED on current source with Files 1–3 prepared.
run_recovery_service_tests WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest
# After phase A declares Files 4–10 in dependency order, replace the temporary reflection probes
# with their typed assertions. Run again for typed behavioral RED; compilation must succeed.
run_recovery_service_tests WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest
# After phase B implements the real bodies, same selection must be GREEN.
run_recovery_service_tests WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest
# Confirm pre-existing finite callers and the earlier receipt tests.
run_recovery_service_tests WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,ProcessControlNativeAccessTest,ProcessWorkerRecoveryReceiptStoreTest,ProcessControlServiceTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,ProcessTaskOriginTest,FlowableCaseServiceTest,FlowableHumanTaskServiceTest,CaseNativeAccessTest,HumanTaskNativeAccessTest
```

Gate outputs must show each selected class actually ran (selected-test absence is allowed at unrelated reactor modules only). Phase A adds constructors, immutable fact/return types and the exact methods with fail-closed temporary unavailable bodies only where declarations need a body. These uncommitted seams must expose no working restore. Typed RED is the positive test failing on that deliberately missing behavior, with a clean compile and no dependency/runtime failure. Phase B implements Files 4–10 in order and removes every temporary probe or body. Preserve original tests. All new writes target the generic and V10 application tables only. Existing mock suites instantiate no application/server, real Flowable engine, DB, listener or provider.

### Final artifact verification

```bash
STELLAR_VERIFY_GOAL=verify ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./scripts/verify-source-reactor.sh -gs /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -s /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none clean -Dtest=ProcessWorkerRecoveryContractTest,ProcessControlContractTest,OrchestrationContractTest,ProcessWorkerRecoveryHttpTest,ProcessWorkerRecoveryOpenApiContractTest,HttpProcessControlClientTest,ProcessControlOpenApiContractTest,ClientDependencyBoundaryTest,ProcessWorkerRecoveryReceiptStoreTest,ProcessApiSchemaVerifierTest,ProcessCancellationReceiptStoreTest,ProcessControlServiceTest,ProcessControlNativeAccessTest,WorkerRecoverySpecificationTest,ProcessWorkerRecoveryServiceTest,FlowableWorkerServiceTest,WorkerTopicRegistryTest,ProcessTaskOriginTest,FlowableCaseServiceTest,FlowableHumanTaskServiceTest,CaseNativeAccessTest,HumanTaskNativeAccessTest,ProcessWorkerRecoveryControllerTest,ProcessStorageConfigurationTest,ProcessControlControllerTest,TianjiProcessAccessTest,ProcessPermissionIntegrationTest,ProcessSecurityConfigurationTest,ProcessApiConfigurationTest
```

Verify actual current PS/CL JARs rather than only source. Capture original permission producer identifiers/metadata at baseline and compare exact old entries plus the new failures/recoveries API and one permission. Use existing processor/CLI verification from current Maven profile; no registration/grant. Check exact new and unchanged old CL OpenAPI bytes inside the actual JAR, PS packaged application.yml source cmp and false flag, stable old source behavior. Persist logs/XML and actual counts; no hardcoded expected total. Use no new work harness. Work-script disposition: Not applicable, existing reactor and current artifact toolchain cover checks.

Strict documents at eventual mirror and actual repository paths:

```bash
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py docs/egon/spec/2026-11-05-13-41-process-worker-recovery.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-05-14-27-process-worker-recovery.md --strict
git diff --check
```

## 9. Migration, Compatibility, Rollout, and Rollback

Only V10 is new and must be exactly reviewed off repo before exclusive creation. Once created, treat it as immutable. No old SQL/constraint editing, repair, data backfill or engine operation. Preserve exact known V8/V9/V10 prefix and older feature readiness with recovery off without V10 runtime grants. Ship compatible code before separately authorized V10; afterward old binary rejects history, so disable feature/compatible forward fix, never database history rollback. Keep old Control wire/OAS and Worker behavior. No deployment or grant in this work.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-002 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-003 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-004 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 3,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-005 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 3,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-006 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 3,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |
| REQ-007 | Primary§4/7/9/11/14 | Step 1,Step 2,Step 3,Step 4,Step 5 | §5/7 exact paths | §8 owning finite gates | Actual reports/staged hash and source/runtime separation |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| RISK-001 | Native lock/flush and real DB rollback not proved by mocks | S4/5 | Pinned Flowable/JDBC static evidence | Root/user | Runtime acceptance remains pending |
| RISK-002 | Unknown effects/clock ambiguity | S4 | V4/worker input and evidence facts | Root | Conservative READ_ONLY admission, manual otherwise |
| RISK-003 | V10 checksum/catalog or older binary | S3/5 | Existing exact prefix | Root/user | Review exact bytes before create; compatible binary first; no repair |
| RISK-004 | SDK/provider/permission artifact drift | S2/5 | Closed current APIs and producer | Root | Exact finite and actual JAR comparisons; no live grants |

No unresolved design decision is claimed in the reviewed primary Spec. This draft still requires Root exact text/strict review and a fresh current-Step lock before source GO. Runtime and the remaining master goal are not accepted.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Seven effective requirements include a complete bounded read or explicit failure, single safely admitted restore and audit, preserving broader manual states and master runtime/T3-12/T4 obligations.

### 12.2 Spec consistency

Consume exact authority, closed wire, V4 proof and same-source native fences. No new policy outside the Spec. §6.4 only resolves current implementation details, no fetch-forward API or generic scheduler.

### 12.3 Repository executability

Exact paths/actual APIs/staged signatures and finite commands; owner-local data sources/transaction and existing permission producer. All commands are planned, not executed. Root reviews each file, stage/branch dependencies and exact SQL pre-create bytes before its authorization.

### 12.4 Test and release completeness

Reflection and typed assertion RED, compile A, minimum B, finite GREEN and final actual artifacts. Existing migration/ABI/permissions/config scopes preserved. No runtime/deployment/grants.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Existing C/CL/PS technical vertical, exact named owner and no new layer. | Actual independent closure still required in each Step |
| MC-REUSE-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Reuse existing orchestration checks, HttpClient transport, Flowable/Store/registry/digest, configured source and IAM. | Actual independent closure still required in each Step |
| MC-DEP-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | All POMs stay unchanged; actual Jackson3/Spring JDBC/Flowable/JDK only. | Actual independent closure still required in each Step |
| MC-NAME-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Semantic immutable DTO/Command/Result/Enum and focused technical service/receipt/specification. | Actual independent closure still required in each Step |
| MC-VALID-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Exact wire/authority/scope/current model/evidence/role/schema validation at boundaries. | Actual independent closure still required in each Step |
| MC-MODEL-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Immutable record values and defensive copies; no mutable business entity. | Actual independent closure still required in each Step |
| MC-CONVERT-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Exact technical projection under approved profile; no cross-layer mapper or business entity. | Actual independent closure still required in each Step |
| MC-LOG-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Safe existing error boundary; no reason/token/diagnostic logging; immutable DB audit. | Actual independent closure still required in each Step |
| MC-BEAN-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Explicit named technical service factory and exact verified source, no business injection change. | Actual independent closure still required in each Step |
| MC-UTIL-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Current JDK/framework/digest helpers only. | Actual independent closure still required in each Step |
| MC-JSON-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Exact named fields/types/enums/null/wire status and packaged OpenAPI; old Control contract stable. | Actual independent closure still required in each Step |
| MC-TIME-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | UTC Instant/DB write-time/microsecond and exact replay; no cross-clock hard ordering or observedAt version. | Actual independent closure still required in each Step |
| MC-CONFIG-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Default-off profile and explicit permission-scope mapping, older readiness works after V10. | Actual independent closure still required in each Step |
| MC-PATTERN-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | One shared eligibility Specification and existing native/transaction/command ownership, no registry/scheduler. | Actual independent closure still required in each Step |
| MC-SCOPE-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Exact paths, old25 SQL plus immutable V10 once created, excluded files unchanged. | Actual independent closure still required in each Step |
| MC-TEST-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | Actual reflection RED/compile A/typedRED/B/GREEN, exact selectors/packaged proof, no live selection. | Actual independent closure still required in each Step |
| MC-BLOCKER-001 | Applicable | PASS | Primary§6/7/9/11 and §4/7/8 | All current-Step checks closed before one commit; separate actual runtime acceptance and later work. | Actual independent closure still required in each Step |

### 12.6 Final verdict

PASS — Ready for user review

Plan structure/semantics only; Source GO remains a separate gate. No user acceptance or runtime claim.

## Java / CQE contract review

Immutable records/enums/strict construction, owner-local projection and actual technical native JDBC profile under explicit Root scope. Components and framework APIs reused; no business entity/soft-delete lifecycle or extra Mapper. CQE Query/Command with no Event; exact audit local transaction. Separate MC evidence per Step, existing architecture retained.

## Backend generation and dependency evidence

No production business catalog scaffolding or generator. V10 is one exact technical append-only Flyway receipt with existing V1/V3 generic rows and V9 style, no old history mutation. No new POM dependency. No generator action or SQL journal is applicable to this technical native boundary. Actual PostgreSQL behavior and IAM admission are separately user-owned.
