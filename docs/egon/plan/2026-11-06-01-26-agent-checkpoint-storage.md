# T4-03 Agent checkpoint 存储实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-01-26-agent-checkpoint-storage.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 01:26 Asia/Shanghai |
| Updated | 2026-11-06 01:26 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | AgentDB V16, explicit owning-primary catalog readiness and finite proof |
| Source Requirement | 主计划 §17 S11 / §19 T4-03 |
| Baseline Revision | 98ccd8fa189aecf54641d8e55af5a7a02060610d |
| Implements Spec | [Checkpoint storage](../spec/2026-11-06-01-25-agent-checkpoint-storage.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 01:25 Asia/Shanghai SHA-256 8916915680a5e8915842cfe95afb2d6bfd5e70bb1139b302098178518831fbfe |
| Effective Specs | [Checkpoint storage](../spec/2026-11-06-01-25-agent-checkpoint-storage.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

一个 source Step 提供不可变 V16、现有 AR verifier 的独立技术校验、有限测试和制品证据。没有实际启动和写入路径。主要证明是有意 RED、有限 GREEN、权限制品构建和精确路径检查。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Checkpoint storage](../spec/2026-11-06-01-25-agent-checkpoint-storage.md)，Review，SHA-256 `8916915680a5e8915842cfe95afb2d6bfd5e70bb1139b302098178518831fbfe`，基线 `98ccd8fa189aecf54641d8e55af5a7a02060610d`。

### 2.2 Effective Spec set

同一主 Spec。V15 和双 JVM 探针是上下文。主计划 T4-03 允许新 AgentDB 迁移并保留恢复模式默认关闭。

### 2.3 Superseded or excluded content

无替换。写入、启动配置、租约切换、跨 owner、真实 DDL 或 S11 runtime 留给独立 Gate。

## 3. Effective Requirements and Acceptance

| ID | Spec source | Acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §8/§11 | Exact bounded V16 and previous 27 byte-identical | One Agent migration |
| REQ-002 | §11/§16 | Two exact CASCADE FK and supporting native event index | No existing DDL changes |
| REQ-003 | §7/§14 | Bounded immutable resource/history/catalog and insert-only role validation | Existing verifier, one finite fixture |
| REQ-004 | §3/§8/§16/§18 | No new callers, writer, authority, config, deployment or runtime evidence | Static artifacts, six paths |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock exact clean source and Spec. Stage typed A fixture and no-op predecessor on the existing base verifier for RED only. Apply final immutable V16 and verifier B in order. Verify targeted source and package before staging.

### 4.2 Test-first strategy

R1 proves inherited verifier alone cannot reject V16 absence. Use exactly the intended JUnit assertion and verify that the failing class was compiled. Do not treat missing symbols as RED.

### 4.3 Sequential and parallel boundaries

Exact R1 → B → G1 → P1 → S1 sequential in the same Maven reactor. Read-only review and document validation can run independently. Actual PostgreSQL application is outside this Step.

### 4.4 Commit boundaries

One semantic commit containing three source/resource/test paths and three documents. No staging of typed A, scratch files or generated outputs.

### 4.5 Spec Simplicity and Implementation-necessity Audit

The existing base check does not attest V16. Add one internal method and the required DDL. A new Spring Bean, abstraction, repository, marker API or writer has no use in this Step.

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Files | Gate |
| --- | --- | --- | --- |
| RED | Baseline verifier | File 1, temporary A overlay in File 3 | R1 |
| Storage B | RED witness | Files 1–3 | G1/P1 |
| Evidence | Observed fresh XML and artifact hashes | Files 4–6 | S1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | AR JDBC and Flyway | Existing traditional infrastructure |
| Validation | AgentStorageVerifier model usage and context readiness | Reuse existing stable failure semantics, own primary transaction |
| Migration | Immutable classpath V1–V15 | One operator-controlled V16, no new dependency or generator |
| Time | Existing database timestamp style | timestamptz(6) finite UTC, later writer uses Instant |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Primary readiness | AgentStorageVerifier | verifyModelUsage, checksum and column/key helpers | Fits infrastructure, no V16 yet | Extend existing verifier | One method and specific catalog assertions | Step 1 MC-REUSE-001 |
| Native identity | Agent V5 event PK | tenant, subject type/issuer/ID, app, Session, sequence | Fits FK | Reference without parent DDL | One child FK/index | Step 1 MC-MODEL-001 |
| Migration | Existing Agent Flyway | V15 lock | Fits | One new resource | No dependency | Step 1 MC-SCOPE-001 |

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
| Rule 1 | §6.2 | Existing Agent infrastructure | Files 1–3 | Existing technical verifier and exact test name | G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | Existing Agent infrastructure | Files 1–3 | Check PostgreSQL catalog and privilege shape at storage boundary | G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | Existing Agent infrastructure | Files 1–3 | No persistence object or application DTO | G1/P1/S1 | Step 1 | N/A |
| Rule 4 | §6.2 | Existing Agent infrastructure | Files 1–3 | Existing non-Bean verifier and fixed domain exception | G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | Existing Agent infrastructure | Files 1–3 | Existing JDBC/transaction and JDK digest | G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | Existing Agent infrastructure | Files 1–3 | DDL JSONB shape only, with no Java or wire JSON change | G1/P1/S1 | Step 1 | N/A |
| Rule 7 | §6.2 | Existing Agent infrastructure | Files 1–3 | No product property or startup wiring | G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | Existing Agent infrastructure | Files 1–3 | One fixed technical guard, without business variation | G1/P1/S1 | Step 1 | N/A |
| Rule 10 | §6.2 | Existing Agent infrastructure | Files 1–3 | UTC occurrence in timestamptz(6) from a later database clock | G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | Existing Agent infrastructure | Files 1–3 | Existing AR Flyway/JDBC boundary and existing CQE unchanged | G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | agent_checkpoint.captured_at | Occurred instant | PostgreSQL timestamptz(6), future Java java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: private storage | Reject infinite instants, DST independent | Six fractional digits, future PostgreSQL clock_timestamp(), no capture call in this Step | New field, older timestamps untouched | Step 1 TEST-001/004 and future operator round-trip |

## 5. Change File Tree

| Operation | Exact path | Responsibility | Step |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java` | Finite fixture | Step 1 File 1 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V16__add_agent_checkpoint.sql` | Immutable V16 | Step 1 File 2 |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java` | Existing catalog verifier | Step 1 File 3 |
| CREATE | `docs/egon/spec/2026-11-06-01-25-agent-checkpoint-storage.md` | Effective reviewed design | Step 1 File 4 |
| CREATE | `docs/egon/plan/2026-11-06-01-26-agent-checkpoint-storage.md` | Exact ordered steps | Step 1 File 5 |
| CREATE | `docs/egon/evidence/2026-11-06-agent-checkpoint-storage.md` | Observed source evidence | Step 1 File 6 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact clean Service `98ccd8fa189aecf54641d8e55af5a7a02060610d`, strict source/doc lock, JDK 25, current offline Maven settings and fixed source reactor. Revalidate current source scope and artifact baseline. All pre-existing 27 production SQL files, product flags, PaC and POMs are out of scope. Only finite tests; neither a DB, a listener, a runtime child nor an operator fixture is launched.

CLAR-001. The probe uses the already existing storage verifier. An explicit call verifies the internal contract, but no product caller is included in this Step.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add Agent checkpoint storage contract

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Current AgentDB Run/Session event keys, owning verifier, exact reviewed Spec.
- Baseline state: No V16, no checkpoint verifier and no product writer or startup property.
- Observable outcome: Exact new packaged SQL and finite fail-closed catalog contract.
- End state: Six exact committed paths and complete actual source evidence.
- Test-first gate: Required. Typed R1 fixture proves one expected assertion with temporary base-only verifier.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-UTIL-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 10, Rule 11.
- Ordered files: Files 1–6 in the order below.

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java`

- Purpose: Lock exact optional catalog behavior and immutable new resource with finite JDBC doubles.
- Symbols: AgentCheckpointStorageVerifierTest, rejects_missing_v16_history.
- Repository evidence: AgentModelUsageStoreTest has existing owner-local synthetic JDBC/readiness fixture patterns.
- Dependencies and consumers: JUnit, existing AgentStorageVerifier, synthetic DataSource/catalog rows and SQL fixture.
- Why now: R1 must fail before final storage.
- Contract/signature changes: New finite test only.
- Input/output and state mapping: Exact stable primary/version/script/hash/checksum and real table shape represented without credentials or live data.
- Error and edge behavior: Missing history gives fixed domain failure. Tampered migration/catalog/index/privilege, primary and ambient transaction cases reject.
- Standards impact: MC-VALID-001, MC-TEST-001, MC-SCOPE-001. Existing test conventions.
- Literal rule enforcement: Rule 1 exact type, Rule 2 exact storage input, Rule 5 JDK/current tools, Rule 10 finite capture-time DDL proof, Rule 11 same AR module.
- Implementation pseudocode:

```java
@Test void rejects_missing_v16_history() {
    var catalog = validOwningPrimaryCatalogWithV1ThroughV6();
    catalog.removeHistoryVersion("16");
    assertThrows(AgentLedgerException.class,
        new AgentStorageVerifier(catalog.dataSource())::verifyCheckpointCapture);
}
// A gives the existing verify() behavior only. G1 proves exact V16 plus negative catalog mutations.
```

- Verification contribution: R1, G1, P1.
- After this file: Typed A will compile with the temporary verifier seam; B tests remain in this file.

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V16__add_agent_checkpoint.sql`

- Purpose: Define the one immutable owner-local checkpoint relation.
- Symbols: V16__add_agent_checkpoint.sql, agent_checkpoint, agent_checkpoint_session_event_idx.
- Repository evidence: V1 Run PK, V5 native Session event owner PK and immutable V15 tip.
- Dependencies and consumers: Existing Flyway path and PostgreSQL constraints. Later writer is a separate Step.
- Why now: Create once after actual R1, before final verifier pin.
- Contract/signature changes: Exact 29 typed NOT NULL columns, no defaults. Defined PK, two CASCADE FKs, nine CHECK constraints and one full owner/sequence nonunique index.
- Input/output and state mapping: Same owner/lease/fence provenance and prior/new counters in Spec §11. Capture UTC timestamptz(6); bounded receipts and first boundary empty.
- Error and edge behavior: PK collisions, malformed digest, mismatched owner FK, invalid typed scope or infinite capture time reject. Parent erasure cascades in original transaction.
- Standards impact: MC-MODEL-001, MC-TIME-001, MC-SCOPE-001. One established operator migration, no alterations or role grants.
- Literal rule enforcement: Rule 10 UTC occurrence, Rule 11 current immutable AgentDB Flyway path.
- Implementation pseudocode:

```sql
CREATE TABLE tianji_agent.agent_checkpoint (
  -- Define the exact 29 typed columns from Spec §11.
  CONSTRAINT agent_checkpoint_pkey PRIMARY KEY (tenant_id, run_id, attempt, checkpoint_no),
  CONSTRAINT agent_checkpoint_run_fkey FOREIGN KEY (tenant_id, run_id)
    REFERENCES tianji_agent.agent_run (tenant_id, run_id) ON DELETE CASCADE,
  CONSTRAINT agent_checkpoint_session_event_fkey
    FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_event_sequence)
    REFERENCES tianji_agent.agent_session_event (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence)
    ON DELETE CASCADE
  -- Apply the nine named CHECK expressions from the locked V16 resource.
);
CREATE INDEX agent_checkpoint_session_event_idx ON tianji_agent.agent_checkpoint
    (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_event_sequence);
