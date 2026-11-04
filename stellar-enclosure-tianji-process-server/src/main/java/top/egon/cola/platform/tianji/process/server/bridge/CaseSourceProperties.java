package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Operator-owned source and credential references; no model or HTTP request chooses the executor. */
@ConfigurationProperties(prefix = "stellar.tianji.process.cases", ignoreUnknownFields = false)
public record CaseSourceProperties(Boolean enabled, Boolean newStartsEnabled, List<Source> sources,
                                   Duration pollInterval, Duration retryDelay, Integer batchSize, Integer maxFailures) {
    public CaseSourceProperties {
        enabled = Boolean.TRUE.equals(enabled); newStartsEnabled = Boolean.TRUE.equals(newStartsEnabled);
        sources = sources == null ? List.of() : List.copyOf(sources);
        pollInterval = pollInterval == null ? Duration.ofSeconds(1) : pollInterval; retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
        batchSize = batchSize == null ? 32 : batchSize; maxFailures = maxFailures == null ? 10 : maxFailures;
        if (newStartsEnabled && !enabled || sources.size() > 256 || batchSize < 1 || batchSize > 100 || maxFailures < 1 || maxFailures > 1000) throw invalid();
        duration(pollInterval); duration(retryDelay);
    }
    public record Source(long tenantId, String sourceId, String releaseKey, String parentDefinitionKey, String parentModelSha256,
                         String sourceElementId, String caseDefinitionKey, String caseModelSha256, URI issuer,
                         String registrationId, String serviceSubject, String appId, URI resourceUri) {
        public Source {
            new CaseSourceRegistry.BindingKey(tenantId, releaseKey, parentDefinitionKey, parentModelSha256, sourceElementId, caseDefinitionKey, caseModelSha256);
            command(sourceId); command(registrationId); text(appId, 128); uri(issuer, 2048); uri(resourceUri, 512);
            ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, serviceSubject, issuer.toASCIIString());
        }
        public CaseSourceRegistry.BindingKey binding() { return new CaseSourceRegistry.BindingKey(tenantId, releaseKey, parentDefinitionKey, parentModelSha256, sourceElementId, caseDefinitionKey, caseModelSha256); }
        @Override public String toString() { return "CaseSourceProperties.Source[redacted]"; }
    }
    private static void duration(Duration value) { if (value.compareTo(Duration.ofMillis(100)) < 0 || value.compareTo(Duration.ofHours(1)) > 0 || value.toNanos() % 1_000_000 != 0) throw invalid(); }
    private static void command(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid(); }
    private static void text(String value, int maximum) { if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim()) || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) || value.chars().anyMatch(Character::isISOControl)) throw invalid(); }
    private static void uri(URI value, int maximum) {
        if (value == null || !"https".equals(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.normalize().equals(value) || !value.toASCIIString().equals(value.toString()) || value.toString().length() > maximum) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case source settings are invalid"); }
}
