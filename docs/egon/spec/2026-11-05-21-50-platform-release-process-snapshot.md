# T4-01A2 天机签名候选与 Process 发布快照离线对照

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 现有离线入口内增加一次钉选文件读取、固定 DTO 对照及确定性输出 |
| Created | 2026-11-05 21:50 Asia/Shanghai |
| Updated | 2026-11-05 21:50 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | T4-01A2 已签名候选与单个 Process 发布快照的离线一致性 |
| Change Surface | 既有 PS CLI 与签名验证器，以及该 CLI 的有限测试 |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) 的不可变清单与发布依赖校验 |
| Baseline Revision | tianji-service 8de3e76184d4812ed2b1bac982b7c6889e4c49c0，A2 74369e760829d60e41d658b780681a54efcb22a0 |
| Amends | None |
| Supersedes | None |
| Depends On | [A2 §9/§10](2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) · [A1 §9](2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) |
| Related Specs | None |
| Related Plans | [Process snapshot implementation](../plan/2026-11-05-21-51-platform-release-process-snapshot.md) |

## 1. Summary

发布操作者显式运行现有 PS exec JAR，将完成 A2 验签的候选与独立钉选的一份既有 Process 发布元数据文件逐项对照。该操作能发现同名发布的模型渲染差异，以及版本、摘要、模型集合和有效输入契约偏差。

## 2. Background and Current State

