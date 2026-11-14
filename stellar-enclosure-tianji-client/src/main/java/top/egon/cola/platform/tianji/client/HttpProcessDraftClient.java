package top.egon.cola.platform.tianji.client;

import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.MAX_DRAFT_REQUEST_BYTES;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.MAX_DRAFT_RESPONSE_BYTES;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.MAX_STANDARD_RESPONSE_BYTES;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.V2;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.commandId;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.definitionKey;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.releaseVersion;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.require;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.revision;

/** Owner-scoped draft operations and publication of an exact saved revision. */
public final class HttpProcessDraftClient implements ProcessDraftClient, AutoCloseable {
    private static final String DRAFTS = V2 + "process-drafts";
    private static final int COMMAND_REQUEST_LIMIT = 4096;
    private static final Set<String> DRAFT_VERSION_CONFLICT = Set.of("TIANJI_DRAFT_VERSION_CONFLICT");
    private static final Set<String> DRAFT_MUTATION_CONFLICT = Set.of("TIANJI_DRAFT_VERSION_CONFLICT", "TIANJI_DRAFT_STATE_CONFLICT");
    private static final Set<String> DRAFT_SOURCE_CONFLICT = Set.of("TIANJI_DRAFT_SOURCE_UNAVAILABLE");
    private static final Set<String> DRAFT_PUBLISH_CONFLICT = Set.of("TIANJI_DRAFT_VERSION_CONFLICT", "TIANJI_DRAFT_STATE_CONFLICT",
            "TIANJI_DRAFT_SOURCE_UNAVAILABLE");
    private static final Set<String> STATES = Set.of("EDITING", "PUBLISHED", "DISCARDED");
    private static final Comparator<DraftSummaryDTO> DRAFT_ORDER = Comparator.comparing(DraftSummaryDTO::getCreatedAt)
            .thenComparing(DraftSummaryDTO::getDraftId).reversed();
    private final AutomationHttpTransport transport;

