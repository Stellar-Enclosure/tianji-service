package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdkEventProjectorTest {
    private final AdkEventProjector projector = new AdkEventProjector();

    @Test
    void projects_only_allowlisted_agent_text_and_omits_native_thought_and_tool_arguments() {
        Event event = Event.builder().author("planner").partial(true).content(Content.fromParts(
                Part.builder().text("private reasoning").thought(true).build(),
                Part.fromText("safe output"),
                Part.fromFunctionCall("lookup", Map.of("secret", "do-not-expose")))).build();
        var projected = projector.project(event, Set.of("planner")).orElseThrow();
        assertEquals("TEXT_DELTA", projected.type());
        assertEquals(Map.of("text", "safe output"), projected.payload());
        assertFalse(projected.payload().toString().contains("private reasoning"));
        assertFalse(projected.payload().toString().contains("do-not-expose"));
    }

    @Test
    void ignores_non_agent_events_but_does_not_hide_failures() {
        Event user = Event.builder().author("user").content(Content.fromParts(Part.fromText("input"))).build();
        assertTrue(projector.project(user, Set.of("planner")).isEmpty());
        Event failure = Event.builder().author("unknown").errorMessage("sensitive detail").build();
        AgentLedgerException error = assertThrows(AgentLedgerException.class,
                () -> projector.project(failure, Set.of("planner")));
        assertFalse(error.getMessage().contains("sensitive detail"));
    }
}