```

- Verification contribution: G1/P1. SHA-256 `8521cd49e7ae4401f7dd9847b27918eeeca26b2ef926aa3d43821d900ed2ca36`, signed Flyway CRC `-415542202`, 4053 bytes.
- After this file: The new migration is the only SQL delta and remains unapplied.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`

- Purpose: Add internal explicit verification of the inert checkpoint storage prerequisite.
- Symbols: AgentStorageVerifier#verifyCheckpointCapture and private checkpoint catalog assertions.
- Repository evidence: Existing verifyModelUsage and checksum path, fixed catalog style and sanitized error.
- Dependencies and consumers: Current Spring JDBC/Tx; zero startup/production call sites.
- Why now: A method seam is used only for R1. Final B follows the pinned migration.
- Contract/signature changes: public void verifyCheckpointCapture(). Existing methods and constructors preserve semantics.
- Input/output and state mapping: Explicit call inspects V16 resource, exact history and actual physical primary catalog, returning void on success.
- Error and edge behavior: Reject ambient tx, wrong primary, resource/history drift, incorrect constraints/keys/index/role and JDBC failure with fixed dependency error. Do not expose SQL or credentials.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-VALID-001, MC-LOG-001, MC-UTIL-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1 existing technical class, Rule 2 fail-closed catalog input, Rule 5 current JDK/JDBC, Rule 11 same infrastructure module.
- Implementation pseudocode:

