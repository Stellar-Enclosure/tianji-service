# T4-01A2 Process 发布快照离线对照实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-21-51-platform-release-process-snapshot.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 21:51 Asia/Shanghai |
| Updated | 2026-11-05 21:51 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | 一个 PS release 源码 Step，三个 Java 路径及对应设计、计划、证据 |
| Source Requirement | 主计划 §19 T4-01，签名候选与不可变 Process 发布材料对照 |
| Baseline Revision | tianji-service main 8de3e76184d4812ed2b1bac982b7c6889e4c49c0 |
| Implements Spec | [Process snapshot](../spec/2026-11-05-21-50-platform-release-process-snapshot.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 21:50 Asia/Shanghai SHA-256 c3c362cae061ab301338b905209c73aaf669e3e430614c3a757a020f6f6d542c |
| Effective Specs | [Snapshot](../spec/2026-11-05-21-50-platform-release-process-snapshot.md), [A2 §9/§10](../spec/2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions), [A1 §9](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) |
| Depends On Plans | [A2](2026-11-05-17-44-platform-release-detached-signature.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在现有 PS 离线 main 加入显式 compare-process-snapshot。一次完整 A2 验签之后，使用独立钉选的真实 ProcessReleaseDTO 形状比较同版本的整个 Process 集合。一项期望 RED、现有 A1/A2 回归、实际 PS 打包 CLI 九次调用及单一提交完成源码范围。包使用冻结公开合成证据。

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Snapshot Spec](../spec/2026-11-05-21-50-platform-release-process-snapshot.md) §4 REQ-001–004、§9 CLI-001 和 §14 TEST-001–005 为本 Step 权威。Root 在当前锁审阅精确源码范围和校验合同后执行。

### 2.2 Effective Spec set

| Effective Spec | Sections | Application |
| --- | --- | --- |
| [Snapshot](../spec/2026-11-05-21-50-platform-release-process-snapshot.md) | §4/§6.3/§8/§9/§14 | 新对照流程、关闭形状、结果与证据 |
| [A2](../spec/2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) | §9/§10 | 现有签名、时间、scope、信任与字节合同 |
| [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md#9-interface-definitions) | §9/§10 | 现有安全 reader、候选和 canonical JSON |

### 2.3 Superseded or excluded content

None. 各 owner 的当前授权、发布、撤销、审阅、激活与回滚保持另行审批及验收。

## 3. Effective Requirements and Acceptance

| Requirement | Exact Spec source | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | Snapshot §4/§9 | 只按显式模式执行，原 A2 成功前不触及快照，旧两命令结果保持 | Test and CLI/verifier |
| REQ-002 | Snapshot §4/§6.3/§9 | 同 root 安全文件独立 pin、严格 DTO、固定关闭错误 | Verifier |
| REQ-003 | Snapshot §4/§7.3/§9 | 完整版本/清单/模型与有效输入相等，模型顺序无关 | Verifier |
| REQ-004 | Snapshot §4/§9/§14 | 单行状态与四项 pendingAuthority、成品 exact replay | CLI, verifier, evidence |
| REQ-005 | Effective A1 §4 and A2 §4 | 既有确定性、签名证明与发布权威分离保持 | G0/G1/P1 regression |
| REQ-006 | Effective A1 §4 | A1 来源边界与 T3-12 独立验收保持 | G0/G1 and S1 scope |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

从已有 A2 用例扩展可编译 R1。包内验证器接入 typed snapshot 解析与比较，随后由 CLI 显式分派。先有限，再以真实 PS exec 成品运行同一入口。最后附上实际报告、审阅文件及聚焦提交。

### 4.2 Test-first strategy

G0 先核对原 10 项。仅替换测试后运行指定 R1，旧命令应返回 64 而触发期望 0 的 assertion。两项源码变化完成后执行 G1 全部 14 项。P1 以干净构建运行这 14 项与已有 12 个本地 PaC/MockMvc 回归。

### 4.3 Sequential and parallel boundaries

File 1 和 R1 在生产路径变更之前。File 2 的包内参数与 File 3 一起形成可编译边界，禁止以两者间短暂编译失败作为 RED。P1 独立于在线系统，只依赖 G1 及新产物。File 4–6 在实际结果取得后归档。

### 4.4 Commit boundaries

一个 PS 所在仓的六路径源提交，测试和实现同归一次逻辑变更。无中间提交。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Implementation handling |
| --- | --- | --- | --- |
| Existing CLI branch | Add / §7.0 | PlatformReleaseArtifactCli#run | One explicit additional branch, old exact flags |
| Typed snapshot comparison | Add / §7.0 | Public ProcessReleaseDTO and A2 verified JsonNode | Same package, one bounded reader call and no persistent side effects |
| A1 safety and A2 trust | Keep / §7.0 | PlatformReleaseArtifactValidator#readSignatureInput, same verifier | Original methods, guard, and crypto logic reused |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| PS finite CLI test | REQ-001–004 | G0/R1 | Existing A2 test and unchanged CLI API | Step 1 |
| PS comparison and dispatch | REQ-001–004 | G1 | A2, A1, neutral Process DTO | Step 1 |
| Package evidence and docs | REQ-004 | P1/S1 | Exact source lock and reviewed Spec/Plan | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture and names | PS release technical package | Snapshot §7/10 | Existing CLI and verifier, one immutable ProcessSnapshotInputDTO | S1 |
| Validation and conversion | A1 shape/field/parse, C records | Snapshot §6/9 | Strict local contract + canonical JSON, no business entity or Spring binding | G1 |
| Utility and time | Existing JDK 25/Jackson 3 | Snapshot §6.3 | Same existing verification and JDK UTC parse | G1 |
| Spring config, logging, persistence | No managed object in A2 path | Snapshot §7/11/16 | No Bean, database, config or logging change | S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Safe file, hashing and JSON | A1 validator | readSignatureInput/parse/canonical/hash | Fits existing secure read | Reuse | New call only | Step 1 MC-REUSE-001 |
| Signature authority and time | A2 verifier | verify, validation, fixed Clock seam | Existing scope and full verification fit | Reuse | Same code | Step 1 MC-TIME-001 |
| Public process facts | Neutral C DTO | ProcessReleaseDTO and ProcessModelDTO | Current GET direct DTO | Reuse | Closed local projection, no wire change | Step 1 MC-MODEL-001 |

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
| Rule 1 | §8/10 | Existing PS release package | Files 1–3 | Named immutable ProcessSnapshotInputDTO | G1 | Step 1 | PASS |
| Rule 2 | §6/9 | A1 strict parser and C constructors | Files 1–3 | Closed argv/shape and semantic compare | R1/G1 | Step 1 | PASS |
| Rule 3 | §6/10 | Immutable neutral model values | Files 2/3 | Immutable record; same package local projection | G1 | Step 1 | PASS |
| Rule 4 | §6/7 | Existing offline main | Files 2/3 | Fixed stderr, no managed bean/logging | P1 | Step 1 | N/A |
| Rule 5 | §6 | Existing JDK/Jackson | Files 1–3 | Reuse A1/Java helpers | G1 | Step 1 | PASS |
| Rule 6 | §9/10 | Existing Jackson and canonical output | Files 1/2 | Closed JSON and exact byte proof | G1/P1 | Step 1 | PASS |
| Rule 7 | §16 | Existing executable | No config file | Explicit local invocation | S1 | Step 1 | N/A |
| Rule 9 | §13 | One fixed flow | File 2 | Direct composition, no strategy registry | G1 | Step 1 | N/A |
| Rule 10 | §6.3 | Existing A2 Clock and C Instant | Files 1–3 | java.time strictly UTC and inherited signature window | G1 | Step 1 | PASS |
| Rule 11 | §7/8/11 | Existing PS ownership | Files 1–6 | One owner, no new layer/storage/event | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | ProcessReleaseDTO.createdAt | Occurred instant | java.time.Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: omitted from output | Strict calendar years 0001–9999, rejects 24:00/leap seconds | Nanoseconds preserved, no comparison to Clock | Existing Process GET contract | Step 1 G1 TEST-001/003 |
| TIME-002 | Existing A2 issuedAt/expiresAt and verification clock | Occurred instant | java.time.Clock and Instant | Not persisted | UTC Z | Not applicable: UTC instant | Not applicable: offline | Existing A2 half-open time bounds | Full current precision | Preserves old A2 byte result | Step 1 G1 TEST-004 |

## 5. Change File Tree

| Operation | Exact path | File | Requirements | Gate |
| --- | --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java` | 1 | REQ-001–004 | R1/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java` | 2 | REQ-001–004 | R1/G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java` | 3 | REQ-001–004 | R1/G1/P1 |
| CREATE | `docs/egon/evidence/2026-11-05-platform-release-process-snapshot.md` | 4 | REQ-004 | S1 |
| CREATE | `docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md` | 5 | REQ-004 | S1 |
| CREATE | `docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md` | 6 | REQ-004 | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact clean `8de3e76184d4812ed2b1bac982b7c6889e4c49c0`; empty staged and worktree diff before Step. Verify A2 sources unchanged since 74369e7. Reconfirm review hash and six exact paths before tracked copy.

### 6.2 Build, test, and environment prerequisites

Existing offline source-reactor script, existing isolated settings and Homebrew JDK25 path in §8. For package proof use Python 3.11+ and current synthetic A2 UTC window. A2 public vector and separate comparison inputs are already frozen in scratch. Before execution verify artifact is built by current source with its PaC evidence.

### 6.3 Immutable constraints and approved decisions

No source app execution, listeners, DB, provider, network, real signer, private key material or authorization. No POM/PaC/HTTP/contract/config/Flyway or other repo change.

### 6.4 Plan Clarifications

| ID | Choice | Evidence / consequence |
| --- | --- | --- |
| PC-001 | Cap snapshot raw bytes at 65,536 | C DTO limits 16 models × 16 declarations and bounded key strings. Allows normal UTF-8 JSON plus formatting with a finite cap. |
| PC-002 | Exclude createdAt from semantic equality | Signed A1 lacks createdAt. Its shape and exact raw bytes remain checked, with no inference about freshness or publication identity. |
| PC-003 | Use a fixed public vector and no fetched DTO | Same serialization shape is tested in JUnit. Frozen package snapshot fixture remains a synthetic fixture even when the CLI is executed from actual archive. |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付已签名候选与 Process 快照离线对照

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005 (inherited compatibility), REQ-006 (inherited compatibility).
- Dependencies: Exact approved Spec/Plan, A1 and A2 source, clean current baseline and synthetic public vectors.
- Baseline state: Existing A2 exact command succeeds; unknown compare-process-snapshot routes to old A1 usage exit 64.
- Observable outcome: Valid matching snapshot yields PS_RELEASE_SNAPSHOT_MATCHED_OFFLINE. Pin drift, valid semantic drift and malformed DTO each yield safe distinct 65.
- End state: Six-path focused Service commit, finite and real offline archive evidence.
- Test-first gate: Required. File 1 only, R1 fails expected 0 vs actual 64 with assertion, then production files.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java`

- Purpose: 固定正例、拒绝和旧输出.
- Symbols: comparesSignedCandidateWithPinnedPublicProcessSnapshot; refusesReleaseManifestModelAndEffectiveInputDrift; refusesMalformedPublicSnapshotAndUnauthorizedFields; verifiesSignatureBeforeReadingDistinctBoundedSnapshotFile.
- Repository evidence: 现有 A2 公开向量、固定 Clock 和 CLI run.
- Dependencies and consumers: A2 test helpers, JUnit @TempDir, neutral ProcessReleaseDTO.
- Why now: 新测试只调用旧 compile seam，先跑 R1.
- Contract/signature changes: 增加 4 个有限测试和私有合成 helper.
- Input/output and state mapping: 直接序列化现有 ProcessReleaseDTO；凑齐 4 个安全文件和独立 raw pin.
- Error and edge behavior: 同名不同 SHA、模型/inputs/full set 偏差、格式/日期/duplicate/size/symlink/link/错误签名，均断言精确输出.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-TIME-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10.
- Implementation pseudocode:

```java
@Test void comparesSignedCandidateWithPinnedPublicProcessSnapshot() {
    var published = new ProcessReleaseDTO("expense-v1", FROZEN_PROCESS_SHA, FIXED_CREATED_AT, REAL_DTO_MODELS);
    String pin = writeSnapshot(root, fixture(JSON.valueToTree(published)));
    var proof = run(snapshotArgs(root, "published.json", pin));
    assertEquals(0, proof.status()); // R1 is 64 with old CLI.
    assertExactProofAndIdenticalReplay(proof, pin, EXPECTED_PROOF);
}
@Test void refusesReleaseManifestModelAndEffectiveInputDrift() {
    assertSnapshotMismatch(root, validDtoWithDifferentManifest);
    assertSnapshotMismatch(root, validDtoWithDifferentModelAndInputs);
}
@Test void refusesMalformedPublicSnapshotAndUnauthorizedFields() {
    assertInvalidSnapshot(root, dtoWithDuplicateKeysOrInvalidCreatedAt);
}
@Test void verifiesSignatureBeforeReadingDistinctBoundedSnapshotFile() {
    assertRefused(run(snapshotArgs(badSignatureRoot, "absent.json", WRONG_PIN)), 65, MISMATCH);
    assertUnsafeSnapshotVariantsAndNoFallback();
}
```

- Verification contribution: R1 actual old exit assertion, G1 four named comparison tests.
- After this file: 旧生产源仍未修改，R1 取得失败后进入 verifier.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`

- Purpose: 在当前验签尾部严格读取并比较一份快照.
- Symbols: verify(..., Clock, ProcessSnapshotInputDTO), compareSnapshot, models, snapshotCreatedAt.
- Repository evidence: A2 verify and A1 readSignatureInput/parse/shape/field/hash/canonical; ProcessReleaseDTO and ProcessModelDTO.
- Dependencies and consumers: File 1 tests, File 3 matching package CLI, C immutable record.
- Why now: 同一 reader 留住前三个物理文件身份，快照为第四个文件.
- Contract/signature changes: 包内 verify 增加可空不可变 ProcessSnapshotInputDTO；null 路径产生原结果.
- Input/output and state mapping: A2 checked candidate + separately pinned closed snapshot into canonical proof; model digest key mapped to contentSha256.
- Error and edge behavior: Unsafe shape 65 INVALID, wrong pin 65 DIGEST_MISMATCH, exact compare drift 65 MISMATCH, file IO 66; old A2 errors first.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
record ProcessSnapshotInputDTO(String path, String expectedSha) { }
static byte[] verify(Path root, String candidatePath, String signaturePath, String trustPath,
                     String candidatePin, String trustPin, long tenant, String channel, Clock clock,
                     ProcessSnapshotInputDTO snapshot) {
    var reader = new PlatformReleaseArtifactValidator(root);
    // Existing sequence reads three files, validates scope, strict time and actual Ed25519 signature.
    ReleaseSignatureResultDTO original = existingA2ResultFromSameReaderAndClock(reader, candidatePath,
            signaturePath, trustPath, candidatePin, trustPin, tenant, channel, clock);
    if (snapshot == null) return canonicalLine(original.payload());
    byte[] bytes = reader.readSignatureInput(snapshot.path(), 65_536);
    String actual = hash(bytes);
    if (!actual.equals(snapshot.expectedSha())) throw new SignatureFailure(65, SNAPSHOT_DIGEST_MISMATCH);
    var object = parse(bytes);
    shape(object, "releaseVersion", "manifestSha256", "createdAt", "models");
    var published = new ProcessReleaseDTO(field(object, "releaseVersion"), field(object, "manifestSha256"),
            snapshotCreatedAt(object), models(object.get("models"), "contentSha256"));
    assertExactReleaseManifestAndModelSet(published, validatedSignedCandidate, "modelSha256");
    return canonicalLine(snapshotProof(original.payload(), actual, published));
}
```

- Verification contribution: G1 semantic/pin/UTC/strict input plus P1 exact artifact proof.
- After this file: 固定包内行为，和 File 3 合并后首次编译.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`

- Purpose: 接入单独 argv 路径和固定 stderr.
- Symbols: run, runSignature, parseSignature, SignatureOptionsDTO.
- Repository evidence: Existing explicit PS main and existing A1/A2 options/diagnostics.
- Dependencies and consumers: File 2 and already compiled A2 CLI seam from File 1.
- Why now: 只在 verifier 已具备闭合快照校验后公开明确子命令.
- Contract/signature changes: Existing main/run signatures unchanged; internal parse selects exact options set.
- Input/output and state mapping: 8 A2 required pairs vs 10 compare pairs, valid snapshot pin feeds package verifier, stdout only after proof.
- Error and edge behavior: Exact usage exit 64; no stdout on refusal; keep existing A2 65/66; output error snapshot 66.
- Standards impact: MC-VALID-001, MC-JSON-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
static int run(String[] args, OutputStream stdout, PrintStream stderr, Clock clock) {
    if (args != null && args.length > 0) {
        if ("verify-signature".equals(args[0])) return runSignature(args, stdout, stderr, clock, false);
        if ("compare-process-snapshot".equals(args[0])) return runSignature(args, stdout, stderr, clock, true);
    }
    return existingA1Run(args, stdout, stderr);
}
// runSignature selects closed SIGNATURE_OPTIONS or SNAPSHOT_OPTIONS, then typed snapshot ref or null.
// SignatureFailure passes exact code and exit to fail; write occurs once after completed verification.
```

- Verification contribution: G1 direct run and P1 explicit PropertiesLauncher argv.
- After this file: 全部源码可编译，执行 G1 后 P1.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-platform-release-process-snapshot.md`

- Purpose: 保存当前测试、包、权限 manifest 和 proof 事实.
- Symbols: G0/R1/G1/P1/S1 and offline boundaries.
- Repository evidence: A2 evidence format and current PS Surefire XML / permission artifact evidence.
- Dependencies and consumers: Files 1–3, frozen fixtures, current package execution.
- Why now: 需要真实执行结果后再填写可提交证据.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Actual reports, signatures/pins, proof SHA and preserved PaC payload and exact current artifact metadata.
- Error and edge behavior: 缺失、旧报告、错误绑定或伪装 live proof 均阻断.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Read fresh G0, R1, G1 and P1 reports and record exact PS test counts.
Verify package archive SHA, prior/current raw PaC SHA, equality excluding provenance fields, published snapshot and both exact stdout proof SHA values.
Record nine actual subprocess exits and no authority promotion; require current reviewed source lock.
```

- Verification contribution: EVD aligns to actual independent finite and packed gates.
- After this file: 实际 EVD 已完成，再归档两个治理文档.

#### File 5 — `CREATE docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md`

- Purpose: 保存本 Step 的审阅 Spec.
- Symbols: REQ-001–004, CLI-001, TEST-001–005 and exact Spec SHA.
- Repository evidence: Current Plan Implements Spec and A2 predecessor.
- Dependencies and consumers: Reviewed scratch Spec, File 6 reciprocal link.
- Why now: 源和 proof 均已通过，提交精确审阅字节.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Owner-approved bytes from scratch to canonical target.
- Error and edge behavior: Occupied target or changed approved SHA stops.
- Standards impact: MC-SCOPE-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md
printf '%s  %s
' "$SNAPSHOT_APPROVED_SPEC_SHA" "$SNAPSHOT_APPROVED_SPEC" | shasum -a 256 -c -
cp -n "$SNAPSHOT_APPROVED_SPEC" docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md
cmp "$SNAPSHOT_APPROVED_SPEC" docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md
```

- Verification contribution: Strict Spec with canonical reciprocal Plan link after File 6.
- After this file: Spec archived.

#### File 6 — `CREATE docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md`

- Purpose: 保存本 Step 的审阅 Plan.
- Symbols: Single Step scope, exact gates and Plan SHA.
- Repository evidence: Existing Version 5 Plans and S1.
- Dependencies and consumers: Reviewed scratch Plan, File 5 archived Spec.
- Why now: 为同一提交记录路径顺序、RED 和真实 P1.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Owner-approved bytes from scratch to canonical target.
- Error and edge behavior: Occupied target or wrong approval SHA stops; strict before exact stage.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md
printf '%s  %s
' "$SNAPSHOT_APPROVED_PLAN_SHA" "$SNAPSHOT_APPROVED_PLAN" | shasum -a 256 -c -
cp -n "$SNAPSHOT_APPROVED_PLAN" docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md
cmp "$SNAPSHOT_APPROVED_PLAN" docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md
```

- Verification contribution: Strict Plan, SHA and S1 exact scope.
- After this file: 六路径 staged 后一次提交.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: G0/R1/G1/P1/S1 in §8.
- Expected result: G0 10 pass, R1 one expected failure, G1 14 pass, P1 26 pass and package exits 0/0/65/65/65/65/66/64/0, S1 exact diff.
- Failure returns to: File 1 for RED/fixtures; File 2 for compare semantics; File 3 for arguments/exit; File 4 for evidence provenance.
- Completion criteria: Exact match and rejection from actual archive, old A1/A2 still verified, no live authority claim and exact six paths.
- Rollback: Revert the single commit. No state cleanup.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`, `docs/egon/evidence/2026-11-05-platform-release-process-snapshot.md`, `docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md`, `docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md`.
- Commit: feat(process): compare signed release snapshots offline.

## 8. Test, Validation, and Quality Gates

Execute as separate phases under §7. Isolate fresh PS owning reports by deleting only the selected existing build-generated XML reports or recording pre-run metadata, then retain fresh XML and status after each phase. The exact G0, R1, G1 commands use existing script.

```sh
snapshot_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/snapshot-comparison
snapshot_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
snapshot_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
snapshot_tests=PlatformReleaseSignatureVerifierTest,PlatformReleaseArtifactCandidateTest
snapshot_base=$(git rev-parse --verify HEAD)
test "$snapshot_base" = 8de3e76184d4812ed2b1bac982b7c6889e4c49c0
snapshot_finite() {
  JAVA_HOME="$snapshot_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$snapshot_settings" -s "$snapshot_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
# G0 before copying File 1
snapshot_finite "$snapshot_tests"
# R1 after copying only File 1
snapshot_finite 'PlatformReleaseSignatureVerifierTest#comparesSignedCandidateWithPinnedPublicProcessSnapshot'
# G1 after Files 2 and 3
snapshot_finite "$snapshot_tests"
```

| Gate | Exact scope and expectation | Failure returns to |
| --- | --- | --- |
| G0 | A2 four and A1 six, fresh 10 passing, zero errors/failures/skips | Baseline |
| R1 | Exactly one expected assertion failure, expected exit 0 actual 64, zero error/skip | File 1, previous production CLI |
| G1 | A2 old four plus new four and A1 six, 14 pass, zero error/failure/skip | Files 1–3 |
| P1 | Clean build 26 PS finite tests including existing 12 local MockMvc/PaC, actual bounded PropertiesLauncher offline matrix below | Files 1–3 or artifact lock |
| S1 | Actual evidence, strict Spec/Plan, immutable SQL/POM/PaC, exact six paths | File 4–6 / scope |

P1 package command uses committed base and existing local permission-producer profile. No Boot app entrypoint execution. Capture build outputs at the exact PS target, require fresh owning XML counts and compare the parsed root PaC with the verified prior PS producer from 1b5700a, pinned at raw SHA 7dff4a536948c260304fb92552391acf6cc1b74d658bea3e88f137f1ec62ce90. Between 1b5700a and the exact current baseline only tooling/docs changed. Preserve every PaC field except artifact.commit and artifact.buildId, which must exactly match the current build flags. Record both raw digests. Existing root PaC entry and Start-Class remain required.

```sh
JAVA_HOME="$snapshot_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$snapshot_settings" -s "$snapshot_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$snapshot_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-01a2-process-snapshot-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  "-Dtest=$snapshot_tests,ProcessPermissionIntegrationTest"
snapshot_exec="$PWD/stellar-enclosure-tianji-process-server/target/stellar-enclosure-tianji-process-server-exec.jar"
printf '%s  %s\n' '59abe5a623c022d62cde7c614ed4c371ebabeaf49b0a7a8bbbb7588b2928099c' "$snapshot_root/packaged-proof.py" | shasum -a 256 -c -
python3.14 "$snapshot_root/packaged-proof.py" "$snapshot_jdk/bin/java" "$snapshot_exec"
```

The bounded runner requires one class for CLI and verifier, one PropertiesLauncher, one existing root PaC, no alias and unchanged Boot Start-Class. It checks PS evidence artifactDigest against archive bytes and reports SHA separately from raw PaC. The real positive invocation is equivalent to the following, with a private staged directory populated from the validated vectors. All options are explicitly supplied.

```sh
"$snapshot_jdk/bin/java" \
  -Dloader.main=top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli \
  -cp "$snapshot_exec" org.springframework.boot.loader.launch.PropertiesLauncher \
  compare-process-snapshot --root "$SNAPSHOT_PRIVATE_ROOT" --candidate candidate.json \
  --signature candidate.sig.json --trust release-trust.json \
  --expect-candidate-sha256 0b2c53ee4d2ca2489f8780627f335cfee2b8ce72f9373dff2dabe120dd93f27c \
  --expect-trust-sha256 b5597e82a6a47af5a043029fe9e9eca7a67eb7994934a069472c510598703972 \
  --tenant 20 --channel expense --process-snapshot published.json \
  --expect-process-snapshot-sha256 75b574667c3dfe490fea92aac1f4ae89578e26d1ff02403361740a2acaf653fc
```

| Invocation | Exact outcome |
| --- | --- |
| Match and re-run | Exit 0 twice, exact expected output raw SHA 11ba30acfa267bed3aef4472ceaca9c467a3139802511b12dc0edc47a6836551, empty stderr |
| Wrong raw pin with same bytes | Exit 65, empty stdout, TIANJI_RELEASE_PROCESS_SNAPSHOT_DIGEST_MISMATCH |
| Correct pinned semantic drift | Exit 65, empty stdout, TIANJI_RELEASE_PROCESS_SNAPSHOT_MISMATCH |
| Correct pinned malformed DTO | Exit 65, empty stdout, TIANJI_RELEASE_PROCESS_SNAPSHOT_INVALID |
| Wrong signer with original snapshot | Exit 65, empty stdout, TIANJI_RELEASE_SIGNATURE_MISMATCH |
| Absent snapshot with valid pin | Exit 66, empty stdout, TIANJI_RELEASE_PROCESS_SNAPSHOT_IO_UNAVAILABLE |
| Missing two snapshot flags | Exit 64, empty stdout, TIANJI_RELEASE_PROCESS_SNAPSHOT_USAGE |
| Original verify-signature | Exit 0, exact frozen A2 stdout SHA d694f50e1dfd994f1b156fdbb524309db073020db573958208959019af2dbc2f, empty stderr |

All error lines include one LF. The vectors are synthetic public material. Neither snapshot nor expected proof is generated from the code under test. The script checks literal pins and never uses network or the API. Preserve script hash and package evidence in File 4 without committing the runner or public fixtures.

S1 under same repository checks strict validators against archived Spec and Plan, then exact staging paths. Compare SQL bytes with the locked tree and compare PaC content with the pinned prior proof, allowing only current artifact.commit and artifact.buildId to differ.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git add -- \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java \
  docs/egon/evidence/2026-11-05-platform-release-process-snapshot.md \
  docs/egon/spec/2026-11-05-21-50-platform-release-process-snapshot.md \
  docs/egon/plan/2026-11-05-21-51-platform-release-process-snapshot.md
git diff --cached --check
git diff --cached --name-status
```

The immutable-path command must have empty output. The staged diff must have the six paths and operations in §5, with reviewed lock bytes and untouched unrelated work. Commit only after all gates, then check exact commit path list and clean status.

## 9. Migration, Compatibility, Rollout, and Rollback

No database, schema, runtime flag or deploy change. Existing A1/A2 byte behavior and A2 permission pending list remain. Explicit opt-in at invocation. Revert one focused commit if needed.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/9 | Step 1 | 1–3 | G0/R1/G1/P1 |
| REQ-002 | §4/6.3/9 | Step 1 | 1/2 | G1/P1 |
| REQ-003 | §4/7/9 | Step 1 | 1/2 | G1/P1 |
| REQ-004 | §4/9/14 | Step 1 | 1–6 | G1/P1/S1 |
| REQ-005 | Effective A1/A2 §4 | Step 1 | 1–3 | G0/G1/P1 |
| REQ-006 | Effective A1 §4 | Step 1 | 1/4–6 | G0/G1/S1 |

## 11. Risks, Blockers, and User Decisions

| Risk / decision | Handling |
| --- | --- |
| Local DTO may be stale or fabricated | Pin fixes local bytes only, retains offline state and all original pendingAuthority. Owner authenticity requires a different reviewed boundary. |
| Wrong proof jar or vector | Require current package evidence and literal pinned synthetic vector, then verify exact stdout. |
| Production review/CAS authority unknown | Excluded from implementation and proof. No trust lookup, review command or activation. |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

Current source delivers independently runnable verification of candidate and supplied Process materials under T4-01. Formal review, activation and all live authority remain separate owner work.

### 12.2 Spec consistency

Exactly ten CLI pairs, strict DTO/time, full set compare, same four pending items and fixed diagnostics.

### 12.3 Repository executability

Exact existing source reactor, no new dependency; independent bounded PS exec CLI command with literal pins and explicit Start-Class override.

### 12.4 Test and release completeness

G0 and R1 before production edits, G1 finite, P1 real package, S1 exact scope. Review verdict describes design readiness only.

### 12.5 Blocking Manual Check

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

### 12.6 Final verdict

PASS — Ready for user review
