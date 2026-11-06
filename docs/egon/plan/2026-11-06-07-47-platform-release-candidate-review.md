# T4-01 A3 发布候选提交和独立审阅实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-07-47-platform-release-candidate-review.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:47 Asia/Shanghai |
| Updated | 2026-11-06 07:47 Asia/Shanghai |
| Owner | 天机发布治理决策方 |
| Repository | tianji-service |
| Scope | PS 技术包，九个源码/测试/SQL 路径及三份精确文档，一次提交 |
| Source Requirement | 工作区主计划 §19 T4-01，不可变候选、发布审核与唯一新迁移 |
| Baseline Revision | 7fd8e37057686d1e9fc7d3544668dccd0bc2efcf |
| Implements Spec | [A3 候选审阅](../spec/2026-11-06-07-46-platform-release-candidate-review.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:46 Asia/Shanghai SHA-256 c7f108fb748836d82772b5610217b0928f916dde93d0a690db4ef2a1ec73dee6 |
| Effective Specs | [A3](../spec/2026-11-06-07-46-platform-release-candidate-review.md) · [A2 §9/10](../spec/2026-11-05-17-30-platform-release-detached-signature.md#9-interface-definitions) · [PS 快照 §9/10](../spec/2026-11-05-21-50-platform-release-process-snapshot.md#9-interface-definitions) |
| Depends On Plans | [A2 快照对照](2026-11-05-21-51-platform-release-process-snapshot.md#7-ordered-file-by-file-implementation-steps) · [Recovery V10](2026-11-05-14-27-process-worker-recovery.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

PS 在当前既有拥有者库中记录经核验的不可变签名候选及至多一次独立审阅。通过 typed A2、PS owner read、两张审计表和同源 JDBC Adapter 实现，保持默认未装配。

## 2. Target Spec and Effective Design

### 2.1 Primary target

A3 §4、§7、§9、§11、§14。当前 Review 草稿可审查，正式落地前重锁基线、文件、内容摘要和精确决策。没有应用、迁移、真实 IAM 或部署步骤。

### 2.2 Effective Spec set

| Effective Spec | Scope | Why retained |
| --- | --- | --- |
| A3 | REQ-001–005 and full source scope | Current authoritative design proposal |
| A2 §9/10 | Existing canonical signature/clock/trust and proof | Same cryptography and unchanged CLI bytes |
| PS snapshot §9/10 | Existing isolated DTO comparison | The runtime uses actual owning read while existing offline path retains its contract |

### 2.3 Superseded or excluded content

None. Signed candidate and terminal review are one change. Cross-owner published dependency checks, global revocation, activation and rollback are separate.

## 3. Effective Requirements and Acceptance

| Requirement | Exact Spec source | Observable acceptance | Scope |
| --- | --- | --- | --- |
| REQ-001 | A3 §4/9 and A2 §9/10 | Verified SERVICE + scoped Ed25519 proof only | A2 typed verifier, admission |
| REQ-002 | A3 §4/7/9 | Independent owner-read grant, exact current release/models | Admission |
| REQ-003 | A3 §4/9/10 | Distinct verified USER, explicit independence, trust continuity, one terminal decision | Admission and audit |
| REQ-004 | A3 §4/7/11 | Exact history, second statement, transaction/clock proof | JDBC adapter and tests |
| REQ-005 | A3 §4/11/16 | One V11, precise readiness, V10 compatible, no auto setup | Schema and static gate |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Start with regression proof and exact file lock. Prepare focused behavioral expectations, extract typed A2, introduce one V11 and explicit history support, then same-source store and scoped service. Validate exact JDBC and owner boundaries, stage only Step paths and make one commit after gates.

### 4.2 Test-first strategy

Scratch U1 is observational GREEN only. On the reviewed baseline first run existing A2 and schema tests. In the isolated validation copy, omit only the post-insert window recheck and run the exact Store expiry method. An unresolved type, bad fixture or dependency error does not count as RED. Restore the frozen reviewed bytes and rerun all four finite selectors.

### 4.3 Sequential and parallel boundaries

V11 content is frozen before the SHA/checksum readiness change. A2 extraction and actual A3 consumer form one compilation boundary. Provider and Process release read calls complete before acquiring the audit transaction. The entire reviewed file set commits as one unit.

### 4.4 Commit boundaries

One PS repository commit for candidate admission and review. No other Step can start before completion. Activation uses a separate decision and migration.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Implementation handling |
| --- | --- | --- | --- |
| Typed A2 | Keep/expand §7 | PlatformReleaseSignatureVerifier already validates A1, Ed25519 and exact trust | Extract validation once, retain CLI wrapper |
| Independent admission | Add §9 | Current ProcessAccess/owner reads cannot establish candidate-specific trust or separation | Explicit current authority ports and exact PS lookup |
| Audit facts | Add §11 | Existing release/receipt ownership differs | Two append-only same-source tables and adapter |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| A2 and source gates | REQ-001/005 | G0, static scope | Existing exact A2/PS release and V10 | Step 1 |
| New V11/readiness | REQ-005 | R1 readiness; G1 catalog exactness | V10 prefix and frozen V11 | Step 1 |
| Admission/JDBC | REQ-001/002/003/004 | R1 admission; G1 store and service | Typed A2, exact PS read, V11 | Step 1 |
| Docs and evidence | REQ-001/002/003/004/005 | S1 exact tree and strict docs | Verified scope and explicit runtime boundary | Step 1 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture and pattern | PS release/store vertical and V9/V10 | A3 §6/7/13 | JDBC Adapter pins exact engine/source/TM and isolates SQL | G1/S1 |
| Scope and DTO values | ProcessActor and A2 signatures | A3 §9/10 | Closed immutable actor, scope, grant, bytes and receipts | R1/G1 |
| Time/transaction | Existing Process clock_timestamp and JdbcTemplate | A3 §6.3/11 | DB clock window, bounded statements, after-write rollback | G1 |
| Persistence and config | User AGENTS and V9/V10 profile | A3 DEC-003 | One new V11, existing external Flyway, default no runtime wiring | S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Signature | A2 validator | SignatureVerifier.verify and existing A2 fixture | Fits | Extract typed byte method and compare | No dependency | Step 1 MC-REUSE-001 |
| Owner | ProcessReleaseService | get/usesStorage | Fits owner only | Reuse exact read after scoped grant | New local service only | Step 1 MC-ARCH-001 |
| Storage | V9/V10 and existing verifier | ProcessApiSchemaVerifier and JDBC technical tables | Current ownership fits; missing governance tables | Same-source Adapter and one immutable V11 | One SQL; no dependency | Step 1 MC-SCOPE-001 |
| Authority | ProcessActor and current PS security | verified issuer and no release-review authority | Needs independent ownership | Require explicit trusted ports, no default implementation | Java-local types | Step 1 MC-VALID-001 |

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
| Rule 1 | §8/10 | Existing PS release types | Files 1–9 | Semantic command/receipt/value and adapter names | G1 | Step 1 | PASS |
| Rule 2 | §6.2/9 | A2 and PS immutable constructor profile | Files 1–9 | Closed scope, current actor, trust and bytes | R1/G1 | Step 1 | PASS |
| Rule 3 | §6.2/10 | PS receipts and row projection | Files 3/5/8/9 | Immutable values, copy arrays, explicit projections | G1 | Step 1 | PASS |
| Rule 4 | §6.2/15 | Explicit PS technical constructors | Files 8/9 | No Bean or raw log; record audit facts | S1 | Step 1 | PASS |
| Rule 5 | §6.1 | Existing JDK/JDBC/Jackson | Files 5/7–9 | Reuse current utilities and dependencies | G1/S1 | Step 1 | PASS |
| Rule 6 | §6.2/9/11 | A2 Jackson and PS DB strings | Files 5/6/8/9 | Preserve old proof and closed named state | G1 | Step 1 | PASS |
| Rule 7 | §3/16 | Existing profile keys | Full path scope | No profile edits or new auto wiring | S1 | Step 1 | N/A |
| Rule 9 | §7/13 | Existing owner adapter and JDBC transactions | Files 8/9 | Adapter keeps provider calls outside write lock | G1 | Step 1 | PASS |
| Rule 10 | §6.3 | UTC Instant and DB clock | Files 4/6/8/9 | Check bounded half-open intersection before/after | G1 | Step 1 | PASS |
| Rule 11 | DEC-003 §6.2/11 | User AGENTS, T4-01, V9 and V10 exact PS profile | Files 2/6–9 | Same source, one new externally applied Flyway migration and lifetime uniqueness; scoped technical deviation only | G1/S1 | Step 1 | PASS under DEC-003 |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | signature issued/expires | Occurred instant | Instant | UTC instant | UTC Z | UTC instant, no local zone | No new display | Start inclusive, end exclusive, no DST | Whole signed seconds and DB verification sample | Existing CLI Clock and output | Step 1 G0/G1 TEST-001 |
| TIME-002 | ownerObservedAt, submittedAt, decidedAt and leases | Occurred instant | Instant | UTC instant | Internal | UTC instant, no local zone | No new display | Intersection, exact expiry, no DST | Primary DB clock_timestamp microseconds, no COMMIT time claim | Existing Process read time stays | Step 1 G1 TEST-004 |

## 5. Change File Tree

| Operation | Exact path | File | Requirements | Gate |
| --- | --- | --- | --- | --- |
| MODIFY | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java | 1 | REQ-001/002 | G1/S1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java | 2 | REQ-005 | G1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java | 3 | REQ-001/002/003/004 | G1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStoreTest.java | 4 | REQ-004 | G1/S1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java | 5 | REQ-001/002/003 | G1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V11__add_platform_release_candidate_reviews.sql | 6 | REQ-004/005 | G1/S1 |
| MODIFY | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java | 7 | REQ-005 | G1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStore.java | 8 | REQ-004/005 | G1/S1 |
| CREATE | stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java | 9 | REQ-001/002/003/004 | G1/S1 |
| CREATE | docs/egon/spec/2026-11-06-07-46-platform-release-candidate-review.md | 10 | REQ-001/002/003/004/005 | G1/S1 |
| CREATE | docs/egon/plan/2026-11-06-07-47-platform-release-candidate-review.md | 11 | REQ-001/002/003/004/005 | G1/S1 |
| CREATE | docs/egon/evidence/2026-11-06-platform-release-candidate-review.md | 12 | REQ-001/002/003/004/005 | G1/S1 |


## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Re-lock actual PS HEAD and compare all touched base blobs to the reference commit before any tracked changes. Preserve independent dirty or staged files. Recheck that V11 is the next available Process migration and that 9 source paths match the reviewed hashes.

### 6.2 Build, test, and environment prerequisites

Use repository's installed Java 25, pinned offline source reactor and existing temporary Maven settings chosen by the owning executor. Finite suites use cached offline dependencies. PostgreSQL/production execution is a separate owner gate.

### 6.3 Immutable constraints and approved decisions

User AGENTS requires one new Flyway version and freezes earlier SQL. T4-01 authorizes source work on candidate and review. DEC-003 preserves same-source PS ordinary JDBC and external Flyway, using the narrow V9/V10 precedent and lifetime immutable audit. No automatic lifecycle, roles, auth providers, PaC, other owner or activation updates.

### 6.4 Plan Clarifications

| ID | Choice | Evidence/consequence |
| --- | --- | --- |
| PC-001 | One permanent decision per scope | A3 §11; SQL candidate and terminal review are separate immutable facts. |
| PC-002 | Revocation is observed through the current lease | A3 §5; the transaction uses local time and does not claim cross-node strong order. |
| PC-003 | Legacy V10-compatible rollout | A3 §16; source first, externally provisioned V11 later. |
| PC-004 | Review package has 9 production/test/SQL and 3 documentation paths | No scratch helper, mirror symlink or evidence log enters Git. |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 持久化并验证候选提交和独立审阅

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005.
- Dependencies: Exact baseline, prior committed A2 signature and snapshot, V9/V10 PS owner schema.
- Baseline state: Existing A2 offline, PS owner read and V10 exact history. Candidate review source and V11 absent.
- Observable outcome: Scoped Java calls write one signed candidate and one independent review, with current evidence, time bounds and history replay.
- End state: The two additive V11 tables and existing compatible readiness are in the PS source, while invocation requires explicit trusted Java composition.
- Test-first gate: Required. Use G0 and R1 before tracked final source; scratch U1 GREEN alone is insufficient for R1.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files: Files 1–12 in §5.

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java`

- Purpose: Share the public fixture only; leave all legacy CLI assertions effective.
- Symbols: CANDIDATE_PIN, TRUST_PIN, CANDIDATE, TRUST, SIDECAR
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseSignatureVerifierTest.java owner path.
- Dependencies and consumers: Expose package-local test constants used by new tests. Preserve golden signed payload and snapshots.
- Why now: File 1 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Expose package-local test constants used by new tests. Preserve golden signed payload and snapshots.
- Input/output and state mapping: Share the public fixture only; leave all legacy CLI assertions effective. Covered by REQ-001/002.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
static final String CANDIDATE_PIN = originalPinnedCandidate;
static final String TRUST_PIN = originalPinnedTrust;
static final String CANDIDATE = sameCanonicalFixture;
static final String TRUST = sameIndependentPublicDescriptor;
static final String SIDECAR = sameSignedClaimsFixture;
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java`

- Purpose: Prove exact V11 prefix and no new prerequisites for old Recovery.
- Symbols: history_accepts_only_exact_v8_through_v11_prefixes, candidate_review_requires_exact_v11_scoped_constraints_and_append_only_role
- Repository evidence: A3 Spec §7/8/9/11 and exact ProcessApiSchemaVerifierTest.java owner path.
- Dependencies and consumers: Extend the catalog fixture to exercise bytea, exact scope FK/index and immutable column and table privilege SQL.
- Why now: File 2 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Extend the catalog fixture to exercise bytea, exact scope FK/index and immutable column and table privilege SQL.
- Input/output and state mapping: Prove exact V11 prefix and no new prerequisites for old Recovery. Covered by REQ-005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
assertKnownHistory(v8, v9, v10, exactV11WithChecksum);
assertWrongHistory(v8, v9, v10, alteredV11);
verifyWorkerRecoveryWithoutCandidateGrants();
verifyCandidateCatalogThenRejectMissingUniqueIndexWrongScopeAndWrongType();
assertColumnPrivilegeSafety(candidateAndReviewRoleSql, false);
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java`

- Purpose: Prove scoped current auth and signed owner comparison at the Java boundary.
- Symbols: signed_candidate_and_independent_review_preserve_audited_facts, historical_replay_requires_independent_read_and_never_rewrites
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseCandidateReviewServiceTest.java owner path.
- Dependencies and consumers: Public signature fixture, controlled owner snapshots and typed audit windows. Deny role/tenant, stale trust and unauthorized read.
- Why now: File 3 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Public signature fixture, controlled owner snapshots and typed audit windows. Deny role/tenant, stale trust and unauthorized read.
- Input/output and state mapping: Prove scoped current auth and signed owner comparison at the Java boundary. Covered by REQ-001/002/003/004.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
submit(verifiedService, signedScope, publicVector);
rejectServiceReviewAndSignerSelfReview();
compareOwnerReleaseManifestAndModels();
assertExactReplayWithIndependentReadGrant();
assertChangedDecisionAndCompetingReviewerAreDifferentConflicts();
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003/004.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStoreTest.java`

- Purpose: Prove transaction order and rollback on the real JDBC adapter with mocks.
- Symbols: late_authority_expiry_rolls_back_even_if_insert_already_returned_success, conflicting_insert_uses_a_fresh_scoped_winner_read_in_read_committed
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseCandidateReviewStoreTest.java owner path.
- Dependencies and consumers: Mock JdbcTemplate and exact manager, record SQL order and transaction outcome.
- Why now: File 4 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Mock JdbcTemplate and exact manager, record SQL order and transaction outcome.
- Input/output and state mapping: Prove transaction order and rollback on the real JDBC adapter with mocks. Covered by REQ-004.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
whenDatabaseClockReturns(admittedInstant, exclusiveDeadline);
assertThrows(AccessDeniedException.class, () -> store.appendReview(receipt, window));
verify(manager).rollback(status);
assertReadCommittedInsertThenScopedWinnerSelect();
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-004.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java`

- Purpose: Provide one typed verification used by both old CLI and A3.
- Symbols: verify, verifyBytes, VerifiedCandidate.matchesProcess
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseSignatureVerifier.java owner path.
- Dependencies and consumers: Reuse raw pin, canonical candidate, signature and time predicates; preserve A2 output and baseline snapshot comparison.
- Why now: File 5 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Reuse raw pin, canonical candidate, signature and time predicates; preserve A2 output and baseline snapshot comparison.
- Input/output and state mapping: Provide one typed verification used by both old CLI and A3. Covered by REQ-001/002/003.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
VerifiedCandidate proof = verifyBytes(envelope, sidecar, descriptor, candidatePin, trustPin, tenant, channel, clock);
if (snapshot == null) return canonicalLine(proof.payload());
return canonicalLine(compareSnapshot(input, snapshot, proof));
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V11__add_platform_release_candidate_reviews.sql`

- Purpose: Create the sole additive ProcessDB migration with lifetime uniqueness.
- Symbols: tianji_platform_release_candidate, tianji_platform_release_review, tianji_platform_release_review_command_idx
- Repository evidence: A3 Spec §7/8/9/11 and exact V11__add_platform_release_candidate_reviews.sql owner path.
- Dependencies and consumers: Candidate stores exact audit and raw signed bytes, review scope FK and unique command; no grants or mutable data changes.
- Why now: File 6 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Candidate stores exact audit and raw signed bytes, review scope FK and unique command; no grants or mutable data changes.
- Input/output and state mapping: Create the sole additive ProcessDB migration with lifetime uniqueness. Covered by REQ-004/005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```sql
CREATE TABLE tianji_platform_release_candidate (..., PRIMARY KEY (tenant_id, channel, candidate_sha256));
CREATE TABLE tianji_platform_release_review (..., FOREIGN KEY (tenant_id, channel, candidate_sha256) REFERENCES tianji_platform_release_candidate (tenant_id, channel, candidate_sha256));
CREATE UNIQUE INDEX tianji_platform_release_review_command_idx ON tianji_platform_release_review (tenant_id, channel, reviewer_issuer, reviewer_subject, command_id);
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-004/005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java`

- Purpose: Admit exact V11 while keeping old minimum history and explicit A3 role checks.
- Symbols: verifyPlatformCandidateReviews, laterMigrations, verifyCaseRole, candidateReviewMigration
- Repository evidence: A3 Spec §7/8/9/11 and exact ProcessApiSchemaVerifier.java owner path.
- Dependencies and consumers: Pin Flyway checksum and resource SHA, verify columns/PK/FK/index/checks and strict append-only role.
- Why now: File 7 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Pin Flyway checksum and resource SHA, verify columns/PK/FK/index/checks and strict append-only role.
- Input/output and state mapping: Admit exact V11 while keeping old minimum history and explicit A3 role checks. Covered by REQ-005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
verifyHistory();
require(laterMigrations().size() >= 4);
for (var table : exactPinnedV11Tables) verifyColumnsChecksKeysAndIndexes(table);
verifyCaseRole(table, false, true);
acceptV10OrV11ForExistingWorkerRecovery();
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStore.java`

- Purpose: Implement exact same-source bounded append and winner lookup.
- Symbols: appendCandidate, appendReview, now, findCandidate, findReviewCommand
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseCandidateReviewStore.java owner path.
- Dependencies and consumers: Same engine/source/schema/manager. Database-clock and conflict proofs inside READ COMMITTED.
- Why now: File 8 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Same engine/source/schema/manager. Database-clock and conflict proofs inside READ COMMITTED.
- Input/output and state mapping: Implement exact same-source bounded append and winner lookup. Covered by REQ-004/005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
require(window.contains(now()));
int inserted = insertScopedRowOnConflictDoNothing();
var winner = selectFreshScopedWinner();
if (inserted == 1) require(window.contains(now()));
return new Append<>(winner, inserted == 1);
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-004/005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 9 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java`

- Purpose: Connect current actor authorization, signer trust and PS publication to commands and query.
- Symbols: submit, review, get, CurrentTrust, CurrentAuthorization
- Repository evidence: A3 Spec §7/8/9/11 and exact PlatformReleaseCandidateReviewService.java owner path.
- Dependencies and consumers: Verify exact scoped current grants, independent signer responsibility, typed signature and owner release. Read replay independently.
- Why now: File 9 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Verify exact scoped current grants, independent signer responsibility, typed signature and owner release. Read replay independently.
- Input/output and state mapping: Connect current actor authorization, signer trust and PS publication to commands and query. Covered by REQ-001/002/003/004.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```java
requireVerifiedActorAndExactAction(actor, scope);
var lease = loadTrust(scope);
var proof = verify(scope, signedMaterial, lease);
var owner = observeProcess(actor, scope, proof);
return store.appendReview(receipt, admissionWindow(proof, lease, owner, reviewGrant, independenceGrant));
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003/004.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 10 — `CREATE docs/egon/spec/2026-11-06-07-46-platform-release-candidate-review.md`

- Purpose: Resolve source-grounded exact candidate-review semantics and scope.
- Symbols: A3 §4/7/9/11 and DEC-003
- Repository evidence: A3 Spec §7/8/9/11 and exact 2026-11-06-07-46-platform-release-candidate-review.md owner path.
- Dependencies and consumers: Repository review artifact with no runtime changes.
- Why now: File 10 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Repository review artifact with no runtime changes.
- Input/output and state mapping: Resolve source-grounded exact candidate-review semantics and scope. Covered by REQ-001/002/003/004/005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```text
Read master T4-01 and the existing A2 and V9/V10 owner contracts.
Bind each requirement to an exact Java/database contract.
Record separate runtime acceptance and source review status.
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003/004/005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 11 — `CREATE docs/egon/plan/2026-11-06-07-47-platform-release-candidate-review.md`

- Purpose: Keep one file-by-file implementation step and re-lock evidence.
- Symbols: Step 1, G0/R1/G1/S1
- Repository evidence: A3 Spec §7/8/9/11 and exact 2026-11-06-07-47-platform-release-candidate-review.md owner path.
- Dependencies and consumers: Repository review artifact with no runtime changes.
- Why now: File 11 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Repository review artifact with no runtime changes.
- Input/output and state mapping: Keep one file-by-file implementation step and re-lock evidence. Covered by REQ-001/002/003/004/005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```text
Confirm reviewed Spec digest and exact current commit.
List changed source/test/SQL/doc paths in order.
Compare staged diff with approved scope and record commit evidence.
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003/004/005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

#### File 12 — `CREATE docs/egon/evidence/2026-11-06-platform-release-candidate-review.md`

- Purpose: Record actual finite evidence and the future runtime gates.
- Symbols: S0, U1–U3, I1, E1
- Repository evidence: A3 Spec §7/8/9/11 and exact 2026-11-06-platform-release-candidate-review.md owner path.
- Dependencies and consumers: Repository review artifact with no runtime changes.
- Why now: File 12 establishes the next exact source or review dependency in Step 1.
- Contract/signature changes: Repository review artifact with no runtime changes.
- Input/output and state mapping: Record actual finite evidence and the future runtime gates. Covered by REQ-001/002/003/004/005.
- Error and edge behavior: Preserve current compatibility and reject missing/mismatched proof or drift in the affected scope.
- Standards impact: MC-SCOPE-001, MC-TEST-001 and applicable §12.5 rows.
- Literal rule enforcement: Rule 11 exact technical boundary, with relevant per-file standards in §4.8.
- Implementation pseudocode:
```text
Record observed test counts and exact V11 checksum.
Describe mocked versus actual processes and expected integration owner.
Separate scratch source evidence from committed or deployed outcomes.
```
- Verification contribution: G1/S1; exercised acceptance and scope according to REQ-001/002/003/004/005.
- After this file: Proceed only when its dependency and exact file scope match the Spec.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: Commands G0, R1, G1, S1 in §8.
- Expected result: Actual focused test counters, no failures or skips in G1, strict docs, unchanged old migration bytes, exact path set.
- Failure returns to: Step 1 source/test or documentation correction after identifying the failing gate; no migration execution.
- Completion criteria: Exact path review, agreed source/role boundary, G0/R1/G1/S1 evidence and scoped commit.
- Rollback: Before external V11, revert the single source change only if owner requests. After V11 use an aware build and keep migration history immutable.
- Commit paths: stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifierTest.java, stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifierTest.java, stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewServiceTest.java, stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStoreTest.java, stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseSignatureVerifier.java, stellar-enclosure-tianji-process-server/src/main/resources/db/tianji-process/V11__add_platform_release_candidate_reviews.sql, stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/ProcessApiSchemaVerifier.java, stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewStore.java, stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseCandidateReviewService.java, docs/egon/spec/2026-11-06-07-46-platform-release-candidate-review.md, docs/egon/plan/2026-11-06-07-47-platform-release-candidate-review.md, docs/egon/evidence/2026-11-06-platform-release-candidate-review.md
- Commit: feat(process): persist signed release candidates and independent reviews

## 8. Test, Validation, and Quality Gates

| Gate | Command/inspection | Expected result |
| --- | --- | --- |
| G0 | Run repository offline Maven source reactor PS selectors PlatformReleaseSignatureVerifierTest, ProcessApiSchemaVerifierTest on exact baseline | Existing signatures, snapshots and schema history pass. |
| R1 | Omit only the Store post-insert requireCurrent(window) line when outcome.created(), then run PlatformReleaseCandidateReviewStoreTest#late_authority_expiry_rolls_back_even_if_insert_already_returned_success | One assertion fails because expired write returned instead of raising AccessDeniedException. |
| G1 | Run repository offline Maven source reactor with selectors PlatformReleaseSignatureVerifierTest, PlatformReleaseCandidateReviewServiceTest, PlatformReleaseCandidateReviewStoreTest, ProcessApiSchemaVerifierTest | All focused tests pass, zero skips. Scratch direct javac and JUnit results are supplementary. |
| S1 | Strict Spec and Plan validator commands below; git apply --check on candidate patch; git diff --check; compare V1–V10 and full repo path/hash inventory | Exact reviewed paths and V11 SHA/line checksum; prior SQL, POM, API, PaC and configs unchanged. |
| I1 | Future authorized isolated PG acceptance per Spec TEST-007 | Verify real migration, physical schema/role, dual writer, rollback and old capability compatibility. |
| E1 | Future production authority review | Confirm actual signer/trust, operator separation, release user policy and no unauthorized enablement. |

```bash
a3_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
a3_jdk=$(/usr/libexec/java_home -v 25)
a3_baseline='PlatformReleaseSignatureVerifierTest,ProcessApiSchemaVerifierTest'
a3_red='PlatformReleaseCandidateReviewStoreTest#late_authority_expiry_rolls_back_even_if_insert_already_returned_success'
a3_finite='PlatformReleaseSignatureVerifierTest,PlatformReleaseCandidateReviewServiceTest,PlatformReleaseCandidateReviewStoreTest,ProcessApiSchemaVerifierTest'
a3_selector="$a3_baseline"  # Set a3_red in the isolated mutation or a3_finite on restored source.
JAVA_HOME="$a3_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test   ./scripts/verify-source-reactor.sh -gs "$a3_settings" -s "$a3_settings" -o -B -ntp   -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false   -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$a3_selector"
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-46-platform-release-candidate-review.md
python3 /Users/mario/SelfProject/Stellar-Enclosure/.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-47-platform-release-candidate-review.md
git diff --check
```

## 9. Migration, Compatibility, Rollout, and Rollback

One new externally applied V11. Application can recognize exact V11 before deployment; current V10 remains enough for old capabilities. V11 readiness is explicit and separate. V1–V10 remain byte-immutable. If later applied, never roll source back to a build rejecting V11. No active release or instances are written.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Paths | Gate |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | Files 1/3/5/9 | G0/R1/G1 |
| REQ-002 | Step 1 | Files 3/5/9 | G1 |
| REQ-003 | Step 1 | Files 3/6/8/9 | G1 |
| REQ-004 | Step 1 | Files 3/4/6/8/9 | G1/I1 |
| REQ-005 | Step 1 | Files 2/6/7/10–12 | G1/S1/I1 |

## 11. Risks, Blockers, and User Decisions

Review source scope and actual Step baseline before committing. Source evidence uses mocked catalogs and transactions. Owner controls independent authorization/trust adapter, external V11 and I1/E1, any privilege grants, and rollout. A subsequent slice defines cross-owner authority and activation.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §6.1, ProcessStorageConfiguration and V9/V10 | Existing technical owner and exact bounded Rule 11 scope | DEC-003 |
| MC-REUSE-001 | Applicable | PASS | §6.1 A2/owner/Flyway | Reuse actual owner read and verifier | Review scoped patch |
| MC-DEP-001 | Applicable | PASS | §6 and PS pom.xml | Current JDK/Jackson/JDBC sufficient | Preserve manifests |
| MC-NAME-001 | Applicable | PASS | §8/10 type list | Semantics separate Commands, Receipts, values and Adapter | Inspect literal names |
| MC-VALID-001 | Applicable | PASS | §9, A2 and exact PS technical profile | Closed actor, trust, scope, deadline and byte input | Run finite tests |
| MC-MODEL-001 | Applicable | PASS | §10 value classification | Immutable values and defensive arrays | Verify signatures |
| MC-CONVERT-001 | Applicable | PASS | §10, current PS ProcessController and release mapping | Direct JDBC and same neutral DTO projection within approved technical profile | No new mapper layer |
| MC-LOG-001 | Not applicable | N/A | §7/15 technical and internal data path | Audit is persisted; no new business logging contract | Never emit raw bytes |
| MC-BEAN-001 | Not applicable | N/A | §8/16 explicit construction | No Spring bean or auto startup lifecycle | No runtime activation |
| MC-UTIL-001 | Applicable | PASS | §6.1 JDK/Jackson/JDBC | Reuse approved native digest, time and existing JSON | No dependency |
| MC-JSON-001 | Applicable | PASS | §7/9 A2 | Strict unchanged JSON and no new wire schema | Verify existing CLI |
| MC-TIME-001 | Applicable | PASS | §6.3 TIME-001/002 | UTC half-open window and after-write check | Execute TEST-004 |
| MC-CONFIG-001 | Not applicable | N/A | §3.3 and §16 | No profile keys touched | Verify static scope |
| MC-PATTERN-001 | Applicable | PASS | §7/13 Adapter | Persistence isolation with explicit authority composition | Review lock boundary |
| MC-SCOPE-001 | Applicable | PASS | §3.3/11/16 and exact Plan list | Nine source paths, one new version | Verify hashes and git scope |
| MC-TEST-001 | Applicable | PASS | §14 TEST-001–007 | Finite, static and distinct live acceptance gates | Run only authorized finite gates |
| MC-BLOCKER-001 | Applicable | PASS | DEC-003 and §14/18 | Design closed for source review; runtime gates separately controlled | Keep Review until owner decision |

### 12.6 Final verdict

PASS — Ready for user review

## Java / CQE contract review

Same PS technical architecture with internal submit/review commands and historical query. Current authorizer and trust reader use explicit ports. No business Event or migration runner.

## Backend generation and dependency evidence

Same-owner technical PS JDBC/Flyway; current public/production config and dependencies stay intact. User AGENTS requires a single new V11 and preserves all existing classpath SQL. Review Rule 11 DEC-003 at final lock.
