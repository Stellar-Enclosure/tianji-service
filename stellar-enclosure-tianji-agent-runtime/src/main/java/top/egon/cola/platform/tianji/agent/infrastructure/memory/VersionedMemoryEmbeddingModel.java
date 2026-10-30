package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Pins explicit embedding options at the existing VectorStore boundary without invoking dimension inference or DDL. */
public final class VersionedMemoryEmbeddingModel implements EmbeddingModel {
    private static final String VERSION = "SPRING_AI_2_JTOKKIT_1_1_MEMORY_EMBEDDING_V1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> TRANSPORT = Set.of("getBaseUrl", "getApiKey", "getCredential", "getOrganizationId", "getTimeout",
            "getProxy", "getCustomHeaders", "getMicrosoftFoundryServiceVersion", "getClass");
    private static final Set<String> OPENAI = Set.of("getModel", "getDimensions", "getEncodingFormat", "getUser", "getExtraBody", "getMaxRetries",
            "getMicrosoftDeploymentName", "getDeploymentName", "isMicrosoftFoundry", "isGitHubModels");
    private final Profile profile;
    private final EmbeddingModel delegate;
    private final Supplier<EmbeddingOptions> defaults;
    private final Encoding encoding;
    private final String defaultsSha256;
    private final Descriptor descriptor;

    /** The host supplies the same immutable defaults used by its actual model; this supplier must be local and perform no I/O. */
    public VersionedMemoryEmbeddingModel(Profile profile, EmbeddingModel delegate, Supplier<EmbeddingOptions> defaults) {
        this.profile = Objects.requireNonNull(profile, "profile"); this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        var model = ModelType.fromName(profile.modelName()).filter(value -> value == ModelType.TEXT_EMBEDDING_3_SMALL || value == ModelType.TEXT_EMBEDDING_3_LARGE)
                .orElseThrow(VersionedMemoryEmbeddingModel::unsupported);
        if (profile.windowTokens() > model.getMaxContextLength()) throw unsupported();
        encoding = Encodings.newLazyEncodingRegistry().getEncodingForModel(model);
        defaultsSha256 = optionsFingerprint();
        String hash;
        try { hash = sha256(JSON.writeValueAsString(List.of(VERSION, profile, delegate.getClass().getName(), encoding.getName(), defaultsSha256))); }
        catch (JsonProcessingException failure) { throw unavailable(); }
        descriptor = new Descriptor(profile.key() + '@' + profile.version() + '#' + hash, profile.modelName(), profile.dimensions(), encoding.getName(), hash);
    }

    /** Exact reviewed OpenAI model types expose defaults without reading credentials or constructing a provider client. */
    public static VersionedMemoryEmbeddingModel forOpenAi(Profile profile, EmbeddingModel model) {
        if (model == null || !model.getClass().getName().equals("org.springframework.ai.openai.OpenAiEmbeddingModel")) throw unsupported();
        return new VersionedMemoryEmbeddingModel(profile, model, () -> {
            try { return (EmbeddingOptions) model.getClass().getMethod("getOptions").invoke(model); }
            catch (ReflectiveOperationException | ClassCastException failure) { throw unavailable(); }
        });
    }

    public Descriptor descriptor() { return descriptor; }
    @Override public int dimensions() { return profile.dimensions(); }

    public long inputTokenEstimate(String text) {
        if (text == null || text.isBlank() || text.length() > 65_536 || !StandardCharsets.UTF_8.newEncoder().canEncode(text)
                || text.getBytes(StandardCharsets.UTF_8).length > 65_536) throw unsupported();
        long estimate = (long) encoding.countTokensOrdinary(text) + profile.requestOverheadTokens();
        if (estimate > profile.windowTokens() - profile.safetyMarginTokens()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, "Memory embedding input exceeds its reviewed window");
        return estimate;
    }

    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory embedding requires no ambient transaction");
        current();
        if (request == null || request.getInstructions() == null || request.getInstructions().size() != 1) throw unsupported();
        var options = request.getOptions();
        if (options != null && (!options.getClass().getName().equals("org.springframework.ai.embedding.DefaultEmbeddingOptions")
                || options.getModel() != null && !profile.modelName().equals(options.getModel())
                || options.getDimensions() != null && options.getDimensions() != profile.dimensions())) throw unsupported();
        String text = request.getInstructions().getFirst(); inputTokenEstimate(text);
        EmbeddingResponse response;
        try {
            response = delegate.call(new EmbeddingRequest(List.of(text), EmbeddingOptions.builder().model(profile.modelName()).dimensions(profile.dimensions()).build()));
        } catch (RuntimeException failure) { throw unavailable(); }
        current();
        if (response == null || response.getMetadata() == null || !profile.modelName().equals(response.getMetadata().getModel())
                || response.getResults() == null || response.getResults().size() != 1 || response.getResult() == null
                || !Integer.valueOf(0).equals(response.getResult().getIndex())) throw unavailable();
        var output = response.getResult().getOutput();
        if (output == null || output.length != profile.dimensions()) throw unavailable();
        double norm = 0;
        for (float value : output) { if (!Float.isFinite(value)) throw unavailable(); norm += (double) value * value; }
        if (norm == 0) throw unavailable();
        var metadata = new EmbeddingResponseMetadata(); metadata.setModel(profile.modelName());
        if (response.getMetadata().getUsage() != null) metadata.setUsage(response.getMetadata().getUsage());
        return new EmbeddingResponse(List.of(new Embedding(output.clone(), 0)), metadata);
    }

    @Override public float[] embed(Document document) {
        if (document == null || document.getText() == null) throw unsupported();
        return call(new EmbeddingRequest(List.of(document.getText()), EmbeddingOptions.builder().build())).getResult().getOutput();
    }

    @Override public String getEmbeddingContent(Document document) {
        if (document == null || document.getText() == null) throw unsupported(); return document.getText();
    }

    private void current() { if (!defaultsSha256.equals(optionsFingerprint())) throw unavailable(); }

    private String optionsFingerprint() {
        EmbeddingOptions options;
        try { options = defaults.get(); }
        catch (RuntimeException failure) { throw unavailable(); }
        if (options == null || !profile.modelName().equals(options.getModel()) || options.getDimensions() != null && options.getDimensions() != profile.dimensions()) throw unsupported();
        String type = options.getClass().getName(); boolean openai = type.equals("org.springframework.ai.openai.OpenAiEmbeddingOptions");
        if (!openai && !type.equals("org.springframework.ai.embedding.DefaultEmbeddingOptions")) throw unsupported();
        Map<String, Object> values = new TreeMap<>(); values.put("schema", openai ? "OPENAI_EMBEDDING_2_V1" : "COMMON_EMBEDDING_2_V1");
        for (var method : options.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || !name.startsWith("get") && !name.startsWith("is") || TRANSPORT.contains(name)) continue;
            if (!(openai ? OPENAI.contains(name) : name.equals("getModel") || name.equals("getDimensions"))) throw unsupported();
            try {
                Object value = method.invoke(options);
                if (name.equals("getMaxRetries") && !Integer.valueOf(0).equals(value)
                        || (name.equals("isMicrosoftFoundry") || name.equals("isGitHubModels")) && Boolean.TRUE.equals(value)
                        || (name.equals("getMicrosoftDeploymentName") || name.equals("getDeploymentName")) && value instanceof String text && !text.isBlank()
                        || name.equals("getExtraBody") && value != null && (!(value instanceof Map<?, ?> map) || !map.isEmpty())
                        || name.equals("getEncodingFormat") && value != null && (!(value instanceof Enum<?> format) || !format.name().equals("FLOAT"))) throw unsupported();
                values.put(name, value);
            } catch (ReflectiveOperationException failure) { throw unsupported(); }
        }
        try { return sha256(JSON.writeValueAsString(values)); }
        catch (JsonProcessingException failure) { throw unavailable(); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }

    public record Profile(String key, long version, String modelBeanName, String modelName, int dimensions,
                          long windowTokens, long safetyMarginTokens, int requestOverheadTokens) {
        public Profile {
            if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}") || version < 1 || modelBeanName == null
                    || !modelBeanName.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}") || modelName == null || modelName.length() > 128
                    || dimensions < 1 || dimensions > 8192 || windowTokens < 2 || safetyMarginTokens < 1 || safetyMarginTokens >= windowTokens
                    || requestOverheadTokens < 1 || requestOverheadTokens > 1024 || requestOverheadTokens >= windowTokens - safetyMarginTokens) throw new IllegalArgumentException("Memory embedding profile is invalid");
        }
    }
    public record Descriptor(String modelRef, String modelName, int dimensions, String encodingName, String adapterSha256) { }
    private static AgentLedgerException unsupported() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Memory embedding is outside the reviewed text profile"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Versioned memory embedding model is unavailable or changed"); }
}
