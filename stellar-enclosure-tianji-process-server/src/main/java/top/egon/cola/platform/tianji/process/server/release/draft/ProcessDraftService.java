package top.egon.cola.platform.tianji.process.server.release.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftCommandReceiptDTO.OperationEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore.ReadLockEnum;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_DRAFT_STATE_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_DRAFT_VERSION_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec.contentHash;

/** Owner-scoped draft commands and queries on the verified Process database. */
@Slf4j
@Validated
@Service("processDraftService")
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessDraftService {
    public static final String DEFINITION_KEY = "[A-Za-z_][A-Za-z0-9_.-]{0,127}";
    public static final String DRAFT_ID = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";
    public static final String COMMAND_ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    private static final String RELEASE_VERSION = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final String DRAFT_ID_INTERNAL = "pd[A-Za-z0-9_.:-]{0,126}";
    private static final List<String> EDIT_ACTIONS = List.of("SAVE", "VALIDATE", "PUBLISH", "DISCARD");
    private static final DraftContentVO EMPTY = new DraftContentVO(null, null, null, null, null);

    @Qualifier("processDraftStore") private final ProcessDraftStore drafts;
    @Qualifier("processDraftCommandReceiptStore") private final ProcessDraftCommandReceiptStore receipts;
    @Qualifier("processDraftContentCodec") private final ProcessDraftContentCodec codec;
    @Qualifier("processDraftConverter") private final ProcessDraftConverter converter;
    @Qualifier("processApiSchemaVerifier") private final ProcessApiSchemaVerifier verifier;
    @Qualifier("processDraftTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("environment") private final Environment environment;

    public DraftSummaryDTO create(@NotNull ProcessActor owner, @NotNull @Pattern(regexp = DEFINITION_KEY) String definitionKey,
                                  @NotNull @Valid CreateProcessDraftCommand command) {
        if (command == null || !matches(definitionKey, DEFINITION_KEY) || !validTitle(command.getTitle())
                || !matches(command.getCommandId(), COMMAND_ID)
                || (command.getBaseReleaseVersion() != null && !matches(command.getBaseReleaseVersion(), RELEASE_VERSION))) throw invalid();
        String title = command.getTitle();
        String base = command.getBaseReleaseVersion();
        String commandId = command.getCommandId();
        String hash = requestHash(OperationEnum.CREATE, definitionKey, commandId,
                ProcessTypedJson.canonicalBytes(java.util.Arrays.asList(title, base)));
        verify(owner);
        return command(() -> {
            var prior = receipts.find(owner, commandId);
            if (prior.isPresent()) return replayCreate(owner, definitionKey, title, base, hash, prior.get());
            requireAdmission();
            if (base != null) throw new ProcessResourceNotFoundException();
            byte[] bytes = codec.canonicalContent(EMPTY);
            Instant time = drafts.now();
            var created = ProcessDraftBO.builder().owner(owner).draftId("pd-" + UUID.randomUUID()).definitionKey(definitionKey)
                    .title(title).state(StateEnum.EDITING).revision(1).content(EMPTY).contentSha256(contentHash(bytes))
                    .lastCommandId(commandId).createdAt(time).updatedAt(time).build();
            drafts.insert(created, new String(bytes, StandardCharsets.UTF_8));
            insertReceipt(owner, OperationEnum.CREATE, commandId, hash, created, time);
            return summary(created, StateEnum.EDITING);
        }, () -> replayCreate(owner, definitionKey, title, base, hash, winning(owner, commandId)));
    }

    public ProcessDraftDTO get(@NotNull ProcessActor owner, @NotNull @Pattern(regexp = DRAFT_ID) String draftId,
                               @Pattern(regexp = COMMAND_ID) String commandId) {
        checkDraftId(draftId);
        if (commandId != null && !matches(commandId, COMMAND_ID)) throw invalid();
        verify(owner);
        return inTransaction(() -> {
            var draft = owned(owner, draftId, commandId == null ? ReadLockEnum.NONE : ReadLockEnum.SHARE);
            var receipt = commandId == null ? null : receipts.find(owner, commandId).filter(original -> draftId.equals(original.getDraftId()))
                    .map(converter::toReceipt).orElse(null);
            var projection = converter.toTarget(draft).setCommandReceipt(receipt).setAvailableActions(actions(draft.getState()));
            if (draft.getState() == StateEnum.PUBLISHED) {
                try {
                    projection.setPublication(new DraftPublicationVO(draftId, draft.getPublishedRevision(), draft.getRevision(),
                            draft.getPublishedReleaseVersion(), draft.getPublishedSourceSha256(), draft.getPublishedCompiledSha256(),
                            draft.getPublishedDependencySha256(), draft.getPublishedCompilerVersion(), draft.getPublishedAt(), draft.getPublishCommandId()));
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalStateException("Stored draft publication evidence is invalid");
                }
            }
            if (!projection.isDraftProjectionConsistent()) throw new IllegalStateException("Stored draft projection is invalid");
            return projection;
        });
    }

    public DraftMutationResultDTO save(@NotNull ProcessActor owner, @NotNull @Pattern(regexp = DRAFT_ID) String draftId,
                                       @NotNull @Valid SaveProcessDraftCommand command) {
        checkDraftId(draftId);
        if (command == null || !validTitle(command.getTitle()) || command.getContent() == null
                || !matches(command.getCommandId(), COMMAND_ID) || !wireRevision(command.getExpectedRevision())) throw invalid();
        String title = command.getTitle();
        byte[] bytes = codec.canonicalContent(command.getContent());
        long expected = command.getExpectedRevision();
        String contentSha = contentHash(bytes);
        String commandId = command.getCommandId();
        var mutation = new MutationVO(OperationEnum.SAVE, commandId, expected, title,
                new String(bytes, StandardCharsets.UTF_8), contentSha,
                requestHash(OperationEnum.SAVE, draftId, commandId, ProcessTypedJson.canonicalBytes(List.of(expected, title)), bytes));
        return mutate(owner, draftId, mutation);
    }

    public DraftMutationResultDTO discard(@NotNull ProcessActor owner, @NotNull @Pattern(regexp = DRAFT_ID) String draftId,
                                          @NotNull @Valid DiscardProcessDraftCommand command) {
        checkDraftId(draftId);
        if (command == null || !matches(command.getCommandId(), COMMAND_ID) || !wireRevision(command.getExpectedRevision())) throw invalid();
        long expected = command.getExpectedRevision();
        String commandId = command.getCommandId();
        return mutate(owner, draftId, new MutationVO(OperationEnum.DISCARD, commandId, expected, null, null, null,
                requestHash(OperationEnum.DISCARD, draftId, commandId, utf8(Long.toString(expected)))));
    }

    private DraftMutationResultDTO mutate(ProcessActor owner, String draftId, MutationVO mutation) {
        verify(owner);
        return command(() -> {
            // Establish ownership before consulting the owner-wide command key, even for historical replays.
            var draft = owned(owner, draftId, ReadLockEnum.UPDATE);
            var prior = receipts.find(owner, mutation.commandId());
            if (prior.isPresent()) return replayMutation(draftId, mutation, prior.get());
            requireAdmission();
            requireCurrent(draft, mutation.expected());
            Instant time = drafts.now();
            if (time.isBefore(draft.getUpdatedAt())) throw new IllegalStateException("Process database clock moved before the draft revision");
            boolean save = mutation.operation() == OperationEnum.SAVE;
            int written = save
                    ? drafts.save(owner, draftId, mutation.expected(), mutation.title(), mutation.canonicalContent(),
                            mutation.contentSha(), mutation.commandId(), time)
                    : drafts.discard(owner, draftId, mutation.expected(), mutation.commandId(), time);
            if (written != 1) throw new AutomationOperationException(TIANJI_DRAFT_VERSION_CONFLICT);
            draft.setRevision(mutation.expected() + 1);
            if (save) draft.setContentSha256(mutation.contentSha());
            var saved = insertReceipt(owner, mutation.operation(), mutation.commandId(), mutation.requestSha(), draft, time);
            return mutationResult(saved);
        }, () -> {
            owned(owner, draftId, ReadLockEnum.NONE);
            return replayMutation(draftId, mutation, winning(owner, mutation.commandId()));
        });
    }

    public ProcessDraftPageDTO list(@NotNull ProcessActor owner, @Pattern(regexp = DEFINITION_KEY) String definitionKey,
                                    @Pattern(regexp = "EDITING|PUBLISHED|DISCARDED") String state,
                                    @Min(1) @Max(100) Integer limit, @Size(min = 1, max = 2048) String cursor,
                                    @Pattern(regexp = COMMAND_ID) String creationCommandId) {
        if (definitionKey != null && !matches(definitionKey, DEFINITION_KEY)
                || state != null && !matches(state, "EDITING|PUBLISHED|DISCARDED")
                || limit != null && (limit < 1 || limit > 100)
                || creationCommandId != null && (!matches(creationCommandId, COMMAND_ID)
                || definitionKey != null || state != null || cursor != null)) throw invalid();
        int size = limit == null ? 20 : limit;
        StateEnum effectiveState = state == null ? StateEnum.EDITING : StateEnum.valueOf(state);
        verify(owner);
        var boundary = cursor == null ? null : decodeCursor(owner, definitionKey, effectiveState, cursor);
        return inTransaction(() -> {
            Instant observedAt = drafts.now();
            if (creationCommandId != null) {
                var summary = receipts.find(owner, creationCommandId).filter(original -> original.getOperation() == OperationEnum.CREATE)
                        .flatMap(original -> drafts.summary(owner, original.getDraftId())).map(draft -> summary(draft, draft.getState()));
                return new ProcessDraftPageDTO(observedAt, summary.stream().toList(), null);
            }
            if (boundary != null && boundary.ceiling().isAfter(observedAt)) throw invalid();
            Instant ceiling = boundary == null ? observedAt : boundary.ceiling();
            var rows = drafts.list(owner, definitionKey, effectiveState, ceiling, boundary == null ? null : boundary.createdAt(),
                    boundary == null ? null : boundary.draftId(), size + 1);
            var items = rows.stream().limit(size).map(row -> summary(row, row.getState())).toList();
            String next = null;
            if (rows.size() > size) {
                var last = rows.get(size - 1);
                next = encodeCursor(owner, definitionKey, effectiveState, new DraftCursorVO(ceiling, last.getCreatedAt(), last.getDraftId()));
            }
            return new ProcessDraftPageDTO(observedAt, items, next);
        });
    }

    private DraftSummaryDTO replayCreate(ProcessActor owner, String definitionKey, String title, String base, String hash,
                                         ProcessDraftCommandReceiptBO prior) {
        if (prior.getOperation() != OperationEnum.CREATE || !hash.equals(prior.getRequestSha256())) throw new ProcessConflictException();
        var current = drafts.summary(owner, prior.getDraftId()).orElseThrow(
                () -> new IllegalStateException("Original draft command has no owned draft"));
        if (!definitionKey.equals(current.getDefinitionKey()) || !Objects.equals(base, current.getBaseReleaseVersion())
                || prior.getResultRevision() != 1 || !prior.getCompletedAt().equals(current.getCreatedAt())) {
            throw new IllegalStateException("Original draft command evidence is inconsistent");
        }
        var historical = converter.toSummary(current).setTitle(title).setState(StateEnum.EDITING)
                .setRevision(prior.getResultRevision()).setContentSha256(prior.getResultContentSha256())
                .setPublishedReleaseVersion(null).setUpdatedAt(prior.getCompletedAt());
        return historical.setAvailableActions(actions(current.getState()));
    }

    private static DraftMutationResultDTO replayMutation(String draftId, MutationVO mutation, ProcessDraftCommandReceiptBO prior) {
        if (prior.getOperation() != mutation.operation() || !draftId.equals(prior.getDraftId())
                || !mutation.requestSha().equals(prior.getRequestSha256())) throw new ProcessConflictException();
        if (prior.getResultRevision() != mutation.expected() + 1) throw new IllegalStateException("Stored draft command revision is invalid");
        return mutationResult(prior);
    }

    private static DraftMutationResultDTO mutationResult(ProcessDraftCommandReceiptBO receipt) {
        var state = receipt.getOperation() == OperationEnum.SAVE
                ? DraftMutationResultDTO.StateEnum.EDITING : DraftMutationResultDTO.StateEnum.DISCARDED;
        return new DraftMutationResultDTO(receipt.getDraftId(), state, receipt.getResultRevision(), receipt.getResultContentSha256(),
                receipt.getCompletedAt(), receipt.getCommandId());
    }

    private ProcessDraftCommandReceiptBO insertReceipt(ProcessActor owner, OperationEnum operation, String commandId, String requestHash,
                                                       ProcessDraftBO draft, Instant time) {
        var receipt = ProcessDraftCommandReceiptBO.builder().actor(owner).operation(operation).commandId(commandId)
                .draftId(draft.getDraftId()).requestSha256(requestHash).resultRevision(draft.getRevision())
                .resultContentSha256(draft.getContentSha256()).completedAt(time).build();
        if (!receipts.insertOriginal(receipt)) throw new LostCommandRaceException();
        return receipt;
    }

    private ProcessDraftCommandReceiptBO winning(ProcessActor owner, String commandId) {
        return receipts.find(owner, commandId).orElseThrow(() -> new IllegalStateException("Original draft command is unavailable"));
    }

    private ProcessDraftBO owned(ProcessActor owner, String draftId, ReadLockEnum lock) {
        var draft = drafts.get(owner, draftId, lock).orElseThrow(ProcessResourceNotFoundException::new);
        if (!contentHash(codec.canonicalContent(draft.getContent())).equals(draft.getContentSha256())) {
            throw new IllegalStateException("Stored draft content digest does not match");
        }
        return draft;
    }

    private static void requireCurrent(ProcessDraftBO draft, long expected) {
        if (draft.getState() != StateEnum.EDITING) throw new AutomationOperationException(TIANJI_DRAFT_STATE_CONFLICT);
        if (draft.getRevision() != expected || expected == ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
            throw new AutomationOperationException(TIANJI_DRAFT_VERSION_CONFLICT);
        }
    }

    private void verify(ProcessActor owner) {
        if (owner == null || !matches(owner.issuerFingerprint(), "[0-9a-f]{64}")) {
            throw new AccessDeniedException("Verified draft owner is required");
        }
        verifier.verifyAutomationV14();
    }

    private void requireAdmission() {
        if (!enabled()) throw new AccessDeniedException("Draft write admission is disabled");
    }

    private boolean enabled() {
        return environment.getProperty(ENABLED, Boolean.class, false);
    }

    private DraftSummaryDTO summary(ProcessDraftBO draft, StateEnum state) {
        return converter.toSummary(draft).setAvailableActions(actions(state));
    }

    private List<String> actions(StateEnum state) {
        if (state != StateEnum.EDITING) return List.of();
        return enabled() ? EDIT_ACTIONS : List.of("VALIDATE");
    }

    private <T> T command(Supplier<T> execute, Supplier<T> replay) {
        try {
            return inTransaction(execute);
        } catch (LostCommandRaceException lost) {
            return inTransaction(replay);
        }
    }

    private <T> T inTransaction(Supplier<T> execute) {
        return Objects.requireNonNull(transaction.execute(status -> execute.get()));
    }

    private static String requestHash(OperationEnum operation, String path, String commandId, byte[]... body) {
        byte[][] segments = new byte[body.length + 3][];
        segments[0] = utf8(operation.name());
        segments[1] = utf8(path);
        segments[2] = utf8(commandId);
        System.arraycopy(body, 0, segments, 3, body.length);
        return ProcessTypedJson.sha256("AUTOMATION_DRAFT_REQUEST_V2", segments);
    }

    private static String encodeCursor(ProcessActor owner, String definitionKey, StateEnum state, DraftCursorVO boundary) {
        String data = boundary.ceiling() + "\n" + boundary.createdAt() + "\n" + boundary.draftId();
        String encoded = data + "\n" + cursorHash(owner, definitionKey, state, data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(utf8(encoded));
    }

    private static DraftCursorVO decodeCursor(ProcessActor owner, String definitionKey, StateEnum state, String token) {
        try {
            if (!matches(token, "[A-Za-z0-9_-]{1,2048}")) throw invalid();
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(token)) throw invalid();
            String[] fields = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(decoded)).toString().split("\n", -1);
            if (fields.length != 4 || !matches(fields[2], DRAFT_ID_INTERNAL)) throw invalid();
            Instant ceiling = cursorTime(fields[0]);
            Instant createdAt = cursorTime(fields[1]);
            if (createdAt.isAfter(ceiling) || !cursorHash(owner, definitionKey, state, String.join("\n", fields[0], fields[1], fields[2]))
                    .equals(fields[3])) throw invalid();
            return new DraftCursorVO(ceiling, createdAt, fields[2]);
        } catch (RuntimeException | CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static Instant cursorTime(String value) {
        Instant instant = Instant.parse(value);
        if (!instant.toString().equals(value) || instant.getNano() % 1_000 != 0 || instant.isBefore(Instant.EPOCH)) throw invalid();
        return instant;
    }

    private static String cursorHash(ProcessActor owner, String definitionKey, StateEnum state, String data) {
        return ProcessTypedJson.sha256("AUTOMATION_DRAFT_CURSOR_V2", utf8(Long.toString(owner.tenantId())), utf8(owner.subjectType().name()),
                utf8(owner.issuerFingerprint()), utf8(owner.subjectId()), utf8(definitionKey == null ? "" : definitionKey),
                utf8(state.name()), utf8(data));
    }

    private static boolean validTitle(String title) {
        return matches(title, "[^\\p{Cc}]{1,128}") && !title.isBlank() && StandardCharsets.UTF_8.newEncoder().canEncode(title);
    }

    private static boolean wireRevision(long value) {
        return value >= 1 && value <= ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    }

    private static void checkDraftId(String draftId) {
        if (!matches(draftId, DRAFT_ID)) throw invalid();
    }

    private static boolean matches(String value, String regex) {
        return value != null && value.matches(regex);
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid process draft command"); }

    private record DraftCursorVO(Instant ceiling, Instant createdAt, String draftId) { }
    private record MutationVO(OperationEnum operation, String commandId, long expected, String title, String canonicalContent,
                              String contentSha, String requestSha) { }
    private static final class LostCommandRaceException extends RuntimeException { }
}
