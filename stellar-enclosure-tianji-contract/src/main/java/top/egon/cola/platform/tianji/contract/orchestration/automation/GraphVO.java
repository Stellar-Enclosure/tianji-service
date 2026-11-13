package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/** Read-only logical graph projection. Consumers render names and labels as text. */
public record GraphVO(@NotNull String definitionKey, @NotNull @Size(max = 128) List<@Valid NodeVO> nodes,
                      @NotNull List<@Valid EdgeVO> edges) {

    public GraphVO {
        require(ProcessTypedJson.isId(definitionKey) && nodes != null && nodes.size() <= 128 && edges != null);
        var nodeIds = new HashSet<String>();
        for (NodeVO node : nodes) {
            require(node != null && nodeIds.add(node.id()));
        }
        var edgeIds = new HashSet<String>();
        for (EdgeVO edge : edges) {
            require(edge != null && edgeIds.add(edge.id()) && nodeIds.contains(edge.source()) && nodeIds.contains(edge.target()));
        }
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public record NodeVO(@NotNull String id, @NotNull String kind, @NotNull String name) {
        public NodeVO {
            require(ProcessTypedJson.isId(id) && kind != null && kind.matches("[A-Z][A-Z0-9_]{0,63}"));
            require(name != null);
            ProcessTypedJson.utf8Length(name, 64 * 1024);
        }
    }

    public record EdgeVO(@NotNull String id, @NotNull String source, @NotNull String target,
                         @JsonProperty(required = true) @JsonInclude(JsonInclude.Include.ALWAYS) String label) {
        public EdgeVO {
            require(ProcessTypedJson.isId(id) && ProcessTypedJson.isId(source) && ProcessTypedJson.isId(target));
            if (label != null) {
                ProcessTypedJson.utf8Length(label, 64 * 1024);
            }
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation graph");
        }
    }
}
