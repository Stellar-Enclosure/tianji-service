package top.egon.cola.platform.tianji.agent.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S11HistoricalStartLossTest {
    private static final URI START = URI.create("https://127.0.0.1:8443/api/tianji/v1/process-instances");
    private static final String INTENT = "cbde293c-2c34-402c-8c6e-c597dd9e3b6b";
    private static final String COMMAND = """
            {"definitionKey":"s07GovernedStart","releaseVersion":"s07-governed-release","businessKey":"s07-object-20",
             "commandId":"%s","correlationId":"%s","variables":{"amount":10}}
            """.formatted(INTENT, INTENT);
    private static final byte[] BODY = """
            {"instanceId":"native-instance","definitionKey":"s07GovernedStart","releaseVersion":"s07-governed-release",
             "businessKey":"s07-object-20","correlationId":"%s","status":"ACTIVE","submittedAt":"2026-11-06T00:00:00Z"}
            """.formatted(INTENT).getBytes(StandardCharsets.UTF_8);

    @Test
    void only_exact_async_start_is_suppressed_after_the_committed_witness_and_never_retried() {
        var delegate = mock(HttpClient.class);
        var upstream = new CompletableFuture<HttpResponse<byte[]>>();
        when(delegate.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(upstream);
        var witness = new Witness();
        var client = new S11HistoricalStartLoss.LossClient(delegate, START, "s07-object-20", witness);
        var handler = HttpResponse.BodyHandlers.ofByteArray();
        var release = HttpRequest.newBuilder(START.resolve("/api/tianji/v1/process-releases/s07-governed-release")).GET().build();
        assertSame(upstream, client.sendAsync(release, handler));
        assertSame(upstream, client.sendAsync(request(COMMAND.replace("s07-object-20", "another-object")), handler));
        assertEquals(0, witness.before.get());

        var matched = client.sendAsync(request(COMMAND), handler);
        assertFalse(matched.isDone());
        assertEquals(1, witness.before.get());
        assertEquals(0, witness.after.get());
        upstream.complete(response(200));
        assertInstanceOf(IOException.class, assertThrows(CompletionException.class, matched::join).getCause());
        assertEquals(1, witness.after.get());
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class,
                () -> client.sendAsync(request(COMMAND), handler).join()).getCause());
        verify(delegate, times(3)).sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals(1, witness.before.get());
        assertEquals(1, witness.after.get());
        assertEquals(1, witness.discarded.get());
    }

    @Test
    void unsuccessful_upstream_response_cannot_claim_a_commit_witness() {
        var delegate = mock(HttpClient.class);
        var unsuccessful = CompletableFuture.completedFuture(response(503));
        when(delegate.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(unsuccessful);
        var witness = new Witness();
        var client = new S11HistoricalStartLoss.LossClient(delegate, START, "s07-object-20", witness);
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class,
                () -> client.sendAsync(request(COMMAND), HttpResponse.BodyHandlers.ofByteArray()).join()).getCause());
        assertEquals(1, witness.before.get());
        assertEquals(0, witness.after.get());
        assertEquals(0, witness.discarded.get());
    }

    private static HttpRequest request(String command) {
        return HttpRequest.newBuilder(START).timeout(java.time.Duration.ofSeconds(2)).POST(HttpRequest.BodyPublishers.ofString(command)).build();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status) {
        var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(
                "Cache-Control", List.of("no-store"), "Content-Type", List.of("application/json")), (name, value) -> true));
        when(response.body()).thenReturn(BODY);
        return response;
    }

    private static final class Witness implements S11HistoricalStartLoss.CommitWitness {
        private final AtomicInteger before = new AtomicInteger();
        private final AtomicInteger after = new AtomicInteger();
        private final AtomicInteger discarded = new AtomicInteger();

        @Override public String before(StartProcessCommand command) {
            assertEquals(INTENT, command.commandId());
            before.incrementAndGet();
            return "dispatch-run";
        }

        @Override public void committed(StartProcessCommand command, ProcessInstanceDTO response) {
            assertEquals(INTENT, command.commandId());
            assertEquals("native-instance", response.instanceId());
            after.incrementAndGet();
        }

        @Override public void discarded(StartProcessCommand command, String runId, ProcessInstanceDTO response) {
            assertEquals("dispatch-run", runId);
            discarded.incrementAndGet();
        }
    }
}
