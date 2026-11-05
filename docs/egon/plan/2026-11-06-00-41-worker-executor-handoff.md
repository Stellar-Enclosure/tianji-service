# T4-05 Worker 执行器交接实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-00-41-worker-executor-handoff.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 00:41 Asia/Shanghai |
| Updated | 2026-11-06 00:41 Asia/Shanghai |
| Owner | Tianji Worker starter |
| Repository | tianji-service |
| Scope | Existing executor handoff, one finite regression and three docs |
| Source Requirement | 主计划 §17 S10 / §19 T4-05 |
| Baseline Revision | 4ff3f279ff302a8d7aef7c885bbadd952a908165 |
| Implements Spec | [Worker executor handoff](../spec/2026-11-06-00-40-worker-executor-handoff.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 00:40 Asia/Shanghai SHA-256 2b3884dc88fdcbde2862cf48efdfdf5a78021fbff326c5ba9f726a4e48c52764 |
| Effective Specs | [Worker executor handoff](../spec/2026-11-06-00-40-worker-executor-handoff.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 Step 修复 Worker 准入交接竞态。复用同一 AtomicBoolean，在回调和执行器 RuntimeException 路径间原子决定槽位归还。只验证当前有限 Worker 行为。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Worker executor handoff](../spec/2026-11-06-00-40-worker-executor-handoff.md) REQ-001–004.

### 2.2 Effective Spec set

Only primary Spec governs the narrow fix. Existing host Executor, Worker scope and ack contracts remain.

### 2.3 Superseded or excluded content

No S11/S12a change, runtime, dependency, endpoint or S10 I/E evidence.

## 3. Effective Requirements and Acceptance

| Requirement | Implementation | Acceptance |
| --- | --- | --- |
| REQ-001 | Single existing local CAS decision | One slot release |
| REQ-002 | Reject delayed callback after catch wins | Zero acquisition and no premature capacity |
| REQ-003 | Preserve accepted inline/async/stop | Existing finite Worker tests pass |
| REQ-004 | Exact source and test scope | Two Java files, three docs, current dependency boundary |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Review baseline and exact file bytes. Apply R1 test only, then B production. Run two finite suites and strict source evidence.

### 4.2 Test-first strategy

R1 expects one actual failing JUnit assertion on the baseline delayed callback. G1 selects only full owner test and existing engine-free boundary.

### 4.3 Sequential and parallel boundaries

No tracked source write while another change owns the repository. R1 before B. Re-lock if another authorized commit lands first and Worker source unchanged.

### 4.4 Commit boundaries

One Worker fix, exact five paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

The same already-allocated flag suffices. No scheduler, executor, persistence or receipt abstraction is needed.

### 4.6 Change-unit Dependency Matrix

| Unit | Predecessor | Outcome |
| --- | --- | --- |
| R1 | Exact clean baseline | One expected 0 vs 1 assertion |
| B/G1 | R1 | Atomic winner and existing behavior regression |
| S1 | G1 and strict docs | Five exact paths, one commit |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | Existing Worker starter poller | §7 same host | No layer or wiring | Step 1 MC-ARCH-001 |
| Reuse | Existing AtomicBoolean/semaphore/monitor | §7.3 one winner | Reuse same object | Step 1 MC-REUSE-001 |
| Validation | Existing Worker fake scheduler and client | §14 race-specific proof | Deterministic test | Step 1 MC-TEST-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Ownership | AtomicBoolean already in tick | CAS available | Fits | Reuse | No extra object | Step 1 MC-REUSE-001 |
| Regression | Existing RemoteWorkerPollerTest | Current mock scheduler, queued fixture | Fits | Extend exact test | No dependency | Step 1 MC-TEST-001 |

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
| Rule 1 | §6.2 | Worker starter | Files 1/2 | Current Worker types | G1 | Step 1 | PASS |
| Rule 2 | §6.2 | Worker starter | Files 1/2 | One owner per submission and exact fake fixture | G1 | Step 1 | PASS |
| Rule 3 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 4 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 5 | §6.2 | Worker starter | Files 1/2 | Existing JDK atomic classes | G1 | Step 1 | PASS |
| Rule 6 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 7 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 9 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 10 | §6.2 | Worker starter | Files 1/2 | No affected contract | G1 | Step 1 | N/A |
| Rule 11 | §6.2 | Worker starter | Files 1/2 | Preserve current Worker layering and contracts | G1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: No affected deadline, lease, retry, elapsed time or stop timeout semantics. This Step only linearizes ownership of the existing completion.

## 5. Change File Tree

| Action | Exact path | Order |
| --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java` | Step 1 File 1 |
| MODIFY | `stellar-enclosure-tianji-worker-starter/src/main/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPoller.java` | Step 1 File 2 |
| CREATE | `docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md` | Step 1 File 3 |
| CREATE | `docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md` | Step 1 File 4 |
| CREATE | `docs/egon/evidence/2026-11-06-worker-executor-handoff.md` | Step 1 File 5 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Current clean Service `4ff3f279ff302a8d7aef7c885bbadd952a908165` after S11 test-only source. Existing Worker source files are byte-identical to draft origin `8fa8474a2880242c7ef6824a285eea15a0adca3f`. Use current JDK25/offline settings and exact scratch red/B. No app/DB/network tests or overlapping source changes. The optional Worker fault fixture simulates the Executor handing off a runnable and then throwing.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Linearize Worker executor handoff completion

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Reviewed exact Spec/Plan, clean source and offline JDK.
- Baseline state: Existing get/set can allow late callback after rejection.
- Observable outcome: Rejected delayed callback does not acquire or release; accepted callback and stop retain current behavior.
- End state: Five exact paths in one local commit.
- Test-first gate: Required. R1 test-only assertion before B.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java`

- Purpose: Make delayed rejection demonstrable before source.
- Symbols: callback_delivered_after_executor_rejection_cannot_acquire_or_release_a_slot
- Repository evidence: Existing inline and async Worker fixtures, mock current scheduler and WorkerClient.
- Dependencies and consumers: Existing baseline poller and B fix.
- Why now: Apply RED test only before production fix.
- Contract/signature changes: No public API change.
- Input/output and state mapping: First callback retained and rejected, second accepted, stale rejected callback must do no acquisition.
- Error and edge behavior: Check capacity, duplicate delivery, stop/restart and old tick generation.
- Standards impact: MC-VALID-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
poller.start();
firstGeneration.run(); // enqueue callback, then reject
firstGeneration.run(); // enqueue one accepted callback
handedOff.getFirst().run();
assertEquals(0, acquisitions.get());
firstGeneration.run();
assertEquals(2, handedOff.size());
```

- Verification contribution: R1 and G1.
- After this file: R1 one expected assertion; B exact regression.

#### File 2 — `MODIFY stellar-enclosure-tianji-worker-starter/src/main/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPoller.java`

- Purpose: Assign one existing slot completion owner.
- Symbols: RemoteWorkerPoller#tick
- Repository evidence: Existing semaphore, inFlight, started flag and generation.
- Dependencies and consumers: Existing Worker store/client and tests.
- Why now: Apply B only after exact R1.
- Contract/signature changes: No public signature change.
- Input/output and state mapping: Callback and catch use CAS on same local flag; accepted callback keeps original finally.
- Error and edge behavior: Catch winner retires later callback, inline catch loser does not release, stale generation still only releases.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
var completionClaimed = new AtomicBoolean();
try {
    executor.execute(() -> {
        if (!completionClaimed.compareAndSet(false, true)) return;
        try { if (active(current)) process(handler, current); }
        finally { finished(); }
    });
} catch (RuntimeException rejected) {
    if (completionClaimed.compareAndSet(false, true)) finished();
    warnPolling("executor", rejected);
}
```

- Verification contribution: G1 and S1.
- After this file: One completion action for each submission, no changed store/lease.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md`

- Purpose: Freeze reviewed scope in the owning repository.
- Symbols: Reviewed SPEC bytes
- Repository evidence: Current exact Worker baseline and previous implementation evidence.
- Dependencies and consumers: Current logical source commit.
- Why now: Freeze before execution.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Preserve exact required approved bytes.
- Error and edge behavior: Stop on path collision or digest drift.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md
cp -n "$WORKER_REVIEWED_SPEC" docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md
cmp "$WORKER_REVIEWED_SPEC" docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md
```

- Verification contribution: S1.
- After this file: Reviewed document staged in exact source scope.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md`

- Purpose: Freeze reviewed scope in the owning repository.
- Symbols: Reviewed PLAN bytes
- Repository evidence: Current exact Worker baseline and previous implementation evidence.
- Dependencies and consumers: Current logical source commit.
- Why now: Freeze before execution.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Preserve exact required approved bytes.
- Error and edge behavior: Stop on path collision or digest drift.
- Standards impact: MC-SCOPE-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md
cp -n "$WORKER_REVIEWED_PLAN" docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md
cmp "$WORKER_REVIEWED_PLAN" docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md
```

- Verification contribution: S1.
- After this file: Reviewed document staged in exact source scope.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-worker-executor-handoff.md`

- Purpose: Record actual finite and scope evidence.
- Symbols: R1, G1 and S1
- Repository evidence: Current isolated Worker proof and repository gate.
- Dependencies and consumers: Existing evidence readers.
- Why now: After actual owner tests.
- Contract/signature changes: No new API.
- Input/output and state mapping: Record exact assertion, counts and logical commit path.
- Error and edge behavior: Preserve runtime boundary and avoid unsupported S10 acceptance claim.
- Standards impact: MC-TEST-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the exact baseline and expected 0 versus 1 acquisition assertion.
Record actual finite owner test counts with zero skips or errors.
Record the exact five staged paths and unverified production lease/ack/S10 acceptance.
```

- Verification contribution: S1.
- After this file: One logical evidence record.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 exact R1/G1/S1.
- Expected result: One expected R1 assertion, 13 finite green cases, clean scope.
- Failure returns to: Test fixture or atomic decision before staging.
- Completion criteria: Same existing owner behavior and exact five staged paths.
- Rollback: Revert the isolated source commit if authorized.
- Commit paths: `stellar-enclosure-tianji-worker-starter/src/test/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPollerTest.java`, `stellar-enclosure-tianji-worker-starter/src/main/java/top/egon/cola/platform/tianji/worker/RemoteWorkerPoller.java`, `docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md`, `docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md`, `docs/egon/evidence/2026-11-06-worker-executor-handoff.md`.
- Commit: `fix(worker): fence rejected executor callbacks`.

## 8. Test, Validation, and Quality Gates

```sh
worker_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
worker_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
worker_base=4ff3f279ff302a8d7aef7c885bbadd952a908165
test "$(git rev-parse --verify HEAD)" = "$worker_base"
worker_finite() {
  JAVA_HOME="$worker_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$worker_settings" -s "$worker_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
worker_finite 'RemoteWorkerPollerTest#callback_delivered_after_executor_rejection_cannot_acquire_or_release_a_slot'
worker_finite 'RemoteWorkerPollerTest,WorkerDependencyBoundaryTest'
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-00-40-worker-executor-handoff.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-00-41-worker-executor-handoff.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

| Gate | Required actual result | Boundary |
| --- | --- | --- |
| R1 | 1 assertion failure, expected 0 actual 1 acquisition, no errors/skips | Existing production, red test only |
| G1 | 12 Worker owner tests and 1 engine-free dependency test, no fail/error/skip | Exact finite suites only |
| S1 | Strict docs, exact five paths, no SQL/POM/permission or runtime delta | One Worker commit |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration, dependency, new permission or config. Existing host executor/lease contract preserved. No production launch.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step and files | Gate |
| --- | --- | --- |
| REQ-001 | Step 1 Files 1/2 | R1/G1 |
| REQ-002 | Step 1 Files 1/2 | R1/G1 |
| REQ-003 | Step 1 Files 1/2 | G1 |
| REQ-004 | Step 1 Files 3–5 | G1/S1 |

## 11. Risks, Blockers, and User Decisions

A host that silently drops accepted callbacks remains subject to existing timeout. Actual Worker lease, business DB and S10 I/E are independent. Source work is permitted only after exact baseline approval.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Exactly one completion owner and no late rejected acquire.

### 12.2 Spec consistency

No changed lifecycle, permission, state or source contract.

### 12.3 Repository executability

Existing offline source reactor and exact finite test classes.

### 12.4 Test and release completeness

R1/G1/S1 source proof only. Actual I/E remains separate.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/§8 | Existing Worker starter | None |
| MC-REUSE-001 | Applicable | PASS | §7/§8 | Same AtomicBoolean, scheduler and semaphore | None |
| MC-DEP-001 | Applicable | PASS | §6 | No new dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Semantic observer | None |
| MC-VALID-001 | Applicable | PASS | §7/§8 | One CAS winner and current generation | None |
| MC-MODEL-001 | Not applicable | N/A | §7/§8 | No model change | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/§7 | No DTO/PO mapping | None |
| MC-LOG-001 | Applicable | PASS | §7/§8 | Existing fixed warning path unchanged | None |
| MC-BEAN-001 | Not applicable | N/A | §7/§8 | No host wiring change | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK and existing libraries | None |
| MC-JSON-001 | Not applicable | N/A | §7 | No JSON change | None |
| MC-TIME-001 | Not applicable | N/A | §7/§8 | No time contract change | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/§8 | No config change | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Simple fixed observer | None |
| MC-SCOPE-001 | Applicable | PASS | §7/§8 | Exact five paths | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/§8 | Source lock, actual S10 outside Step | None |

### 12.6 Final verdict

PASS — Ready for user review

当前为仓库外 source 计划。无实际数据库、服务或部署。
