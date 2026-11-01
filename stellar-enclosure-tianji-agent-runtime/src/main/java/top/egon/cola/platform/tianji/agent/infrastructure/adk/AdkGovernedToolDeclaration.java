package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Shared native declaration only; execution requires the governed run scope. */
final class AdkGovernedToolDeclaration extends BaseTool {
    private final FunctionDeclaration declaration;

    AdkGovernedToolDeclaration(ToolDescriptor descriptor) {
        super(descriptor.name(), descriptor.description());
        var properties = new TreeMap<String, Object>(); descriptor.input().fields().forEach((key, value) -> properties.put(key, Map.of("type", value.name().toLowerCase(Locale.ROOT))));
        var schema = new LinkedHashMap<String, Object>(); schema.put("type", "object"); schema.put("properties", Collections.unmodifiableMap(properties));
        schema.put("required", List.copyOf(new TreeSet<>(descriptor.input().required()))); schema.put("additionalProperties", false);
        declaration = FunctionDeclaration.builder().name(name()).description(description()).parametersJsonSchema(Collections.unmodifiableMap(schema)).build();
    }
    @Override public Optional<FunctionDeclaration> declaration() { return Optional.of(declaration); }
    @Override public Single<Map<String, Object>> runAsync(Map<String, Object> arguments, ToolContext context) {
        return Single.error(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Governed read-only tools are unavailable"));
    }
}