```java
// A only for R1.
public void verifyCheckpointCapture() { verify(); }
// B.
// Reject ambient transaction and require exact bounded V16 resource hash.
// Reuse base readiness and own owning-primary catalog transaction.
// Match one V16 script/type/success/checksum history row.
// Match all 29 table columns, collations/null/defaults, nine CHECK, three keys and two exact indexes.
// Verify INSERT-only role and no catalog shortcuts. Map catalog/JDBC mismatch to invalid().
```

- Verification contribution: R1 before B, G1/P1/S1 after B.
- After this file: Explicit method is usable in test, with no callers from production or startup.

#### File 4 — `CREATE docs/egon/spec/2026-11-06-01-25-agent-checkpoint-storage.md`

- Purpose: Record effective typed data, current verifier and migration contract.
- Symbols: REQ-001 through REQ-004 and TEST-001 through TEST-004.
- Repository evidence: Master T4-03, current Agent primary, old Flyway and required fixtures.
- Dependencies and consumers: Maintainer review and Plan §2.
- Why now: Include locked effective Spec in the same source commit after verifying source.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Link current evidence to exact independent requirements and future gates.
- Error and edge behavior: Broken or drifting scope stops the commit.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 layer and existing migration policy.
- Implementation pseudocode:

```text
Copy the strict-validated Spec with exact baseline and V16 identity.
Verify its requirement IDs and reviewed six-path tree match this Step.
Check that the formal requirements include both existing deletion paths and the INSERT-only role.
Verify its related Plan link and leave actual acceptance at the source-only boundary.
```

