# T4-01A1 Offline Release Artifact Validator Implementation Plan

| Field | Value |
| --- | --- |
| Document | `2026-11-05-16-33-platform-release-artifact-candidate.md` |
| Template Version | `5` |
| Status | `Review` |
| Created | `2026-11-05 16:33 Asia/Shanghai` |
| Updated | `2026-11-05 16:46 Asia/Shanghai` |
| Owner | `Tianji release governance decision owner` |
| Repository | `tianji-service` |
| Scope | `One PS release package, five source paths, three review/evidence documents, two local commits` |
| Source Requirement | `Master §19 T4-01 A1, the independent offline artifact check only` |
| Baseline Revision | `tianji-service 92bac51aa8d131c9143dcc23e11c28e49fca4731, tianji-web f558e02c8d07da2da758c5273a5eb531ca251018` |
| Implements Spec | [A1 Offline Artifact Validator](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-05 16:39 Asia/Shanghai, SHA-256 6e49970139f714048ae95108317fffad445538aa9351dbfb19f39d106fb930ab` |
| Effective Specs | [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md) |
| Depends On Plans | [T3-12 source Steps 1–5](2026-11-05-15-30-expense-four-path-acceptance.md#7-ordered-file-by-file-implementation-steps) |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

Expose the existing Process manifest algorithm as one package-private pure method and prove the publication digest is identical. Then add the bounded offline verifier and direct packaged entrypoint. The second Step uses the verified first Step, leaving existing server and SDK entries unchanged. Completion requires actual finite tests, a deterministic local CLI run from the exact PS exec archive, and both AS/PS permission-producer archives. No application, database, IAM or T3-12 E execution belongs to either Step.

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [A1 Offline Artifact Validator](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md), proposed repository path `docs/egon/spec/2026-11-05-16-17-platform-release-artifact-candidate.md`.
- Status: Review, exact offline source scope reviewed by Root under the user master goal.
- Revision: 2026-11-05 16:39 Asia/Shanghai, SHA-256 `6e49970139f714048ae95108317fffad445538aa9351dbfb19f39d106fb930ab`.
- Approval evidence: Root approved only this exact offline source slice under the user instruction to continue the master Plan. The prior T3-12 source lock and actual baseline artifacts were verified. This is not user acceptance, signing authority, live E, activation or permission to deploy.

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [A1](../spec/2026-11-05-16-17-platform-release-artifact-candidate.md) | Review, exact SHA above | §3–§16 and §19–20 | Only source requirement for new behavior |
| Source prerequisite only | [T3-12 source](../spec/2026-11-05-15-13-expense-four-path-acceptance.md#16-compatibility-migration-rollout-and-rollback) | Source committed and separately corrected | Model/fixture ownership and runtime separation | Reuse existing assets. T3-12 functional requirements remain outside the A1 Effective Spec set |

### 2.3 Superseded or excluded content

No Spec is superseded. The broad T4 candidate is context for the split. Signed release, fresh owner lookup, tool revocation, reviewer authority, CAS/rollback, governed starts and migration remain separate. T3-12 E is a prerequisite for live T4 governance and stays outside approved A1 offline source work.

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| REQ-001 | §4, §9 CLI-001 | One explicit existing PS exec archive CLI | Exit 0 one canonical line, nonzero fixed stderr, original Boot entry unchanged | Step 2 CLI/tests/package |
| REQ-002 | §4, §7.3/§9/§15 | Exact scope, bounded closed inputs, hashes, Agent shape/evidence | Malformed/missing/unsafe/forged inputs rejected before output | Step 2 validator |
| REQ-003 | §4, §7.3/§8/§10 | Reuse old manifest digest and pure model validation including declared Agent link | Old digest golden/actual publish path stable, typed relation drift rejected | Step 1 helper and Step 2 calls |
| REQ-004 | §4, §7.3/§9/§15 | Verify local AS/PS exec artifacts and PaC ZIP-root entries | Hash, Boot shape, producer identity and root-entry proofs | Step 2 tests and package |
| REQ-005 | §4, §9/§10 | Deterministic canonical payload and domain digest | Same semantic input, relocation and order yield same bytes, changed facts fail pinned check | Step 2 canonical output |
| REQ-006 | §4, §5/§16/§18 | Keep artifact and authority boundaries | ARTIFACT_CHECKED_ONLY, owner facts declared/pending, no app/DB/network | Both Steps |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

Step 1 proves byte-for-byte compatibility of the existing Process release hash using current publication tests and fixed assets. Step 2 adds one direct CLI and one package-local validator after its typed assertion RED. It reads actual bounded artifacts while reusing Jackson and the existing native model validators. No producer, public API, POM, Spring wiring or SQL changes.

### 4.2 Test-first strategy

| Gate | Exact proof | Expected result |
| --- | --- | --- |
| D1 | `ProcessReleaseServiceTest#manifest_digest_has_package_scope` | One assertion observes the private baseline helper |
| D2 | `ProcessReleaseServiceTest,ExpenseFixtureModelsTest` | Existing and independently pinned goldens pass |
| C1 | `PlatformReleaseArtifactCandidateTest#offline_cli_entrypoint_is_present` | Reflection assertion fails for missing class |
| C2 | `PlatformReleaseArtifactCandidateTest#valid_candidate_is_canonical_and_location_independent` | Typed test compiles and fails expected exit 0 against stub |
| C3 | `PlatformReleaseArtifactCandidateTest,ProcessReleaseServiceTest,ExpenseFixtureModelsTest,AgentJsonCodecTest` | All named finite reports run and have zero failures/errors/skips |
| C4 | Existing permission-producer verify plus actual archive CLI invocation | Exact main/root manifests, positive and negative exit and independently recomputed candidate SHA |

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| 1 | Approved A1 and fresh baseline lock | Read-only design/test review | Old release helper write scope | Preserve producer digest first |
| 2 | Step 1 commit | Read-only archive evidence preparation | Any unapproved product/runtime change | Exact helper is used by verifier |

### 4.4 Commit boundaries

One semantic commit per Step, exact paths only in `tianji-service`. Publish approved Spec/Plan alongside Step 1, actual finite/package evidence in Step 2. No Web commit. No archive, disposable fixture directory, private output or untracked report is committed.

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| Shared Process digest | Required §7.3/§8 | Private `ProcessReleaseService#manifestHash`, current reflective golden | Same frame and typed package access | One adapter at existing publisher | Implement |
| CLI entrypoint | Required §9 | Existing PS exec JAR contains `PropertiesLauncher.class` | Direct current launcher, no new packaging | One local process | Implement |
| Artifact validator | Required §7.3/§9/§10 | C DTOs, native model validator, JarFile and Jackson3 | Direct reuse, no AR or AS dependency | Bounded local reads | Implement |
| External Agent/Prompt/Tool approval | Excluded §3/§18 | AR private digest, Tianshu scoped run and current Tool grant | Local content only | No remote credential | Keep separate |

The CLI validates declared local inputs directly. It creates no network retrieval, speculative cache, duplicate DTO mapping layer or runtime state.

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| Same legacy digest exposed internally | REQ-003 | D1 | Current PS records | Pure method and old goldens | A1 verifier | Step 1 |
| Standalone artifact validation | REQ-001–006 | C1/C2 | Step 1 method and existing libraries | CLI and tests | Operator | Step 2 |
| Packaged verification | REQ-001/004/005/006 | C3/C4 | Existing profile and both rebuilt exec JARs | Raw PaC/archive and command evidence | Scope closure | Step 2 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | Existing PS technical release vertical | A1 §6–8 | Same owner, no new layer, module, Bean or datasource | Steps 1–2, MC-ARCH-001 |
| Reuse/capability | Existing model validator, C DTO and Boot launcher | A1 §7–9 | Reuse package helper and bounded technical CLI | Steps 1–2, MC-REUSE-001/MC-DEP-001 |
| Naming/model/validation/conversion | C `AgentDefinitionSpecDTO`, PS `ModelResource` | A1 §10 | Semantic CLI/Validator, private immutable values, exact C checks | Step 2, MC-NAME-001/MC-VALID-001/MC-MODEL-001 |
| Bean/logging/util/JSON/time/config | Existing Jackson3 and JDK | A1 §6, §9/§15 | No Bean/logging/profile/time changes, strict JSON | Step 2, MC-UTIL-001/MC-JSON-001 |
| Business variation/pattern | Single bounded deterministic validation | A1 §13 | Direct validation with existing model behavior | Step 2, MC-PATTERN-001 N/A |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| Native model validation | PS | `ProcessModelValidator#validateBpmn,validateDmn,validateCmmn` and exact binding checks | Offline fit | Reuse | None | Step 2, MC-REUSE-001 |
| Framed Process SHA | PS | `ProcessReleaseService#manifestHash,writeField` | Private input | Shared pure method | Scope/signature only | Step 1, MC-REUSE-001 |
| Strict JSON/typed values | Jackson3/C | `ProcessRequestBodyAdvice` and neutral DTOs | Existing fit | Reuse | None | Step 2, MC-VALID-001 |
| Packaged entry | Boot exec/JDK | PropertiesLauncher class and JarFile | Current transport fits | Reuse exact class/profile | No POM | Step 2, MC-DEP-001 |
| Live facts | IAM/Tianshu/Yuheng | Existing owner-specific authority | Unsupported locally | Keep pending | None | Step 2, MC-SCOPE-001 |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2/§8 | Current PS types | Test → existing helper, then CandidateTest → CLI → Validator | Named typed values with private scope | D2/C3 | 1–2 | PASS |
| Rule 2 | §7.3/§9 | ModelResource and existing C constructors | CandidateTest → CLI → Validator | Validate scope, file, typed Agent, model and link | C2/C3 | 2 | PASS |
| Rule 3 | §10 | Immutable C and PS records | Helper then validator | Reuse values and explicit read-only projection | D2/C3 | 1–2 | PASS |
| Rule 4 | §6.2 | No business Bean or log | CLI/Validator | Stable stderr only | C3/C4 | 2 | N/A |
| Rule 5 | §6/§15 | JDK and Jackson present | Both Steps | JDK SHA/IO/ZIP and Jackson | D2/C3 | 1–2 | PASS |
| Rule 6 | §9/§10 | Jackson3 and C enums | CandidateTest → Validator | Canonical UTF-8 and exact keys | C3/C4 | 2 | PASS |
| Rule 7 | §6/§16 | No config source change | Both Steps | Existing permission profile for tests only | C4 | 1–2 | N/A |
| Rule 9 | §13 | Single deterministic local validator | Both Steps | No new strategy or coordination | C3 | 1–2 | N/A |
| Rule 10 | §6.3 | No time in output; file metadata only drift check | Both Steps | No time contract change | C3 | 1–2 | N/A |
| Rule 11 | §6/§8/§11 | Existing PS owner and no persistence additions | All exact paths | Keep module, API, SQL and CQE boundaries | D2/C3/C4 | 1–2 | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Not applicable

Reason: A1 candidate is deterministic and has no time fields. The input-file modification metadata comparison detects local IO drift and does not change existing Process publish time fields.

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java` | Current reflective two-type golden and Case publish fixture | Package access and old goldens | Legacy SHA | Step 1 | REQ-003 | D1/D2 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java` | Private #manifestHash, #publish caller | Pure package-private helper on ModelResource | Shared SHA | Step 1 | REQ-003 | D2 |
| CREATE | `docs/egon/spec/2026-11-05-16-17-platform-release-artifact-candidate.md` | Exact reviewed external Spec | Approved Spec and Plan relationship | Normative owner | Step 1 | REQ-001–006 | Doc strict |
| CREATE | `docs/egon/plan/2026-11-05-16-33-platform-release-artifact-candidate.md` | Exact reviewed external Plan | Approved locked Steps | Scope owner | Step 1 | REQ-001–006 | Doc strict |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCandidateTest.java` | Existing finite PS tests and expense fixtures | Exact CLI and package assertions | Proof | Step 2 | REQ-001–006 | C1–C4 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java` | Existing Boot launcher | Public main, package-private run | Offline adapter | Step 2 | REQ-001/002/005/006 | C2–C4 |
| CREATE | `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java` | Existing JDK/Jackson/C/model APIs | Closed artifact/link/digest validation | Deterministic candidate | Step 2 | REQ-002–006 | C3/C4 |
| CREATE | `docs/egon/evidence/2026-11-05-platform-release-artifact-candidate.md` | Existing service evidence conventions | Actual finite/package evidence | Closure | Step 2 | REQ-001–006 | Final audit |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

T3-12 source scope is locked at service `92bac51aa8d131c9143dcc23e11c28e49fca4731`, web `f558e02c8d07da2da758c5273a5eb531ca251018`. The initially observed Agent fixture scan regression in `TianjiPermissionIntegrationTest#servlet_filter_chain_keeps_verified_user_token_available_for_event_authorization` was fixed in the separate service baseline commit. Root's `t4-01/a1-baseline-guard-green.log` and `t4-01/baseline-artifacts/proof.json` record 47 named tests passing and both root manifest entries. Preserve the corrected AS fixture as baseline and rerun exact source scope checks before A1.

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| Service build | Existing `./scripts/verify-source-reactor.sh`, settings `/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml` | JDK 25 and locked sibling revisions | Finite tests/archives |
| Permission package | Existing `-Ppermission-producer` | Rebuilt exec archives, one ZIP-root PaC each | No IAM writes |
| CLI proof | Current PS exec contains `org/springframework/boot/loader/launch/PropertiesLauncher.class` | Updated exact class entry and private local proof assets | Explicit CLI only |

### 6.3 Immutable constraints and approved decisions

Preserve POM/production YAML, release behavior, XML fixture bytes, Flyway, routes, IAM and data. A1 uses exact root PaC entry and distinct producer IDs. Raw PaC digest is separate from Jianshen canonical policy digest. No use of JWT keys or authority claims from local bytes.

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| PLAN-CLAR-001 | Package-private `manifestHash(List<ModelResource>)` uses already validated XML and effective inputs from PreparedModel | Existing framed source and legacy test | Exact bytes and ordering preserved | A golden mismatch blocks Step 1 |
| PLAN-CLAR-002 | Synthetic finite archive/descriptor fixtures live in JUnit temp directories | T3-12 fixture and Spec §8 exact paths | No duplicate committed asset, actual package gate separate | Return to fixture review |
| PLAN-CLAR-003 | Packaged proof stages local user-owned artifacts with a disposable command block in §8; new CLI and existing profile are its checkers | Current wrapper and exact Spec contract | No new script or fixture source | Stop before commit if evidence cannot be reproduced |

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Expose the same Process release digest to package callers

- Requirements: REQ-003, REQ-006.
- Dependencies: Approved A1, exact corrected service baseline and its clean finite/PaC proof. T3-12 source lock, separate E boundary.
- Baseline state: `ProcessReleaseService#publish` calls private `manifestHash(List<PreparedModel>)` after `prepare` and `validateCaseSources`. Existing legacy golden calls it reflectively with the private list. No CLI exists.
- Observable outcome: Package consumers can calculate the exact same Process release digest from validated XML/effective schema. Previously published model sets still produce identical SHA and existing release behavior.
- End state: Two Java paths and accepted Spec/Plan, one local commit. No CLI behavior or state write added.
- Test-first gate: Required. D1 uses reflection access without making method accessible; an actual JUnit assertion must fail on the current private method. D2 updates direct typed goldens and exercises the existing publish tests.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11.
- Ordered files:

#### File 1 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`

- Purpose: Lock package accessibility and byte-for-byte legacy compatibility independently from the offline verifier.
- Symbols: Add `manifest_digest_has_package_scope`; adapt `legacy_constructor_overloads_and_two_type_manifest_bytes_remain_compatible`; add independent SHA assertion to `linked_source_publication_binds_both_models_and_the_source_node_in_one_release`.
- Repository evidence: Existing legacy test obtains `prepare` then calls `manifestHash` by private reflection. `CaseFixture` already tests publication through mocked JDBC and native repositories, including DB commit.
- Dependencies and consumers: JUnit, existing `fixture(String)`, `ProcessModelValidator`, `ProcessReleaseService.ModelResource`, baseline BPMN/DMN and `sourceBundle(CaseFixture)`.
- Why now: D1 compiles on the old method because the erased signature exists. Add only its accessibility assertion first. After D1 and File 2, replace old digest reflection with actual package typed tests.
- Contract/signature changes: Same-package test calls `ProcessReleaseService.manifestHash(List<ModelResource>)` with validated BPMN and DMN XML, effective DMN schema and current value types. It observes actual public publish for the existing linked Case fixture.
- Input/output and state mapping: Baseline BPMN `baseline` empty inputs plus DMN `baselineDecision` effective `amount:INTEGER` must return `2633baa271f32928b180858f00c49923f813011e00a00aa4fc3df162cfcc1049`. Reversed model order returns the same digest. Existing linked BPMN/CMMN publish returns `cf7819f270487cd29941ee68d1c3c067ca0335e57e6e09b32556186a0b9bfee3`. These were independently calculated from current XML bytes and the existing frame.
- Error and edge behavior: D1 checks access without `setAccessible(true)`. D2 preserves all overload, no-datasource, Case binding/commit and rejection assertions. A malformed model or changed schema is not repaired by digesting a different shape.
- Standards impact: MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001, MC-TEST-001. Existing records and fixture; no additional framework.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11. Use existing typed model facts and finite same-owner test package.
- Implementation pseudocode:

```java
@Test void manifest_digest_has_package_scope() {
    var method = ProcessReleaseService.class.getDeclaredMethod("manifestHash", List.class);
    assertFalse(Modifier.isPrivate(method.getModifiers()), "manifest digest is callable within the release package");
    assertTrue(method.canAccess(null));
    assertFalse(Modifier.isPublic(method.getModifiers()));
    assertFalse(Modifier.isProtected(method.getModifiers()));
    assertTrue(Modifier.isStatic(method.getModifiers()));
}
// After D1 and File 2:
var bpmn = validator.validateBpmn(fixture("baseline.bpmn20.xml"), "baseline");
var dmn = validator.validateDmn(fixture("baseline.dmn"), "baselineDecision");
var effective = List.of(new ModelResource(DMN, dmn.definitionKey(), dmn.xml(), dmn.inputs()),
                       new ModelResource(BPMN, bpmn.definitionKey(), bpmn.xml(), Map.of()));
assertEquals(BASELINE_SHA, ProcessReleaseService.manifestHash(effective));
assertEquals(BASELINE_SHA, ProcessReleaseService.manifestHash(effective.reversed()));
// Existing linked publish fixture:
assertEquals(LINKED_CASE_SHA, release.manifestSha256());
```

- Verification contribution: Exact D1 assertion, D2 goldens plus real production call path under existing fakes.
- After this file: Stage A compiles and is RED. Finish direct typed tests only after exposing the new signature in File 2.

#### File 2 — `MODIFY stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`

- Purpose: Provide one shared digest implementation with exact publication behavior.
- Symbols: `publish`, `static String manifestHash(List<ModelResource>)`, existing `writeField,sha256`.
- Repository evidence: Existing `prepare` performs type-specific parsing, effective DMN schema adoption and sorting. `manifestHash` frames count, model type/key/XML, sorted input fields. `publishInTransaction` consumes resulting hash and original prepared records.
- Dependencies and consumers: Existing PS `ModelResource` and private `PreparedModel`, original `publish`, package caller in Step 2.
- Why now: D1 proves the missing package seam. Change visibility, shape and canonical order in one source edit while preserving old call flow.
- Contract/signature changes: Package-private, pure `static String manifestHash(List<ModelResource>)`. Keep public service/API contract and all current native checks. No additional dependency.
- Input/output and state mapping: After current `prepare` and `validateCaseSources`, adapt the normalized records to `ModelResource(type,key,validatedXml,effectiveInputs)`. Canonically sort a private list copy using existing type name and definition key comparator. Write 32-bit big-endian counts/lengths and old UTF-8/type/key/XML/effective-input bytes. Keep original PreparedModel list for publishInTransaction.
- Error and edge behavior: No I/O, network, engine or transaction in helper. Preserve old error path, map/array defensive copies and ordering. All original publish validation remains before digest and transaction. No additional validation rule for callers already providing checked values.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-MODEL-001, MC-UTIL-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 11. Current technical record/schema shape, JDK digest, package scope, no Spring wiring.
- Implementation pseudocode:

```java
Release publish(actor, key, resources) {
    require same key/actor checks
    prepared = prepare(resources, actor.tenantId())
    validateCaseSources(actor.tenantId(), key, prepared)
    typed = prepared mapped to ModelResource from existing validatedXml and effectiveInputs
    digest = manifestHash(typed)
    return same existing transaction invoking publishInTransaction(actor, key, digest, prepared)
}
static String manifestHash(List<ModelResource> models) {
    sorted = models sorted by Type.name then definitionKey, preserving input list
    retain exact old frame and sha256/writeField implementation on sorted
    return lowercase hex
}
```

- Verification contribution: D2 old exact digests and existing finite release/expense model checks.
- After this file: Direct typed tests compile. Run D2 before copying approved governance documents.

#### File 3 — `CREATE docs/egon/spec/2026-11-05-16-17-platform-release-artifact-candidate.md`

- Purpose: Publish exact approved A1 normative contract.
- Symbols: A1 Spec §3–§20 and exact linked Plan.
- Repository evidence: Existing service EGON Spec and T3-12 scope.
- Dependencies and consumers: Reviewed off-repo Spec, actual approval and final SHA, File 4.
- Why now: D2 compatibility and exact A1 authority can be committed together.
- Contract/signature changes: Documentation only. Review status promotion and metadata-only Plan backlink may be applied only as approved.
- Input/output and state mapping: Final accepted bytes to exact repository path, links repository-relative.
- Error and edge behavior: Wrong hash/status/relationship returns to decision owner before staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Retain existing owner and data boundaries.
- Implementation pseudocode:

```text
verify approved Spec hash and exact status / links
write once to the explicit service docs/egon/spec path
after File 4 run strict Spec validation
```

- Verification contribution: Strict effective-Spec structure and content review.
- After this file: Exact Spec present, linked Plan is next.

#### File 4 — `CREATE docs/egon/plan/2026-11-05-16-33-platform-release-artifact-candidate.md`

- Purpose: Lock ordered reviewable source scope and finite gates in the service repository.
- Symbols: Exact approved Plan and two commit scopes.
- Repository evidence: Existing service EGON Plan and T3-12 dependency.
- Dependencies and consumers: File 3, exact reviewed Plan and final baseline lock.
- Why now: Commit immutable execution scope with its normative Spec.
- Contract/signature changes: Documentation only. No new implementation surface.
- Input/output and state mapping: Approved Plan to exact file, complete effective links and frozen source baseline.
- Error and edge behavior: Incomplete Step selectors/scopes, Spec disagreement or changed hash block staging.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Exact owner module/paths and independent live gates.
- Implementation pseudocode:

```text
verify final reviewed Plan bytes and post-fix baseline
write once to explicit service docs/egon/plan path
validate both strict documents and check staged scope
```

- Verification contribution: Strict Plan gate and repository scope audit.
- After this file: D2 reports and strict docs support Step 1 commit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 defines exact JDK 25 and wrapper settings. Run `a1_finite 'ProcessReleaseServiceTest#manifest_digest_has_package_scope'` after File 1 RED only, then `a1_finite ProcessReleaseServiceTest,ExpenseFixtureModelsTest` after File 2 plus finalized File 1. Run §8 strict Spec/Plan and staged-scope commands.
- Expected result: One actual D1 assertion failure with 0 errors/skips. D2 runs both named classes with no failures/errors/skips. Old SHA goldens and actual publish mock commit evidence match.
- Failure returns to: File 1 for assertion/fixture, File 2 for framing/normalization, Files 3–4 for document relationship problems. A legacy digest mismatch blocks the Step without altering the expected SHA.
- Completion criteria: Existing release behavior and old digest unchanged, reusable same-owner helper available, and accepted reviewed documents committed.
- Rollback: Revert the focused Step 1 commit before Step 2 depends on it. Once Step 2 is committed, revert in reverse dependency order.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseServiceTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/ProcessReleaseService.java`, `docs/egon/spec/2026-11-05-16-17-platform-release-artifact-candidate.md`, `docs/egon/plan/2026-11-05-16-33-platform-release-artifact-candidate.md`.
- Commit: `refactor(process): share exact release manifest digest`.

### Step 2 — Build and verify the actual offline candidate command

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006.
- Dependencies: Exact Step 1 commit and current primary Spec. Existing JDK 25, source reactor, PS/AS permission-producer paths and T3-12 finite fixture.
- Baseline state: Package-private Process helper exists and old digest is proven. Neither CLI nor artifact validation behavior exists.
- Observable outcome: The explicitly selected PS packaged CLI validates one local candidate and returns byte-stable JSON, or refuses exact malformed/fake inputs without external effects.
- End state: Three Java paths and one actual evidence document in one service commit. Existing AS/PS Boot Start-Class and PaC identities remain.
- Test-first gate: Required. C1 missing class assertion, C2 typed valid-input assertion with compiled fixed CLI stub, then minimum final behavior. Discovery/compile errors do not qualify.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCandidateTest.java`

- Purpose: Establish existence, deterministic output, exact scope and all refusal behavior through the real command seam.
- Symbols: `offline_cli_entrypoint_is_present`, `valid_candidate_is_canonical_and_location_independent`, `rejects_invalid_scope_input_and_file_binding`, `rejects_invalid_models_and_agent_link`, `rejects_changed_or_unapproved_agent_artifacts`, `rejects_inconsistent_or_ambiguous_permission_archives`.
- Repository evidence: Same-module finite `ExpenseFixtureModelsTest,ProcessReleaseServiceTest`, T3-12 exact model XML and JUnit temp fixture idiom, current PaC archive metadata.
- Dependencies and consumers: JUnit `@TempDir`, existing DTO/model fixtures, JDK stream/ZIP/digest, Jackson3 test parsing and File 2 `run`. No Spring test context, Flowable engine or database.
- Why now: Stage A declares only the reflective C1 method. Once File 2 stub compiles, add typed tests and run C2 before any real validation. Keep exact methods for final C3.
- Contract/signature changes: New finite test class only. Private fixture builder creates bounded valid local files under same-owner POSIX mode. Do not add a test resource path or bundled exec archive.
- Input/output and state mapping: Copy actual expense XML test resources to temporary files. Construct bounded CONFIG Agent definition JSON and exact two C source-evidence files in temp root. Produce deterministic small ZIPs using fixed-order entries and timestamp-free or fixed metadata, with exact Boot attributes and one ZIP-root PaC entry per role. Descriptor uses actual raw hashes and expense legacy SHA `f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5`. A golden byte assertion derives expected payload from independently enumerated exact fields, fixed stamps and fixture hashes, and pins the resulting SHA after independent calculation.
- Error and edge behavior: Assert usage 64, validation/digest 65, and I/O 66, with stdout empty and only safe stderr. Mutate one checked fact per case and update the input hash when necessary to reach the intended inner semantic guard. Test symlink path/root and scope mismatch, duplicate/unknown/trailing JSON or flags, wrong encoding/type, nonempty Tool/Prompt/Skill/budget, name/release/model/link/schema drift, incorrect/missing/ambiguous/root-alias PaC, wrong producer and Boot metadata. No endpoint or bearer.
- Standards impact: MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001. Existing test utilities, no fake publication authority.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11. Typed C fixture, exact validation, immutable value, JDK and canonical JSON, current PS module.
- Implementation pseudocode:

```java
@Test void offline_cli_entrypoint_is_present() {
    assertDoesNotThrow(() -> Class.forName("top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli"));
}
@Test void valid_candidate_is_canonical_and_location_independent() {
    var one = stageApprovedLocalFixture(privateTempRoot, expenseModelResources, deterministicSmallZipEntries);
    var output = new ByteArrayOutputStream();
    assertEquals(0, PlatformReleaseArtifactCli.run(one.argv(), output, safeErrors));
    // C2 fails here on stub; after B compare exactly to independent golden bytes.
    assertCanonicalPayloadAndDomainDigest(output.toByteArray());
    assertArrayEquals(first, runSameFactsAtDifferentRootAndDifferentDescriptorOrdering());
    assertExactPinnedDigestRoundTrip(first);
}
@Test void rejects_invalid_scope_input_and_file_binding() {
    assertSafeCodeAndEmptyStdout for wrong argv/scope, oversize, symlink, traversal,
        same-path duplicates, invalid UTF8, duplicate/unknown/trailing keys, wrong schema and overflow;
}
@Test void rejects_invalid_models_and_agent_link() {
    compare correct raw hash before asserting changed model key/type, validated schema, CMMN or AgentLink failure;
}
@Test void rejects_changed_or_unapproved_agent_artifacts() {
    reject changed bytes, unknown config, nonempty dependencies or Tool budget and fake authority;
    assert valid output leaves Agent owner digest, source revision, route and policy declared;
}
@Test void rejects_inconsistent_or_ambiguous_permission_archives() {
    reject raw hash mismatch, absent/duplicate root or nested alias, wrong Boot or producer identity;
    assert no output contains files, prompts, secrets, raw contents or time;
}
```

- Verification contribution: C1/C2 actual assertion RED, C3 local contract regression. Synthetic archives are independently distinguished from actual C4 artifacts.
- After this file: Stage A fails missing class. Typed tests wait for File 2 stub and precede final behavior.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`

- Purpose: Provide one explicit offline launcher and controlled argv/exit/output boundary.
- Symbols: `public static void main(String[] args)`, package-private `static int run(String[] args, OutputStream stdout, PrintStream stderr)`.
- Repository evidence: Actual PS exec archive contains Boot `PropertiesLauncher.class`, existing Start-Class is server application, test-only T3-12 mains demonstrate explicit launcher selection.
- Dependencies and consumers: JDK only at adapter boundary, File 3 validator, unit test and offline operator.
- Why now: First declare exactly the safe compile-only methods for C2. After C2 run, complete argument/error handling, then add File 3 before compiling again.
- Contract/signature changes: Exact `verify --root --input --tenant --channel [--expect-sha256]` with no alias or automatic startup. Verify exit and stderr per Spec §9. No Spring component or ordinary application entry changes.
- Input/output and state mapping: Parse one value for each required flag and optional digest, exact command and safe types. Resolve only via File 3. On success write complete pre-built UTF-8 envelope and LF, flush, and return 0. For `System.out` PrintStream check post-write error. Main uses process exit only when status is nonzero.
- Error and edge behavior: Duplicate/unknown/missing/invalid flags return fixed 64. Validator invalid and digest mismatch return fixed 65 codes; missing/unreadable file and output error return 66. No raw exception, usage text leaking args or partial output before validation. Zip structure failures map to INVALID. Device failure may interrupt final write.
- Standards impact: MC-ARCH-001, MC-VALID-001, MC-UTIL-001, MC-JSON-001, MC-SCOPE-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 5, Rule 6, Rule 11. Direct JDK CLI, validated scope, fixed safe codes, no Bean.
- Implementation pseudocode:

```java
// A: both signatures exist, return fixed safe unavailable via stderr; no File 3 import.
public static void main(String[] args) {
    int result = run(args, System.out, System.err);
    if (result != 0) System.exit(result);
}
static int run(String[] args, OutputStream stdout, PrintStream stderr) {
    parse exact verb and closed flags, no defaults
    byte[] complete = validator.verify(root, relativeInput, tenantId, channel, expectedSha)
    write and flush complete bytes; check PrintStream error if applicable
    return 0
    // Closed checked/typed boundaries map to Spec stable error line/exit
}
```

- Verification contribution: C2 stable behavior RED, C3 status/argv/stdout tests, C4 actual packaged main.
- After this file: Compile-only A produces the expected valid-input assertion failure. Finish B after File 3 is in place.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java`

- Purpose: Validate bounded local files, typed model relation, actual archive identity and deterministic output.
- Symbols: Package-local final `PlatformReleaseArtifactValidator`; `byte[] verify(Path root, String relativeInput, String tenantId, String channel, String expectedSha)`; private immutable scoped artifact/file/expected values, one closed validation failure family, deterministic SHA helpers.
- Repository evidence: A1 §7.3/§9/§10/§15, PS `ProcessModelValidator`, `ValidatedProcessModel.MessageWait/WorkerTask`, Step 1 `ProcessReleaseService#manifestHash`, current C `AgentDefinitionSpecDTO`, Boot actual ZIP root PaC, current Jackson3 strict adapter idiom.
- Dependencies and consumers: Existing JDK file/ZIP/digest/UTF-8, Jackson 3 tree and typed DTO binding, PS model validators and manifest helper. File 2 is sole production consumer.
- Why now: C2 proves missing verifier after A. Implement one direct finite sequence with no Spring, dependency, context or network addition.
- Contract/signature changes: Local only. Produce exact Spec payload, fixed pending states, canonical envelope and LF, and safe fixed category errors. Reject all unverified owner/artifact promotion fields.
- Input/output and state mapping: Exact positive tenant string and channel from CLI compared to descriptor. Bind file references to actual same-owner local regular files and bytes. Agent typed scope, raw definition/contracts, models with effective inputs and actual link, both raw archive/manifest SHA and exact identities populate the closed result. `definitionContentSha256`, route/policy and source revision remain declared facts.
- Error and edge behavior: Enforce Spec byte/path, archive-entry, strict UTF-8/tree limits during reads. Require no duplicate relative path or ZIP target alias and reject structural proof anomalies. Compare file identity/size/mtime before and after reads. Separate usage/invalid/digest/I/O. No parser callbacks or datasource, no raw output. Do not reimplement AR owner digest or use fake owner.
- Standards impact: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 11. Existing technical owner, immutable values and typed DTO, finite JDK and exact Jackson wire.
- Implementation pseudocode:

```java
byte[] verify(root, relativeInput, tenantId, channel, expectedSha) {
    verify owner-private real POSIX root and safe no-symlink relative resolution
    descriptor = boundedStrictUtf8Json(64KiB, exact Spec fields)
    assert descriptor.tenantId/channel equal independently checked argv
    for each FileRef once:
        verify normalized owned path, drift metadata and configured byte cap
        stream raw SHA, compare exact claimed hash and reject duplicate normalized path
    definition = strict typed AgentDefinitionSpecDTO from bounded actual bytes
    assert closed CONFIG shape, exactly one root agent, null codeRef, zero Prompt/Skill/Tool and zero tool budget
    check exact definition key/version and two typed source basenames/package/record identifiers
    retain owner Agent content digest, route and policy as declared
    verify model list 1..16 with BPMN present, unique type/key, bounded XML and schema
    for each model apply existing validateBpmn/Dmn/Cmmn and map effective input schema
    for each BPMN apply existing decision, human and Worker binding validation
        resolve exact same-release CMMN key for each Case source
    require one MessageWait across BPMN candidates, exact agentLink model/worker/topic/schema/effect and Agent identity
    legacySha = ProcessReleaseService.manifestHash(validated ModelResource list)
    compare descriptor process.manifestSha256
    for each AS/PS archive:
        bound regular bytes and hash, ZipFile/JarFile metadata only
        scan at most 65536 entries; exactly one bounded Boot and ZIP-root PaC entry
        reject any Boot classes alias of PaC and duplicate expected entries
        parse bounded Java Manifest bytes for Start-Class and Boot layout
        parse bounded PaC JSON and match schemaVersion/application/producer/module
        compare raw entry SHA, recheck file drift
    candidate = exact Spec payload with ARTIFACT_CHECKED_ONLY and declared/pending states
    canonical = recursively sort object keys by unsigned UTF-8, sort models by type/key,
        preserve fixed arrays, check all output strings and canonical integer spellings
    sha = SHA256(UTF8("TIANJI_PLATFORM_RELEASE_ARTIFACT_V1") || 0x00 || canonical(candidate))
    if expectedSha present and unequal, throw fixed digest mismatch
    return canonical(envelope(candidate, sha)) + single LF
}
```

- Verification contribution: Makes C3 deterministic/closed behavior GREEN and supports real C4 archive verification.
- After this file: Compile and run C3 and C4, then record actual evidence.

#### File 4 — `CREATE docs/egon/evidence/2026-11-05-platform-release-artifact-candidate.md`

- Purpose: Preserve actual RED/GREEN and verified offline package/CLI evidence.
- Symbols: D1/D2/C1–C4, two raw archives, two root entry hashes, exact local result and source locks.
- Repository evidence: Existing service acceptance evidence conventions and PaC profile output.
- Dependencies and consumers: Current owning JUnit reports, actual rebuilt archive SHA/Start-Class/root manifest and CLI result/error evidence.
- Why now: Evidence records observed results and scope only after tests and package gate.
- Contract/signature changes: Documentation only. Capture reproducible commands/results and exact model/fixture identity without committing inputs or archives.
- Input/output and state mapping: Source/approval locks, test selectors/counts, existing Process golden SHA, verified archive hash/producer, direct CLI output digest/parity, exit64/65/66 unit evidence, exact C4 exit0/65.
- Error and edge behavior: Missing/skipped test, stale archive, unknown path, changed evidence or unexecuted package gate blocks completion. No fabricated counts or live authority claim.
- Standards impact: MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal rule enforcement: Rule 11. Test and release evidence preserve existing owner and no-runtime boundary.
- Implementation pseudocode:

```text
read fresh actual report XML and exact JAR metadata/digests
record current local outputs and boundary evidence with exact source commits
verify no migration, SDK or unrelated paths staged
```

- Verification contribution: User-reviewable finite and packaged closure.
- After this file: Step 2 is ready for exact path-limited commit after final audit.

- Validation working directory: `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`.
- Verification command: §8 defines exact commands. C1 `a1_finite 'PlatformReleaseArtifactCandidateTest#offline_cli_entrypoint_is_present'`, C2 `a1_finite 'PlatformReleaseArtifactCandidateTest#valid_candidate_is_canonical_and_location_independent'`, C3 full finite selector, C4 fresh profile verify and direct archive command.
- Expected result: C1 one missing-class assertion and C2 one expected-exit assertion, 0 errors/skips. C3/C4 fresh reports actual 0 failures/errors/skips and positive/negative packaged output. Original Start-Class and two exact root entries remain.
- Failure returns to: File 1 for fixture/discovery, File 2 for flags/status, File 3 for real validation and serialization, §8 archive gate for artifact paths. New baseline failures require separate correction before claiming A1.
- Completion criteria: Real executable candidate, finite tests, actual package gates and exact evidence. ARTIFACT_CHECKED_ONLY remains independent of signature, role, registry and E.
- Rollback: Revert only Step 2. Step 1 shared digest can remain or revert afterward.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCandidateTest.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactCli.java`, `stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/PlatformReleaseArtifactValidator.java`, `docs/egon/evidence/2026-11-05-platform-release-artifact-candidate.md`.
- Commit: `feat(process): verify offline release artifact candidates`.

## 8. Test, Validation, and Quality Gates

Work from `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service`, with locked repository and sibling revisions. Use fresh owning Surefire XML and actual exit status, not summary text alone. D1/C1/C2 must each run one actual test and produce one expected AssertionFailedError, 0 errors and 0 skips. D2/C3/C4 require every selected owner report to exist and actually run, with no failures/errors/skips.

```sh
a1_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
a1_finite() {
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$a1_settings" -s "$a1_settings" \
    -o -B -ntp -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}

# Run individually at their exact Step points.
a1_finite 'ProcessReleaseServiceTest#manifest_digest_has_package_scope'
a1_finite ProcessReleaseServiceTest,ExpenseFixtureModelsTest
a1_finite 'PlatformReleaseArtifactCandidateTest#offline_cli_entrypoint_is_present'
a1_finite 'PlatformReleaseArtifactCandidateTest#valid_candidate_is_canonical_and_location_independent'
a1_finite PlatformReleaseArtifactCandidateTest,ExpenseFixtureModelsTest,ProcessReleaseServiceTest,AgentJsonCodecTest

# After C3, rebuild both producers and fresh archive evidence from Step 2 candidate source.
a1_artifact_base="$(git rev-parse --verify HEAD)"
printf '%s\n' "$a1_artifact_base" | rg -x '[0-9a-f]{40}' >/dev/null || exit 1
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
./scripts/verify-source-reactor.sh -gs "$a1_settings" -s "$a1_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$a1_artifact_base" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-t4-01a1-20261105-step2 \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none \
  -Dtest=PlatformReleaseArtifactCandidateTest,ExpenseFixtureModelsTest,ProcessReleaseServiceTest,AgentJsonCodecTest,TianjiPermissionIntegrationTest,ProcessPermissionIntegrationTest
```

C4 archive proof uses actual matching build outputs `stellar-enclosure-tianji-agent-server/target/stellar-enclosure-tianji-agent-server-exec.jar` and `stellar-enclosure-tianji-process-server/target/stellar-enclosure-tianji-process-server-exec.jar`. Both ZIP-root `META-INF/permissions/manifest-v1.json` entries must be unique with no BOOT-INF alias. Recompute raw entry and archive SHA, compare packaged evidence from each existing `target/permissions/artifact-evidence.json` using the CLI's documented digest meaning. The profile's canonical `manifestDigest` is a separate value, and is never compared to raw ZIP entry SHA. Require exact Boot Start-Class and explicit A1 class path in PS only. Existing Boot application main and T3-12 children are never invoked.

The following local package proof block uses Python already present for the source-reactor wrapper. It stages private disposable inputs and invokes only the offline A1 command from the freshly verified PS exec JAR. It introduces no committed tool, fixture or build path. Retain only bounded summarized proof for the evidence document.

```sh
python3 - <<'PY'
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

java = "/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home/bin/java"
repo = Path.cwd()
ps = repo / "stellar-enclosure-tianji-process-server"
as_ = repo / "stellar-enclosure-tianji-agent-server"
assets = ps / "src/test/resources"
contracts = repo / "stellar-enclosure-tianji-contract/src/main/java/top/egon/cola/platform/tianji/contract/agent"
manifest_name = "META-INF/permissions/manifest-v1.json"
cli_class = "top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactCli"
ps_main = "top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication"
as_main = "top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication"
ps_jar = ps / "target/stellar-enclosure-tianji-process-server-exec.jar"
as_jar = as_ / "target/stellar-enclosure-tianji-agent-server-exec.jar"
source_revision = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
def sha(raw):
    return hashlib.sha256(raw).hexdigest()
def file_sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()
def canon(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

with tempfile.TemporaryDirectory(prefix="tianji-a1-package-") as temp:
    stage = Path(temp).resolve()
    stage.chmod(0o700)
    def copy(source, relative):
        path = stage / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, path)
        return {"path": relative, "sha256": file_sha(path)}
    def pack(source, target, producer, start, expect_cli):
        with zipfile.ZipFile(source) as jar:
            names = jar.namelist()
            assert names.count(manifest_name) == 1 and "BOOT-INF/classes/" + manifest_name not in names
            assert names.count("META-INF/MANIFEST.MF") == 1
            main = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "").replace("\n ", "")
            assert "Start-Class: " + start in main
            class_file = "BOOT-INF/classes/" + cli_class.replace(".", "/") + ".class"
            assert (class_file in names) is expect_cli
            raw = jar.read(manifest_name)
            assert json.loads(raw)["producer"]["id"] == producer
        evidence = json.loads((source.parent / "permissions/artifact-evidence.json").read_bytes())
        ref = copy(source, target)
        assert evidence["artifactDigest"] == ref["sha256"] and evidence["producerId"] == producer
        return {"archive": ref, "manifestEntrySha256": sha(raw)}
    model_rows = [
        ("BPMN", "expenseReview", "processes/expense-review.bpmn20.xml", {"amount":"INTEGER", "operationId":"STRING"}),
        ("DMN", "expenseRouting", "decisions/expense-routing.dmn", {"amount":"INTEGER"}),
        ("CMMN", "expenseException", "cases/expense-exception.cmmn", {}),
    ]
    models = [{"type": typ, "definitionKey": key, "xml": copy(assets / path, "models/" + Path(path).name), "inputs": inputs}
              for typ, key, path, inputs in model_rows]
    # Mirrors the committed expenseAnalysis v1 fixture shape. The AR digest is still DECLARED.
    definition = {
        "definitionKey":"expenseAnalysis", "version":1, "executionMode":"CONFIG", "codeRef":None,
        "inputSchemaVersion":"1", "outputSchemaVersion":"1",
        "dependencies":{"prompts":[], "skills":[], "tools":[], "modelRouteRef":"expense-fixture-route", "policyVersionRef":"expense-fixture-policy"},
        "policy":{"totalTimeout":"PT1M","modelIdleTimeout":"PT20S","toolTimeout":"PT10S","maxModelCalls":1,
                  "maxToolCalls":0,"maxInputTokens":16384,"maxOutputTokens":128,"maxEstimatedCost":0.20,
                  "currency":"USD","maxParallelism":1,"allowedToolVersions":[],"retryPolicyRef":None,"approvalPolicyRef":None},
        "configuration":{"chatModelBeanName":"expenseFixtureModel","modelName":"expense-fixture-model","rootAgentName":"expenseAnalyzer",
                         "agents":[{"name":"expenseAnalyzer","instruction":"Analyze the expense fixture."}]},
    }
    definition_path = stage / "agent/definition.json"
    definition_path.parent.mkdir()
    definition_bytes = canon(definition)
    definition_path.write_bytes(definition_bytes)
    owner_sha = "00b1d06cf7d973a32b03f2fdba1a25612892f0afa16ab6381b945f786d92ed7c"
    descriptor = {
        "schemaVersion":1,"tenantId":"20","channel":"expense","sourceRevision":source_revision,
        "agent":{"definitionKey":"expenseAnalysis","definitionVersion":1,"definitionContentSha256":owner_sha,
                 "definition":{"path":"agent/definition.json","sha256":sha(definition_bytes)},
                 "inputContract":copy(contracts / "AgentInputDTO.java", "agent/AgentInputDTO.java"),
                 "outputContract":copy(contracts / "AgentExecutionResult.java", "agent/AgentExecutionResult.java")},
        "process":{"releaseVersion":"expense-v1","manifestSha256":"f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5","models":models},
        "agentLink":{"processDefinitionKey":"expenseReview","workerElementId":"agentStart","topic":"agentStart","schemaVersion":"1",
                     "agentDefinitionKey":"expenseAnalysis","agentDefinitionVersion":1,"agentDefinitionContentSha256":owner_sha},
        "packages":{"agent":pack(as_jar,"dist/agent-exec.jar","tianji-agent-java",as_main,False),
                    "process":pack(ps_jar,"dist/process-exec.jar","tianji-process-java",ps_main,True)},
    }
    (stage / "candidate-input.json").write_bytes(canon(descriptor))
    command = [java, "-Dloader.main=" + cli_class, "-cp", str(ps_jar), "org.springframework.boot.loader.launch.PropertiesLauncher",
               "verify", "--root", str(stage), "--input", "candidate-input.json", "--tenant", "20", "--channel", "expense"]
    environment = {"ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS":"false"}
    first = subprocess.run(command, capture_output=True, timeout=120, env=environment)
    assert first.returncode == 0 and first.stderr == b""
    envelope = json.loads(first.stdout)
    assert set(envelope) == {"candidate", "candidateSha256"}
    candidate = envelope["candidate"]
    digest = sha(b"TIANJI_PLATFORM_RELEASE_ARTIFACT_V1\x00" + canon(candidate))
    assert digest == envelope["candidateSha256"] and first.stdout == canon(envelope) + b"\n"
    assert candidate["status"] == "ARTIFACT_CHECKED_ONLY" and candidate["agent"]["ownerDigestState"] == "DECLARED"
    assert candidate["process"]["manifestSha256"] == descriptor["process"]["manifestSha256"]
    assert candidate["pendingAuthority"] == ["RELEASE_SIGNING","OWNER_PUBLICATION","LIVE_REVOCATION","REVIEW_AND_ACTIVATION"]
    for role in ("agent", "process"):
        assert candidate["packages"][role]["archiveSha256"] == descriptor["packages"][role]["archive"]["sha256"]
        assert candidate["packages"][role]["manifestEntrySha256"] == descriptor["packages"][role]["manifestEntrySha256"]
    again = subprocess.run(command + ["--expect-sha256",digest], capture_output=True, timeout=120, env=environment)
    assert again.returncode == 0 and again.stderr == b"" and again.stdout == first.stdout
    wrong = "0" * 64 if digest != "0" * 64 else "1" * 64
    denied = subprocess.run(command + ["--expect-sha256",wrong], capture_output=True, timeout=120, env=environment)
    assert denied.returncode == 65 and denied.stdout == b""
    assert denied.stderr == b"TIANJI_RELEASE_ARTIFACT_DIGEST_MISMATCH\n"
    print("A1 packaged candidate SHA-256", digest)
    for role in ("agent", "process"):
        print(role, candidate["packages"][role]["archiveSha256"], candidate["packages"][role]["manifestEntrySha256"])
PY
```

Before each commit run `git diff --check` and `git diff --cached --check`, then compare the staged path names against that Step's exact paths. After publishing the accepted docs, execute these from the service repository.

```sh
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py \
  docs/egon/spec/2026-11-05-16-17-platform-release-artifact-candidate.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py \
  docs/egon/plan/2026-11-05-16-33-platform-release-artifact-candidate.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

## 9. Migration, Compatibility, Rollout, and Rollback

No database or migration work. Step 1 preserves exact legacy publish hash and runtime flow. Step 2 adds only an explicit local main, with normal Boot start unchanged. Revert Step 2 first if necessary and then Step 1. Preserve prior captured candidate hashes as historical local content checks, without any authority promotion.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Owning Step | Exact gate | Boundary |
| --- | --- | --- | --- |
| REQ-001 | 2 | C1/C2/C3/C4 | Packaged explicit offline main |
| REQ-002 | 2 | C2/C3 | Closed input/scoped files/Agent |
| REQ-003 | 1, 2 | D1/D2/C3 | Same Process digest and exact typed model/AgentLink |
| REQ-004 | 2 | C3/C4 | Actual raw root entries and producer identities |
| REQ-005 | 2 | C2/C3/C4 | Canonical bytes, pinned digest and repeat parity |
| REQ-006 | 1, 2 | All and final scope review | No runtime, signer, IAM or activation claim |

## 11. Risks, Blockers, and User Decisions

| Issue | Disposition |
| --- | --- |
| Primary Spec and Plan approval | Exact reviewed artifacts are ready for approval. Execution begins only after acceptance and final locked hashes |
| Prior AS fixture scan baseline | Separate T3-12 fix and verification. Plan execution uses corrected exact baseline, no AS edit in A1 |
| Potential zip layout or permission artifact drift | C4 requires actual AS/PS ZIP-root evidence and exact current producer identities; any difference returns to Spec rather than adding fallback |
| Agent owner digest/publication, live roles and revoke | Deliberately declared/pending, unaffected by source Plan. New authority requires separate design and accepted T3-12 E |

## 12. Review and Acceptance

### 12.1 Requirement fidelity

Every A1 requirement and acceptance condition maps to exact source paths, assertion gates and one local evidence document. Live T4 behavior remains separate.

### 12.2 Repository executability

The validated module/entrypoint and Maven profile exist at the exact reviewed source. The Step 1 reflection baseline is isolated, each new selector has a concrete observed failure target, and C4 invokes the actual built CLI using only local files.

### 12.3 Test, release and rollback review

Finite/package evidence is explicitly separated from actual deployment and T3-12 E. No data modification or deployment gate. One semantic commit per Step and reverse dependency rollback.

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §4.7/§5 and existing PS release owner | Same technical module and package | None |
| MC-REUSE-001 | Applicable | PASS | §4.5–4.7 and Step 1 | Existing framed digest and pure validators | None |
| MC-DEP-001 | Applicable | PASS | §4/§8 | No new dependency or POM | None |
| MC-NAME-001 | Applicable | PASS | §5/Steps 1–2 | Named exact methods and semantic classes | None |
| MC-VALID-001 | Applicable | PASS | Step 2 Files 1–3 | Exact strict parsing, scopes and model binding | None |
| MC-MODEL-001 | Applicable | PASS | §4.7 and Step 2 File 3 | Existing DTO and private immutable values | None |
| MC-CONVERT-001 | Not applicable | N/A | A1 Spec §10 and Step 2 | Direct technical output projection, no cross-layer mapper | None |
| MC-LOG-001 | Not applicable | N/A | A1 §9 and Step 2 File 2 | Fixed stderr only | None |
| MC-BEAN-001 | Not applicable | N/A | Step 2 File 2 and package gate | No new bean | None |
| MC-UTIL-001 | Applicable | PASS | §4.7 and Step 2 | JDK SHA/IO/ZIP, existing Jackson | None |
| MC-JSON-001 | Applicable | PASS | Step 2 Files 1–3 and §8 | Exact canonical bytes and digest | None |
| MC-TIME-001 | Not applicable | N/A | §4.9 | No time-dependent output | None |
| MC-CONFIG-001 | Not applicable | N/A | §5/§9 | No configuration changes | None |
| MC-PATTERN-001 | Not applicable | N/A | §4.5 and A1 §13 | Deterministic finite validator | None |
| MC-SCOPE-001 | Applicable | PASS | §5/§7 and §8 staged gate | Exact repo and paths | None |
| MC-TEST-001 | Applicable | PASS | §4.2/§7/§8 | Exact assertion and package gates planned | None |
| MC-BLOCKER-001 | Applicable | PASS | §2/§6/§11 | Complete for review only | Final explicit approval and source lock before edits |

**PASS — Ready for user review**

Plan-only candidate. No source edit, Maven execution, CLI run, application, database or network occurred in authoring this Plan.
