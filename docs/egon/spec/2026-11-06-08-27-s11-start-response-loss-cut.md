# T4-03 S11 原 START 已提交后成功响应丢失切点规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Isolated S07 operator mode and test-only transport decorator with verified committed response |
| Created | 2026-11-06 08:27 Asia/Shanghai |
| Updated | 2026-11-06 08:27 Asia/Shanghai |
| Owner | Tianji Agent and Process test fixtures |
| Repository | tianji-service |
| Scope | Six test sources and three review documents |
| Change Surface | AS/PS test-only launcher, decorator, driver and evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | ac8384069257a457089bcf3d13b688b79ea9091b |
| Amends | None |
| Supersedes | None |
| Depends On | [Protected historical START observer](./2026-11-06-08-03-s11-protected-historical-start-receipt.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [S11 START cut producer](../plan/2026-11-06-08-28-s11-start-response-loss-cut.md) |

## 1. Summary

在独立操作者套件中沿用 S07 真实 Tool、IAM 和两个主库。对同一原始 START，只在实际 HTTPS 成功并由主库证明确已提交后丢弃调用方响应，生成可复用的真实 UNKNOWN 切点。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | BusinessProcessAgentAcceptanceIT.Driver#bootGovernedStart and #startGovernedRun | S07 使用真实 IAM、Process 发布和 AS 原 Tool，现有成功用例无法生成 UNKNOWN。 |
| EVD-002 | TianjiGovernedProcessToolConfiguration#tianjiGovernedProcessHttpClient and TianjiFixedBpmnStartToolTransport | 固定同名 JDK Client，正式路径转发原 bearer，已派发结果未返回映射 UNKNOWN。 |
| EVD-003 | ProcessController#start, ProcessExecutionService#start and ToolInvocationSql#completeIdempotentWrite | PS 在真实写事务提交原 APPLIED 与 Flowable，AR 使用原 Tool journal 记录 UNKNOWN。 |
| EVD-004 | ExpenseAcceptanceFaultRelay and TianjiFixedBpmnStartToolBindings.BackendVO#bindingSha256 | 原 relay 没有 Process START，替换 URI 会改变 backend 摘要。 |
| EVD-005 | AgentHistoricalStartReceiptHttpPostgresTest | 已有真实 protected observer 需要真实原 SERVICE 和未观察 UNKNOWN 切点。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Extend existing test fixtures only.

## 3. Goals and Non-goals

提供独立套件生成实际已提交但调用方丢失成功响应的 START 切点。故障发生在 AS JDK Client future 交付处。完整历史对账、真正的套接字断连和部署属于独立行为。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS/PS tests | Affected | EVD-001/004/005 | 一个新 opt-in 模式和现有 transport test-only 装饰器 | Fixture and evidence | §7, §8, §14 |
| AS/PS production | Context-only | EVD-002/003 | 原路径、URI、注册和授权都保持 | Existing contract | §9, §16 |
| Both primaries | Context-only | EVD-003 | 证人实际只读，唯一写入由原真实 START 负责 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 独立验收与精确原命令 | Must | 专用 manifest、原 S07 启动和 SERVICE，只有第一个绑定 tenant 的精确 Tool POST 进入一次性故障 | EVD-001–004 |
| REQ-002 | 仅真实 commit 后丢弃响应 | Must | 真实 200 与 PS 主库 APPLIED/原生实例匹配才在期限内令原调用 future 失败。私有 marker 与最终 V13 UNKNOWN、终态和原 PS 实例对上 | EVD-002/003/005 |
| REQ-003 | 分开有限与真实 E | Must | typed duplicate RED、实际编译有限成功、九个精确路径和原 29 SQL 字节相同 | EVD-001/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 专用 S11 manifest、原真实 S07 权限与隔离两主库 |
| ACTOR-002 | Source reviewer | JDK 25 与有限 fixture 对照 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 生成真实 UNKNOWN 切点 | ACTOR-001 | 两租户隔离 manifest、首次 action、空目标 START | 调用原 S07 工具并验证实际 commit 后丢弃响应 | 未提交、错配、超时、重复均不能产生 marker | 原 Run 和 intent 可交付后续 I1 | REQ-001/002 |
| UC-002 | 审查一次性和故障阶段 | ACTOR-002 | 当前基线和 Java 测试 classpath | RED、G1/G2/S1 | 任一类型或作用域错误阻断 | 真实 E 独立 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

独立 manifest 仍需要两个真实租户，使用其第一个。要求两主库与独立只读 observer 同历史对账验收的精确命名。沿用 S07 直接 HTTPS 地址和注册。只在私有 0700 目录创建一个不覆盖的 0600 marker。

## 6. Project Technology Context

JDK 25、Spring BeanPostProcessor、JDK HTTP、现有 Jackson、Spring JDBC、JUnit、Mockito 和 PostgreSQL。

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
| Rule 1 | Yes | EVD-001–005 | 在已存在 fixture package 使用具名测试和选择器。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 复用命令约束，增加精确原命令、上下文和证人验证。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 复用不可变 DTO 和有限字段 marker。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 4 | Yes | EVD-001–005 | 专用 fixture Bean 由显式 initializer 以准确名称登记。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 5 | Yes | EVD-001–005 | JDK、JDBC、Jackson、Mockito 和现有验收 fixture。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–005 | 复用严格 Jackson 和原合同。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–005 | 只有新测试 suite 能开启装饰，部署配置保持。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 使用 Decorator 保持原 transport 和凭据。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | 只用 java.time 和单调时钟、原数据库时钟。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | 测试观察现有 schema，原 29 SQL 不变。 | §7/8/14 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | dispatch_not_after | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Original dispatch is current before forwarding and expired before cut acceptance | PostgreSQL clock_timestamp | Preserve original V13 timestamps | Step 1 TEST-004 |
| TIME-002 | Prepared START request, decode and JDBC budgets | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed time | Not applicable: no display | Witness must finish before original client request budget and no timed-out marker | JDK monotonic clock, JDBC query timeout | Preserve production SDK bounds | Step 1 TEST-001/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| AS exact Client decorator | Add | REQ-001/002 | Generic fault relay | Relay does not support route and endpoint changes binding | One test-only class | Use |
| Existing S07 launcher and driver | Extend | REQ-001/002 | Clone bootstrap | Scope requires same original real path | Opt-in branch only | Use |
| Finite witness | Add | REQ-003 | Existing S07 success case | No isolated causal response gate proof | Two focused finite methods | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 原 HTTPS、真实 IAM、隔离主库 | 原 SERVICE START 与原 USER | 真实 V13、PS APPLIED、Flowable、私有 marker | 只有证据齐全后丢弃成功 |
| R1/G1/G2/S1 | 无 operator 连接 | 有限 mock 和编译 | JUnit/bytecode | 任一误吞或重复执行失败 |

### 7.1 System Architecture Design

新增 S11 suite 选择现有 S07 launcher。AS child 只在该 suite 装配 test-only BeanPostProcessor，包装原同名 HTTP Client。PS 与 IAM 继续处理原真实请求。

### 7.2 High-Level Design

解析有界且闭合的准确 START 命令。绑定原 Agent V13 DISPATCHING 与原 SERVICE 并证明 PS 无原行。真实委派正常请求。只认精确 200 no-store DTO，独立查询原 APPLIED 和唯一匹配原生实例。

### 7.3 Detailed Design

在原请求期限内让选中的客户端 future 一次性返回 I/O 失败，成功设置异常后原子写四字段 marker。父进程随后核对原 Run 终态、过期 dispatch、原 V13 UNKNOWN、预算 UNKNOWN、原 PS APPLIED 和稳定 Review 等待。重复选择不会重发。其他请求沿用原 HTTP Client。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLoss.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/S11HistoricalStartLossTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S11HistoricalStartCutIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-08-27-s11-start-response-loss-cut.md` |
| CREATE | `docs/egon/plan/2026-11-06-08-28-s11-start-response-loss-cut.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-start-response-loss-cut.md` |

## 9. Interface Definitions

复用现有 S07 start、原 PS release/read 和原 START；无生产接口变化。独立操作者 selector 使用显式私有 manifest。

## 10. POJO and Data Model Design

复用 StartProcessCommand、ProcessInstanceDTO、TrustedAgentActor。marker 四行精确字段为 dispatch Run、intent、原 instance、request SHA-256。用 POSIX 权限和独占链接发布。

## 11. Database Design

Relational model change: No. 既有真实原 V13、Agent Run 和预算，既有 PS 原 APPLIED 收据与 Flowable 原生历史。只读连接不改事实。原 28 个迁移保持。

## 12. Frontend Page Design

无页面变化。

## 13. Design Patterns and Architecture Principles

Test-only Decorator 解决准确故障点选择与原 HTTPS 依赖稳定。外部代理需要更多路由并改变 backend URI 的摘要，直接替换产出会绕过原事实验证。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 移除一次性 CAS 的 scratch 副本触发重复抑制 typed RED。 |
| TEST-002 | G1 运行 decorator 两项与既有 S07、harness 有限回归。 |
| TEST-003 | JDK 25 编译 AS/PS 原与新增类，operator 入口保留独立 manifest。 |
| TEST-004 | 真实授权 I1 生成 exact committed START 和 durable UNKNOWN。 |

## 15. Non-functional and Cross-cutting Design

现有原 TLS 配置、边界和定向权限保持。故障输入与成功响应有界，精确一次选择，敏感值不写 marker 或日志。

## 16. Compatibility, Migration, Rollout, and Rollback

现有 suite 仍走原路径。回滚为撤销一次测试提交。

## 17. Alternatives and Decisions

采用原 Client 回调边界，在真实响应到达 SDK 前完成一次性 I/O 失败。独立 TLS 代理可证明物理连接关闭，但额外路由和 backend 身份变化超出本范围。

## 18. Risks and Open Questions

真实 IAM、独立主库和子进程均待操作者。此切片产生 cut，后续实际 protected POST 依赖独立宿主与原 SERVICE READ 配置。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/9 | TEST-003/004 |
| REQ-002 | UC-001 | §7/10/11 | TEST-001/004 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001–003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 沿用真实 AS Tool、PS START 与独立主库证人。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用 S07、现有同名 HttpClient Bean、JDK 和 JDBC。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 现有 JDK、JUnit、Mockito、Jackson 和 PostgreSQL JDBC。 | None |
| MC-NAME-001 | Applicable | PASS | §8 | 独立套件和测试类沿用当前命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 严格原命令、完整 owner、原 SERVICE、主库与单次状态。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 使用现有合同 DTO，私有 marker 保留四个字段。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 没有新增业务层模型或转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 不增加生产 logger 或输出凭据。 | None |
| MC-BEAN-001 | Applicable | PASS | §7 | 专用 child flag 注册具名 test-only 装饰器，绑定准确生产 Bean 名。 | None |
| MC-UTIL-001 | Applicable | PASS | §6 | 复用 JDK、Spring、Jackson 和已有 Driver。 | None |
| MC-JSON-001 | Applicable | PASS | §10 | 精确限长和严格 Jackson 入参及成功 DTO。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 使用原请求界限和单调时钟，派发到期由 PostgreSQL 判定。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 新独立操作者 manifest，原生产与 S07 启动条件保持。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 测试装饰器仅拦截精确成功交付，避免更换 URL 和鉴权。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 仅六测试源和三份文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | typed RED、有限测试和实际独立 E 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 明确真实 IAM、HTTPS、子进程和 PostgreSQL 由操作者运行。 | None |

### 20.6 Final verdict

PASS — Ready for user review
