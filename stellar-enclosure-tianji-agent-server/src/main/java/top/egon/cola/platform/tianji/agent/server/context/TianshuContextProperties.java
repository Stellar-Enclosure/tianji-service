package top.egon.cola.platform.tianji.agent.server.context;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.rpc.consumer.reference.RpcServiceTargetVO;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit grants map a verified identity realm to one Tianshu transport scope and immutable resource identity. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.context.tianshu", ignoreUnknownFields = false)
@Validated
public record TianshuContextProperties(boolean enabled,
                                       @Size(max = 32) List<@Valid Connection> connections,
                                       @Size(max = 1000) List<@Valid Binding> bindings) {
    public TianshuContextProperties {
        connections = connections == null ? List.of() : List.copyOf(connections);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (connections.size() > 32 || bindings.size() > 1000) throw invalid();
        var declared = new HashMap<String, Connection>();
        for (var connection : connections) if (declared.putIfAbsent(connection.id(), connection) != null) throw invalid();
        Set<List<Object>> keys = new HashSet<>();
        for (var binding : bindings) {
            var connection = declared.get(binding.connectionId());
            if (connection == null || connection.identityTenantId() != binding.tenantId()
                    || !keys.add(List.of(binding.tenantId(), binding.issuerFingerprint(), binding.kind(), binding.key()))) throw invalid();
        }
    }

    public record Connection(@NotBlank String id, @Positive long tianshuTenantId,
                              @NotBlank String bizCode, @NotBlank String env, @NotBlank String appCode,
                              @Positive long identityTenantId, @NotBlank String bindingId, @Positive long bindingVersion,
                              @NotNull Duration timeout, @Min(1) @Max(16) int maxConcurrent) {
        public Connection {
            identifier(id);
            new DdcAiClientScope(tianshuTenantId, bizCode, env, appCode);
            maxConcurrent = maxConcurrent == 0 ? 4 : maxConcurrent;
            if (identityTenantId <= 0 || !RpcServiceTargetVO.isBindingId(bindingId) || bindingVersion <= 0
                    || timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0
                    || timeout.compareTo(Duration.ofSeconds(5)) > 0 || maxConcurrent < 1 || maxConcurrent > 16) throw invalid();
        }
        public DdcAiClientScope scope() { return new DdcAiClientScope(tianshuTenantId, bizCode, env, appCode); }
        @Override public String toString() { return "TianshuContextConnection[redacted]"; }
    }

    public record Binding(@Positive long tenantId, @NotNull URI issuer, @NotBlank String connectionId,
                           @NotNull ContextProvider.Kind kind, @NotBlank String key, @NotBlank String namespace,
                           String ownerAppCode, @NotBlank String resourceKey, @Positive long resourceId) {
        public Binding {
            identifier(connectionId); identifier(key);
            if (issuer == null || kind != ContextProvider.Kind.PROMPT && kind != ContextProvider.Kind.SKILL
                    || namespace == null || namespace.isBlank() || namespace.length() > 128 || !namespace.equals(namespace.trim())
                    || namespace.chars().anyMatch(Character::isISOControl) || resourceKey == null
                    || !resourceKey.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || resourceId < 1) throw invalid();
            if (ownerAppCode != null) identifier(ownerAppCode);
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
