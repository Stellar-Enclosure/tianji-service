# CMMN Case HTTP SDK 与 Contract-First OpenAPI 实施计划

| Field | Value |
| --- | --- |
| Document | `2026-11-01-23-45-case-http-client.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-01 23:45 Asia/Shanghai` |
| Updated | `2026-11-04 15:22 Asia/Shanghai` |
| Owner | `Root source-grounded design review；原用户继续范围授权，完成后审查` |
| Repository | `tianji-service` |
| Scope | `Step36 client-only Case REST SDK/OpenAPI；exact 8 paths` |
| Source Requirement | `Root Step36 frozen consumer protocol` |
| Baseline Revision | `main@4584acb35f87f4a811596e80a54c85b8fdbf42e1；clean` |
| Implements Spec | [Case HTTP client](../spec/2026-11-01-23-45-case-http-client.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-04；SHA-256 e6a47e758b685097d3641a1e913c8baa5f249b0f76757e16da82852e14b82cd8` |
| Effective Specs | [Case HTTP client](../spec/2026-11-01-23-45-case-http-client.md), [Case Java contract](../spec/2026-11-01-23-20-case-client-contract.md) |
| Depends On Plans | [Case Java contract Plan](2026-11-01-23-20-case-client-contract.md) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

One Step owns exact eight paths。Create packaged OpenAPI resource first；create reject-all CaseResponseCodec and modify HttpProcessClient to implement CaseClient with three compile-ready UOE methods；add two listener-free tests and capture RED；then revisit codec/client uncommitted for minimum GREEN。Strict docs、focused tests、static legacy/excluded paths and Evidence close one commit。

No provider/server/controller/runtime/DB exists in this Step。本文件定义 GO 后的执行；文档准备阶段仅复核候选与合同，不执行 build。RED/GREEN、测试计数和提交结果均由 Root 实际执行后写入 Evidence，当前为未执行。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [Case HTTP client](../spec/2026-11-01-23-45-case-http-client.md)
- Status: Review
- Revision/baseline: header values
- Approval evidence: original continuation authority；Root strict/full-read/hash GO；no fake Accepted。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | Case HTTP client | Review | all | exact implementation design |
| Dependency | Case Java contract | Review/implemented | REQ-201–209, fields/time/invariants | codec/wire authority |

### 2.3 Superseded or excluded content

None。Provider、springdoc、permissions、DB and legacy listener test excluded。Step35 的 15-path/no-HTTP 边界描述已交付的 predecessor task；本 Plan 保持其 C 文件不变，新增 HTTP 的授权与 exact8 边界来自 primary Spec，不能把前一步的交付边界误解成对后续 HTTP 的禁止。

## 3. Effective Requirements and Acceptance

| Requirement | Source | Statement | Acceptance | Impact |
| --- | --- | --- | --- | --- |
| `REQ-201`, `REQ-202`, `REQ-203`, `REQ-204`, `REQ-205`, `REQ-206`, `REQ-207`, `REQ-208`, `REQ-209` | Case contract | 已交付 C fields/enums/invariants/time；仅继承不变条件 | reflection/constructor decoder proof；existing C unchanged | unchanged C dependency |
| `REQ-301` | primary | 3 POST methods/binding | fake transport | client |
| `REQ-302` | primary | transport/header guard | pre-token/header tests | client |
| `REQ-303` | primary | one-send/timeout/128KiB/no retry | fake future/subscriber | client/codec |
| `REQ-304–305` | primary | exact200/media/strict closed JSON | codec mutations | codec |
| `REQ-306` | primary | request-response correlation/terminal replay | mismatch tests | client |
| `REQ-307–308` | primary | safe errors/sync masking/legacy rethrow | paired tests | client/codec |
| `REQ-309` | primary | packaged OAS3.1 drift contract | artifact semantic test | resource/test |
| `REQ-310` | primary | Instant offset/nanos/no chronology | codec time tests | codec |
| `REQ-311–312` | primary | legacy/exact8/no broad changes | smoke/static | all |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Resource first prevents false RED from a missing fixture。Codec/client compile prerequisites precede tests。After focused RED, only Files2–3 change to final behavior。No test or artifact is rewritten merely to make GREEN。

### 4.2 Test-first strategy

| Behavior | RED | GREEN |
| --- | --- | --- |
| OAS | resource present before tests | semantic assertions pass from start |
| codec | valid decode asserts fail reject-all | strict mapper/tree/constructors |
| transport | typed methods UOE | existing adapter branches/Function decoder |
| legacy | baseline fake behavior | controlled smoke unchanged |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | Parallel | Must not overlap | Reason |
| --- | --- | --- | --- | --- |
| Step1 | Step35 contract committed + Root GO | None | exact8 paths | one protocol slice |

### 4.4 Commit boundaries

One commit: `feat(client): add governed Case HTTP SDK contract`。No RED/stub/docs-only commit。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity | Evidence | Direct alternative | Cost | Decision |
| --- | --- | --- | --- | --- | --- |
| client expansion | Spec §7 | current adapter | new client duplicates | branches | implement |
| strict codec | Spec §7/9 | legacy mapper permissive | tighten global breaks | one class | implement |
| OAS resource | Spec §9 | provider absent | generated doc unavailable | one JSON | implement |
| provider/springdoc/deps | non-goal | absent | later | broad | exclude |

### 4.6 Change-unit Dependency Matrix

| Unit | Requirements | RED/proof | Prereq | Produces | Consumer | Step |
| --- | --- | --- | --- | --- | --- | --- |
| OAS | 309 | artifact test | Step35 C | canonical resource | tests/future provider | 1 |
| codec | 304/305/307/310 | reject-all positives | Jackson/C | strict decoder | client | 1 |
| adapter | 301–303/306–308/311 | UOE methods | codec/C | CaseClient transport | SDK | 1 |
| evidence | 312 | strict/static/focused | GREEN | commit proof | Root/user | 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture | technical client Adapter | extend existing type | no module/layer | Step1 ARCH |
| Reuse | HttpClient/subscriber/Jackson/C | reuse existing | no dependency | REUSE/DEP |
| Naming/model | Codec/Test roles; no carriers | semantic types | no mapping model | NAME/MODEL |
| JSON/time | Jackson3/Instant/Duration helpers | strict Case branch | OAS/codec/time tests | JSON/TIME |
| Pattern | complex transport variation | Adapter + Function decoder | no new abstraction | PATTERN |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| HTTP/auth/timeout | HttpProcessClient | baseline source | fits, no Case methods | extend | none | 1/REUSE |
| body cap | BoundedBodySubscriber | baseline | fits selectable limit | reuse | none | 1/REUSE |
| strict JSON | generic mapper/HumanTask codec | source | pattern fits; shape absent | new codec | no dep | 1/JSON |
| OAS parse | existing Jackson test | client POM | sufficient | resource test | no tool dep | 1/DEP |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/8 | Adapter/Codec/Test | Files2–5 | semantic names | compile | 1 | PASS |
| Rule 2 | §6.2 | no Spring handoff | Files3–5 | C/client checks | tests | 1 | N/A |
| Rule 3 | §10 | no new carrier | none | no mapping | scope | 1 | N/A |
| Rule 4 | §6.2 | no Bean/business | none | no DI/log | scope | 1 | N/A |
| Rule 5 | §6.2 | JDK/Jackson existing | Files2–5 | no utility dep | POM/import | 1 | PASS |
| Rule 6 | §9 | external JSON | Files1–5 | strict Jackson/OAS | codec/OAS | 1 | PASS |
| Rule 7 | §6.2 | no config | none | no keys | scope | 1 | N/A |
| Rule 9 | §13 | Complex Adapter variation | Files2–3 | reuse Adapter/Function | branch tests | 1 | PASS |
| Rule 10 | §6.3 | Instant/Duration | Files2–5 | exact time semantics | tests | 1 | PASS |
| Rule 11 | §6.1/8 | client package | all8 | no layer/module | path | 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-301` | `CaseInstanceDTO.submittedAt/finishedAt`, `CaseOperationResult.occurredAt` | Occurred instant | `Instant` | Not persisted | UTC Z | Not applicable: no business calendar computation | Not applicable: no frontend/display | absolute offsets accepted and normalized to UTC；numeric/naive reject；ACTIVE=null finish, terminal=nonnull；no chronology/DST calculation | nanoseconds preserved；no freshness rule or generated Clock instant | unchanged Step35 C semantics；new Case-only JSON boundary | `Step 1; TEST-301/302; §8 focused command; Files1–5` |
| `TIME-302` | `HttpProcessClient.timeout`, `HttpRequest.timeout`, `CompletableFuture.get` | Duration | `Duration` | Not persisted | Internal | Not applicable: transport waiting interval | Not applicable: no frontend/display | HTTP window starts after token supplier；timeout/interruption cancels future；supplier is not bounded | positive Duration ≤Long.MAX_VALUE nanoseconds；no wall-clock calculation | shared legacy timeout helper preserved；Case branch uses same window | `Step 1; TEST-305; §8 focused command; Files3/5` |
| `TIME-303` | `ProcessClientException.retryAfter`, `HttpProcessClient.retryAfter(String)` | Duration | `Duration` | Not persisted | Internal | Not applicable: advisory retry metadata only | Not applicable: no frontend/display | nonnegative seconds/RFC1123 absolute date；negative seconds or invalid→null；past date→zero；maximum one day；no automatic retry | existing `Instant.now` for HTTP-date difference；seconds/date precision inherited | unchanged shared helper; Case exception exposes only bounded Duration metadata | `Step 1; TEST-303/306; §8 focused command; Files3/5` |

## 5. Change File Tree

```text
CREATE stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/case-v1.json
CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/CaseResponseCodec.java
MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java
CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/CaseResponseCodecTest.java
CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java
CREATE docs/egon/spec/2026-11-01-23-45-case-http-client.md
CREATE docs/egon/plan/2026-11-01-23-45-case-http-client.md
CREATE docs/egon/evidence/2026-11-01-case-http-client.md
```

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository baseline

`4584acb...` clean；shared workspace rechecked；C/PS/POM/DB/permissions untouched。

### 6.2 Build/test prerequisites

JDK25、source Maven reactor；pure fake HttpClient, no listener。Root 已恢复隔离 `/tmp/tianji-compat-fix/settings.xml`，仅指定 `/Users/mario/maven/repository` 与 noninteractive，不复制用户 credentials/mirrors。首次 RED/GREEN 使用 offline `-o`；cache/repository-ID 前置失败不算行为 RED，Root 如需调整仅修改临时 settings 并记录。不得修改全局 Maven settings 或以未执行替代测试结果。

### 6.3 Immutable constraints

Exact routes/headers/errors/caps/OAS schemas；no provider/springdoc/new deps；legacy paths unchanged；focused excludes HttpProcessClientTest。

### 6.4 Plan Clarifications

| ID | Inference | Evidence | Same semantics | Impact |
| --- | --- | --- | --- | --- |
| CLAR-301 | OAS before RED | explicit instruction | avoids fixture-only failure | order only |
| CLAR-302 | Jackson Instant authoritative | Jackson 3.1.7 accepts quoted numeric epoch | validate Instant.parse grammar before mapper conversion without manual normalization/chronology | regression needed on upgrade |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付严格 Case REST SDK 与 consumer OpenAPI authority

- Requirements: `REQ-201, REQ-202, REQ-203, REQ-204, REQ-205, REQ-206, REQ-207, REQ-208, REQ-209, REQ-301, REQ-302, REQ-303, REQ-304, REQ-305, REQ-306, REQ-307, REQ-308, REQ-309, REQ-310, REQ-311, REQ-312`
- Dependencies: Step35 commit；Root GO。
- Baseline state: C contract exists；client has no Case implementation/resource。
- Observable outcome: typed Case calls use safe strict one-send REST consumer contract；legacy smoke unchanged。
- End state: exact8 one commit；provider absent。
- Test-first gate: `Required — create OAS then reject-all codec + three UOE methods; new valid positives RED before final Files2–3.`
- Manual Checks: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/case-v1.json`
- Purpose: canonical contract-first OpenAPI3.1 resource before RED.
- Symbols: 3 paths/POST/operationIds, bearer scheme, 11 schemas, 200+8 statuses.
- Repository evidence: Step35 C fields + frozen API report.
- Dependencies and consumers: tests/future provider; packaged client JAR.
- Why now: tests must not fail due missing fixture.
- Contract/signature changes: resource only, no route.
- Input/output and state mapping: C reflection to strict JSON schemas.
- Error and edge behavior: all success required incl nullable finish；error requires code only；no fake version inequality.
- Standards impact: `MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 6, Rule 10, Rule 11`.
- Implementation pseudocode:
```json
{"openapi":"3.1.0","paths":{"/api/tianji/v1/cases/start":{"post":{"operationId":"startCase"}},
"/api/tianji/v1/cases/get":{"post":{"operationId":"getCase"}},
"/api/tianji/v1/cases/act-plan-item":{"post":{"operationId":"actOnCasePlanItem"}}},
"components":{"securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer"}},"schemas":"exact reviewed schemas"}}
```
- Verification contribution: TEST-304 resource/reflection/local-ref gate.
- After this file: artifact exists, no provider.

#### File 2 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/CaseResponseCodec.java`
- Purpose: Case-only closed success/error decoder.
- Symbols: MAX_BYTES, strict mapper, instance/operation/errorCode/tree/planItem plus Instant grammar prevalidation helpers.
- Repository evidence: HumanTask codec pattern/candidate.
- Dependencies and consumers: Jackson3/C/HttpProcessClient/tests.
- Why now: reject-all stub compiles client/tests.
- Contract/signature changes: package-private codec only.
- Input/output and state mapping: ≤128KiB bytes→strict tree→C constructors；error body→trusted code/null.
- Error and edge behavior: duplicate/unknown/trailing/coercion/numeric/required/bounds reject；correlation ignored.
- Standards impact: `MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
// RED: instance/operation throw invalid(); errorCode returns null.
// GREEN: strict Jackson mapper; tree checks body 1..128KiB/object; require all Case/item/result fields;
// validate arrays16/4 and textual scalars; Instant.parse every required submittedAt/occurredAt and nonnull finishedAt before treeToValue; C constructors enforce cross-field invariants;
// errorCode parses only code + optional correlation and returns code iff exact frozen status pair, else null.
```
- Verification contribution: TEST-301–304.
- After this file: initially RED, finally strict decoder.

