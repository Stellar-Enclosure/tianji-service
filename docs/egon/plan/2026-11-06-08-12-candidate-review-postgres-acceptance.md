# T4-01 A3 PostgreSQL 候选审阅验收实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-08-12-candidate-review-postgres-acceptance.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 08:12 Asia/Shanghai |
| Updated | 2026-11-06 08:12 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 两个 PS 测试源、三份文档、单步提交；运行 PostgreSQL 另行授权 |
| Source Requirement | 主计划 §19 T4-01 U+I、A3 TEST-007 |
| Baseline Revision | 28ad7d522a214adf7544545c7fe9eedf3e645ae5 |
| Implements Spec | [PS PostgreSQL 验收](../spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 08:11 Asia/Shanghai SHA-256 c28df1aecdb1cc9db315adf567f3c99bd273d678bdec4438af3199c4718beb2e |
| Effective Specs | [本切片](../spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md) |
| Depends On Plans | [A3 候选审阅](2026-11-06-07-47-platform-release-candidate-review.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

编写并编译 A3 真实 PostgreSQL 操作者用例，验证当前生产 JDBC 路径和已提交 V11 的隔离库合同，记录实际运行前置。

## 2. Target Spec and Effective Design

| Spec | Scope | Use |
| --- | --- | --- |
| Current A3 PostgreSQL Spec | §4/7/14 | 本测试的完整 acceptance |
| A3 existing Spec | §11、TEST-007 | 只读前置，当前生产合同不变 |

## 3. Effective Requirements and Acceptance

| Requirement | Exact Spec source | Observable acceptance | Gate |
| --- | --- | --- | --- |
| REQ-001 | §4/7.2 | 独立测试门禁和实际 V10→V11，历史保留 | C1/I1 |
| REQ-002 | §4/7.2/7.3 | 真实角色、目录、权限和范围 FK | C1/I1 |
| REQ-003 | §4/7.3 | 真双连接、一个审阅、独立读权限与过期回滚 | C1/I1 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先锁定当前生产/迁移不动的基线，编写 release-package scenarios，接入唯一 JUnit 方法，再编译、检查 opt-in 字节码与有限回归。

### 4.2 Test-first strategy

本步只新增操作者测试；不引入生产行为。真实 I1 需用户独立授权，未执行不能作为通过证据。当前 gate 是编译和已有有限回归。

### 4.3 Sequential and parallel boundaries

两个 Java 类型在同一次 compile gate 中验证。I1 只可用明确授权的独立数据库和预置临时角色运行。

### 4.4 Commit boundaries

一个测试源码和文档提交；不混入任何新的生产迁移、配置或权限。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Handling |
| --- | --- | --- | --- |
| 独立操作者方法 | Add §7/14 | 现有 ProcessServerPostgresTest guard | 复用 guard 和专用 flag |
| 真实写入及等待夹具 | Add §7.3 | A3 finite mocks | 实际 JdbcTemplate、事务和 PG 锁视图 |
| 生产 A3 | Keep §11 | Committed V11/source | 只读复用 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| Scenarios | REQ-002/003 | C1/I1 | Frozen A2 vectors and actual owner SQL | Step 1 |
| Selector | REQ-001/002/003 | C1/I1 | Existing loopback/runtime role guard | Step 1 |
| Review docs | REQ-001/002/003 | S1 | Test gates and exact source baseline | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Obligation | Gate |
| --- | --- | --- | --- | --- |
| Scope | Existing PS tests | §7/8 | New two test classes; no production changes | C1/S1 |
| Persistence | Existing Flyway/JDBC | §7/11 | Same frozen migrations and real runtime role | I1 |
| Time | Existing A3 DB half-open window | §6.3 | Actual DB expiry, bounded monotonic polling | I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Database guard | ProcessServerPostgresTest | explicitTestUrl, explicitRuntimeRole | Fit | Reuse both, fresh schema only | Test code only | Step 1 MC-SCOPE-001 |
| Signature | PlatformReleaseSignatureVerifierTest | public synthetic fixture | Fit | Import same package vectors | No dependency | Step 1 MC-REUSE-001 |
| Owner and transaction | ProcessReleaseService/A3 store | owned SQL and clock | Fit | Actual storage, engine metadata fixture | Test source only | Step 1 MC-TEST-001 |

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
| Rule 1 | §8/10 | PS named test types | Files 1–2 | Keep fixture responsibility separate | C1 | Step 1 | PASS |
| Rule 2 | §7/14 | Existing URL/role guards | Files 1–2 | Exact scope and proof checks | C1/I1 | Step 1 | PASS |
| Rule 3 | §10 | Existing immutable values | File 1 | Reuse immutable scope/receipt | C1 | Step 1 | PASS |
| Rule 4 | §15 | Existing JUnit | Files 1–2 | No managed bean or logger | S1 | Step 1 | N/A |
| Rule 5 | §6 | JDK and existing test deps | Files 1–2 | No new dependencies | C1/S1 | Step 1 | PASS |
| Rule 6 | §7 | Existing PS Jackson 3 | File 1 | Only serialize owner fixture input map | C1/I1 | Step 1 | PASS |
| Rule 7 | §16 | Existing profile | File 2 | Test-only opt-in | S1 | Step 1 | N/A |
| Rule 9 | §13 | Fixed fixture workflow | Files 1–2 | Production adapter reused | S1 | Step 1 | N/A |
| Rule 10 | §6.3 | DB Clock and JDK elapsed | File 1 | Distinguish UTC deadline and monotonic bound | I1 | Step 1 | PASS |
| Rule 11 | §11/16 | User AGENTS immutable Flyway and A3 source | Files 1–5 | Existing V1–V11 unchanged, no new runtime or storage | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Fixture read grant deadline | Occurred instant | Instant | Not persisted | Internal | UTC instant without local conversion | No display | Exclusive expiry, no DST | DB clock_timestamp | Existing A3 admission window | Step 1 I1 TEST-002 |
| TIME-002 | Lock observation and future waits | Duration | Duration | Not persisted | Internal | Monotonic elapsed, no zone | No display | Finite wait and no wall time subtraction | System.nanoTime, JDBC timeout | Existing A3 store limits | Step 1 I1 TEST-002 |

## 5. Change File Tree

| Operation | Exact path | File | Requirement | Gate |
| --- | --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewPostgresScenarios.java | 1 | REQ-002/003 | C1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java | 2 | REQ-001/002/003 | C1/S1 |
| CREATE | docs/egon/spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md | 3 | REQ-001/002/003 | C1/S1 |
| CREATE | docs/egon/plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md | 4 | REQ-001/002/003 | C1/S1 |
| CREATE | docs/egon/evidence/2026-11-06-candidate-review-postgres-acceptance.md | 5 | REQ-001/002/003 | C1/S1 |


## 6. Prerequisites, Constraints, and Plan Clarifications

Use existing PS module, cached Java 25 test dependencies and exact known current A3 commit. Confirm fresh loopback test gate and no new runtime provisioning. Relock the current service commit and five paths before staging. I1 needs one externally prepared disposable loopback tianji_it_ database, bootstrap owner, pre-provisioned nonmember test runtime role and explicit operator authorization.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 准备 A3 真实 PostgreSQL 操作者测试

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Committed A3 source and V11, current PS PostgreSQL guards, published public signed test fixtures.
- Baseline state: A3 finite tests present; PostgreSQL candidate acceptance not present.
- Observable outcome: One guarded method and helper compile with existing PS test scope.
- End state: Candidate test and exact evidence committed, operator acceptance still separately controlled.
- Test-first gate: Not applicable. This Step adds a test-only operator fixture, with no production behavior change.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files: Files 1–5 below.

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewPostgresScenarios.java`

- Purpose: Drive actual A3 DB fixture with package-local vectors.
- Symbols: verify, lockReviewInShareMode, awaitBlockedWriters.
- Repository evidence: Spec §7/8 and committed A3 implementation/source tests.
- Dependencies and consumers: Existing PS test source and owning JDBC; explicit operator only.
- Why now: Exact next path required to satisfy Step 1 source or review.
- Contract/signature changes: Test or documentation only. Existing APIs, DB files and production code unchanged.
- Input/output and state mapping: Drive actual A3 DB fixture with package-local vectors. Map the fixture to REQ-002/003.
- Error and edge behavior: Fail closed when source scope, proof, role or expected outcome does not match.
- Standards impact: MC-SCOPE-001 and MC-TEST-001, with applicable §12.5 rows.
- Literal rule enforcement: Rule 11 preserves production module and all migration bytes; §4.8 records each rule.
- Implementation pseudocode:
```java
var verified = verifyBytes(publicVector, fixedAtDatabaseClock);
insertFixtureRelease(owner, verified);
waitForRealInsertLocksThenVerifyRollbackAndWinner();
assertExactHistoryAndRestrictedSqlStates();
```
- Verification contribution: Compile and static proof at C1/S1; executable I1 requires operator authorization.
- After this file: Verify the exact path and prerequisite evidence before staging.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java`

- Purpose: Own fresh PostgreSQL guard and migration selector.
- Symbols: upgrades_v10_and_serializes_real_independent_review_writes.
- Repository evidence: Spec §7/8 and committed A3 implementation/source tests.
- Dependencies and consumers: Existing PS test source and owning JDBC; explicit operator only.
- Why now: Exact next path required to satisfy Step 1 source or review.
- Contract/signature changes: Test or documentation only. Existing APIs, DB files and production code unchanged.
- Input/output and state mapping: Own fresh PostgreSQL guard and migration selector. Map the fixture to REQ-001/002/003.
- Error and edge behavior: Fail closed when source scope, proof, role or expected outcome does not match.
- Standards impact: MC-SCOPE-001 and MC-TEST-001, with applicable §12.5 rows.
- Literal rule enforcement: Rule 11 preserves production module and all migration bytes; §4.8 records each rule.
- Implementation pseudocode:
```java
assertFreshLoopbackDatabaseAndExactTemporaryRole();
migrateV10ThenApplyV11Once();
verifyKnownHistoryAndRestrictedA3Role();
PlatformReleaseCandidateReviewPostgresScenarios.verify(owner, runtime);
```
- Verification contribution: Compile and static proof at C1/S1; executable I1 requires operator authorization.
- After this file: Verify the exact path and prerequisite evidence before staging.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md`

- Purpose: Document exact scoped operator contract.
- Symbols: §4/7/14.
- Repository evidence: Spec §7/8 and committed A3 implementation/source tests.
- Dependencies and consumers: Existing PS test source and owning JDBC; explicit operator only.
- Why now: Exact next path required to satisfy Step 1 source or review.
- Contract/signature changes: Test or documentation only. Existing APIs, DB files and production code unchanged.
- Input/output and state mapping: Document exact scoped operator contract. Map the fixture to REQ-001/002/003.
- Error and edge behavior: Fail closed when source scope, proof, role or expected outcome does not match.
- Standards impact: MC-SCOPE-001 and MC-TEST-001, with applicable §12.5 rows.
- Literal rule enforcement: Rule 11 preserves production module and all migration bytes; §4.8 records each rule.
- Implementation pseudocode:
```text
Ground the isolated schema and temporary role in existing PS tests.
Record exact operation scope, evidence and non-runtime source boundary.
Associate every requirement with an observable failure or proof.
```
- Verification contribution: Compile and static proof at C1/S1; executable I1 requires operator authorization.
- After this file: Verify the exact path and prerequisite evidence before staging.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md`

- Purpose: Lock the two sources and three docs.
- Symbols: Step 1 and C1/S1/I1.
- Repository evidence: Spec §7/8 and committed A3 implementation/source tests.
- Dependencies and consumers: Existing PS test source and owning JDBC; explicit operator only.
- Why now: Exact next path required to satisfy Step 1 source or review.
- Contract/signature changes: Test or documentation only. Existing APIs, DB files and production code unchanged.
- Input/output and state mapping: Lock the two sources and three docs. Map the fixture to REQ-001/002/003.
- Error and edge behavior: Fail closed when source scope, proof, role or expected outcome does not match.
- Standards impact: MC-SCOPE-001 and MC-TEST-001, with applicable §12.5 rows.
- Literal rule enforcement: Rule 11 preserves production module and all migration bytes; §4.8 records each rule.
- Implementation pseudocode:
```text
Resolve the exact Spec SHA and reviewed source baseline.
Identify test-only paths and independent PostgreSQL acceptance.
Record direct compilation and finite selector results without live claims.
```
- Verification contribution: Compile and static proof at C1/S1; executable I1 requires operator authorization.
- After this file: Verify the exact path and prerequisite evidence before staging.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-candidate-review-postgres-acceptance.md`

- Purpose: Record actual source-only proof.
- Symbols: C1/S1/I1.
- Repository evidence: Spec §7/8 and committed A3 implementation/source tests.
- Dependencies and consumers: Existing PS test source and owning JDBC; explicit operator only.
- Why now: Exact next path required to satisfy Step 1 source or review.
- Contract/signature changes: Test or documentation only. Existing APIs, DB files and production code unchanged.
- Input/output and state mapping: Record actual source-only proof. Map the fixture to REQ-001/002/003.
- Error and edge behavior: Fail closed when source scope, proof, role or expected outcome does not match.
- Standards impact: MC-SCOPE-001 and MC-TEST-001, with applicable §12.5 rows.
- Literal rule enforcement: Rule 11 preserves production module and all migration bytes; §4.8 records each rule.
- Implementation pseudocode:
```text
Record the Java 25 compiler and actual finite JUnit counters.
Confirm both explicit operator gates exist in compiled test metadata.
Record PostgreSQL execution as pending separate operator authorization.
```
- Verification contribution: Compile and static proof at C1/S1; executable I1 requires operator authorization.
- After this file: Verify the exact path and prerequisite evidence before staging.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: C1/S1 in §8. I1 deferred to the authorized operator.
- Expected result: Both Java files compile, both opt-in conditions present, no PostgreSQL execution, existing A3 finite tests pass.
- Failure returns to: Exact test/source path or strict review artifact; do not run the unapproved operator gate.
- Completion criteria: Scope and migration hashes, compile, finite regression and strict documents all pass, then one commit.
- Rollback: Remove the test-only source change if requested. Existing production code and migration data stay unchanged.
- Commit paths: stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewPostgresScenarios.java, stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/PlatformReleaseCandidateReviewPostgresTest.java, docs/egon/spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md, docs/egon/plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md, docs/egon/evidence/2026-11-06-candidate-review-postgres-acceptance.md
- Commit: test(process): prepare PostgreSQL candidate review acceptance

## 8. Test, Validation, and Quality Gates

| Gate | Exact action | Expected result |
| --- | --- | --- |
| C1 | Java 25 compile exact test sources, javap on PlatformReleaseCandidateReviewPostgresTest, and finite selectors below | Two new test classes compile. URL and dedicated method flags present. All current finite tests pass. |
| S1 | Strict Spec and Plan, git diff --check, exact five-path patch and pre-existing SQL/source compare | No added production or old migration change. |
| I1 | Future method upgrades_v10_and_serializes_real_independent_review_writes with the three explicit private properties | One successful unskipped operator result and fresh database evidence. |

```sh
a3_pg_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
a3_pg_java=$(/usr/libexec/java_home -v 25)
a3_pg_finite='PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseCandidateReviewStoreTest,ProcessApiSchemaVerifierTest'
JAVA_HOME="$a3_pg_java" STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$a3_pg_settings" -s "$a3_pg_settings" -o -B -ntp   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=$a3_pg_finite"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-08-11-candidate-review-postgres-acceptance.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-08-12-candidate-review-postgres-acceptance.md
git diff --check
```

### Separate operator-only I1

Select PlatformReleaseCandidateReviewPostgresTest#upgrades_v10_and_serializes_real_independent_review_writes with tianji.process.postgres.test.url, tianji.process.postgres.test.runtime-user and tianji.process.postgres.candidate-review=true. The URL must be the operator's authorized fresh loopback tianji_it_ database; the pre-provisioned runtime must be a temporary tianji_it_runtime_ role with 12 lowercase hexadecimal characters. Process test guard rejects a nonconforming scope. Keep the credentials and URLs private. Expected evidence is an actual unskipped success, with both waiting backend sessions, expiry, role and FK assertions. The fixture writes only to the disposable schema during the approved run.

## 9. Migration, Compatibility, Rollout, and Rollback

The test executes existing immutable V1–V11 only in the operator's isolated environment. No new migration or production rollout in Step 1. Do not automatically run or clean a database.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Files 2–5 | C1/S1/I1 |
| REQ-002 | Step 1 | Files 1–5 | C1/S1/I1 |
| REQ-003 | Step 1 | Files 1–5 | C1/S1/I1 |

## 11. Risks, Blockers, and User Decisions

Operator role and empty database are prerequisites. Only actual PostgreSQL I1 establishes physical catalog, lock and SQLSTATE results. Fixture signer/owner data do not establish deployed Flowable definition or production trust. Owner decides authorized I1 and teardown.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7, exact PS test owner | Current test structure preserved | Recheck at scoped intake |
| MC-REUSE-001 | Applicable | PASS | EVD-002–004, §7 | Existing URL/role guards, A2 vectors and A3 production JDBC reused | Recheck at scoped intake |
| MC-DEP-001 | Applicable | PASS | §6, PS POM | Existing Java/Flyway/JDBC/Mockito sufficient | Recheck at scoped intake |
| MC-NAME-001 | Applicable | PASS | §8/10 named types | Test selector, fixture and outcome separate | Recheck at scoped intake |
| MC-VALID-001 | Applicable | PASS | §7 existing exact URL/role and candidate scope | Gate explicit and closed before operator writes | Recheck at scoped intake |
| MC-MODEL-001 | Applicable | PASS | §10 current signed immutable values | Test-only ReviewOutcome and existing receipts | Recheck at scoped intake |
| MC-CONVERT-001 | Not applicable | N/A | §7/10 existing A3 SQL mapping | Fixture only seeds the already verified input map | Recheck at scoped intake |
| MC-LOG-001 | Not applicable | N/A | §15 JUnit/JDBC fixture | No logger or secret data added | Recheck at scoped intake |
| MC-BEAN-001 | Not applicable | N/A | §7/15 no application context | No Spring managed component added | Recheck at scoped intake |
| MC-UTIL-001 | Applicable | PASS | §6.3 and test helpers | Existing JDK/JDBC tools and bounds | Recheck at scoped intake |
| MC-JSON-001 | Not applicable | N/A | §7 and unchanged A2 wire | Fixture only uses existing JSON map serialization | Recheck at scoped intake |
| MC-TIME-001 | Applicable | PASS | §6.3, TEST-002 | DB instant and monotonic polling separate | Recheck at scoped intake |
| MC-CONFIG-001 | Not applicable | N/A | §3.3, §16 no config | Opt-in test system property only | Recheck at scoped intake |
| MC-PATTERN-001 | Not applicable | N/A | §7/13 fixed operator fixture | Current production Adapter reused | Recheck at scoped intake |
| MC-SCOPE-001 | Applicable | PASS | §8/11/16 test-only | Two test paths and three docs; existing migrations unchanged | Recheck at scoped intake |
| MC-TEST-001 | Applicable | PASS | §14 TEST-001/002 | Compilation differs from operator acceptance | Recheck at scoped intake |
| MC-BLOCKER-001 | Applicable | PASS | §5/18 and TEST-002 | Closed source-review scope; real DB operator gate remains separate | Recheck at scoped intake |

### 12.6 Final verdict

PASS — Ready for user review
