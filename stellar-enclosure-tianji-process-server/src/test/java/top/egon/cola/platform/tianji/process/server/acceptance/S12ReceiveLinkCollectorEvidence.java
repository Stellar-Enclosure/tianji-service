package top.egon.cola.platform.tianji.process.server.acceptance;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.client.AgentResultTraceLinkCarrier;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Private collector and runtime-library evidence for the isolated receive-link scenario only. */
final class S12ReceiveLinkCollectorEvidence {
    static final String TRACE_FILE = "s12-receive-link-traces.jsonl";
    static final List<String> TRACE_LIBRARIES = List.of("opentelemetry-sdk-trace-1.62.0.jar", "opentelemetry-sdk-common-1.62.0.jar");
    private static final Map<String, String> SDK_SHA256 = Map.of(
            "opentelemetry-sdk-trace-1.62.0.jar", "fb6c0953b229a6da1db813135ae11a621bf9da56b7356c1e93049ed4b088c232",
            "opentelemetry-sdk-common-1.62.0.jar", "bf9d13df9a50e49a5db664ba8ebfad79f73f03219d2ac6f6709d432f6896eba0");
    private static final String AGENT_SCOPE = "top.egon.cola.tianji.agent.result";
    private static final String PROCESS_SCOPE = "top.egon.cola.platform.tianji.process.agent.result";
    private static final String ENQUEUE = "tianji.agent.result.enqueue";
    private static final String DELIVER = "tianji.agent.result.deliver";
    private static final String RECEIVE = "tianji.process.agent.result.receive";
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_REPLAY_DELIVERIES = 8;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Set<String> SPAN_FIELDS = Set.of("traceId", "spanId", "traceState", "parentSpanId", "flags", "name",
            "kind", "startTimeUnixNano", "endTimeUnixNano", "attributes", "droppedAttributesCount", "events",
            "droppedEventsCount", "links", "droppedLinksCount", "status");

    private S12ReceiveLinkCollectorEvidence() { }

    static Map<Path, String> prepare(ExpenseAcceptanceManifestDTO manifest) throws IOException {
        var root = Path.of(manifest.privateRoot());
        String endpoint = manifest.endpoints().collector();
        URI uri = URI.create(endpoint);
        require(uri.getPort() > 0 && endpoint.equals("http://127.0.0.1:" + uri.getPort() + "/v1/traces"));
        require(manifest.agent().testClasses().indexOf(',') < 0 && manifest.process().testClasses().indexOf(',') < 0
                && root.toString().indexOf(',') < 0);
        var traceFile = root.resolve(TRACE_FILE);
        require(readPrivate(traceFile).length == 0);
        var archivePath = Path.of(manifest.agent().execJar());
        require(manifest.agent().execJarSha256().equals(ExpenseAcceptanceHarness.sha256(archivePath)));
        var libraries = new LinkedHashMap<Path, String>();
        try (var jar = new JarFile(archivePath.toFile(), false)) {
            for (String name : TRACE_LIBRARIES) {
                var entryName = "BOOT-INF/lib/" + name;
                require(jar.stream().filter(entry -> entryName.equals(entry.getName())).count() == 1);
                var entry = jar.getJarEntry(entryName);
                require(entry.getSize() > 0 && entry.getSize() <= 1_048_576);
                var file = root.resolve(name);
                try (var source = jar.getInputStream(entry);
                     var target = Files.newByteChannel(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                             LinkOption.NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    byte[] bytes = source.readNBytes(1_048_577);
                    require(bytes.length == entry.getSize());
                    var buffer = java.nio.ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) target.write(buffer);
                }
                String expectedDigest = SDK_SHA256.get(name);
                require(expectedDigest.equals(ExpenseAcceptanceHarness.sha256(file)));
                libraries.put(file, expectedDigest);
            }
        }
        return java.util.Collections.unmodifiableMap(libraries);
    }

    static byte[] readPrivate(Path file) throws IOException {
        require(file.isAbsolute() && file.equals(file.toRealPath()) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && Files.getOwner(file).equals(Files.getOwner(file.getParent()))
                && Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                && ((Number) Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue() == 1L);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            require(data.length <= MAX_BYTES);
            return data;
        }
    }

