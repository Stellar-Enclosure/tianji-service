# T4-01A2 天机离线分离签名验证实现计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-17-44-platform-release-detached-signature.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 17:44 Asia/Shanghai |
| Updated | 2026-11-05 17:44 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 一个 PS release 源码 Step，四个 Java 路径，当前候选与信任钉选的离线验证 |
| Source Requirement | 主计划 §19 T4-01，分离签名验证首切片 |
| Baseline Revision | tianji-service main 17fee280792e95da938c68ba65a783b965ca63d8 |
| Implements Spec | [A2 离线分离签名验证](../spec/2026-11-05-17-30-platform-release-detached-signature.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 17:38 Asia/Shanghai SHA-256 43b785c8eb9cfcbe2426041f08c6b680b14f688dfe332325d26a911f62d1c8f5 |
| Effective Specs | [A2](../spec/2026-11-05-17-30-platform-release-detached-signature.md), [A1 §9/§10](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) |
| Depends On Plans | [A1 实现](2026-11-05-16-33-platform-release-artifact-candidate.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Root has locked this exact offline source slice under the user's master Plan. Test vectors use only ephemeral synthetic keys in memory and retained public data. This approves no actual signer, private-key storage, publication, deployment, authority grant or runtime activation.

通过现有 PS exec JAR 增加显式 verify-signature。一个源码 Step 完成 A1 字节绑定、独立公开信任钉选、Ed25519 和 UTC 窗口验证。有限 RED/GREEN 与同一生产入口的真实 exec JAR 调用合并验收。已有 verify 命令和 A1 候选合同保持原样。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[A2 Spec](../spec/2026-11-05-17-30-platform-release-detached-signature.md) 状态 Review，2026-11-05 17:38 Asia/Shanghai，SHA-256 见头部。授权仅涵盖合成向量、显式 pin 和离线验证的 Plan 编制。执行之前锁定审批后 Spec/Plan 文档及提交基线。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [A2](../spec/2026-11-05-17-30-platform-release-detached-signature.md) | Review, pinned above | All | 规定新输入、结果及失败 |
| Dependency | [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) | 已按 2f10711b152bbce7000ba7fb31d6f931e4f52927 交付 | §9/§10 | 已提交的候选形状、域摘要、规范编码和现有入口 |

### 2.3 Superseded or excluded content

无替代 Spec。真实签发、公钥部署、在线撤销、owner 发布、审批、CAS、数据、T3-12 E 和运行时验收各有独立阶段。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | A2 §4/§9.2 and A1 §9/§10 | 绑定 exact A1 候选 | 规范外壳、候选域 SHA、独立期望 SHA 和外壳原始 SHA 全匹配 | A1 reader/digest 与 verifier |
| REQ-002 | A2 §4/§5/§9.2 | 独立固定唯一公开信任 | raw trust SHA 和 SPKI、keyId、用途、角色及 scope 绑定 | verifier 与 CLI 参数 |
| REQ-003 | A2 §4/§6.3/§9.2 | 真实 Ed25519 和有效窗口 | 合成正向向量与完整边界，错误签名拒绝 | verifier 与固定 Clock 测试 |
| REQ-004 | A2 §4/§9.2 | 有界、安全读取及固定错误 | 私有目录、严格文档、固定 exit/stderr、失败 stdout 空 | A1 file guard、CLI adapter |
| REQ-005 | A2 §4/§9.2/§16 | 独立离线证据与发布权威 | 单行准确状态和 pendingAuthority，A1 与 Boot 不变 | CLI/有限证明 |
| REQ-006 (A1 compatibility) | A1 §4/§9/§16 | 已有 A1 不产生真实签发或 owner 证据 | A1 原状态与错误保留，A2 只产生离线证明，T3-12 E 独立 | Existing A1 regression and P1 evidence |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Test → A1 package-local shared seams → A2 verifier → A1 CLI explicit branch → focused GREEN → rebuild and direct packaged invocation → record evidence and commit. A1 校验链只抽取同字节的候选域摘要和最小读取/编码 seam。

### 4.2 Test-first strategy

| Gate | Before behavior | Expected failure | GREEN |
| --- | --- | --- | --- |
| R1 | 仅增加 JUnit positive 方法，使用已有三参数 CLI run | 新子命令在现有 parse 走 64，expected 0 | 最后 CLI wiring 后精确返回独立 golden |
| R2 | 增加剩余测试，暂通过反射访问计划内四参数 Clock seam | 如 seam 不存在，R2 只选择使用三参数 run 的错误签名断言，预期 65/实际 64 | Clock seam 落地后改为直接 typed call，全集通过 |
| G1 | 文件 2–4 完成后 | 任何 unexpected fail、skip、未执行或 A1 回归均退回 owning file | A2 4 方法和 A1 6 方法全部实际执行 |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | Committed A1 and PS baseline | 独立只读检查 | 同一 checkout 中的 AS/PS 打包、同一 release path 写入 | 一个 source reactor 含多 owner，fresh test/ZIP 证据要可归属 |

### 4.4 Commit boundaries

只提交一个 path-limited 服务 commit，精确七路径。owner 在执行前锁定 scratch Spec/Plan bytes，最终原样归档到仓库相对路径。无中间提交。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| CLI branch | A2 §7.0 Add | PlatformReleaseArtifactCli#main/run/parse | Same PS main | 一次本地进程，0 网络 | Extend explicit command dispatch |
| Separate verifier | A2 §7.0 Add | Existing Validator is artifact-focused | Existing release package | 一个同包调用，无新服务 | Keep |
| Shared A1 reader/encoding | A2 §7.0 Keep | Validator#open/read/parse/canonical/candidate digest | 同一已有实现 | 无重复文件访问算法 | Expose narrow seams |

### 4.6 Change-unit Dependency Matrix

| Unit | Depends on | Produces | Consumed by |
| --- | --- | --- | --- |
| A2 tests | A1 CLI baseline, synthetic public fixture | R1/R2 behavior contract | One Step |
| A1 seams | Existing validator internals | File reads, strict tree, canonical bytes and domain digest | A2 verifier |
| A2 verifier | A1 seams, JDK Ed25519/UTC | Bytes or fixed failure | A1 CLI |
| Dispatch and proof | Verifier, existing main, source reactor | Existing exec JAR and safe proof | Offline operator |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

使用 PS 现有 release 包，私有不可变 DTO，Jackson 3 的现有封闭 JSON 边界，JDK 25 安全和文件 API。无 Spring bean、业务日志、Web/API、持久化、Outbox 或应用配置。既有目标 reactor 与权限生产者 profile 仅用于有限测试和打包验证。

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Existing PS release technical package | A2 §7/§8 | Existing owner and launcher | Step 1, MC-ARCH-001 |
| Reuse/capability | A1 Validator parser/digest and JDK 25 | A2 §6/§7 | No dependency or module addition | Step 1, MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | A1 CLI Options and immutable package values | A2 §8/§10 | DTO suffix on new immutable values, closed validation, direct technical projection | Step 1, MC-NAME-001/MC-MODEL-001/MC-VALID-001 |
| Bean/logging/util/JSON/time/config | Existing CLI, mapper and JDK | A2 §6/§9/§16 | No bean, log or config, one strict mapper and UTC clock | Step 1, MC-UTIL-001/MC-JSON-001/MC-TIME-001 |
| Business variation/pattern | One fixed algorithm and purpose | A2 §13 | Direct same-package composition | Step 1, MC-PATTERN-001 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| CLI and archive | A1 PS exec | PlatformReleaseArtifactCli and PS exec classifier | Existing main and PropertiesLauncher fit | Reuse | One branch only | Step 1, MC-REUSE-001 |
| File/JSON/digest | A1 Validator | PlatformReleaseArtifactValidator#open/read/parse/canonical | Same guard, digest domain and ordering | Reuse | Narrow package seam only | Step 1, MC-REUSE-001 |
| Crypto and time | JDK 25, Tianshu precedent | DdcLegacyImportService#verifyMappingSignature and java.time | Existing JDK features suffice | Reuse technique | None | Step 1, MC-DEP-001/MC-TIME-001 |
| Independent trust | A2 Spec and existing JWT uses | A2 §5/§9.2 | Scope-specific raw pin | Use explicit local input | No IAM/JWT coupling | Step 1, MC-VALID-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

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

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | A2 §6/§8/§10 | PS release package | A2 Verifier/immutable DTO | Semantic names | G1 | Step 1 | PASS |
| Rule 2 | A2 §6/§9 | A1 parse/shape/field | Validator, Verifier and CLI | Strict technical input validation | R1/R2/G1 | Step 1 | PASS |
| Rule 3 | A2 §6/§10 | Existing private immutable records | Claims/trust/result DTO | record only for immutable value | G1 | Step 1 | PASS |
| Rule 4 | A2 §6/§7 | Existing non-Spring main | CLI/verifier | No bean or business log | G1/P1 | Step 1 | N/A |
| Rule 5 | A2 §6 | JDK/Jackson present | All Java paths | Existing tools only | G1/P1 | Step 1 | PASS |
| Rule 6 | A2 §9/§10 | Existing canonical serializer | A1 Validator and A2 verifier | Exact UTF-8, no ordinal/coercion | G1 | Step 1 | PASS |
| Rule 7 | A2 §16 | Existing application config | None | No profile/config mutation | S1 | Step 1 | N/A |
| Rule 9 | A2 §7/§13 | One fixed deterministic flow | A2 verifier | Direct composition | G1 | Step 1 | N/A |
| Rule 10 | A2 §6.3 | JDK 25 | Verifier, CLI Clock seam, JUnit | One sample, exact UTC window | G1 TEST-003 | Step 1 | PASS |
| Rule 11 | A2 §8/§11 | PS package/module | All Step paths | One owner and no new layer | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | trust.notBefore / notAfter | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: CLI only | Strict calendar and half-open trust window | Seconds input, no rounding | A1 unchanged | Step 1 G1 TEST-003 |
| TIME-002 | claims.issuedAt / expiresAt and proof | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: CLI only | Positive half-open claims window contained by trust | Seconds input, no rounding | New A2 | Step 1 G1 TEST-003 |
| TIME-003 | verification now | Occurred instant | java.time.Clock / Instant | Not persisted | Internal | Not applicable: UTC system clock | Not applicable: omitted | Exactly once, full precision | Clock.systemUTC / fixed tests | New A2 | Step 1 G1 TEST-003 |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java | Existing A1 finite test package | TEST-001–004 | Public fixture and finite gates | Step 1 | REQ-001–005 | R1/R2/G1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java | #open/read/parse/canonical/#verifyCandidate | Narrow reuse seams | Same A1 bytes and guard | Step 1 | REQ-001/004 | G1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java | Existing release package | #verify and closed DTO | Scoped signature verification | Step 1 | REQ-001–005 | G1/P1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java | #main/run/parse | Exact verify-signature branch | CLI/Clock and fixed diagnostics | Step 1 | REQ-004/005 | G1/P1 |
| CREATE | docs/egon/evidence/2026-11-05-platform-release-detached-signature.md | Committed A1 evidence | Actual finite and packaged proof | Evidence closure | Step 1 | REQ-001–005 | P1/S1 |

| CREATE | docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md | Exact reviewed Spec | Locked governing design | Review provenance | Step 1 | REQ-001–005 | S1 |
| CREATE | docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md | Exact reviewed Plan | Locked governing implementation | Review provenance | Step 1 | REQ-001–005 | S1 |

The exact reviewed governing documents are supplied by the owner and staged in the same logical commit.

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

在服务仓库 main 提交 17fee280792e95da938c68ba65a783b965ca63d8 重新观察 clean tree。该提交包含 T4-02 写入和 T4-03 AS diagnostics。git diff --name-only 2f10711..HEAD -- stellar-enclosure-tianji-process-server 为空。AS unrelated local paths 曾存在，已由 T4-03 自有提交接收。执行前重新核对 HEAD、worktree 和 index，保留后续用户工作，排除非 Step 路径。有效指令来自用户 AGENTS.md 指令及本技能，目标模块没有更近 AGENTS.md。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| Toolchain | /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home and scripts/verify-source-reactor.sh | Local JDK 25, existing offline settings | Finite and archive only |
| Sibling source | Components 87af09fea9a9d58e7127b009d76f60438c79e569, Shoubing e423428e01591c122551f0c11aca8a4ffda9b4c1, Jianshen 3afec38631ff16f44018a297c1267d48fc04a508, Tianshu b1c607f4d8a03855be8179d73849ebfb4488468b | Capture current clean locked revisions before execution | Build provenance |
| A1 candidate | Existing A1 Plan package proof with exact PS main verify | Frozen authentic A1 canonical bytes, recompute semantic/envelope hashes independently | Public synthetic signing vector |
| Package | Current PS exec classifier plus explicit permission-producer | Fresh matched source and artifact lock | Direct offline child, bounded timeout |

### 6.3 Immutable constraints and approved decisions

显式 pin、合成离线使用，零新依赖。A1 候选、POM、YAML、权限生产、Boot Start-Class、数据库和既有发布路径保持现状。运行时启动、部署和真实 key 不属于授权。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | A1 package-private helpers keep A1 guarded file state in the same instance and share exact digest/canonical implementation | Validator#open/read/parse/canonical/#verifyCandidate | A2 adds a required fileKey check only in its wrapper | A1 golden or file regressions block |
| PLAN-CLAR-002 | Fixed public fixture constants in the sole new test and disposable package proof stage | A2 §8/§14, A1 fixture convention | No repository fixture file, new dependency or signing path | Return to vector provenance gate |
| PLAN-CLAR-003 | One service evidence file records actual gates | A2 §8, A1 evidence practice | Documentation only | Missing evidence blocks Step close |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付离线分离签名验证

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006 (A1 compatibility).
- Dependencies: Approved and pinned A2 Spec/Plan, committed A1, current PS baseline, current source reactor, one synthetic public vector.
- Baseline state: 现有 verify 和 ARTIFACT_CHECKED_ONLY 已交付，verify-signature 目前返回 A1 usage 64。
- Observable outcome: Exact verification produces one ARTIFACT_SIGNATURE_VERIFIED_OFFLINE line, invalid pins, scope, time or signature return fixed safe diagnostics.
- End state: 四个 Java 文件、实际证据与两份审阅文档，单一七路径服务 commit。
- Test-first gate: Required. R1/R2 on existing CLI call, then G1 on typed time seam, exact commands in §8.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java`

- Purpose: Freeze the detached-signature behavior before production edits.
- Symbols: verifiesPinnedDetachedSignature, rejectsSubstitutedCandidateTrustAndSignature, enforcesExactValidityWindow, rejectsUnsafeFilesAndMalformedInputs.
- Repository evidence: PlatformReleaseArtifactCandidateTest uses exact CLI run, JUnit and @TempDir POSIX fixtures.
- Dependencies and consumers: Existing three-argument CLI run, package-local four-argument Clock run after File 4, independent public A2 vector, existing JUnit/JDK/Jackson.
- Why now: R1 and R2 exercise the same already-compilable CLI seam before any A2 branch.
- Contract/signature changes: Test-only immutable fixtures, no resource classpath additions. Use actual A1 output, frozen claims, raw trust bytes, public SPKI, signature and an independent expected result.
- Input/output and state mapping: Temp root 0700, three distinct files 0600. Explicit candidate/trust SHA constants are fixture identities. For two verdicts derive exact stdout/stderr expected bytes independently, including LF.
- Error and edge behavior: Separate re-signed valid wrong-key vector from bit-flip; mutate candidate content and recompute its A1 digest and independent candidate pin to demonstrate signature remains bound to original. Re-pin mutated trust only when checking its inner fields. Assert future, earliest equality, expiry minus 1 ns, exact expiry, nested bounds, key expiry, invalid dates/time offsets, unsafe path identity/symlink/modes, limits/UTF-8/trailing/duplicate/scalars, DER and Base64. Confirm each intended first error and stdout empty.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-TEST-001. No Spring context, external credentials or key material.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Private value fixtures, JDK timing, same release package.
- Implementation pseudocode:

```java
@Test void verifiesPinnedDetachedSignature() {
    var input = stagePublicVector(privateRoot, FROZEN_A1_BYTES, SIDECAR, TRUST);
    var outcome = runExistingThreeArgumentCli(input, INDEPENDENT_CANDIDATE_SHA, INDEPENDENT_TRUST_SHA);
    assertEquals(0, outcome.status());  // R1 observes existing 64.
    assertArrayEquals(EXACT_GOLDEN_PROOF, outcome.stdout());
    assertEquals("", outcome.stderr());
    assertArrayEquals(EXACT_GOLDEN_PROOF, rerunWithSidecarReformatted(input).stdout());
}
@Test void rejectsSubstitutedCandidateTrustAndSignature() {
    assertRefused(runExistingThreeArgumentCli(publicVectorWithValidBase64FlippedSignature()),
            65, "TIANJI_RELEASE_SIGNATURE_MISMATCH");  // R2 observes existing 64.
    assertEachIsolatedDigestOrScopeMutationReturnsItsExactCode();
}
@Test void enforcesExactValidityWindow() {
    assertAt(issuedAt, 0);
    assertAt(issuedAt.minusNanos(1), 65, TRUST_REJECTED);
    assertAt(expiresAt.minusNanos(1), 0);
    assertAt(expiresAt, 65, TRUST_REJECTED);
    assertMalformedAndOutOfTrustWindow();
}
@Test void rejectsUnsafeFilesAndMalformedInputs() {
    assertUsageErrors();
    assertPosixPathAndIdentityErrors();
    assertStrictJsonAndBinaryEncodingErrors();
}
```

- Verification contribution: R1/R2 expected assertion failures, G1 four executed methods. File 4 removes any temporary reflective Clock adapter and uses direct typed overload.
- After this file: R1/R2 compile and fail for existing exit 64. No production modification.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java`

- Purpose: Reuse file/JSON/hash/canonical behavior in the same package.
- Symbols: Constructor, readSignatureInput(String,int), parse(byte[]), shape(JsonNode,String...), field(JsonNode,String), integer(JsonNode,String), hash(byte[]), canonical(Object), candidateSha256(Object).
- Repository evidence: Existing private strict mapper/guard and candidate domain hash in verifyCandidate. Existing canonicalValue accepts only Map/List/String/Integer/Long. Existing ValidationFailure carries fixed status and string.
- Dependencies and consumers: A1 same constructor/verify path, A2 one Validator instance, Jackson tree and JDK digest.
- Why now: Provide compilable existing owner seams before new verifier.
- Contract/signature changes: Expose only same-package helpers above. A2 wrapper uses existing open/read, requires non-null fileKey, and returns bounded byte array. Extract exact candidateSha256 algorithm and call from old verifyCandidate.
- Input/output and state mapping: Existing reader captures path and fileKey sets once per Validator instance. Exact canonical candidate map and original A1 output remain unchanged.
- Error and edge behavior: Existing A1 guards and A1 codes preserved. A2 maps ValidationFailure status 65 to its INVALID and status 66 to its IO_UNAVAILABLE. Non-null identity requirement applied only via readSignatureInput.
- Standards impact: MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001. No new parser, API or utility dependency.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 11. Same package and same technical validation.
- Implementation pseudocode:

```java
PlatformReleaseArtifactValidator(Path root) throws IOException { /* existing body */ }
byte[] readSignatureInput(String relative, int limit) throws IOException {
    StagedFile file = open(relative, limit);
    if (file.attributes().fileKey() == null) throw unavailable();
    return read(file);
}
static String candidateSha256(Object candidate) {
    MessageDigest digest = sha256();
    digest.update(DOMAIN.getBytes(UTF_8));
    digest.update((byte) 0);
    return HexFormat.of().formatHex(digest.digest(canonical(candidate).getBytes(UTF_8)));
}
// verifyCandidate calls candidateSha256 and preserves compare and serialization.
```

- Verification contribution: Run G0 A1 finite unchanged before file 3 if shared implementation changes behavior.
- After this file: A1 fully compiles and retains golden output. A2 R1/R2 still fail for missing command.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`

- Purpose: Validate one A1 candidate and one detached signature against one independently pinned trust descriptor.
- Symbols: package-private PlatformReleaseSignatureVerifier#verify(Path,String,String,String,String,String,long,String,Clock), private immutable ReleaseSignatureClaimsDTO, ReleaseSignatureTrustDTO, ReleaseSignatureResultDTO, and safe package-local signature failure carrying exitCode/code.
- Repository evidence: Current Validator semantic package, JDK 25, existing Tianshu DdcLegacyImportService#verifyMappingSignature using KeyFactory/X509EncodedKeySpec/Signature.
- Dependencies and consumers: File 2 same-package seams, File 4 CLI, JDK EdECPublicKey/Base64/HexFormat/java.time. No Tianshu Java dependency.
- Why now: New verifier compiles against already-existing validated seams.
- Contract/signature changes: Arguments in order root, candidateRelative, signatureRelative, trustRelative, expectedCandidateSha256, expectedTrustSha256, tenant, channel, clock. Returns finished canonical proof bytes or typed fixed failure. Keep all payload value types private and immutable, no array-bearing public DTO.
- Input/output and state mapping: A2 Spec §9.2 exact trust, sidecar, claims and result. Candidate nested shapes inherit A1 §9/§10. Use a bounded strict tree-to-canonical-value projection consisting of maps/lists/strings and signed-long integrals only, after closed per-object field/type checks. Input schema map keys/types follow A1 and model array sorted by type/key. Explicitly construct final result with exact fields and fixed pendingAuthority order.
- Error and edge behavior: Fixed precedence in A2 §9.2. Compare raw trust before parsing trust. A1 noncanonical candidate/format maps INVALID, three candidate digest discrepancies map DIGEST_MISMATCH, valid scope/claims/key/time mismatches map TRUST_REJECTED. Strict Base64 length/padding before crypto. Invalid SPKI with correct Base64 maps TRUST_REJECTED. Missing JDK crypto service and filesystem inability map IO_UNAVAILABLE. A length-valid Ed25519 signature with invalid point/scalar or verify false maps SIGNATURE_MISMATCH. No side effect before output. Catch boundary exceptions by purpose and retain no raw exception text in public diagnostics.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001. Use JDK only for crypto, UTC and Base64. Existing Jackson for tree and canonical serialization.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Keep same-package technical query and immutable carriers.
- Implementation pseudocode:

```java
static byte[] verify(Path root, String candidatePath, String signaturePath, String trustPath,
                     String expectedCandidateSha, String expectedTrustSha, long tenant,
                     String channel, Clock clock) {
    var input = new PlatformReleaseArtifactValidator(root);
    var candidateBytes = input.readSignatureInput(candidatePath, 1_048_576);
    var signatureBytes = input.readSignatureInput(signaturePath, 8_192);
    var trustBytes = input.readSignatureInput(trustPath, 8_192);
    requireTrust(expectedTrustSha.equals(hash(trustBytes)));
    var candidate = validateAndProjectExactA1Envelope(parse(candidateBytes));
    var sidecar = validateExactSidecar(parse(signatureBytes));
    requireInvalid(Arrays.equals(candidateBytes, canonicalLine(candidate.envelope())));
    String a1Sha = candidateSha256(candidate.payload());
    requireDigest(a1Sha.equals(candidate.sha()) && a1Sha.equals(expectedCandidateSha)
            && a1Sha.equals(sidecar.claims().candidateSha256())
            && hash(candidateBytes).equals(sidecar.claims().candidateEnvelopeSha256()));
    var trust = validateExactTrust(parse(trustBytes));
    requireExactPurposeRoleAlgorithmKeyIdAndScope(trust, sidecar.claims(), candidate, tenant, channel);
    var publicKey = decodeCanonicalEd25519Spki(trust);  // 44 bytes, exact prefix and keyId, JDK round trip.
    Instant now = clock.instant();  // One sample at full precision.
    requireTrust(!trust.notBefore().isAfter(sidecar.claims().issuedAt())
            && !sidecar.claims().issuedAt().isAfter(now)
            && now.isBefore(sidecar.claims().expiresAt())
            && !sidecar.claims().expiresAt().isAfter(trust.notAfter()));
    Signature check = Signature.getInstance("Ed25519");
    check.initVerify(publicKey);
    check.update("TIANJI_PLATFORM_RELEASE_DETACHED_SIGNATURE_V1".getBytes(UTF_8));
    check.update((byte) 0);
    check.update(canonical(sidecar.claims().canonicalFields()).getBytes(UTF_8));
    requireSignature(check.verify(sidecar.signature()));
    return canonicalLine(projectExactProof(candidate, sidecar.claims(), expectedTrustSha));
}
```

- Verification contribution: Ready for typed G1 end-to-end CLI.
- After this file: Compiles, A1 still green, A2 not reachable until File 4.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`

- Purpose: Expose exact A2 invocation through existing PS main and safe stdout/stderr.
- Symbols: Existing main and run, new package-private run(String[],OutputStream,PrintStream,Clock), private SignatureOptionsDTO and signature options parser.
- Repository evidence: Existing explicit A1 verify parse, usage and output check, PS Boot PropertiesLauncher.
- Dependencies and consumers: File 3 verifier, existing PS exec JAR, File 1 test.
- Why now: Verifier is already compiled before entry dispatch.
- Contract/signature changes: Existing three-argument run delegates with Clock.systemUTC. Exact leading token verify-signature dispatches A2. Its eight named flags each exactly once, 17 argv elements, no fallback. Existing A1 verify goes through the old parse/body. Final output uses existing write/flush/checkError pattern with per-command code.
- Input/output and state mapping: Path.of validated against A1 normalized absolute root text, positive canonical long, channel pattern, two 64-lowerhex digests. Delegate paths and clock to verifier. Output single buffer only after valid proof.
- Error and edge behavior: Bad argv/A2 root text exit 64 A2_USAGE. Verifier fixed failure 65/66. IOException or PrintStream.checkError returns 66 A2_IO_UNAVAILABLE, possible partial stdout on actual output failure. Existing A1 exit 64/65/66 and CLI bytes unchanged.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001. No Spring configuration.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11. Private immutable options and one same-package Clock seam.
- Implementation pseudocode:

```java
static int run(String[] argv, OutputStream stdout, PrintStream stderr) {
    return run(argv, stdout, stderr, Clock.systemUTC());
}
static int run(String[] argv, OutputStream stdout, PrintStream stderr, Clock clock) {
    if (argv != null && argv.length > 0 && "verify-signature".equals(argv[0])) {
        SignatureOptionsDTO options;
        try {
            options = parseSignatureOptions(argv);
        } catch (IllegalArgumentException badArgv) {
            return fail(stderr, 64, SIGNATURE_USAGE);
        }
        try {
            return writeExactProof(PlatformReleaseSignatureVerifier.verify(
                    options.root(), options.candidate(), options.signature(), options.trust(),
                    options.expectedCandidateSha(), options.expectedTrustSha(), options.tenant(),
                    options.channel(), clock), stdout, stderr, SIGNATURE_IO_UNAVAILABLE);
        } catch (SignatureFailure rejected) {
            return fail(stderr, rejected.exitCode(), rejected.code());
        }
    }
    return runExistingVerify(argv, stdout, stderr);
}
```

- Verification contribution: Run G1 full A1/A2 and P1 packaged success and refusal. Clock tests change to typed call after compilation.
- After this file: Full finite command and real packaged CLI ready to verify.

#### File 5 — `CREATE docs/egon/evidence/2026-11-05-platform-release-detached-signature.md`

- Purpose: Retain actual proof with exact source and package provenance.
- Symbols: G0/R1/R2/G1/P1/S1 and Step closure.
- Repository evidence: A1 evidence uses fresh XML, byte digests and exact archive mode.
- Dependencies and consumers: Current source hashes, public vector source/expected digests, current test reports, fresh PS archive and CLI outcome. Owner uses as offline evidence.
- Why now: Capture observed results after finite and packaged gates.
- Contract/signature changes: Documentation only, path authorized by A2 §8.
- Input/output and state mapping: Record actual commands, exits, tests/errors/failures/skips, source/dirty lock, archive SHA, class/hash/Start-Class, candidate/trust/proof digests and exact fixed denial codes. Document provenance of synthetic vector and no live claims.
- Error and edge behavior: Missing or stale outputs block Step, keep only redacted evidence. Never record runtime success.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Same owner and evidence path, no additional code.
- Implementation pseudocode:

```text
verify current lock matches owning Surefire XML and rebuilt PS archive
record actual finite and offline package outcomes with SHA-256
record actual signer and runtime evidence as outside this Step
```

- Verification contribution: Reviewable final proof.
- After this file: Exact test/package evidence is ready; copy only the governing documents and stage after the static gate.

#### File 6 — `CREATE docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md`

- Purpose: Preserve the exact approved A2 design used by Step 1.
- Symbols: A2 §4/§6.3/§9/§14, REQ-001–005 and Related Plans link.
- Repository evidence: Same owner and Version 9 conventions as committed A1 Spec.
- Dependencies and consumers: Owner-approved scratch A2 Spec, File 7 Plan, existing A1 dependency link.
- Why now: The design is reviewed and SHA-locked before code, then archived with the implementation.
- Contract/signature changes: Documentation only. Byte-for-byte owner-approved Spec.
- Input/output and state mapping: Exact scratch bytes → exact target path; preserve metadata and A1 relative references.
- Error and edge behavior: Any changed approval SHA or occupied path stops archival; no overwrite.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Same owner and no scope changes.
- Implementation pseudocode:

```sh
shasum -a 256 "$A2_APPROVED_SPEC"
test ! -e docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md
cp -n "$A2_APPROVED_SPEC" docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md
cmp "$A2_APPROVED_SPEC" docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md
```

- Verification contribution: S1 document structure, exact SHA and relationship.
- After this file: Approved Spec is copied and ready for reciprocal link validation after File 7.

#### File 7 — `CREATE docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md`

- Purpose: Archive the reviewed Plan used by the single Step.
- Symbols: This Version 5 Plan, A2 Implements Spec, A1 dependency and Step 1.
- Repository evidence: Existing A1 Plan and user requirement for one logical commit.
- Dependencies and consumers: Owner-approved scratch Plan, File 6 approved Spec, final audit.
- Why now: Preserve the exact governing instructions with the implementation and evidence.
- Contract/signature changes: Documentation only, exact final lock.
- Input/output and state mapping: Exact scratch bytes → exact target path; both relative links resolve.
- Error and edge behavior: Wrong approval hash, occupied path or failed strict document gate blocks staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Existing owner and document structure.
- Implementation pseudocode:

```sh
shasum -a 256 "$A2_APPROVED_PLAN"
test ! -e docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md
cp -n "$A2_APPROVED_PLAN" docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md
cmp "$A2_APPROVED_PLAN" docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md
```

- Verification contribution: Exact S1 document, spec binding and commit scope.
- After this file: Audit the exact seven paths and one focused commit.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: G0/R1/R2/G1/P1/S1 in §8.
- Expected result: G0/G1 actual finite tests, two exact intentional RED assertions, P1 actual offline child success and safe declines, S1 exact path diff.
- Failure returns to: File 1 for vector/test isolation, File 2 for A1 changes, File 3 for verifier/UTC/scope, File 4 for diagnostics/CLI and File 5 for provenance.
- Completion criteria: Artifact-signature verification works from actual PS exec JAR with a known independent synthetic pin. Existing A1 stays byte compatible.
- Rollback: Revert only the one A2 Step commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`, `docs/egon/evidence/2026-11-05-platform-release-detached-signature.md`, `docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md`, `docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md`.
- Commit: feat(process): verify detached release candidate signatures.

## 8. Test, Validation, and Quality Gates

Key-validity review before source lock: reject all canonical low-order Ed25519 public points, including the identity. The finite malformed-trust test includes an independently pinned identity SPKI and a forged signature that the bare local provider otherwise accepted. No third-party crypto dependency; existing JDK verification remains authoritative for the admitted keys.

Working directory is /Users/mario/SelfProject/Stellar-Enclosure/tianji-service. All commands below are future execution gates.

```sh
a2_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
a2_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
a2_finite() {
  JAVA_HOME="$a2_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$a2_settings" -s "$a2_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
a2_finite PlatformReleaseArtifactCandidateTest
a2_finite 'PlatformReleaseSignatureVerifierTest#verifiesPinnedDetachedSignature'
a2_finite 'PlatformReleaseSignatureVerifierTest#rejectsSubstitutedCandidateTrustAndSignature'
a2_finite PlatformReleaseSignatureVerifierTest,PlatformReleaseArtifactCandidateTest
```

| Gate | Exact scope and observed expectation | Failure returns to |
| --- | --- | --- |
| G0 | Owning fresh PS Surefire XML, 6 A1 tests, no failure/error/skip | Current baseline/Validator |
| R1 | 1 actual assertion failure for 0 expected vs existing 64, no error/skip | File 1 and public vector preconditions |
| R2 | 1 actual assertion failure for 65 expected vs existing 64, no error/skip | File 1 and error vector |
| G1 | Exact four A2 methods and six A1 methods in fresh PS XML, zero failures/errors/skips | Files 1–4 |
| P1 | Build fresh permission-producer exec JAR, direct bounded A2 0/65/65 using public vectors | Files 2–4 or artifact provenance |
| S1 | git diff --check, path-scoped final diff, exact stage/commit SHA | File 5 and Step scope |

P1 uses the current production archive and no application. Build after G1 using the existing source reactor.

```sh
a2_base=$(git rev-parse --verify HEAD)
test "$a2_base" = 17fee280792e95da938c68ba65a783b965ca63d8
JAVA_HOME="$a2_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$a2_settings" -s "$a2_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$a2_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-01a2-20261105-step1 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=PlatformReleaseSignatureVerifierTest,PlatformReleaseArtifactCandidateTest,ProcessPermissionIntegrationTest
```

The captured lock supersedes the literal head only after owner rechecks the same source scopes and approves the new exact lock. Verify current PS exec at stellar-enclosure-tianji-process-server/target/stellar-enclosure-tianji-process-server-exec.jar. Require unique BOOT-INF/classes/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.class and PlatformReleaseSignatureVerifier.class, unique existing root PaC entry META-INF/permissions/manifest-v1.json, no BOOT-INF PaC alias, existing PropertiesLauncher and unchanged Start-Class top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication. Recompute archive SHA and match target/permissions/artifact-evidence.json artifactDigest. Record raw PaC SHA separately from canonical PaC digest.

Synthetic vector preparation uses the already committed A1 Plan package proof to capture the exact stdout of successful verify with an independently checked candidate semantic SHA. Capture into owner-private scratch outside Git. Freeze raw A1 stdout, two hashes, public SPKI, trust file, sidecar and exact expected proof. Produce detached signature in a separately authorized one-shot JDK 25 scratch author. Generate synthetic KeyPairGenerator.getInstance("Ed25519").generateKeyPair() in memory, call Signature.initSign with the ephemeral private handle, sign the independently assembled domain bytes, then exit. Generate a second unrelated ephemeral key only when preparing the wrong-key signed vector. Retain only public SPKI, detached signatures and exact signed bytes. No user/production private key, key encoding, private key output, persisted key, signing production path or repository vector-generation script. Test-only clock window is finite, e.g. issuedAt 2025-01-01T00:00:00Z, expiresAt 2050-01-01T00:00:00Z and trust notBefore 2025-01-01T00:00:00Z/notAfter 2051-01-01T00:00:00Z. Actual packaged proof uses current UTC and must be in this frozen synthetic window. P1 consumes identical public file bytes and hardcoded independent expected pins from that corpus. The A1 candidate sourceRevision and archive SHA attest the frozen A1 fixture, distinct from the A2 PS archive executing the proof.

Run with private real POSIX A2_VECTOR_ROOT, filename candidate.json, candidate.sig.json and release-trust.json. Pin both digests independently before passing argv. An inline bounded runner checks exact byte result, safe error lines, no app start and status. P1 requires negative using one flipped bit in valid-length detached signature, and negative using a different 64-lowerhex independent trust pin, in separate scratch invocations. No committed harness.

```sh
"$a2_jdk/bin/java" -Dloader.main=top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli \
  -cp "$A2_PS_EXEC" org.springframework.boot.loader.launch.PropertiesLauncher \
  verify-signature --root "$A2_VECTOR_ROOT" --candidate candidate.json --signature candidate.sig.json \
  --trust release-trust.json --expect-candidate-sha256 "$A2_EXPECTED_CANDIDATE_SHA" \
  --expect-trust-sha256 "$A2_EXPECTED_TRUST_SHA" --tenant 20 --channel expense
```

Stage the four reviewed public fixture files named below and pass the independent candidate/trust pins. Run the same flags with a bounded child, retaining only summarized evidence.

```sh
python3 - "$a2_jdk/bin/java" "$A2_PS_EXEC" "$A2_VECTOR_ROOT" \
  "$A2_EXPECTED_CANDIDATE_SHA" "$A2_EXPECTED_TRUST_SHA" <<'PY_GATE'
import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

java, archive_arg, vectors_arg, candidate_pin, trust_pin = sys.argv[1:]
jar_path = Path(archive_arg).resolve(strict=True)
vectors = Path(vectors_arg).resolve(strict=True)
package = "top.egon.cola.platform.tianji.process.server.release"
class_prefix = "BOOT-INF/classes/" + package.replace(".", "/") + "/"
main = package + ".PlatformReleaseArtifactCli"
launcher = "org.springframework.boot.loader.launch.PropertiesLauncher"
pac = "META-INF/permissions/manifest-v1.json"

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

with jar_path.open("rb") as stream:
    archive_sha = hashlib.file_digest(stream, "sha256").hexdigest()
permission = json.loads((jar_path.parent / "permissions/artifact-evidence.json").read_bytes())
assert permission["artifactDigest"] == archive_sha
assert permission["producerId"] == "tianji-process-java"
with zipfile.ZipFile(jar_path) as archive:
    names = archive.namelist()
    for name in (class_prefix + "PlatformReleaseArtifactCli.class",
                 class_prefix + "PlatformReleaseSignatureVerifier.class",
                 launcher.replace(".", "/") + ".class", "META-INF/MANIFEST.MF", pac):
        assert names.count(name) == 1
    assert "BOOT-INF/classes/" + pac not in names
    manifest = archive.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "").replace("\n ", "")
    assert ("Start-Class: top.egon.cola.platform.tianji.process.server."
            "TianjiProcessServerApplication") in manifest.splitlines()
    pac_sha = sha(archive.read(pac))

candidate_bytes = (vectors / "candidate.json").read_bytes()
candidate = json.loads(candidate_bytes)
trust_bytes = (vectors / "release-trust.json").read_bytes()
signature_bytes = (vectors / "candidate.sig.json").read_bytes()
expected = (vectors / "expected-proof.json").read_bytes()
assert candidate["candidateSha256"] == candidate_pin
assert sha(trust_bytes) == trust_pin
assert json.loads(expected)["candidateEnvelopeSha256"] == sha(candidate_bytes)
wrong_pin = "0" * 64 if trust_pin != "0" * 64 else "1" * 64
environment = {"ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS": "false"}

with tempfile.TemporaryDirectory(prefix="tianji-a2-signature-") as temp:
    stage = Path(temp).resolve(strict=True)
    stage.chmod(0o700)
    for name in ("candidate.json", "candidate.sig.json", "release-trust.json"):
        shutil.copyfile(vectors / name, stage / name)
        (stage / name).chmod(0o600)
    def run(pin):
        args = [java, "-Dloader.main=" + main, "-cp", str(jar_path), launcher,
                "verify-signature", "--root", str(stage), "--candidate", "candidate.json",
                "--signature", "candidate.sig.json", "--trust", "release-trust.json",
                "--expect-candidate-sha256", candidate_pin, "--expect-trust-sha256", pin,
                "--tenant", "20", "--channel", "expense"]
        return subprocess.run(args, capture_output=True, timeout=30, env=environment)
    success = run(trust_pin)
    assert (success.returncode, success.stdout, success.stderr) == (0, expected, b"")
    denied = run(wrong_pin)
    assert (denied.returncode, denied.stdout, denied.stderr) == (
        65, b"", b"TIANJI_RELEASE_SIGNATURE_TRUST_REJECTED\n")
    changed = json.loads(signature_bytes)
    raw = bytearray(base64.b64decode(changed["signature"], validate=True))
    raw[0] ^= 1
    changed["signature"] = base64.b64encode(raw).decode("ascii")
    (stage / "candidate.sig.json").write_text(json.dumps(changed), encoding="utf-8")
    denied = run(trust_pin)
    assert (denied.returncode, denied.stdout, denied.stderr) == (
        65, b"", b"TIANJI_RELEASE_SIGNATURE_MISMATCH\n")
print(json.dumps({"archiveSha256": archive_sha, "rawPacSha256": pac_sha,
                  "candidateSha256": candidate_pin, "candidateEnvelopeSha256": sha(candidate_bytes),
                  "trustSha256": trust_pin, "proofSha256": sha(expected),
                  "exits": [0, 65, 65]}, sort_keys=True))
PY_GATE
```

S1 checks no unrelated staged path, source/Spec/Plan hashes and structural validation, then stages the reviewed seven paths explicitly. Compare the staged name list with §5 before the single commit.

```sh
git diff --check
git diff --cached --check
git add -- \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java \
  docs/egon/evidence/2026-11-05-platform-release-detached-signature.md \
  docs/egon/spec/2026-11-05-17-30-platform-release-detached-signature.md \
  docs/egon/plan/2026-11-05-17-44-platform-release-detached-signature.md
git diff --cached --check
git diff --cached --name-only
```

## 9. Migration, Compatibility, Rollout, and Rollback

A2 §11/§16 excludes database, configuration and production rollout. Existing PS default Start-Class, verify flags, A1 status, digest and permission manifest stay compatible. An operator opts into the explicit subcommand. Revert one A2 commit for source rollback.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| REQ-001 | A2 §4/§9.2, A1 §9/§10 | Step 1 | 1/2/3 | R1/G1/P1 | Independent candidate and envelope hashes |
| REQ-002 | A2 §5/§9.2 | Step 1 | 1/3/4 | R2/G1/P1 | Explicit trust and exact binding/negative |
| REQ-003 | A2 §6.3/§9.2 | Step 1 | 1/3/4 | G1/P1 | UTC boundaries and real Ed25519 |
| REQ-004 | A2 §9.2 | Step 1 | 1/2/3/4 | G1/P1 | Private files, strict parser, diagnostics |
| REQ-005 | A2 §9.2/§16 | Step 1 | 1/4/5 | G0/G1/P1/S1 | Separate evidence and unchanged A1 |
| REQ-006 (A1 compatibility) | A1 §4/§9/§16 | Step 1 | 1/2/4/5 | G0/G1/P1 | A1 states unchanged and owner evidence stays separate |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| RISK-001 | Wrong key/source/sidecar can accidentally be tested | Step 1 | A2 §9.2/§14 | Implementation owner | Freeze public vector provenance, both pins and proof bytes |
| RISK-002 | PS execution archive built from stale source | Step 1 P1 | Existing artifact evidence and class entries | Implementation owner | Rebuild under exact lock, confirm archive and actual result |
| OPEN-001 | Actual approved signer and independent trust distribution | Future real use | A2 §5/§18 | Release governance owner | Pending for real use, synthetic source scope approved for planning |
| RISK-003 | Local time and offline trust may be stale | A2 result consumption | A2 §6.3/§18 | Offline operator | Current verification, independent governance for live revocation |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

A2 REQ-001–005 and A1 REQ-006 compatibility lead to a single offline command and precise source/packaged gates.

### 12.2 Spec consistency

PLAN-CLAR-001–003 only fix shared seams, finite fixture location and evidence capture. No new role, HTTP, store or release workflow.

### 12.3 Repository executability

Current PS files, package, owning JUnit paths, existing source reactor and Boot profile were inspected. Baseline locked after T4-02 and T4-03 commits. All future checks are exact paths and use existing tools.

### 12.4 Test and release completeness

R1/R2 before production behavior, G1 exact finite coverage, P1 actual offline exec package, S1 source and evidence. Live signer/deployment/revocation remain independent.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4/§5 | Existing PS release owner | None |
| MC-REUSE-001 | Applicable | PASS | §4.7/File 2 | Existing A1 guard/canonical/hash | None |
| MC-DEP-001 | Applicable | PASS | §4.7/§6 | Existing JDK and Jackson only | None |
| MC-NAME-001 | Applicable | PASS | §5/Files 1–4 | Semantic classes and DTO | None |
| MC-VALID-001 | Applicable | PASS | Files 1–4 | Exact CLI, scope, digest and inputs | None |
| MC-MODEL-001 | Applicable | PASS | Files 1/3/4 | Private immutable values | None |
| MC-CONVERT-001 | Not applicable | N/A | A2 §10 | Technical same-package closed projection | None |
| MC-LOG-001 | Not applicable | N/A | File 4 | Fixed diagnostics, no business log | None |
| MC-BEAN-001 | Not applicable | N/A | §4.7 | No Spring bean | None |
| MC-UTIL-001 | Applicable | PASS | Files 2/3 | JDK file, Ed25519, time and hashing | None |
| MC-JSON-001 | Applicable | PASS | Files 1–3 | Same strict Jackson and canonical bytes | None |
| MC-TIME-001 | Applicable | PASS | §4.9/Files 1/3/4 | UTC and half-open exact time | None |
| MC-CONFIG-001 | Not applicable | N/A | §6/§9 | No config or profile change | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5/A2 §13 | Fixed simple flow | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 | One exact commit scope | None |
| MC-TEST-001 | Applicable | PASS | §7/§8 | Focused RED/GREEN and packaged offline proof | None |
| MC-BLOCKER-001 | Applicable | PASS | §11 | Synthetic source Plan ready to review | Actual signer and trust distribution remain pending outside Step |

### 12.6 Final verdict

PASS — Ready for user review

本次只交付 Plan。源码修改、测试、Maven、打包、数据库及应用均未执行。
