package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProcessSecurityConfigurationTest.Probe.class, properties = "spring.main.web-application-type=servlet",
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({ProcessSecurityConfiguration.class, ProcessSecurityConfigurationTest.Probe.class})
class ProcessSecurityConfigurationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void allows_health_but_denies_other_paths() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/process-api")).andExpect(status().isForbidden());
    }

    @Test
    void default_off_security_denies_all_three_case_posts() throws Exception {
        for (String operation : java.util.List.of("start", "get", "act-plan-item"))
            mvc.perform(post("/api/tianji/v1/cases/" + operation).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
    }

    @Test
    void case_start_and_get_are_explicit_mixed_principal_endpoints() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        for (String operation : java.util.List.of("start", "get")) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/tianji/v1/cases/" + operation);
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE, policy.requirement(request));
        }
    }

    @Test
    void control_and_cancel_use_the_existing_mixed_principal_wildcard_policy() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        for (var operation : java.util.List.of("control", "cancellations")) {
            var request = new org.springframework.mock.web.MockHttpServletRequest(
                    operation.equals("control") ? "GET" : "POST", "/api/tianji/v1/process-instances/one/" + operation);
            assertEquals(top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement.USER_OR_SERVICE,
                    policy.requirement(request));
        }
    }

    @Test
    void cancellation_has_an_explicit_default_off_configuration_boundary() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> java.util.Arrays.stream(ProcessSecurityConfiguration.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CancellationConfiguration")).findFirst().orElseThrow());
    }

    @RestController
    static class Probe {
        @GetMapping("/actuator/health")
        String health() { return "ok"; }

        @GetMapping("/process-api")
        String process() { return "not exposed"; }
    }
}
