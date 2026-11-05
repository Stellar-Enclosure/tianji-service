# T4-02 S12a 接收 Link 验收切片实施计划

| Field | Value |
| --- | --- |
| Document | 2026-11-06-00-38-s12-receive-link-evidence.md |
| Template Version | 5 |
| Status | Review |
| Created | 2026-11-06 00:38 Asia/Shanghai |
| Updated | 2026-11-06 00:38 Asia/Shanghai |
| Owner | Tianji AS/PS |
| Repository | tianji-service |
| Scope | 10 Java test paths, one PS collector fixture, four reviewed docs |
| Source Requirement | Master §17.2 S12 and §19 T4-02 real AS/PS receive-Link evidence |
| Baseline Revision | Service f7641a373892bd9e054f79ec97bfbb9dfc8c8e45 |
| Implements Spec | [S12a](../spec/2026-11-06-00-37-s12-receive-link-evidence.md) |
| Spec Status | Review |
| Spec Revision | 2026-11-06 00:37 Asia/Shanghai, SHA-256 99c733701a331724a6ba08346c8a202531394b592c3caaf70d4431c728ff1190 |
| Effective Specs | [S12a](../spec/2026-11-06-00-37-s12-receive-link-evidence.md) |
| Depends On Plans | None |
| Supersedes | None |
| Superseded By | None |
| Related Plans | [Master T4-02](../../../../docs/plans/agent-framework-upgrade-plan.md) |

## 1. Summary

Publish a test-only positive receive Link evidence entry using existing isolated expense prerequisites. Keep product declarations and ordinary Business/Recovery paths. Source-only workflow runs finite proof and compile-only E.

## 2. Target Spec and Effective Design

Spec §§5/7/8/14/16 defines exact artifact source, private runtime and bounded collector evidence. The source and operator runs are separate.

## 3. Effective Requirements and Acceptance

| ID | Evidence |
| --- | --- |
| REQ-001 | Separate explicit test fixture and both real archive/class-tree locks |
| REQ-002 | Existing scoped durable and once-advance witnesses |
| REQ-003 | Two exact persisted sources and independent collector Links, closed envelope |
| REQ-004 | Actual finite and package proof distinct from operator-only E |

## 4. Implementation Strategy and Dependency Order

First show one typed parser RED against A. Then complete B SDK/parser, test-only configuration and child launch, scenario selector, operator fixture and documents. Gate exact source inventory before one logical commit.

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision |
| --- | --- | --- |
| Test fixture | Existing expense driver and child launchers | Extend only under explicit independent manifest |
| SDK | PS existing test dependency and AS packaged 1.62.0 | Fixed Spring test provider and source-constant pins |
| Storage | Existing scoped observer | Exact durable outbox carrier read, no new schema |
| Publication | Existing selected source reactor | Actual finite and clean package, then separately authorized E |

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision |
| --- | --- | --- | --- | --- |
| Launch provenance | Existing PropertiesLauncher, mock launchers | two actual archives and two class trees | reuse exact verified classes | Reuse |
| Telemetry | Existing AS/CL/PS Link and SDK | real collector missing | fixed test-only provider | Add |
| Outcome | Expense normal driver | scoped durable facts | existing separate owner facts | Reuse |
| Source | Existing test reactor | finite/integration selector isolation | test compile only | Reuse |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Pseudocode obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Named test fixture and immutable Observation record | G1/P1/S1 | 1 | PASS |
| Rule 2 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Exact private manifest, raw collector and endpoint guards | G1/P1/S1 | 1 | PASS |
| Rule 3 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | No production mutable business carriers | G1/P1/S1 | 1 | PASS |
| Rule 4 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Single fixed named Spring test provider; no product log | G1/P1/S1 | 1 | PASS |
| Rule 5 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Existing JDK, Jackson and OTel SDK | G1/P1/S1 | 1 | PASS |
| Rule 6 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Strict OTLP JSON parser and no app codec delta | G1/P1/S1 | 1 | PASS |
| Rule 7 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Private fixture opt-in only; no environment default changes | G1/P1/S1 | 1 | PASS |
| Rule 9 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Existing harness and direct fixed test export | G1/P1/S1 | 1 | PASS |
| Rule 10 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Instant and monotonic waits, native epoch nanos | G1/P1/S1 | 1 | PASS |
| Rule 11 | §6.2 | Existing AS/PS test and artifact path | §7 File blocks | Existing owner stores and no DDL or production CQE changes | G1/P1/S1 | 1 | PASS |


### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-001 | OTLP startTimeUnixNano/endTimeUnixNano and test lower bound | Occurred instant | Instant and decimal epoch-nanos | Not persisted | Internal | Explicit UTC epoch | Private UTC evidence | Reject stale/out-of-order spans | Nanosecond decimal OTLP wire and monotonic wait | Existing business time unchanged | Step 1 TEST-002 and TEST-004 |

## 5. Change File Tree

| Operation | Exact path | Purpose | Gate |
| --- | --- | --- | --- |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetry.java` | Pinned test provider and safe OTLP export | G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkInitializer.java` | Only the private profile may register test provider | R1/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java` | Reject stale/corrupt collector input and runtime SDK drift | R1/G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetryTest.java` | Real SDK finite export, privacy and endpoint tests | G1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java` | Finite positive and adversarial collector tests | R1/G1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java` | Verify isolated optional fixture launch with original modes unchanged | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java` | AS explicit test-only initializer seam | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java` | PS explicit test-only initializer seam | G1/P1 |
| MODIFY | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java` | Reuse durable normal-path evidence in the distinct S12 driver mode | G1/P1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkAcceptanceIT.java` | Explicit operator-only scenario | P1/E1 |
| CREATE | `stellar-enclosure-tianji-process-server/src/test/resources/acceptance/s12/collector-v0.136.0.yaml` | Pinned offline operator fixture resource | P1/E1 |
| CREATE | `docs/egon/spec/2026-11-06-00-37-s12-receive-link-evidence.md` | Reviewed design | S1 |
| CREATE | `docs/egon/plan/2026-11-06-00-38-s12-receive-link-evidence.md` | Exact source execution order | S1 |
| CREATE | `docs/egon/evidence/2026-11-06-s12-receive-link-operator.md` | Operator-only executable recipe and evidence retention | P1/E1 |
| CREATE | `docs/egon/evidence/2026-11-06-s12-receive-link-evidence.md` | Actual gate results and E boundary | S1 |

## 6. Prerequisites, Constraints, and Plan Clarifications

Require exact clean Service f7641a373892bd9e054f79ec97bfbb9dfc8c8e45, reviewed source GO, pinned approved JDK/Maven settings and existing sibling source reactor. Candidate under /Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-02/s12-receive-link. Use the Source108 baseline PaC files at t4-02/agent-queue-backlog/root-agent-server-manifest.json and root-process-server-manifest.json to verify unchanged declarations after normal build metadata exclusions. Source109 is test-only for AR. Source110 changes Worker, so actual fresh artifacts and nested SDK digests are required. Preserve 27 existing migration files. Review the pinned collector YAML statically and do not download, run or install it in source validation.

## 7. Ordered File-by-file Implementation Steps

### Step 1 — Add the isolated AS to PS receive-link evidence fixture

- Requirements: REQ-001, REQ-002, REQ-003, REQ-004.
- Dependencies: Exact locked source, current fixture prereqs, reviewed Spec/Plan and Source GO.
- Baseline state: Existing default Business/Recovery fixture and SDK/MockMvc local proof. No distinct S12a E selector.
- Observable outcome: Future operator can run one exact isolated fixture scenario. This Step proves only source and fixture packaging.
- End state: 15 exact paths and one logical commit.
- Test-first gate: Required. Typed R1 shows false vs expected true on both exact persisted receive-link chains.
- Manual Checks: MC-ARCH-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001, MC-PATTERN-001, MC-SCOPE-001, MC-TEST-001, MC-BLOCKER-001.
- Literal Rules: Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 7, Rule 9, Rule 10, Rule 11.
- Ordered files:

#### File 1 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetry.java`

- Purpose: Pinned test provider and safe OTLP export.
- Symbols: open, Export, Role.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1/P1.
- Contract/signature changes: open, Export, Role.
- Input/output and state mapping: validate exact loopback OTLP URI before constructing client; require fixed scope, name, kind and zero attributes/events; encode sampled remote link and preserve distinct ambient parent;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
validate exact loopback OTLP URI before constructing client;
require fixed scope, name, kind and zero attributes/events;
encode sampled remote link and preserve distinct ambient parent;
```

- Verification contribution: G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 2 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkInitializer.java`

