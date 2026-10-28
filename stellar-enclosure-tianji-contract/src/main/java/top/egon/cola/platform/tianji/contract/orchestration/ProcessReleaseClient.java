package top.egon.cola.platform.tianji.contract.orchestration;

/** Identity and tenant are supplied by the transport's trusted authentication context. */
public interface ProcessReleaseClient {
    ProcessReleaseDTO publish(PublishProcessReleaseCommand command);
    ProcessReleaseDTO get(GetProcessReleaseQuery query);
}
