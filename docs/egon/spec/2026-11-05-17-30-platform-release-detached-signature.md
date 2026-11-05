# T4-01A2 天机候选制品离线签名验证

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | 单一离线 CLI 子命令，三个有界文件，固定签名算法和公钥范围，无跨服务协作或状态变更 |
| Created | 2026-11-05 17:30 Asia/Shanghai |
| Updated | 2026-11-05 17:38 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | T4-01A2 PS 离线 A1 候选验证与外部分离签名 |
| Change Surface | 现有 PS release CLI，复用 A1 私有文件和规范编码，一组有限测试 |
| Affected Chapters | §7, §8, §9, §10, §14, §15, §16 |
| Source Requirement | [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) 签名验证切片 |
| Baseline Revision | A1 2f10711b152bbce7000ba7fb31d6f931e4f52927；current tianji-service 17fee280792e95da938c68ba65a783b965ca63d8 |
| Amends | None |
| Supersedes | None |
| Depends On | [A1 制品候选 §9/§10](2026-11-05-16-17-platform-release-artifact-candidate.md) |
| Related Specs | None |
| Related Plans | [A2 implementation](../plan/2026-11-05-17-44-platform-release-detached-signature.md) |

## 1. Summary

发布操作者拿到一份已经生成的 A1 候选、其分离签名和通过独立渠道审阅过的公开信任描述后，可运行同一个 PS exec JAR 的离线子命令。子命令验证字节、候选摘要、签名、租户、渠道、签名角色和有效期，向 stdout 写一行可审阅的独立验证结果。A1 候选原封保留。现行签名权威、owner 发布、实时撤销、审核及 CAS 仍需各自的治理证据。

## 2. Background and Current State

