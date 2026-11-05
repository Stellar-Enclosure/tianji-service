# S07 Process START 授权组合验收用例

| Field | Value |
| --- | --- |
| Document | docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md |
| Template Version | 9 |
| Archetype Cleanup | Not applicable |
| Status | Review |
| Type | Feature |
| Complexity | Simple |
| Complexity Drivers | One shared protected servlet probe and one durable witness in the existing PG test |
| Created | 2026-11-06 04:32 Asia/Shanghai |
| Updated | 2026-11-06 04:32 Asia/Shanghai |
| Owner | Tianji Process Server |
| Repository | tianji-service |
| Scope | Two existing PS test sources and three review documents |
| Change Surface | PS protected release GET and START POST fixture, finite source and isolated PG evidence |
| Affected Chapters | §7, §8, §14, §15, §16 |
| Source Requirement | [主计划 §17.2 S07、§19 T3-07/T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md) |
| Baseline Revision | 525f4afa9e0da01583811f292f21331ef9a77709 |
| Amends | None |
| Supersedes | None |
| Depends On | None |
| Related Specs | None |
| Related Plans | [S07 START authorization](../plan/2026-11-06-04-33-s07-process-start-authorization.md) |

## 1. Summary

为 S07 增加 Process Server 侧组合验收。通过实际身份过滤器、权限 guard、HTTP handler、Flowable 和 PostgreSQL 观察一条完整 SERVICE 身份的 START 写入，并验证拒绝与重复请求。

## 2. Background and Current State

| Evidence ID | Exact source | Observed fact |
| --- | --- | --- |
| EVD-001 | ProcessPermissionIntegrationTest#servlet_chain_checks_current_user_permission_scope_and_trusted_tenant and #guard | Actual Shoubing/Jianshen filters, guarded controller and controlled JWT/PDP responses; storage handler is mocked |
| EVD-002 | ProcessServerPostgresTest#verifyHttpAgainstDatabase | Actual Process services and PostgreSQL; existing standalone HTTP case mocks PermissionExecutionGuard |
| EVD-003 | ProcessController#release/#start, TianjiProcessAccess#require, ProcessExecutionService#startInTransaction and ProcessCommandReceiptStore | Protected release/START endpoints, full verified tenant/type/issuer/subject, and atomic native effect plus START receipt |
| EVD-004 | BusinessProcessAgentAcceptanceIT#start and #agentConfig | Existing expense suite starts BPMN directly as USER and runs BPMN to Agent; S07 end-to-end remains separate |

### 2.5 Archetype example cleanup

Cleanup disposition: Not applicable

Reason: Existing PS test fixtures with no generated project scope.

## 3. Goals and Non-goals

Compose real PS permission execution and actual native START and database receipt evidence under controlled verifier/PDP responses. The offline finite gate observes the same servlet probe using typed mocked services. Live IAM, actual Agent tool dispatch and approval verification remain independent S07 work.

### 3.3 Change Surface and Design Depth

| Area/layer | Disposition | Exact repository evidence | Changed or preserved behavior/contract | Required Spec treatment | Chapter(s) |
| --- | --- | --- | --- | --- | --- |
| PS permission test | Affected | EVD-001/003 | Shared exact release and START probe plus one finite case | Authorization sequence and typed status assertions | §7, §8, §14 |
| PS PostgreSQL test | Affected | EVD-002/003 | Invoke the same probe with real services and verify native/receipt facts | Guarded fixture, durable witnesses and source scope | §7, §8, §14, §15, §16 |
| Product contracts and authorization | Unchanged | EVD-003/004 | Current endpoints, resources, identities and scopes | Separation from E | §16/18 |

## 4. Requirements and Acceptance Criteria

