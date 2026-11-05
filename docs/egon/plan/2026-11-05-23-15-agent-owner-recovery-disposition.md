# T4-03 Agent owner 恢复处置观察实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-05-23-15-agent-owner-recovery-disposition.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-05 23:15 Asia/Shanghai |
| Updated | 2026-11-05 23:15 Asia/Shanghai |
| Owner | Tianji Agent |
| Repository | tianji-service |
| Scope | Default-off current Agent owner Query and static package proof |
| Source Requirement | Master §19 T4-03, bounded observation and manual review |
| Baseline Revision | Service 1ae891f2aca338cceb80fc0d8517c100a53ae55b |
| Implements Spec | [Agent owner recovery disposition](../spec/2026-11-05-23-14-agent-owner-recovery-disposition.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-05 23:14 Asia/Shanghai SHA-256 66a298ff437a53703bc9d6d084f5611afc41321c7b4f40d8b3cced3351c62d7e |
| Effective Specs | [Agent owner recovery disposition](../spec/2026-11-05-23-14-agent-owner-recovery-disposition.md), [AR owner evidence](../spec/2026-11-05-16-52-agent-recovery-disposition.md#7-architecture-design) |
| Depends On Plans | [Internal AR recovery disposition](2026-11-05-17-42-agent-recovery-disposition.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | None |
| Superseded By | None |
| Related Plans | None |

## 1. Summary

One dedicated AS owner GET consumes the existing AR inspection. The same exact read authorization and a new resource key protect a closed C projection. R1 and typed R2 precede complete B, then finite G1, packaged static P1 and one exact commit.

## 2. Target Spec and Effective Design

### 2.1 Primary target

[Spec](../spec/2026-11-05-23-14-agent-owner-recovery-disposition.md) §4/§7/§9 API-001/§14. Approved source does not configure runtime access, IAM or external recovery.

### 2.2 Effective Spec set

| Spec | Sections | Applicability |
| --- | --- | --- |
| Agent owner recovery disposition | §4/§7/§9/§14 | Exact current owner REST, public projection, gating and tests |
| AR owner evidence | §7/§9/§14 | Internal reader, scope, uncertainty, prior no-write and behavior compatibility |

### 2.3 Superseded or excluded content

None. Existing internal PS Worker recovery and Agent Tool/result behavior remain separate.

## 3. Effective Requirements and Acceptance

| Requirement | Spec | Acceptance | Impact |
| --- | --- | --- | --- |
| REQ-001 | §4/9 | Current actor and protected exact route, no widening | AS controller and PaC |
| REQ-002 | §4/7 | One existing scoped AR read, exact output owner affinity and controlled errors | C/AS |
| REQ-003 | §4/7/9 | Closed lifecycle/reason/effect and tri-state, no per-tool IDs | C/AS |
| REQ-004 | §4/14/16 | Default off and existing read prerequisite, finite/package provenance | AS config/OAS |
| REQ-005 | Effective AR §4 | Fixed scoped evidence only, no provider or mutation | G1/P1/S1 |
| REQ-006 | Effective AR §4 | Approved explicit current-owner read only; no checkpoint/takeover/write | G1/S1 |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

A reflection-safe endpoint test first. Create immutable C shape and exact new resource constant, then install typed controller A and complete test for R2. Replace controller with B using unchanged ledger. Add default setting and static docs, execute finite and package gates.

### 4.2 Test-first strategy

R1 against old actual classpath fails on ClassNotFound. R2 typed A has exact route/permissions and returns controlled 503 rather than the expected positive 200. All B tests must then pass.

### 4.3 Sequential and parallel boundaries

Web consumer drafts can be separate and must use exact C/API. This commit owns only C/AS and source docs. Dependency order for tests is File 1 RED, then Files 2–4 typed A, File 1 full test, File 4 B and remaining source.

### 4.4 Commit boundaries

One focused nine-path Service commit. No test tooling, jar, fixtures, generated output, or runtime config outside the reviewed false default.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Implementation handling |
| --- | --- | --- | --- |
| Dedicated HTTP read | Add / §7.0 | Current AgentRunController and owner detail | Protected explicit opt-in; no mutation |
| Closed C DTO | Add / §7.0 | Existing private AR shape | Omit attempt/revision, enforce finite evidence |
| Internal evidence source | Keep / §7.0 | AgentLedgerService.inspectRecovery | No changes to AR reader or storage |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Dependencies | Owning Step |
| --- | --- | --- | --- | --- |
| Contract and REST | REQ-001–004 | R1/R2 | Existing C and current auth |
| Evidence projection | REQ-002/003/005 | G1 | Existing owner AR read |
| Default and exact package | REQ-004/006 | P1/S1 | Existing AS permission producer |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Implementation obligation | Gate |
| --- | --- | --- | --- | --- |
| Architecture and naming | Existing AS owner technical controller | §6/7 | One sibling technical controller and immutable C value | G1 |
| Validation and JSON | Existing owner path/AgentApiErrors | §7/9 | Exact input, guard and closed output | R2/G1/P1 |
| Time and tools | AR primary snapshot and JDK Instant | §6.3 | Do not reinterpret DB clock or call providers | G1 |
| Configuration, persistence | AS existing flags and AR existing read | §11/16 | One false property, no schema or dependency | P1/S1 |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Current authority | TianjiAgentAccess and current GET | require and existing READ scope | Fits exact resource | Reuse | Dedicated key only | Step 1, MC-VALID-001 |
| Owner recovery state | AgentLedgerService.inspectRecovery | Existing bounded physical primary and safe projection | Fits | Reuse | AS mapping only | Step 1, MC-REUSE-001 |
| Closed wire | Existing neutral C and static producer | Agent context/correlation | Fits | Reuse technique | One DTO and exact OAS | Step 1, MC-JSON-001 |

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
| Rule 1 | §6/10 | Current C/AS names | Files 1–4 | Exact DTO and controller | G1 | Step 1 | PASS |
| Rule 2 | §7/9 | Current AgentAccess and C records | Files 1/2/4 | Guard first, exact runId and bounded shape | R2/G1 | Step 1 | PASS |
| Rule 3 | §10 | Immutable current neutral values | Files 2/4 | Immutable record and direct technical mapping | G1 | Step 1 | PASS |
| Rule 4 | §6/8 | Existing technical MVC constructors | File 4 | Reuse current technical injection, no business logging | G1 | Step 1 | PASS |
| Rule 5 | §6 | Existing JDK and AR Query | Files 2/4 | No dependency or custom utilities | G1 | Step 1 | PASS |
| Rule 6 | §9/10 | Existing Jackson and closed enum schema | Files 2/4/6 | Fixed JSON field, enum and safe error | G1/P1 | Step 1 | PASS |
| Rule 7 | §16 | Existing AS application.yml | File 5 | Explicit false without profile drift | P1 | Step 1 | PASS |
| Rule 9 | §7/13 | Current owner Query | File 4 | Direct composition on actual reader | G1 | Step 1 | PASS |
| Rule 10 | §6.3 | AR database clock + Instant | Files 1/2/4 | Exact UTC microseconds | G1 | Step 1 | PASS |
| Rule 11 | §7/11 | Existing AS/AR ownership | Files 1–9 | No write, new data store, schema or event | S1 | Step 1 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | observedAt | Occurred instant | java.time.Instant | Not persisted | UTC Z | Explicit UTC Instant | Wire UTC Z | Epoch through year9999, no local-time conversion | Existing primary clock microseconds | Prior timestamps unchanged | Step 1 G1 TEST-002 |

## 5. Change File Tree

| Operation | Exact path |
| --- | --- |
| Create | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionControllerTest.java` |
| Create | `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryDispositionDTO.java` |
| Modify | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java` |
| Create | `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionController.java` |
| Modify | `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` |
| Create | `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json` |
| Create | `docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md` |
| Create | `docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md` |
| Create | `docs/egon/evidence/2026-11-05-agent-owner-recovery-disposition.md` |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

Exact clean Service `1ae891f2aca338cceb80fc0d8517c100a53ae55b`. Check source/Spec/Plan hash and exactly nine paths before changing tracked files. Other repo changes remain isolated.

### 6.2 Build, test, and environment prerequisites

Use existing offline source reactor, isolated settings and JDK25. Static package checker needs Python 3.11+ and existing verified baseline AS producer manifest.

### 6.3 Immutable constraints and approved decisions

No SQL/POM, owner DB/roles, application, listener, network, browser, vendor or tool execution. No AR/PS/CL/Web code scope, no current grant enablement.

### 6.4 Plan Clarifications

| ID | Choice | Evidence and effect |
| --- | --- | --- |
| PC-001 | Public DTO omits internal revision and attempt | Named consumer needs disposition only, no CAS/use-on-write affordance |
| PC-002 | UI and actual role rollout separate | Current owner auth and one new exact PaC resource are source-only |
| PC-003 | Tool coverage unavailable is a valid 200 | Exact owned run may exist without V13 evidence; actual source/affinity failure stays 503 |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — 交付 Agent owner 恢复处置读取

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006.
- Dependencies: Reviewed exact Spec/Plan, existing AR reader, current AS guard and prior package provenance.
- Baseline state: Existing AR inspection and no production consumer for it; new route/controller absent.
- Observable outcome: Current exact owner gets closed disposition evidence with stable no-store/errors, while feature defaults off.
- End state: Exactly nine selected Service paths with finite/package/static evidence.
- Test-first gate: Required. R1 exact missing class; R2 actual 200 versus 503. No compile-error proof.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionControllerTest.java`

- Purpose: Freeze missing route, actual same-owner HTTP and default-off flags.
- Symbols: recovery_disposition_route_exists; exposes_only_same_owner_closed_recovery_evidence; denies_before_read_and_rejects_non_exact_path_or_extra_query; distinguishes_unavailable_coverage_from_foreign_or_inconsistent_evidence; registers_only_when_all_existing_prerequisites_and_explicit_flag_are_true.
- Repository evidence: Current AgentRunHttpTest uses finite MockMvc and safe AgentApiErrors.
- Dependencies and consumers: Old baseline R1, Files 2–4 A for R2 and B for G1.
- Why now: One genuine missing-class assertion, then typed read behavior.
- Contract/signature changes: Reflection-only red test, final typed owner evidence and context-runner test.
- Input/output and state mapping: Reflection-only red test, final typed owner evidence and context-runner test.
- Error and edge behavior: Prove 503 on A, wrong scope/query/inconsistent stored shape and no lookup after denied.
- Standards impact: MC-SCOPE-001 and R1, R2 and five G1 controller/condition tests.
- Literal rule enforcement: Rule 1, Rule 2, Rule 6, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@Test void recovery_disposition_route_exists() {
    assertDoesNotThrow(() -> Class.forName(CONTROLLER_FQN)); // Baseline ClassNotFoundException.
}
@Test void exposes_only_same_owner_closed_recovery_evidence() {
    when(ledger.inspectRecovery(OWNER, new GetAgentRunQuery(RUN))).thenReturn(exactOwnerEvidence);
    mockMvc.perform(get(PATH, RUN)).andExpect(status().isOk()); // Typed A returns 503.
    assertExactWireAndNoStore();
}
```

- Verification contribution: R1, R2 and five G1 controller/condition tests.
- After this file: Stage red now; switch to B only when Files 2–4 A compile.

#### File 2 — `CREATE stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryDispositionDTO.java`

- Purpose: Define a neutral current-owner immutable projection.
- Symbols: AgentRecoveryDispositionDTO, ToolEvidenceDTO and nested closed enums.
- Repository evidence: Existing internal AR disposition record and neutral C Java types.
- Dependencies and consumers: File 1 B and File 4.
- Why now: Compile exact bounded response before the HTTP seam.
- Contract/signature changes: Exact seven outer fields and five nested fields, no attempt/revision; preserve tri-state/effect.
- Input/output and state mapping: Exact seven outer fields and five nested fields, no attempt/revision; preserve tri-state/effect.
- Error and edge behavior: Malformed UTC, lifecycle/reason, absent/invalid tri-state and false conflict rejected.
- Standards impact: MC-SCOPE-001 and R2 typed availability and G1 closed proof.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10.
- Implementation pseudocode:

```java
public record AgentRecoveryDispositionDTO(String runId, Instant observedAt, Lifecycle lifecycle, Reason reason,
        AgentEffect agentEffect, ToolEvidenceDTO toolEvidence, boolean evidenceConflict) {
    public record ToolEvidenceDTO(ToolCoverage coverage, Boolean hasDispatching, Boolean hasUnknownResult,
                                  Boolean hasUnresolvedWrite, Boolean hasLocalReceipt) { }
}
// Verify exact UTC millis/micros, closed reason/lifecycle, tri-state and evidenceConflict invariants.
```

- Verification contribution: R2 typed availability and G1 closed proof.
- After this file: B C schema ready.

#### File 3 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java`

- Purpose: Declare a dedicated existing-scope protected resource label.
- Symbols: RECOVERY_DISPOSITION_API and api.agent.recoveryDisposition.
- Repository evidence: Current Agent READ and existing Agent permissions.
- Dependencies and consumers: Files 1 and 4, AS permission producer.
- Why now: Exact metadata needed for typed A and B; existing rights remain.
- Contract/signature changes: One static resource key and one English label.
- Input/output and state mapping: One static resource key and one English label.
- Error and edge behavior: No change to permission definitions, grants or previous resource meaning.
- Standards impact: MC-SCOPE-001 and G1 permission reflection and P1 exact delta.
- Literal rule enforcement: Rule 1, Rule 2, Rule 11.
- Implementation pseudocode:

```java
public static final String RECOVERY_DISPOSITION_API = "tianji.api.agent.recovery.disposition";
// One @PermissionMessage under tianji.permissions with key api.agent.recoveryDisposition.
// Default en-US label "Read owned Agent recovery evidence"; use existing READ_SCOPE.
```

- Verification contribution: G1 permission reflection and P1 exact delta.
- After this file: New resource metadata can be used in controller.

#### File 4 — `CREATE stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionController.java`

- Purpose: Connect exact owner GET to existing AR query.
- Symbols: AgentRecoveryDispositionController#get and publicEvidence.
- Repository evidence: Current AgentContextSnapshotController, TianjiAgentAccess and AgentLedgerService.inspectRecovery.
- Dependencies and consumers: Files 1–3, current AS storage and API.
- Why now: A is compile-safe controlled 503, B consumes the real owner evidence.
- Contract/signature changes: Conditional servlet and three flags. Dedicated resource before strict runId and empty parameters. Exact one existing AR call..
- Input/output and state mapping: Conditional servlet and three flags. Dedicated resource before strict runId and empty parameters. Exact one existing AR call..
- Error and edge behavior: Preserve safe AR 404/503 and auth; malformed or wrong-run projection becomes fixed 503.
- Standards impact: MC-SCOPE-001 and R2 on A, G1 valid/invalid HTTP on B.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11.
- Implementation pseudocode:

```java
@GetMapping
ResponseEntity<AgentRecoveryDispositionDTO> get(String runId, MultiValueMap<String,String> parameters) {
    var actor = access.require(TianjiAgentPermissions.RECOVERY_DISPOSITION_API).actor();
    var query = new GetAgentRunQuery(runId);
    if (!parameters.isEmpty() || !runId.equals(query.runId())) throw new IllegalArgumentException();
    var evidence = ledger.inspectRecovery(actor, query); // B only, A responds 503.
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicEvidence(query.runId(), evidence));
}
// publicEvidence validates run match and maps only closed enum/effect/Tool facts.
```

- Verification contribution: R2 on A, G1 valid/invalid HTTP on B.
- After this file: Replace A with final B after R2.

#### File 5 — `MODIFY stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`

- Purpose: Make separate source flag explicitly disabled.
- Symbols: stellar.tianji.agent.recovery-diagnostics.enabled.
- Repository evidence: Existing AS application.yml and current conditional conventions.
- Dependencies and consumers: File 4 factory condition.
- Why now: Expose exact source default before package.
- Contract/signature changes: One false property, existing behavior retained.
- Input/output and state mapping: One false property, existing behavior retained.
- Error and edge behavior: No environment, profile or database enablement.
- Standards impact: MC-SCOPE-001 and P1 actual packaged false and G1 AND test.
- Literal rule enforcement: Rule 7, Rule 11.
- Implementation pseudocode:

```yaml
stellar:
  tianji:
    agent:
      recovery-diagnostics:
        enabled: false
        # Existing storage and API settings keep their own gates.
        # The controller also requires servlet, storage.enabled and api.enabled to be true.
```

- Verification contribution: P1 actual packaged false and G1 AND test.
- After this file: Config complete.

#### File 6 — `CREATE stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json`

- Purpose: Publish exact contract-first static OpenAPI.
- Symbols: getOwnedAgentRecoveryDisposition, Disposition, ToolEvidence and Error.
- Repository evidence: Current static AS correlation OpenAPI and existing package producer.
- Dependencies and consumers: Files 2–5.
- Why now: Static contract after exact mappings are known.
- Contract/signature changes: One exact GET, existing read scope, current error and closed nullable evidence.
- Input/output and state mapping: One exact GET, existing read scope, current error and closed nullable evidence.
- Error and edge behavior: No runtime docs and no old OAS change.
- Standards impact: MC-SCOPE-001 and P1 archive byte match and closed schema.
- Literal rule enforcement: Rule 6, Rule 11.
- Implementation pseudocode:

```json
{
  "paths": {"/api/tianji/v1/agent-runs/{runId}/recovery-disposition": {
    "get": {"operationId": "getOwnedAgentRecoveryDisposition", "security": [{"oauth2": ["tianji:agent:read"]}]}
  }},
  "components": {"schemas": {"Disposition": {"additionalProperties": false}, "ToolEvidence": {"additionalProperties": false}}}
}
```

- Verification contribution: P1 archive byte match and closed schema.
- After this file: Source ready for exact G1 and P1.

#### File 7 — `CREATE docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md`

- Purpose: Archive exact reviewed Spec.
- Symbols: Exact governing source metadata.
- Repository evidence: Existing strict documentation convention.
- Dependencies and consumers: Owner reviewed bytes and SHA.
- Why now: Retain exact source decision in the same commit.
- Contract/signature changes: Byte-for-byte copy from approved mirror.
- Input/output and state mapping: Byte-for-byte copy from approved mirror.
- Error and edge behavior: Occupied path or wrong digest blocks staging.
- Standards impact: MC-SCOPE-001 and S1 strict docs/hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md
printf '%s  %s\n' "$RECOVERY_APPROVED_SPEC_SHA" "$RECOVERY_APPROVED_SPEC" | shasum -a 256 -c -
cp -n "$RECOVERY_APPROVED_SPEC" docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md
cmp "$RECOVERY_APPROVED_SPEC" docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md
```

- Verification contribution: S1 strict docs/hash.
- After this file: Exact reviewed bytes present.

#### File 8 — `CREATE docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md`

- Purpose: Archive exact reviewed Plan.
- Symbols: Exact governing source metadata.
- Repository evidence: Existing strict documentation convention.
- Dependencies and consumers: Owner reviewed bytes and SHA.
- Why now: Retain exact source decision in the same commit.
- Contract/signature changes: Byte-for-byte copy from approved mirror.
- Input/output and state mapping: Byte-for-byte copy from approved mirror.
- Error and edge behavior: Occupied path or wrong digest blocks staging.
- Standards impact: MC-SCOPE-001 and S1 strict docs/hash.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```sh
test ! -e docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md
printf '%s  %s\n' "$RECOVERY_APPROVED_PLAN_SHA" "$RECOVERY_APPROVED_PLAN" | shasum -a 256 -c -
cp -n "$RECOVERY_APPROVED_PLAN" docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md
cmp "$RECOVERY_APPROVED_PLAN" docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md
```

- Verification contribution: S1 strict docs/hash.
- After this file: Exact reviewed bytes present.

#### File 9 — `CREATE docs/egon/evidence/2026-11-05-agent-owner-recovery-disposition.md`

- Purpose: Capture actual owning evidence and limitations.
- Symbols: R1/R2/G1/P1/S1, root AS artifact and exact scope.
- Repository evidence: Existing Source74 evidence and actual offline package inputs.
- Dependencies and consumers: Current owning reports after gates.
- Why now: Record observations only after execution.
- Contract/signature changes: Finite proof, archive/source/PaC digests and immutable selected paths.
- Input/output and state mapping: Finite proof, archive/source/PaC digests and immutable selected paths.
- Error and edge behavior: Missing/stale artifact or skipped tests block completion.
- Standards impact: MC-SCOPE-001 and S1 exact evidence and stage.
- Literal rule enforcement: Rule 11.
- Implementation pseudocode:

```text
Record actual R1 ClassNotFound assertion, R2 expected 200/actual 503 and G1 owning test counts.
Record actual clean AS package SHA, exact OpenAPI, default and one protected resource/label.
Confirm no old SQL/POM changes and archive no runtime/IAM/S11 acceptance.
```

- Verification contribution: S1 exact evidence and stage.
- After this file: Stage and commit exact nine paths.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: R1/R2/G1/P1/S1 in §8.
- Expected result: Two exact intentional failures, 41 finite green and clean package regression, static exact PaC/OAS/default and scoped diff.
- Failure returns to: File 1 for focused tests, Files 2–4 for contract/mapping/authority, Files 5–6 for package config or proof.
- Completion criteria: Current owner/read-only contract and dedicated package resource verified, no runtime claim, exact source scope.
- Rollback: Revert only one focused commit.
- Commit paths: `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionControllerTest.java`, `stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryDispositionDTO.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java`, `stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionController.java`, `stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`, `stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json`, `docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md`, `docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md`, `docs/egon/evidence/2026-11-05-agent-owner-recovery-disposition.md`.
- Commit: feat(agent): expose owned recovery disposition.

## 8. Test, Validation, and Quality Gates

| Gate | Exact source phase | Expected observation |
| --- | --- | --- |
| R1 | File 1 red only | AgentRecoveryDispositionControllerTest#recovery_disposition_route_exists, ClassNotFound expected assertion, one failure and no errors/skips |
| R2 | C B and permission definition plus File 4 A and File 1 B test | exposes_only_same_owner_closed_recovery_evidence, one assertion 200 vs 503, no errors/skips |
| G1 | All B source | New AS 5, AgentRunHttpTest 12, TianjiAgentAccessTest 4, AgentModelUsageDiagnosticsTest 7, existing AR 5 and C 8; 41 pass |
| P1 | Clean offline package | Same finite selectors and exact AS static PaC/OAS/default artifact |
| S1 | Strict docs and exact stage | Nine paths, existing SQL/POM unchanged, one commit |

Run phases separately and capture fresh owning XML and exit codes. No I/E or provider tests.

```sh
recovery_root=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-03/recovery-disposition-api
recovery_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
recovery_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
recovery_base=1ae891f2aca338cceb80fc0d8517c100a53ae55b
test "$(git rev-parse --verify HEAD)" = "$recovery_base"
recovery_core=AgentRecoveryDispositionControllerTest,AgentRunHttpTest,TianjiAgentAccessTest,AgentModelUsageDiagnosticsTest,AgentRecoveryDispositionEvidenceTest,AgentContractTest
recovery_finite() {
  JAVA_HOME="$recovery_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$recovery_settings" -s "$recovery_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
recovery_finite 'AgentRecoveryDispositionControllerTest#recovery_disposition_route_exists'
recovery_finite 'AgentRecoveryDispositionControllerTest#exposes_only_same_owner_closed_recovery_evidence'
recovery_finite "$recovery_core"
JAVA_HOME="$recovery_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$recovery_settings" -s "$recovery_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$recovery_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-03-agent-owner-recovery-20261105 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$recovery_core"
recovery_as=stellar-enclosure-tianji-agent-server
printf '%s  %s\n' 'edbb1cf34d1d2abea6e9475b6c13b549e034ba62b902c0df28255e854dcc16af' "$recovery_root/packaged-proof.py" | shasum -a 256 -c -
python3.14 "$recovery_root/packaged-proof.py" "$recovery_as/target/stellar-enclosure-tianji-agent-server-exec.jar" \
  /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/process-agent-outcomes/root/agent-server-manifest.json \
  "$recovery_as/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json" "$recovery_base"
```

The prior actual AS PaC raw SHA is e8f2533ce14432b249190a760686b80aa6f8efe4292617a3682fb99105902e76. P1 checks exact artifact evidence, unique actual root OAS/PaC, existing Agent correlation OAS, exact protected GET/HEAD resource and label, unchanged old permissions plus current build metadata and exact packaged false property. No process is launched. Preserve exact proof from new archive and do not commit scratch tool.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py --strict \
  docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py --strict \
  docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md
git diff --check
git diff --name-only HEAD -- '*.sql' '**/pom.xml' pom.xml
git add -- \
  stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionControllerTest.java \
  stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent/AgentRecoveryDispositionDTO.java \
  stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/security/TianjiAgentPermissions.java \
  stellar-enclosure-tianji-agent-server/src/main/java/top/egon/cola/platform/tianji/agent/server/api/AgentRecoveryDispositionController.java \
  stellar-enclosure-tianji-agent-server/src/main/resources/application.yml \
  stellar-enclosure-tianji-agent-server/src/main/resources/META-INF/tianji/openapi/agent-recovery-disposition-v1.json \
  docs/egon/spec/2026-11-05-23-14-agent-owner-recovery-disposition.md \
  docs/egon/plan/2026-11-05-23-15-agent-owner-recovery-disposition.md \
  docs/egon/evidence/2026-11-05-agent-owner-recovery-disposition.md
git diff --cached --check
git diff --cached --name-status
```

Immutable scope output must be empty. Exact path/operation diff must match §5, then one commit and clean check.

## 9. Migration, Compatibility, Rollout, and Rollback

No migration or actual role/config enablement. Old APIs and ledger behavior unchanged. The separate feature is default false. Revert one source commit.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Spec | Step | Files | Gates |
| --- | --- | --- | --- | --- |
| REQ-001 | §4/9 | Step 1 | 1/3/4/6 | R1/G1/P1 |
| REQ-002 | §4/7/10 | Step 1 | 1/2/4 | R2/G1 |
| REQ-003 | §4/7/9 | Step 1 | 1/2/4/6 | G1/P1 |
| REQ-004 | §4/14/16 | Step 1 | 5–9 | G1/P1/S1 |
| REQ-005 | Effective AR §4 | Step 1 | 1/2/4 | G1/S1 |
| REQ-006 | Effective AR §4 | Step 1 | 4–9 | P1/S1 |

## 11. Risks, Blockers, and User Decisions

Current scope evidence cannot prove recoverable state or external effects. No assertion of ready to resume or present deployment permissions. Web consumer and runtime/DB/IAM verification separate.

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

A real current owner source query for local recovery disposition. No cross-owner auth or state mutation.

### 12.2 Spec consistency

Exact current actor, bounded tri-state, closed external shape and default off.

### 12.3 Repository executability

Exact baseline, owning wrapper, finite selectors and static package, without application startup.

### 12.4 Test and release completeness

R1/R2 actual assertion RED before final B. Complete finite, static artifact and exact source evidence.

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
| MC-BEAN-001 | Applicable | PASS | §7/8/14 | Current technical MVC constructor and existing guard | None |
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
