# S07 Process START 授权组合验收计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 04:33 Asia/Shanghai |
| Updated | 2026-11-06 04:33 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Two existing PS test sources and three review documents |
| Source Requirement | Master §17.2 S07 and §19 T3-07/T4-05 |
| Baseline Revision | 525f4afa9e0da01583811f292f21331ef9a77709 |
| Implements Spec | [S07 START authorization Spec](../spec/2026-11-06-04-32-s07-process-start-authorization.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 04:32 Asia/Shanghai SHA-256 832201035de44bdeee8629d8d21340cf4864802de9e525bbbf76326da3986f22 |
| Effective Specs | [S07 START authorization Spec](../spec/2026-11-06-04-32-s07-process-start-authorization.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

为 S07 增加 Process Server 侧组合验收。通过实际身份过滤器、权限 guard、HTTP handler、Flowable 和 PostgreSQL 观察一条完整 SERVICE 身份的 START 写入，并验证拒绝与重复请求。

## 2. Target Spec and Effective Design

[S07 START authorization Spec](../spec/2026-11-06-04-32-s07-process-start-authorization.md) §§4/7/11/14 defines the bounded PS fixture. The actual two-service S07 E remains a distinct operator acceptance scope.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §4/7/9 | Actual PS release metadata and exact repeated SERVICE START ACK |
| REQ-002 | §4/7/14 | Expected 401/403/409 for each denied credential, scope, grant or tenant lane |
| REQ-003 | §4/7/11 | One native instance and review task plus exact full-owner APPLIED receipt |
| REQ-004 | §4/8/14/16 | Typed RED, one finite GREEN, actual compiled fixture and scoped docs/source with independent I1/E |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Verify the clean source baseline and scoped absence. Add the shared finite test probe and run typed R1 with a disposable fixture mutation. Restore and connect the existing PG method. Run clean G1 and inspect G2. Fill evidence and run S1. Keep I1 separate.

### 4.2 Test-first strategy

The Step adds test coverage. R1 mutates only the finite test's scoped START resource grant. The negative request reaches a typed mock handler and returns 200 instead of expected 403. Restore the fixture for G1. The selector excludes the PG test class.

### 4.3 Sequential and parallel boundaries

G0, R1, G1, G2 and S1 are sequential in the reviewed source reactor. Operator I1 runs only against a separately authorized fresh loopback fixture.

### 4.4 Commit boundaries

One focused test-only commit after exact finite and compiled evidence plus strict docs validation.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Shared protected servlet probe | Required §7/14 | EVD-001/003 | Extend existing permission test class | Reuse its real chain and PDP fixture |
| Durable witness after real guard | Required §7/11 | EVD-002/003 | Reuse the existing PG class and release fixtures | Add one bounded PG helper invocation |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Finite shared servlet probe | REQ-001/002/004 | G0/R1/G1/G2 | 1 | Existing Process SecurityConfiguration and permission test |
| PG witness | REQ-003/004 | G2/I1 | 2 | Shared probe and existing PG fixture |
| Review and evidence | REQ-004 | S1 | 3–5 | Exact owning evidence |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Existing PS tests | Extend exact two sources and reuse module classpath |
| Authorization and native evidence | Existing filter guard and PG fixture | Use controlled JWT/PDP responses and real guard/service in I1 |
| Production scope | Current product ports and SQL | Test-only coverage with unchanged permissions and migrations |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Actual resource authorization | Existing servlet test chain | EVD-001/003 | Reuse production wiring with fixture IAM | Share its probe in the PG test | Existing test dependencies | R1/G1/I1 |
| Durable effect | Flowable and Process receipt store | EVD-002/003 | Need isolated full owner and digest witness | Use real services and scoped SELECT | Existing test dependencies | G2/I1 |

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
| Rule 1 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse exact PS test classes, typed selectors and descriptive helper names | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–004 | Files 1–2 | Exercise typed START command and production request validation through the real filters | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse ProcessActor, START command, release and instance DTOs | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 4 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse existing test context and production filter registration | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse JUnit, MockMvc, Mockito and Spring JDBC from the current test classpath | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–004 | Files 1–2 | Use the existing starter mapper and managed Jackson DTO serialization | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse existing test properties and the PostgreSQL opt-in | R1/G1/G2/I1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse the production Spring Security filter chain and direct test sequencing | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–004 | Files 1–2 | Reuse fixture Clock and token expiry instants without changing persisted time semantics | R1/G1/G2/I1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–004 | Files 1–2 | Keep existing PS test package and production architecture | R1/G1/G2/I1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Existing test NOW/CLOCK and token validity | Occurred instant | java.time.Instant and Clock | Not persisted | Internal | Not applicable: fixed UTC fixture clock | Not applicable: no fixture display | Fixed test clock with issued time minus 60 seconds and expiry plus 300 seconds | Existing fixture and UTC clock | Existing guards and native receipt time | Step 1 TEST-001/003 |


## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md` |
| CREATE | `docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s07-process-start-authorization.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact clean Service baseline 525f4afa9e0da01583811f292f21331ef9a77709, JDK25, existing offline settings and permission test classpath. Preserve all 28 existing migrations and all runtime wiring. Source selectors exclude PostgreSQL execution.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Compose the actual PS permission and native START boundaries

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Existing PS WebApplicationContextRunner, Process security chain, Flowable and disposable PostgreSQL fixture.
- Baseline state: Existing permission and native tests verify the two boundaries separately.
- Observable outcome: Exact finite denial and typed ACK assertions plus an opt-in native test with one full SERVICE APPLIED receipt.
- End state: Two existing PS test sources and three reviewer documents.
- Test-first gate: Required. G0 exact absence and finite R1 with one 403 versus 200 failure.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`

- Purpose: Add the shared actual servlet permission probe and its finite test.
- Symbols: ProcessPermissionIntegrationTest#governed_start_routes_preserve_service_identity_and_current_permissions, #verifyServiceStartRoutes.
- Repository evidence: EVD-001–004 and the exact Service baseline.
- Dependencies and consumers: Existing PS fixtures, source reviewer and authorized operator.
- Why now: The exact PS S07 composition Step owns this path.
- Contract/signature changes: Test-source or reviewer artifact within the existing fixture boundary.
- Input/output and state mapping: Use exact controlled identities and current policy in the real filter chain; verify the typed request and ACK.
- Error and edge behavior: Wrong identity, receipt, fixture scope or observed gate fails the associated test.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
mvc = webContext(actualSecurity, controlledVerifier, controlledPdp);
require forbidden release and START separately;
require exact release response and all denied identity/scope cases;
require same typed ACK on two START calls from the verified SERVICE;
```

- Verification contribution: R1/G1/G2 and shared I1 assertions.
- After this file: The Step owns this exact artifact.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`

- Purpose: Connect the probe to actual Flowable and PostgreSQL.
- Symbols: ProcessServerPostgresTest#verifyGovernedServiceStartAgainstDatabase.
- Repository evidence: EVD-001–004 and the exact Service baseline.
- Dependencies and consumers: Existing PS fixtures, source reviewer and authorized operator.
- Why now: The exact PS S07 composition Step owns this path.
- Contract/signature changes: Test-source or reviewer artifact within the existing fixture boundary.
- Input/output and state mapping: Publish unique release after existing main native/outbox assertions and query the scoped native task and START receipt.
- Error and edge behavior: Wrong identity, receipt, fixture scope or observed gate fails the associated test.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```java
release = actualReleaseService.publish(testPublisher, isolatedBpmn);
instance = verifyServiceStartRoutes(actualReleases, actualProcesses, release, command);
assert unique native instance, held review task and one exact APPLIED SERVICE receipt;
```

- Verification contribution: G2 and separate I1.
- After this file: The Step owns this exact artifact.

#### File 3 — `CREATE docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md`

- Purpose: Record bounded S07 PS integration requirements.
- Symbols: REQ-001–004 and UC-001/002.
- Repository evidence: EVD-001–004 and the exact Service baseline.
- Dependencies and consumers: Existing PS fixtures, source reviewer and authorized operator.
- Why now: The exact PS S07 composition Step owns this path.
- Contract/signature changes: Test-source or reviewer artifact within the existing fixture boundary.
- Input/output and state mapping: Record real guard, receipt proof and separate E.
- Error and edge behavior: Wrong identity, receipt, fixture scope or observed gate fails the associated test.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record actual filter and guard composition.
Define native and receipt witnesses under I1.
Keep dual-service S07 E separate.
```

- Verification contribution: S1.
- After this file: The Step owns this exact artifact.

#### File 4 — `CREATE docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md`

- Purpose: Lock implementation and exact gates.
- Symbols: Step 1 and G0/R1/G1/G2/S1/I1.
- Repository evidence: EVD-001–004 and the exact Service baseline.
- Dependencies and consumers: Existing PS fixtures, source reviewer and authorized operator.
- Why now: The exact PS S07 composition Step owns this path.
- Contract/signature changes: Test-source or reviewer artifact within the existing fixture boundary.
- Input/output and state mapping: Pin two test paths and exact finite and operator selectors.
- Error and edge behavior: Wrong identity, receipt, fixture scope or observed gate fails the associated test.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Pin the exact five paths and master requirement.
Run finite R1 and G1 with identical method selectors.
Inspect compiled I1 guard without executing it.
```

- Verification contribution: S1.
- After this file: The Step owns this exact artifact.

#### File 5 — `CREATE docs/egon/evidence/2026-11-06-s07-process-start-authorization.md`

- Purpose: Record actual available evidence.
- Symbols: R1/G1/G2/S1 and pending I1.
- Repository evidence: EVD-001–004 and the exact Service baseline.
- Dependencies and consumers: Existing PS fixtures, source reviewer and authorized operator.
- Why now: The exact PS S07 composition Step owns this path.
- Contract/signature changes: Test-source or reviewer artifact within the existing fixture boundary.
- Input/output and state mapping: Separate isolated draft observations from executed reactor and operator results.
- Error and edge behavior: Wrong identity, receipt, fixture scope or observed gate fails the associated test.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record observed R1, G1 and compiled symbols.
Report any omitted operator execution explicitly.
Classify the real two-service E as independent.
```

- Verification contribution: S1.
- After this file: The Step owns this exact artifact.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact G0/R1/G1/G2/S1. I1 is separate.
- Expected result: One expected finite failure, one finite pass, exact compiled test bridge and existing PG guard, no PG execution report.
- Failure returns to: File 1 for finite behavior, File 2 for composed fixture, Files 3–5 for docs and evidence.
- Completion criteria: Exact five paths, finite and compiled source evidence and 28 unchanged migrations.
- Rollback: Revert one focused test-only commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java`, `docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md`, `docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md`, `docs/egon/evidence/2026-11-06-s07-process-start-authorization.md`.
- Commit: test(process): prepare S07 START authorization integration.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| G0 | Baseline | Shared probe and PG helper absent in the two locked test sources |
| R1 | One finite method with a typed temporary fixture mutation | One HTTP status mismatch 403 expected versus 200 actual; zero errors/skips |
| G1 | Clean offline source reactor with exact one-method selector | One passed test; zero failures/errors/skips |
| G2 | Actual compiled class inspection | Shared finite probe and PG helper exist; existing exact PG annotation present; no PG XML report |
| S1 | Strict docs and exact scope | Five paths, existing mock tests byte-identical and 28 immutable migrations |
| I1 | Separate authorized PG | One unskipped owning method and exact native and receipt assertions pass |

```sh
s07_base=525f4afa9e0da01583811f292f21331ef9a77709
s07_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s07_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s07_method=governed_start_routes_preserve_service_identity_and_current_permissions
s07_pg_method=engines_persist_and_flowable_and_outbox_commit_or_roll_back_together
s07_ps=stellar-enclosure-tianji-process-server
s07_class=top.egon.cola.platform.tianji.process.server.ProcessPermissionIntegrationTest
s07_pg_class=top.egon.cola.platform.tianji.process.server.ProcessServerPostgresTest
s07_test_1=stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java
s07_test_2=stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java
test "$(git rev-parse --verify HEAD)" = "$s07_base"
test -z "$(git show "$s07_base:$s07_test_1" | rg -F "$s07_method")"
test -z "$(git show "$s07_base:$s07_test_2" | rg -F 'verifyGovernedServiceStartAgainstDatabase')"
s07_reactor() {
  JAVA_HOME="$s07_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
    ./scripts/verify-source-reactor.sh -gs "$s07_settings" -s "$s07_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "$@"
}
s07_reactor clean "-Dtest=ProcessPermissionIntegrationTest#$s07_method"
"$s07_jdk/bin/javap" -p -v -classpath "$s07_ps/target/test-classes" "$s07_class" "$s07_pg_class"
test -z "$(find "$s07_ps/target/surefire-reports" -name "TEST-$s07_pg_class*.xml" -print)"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md
git diff --check
git diff HEAD --exit-code -- '*.sql' '**/pom.xml' pom.xml
git diff --cached --check
git diff --cached --name-status
```

For R1 use the same exact finite command in the disposable mutation tree. Replace only the grant set directly before the request with suffix `-no-grant` with `Set.of(RELEASE_GET_API, START_API)`. Keep the typed stub and status assertion. Require one expected assertion failure and restore the exact source before G1. G1 owns a fresh ProcessPermissionIntegrationTest XML with one executed passing test. G2 checks the JUnit marker, shared method, PG helper, existing `tianji.process.postgres.test.url` class annotation and absent PG XML.

### Separate operator-only I1

Provide an independently authorized fresh loopback PostgreSQL primary with a `tianji_it_*` name and dedicated existing-format runtime role. Use existing local JDBC authentication and keep credentials out of flags, URLs and evidence. The owning method provisions its scoped test schema through existing setup and still executes its existing assertions.

```sh
: "${TIANJI_S07_PRIVATE_PG_URL:?Provide authorized fresh loopback fixture URL}"
: "${TIANJI_S07_PRIVATE_RUNTIME_ROLE:?Provide authorized fixture runtime role}"
s07_reactor "-Dtest=ProcessServerPostgresTest#$s07_pg_method" \
  "-Dtianji.process.postgres.test.url=$TIANJI_S07_PRIVATE_PG_URL" \
  "-Dtianji.process.postgres.test.runtime-user=$TIANJI_S07_PRIVATE_RUNTIME_ROLE"
```

Require one unskipped executed owning method and retain its current fixture-owned report. Successful I1 establishes the PS-side guard-to-receipt slice. S07 E requires the real Agent transport and isolated IAM plus both services.

## 9. Migration, Compatibility, Rollout, and Rollback

Existing PG selection and migration behavior stay in the existing test. All 28 SQL files remain byte-identical. Revert the single test-only commit to remove this scope.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Files | Gates |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–2 | G1/I1 |
| REQ-002 | Step 1 | 1–2 | R1/G1/I1 |
| REQ-003 | Step 1 | 2 | G2/I1 |
| REQ-004 | Step 1 | 1–5 | G0/R1/G1/G2/S1 |

## 11. Risks, Blockers, and User Decisions

I1 needs the existing independently authorized isolated PG setup. Controlled IAM responses and finite mocks leave actual IAM and the full Agent→Process E scenario separate.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing Process Server test package and production architecture | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing filters, guard, controller, native services and PG gate | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test dependencies; two test Java sources | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | Explicit S07 selector and two named probes | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Real protected routes, denied scope/resource and valid START | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing immutable orchestration values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing controller and SDK conversion | None |
| MC-LOG-001 | Not applicable | N/A | §15 | Existing test and production logging | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Existing production filter beans with fixture-only inputs | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JUnit, MockMvc, Mockito, Spring JDBC and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Existing managed Jackson and typed command/ACK | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Existing test Clock and token validity window | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing PG gate and isolated test context | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing Spring Security chain plus direct fixture sequencing | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact two test paths and three review documents | None |
| MC-TEST-001 | Applicable | PASS | §14 | One finite RED/GREEN and compiled fixture, independent operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | The operator run remains an independent gate | None |

### 12.6 Final verdict

PASS — Ready for user review
