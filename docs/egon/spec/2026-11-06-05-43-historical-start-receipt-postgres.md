# T4-03 历史 START 收据 PostgreSQL 验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | Guarded real V13-to-V2/V5 operator fixture with controlled ports |
| Created | 2026-11-06 05:43 Asia/Shanghai |
| Updated | 2026-11-06 05:43 Asia/Shanghai |
| Owner | Tianji Agent Runtime |
| Repository | tianji-service |
| Scope | One existing AR PostgreSQL fixture and three reviewer documents |
| Change Surface | One existing AR PostgreSQL test and three reviewer documents |
| Affected Chapters | §7, §8, §14 |
| Source Requirement | [主计划 §17 S11、§19 T4-03](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | e789021e4c57f25110f7cff3496fd58c5ec42a1c |
| Amends | None |
| Supersedes | None |
| Depends On | [Historical receipt command](2026-11-06-05-28-tool-start-receipt-reconciliation.md#73-detailed-design) |
| Related Specs | None |
| Related Plans | [Historical START receipt PostgreSQL proof](../plan/2026-11-06-05-44-historical-start-receipt-postgres.md) |

## 1. Summary

复用既有隔离 V13 日志夹具，分别构造 UNKNOWN 与 DISPATCHING 的历史派发。验证正向证据仅追加 V2/V5 收据，并核对拒绝、未知结果、并发、精确重放及原始记录保全。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ToolExecutionJournalPostgresTest and WriteFixture | Existing guarded V13 tests record actual dispatch and UNKNOWN through the original write path |
| EVD-002 | ToolStartReceiptReconciliationService and ToolStartReceiptReconciliationSql at e789021e4c57f25110f7cff3496fd58c5ec42a1c | Existing historical service inserts positive evidence only into the V2/V5 receipt |
| EVD-003 | ToolStartReceiptReconciliationServiceTest and ToolStartReceiptReconciliationSqlTest | Owning finite cases cover command sequencing and conditional SQL |
| EVD-004 | V2, V5 and V13 resources | Existing owner operation key and V13 action/invocation dispatch cutoff constraints |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: One existing Agent Runtime integration test and reviewer documents. No generated example or scaffold is added.

## 3. Goals and Non-goals

Add an independently selected operator case for the real V13-to-V2/V5 path. Current source evidence covers finite checks and compiled selector only. Actual PostgreSQL, production IAM, HTTPS and full S11 remain separate.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| AR test | Affected | EVD-001–003 | Exact new opt-in and persisted row assertions | Fixture and evidence | §7, §8, §14 |
| AgentDB | Context-only | EVD-004 | Existing V1–V13 schema and V2/V5 receipt only | Existing schema and witnesses | §11 |
| Production AS/PS/Web | Unchanged | EVD-002/003 | No route, wiring or authority change | Scope boundaries | §12, §16 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Exact independent operator fixture | Must | Existing URL and vector pins plus one new method guard; V1–V13 on a fresh loopback primary | EVD-001/004 |
| REQ-002 | Append only positive historical evidence | Must | UNKNOWN and DISPATCHING actual rows; pre-cutoff, owner mismatch, denied and empty responses append nothing; positive writes one row, overlap and replay return the same ACK and contradiction conflicts; complete source rows stay equal | EVD-001–004 |
| REQ-003 | Keep evidence boundaries | Must | 45 finite tests, bytecode, exact four paths and all 28 SQL byte-identical; actual operator proof pending | EVD-003/004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Authorized integration operator | Independent disposable loopback PostgreSQL with vector |
| ACTOR-002 | Source reviewer | Finite and compiled gates |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify actual historical append and replay | ACTOR-001 | Exact I1 and fresh DB | Original V13 write fixture, current controlled evidence, exact V2/V5 checks | Denial, empty receipt or conflict keeps original state | One positive row with no source mutation | REQ-001/002 |
| UC-002 | Review source without PostgreSQL | ACTOR-002 | Clean baseline and existing offline settings | G0/G1/G2/S1 | Wrong selector, failed count or diff rejects | I1 remains separate | REQ-003 |

## 5. Constraints, Assumptions, and Decisions

I1 selects historical_start_receipts_append_positive_evidence_for_unknown_and_dispatched_actions and tianji.tool.journal.postgres.historical.start.receipt=true with existing loopback URL and pinned preinstalled vector. Prior operator method retains its original entry. Each operator method requires a schema-empty disposable database.

## 6. Project Technology Context

JDK25, JUnit, Spring JDBC, Flyway, PostgreSQL and existing Agent Runtime owners.

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
| Rule 1 | Yes | EVD-001–004 | One exact new operator method on the existing test class | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Reuse existing disposable DB guards and exact typed owner/authority inputs | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | Yes | EVD-001–004 | Reuse production DTOs and private immutable database fixture pair | §7/8/14 | TEST-001–003 | PASS |
| Rule 4 | No | EVD-001–004 | No application bean, dependency injection or logger changes | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse existing JDK, JUnit, JDBC and Flyway | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–004 | Verify existing receipt JSONB and complete source rows | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–004 | Add one test JVM property with no configuration change | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–004 | Directly compose existing fixture and production command with controlled ports | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–004 | Reuse Instant, DB time and existing operator timeout | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Reuse existing Agent Runtime fixture package and schema | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | dispatch_not_after, recordedAt and observedAt | Occurred instant | java.time.Instant and JDBC Timestamp | UTC instant | Internal | Not applicable: absolute instant | Not applicable: no display | Fixture bound is dispatchedAt plus one microsecond after pre-fence rejection | PostgreSQL microseconds and independent Clock | Existing source timestamps and receipts | Step 1 TEST-003 |
| TIME-002 | Operator test, JDBC and rendezvous deadlines | Duration | JUnit Timeout and JDK elapsed wait | Not persisted | Internal | Not applicable: elapsed duration | Not applicable: no display | 120s test, 5s connection, 15s socket, 4s gate and 12s futures | JUnit, driver and executor elapsed clocks | Retain existing operator timeouts | Step 1 TEST-002/003 |

## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| One test method | Extend | REQ-001/002 | Existing isolated V13 test | No composed historical service on real PostgreSQL | Exact operator gate only | Use |
| Existing setup and WriteFixture | Reuse | REQ-001 | Repeat or rebuild guard logic | Need same isolation and source states | Private setup extraction | Use |
| Real command and controlled ports | Reuse | REQ-002 | Mock JDBC shape test | No actual V13 and V2 row persistence in finite tests | Test-local port substitutes | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| I1 | Disposable PostgreSQL only | Real V13 and historical command | Existing AgentDB | Any drift or duplicate evidence fails |
| G0/G1/G2/S1 | None | Source and class-only | Finite XML only | No operator selector |

### 7.1 System Architecture Design

Compose real AgentLedgerService and ToolStartReceiptReconciliationService on the existing WriteFixture. Exact owner and Process responses are controlled in process.

### 7.2 High-Level Design

Create actual V13 UNKNOWN and DISPATCHING pairs, finish the Runs and verify future original cutoff rejection. Narrow only the fixture cutoff before capturing the source baseline. Compare scoped results before and after the command.

### 7.3 Detailed Design

For each state reject foreign owner, denied authority and no Process evidence. Require one V2/V5 row on a positive read. In DISPATCHING overlap two commands with a bounded rendezvous at the controlled positive read, then require equal ACK and one stored row. Both states separately replay and reject a conflicting positive observation. Verify the complete action, invocation, capability, Run and budget JSONB remain equal to the pre-command baseline.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/infrastructure/persistence/ToolExecutionJournalPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-05-43-historical-start-receipt-postgres.md` |
| CREATE | `docs/egon/plan/2026-11-06-05-44-historical-start-receipt-postgres.md` |
| CREATE | `docs/egon/evidence/2026-11-06-historical-start-receipt-postgres.md` |

## 9. Interface Definitions

Only a method-level operator JVM gate is added. Existing production interfaces retain their contracts.

## 10. POJO and Data Model Design

Reuse existing V13 fixture, typed historical ports, Process DTO and command result. Private DisposableDatabase pairs the existing data source and JDBC fixture. No production model changes.

## 11. Database Design

Relational model change: No. Use actual V1–V13 on an authorized fresh fixture. The existing V13 constraint permits a test-only cutoff of dispatched_at plus one microsecond. Capture baseline after this setup. The production command may only add the exact V2/V5 row. All 28 source SQL files remain byte-identical.

## 12. Frontend Page Design

This AR test adds no frontend path.

## 13. Design Patterns and Architecture Principles

Direct fixture composition reuses the real service and two existing ports. Persisted witnesses are independent of the controlled responses.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | Clean owning G1 runs four finite suites and requires 45 successful tests. |
| TEST-002 | Baseline absence, compiled method, class and method JVM gates, no PG report, and exact staged scope. |
| TEST-003 | Separate I1 uses actual V13 and V2/V5 rows, proves both states, concurrent positive equality, original evidence replay and no other writes. |

## 15. Non-functional and Cross-cutting Design

Reuse bounded test and JDBC settings. Evidence reports fixed outcome and test gates without fixture credentials or raw runtime payload.

## 16. Compatibility, Migration, Rollout, and Rollback

Keep the original operator selector and V12-to-V13 verification. Revert one test-only commit to remove the new fixture.

## 17. Alternatives and Decisions

Existing finite tests establish SQL and control shape. Add one guarded actual database method for the composed proof.

## 18. Risks and Open Questions

Actual PostgreSQL execution needs the authorized isolated operator environment. Test-local ports do not establish live IAM, HTTPS or complete S11.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §5/7/11 | TEST-002/003 |
| REQ-002 | UC-001 | §7/10/14 | TEST-003 |
| REQ-003 | UC-002 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing AR operator fixture owns the scenario | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing disposable DB, actual journal and historical command | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test and runtime dependencies | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | One named exact guarded integration method | None |
| MC-VALID-001 | Applicable | PASS | §5/7/14 | Disposable primary, exact owner and test-local authority | None |
| MC-MODEL-001 | Applicable | PASS | §10 | Private immutable disposable database pair and exact row witnesses | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing receipt JSONB, no cross-layer mapper | None |
| MC-LOG-001 | Not applicable | N/A | §15 | No new logger, raw payload or credentials in reviewer evidence | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Explicit fixture construction only | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | Existing fixed fixture helpers and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/11 | Verify stored status and complete source JSONB | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Database clock cutoff and bounded operator test | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Dedicated test JVM properties only | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Direct reuse of existing fixture and current command | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | One test and three reviewer artifacts | None |
| MC-TEST-001 | Applicable | PASS | §14 | Exact finite and bytecode gates separate from operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | Actual PostgreSQL and production IAM results pending | None |

### 20.6 Final verdict

PASS — Ready for user review
