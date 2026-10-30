package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TianjiMemoryHttpConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TianjiMemoryHttpConfiguration.class)
            .withBean(TianjiAgentAccess.class, () -> mock(TianjiAgentAccess.class));

    @Test void authority_is_opt_in_and_enabling_http_requires_both_storage_and_the_verified_api() {
        runner.run(context -> assertTrue(context.getBeansOfType(MemoryAuthorization.class).isEmpty()));
        runner.withPropertyValues("stellar.tianji.agent.memory.http.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("stellar.tianji.agent.memory.http.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.api.enabled=true")
                .run(context -> { assertNull(context.getStartupFailure()); assertInstanceOf(TianjiMemoryHttpAuthorization.class, context.getBean("tianjiMemoryAuthorization")); });
        var custom = mock(MemoryAuthorization.class);
        runner.withPropertyValues("stellar.tianji.agent.memory.http.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.api.enabled=true")
                .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> custom)
                .run(context -> { assertNull(context.getStartupFailure()); assertSame(custom, context.getBean("tianjiMemoryAuthorization")); });
    }

    @Test void duplicate_or_unbounded_team_mappings_are_rejected() {
        var team = new TianjiMemoryAccessProperties.Team(20, URI.create("https://idp.example/20"), "team", "research", TianjiMemoryAccessProperties.DirectoryType.ORGANIZATION, "10");
        assertThrows(IllegalArgumentException.class, () -> new TianjiMemoryAccessProperties(List.of(team, team)));
        assertThrows(IllegalArgumentException.class, () -> new TianjiMemoryAccessProperties.Team(20, team.issuer(), "team", "research", team.directoryType(), "9223372036854775808"));
        assertThrows(IllegalArgumentException.class, () -> new TianjiMemoryAccessProperties.Team(20, URI.create("http://idp.example/20"), "team", "research", team.directoryType(), "10"));
    }
}
