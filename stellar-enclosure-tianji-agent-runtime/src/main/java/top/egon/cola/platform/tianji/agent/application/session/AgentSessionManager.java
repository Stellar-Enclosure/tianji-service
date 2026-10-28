package top.egon.cola.platform.tianji.agent.application.session;

import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionException;
import com.google.adk.sessions.SessionNotFoundException;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionListDTO;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DeleteAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owner-scoped session metadata operations; callers authenticate and authorize the actor first. */
public final class AgentSessionManager {
    private static final String APP = "tianji";
    private final DataSource dataSource;

    public AgentSessionManager(AgentLedgerService ledger, DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (!Objects.requireNonNull(ledger, "ledger").usesDataSource(dataSource)) {
            throw new IllegalArgumentException("Agent sessions and runs require the same DataSource");
        }
    }

    /** Repeating the same ID returns the owned session without replacing its state or history. */
    public AgentSessionDTO create(TrustedAgentActor actor, CreateAgentSessionCommand command) {
        Objects.requireNonNull(command, "command");
        PostgresAdkSessionService sessions = sessions(actor);
        try {
            return metadata(sessions.createSession(APP, sessions.adkUserId(), Map.of(), command.sessionId()).blockingGet());
        } catch (SessionException failure) {
            if (!SessionException.SESSION_ALREADY_EXISTS.equals(failure.getMessage())) throw unavailable();
            return get(actor, new GetAgentSessionQuery(command.sessionId()));
        }
    }

    public AgentSessionDTO get(TrustedAgentActor actor, GetAgentSessionQuery query) {
        Objects.requireNonNull(query, "query");
        PostgresAdkSessionService sessions = sessions(actor);
        Session session = sessions.getSession(APP, sessions.adkUserId(), query.sessionId(),
                Optional.of(GetSessionConfig.builder().numRecentEvents(0).build())).blockingGet();
        if (session == null) throw notFound();
        return metadata(session);
    }

    public AgentSessionListDTO list(TrustedAgentActor actor) {
        PostgresAdkSessionService sessions = sessions(actor);
        return new AgentSessionListDTO(sessions.listSessions(APP, sessions.adkUserId()).blockingGet().sessions().stream()
                .map(AgentSessionManager::metadata).toList());
    }

    public void delete(TrustedAgentActor actor, DeleteAgentSessionCommand command) {
        Objects.requireNonNull(command, "command");
        PostgresAdkSessionService sessions = sessions(actor);
        try {
            sessions.deleteSession(APP, sessions.adkUserId(), command.sessionId()).blockingAwait();
        } catch (SessionNotFoundException missing) {
            throw notFound();
        }
    }

    private PostgresAdkSessionService sessions(TrustedAgentActor actor) {
        return new PostgresAdkSessionService(dataSource, Objects.requireNonNull(actor, "actor"), APP);
    }

    private static AgentSessionDTO metadata(Session session) {
        return new AgentSessionDTO(session.id(), session.lastUpdateTime());
    }

    private static AgentLedgerException notFound() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Agent session not found");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent session storage is unavailable");
    }
}
