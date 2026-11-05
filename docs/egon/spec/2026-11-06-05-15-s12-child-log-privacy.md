# T4-02 S12 子进程日志字面值隐私规格

| Field | Value |
| --- | --- |
| Document | 2026-11-06-05-15-s12-child-log-privacy.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Isolated two-role log proof, bounded draining, fixed Fake prompt and selected actual private inputs |
| Created | 2026-11-06 05:15 Asia/Shanghai |
| Updated | 2026-11-06 05:15 Asia/Shanghai |
| Owner | Tianji acceptance fixtures |
| Repository | tianji-service |
| Scope | Eight test Java files and three review documents |
| Change Surface | Test-only launcher, fixture boundary markers and private log verifier |
| Affected Chapters | §7, §8, §10, §13, §14, §15, §16 |
| Source Requirement | Master §17.2 S12 and §19 T4-02/T4-05 |
| Baseline Revision | Service 57d695e5b16e69293c3c07d1082224da3db4dd23 |
| Amends | None |
| Supersedes | None |
| Depends On | [S12 metrics fixture §7.3](2026-11-06-04-38-s12-metrics-subset.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [S12 child log privacy Plan](../plan/2026-11-06-05-16-s12-child-log-privacy.md) |

## 1. Summary

在独立的 S12 指标操作者入口中，复用两个正常租户和现有 Fake 模型，观察实际子进程日志的完整排空、固定提示到达与所选真实夹具凭据的字面值隐私。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ExpenseAcceptanceHarness#processBuilderLauncher, #stopHandle | 现有子进程 stdout 和 stderr 丢弃，已有关闭及强制终止边界。 |
| EVD-002 | ExpenseAgentAcceptanceMain#requestedAmount and MODEL_INPUT | 现有 Fake 验证唯一 UserMessage、固定提示前缀与闭合任务 JSON。 |
| EVD-003 | BusinessProcessAgentAcceptanceIT.Driver#open, #token, #childConfig | 实际 bearer、AS/PS 运行库口令和 TLS keystore 口令从私有夹具取得。 |
| EVD-004 | S12MetricsInitializer and S12MetricsAcceptanceIT | 指标模式使用既有共享测试 initializer 和两条实际操作路径。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing acceptance tests, without a generated module or archetype.

## 3. Goals and Non-goals

证明两条现有 Fake 路径的 AS/PS 子进程日志经过完整检查，固定提示和所选私有凭据字面值均未出现在其中。动态签发的子进程出站凭据、编码或截断变体、父进程日志、Collector 日志与 trace、生产环境以及完整 S12 各自单独验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| Private fixture and log oracle | Affected | EVD-001/002/003/004 | Exact isolated capture selector and fixed markers | Detailed implementation and test | §7, §8, §10, §13, §14, §15, §16 |
| Production API and storage | Context-only | EVD-003/004 | Existing owned workflow and database witnesses | Existing contract | §9, §11 |
| Frontend | Unchanged | EVD-004 | Operator test only | Scope boundary | §12 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Exclusive private metrics log selector | Must | Dedicated operator method and private property under S12_METRICS, existing metrics and all other launches keep discard behavior. | EVD-001/004 |
| REQ-002 | Complete bounded child evidence | Must | Each exact role has merged stdout/stderr, ordered unique lifecycle markers and EOF. Overflow invalidates success while reads continue. Missing, timeout or read failure rejects. | EVD-001/004 |
| REQ-003 | Actual Fake prompt and selected literal credentials | Must | Two exact validated Fake prompt hash observations, neither captured role has the prompt prefix or any actual selected credential, all four credential categories present. | EVD-002/003 |
| REQ-004 | Source and operator evidence distinct | Must | Typed RED, 25 finite cases, production-package exclusion and separate actual E report. No raw child logs or credentials in reports. | S12/T4-05 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | Source reviewer | Verify scope and scanner tests | Offline JVM and package checks |
| ACTOR-002 | Isolated operator | Observe two actual child paths and selected privacy facts | Explicit private manifest and existing fixture roles |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify bounded scanner and artifact boundaries | ACTOR-001 | JDK and JUnit | Finite selectors | Locked baseline | Typed RED and finite GREEN, private fixtures excluded | No success after leaks, missing/partial logs or overflow | E pending | REQ-001/002/003/004 | Test fixture | TEST-001/002/003 |
| UC-002 | Observe normal AS/PS paths and private logs | ACTOR-002 | AS, PS and Collector | Exact operator E selector | Fresh authorized fixture | Normal workflow, existing metrics and complete private log verdict | Any missing marker or observed secret fails | Fixed summary only | REQ-001/002/003/004 | Existing HTTPS and test OTLP | TEST-004 |

## 5. Constraints, Assumptions, and Decisions

Suite 保持 S12_METRICS。新属性 tianji.s12.metrics.log-privacy.manifest 与原 metrics-only 属性独立。仅 Driver 可为该组合开捕获，子进程仅接受受控环境标识。操作者提供既有三库、TLS、两租户与独立 Collector，所选夹具凭据为 16 至 4096 个允许的高熵 ASCII 字符，每次最多 96 个不同值。

## 6. Project Technology Context

JDK 25、Spring 事件、既有锁定子进程、JUnit 和原 S12 指标夹具。现有 Agent 定义与内容 SHA 保持。

### 6.2 User-mandated Java rule compliance

| Literal rule | Affected? | Repository evidence | Exact design decision | Files/types/interfaces | Validation/test evidence | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Yes | EVD-001–004 | 现有测试包与语义技术类名 | §7/8 | TEST-001–004 | PASS |
| Rule 2 | Yes | EVD-001–004 | 闭合角色、固定字面标识、实际值来源和失败门禁 | §7/8 | TEST-001–004 | PASS |
| Rule 3 | No | EVD-001–004 | 仅私有技术状态和已有 immutable fixture DTO | §7/8 | TEST-001–004 | N/A |
| Rule 4 | Yes | EVD-001–004 | 现有测试 initializer 与固定脱敏诊断 | §7/8 | TEST-001–004 | PASS |
| Rule 5 | Yes | EVD-001–004 | 沿用 JDK、Spring、JUnit 与既有夹具 | §7/8 | TEST-001–004 | PASS |
| Rule 6 | Yes | EVD-001–004 | 现有 Fake 闭合输入、指标 JSON 字段与解析语义保留 | §7/8 | TEST-001–004 | PASS |
| Rule 7 | Yes | EVD-001–004 | 仅在独立选择器下注入测试子进程环境标识 | §7/8 | TEST-001–004 | PASS |
| Rule 9 | Yes | EVD-001–004 | 直接复用现有宿主生命周期并附加私有观察器 | §7/8 | TEST-001–004 | PASS |
| Rule 10 | Yes | EVD-001–004 | Duration 有界读线程等待和原指标时间窗 | §7/8 | TEST-001–004 | PASS |
| Rule 11 | Yes | EVD-001–004 | AS/PS 测试责任和既有 CQE 生产架构保持 | §7/8 | TEST-001–004 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Child log join | Duration | java.time.Duration and Thread.join(Duration) | Not persisted | Internal | No zone | No display | At most three seconds per reader after existing child stop | Relative elapsed time | Private mode only | TEST-001/004 |
| TIME-002 | Existing metrics collection | Duration | Duration and nanoTime | Not persisted | Internal | No zone | No display | Existing five-second read bound | Existing UTC and elapsed clocks | Reuse | TEST-003/004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Necessity |
| --- | --- | --- | --- |
| Dedicated operator entry | Separate exact selector with reused Driver | REQ-001 | Preserve the old metrics-only run and independent evidence |
| Merged process log reader | Fixed private byte buffer and continued pipe draining | REQ-002/004 | Bound memory and avoid holding children on overflow |
| Fixed lifecycle and Fake markers | Exact role and verified prompt observation | REQ-002/003 | Detect empty, wrong or incomplete capture |
| Direct credential observations | Existing actual bearer suppliers and child configuration values | REQ-003 | Observe what the selected private fixture passes rather than invent values |

| Path | Network calls | Client states | Reason |
| --- | --- | --- | --- |
| Business fixture | Existing owner-authorized HTTPS | Existing | Two normal paths |
| Privacy evidence | None added | Two child readers and a parent verifier | Check literal stdout and stderr privately |

### 7.1 System Architecture Design

由既有拥有者启动两个锁定子进程并执行原指标流程。隐私模式合并 stdout 和 stderr，每个角色由一个私有后台读线程排空。仍由 Driver 负责停止进程，父测试持有观察器并清除私有内存。

### 7.2 High-Level Design

AS 与 PS 的现有测试 initializer 仅在独立隐私模式输出固定 READY 与 CLOSING。前者走 stdout，后者走 stderr。子进程关闭后检查各自完整流。Agent Fake 完成原严格提示检查且金额为 100 后记录观察到的固定前缀 SHA-256，两条实际模型输入必须产生两个标识。

### 7.3 Detailed Design

每个角色固定 1 MiB 私有内存。超限后保留拒绝标志并继续排空到 EOF。关闭后，每个读线程在三秒内结束才允许检查固定标识和字面值。标识缺少、重复、顺序错误或角色错误，实际读失败，超限和超时均拒绝。两条 Agent 摘要必须在 READY 与 CLOSING 之间，PS 无 Agent 摘要。精确提示字节使用现有 MODEL_INPUT 除末尾 LF 的完整固定前缀，摘要则绑定含 LF 的实际接收前缀。

检查实际 HTTP bearer、AS 和 PS runtime JDBC 密码及 TLS keystore 密码，四类齐备。选值均在既有使用点登记，不扫描任意配置或猜测动态凭据。父进程逐字节匹配且不构造原始日志字符串，结束清零各缓冲与凭据数组。使用已有指标 raw JSONL 的独立校验，子进程日志留在内存，报告仅固定标识和原指标摘要。

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyTest.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacy.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12MetricsInitializer.java` |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12MetricsAcceptanceIT.java` |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ChildLogPrivacyAcceptanceIT.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-15-s12-child-log-privacy.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-16-s12-child-log-privacy.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s12-child-log-privacy.md` |

## 9. Interface Definitions

私有 JVM 属性 tianji.s12.metrics.log-privacy.manifest 仅关联测试入口。复用现有 S12 manifest、HTTPS、Collector 和数据库边界。

## 10. POJO and Data Model Design

沿用私有 manifest。新状态仅包括两个角色捕获缓冲、EOF 和 overflow 状态以及所选凭据类别，没有持久模型或证据 JSON 扩展。

## 11. Database Design

沿用现有隔离库与已提交事实。所有 28 个原 SQL 均保持逐字节一致。

## 12. Frontend Page Design

独立操作者测试，不涉及 UI。

## 13. Design Patterns and Architecture Principles

在现有宿主生命周期附加私有观察器，复用原 Driver 和指标校验。通过同一条已知子进程流将捕获、排空与验证绑定。

## 14. Test Design

| ID | Test | Expected |
| --- | --- | --- |
| TEST-001 | S12ChildLogPrivacyTest | Six finite cases for EOF, liveness, overflow, timeout, read error and exact content rejection. |
| TEST-002 | Exact typed R1 | Removing only the credential literal predicate fails the one intended missing exception assertion. |
| TEST-003 | 19 existing finite cases and clean package | Metrics registry, raw Collector evidence, and harness regressions. Both archives exclude fixture classes and E XML absent. |
| TEST-004 | S12ChildLogPrivacyAcceptanceIT#collects_the_same_metrics_with_private_child_log_evidence | Authorized actual two-role, two-tenant operator E and fixed verdict with existing metrics evidence. |

## 15. Non-functional and Cross-cutting Design

两路输出保留在父测试有界内存。原始捕获、已知凭据和提示不进入任何报告。任何非完整观察均拒绝。只有私有操作者入口的固定成功标识与原指标摘要可见。

## 16. Compatibility, Migration, Rollout, and Rollback

现有 metrics-only 选择器、原 manifest 属性与其他套件保持原语义。回滚为撤回一个聚焦测试提交。

## 17. Alternatives and Decisions

私有内存缓冲可在子进程关闭后匹配全部实际消费值。实时丢弃或预先排除秘密会缺少可核对原始字节，磁盘捕获则扩大了原始日志留存面。

## 18. Risks and Open Questions

源代码和有限扫描不构成真实 AS/PS、Collector、IAM、PostgreSQL 或生产隐私验收。字面值观察不覆盖动态交换凭据或其他编码。

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/UC-002 | §5/7/8 | TEST-003/004 |
| REQ-002 | UC-001/UC-002 | §6.3/7/10 | TEST-001/003/004 |
| REQ-003 | UC-001/UC-002 | §5/7/15 | TEST-001/002/004 |
| REQ-004 | UC-001/UC-002 | §8/14/16 | TEST-001/002/003/004 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 or Plan §8 | 沿用 AS/PS 测试类树、生产制品和独立 S12 指标 Driver | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 or Plan §8 | 沿用 ProcessBuilder、现有关闭顺序、指标流程及私有凭据读取 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 or Plan §8 | 只使用现有 JDK 25、JUnit、Spring 与已有 Collector | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 or Plan §8 | 测试类和环境标识按 S12 私有模式命名 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 or Plan §8 | 要求完整排空、闭合角色、精确标识和四类实际凭据 | None |
| MC-MODEL-001 | Not applicable | N/A | §7/8/14 or Plan §8 | 没有新增业务实体或公开 DTO | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 or Plan §8 | 没有业务映射或 DTO 转换 | None |
| MC-LOG-001 | Applicable | PASS | §7/8/14 or Plan §8 | 子进程只输出固定标识或既有指标摘要，扫描器只给固定失败诊断 | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 or Plan §8 | 复用现有测试 initializer，只增加同源生命周期监听 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 or Plan §8 | 复用 JDK SHA-256、数组、受限线程和持续时间 | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 or Plan §8 | Fake 保持现有闭合输入解析，指标原始 JSON 校验保持独立 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 or Plan §8 | 每个读线程独立三秒相对等待，原 Collector 和关闭预算保持 | None |
| MC-CONFIG-001 | Applicable | PASS | §7/8/14 or Plan §8 | 独立测试属性和精确子进程环境标识，生产配置排除在范围外 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 or Plan §8 | 将私有观察器接到已有宿主生命周期，避免第二套 Driver | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 or Plan §8 | 八个测试 Java 路径和三个文档，28 个现有迁移逐字节比较 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 or Plan §8 | 精确凭据 RED、25 项有限测试、制品和独立操作者门槛 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 or Plan §8 | 源码门禁与实际双租户、数据库、Collector 运行分开 | None |

### 20.6 Final verdict

PASS — Ready for user review
