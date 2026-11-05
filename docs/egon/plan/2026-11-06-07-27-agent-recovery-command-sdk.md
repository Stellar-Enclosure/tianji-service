# T4-03 Java SDK 受保护的 Agent 恢复命令计划

| Field | Value |
| --- | --- |
| Document | docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 07:27 Asia/Shanghai |
| Updated | 2026-11-06 07:27 Asia/Shanghai |
| Owner | Tianji Java SDK |
| Repository | tianji-service |
| Scope | One optional interface, command codec, existing Agent Adapter and finite transport tests |
| Source Requirement | Master T4-03 and S11 |
| Baseline Revision | 7c83b4254d0988d400824268d6ce38ab8725b6c5 |
| Implements Spec | [Agent recovery SDK Spec](../spec/2026-11-06-07-26-agent-recovery-command-sdk.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 07:26 Asia/Shanghai SHA-256 fa0825a48121d3962e133dcf032b023db5ef96562bb95e468e5d03b161b2b307 |
| Effective Specs | [Agent recovery SDK Spec](../spec/2026-11-06-07-26-agent-recovery-command-sdk.md) |
| Depends On Plans | [Resume](2026-11-06-06-49-protected-checkpoint-resume.md), [Historical receipt](2026-11-06-06-56-protected-historical-start-receipt.md) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Java SDK 为已发布的 checkpoint RESUME 和历史 START 收据对账提供两个有类型的 USER POST，验证精确回显和有界响应。每次调用只发送一个请求。

## 2. Target Spec and Effective Design

[Agent recovery SDK Spec](../spec/2026-11-06-07-26-agent-recovery-command-sdk.md) §§4/7/9/14 governs this Step. Existing AS endpoints define the wire and identity.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance |
| --- | --- | --- |
| REQ-001 | §7/9 | Two exact typed USER POST methods with current bearer |
| REQ-002 | §7/14 | Closed bounded decode with exact request binding |
| REQ-003 | §8/16/18 | Existing methods pass regression, I1 stays independent |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Freeze baseline and sources. Add contract, codec and adapter, prove R1 and restore G1, validate actual jars, then stage and commit the exact paths.

### 4.2 Test-first strategy

R1 removes only the ACK revision equality. The wrong-revision test must fail because the exception was not thrown. Restore the locked candidate before G1.

### 4.3 Sequential and parallel boundaries

Run R1, G1, P1 and exact scope in order. Independent protected I1 uses the operator environment.

### 4.4 Commit boundaries

One focused commit covering four source files and three review documents.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Plan decision |
| --- | --- | --- | --- | --- |
| Typed commands | Required §7/9 | EVD-001–003 | Current transport fits | Extend existing Adapter |
| Closed response | Required §7/14 | EVD-001/002 | Generic decoder lacks command binding | One package-private codec |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Files | Dependency |
| --- | --- | --- | --- | --- |
| Commands and strict adapter | REQ-001/002 | R1/G1/P1 | 1–4 | Existing protected AS endpoints |
| Review evidence | REQ-003 | S1 | 5–7 | Owning artifacts |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Boundary | JDK HTTP Adapter and contract | Reuse existing transport |
| Dependencies | Jackson 3 and JUnit | Existing dependencies |
| Service ownership | Protected AS endpoints | Service remains authoritative |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| HTTP command | HttpAgentClient | EVD-003 | Bearer, deadline and subscriber fit | Extend | Scoped methods | G1 |
| Exact response | Existing OAS and owner codec | EVD-001–003 | Need exact command binding | Reuse conventions | One command codec | R1/G1 |
| Typed result | Existing contract conventions | EVD-001/002 | Results have specific command identity | Add records | One interface | G1/P1 |

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
| Rule 1 | §6.2/7 | EVD-001–003 | Files 1–4 | Use existing Agent Java SDK package names and command types. | R1/G1 | Step 1 | PASS |
| Rule 2 | §6.2/7 | EVD-001–003 | Files 1–4 | Validate request identifiers, fixed ACK revision, receipt echoes and bounded JSON. | R1/G1 | Step 1 | PASS |
| Rule 3 | §6.2/7 | EVD-001–003 | Files 1–4 | Use immutable command and response records. | R1/G1 | Step 1 | PASS |
| Rule 4 | §6.2/7 | EVD-001–003 | Files 1–4 | Outside the SDK change | R1/G1 | Step 1 | N/A |
| Rule 5 | §6.2/7 | EVD-001–003 | Files 1–4 | Use JDK URI, charset, UUID and java.time with existing SDK helpers. | R1/G1 | Step 1 | PASS |
| Rule 6 | §6.2/7 | EVD-001–003 | Files 1–4 | Reject unexpected fields, duplicate keys, trailing tokens and malformed UTF-8. | R1/G1 | Step 1 | PASS |
| Rule 7 | §6.2/7 | EVD-001–003 | Files 1–4 | Outside the SDK change | R1/G1 | Step 1 | N/A |
| Rule 9 | §6.2/7 | EVD-001–003 | Files 1–4 | Reuse the HTTP Adapter with a package-private closed codec. | R1/G1 | Step 1 | PASS |
| Rule 10 | §6.2/7 | EVD-001–003 | Files 1–4 | Use UTC occurrence instants for independent AS and PS clocks. | R1/G1 | Step 1 | PASS |
| Rule 11 | §6.2/7 | EVD-001–003 | Files 1–4 | Keep engine-neutral records and the existing JDK HTTP Adapter boundary. | R1/G1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | recordedAt, observedAt | Occurred instant | java.time.Instant | UTC instant | UTC Z | Not applicable: absolute instant | Not applicable: no display | Reject out-of-range clock fields and offsets. No cross-clock order requirement. | Up to nanoseconds. Independent AS and PS clocks. | Existing historical response fields | Step 1 TEST-002 |
| TIME-002 | Existing request timeout | Duration | java.time.Duration | Not persisted | Internal | Not applicable: elapsed | Not applicable: no display | Existing deadline and cancellation | JDK HTTP | Existing client policy | Step 1 TEST-001 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| CREATE | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryCommandClient.java` |
| CREATE | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentRecoveryCommandResponseCodec.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java` |
| MODIFY | `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md` |
| CREATE | `docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md` |
| CREATE | `docs/egon/evidence/2026-11-06-agent-recovery-command-sdk.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

Exact Service baseline 7c83b4254d0988d400824268d6ce38ab8725b6c5. Existing offline JDK25 reactor and finite selectors only. Source OpenAPI, permissions, dependencies, configuration and migrations retain their published behavior.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Expose the two protected recovery commands in Java

- Requirements: REQ-001, REQ-002, REQ-003.
- Dependencies: Two existing protected AS routes and current JDK Adapter.
- Baseline state: Both POSTs are published and have no typed Java SDK method.
- Observable outcome: Two bounded exact Java calls and immutable typed results.
- End state: Four source paths, three documents and finite evidence.
- Test-first gate: Required. R1 fails one exact wrong-revision case.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryCommandClient.java`

- Purpose: AgentRecoveryCommandClient.
- Symbols: AgentRecoveryCommandClient.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Additive capability or scoped implementation.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Declare two typed operations on the existing protected routes.
Validate bounded exact IDs and nonnegative revision.
Return redacted immutable ACK and receipt records.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 2 — `CREATE stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentRecoveryCommandResponseCodec.java`

- Purpose: AgentRecoveryCommandResponseCodec.
- Symbols: AgentRecoveryCommandResponseCodec.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Additive capability or scoped implementation.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Require exact status, media and cache policy.
Decode bounded closed UTF-8 JSON and sanitized errors.
Require ACK revision plus two and exact historical echo.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 3 — `MODIFY stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java`

- Purpose: HttpAgentClient.
- Symbols: HttpAgentClient.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Additive capability or scoped implementation.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Check the existing secure origin and encode fixed request fields.
Send one POST through the existing bounded JDK subscriber.
Select the command codec and retain ordinary behavior.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 4 — `MODIFY stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java`

- Purpose: OwnerObservationHttpTest.
- Symbols: OwnerObservationHttpTest.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Additive capability or scoped implementation.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```text
Use FakeHttpClient and the actual SDK.
Check exact requests, echo, UNKNOWN and timeout count.
Remove revision equality for R1 and restore the tested candidate.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 5 — `CREATE docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md`

- Purpose: REQ-001–003.
- Symbols: REQ-001–003.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Identify the exact protected routes.
Freeze wire, timing and failure boundaries.
Separate finite proof and independent I1.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 6 — `CREATE docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md`

- Purpose: R1/G1/P1/S1.
- Symbols: R1/G1/P1/S1.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Lock Service parent and exact paths.
Run typed RED and restore owning green.
Compare clean artifacts and stage exact scope.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.

#### File 7 — `CREATE docs/egon/evidence/2026-11-06-agent-recovery-command-sdk.md`

- Purpose: Validation evidence.
- Symbols: Validation evidence.
- Repository evidence: Spec EVD-001–003.
- Dependencies and consumers: Existing Java SDK and protected AS routes.
- Why now: Expose typed Java commands before independent operator proof.
- Contract/signature changes: Documentation only.
- Input/output and state mapping: Typed bounded command and published durable result.
- Error and edge behavior: Match exact success with sanitized failures.
- Standards impact: MC-REUSE-001, MC-VALID-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record the expected assertion failure and actual finite XML test counts.
Record actual packaged Java APIs and compare the existing server permission artifacts.
Keep independently operated protected IAM, HTTPS and PostgreSQL evidence pending.
```

- Verification contribution: R1/G1/P1/S1.
- After this file: Keep the Step scope exact.


- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 R1/G1/P1/S1.
- Expected result: One exact RED, 23 finite GREEN cases, clean package and seven staged paths.
- Failure returns to: Files 1–4 for source or Files 5–7 for review scope.
- Completion criteria: Source gates pass and independent operator proof stays pending.
- Rollback: Revert one SDK commit.
- Commit paths: `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryCommandClient.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/AgentRecoveryCommandResponseCodec.java`, `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpAgentClient.java`, `stellar-enclosure-tianji-client/src/test/java/top/egon/cola/platform/tianji/client/OwnerObservationHttpTest.java`, `docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md`, `docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md`, `docs/egon/evidence/2026-11-06-agent-recovery-command-sdk.md`.
- Commit: feat(client): expose protected agent recovery commands.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Expected |
| --- | --- | --- |
| R1 | OwnerObservationHttpTest#rejects_an_acknowledgement_for_another_revision | Typed expected exception is absent after removing the exact revision predicate |
| G1 | Owning reactor finite SDK | 17 OwnerObservationHttpTest and 6 ProcessStartReceiptHttpTest cases pass |
| P1 | Clean offline reactor with permission producer | Same 23 pass, actual contract and client jars contain the new classes, AS and PS packaged manifests match prior semantics |
| S1 | Strict review documents and stage | Seven exact paths with existing server contracts preserved |
| I1 | Independent operator acceptance | Actual protected IAM, HTTPS and durable PostgreSQL evidence |

```sh
sdk_base=7c83b4254d0988d400824268d6ce38ab8725b6c5
sdk_runner=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-correlation/root/reactor.sh
test "$(git rev-parse --verify HEAD)" = "$sdk_base"
"$sdk_runner" 'OwnerObservationHttpTest,ProcessStartReceiptHttpTest'
STELLAR_VERIFY_GOAL=verify "$sdk_runner" 'OwnerObservationHttpTest,ProcessStartReceiptHttpTest' clean -Ppermission-producer "-Dpermission.commit=$sdk_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-agent-recovery-command-sdk
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict docs/egon/spec/2026-11-06-07-26-agent-recovery-command-sdk.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict docs/egon/plan/2026-11-06-07-27-agent-recovery-command-sdk.md
git diff --check
git diff --cached --check
git diff --cached --name-status
```

Run R1 by removing only the exact revision guard and restoring the frozen candidate. Verify owning XML and actual package contents. I1 remains an independent operator gate.

## 9. Migration, Compatibility, Rollout, and Rollback

An additive Java capability over existing routes. Revert one commit to remove it.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact files | Verification |
| --- | --- | --- | --- |
| REQ-001 | Step 1 | 1–4 | G1/P1 |
| REQ-002 | Step 1 | 2–4 | R1/G1 |
| REQ-003 | Step 1 | 3–7 | G1/P1/S1 |

## 11. Risks, Blockers, and User Decisions

Transport timeout can leave the outcome uncertain. Callers resolve the durable result explicitly. Actual IAM, protected HTTPS and PostgreSQL remain operator acceptance.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14/18 | Keep engine-neutral records and the existing JDK HTTP Adapter boundary. | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14/18 | Reuse existing USER routes, bearer supplier, deadline and bounded subscriber. | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14/18 | Reuse JDK, Jackson 3 and JUnit. | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14/18 | Use existing Agent Java SDK package names and command types. | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14/18 | Validate request identifiers, fixed ACK revision, receipt echoes and bounded JSON. | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14/18 | Use immutable command and response records. | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-BEAN-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14/18 | Use JDK URI, charset, UUID and java.time with existing SDK helpers. | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14/18 | Reject unexpected fields, duplicate keys, trailing tokens and malformed UTF-8. | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14/18 | Use UTC occurrence instants for independent AS and PS clocks. | None |
| MC-CONFIG-001 | Not applicable | N/A | §7/8/14/18 | This SDK has no change at this boundary. | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14/18 | Reuse the HTTP Adapter with a package-private closed codec. | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14/18 | Keep changes to four source files and three review documents. | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14/18 | Run a revision counterexample and finite SDK regression. | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14/18 | Keep live IAM, protected AS/PS and PostgreSQL proof pending. | None |

### 12.6 Final verdict

PASS — Ready for user review
