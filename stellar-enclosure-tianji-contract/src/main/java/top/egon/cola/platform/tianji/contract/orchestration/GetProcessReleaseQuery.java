package top.egon.cola.platform.tianji.contract.orchestration;

public record GetProcessReleaseQuery(String releaseVersion) {
    public GetProcessReleaseQuery { releaseVersion = OrchestrationChecks.release(releaseVersion); }
}
