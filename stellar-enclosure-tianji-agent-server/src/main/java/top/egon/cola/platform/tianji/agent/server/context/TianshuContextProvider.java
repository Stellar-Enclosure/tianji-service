package top.egon.cola.platform.tianji.agent.server.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.component.tianshu.error.DdcException;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeTypeEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.validation.AiContentValidator;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** Adapter for literal published text and Skill files. It neither evaluates templates nor executes package assets. */
public final class TianshuContextProvider implements ContextProvider {
    private static final byte[] CONTENT_DOMAIN = "tianshu-ai-content-v1".getBytes(StandardCharsets.UTF_8);
    private static final AiContentValidator VALIDATOR = new AiContentValidator();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<Key, TianshuContextProperties.Binding> bindings;
    private final Map<String, Ports> connections;
    private final TrustedIssuerRegistry issuers;
    private final Clock clock;

    public TianshuContextProvider(TianshuContextProperties properties, Map<String, Ports> connections, TrustedIssuerRegistry issuers, Clock clock) {
        this.connections = Map.copyOf(connections);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clock = Objects.requireNonNull(clock, "clock");
        Map<Key, TianshuContextProperties.Binding> values = new HashMap<>();
        for (var binding : properties.bindings()) {
            var ports = this.connections.get(binding.connectionId());
            if (ports == null || values.putIfAbsent(new Key(binding.tenantId(), binding.issuerFingerprint(), binding.kind(), binding.key()), binding) != null) throw unavailable();
            requireIssuer(binding, issuers); ports.requireScope(binding.kind());
        }
        this.bindings = Map.copyOf(values);
    }

    @Override public List<Fragment> provide(Request request) {
        requireNoTransaction();
        long deadline = System.nanoTime() + request.timeout().toNanos();
        List<Fragment> fragments = new ArrayList<>();
        for (var reference : request.snapshot().definition().dependencies().prompts()) fragments.add(read(request, Kind.PROMPT, reference, deadline));
        for (var reference : request.snapshot().definition().dependencies().skills()) fragments.add(read(request, Kind.SKILL, reference, deadline));
        return List.copyOf(fragments);
    }

    @Override public void revalidate(Request request, List<Fragment> fragments) {
        requireNoTransaction();
        long deadline = System.nanoTime() + request.timeout().toNanos();
        for (var previous : fragments) {
            List<DefinitionResourceRef> declared = previous.kind() == Kind.PROMPT ? request.snapshot().definition().dependencies().prompts()
                    : previous.kind() == Kind.SKILL ? request.snapshot().definition().dependencies().skills() : List.of();
            var reference = declared.stream().filter(value -> value.key().equals(previous.sourceId())
                    && Long.toString(value.version()).equals(previous.sourceVersion()) && value.contentSha256().equals(previous.sourceSha256())).findFirst().orElseThrow(TianshuContextProvider::unavailable);
            var current = read(request, previous.kind(), reference, deadline);
            if (!current.provenanceId().equals(previous.provenanceId()) || !current.text().equals(previous.text())) throw unavailable();
        }
    }

    private Fragment read(Request request, Kind kind, DefinitionResourceRef reference, long deadline) {
        var actor = request.authorization().actor();
        var binding = bindings.get(new Key(actor.tenantId(), actor.issuerFingerprint(), kind, reference.key()));
        if (binding == null) throw denied();
        var ports = connections.get(binding.connectionId());
        requireIssuer(binding, issuers); ports.requireScope(kind);
        if (deadline - System.nanoTime() < ports.timeout.toNanos()) throw unavailable();
        if (!ports.slots.tryAcquire()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Tianshu context source capacity is unavailable");
        try {
            var query = new DdcAiResourceQuery(binding.namespace(), binding.resourceKey(), reference.version(), binding.resourceId());
            String text;
            Long versionId;
            if (kind == Kind.PROMPT) {
                var value = ports.prompt.get(query);
                if (value == null) throw unavailable();
                match(binding, reference, value.getResourceId(), value.getResourceKey(), value.getVersionNo(), value.getContentSha256());
                text = value.getTemplate(); versionId = value.getVersionId();
                var content = DdcAiContentDTO.builder().promptText(text).nodes(List.of()).build();
                if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > 1_048_576
                        || !VALIDATOR.isValid(content, null) || !reference.contentSha256().equals(contentHash(text, List.of()))) throw unavailable();
            } else {
                var value = ports.skill.get(query);
                if (value == null || value.getFiles() == null || value.getFiles().size() > 257) throw unavailable();
                match(binding, reference, value.getResourceId(), value.getResourceKey(), value.getVersionNo(), value.getContentSha256());
                versionId = value.getVersionId();
                var nodes = value.getFiles().stream().map(node -> DdcAiNodeDTO.builder().path(node.getPath()).nodeType(node.getNodeType()).content(node.getContent()).build()).toList();
                if (!VALIDATOR.isValid(DdcAiContentDTO.builder().nodes(nodes).build(), null)) throw unavailable();
                var sorted = new ArrayList<>(nodes);
                sorted.sort((left, right) -> Arrays.compareUnsigned(left.getPath().getBytes(StandardCharsets.UTF_8), right.getPath().getBytes(StandardCharsets.UTF_8)));
                if (!reference.contentSha256().equals(contentHash(null, sorted))) throw unavailable();
                text = "Published Skill files (read-only text):\n" + JSON.writeValueAsString(sorted.stream()
                        .map(node -> new SkillTextNode(node.getPath(), node.getNodeType().name(), node.getContent())).toList());
            }
            requireIssuer(binding, issuers); ports.requireScope(kind);
            if (versionId == null || versionId < 1 || deadline - System.nanoTime() < 0) throw unavailable();
            return new Fragment(kind, reference.key(), Long.toString(reference.version()), reference.contentSha256(),
                    provenance(ports.scope, binding, versionId, reference.version()), text, clock.instant());
        } catch (AgentLedgerException failure) { throw failure; }
        catch (DdcException failure) { throw "TIANSHU_AI_SCOPE_FORBIDDEN".equals(failure.getStatus()) ? denied() : unavailable(); }
        catch (RuntimeException | JsonProcessingException failure) { throw unavailable(); }
        finally { ports.slots.release(); }
    }

