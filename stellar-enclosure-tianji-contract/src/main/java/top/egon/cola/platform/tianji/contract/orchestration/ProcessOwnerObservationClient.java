package top.egon.cola.platform.tianji.contract.orchestration;

/** Reads Process-local observations under the transport's current Process owner. */
public interface ProcessOwnerObservationClient {
    ProcessInstanceCorrelationPageDTO listByCorrelation(ListProcessInstancesByCorrelationQuery query);

    ProcessAgentOutcomePageDTO getAgentOutcomes(GetProcessAgentOutcomesQuery query);
}