#### File 3 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`
- Purpose: existing Adapter implements CaseClient without legacy drift.
- Symbols: implements list/imports/cases field/3 methods/shared `call(..., Function<byte[],T>)` Case branches/`caseError`.
- Repository evidence: existing shared call/subscriber/errors + candidate.
- Dependencies and consumers: C/codec/JDK HttpClient/tests.
- Why now: codec symbol exists.
- Contract/signature changes: CaseClient methods；no constructor/public legacy change.
- Input/output and state mapping: C body→exact path JSON；start binds definitionKey/releaseVersion/businessKey/correlationId；get binds caseInstanceId；act binds commandId/caseInstanceId/planItemInstanceId/action/previousVersion=expectedVersion。
- Error and edge behavior: secure pre-token guard、one-send、128KiB、sync mask Case only、status/media/error fallback；legacy unchanged.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-NAME-001, MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
// RED: implements CaseClient; methods throw UnsupportedOperationException.
// GREEN: methods require input, shared call POST cases/{path}, decode via Function, bind request-owned fields;
// in shared call branch cases/: guard normalized HTTPS/loopback before token, add no-store, choose 128KiB subscriber;
// require exact200 JSON/+json; mask Case sync runtime; caseError strict code/status or safe fallback, null correlation.
```
- Verification contribution: TEST-305–307 and controlled legacy smoke.
- After this file: Case SDK GREEN; old methods semantically unchanged.

#### File 4 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/CaseResponseCodecTest.java`
- Purpose: strict codec/OAS/Instant/error drift tests.
- Symbols: 7 tests + helpers.
- Repository evidence: reviewed current JUnit/Jackson candidate.
- Dependencies and consumers: Files1–2/C.
- Why now: defines codec RED after resource/stub.
- Contract/signature changes: test only.
- Input/output and state mapping: JSON/OAS→C values/assertions.
- Error and edge behavior: full mutation/required/bounds/code-pair/local-ref coverage；quoted numeric epoch must reject despite Jackson 3.1.7 coercion settings.
- Standards impact: `MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 5, Rule 6, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
assert valid active/terminal/sparse/offset-nanos and all four transitions decode;
remove each required Case/item/result field; inject duplicate/unknown/trailing/numeric/coercion/oversize;
assert nine code/status pairs and safe null fallback; parse packaged OAS and compare C record components/enums;
collect every local $ref, require schemas resolve, bearer security and exact paths/statuses.
```
- Verification contribution: focused codec RED/GREEN.
- After this file: valid positives RED on stub; OAS assertions runnable.

