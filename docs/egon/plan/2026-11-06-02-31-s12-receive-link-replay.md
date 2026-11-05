# T4-02 S12 Agent 结果重放 Span Link 实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-31-s12-receive-link-replay.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 02:31 Asia/Shanghai |
| Updated | 2026-11-06 02:31 Asia/Shanghai |
| Owner | Tianji Process Server acceptance fixtures |
| Repository | tianji-service |
| Scope | Eight PS test files and three review documents, one commit |
| Source Requirement | Master §17.2 S12, §19 T4-02/T4-05 |
| Baseline Revision | Service c07b8eddbb8459c04c7fd26953933a02ef0b347c |
| Implements Spec | [S12 replay Spec](../spec/2026-11-06-02-30-s12-receive-link-replay.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 02:30 Asia/Shanghai SHA-256 4eacf92005f34a0d24c9680022253fb0417c02f21fc6e87af999828d131b6379 |
| Effective Specs | [S12 replay Spec](../spec/2026-11-06-02-30-s12-receive-link-replay.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Prepare independent operator proof for two real PS Receive Links after one committed 202 is lost and AS restarts with same result. Verify exact relay and collector boundaries using finite source gates. Existing BUSINESS/RECOVERY/direct RECEIVE_LINK evidence retains its existing contract.

## 2. Target Spec and Effective Design

The [S12 replay Spec](../spec/2026-11-06-02-30-s12-receive-link-replay.md) §§4/7/10/14 governs only the new private mode and graph oracle. Existing S12a provides pinned collector/runtime and exact direct link. Existing recovery test provides lost response and restart choreography. Operator E1 is separate.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/11/14 | Existing committed drop, durable retry, distinct replacement and same-event one-advance proof | Operator fixture |
| REQ-002 | §4/5/7/9 | Exact scoped RESULT POST and phase, one valid header, existing mode compatibility | Test relay |
| REQ-003 | §4/6.3/7/10/14 | One persisted enqueue, two real Receive Links, replacement timing and bounded extra AS attempts | Test collector |
| REQ-004 | §4/8/14/16 | Actual finite/package evidence and exact 11 paths; operator E separate | Review evidence |

## 4. Implementation Strategy and Dependency Order

Lock clean baseline and A/B drafts. Run exact typed RED with File 1 and File 2 A. Apply all B files in order, run finite GREEN, then clean package and verify both actual archives and compiled test-only scope. Fill exact evidence and stage just the eleven paths.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing PS acceptance Driver and test relay | §6–8 | Source test-only and existing module structure | S1 |
| Validation | Existing exact POST, body and committed relay witness | §5/7/10 | One valid carrier and exact two replay phases | G1/P1/E1 |
| Time and isolation | Existing UTC child timestamps and closed collector parser | §5/6.3/11 | Separate replacement generation and bounded read | G1/P1/E1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Restart replay | WorkerRecoveryIT | earlyResultAndAgentRestart persists same event after lost 202 | Fits | Reuse one test helper | Narrow test visibility | Step 1 MC-REUSE-001 |
| Scoped private header | ExpenseAcceptanceFaultRelay and AgentResultTraceLinkCarrier | Existing exact path and carrier contract, current relay strips header | Test-only gap | Explicit private replay mode | One bounded link fact per committed phase | Step 1 MC-VALID-001 |
| Collector verification | S12ReceiveLinkCollectorEvidence | Existing pinned SDK and strict JSONL reader | Reuse parse, separate oracle | Bound replay graph | No new dependency/collector config | Step 1 MC-JSON-001 |
| End-to-end fixture | ExpenseAcceptanceHarness and Driver | Existing exact artifact/role/manifest guards | Fits | Separate suite/property | One new test mode | Step 1 MC-ARCH-001 |

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
| Rule 1 | §7/8 | Existing acceptance test ownership | Files 1–8 | Exact test symbols and modes | P1 | Step 1 | PASS |
| Rule 2 | §5/7 | Closed relay route and phase, existing guarded manifest | Files 1–8 | Exact owned request/receipt and bounded graph | R1/G1/P1/E1 | Step 1 | PASS |
| Rule 3 | §10 | Existing test DTO and private observation | Files 2/3 | No new business model | S1 | Step 1 | N/A |
| Rule 4 | §6 | Existing test fixture wiring | Files 1–8 | No new application bean/logging | S1 | Step 1 | N/A |
| Rule 5 | §6/7 | Existing JDK/JUnit/collector helpers | Files 1–8 | Reuse current fixture dependencies | G1/P1 | Step 1 | PASS |
| Rule 6 | §7/10 | Existing bounded Jackson reader | Files 1/2 | Strict replay and direct predicates with existing parser | R1/G1/P1 | Step 1 | PASS |
| Rule 7 | §5/16 | Test-only manifest modes | Files 5/6/8 | Preserve production defaults | P1/S1 | Step 1 | N/A |
| Rule 9 | §13 | Compose existing test fixtures | Files 1–8 | No production design expansion | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | Actual child/OTLP UTC instants | Files 1/2/8 | Require replacement ordering and bounded wait | G1/E1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing PS test paths and ownership | All | No production code/config/role/SQL changes | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ChildIdentityDTO.startedAt and collector span start | Occurred instant | java.time.Instant | UTC instant | Internal | Existing child/collector UTC time | No display change | Original before replacement; replay at or after replacement | Existing child and OTLP timestamps | Test only | Step 1 G1/E1 TEST-002/004 |
| TIME-002 | Collector poll and recovery waits | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time has no zone | No display change | Five-second bounded poll after child close | Existing monotonic clock | Test only | Step 1 P1/E1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java` |
| Create | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkReplayAcceptanceIT.java` |
| Create | `docs/egon/spec/2026-11-06-02-30-s12-receive-link-replay.md` |
| Create | `docs/egon/plan/2026-11-06-02-31-s12-receive-link-replay.md` |
| Create | `docs/egon/evidence/2026-11-06-s12-receive-link-replay.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Service c07b8eddbb8459c04c7fd26953933a02ef0b347c. Use exact frozen A/B candidates, approved offline source reactor and JDK 25. New source mode has no production startup authority. Clear all ordinary, direct and replay manifest properties from finite gates. Compare all 28 existing migrations, actual AS/PS app defaults, dependency and protected declaration scope. E1 independently requires fresh privateRoot, pinned collector and exact artifact/fixture manifest.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare S12 result-replay receive-link operator proof

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing PS acceptance fixtures, pinned direct S12 collector reader, production exact header and existing recovery choreography.
- Baseline state: Existing independent direct Link and recovery cases. Exact new replay oracle and E method absent.
- Observable outcome: One exact R1, 19 finite passes in G1 and P1, compiled E method and actual package no fixture. Operator E1 pending.
- End state: Eight PS test files and three review docs in one commit.
- Test-first gate: Required. Apply both A files only and run exact new collector method. The typed A predicate returns false and the first positive assertion must fail. An unresolved symbol, different failure, skip or successful test invalidates R1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java`

- Purpose: Specify exact collector R1/GREEN replay graph.
- Symbols: S12ReceiveLinkCollectorEvidenceTest#replay_matches_one_exact_enqueue_two_receives_and_bounded_blocked_attempts
- Repository evidence: S12a collector tests and EVD-004.
- Dependencies and consumers: Existing reader and test spans; typed A helper for compilation.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: One additional finite method; existing direct tests retained.
- Input/output and state mapping: Valid exact replay graph, incomplete capture, old generation, wrong receive target/parent, extra source, duplicate link, cap overflow.
- Error and edge behavior: A false positive or unsafe graph fails the selected assertion.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
assertTrue(replayLinked(ownedGraph, exactEnqueue, lostLink, replayLink, earliest, replacementStart));
assertFalse(replayLinked(incompleteGraph, exactEnqueue, lostLink, replayLink, earliest, replacementStart));
assertThrows(IllegalStateException.class, () -> replayLinked(oldAttemptGraph, exactEnqueue, lostLink, replayLink, earliest, replacementStart));
```

- Verification contribution: R1/G1/P1.
- After this file: Finite exact replay assertions are defined.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java`

- Purpose: Add bounded replay-only collector predicate using strict parser.
- Symbols: S12ReceiveLinkCollectorEvidence#replayLinked/#readSpans and ObservedSpan
- Repository evidence: EVD-004; existing parser and direct six-span graph.
- Dependencies and consumers: File 1, exact production wire validator and File 8.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Test-only replay method and private parsed start Instant; current linked method contract remains.
- Input/output and state mapping: Map one persisted enqueue, exact first/replacement wire IDs and two receive graph edges. Bound total deliveries to eight.
- Error and edge behavior: Reject wrong links, time, duplicate IDs, parent substitution, extra root/receive and sensitive span fields. Allow incomplete evidence to poll.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
var spans = readSpans(exported, earliest, 11);
verifyOneExactEnqueueAndBoundedDeliveries(spans);
verifyTwoExactReceiveLinksAndReplacementStart(spans, lostLink, replayLink, replacementStartedAt);
```

- Verification contribution: R1/G1/P1/E1.
- After this file: A typed false replay predicate gives exact RED. Replace with B reader for GREEN.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`

- Purpose: Forward validated diagnostic carrier only in the exact test replay RESULT path and phases.
- Symbols: ExpenseAcceptanceFaultRelay#open/#arm/#exchange/#upstreamRequest/#committedResultLink
- Repository evidence: EVD-003/005; current exact route and commit witness.
- Dependencies and consumers: Existing test transport, production Link format and File 4.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Fixture boolean, bounded mode, private accepted phase link and request-construction seam.
- Input/output and state mapping: Replay suite exact HTTPS loopback RESULT POST builds one header. Only committed-drop and replacement phases arm once. Match exact owner/body/event and retain only completed phase links.
- Error and edge behavior: Reject wrong route/header, remote origin, unmatched or reused result window, wrong phase or unverified receipt. Nonreplay and other directions retain old forwarding.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
if (resultLinkReplay && route == RESULT) requireArmedExactResultPhase();
var carrier = requireOneValidCarrierForExactReplayResultPost(request);
var response = forwardOnlyExactLoopbackReplayCarrier(request);
recordPrivateLinkAfterExactReceiptAndDurableCommit(carrier, response);
```

- Verification contribution: G1/P1/E1.
- After this file: Relay witness is available only in the private replay suite.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java`

- Purpose: Prove relay scope and transition safeguards without transport.
- Symbols: ExpenseAcceptanceHarnessTest#s12ReplayBuildsTheExactUpstreamResultHeaderWithoutOpeningATransport/#s12ReplayBindsOnlyTwoCommittedForwardedLinksAndClosesTheWindowGap
- Repository evidence: EVD-003; existing relay tests use stub witnesses.
- Dependencies and consumers: File 3 and existing harness test fixtures.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Two finite tests and small local builders only.
- Input/output and state mapping: Test exact RESULT method/URI/header, old mode and other-route stripping, blocked gap, wrong phase, rejected premature replay and exactly two committed wire observations.
- Error and edge behavior: Malformed, duplicate, nonlocal or invalid phase must reject before successful forwarding.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
assertEquals(List.of(valid), upstreamRequest(exactOrigin, result, timeout, true).headers().allValues(header));
assertTrue(upstreamRequest(exactOrigin, result, timeout, false).headers().allValues(header).isEmpty());
assertThrows(IllegalStateException.class, () -> replayRelay.exchange(unarmedResult));
assertEquals(2, forwarded.get());
```

- Verification contribution: G1/P1.
- After this file: Exact relay behavior and existing compatibility regressions can run offline.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: Add the private RECEIVE_LINK_REPLAY suite and reuse pinned trace startup.
- Symbols: ExpenseAcceptanceHarness.Suite#observesReceiveLinks, open, addTrustedFixtureSettings, launchCommand and publishEvidence
- Repository evidence: EVD-001/004/006; existing pinned artifact/SDK and private trace profile.
- Dependencies and consumers: Existing AS/PS fixture mains and File 6 Driver.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: One private enum and predicate, no public DTO or fixture files.
- Input/output and state mapping: Both trace suites use the same SDK prep and child flags and cannot publish ordinary Expense evidence. Other suites keep previous branch.
- Error and edge behavior: Invalid private manifest, hash mismatch or attempted ordinary evidence publication reject the fixture.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
if (manifest.suite().observesReceiveLinks()) prepareExistingPinnedCollectorAndSdk();
applyExistingTraceChildFlagOnlyForTraceSuites();
rejectOrdinaryExpenseEvidenceForTraceSuites();
```

- Verification contribution: G1/P1/E1.
- After this file: Replay suite loads only existing private tracing setup.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: Bind the new exact private property and reuse persisted carrier evidence.
- Symbols: BusinessProcessAgentAcceptanceIT.Driver#open/#receiveLinkManifest/#committedReceiveLinkCarrier/#agentConfig
- Repository evidence: EVD-001/002/006; current Driver keeps direct S12a to PS.
- Dependencies and consumers: File 5 and existing private Driver guards.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Existing Driver with new manifest selector and trace predicate reuse.
- Input/output and state mapping: Replay property only for RECEIVE_LINK_REPLAY. Existing RECEIVE_LINK still uses direct PS endpoint. New replay uses existing resultRelay, and both trace suites read exact successful Outbox carrier.
- Error and edge behavior: Missing manifest is an operator skip. Wrong suite, store or event fails. Ordinary property, modes and routing remain.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
var property = exactPropertyFor(suite);
require(manifest.suite() == suite);
configureExistingDirectPsOnlyForReceiveLinkOtherwiseExistingResultRelay();
readExactPersistedCarrierAfterAppliedOwnedEventAndSucceededOutbox();
```

- Verification contribution: G1/P1/E1.
- After this file: Private suite and direct S12a remain independently selectable.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`

- Purpose: Reuse exact existing recovery chronology.
- Symbols: WorkerRecoveryIT#earlyResultAndAgentRestart
- Repository evidence: EVD-002; existing private lost 202 and persisted replay choreography.
- Dependencies and consumers: File 8 in same package; existing WorkerRecoveryIT method unchanged.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Narrow package visibility for one static test helper.
- Input/output and state mapping: Same owner, same event/fingerprint, lost committed 202, distinct AS replacement, successful retry and one native advance.
- Error and edge behavior: Existing durable timing, scope and receipt failures fail exactly as before.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
armExistingFirstDropAndLinkedHold();
verifySameOwnedRetryAndReplaceExistingAgentChild();
assertNormalEndAndExactAppliedEvent();
```

- Verification contribution: P1/E1.
- After this file: Existing choreography can be called by new E case.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkReplayAcceptanceIT.java`

- Purpose: Run exact operator replay case and check two real collector links.
- Symbols: S12ReceiveLinkReplayAcceptanceIT#receives_two_exact_links_when_a_committed_response_is_lost_and_the_agent_restarts
- Repository evidence: EVD-001/002/004/006; existing distinct child identity, collector reader and same-event replay.
- Dependencies and consumers: Files 2–7 and operator private manifest/collector.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: One new operator-only test class.
- Input/output and state mapping: Select first owned Run, invoke existing replay, read exact persisted enqueue and two phase carriers, close children then validate collector within bounded read and print digest marker.
- Error and edge behavior: Missing/partial, wrong phase or event, wrong generation or mismatched collector fail. No raw carrier or owner values printed.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
try (var driver = Driver.open(Suite.RECEIVE_LINK_REPLAY)) {
    earlyResultAndAgentRestart(driver, oneOwnedRun);
    capturePersistedEnqueueAndTwoExactPhaseCarriers();
}
assertTrue(replayLinked(privateCollectorBytes, enqueue, lost, replay, startedAt, replacementStartedAt));
```

- Verification contribution: P1/E1.
- After this file: New E compiles; source gate does not execute it.

#### File 9 — `CREATE docs/egon/spec/2026-11-06-02-30-s12-receive-link-replay.md`

- Purpose: Record the exact scoped Spec for the new replay slice.
- Symbols: Spec and REQ-001–004
- Repository evidence: Master §17.2 S12 and previous direct/recovery evidence.
- Dependencies and consumers: Reviewed baseline, source gates and operator.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Map scoped requirements, tests, package, timing and pending operator evidence.
- Error and edge behavior: Missing provenance or broader acceptance claim blocks commit.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Preserve the frozen source and exact test selector.
Record the scoped requirement, proof and operator boundary.
Reject any production or migration changes and any unexecuted runtime claim.
```

- Verification contribution: S1.
- After this file: Reviewed Spec ready for the exact commit.

#### File 10 — `CREATE docs/egon/plan/2026-11-06-02-31-s12-receive-link-replay.md`

- Purpose: Record the exact scoped Plan for the new replay slice.
- Symbols: Plan and REQ-001–004
- Repository evidence: Master §17.2 S12 and previous direct/recovery evidence.
- Dependencies and consumers: Reviewed baseline, source gates and operator.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Map scoped requirements, tests, package, timing and pending operator evidence.
- Error and edge behavior: Missing provenance or broader acceptance claim blocks commit.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Preserve the frozen source and exact test selector.
Record the scoped requirement, proof and operator boundary.
Reject any production or migration changes and any unexecuted runtime claim.
```

- Verification contribution: S1.
- After this file: Reviewed Plan ready for the exact commit.

#### File 11 — `CREATE docs/egon/evidence/2026-11-06-s12-receive-link-replay.md`

- Purpose: Record the exact scoped Evidence for the new replay slice.
- Symbols: Evidence and REQ-001–004
- Repository evidence: Master §17.2 S12 and previous direct/recovery evidence.
- Dependencies and consumers: Reviewed baseline, source gates and operator.
- Why now: This file closes its ordered proof boundary within the same Step.
- Contract/signature changes: Review document only.
- Input/output and state mapping: Map scoped requirements, tests, package, timing and pending operator evidence.
- Error and edge behavior: Missing provenance or broader acceptance claim blocks commit.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Preserve the frozen source and exact test selector.
Record the scoped requirement, proof and operator boundary.
Reject any production or migration changes and any unexecuted runtime claim.
```

- Verification contribution: S1.
- After this file: Reviewed Evidence ready for the exact commit.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run §8 R1/G1/P1/S1. E1 is independent.
- Expected result: One intended false predicate RED; 19 passes; new E compiled; actual AS/PS package without fixtures and no E report.
- Failure returns to: Files 1/2 for RED and collector predicate, Files 3–8 for relay/suite/E compile, Files 9–11 for scope/evidence.
- Completion criteria: Actual finite and package evidence, exact staged paths and E1 pending.
- Rollback: Revert the focused test-only source commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceFaultRelay.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarnessTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/WorkerRecoveryIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkReplayAcceptanceIT.java`, `docs/egon/spec/2026-11-06-02-30-s12-receive-link-replay.md`, `docs/egon/plan/2026-11-06-02-31-s12-receive-link-replay.md`, `docs/egon/evidence/2026-11-06-s12-receive-link-replay.md`.
- Commit: test(process): prepare S12 receive-link replay acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | Two A files, exact finite selector | One expected true but false assertion, zero errors/skips |
| G1 | All B, exact three finite classes | Harness 11, CollectorEvidence 5, Telemetry 3, total 19 with no errors/skips |
| P1 | Actual clean permission-producer build, same finite selector | Same counts, compiled E methods, actual AS/PS package and protected declarations, no test fixtures or E report |
| S1 | Strict docs and exact paths | Eleven paths and all 28 existing production SQL unchanged |
| E1 | Separate operator-only explicit isolated run | One passing non-skipped E method plus final raw collector and artifact witness |

```sh
replay_base=c07b8eddbb8459c04c7fd26953933a02ef0b347c
replay_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
replay_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
replay_g1='ExpenseAcceptanceHarnessTest,S12ReceiveLinkCollectorEvidenceTest,S12ReceiveLinkTelemetryTest'
test "$(git rev-parse --verify HEAD)" = "$replay_base"
replay_finite() {
  JAVA_HOME="$replay_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$replay_settings" -s "$replay_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
replay_finite '-Dtest=S12ReceiveLinkCollectorEvidenceTest#replay_matches_one_exact_enqueue_two_receives_and_bounded_blocked_attempts'
replay_finite "-Dtest=$replay_g1"
JAVA_HOME="$replay_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify   ./scripts/verify-source-reactor.sh -gs "$replay_settings" -s "$replay_settings" -o clean -B -ntp   -Ppermission-producer "-Dpermission.commit=$replay_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-receive-link-replay   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$replay_g1"
ps_target=stellar-enclosure-tianji-process-server/target
new_class=top.egon.cola.platform.tianji.process.server.acceptance.S12ReceiveLinkReplayAcceptanceIT
"$replay_jdk/bin/javap" -p -v -classpath "$ps_target/test-classes" "$new_class"
test ! -e "$ps_target/surefire-reports/TEST-$new_class.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-02-30-s12-receive-link-replay.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-02-31-s12-receive-link-replay.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Run R1 on the typed A reader and test only, then G1/P1 on B. Confirm fresh exact finite XML and no BUSINESS/RECOVERY/direct/replay E selector or report. Verify prior semantics by targeted diff and finite compatibility cases. BUSINESS and RECOVERY retain ordinary property and private routes; direct RECEIVE_LINK still reaches direct PS and keeps the existing six-span predicate and strict parser; the private carrier only crosses the two new replay phases. Inspect both actual packaged AS/PS Boot archives and compare PaC declarations apart from build provenance. Require no E/relay/collector fixture class or collector YAML in either archive, no new PS SDK entry, unchanged actual application defaults, and byte equality of all 28 SQL. Record actual archive hashes and inspect all E test classes in test output without executing them.

### Separate operator-only E1

Reuse [existing S12 operator preparation](../evidence/2026-11-06-s12-receive-link-operator.md) and its exact private TLS, three disposable stores, two synthetic tenant credentials, archive and class-tree locks, exact pinned local collector executable/configuration, and fresh owner-private root. Set manifest `suite` to `RECEIVE_LINK_REPLAY`. Its Agent store remains at V1–V14; keep current PS and Worker histories. Select exact first synthetic owner fixture Run through the test. Use the result relay's separately bound HTTPS loopback port and same locked PS upstream, and use the existing S12 collector configuration/file on a fresh run. Freeze actual executable and test class-tree hashes after building, before selecting E1. Keep the exact private manifest outside the checkout and credentials out of published artifacts.

```sh
: "${TIANJI_S12_REPLAY_PRIVATE_MANIFEST:?Provide an authorized fresh private replay manifest}"
replay_finite '-Dtest=S12ReceiveLinkReplayAcceptanceIT#receives_two_exact_links_when_a_committed_response_is_lost_and_the_agent_restarts'   "-Dtianji.s12.receive-link-replay.manifest=$TIANJI_S12_REPLAY_PRIVATE_MANIFEST"
```

Retain exactly one actual passing unskipped E method, raw Surefire XML, private manifest provenance and final collector JSONL. Confirm existing hard evidence of one scoped applied result despite retry. Require marker `S12_RECEIVE_LINK_REPLAY_SUBSET` with exact AS/PS archive/test-tree digests and `receives=2`. Stop the collector after test and compare final raw file SHA-256 with the marker. Extra late collector output that changes the digest invalidates evidence. Missing or skipped report, provider/collector failure, mismatched artifacts or graph cannot satisfy E1.

## 9. Migration, Compatibility, Rollout, and Rollback

All production code, PaC, configuration and migrations stay unchanged. Existing three acceptance modes retain their scopes. A separate opt-in replay fixture uses the existing authorized provisioning and local paths. Rollback is a revert of this focused test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 5–8 | P1/E1 |
| REQ-002 | Step 1 | 3–6 | G1/P1/E1 |
| REQ-003 | Step 1 | 1/2/8 | R1/G1/P1/E1 |
| REQ-004 | Step 1 | 1–11 | R1/G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

Actual E1 requires separately authorized fixture services and collector. Exact wire/collector match, isolated role and end-to-end evidence remain operator responsibilities. Worker trace, metrics, logs and full S12 need separate proof.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing PS acceptance fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing recovery flow, relay, SDK and collector reader | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | JDK, JUnit, existing HTTPS test fixture and pinned SDK already present | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact semantic test method | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Exact private suite, validated header, phase/owner checks and collector evidence | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing DTOs only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing mapped ledger values | None |
| MC-LOG-001 | Not applicable | N/A | §8/15 | Existing JUnit reporting | None |
| MC-BEAN-001 | Not applicable | N/A | §6/8 | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, Spring tests and JUnit | None |
| MC-JSON-001 | Applicable | PASS | §7.3/10 | Reuse closed bounded collector parser | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | Existing process start and span UTC instants, bounded elapsed waits | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing test opt-in only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct fixture reuse | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Eleven exact paths | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite sources and independent E evidence | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Source gates independent of later E1 | None |

### 12.6 Final verdict

PASS — Ready for user review