    public HttpProcessDraftClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, null);
    }

    public HttpProcessDraftClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout, Long tenantId) {
        this.transport = new AutomationHttpTransport(baseUri, http, tokens, timeout, tenantId);
    }

    @Override public DraftSummaryDTO create(String definitionKey, CreateProcessDraftCommand command) {
        definitionKey(definitionKey);
        require(command != null);
        var request = new CreateProcessDraftCommand(command.getTitle(), command.getBaseReleaseVersion(), command.getCommandId());
        title(request.getTitle());
        commandId(request.getCommandId());
        if (request.getBaseReleaseVersion() != null) releaseVersion(request.getBaseReleaseVersion());
        var response = transport.sendJson("POST", V2 + "process-definitions/" + AutomationHttpTransport.segment(definitionKey) + "/drafts",
                Map.of(), request, COMMAND_REQUEST_LIMIT, 200, MAX_STANDARD_RESPONSE_BYTES, DRAFT_SOURCE_CONFLICT);
        return AutomationResponseCodec.summary(response, value -> definitionKey.equals(value.getDefinitionKey())
                && request.getTitle().equals(value.getTitle()) && Objects.equals(request.getBaseReleaseVersion(), value.getBaseReleaseVersion())
                && value.getState() == DraftSummaryDTO.StateEnum.EDITING && value.getRevision() == 1);
    }

    @Override public ProcessDraftDTO get(String draftId, String commandId) {
        if (commandId != null) commandId(commandId);
        var response = transport.send("GET", draftPath(draftId), commandId == null ? Map.of() : Map.of("commandId", commandId),
                null, 0, 200, MAX_DRAFT_RESPONSE_BYTES, Set.of());
        return AutomationResponseCodec.draft(response, value -> draftId.equals(value.getDraftId())
                && (value.getCommandReceipt() == null || value.getCommandReceipt().getCommandId().equals(commandId)));
    }

    @Override public DraftMutationResultDTO save(String draftId, SaveProcessDraftCommand command) {
        require(command != null);
        var request = new SaveProcessDraftCommand(command.getCommandId(), command.getExpectedRevision(), command.getTitle(), command.getContent());
        commandId(request.getCommandId());
        revision(request.getExpectedRevision(), 1);
        title(request.getTitle());
        require(request.getContent() != null);
        var response = transport.sendJson("PUT", draftPath(draftId), Map.of(), request,
                MAX_DRAFT_REQUEST_BYTES, 200, MAX_STANDARD_RESPONSE_BYTES, DRAFT_MUTATION_CONFLICT);
        return mutation(response, draftId, request.getCommandId(), request.getExpectedRevision(), DraftMutationResultDTO.StateEnum.EDITING);
    }

    @Override public ProcessDraftPageDTO list(String definitionKey, String state, Integer limit, String cursor, String creationCommandId) {
        var filters = new LinkedHashMap<String, String>();
        if (creationCommandId != null) {
            require(definitionKey == null && state == null && cursor == null);
            commandId(creationCommandId);
            filters.put("creationCommandId", creationCommandId);
        }
        if (definitionKey != null) {
            definitionKey(definitionKey);
            filters.put("definitionKey", definitionKey);
        }
        if (state != null) {
            require(STATES.contains(state));
            filters.put("state", state);
        }
        if (limit != null) {
            require(limit >= 1 && limit <= 100);
            filters.put("limit", limit.toString());
        }
        if (cursor != null) filters.put("cursor", cursor);
        var response = transport.send("GET", DRAFTS, filters, null, 0, 200, MAX_STANDARD_RESPONSE_BYTES, Set.of());
        return AutomationResponseCodec.drafts(response, page -> {
            if (creationCommandId != null) return page.getItems().size() <= 1 && page.getNextCursor() == null;
            if (page.getItems().size() > (limit == null ? 20 : limit)) return false;
            String expectedState = state == null ? "EDITING" : state;
            var seen = new HashSet<String>();
            DraftSummaryDTO previous = null;
            for (DraftSummaryDTO item : page.getItems()) {
                if (!seen.add(item.getDraftId()) || definitionKey != null && !definitionKey.equals(item.getDefinitionKey())
                        || !expectedState.equals(item.getState().name()) || previous != null && DRAFT_ORDER.compare(previous, item) >= 0) return false;
                previous = item;
            }
            return true;
        });
    }

    @Override public DraftMutationResultDTO discard(String draftId, DiscardProcessDraftCommand command) {
        require(command != null);
        var request = new DiscardProcessDraftCommand(command.getCommandId(), command.getExpectedRevision());
        commandId(request.getCommandId());
        revision(request.getExpectedRevision(), 1);
        var response = transport.sendJson("POST", draftPath(draftId) + "/discard-commands", Map.of(), request,
                COMMAND_REQUEST_LIMIT, 200, MAX_STANDARD_RESPONSE_BYTES, DRAFT_MUTATION_CONFLICT);
        return mutation(response, draftId, request.getCommandId(), request.getExpectedRevision(), DraftMutationResultDTO.StateEnum.DISCARDED);
    }

    @Override public DraftValidationResultDTO validate(ValidateProcessDraftQuery query) {
        require(query != null);
        var request = new ValidateProcessDraftQuery(query.getDraftId(), query.getExpectedRevision(), query.getExpectedContentSha256());
        draftId(request.getDraftId());
        revision(request.getExpectedRevision(), 1);
        digest(request.getExpectedContentSha256());
        var response = transport.sendJson("POST", V2 + "process-model-validations", Map.of(), request,
                COMMAND_REQUEST_LIMIT, 200, MAX_STANDARD_RESPONSE_BYTES, DRAFT_VERSION_CONFLICT);
        return AutomationResponseCodec.validation(response, value -> request.getDraftId().equals(value.getDraftId())
                && request.getExpectedRevision() == value.getRevision() && request.getExpectedContentSha256().equals(value.getContentSha256()));
    }

    @Override public DraftPublicationVO publish(PublishProcessDraftCommand command) {
        require(command != null);
        var request = new PublishProcessDraftCommand(command.getDraftId(), command.getExpectedRevision(), command.getExpectedContentSha256(),
                command.getReleaseVersion(), command.getCommandId());
        draftId(request.getDraftId());
        revision(request.getExpectedRevision(), 1);
        digest(request.getExpectedContentSha256());
        releaseVersion(request.getReleaseVersion());
        commandId(request.getCommandId());
        var response = transport.sendJson("POST", V2 + "process-releases", Map.of(), request,
                COMMAND_REQUEST_LIMIT, 200, MAX_STANDARD_RESPONSE_BYTES, DRAFT_PUBLISH_CONFLICT);
        return AutomationResponseCodec.publication(response, value -> request.getDraftId().equals(value.draftId())
                && request.getExpectedRevision() == value.publishedRevision() && request.getReleaseVersion().equals(value.releaseVersion())
                && request.getCommandId().equals(value.commandId()));
    }

    private static DraftMutationResultDTO mutation(AutomationHttpTransport.ResponseVO response, String draftId, String commandId,
                                                    long expectedRevision, DraftMutationResultDTO.StateEnum state) {
        return AutomationResponseCodec.mutation(response, value -> draftId.equals(value.getDraftId())
                && commandId.equals(value.getCommandId()) && expectedRevision + 1 == value.getRevision() && state == value.getState());
    }

    private static String draftPath(String draftId) {
        draftId(draftId);
        return DRAFTS + "/" + AutomationHttpTransport.segment(draftId);
    }

    private static void draftId(String input) { require(input != null && input.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")); }
    private static void digest(String input) { require(input != null && input.matches("[0-9a-f]{64}")); }
    private static void title(String input) {
        require(input != null && !input.isBlank() && input.matches("[^\\p{Cc}]{1,128}")
                && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(input));
    }

    @Override public void close() { transport.close(); }
}
