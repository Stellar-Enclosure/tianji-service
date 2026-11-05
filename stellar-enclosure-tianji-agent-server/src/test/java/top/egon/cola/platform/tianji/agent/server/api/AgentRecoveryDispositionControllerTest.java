package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryDispositionDTO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryEffectEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryLifecycleEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryReasonEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryToolCoverageEnum;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentRecoveryToolEvidenceDTO;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentRecoveryDispositionControllerTest {
    private static final String PATH = "/api/tianji/v1/agent-runs/{runId}/recovery-disposition";
    private static final String RUN = "run-01";
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00.123456Z");
    private static final URI ISSUER = URI.create("https://issuer.example/tenant-20");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER,
            "same-owner", ISSUER.toString());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void recovery_disposition_route_exists() {
        var type = assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.api.AgentRecoveryDispositionController"));
        assertArrayEquals(new String[]{PATH}, type.getAnnotation(RequestMapping.class).path());
        var flags = type.getAnnotation(ConditionalOnProperty.class);
        assertEquals("stellar.tianji.agent", flags.prefix());
        assertEquals(Set.of("api.enabled", "storage.enabled", "recovery-diagnostics.enabled"), Set.of(flags.name()));
        assertEquals("true", flags.havingValue());
        assertFalse(flags.matchIfMissing());
        assertEquals(ConditionalOnWebApplication.Type.SERVLET, type.getAnnotation(ConditionalOnWebApplication.class).type());
        var method = Arrays.stream(type.getDeclaredMethods()).filter(value -> value.isAnnotationPresent(GetMapping.class))
                .findFirst().orElseThrow();
        var resource = method.getAnnotation(PermissionResource.class);
        assertEquals("tianji.api.agent.recovery.disposition", resource.key());
        assertArrayEquals(new String[]{TianjiAgentPermissions.READ_SCOPE}, resource.oauthScopes());
        assertEquals(Set.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                Set.of(resource.allowedPrincipals()));
        assertArrayEquals(new String[]{TianjiAgentPermissions.READ}, method.getAnnotation(RequiresPermissions.class).codes());
    }

    @Test
    void exposes_only_same_owner_closed_recovery_evidence() throws Exception {
        var fixture = new Fixture();
        when(fixture.ledger.inspectRecovery(OWNER, new GetAgentRunQuery(RUN))).thenReturn(
                evidence(RUN, AgentRecoveryEffectEnum.RECORDED_NONE, new AgentRecoveryToolEvidenceDTO(
                        AgentRecoveryToolCoverageEnum.TRUNCATED, true, null, true, null), true));
        var response = fixture.mvc.perform(get(PATH, RUN)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var body = JSON.readTree(response.getContentAsByteArray());
        assertEquals(Set.of("runId", "observedAt", "lifecycle", "reason", "agentEffect", "toolEvidence", "evidenceConflict"),
                body.propertyNames());
        assertEquals(RUN, body.get("runId").asString());
        assertEquals(NOW.toString(), body.get("observedAt").asString());
        assertEquals("TERMINAL", body.get("lifecycle").asString());
        assertEquals("TERMINAL_RECORDED", body.get("reason").asString());
        assertEquals("RECORDED_NONE", body.get("agentEffect").asString());
        assertTrue(body.get("evidenceConflict").asBoolean());
        var tool = body.get("toolEvidence");
        assertEquals(Set.of("coverage", "hasDispatching", "hasUnknownResult", "hasUnresolvedWrite", "hasLocalReceipt"),
                tool.propertyNames());
        assertEquals("TRUNCATED", tool.get("coverage").asString());
        assertTrue(tool.get("hasDispatching").asBoolean());
        assertTrue(tool.get("hasUnknownResult").isNull());
        assertTrue(tool.get("hasLocalReceipt").isNull());
        verify(fixture.access).require(TianjiAgentPermissions.RECOVERY_DISPOSITION_API);
        verify(fixture.ledger).inspectRecovery(OWNER, new GetAgentRunQuery(RUN));
        verifyNoMoreInteractions(fixture.ledger);
    }

    @Test
    void denies_before_read_and_rejects_non_exact_path_or_extra_query() throws Exception {
        var fixture = new Fixture();
        fixture.mvc.perform(get(PATH, RUN).queryParam("owner", "foreign"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        fixture.mvc.perform(get(PATH, " " + RUN + " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        when(fixture.access.require(TianjiAgentPermissions.RECOVERY_DISPOSITION_API))
                .thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "private reason"));
        fixture.mvc.perform(get(PATH, RUN).queryParam("owner", "foreign"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"))
                .andExpect(jsonPath("$.message").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(fixture.ledger);
    }

    @Test
    void distinguishes_unavailable_coverage_from_foreign_or_inconsistent_evidence() throws Exception {
        var fixture = new Fixture();
        var query = new GetAgentRunQuery(RUN);
        var noTools = new AgentRecoveryToolEvidenceDTO(AgentRecoveryToolCoverageEnum.UNAVAILABLE, null, null, null, null);
        when(fixture.ledger.inspectRecovery(OWNER, query)).thenReturn(
                evidence(RUN, AgentRecoveryEffectEnum.UNKNOWN, noTools, false),
                evidence("foreign-run", AgentRecoveryEffectEnum.UNKNOWN, noTools, false),
                evidence(RUN, AgentRecoveryEffectEnum.UNKNOWN,
                        new AgentRecoveryToolEvidenceDTO(AgentRecoveryToolCoverageEnum.UNAVAILABLE, false, null, null, null), false))
                .thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "private source"))
                .thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private source"));
        fixture.mvc.perform(get(PATH, RUN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.agentEffect").value("UNKNOWN"))
                .andExpect(jsonPath("$.toolEvidence.coverage").value("UNAVAILABLE"));
        for (int index = 0; index < 2; index++) {
            fixture.mvc.perform(get(PATH, RUN)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        }
        fixture.mvc.perform(get(PATH, RUN)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("TIANJI_RESOURCE_NOT_FOUND"));
        fixture.mvc.perform(get(PATH, RUN)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void registers_only_when_all_existing_prerequisites_and_explicit_flag_are_true() {
        var fixture = new Fixture();
        var runner = new WebApplicationContextRunner().withUserConfiguration(AgentRecoveryDispositionController.class)
                .withBean(AgentLedgerService.class, () -> fixture.ledger)
                .withBean(TianjiAgentAccess.class, () -> fixture.access);
        String api = "stellar.tianji.agent.api.enabled=true";
        String storage = "stellar.tianji.agent.storage.enabled=true";
        String enabled = "stellar.tianji.agent.recovery-diagnostics.enabled=true";
        runner.withPropertyValues(api, storage).run(ctx -> assertTrue(ctx.getBeansOfType(AgentRecoveryDispositionController.class).isEmpty()));
        runner.withPropertyValues(api, enabled).run(ctx -> assertTrue(ctx.getBeansOfType(AgentRecoveryDispositionController.class).isEmpty()));
        runner.withPropertyValues(storage, enabled).run(ctx -> assertTrue(ctx.getBeansOfType(AgentRecoveryDispositionController.class).isEmpty()));
        runner.withPropertyValues(api, storage, enabled).run(ctx -> assertEquals(1, ctx.getBeansOfType(AgentRecoveryDispositionController.class).size()));
        verifyNoInteractions(fixture.ledger);
    }

    private static AgentRecoveryDispositionDTO evidence(String runId, AgentRecoveryEffectEnum effect,
                                                         AgentRecoveryToolEvidenceDTO tool, boolean conflict) {
        return new AgentRecoveryDispositionDTO(runId, 41L, 4, NOW, AgentRecoveryLifecycleEnum.TERMINAL,
                AgentRecoveryReasonEnum.TERMINAL_RECORDED, effect, tool, conflict);
    }

    private static final class Fixture {
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
        final MockMvc mvc;

        Fixture() {
            when(access.require(TianjiAgentPermissions.RECOVERY_DISPOSITION_API))
                    .thenReturn(new TianjiAgentAccess.Authorized(OWNER, NOW.plusSeconds(60), ISSUER));
            mvc = MockMvcBuilders.standaloneSetup(new AgentRecoveryDispositionController(ledger, access))
                    .setControllerAdvice(new AgentApiErrors()).build();
        }
    }
}
