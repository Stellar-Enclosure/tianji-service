package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.apps.App;
import com.google.adk.apps.ResumabilityConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.LongRunningFunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins isolated ADK behavior at the Session tool-result append boundary. */
@Timeout(12)
class AdkNativeResumeSemanticsTest {
    private static final String APP = "native_resume_fixture";
    private static final String ROOT = "fixture_root";
    private static final String USER = "fixture_user";
    private static final String SESSION = "fixture_session";
    private static final String ORIGINAL = "inspect deterministic fixture";
    private static final String READ = "readFact";
    private static final String WAIT = "waitForFact";
    private static final String READ_CALL = "fixture-read-call";
    private static final String WAIT_CALL = "fixture-wait-call";
    private static final long SECONDS = 3;
    private static final RunConfig CONFIG = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
            .setMaxLlmCalls(3).setAutoCreateSession(false).build();

    @Test
    void resumes_exact_invocation_from_saved_read_result_without_tool_replay() {
        var fixture = new Fixture();
        var initialModel = new ScriptedModel(request -> {
            assertTrue(!hasResponse(request, READ_CALL, "value", "read-only"), "initial request has no read response");
            return Flowable.just(call(READ, READ_CALL));
        });
        Runner before = fixture.runner(initialModel, true, fixture.readTool());
        var first = collect(fresh(before, ORIGINAL).takeUntil(event -> !event.functionResponses().isEmpty()));
        var history = fixture.history();
        String invocation = first.invocationId();
        assertTrue(initialModel.calls.size() == 1 && fixture.tools.reads.get() == 1, "one initial model and read call");
        assertTrue(hasPersistedCall(history, invocation, READ_CALL), "native read function call was saved");
        assertTrue(history.stream().anyMatch(event -> event.invocationId().equals(invocation)
                && event.functionResponses().stream().anyMatch(response -> responseMatches(response, READ_CALL, "value", "read-only"))),
                "native read result was saved before cancellation");
        assertTrue(originals(history, invocation) == 1 && !ended(history, invocation), "original message persisted and invocation unfinished");
        close(before);

        var afterModel = new ScriptedModel(request -> {
            assertTrue(hasText(request, ORIGINAL), "rehydrated request reads original user message");
            assertTrue(hasResponse(request, READ_CALL, "value", "read-only"), "rehydrated model request uses persisted exact read result");
            return Flowable.just(text("resumed from committed response"));
        });
        Runner after = fixture.runner(afterModel, true, fixture.readTool());
        var resumed = collect(explicit(after, invocation));
        assertTrue(resumed.invocationId().equals(invocation), "explicit no-content resume kept invocation ID");
        assertTrue(resumed.hasText("resumed from committed response"), "resumed model response emitted");
        assertTrue(afterModel.calls.size() == 1 && fixture.tools.reads.get() == 1, "one continuation model call and no read-tool replay");
        var finished = fixture.history();
        assertTrue(originals(finished, invocation) == 1 && ended(finished, invocation), "resume adds native end marker without a second user message");
        assertTrue(finished.subList(0, history.size()).equals(history), "saved prefix unchanged");
        var replay = collect(explicit(after, invocation));
        assertTrue(replay.events().isEmpty() && fixture.history().equals(finished) && afterModel.calls.size() == 1,
                "same completed invocation is a native no-op");
        var unknown = collectFailure(explicit(after, "unknown-invocation"));
        assertTrue(unknown instanceof IllegalArgumentException
                && "No user message to resume the requested invocation from.".equals(unknown.getMessage())
                && fixture.history().equals(finished), "unknown invocation fails closed without appends");
        close(after);

        var freshModel = new ScriptedModel(request -> {
            assertTrue(hasText(request, "new user prompt"), "fresh control passes a new user prompt");
            return Flowable.just(text("fresh model response"));
        });
        Runner fresh = fixture.runner(freshModel, true, fixture.readTool());
        var newTurn = collect(fresh(fresh, "new user prompt"));
        assertTrue(!newTurn.invocationId().equals(invocation) && newTurn.hasText("fresh model response"), "ordinary new text creates a new invocation");
        assertTrue(freshModel.calls.size() == 1 && fixture.tools.reads.get() == 1, "fresh control did not rerun read tool");
        close(fresh);

        Runner disabled = fixture.runner(freshModel, false, fixture.readTool());
        var disabledHistory = fixture.history();
        var refused = collectFailure(Flowable.defer(() -> explicit(disabled, invocation)));
        assertTrue(refused instanceof IllegalStateException
                && refused.getMessage().contains("requires an App configured with a resumable ResumabilityConfig")
                && fixture.history().equals(disabledHistory), "explicit resume requires the App flag");
        close(disabled);
    }

