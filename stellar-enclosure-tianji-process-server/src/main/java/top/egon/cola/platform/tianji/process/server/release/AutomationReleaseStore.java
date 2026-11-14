package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.GraphVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledAutomationModelVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.DependencySnapshotVO;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftBO;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Validated
public class AutomationReleaseStore {
    private static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DEPENDENCY_BYTES = 256 * 1024;
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxNameLength(512)
                            .maxStringLength(MAX_SOURCE_BYTES).maxNumberLength(25).maxTokenCount(200_000).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final JdbcTemplate jdbc;
    private final String releases;
    private final String drafts;

    public AutomationReleaseStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (jdbc.getDataSource() == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid automation release storage");
        }
        this.releases = releaseTable(schema);
        this.drafts = schema + ".tianji_process_draft";
    }

    static String releaseTable(String schema) {
        return schema + ".tianji_process_automation_release";
    }

    public static String compilerVersion() {
        return AutomationModelCompiler.VERSION;
    }

    /** Compile evidence outside the write transaction and check the bounds applied by PostgreSQL. */
    public PreparedDescriptorVO prepare(@NotNull @Valid CompiledAutomationModelVO compiled) {
        String source = canonical(JSON.convertValue(compiled.source(), Object.class), MAX_SOURCE_BYTES);
        String dependencies = canonical(compiled.dependencyManifest(), MAX_DEPENDENCY_BYTES);
        boolean fits = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT octet_length((?::jsonb)::text)<=? AND octet_length((?::jsonb)::text)<=?
                """, Boolean.class, source, MAX_SOURCE_BYTES, dependencies, MAX_DEPENDENCY_BYTES));
        if (!fits) throw invalidSource();
        var decisionInputs = new LinkedHashMap<String, Map<String, ValueType>>();
        compiled.artifacts().values().stream().filter(asset -> asset.type() == Type.DMN).forEach(asset -> {
            byte[] xml = asset.xml();
            var document = SafeModelXml.parseAutomation(xml, false, MAX_SOURCE_BYTES);
            var decision = ProcessModelValidator.validateAutomationDecision(xml, asset.definitionKey(), document.getDocumentElement());
            decisionInputs.put(asset.definitionKey(), decision.inputs());
        });
        return new PreparedDescriptorVO(compiled, source, dependencies, logicalGraph(compiled.source()),
                Map.copyOf(decisionInputs));
    }

    public void insert(@NotNull @Valid ProcessDraftBO draft, @NotNull String releaseVersion,
                       @NotNull PreparedDescriptorVO descriptor, @NotNull Instant now) {
        requireWriting();
        var evidence = descriptor.compiled.evidence();
        if (draft.getOwner().tenantId() != evidence.tenantId() || !draft.getDefinitionKey().equals(evidence.rootDefinitionKey())) {
            throw new IllegalStateException("Automation publication draft identity changed");
        }
        int written = jdbc.update("""
                INSERT INTO %s (tenant_id,release_key,root_definition_key,source_descriptor,source_sha256,compiler_version,
                  compiled_sha256,created_at,source_draft_id,source_draft_revision,draft_content_sha256,dependency_manifest,dependency_sha256)
                VALUES (?,?,?,?::jsonb,?,?,?,?,?,?,?,?::jsonb,?)
                """.formatted(releases), evidence.tenantId(), releaseVersion, evidence.rootDefinitionKey(), descriptor.source,
                evidence.sourceSha256(), evidence.compilerVersion(), evidence.compiledSha256(), stamp(now), draft.getDraftId(),
                draft.getRevision(), draft.getContentSha256(), descriptor.dependencies, evidence.dependencySha256());
        if (written != 1) throw new IllegalStateException("Automation release insert did not write one row");
    }

    public void seal(@NotNull @Valid ProcessDraftBO draft, @NotNull String releaseVersion, @NotNull String commandId,
                     @NotNull Instant now) {
        requireWriting();
        ProcessActor owner = draft.getOwner();
        int written = jdbc.update("""
                UPDATE %s SET state='PUBLISHED',revision=revision+1,published_release_key=?,published_revision=revision,
                  publish_command_id=?,published_at=?,last_command_id=?,updated_at=?
                WHERE tenant_id=? AND owner_type=? AND owner_issuer=?::char(64) COLLATE "C" AND owner_id=? AND draft_id=?
                  AND state='EDITING' AND revision=? AND content_sha256=?::char(64) COLLATE "C"
                """.formatted(drafts), releaseVersion, commandId, stamp(now), commandId, stamp(now), owner.tenantId(),
                owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), draft.getDraftId(), draft.getRevision(),
                draft.getContentSha256());
        if (written != 1) throw new IllegalStateException("Locked automation draft was not sealed");
    }

    public Optional<ImmutableReleaseVO> find(long tenantId, @NotNull String releaseVersion) {
        return jdbc.query("""
                SELECT release.root_definition_key,release.source_descriptor::text,release.source_sha256,
                  release.compiler_version,release.compiled_sha256,release.created_at,release.source_draft_id,
                  release.source_draft_revision,release.draft_content_sha256,release.dependency_manifest::text,
                  release.dependency_sha256,
                  origin.draft_id IS NOT NULL AS sealed_origin_matches
                FROM %s release
                LEFT JOIN %s origin ON origin.tenant_id=release.tenant_id AND origin.draft_id=release.source_draft_id
                  AND origin.definition_key=release.root_definition_key AND origin.state='PUBLISHED'
                  AND origin.published_release_key=release.release_key AND origin.published_revision=release.source_draft_revision
                  AND origin.content_sha256=release.draft_content_sha256 AND origin.published_at=release.created_at
                WHERE release.tenant_id=? AND release.release_key=?
                """.formatted(releases, drafts), (row, index) -> {
            if (!row.getBoolean("sealed_origin_matches")) {
                throw new IllegalStateException("Stored automation release does not match its sealed source revision");
            }
            try {
                var source = JSON.readValue(row.getString("source_descriptor"), AutomationModelSourceVO.class);
                var dependencies = JSON.readValue(row.getString("dependency_manifest"), OBJECT);
                String root = row.getString("root_definition_key");
                String sourceSha = row.getString("source_sha256");
                String dependencySha = row.getString("dependency_sha256");
                String compiler = row.getString("compiler_version");
                if (!root.equals(source.models().getFirst().definitionKey())
                        || !AutomationModelCompiler.VERSION.equals(compiler)
                        || !sourceSha.equals(AutomationModelCompiler.sourceSha256(AutomationModelCompiler.sourceManifest(source)))
                        || !dependencySha.equals(AutomationModelCompiler.dependencySha256(dependencies))) {
                    throw new IllegalStateException("Stored automation release digest or identity does not match");
                }
                return new ImmutableReleaseVO(tenantId, releaseVersion, root, source, sourceSha, compiler,
                        row.getString("compiled_sha256"), row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        row.getString("source_draft_id"), row.getLong("source_draft_revision"),
                        row.getString("draft_content_sha256"), Map.copyOf(dependencies), dependencySha);
            } catch (IllegalArgumentException | tools.jackson.core.JacksonException malformed) {
                throw new IllegalStateException("Stored automation release descriptor is invalid");
            }
        }, tenantId, releaseVersion).stream().findFirst();
    }

    public static DraftContentVO copyContent(@NotNull @Valid AutomationModelSourceVO source) {
        return new DraftContentVO(source.models().stream()
                .map(model -> new DraftModelSourceVO(model.type(), model.definitionKey(), model.xml())).toList(),
                canonical(source.inputSchema().toWire(), MAX_SOURCE_BYTES),
                canonical(source.outputSchema().toWire(), MAX_SOURCE_BYTES),
                canonical(JSON.convertValue(source.nodeBindings(), Object.class), MAX_SOURCE_BYTES),
                canonical(JSON.convertValue(source.eventRoutes(), Object.class), MAX_SOURCE_BYTES));
    }

    /** Project the verified original root scope. Nested definitions remain available in the source descriptor. */
    public static GraphVO logicalGraph(@NotNull @Valid AutomationModelSourceVO source) {
        var root = source.models().getFirst();
        var document = SafeModelXml.parseAutomation(AutomationModelValidator.utf8(root.xml()), true, MAX_SOURCE_BYTES);
        Element process = children(document.getDocumentElement()).stream()
                .filter(element -> "process".equals(element.getLocalName())).findFirst().orElseThrow(AutomationReleaseStore::invalidSource);
        Map<String, String> kinds = new LinkedHashMap<>();
        source.nodeBindings().forEach(binding -> kinds.put(binding.nodeId(), binding.kind()));
        List<GraphVO.NodeVO> nodes = new ArrayList<>();
        List<GraphVO.EdgeVO> edges = new ArrayList<>();
        for (Element element : children(process)) {
            String id = element.getAttribute("id");
            String name = element.hasAttribute("name") ? element.getAttribute("name") : null;
            var kind = switch (element.getLocalName()) {
                case "startEvent" -> "START";
                case "endEvent" -> "END";
                case "exclusiveGateway" -> "XOR_GATEWAY";
                case "parallelGateway" -> kinds.getOrDefault(id, "AND_SPLIT");
                default -> kinds.get(id);
            };
            if (kind != null) nodes.add(new GraphVO.NodeVO(id, kind, name == null ? id : name));
            if ("sequenceFlow".equals(element.getLocalName())) {
                edges.add(new GraphVO.EdgeVO(id, element.getAttribute("sourceRef"), element.getAttribute("targetRef"), name));
            }
        }
        return new GraphVO(root.definitionKey(), nodes, edges);
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && AutomationModelValidator.BPMN.equals(element.getNamespaceURI())) result.add(element);
        }
        return result;
    }

    private static String canonical(Object value, int limit) {
        return new String(ProcessTypedJson.canonicalBytes(value, limit), StandardCharsets.UTF_8);
    }

    private static OffsetDateTime stamp(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private void requireWriting() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("Automation release requires its owning Process write transaction");
        }
    }

    private static IllegalArgumentException invalidSource() {
        return new IllegalArgumentException("Automation release source exceeds its representation limit or is invalid");
    }

    @FunctionalInterface
    public interface DependencyResolver {
        DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO source);

        /** Read-only local proof recheck under the publication fence. Network observation belongs in resolve. */
        default boolean isCurrent(ProcessActor actor, AutomationModelSourceVO source, DependencySnapshotVO snapshot) {
            return actor.tenantId() == snapshot.tenantId() && snapshot.equals(DependencySnapshotVO.empty(actor.tenantId()));
        }
    }

    public static final class PreparedDescriptorVO {
        private final CompiledAutomationModelVO compiled;
        private final String source;
        private final String dependencies;
        private final GraphVO graph;
        private final Map<String, Map<String, ValueType>> decisionInputs;

        private PreparedDescriptorVO(CompiledAutomationModelVO compiled, String source, String dependencies,
                                     GraphVO graph, Map<String, Map<String, ValueType>> decisionInputs) {
            this.compiled = compiled;
            this.source = source;
            this.dependencies = dependencies;
            this.graph = graph;
            this.decisionInputs = decisionInputs;
        }

        Map<String, ValueType> decisionInputs(String definitionKey) {
            return Objects.requireNonNull(decisionInputs.get(definitionKey));
        }

        public CompiledAutomationModelVO compiled() { return compiled; }
        public GraphVO graph() { return graph; }
    }

    public record ImmutableReleaseVO(long tenantId, String releaseVersion, String rootDefinitionKey,
                                     AutomationModelSourceVO source, String sourceSha256, String compilerVersion,
                                     String compiledSha256, Instant createdAt, String sourceDraftId, long sourceDraftRevision,
                                     String draftContentSha256, Map<String, Object> dependencies, String dependencySha256) { }
}
