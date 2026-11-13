package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record NodePolicyVO(
        @NotNull Duration nodeTimeout,
        @NotNull Duration attemptTimeout,
        @NotNull Duration dispatchTimeout,
        @NotNull @Valid PolicyActionVO onFailure,
        @NotNull @Valid PolicyActionVO onAttemptTimeout,
        @NotNull @Valid PolicyActionVO onNodeTimeout,
        @NotNull @Valid RetryPolicyVO retry,
        @NotNull String cancel,
        @NotNull String onUnknownEffect) {

    public NodePolicyVO {
        require(nodeTimeout != null && nodeTimeout.isPositive() && nodeTimeout.compareTo(Duration.ofDays(30)) <= 0);
        require(attemptTimeout != null && attemptTimeout.isPositive() && attemptTimeout.compareTo(nodeTimeout) <= 0);
        require(dispatchTimeout != null && dispatchTimeout.isPositive() && dispatchTimeout.compareTo(attemptTimeout) <= 0);
        require(onFailure != null && onAttemptTimeout != null && onNodeTimeout != null && retry != null);
        require("BRANCH".equals(onNodeTimeout.action()) || "END".equals(onNodeTimeout.action()));
        require("BEST_EFFORT_STOP".equals(cancel) && "WAIT_RECOVERY".equals(onUnknownEffect));
    }

    public record RetryPolicyVO(@Min(1) @Max(10) int maxAttempts, @NotNull Duration backoff,
                                @NotNull @Size(max = 16) List<String> retryableCodes) {
        public RetryPolicyVO {
            require(maxAttempts >= 1 && maxAttempts <= 10);
            require(backoff != null && !backoff.isNegative() && backoff.compareTo(Duration.ofHours(1)) <= 0);
            require(retryableCodes != null && retryableCodes.size() <= 16);
            var seen = new HashSet<String>();
            for (String code : retryableCodes) {
                require(code != null && code.matches("[A-Z][A-Z0-9_]{0,127}") && seen.add(code));
            }
            retryableCodes = List.copyOf(retryableCodes);
        }
    }

    public record PolicyActionVO(@NotNull String action, String branchTarget) {
        private static final Set<String> ACTIONS = Set.of("BRANCH", "END", "WAIT_RECOVERY", "RETRY");

        public PolicyActionVO {
            require(action != null && ACTIONS.contains(action));
            if ("BRANCH".equals(action)) {
                require(ProcessTypedJson.isId(branchTarget));
            } else {
                require(branchTarget == null);
            }
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation node policy");
        }
    }
}
