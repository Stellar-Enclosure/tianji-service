package top.egon.cola.platform.tianji.agent.application.run;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionLiteralVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionOperationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionPathVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticConditionVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticPredicateVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticRootTerminalVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplateLeafVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplateLinkVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplatePlanVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.AgenticTemplateTerminationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "agenticTemplateConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AgenticTemplateConverter extends BaseForwardConverter<AgenticTemplatePlanVO, AgenticPlanVO> {
    @Override AgenticPlanVO toTarget(AgenticTemplatePlanVO source);

    AgenticPlanVO.LeafVO toLeaf(AgenticTemplateLeafVO source);
    AgenticPlanVO.DependencyRefVO toReference(TemplateDependencyRefVO source);
    AgenticPlanVO.LinkVO toLink(AgenticTemplateLinkVO source);
    AgenticPlanVO.RootTerminalVO toRoot(AgenticRootTerminalVO source);
    AgenticPlanVO.PredicateVO toPredicate(AgenticPredicateVO source);
    AgenticPlanVO.OperationVO toOperation(AgenticConditionOperationVO source);
    AgenticPlanVO.PathVO toPath(AgenticConditionPathVO source);
    AgenticPlanVO.LiteralVO toLiteral(AgenticConditionLiteralVO source);

    default AgenticPlanVO.TerminationVO toTermination(AgenticTemplateTerminationVO source) {
        return switch (source) {
            case AgenticRootTerminalVO root -> toRoot(root);
            case AgenticPredicateVO predicate -> toPredicate(predicate);
        };
    }

    default AgenticPlanVO.ConditionVO toCondition(AgenticConditionVO source) {
        return switch (source) {
            case AgenticConditionOperationVO operation -> toOperation(operation);
            case AgenticConditionPathVO path -> toPath(path);
            case AgenticConditionLiteralVO literal -> toLiteral(literal);
        };
    }
}