- Purpose: Only the private profile may register test provider.
- Symbols: initialize.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for R1/G1/P1.
- Contract/signature changes: initialize.
- Input/output and state mapping: require fixture run-authorized and s12-receive-link true; parse exact role and private collector endpoint from verified settings; register one fixed named test bean with destroy close;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
require fixture run-authorized and s12-receive-link true;
parse exact role and private collector endpoint from verified settings;
register one fixed named test bean with destroy close;
```

- Verification contribution: R1/G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 3 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java`

- Purpose: Reject stale/corrupt collector input and runtime SDK drift.
- Symbols: prepare, readPrivate, linked.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for R1/G1/P1.
- Contract/signature changes: prepare, readPrivate, linked.
- Input/output and state mapping: stage unique exact AS nested trace/common JARs and preserve compiled digest pins; read private raw bounded JSONL and require six distinct trace IDs; join each persisted enqueue carrier to exact delivery and receive links;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
stage unique exact AS nested trace/common JARs and preserve compiled digest pins;
read private raw bounded JSONL and require six distinct trace IDs;
join each persisted enqueue carrier to exact delivery and receive links;
```

- Verification contribution: R1/G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 4 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetryTest.java`

- Purpose: Real SDK finite export, privacy and endpoint tests.
- Symbols: encodes_exact_remote_links_and_distinct_local_parent_without_a_listener.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1.
- Contract/signature changes: encodes_exact_remote_links_and_distinct_local_parent_without_a_listener.
- Input/output and state mapping: create real SDK spans with an independent local parent; encode only expected fixed names and validate numeric remote flags; assert malformed endpoints reject before transport and batch privacy fail;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
create real SDK spans with an independent local parent;
encode only expected fixed names and validate numeric remote flags;
assert malformed endpoints reject before transport and batch privacy fail;
```

- Verification contribution: G1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 5 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java`

- Purpose: Finite positive and adversarial collector tests.
- Symbols: checks_both_persisted_sources_without_matching_by_timing_or_count_alone.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for R1/G1.
- Contract/signature changes: checks_both_persisted_sources_without_matching_by_timing_or_count_alone.
- Input/output and state mapping: assert two persisted distinct sources produce two complete raw chains; reject incomplete, stale, cross-linked or extra fields and duplicate traces; reject malformed kind/flags and assert initializer no implicit registration;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
assert two persisted distinct sources produce two complete raw chains;
reject incomplete, stale, cross-linked or extra fields and duplicate traces;
reject malformed kind/flags and assert initializer no implicit registration;
```

- Verification contribution: R1/G1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 6 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`

- Purpose: Verify isolated optional fixture launch with original modes unchanged.
- Symbols: Suite.RECEIVE_LINK, open, launchCommand, publishEvidence.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1/P1.
- Contract/signature changes: Suite.RECEIVE_LINK, open, launchCommand, publishEvidence.
- Input/output and state mapping: keep ordinary BUSINESS and RECOVERY paths exactly as before; S12 only: verify other fixture tree and source-constant SDK digests before child launch; reject S12 on ordinary expense evidence writer;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
keep ordinary BUSINESS and RECOVERY paths exactly as before;
S12 only: verify other fixture tree and source-constant SDK digests before child launch;
reject S12 on ordinary expense evidence writer;
```

- Verification contribution: G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 7 — `MODIFY stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`

- Purpose: AS explicit test-only initializer seam.
- Symbols: main.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1/P1.
- Contract/signature changes: main.
- Input/output and state mapping: validate existing owner-private settings before considering optional initializer; when special fixture flag true, resolve exactly one fixed test class; retain original sources and builder startup when flag is absent;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
validate existing owner-private settings before considering optional initializer;
when special fixture flag true, resolve exactly one fixed test class;
retain original sources and builder startup when flag is absent;
```

- Verification contribution: G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 8 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`

- Purpose: PS explicit test-only initializer seam.
- Symbols: main.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1/P1.
- Contract/signature changes: main.
- Input/output and state mapping: validate existing Process-only private settings and TLS before builder; attach fixed initializer only when new private fixture mode is present; retain current Process application source and disabled ordinary behavior;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
validate existing Process-only private settings and TLS before builder;
attach fixed initializer only when new private fixture mode is present;
retain current Process application source and disabled ordinary behavior;
```

- Verification contribution: G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 9 — `MODIFY stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`

