package top.egon.cola.platform.tianji.agent.server.context;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcProperties;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit grants map a verified identity realm to one Tianshu transport scope and immutable resource identity. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.context.tianshu")
public record TianshuContextProperties(List<Connection> connections, List<Binding> bindings) {
    public TianshuContextProperties {
        connections = connections == null ? List.of() : List.copyOf(connections);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (connections.size() > 32 || bindings.size() > 1000) throw invalid();
        Set<String> names = new HashSet<>();
        for (var connection : connections) if (!names.add(connection.id())) throw invalid();
        Set<List<Object>> keys = new HashSet<>();
        for (var binding : bindings) {
            if (!names.contains(binding.connectionId()) || !keys.add(List.of(binding.tenantId(), binding.issuerFingerprint(), binding.kind(), binding.key()))) throw invalid();
        }
    }

    public record Connection(String id, long tianshuTenantId, String bizCode, String env, String appCode,
                              DdcRpcProperties rpc, int maxConcurrent) {
        public Connection {
            identifier(id);
            new DdcAiClientScope(tianshuTenantId, bizCode, env, appCode);
            maxConcurrent = maxConcurrent == 0 ? 4 : maxConcurrent;
            if (rpc == null || rpc.getTls() == null || !rpc.getTls().isEnabled() || rpc.getTls().isDevelopmentPlaintext()
                    || rpc.getAuth() == null || !rpc.getAuth().isEnabled() || maxConcurrent < 1 || maxConcurrent > 16
                    || rpc.getDefaultTimeout() == null || rpc.getDefaultTimeout().compareTo(Duration.ofMillis(100)) < 0
                    || rpc.getDefaultTimeout().compareTo(Duration.ofSeconds(30)) > 0 || rpc.getConnectTimeout() == null
                    || rpc.getConnectTimeout().compareTo(Duration.ofMillis(100)) < 0 || rpc.getConnectTimeout().compareTo(rpc.getDefaultTimeout()) > 0
                    || rpc.getShutdownTimeout() == null || rpc.getShutdownTimeout().compareTo(Duration.ofSeconds(5)) > 0
                    || rpc.getMaxInboundMessageSize() < 1024 || rpc.getMaxInboundMessageSize() > 4_194_304) throw invalid();
            rpc.requireTarget();
        }
        public DdcAiClientScope scope() { return new DdcAiClientScope(tianshuTenantId, bizCode, env, appCode); }
        @Override public String toString() { return "TianshuContextConnection[redacted]"; }
    }

    public record Binding(long tenantId, URI issuer, String connectionId, ContextProvider.Kind kind, String key,
                           String namespace, String resourceKey, long resourceId) {
        public Binding {
            identifier(connectionId); identifier(key);
            if (issuer == null || kind != ContextProvider.Kind.PROMPT && kind != ContextProvider.Kind.SKILL
                    || namespace == null || namespace.isBlank() || namespace.length() > 128 || !namespace.equals(namespace.trim())
                    || namespace.chars().anyMatch(Character::isISOControl) || resourceKey == null
                    || !resourceKey.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || resourceId < 1) throw invalid();
            TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "context-binding", issuer.toASCIIString());
        }
        public String issuerFingerprint() {
            return TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "context-binding", issuer.toASCIIString()).issuerFingerprint();
        }
    }

    private static void identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tianshu context bindings are invalid"); }
}
