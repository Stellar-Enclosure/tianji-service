package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class OwnerObservationResponseCodec {
    static final int MAX_BYTES = 262_144;
    private static final int AGENT_PAGE_SIZE = 20;
    private static final String INVALID_RESPONSE = "TIANJI_INVALID_RESPONSE";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final Set<String> RUN_PAGE_FIELDS = Set.of("items", "nextCursor");
    private static final Set<String> RUN_FIELDS = Set.of("runId", "definitionKey", "definitionVersion", "sessionId",
            "status", "waitReason", "cancelRequested", "attempt", "revision", "lastEventSequence", "deadlineAt",
            "createdAt", "finishedAt");
    private static final Set<String> PROCESS_PAGE_FIELDS = Set.of("correlationId", "observedAt", "items", "nextCursor");
    private static final Set<String> RECEIPT_FIELDS = Set.of("instanceId", "releaseVersion", "submittedAt");
    private static final Set<String> OUTCOME_PAGE_FIELDS = Set.of("instanceId", "observedAt", "items", "nextCursor");
    private static final Set<String> OUTCOME_FIELDS = Set.of("activityId", "dispatchState", "inboxState",
            "terminalStatus", "dispatchRecordedAt", "receivedAt", "decidedAt");
    private static final Set<String> AGENT_ERROR_FIELDS = Set.of("code", "correlationId", "message", "decisionId", "retryAfter");
    private static final Set<String> PROCESS_ERROR_FIELDS = Set.of("code", "correlationId");
    private static final Map<Integer, String> ERROR_CODES = Map.of(
            400, "TIANJI_INVALID_COMMAND", 401, "TIANJI_UNAUTHENTICATED", 403, "TIANJI_PERMISSION_DENIED",
            409, "TIANJI_TENANT_CONTEXT_CHANGED", 503, "TIANJI_DEPENDENCY_UNAVAILABLE");
    private static final Pattern DATE_TIME = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})");
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(65_536)
                            .maxNameLength(64).maxNumberLength(32).maxTokenCount(1024).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    enum Route { AGENT_RUNS, PROCESS_INSTANCES, PROCESS_AGENT_OUTCOMES }

    private OwnerObservationResponseCodec() { }

    static void requireSecureBase(URI base) {
        if (!base.normalize().equals(base) || !"https".equalsIgnoreCase(base.getScheme())
                && !("http".equalsIgnoreCase(base.getScheme()) && LOOPBACK_HOSTS.contains(base.getHost().toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("Owner observation transport requires HTTPS or a loopback fixture");
        }
    }

    static String windowQuery(String correlation, Instant from, Instant to, String cursor) {
        return "?correlationId=" + queryValue(correlation) + "&fromInclusive=" + queryValue(from.toString())
                + "&toExclusive=" + queryValue(to.toString()) + (cursor == null ? "" : "&cursor=" + cursor);
    }

    private static String queryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String failureCode(HttpResponse<byte[]> response, Route route) {
        var contentTypes = response.headers().allValues("Content-Type");
        if (contentTypes.size() != 1 || !"application/json".equalsIgnoreCase(contentTypes.getFirst().split(";", 2)[0].trim())
                || !List.of("no-store").equals(response.headers().allValues("Cache-Control"))) {
            return INVALID_RESPONSE;
        }
        int status = response.statusCode();
        if (status == 200) return null;
        String expected = status == 404 && route == Route.PROCESS_AGENT_OUTCOMES
                ? "TIANJI_RESOURCE_NOT_FOUND" : ERROR_CODES.get(status);
        if (expected == null) return INVALID_RESPONSE;
        try {
            var root = read(response.body());
            if (route == Route.AGENT_RUNS) {
                if (!root.isObject() || !AGENT_ERROR_FIELDS.containsAll(root.propertyNames())) return INVALID_RESPONSE;
                for (String field : List.of("correlationId", "decisionId", "retryAfter")) {
                    if (root.has(field)) optionalText(root, field);
                }
                if (root.has("message")) text(root, "message");
            } else {
                shape(root, PROCESS_ERROR_FIELDS);
                if (!root.path("correlationId").isNull()) return INVALID_RESPONSE;
            }
            return expected.equals(text(root, "code")) ? expected : INVALID_RESPONSE;
        } catch (RuntimeException malformed) {
            return INVALID_RESPONSE;
        }
    }

    static AgentRunPageDTO agentRuns(byte[] body, ListAgentRunsByCorrelationQuery query) {
        var root = read(body);
        shape(root, RUN_PAGE_FIELDS);
        var rows = rows(root, AGENT_PAGE_SIZE);
        String cursor = optionalText(root, "nextCursor");
        if (cursor != null && (cursor.length() > 256 || !cursor.matches("v1\\.[A-Za-z0-9_-]+")
                || rows.size() != AGENT_PAGE_SIZE || cursor.equals(query.cursor()))) throw invalid();

        var items = new ArrayList<AgentRunDTO>(rows.size());
        var seen = new HashSet<String>();
        AgentRunDTO previous = null;
        for (var row : rows) {
            shape(row, RUN_FIELDS);
            String id = text(row, "runId");
            var created = ListAgentRunsByCorrelationQuery.parseUtc(text(row, "createdAt"));
            if (!id.matches("[A-Za-z0-9_-]{1,64}") || !seen.add(id)
                    || outsideWindow(created, query.fromInclusive(), query.toExclusive())
                    || previous != null && !follows(created, id, previous.createdAt(), previous.runId())) throw invalid();

            var flag = row.path("cancelRequested");
            if (!flag.isBoolean()) throw invalid();
            String definition = text(row, "definitionKey");
            String session = optionalText(row, "sessionId");
            if (!definition.equals(definition.trim()) || session != null && !session.equals(session.trim())) throw invalid();
            var run = new AgentRunDTO(id, definition, number(row, "definitionVersion"), session,
                    AgentRunStatusEnum.valueOf(text(row, "status")), optionalText(row, "waitReason"), flag.booleanValue(),
                    Math.toIntExact(number(row, "attempt")), number(row, "revision"), number(row, "lastEventSequence"),
                    optionalInstant(row, "deadlineAt"), created, optionalInstant(row, "finishedAt"));
            items.add(run);
            previous = run;
        }
        return new AgentRunPageDTO(items, cursor);
    }

    static ProcessInstanceCorrelationPageDTO processInstances(byte[] body, ListProcessInstancesByCorrelationQuery query) {
        var root = read(body);
        shape(root, PROCESS_PAGE_FIELDS);
        String correlation = text(root, "correlationId");
        var observed = ListProcessInstancesByCorrelationQuery.parseUtc(text(root, "observedAt"));
        String cursor = optionalText(root, "nextCursor");
        if (!query.correlationId().equals(correlation) || observed.isBefore(query.toExclusive())
                || cursor != null && cursor.equals(query.cursor())) throw invalid();

        var rows = rows(root, ProcessInstanceCorrelationPageDTO.PAGE_SIZE);
        var items = new ArrayList<ProcessInstanceCorrelationPageDTO.ReceiptDTO>(rows.size());
        ProcessInstanceCorrelationPageDTO.ReceiptDTO previous = null;
        for (var row : rows) {
            shape(row, RECEIPT_FIELDS);
            var item = new ProcessInstanceCorrelationPageDTO.ReceiptDTO(text(row, "instanceId"), text(row, "releaseVersion"),
                    ListProcessInstancesByCorrelationQuery.parseUtc(text(row, "submittedAt")));
            if (outsideWindow(item.submittedAt(), query.fromInclusive(), query.toExclusive())
                    || previous != null && !follows(item.submittedAt(), item.instanceId(),
                            previous.submittedAt(), previous.instanceId())) throw invalid();
            items.add(item);
            previous = item;
        }
        return new ProcessInstanceCorrelationPageDTO(correlation, observed, items, cursor);
    }

    static ProcessAgentOutcomePageDTO agentOutcomes(byte[] body, GetProcessAgentOutcomesQuery query) {
        var root = read(body);
        shape(root, OUTCOME_PAGE_FIELDS);
        String instance = text(root, "instanceId");
        String cursor = optionalText(root, "nextCursor");
        if (!query.instanceId().equals(instance) || cursor != null && cursor.equals(query.cursor())) throw invalid();

        var rows = rows(root, ProcessAgentOutcomePageDTO.PAGE_SIZE);
        var items = new ArrayList<ProcessAgentOutcomePageDTO.ItemDTO>(rows.size());
        for (var row : rows) {
            shape(row, OUTCOME_FIELDS);
            String terminal = optionalText(row, "terminalStatus");
            items.add(new ProcessAgentOutcomePageDTO.ItemDTO(text(row, "activityId"),
                    ProcessAgentOutcomePageDTO.DispatchState.valueOf(text(row, "dispatchState")),
                    ProcessAgentOutcomePageDTO.InboxState.valueOf(text(row, "inboxState")),
                    terminal == null ? null : AgentRunStatusEnum.valueOf(terminal), instant(text(row, "dispatchRecordedAt")),
                    optionalInstant(row, "receivedAt"), optionalInstant(row, "decidedAt")));
        }
        return new ProcessAgentOutcomePageDTO(instance, instant(text(root, "observedAt")), items, cursor);
    }

    private static boolean outsideWindow(Instant value, Instant from, Instant to) {
        return value.isBefore(from) || !value.isBefore(to);
    }

    private static boolean follows(Instant time, String id, Instant previousTime, String previousId) {
        // PostgreSQL COLLATE "C" compares UTF-8 bytes, including for non-ASCII Process instance IDs.
        return time.isBefore(previousTime) || time.equals(previousTime)
                && Arrays.compareUnsigned(id.getBytes(StandardCharsets.UTF_8), previousId.getBytes(StandardCharsets.UTF_8)) < 0;
    }

    private static JsonNode read(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_BYTES) throw invalid();
        try {
            return MAPPER.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static void shape(JsonNode object, Set<String> fields) {
        if (object == null || !object.isObject() || !fields.equals(Set.copyOf(object.propertyNames()))) throw invalid();
    }

    private static JsonNode rows(JsonNode root, int maximum) {
        var rows = root.path("items");
        if (!rows.isArray() || rows.size() > maximum) throw invalid();
        return rows;
    }

    private static String text(JsonNode object, String name) {
        var value = object.path(name);
        if (!value.isString()) throw invalid();
        String text = value.asString();
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw invalid();
        return text;
    }

    private static String optionalText(JsonNode object, String name) {
        return object.path(name).isNull() ? null : text(object, name);
    }

    private static long number(JsonNode object, String name) {
        var value = object.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        return value.longValue();
    }

    private static Instant instant(String value) {
        if (!DATE_TIME.matcher(value).matches() || value.startsWith("0000")) throw invalid();
        return OffsetDateTime.parse(value).toInstant();
    }

    private static Instant optionalInstant(JsonNode object, String name) {
        String value = optionalText(object, name);
        return value == null ? null : instant(value);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Owner observation response is invalid");
    }
}