| ID | Requirement | Priority | Observable acceptance | Source |
| --- | --- | --- | --- | --- |
| REQ-001 | Exercise protected release and START independently | Must | Verified SERVICE release GET returns exact version, manifest/model hashes and input schema; START ACK preserves definition, release, business key and correlation; same-command retry returns same ACK | EVD-001/003 |
| REQ-002 | Exercise fail-closed current permission and identity | Must | Missing release grant and START grant return 403 separately; no START scope, foreign tenant and same-name USER without START permission return 403; anonymous and rejected issuer return 401; forged tenant header returns 409 | EVD-001/003 |
| REQ-003 | Observe one native effect and exact receipt | Must | One active native instance and held review task; exactly one START receipt for the fixture release and one APPLIED match with full SERVICE owner, command, digest and instance | EVD-002/003 |
| REQ-004 | Keep finite, database and E evidence separate | Must | Typed single-test RED and GREEN, compiled existing PG opt-in, exactly five source/doc paths and 28 unchanged migrations; actual PG and Agent-to-Process E remain separate | EVD-001–004 |

### 4.2 Use-case analysis

| Actor ID | Actor | Context |
| --- | --- | --- |
| ACTOR-001 | Controlled SERVICE and USER identities | Two principal types sharing one subject text, using exact Process scopes and catalog grant decisions |
| ACTOR-002 | Isolated PostgreSQL test operator | Supplies existing fresh loopback primary and dedicated runtime role |
| ACTOR-003 | Source reviewer | Runs finite servlet case and inspects compiled fixture |

| ID | Use case/goal | Actors | Trigger/preconditions | Main flow | Alternative/failure | Postcondition | Requirements |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-001 | Verify actual protected Process START | ACTOR-001/002 | Existing PG class guard and independent fixture release | Verify release, exercise denied calls, submit and retry one authorized command, read scoped witnesses | Wrong authorization result, mismatched ACK or extra effect/receipt fails | One active PROCESS instance and one full SERVICE APPLIED receipt | REQ-001/002/003 |
| UC-002 | Verify scoped source delivery | ACTOR-003 | Exact two candidate test files and baseline | Run R1/G1/G2/S1 without selecting the PG class | Wrong assertion, class, scope or accidental PG report fails | Finite and compile evidence only | REQ-004 |

## 5. Constraints, Assumptions, and Decisions

Use the existing `tianji.process.postgres.test.url` and `tianji.process.postgres.test.runtime-user` admission guards. I1 requires a fresh loopback `tianji_it_*` database, the existing setup role and a dedicated `tianji_it_runtime_<12 lowercase hex>` login without administrative privileges or extra memberships. Use local JDBC authentication and no secrets in flags, URLs or evidence. The same fixture runs existing Flyway, native Flowable and JDBC Outbox setup with its current isolation. The finite selector targets only the specific permission method and omits PG properties.

## 6. Project Technology Context

Existing Process Server test infrastructure uses JDK25, Spring Security and MockMvc, Shoubing and Jianshen, JUnit, Mockito, Flowable and Spring JDBC.

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
| Rule 1 | Yes | EVD-001–004 | Reuse exact PS test classes, typed selectors and descriptive helper names | §7/8/14 | TEST-001–003 | PASS |
| Rule 2 | Yes | EVD-001–004 | Exercise typed START command and production request validation through the real filters | §7/8/14 | TEST-001–003 | PASS |
| Rule 3 | No | EVD-001–004 | Reuse ProcessActor, START command, release and instance DTOs | §7/8/14 | TEST-001–003 | N/A |
| Rule 4 | No | EVD-001–004 | Reuse existing test context and production filter registration | §7/8/14 | TEST-001–003 | N/A |
| Rule 5 | Yes | EVD-001–004 | Reuse JUnit, MockMvc, Mockito and Spring JDBC from the current test classpath | §7/8/14 | TEST-001–003 | PASS |
| Rule 6 | Yes | EVD-001–004 | Use the existing starter mapper and managed Jackson DTO serialization | §7/8/14 | TEST-001–003 | PASS |
| Rule 7 | No | EVD-001–004 | Reuse existing test properties and the PostgreSQL opt-in | §7/8/14 | TEST-001–003 | N/A |
| Rule 9 | Yes | EVD-001–004 | Reuse the production Spring Security filter chain and direct test sequencing | §7/8/14 | TEST-001–003 | PASS |
| Rule 10 | Yes | EVD-001–004 | Reuse fixture Clock and token expiry instants without changing persisted time semantics | §7/8/14 | TEST-001–003 | PASS |
| Rule 11 | Yes | EVD-001–004 | Keep existing PS test package and production architecture | §7/8/14 | TEST-001–003 | PASS |

