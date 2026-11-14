package top.egon.cola.platform.tianji.process.server.execution;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO;

import java.util.List;
import java.util.Map;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "processNodeExecutionConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = List.class)
public interface ProcessNodeExecutionConverter extends BaseForwardConverter<ProcessNodeExecutionBO, ProcessNodeExecutionDTO> {
    @Override
    @Mapping(target = "deadlineAt", source = "attemptDeadlineAt")
    @Mapping(target = "version", expression = "java(versionOf(row))")
    @Mapping(target = "eventReceiptIds", expression = "java(List.of())")
    @Mapping(target = "availableActions", expression = "java(List.of())")
    @Mapping(target = "output", ignore = true)
    @Mapping(target = "agentRunId", ignore = true)
    ProcessNodeExecutionDTO toTarget(ProcessNodeExecutionBO row);

    default ProcessNodeExecutionDTO toTarget(ProcessNodeExecutionBO row, boolean allowData, boolean allowDiagnostics) {
        var projection = toTarget(row);
        if (projection == null) return null;
        if (allowData) projection.setOutput(row.getOutput());
        if (allowDiagnostics) projection.setAgentRunId(row.getAgentRunId());
        return projection;
    }

    default String versionOf(ProcessNodeExecutionBO row) {
        return ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_NODE_OBSERVATION_V2", Long.toString(row.getTenantId()),
                row.getInstanceId(), row.getOccurrenceId(), Integer.toString(row.getAttempt()), row.getNodeExecutionRef(),
                Long.toString(row.getRevision())), Map.of());
    }
}
