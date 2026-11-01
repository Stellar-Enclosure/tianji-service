package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TianjiMcpJsonRpcTransportTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI ENDPOINT = URI.create("https://mcp.example/mcp");
    private static final String SESSION = "20000000-0000-0000-0000-000000000001";
    private static final String PRIVATE = "PRIVATE_MCP_BODY_SENTINEL";
    private static final String BEARER = "PRIVATE_MCP_BEARER_SENTINEL";
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount"));
    private static final ToolSchema OUTPUT = new ToolSchema(Map.of("eligible", ToolSchema.ValueType.BOOLEAN), Set.of("eligible"));

    @Test void native_client_owns_initialize_notification_discovery_call_and_ids_with_private_projections_and_owned_delete() {
        var exchange = new FakeExchange();
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange);
        McpSyncClient client = client(transport);
        try {
            var initialized = client.initialize();
            assertEquals(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION, initialized.protocolVersion());
            assertEquals("yuheng", initialized.serverInfo().name());
            assertEquals("1.0", initialized.serverInfo().version());
            assertNull(initialized.instructions()); assertNull(initialized.meta());
            assertNull(initialized.capabilities().resources()); assertNull(initialized.capabilities().prompts());
            assertNotNull(initialized.capabilities().tools());

            var listed = client.listTools((String) null);
            assertNull(listed.nextCursor()); assertNull(listed.meta()); assertEquals(1, listed.tools().size());
            var tool = listed.tools().getFirst();
            assertEquals("catalog.read", tool.name()); assertNull(tool.title()); assertNull(tool.description());
            assertNull(tool.outputSchema()); assertNull(tool.annotations()); assertNull(tool.meta());
            assertEquals(Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")),
                    "required", List.of("amount"), "additionalProperties", false), tool.inputSchema());

            var called = client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L)));
            assertEquals(Boolean.FALSE, called.isError()); assertEquals(Map.of("eligible", true), called.structuredContent());
            assertEquals(List.of(), called.content()); assertNull(called.meta());
            assertEquals(List.of("initialize", "notifications/initialized", "tools/list", "tools/call"), exchange.methods);
            assertEquals(List.of(false, false, false, true), exchange.toolsCalls);
            assertEquals(3, exchange.ids.size()); assertEquals(3, Set.copyOf(exchange.ids).size());

            transport.deleteOwnedSession().block(Duration.ofSeconds(1));
            assertEquals("DELETE", exchange.requests.getLast().method()); assertEquals(5, exchange.requests.size());
            assertTrue(client.closeGracefully()); transport.closeGracefully().block(Duration.ofSeconds(1));
            assertEquals(5, exchange.requests.size());
        } finally { client.closeGracefully(); }
    }

    @ParameterizedTest(name = "call envelope rejects {0}") @MethodSource("invalidEnvelopes")
    void native_pending_request_fails_fast_on_invalid_envelopes_without_dispatch_retry_or_private_cause(String name, UnaryOperator<ReplyVO> mutate) {
        var exchange = new FakeExchange(); exchange.responses = only("tools/call", mutate);
        assertCallFailure(exchange, ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
        assertEquals(4, exchange.requests.size()); assertEquals(1, exchange.methods.stream().filter("initialize"::equals).count());
    }

    private static Stream<Arguments> invalidEnvelopes() {
        return Stream.of(
                Arguments.of("wrong id", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "id", "foreign-" + PRIVATE, "result", Map.of()))),
                Arguments.of("wrong id type", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of()))),
                Arguments.of("missing id", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "result", Map.of()))),
                Arguments.of("wrong version", (UnaryOperator<ReplyVO>) r -> r.body(new String(r.body, StandardCharsets.UTF_8).replace("2.0", "1.0"))),
                Arguments.of("batch", (UnaryOperator<ReplyVO>) r -> r.body("[" + new String(r.body, StandardCharsets.UTF_8) + "]")),
                Arguments.of("scalar root", (UnaryOperator<ReplyVO>) r -> r.json(PRIVATE)),
                Arguments.of("server request", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "id", r.id, "method", "roots/list", "params", Map.of("private", PRIVATE)))),
                Arguments.of("server notification", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "method", "notifications/tools/list_changed", "params", Map.of("private", PRIVATE)))),
                Arguments.of("unknown root field", (UnaryOperator<ReplyVO>) r -> { var envelope = r.envelope(); envelope.put("private", PRIVATE); return r.json(envelope); }),
                Arguments.of("result and error null", (UnaryOperator<ReplyVO>) r -> { var envelope = r.envelope(); envelope.put("error", null); return r.json(envelope); }),
                Arguments.of("error and result null", (UnaryOperator<ReplyVO>) r -> { var envelope = r.envelope(); envelope.put("result", null); envelope.put("error", Map.of("code", -1, "message", PRIVATE)); return r.json(envelope); }),
                Arguments.of("missing result", (UnaryOperator<ReplyVO>) r -> r.json(Map.of("jsonrpc", "2.0", "id", r.id))),
                Arguments.of("null result", (UnaryOperator<ReplyVO>) r -> { var envelope = r.envelope(); envelope.put("result", null); return r.json(envelope); }),
                Arguments.of("duplicate key", (UnaryOperator<ReplyVO>) r -> r.body("{\"jsonrpc\":\"2.0\",\"id\":" + json(r.id) + ",\"result\":{},\"result\":{\"private\":\"" + PRIVATE + "\"}}")),
                Arguments.of("trailing json", (UnaryOperator<ReplyVO>) r -> r.body(new String(r.body, StandardCharsets.UTF_8) + " {\"private\":\"" + PRIVATE + "\"}")),
                Arguments.of("too deep", (UnaryOperator<ReplyVO>) r -> r.result(Map.of("isError", false, "structuredContent", Map.of("private", List.of(List.of(List.of(List.of(List.of(PRIVATE))))))))),
                Arguments.of("oversized body", (UnaryOperator<ReplyVO>) r -> r.body(" ".repeat(TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES) + PRIVATE)),
                Arguments.of("oversized container", (UnaryOperator<ReplyVO>) r -> r.result(Map.of("isError", false, "content", java.util.Collections.nCopies(257, Map.of("type", PRIVATE)), "structuredContent", Map.of("eligible", true)))),
                Arguments.of("non integral error code", (UnaryOperator<ReplyVO>) r -> r.error(Map.of("code", 1.5, "message", PRIVATE, "data", Map.of("bearer", BEARER)))),
                Arguments.of("out of range error code", (UnaryOperator<ReplyVO>) r -> r.error(Map.of("code", 2_147_483_648L, "message", PRIVATE))),
                Arguments.of("error scalar", (UnaryOperator<ReplyVO>) r -> r.error(PRIVATE)));
    }

    @ParameterizedTest(name = "initialize rejects {0}") @MethodSource("invalidInitialization")
    void initialize_pins_protocol_server_tools_and_unique_canonical_session(String name, UnaryOperator<ReplyVO> mutate,
                                                                          ReadOnlyMcpToolTransport.FailureKind expected) {
        var exchange = new FakeExchange(); exchange.responses = only("initialize", mutate);
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try { assertSafeFailure(expected, () -> client.initialize()); assertEquals(1, exchange.requests.size()); }
        finally { client.closeGracefully(); }
    }

    private static Stream<Arguments> invalidInitialization() {
        var unavailable = ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE;
        var changed = ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED;
        return Stream.of(
                Arguments.of("missing content type", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Mcp-Session-Id", List.of(SESSION))), unavailable),
                Arguments.of("missing session", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("application/json"))), unavailable),
                Arguments.of("duplicate session", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of(SESSION, SESSION))), unavailable),
                Arguments.of("non uuid session", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of(PRIVATE))), unavailable),
                Arguments.of("non canonical uuid", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of("ABCDEF00-0000-0000-0000-000000000001"))), unavailable),
                Arguments.of("SSE", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("text/event-stream"), "Mcp-Session-Id", List.of(SESSION))), unavailable),
                Arguments.of("non utf8 charset", (UnaryOperator<ReplyVO>) r -> r.headers(Map.of("Content-Type", List.of("application/json;charset=ISO-8859-1"), "Mcp-Session-Id", List.of(SESSION))), unavailable),
                Arguments.of("server name drift", (UnaryOperator<ReplyVO>) r -> { var value = r.result(); value.put("serverInfo", Map.of("name", PRIVATE, "version", "1.0")); return r.result(value); }, changed),
                Arguments.of("server version drift", (UnaryOperator<ReplyVO>) r -> { var value = r.result(); value.put("serverInfo", Map.of("name", "yuheng", "version", PRIVATE)); return r.result(value); }, changed),
                Arguments.of("protocol drift", (UnaryOperator<ReplyVO>) r -> { var value = r.result(); value.put("protocolVersion", "2025-06-18"); return r.result(value); }, changed),
                Arguments.of("missing tools", (UnaryOperator<ReplyVO>) r -> { var value = r.result(); value.put("capabilities", Map.of("resources", Map.of("private", PRIVATE))); return r.result(value); }, changed),
                Arguments.of("missing server version", (UnaryOperator<ReplyVO>) r -> { var value = r.result(); value.put("serverInfo", Map.of("name", "yuheng")); return r.result(value); }, changed));
    }

    @Test void supported_schema_metadata_and_open_defaults_are_safely_narrowed_to_the_local_closed_shape() {
        for (Object additional : List.of("absent", true, false)) for (String dialect : List.of("https://json-schema.org/draft/2020-12/schema", "http://json-schema.org/draft-07/schema#")) {
            var exchange = new FakeExchange(); var schema = new LinkedHashMap<String, Object>(schema(Map.of("type", "integer", "title", PRIVATE)));
            schema.put("$schema", dialect); schema.put("description", PRIVATE);
            if (!"absent".equals(additional)) schema.put("additionalProperties", additional);
            exchange.toolDocument = changed(exchange.toolDocument, "inputSchema", schema);
            exchange.toolDocument = changed(exchange.toolDocument, "annotations", List.of(PRIVATE, true));
            var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
            try {
                client.initialize(); var tool = client.listTools((String) null).tools().getFirst();
                assertEquals(Boolean.FALSE, tool.inputSchema().get("additionalProperties")); assertFalse(tool.inputSchema().toString().contains(PRIVATE));
                assertNull(tool.annotations()); assertNull(tool.outputSchema());
            } finally { client.closeGracefully(); }
        }
    }

    @Test void a_present_remote_output_schema_must_match_the_local_primitive_shape_and_is_projected_safely() {
        var exchange = new FakeExchange();
        exchange.toolDocument = changed(exchange.toolDocument, "outputSchema", Map.of("type", "object", "properties", Map.of("eligible", Map.of("type", "boolean", "title", PRIVATE)),
                "required", List.of("eligible"), "additionalProperties", false));
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize(); var tool = client.listTools((String) null).tools().getFirst();
            assertEquals(Map.of("type", "object", "properties", Map.of("eligible", Map.of("type", "boolean")), "required", List.of("eligible"), "additionalProperties", false), tool.outputSchema());
        } finally { client.closeGracefully(); }
        exchange = new FakeExchange(); exchange.toolDocument = changed(exchange.toolDocument, "outputSchema", schema(Map.of("type", "integer")));
        assertDiscoveryFailure(exchange, backend(exchange.toolDocument), ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED);
    }

    @ParameterizedTest(name = "tool document pins {0}") @MethodSource("documentChanges")
    void entire_remote_document_is_pinned_including_metadata_and_output_schema(String field, Object replacement) {
        var exchange = new FakeExchange(); var changed = new LinkedHashMap<>(exchange.toolDocument); changed.put(field, replacement);
        exchange.responses = only("tools/list", r -> r.result(Map.of("tools", List.of(changed))));
        assertDiscoveryFailure(exchange, backend(exchange.toolDocument), ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED);
    }
    private static Stream<Arguments> documentChanges() {
        return Stream.of(Arguments.of("title", PRIVATE + "changed"), Arguments.of("description", PRIVATE + "changed"),
                Arguments.of("annotations", Map.of("readOnlyHint", true)), Arguments.of("_meta", Map.of("private", BEARER)),
                Arguments.of("outputSchema", Map.of("type", "object", "properties", Map.of("eligible", Map.of("type", "boolean")))));
    }

    @ParameterizedTest(name = "schema rejects {0}") @MethodSource("invalidSchemas")
    void even_correctly_pinned_documents_require_exact_supported_primitive_schema(String name, Map<String, Object> schema) {
        var exchange = new FakeExchange(); exchange.toolDocument = changed(exchange.toolDocument, "inputSchema", schema);
        assertDiscoveryFailure(exchange, backend(exchange.toolDocument), ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED);
    }
    private static Stream<Arguments> invalidSchemas() {
        return Stream.of(Arguments.of("non object", Map.of("type", "array")),
                Arguments.of("missing field", Map.of("type", "object", "properties", Map.of(), "required", List.of("amount"))),
                Arguments.of("wrong type", schema(Map.of("type", "string"))), Arguments.of("nested object", schema(Map.of("type", "object"))),
                Arguments.of("numeric type", schema(Map.of("type", "number"))), Arguments.of("leaf constraint", schema(Map.of("type", "integer", "minimum", 0))),
                Arguments.of("leaf ref", schema(Map.of("type", "integer", "$ref", PRIVATE))),
                Arguments.of("required missing", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")))),
                Arguments.of("required duplicated", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount", "amount"))),
                Arguments.of("open schema object", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount"), "additionalProperties", Map.of())),
                Arguments.of("unsupported dialect", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount"), "$schema", "https://private.example/schema")),
                Arguments.of("composition", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount"), "allOf", List.of(Map.of()))));
    }

    @Test void one_page_discovery_rejects_missing_duplicate_tool_and_next_cursor() {
        for (var result : List.of(Map.of("tools", List.of()), Map.of("tools", List.of(new FakeExchange().toolDocument, new FakeExchange().toolDocument)),
                Map.of("tools", List.of(new FakeExchange().toolDocument), "nextCursor", PRIVATE))) {
            var exchange = new FakeExchange(); exchange.responses = only("tools/list", r -> r.result(result));
            assertDiscoveryFailure(exchange, backend(exchange.toolDocument), ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED);
        }
    }

    @ParameterizedTest(name = "call shape rejects {0}") @MethodSource("invalidCallResults")
    void call_result_requires_literal_flag_bounded_flat_structured_output_and_known_top_level_shape(String name, Map<String, Object> result) {
        var exchange = new FakeExchange(); exchange.callResult = result;
        assertCallFailure(exchange, ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
    }
    private static Stream<Arguments> invalidCallResults() {
        var absentFlag = Map.<String, Object>of("structuredContent", Map.of("eligible", true));
        var nullFlag = new HashMap<>(absentFlag); nullFlag.put("isError", null);
        return Stream.of(Arguments.of("missing flag", absentFlag), Arguments.of("null flag", nullFlag),
                Arguments.of("string flag", Map.of("isError", "false", "structuredContent", Map.of("eligible", true))),
                Arguments.of("missing structured content", Map.of("isError", false)),
                Arguments.of("scalar structured content", Map.of("isError", false, "structuredContent", PRIVATE)),
                Arguments.of("nested structured content", Map.of("isError", false, "structuredContent", Map.of("eligible", Map.of("private", PRIVATE)))),
                Arguments.of("fractional output", Map.of("isError", false, "structuredContent", Map.of("eligible", 1.5))),
                Arguments.of("too many output fields", Map.of("isError", false, "structuredContent", manyFields(17))),
                Arguments.of("content scalar", Map.of("isError", false, "structuredContent", Map.of("eligible", true), "content", PRIVATE)),
                Arguments.of("content array scalar", Map.of("isError", false, "structuredContent", Map.of("eligible", true), "content", List.of(PRIVATE))),
                Arguments.of("meta scalar", Map.of("isError", false, "structuredContent", Map.of("eligible", true), "_meta", PRIVATE)),
                Arguments.of("task", Map.of("isError", false, "structuredContent", Map.of("eligible", true), "task", Map.of("taskId", PRIVATE))),
                Arguments.of("unknown augmentation", Map.of("isError", false, "structuredContent", Map.of("eligible", true), "private", PRIVATE)));
    }

    @Test void actual_peer_tool_error_drops_private_content_and_structured_error_without_success_schema_validation() {
        var exchange = new FakeExchange(); exchange.callResult = Map.of("isError", true, "structuredContent", Map.of("error", PRIVATE),
                "content", List.of(Map.of("type", "text", "text", PRIVATE)), "_meta", Map.of("bearer", BEARER));
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize(); client.listTools((String) null); var result = client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L)));
            assertEquals(Boolean.TRUE, result.isError()); assertEquals(List.of(), result.content()); assertNull(result.structuredContent()); assertNull(result.meta());
        } finally { client.closeGracefully(); }
    }

    @ParameterizedTest(name = "tool error discards {0}") @MethodSource("errorStructuredValues")
    void literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure(String name, Object structured) {
        var exchange = new FakeExchange(); var raw = new HashMap<String, Object>(); raw.put("isError", true); raw.put("structuredContent", structured);
        raw.put("content", List.of(Map.of("private", PRIVATE))); exchange.callResult = raw;
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize(); client.listTools((String) null);
            var result = client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L)));
            assertEquals(Boolean.TRUE, result.isError()); assertEquals(List.of(), result.content()); assertNull(result.structuredContent()); assertNull(result.meta());
        } finally { client.closeGracefully(); }
    }
    private static Stream<Arguments> errorStructuredValues() {
        return Stream.of(Arguments.of("scalar", PRIVATE), Arguments.of("array", List.of(PRIVATE, Map.of("private", BEARER))),
                Arguments.of("null", (Object) null), Arguments.of("boolean", true), Arguments.of("integer", 42));
    }

    @Test void validated_json_rpc_error_keeps_only_integer_code_and_fixed_message_without_original_data_or_cause() {
        var exchange = new FakeExchange(); exchange.responses = only("tools/call", r -> r.error(Map.of("code", -32603, "message", PRIVATE, "data", Map.of("bearer", BEARER))));
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize(); client.listTools((String) null);
            var error = assertThrows(McpError.class, () -> client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L))));
            assertEquals(-32603, error.getJsonRpcError().code()); assertEquals("TIANJI_MCP_REMOTE_ERROR", error.getJsonRpcError().message());
            assertNull(error.getJsonRpcError().data()); assertPrivateAbsent(error);
        } finally { client.closeGracefully(); }
    }

    @ParameterizedTest(name = "call HTTP {0}") @MethodSource("httpFailures")
    void call_http_classification_is_safe_and_does_not_reinitialize(int status, ReadOnlyMcpToolTransport.FailureKind expected) {
        var exchange = new FakeExchange(); exchange.responses = only("tools/call", r -> r.status(status).body(PRIVATE + BEARER));
        assertCallFailure(exchange, expected); assertEquals(4, exchange.requests.size());
    }
    private static Stream<Arguments> httpFailures() {
        return Stream.of(Arguments.of(401, ReadOnlyMcpToolTransport.FailureKind.OPERATION_DENIED), Arguments.of(403, ReadOnlyMcpToolTransport.FailureKind.OPERATION_DENIED),
                Arguments.of(404, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN), Arguments.of(500, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN));
    }

    @Test void asynchronous_exchange_failures_complete_native_pending_request_with_fixed_causeless_failure() {
        var exchange = new FakeExchange(); exchange.failMethod = "tools/call";
        assertTimeout(Duration.ofMillis(750), () -> assertCallFailure(exchange, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN));
        assertEquals(4, exchange.requests.size());
    }

    @Test void raw_exchange_io_before_any_tools_submission_is_not_sent_and_keeps_only_a_safe_native_initialize_wrapper() {
        var exchange = new FakeExchange(); exchange.failMethod = "initialize";
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            assertTimeout(Duration.ofMillis(750), () -> assertSafeFailure(ReadOnlyMcpToolTransport.FailureKind.OPERATION_NOT_SENT, () -> client.initialize()));
            assertEquals(List.of("initialize"), exchange.methods);
        } finally { client.closeGracefully(); }
    }

    @Test void a_future_completed_with_null_response_fails_native_pending_call_immediately_without_timeout_or_private_cause() {
        var exchange = new FakeExchange(); exchange.nullResponseMethod = "tools/call";
        assertTimeout(Duration.ofMillis(750), () -> assertCallFailure(exchange, ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN));
        assertEquals(4, exchange.requests.size());
    }

    @Test void a_raw_target_document_exceeding_the_existing_hash_bound_is_profile_changed_without_hash_exception_cause() {
        var exchange = new FakeExchange(); var oversized = changed(exchange.toolDocument, "description", PRIVATE.repeat(6000));
        exchange.responses = only("tools/list", r -> r.result(Map.of("tools", List.of(oversized))));
        assertDiscoveryFailure(exchange, backend(exchange.toolDocument), ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED);
    }

    @Test void call_sse_and_invalid_json_media_type_are_rejected_without_raw_protocol_fallback() {
        for (String type : List.of("text/event-stream", "text/html", "application/json;charset=ISO-8859-1")) {
            var exchange = new FakeExchange(); exchange.responses = only("tools/call", r -> r.headers(Map.of("Content-Type", List.of(type))));
            assertCallFailure(exchange, ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
        }
    }

    @Test void owned_delete_requires_empty_204_and_cannot_let_remote_headers_or_failure_escape() {
        var exchange = new FakeExchange(); exchange.responses = only("DELETE", r -> r.status(200).body(PRIVATE + BEARER));
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize();
            var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.deleteOwnedSession().block(Duration.ofSeconds(1)));
            assertNull(failure.getCause()); assertPrivateAbsent(failure); assertEquals(3, exchange.requests.size());
        } finally { client.closeGracefully(); }
    }

    @Test void a_null_completed_delete_future_is_unknown_and_cannot_trigger_a_second_delete() {
        var exchange = new FakeExchange(); exchange.nullResponseMethod = "DELETE";
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize();
            var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.deleteOwnedSession().block(Duration.ofSeconds(1)));
            assertEquals(ReadOnlyMcpToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN, failure.kind()); assertNull(failure.getCause()); assertPrivateAbsent(failure);
            assertEquals(3, exchange.requests.size()); transport.deleteOwnedSession().block(Duration.ofSeconds(1));
            assertEquals(3, exchange.requests.size()); assertEquals("DELETE", exchange.requests.getLast().method());
        } finally { client.closeGracefully(); }
    }

    @Test void initialized_notification_requires_empty_202_and_does_not_start_discovery_after_failure() {
        for (var mutate : List.<UnaryOperator<ReplyVO>>of(r -> r.status(200), r -> r.body(PRIVATE))) {
            var exchange = new FakeExchange(); exchange.responses = only("notifications/initialized", mutate);
            var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
            try { assertSafeFailure(ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE, () -> client.initialize()); assertEquals(2, exchange.requests.size()); }
            finally { client.closeGracefully(); }
        }
    }

    @Test void opaque_holders_hide_values_and_unmarshal_only_owned_exact_sdk_result_types() {
        var exchange = new FakeExchange(); var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange);
        var responses = new ArrayList<McpSchema.JSONRPCResponse>();
        transport.connect(incoming -> incoming.doOnNext(message -> responses.add((McpSchema.JSONRPCResponse) message))).block();
        transport.sendMessage(initialize("sdk-1")).block(Duration.ofSeconds(1));
        Object opaque = responses.getFirst().result(); assertNotNull(opaque); assertFalse(opaque instanceof Map<?, ?>); assertFalse(opaque.toString().contains(PRIVATE));
        var initialized = transport.unmarshalFrom(opaque, new TypeRef<McpSchema.InitializeResult>() { }); assertEquals("yuheng", initialized.serverInfo().name());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.unmarshalFrom(opaque, new TypeRef<McpSchema.CallToolResult>() { }));
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.unmarshalFrom(opaque, new TypeRef<Object>() { }));
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.unmarshalFrom(Map.of("private", PRIVATE), new TypeRef<McpSchema.InitializeResult>() { }));
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.unmarshalFrom(opaque, new TypeRef<List<McpSchema.InitializeResult>>() { }));
        transport.closeGracefully().block();
    }

    @Test void connect_and_close_are_local_once_only_and_unsupported_outbound_traffic_never_reaches_exchange() {
        var exchange = new FakeExchange(); var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange);
        assertEquals(List.of(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION), transport.protocolVersions());
        transport.connect(incoming -> incoming).block(); assertEquals(0, exchange.requests.size());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.connect(incoming -> incoming).block());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(new McpSchema.JSONRPCRequest("ping", "sdk-1")).block());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(new McpSchema.JSONRPCNotification("notifications/cancelled")).block());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(new McpSchema.JSONRPCRequest("tools/list", "sdk-1")).block());
        assertEquals(0, exchange.requests.size()); transport.closeGracefully().block(); transport.closeGracefully().block();
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(initialize("sdk-1")).block());
        assertEquals(0, exchange.requests.size());
    }

    @Test void duplicate_and_reentrant_lifecycle_and_call_metadata_are_rejected_before_exchange() {
        var exchange = new FakeExchange(); var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange);
        transport.connect(incoming -> incoming).block(); transport.sendMessage(initialize("sdk-1")).block();
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(initialize("sdk-2")).block());
        transport.sendMessage(new McpSchema.JSONRPCNotification("notifications/initialized")).block();
        transport.sendMessage(new McpSchema.JSONRPCRequest("tools/list", "sdk-2", Map.of())).block();
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(new McpSchema.JSONRPCRequest("tools/list", "sdk-3", Map.of())).block());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(new McpSchema.JSONRPCRequest("tools/call", "sdk-3",
                new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L), Map.of("hostToolContext", PRIVATE)))).block());
        assertEquals(3, exchange.requests.size()); transport.closeGracefully().block();
    }

    @Test void at_most_one_exchange_is_pending_and_no_second_initialize_is_submitted() {
        var exchange = new FakeExchange(); exchange.holdMethod = "initialize";
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); transport.connect(incoming -> incoming).block();
        var first = transport.sendMessage(initialize("sdk-1")).toFuture();
        assertFalse(first.isDone()); assertEquals(1, exchange.requests.size());
        assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> transport.sendMessage(initialize("sdk-2")).block(Duration.ofSeconds(1)));
        assertEquals(1, exchange.requests.size()); exchange.releaseHeld(); first.join(); transport.closeGracefully().block();
    }

    @Test void sdk_debug_info_warn_error_and_throwables_never_expose_private_success_error_or_malformed_payloads() {
        try (var logs = new SdkLogs()) {
            var exchange = new FakeExchange(); exchange.callResult = Map.of("isError", false, "structuredContent", Map.of("note", PRIVATE),
                    "content", List.of(Map.of("type", "unknown-private-subprotocol", "value", PRIVATE)), "_meta", Map.of("bearer", BEARER));
            var output = new ToolSchema(Map.of("note", ToolSchema.ValueType.STRING), Set.of("note"));
            var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument, output), exchange); var client = client(transport);
            try {
                client.initialize(); client.listTools((String) null);
                assertEquals(Map.of("note", PRIVATE), client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L))).structuredContent());
            } finally { client.closeGracefully(); }
            actual_peer_tool_error_drops_private_content_and_structured_error_without_success_schema_validation();
            literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure("scalar", PRIVATE);
            literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure("array", List.of(PRIVATE, Map.of("private", BEARER)));
            literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure("null", null);
            literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure("boolean", true);
            literal_tool_error_discards_any_bounded_json_structured_content_before_sdk_exposure("integer", 42);
            validated_json_rpc_error_keeps_only_integer_code_and_fixed_message_without_original_data_or_cause();
            var malformed = new FakeExchange(); malformed.responses = only("tools/call", r -> r.body("{\"private\":\"" + PRIVATE + BEARER + "\""));
            assertCallFailure(malformed, ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE);
            asynchronous_exchange_failures_complete_native_pending_request_with_fixed_causeless_failure();
            String captured = logs.text(); assertTrue(captured.contains("Received response:")); assertTrue(captured.contains("Server response with Protocol:"));
            assertTrue(captured.contains("TIANJI_MCP_REMOTE_ERROR")); assertFalse(captured.contains(PRIVATE)); assertFalse(captured.contains(BEARER));
        }
    }

    private static McpSyncClient client(TianjiMcpJsonRpcTransport transport) {
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(1)).initializationTimeout(Duration.ofSeconds(1))
                .clientInfo(new McpSchema.Implementation("tianji-readonly-mcp", "1.0"))
                .capabilities(McpSchema.ClientCapabilities.builder().build()).enableCallToolSchemaCaching(false).build();
    }
    private static TianjiFixedReadOnlyMcpToolBindings.BackendVO backend(Map<String, Object> document) {
        return backend(document, OUTPUT);
    }
    private static TianjiFixedReadOnlyMcpToolBindings.BackendVO backend(Map<String, Object> document, ToolSchema output) {
        return new TianjiFixedReadOnlyMcpToolBindings.BackendVO(21, URI.create("https://idp.example/21"), ENDPOINT,
                URI.create("https://mcp.example/api"), "agent-mcp", "agent-client", "agent-app", "business", "agent", "test",
                Set.of("catalog:read"), "yuheng", "1.0", "catalog.read", TianjiFixedReadOnlyMcpToolBindings.remoteToolDocumentSha256(document),
                INPUT, output, true, Duration.ofSeconds(3));
    }

    private static McpSchema.JSONRPCRequest initialize(String id) {
        return new McpSchema.JSONRPCRequest("initialize", id, new McpSchema.InitializeRequest(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION,
                McpSchema.ClientCapabilities.builder().build(), new McpSchema.Implementation("tianji-readonly-mcp", "1.0")));
    }
    private static void assertCallFailure(FakeExchange exchange, ReadOnlyMcpToolTransport.FailureKind expected) {
        var transport = new TianjiMcpJsonRpcTransport(backend(exchange.toolDocument), exchange); var client = client(transport);
        try {
            client.initialize(); client.listTools((String) null);
            assertSafeFailure(expected, () -> client.callTool(new McpSchema.CallToolRequest("catalog.read", Map.of("amount", 20L))));
        } finally { client.closeGracefully(); }
    }
    private static void assertDiscoveryFailure(FakeExchange exchange, TianjiFixedReadOnlyMcpToolBindings.BackendVO backend,
                                               ReadOnlyMcpToolTransport.FailureKind expected) {
        var transport = new TianjiMcpJsonRpcTransport(backend, exchange); var client = client(transport);
        try { client.initialize(); assertSafeFailure(expected, () -> client.listTools((String) null)); assertEquals(3, exchange.requests.size()); }
        finally { client.closeGracefully(); }
    }
    private static void assertSafeFailure(ReadOnlyMcpToolTransport.FailureKind expected, org.junit.jupiter.api.function.Executable operation) {
        var surfaced = assertThrows(RuntimeException.class, operation); assertPrivateAbsent(surfaced);
        ReadOnlyMcpToolTransport.Failure failure;
        if (surfaced instanceof ReadOnlyMcpToolTransport.Failure direct) failure = direct;
        else {
            assertEquals(RuntimeException.class, surfaced.getClass()); assertEquals("Client failed to initialize by explicit API call", surfaced.getMessage());
            failure = assertInstanceOf(ReadOnlyMcpToolTransport.Failure.class, surfaced.getCause());
        }
        assertEquals(expected, failure.kind()); assertEquals(expected.name(), failure.getMessage()); assertNull(failure.getCause()); assertPrivateAbsent(failure);
    }
    private static void assertPrivateAbsent(Throwable failure) {
        assertFalse(failure.toString().contains(PRIVATE)); assertFalse(failure.toString().contains(BEARER));
        if (failure.getCause() != null) assertPrivateAbsent(failure.getCause());
        for (var suppressed : failure.getSuppressed()) assertPrivateAbsent(suppressed);
    }
    private static UnaryOperator<ReplyVO> only(String method, UnaryOperator<ReplyVO> mutate) { return reply -> method.equals(reply.method) ? mutate.apply(reply) : reply; }
    private static Map<String, Object> schema(Map<String, Object> leaf) { return Map.of("type", "object", "properties", Map.of("amount", leaf), "required", List.of("amount")); }
    private static Map<String, Object> changed(Map<String, Object> values, String field, Object value) { var changed = new LinkedHashMap<>(values); changed.put(field, value); return changed; }
    private static Map<String, Object> manyFields(int count) { var values = new LinkedHashMap<String, Object>(); for (int index = 0; index < count; index++) values.put("field" + index, true); return values; }

    private record ReplyVO(HttpRequest request, String method, JsonNode id, int status, Map<String, List<String>> headers, byte[] body) {
        private ReplyVO body(String value) { return new ReplyVO(request, method, id, status, headers, value.getBytes(StandardCharsets.UTF_8)); }
        private ReplyVO json(Object value) { return new ReplyVO(request, method, id, status, headers, bytes(value)); }
        private ReplyVO headers(Map<String, List<String>> value) { return new ReplyVO(request, method, id, status, value, body); }
        private ReplyVO status(int value) { return new ReplyVO(request, method, id, value, headers, body); }
        @SuppressWarnings("unchecked") private Map<String, Object> envelope() { return JSON.convertValue(tree(new String(body, StandardCharsets.UTF_8)), Map.class); }
        @SuppressWarnings("unchecked") private Map<String, Object> result() { return (Map<String, Object>) envelope().get("result"); }
        private ReplyVO result(Object value) { return json(Map.of("jsonrpc", "2.0", "id", id, "result", value)); }
        private ReplyVO error(Object value) { return json(Map.of("jsonrpc", "2.0", "id", id, "error", value)); }
    }

    private static final class SdkLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger("io.modelcontextprotocol");
        private final Level originalLevel = logger.getLevel();
        private final boolean originalAdditive = logger.isAdditive();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private SdkLogs() { appender.start(); logger.addAppender(appender); logger.setAdditive(false); logger.setLevel(Level.DEBUG); }
        private String text() {
            var output = new StringBuilder();
            for (var event : appender.list) {
                output.append(event.getLevel()).append(' ').append(event.getFormattedMessage()).append('\n');
                if (event.getThrowableProxy() != null) output.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            return output.toString();
        }
        @Override public void close() { logger.detachAppender(appender); logger.setLevel(originalLevel); logger.setAdditive(originalAdditive); appender.stop(); }
    }

    private static final class FakeExchange implements TianjiMcpJsonRpcTransport.Exchange {
        private Map<String, Object> toolDocument = Map.of("name", "catalog.read", "title", PRIVATE, "description", PRIVATE,
                "inputSchema", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer", "description", PRIVATE)),
                        "required", List.of("amount"), "additionalProperties", true),
                "annotations", Map.of("readOnlyHint", "true", "destructiveHint", "false"), "_meta", Map.of("private", PRIVATE));
        private Map<String, Object> callResult = Map.of("isError", false, "structuredContent", Map.of("eligible", true),
                "content", List.of(Map.of("type", "text", "text", PRIVATE)), "_meta", Map.of("private", PRIVATE));
        private UnaryOperator<ReplyVO> responses = UnaryOperator.identity();
        private String failMethod;
        private String nullResponseMethod;
        private String holdMethod;
        private ReplyVO heldReply;
        private final CompletableFuture<HttpResponse<byte[]>> held = new CompletableFuture<>();
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> methods = new ArrayList<>();
        private final List<Boolean> toolsCalls = new ArrayList<>();
        private final List<JsonNode> ids = new ArrayList<>();

        @Override public CompletableFuture<HttpResponse<byte[]>> exchange(HttpRequest request, boolean toolsCall) {
            requests.add(request); assertEquals(ENDPOINT, request.uri());
            assertTrue(request.headers().map().keySet().stream().map(name -> name.toLowerCase(Locale.ROOT))
                    .allMatch(Set.of("content-type", "accept", "mcp-protocol-version", "mcp-session-id")::contains));
            assertEquals(Optional.of("application/json,text/event-stream"), request.headers().firstValue("Accept"));
            assertEquals(Optional.of(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION), request.headers().firstValue("Mcp-Protocol-Version"));
            assertFalse(request.headers().firstValue("Authorization").isPresent()); assertFalse(request.headers().firstValue("Cookie").isPresent());
            if ("DELETE".equals(request.method())) {
                assertFalse(toolsCall); assertEquals(Optional.of(SESSION), request.headers().firstValue("Mcp-Session-Id"));
                return reply(new ReplyVO(request, "DELETE", null, 204, Map.of(), new byte[0]));
            }
            assertEquals("POST", request.method());
            assertTrue(request.headers().firstValue("Content-Type").orElseThrow().toLowerCase(Locale.ROOT).startsWith("application/json"));
            var payload = body(request); var message = tree(payload);
            assertEquals("2.0", message.path("jsonrpc").textValue());
            assertFalse(payload.contains(PRIVATE)); assertFalse(payload.contains("hostToolContext")); assertFalse(payload.contains("_meta"));
            String method = message.path("method").textValue(); methods.add(method); toolsCalls.add(toolsCall);
            JsonNode id = message.get("id"); if (id != null) { assertTrue(id.isTextual() || id.isIntegralNumber()); ids.add(id); }
            if ("initialize".equals(method)) {
                assertEquals(1, requests.size()); assertFalse(request.headers().firstValue("Mcp-Session-Id").isPresent());
                assertEquals(TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION, message.path("params").path("protocolVersion").textValue());
                assertEquals(0, message.path("params").path("capabilities").size());
                return rpc(request, id, Map.of("protocolVersion", TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION,
                        "serverInfo", Map.of("name", "yuheng", "version", "1.0", "title", PRIVATE),
                        "capabilities", Map.of("tools", Map.of(), "resources", Map.of(), "prompts", Map.of()),
                        "instructions", PRIVATE, "_meta", Map.of("private", PRIVATE)), true);
            }
            assertEquals(Optional.of(SESSION), request.headers().firstValue("Mcp-Session-Id"));
            if ("notifications/initialized".equals(method)) {
                assertEquals(2, requests.size()); assertNull(id);
                return reply(new ReplyVO(request, method, null, 202, Map.of(), new byte[0]));
            }
            if ("tools/list".equals(method)) {
                assertEquals(3, requests.size()); assertFalse(message.path("params").hasNonNull("cursor"));
                return rpc(request, id, Map.of("tools", List.of(toolDocument), "_meta", Map.of("private", PRIVATE)), false);
            }
            assertEquals("tools/call", method); assertEquals(4, requests.size());
            assertEquals("catalog.read", message.path("params").path("name").textValue());
            assertEquals(20L, message.path("params").path("arguments").path("amount").longValue());
            return rpc(request, id, callResult, false);
        }
        private CompletableFuture<HttpResponse<byte[]>> rpc(HttpRequest request, JsonNode id, Map<String, Object> result, boolean initialization) {
            Map<String, List<String>> headers = initialization
                    ? Map.of("Content-Type", List.of("application/json;charset=UTF-8"), "Mcp-Session-Id", List.of(SESSION))
                    : Map.of("Content-Type", List.of("application/json"));
            return reply(new ReplyVO(request, methods.getLast(), id, 200, headers, bytes(Map.of("jsonrpc", "2.0", "id", id, "result", result))));
        }
        private CompletableFuture<HttpResponse<byte[]>> reply(ReplyVO original) {
            if (original.method.equals(failMethod)) return CompletableFuture.failedFuture(new IllegalStateException(PRIVATE + BEARER));
            if (original.method.equals(nullResponseMethod)) return CompletableFuture.completedFuture(null);
            if (original.method.equals(holdMethod)) { heldReply = original; return held; }
            var reply = responses.apply(original); return CompletableFuture.completedFuture(response(reply.request, reply.status, reply.headers, reply.body));
        }
        private void releaseHeld() { held.complete(response(heldReply.request, heldReply.status, heldReply.headers, heldReply.body)); }
    }

    private static JsonNode tree(String value) { try { return JSON.readTree(value); } catch (Exception malformed) { throw new AssertionError(malformed); } }
    private static byte[] bytes(Object value) { try { return JSON.writeValueAsBytes(value); } catch (Exception malformed) { throw new AssertionError(malformed); } }
    private static String json(Object value) { return new String(bytes(value), StandardCharsets.UTF_8); }
    private static String body(HttpRequest request) {
        var completed = new CompletableFuture<byte[]>(); var output = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) { byte[] data = new byte[buffer.remaining()]; buffer.get(data); output.writeBytes(data); }
            @Override public void onError(Throwable failure) { completed.completeExceptionally(failure); }
            @Override public void onComplete() { completed.complete(output.toByteArray()); }
        });
        return new String(completed.join(), StandardCharsets.UTF_8);
    }
    private static HttpResponse<byte[]> response(HttpRequest request, int status, Map<String, List<String>> headers, byte[] body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(headers, (name, value) -> true); }
            @Override public byte[] body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
