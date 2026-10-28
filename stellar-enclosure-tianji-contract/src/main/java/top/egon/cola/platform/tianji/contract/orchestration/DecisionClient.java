package top.egon.cola.platform.tianji.contract.orchestration;

public interface DecisionClient {
    DecisionResultDTO evaluate(EvaluateDecisionCommand command);
}