- Purpose: Reuse durable normal-path evidence in the distinct S12 driver mode.
- Symbols: Driver.open, committedReceiveLinkCarrier, childConfig.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for G1/P1.
- Contract/signature changes: Driver.open, committedReceiveLinkCarrier, childConfig.
- Input/output and state mapping: ordinary BUSINESS/RECOVERY keep existing manifest property and relay; S12 alone sets three existing flags and exact direct HTTPS result destination; read the succeeded Outbox trace carrier only for the owned applied Inbox event;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
ordinary BUSINESS/RECOVERY keep existing manifest property and relay;
S12 alone sets three existing flags and exact direct HTTPS result destination;
read the succeeded Outbox trace carrier only for the owned applied Inbox event;
```

- Verification contribution: G1/P1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 10 — `CREATE stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkAcceptanceIT.java`

- Purpose: Explicit operator-only scenario.
- Symbols: receives_both_owned_terminal_results_with_exact_collector_links.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for P1/E1.
- Contract/signature changes: receives_both_owned_terminal_results_with_exact_collector_links.
- Input/output and state mapping: open separate S12 manifest and drive both owned normal expense cases; require scoped APPLIED, successful Outbox, one native advance and correct effects; close children, await bounded collector match and emit digest-only evidence;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
open separate S12 manifest and drive both owned normal expense cases;
require scoped APPLIED, successful Outbox, one native advance and correct effects;
close children, await bounded collector match and emit digest-only evidence;
```

- Verification contribution: P1/E1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 11 — `CREATE stellar-enclosure-tianji-process-server/src/test/resources/acceptance/s12/collector-v0.136.0.yaml`

- Purpose: Pinned offline operator fixture resource.
- Symbols: OTLP receiver and File Exporter.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for P1/E1.
- Contract/signature changes: OTLP receiver and File Exporter.
- Input/output and state mapping: configure only loopback OTLP HTTP /v1/traces and private file output; pin exact collector release in the operator procedure; keep metrics disabled and no external pipeline or binary;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```yaml
configure only loopback OTLP HTTP /v1/traces and private file output;
pin exact collector release in the operator procedure;
keep metrics disabled and no external pipeline or binary;
```

- Verification contribution: P1/E1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 12 — `CREATE docs/egon/spec/2026-11-06-00-37-s12-receive-link-evidence.md`

- Purpose: Reviewed design.
- Symbols: REQ-001–004 and exact source boundaries.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for S1.
- Contract/signature changes: REQ-001–004 and exact source boundaries.
- Input/output and state mapping: bind reviewed owner/provenance and privacy decisions; lock the exact 15-path source and document inventory; keep all real execution authorization independently reviewed;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
bind reviewed owner/provenance and privacy decisions;
lock the exact 15-path source and document inventory;
keep all real execution authorization independently reviewed;
```

- Verification contribution: S1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 13 — `CREATE docs/egon/plan/2026-11-06-00-38-s12-receive-link-evidence.md`

- Purpose: Exact source execution order.
- Symbols: Step 1 and R1/G1/P1/S1/E1.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for S1.
- Contract/signature changes: Step 1 and R1/G1/P1/S1/E1.
- Input/output and state mapping: bind reviewed Spec SHA and actual source baseline; list source phase inputs, finite selectors and exact stage commands; separate source acceptance from actual isolated operator test;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
bind reviewed Spec SHA and actual source baseline;
list source phase inputs, finite selectors and exact stage commands;
separate source acceptance from actual isolated operator test;
```

- Verification contribution: S1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 14 — `CREATE docs/egon/evidence/2026-11-06-s12-receive-link-operator.md`

