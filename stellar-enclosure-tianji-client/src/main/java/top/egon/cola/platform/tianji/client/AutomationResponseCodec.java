package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Strict response validation before mapping an automation wire value to its neutral contract type. */
final class AutomationResponseCodec {
    private static final Pattern UTC_MICROS = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](?:\\.[0-9]{1,6})?Z");
    private static final Pattern DEFINITION_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern RELEASE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern COMMAND_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern LOGICAL_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CORRELATION = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> SUMMARY_FIELDS = Set.of("draftId", "definitionKey", "title", "baseReleaseVersion",
            "state", "revision", "contentSha256", "publishedReleaseVersion", "createdAt", "updatedAt", "availableActions");
    private static final Set<String> DRAFT_FIELDS = Set.of("baseSourceManifestSha256", "content", "lastCommandId", "publication", "commandReceipt");
    private static final int MAX_RESPONSE_BYTES = 9 * 1024 * 1024;
    private static final Set<String> ERROR_FIELDS = Set.of("code", "correlationId");
    private static final Set<String> GENERIC_CONFLICTS = Set.of("TIANJI_PROCESS_VERSION_CONFLICT", "TIANJI_IDEMPOTENCY_CONFLICT");
    private static final Map<Integer, String> ERRORS = Map.of(400, "TIANJI_INVALID_COMMAND", 401, "TIANJI_UNAUTHENTICATED",
            403, "TIANJI_PERMISSION_DENIED", 404, "TIANJI_RESOURCE_NOT_FOUND", 413, "TIANJI_REQUEST_TOO_LARGE",
            503, "TIANJI_DEPENDENCY_UNAVAILABLE");
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                            .maxStringLength(DraftContentVO.MAX_CONTENT_BYTES).maxNameLength(512)
                            .maxNumberLength(32).maxTokenCount(500_000).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    private AutomationResponseCodec() { }

    static byte[] encode(Object body) { return MAPPER.writeValueAsBytes(body); }

    static <T> T decode(AutomationHttpTransport.ResponseVO response, Class<T> type, Consumer<JsonNode> wireValidator) {
        return decode(response, type, wireValidator, value -> true);
    }

    static <T> T decode(AutomationHttpTransport.ResponseVO response, Class<T> type,
                        Consumer<JsonNode> wireValidator, Predicate<T> matchesRequest) {
        try {
            JsonNode root = tree(response.body());
            wireValidator.accept(root);
            T result = MAPPER.treeToValue(root, type);
            if (result == null || !matchesRequest.test(result)) throw invalid();
            return result;
        } catch (RuntimeException invalid) {
            throw invalidResponse(response.status());
        }
    }

    static ProcessClientException error(byte[] body, int status, String token, Set<String> extraConflicts, Map<Integer, String> extraErrors) {
        try {
            JsonNode root = tree(body);
            boolean security = status == 401 || status == 403;
            boolean securityShape = security && Set.of("code").equals(Set.copyOf(root.propertyNames()));
            if (!securityShape) exact(root, ERROR_FIELDS);
            String code = string(root.path("code"));
            boolean accepted = status == 409 ? GENERIC_CONFLICTS.contains(code) || extraConflicts.contains(code)
                    : code.equals(ERRORS.getOrDefault(status, extraErrors.get(status)));
            if (!accepted) throw invalid();
            String correlation = null;
            if (!securityShape && !root.path("correlationId").isNull()) {
                correlation = checked(root.path("correlationId"), CORRELATION);
                if (correlation.contains(token)) correlation = null;
            }
            return new ProcessClientException(status, code, correlation, null);
        } catch (RuntimeException malformed) {
            return invalidResponse(status);
        }
    }

    static DraftSummaryDTO summary(AutomationHttpTransport.ResponseVO response, Predicate<DraftSummaryDTO> matchesRequest) {
        return decode(response, DraftSummaryDTO.class, node -> {
            exact(node, SUMMARY_FIELDS);
            summaryFields(node);
        }, value -> value.isSummaryConsistent() && matchesRequest.test(value));
    }

    static ProcessDraftDTO draft(AutomationHttpTransport.ResponseVO response, Predicate<ProcessDraftDTO> matchesRequest) {
        return decode(response, ProcessDraftDTO.class, node -> {
            var fields = new HashSet<>(SUMMARY_FIELDS);
            fields.addAll(DRAFT_FIELDS);
            exact(node, fields);
            summaryFields(node);
            nullablePattern(node, "baseSourceManifestSha256", SHA256);
            checked(node.path("lastCommandId"), COMMAND_ID);
            content(node.path("content"));
            nullable(node, "publication", AutomationResponseCodec::publication);
            nullable(node, "commandReceipt", AutomationResponseCodec::receipt);
        }, value -> value.isDraftProjectionConsistent()
                && (value.getCommandReceipt() == null || value.getCommandReceipt().isReceiptConsistent())
                && matchesRequest.test(value));
    }

    static ProcessDraftPageDTO drafts(AutomationHttpTransport.ResponseVO response, Predicate<ProcessDraftPageDTO> matchesRequest) {
        return decode(response, ProcessDraftPageDTO.class, node -> {
            exact(node, "observedAt", "items", "nextCursor");
            utc(node.path("observedAt"));
            cursor(node.path("nextCursor"));
            array(node.path("items"), 100).forEach(item -> {
                exact(item, SUMMARY_FIELDS);
                summaryFields(item);
            });
        }, value -> value.isObservationTimeValid() && value.getItems().stream().allMatch(DraftSummaryDTO::isSummaryConsistent)
                && matchesRequest.test(value));
    }

    static DraftMutationResultDTO mutation(AutomationHttpTransport.ResponseVO response, Predicate<DraftMutationResultDTO> matchesRequest) {
        return decode(response, DraftMutationResultDTO.class, node -> {
            exact(node, "draftId", "state", "revision", "contentSha256", "updatedAt", "commandId");
            checked(node.path("draftId"), LOGICAL_ID);
            checked(node.path("commandId"), COMMAND_ID);
            checked(node.path("contentSha256"), SHA256);
            string(node.path("state"));
            exactInteger(node.path("revision"), 1);
            utc(node.path("updatedAt"));
        }, value -> value.isUpdateTimeValid() && matchesRequest.test(value));
    }

    static DraftValidationResultDTO validation(AutomationHttpTransport.ResponseVO response, Predicate<DraftValidationResultDTO> matchesRequest) {
        return decode(response, DraftValidationResultDTO.class, node -> {
            exact(node, "draftId", "revision", "contentSha256", "valid", "compilerVersion", "sourceManifestSha256", "graph", "issues");
            checked(node.path("draftId"), LOGICAL_ID);
            exactInteger(node.path("revision"), 1);
            checked(node.path("contentSha256"), SHA256);
            require(node.path("valid").isBoolean());
            nonBlank(node.path("compilerVersion"), 128);
            nullablePattern(node, "sourceManifestSha256", SHA256);
            nullable(node, "graph", AutomationResponseCodec::graph);
            array(node.path("issues"), 128).forEach(issue -> {
                exact(issue, "path", "code", "message", "nodeId");
                string(issue.path("path"));
                nonBlank(issue.path("code"), DraftContentVO.MAX_CONTENT_BYTES);
                nonBlank(issue.path("message"), DraftContentVO.MAX_CONTENT_BYTES);
                nullablePattern(issue, "nodeId", LOGICAL_ID);
            });
        }, value -> value.isValidationOutcomeConsistent() && matchesRequest.test(value));
    }

    static DraftPublicationVO publication(AutomationHttpTransport.ResponseVO response, Predicate<DraftPublicationVO> matchesRequest) {
        return decode(response, DraftPublicationVO.class, AutomationResponseCodec::publication, matchesRequest);
    }

    static AutomationActivationDTO activation(AutomationHttpTransport.ResponseVO response, Predicate<AutomationActivationDTO> matchesRequest) {
        return decode(response, AutomationActivationDTO.class, AutomationResponseCodec::activation,
                value -> value.isActivationConsistent() && matchesRequest.test(value));
    }

    static ActivationPageDTO activations(AutomationHttpTransport.ResponseVO response, Predicate<ActivationPageDTO> matchesRequest) {
        return decode(response, ActivationPageDTO.class, node -> {
            exact(node, "definitionKey", "observedAt", "current", "items", "nextCursor");
            checked(node.path("definitionKey"), DEFINITION_KEY);
            utc(node.path("observedAt"));
            cursor(node.path("nextCursor"));
            nullable(node, "current", current -> {
                exact(current, "revision", "releaseVersion", "activatedAt");
                exactInteger(current.path("revision"), 1);
                checked(current.path("releaseVersion"), RELEASE_VERSION);
                utc(current.path("activatedAt"));
            });
            array(node.path("items"), 100).forEach(AutomationResponseCodec::activation);
        }, value -> value.isActivationObservationConsistent() && value.getItems().stream().allMatch(AutomationActivationDTO::isActivationConsistent)
                && matchesRequest.test(value));
    }

    private static void summaryFields(JsonNode node) {
        checked(node.path("draftId"), LOGICAL_ID);
        checked(node.path("definitionKey"), DEFINITION_KEY);
        String title = nonBlank(node.path("title"), 128);
        require(title.chars().noneMatch(Character::isISOControl));
        nullablePattern(node, "baseReleaseVersion", RELEASE_VERSION);
        string(node.path("state"));
        exactInteger(node.path("revision"), 1);
        checked(node.path("contentSha256"), SHA256);
        nullablePattern(node, "publishedReleaseVersion", RELEASE_VERSION);
        utc(node.path("createdAt"));
        utc(node.path("updatedAt"));
        array(node.path("availableActions"), 4).forEach(AutomationResponseCodec::string);
    }

    private static void content(JsonNode content) {
        exact(content, "models", "inputSchemaSource", "outputSchemaSource", "nodeBindingsSource", "eventRoutesSource");
        nullable(content, "models", models -> array(models, 64).forEach(model -> {
            exact(model, "type", "definitionKey", "xml");
            string(model.path("type"));
            string(model.path("definitionKey"));
            string(model.path("xml"));
        }));
        for (String name : Set.of("inputSchemaSource", "outputSchemaSource", "nodeBindingsSource", "eventRoutesSource")) {
            nullable(content, name, AutomationResponseCodec::string);
        }
    }

    private static void receipt(JsonNode node) {
        exact(node, "commandId", "operation", "requestSha256", "resultRevision", "resultContentSha256", "releaseVersion", "completedAt");
        checked(node.path("commandId"), COMMAND_ID);
        string(node.path("operation"));
        checked(node.path("requestSha256"), SHA256);
        exactInteger(node.path("resultRevision"), 1);
        checked(node.path("resultContentSha256"), SHA256);
        nullablePattern(node, "releaseVersion", RELEASE_VERSION);
        utc(node.path("completedAt"));
    }

    private static void publication(JsonNode node) {
        exact(node, "draftId", "publishedRevision", "draftRevision", "releaseVersion", "sourceManifestSha256", "compiledManifestSha256",
                "dependencyManifestSha256", "compilerVersion", "publishedAt", "commandId");
        checked(node.path("draftId"), LOGICAL_ID);
        exactInteger(node.path("publishedRevision"), 1);
        exactInteger(node.path("draftRevision"), 1);
        checked(node.path("releaseVersion"), RELEASE_VERSION);
        manifestDigests(node);
        nonBlank(node.path("compilerVersion"), 128);
        utc(node.path("publishedAt"));
        checked(node.path("commandId"), COMMAND_ID);
    }

    private static void activation(JsonNode node) {
        exact(node, "definitionKey", "revision", "releaseVersion", "previousRevision", "commandId", "expectedActivationRevision",
                "sourceManifestSha256", "compiledManifestSha256", "dependencyManifestSha256", "activatedAt");
        checked(node.path("definitionKey"), DEFINITION_KEY);
        exactInteger(node.path("revision"), 1);
        checked(node.path("releaseVersion"), RELEASE_VERSION);
        nullable(node, "previousRevision", value -> exactInteger(value, 1));
        checked(node.path("commandId"), COMMAND_ID);
        exactInteger(node.path("expectedActivationRevision"), 0);
        manifestDigests(node);
        utc(node.path("activatedAt"));
    }

    private static void manifestDigests(JsonNode node) {
        for (String field : Set.of("sourceManifestSha256", "compiledManifestSha256", "dependencyManifestSha256")) {
            checked(node.path(field), SHA256);
        }
    }

    static void graph(JsonNode graph) {
        exact(graph, "definitionKey", "nodes", "edges");
        checked(graph.path("definitionKey"), LOGICAL_ID);
        array(graph.path("nodes"), 128).forEach(node -> {
            exact(node, "id", "kind", "name");
            string(node.path("id"));
            string(node.path("kind"));
            string(node.path("name"));
        });
        array(graph.path("edges"), Integer.MAX_VALUE).forEach(edge -> {
            exact(edge, "id", "source", "target", "label");
            string(edge.path("id"));
            string(edge.path("source"));
            string(edge.path("target"));
            nullable(edge, "label", AutomationResponseCodec::string);
        });
    }

    private static void cursor(JsonNode value) {
        if (!value.isNull()) {
            String text = string(value);
            require(!text.isEmpty() && text.length() <= 2048);
        }
    }

    static void exact(JsonNode node, String... fields) { exact(node, Set.of(fields)); }

    private static void exact(JsonNode node, Set<String> fields) {
        require(node.isObject() && fields.equals(Set.copyOf(node.propertyNames())));
    }

    static JsonNode array(JsonNode node, int limit) {
        require(node.isArray() && node.size() <= limit);
        return node;
    }

    static void nullable(JsonNode node, String name, Consumer<JsonNode> validator) {
        JsonNode field = node.path(name);
        require(!field.isMissingNode());
        if (!field.isNull()) validator.accept(field);
    }

    private static void nullablePattern(JsonNode node, String name, Pattern pattern) {
        nullable(node, name, field -> checked(field, pattern));
    }

    static String string(JsonNode node) {
        require(node.isString());
        String value = node.asString();
        require(StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.indexOf('\0') < 0);
        return value;
    }

    private static String checked(JsonNode node, Pattern pattern) {
        String text = string(node);
        require(pattern.matcher(text).matches());
        return text;
    }

    private static String nonBlank(JsonNode node, int maxLength) {
        String value = string(node);
        require(!value.isBlank() && value.length() <= maxLength);
        return value;
    }

    static long exactInteger(JsonNode node, long min) {
        require(node.isIntegralNumber() && node.canConvertToLong());
        long value = node.asLong();
        require(value >= min && value <= ProcessTypedJson.V2_MAX_WIRE_INTEGER);
        return value;
    }

    static Instant utc(JsonNode node) { return Instant.parse(checked(node, UTC_MICROS)); }

    static JsonNode tree(byte[] body) {
        require(body != null && body.length > 0 && body.length <= MAX_RESPONSE_BYTES);
        try {
            JsonNode root = MAPPER.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString());
            require(root != null && root.isObject());
            return root;
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    static ProcessClientException invalidResponse(int status) {
        return new ProcessClientException(status, "TIANJI_INVALID_RESPONSE", null, null);
    }

    static void require(boolean accepted) { if (!accepted) throw invalid(); }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Automation response is invalid"); }
}