### 6.3 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | Existing test NOW/CLOCK and token validity | Occurred instant | java.time.Instant and Clock | Not persisted | Internal | Not applicable: fixed UTC fixture clock | Not applicable: no fixture display | Fixed test clock with issued time minus 60 seconds and expiry plus 300 seconds | Existing fixture and UTC clock | Existing guards and native receipt time | Step 1 TEST-001/003 |


## 7. Architecture Design

### 7.0 Minimum-design baseline and element-necessity audit

| Proposed element | Change | Requirements | Existing alternative | Gap | Cost | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| Shared servlet permission probe | Extend existing test | REQ-001/002/004 | Existing independently mocked boundaries | No exact same protected chain drives finite and real PostgreSQL cases | One test and local helpers | Use |
| PostgreSQL scoped witness | Extend existing test | REQ-003 | Existing standalone mocked-guard HTTP test | No durable witness after production guard | One isolated release and scoped assertions | Use |

| Path | Network calls | Client states | Persisted evidence | Failure boundary |
| --- | --- | --- | --- | --- |
| R1/G1/G2/S1 | None | In-process servlet and controlled IAM inputs | Finite XML and compiled classes | Wrong HTTP status, identity, scope or compiled guard fails |
| I1 | Existing isolated PostgreSQL only | Same servlet probe with real Process services | Active Flowable instance, held review task and APPLIED receipt | Unexpected native or receipt evidence fails |

### 7.1 System Architecture Design

Reuse the production Process security chain, actual IdP/RBAC filters, PermissionExecutionGuard and ProcessController in the existing MockMvc WebApplicationContextRunner. Mock only credential verification, use controlled PDP responses and reuse unrelated test context stubs. I1 supplies actual release and execution services using the existing disposable Process primary.

### 7.2 High-Level Design

Use a fixed fixture issuer and same text for USER and SERVICE. Under the SERVICE principal use the release-read and START scopes. Independently deny each API via current service policy, then assert release metadata and exercise the separately denied inputs. Repeat the same authorized START once and compare the typed response.

### 7.3 Detailed Design

Finite `governed_start_routes_preserve_service_identity_and_current_permissions` drives the shared `verifyServiceStartRoutes` and independently verifies that only the exact verified SERVICE actor reaches the two handlers, with one release read and two same-command START calls. Reuse typed Process release, model and instance contracts. Distinct denied command IDs share the isolated fixture release and business key so I1 can detect any unexpected side effect.

I1 invokes `verifyGovernedServiceStartAgainstDatabase` after the original main native/outbox assertions. Render the existing baseline BPMN with one different definition ID, leaving its held `review` task and flow intact. Publish a unique test release via the real fixture service. Run the shared servlet probe and query Flowable by scoped fixture business key/returned instance and review task. Query the same Process database for exactly one START receipt under the fixture release and one APPLIED row matching tenant, SERVICE type, issuer fingerprint, subject, command ID, C request digest, correlation and instance.

## 8. Package Structure and Code File Tree

| Operation | Exact path |
| --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessPermissionIntegrationTest.java` |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/ProcessServerPostgresTest.java` |
| CREATE | `docs/egon/spec/2026-11-06-04-32-s07-process-start-authorization.md` |
| CREATE | `docs/egon/plan/2026-11-06-04-33-s07-process-start-authorization.md` |
| CREATE | `docs/egon/evidence/2026-11-06-s07-process-start-authorization.md` |

## 9. Interface Definitions

Use the existing protected release GET and process START POST. The PostgreSQL opt-in is the existing test gate and operator selector. The Step introduces no production route, permission or OAuth scope.

## 10. POJO and Data Model Design

Reuse StartProcessCommand, ProcessReleaseService.Release, ProcessInstanceDTO and ProcessActor. Existing Jackson serializers drive JSON and typed ACKs.

## 11. Database Design

Context-only. The existing test prepares its disposable schema. Scoped JDBC SELECTs observe START receipt rows by independent fixture release and exact actor/command/digest. Existing Flowable services count one runtime instance and held user task. All 28 repository migrations are byte-preserved.

