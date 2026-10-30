package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Operator-owned dispatch and credential references; no model or request supplies these bindings. */
@ConfigurationProperties(prefix = "stellar.tianji.process.agent-bridge")
public record ProcessAgentBridgeProperties(List<Binding> bindings, Boolean dispatchEnabled, int dispatchConcurrency,
                                           int maintenanceConcurrency, int inboxBatchSize, Duration pollInterval,
                                           Duration shutdownTimeout, Duration acknowledgementReserve, Duration maxClockSkew,
                                           Duration inboxRetryDelay, int inboxMaxFailures) {
    public ProcessAgentBridgeProperties {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        dispatchEnabled = dispatchEnabled == null || dispatchEnabled;
        dispatchConcurrency = dispatchConcurrency == 0 ? 4 : dispatchConcurrency;
        maintenanceConcurrency = maintenanceConcurrency == 0 ? 2 : maintenanceConcurrency;
        inboxBatchSize = inboxBatchSize == 0 ? 16 : inboxBatchSize;
        inboxMaxFailures = inboxMaxFailures == 0 ? 12 : inboxMaxFailures;
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        shutdownTimeout = shutdownTimeout == null ? Duration.ofSeconds(5) : shutdownTimeout;
        acknowledgementReserve = acknowledgementReserve == null ? Duration.ofSeconds(3) : acknowledgementReserve;
        maxClockSkew = maxClockSkew == null ? Duration.ofSeconds(2) : maxClockSkew;
        inboxRetryDelay = inboxRetryDelay == null ? Duration.ofSeconds(2) : inboxRetryDelay;
        if (bindings.size() > 1000 || dispatchConcurrency < 1 || dispatchConcurrency > 32 || maintenanceConcurrency < 1
                || maintenanceConcurrency > 32 || inboxBatchSize < 1 || inboxBatchSize > 32 || inboxMaxFailures < 1 || inboxMaxFailures > 1000) throw invalid();
        duration(pollInterval, Duration.ofMillis(250), Duration.ofHours(1));
        duration(shutdownTimeout, Duration.ZERO, Duration.ofSeconds(30));
        duration(acknowledgementReserve, Duration.ofMillis(100), Duration.ofSeconds(30));
        duration(maxClockSkew, Duration.ZERO, Duration.ofSeconds(30));
        duration(inboxRetryDelay, Duration.ofMillis(100), Duration.ofHours(1));
        Set<List<Object>> keys = new HashSet<>();
        for (var item : bindings) {
            var dispatch = item.dispatch();
            if (!keys.add(List.of(dispatch.actor(), dispatch.topic(), dispatch.schemaVersion()))) throw invalid();
        }
    }

    public record Binding(long tenantId, URI issuer, String subjectId, String sourceId, String topic, String schemaVersion,
                           String definitionKey, long definitionVersion, String definitionContentSha256, String instruction,
                           Duration submissionTimeout, URI baseUri, URI resourceUri, String registrationId, Duration httpTimeout,
                           int maxTokenCalls) {
        public Binding {
            uri(issuer); uri(baseUri); uri(resourceUri);
            submissionTimeout = submissionTimeout == null ? Duration.ofMinutes(2) : submissionTimeout;
            httpTimeout = httpTimeout == null ? Duration.ofSeconds(3) : httpTimeout;
            maxTokenCalls = maxTokenCalls == 0 ? 4 : maxTokenCalls;
            if (registrationId == null || !registrationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || resourceUri.toASCIIString().length() > 512 || maxTokenCalls < 1 || maxTokenCalls > 8) throw invalid();
            duration(httpTimeout, Duration.ofMillis(100), Duration.ofSeconds(30));
            new AgentDispatchBinding(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer.toASCIIString()),
                    sourceId, topic, schemaVersion, definitionKey, definitionVersion, definitionContentSha256, instruction, submissionTimeout);
        }

        public AgentDispatchBinding dispatch() {
            return new AgentDispatchBinding(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer.toASCIIString()),
                    sourceId, topic, schemaVersion, definitionKey, definitionVersion, definitionContentSha256, instruction, submissionTimeout);
        }
        @Override public String toString() { return "ProcessAgentBridgeBinding[redacted]"; }
    }

    private static void duration(Duration value, Duration min, Duration max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0 || value.toNanos() % 1_000_000 != 0) throw invalid();
    }
    private static void uri(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null || !value.normalize().equals(value)
                || !value.toASCIIString().equals(value.toString()) || value.toASCIIString().length() > 2048) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process Agent bridge settings are invalid"); }
}
