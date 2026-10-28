package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

/** Finite operator-owned bindings; the request cannot choose its service or source identity. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.linked", ignoreUnknownFields = false)
public record TianjiLinkedDispatchProperties(boolean enabled, boolean submissionEnabled, List<Binding> bindings) {
    public TianjiLinkedDispatchProperties {
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (bindings.size() > 1_024) throw invalid();
        Set<List<Object>> identities = new HashSet<>();
        for (Binding binding : bindings) {
            if (binding == null || !identities.add(List.of(binding.issuer(), binding.tenantId(), binding.clientId(), binding.sourceId()))
                    || submissionEnabled && binding.definitions().isEmpty()) throw invalid();
        }
    }

    public record Binding(URI issuer, long tenantId, String clientId, String appId, String businessCode,
                          String applicationCode, String environment, String sourceId,
                          Set<WorkerScopeTypeEnum> scopeTypes, List<Definition> definitions) {
        public Binding {
            if (issuer == null || !"https".equalsIgnoreCase(issuer.getScheme()) || issuer.getHost() == null
                    || issuer.getUserInfo() != null || issuer.getQuery() != null || issuer.getFragment() != null
                    || !issuer.normalize().equals(issuer) || !issuer.toASCIIString().equals(issuer.toString())
                    || issuer.toString().length() > 2048 || tenantId <= 0 || !text(clientId, 128) || !text(appId, 128)
                    || !text(businessCode, 128) || !text(applicationCode, 128) || !text(environment, 128)
                    || sourceId == null || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
            scopeTypes = scopeTypes == null ? Set.of() : Set.copyOf(scopeTypes);
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
            if (scopeTypes.isEmpty() || definitions.size() > 256) throw invalid();
            Set<List<Object>> keys = new HashSet<>();
            for (Definition definition : definitions) {
                if (definition == null || !keys.add(List.of(definition.key(), definition.version()))) throw invalid();
            }
        }
        @Override public String toString() { return "TianjiLinkedDispatchProperties.Binding[redacted]"; }
    }

    public record Definition(String key, long version, String contentSha256) {
        public Definition {
            if (!text(key, 128) || version <= 0 || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) throw invalid();
        }
    }

    private static boolean text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim())) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_LINKED_BINDINGS_INVALID"); }
}
