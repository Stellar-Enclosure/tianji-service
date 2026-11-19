package top.egon.cola.platform.tianji.agent.server.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeDTO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.ai.validation.AiContentValidator;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
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
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.Semaphore;

/** Adapter for literal published text and Skill files. It neither evaluates templates nor executes package assets. */
@Slf4j
public final class TianshuContextProvider implements ContextProvider {
    private static final int MAX_SNAPSHOT_BYTES = 1_048_576;
    private static final AiContentValidator VALIDATOR = new AiContentValidator();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Map<Key, TianshuContextProperties.Binding> bindings;
    private final Map<String, Ports> connections;
    private final TrustedIssuerRegistry issuers;
    private final Clock clock;

    @Override public Set<Kind> historicalKinds() { return Set.of(Kind.PROMPT, Kind.SKILL); }
    @Override public void revalidateHistory(Request request, List<AgentContextSourceDTO> sources) {
        if (!request.historyValidated() || sources.size() > 256) throw unavailable(); var current = provide(request);
        for (var source : List.copyOf(sources)) {
            var matching = current.stream().filter(fragment -> fragment.kind().name().equals(source.kind().name()) && fragment.sourceId().equals(source.sourceId())
                    && fragment.sourceVersion().equals(source.sourceVersion()) && fragment.sourceSha256().equals(source.sourceSha256()) && fragment.provenanceId().equals(source.provenanceId())).toList();
            if (matching.size() != 1 || !modelTextHash(matching.getFirst().modelText()).equals(source.modelTextSha256())
                    || matching.getFirst().modelText().getBytes(StandardCharsets.UTF_8).length != source.utf8Bytes()) throw unavailable();
        }
    }
    private static String modelTextHash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw unavailable(); }
    }

    public TianshuContextProvider(TianshuContextProperties properties, Map<String, Ports> connections, TrustedIssuerRegistry issuers, Clock clock) {
        this.connections = Map.copyOf(connections);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clock = Objects.requireNonNull(clock, "clock");
        Map<Key, TianshuContextProperties.Binding> values = new HashMap<>();
        for (var binding : properties.bindings()) {
            var ports = this.connections.get(binding.connectionId());
            if (ports == null || values.putIfAbsent(new Key(binding.tenantId(), binding.issuerFingerprint(), binding.kind(), binding.key()), binding) != null) throw unavailable();
            requireIssuer(binding, issuers);
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

    @Override public String policyFingerprint(Request request) {
        requireNoTransaction();
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                write(output, "TIANJI_TIANSHU_SI_LITERAL_CONTEXT_V1");
                var declared = new ArrayList<Map.Entry<Kind, DefinitionResourceRef>>();
                request.snapshot().definition().dependencies().prompts().forEach(reference -> declared.add(Map.entry(Kind.PROMPT, reference)));
                request.snapshot().definition().dependencies().skills().forEach(reference -> declared.add(Map.entry(Kind.SKILL, reference)));
                output.writeInt(declared.size());
                for (var item : declared) {
                    var actor = request.authorization().actor();
                    var binding = bindings.get(new Key(actor.tenantId(), actor.issuerFingerprint(), item.getKey(), item.getValue().key()));
                    if (binding == null) throw denied();
                    write(output, item.getKey().name()); write(output, item.getValue().key());
                    write(output, Long.toString(actor.tenantId())); write(output, actor.issuerFingerprint());
                    write(output, binding.connectionId());
                    var ports = connections.get(binding.connectionId());
                    write(output, ports.authority.target().bindingId());
                    write(output, Long.toString(ports.authority.target().version()));
                    for (var coordinate : coordinates(ports.scope, binding)) write(output, coordinate);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException failure) { throw unavailable(); }
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
        requireIssuer(binding, issuers);
        if (deadline - System.nanoTime() < ports.timeout.toNanos()) throw unavailable();
        if (!ports.slots.tryAcquire()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Tianshu context source capacity is unavailable");
        try {
            var siKind = DdcSiKindEnum.valueOf(kind.name());
            var snapshot = ports.read(binding, siKind, reference.version());
            String text = checkedText(ports.scope, binding, siKind, reference, snapshot);
            requireIssuer(binding, issuers);
            if (deadline - System.nanoTime() < 0) throw unavailable();
            return new Fragment(kind, reference.key(), Long.toString(reference.version()), reference.contentSha256(),
                    provenance(ports.scope, binding, snapshot), text, clock.instant());
        } catch (AgentLedgerException failure) {
            throw failure;
        } catch (StatusRuntimeException failure) {
            if (failure.getStatus().getCode() == Status.Code.PERMISSION_DENIED) throw denied();
            throw unavailable();
        } catch (RuntimeException | JsonProcessingException failure) {
            log.debug("Rejected a Tianshu context source");
            throw unavailable();
        } finally {
            ports.slots.release();
        }
    }

    private static String checkedText(DdcAiClientScope scope, TianshuContextProperties.Binding binding,
            DdcSiKindEnum kind, DefinitionResourceRef reference, DdcSiResourceDescriptor snapshot) throws JsonProcessingException {
        if (!Objects.equals(snapshot.getTenantId(), scope.tenantId())
                || !scope.bizCode().equals(snapshot.getBizCode()) || !scope.env().equals(snapshot.getEnvCode())
                || !binding.namespace().equals(snapshot.getNamespaceCode())
                || !Objects.equals(binding.resourceId(), snapshot.getResourceId())
                || !binding.resourceKey().equals(snapshot.getResourceKey())
                || !Objects.equals(binding.ownerAppCode(), snapshot.getOwnerAppCode())
                || kind != snapshot.getKind() || snapshot.getStatus() == null || !snapshot.getStatus().isQueryableByRuntime()
                || !Objects.equals(reference.version(), snapshot.getResolvedVersionNo())
                || snapshot.getDependencyManifest() == null || !snapshot.isExecutionSnapshotShapeValid()
                || !reference.contentSha256().equals(snapshot.getDigestSha256())) throw unavailable();
        String body = snapshot.getSpecJson();
        if (body == null || body.length() > MAX_SNAPSHOT_BYTES) throw unavailable();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES || !reference.contentSha256().equals(modelTextHash(body))) throw unavailable();
        JsonNode spec = DdcConfigCanonicalJson.parse(bytes);
        if (!spec.isObject()) throw unavailable();
        return kind == DdcSiKindEnum.PROMPT ? literal(spec.get("promptText")) : skillText(spec);
    }

    private static String skillText(JsonNode spec) throws JsonProcessingException {
        if (spec.has("skillMarkdown")) {
            if (spec.has("files")) throw unavailable();
            return literal(spec.get("skillMarkdown"));
        }
        JsonNode files = spec.get("files");
        if (files == null || !files.isArray() || files.size() > 257) throw unavailable();
        var nodes = new ArrayList<DdcAiNodeDTO>(files.size());
        for (JsonNode file : files) nodes.add(JSON.treeToValue(file, DdcAiNodeDTO.class));
        if (!VALIDATOR.isValid(DdcAiContentDTO.builder().nodes(nodes).build(), null)) throw unavailable();
        nodes.sort((left, right) -> Arrays.compareUnsigned(left.getPath().getBytes(StandardCharsets.UTF_8),
                right.getPath().getBytes(StandardCharsets.UTF_8)));
        return "Published Skill files (read-only text):\n" + JSON.writeValueAsString(nodes.stream()
                .map(node -> new SkillTextNode(node.getPath(), node.getNodeType().name(), node.getContent())).toList());
    }

    private static String literal(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) throw unavailable();
        return node.textValue();
    }

    static void requireIssuer(TianshuContextProperties.Binding binding, TrustedIssuerRegistry issuers) {
        var trusted = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(TianshuContextProvider::unavailable);
        if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(binding.tenantId()).equals(trusted.tenantId())) throw unavailable();
    }
    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeLong(bytes.length); output.write(bytes);
    }

    private static String provenance(DdcAiClientScope scope, TianshuContextProperties.Binding binding, DdcSiResourceDescriptor value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                write(output, "TIANJI_TIANSHU_SI_CONTEXT_SOURCE_V1");
                for (var coordinate : coordinates(scope, binding)) write(output, coordinate);
                write(output, Long.toString(value.getResolvedVersionId()));
                write(output, Long.toString(value.getResolvedVersionNo()));
                write(output, value.getDependencyManifest().closureDigest());
            }
            return "tianshu-si:v1:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException failure) {
            throw unavailable();
        }
    }

    private static List<String> coordinates(DdcAiClientScope scope, TianshuContextProperties.Binding binding) {
        return List.of(Long.toString(scope.tenantId()), scope.bizCode(), scope.env(), scope.appCode(), binding.namespace(),
                Objects.requireNonNullElse(binding.ownerAppCode(), ""), binding.resourceKey(), Long.toString(binding.resourceId()));
    }

    static final class Ports {
        private final DdcAiClientScope scope;
        private final DdcSiCatalogClient catalog;
        private final TianshuContextConfiguration.CatalogAuthorityVO authority;
        private final Duration timeout;
        private final Semaphore slots;

        Ports(DdcAiClientScope scope, DdcSiCatalogClient catalog, Duration timeout, int maxConcurrent,
              TianshuContextConfiguration.CatalogAuthorityVO authority) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.catalog = Objects.requireNonNull(catalog, "catalog");
            this.authority = Objects.requireNonNull(authority, "authority");
            if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(5)) > 0
                    || maxConcurrent < 1 || maxConcurrent > 16) throw unavailable();
            this.timeout = timeout;
            this.slots = new Semaphore(maxConcurrent);
        }

        private DdcSiResourceDescriptor read(TianshuContextProperties.Binding binding, DdcSiKindEnum kind, long version) {
            Supplier<DdcSiResourceDescriptor> exact = () -> catalog.resolveExecutionResource(scope.tenantId(), scope.bizCode(),
                    binding.namespace(), scope.env(), binding.ownerAppCode(), scope.appCode(), kind, binding.resourceKey(), version)
                    .orElseThrow(TianshuContextProvider::unavailable);
            return TianshuContextConfiguration.withAuthority(authority, exact);
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
