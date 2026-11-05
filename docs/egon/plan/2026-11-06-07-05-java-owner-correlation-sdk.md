# T4-02 Java SDK 所有者关联观察计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:05 Asia/Shanghai |
| Updated | 2026-11-06 07:05 Asia/Shanghai |
| Owner | Tianji Java SDK |
| Repository | tianji-service |
| Scope | Two contract capabilities, existing client adapters, one codec and one finite test |
| Source Requirement | Master T4-02 and S12 |
| Baseline Revision | a0b7884ef1a8574f219101d4b66f0f8c36a0c52a |
| Implements Spec | [Owner observation SDK Spec](../spec/2026-11-06-07-04-java-owner-correlation-sdk.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:04 Asia/Shanghai SHA-256 2dc7976e3b9f21556aade4dcf746010de202b20fb99fc0c97657f542e42a50e4 |
| Effective Specs | [Owner observation SDK Spec](../spec/2026-11-06-07-04-java-owner-correlation-sdk.md) |
| Depends On Plans | [Agent correlation](2026-11-05-21-53-owned-agent-correlation-filter.md), [Process correlation](2026-11-05-20-37-owned-process-correlation-lookup.md), [Process Agent outcomes](2026-11-05-22-42-process-owned-agent-outcomes.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Java SDK 复用已发布的 Agent 精确关联 LIST、Process START 回执关联 LIST 和单实例 Agent outcome GET，直接返回现有有限 DTO。三个读取只接受当前服务凭据和已发布的封闭响应。

## 2. Target Spec and Effective Design

[Owner observation SDK Spec](../spec/2026-11-06-07-04-java-owner-correlation-sdk.md) §§4/7/9/14 governs this Step. Existing AS/PS routes are the authority for the wire response and identity.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Three exact typed GET methods and no body or caller-supplied owner |
| REQ-002 | §7/14 | Bounded closed decode, ownership echo and safe failure |
| REQ-003 | §8/16/18 | Existing ordinary Agent list and Process receipt regression, operator evidence separate |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock baseline and routes. Add contract and source files, perform exact RED and restore GREEN, then stage the exact paths and commit.

### 4.2 Test-first strategy

R1 removes only Process exact correlation echo equality. The first mismatched-echo case must fail because no exception was thrown. Restore the candidate and rerun the finite suite.

### 4.3 Sequential and parallel boundaries

Execute R1 before owning G1. S1 checks strict review documents and exact paths. Actual I1 is independently selected by the operator.

### 4.4 Commit boundaries

One commit for two capability interfaces, a codec, two adapters, one test and three documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Three typed observation methods | Required §7/9 | EVD-001–004 | Use existing query records and transport | Add two optional capabilities |
| Closed and bounded response | Required §7/14 | EVD-001–005 | Generic codec lacks exact shape and owner echo | One strict codec |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Typed owner observations | REQ-001/002 | R1/G1 | 1–6 | Existing AS/PS GET and DTO |
| Review evidence | REQ-003 | S1 | 7–9 | Exact outputs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Boundary | Existing typed DTO and JDK HTTP Adapter | Reuse current owner transport and validate the wire |
| Dependencies | Existing Jackson 3 and JUnit | Reuse without dependency changes |
| Service ownership | Existing protected controllers | Keep service and persisted data scopes unchanged |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| HTTP operation | HttpAgentClient and HttpProcessClient | EVD-004 | Bearer, deadline and subscriber fit | Extend | Two local adapters, no dependency | G1 |
| Closed wire | Current OAS and ProcessStartReceiptResponseCodec | EVD-001–005 | Need owner echo and page-specific shape | Reuse conventions | One codec, no dependency | R1/G1 |
| Typed mapping | Existing request and page records | EVD-001–003 | Existing validation and types fit | Reuse | Two capability interfaces | G1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–6 | Use the existing contract and client packages and semantic capability/codec names. | R1/G1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–6 | Reuse typed query/DTO constructor boundaries and add exact wire validation at transport. | R1/G1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–6 | Reuse immutable DTO records. New types are transport capabilities and one non-model codec. | R1/G1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–6 | No new business class, Spring bean, injection or logging. | R1/G1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–6 | Reuse JDK URI/charset/time and existing bounded body subscriber. | R1/G1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–6 | Use existing Jackson 3 and enum text with exact scalar and object shape. | R1/G1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–6 | No environment configuration changes. | R1/G1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–6 | Reuse the HTTP Adapter and direct closed codec. | R1/G1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–6 | Use Instant and a precise half-open UTC window, with Duration for existing timeout. | R1/G1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–6 | Retain the established contract/client split. No database or Event path is affected. | R1/G1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive, createdAt, submittedAt and Process correlation observedAt | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Inclusive lower, exclusive upper; maximum 31-day query | Microseconds; caller supplies bounds; Process clock witnesses observedAt | Reuse existing correlation queries and page | Step 1 TEST-001/002 |
| TIME-002 | Agent deadlineAt/finishedAt and Process outcome timestamps | Occurred instant | java.time.Instant and OffsetDateTime decode | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Existing Process outcome temporal order via DTO constructor | Up to nanoseconds within the shipped format | Existing DTO and owner projection preserved | Step 1 TEST-001/002 |
| TIME-003 | Existing request timeout | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Existing request deadline and cancellation | Existing JDK transport | Existing constructor policy preserved | Step 1 TEST-001 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRunCorrelationClient.java` |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessOwnerObservationClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/OwnerObservationResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-java-owner-correlation-sdk.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline a0b7884ef1a8574f219101d4b66f0f8c36a0c52a. Use the existing offline JDK25 reactor setup and non-listening selectors only. Preserve source OAS, permissions, dependencies, configuration and migrations.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Expose exact protected owner observations in the Java SDK

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing protected AS/PS controllers, contract records and client transport.
- Baseline state: These three owner observations are available over GET and absent in the Java clients.
- Observable outcome: Three exact typed calls, closed response decode and preserved ordinary methods.
- End state: Six source paths, three documents and only finite source evidence.
- Test-first gate: Required. R1 fails one exact mismatched Process echo case.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRunCorrelationClient.java`

- Purpose: Agent exact correlation capability.
- Symbols: AgentRunCorrelationClient#listByCorrelation.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Accept existing ListAgentRunsByCorrelationQuery with validated exact owner correlation filters.
Return the existing immutable AgentRunPageDTO with bounded run summaries.
Keep AgentClient contract unchanged.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessOwnerObservationClient.java`

- Purpose: Process owner observation capability.
- Symbols: ProcessOwnerObservationClient#listByCorrelation and #getAgentOutcomes.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Expose listByCorrelation with existing typed correlation query.
Expose getAgentOutcomes with existing typed exact instance query.
Return existing Process owner page records.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 3 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/OwnerObservationResponseCodec.java`

- Purpose: Shared strict owner response codec.
- Symbols: OwnerObservationResponseCodec.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Gate exact HTTP status, media and cache.
Validate strict JSON structure, types, bounds and error codes.
Bind response echo and time to request.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 4 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java`

- Purpose: Existing Agent transport.
- Symbols: HttpAgentClient#listByCorrelation.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Validate origin before reading credentials.
Encode the existing LIST correlation branch without limit.
Keep the ordinary LIST transport path and decoder.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 5 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`

- Purpose: Existing Process transport.
- Symbols: HttpProcessClient#listByCorrelation and #getAgentOutcomes.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Select strict observation only for the two new page types.
Encode the exact existing GET paths.
Keep the previous START receipt behavior.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 6 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java`

- Purpose: Finite in-memory transport tests.
- Symbols: OwnerObservationHttpTest.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Additive SDK capability or scoped implementation.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Exercise the actual SDK and bounded subscriber through fake HttpClient.
Assert one call and strict response outcomes.
Mutate one Process correlation predicate for R1.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 7 — `CREATE docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md`

- Purpose: Freeze Step contract.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Enumerate exact existing routes and bounds.
Trace requirements to closed response predicates.
Keep independent runtime evidence pending.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 8 — `CREATE docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md`

- Purpose: Freeze Step sequence.
- Symbols: R1/G1/S1.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock the exact Service baseline revision and all nine scoped paths.
Run R1 and the finite owner gate.
Compare the reviewed exact staged path list and zero permission, configuration and migration change.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.

#### File 9 — `CREATE docs/egon/evidence/2026-11-06-java-owner-correlation-sdk.md`

- Purpose: Record observed result.
- Symbols: Validation evidence.
- Repository evidence: Spec EVD-001–005.
- Dependencies and consumers: Existing Java SDK, source reviewer and later S12 operator.
- Why now: Establish Java operator client capability before an independent S12 run.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Follow existing typed query and owner observation response.
- Error and edge behavior: Exact route failures expose only a sanitized code and HTTP status.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record exact finite source counts.
Record defensive handling of malformed intermediary responses and the current exact Process owner-read security contract.
Keep actual runtime evidence separate.
```

- Verification contribution: R1/G1/S1.
- After this file: Keep the Step scope exact.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/S1.
- Expected result: Exact intended RED, sixteen finite GREEN cases, strict review and exact scope.
- Failure returns to: Files 1–6 for source checks or Files 7–9 for review/evidence checks.
- Completion criteria: All source gates pass and independent runtime evidence stays pending.
- Rollback: Revert one Java SDK commit.
- Commit paths: `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRunCorrelationClient.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessOwnerObservationClient.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/OwnerObservationResponseCodec.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java`, `docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md`, `docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md`, `docs/egon/evidence/2026-11-06-java-owner-correlation-sdk.md`.
- Commit: feat(client): expose protected owner observation queries.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | OwnerObservationHttpTest#rejects_process_echo_mismatch_windows_bad_receipts_and_impossible_outcomes | Exact foreign-correlation case fails with expected exception absent after removing equality guard |
| G1 | Owning offline reactor | 10 owner observations and 6 existing START receipt cases, 16 successes, zero failures/errors/skips |
| P1 | Clean permission-producer and standalone JAR comparison | Same 16 finite cases, exact new client and contract classes, unchanged semantic AS/PS declarations |
| S1 | Strict documents and stage | Nine paths, source wire and scope untouched |
| I1 | Independent protected runtime S12 | Exact approved owner and service environment produce actual protected responses |

```sh
observation_base=a0b7884ef1a8574f219101d4b66f0f8c36a0c52a
observation_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse --verify HEAD)" = "$observation_base"
"$observation_runner" 'OwnerObservationHttpTest,ProcessStartReceiptHttpTest'
STELLAR_VERIFY_GOAL=verify "$observation_runner" 'OwnerObservationHttpTest,ProcessStartReceiptHttpTest' clean -Ppermission-producer -Dpermission.commit="$observation_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-java-owner-correlation-sdk
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-04-java-owner-correlation-sdk.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-05-java-owner-correlation-sdk.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '*.yaml' '*.yml' '**/pom.xml' pom.xml '*/src/main/resources/META-INF/tianji/openapi/*'
git diff --cached --check
git diff --cached --name-status
```

Perform R1 with the exact mutation in a scratch compile or restore the verified candidate before G1. Require owning XML case counts. Use only the two finite SDK test classes. Independent I1 is outside this source gate.

## 9. Migration, Compatibility, Rollout, and Rollback

The capabilities add Java source interfaces. Existing public methods and service contracts retain their previous behavior. Revert one focused commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–6 | G1 |
| REQ-002 | Step 1 | 3–6 | R1/G1 |
| REQ-003 | Step 1 | 4–9 | G1/S1 |

## 11. Risks, Blockers, and User Decisions

Generic Process security can emit code-only error JSON. The SDK reports its received HTTP status with TIANJI_INVALID_RESPONSE because the published Process envelope requires explicit null correlationId. Actual IAM, protected HTTP and full S12 remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Keep contract interfaces and the existing JDK HTTP client module boundary. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Use existing routes, typed queries, DTO constructors, bearer supplier and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use existing JDK, Jackson 3 and JUnit dependencies. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Use transport capability interfaces, a package-local codec and an exact test class. | None |
| MC-VALID-001 | Applicable | PASS | §7/9/14 | Use query constructors and a closed media, header, JSON, owner echo and time validation boundary. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Return the existing immutable records and enum values. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No new layer mapping or business Converter. The codec decodes an exact wire object. | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new log or retained remote error data. | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | No new Spring bean or dependency injection. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Use JDK URI, UTF-8, java.time and existing Jackson codecs. | None |
| MC-JSON-001 | Applicable | PASS | §7/9/14 | Use exact shape, type, depth, number, token and byte bounds. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Use java.time UTC microsecond correlation windows with half-open boundary. | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration file or feature flag change. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse existing Adapter and direct validation of these three closed responses. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Only six source paths and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Test fake transport behavior and Process correlation echo mutation. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual protected S12 runtime evidence is independently pending. | None |

### 12.6 Final verdict

PASS — Ready for user review
