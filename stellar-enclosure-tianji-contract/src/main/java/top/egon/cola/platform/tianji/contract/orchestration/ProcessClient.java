package top.egon.cola.platform.tianji.contract.orchestration;

public interface ProcessClient {
    String START_SCOPE = "tianji:process:start";
    String START_RESOURCE_KEY = "tianji.api.process.instance.start";

    ProcessInstanceDTO start(StartProcessCommand command);
    ProcessInstanceDTO get(GetProcessInstanceQuery query);
}