    static boolean linked(byte[] exported, List<String> expectedCarriers, Instant earliest) {
        require(exported != null && exported.length <= MAX_BYTES && expectedCarriers.size() == 2 && earliest != null);
        var bySpan = readSpans(exported, earliest, 6);
        if (bySpan.size() < 6) return false;
        var consumed = new HashSet<String>();
        for (String carrier : expectedCarriers) {
            require(carrier.matches("tianji-link-v1:00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
            String key = carrier.substring("tianji-link-v1:00-".length(), carrier.length() - 3);
            var enqueue = bySpan.get(key);
            require(enqueue != null && ENQUEUE.equals(enqueue.name()) && consumed.add(key));
            var deliver = findLinked(bySpan, DELIVER, key);
            var receive = findLinked(bySpan, RECEIVE, deliver.key());
            require(consumed.add(deliver.key()) && consumed.add(receive.key())
                    && !deliver.traceId().equals(enqueue.traceId()) && !receive.traceId().equals(deliver.traceId())
                    && !receive.parentId().equals(deliver.spanId()));
        }
        require(consumed.size() == 6);
        return true;
    }

    static boolean replayLinked(byte[] exported, String expectedEnqueue, String lostLink, String replayLink,
                                Instant earliest, Instant replacementStartedAt) {
        require(exported != null && exported.length <= MAX_BYTES && earliest != null && replacementStartedAt != null
                && !replacementStartedAt.isBefore(earliest));
        require(expectedEnqueue != null && expectedEnqueue.startsWith("tianji-link-v1:")
                && AgentResultTraceLinkCarrier.isValid(expectedEnqueue.substring("tianji-link-v1:".length()))
                && AgentResultTraceLinkCarrier.isValid(lostLink) && AgentResultTraceLinkCarrier.isValid(replayLink)
                && !lostLink.equals(replayLink));
        String enqueueKey = linkKey(expectedEnqueue.substring("tianji-link-v1:".length()));
        String lostKey = linkKey(lostLink);
        String replayKey = linkKey(replayLink);
        var spans = readSpans(exported, earliest, 1 + MAX_REPLAY_DELIVERIES + 2);
        int received = 0;
        int deliveries = 0;
        for (var span : spans.values()) {
            switch (span.name()) {
                case ENQUEUE -> require(enqueueKey.equals(span.key()));
                case DELIVER -> {
                    deliveries++;
                    require(enqueueKey.equals(span.linked()) && deliveries <= MAX_REPLAY_DELIVERIES);
                }
                case RECEIVE -> {
                    received++;
                    require((lostKey.equals(span.linked()) || replayKey.equals(span.linked())) && received <= 2);
                }
                default -> throw new IllegalStateException("Invalid S12 receive-link collector evidence");
            }
        }
        var lost = spans.get(lostKey);
        var replay = spans.get(replayKey);
        if (!spans.containsKey(enqueueKey) || lost == null || replay == null || received < 2) return false;
        require(DELIVER.equals(lost.name()) && DELIVER.equals(replay.name())
                && lost.startedAt().isBefore(replacementStartedAt)
                && !replay.startedAt().isBefore(replacementStartedAt));
        var lostReceive = findLinked(spans, RECEIVE, lostKey);
        var replayReceive = findLinked(spans, RECEIVE, replayKey);
        require(!lostReceive.parentId().equals(lost.spanId()) && !replayReceive.parentId().equals(replay.spanId()));
        return true;
    }

    private static String linkKey(String wireCarrier) {
        return wireCarrier.substring(3, wireCarrier.length() - 3);
    }

    private static Map<String, ObservedSpan> readSpans(byte[] exported, Instant earliest, int maximumSpans) {
        if (exported.length == 0 || exported[exported.length - 1] != '\n') return Map.of();
        var bySpan = new HashMap<String, ObservedSpan>();
        var traceIds = new HashSet<String>();
        int lines = 0;
        for (String line : new String(exported, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
            require(!line.isEmpty() && ++lines <= 24);
            JsonNode batch = JSON.readTree(line);
            fields(batch, Set.of("resourceSpans"));
            for (JsonNode resource : array(batch.path("resourceSpans"), 4)) {
                fields(resource, Set.of("resource", "scopeSpans", "schemaUrl"));
                emptyText(resource, "schemaUrl");
                var labels = resource.path("resource");
                fields(labels, Set.of("attributes", "droppedAttributesCount"));
                zero(labels, "droppedAttributesCount");
                var attributes = array(labels.path("attributes"), 1);
                require(attributes.size() == 1);
                var service = attributes.get(0);
                fields(service, Set.of("key", "value"));
                require("service.name".equals(service.path("key").stringValue()));
                var value = service.path("value");
                fields(value, Set.of("stringValue"));
                var serviceName = value.path("stringValue").stringValue();
                for (JsonNode scope : array(resource.path("scopeSpans"), 4)) {
                    fields(scope, Set.of("scope", "spans", "schemaUrl"));
                    emptyText(scope, "schemaUrl");
                    var info = scope.path("scope");
                    fields(info, Set.of("name", "version", "attributes", "droppedAttributesCount"));
                    emptyText(info, "version");
                    empty(info, "attributes");
                    zero(info, "droppedAttributesCount");
                    String scopeName = info.path("name").stringValue();
                    boolean agent = "tianji-s12-agent".equals(serviceName) && AGENT_SCOPE.equals(scopeName);
                    require(agent || "tianji-s12-process".equals(serviceName) && PROCESS_SCOPE.equals(scopeName));
                    for (JsonNode span : array(scope.path("spans"), maximumSpans)) {
                        var observed = span(span, agent, earliest);
                        require(traceIds.add(observed.traceId()) && bySpan.putIfAbsent(observed.key(), observed) == null && bySpan.size() <= maximumSpans);
                    }
                }
            }
        }
        return bySpan;
    }

    private static ObservedSpan span(JsonNode node, boolean agent, Instant earliest) {
        fields(node, SPAN_FIELDS);
        String name = node.path("name").stringValue();
        boolean enqueue = ENQUEUE.equals(name);
        require(agent ? enqueue || DELIVER.equals(name) : RECEIVE.equals(name));
        require(integer(node, "kind") == (enqueue ? 4 : 5) && (integer(node, "flags") & 1) == 1);
        empty(node, "attributes"); empty(node, "events"); emptyText(node, "traceState");
        zero(node, "droppedAttributesCount"); zero(node, "droppedEventsCount"); zero(node, "droppedLinksCount");
        if (node.has("status")) {
            fields(node.get("status"), Set.of("code", "message"));
            zero(node.get("status"), "code");
            emptyText(node.get("status"), "message");
        }
        var start = instant(node.path("startTimeUnixNano"));
        var end = instant(node.path("endTimeUnixNano"));
        require(!start.isBefore(earliest) && !end.isBefore(start) && !end.isAfter(Instant.now().plusSeconds(2)));
        String parent = node.has("parentSpanId") ? node.path("parentSpanId").stringValue() : "";
        require(parent != null && (parent.isEmpty() || !agent && parent.matches("[0-9a-f]{16}")));
        String linked = null;
        if (enqueue) empty(node, "links");
        else {
            var links = array(node.path("links"), 1);
            require(links.size() == 1);
            var link = links.get(0);
            fields(link, Set.of("traceId", "spanId", "traceState", "attributes", "droppedAttributesCount", "flags"));
            empty(link, "attributes"); emptyText(link, "traceState"); zero(link, "droppedAttributesCount");
            require(integer(link, "flags") == 769);
            linked = id(link, "traceId", 32) + "-" + id(link, "spanId", 16);
        }
        return new ObservedSpan(name, id(node, "traceId", 32), id(node, "spanId", 16), parent, linked, start);
    }

    private static ObservedSpan findLinked(Map<String, ObservedSpan> spans, String name, String target) {
        var matches = spans.values().stream().filter(span -> name.equals(span.name()) && target.equals(span.linked())).toList();
        require(matches.size() == 1);
        return matches.getFirst();
    }

    private static Instant instant(JsonNode node) {
        require(node.isString() && node.stringValue().matches("[1-9][0-9]{0,18}"));
        long nanos = Long.parseLong(node.stringValue());
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    private static String id(JsonNode node, String field, int length) {
        String value = node.path(field).stringValue();
        require(value != null && value.matches("[0-9a-f]{" + length + "}") && !value.equals("0".repeat(length)));
        return value;
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        require(node.isObject() && allowed.containsAll(node.propertyNames()));
    }

    private static JsonNode array(JsonNode node, int max) {
        require(node.isArray() && !node.isEmpty() && node.size() <= max);
        return node;
    }

    private static void empty(JsonNode node, String field) {
        require(!node.has(field) || node.path(field).isArray() && node.path(field).isEmpty());
    }

    private static void emptyText(JsonNode node, String field) {
        require(!node.has(field) || "".equals(node.path(field).stringValue()));
    }

    private static int integer(JsonNode node, String field) {
        var value = node.path(field);
        require(value.isIntegralNumber() && value.canConvertToInt());
        return value.intValue();
    }

    private static void zero(JsonNode node, String field) {
        require(!node.has(field) || node.path(field).isIntegralNumber() && node.path(field).intValue() == 0);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("S12 receive-link fixture evidence is invalid");
    }

    private record ObservedSpan(String name, String traceId, String spanId, String parentId, String linked, Instant startedAt) {
        String key() { return traceId + "-" + spanId; }
    }
}
