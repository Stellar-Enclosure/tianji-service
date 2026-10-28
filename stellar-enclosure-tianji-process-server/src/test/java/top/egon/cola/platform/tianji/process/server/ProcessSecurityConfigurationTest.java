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

    @RestController
    static class Probe {
        @GetMapping("/actuator/health")
        String health() { return "ok"; }

        @GetMapping("/process-api")
        String process() { return "not exposed"; }
    }
}
