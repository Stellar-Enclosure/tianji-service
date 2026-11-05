# T4-03 Agent checkpoint 原子接管实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-05-agent-checkpoint-takeover.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:05 Asia/Shanghai |
| Updated | 2026-11-06 03:05 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Eight AR Java and three review documents, one focused commit |
| Source Requirement | Master §10.2, §17.2 S11, §19 T4-03 |
| Baseline Revision | 9849d535afa8c0e51aa65924760355a02fe3b1e6 |
| Implements Spec | [Takeover Spec](../spec/2026-11-06-03-04-agent-checkpoint-takeover.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:04 Asia/Shanghai SHA-256 aebd9ba7efbdf338d70ae1ab8f1221f8f431337a156f9a1943ee992763dfdabb |
| Effective Specs | [Takeover Spec](../spec/2026-11-06-03-04-agent-checkpoint-takeover.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

按精确 V16 首输入切点增加默认未装配的内部接管事务。当前授权与 JDBC 事务分离。只在锁后证据一致时完成双租约、状态事件和 ACK，重放不产生 launch。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Takeover Spec](../spec/2026-11-06-03-04-agent-checkpoint-takeover.md) §§4/7/9/11/14 defines exact authority, transaction and evidence.

### 2.2 Effective Spec set

The linked Spec and existing V16 capture/observation predecessors stay authoritative. The master authorizes this source slice only. Runtime, caller and operational provisioning are separate.

### 2.3 Superseded or excluded content

No supersession. No product runner, scheduler, endpoint, configuration or grant mutation.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7/9 | Fresh independently supplied exact RESUME decision outside JDBC, strictly current and bounded | Internal AR port |
| REQ-002 | §7/11 | Same attempt, RC post-lock exact cut, paired leases, ordered status pair and ACK atomically | Existing AR state and Session |
| REQ-003 | §7/9/10/11 | Exact historical ACK on replay, one package launch after new commit, no automatic write retry | Existing V2/V5 |
| REQ-004 | §8/14/16 | 30 finite cases, clean actual AS/PS and 28 unchanged SQL | Verification |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Freeze exact baseline and source hashes. Apply helpers and test source with typed A core that removes only postlock Match equality. Run exact R1, then install B and execute owner G1/P1 before writing actual evidence and staging.

### 4.2 Test-first strategy

The R1 fixture changes the observed cut after the ordered locks. Typed A must produce one expected missing-conflict assertion. No unrelated failure, compilation error, skip or runtime execution qualifies. B compares the entire recomputed Match.

### 4.3 Sequential and parallel boundaries

R1, G1 and P1 run sequentially in the owning reactor. Review documents and static source hashes can be prepared without app, DB or operator children.

### 4.4 Commit boundaries

Stage only the eleven locked paths. One source commit after both strict validators, actual reports and archive comparisons.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity and evidence | Direct alternative | Plan decision |
| --- | --- | --- | --- |
| Internal takeover transaction | §7, EVD-001–004 | Existing observe and queue claim are insufficient | One source-only owner |
| Current authority | §7/9, EVD-005 | Existing ordinary permit cannot authorize RESUME cut | Use injected exact port, no deployed adapter |
| V2/V5 and Session leases | §11, EVD-002/003 | No new table needed | Reuse existing rows and same source |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Tests and typed A | REQ-001–004 | One stale-cut failure | Files 1–8 | Existing V16 and current adapter |
| Complete B | REQ-001–003 | 30 owning cases and primary RC recheck | Files 1–8 | Exact R1 |
| Scope/evidence | REQ-004 | P1/S1 | Files 9–11 | Actual R1/G1/P1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Authority | Existing AgentRunAuthorization and AgentExecutionGrant fingerprints | Exact injected RESUME outside JDBC |
| Topology | Existing Agent Runtime Session infrastructure | Same source, no app Bean or runtime caller |
| Consistency | Existing Run/Session/lease/scope locks and V2/V5 receipt | Ordered locks and postlock RC; exact replay ACK only |
| Testing | Current finite fixture and actual producer artifact gates | R1/G1/P1 without product runtime |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Scoped checkpoint evidence | AgentCheckpointObservationService and codecs | EVD-001 | Need writable RC branch after locks | Reuse scoped Match and parser | No dependency | G1 |
| Paired leases | PostgresAdkSessionService and JdbcAdkSessionRepository | EVD-002 | Need join and exact previous epoch | Add bounded internal helpers | No dependency | G1 |
| Receipt replay | V2/V5 and established status event shape | EVD-003/004 | Fit same key and event schema | Reuse owner scope and persist ACK only | No DDL | G1/P1 |
| Current authority | Existing material and fingerprint | EVD-005 | Ordinary action insufficient | Dedicated injected RESUME port | No dependency or host binding | G1 |

### 4.8 User-mandated Java Rule Implementation Matrix

```text
1 类名规范，必须以 java的pojo规范命名。以dao po bo vo dto query command event等结尾
2 每层之间必须被 springboot-validation 校验，复用对象使用分组校验；基于原生和自定义约束注解、@Valid、@Validated 及 ConstraintValidator 扩展实现。ValidationUtils 只承担通用手工校验，不承载全部业务校验；电话号码复用 libphonenumber。
3 Java POJO 默认使用 class，注解为 @Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)，按构造目标选择 @Builder 或 @SuperBuilder；父类状态参与相等性时使用 @EqualsAndHashCode(callSuper = true)。只有不可变 value object 可以使用 record，并豁免 class 的 Lombok 规范；普通实体不能因简单而使用 record。使用 MapStruct、MapStructPlus 和 egon-cola-component-common-core 的 BaseConverter（双向）或 BaseForwardConverter（不可逆投影）进行转换。
4业务类必须使用@Slf4j注解注入log对象。如果业务类被spring管理，必须指定名称，如果是单例的情况下，参考@Service("userService")。如果需要依赖注入，必须@RequiredArgsConstructor进行修饰，不要代码中写。且属性必须被@qualify修饰。
5 工具类只允许使用jdk原生、Apache Commons(commons-lang3、commons-collections4、commons-io、commons-text、commons-codec、commons-beanutils)、Guava。针对Tika按需引入。
6 JSON 使用 Spring Boot Jackson；持久化枚举值使用 @EnumValue，向前端输出的枚举值使用 @JsonValue，禁止 ordinal 作为业务编码。
7 springboot 多环境配置文件，必须保持配置一致，但值不一定一致。
9 复杂业务必须引入设计模式，不允许硬编码
10 日期相关的必须使用java.time下的实体类，不允许使用java.util下的
11 只允许现有三层结构或当前 egon-cola-archetypes 的 DDD 结构，三层结构保持现状。必须尽量复用 egon-cola-components；持久化模块必须使用 Egon COLA MP Starter，DDL 统一由 egon-mp-sdj-ext-starter 分布式管理。业务唯一键必须组合业务列与 deletedAt（数据库 deleted_at），并验证 NULL、租户及重复软删除语义。采用 CQE（Command Query Event）；Event 必须经 egon-cola-component-transactional-outbox-starter 或 MQ 中间件投递。
```

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/7 | EVD-001–006 | Files 1–8 | Use exact existing AR adapter and semantic internal type names | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–006 | Files 1–8 | Validate verified USER, command, exact permit and locked evidence | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–006 | Files 1–8 | Use immutable request/ack and an internal one-use launch capability | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–006 | Files 1–8 | No Spring Bean or new logger | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–006 | Files 1–8 | Reuse Spring JDBC/Tx, JDK SHA-256, ADK and current test stack | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–006 | Files 1–8 | Fixed existing JSONB acknowledgement and status schema, no ordinal | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–006 | Files 1–8 | No configuration or profile change | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–006 | Files 1–8 | Use explicit current authority port and existing JDBC adapters | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–006 | Files 1–8 | Use Instant and DB UTC with conservative microsecond expiry | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–006 | Files 1–8 | Keep existing AR/AgentDB/Session and Command receipt ownership | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ResumePermit.expiresAt, Run/Session lease expiry | Occurred instant | java.time.Instant, JDBC Timestamp and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Strictly before permit and original deadline; both leases share one expiry | Database clock_timestamp; truncate permit down to microseconds. Least of DB now plus 30 seconds, original deadline and permit expiry | Existing schema/deadlines | Step 1 TEST-003/004 |
| TIME-002 | Read, writer and lock bounds | Duration | Spring TransactionTemplate and PostgreSQL local timeouts | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Two-second transaction and statement bounds, one-second lock timeout, no write retry | Existing DB/JDBC elapsed clocks | No existing scheduler defaults changed | Step 1 TEST-002/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-04-agent-checkpoint-takeover.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-05-agent-checkpoint-takeover.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-takeover.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Use Service 9849d535afa8c0e51aa65924760355a02fe3b1e6, JDK25 and current offline settings. Keep all 28 SQL migrations immutable, exact V16 SELECT-only and independent capture-role semantics. Existing-table DML and current RESUME provisioning are external gates. No host, integration profile, database or application startup.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add one exact first-input checkpoint takeover transaction

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing V16 exact capture and observation, current Agent Runtime Session locks and V2/V5 receipts.
- Baseline state: Read-only checkpoint evidence exists, with no takeover mutation or product continuation caller.
- Observable outcome: Same owned Run and attempt, two new fences and ACK-only replay, with package one-use launch only after fresh commit.
- End state: Eight Java and three docs in one verified source commit.
- Test-first gate: Required. Typed A uses Files 1–8, with only postlock Match equality absent in File 8. Run exact R1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`

- Purpose: Lock exact takeover and replay fixtures.
- Symbols: AgentCheckpointTakeoverServiceTest#stale_cut_fails_before_writing.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Test paired fences, current exact authority, stale cut, rollback and ACK-only races.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
fixture.previewMatch = expected;
fixture.lockedMatch = changedSessionEpoch;
assertThrowsRevisionConflict(() -> service.takeOver(owner, command, worker));
assertNoReceiptMutationOrSecondLaunch();
```

- Verification contribution: R1/G1.
- After this file: Exact source ready for its ordered gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`

- Purpose: Verify locked Match and read-only compatibility.
- Symbols: AgentCheckpointObservationServiceTest.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Exercise writable RC exact post-lock evidence and preserve public detached observation.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertPhysicalReadOnlyPreviewIsRepeatableRead();
assertBoundWritableMatchUsesCurrentReadCommittedEvidence();
assertFullOwnerAndCutDigestRetained();
```

- Verification contribution: G1.
- After this file: Exact source ready for its ordered gate.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java`

- Purpose: Verify same connection native lease.
- Symbols: AdkCheckpointCaptureTest#takeover_session_lease_uses_the_bound_connection_and_exact_prior_epoch.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Assert exact previous Session epoch and common absolute Run/Session fence.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertBoundWritableConnection();
lease = sessionService.acquireCheckpointLeaseInTransaction(id, runLease, previousEpoch, expiry);
assertEquals(previousEpoch + 1, lease.epoch());
```

- Verification contribution: G1.
- After this file: Exact source ready for its ordered gate.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`

- Purpose: Read exact V16 and V13 evidence under writable locks.
- Symbols: AgentStorageVerifier#checkpointTakeoverEvidenceReadable.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Reuse same-source physical primary checks, V16 SELECT-only and exact V13 column/catalog/history validation.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireBoundWritablePrimary();
verifyCheckpointRole(true);
return checkpointObservationCoreTables() && checkpointToolEvidenceReadable("off");
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for its ordered gate.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`

- Purpose: CAS exact Session lease row.
- Symbols: JdbcAdkSessionRepository#acquireCheckpoint.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Existing full owner Session/app and prior inactive epoch; existing absolute expiry semantics.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
UPDATE existing lease WHERE exactOwnerAndAppAndSession AND epoch = prior AND inactive AND expiry > databaseClock;
SET newOwner, epoch = prior + 1, expiry;
RETURNING existing lease fields;
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for its ordered gate.

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`

- Purpose: Join existing locked writable transaction.
- Symbols: PostgresAdkSessionService#acquireCheckpointLeaseInTransaction.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Require exact bound datasource, verified owner, inactive exact epoch and valid Run fence.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireBoundWritableSource();
requireRunFence(sessionId, runLease);
row = repository.acquireCheckpoint(sessionId, newOwner, priorEpoch, expiry);
requireMatchingReturnedEpochOwnerAndBothLiveFences();
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for its ordered gate.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`

- Purpose: Recompute exact first-input cut.
- Symbols: AgentCheckpointObservationService#matchForTakeover, #matchInTakeoverTransaction.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Preserve detached RR observation. Recheck exact first cut under same writable RC after locks with fixed bounded read evidence.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
preview = readInspection(owner, query).match();
current = inspectInTransaction(owner, query, writableReadCommitted).match();
frameAndHashExactOwnerRunSessionNativeEventStateAndEpochs();
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for its ordered gate.

#### File 8 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`

- Purpose: Commit same-Run paired takeover and ACK.
- Symbols: AgentCheckpointTakeoverService#takeOver.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Internal Java or finite fixture only.
- Input/output and state mapping: Exact authority, existing lock order, RC equality, paired CAS, status pair, final ACK and one-use handle.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
replay = boundedOwnerReceiptRead();
permit = authorizeExactFreshRequestOutsideJdbc(previewMatch);
return writerRc(() -> {
  lockRunThenSessionThenLeaseThenScope();
  requireExactCurrentMatch();
  reserveExistingResumeReceipt();
  advanceSameRunAndSessionFences();
  appendTwoStatusEventsAndCompleteAck();
  rereadExactPersistedAckAndVerifyLiveFences();
  return oneUseLaunchAfterCommit();
});
```

- Verification contribution: R1/G1/P1.
- After this file: Exact source ready for its ordered gate.

#### File 9 — `CREATE docs/egon/spec/2026-11-06-03-04-agent-checkpoint-takeover.md`

- Purpose: Record exact authority and ownership design.
- Symbols: REQ-001–004.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Record data-source roles, fresh RESUME, RC recheck, same attempt, expiry and durable ACK.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Bind the exact owner and scoped first-input cut to RESUME.
Document physical RR reader and RC writer and the existing lock ordering.
Record two logical status events and one physical CAS.
Tie each exact behavior to R1/G1/P1 without production activation.
```

- Verification contribution: S1.
- After this file: Exact reviewer artifact available.

#### File 10 — `CREATE docs/egon/plan/2026-11-06-03-05-agent-checkpoint-takeover.md`

- Purpose: Lock ordered implementation and artifact gate.
- Symbols: Step 1.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Lock exact reviewed baseline, eight Java candidates, typed A and actual package checks.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Use the exact reviewed Spec digest and Service baseline.
Apply typed A for the exact stale-cut selector with only Match equality absent.
Run the 30-case B selector and clean permission-producer archive verification.
Stage exactly eleven reviewed source and documentation paths.
```

- Verification contribution: S1.
- After this file: Exact reviewer artifact available.

#### File 11 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-takeover.md`

- Purpose: Record actual scope and proof.
- Symbols: R1/G1/P1/S1.
- Repository evidence: EVD-001–006 and effective Spec.
- Dependencies and consumers: Existing Agent Runtime infrastructure and source reviewer.
- Why now: Apply exact typed source and test order before final stage.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Replace pending evidence with actual observed owner reports and artifact SHAs.
- Error and edge behavior: Reject invalid owner, changed evidence, failed write or invalid source gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record fresh expected RED and exact 30-case finite and clean package results.
Compare AS/PS configs and effective manifests, and match 28 migration bytes.
Explicitly leave real PostgreSQL, host authority and native launch unverified.
```

- Verification contribution: S1.
- After this file: Exact reviewer artifact available.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact R1 against typed A, then G1/P1/S1 after B.
- Expected result: R1 one expected assertion; G1/P1 30 finite pass; exact package and scope parity.
- Failure returns to: Files 1–8 for source evidence or failure; Files 9–11 for spec/plan/evidence and staging.
- Completion criteria: Actual fresh reports, exact AS/PS and 28 SQL bytes, and exact eleven paths with explicit runtime gap.
- Rollback: Revert the one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointCaptureTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`, `docs/egon/spec/2026-11-06-03-04-agent-checkpoint-takeover.md`, `docs/egon/plan/2026-11-06-03-05-agent-checkpoint-takeover.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-takeover.md`.
- Commit: feat(agent): take over exact first-input checkpoint.

## 8. Test, Validation, and Quality Gates

```sh
takeover_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
takeover_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
takeover_base=9849d535afa8c0e51aa65924760355a02fe3b1e6
test "$(git rev-parse --verify HEAD)" = "$takeover_base"
takeover_red='AgentCheckpointTakeoverServiceTest#stale_cut_fails_before_writing'
takeover_core=AgentCheckpointTakeoverServiceTest,AgentCheckpointObservationServiceTest,AdkCheckpointCaptureTest,AgentCheckpointStorageVerifierTest
takeover_finite() {
  JAVA_HOME="$takeover_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$takeover_settings" -s "$takeover_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
takeover_finite "$takeover_red"
takeover_finite "$takeover_core"
JAVA_HOME="$takeover_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify   ./scripts/verify-source-reactor.sh -gs "$takeover_settings" -s "$takeover_settings" -o clean -B -ntp   -Ppermission-producer "-Dpermission.commit=$takeover_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-agent-checkpoint-takeover   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$takeover_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-04-agent-checkpoint-takeover.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-05-agent-checkpoint-takeover.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and boundary |
| --- | --- |
| R1 | Only postlock equality omitted. Exact selected method, one intended missing-conflict assertion, zero errors/skips. |
| G1 | Ten takeover, seven observation, six capture and seven storage verifier tests. Require all 30 finite reports and zero failures/errors/skips. |
| P1 | Clean permission producer repeats 30. Check actual AR class in nested AS, AS/PS config bytes and semantic PaC parity against baseline after only build identity removal. No PG/IT report. All 28 migrations byte-identical. |
| S1 | Both strict validators and exact eleven paths. No product startup, config, POM, route, grant or DDL changes. |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration, activation or role grant. Existing capture writer and observer remain independent. Revert one source commit if needed. Runtime host and evidence are separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1,7,8 | R1/G1 |
| REQ-002 | Step 1 | 1–8 | R1/G1/P1 |
| REQ-003 | Step 1 | 1,8 | G1/P1 |
| REQ-004 | Step 1 | 1–11 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL privilege/transaction proof, fresh IAM adapter, invocation execution and crash recovery after claim-before-launch remain independent. No S11 E or production rollout is credited by this source commit.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/11 | Existing AR owning adapter | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–006 | Existing exact V16 match, scoped locks and V2/V5 receipts | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, Spring, ADK and JUnit | None |
| MC-NAME-001 | Applicable | PASS | §7/8/10 | Exact internal symbols and private facts | None |
| MC-VALID-001 | Applicable | PASS | §5/7/11/14 | Owner, permit, cut, expiry and epoch rechecks | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable command/ACK and atomic handle | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No cross-layer mapper or wire DTO | None |
| MC-LOG-001 | Applicable | PASS | §7/15 | Fixed errors and redacted technical values | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | Explicit host construction, no Spring registration | None |
| MC-UTIL-001 | Applicable | PASS | §6/10 | Existing JDK digest and exact authority binding | None |
| MC-JSON-001 | Applicable | PASS | §10/11 | ACK-only existing JSONB and status schema 1 | None |
| MC-TIME-001 | Applicable | PASS | §6.3/11 | UTC Instant and database expiry clock | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No config or activation change | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Explicit authority port and direct adapter reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Eight Java and three documents; 28 existing SQL | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact R1 and 30 finite owner/P1 gates | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source-only acceptance separated from operational prerequisites | None |

### 12.6 Final verdict

PASS — Ready for user review