    @Test
    void reexecutes_the_tool_when_the_native_call_exists_without_a_saved_result() {
        var fixture = new Fixture();
        var initialModel = new ScriptedModel(request -> Flowable.just(call(READ, READ_CALL)));
        var interruptBeforeSavingResult = PublishProcessor.<Boolean>create();
        Runner before = fixture.runner(initialModel, true, fixture.readTool(), builder ->
                builder.afterToolCallback((invocation, tool, arguments, context, result) -> Maybe.defer(() -> {
                    interruptBeforeSavingResult.onNext(true);
                    return Maybe.never();
                })));
        collect(fresh(before, ORIGINAL).takeUntil(interruptBeforeSavingResult));
        var prefix = fixture.history();
        var savedCall = prefix.stream().filter(event -> !event.functionCalls().isEmpty()).findFirst().orElseThrow();
        String invocation = savedCall.invocationId();
        assertTrue(hasPersistedCall(prefix, invocation, READ_CALL), "saved native call");
        assertTrue(fixture.tools.reads.get() == 1 && prefix.stream().noneMatch(event -> !event.functionResponses().isEmpty())
                && !ended(prefix, invocation), "read already executed but no response was saved before interruption");
        close(before);

        var resumedModel = new ScriptedModel(request -> {
            assertTrue(hasResponse(request, READ_CALL, "value", "read-only"), "resume model sees generated read response");
            return Flowable.just(text("resumed from pending call"));
        });
        Runner after = fixture.runner(resumedModel, true, fixture.readTool());
        var resumed = collect(explicit(after, invocation));
        assertTrue(resumed.invocationId().equals(invocation) && resumed.hasText("resumed from pending call"), "pending-call continuation reused invocation");
        assertTrue(fixture.tools.reads.get() == 2 && resumedModel.calls.size() == 1 && ended(fixture.history(), invocation),
                "native resume executes the same read tool a second time without a saved response");
        close(after);
    }

