package top.egon.cola.platform.tianji.process.server;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.api.ProcessController;
import top.egon.cola.platform.tianji.process.server.api.WorkerController;
import top.egon.cola.platform.tianji.process.server.api.EngineTaskController;
import top.egon.cola.platform.tianji.process.server.api.ProcessNodeExecutionController;
import top.egon.cola.platform.tianji.process.server.api.ProcessDraftController;
import top.egon.cola.platform.tianji.process.server.api.ProcessActivationController;
import top.egon.cola.platform.tianji.process.server.api.ProcessApiErrors;
import top.egon.cola.platform.tianji.process.server.api.ProcessAutomationController;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.api.ProcessStartReceiptController;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceConverter;
import top.egon.cola.platform.tianji.process.server.execution.GovernedStartPilotProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartAuthority;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.DependencySnapshotVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCancellationReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessWorkerRecoveryReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessWorkerRecoveryService;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.CasePlanItemReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiCaseAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskCompletionObservation;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskReceiptStore;
import top.egon.cola.platform.tianji.process.server.task.ApprovalConsumerRegistry;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationProperties;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationService;
import top.egon.cola.platform.tianji.process.server.security.TianjiApprovalVerificationAccess;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient;
import top.egon.cola.platform.tianji.process.server.security.VerifiedProcessIssuerFilter;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicProperties;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.process.server.worker.WorkerNativeAvailability;
import top.egon.cola.platform.tianji.process.server.bridge.CaseSourceProperties;
import top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionCorrelationService;
import top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionPoller;
import top.egon.cola.platform.tianji.process.server.bridge.FlowableCaseWaitInspector;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStore;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxLagObservation;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxProperties;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistry;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessLinkedAgentOutcomeQuery;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessAgentBridgePoller;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationDeniedException;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionContext;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_NODE_RETRY_API;

