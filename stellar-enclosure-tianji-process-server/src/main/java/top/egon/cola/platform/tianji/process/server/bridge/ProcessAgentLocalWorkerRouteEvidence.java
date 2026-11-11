package top.egon.cola.platform.tianji.process.server.bridge;

import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable evidence of one Process Server's validated local Worker and Agent bridge configuration. */
public final class ProcessAgentLocalWorkerRouteEvidence {
    private final ProcessAgentBridgeProperties properties;
    private final List<WorkerTopicRegistry.Registration> registrations;
    private final String appId;

    private ProcessAgentLocalWorkerRouteEvidence(ProcessAgentBridgeProperties properties, WorkerTopicRegistry topics, String appId) {
        this.properties = properties;
        this.registrations = topics.registrations();
        this.appId = appId;
    }

    /**
     * Reads the same validated inputs used by the local bridge. Callers must supply the actual inputs for
     * the Process Server under inspection. Deployment authority and historical route retention are separate.
     */
    public static ProcessAgentLocalWorkerRouteEvidence validate(ProcessAgentBridgeProperties properties, WorkerTopicRegistry topics,
            AgentResultProducerRegistry producers, AgentResultInboxProperties inbox, IdpStarterProperties starter,
            TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth) {
        Objects.requireNonNull(properties, "properties");
        if (!properties.dispatchEnabled()) throw new ProcessResourceNotFoundException();
        ProcessAgentBridgeConfiguration.validateBindings(properties, topics, producers, inbox, starter, issuers, oauth);
        return new ProcessAgentLocalWorkerRouteEvidence(properties, topics, starter.getServiceClient().getAppId());
    }

    /** Resolves the complete local native tuple across all configured Worker actors. */
    public Optional<Route> find(long tenantId, String topic, String schemaVersion) {
        if (tenantId <= 0) throw new IllegalArgumentException("Worker route is invalid");
        ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
        var bindings = properties.bindings().stream().filter(item -> item.tenantId() == tenantId
                && item.topic().equals(topic) && item.schemaVersion().equals(schemaVersion)).limit(2).toList();
        if (bindings.size() != 1) return Optional.empty();

        var registered = registrations.stream().filter(item -> item.tenantId() == tenantId
                && item.topic().equals(topic) && item.schemaVersion().equals(schemaVersion)).limit(2).toList();
        if (registered.size() != 1) return Optional.empty();

        var configured = bindings.getFirst();
        var binding = configured.dispatch();
        var registration = registered.getFirst();
        if (!registration.internalAgentDispatch() || !registration.actor().equals(binding.actor())) return Optional.empty();
        return Optional.of(new Route(binding, registrationDigest(configured, binding, registration)));
    }

    private String registrationDigest(ProcessAgentBridgeProperties.Binding configured, AgentDispatchBinding binding,
                                      WorkerTopicRegistry.Registration registration) {
        var actor = binding.actor();
        List<String> fields = List.of("TIANJI_PS_LOCAL_WORKER_REGISTRATION_V1", Long.toString(registration.tenantId()),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), registration.topic(), registration.schemaVersion(),
                Integer.toString(registration.maxConcurrent()), Long.toString(registration.leaseDuration().toMillis()),
                Long.toString(registration.retryDelay().toMillis()), registration.effectSafety().name(),
                Boolean.toString(registration.internalAgentDispatch()), configured.baseUri().toASCIIString(),
                configured.registrationId(), appId, configured.resourceUri().toASCIIString(), ServiceTokenContext.TENANT.name(),
                LinkedAgentClient.SUBMIT_SCOPE, LinkedAgentClient.RESOLVE_SCOPE, LinkedAgentClient.CLOSE_SCOPE,
                Long.toString(configured.httpTimeout().toMillis()), Integer.toString(configured.maxTokenCalls()),
                Long.toString(properties.acknowledgementReserve().toMillis()), Long.toString(properties.maxClockSkew().toMillis()));
        Map<String, Object> values = new HashMap<>();
        addMappings(values, "input", registration.inputs(), registration.inputSources());
        addMappings(values, "output", registration.outputs(), registration.outputTargets());
        return OrchestrationRequestDigests.hash(fields, values);
    }

    private static void addMappings(Map<String, Object> values, String prefix, Map<String, ValueType> types,
                                    Map<String, Set<String>> mappings) {
        // Worker variable names cannot contain a dot, so no two names or mapping pairs share a key.
        types.forEach((name, type) -> {
            String field = prefix + "." + name;
            values.put(field + ".type", type.name());
            mappings.get(name).forEach(mapped -> values.put(field + ".maps." + mapped, true));
        });
    }

    /** The A4 Worker fingerprint can combine these fields with independently verified deployment evidence. */
    public static final class Route {
        private final AgentDispatchBinding binding;
        private final String registrationSha256;

        private Route(AgentDispatchBinding binding, String registrationSha256) {
            this.binding = binding;
            this.registrationSha256 = registrationSha256;
        }

        public AgentDispatchBinding binding() { return binding; }
        public String registrationSha256() { return registrationSha256; }

        @Override public String toString() { return "ProcessAgentLocalWorkerRouteEvidence.Route[redacted]"; }
    }
}
