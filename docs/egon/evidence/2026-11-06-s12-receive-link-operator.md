# T4-02 S12a 接收 Link 运行候选

This is a source-only operator fixture proposal. The current evidence covers JDK compilation, finite SDK/parser tests and AS/PS archive class loading. The fixture does not yet have a real operator execution report.

## Exact opt-in

Use the existing T3-12 owner-private two-tenant fixtures and three fresh disposable stores in a separately authorized environment. Use a new owner-private privateRoot for every attempt. The exact extracted SDK names are written with CREATE_NEW and remain in the run bundle. Keep the AR history at V1–V14, the existing PS/Worker histories, and all default product flags. The S12 fixture uses the same manifest shape with suite RECEIVE_LINK and a separate absolute tianji.s12.receive-link.manifest. It may never emit the Business/Recovery acceptance document or be passed to the expense evidence gate.

Use exact independently locked revisions, AS/PS executable SHA-256 and class-tree SHA-256 as existing ExpenseAcceptanceHarness verifies. The test code adds one dedicated normal path per tenant. No real provider or real expense effect is used. The S12 child enables exactly the existing enqueue, cross-Process delivery and receiver flags in its private configuration. The AS result client posts directly to the exact PS loopback HTTPS root. The expense fault relay continues to handle existing linked/Worker paths and binds its separate resultRelay listener. S12 AS delivery bypasses that listener. Keep all manifest ports distinct.

The unique role-specific test OpenTelemetry provider exports only the fixed trace span envelope. AS loads its existing SDK. PS loads only sdk-trace and sdk-common 1.62.0 extracted as exact nested entries from the separately hash-locked AS archive and rechecked before launch. Their expected hashes are asserted in source. No POM or production dependency/configuration change is proposed.

## Collector

Use official contrib 0.136.0 File Exporter. The verified Linux ARM64 asset is otelcol-contrib_0.136.0_linux_arm64.tar.gz, SHA-256 f768015766c8ace50caf9130ad0f38e4781f76b9abe4380b26c875108bb51899. If the operator uses a different supported host, independently bind that exact official release asset and SHA-256 before launch. No binary was fetched or installed by this source work. Record release-asset SHA, unpacked executable SHA and rendered configuration SHA in the private operator lock. [Official release assets](https://github.com/open-telemetry/opentelemetry-collector-releases/releases/expanded_assets/v0.136.0)

Copy the [pinned collector fixture](../../../stellar-enclosure-tianji-process-server/src/test/resources/acceptance/s12/collector-v0.136.0.yaml) into the owner-private run directory. Before launching the collector, prepare one new empty s12-receive-link-traces.jsonl with mode 0600 and link count one. Run the collector from that directory with umask 077, no remote exporters, and one dedicated local OTLP HTTP port. The manifest endpoint must be exactly http://127.0.0.1:4318/v1/traces for the supplied template. Record the collector PID and argv, its independently verified executable and rendered configuration SHA, and its stop outcome. The test does not authenticate the collector executable or configuration. Reject the run if this independent provenance and raw output cannot be retained. [Pinned File Exporter format](https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.136.0/exporter/fileexporter/README.md) · [OTLP JSON protocol](https://opentelemetry.io/docs/specs/otlp/#json-protobuf-encoding)

## Commands after authorized review

Build the actual archives before freezing the private manifest. Use the approved JDK, offline settings and exact Service commit. Lock each executable SHA-256 and each test class-tree SHA-256. To compute the latter, sort all regular file paths relative to target/test-classes by unsigned UTF-8 bytes, append for each a four-byte big-endian path byte length, the UTF-8 path bytes, and the raw 32-byte SHA-256 of the file. Hash the entire byte stream. Reject symlinks and follow the existing harness size bounds. Preserve the packaged archives and class trees through the E invocation. A changed class tree cannot pass the locked manifest preflight.

```sh
JAVA_HOME="$jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=verify \
  ./scripts/verify-source-reactor.sh -gs "$settings" -s "$settings" -o -B -ntp clean \
  -Ppermission-producer "-Dpermission.commit=$approved_service_sha" \
  -Dpermission.buildId=LOCAL_UNCOMMITTED-s12-receive-link-fixture \
  -DskipITs=true -DskipTests=true \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

Once the operator has completed the private manifest and separately authorized startup, use the same approved settings and only the exact S12a test selector. A missing manifest makes the explicitly selected test skip and cannot satisfy E. Keep the three service credential files and raw output private.

```sh
JAVA_HOME="$jdk" ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test ./scripts/verify-source-reactor.sh -gs "$settings" -s "$settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=S12ReceiveLinkAcceptanceIT \
  "-Dtianji.s12.receive-link.manifest=$approved_absolute_private_manifest"
```

The E test starts only its owned test child processes, stops them, waits at most five seconds for the final collector file, and passes only when both durable enqueue sources match exactly two independent three-span chains with six distinct trace IDs. Retain the unmodified collector JSONL, exact Surefire XML, private manifest and digest ledger in the same run. The JUnit report contains one S12_RECEIVE_LINK_SUBSET line with the collector SHA, AS/PS archive and fixture SHA, and paths=2. After the test, stop the collector and retain its final raw file unchanged. Require one test executed, zero errors/failures/skips, the final post-shutdown raw collector SHA matching the JUnit report, and exact archive, class tree, collector and manifest provenance. No raw owner, run, event, or trace identifiers go into the test summary.

## Meaning of a positive result

The existing Driver observes matching START receipts, scoped linked Agent terminal states, successful terminal Outbox, scoped APPLIED Inbox, exactly one applied AGENT_RESULT receipt and native wait advance, and one fixture original and notice receipt per tenant. The new collector gate reads each exact applied event's own durable enqueue carrier and verifies enqueue → delivery → receiver with real remote Link IDs, distinct traces, and no substitution of the AS source as a PS parent. It fails on absent, stale, extra, cross-linked or sensitive trace observations. The run uses direct AS→PS HTTPS and does not replay a result POST. Durable restart replay, negative transport routes, Worker Links, V15, metrics cardinality, and process/application log scans remain separate.

No app, collector, listener, database or provider was started for this draft. Existing business and recovery acceptance requirements retain their own scopes.
