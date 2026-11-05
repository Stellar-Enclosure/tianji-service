# T4-03 接管后首次 Session 读取实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:43 Asia/Shanghai |
| Updated | 2026-11-06 03:43 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Four existing main sources, one finite test and three review artifacts |
| Source Requirement | Master §10.2, §17.2 S11, §19 T4-03 |
| Baseline Revision | 76fd92c6fb855a540d9711e180062360501e3cb1 |
| Implements Spec | [First read Spec](../spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:42 Asia/Shanghai SHA-256 a7b12f20f6b5072440c9fae9009b623a6a28c0375dc1e38daf12b2bff9337551 |
| Effective Specs | [First read Spec](../spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

将赢得接管的 Launch 与首次 Session 读取的完整校验绑定。重用现有 cut 摘要、事务锁与原生会话视图。只在事务成功返回后开启后续访问。

## 2. Target Spec and Effective Design

[First read Spec](../spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md) §§4/7/9/11/14 controls this scope. Existing takeover and Session behavior are its context. Native executor, external IAM and PG acceptance remain separate.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Same primary and owner, exact successor leases, no ordinary rebind/renew |
| REQ-002 | §7/11 | Locked exact postclaim Run and original Session raw cut for both codecs |
| REQ-003 | §7/9/14 | Same checked Session returned, early access and failed commit leave the guard closed |
| REQ-004 | §8/14/16 | One exact RED, 29 finite tests and archive parity, eight paths and 28 immutable SQL |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline and source hashes. Apply all five Java sources with typed A omitting only the raw cut digest comparison. Require exact RED, then replace A with B and run G1/P1.

### 4.2 Test-first strategy

The mutation keeps same native ID, invocation and timestamp and a valid event envelope, while changing the raw first user input. RED must fail with a missing expected AgentLedgerException, never an unrelated compile/test failure.

### 4.3 Sequential and parallel boundaries

R1 then G1 then P1 use the same owning offline reactor. No PostgreSQL, app, children or native runtime start. Static document review is independent.

### 4.4 Commit boundaries

Exactly eight paths, one source commit after both strict validators and actual P1 evidence.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Guarded first view | Required §7/9 | EVD-001–003 | Detached recheck races later ordinary read | Reuse scoped adapter and same returned view |
| Raw cut digest | Reuse §7/10 | EVD-002 | New retention or token unnecessary | Share framing exactly |
| Bounded locks | Required §7/11 | EVD-003 | Existing lock order already established | Reuse with bounded first read only |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Typed A and fixture | REQ-001–004 | Same-id altered raw event unexpectedly returns Session | 1–5 | Baseline takeover |
| B guarded adapter | REQ-001–003 | 29 finite source cases and archive checks | 1–5 | Exact RED |
| Scope and evidence | REQ-004 | Strict validators and actual outputs | 6–8 | R1/G1/P1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Adapter ownership | Existing AR Session package | Keep same source and owner, no bean, controller or role change |
| Session boundary | Existing scoped Run, Session, lease and scope lock sequence | Verify exact raw cut within a single bounded first fetch |
| State and events | Existing versioned native codecs and framed SHA-256 | Return same decoded view and avoid extra storage or shape changes |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| First scoped view | PostgresAdkSessionService and JdbcAdkSessionRepository | EVD-003 | Ordinary read does not prove exact cut | Reuse locks and view construction | No dependency | G1 |
| Existing takeover cut | AgentCheckpointObservationService and Launch | EVD-001/002 | Needs exact matching at SDK fetch | Preserve digest framing and same-source identity | No schema | R1/G1 |
| Runtime boundary | Existing components bridge and finite tests | EVD-004 | Executor assembly remains separate | Verify source and archive only | No host binding | P1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | Files 1–5 | Use existing adapter and immutable internal cut with an explicit first-read guard | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | Files 1–5 | Check complete owner, source, fixed epochs, snapshot and decoded envelope at boundary | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | Files 1–5 | Reuse ResumeMatch, SessionView and ADK objects; mutable readiness has one lease-bound owner | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | Files 1–5 | Existing direct adapters; no new bean, injection or logging | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | Files 1–5 | Reuse existing JDBC, codecs and JDK SHA-256 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | Files 1–5 | Reuse native versioned codecs; no wire JSON contract or ordinal | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | Files 1–5 | No configuration or profile edits | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | Files 1–5 | Guarded adapter composes existing physical locks and proven native Session view | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | Files 1–5 | Use fixed DB UTC lease instants and bounded transaction durations | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | Files 1–5 | Existing Agent Runtime Session adapter and Agent primary ownership | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run and Session lease expiry, original Run deadline and event timestamp | Occurred instant | java.time.Instant, JDBC Timestamp and existing native epoch millis | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Both successor leases match original claimed expiry and remain strictly live on the primary | Database clock_timestamp and unchanged PostgreSQL microseconds; preserve stored native epoch millis in digest | Existing deadlines and V16 digest | Step 1 TEST-001/002 |
| TIME-002 | Initial read bounds | Duration | Spring TransactionTemplate and PostgreSQL local timeouts | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Two-second transaction/statement bound, one-second lock timeout, nonblocking scope acquisition | JDBC/DB elapsed clocks | Ordinary Session paths retain current timing | Step 1 TEST-001/004 |


## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointResumeFirstReadTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-first-read.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Use Service 76fd92c6fb855a540d9711e180062360501e3cb1 and current JDK25 offline settings. All 28 existing migrations remain immutable. Retain existing owner-scoped locks and native codec versions. No IAM, product wiring, configuration or runtime changes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Guard the first full Session view of a winning takeover

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing first-input takeover, Session adapters and V16 cut.
- Baseline state: Takeover commits a one-use internal launch without a guarded SDK first view.
- Observable outcome: Same locked view crosses the boundary only after successful first-read transaction.
- End state: Four main sources, one finite test and three reviewer files, with no product caller.
- Test-first gate: Required. Typed A installs the five Java files with only raw digest equality absent in File 2; run exact R1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointResumeFirstReadTest.java`

- Purpose: Lock first-view behavior with both codecs.
- Symbols: AdkCheckpointResumeFirstReadTest.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Lock first-view behavior with both codecs in the ordered scope.
- Contract/signature changes: Internal Java and existing Session SPI only.
- Input/output and state mapping: Use real codecs and mocked JDBC against the new scoped read. Verify ordered locks and commit, changed raw payload, pre-read filters/list/appends, source and lease drift, ambient transaction and commit failure.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
Fixture claims exact Session successor on mocked primary; cut := digest(original state and event).
assertBothCodecs(firstFullGetSessionReturnsSameVerifiedView());
assertR1(changedValidPayloadWithSameIdsThrowsRevisionConflict());
assertRollbackRetainsGateAndRetryRevalidates());
```

- Verification contribution: R1/G1.
- After this file: Exact source ready for the specified phase.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`

- Purpose: Share the original length-prefixed cut digest.
- Symbols: AgentCheckpointObservationService.ResumeMatch#matchesFirstRead.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Share the original length-prefixed cut digest in the ordered scope.
- Contract/signature changes: Internal Java and existing Session SPI only.
- Input/output and state mapping: Reuse field order and exact bytes. Read check consumes existing scoped SessionRow and EventRow; no retained raw Session data.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
cut := original framed owner, execution, ids, deadline, epochs, watermarks and SHA-256;
assertCapturedRevisionSequenceAndEventSequenceMatch();
return cutSha256.equals(digest(exactCodecStateEventIdTimestampPayload));
```

- Verification contribution: R1/G1/P1.
- After this file: Exact source ready for the specified phase.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`

- Purpose: Add exact postclaim Run proof and local bounds.
- Symbols: JdbcAdkSessionRepository#boundCheckpointFirstRead and #checkpointRunMatches.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Add exact postclaim Run proof and local bounds in the ordered scope.
- Contract/signature changes: Internal Java and existing Session SPI only.
- Input/output and state mapping: Reuse full owner and primary Run scope; set bounded local timeouts and check successor expiry, deadline and +2 watermarks after Run lock.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
SET LOCAL statement_timeout = 2s and lock_timeout = 1s with pg_catalog search path;
require primary writable RC and owned RUNNING attempt 1 with exact Session and both postclaim watermarks;
compare new Run epoch/owner/expiry, original deadline and no cancellation/error/result.
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for the specified phase.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`

- Purpose: Guard one physical Session view.
- Symbols: PostgresAdkSessionService#withResumeCheckpoint and #readResumeFirstView.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Guard one physical Session view in the ordered scope.
- Contract/signature changes: Internal Java and existing Session SPI only.
- Input/output and state mapping: Serialize only the first full fetch. Validate source, owner, app, successor lease and no ambient transaction. Register only after commit. Keep normal capture and subsequent scoped operations.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
in bounded writable RC: lock Run, Session, lease and scope in order; verify exact current fences;
reject nonempty scope/state or more than one event; decode the same bounded event only after cut digest;
return same Session view; after transaction returns register and set volatile ready. Reject list/partial/append while guarded.
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for the specified phase.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`

- Purpose: Bind exact winning primary to package launch.
- Symbols: AgentCheckpointTakeoverService.Launch#bindSession.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Bind exact winning primary to package launch in the ordered scope.
- Contract/signature changes: Internal Java and existing Session SPI only.
- Input/output and state mapping: Carry source identity only on winning fresh commit. Pass exact existing Session lease and immutable cut into the guarded adapter.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
return new TakeoverResult(ack, new Launch(primary, runLease, sessionLease, cut, permit.request()));
Launch.bindSession(unboundStore) => unboundStore.withResumeCheckpoint(primary, sessionLease, cut);
existing replay ACK has null launch and cannot bind.
```

- Verification contribution: G1/P1.
- After this file: Exact source ready for the specified phase.

#### File 6 — `CREATE docs/egon/spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md`

- Purpose: Record exact source behavior.
- Symbols: REQ-001–004.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Record exact source behavior in the ordered scope.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Document locked first fetch and no production enablement.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record same primary and owner capability scope;
Describe exact framed digest reuse and same locked view construction;
Separate finite proof, package checks and PostgreSQL/IAM/native execution gates.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available within the same commit.

#### File 7 — `CREATE docs/egon/plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md`

- Purpose: Lock ordered application and tests.
- Symbols: Step 1.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Lock ordered application and tests in the ordered scope.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Record exact hashes and owning validation commands for this source slice.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Apply only the reviewed five Java paths with typed A lacking the digest comparison;
Require exact same-id payload RED before restoring B and running 29 owner tests;
Stage five source paths and three documents after archive verification.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available within the same commit.

#### File 8 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-resume-first-read.md`

- Purpose: Capture observed source evidence.
- Symbols: R1/G1/P1/S1.
- Repository evidence: EVD-001–004 and the effective Spec.
- Dependencies and consumers: Existing AR Session/Run adapters and source reviewer.
- Why now: Capture observed source evidence in the ordered scope.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Update evidence only after gates. Keep real runtime pending.
- Error and edge behavior: Reject mismatched source, owner, fences, persisted bytes or incomplete verification.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact one-assertion R1 and 29-case finite owner evidence;
Record actual AR class in AS archive and AS/PS YAML/manifest parity, 28 SQL byte comparisons;
Explicitly distinguish PostgreSQL/native executor and host IAM as unexecuted.
```

- Verification contribution: S1.
- After this file: Reviewer artifact available within the same commit.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact offline R1, G1, P1 and S1.
- Expected result: R1 one intended missing AgentLedgerException. G1 and P1 repeat 29 finite tests without failure, error or skip.
- Failure returns to: File 2 for a digest witness mismatch, Files 3–5 for lock/binding drift, File 1 for unrelated fixture failure.
- Completion criteria: Same verified Session on success, ready only after commit, ordinary behavior verified, and actual archive and scope parity.
- Rollback: Revert the single scoped source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AdkCheckpointResumeFirstReadTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/JdbcAdkSessionRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/PostgresAdkSessionService.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointTakeoverService.java`, `docs/egon/spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md`, `docs/egon/plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-resume-first-read.md`
- Commit: feat(agent): guard the first resumed Session view.

## 8. Test, Validation, and Quality Gates

```sh
root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cd "$root"
test "$(git rev-parse HEAD)" = "76fd92c6fb855a540d9711e180062360501e3cb1"
red='AdkCheckpointResumeFirstReadTest#resume_first_read_rejects_changed_raw_event_under_the_same_ids'
core='AdkCheckpointResumeFirstReadTest,AgentCheckpointTakeoverServiceTest,AgentCheckpointObservationServiceTest,AdkCheckpointCaptureTest'
"$runner" "$red"
"$runner" "$core"
STELLAR_VERIFY_GOAL=verify "$runner" "$core" clean -Ppermission-producer -Dpermission.commit=76fd92c6fb855a540d9711e180062360501e3cb1 -Dpermission.buildId=LOCAL_UNCOMMITTED-agent-checkpoint-resume-first-read
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-42-agent-checkpoint-resume-first-read.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-43-agent-checkpoint-resume-first-read.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | Exactly one failing selector, expected AgentLedgerException but nothing was thrown, no errors/skips. Typed A uses B files except File 2. |
| G1 | 6 first-read, 10 takeover, 7 observation and 6 capture tests. Exactly 29 passes. |
| P1 | Repeat exact 29 cases in clean producer reactor. Compare actual AS/PS YAML and permission manifests after excluding build identity, confirm new classes in AS nested AR only, no host wiring or PG report and all 28 SQL byte-identical. |
| S1 | Exactly eight paths, strict docs, scope and migration checks. |

## 9. Migration, Compatibility, Rollout, and Rollback

Keep current migrations, runtime wiring, YAML, manifests, dependencies and privileges. No rollout. Revert the one focused commit to roll back.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1,4,5 | G1/P1 |
| REQ-002 | Step 1 | 1–5 | R1/G1/P1 |
| REQ-003 | Step 1 | 1,4,5 | G1/P1 |
| REQ-004 | Step 1 | 1–8 | R1/G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL and native continuation, host current authority and the production executor remain separate. This plan authorizes source scope and finite verification only.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/11 | Existing scoped Agent primary and Session adapter | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Use existing fences, Session view and cut hash | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK25, Spring JDBC and ADK | None |
| MC-NAME-001 | Applicable | PASS | §7/8/10 | Existing immutable cut and scoped launch | None |
| MC-VALID-001 | Applicable | PASS | §7/9/11/14 | Full owner and source checks and exact locked history | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Separate guarded adapter readiness and immutable cut | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No cross-layer mapper or wire DTO | None |
| MC-LOG-001 | Applicable | PASS | §15 | Fixed generic failures without raw stored history or owner | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | No Spring registration or injection | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse framed JDK digest and existing codecs | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Retain supported stored event and state codecs | None |
| MC-TIME-001 | Applicable | PASS | §6.3/11 | Check exact absolute expiry and bounded lock waits | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration changes | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Guarded adapter composes scoped existing reads | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Five Java paths and three review artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact RED and 29 finite owner/P1 checks | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Separate runtime, native executor and IAM gates | None |

### 12.6 Final verdict

PASS — Ready for user review
