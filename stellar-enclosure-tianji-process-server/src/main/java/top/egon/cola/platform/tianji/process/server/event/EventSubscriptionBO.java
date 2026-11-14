package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** Durable facts of one prospective or bound automation wait. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EventSubscriptionBO {
    @Min(1) private long tenantId;
    @NotNull @ToString.Exclude private String tokenSha256;
    @NotNull private String instanceId;
    @NotNull private String parentScopeId;
    @NotNull private String targetNodeId;
    @NotNull private UUID reservationId;
    @NotNull private String routeKey;
    @Min(1) private long routeVersion;
    @NotNull private String sourceRef;
    private String nativeExecutionId;
    private String nativeSubscriptionId;
    private String nodeExecutionRef;
    @NotNull private State state;
    @Min(1) private long revision;
    @NotNull private Instant deadlineAt;
    @NotNull private Instant createdAt;
    private Instant closedAt;

    public enum State { RESERVED, BOUND, CONSUMED, CLOSED }
}