#### File 5 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java`
- Purpose: listener-free actual JDK BodyHandler/subscriber transport proof and legacy smoke.
- Symbols: 6 tests, FakeHttpClient/FakeResponse/standard incomplete CompletableFuture/tracking subscription/body capture.
- Repository evidence: reviewed pure JDK fake candidate；no Mockito dependency.
- Dependencies and consumers: File3/C/JDK SSL types.
- Why now: defines Adapter behavior RED.
- Contract/signature changes: test only.
- Input/output and state mapping: queued fake responses→real body handler→client results/requests.
- Error and edge behavior: guard/token order、headers/prefix/bodies、mismatch/status/media、errors/privacy、timeout/oversize cancel、sync mask vs legacy rethrow。
- Standards impact: `MC-REUSE-001, MC-DEP-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 5, Rule 6, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```java
FakeHttpClient extends JDK HttpClient and passes actual ResponseInfo to BodyHandler/BoundedBodySubscriber;
capture three request paths/bodies/headers; assert no tenant/actor/role headers and one send;
simulate terminal replay/mismatch/known+unknown errors/sync runtime/incomplete future/oversize chunks;
wrap incomplete-future call in assertTimeoutPreemptively(Duration.ofSeconds(2)); finally cancel pending future so broken get()/join() stays finite;
assert cancellation/safe exceptions and controlled Process/Decision/Human legacy smoke; never open listener.
```
- Verification contribution: focused transport RED/GREEN and legacy boundary.
- After this file: focused two-class RED ready; then Files2–3 final phase.

#### File 6 — `CREATE docs/egon/spec/2026-11-01-23-45-case-http-client.md`
- Purpose: persist V9 Complex Review contract.
- Symbols: REQ301–312/API301–303/API gates/diagrams/time/17MC/10Rules.
- Repository evidence: source/candidates/blueprint/Step35.
- Dependencies and consumers: Root/Plan.
- Why now: design gate.
- Contract/signature changes: docs only.
- Input/output and state mapping: evidence→wire/flow/tests.
- Error and edge behavior: strict failure/drift blocks; no Accepted/provider claim.
- Standards impact: all `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```text
Copy reviewed V9 draft to exact path; resolve Case/blueprint/Plan links.
Run strict validator including API gates, diagrams, jsonc and time rows; full-read all wire fields/errors/scopes.
Record SHA in Evidence; retain Review and explicit provider-unimplemented boundary.
```
- Verification contribution: strict Spec gate.
- After this file: Review design exists.

