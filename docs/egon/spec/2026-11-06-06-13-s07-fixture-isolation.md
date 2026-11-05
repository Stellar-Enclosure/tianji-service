# S07 Agent 验收配置扫描隔离

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Fix |
| Complexity | Simple |
| Complexity Drivers | Two existing test configurations must be mutually exclusive in an existing scan. |
| Created | 2026-11-06 06:13 Asia/Shanghai |
| Updated | 2026-11-06 06:13 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | S07 and ordinary Agent acceptance fixture selection |
| Change Surface | Three AS test Java files and three review documents |
| Affected Chapters | §7, §8, §14, §16 |
| Source Requirement | [主计划 T4-05 / S07](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 9364b27a96c0ae66687784425055a4fab217ad17 |
| Amends | None |
| Supersedes | None |
| Depends On | [S07 Agent to Process fixture](2026-11-06-05-51-s07-governed-start-e.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Fixture isolation Plan](../plan/2026-11-06-06-14-s07-fixture-isolation.md) |

## 1. Summary

Make existing ordinary and S07 test configuration mutually exclusive even when the application scan sees both classes.

## 2. Background and Current State

| Evidence ID | Repository symbol | Finding |
| --- | --- | --- |
| EVD-001 | ExpenseAgentAcceptanceMain.FixtureConfiguration | Existing authorization condition alone also admits the ordinary test configuration in authorized S07 mode. |
| EVD-002 | S07GovernedStartAgentFixtureConfiguration | Existing S07 main chooses the fixture explicitly. A scan can independently discover the test configuration and must use both conditions. |
| EVD-003 | S07GovernedStartAgentFixtureTest | Add exact scan candidate matrix without starting a context or a service. |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing fixture source, with no generated project or example cleanup.

## 3. Goals and Non-goals

Preserve existing ordinary and S07 behavior under exact selection. Production bootstrap, bean contracts and actual operator execution remain independent.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS test configuration | Affected | EVD-001–003 | Exact conditional class selection | Conditions, test matrix and file scope | §7, §8, §14, §16 |
| Runtime, catalog and DB | Unchanged | Existing AS and PS classes | Existing production ownership | Scope | §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Acceptance criterion |
| --- | --- | --- |
| REQ-001 | Existing authorization and exact mode select mutually exclusive fixtures | Neither without authorization, only ordinary for authorized false/missing mode, only S07 for authorized true. |
| REQ-002 | Prove both conditions independently and preserve nearby fixture gates | Two typed negative assertions, 26 finite tests and exact six paths. |

### 4.2 Use-case analysis

| Actor ID | Actor |
| --- | --- |
| ACTOR-001 | Authorized isolated acceptance launcher. |

| ID | Use case/goal | Actor | Trigger | Result |
| --- | --- | --- | --- | --- |
| UC-001 | Select the intended fixture under a component scan | ACTOR-001 | Existing authorized flag and S07 mode | Exactly the authorized fixture candidate or none. |

## 5. Constraints, Assumptions, and Decisions

Use exact true/false conditions on existing fixture properties. Keep ordinary compatibility for missing S07 mode. The finite scan only obtains candidates.

## 6. Project Technology Context

JDK 25, Spring Boot conditional properties, Spring classpath scanning and JUnit.

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
| Rule 1 | Yes | EVD-001–003 | Use existing technical names and one focused test method. | §7 | TEST-001/002 | PASS |
| Rule 2 | Yes | EVD-001–003 | Require exact existing authorization and exact fixture mode. | §7 | TEST-001/002 | PASS |
| Rule 3 | No | EVD-001–003 | No POJO or DTO change. | §7 | TEST-001/002 | N/A |
| Rule 4 | Yes | EVD-001–003 | Spring conditions isolate existing technical fixtures. | §7 | TEST-001/002 | PASS |
| Rule 5 | Yes | EVD-001–003 | Reuse Spring scanner and existing JUnit. | §7 | TEST-001/002 | PASS |
| Rule 6 | No | EVD-001–003 | No wire or persisted shape. | §7 | TEST-001/002 | N/A |
| Rule 7 | Yes | EVD-001–003 | No property introduced; existing mode now gates fixtures. | §7 | TEST-001/002 | PASS |
| Rule 9 | Yes | EVD-001–003 | Reuse existing condition pattern and main switch. | §7 | TEST-001/002 | PASS |
| Rule 10 | No | EVD-001–003 | No time behavior. | §7 | TEST-001/002 | N/A |
| Rule 11 | Yes | EVD-001–003 | Keep test fixture layer and existing DB ownership. | §7 | TEST-001/002 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Not applicable

Reason: The change reads existing Boolean properties during component scanning and does not change a time-bearing field or time boundary.

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Need |
| --- | --- | --- | --- |
| Conditional gates | Modify | REQ-001 | Scan can discover both existing fixtures. |
| Finite scan matrix | Modify | REQ-002 | Directly checks discovery without starting a fixture. |

| Path | Network calls | Client states | Decision |
| --- | --- | --- | --- |
| Fixture scan | None | No service context | Existing property conditions. |

### 7.1 System Architecture Design

Spring component discovery applies existing authorization and exact fixture mode to the two test configurations.

### 7.2 High-Level Design

Absent authorization gives no candidate. Authorized mode absent or false gives ordinary. Authorized mode true gives S07.

### 7.3 Detailed Design

The existing ordinary fixture class keeps `run-authorized=true` and additionally requires `s07-governed-start=false` or missing. The existing S07 fixture requires both properties true. The finite test uses isolated mock environment values and classpath component scanning, then compares the exact candidate sets.

## 8. Package Structure and Code File Tree

| Path | Operation | Responsibility |
| --- | --- | --- |
| `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureTest.java` | Modify | Use an isolated component scanner with four exact activation states. |
| `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` | Modify | Add the ordinary fixture mode discriminator to the existing authorization condition. |
| `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S07GovernedStartAgentFixtureConfiguration.java` | Modify | Add both exact authorization and S07 mode conditions to its scanned class. |
| `docs/egon/spec/2026-11-06-06-13-s07-fixture-isolation.md` | Create | Describe exact test configuration isolation and finite acceptance. |
| `docs/egon/plan/2026-11-06-06-14-s07-fixture-isolation.md` | Create | Lock two RED cases and the exact finite selector. |
| `docs/egon/evidence/2026-11-06-s07-fixture-isolation.md` | Create | Record actual finite report and exact scoped source facts. |

## 9. Interface Definitions

No interface change. The existing fixture flags keep their operator interpretation.

## 10. POJO and Data Model Design

No model changes.

## 11. Database Design

No DB interaction or migration.

## 12. Frontend Page Design

No frontend changes.

## 13. Design Patterns and Architecture Principles

Reuse Spring conditional configuration with a finite scan proof.

## 14. Test Design

| Test ID | Coverage |
| --- | --- |
| TEST-001 | Remove each new discriminator independently and observe exact scan-set mismatch. |
| TEST-002 | Clean 26 finite tests, scoped paths and all 28 migration bytes. |

## 15. Non-functional and Cross-cutting Design

No child startup, network or database interaction in finite proof.

## 16. Compatibility, Migration, Rollout, and Rollback

Ordinary fixture still accepts absent mode and explicit false. Existing opt-in S07 operator run remains separately required. Revert the scoped test-source commit if needed.

## 17. Alternatives and Decisions

Keeping only explicit main selection would leave scanned classes unguarded. Existing Spring conditional properties are sufficient.

## 18. Risks and Open Questions

Operator I/E requires actual isolated children, IAM and PostgreSQL. This test checks source selection.

## 19. Traceability Matrix

| Requirement | Use case | Implementation | Evidence |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | Existing test configuration conditions | TEST-001/002 |
| REQ-002 | UC-001 | Finite scanner | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-REUSE-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-DEP-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-NAME-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-MODEL-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-CONVERT-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-LOG-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-BEAN-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-UTIL-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-JSON-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-TIME-001 | Not applicable | N/A | §3/10 | Test configuration and scan assertions do not touch this layer. | None |
| MC-CONFIG-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-TEST-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/14 | Existing test-only configuration, exact mode matrix and narrow paths. | None |

### 20.6 Final verdict

PASS — Ready for user review
