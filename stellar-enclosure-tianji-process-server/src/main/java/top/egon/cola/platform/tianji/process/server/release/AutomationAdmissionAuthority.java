package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@FunctionalInterface
public interface AutomationAdmissionAuthority {
    AutomationAdmissionProofVO observe(@NotNull @Valid AutomationAdmissionQuery query);
}