#### File 7 — `CREATE docs/egon/plan/2026-11-01-23-45-case-http-client.md`
- Purpose: persist this V5 one-Step exact8 plan.
- Symbols: effective requirements/file blocks/commands/checks.
- Repository evidence: File6/current scripts/candidates.
- Dependencies and consumers: Root/executor.
- Why now: implementation gate.
- Contract/signature changes: docs only.
- Input/output and state mapping: Spec→RED/GREEN/evidence.
- Error and edge behavior: strict/link/drift blocks.
- Standards impact: `MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`；逐项消费 File6 的适用性与证据。
- Literal rule enforcement: all `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`.
- Implementation pseudocode:
```text
Copy reviewed V5 plan after Spec; strict validate both effective Specs and all Step requirements.
Full-read OAS-before-RED, stub/final phases, fake transport, no-listener selector, exact8 and one commit.
Record Plan SHA; no code execution as Plan side effect.
```
- Verification contribution: strict Plan gate.
- After this file: Root may GO.

#### File 8 — `CREATE docs/egon/evidence/2026-11-01-case-http-client.md`
- Purpose: actual RED/GREEN/static/artifact/commit proof.
- Symbols: hashes/logs/counts/path/OAS digest/MC/Rules/boundaries.
- Repository evidence: actual outputs only.
- Dependencies and consumers: Files1–7 final.
- Why now: last, no fabricated results.
- Contract/signature changes: docs only.
- Input/output and state mapping: logs/diffs→claims.
- Error and edge behavior: no provider/runtime claim；actual counts only。
- Standards impact: `MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11`；分别记录已验证或证据支持的 N/A，不把设计 PASS 当测试结果。
- Implementation pseudocode:
```markdown
Record baseline, Spec/Plan/OAS hashes and actual two-class RED/GREEN outputs.
Record source diff, legacy constants/methods/body checks, exact8/excluded C/PS/POM/DB/permissions hashes.
Record post-commit git-log lookup and no provider/listener/DB/browser/network/live API boundary.
```
- Verification contribution: closes evidence.
- After this file: one commit ready.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`
- Verification command: §8 focused/strict/static commands.
- Expected result: RED only missing codec/UOE behavior；GREEN tests exit0；exact8/no excluded drift。
- Failure returns to: resource/codec/client/test/docs owner or Spec on protocol drift。
- Completion criteria: all effective REQs actual evidence、17MC/API gates closed、one commit、provider boundary explicit。
- Rollback: revert one commit；no data/provider rollback。
- Commit paths: `stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/case-v1.json`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/CaseResponseCodec.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/CaseResponseCodecTest.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/HttpCaseClientTest.java`, `docs/egon/spec/2026-11-01-23-45-case-http-client.md`, `docs/egon/plan/2026-11-01-23-45-case-http-client.md`, `docs/egon/evidence/2026-11-01-case-http-client.md`。
- Commit: `feat(client): add governed Case HTTP SDK contract`