- Verification contribution: S1.
- After this file: Exact governing Spec is in the commit scope.

#### File 5 — `CREATE docs/egon/plan/2026-11-06-01-26-agent-checkpoint-storage.md`

- Purpose: Record the locked one-Step file and validation sequence.
- Symbols: Step 1 and R1/G1/P1/S1.
- Repository evidence: Current reactor and exact effective Spec.
- Dependencies and consumers: Same Step reviewers.
- Why now: Stage the recorded implementation sequence beside effective Spec.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Exact selectors, source files, package proof and manual scope.
- Error and edge behavior: A drifted baseline, missing old migration evidence or unverified gate blocks stage.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 path and lifecycle boundaries.
- Implementation pseudocode:

```text
Copy the strict-validated one-Step Plan.
Require its primary Spec SHA and git baseline match the files used in verification.
Verify the R1, G1, P1 and S1 commands refer to the exact test and artifact path set.
Check the commit path list against the actual source scope before staging.
```

- Verification contribution: S1.
- After this file: Reviewers can reproduce the finite scope.

#### File 6 — `CREATE docs/egon/evidence/2026-11-06-agent-checkpoint-storage.md`

- Purpose: Capture actual evidence with boundaries.
- Symbols: Actual R1/G1/P1/S1 observations.
- Repository evidence: Fresh raw JUnit XML, artifacts, resource hash and staged diff.
- Dependencies and consumers: Source and operator reviewers.
- Why now: Only actual commands can populate verification results.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Record counts, digests, no old migration mutation and separate operator gate.
- Error and edge behavior: Compile-only, skipped or stale data is never called execution evidence.
- Standards impact: MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 distinguishes source and runtime.
- Implementation pseudocode:

