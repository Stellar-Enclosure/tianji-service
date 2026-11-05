# Default-off native template factories — Step 51 / native Plan Step 3

- Baseline: `9366588663525c94b6aae22fd8147a40e8fb79bc`, clean; 916 tracked files and 25 immutable main-classpath SQL files. All Step 50 path hashes were verified before editing.
- Effective Spec: [Native Agentic templates](../spec/2026-11-05-04-45-native-agentic-transfer-templates.md), SHA-256 `03a94fa8730193f0d0cbc887374fdb618e4795196913d91cf2d1f8ced8a67270`.
- Effective Plan: [Native template implementation](../plan/2026-11-05-05-22-native-agentic-transfer-templates.md), SHA-256 `c4fe5575a4603b65d040aa1181123d84f10e4a8ab247f240f1eb89a30efd98d2`.

The AS host now conditionally contributes exactly two Function-compatible strategies under the existing `tianjiAgentCodeFirstFactories` name. Strict properties select one existing ChatModel by exact qualified map key/name; missing model/options/adapter, callback-bearing defaults, invalid metadata or unknown enabled properties fail configuration. Native flag off leaves the properties unbound and contributes no map. No model client, credential, Runner or definition is created by configuration. Each factory apply builds fresh LlmAgents sharing one explicitly adapted BaseLlm, fixed output keys/parent edges/transfer flags and immutable v1 metadata. Instructions require child text and parent transfer in one complete response; Session/outputKey values are not branch evidence.

The existing adapter has only `create(ChatModel,String)`, without a Scheduler overload. The Function argument is validated nonnull, while the existing adapter owns its execution scheduling. No Components or dependency change was introduced. The sole AS base YAML has all four keys with `enabled=false` and empty model metadata.

## Actual finite validation and artifacts

Commands used JDK 25, `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false`, the existing offline source reactor/settings, `-DskipITs=true`, explicit selectors and all three OTel exporters disabled. No application/server, provider, database, listener, browser or GUI was started.

| Gate | Actual result | Log |
| --- | --- | --- |
| Presence/resource RED | 5 tests, 5 expected assertion failures, 0 errors/skips; 16.802 s | `/tmp/tianji-native-step51-presence-red.log` |
| Compile seam A | 4 type-presence assertions passed; the base YAML assertion remained the one expected failure, 0 errors; 15.525 s | `/tmp/tianji-native-step51-compile-a.log` |
| Typed behavior RED | 22 tests, 17 expected assertion failures, 0 errors/skips; 16.496 s | `/tmp/tianji-native-step51-typed-red.log` |
| Direct factories/config GREEN | 22 passed, 0 failures/errors/skips; 17.778 s | `/tmp/tianji-native-step51-green.log` |
| Required clean finite verify | `STELLAR_VERIFY_GOAL=verify` plus `clean` and the Plan's exact 18-class selector: 181 passed, 0 failures/errors/skips; 1:07 min, 2026-11-05 08:38:43 +08:00 | `/tmp/tianji-native-step51-clean-verify.log` |

Clean verify reports 20 Components adapter tests, 136 AR tests and 25 AS tests. It includes both real Runner/scripted-model flows and the default-shell ApplicationContextRunner test that asserts no AgentClient/DataSource/web-server factory. These remain finite source/package proofs.

Actual fresh JAR inspection used `jar tf`, `unzip -p` and `shasum -a 256`. AR contains NativeAgentCompilerStrategy, NativeAgentGate, AdkRunBudgetPlugin and AdkAgentRunExecutor; AS contains NativeAgentTemplateProperties, both factories and TianjiNativeAgentTemplateConfiguration. Packaged application.yml is byte-identical to source and its four native keys are complete/default-off.

| Artifact | SHA-256 |
| --- | --- |
| AR `stellar-enclosure-tianji-agent-runtime-0.0.1-SNAPSHOT.jar` | `0bf070c12015b673bf01f16c454a822c93dd02e41b9c3da254e9000f58cc5736` |
| AS `stellar-enclosure-tianji-agent-server.jar` | `03ab8b74a2c21f41aa7ae5815c293055622d2e16e4c4625984b667657bd5800a` |
| Packaged AS application.yml | `b998a67e943b613c5c6b60d07e3b92bd043f33c5d2fa8fa7ad372a8af143fe5a` |

The exact nine Step paths, 915 excluded tracked SHA values and 25 SQL hashes were checked. The aggregate three-Step scope remains 26 unique paths. No POM, C/CL/API, permissions, migration, Components source or frontend change is part of this slice. Build artifacts are not committed, registered, activated or granted.

## Effective Spec conformance for this first slice

