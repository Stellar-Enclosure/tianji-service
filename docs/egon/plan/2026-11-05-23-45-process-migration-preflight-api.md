# T4-01B1 Process owner 迁移预检读取实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-45-process-migration-preflight-api.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 23:45 Asia/Shanghai |
| Updated | 2026-11-05 23:45 Asia/Shanghai |
| Owner | Tianji Process |
| Repository | tianji-service |
| Scope | One default-off REST Query on existing internal preflight and exact dual read authority |
| Source Requirement | Master §19 T4-01 single-instance structural preflight |
| Baseline Revision | Service d9bec9c34bdbaa337cb5bd8eb776e2383bac97c6 |
| Implements Spec | [Process migration preflight API](../spec/2026-11-05-23-44-process-migration-preflight-api.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 23:44 Asia/Shanghai SHA-256 120d4138175d26679af523f250f64140af3274a61b15c8e2fe259005573f1b60 |
| Effective Specs | [Preflight API](../spec/2026-11-05-23-44-process-migration-preflight-api.md), [Existing internal B0](../spec/2026-11-05-18-46-bpmn-migration-preflight.md#7-architecture-design) |
| Depends On Plans | [Existing internal preflight](2026-11-05-18-53-bpmn-migration-preflight.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

Expose one explicit default-off PS GET reusing existing B0 exact read and fixed native structural validation. Bind the dedicated route actor to both original Process and release resources before any storage. Prove reflection presence, actor binding and real finite HTTP prior to static package and one focused commit.

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Preflight API Spec](../spec/2026-11-05-23-44-process-migration-preflight-api.md) §4/7/9 API-001/§14. Scope is source-only; actual deployment authorization and I are independent.

### 2.2 Effective Spec set

| Spec | Sections | Applicability |
| --- | --- | --- |
| Preflight API | §4/7/9/14 | Exact external read and actor binding |
| Existing B0 | §7/9/11/14 | Current narrow engine transaction, statuses and no write |

### 2.3 Superseded or excluded content

None. B0 internal consumer and defaults remain. A3 publication and live migration approval stay separate.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Observable acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | API §4/7/9 | New resource, both existing scopes and all three same-actor decisions | Controller and service |
| REQ-002 | API §4/9 | Exact request, closed eight statuses and no raw native payload | C/PS |
| REQ-003 | API §4/7 | Existing B0 actor, engine and transaction remain | PS adapter |
| REQ-004 | API §4/14/16 | Independent false flag, finite and actual OAS/PaC | PS package |
| REQ-005 | B0 §4 | Exact one structural validation only | Existing B0 finite regression |
| REQ-006 | B0 §4 | No runtime/I enablement from source tests | S1 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

R1 absence → R2 typed authority mismatch → immutable C, exact resource and R3 typed-A endpoint → B controller → all finite → actual static producer → exact commit.

### 4.2 Test-first strategy

R1 reflection wraps actual missing class. R2 typed service A bypasses the route actor and succeeds, failing the expected AccessDenied assertion. R3 typed controller A routes 503 instead of positive 200. Compile failure never counts.

### 4.3 Sequential and parallel boundaries

Existing service test and service A form the R2 phase. After R2 copy service B, C and permissions, then Controller A plus B controller test for R3. After R3 only the Controller flips to B. Web or runtime grant work is outside this commit.

### 4.4 Commit boundaries

One 11-path Service commit with eight source/test/resource paths and exact three governing docs.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Implementation handling |
| --- | --- | --- | --- |
| Dedicated owner GET | Add / §7.0 | No current public preflight route | One Query and extra false flag |
| Same actor check | Add / §7.0 | Original two resource guards agree only with each other | Pass and bind expected actor; keep original method |
| Physical inspection | Keep / §7.0 | Existing ProcessMigrationPreflightService | No SQL/Flowable semantics change |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| Owner authority seam | REQ-001/003 | R2 | Existing dual guard/service |
| Protected HTTP and C | REQ-001/002/004 | R1/R3 | Bound service, current PS permissions |
| Static package and source proof | REQ-004/005/006 | G1/P1/S1 | Existing producer and strict docs |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Technical owner boundary | Existing B0 service and PS MVC | §7/8 | One qualified controller, private existing service entry | G1 |
| Current validation | Existing exact C validators and dual resource check | §7/9 | Exact two parameters, full actor equality, fixed output | R2/G1 |
| JSON and time | Existing Jackson and DB Instant | §6.3/10 | Closed DTO and exact UTC microseconds | G1/P1 |
| Configuration and persistence | Existing PS application.yml and B0 read | §11/16 | One false HTTP flag, no dependency or storage change | P1/S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Scoped authorization | TianjiProcessAccess and existing GET | Current policy plus exact actor | Fits | Reuse plus exact bind | No dependency | Step 1 MC-VALID-001 |
| Native validation | Existing B0 | Exact one validateMigration | Fits | Reuse unchanged | No engine code | Step 1 MC-REUSE-001 |
| Public C and package | Existing Process OAS and read DTO | Existing validators/static producer | Fits | Closed projection | One C DTO and OAS | Step 1 MC-JSON-001 |

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
| Rule 1 | §8/10 | C/PS naming | Files 1–6 | Exact DTO and controller names | G1 | Step 1 | PASS |
| Rule 2 | §7/9 | Current B0 guard and neutral C | Files 1–6 | Exact owner before storage and input/output | R2/G1 | Step 1 | PASS |
| Rule 3 | §10 | Immutable technical DTO | Files 4/6 | Immutable record and direct mapping | G1 | Step 1 | PASS |
| Rule 4 | §8 | Existing named technical service | File 6 | Qualified existing dependencies, no log | G1 | Step 1 | PASS |
| Rule 5 | §6/7 | Existing JDK/Spring and C | Files 3/4/6 | Reuse rather than add dependencies | G1 | Step 1 | PASS |
| Rule 6 | §9 | Current PS JSON and OAS | Files 4/6/8 | Closed type, status and errors | G1/P1 | Step 1 | PASS |
| Rule 7 | §16 | Current PS config | File 7 | Separate false property | P1 | Step 1 | PASS |
| Rule 9 | §13 | Current Query adapter | Files 3/6 | Direct existing authority and read | G1 | Step 1 | PASS |
| Rule 10 | §6.3 | Existing owning UTC clock | Files 4/6 | Preserve Instant precision | G1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing technical read owner | Files 1–11 | No storage/write/DDL/event | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | java.time.Instant | Not persisted | UTC Z | Explicit UTC Instant | Wire UTC Z | Epoch through year9999, no local time conversion | Same existing DB clock, micros | Old timestamps unchanged | Step 1 G1 TEST-002/003 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightControllerTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java` |
| Create | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessMigrationPreflightDTO.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java` |
| Create | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightController.java` |
| Modify | `stellar-enclosure-tianji-process-server/src/main/resources/application.yml` |
| Create | `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-migration-preflight-v1.json` |
| Create | `docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md` |
| Create | `docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md` |
| Create | `docs/egon/evidence/2026-11-05-process-migration-preflight-api.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact clean Service `d9bec9c34bdbaa337cb5bd8eb776e2383bac97c6`. Lock exact paths and SHA, preserve current unrelated work.

### 6.2 Build, test, and environment prerequisites

Existing offline source reactor and settings, JDK25, Python 3.11+ static archive. Verified prior PS producer from completed recovery API Step.

### 6.3 Immutable constraints and approved decisions

No actual DB, migration, app, listener, provider or Browser. No schema/POM, actual grants, engine mutation or external owner change.

### 6.4 Plan Clarifications

| ID | Choice | Evidence and consequence |
| --- | --- | --- |
| PC-001 | Separate HTTP flag | Existing internal opt-in alone must not begin serving HTTP |
| PC-002 | Preserve source statuses exactly | Native positive only structural; TARGET_UNAVAILABLE and pin mismatch stay 200 |
| PC-003 | Three same actor decisions | Dedicated policy cannot replace existing separate current GET and RELEASE_GET |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付单实例迁移预检的 owner 读取

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006.
- Dependencies: Reviewed Spec/Plan, exact current owner, existing internal B0 physical primary guard.
- Baseline state: Existing internal method and flags, no public route.
- Observable outcome: Exact current owner gets existing finite structural observations under the dual policy.
- End state: Eleven selected files and real static package evidence.
- Test-first gate: Required. R1 missing class, R2 exact actor binding, R3 actual HTTP response before B.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightControllerTest.java`

- Purpose: Freeze actual public route and closed exact HTTP.
- Symbols: migration_preflight_http_route_exists; returns_exact_owner_pinned_structural_observation; denies_before_inspecting_and_rejects_open_or_inexact_input; preserves_closed_negative_and_controlled_source_errors; registers_only_when_internal_preflight_and_http_are_both_explicit.
- Repository evidence: Current ProcessControllerTest and ProcessApiErrors.
- Dependencies and consumers: R1 existing sources; R3 typed controller A and Files 3–5; G1 all B.
- Why now: Actual absence before source, exact mock HTTP after.
- Contract/signature changes: R1 only reflection; B owner/route/false conditions and fixed errors.
- Input/output and state mapping: R1 only reflection; B owner/route/false conditions and fixed errors.
- Error and edge behavior: No preflight on denied/untrusted inputs; compare exact response fields and flags.
- Standards impact: MC-SCOPE-001 and R1/R3 and five G1 cases.
- Literal rule enforcement: Rule 1, Rule 2, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test void migration_preflight_http_route_exists() {
    assertDoesNotThrow(() -> Class.forName(CONTROLLER_FQN)); // R1 wraps ClassNotFoundException.
}
@Test void returns_exact_owner_pinned_structural_observation() {
    when(service.inspectForResourceActor(OWNER, QUERY)).thenReturn(validNativeObservation);
    mvc.perform(request(INSTANCE)).andExpect(status().isOk()); // R3 typed A returns 503.
    assertExactNoStoreBodyAndResourceBeforeService();
}
```

- Verification contribution: R1/R3 and five G1 cases.
- After this file: Red first, use full test after typed A.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java`

- Purpose: Freeze same actor across dedicated and original resources.
- Symbols: denies_a_different_route_actor_before_opening_storage.
- Repository evidence: Existing read-only B0 fixture with mocked DB and exact authority.
- Dependencies and consumers: File 3 typed A then B.
- Why now: Prove existing two guards alone do not bind route identity.
- Contract/signature changes: One new finite method, prior cases stay.
- Input/output and state mapping: One new finite method, prior cases stay.
- Error and edge behavior: Mismatched issuer or null rejects before data source; same actor preserves original result.
- Standards impact: MC-SCOPE-001 and R2 actual no exception and G1 five service cases.
- Literal rule enforcement: Rule 2, Rule 5, Rule 11.
- Implementation pseudocode:

```java
var foreign = new ProcessActor(OWNER.tenantId(), OWNER.subjectType(), OWNER.subjectId(), "f".repeat(64));
assertThrows(AccessDeniedException.class, () -> service.inspectForResourceActor(foreign, query));
verify(access).require(TianjiProcessPermissions.GET_API);
verify(access).require(TianjiProcessPermissions.RELEASE_GET_API);
verifyNoInteractions(source, builder);
```

- Verification contribution: R2 actual no exception and G1 five service cases.
- After this file: With File 3 A run exact R2.

#### File 3 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java`

- Purpose: Bind exact route actor while preserving existing B0.
- Symbols: inspect, inspectForResourceActor, inspectBoundOwner.
- Repository evidence: Existing two exact read policies and physical observation path.
- Dependencies and consumers: File 2 test and new HTTP adapter.
- Why now: Typed A compiles and proves why binding is required.
- Contract/signature changes: Old inspect still reaches same dual-guard implementation; new bound overload additionally compares full route actor.
- Input/output and state mapping: Old inspect still reaches same dual-guard implementation; new bound overload additionally compares full route actor.
- Error and edge behavior: Null, LEGACY or mismatch before storage, existing Flowable/DB behavior unchanged.
- Standards impact: MC-SCOPE-001 and R2 typed A delegates without bind, B preserves old/new.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 9, Rule 11.
- Implementation pseudocode:

```java
public ProcessMigrationPreflightDTO inspect(ValidateProcessMigrationQuery query) {
    return inspectBoundOwner(query, null);
}
public ProcessMigrationPreflightDTO inspectForResourceActor(ProcessActor routeActor, ValidateProcessMigrationQuery query) {
    requireVerifiedRouteActor(routeActor);
    return inspectBoundOwner(query, routeActor);
}
// Shared entry calls both old guards, checks same ProcessActor before original read.execute.
```

- Verification contribution: R2 typed A delegates without bind, B preserves old/new.
- After this file: Replace A with B after R2.

#### File 4 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessMigrationPreflightDTO.java`

- Purpose: Declare neutral closed structural report.
- Symbols: ProcessMigrationPreflightDTO and Status.
- Repository evidence: Existing B0 exact immutable result and C validation.
- Dependencies and consumers: Files 1/6/8.
- Why now: Fixed response before typed controller A.
- Contract/signature changes: Exact six fields and eight status names, activity only two native branches.
- Input/output and state mapping: Exact six fields and eight status names, activity only two native branches.
- Error and edge behavior: Bad identifiers or non-microsecond/unbounded UTC rejected.
- Standards impact: MC-SCOPE-001 and R3 compilation and G1 actual closed wire.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 6, Rule 10.
- Implementation pseudocode:

```java
public record ProcessMigrationPreflightDTO(String instanceId, String sourceReleaseVersion, String targetReleaseVersion,
                                           Status status, String activityId, Instant observedAt) {
    // Existing exact instance/release validators and immutable micros UTC.
    // Only NATIVE_STRUCTURE_VALIDATED_ONLY and NATIVE_REJECTED require authored activityId.
}
```

- Verification contribution: R3 compilation and G1 actual closed wire.
- After this file: C neutral schema exists.

#### File 5 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`

- Purpose: Declare only dedicated PaC key and label.
- Symbols: MIGRATION_PREFLIGHT_API, api.process.migrationPreflight.
- Repository evidence: Current Process READ and RELEASE_READ policy/labels.
- Dependencies and consumers: File 6 A/B and static package.
- Why now: Bind exact resource without changing any existing permission code.
- Contract/signature changes: One key and English label with existing two scopes.
- Input/output and state mapping: One key and English label with existing two scopes.
- Error and edge behavior: No grants or other resource mutation.
- Standards impact: MC-SCOPE-001 and G1 reflection and P1 exact resource delta.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11.
- Implementation pseudocode:

```java
public static final String MIGRATION_PREFLIGHT_API = "tianji.api.process.migration.preflight";
// Add @PermissionMessage under tianji.process.permissions with key api.process.migrationPreflight.
// en-US "Read an owned BPMN migration preflight"; prior labels remain.
```

- Verification contribution: G1 reflection and P1 exact resource delta.
- After this file: Ready for controller A/B.

#### File 6 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightController.java`

- Purpose: Attach exact default-off HTTP consumer.
- Symbols: ProcessMigrationPreflightController#get and publicReport.
- Repository evidence: Existing PS ProcessController, B0 service, exact C.
- Dependencies and consumers: Files 1 and 3–5.
- Why now: A supplies typed 503 for R3, B reads exact existing service.
- Contract/signature changes: New dedicated authorized actor, two exact params and same-run/target affinity, one immutable response.
- Input/output and state mapping: New dedicated authorized actor, two exact params and same-run/target affinity, one immutable response.
- Error and edge behavior: Malformed request 400, old guards 403, owner absent 404, unknown source 503.
- Standards impact: MC-SCOPE-001 and R3 on A then G1 B, P1 annotated route.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@GetMapping
ResponseEntity<ProcessMigrationPreflightDTO> get(String instanceId, MultiValueMap<String,String> parameters) {
    var actor = access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API);
    requireExactlyOneOfBothApprovedParameters(parameters);
    var query = new ValidateProcessMigrationQuery(instanceId, parameters.getFirst("targetReleaseVersion"),
            parameters.getFirst("expectedTargetManifestSha256"));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(publicReport(query, service.inspectForResourceActor(actor, query)));
}
// Servlet plus api.enabled, migration-preflight.enabled and migration-preflight.http-enabled.
```

- Verification contribution: R3 on A then G1 B, P1 annotated route.
- After this file: After R3 use only B.

#### File 7 — `MODIFY stellar-enclosure-tianji-process-server/src/main/resources/application.yml`

- Purpose: Keep both original and new HTTP defaults off.
- Symbols: stellar.tianji.process.migration-preflight.http-enabled.
- Repository evidence: Existing PS application.yml and named B0 service conditions.
- Dependencies and consumers: File 6 route switch.
- Why now: Do not expose HTTP for existing internal-only opt-in.
- Contract/signature changes: Single new false flag under migration-preflight.
- Input/output and state mapping: Single new false flag under migration-preflight.
- Error and edge behavior: No changes to existing B0 or unrelated settings.
- Standards impact: MC-SCOPE-001 and P1 actual packaged YAML and G1 flags.
- Literal rule enforcement: Rule 7, Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    process:
      migration-preflight:
        enabled: false
        http-enabled: false
        # HTTP is separately opt-in and requires both original preflight and Process API flags.
```

- Verification contribution: P1 actual packaged YAML and G1 flags.
- After this file: Default source closed.

#### File 8 — `CREATE stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-migration-preflight-v1.json`

- Purpose: Package static exact scoped OpenAPI.
- Symbols: getOwnedProcessMigrationPreflight and Preflight/Error.
- Repository evidence: Existing Process static OAS producer.
- Dependencies and consumers: Files 4–7.
- Why now: After fixed route and schema.
- Contract/signature changes: One exact GET, both scopes, two required query values, closed response.
- Input/output and state mapping: One exact GET, both scopes, two required query values, closed response.
- Error and edge behavior: No runtime documentation endpoint or prior OAS edits.
- Standards impact: MC-SCOPE-001 and P1 exact packaged source and OAS contract.
- Literal rule enforcement: Rule 6, Rule 11.
- Implementation pseudocode:

```json
{
  "paths": {"/api/tianji/v1/process-instances/{instanceId}/migration-preflight": {
    "get": {"operationId": "getOwnedProcessMigrationPreflight",
            "security": [{"oauth2": ["tianji:process:read", "tianji:process:release:read"]}]}
  }},
  "components": {"schemas": {"Preflight": {"additionalProperties": false}}}
}
```

- Verification contribution: P1 exact packaged source and OAS contract.
- After this file: All B ready for G1/P1.

#### File 9 — `CREATE docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md`

- Purpose: Archive exact reviewed SPEC.
- Symbols: Governing source metadata and reciprocal link.
- Repository evidence: Existing strict Doc conventions.
- Dependencies and consumers: Approved scratch and reviewed bytes.
- Why now: Keep source and review in the same commit.
- Contract/signature changes: Exact copy.
- Input/output and state mapping: Exact copy.
- Error and edge behavior: Wrong hash or occupied target blocks source.
- Standards impact: MC-SCOPE-001 and S1 strict validator and exact SHA.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md
printf '%s  %s\n' "$PREFLIGHT_APPROVED_SPEC_SHA" "$PREFLIGHT_APPROVED_SPEC" | shasum -a 256 -c -
cp -n "$PREFLIGHT_APPROVED_SPEC" docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md
cmp "$PREFLIGHT_APPROVED_SPEC" docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md
```

- Verification contribution: S1 strict validator and exact SHA.
- After this file: Reviewed doc archived.

#### File 10 — `CREATE docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md`

- Purpose: Archive exact reviewed PLAN.
- Symbols: Governing source metadata and reciprocal link.
- Repository evidence: Existing strict Doc conventions.
- Dependencies and consumers: Approved scratch and reviewed bytes.
- Why now: Keep source and review in the same commit.
- Contract/signature changes: Exact copy.
- Input/output and state mapping: Exact copy.
- Error and edge behavior: Wrong hash or occupied target blocks source.
- Standards impact: MC-SCOPE-001 and S1 strict validator and exact SHA.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md
printf '%s  %s\n' "$PREFLIGHT_APPROVED_PLAN_SHA" "$PREFLIGHT_APPROVED_PLAN" | shasum -a 256 -c -
cp -n "$PREFLIGHT_APPROVED_PLAN" docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md
cmp "$PREFLIGHT_APPROVED_PLAN" docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md
```

- Verification contribution: S1 strict validator and exact SHA.
- After this file: Reviewed doc archived.

#### File 11 — `CREATE docs/egon/evidence/2026-11-05-process-migration-preflight-api.md`

- Purpose: Record actual finite and packaging proof.
- Symbols: R1/R2/R3/G1/P1/S1.
- Repository evidence: Existing B0 evidence and current producer.
- Dependencies and consumers: Actual owning reports and scope.
- Why now: After gates record only observed outcomes.
- Contract/signature changes: Exact finite and archive/PaC/OAS/default hashes.
- Input/output and state mapping: Exact finite and archive/PaC/OAS/default hashes.
- Error and edge behavior: Missing actual tests or package blocks closure.
- Standards impact: MC-SCOPE-001 and S1 objective source evidence.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record actual R1/R2/R3 fixed failures and fresh G1/P1 owning counts.
Record current archive, PaC and OAS hashes, exact both-scope delta and false flags.
Check immutable SQL, exact paths, one commit and clearly separate runtime I.
```

- Verification contribution: S1 objective source evidence.
- After this file: Exact eleven paths ready to stage.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/R2/R3/G1/P1/S1 in §8.
- Expected result: Three exact assertion failures, G1/P1 50 finite passes, exact package/flags and scope.
- Failure returns to: Files 1–3 for presence and scope, Files 4–6 for wire/HTTP, Files 7–8 for flag and package.
- Completion criteria: Existing native-only semantics, exact current owner, preserved permissions and static artifact proof.
- Rollback: Revert one commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightControllerTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessMigrationPreflightDTO.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightController.java`, `stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-migration-preflight-v1.json`, `docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md`, `docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md`, `docs/egon/evidence/2026-11-05-process-migration-preflight-api.md`.
- Commit: feat(process): expose owned migration preflight.

## 8. Test, Validation, and Quality Gates

| Gate | Exact source phase | Expected |
| --- | --- | --- |
| R1 | Only new controller test RED | migration_preflight_http_route_exists fails on ClassNotFoundException |
| R2 | Existing service test B plus typed service A | denies_a_different_route_actor_before_opening_storage fails expected AccessDenied, nothing thrown |
| R3 | Existing service B, C/permissions B, new controller A and new controller test B | returns_exact_owner_pinned_structural_observation fails 200 expected vs 503 |
| G1 | All B | New controller 5, existing service updated 5, Storage config 11, Permission 12, ProcessController 7, C Orchestration 10, total 50 |
| P1 | Clean offline permission-producer | Same 50 finite, actual owner artifact and exact static scope |
| S1 | Strict docs and exact source closure | Eleven paths and no old SQL/POM change |

Run each phase separately with fresh owning XML. RED assertions have zero errors/skips. R1 uses red test, R2 typed service A, R3 typed controller A. No I/E selections.

```sh
preflight_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-01/migration-preflight-api
preflight_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
preflight_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
preflight_base=d9bec9c34bdbaa337cb5bd8eb776e2383bac97c6
test "$(git rev-parse --verify HEAD)" = "$preflight_base"
preflight_core=ProcessMigrationPreflightControllerTest,ProcessMigrationPreflightServiceTest,ProcessStorageConfigurationTest,ProcessPermissionIntegrationTest,ProcessControllerTest,OrchestrationContractTest
preflight_finite() {
  JAVA_HOME="$preflight_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$preflight_settings" -s "$preflight_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
preflight_finite 'ProcessMigrationPreflightControllerTest#migration_preflight_http_route_exists'
preflight_finite 'ProcessMigrationPreflightServiceTest#denies_a_different_route_actor_before_opening_storage'
preflight_finite 'ProcessMigrationPreflightControllerTest#returns_exact_owner_pinned_structural_observation'
preflight_finite "$preflight_core"
JAVA_HOME="$preflight_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$preflight_settings" -s "$preflight_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$preflight_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-01-process-migration-preflight-api-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$preflight_core"
preflight_ps=stellar-enclosure-tianji-process-server
printf '%s  %s\n' '9515467815b939a4b2ddfdf819059071991410206e2484c7c94c548e6ff82b52' "$preflight_root/packaged-proof.py" | shasum -a 256 -c -
python3.14 "$preflight_root/packaged-proof.py" "$preflight_ps/target/stellar-enclosure-tianji-process-server-exec.jar" \
  /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/recovery-disposition-api/root-process-server-manifest.json \
  "$preflight_ps/src/main/resources/META-INF/tianji/openapi/process-migration-preflight-v1.json" "$preflight_base"
```

Baseline PS raw manifest SHA 52b44c578d3d570402ad1a2e2de3eb80593a9408ab1e9e868c29eee7ed2619dd. P1 checks unique owning root OAS/PaC, actual source bytes, two required query values, both old scopes and READ permission codes, one exact protected GET/HEAD resource and label, unchanged other policy fields except artifact metadata, and both false flags in actual archive. No process launch or actual authority changes.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git add -- \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightControllerTest.java \
  stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightServiceTest.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/execution/ProcessMigrationPreflightService.java \
  stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/orchestration/ProcessMigrationPreflightDTO.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/security/TianjiProcessPermissions.java \
  stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/api/ProcessMigrationPreflightController.java \
  stellar-enclosure-tianji-process-server/src/main/resources/application.yml \
  stellar-enclosure-tianji-process-server/src/main/resources/META-INF/tianji/openapi/process-migration-preflight-v1.json \
  docs/egon/spec/2026-11-05-23-44-process-migration-preflight-api.md \
  docs/egon/plan/2026-11-05-23-45-process-migration-preflight-api.md \
  docs/egon/evidence/2026-11-05-process-migration-preflight-api.md
git diff --cached --check
git diff --cached --name-status
```

Exact 11 paths and operation list. Immutable path output empty. One commit and clean check after verification.

## 9. Migration, Compatibility, Rollout, and Rollback

No database migration, existing internal behavior change, role assignment or runtime enablement. Separate false HTTP opt-in and no mutation. Revert one source commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates |
| --- | --- | --- | --- | --- |
| REQ-001 | API §4/7 | Step 1 | 1–6 | R2/G1/P1 |
| REQ-002 | API §4/9 | Step 1 | 1/4/6/8 | R3/G1 |
| REQ-003 | API §4/7 | Step 1 | 2/3/6 | R2/G1 |
| REQ-004 | API §4/14 | Step 1 | 7–11 | P1/S1 |
| REQ-005 | B0 §4 | Step 1 | 2/3 | G1 |
| REQ-006 | B0 §4 | Step 1 | 7–11 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Observed structure can change after the read. Actual runtime I and IAM/role activation remain separate, as do A3 and migration execution. Existing plain timer restrictions preserved.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

One actual operator-consumable current-owner read on an existing guarded observation.

### 12.2 Spec consistency

Exactly original state and transaction proof, with same verified actor across all resource checks and separate explicit HTTP flag.

### 12.3 Repository executability

Exact paths, real finite selectors, current source producer and strict docs.

### 12.4 Test and release completeness

Three RED phases and one focused final scope. No live or mutation claim.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §7/8/14 | Existing C/PS boundary | None |
| MC-REUSE-001 | Applicable | PASS | §7/8/14 | Existing permission/JDBC/receipt adapters | None |
| MC-DEP-001 | Applicable | PASS | §7/8/14 | No dependency change | None |
| MC-NAME-001 | Applicable | PASS | §7/8/14 | Exact Query/DTO/adapter symbols | None |
| MC-VALID-001 | Applicable | PASS | §7/8/14 | Current authority, query, exact cursor | None |
| MC-MODEL-001 | Applicable | PASS | §7/8/14 | Immutable C value DTOs | None |
| MC-CONVERT-001 | Not applicable | N/A | §7/8/14 | No mapping layer or PO | None |
| MC-LOG-001 | Not applicable | N/A | §7/8/14 | No new log/diagnostic echo | None |
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Qualified existing technical PS query and current guard | None |
| MC-UTIL-001 | Applicable | PASS | §7/8/14 | Existing JDK and length-safe digest | None |
| MC-JSON-001 | Applicable | PASS | §7/8/14 | Closed wire/OAS and exact errors | None |
| MC-TIME-001 | Applicable | PASS | §7/8/14 | UTC microsecond/DB clock | None |
| MC-CONFIG-001 | Applicable | PASS | §8/16 | One owner-only default-false property, existing settings remain | None |
| MC-PATTERN-001 | Applicable | PASS | §7/8/14 | Existing owner query adapter | None |
| MC-SCOPE-001 | Applicable | PASS | §7/8/14 | One exact route, zero schema/trace scope | None |
| MC-TEST-001 | Applicable | PASS | §7/8/14 | Finite plus actual package and separate I | None |
| MC-BLOCKER-001 | Applicable | PASS | §7/8/14 | Source review, runtime separate | None |

### 12.6 Final verdict

PASS — Ready for user review
