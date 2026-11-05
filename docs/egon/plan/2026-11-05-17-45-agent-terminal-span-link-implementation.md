# T4-02 AS 终态 Outbox Span Link 实施计划候选

| Field | Value |
| --- | --- |
| Document | `2026-11-05-17-45-agent-terminal-span-link-implementation.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 17:45 Asia/Shanghai` |
| Updated | `2026-11-05 17:45 Asia/Shanghai` |
| Owner | `天机决策负责人` |
| Repository | `tianji-service` |
| Scope | `AS enqueue→existing Outbox→AS verified delivery，原 headers/幂等、默认关闭` |
| Source Requirement | `主计划 §19 T4-02 / §17 S12 子集，exact reviewed AS-only Spec` |
| Baseline Revision | `tianji-service 74369e760829d60e41d658b780681a54efcb22a0；Components 87af09fea9a9d58e7127b009d76f60438c79e569；clean` |
| Implements Spec | [AS 终态异步 Link](../spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 17:35 Asia/Shanghai；exact SHA 70c434a98750d929306240cdf06d1b5f5fc4ea433952e26f8eec12b0414f4d3e；DEC-005 scoped source` |
| Effective Specs | [AS 终态异步 Link](../spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | [主计划](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

Root approved only this exact default-off AS source slice under the user master Plan. The terminal-channel diagnostic carrier, original payload/headers/fingerprint, dependency and ownership boundaries are locked. Actual compatibility and deployment approval remain independent.

两个独立提交。先固定 AS 技术 helper、严格 carrier 和真实 SDK 本地测试，再接原 publisher/handler、现有 named factories 与 default-off 配置。Tianji 专用 trace_id carrier 保持原 Outbox headers、幂等、SQL 和 wire。真实部署的 listener 兼容审计及 isolated exporter 证据是单独启用前置。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [AS 终态异步 Link](../spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md)
- Status: Draft
- Revision: 2026-11-05 17:35 Asia/Shanghai，§7–16。
- Approval evidence: Root 指定 AS-only、channel-local versioned trace_id、explicit false、无 POM/schema/wire 变化。本 Draft 仍需锁定有效 Spec/Plan 与 source GO。部署启用独立于源交付。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [AS 终态异步 Link](../spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md) | Draft 17:35 | §4/7–16 | exact AS source |
| Context | [T4-02 首切片](review-mirror/tianji-service/docs/egon/spec/2026-11-05-16-08-agent-model-usage-ledger.md) | 既有首切片 | scope/compatibility | 首切片源码 6b806e4，不改其 usage |

### 2.3 Superseded or excluded content

完整 S12、PS/CL/W 传播、PS 应用、持久 queue origin、跨源诊断与 metrics/SLO 总体保持后续范围。本 Plan 不给出真实 exporter、listener 或整体 S12 通过结论。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/7/9 | actual producer context 首写 | sampled 有效 carrier，重复仍旧值 | AS publisher + local helper |
| REQ-002 | §4/7/14 | verified delivery 真 Span Link | 两次 attempt 的 SpanData root 与 exact Link IDs | AS handler + SDK finite |
| REQ-003 | §4/7/16 | 原指纹/鉴权/事务 | single header unchanged、owner gate first、bad carrier 仍交付 | 两现有入口 |
| REQ-004 | §4/7/15 | default off、低基数 | off no SDK、仅固定 4 label，安全诊断 | AS factories/YAML |
| REQ-005 | §4/14/18 | 分开证据 | source finite 与用户启用分开 | 单独 G/I/E |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先独立验证纯观测 helper 与实际可用 SDK。随后同步改两个现有调用点、各自 factories、有限回归和 sole default-off key，形成一条完整技术链。当前不修改 generic Outbox。

### 4.2 Test-first strategy

Step 1 反射缺失断言 RED → A compile → typed positive RED → helper GREEN。Step 2 先有限 publisher/handler 新行为 RED（必要时声明 A overload 和固定 disabled delegate），然后 wiring GREEN。仅真实有限断言失败计 RED。启动、监听、DB、网络、导出服务均在源门禁之外。

### 4.3 Sequential and parallel boundaries

两步严格有序。Step 1 helper/test 不接入任何生产 Bean。Step 2 先封闭现有两个入口，再同时接 factories 和 default。环境中即使部署新制品也保持 off；实际 DEAD-listener 审查及 isolated exporter 证明后，才由 operator 独立启用。

### 4.4 Commit boundaries

每步一个 focused commit。Plan 文档、运行证明或新 evidence 路径不隐式进入两步 source scope。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| actual Link | §7.3 必须 | AS SDK present，MDC 不足 | reuse pinned OTel API | 两处可选 span | Implement |
| durable carrier | §7.3.1 | headers 进 fingerprint，trace_id 独立 | reuse existing diagnostic slot | 无 schema/wire，新 channel-local 语义 | Implement after source scope approved |
| telemetry availability | §7.3.3 | existing MicrometerOutboxMetrics 只有业务交付结果 | optional fixed counter | 四个 series，无重复业务指标 | Implement |
| PS/W header | deferred | actual PS archive 无 OTel；CL/W thin | 保留原 transport | 无新调用 | Keep |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Closed carrier and real Link | REQ-001/002/004/005 | absent seam / typed SDK RED | pinned AS OTel SDK, no new artifact | tested AS local helper | AS publisher/delivery | Step 1 |
| gated existing AS path | REQ-001/002/003/004/005 | new publisher/handler assertions | helper and existing transactions/auth | default-off durable/retry compatible | isolated operator activation | Step 2 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | existing AS integration/process | owner-local technical helper | no generic observer architecture | Step 1/2; MC-ARCH-001 |
| Reuse | OTel 1.62.0, Micrometer 1.17.2, current Tx | existing compiled classes | no POM | Step 1/2; MC-REUSE-001 |
| Validation/model | exact authenticated event/fingerprint and route | strict carrier separate | no state/payload mapping changes | Step 1/2; MC-VALID-001 |
| Bean/log/config | existing named factories | optional diagnostics | same Environment/key and no raw logs | Step 2; MC-BEAN-001 |
| Pattern | observer on two existing boundaries | narrow shared helper | fixed side-effect-free decode / one business attempt | Step 1/2; MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Tracing | AS OTel SDK 1.62.0 | current AS Surefire/exec artifacts | full SpanBuilder/SpanData API already available | reuse | no dependency | Step 1; MC-DEP-001 |
| SDK fixture | sdk-testing InMemorySpanExporter vs SDK trace SpanProcessor | owning classpath lacks sdk-testing; has readable SpanData/processor | finite SDK capture needs no extra artifact | test-local SpanProcessor with list; if locked classpath lacks SDK, provider-less finite only and explicit actual Link evidence gap | no dependency | Step 1; MC-TEST-001 |
| Spring transaction | current OutboxTransactionGuard | selected Tx and active synchronization | use afterCompletion | no new transaction manager | none | Step 2; MC-REUSE-001 |
| Metrics | optional AS MeterRegistry | existing MicrometerOutboxMetrics | missing carrier health | fixed enum counter | no dependency | Step 1/2; MC-UTIL-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §8/10 | AS current technical names | §7 | package-local helper/immutable facts | compile | Step 1/2 | PASS |
| Rule 2 | §7/9 | exact original event authority | §7 | strict carrier after owner proof | finite negative | Step 1/2 | PASS |
| Rule 3 | §10 | SpanContext/value | §7 | closed same-scope projection | compile/SDK tests | Step 1 | PASS |
| Rule 4 | §8/15 | current named factories | §7 Step 2 | named wiring, no private logs | source/fixtures | Step 2 | PASS |
| Rule 5 | §6 | existing JDK/SDK | §7 | no new utility dep | POM diff | Step 1/2 | PASS |
| Rule 6 | §9 | unchanged wire and headers | §7 Step 2 | preserve existing Jackson/event | finite regressions | Step 2 | PASS |
| Rule 7 | §16 | sole AS yaml | §7 Step 2 | explicit default false | resource tests | Step 2 | PASS |
| Rule 9 | §13 | narrow observer | §7 | observation never owns business state | both source gates | Step 1/2 | PASS |
| Rule 10 | §6.3 | current Tx and SDK clock | §7 | end after completion, old event/deadline unchanged | finite cases | Step 1/2 | PASS |
| Rule 11 | §6/8/11 | exact source and slot | §7 all | same profile and technical scope | old schema/hash checks | Every Step | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | producer and delivery SDK span timing | Occurred instant | Instant | Not persisted | Internal | Not applicable: SDK observation | Not applicable: no UI | producer afterCompletion and local delivery end; no cross-clock ordering | existing OTel SDK clock | old event finishedAt and delivery deadline kept | Step 1 / Step 2; TEST-002/003 and §8 |

## 5. Change File Tree

AS 根为 `stellar-enclosure-tianji-agent-server`，exact paths 见 §7。

| File | Operation | Step | Responsibility |
| --- | --- | --- | --- |
| AS AgentResultSpanObservationTest.java, AgentResultSpanObservation.java | Create | 1 | strict carrier, actual local SDK Link, counters |
| AS OutboxAgentTerminalEventPublisherTest.java, AgentResultDeliveryHandlerTest.java | Modify | 2 | existing tx/auth/wire tests + finite opt-in |
| AS OutboxAgentTerminalEventPublisher.java, AgentResultDeliveryHandler.java | Modify | 2 | two existing call paths |
| AS TianjiAgentTerminalOutboxConfiguration.java, TianjiAgentResultDeliveryConfiguration.java | Modify | 2 | existing named factories |
| AS application.yml | Modify | 2 | one key default false |
| `docs/egon/spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md`, `docs/egon/plan/2026-11-05-17-45-agent-terminal-span-link-implementation.md` | Create | 1 | exact approved design and implementation plan |
| `docs/egon/evidence/2026-11-05-agent-terminal-outbox-span-links.md` | Create | 2 | actual finite and package evidence |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

`/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`。锁定 6b806e4 或经 Root 审核的新 HEAD、primary Spec hash 与所有受保护路径。实施不把无关用户编辑或首切片变化带入。

### 6.2 Build, test, and environment prerequisites

既有 JDK25 和 `scripts/verify-source-reactor.sh`；当前 AS owning Surefire classpath 已包含 OTel API/SDK trace 1.62.0、Micrometer 1.17.2，没有 sdk-testing 依赖。直接 SDK + test-local SpanProcessor 提取 SpanData，不添加 jar。若锁定环境确实无既有 SDK trace，有限门禁退化为 provider-less scope/decoder/counter，真实 Link finite 明示未证，保持独立 E；禁止为通过门禁添加新测试依赖。

### 6.3 Immutable constraints and approved decisions

Root 指定 no POM、Outbox schema/wire 或 fingerprint change。carrier 限 Tianji terminal channel、explicit default false。启用独立于 source，即使 finite GREEN，也要实际部署 DEAD-listener 兼容/审计和隔离 exporter 证明。具体技术采用须以有效 Spec source lock 为准。

### 6.4 Plan Clarifications

| ID | Clarification | Evidence | Effect if wrong |
| --- | --- | --- | --- |
| PC-001 | 用现有 SDK SpanProcessor/ReadableSpan.toSpanData 本地收集，不靠 InMemorySpanExporter artifact | owning AS classpath，无 sdk-testing | 若 SDK 不在锁定 classpath，provider-less；actual Link pending |
| PC-002 | producer 在原选定 Tx 后注册同步，先成功注册再创建 Span；end afterCompletion | OutboxTransactionGuard.requireSelectedTransaction 要求 active sync | 失败时只移除观测，原 enqueue |
| PC-003 | delivery 原 exact authority/reference 先行，不主动 mutate global/current context | existing handler and pinned setNoParent/addLink | link absence 不决定业务 |
| PC-004 | 未确定是否要提交 Spec/Plan/EVD 到仓库；当前 source scope 只列 Spec §8 九个路径 | 用户要求仓库外 Plan | 若需要额外 artifact 路径，由 Root 在源码锁前列明 |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 固定封闭 carrier 和本地实际 SDK Link 证据

- Requirements: REQ-001, REQ-002, REQ-004, REQ-005
- Dependencies: approved source scope / clean 6b806e4 lock
- Baseline state: existing AS OTel/metrics present，no helper or carrier。
- Observable outcome: 同包 helper 支持严格 slot 与实际 SDK Link、低基数和 no-op。
- End state: 仅 helper/test 两路径，未接任何 factory。
- Test-first gate: Required — 反射缺 helper 真 RED；A compile 后 sampled origin/Link positive assert RED。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-PATTERN-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservationTest.java`

- Purpose: 固定 strict codec、SDK SpanData 和 bounded counter behavior。
- Symbols: `spanObservationSeamExists` temporary；`closedCarrierAndRealSdkLinks`、`disabledAndUnavailableDoNotMutateContext`。
- Repository evidence: AS owning OTel API/SDK trace 1.62.0、Micrometer 1.17.2；no SDK-testing exporter.
- Dependencies and consumers: JUnit, existing SdkTracerProvider, test-local SpanProcessor, SimpleMeterRegistry. 无网络/全局 provider 更改。
- Why now: 反射测试缺 class，A declared seam 后 typed actual Link assertion。
- Contract/signature changes: 关闭、坏/旧/缺、无 SDK；真实 sampled origin + two independent consumer roots。
- Input/output and state mapping: 严格 70 ASCII/version/sampled/非全零；检查 SpanData.getLinks 两 ID、one Link、计数 ≤4。
- Error and edge behavior: SDK/metrics 抛出仅 observation；invalid 不打印原值；primary SDK classpath absence 按 PC-001。
- Standards impact: MC-TEST-001 / MC-VALID-001 / MC-UTIL-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11；本地 SDK, no added dependency。
- Implementation pseudocode:

```java
assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.agent.server.integration.process.AgentResultSpanObservation"));
var completed = new ArrayList<SpanData>();
var provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).addSpanProcessor(captureOnEnd(completed)).build();
var origin = captureAndCompleteFixtureTransaction(provider);
assertTwoSeparateRootSpansLinkToExactly(origin, completed);
assertCounterIdsAreFixedFourValuesAndNoGlobalContextMutation();
```

- Verification contribution: G1 reflection/typed RED and finite GREEN。
- After this file: A 后运行正向 SDK 或明确 provider-less 条件。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservation.java`

- Purpose: 单一 package-local 技术 observer 与 trace_id codec。
- Symbols: `AgentResultSpanObservation.disabled()/enabled(...), enqueueCarrier(), delivery(String traceId)`，local no-op AutoCloseable。
- Repository evidence: pinned GlobalOpenTelemetry.getOrNoop、SpanBuilder.setNoParent/addLink，现有 Tx synchronization。
- Dependencies and consumers: Step 2 publisher/handler；供 Mock/SDK fixtures，no app。
- Why now: A 安全 no-op 让 typed RED；B 只实现有限观测。
- Contract/signature changes: opt-in 读取唯一宿主 OpenTelemetry（无则 getOrNoop），不强行 init/mutate；optional MeterRegistry 单 counter。
- Input/output and state mapping: sampled valid local source → exact tianji-link-v1:00-…-…-01；校验 32/16 lowercase nonzero；delivery 独立 root、1 actual Link。
- Error and edge behavior: 不存在/无效/SDK 异常只降级；先登记 afterCompletion 再建 producer，end safe；不 scope current/MDC 或输出 raw carrier。
- Standards impact: MC-ARCH-001 / MC-VALID-001 / MC-TIME-001 / MC-PATTERN-001。
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 9, Rule 10, Rule 11；narrow technical role。
- Implementation pseudocode:

```java
if (!enabled || !registerAfterCompletionBeforeStart()) return null;
var producer = tracer.spanBuilder("tianji.agent.result.enqueue").setNoParent().setSpanKind(PRODUCER).startSpan();
rememberForAfterCompletion(producer);
return encodeOnlyVerifiedSampledLocal(producer.getSpanContext());
var source = decodeExactCarrierOrAbsent(traceId);
return startIndependentDeliverySpanWithOneLink(source); // Do not change current context.
```

- Verification contribution: G1 local SDK and metric identities。
- After this file: 未接任何生产 Bean/配置。

#### File 3 — `CREATE docs/egon/spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md`

- Purpose: Preserve exact approved source review.
- Symbols: Exact reviewed hash, source scope and actual finite proof references.
- Repository evidence: Existing docs/egon reviewed Spec, Plan and evidence layout.
- Dependencies and consumers: This Step's strict source, prior gates and user review.
- Why now: Record this exact approved source scope or actual completed proof before commit.
- Contract/signature changes: None.
- Input/output and state mapping: Preserve exact checked metadata; runtime remains separately pending.
- Error and edge behavior: Missing, stale or wrong scope/hash prevents commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 exact existing technical owner and validated path.
- Implementation pseudocode:

```text
verify exact approved bytes or actual report hashes
write the named documentation once
verify strict links, planned path and staged tree
```

- Verification contribution: Reviewable source and verification boundary.
- After this file: No runtime activation; verify Step exact scope.

#### File 4 — `CREATE docs/egon/plan/2026-11-05-17-45-agent-terminal-span-link-implementation.md`

- Purpose: Preserve exact approved source review.
- Symbols: Exact reviewed hash, source scope and actual finite proof references.
- Repository evidence: Existing docs/egon reviewed Spec, Plan and evidence layout.
- Dependencies and consumers: This Step's strict source, prior gates and user review.
- Why now: Record this exact approved source scope or actual completed proof before commit.
- Contract/signature changes: None.
- Input/output and state mapping: Preserve exact checked metadata; runtime remains separately pending.
- Error and edge behavior: Missing, stale or wrong scope/hash prevents commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 exact existing technical owner and validated path.
- Implementation pseudocode:

```text
verify exact approved bytes or actual report hashes
write the named documentation once
verify strict links, planned path and staged tree
```

- Verification contribution: Reviewable source and verification boundary.
- After this file: No runtime activation; verify Step exact scope.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `span_finite AgentResultSpanObservationTest` reflection→typed RED→GREEN。
- Expected result: exact report nonzero，RED F>0/E=S=0，GREEN F=E=S=0。
- Failure returns to: File 1 fixture/classpath、File 2 codec/SDK/metrics。
- Completion criteria: 四个路径（含审阅文档）、POM unchanged、no wiring。
- Rollback: focused revert，no persisted metadata。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservationTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservation.java`, `docs/egon/spec/2026-11-05-17-35-agent-terminal-outbox-span-links.md`, `docs/egon/plan/2026-11-05-17-45-agent-terminal-span-link-implementation.md`.
- Commit: `feat(agent-server): add scoped terminal trace observation`

### Step 2 — 接入原 terminal enqueue 与 verified delivery，默认关闭

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005
- Dependencies: Step 1
- Baseline state: helper 已 finite，原 publisher/handler 与配置 unchanged。
- Observable outcome: 保留原 fingerprint、SDK/authority/Tx，opt-in 时仅同源 trace_id first write 与 Link。
- End state: AS 7 paths，explicit off，不启用任何部署。
- Test-first gate: Required — 两个既有 tests 加 positive and negative RED；需要时 A overload 安全委托旧行为。
- Manual Checks: MC-SCOPE-001, MC-TEST-001, MC-ARCH-001, MC-VALID-001, MC-BEAN-001, MC-LOG-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/OutboxAgentTerminalEventPublisherTest.java`

- Purpose: 钉住原事件 headers/trace 写入顺序与 transaction。
- Symbols: `traceCarrierIsOptionalAndTransactionOwned` + 原 publishes/rollback tests。
- Repository evidence: 当前 mock DataSource/Connection + TransactionTemplate 与 exact singleton headers。
- Dependencies and consumers: File 3 package overload、Step 1 helper fake。
- Why now: 先固定 old and enabled behavior；不启动 Pg。
- Contract/signature changes: assert old headers/canonical eventId/idempotency same bytes；原 route/ownership/encoding fail 0 observation。
- Input/output and state mapping: enabled traceId only，afterCompletion/rollback，no SDK 原 enqueue；同事件两上下文业务 fingerprint 输入相同。
- Error and edge behavior: SDK 异常不吞原 outbox failure，不重复 send。
- Standards impact: MC-TEST-001 / MC-JSON-001 / MC-TIME-001。
- Literal rule enforcement: Rule 2, Rule 6, Rule 10, Rule 11；保留同源证据。
- Implementation pseudocode:

```java
assertOriginalFingerprintHeaderOnlyAndCanonicalPayload(outboxMessage);
assertTraceIdNullWhenDisabled();
assertCarrierProvidedOnlyAfterSelectedTransactionAndEventChecks();
assertTelemetryEndRunsAfterExistingCommitOrRollback();
assertSameBusinessFingerprintInputsOnRepeatedEventWithDifferentCarrier();
```

- Verification contribution: G2 typed RED then exact finite。
- After this file: 原 tests 保留，新增 source boundary。

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandlerTest.java`

- Purpose: 钉住 authority-first、strict carrier degraded 与一次原传输。
- Symbols: `verifiedAttemptLinksWithoutChangingWireOrRetry` + 原 invalid/replay cases。
- Repository evidence: 当前 Mockito ledger/HttpAgentResultClient、fake Clock。
- Dependencies and consumers: File 4 package overload，Step 1 SDK helper。
- Why now: 先锁旧合约和真实 new behavior，再改 production。
- Contract/signature changes: legacy/disabled/invalid carrier/SDK fail 仍 normal existing disposition；verified valid carrier one Link。
- Input/output and state mapping: exact ledger/reference and deadline before observation, original client.receive(event) once。
- Error and edge behavior: bad header/authority 时 0 SDK/transport；transport failure 本义不变，no fake success。
- Standards impact: MC-TEST-001 / MC-VALID-001 / MC-JSON-001。
- Literal rule enforcement: Rule 2, Rule 6, Rule 10, Rule 11；固定旧错误。
- Implementation pseudocode:

```java
verify(ledger).terminalReference(owner, query, readBudget);
assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(withTraceId(verifiedContext, validCarrier)).kind());
verify(client, times(1)).receive(event);
assertSdkLinkUsesVerifiedCarrierAfterOriginalReferenceProof();
assertUnknownOrMalformedCarrierDoesNotChangeExistingDeliveryDecision();
```

- Verification contribution: G2 typed scope and exact Link/transport cases。
- After this file: existing tests remain source-only。

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/OutboxAgentTerminalEventPublisher.java`

- Purpose: 在原 selected Tx 添加 optional first-write diagnostic slot。
- Symbols: `OutboxAgentTerminalEventPublisher.publish`，保留 public ctor、包内 observation overload。
- Repository evidence: 原 selected Tx/producer/destination/encode/enqueue/receipt 顺序；headers 进 Outbox fingerprint。
- Dependencies and consumers: File 1 tests、Step 1 helper、File 5 factory。
- Why now: test-first 后最小 publisher branch，先校验后观察。
- Contract/signature changes: 只在此 channel 按 opt-in 将编码放 OutboxMessage.traceId；headers/bytes unchanged。
- Input/output and state mapping: encode 后 obtain optional producer carrier，原 builder/receipt 检查仍同源。
- Error and edge behavior: 缺/malformed/unavailable 观察不阻断；原失败照抛并 rollback；不主动 flush/export。
- Standards impact: MC-REUSE-001 / MC-JSON-001 / MC-TIME-001。
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 10, Rule 11；单技术 slot。
- Implementation pseudocode:

```java
guard.requireSelectedTransaction();
requireExactExistingEventAndDestination();
String payload = encode(event);
String carrier = observation.enqueueCarrier();
var message = originalMessageBuilder(event, payload).traceId(carrier).build();
requireOriginalMatchingReceipt(outbox.enqueue(message));
```

- Verification contribution: G2 original header + rollback/success。
- After this file: publisher preserves legacy constructor / no wire changes。

#### File 4 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandler.java`

- Purpose: 现有 verified delivery 添加真实 Link。
- Symbols: `AgentResultDeliveryHandler.deliver`，legacy constructor delegates disabled。
- Repository evidence: context header equals、terminalReference、现有 deadline、receive 和 status mapping。
- Dependencies and consumers: File 2 tests、Step 1 helper、File 6 factory。
- Why now: 先守原 authority，typed RED 后接本地 span。
- Contract/signature changes: only exact tianji-agent-result after verified event/reference；create root with Link from carrier；no HTTP data added。
- Input/output and state mapping: 围住 original single receive call；原无 ambient TX、deadline 复验和 retry/permanent/success。
- Error and edge behavior: SDK/registry failure 可降级但不能 catch 并重试业务调用；wrong header/ledger proof 仍走旧拒绝。
- Standards impact: MC-VALID-001 / MC-JSON-001 / MC-TIME-001。
- Literal rule enforcement: Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11；不替换原处理器。
- Implementation pseudocode:

```java
requireExistingEnvelopeRouteLedgerReferenceAndDeadline(context);
try (var deliverySpan = observation.delivery(context.traceId())) {
    preserveExistingBeforeSendDeadline();
    var receipt = route.client().receive(event);
    return existingReceiptDecision(receipt, event);
}
```

- Verification contribution: G2 exact call count/wire and proof order。
- After this file: handler legacy/no carrier behavior preserved。

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentTerminalOutboxConfiguration.java`

- Purpose: 保留原 named publisher factory，读取唯一 opt-in。
- Symbols: `tianjiAgentTerminalEventPublisher`，Environment flag。
- Repository evidence: 现有 terminal-events.enabled、DataSource/Outbox/ledger guard 不变。
- Dependencies and consumers: File 3 ctor；optional OpenTelemetry provider。
- Why now: verified publisher source 已就绪才接工厂。
- Contract/signature changes: flag false → disabled；true → scoped helper with trusted OTel provider or getOrNoop。
- Input/output and state mapping: publisher 不注册 meter；原 current destination/producer bind。
- Error and edge behavior: missing/ambiguous optional provider 不构造新 SDK 或改 global；不动原 prerequisite。
- Standards impact: MC-BEAN-001 / MC-CONFIG-001。
- Literal rule enforcement: Rule 4, Rule 5, Rule 7, Rule 11；现有 named factory。
- Implementation pseudocode:

```java
var enabled = environment.getProperty("stellar.tianji.agent.terminal-events.span-links-enabled", Boolean.class, false);
var observation = enabled ? localObservationWithExistingTelemetry(telemetryProvider) : disabledObservation();
return new OutboxAgentTerminalEventPublisher(source, infrastructure, outbox, producerId, destinations, observation);
```

- Verification contribution: G2 current factory off regression。
- After this file: publisher conditional semantics unchanged。

#### File 6 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentResultDeliveryConfiguration.java`

- Purpose: 保留原 named delivery factory，optional counter 和 Link。
- Symbols: `tianjiAgentResultDeliveryHandler`，same flag。
- Repository evidence: 现有 result-delivery enabled、clients/ledger provider/route。
- Dependencies and consumers: File 4 ctor，现有 optional SDK/MeterRegistry。
- Why now: publisher/handler seam 已齐后，读取同一 flag。
- Contract/signature changes: off 不调 registry/provider；on unique optional existing provider，非阻断。
- Input/output and state mapping: 仅固定 result enum counter；原 clients/destination/auth no changes。
- Error and edge behavior: registry/SDK absence/failure safe，未越权替换 local global。
- Standards impact: MC-BEAN-001 / MC-CONFIG-001。
- Literal rule enforcement: Rule 4, Rule 5, Rule 7, Rule 11；现有 named factory。
- Implementation pseudocode:

```java
var enabled = environment.getProperty("stellar.tianji.agent.terminal-events.span-links-enabled", Boolean.class, false);
var observation = enabled ? existingTelemetryAndOptionalFixedCarrierCounter(telemetryProvider, meterProvider)
                          : disabledObservation();
return new AgentResultDeliveryHandler(ledgers::getObject, clients.routes(), Clock.systemUTC(), observation);
```

- Verification contribution: G2 config defaults and scopes。
- After this file: 两个 factories 都有相同 explicit gate。

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: 在唯一现有 AS profile 显式保留 off。
- Symbols: `stellar.tianji.agent.terminal-events.span-links-enabled=false`。
- Repository evidence: 当前 sole application.yml，无 terminal-events subtree；原 model usage/other keys 保持。
- Dependencies and consumers: 两原 factories，operator independent activation。
- Why now: 完整 wiring/finite 后加唯一默认。
- Contract/signature changes: false only；terminal-events.enabled、result-delivery 等旧条件不变。
- Input/output and state mapping: explicit opt-in 后才启动 observability 代码，source deliver 不写 true。
- Error and edge behavior: 无需新 config/policy/owner scope；运行开启须 separate evidence。
- Standards impact: MC-CONFIG-001 / MC-SCOPE-001。
- Literal rule enforcement: Rule 7, Rule 11；唯一 source YAML change。
- Implementation pseudocode:

```yaml
# Add only the technical flag under the existing AS application profile. Preserve all prerequisite and other defaults.
stellar:
  tianji:
    agent:
      terminal-events:
        span-links-enabled: false
```

- Verification contribution: G2 default property key + exact diff。
- After this file: AS 编译可验证，仍默认关闭。

#### File 8 — `CREATE docs/egon/evidence/2026-11-05-agent-terminal-outbox-span-links.md`

- Purpose: Record actual finite/packaged trace source proof.
- Symbols: Exact reviewed hash, source scope and actual finite proof references.
- Repository evidence: Existing docs/egon reviewed Spec, Plan and evidence layout.
- Dependencies and consumers: This Step's strict source, prior gates and user review.
- Why now: Record this exact approved source scope or actual completed proof before commit.
- Contract/signature changes: None.
- Input/output and state mapping: Preserve exact checked metadata; runtime remains separately pending.
- Error and edge behavior: Missing, stale or wrong scope/hash prevents commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11 exact existing technical owner and validated path.
- Implementation pseudocode:

```text
verify exact approved bytes or actual report hashes
write the named documentation once
verify strict links, planned path and staged tree
```

- Verification contribution: Reviewable source and verification boundary.
- After this file: No runtime activation; verify Step exact scope.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: `span_finite OutboxAgentTerminalEventPublisherTest,AgentResultDeliveryHandlerTest,AgentResultSpanObservationTest,TianjiAgentResultDeliveryConfigurationTest,ClientDependencyBoundaryTest`；final §8。
- Expected result: typed assertion RED 后所有 owning finite F=E=S=0；old JSON/schema/POM hashes unchanged。
- Failure returns to: Files 1/2 fixture、3/4 tx/authority、5/6 wiring、7 default。
- Completion criteria: 八路径（含实际证据）、fresh finite XML、no repo runtime activation。
- Rollback: 关闭或回退新二进制；durable already-written metadata 留存。
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/OutboxAgentTerminalEventPublisherTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandlerTest.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/OutboxAgentTerminalEventPublisher.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandler.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentTerminalOutboxConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentResultDeliveryConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `docs/egon/evidence/2026-11-05-agent-terminal-outbox-span-links.md`.
- Commit: `feat(agent-server): link terminal outbox delivery spans`

## 8. Test, Validation, and Quality Gates

The otherwise related `HttpAgentResultClientTest` is excluded because its fixture binds a JDK HTTP listener. Current fake AS delivery tests and `ClientDependencyBoundaryTest` provide finite wire/SDK boundary checks. No listener, app, PostgreSQL or exporter endpoint is opened.

未来 source GO 从 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 执行。只认本次实际有限测试的 owning XML；不选择 PostgresTest/E。SDK 测试本地收集 SpanData，无 exporter/network。Provider-less fallback 时只报告实际覆盖项。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false
span_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
span_finite() {
  STELLAR_VERIFY_GOAL=test ./scripts/verify-source-reactor.sh \
    -gs "$span_settings" -s "$span_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
    "-Dtest=$1"
}
```

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| G1 RED/GREEN | tianji-service | `span_finite AgentResultSpanObservationTest` | codec and local SDK | reflective/typed RED then finite GREEN | S1F1/2 | REQ-001/002/004/005；U |
| G2 RED/GREEN | tianji-service | `span_finite OutboxAgentTerminalEventPublisherTest,AgentResultDeliveryHandlerTest,AgentResultSpanObservationTest,TianjiAgentResultDeliveryConfigurationTest,ClientDependencyBoundaryTest` | original paths/default off | actual RED then F=E=S=0 | owning file | REQ-001–005；U |
| Final package | tianji-service | 下方 clean verify + exact old hashes | AS + existing wire | finite/actual pack | owning Step | source only |
| I/E | isolated operator environment | existing Outbox/collector seams | actual first-write/exporter | real Link and compatibility evidence | independent runtime approval | no source substitution |
| Activation | deployed target | §9 approvals | actual listener/collector | both verified before opt-in | operator | independent |

```sh
STELLAR_VERIFY_GOAL=verify ./scripts/verify-source-reactor.sh \
  -gs "$span_settings" -s "$span_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none clean \
  '-Dtest=AgentResultSpanObservationTest,OutboxAgentTerminalEventPublisherTest,AgentResultDeliveryHandlerTest,TianjiAgentResultDeliveryConfigurationTest,ClientDependencyBoundaryTest,ProcessPermissionIntegrationTest'
```

已有 test suites、JDK 与 wrapper 覆盖有限门禁，当前不新增脚本。E/S12 运行证据为独立用户范围。

## 9. Migration, Compatibility, Rollout, and Rollback

No migration。现有 schema、POM、event body、header singleton、幂等 fingerprint、retry 与权限保持；trace_id 只对 Tianji terminal channel 增有界 optional diagnostic value，旧 handler 仍忽略，所有其它 channels 保持。默认 false 源码可发布，实际启用另外要求：

1. 实际部署环境所有 DEAD listeners、告警、索引和 traceId 解析消费者的兼容审核，记录 exact versions 与查询证据。
2. 用户明确授权的隔离 I/E，用真实 AS SDK/collector 确认 source→delivery Link、首写/重投、exporter 失效隔离与无秘密。临时 opt-in 只在该隔离环境。
3. 生产 operator 审阅两类实际证据后才启用；缺一继续 off。回滚关开关或旧制品，保留已写 metadata，无历史清理。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Steps | Changed files / symbols | Required evidence |
| --- | --- | --- | --- |
| REQ-001 | Step 1/2 | helper/publisher carrier | exact first-write/rollback |
| REQ-002 | Step 1/2 | local SDK/handler | actual Link IDs and independent E |
| REQ-003 | Step 2 | original event/fingerprint/auth | finite legacy parity |
| REQ-004 | Step 1/2 | counter/factory/yaml | low cardinality/default off |
| REQ-005 | Step 1/2 | U vs I/E | explicit boundaries |

## 11. Risks, Blockers, and User Decisions

| ID | Risk/blocker | Evidence/decision | Required action |
| --- | --- | --- | --- |
| DEC-005 | Exact AS-only source with explicit default false | Root scoped source, unchanged old Outbox schema/headers/hash | Actual DEAD listener compatibility and exporter evidence remain deployment prerequisites |
| R-001 | deployed DEAD listeners 未查 | 当前源码未发现 Tianji custom listeners，slot 可传 generic diagnostic event | 实际 audit，缺证据不启用 |
| R-002 | installed SDK/exporter 未证 | local SpanData 不能代替 collector | isolated E before activation |
| R-003 | PS/W 未覆盖 | 当前 PS 无 OTel；保持 wire | 完整 S12 后续 |
| R-004 | SDK test classpath drift | exact existing SDK，无 sdk-testing artifact | 不增依赖，provider-less finite 并报告 actual Link 未证 |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

范围为 AS durable async edge，原 schema、fingerprint 与 wire 保持。

### 12.2 Spec consistency

helper、carrier、off 对齐 §7–16，source scope 与 activation 分开。

### 12.3 Repository executability

精确九条 AS 路径、原 constructors、same flag 与现有 source reactor 已只读核对。

### 12.4 Test and release completeness

仅 Draft Plan。源码、Maven、真实 I/E 和 activation 均需独立证据。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/7 | existing AS owner | None |
| MC-REUSE-001 | Applicable | PASS | §4/7 | existing SDK/Outbox | None |
| MC-DEP-001 | Applicable | PASS | §6/7 | no POM change | None |
| MC-NAME-001 | Applicable | PASS | §5/7 | narrow helper | None |
| MC-VALID-001 | Applicable | PASS | §7 | original authority + strict codec | None |
| MC-MODEL-001 | Applicable | PASS | §7 Step 1 | immutable values | None |
| MC-CONVERT-001 | Applicable | PASS | §7 Step 1 | closed technical mapping | None |
| MC-LOG-001 | Applicable | PASS | §7/9 | no raw log | None |
| MC-BEAN-001 | Applicable | PASS | §7 Step 2 | existing factories | None |
| MC-UTIL-001 | Applicable | PASS | §4/6 | no new utility | None |
| MC-JSON-001 | Applicable | PASS | §7/9 | original wire | None |
| MC-TIME-001 | Applicable | PASS | §4.9/7 | SDK time and Tx completion | None |
| MC-CONFIG-001 | Applicable | PASS | §7/9 | one default false flag | None |
| MC-PATTERN-001 | Applicable | PASS | §4/7 | narrow Observer | None |
| MC-SCOPE-001 | Applicable | PASS | §6/11 | exact channel-local slot source lock | DEC-005 |
| MC-TEST-001 | Applicable | PASS | §7/8 | explicit finite vs I/E | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Exact scoped source reviewed | DEC-005 |

### 12.6 Final verdict

PASS — Ready for user review

## Java / CQE contract review

原终态 Event 使用已有 Outbox；技术诊断无业务 Command/Query/授权。沿用原 Spring named factories、OTel/Micrometer、值和 UTC。

## Backend generation and dependency evidence

无 DDL、generator 或新增依赖。仅 Tianji channel 复用 trace_id；所有 POM、SQL 与通用 Outbox fingerprint 受 exact scope 检查。
