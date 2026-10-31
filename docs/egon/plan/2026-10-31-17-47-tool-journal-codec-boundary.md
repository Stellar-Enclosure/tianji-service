# T3-07 工具持久记录编解码边界执行细化

| Field | Value |
| --- | --- |
| Document | `2026-10-31-17-47-tool-journal-codec-boundary.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-10-31 17:47 Asia/Shanghai` |
| Updated | `2026-10-31 17:47 Asia/Shanghai` |
| Owner | 用户授权，主代理执行及审计 |
| Repository | `tianji-service` |
| Scope | T3-07 的私有工具持久记录 JSON 边界；不含 journal 存储或调用派发 |
| Source Requirement | 用户要求继续原 plan 未完成工作、分步验证提交，并明确允许完善和提交三个既有未跟踪工具文件 |
| Baseline Revision | `main@b9ee90072fd043b50dce045d1884ebaf80002684`；三个授权接管的未跟踪工具文件 |
| Implements Spec | [平台架构与工程实施蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Spec Status | Legacy blueprint；用户本轮明确授权执行其剩余工作，源文件无规范状态字段 |
| Spec Revision | 2026-10-31 本次读取；SHA-256 `577ce2722cd94d4ae905d44ad6bef6817f903bbfc6b640576ba8ba216cd7b27b` |
| Effective Specs | [平台架构与工程实施蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

把原蓝图 §19 T3-07 中固定工具版本、参数和业务结果的安全持久表示细化为一个独立可验证步骤。已有 `ToolDescriptor`、`ToolSchema`、`ToolExecutionOutcome` 及当前权限合同已在基线提交；用户同时授权继续完善工作区的 `ToolExecutionJsonCodec` 和其测试。此切片先统一编码侧失效行为，再固化闭合 JSON、类型、hash、私密内容脱敏的测试；不创建表、不取得执行权限、不打开任何工具入口。

原蓝图提供设计与验收依据，但不是新版执行 skill 的规范文档格式。本文是对已授权局部工作的文件级执行细化，不伪造原蓝图已处于 Accepted/Implemented 状态。本文保持 Review，由本轮用户对原蓝图剩余工作的明确执行授权支持实施。

## 2. Target Spec and Effective Design

### 2.1 Primary target

主设计是上方链接的原蓝图。有效范围为 §6 Maven 依赖方向、§7.2 副作用事实与 UNKNOWN、§13 Tool 治理和 §19 T3-07。现有代码的实际进度以 `b9ee900` 及授权未跟踪快照为准，原文的历史进度句子不回退当前能力。原蓝图的其他阶段仍保持其原要求。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [平台蓝图](../../../../docs/plans/agent-framework-upgrade-plan.md) | Legacy；本轮明确授权；摘要见头部 | §6、§7.2、§13、§19 T3-07 | 固定版本/参数、意图与副作用结果的唯一业务设计来源 |

### 2.3 Superseded or excluded content

没有 supersede。工具 journal SQL、独立当前授权实现、审批收据消费、Process/DMN 业务调用、ADK callback 装配和完整 T3-07 验收均不属于这个编解码步骤；后续应另行锁定精确存储/派发文件和验证，不能把本步骤当作它们已完成。

## 3. Effective Requirements and Acceptance

原蓝图没有 `REQ-NNN` 编号。下列 `PLAN-REQ-*` 是对现有要求的追踪别名，不新增业务要求。

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13、§19 T3-07 固定 descriptor/version/hash 与审批输入 hash | 工具描述和原始参数须按固定 schema/hash 保真、拒绝篡改 | 描述 round-trip 保持 reference；未知字段、重复键、尾随 JSON、数字/字符串强制转换和未知参数均拒绝 | 私有 codec 和单元测试 |
| PLAN-REQ-002 | §7.2、§19 T3-07 intent/receipt 与 UNKNOWN | 成功、业务拒绝、已知失败、未知副作用必须保持原语义，不伪造 receipt | 写成功必须携带真实格式 receipt；UNKNOWN 保留未知 cost；无效结果拒绝 | 复用 ToolExecutionOutcome.validated |
| PLAN-REQ-003 | §13、§19 T3-07 安全治理、S07/S10 | 私密参数与工具输出不得通过错误消息泄漏，非法持久内容须封闭失败 | 读写失败均为同一固定消息 IllegalStateException，无原异常 cause 或正文 | 编码侧和解码侧统一边界 |
| PLAN-REQ-004 | §6 依赖边界、§19 T3-07 禁改范围及回滚 | 保持单一 ADK 工具循环、无新层/依赖，写工具逐项接入 | 无 POM/schema/API/开关/调用路径变更；文档准确标记后续待办 | 差异检查、README、执行证据 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

先备份用户授权的三个未跟踪文件到仓库外，再只编辑 codec 测试观察真正的 RED。测试应证明目前 `descriptor(null)` 产生 JSON `null`，且 arguments/outcome 入口在 try/catch 外预校验时抛不同异常。随后把同一预校验纳入 codec 的私有持久内容异常边界，保持原有 Jackson/ToolSchema/ToolExecutionOutcome 行为。最后记录验证及未验收边界并提交本步骤，才锁定后续 journal 步骤。

### 4.2 Test-first strategy

在 `ToolExecutionJsonCodecTest` 增加 null descriptor、非法参数、null/inconsistent outcome 的编码侧拒绝与统一异常断言。RED 必须是这些断言未满足，不允许把编译、仓库解析或缺少环境当作行为 RED。GREEN 只收拢编码侧的预校验异常；不改变领域合同，不增加 JSON 中转映射。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | 基线 b9ee900 已提交 | 只读 journal 设计审阅 | ToolActionIntentSql 和任何未锁定持久/派发编辑 | 当前切片仅编解码安全边界；保持一项实现步骤 In Progress |

### 4.4 Commit boundaries

一个语义提交涵盖 codec、测试、README、本计划及证据报告。使用下方列出的显式路径暂存，不包含尚未接线的 `ToolActionIntentSql.java`。报告在提交前记录 baseline/检查结果，提交 hash 可在下一份报告中关联，不为了回填 hash 改写历史或多做同一任务提交。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| 固定工具 JSON | §13/T3-07 的持久参数和 receipt 需保真 | 既有 AgentContextJsonCodec、ToolExecutionJsonCodec 草稿 | 复用既有 Jackson 和领域值对象；不新建通用序列化层 | 一个私有 codec；无新网络调用 | Implement |
| 结果/参数验证 | §13/T3-07 固定 schema 和风险 | ToolSchema.values、ToolExecutionOutcome.validated | 直接调用原领域验证；不复制 DTO/校验框架 | 局部 try/catch 收拢 | Implement |
| 工具执行授权 | §13/T3-07 当前权限 | ToolExecutionAuthorization 已在 b9ee900 | 本步骤不调用、不伪造权限 | 继续保持运行入口关闭 | Already exists；后续接线 |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| 编码/解码封闭边界 | PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003 | writer null/无效输入断言 | 现有 ToolDescriptor/ToolSchema/Outcome、Jackson | 已测试的私有持久 JSON 表示 | 后续同源 journal | Step 1 |
| 证据与兼容说明 | PLAN-REQ-004 | diff 范围审查 | codec GREEN、source reactor | 精确报告与回退范围 | 用户审查及后续步骤 | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | 实际 AR/domain、application、infrastructure；蓝图 §6 明确天机边界 | 保持已授权天机模块 | 仅现有 infrastructure/persistence 私有 codec | Step 1；MC-ARCH-001 |
| Reuse/capability | Jackson、ToolSchema.values、ToolExecutionOutcome.validated | 原生框架/现有组件优先 | 无 POM、框架、工具类依赖新增 | Step 1；MC-REUSE-001、MC-DEP-001 |
| Naming/model/validation/conversion | ToolDescriptor/Outcome 是已有不可变值；AgentContextJsonCodec 是邻近范例 | 无新业务模型、跨层 DTO 或 Converter | codec 对现有值执行其构造/schema 验证；不创建手工映射 | Step 1；MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001 |
| Bean/logging/util/JSON/time/config | 包私有 codec、JDK + Jackson，无 Spring Bean/日志/时间 | 私密错误固定消息 | 不输出 body/cause；不改 Bean/profile | Step 1；MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001 |
| Business variation/pattern | 编解码顺序固定，复用领域验证 | Simple 直接实现 | 不增策略/管理器；现有工具 Adapter/治理边界后续接线 | Step 1；MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| JSON | Spring Boot 管理 Jackson；AgentContextJsonCodec | runtime POM 已有 jackson-databind；草稿封闭 mapper | 足够，无新库 | 复用同套 Jackson | None；仅现有 codec 修正 | Step 1；MC-REUSE-001、MC-DEP-001 |
| 工具值校验 | ToolSchema、ToolDescriptor、ToolExecutionOutcome | values/reference/validated 方法 | 足够，无需重复 schema 模型 | 直接复用 | None | Step 1；MC-VALID-001 |
| 持久存储/DDL | AgentLedgerService、MemoryPreferenceIntentStorageVerifier | 已有 JDBC/Flyway 隔离历史 | 本步骤无 DB 编辑/执行 | 保持原蓝图及当前用户 Flyway 不可变约束；不在 codec 任务改造迁移体系 | None | Step 1；MC-SCOPE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6、T3-07 | 邻近 AgentContextJsonCodec | test → ToolExecutionJsonCodec | 沿用 Codec/Test 职责后缀；不新建 carrier | 编译和声明检查 | Step 1 | PASS |
| Rule 2 | §13、T3-07 | ToolSchema.values、ToolExecutionOutcome.validated | test → codec | 验证持久表示输入/输出；本切片无新 Controller/Service/DAO 手交 | 正反编解码测试 | Step 1 | PASS |
| Rule 3 | §13、T3-07 | 既有不可变 domain records | test → codec | 无新 POJO/Converter；保留值对象防御复制 | equality/round-trip | Step 1 | PASS |
| Rule 4 | §6、T3-07 | codec 非业务服务、非 Spring Bean，无注入依赖 | test → codec | 无新业务 Bean/日志；错误只含固定消息 | 源码和异常断言 | Step 1 | N/A：无业务类或 Bean 编辑 |
| Rule 5 | §6、T3-07 | runtime 既有 JDK/Jackson | test → codec | 不添加工具库 | import/POM diff | Step 1 | PASS |
| Rule 6 | §13、T3-07 | Jackson 2 既有私有持久格式 | test → codec | 保持 strict duplicate/unknown/coercion 限制，禁止正文 cause | codec 回归 | Step 1 | PASS |
| Rule 7 | §6、T3-07 | 不编辑任何环境配置 | README、证据 | 不新增/变更配置键 | 文件范围检查 | Step 1 | N/A：配置不变 |
| Rule 9 | §13、T3-07 | 简单顺序编解码，无业务分派 | test → codec | 直接复用验证，不引入模式层 | 控制流审阅 | Step 1 | PASS |
| Rule 10 | §7.2、T3-07 | codec 不承载时间字段 | test → codec | 不新增日期/时间 API | imports/diff 检查 | Step 1 | N/A：无时间变更 |
| Rule 11 | §6、T3-07 | 原蓝图明确 AR 模块边界 | 全部当前步骤文件 | 保持现有天机 domain/application/infrastructure；无新持久/事件边界 | source reactor + 范围审查 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable
Reason: 本步骤仅 ToolDescriptor/ToolSchema/ToolExecutionOutcome 的 JSON 与异常边界；其字段无时间类型，不编辑 ToolActionIntentSql、lease、deadline、数据库时间列或任何 wire 时间合同。

## 5. Change File Tree

```text
stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java  CREATE（授权草稿接管）
stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java  CREATE（授权草稿接管）
README.md  MODIFY
docs/egon/evidence/2026-10-31-tool-journal-codec.md  CREATE
docs/egon/plan/2026-10-31-17-47-tool-journal-codec-boundary.md  CREATE（本计划）
```

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java` | 已有授权未跟踪四项测试 | 增加 writer 封闭失败断言 | RED/GREEN | Step 1 | PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003 | 执行者 |
| CREATE | `stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java` | 授权未跟踪 codec | 统一读写异常边界 | 私有持久编解码 | Step 1 | PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003 | 执行者 |
| MODIFY | `README.md` | T3-07 进度已有说明 | 准确补编解码范围 | 消费者/运维说明 | Step 1 | PLAN-REQ-004 | 执行者 |
| CREATE | `docs/egon/evidence/2026-10-31-tool-journal-codec.md` | 不存在 | 两个逐行检查矩阵、测试和未验收边界 | 可审计证据 | Step 1 | PLAN-REQ-004 | 执行者 |
| CREATE | `docs/egon/plan/2026-10-31-17-47-tool-journal-codec-boundary.md` | 本文 | 严格校验执行细化 | 当前步骤边界 | Step 1 | PLAN-REQ-004 | 执行者 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

用户本轮 AGENTS 规则适用；仓库内未发现额外 AGENTS 文件。baseline 为头部提交；既有三个未跟踪文件已获明确授权接管，但本步骤只纳入 codec 和其测试，`ToolActionIntentSql.java` 保留原状。先在仓库外保存三个文件的原始副本。无宽泛暂存、历史重写、push、PR 或运行启动。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| JDK/Maven | 根 README、`scripts/verify-source-reactor.sh`、`mvnw` | JDK25、既有可用 Maven 仓库/同级源码 | 编译/单测，不启动应用 |
| 计划校验 | workspace `.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py` | 本文及原蓝图可读 | strict 格式/追踪校验 |
| DB/模型/IAM | 原蓝图 §17/§19 | 本步骤不使用 | 未产生真实运行证明 |

### 6.3 Immutable constraints and approved decisions

不编辑现有迁移、POM、运行开关、领域/公开接口；不创建 DDL；不启动项目、浏览器、数据库或容器。用户对原蓝图现有 JDBC/Flyway 技术账本的明确执行授权保持有效，本切片不把它重构为另一迁移框架，也不宣称完成 MP-SDJ 迁移。没有业务事件发布或业务副作用。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | 私有持久 codec 的编码侧非法输入和读取侧同用固定 IllegalStateException | 草稿 read/write 方法已使用固定内容错误，预校验尚在 try 外 | 只关闭错误格式/私密泄漏缺口；无公共 API 异常合同 | 如发现公开调用方依赖原异常，返回本步骤审阅，不静默更改 |
| PLAN-CLAR-002 | 将先行 codec 切片独立提交 | 原 T3-07 要求固定 schema/hash 和 receipt，基线已有 domain 合同 | 只拆解顺序，不降低完整 T3-07 验收 | 后续 journal 必须另行执行，不得标为整项完成 |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 封闭工具持久 JSON 的编码与解码失败边界

- Requirements: PLAN-REQ-001、PLAN-REQ-002、PLAN-REQ-003、PLAN-REQ-004
- Dependencies: 已提交 b9ee900 的工具值与权限合同；无前序未提交步骤。
- Baseline state: 两个 codec 文件为用户授权草稿，四项原测试覆盖基本 round-trip/非法读取；编码预校验未统一，null descriptor 可生成 null JSON。
- Observable outcome: 不合法的描述/参数/结果在读写两侧均封闭拒绝，并保留固定 schema、工具 hash、receipt 和 UNKNOWN 语义。
- End state: 私有 codec 和回归可被后续 journal 使用；无持久 journal、授权执行、审批消费或 callback 暴露。
- Test-first gate: Required — 新增编码侧 null/非法输入断言先失败于现有行为，再最小收拢预校验异常；不得把编译/环境失败算 RED。
- Manual Checks: MC-ARCH-001、MC-REUSE-001、MC-DEP-001、MC-NAME-001、MC-VALID-001、MC-MODEL-001、MC-CONVERT-001、MC-LOG-001、MC-BEAN-001、MC-UTIL-001、MC-JSON-001、MC-TIME-001、MC-CONFIG-001、MC-PATTERN-001、MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001
- Literal Rules: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java`

- Purpose: 接管草稿测试并先声明编码侧缺失的封闭失败行为。
- Symbols: `ToolExecutionJsonCodecTest`、writer null/invalid argument/outcome tests、既有四项 round-trip/read tests。
- Repository evidence: 同包 AgentContextJsonCodecTest 风格及既有草稿的真实 ToolDescriptor/Schema/Outcome fixture。
- Dependencies and consumers: 只调用包私有 codec 及真实领域验证，JUnit5；不依赖 Store/DB/Runner。
- Why now: 先固定 RED，避免在实现后写镜像测试。
- Contract/signature changes: 无公开合同；新增 writer 必须抛固定 IllegalStateException 的断言。
- Input/output and state mapping: 合法 descriptor→相同 reference；参数整数→Long；成功结果→真实 receipt；UNKNOWN→未知 cost；非法值→无正文固定异常。
- Error and edge behavior: null 描述、未知键、类型错误、null outcome、缺写 receipt、重复键、尾随 JSON、过大/无效 UTF-8 都不接受；错误无 cause/secret。
- Standards impact: MC-VALID-001、MC-JSON-001、MC-MODEL-001、MC-SCOPE-001、MC-TEST-001；复用原不可变值与 Jakarta 边界，不新建跨层 DTO。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 6、Rule 11；使用真实领域/schema 校验，保留现有 CodecTest 命名和模块结构。
- Implementation pseudocode:

```java
@Test void encoding_invalid_private_content_is_rejected_consistently() {
    assertSanitized(() -> codec.descriptor((ToolDescriptor) null));
    assertSanitized(() -> codec.arguments(descriptor, Map.of("amount", "private arguments")));
    assertSanitized(() -> codec.outcome(descriptor, null));
    // assertSanitized: exact IllegalStateException, fixed message, null cause; no body in message.
    // Keep successful descriptor/arguments/receipt/UNKNOWN round-trips and strict read negatives.
}
```

- Verification contribution: TEST-001 观察现有 null descriptor/预校验异常的真实 RED，并在实现后 GREEN。
- After this file: 测试可编译；新增断言因 codec 边界尚未收拢失败，原四项测试保持原断言。

#### File 2 — `CREATE stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java`

- Purpose: 对授权草稿做最小编码侧边界修正，统一私密持久内容错误。
- Symbols: `descriptor(ToolDescriptor)`、`arguments(ToolDescriptor,Map)`、`outcome(ToolDescriptor,ToolExecutionOutcome)`、`write`、`invalid`。
- Repository evidence: 草稿读取方法已捕获 RuntimeException/IOException 并调用固定消息 invalid；AgentContextJsonCodec 为同模块私有持久格式。
- Dependencies and consumers: 复用 Jackson strict mapper、ToolDescriptor.reference、ToolSchema.values、ToolExecutionOutcome.validated；后续 ToolActionIntentSql 为潜在消费者，本步不接线。
- Why now: File1 已锁定缺失行为，现有领域验证足够，不需要新模型。
- Contract/signature changes: 保持包私有签名；编码侧 null/校验异常统一为无 cause 的固定 IllegalStateException。
- Input/output and state mapping: 非 null 描述按既有排序 mapper 写入；参数先按固定 input schema 归一；结果先 validated；全部产生最大 128KiB 有效 UTF-8 JSON。
- Error and edge behavior: 在本 codec 边界捕获预校验异常；保持 read unknown/duplicate/trailing/coercion 限制；不返回 JSON null、下游错误或私密 cause。
- Standards impact: MC-REUSE-001、MC-DEP-001、MC-VALID-001、MC-UTIL-001、MC-JSON-001、MC-SCOPE-001；JDK/既有 Jackson 足够，无 Bean/模型/时间改动。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 5、Rule 6、Rule 9、Rule 11；Simple codec 直接实现，调用已有领域值验证，不新增 DTO/Converter/框架。
- Implementation pseudocode:

```java
String descriptor(ToolDescriptor value) {
    try { requireNonNull(value); return write(value); }
    catch (RuntimeException malformed) { throw invalid(); }
}
String arguments(ToolDescriptor descriptor, Map<String,Object> values) {
    try { return write(descriptor.input().values(values)); }
    catch (RuntimeException malformed) { throw invalid(); }
}
String outcome(ToolDescriptor descriptor, ToolExecutionOutcome value) {
    try { return write(value.validated(descriptor)); }
    catch (RuntimeException malformed) { throw invalid(); }
}
```

- Verification contribution: TEST-001 所有 writer/read/round-trip 断言应 GREEN；TEST-002 检查领域合同未回归。
- After this file: 行为 GREEN，无运行入口和 schema 变化；再编写说明及证据。

#### File 3 — `MODIFY README.md`

- Purpose: 准确记录 T3-07 codec 切片可用范围及未完成接线。
- Symbols: 既有 T3-07/工具元数据说明段落。
- Repository evidence: README 已说明 metadata、稳定 intent 和独立 ToolExecutionAuthorization 并不等于执行权限。
- Dependencies and consumers: 面向天机使用者；依赖 File2 的实际结果。
- Why now: 仅在验证后描述已实现行为。
- Contract/signature changes: 无 API/配置变化；补充 codec 的安全属性。
- Input/output and state mapping: 标出固定 schema/hash、最大体积、错误脱敏；明确 journal/approval/dispatch/callback 待接线。
- Error and edge behavior: 不写成真实 DB、真实 IAM/MCP 或平台已验收。
- Standards impact: MC-SCOPE-001、MC-TEST-001；无代码/配置/依赖影响。
- Literal rule enforcement: Rule 11；保持原蓝图模块和职责边界，准确描述证据限制。
- Implementation pseudocode:

```text
Find the existing T3-07 tool governance paragraph in README.md.
Append the tested codec facts: pinned JSON, closed fields/types, bounded size, sanitized failures.
State that durable journal, approval consumption, dispatch and ADK tool registration remain pending.
Retain the existing default-off execution and operator-controlled runtime acceptance statements.
```

- Verification contribution: TEST-003 手工逐行比对实现与范围。
- After this file: README 不夸大本步骤能力。

#### File 4 — `CREATE docs/egon/evidence/2026-10-31-tool-journal-codec.md`

- Purpose: 保存本步骤检查、测试、原始文件保留和未验收边界。
- Symbols: Step1 baseline、命令/退出状态、17 项 Manual Check、10 项 Literal Rule、spec 追踪状态。
- Repository evidence: 执行 skill 要求每个 ID/Rule 单独记录；本计划 §12.5 规定适用范围。
- Dependencies and consumers: 依赖实际 RED/GREEN 输出及最终 diff；用于用户审查。
- Why now: 只在测试与最终源码检查后记录事实，不预填通过结果。
- Contract/signature changes: 无产品合同，纯证据文档。
- Input/output and state mapping: 实际命令→实际结果；每条检查→路径/符号与结论；完整 T3-07→Partial。
- Error and edge behavior: 跳过/未执行一律保留；不把源码/单测视为真实 PostgreSQL/模型/IAM 证明。
- Standards impact: MC-SCOPE-001、MC-TEST-001、MC-BLOCKER-001；逐行 evidence-backed 检查。
- Literal rule enforcement: Rule 1、Rule 2、Rule 3、Rule 4、Rule 5、Rule 6、Rule 7、Rule 9、Rule 10、Rule 11；分别记录，不合并。
- Implementation pseudocode:

```text
Record baseline b9ee900 and the copied original-file snapshot outside the repository.
Copy actual RED and GREEN commands and outcomes; attach source/diff evidence to every MC and Rule row.
Mark this codec slice satisfied only after validation; mark full T3-07 and runtime proof Partial/unverified.
Confirm no scripts/work harness was created, no service/browser/database started, no push performed.
```

- Verification contribution: TEST-003 最终审计与提交范围核对。
- After this file: 所有可验证本步骤门禁有具体证据，准备显式路径提交。

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`；计划校验在工作区根执行。
- Verification command: `./scripts/verify-source-reactor.sh -B -ntp -DskipTests=false -Dtest=ToolExecutionJsonCodecTest,ToolExecutionContractTest,ToolRegistryTest,ToolActionRequestTest -Dsurefire.failIfNoSpecifiedTests=false`；再运行 `git diff --check`。
- Expected result: RED 只失败于编码侧缺失边界；GREEN 命名 codec/既有工具领域测试全部通过，编译/格式 exit0；实际数量以报告为准。
- Failure returns to: File1 的 fixture/断言错误或 File2 的 codec 行为；依赖/环境失败单独报告，不能当作 RED。
- Completion criteria: 四项别名有实现/检查证据；17 MC、10 Literal Rules 分别 PASS 或证据支持 N/A；无未解释失败、越界文件和私密输出。
- Rollback: 运行尚未启用；可显式 revert 本步骤提交，保留未接线 ToolActionIntentSql 及仓库外原始副本；不改写历史。
- Commit paths: `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodecTest.java`、`stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJsonCodec.java`、`README.md`、`docs/egon/evidence/2026-10-31-tool-journal-codec.md`、`docs/egon/plan/2026-10-31-17-47-tool-journal-codec-boundary.md`
- Commit: `fix(agent): close tool journal JSON encoding failures`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| TEST-001 RED/GREEN | tianji-service | 上述 source-reactor 命令选择 ToolExecutionJsonCodecTest | codec 所有读写边界 | 先真实行为 RED 后全 GREEN | Step1 File1/2 | PLAN-REQ-001/002/003；单测 |
| TEST-002 领域回归 | tianji-service | 上述命令同时选择 ToolExecutionContractTest、ToolRegistryTest、ToolActionRequestTest | 既有工具合同 | 零回归；selector 以实际文件名核实 | Step1 File2 | PLAN-REQ-001/002/004；单测 |
| TEST-003 范围审计 | tianji-service | `git diff --check`、逐行 diff/status、17 MC/10 Rule | 五条提交路径与完整原始状态 | 只有本步骤路径、无秘密/格式问题 | Step1 | 全部；静态 |
| TEST-004 计划结构 | workspace root | `python3 .agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py tianji-service/docs/egon/plan/2026-10-31-17-47-tool-journal-codec-boundary.md --strict` | 本计划 metadata/追踪 | exit0 | 本计划 | 全部；文档 |

复用既有 source-reactor 命令，不创建临时验证脚本。没有 `scripts/work/` harness；无 DB/模型/HTTP 运行验收任务在本步骤被执行或声称通过。

## 9. Migration, Compatibility, Rollout, and Rollback

本步骤无数据库/manifest/迁移/配置/API/事件合同编辑。现有 SQL 全部不可变。两份授权草稿的状态从未跟踪变为本次审阅提交，不改变已发布的公开 API。新 codec 尚无可用存储入口，默认工具执行拒绝保持。回滚仅撤销本步骤提交；后续 journal 的唯一新版本必须由其独立步骤确定，不能在本步骤预生成或执行。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| PLAN-REQ-001 | §13、§19 T3-07 | Step 1 | codec、codec test | TEST-001、TEST-002 | pinned round-trip/type/schema/hash 回归 |
| PLAN-REQ-002 | §7.2、§19 T3-07 | Step 1 | codec、codec test | TEST-001、TEST-002 | receipt 与 UNKNOWN 区分 |
| PLAN-REQ-003 | §13、§19 T3-07 | Step 1 | codec、codec test | TEST-001 | 读写固定异常、无私密正文/cause |
| PLAN-REQ-004 | §6、§19 T3-07 | Step 1 | README、本计划、执行证据 | TEST-003、TEST-004 | 无 schema/授权/dispatch 接线、完整任务仍 Partial |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| DEC-001 | 既有三个未跟踪文件接管 | codec/test；SQL 后续 | 本轮用户明确同意继续完善提交；先备份 | 主代理 | Closed：仅本步骤两份代码接管 |
| DEC-002 | 旧蓝图无新版 strict metadata | 本计划 | 用户明确授权 exact 原计划剩余工作；本文是局部执行细化 | 主代理 | Closed：保持 Review，显式记录原状态与范围 |
| RISK-001 | 本步骤不提供持久执行能力 | 后续 T3-07 | 无 journal Store/审批消费/运行装配 | 主代理 | Tracked：后续锁定独立执行步骤，整项不标完成 |
| RISK-002 | 源码/单测不能证明 provider/DB/IAM | 运行验收 | 用户禁止自动启动运行系统 | 用户 | Tracked：保留 runtime unverified，不启动 |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

只落实原 T3-07 固定工具持久表示的先行切片，保留完整蓝图未完成任务；分步提交、已有工作保护、用户启动运行系统的约束全部保持。

### 12.2 Spec consistency

两个 Plan Clarification 均为现有私有 codec 异常边界和提交顺序细节；没有新业务权限、状态、API 或迁移。必要性审计确认复用领域验证和既有 Jackson 足够，不创建 fetch-then-forward 接口、缓存、框架或额外层。

### 12.3 Repository executability

两个草稿文件和其依赖已核实；README 段落、source-reactor 命令和基线合同均存在。实施时再次核对 selector 和最终工作树；只有 Step1 commit 后才能编辑下一持久步骤。

### 12.4 Test and release completeness

本计划要求新增真实行为 RED、全部 codec GREEN、最小领域回归和差异审查。没有发布/部署活动，运行验收保持未执行；计划结构通过不代表实现或运行通过。

### 12.5 Blocking Manual Check

以下是计划级可执行性结论，实施后须在独立证据文档逐行重评，不预先宣称执行检查已通过。

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 原蓝图 §6、AR infrastructure/persistence 原目录 | 本切片保持已授权现有模块；无新架构 | None |
| MC-REUSE-001 | Applicable | PASS | §4.7 ledger、ToolSchema.values/Outcome.validated | 复用框架与原领域值校验 | None |
| MC-DEP-001 | Applicable | PASS | §5 无 POM 或 dependency 文件 | 无新依赖/工具 | None |
| MC-NAME-001 | Applicable | PASS | ToolExecutionJsonCodec/Test；邻近 AgentContextJsonCodec | 私有 codec 保持职责命名，无新 carrier | None |
| MC-VALID-001 | Applicable | PASS | §7 writer/read 先 schema/validated 再 JSON | 仅私有表示边界；无新层间输入模型 | None |
| MC-MODEL-001 | Applicable | PASS | 复用 immutable ToolDescriptor/Schema/Outcome | 不创建实体或可变 POJO，值对象不变 | None |
| MC-CONVERT-001 | Not applicable | N/A | §5 无 Converter 或跨层模型映射 | 私有 JSON 读写是持久表示，不是替代 MapStruct 的对象转换 | None |
| MC-LOG-001 | Not applicable | N/A | codec 非业务服务，§5 无业务类 | 不新增业务日志，私密异常固定消息 | None |
| MC-BEAN-001 | Not applicable | N/A | 包私有 codec 无 Spring stereotype/injection | 无 Bean 命名、构造注入或 Qualifier 改动 | None |
| MC-UTIL-001 | Applicable | PASS | 已有 JDK/Boot-managed Jackson import | 无第三方 utility 或 duplicate Utils | None |
| MC-JSON-001 | Applicable | PASS | §7 严格 mapper、schema/hash、统一错误 | 闭合和类型合同具备具体测试 | None |
| MC-TIME-001 | Not applicable | N/A | §4.9；三个 domain 值无时间字段 | 本切片没有时间语义/精度/zone 改动 | None |
| MC-CONFIG-001 | Not applicable | N/A | §5 无 application/profile/properties 文件 | 环境键集合保持不变 | None |
| MC-PATTERN-001 | Applicable | PASS | §4.5/4.7 简单顺序表示验证 | Simple 直接实现，避免新策略层 | None |
| MC-SCOPE-001 | Applicable | PASS | §5 五条路径，保留 SQL 草稿 | 原始文件备份、显式路径提交，完整任务保持 Partial | None |
| MC-TEST-001 | Applicable | PASS | §8 命名 RED/GREEN/领域/静态命令 | 计划定义可执行证明，实施报告记录实际结果 | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 与其余独立检查 | 当前 codec 切片无未决重大设计，后续存储独立锁定 | None |

### 12.6 Final verdict

PASS — Ready for user review
