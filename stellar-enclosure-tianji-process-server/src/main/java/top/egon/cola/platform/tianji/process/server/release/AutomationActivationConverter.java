package top.egon.cola.platform.tianji.process.server.release;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "automationActivationConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AutomationActivationConverter extends BaseForwardConverter<AutomationActivationBO, AutomationActivationDTO> {
    @Override
    AutomationActivationDTO toTarget(AutomationActivationBO row);
}
