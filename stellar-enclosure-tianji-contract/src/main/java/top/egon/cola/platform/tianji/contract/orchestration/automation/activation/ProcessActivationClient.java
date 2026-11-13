package top.egon.cola.platform.tianji.contract.orchestration.automation.activation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Separates activation commands from observations of current and historical activations. */
public interface ProcessActivationClient {

    AutomationActivationDTO activate(@NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}") String definitionKey,
                                     @NotNull @Valid ActivationCommand command);

    /** A command lookup may not be combined with a cursor. */
    ActivationPageDTO list(@NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}") String definitionKey,
                           @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") String commandId,
                           @Min(1) @Max(100) Integer limit, @Size(min = 1, max = 2048) String cursor);
}
