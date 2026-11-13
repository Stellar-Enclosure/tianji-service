package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record EventRouteVO(
        @NotNull String routeKey,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long version,
        @NotNull String type,
        @NotNull String mode,
        @NotNull String sourceRef,
        @NotNull @Valid ProcessSchemaVO payloadSchema,
        @NotNull List<@Valid MappingVO> mapping,
        @NotNull Duration ttl,
        @JsonInclude(JsonInclude.Include.NON_NULL) String targetDefinitionKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) String targetNodeId,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Valid ScheduleVO schedule) {

    public EventRouteVO {
        require(ProcessTypedJson.isId(routeKey) && version >= 1 && version <= ProcessTypedJson.V2_MAX_WIRE_INTEGER);
        require(type != null && type.matches("[A-Za-z_][A-Za-z0-9_.:-]{0,127}"));
        require(ProcessTypedJson.isId(sourceRef));
        require(payloadSchema != null && "object".equals(payloadSchema.type()));
        mapping = AutomationNodeBindingVO.mappings(mapping);
        require(mapping.stream().allMatch(entry -> entry.source().startsWith("event.")));
        require(ttl != null && ttl.isPositive() && ttl.compareTo(Duration.ofDays(30)) <= 0);
        if ("START".equals(mode)) {
            require(ProcessTypedJson.isId(targetDefinitionKey) && targetNodeId == null);
        } else {
            require("SIGNAL".equals(mode) && ProcessTypedJson.isId(targetNodeId) && targetDefinitionKey == null && schedule == null);
        }
    }

    public record ScheduleVO(
            @NotNull Instant startAt,
            @NotNull Duration interval,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant endAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Min(1) @Max(10000) Integer maxOccurrences) {

        public ScheduleVO {
            require(startAt != null && startAt.getNano() % 1_000 == 0 && interval != null && interval.isPositive());
            require(endAt != null || maxOccurrences != null);
            require(endAt == null || endAt.getNano() % 1_000 == 0 && !endAt.isBefore(startAt));
            require(maxOccurrences == null || maxOccurrences >= 1 && maxOccurrences <= 10_000);
            if (endAt != null && maxOccurrences == null) {
                try {
                    require(Duration.between(startAt, endAt).dividedBy(interval) < 10_000);
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("Invalid automation event route");
                }
            }
        }
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid automation event route");
        }
    }
}
