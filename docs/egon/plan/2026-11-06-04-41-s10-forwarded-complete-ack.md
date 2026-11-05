# S10 Worker 完成已提交后的 ACK 丢失验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-04-41-s10-forwarded-complete-ack.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 04:41 Asia/Shanghai |
| Updated | 2026-11-06 04:41 Asia/Shanghai |
| Owner | Tianji Process Server acceptance fixtures |
| Repository | tianji-service |
| Scope | Three test Java paths and three review artifacts |
| Source Requirement | Master §17.2 S10 and §19 T3-03/T3-10 |
| Baseline Revision | b46a86a7f47dd029190c52092a5e4c644eb8caec |
| Implements Spec | [Forwarded COMPLETE ACK Spec](../spec/2026-11-06-04-40-s10-forwarded-complete-ack.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 04:40 Asia/Shanghai SHA-256 2d9994b2f3c7c9694d8d54db986f1d5ea2c9a1df44f7213c3072d9d84da31fe9 |
| Effective Specs | [Forwarded COMPLETE ACK Spec](../spec/2026-11-06-04-40-s10-forwarded-complete-ack.md) |
| Depends On Plans | [Expense acceptance Plan](2026-11-05-15-30-expense-four-path-acceptance.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在现有验收用例中，新增已完成的 COMPLETE 成功响应丢失并由同一 Worker 命令重试的边界。保持生产接口和原验收开关，源代码和有限证明完成后由操作者运行 E。

## 2. Target Spec and Effective Design

[Forwarded COMPLETE ACK Spec](../spec/2026-11-06-04-40-s10-forwarded-complete-ack.md) §§4/7/9/14 governs the exact owner witness, postcommit fault and one original receipt. Existing recovery infrastructure provides scoped credentials, database observers, bounded polling and release checks.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Forward one exact owned COMPLETE and verify typed 200 before dropping |
| REQ-002 | §7/9/10 | Same request digest and original response only |
| REQ-003 | §7/14 | Per-tenant exact original completed lease and one business effect |
| REQ-004 | §8/14/16 | Exact R1, finite G1/P1, unchanged manifest and evidence gates |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline and six paths. Stage finite tests, fixture relay and operator test with the typed R1 mutation, then restore B for G1 and P1. Stage review artifacts after evidence.

### 4.2 Test-first strategy

R1 omits only retry-body digest equality in the matched replay. The changed-output test must report that its expected IllegalStateException was not thrown. Restore B before G1.

### 4.3 Sequential and parallel boundaries

Keep R1, G1, P1 and S1 ordered. Static inspection of already compiled artifacts is independent. The E operator selector stays excluded from all local test commands.

### 4.4 Commit boundaries

One focused commit covering exactly three test Java files and three review documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Scoped committed response fault | Use §7.0 | Existing request drop never forwards | Reuse private relay | One new guarded phase |
| One separate run per tenant | Use §7.0 | Existing scenario and Worker pool | Reuse original selector | Use existing standard ACK policy |
| Separate release witness | Omit §7.0 | Locked release evidence policy | Existing successful selector | Preserve report and manifest schema |

### 4.6 Change-unit Dependency Matrix

| Change unit | Depends on | Blocks | Order |
| --- | --- | --- | --- |
| Finite relay witness | Existing fake transport and receipt DTO | R1/G1 | 1 |
| Relay and opt-in operator source | Existing driver, observer and Worker | R1/G1/P1 | 2–3 |
| Review artifacts | Effective Spec and exact owner evidence | S1 | 4–6 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | ExpenseAcceptanceHarness and Driver | Reuse exact test-only route, tenant and auth scope |
| JSON and privacy | Existing Jackson and HttpProcessClient | Typed receipt and private digest, no wire changes |
| Error boundaries | Existing fault window and test transport | Fail closed before incorrect retry, keep prior mode |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Bounded transport | ExpenseAcceptanceFaultRelay | EVD-001 | Existing phase drops before upstream | Extend guarded phase | One private test state | Step 1 R1/G1 |
| Exact retry | Worker poller and SDK | EVD-003 | Stable fixture command and serialization | Reuse | None | Step 1 G1/P1 |
| Durable witnesses | Driver observer, lease and effect checks | EVD-004 | Existing role reads sufficient | Reuse | New test assertions | Step 1 P1/E |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–3 | Reuse Worker command/receipt DTO names; relay and test types retain technical names | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–3 | Existing DTO field guards, exact tenant owner witness, bounded valid JSON, and exact retry digest | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–3 | Existing immutable CQE command and receipt; immutable private witness holds a digest and receipt only | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–3 | No Spring bean, injected business dependency, or logging added | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–3 | Existing fixture, Jackson mapper and JDK SHA-256 utilities | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–3 | Existing bounded Jackson codec decodes typed COMPLETE receipt and preserves exact request bytes | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–3 | No production settings, properties, profiles or new dependencies | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–3 | Reuse the fixture transport and existing guarded window state machine | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–3 | Use the existing elapsed Duration window and unchanged receipt Instant | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–3 | Existing Process Server test boundary, real SDK/Worker and observed receipts without new storage | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | FaultWindowDTO budget | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Bound both forwards by the current window | Existing elapsed nanoTime | No lease or retry clock change | Step 1 TEST-001 |
| TIME-002 | WorkerReceiptDTO decidedAt | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Require exact replay decidedAt equality | Existing persisted decision precision | No lease or retry clock change | Step 1 TEST-002 |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseWorkerCompletionFaultTest.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java
MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java
CREATE docs/egon/spec/2026-11-06-04-40-s10-forwarded-complete-ack.md
CREATE docs/egon/plan/2026-11-06-04-41-s10-forwarded-complete-ack.md
CREATE docs/egon/evidence/2026-11-06-s10-forwarded-complete-ack.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

Use exact baseline and existing source reactor. Running WorkerRecoveryIT requires the existing explicit RECOVERY manifest, fresh isolated PostgreSQL roles/stores, loopback TLS and exact artifact locks. The current Step prepares that selector but locally executes only finite fixture tests.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prove exact retry after a committed Worker completion response is lost

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing expense acceptance harness, Worker poller, real SDK and Process observer.
- Baseline state: Real E source covers request loss before upstream and MockMvc covers postcommit response loss.
- Observable outcome: Existing E selector can verify a successful upstream COMPLETE then lose its response and observe only exact retry.
- End state: Three Java paths and three review artifacts at the locked service revision.
- Test-first gate: Required. The typed R1 candidate removes only retry digest equality.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseWorkerCompletionFaultTest.java`

- Purpose: Exercise exact replay and typed receipt matching using only fake relay transport.
- Symbols: ExpenseWorkerCompletionFaultTest
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Exercise exact replay and typed receipt matching using only fake relay transport.
- Contract/signature changes: Test-only implementation.
- Input/output and state mapping: Use an existing immutable CompleteWorkerTaskCommand and fake completed receipt. Check different output never reaches transport.
- Error and edge behavior: A changed receipt decision time cannot satisfy replay.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
assertThrows(ConnectionDropped.class, () -> relay.exchange(original));
assertThrows(IllegalStateException.class, () -> relay.exchange(changedOutput));
assertEquals(200, relay.exchange(original).status());
assertSameCommittedDecisionTime();
```

- Verification contribution: R1/G1/P1.
- After this file: Use an existing immutable CompleteWorkerTaskCommand and fake completed receipt. Check different output never reaches transport.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`

- Purpose: Forward the exact original COMPLETE before dropping its successful response.
- Symbols: ExpenseAcceptanceFaultRelay#forwardWorkerCompletion
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Forward the exact original COMPLETE before dropping its successful response.
- Contract/signature changes: Test-only implementation.
- Input/output and state mapping: Use the existing owner and business commit proof, hold a digest plus typed receipt, and forward only an exact retry.
- Error and edge behavior: An invalid upstream receipt, changed retry bytes, or changed response cannot prove success.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactOwnedBusinessCommitBeforeInitialForward();
var originalReceipt = readCompletedReceipt(forwardChecked(request));
rememberDigestAndOriginalReceiptThenDropResponse();
requireExactRetryBodyDigestBeforeForward();
finishOnlyWithIdenticalTypedReceipt(originalReceipt);
```

- Verification contribution: R1/G1/P1.
- After this file: Use the existing owner and business commit proof, hold a digest plus typed receipt, and forward only an exact retry.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`

- Purpose: Add one existing recovery-scenario run per tenant with bounded asynchronous Worker.
- Symbols: WorkerRecoveryIT#forwardedAcknowledgement
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Add one existing recovery-scenario run per tenant with bounded asynchronous Worker.
- Contract/signature changes: Test-only implementation.
- Input/output and state mapping: Use a distinct fixture business key, exact task, standard two-attempt mode and the current observer roles.
- Error and edge behavior: A changed receipt, extra lease, extra effect or wrong process result fails the original selector.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var run = driver.start(tenant, RECOVERY, "forwarded-complete", 200);
armExactWorkerJobAndStartStandardPoller(run);
awaitScopedReplayAndAssertUniqueCompletedOriginalLease(run);
assertExistingBusinessReceiptAndNormalProcessEnd(run);
```

- Verification contribution: P1/S1.
- After this file: Use a distinct fixture business key, exact task, standard two-attempt mode and the current observer roles.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-04-40-s10-forwarded-complete-ack.md`

- Purpose: Record exact fixture behavior, authorization and durable witness boundaries.
- Symbols: Spec §§4/7/9/14
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Record exact fixture behavior, authorization and durable witness boundaries.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Document pre-forward and postcommit cuts separately, and keep the production contract unchanged.
- Error and edge behavior: No runtime claim is inferred from source or finite proofs.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record exact fixture scope and existing actor identities.
Specify verified upstream receipt loss and unchanged retry bytes.
Keep machine evidence schema unchanged and list operator E prerequisites.
```

- Verification contribution: S1.
- After this file: Document pre-forward and postcommit cuts separately, and keep the production contract unchanged.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-04-41-s10-forwarded-complete-ack.md`

- Purpose: Record the ordered six-path implementation and exact focused checks.
- Symbols: Plan Step 1
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Record the ordered six-path implementation and exact focused checks.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Pin the Spec hash and expected baseline and record typed digest-only mutation.
- Error and edge behavior: Unexpected assertion, failed tests, unexpected file or runtime exercise fails the gate.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Apply fixture and finite source with only retry digest equality removed for R1.
Restore the digest check and run both finite owner classes.
Compile the gated WorkerRecoveryIT without invoking it and review exact paths.
```

- Verification contribution: S1.
- After this file: Pin the Spec hash and expected baseline and record typed digest-only mutation.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-s10-forwarded-complete-ack.md`

- Purpose: Record exact test and archive observations without claiming operator E.
- Symbols: R1/G1/P1/S1 evidence
- Repository evidence: EVD-001–005 in the effective Spec and the locked baseline.
- Dependencies and consumers: Existing Process Server acceptance fixtures and exact source reviewer.
- Why now: Record exact test and archive observations without claiming operator E.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Keep observed R1, finite counts, source compilation and migration proof separately labeled.
- Error and edge behavior: No live listener, child service, database, authorization or report mutation is authorized by this Step.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record focused missing IllegalStateException and finite pass counts.
Record isolated compile or clean package evidence and unchanged SQL.
Keep actual HTTPS, IAM, BusinessDB and ProcessDB E acceptance pending.
```

- Verification contribution: S1.
- After this file: Keep observed R1, finite counts, source compilation and migration proof separately labeled.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact offline R1, G1, P1 and S1.
- Expected result: One intended R1 assertion and 13 finite G1/P1 successes with no skips, errors or failures.
- Failure returns to: File 1 for finite fixture, File 2 for scope/retry behavior, and File 3 for exact operator source.
- Completion criteria: Exact RED, finite suite, operator source compile, unchanged production files and documented pending E.
- Rollback: Revert the single scoped test commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseWorkerCompletionFaultTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`, `docs/egon/spec/2026-11-06-04-40-s10-forwarded-complete-ack.md`, `docs/egon/plan/2026-11-06-04-41-s10-forwarded-complete-ack.md`, `docs/egon/evidence/2026-11-06-s10-forwarded-complete-ack.md`
- Commit: test(process): prepare committed Worker acknowledgement loss acceptance.

## 8. Test, Validation, and Quality Gates

```sh
root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
cd "$root"
test "$(git rev-parse HEAD)" = "b46a86a7f47dd029190c52092a5e4c644eb8caec"
red='ExpenseWorkerCompletionFaultTest#drops_forwarded_success_and_observes_only_the_same_command_and_committed_receipt'
core='ExpenseWorkerCompletionFaultTest,ExpenseAcceptanceHarnessTest'
# Apply only the typed retry-digest mutation before R1 and restore B afterwards.
"$runner" "$red"
"$runner" "$core"
STELLAR_VERIFY_GOAL=verify "$runner" "$core" clean -Ppermission-producer -Dpermission.commit=b46a86a7f47dd029190c52092a5e4c644eb8caec -Dpermission.buildId=LOCAL_UNCOMMITTED-s10-forwarded-complete-ack
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-04-40-s10-forwarded-complete-ack.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-04-41-s10-forwarded-complete-ack.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Expected proof |
| --- | --- |
| R1 | One missing IllegalStateException for changed output retry, no syntax, fixture or environment failure. |
| G1 | 2 new fake relay tests and 11 existing ExpenseAcceptanceHarnessTest cases, no real listener/child/DB and no skips. |
| P1 | Repeat exact 13 in clean source reactor with WorkerRecoveryIT compiled and skipped by existing IT gate. Check 28 existing migrations byte-for-byte and existing PS packaging scope. |
| S1 | Strict Spec/Plan and exact six paths. Existing recovery E selector, manifest and machine evidence schema are preserved. |

## 9. Migration, Compatibility, Rollout, and Rollback

No SQL, production class, POM, profiles, permissions or deployment changes. Revert the focused commit to remove the extra fixture scenario.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–3 | G1/P1 |
| REQ-002 | Step 1 | 1–3 | R1/G1/P1 |
| REQ-003 | Step 1 | 2–3 | P1 and operator E |
| REQ-004 | Step 1 | 1–6 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

No real network, server process or PostgreSQL execution in the source gate. Actual E remains manifest-gated. The existing report schema gains no dedicated witness field, so successful execution of the exact locked selector is the extra check.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Only Process Server acceptance fixtures and finite test | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Existing relay, worker poller, same SDK, witnesses and selector | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Existing JDK, Jackson and test dependencies | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Existing Worker command and typed receipt plus private fixture witness | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Exact owner and business commit guard, digest and original typed receipt | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Bounded private immutable witness, no business object introduced | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing direct Jackson contract binding, no converter or mapping change | None |
| MC-LOG-001 | Applicable | PASS | §15 | Existing generic failures; no new logs or material exposed | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | Existing test-only constructor, no Spring registration | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Use JDK SHA-256 and existing relay codec and synchronization | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | Use exact SDK bytes and existing strict typed WorkerReceiptDTO decode | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Existing bounded deadline, stable decidedAt Instant | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No manifest gate, production config or profile changes | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing relay transport and guarded phase lifecycle | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact three test Java paths and three review documents | None |
| MC-TEST-001 | Applicable | PASS | §14 | Digest-only R1, full finite fixture class and separate operator compile gate | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Real HTTPS, IAM, databases and E evidence still require operator environment | None |

### 12.6 Final verdict

PASS — Ready for user review
