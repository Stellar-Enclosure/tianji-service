package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Reviewed business team/scope to IAM directory-node mapping; descendant expansion requires separate directory evidence. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.memory.authorization")
public record TianjiMemoryAccessProperties(List<Team> teams) {
    public TianjiMemoryAccessProperties {
        teams = teams == null ? List.of() : List.copyOf(teams);
        if (teams.size() > 1000 || teams.stream().map(team -> List.of(Long.toString(team.tenantId()), team.issuer().toASCIIString(), team.teamId(), team.scopeId())).distinct().count() != teams.size()) throw invalid();
    }
    public enum DirectoryType { ORGANIZATION, DEPARTMENT }
    public record Team(long tenantId, URI issuer, String teamId, String scopeId, DirectoryType directoryType, String directoryId) {
        public Team {
            if (issuer == null) throw invalid();
            TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.USER, "memory-team", issuer.toASCIIString());
            new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, teamId); directoryType = Objects.requireNonNull(directoryType, "directoryType");
            if (scopeId == null || scopeId.isBlank() || scopeId.length() > 128 || !scopeId.equals(scopeId.trim())
                    || directoryId == null || !directoryId.matches("[1-9][0-9]{0,18}")) throw invalid();
            try { Long.parseLong(directoryId); } catch (NumberFormatException invalid) { throw invalid(); }
        }
        @Override public String toString() { return "MemoryTeamBinding[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory team authority mapping is invalid"); }
}