/** The process API is opt-in and requires both identity and authorization filters. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(GovernedStartPilotProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProcessSecurityConfiguration {
    private static final PathPatternRequestMatcher.Builder MATCHER = PathPatternRequestMatcher.withDefaults();
    private static final List<RequestMatcher> DRAFT_ROUTES = List.of(
            MATCHER.matcher(HttpMethod.POST, ProcessDraftController.CREATE_PATH),
            MATCHER.matcher(HttpMethod.GET, ProcessDraftController.DRAFT_PATH),
            MATCHER.matcher(HttpMethod.PUT, ProcessDraftController.DRAFT_PATH),
            MATCHER.matcher(HttpMethod.GET, ProcessDraftController.LIST_PATH),
            MATCHER.matcher(HttpMethod.POST, ProcessDraftController.DISCARD_PATH),
            MATCHER.matcher(HttpMethod.POST, ProcessAutomationController.VALIDATE_PATH),
            MATCHER.matcher(HttpMethod.POST, ProcessAutomationController.RELEASES_PATH),
            MATCHER.matcher(HttpMethod.GET, ProcessAutomationController.RELEASE_PATH));
    private static final List<RequestMatcher> ACTIVATION_ROUTES = List.of(
            MATCHER.matcher(HttpMethod.POST, ProcessActivationController.PATH),
            MATCHER.matcher(HttpMethod.GET, ProcessActivationController.PATH));
    private static final List<RequestMatcher> AUTOMATION_INSTANCE_ROUTES = List.of(
            MATCHER.matcher(HttpMethod.POST, ProcessAutomationController.INSTANCES_PATH),
            MATCHER.matcher(HttpMethod.GET, ProcessAutomationController.INSTANCE_PATH));
    private static final RequestMatcher AUTOMATION_NODE_ROUTE = MATCHER.matcher(HttpMethod.GET, ProcessNodeExecutionController.PATH);
    private static final RequestMatcher AUTOMATION_NODE_RETRY_ROUTE = MATCHER.matcher(HttpMethod.POST, ProcessNodeExecutionController.RETRY_PATH);
    private static final List<RequestMatcher> AUTOMATION_ACTION_ROUTES = List.of(
            MATCHER.matcher(HttpMethod.GET, EngineTaskController.TASK_PATH),
            MATCHER.matcher(HttpMethod.POST, EngineTaskController.COMPLETION_PATH));
    private static final List<RequestMatcher> AUTOMATION_WORKER_ROUTES = List.of(
            MATCHER.matcher(HttpMethod.POST, WorkerController.ACQUIRE_AUTOMATION_PATH),
            MATCHER.matcher(HttpMethod.POST, WorkerController.COMPLETE_AUTOMATION_PATH),
            MATCHER.matcher(HttpMethod.POST, WorkerController.FAIL_AUTOMATION_PATH));

    @Bean("processNodeRetryAuthorization")
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessNodeExecutionController.NodeRetryAuthorization processNodeRetryAuthorization(
            @Qualifier("tianjiProcessAccess") TianjiProcessAccess access,
            @Qualifier("permissionServicePolicyGuard") ObjectProvider<PermissionServicePolicyGuard> servicePolicies) {
        return actor -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("Node retry authorization must precede its write transaction");
            }
            if (!actor.equals(access.require(AUTOMATION_NODE_RETRY_API))) {
                throw new AccessDeniedException("Node retry actor changed during authorization");
            }
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof Rbac3AuthenticationToken user) {
                var details = user.getPrincipal();
                var snapshot = details.snapshot();
                var view = snapshot.permissionSnapshot();
                if (view == null) throw new IllegalStateException("Current node retry permission evidence is unavailable");
                return earlier(details.identity().expiresAt(), earlier(snapshot.expiresAt(), view.snapshotExpiresAt()));
            }
            if (auth instanceof IdpAuthenticationToken token && token.getPrincipal() instanceof ServiceIdentityPrincipal service) {
                var policies = servicePolicies.getIfAvailable();
                if (policies == null) throw new IllegalStateException("Current node retry service permission is unavailable");
                try {
                    var snapshot = policies.require(service, AUTOMATION_NODE_RETRY_API);
                    PermissionExecutionContext.verifyInput(AUTOMATION_NODE_RETRY_API, snapshot);
                    return earlier(service.expiresAt(), snapshot.expiresAt());
                } catch (AuthorizationDeniedException denied) {
                    throw new AccessDeniedException("Current node retry permission is denied");
                }
            }
            throw new AuthenticationCredentialsNotFoundException("Current node retry identity is required");
        };
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    @Bean("automationWorkerMediaErrors")
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "worker.enabled"}, havingValue = "true")
    HandlerExceptionResolver automationWorkerMediaErrors() {
        return ProcessApiErrors.automationWorkerMediaErrors(request -> AUTOMATION_WORKER_ROUTES.stream()
                .anyMatch(route -> route.matches(request)));
    }

    @Bean("processNodeRetryMediaErrors")
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    HandlerExceptionResolver processNodeRetryMediaErrors() {
        return ProcessApiErrors.strictCommandMediaErrors(AUTOMATION_NODE_RETRY_ROUTE);
    }

    @Bean("processActivationMediaErrors")
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    HandlerExceptionResolver processActivationMediaErrors() {
        return ProcessApiErrors.closedJsonMediaErrors(request -> ACTIVATION_ROUTES.stream().anyMatch(route -> route.matches(request))
                        || AUTOMATION_INSTANCE_ROUTES.stream().anyMatch(route -> route.matches(request)),
                request -> AUTOMATION_NODE_ROUTE.matches(request) || AUTOMATION_ACTION_ROUTES.stream().anyMatch(route -> route.matches(request)));
    }

    @Bean
    SecurityFilterChain processSecurityFilterChain(HttpSecurity http, Environment environment,
            ObjectProvider<IdpBearerAuthenticationFilter> identityFilters,
            ObjectProvider<Rbac3BearerAuthenticationFilter> authorizationFilters,
            ObjectProvider<PermissionExecutionGuard> policies) throws Exception {
        boolean enabled = environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false);
        boolean startReceiptReadEnabled = environment.getProperty(
                "stellar.tianji.process.api.start-receipt-read-enabled", Boolean.class, false);
        boolean workerEnabled = environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false);
        boolean workerRecoveryEnabled = environment.getProperty("stellar.tianji.process.worker-recovery.enabled", Boolean.class, false);
        boolean inboxEnabled = environment.getProperty("stellar.tianji.process.agent-results.enabled", Boolean.class, false);
        boolean ownerOutcomesEnabled = environment.getProperty("stellar.tianji.process.agent-results.owner-outcomes-enabled", Boolean.class, false);
        boolean tasksEnabled = environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false);
        boolean taskPublicationEnabled = environment.getProperty("stellar.tianji.process.human-tasks.publish-enabled", Boolean.class, false);
        boolean approvalVerificationEnabled = environment.getProperty("stellar.tianji.process.approval-verification.enabled", Boolean.class, false);
        boolean casesEnabled = environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false);
        boolean newCaseStartsEnabled = environment.getProperty("stellar.tianji.process.cases.new-starts-enabled", Boolean.class, false);
        boolean cancellationEnabled = environment.getProperty("stellar.tianji.process.cancellation.enabled", Boolean.class, false);
        if (cancellationEnabled && !enabled) throw new IllegalStateException("Process cancellation requires the Process API security boundary");
        if (workerEnabled && !enabled) throw new IllegalStateException("Worker API requires the Process API security boundary");
        if (inboxEnabled && !enabled) throw new IllegalStateException("Agent result Inbox requires the Process API security boundary");
        if (ownerOutcomesEnabled && !inboxEnabled) throw new IllegalStateException("Process Agent outcomes require the durable Agent result Inbox");
        if (tasksEnabled && !enabled) throw new IllegalStateException("Human task API requires the Process API security boundary");
        if (taskPublicationEnabled && !tasksEnabled) throw new IllegalStateException("Governed task publication requires the human task API");
        if (approvalVerificationEnabled && !enabled) throw new IllegalStateException("Approval verification requires the Process API security boundary");
        if (casesEnabled && !enabled) throw new IllegalStateException("Case API requires the Process API security boundary");
        if (newCaseStartsEnabled && (!casesEnabled || !tasksEnabled)) throw new IllegalStateException("New Case starts require the Case and human task APIs");
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (enabled) authorize.requestMatchers("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                            "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                            "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**").authenticated();
                    if (enabled) authorize.requestMatchers(HttpMethod.GET, "/api/tianji/v1" + ProcessController.CORRELATION_PATH).authenticated()
                            .requestMatchers(HttpMethod.HEAD, "/api/tianji/v1" + ProcessController.CORRELATION_PATH).authenticated();
                    if (enabled && startReceiptReadEnabled) authorize
                            .requestMatchers(HttpMethod.GET, ProcessStartReceiptController.PATH).authenticated()
                            .requestMatchers(HttpMethod.HEAD, ProcessStartReceiptController.PATH).authenticated();
                    if (enabled) authorize.requestMatchers(DRAFT_ROUTES.toArray(RequestMatcher[]::new)).authenticated()
                            .requestMatchers(ACTIVATION_ROUTES.toArray(RequestMatcher[]::new)).authenticated()
                            .requestMatchers(AUTOMATION_INSTANCE_ROUTES.toArray(RequestMatcher[]::new)).authenticated()
                            .requestMatchers(AUTOMATION_NODE_ROUTE, AUTOMATION_NODE_RETRY_ROUTE).authenticated();
                    if (workerEnabled) authorize.requestMatchers("/api/tianji/v1/worker-tasks", "/api/tianji/v1/worker-tasks/**").authenticated()
                            .requestMatchers(AUTOMATION_WORKER_ROUTES.toArray(RequestMatcher[]::new)).authenticated();
                    if (inboxEnabled) authorize.requestMatchers("/api/tianji/v1/agent-result-inbox").authenticated();
                    if (tasksEnabled) authorize.requestMatchers("/api/tianji/v1/human-tasks", "/api/tianji/v1/human-tasks/**").authenticated()
                            .requestMatchers(AUTOMATION_ACTION_ROUTES.toArray(RequestMatcher[]::new)).authenticated();
                    if (approvalVerificationEnabled) authorize.requestMatchers(ApprovalClient.PATH).authenticated();
                    if (casesEnabled) authorize.requestMatchers("/api/tianji/v1/cases/start", "/api/tianji/v1/cases/get", "/api/tianji/v1/cases/act-plan-item").authenticated();
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
            http.addFilterBefore(new ClosedProcessSecurityErrors(startReceiptReadEnabled, ownerOutcomesEnabled,
                    workerEnabled && workerRecoveryEnabled, workerEnabled), RequestContextFilter.class);
        }
        return http.build();
    }

    /** Keeps the closed wire envelope when IAM short-circuits the selected Process operations. */
    static final class ClosedProcessSecurityErrors extends OncePerRequestFilter {
        private static final String CORRELATION = "/api/tianji/v1" + ProcessController.CORRELATION_PATH;
        private static final String OUTCOMES = "/api/tianji/v1/process-instances/{instanceId}/agent-outcomes";
        private final List<RequestMatcher> routes;

        ClosedProcessSecurityErrors(boolean startReceiptReadEnabled, boolean ownerOutcomesEnabled, boolean workerRecoveryEnabled) {
            this(startReceiptReadEnabled, ownerOutcomesEnabled, workerRecoveryEnabled, false);
        }

        ClosedProcessSecurityErrors(boolean startReceiptReadEnabled, boolean ownerOutcomesEnabled, boolean workerRecoveryEnabled,
                                    boolean workerEnabled) {
            var selected = new ArrayList<RequestMatcher>();
            addRead(selected, CORRELATION);
            if (startReceiptReadEnabled) addRead(selected, ProcessStartReceiptController.PATH);
            if (ownerOutcomesEnabled) addRead(selected, OUTCOMES);
            if (workerRecoveryEnabled) {
                selected.add(MATCHER.matcher(HttpMethod.GET, ProcessWorkerRecoveryClient.WORKER_FAILURES_PATH));
                selected.add(MATCHER.matcher(HttpMethod.POST, ProcessWorkerRecoveryClient.WORKER_RECOVERIES_PATH));
            }
            selected.addAll(DRAFT_ROUTES);
            selected.addAll(ACTIVATION_ROUTES);
            selected.addAll(AUTOMATION_INSTANCE_ROUTES);
            selected.add(AUTOMATION_NODE_ROUTE);
            selected.add(AUTOMATION_NODE_RETRY_ROUTE);
            selected.addAll(AUTOMATION_ACTION_ROUTES);
            if (workerEnabled) selected.addAll(AUTOMATION_WORKER_ROUTES);
            routes = List.copyOf(selected);
        }

        private static void addRead(List<RequestMatcher> selected, String path) {
            selected.add(MATCHER.matcher(HttpMethod.GET, path));
            selected.add(MATCHER.matcher(HttpMethod.HEAD, path));
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return routes.stream().noneMatch(route -> route.matches(request));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var cached = new ContentCachingResponseWrapper(response);
            chain.doFilter(request, cached);
            if (response.isCommitted()) return;

            String code = switch (cached.getStatus()) {
                case HttpServletResponse.SC_UNAUTHORIZED -> "TIANJI_UNAUTHENTICATED";
                case HttpServletResponse.SC_FORBIDDEN -> "TIANJI_PERMISSION_DENIED";
                case HttpServletResponse.SC_SERVICE_UNAVAILABLE -> "TIANJI_DEPENDENCY_UNAVAILABLE";
                default -> null;
            };
            if (code != null) {
                byte[] body = ("{\"code\":\"" + code + "\",\"correlationId\":null}").getBytes(StandardCharsets.UTF_8);
                cached.resetBuffer();
                cached.setContentType(MediaType.APPLICATION_JSON_VALUE);
                cached.setCharacterEncoding(StandardCharsets.UTF_8.name());
                cached.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                if (HttpMethod.HEAD.matches(request.getMethod())) {
                    response.setContentLength(body.length);
                } else {
                    cached.setContentLength(body.length);
                    cached.getOutputStream().write(body);
                }
            }
            cached.copyBodyToResponse();
        }
    }

    @Bean
    @ConditionalOnMissingBean(IdpEndpointAuthenticationPolicy.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    IdpEndpointAuthenticationPolicy processEndpointAuthenticationPolicy() {
        return new IdpEndpointAuthenticationPolicy(List.of("/actuator/health", "/actuator/health/**"),
                List.of("/api/tianji/v1/worker-tasks", "/api/tianji/v1/worker-tasks/**", "/api/tianji/v1/agent-result-inbox", ApprovalClient.PATH,
                        WorkerController.ACQUIRE_AUTOMATION_PATH, WorkerController.COMPLETE_AUTOMATION_PATH, WorkerController.FAIL_AUTOMATION_PATH),
                List.of("/api/tianji/v1/process-releases", "/api/tianji/v1/process-releases/**",
                        "/api/tianji/v1/process-instances", "/api/tianji/v1/process-instances/**",
                        "/api/tianji/v1" + ProcessController.CORRELATION_PATH, ProcessStartReceiptController.PATH,
                        "/api/tianji/v1/decisions", "/api/tianji/v1/decisions/**", "/api/tianji/v1/cases/start", "/api/tianji/v1/cases/get",
                        ProcessDraftController.CREATE_PATH, ProcessDraftController.DRAFT_PATH, ProcessDraftController.LIST_PATH,
                        ProcessDraftController.DISCARD_PATH, ProcessAutomationController.VALIDATE_PATH,
                        ProcessAutomationController.RELEASES_PATH, ProcessAutomationController.RELEASE_PATH,
                        ProcessActivationController.PATH, ProcessAutomationController.INSTANCES_PATH,
                        ProcessAutomationController.INSTANCE_PATH, ProcessNodeExecutionController.PATH, ProcessNodeExecutionController.RETRY_PATH,
                        EngineTaskController.TASK_PATH, EngineTaskController.COMPLETION_PATH), true);
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

    @Bean("processAutomationStorageReadiness")
    @ConditionalOnProperty(prefix = "stellar.tianji.process.automation", name = "enabled", havingValue = "true")
    SmartInitializingSingleton processAutomationStorageReadiness(ProcessEngine engine, Environment environment) {
        return () -> {
            if (environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    && !environment.getProperty("stellar.tianji.process.api.start-receipt-read-enabled", Boolean.class, false)) {
                throw new IllegalStateException("Process automation requires START receipt recovery");
            }
            engineSchemaVerifier(engine).verifyAutomationV14();
        };
    }

    @Bean("processEventStorageReadiness")
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "events.enabled"}, havingValue = "true")
    EventStorageReadiness processEventStorageReadiness(ProcessEngine engine) {
        engineSchemaVerifier(engine).verifyEventsV15();
        return new EventStorageReadiness();
    }

    static final class EventStorageReadiness { }

    @Bean("processApiSchemaVerifier")
    @ConditionalOnMissingBean(ProcessApiSchemaVerifier.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessApiSchemaVerifier processApiSchemaVerifier(ProcessEngine engine) {
        var verifier = engineSchemaVerifier(engine);
        verifier.verify();
        return verifier;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    static class DraftConfiguration {
        @Bean("processDraftStore")
        @Lazy
        ProcessDraftStore processDraftStore(ProcessEngine engine) {
            var process = processConfiguration(engine);
            return new ProcessDraftStore(new JdbcTemplate(processDraftTransactionManager(process).getDataSource()),
                    process.getDatabaseSchema());
        }

        @Bean("processDraftCommandReceiptStore")
        @Lazy
        ProcessDraftCommandReceiptStore processDraftCommandReceiptStore(ProcessEngine engine) {
            var process = processConfiguration(engine);
            return new ProcessDraftCommandReceiptStore(new JdbcTemplate(processDraftTransactionManager(process).getDataSource()),
                    process.getDatabaseSchema());
        }

        @Bean("automationReleaseStore")
        @Lazy
        AutomationReleaseStore automationReleaseStore(ProcessEngine engine) {
            var process = processConfiguration(engine);
            return new AutomationReleaseStore(new JdbcTemplate(processDraftTransactionManager(process).getDataSource()),
                    process.getDatabaseSchema());
        }

        @Bean("automationActivationStore")
        @Lazy
        AutomationActivationStore automationActivationStore(ProcessEngine engine) {
            var process = processConfiguration(engine);
            return new AutomationActivationStore(new JdbcTemplate(processDraftTransactionManager(process).getDataSource()),
                    process.getDatabaseSchema());
        }

        @Bean("automationReleaseDependencies")
        @ConditionalOnMissingBean(name = "automationReleaseDependencies")
        AutomationReleaseStore.DependencyResolver automationReleaseDependencies(
                @Qualifier("workerTopicRegistry") ObjectProvider<WorkerTopicRegistry> workers) {
            return new AutomationReleaseStore.DependencyResolver() {
                @Override
                public DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO source) {
                    var registry = workers.getIfAvailable();
                    if (registry == null) return DependencySnapshotVO.empty(actor.tenantId());
                    return new DependencySnapshotVO(actor.tenantId(), registry.automationReleaseRegistrations(actor.tenantId()),
                            Map.of(), Set.of(), Map.of(), Map.of());
                }

                @Override
                public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO source, DependencySnapshotVO snapshot) {
                    return resolve(actor, source).equals(snapshot);
                }
            };
        }

        @Bean("processDraftTransactionTemplate")
        @Lazy
        TransactionTemplate processDraftTransactionTemplate(ProcessEngine engine) {
            var transaction = new TransactionTemplate(processDraftTransactionManager(processConfiguration(engine)));
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return transaction;
        }

        private static DataSourceTransactionManager processDraftTransactionManager(SpringProcessEngineConfiguration process) {
            var source = process.getDataSource();
            if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
            if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager)
                    || source == null || source != manager.getDataSource()) {
                throw new IllegalStateException("Process drafts require the owning Process JDBC manager and data source");
            }
            return manager;
        }
    }

    private static ProcessApiSchemaVerifier engineSchemaVerifier(ProcessEngine engine) {
        var process = processConfiguration(engine);
        var source = process.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        return new ProcessApiSchemaVerifier(source, process.getDatabaseSchema());
    }

    @Bean
    @ConditionalOnMissingBean(ProcessReleaseService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessReleaseService processReleaseService(ProcessEngine process, DmnEngine dmn,
                                                 ProcessModelValidator validator, Environment environment,
                                                 ObjectProvider<WorkerTopicRegistry> registries,
                                                 ObjectProvider<ProcessAgentBridgePoller> bridges,
                                                 ObjectProvider<CmmnEngine> caseEngines,
                                                 ObjectProvider<CaseSourceRegistry> sources,
                                                 ObjectProvider<CaseCompletionPoller> completions) {
        boolean decisionTasksEnabled = environment.getProperty("stellar.tianji.process.decision-tasks.enabled", Boolean.class, false);
        boolean workerEnabled = environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false);
        WorkerTopicRegistry registry = workerEnabled ? registries.getIfAvailable() : null;
        if (workerEnabled && registry == null) throw new IllegalStateException("Worker API requires an operator-owned topic registry");
        boolean bridgeEnabled = environment.getProperty("stellar.tianji.process.agent-bridge.enabled", Boolean.class, false);
        boolean humanPublicationEnabled = environment.getProperty("stellar.tianji.process.human-tasks.publish-enabled", Boolean.class, false);
        if (humanPublicationEnabled && !environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false)) throw new IllegalStateException("Governed task publication requires the human task API");
        BooleanSupplier maintenanceReady = () -> {
            var bridge = bridges.getIfAvailable(); return bridge != null && bridge.isRunning();
        };
        if (environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false)) {
            var cmmn = caseEngines.getIfAvailable();
            if (cmmn == null) throw new IllegalStateException("Case publication requires the initialized CMMN engine");
            return new ProcessReleaseService(processConfiguration(process), caseConfiguration(cmmn), decisionConfiguration(dmn), validator,
                    decisionTasksEnabled, registry, bridgeEnabled, maintenanceReady, humanPublicationEnabled, true, sources.getObject(),
                    () -> { var poller = completions.getIfAvailable(); return poller != null && poller.isRunning(); },
                    environment.getProperty("stellar.tianji.process.cases.new-starts-enabled", Boolean.class, false));
        }
        return new ProcessReleaseService(processConfiguration(process), decisionConfiguration(dmn), validator, decisionTasksEnabled, registry,
                bridgeEnabled, maintenanceReady, humanPublicationEnabled);
    }

    @Bean("processExecutionService")
    @ConditionalOnMissingBean(ProcessExecutionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessExecutionService processExecutionServiceWithAutomation(ProcessEngine process, DmnEngine dmn,
            ProcessModelValidator validator, GovernedStartPilotProperties pilots, ObjectProvider<GovernedStartAuthority> authorities,
            @Qualifier("automationActivationStore") AutomationActivationStore activations,
            @Qualifier("automationReleaseStore") AutomationReleaseStore releases,
            @Qualifier("processInstanceBindingStore") ProcessInstanceBindingStore bindings,
            @Qualifier("automationInstanceConverter") AutomationInstanceConverter converter,
            ProcessApiSchemaVerifier verifier, Environment environment,
            @Qualifier("automationAdmissionAuthority") ObjectProvider<AutomationAdmissionAuthority> admissionAuthority,
            @Qualifier("automationReleaseTrustAuthority") ObjectProvider<AutomationReleaseTrustVerifier.TrustAuthority> trustAuthority) {
        var automation = new ProcessExecutionService.AutomationDependenciesVO(activations, releases, bindings, converter,
                verifier, environment, admissionAuthority, trustAuthority);
        return new ProcessExecutionService(processConfiguration(process), decisionConfiguration(dmn), validator, pilots,
                pilots.pilots().isEmpty() ? null : requiredPilotAuthority(authorities), automation);
    }

    ProcessExecutionService processExecutionService(ProcessEngine process, DmnEngine dmn,
            ProcessModelValidator validator, GovernedStartPilotProperties pilots,
            ObjectProvider<GovernedStartAuthority> authorities) {
        if (pilots.pilots().isEmpty()) {
            return new ProcessExecutionService(processConfiguration(process), decisionConfiguration(dmn), validator);
        }
        return new ProcessExecutionService(processConfiguration(process), decisionConfiguration(dmn), validator,
                pilots, requiredPilotAuthority(authorities));
    }

    private static GovernedStartAuthority requiredPilotAuthority(ObjectProvider<GovernedStartAuthority> authorities) {
        var candidates = authorities.stream().limit(2).toList();
        if (candidates.size() != 1) throw new IllegalStateException("Governed Process START pilots require one explicit authority");
        return candidates.getFirst();
    }

    @Bean
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    SmartInitializingSingleton governedStartPilotGuard(GovernedStartPilotProperties pilots,
                                                        ObjectProvider<ProcessExecutionService> executions,
                                                        ObjectProvider<GovernedStartAuthority> authorities) {
        return () -> {
            if (pilots.pilots().isEmpty()) return;
            var authority = requiredPilotAuthority(authorities);
            var candidates = executions.stream().limit(2).toList();
            if (candidates.size() != 1 || !candidates.getFirst().enforcesGovernedStartPilots(pilots, authority)) {
                throw new IllegalStateException("Protected Process START does not enforce the configured pilots");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDecisionService.class)
    @ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
    ProcessDecisionService processDecisionService(ProcessEngine process, DmnEngine dmn,
                                                   ProcessModelValidator validator) {
        return new ProcessDecisionService(processConfiguration(process), decisionConfiguration(dmn), validator);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "approval-verification.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(ApprovalVerificationProperties.class)
    static class ApprovalVerificationConfiguration {
        @Bean
        @ConditionalOnMissingBean(ApprovalConsumerRegistry.class)
        ApprovalConsumerRegistry approvalConsumerRegistry(ApprovalVerificationProperties properties) {
            if (properties.consumers().isEmpty()) throw new IllegalStateException("Approval verification requires registered consumers");
            return new ApprovalConsumerRegistry(properties.registrations());
        }
        @Bean
        @ConditionalOnMissingBean(HumanTaskReceiptStore.class)
        HumanTaskReceiptStore approvalReceiptStore(ProcessEngine process, Environment environment,
                ObjectProvider<CaseStartProvenanceStore> provenance) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw new IllegalStateException("Approval verification requires the owning Process JDBC manager");
            var store = environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false)
                    ? new HumanTaskReceiptStore(caseDataSource(configuration), configuration.getDatabaseSchema(), provenance.getObject())
                    : new HumanTaskReceiptStore(manager.getDataSource(), configuration.getDatabaseSchema());
            store.verifyStorage(); return store;
        }
        @Bean
        @ConditionalOnMissingBean(ApprovalVerificationService.class)
        ApprovalVerificationService approvalVerificationService(ProcessEngine process, HumanTaskReceiptStore store, ApprovalConsumerRegistry consumers) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager)) throw new IllegalStateException("Approval verification requires the owning Process JDBC manager");
            return new ApprovalVerificationService(manager.getDataSource(), manager, store, consumers);
        }
        @Bean
        @ConditionalOnMissingBean(TianjiApprovalVerificationAccess.class)
        TianjiApprovalVerificationAccess tianjiApprovalVerificationAccess(TianjiProcessAccess access) { return new TianjiApprovalVerificationAccess(access, Clock.systemUTC()); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "human-tasks.enabled"}, havingValue = "true")
    static class HumanTaskConfiguration {
        private static final String COMPLETION_METRICS_ENABLED = "stellar.tianji.process.human-tasks.completion-metrics-enabled";

        @Bean("humanTaskReceiptStore")
        @ConditionalOnMissingBean(HumanTaskReceiptStore.class)
        HumanTaskReceiptStore caseAwareHumanTaskReceiptStore(ProcessEngine process, Environment environment,
                ObjectProvider<CaseStartProvenanceStore> provenance) {
            if (!environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false)) return humanTaskReceiptStore(process);
            var configuration = processConfiguration(process);
            var store = new HumanTaskReceiptStore(caseDataSource(configuration), configuration.getDatabaseSchema(), provenance.getObject());
            store.verifyStorage(); return store;
        }

        HumanTaskReceiptStore humanTaskReceiptStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw new IllegalStateException("Human task API requires the owning Process JDBC manager");
            var store = new HumanTaskReceiptStore(manager.getDataSource(), configuration.getDatabaseSchema()); store.verifyStorage(); return store;
        }

        @Bean("flowableHumanTaskService")
        @ConditionalOnMissingBean(FlowableHumanTaskService.class)
        FlowableHumanTaskService flowableHumanTaskServiceWithAutomation(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts,
                Environment environment, ObjectProvider<CmmnEngine> caseEngines, ObjectProvider<CaseStartProvenanceStore> provenance,
                ObjectProvider<MeterRegistry> registries,
                @Qualifier("automationTaskParticipants") ObjectProvider<FlowableHumanTaskService.AutomationTaskParticipants> automation) {
            return flowableHumanTaskService(process, dmn, validator, receipts, environment, caseEngines, provenance, registries, automation.getIfAvailable());
        }

        FlowableHumanTaskService flowableHumanTaskService(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator, HumanTaskReceiptStore receipts,
                Environment environment, ObjectProvider<CmmnEngine> caseEngines, ObjectProvider<CaseStartProvenanceStore> provenance,
                ObjectProvider<MeterRegistry> registries) {
            return flowableHumanTaskService(process, dmn, validator, receipts, environment, caseEngines, provenance, registries, null);
        }

        private FlowableHumanTaskService flowableHumanTaskService(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator,
                HumanTaskReceiptStore receipts, Environment environment, ObjectProvider<CmmnEngine> caseEngines,
                ObjectProvider<CaseStartProvenanceStore> provenance, ObjectProvider<MeterRegistry> registries,
                FlowableHumanTaskService.AutomationTaskParticipants automation) {
            var completion = environment.getProperty(COMPLETION_METRICS_ENABLED, Boolean.class, false)
                    ? HumanTaskCompletionObservation.enabled(registries::getIfUnique)
                    : HumanTaskCompletionObservation.disabled();
            boolean cases = environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false);
            var cmmn = cases ? caseEngines.getIfAvailable() : null;
            if (cases && cmmn == null) throw new IllegalStateException("Case tasks require the initialized CMMN engine");
            var processConfiguration = processConfiguration(process);
            var dmnConfiguration = decisionConfiguration(dmn);
            var cmmnConfiguration = cases ? caseConfiguration(cmmn) : null;
            var caseProvenance = cases ? provenance.getObject() : null;
            return automation == null
                    ? new FlowableHumanTaskService(processConfiguration, cmmnConfiguration, dmnConfiguration, validator, receipts, caseProvenance, completion)
                    : new FlowableHumanTaskService(processConfiguration, cmmnConfiguration, dmnConfiguration, validator, receipts, caseProvenance, completion, automation);
        }

        @Bean
        @ConditionalOnMissingBean(TianjiHumanTaskAccess.class)
        TianjiHumanTaskAccess tianjiHumanTaskAccess(TianjiProcessAccess access) { return new TianjiHumanTaskAccess(access, Clock.systemUTC()); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "cases.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(CaseSourceProperties.class)
    static class CaseConfiguration {
        @Bean
        @ConditionalOnMissingBean(CaseStartProvenanceStore.class)
        CaseStartProvenanceStore caseStartProvenanceStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            var store = new CaseStartProvenanceStore(caseDataSource(configuration), configuration.getDatabaseSchema()); store.verifyStorage(); return store;
        }

        @Bean
        @ConditionalOnMissingBean(CasePlanItemReceiptStore.class)
        CasePlanItemReceiptStore casePlanItemReceiptStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            var store = new CasePlanItemReceiptStore(caseDataSource(configuration), configuration.getDatabaseSchema()); store.verifyStorage(); return store;
        }

        @Bean
        @ConditionalOnMissingBean(TianjiCaseAccess.class)
        TianjiCaseAccess tianjiCaseAccess(TianjiProcessAccess access) { return new TianjiCaseAccess(access, Clock.systemUTC()); }

        @Bean
        @ConditionalOnMissingBean(FlowableCaseService.class)
        FlowableCaseService flowableCaseService(ProcessEngine process, CmmnEngine cmmn, DmnEngine dmn, ProcessModelValidator validator,
                CaseStartProvenanceStore provenance, CasePlanItemReceiptStore receipts, Environment environment,
                CaseSourceRegistry registry, CaseCompletionInbox inbox, ObjectProvider<CaseCompletionPoller> completions) {
            return new FlowableCaseService(processConfiguration(process), caseConfiguration(cmmn), decisionConfiguration(dmn), validator, provenance, receipts,
                    environment.getProperty("stellar.tianji.process.cases.new-starts-enabled", Boolean.class, false), registry, inbox,
                    () -> { var poller = completions.getIfAvailable(); return poller != null && poller.isRunning(); });
        }

        @Bean
        @ConditionalOnMissingBean(CaseSourceRegistry.class)
        CaseSourceRegistry caseSourceRegistry(CaseSourceProperties properties, ObjectProvider<IdpStarterProperties> starter,
                ObjectProvider<TrustedIssuerRegistry> issuers, ObjectProvider<IdpServiceOAuth2Client> oauth) {
            if (properties.sources().isEmpty()) return new CaseSourceRegistry(properties, null, null, oauth::getObject);
            return new CaseSourceRegistry(properties, starter.getObject(), issuers.getObject(), oauth::getObject);
        }

        @Bean
        @ConditionalOnMissingBean(CaseCompletionInbox.class)
        CaseCompletionInbox caseCompletionInbox(ProcessEngine process) {
            var configuration = processConfiguration(process); var source = caseDataSource(configuration);
            return new CaseCompletionInbox(source, (DataSourceTransactionManager) configuration.getTransactionManager(), configuration.getDatabaseSchema());
        }

        @Bean
        @ConditionalOnMissingBean(VerifiedProcessModels.class)
        VerifiedProcessModels verifiedCaseModels(ProcessEngine process, CmmnEngine cmmn, DmnEngine dmn, ProcessModelValidator validator) {
            return new VerifiedProcessModels(processConfiguration(process), caseConfiguration(cmmn), decisionConfiguration(dmn), validator);
        }

        @Bean
        @ConditionalOnMissingBean(FlowableCaseWaitInspector.class)
        FlowableCaseWaitInspector flowableCaseWaitInspector(ProcessEngine process, ProcessReleaseService releases, VerifiedProcessModels models, ProcessModelValidator validator) {
            return new FlowableCaseWaitInspector(processConfiguration(process), releases, models, validator);
        }

        @Bean
        @ConditionalOnMissingBean(CaseCompletionCorrelationService.class)
        CaseCompletionCorrelationService caseCompletionCorrelationService(ProcessEngine process, CmmnEngine cmmn, ProcessReleaseService releases,
                VerifiedProcessModels models, CaseStartProvenanceStore provenance, CaseCompletionInbox inbox, CaseSourceRegistry registry,
                FlowableCaseWaitInspector waits, CaseSourceProperties properties) {
            return new CaseCompletionCorrelationService(processConfiguration(process), caseConfiguration(cmmn), releases, models, provenance, inbox, registry,
                    waits, properties.retryDelay(), properties.maxFailures());
        }

        @Bean(destroyMethod = "stop")
        @ConditionalOnMissingBean(CaseCompletionPoller.class)
        CaseCompletionPoller caseCompletionPoller(CaseSourceRegistry registry, CaseCompletionCorrelationService completion, CaseSourceProperties properties) {
            return new CaseCompletionPoller(registry, completion, properties.pollInterval(), properties.batchSize());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "worker.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(WorkerTopicProperties.class)
    static class WorkerConfiguration {
        @Bean("workerTopicRegistry")
        @ConditionalOnMissingBean(WorkerTopicRegistry.class)
        WorkerTopicRegistry workerTopicRegistry(WorkerTopicProperties properties) {
            return new WorkerTopicRegistry(properties.registrations(), properties.automationRegistrations());
        }

        @Bean("flowableWorkerService")
        @ConditionalOnMissingBean(FlowableWorkerService.class)
        FlowableWorkerService flowableWorkerService(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator,
                WorkerTopicRegistry registry,
                @Qualifier("processInstanceBindingStore") ObjectProvider<ProcessInstanceBindingStore> bindings,
                @Qualifier("automationReleaseStore") ObjectProvider<AutomationReleaseStore> releases,
                @Qualifier("automationInstanceGuardCommandInterceptor") ObjectProvider<AutomationInstanceGuardCommandInterceptor> guard,
                @Qualifier("processNodeExecutionStore") ObjectProvider<ProcessNodeExecutionStore> nodes,
                @Qualifier("processNodeLifecycleListener") ObjectProvider<ProcessNodeLifecycleListener> lifecycle) {
            var configuration = processConfiguration(process);
            WorkerNativeAvailability.requireConfigured(configuration);
            if (registry.hasAutomationRegistrations()) {
                engineSchemaVerifier(process).verifyAutomationV14();
                var automation = new FlowableWorkerService.AutomationWorkerParticipants(bindings.getObject(), releases.getObject(),
                        guard.getObject(), nodes.getObject(), lifecycle.getObject());
                return new FlowableWorkerService(configuration, decisionConfiguration(dmn), validator, registry, automation);
            }
            return new FlowableWorkerService(configuration, decisionConfiguration(dmn), validator, registry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "agent-results.enabled"}, havingValue = "true")
    @EnableConfigurationProperties(AgentResultInboxProperties.class)
    static class AgentResultConfiguration {
        private static final String LAG_METRICS_ENABLED = "stellar.tianji.process.agent-results.lag-metrics-enabled";

        @Bean
        @ConditionalOnMissingBean(AgentResultProducerRegistry.class)
        AgentResultProducerRegistry agentResultProducerRegistry(AgentResultInboxProperties properties) {
            return new AgentResultProducerRegistry(properties.registrations());
        }

        @Bean
        @ConditionalOnMissingBean(AgentDispatchStore.class)
        AgentDispatchStore agentDispatchStore(ProcessEngine engine) {
            var configuration = processConfiguration(engine);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) {
                throw new IllegalStateException("Agent result Inbox requires shared JDBC storage");
            }
            return new AgentDispatchStore(manager.getDataSource(), manager, configuration.getDatabaseSchema());
        }

        @Bean("processLinkedAgentOutcomeQuery")
        @ConditionalOnProperty(prefix = "stellar.tianji.process.agent-results", name = "owner-outcomes-enabled", havingValue = "true")
        ProcessLinkedAgentOutcomeQuery processLinkedAgentOutcomeQuery(ProcessEngine engine, AgentDispatchStore dispatches,
                AgentResultInbox inbox, AgentResultProducerRegistry producers, AgentResultInboxProperties properties) {
            var configuration = processConfiguration(engine);
            var source = caseDataSource(configuration);
            var manager = (DataSourceTransactionManager) configuration.getTransactionManager();
            String schema = configuration.getDatabaseSchema();
            new ProcessApiSchemaVerifier(source, schema).verifyAgentResultInbox();
            return new ProcessLinkedAgentOutcomeQuery(source, manager, schema, dispatches, inbox, producers, properties.registrations());
        }

        @Bean
        @ConditionalOnMissingBean(AgentResultInbox.class)
        AgentResultInbox agentResultInbox(ProcessEngine engine, AgentDispatchStore dispatches, AgentResultProducerRegistry producers,
                                           AgentResultInboxProperties properties, Environment environment,
                                           ObjectProvider<MeterRegistry> registries) {
            var configuration = processConfiguration(engine);
            if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) {
                throw new IllegalStateException("Agent result Inbox requires shared JDBC storage");
            }
            var lag = environment.getProperty(LAG_METRICS_ENABLED, Boolean.class, false)
                    ? AgentResultInboxLagObservation.enabled(registries::getIfUnique)
                    : AgentResultInboxLagObservation.disabled();
            return new AgentResultInbox(manager.getDataSource(), manager, configuration.getDatabaseSchema(),
                    dispatches, producers, properties.pendingRetention(), lag);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "cancellation.enabled"}, havingValue = "true")
    static class CancellationConfiguration {
        @Bean
        @ConditionalOnMissingBean(ProcessControlNativeAccess.class)
        ProcessControlNativeAccess processControlNativeAccess(ProcessEngine process) {
            var configuration = processConfiguration(process);
            DataSource source = caseDataSource(configuration);
            new ProcessApiSchemaVerifier(source, configuration.getDatabaseSchema()).verifyCancellation();
            return new ProcessControlNativeAccess(source, configuration);
        }

        @Bean
        @ConditionalOnMissingBean(ProcessCancellationReceiptStore.class)
        ProcessCancellationReceiptStore processCancellationReceiptStore(ProcessEngine process) {
            var configuration = processConfiguration(process);
            var store = new ProcessCancellationReceiptStore(caseDataSource(configuration), configuration.getDatabaseSchema());
            store.verifyStorage();
            return store;
        }

        @Bean
        @ConditionalOnMissingBean(ProcessControlService.class)
        ProcessControlService processControlService(ProcessEngine process, DmnEngine dmn, ProcessModelValidator validator,
                ProcessReleaseService releases, ProcessControlNativeAccess nativeAccess, ProcessCancellationReceiptStore cancellations,
                ObjectProvider<AgentDispatchStore> dispatch,
                @Qualifier("automationControlParticipants") ObjectProvider<ProcessControlService.AutomationControlParticipants> automation) {
            var configuration = processConfiguration(process);
            DataSource source = caseDataSource(configuration);
            var manager = (DataSourceTransactionManager) configuration.getTransactionManager();
            String schema = configuration.getDatabaseSchema();
            new ProcessApiSchemaVerifier(source, schema).verifyCancellation();
            if (!nativeAccess.usesDataSource(source) || !nativeAccess.usesEngine(configuration) || !cancellations.usesDataSource(source)) {
                throw new IllegalStateException("Process cancellation requires its exact owning storage participants");
            }
            var selected = dispatch.getIfAvailable();
            if (selected == null) selected = new AgentDispatchStore(source, manager, schema);
            selected.requireStorage(source, manager, schema);
            var participants = automation.getIfAvailable();
            return participants == null
                    ? new ProcessControlService(configuration, decisionConfiguration(dmn), validator, releases, nativeAccess, cancellations, selected)
                    : new ProcessControlService(configuration, decisionConfiguration(dmn), validator, releases, nativeAccess, cancellations, selected, participants);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "stellar.tianji.process",
            name = {"api.enabled", "worker.enabled", "worker-recovery.enabled"}, havingValue = "true")
    static class WorkerRecoveryConfiguration {
        @Bean("processWorkerRecoveryService")
        ProcessWorkerRecoveryService processWorkerRecoveryService(ProcessEngine process,
                @Qualifier("processReleaseService") ProcessReleaseService releases,
                @Qualifier("flowableWorkerService") FlowableWorkerService workers) {
            var configuration = processConfiguration(process);
            DataSource source = caseDataSource(configuration);
            String schema = configuration.getDatabaseSchema();
            var nativeAccess = new ProcessControlNativeAccess(source, configuration);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(5);
            var commands = new ProcessCommandReceiptStore(jdbc, schema);
            var audit = new ProcessWorkerRecoveryReceiptStore(source, schema);
            audit.verifyStorage();
            return new ProcessWorkerRecoveryService(configuration, releases, commands, nativeAccess, audit, workers);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "stellar.tianji.process",
            name = {"api.enabled", "migration-preflight.enabled"}, havingValue = "true")
    static class MigrationPreflightConfiguration {
        @Bean("processMigrationPreflightService")
        ProcessMigrationPreflightService processMigrationPreflightService(ProcessEngine process, DmnEngine dmn,
                @Qualifier("processReleaseService") ProcessReleaseService releases,
                @Qualifier("processModelValidator") ProcessModelValidator validator,
                @Qualifier("tianjiProcessAccess") TianjiProcessAccess access) {
            var service = new ProcessMigrationPreflightService(process, decisionConfiguration(dmn), releases, validator, access);
            service.verifyReadiness();
            return service;
        }
    }

    private static SpringCmmnEngineConfiguration caseConfiguration(CmmnEngine engine) {
        if (!(engine.getCmmnEngineConfiguration() instanceof SpringCmmnEngineConfiguration configuration)) {
            throw new IllegalStateException("Case API requires the Spring Flowable CMMN engine");
        }
        return configuration;
    }

    private static DataSource caseDataSource(SpringProcessEngineConfiguration configuration) {
        if (!(configuration.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null) throw new IllegalStateException("Case storage requires its owning Process JDBC manager");
        var source = configuration.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        if (source != manager.getDataSource()) throw new IllegalStateException("Case storage requires the exact owning Process DataSource");
        return source;
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
