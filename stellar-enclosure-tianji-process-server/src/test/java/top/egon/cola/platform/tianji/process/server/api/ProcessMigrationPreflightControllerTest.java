package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ProcessMigrationPreflightDTO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ProcessMigrationPreflightStatusEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ValidateProcessMigrationQuery;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessMigrationPreflightControllerTest {
    private static final String PATH = "/api/tianji/v1/process-instances/{instanceId}/migration-preflight";
    private static final String INSTANCE = "process-1";
    private static final String TARGET = "release-2";
    private static final String DIGEST = "b".repeat(64);
    private static final ProcessActor OWNER = new ProcessActor(20, ProcessActor.SubjectType.USER, "owner", "a".repeat(64));
    private static final Instant OBSERVED = Instant.parse("2026-11-05T10:00:00.123456Z");
    private static final ValidateProcessMigrationQuery QUERY = new ValidateProcessMigrationQuery(INSTANCE, TARGET, DIGEST);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void migration_preflight_http_route_exists() {
        var controller = assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.api.ProcessMigrationPreflightController"));
        assertArrayEquals(new String[]{PATH}, controller.getAnnotation(RequestMapping.class).path());
        var flags = controller.getAnnotation(ConditionalOnProperty.class);
        assertEquals("stellar.tianji.process", flags.prefix());
        assertEquals(Set.of("api.enabled", "migration-preflight.enabled", "migration-preflight.http-enabled"), Set.of(flags.name()));
        assertEquals("true", flags.havingValue());
        assertFalse(flags.matchIfMissing());
        assertEquals(ConditionalOnWebApplication.Type.SERVLET, controller.getAnnotation(ConditionalOnWebApplication.class).type());
        var method = Arrays.stream(controller.getDeclaredMethods()).filter(value -> value.isAnnotationPresent(GetMapping.class))
                .findFirst().orElseThrow();
        var resource = method.getAnnotation(PermissionResource.class);
        assertEquals("tianji.api.process.migration.preflight", resource.key());
        assertEquals(Set.of("tianji:process:read", "tianji:process:release:read"), Set.of(resource.oauthScopes()));
        assertEquals(Set.of(PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE),
                Set.of(resource.allowedPrincipals()));
        assertEquals(Set.of(TianjiProcessPermissions.READ, TianjiProcessPermissions.RELEASE_READ),
                Set.of(method.getAnnotation(RequiresPermissions.class).codes()));
    }

    @Test
    void returns_exact_owner_pinned_structural_observation() throws Exception {
        var fixture = new Fixture();
        when(fixture.service.inspectForResourceActor(OWNER, QUERY)).thenReturn(
                report(INSTANCE, TARGET, ProcessMigrationPreflightStatusEnum.NATIVE_STRUCTURE_VALIDATED_ONLY, "wait"));
        var response = fixture.mvc.perform(request(INSTANCE)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var body = JSON.readTree(response.getContentAsByteArray());
        assertEquals(Set.of("instanceId", "sourceReleaseVersion", "targetReleaseVersion", "status", "activityId", "observedAt"),
                body.propertyNames());
        assertEquals(INSTANCE, body.get("instanceId").asString());
        assertEquals("release-1", body.get("sourceReleaseVersion").asString());
        assertEquals(TARGET, body.get("targetReleaseVersion").asString());
        assertEquals("NATIVE_STRUCTURE_VALIDATED_ONLY", body.get("status").asString());
        assertEquals("wait", body.get("activityId").asString());
        assertEquals(OBSERVED.toString(), body.get("observedAt").asString());
        var calls = inOrder(fixture.access, fixture.service);
        calls.verify(fixture.access).require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API);
        calls.verify(fixture.service).inspectForResourceActor(OWNER, QUERY);
        calls.verifyNoMoreInteractions();
    }

    @Test
    void denies_before_inspecting_and_rejects_open_or_inexact_input() throws Exception {
        var fixture = new Fixture();
        fixture.mvc.perform(get(PATH, INSTANCE)).andExpect(status().isBadRequest());
        fixture.mvc.perform(request(INSTANCE).queryParam("targetReleaseVersion", TARGET)).andExpect(status().isBadRequest());
        fixture.mvc.perform(request(INSTANCE).queryParam("owner", "foreign")).andExpect(status().isBadRequest());
        fixture.mvc.perform(get(PATH, INSTANCE).queryParam("targetReleaseVersion", TARGET)
                .queryParam("expectedTargetManifestSha256", "B".repeat(64))).andExpect(status().isBadRequest());
        when(fixture.access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API))
                .thenThrow(new AccessDeniedException("private"));
        fixture.mvc.perform(get(PATH, INSTANCE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verifyNoInteractions(fixture.service);
    }

    @Test
    void preserves_closed_negative_and_controlled_source_errors() throws Exception {
        var fixture = new Fixture();
        when(fixture.service.inspectForResourceActor(OWNER, QUERY)).thenReturn(
                report(INSTANCE, TARGET, ProcessMigrationPreflightStatusEnum.TARGET_UNAVAILABLE, null),
                report(INSTANCE, TARGET, ProcessMigrationPreflightStatusEnum.NATIVE_REJECTED, "wait"),
                report("foreign-instance", TARGET, ProcessMigrationPreflightStatusEnum.NATIVE_REJECTED, "wait"),
                report(INSTANCE, "other-target", ProcessMigrationPreflightStatusEnum.NATIVE_REJECTED, "wait"),
                report(INSTANCE, TARGET, ProcessMigrationPreflightStatusEnum.NATIVE_REJECTED, null))
                .thenThrow(new ProcessResourceNotFoundException()).thenThrow(new IllegalStateException("private"));
        var missing = fixture.mvc.perform(request(INSTANCE)).andExpect(status().isOk()).andReturn().getResponse();
        var json = JSON.readTree(missing.getContentAsByteArray());
        assertEquals("TARGET_UNAVAILABLE", json.get("status").asString());
        assertTrue(json.get("activityId").isNull());
        fixture.mvc.perform(request(INSTANCE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NATIVE_REJECTED"));
        for (int index = 0; index < 3; index++) {
            fixture.mvc.perform(request(INSTANCE)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("TIANJI_DEPENDENCY_UNAVAILABLE"));
        }
        fixture.mvc.perform(request(INSTANCE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        fixture.mvc.perform(request(INSTANCE)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void registers_only_when_internal_preflight_and_http_are_both_explicit() {
        var fixture = new Fixture();
        var runner = new WebApplicationContextRunner().withUserConfiguration(ProcessMigrationPreflightController.class)
                .withBean("processMigrationPreflightService", ProcessMigrationPreflightService.class, () -> fixture.service)
                .withBean("tianjiProcessAccess", TianjiProcessAccess.class, () -> fixture.access);
        String api = "stellar.tianji.process.api.enabled=true";
        String internal = "stellar.tianji.process.migration-preflight.enabled=true";
        String http = "stellar.tianji.process.migration-preflight.http-enabled=true";
        runner.withPropertyValues(api, internal).run(ctx -> assertTrue(ctx.getBeansOfType(ProcessMigrationPreflightController.class).isEmpty()));
        runner.withPropertyValues(api, http).run(ctx -> assertTrue(ctx.getBeansOfType(ProcessMigrationPreflightController.class).isEmpty()));
        runner.withPropertyValues(internal, http).run(ctx -> assertTrue(ctx.getBeansOfType(ProcessMigrationPreflightController.class).isEmpty()));
        runner.withPropertyValues(api, internal, http).run(ctx -> assertEquals(1, ctx.getBeansOfType(ProcessMigrationPreflightController.class).size()));
        verifyNoInteractions(fixture.service);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String instance) {
        return get(PATH, instance).queryParam("targetReleaseVersion", TARGET).queryParam("expectedTargetManifestSha256", DIGEST);
    }

    private static ProcessMigrationPreflightDTO report(String instance, String target, ProcessMigrationPreflightStatusEnum status,
                                                        String activity) {
        return new ProcessMigrationPreflightDTO(instance, "release-1", target, status, activity, OBSERVED);
    }

    private static final class Fixture {
        final ProcessMigrationPreflightService service = mock(ProcessMigrationPreflightService.class);
        final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
        final MockMvc mvc;

        Fixture() {
            when(access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API)).thenReturn(OWNER);
            mvc = MockMvcBuilders.standaloneSetup(new ProcessMigrationPreflightController(service, access))
                    .setControllerAdvice(new ProcessApiErrors()).build();
        }
    }
}
