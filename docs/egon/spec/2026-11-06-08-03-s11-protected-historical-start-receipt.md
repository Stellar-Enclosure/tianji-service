# T4-03 S11 受保护历史 START 对账的跨主库验收规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One external operator observer and one finite cross-store witness |
| Created | 2026-11-06 08:03 Asia/Shanghai |
| Updated | 2026-11-06 08:03 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Two AS test sources and three review documents |
| Change Surface | AS operator test and review evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 5052339f5d7cb3aa1b2510a28d849b7b6894456b |
| Amends | None |
| Supersedes | None |
| Depends On | [Protected historical START](./2026-11-06-06-55-protected-historical-start-receipt.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Historical START operator acceptance](../plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md) |

## 1. Summary

在已配置的真实 AS、PS 和独立主库上，核对一次已提交但原 Tool 结果仍未知的 START。以原 SERVICE 独立读取、当前 USER 授权、两个主库只读证人与精确重放证明一次原业务动作和一次历史观察。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentHistoricalStartReceiptController#reconcile and TianjiServletHistoricalStartReceiptAuthority | 默认关闭的固定 POST 以独立当前 USER 权限保护历史对账并刷新决策。 |
| EVD-002 | TianjiFixedBpmnStartReceiptReader#getReceipt and ProcessStartReceiptController#receipt | 保留原执行 SERVICE，独立受保护 READ 同一命令与摘要。 |
| EVD-003 | ToolStartReceiptReconciliationSql and ToolExecutionJournalPostgresTest#historical_start_receipts_append_positive_evidence_for_unknown_and_dispatched_actions | Agent 实际 PG 有限受控来源测试，追加 V2/V5，保留 V13、Run 和预算。 |
| EVD-004 | HttpAgentClient#reconcileHistoricalStart, HttpProcessClient#getStartReceipt and existing AS checkpoint loopback guard | 现有 SDK 请求及响应有界，已有精确 HTTPS origin 校验可复用。 |
| EVD-005 | ExpenseAcceptanceFaultRelay.FaultPhase and BusinessProcessAgentAcceptanceIT | 现有进程验收及 Flowable 历史 SQL，已有 response-drop 窗口没有原 Process START。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Test-only changes in the existing AS module.

## 3. Goals and Non-goals

提供真实已准备正向历史 START 的独立 E 证人。真实缺失收据、专用故障触发和上线由各自的现有受控证据及后续操作者步骤覆盖。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS test | Affected | EVD-001–005 | Opt-in 外部测试和有限严格证人 | Fixture and evidence | §7, §8, §14 |
| AS and PS production | Context-only | EVD-001/002/004 | 复用现有保护路由、角色和产品行为 | Existing contract | §9, §16 |
| AgentDB and ProcessDB | Context-only | EVD-003/005 | 两个独立只读主库 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 精确隔离的真实 HTTPS 与双只读主库 | Must | 独立本机 AS、PS、原 USER 与原 SERVICE、两实际 PostgreSQL 主库和准确原 START cut | EVD-001–005 |
| REQ-002 | 原 START 与一次历史观察精确关联 | Must | 原 SERVICE 精确 READ，当前 READ-only/异 owner 拒绝，受保护正向对账和重放，原 native 实例与 Agent V13/Run/budget 保持 | EVD-001–004 |
| REQ-003 | 独立有限与 E 证据 | Must | 单一跨库时间守卫 typed RED，实际编译和默认跳过，精确五文件与 28 原迁移字节一致 | EVD-003/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 真实已配置独立服务、IAM、只读主库和业务故障切点 |
| ACTOR-002 | Source reviewer | 现有 JDK 25 和有限对照 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 精确对账一次真实丢失响应的 START | ACTOR-001 | 原工具 unresolved，PS APPLIED 收据和唯一 Flowable 历史行存在 | 读原 SERVICE 收据，验证 USER 拒绝和正确调用，比较双库和重放 | 任一不匹配或环境越界即失败 | 一条 Agent 观察和一个原 PS 实例 | REQ-001/002 |
| UC-002 | 离线检查证人的跨库关联 | ACTOR-002 | 原 SDK 和目标基线 | Typed RED、finite G1、compile G2 和 S1 | 无法识别错误证据就不进入提交 | 独立 I1 待运行 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

