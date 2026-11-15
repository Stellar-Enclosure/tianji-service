package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.stereotype.Component;
import top.egon.cola.component.agentflow.api.memory.MemoryFactTypeEnum;
import top.egon.cola.component.agentflow.api.memory.MemoryFactVO;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "legacyMemoryFactConverter"))
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = MemoryFactTypeEnum.class)
public interface LegacyMemoryFactConverter extends BaseForwardConverter<LegacyMemoryFactBO, MemoryFactVO> {
    @Override
    @Mapping(target = "factId", expression = "java(source.getEntry().memoryId())")
    @Mapping(target = "type", expression = "java(MemoryFactTypeEnum.valueOf(source.getEntry().kind().name()))")
    @Mapping(target = "revision", expression = "java(source.getEntry().revision())")
    @Mapping(target = "content", expression = "java(content(source.getEntry()))")
    @Mapping(target = "provenance", expression = "java(provenance(source))")
    @Mapping(target = "tombstone", expression = "java(source.getEntry().content() == null)")
    MemoryFactVO toTarget(LegacyMemoryFactBO source);

    default Map<String, Object> content(MemoryEntryDTO entry) {
        if (entry.content() == null) return null;
        var data = new LinkedHashMap<String, Object>();
        data.put("text", entry.content().text());
        data.put("facts", entry.content().facts());
        return data;
    }

    default List<MemoryFactVO.ProvenanceVO> provenance(LegacyMemoryFactBO fact) {
        var entry = fact.getEntry();
        if (entry.content() == null) return null;
        var source = entry.source();
        return source == null ? List.of() : List.of(new MemoryFactVO.ProvenanceVO(sourceRef(source),
                fact.getSourceRevision(), source.evidenceSha256(), fact.getSourceObservedAt()));
    }

    static String sourceRef(MemorySourceDTO source) {
        return "tianji-memory-source:" + MemoryJsonCodec.sha256("TIANJI_MEMORY_SOURCE_REF_V1:" + new MemoryJsonCodec().source(source));
    }
}
