package top.egon.cola.platform.tianji.agent.application.context;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "tianjiHostContextConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TianjiHostContextConverter extends BaseForwardConverter<TianjiHostContextBO, AgentExecutionContextVO> {
    @Override
    @Mapping(target = "scope", source = "verifiedScope")
    AgentExecutionContextVO toTarget(TianjiHostContextBO source);
}
