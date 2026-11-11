package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;

/** Operator-owned routes for the protected Process START entry point. */
@ConfigurationProperties(prefix = "stellar.tianji.process.governed-start", ignoreUnknownFields = false)
public record GovernedStartPilotProperties(List<Pilot> pilots) {
    public GovernedStartPilotProperties {
        pilots = pilots == null ? List.of() : List.copyOf(pilots);
        if (pilots.size() > 256) throw new IllegalArgumentException("Too many governed Process START pilots");
        var definitions = new HashSet<Scope>();
        var channels = new HashSet<Scope>();
        for (var pilot : pilots) {
            if (!definitions.add(new Scope(pilot.tenantId(), pilot.definitionKey()))
                    || !channels.add(new Scope(pilot.tenantId(), pilot.channel()))) {
                throw new IllegalArgumentException("Duplicate governed Process START pilot");
            }
        }
    }

    String channelFor(long tenantId, String definitionKey) {
        return pilots.stream().filter(pilot -> pilot.tenantId() == tenantId
                        && pilot.definitionKey().equals(definitionKey))
                .map(Pilot::channel).findFirst().orElse(null);
    }

    public record Pilot(long tenantId, String definitionKey, String channel) {
        public Pilot {
            if (tenantId <= 0 || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException("Governed Process START pilot is invalid");
            }
        }
    }

    private record Scope(long tenantId, String key) { }
}