- Purpose: Operator-only executable recipe and evidence retention.
- Symbols: manifest, package, collector and raw evidence rules.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for P1/E1.
- Contract/signature changes: manifest, package, collector and raw evidence rules.
- Input/output and state mapping: bind exact collector archive, executable, config and independent process facts; lock both actual executables and unsigned UTF-8 class-tree digests; record final raw collector hash and exact non-skipped E XML;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
bind exact collector archive, executable, config and independent process facts;
lock both actual executables and unsigned UTF-8 class-tree digests;
record final raw collector hash and exact non-skipped E XML;
```

- Verification contribution: P1/E1.
- After this file: Exact test-local path participates only in the declared gate.

#### File 15 — `CREATE docs/egon/evidence/2026-11-06-s12-receive-link-evidence.md`

- Purpose: Actual gate results and E boundary.
- Symbols: R1/G1/P1/S1/E1 evidence.
- Repository evidence: Spec §2/7 and exact §8 test/resource role.
- Dependencies and consumers: Preceding paths or the exact phase input in §8.
- Why now: Required for S1.
- Contract/signature changes: R1/G1/P1/S1/E1 evidence.
- Input/output and state mapping: retain actual RED/GREEN selected reports and immutable artifact proof; record owned fixture paths and collector dependencies kept out of archives; label actual E outstanding until operator output exists;
- Error and edge behavior: Fail closed with no positive evidence on mismatch; preserve all ordinary fixture behavior.
- Standards impact: MC-REUSE-001, MC-SCOPE-001, MC-TEST-001.
- Literal rule enforcement: Rule 11 preserves existing owner stores, schema and production CQE.
- Implementation pseudocode:

```java
retain actual RED/GREEN selected reports and immutable artifact proof;
record owned fixture paths and collector dependencies kept out of archives;
label actual E outstanding until operator output exists;
```

- Verification contribution: S1.
- After this file: Exact test-local path participates only in the declared gate.

- Validation working directory: /Users/mario/SelfProject/Stellar-Enclosure/tianji-service.
- Verification command: §8 exact offline commands and archive checks.
- Expected result: One intended R1 assertion; G1/P1 pass with zero failures/errors/skips and no E execution.
- Failure returns to: Exact owning phase; no fixture, dependency or runtime scope expansion.
- Completion criteria: Exact staged paths, archive no-test leakage, raw finite reports and source evidence.
- Rollback: Before commit withdraw only Step 1. Later omit new opt-in or separately revert the one commit.
- Commit paths: `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetry.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkInitializer.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidence.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/acceptance/S12ReceiveLinkTelemetryTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkCollectorEvidenceTest.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseAcceptanceHarness.java`, `stellar-enclosure-tianji-agent-server/src/test/java/top/egon/cola/platform/tianji/agent/server/acceptance/ExpenseAgentAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/ExpenseProcessAcceptanceMain.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/BusinessProcessAgentAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/acceptance/S12ReceiveLinkAcceptanceIT.java`, `stellar-enclosure-tianji-process-server/src/test/resources/acceptance/s12/collector-v0.136.0.yaml`, `docs/egon/spec/2026-11-06-00-37-s12-receive-link-evidence.md`, `docs/egon/plan/2026-11-06-00-38-s12-receive-link-evidence.md`, `docs/egon/evidence/2026-11-06-s12-receive-link-operator.md`, `docs/egon/evidence/2026-11-06-s12-receive-link-evidence.md`
- Commit: test(tianji): add isolated receive-link acceptance fixture.

## 8. Test, Validation, and Quality Gates

| Gate | Exact phase | Outcome |
| --- | --- | --- |
| R1 | A CollectorEvidence; B CollectorEvidenceTest, Initializer, Telemetry; base harness unchanged | Exact positive parser case expected true vs false; one assertion and no errors/skips |
| G1 | All B source; exact finite selector | Both fixture test classes and existing regression owners pass; no E report |
| P1 | Clean actual AS/PS archives with same finite selector | PaC and production declarations unchanged; E compile only; exact SDK hashes and class-only linkage |
| S1 | Strict docs and exact staged path check | One source commit, no I/E result |
| E1 | Separate operator opt-in with independent environment and authorization | One non-skipped E report and matching raw private collector; never execute in this Plan |

```sh
s12_settings=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/settings.xml
s12_jdk=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
s12_base=f7641a373892bd9e054f79ec97bfbb9dfc8c8e45
s12_red='S12ReceiveLinkCollectorEvidenceTest#checks_both_persisted_sources_without_matching_by_timing_or_count_alone'
s12_finite=S12ReceiveLinkTelemetryTest,S12ReceiveLinkCollectorEvidenceTest,ExpenseAcceptanceHarnessTest,ExpenseAcceptanceModelTest,ExpenseFixtureModelsTest,AgentResultSpanObservationTest,AgentResultDeliveryHandlerTest,HttpAgentResultTraceLinkTest,AgentResultInboxControllerTest
s12_run_finite() {
  JAVA_HOME="$s12_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$s12_settings" -s "$s12_settings" -o -B -ntp \
    -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$1"
}
s12_run_finite "$s12_red"
s12_run_finite "$s12_finite"
test "$(git rev-parse --verify HEAD)" = "$s12_base"
JAVA_HOME="$s12_jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
  ./scripts/verify-source-reactor.sh -gs "$s12_settings" -s "$s12_settings" -o clean -B -ntp \
  -Ppermission-producer "-Dpermission.commit=$s12_base" -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-receive-link-fixture \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$s12_finite"
