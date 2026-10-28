package top.egon.cola.platform.tianji.client;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounds response buffering before a client attempts JSON parsing. */
final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedBodySubscriber(int limit) {
        this.limit = limit;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
            return;
        }
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            int count = buffer.remaining();
            if (count > limit - output.size()) {
                subscription.cancel();
                body.completeExceptionally(new TooLarge());
                return;
            }
            byte[] bytes = new byte[count];
            buffer.get(bytes);
            output.writeBytes(bytes);
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable failure) {
        body.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
        body.complete(output.toByteArray());
    }

    static final class TooLarge extends RuntimeException { }

    static boolean tooLarge(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++, current = current.getCause()) {
            if (current instanceof TooLarge) return true;
        }
        return false;
    }
}
