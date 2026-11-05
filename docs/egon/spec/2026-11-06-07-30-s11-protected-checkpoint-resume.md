# T4-03 S11 首条输入 checkpoint 的受保护 RESUME 验收规格

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One external test-only HTTP and PostgreSQL witness for existing protected flow |
| Created | 2026-11-06 07:30 Asia/Shanghai |
| Updated | 2026-11-06 07:30 Asia/Shanghai |
| Owner | Tianji Agent Server |
| Repository | tianji-service |
| Scope | Two AS tests and three review documents |
| Change Surface | AS test source and operator evidence |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11 与 §19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 79779815f88b2b128363e27922ceac093157826b |
| Amends | None |
| Supersedes | None |
| Depends On | [Protected checkpoint RESUME](./2026-11-06-06-48-protected-checkpoint-resume.md#73-detailed-design) |
| Related Specs | [Process-exit checkpoint](2026-11-06-02-11-agent-checkpoint-product-fork.md), [Resume dispatcher PostgreSQL](2026-11-06-04-46-agent-checkpoint-resume-dispatch-postgres.md) |
| Related Plans | [S11 protected RESUME acceptance](../plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md) |

## 1. Summary

为真实独立签发的 USER、现有 AS 受保护入口和同一主库的独立只读证人准备可选择的验收。分别证明拒绝、原 ACK、单次持久接续与历史重放。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | AgentCheckpointResumeController#resume and agent-checkpoint-resume-v1.json | 固定受保护 POST 要求 USER、READ 与专用 RESUME，返回原 RUNNING ACK。 |
| EVD-002 | TianjiAgentAccess, TianjiCheckpointResumeGrantHandoff#submit and TianjiCheckpointResumeGrantAuthorizer | 真实 owner 与独立当前授权由生产部署处理。 |
| EVD-003 | AgentCheckpointCapturePostgresTest#resumes_the_claimed_checkpoint_to_completion_and_replays_only_the_acknowledgement | 两种 codec 的强制进程退出、持久接续与重放已有 operator fixture，使用受控授权边界。 |
| EVD-004 | AgentCheckpointTakeoverService and V16__add_agent_checkpoint.sql | 接管和原 ACK 在当前主库提交，V16 原生输入前缀与原 run 租约绑定。 |
| EVD-005 | AgentCheckpointResumeControllerTest, TianjiCheckpointResumeGrantHandoffTest and AgentRunController#get | 已有有限保护和交接用例，真实 READ 可核实准确 owner run。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 现有 AS 模块中的测试和文档。

## 3. Goals and Non-goals

增加 S11 首条输入提交、模型和工具前窗口的真实 HTTP/PG 验收入口。更晚的工具、checkpoint 失败、Session 投影与定义变化属于各自验收窗口。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AS tests | Affected | EVD-001–005 | 独立真实保护用例与有限输入证人 | Fixture and evidence | §7, §8, §14 |
| AS production | Context-only | EVD-001/002/005 | 复用原生产保护和独立签发链 | Existing contract | §9, §16 |
| AgentDB | Context-only | EVD-003/004 | 现有 V16 主库和已读授权证人 | Existing schema | §11 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 独立真实 HTTPS 和只读主库 | Must | 明确 opt-in、本机 TLS、一次性 PG 主库、无 DML 角色、owner 与 codec 精确绑定 | EVD-001–004 |
| REQ-002 | 当前 USER 保护、持久接续和重放 | Must | 真实 READ 成功，缺身份 401，仅 READ 403，拒绝前后证据一致，正确双授权获得原 ACK，单模型原生接续完成，精确回执及重放，变更输入 409 | EVD-001–005 |
| REQ-003 | 分开有限与 operator 证据 | Must | revision typed RED、绿色有限门、默认跳过 I1、精确文件范围、28 份原 SQL 字节一致 | EVD-003/005 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | 私有真实 IAM、隔离 AS 和现有 V16 主库 |
| ACTOR-002 | Source reviewer | 离线有限证据 |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | 验证精确 USER 续跑 | ACTOR-001 | 真实 owner、两个 bearer、V16 过期 cut 与明确 I1 | READ、两次拒绝、保护 RESUME、同库见证、原 ACK 重放 | 非准确角色、cut 或任何异常路径即失败 | 仅一个原 ACK 与一次模型结算 | REQ-001/002 |
| UC-002 | 在有限环境审查 | ACTOR-002 | 当前基线与现有 JDK 25 测试环境 | RED、G1、G2 和 S1 | 任一有限门异常停止提交 | I1 单独归档 | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

默认关闭 `tianji.postgres.checkpoint.protected-resume`。I1 由操作者准备真实普通及恢复角色、真实 Shoubing/Jianshen 独立 RESUME 策略、真实 owner 的原 MODEL 授权、两份私有 bearer、只读 observer 和一次性已存在的 V16 cut。I1 使用严格本机 HTTPS origin 和一次性库名，不负责启动、迁移或授权。

## 6. Project Technology Context

JDK 25、AS 现有 JUnit Jupiter、Spring Boot Jackson、PostgreSQL JDBC、现有 AS 保护与 AR V16 账本。

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
| Rule 1 | Yes | EVD-001–005 | 使用现有 AS 测试命名及独立选择器。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–005 | 生产端口复用现有命令校验，测试对输入与 ACK 执行边界校验。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 3 | Yes | EVD-001–005 | 复用不可变产品类型，测试局部证人为不可变 value object。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 4 | No | EVD-001–005 | 范围为测试与文档，无新业务 Spring Bean。 | §7/8/14 | TEST-001–004 | N/A |
| Rule 5 | Yes | EVD-001–005 | JDK 工具、Jackson、JDBC 和现有 JUnit。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–005 | Spring Boot Jackson 读取闭合 ACK，PG JSONB 见证存储。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–005 | 独立 JVM 测试开关，生产多环境配置与默认值一致。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–005 | 复用外部 operator fixture 与只读 Query 证人。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–005 | JDK Duration、单调计时与 PostgreSQL 原时钟。 | §7/8/14 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–005 | 使用现有真实主库既有 schema，独立核验不可变迁移。 | §7/8/14 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Existing run deadline and prior Session lease | Occurred instant | java.time.Instant and PostgreSQL timestamptz | UTC instant | UTC Z | Not applicable: absolute instants | Not applicable: no user display | Lease expiration and at least 50 seconds to deadline evaluated at the primary | Existing PostgreSQL clock | Retain current stored values and data types | Step 1 TEST-004 initial snapshot |
| TIME-002 | Connect, body and completion deadlines | Duration | java.time.Duration, JUnit Timeout and monotonic elapsed nanos | Not persisted | Internal | Not applicable: relative elapsed duration | Not applicable: no user display | One bounded HTTP exchange and bounded completion observation | JDK monotonic clock and JUnit test budget | Preserve production endpoint response timeout | Step 1 TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| 真实 HTTPS/PG 选定测试 | Add | REQ-001/002 | EVD-003 受控授权 fork | 保护 REST 和真实外部 IAM | 一份测试 | Use |
| 精确 origin 和 ACK 有限对照 | Add | REQ-003 | EVD-005 MockMvc 测试 | 防止 operator 证人宽松误报 | 一份测试 | Use |
| 保护链和原主库 | Reuse | REQ-001/002 | EVD-001/002/004 | 需要原样观察 | 操作者已有部署 | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | 隔离本机 HTTPS 和 PG | 真实当前 USER 与准确 owner | V16、账本、Session 前缀、模型预算及原回执 | 初始安全检查、保护响应与原持久证据缺一即失败 |
| R1/G1/G2/S1 | 无 operator HTTP/PG | 有限输入和编译 | 有限报告 | 精确 revision 或开关与范围偏移即失败 |

### 7.1 System Architecture Design

操作者的 HTTPS 请求由实际 AS 安全链处理。已部署的 Shoubing、Jianshen 与专用恢复连接承担现有职责。测试使用独立只读连接在同一主库观察持久结果。

### 7.2 High-Level Design

按完整 tenant、issuer 指纹、subject、run 与 codec 读取原 cut。保护 GET 验证独立 READ bearer，随后对固定 POST 执行缺身份和 READ-only 对照。使用真实双权限 bearer 发出同一精确命令。

### 7.3 Detailed Design

要求成功媒体类型、no-store、原 run 与精确 RUNNING/attempt/revision ACK。核对同一 attempt、原 checkpoint 与首条输入、同 codec 原 invocation 延续、唯一 USER 输入、唯一新租约纪元、两个接管事件、单模型结算和精确原回执。完成后重放得同一 ACK，变更 revision 得 409，证人前后一致。请求体和响应体有界，凭据不出现在测试结果。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeHttpPostgresTest.java` |
| CREATE | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentCheckpointResumeWitnessTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-30-s11-protected-checkpoint-resume.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-31-s11-protected-checkpoint-resume.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s11-protected-checkpoint-resume.md` |

## 9. Interface Definitions

复用现有 GET /api/tianji/v1/agent-runs/和 POST /api/tianji/v1/agent-checkpoints/resume。测试配置为显式 JVM 私有参数，详见验收证据。

## 10. POJO and Data Model Design

复用 `TrustedAgentActor`、`ResumeCommand` 和 `Acknowledgement`。私有 fixture 输入与 witness 使用不可变 record，只为精准对比保留数据摘要。JSON 使用既有 Jackson，禁止重复键、额外尾部 token、ACK 扩展字段和数字强制转换。

## 11. Database Design

Relational model change: No. 复用现有 V16 与原 Session、run、event、budget 和 receipt。只读 observer 启用实际 transaction_read_only，限定为同库主写实例的精确登录角色。比较单个 SELECT 快照内的范围一致证据。原 28 份迁移保持字节一致。

## 12. Frontend Page Design

无页面变化。

## 13. Design Patterns and Architecture Principles

复用 operator Test Fixture 与独立只读 Query，保持生产保护链为实际调用方。精确 decoder 对照不需要额外业务抽象。

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | 以独立 scratch 副本移除精确 revision 比较，有限用例须因允许错误 revision 触发类型化断言失败。 |
| TEST-002 | owning G1 运行新 witness 和既有 controller、handoff 有限用例，全部成功。 |
| TEST-003 | 真实 operator 类实际编译，默认条件跳过，并检查精确提交范围与不可变 SQL。 |
| TEST-004 | I1 在真实 IAM、AS 与一次性 PostgreSQL 上分别验证 codec 1 和 2，每次使用一个新 cut。 |

## 15. Non-functional and Cross-cutting Design

本机 TLS 使用 JVM 信任源，禁用 HTTP 重定向和默认代理。私有凭据文件固定访问权限，响应限长并有限超时。仅通过只读 SQL 捕获 witness 摘要，测试消息不输出敏感响应或凭据。

## 16. Compatibility, Migration, Rollout, and Rollback

默认测试套件只运行有限对照，独立 operator 测试显式启用。回滚为撤销本次两份测试与三份文档的单一提交。

## 17. Alternatives and Decisions

AR fork 保留作强制退出与原生接续的独立受控证据。operator 验收通过真实 HTTPS 与主库观察当前生产保护链，避免克隆 AR 私有子进程夹具。

## 18. Risks and Open Questions

真实已授权普通 START、故障触发、IAM 当前独立 purpose、部署角色和实际 AS cut 均由操作者准备。AR 既有受控授权及合成 owner 不能直接替代。该窗口之外的 S11 场景分别验收。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/11 | TEST-002–004 |
| REQ-002 | UC-001 | §7/9/10/14 | TEST-004 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001–003 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7 | 复用 AS 保护路由、签发交接与 AR 现有主库。 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–005 | 复用真实保护端点、原命令和 ACK 类型、现有 PostgreSQL JDBC。 | None |
| MC-DEP-001 | Applicable | PASS | §6 | 使用现有 JDK、Jackson、PG JDBC 和 JUnit。 | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 测试类与不可变 fixture 记录使用既有命名。 | None |
| MC-VALID-001 | Applicable | PASS | §5/14 | 保护本机 origin、一次性库、角色、凭据文件和 ACK 类型。 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 复用命令与 ACK，证人记录仅保留标量和摘要。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 没有新业务层对象或转换器。 | None |
| MC-LOG-001 | Not applicable | N/A | §15 | 没有生产日志和凭据输出。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 没有新组件或运行配置绑定。 | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | 复用 JDK HTTPS、URI、POSIX、JDBC 和现有 Jackson。 | None |
| MC-JSON-001 | Applicable | PASS | §10/14 | 严格 Jackson 对象、原整数类型、重复键和末尾 token 检查。 | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | 使用相对 Duration 和主库 clock，现有存储类型一致。 | None |
| MC-CONFIG-001 | Applicable | PASS | §5/16 | 一个测试专用开关，部署配置仍由操作者提供。 | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | 现有真实端到端 fixture 加独立只读证人。 | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | 只有两份测试与三份审查文档。 | None |
| MC-TEST-001 | Applicable | PASS | §14 | 有限 RED 和 G1，与实际执行 I1 分开。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | 精确记录操作者的真实 IAM、主库、外部进程和剩余 S11 窗口。 | None |

### 20.6 Final verdict

PASS — Ready for user review
