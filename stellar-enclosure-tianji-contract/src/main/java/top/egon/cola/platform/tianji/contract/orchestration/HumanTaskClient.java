package top.egon.cola.platform.tianji.contract.orchestration;

/** Current-user task operations; commands never establish tenant, membership or an assignee identity. */
public interface HumanTaskClient {
    String PATH = "/api/tianji/v1/human-tasks";
    String READ_SCOPE = "tianji:task:read";
    String CLAIM_SCOPE = "tianji:task:claim";
    String COMPLETE_SCOPE = "tianji:task:complete";
    String TRANSFER_SCOPE = "tianji:task:transfer";

    HumanTaskDTO getHumanTask(GetHumanTaskQuery query);
    HumanTaskPageDTO humanTasks(ListHumanTasksQuery query);
    /** Explicit administrator visibility; ordinary ALL selection never substitutes for this separately authorized operation. */
    default HumanTaskPageDTO humanTasksForAdministration(ListHumanTasksQuery query) { throw new UnsupportedOperationException("Administrative task queries are not supported by this client"); }
    HumanTaskActionReceiptDTO claimHumanTask(ClaimHumanTaskCommand command);
    HumanTaskActionReceiptDTO unclaimHumanTask(UnclaimHumanTaskCommand command);
    HumanTaskActionReceiptDTO transferHumanTask(TransferHumanTaskCommand command);
    HumanTaskActionReceiptDTO completeHumanTask(CompleteHumanTaskCommand command);
}
