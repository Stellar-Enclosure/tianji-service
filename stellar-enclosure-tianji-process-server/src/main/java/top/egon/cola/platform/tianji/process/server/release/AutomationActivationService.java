package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.Purpose;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustObservationVO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson.V2_MAX_WIRE_INTEGER;
import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.*;

@Slf4j
@Lazy
@Service("automationActivationService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AutomationActivationService {
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final String CURSOR = "[A-Za-z0-9_-]{1,2048}";

    @Qualifier("automationActivationStore") private final AutomationActivationStore store;
    @Qualifier("automationReleaseStore") private final AutomationReleaseStore automation;
    @Qualifier("processReleaseService") private final ProcessReleaseService releases;
    @Qualifier("automationActivationConverter") private final AutomationActivationConverter converter;
    @Qualifier("processApiSchemaVerifier") private final ProcessApiSchemaVerifier verifier;
    @Qualifier("processDraftTransactionTemplate") private final TransactionTemplate transaction;
    @Qualifier("environment") private final Environment environment;
    @Qualifier("automationAdmissionAuthority") private final ObjectProvider<AutomationAdmissionAuthority> authority;
    @Qualifier("automationReleaseTrustAuthority") private final ObjectProvider<TrustAuthority> trustAuthority;

    public AutomationActivationDTO activate(@NotNull ProcessActor actor, @NotNull @Pattern(regexp = DEFINITION_KEY) String definitionKey,
                                            @NotNull @Valid ActivationCommand command) {
        verify(actor, true);
        if (!matches(definitionKey, DEFINITION_KEY) || command == null || !matches(command.getReleaseVersion(), RELEASE_VERSION)
                || !matches(command.getCommandId(), COMMAND_ID) || command.getExpectedActivationRevision() == null
                || command.getExpectedActivationRevision() < 0 || command.getExpectedActivationRevision() > V2_MAX_WIRE_INTEGER) {
            throw invalid();
        }
        var intent = new ActivationIntentVO(actor, definitionKey, command.getReleaseVersion(), command.getExpectedActivationRevision(),
                command.getCommandId());
        var original = original(intent);
        if (original != null) return replay(intent, original);

        final PreparedAdmissionVO prepared;
        try {
            requireNewActivationEnabled();
            prepared = prepare(intent);
        } catch (RuntimeException failedPreparation) {
            original = original(intent);
            if (original != null) return replay(intent, original);
            throw failedPreparation;
        }

        WriteResultVO result = Objects.requireNonNull(transaction.execute(status -> write(intent, prepared)));
        return result.replay() ? replay(intent, result.row()) : receipt(result.row());
    }

    public ActivationPageDTO list(@NotNull ProcessActor actor, @NotNull @Pattern(regexp = DEFINITION_KEY) String definitionKey,
                                  @Pattern(regexp = COMMAND_ID) String commandId, @Min(1) @Max(100) Integer limit,
                                  @Size(min = 1, max = 2048) String cursor) {
        verify(actor, false);
        int pageSize = limit == null ? 20 : limit;
        if (!matches(definitionKey, DEFINITION_KEY) || pageSize < 1 || pageSize > 100
                || commandId != null && (!matches(commandId, COMMAND_ID) || cursor != null)) throw invalid();
        var boundary = cursor == null ? null : decodeCursor(actor.tenantId(), definitionKey, cursor);
        return Objects.requireNonNull(readSnapshot().execute(status -> readPage(actor, definitionKey, commandId, pageSize, boundary)));
    }

    private void verify(ProcessActor actor, boolean requiresUser) {
        if (actor == null || !matches(actor.issuerFingerprint(), SHA256)
                || requiresUser && actor.subjectType() != ProcessActor.SubjectType.USER) {
            throw new AccessDeniedException("Verified automation actor is required");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw unavailable();
        }
        verifier.verifyAutomationV14();
    }

    private PreparedAdmissionVO prepare(ActivationIntentVO intent) {
        var descriptor = requireTarget(intent);
        var query = query(Purpose.ACTIVATE, intent, descriptor, intent.expected());
        String querySha = query.sha256();
        var proof = observe(query, querySha);
        try {
            var trusted = trustAuthority.getIfAvailable();
            if (trusted == null) throw unavailable();
            var observation = trusted.observe(query, descriptor);
            if (!querySha.equals(query.sha256())) throw unavailable();
            AutomationReleaseTrustVerifier.verify(descriptor, query, proof, observation);
            return new PreparedAdmissionVO(descriptor, observation, proof);
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private WriteResultVO write(ActivationIntentVO intent, PreparedAdmissionVO prepared) {
        store.lockTenant(intent.actor().tenantId());
        var winner = original(intent);
        if (winner != null) return new WriteResultVO(winner, true);

        requireNewActivationEnabled();
        long current = store.current(intent.actor().tenantId(), intent.definition())
                .map(AutomationActivationBO::getRevision).orElse(0L);
        if (current != intent.expected() || current >= V2_MAX_WIRE_INTEGER) throw new ProcessVersionConflictException();
        var inspected = requireTarget(intent);
        if (!samePinnedRelease(prepared.release(), inspected)) throw unavailable();
        releases.requireVerifiedAutomation(inspected);
        var databaseNow = store.now();
        var proof = prepared.proof();
        proof.requireCurrent(query(Purpose.ACTIVATE, intent, inspected, current).sha256(), databaseNow);
        AutomationReleaseTrustVerifier.requireWindow(proof, prepared.observation(), databaseNow);

        var row = new AutomationActivationBO().setTenantId(inspected.tenantId()).setDefinitionKey(inspected.rootDefinitionKey())
                .setRevision(current + 1).setPreviousRevision(current == 0 ? null : current).setReleaseVersion(inspected.releaseVersion())
                .setActor(intent.actor()).setCommandId(intent.command()).setRequestSha256(intent.sha256())
                .setSourceManifestSha256(inspected.sourceSha256()).setCompiledManifestSha256(inspected.compiledSha256())
                .setDependencyManifestSha256(inspected.dependencySha256()).setProof(proof).setActivatedAt(databaseNow);
        store.insert(row);
        return new WriteResultVO(row, false);
    }

    private AutomationActivationDTO replay(ActivationIntentVO intent, AutomationActivationBO original) {
        if (!original.getRequestSha256().equals(intent.sha256())) throw new ProcessConflictException();
        var release = automation.find(intent.actor().tenantId(), original.getReleaseVersion()).orElseThrow(AutomationActivationService::unavailable);
        var query = query(Purpose.REPLAY_ACTIVATION, intent, release, original.getRevision());
        String querySha = query.sha256();
        var currentPermission = observe(query, querySha);
        return Objects.requireNonNull(readSnapshot().execute(status -> {
            var archived = original(intent);
            if (!original.equals(archived)) throw unavailable();
            var inspected = automation.find(intent.actor().tenantId(), original.getReleaseVersion())
                    .orElseThrow(AutomationActivationService::unavailable);
            if (!samePinnedRelease(release, inspected)) throw unavailable();
            releases.requireVerifiedAutomation(inspected);
            verifyArchive(intent, archived, inspected);
            currentPermission.requireCurrent(querySha, store.now());
            return receipt(archived);
        }));
    }

    private void verifyArchive(ActivationIntentVO intent, AutomationActivationBO row, ImmutableReleaseVO release) {
        if (!release.sourceSha256().equals(row.getSourceManifestSha256())
                || !release.compiledSha256().equals(row.getCompiledManifestSha256())
                || !release.dependencySha256().equals(row.getDependencyManifestSha256())
                || intent.expected() != row.getExpectedActivationRevision()
                || !AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release))
                        .equals(row.getProof().signedPayloadSha256())) throw unavailable();
    }

    private AutomationAdmissionProofVO observe(AutomationAdmissionQuery query, String querySha) {
        try {
            var host = authority.getIfAvailable();
            if (host == null) throw unavailable();
            var proof = host.observe(query);
            if (proof == null || !querySha.equals(query.sha256())) throw unavailable();
            proof.requireCurrent(querySha, store.now());
            return proof;
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private ActivationPageDTO readPage(ProcessActor actor, String definitionKey, String commandId, int pageSize, CursorVO cursor) {
        var current = store.current(actor.tenantId(), definitionKey).orElse(null);
        var page = new ActivationPageDTO().setDefinitionKey(definitionKey).setObservedAt(store.now()).setItems(List.of());
        if (current == null) {
            if (!store.hasPublishedDefinition(actor.tenantId(), definitionKey)) throw new ProcessResourceNotFoundException();
            if (cursor != null) throw invalid();
            return page;
        }
        page.setCurrent(new ActivationPageDTO.CurrentActivationVO(current.getRevision(), current.getReleaseVersion(),
                current.getActivatedAt()));
        if (commandId != null) {
            return page.setItems(store.findByCommand(actor, definitionKey, commandId).map(this::receipt).stream().toList());
        }

        long highWater = cursor == null ? current.getRevision() : cursor.highWater();
        if (highWater > current.getRevision()) throw invalid();
        long before = cursor == null ? highWater + 1 : cursor.before();
        var rows = store.list(actor.tenantId(), definitionKey, highWater, before, pageSize + 1);
        var results = rows.stream().limit(pageSize).map(this::receipt).toList();
        page.setItems(results);
        if (rows.size() > pageSize) {
            page.setNextCursor(encodeCursor(actor.tenantId(), definitionKey, new CursorVO(highWater, results.getLast().getRevision())));
        }
        return page;
    }

    private AutomationActivationDTO receipt(AutomationActivationBO row) {
        var result = converter.toTarget(row);
        if (!result.isActivationConsistent()) throw unavailable();
        return result;
    }

    private TransactionTemplate readSnapshot() {
        var read = new TransactionTemplate(transaction.getTransactionManager());
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);
        read.setTimeout(transaction.getTimeout());
        return read;
    }

    private ImmutableReleaseVO requireTarget(ActivationIntentVO intent) {
        var release = automation.find(intent.actor().tenantId(), intent.release()).orElseThrow(ProcessResourceNotFoundException::new);
        if (!intent.definition().equals(release.rootDefinitionKey())) throw invalid();
        return release;
    }

    private AutomationActivationBO original(ActivationIntentVO intent) {
        return store.findByCommand(intent.actor(), intent.definition(), intent.command()).orElse(null);
    }

    private void requireNewActivationEnabled() {
        if (!environment.getProperty(ENABLED, Boolean.class, false)) throw new AccessDeniedException("New activation is disabled");
    }

    private static boolean samePinnedRelease(ImmutableReleaseVO expected, ImmutableReleaseVO actual) {
        return expected.tenantId() == actual.tenantId() && expected.rootDefinitionKey().equals(actual.rootDefinitionKey())
                && expected.releaseVersion().equals(actual.releaseVersion()) && expected.sourceSha256().equals(actual.sourceSha256())
                && expected.compiledSha256().equals(actual.compiledSha256()) && expected.compilerVersion().equals(actual.compilerVersion())
                && expected.dependencySha256().equals(actual.dependencySha256()) && expected.createdAt().equals(actual.createdAt())
                && expected.sourceDraftId().equals(actual.sourceDraftId()) && expected.sourceDraftRevision() == actual.sourceDraftRevision()
                && expected.draftContentSha256().equals(actual.draftContentSha256());
    }

    private static AutomationAdmissionQuery query(Purpose purpose, ActivationIntentVO intent, ImmutableReleaseVO release, long revision) {
        return activation(purpose, intent.actor(), intent.definition(), release.releaseVersion(), intent.command(),
                intent.sha256(), revision, release.sourceSha256(), release.compiledSha256(), release.dependencySha256());
    }

    private static String encodeCursor(long tenantId, String definition, CursorVO cursor) {
        String data = cursor.highWater() + "\n" + cursor.before();
        return Base64.getUrlEncoder().withoutPadding().encodeToString((data + "\n" + cursorSha256(tenantId, definition, data))
                .getBytes(StandardCharsets.US_ASCII));
    }

    private static CursorVO decodeCursor(long tenantId, String definition, String cursor) {
        try {
            if (!matches(cursor, CURSOR)) throw invalid();
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            var values = new String(decoded, StandardCharsets.US_ASCII).split("\n", -1);
            if (values.length != 3) throw invalid();
            var boundary = new CursorVO(Long.parseLong(values[0]), Long.parseLong(values[1]));
            if (boundary.before() <= 1 || boundary.highWater() > V2_MAX_WIRE_INTEGER || boundary.before() > boundary.highWater()
                    || !cursor.equals(encodeCursor(tenantId, definition, boundary))) throw invalid();
            return boundary;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    private static String cursorSha256(long tenantId, String definition, String data) {
        return ProcessTypedJson.sha256("AUTOMATION_ACTIVATION_CURSOR_V2", Long.toString(tenantId).getBytes(StandardCharsets.US_ASCII),
                definition.getBytes(StandardCharsets.US_ASCII), data.getBytes(StandardCharsets.US_ASCII));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation activation request");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation activation evidence is unavailable or inconsistent");
    }

    private record ActivationIntentVO(ProcessActor actor, String definition, String release, long expected, String command) {
        String sha256() {
            return activationRequestSha256(actor, definition, release, expected, command);
        }
    }

    private record PreparedAdmissionVO(ImmutableReleaseVO release, TrustObservationVO observation, AutomationAdmissionProofVO proof) { }
    private record WriteResultVO(AutomationActivationBO row, boolean replay) { }
    private record CursorVO(long highWater, long before) { }
}
