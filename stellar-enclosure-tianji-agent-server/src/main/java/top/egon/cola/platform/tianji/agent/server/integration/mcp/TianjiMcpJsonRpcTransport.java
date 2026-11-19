package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Fixed JSON protocol adapter; the enclosing verified scope owns credentials and submission. */
final class TianjiMcpJsonRpcTransport implements McpClientTransport {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_DEPTH)
                    .maxStringLength(TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES)
                    .maxNameLength(TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES).maxNumberLength(512)
                    .maxTokenCount(TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NODES * 3L).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Set<String> ENVELOPE_FIELDS = Set.of("jsonrpc", "id", "result", "error");
    private static final Set<String> SCHEMA_FIELDS = Set.of("type", "properties", "required", "additionalProperties", "title", "description", "$schema");
    private static final Set<String> LEAF_FIELDS = Set.of("type", "title", "description");
    private static final Set<String> SCHEMA_DIALECTS = Set.of("https://json-schema.org/draft/2020-12/schema", "http://json-schema.org/draft-07/schema#");
    private final TianjiFixedReadOnlyMcpToolBindings.BackendVO backend;
    private final Exchange exchange;
    private final Object lock = new Object();
    private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler;
    private int phase;
    private boolean busy;
    private boolean failed;
    private boolean closed;
    private boolean deleteAttempted;
    private String sessionId;

    TianjiMcpJsonRpcTransport(TianjiFixedReadOnlyMcpToolBindings.BackendVO backend, Exchange exchange) {
        if (backend == null || exchange == null) throw failure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE);
        this.backend = backend; this.exchange = exchange;
    }

    @Override public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return Mono.fromRunnable(() -> {
            synchronized (lock) {
                if (handler == null || this.handler != null || closed) throw failure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE);
                this.handler = handler;
            }
        });
    }
    @Override public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.defer(() -> {
            PreparedVO prepared;
            try {
                synchronized (lock) {
                    if (closed) throw failure(ReadOnlyMcpToolTransport.FailureKind.CANCELLED);
                    if (handler == null || busy || failed) throw failure(notSent());
                    prepared = prepare(message); busy = true; phase++;
                }
            } catch (Throwable uncertain) { return Mono.error(safe(uncertain, notSent())); }
            CompletableFuture<HttpResponse<byte[]>> pending;
            try { pending = Objects.requireNonNull(exchange.exchange(prepared.request(), prepared.toolsCall())); }
            catch (Throwable uncertain) { release(true); return Mono.error(safe(uncertain, ioFailure(prepared))); }
            return Mono.fromFuture(pending).switchIfEmpty(Mono.error(failure(ioFailure(prepared)))).flatMap(response -> deliver(prepared, response))
                    .onErrorMap(uncertain -> { release(true); return safe(uncertain, ioFailure(prepared)); })
                    .doOnCancel(() -> release(true));
        });
    }
    @Override public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        if (!(data instanceof OpaqueResultVO opaque) || typeRef == null || !opaque.type().equals(typeRef.getType())) {
            throw failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
        }
        @SuppressWarnings("unchecked") T result = (T) opaque.value(); return result;
    }
    @Override public List<String> protocolVersions() { return List.of(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION); }
    @Override public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> { synchronized (lock) { closed = true; handler = null; } });
    }
    Mono<Void> deleteOwnedSession() {
        return Mono.defer(() -> {
            HttpRequest request;
            synchronized (lock) {
                if (closed || deleteAttempted || sessionId == null) return Mono.empty();
                if (busy) return Mono.error(failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN));
                deleteAttempted = true; busy = true; request = headers(HttpRequest.newBuilder(backend.endpoint()), true).DELETE().build();
            }
            try {
                return Mono.fromFuture(Objects.requireNonNull(exchange.exchange(request, false)))
                        .switchIfEmpty(Mono.error(failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN))).flatMap(response -> {
                    if (response == null || response.statusCode() != 204 || response.body() == null || response.body().length != 0) {
                        return Mono.<Void>error(failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN));
                    }
                    return Mono.<Void>empty();
                }).onErrorMap(uncertain -> safe(uncertain, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN)).doFinally(signal -> release(false));
            } catch (Throwable uncertain) { release(false); return Mono.error(safe(uncertain, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN)); }
        });
    }

    private PreparedVO prepare(McpSchema.JSONRPCMessage message) {
        String method; Object id; Object params;
        if (message instanceof McpSchema.JSONRPCRequest request && "2.0".equals(request.jsonrpc()) && validId(request.id())) {
            method = request.method(); id = request.id(); params = request.params();
        } else if (message instanceof McpSchema.JSONRPCNotification notification && "2.0".equals(notification.jsonrpc())) {
            method = notification.method(); id = null; params = notification.params();
        } else throw failure(notSent());
        String expected = switch (phase) { case 0 -> "initialize"; case 1 -> "notifications/initialized"; case 2 -> "tools/list"; case 3 -> "tools/call"; default -> ""; };
        if (!expected.equals(method) || (phase == 1) != (id == null)) throw failure(notSent());
        Map<String, Object> wireParams;
        if (phase == 0) {
            if (!(params instanceof McpSchema.InitializeRequest initialize) || !TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION.equals(initialize.protocolVersion())
                    || initialize.meta() != null || initialize.capabilities() == null || initialize.clientInfo() == null
                    || initialize.capabilities().roots() != null || initialize.capabilities().sampling() != null || initialize.capabilities().elicitation() != null
                    || initialize.capabilities().experimental() != null && !initialize.capabilities().experimental().isEmpty()
                    || !"tianji-readonly-mcp".equals(initialize.clientInfo().name()) || !"1.0".equals(initialize.clientInfo().version())) throw failure(notSent());
            wireParams = Map.of("protocolVersion", TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION, "capabilities", Map.of(), "clientInfo", Map.of("name", "tianji-readonly-mcp", "version", "1.0"));
        } else if (phase == 1) {
            if (params != null && (!(params instanceof Map<?, ?> values) || !values.isEmpty())) throw failure(notSent());
            wireParams = null;
        } else if (phase == 2) {
            if (params instanceof McpSchema.PaginatedRequest page) {
                if (page.meta() != null || page.cursor() != null && !page.cursor().isEmpty()) throw failure(notSent());
            } else if (params != null && (!(params instanceof Map<?, ?> values) || !values.isEmpty())) throw failure(notSent());
            wireParams = Map.of();
        } else {
            if (!(params instanceof McpSchema.CallToolRequest call) || !backend.remoteToolName().equals(call.name()) || call.meta() != null) throw failure(notSent());
            wireParams = Map.of("name", backend.remoteToolName(), "arguments", backend.inputSchema().values(call.arguments()));
        }
        var document = new LinkedHashMap<String, Object>(); document.put("jsonrpc", "2.0"); document.put("method", method);
        if (id != null) document.put("id", id); if (wireParams != null) document.put("params", wireParams);
        var builder = headers(HttpRequest.newBuilder(backend.endpoint()), phase != 0).header("Content-Type", "application/json;charset=UTF-8");
        return new PreparedVO(method, id, builder.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(document))).build(), phase == 3);
    }

    private HttpRequest.Builder headers(HttpRequest.Builder builder, boolean session) {
        builder.header("Accept", "application/json,text/event-stream").header("Mcp-Protocol-Version", TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION);
        if (session) { if (sessionId == null) throw failure(notSent()); builder.header("Mcp-Session-Id", sessionId); }
        return builder;
    }

    private Mono<Void> deliver(PreparedVO prepared, HttpResponse<byte[]> response) {
        ReadOnlyMcpToolTransport.FailureKind invalid = prepared.toolsCall() ? ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE : ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE;
        if (response == null || response.body() == null) return Mono.error(failure(invalid));
        if ("notifications/initialized".equals(prepared.method())) {
            if (response.statusCode() != 202 || response.body().length != 0) return Mono.error(failure(invalid));
            release(false); return Mono.empty();
        }
        if (response.statusCode() != 200) {
            if (prepared.toolsCall() && (response.statusCode() == 401 || response.statusCode() == 403)) return Mono.error(failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_DENIED));
            return Mono.error(failure(prepared.toolsCall() ? ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN : ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE));
        }
        try {
            requireJson(response, invalid);
            if (response.body().length > TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES) throw failure(invalid);
            Object parsed = JSON.readValue(response.body(), Object.class); bounded(parsed, 1, new int[]{0, 0}, invalid);
            var envelope = object(parsed, invalid);
            if (!ENVELOPE_FIELDS.containsAll(envelope.keySet()) || !"2.0".equals(envelope.get("jsonrpc")) || !matchesId(prepared.id(), envelope.get("id"))
                    || envelope.containsKey("result") == envelope.containsKey("error")) throw failure(invalid);
            McpSchema.JSONRPCResponse safeResponse;
            if (envelope.containsKey("error")) {
                var error = object(envelope.get("error"), invalid);
                if (!Set.of("code", "message", "data").containsAll(error.keySet()) || !(error.get("code") instanceof BigInteger code) || code.bitLength() > 31) throw failure(invalid);
                safeResponse = McpSchema.JSONRPCResponse.error(prepared.id(), new McpSchema.JSONRPCResponse.JSONRPCError(code.intValueExact(), "TIANJI_MCP_REMOTE_ERROR", null));
            } else {
                var result = object(envelope.get("result"), invalid);
                OpaqueResultVO opaque = switch (prepared.method()) {
                    case "initialize" -> initializeResult(result, response);
                    case "tools/list" -> listResult(result);
                    case "tools/call" -> callResult(result);
                    default -> throw failure(invalid);
                };
                safeResponse = McpSchema.JSONRPCResponse.result(prepared.id(), opaque);
            }
            Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> consumer;
            synchronized (lock) {
                if (closed) throw failure(ReadOnlyMcpToolTransport.FailureKind.CANCELLED);
                busy = false; consumer = handler;
            }
            return consumer.apply(Mono.just(safeResponse)).then();
        } catch (Throwable uncertain) { return Mono.error(safe(uncertain, invalid)); }
    }

    private OpaqueResultVO initializeResult(Map<String, Object> result, HttpResponse<byte[]> response) {
        var changed = ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED;
        if (!Set.of("protocolVersion", "capabilities", "serverInfo", "instructions", "_meta").containsAll(result.keySet())
                || !TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION.equals(result.get("protocolVersion"))) throw failure(changed);
        var server = object(result.get("serverInfo"), changed); var capabilities = object(result.get("capabilities"), changed);
        if (!backend.expectedServerName().equals(server.get("name")) || !backend.expectedServerVersion().equals(server.get("version"))) throw failure(changed);
        var publication = backend.publication();
        if (publication != null && publication.catalogueFileSha256() != null) {
            var metadata = object(result.get("_meta"), changed);
            if (!publication.catalogueFileSha256().equals(metadata.get("top.egon/selectedMcpCatalogueSha256"))) {
                throw failure(changed);
            }
        }
        object(capabilities.get("tools"), changed);
        var sessions = response.headers().allValues("Mcp-Session-Id");
        if (sessions.size() != 1) throw failure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE);
        String captured = sessions.getFirst();
        try { if (!UUID.fromString(captured).toString().equals(captured)) throw failure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE); }
        catch (RuntimeException malformed) { throw failure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE); }
        synchronized (lock) { if (closed || sessionId != null) throw failure(ReadOnlyMcpToolTransport.FailureKind.CANCELLED); sessionId = captured; }
        var tools = new McpSchema.ServerCapabilities.ToolCapabilities(null);
        var safeCapabilities = new McpSchema.ServerCapabilities(null, null, null, null, null, tools);
        return new OpaqueResultVO(McpSchema.InitializeResult.class, new McpSchema.InitializeResult(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION,
                safeCapabilities, McpSchema.Implementation.builder(backend.expectedServerName(), backend.expectedServerVersion()).build(), null, null));
    }

    private OpaqueResultVO listResult(Map<String, Object> result) {
        var changed = ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED;
        if (!Set.of("tools", "nextCursor", "_meta").containsAll(result.keySet()) || !(result.get("tools") instanceof List<?> tools)
                || result.get("nextCursor") != null && !"".equals(result.get("nextCursor"))) throw failure(changed);
        Map<String, Object> selected = null;
        for (var value : tools) {
            var document = object(value, changed);
            if (backend.remoteToolName().equals(document.get("name"))) { if (selected != null) throw failure(changed); selected = document; }
        }
        if (selected == null) throw failure(changed);
        String fingerprint;
        try { fingerprint = TianjiFixedReadOnlyMcpToolBindings.remoteToolDocumentSha256(selected); }
        catch (RuntimeException malformed) { throw failure(changed); }
        if (!backend.remoteToolDocumentSha256().equals(fingerprint)) throw failure(changed);
        var input = schema(selected.get("inputSchema"), backend.inputSchema());
        var output = selected.containsKey("outputSchema") ? schema(selected.get("outputSchema"), backend.outputSchema()) : null;
        var tool = new McpSchema.Tool(backend.remoteToolName(), null, null, input, output, null, null, null);
        return new OpaqueResultVO(McpSchema.ListToolsResult.class, new McpSchema.ListToolsResult(List.of(tool), null, null));
    }

    static Map<String, Object> schema(Object supplied, ToolSchema expected) {
        var changed = ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED; var schema = object(supplied, changed);
        if (!SCHEMA_FIELDS.containsAll(schema.keySet()) || !"object".equals(schema.get("type"))
                || schema.containsKey("additionalProperties") && !(schema.get("additionalProperties") instanceof Boolean)
                || schema.containsKey("$schema") && !SCHEMA_DIALECTS.contains(schema.get("$schema"))) throw failure(changed);
        var properties = schema.containsKey("properties") ? object(schema.get("properties"), changed) : Map.<String, Object>of();
        if (!properties.keySet().equals(expected.fields().keySet())) throw failure(changed);
        var required = new HashSet<String>();
        if (schema.containsKey("required")) {
            if (!(schema.get("required") instanceof List<?> values)) throw failure(changed);
            for (var value : values) if (!(value instanceof String field) || !required.add(field)) throw failure(changed);
        }
        if (!required.equals(expected.required())) throw failure(changed);
        var projected = new TreeMap<String, Object>();
        for (var entry : expected.fields().entrySet()) {
            var leaf = object(properties.get(entry.getKey()), changed); String type = entry.getValue().name().toLowerCase(Locale.ROOT);
            if (!LEAF_FIELDS.containsAll(leaf.keySet()) || !type.equals(leaf.get("type"))) throw failure(changed);
            projected.put(entry.getKey(), Map.of("type", type));
        }
        return Map.of("type", "object", "properties", Map.copyOf(projected), "required", required.stream().sorted().toList(), "additionalProperties", false);
    }

    private OpaqueResultVO callResult(Map<String, Object> result) {
        var invalid = ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE;
        if (!Set.of("content", "isError", "structuredContent", "_meta").containsAll(result.keySet()) || !(result.get("isError") instanceof Boolean isError)
                || result.get("_meta") != null && !(result.get("_meta") instanceof Map<?, ?>)) throw failure(invalid);
        if (result.containsKey("content")) {
            if (!(result.get("content") instanceof List<?> content)) throw failure(invalid);
            for (var item : content) object(item, invalid);
        }
        Object structured = result.get("structuredContent");
        if (isError) return new OpaqueResultVO(McpSchema.CallToolResult.class, new McpSchema.CallToolResult(List.of(), true, null, null));
        var values = object(structured, invalid); var fields = new HashMap<String, ToolSchema.ValueType>();
        for (var entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) fields.put(entry.getKey(), ToolSchema.ValueType.STRING);
            else if (value instanceof Boolean) fields.put(entry.getKey(), ToolSchema.ValueType.BOOLEAN);
            else if (value instanceof BigInteger) fields.put(entry.getKey(), ToolSchema.ValueType.INTEGER);
            else throw failure(invalid);
        }
        var bounded = new ToolSchema(fields, fields.keySet()).values(values);
        return new OpaqueResultVO(McpSchema.CallToolResult.class, new McpSchema.CallToolResult(List.of(), false, bounded, null));
    }

    private static void requireJson(HttpResponse<byte[]> response, ReadOnlyMcpToolTransport.FailureKind invalid) {
        var values = response.headers().allValues("Content-Type"); if (values.size() != 1) throw failure(invalid);
        String[] parts = values.getFirst().split(";", -1);
        if (!"application/json".equalsIgnoreCase(parts[0].trim()) || parts.length > 2) throw failure(invalid);
        if (parts.length == 2 && !parts[1].trim().matches("(?i)charset\\s*=\\s*(UTF-8|\"UTF-8\")")) throw failure(invalid);
    }
    private static boolean validId(Object id) { return id instanceof String text && !text.isEmpty() && text.length() <= 128 || id instanceof Integer || id instanceof Long; }
    private static boolean matchesId(Object sent, Object received) {
        if (sent instanceof String) return sent.equals(received);
        return (sent instanceof Integer || sent instanceof Long) && received instanceof BigInteger integer && integer.equals(BigInteger.valueOf(((Number) sent).longValue()));
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value, ReadOnlyMcpToolTransport.FailureKind invalid) {
        if (!(value instanceof Map<?, ?> object) || object.keySet().stream().anyMatch(key -> !(key instanceof String))) throw failure(invalid);
        return (Map<String, Object>) object;
    }
    private static void bounded(Object value, int depth, int[] counts, ReadOnlyMcpToolTransport.FailureKind invalid) {
        if (depth > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_DEPTH || ++counts[0] > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NODES) throw failure(invalid);
        if (value == null || value instanceof Boolean) return;
        if (value instanceof String text) { boundedText(text, counts, invalid); return; }
        if (value instanceof Map<?, ?> object) {
            if (object.size() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_CONTAINER_SIZE) throw failure(invalid);
            for (var entry : object.entrySet()) {
                if (!(entry.getKey() instanceof String key) || ++counts[0] > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NODES) throw failure(invalid);
                boundedText(key, counts, invalid); bounded(entry.getValue(), depth + 1, counts, invalid);
            }
            return;
        }
        if (value instanceof List<?> array) {
            if (array.size() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_CONTAINER_SIZE) throw failure(invalid);
            for (var item : array) bounded(item, depth + 1, counts, invalid); return;
        }
        BigDecimal number;
        if (value instanceof BigInteger integer) { if (integer.abs().bitLength() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NUMBER_BITS) throw failure(invalid); number = new BigDecimal(integer); }
        else if (value instanceof BigDecimal decimal) { if (decimal.unscaledValue().abs().bitLength() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NUMBER_BITS) throw failure(invalid); number = decimal; }
        else throw failure(invalid);
        number = number.stripTrailingZeros();
        if (number.precision() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NUMBER_PRECISION || number.scale() < -TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NUMBER_SCALE
                || number.scale() > TianjiFixedReadOnlyMcpToolBindings.MAX_JSON_NUMBER_SCALE) throw failure(invalid);
    }
    private static void boundedText(String text, int[] counts, ReadOnlyMcpToolTransport.FailureKind invalid) {
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw failure(invalid);
        int size = text.getBytes(StandardCharsets.UTF_8).length;
        if (size > TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES - counts[1]) throw failure(invalid); counts[1] += size;
    }
    private void release(boolean failed) { synchronized (lock) { busy = false; this.failed |= failed; } }
    private ReadOnlyMcpToolTransport.FailureKind notSent() { return phase < 3 ? ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE : ReadOnlyMcpToolTransport.FailureKind.OPERATION_NOT_SENT; }
    private static ReadOnlyMcpToolTransport.FailureKind ioFailure(PreparedVO prepared) {
        return prepared.toolsCall() ? ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN : ReadOnlyMcpToolTransport.FailureKind.OPERATION_NOT_SENT;
    }
    private static ReadOnlyMcpToolTransport.Failure safe(Throwable uncertain, ReadOnlyMcpToolTransport.FailureKind fallback) {
        return failure(uncertain instanceof ReadOnlyMcpToolTransport.Failure fixed ? fixed.kind() : fallback);
    }
    private static ReadOnlyMcpToolTransport.Failure failure(ReadOnlyMcpToolTransport.FailureKind kind) { return new ReadOnlyMcpToolTransport.Failure(kind); }
    private record PreparedVO(String method, Object id, HttpRequest request, boolean toolsCall) {
        @Override public String toString() { return "TianjiMcpPreparedVO[redacted]"; }
    }
    private record OpaqueResultVO(Class<?> type, Object value) {
        private OpaqueResultVO {
            if (type != McpSchema.InitializeResult.class && type != McpSchema.ListToolsResult.class && type != McpSchema.CallToolResult.class
                    || value == null || !type.isInstance(value)) throw failure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
        }
        @Override public String toString() { return "TianjiMcpResultVO[redacted]"; }
    }

    @FunctionalInterface interface Exchange {
        CompletableFuture<HttpResponse<byte[]>> exchange(HttpRequest requestWithoutAuthorization, boolean toolsCall);
    }
}