| Evidence ID | Classification | Source | Observed fact |
| --- | --- | --- | --- |
| EVD-001 | Committed source | [A1 §9/§10](2026-11-05-16-17-platform-release-artifact-candidate.md) · [A1 evidence](../evidence/2026-11-05-platform-release-artifact-candidate.md) | 现有 PS PlatformReleaseArtifactCli 和 Validator 产出 ARTIFACT_CHECKED_ONLY，摘要有独立域，最终规范 JSON 以一个 LF 结尾。已有本地包执行证明。 |
| EVD-002 | Static repository | [DdcLegacyImportService#verifyMappingSignature](../../../../tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/migration/DdcLegacyImportService.java#L60) | 天枢迁移从单独提供的 Base64 X.509 公钥加载 JDK Ed25519，以 initVerify、update、verify 验证原始字节。该调用属于天枢自己的迁移身份边界。 |
| EVD-003 | Published standard | [Java 25 Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html) · [RFC 8410 §3–4](https://www.rfc-editor.org/rfc/rfc8410.html#section-4) · [RFC 8032 §7](https://www.rfc-editor.org/rfc/rfc8032.html#section-7) | JDK 提供固定 Ed25519 算法名，公钥 SPKI 参数为空缺，Ed25519 原始签名是 64 字节。 |
| EVD-004 | Scope decision | [主计划 §19 T4-01](../../../../docs/plans/agent-framework-upgrade-plan.md) | 完整 release 治理另需各 owner 事实、撤销、人工决策和作用于当前版本的 CAS。 |

现有 A1 CLI 接收受控目录和构建输入，独立读取制品并生成候选。A2 从 A1 的输出开始。A2 针对签名是否绑定操作者独立选择的候选和公钥，而不需要再次解析 JAR 或连接 owner。

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: 保持现有 PS 模块与启动入口，无 archetype、示例数据或迁移。

## 3. Goals and Non-goals

目标是提供可独立复现的分离签名验证和最小验证证据。真实签发、密钥目录、密钥轮换、在线撤销、证据持久化、owner 出版/授权、审核、激活、CAS、迁移和运行时验收独立交付。

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS offline release CLI | Affected | PlatformReleaseArtifactCli#run，PlatformReleaseArtifactValidator#verify | 新增一个显式子命令，既有 A1 argv 和输出保留 | Exact args, payload, errors and bytes | §7, §8, §9, §10, §16 |
| Trust inputs and JDK crypto | Affected | Tianshu precedent in EVD-002 | 独立外部公开信任钉选，Ed25519 单密钥验证 | Exact authority, digest, time and safe failures | §9, §10, §14, §15 |
| Archive, owner API, IAM, DB, UI | Context-only | A1 evidence, master T4-01 | 原有模块继续拥有自身发布、身份和运行时状态 | Scope and regression | §11–16 |

## 4. Requirements and Acceptance Criteria

| ID | Atomic requirement | Priority | Observable acceptance criteria | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | 保持同一 A1 候选 | Must | A1 exact canonical bytes 和独立期望 candidateSha256 匹配，claims 同时绑定候选摘要与完整外壳原始 SHA | A1 §9/§10 |
| REQ-002 | 独立固定唯一公开信任 | Must | trust 原始字节 SHA 与操作者指定的 digest 相同，SPKI 与 keyId、role、purpose、tenant、channel 全部匹配 | EVD-002/003 |
| REQ-003 | 真实验证 Ed25519 与签名有效期 | Must | 明确域的有效向量通过。变更、错误公钥、混用范围和时间边界失败 | EVD-002/003 |
| REQ-004 | 安全离线输入和失败 | Must | 私有 POSIX root、安全三个文件、有界严格 JSON、固定退出码及错误、成功前 stdout 空 | A1 §9 |
| REQ-005 | 验证证据与发布权威分离 | Must | 唯一成功状态是 ARTIFACT_SIGNATURE_VERIFIED_OFFLINE，原 A1 状态保持，输出保留待验证权威 | Master T4-01 |

### 4.2 Use-case analysis

#### 4.2.1 Actor inventory

| Actor ID | Actor | Goal | Permission/context |
| --- | --- | --- | --- |
| ACTOR-001 | 天机发布操作者 | 传入从独立渠道取得的候选和公开 trust 摘要 | 当前本地目录所有者，核对单一 tenant/channel |
| ACTOR-002 | 签发方 | 提供已签名的 detached claims | 真实签发授权由未来 owner 流程决定，A2 不持有其私钥 |
| ACTOR-003 | 证据审阅方 | 检查离线结果并保留缺项 | 可根据同一独立钉选重验，后续治理独立判断 |

#### 4.2.2 Use-case artifact

| ID | Use case/goal | Primary actor | Supporting actors/systems | Trigger | Preconditions | Main success outcome | Alternatives/failures | Postconditions | Requirements | Interfaces/pages | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify one pinned detached release signature | ACTOR-001 | ACTOR-002/003 | Explicit local invocation | A1 candidate, sidecar, externally reviewed trust SHA and candidate SHA | Exact one-line ARTIFACT_SIGNATURE_VERIFIED_OFFLINE | Invalid format, digest, scope, time or signature exits nonzero | No state writes, reverify only within validity | REQ-001–005 | CLI-001 | TEST-001–005 |

## 5. Constraints, Assumptions, and Decisions

| ID | Decision or constraint |
| --- | --- |
| DEC-001 | PS 复用现有 exec JAR 和显式 PropertiesLauncher。只接受 Ed25519，一份信任描述只允许一个公开密钥。 |
| DEC-002 | 操作者在制品来源之外取得并审阅 trust 原始 SHA，且单独选择期望候选摘要。候选、签名或下载同一来源给出的 SHA 不能成为该独立信任。 |
| DEC-003 | 信任描述只为固定 release 签名用途和签名角色声明一个精确租户、渠道和有效期。没有默认密钥或跨用途授权。 |
| DEC-004 | 签名有效窗口上限由独立钉选的公钥有效窗口约束。时钟来自本机可信 UTC 系统时钟，一次采样，无历史时刻参数、容差或延期。 |
| OPEN-001 | 真实使用前确认独立 trust SHA 的分发和审阅责任，以及首个真实 key 的签发 owner 与允许范围。源码有限测试使用无私钥材料的合成公开向量，不依赖该真实 key。 |

## 6. Project Technology Context

PS 使用现有 JDK 25、Jackson 3、现有 exec 打包和 test 框架。验签复用 JDK KeyFactory、X509EncodedKeySpec、EdECPublicKey、Signature、MessageDigest、Base64 和 java.time。天枢仅是本地代码先例，保持模块依赖图。

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
| Rule 1 | Yes | PS release 包 | CLI/Verifier 和私有不可变 claims/result DTO | §8/10 | TEST-001 | PASS |
| Rule 2 | Yes | A1 严格 JSON 和 CLI 校验 | 保留封闭入站解析及独立绑定校验，无 Spring 跨层接口 | §9 | TEST-001/002/003 | PASS |
| Rule 3 | Yes | A1 私有不可变值 | 不可变 DTO 可用 record，显式投影固定输出 | §10 | TEST-001 | PASS |
| Rule 4 | No | 离线 PS CLI | 无 Spring bean 或业务日志 | §9 | TEST-004 | N/A |
| Rule 5 | Yes | EVD-001/002 | JDK 文件/密码学、现有 Jackson | §6–10 | TEST-001/004 | PASS |
| Rule 6 | Yes | A1 Jackson 3 | 严格 UTF-8，固定 JSON 文字量和规范编码 | §9/10 | TEST-001/002 | PASS |
| Rule 7 | No | 现有 exec launcher | 无 profile 或运行配置变化 | §16 | TEST-005 | N/A |
| Rule 9 | No | 单次离线验证 | 直接顺序验证，复用已存在的技术函数 | §13 | TEST-001 | N/A |
| Rule 10 | Yes | JDK 25 | java.time.Clock 和 Instant，UTC 半开区间 | §6.3/9 | TEST-003 | PASS |
| Rule 11 | Yes | 现有 PS release 技术边界 | 同包，无持久化、服务层或事件 | §7/8/11 | TEST-005 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

notBefore、notAfter、issuedAt、expiresAt 均为 UTC 整秒，固定 uuuu-MM-dd'T'HH:mm:ss'Z'。严格拒绝非法日期、闰秒文本、24:00:00、非规范年份、时区偏移和小数。使用 java.time.Instant 和 strict resolver，年限 0001–9999。系统读取完整精度的 now，比较不截断。测试只能从 package-local seam 注入固定 Clock。

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | trust.notBefore / trust.notAfter | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: instant in UTC | Not applicable: CLI text | Half-open trust window, strict UTC calendar | Seconds input, no rounding | A1 unchanged | TEST-003 |
| TIME-002 | claims.issuedAt / claims.expiresAt / result timestamps | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: instant in UTC | Not applicable: CLI text | Half-open claims window nested within trust | Seconds input, no rounding | New A2 contract | TEST-003 |
| TIME-003 | verification now | Occurred instant | java.time.Instant / Clock | Not persisted | Internal | Not applicable: system UTC clock | Not applicable: omitted from output | A single trusted clock sample with full precision | System UTC / fixed test Clock | New A2 contract | TEST-003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing/direct alternative | Concrete inadequacy of alternative | Added calls/state/coupling/failures/migration/operations | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Explicit CLI branch | Expand | REQ-004/005 | Existing PS main | A1 verify owns a different input shape | One explicit branch, no launcher change | Add |
| One package-private verifier | New | REQ-001–005 | Put verification inside main | Mixes existing A1 argv and output with a separate detached trust contract | One direct local call | Add |
| Existing A1 reader/hash/canonical | Expand | REQ-001/004 | A1 helper seams | Currently private to A1 | Same reader/encoding with package visibility only where needed | Keep |

| Path | Network calls | Client states | Server contracts/state | Failure and TOCTOU points | Additional user/business value |
| --- | --- | --- | --- | --- | --- |
| Direct baseline | 0 | A1 checked artifact | None | No detached trust binding | Existing A1 verification |
| Selected design | 0 | Detached result or fixed error | None | Single reads, pinned trust and candidate, exact claims | Independently reproducible offline signature verification |

### 7.1 System Architecture Design

操作者 → 现有 PS exec JAR 的 PlatformReleaseArtifactCli → A1 同一私有读取边界与包内 PlatformReleaseSignatureVerifier → stdout 或安全 stderr。进程无 Spring Boot 上下文、网络和外部进程调用。

### 7.2 High-Level Design

先校验 argv，安全读取并保留三个输入的原始字节。独立核对候选和信任钉选，校验精确 scope、时效和输入形状，再对规范 claims 运行 JDK Ed25519。只有全部通过才构造证据。

### 7.3 Detailed Design

复用一个 A1 root 实例的文件身份集合，确保三个角色互不重叠。每个输入的 digest、解析和验签在同一次读取的 byte array 上完成。A1 规范载体摘要和原始外壳 SHA 分别比较。用 System UTC 一次采样判定 key 和 claims 窗口。签名路径无写入或降级。测试 seam 用于输入、输出与固定 Clock，主入口始终使用系统时钟。

## 8. Package Structure and Code File Tree

| Change | Path under stellar-enclosure-tianji-process-server | Responsibility |
| --- | --- | --- |
| M | src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java | 只分派显式 verify-signature，A1 verify 子命令的调用保留 |
| M | src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java | 为同包 verifier 暴露最小的安全读取、严格 JSON 和摘要/规范编码 seam，复用原实现 |
| C | src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java | Closed claims/trust、候选绑定、Ed25519、结果 |
| C | src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java | 有限端到端 CLI 与公开向量测试，必要向量直接随测试类保存 |

最终测试证据可沿现有 docs/egon/evidence 落盘。Plan 锁文件树和真实 exec JAR 证据。无 POM、PaC、HTTP、数据库或其他仓库改动。

## 9. Interface Definitions

### 9.1 Interface Inventory

| Interface ID | Type | Operation | Source |
| --- | --- | --- | --- |
| CLI-001 | Local read-only query | PlatformReleaseArtifactCli verify-signature | REQ-001–005 |

### 9.2 Per-interface Detailed Contracts

#### 9.2.1 CLI-001 — Verify one detached release candidate signature

##### Necessity and interaction-cost decision

| Concern | Decision |
| --- | --- |
| Change classification | 显式离线 Query |
| Independent consumer goal | 操作者凭审阅过的候选和独立 trust 钉选可重现验签 |
| Parameter ownership and derivation | 操作者选择 scope 与两个独立 digest，文件提供公开候选、签名及 trust 数据 |
| Direct/no-new-interface alternative | 现有 A1 verify 只生成候选，无法表达固定 trust、签名和当前有效窗口 |
| Caller use of result | 保留独立审阅证据，并识别仍待核验的发布权威 |
| Round trips and failure points | 单次进程、三次有界本地读取、零网络、固定失败 |
| Verdict | 使用同一 main 增加单个有界离线子命令 |

##### Identity and purpose

现有 PS PropertiesLauncher 显式 main 下的只读子命令，以当前本地 owner 的文件权限执行。

    java -Dloader.main=top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli \
      -cp "$PS_EXEC_JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
      verify-signature --root "$PRIVATE_ROOT" --candidate candidate.json \
      --signature candidate.sig.json --trust release-trust.json \
      --expect-candidate-sha256 "$EXPECTED_CANDIDATE_SHA" --expect-trust-sha256 "$INDEPENDENT_TRUST_SHA" \
      --tenant 20 --channel expense

##### Request parameters

子命令后的八个选项全部必传且各出现一次。未知选项、重复选项、缺值和额外位置参数失败。tenant 是规范正 signed long 文本，channel 沿用 A1 的 [a-z][a-z0-9._-]{0,63}，两项期望 SHA 是 64 位小写十六进制。root 沿用 A1 当前 owner 的规范绝对 POSIX 路径，各段禁止 symlink，root 的 group/other 权限全空。三个文件沿用 A1 当前 owner、无 group/other 写权限、路径段、普通文件、owner 可读、前后元数据一致与 NOFOLLOW_LINKS 防护。路径和 fileKey 三者必须分别不同，平台无法提供文件身份时返回 IO_UNAVAILABLE。候选上限 1 MiB，sidecar 和 trust 各 8 KiB。零长度拒绝。三个文档严格 UTF-8，无 BOM、重复键、未知字段、附加根值或标量强制转换。

###### Candidate

外壳精确字段 candidateSha256 和 candidate。候选遵循 A1 §9/§10 的封闭输出形状，schemaVersion 为数字 1，status 固定 ARTIFACT_CHECKED_ONLY，pendingAuthority 保持 A1 固定数组。tenantId 与 channel 同 argv。sourceRevision 为 40 位小写十六进制。使用 A1 域和 canonicalJson 独立重算 candidateSha256，与外壳、独立 argv 钉选、claims 三者全匹配。候选原始字节必须恰为 A1 canonicalJson(envelope) 及一个 LF。原始 bytes 的 SHA-256 必须匹配 claims.candidateEnvelopeSha256。原 A1 输出不被重写。

###### Pinned trust and detached sidecar

| File/member | Exact field set | Values and comparison |
| --- | --- | --- |
| trust root | schemaVersion, purpose, algorithm, role, tenantId, channel, keyId, publicKey, notBefore, notAfter | schemaVersion 数字 1。purpose 固定 RELEASE_CANDIDATE_ARTIFACT，algorithm 固定 Ed25519，role 固定 RELEASE_ARTIFACT_SIGNER。tenantId、channel 与 argv 精确相等。 |
| sidecar root | claims, signature | claims 是下表对象，signature 为 canonical RFC4648 standard Base64，以 padding 恢复的 64 字节原始 Ed25519 签名。 |
| claims | schemaVersion, purpose, algorithm, role, tenantId, channel, keyId, candidateSha256, candidateEnvelopeSha256, issuedAt, expiresAt | 前七项同 trust 精确相等，tenantId/channel 同候选与 argv。其余值按摘要和时间合同校验。 |

trust SHA 是整个文件原始 bytes 的 SHA-256，在消费信任内容前与独立期望值比较。相同语义但不同的 trust bytes 要求新的独立钉选。签名 JSON 的成员顺序和合法空白可不同，签名字节以规范 claims 为准。

publicKey 是 canonical padded standard Base64 的 44 字节 X.509 SubjectPublicKeyInfo DER。DER 以 12 字节 302a300506032b6570032100 开始，随后是 32 字节公钥。keyId 为 64 位小写十六进制 SHA-256(SPKI DER)，由 verifier 重算。KeyFactory Ed25519 解码后必须为 EdECPublicKey，参数名 Ed25519，getEncoded 与原 DER 字节相等。公钥 SPKI 的算法参数缺省。拒绝阶整除 8 的公钥点（包括恒等元），不能把不需要私钥的低阶点验证视为签发证据；保留 JDK 验证和独立公钥钉选。参见 [RFC 8032 §5.1.7](https://www.rfc-editor.org/rfc/rfc8032.html#section-5.1.7) 与 [libsodium 验签的低阶拒绝](https://github.com/jedisct1/libsodium/blob/master/src/libsodium/crypto_sign/ed25519/ref10/open.c)。Base64 解码后须等长且标准编码器重编码与原文本逐字符相等。签名、公钥、签名方记录与外部 URI 均无自动发现。参见 [RFC 8410 §3–4](https://www.rfc-editor.org/rfc/rfc8410.html#section-4)、[RFC 8032 §7](https://www.rfc-editor.org/rfc/rfc8032.html#section-7) 和 [JDK 25 X509EncodedKeySpec](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html)。

以单次系统时刻 now 检验 notBefore ≤ issuedAt ≤ now < expiresAt ≤ notAfter。两个区间各自严格正长度。独立 pin 的公钥窗口给出最大边界。签发时刻是签名自报的值，由受信公钥和本地有效窗口约束，独立时间戳和历史撤销事实留给治理阶段。

###### Bytes to verify

    UTF8("TIANJI_PLATFORM_RELEASE_DETACHED_SIGNATURE_V1") || 0x00 || A1CanonicalJson(claims)

A1 canonicalJson 编码规则来自 A1 §10。上述消息无尾 LF。以 JDK Signature.getInstance("Ed25519") 的 initVerify、update、verify 直接验证原始消息，避免 Ed25519ph 和二次预哈希。上述域与 A1 候选域和未来 owner/activation 证据分离。

##### Success response

成功退出 0。stdout 是 A1 规范 JSON 字节加一个 LF，精确字段 schemaVersion、status、candidateSha256、candidateEnvelopeSha256、sourceRevision、tenantId、channel、keyId、role、purpose、trustSha256、issuedAt、expiresAt、pendingAuthority。schemaVersion=1，status=ARTIFACT_SIGNATURE_VERIFIED_OFFLINE，pendingAuthority 是固定数组 [CURRENT_SIGNER_AUTHORITY, OWNER_PUBLICATION, LIVE_REVOCATION, REVIEW_AND_ACTIVATION]。同一输入在同一有效窗口输出完全相同。该行描述离线判定，后续阅读不延长有效期。

##### Error responses

| Exit | Stderr exactly one fixed line | Condition |
| --- | --- | --- |
| 64 | TIANJI_RELEASE_SIGNATURE_USAGE | 子命令 argv 形状、tenant/channel 或钉选文本无效 |
| 65 | TIANJI_RELEASE_SIGNATURE_INVALID | 文档、封闭字段、规范候选、编码或长度无效 |
| 65 | TIANJI_RELEASE_SIGNATURE_DIGEST_MISMATCH | 候选语义摘要、独立候选钉选或候选外壳摘要不同 |
| 65 | TIANJI_RELEASE_SIGNATURE_TRUST_REJECTED | 独立 trust SHA、角色/用途/scope、keyId/SPKI 或时效不匹配 |
| 65 | TIANJI_RELEASE_SIGNATURE_MISMATCH | 合法编码的 Ed25519 签名验证失败 |
| 66 | TIANJI_RELEASE_SIGNATURE_IO_UNAVAILABLE | I/O 错误、平台无法读取所需元数据或算法不可用、输出失败 |

多个输入问题取验证顺序遇到的第一个固定诊断。argv → 安全读文件 → trust 钉选 → 候选和签名严格格式与候选 digest → trust 解析、用途/范围/公钥/时间 → 签名数学验证 → 输出。不合规路径、权限或重复文件身份属于 INVALID。已解码输入违反 DER/Ed25519 要求属于 TRUST_REJECTED，JDK 算法服务缺失属于 IO_UNAVAILABLE。默认失败 stdout 为空。底层 stdout 写错误时可能产生未完成片段，消费者须同时检查退出 0 和一整行 JSON。stderr 永不包含文件路径、用户输入、密钥、公钥字节、签名字节或异常堆栈。

##### Interface logic for frontend and consumers

操作者先经独立可信渠道核对 trust SHA，明确选择候选与 tenant/channel，再把三个有界文件放到私有本地目录。读者同时检查退出 0 和完整规范行。若读取既有输出，时间敏感的使用应以同一独立钉选重跑 CLI 判断当前有效窗口。

##### Compatibility and verification

现有 verify 参数、A1 摘要和结果原样保留。TEST-001–005 覆盖兼容、封闭输入、准确时间和真实 exec JAR 上的显式 main。工具结果只对应一次本地验签和已选 trust 描述。

## 10. POJO and Data Model Design

仅存在闭合的 JSON 输入和一个不可变离线结果。私有不可变 ReleaseSignatureClaimsDTO、ReleaseSignatureTrustDTO 和 ReleaseSignatureResultDTO 可用 record。复用现有 Jackson 树边界和 A1 canonical JSON，在验证完成后显式投影结果。密钥字节只用于一次计算。candidateEnvelopeSha256 绑定 A1 原始外壳全量字节，candidateSha256 保留 A1 原有含义，trustSha256 绑定独立审阅的描述原始字节。所有 digest 和 keyId 均用小写 hex。

## 11. Database Design

不涉及数据库、ORM、迁移或写入。证据生命周期由离线操作者处理。

## 12. Frontend Page Design

不涉及页面、浏览器或对外接口。

## 13. Design Patterns and Architecture Principles

选用直接组合现有 A1 文件和规范编码实现，单独 verifier 封装 Ed25519 与发布语义。一个算法、一种用途和一条读路径，Strategy、Factory 或新服务层没有当前变化点。

## 14. Test Design

| Test ID | Finite or explicit gate | Expected observation |
| --- | --- | --- |
| TEST-001 | PlatformReleaseSignatureVerifierTest#verifiesPinnedDetachedSignature | 实际 A1 规范候选、独立制备的正向 Ed25519 公开向量及固定 Clock 产生逐字节精确结果。重排 claims JSON 合法空白仍同结果。测试只保存公开材料。 |
| TEST-002 | #rejectsSubstitutedCandidateTrustAndSignature | 对候选内容、外壳 SHA、重新计算 A1 摘要、换一份有效签名、候选钉选、签名翻位、替换 key/trust 及独立 trust 原始字节变化做失败断言。篡改的 tenant/channel/keyId/role/purpose 各有独立反例。 |
| TEST-003 | #enforcesExactValidityWindow | Clock 精确测试 notBefore/issuedAt 同时刻，过早，expiresAt−1ns，恰到 expiresAt，key notAfter，expiresAt 超出 key 窗口，以及非规范时间文本。 |
| TEST-004 | #rejectsUnsafeFilesAndMalformedInputs | 未知/重复 argv、三类路径别名、symlink、文件具备 group/other 写权限、越界、超限、坏 UTF-8/JSON、Base64 padding/长度、SPKI 编码错误与独立钉选的低阶公钥伪造向量。失败 stdout 空且 stderr 固定。 |
| TEST-005 | Existing PlatformReleaseArtifactCandidateTest and explicit packaged CLI proof | 全部 A1 旧测试通过。既有 PS exec JAR 通过显式 PropertiesLauncher 对已知有效公开向量成功，错误签名退出 65。Boot Start-Class 和归档结构维持，应用和数据库未启动。 |

有限验证可用当前 reactor 显式选择 PS 的 PlatformReleaseSignatureVerifierTest 和 PlatformReleaseArtifactCandidateTest，Plan 锁 exact command、JDK 和产物路径。真实发布 signer 的外部验签是独立证据。

## 15. Non-functional and Cross-cutting Design

安全模型信任本机 owner、当前系统时间和独立 pin 渠道。目录及文件权限、防 symlink、大小界限、strict JSON 和 digest 都复用同一边界。无需 secret、私钥、JWT authority、JWK 或证书信任链。三个小文件一次读取，无缓存、重试、异步、目录扫描、进程和网络。证明单行固定字段，输出前完成完整验证。

## 16. Compatibility, Migration, Rollout, and Rollback

A2 以显式子命令加入已有 PS exec JAR，A1 verify argv、ARTIFACT_CHECKED_ONLY、hash、输出字节和 Boot Start-Class 保留。初次部署无需数据迁移或 runtime 参数。停止调用新子命令即可撤回离线使用，既有候选继续可供 A1 流程读取。期满的签名依既有外部签发决定另行取得。

## 17. Alternatives and Decisions

| Choice | Disposition | Reason |
| --- | --- | --- |
| 一个独立 pin 的单用途 Ed25519 trust descriptor | Selected | 文件、角色、租户、渠道和时效由同一独立 SHA 绑定 |
| 接受 sidecar 自带的密钥作为 trust | Rejected | 签名提供方可同时替换公钥、声明和签名 |
| 从已有 IAM JWT/JWKS 推断发布权威 | Rejected | 签发目的、授信方、可用性和撤销语义不同 |
| 发布 registry、签发接口、CAS 一并交付 | Deferred | 需要 owner 权威、运行时证据和独立审批 |

## 18. Risks and Open Questions

| ID | Scope | Risk or decision | Required action |
| --- | --- | --- | --- |
| OPEN-001 | Live use | 独立信任渠道和实际发布 signer 尚未指定 | 发布决策方确认职责和真实 key 引入方式。源码验证采用合成公开向量。 |
| RISK-001 | Evidence freshness | 离线时钟和 trust pin 可能过时，已取出的成功结果无法证明当前权限 | 当前操作者重新验证，live 撤销和 owner 流程提供后续实时事实。 |

## 19. Traceability Matrix

| Requirement | Use case/interface | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001/CLI-001 | §7.3/§9.2 Candidate | TEST-001/002/005 |
| REQ-002 | UC-001/CLI-001 | §5/§9.2 trust | TEST-001/002/004 |
| REQ-003 | UC-001/CLI-001 | §6.3/§9.2 crypto | TEST-001/002/003 |
| REQ-004 | UC-001/CLI-001 | §7.3/§9.2 errors | TEST-004/005 |
| REQ-005 | UC-001/CLI-001 | §1/§9.2 success/§16 | TEST-001/005 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | 现有 PS 技术入口 | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001/002 | 复用 A1 读取与编码，采用已知 JDK 验签链 | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | 既有 JDK 和 Jackson | None |
| MC-NAME-001 | Applicable | PASS | §8/10 | 同包 verifier 和私有不可变 DTO | None |
| MC-VALID-001 | Applicable | PASS | §9/14 | 闭合输入、独立 pin、严格失败 | None |
| MC-MODEL-001 | Applicable | PASS | §10 | 封闭输入和验证结果 | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | 同包显式固定 JSON 投影 | None |
| MC-LOG-001 | Not applicable | N/A | §9 | 固定 stderr，无运行日志 | None |
| MC-BEAN-001 | Not applicable | N/A | §7 | 无 Spring bean | None |
| MC-UTIL-001 | Applicable | PASS | §6/9 | JDK 密码学和现有 A1 规范规则 | None |
| MC-JSON-001 | Applicable | PASS | §9/10 | 候选原始字节、规范 claims 与 trust 独立 pin | None |
| MC-TIME-001 | Applicable | PASS | §5/6.3/18 | UTC 和半开边界明确，窗口嵌套 | None |
| MC-CONFIG-001 | Not applicable | N/A | §8/16 | 无应用配置 | None |
| MC-PATTERN-001 | Not applicable | N/A | §13 | 固定直接验证 | None |
| MC-SCOPE-001 | Applicable | PASS | §3/5/8 | 发布与审计治理边界明确 | None |
| MC-TEST-001 | Applicable | PASS | §14 | finite selectors 与 packaged CLI gate | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/18 | 源码边界可审阅，真实使用依赖独立信任交接 | Confirm OPEN-001 before real trust use |

### 20.6 Final verdict

**PASS — Ready for user review**

供 owner 审阅。真实使用需要独立信任渠道和签发责任落地。此候选仅完成静态证据收集和接口设计。
