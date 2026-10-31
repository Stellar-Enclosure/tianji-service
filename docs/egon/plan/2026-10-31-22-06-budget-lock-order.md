# 预算预留与结算统一run优先锁顺序

| Field | Value |
| --- | --- |
| Document | `2026-10-31-22-06-budget-lock-order.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 22:06 Asia/Shanghai` |
| Updated | `2026-10-31 22:06 Asia/Shanghai` |
| Owner | 用户授权，主代理实施与审计 |
| Repository | `tianji-service` |
| Scope | 工具invocation前置纠正：既有budget reserve/settle同事务先锁run身份；仅8路径 |
| Source Requirement | 原plan继续执行中发现并发锁顺序缺陷，先做独立纠正提交再推进invocation |
| Baseline Revision | `main@56f1ccef87580b57984c2d2fb0ae7a5c3a2ae963` |
| Implements Spec | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户明确授权剩余工作，源文件无规范状态字段 |
| Spec Revision | 2026-10-31；SHA-256 `42d44a5b85e35d218005d7c7a22808c890217c1f21aaf9e930618ee2091dfcb6` |
| Effective Specs | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | [前序owner/queued链](2026-10-31-21-16-tool-authority-issuance-chain.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

当前reserveBudget先INSERT/冲突等待budget再UPDATE run；后续invocation与现有终态/结算路径使用run→budget时，同reservation可能形成反向等待。先独立纠正原budget服务：reserveBudget和settleBudget进入其事务后的第一步都按完整owner namespace锁run行，再执行原逻辑。锁只证明身份，不增加live条件，保留已SETTLED/UNKNOWN相同结果在终态后的幂等重放。invocation草案已移至仓库外并保持Pending，不实施、不混入本次8路径提交。

## 2. Target Spec and Effective Design

主设计为原蓝图§7同源短事务与fencing、§12/13预算幂等和故障一致性、§19 T3-07；这次是推进invocation前必须关闭的既有锁顺序缺口，不添加功能/权限。AgentBudgetService现有TransactionTemplate和JdbcAgentLedgerRepository继续使用。当前lease/deadline/cancel/epoch、配额/金额/未知使用量和终态规则都保持；新run身份锁不能替代原业务有效性检查。

## 3. Effective Requirements and Acceptance

PLAN-REQ是原有一致性/预算要求的局部追踪别名。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/13短事务与预算 | budget预留/结算统一run→budget锁顺序，避免反向等待 | 两个事务第一repository动作均为完整身份run FOR UPDATE，之后才查/插/锁budget | Service/Repository/new unit test |
| PLAN-REQ-002 | §7/13可信owner与兼容 | 身份锁按tenant/type/issuer/subject/run，不用live条件误挡历史事实重放 | 缺run/not owner先notFound，零budget读写；SQL没有状态/租约/deadline/cancel谓词；已SETTLED/UNKNOWN终态重放仍通过 | scoped SQL/test |
| PLAN-REQ-003 | §12/13预算幂等/故障 | 原租约、预算、unknown/rollback语义不变 | 原context budget测试仅补新锁stub仍通过，原caller回归；mixed reserve/settle PG测试可复跑 | 三测试及受影响工具调用者 |
| PLAN-REQ-004 | §6/17边界与证据 | 纠正提交聚焦，未运行PG不标成功 | 精确8路径，无SQL/config/deps/transport/invocation实现；已有PG属性默认跳过 | README/plan/evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先只声明package-private repository新方法供测试编译，返回true的仅编译scaffold；新unit测试stub身份锁成功后观察旧Service未先调用锁的真实RED，同时测repository精确SQL。补现有context fixture的成功锁stub不弱化旧断言。完成SQL和两事务首句后GREEN，再扩既有PG混合并发场景和说明/证据，独立一次提交。先commit本纠正，才恢复/更新invocation草案baseline/API。

### 4.2 Test-first strategy

NEW AgentBudgetLockOrderingTest用实际Spring事务及受控repository/Connection，明确reserve和settle的first-call顺序、缺run短路、终态SETTLED重放、scoped SQL无live条件。RED须因旧顺序或缺SQL行为，不把编译/fixture错误计入。已有context测试只为新前置调用补stub，原context/lease/rollback/幂等断言保留。

### 4.3 Sequential and parallel boundaries

一个Step；主代理拥有两production/README，worker拥有三test，本文作者拥有plan/evidence。接口声明后组件可独立真实RED→GREEN，最终一个commit。invocation及其他步骤不编辑。Pending invocation草案现位于`/tmp/tianji-invocation-preparation-plan-draft.md`，不在提交范围。

### 4.4 Commit boundaries

唯一聚焦纠正提交覆盖§5八路径，记录为T3-07前置锁顺序修正；不改写旧预算提交。显式stage，不push，不将Pending草案或临时文件纳入。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| run身份锁 | §7/13事务/并发一致性 | insertBudgetReservation在run counters UPDATE之前；settle有run/budget锁 | 一条scoped FOR UPDATE和两个首句，无锁管理器/新表 | Implement |
| 业务规则 | 原预算/租约合同 | validLease、lockLeaseForSettlement、bound/unknown/settlement已有 | 全部保留，不用新身份锁重写规则 | Already exists |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| repository锁+service调用顺序 | PLAN-REQ-001/002 | 新顺序/SQL断言 | 最小方法声明、既有预算值和TransactionTemplate | run-first budget事务 | 后续invocation准备 | Step 1 |
| 兼容及并发证明定义 | PLAN-REQ-003/004 | context/PG/helper及caller回归 | 上项GREEN | 持久测试和实际报告 | 用户复核/未来PG验收 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture/reuse | 原AR/infrastructure/persistence技术库/预算TransactionTemplate | 保持原结构/语义 | 不改Bean/模型/预算API，只复用原repository事务 | Step1；MC-ARCH-001、MC-REUSE-001 |
| Validation/model | 既有actor/lease/budget values与exact owner谓词 | 新锁身份非权限 | 先身份锁，业务有效性仍在原位置；无新carrier/实体/Converter | Step1；MC-VALID-001、MC-MODEL-001 |
| Bean/JSON/time/config | 原manual技术JDBC库，不动这些外部面 | 无迁移/时间或协议改造 | 无新DI/日志/JSON/config/时间规则 | Step1；相关N/A独立记录 |
| Pattern | Simple固定SQL/顺序 | 直接复用 | 不加锁服务/策略/引擎，避免过度抽象 | Step1；MC-PATTERN-001 |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| row锁 | JdbcTemplate、ownedRunExists、lockLeaseForSettlement | scoped查询和FOR UPDATE模式已有 | 现live锁会误挡终态重放 | 新纯身份lockRunForBudget，复用原JDBC | None | Step1；MC-REUSE-001 |
| 事务/预算 | AgentBudgetService TransactionTemplate、原reserve/settle实现 | 可加入同源ambient事务，原context规则独立 | 足够，仅缺统一首锁 | 两事务首句调用，不改预算服务结构 | None | Step1；MC-DEP-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6/T3-07 | 既有Service/Repository、新Test | 声明→test→实现 | 语义方法lockRunForBudget，不新carrier | 编译/声明审阅 | Step1 | PASS |
| Rule 2 | §7/13 | 原actor/lease/budget及owner查询 | Service/Repo/test | 完整namespace身份锁；原校验保留；无新HTTP handoff | 正反顺序/SQL测试 | Step1 | PASS |
| Rule 3 | §6/13 | 未改任何模型/Converter | 两技术类 | 不新增POJO/record或手工转换 | diff | Step1 | N/A：无模型变更 |
| Rule 4 | §6 | 原manual技术JDBC类，不新Bean | 两类 | 不改业务Bean/DI/日志 | source diff | Step1 | N/A：无业务Bean面 |
| Rule 5 | §6 | 原JDK/Spring/Mockito | 全Java | 无工具库/依赖新增 | imports/POM | Step1 | PASS |
| Rule 6 | §13 | 无JSON/协议改动 | tests/两类 | 不变codec/枚举/持久编码 | diff | Step1 | N/A：无JSON面 |
| Rule 7 | §6 | 无profile/config路径 | 文档 | 配置键结构不变 | diff | Step1 | N/A：配置不变 |
| Rule 9 | §7/13 | 简单row锁和首句 | 两类 | 直接实现，无锁管理器/策略层 | 顺序测试 | Step1 | PASS |
| Rule 10 | §7/13 | 新身份锁不含任何时间谓词 | Repo/Service | 不新增Clock/时间模型；原live规则全部保持 | SQL/no-live+旧回归 | Step1 | N/A：时间合同不改 |
| Rule 11 | §6/7 | 原AR技术账本、无SQL | 八路径 | 不改schema/模块/框架/协议或invocation | diff/回归 | Step1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable
Reason: 本纠正只增完整身份run FOR UPDATE及两个事务首句，不新增或修改时间字段、Clock、期限/租约计算、存储/JSON精度或zone；原validLease/lockLeaseForSettlement/deadline/cancel规则保持，新身份锁明确不含live谓词。

## 5. Change File Tree

| Operation | Repository-relative path | Responsibility | Step |
| --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetService.java | 两预算事务第一步锁run身份 | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java | package-private scoped lockRunForBudget | Step1 |
| CREATE | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetLockOrderingTest.java | 顺序/缺身份/终态重放/SQL shape | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentContextBudgetServiceTest.java | 既有fixture仅补成功身份锁stub | Step1 |
| MODIFY | stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetPostgresTest.java | 现有opt-in混合reserve/settle并发fixture | Step1 |
| MODIFY | README.md | 预算run-first与未运行边界 | Step1 |
| CREATE | docs/egon/evidence/2026-10-31-budget-lock-order.md | 实际命令/17MC/10Rule证据 | Step1 |
| CREATE | docs/egon/plan/2026-10-31-22-06-budget-lock-order.md | 本纠正执行细化 | Step1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

保持baseline56f1cce和原工作，Pendinginvocation草案移仓库外，未实现任何invocation生产代码。先在repository仅声明`boolean lockRunForBudget(TrustedAgentActor actor,String runId)`仅编译stub（返回true）供新Test编译，然后真RED再实现。SQL按tenant_id/subject_type/subject_issuer/subject_id/run_id SELECT run_id FOR UPDATE；**没有**status/lease/deadline/cancel条件；缺匹配行返回false，由原service notFound映射，不能读写budget。两事务拿到身份锁后原规则全部照旧，尤其已SETTLED/UNKNOWN精确重放在终态后仍允许。

原PG属性`tianji.postgres.test.url`沿旧安全限制/空loopback库fixture，不建新基础设施、不自动启动PG，未给属性仅编译/skip，不能称真实deadlock回归通过。旧SQL/V13、配置、依赖、transport和公开预算API都不改。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 统一预算reserve/settle的完整身份run优先锁

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004
- Dependencies: baseline56f1cce既有预算/ledger；本纠正commit前不推进invocation。
- Baseline state: reserve先budget insert再run UPDATE；settle先查budget，存在与run-first调用者反向等待风险。
- Observable outcome: 两预算事务内第一动作都是同owner run身份FOR UPDATE，原业务有效性和幂等不变。
- End state: 原预算路径统一锁顺序并有受控/opt-in测试定义；invocation仍Pending，真实PG未执行即保留未验收。
- Test-first gate: Required — 最小repository签名stub后新测试真实顺序/SQL RED，再实现；context fixture只补新锁stub，不弱化旧断言。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetLockOrderingTest.java`

- Purpose: Define first-lock order, source isolation and terminal settled/UNKNOWN replay before behavior changes.
- Symbols: reserve/settle first interaction; missing owner/run short circuit; terminal SETTLED/UNKNOWN replay; repository scoped SQL.
- Repository evidence: Existing AgentContextBudgetServiceTest uses real Spring local transaction with mocked repository/Connection.
- Dependencies and consumers: Compile-only repository method declaration and existing budget/domain types; no database.
- Why now: This is the meaningful RED for the original order defect.
- Contract/signature changes: No public API changes; test new package-private lock method and original reserve/settle entry points.
- Input/output and state mapping: Stub a successful identity lock, then assert it is the first repository call in both transactions; false returns notFound without budget reads/writes.
- Error and edge behavior: SQL must bind tenant/type/issuer/subject/run and FOR UPDATE, contain no live predicate; alreadySETTLED/UNKNOWN same settlement returns after terminal without requiring a live lease.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
@Test void reserve_and_settle_take_the_scoped_run_identity_lock_first() {
    repository.lockRunForBudget(actor,runId) = true;
    callOriginalReserveAndSettleWithValidFixtures();
    assertFirstRepositoryCallIsIdentityRunLockBeforeBudgetReadInsertOrLock();
    assertMissingOwnerRunStopsAllBudgetAccess(); assertSettledAndUnknownTerminalReplayArePreserved();
    captureActualRepositorySqlAndRequireFullOwnerParametersWithoutStateLeaseDeadlineOrCancelPredicates();
}
```

- Verification contribution: TEST-001 actual behavior RED/GREEN and TEST-002 repository/source/compatibility assertions.
- After this file: Missing order or SQL behavior is observed after valid fixture setup, never compilation RED.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentContextBudgetServiceTest.java`

- Purpose: Keep existing context-budget regression valid under the new mandatory identity first-lock.
- Symbols: @BeforeEach setup repository.lockRunForBudget success stub.
- Repository evidence: Existing six context/rollback/owner/replay/no-context tests must keep their original assertions.
- Dependencies and consumers: Same mocked ACTOR/LEASE/RUN fixtures and original service.
- Why now: A necessary new collaborator precondition must not turn old tests into unrelated missing-run failures.
- Contract/signature changes: Only add exact successful owner/run lock stub; do not weaken prior validation/rollback assertions.
- Input/output and state mapping: For valid source setup return true for identity lock, leaving context validation/replay/lease branches unchanged.
- Error and edge behavior: Invalid owner/estimate and forbidden ambient-context calls still fail before any storage; never broadly stub all calls or swallow errors.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
In the existing setup, stub repository.lockRunForBudget(ACTOR,RUN.runId()) to true.
Retain the context transaction, rollback, policy drift, context replay and invalid-input assertions.
Keep all fixtures and original argument positions; no relaxed context or live-lease checks.
```

- Verification contribution: TEST-002 existing context six-test compatibility; not an independent fake RED.
- After this file: Old tests can prove original business/context behavior after the new identity lock.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetPostgresTest.java`

- Purpose: Define real same-run mixed reserve/settle concurrency under the existing opt-in database fixture.
- Symbols: New mixed reserve/settle race scenario and settled-after-terminal replay checks.
- Repository evidence: Existing class already uses tianji.postgres.test.url and tests concurrent reservation and settlement/terminal races.
- Dependencies and consumers: Same existing disposable loopback database safety fixture; no infrastructure or new property.
- Why now: Persistence proof obligations belong to the corrective step, while default local execution remains opt-in.
- Contract/signature changes: Keep all original test methods/setup/property restrictions; add narrowly scoped repeated mixed exact-ID operations.
- Input/output and state mapping: Create one valid reservation then race exact same-ID reserve replay and known settlement; assert one receipt/correct counters/no deadlock, then terminal settled/UNKNOWN replay.
- Error and edge behavior: Do not catch deadlock SQL errors as allowed business outcomes, reset production state or start PG; absent explicit property compiles/skips and does not prove runtime.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
@Test void mixed_same_id_reserve_and_settle_preserve_one_receipt_without_inverse_locks() {
    claimFreshScopedRunAndCreateKnownBudgetReservation();
    releaseBarrierForSameIdReserveReplayAndMatchingSettlementInSeparateTransactions();
    awaitBoundedBothResultsWithoutDeadlockOrUnexpectedSqlError();
    assertSingleReservationAndExactCounters(); finishRunThenAssertSameSettledReplayStillReturns();
}
```

- Verification contribution: TEST-003 future explicit PG proof, default compile/skip only.
- After this file: Actual concurrency suite is durable but remains unverified without a supplied database.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`

- Purpose: Implement the minimal full-owner run identity row lock.
- Symbols: boolean lockRunForBudget(TrustedAgentActor actor,String runId).
- Repository evidence: ownedRunExists and lockLeaseForSettlement already provide full-owner predicates and row-lock conventions.
- Dependencies and consumers: Existing JdbcTemplate/actor fields; AgentBudgetService calls inside its current transaction.
- Why now: Only after the new ordering/SQL RED is observed; replace the compile-only declaration.
- Contract/signature changes: Package-private boolean method, no public budget or domain changes.
- Input/output and state mapping: SELECT run_id FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=? FOR UPDATE; matched row true, no match false.
- Error and edge behavior: No status/owner lease/epoch/lease expiry/deadline/cancel predicates; this identity lock must not replace later business checks or reject terminal historical replay.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
boolean lockRunForBudget(TrustedAgentActor actor,String runId) {
    rows = jdbc.query(scoped SELECT run_id FROM tianji_agent.agent_run FOR UPDATE,
        actor.tenantId(),actor.subjectType().name(),actor.issuerFingerprint(),actor.subjectId(),runId);
    return !rows.isEmpty(); // exact owner identity only; no live-state authorization here
}
```

- Verification contribution: TEST-001 scoped SQL and service first-call GREEN.
- After this file: Both budget methods can serialize on run identity before accessing any budget record.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetService.java`

- Purpose: Make the run identity lock the first operation of both original budget transactions.
- Symbols: reserveBudget transaction callback first line; settleBudget transaction callback first line.
- Repository evidence: Original reserve inserts budget before run counter update; original settle reads budget before run/live settlement lock.
- Dependencies and consumers: Existing repository method and current notFound error mapping; no transaction API change.
- Why now: Meaningful RED fixes the required ordering while original behavior remains available.
- Contract/signature changes: No new public overload or request type; preserve every pretransaction validation and original transaction propagation.
- Input/output and state mapping: At start of each transaction: if !repository.lockRunForBudget(actor,lease.runId()) throw notFound; then execute original policy/reserve/settle code unchanged.
- Error and edge behavior: Keep live lease/epoch/cancel/deadline/bounds/unknown/cost/refund behavior where already defined; settled historical replay must not acquire a new live predicate.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
reserveBudget existing transaction callback:
    if (!repository.lockRunForBudget(actor,lease.runId())) throw notFound();
    continue original policy/material, insert/replay, counter/fence/context logic unchanged;
settleBudget existing transaction callback:
    if (!repository.lockRunForBudget(actor,lease.runId())) throw notFound();
    continue original budget read, epoch/live check for RESERVED, settled replay, refund/unknown logic unchanged;
```

- Verification contribution: TEST-001/002 first-lock, missing source, context/caller regression GREEN.
- After this file: Existing budget semantics persist under one consistent run-before-budget order.

#### File 6 — `MODIFY README.md`

- Purpose: Record the corrective lock ordering and honest runtime boundary.
- Symbols: Existing budget/transaction/testing notes.
- Repository evidence: README already distinguishes isolated/source tests from opt-in PG.
- Dependencies and consumers: Actual final implementation and unit/compile outcomes.
- Why now: Describe only after GREEN and focused diff review.
- Contract/signature changes: No feature flag or startup change.
- Input/output and state mapping: State original reserve/settle now identity-lock run first and preserve terminal settled/UNKNOWN replay; invocation remains a later step.
- Error and edge behavior: Do not claim a real PG race or whole-system validation ran if the opt-in class skipped.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
Add a short note describing full-owner run identity locking before both budget reservation and settlement.
State that existing live checks/unknown/refund rules and terminal settled/UNKNOWN replay remain unchanged.
Describe the existing opt-in mixed concurrency regression and retain unexecuted PG/invocation boundaries.
```

- Verification contribution: TEST-004 documentation-to-source review.
- After this file: Readers can distinguish a lock-order correction from new execution capability.

#### File 7 — `CREATE docs/egon/evidence/2026-10-31-budget-lock-order.md`

- Purpose: Record actual corrective RED/GREEN, scope, standards and unexecuted concurrency proof.
- Symbols: Baseline56f1cce, exact selected outputs,17MC/10Rules, local aliases, unchanged SQL and pending invocation.
- Repository evidence: Earlier per-Step reports and skill evidence contract.
- Dependencies and consumers: Actual final source/commands/diff and explicit eight paths.
- Why now: Evidence is filled only after its corresponding command/check completes.
- Contract/signature changes: No product API; one corrective commit linked to the original budget concern.
- Input/output and state mapping: Record first-run-lock SQL/ordering, source miss, settled replay, old context/consumers and PG default skip honestly.
- Error and edge behavior: Do not prefill PG/deadlock proof or mix pending invocation; no private source/body in errors/logs.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001; existing technical JDBC/value contracts only, no model/Bean/time/JSON/config change.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11; preserve exact namespace and old business semantics, simple row-lock order, evidence-backed N/A for untouched surfaces.

- Implementation pseudocode:

```text
Record baseline56f1cce and exact new-ordering RED/GREEN commands, results, selected caller compatibility and PG skip.
Evaluate17 Manual Checks and10 Literal Rules independently with source/test evidence and justified N/A.
Confirm eight-path scope, untouched SQL/config/deps/transport, pending invocation draft outside the repository and no automatic runtime.
After review, commit only the corrective slice; preserve old history and update future invocation baseline later.
```

- Verification contribution: TEST-004 actual audit and final staged-scope gate.
- After this file: The focused correction has reviewable nonruntime evidence before commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`; strict Plan validation from workspace root.
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=AgentBudgetLockOrderingTest,AgentContextBudgetServiceTest,AgentBudgetPostgresTest,ToolActionIntentStoreTest,ToolCapabilityStoreTest,TianjiToolGrantIssuerTest,TianjiToolGrantAuthorizerTest -Dsurefire.failIfNoSpecifiedTests=false`; `git diff --check`.
- Expected result: new ordering/SQL assertions real RED first, then selected nonruntime tests GREEN; existing PG class compiles/skips without explicit property, actual counts recorded.
- Failure returns to: new test fixture if invalid RED, minimal repository/service lines if wrong order, existing fixture if missing required stub; do not broaden schema or live-rule changes.
- Completion criteria: four aliases have nonruntime proof;17MC/10Rules independently PASS or valid N/A; old semantics/SQL unchanged, eight paths only, invocation remains pending until this commit.
- Rollback: explicit revert of this focused corrective commit if necessary, no schema/data/config rollback; preserve old history. Do not resume invocation implementation before a verified replacement ordering is committed.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetLockOrderingTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentContextBudgetServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetPostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/JdbcAgentLedgerRepository.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentBudgetService.java`, `README.md`, `docs/egon/evidence/2026-10-31-budget-lock-order.md`, `docs/egon/plan/2026-10-31-22-06-budget-lock-order.md`
- Commit: `fix(agent): lock run before budget reservation and settlement`

## 8. Test, Validation, and Quality Gates

TEST-001：先新AgentBudgetLockOrderingTest有效RED，再保持相同断言GREEN；最小方法声明只供编译。TEST-002：同source-reactor选择原Context6项及工具caller回归，验证原guard/rollback/replay无回退。TEST-003：现有AgentBudgetPostgresTest新混合并发/终态replay用例只在操作方显式提供`tianji.postgres.test.url`时运行，默认编译/skip，绝不自动建/启DB。

TEST-004：`git diff --check`、八路径/未改SQL/不含invocation及17MC/10Rules逐行审阅。TEST-005：工作区根执行`python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-22-06-budget-lock-order.md --strict`。复用已有命令，不建scripts/work harness；临时文档生成脚本结束删除，不提交。0 unit失败或PG skip均不能证明真实锁竞争已通过。

## 9. Migration, Compatibility, Rollout, and Rollback

没有SQL/schema/配置/依赖/transport/API变更；不加新预算服务或锁基础设施。唯一代码行为为两个原预算事务先取得完整owner的run身份行锁，再执行原逻辑。新锁没有live谓词，已SETTLED/UNKNOWN精确重放不会因run终态被拒绝；RESERVED/live lease/deadline/cancel/epoch/bounds/unknown规则仍按原方法决定。现有TransactionTemplate传播保持，未来同源invocation可复用。无需启动应用或部署操作；回滚仅显式revert本纠正，不改历史。invocation草案需本提交完成后再恢复/更新baseline与最终API，不混入本次。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §7/13 | Step 1 | Service/Repository/new LockOrderingTest | TEST-001 | reserve/settle第一repository操作run FOR UPDATE |
| PLAN-REQ-002 | §7/13 | Step 1 | scoped Repo/new Test/PG fixture | TEST-001/003 | 全namespace/no-live/缺身份短路/terminal settled/UNKNOWN replay |
| PLAN-REQ-003 | §12/13 | Step 1 | 三tests与原预算实现 | TEST-002/003 | 原context/caller语义、混合并发可复跑且运行边界保留 |
| PLAN-REQ-004 | §6/17 | Step 1 | README/plan/evidence及八路径 | TEST-004/005 | 独立纠正提交，无SQL/config/deps/invocation生产 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 先修既有budget锁序，再推进新invocation | 两prod/三tests | API审阅发现budget→run与run→budget潜在相互等待 | 主代理 | Closed：独立八路径纠正提交，invocation停止 |
| DEC-002 | 新锁只身份，不加入live谓词 | Repository/Service/Test | settled终态replay是原事实幂等，不应重新授权live work | 主代理 | Closed：旧live规则完全保留 |
| RISK-001 | 真实PG mixed race尚未执行 | 原PG类 | 用户不自动启动DB；已有显式property | 用户/主代理 | Tracked：默认compile/skip保留未验收，不能声称真实deadlock场景通过 |

## 12. Review and Acceptance

本计划对原事务/预算设计作必要并发纠正，不扩业务、权限、模型、schema或运行能力。简单一条scoped run锁和两个事务首句足够，不引新抽象。八路径、声明→真实RED→最小实现→回归→独立commit清晰；future invocation草案已仓库外暂停。实际结果/失败/skip与所有检查由本Step报告记录，不预填runtime通过。

### 12.5 Blocking Manual Check

计划级结论须在实施后用实际source/output逐行重评。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原AR/persistence两技术类和八路径 | 保持原事务/repository结构，无新层 | None |
| MC-REUSE-001 | Applicable | PASS | 原JdbcTemplate/TransactionTemplate/actor/budget值 | 只补缺失身份锁，不造基础设施 | None |
| MC-DEP-001 | Applicable | PASS | 八路径无POM/build修改 | 无新增依赖/工具 | None |
| MC-NAME-001 | Applicable | PASS | lockRunForBudget、AgentBudgetLockOrderingTest | 方法与测试职责明确，旧类型不动 | None |
| MC-VALID-001 | Applicable | PASS | 完整namespace谓词、service缺row notFound、原validLease等保持 | 身份锁不等于live权限；旧输入/业务校验仍在 | None |
| MC-MODEL-001 | Not applicable | N/A | 未改任何domain/DTO/实体/record | 无模型/Lombok分类变化 | None |
| MC-CONVERT-001 | Not applicable | N/A | 无Converter/跨层映射文件 | 不新增转换层或绕过BaseConverter | None |
| MC-LOG-001 | Not applicable | N/A | 原技术JDBC类，无日志变更 | 无新业务日志/私密输出 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Bean/DI/config/构造器改动 | 技术库原依赖保持，无新Qualifier面 | None |
| MC-UTIL-001 | Applicable | PASS | 既有JDK/Spring/JUnit/Mockito | 无utility库新增 | None |
| MC-JSON-001 | Not applicable | N/A | 无JSON/codec/协议/枚举变更 | 持久编码和外部格式保持 | None |
| MC-TIME-001 | Not applicable | N/A | §4.9，新锁无时间谓词 | 不改Clock/时限/精度/zone；原live规则保持 | None |
| MC-CONFIG-001 | Not applicable | N/A | 八路径无profile/properties | 配置键不变 | None |
| MC-PATTERN-001 | Applicable | PASS | 简单首锁顺序、原repository复用 | 直接实现比锁服务/策略层更安全明确 | None |
| MC-SCOPE-001 | Applicable | PASS | 八路径与Pending草案移仓库外 | 单一纠正，无SQL/invocation/transport扩张 | None |
| MC-TEST-001 | Applicable | PASS | §8真RED/GREEN/context/caller/optin命令 | 非运行证据可执行，PG未跑保留标识 | None |
| MC-BLOCKER-001 | Applicable | PASS | 两明确设计决定及各独立行 | 当前纠正无未决API，future invocation保持Pending | None |

### 12.6 Final verdict

PASS — Ready for user review
