package top.egon.cola.platform.tianji.process.server.acceptance;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class S12MetricsCollectorEvidence {
    static final String METRICS_FILE = "s12-metrics.jsonl";
    private static final int MAX_BYTES = 8_192;
    private static final Map<String, String> METERS = Map.of(
            "tianji-s12-agent", "tianji.agent.run.initial.claim.delay",
            "tianji-s12-process", "tianji.process.agent.result.inbox.decision.delay");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private S12MetricsCollectorEvidence() { }

    static byte[] readAvailable(Path file) throws IOException {
        try {
            return S12ReceiveLinkCollectorEvidence.readPrivate(file);
        } catch (NoSuchFileException absent) {
            if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) throw absent;
            return new byte[0];
        }
    }

    static boolean verified(byte[] exported, Instant earliest, Instant latest) {
        require(exported != null && exported.length <= MAX_BYTES && earliest != null && latest != null && !latest.isBefore(earliest));
        int complete = exported.length;
        while (complete > 0 && exported[complete - 1] != '\n') complete--;
        if (complete == 0) return false;
        String body;
        try {
            body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(exported, 0, complete - 1)).toString();
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }

        var seen = new HashSet<String>();
        for (String line : body.split("\n", -1)) {
            try {
                String service = verifyRecord(JSON.readTree(line), earliest, latest);
                require(seen.add(service));
            } catch (RuntimeException malformed) {
                throw invalid();
            }
        }
        return complete == exported.length && seen.equals(METERS.keySet());
    }

    private static String verifyRecord(JsonNode record, Instant earliest, Instant latest) {
        fields(record, "resourceMetrics");
        var envelope = only(record.path("resourceMetrics"));
        fields(envelope, "resource", "scopeMetrics");
        var resource = envelope.path("resource");
        fields(resource, "attributes");
        String service = attribute(only(resource.path("attributes")), "service.name");
        require(METERS.containsKey(service));

        var scopeMetrics = only(envelope.path("scopeMetrics"));
        fields(scopeMetrics, "scope", "metrics");
        var scope = scopeMetrics.path("scope");
        fields(scope, "name");
        require("top.egon.cola.tianji.s12.metrics".equals(scope.path("name").asString()));

        var meter = only(scopeMetrics.path("metrics"));
        fields(meter, "name", "unit", "histogram");
        require(METERS.get(service).equals(meter.path("name").asString()) && "s".equals(meter.path("unit").asString()));
        var histogram = meter.path("histogram");
        fields(histogram, "aggregationTemporality", "dataPoints");
        var temporality = histogram.path("aggregationTemporality");
        require(temporality.isInt() && temporality.asInt() == 2);
        var point = only(histogram.path("dataPoints"));
        // The pinned exporter may omit the empty Agent attributes array.
        if (point.has("attributes")) {
            fields(point, "attributes", "startTimeUnixNano", "timeUnixNano", "count", "sum", "bucketCounts");
        } else {
            fields(point, "startTimeUnixNano", "timeUnixNano", "count", "sum", "bucketCounts");
        }
        var tags = point.path("attributes");
        if ("tianji-s12-agent".equals(service)) {
            require(tags.isMissingNode() || tags.isArray() && tags.isEmpty());
        } else {
            require("applied".equals(attribute(only(tags), "outcome")));
        }
        var started = timestamp(point.path("startTimeUnixNano"));
        var sampled = timestamp(point.path("timeUnixNano"));
        require(!started.isBefore(earliest) && sampled.isAfter(started) && !sampled.isAfter(latest));
        var sum = point.path("sum");
        require(unsigned(point.path("count")) == 2 && unsigned(only(point.path("bucketCounts"))) == 2
                && sum.isNumber() && Double.isFinite(sum.asDouble()) && sum.asDouble() >= 0);
        return service;
    }

    private static void fields(JsonNode object, String... names) {
        require(object != null && object.isObject() && object.size() == names.length);
        require(object.properties().stream().allMatch(field -> Set.of(names).contains(field.getKey())));
    }

    private static JsonNode only(JsonNode array) {
        require(array.isArray() && array.size() == 1);
        return array.get(0);
    }

    private static String attribute(JsonNode node, String key) {
        fields(node, "key", "value");
        require(key.equals(node.path("key").asString()));
        var value = node.path("value");
        fields(value, "stringValue");
        require(value.path("stringValue").isString());
        return value.path("stringValue").asString();
    }

    private static long unsigned(JsonNode value) {
        require(value.isString() && value.asString().matches("0|[1-9][0-9]{0,18}"));
        return Long.parseLong(value.asString());
    }

    private static Instant timestamp(JsonNode value) {
        long nanos = unsigned(value);
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    private static void require(boolean condition) {
        if (!condition) throw invalid();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("S12 metrics Collector evidence is invalid");
    }
}
