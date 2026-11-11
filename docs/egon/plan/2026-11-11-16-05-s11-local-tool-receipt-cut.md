# T4-03 S11 本地 START 成功收据提交后原执行进程退出计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 16:05 Asia/Shanghai |
| Updated | 2026-11-11 16:05 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Three AR test sources and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | ec9dc02f8ab00ec788028581e8ab61ae2b5df47b |
| Implements Spec | [S11 local Tool receipt cut](../spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 16:04 Asia/Shanghai SHA-256 2628a35b62997f7f02e19a4f2a40153247820a55a8f449b2c33dd7083c47a1af |
| Effective Specs | [S11 local Tool receipt cut](../spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

沿用原 V13 固定 START recorder，受控 transport 在本地收据前观察 DISPATCHING 和空收据。真实本地 invocation SUCCEEDED 和 action APPLIED 在同一事务提交后，于现有 ToolCommittedFactObserver 回调强制回收原 JVM。替代观察者使旧 Run 租约过期并通过产品 reconciler 单次暂停，原收据不变，未完成预算标为 UNKNOWN，旧租约和新 claim 均不能启动再次执行。

## 2. Target Spec and Effective Design

[S11 local Tool receipt cut](../spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md) §§4/7/11/14 控制精确 post-commit 切点、强制退出和恢复证据。

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/11 | 真实 V13 收据提交后预算仍 RESERVED，原 JVM 在回调内被强制回收 |
| REQ-002 | §7/9/10 | 原收据不变，单次暂停及预算 UNKNOWN，恢复在旧租约和新 claim 拒绝 |
| REQ-003 | §8/14/16 | 私有 marker 反证、37 有限、独立运行与六路径 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

锁定现有测试及精确 parent。执行 R1、G1/G2 和严格 S1。I1 独立执行。

### 4.2 Test-first strategy

仅删除独立副本的 invocation UUID canonical guard。非标准 UUID 应被有限用例检出。原候选验证相同断言。

### 4.3 Sequential and parallel boundaries

依序 G0/R1/G1/G2/S1。I1 需一次性真实环境。

### 4.4 Commit boundaries

一个测试提交包含三份测试源和三份文档。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| 精确 post-commit 切点 | Required §7/14 | EVD-001/002/005 | 原 ToolCommittedFactObserver | Reuse |
| 一次性 Run 处置 | Required §7/14 | EVD-003/004 | 原 reconcile 和只读证据 | Reuse |
| 私有进程 | Required §5/14 | EVD-005 | 原 AR fixture fork 惯例 | Extend test only |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| 实际 recorder 原进程 | REQ-001/002 | G2/I1 | 1 | 现有 V13 夹具 |
| 私有协议 | REQ-003 | R1/G1 | 2–3 | 独立 JVM |
| 文档与证据 | REQ-003 | S1 | 4–6 | 精确范围和有限报告 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | EVD-001–005 | AR fixture owner |
| Persistence | EVD-001–003 | 原产品 V13 和原 V2 |
| Production scope | EVD-001–004 | 原生产、迁移与 POM 保持 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 真实已提交成功 | ToolInvocationRecorder and ToolInvocationSql | EVD-001/002 | 原 hook 和写入 | Reuse | 受控 fixture 调用 | G2/I1 |
| 安全退出和替代观察 | ToolExecutionJournalPostgresTest and AgentCheckpointCapturePostgresTest | EVD-003–005 | 原夹具、数据库与 fork 惯例 | Extend | 私有 marker 和 process bound | G2/I1 |
| 精确输入安全 | JDK IO and UUID | EVD-005 | UUID 必须严格规范 | Extend | 两份测试文件 | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–3 | 三份测试源码及精确选择器。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–3 | 专用数据库、完整 owner 和原执行证据。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–3 | 复用原 Run DTO、V13 outcome 和私有 witness。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–3 | 生产合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–3 | JDK、JUnit、Spring JDBC、Flyway。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–3 | 生产合同保持。 | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–3 | 独立测试和子进程开关。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–3 | 原 committed fact observer 与精确租约 CAS。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–3 | 单调限时和 PostgreSQL 租约时钟。 | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–3 | 只复用真实 V1–V13 schema。 | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | RECEIPT_CUT_SECONDS and child reap | Duration | monotonic nanoseconds and java.time.Duration | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | Sixteen seconds per operation and bounded JDBC | System.nanoTime | Existing child fixture approach | Step 1 TEST-003 |
| TIME-002 | Original Run lease expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Expire exact active lease while original total deadline is live | PostgreSQL clock_timestamp with microseconds | Existing lease and reconcile storage | Step 1 TEST-003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoff.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoffTest.java` |
| CREATE | `docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md` |
| CREATE | `docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-11-s11-local-tool-receipt-cut.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

精确 Service 基线 ec9dc02f8ab00ec788028581e8ab61ae2b5df47b。JDK 25，既有离线依赖，原 31 份 SQL 字节保持。

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prove local V13 receipt survives original recorder exit

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: 原 Tool recorder、PostgreSQL V13 和原 Run reconciliation。
- Baseline state: 现有真实 Tool journal PG、post-commit 观察及原同 JVM 结算失败探针。
- Observable outcome: 原 success receipt 在原 JVM 退出后完整，预算 UNKNOWN，Run 仅暂停一次。
- End state: 三测试和三文档，真实 I1 独立。
- Test-first gate: Required. Canonical invocation UUID 反证。
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`

- Purpose: 原 recorder 回调处实施精确 fault.
- Symbols: ToolExecutionJournalPostgresTest#preserves_a_committed_local_start_receipt_after_forced_exit_before_accounting.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 原 recorder 回调处实施精确 fault。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
var receipt = awaitExactPrivateCut(child, marker);
requireCommittedInvocationAndIntent(receipt); requireReservedAccountingAndNoOutput();
reapOriginalChildAndReconcileExactLease();
assertRetainedReceiptAndUnknownBudgetWhileOwnerSeesStalled();
```

- Verification contribution: G2/I1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoff.java`

- Purpose: 定义私有启动和精确 receipt marker.
- Symbols: ToolReceiptCutForkHandoff.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 定义私有启动和精确 receipt marker。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
requireDisposableLoopbackPrimary(url, canonicalRole, pinnedVectorVersion);
decodeOnlyBoundedExactVersionedFrame();
return Cut.parseCanonicalRunInvocationAndIntent(privateMarker);
```

- Verification contribution: R1/G1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

#### File 3 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoffTest.java`

- Purpose: 验证专用数据库和规范 marker.
- Symbols: ToolReceiptCutForkHandoffTest.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 验证专用数据库和规范 marker。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
assertRoundTripExactFrameAndThreeCanonicalUuids();
assertThrows(IllegalArgumentException.class, () -> decode(nonCanonicalInvocation));
assertThrows(IllegalArgumentException.class, () -> receive(trailingOrOversizedFrame));
```

- Verification contribution: R1/G1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

#### File 4 — `CREATE docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md`

- Purpose: 记录两个独立故障窗口.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 记录两个独立故障窗口。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
trace REQ-001 to product post-commit observer before original V2 accounting;
trace REQ-002 to retained exact rows, original lease fencing and owner recovery evidence;
trace REQ-003 to private guarded fixture and exact source scope.
```

- Verification contribution: S1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

#### File 5 — `CREATE docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md`

- Purpose: 固定可审查验证门.
- Symbols: G0/R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 固定可审查验证门。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
lock baseline and three test sources with exact review paths;
run isolated guard mutation and 37 finite Java 25 tests with all PG cases skipped;
validate exact strict review links and preserve all preexisting migration bytes.
```

- Verification contribution: S1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

#### File 6 — `CREATE docs/egon/evidence/2026-11-11-s11-local-tool-receipt-cut.md`

- Purpose: 记录有限与 operator 证据.
- Symbols: R1/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: 现有 AR V13 与持久恢复。
- Why now: 记录有限与 operator 证据。
- Contract/signature changes: 仅测试和评审。
- Input/output and state mapping: 本地 V13 receipt 保留，原 Run 暂停，V2 预算 UNKNOWN。
- Error and edge behavior: 精确证据、租约或清理不符使操作者用例失败。
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
record the expected typed guard failure and 37 finite successes;
record the exact compiled opt-in selector and three default PostgreSQL skips;
leave I1 process and physical database evidence pending independent execution.
```

- Verification contribution: S1.
- After this file: 保持 REQ-001–003 与独立 operator 证据对应。

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/R1/G1/G2/S1，I1 独立。
- Expected result: 一项反证、37 有限结果、3 个默认 PG 跳过和六条准确路径。
- Failure returns to: Files 1–3 source，Files 4–6 review。
- Completion criteria: 原迁移 31 份、精确补丁、有限及独立 I1 边界。
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoff.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolReceiptCutForkHandoffTest.java`, `docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md`, `docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md`, `docs/egon/evidence/2026-11-11-s11-local-tool-receipt-cut.md`.
- Commit: test(agent): prepare committed local tool receipt crash cut.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | 现有 ToolExecutionJournalPostgresTest 精确，两个私有源码尚未存在 |
| R1 | Isolated Java 25 mutation | 非规范 invocation UUID 反证失败 |
| G1 | Owning offline source reactor | 2 + 30 + 5 = 37 项有限成功 |
| G2 | Java 25 javap | 独立 selector 与 private child，3 PG 默认跳过 |
| S1 | Strict docs and scoped diff | 六路径和 31 份 SQL 无改动 |
| I1 | Operator-only isolated PostgreSQL | 实际 V13 receipt、回收、单次暂停和 owner 证据 |

```sh
tool_base=ec9dc02f8ab00ec788028581e8ab61ae2b5df47b
tool_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/s11-local-tool-receipt-cut/step165
tool_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse --verify HEAD)" = "$tool_base"
"$tool_scratch/run-scratch.sh"
"$tool_runner" 'ToolReceiptCutForkHandoffTest,ToolInvocationRecorderTest,AgentRecoveryDispositionEvidenceTest' clean
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-16-04-s11-local-tool-receipt-cut.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-16-05-s11-local-tool-receipt-cut.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

### Separate operator-only I1

操作者使用获授权的一次性 loopback primary，空 tianji_agent schema，预装 public.vector 精确版本，并为父 JVM 和私有子 JVM 提供本地 setup role。数据库名称必须是 tianji_it_tool_cut_ 加一至二十四个小写字母、数字或下划线，URL 无凭据或查询参数。

```sh
: "${TIANJI_TOOL_CUT_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_TOOL_CUT_VECTOR_VERSION:?Provide the pinned preinstalled vector patch}"
tool_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
tool_java=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
JAVA_HOME="$tool_java" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$tool_settings" -s "$tool_settings" -o -B -ntp \
  -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false \
  "-Dtest=ToolExecutionJournalPostgresTest#preserves_a_committed_local_start_receipt_after_forced_exit_before_accounting" \
  -Dtianji.tool.journal.postgres.start-receipt-cut=true "-Dtianji.tool.journal.postgres.test.url=$TIANJI_TOOL_CUT_PRIVATE_PG_URL" \
  "-Dtianji.tool.journal.postgres.vector.version=$TIANJI_TOOL_CUT_VECTOR_VERSION"
```

保留一项实际执行的 owning XML，结束后单独处置一次性库。真实 PS HTTPS/IAM 仍为独立 E。

## 9. Migration, Compatibility, Rollout, and Rollback

回滚为撤销测试提交。既有生产合同、数据库迁移和配置保持。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–6 | R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

实际隔离 PostgreSQL 和原进程退出尚待独立操作者 I1。真实 PS START、IAM 与完整 S11 不由受控 recorder port 替代。

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | 沿用原 AR 固定 START recorder、工具账本和 reconciler。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | 复用现有 ToolCommittedFactObserver、V13 fixture 及 owner 处置。 | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | 现有 JDK 25、JUnit、Spring JDBC、Flyway 和 PostgreSQL。 | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | 原 Tool fixture 包内新增精确测试 selector。 | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | 专用 loopback、完整 owner/Run/invocation/intent/原执行 SERVICE。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/14 | 原 V13 outcome、V2 budget 和私有不可变证人。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | JDK 安全子进程、受限文件与原持久测试能力。 | None |
| MC-JSON-001 | Not applicable | N/A | §7/14 | 既有产品和持久编码保持。 | None |
| MC-TIME-001 | Applicable | PASS | §7/14 | 单调限时，租约时间由 PostgreSQL 统一判定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | 专用操作属性和私有子进程属性默认关闭。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | 原 post-commit 观察接口和测试进程故障注入。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | 精确三份测试和三份文档，生产及迁移保持。 | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | R1 有限反证、37 有限项、独立编译和真实 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | 真实 PostgreSQL 和原进程退出由独立操作者验收。 | None |

### 12.6 Final verdict

PASS — Ready for user review
