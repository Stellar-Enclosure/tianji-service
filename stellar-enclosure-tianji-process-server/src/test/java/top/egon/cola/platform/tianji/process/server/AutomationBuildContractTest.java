package top.egon.cola.platform.tianji.process.server;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mapstruct.AnnotateWith;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AutomationBuildContractTest {

    @Test
    void generatedConstructorRetainsAutowiredAndQualifier() throws NoSuchMethodException {
        var constructor = ProbeService.class.getDeclaredConstructor(Clock.class);
        assertEquals(1, ProbeService.class.getDeclaredConstructors().length);
        assertNotNull(constructor.getAnnotation(Autowired.class));

        var qualifier = constructor.getParameters()[0].getAnnotation(Qualifier.class);
        assertNotNull(qualifier, "Lombok must copy @Qualifier to the constructor parameter");
        assertEquals("probeClock", qualifier.value());
    }

    @Test
    void generatedConverterReadsLombokProperties() {
        var converter = Mappers.getMapper(ProbeConverter.class);
        assertEquals("probeConverter", converter.getClass().getAnnotation(Component.class).value());

        var target = converter.toTarget(new ProbeSourceBO("draft", 7));
        assertEquals("draft", target.getName());
        assertEquals(7, target.getRevision());
    }

    @Slf4j
    @Service("probeService")
    @RequiredArgsConstructor(onConstructor_ = @Autowired)
    static class ProbeService {
        @Qualifier("probeClock")
        private final Clock clock;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    static class ProbeSourceBO {
        private String name;
        private long revision;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    static class ProbeTargetDTO {
        private String name;
        private long revision;
    }

    @AnnotateWith(value = Component.class, elements = @AnnotateWith.Element(strings = "probeConverter"))
    @Mapper(componentModel = "spring")
    interface ProbeConverter extends BaseForwardConverter<ProbeSourceBO, ProbeTargetDTO> {
        @Override
        ProbeTargetDTO toTarget(ProbeSourceBO value);
    }
}
