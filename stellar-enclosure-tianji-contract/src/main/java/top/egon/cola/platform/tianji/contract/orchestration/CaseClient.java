package top.egon.cola.platform.tianji.contract.orchestration;

/** Engine-neutral fixed-Case operations. Tenant and actor come from the trusted transport context. */
public interface CaseClient {
    CaseInstanceDTO start(StartCaseCommand command);
    CaseInstanceDTO get(CaseInstanceQuery query);
    CaseOperationResult actOnPlanItem(ActOnPlanItemCommand command);
}