```text
Record the actual one-failure RED and final zero-skip JUnit counts.
Record actual archive and migration SHA, prior SQL equality and no startup call.
State pending V16 role, cascade, capture and S11 operator evidence.
```

- Verification contribution: S1.
- After this file: Focused commit ready when all gates pass.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Use §8 R1, G1, P1 and S1 at the corresponding source phase.
- Expected result: R1 exactly one intended assertThrows failure and zero errors/skips. G1/P1 both exact suites pass with zero failures/errors/skips. P1 exact 4053 V16 bytes and equal existing SQL/config/PaC.
- Failure returns to: Exact failing file or baseline lock. Re-run any affected gate before staging.
- Completion criteria: Strict Spec and Plan, exact six paths, all finite/package evidence, no actual DDL or runtime.
- Rollback: One source revert before deployment. Already applied SQL remains immutable and an independently approved forward correction would govern.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentCheckpointStorageVerifierTest.java`, `stellar-enclosure-tianji-agent-runtime/src/main/resources/db/tianji-agent/V16__add_agent_checkpoint.sql`, `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/AgentStorageVerifier.java`, `docs/egon/spec/2026-11-06-01-25-agent-checkpoint-storage.md`, `docs/egon/plan/2026-11-06-01-26-agent-checkpoint-storage.md`, `docs/egon/evidence/2026-11-06-agent-checkpoint-storage.md`.
- Commit: `feat(agent): prepare checkpoint storage contract`.

## 8. Test, Validation, and Quality Gates

Use the existing offline source reactor, in this exact sequence on the assigned local workspace.

```sh
ckpt_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
ckpt_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
ckpt_base=98ccd8fa189aecf54641d8e55af5a7a02060610d
test "$(git rev-parse --verify HEAD)" = "$ckpt_base"
ckpt_red='AgentCheckpointStorageVerifierTest#rejects_missing_v16_history'
ckpt_core=AgentCheckpointStorageVerifierTest,AgentModelUsageStoreTest
ckpt_finite() {
  JAVA_HOME="$ckpt_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$ckpt_settings" -s "$ckpt_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
ckpt_finite "$ckpt_red"
ckpt_finite "$ckpt_core"
JAVA_HOME="$ckpt_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
  ./scripts/verify-source-reactor.sh -gs "$ckpt_settings" -s "$ckpt_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$ckpt_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-03-agent-checkpoint-storage \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$ckpt_core"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-01-25-agent-checkpoint-storage.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-01-26-agent-checkpoint-storage.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and exact boundary |
| --- | --- |
| R1 | Select only the missing V16 assertion in A. Require fresh owning Surefire XML with tests=1, failures=1, errors=0, skipped=0 and exact expected exception assertion. A compile error fails this gate. |
| G1 | B finite exact classes. Require all five new finite cases, measure the adjacent suite count from fresh owning XML and require zero failures/errors/skips. No Postgres fixture selected. |
| P1 | Actual clean builds repeat the same finite counts. Compare source V16 bytes with AR resource and the same resource in the AR jar nested in AS exec. PS archive has no V16 resource. Compare actual AS/PS configurations and protected PaC with existing content. Confirm all 27 earlier SQL match baseline bytes. |
| S1 | Both strict docs pass. Exactly six paths and no other config, POM, authority or integration report. Production search finds no checkpoint method invocation or writer. |

## 9. Migration, Compatibility, Rollout, and Rollback

The V15 fixture stays explicitly 14→15, the ordinary E fixtures V14, and native fork V9. Flyway is disabled at boot. Apply V16 only in separately authorized AgentDB provisioning with a dedicated operator role. Capture writer/default-off configuration and actual fault/restart evidence are separate.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step and files | Gates |
| --- | --- | --- | --- |
| REQ-001 | §8/§11 | Step 1 Files 1–3 | R1/G1/P1 |
| REQ-002 | §11/§16 | Step 1 Files 1–3 | G1/P1 |
| REQ-003 | §7/§14 | Step 1 Files 1/3 | R1/G1/P1 |
| REQ-004 | §3/§8/§16/§18 | Step 1 Files 1–6 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

No additional decision affects this inert source Step. V16 application/role, actual DDL cascade, normal-path writer, true kill/restart, native resume and full S11 remain separate. A new user/administrator execution authority is outside this change.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

T4-03 calls for one AgentDB checkpoint migration and bounded provenance. This Step delivers only its existing-owner storage prerequisite.

### 12.2 Spec consistency

The six-path sequence and scope match the pinned Spec. No public or startup contract in either document.

### 12.3 Repository executability

Existing offline source reactor and packaged permission-producer proof. Actual deployment requires independent operator controls.

### 12.4 Test and release completeness

R1/G1/P1/S1 are finite source/package gates. Real schema, roles, cascading erase, writer and S11 need separate actual results.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/§7 | Existing AR JDBC and isolated schema ownership | None |
| MC-REUSE-001 | Applicable | PASS | §4.7/§7 | Reuse current technical verifier, Flyway history and JDBC fixtures | None |
| MC-DEP-001 | Applicable | PASS | §6 | No dependency, generator or new build tool | None |
| MC-NAME-001 | Applicable | PASS | §5/§7 | Current verifier, exact technical table and test names | None |
| MC-VALID-001 | Applicable | PASS | §7/§8 | Exact catalog and role shape, closed errors | None |
| MC-MODEL-001 | Applicable | PASS | §5/§7 | Typed historical relational evidence, no new object model | None |
| MC-CONVERT-001 | Not applicable | N/A | §4.8 | No DTO or persistence object conversion | None |
| MC-LOG-001 | Applicable | PASS | §7/§8 | Only fixed technical failure, no sensitive payload or role | None |
| MC-BEAN-001 | Not applicable | N/A | §4.7 | No host Bean or startup wiring | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 | JDK digest and current Spring JDBC only | None |
| MC-JSON-001 | Not applicable | N/A | §4.8/§7 | Database array constraint only, no JSON mapper or API | None |
| MC-TIME-001 | Applicable | PASS | §4.9/§8 | Occurrence UTC and microsecond precision recorded | None |
| MC-CONFIG-001 | Not applicable | N/A | §6/§9 | AS and PS bootstrap defaults preserved | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 | Direct fixed catalog proof in existing component | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7/§8 | Six paths, one V16, zero runtime or authority changes | None |
| MC-TEST-001 | Applicable | PASS | §8 | Exact finite proof and independent operator gates | None |
| MC-BLOCKER-001 | Applicable | PASS | §9/§11 | Writer, DDL provisioning and actual S11 remain independent | None |

### 12.6 Final verdict

PASS — Ready for user review

Actual database, runtime, collector and deployment are outside this source Step.
