package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit source-resource bindings for process SERVICE execution; disabled by default. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.service-execution", ignoreUnknownFields = false)
public record TianjiServiceExecutionProperties(boolean enabled, List<Source> sources) {
    public TianjiServiceExecutionProperties {
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.size() > 1_024 || enabled && sources.isEmpty()) throw invalid();
        Set<List<Object>> identities = new HashSet<>();
        for (var source : sources) {
            if (source == null || !identities.add(List.of(source.issuer().toASCIIString(), source.tenantId(), source.clientId()))) {
                throw invalid();
            }
        }
    }

    public record Source(URI issuer, long tenantId, String clientId, URI resourceUri) {
        public Source {
            issuer = secure(issuer); resourceUri = secure(resourceUri);
            if (tenantId <= 0 || !text(clientId)) throw invalid();
        }
        @Override public String toString() { return "TianjiServiceExecutionProperties.Source[redacted]"; }
    }

    private static URI secure(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.normalize().equals(value) || !value.toASCIIString().equals(value.toString())
                || value.toASCIIString().length() > 2048) throw invalid();
        return value;
    }
    private static boolean text(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isISOControl(character) || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_SERVICE_EXECUTION_BINDINGS_INVALID"); }
}