## 8. Test, Validation, and Quality Gates

Focused selector only: `CaseResponseCodecTest,HttpCaseClientTest`。Do not run `HttpProcessClientTest` in focused phase because it opens a real local listener。Root 在 RED 和 GREEN 使用同一有限命令，仅切换 Files2–3 的 stub/final 状态；当前均未执行：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipTests=false -Dtest=CaseResponseCodecTest,HttpCaseClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

执行前确认 §6.2 的 settings/JDK/source reactor 前置条件；使用实际临时 settings 时仅替换上述两个路径并记录原命令。现有 JUnit、source-reactor、Git、Python JSON 与 skill validators 覆盖全部门禁，无新增 scratch harness。

| Gate | CWD | Command/method | Scope | Expected | Return | Boundary |
| --- | --- | --- | --- | --- | --- | --- |
| RED | tianji-service | source reactor focused selector with stubs | two tests | valid positives fail missing behavior；OAS fixture present | Files1–5 | module/no listener |
| GREEN | same | same selector final | codec/transport/OAS/legacy smoke | exit0 actual counts | Files1–5 | fake only |
| Spec strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py tianji-service/docs/egon/spec/2026-11-01-23-45-case-http-client.md --strict` | V9/API | PASS | File6 | docs |
| Plan strict | repo root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-11-01-23-45-case-http-client.md --strict` | V5 | PASS | File7 | docs |
| Static | tianji-service | `git diff --check`; `git status --short`; `git diff --name-only 4584acb35f87f4a811596e80a54c85b8fdbf42e1`; `python3 -m json.tool stellar-enclosure-tianji-client/src/main/resources/META-INF/tianji/openapi/case-v1.json`；baseline hashes | exact8/exclusions | no C/PS/POM/DB/permission drift | owner | static |
| Live | user-controlled | not authorized | provider/network | not run | future | unvalidated |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration/config/permission/dependency。Client artifact additive；legacy branches preserved。Provider rollout deferred；response no-store requirement not enforced client-side。Rollback one commit。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates | Evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-201` | Case Spec §4/9 | Step 1 | 1–5 context | reflection/codec | unchanged exact C types |
| `REQ-202` | Case Spec §4/10 | Step 1 | 1–5 context | reflection/request/codec | existing constructors/forbidden fields |
| `REQ-203` | Case Spec §4/10 | Step 1 | 1/2/4 | codec constructor boundary | action/state subsets unchanged |
| `REQ-204` | Case Spec §4/10 | Step 1 | 1/2/4 | snapshot constructor boundary | list/terminal invariants unchanged |
| `REQ-205` | Case Spec §4/10 | Step 1 | 1/2/4 | four-transition decode | receipt invariants unchanged |
| `REQ-206` | Case Spec §4/10 | Step 1 | 2/4 context | same C record decode | no new carrier |
| `REQ-207` | Case Spec §4/6.3 | Step 1 | 1/2/4 | TEST-301/302 | existing Instant/null contract |
| `REQ-208` | Case Spec §4/14 | Step 1 | 1/3/4/5 | reflection/request/error | finite fields and enums unchanged |
| `REQ-209` | Case Spec §4/16 | Step 1 | all8 context | Git baseline/excluded hashes | prior 15 C-task paths preserved |
| `REQ-301` | Primary §4/7/9 | Step 1 | 3/5 | TEST-305/306 | exact requests |
| `REQ-302` | Primary §4/7/9 | Step 1 | 3/5 | TEST-305 | pre-token/header tests |
| `REQ-303` | Primary §4/7/9 | Step 1 | 3/5 | TEST-305 | one-send/cancel/body cap |
| `REQ-304` | Primary §4/9 | Step 1 | 2/3/4/5 | TEST-301/302/306 | closed success/status/media |
| `REQ-305` | Primary §4/9 | Step 1 | 2/4 | TEST-302 | strict JSON mutations |
| `REQ-306` | Primary §4/9 | Step 1 | 3/5 | TEST-301/306 | all correlation fields/terminal replay |
| `REQ-307` | Primary §4/7/9 | Step 1 | 2/3/4/5 | TEST-303/306 | exact pairs/safe fallback/privacy |
| `REQ-308` | Primary §4/7/9 | Step 1 | 3/5 | TEST-306/307 | Case mask/legacy rethrow |
| `REQ-309` | Primary §4/9 | Step 1 | 1/4 | TEST-304 | OAS artifact/refs/security |
| `REQ-310` | Primary §4/6.3 | Step 1 | 1/2/4 | TEST-301/302 | time precision/offsets |
| `REQ-311` | Primary §4/16 | Step 1 | 3/5 | TEST-307/static | legacy smoke/constants/error helper |
| `REQ-312` | Primary §4/16 | Step 1 | all8 | Git scope/static | exact8/excluded paths |

## 11. Risks, Blockers, and User Decisions

| ID | Risk | Files | Action | Status |
| --- | --- | --- | --- | --- |
| R1 stubs committed | 2/3 | final behavior/source review | 待 GREEN 实证关闭 |
| R2 provider inferred | 1/6/8 | explicit boundary | closed |
| R3 legacy drift | 3/5 | controlled smoke/static | 待实际 gate 关闭 |
| R4 shared workspace | all | recheck/no revert | executor action |
| R5 offline cache/settings | validation only | Root restored isolated settings; offline first；cache failure is prerequisite failure, not behavioral RED | execution prerequisite |

No open consumer design blocker。Provider remains later scope。§6.2 的临时 settings 已由 Root 恢复；offline cache 仍是执行前置条件，尚不代表 Maven 或任何 focused test 已通过。

## 12. Review and Acceptance

### 12.1–12.4 Fidelity/executability

Plan maps every field/route/error/time/limit/OAS/legacy rule into exact files and listener-free gates。No unapproved provider/dependency appears。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | client Adapter tree | preserved | None |
| MC-REUSE-001 | Applicable | PASS | ledger | existing transport/subscriber/Jackson/C | None |
| MC-DEP-001 | Applicable | PASS | POM unchanged | no dep | None |
| MC-NAME-001 | Applicable | PASS | exact types | semantic | None |
| MC-VALID-001 | Applicable | PASS | C/client/codec checks | consumer boundary covered | None |
| MC-MODEL-001 | Not applicable | N/A | no carrier | unchanged C | None |
| MC-CONVERT-001 | Not applicable | N/A | direct same types | no mapper | None |
| MC-LOG-001 | Not applicable | N/A | no logs | absent | None |
| MC-BEAN-001 | Not applicable | N/A | no Spring Bean | absent | None |
| MC-UTIL-001 | Applicable | PASS | JDK/Jackson | current deps | None |
| MC-JSON-001 | Applicable | PASS | strict codec/OAS | closed wire | None |
| MC-TIME-001 | Applicable | PASS | §4.9/tests | exact semantics | None |
| MC-CONFIG-001 | Not applicable | N/A | no config | unchanged | None |
| MC-PATTERN-001 | Applicable | PASS | Adapter/Function | no new abstraction | None |
| MC-SCOPE-001 | Applicable | PASS | exact8 | no provider/broad edit | None |
| MC-TEST-001 | Applicable | PASS | §7/8/API gates | exact future proof | Execute after GO |
| MC-BLOCKER-001 | Applicable | PASS | all rows | no open blocker | None |

### 12.6 Final verdict

`PASS — Ready for user review`

## Java / CQE contract review

Existing C Command/Query/DTO/Result types reused；no new carrier/PO/persistence/DDL/Event/outbox。REST roles explicitly Command/Query without extra CQE infrastructure。

## Backend generation and dependency evidence

N/A：no DDL/CRUD/generator/new dependency。OAS is reviewed resource, not generated provider code。
