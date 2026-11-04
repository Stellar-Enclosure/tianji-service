package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Rechecks the engine definition and immutable release digest before executing a model. */
public final class VerifiedProcessModels {
    private static final int MAX_XML_BYTES = 512 * 1024;
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