默认关闭。只接收具备真实 Process APPLIED 和准确原 Agent unresolved 状态的隔离切点。原生实例必须位于稳定等待点或已完成，验收期间流程推进和其他历史维护保持静止。要求四份独立身份 bearer、两个本机 TLS origin、两个独立一次性可写主库上的只读 observer。测试不准备或修改底层业务事实。

## 6. Project Technology Context

JDK 25、现有 AS JUnit、既有 Tianji SDK 和 PostgreSQL JDBC、现有 V2/V5/V13 与 Flowable 历史。

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
| Rule 1 | Yes | EVD-001–005 | 沿用既有 AS 测试命名与不可变局部证人。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 沿用 SDK 合同，精确绑定真实两主库输入与证人。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 复用已发布合同类型，局部 record 为不可变证人。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–005 | 无新生产 Bean 或日志。 | §7/8/14 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK、JDBC、JUnit 和既有 SDK。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–005 | 复用原 SDK Jackson 严格解码与数据库 JSONB。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–005 | 独立测试开关，实际配置仍由操作者控制。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 实际受保护 operator fixture 和双只读 Query。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | Instant 按各原数据库时间精确比对。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | 沿用两主库原 SQL 和业务事实，观察连接只读。 | §7/8/14 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Process recordedAt and Agent observedAt | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | UTC Z | Not applicable: absolute instants | Not applicable: no user display | Compare timestamps to their own primary without assuming clock ordering | Existing independent database clocks and precision | No reinterpretation of original receipt times | Step 1 TEST-001/004 |
| TIME-002 | Test and JDBC/HTTPS bounds | Duration | java.time.Duration and JUnit Timeout | Not persisted | Internal | Not applicable: elapsed budget | Not applicable: no user display | Bounded transport and statement per fixture request | JDK and JUnit deadlines | Original production timeouts stay unchanged | Step 1 TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 两主库外部真实见证 | Add | REQ-001/002 | EVD-003 受控 AR fixture | 缺真实保护和 Process 原证据联合 | 一份测试 | Use |
| 跨库时间严格有限对照 | Add | REQ-003 | 既有独立 SDK 解码 | 需验证两个来源独立关联 | 一份测试 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 两个隔离 HTTPS origin 和两只读数据库 | 原 SERVICE 与当前独立 USER | PS APPLIED、单实例、AS 原 V13 与历史观察 | 缺任一原切点、严格权限或精确证人即失败 |
| R1/G1/G2/S1 | 无 operator 连接 | 有限编译和输入对照 | JUnit 和源证据 | 无法区分两个时钟或范围扩大即失败 |

### 7.1 System Architecture Design

AS 负责实时历史 USER 授权及使用独立注册重新取得原 SERVICE READ。PS 负责精确原收据保护。测试另外以同一原 SERVICE 身份独立 GET，并从两个只读主库核对实际状态。

### 7.2 High-Level Design

按原完整 owner、executor、Run 和 intent 固定 Agent 源事实，从 PS receipt 与 Flowable business key 找到精确原实例。READ-only USER 与持有专用权限的异 owner 请求必须分别拒绝。当前真实 owner 通过现有 SDK 调用受保护 POST。

### 7.3 Detailed Design

