package top.egon.cola.platform.tianji.contract.orchestration;

public interface ProcessClient {
    ProcessInstanceDTO start(StartProcessCommand command);
    ProcessInstanceDTO get(GetProcessInstanceQuery query);
}
