# T4-03 精确 Process START 收据 Java SDK 计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:06 Asia/Shanghai |
| Updated | 2026-11-06 05:06 Asia/Shanghai |
| Owner | Tianji contract and client |
| Repository | tianji-service |
| Scope | Four Java files and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | 52dd82be9b4831c5e3412bf63f0e2c279a0c7dfe |
| Implements Spec | [Receipt SDK Spec](../spec/2026-11-06-05-05-process-start-receipt-sdk.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:05 Asia/Shanghai SHA-256 ca4f2cddcf57c6b1aabd219f16fa73b454dbb0b0ba76929a071f69c61c15439e |
| Effective Specs | [Receipt SDK Spec](../spec/2026-11-06-05-05-process-start-receipt-sdk.md) |
| Depends On Plans | [Existing Process receipt](2026-11-05-23-51-owned-process-start-receipt.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

复用已发布的 Process START 精确收据 Query，给 Java 客户端增加只读取已应用证据的入口。无匹配、错配或不合法响应都不能触发重发。

## 2. Target Spec and Effective Design

[Receipt SDK Spec](../spec/2026-11-06-05-05-process-start-receipt-sdk.md) §§4/7/9/14 controls this Step. The landed Process OAS and current owner guard supply the existing wire and authorization contract.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/9 | One exact credential-bound GET to the existing endpoint; no alternate transport |
| REQ-002 | §6.3/7/9/14 | Only closed bounded 200/JSON/no-store APPLIED becomes positive proof |
| REQ-003 | §5/7/14/16 | Safe exact errors, typed 404 inconclusive, existing consumers compatible, seven paths |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Verify the current owner contract and clean baseline. Add the exact finite test and minimal candidate with duplicate guard disabled. Run R1, restore the single guard, run G1 and clean P1, then record observed evidence before staging.

### 4.2 Test-first strategy

R1 removes only the duplicate-field guard in the new codec; its targeted test must see exactly one missing ProcessClientException when duplicate status is accepted. Compilation or unrelated failures do not count.

### 4.3 Sequential and parallel boundaries

Run the exact owning reactor gates sequentially. Package and static audit read outputs after the corresponding test gate. No application, listener, IAM, database or external runtime is needed.

### 4.4 Commit boundaries

One focused source commit. Do not include unrelated AS, Process, Web, SQL, permission or configuration files.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Receipt Query | Required §7/9 | EVD-001/002 | Existing owner GET | Reuse shipped contract |
| Closed wire | Required §7/14 | EVD-002/003 | Generic permissive codec | Add one scoped decoder |
| Bounded transport | Required §7/14 | EVD-003 | Parallel client | Extend existing JDK adapter |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| SDK and finite test | REQ-001/002/003 | R1 duplicate guard and G1 | 1–4 | Existing Process OAS |
| Review and evidence | REQ-003 | Strict S1 and P1 | 5–7 | Exact owning outputs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Contract and client modules | Add engine-neutral interface and existing adapter branch |
| Validation | Existing immutable Query and DTO | Use their invariants plus closed JSON checks |
| Runtime scope | Existing owner guard and read opt-in | SDK consumes supplied authentication only |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Secure HTTP call | HttpProcessClient and BoundedBodySubscriber | EVD-003 | Existing adapter owns transport | Reuse GET, timeout and response buffering | Existing classes plus one branch | G1/P1 |
| Receipt evidence | Existing Process Query/DTO and OAS | EVD-001/002 | No Java port or strict decoder | Add one receipt capability | No dependency | R1/G1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | Files 1–4 | Reuse existing Query and DTO with a named client port. | R1/G1/P1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | Files 1–4 | Validate closed request and response through existing records and wire checks. | R1/G1/P1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | Files 1–4 | Reuse immutable value records without introducing a mutable model or converter. | R1/G1/P1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | Files 1–4 | No Spring Bean, injected business dependency or logging. | R1/G1/P1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | Files 1–4 | Reuse JDK HTTP and the current Jackson transport library. | R1/G1/P1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | Files 1–4 | Require exact scalar JSON and the APPLIED enum token. | R1/G1/P1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | Files 1–4 | No runtime property or profile changes. | R1/G1/P1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | Files 1–4 | Reuse the existing HTTP Adapter and one exact Query. | R1/G1/P1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | Files 1–4 | Decode OAS UTC microseconds to the existing Instant field. | R1/G1/P1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | Files 1–4 | Keep contract and client ownership, with no persistence or event change. | R1/G1/P1 | Step 1 | PASS |


### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ProcessStartReceiptDTO.recordedAt | Occurred instant | java.time.OffsetDateTime and Instant | UTC instant | UTC Z | Explicit UTC | No SDK display | Gregorian year 0001–9999; no offset, leap second or 24:00 coercion | Up to six fractional digits from PS receipt, no local clock substituted | Existing exact receipt creation instant; no commit-clock claim | Step 1 TEST-001/002 |
| TIME-002 | HttpProcessClient.timeout | Duration | java.time.Duration and JDK timed Future | Not persisted | Internal | Not applicable: elapsed client timeout | Not applicable: no SDK display | Existing positive request deadline and timeout cancellation | Nanosecond elapsed bound | Existing client timeout semantics | Step 1 TEST-001 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptHttpTest.java` |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-process-start-receipt-sdk.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 52dd82be9b4831c5e3412bf63f0e2c279a0c7dfe, JDK25 and existing local offline reactor settings. Review exact shipped Process receipt OAS. The SDK has no access-policy activation, storage mutation, new IAM grant, POM or SQL change.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the exact owned Process START receipt Java Query

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing receipt OAS, Query, DTO, Process READ guard and current client.
- Baseline state: No Java-owned START receipt client exists.
- Observable outcome: Caller can read the exact positive proof with current existing READ credential.
- End state: Exact seven paths pass owning finite and artifact checks.
- Test-first gate: Required. Typed R1 and G1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptHttpTest.java`

- Purpose: Lock the in-memory receipt wire tests.
- Symbols: ProcessStartReceiptHttpTest.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: No existing Java receipt request test.
- Contract/signature changes: Only finite fake JDK HTTP.
- Input/output and state mapping: Supply exact status, headers and raw bytes through the actual BodySubscriber.
- Error and edge behavior: Verify exact owner GET and reject alternate response shape without fallback.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var receipt = fixture.reply(200, exactHeaders, knownAppliedJson);
assert client.getStartReceipt(query) equals receipt; assert exactEncodedGetAndNoBody();
assert malformedJsonAndUnsafeHeadersRaiseOnlySanitizedProcessClientException();
```

- Verification contribution: Typed R1 and all six G1 cases.
- After this file: Continue within the exact seven-path Step.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptClient.java`

- Purpose: Add the independent Query capability.
- Symbols: ProcessStartReceiptClient#getStartReceipt.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Existing ProcessClient owns START and instance reads.
- Contract/signature changes: One additive interface, existing DTO signature.
- Input/output and state mapping: Reuse the existing commandId and digest query; no owner or token fields.
- Error and edge behavior: Document misses as inconclusive and no authorization for POST.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
interface ProcessStartReceiptClient exposes getStartReceipt(GetProcessStartReceiptQuery);
return existing ProcessStartReceiptDTO under supplied current owner credential;
describe READ_SCOPE, exact resource key and one GET path; document inconclusive misses;
```

- Verification contribution: Existing contract plus API presence.
- After this file: Continue within the exact seven-path Step.

#### File 3 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptResponseCodec.java`

- Purpose: Decode receipt and exact negative evidence.
- Symbols: ProcessStartReceiptResponseCodec#receipt/#errorCode.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Existing worker codec precedent and shipped receipt OAS.
- Contract/signature changes: Private static decoder, no new DTOs.
- Input/output and state mapping: Read only exact JSON fields and map the existing APPLIED status.
- Error and edge behavior: Reject invalid UTF-8, duplicate/trailing/extra, malformed time and mismatched status/code.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var root = readStrictUtf8JsonTree(bytes, maxBytes = 4096);
require exactThreeScalarFieldsAndAppliedStatus(root); convertUtcMicrosToInstant(root.recordedAt);
accept documented errorCode only when HTTP status matches and correlationId is null;
```

- Verification contribution: R1 exact duplicate failure and G1 receipt fields.
- After this file: Continue within the exact seven-path Step.

#### File 4 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`

- Purpose: Send one exact existing GET.
- Symbols: HttpProcessClient#getStartReceipt/#call.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Existing bounded JDK HTTP adapter and response subscriber.
- Contract/signature changes: Add the receipt interface to the existing implementation.
- Input/output and state mapping: Percent-encode original command, send lowercase digest, pass supplied credential and exact no-store.
- Error and edge behavior: Require normalized HTTPS or loopback, reject redirects, bound response buffering and refuse undocumented status/media/cache.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
requireNormalizedHttpsOrLoopbackBeforeReadingSuppliedBearer();
var uri = encodeExactCommandAndDigestForExistingGet(query);
use boundedSubscriber(4096); require exact no-store and JSON; return only strict applied receipt;
```

- Verification contribution: G1 and P1 existing adapter regression.
- After this file: Continue within the exact seven-path Step.

#### File 5 — `CREATE docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md`

- Purpose: Freeze the source-facing transport design.
- Symbols: SDK Spec §§4/7/9/14.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Existing route Spec and exact shipped OAS.
- Contract/signature changes: Reviewer design only.
- Input/output and state mapping: Describe one Query using current READ and immutable proof.
- Error and edge behavior: Distinguish no match, invalid wire and source/runtime gates.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
state existing CurrentProcessReadGuard and original owner as sole authorization;
describe SDK exact two-field Query and closed success/failure mapping;
trace bounded transport, existing UTC contract and no-effect-proof limits to the finite cases;
```

- Verification contribution: Strict Spec and Scope.
- After this file: Continue within the exact seven-path Step.

#### File 6 — `CREATE docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md`

- Purpose: Freeze the exact implementation and gates.
- Symbols: SDK Plan §§7–8.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Approved master scope and locked parent.
- Contract/signature changes: Reviewer plan only.
- Input/output and state mapping: Record R1, G1, P1, exact paths and rollback.
- Error and edge behavior: Fail on scope drift or unrelated failure.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
pin baseline commit and strict Spec SHA-256 in the header;
run typed duplicate-guard variant and then restore exact candidate for finite reactor;
inspect actual producer classes and compare staged files, YAML, SQL and manifests;
```

- Verification contribution: Strict Plan and Scope.
- After this file: Continue within the exact seven-path Step.

#### File 7 — `CREATE docs/egon/evidence/2026-11-06-process-start-receipt-sdk.md`

- Purpose: Record observed candidate validation.
- Symbols: SDK evidence.
- Repository evidence: Spec EVD-001–004 and exact Service parent.
- Dependencies and consumers: Existing contract and SDK owners, Process receipt route and source reviewer.
- Why now: Isolated finite evidence and owning reactor artifacts.
- Contract/signature changes: Reviewer evidence only.
- Input/output and state mapping: Record exact counts, typed RED and artifact checks as observed.
- Error and edge behavior: Leave external IAM and historical receipt writeback pending.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
record actual selected method and missing ProcessClientException from typed variant;
record exact successful counts, relevant package classes and Process OAS bytes;
keep IAM, deployed HTTPS and historical action writeback outside finite evidence;
```

- Verification contribution: Review and staging.
- After this file: Continue within the exact seven-path Step.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact owner selectors and artifact/scope review.
- Expected result: R1 fails once for duplicate status. G1 and P1 pass expected 50 finite tests across six suites without skips.
- Failure returns to: Exact typed mutation or affected wire branch. Recheck clean baseline for unrelated drift.
- Completion criteria: Exact gate observations, strict docs, seven exact staged paths and immutable SQL.
- Rollback: Revert this one source commit.
- Commit paths: `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptHttpTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessStartReceiptClient.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/ProcessStartReceiptResponseCodec.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`, `docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md`, `docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md`, `docs/egon/evidence/2026-11-06-process-start-receipt-sdk.md`.
- Commit: `feat(client): read exact owned process start receipts`.

## 8. Test, Validation, and Quality Gates

| Gate | Phase | Expected |
| --- | --- | --- |
| R1 | Remove only strict duplicate detection | One expected missing ProcessClientException; zero compile failures and skips |
| G1 | Owning offline reactor | 50 finite tests across six suites; six new client protocol cases |
| P1 | Clean owning producer | Same finite result, actual SDK classes and unchanged Process OAS, SQL, configs and semantic PaC |
| S1 | Strict docs and exact diff | Seven scoped paths, no runtime permission activation |

```sh
sdk_base=52dd82be9b4831c5e3412bf63f0e2c279a0c7dfe
sdk_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
sdk_red='ProcessStartReceiptHttpTest#rejects_extra_duplicate_or_invalid_receipt_fields'
sdk_finite='ProcessStartReceiptHttpTest,HttpProcessControlClientTest,ProcessWorkerRecoveryHttpTest,HttpCaseClientTest,OrchestrationContractTest,ProcessStartReceiptControllerTest'
test "$(git rev-parse --verify HEAD)" = "$sdk_base"
# R1 after applying only the typed strict-duplicate mutation
"$sdk_runner" "$sdk_red"
# Restore the exact B candidate before G1 and P1
"$sdk_runner" "$sdk_finite"
STELLAR_VERIFY_GOAL=verify "$sdk_runner" "$sdk_finite" clean -Ppermission-producer   "-Dpermission.commit=$sdk_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-process-start-receipt-sdk
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-05-process-start-receipt-sdk.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-06-process-start-receipt-sdk.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml '**/application.yml'
git diff --cached --check
git diff --cached --name-status
```

Inspect actual contract and client archives plus packaged Process OAS equality. Verify receipt client request parity with exact Process GET, parameters, scope, operation ID and six documented errors. Compare real production configs and semantic permission manifests after normalizing build metadata. An actual deployed Process route, original executor READ credential and Agent historical authorization remain separate.

## 9. Migration, Compatibility, Rollout, and Rollback

Additive Java interface and scoped adapter only. Retain old clients, 28 SQL files, PS OAS and AS/PS permissions. Revert the focused source commit to remove the Java capability.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–4 | G1/P1 |
| REQ-002 | Step 1 | 1–4 | R1/G1/P1 |
| REQ-003 | Step 1 | 1–7 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Existing START credentials are not extended by this SDK. Deployments must independently authorize the current original owner to read the existing resource. A separate decision is needed for historical action authorization and local receipt promotion.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/16 | Existing engine-neutral contract and client packages. | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/16 | Reuse exact Process route, DTOs, HttpProcessClient and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/16 | Use current JDK, Jackson 3 and JUnit without POM changes. | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/16 | Use one named receipt client port and private wire codec. | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/16 | Keep constructor query invariants and closed wire evidence. | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/16 | Retain existing immutable query and receipt records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/16 | No business model or persistence conversion. | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/16 | No new logging, response detail, query material or token in error outputs. | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/16 | The SDK port and adapter are explicitly constructed. | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/16 | Use existing JDK HTTP, URI encoding and Jackson primitives. | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/16 | Check exact closed fields, duplicate keys and typed error pairs. | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/16 | Preserve occurred UTC instant at microsecond precision. | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/16 | No product flags, defaults or profiles change. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/16 | Extend the existing HTTP Adapter for one Query. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/16 | Four Java files and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/16 | Separate finite network-free gates from external IAM acceptance. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/16 | External reconciliation policy and runtime remain separate from this SDK. | None |

### 12.6 Final verdict

PASS — Ready for user review
