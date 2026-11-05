package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import java.time.Instant;
import java.util.Optional;

/** Historical Process START evidence through the original verified SERVICE and a separate current READ authority. */
public interface FixedBpmnStartReceiptRead {
    /** Pure resolution of the pinned backend and canonical original Process request. */
    TargetVO bind(Request request);

    /** Bounded, single read outside Agent transactions. Empty carries no conclusion about the effect. */
    Optional<ObservationVO> read(Request request, TargetVO expected, Instant deadlineAt);

    record Request(AgentRunAuthorization.Request run, ToolDescriptor descriptor, ToolActionRequest action, TrustedAgentActor executor) {
        public Request {
            if (run == null || descriptor == null || action == null || executor == null
                    || !run.actor().equals(action.owner()) || "LEGACY".equals(run.actor().issuerFingerprint())
                    || executor.subjectType() != TrustedAgentActor.SubjectType.SERVICE
                    || "LEGACY".equals(executor.issuerFingerprint()) || executor.tenantId() != run.actor().tenantId()
                    || !descriptor.reference().equals(action.tool()) || !descriptor.policy().equals(action.policy())
                    || descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY) throw invalid();
        }
        @Override public String toString() { return "StartReceiptReadRequest[redacted]"; }
    }

    record TargetVO(String namespaceSha256, GetProcessStartReceiptQuery query) {
        public TargetVO {
            if (!hash(namespaceSha256) || query == null) throw invalid();
        }
        @Override public String toString() { return "StartReceiptReadTarget[redacted]"; }
    }

    record ObservationVO(TargetVO target, TrustedAgentActor executor, ProcessStartReceiptDTO receipt,
                         String hostReceiptReference, Instant expiresAt) {
        public ObservationVO {
            if (target == null || executor == null || executor.subjectType() != TrustedAgentActor.SubjectType.SERVICE
                    || "LEGACY".equals(executor.issuerFingerprint()) || receipt == null || expiresAt == null
                    || hostReceiptReference == null || !hostReceiptReference.matches("tianji:ps-start:v1:[0-9a-f]{64}")) throw invalid();
        }
        @Override public String toString() { return "StartReceiptObservation[redacted]"; }
    }

    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Historical START receipt read binding is invalid");
    }
}
