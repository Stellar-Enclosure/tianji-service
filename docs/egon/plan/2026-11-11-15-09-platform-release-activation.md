# T4-01 A4 签名发布激活和回滚实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-09-platform-release-activation.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:09 Asia/Shanghai |
| Updated | 2026-11-11 15:09 Asia/Shanghai |
| Owner | Tianji Process release governance |
| Repository | tianji-service |
| Scope | PS 的激活与回滚命令、独立发布证据、同库追加历史和 V12 |
| Source Requirement | Master T4-01 |
| Baseline Revision | 44d9dd530b278eac16a411faa86b9ca81fcbc736 |
| Implements Spec | [A4 Spec](../spec/2026-11-11-15-08-platform-release-activation.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:08 Asia/Shanghai SHA-256 8e3ea59ad909e712d855e4fd9c9fd37f4925f0effe7436e457490f0eff9acd65 |
| Effective Specs | [A4 Spec](../spec/2026-11-11-15-08-platform-release-activation.md) |
| Depends On Plans | [A3](./2026-11-06-07-47-platform-release-candidate-review.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

通过实际 A2 签名、当前 A3 审阅、原生 Process 发布和独立部署证据后，按期望修订激活或回滚租户渠道。当前指针由最新追加修订表示，治理 START 消费由后续步骤接入。

## 2. Target Spec and Effective Design

[A4 Spec](../spec/2026-11-11-15-08-platform-release-activation.md) §§4/7/10/11/14 governs this Step.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7 | Exact accepted signature, PS native release and link. |
| REQ-002 | §7/10 | Independent short-lived exact authorization and deployment. |
| REQ-003 | §7/11 | Tenant fence, expected revision, exact rollback/replay and historical routing. |
| REQ-004 | §8/14/16 | Exact V12 and prior compatibility. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock source and read A3. Add tests, physical schema contract, typed revalidation, JDBC arbitration and command. Run typed RED, restore GREEN and verify actual archives.

### 4.2 Test-first strategy

Remove only the activation service comparison of signed and independently observed deployment manifests. The different Agent content test must fail because the call incorrectly reaches the store. Restore the candidate before owning tests.

### 4.3 Sequential and parallel boundaries

Strict documents, R1, G1, P1, exact stage and commit. Independent I1 operates only after suitable authorities and isolated storage are configured.

### 4.4 Commit boundaries

One commit for the internal activation and rollback operation, its single V12 and focused tests.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Current owner revalidation | Required §7 | A2/A3 and VerifiedProcessModels | Current release snapshot and signed bytes | Extend existing exact flow. |
| Atomic revision and historical route continuity | Required §11 | V11 accepted fact only | Reuse JDBC and existing readiness | Add one audit table with scope key and route index. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Authority and CAS | REQ-001–003 | R1/G1 | 1–12 | A2/A3, native release and PS storage |
| Compatibility | REQ-004 | P1/S1 | 3–15 | Actual artifacts and reviewed files |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Technical storage | V11 and owning Flowable Source | Keep same source and ordinary JDBC. |
| Runtime identity | Existing verified ProcessActor and AgentDispatchBinding | Explicit current authority. |
| Time | PS clock and A3 AdmissionWindow | Half-open windows and post-write check. |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Signed candidate | A2/A3 | EVD-001/002 | Current proof extends existing accepted candidate | Revalidate exact original and reuse A2 signature | Internal Java result | G1 |
| Model checks | ProcessReleaseService and VerifiedProcessModels | EVD-003 | Native same owner model bytes available | Reuse definitions and validators | Owner snapshot method | G1 |
| Revision write | A3 Store and PS schema verifier | EVD-005 | Requires history and route fence | Follow current JDBC Adapter | One table and adapter | P1 |

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
| Rule 1 | §6.2 | EVD-001–005 | Files 1–12 | 使用现有 release 包，技术 Service/Store 和不可变请求及回执类型。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–005 | Files 1–12 | 验证命令、主体、签名、当前权威、精确 owner 和持久输入。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–005 | Files 1–12 | 使用不可变值对象与现有 JDBC 记录映射，未添加 ORM 实体。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–005 | Files 1–12 | 显式技术构造，无 Spring Bean、日志或配置变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–005 | Files 1–12 | 使用现有 JDK、Jackson、JDBC、Flowable 与 JUnit。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–005 | Files 1–12 | 沿用 A2 Jackson 结构，数据库操作码明确使用 ACTIVATE 和 ROLLBACK。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2 | EVD-001–005 | Files 1–12 | 显式技术构造，无 Spring Bean、日志或配置变更。 | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–005 | Files 1–12 | 复用现有 JDBC Adapter，治理命令编排独立 owner 证据并将原子操作交给存储。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–005 | Files 1–12 | 使用 Instant、Duration 和数据库 timestamptz(6)，窗口右界排除。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–005 | Files 1–12 | 用户 AGENTS 的 Flyway 规则及主计划的同 owner 迁移边界适用。沿用 V11 同源普通 JDBC。V12 是一张生命周期不可删除的技术审计表，使用物理唯一键。 | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | activatedAt, observedAt, validUntil | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute | Not applicable: no display | Lower inclusive, upper exclusive, no local zone | Process database clock, microseconds | Existing signature and review stay valid | Step 1 TEST-001/002 |
| TIME-002 | Independent activation and deployment proof lifetime | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | At most 30 seconds | Measured using the Process database instant | Additive internal constraint | Step 1 TEST-001/002 |

## 5. Change File Tree

| Operation | Exact path | Responsibility |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationServiceTest.java` | 真实 A2 向量与激活准入反证 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStoreTest.java` | 事务次序、CAS 与回滚的有限验证 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V12__add_platform_release_activations.sql` | 唯一 V12 定义追加式修订记录 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java` | 独立 V12 就绪及历史识别 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java` | 既有 V11 与 V12 目录证据 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java` | 签名验证后导出类型化依赖证据 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java` | Process owner 核对原生模型 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java` | 原生模型和部署链接测试 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java` | 候选当前权威测试 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java` | 对已审阅候选重新验签 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java` | JDBC 事务仲裁指针修订 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java` | 现有 PS 内部治理命令 |
| CREATE | `docs/egon/spec/2026-11-11-15-08-platform-release-activation.md` | 源码与操作者边界 |
| CREATE | `docs/egon/plan/2026-11-11-15-09-platform-release-activation.md` | 基线与验证顺序 |
| CREATE | `docs/egon/evidence/2026-11-11-platform-release-activation.md` | 可审阅的执行证据 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 44d9dd530b278eac16a411faa86b9ca81fcbc736. Use local offline Java 25 settings. New V12 is the only added SQL. Existing Postgres tests compile and are excluded from finite selectors.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Implement PS internal activation and rollback

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Current A3 candidate review, A2 signature, existing Process and Worker ownership.
- Baseline state: A3 persists accepted candidates. No activation revision exists.
- Observable outcome: Scoped command stores one monotonic revision and can explicitly restore the recorded previous candidate.
- End state: Reviewed activation, current typed evidence, one SQL migration and finite regressions.
- Test-first gate: Required. R1 admits mismatched deployment when exact equality is removed.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationServiceTest.java`

- Purpose: 真实 A2 向量与激活准入反证。
- Symbols: PlatformReleaseActivationServiceTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 核对精确签名声明、权限、发布、Worker、复读和时间窗。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Verify actual signed vector.
Reject mismatched deployment, stale permission and an incompatible bridge.
Return the original receipt for exact authorized replay.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStoreTest.java`

- Purpose: 事务次序、CAS 与回滚的有限验证。
- Symbols: PlatformReleaseActivationStoreTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 核对 READ COMMITTED 锁、前任、竞争、重放和到期回滚。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Acquire tenant lock in READ COMMITTED.
Reject stale revision, wrong predecessor, route conflicts and expired replay.
Verify rollback and no duplicate insertion.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V12__add_platform_release_activations.sql`

- Purpose: 唯一 V12 定义追加式修订记录。
- Symbols: V12__add_platform_release_activations。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 添加一个表、历史外键与命令和路由索引。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Create the owning append-only table.
Bind target and predecessor to V11 within the same scope.
Index latest revision, historical command and route compatibility.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`

- Purpose: 独立 V12 就绪及历史识别。
- Symbols: ProcessApiSchemaVerifier。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 验证资源和历史摘要、目录合同与只追加角色。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Recognize and pin V12 in the exact Flyway sequence.
Keep readiness for V10 and V11.
Verify table, exact C collation, indexes, constraints and restricted role.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`

- Purpose: 既有 V11 与 V12 目录证据。
- Symbols: ProcessApiSchemaVerifierTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 核对旧就绪路径、权限与 V12 精确检查。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Check the known V12 catalog and independent old readiness.
Reject wrong hash, catalog, role or schema collation.
Keep prior schema test expectations.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`

- Purpose: 签名验证后导出类型化依赖证据。
- Symbols: PlatformReleaseSignatureVerifier。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 复用已校验字段，保留原 CLI 结果字节。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Validate the existing signed input.
Decode verified Agent publication, contracts, link and package hashes.
Preserve the existing CLI payload bytes.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`

- Purpose: Process owner 核对原生模型。
- Symbols: ProcessReleaseService。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 同一只读快照校验 BPMN、DMN、CMMN、清单及 Worker 节点。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Open one same-source repeatable primary snapshot.
Verify the native BPMN, DMN and CMMN artifacts and effective inputs.
Match the exact signed manifest and Worker link.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`

- Purpose: 原生模型和部署链接测试。
- Symbols: ProcessReleaseServiceTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 准备原生 BPMN、DMN、CMMN 证据，分别核对模型、链接、清单和主库。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Create a release fixture with three actual native model validators.
Supply exact native model resources and compare the manifest.
Exercise mismatch, stale snapshot and primary refusal.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java`

- Purpose: 候选当前权威测试。
- Symbols: PlatformReleaseCandidateReviewServiceTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 要求已接受候选、有效信任与两项独立读取权限。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Create an accepted candidate and bounded A3 trust.
Require exact current candidate read and Process read permissions.
Reject a rejected review and changed trust before activation.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 10 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java`

- Purpose: 对已审阅候选重新验签。
- Symbols: PlatformReleaseCandidateReviewService。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 要求 ACCEPT、精确当前信任和独立的候选及 Process 读取权威。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Read one exact accepted historical candidate.
Reapply original A3 trust, grant and signed content checks.
Return native verification and the intersected admission window.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 11 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java`

- Purpose: JDBC 事务仲裁指针修订。
- Symbols: PlatformReleaseActivationStore。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 获取租户锁，查历史和当前，验证 revision、前任、路由、时间后追加。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Acquire the transaction-scoped advisory tenant lock.
Check current revision, exact predecessor, Worker route and committed ACCEPT.
Insert one row and recheck the database clock.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 12 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java`

- Purpose: 现有 PS 内部治理命令。
- Symbols: PlatformReleaseActivationService。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 重新核验已接受候选，验证部署与 Worker，取得短授权窗口后调用存储。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Authenticate the exact USER and command grant.
Check independent deployments and compute the effective Worker binding.
Append through the owning Store after complete current evidence.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 13 — `CREATE docs/egon/spec/2026-11-11-15-08-platform-release-activation.md`

- Purpose: 源码与操作者边界。
- Symbols: 2026-11-11-15-08-platform-release-activation。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 列明审阅、授权、独立 owner 证据、CAS 和迁移约束。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Identify the immutable A2 and A3 source contracts.
Specify exact revision, deployment and rollback semantics.
Separate runtime acceptance from scoped source evidence.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 14 — `CREATE docs/egon/plan/2026-11-11-15-09-platform-release-activation.md`

- Purpose: 基线与验证顺序。
- Symbols: 2026-11-11-15-09-platform-release-activation。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 锁定实际父提交、范围和有限门禁，记录实库验收条件。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Pin repository baseline and Spec digest.
Record scoped typed RED and owning finite selectors.
Verify the exact staged paths and original migration bytes.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.

#### File 15 — `CREATE docs/egon/evidence/2026-11-11-platform-release-activation.md`

- Purpose: 可审阅的执行证据。
- Symbols: 2026-11-11-platform-release-activation。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: PS release governance and owning acceptance.
- Why now: Enable the exact A4 operation and its evidence.
- Contract/signature changes: 记录有限回归、实际包、原有迁移核验和实库状态。
- Input/output and state mapping: Own the exact source or evidence specified in §7 and §11.
- Error and edge behavior: Fail the owning check for an unavailable, wrong or stale authority.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Capture the actual finite XML counts and package assertions.
Record the reproduced typed negative counterexample.
List the isolated runtime cases left for operator acceptance.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope and signed owner dependencies exact.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/P1/S1.
- Expected result: Exact typed RED, all selected finite tests green, real package compatibility and fifteen paths.
- Failure returns to: The owning source or validation file.
- Completion criteria: All scoped source gates pass. Record independent I1 status.
- Rollback: Before migration, revert the focused commit. A migrated environment requires V12-aware readiness and the new scope stays explicitly unassembled.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStoreTest.java`, `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V12__add_platform_release_activations.sql`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java`, `docs/egon/spec/2026-11-11-15-08-platform-release-activation.md`, `docs/egon/plan/2026-11-11-15-09-platform-release-activation.md`, `docs/egon/evidence/2026-11-11-platform-release-activation.md`.
- Commit: feat(process): persist CAS release activation and rollback.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | PlatformReleaseActivationServiceTest#rejects_a_current_deployment_with_different_agent_content_before_writing | Remove only exact manifest equality. Expected AccessDeniedException absent. |
| G1 | Owning reactor finite PS suites | Activation service/store, candidate service/store, schema, owning release and A2 CLI suites pass. |
| P1 | Clean verify permission producer | Same tests, actual V12 PS archive, byte-exact original A2 CLI proof, unchanged semantic PaC and prior SQL. |
| S1 | Strict docs and stage | Fifteen exact scoped paths. |
| I1 | Independent isolated owner acceptance | Actual V12, roles, native models, current authorities, two writers, and rollback. |

```sh
a4_base=44d9dd530b278eac16a411faa86b9ca81fcbc736
a4_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
a4_core=PlatformReleaseActivationServiceTest,PlatformReleaseActivationStoreTest,PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseCandidateReviewStoreTest,ProcessApiSchemaVerifierTest,ProcessReleaseServiceTest
test "$(git rev-parse HEAD)" = "$a4_base"
"$a4_runner" "$a4_core"
STELLAR_VERIFY_GOAL=verify "$a4_runner" "$a4_core" clean -Ppermission-producer "-Dpermission.commit=$a4_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-platform-release-activation
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-08-platform-release-activation.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-09-platform-release-activation.md
git diff --check
```

Apply the single R1 mutation, confirm the expected typed assertion, then restore the exact source. Owner authorities and real database are independent I1.

## 9. Migration, Compatibility, Rollout, and Rollback

Keep all older migration bytes. V12 adds one technical table and exact role/schema validation. Use explicit assembly and an owning build which understands the new version before isolated migration.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1, 6–10, 12 | G1/I1 |
| REQ-002 | Step 1 | 1, 6–10, 12 | R1/G1/I1 |
| REQ-003 | Step 1 | 1–3, 11–12 | G1/I1 |
| REQ-004 | Step 1 | 3–6, 13–15 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Current deployed authorities, new-start consumption and real PostgreSQL are independently verified. The tenant-wide lock is needed because one Worker route can be used in multiple channels.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/16 | 同一 PS 数据源、Engine 和事务管理器。A3 与现有 Process 读取路径是直接依赖。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/16 | 复用 A2 验签、A3 当前信任及授权、VerifiedProcessModels 与现有 schema verifier。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/16 | 使用仓内现有依赖。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/16 | release 包的 Service、Store 和不可变证据类型。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/16 | 精确回显授权、发布、签名和 Worker 绑定，检查 revision 和旧路由。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/16 | 签名及权限数据是不可变值对象，追加表记录审计事实。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/16 | 显式技术装配与 Java 内部合同不涉及该边界。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/16 | 显式技术装配与 Java 内部合同不涉及该边界。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/16 | 显式技术装配与 Java 内部合同不涉及该边界。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/16 | JDK、Jackson、JDBC 和现有项目摘要实现。 | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/16 | A2 输出字节不变，内部固定操作码。 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/16 | 数据库时钟、UTC 发生时间、半开短授权窗口。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/16 | 显式技术装配与 Java 内部合同不涉及该边界。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/16 | 服务编排和 JDBC Adapter，底层保持同库原子状态。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/16 | 精确文件、单一新增 V12，历史资源原字节。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/16 | 定向负向变体、有限回归、干净包和源码规则。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/16 | 限本源码步骤。生产权威、治理 START 消费和真实 PostgreSQL 另有证据门禁。 | None |

### 12.6 Final verdict

PASS — Ready for user review
