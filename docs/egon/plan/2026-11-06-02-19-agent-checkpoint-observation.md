# T4-03 Agent checkpoint 边界观察实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-02-19-agent-checkpoint-observation.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 02:19 Asia/Shanghai |
| Updated | 2026-11-06 02:19 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | Four exact Java paths and three review documents for internal V16 observation |
| Source Requirement | 主计划 §17 S11 / §19 T4-03 |
| Baseline Revision | 6fa58d2ff7dd9f0a286ca9a149f9b7ee30f8de62 |
| Implements Spec | [Checkpoint observation](../spec/2026-11-06-02-18-agent-checkpoint-observation.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 02:18 Asia/Shanghai SHA-256 41f94c04ad849c0ff5d5f78ffeede469e30c5feedb7701d5c2dd8ec9c62d9289 |
| Effective Specs | [Checkpoint observation](../spec/2026-11-06-02-18-agent-checkpoint-observation.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Add an explicitly constructed AR observer and independent reader verification. Keep production defaults, permissions and all 28 SQL immutable.

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Checkpoint observation](../spec/2026-11-06-02-18-agent-checkpoint-observation.md), Review, exact SHA-256 `41f94c04ad849c0ff5d5f78ffeede469e30c5feedb7701d5c2dd8ec9c62d9289` and Service baseline `6fa58d2ff7dd9f0a286ca9a149f9b7ee30f8de62`.

### 2.2 Effective Spec set

The linked primary Spec owns this Step. The existing V16 and capture behavior are evidence and remain unchanged.

### 2.3 Superseded or excluded content

No replacement. Resume, caller wiring, grant provisioning and live operator acceptance are independent.

## 3. Effective Requirements and Acceptance

| ID | Spec source | Acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7/11 | Reader readiness and per-call exact ACL; capture stays INSERT-only | Verifier and same physical snapshot |
| REQ-002 | §7/9/11 | Full owner and exact post-expiry Run fence | Internal observer |
| REQ-003 | §7/10/11 | Native event, Session state, all budget and V13 proof | Internal observer, existing codecs |
| REQ-004 | §8/14/15/16 | Bounded enum, four Java paths, no API and SQL drift | Tests and package gates |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock clean baseline, exact V16 and candidate hashes. Compile typed A with final verifier and only the V13 guard absent, then restore B and run G1 and P1. Stage exact paths after actual evidence.

### 4.2 Test-first strategy

Select one finite method `AgentCheckpointObservationServiceTest#unreadable_v13_never_observes_boundary` on typed A. Require one intended assertion with no compilation error or skip.

### 4.3 Sequential and parallel boundaries

One Maven reactor at a time. No PostgreSQL, child process acceptance or network access in source gates.

### 4.4 Commit boundaries

One semantic commit and seven explicit paths. Typed A and generated outputs remain outside the commit.

### 4.5 Spec Simplicity and Implementation-necessity Audit

Existing capture forbids SELECT. An internal observer on an independent dedicated reader and the existing verifier/codecs resolve the exact gap. No new public boundary, role grant, schema, scheduler or layer.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Files | Gate |
| --- | --- | --- | --- |
| Typed A | Final read verifier and scoped fixture | 1–4 temporary A guard | R1 |
| Product B | Intended R1 and exact baseline | 1–4 final candidate | G1/P1 |
| Evidence | Actual source and package results | 5–7 | S1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Existing AR JDBC and ADK Session | Existing infrastructure owner |
| Validation | Existing scoped material and native codecs | Reuse and add exact bounded guards |
| Migration | V16 and prior locked SQL | No new DDL |
| Config and time | Existing boot properties and DB timestamps | No config, UTC instant comparison in physical snapshot |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| JDBC read | Existing ledger and transaction templates | EVD-001/002 | Reuse own same-source material | Use fixed observer query | No dependency | G1 |
| Native event match | Existing Session codecs | EVD-003 | Supports both versions | Reuse existing codecs and matcher | No dependency | G1 |
| Role integrity | Existing checkpoint verifier | EVD-001/004 | Shared V16 catalog, role split needed | Extend existing verifier | No dependency | G1/P1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | Files 1–4 | Exact observation service and typed private row | G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | Files 1–4 | Exact verified USER, run ID and existing material plus native evidence validation | G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | Files 1–4 | Immutable observation row private to JDBC and redacted | G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–005 | Files 1–4 | Explicit constructor without bean registration or new logging | G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | Files 1–4 | Existing JDK, Spring JDBC and ADK codecs | G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | Files 1–4 | Existing codec paths and fixed typed JSONB comparisons | G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–005 | Files 1–4 | No application profiles or runtime flags change | G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | Files 1–4 | One fixed query boundary without a strategy hierarchy | G1/P1/S1 | Step 1 | N/A |
| Rule 10 | §6.2/7 | EVD-001–005 | Files 1–4 | Authoritative PostgreSQL timestamp comparison and existing Java instants | G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–005 | Files 1–4 | Existing AR infrastructure and immutable Flyway history; no new events | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | AgentRunDTO.deadlineAt, checkpoint captured_at, Session lease_expires_at | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Database UTC clock | No display surface | Finite times compared to same transaction; expired lease at equality, deadline must be later | PostgreSQL transaction timestamp, existing precision | Existing formats and values preserved | Step 1 TEST-002/003 |
| TIME-002 | event_timestamp_ms and native Event timestamp | Occurred instant | Existing ADK epoch milliseconds | UTC instant | Internal | Database and recorded ADK instant | No display surface | Exact native id, invocation and timestamp match | Existing native milliseconds | Both native codecs retained | Step 1 TEST-002 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java` |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java` |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java` |
| CREATE | `docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md` |
| CREATE | `docs/egon/plan/2026-11-06-02-19-agent-checkpoint-observation.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-observation.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Use the exact reviewed baseline, JDK25 and local offline settings. No startup, database apply, new dependencies, IAM or remote action. Typed A changes only the guard described in R1. Strict docs and actual evidence precede the commit.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the exact owner checkpoint observation and reader profile

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing V16 table, capture role, native codecs, AgentLedgerService and JdbcTemplate.
- Baseline state: V16 captures exact first input; no independent reader observation.
- Observable outcome: Exact private evidence under a separate explicit primary read DataSource; no state mutation.
- End state: Four Java files and three documents in one verified commit.
- Test-first gate: Required. Typed A removes checkpointToolEvidenceReadable only; exact R1 selector must fail one assertion.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`

- Purpose: Observer finite fixture.
- Symbols: AgentCheckpointObservationServiceTest.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and the closed observer source gate.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Internal Java or finite test scope only.
- Input/output and state mapping: Typed test expects unknown for unreadable V13; validate primary RR, both codecs and conservative negative witnesses.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
when(fixture.verifier.checkpointToolEvidenceReadable()).thenReturn(false);
assertEquals(TOOL_EVIDENCE_UNAVAILABLE, fixture.service.observe(OWNER, QUERY));
assertNull(fixture.toolSql);
```

- Verification contribution: R1/G1.
- After this file: Exact typed source is ready for its bounded gate.

#### File 2 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java`

- Purpose: Role verification regression.
- Symbols: AgentCheckpointStorageVerifierTest.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and the closed observer source gate.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Internal Java or finite test scope only.
- Input/output and state mapping: Validate exact reader/capture separation and evidence relations on controlled JDBC responses.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyCheckpointObservationReaderContract();
assertCurrentReadRoleDeniesCoreTableMutationAndRowSecurity();
assertCaptureWriterStillRejectsSelectAndReaderRejectsWriter();
```

- Verification contribution: G1/P1.
- After this file: Exact typed source is ready for its bounded gate.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`

- Purpose: Existing verifier reader split.
- Symbols: AgentStorageVerifier#verifyCheckpointObservation, #checkpointObservationRoleReadable, #checkpointToolEvidenceReadable.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and the closed observer source gate.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Internal Java or finite test scope only.
- Input/output and state mapping: Reuse exact V16 catalog pins, inspect real current SELECT-only role and V13 readiness; keep capture role independent.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
verifyExactV16Catalog();
assertPrimaryPhysicalReadOnlyAndEffectiveReader();
assertReadableV13EvidenceRelationsWithoutGrantOrPayloadRead();
```

- Verification contribution: G1/P1.
- After this file: Exact typed source is ready for its bounded gate.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`

- Purpose: Owner exact observation.
- Symbols: AgentCheckpointObservationService#observe.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and the closed observer source gate.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Internal Java or finite test scope only.
- Input/output and state mapping: Use one bounded primary RR snapshot, exact ledger material, monotonic Run and Session fences, existing codecs and zero budget/Tool evidence.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Implementation pseudocode:

```java
return readOnlyRepeatableRead(() -> {
  checkSameSourcePrimaryAndReader();
  var material = ledger.executionMaterial(actor, query);
  return exactBoundaryEvidence(material, checkedV16AndV13);
});
```

- Verification contribution: R1/G1/P1.
- After this file: Exact typed source is ready for its bounded gate.

#### File 5 — `CREATE docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md`

- Purpose: Source requirements.
- Symbols: REQ-001–004.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and reviewer.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Record the boundaries and independent operator acceptance.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Link T4-03/S11 and current V16 capture to REQ-001–004.
Specify bounded owner and native Session evidence and the closed enum.
Preserve source/operational evidence distinction and exact four Java paths.
```

- Verification contribution: S1.
- After this file: Scoped review evidence available.

#### File 6 — `CREATE docs/egon/plan/2026-11-06-02-19-agent-checkpoint-observation.md`

- Purpose: Source execution order.
- Symbols: Step 1.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and reviewer.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Lock exact paths and finite/source artifact tests.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock baseline, effective Spec digest and four source candidate hashes.
Run exact typed A R1 and inspect the intended assertion.
Restore B, run G1/P1/S1 and stage only the seven specified paths.
```

- Verification contribution: S1.
- After this file: Scoped review evidence available.

#### File 7 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-observation.md`

- Purpose: Actual evidence.
- Symbols: R1/G1/P1/S1.
- Repository evidence: EVD-001–005 and exact effective Spec.
- Dependencies and consumers: Existing AR boundaries and reviewer.
- Why now: Keep one scoped source change and its evidence.
- Contract/signature changes: Review artifact only.
- Input/output and state mapping: Replace scratch status with actual result counts and artifacts before commit.
- Error and edge behavior: Scope, missing evidence or validation drift refuses positive.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the fresh R1/G1/P1 reports and count failures and skips.
Compare AS/PS manifests and actual nested AR class artifact.
Record the exact SQL set and explicitly separate live reader and S11 acceptance.
```

- Verification contribution: S1.
- After this file: Scoped review evidence available.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run §8 R1 on typed A and G1/P1/S1 on B.
- Expected result: Exact one failing assertion R1; all selected finite cases pass on B; clean package and scope evidence.
- Failure returns to: Files 1–4 for runtime source or tests; Files 5–7 for evidence.
- Completion criteria: Actual R1, G1, P1 and S1; all 28 SQL exact; explicit live/operator gaps.
- Rollback: Revert one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationServiceTest.java`, `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/adk/session/AgentCheckpointObservationService.java`, `docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md`, `docs/egon/plan/2026-11-06-02-19-agent-checkpoint-observation.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-observation.md`.
- Commit: feat(agent): observe owned checkpoint boundaries.

## 8. Test, Validation, and Quality Gates

```sh
observe_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
observe_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
observe_base=6fa58d2ff7dd9f0a286ca9a149f9b7ee30f8de62
test "$(git rev-parse --verify HEAD)" = "$observe_base"
observe_red='AgentCheckpointObservationServiceTest#unreadable_v13_never_observes_boundary'
observe_core=AgentCheckpointObservationServiceTest,AgentCheckpointStorageVerifierTest,AdkCheckpointCaptureTest,AdkSessionEventCodecTest,TianjiAgentRuntimeAutoConfigurationTest,AdkExecutionTransactionBoundaryTest
observe_finite() {
  JAVA_HOME="$observe_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$observe_settings" -s "$observe_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
observe_finite "$observe_red"
observe_finite "$observe_core"
JAVA_HOME="$observe_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify   ./scripts/verify-source-reactor.sh -gs "$observe_settings" -s "$observe_settings" -o clean -B -ntp   -Ppermission-producer "-Dpermission.commit=$observe_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-03-agent-checkpoint-observation   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$observe_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-02-18-agent-checkpoint-observation.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-02-19-agent-checkpoint-observation.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and boundary |
| --- | --- |
| R1 | One intended wrong BOUNDARY_OBSERVED on unreadable V13, zero errors/skips. |
| G1 | Exact six finite classes; preserve both writer and reader contract and actual complete owning reports. |
| P1 | Clean permission-producer repeats finite reports. Confirm actual observer class in AR and nested AS; no new AS/PS wiring, PaC semantic diff, YAML or SQL change. No PostgreSQL report. |
| S1 | Strict Spec and Plan, seven paths, all 28 migrations byte-identical. |

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or production wiring. Leave current permission, capture defaults and grant semantics as-is. Revert the single scoped commit if required.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–4 | G1/P1 |
| REQ-002 | Step 1 | 1,4 | G1 |
| REQ-003 | Step 1 | 1–4 | R1/G1 |
| REQ-004 | Step 1 | 5–7 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual primary role provision and native continuation are independent. No S11, cross-owner authority, or operator PostgreSQL acceptance is credited here. Observation can stale immediately, and future write-side policy/CAS is out of this Step.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR native Session and storage infrastructure | None |
| MC-REUSE-001 | Applicable | PASS | EVD-002/003/004 | Existing ledger material, JDBC, codecs and V16 checks | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, Spring JDBC, Mockito, ADK | None |
| MC-NAME-001 | Applicable | PASS | §7/8/10 | Exact observation service, JDBC row and closed result | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Exact verified owner and fail-closed snapshot evidence | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable mapped row with redacted string and closed result | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No layer or wire mapper, only existing codecs | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logs and no raw values in result/errors | None |
| MC-BEAN-001 | Not applicable | N/A | §8/16 | Explicit construction and no Spring registration | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing JDK, JDBC and codecs | None |
| MC-JSON-001 | Applicable | PASS | §10/11 | Existing codecs and exact JSONB status evidence | None |
| MC-TIME-001 | Applicable | PASS | §6.3/15 | DB UTC comparison and unchanged instant contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | No configuration change | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing adapters and fixed bounded query | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Four Java paths and three documents | None |
| MC-TEST-001 | Applicable | PASS | §14 | R1, G1, P1 and actual result evidence required | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator permissions, PostgreSQL and S11 remain separate | None |

### 12.6 Final verdict

PASS — Ready for user review
