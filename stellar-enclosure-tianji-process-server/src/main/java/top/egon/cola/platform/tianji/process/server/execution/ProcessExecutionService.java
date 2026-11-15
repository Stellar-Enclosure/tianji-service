package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand.AgentTaskOverrideVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationBO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.Purpose;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustObservationVO;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseTenantFence;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.event.EventInboxBO;
import top.egon.cola.platform.tianji.process.server.event.EventInboxBO.EventAdmissionVO;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventInboxStore;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustAuthority;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO.ReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessAgentBridgeConfiguration.TypedRouteProvider;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Pointer;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Provenance;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.CorrelationReceipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Internal process entry point; the caller must authenticate and authorize the supplied actor. */
@Slf4j
public final class ProcessExecutionService {
    private static final int CORRELATION_PAGE_SIZE = ProcessInstanceCorrelationPageDTO.PAGE_SIZE;
    private static final Duration MAX_GOVERNED_PROOF_WINDOW = Duration.ofSeconds(30);
    private final SpringProcessEngineConfiguration engine;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final TransactionTemplate receiptRead;
    private final GovernedStartPilotProperties pilots;
    private final GovernedStartAuthority governedAuthority;
    private final GovernedProcessStartStore governed;
    private final TransactionTemplate governedWrite;
    private final AutomationDependenciesVO automation;
    private final JdbcTemplate automationJdbc;
    private final String schema;

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator) {
        this(engine, dmn, validator, null);
    }

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator, GovernedStartAuthority governedAuthority) {
        this(engine, dmn, validator, new GovernedStartPilotProperties(List.of()), governedAuthority);
    }

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator, GovernedStartPilotProperties pilots,
                                   GovernedStartAuthority governedAuthority) {
        this(engine, dmn, validator, pilots, governedAuthority, null);
    }

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator, GovernedStartPilotProperties pilots,
                                   GovernedStartAuthority governedAuthority, AutomationDependenciesVO automation) {
        this.automation = automation;
        this.pilots = Objects.requireNonNull(pilots, "pilots");
        if (!pilots.pilots().isEmpty() && governedAuthority == null) {
            throw new IllegalStateException("Governed Process START pilots require an explicit authority");
        }
        this.engine = Objects.requireNonNull(engine, "engine");
        this.models = new VerifiedProcessModels(engine, dmn, validator);
        this.releases = new ProcessReleaseService(engine, dmn, validator);
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || engine.getRuntimeService() == null || engine.getHistoryService() == null
                || !engine.isDbHistoryUsed() || engine.getHistoryLevel() == null
                || !engine.getHistoryLevel().isAtLeast(HistoryLevel.INSTANCE) || engine.isAsyncHistoryEnabled()) {
            throw new IllegalArgumentException("Process execution requires synchronous instance history and a shared JDBC transaction manager");
        }
        this.automationJdbc = new JdbcTemplate(manager.getDataSource());
        this.schema = engine.getDatabaseSchema();
        this.receipts = new ProcessCommandReceiptStore(automationJdbc, schema);
        this.write = new TransactionTemplate(manager);
        this.read = new TransactionTemplate(manager);
        this.read.setReadOnly(true);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.receiptRead = new TransactionTemplate(manager);
        // Read-only is enforced on the primary by the receipt store, not a replica routing hint.
        this.receiptRead.setReadOnly(false);
        this.receiptRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.receiptRead.setTimeout(2);
        this.governedAuthority = governedAuthority;
        if (governedAuthority == null) {
            this.governed = null;
            this.governedWrite = null;
        } else {
            this.governed = new GovernedProcessStartStore(manager.getDataSource(), engine.getDatabaseSchema());
            this.governedWrite = new TransactionTemplate(manager);
            this.governedWrite.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            this.governedWrite.setTimeout(10);
        }
    }

    /** Allows the host to verify that a replacement execution bean enforces the configured pilots. */
    public boolean enforcesGovernedStartPilots(GovernedStartPilotProperties configured, GovernedStartAuthority authority) {
        return governed != null && pilots.equals(configured) && governedAuthority == authority;
    }

    public Snapshot start(ProcessActor actor, StartCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String channel = pilots.channelFor(actor.tenantId(), command.definitionKey());
        if (channel == null) return Objects.requireNonNull(write.execute(status -> startInTransaction(actor, command)));

        var governedCommand = new GovernedStartCommand(channel, command.definitionKey(), command.businessKey(),
                command.commandId(), command.correlationId(), command.variables());
        return executeGoverned(actor, governedCommand, command.releaseKey()).instance();
    }

    /** Starts a BPMN instance at the active channel revision and pins its provenance with the native instance. */
    public GovernedStartResult startGoverned(ProcessActor actor, GovernedStartCommand command) {
        var result = executeGoverned(actor, command, null);
        var pointer = Objects.requireNonNull(result.pointer());
        return new GovernedStartResult(result.instance(), pointer.channel(), pointer.revision(), pointer.candidateHash());
    }

    private StartOutcome executeGoverned(ProcessActor actor, GovernedStartCommand command, String expectedRelease) {
        if (governed == null) throw unavailable();
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint()) || command == null) throw governedDenied();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Governed Process START requires an independent transaction boundary");
        }
        // The HTTP-shaped command has no channel. An owned provenance row keeps its original scope after a remap.
        var history = expectedRelease == null ? Optional.<Provenance>empty() : governed.find(actor, command.commandId());
        var admitted = history.filter(source -> command.definitionKey().equals(source.pointer().definitionKey()))
                .map(source -> new GovernedStartCommand(source.pointer().channel(), command.definitionKey(),
                        command.businessKey(), command.commandId(), command.correlationId(), command.variables()))
                .orElse(command);
        var query = new StartAuthorizationQuery(actor, admitted.channel(), admitted.definitionKey(), admitted.commandId());
        var grant = governedAuthority.authorize(query);
        if (grant == null || !query.equals(grant.query())) throw governedDenied();
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.now());

        Pointer observed = null;
        WorkerRouteProof proof = null;
        boolean existing = expectedRelease == null ? governed.find(actor, command.commandId()).isPresent()
                : history.isPresent() || governed.hasReceipt(actor, command.commandId());
        if (!existing) {
            observed = governed.current(actor.tenantId(), admitted.channel()).orElse(null);
            if (observed != null && admitted.definitionKey().equals(observed.definitionKey())
                    && (expectedRelease == null || expectedRelease.equals(observed.releaseKey()))) {
                proof = governedAuthority.currentWorker(workerQuery(observed));
            }
        }
        var observedPointer = observed;
        var workerProof = proof;
        return Objects.requireNonNull(governedWrite.execute(status ->
                governedStartInTransaction(actor, admitted, expectedRelease, grant, observedPointer, workerProof, history)));
    }

    public Snapshot get(ProcessActor actor, String instanceId) {
        Objects.requireNonNull(actor, "actor");
        if (!validId(instanceId, 255)) throw invalid();
        return Objects.requireNonNull(read.execute(status -> snapshot(actor, receipts.startByInstance(actor, instanceId))));
    }

    public record AutomationDependenciesVO(AutomationActivationStore activations, AutomationReleaseStore releases,
            ProcessInstanceBindingStore bindings, AutomationInstanceConverter converter, ProcessApiSchemaVerifier verifier,
            Environment environment, ObjectProvider<AutomationAdmissionAuthority> admissionAuthority,
            ObjectProvider<TrustAuthority> trustAuthority, ObjectProvider<AgentTemplateAuthority> agentTemplates,
            ObjectProvider<TypedRouteProvider> typedRoutes) {
        public AutomationDependenciesVO(AutomationActivationStore activations, AutomationReleaseStore releases,
                ProcessInstanceBindingStore bindings, AutomationInstanceConverter converter, ProcessApiSchemaVerifier verifier,
                Environment environment, ObjectProvider<AutomationAdmissionAuthority> admissionAuthority,
                ObjectProvider<TrustAuthority> trustAuthority) {
            this(activations, releases, bindings, converter, verifier, environment, admissionAuthority, trustAuthority, null, null);
        }
    }

    @FunctionalInterface
    public interface AgentTemplateAuthority {
        /** Attests the exact current source, template and any approved configuration against the template. */
        AgentRuntimeTemplateCommand resolve(ProcessActor requester, String sourceId, TemplateRefVO templateRef,
                TemplateRefVO configurationRef);
    }

    public record AutomationObservationPermitVO(boolean ownerRead, boolean observe, boolean dataRead,
                                                boolean diagnosticRead, boolean cancel, boolean dataReadOwnerOnly) {
        public AutomationObservationPermitVO(boolean ownerRead, boolean observe, boolean dataRead,
                                             boolean diagnosticRead, boolean cancel) {
            this(ownerRead, observe, dataRead, diagnosticRead, cancel, false);
        }
    }

    private static final String AUTOMATION_ENABLED = "stellar.tianji.process.automation.enabled";
    private static final int MAX_ROOT_INPUT_BYTES = 64 * 1024;
    private static final JsonMapper NATIVE_JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };
    private static final String EVENT_START_PREFIX = "evt-v2-";

    public AutomationInstanceDTO startAutomation(@NotNull ProcessActor actor, @NotNull @Valid AutomationStartCommand command) {
        if (command == null || !command.isPublicStartValid()) throw invalid();
        requireAutomationActor(actor);
        var intent = new AutomationStartCommand(command.getDefinitionKey(), command.getBusinessKey(), command.getCommandId(),
                command.getCorrelationId(), decodeCanonical(command.getVariables(), MAX_ROOT_INPUT_BYTES),
                Map.copyOf(command.getAgentTaskOverrides()));
        String raw = intent.requestSha256();
        automation.verifier().verifyAutomationV14();
        var existing = receipts.findOwnedStart(actor, intent.getCommandId()).orElse(null);
        if (existing != null) return replayAutomation(actor, intent, raw);

        final PreparedStartVO prepared;
        try {
            requireNewAutomationStart();
            prepared = prepareAutomation(actor, intent, raw);
        } catch (RuntimeException failedPreparation) {
            if (receipts.findOwnedStart(actor, intent.getCommandId()).isPresent()) return replayAutomation(actor, intent, raw);
            throw failedPreparation;
        }

        var projection = write.execute(status -> writeAutomation(actor, intent, raw, prepared));
        return projection == null ? replayAutomation(actor, intent, raw) : projection;
    }

    private PreparedStartVO prepareAutomation(ProcessActor actor, AutomationStartCommand intent, String raw) {
        var pinned = Objects.requireNonNull(read.execute(status -> {
            var activation = automation.activations().current(actor.tenantId(), intent.getDefinitionKey())
                    .orElseThrow(() -> new AutomationOperationException(AutomationOperationException.Code.TIANJI_PROCESS_NOT_ACTIVATED));
            var release = automation.releases().find(actor.tenantId(), activation.getReleaseVersion())
                    .orElseThrow(ProcessExecutionService::automationUnavailable);
            requireActivation(activation, release);
            return new PinnedReleaseVO(activation, release);
        }));
        var release = pinned.release();
        var normalized = release.source().inputSchema().normalizeValue(intent.getVariables());
        var configuration = freezeAgentManifest(actor, release, intent.getAgentTaskOverrides());
        automation.bindings().requireConfigurationFits(configuration);
        String configurationSha = ProcessInstanceBindingStore.configurationSha256(configuration);
        var query = admissionQuery(Purpose.START, actor, release, pinned.activation().getRevision(), intent.getCommandId(), raw, configurationSha);
        var evidence = observeTrustedStart(query, release);
        return new PreparedStartVO(pinned, normalized, configuration, configurationSha, evidence.proof(), evidence.trust());
    }

    private TrustedStartVO observeTrustedStart(AutomationAdmissionQuery query, ImmutableReleaseVO release) {
        String querySha = query.sha256();
        var proof = observeAdmission(query, querySha);
        try {
            var trust = automation.trustAuthority().getIfAvailable();
            if (trust == null) throw automationUnavailable();
            var material = trust.observe(query, release);
            if (!querySha.equals(query.sha256())) throw automationUnavailable();
            AutomationReleaseTrustVerifier.verify(release, query, proof, material);
            return new TrustedStartVO(proof, material);
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException failure) {
            throw automationUnavailable();
        }
    }

    public PreparedEventAdmissionVO prepareEventAdmission(ProcessActor producer, ProcessEventCommand submitted,
                                                           String commandId, String fingerprint) {
        requireAutomationActor(producer);
        requireNewAutomationStart();
        if (producer.subjectType() != ProcessActor.SubjectType.SERVICE || submitted == null || !submitted.isEventEnvelopeValid()
                || !eventStartCommandId(producer, submitted.getEventId()).equals(commandId)
                || !ProcessInstanceBindingStore.sha256(fingerprint)) throw invalid();
        var command = new ProcessEventCommand(submitted.getEventId(), submitted.getDefinitionKey(), submitted.getRouteKey(),
                submitted.getRouteVersion(), submitted.getType(), submitted.getOccurredAt(), submitted.getCorrelationKey(),
                submitted.getWaitToken(), decodeCanonical(submitted.getPayload(), MAX_ROOT_INPUT_BYTES));
        var pinned = Objects.requireNonNull(read.execute(status -> {
            var activation = automation.activations().current(producer.tenantId(), command.getDefinitionKey())
                    .orElseThrow(() -> new AutomationOperationException(AutomationOperationException.Code.TIANJI_PROCESS_NOT_ACTIVATED));
            var release = automation.releases().find(producer.tenantId(), activation.getReleaseVersion())
                    .orElseThrow(ProcessExecutionService::automationUnavailable);
            requireActivation(activation, release);
            return new PinnedReleaseVO(activation, release);
        }));
        var release = pinned.release();
        var route = eventStartRoute(release, command.getRouteKey(), command.getRouteVersion());
        command.validateForRoute(route);
        String routeSha = eventRouteSha(release, route);
        var normalized = map(release.source().inputSchema().normalizeValue(EventInboxBO.mapStartInput(route, command.getPayload())));
        var configuration = freezeAgentManifest(producer, release, Map.of());
        automation.bindings().requireConfigurationFits(configuration);
        String configurationSha = ProcessInstanceBindingStore.configurationSha256(configuration);
        var query = eventAdmissionQuery(Purpose.EVENT_ADMIT, producer, release, pinned.activation().getRevision(),
                commandId, fingerprint, configurationSha, command.getEventId(), routeSha, null);
        var evidence = observeTrustedStart(query, release);
        return new PreparedEventAdmissionVO(producer, command, commandId, fingerprint, pinned.activation(), release, route, routeSha,
                normalized, configuration, configurationSha, evidence.proof(), evidence.trust());
    }

    public EventInboxBO validateEventAdmission(PreparedEventAdmissionVO prepared, String receiptId, Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(automationJdbc.getDataSource())) throw automationUnavailable();
        requireNewAutomationStart();
        var command = prepared.command();
        var producer = prepared.producer();
        var pinned = automation.activations().current(producer.tenantId(), command.getDefinitionKey())
                .orElseThrow(ProcessExecutionService::activationConflict);
        if (!pinned.equals(prepared.activation())) throw activationConflict();
        var release = automation.releases().find(producer.tenantId(), pinned.getReleaseVersion())
                .orElseThrow(ProcessExecutionService::automationUnavailable);
        requireActivation(pinned, release);
        var route = eventStartRoute(release, command.getRouteKey(), command.getRouteVersion());
        if (!route.equals(prepared.route()) || !eventRouteSha(release, route).equals(prepared.routeSha())) throw activationConflict();
        var query = eventAdmissionQuery(Purpose.EVENT_ADMIT, producer, release, pinned.getRevision(), prepared.commandId(),
                prepared.fingerprint(), prepared.configurationSha(), command.getEventId(), prepared.routeSha(), null);
        prepared.proof().requireCurrent(query.sha256(), now);
        AutomationReleaseTrustVerifier.requireWindow(prepared.proof(), prepared.trust(), now);
        Instant expires = now.plus(route.ttl()).truncatedTo(ChronoUnit.MICROS);
        Instant day = now.plus(Duration.ofDays(1));
        Instant cutoff = expires.isBefore(day) ? expires : day;
        if (!cutoff.isAfter(now)) throw invalid();
        var admission = new EventAdmissionVO(receiptId, command.getDefinitionKey(), pinned.getRevision(), release.releaseVersion(),
                prepared.routeSha(), prepared.fingerprint(), Map.of("variables", prepared.normalized(), "agentTaskOverrides", Map.of()),
                prepared.configuration(), prepared.configurationSha(), prepared.proof().authorizationSha256(), now, cutoff);
        return new EventInboxBO().setTenantId(producer.tenantId()).setProducerIssuer(producer.issuerFingerprint())
                .setProducerSubject(producer.subjectId()).setEventId(command.getEventId()).setReceiptId(receiptId)
                .setFingerprint(prepared.fingerprint()).setRouteKey(route.routeKey()).setRouteVersion(route.version())
                .setRouteSha256(prepared.routeSha()).setReleaseKey(release.releaseVersion()).setMode("START").setCommandId(prepared.commandId())
                .setPayload(command.getPayload()).setCorrelationKey(command.getCorrelationKey()).setState("RECEIVED")
                .setOccurredAt(command.getOccurredAt()).setReceivedAt(now).setExpiresAt(expires).setNextRetryAt(now)
                .setDefinitionKey(command.getDefinitionKey()).setActivationRevision(pinned.getRevision())
                .setAdmittedAt(now).setAdmissionNotAfter(cutoff).setAdmission(admission);
    }

    public static String eventStartCommandId(ProcessActor producer, String eventId) {
        return EVENT_START_PREFIX + ProcessTypedJson.sha256("AUTOMATION_EVENT_START_COMMAND_V2", ProcessTypedJson.canonicalBytes(
                List.of(producer.tenantId(), producer.issuerFingerprint(), producer.subjectId(), eventId)));
    }

    private static EventRouteVO eventStartRoute(ImmutableReleaseVO release, String routeKey, long routeVersion) {
        return release.source().eventRoutes().stream()
                .filter(route -> route.routeKey().equals(routeKey) && route.version() == routeVersion && "START".equals(route.mode()))
                .findFirst().orElseThrow(() -> new AutomationOperationException(
                        AutomationOperationException.Code.TIANJI_EVENT_ROUTE_VERSION_CONFLICT));
    }

    private static String eventRouteSha(ImmutableReleaseVO release, EventRouteVO route) {
        Object digest = map(release.dependencies().get("eventRoutes")).get(route.routeKey() + "/" + route.version());
        if (!(digest instanceof String value) || !ProcessInstanceBindingStore.sha256(value)) throw automationUnavailable();
        return value;
    }

    private static AutomationAdmissionQuery eventAdmissionQuery(Purpose purpose, ProcessActor producer, ImmutableReleaseVO release,
            long revision, String commandId, String fingerprint, String configurationSha, String eventId, String routeSha, String receiptId) {
        return admissionQuery(purpose, producer, release, revision, commandId, fingerprint, configurationSha)
                .setEventIdentity(new AutomationAdmissionQuery.EventIdentityVO(producer.issuerFingerprint(), producer.subjectId(), eventId, fingerprint))
                .setRouteSha256(routeSha).setAdmissionReceiptId(receiptId);
    }

    public record PreparedEventAdmissionVO(ProcessActor producer, ProcessEventCommand command, String commandId, String fingerprint,
            AutomationActivationBO activation, ImmutableReleaseVO release, EventRouteVO route, String routeSha,
            Map<String, Object> normalized, Map<String, Object> configuration, String configurationSha,
            AutomationAdmissionProofVO proof, TrustObservationVO trust) { }

    private record TrustedStartVO(AutomationAdmissionProofVO proof, TrustObservationVO trust) { }

    public EventStartOutcomeVO startAdmittedEvent(ProcessEventInboxStore store, long tenantId, String receiptId) {
        if (automation == null || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw automationUnavailable();
        var snapshot = store.find(tenantId, receiptId).orElseThrow(ProcessResourceNotFoundException::new);
        requireEventStartSource(snapshot);
        TrustedStartVO evidence = null;
        boolean revoked = false;
        if ("RECEIVED".equals(snapshot.getState()) && snapshot.getAttempts() < ProcessEventInboxStore.MAX_RETRIES
                && store.now().isBefore(snapshot.getAdmissionNotAfter())
                && receipts.findOwnedStart(snapshot.producer(), snapshot.getCommandId()).isEmpty()) {
            var release = eventStartRelease(snapshot);
            try {
                requireNewAutomationStart();
                evidence = observeTrustedStart(eventStartQuery(snapshot, release), release);
            } catch (AccessDeniedException denied) {
                revoked = true;
            } catch (RuntimeException failedPreparation) {
                if (receipts.findOwnedStart(snapshot.producer(), snapshot.getCommandId()).isEmpty()) throw failedPreparation;
            }
        }
        var prepared = evidence;
        boolean denied = revoked;
        return Objects.requireNonNull(write.execute(status ->
                applyEventStart(store, tenantId, receiptId, snapshot, prepared, denied)));
    }

    private EventStartOutcomeVO applyEventStart(ProcessEventInboxStore store, long tenantId, String receiptId,
            EventInboxBO observed, TrustedStartVO evidence, boolean denied) {
        store.acquireTenantShared(tenantId);
        var row = store.lock(tenantId, receiptId);
        requireEventStartSource(row);
        if (!observed.getAdmission().equals(row.getAdmission()) || !observed.producer().equals(row.producer())
                || !observed.getCommandId().equals(row.getCommandId())) throw automationUnavailable();
        var actor = row.producer();
        var prior = receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.START, row.getCommandId()).orElse(null);
        if (prior != null) {
            var binding = automation.bindings().findByCommand(actor, row.getCommandId())
                    .orElseThrow(ProcessExecutionService::automationUnavailable);
            var release = verifyBoundRelease(binding, prior);
            if (!row.getReceiptId().equals(binding.getSourceEventReceiptId())
                    || !row.getFingerprint().equals(binding.getStartRequestSha256())
                    || !eventStartQuery(row, release).sha256().equals(binding.getProof().querySha256())
                    || !"RECEIVED".equals(row.getState()) && (!"APPLIED".equals(row.getState())
                        || !prior.resourceId().equals(row.getInstanceId()))) throw automationUnavailable();
            requireResolvedInput(binding, row.getCorrelationKey(), eventCorrelation(row.getCorrelationKey()), eventStartInput(row, release));
            verifyNativeReceiptResource(binding, nativeDefinition(release));
            return new EventStartOutcomeVO("APPLIED", "APPLIED", prior.resourceId());
        }
        if ("APPLIED".equals(row.getState())) throw automationUnavailable();
        if (!"RECEIVED".equals(row.getState())) return eventOutcome(row);
        Instant now = store.now();
        if (!now.isBefore(row.getAdmissionNotAfter())) return eventOutcome(store.decide(row, "REJECTED", "EXPIRED", null));
        if (denied) return eventOutcome(store.decide(row, "REJECTED", "ROUTE_REVOKED", null));
        if (row.getAttempts() >= ProcessEventInboxStore.MAX_RETRIES) {
            return eventOutcome(store.decide(row, "DEAD", "RETRY_EXHAUSTED", null));
        }
        if (evidence == null) throw automationUnavailable();
        var release = eventStartRelease(row);
        var query = eventStartQuery(row, release);
        evidence.proof().requireCurrent(query.sha256(), now);
        AutomationReleaseTrustVerifier.requireWindow(evidence.proof(), evidence.trust(), now);
        var admission = row.getAdmission();
        var input = eventStartInput(row, release);
        var binding = new AutomationInstanceBindingBO().setTenantId(tenantId).setOwner(actor)
                .setReleaseKey(row.getReleaseKey()).setDefinitionKey(row.getDefinitionKey()).setActivationRevision(row.getActivationRevision())
                .setEffectiveManifest(admission.effectiveConfiguration()).setConfigurationSha256(admission.configurationSha256())
                .setSourceCommandId(row.getCommandId()).setStartRequestSha256(row.getFingerprint())
                .setAdmissionKind(AutomationInstanceBindingBO.AdmissionKind.EVENT).setSourceEventReceiptId(receiptId)
                .setAdmittedAt(row.getAdmittedAt()).setProof(evidence.proof());
        String nativeId = nativeDefinition(release);
        var receipt = createBoundAutomationInstance(binding, nativeId, row.getCorrelationKey(), eventCorrelation(row.getCorrelationKey()), input);
        if (receipt == null) throw automationUnavailable();
        Instant finalCheck = store.now();
        if (!finalCheck.isBefore(row.getAdmissionNotAfter())) throw automationUnavailable();
        evidence.proof().requireCurrent(query.sha256(), finalCheck);
        AutomationReleaseTrustVerifier.requireWindow(evidence.proof(), evidence.trust(), finalCheck);
        return new EventStartOutcomeVO("APPLIED", "APPLIED", receipt.resourceId());
    }

    private ImmutableReleaseVO eventStartRelease(EventInboxBO row) {
        var release = automation.releases().find(row.getTenantId(), row.getReleaseKey())
                .orElseThrow(ProcessExecutionService::automationUnavailable);
        if (!row.getDefinitionKey().equals(release.rootDefinitionKey())
                || !row.getRouteSha256().equals(eventRouteSha(release, eventStartRoute(release, row.getRouteKey(), row.getRouteVersion())))) {
            throw automationUnavailable();
        }
        return release;
    }

    private static void requireEventStartSource(EventInboxBO row) {
        if (!"START".equals(row.getMode()) || row.getAdmission() == null
                || !eventStartCommandId(row.producer(), row.getEventId()).equals(row.getCommandId())) throw automationUnavailable();
    }

    private static AutomationAdmissionQuery eventStartQuery(EventInboxBO row, ImmutableReleaseVO release) {
        return eventAdmissionQuery(Purpose.EVENT_START, row.producer(), release, row.getActivationRevision(), row.getCommandId(),
                row.getFingerprint(), row.getAdmission().configurationSha256(), row.getEventId(), row.getRouteSha256(), row.getReceiptId());
    }

    private Object eventStartInput(EventInboxBO row, ImmutableReleaseVO release) {
        var admission = row.getAdmission();
        var route = eventStartRoute(release, row.getRouteKey(), row.getRouteVersion());
        var input = release.source().inputSchema().normalizeValue(EventInboxBO.mapStartInput(route, row.getPayload()));
        if (!input.equals(release.source().inputSchema().normalizeValue(admission.mappedStartInput().get("variables")))
                || !Map.of().equals(admission.mappedStartInput().get("agentTaskOverrides"))
                || !matchesEventAgentManifest(release, admission.effectiveConfiguration())
                || !ProcessInstanceBindingStore.configurationSha256(admission.effectiveConfiguration()).equals(admission.configurationSha256())) {
            throw automationUnavailable();
        }
        return input;
    }

    private static EventStartOutcomeVO eventOutcome(EventInboxBO row) {
        return new EventStartOutcomeVO(row.getState(), row.getDecisionCode(), row.getInstanceId());
    }

    private static String eventCorrelation(String key) {
        if (key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) return key;
        return "event-correlation-" + ProcessTypedJson.sha256("AUTOMATION_EVENT_CORRELATION_V2", ProcessTypedJson.canonicalBytes(key));
    }

    public record EventStartOutcomeVO(String state, String decisionCode, String instanceId) { }

    private AutomationInstanceDTO writeAutomation(ProcessActor actor, AutomationStartCommand intent, String raw, PreparedStartVO prepared) {
        PlatformReleaseTenantFence.acquireShared(automationJdbc, actor.tenantId());
        if (receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.START, intent.getCommandId()).isPresent()) return null;
        requireNewAutomationStart();
        var activation = automation.activations().current(actor.tenantId(), intent.getDefinitionKey())
                .orElseThrow(ProcessExecutionService::activationConflict);
        if (!activation.equals(prepared.pinned().activation())) throw activationConflict();
        var release = automation.releases().find(actor.tenantId(), activation.getReleaseVersion())
                .orElseThrow(ProcessExecutionService::automationUnavailable);
        requireActivation(activation, release);
        var nativeId = nativeDefinition(release);
        Instant admitted = automation.bindings().now();
        var query = admissionQuery(Purpose.START, actor, release, activation.getRevision(), intent.getCommandId(), raw, prepared.configurationSha());
        prepared.proof().requireCurrent(query.sha256(), admitted);
        AutomationReleaseTrustVerifier.requireWindow(prepared.proof(), prepared.trust(), admitted);
        var binding = new AutomationInstanceBindingBO().setTenantId(actor.tenantId()).setOwner(actor)
                .setReleaseKey(release.releaseVersion()).setDefinitionKey(release.rootDefinitionKey())
                .setActivationRevision(activation.getRevision()).setEffectiveManifest(prepared.configuration())
                .setConfigurationSha256(prepared.configurationSha()).setSourceCommandId(intent.getCommandId()).setStartRequestSha256(raw)
                .setAdmissionKind(AutomationInstanceBindingBO.AdmissionKind.DIRECT).setAdmittedAt(admitted).setProof(prepared.proof());
        var receipt = createBoundAutomationInstance(binding, nativeId, intent.getBusinessKey(), intent.getCorrelationId(), prepared.normalized());
        if (receipt == null) return null;
        var projection = project(binding, receipt, release, nativeId, new AutomationObservationPermitVO(true, false, false, false, false), actor);
        Instant finalCheck = automation.bindings().now();
        prepared.proof().requireCurrent(query.sha256(), finalCheck);
        AutomationReleaseTrustVerifier.requireWindow(prepared.proof(), prepared.trust(), finalCheck);
        return projection;
    }

    private Receipt createBoundAutomationInstance(AutomationInstanceBindingBO binding, String nativeId,
                                                   String business, String correlation, Object normalized) {
        var actor = binding.getOwner();
        var command = binding.getSourceCommandId();
        String resolved = resolvedHash(binding.getStartRequestSha256(), binding.getTenantId(), binding.getDefinitionKey(),
                binding.getReleaseKey(), binding.getActivationRevision(), binding.getConfigurationSha256(), business, correlation, normalized);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command, resolved, binding.getReleaseKey(), correlation)) {
            return null;
        }
        String instanceRef = engine.getIdGenerator().getNextId();
        var runtime = engine.getRuntimeService();
        ProcessInstance instance = runtime.createProcessInstanceBuilder()
                .predefineProcessInstanceId(instanceRef).processDefinitionId(nativeId).tenantId(Long.toString(actor.tenantId()))
                .businessKey(business).variables(Map.of(ProcessReleaseService.AUTOMATION_BINDING_REF, instanceRef)).start();
        if (instance == null || !instanceRef.equals(instance.getId()) || !nativeId.equals(instance.getProcessDefinitionId())
                || !Long.toString(actor.tenantId()).equals(instance.getTenantId())) throw automationUnavailable();
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.START, command, instanceRef, null);
        var receipt = receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command);
        binding.setInstanceId(instanceRef).setResolvedStartSha256(resolved).setSubmittedAt(receipt.createdAt());
        automation.bindings().insert(binding);
        runtime.setVariableLocal(instanceRef, AutomationBootstrapBehavior.ROOT_INPUTS,
                engine.getVariableJsonMapper().readTree(ProcessTypedJson.canonicalBytes(normalized, MAX_ROOT_INPUT_BYTES)));
        requireBoundReceipt(binding, receipt);
        return receipt;
    }

    private AutomationInstanceDTO replayAutomation(ProcessActor actor, AutomationStartCommand intent, String raw) {
        var original = Objects.requireNonNull(read.execute(status -> {
            var receipt = receipts.findOwnedStart(actor, intent.getCommandId()).orElseThrow(ProcessExecutionService::automationUnavailable);
            if (!isAutomationStartReceipt(actor, receipt)) throw conflict();
            var expectedRaw = automation.bindings().findRawDigest(actor, intent.getCommandId())
                    .orElseThrow(ProcessExecutionService::automationUnavailable);
            if (!raw.equals(expectedRaw)) throw conflict();
            var binding = automation.bindings().findByCommand(actor, intent.getCommandId()).orElseThrow(ProcessExecutionService::automationUnavailable);
            if (binding.getAdmissionKind() != AutomationInstanceBindingBO.AdmissionKind.DIRECT
                    || !raw.equals(binding.getStartRequestSha256())) throw conflict();
            var release = verifyBoundRelease(binding, receipt);
            try {
                Object normalized = release.source().inputSchema().normalizeValue(intent.getVariables());
                requireResolvedInput(binding, intent.getBusinessKey(), intent.getCorrelationId(), normalized);
            } catch (IllegalArgumentException malformedStoredSource) {
                throw automationUnavailable();
            }
            return binding;
        }));
        var descriptor = automation.releases().find(actor.tenantId(), original.getReleaseKey()).orElseThrow(ProcessExecutionService::automationUnavailable);
        var query = admissionQuery(Purpose.REPLAY_START, actor, descriptor, original.getActivationRevision(), intent.getCommandId(), raw, null);
        String querySha = query.sha256();
        var permission = observeAdmission(query, querySha);
        return Objects.requireNonNull(read.execute(status -> {
            var receipt = receipts.findOwnedStart(actor, intent.getCommandId()).orElseThrow(ProcessExecutionService::automationUnavailable);
            var binding = automation.bindings().findByCommand(actor, intent.getCommandId()).orElseThrow(ProcessExecutionService::automationUnavailable);
            if (!binding.equals(original)) throw automationUnavailable();
            var release = verifyBoundRelease(binding, receipt);
            permission.requireCurrent(querySha, automation.bindings().now());
            return project(binding, receipt, release, nativeDefinition(release),
                    new AutomationObservationPermitVO(true, false, false, false, false), actor);
        }));
    }

    public AutomationInstanceDTO observeAutomation(@NotNull ProcessActor actor, @NotNull String instanceId,
                                                   @NotNull AutomationObservationPermitVO permit) {
        requireAutomationActor(actor);
        if (permit == null || !validId(instanceId, 255)) throw invalid();
        if (!permit.ownerRead() && !permit.observe()) throw new AccessDeniedException("Automation instance observation permission is required");
        automation.verifier().verifyAutomationV14();
        return Objects.requireNonNull(read.execute(status -> {
            var owner = automation.bindings().findOwner(actor.tenantId(), instanceId).orElseThrow(ProcessResourceNotFoundException::new);
            if (!permit.observe() && !actor.equals(owner)) throw new ProcessResourceNotFoundException();
            var binding = automation.bindings().find(actor.tenantId(), instanceId).orElseThrow(ProcessExecutionService::automationUnavailable);
            var receipt = receipts.findOwnedStart(owner, binding.getSourceCommandId()).orElseThrow(ProcessExecutionService::automationUnavailable);
            var release = verifyBoundRelease(binding, receipt);
            return project(binding, receipt, release, nativeDefinition(release), permit, actor);
        }));
    }

    private void requireAutomationActor(ProcessActor actor) {
        if (actor == null || !ProcessInstanceBindingStore.sha256(actor.issuerFingerprint())) throw new AccessDeniedException("Verified Process actor is required");
        if (automation == null || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw automationUnavailable();
        }
    }

    private void requireNewAutomationStart() {
        if (!automation.environment().getProperty(AUTOMATION_ENABLED, Boolean.class, false)) {
            throw new AccessDeniedException("New automation START is disabled");
        }
    }

    private AutomationAdmissionProofVO observeAdmission(AutomationAdmissionQuery query, String querySha) {
        try {
            var authority = automation.admissionAuthority().getIfAvailable();
            if (authority == null) throw automationUnavailable();
            var proof = authority.observe(query);
            if (proof == null || !querySha.equals(query.sha256())) throw automationUnavailable();
            proof.requireCurrent(querySha, automation.bindings().now());
            return proof;
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (RuntimeException failure) {
            throw automationUnavailable();
        }
    }

    private static AutomationAdmissionQuery admissionQuery(Purpose purpose, ProcessActor actor, ImmutableReleaseVO release,
            long revision, String command, String raw, String configuration) {
        return new AutomationAdmissionQuery().setPurpose(purpose).setActor(actor).setDefinitionKey(release.rootDefinitionKey())
                .setReleaseVersion(release.releaseVersion()).setActivationRevision(revision).setCommandId(command).setRequestSha256(raw)
                .setSourceManifestSha256(release.sourceSha256()).setCompiledManifestSha256(release.compiledSha256())
                .setDependencyManifestSha256(release.dependencySha256()).setEffectiveConfigurationSha256(configuration);
    }

    private static void requireActivation(AutomationActivationBO activation, ImmutableReleaseVO release) {
        if (activation.getTenantId() != release.tenantId() || !activation.getDefinitionKey().equals(release.rootDefinitionKey())
                || !activation.getReleaseVersion().equals(release.releaseVersion())
                || !activation.getSourceManifestSha256().equals(release.sourceSha256())
                || !activation.getCompiledManifestSha256().equals(release.compiledSha256())
                || !activation.getDependencyManifestSha256().equals(release.dependencySha256())) throw automationUnavailable();
    }

    private String nativeDefinition(ImmutableReleaseVO release) {
        try {
            releases.requireVerifiedAutomation(release);
            return automation.bindings().nativeDefinition(release.tenantId(), release.releaseVersion(), release.rootDefinitionKey())
                    .orElseThrow(ProcessExecutionService::automationUnavailable).definitionId();
        } catch (ProcessResourceNotFoundException missing) {
            throw automationUnavailable();
        }
    }

    private ImmutableReleaseVO verifyBoundRelease(AutomationInstanceBindingBO binding, Receipt receipt) {
        requireBoundReceipt(binding, receipt);
        var release = automation.releases().find(binding.getTenantId(), binding.getReleaseKey()).orElseThrow(ProcessExecutionService::automationUnavailable);
        var activation = automation.bindings().activationEvidence(binding).orElseThrow(ProcessExecutionService::automationUnavailable);
        if (!binding.getDefinitionKey().equals(release.rootDefinitionKey())
                || !activation.sourceSha256().equals(release.sourceSha256()) || !activation.compiledSha256().equals(release.compiledSha256())
                || !activation.dependencySha256().equals(release.dependencySha256())
                || !AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release))
                        .equals(binding.getProof().signedPayloadSha256())) throw automationUnavailable();
        if (binding.getAdmissionKind() == AutomationInstanceBindingBO.AdmissionKind.DIRECT) {
            var query = admissionQuery(Purpose.START, binding.getOwner(), release, binding.getActivationRevision(),
                    binding.getSourceCommandId(), binding.getStartRequestSha256(), binding.getConfigurationSha256());
            binding.getProof().requireCurrent(query.sha256(), binding.getAdmittedAt());
        } else {
            var inbox = new ProcessEventInboxStore(automationJdbc, schema);
            var event = inbox.find(binding.getTenantId(), binding.getSourceEventReceiptId())
                    .orElseThrow(ProcessExecutionService::automationUnavailable);
            requireEventStartSource(event);
            if (!event.producer().equals(binding.getOwner()) || !event.getCommandId().equals(binding.getSourceCommandId())
                    || !event.getFingerprint().equals(binding.getStartRequestSha256())
                    || !event.getReleaseKey().equals(binding.getReleaseKey()) || !event.getDefinitionKey().equals(binding.getDefinitionKey())
                    || event.getActivationRevision() != binding.getActivationRevision()
                    || !event.getAdmission().configurationSha256().equals(binding.getConfigurationSha256())
                    || !eventCorrelation(event.getCorrelationKey()).equals(receipt.correlationId())
                    || !binding.getAdmittedAt().equals(event.getAdmittedAt())
                    || !binding.getSubmittedAt().isBefore(event.getAdmissionNotAfter())
                    || !("RECEIVED".equals(event.getState()) || "APPLIED".equals(event.getState())
                        && binding.getInstanceId().equals(event.getInstanceId()))) throw automationUnavailable();
            var input = eventStartInput(event, eventStartRelease(event));
            requireResolvedInput(binding, event.getCorrelationKey(), receipt.correlationId(), input);
            binding.getProof().requireCurrent(eventStartQuery(event, release).sha256(), binding.getSubmittedAt());
        }
        return release;
    }

    private void verifyNativeReceiptResource(AutomationInstanceBindingBO binding, String nativeId) {
        String tenant = Long.toString(binding.getTenantId());
        String id = binding.getInstanceId();
        var live = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id)
                .processInstanceTenantId(tenant).singleResult();
        if (live != null) {
            if (!id.equals(live.getId()) || !tenant.equals(live.getTenantId()) || !nativeId.equals(live.getProcessDefinitionId())) {
                throw startReceiptUnavailable();
            }
            return;
        }
        var historical = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(id)
                .processInstanceTenantId(tenant).singleResult();
        if (historical == null || !id.equals(historical.getId()) || !tenant.equals(historical.getTenantId())
                || !nativeId.equals(historical.getProcessDefinitionId()) || historical.getEndTime() == null) throw startReceiptUnavailable();
    }

    static void requireBoundReceipt(AutomationInstanceBindingBO binding, Receipt receipt) {
        if (receipt == null || !"APPLIED".equals(receipt.state()) || !binding.getInstanceId().equals(receipt.resourceId())
                || !binding.getReleaseKey().equals(receipt.releaseKey()) || !binding.getResolvedStartSha256().equals(receipt.requestHash())
                || !binding.getSubmittedAt().equals(receipt.createdAt()) || !validId(receipt.correlationId(), 128)) {
            throw automationUnavailable();
        }
    }

    static void requireResolvedInput(AutomationInstanceBindingBO binding, String business, String correlation, Object normalized) {
        var computed = resolvedHash(binding.getStartRequestSha256(), binding.getTenantId(), binding.getDefinitionKey(), binding.getReleaseKey(),
                binding.getActivationRevision(), binding.getConfigurationSha256(), business, correlation, normalized);
        if (!binding.getResolvedStartSha256().equals(computed)) throw automationUnavailable();
    }

    static String rootInputSha256(Object normalized) {
        return ProcessTypedJson.sha256("AUTOMATION_ROOT_INPUT_V2", ProcessTypedJson.canonicalBytes(normalized, MAX_ROOT_INPUT_BYTES));
    }

    private static String resolvedHash(String raw, long tenant, String definition, String release, long revision, String configuration,
                                       String business, String correlation, Object normalized) {
        return ProcessTypedJson.sha256("AUTOMATION_RESOLVED_START_V2", ProcessTypedJson.canonicalBytes(
                Map.of("rawRequestSha256", raw, "tenantId", tenant, "definitionKey", definition, "releaseVersion", release,
                        "activationRevision", revision, "configurationSha256", configuration, "businessKey", business,
                        "correlationId", correlation, "normalizedInputSha256", rootInputSha256(normalized))));
    }

    private static final Set<String> AGENT_ROUTE_FIELDS = Set.of("sourceId", "issuer", "subject", "limits");

    private Map<String, Object> freezeAgentManifest(ProcessActor requester, ImmutableReleaseVO release,
            Map<String, AgentTaskOverrideVO> overrides) {
        var base = effectiveManifest(release, overrides);
        if (base.isEmpty()) return base;
        var routeProvider = automation.typedRoutes() == null ? null : automation.typedRoutes().getIfAvailable();
        var templates = automation.agentTemplates() == null ? null : automation.agentTemplates().getIfAvailable();
        if (routeProvider == null || templates == null) throw automationUnavailable();
        var route = routeProvider.route(requester.tenantId());
        if (route == null || route.actor().tenantId() != requester.tenantId()) throw automationUnavailable();

        var result = new TreeMap<String, Object>();
        var observed = new HashMap<List<TemplateRefVO>, AgentRuntimeTemplateCommand>();
        for (var node : release.source().nodeBindings()) {
            if (node.agent() == null) continue;
            var item = new LinkedHashMap<>(map(base.get(node.nodeId())));
            var selected = templateRef(item.get("templateRef"));
            var configuration = node.agent().configurationRef();
            var identity = configuration == null ? List.of(selected) : List.of(selected, configuration);
            var template = observed.computeIfAbsent(identity,
                    refs -> templates.resolve(requester, route.sourceId(), selected, configuration));
            if (template == null || !template.isTemplateConsistent()
                    || !selected.key().equals(template.getTemplateKey()) || selected.version() != template.getTemplateVersion()
                    || !selected.contentSha256().equals(template.contentSha256())
                    || !node.inputSchema().toWire().equals(template.getInputSchema().toWire())
                    || !node.outputSchema().toWire().equals(template.getOutputSchema().toWire())
                    || !node.agent().parameterSchema().toWire().equals(template.getParameterSchema().toWire())) {
                throw automationUnavailable();
            }
            item.put("sourceId", route.sourceId());
            item.put("issuer", route.actor().issuerFingerprint());
            item.put("subject", route.actor().subjectId());
            item.put("limits", template.getLimits().toWire());
            result.put(node.nodeId(), Map.copyOf(item));
        }
        return Map.copyOf(result);
    }

    private static boolean matchesEventAgentManifest(ImmutableReleaseVO release, Map<String, Object> manifest) {
        var base = effectiveManifest(release, Map.of());
        if (!manifest.keySet().equals(base.keySet())) return false;
        // Historic event admissions retain their original proof and digest.
        if (ProcessInstanceBindingStore.configurationSha256(base)
                .equals(ProcessInstanceBindingStore.configurationSha256(manifest))) return true;
        var baseFields = new TreeMap<String, Object>();
        for (var entry : manifest.entrySet()) {
            var item = new LinkedHashMap<>(map(entry.getValue()));
            if (!(item.get("sourceId") instanceof String sourceId) || sourceId.isBlank()
                    || !(item.get("issuer") instanceof String issuer) || !ProcessInstanceBindingStore.sha256(issuer)
                    || !(item.get("subject") instanceof String subject) || subject.isBlank()) return false;
            TechnicalLimitsVO.fromExactWire(map(item.get("limits")));
            item.keySet().removeAll(AGENT_ROUTE_FIELDS);
            baseFields.put(entry.getKey(), item);
        }
        return ProcessInstanceBindingStore.configurationSha256(base)
                .equals(ProcessInstanceBindingStore.configurationSha256(baseFields));
    }

    private static TemplateRefVO templateRef(Object value) {
        var ref = map(value);
        if (!ref.keySet().equals(Set.of("key", "version", "contentSha256"))
                || !(ref.get("key") instanceof String key) || !(ref.get("version") instanceof Number version)
                || !(ref.get("contentSha256") instanceof String digest)) throw automationUnavailable();
        return new TemplateRefVO(key, version.longValue(), digest);
    }

    private static Map<String, Object> effectiveManifest(ImmutableReleaseVO release, Map<String, AgentTaskOverrideVO> overrides) {
        var result = new TreeMap<String, Object>();
        for (var node : release.source().nodeBindings()) {
            var agent = node.agent();
            if (agent == null) continue;
            var requested = overrides.get(node.nodeId());
            var template = requested == null || requested.templateRef() == null ? agent.defaultTemplate() : requested.templateRef();
            if (!agent.allowedTemplates().contains(template)) throw invalid();
            var merged = new LinkedHashMap<>(agent.parameterDefaults());
            if (requested != null && requested.parameters() != null) {
                if (!agent.overridableParameters().containsAll(requested.parameters().keySet())) throw invalid();
                merged.putAll(requested.parameters());
            }
            var item = new LinkedHashMap<String, Object>();
            item.put("templateRef", templateWire(template));
            item.put("allowedTemplates", agent.allowedTemplates().stream().map(ProcessExecutionService::templateWire).toList());
            item.put("parameters", agent.parameterSchema().normalizeValue(merged));
            if (agent.configurationRef() != null) item.put("configurationRef", templateWire(agent.configurationRef()));
            result.put(node.nodeId(), Map.copyOf(item));
        }
        if (!result.keySet().containsAll(overrides.keySet())) throw invalid();
        return Map.copyOf(result);
    }

    private static Map<String, Object> templateWire(TemplateRefVO ref) {
        return Map.of("key", ref.key(), "version", ref.version(), "contentSha256", ref.contentSha256());
    }

    private AutomationInstanceDTO project(AutomationInstanceBindingBO binding, Receipt receipt, ImmutableReleaseVO release, String nativeId,
                                          AutomationObservationPermitVO permit, ProcessActor actor) {
        var runtime = engine.getRuntimeService();
        String tenant = Long.toString(binding.getTenantId());
        String instanceId = binding.getInstanceId();
        var current = runtime.createProcessInstanceQuery().processInstanceId(instanceId).processInstanceTenantId(tenant).singleResult();
        String business;
        StateEnum state;
        Instant finished = null;
        Map<String, Object> output = null;
        if (current != null) {
            if (!instanceId.equals(current.getId()) || !tenant.equals(current.getTenantId())
                    || !nativeId.equals(current.getProcessDefinitionId())) throw automationUnavailable();
            business = current.getBusinessKey();
            state = current.isSuspended() ? StateEnum.SUSPENDED : StateEnum.ACTIVE;
            Object stored = runtime.getVariableLocal(instanceId, AutomationBootstrapBehavior.ROOT_INPUTS);
            Object input = decodeNativeJson(engine, stored, MAX_ROOT_INPUT_BYTES);
            try {
                Object normalized = release.source().inputSchema().normalizeValue(input);
                if (!rootInputSha256(input).equals(rootInputSha256(normalized))) throw automationUnavailable();
                requireResolvedInput(binding, business, receipt.correlationId(), normalized);
            } catch (IllegalArgumentException malformedStoredInput) {
                throw automationUnavailable();
            }
        } else {
            var history = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instanceId)
                    .processInstanceTenantId(tenant).singleResult();
            if (history == null || !instanceId.equals(history.getId()) || !tenant.equals(history.getTenantId())
                    || !nativeId.equals(history.getProcessDefinitionId()) || history.getEndTime() == null) throw automationUnavailable();
            business = history.getBusinessKey();
            finished = history.getEndTime().toInstant().truncatedTo(ChronoUnit.MICROS);
            state = switch (history.getState() == null ? "" : history.getState()) {
                case "COMPLETED" -> StateEnum.COMPLETED;
                case "TERMINATED" -> StateEnum.FAILED;
                case "CANCELLED", "EXTERNALLY_TERMINATED", "INTERNALLY_TERMINATED" -> StateEnum.CANCELLED;
                default -> history.getDeleteReason() == null ? StateEnum.COMPLETED : StateEnum.CANCELLED;
            };
            if (state == StateEnum.COMPLETED && (permit.dataRead() || permit.dataReadOwnerOnly() && binding.getOwner().equals(actor))) {
                var outputs = engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(instanceId)
                        .variableName(AutomationBootstrapBehavior.ROOT_OUTPUT).list();
                if (outputs.size() > 1) throw automationUnavailable();
                if (!outputs.isEmpty()) {
                    try {
                        output = map(release.source().outputSchema().normalizeValue(
                                decodeNativeJson(engine, outputs.getFirst().getValue(), MAX_ROOT_INPUT_BYTES)));
                    } catch (IllegalArgumentException malformedOutput) {
                        throw automationUnavailable();
                    }
                }
            }
        }
        if (binding.getAdmissionKind() == AutomationInstanceBindingBO.AdmissionKind.EVENT
                ? business == null || business.isBlank() || business.length() > 255 || business.chars().anyMatch(Character::isISOControl)
                : !validId(business, 255)) throw automationUnavailable();
        var actions = current != null && permit.cancel() && binding.getOwner().equals(actor) ? List.of("CANCEL") : List.<String>of();
        String version = ProcessTypedJson.sha256("AUTOMATION_INSTANCE_OBSERVATION_V2", ProcessTypedJson.canonicalBytes(
                List.of(binding.getResolvedStartSha256(), state.name(), finished == null ? "" : finished.toString())));
        var projection = new AutomationInstanceProjectionBO().setBinding(binding).setBusinessKey(business)
                .setCorrelationId(receipt.correlationId()).setState(state).setFinishedAt(finished).setVersion(version)
                .setAuthorizedOutput(output).setAvailableActions(actions).setGraph(AutomationReleaseStore.logicalGraph(release.source()));
        var result = automation.converter().toTarget(projection);
        if (!result.isInstanceProjectionConsistent()) throw automationUnavailable();
        return result;
    }

    static Object decodeNativeJson(SpringProcessEngineConfiguration engine, Object data, int maxBytes) {
        if (data == null || !engine.getVariableJsonMapper().isJsonNode(data)) throw automationUnavailable();
        try {
            byte[] raw = data.toString().getBytes(StandardCharsets.UTF_8);
            if (raw.length > maxBytes) throw automationUnavailable();
            return NATIVE_JSON.readValue(raw, JSON_OBJECT);
        } catch (RuntimeException malformed) {
            throw automationUnavailable();
        }
    }

    private static Map<String, Object> decodeCanonical(Map<String, Object> data, int maxBytes) {
        return NATIVE_JSON.readValue(ProcessTypedJson.canonicalBytes(data, maxBytes), JSON_OBJECT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> object)) throw automationUnavailable();
        return (Map<String, Object>) object;
    }

    private boolean isAutomationStartReceipt(ProcessActor actor, Receipt receipt) {
        return receipts.hasAutomationRelease(actor.tenantId(), receipt.releaseKey())
                || receipts.hasAutomationRootInput(actor.tenantId(), receipt.releaseKey())
                        && releases.readMigrationRelease(actor, receipt.releaseKey()).isEmpty();
    }

    private static AutomationOperationException activationConflict() {
        return new AutomationOperationException(AutomationOperationException.Code.TIANJI_PROCESS_ACTIVATION_CONFLICT);
    }

    private static IllegalStateException automationUnavailable() {
        return new IllegalStateException("Automation START or instance evidence is unavailable");
    }

    private record PinnedReleaseVO(AutomationActivationBO activation, ImmutableReleaseVO release) { }
    private record PreparedStartVO(PinnedReleaseVO pinned, Object normalized, Map<String, Object> configuration, String configurationSha,
                                   AutomationAdmissionProofVO proof, TrustObservationVO trust) { }

    public ProcessStartReceiptDTO getStartReceipt(ProcessActor actor, GetProcessStartReceiptQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        if (query == null) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw startReceiptUnavailable();
        }
        try {
            return Objects.requireNonNull(receiptRead.execute(status -> {
                receipts.beginReceiptObservation();
                var row = automation == null ? receipts.findAppliedStart(actor, query.commandId(), query.requestSha256())
                        .orElseThrow(ProcessResourceNotFoundException::new) : findAutomationAwareReceipt(actor, query);
                if (row.recordedAt() == null || Thread.currentThread().isInterrupted()) throw startReceiptUnavailable();
                try {
                    return new ProcessStartReceiptDTO(row.instanceId(), ProcessStartReceiptDTO.StatusEnum.APPLIED,
                            row.recordedAt().toInstant());
                } catch (IllegalArgumentException corruptSource) {
                    throw startReceiptUnavailable();
                }
            }));
        } catch (DataAccessException | TransactionException failure) {
            throw startReceiptUnavailable();
        }
    }

    private ProcessCommandReceiptStore.StartReceiptObservation findAutomationAwareReceipt(ProcessActor actor, GetProcessStartReceiptQuery query) {
        var receipt = receipts.findOwnedStart(actor, query.commandId())
                .filter(row -> "APPLIED".equals(row.state())).orElseThrow(ProcessResourceNotFoundException::new);
        if (!isAutomationStartReceipt(actor, receipt)) {
            return receipts.findAppliedStart(actor, query.commandId(), query.requestSha256())
                    .orElseThrow(ProcessResourceNotFoundException::new);
        }
        var raw = automation.bindings().findRawDigest(actor, query.commandId()).orElseThrow(ProcessExecutionService::startReceiptUnavailable);
        if (!query.requestSha256().equals(raw)) throw new ProcessResourceNotFoundException();
        var binding = automation.bindings().findByCommand(actor, query.commandId()).orElseThrow(ProcessExecutionService::startReceiptUnavailable);
        var release = verifyBoundRelease(binding, receipt);
        verifyNativeReceiptResource(binding, nativeDefinition(release));
        return new ProcessCommandReceiptStore.StartReceiptObservation(receipt.resourceId(), receipt.createdAt().atOffset(java.time.ZoneOffset.UTC));
    }

    private static IllegalStateException startReceiptUnavailable() {
        return new IllegalStateException("Process START receipt evidence is unavailable");
    }

    public ProcessInstanceCorrelationPageDTO listByCorrelation(ProcessActor actor, ListProcessInstancesByCorrelationQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        if (query == null) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw correlationUnavailable();
        }
        CorrelationCursor cursor = query.cursor() == null ? null : decodeCorrelationCursor(actor, query);
        try {
            return Objects.requireNonNull(receiptRead.execute(status -> correlationPage(actor, query, cursor)));
        } catch (DataAccessException | TransactionException failure) {
            throw correlationUnavailable();
        }
    }

    private ProcessInstanceCorrelationPageDTO correlationPage(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                              CorrelationCursor cursor) {
        Instant observedAt = receipts.beginReceiptObservation();
        if (query.toExclusive().isAfter(observedAt)) throw invalid();
        if (cursor != null && !receipts.hasCorrelationAnchor(actor, query.correlationId(), query.fromInclusive(),
                query.toExclusive(), cursor.submittedAt(), cursor.instanceId(), automation != null)) throw invalid();

        var rows = receipts.listCorrelationReceipts(actor, query.correlationId(), query.fromInclusive(), query.toExclusive(),
                cursor == null ? null : cursor.submittedAt(), cursor == null ? null : cursor.instanceId(), automation != null);
        validateCorrelationRows(rows, query, cursor);
        var boundary = rows.size() > CORRELATION_PAGE_SIZE ? rows.get(CORRELATION_PAGE_SIZE - 1) : null;
        String next = boundary == null ? null
                : encodeCorrelationCursor(actor, query, boundary.submittedAt(), boundary.instanceId());
        try {
            var items = rows.stream().limit(CORRELATION_PAGE_SIZE)
                    .map(row -> new ReceiptDTO(row.instanceId(), row.releaseVersion(), row.submittedAt())).toList();
            return new ProcessInstanceCorrelationPageDTO(query.correlationId(), observedAt, items, next);
        } catch (IllegalArgumentException corruptSource) {
            throw correlationUnavailable();
        }
    }

    private static void validateCorrelationRows(List<CorrelationReceipt> rows, ListProcessInstancesByCorrelationQuery query,
                                                CorrelationCursor cursor) {
        if (rows.size() > CORRELATION_PAGE_SIZE + 1 || Thread.currentThread().isInterrupted()) throw correlationUnavailable();
        Instant previousTime = cursor == null ? null : cursor.submittedAt();
        String previousId = cursor == null ? null : cursor.instanceId();
        var identities = new HashSet<String>();
        for (var row : rows) {
            if (row == null || !validCorrelationInstanceId(row.instanceId()) || row.releaseVersion() == null
                    || !row.releaseVersion().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") || row.submittedAt() == null
                    || row.submittedAt().getNano() % 1_000 != 0 || row.submittedAt().isBefore(query.fromInclusive())
                    || !row.submittedAt().isBefore(query.toExclusive()) || !identities.add(row.instanceId())) {
                throw correlationUnavailable();
            }
            if (previousTime != null && (row.submittedAt().isAfter(previousTime)
                    || row.submittedAt().equals(previousTime) && compareUtf8(row.instanceId(), previousId) >= 0)) {
                throw correlationUnavailable();
            }
            previousTime = row.submittedAt();
            previousId = row.instanceId();
        }
    }

    private static boolean validCorrelationInstanceId(String value) {
        return validId(value, 255) && !".".equals(value) && !"..".equals(value);
    }

    private static int compareUtf8(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static CorrelationCursor decodeCorrelationCursor(ProcessActor actor, ListProcessInstancesByCorrelationQuery query) {
        String token = query.cursor();
        try {
            var bytes = Base64.getUrlDecoder().decode(token.substring(3));
            if (!token.equals("v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) throw invalid();
            var text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().split("\n", -1);
            if (text.length != 3 || !validCorrelationInstanceId(text[1])) throw invalid();
            Instant submittedAt = ListProcessInstancesByCorrelationQuery.parseUtc(text[0]);
            if (!submittedAt.toString().equals(text[0]) || submittedAt.isBefore(query.fromInclusive())
                    || !submittedAt.isBefore(query.toExclusive())
                    || !correlationCursorHash(actor, query, submittedAt, text[1]).equals(text[2])) throw invalid();
            return new CorrelationCursor(submittedAt, text[1]);
        } catch (IllegalArgumentException | CharacterCodingException invalidCursor) {
            throw invalid();
        }
    }

    private static String encodeCorrelationCursor(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                  Instant submittedAt, String instanceId) {
        var text = submittedAt + "\n" + instanceId + "\n" + correlationCursorHash(actor, query, submittedAt, instanceId);
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String correlationCursorHash(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                Instant submittedAt, String instanceId) {
        return ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CORRELATION_CURSOR_V1", Long.toString(actor.tenantId()),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), query.correlationId(),
                query.fromInclusive().toString(), query.toExclusive().toString(), submittedAt.toString(), instanceId), Map.of());
    }

    private static IllegalStateException correlationUnavailable() {
        return new IllegalStateException("Process correlation evidence is unavailable");
    }

    private record CorrelationCursor(Instant submittedAt, String instanceId) { }

    private Snapshot startInTransaction(ProcessActor actor, StartCommand command) {
        if (automation != null) {
            var original = receipts.findOwnedStart(actor, command.commandId()).orElse(null);
            if (original != null && isAutomationStartReceipt(actor, original)) throw conflict();
        }
        Definition binding = explicitDefinition(actor, command.releaseKey(), command.definitionKey());
        Map<String, Object> inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String requestHash = resolvedStartHash(command.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), requestHash,
                command.releaseKey(), command.correlationId())) {
            var existing = receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
            if (automation != null && isAutomationStartReceipt(actor, existing) || !existing.requestHash().equals(requestHash)) throw conflict();
            if (!"APPLIED".equals(existing.state()) || existing.resourceId() == null
                    || !command.releaseKey().equals(existing.releaseKey())
                    || !command.correlationId().equals(existing.correlationId())) throw unavailable();
            return snapshot(actor, existing);
        }
        var applied = startClaimed(actor, binding, command.businessKey(), command.commandId(), inputs);
        return snapshot(actor, applied);
    }

    private Definition explicitDefinition(ProcessActor actor, String releaseKey, String definitionKey) {
        return releases.get(actor, releaseKey).definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.definitionKey().equals(definitionKey)).findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private StartOutcome governedStartInTransaction(ProcessActor actor, GovernedStartCommand command,
                                                     String expectedRelease, StartGrant grant, Pointer observed,
                                                     WorkerRouteProof proof, Optional<Provenance> history) {
        governed.begin(actor.tenantId());
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.transactionTime());
        var existing = receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
        if (existing.isPresent()) {
            return replayOwnedStart(actor, command, expectedRelease, grant, existing.orElseThrow(), history);
        }
        if (history.isPresent()) throw unavailable();

        var pointer = governed.current(actor.tenantId(), command.channel()).orElseThrow(ProcessExecutionService::unavailable);
        if (!pointer.equals(observed)) throw new ProcessVersionConflictException();
        if (!command.definitionKey().equals(pointer.definitionKey())) throw unavailable();
        if (expectedRelease != null && !expectedRelease.equals(pointer.releaseKey())) throw new ProcessVersionConflictException();
        requireWorker(pointer, proof);
        var binding = governedDefinition(actor, pointer);
        var inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String resolvedHash = resolvedStartHash(pointer.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), resolvedHash,
                pointer.releaseKey(), command.correlationId())) {
            return replayOwnedStart(actor, command, expectedRelease, grant,
                    receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId()), history);
        }

        requireAdmission(grant, proof);
        var applied = startClaimed(actor, binding, command.businessKey(), command.commandId(), inputs);
        governed.append(actor, command.commandId(), command.correlationId(), pointer, governedHash(command, inputs),
                resolvedHash, grant.auditSha256(), applied.resourceId());
        var instance = checkedSnapshot(actor, command, applied);
        requireAdmission(grant, proof);
        return new StartOutcome(instance, pointer);
    }

    private StartOutcome replayOwnedStart(ProcessActor actor, GovernedStartCommand command, String expectedRelease,
                                          StartGrant grant, Receipt receipt, Optional<Provenance> history) {
        if (automation != null && isAutomationStartReceipt(actor, receipt)) throw conflict();
        var provenance = governed.find(actor, command.commandId());
        if (history.isPresent() && !history.equals(provenance)) throw conflict();
        StartOutcome result;
        if (provenance.isPresent()) {
            result = replayGoverned(actor, command, expectedRelease, receipt, provenance.orElseThrow());
        } else {
            if (expectedRelease == null) throw conflict();
            result = new StartOutcome(replayExplicit(actor, command, expectedRelease, receipt), null);
        }
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.transactionTime());
        return result;
    }

    private Snapshot replayExplicit(ProcessActor actor, GovernedStartCommand command, String expectedRelease, Receipt receipt) {
        if (!expectedRelease.equals(receipt.releaseKey()) || !command.correlationId().equals(receipt.correlationId())) {
            throw conflict();
        }
        if (!"APPLIED".equals(receipt.state()) || receipt.resourceId() == null) throw unavailable();
        var binding = explicitDefinition(actor, expectedRelease, command.definitionKey());
        var inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        if (!receipt.requestHash().equals(resolvedStartHash(expectedRelease, command.definitionKey(), command.businessKey(),
                command.correlationId(), inputs))) throw conflict();
        return checkedSnapshot(actor, command, receipt);
    }

    private StartOutcome replayGoverned(ProcessActor actor, GovernedStartCommand command, String expectedRelease,
                                        Receipt receipt, Provenance source) {
        var pointer = source.pointer();
        if (!command.channel().equals(pointer.channel()) || !command.definitionKey().equals(pointer.definitionKey())
                || !command.correlationId().equals(receipt.correlationId())
                || expectedRelease != null && !expectedRelease.equals(pointer.releaseKey())) throw conflict();
        if (!source.instanceId().equals(receipt.resourceId()) || !source.resolvedHash().equals(receipt.requestHash())
                || !pointer.releaseKey().equals(receipt.releaseKey())) throw unavailable();
        var inputs = ProcessValues.inputs(governedDefinition(actor, pointer).inputs(), command.variables());
        if (!source.governedHash().equals(governedHash(command, inputs))
                || !source.resolvedHash().equals(resolvedStartHash(pointer.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs))) throw conflict();
        return new StartOutcome(checkedSnapshot(actor, command, receipt), pointer);
    }

    private Snapshot checkedSnapshot(ProcessActor actor, GovernedStartCommand command, Receipt receipt) {
        var instance = snapshot(actor, receipt);
        if (!command.definitionKey().equals(instance.definitionKey()) || !command.businessKey().equals(instance.businessKey())
                || !command.correlationId().equals(instance.correlationId())) throw unavailable();
        return instance;
    }

    private record StartOutcome(Snapshot instance, Pointer pointer) { }

    private Definition governedDefinition(ProcessActor actor, Pointer pointer) {
        var release = releases.get(actor, pointer.releaseKey());
        if (pointer.tenantId() != actor.tenantId() || !pointer.manifestHash().equals(release.manifestSha256())) throw unavailable();
        return release.definitions().stream().filter(definition -> definition.type() == Type.BPMN
                && pointer.definitionKey().equals(definition.definitionKey()) && pointer.modelHash().equals(definition.modelSha256()))
                .findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private void requireAdmission(StartGrant grant, WorkerRouteProof proof) {
        Instant now = governed.transactionTime();
        requireCurrent(grant.observedAt(), grant.validUntil(), now);
        requireCurrent(proof.observedAt(), proof.validUntil(), now);
    }

    private static void requireWorker(Pointer pointer, WorkerRouteProof proof) {
        if (proof == null || !workerQuery(pointer).equals(proof.query())) throw governedDenied();
        var binding = proof.binding();
        if (binding.actor().tenantId() != pointer.tenantId() || !binding.topic().equals(pointer.workerTopic())
                || !binding.schemaVersion().equals(pointer.workerSchemaVersion())
                || !pointer.workerBindingHash().equals(PlatformReleaseActivationService.workerFingerprint(
                        binding, proof.registrationSha256()))) throw governedDenied();
    }

    private static WorkerRouteQuery workerQuery(Pointer pointer) {
        return new WorkerRouteQuery(pointer.tenantId(), pointer.channel(), pointer.revision(), pointer.candidateHash(),
                pointer.workerTopic(), pointer.workerSchemaVersion());
    }

    private static void requireCurrent(Instant observedAt, Instant validUntil, Instant now) {
        if (observedAt.isAfter(now) || !now.isBefore(validUntil)
                || Duration.between(observedAt, validUntil).compareTo(MAX_GOVERNED_PROOF_WINDOW) > 0
                || Thread.currentThread().isInterrupted()) throw governedDenied();
    }

    private static AccessDeniedException governedDenied() {
        return new AccessDeniedException("Current governed Process START authority is required");
    }

    private static String governedHash(GovernedStartCommand command, Map<String, Object> inputs) {
        return ProcessRequestDigest.hash(List.of("TIANJI_GOVERNED_PROCESS_START_V1", command.channel(),
                command.definitionKey(), command.businessKey(), command.correlationId()), inputs);
    }

    private static String resolvedStartHash(String release, String definition, String business, String correlation,
                                            Map<String, Object> inputs) {
        return ProcessRequestDigest.hash(List.of(release, definition, business, correlation), inputs);
    }

    private Receipt startClaimed(ProcessActor actor, Definition binding, String businessKey, String commandId,
                                 Map<String, Object> inputs) {
        models.process(actor.tenantId(), binding);
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(binding.engineDefinitionId()).tenantId(Long.toString(actor.tenantId()))
                .businessKey(businessKey).variables(new HashMap<>(inputs)).start();
        if (instance == null || !binding.engineDefinitionId().equals(instance.getProcessDefinitionId())
                || !Long.toString(actor.tenantId()).equals(instance.getTenantId()) || !validId(instance.getId(), 255)) {
            throw new IllegalStateException("Process instance does not match its release");
        }
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.START, commandId, instance.getId(), null);
        return receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, commandId);
    }

    private Snapshot snapshot(ProcessActor actor, Receipt receipt) {
        if (!"APPLIED".equals(receipt.state()) || receipt.resourceId() == null) throw unavailable();
        Release release = releases.get(actor, receipt.releaseKey());
        String tenant = Long.toString(actor.tenantId());
        ProcessInstance current = engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (current != null) {
            Definition definition = releaseDefinition(release, current.getProcessDefinitionId());
            return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), current.getBusinessKey(),
                    receipt.correlationId(), current.isSuspended() ? State.SUSPENDED : State.ACTIVE, receipt.createdAt(), null);
        }
        HistoricProcessInstance history = engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (history == null || history.getEndTime() == null) throw unavailable();
        Definition definition = releaseDefinition(release, history.getProcessDefinitionId());
        State state = history.getDeleteReason() == null ? State.COMPLETED : State.CANCELLED;
        return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), history.getBusinessKey(),
                receipt.correlationId(), state, receipt.createdAt(), history.getEndTime().toInstant());
    }

    private static Definition releaseDefinition(Release release, String definitionId) {
        return release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.engineDefinitionId().equals(definitionId)).findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private static boolean validId(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process request is invalid"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    public enum State { ACTIVE, SUSPENDED, COMPLETED, CANCELLED }

    public record StartCommand(String releaseKey, String definitionKey, String businessKey, String commandId,
                               String correlationId, Map<String, Object> variables) {
        public StartCommand {
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
            variables = checkedStartVariables(definitionKey, businessKey, commandId, correlationId, variables);
        }
    }

    public record GovernedStartCommand(String channel, String definitionKey, String businessKey, String commandId,
                                       String correlationId, Map<String, Object> variables) {
        public GovernedStartCommand {
            if (channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")) throw invalid();
            variables = checkedStartVariables(definitionKey, businessKey, commandId, correlationId, variables);
        }
    }

    private static Map<String, Object> checkedStartVariables(String definitionKey, String businessKey, String commandId,
                                                             String correlationId, Map<String, Object> variables) {
        if (definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                || !validId(businessKey, 255) || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || variables == null) throw invalid();
        try { return Map.copyOf(variables); }
        catch (RuntimeException failure) { throw invalid(); }
    }

    public record StartAuthorizationQuery(ProcessActor actor, String channel, String definitionKey, String commandId) { }

    public record StartGrant(StartAuthorizationQuery query, String auditSha256, Instant observedAt, Instant validUntil) {
        public StartGrant {
            if (query == null || !GovernedProcessStartStore.hash(auditSha256) || observedAt == null || validUntil == null
                    || !validUntil.isAfter(observedAt)) throw invalid();
        }
    }

    public record WorkerRouteQuery(long tenantId, String channel, long revision, String candidateSha256,
                                   String topic, String schemaVersion) { }

    public record WorkerRouteProof(WorkerRouteQuery query, AgentDispatchBinding binding, String registrationSha256,
                                   Instant observedAt, Instant validUntil) {
        public WorkerRouteProof {
            if (query == null || binding == null || !GovernedProcessStartStore.hash(registrationSha256)
                    || observedAt == null || validUntil == null || !validUntil.isAfter(observedAt)) throw invalid();
        }
    }

    public interface GovernedStartAuthority {
        /** Grants the exact authenticated caller current access to start in the selected channel. */
        StartGrant authorize(StartAuthorizationQuery query);

        /**
         * Provides authoritative current evidence for one effective registration and dispatch binding
         * across all Worker actors able to acquire the tenant/topic/schema tuple. The authority must
         * keep earlier bindings usable for their existing instances and deny missing, ambiguous, or
         * unverifiable routes. A binding read scoped only to the selected actor is insufficient.
         */
        WorkerRouteProof currentWorker(WorkerRouteQuery query);
    }

    public record GovernedStartResult(Snapshot instance, String channel, long activationRevision, String candidateSha256) { }

    public record Snapshot(String instanceId, String releaseKey, String definitionKey, String businessKey,
                           String correlationId, State state, Instant submittedAt, Instant finishedAt) { }
}
