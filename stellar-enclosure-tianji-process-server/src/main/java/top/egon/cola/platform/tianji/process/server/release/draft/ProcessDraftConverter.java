package top.egon.cola.platform.tianji.process.server.release.draft;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftCommandReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "processDraftConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface ProcessDraftConverter extends BaseForwardConverter<ProcessDraftBO, ProcessDraftDTO> {
    @Override
    @Mapping(target = "availableActions", ignore = true)
    @Mapping(target = "publication", ignore = true)
    @Mapping(target = "commandReceipt", ignore = true)
    ProcessDraftDTO toTarget(ProcessDraftBO draft);

    @Mapping(target = "availableActions", ignore = true)
    DraftSummaryDTO toSummary(ProcessDraftBO draft);

    DraftCommandReceiptDTO toReceipt(ProcessDraftCommandReceiptBO receipt);
}
