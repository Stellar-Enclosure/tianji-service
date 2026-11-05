# S04 持久 Session 与 Memory 遗忘联合验收实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 03:25 Asia/Shanghai |
| Updated | 2026-11-06 03:25 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL test and three review documents, one focused commit |
| Source Requirement | Master §17.2 S04, §19 T2-01/T2-03/T4-05 |
| Baseline Revision | 16f0dcd5f8e6fb9abc537e86433911a7b085e1be |
| Implements Spec | [S04 composed operator Spec](../spec/2026-11-06-03-24-s04-session-memory-composition.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 03:24 Asia/Shanghai SHA-256 2cb1879514ba3996d941ca5888d87ce8029ab37f0d116a5c5a5c4d72fffb92b0 |
| Effective Specs | [S04 composed operator Spec](../spec/2026-11-06-03-24-s04-session-memory-composition.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在既有隔离 PG/pgvector 夹具中增加一个独立 S04 选择器，验证真实持久 Session、替换执行器、Memory 遗忘与重放。源码只执行有限相邻回归和字节码检查。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[S04 composed operator Spec](../spec/2026-11-06-03-24-s04-session-memory-composition.md) §§4/7/14 locks the scenario, owner scope and operator acceptance.

### 2.2 Effective Spec set

Keep existing Session, Memory, vector and V9/V10 definitions. The source step only adds exact test composition. Operator DB execution remains separate.

### 2.3 Superseded or excluded content

No supersession. No production code, schema, permissions, configuration, hosted service, provider or genuine IAM test.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/7/14 | Existing actual Runner and Session plus exact Memory provenance in first and replacement second turn | AR test |
| REQ-002 | §7/11/14 | Same-ID tenant/scope controls, real forget+replay, no provider/budget for old Session, clean Session succeeds | AR test |
| REQ-003 | §8/14/16 | Four paths, 28 unchanged SQL, 37 finite tests and compiled exact method/guards only | Review evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Verify exact baseline and frozen candidate. Apply only the existing integration test file, then run finite owning suites and inspect bytecode. Record the exact artifact result and stage the four paths.

### 4.2 Test-first strategy

No synthetic RED for the test-only composed operator case. G0 proves the new method absent on the baseline. Actual I1 requires its own authorization and never runs in the source gate.

### 4.3 Sequential and parallel boundaries

G0, source apply, G1 and G2 in order. Documentation and hashes can be reviewed without PostgreSQL or running native operator work.

### 4.4 Commit boundaries

One focused source-only commit after exact G1/G2/S1 and strict docs. Stage only four paths.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Necessity and evidence | Direct alternative | Plan decision |
| --- | --- | --- | --- |
| One composed I1 | Existing partial source coverage, EVD-001–005 | Another mocked Memory unit test | Reuse real composed owner fixture |
| Shared V9/V10 setup | Existing isolated one-method setup, EVD-002 | Duplicate guard, vector and publication | Factor only shared test setup |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Composed opt-in | REQ-001–003 | G0/G2 exact method guard and G1 finite regressions | File 1 | Existing V9/V10 and product adapters |
| Exact evidence | REQ-003 | S1 after actual reports | Files 2–4 | Actual G1/G2 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Architecture | Existing AR Memory PG test | Keep one fixture and one test source |
| Validation | Existing owned Run and Memory contexts plus pinned pgvector | Scoped fixture authority and exact fail-closed assertions |
| Compatibility | Current original V10 test and later historical stages | Keep original guards and origin callback, new explicit opt-in |
| Evidence | Current owner reactor and separate I layer | G1/G2 compile/source separate from operator I1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| V9/V10 fixture | MemoryPostgresTest | EVD-002 | Existing pinned setup and index | Extract shared private setup and preserve original origin callback | No dependency | G2/I1 |
| Session + current Memory | AdkAgentRunExecutor, PostgresAdkSessionService, MemoryContextProvider | EVD-003–005 | Existing behavior needs combined proof | Compose in existing test only | One opt-in method | G2/I1 |
| Forget replay | PostgresMemoryService and MemoryIndexWork | EVD-005 | Existing real APIs | Repeat exact command and BUILD | No production code | I1 |

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
| Rule 1 | §6.2/7 | EVD-001–005 | File 1 | Use existing MemoryPostgresTest and exact semantic method name | G1/G2/S1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–005 | File 1 | Use guarded fixture and exact owner, run, memory and outcome checks | G1/G2/S1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–005 | File 1 | Existing values and private test resource owner, no production DTO | G1/G2/S1 | Step 1 | N/A |
| Rule 4 | §6.2/7 | EVD-001–005 | File 1 | No Bean or production logger | G1/G2/S1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–005 | File 1 | Reuse current ADK, Spring AI, JDK, Mockito, JUnit and JDBC | G1/G2/S1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–005 | File 1 | No JSON mapping or enum code change | G1/G2/S1 | Step 1 | N/A |
| Rule 7 | §6.2/7 | EVD-001–005 | File 1 | Add only a private JUnit property guard | G1/G2/S1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–005 | File 1 | Direct existing fixture reuse, no production abstraction | G1/G2/S1 | Step 1 | N/A |
| Rule 10 | §6.2/7 | EVD-001–005 | File 1 | Existing fixture duration and clock semantics | G1/G2/S1 | Step 1 | N/A |
| Rule 11 | §6.2/7 | EVD-001–005 | File 1 | Keep existing AR PostgreSQL test, schema ownership and current CQE boundaries | G1/G2/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: No production deadline, timestamp, scheduling or conversion changes. The fixture reuses current bounded Run and Memory durations and the existing class timeout.

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/memory/MemoryPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md` |
| CREATE | `docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s04-session-memory-composition.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Use exact Service 16f0dcd5f8e6fb9abc537e86433911a7b085e1be and frozen B SHA in the lock. Owner source gate uses JDK25, current offline Maven settings and all PG opt-ins unset. Actual I1 requires an independent fresh loopback tianji_it_ DB with tianji_agent absent and exact preinstalled vector patch. Never select both MemoryPostgresTest methods in one operator command, because each starts from an empty schema. Existing class guard remains authoritative.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Prepare the exact composed S04 operator fixture

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Existing MemoryPostgresTest, V9/V10 fixture, scoped authority, Session and owning Runtime.
- Baseline state: Existing isolated Memory and Session proof, no composed exact method.
- Observable outcome: New compiled opt-in method crosses first/replacement/forget/replay/current-scope execution.
- End state: One existing test source and three docs in one verified commit.
- Test-first gate: Not applicable. New test-only operator scenario without changed production behavior; G0 checks method absence.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/memory/MemoryPostgresTest.java`

- Purpose: Add exact composed S04 method and reuse private setup.
- Symbols: MemoryPostgresTest#replacement_executor_revalidates_owned_durable_memory_history_and_blocks_forgotten_sources.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime test owner and source reviewer.
- Why now: Execute exact test source before scope and evidence review.
- Contract/signature changes: Test-local or review artifact only.
- Input/output and state mapping: Preserve existing class guard and tenant-only old event authority. Share existing V9→V10 guarded setup, then attach stricter scoped authority for the new exact method. Real product adapters, two tenants, same IDs and one sibling scope.
- Error and edge behavior: Require existing isolation and fail closed on a stale source or invalid evidence.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
with openV10Fixture(exactAuthorityFactory, scopedBuildAuthority):
  publish and index owned/foreign/sibling synthetic facts under exact keys;
  run first and replacement CONFIG executors on one stored Session;
  assert matching Memory source digest and lineage, no foreign marker;
  forget, replay exact UPSERT and old BUILD;
  assert tombstone, no target vector, stale Session failure before Fake and budget;
  assert fresh and other-scope controls remain valid.
```

- Verification contribution: G0/G1/G2/I1.
- After this file: Exact source or review artifact available for its gate.

#### File 2 — `CREATE docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md`

- Purpose: Record exact composition and guards.
- Symbols: REQ-001–003.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime test owner and source reviewer.
- Why now: Execute exact test source before scope and evidence review.
- Contract/signature changes: Test-local or review artifact only.
- Input/output and state mapping: Describe local authority and test behavior, including no real runtime acceptance in source turn.
- Error and edge behavior: Require existing isolation and fail closed on a stale source or invalid evidence.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```text
Record verified synthetic owner and the precise reviewed scope.
Describe actual Runtime, V9 Session lineage and V10 fact/vector components.
Bind original and replacement outcomes and post-forget replay to exact tests.
Keep operator execution separate from G2.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact available for its gate.

#### File 3 — `CREATE docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md`

- Purpose: Lock ordered fixture and verification.
- Symbols: Step 1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime test owner and source reviewer.
- Why now: Execute exact test source before scope and evidence review.
- Contract/signature changes: Test-local or review artifact only.
- Input/output and state mapping: Record exact B source hash, owner finite selection and exact disposable operator selector.
- Error and edge behavior: Require existing isolation and fail closed on a stale source or invalid evidence.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```text
Record the exact approved baseline and Spec hash.
Apply the sole existing test file and run exact 37-case owner reactor.
Inspect compiled method, class and method guards with javap, and no PostgreSQL report.
Give only the single exact operator I1 selector and all three required properties.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact available for its gate.

#### File 4 — `CREATE docs/egon/evidence/2026-11-06-s04-session-memory-composition.md`

- Purpose: Record actual source and scope evidence.
- Symbols: G0/G1/G2/S1.
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Agent Runtime test owner and source reviewer.
- Why now: Execute exact test source before scope and evidence review.
- Contract/signature changes: Test-local or review artifact only.
- Input/output and state mapping: Replace pending statements with actual observed reports and explicit runtime gaps.
- Error and edge behavior: Require existing isolation and fail closed on a stale source or invalid evidence.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```text
Record the exact owning report names/counts and compiled guard.
Record the four-path diff and 28 unchanged SQL bytes.
Keep PostgreSQL, native tool exchange, true restart and full S04 separate.
```

- Verification contribution: S1.
- After this file: Exact source or review artifact available for its gate.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Exact §8 G0/G1/G2/S1. Do not execute I1 in source delivery.
- Expected result: 37 finite pass, compiled exact operator method and both guards, no PostgreSQL report.
- Failure returns to: File 1 for test compile, guards or scope; Files 2–4 for reviewer evidence.
- Completion criteria: Actual finite and bytecode evidence plus exact four-path scope and 28 existing migrations.
- Rollback: Revert one source commit.
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/memory/MemoryPostgresTest.java`, `docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md`, `docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md`, `docs/egon/evidence/2026-11-06-s04-session-memory-composition.md`.
- Commit: test(agent): compose S04 durable session and memory forget acceptance.

## 8. Test, Validation, and Quality Gates

```sh
s04_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s04_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s04_base=16f0dcd5f8e6fb9abc537e86433911a7b085e1be
test "$(git rev-parse --verify HEAD)" = "$s04_base"
s04_core=MemoryContextProviderTest,AdkRunContextTest,AdkSessionEventCodecTest
env -u tianji.memory.postgres.test.url -u tianji.memory.postgres.session-context.enabled   JAVA_HOME="$s04_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$s04_settings" -s "$s04_settings" -o clean -B -ntp   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$s04_core"
"$s04_jdk/bin/javap" -p -v   -classpath stellar-enclosure-tianji-agent-runtime/target/test-classes   top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryPostgresTest
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-03-24-s04-session-memory-composition.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-03-25-s04-session-memory-composition.md
git diff --check
git diff --cached --check
git diff --cached --name-only
```

| Gate | Evidence and boundary |
| --- | --- |
| G0 | Exact baseline and absent new method. Original class and default guard still present. |
| G1 | 8 MemoryContextProvider, 23 AdkRunContext and 6 AdkSessionEventCodec finite cases; 37 total, no failures/errors/skips. |
| G2 | Owning target test class exists. javap shows exact method, class-level tianji.memory.postgres.test.url and method tianji.memory.postgres.session-context.enabled. Require no MemoryPostgresTest test report. |
| S1 | Both strict validators pass and exact four paths. All 28 production migrations byte-identical. |
| I1 | Separately authorized operator exact method only, requiring actual PostgreSQL and a pinned preinstalled vector patch in an isolated fresh DB. |

### 8.1 Separate operator-only I1

Require a disposable loopback primary with tianji_it_ DB name, no tianji_agent schema, preinstalled pinned vector extension, and independent fixture authentication outside the URL. Provide exact URL and vector patch through operator-controlled variables. Do not run the class or a broader suite with both setup methods, and do not reuse an existing fixture DB. Do not execute this command in the current source gate.

```sh
: "${TIANJI_S04_DISPOSABLE_URL:?explicit empty loopback PG URL required}"
: "${TIANJI_S04_PINNED_VECTOR_VERSION:?exact installed vector patch required}"
JAVA_HOME="$s04_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$s04_settings" -s "$s04_settings" -o -B -ntp   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none   '-Dtest=MemoryPostgresTest#replacement_executor_revalidates_owned_durable_memory_history_and_blocks_forgotten_sources'   "-Dtianji.memory.postgres.test.url=$TIANJI_S04_DISPOSABLE_URL"   "-Dtianji.memory.postgres.vector.version=$TIANJI_S04_PINNED_VECTOR_VERSION"   -Dtianji.memory.postgres.session-context.enabled=true
```

I1 requires one exact passing method, no skips, and operator evidence bound to the isolated environment. Compilation or a skipped case never counts.

## 9. Migration, Compatibility, Rollout, and Rollback

No production change or migration. Existing PG method retains its original fixture tenant-only BUILD authority and prior V11/V12 stages. Revert one test/docs commit if needed.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1 | G2/I1 |
| REQ-002 | Step 1 | 1 | G2/I1 |
| REQ-003 | Step 1 | 1–4 | G0/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

Actual PostgreSQL, vector extension and full S04 acceptance remain operator responsibilities. Test authority and deterministic providers do not prove deployed IAM, vendor service or full process restart.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6/7/8 | Existing AR integration fixture owns composed proof | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | Reuse V9/V10 fixture, CONFIG executor, session and Memory adapters | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing JDK/ADK/JDBC/JUnit | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | Exact owner test and method selector | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Guard disposable PG plus exact Memory/Run authority and two owner scopes | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Current typed values and private test fixture only | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | No production DTO mapping | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No logger or new exported content | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8 | Test-local construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Current JDK, Mockito and ADK Fixture | None |
| MC-JSON-001 | Not applicable | N/A | §10 | Existing native context and fact codecs | None |
| MC-TIME-001 | Not applicable | N/A | §6.3 | No changed time contract | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing PG properties and additional test-only method guard | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Compose existing real adapters and bounded fakes in exact test owner | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One Java test plus three docs, all 28 SQL unchanged | None |
| MC-TEST-001 | Applicable | PASS | §14 | Finite and compiled/source proof separately from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | No live PG needed for the source gate | None |

### 12.6 Final verdict

PASS — Ready for user review
