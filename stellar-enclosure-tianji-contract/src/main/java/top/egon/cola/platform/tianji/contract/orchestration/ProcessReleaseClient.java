package top.egon.cola.platform.tianji.contract.orchestration;

/** Identity and tenant are supplied by the transport's trusted authentication context. */
public interface ProcessReleaseClient {
    String READ_SCOPE = "tianji:process:release:read";
    String READ_RESOURCE_KEY = "tianji.api.process.release.get";
    ProcessReleaseDTO publish(PublishProcessReleaseCommand command);
    ProcessReleaseDTO get(GetProcessReleaseQuery query);
}
