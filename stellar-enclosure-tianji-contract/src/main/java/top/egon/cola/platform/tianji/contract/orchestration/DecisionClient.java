package top.egon.cola.platform.tianji.contract.orchestration;

public interface DecisionClient {
    String EXECUTE_SCOPE = "tianji:decision:execute";
    String EXECUTE_RESOURCE_KEY = "tianji.api.process.decision.evaluate";
    DecisionResultDTO evaluate(EvaluateDecisionCommand command);
}