    @Test
    void pending_long_running_call_requires_a_matching_function_response() {
        var fixture = new Fixture();
        var waitingModel = new ScriptedModel(request -> Flowable.just(call(WAIT, WAIT_CALL)));
        Runner before = fixture.runner(waitingModel, true, fixture.waitTool());
        var initial = collect(fresh(before, ORIGINAL));
        String invocation = initial.invocationId();
        var pending = fixture.history();
        assertTrue(pending.stream().anyMatch(event -> event.invocationId().equals(invocation)
                && event.longRunningToolIds().stream().flatMap(java.util.Collection::stream).anyMatch(WAIT_CALL::equals)),
                "native long-running call was saved");
        assertTrue(waitingModel.calls.size() == 1 && fixture.tools.waits.get() == 1 && !ended(pending, invocation),
                "native long-running tool entered once and paused");
        close(before);

        var resumedModel = new ScriptedModel(request -> {
            assertTrue(hasResponse(request, WAIT_CALL, "status", "completed"), "model sees supplied matching function response");
            return Flowable.just(text("resumed from supplied function response"));
        });
        Runner after = fixture.runner(resumedModel, true, fixture.waitTool());
        var wrong = collectFailure(after.runAsync(USER, SESSION, answer("wrong-call"), CONFIG));
        assertTrue(wrong instanceof IllegalArgumentException && fixture.history().equals(pending) && resumedModel.calls.isEmpty(),
                "unknown function-response ID cannot append or invoke model");
        var noAnswer = collect(explicit(after, invocation));
        assertTrue(noAnswer.events().isEmpty() && fixture.history().equals(pending)
                && resumedModel.calls.isEmpty() && fixture.tools.waits.get() == 1,
                "same-ID null input cannot complete a pending long-running tool");
        var matching = collect(after.runAsync(USER, SESSION, answer(WAIT_CALL), CONFIG));
        assertTrue(matching.invocationId().equals(invocation) && matching.hasText("resumed from supplied function response"),
                "ordinary Content overload resolves matching function response to the saved invocation");
        var completed = fixture.history();
        assertTrue(ended(completed, invocation) && fixture.tools.waits.get() == 1 && resumedModel.calls.size() == 1,
                "matching response completes without reissuing waiting tool");
        assertTrue(completed.stream().filter(event -> event.invocationId().equals(invocation) && "user".equals(event.author()))
                .flatMap(event -> event.functionResponses().stream())
                .filter(response -> responseMatches(response, WAIT_CALL, "status", "completed")).count() == 1,
                "matching response path appends caller-supplied new user data");
        close(after);
    }

    private static Flowable<Event> explicit(Runner runner, String invocation) {
        return runner.runAsync(USER, SESSION, invocation, null, CONFIG, Map.of());
    }

    private static Flowable<Event> fresh(Runner runner, String prompt) {
        return runner.runAsync(USER, SESSION, Content.builder().role("user").parts(List.of(Part.fromText(prompt))).build(), CONFIG);
    }

    private static Content answer(String id) {
        return Content.builder().role("user").parts(List.of(Part.builder()
                .functionResponse(FunctionResponse.builder().id(id).name(WAIT).response(Map.of("status", "completed")).build())
                .build())).build();
    }

    private static LlmResponse call(String name, String id) {
        return LlmResponse.builder().content(Content.builder().role("model").parts(List.of(Part.builder()
                .functionCall(FunctionCall.builder().id(id).name(name).args(Map.of()).build()).build())).build())
                .partial(false).turnComplete(true).build();
    }

    private static LlmResponse text(String value) {
        return LlmResponse.builder().content(Content.builder().role("model").parts(List.of(Part.fromText(value))).build())
                .partial(false).turnComplete(true).build();
    }

    private static boolean hasText(LlmRequest request, String expected) {
        return request.contents().stream().anyMatch(content -> hasText(content, expected));
    }

    private static boolean hasText(Content content, String expected) {
        return content.parts().stream().flatMap(List::stream).flatMap(part -> part.text().stream()).anyMatch(expected::equals);
    }

    private static boolean hasResponse(LlmRequest request, String id, String key, String expected) {
        return request.contents().stream().flatMap(content -> content.parts().stream().flatMap(List::stream))
                .flatMap(part -> part.functionResponse().stream())
                .anyMatch(response -> responseMatches(response, id, key, expected));
    }

    private static boolean responseMatches(FunctionResponse response, String id, String key, String expected) {
        return response.id().filter(id::equals).isPresent()
                && response.response().map(value -> expected.equals(value.get(key))).orElse(false);
    }

    private static boolean hasPersistedCall(List<Event> events, String invocation, String id) {
        return events.stream().filter(event -> event.invocationId().equals(invocation))
                .flatMap(event -> event.functionCalls().stream()).anyMatch(call -> call.id().filter(id::equals).isPresent());
    }

    private static boolean ended(List<Event> events, String invocation) {
        return events.stream().anyMatch(event -> event.invocationId().equals(invocation)
                && ROOT.equals(event.author()) && event.actions().endOfAgent());
    }