| Requirement | Source and finite evidence |
| --- | --- |
| REQ-901 | Strategy extends existing Function ABI; plain-Function regression passes. |
| REQ-902 | Exact metadata/tree/BaseLlm/ChatModel/profile/snapshot binding and canonical semantic hash; compiler, Gate, estimator, factories and executor tests. |
| REQ-903 | Conditional named host map, strict metadata, qualified existing model/adapter and default-off YAML; config/default-shell tests and actual JAR. |
| REQ-904 | Executor rejects nonempty config/tools/allowed tools, wrong schema/route/codeRef or nonzero tool budget before model. |
| REQ-905 | Conditional one-child round trip, depth2/max2 and exact child output; quick/deep Runner and Gate matrix. |
| REQ-906 | Researcher then critic round trips, depth2/max4 and exact outputs; supervisor Runner plus order/duplicate/cap negatives. |
| REQ-907 | Unique nonreserved keys; handoff nonfinal/stateDelta fact and later root-final child-key overwrite observed; collector ignores keys. |
| REQ-908 | Pinned Method.equals/name/schema/one agent_name argument; spoof/malformed/unknown/self/peer tests. |
| REQ-909 | Current MODEL/session checks then existing ledger CAS before dispatch; denied/fenced/append failures block action. |
| REQ-910 | Actual context/call-ID single use, including confirmed-key reuse before a second audit; absent/empty/present/mismatch tests. |
| REQ-911 | Authorization payload exactly to/ordinal/topologyHash/state; no text or arguments; audit tests. |
| REQ-912 | Native tracking without ordinary scope, exact current role before budget and distinct per-agent context identity; context/Runner tests. |
| REQ-913 | Settled current nonpartial text+call envelope, UTF-16/control/size bounds and no normalization; Gate tests. |
| REQ-914 | Exact actual transfer action confirms child result, identical after replay only; Gate/Runner tests. |
| REQ-915 | Sticky sanitized fatal token and same authorization/ledger/timeout disposition across callback/control; native callback-before-deadline leased finish test. |
| REQ-916 | Genuine current root final plus exact confirmed children; missing/stale/foreign/fatal negatives; existing agents/text output. |
| REQ-917 | Exact-snapshot per-execution budget view, shared model accounting and zero ordinary tool entries; estimator/context/Runner tests. |
| REQ-918 | Single queued claim/no same-run resume preserved; no checkpoint/recovery API/table added. |
| REQ-919 | Existing contracts/Function/CONFIG/tool paths and default shell pass; aggregate path/POM/SQL audit. |
| REQ-920 | Full bounded task decomposition remains Required and unimplemented by this first slice; T3-09/overall roadmap completion is not claimed. |

## Blocking Manual Checks

| Check | Applicability | Status | Evidence and finding |
| --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | Existing AS technical package and AR Strategy/Function map; no new module/layer. |
| MC-REUSE-001 | Applicable | PASS | Existing adapter, qualified model map, named fallback ABI, Spring binder and LlmAgent builder reused. |
| MC-DEP-001 | Applicable | PASS | All POMs baseline-identical; existing ADK/Spring AI/Boot/JDK only. |
| MC-NAME-001 | Applicable | PASS | Properties value, Configuration and two Factory behavior classes follow technical roles. |
| MC-VALID-001 | Applicable | PASS | Compact properties guards and exact host options/model/qualifier checks; positive/negative config and factory tests. |
| MC-MODEL-001 | Applicable | PASS | Immutable properties/TemplateSpec values and final factory fields; fresh tree per apply. |
| MC-CONVERT-001 | Not applicable | N/A | No DTO projection or Converter introduced. |
| MC-LOG-001 | Not applicable | N/A | No business Service/logger or log statement added. |
| MC-BEAN-001 | Applicable | PASS | Explicit tianjiAgentCodeFirstFactories Bean and exact agentFlowChatModels/springAiModelAdapterFactory Qualifiers; disabled config creates no map/properties. |
| MC-UTIL-001 | Applicable | PASS | JDK validation/collections/Objects; existing adapter/binder; no utility dependency. |
| MC-JSON-001 | Not applicable | N/A | No wire/Event/schema/codec change in this Step. |
| MC-TIME-001 | Not applicable | N/A | No date/time symbol or temporal boundary changed. |
| MC-CONFIG-001 | Applicable | PASS | AS has one base profile; all four native keys default-off and actual packaged YAML matches source. |
| MC-PATTERN-001 | Applicable | PASS | Two existing-ABI Strategy implementations isolate fixed topologies and reuse immutable compiler metadata; no extra registry/DSL. |
| MC-SCOPE-001 | Applicable | PASS | Exact nine paths, 915 excluded tracked hashes and 25 SQL unchanged; aggregate 26 unique paths. |
| MC-TEST-001 | Applicable | PASS | Actual RED/A/typed RED/22 GREEN, clean 181 tests, eight fresh JAR classes and default-off resource proof. |
| MC-BLOCKER-001 | Applicable | PASS | All current-Step rows closed; remaining roadmap/runtime gates explicitly excluded from completion claims. |

## Literal Rule Gate

| Rule | Applicability | Status | Independent evidence |
| --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | Factory/Configuration behavior and immutable Properties value roles; exact existing package style. |
| Rule 2 | Applicable | PASS | Strict property binding, host metadata/model/callback validation and nonnull factory handoffs. |
| Rule 3 | Applicable | PASS | Properties is an immutable value record; concrete factories use final fields, not ordinary POJO/entity records. |
| Rule 4 | Applicable | PASS | Explicit named technical Bean and qualified method injection; no managed business class or logger introduced. |
| Rule 5 | Applicable | PASS | JDK utilities and existing Spring/ADK adapter only. |
| Rule 6 | Not applicable | N/A | No JSON, persisted enum or frontend contract change. |
| Rule 7 | Applicable | PASS | Complete default-off key set in the sole AS base profile and actual JAR; no other AS profile exists. |
| Rule 9 | Applicable | PASS | Approved Strategy factories for the concrete topology variation; fixed codeRefs and immutable metadata. |
| Rule 10 | Not applicable | N/A | No date/time field or boundary change. |
| Rule 11 | Applicable | PASS | Technical AS/AR vertical preserved, Components reused, no persistence/DDL/business Event/CRUD/generator change. |

NativeTemplatesEnabledByDefault=false; FactoryMapWhenEnabled=exactly2; RuntimeUnverified=true; ProductionAccepted=false. Actual provider behavior, IAM, PostgreSQL, browser, deployment and complete S03/S10/S11/T3-09 acceptance remain unexecuted. Required work continues with bounded task decomposition and the remaining compensation/recovery, business samples and T4 roadmap.
