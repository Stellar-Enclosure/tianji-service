# T4-01 B1 BPMN 迁移预检 PostgreSQL 验收规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Guarded real Flowable and PostgreSQL operator fixture with controlled access |
| Created | 2026-11-06 06:47 Asia/Shanghai |
| Updated | 2026-11-06 06:47 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | One new PS PostgreSQL fixture and three review documents |
| Change Surface | Test-only fixture and review evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §19 T4-01 B1](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 4a090b111b7bc27e2954586548f5b0461208354d |
| Amends | None |
| Supersedes | None |
| Depends On | [Owned migration preflight API §7/9](2026-11-05-23-44-process-migration-preflight-api.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Migration preflight PostgreSQL proof](../plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md) |

## 1. Summary

复用现有隔离 ProcessDB 和临时角色，发布两个不同版本的同名普通等待 Timer，调用现有 owner 迁移预检，并用原生数据行比较证明单实例结构校验无迁移写入。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ProcessServerPostgresTest#formalCaseDatabase and #context | Existing explicit disposable loopback PostgreSQL bootstrap, Flowable schema, existing V1–V8 and dedicated runtime role |
| EVD-002 | ProcessMigrationPreflightController#get and ProcessMigrationPreflightService#inspectForResourceActor | Exact resource and dual read actor, one native structural validator call on the owning primary |
| EVD-003 | ProcessMigrationPreflightControllerTest, ProcessMigrationPreflightServiceTest and ProcessStorageConfigurationTest | Existing finite mocks and configuration checks with no native PostgreSQL fixture |
| EVD-004 | ProcessMigrationPreflightService#verifyReadiness, ProcessControlNativeAccess#verifyMigrationReadCoverage and unchanged V9/V10 | Existing V10 history and recovery receipt SELECT required, explicit native UTC and isolation gates |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: One test-only Process Server operator fixture and reviewer documents.

## 3. Goals and Non-goals

Supply an independently selected operator fixture for the current protected GET, real native validation and same-source read-only path. Real IAM and HTTPS, migration execution and release activation remain separate.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS test | Affected | EVD-001–004 | Independent operator fixture using the existing controller and service | Fixture and evidence | §7, §8, §14 |
| ProcessDB | Context-only | EVD-001/004 | Existing V1–V10 inside the disposable fixture | Existing schema and witnesses | §11 |
| Production APIs | Context-only | EVD-002 | Existing published GET and controlled IAM boundary | Existing contract | §9, §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Independent operator fixture | Must | Fresh existing loopback URL and dedicated role guards, explicit UTC and method flag, V1–V10 in disposable ProcessDB | EVD-001/004 |
| REQ-002 | Real native structure observation without mutation | Must | Two exact same-key releases, one live timer, three exact access calls, positive native result, target pin mismatch, distinct issuer denial and native variable blocker, with equal before/after rows | EVD-001/002/004 |
| REQ-003 | Separate source and runtime evidence | Must | 21 finite tests, compiled exact selector, four paths and byte-identical 28 existing migration files | EVD-003 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | Independent disposable loopback PostgreSQL, dedicated role, UTC JVM |
| ACTOR-002 | Source reviewer | Finite and compiled gates only |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify current native timer observation | ACTOR-001 | Exact I1 and fresh DB | Publish exact releases, start source and exercise current GET controller plus real native service | Wrong pin, foreign owner or variable gives defined safe outcome | Same native and START receipt rows | REQ-001/002 |
| UC-002 | Review source without PostgreSQL | ACTOR-002 | Clean baseline and existing offline settings | G0/G1/G2/S1 | Wrong selector or proof rejects | I1 remains separate | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

I1 selects validates_one_owned_native_timer_without_moving_it and tianji.process.postgres.migration-preflight=true with the existing loopback URL, temporary runtime role, and JVM `-Duser.timezone=UTC`. Existing provisioner migrates V1–V8; the isolated test applies existing V9/V10 and gives the temporary role only SELECT on the V10 worker recovery receipt. The Process connection uses fixed UTC. Real Flowable async acquisition is disabled for a deterministic waiting timer.

## 6. Project Technology Context

JDK25, JUnit, Flowable 8, Spring JDBC and the existing test-only PostgreSQL fixture tooling.

### 6.2 User-mandated Java rule compliance

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

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–004 | Use the existing Process test package and one exact operator method. | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Reuse the exact loopback, actor, typed query and role checks. | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–004 | Existing immutable API types and private immutable row witness. | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–004 | No production bean, dependency injection or logging changes. | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Use JDK, JUnit, Spring JDBC and existing test capabilities. | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–004 | Compare native rows through actual PostgreSQL JSONB without new serialization. | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | Yes | EVD-001–004 | Keep source profiles and packaged configuration, one explicit test-only JVM flag. | §7/8/14 | TEST-001–003 | PASS |
| Rule 9 | Yes | EVD-001–004 | Reuse existing Test Fixture and actual read-only Query. | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–004 | Keep existing Instant, explicit UTC and duration semantics. | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Reuse the existing PS test-side PostgreSQL setup and its unchanged schema. | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt and native timer due time | Occurred instant | java.time.Instant and existing native JDBC conversion | Legacy UTC | UTC Z | Not applicable: absolute instant | Not applicable: no display | Explicit UTC JVM and JDBC connection, no shared default change | Existing PostgreSQL clock and native timestamp precision | Existing native timestamps interpreted in UTC and converted to UTC Instant by the existing PostgreSQL/JDBC mapping | Step 1 TEST-003 |
| TIME-002 | timer wait, test and read limits | Duration | ISO Duration and JUnit Timeout | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | PT1H fixture timer remains in future, existing two-second observation query and test deadline | Existing native and JUnit clocks | Keep existing scheduler and SQL timeouts | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One independently selected test | Add | REQ-001/002 | Finite preflight tests | No composed actual Process primary and native engine witness | Test-only file | Use |
| Existing isolated bootstrap and controller/service | Reuse | REQ-001/002 | Repeat fixture and native preflight logic | Need exact existing behavior | Two existing version migrations and test-local access stand-in | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | Disposable PostgreSQL only | Real Process engine and controlled access | Existing native and receipt rows | Any drift or unexpected outcome fails |
| G0/G1/G2/S1 | None | Source and class-only | Finite reports only | No operator selector |

### 7.1 System Architecture Design

Compose existing ProcessReleaseService, ProcessExecutionService and ProcessMigrationPreflightController/Service in the same actual Process fixture. Only TianjiProcessAccess is substituted in process. Record native rows through the separate fixture observer.

### 7.2 High-Level Design

Publish two deployments with identical ordinary timer graph and distinct names and manifest hashes. Start the source under a verified actor and assert one native timer. Use the independently returned target manifest as the exact GET pin.

### 7.3 Detailed Design

Call the actual controller method, prove three ordered existing resource checks and assert NATIVE_STRUCTURE_VALIDATED_ONLY. Repeat with a valid wrong digest and with mismatched resource actor and same-name foreign issuer. Compare complete execution, timer, variable and START receipt rows. Add one native variable solely as fixture setup, snapshot again, require ACTIVE_EVIDENCE_UNSAFE and compare rows after the negative query.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessMigrationPreflightPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-06-47-bpmn-migration-preflight-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-06-48-bpmn-migration-preflight-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-bpmn-migration-preflight-postgres.md` |

## 9. Interface Definitions

No new interface. The existing controller and its two exact query parameters are exercised in process.

## 10. POJO and Data Model Design

Reuse exact typed owner, release, Process command, preflight response and immutable private NativeRowsVO list snapshots.

## 11. Database Design

Relational model change: No. Use actual existing V1–V10 through an authorized fresh fixture and SELECT-only V10 coverage. Both snapshots query exact instance rows. All 28 source migrations remain byte-identical.

## 12. Frontend Page Design

This PS integration test adds no frontend path.

## 13. Design Patterns and Architecture Principles

Direct Test Fixture composition and the existing read-only Query keep native behavior owned by PS. Persisted witnesses come from an independent fixture observer.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean owning G1 runs controller, service and storage-configuration finite suites, 21 exact cases. |
| TEST-002 | Baseline absence, actual compiled method and separate opt-in JVM gates, no PostgreSQL report, exact scope and migrations. |
| TEST-003 | Separate I1 uses actual native releases, timer, owner receipt and primary read-only validation. Snapshot equality proves no migration writes for all checked paths. |

## 15. Non-functional and Cross-cutting Design

Use existing bounded Process query and isolated test deadline. Evidence retains outcome and counts without temporary fixture credentials.

## 16. Compatibility, Migration, Rollout, and Rollback

One new test method in a separate class. Existing test selectors and deployment stay compatible. Revert the single test-only commit to remove the fixture.

## 17. Alternatives and Decisions

Finite mock tests already cover deliberate guard negatives. Add an actual operator-only Process primary fixture to exercise native behavior.

## 18. Risks and Open Questions

Actual PostgreSQL/Flowable I1 requires the authorized isolated operator environment. The fixture scope leaves actual IAM, HTTPS, formal migration execution and activation independent.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/11 | TEST-002/003 |
| REQ-002 | UC-001 | §7/10/14 | TEST-003 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

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

### 20.6 Final verdict

PASS — Ready for user review
