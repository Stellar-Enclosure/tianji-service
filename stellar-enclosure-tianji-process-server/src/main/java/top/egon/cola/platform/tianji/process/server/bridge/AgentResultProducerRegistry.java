package top.egon.cola.platform.tianji.process.server.bridge;

import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Operator grants bind the authenticated producer to an exact Process source and original dispatch owner. */
public final class AgentResultProducerRegistry {
    private final Map<Key, Registration> registrations;

    public AgentResultProducerRegistry(List<Registration> registrations) {
        if (registrations == null || registrations.isEmpty() || registrations.size() > 1_000) throw invalid();
        Map<Key, Registration> values = new HashMap<>();
        for (Registration item : registrations) {
            if (item == null || values.putIfAbsent(key(item.producer(), item.producerId(), item.sourceId(), item.owner()), item) != null) throw invalid();
        }
        this.registrations = Map.copyOf(values);
    }

    public Registration require(ProcessActor authenticated, AgentTerminalEvent event) {
        if (!service(authenticated) || event == null || authenticated.tenantId() != event.tenantId()
                || event.link().scopeType() != WorkerScopeTypeEnum.BPMN) throw new ProcessResourceNotFoundException();
        var owner = new ProcessActor(event.tenantId(), ProcessActor.SubjectType.SERVICE, event.ownerSubjectId(), event.ownerIssuerSha256());
        Registration registration = registrations.get(key(authenticated, event.producerId(), event.link().sourceId(), owner));
        if (registration == null) throw new ProcessResourceNotFoundException();
        return registration;
    }

    private static Key key(ProcessActor producer, String producerId, String source, ProcessActor owner) {
        return new Key(producer, producerId, source, owner);
    }
    private static boolean service(ProcessActor actor) {
        return actor != null && actor.subjectType() == ProcessActor.SubjectType.SERVICE && !"LEGACY".equals(actor.issuerFingerprint());
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result producer binding is invalid"); }
    private record Key(ProcessActor producer, String producerId, String source, ProcessActor owner) { }

    public record Registration(ProcessActor producer, String producerId, String sourceId, ProcessActor owner) {
        public Registration {
            if (!service(producer) || !service(owner) || producer.tenantId() != owner.tenantId()
                    || producerId == null || !producerId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || sourceId == null || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
        }
    }
}
