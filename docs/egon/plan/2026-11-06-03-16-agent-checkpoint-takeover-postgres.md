# T4-03 Checkpoint 单次接管 PostgreSQL 验收实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-03-16-agent-checkpoint-takeover-postgres.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:16 Asia/Shanghai |
| Updated | 2026-11-06 03:16 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents |
| Source Requirement | Master §17 S11, §19 T4-03 |
| Baseline Revision | Service 41050e26f35af6b2ca98d33c6a63e26b9df387d2 |
| Implements Spec | [Checkpoint takeover PostgreSQL Spec](../spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:15 Asia/Shanghai SHA-256 458027739d7ee1e93e5d5576bb271dc0fad1e9ba19e68f3cce0cb2be8943a804 |
| Effective Specs | [Checkpoint takeover PostgreSQL Spec](../spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Extend the existing V16 product fork fixture with one separately guarded PostgreSQL takeover proof. The owning source gate compiles the method, runs three finite suites and leaves I1 to the operator.

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Checkpoint takeover PostgreSQL Spec](../spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md), Review, SHA-256 `458027739d7ee1e93e5d5576bb271dc0fad1e9ba19e68f3cce0cb2be8943a804` and Service `41050e26f35af6b2ca98d33c6a63e26b9df387d2`.

### 2.2 Effective Spec set

The linked primary Spec owns this proof. Existing product fork and checkpoint takeover are reused prerequisites.

### 2.3 Superseded or excluded content

No replacement. Actual operator I1, production role provisioning, real authority registration and native model continuation remain independent.

## 3. Effective Requirements and Acceptance

| ID | Spec source | Acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §5/7/11 | Exact reconciled product Run claims once with same attempt, two new fences and two ordered events on both codecs | Existing AR fixture |
| REQ-002 | §7/14 | Owner, revision, expired permit and changed-command refusal; stable replay with exact persisted lease/event/receipt evidence | Existing takeover path through real fixture |
| REQ-003 | §8/14/16 | Exact compiled guard, 22 finite tests, unchanged SQL | Four exact paths |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock exact committed baseline and source candidate. Run G0 to verify method absence. Apply the single existing test file change, run G1/G2, record observed evidence, then stage only four paths and verify S1.

### 4.2 Test-first strategy

This Step adds an independently gated PostgreSQL verification of existing product source. G0 proves the exact operator method is absent. I1 remains operator-owned; source verification uses finite tests and compile inspection.

### 4.3 Sequential and parallel boundaries

One Maven reactor at a time. Source execution never provisions PostgreSQL roles, runs any PostgreSQL fixture or launches children.

### 4.4 Commit boundaries

One semantic test-only commit containing four exact paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

Reuse the current V16 fork, third-role fixture validation and actual takeover service. One guarded test method and bounded post-reconcile assertion helper close this physical evidence gap without another fixture layer or DDL.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Files | Gate |
| --- | --- | --- | --- |
| Existing PostgreSQL fixture method | Exact committed claim, current V16 fork and role setup | 1 | G0/G1/G2/I1 |
| Review and evidence | Strict Spec and actual finite/bytecode results | 2–4 | S1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Existing AR integration fixture | Keep same owner and current production classes |
| Validation | Existing disposable PG identity and owner guards | Reuse checks; add explicit separate takeover role and exact typed permit |
| Migration and time | Existing V16 fixture, database clock | No SQL change; test expired and bounded permit |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Real V16 checkpoint | Existing product fork and provisioner | EVD-002 | Reuse | Existing RECONCILE path and local assertions | No dependency | G2/I1 |
| One-shot claim | Existing takeover source and finite tests | EVD-001/004 | Reuse; physical role/PG gap | Add exact operator selector and use actual service | No dependency | G1/I1 |
| Role separation | Existing reader role validation and PostgreSQL SELECT rule | EVD-003 | Existing test shape needs narrow claim DML | Supply third login, keep V16 SELECT-only, permit Session revision UPDATE only for locking | No production configuration | G2/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | File 1 | Exact semantic test method and separately named fixture role | G1/G2/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | File 1 | Use current PG guards, exact owner, revision and permit checks | G1/G2/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | File 1 | Reuse current types; any private witness is immutable | G1/G2/S1 | Step 1 | N/A |
| Rule 4 | §6.2/7 | EVD-001–004 | File 1 | Direct fixture construction without production Spring components or log changes | G1/G2/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | File 1 | Use existing JDK, JDBC, Fake and fork handoff | G1/G2/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | File 1 | Verify persisted typed ACK and exact status events via existing ledger and PostgreSQL JSONB | G1/G2/S1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | File 1 | Only test-local opt-in properties | G1/G2/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | File 1 | One bounded fixture with direct reuse of existing fork helper | G1/G2/S1 | Step 1 | N/A |
| Rule 10 | §6.2/7 | EVD-001–004 | File 1 | Database-origin expiry and bounded operator process waits | G1/G2/S1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | File 1 | Existing AR test owner and immutable Flyway history | G1/G2/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Run deadline, permit and Run/Session lease expiry | Occurred instant | java.time.Instant, PostgreSQL timestamptz, JDBC Timestamp | UTC instant | Internal | Database clock | Not applicable: no display | Expired permit rolls back; positive lease must be future and bounded by permit/deadline; no local-zone conversion | PostgreSQL clock and existing microsecond precision | Existing Run/Session lease schema | Step 1 TEST-003 |
| TIME-002 | Child wait and method timeout | Duration | java.time.Duration and test timeout | Not persisted | Internal | Not applicable: elapsed time has no zone | Not applicable: no display | Bounded existing fork child lifetime and test timeout | Existing fixture timeout and JVM wait | Existing child protocol unchanged | Step 1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-takeover-postgres.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service `41050e26f35af6b2ca98d33c6a63e26b9df387d2`, JDK 25 and existing local offline Maven settings. Reviewed Java candidate SHA-256 is `f9a8ef0902a424e5e3d2a87afa84d515568ecb58875c056427a194109b142a10`. All 28 production SQL, production Java/POM/config and PaC remain byte-identical. Operator I1 independently requires approved fresh loopback primary, absent Tianji schema, exact installed vector patch, setup identity, capture writer and a distinct limited takeover login.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Compile the guarded product checkpoint takeover case

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing V16 product fork, current Java takeover service and current fixture provisioner.
- Baseline state: Existing capture, product fork and native resume scenarios; exact new takeover operator method absent.
- Observable outcome: One compiled independent opt-in method with exact scoped role, permit, commit, rollback and replay checks.
- End state: One existing test and three documents; actual G0/G1/G2/S1, I1 independently pending.
- Test-first gate: Not applicable. Existing behavior has finite tests; this Step adds an operator integration proof.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`

- Purpose: Add exact takeover PostgreSQL scenario.
- Symbols: AgentCheckpointCapturePostgresTest#takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit.
- Repository evidence: Spec EVD-001–004 and the clean baseline.
- Dependencies and consumers: Existing AR owner, source reviewer and authorized operator.
- Why now: Keep a single independently reviewable fixture proof.
- Contract/signature changes: Dedicated test method and test-local inputs only.
- Input/output and state mapping: Use the current V16 role validation, test-only restricted column DML and private RECONCILE callback. Each codec checks same-attempt commit, negative permit and exact replay witnesses.
- Error and edge behavior: Missing role, ownership, exact persisted witness or class evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
var db = provision(exactThirdTakeoverRole);
assertCheckpointReaderPrivileges(takeoverJdbc);
grantCheckpointTakeover(db.observer(), quotedRole);
verifyProductCheckpointForks(db, RECONCILE, (fixture, cut) -> {
  expireReconciledSessionLease(cut);
  assertWrongOwnerStaleRevisionAndExpiredPermit();
  var original = claimWithBoundedFixturePermit();
  assertExactRunSessionEpochsAndTwoEvents(original);
  assertReplayHasSameAckLeaseEventReceiptAndNoNewDecision();
  assertUnchangedNativePrefixCheckpointAndNoBudgetToolOrProductResult();
});
```

- Verification contribution: G0/G1/G2 and independent I1.
- After this file: New guarded fixture compiles; I1 remains operator-owned.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md`

- Purpose: Record source requirements.
- Symbols: REQ-001–003 and UC-001/002.
- Repository evidence: Spec EVD-001–004 and the clean baseline.
- Dependencies and consumers: Existing AR owner, source reviewer and authorized operator.
- Why now: Keep a single independently reviewable fixture proof.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Specify exact third role, required PostgreSQL row-lock privilege and replay evidence boundaries.
- Error and edge behavior: Missing role, ownership, exact persisted witness or class evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record baseline and current V16 fork and claim.
Define guard flags, exact permission surface and one operator selector.
Distinguish physical execution from finite and class proofs.
```

- Verification contribution: S1.
- After this file: Exact review artifact available.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md`

- Purpose: Lock implementation order.
- Symbols: Step 1 and G0/G1/G2/S1/I1.
- Repository evidence: Spec EVD-001–004 and the clean baseline.
- Dependencies and consumers: Existing AR owner, source reviewer and authorized operator.
- Why now: Keep a single independently reviewable fixture proof.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Keep exact one test change with finite source sequence.
- Error and edge behavior: Missing role, ownership, exact persisted witness or class evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock reviewed Spec, base, source hash and four paths.
Run baseline G0, exact 22 finite tests and bytecode G2.
Record I1 separately and verify staged scope.
```

- Verification contribution: S1.
- After this file: Exact review artifact available.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-takeover-postgres.md`

- Purpose: Record observed evidence.
- Symbols: G0/G1/G2/S1 and independent I1.
- Repository evidence: Spec EVD-001–004 and the clean baseline.
- Dependencies and consumers: Existing AR owner, source reviewer and authorized operator.
- Why now: Keep a single independently reviewable fixture proof.
- Contract/signature changes: Review documentation only.
- Input/output and state mapping: Record actual results before commit and leave operator execution pending.
- Error and edge behavior: Missing role, ownership, exact persisted witness or class evidence fails the relevant gate.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record G0 and fresh owning report counts.
Record exact class guards and no PG report plus 28 unchanged migrations.
Reference precise I1 selector and properties in Plan.
```

- Verification contribution: S1.
- After this file: Exact review artifact available.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Run §8 G0/G1/G2/S1. I1 is operator-only.
- Expected result: 22 finite passing tests; exact method and guards compiled; no PG report.
- Failure returns to: File 1 for source/fixture proof; Files 2–4 for review evidence.
- Completion criteria: Actual source reports and exact four paths, 28 SQL unchanged, separate I1 pending.
- Rollback: Revert one test-only commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java`, `docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md`, `docs/egon/plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-takeover-postgres.md`.
- Commit: test(agent): prepare PostgreSQL checkpoint takeover acceptance.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Clean baseline | Existing capture/fork source and exact new method absent |
| G1 | Offline clean reactor, exact three finite selectors | Fresh owning reports, 10 + 5 + 7, zero failures, errors or skips |
| G2 | JDK 25 javap of test class | Exact method, capture and takeover gates, takeover role property and preserved private child entry; no PG report |
| S1 | Strict docs and exact staged scope | Four paths and 28 unchanged migrations |
| I1 | Separate authorized isolated PostgreSQL | One actually executed unskipped method verifies both codecs, exact claim, rollback and replay |

```sh
takeover_pg_base=41050e26f35af6b2ca98d33c6a63e26b9df387d2
takeover_pg_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
takeover_pg_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
takeover_pg_method=takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit
takeover_pg_ar=stellar-enclosure-tianji-agent-runtime
takeover_pg_class=top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointCapturePostgresTest
takeover_pg_path=stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointCapturePostgresTest.java
test "$(git rev-parse --verify HEAD)" = "$takeover_pg_base"
git cat-file -e "$takeover_pg_base:$takeover_pg_path"
test -z "$(git show "$takeover_pg_base:$takeover_pg_path" | rg -F "$takeover_pg_method")"
takeover_pg_reactor() {
  JAVA_HOME="$takeover_pg_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$takeover_pg_settings" -s "$takeover_pg_settings" -o -B -ntp     -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false     -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
takeover_pg_reactor clean '-Dtest=AgentCheckpointTakeoverServiceTest,AgentCheckpointForkHandoffTest,AgentCheckpointObservationServiceTest'
"$takeover_pg_jdk/bin/javap" -p -v -classpath "$takeover_pg_ar/target/test-classes" "$takeover_pg_class"
test ! -e "$takeover_pg_ar/target/surefire-reports/TEST-$takeover_pg_class.xml"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-15-agent-checkpoint-takeover-postgres.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-16-agent-checkpoint-takeover-postgres.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

Inspect exact three fresh owning XML reports and bytecode method/properties. G1/G2 never enable a PostgreSQL, role or child fixture profile. This test-only source Step has no package or deployed verification requirement.

### Separate operator-only I1

Only with an independently authorized fresh disposable loopback primary and absent tianji_agent schema. Supply the installed vector patch, separate capture writer and preprovisioned limited takeover login. Credentials stay outside the URL. The fixture role prefix is `tianji_it_checkpoint_takeover_` and the suffix is one to 24 `[a-z0-9_]` characters. Setup grants exact fixture privileges, including column-level UPDATE on the Session revision solely to permit its SELECT FOR UPDATE. [PostgreSQL 18 SELECT](https://www.postgresql.org/docs/18/sql-select.html)

```sh
: "${TIANJI_CHECKPOINT_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_CHECKPOINT_RUNTIME_ROLE:?Provide the limited capture login}"
: "${TIANJI_CHECKPOINT_VECTOR_VERSION:?Provide the installed vector patch}"
: "${TIANJI_CHECKPOINT_TAKEOVER_ROLE:?Provide the separate limited takeover login}"
takeover_pg_reactor "-Dtest=AgentCheckpointCapturePostgresTest#$takeover_pg_method"   -Dtianji.postgres.checkpoint.capture=true   "-Dtianji.postgres.checkpoint.capture.url=$TIANJI_CHECKPOINT_PRIVATE_PG_URL"   "-Dtianji.postgres.checkpoint.capture.runtime-role=$TIANJI_CHECKPOINT_RUNTIME_ROLE"   "-Dtianji.postgres.checkpoint.capture.vector.version=$TIANJI_CHECKPOINT_VECTOR_VERSION"   -Dtianji.postgres.checkpoint.takeover=true   "-Dtianji.postgres.checkpoint.takeover.role=$TIANJI_CHECKPOINT_TAKEOVER_ROLE"
```

Use the exact new method, with no unrelated operator flags. Require one actually executed unskipped PostgreSQL method and retain its owning XML. Dispose of the fixture database separately. Finite results and javap cannot substitute for I1.

## 9. Migration, Compatibility, Rollout, and Rollback

No production code, SQL, configuration or permissions are changed. Existing V1–V15 then V16 and earlier operator modes remain. Revert the single test-only commit to remove this scenario.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

I1 remains runtime-unverified until independently authorized execution. Database scope, three distinct identities and child process limits are hard prerequisites. Product IAM, application wiring, post-takeover native execution and full S11 remain independent.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002 | Existing fork, provisioner, takeover service and evidence checks | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK, JUnit, Spring JDBC, driver and Flyway | None |
| MC-NAME-001 | Applicable | PASS | §7.3/8 | Exact guarded method and separate takeover identity | None |
| MC-VALID-001 | Applicable | PASS | §5/7.3/14 | Loopback primary, exact role, owner, revision and permit | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing types and private immutable witness only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing codecs and lease observations | None |
| MC-LOG-001 | Not applicable | N/A | §15 | JUnit assertions without credential, lease owner or payload logs | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | Explicit fixture construction without runtime wiring | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Validate existing acknowledgement projection and fixed event payload | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database-origin expiry and strictly bounded permits and leases | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test-local JVM opt-in | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing guarded fork with local callback | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | One test path and three review files | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and class gates separate from I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Operator runtime evidence pending | None |

### 12.6 Final verdict

PASS — Ready for user review
