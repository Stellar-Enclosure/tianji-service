package top.egon.cola.platform.tianji.process.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import java.time.Clock;
import java.util.List;

/** The process API is opt-in and requires both identity and authorization filters. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProcessSecurityConfiguration {
    @Bean
    SecurityFilterChain processSecurityFilterChain(HttpSecurity http, Environment environment,
            ObjectProvider<IdpBearerAuthenticationFilter> identityFilters,
            ObjectProvider<Rbac3BearerAuthenticationFilter> authorizationFilters,
            ObjectProvider<PermissionExecutionGuard> policies) throws Exception {
        boolean enabled = environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (enabled) authorize.requestMatchers("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                            "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                            "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**").authenticated();
                    authorize.anyRequest().denyAll();
                });
        if (enabled) {
            IdpBearerAuthenticationFilter identity = identityFilters.getIfAvailable();
            Rbac3BearerAuthenticationFilter authorization = authorizationFilters.getIfAvailable();
            if (identity == null || authorization == null || policies.getIfAvailable() == null
                    || environment.getProperty("egon.cola.platform.tianquan.shoubing.register-filter", Boolean.class, true)
                    || environment.getProperty("egon.cola.platform.tianquan.jianshen.register-filter", Boolean.class, true)) {
                throw new IllegalStateException("Tianji Process API requires both identity and permission filters in its security chain");
            }
            http.exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, failure) -> {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write("{\"code\":\"TIANJI_UNAUTHENTICATED\"}");
                    })
                    .accessDeniedHandler((request, response, failure) -> {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write("{\"code\":\"TIANJI_PERMISSION_DENIED\"}");
                    }));
            http.addFilterBefore(identity, AnonymousAuthenticationFilter.class);
            http.addFilterBefore(new RequestContextFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(new VerifiedProcessIssuerFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(authorization, VerifiedProcessIssuerFilter.class);
        }
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(IdpEndpointAuthenticationPolicy.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    IdpEndpointAuthenticationPolicy processEndpointAuthenticationPolicy() {
        return new IdpEndpointAuthenticationPolicy(List.of("/actuator/health", "/actuator/health/**"), List.of(),
                List.of("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                        "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                        "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**"), true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    TianjiProcessAccess tianjiProcessAccess(PermissionExecutionGuard permissions) {
        return new TianjiProcessAccess(permissions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(ProcessModelValidator.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessModelValidator processModelValidator() { return new ProcessModelValidator(); }

    @Bean
    @ConditionalOnMissingBean(ProcessApiSchemaVerifier.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessApiSchemaVerifier processApiSchemaVerifier(ProcessEngine engine) {
        var process = processConfiguration(engine);
        var source = process.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        var verifier = new ProcessApiSchemaVerifier(source, process.getDatabaseSchema());
        verifier.verify();
        return verifier;
    }

    @Bean
    @ConditionalOnMissingBean(ProcessReleaseService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessReleaseService processReleaseService(ProcessEngine process, DmnEngine dmn,
                                                 ProcessModelValidator validator) {
        return new ProcessReleaseService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessExecutionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessExecutionService processExecutionService(ProcessEngine process, DmnEngine dmn,
                                                     ProcessModelValidator validator) {
        return new ProcessExecutionService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDecisionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessDecisionService processDecisionService(ProcessEngine process, DmnEngine dmn,
                                                   ProcessModelValidator validator) {
        return new ProcessDecisionService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    private static SpringProcessEngineConfiguration processConfiguration(ProcessEngine engine) {
        if (!(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration configuration)) {
            throw new IllegalStateException("Process API requires the Spring Flowable process engine");
        }
        return configuration;
    }

    private static SpringDmnEngineConfiguration decisionConfiguration(DmnEngine engine) {
        if (!(engine.getDmnEngineConfiguration() instanceof SpringDmnEngineConfiguration configuration)) {
            throw new IllegalStateException("Process API requires the Spring Flowable decision engine");
        }
        return configuration;
    }
}
