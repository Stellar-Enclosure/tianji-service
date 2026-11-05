# T4-01 B1 BPMN 迁移预检 PostgreSQL 验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 06:48 Asia/Shanghai |
| Updated | 2026-11-06 06:48 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | One new PS PostgreSQL fixture and three review documents |
| Source Requirement | Master §19 T4-01 B1 |
| Baseline Revision | 4a090b111b7bc27e2954586548f5b0461208354d |
| Implements Spec | [Migration preflight PostgreSQL Spec](../spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 06:47 Asia/Shanghai SHA-256 da19eed8b25888530bc3c78a28b93b81b1368a9d9bb6c46ac85fbcbe5614f34a |
| Effective Specs | [Migration preflight PostgreSQL Spec](../spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md) |
| Depends On Plans | [Owned migration preflight API](2026-11-05-23-45-process-migration-preflight-api.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

复用现有隔离 ProcessDB 和临时角色，发布两个不同版本的同名普通等待 Timer，调用现有 owner 迁移预检，并用原生数据行比较证明单实例结构校验无迁移写入。

## 2. Target Spec and Effective Design

[Migration preflight PostgreSQL Spec](../spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md) §§4/7/11/14 governs this Step. The existing B1 production code owns the controller and native query.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §5/7/11 | Existing disposable PostgreSQL and exact new method with V1–V10 |
| REQ-002 | §7/10/14 | One actual native wait with exact target, owner and negative checks, complete source rows preserved |
| REQ-003 | §8/14/16 | Four exact paths, 21 finite cases, actual bytecode and no operator report, all 28 migrations unchanged |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock exact baseline and test absence. Apply test-only source and run finite and class gates. Commit exact scope and keep I1 separate.

### 4.2 Test-first strategy

The addition is an operator test only. G0 proves test absence and G2 proves the exact compiled gate. Actual I1 belongs to the operator.

### 4.3 Sequential and parallel boundaries

Run owning G0/G1/G2/S1 sequentially. I1 uses a separately authorized fresh disposable database.

### 4.4 Commit boundaries

One commit with one test and three review documents after G0/G1/G2/S1.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Native PostgreSQL operator fixture | Required §7/14 | EVD-001–004 | Reuse existing guarded Process fixture | Add one test class |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Operator fixture | REQ-001/002/003 | G0/G1/G2 and separate I1 | 1 | Existing B1 and fixture |
| Review evidence | REQ-003 | Strict S1 | 2–4 | Exact owning outputs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Existing PS PostgreSQL fixture | Add one method in the same existing package |
| Persistence | Existing native Flowable and JDBC | Compare persisted rows and keep test role grants confined to the disposable database |
| Production scope | Existing controller and service | No production change |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Disposable ProcessDB | ProcessServerPostgresTest#formalCaseDatabase and #context | EVD-001/004 | Needs existing V9/V10 on disposable V8 setup | Reuse | Test-only calls and temporary SELECT | G2/I1 |
| Native structural validation | Current ProcessMigrationPreflightService | EVD-002/003 | Needs actual PostgreSQL/Flowable proof | Reuse | Test-only method | G1/G2/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | File 1 | Use the existing Process test package and one exact operator method. | G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | File 1 | Reuse the exact loopback, actor, typed query and role checks. | G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | File 1 | Existing immutable API types and private immutable row witness. | G1/G2/I1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–004 | File 1 | No production bean, dependency injection or logging changes. | G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | File 1 | Use JDK, JUnit, Spring JDBC and existing test capabilities. | G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | File 1 | Compare native rows through actual PostgreSQL JSONB without new serialization. | G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | File 1 | Keep source profiles and packaged configuration, one explicit test-only JVM flag. | G1/G2/I1 | Step 1 | PASS |
| Rule 9 | §6.2/7 | EVD-001–004 | File 1 | Reuse existing Test Fixture and actual read-only Query. | G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | File 1 | Keep existing Instant, explicit UTC and duration semantics. | G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | File 1 | Reuse the existing PS test-side PostgreSQL setup and its unchanged schema. | G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt and native timer due time | Occurred instant | java.time.Instant and existing native JDBC conversion | Legacy UTC | UTC Z | Not applicable: absolute instant | Not applicable: no display | Explicit UTC JVM and JDBC connection, no shared default change | Existing PostgreSQL clock and native timestamp precision | Existing native timestamps interpreted in UTC and converted to UTC Instant by the existing PostgreSQL/JDBC mapping | Step 1 TEST-003 |
| TIME-002 | timer wait, test and read limits | Duration | ISO Duration and JUnit Timeout | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | PT1H fixture timer remains in future, existing two-second observation query and test deadline | Existing native and JUnit clocks | Keep existing scheduler and SQL timeouts | Step 1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessMigrationPreflightPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-bpmn-migration-preflight-postgres.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 4a090b111b7bc27e2954586548f5b0461208354d. Existing JDK25 offline settings. I1 applies unchanged V1–V10 only in a fresh operator fixture. Preserve all 28 existing SQL bytes.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the exact BPMN migration preflight PostgreSQL fixture

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing B1 controller/service and native Process fixture.
- Baseline state: Finite B1 tests are present. The exact native acceptance selector is absent.
- Observable outcome: Operator I1 exercises the actual owner timer and proves before/after native and receipt row equality.
- End state: One test and three documents, only source evidence executed in this Step.
- Test-first gate: Not applicable. Test-only fixture and G0 absence.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessMigrationPreflightPostgresTest.java`

- Purpose: Provide exact native read-only operator fixture.
- Symbols: validates_one_owned_native_timer_without_moving_it.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing PS operator fixture and source reviewer.
- Why now: Provide exact native read-only operator fixture within B1.
- Contract/signature changes: Test-only method and private JVM gate.
- Input/output and state mapping: Scope remains exact; only explicit I1 may exercise PostgreSQL.
- Error and edge behavior: Missing selector, fixture safety or evidence fails the owning gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 7, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Reuse the guarded fixture bootstrap and apply existing V9/V10 inside the disposable schema.
Publish two exact versions and START the owned native timer.
Call existing controller and actual service; verify full row witnesses and guard outcomes.
```

- Verification contribution: G0/G1/G2 and separate I1.
- After this file: Preserve separate operator gate evidence.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md`

- Purpose: Freeze the operator fixture and runtime boundary.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing PS operator fixture and source reviewer.
- Why now: Freeze the operator fixture and runtime boundary within B1.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Scope remains exact; only explicit I1 may exercise PostgreSQL.
- Error and edge behavior: Missing selector, fixture safety or evidence fails the owning gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record existing fixture and source guards with exact owner.
Record native validation and row comparison without authorization expansion.
Retain independently pending I1 until actually executed.
```

- Verification contribution: S1.
- After this file: Preserve separate operator gate evidence.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md`

- Purpose: Order exact source, compile and staging verification.
- Symbols: G0/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing PS operator fixture and source reviewer.
- Why now: Order exact source, compile and staging verification within B1.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Scope remains exact; only explicit I1 may exercise PostgreSQL.
- Error and edge behavior: Missing selector, fixture safety or evidence fails the owning gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Pin the exact baseline and finite selector list.
Verify compiled method and class-level and method-level opt-in.
Compare exact staged paths and existing SQL bytes before commit.
```

- Verification contribution: S1.
- After this file: Preserve separate operator gate evidence.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-bpmn-migration-preflight-postgres.md`

- Purpose: Record only executed validation and pending operator proof.
- Symbols: G0/G1/G2/S1 evidence.
- Repository evidence: Spec EVD-001–004.
- Dependencies and consumers: Existing PS operator fixture and source reviewer.
- Why now: Record only executed validation and pending operator proof within B1.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Scope remains exact; only explicit I1 may exercise PostgreSQL.
- Error and edge behavior: Missing selector, fixture safety or evidence fails the owning gate.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record observed finite counts and source compile/bytecode checks.
Confirm no operator report and reconcile exact staged path and SQL scope.
Keep actual PostgreSQL marked unverified unless separate I1 is run.
```

- Verification contribution: S1.
- After this file: Preserve separate operator gate evidence.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 G0/G1/G2/S1. I1 is separate.
- Expected result: 21 finite cases, compiled fixture and no PostgreSQL report.
- Failure returns to: File 1 for test/source errors or Files 2–4 for review and evidence.
- Completion criteria: Four exact paths, source and compiled evidence, and 28 immutable SQL.
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessMigrationPreflightPostgresTest.java`, `docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md`, `docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md`, `docs/egon/evidence/2026-11-06-bpmn-migration-preflight-postgres.md`.
- Commit: test(process): prepare native BPMN migration preflight acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Exact baseline | Existing Process fixture present and new selector absent |
| G1 | Owning offline reactor | Exact three finite suites, 21 successes and zero failures/errors/skips |
| G2 | JDK25 actual test class | Compiled method, class and method guards, and no PostgreSQL report |
| S1 | Strict documents and staged scope | Four paths and 28 byte-identical migrations |
| I1 | Separate operator PostgreSQL | One non-skipped method with native positive and negative outcomes, exact row preservation |

```sh
migration_base=4a090b111b7bc27e2954586548f5b0461208354d
migration_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
migration_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
migration_ps=stellar-enclosure-tianji-process-server
migration_source=stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessMigrationPreflightPostgresTest.java
migration_class=top.egon.cola.platform.tianji.process.server.ProcessMigrationPreflightPostgresTest
migration_method=validates_one_owned_native_timer_without_moving_it
migration_core=ProcessMigrationPreflightControllerTest,ProcessMigrationPreflightServiceTest,ProcessStorageConfigurationTest
test "$(git rev-parse --verify HEAD)" = "$migration_base"
if git cat-file -e "$migration_base:$migration_source" 2>/dev/null; then exit 1; fi
"$migration_runner" "$migration_core" clean
"$migration_jdk/bin/javap" -p -v -classpath "$migration_ps/target/test-classes" "$migration_class"
test -z "$(find "$migration_ps/target/surefire-reports" -name "TEST-$migration_class*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Owning XML must report 5 controller, 5 service and 11 storage configuration tests. Test-only scope requires no P1 package.

### Separate operator-only I1

Use a fresh authorized loopback database with no tianji_process schema and the existing explicit dedicated runtime role convention. The fixture sets UTC on the actual Process connection and requires the JVM default UTC. Supply the dedicated operator selector only.

```sh
: "${TIANJI_MIGRATION_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_MIGRATION_RUNTIME_ROLE:?Provide existing dedicated temporary role}"
"$migration_runner" "ProcessMigrationPreflightPostgresTest#$migration_method" -Duser.timezone=UTC -Dtianji.process.postgres.migration-preflight=true "-Dtianji.process.postgres.test.url=$TIANJI_MIGRATION_PRIVATE_PG_URL" "-Dtianji.process.postgres.test.runtime-user=$TIANJI_MIGRATION_RUNTIME_ROLE"
```

Require exactly one non-skipped test. I1 uses controlled access and does not exercise actual IAM or Process HTTPS.

## 9. Migration, Compatibility, Rollout, and Rollback

Keep existing runtime wiring, privileges and migration bytes. Revert the one test-only commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G0/G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual isolated PostgreSQL and Flowable execution, live IAM and HTTPS, migration execution and activation remain independent. I1 is operator-only.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Reuse Process Server owner and the existing native fixture. | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Use actual existing fixture bootstrap, Flowable and Process Query. | None |
| MC-DEP-001 | Applicable | PASS | §6/16 | Use existing JDK, Spring JDBC, Flyway test scope and JUnit. | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Name the exact operator selector and immutable native-row witness. | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | Reuse existing fresh loopback, temporary role and typed owner gates. | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Reuse immutable DTO and copy snapshot lists. | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No new wire conversion or mapper. | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger or credential evidence output. | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | No new production wiring or Spring component. | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Reuse existing typed controller, JDBC and operator fixture. | None |
| MC-JSON-001 | Applicable | PASS | §10/14 | Compare whole persisted rows through PostgreSQL JSONB. | None |
| MC-TIME-001 | Applicable | PASS | §6.3/14 | Require UTC JVM and UTC native observation, with a fixed one-hour waiting timer. | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | Add a method-only test flag and preserve packaged runtime config. | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Reuse direct Test Fixture and existing read-only Query. | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One Java test, three review documents and unchanged existing migrations. | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite counts and compiled method are separate from actual PostgreSQL execution. | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Document independent operator runtime gate; source acceptance makes no runtime claim. | None |

### 12.6 Final verdict

PASS — Ready for user review
