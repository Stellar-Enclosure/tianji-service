# T4-02 Agent 结果接收 Span Link 实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-19-20-agent-result-receive-span-links.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 19:20 Asia/Shanghai |
| Updated | 2026-11-05 19:38 Asia/Shanghai |
| Owner | Tianji AS/PS 技术所有者和 Master 决策人 |
| Repository | tianji-service |
| Scope | 13 路径诊断接入、Spec/Plan/EVD，单逻辑提交 |
| Source Requirement | Master §13.3/§14.7/§19 T4-02 |
| Baseline Revision | Source83 clean af26473216d259049a4edac8b2d42b236d3a5fa7, Step82 package baseline retained |
| Implements Spec | [T4-02 cross receive Link](../spec/2026-11-05-19-19-agent-result-receive-span-links.md) |
| Spec Status | Accepted |
| Spec Revision | 2026-11-05 19:38 Asia/Shanghai, SHA-256 a9acae58e184648d102837fde7aeb0c301a3f8c4843e1ed36870099434e2883b |
| Effective Specs | [T4-02 cross receive Link](../spec/2026-11-05-19-19-agent-result-receive-span-links.md) |
| Depends On Plans | [AS local Link](2026-11-05-17-45-agent-terminal-span-link-implementation.md), [PS lag](2026-11-05-18-47-process-inbox-decision-delay.md), [Step82](2026-11-05-18-53-bpmn-migration-preflight.md), [Source83](2026-11-05-19-28-governed-process-injection-corpus.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

以现有 AS delivery span 和 PS 成功 receipt 之间的一项默认关闭诊断 Link 为唯一交付目标。先完成双项审批和 Step82 源码锁，再执行一个测试优先的 16 路径聚焦 Step。无新业务写入、启动、启用或发布授权。

## 2. Target Spec and Effective Design

Primary Spec §5 DEC-001 记录用户已明确批准 Master §14.7 窄例外与 PS 两项依赖。现有 AS 本地 Link、PS lag 和 Step82 migration-preflight 是上下文和共存基线，非本次实施需求。本轮明确选择批准分支。未批准或仅批准一项的备选分支维持 off-repo 证据。Master owner 已记录窄例外，Master 路径不进本提交。源代码须单独锁 SHA 和 Source GO，部署与启用继续独立。

## 3. Effective Requirements and Acceptance

| ID | Acceptance |
| --- | --- |
| REQ-001 | 已 verified 的本次 recording delivery Span，在双 flag 条件下产生唯一闭合值 |
| REQ-002 | 原 C 接口和单次 POST 保留，关闭时原 bytes/headers，启用时只有私有头变化 |
| REQ-003 | PS existing SERVICE+Inbox 校验后单 Link 保留 ambient parent，错误/无 provider/关闭无作用 |
| REQ-004 | PS API compile/Trace SDK test，BOM 1.62.0，默认 false，Step82+lag 兼容 |
| REQ-005 | 独立真实有限报告、实际包和范围证明，不启动运行环境或启用 exporter |

## 4. Implementation Strategy and Dependency Order

R1 校验旧 AS YAML 缺显式 false，R2 用 CL A 验证缺头，CL B 锁后 R3 用 PS A 验证缺 Link，R4 用 AS A 验证缺本次 Span 的 wire carrier。最终三个所有者合并到一个 Step 验证和提交。生产行为的 A 只给真实断言所需声明，不接出新诊断能力。

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Owner placement | Current AS/CL/PS technical packages | Extend exact adapters, keep C and persistence |
| Validation | Existing PS body advice and authority | Optional fixed header validator independent of existing business verification |
| Configuration | Existing Boot Condition/Environment | Explicit defaults, same PS endpoint/constructors |
| Security | Current SERVICE Scope, verified issuer/tenant and dispatch | No new access or trace parent |
| Scope | Step82 and PS lag source | Preserve both YAML settings and native invariants |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Native Link | Existing Micrometer Observation/TraceContext and OTel | Existing PS artifact no tracing provider | Only OTel direct provides exact SDK Link in smallest approved scope | Add PS API, test-only SDK Trace after DEC-001 |
| Wire | C AgentResultClient and current CL HttpClient | Closed event body and single bound send | Keep C shape, extend concrete CL | Reuse |
| Authority | PS current controller, access and Inbox | Committed receipt only after actual owner/dispatch/event checks | Post-success seam | Reuse |
| Test | Existing MockMvc, JDK fakes, JUnit, local SDK | CL old loopback class binds listener | New finite fake fixture with no listener | Reuse established patterns |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Exact adapter names | Files 1–11 | Test and technical helper names | G1 | 1 | PASS |
| Rule 2 | §9 | Existing body/authority plus diagnostic validation | Files 4/5/7/8 | Validate closed header independently | R2/R3/G1 | 1 | PASS |
| Rule 3 | §10 | Existing C immutable records | Files 4–11 | No new business POJO | S1 | 1 | N/A |
| Rule 4 | §7 | Current Spring beans | Files 8/11 | Reuse existing controller/bean and selected injection | G1 | 1 | PASS |
| Rule 5 | §6 | JDK regex and pinned OTel | Files 4/6/7/9 | No reflection or transport dependency beyond approved | G1/P1 | 1 | PASS |
| Rule 6 | §9/10 | Existing JSON/event hash | Files 4/5/8 | Keep body/response and mapper | R2/G1/S1 | 1 | N/A |
| Rule 7 | §16 | Existing base YAML | Files 12/13 | Explicit false, preserve Step82 and lag | R1/P1 | 1 | PASS |
| Rule 9 | §13 | Current adapter boundaries | Files 5/8/10 | Compose closed diagnostic helper at actual seam | G1 | 1 | PASS |
| Rule 10 | §6.3 | Existing deadline/Instant | Files 5/10 | Preserve timing semantics | G1 | 1 | N/A |
| Rule 11 | §7/9/11 | Current AS Outbox and PS Inbox ownership | Files 1–16 | Same event and owner, zero SQL, no business interface | P1/S1 | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable
Reason: Existing deadlines, receipt Instants and storage stay intact. SDK Span instrumentation does not add business time, timezone, storage or wire conversion.

## 5. Change File Tree

| Operation | Exact path | Owner and purpose | Gate |
| --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpAgentResultTraceLinkTest.java | JDK fake captures exact outbound method, headers and body, returns strict 202 | R2/G1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandlerTest.java | Existing delivery span and its first-write source Link, sender false fallback, live carrier | R1/R4/G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxControllerTest.java | Mock established Inbox returns committed-shape receipt, real SpanData verifies parent and Link | R3/G1 |
| CREATE | stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentResultTraceLinkCarrier.java | Closed wire value of length 55 ASCII, sampled v00, exact case and both IDs nonzero | R2/G1 |
| MODIFY | stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentResultClient.java | Existing request builder and exact route, validated HTTPS diagnostic header only | R2/G1 |
| MODIFY | stellar-enclosure-tianji-process-server/pom.xml | Declare 2 versionless BOM-managed artifacts at exact scopes, 1.62.0 | R3/P1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxReceiveObservation.java | Read bounded single private header from current servlet context and create CONSUMER link | R3/G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxController.java | After original SERVICE auth and full Inbox receipt returned, invoke observation then original 202 | R3/G1/P1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservation.java | Expose only real recording/local/valid/sampled IDs from this delivery | R4/G1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandler.java | Select old or optional overload only after all unchanged terminal checks and in existing delivery lifetime | R4/G1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentResultDeliveryConfiguration.java | Derive sender permission only when existing local observation enabled and new property true | G1/P1 |
| MODIFY | stellar-enclosure-tianji-agent-server/src/main/resources/application.yml | Add only sender flag under agent/result-delivery and preserve local false | R1/P1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/resources/application.yml | Add one false leaf under existing agent-results block | P0/P1 |
| CREATE | docs/egon/spec/2026-11-05-19-19-agent-result-receive-span-links.md | Reviewed bytes and exact 16-path inventory | S1 |
| CREATE | docs/egon/plan/2026-11-05-19-20-agent-result-receive-span-links.md | Approved bytes and dependency order | S1 |
| CREATE | docs/egon/evidence/2026-11-05-agent-result-receive-span-links.md | Archive/dependency/permissions/YAML/SQL and current failure boundaries | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

- **P0a dual approval.** User explicitly approved the exact private header exception and both PS scopes on 2026-11-05. Master §14.7 records the exception. A partial/refused decision would have stopped source. Actual Source GO remains separate, as do runtime, deployment, exporter and flags.
- **P0b Source83 lock.** Read current service HEAD and require clean exact af26473216d259049a4edac8b2d42b236d3a5fa7. This test-only Source83 advances Step82 6fcd76be629680d8cd8863537a4c31adc2506952 by the exact AR test, evaluation fixture and three docs. Its five-file delta has zero intersection with the 13 source paths and leaves AS/CL/PS module contents unchanged. Previous source lock 695c9115921ba3068db445526b7bf51be87a350b advanced only through Step82's 12-path commit. Its intersection with the candidate is PS application.yml only. Step82 package SHA a2d19814011957d53aa72ae4897c529214de11cc76e0502e18b2ece5e7bcc1e8, permissions manifest SHA a6e8dc9a9edece682dbcf04b0584aa09dd1337b78f953bb5bfaa86b38c0fb4e9, PS base YAML SHA d44854f0975763972f58b76d73823214a7d7937f85c9afbf057b8777064fdbb3. Verify off-repo candidate SHA a644c6311b69832a6f55442cb27268318321b7910ad5813f6d0e19bf407b1700 with git apply --check. Use a contextual YAML patch. Three-way preview showed independent hunks. Do not copy the old complete PS YAML from the prototype over Step82.
- **P0c resources.** Freeze all 27 existing SQL, both existing packaged AS and Step82 PS PaC projections and original C/Outbox event/fingerprint paths. Recheck existing JDK25/offline settings and local artifact pins. An absent OTel JAR stops before source instead of fetching or changing resolver settings. No repo changes before Source GO.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Link accepted PS Agent result to one verified AS delivery span

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: P0a/P0b/P0c, actual source GO, completed AS local Links, Step82 and Source83.
- Baseline state: Clean Source83 af26473216d259049a4edac8b2d42b236d3a5fa7, PS has no OTel API/SDK, both existing transfer and permission contracts frozen.
- Observable outcome: A bounded post-commit native Link on authorized private ingress when both sides qualify. Old flow, receipt and source owner verified.
- End state: Exact 13 reviewed source paths and three docs, one logical commit, no runtime enablement.
- Test-first gate: Required. R1 base YAML presence, R2 CL typed safe old call, R3 PS typed no-op after receipt, R4 AS typed no-carrier behavior. Each is a genuine targeted assertion with zero compile/fixture errors.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpAgentResultTraceLinkTest.java`

- Purpose: JDK fake captures exact outbound method, headers and body, returns strict 202.
- Symbols: HttpAgentResultTraceLinkTest, FakeHttpClient and native BodyPublisher byte capture.
- Repository evidence: Existing CL HttpProcessControlClientTest and HttpAgentResultClient transport.
- Dependencies and consumers: CL concrete overload and validator, JUnit and JDK only.
- Why now: R2 before the new header branch.
- Contract/signature changes: JDK fake captures exact outbound method, headers and body, returns strict 202.
- Input/output and state mapping: JDK fake captures exact outbound method, headers and body, returns strict 202.
- Error and edge behavior: Invalid, merged, zero, upper, unsampled, cleartext and legacy inputs issue same single old POST.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
var original = fake.requestAfter(client.receive(event));
var linked = fake.requestAfter(client.receive(event, exactCarrier));
assert same URI, POST, deadline bound, body bytes, event fingerprint and old headers;
assert only added header equals exactCarrier; malformed, cleartext and prefixed HTTPS keep old requests;
```

- Verification contribution: R2/G1.
- After this file: HttpAgentResultTraceLinkTest, FakeHttpClient and native BodyPublisher byte capture is available to R2/G1 in the declared source phase.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandlerTest.java`

- Purpose: Existing delivery span and its first-write source Link, sender false fallback, live carrier.
- Symbols: terminalSpanLinksFlagDefaultsOffWithoutEnablingPublication and crossProcessCarrierUsesTheActualDeliverySpanAndRetainsTheOldOffCall.
- Repository evidence: Existing AS AgentResultSpanObservationTest and exact Mockito delivery tests.
- Dependencies and consumers: AS observation, handler, approved CL B and real test-local OTel SDK.
- Why now: R1 first, R4 after CL/PS compile.
- Contract/signature changes: Existing delivery span and its first-write source Link, sender false fallback, live carrier.
- Input/output and state mapping: Existing delivery span and its first-write source Link, sender false fallback, live carrier.
- Error and edge behavior: No-op SDK, invalid route, expired deadlines and wrong terminal reference preserve original outcomes.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
var original = verifyExistingTerminalAndOutboxFingerprint();
assert explicitYamlFalseUnderResultDelivery(); // R1 selects only this added assertion against baseline
verify(client).receive(event);
var recorded = collectingSdk.lastCompletedDelivery();
verify(client).receive(event, format(recorded.getTraceId(), recorded.getSpanId()));
```

- Verification contribution: R1/R4/G1.
- After this file: terminalSpanLinksFlagDefaultsOffWithoutEnablingPublication and crossProcessCarrierUsesTheActualDeliverySpanAndRetainsTheOldOffCall is available to R1/R4/G1 in the declared source phase.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxControllerTest.java`

- Purpose: Mock established Inbox returns committed-shape receipt, real SpanData verifies parent and Link.
- Symbols: records_one_remote_link_only_after_durable_receive_and_keeps_the_local_parent and negative MockMvc cases.
- Repository evidence: Existing AgentResultInboxControllerTest, MockMvc and closed body validation.
- Dependencies and consumers: PS typed controller and observer, CL B helper and OTel Trace SDK test-only.
- Why now: R3 after safe PS declarations.
- Contract/signature changes: Mock established Inbox returns committed-shape receipt, real SpanData verifies parent and Link.
- Input/output and state mapping: Mock established Inbox returns committed-shape receipt, real SpanData verifies parent and Link.
- Error and edge behavior: Disabled, denied, duplicate, invalid, no provider, provider failure and conflict keep existing status.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
withCurrent(localPsParent) { mock inbox.receive returns existing 202 receipt after asserting no collected spans;
    performLinked(POST, fixedHeader); assert receipt body and no-store;
    assert same local parent, one exact remote sampled link, zero attributes, no changed current Context;
    assert invalid, missing, duplicated, path-prefixed, denied and failed calls do not resolve provider; }
```

- Verification contribution: R3/G1.
- After this file: records_one_remote_link_only_after_durable_receive_and_keeps_the_local_parent and negative MockMvc cases is available to R3/G1 in the declared source phase.

#### File 4 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentResultTraceLinkCarrier.java`

- Purpose: Closed wire value of length 55 ASCII, sampled v00, exact case and both IDs nonzero.
- Symbols: AgentResultTraceLinkCarrier.PATH, HEADER and isValid(String).
- Repository evidence: Existing CL and PS dependency on CL; AS local carrier parser in AgentResultSpanObservation.
- Dependencies and consumers: HttpAgentResultClient, AS observation and PS receive observation.
- Why now: A compile for R2; B first shared format authority.
- Contract/signature changes: Closed wire value of length 55 ASCII, sampled v00, exact case and both IDs nonzero.
- Input/output and state mapping: Closed wire value of length 55 ASCII, sampled v00, exact case and both IDs nonzero.
- Error and edge behavior: Null/invalid returns false without diagnostic logging or allocation beyond bounded regex.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
public static final String PATH = "/api/tianji/v1/agent-result-inbox";
public static final String HEADER = "X-Egon-Tianji-Agent-Result-Link";
boolean isValid(String value) {
    return value != null && lengthIs55(value) && matchesSampledV00WithNonzeroIds(value);
}
```

- Verification contribution: R2/G1.
- After this file: AgentResultTraceLinkCarrier.PATH, HEADER and isValid(String) is available to R2/G1 in the declared source phase.

#### File 5 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentResultClient.java`

- Purpose: Existing request builder and exact route, validated HTTPS diagnostic header only.
- Symbols: Existing receive(event) and additive concrete receive(event, String).
- Repository evidence: Current strict request builder and single sendAsync.
- Dependencies and consumers: C AgentResultClient unchanged; AS direct and legacy CL callers.
- Why now: Provide R2 compile seam then final send after typed assertion.
- Contract/signature changes: Existing request builder and exact route, validated HTTPS diagnostic header only.
- Input/output and state mapping: Existing request builder and exact route, validated HTTPS diagnostic header only.
- Error and edge behavior: Off, loopback, prefixed HTTPS, invalid value, token or deadline failure preserve original behavior.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
AgentResultReceipt receive(event) { return receive(event, null); }
var builder = originalBoundRequestBuilder(event, token, remainingDeadline);
if (https && PATH.equals(endpoint.getRawPath()) && AgentResultTraceLinkCarrier.isValid(traceLink)) builder.header(HEADER, traceLink);
return sendOnceAndCheckOriginalReceipt(builder.POST(originalEventBytes).build());
```

- Verification contribution: R2/G1.
- After this file: Existing receive(event) and additive concrete receive(event, String) is available to R2/G1 in the declared source phase.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/pom.xml`

- Purpose: Declare 2 versionless BOM-managed artifacts at exact scopes, 1.62.0.
- Symbols: PS OTel API compile and SDK Trace test.
- Repository evidence: Boot 4.1.2 BOM and existing PS module dependencies.
- Dependencies and consumers: PS receive observer compile and real finite SDK test only.
- Why now: After explicit DEC-001 and CL B, before R3 compile.
- Contract/signature changes: Declare 2 versionless BOM-managed artifacts at exact scopes, 1.62.0.
- Input/output and state mapping: Declare 2 versionless BOM-managed artifacts at exact scopes, 1.62.0.
- Error and edge behavior: No starter, exporter, runtime SDK, parent override, dependency installation or network.
- Standards impact: MC-DEP-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 5, Rule 11. The approved native API and test-only dependency stay PS-local and BOM-pinned.
- Implementation pseudocode:

```xml
<dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-api</artifactId></dependency>
<dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-sdk-trace</artifactId><scope>test</scope></dependency>
<!-- Existing imported Boot BOM resolves both to 1.62.0. -->
```

- Verification contribution: R3/P1.
- After this file: PS OTel API compile and SDK Trace test is available to R3/P1 in the declared source phase.

#### File 7 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxReceiveObservation.java`

- Purpose: Read bounded single private header from current servlet context and create CONSUMER link.
- Symbols: AgentResultInboxReceiveObservation.afterReceive, disabled/enabled and FLAG.
- Repository evidence: Existing Spring RequestContextHolder, native OTel SpanBuilder and PS controller.
- Dependencies and consumers: Controller calls only after current Inbox succeeds, CL validator is shared.
- Why now: R3 A no-op then B Link.
- Contract/signature changes: Read bounded single private header from current servlet context and create CONSUMER link.
- Input/output and state mapping: Read bounded single private header from current servlet context and create CONSUMER link.
- Error and edge behavior: No header lookup on wrong method or raw path, no provider lookup for absent/bad header, ambiguous provider no-op, no parent override or makeCurrent.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
if (disabled || !currentServletRequest || !POST.equals(method) || !PATH.equals(rawRequestUri)) return;
var carrier = exactlyOneValue(request.getHeaders(HEADER));
if (!isValid(carrier)) return;
var remote = SpanContext.createFromRemoteParent(exactTrace, exactSpan, sampled, emptyTraceState);
uniqueInstalledTelemetry().getTracer(scope).spanBuilder(name).setSpanKind(CONSUMER).addLink(remote).startSpan().end();
```

- Verification contribution: R3/G1.
- After this file: AgentResultInboxReceiveObservation.afterReceive, disabled/enabled and FLAG is available to R3/G1 in the declared source phase.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxController.java`

- Purpose: After original SERVICE auth and full Inbox receipt returned, invoke observation then original 202.
- Symbols: Existing receive(AgentTerminalEvent), old direct fixture constructor, selected Spring constructor.
- Repository evidence: Current controller path and TianjiProcessAccess.require.
- Dependencies and consumers: Existing Inbox and new package-local observation.
- Why now: R3 A keeps real accepted status and no span.
- Contract/signature changes: After original SERVICE auth and full Inbox receipt returned, invoke observation then original 202.
- Input/output and state mapping: After original SERVICE auth and full Inbox receipt returned, invoke observation then original 202.
- Error and edge behavior: No trace lookup in denial, tenant mismatch, invalid event, conflict or DB exception.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
if (environment.getProperty(FLAG, Boolean.class, false)) use unique lazy provider; else use disabled;
ProcessActor producer = access.require(existingResource);
requireSameServicePrincipal(producer);
var receipt = inbox.receive(producer, event);
observation.afterReceive();
return originalAcceptedNoStore(receipt);
```

- Verification contribution: R3/G1/P1.
- After this file: Existing receive(AgentTerminalEvent), old direct fixture constructor, selected Spring constructor is available to R3/G1/P1 in the declared source phase.

#### File 9 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservation.java`

- Purpose: Expose only real recording/local/valid/sampled IDs from this delivery.
- Symbols: Existing AS delivery plus Delivery.wireCarrier and CROSS_PROCESS_FLAG.
- Repository evidence: Current AS real Span and versioned local trace carrier.
- Dependencies and consumers: Existing handler and CL validator.
- Why now: R4 A returns null while existing SDK Link remains active.
- Contract/signature changes: Expose only real recording/local/valid/sampled IDs from this delivery.
- Input/output and state mapping: Expose only real recording/local/valid/sampled IDs from this delivery.
- Error and edge behavior: Invalid local carrier, absent provider, nonrecording, unsampled and exceptions return null.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
var deliverySpan = originalLinkedDeliveryStart(savedOrigin);
String wireCarrier() {
    if (!recordingLocalValidSampled(deliverySpan)) return null;
    return encodeAndValidateCurrentDeliveryContext(deliverySpan.getSpanContext());
}
close ends original delivery span without changing ambient Context;
```

- Verification contribution: R4/G1.
- After this file: Existing AS delivery plus Delivery.wireCarrier and CROSS_PROCESS_FLAG is available to R4/G1 in the declared source phase.

#### File 10 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandler.java`

- Purpose: Select old or optional overload only after all unchanged terminal checks and in existing delivery lifetime.
- Symbols: Existing deliver, preserved old constructors and explicit boolean opt-in.
- Repository evidence: Existing route/fingerprint/terminalReference/deadline validation.
- Dependencies and consumers: Current AS observation and concrete CL overload.
- Why now: R4 A compiles without diagnostic send, B dispatches exact span.
- Contract/signature changes: Select old or optional overload only after all unchanged terminal checks and in existing delivery lifetime.
- Input/output and state mapping: Select old or optional overload only after all unchanged terminal checks and in existing delivery lifetime.
- Error and edge behavior: No change to original receipt validation, timeout or retry classification.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
verifyOriginalRouteEventFingerprintAndTerminalReference();
try (var delivery = observation.delivery(context.traceId())) {
    var carrier = optedIn ? delivery.wireCarrier() : null;
    recheckOriginalRemainingBudget();
    var receipt = carrier == null ? client.receive(event) : client.receive(event, carrier);
    verifyExistingExactReceipt(receipt);
}
```

- Verification contribution: R4/G1.
- After this file: Existing deliver, preserved old constructors and explicit boolean opt-in is available to R4/G1 in the declared source phase.

#### File 11 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentResultDeliveryConfiguration.java`

- Purpose: Derive sender permission only when existing local observation enabled and new property true.
- Symbols: Existing handler bean and independent sender feature property.
- Repository evidence: Current AS delivery condition and unique OTel/Meter providers.
- Dependencies and consumers: New handler boolean, existing terminal-local flag remains authoritative.
- Why now: Wire flags after R4 and before packaged proof.
- Contract/signature changes: Derive sender permission only when existing local observation enabled and new property true.
- Input/output and state mapping: Derive sender permission only when existing local observation enabled and new property true.
- Error and edge behavior: Absent/false maintains current AS behavior and does not enable delivery or publisher.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11. Explicit bounded technical names and no change to existing authority, business event or storage owner.
- Implementation pseudocode:

```java
var observation = existingTerminalSpanFlag ? enabled(existingProvider) : disabled();
boolean cross = observation.isEnabled() && environment.getProperty(CROSS_PROCESS_FLAG, Boolean.class, false);
return new AgentResultDeliveryHandler(existingLedger, existingRoutes, existingClock, observation, cross);
```

- Verification contribution: G1/P1.
- After this file: Existing handler bean and independent sender feature property is available to G1/P1 in the declared source phase.

#### File 12 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Add only sender flag under agent/result-delivery and preserve local false.
- Symbols: stellar.tianji.agent.result-delivery.cross-process-span-links-enabled.
- Repository evidence: AS base YAML current local terminal span flag.
- Dependencies and consumers: Existing handler environment reads both flags.
- Why now: R1 proves missing explicit false before this hunk.
- Contract/signature changes: Add only sender flag under agent/result-delivery and preserve local false.
- Input/output and state mapping: Add only sender flag under agent/result-delivery and preserve local false.
- Error and edge behavior: No implicit terminal publication, no profile changes.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 11. One default-false base config leaf and existing production profile semantics.
- Implementation pseudocode:

```yaml
stellar.tianji.agent:
  terminal-events.span-links-enabled: false # existing
  result-delivery.cross-process-span-links-enabled: false # only new leaf
archive BOOT-INF/classes/application.yml must match approved source bytes;
```

- Verification contribution: R1/P1.
- After this file: stellar.tianji.agent.result-delivery.cross-process-span-links-enabled is available to R1/P1 in the declared source phase.

#### File 13 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Add one false leaf under existing agent-results block.
- Symbols: stellar.tianji.process.agent-results.receive-span-links-enabled.
- Repository evidence: Step82 current YAML and Source80 Inbox lag key.
- Dependencies and consumers: Existing PS controller config and package gate.
- Why now: Apply contextual hunk only after Step82 committed.
- Contract/signature changes: Add one false leaf under existing agent-results block.
- Input/output and state mapping: Add one false leaf under existing agent-results block.
- Error and edge behavior: Preserve migration-preflight and lag keys and their false values; never copy the old full file.
- Standards impact: MC-CONFIG-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 7, Rule 11. One default-false base config leaf and existing production profile semantics.
- Implementation pseudocode:

```yaml
stellar.tianji.process:
  migration-preflight.enabled: false # Step82 existing
  agent-results.lag-metrics-enabled: false # existing
  agent-results.receive-span-links-enabled: false # only new leaf
compare staged diff to clean Step82; require exactly this added leaf;
```

- Verification contribution: P0/P1.
- After this file: stellar.tianji.process.agent-results.receive-span-links-enabled is available to P0/P1 in the declared source phase.

#### File 14 — `CREATE docs/egon/spec/2026-11-05-19-19-agent-result-receive-span-links.md`

- Purpose: Reviewed bytes and exact 16-path inventory.
- Symbols: Approved REQ-001–005 and DEC-001.
- Repository evidence: Master §14.7 and existing Source77/80 scope.
- Dependencies and consumers: Approved implementation and later runtime owner.
- Why now: Mirror reviewed decisions, no edit until exact source GO.
- Contract/signature changes: Reviewed bytes and exact 16-path inventory.
- Input/output and state mapping: Reviewed bytes and exact 16-path inventory.
- Error and edge behavior: Retain pending future activation and durable S12 evidence.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves current business and storage owner.
- Implementation pseudocode:

```text
Copy accepted Spec bytes with exact canonical filename and record SHA.
Confirm DEC-001 explicitly permits both dependency scopes and exact transport exception.
Verify private receive-only design and traceability at all 16 authorized paths.
```

- Verification contribution: S1.
- After this file: Approved REQ-001–005 and DEC-001 is available to S1 in the declared source phase.

#### File 15 — `CREATE docs/egon/plan/2026-11-05-19-20-agent-result-receive-span-links.md`

- Purpose: Approved bytes and dependency order.
- Symbols: Approved Step 1, P0, R1–R4, G1, P1 and S1.
- Repository evidence: Current source reactor and Step82 permission evidence.
- Dependencies and consumers: Exact reviewed Spec and implementation owner.
- Why now: Pin source authorization and selected checks.
- Contract/signature changes: Approved bytes and dependency order.
- Input/output and state mapping: Approved bytes and dependency order.
- Error and edge behavior: Never infer readiness from a pending decision or skip/empty report.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves current business and storage owner.
- Implementation pseudocode:

```text
Copy approved Plan bytes and verify exact Spec path, digest and latest clean Step82 lock.
Check the four assertion-focused REDs, finite owner selectors and archive proof agree with source.
Stage exactly the approved 16 files for one logical commit after all gates.
```

- Verification contribution: S1.
- After this file: Approved Step 1, P0, R1–R4, G1, P1 and S1 is available to S1 in the declared source phase.

#### File 16 — `CREATE docs/egon/evidence/2026-11-05-agent-result-receive-span-links.md`

- Purpose: Archive/dependency/permissions/YAML/SQL and current failure boundaries.
- Symbols: Actual RED/green, package and scope evidence.
- Repository evidence: Current AS/PS technical EVD convention.
- Dependencies and consumers: Source reports, POM/artifact allowlist and Master review.
- Why now: Fill after actual source validation.
- Contract/signature changes: Archive/dependency/permissions/YAML/SQL and current failure boundaries.
- Input/output and state mapping: Archive/dependency/permissions/YAML/SQL and current failure boundaries.
- Error and edge behavior: Record unavailable S12/deployment evidence separately, no speculative passing counts.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves current business and storage owner.
- Implementation pseudocode:

```text
Record raw actual owning reports and the four expected assertion errors independently of the prototype.
Verify AS/PS archive YAML, exact PS OTel artifacts, packaged PaC, unchanged 27 SQL and stage paths.
State source limitations and no approval for deployment, provider, durable Inbox, Worker or S12.
```

- Verification contribution: S1.
- After this file: Actual RED/green, package and scope evidence is available to S1 in the declared source phase.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1–R4, G1, P1, S1.
- Expected result: each target RED has one assertion failure only. G1/P1 actual owning selected classes positive with zero failures, errors or skips.
- Failure returns to: Exact gate, no broader source or deployment activity.
- Completion criteria: Approved exact scope, GREEN tests, packaged false/OTel/PaC/SQL facts and staged exact paths.
- Rollback: Before commit withdraw only the authorized task changes. After commit use a separately approved focused revert. Operator rollback for later deployment is sender off then receiver off.
- Commit paths: `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpAgentResultTraceLinkTest.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandlerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxControllerTest.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentResultTraceLinkCarrier.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentResultClient.java`, `stellar-enclosure-tianji-process-server/pom.xml`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxReceiveObservation.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/AgentResultInboxController.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultSpanObservation.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/AgentResultDeliveryHandler.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/integration/process/TianjiAgentResultDeliveryConfiguration.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `docs/egon/spec/2026-11-05-19-19-agent-result-receive-span-links.md`, `docs/egon/plan/2026-11-05-19-20-agent-result-receive-span-links.md`, `docs/egon/evidence/2026-11-05-agent-result-receive-span-links.md`
- Commit: feat(observability): link accepted process agent results.

## 8. Test, Validation, and Quality Gates

| Gate | Selected test and failure mechanism | Output |
| --- | --- | --- |
| R1 | AgentResultDeliveryHandlerTest#terminalSpanLinksFlagDefaultsOffWithoutEnablingPublication, A test adds a safe map lookup for sender explicit false before source/YAML | Expected false but missing, one actual assertion failure, never null dereference |
| R2 | HttpAgentResultTraceLinkTest#adds_only_one_diagnostic_header_without_changing_the_request_or_event_fingerprint, A CL exact shared parser and concrete overload delegate the original request only | expected private header missing, one assertion failure |
| R3 | AgentResultInboxControllerTest#records_one_remote_link_only_after_durable_receive_and_keeps_the_local_parent, CL B, authorized PS dependencies and PS A helper no-op | existing 202 still returned, expected one span actual zero |
| R4 | AgentResultDeliveryHandlerTest#crossProcessCarrierUsesTheActualDeliverySpanAndRetainsTheOldOffCall, AS A wireCarrier returns null with local Links active | actual linked request overload missing, real Mockito assertion only |
| G1 | After B, all exact core selectors below | Fresh named owner reports, total 24 for prototype selectors on current test inventory, individual n>0, f=e=s=0 |
| P1 | Existing offline clean permission producer profile with core selectors plus ProcessPermissionIntegrationTest and ProcessStorageConfigurationTest | Actual jars/config/permissions/dependency/SQL proof. Exact 16 staged paths after EVD |
| S1 | Strict canonical Spec and Plan, git diff --check and scope audit | Record fresh actual results and one commit |

```sh
cross_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
cross_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
cross_baseline=af26473216d259049a4edac8b2d42b236d3a5fa7
cross_g1=HttpAgentResultTraceLinkTest,AgentResultSpanObservationTest,AgentResultDeliveryHandlerTest,TianjiAgentResultDeliveryConfigurationTest,AgentResultInboxControllerTest
cross_finite() {
  JAVA_HOME="$cross_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$cross_settings" -s "$cross_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
cross_finite 'AgentResultDeliveryHandlerTest#terminalSpanLinksFlagDefaultsOffWithoutEnablingPublication'
cross_finite 'HttpAgentResultTraceLinkTest#adds_only_one_diagnostic_header_without_changing_the_request_or_event_fingerprint'
cross_finite 'AgentResultInboxControllerTest#records_one_remote_link_only_after_durable_receive_and_keeps_the_local_parent'
cross_finite 'AgentResultDeliveryHandlerTest#crossProcessCarrierUsesTheActualDeliverySpanAndRetainsTheOldOffCall'
cross_finite "$cross_g1"
test "$(git rev-parse --verify HEAD)" = "$cross_baseline"
JAVA_HOME="$cross_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$cross_settings" -s "$cross_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$cross_baseline" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-02-receive-link-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$cross_g1,ProcessPermissionIntegrationTest,ProcessStorageConfigurationTest"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-19-19-agent-result-receive-span-links.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-19-20-agent-result-receive-span-links.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

Run each test at its prescribed source phase, not by pasting all invocations into one shell. Existing CL HttpAgentResultClientTest opens a listener and is excluded. No PostgresTest, IT, app, provider or collector selection. Treat a missing owning XML report, missing asserted failure or any unrelated compiler/bootstrap failure as gate failure.

P1 inspect actual AS and PS executable archives using existing permission producer profile and inline zipfile/unzip. Require unique BOOT-INF/classes/application.yml matching each source, sender/receiver false, AS local span-links false, PS lag false and Step82 migration-preflight false. In PS require exactly one each OTel API, Context and Common 1.62.0 as the only new OTel runtime entries; exclude SDK, exporters and bridge. Inspect actual root META-INF/permissions/manifest-v1.json for AS and PS and compare application/producer/schema and permission/resources/references/messages to pinned baseline. Preserve exact event schema, contract, route and 27 migration contents. The source wrapper and archive inspection cover every gate, so no scratch script enters the commit.

## 9. Migration, Compatibility, Rollout, and Rollback

Zero SQL or production start. Both flags false after package. Old sender and receiver remain compatible. Post-receive span can record per accepted retry and does not define first-write semantics. Future operator must prove verified provider, bounded exporter, DEAD-listener compatibility and exact proxy route forwarding, then obtain separate enablement authority. No S12 disposition change.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Proof |
| --- | --- | --- |
| REQ-001 | 1 | R4/G1 AS |
| REQ-002 | 1 | R2/G1 CL |
| REQ-003 | 1 | R3/G1 PS |
| REQ-004 | 1 | P0/R1/P1/S1 |
| REQ-005 | 1 | G1/P1/S1; independent deployment and S12 |

## 11. Risks, Blockers, and User Decisions

DEC-001 is closed by actual user approval. Exact Source83 lock and root Source GO precede changes. A one-sided or declined decision would have left the prototype off-repo. Any relevant Step82/Source83 drift reopens source review. The post-commit observer cannot influence permission, body or persisted state. A misconfigured exporter could delay the ACK, which is an independently tested deployment concern.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/5 | Existing adapters and owner modules | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 | Reuse source route, client, receipt and local observation | None |
| MC-DEP-001 | Applicable | PASS | §6/P0a | Actual user approval covers the exact PS dependency scopes | P1 proves runtime allowlist |
| MC-NAME-001 | Applicable | PASS | §5/7 | Exact technical paths and names | None |
| MC-VALID-001 | Applicable | PASS | §7/8 | Closed optional grammar and original checks | None |
| MC-MODEL-001 | Not applicable | N/A | §4.8 | No production business record | None |
| MC-CONVERT-001 | Not applicable | N/A | §4.7 | No mapping layer | None |
| MC-LOG-001 | Applicable | PASS | §7 | No raw header or new sensitive tags | None |
| MC-BEAN-001 | Applicable | PASS | §7 File 8/11 | Reuse existing configuration and unique provider | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK parser and local API | None |
| MC-JSON-001 | Not applicable | N/A | §4.8 | No schema or mapping change | None |
| MC-TIME-001 | Not applicable | N/A | §4.9 | Existing timeout/receipt semantics only | None |
| MC-CONFIG-001 | Applicable | PASS | §6/8 | Both flags false and same Step82/lag values | None |
| MC-PATTERN-001 | Applicable | PASS | §4.7/7 | Existing owner adapters | None |
| MC-SCOPE-001 | Applicable | PASS | §6/P0a | Approved exact raw path, single private header and closed source scope | Source GO before editing |
| MC-TEST-001 | Applicable | PASS | §7/8 | R1–R4, G1 and P1 exact conditions | None |
| MC-BLOCKER-001 | Applicable | PASS | §6/11 | Dual decision closed and Source83 audited | Exact Source GO before editing |

**PASS — Ready for user review**
