package top.egon.cola.platform.tianji.process.server.acceptance;

import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

final class S12ToolEvidence {
    static final String TOKEN = "token.s12-agent-tool-owner-read";
    static final int MAX_EVENTS = 64;
    private static final Set<String> START_FIELDS = Set.of("toolRef", "hostInvocationId", "state", "dispatchEpoch");
    private static final Set<String> FINISH_FIELDS = Set.of("toolRef", "hostInvocationId", "state", "dispatchEpoch", "accountingState");

    private S12ToolEvidence() { }

    static void requireReadToken(String value, TenantBindingDTO tenant) {
        S12OwnerObservationEvidence.requireReadToken(value, tenant, tenant.ownerSubject(), tenant.agentResource(),
                "USER", "tianji:agent:read");
    }

    static List<AgentEventDTO> read(Flow.Publisher<AgentEventDTO> publisher) throws InterruptedException {
        var subscription = new AtomicReference<Flow.Subscription>();
        var completed = new CompletableFuture<List<AgentEventDTO>>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final List<AgentEventDTO> events = new ArrayList<>();

            @Override public void onSubscribe(Flow.Subscription active) {
                if (subscription.compareAndSet(null, active)) active.request(MAX_EVENTS + 1L);
                else active.cancel();
            }
            @Override public void onNext(AgentEventDTO event) {
                if (events.size() < MAX_EVENTS) events.add(event);
                else {
                    completed.completeExceptionally(new AssertionError("Owned Tool event stream exceeds its fixture limit"));
                    subscription.get().cancel();
                }
            }
            @Override public void onError(Throwable failure) { completed.completeExceptionally(failure); }
            @Override public void onComplete() { completed.complete(List.copyOf(events)); }
        });
        try {
            return completed.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof AgentClientException safe) throw safe;
            if (failure.getCause() instanceof AssertionError invalid) throw invalid;
            throw new AssertionError("Owned Tool event stream is unavailable");
        } catch (TimeoutException timeout) {
            throw new AssertionError("Owned Tool event stream did not complete within its deadline");
        } finally {
            var active = subscription.get();
            if (active != null) active.cancel();
        }
    }

    static void verify(String runId, long terminalSequence, String correlation, UUID invocation, DefinitionResourceRef tool,
                       List<AgentEventDTO> events) {
        require(terminalSequence > 0 && terminalSequence <= MAX_EVENTS && events.size() == terminalSequence,
                "Owned Tool event stream reached the exact terminal watermark");
        var toolEvents = new ArrayList<AgentEventDTO>(2);
        int sequence = 0;
        for (var event : events) {
            require(runId.equals(event.runId()) && event.sequence() == ++sequence,
                    "Exact owned Run and contiguous event order");
            if (event.type().startsWith("TOOL_")) toolEvents.add(event);
        }
        require(toolEvents.size() == 2 && toolEvents.getFirst().type().equals("TOOL_STARTED")
                        && toolEvents.getLast().type().equals("TOOL_FINISHED"),
                "One ordered Tool STARTED and FINISHED pair");
        verifyToolEvent(toolEvents.getFirst(), correlation, invocation, tool, false);
        verifyToolEvent(toolEvents.getLast(), correlation, invocation, tool, true);
        require(!toolEvents.getLast().occurredAt().isBefore(toolEvents.getFirst().occurredAt()),
                "Tool finish is recorded after its start");
    }

    private static void verifyToolEvent(AgentEventDTO event, String correlation, UUID invocation, DefinitionResourceRef tool,
                                        boolean finished) {
        var payload = event.payload();
        require(correlation.equals(event.correlationId()) && "1".equals(event.payloadSchemaVersion())
                        && event.branch() == null && event.toolCallId() == null
                        && payload.keySet().equals(finished ? FINISH_FIELDS : START_FIELDS),
                "Closed original owner Tool event projection");
        require(invocation.toString().equals(payload.get("hostInvocationId"))
                        && (finished ? "SUCCEEDED" : "REQUESTED").equals(payload.get("state"))
                        && sameInteger(payload.get("dispatchEpoch"), finished ? 1 : 0)
                        && (!finished || "SETTLED".equals(payload.get("accountingState"))),
                "Tool event matches the exact original invocation and settlement");
        require(payload.get("toolRef") instanceof Map<?, ?> ref && ref.keySet().equals(Set.of("key", "version", "contentSha256"))
                        && tool.key().equals(ref.get("key")) && sameInteger(ref.get("version"), tool.version())
                        && tool.contentSha256().equals(ref.get("contentSha256")),
                "Tool event matches the exact durable published reference");
    }

    static void verifyReceipt(TenantBindingDTO tenant, String issuerHash, String executor, StartProcessCommand command,
                              String instance, DefinitionResourceRef tool, String receiptId) {
        require(("s07.start." + tenant.tenantId()).equals(tool.key()) && tool.version() == 1,
                "Exact fixed governed START Tool version");
        String expected = "tianji:ps-start:v1:" + OrchestrationRequestDigests.hash(List.of("TIANJI_PS_START_RECEIPT_REFERENCE_V1",
                tenant.issuer(), tenant.processResource(), Long.toString(tenant.tenantId()), "SERVICE", issuerHash, executor,
                "START", command.commandId(), OrchestrationRequestDigests.start(command), instance, command.definitionKey(),
                command.releaseVersion(), command.businessKey(), command.correlationId()), Map.of());
        require(expected.equals(receiptId), "Durable Tool receipt matches the exact native START acknowledgement");
    }

    private static boolean sameInteger(Object actual, long expected) {
        return (actual instanceof Integer || actual instanceof Long) && ((Number) actual).longValue() == expected;
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
