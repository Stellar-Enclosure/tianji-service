# T4-03 历史 START 收据 PostgreSQL 验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 05:44 Asia/Shanghai |
| Updated | 2026-11-06 05:44 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents |
| Source Requirement | Master §17 S11 and §19 T4-03 |
| Baseline Revision | e789021e4c57f25110f7cff3496fd58c5ec42a1c |
| Implements Spec | [Historical START receipt PostgreSQL Spec](../spec/2026-11-06-05-43-historical-start-receipt-postgres.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 05:43 Asia/Shanghai SHA-256 282ed4b4d664920bb81bce1fc57d5d6c9a7db3b886dda97e7b9a5014263159f9 |
| Effective Specs | [Historical START receipt PostgreSQL Spec](../spec/2026-11-06-05-43-historical-start-receipt-postgres.md) |
| Depends On Plans | [Historical receipt implementation](2026-11-06-05-29-tool-start-receipt-reconciliation.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

复用既有隔离 V13 日志夹具，分别构造 UNKNOWN 与 DISPATCHING 的历史派发。验证正向证据仅追加 V2/V5 收据，并核对拒绝、未知结果、并发、精确重放及原始记录保全。

## 2. Target Spec and Effective Design

[Historical START receipt PostgreSQL Spec](../spec/2026-11-06-05-43-historical-start-receipt-postgres.md) §§4/7/11/14 governs this Step. Source140 already owns the production command. The existing V13 test supplies disposable database, real write fixture and operator safety guards.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/11 | Existing disposable PostgreSQL and exact new method with V1–V13 |
| REQ-002 | §7/10/14 | Real UNKNOWN/DISPATCHING, refusal, missing, one positive append, concurrent equality, exact replay and preserved source rows |
| REQ-003 | §8/14/16 | Four exact paths, 45 finite tests, bytecode, no PG report and 28 immutable SQL |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the exact baseline and absent selector. Apply the test-only candidate. Run finite and class checks, review exact scope, and commit once. Keep I1 independent.

### 4.2 Test-first strategy

Test-only fixture addition. Verify exact method absence with G0 and prove compilation with G2. The operator executes only I1.

### 4.3 Sequential and parallel boundaries

Run source gates sequentially in the owning reactor. Actual PostgreSQL only through the independently authorized I1.

### 4.4 Commit boundaries

One commit with the existing test and three review documents after G0/G1/G2/S1.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Actual historical receipt operator case | Required §7/14 | EVD-001–004 | Existing guarded journal fixture | Extend one test only |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Operator fixture | REQ-001/002/003 | G0/G1/G2 and independent I1 | 1 | Source140 and existing V13 test |
| Review and evidence | REQ-003 | Strict S1 | 2–4 | Exact owning outputs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Existing AR PostgreSQL test | Add one exact method in existing package |
| Persistence | Existing real V13 and V2/V5 | Compare exact witnesses with test-local ports |
| Production scope | Existing command owns authority and SQL | No production edit |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Controlled origin | Existing WriteFixture and ledger.finish | EVD-001 | Creates real stopped dispatch pairs | Reuse | Test-only calls | G2/I1 |
| Positive append and replay | Existing historical command | EVD-002/003 | Needs actual PostgreSQL operator witness | Reuse | Existing test only | G1/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | File 1 | One exact new operator method on the existing test class | G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | File 1 | Reuse existing disposable DB guards and exact typed owner/authority inputs | G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | File 1 | Reuse production DTOs and private immutable database fixture pair | G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | File 1 | No application bean, dependency injection or logger changes | G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | File 1 | Reuse existing JDK, JUnit, JDBC and Flyway | G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | File 1 | Verify existing receipt JSONB and complete source rows | G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | File 1 | Add one test JVM property with no configuration change | G1/G2/I1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | File 1 | Directly compose existing fixture and production command with controlled ports | G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | File 1 | Reuse Instant, DB time and existing operator timeout | G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | File 1 | Reuse existing Agent Runtime fixture package and schema | G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | dispatch_not_after, recordedAt and observedAt | Occurred instant | java.time.Instant and JDBC Timestamp | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Fixture bound is dispatchedAt plus one microsecond after pre-fence rejection | PostgreSQL microseconds and independent Clock | Existing source timestamps and receipts | Step 1 TEST-003 |
| TIME-002 | Operator test, JDBC and rendezvous deadlines | Duration | JUnit Timeout and JDK elapsed wait | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | 120s test, 5s connection, 15s socket, 4s gate and 12s futures | JUnit, driver and executor elapsed clocks | Retain existing operator timeouts | Step 1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-historical-start-receipt-postgres.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline e789021e4c57f25110f7cff3496fd58c5ec42a1c. Existing JDK25 offline settings. The test only applies existing V1–V13 to an authorized fresh fixture through I1. All 28 tracked SQL resources stay byte-identical.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the exact historical START receipt PostgreSQL acceptance method

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Source140 historical command and original V13 journal test.
- Baseline state: Real V13 fixture and finite historical tests exist. The composed PostgreSQL method is absent.
- Observable outcome: Independent I1 proves one persisted positive receipt and complete source preservation.
- End state: One test and three documents with only source evidence executed.
- Test-first gate: Not applicable. Test-only fixture; G0 checks exact selector absence.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`

- Purpose: Compose real V13 and V2/V5 through one independent method.
- Symbols: historical_start_receipts_append_positive_evidence_for_unknown_and_dispatched_actions.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR test owner, reviewer and authorized operator.
- Why now: Compose real V13 and V2/V5 through one independent method in the one scoped Step.
- Contract/signature changes: Test-only method and one method property.
- Input/output and state mapping: Fixture sources and exact gates remain fixed. Only the new isolated test may exercise real PostgreSQL.
- Error and edge behavior: Missing scope, selector or proof fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Reuse the guarded disposable database and actual WriteFixture; create UNKNOWN and DISPATCHING with the original journal.
Finish both Runs; reject future cutoff and then shorten only the fixture cutoff.
Call the real reconciliation service with current controlled ports.
Assert refusal and empty cases, one appended row, bounded overlap equality, original ACK replay and complete source row preservation.
```

- Verification contribution: G0/G1/G2 and separate I1.
- After this file: Reviewable evidence remains separate from the operator run.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md`

- Purpose: Define the guarded acceptance contract.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR test owner, reviewer and authorized operator.
- Why now: Define the guarded acceptance contract in the one scoped Step.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Fixture sources and exact gates remain fixed. Only the new isolated test may exercise real PostgreSQL.
- Error and edge behavior: Missing scope, selector or proof fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the existing data owner and isolated fixture input.
Specify exact method, property and separate source gates.
Require fixed source row preservation and retain I1 pending.
```

- Verification contribution: S1.
- After this file: Reviewable evidence remains separate from the operator run.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md`

- Purpose: Freeze order, scope and exact verification.
- Symbols: G0/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR test owner, reviewer and authorized operator.
- Why now: Freeze order, scope and exact verification in the one scoped Step.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Fixture sources and exact gates remain fixed. Only the new isolated test may exercise real PostgreSQL.
- Error and edge behavior: Missing scope, selector or proof fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock the exact baseline and candidate before applying the fixture.
Run finite owner selectors and verify compiled guard flags.
Review staged scope and SQL immutability without selecting PostgreSQL.
```

- Verification contribution: S1.
- After this file: Reviewable evidence remains separate from the operator run.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-historical-start-receipt-postgres.md`

- Purpose: Record observed proof and distinct runtime boundary.
- Symbols: G0/G1/G2/S1 evidence.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing AR test owner, reviewer and authorized operator.
- Why now: Record observed proof and distinct runtime boundary in the one scoped Step.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Fixture sources and exact gates remain fixed. Only the new isolated test may exercise real PostgreSQL.
- Error and edge behavior: Missing scope, selector or proof fails the corresponding gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record observed test counts and zero PostgreSQL reports.
Record actual bytecode and exact scope evidence.
Keep I1 unexecuted until the operator runs the independent selector.
```

- Verification contribution: S1.
- After this file: Reviewable evidence remains separate from the operator run.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/G1/G2/S1. I1 is separate.
- Expected result: 45 finite tests, compiled fixture and no PostgreSQL report.
- Failure returns to: File 1 for test errors or Files 2–4 for scope and evidence.
- Completion criteria: Exact four paths, finite and compiled proof and 28 immutable SQL.
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java`, `docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md`, `docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md`, `docs/egon/evidence/2026-11-06-historical-start-receipt-postgres.md`.
- Commit: test(agent): compose PostgreSQL historical START receipt acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Original operator method present and new method absent |
| G1 | Owning offline reactor | Exact four finite suites, 45 passes and zero failures/errors/skips |
| G2 | JDK25 actual test class | Both methods and the distinct method, URL and vector guards; no PostgreSQL report |
| S1 | Strict docs and staged scope | Four paths and 28 byte-identical migrations |
| I1 | Separate operator PostgreSQL | One non-skipped exact method proving both states, overlap/replay and V13/Run/budget preservation |

```sh
history_base=e789021e4c57f25110f7cff3496fd58c5ec42a1c
history_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
history_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
history_ar=stellar-enclosure-tianji-agent-runtime
history_source=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java
history_class=top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionJournalPostgresTest
history_method=historical_start_receipts_append_positive_evidence_for_unknown_and_dispatched_actions
history_core=ToolStartReceiptReconciliationServiceTest,ToolStartReceiptReconciliationSqlTest,ToolActionWriteSqlTest,ToolActionIntentStoreTest
test "$(git rev-parse --verify HEAD)" = "$history_base"
test -z "$(git show "$history_base:$history_source" | rg -F "$history_method")"
git show "$history_base:$history_source" | rg -F opt_in_tool_journal_contract_and_atomicity
"$history_runner" "$history_core" clean
"$history_jdk/bin/javap" -p -v -classpath "$history_ar/target/test-classes" "$history_class"
test -z "$(find "$history_ar/target/surefire-reports" -name "TEST-$history_class*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

G1 XML must report 7 service, 5 SQL, 10 write and 23 intent tests. Test-only changes have no P1 gate.

### Separate operator-only I1

Use a fresh authorized loopback database with no tianji_agent schema and the existing pinned preinstalled vector. Supply only the independent method and additional flag. Keep actual fixture credentials outside URLs and source proof.

```sh
: "${TIANJI_HISTORY_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_HISTORY_VECTOR_VERSION:?Provide pinned installed vector version}"
"$history_runner" "ToolExecutionJournalPostgresTest#$history_method" -Dtianji.tool.journal.postgres.historical.start.receipt=true "-Dtianji.tool.journal.postgres.test.url=$TIANJI_HISTORY_PRIVATE_PG_URL" "-Dtianji.tool.journal.postgres.vector.version=$TIANJI_HISTORY_VECTOR_VERSION"
```

Require exactly one non-skipped operator method. Neither controlled port exercises actual IAM or Process HTTPS.

## 9. Migration, Compatibility, Rollout, and Rollback

Retain existing test modes, production code, privileges, config, dependencies and 28 SQL files. Revert the one test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G0/G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL fixture results and production IAM, HTTPS and full S11 remain pending. I1 is operator-only.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing disposable DB, actual journal and historical command | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test and runtime dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | One named exact guarded integration method | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Disposable primary, exact owner and test-local authority | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable disposable database pair and exact row witnesses | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing receipt JSONB, no cross-layer mapper | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger, raw payload or credentials in reviewer evidence | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Explicit fixture construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing fixed fixture helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Verify stored status and complete source JSONB | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database clock cutoff and bounded operator test | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test JVM properties only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct reuse of existing fixture and current command | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual PostgreSQL and production IAM results pending | None |

### 12.6 Final verdict

PASS — Ready for user review
