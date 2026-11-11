# T4-01 A5 治理 START 与原发布来源实施计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-11-15-29-governed-process-start.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-11 15:29 Asia/Shanghai |
| Updated | 2026-11-11 15:29 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | 治理 START、原发布来源、同源事务及 V13 |
| Source Requirement | Master T4-01 |
| Baseline Revision | e3e5603790b6e5ef51d0ec2f4583286463ad1ae8 |
| Implements Spec | [A5 Spec](../spec/2026-11-11-15-28-governed-process-start.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-11 15:28 Asia/Shanghai SHA-256 6b0d0e5f5a12b79badb0447b13508356ced3785a85cd5e1bf5a6a73202ec57a1 |
| Effective Specs | [A5 Spec](../spec/2026-11-11-15-28-governed-process-start.md) |
| Depends On Plans | [A4](./2026-11-11-15-09-platform-release-activation.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

在显式装配的 Process 内部入口中，通过当前渠道权限与独立 Worker 证据选择 A4 修订。原 START 回执、原生实例与 V13 来源在同一 ProcessDB 事务提交。精确重试读取原修订和原实例。

## 2. Target Spec and Effective Design

[A5 Spec](../spec/2026-11-11-15-28-governed-process-start.md) §§4/7/10/11/14 governs the exact owner transaction and original replay.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7 | Exact current admission, pointer and route. |
| REQ-002 | §7/11 | Native effect, ordinary receipt and provenance commit together. |
| REQ-003 | §7/10 | Retry pins original activation and exact owner. |
| REQ-004 | §8/16 | One additive V13 and existing entry compatibility. |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Lock the baseline and exact reviewed paths. Prepare native receipt tests, then V13, shared fence, least-privilege readiness and internal START. Run exact finite and package gates before staging.

### 4.2 Test-first strategy

Remove only the post-provenance admission-window recheck. The test must report the missing AccessDeniedException because the late writes would otherwise return. Restore the candidate before G1.

### 4.3 Sequential and parallel boundaries

Strict documents, R1, G1, P1 and S1 complete before the source commit. I1 uses real authorized authorities and isolated PostgreSQL.

### 4.4 Commit boundaries

One focused commit for consuming the A4 pointer and preserving its original START provenance in V13.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Native START and owner identity | Required §7 | Existing receipt and execution service | Reuse current transaction and native flow | Add explicit governed overload and exact proof. |
| Provenance | Required §11 | Existing START receipt contract and activation history | Existing receipt JSON serves DECISION only | Add one V13 source table. |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Governed START | REQ-001–003 | R1/G1 | 1–9 | A4, Flowable and ordinary receipt |
| Scoped evidence | REQ-004 | P1/S1 | 2–12 | Exact source and artifacts |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Ownership | Flowable and Process receipt | Same primary transaction |
| Independent authority | A4 current Worker fingerprint | Reuse canonical binding and caller scope |
| Schema | Current Flyway sequence and verifier | One version with read-only V12 and append-only V13 readiness |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Native START | ProcessExecutionService | EVD-002 | Reuse ordinary write core | Preserve legacy constructor | Add internal entry | G1 |
| Fence | PlatformReleaseActivationStore | EVD-001 | Same tenant namespace | Share immutable lock helper | No dependency | G1 |
| Provenance | Existing receipt payload, V12 | EVD-001/003 | Receipt shape is fixed | One same-source row and two FKs | V13 | P1 |

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
| Rule 1 | §6.2 | EVD-001–005 | Files 1–9 | 内部命令、Grant、Worker 证据、Store 和只读指针值对象。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 2 | §6.2 | EVD-001–005 | Files 1–9 | 在调用、事务和回执边界校验完整 owner、指针、请求摘要和有效期。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 3 | §6.2 | EVD-001–005 | Files 1–9 | 复用既有回执，附加不可变来源。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 4 | §6.2 | EVD-001–005 | Files 1–9 | No affected component in this Step. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 5 | §6.2 | EVD-001–005 | Files 1–9 | 现有 JDK、JDBC 及摘要工具。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 6 | §6.2 | EVD-001–005 | Files 1–9 | 普通 START 沿用持久类型及摘要，内部来源使用固定 START。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 7 | §6.2 | EVD-001–005 | Files 1–9 | No affected component in this Step. | R1/G1/P1/S1 | Step 1 | N/A |
| Rule 9 | §6.2 | EVD-001–005 | Files 1–9 | 同库 Transaction Template 和现有 Store Adapter。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 10 | §6.2 | EVD-001–005 | Files 1–9 | UTC 主库时间，授权和 Worker 观察窗口右界排除。 | R1/G1/P1/S1 | Step 1 | PASS |
| Rule 11 | §6.2 | EVD-001–005 | Files 1–9 | 按主计划与用户 Flyway 规则使用现有同源 JDBC 和独立 V13。追加来源使用原 START 物理键与 V12 外键。 | R1/G1/P1/S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | createdAt, observedAt, validUntil | Occurred instant | java.time.Instant | UTC instant | Internal | Not applicable: absolute | Not applicable: no display | Lower inclusive and upper exclusive | Primary Process DB clock, microseconds | Existing receipt time and projection | Step 1 TEST-001/002 |
| TIME-002 | START grant and Worker proof lifetime | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Maximum 30 seconds | Checked against the primary Process clock | Additive for governed START | Step 1 TEST-001/002 |

## 5. Change File Tree

| Operation | Exact path | Responsibility |
| --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java` | 有限 START 与事务回归 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V13__add_governed_process_start_provenance.sql` | 唯一 V13 追加 START 来源 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java` | V13、V12 读取和物理权限就绪 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java` | 新旧角色和迁移前缀回归 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseTenantFence.java` | 共享租户事务锁键 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java` | 复用既有排他锁语义 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java` | 复用实际 Worker 绑定摘要 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java` | 读取指针和写入同源来源 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java` | 激活指针接入内部新启动 |
| CREATE | `docs/egon/spec/2026-11-11-15-28-governed-process-start.md` | 明确运行行为与兼容范围 |
| CREATE | `docs/egon/plan/2026-11-11-15-29-governed-process-start.md` | 冻结范围和执行门禁 |
| CREATE | `docs/egon/evidence/2026-11-11-governed-process-start.md` | 记录实际源码与构建证据 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline e3e5603790b6e5ef51d0ec2f4583286463ad1ae8. Existing offline JDK 25 profile and finite selectors. V13 is the only new migration.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Implement governed START with original revision provenance

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: A4 current pointer, ProcessDB receipts and Flowable.
- Baseline state: An active revision exists independently of explicit START.
- Observable outcome: New internal governed start consumes the exact pointer and replays the original revision across changes.
- End state: Same-source native START and provenance, independently verified authority and existing exact entry compatibility.
- Test-first gate: Required. R1 removes only final admission recheck.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java`

- Purpose: 有限 START 与事务回归。
- Symbols: GovernedProcessStartServiceTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 有限 START 与事务回归。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Use exact pointer and owned START receipts.
Assert native start, provenance append and stable replay.
Expire authority after writes and require rollback.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V13__add_governed_process_start_provenance.sql`

- Purpose: 唯一 V13 追加 START 来源。
- Symbols: V13__add_governed_process_start_provenance。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 唯一 V13 追加 START 来源。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Create one append-only provenance table.
Reference exact START receipt and selected activation revision.
Preserve the published receipt digest.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`

- Purpose: V13、V12 读取和物理权限就绪。
- Symbols: ProcessApiSchemaVerifier。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: V13、V12 读取和物理权限就绪。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Pin V13 resource and exact migration prefix.
Verify V12 pointer SELECT and V13 append privileges.
Keep existing V12 activation and older readers independent.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 4 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`

- Purpose: 新旧角色和迁移前缀回归。
- Symbols: ProcessApiSchemaVerifierTest。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 新旧角色和迁移前缀回归。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Exercise V12 read-only and V13 append-only catalogs.
Reject incorrect schema or permission drift.
Preserve old readiness with V13 installed.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseTenantFence.java`

- Purpose: 共享租户事务锁键。
- Symbols: PlatformReleaseTenantFence。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 共享租户事务锁键。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Construct the exact namespaced tenant lock.
Use the exclusive side for activation.
Use the shared side for governed START.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java`

- Purpose: 复用既有排他锁语义。
- Symbols: PlatformReleaseActivationStore。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 复用既有排他锁语义。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Replace the private inline lock with the common fence.
Keep the same tenant key and PostgreSQL lock type.
Preserve independent activation ordering.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java`

- Purpose: 复用实际 Worker 绑定摘要。
- Symbols: PlatformReleaseActivationService。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 复用实际 Worker 绑定摘要。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Expose exact canonical binding calculation internally to Process START.
Bind the complete effective registration and bridge identity.
Retain exact activation fingerprints.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java`

- Purpose: 读取指针和写入同源来源。
- Symbols: GovernedProcessStartStore。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 读取指针和写入同源来源。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Read exact owner provenance and immutable pointer.
Append only from matching APPLIED START receipt and activation.
Compare actual database clock and scope.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`

- Purpose: 激活指针接入内部新启动。
- Symbols: ProcessExecutionService。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 激活指针接入内部新启动。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Authorize exact scoped caller and obtain current Worker proof.
Lock tenant and pin active revision for the native start.
Commit receipt and provenance together or replay the original owner receipt.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 10 — `CREATE docs/egon/spec/2026-11-11-15-28-governed-process-start.md`

- Purpose: 明确运行行为与兼容范围。
- Symbols: 2026-11-11-15-28-governed-process-start。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 明确运行行为与兼容范围。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record exact old and new command behavior.
Define owned historical replay and shared fence.
Trace source and runtime criteria.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 11 — `CREATE docs/egon/plan/2026-11-11-15-29-governed-process-start.md`

- Purpose: 冻结范围和执行门禁。
- Symbols: 2026-11-11-15-29-governed-process-start。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 冻结范围和执行门禁。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Lock source and Spec hashes.
Require one typed counterexample and owning clean package.
Record actual scope and independent I1.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.

#### File 12 — `CREATE docs/egon/evidence/2026-11-11-governed-process-start.md`

- Purpose: 记录实际源码与构建证据。
- Symbols: 2026-11-11-governed-process-start。
- Repository evidence: EVD-001–005.
- Dependencies and consumers: Existing Process owner, native START and release governance.
- Why now: Consume the pinned activation source for an exact START.
- Contract/signature changes: 记录实际源码与构建证据。
- Input/output and state mapping: Own the reviewed command or evidence in Spec §7/11.
- Error and edge behavior: Reject missing owner, unavailable evidence or conflicting state.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001 and MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Record the finite owned commands and package checks.
Confirm older SQL and original explicit entrypoint.
Record operator proof still due.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Preserve existing START receipt and ownership semantics.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/P1/S1.
- Expected result: Typed RED, owning finite tests, clean package and exact scope.
- Failure returns to: The affected authority, transaction or catalog check.
- Completion criteria: Pass source gates and record independent real I1 status.
- Rollback: Use the existing constructor. Use V13-aware source once the isolated DB is migrated.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V13__add_governed_process_start_provenance.sql`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseTenantFence.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseActivationService.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/GovernedProcessStartStore.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessExecutionService.java`, `docs/egon/spec/2026-11-11-15-28-governed-process-start.md`, `docs/egon/plan/2026-11-11-15-29-governed-process-start.md`, `docs/egon/evidence/2026-11-11-governed-process-start.md`.
- Commit: feat(process): pin governed starts to activation revisions.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | GovernedProcessStartServiceTest#expired_admission_after_provenance_rolls_back_native_start_and_receipt | Remove only the final admission check. Exact missing AccessDeniedException. |
| G1 | Owning finite Process suites | Governed START, activation, receipts and schema pass. |
| P1 | Clean offline verify | Actual V13 and classes, compatible PaC and existing migration bytes. |
| S1 | Strict Spec, Plan and exact paths | Scope covers the single reviewed internal operation. |
| I1 | Independent isolated PostgreSQL/Flowable | Actual runtime authority, scoped roles, current pointer selection, rollback/replay and concurrency. |

```sh
a5_base=e3e5603790b6e5ef51d0ec2f4583286463ad1ae8
a5_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
a5_core=GovernedProcessStartServiceTest,PlatformReleaseActivationServiceTest,PlatformReleaseActivationStoreTest,ProcessApiSchemaVerifierTest,ProcessReleaseServiceTest
test "$(git rev-parse HEAD)" = "$a5_base"
"$a5_runner" "$a5_core"
STELLAR_VERIFY_GOAL=verify "$a5_runner" "$a5_core" clean -Ppermission-producer "-Dpermission.commit=$a5_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-governed-process-start
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-11-15-28-governed-process-start.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-11-15-29-governed-process-start.md
git diff --check
```

Use the exact single source mutation for R1 and restore the reviewed candidate before G1. Verify original native receipt format and actual archive resources.

## 9. Migration, Compatibility, Rollout, and Rollback

Only V13 adds provenance. Existing activation readiness stays independent of V13 grants. Governed capability requires an explicit constructor and independently authorized owner adapter.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Source files | R1/G1/I1 |
| REQ-002 | Step 1 | Source files | R1/G1/P1/I1 |
| REQ-003 | Step 1 | Source files | G1/I1 |
| REQ-004 | Step 1 | All files | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Actual authority providers, live ProcessDB, and public channel policy are independent deployment work.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/16 | 同一 ProcessDB、Flowable 和事务管理器。 | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/16 | 沿用 Process START 回执、已有 digest、VerifiedProcessModels 和 A4 锁键。 | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/16 | 现有 Java、JDBC、Flowable 和 JUnit 足够。 | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/16 | 内部命令、Grant、Worker 证据、Store 和只读指针值对象。 | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/16 | 在调用、事务和回执边界校验完整 owner、指针、请求摘要和有效期。 | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/16 | 复用既有回执，附加不可变来源。 | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/16 | 本步骤的显式技术装配未涉及该边界。 | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/16 | 本步骤的显式技术装配未涉及该边界。 | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/16 | 本步骤的显式技术装配未涉及该边界。 | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/16 | 现有 JDK、JDBC 及摘要工具。 | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/16 | 普通 START 沿用持久类型及摘要，内部来源使用固定 START。 | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/16 | UTC 主库时间，授权和 Worker 观察窗口右界排除。 | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/16 | 本步骤的显式技术装配未涉及该边界。 | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/16 | 同库 Transaction Template 和现有 Store Adapter。 | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/16 | 单一 V13、内部入口及限定测试。 | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/16 | 精确反证、定向测试、干净包和旧合约检查。 | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/16 | 源码行为及验证范围明确。真实主库、权限和上线后入口由独立验收覆盖。 | None |

### 12.6 Final verdict

PASS — Ready for user review
