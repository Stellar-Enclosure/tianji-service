# T4-02 S12 原始所有者保护读取验收规格

| Field | Value |
| --- | --- |
| Document | 2026-11-06-07-30-s12-owner-observation-e.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One operator selector reusing two existing owned flows and three existing typed reads |
| Created | 2026-11-06 07:30 Asia/Shanghai |
| Updated | 2026-11-06 07:30 Asia/Shanghai |
| Owner | Tianji acceptance fixtures |
| Repository | tianji-service |
| Scope | Six Java test paths and three review documents |
| Change Surface | Private operator selector, credential binding and owner evidence |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | Master §17.2 S12 and §19 T4-02/T4-05 |
| Baseline Revision | Service 9013b635dcc950968a8c100b10d6725bc5543924 |
| Amends | None |
| Supersedes | None |
| Depends On | [Java owner SDK](2026-11-06-07-04-java-owner-correlation-sdk.md#73-detailed-design), [S12 committed metrics](2026-11-06-04-38-s12-metrics-subset.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [S12 owner observation](../plan/2026-11-06-07-31-s12-owner-observation-e.md) |

## 1. Summary

为 S12 增加独立操作者入口。两个租户走相同关联标识的正常 expense 流程，各自使用原始 Process USER 与原始 bridge SERVICE 的精确 READ 凭据，核对 Process START、Process Agent outcome 和独立 AS Run。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ExpenseAcceptanceHarness and BusinessProcessAgentAcceptanceIT.Driver | 已有私有 manifest、锁定制品、双租户和三个隔离库。 |
| EVD-002 | S12MetricsAcceptanceIT and S12MetricsCollectorEvidence | 双租户正常流程和真实原始指标证据可直接复用。 |
| EVD-003 | HttpProcessClient#listByCorrelation and #getAgentOutcomes | 已有保护读取和类型化回显。 |
| EVD-004 | HttpAgentClient#listByCorrelation and Driver#agentStatus | 已有原始 AS owner 查询和带完整 bridge 身份的独立持久观察。 |
| EVD-005 | AuthorizationServerConfiguration OAuth access token customizer and TenantIssuerAuthenticationManagerComponent | 真实 OAuth TENANT token 有精确 issuer、subject、tenant、scope 和 audience。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing acceptance test sources. No archetype or generated module.

## 3. Goals and Non-goals

交付双租户的 protected owner-read E 入口、可反证的有限证据和明确操作者前置条件。完整 S12、Tool/Model 历史、告警、部署和独立的其他 E 选择器分开验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Private owner fixture | Affected | EVD-001–005 | Exact suite, credential preflight and evidence | Detailed implementation and test | §7, §8, §10, §13, §14, §15, §16 |
| Production APIs and storage | Context-only | EVD-003/004/005 | Existing owner and read contracts | Existing contract | §9, §11 |
| Frontend | Unchanged | EVD-001 | Operator selector | Scope boundary | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Independent opt-in and exact roles | Must | Manifest suite and selector match. Each tenant supplies independent dedicated Process USER READ and AS bridge SERVICE READ tokens before startup. | EVD-001/005 |
| REQ-002 | Exact Process owner proof | Must | One matching START receipt, one reconciled Agent outcome and exact 404 for each swapped foreign instance. | EVD-001/003 |
| REQ-003 | Exact AS owner proof | Must | Same-correlation Agent result has exactly one expected bridge run whose ID, definition, version, creation time and status match the existing owner-scoped database witness. | EVD-004 |
| REQ-004 | Distinct finite, package and actual E proof | Must | Exact AS identity mutation fails, finite tests pass, and actual E requires passed report and original Collector evidence. | EVD-002/004 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Audit finite assertions and artifact scope | JDK 25 and local artifacts |
| ACTOR-002 | Isolated operator | Execute actual protected reads | Explicit isolated fixture approval and credential issuance |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify source witnesses | ACTOR-001 | JDK and JUnit | Exact finite selectors | Locked source | Foreign run mutation fails and candidate passes | Wrong failure or skipped test fails | E remains separate | REQ-001/002/003/004 | Test-only validator | TEST-001/002/003 |
| UC-002 | Prove both original owners | ACTOR-002 | AS, PS, IAM, PostgreSQL and Collector | Exact E selector | Fresh approved fixture with exact private READ inputs | Six exact reads, two swapped 404 and two raw metric series | Any ownership or raw evidence mismatch fails | Artifacts stay private | REQ-001/002/003/004 | Existing typed HTTPS | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

使用独立属性 `tianji.s12.owner-observation.manifest` 和 suite `S12_OWNER_OBSERVATION`。每个私有 tenant credential file 提供 `token.s12-process-owner-read` 与 `token.s12-agent-bridge-read`，其他 fixture 凭据继续走原启动路径。Process USER 对应 manifest owner subject、Process audience 和唯一 `tianji:process:read`，AS SERVICE 对应 `identity.bridge-subject`、AS audience 和唯一 `tianji:agent:read`。真实 token 由已批准的 IAM 签发，需覆盖保护读取时段。

## 6. Project Technology Context

JDK 25、现有 Java SDK、Jackson 3、JUnit、Spring JDBC 和已有 metrics 测试基础设施。

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
| Rule 1 | Yes | EVD-001–005 | 沿用 PS 测试包，名称标明 S12 owner observation。 | §7/8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 在 fixture 边界验证凭据和类型化证据。 | §7/8 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 沿用公开 immutable DTO 和一个内部持久观察记录。 | §7/8 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–005 | 沿用现有测试子进程和 Registry。 | §7/8 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | 复用 Java SDK、JDBC、Jackson 和 JUnit。 | §7/8 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–005 | 使用严格 Jackson 预检 OAuth payload，不保留解析异常。 | §7/8 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–005 | 新私有 suite 只向隔离 PS 注入现有 outcome 开关。 | §7/8 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 沿用 Test Fixture 和 HTTP Adapter。 | §7/8 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | 按各自主库时钟使用微秒 UTC 半开窗口。 | §7/8 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | 保持测试和生产边界与已有 AS/PS 存储归属。 | §7/8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | fromInclusive, toExclusive, receipt submittedAt and Agent createdAt | Occurred instant | java.time.Instant and OffsetDateTime | UTC instant | UTC Z | Absolute instant | No display | Each owning store gives a single exclusive upper bound and one-hour inclusive lower bound for both tenants | Existing primary database microseconds | Existing correlation queries and typed times | Step 1 TEST-001/004 |
| TIME-002 | Request timeout and Collector deadline | Duration | java.time.Duration and System.nanoTime | Not persisted | Internal | Elapsed time | No display | Five seconds for each HTTP request and existing Collector poll | Existing monotonic deadline | Existing fixture timeout | Step 1 TEST-002/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Necessity |
| --- | --- | --- | --- |
| Dedicated owner observation suite | Reuse the existing metrics harness and select exact credentials | REQ-001 | Keep operator opt-in independent |
| Typed evidence comparison | Check Process snapshots and the full owner-scoped durable AS row | REQ-002/003 | Same correlation is deliberately shared |
| Finite and operator checks | Type-valid RED and actual E artifacts | REQ-004 | Preserve proof boundary |

| Path | Network calls | Client states | Reason |
| --- | --- | --- | --- |
| Normal workflow | Existing HTTPS | Existing | One workflow per tenant |
| Owner observations | Eight existing protected reads | Two owner identities per tenant | Six exact successes and two reversed 404 |

### 7.1 System Architecture Design

复用现有已锁定 AS/PS Boot 子进程、原正常路径、三个隔离库、固定 Collector 和有界生命周期。只有新模式在 PS 子进程设置 `stellar.tianji.process.agent-results.owner-outcomes-enabled=true`。

### 7.2 High-Level Design

每条正常流程在自己的时限内等 AS 和 PS 两侧 dispatch CLOSED。两条路径均收敛后验证原有租户隔离，按各自数据库时钟给两租户相同的只读查询区间。每个租户分别向 PS 与 AS 发起三项保护读取，再用同一 PS USER 查询另一租户实例并要求 404。最后复用原独立 Collector 校验。

### 7.3 Detailed Design

操作者预检解码 JWT payload 核对 TENANT、issuer、subject、tenant、唯一 resource audience 和精确 READ scope。解析前复用 8 KiB 私有值上限，拒绝重复 JSON 字段和尾随内容，异常只带固定信息。该预检用于阻止误发凭据，签名、时效和授权由真实服务验证。测试锁定已核对的凭据用于本轮请求。

Process START 回执必须是同 correlation 的唯一无游标项目，实例 ID、release 和提交时刻与原始已核验实例一致。实例 outcome 必须是唯一无游标的 `agentStart` 项，dispatch CLOSED、Inbox APPLIED、Agent COMPLETED。AS LIST 必须是唯一无游标的已关联 run，精确匹配由完整 tenant、原始 SERVICE issuer/subject 和 native instance 范围读出的 run ID、定义版本、创建时刻和 COMPLETED 状态。另一个租户的关联 ID 相同且 Run ID 不同，因此任意外部或额外项均失败。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidenceTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationEvidence.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12OwnerObservationAcceptanceIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-30-s12-owner-observation-e.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-31-s12-owner-observation-e.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s12-owner-observation-e.md` |

## 9. Interface Definitions

沿用现有权限资源 `tianji.api.process.instance.correlations`、`tianji.api.process.agent.outcomes` 和 `tianji.api.agent.list`。具体发布合同见 Depends On 中的 Java SDK Spec，测试只通过现有 SDK 发起这三个读取。

## 10. POJO and Data Model Design

复用 SDK 已发布 Process 和 AS 页类型。测试内部查询投影附带 AS run 的持久创建时刻。

## 11. Database Design

现有隔离库观察者执行既有完整 owner 查询和 `pg_catalog.clock_timestamp()`。本步骤只增加测试观察读取。

## 12. Frontend Page Design

由独立操作者测试选择器触发。

## 13. Design Patterns and Architecture Principles

沿用 Test Fixture 固定测试资源和生命周期，由现有 HTTP Adapter 访问实际保护接口。证据关联是直接的类型化比较。

## 14. Test Design

| ID | Test | Expected |
| --- | --- | --- |
| TEST-001 | S12OwnerObservationEvidenceTest | Four finite cases, exact foreign AS Run ID mutation fails. |
| TEST-002 | ExpenseAcceptanceHarnessTest, S12MetricsCollectorEvidenceTest and S12MetricsTelemetryTest | Nineteen adjacent finite cases. |
| TEST-003 | Clean package and test-only class check | E source compiles and fixture stays out of shipped executable archives. |
| TEST-004 | S12OwnerObservationAcceptanceIT#observes_each_tenants_owned_process_and_bridge_agent_run | Actual protected test, fixed summary and original private Collector digest. |

## 15. Non-functional and Cross-cutting Design

新增通过标记只记录租户、读取和拒绝的固定计数。现有报告仍记录原始 Collector 摘要和制品摘要。原始凭据、配置和业务标识保留在私有环境。

## 16. Compatibility, Migration, Rollout, and Rollback

原有指标、日志隐私、接收 Link、S07 和业务套件继续使用各自选择器。独立测试改动可通过撤回该 focused commit 回滚。

## 17. Alternatives and Decisions

复用既有正常路径及同一 correlation 的两个租户，令原 owner 的单页证据可以相互区分。AS owner proof 通过原桥接 SERVICE 的精确 LIST 和独立持久 run 身份完成。

## 18. Risks and Open Questions

实际 IAM、HTTPS、隔离 PostgreSQL 和原始 Collector 由操作者 E1 验收。两个专用 READ token 若缺失、范围错误或过期，验收失败。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/UC-002 | §5/7/8 | TEST-001/002/004 |
| REQ-002 | UC-001/UC-002 | §7/9/10 | TEST-001/004 |
| REQ-003 | UC-001/UC-002 | §7/9/11 | TEST-001/004 |
| REQ-004 | UC-001/UC-002 | §8/14/16 | TEST-001/002/003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §5/7/14 | 既有 PS 验收驱动、AS/PS 独立子进程和原有授权边界。 | None |
| MC-REUSE-001 | Applicable | PASS | §5/7/14 | 复用 Java SDK、正向流程、原 owner 查询和固定 Collector。 | None |
| MC-DEP-001 | Applicable | PASS | §5/7/14 | 沿用 JDK 25、Jackson、JUnit 与已有 JDBC。 | None |
| MC-NAME-001 | Applicable | PASS | §5/7/14 | 独立 S12 suite、操作者选择器和测试证据验证器。 | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | 完整 owner、精确私有 READ 和单页证据。 | None |
| MC-MODEL-001 | Applicable | PASS | §5/7/14 | 沿用已发布只读 DTO，仅增加测试观察记录。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §5/7/14 | 已有 SDK 解码发布的合同 DTO。 | None |
| MC-LOG-001 | Applicable | PASS | §5/7/14 | 只在全部证据通过后打印固定计数，凭据失败信息固定。 | None |
| MC-BEAN-001 | Not applicable | N/A | §5/7/14 | 沿用既有子进程和 Registry。 | None |
| MC-UTIL-001 | Applicable | PASS | §5/7/14 | 复用 Java 时间、编码与现有测试基础设施。 | None |
| MC-JSON-001 | Applicable | PASS | §5/7/14 | 重复字段、尾随对象和错误 scope 在转发前拒绝。 | None |
| MC-TIME-001 | Applicable | PASS | §5/7/14 | 按各 owner 主库时间确定同租户对比窗口。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/7/14 | 独立私有 opt-in，仅在该 PS 测试子进程打开 outcome。 | None |
| MC-PATTERN-001 | Applicable | PASS | §5/7/14 | 复用 Test Fixture 和 HTTP Adapter，证据用直接比较。 | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7/14 | 六个测试路径和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §5/7/14 | 精确反证、有限回归与单独操作者门槛。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/7/14 | 真实授权、隔离 PostgreSQL 和 Collector 仍由 E1 验收。 | None |

### 20.6 Final verdict

PASS — Ready for user review
