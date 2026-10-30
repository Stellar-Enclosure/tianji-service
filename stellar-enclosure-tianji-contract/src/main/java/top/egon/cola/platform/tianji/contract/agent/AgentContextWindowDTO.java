package top.egon.cola.platform.tianji.contract.agent;

public record AgentContextWindowDTO(String modelName, String modelRouteRef, String policyVersionRef,
                                     long windowTokens, long safetyMarginTokens) {
    public AgentContextWindowDTO {
        modelName = ContractChecks.contextIdentifier(modelName, "modelName", 512);
        modelRouteRef = ContractChecks.contextIdentifier(modelRouteRef, "modelRouteRef", 256);
        policyVersionRef = ContractChecks.contextIdentifier(policyVersionRef, "policyVersionRef", 256);
        if (windowTokens < 2 || safetyMarginTokens < 1 || safetyMarginTokens >= windowTokens) throw new IllegalArgumentException("context window is invalid");
    }
}
