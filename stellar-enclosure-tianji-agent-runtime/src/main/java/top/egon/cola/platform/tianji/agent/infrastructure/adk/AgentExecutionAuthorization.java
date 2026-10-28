package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Objects;

/** Normalizes authorization failures without exposing identity or provider error details. */
final class AgentExecutionAuthorization {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionAuthorization.class);
    private final AgentRunAuthorization authorizer;
    private final AgentRunAuthorization.Request request;

    AgentExecutionAuthorization(AgentRunAuthorization authorizer, AgentRunAuthorization.Request request) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.request = Objects.requireNonNull(request, "request");
    }

    void require(AgentRunAuthorization.Boundary boundary) {
        if ("LEGACY".equals(request.actor().issuerFingerprint())) {
            throw new Failure(AgentRunAuthorization.Decision.DENY);
        }
        AgentRunAuthorization.Decision decision;
        try {
            decision = authorizer.authorize(request, boundary);
        } catch (RuntimeException failure) {
            log.warn("Agent authorization stage={} outcome=UNAVAILABLE errorType={}",
                    boundary, failure.getClass().getSimpleName());
            throw new Failure(AgentRunAuthorization.Decision.UNAVAILABLE);
        }
        if (decision != AgentRunAuthorization.Decision.ALLOW) {
            throw new Failure(decision == AgentRunAuthorization.Decision.DENY
                    ? AgentRunAuthorization.Decision.DENY : AgentRunAuthorization.Decision.UNAVAILABLE);
        }
    }

    static final class Failure extends AgentLedgerException {
        private final AgentRunAuthorization.Decision decision;

        Failure(AgentRunAuthorization.Decision decision) {
            super(decision == AgentRunAuthorization.Decision.DENY ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED
                    : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent execution authorization failed");
            this.decision = decision;
        }

        AgentRunAuthorization.Decision decision() { return decision; }
    }
}