    private static long originals(List<Event> events, String invocation) {
        return events.stream().filter(event -> event.invocationId().equals(invocation) && "user".equals(event.author()))
                .filter(event -> event.content().map(content -> hasText(content, ORIGINAL)).orElse(false)).count();
    }

    private static NativeOutcome collect(Flowable<Event> flow) {
        return new NativeOutcome(flow.toList().timeout(SECONDS, TimeUnit.SECONDS).blockingGet());
    }

    private static Throwable collectFailure(Flowable<Event> flow) {
        var signals = flow.timeout(SECONDS, TimeUnit.SECONDS).materialize().toList().blockingGet();
        assertTrue(signals.size() == 1 && signals.getFirst().isOnError(), "expected error without emitted events");
        return signals.getFirst().getError();
    }

    private static void close(Runner runner) {
        runner.close().timeout(SECONDS, TimeUnit.SECONDS).blockingAwait();
    }

    private record NativeOutcome(List<Event> events) {
        String invocationId() {
            var ids = events.stream().map(Event::invocationId).distinct().toList();
            assertTrue(ids.size() == 1 && !ids.getFirst().isBlank(), "one exact invocation ID emitted");
            return ids.getFirst();
        }

        boolean hasText(String expected) {
            return events.stream().filter(event -> ROOT.equals(event.author()))
                    .anyMatch(event -> event.content().map(content -> AdkNativeResumeSemanticsTest.hasText(content, expected)).orElse(false));
        }
    }

    private static final class ScriptedModel extends BaseLlm {
        final List<LlmRequest> calls = new ArrayList<>();
        private final Function<LlmRequest, Flowable<LlmResponse>> script;

        ScriptedModel(Function<LlmRequest, Flowable<LlmResponse>> script) {
            super("fake-native-model");
            this.script = script;
        }

        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean streaming) {
            assertTrue(streaming, "production-shaped SSE RunConfig reaches the fake model");
            calls.add(request);
            return script.apply(request);
        }

        @Override public BaseLlmConnection connect(LlmRequest request) {
            throw new AssertionError("live connection is outside the probe");
        }
    }

    public static final class ReadOnlyTools {
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger waits = new AtomicInteger();

        public Map<String, Object> readFact() {
            reads.incrementAndGet();
            return Map.of("value", "read-only");
        }

        public Map<String, Object> waitForFact() {
            waits.incrementAndGet();
            return Map.of();
        }
    }

    private static final class Fixture {
        final InMemorySessionService store = new InMemorySessionService();
        final ReadOnlyTools tools = new ReadOnlyTools();

        Fixture() {
            store.createSession(APP, USER, Map.of(), SESSION).timeout(SECONDS, TimeUnit.SECONDS).blockingGet();
        }

        Runner runner(ScriptedModel model, boolean resumable, BaseTool tool) {
            return runner(model, resumable, tool, ignored -> {});
        }

        Runner runner(ScriptedModel model, boolean resumable, BaseTool tool, Consumer<LlmAgent.Builder> customize) {
            var builder = LlmAgent.builder().name(ROOT).description("isolated read-only fixture")
                    .instruction("Read only the in-process fixture.").model(model).tools(tool).executor(Runnable::run);
            customize.accept(builder);
            BaseAgent root = builder.build();
            App app = App.builder().name(APP).rootAgent(root).resumabilityConfig(ResumabilityConfig.builder()
                    .resumable(resumable).plainTextContinuationAutoResume(false).build()).build();
            return Runner.builder().app(app).sessionService(store).build();
        }

        BaseTool readTool() { return FunctionTool.create(tools, READ); }
        BaseTool waitTool() { return LongRunningFunctionTool.create(tools, WAIT); }

        List<Event> history() {
            return store.getSession(APP, USER, SESSION, Optional.empty()).timeout(SECONDS, TimeUnit.SECONDS)
                    .blockingGet().immutableEvents();
        }
    }
}
