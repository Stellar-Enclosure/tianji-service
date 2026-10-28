package top.egon.cola.platform.tianji.agent.server.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.RequestContextFilter;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.server.api.AgentRunEventStream;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TianjiAgentSecurityConfiguration {
    @Bean
    SecurityFilterChain tianjiSecurityFilterChain(HttpSecurity http, Environment environment,
            ObjectProvider<IdpBearerAuthenticationFilter> identityFilters,
            ObjectProvider<Rbac3BearerAuthenticationFilter> authorizationFilters,
            ObjectProvider<PermissionExecutionGuard> policies) throws Exception {
        boolean enabled = environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/authorization/effective-permissions").authenticated()
                        .requestMatchers("/api/tianji/v1/agent-runs", "/api/tianji/v1/agent-runs/**")
                            .authenticated()
                        .requestMatchers("/api/tianji/v1/agent-sessions", "/api/tianji/v1/agent-sessions/**")
                            .authenticated()
                        .requestMatchers("/api/tianji/v1/agent-process-dispatches", "/api/tianji/v1/agent-process-dispatches/**")
                            .authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
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
        if (enabled) {
            IdpBearerAuthenticationFilter identity = identityFilters.getIfAvailable();
            Rbac3BearerAuthenticationFilter authorization = authorizationFilters.getIfAvailable();
            if (identity == null || authorization == null || policies.getIfAvailable() == null
                    || environment.getProperty("egon.cola.platform.tianquan.shoubing.register-filter", Boolean.class, true)
                    || environment.getProperty("egon.cola.platform.tianquan.jianshen.register-filter", Boolean.class, true)) {
                throw new IllegalStateException("Tianji Agent API requires both identity and permission filters in its security chain");
            }
            http.addFilterBefore(identity, AnonymousAuthenticationFilter.class);
            http.addFilterBefore(new RequestContextFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(new VerifiedAgentIssuerFilter(), IdpBearerAuthenticationFilter.class);
            http.addFilterAfter(authorization, VerifiedAgentIssuerFilter.class);
        }
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(IdpEndpointAuthenticationPolicy.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
    IdpEndpointAuthenticationPolicy tianjiEndpointAuthenticationPolicy() {
        return new IdpEndpointAuthenticationPolicy(List.of("/actuator/health", "/actuator/health/**"),
                List.of("/api/tianji/v1/agent-process-dispatches", "/api/tianji/v1/agent-process-dispatches/**"),
                List.of("/api/tianji/v1/agent-runs", "/api/tianji/v1/agent-runs/**",
                        "/api/tianji/v1/agent-sessions", "/api/tianji/v1/agent-sessions/**"), true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
    TianjiAgentAccess tianjiAgentAccess(PermissionExecutionGuard permissions) {
        return new TianjiAgentAccess(permissions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(AgentSessionManager.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
    AgentSessionManager tianjiAgentSessionManager(AgentLedgerService ledger, DataSource dataSource) {
        return new AgentSessionManager(ledger, dataSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
    AgentRunEventStream tianjiAgentEventStream(AgentLedgerService ledger, TianjiAgentAccess access) {
        return new AgentRunEventStream(ledger, access, Clock.systemUTC());
    }
}
