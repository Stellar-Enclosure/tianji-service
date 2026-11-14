package top.egon.cola.platform.tianji.process.server.execution;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "automationInstanceConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AutomationInstanceConverter extends BaseForwardConverter<AutomationInstanceProjectionBO, AutomationInstanceDTO> {
    @Override
    @Mapping(target = "instanceId", source = "binding.instanceId")
    @Mapping(target = "definitionKey", source = "binding.definitionKey")
    @Mapping(target = "releaseVersion", source = "binding.releaseKey")
    @Mapping(target = "activationRevision", source = "binding.activationRevision")
    @Mapping(target = "profile", constant = "AUTOMATION_V2")
    @Mapping(target = "configurationSha256", source = "binding.configurationSha256")
    @Mapping(target = "submittedAt", source = "binding.submittedAt")
    @Mapping(target = "output", source = "authorizedOutput")
    AutomationInstanceDTO toTarget(AutomationInstanceProjectionBO row);
}
