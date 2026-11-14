package top.egon.cola.platform.tianji.process.server.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Host authority SPI. Observation is read-only and runs outside Process transactions and root guards. */
@FunctionalInterface
public interface WorkerEffectRecoveryAuthority {
    WorkerEffectRecoveryProofVO observe(@NotNull @Valid WorkerEffectRecoveryQuery query);
}