| Evidence ID | Classification | Source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Committed source | [A2 Spec §9](2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) · [A2 evidence](../evidence/2026-11-05-platform-release-detached-signature.md) | PS 已可验证独立钉选候选及公钥，同一安全根中的文件按路径和物理文件身份去重。A2 签名绑定候选原始字节，成功证据保留四项待确认权限。 |
| EVD-002 | Committed source | [ProcessController](../../../stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessController.java#L74) · [ProcessReleaseDTO](../../../stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessReleaseDTO.java#L9) | 现有 GET 对当前 actor 授权，返回直接 DTO，含 releaseVersion、manifestSha256、createdAt 和 models。返回对象没有租户、issuer、读取时点或来源证明。 |
| EVD-003 | Committed source | [T3-12 renderRelease](../../../stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java#L1023) · [PS release manifest](../../../stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java#L402) | expense 夹具依角色渲染 BPMN/CMMN。相同 release 名下，渲染后的 XML 字节和有效输入决定清单。离线复算已发现原模板 f65fc8ac… 与合成角色变体 77b76704… 摘要不同。 |
| EVD-004 | Committed source | [A1 candidate](2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) · [Master §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) | 候选记录 Process releaseVersion、manifestSha256、各模型 type/definitionKey/modelSha256/inputs。主计划后续另有各 owner 审核、撤销、CAS 和治理生效。 |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 现有模块的显式离线技术入口增加一个子命令。没有 archetype、业务示例、迁移、启动接线或资源登记。

## 3. Goals and Non-goals

一份通过 A2 的候选对一份独立钉选的 Process 公开 DTO 文件，产出可复验的离线匹配结果。真实 owner publication、trust 分发、当前 signer/revocation、review、activation、rollback 和 runtime 继续各自验收。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS offline CLI and verifier | Affected | PlatformReleaseArtifactCli#run and PlatformReleaseSignatureVerifier#verify | 新增显式 compare-process-snapshot，验证 A2 后检查快照 | Exact argv, input, comparison, output and codes | §7, §8, §9, §10, §14, §15, §16 |
| Local file/JSON/canonical | Context-only | PlatformReleaseArtifactValidator#readSignatureInput/parse/canonical | 复用同实例安全读取和编码 | Binding and regression | §7, §9 |
| Process public DTO/API | Context-only | EVD-002 | 读取现有公开 DTO 形状，HTTP 合同和授权保持 | DTO mapping and authority | §9/10 |
| Persistence, UI, Bean, IAM | Not applicable | EVD-001/004 | 由现有 owner 管理 | Exact scope | §11/12/16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 显式本地子命令只受理已完整验签候选 | Must | 四个现有 A2 证据参数及原 scope 参数保持，候选签名成功后才读取快照，旧 verify 和 verify-signature 独立工作 | EVD-001/004 |
| REQ-002 | 固定快照原始字节并严格解析现有 DTO | Must | 安全根中物理 distinct，1–65,536 字节，独立 raw SHA 匹配才解析，闭合 JSON 和时间字段 | EVD-001/002 |
| REQ-003 | 精确比较整个 Process 发布模型集合 | Must | 版本、清单、模型 type+key、内容 SHA 和完整有效输入映射全部匹配，模型/输入顺序等价，同名不同模型拒绝 | EVD-002/003/004 |
| REQ-004 | 保留离线权限边界并可复验 | Must | 固定成功状态、证明字节/安全拒绝与原 pendingAuthority，独立 PS exec 包调用 | EVD-001/004 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | 发布操作者 | 对照已验签候选与已有发布元数据 | 当前私有 root 所有者，独立选择候选、公开 trust 与快照 pin |
| ACTOR-002 | 证据审阅方 | 复验同一候选和快照是否一致 | 通过独立渠道获得同一输入和钉选，未来治理权限另行确认 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Reconcile one signed candidate and Process snapshot | ACTOR-001 | ACTOR-002 / existing PS DTO | Explicit local CLI | A2 verified within current time; separately pinned authorized export bytes | PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE | Bad A2, wrong pin, malformed snapshot, metadata drift or unavailable file return fixed diagnostics | Local proof only, no state changes | REQ-001–004 | CLI-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | 显式本地比较。操作者通过现有获授权 GET 获取文件，程序只读取该 DTO 文件。 |
| DEC-002 | 文件期望 raw SHA 由操作者在比较前独立审阅并提供。候选与同源未审阅输入中的摘要无权替代。包验证使用提前冻结的公共合成向量和独立 literal pins。 |
| DEC-003 | 签名和信任语义沿用 A2，成功证明中 tenantId/channel 描述签名候选范围。快照无租户字段，因而无法证明快照归属。 |
| DEC-004 | 比较 releaseVersion、manifestSha256 与完整模型。createdAt 只做严格形状校验和 raw SHA 绑定。 |
| OPEN-001 | 生产审阅、信任分发和当前 owner source policy 另审。该决定不阻塞本切片的 synthetic offline 证明。 |

## 6. Project Technology Context

现有 PS 技术包，JDK 25、Jackson 3、JUnit 6，复用 A1 安全读/JSON/规范编码和 A2 JDK 签名验证，复用现有 neutral Process DTO。无需依赖或构建 profile 变化。

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
| Rule 1 | Yes | PS release package | Semantic verifier, immutable ProcessSnapshotInputDTO | §8/10 | TEST-001/002 | PASS |
| Rule 2 | Yes | Existing A1 strict JSON and A2 CLI boundary | Closed args, strict shapes, ProcessReleaseDTO/ProcessModelDTO constructors, no Spring layer crossing | §9 | TEST-001/003/004 | PASS |
| Rule 3 | Yes | Existing immutable A2 DTOs | Immutable record and typed neutral DTOs, local comparison only | §10 | TEST-001/002 | PASS |
| Rule 4 | No | Existing offline main | No managed bean or business log | §7/9 | TEST-005 | N/A |
| Rule 5 | Yes | Existing A1/A2 helpers | Reuse JDK file/time and existing Jackson | §6/9 | TEST-001/003 | PASS |
| Rule 6 | Yes | A1 canonical Jackson output | Strict closed JSON and enum names, fixed canonical proof | §9/10 | TEST-001/003 | PASS |
| Rule 7 | No | Existing exec packaging | Explicit subcommand without environment config changes | §16 | TEST-005 | N/A |
| Rule 9 | No | Direct bounded comparison | No strategy, plugin or Spring layer for one fixed contract | §13 | TEST-001/002 | N/A |
| Rule 10 | Yes | Existing DTO Instant and A2 Clock | Strict java.time UTC metadata parse, A2 verification Clock | §6.3 | TEST-003/004 | PASS |
| Rule 11 | Yes | Existing PS ownership | Same package, no persistence, application layers or event | §7/11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ProcessReleaseDTO.createdAt | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: omitted from proof | Strict calendar year 0001–9999, rejects leap-second and 24-hour spelling | Exact parsed nanos, no Clock comparison | Existing DTO read body accepted | TEST-001/003 |
| TIME-002 | A2 verification now and validity windows | Occurred instant | java.time.Clock and Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: CLI | A2 semantics unchanged | Current single UTC sample at full precision | Old A2 proof preserved | TEST-004 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| PS CLI compare-process-snapshot | Expand | REQ-001/004 | Run verify-signature alone | It establishes signature validity without detecting snapshot drift | One opt-in CLI argv branch, no new entrypoint | Add |
| Same verifier checks snapshot | Expand | REQ-002/003 | Compare only manifest text or add online lookup | Model schema mismatch remains possible; online lookup requires source/role policy | One bounded local read, one in-memory comparison, no persisted state | Add |
| A1/A2 reader and shared C DTO | Reuse | REQ-001–004 | Create separate filesystem/serialization implementation | Existing closed secure read and public validation fit | Same root/identity fence and neutral data shape | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct A2 baseline | 0 | Signed artifact verified or refused | None | Existing pinned inputs only | Signature and binding evidence |
| Selected offline comparison | 0 | Match or fixed refusal | None | Existing secure single read of each distinct input and raw snapshot pin | Detects same-version and model/input drift |

### 7.1 System Architecture Design

操作者通过现有 PS PropertiesLauncher 的离线 main 执行。同包 verifier 复用 A2 和 A1 本地读取，成功结果仅写 stdout。没有 HTTP、网络、Spring、应用子进程、DB 或 IAM 调用。

### 7.2 High-Level Design

校验 argv 后依既有 A2 次序检查候选、公钥、scope、UTC 及签名。成功才以同一个文件 reader 读快照，一次读取所得字节同时用于 SHA 和严格解析。通过完整事实对照后规范编码一行。

### 7.3 Detailed Design

以 ProcessModelDTO 比较 type、key、contentSha256 与 inputs。公开快照与已验证 A1 候选的模型字段只有摘要键名映射。对候选既有重复/顺序校验保留，快照由 ProcessReleaseDTO 拒绝重复 type+key。对两个无重复集合做相等比较。快照 createdAt 不参与相等判断，仍随 raw SHA 一起被固定。验证失败时不构造输出。

## 8. Package Structure and Code File Tree

| Change | Path | Responsibility |
| --- | --- | --- |
| M | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java | 已有测试内用公开合成 A2 向量及真实 DTO 序列化验证四个有限行为。 |
| M | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java | 既有验签尾部增加可选固定快照对照。 |
| M | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java | 增加独立显式 argv 分支与安全退出码。 |
| C | docs/egon/evidence/2026-11-05-platform-release-process-snapshot.md | 单 Step 的有限与离线成品证据。 |
| C | docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md | 审阅合同。 |
| C | docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md | 单 Step 实施与具体门禁。 |

## 9. Interface Definitions

### 9.1 Interface Inventory

| ID | Name | Protocol | Owner | Caller | Change |
| --- | --- | --- | --- | --- | --- |
| CLI-001 | compare-process-snapshot | Explicit local CLI | PS release package | ACTOR-001/002 | New subcommand under existing main |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CLI-001 — 对照一个已签名候选和本地 Process 发布快照

##### Necessity and interaction-cost decision

A2 只能核验签名。独立钉选快照比较提供同名内容差异检测，调用成本为同一个显式进程和一个本地有界文件读取。现有 DTO 及信任选择规则继续作为上游证据。

| Concern | Decision |
| --- | --- |
| Change classification | 显式离线 Query |
| Independent consumer goal | 发现候选与本地发布材料的同名内容差异 |
| Parameter ownership and derivation | 操作者独立选择三项 pins，四份文件提供固定材料 |
| Direct/no-new-interface alternative | 既有 verify-signature 不读取快照，不能发现发布元数据漂移 |
| Caller use of result | 将固定 SHA 绑定的证明保存为离线审阅材料 |
| Round trips and failure points | 单次进程，四次有界读取，零网络，固定错误 |
| Verdict | 同一个 main 增加一个离线子命令，保留现有两个模式 |

##### Identity and purpose

现有 `top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli`，由 PS exec JAR 的 `org.springframework.boot.loader.launch.PropertiesLauncher` 运行。调用者必须提供 A2 参数，以及独立保存的公开快照文件和 SHA-256。

##### Request parameters

| Option | Required | Validation and meaning |
| --- | --- | --- |
| --root, --candidate, --signature, --trust, --expect-candidate-sha256, --expect-trust-sha256, --tenant, --channel | Yes | A2 §9.2 的全部精确规则、独立 pins 与作用域，次序可换，每项只出现一次。 |
| --process-snapshot | Yes | 当前同一个私有 POSIX root 内的相对文件，1–65,536 raw bytes。沿用 A2 路径、owner、非链接、物理身份、权限与不变读检查。文件须与其他三个输入物理 distinct。 |
| --expect-process-snapshot-sha256 | Yes | 独立提供的 64 lowercase hex，原始快照字节 SHA-256。 |

恰好十个选项键值对，拒绝未知、重复、空值或遗漏。快照使用严格 UTF-8 JSON，拒绝重复键、尾部 token、未声明/缺失/null 字段及宽松强转。根是精确 `ProcessReleaseDTO(releaseVersion, manifestSha256, createdAt, models)`。模型数组含 1–16 个精确 `ProcessModelDTO(type, definitionKey, contentSha256, inputs)`；枚举沿中立 DTO，输入对象含 0–16 个已验证变量键和有效枚举值。createdAt 采用 UTC Z 严格 uuuu-MM-dd'T'HH:mm:ss 格式，允许 1–9 位小数。标识遵循 C DTO 约束。匹配关系中 A1 `modelSha256` 对应 DTO `contentSha256`，其余模型字段同名。

##### Success response

退出 0、stderr 空，stdout 为一个 A1 canonical JSON UTF-8 对象和单一 LF。保留 A2 §9.2 的全部既有证明字段，其中 `status` 是 `PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE`，额外唯一键 `processSnapshot` 只含 `sha256`、`releaseVersion`、`manifestSha256`。pendingAuthority 精确继承 `CURRENT_SIGNER_AUTHORITY`、`OWNER_PUBLICATION`、`LIVE_REVOCATION`、`REVIEW_AND_ACTIVATION`。

##### Error responses

| Exit | Fixed stderr plus LF | Condition |
| --- | --- | --- |
| 64 | TIANJI_RELEASE_PROCESS_SNAPSHOT_USAGE | argv 错误 |
| 65 / 66 | Existing A2 signature diagnostic | 快照读取前 A2 校验或读文件失败，沿用 A2 §9.2 原优先级 |
| 65 | TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID | 不安全、冲突或超限快照，或非法 DTO 形状 |
| 65 | TIANJI_RELEASE_PROCESS_SNAPSHOT_DIGEST_MISMATCH | 快照 raw pin 不符，发生在解析前 |
| 65 | TIANJI_RELEASE_PROCESS_SNAPSHOT_MISMATCH | 合法 DTO 与签名候选比较不等 |
| 66 | TIANJI_RELEASE_PROCESS_SNAPSHOT_IO_UNAVAILABLE | 快照文件 IO 或成功结果写入失败 |

写成功结果之前的任何拒绝 stdout 为空。输出流物理失败可能已接收部分字节，调用方必须同时检查退出码和精确结果。

##### Interface logic for frontend and consumers

依顺序独立取得候选与 trust pin、既有已授权导出及快照 pin。在当前 A2 有效窗口内执行，读取退出状态与固定一行结果。只有 status、绑定 hash 和当前提供的候选/快照相符时，才能作为本地一致性材料。授权和现时来源始终由其 owner 证据建立。

##### Compatibility and verification

旧 `verify` 与 `verify-signature` 的入参、状态、字节和退出码保持。A1 候选与 A2 文件证据保持，不增加 HTTP/SDK/前端接口。有限测试与同一实际打包 CLI 验证。

## 10. POJO and Data Model Design

`ProcessSnapshotInputDTO(path, expectedSha)` 为 PS 技术包内不可变输入，不对外发布。复用现有 `ProcessReleaseDTO` 和 `ProcessModelDTO` 的强类型语义。证据为已存在 A2 证明的固定 JSON 投影加小型快照对象，沿用规范编码，无业务实体或持久化转换。

## 11. Database Design

N/A. 对照 CLI 无 JDBC 或数据库；源 Process GET 仍由原有 owner 管理。

## 12. Frontend Page Design

N/A. 子命令由本地发布操作者显式运行。

## 13. Design Patterns and Architecture Principles

考虑新增比较策略或独立 service。该范围只有一种快照契约、同一验证次序且只有一个消费者。直接组合现有验证与纯值比较可在一个包内维持签名优先顺序，避免重复读取或扩展层。

## 14. Test Design

| ID | Scope | Proof |
| --- | --- | --- |
| TEST-001 | CLI 正例与重放 | comparesSignedCandidateWithPinnedPublicProcessSnapshot，真实 DTO 正例、raw pin、顺序无关、确定性与旧 A2 exact stdout |
| TEST-002 | 精确差异 | refusesReleaseManifestModelAndEffectiveInputDrift，版本、同名不同清单、模型 hash/key、有效输入、额外/缺失模型 |
| TEST-003 | 安全 DTO 与本地输入 | refusesMalformedPublicSnapshotAndUnauthorizedFields、verifiesSignatureBeforeReadingDistinctBoundedSnapshotFile，闭合输入、日期、大小、别名和安全路径 |
| TEST-004 | A2 先决条件及兼容 | 签名失败优先、旧 A1/A2 10 项有限测试保持 |
| TEST-005 | 成品验证 | 现有 permission-producer 构建的 PS exec JAR，用独立冻结公开 pins，匹配/重放/错 pin/语义差异/坏 shape/签名失败/IO/usage/原 A2 |

## 15. Non-functional and Cross-cutting Design

一次有界快照读取，最多 16 个模型与各 16 项输入，零网络、密钥私有材料、运行应用或持久化。stderr 固定诊断且不回显敏感路径/内容；包验证在 30 秒逐子进程界限内执行。现有 PaC root entry 和 Start-Class 保持。

## 16. Compatibility, Migration, Rollout, and Rollback

现有离线入口保持。只通过额外子命令选择启用。无配置、迁移、数据库、权限或外部升级。回滚仅回退一个聚焦提交，先前独立证据保持可读。

## 17. Alternatives and Decisions

| Alternative | Decision | Reason |
| --- | --- | --- |
| 只比版本或清单 | Reject | 同名渲染与模型级声明偏差需核验完整集合。 |
| 远程读取或服务端实时预检 | Defer | 现有公开 DTO 缺租户来源，需另审当前可信信任和授权。 |
| 本地受控 A2 对照 | Select | 可独立发现源制品与发布材料差异，复用同一已有操作者入口。 |

## 18. Risks and Open Questions

| Risk/open item | Handling |
| --- | --- |
| 过期或伪造的本地快照可由操作者错误钉选 | 明示离线语义和已有 pendingAuthority。独立钉选只能固定原始字节，不能证明来源和现时授权。 |
| 缺真实 signer/reviewer/CAS 规则 | 继续独立 owner 决策，有限测试使用冻结公开向量。 |

## 19. Traceability Matrix

| Requirement | Use case | Design | Interface | Verification |
| --- | --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9 | CLI-001 | TEST-004/005 |
| REQ-002 | UC-001 | §6.3/9 | CLI-001 | TEST-001/003/005 |
| REQ-003 | UC-001 | §7/10 | CLI-001 | TEST-001/002/005 |
| REQ-004 | UC-001 | §9/14/15 | CLI-001 | TEST-001/004/005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing PS release owner | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002 | A1/A2 guard and C DTO | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | Scope-specific immutable DTO | None |
| MC-VALID-001 | Applicable | PASS | §9/14 | Closed signed and snapshot boundaries | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Immutable existing neutral values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Technical wire projection only | None |
| MC-LOG-001 | Not applicable | N/A | §9 | Fixed stderr, no business logger | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | Offline main only | None |
| MC-UTIL-001 | Applicable | PASS | §6 | JDK and existing A1 helpers | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | Strict UTF-8, exact canonical output | None |
| MC-TIME-001 | Applicable | PASS | §6.3 | Existing signature windows, exact UTC snapshot metadata | None |
| MC-CONFIG-001 | Not applicable | N/A | §16 | Explicit argv | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | Direct composition | None |
| MC-SCOPE-001 | Applicable | PASS | §3/8/16 | Offline source only | None |
| MC-TEST-001 | Applicable | PASS | §14 | Named finite and package proofs | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/18 | Exact offline behavior independent of real governance | Production owner source remains separately gated |

### 20.6 Final verdict

PASS — Ready for user review