    static void requireIssuer(TianshuContextProperties.Binding binding, TrustedIssuerRegistry issuers) {
        var trusted = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(TianshuContextProvider::unavailable);
        if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(binding.tenantId()).equals(trusted.tenantId())) throw unavailable();
    }
    private static void match(TianshuContextProperties.Binding binding, DefinitionResourceRef reference, Long resourceId, String key, Long version, String hash) {
        if (resourceId == null || resourceId != binding.resourceId() || !binding.resourceKey().equals(key) || version == null
                || version != reference.version() || !reference.contentSha256().equals(hash)) throw unavailable();
    }

    /** Recomputes Tianshu's v1 domain-separated wire digest, including the complete Skill tree. */
    static String contentHash(String prompt, List<DdcAiNodeDTO> sortedNodes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.write(CONTENT_DOMAIN); output.writeByte(0); output.writeByte(prompt == null ? 2 : 1);
                if (prompt != null) write(output, prompt);
                else {
                    output.writeInt(sortedNodes.size());
                    for (var node : sortedNodes) {
                        write(output, node.getPath());
                        output.writeByte(node.getNodeType() == DdcAiNodeTypeEnum.DIRECTORY ? 0 : 1);
                        if (node.getNodeType() == DdcAiNodeTypeEnum.FILE) write(output, node.getContent());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException failure) { throw new IllegalStateException("Tianshu content digest is unavailable"); }
    }
    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeLong(bytes.length); output.write(bytes);
    }
    private static String provenance(DdcAiClientScope scope, TianshuContextProperties.Binding binding, long versionId, long version) {
        var value = new StringBuilder("tianshu:v1:");
        for (var field : List.of(Long.toString(scope.tenantId()), scope.bizCode(), scope.env(), scope.appCode(), binding.namespace(),
                binding.resourceKey(), Long.toString(binding.resourceId()), Long.toString(versionId), Long.toString(version))) {
            value.append(field.length()).append(':').append(field);
        }
        return value.toString();
    }

    public static final class Ports {
        private final DdcAiClientScope scope;
        private final DdcPromptClient prompt;
        private final DdcSkillClient skill;
        private final Duration timeout;
        private final Semaphore slots;
        public Ports(DdcAiClientScope scope, DdcPromptClient prompt, DdcSkillClient skill, Duration timeout, int maxConcurrent) {
            this.scope = Objects.requireNonNull(scope, "scope"); this.prompt = prompt; this.skill = skill;
            if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0
                    || maxConcurrent < 1 || maxConcurrent > 16) throw unavailable();
            this.timeout = timeout; this.slots = new Semaphore(maxConcurrent);
        }
        private void requireScope(Kind kind) {
            try {
                var actual = kind == Kind.PROMPT ? Objects.requireNonNull(prompt).scope() : Objects.requireNonNull(skill).scope();
                if (!scope.equals(actual)) throw unavailable();
            } catch (RuntimeException failure) { throw unavailable(); }
        }
    }
    private record Key(long tenant, String issuer, Kind kind, String key) { }
    private record SkillTextNode(String path, String nodeType, String content) { }
    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tianshu context reads must run outside a database transaction");
    }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Published Tianshu context source is unavailable"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Published Tianshu context source is not authorized"); }
}
