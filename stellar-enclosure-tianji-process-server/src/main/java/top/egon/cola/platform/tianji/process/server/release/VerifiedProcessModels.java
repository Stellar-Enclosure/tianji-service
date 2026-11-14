package top.egon.cola.platform.tianji.process.server.release;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledControlBindingsVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler.CompiledModelEvidenceVO;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Rechecks the engine definition and immutable release digest before executing a model. */
@Slf4j
public final class VerifiedProcessModels {
    private static final int MAX_XML_BYTES = 512 * 1024;
    private static final Pattern RELEASE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private final SpringProcessEngineConfiguration process;
    private final SpringDmnEngineConfiguration dmn;
    private final SpringCmmnEngineConfiguration cmmn;
    private final ProcessModelValidator validator;

    public VerifiedProcessModels(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator) {
        this(process, null, dmn, validator);
    }

    public VerifiedProcessModels(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn,
                                 SpringDmnEngineConfiguration dmn, ProcessModelValidator validator) {
        this.cmmn = cmmn;
        this.process = Objects.requireNonNull(process, "process");
        this.dmn = Objects.requireNonNull(dmn, "dmn");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ValidatedCaseModel caseModel(long tenantId, Definition binding) {
        if (tenantId <= 0 || cmmn == null || binding == null || binding.type() != Type.CMMN || !binding.inputs().isEmpty()) throw unavailable();
        var repository = cmmn.getCmmnRepositoryService();
        if (repository == null) throw unavailable();
        var definition = repository.createCaseDefinitionQuery().caseDefinitionId(binding.engineDefinitionId())
                .deploymentId(binding.engineDeploymentId()).caseDefinitionTenantId(Long.toString(tenantId))
                .caseDefinitionKey(binding.definitionKey()).caseDefinitionVersion(binding.engineVersion()).singleResult();
        if (definition == null || !binding.engineDefinitionId().equals(definition.getId()) || !binding.engineDeploymentId().equals(definition.getDeploymentId())
                || !binding.definitionKey().equals(definition.getKey()) || !Long.toString(tenantId).equals(definition.getTenantId())
                || definition.getVersion() != binding.engineVersion() || !(binding.definitionKey() + ".cmmn").equals(definition.getResourceName())) throw unavailable();
        try (InputStream source = repository.getResourceAsStream(binding.engineDeploymentId(), definition.getResourceName())) {
            byte[] xml = checkedBytes(source, binding);
            try { return validator.validateCmmn(xml, binding.definitionKey()); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("Stored case model is invalid"); }
        } catch (FlowableObjectNotFoundException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Case model could not be read safely");
        }
    }

    public ValidatedProcessModel process(long tenantId, Definition binding) {
        return process(tenantId, binding, false);
    }

    public ValidatedProcessModel runningProcess(long tenantId, Definition binding) {
        return process(tenantId, binding, true);
    }

    private ValidatedProcessModel process(long tenantId, Definition binding, boolean allowSuspended) {
        if (binding.type() != Type.BPMN) throw unavailable();
        var repository = process.getRepositoryService();
        var definition = repository.createProcessDefinitionQuery().processDefinitionId(binding.engineDefinitionId())
                .deploymentId(binding.engineDeploymentId()).processDefinitionTenantId(Long.toString(tenantId)).singleResult();
        if (definition == null || definition.isSuspended() && !allowSuspended || definition.getVersion() != binding.engineVersion()
                || !binding.definitionKey().equals(definition.getKey())) throw unavailable();
        try (InputStream source = repository.getResourceAsStream(binding.engineDeploymentId(), definition.getResourceName())) {
            byte[] xml = checkedBytes(source, binding);
            try { return validator.validateBpmn(xml, binding.definitionKey()); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("Stored process model is invalid"); }
        } catch (FlowableObjectNotFoundException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Process model could not be read safely");
        }
    }

    public ValidatedProcessModel decision(long tenantId, Definition binding) {
        if (binding.type() != Type.DMN) throw unavailable();
        var repository = dmn.getDmnRepositoryService();
        var definition = repository.createDecisionQuery().decisionId(binding.engineDefinitionId())
                .decisionTenantId(Long.toString(tenantId)).deploymentId(binding.engineDeploymentId())
                .decisionKey(binding.definitionKey()).decisionVersion(binding.engineVersion()).singleResult();
        if (definition == null) throw unavailable();
        try (InputStream source = repository.getDmnResource(binding.engineDefinitionId())) {
            byte[] xml = checkedBytes(source, binding);
            ValidatedProcessModel model;
            try { model = validator.validateDmn(xml, binding.definitionKey()); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("Stored decision model is invalid"); }
            if (!model.inputs().equals(binding.inputs())) throw unavailable();
            return model;
        } catch (FlowableObjectNotFoundException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Decision model could not be read safely");
        }
    }

    /**
     * Reopens an immutable release selected by the owner's tenant fence. The original release row anchors
     * the compiled manifest. DMN uses independent engine deployment and definition identities.
     */
    public VerifiedAutomationModelsVO automation(long tenantId, CompiledModelEvidenceVO evidence,
                                                 AutomationModelSourceVO source, Map<String, Object> dependencyManifest,
                                                 Release release) {
        if (evidence == null || evidence.tenantId() != tenantId
                || !AutomationModelValidator.PROFILE.equals(evidence.profile())) throw unavailable();
        return automation(tenantId, evidence.compilerVersion(), evidence.sourceSha256(), evidence.compiledSha256(),
                evidence.dependencySha256(), source, dependencyManifest, release, evidence);
    }

    /** Reopens stored v2 evidence using the original deployment to recover the native mapping. */
    public VerifiedAutomationModelsVO automation(long tenantId, String compilerVersion, String sourceSha256,
                                                 String compiledSha256, String dependencySha256, AutomationModelSourceVO source,
                                                 Map<String, Object> dependencyManifest, Release release) {
        return automation(tenantId, compilerVersion, sourceSha256, compiledSha256, dependencySha256,
                source, dependencyManifest, release, null);
    }

    private VerifiedAutomationModelsVO automation(long tenantId, String compilerVersion, String sourceSha256,
                                                  String compiledSha256, String dependencySha256, AutomationModelSourceVO source,
                                                  Map<String, Object> dependencyManifest, Release release,
                                                  CompiledModelEvidenceVO expected) {
        if (tenantId <= 0 || source == null || !AutomationModelCompiler.VERSION.equals(compilerVersion)
                || release == null || release.releaseKey() == null || !RELEASE_KEY.matcher(release.releaseKey()).matches()
                || !AutomationModelValidator.isSha256(release.manifestSha256())
                || !release.manifestSha256().equals(compiledSha256)
                || release.definitions().isEmpty() || release.definitions().size() > 64
                || dependencyManifest == null) {
            throw unavailable();
        }
        Map<String, Definition> definitions = automationResources(release);
        try {
            if (!AutomationModelCompiler.sourceSha256(AutomationModelCompiler.sourceManifest(source)).equals(sourceSha256)
                    || !manifestTenant(dependencyManifest.get("tenantId"), tenantId)
                    || !AutomationModelCompiler.dependencySha256(dependencyManifest).equals(dependencySha256)) {
                throw unavailable();
            }
            String rootKey = source.models().getFirst().definitionKey();
            String root = rootKey + ".bpmn20.xml";
            var expectedDecisions = new LinkedHashMap<String, String>();
            for (var model : source.models()) {
                if ("DMN".equals(model.type())) {
                    expectedDecisions.put(model.definitionKey(), AutomationModelValidator.rawSha256(AutomationModelValidator.utf8(model.xml())));
                }
            }
            if (!expectedDecisions.equals(dependencyManifest.get("dmn"))
                    || definitions.size() != expectedDecisions.size() + 1 || !definitions.containsKey(root)) throw unavailable();

            Definition rootBinding = definitions.get(root);
            if (rootBinding == null || rootBinding.type() != Type.BPMN
                    || !rootKey.equals(rootBinding.definitionKey())) throw unavailable();
            byte[] bpmn = automationBpmnBytes(tenantId, root, rootBinding);
            var document = SafeModelXml.parseCompiledAutomationBpmn(bpmn);
            var resourceDigests = new LinkedHashMap<String, String>();
            definitions.forEach((resource, definition) -> resourceDigests.put(resource, definition.modelSha256()));
            var evidence = new CompiledModelEvidenceVO(tenantId, AutomationModelValidator.PROFILE, compilerVersion, rootKey,
                    sourceSha256, dependencySha256, compiledSha256,
                    AutomationModelCompiler.id("first", AutomationModelCompiler.BOOTSTRAP), resourceDigests,
                    ProcessModelValidator.compiledNativeIds(document));
            if (expected != null && !expected.equals(evidence)) throw unavailable();
            var controls = ProcessModelValidator.validateCompiledAutomationBpmn(bpmn, document, evidence);

            var verified = new LinkedHashMap<String, ValidatedProcessModel>();
            var decisionDefinitions = new LinkedHashMap<String, Definition>();
            for (var expectedDecision : expectedDecisions.entrySet()) {
                String key = expectedDecision.getKey();
                String resource = key + ".dmn";
                String hash = expectedDecision.getValue();
                Definition binding = definitions.get(resource);
                if (binding == null || binding.type() != Type.DMN || !key.equals(binding.definitionKey())
                        || !hash.equals(binding.modelSha256()) || !hash.equals(evidence.resourceDigests().get(resource))) throw unavailable();
                var decision = automationDecision(tenantId, resource, binding);
                if (!decision.inputs().equals(binding.inputs())) throw unavailable();
                verified.put(key, decision);
                decisionDefinitions.put(key, binding);
            }
            return new VerifiedAutomationModelsVO(rootBinding, verified, decisionDefinitions, evidence.nativeIds(), controls);
        } catch (FlowableObjectNotFoundException | IllegalArgumentException missing) {
            throw unavailable();
        } catch (IOException failure) {
            throw new IllegalStateException("Automation model could not be read safely");
        }
    }

    private static Map<String, Definition> automationResources(Release release) {
        var definitions = new LinkedHashMap<String, Definition>();
        for (Definition definition : release.definitions()) {
            if (!AutomationModelValidator.isId(definition.definitionKey()) || definition.engineVersion() <= 0
                    || !AutomationModelValidator.isSha256(definition.modelSha256())
                    || definition.engineDefinitionId() == null || definition.engineDefinitionId().isBlank()
                    || definition.engineDeploymentId() == null || definition.engineDeploymentId().isBlank()) throw unavailable();
            String suffix = switch (definition.type()) {
                case BPMN -> ".bpmn20.xml";
                case DMN -> ".dmn";
                case CMMN -> throw unavailable();
                case null -> throw unavailable();
            };
            if (definitions.putIfAbsent(definition.definitionKey() + suffix, definition) != null) throw unavailable();
        }
        return definitions;
    }

    private static boolean manifestTenant(Object value, long tenantId) {
        return (value instanceof Long || value instanceof Integer) && ((Number) value).longValue() == tenantId;
    }

    private byte[] automationBpmnBytes(long tenantId, String resource, Definition binding) throws IOException {
        var repository = process.getRepositoryService();
        if (repository == null) throw unavailable();
        String tenant = Long.toString(tenantId);
        var definition = repository.createProcessDefinitionQuery().processDefinitionId(binding.engineDefinitionId())
                .deploymentId(binding.engineDeploymentId()).processDefinitionTenantId(tenant)
                .processDefinitionKey(binding.definitionKey()).processDefinitionVersion(binding.engineVersion()).singleResult();
        if (definition == null || !binding.engineDefinitionId().equals(definition.getId())
                || !binding.engineDeploymentId().equals(definition.getDeploymentId())
                || !binding.definitionKey().equals(definition.getKey()) || binding.engineVersion() != definition.getVersion()
                || !tenant.equals(definition.getTenantId()) || !resource.equals(definition.getResourceName())) throw unavailable();
        try (InputStream xml = repository.getResourceAsStream(binding.engineDeploymentId(), resource)) {
            return checkedAutomationBytes(xml, binding);
        }
    }

    private ValidatedProcessModel automationDecision(long tenantId, String resource, Definition binding) throws IOException {
        var repository = dmn.getDmnRepositoryService();
        if (repository == null) throw unavailable();
        String tenant = Long.toString(tenantId);
        var definition = repository.createDecisionQuery().decisionId(binding.engineDefinitionId())
                .deploymentId(binding.engineDeploymentId()).decisionTenantId(tenant).decisionKey(binding.definitionKey())
                .decisionVersion(binding.engineVersion()).decisionResourceName(resource).singleResult();
        if (definition == null || !binding.engineDefinitionId().equals(definition.getId())
                || !binding.engineDeploymentId().equals(definition.getDeploymentId())
                || !binding.definitionKey().equals(definition.getKey()) || binding.engineVersion() != definition.getVersion()
                || !tenant.equals(definition.getTenantId()) || !resource.equals(definition.getResourceName())) throw unavailable();
        try (InputStream xml = repository.getDmnResource(binding.engineDefinitionId())) {
            byte[] bytes = checkedAutomationBytes(xml, binding);
            return ProcessModelValidator.validateAutomationDecision(bytes, binding.definitionKey(),
                    SafeModelXml.parseAutomation(bytes, false, SafeModelXml.MAX_AUTOMATION_SOURCE_BYTES).getDocumentElement());
        }
    }

    private static byte[] checkedAutomationBytes(InputStream source, Definition binding) throws IOException {
        if (source == null) throw unavailable();
        byte[] xml = source.readNBytes(SafeModelXml.MAX_AUTOMATION_SOURCE_BYTES + 1);
        if (xml.length > SafeModelXml.MAX_AUTOMATION_SOURCE_BYTES
                || !AutomationModelValidator.rawSha256(xml).equals(binding.modelSha256())) throw unavailable();
        return xml;
    }

    public record VerifiedAutomationModelsVO(Definition root, Map<String, ValidatedProcessModel> decisions,
                                             Map<String, Definition> decisionDefinitions, Map<String, String> nativeIds,
                                             CompiledControlBindingsVO controlBindings) {
        public VerifiedAutomationModelsVO {
            Objects.requireNonNull(root, "root");
            decisions = Map.copyOf(decisions);
            decisionDefinitions = Map.copyOf(decisionDefinitions);
            if (!decisions.keySet().equals(decisionDefinitions.keySet())) throw unavailable();
            nativeIds = Map.copyOf(nativeIds);
            Objects.requireNonNull(controlBindings, "controlBindings");
        }
    }

    private static byte[] checkedBytes(InputStream source, Definition binding) throws IOException {
        if (source == null) throw unavailable();
        byte[] xml = source.readNBytes(MAX_XML_BYTES + 1);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml));
            if (!hash.equals(binding.modelSha256())) throw unavailable();
            return xml;
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
}
