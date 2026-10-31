package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolRegistry;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Fixed host selection only; neither configured metadata nor a resolved binding grants tool authority. */
public final class TianjiToolGrantBindings {
    private final ToolRegistry registry;
    private final List<BindingVO> bindings;
    private final URI resource;
    private final TianjiServiceExecutionBindings services;
    private final boolean serviceBindings;

    public TianjiToolGrantBindings(Collection<ToolDescriptor> registrations, Collection<BindingVO> bindings, URI resource, TianjiServiceExecutionBindings services) {
        if (registrations == null || registrations.isEmpty() || registrations.size() > 256 || bindings == null || bindings.isEmpty() || bindings.size() > 256) throw invalid();
        var fixed = new ArrayList<>(registrations); registry = new ToolRegistry(fixed);
        this.resource = TianjiExecutionAuthorityBindings.resource(resource); this.services = services;
        var references = new HashSet<DefinitionResourceRef>(); for (var descriptor : fixed) references.add(descriptor.reference());
        var configured = new ArrayList<>(bindings); var slots = new HashSet<List<Object>>(); var hashes = new HashMap<List<Object>, String>();
        for (var binding : configured) {
            if (binding == null || !references.contains(binding.tool()) || binding.principalType() == TrustedAgentActor.SubjectType.SERVICE && services == null) throw invalid();
            var origin = List.<Object>of(binding.tenantId(), binding.issuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion());
            var slot = new ArrayList<>(origin); slot.add(binding.tool().key()); slot.add(binding.tool().version());
            String oldHash = hashes.putIfAbsent(origin, binding.definitionSha256());
            if (!slots.add(List.copyOf(slot)) || oldHash != null && !oldHash.equals(binding.definitionSha256())) throw invalid();
        }
        this.bindings = List.copyOf(configured);
        serviceBindings = this.bindings.stream().anyMatch(binding -> binding.principalType() == TrustedAgentActor.SubjectType.SERVICE);
    }

    /** Resolves only trusted ledger material; matching metadata is not a current permission decision. */
    public List<ResolvedToolResult> resolve(AgentLedgerService.ExecutionMaterial material) {
        try {
            requireMaterial(material); var run = material.authorization(); var snapshot = material.snapshot();
            var visible = registry.visible(snapshot); if (visible.isEmpty()) return List.of();
            var subject = run.actor().subjectType() == TrustedAgentActor.SubjectType.SERVICE
                    ? services == null ? null : services.require(run, snapshot.processLink()) : null;
            var result = new ArrayList<ResolvedToolResult>();
            for (var descriptor : visible.values()) {
                var reference = descriptor.reference();
                var matches = bindings.stream().filter(binding -> binding.tenantId() == run.actor().tenantId()
                        && binding.issuerFingerprint().equals(run.actor().issuerFingerprint()) && binding.principalType() == run.actor().subjectType()
                        && binding.definitionKey().equals(run.definitionKey()) && binding.definitionVersion() == run.definitionVersion()
                        && binding.definitionSha256().equals(run.definitionSha256()) && binding.tool().equals(reference)).toList();
                if (matches.size() != 1) throw unavailable(); var selected = matches.getFirst();
                result.add(new ResolvedToolResult(descriptor, new ToolExecutionGrant.BindingVO(run, reference, descriptor.policy().bindingSha256(),
                        selected.purpose(), resource, selected.authorizationResourceKey(), subject)));
            }
            return List.copyOf(result);
        } catch (RuntimeException uncertain) { throw unavailable(); }
    }

    public List<BindingVO> bindings() { return bindings; }
    public boolean hasServiceBindings() { return serviceBindings; }
    public boolean usesServices(TianjiServiceExecutionBindings candidate) { return services == candidate; }
    public boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return services == null || services.usesIssuerRegistry(candidate); }
    public boolean usesResource(URI candidate) { return candidate != null && resource.toASCIIString().equals(candidate.toASCIIString()); }

    private static void requireMaterial(AgentLedgerService.ExecutionMaterial material) {
        if (material == null || material.run() == null || material.authorization() == null || material.snapshot() == null) throw unavailable();
        var run = material.authorization(); var stored = material.run(); var snapshot = material.snapshot(); var definition = snapshot.definition();
        if ("LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(stored.runId()) || !run.definitionKey().equals(stored.definitionKey())
                || run.definitionVersion() != stored.definitionVersion() || run.sessionRequired() != (stored.sessionId() != null) || !run.deadlineAt().equals(stored.deadlineAt())
                || !run.definitionKey().equals(definition.definitionKey()) || run.definitionVersion() != definition.version()
                || !run.definitionSha256().equals(snapshot.definitionContentSha256()) || !run.policyVersionRef().equals(definition.dependencies().policyVersionRef())) throw unavailable();
    }

    public record BindingVO(long tenantId, String issuerFingerprint, TrustedAgentActor.SubjectType principalType, String definitionKey,
                            long definitionVersion, String definitionSha256, DefinitionResourceRef tool, String purpose, String authorizationResourceKey) {
        public BindingVO {
            if (tenantId < 1 || !hash(issuerFingerprint) || principalType == null || !text(definitionKey, 128) || definitionVersion < 1 || !hash(definitionSha256)
                    || tool == null || !key(tool.key()) || purpose == null || !purpose.matches("TIANJI_TOOL_[A-Z][A-Z0-9_]{0,83}") || !key(authorizationResourceKey)) throw invalid();
        }
        @Override public String toString() { return "TianjiToolGrantBindings.BindingVO[redacted]"; }
    }

    public record ResolvedToolResult(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding) {
        public ResolvedToolResult {
            if (descriptor == null || binding == null || !descriptor.reference().equals(binding.tool()) || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256())) throw invalid();
        }
        @Override public String toString() { return "TianjiToolGrantBindings.ResolvedToolResult[redacted]"; }
    }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_TOOL_GRANT_BINDING_INVALID"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_TOOL_GRANT_BINDING_UNAVAILABLE"); }
}
