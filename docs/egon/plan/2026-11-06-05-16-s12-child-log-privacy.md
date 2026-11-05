# T4-02 S12 子进程日志字面值隐私实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-05-16-s12-child-log-privacy.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:16 Asia/Shanghai |
| Updated | 2026-11-06 05:16 Asia/Shanghai |
| Owner | Tianji acceptance fixtures |
| Repository | tianji-service |
| Scope | Eight test Java files and three review documents, one focused commit |
| Source Requirement | Master §17.2 S12 and §19 T4-02/T4-05 |
| Baseline Revision | Service 57d695e5b16e69293c3c07d1082224da3db4dd23 |
| Implements Spec | [S12 child log privacy Spec](../spec/2026-11-06-05-15-s12-child-log-privacy.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:15 Asia/Shanghai SHA-256 50b86a309af0607d9cd8665a34f6da2e4ade82e41c5f7f1d3fb6280efced6ee1 |
| Effective Specs | [S12 child log privacy Spec](../spec/2026-11-06-05-15-s12-child-log-privacy.md) |
| Depends On Plans | [S12 metrics fixture](../plan/2026-11-06-04-39-s12-metrics-subset.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在独立的 S12 指标操作者入口中，复用两个正常租户和现有 Fake 模型，观察实际子进程日志的完整排空、固定提示到达与所选真实夹具凭据的字面值隐私。

## 2. Target Spec and Effective Design

[S12 child log privacy Spec](../spec/2026-11-06-05-15-s12-child-log-privacy.md) 的 REQ-001 至 REQ-004 规定独立启动入口、有界完整捕获、所选字面值和源码证据分离。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/5/7 | 独立操作者入口，既有 suites 保持原输出行为 | Harness, Driver and IT |
| REQ-002 | §4/7/10 | 两角色完整排空、1 MiB 上限和失败关闭 | Privacy scanner and initializer |
| REQ-003 | §4/7/15 | 实际 Fake 两次见证，四类私有输入字面值检查 | Fake and scanner |
| REQ-004 | §8/14/16 | R1/G1/P1/S1 与独立 E1 | Review |

## 4. Implementation Strategy and Dependency Order

冻结真实 baseline 和 Spec SHA。先构建类型正确的 R1，观察去掉唯一凭据匹配后的预期失败。按 Step 1 应用最终 11 个路径，执行 G1/P1/S1 后一次提交。E1 独立保留。

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture | Existing expense test-owned children and metric proofs | §7/8 | Preserve owners, add test-only observation | S1 |
| Validation | Existing prompt and selected private settings | §5/7/14 | Bound, match actual selected input and reject missing liveness | R1/G1/P1/E1 |
| Lifecycle and time | Existing child termination and separate timed drain | §6.3/7 | Continue draining after overflow, require EOF then clear | G1/P1/E1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Actual child output | Existing ProcessBuilder and Driver close | EVD-001/004 | Both currently discard | Reuse and branch for dedicated mode | Bounded private buffer only | Step 1 MC-REUSE-001 |
| Prompt liveness | Existing validated Fake path | EVD-002 | Actual fixed observed prefix exists | Emit hash marker after exact validation | JDK SHA-256 | Step 1 MC-VALID-001 |
| Credentials | Existing actual-use private sources | EVD-003 | Values available at use | Register only selected four classes | No dependency | Step 1 MC-LOG-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §5/6/7 | EVD-001–004 | Files 1–11 | 现有测试包与语义技术类名 | G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §5/6/7 | EVD-001–004 | Files 1–11 | 闭合角色、固定字面标识、实际值来源和失败门禁 | G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §5/6/7 | EVD-001–004 | Files 1–11 | 仅私有技术状态和已有 immutable fixture DTO | G1/P1/S1 | Step 1 | N/A |
| Rule 4 | §5/6/7 | EVD-001–004 | Files 1–11 | 现有测试 initializer 与固定脱敏诊断 | G1/P1/S1 | Step 1 | PASS |
| Rule 5 | §5/6/7 | EVD-001–004 | Files 1–11 | 沿用 JDK、Spring、JUnit 与既有夹具 | G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §5/6/7 | EVD-001–004 | Files 1–11 | 现有 Fake 闭合输入、指标 JSON 字段与解析语义保留 | G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §5/6/7 | EVD-001–004 | Files 1–11 | 仅在独立选择器下注入测试子进程环境标识 | G1/P1/S1 | Step 1 | PASS |
| Rule 9 | §5/6/7 | EVD-001–004 | Files 1–11 | 直接复用现有宿主生命周期并附加私有观察器 | G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §5/6/7 | EVD-001–004 | Files 1–11 | Duration 有界读线程等待和原指标时间窗 | G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §5/6/7 | EVD-001–004 | Files 1–11 | AS/PS 测试责任和既有 CQE 生产架构保持 | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Child log join | Duration | java.time.Duration and Thread.join(Duration) | Not persisted | Internal | No zone | No display | At most three seconds per reader after existing child stop | Relative elapsed time | Private mode only | Step 1 TEST-001/004 |
| TIME-002 | Existing metrics collection | Duration | Duration and nanoTime | Not persisted | Internal | No zone | No display | Existing five-second read bound | Existing UTC and elapsed clocks | Reuse | Step 1 TEST-003/004 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacy.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12MetricsInitializer.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyAcceptanceIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-15-s12-child-log-privacy.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-16-s12-child-log-privacy.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s12-child-log-privacy.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

JDK 25、离线 source reactor、已有 permission-producer 和当前 S12 指标制品。生产依赖、权限、迁移、配置及部署不在本步。操作者实际执行须独立授权并使用私有两租户、三隔离库及固定 Collector。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare bounded literal child log privacy acceptance

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing S12_METRICS fixture, AS Fake, test initializer and owned-process shutdown.
- Baseline state: Existing metrics proof drops both child log streams.
- Observable outcome: One intended R1 failure, 25 finite passes in G1/P1 and exact operator class compiled.
- End state: Eight test Java paths and three review documents in one focused commit.
- Test-first gate: Required. Compile the typed candidate with only the final raw credential matching predicate removed. Select the exact R1 method. Its captured registered credential case must fail with Expected java.lang.IllegalStateException to be thrown, but nothing was thrown. Prompt rejection and compile remain intact. Any other error, skip, or failure rejects R1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyTest.java`

- Purpose: 用有限内存输入验证两角色、完整排空、固定提示与私有凭据拒绝。
- Symbols: `S12ChildLogPrivacyTest` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireBothOwnedRolesAndAllCredentialKinds();
assertThrows(IllegalStateException.class, () -> verifyCapturedSyntheticCredentialAcrossReads());
requireOverflowStillReadsThroughEofWithoutVerdict();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacy.java`

- Purpose: 持有两个有界私有缓冲，溢出后排空并拒绝，闭合扫描后清零。
- Symbols: `S12ChildLogPrivacy` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
attachOneReaderPerRole();
while (readChunk() != EOF) appendBoundedOrDiscardAfterOverflow();
requireJoinedEofAndOrderedMarkers();
requireLiteralPromptAndCredentialAbsence();
clearPrivateBuffersOnClose();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12MetricsInitializer.java`

- Purpose: 只为隐私子模式在同一 Spring context 的两次生命周期事件发固定标识。
- Symbols: `S12MetricsInitializer` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
if (privacyEnvironmentEnabled()) registerContextOwnedListener();
onRefreshed(System.out, readyMarker());
onClosed(System.err, closingMarker());
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: 在现有 Fake 真正消费默认提示且解析金额 100 后记录无正文的摘要标识。
- Symbols: `ExpenseAgentAcceptanceMain` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireExactMetricsModeForPrivacy();
requireExistingClosedFakePromptAndAmount100();
String observedPrefix = text.substring(0, MODEL_INPUT.length());
emitObservedPrefixSha256Marker(observedPrefix);
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: 只在显式捕获时合并两路输出，并继续持有和清理子进程。
- Symbols: `ExpenseAcceptanceHarness` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
if (logs == null) discardBothStreams();
else mergeStreamsAndEnablePrivacyMarker();
var ownedChild = startExistingLockedChild();
attachOrStop(ownedChild);
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: 复用既有 Driver，在实际使用时记录 HTTP bearer、运行库密码和 TLS 口令。
- Symbols: `BusinessProcessAgentAcceptanceIT` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requirePrivacyOnlyForMetricsSuite();
selectIndependentPrivateManifest();
registerSelectedActualCredentialsBeforeUse();
startExistingLockedChildren();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java`

- Purpose: 提供测试包内的原指标流程复用点，只在两子进程都关闭后验证可选隐私证据。
- Symbols: `S12MetricsAcceptanceIT` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
runExistingTwoTenantMetricsPaths();
closeOwnedDriver();
if (logs != null) logs.requireSafe();
requireExistingCollectorMetricsEvidence();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyAcceptanceIT.java`

- Purpose: 专用操作者入口持有并始终释放隐私扫描器。
- Symbols: `S12ChildLogPrivacyAcceptanceIT` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
try (var logs = new S12ChildLogPrivacy()) {
    S12MetricsAcceptanceIT.verifyMetrics(logs);
    requireOnlyTheFixedVerdictAfterMetricsAndPrivacyGates();
}
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 9 — `CREATE docs/egon/spec/2026-11-06-05-15-s12-child-log-privacy.md`

- Purpose: 记录固定提示、所选凭据、完整排空和证据边界。
- Symbols: `2026-11-06-05-15-s12-child-log-privacy` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
recordExactLiteralPromptAndSelectedCredentialCategories();
recordTheBoundedCompletePipeDrainAndMissingEvidenceFailures();
requireSeparateOperatorProofForTwoRealFixturePaths();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 10 — `CREATE docs/egon/plan/2026-11-06-05-16-s12-child-log-privacy.md`

- Purpose: 锁定八个测试 Java 路径、文档、顺序和验证选择器。
- Symbols: `2026-11-06-05-16-s12-child-log-privacy` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
pinExactBaselineAndTheFinalSpecSha256();
recordCredentialPredicateOnlyRedAndTwentyFiveFiniteCases();
requireBothActualArchivesExcludeTestFixtures();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

#### File 11 — `CREATE docs/egon/evidence/2026-11-06-s12-child-log-privacy.md`

- Purpose: 记录观察到的源码、有限和制品证据，操作者验收单列。
- Symbols: `2026-11-06-s12-child-log-privacy` and the symbols shown below.
- Repository evidence: Spec EVD-001–004 and §7.
- Dependencies and consumers: Exact test roles and existing metrics fixture, validated in order by §8.
- Why now: REQ-001–004 require this selected path for the isolated proof.
- Contract/signature changes: Test-only capture or review scope. Existing production contracts stay separate.
- Input/output and state mapping: Owned inputs and fixed bounded evidence as shown below.
- Error and edge behavior: The source or acceptance gate fails closed if exact scope or complete proof is missing.
- Standards impact: MC-VALID-001, MC-LOG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```text
recordObservedSourceGatesWithoutChildOutput();
compareActualFakeModelInputAndScannerPrefixByClassOnlyInspection();
keepActualPostgresIamHttpsAndCollectorAcceptancePending();
```

- Verification contribution: R1, G1, P1 and S1 in §8. E1 remains operator-only.
- After this file: Verify scope and keep the next path blocked until prerequisites are met.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: Exact R1/G1/P1/S1 in §8, with separate E1.
- Expected result: One intended R1 assertion, 25 finite cases in G1/P1, and actual operator bytecode compiled without E XML.
- Failure returns to: Only the failing gate, without staging, or an externally owned E prerequisite.
- Completion criteria: Exact 11 paths, source gates green, both packaged runtime scopes and 28 immutable SQL checked.
- Rollback: Revert the single test-only commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacy.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12MetricsInitializer.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyAcceptanceIT.java`, `docs/egon/spec/2026-11-06-05-15-s12-child-log-privacy.md`, `docs/egon/plan/2026-11-06-05-16-s12-child-log-privacy.md`, `docs/egon/evidence/2026-11-06-s12-child-log-privacy.md`.
- Commit: test(observability): verify literal S12 child log privacy.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Required evidence |
| --- | --- | --- |
| R1 | S12ChildLogPrivacyTest#rejects_the_exact_prompt_and_an_actual_registered_credential_across_chunk_boundaries | One typed assertion at registered credential input, no other error or skip. |
| G1 | S12ChildLogPrivacyTest,S12MetricsTelemetryTest,S12MetricsCollectorEvidenceTest,ExpenseAcceptanceHarnessTest | 6 scanner, 5 registry, 3 raw metric and 11 harness tests, 25 finite cases. |
| P1 | Clean permission-producer and class-only inspection | Same 25 cases. Compiled exact operator and marker symbols, no IT report or fixture in executables. |
| S1 | Strict docs, exact file scope, artifact and migration audit | 11 paths, 28 byte-identical SQL, equivalent declarations and runtime YAML. |
| E1 | S12ChildLogPrivacyAcceptanceIT#collects_the_same_metrics_with_private_child_log_evidence | One actual passing E report, final private metrics digest and fixed log verdict. |

```sh
root=/Users/mario/SelfProject/Stellar-Enclosure/tianji-service
runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
base=57d695e5b16e69293c3c07d1082224da3db4dd23
red='S12ChildLogPrivacyTest#rejects_the_exact_prompt_and_an_actual_registered_credential_across_chunk_boundaries'
core='S12ChildLogPrivacyTest,S12MetricsTelemetryTest,S12MetricsCollectorEvidenceTest,ExpenseAcceptanceHarnessTest'
cd "$root"
test "$(git rev-parse HEAD)" = "$base"
# R1 uses the compiled candidate with only the final literal credential predicate removed.
"$runner" "$red"
# Restore the exact final verifier before G1.
"$runner" "$core"
STELLAR_VERIFY_GOAL=verify "$runner" "$core" clean -Ppermission-producer   "-Dpermission.commit=$base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-child-log-privacy
"$jdk/bin/javap" -p -c -classpath stellar-enclosure-tianji-agent-server/target/test-classes   top.egon.cola.platform.tianji.agent.server.acceptance.ExpenseAgentAcceptanceMain
"$jdk/bin/javap" -p -c -classpath stellar-enclosure-tianji-process-server/target/test-classes   top.egon.cola.platform.tianji.process.server.acceptance.S12ChildLogPrivacyAcceptanceIT   top.egon.cola.platform.tianji.acceptance.S12MetricsInitializer
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-15-s12-child-log-privacy.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-16-s12-child-log-privacy.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

核对真实 AS/PS archives 内的语义 PaC、应用 YAML 和验收类排除。检查无 S12ChildLogPrivacyAcceptanceIT XML，检验实际 AS Fake 的 MODEL_INPUT 与私有 verifier 固定前缀字节一致。源码 gate 只执行无真实运行服务的测试。

### Separate operator-only E1

复用 [S12 指标操作者前置条件](../plan/2026-11-06-04-39-s12-metrics-subset.md#separate-operator-only-e1)。使用独立新私有目录、两租户、三隔离库、原有 TLS/权限和固定的独立 Collector，继续锁定真实制品与测试类树。Suite 保持 S12_METRICS。所选私有凭据在本次模式中必须是高熵可匹配的 ASCII 字面值，长度 16 至 4096 字符。允许字母、数字、点、下划线、波浪线和连字符。

```sh
: "${TIANJI_S12_LOG_PRIVACY_PRIVATE_MANIFEST:?Provide an authorized isolated S12 metrics manifest for privacy}"
"$runner" 'S12ChildLogPrivacyAcceptanceIT#collects_the_same_metrics_with_private_child_log_evidence'   "-Dtianji.s12.metrics.log-privacy.manifest=$TIANJI_S12_LOG_PRIVACY_PRIVATE_MANIFEST"
```

只保留已执行、无跳过的测试 XML、既有私有指标 JSONL 与制品锁，并核对 S12_METRICS_SUBSET 摘要和 S12_CHILD_LOG_PRIVACY_SUBSET 固定成功标识。子进程原始日志和凭据保持在测试运行内存。实际字面值验收不扩展到其他日志、动态交换凭据或完整 S12。

## 9. Migration, Compatibility, Rollout, and Rollback

独立入口保留原 metrics-only 入口和全部既有模式。撤回单一测试提交即可回滚。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | File | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 3–8 | P1/E1 |
| REQ-002 | Step 1 | 1–3, 5, 7–8 | G1/P1/E1 |
| REQ-003 | Step 1 | 1–4, 6–8 | R1/G1/P1/E1 |
| REQ-004 | Step 1 | 1–11 | R1/G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

真实子进程、隔离 IAM/数据库、Collector、实际 E 及完整 S12 仍需操作者证据。源码门槛可在离线环境完成。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 or Plan §8 | 沿用 AS/PS 测试类树、生产制品和独立 S12 指标 Driver | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 or Plan §8 | 沿用 ProcessBuilder、现有关闭顺序、指标流程及私有凭据读取 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 or Plan §8 | 只使用现有 JDK 25、JUnit、Spring 与已有 Collector | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 or Plan §8 | 测试类和环境标识按 S12 私有模式命名 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 or Plan §8 | 要求完整排空、闭合角色、精确标识和四类实际凭据 | None |
| MC-MODEL-001 | Not applicable | N/A | §7/8/14 or Plan §8 | 没有新增业务实体或公开 DTO | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 or Plan §8 | 没有业务映射或 DTO 转换 | None |
| MC-LOG-001 | Applicable | PASS | §7/8/14 or Plan §8 | 子进程只输出固定标识或既有指标摘要，扫描器只给固定失败诊断 | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 or Plan §8 | 复用现有测试 initializer，只增加同源生命周期监听 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 or Plan §8 | 复用 JDK SHA-256、数组、受限线程和持续时间 | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 or Plan §8 | Fake 保持现有闭合输入解析，指标原始 JSON 校验保持独立 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 or Plan §8 | 每个读线程独立三秒相对等待，原 Collector 和关闭预算保持 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/14 or Plan §8 | 独立测试属性和精确子进程环境标识，生产配置排除在范围外 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 or Plan §8 | 将私有观察器接到已有宿主生命周期，避免第二套 Driver | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 or Plan §8 | 八个测试 Java 路径和三个文档，28 个现有迁移逐字节比较 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 or Plan §8 | 精确凭据 RED、25 项有限测试、制品和独立操作者门槛 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 or Plan §8 | 源码门禁与实际双租户、数据库、Collector 运行分开 | None |

### 12.6 Final verdict

PASS — Ready for user review