## 12. Frontend Page Design

Context-only. The change is inside PS tests.

## 13. Design Patterns and Architecture Principles

Use the existing Spring Security filter chain, guarded controller and Process transaction. Share the same in-process test probe across finite and PostgreSQL service inputs. The test cases use direct sequencing.

## 14. Test Design

| Test ID | Design |
| --- | --- |
| TEST-001 | R1 changes only the finite no-START-grant fixture to grant START in a disposable copy. Expect one typed 403-versus-200 assertion failure. G1 restores the exact fixture and runs one finite passing case. |
| TEST-002 | G0 checks method absence in the locked baseline. G2 checks compiled symbols and existing PG annotation and excludes PostgreSQL execution reports. S1 verifies strict docs, exact five paths and 28 immutable migrations. |
| TEST-003 | Authorized I1 runs the existing PostgreSQL test method and exercises the same real PS HTTP authorization chain with actual Flowable and Process primary services. |

## 15. Non-functional and Cross-cutting Design

Keep the existing PG class timeout and disposal discipline. The finite gate uses in-process MockMvc and controlled credentials. Evidence records selectors and witness outcomes without storing real credentials or payloads.

## 16. Compatibility, Migration, Rollout, and Rollback

Test-only fixture extension. Revert one focused commit to remove it. Production config, wiring, IAM resources and migrations retain their existing definitions.

## 17. Alternatives and Decisions

Separate PS tests with mocked handler or mocked guard remain useful for their individual boundaries. The shared probe connects those existing capabilities in the operator test while allowing a one-case finite gate.

## 18. Risks and Open Questions

The finite gate cannot observe native or durable database evidence. I1 requires a separately authorized isolated primary. Actual Agent→Process transport, tampered release and fake approval coverage across two services require S07 E.

## 19. Traceability Matrix

| Requirement | Use case | Design | Tests |
| --- | --- | --- | --- |
| REQ-001 | UC-001 | §7/9/10 | TEST-001/003 |
| REQ-002 | UC-001 | §7/9/14 | TEST-001/003 |
| REQ-003 | UC-001 | §7/11 | TEST-003 |
| REQ-004 | UC-002 | §8/14/16 | TEST-001/002 |

## 20. Review and Acceptance

### 20.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8 | Existing Process Server test package and production architecture | None |
| MC-REUSE-001 | Applicable | PASS | EVD-001–004 | Existing filters, guard, controller, native services and PG gate | None |
| MC-DEP-001 | Applicable | PASS | §6/8 | Existing test dependencies; two test Java sources | None |
| MC-NAME-001 | Applicable | PASS | §7/8 | Explicit S07 selector and two named probes | None |
| MC-VALID-001 | Applicable | PASS | §7/14 | Real protected routes, denied scope/resource and valid START | None |
| MC-MODEL-001 | Not applicable | N/A | §10 | Existing immutable orchestration values | None |
| MC-CONVERT-001 | Not applicable | N/A | §10 | Existing controller and SDK conversion | None |
| MC-LOG-001 | Not applicable | N/A | §15 | Existing test and production logging | None |
| MC-BEAN-001 | Not applicable | N/A | §7/16 | Existing production filter beans with fixture-only inputs | None |
| MC-UTIL-001 | Applicable | PASS | §6/7 | JUnit, MockMvc, Mockito, Spring JDBC and JDK | None |
| MC-JSON-001 | Applicable | PASS | §7/10 | Existing managed Jackson and typed command/ACK | None |
| MC-TIME-001 | Applicable | PASS | §6.3/7 | Existing test Clock and token validity window | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/16 | Existing PG gate and isolated test context | None |
| MC-PATTERN-001 | Applicable | PASS | §13 | Existing Spring Security chain plus direct fixture sequencing | None |
| MC-SCOPE-001 | Applicable | PASS | §8/16 | Exact two test paths and three review documents | None |
| MC-TEST-001 | Applicable | PASS | §14 | One finite RED/GREEN and compiled fixture, independent operator I1 | None |
| MC-BLOCKER-001 | Applicable | PASS | §18 | The operator run remains an independent gate | None |

### 20.6 Final verdict

PASS — Ready for user review
