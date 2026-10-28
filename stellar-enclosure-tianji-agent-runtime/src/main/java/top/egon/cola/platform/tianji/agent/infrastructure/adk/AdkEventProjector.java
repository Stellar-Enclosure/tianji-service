package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Projects only text deltas and complete per-agent text, never native tool arguments or reasoning metadata. */
final class AdkEventProjector {
    Optional<Projection> project(Event event, Set<String> agentNames) {
        if (event.errorMessage().isPresent() || event.errorCode().isPresent() || event.interrupted().orElse(false)) {
            throw new IllegalStateException("ADK reported an execution failure");
        }
        if (event.author() == null || !agentNames.contains(event.author())) {
            return Optional.empty();
        }
        String text = event.content().flatMap(Content::parts).orElse(List.of()).stream()
                .filter(part -> !part.thought().orElse(false))
                .map(Part::text).flatMap(Optional::stream).reduce("", String::concat);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String author = event.author();
        if (event.partial().orElse(false)) {
            return Optional.of(new Projection("TEXT_DELTA", author, Map.of("text", text), false));
        }
        if (event.finalResponse() && !text.isBlank()) {
            return Optional.of(new Projection("AGENT_OUTPUT", author, Map.of("text", text), true));
        }
        return Optional.empty();
    }

    record Projection(String type, String author, Map<String, Object> payload, boolean completeAgentOutput) { }
}
