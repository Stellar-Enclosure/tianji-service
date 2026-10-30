package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.util.List;

/** Administrative grants only; incoming events cannot add or change registrations. */
@ConfigurationProperties(prefix = "stellar.tianji.process.agent-results")
public record AgentResultInboxProperties(List<Producer> producers, Duration pendingRetention) {
    public AgentResultInboxProperties {
        producers = producers == null ? List.of() : List.copyOf(producers);
        pendingRetention = pendingRetention == null ? Duration.ofDays(1) : pendingRetention;
        if (producers.size() > 1_000 || pendingRetention.compareTo(Duration.ofMinutes(1)) < 0
                || pendingRetention.compareTo(Duration.ofDays(30)) > 0 || pendingRetention.toNanos() % 1_000_000 != 0) {
            throw new IllegalArgumentException("Agent result Inbox settings are invalid");
        }
    }

    public List<AgentResultProducerRegistry.Registration> registrations() { return producers.stream().map(Producer::registration).toList(); }

    public record Producer(long tenantId, String issuer, String subjectId, String producerId, String sourceId,
                           String ownerIssuer, String ownerSubjectId) {
        public Producer {
            new AgentResultProducerRegistry.Registration(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer),
                    producerId, sourceId, ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, ownerSubjectId, ownerIssuer));
        }

        AgentResultProducerRegistry.Registration registration() {
            return new AgentResultProducerRegistry.Registration(ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, subjectId, issuer),
                    producerId, sourceId, ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, ownerSubjectId, ownerIssuer));
        }
    }
}
