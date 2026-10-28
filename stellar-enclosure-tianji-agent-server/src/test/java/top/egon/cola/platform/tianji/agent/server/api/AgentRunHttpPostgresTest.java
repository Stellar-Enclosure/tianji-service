package top.egon.cola.platform.tianji.agent.server.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses only a separately created, empty loopback database with the explicit test-name prefix. */
@EnabledIfSystemProperty(named = "tianji.http.postgres.test.url", matches = ".+")
@Timeout(60)
class AgentRunHttpPostgresTest {
    private static final String BASE = "/api/tianji/v1/agent-runs";
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.SERVICE, "worker");
    private static AgentLedgerService ledger;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeAll
    static void migrateFreshDatabase() {
        String url = System.getProperty("tianji.http.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("HTTP integration tests require a loopback tianji_it_ database");
        }
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        if (new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM pg_namespace WHERE nspname = 'tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("HTTP integration tests require an empty database");
        }
        var flyway = Flyway.configure().dataSource(dataSource).schemas("tianji_agent")
                .defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        ledger = new AgentLedgerService(dataSource);
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
        ledger.publish(OWNER, new AgentDefinitionSpecDTO("review", 1, "SEQUENTIAL", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"),
                policy, Map.of()));
    }

    @BeforeEach
    void setup() {
        authenticate("40001", "worker");
        var access = new TianjiAgentAccess(mock(PermissionExecutionGuard.class), Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new AgentRunController(ledger, access),
                new AgentRunSseController(access, new AgentRunEventStream(ledger, access, Clock.systemUTC())))
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void http_commands_replay_and_cross_tenant_isolation_use_the_real_transactional_ledger() throws Exception {
        var start = new StartAgentRunCommand("review", 1, null, "request-1", null, "trace-1",
                new AgentInputDTO("question", List.of(), Map.of()), null);
        byte[] payload = mapper.writeValueAsBytes(start);
        String body = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        AgentRunDTO run = mapper.readValue(body, AgentRunDTO.class);
        String repeated = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(run.runId(), mapper.readValue(repeated, AgentRunDTO.class).runId());
        mvc.perform(get(BASE).param("limit", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].runId").value(run.runId()));

        authenticate("40002", "worker");
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(BASE + "/" + run.runId())).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + run.runId() + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
        authenticate("40001", "other");
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(BASE + "/" + run.runId())).andExpect(status().isNotFound());
        authenticate("40001", "worker");

        var cancel = new CancelAgentRunCommand(run.runId(), "cancel-1", run.revision(), "user request");
        byte[] cancellation = mapper.writeValueAsBytes(cancel);
        String cancelled = mvc.perform(post(BASE + "/" + run.runId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        String replayed = mvc.perform(post(BASE + "/" + run.runId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancellation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(cancelled), mapper.readTree(replayed));
        mvc.perform(get(BASE + "/" + run.runId() + "/result"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        String events = mvc.perform(get(BASE + "/" + run.runId() + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(events.contains("id: 1\nevent: agent.event"));
        assertTrue(events.contains("id: 2\nevent: agent.event"));
        assertTrue(events.endsWith("event: stream.complete\n\n"));
        String resumed = mvc.perform(get(BASE + "/" + run.runId() + "/events")
                        .header("Last-Event-ID", "1").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(resumed.contains("id: 1\n"));
        assertTrue(resumed.contains("id: 2\n"));
        assertTrue(resumed.endsWith("event: stream.complete\n\n"));
    }

    private static void authenticate(String tenant, String subject) {
        Instant now = Instant.now();
        var identity = new ServiceIdentityPrincipal(subject, tenant, subject, "token-id",
                URI.create("https://api.example/tianji"), 1,
                Set.of("tianji:agent:read", "tianji:agent:start", "tianji:agent:cancel"),
                "xingyuan", subject, "test", "credential", now.minusSeconds(60), now.plusSeconds(300));
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(identity, List.of(),
                URI.create("https://idp.example/tenants/" + tenant)));
    }
}