核对原 SERVICE GET 和 Process 主库 APPLIED 完全一致，观察到唯一的 Agent 正向 V2/V5 追加。recordedAt 对应原 PS 主库，observedAt 对应 AS 插入时间。对照前后 V13 action/invocation、原始与派发 Run、预算和 Process 原 receipt/history 摘要。重复请求得到同一观察和相同持久事实。独立操作者审计证实 AS 出站原 SERVICE READ。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptHttpPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentHistoricalStartReceiptWitnessTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-08-03-s11-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/plan/2026-11-06-08-04-s11-protected-historical-start-receipt.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-protected-historical-start-receipt.md` |

## 9. Interface Definitions

复用 AS 既有 Agent GET 与历史 START POST，复用 PS 精确 START receipt GET，均使用现有 SDK。

## 10. POJO and Data Model Design

复用 HistoricalStartReceiptCommand、HistoricalStartReceipt、GetProcessStartReceiptQuery、ProcessStartReceiptDTO 和 TrustedAgentActor。局部只读证人使用不可变 record，比较各主库返回的私有摘要。

## 11. Database Design

Relational model change: No. Agent 原 V13、Run 与预算保持，只允许生产对账按现有逻辑追加 V2/V5 观察。Process 原 APPLIED 收据和 Flowable 历史应相同。观察者实际 transaction_read_only，仅 SELECT。现有 28 迁移保持字节一致。

## 12. Frontend Page Design

无页面变化。

## 13. Design Patterns and Architecture Principles

复用真实 operator Test Fixture 和两个只读 Query。现有 SDK 负责 wire 类型和限长，fixture 只处理跨源行为证据。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Scratch 中移除原 Process recordedAt 比较，要求独立有限见证触发 typed RED。 |
| TEST-002 | G1 owning 运行有限 witness 和现有保护控制器及 reader 测试。 |
| TEST-003 | 编译 opt-in 类、明确默认跳过及准确五路径和 SQL 字节。 |
| TEST-004 | I1 在已授权真实环境验证一个原 UNKNOWN 或 DISPATCHING 切点，若需要覆盖二者，各运行新的切点。 |

## 15. Non-functional and Cross-cutting Design

保留 JDK TLS 信任与 bounded SDK transport，禁用重定向和代理。只有受限私有文件装载 bearer 与密码。测试结果只给出状态与匿名错误。

## 16. Compatibility, Migration, Rollout, and Rollback

只新增测试和审查文档。独立默认关闭的 operator 选择器不进入有限执行。回滚为撤销单次提交。

## 17. Alternatives and Decisions

保留受控来源的真实 AR PostgreSQL 验证，同时对真实原 SERVICE 和实际 AS/PS 采用独立外部证人。复用已有 SDK，避免新增 transport 或部署代码。

## 18. Risks and Open Questions

需要真实授权的 START 成功响应阻断和原 cut。现有 relay 不覆盖该路径，不能以合成 Tool 状态代替。实际外部 IAM 双刷新与 AS 出站原 SERVICE READ 由操作者留存独立脱敏审计。真实缺失收据 E 仍是单独窗口。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/11 | TEST-003/004 |
| REQ-002 | UC-001 | §7/9/10/14 | TEST-001/004 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001–003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 真实 AS 与 PS 分别保持其已授权职责，两个独立只读主库。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用既有 SDK、合同、精确 loopback 校验和生产路由。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 使用现有 JDK、SDK、JDBC 和 JUnit。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 两份新类沿用现有 AS API 测试命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 精确 owner、原 SERVICE、私有文件、只读角色与时间戳关联。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用合同 DTO，局部证人为不可变 record。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 无新业务层转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 无新 logger 或凭据输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 无 Spring Bean 改动。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用 JDK、两 SDK 和先前 operator guard。 | None |
| MC-JSON-001 | Applicable | PASS | §10/14 | 由原 SDK 严格解码，SQL JSONB 验证原账本。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 按各来源独立精确比对 recordedAt 与 observedAt。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 独立测试 flag，部署配置由操作者准备。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 复用外部 operator fixture 与两个只读 Query。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 两份测试和三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 独立 typed RED、有限回归、实际 operator E 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 真实 START 响应丢失切点和实际 IAM 审计需外部。 | None |

### 20.6 Final verdict

PASS — Ready for user review