python3 ../.agents/skills/stellar-enclosure-coding-writing-spec/scripts/validate_spec.py docs/egon/spec/2026-11-06-00-37-s12-receive-link-evidence.md --strict
python3 ../.agents/skills/stellar-enclosure-coding-writing-plan/scripts/validate_plan.py docs/egon/plan/2026-11-06-00-38-s12-receive-link-evidence.md --strict
git diff --check
git diff --cached --check
git diff --cached --name-only
```

Execute R1 and G1 only at their prescribed phases. Explicitly exclude S12ReceiveLinkAcceptanceIT, BusinessProcessAgentAcceptanceIT, WorkerRecoveryIT and all Postgres/I tests. Clear both tianji.expense.e2e.manifest and tianji.s12.receive-link.manifest from the source gate. Fresh R/G/P Surefire XML must contain no E test case or skipped-runtime report. Verify ordinary Driver opens BUSINESS and RECOVERY with the original property, ordinary launcher uses one role test class tree, original result delivery uses resultRelay, and ordinary acceptance evidence names and modes remain unchanged. The existing harness finite suite must pass.

P1 reads actual AS/PS Boot jars and raw PaC and verifies no S12 classes or collector YAML entered production archives, no new PS OTel SDK entry and no migration or declaration delta. Confirm the S12 E test class, both existing E test classes and collector YAML exist only in expected test output. Verify exact nested OTel SDK trace/common hashes. Class-only proof may use isolated/S12ClassPathProbe.java with the actual packaged PropertiesLauncher and scratch test classes, without starting application contexts or exporters. Keep archive digests, XML and output privately.

E1 is out of source execution scope. The operator procedure requires exact actual artifact locks, official collector metadata and file hashes, fresh privateRoot and isolated stores, and one real test report with zero failures/errors/skips. No runtime credit before that report and raw collector verification.

## 9. Migration, Compatibility, Rollout, and Rollback

No production activation or migration. Current expense profile stays intact. New private S12 manifest and test selector provide opt-in only.

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Step | Gate |
| --- | --- | --- |
| REQ-001 | 1 | G1/P1/E1 |
| REQ-002 | 1 | P1/E1 |
| REQ-003 | 1 | R1/G1/P1/E1 |
| REQ-004 | 1 | G1/P1/S1/E1 |

## 11. Risks, Blockers, and User Decisions

Actual operator execution, collector identity, logs/metrics, replay and full S12 are independent and not authorized by this Plan. Test-only source changes require reviewed GO.

## 12. Review and Acceptance

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | §5/7/8/14/16 | Existing fixture and actual AS/PS | None |
| MC-REUSE-001 | Applicable | PASS | §5/7/8/14/16 | Existing real launcher and normal witness | None |
| MC-DEP-001 | Applicable | PASS | §5/7/8/14/16 | No POM or new production dependency | None |
| MC-NAME-001 | Applicable | PASS | §5/7/8/14/16 | Explicit test fixture types | None |
| MC-VALID-001 | Applicable | PASS | §5/7/8/14/16 | Exact manifest, local endpoint and closed parser | None |
| MC-MODEL-001 | Applicable | PASS | §5/7/8/14/16 | Immutable source evidence | None |
| MC-CONVERT-001 | Applicable | PASS | §5/7/8/14/16 | Fixed SDK envelope | None |
| MC-LOG-001 | Applicable | PASS | §5/7/8/14/16 | Digest-only report and no product log | None |
| MC-BEAN-001 | Applicable | PASS | §5/7/8/14/16 | One explicit test provider only | None |
| MC-UTIL-001 | Applicable | PASS | §5/7/8/14/16 | Existing JDK, Jackson and SDK | None |
| MC-JSON-001 | Applicable | PASS | §5/7/8/14/16 | Strict private collector JSON | None |
| MC-TIME-001 | Applicable | PASS | §5/7/8/14/16 | UTC nanos and monotonic wait | None |
| MC-CONFIG-001 | Applicable | PASS | §5/7/8/14/16 | Private test opt-in | None |
| MC-PATTERN-001 | Applicable | PASS | §5/7/8/14/16 | Existing driver and fixed exporter | None |
| MC-SCOPE-001 | Applicable | PASS | §5/7/8/14/16 | 15 test/resource/doc paths only | None |
| MC-TEST-001 | Applicable | PASS | §5/7/8/14/16 | Finite/packaged/live evidence separated | None |
| MC-BLOCKER-001 | Applicable | PASS | §5/7/8/14/16 | No E or production activation inferred | None |

**PASS — Ready for user review**
