package top.egon.cola.platform.tianji.contract.orchestration;

public record OrchestrationErrorDTO(String code, String correlationId) {
    public OrchestrationErrorDTO {
        if (code == null || !code.matches("[A-Z0-9_]{1,80}")
                || correlationId != null && !correlationId.matches("[A-Za-z0-9._:-]{1,128}")) throw OrchestrationChecks.invalid();
    }
}
