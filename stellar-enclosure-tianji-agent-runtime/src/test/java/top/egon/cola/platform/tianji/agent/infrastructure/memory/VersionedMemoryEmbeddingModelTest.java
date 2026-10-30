package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VersionedMemoryEmbeddingModelTest {
    private static final String MODEL = "text-embedding-3-small";

    @Test
    void dimension_and_descriptor_access_never_infer_dimensions_and_calls_pin_options_and_copy_vectors() {
        var delegate = mock(EmbeddingModel.class); float[] vector = {0.25f, 0.75f};
        when(delegate.call(any(EmbeddingRequest.class))).thenReturn(response(MODEL, vector, 0));
        var model = new VersionedMemoryEmbeddingModel(profile(8191), delegate, VersionedMemoryEmbeddingModelTest::defaults);
        assertEquals(2, model.dimensions()); assertTrue(model.descriptor().modelRef().matches("memory-model@1#[0-9a-f]{64}"));
        assertEquals("cl100k_base", model.descriptor().encodingName());
        verifyNoInteractions(delegate);
        var output = model.call(new EmbeddingRequest(List.of("private fact 中文"), EmbeddingOptions.builder().build()));
        vector[0] = 1; assertEquals(0.25f, output.getResult().getOutput()[0]);
        verify(delegate).call(argThat((EmbeddingRequest value) -> value.getOptions().getModel().equals(MODEL)
                && value.getOptions().getDimensions() == 2 && value.getInstructions().equals(List.of("private fact 中文"))));
        verify(delegate, never()).dimensions();
        assertEquals(MODEL, output.getMetadata().getModel());
    }

    @Test
    void the_vectorstore_document_batch_path_reaches_one_explicit_model_call() {
        var delegate = mock(EmbeddingModel.class); when(delegate.call(any(EmbeddingRequest.class))).thenReturn(response(MODEL, new float[]{1, 0}, 0));
        var model = new VersionedMemoryEmbeddingModel(profile(8191), delegate, VersionedMemoryEmbeddingModelTest::defaults);
        var result = model.embed(List.of(new Document("private fact", Map.of("ownerId", "private-owner-id"))), EmbeddingOptions.builder().build(), documents -> List.of(documents));
        assertEquals(1, result.size()); assertArrayEquals(new float[]{1, 0}, result.getFirst());
        verify(delegate, times(1)).call(any(EmbeddingRequest.class)); verify(delegate, never()).dimensions();
        verify(delegate).call(argThat((EmbeddingRequest value) -> value.getInstructions().equals(List.of("private fact"))));
    }

    @Test
    void current_defaults_scope_window_and_transactions_are_checked_before_the_provider_call() {
        var delegate = mock(EmbeddingModel.class); var defaults = new AtomicReference<>(defaults());
        var model = new VersionedMemoryEmbeddingModel(profile(64), delegate, defaults::get);
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED,
                assertThrows(AgentLedgerException.class, () -> model.embed("many words ".repeat(100))).code());
        assertThrows(AgentLedgerException.class, () -> model.call(new EmbeddingRequest(List.of("fact"), EmbeddingOptions.builder().model("another-model").build())));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> model.embed("fact")); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        defaults.set(EmbeddingOptions.builder().model(MODEL).dimensions(3).build());
        assertThrows(AgentLedgerException.class, () -> model.embed("fact")); verifyNoInteractions(delegate);
        assertThrows(AgentLedgerException.class, () -> new VersionedMemoryEmbeddingModel(new VersionedMemoryEmbeddingModel.Profile("memory-model", 1,
                "embeddingBean", "unknown-alias", 2, 64, 8, 4), delegate, VersionedMemoryEmbeddingModelTest::defaults));
    }

    @Test
    void wrong_response_model_dimensions_indices_nonfinite_and_zero_vectors_are_not_accepted() {
        var delegate = mock(EmbeddingModel.class);
        var model = new VersionedMemoryEmbeddingModel(profile(8191), delegate, VersionedMemoryEmbeddingModelTest::defaults);
        for (var response : List.of(response("another-model", new float[]{1, 0}, 0), response(MODEL, new float[]{1}, 0),
                response(MODEL, new float[]{Float.NaN, 0}, 0), response(MODEL, new float[]{0, 0}, 0), response(MODEL, new float[]{1, 0}, 1))) {
            when(delegate.call(any(EmbeddingRequest.class))).thenReturn(response);
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> model.embed("fact")).code());
        }
        when(delegate.call(any(EmbeddingRequest.class))).thenThrow(new IllegalStateException("private provider detail"));
        var failure = assertThrows(AgentLedgerException.class, () -> model.embed("fact"));
        assertFalse(failure.toString().contains("private provider detail")); assertNull(failure.getCause());
    }

    @Test
    void reviewed_openai_defaults_never_read_credentials_or_headers_and_hidden_retries_are_refused() {
        var options = spy(OpenAiEmbeddingOptions.builder().model(MODEL).dimensions(2).maxRetries(0).encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT).build());
        doThrow(new AssertionError("credential getter must not be inspected")).when(options).getApiKey();
        doThrow(new AssertionError("credential getter must not be inspected")).when(options).getCredential();
        doThrow(new AssertionError("headers must not be inspected")).when(options).getCustomHeaders();
        var delegate = mock(OpenAiEmbeddingModel.class); when(delegate.getOptions()).thenReturn(options);
        var model = VersionedMemoryEmbeddingModel.forOpenAi(profile(8191), delegate);
        assertEquals(2, model.dimensions()); verify(delegate, never()).call(any(EmbeddingRequest.class)); verify(delegate, never()).dimensions();
        verify(options, never()).getApiKey(); verify(options, never()).getCredential(); verify(options, never()).getCustomHeaders();
        when(delegate.getOptions()).thenReturn(OpenAiEmbeddingOptions.builder().model(MODEL).dimensions(2).maxRetries(1).build());
        assertThrows(AgentLedgerException.class, () -> model.embed("fact")); verify(delegate, never()).call(any(EmbeddingRequest.class));
    }

    private static EmbeddingOptions defaults() { return EmbeddingOptions.builder().model(MODEL).dimensions(2).build(); }
    private static VersionedMemoryEmbeddingModel.Profile profile(int window) { return new VersionedMemoryEmbeddingModel.Profile("memory-model", 1, "embeddingBean", MODEL, 2, window, 8, 4); }
    private static EmbeddingResponse response(String model, float[] vector, int index) {
        var metadata = new EmbeddingResponseMetadata(); metadata.setModel(model);
        return new EmbeddingResponse(List.of(new Embedding(vector, index)), metadata);
    }
}
