package top.egon.cola.platform.tianji.agent.application.run;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO.DecisionEnum;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Trusted, current metadata/ACL lookup of exactly one opaque reference. No artifact body is read. */
@FunctionalInterface
public interface AgentResultArtifactVerifier {
    AgentResultArtifactEvidenceVO verify(@NotNull @Valid AgentResultArtifactQuery query);

    static AgentResultArtifactVerifier guarded(AgentResultArtifactVerifier authority, Clock clock) {
        return guarded(authority, clock, null);
    }

    static AgentResultArtifactVerifier guarded(AgentResultArtifactVerifier authority, Clock clock, Validator validator) {
        return new GuardedVerifier(authority, Objects.requireNonNull(clock, "clock"), validator);
    }

    @Slf4j
    final class GuardedVerifier implements AgentResultArtifactVerifier, AutoCloseable {
        private static final AgentResultArtifactEvidenceVO UNAVAILABLE =
                new AgentResultArtifactEvidenceVO(DecisionEnum.UNAVAILABLE, null, null, null, null);
        private static final int MAX_IN_FLIGHT = 8;

        private final AgentResultArtifactVerifier authority;
        private final Clock clock;
        private final Validator validator;
        private final ThreadPoolExecutor calls = new ThreadPoolExecutor(0, MAX_IN_FLIGHT, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), Thread.ofVirtual().inheritInheritableThreadLocals(false)
                        .name("tianji-artifact-evidence-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

        private GuardedVerifier(AgentResultArtifactVerifier authority, Clock clock, Validator validator) {
            this.authority = authority;
            this.clock = clock;
            this.validator = validator;
        }

        @Override
        public AgentResultArtifactEvidenceVO verify(AgentResultArtifactQuery query) {
            if (authority == null || query == null || Thread.currentThread().isInterrupted()
                    || TransactionSynchronizationManager.isActualTransactionActive()) return UNAVAILABLE;
            try {
                long started = System.nanoTime();
                var startedAt = clock.instant();
                var expected = query.snapshot();
                if (!expected.hasValidShape() || validator != null && !validator.validate(expected).isEmpty()) return UNAVAILABLE;
                var deadline = expected.getDeadlineAt();
                long budget = Duration.between(startedAt, deadline).toNanos();
                String digest = expected.querySha256();
                long remaining = budget - (System.nanoTime() - started);
                if (remaining <= 0 || !clock.instant().isBefore(deadline)) return UNAVAILABLE;
                var task = calls.submit(() -> clock.instant().isBefore(deadline) ? authority.verify(expected.snapshot()) : UNAVAILABLE);
                try {
                    remaining = budget - (System.nanoTime() - started);
                    if (remaining <= 0) return UNAVAILABLE;
                    var proof = task.get(remaining, TimeUnit.NANOSECONDS);
                    var now = clock.instant();
                    if (proof == null || Thread.currentThread().isInterrupted() || !now.isBefore(deadline)
                            || System.nanoTime() - started >= budget) return UNAVAILABLE;
                    return proof.decision() != DecisionEnum.ALLOW || proof.permits(digest, deadline, now) ? proof : UNAVAILABLE;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return UNAVAILABLE;
                } catch (TimeoutException | ExecutionException | CancellationException uncertain) {
                    return UNAVAILABLE;
                } finally {
                    task.cancel(true);
                }
            } catch (RejectedExecutionException unavailable) {
                log.debug("Agent artifact metadata verifier capacity unavailable");
                return UNAVAILABLE;
            } catch (RuntimeException malformed) {
                return UNAVAILABLE;
            }
        }

        @Override
        public void close() {
            calls.shutdownNow();
        }
    }
}
