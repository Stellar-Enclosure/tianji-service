package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.client.HttpAgentClient;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultWindowDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.ObservedExchangeDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.*;
import top.egon.cola.platform.tianji.process.server.compensation.ExpenseBusinessWorkerFixture;
import top.egon.cola.platform.tianji.process.server.compensation.ExpenseBusinessWorkerFixture.EffectsVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;
import top.egon.cola.platform.tianji.worker.RemoteWorkerPoller;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class BusinessProcessAgentAcceptanceIT {
    private static final String RELEASE = "expense-v1";
    private static final String BPMN = "expenseReview";
    private static final String DMN = "expenseRouting";
    private static final String CASE = "expenseException";
    private static final String AGENT = "expenseAnalysis";
    private static final String INSTRUCTION = "Analyze the expense fixture.";
    private static final String PRODUCER = "expense-agent";
    private static final Duration NETWORK = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Driver driver;
    private final List<ExpensePathResultDTO> results = new ArrayList<>();

    @BeforeAll
    void startApprovedIsolatedFixture() throws Exception {
        driver = Driver.open(Suite.BUSINESS);
    }

    @AfterAll
    void closeOwnedFixture() {
        if (driver != null) {
            try (var active = driver) {
                if (results.size() == 6) driver.publish(results);
                else driver.publishFailure(results);
            }
        }
    }

    @Order(1)
    @Test
    void normalWorkflowAcrossBothTenants() {
        runBoth(Scenario.NORMAL, 100, run -> {
            driver.assertNormalEnd(run);
            return Map.of();
        });
    }

    @Order(2)
    @Test
    void failedAgentCompensatesAcrossBothTenants() {
        runBoth(Scenario.AGENT_FAILURE, 700, run -> {
            driver.awaitEnd(run, "compensatedEnd");
            driver.assertAgent(run, "FAILED", "APPLIED");
            driver.assertEffects(run, 0, 1);
            return Map.of();
        });
    }

    @Order(3)
    @Test
    void manualCaseAndAuthorizedReviewAcrossBothTenants() {
        runBoth(Scenario.MANUAL_CASE, 1500, driver::driveManualCase);
    }

    private void runBoth(Scenario scenario, long amount, java.util.function.Function<RunDTO, Map<WitnessKind, WitnessDTO>> scenarioWork) {
        RunDTO previous = null;
        for (var tenant : driver.tenants()) {
            RunDTO run = driver.start(tenant, scenario, scenario.name().toLowerCase(Locale.ROOT), amount);
            try {
                if (scenario == Scenario.MANUAL_CASE) driver.armManualBeforeWorker(run);
                driver.startWorker(run, false);
                var extra = scenarioWork.apply(run);
                if (previous != null) driver.assertDisjoint(previous, run);
                results.add(driver.result(run, extra));
                previous = run;
            } finally {
                driver.stopWorker(run);
            }
        }
    }

    record RunDTO(TenantBindingDTO tenant, Scenario scenario, String window, UUID operationId, ProcessInstanceDTO instance) { }
    record DispatchDTO(String occurrenceId, String runId, String state, String sourceId) { }
    record OwnedAgentRunDTO(String status, Instant createdAt) { }
    record InboxDTO(String eventId, String fingerprint, String state) { }
    record OutboxDTO(String eventId, String status, int attempts) { }
    record NativeWaitDTO(String subscriptionId, String timerId, Instant deadline) { }
    record LeaseDTO(String jobId, String occurrenceId, String idempotencyKey, Instant leasedAt, Instant expiresAt,
                    String state, String tokenSha256) {
        @Override public String toString() { return "Expense lease witness"; }
    }
    private record DecisionDTO(String id, String rule, String lane) { }
    private record CaseOriginDTO(String id, String sourceId, String sourceOccurrence) { }
    private record TenantClients(HttpProcessClient owner, HttpProcessClient first, HttpProcessClient second, HttpProcessClient worker) { }
    private record StoreAccess(DataSource setup, DataSource runtime, JdbcTemplate observer) { }
    private record RenderedRelease(PublishProcessReleaseCommand command, String bpmnSha, String caseSha) { }

    static final class Driver implements AutoCloseable, ExpenseAcceptanceFaultRelay.Witnesses {
        private final ExpenseAcceptanceManifestDTO manifest;
        private final ExpenseAcceptanceHarness harness;
        private final HttpClient http;
        private final Map<StoreOwner, StoreAccess> stores;
        private final Map<Long, Properties> tenantSettings;
        private final Map<Long, TenantClients> clients;
        private final Map<String, ExpenseBusinessWorkerFixture> business = new HashMap<>();
        private final Map<ChildRole, ChildIdentityDTO> children = new ConcurrentHashMap<>();
        private final Map<ChildRole, Map<String, String>> configs = new EnumMap<>(ChildRole.class);
        private final Map<String, RunDTO> runs = new ConcurrentHashMap<>();
        private final Map<String, RemoteWorkerPoller> pollers = new HashMap<>();
        private final Map<String, PhaseDeadlineDTO> deadlines = new HashMap<>();
        private final Map<String, CompleteWorkerTaskCommand> dropped = new ConcurrentHashMap<>();
        private final Map<String, ChildIdentityDTO> outboxDropAgent = new ConcurrentHashMap<>();
        private final RenderedRelease release;
        private final S12ChildLogPrivacy logs;
        private ExpenseAcceptanceFaultRelay relay;

        static Driver open(Suite suite) throws Exception {
            return open(suite, null);
        }

        static Driver open(Suite suite, S12ChildLogPrivacy logs) throws Exception {
            require(logs == null || suite == Suite.S12_METRICS, "S12 metrics child privacy capture scope");
            String property = switch (suite) {
                case RECEIVE_LINK -> "tianji.s12.receive-link.manifest";
                case RECEIVE_LINK_REPLAY -> "tianji.s12.receive-link-replay.manifest";
                case S07_GOVERNED_START -> "tianji.s07.governed-start.manifest";
                case S07_APPROVED_START -> "tianji.s07.approved-start.manifest";
                case S12_OWNER_OBSERVATION -> "tianji.s12.owner-observation.manifest";
                case S12_MODEL_USAGE -> "tianji.s12.model-usage.manifest";
                case S12_METRICS -> logs == null
                        ? "tianji.s12.metrics.manifest" : "tianji.s12.metrics.log-privacy.manifest";
                default -> "tianji.expense.e2e.manifest";
            };
            var permit = ExpenseAcceptanceHarness.readManifest(System.getProperty(property));
            assumeTrue(permit.isPresent(), "Explicit isolated expense acceptance manifest is required");
            var manifest = permit.orElseThrow();
            require(manifest.suite() == suite, "Expense acceptance manifest suite");
            var tenantSettings = new HashMap<Long, Properties>();
            for (var tenant : manifest.tenants()) {
                var settings = privateProperties(tenant.credentialFile());
                if (suite == Suite.S12_OWNER_OBSERVATION) {
                    var processRead = value(settings, S12OwnerObservationEvidence.PROCESS_TOKEN);
                    var agentRead = value(settings, S12OwnerObservationEvidence.AGENT_TOKEN);
                    S12OwnerObservationEvidence.requireReadToken(processRead, tenant, tenant.ownerSubject(),
                            tenant.processResource(), "USER", ProcessControlClient.READ_SCOPE);
                    S12OwnerObservationEvidence.requireReadToken(agentRead, tenant, value(settings, "identity.bridge-subject"),
                            tenant.agentResource(), "SERVICE", "tianji:agent:read");
                }
                tenantSettings.put(tenant.tenantId(), settings);
            }
            var stores = sourceAccess(manifest);
            var harness = ExpenseAcceptanceHarness.open(manifest, lock -> verifySources(lock, stores),
                    ExpenseAcceptanceHarness.processBuilderLauncher(logs), System::nanoTime, Clock.systemUTC(),
                    duration -> Thread.sleep(duration));
            Driver driver = null;
            try {
                char[] password = tlsPassword(manifest.tls().credentialFile());
                try {
                    if (logs != null) logs.secret(S12ChildLogPrivacy.Credential.TLS, java.nio.CharBuffer.wrap(password));
                    var trust = ssl(manifest.tls().trustStore(), null, password);
                    var listener = ssl(manifest.tls().trustStore(), manifest.tls().relayKeyStore(), password);
                    var http = HttpClient.newBuilder().sslContext(trust).connectTimeout(NETWORK)
                            .followRedirects(HttpClient.Redirect.NEVER).build();
                    driver = new Driver(manifest, harness, stores, tenantSettings, http, suite.isS07() ? null : renderRelease(manifest), logs);
                    driver.boot(listener, trust, password);
                    return driver;
                } finally {
                    Arrays.fill(password, '\0');
                }
            } catch (Exception | AssertionError failure) {
                if (driver == null) harness.close();
                else driver.close();
                throw failure;
            }
        }

        private Driver(ExpenseAcceptanceManifestDTO manifest, ExpenseAcceptanceHarness harness, Map<StoreOwner, StoreAccess> stores,
                       Map<Long, Properties> tenantSettings, HttpClient http, RenderedRelease release, S12ChildLogPrivacy logs) {
            this.logs = logs;
            this.manifest = manifest;
            this.harness = harness;
            this.stores = Map.copyOf(stores);
            this.tenantSettings = Map.copyOf(tenantSettings);
            this.http = http;
            this.release = release;
            var scoped = new HashMap<Long, TenantClients>();
            for (var tenant : manifest.tenants()) {
                URI process = URI.create(manifest.endpoints().process());
                URI worker = URI.create(manifest.endpoints().workerRelay());
                scoped.put(tenant.tenantId(), new TenantClients(processClient(process, tenant, "token.owner"),
                        processClient(process, tenant, "token.reviewer-1"), processClient(process, tenant, "token.reviewer-2"),
                        processClient(worker, tenant, "token.worker")));
            }
            clients = Map.copyOf(scoped);
        }

        private void boot(SSLContext listener, SSLContext trust, char[] password) {
            StoreBindingDTO businessStore = binding(StoreOwner.BUSINESS);
            var access = stores.get(StoreOwner.BUSINESS);
            ExpenseBusinessWorkerFixture.prepareDisposableFixtureTables(access.setup(), businessStore.runtimeRole(), businessStore.observerRole());
            if (manifest.suite().isS07()) {
                bootGovernedStart(password);
                return;
            }
            relay = ExpenseAcceptanceFaultRelay.open(manifest, listener, trust, this);
            configs.put(ChildRole.AGENT, childConfig(ChildRole.AGENT, password));
            configs.put(ChildRole.PROCESS, childConfig(ChildRole.PROCESS, password));
            startChild(ChildRole.AGENT);
            for (var tenant : tenants()) {
                String actual = one(db(StoreOwner.AGENT), """
                        SELECT content_sha256 FROM tianji_agent.agent_definition_version
                        WHERE tenant_id=? AND definition_key='expenseAnalysis' AND version=1
                        """, (rs, row) -> rs.getString(1), tenant.tenantId()).orElseThrow();
                assertEquals(AGENT_SHA, actual);
            }
            startChild(ChildRole.PROCESS);
            String manifestHash = null;
            for (var tenant : tenants()) {
                var published = clients(tenant).owner().publish(release.command());
                assertEquals(published, clients(tenant).owner().get(new GetProcessReleaseQuery(RELEASE)));
                assertEquals(RELEASE, published.releaseVersion());
                assertEquals(release.bpmnSha(), published.models().stream().filter(model -> model.type() == ProcessModelTypeEnum.BPMN)
                        .findFirst().orElseThrow().contentSha256());
                assertEquals(release.caseSha(), published.models().stream().filter(model -> model.type() == ProcessModelTypeEnum.CMMN)
                        .findFirst().orElseThrow().contentSha256());
                if (manifestHash != null) assertEquals(manifestHash, published.manifestSha256());
                manifestHash = published.manifestSha256();
            }
        }

        private void bootGovernedStart(char[] password) {
            boolean approved = manifest.suite() == Suite.S07_APPROVED_START;
            var process = new TreeMap<>(childConfig(ChildRole.PROCESS, password));
            if (approved) {
                for (int index = 0; index < tenants().size(); index++) {
                    var tenant = tenants().get(index);
                    S07ApprovalFixture.processProperties(process, index, tenant,
                            setting(tenant, "s07.approval-service-subject"));
                }
            }
            configs.put(ChildRole.PROCESS, Map.copyOf(process));
            startChild(ChildRole.PROCESS);
            var agent = new TreeMap<>(childConfig(ChildRole.AGENT, password));
            var businessStore = binding(StoreOwner.BUSINESS);
            String fixture = "stellar.tianji.agent.s07-fixture.";
            put(agent, fixture + "process-base", manifest.endpoints().process());
            put(agent, fixture + "observer-url", businessStore.jdbcUrl());
            put(agent, fixture + "observer-role", businessStore.observerRole());
            put(agent, fixture + "observer-password", value(privateProperties(businessStore.credentialFile()), "observer.password"));
            put(agent, fixture + "bpmn-sha256", S07GovernedStartFixture.BPMN_SHA);
            put(agent, fixture + "dmn-sha256", S07GovernedStartFixture.DMN_SHA);
            if (approved) {
                var processStore = binding(StoreOwner.PROCESS);
                put(agent, fixture + "approval-observer-url", processStore.jdbcUrl());
                put(agent, fixture + "approval-observer-role", processStore.observerRole());
                put(agent, fixture + "approval-observer-password",
                        value(privateProperties(processStore.credentialFile()), "observer.password"));
            }
            for (int index = 0; index < tenants().size(); index++) {
                var tenant = tenants().get(index);
                var publisher = clients(tenant).owner();
                var published = publisher.publish(S07GovernedStartFixture.release());
                S07GovernedStartFixture.verifyRelease(published);
                assertEquals(published, publisher.get(new GetProcessReleaseQuery(S07GovernedStartFixture.RELEASE)));
                var businessKey = S07GovernedStartFixture.businessKey(tenant.tenantId());
                ExpenseBusinessWorkerFixture.seedDisposableObject(stores.get(StoreOwner.BUSINESS).setup(),
                        tenantSourceId(tenant), tenant.tenantId(), businessKey, S07GovernedStartFixture.BUSINESS_VERSION, 0);
                String prefix = fixture + "tenant-" + (index + 1) + ".";
                put(agent, prefix + "source-id", tenantSourceId(tenant));
                put(agent, prefix + "business-key", businessKey);
                put(agent, prefix + "process-resource", tenant.processResource());
                put(agent, prefix + "manifest-sha256", published.manifestSha256());
                if (approved) {
                    String subject = setting(tenant, "s07.approval-service-subject");
                    var approval = S07ApprovalFixture.registration(tenant, subject);
                    var source = publisher.publish(S07ApprovalFixture.release(tenant));
                    S07ApprovalFixture.verify(source, approval.definitionSha256());
                    assertEquals(source, publisher.get(new GetProcessReleaseQuery(S07ApprovalFixture.RELEASE)));
                    put(agent, prefix + "approval-policy-binding-sha256", approval.fingerprint());
                    put(agent, prefix + "approval-model-sha256", approval.definitionSha256());
                    for (String name : List.of("approval-service-subject", "approval-service-app", "approval-service-biz",
                            "approval-service-source-app", "approval-service-environment", "approval-registration")) {
                        put(agent, prefix + name, setting(tenant, "s07." + name));
                    }
                }
                for (String name : List.of("service-subject", "service-app", "service-biz", "service-source-app",
                        "service-environment", "start-registration", "decision-registration", "start-authority-resource",
                        "decision-authority-resource")) {
                    put(agent, prefix + name, setting(tenant, "s07." + name));
                }
            }
            configs.put(ChildRole.AGENT, Map.copyOf(agent));
            startChild(ChildRole.AGENT);
        }

        void verifyGovernedStart() {
            require(manifest.suite() == Suite.S07_GOVERNED_START, "Exact S07 suite");
            for (var tenant : tenants()) {
                long id = tenant.tenantId();
                String ownerIssuer = issuerSha(tenant);
                String businessKey = S07GovernedStartFixture.businessKey(id);
                var client = new HttpAgentClient(URI.create(manifest.endpoints().agent()), http,
                        token(tenant, "token.agent-owner"), NETWORK, 0, Duration.ZERO);
                var first = startGovernedRun(client, businessKey, "s07-first");
                assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, first.status());
                var firstResult = client.getResult(new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(first.runId()));
                String instance = governedInstance(firstResult);
                var action = governedAction(tenant, businessKey, first.runId(), instance);
                var replay = startGovernedRun(client, businessKey, "s07-replay");
                assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, replay.status());
                assertNotEquals(first.runId(), replay.runId());
                var replayResult = client.getResult(new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(replay.runId()));
                assertEquals(instance, governedInstance(replayResult));
                assertEquals(1L, count(db(StoreOwner.AGENT), """
                        SELECT count(*) FROM tianji_agent.agent_tool_result_access a
                        JOIN tianji_agent.agent_budget_reservation b
                          ON (b.tenant_id,b.run_id,b.reservation_id)=(a.tenant_id,a.run_id,a.reservation_id)
                        WHERE a.tenant_id=? AND a.run_id=? AND a.intent_id=? AND a.owner_type='USER'
                          AND a.owner_issuer=? AND a.owner_id=? AND a.source_action_revision=?
                          AND a.authorization_audit->>'boundary'='RECONCILE' AND a.authorization_audit->>'decision'='ALLOW'
                          AND b.state='SETTLED'
                        """, id, replay.runId(), action.intentId(), ownerIssuer, tenant.ownerSubject(), action.revision()));
                assertEquals(0L, count(db(StoreOwner.AGENT), """
                        SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?
                        """, id, replay.runId()));
                var foreign = tenants().stream().filter(other -> other.tenantId() != id).findFirst().orElseThrow();
                var blocked = startGovernedRun(client, S07GovernedStartFixture.businessKey(foreign.tenantId()), "s07-foreign-object");
                assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.FAILED, blocked.status());
                assertEquals(0L, count(db(StoreOwner.AGENT), """
                        SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?
                        """, id, blocked.runId()));
                assertEquals(1L, count(db(StoreOwner.PROCESS), """
                        SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                        WHERE tenant_id=? AND command_type='START' AND release_key=?
                        """, id, S07GovernedStartFixture.RELEASE));
                assertEquals(action, governedAction(tenant, businessKey, first.runId(), instance));
            }
            assertEquals((long) tenants().size(), count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                    WHERE command_type='START' AND release_key=?
                    """, S07GovernedStartFixture.RELEASE));
            assertEquals((long) tenants().size(), count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.act_ru_task WHERE task_def_key_='review'
                    """));
        }

        void verifyApprovedStart() {
            require(manifest.suite() == Suite.S07_APPROVED_START, "Exact S07 approved suite");
            for (var tenant : tenants()) {
                long id = tenant.tenantId();
                String issuer = issuerSha(tenant);
                String businessKey = S07GovernedStartFixture.businessKey(id);
                var users = clients(tenant);
                var agent = new HttpAgentClient(URI.create(manifest.endpoints().agent()), http,
                        token(tenant, "token.agent-owner"), NETWORK, 0, Duration.ZERO);
                var waiting = startGovernedRun(agent, businessKey, "s07-approval-preparation");
                assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, waiting.status());
                var result = agent.getResult(new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(waiting.runId()));
                assertEquals("s07-awaiting-approval", result.output().get("text"));
                var action = one(db(StoreOwner.AGENT), """
                        SELECT a.intent_id,a.request_sha256,a.business_version,a.tool_sha256
                        FROM tianji_agent.agent_tool_action_intent a JOIN tianji_agent.agent_tool_invocation i
                          ON i.tenant_id=a.tenant_id AND i.intent_id=a.intent_id AND i.run_id=a.origin_run_id
                        WHERE a.tenant_id=? AND a.owner_type='USER' AND a.owner_issuer=? AND a.owner_id=?
                          AND a.business_key=? AND a.origin_run_id=? AND a.risk='APPROVAL_REQUIRED'
                          AND a.state='AWAITING_APPROVAL' AND a.dispatch_epoch=0 AND a.approval_receipt_id IS NULL
                          AND i.state='AWAITING_APPROVAL' AND i.dispatch_epoch=0
                        """, (rs, row) -> new ApprovalActionVO(rs.getObject("intent_id", UUID.class), rs.getString("request_sha256"),
                        rs.getString("business_version"), rs.getString("tool_sha256")), id, issuer,
                        tenant.ownerSubject(), businessKey, waiting.runId()).orElseThrow();
                assertEquals(Long.toString(S07GovernedStartFixture.BUSINESS_VERSION), action.businessVersion());
                assertEquals(0L, count(db(StoreOwner.PROCESS), """
                        SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                        WHERE tenant_id=? AND command_type='START' AND release_key=?
                        """, id, S07GovernedStartFixture.RELEASE));
                String intent = action.intentId().toString();
                var source = S07ApprovalFixture.sourceStart(businessKey, intent, action.requestSha256(),
                        action.businessVersion(), action.toolSha256());
                var scope = users.owner().start(source);
                var task = harness.await(Phase.STANDARD_PATH, () -> task(users.first(), HumanTaskScopeTypeEnum.PROCESS,
                        scope.instanceId(), S07ApprovalFixture.TASK, S07ApprovalFixture.RELEASE));
                assertEquals(HumanTaskKindEnum.APPROVAL, task.kind());
                rejectForeign(() -> users.owner().claimHumanTask(new ClaimHumanTaskCommand(
                        "s07-requester-claim", task.taskId(), task.version())));
                users.first().claimHumanTask(new ClaimHumanTaskCommand("s07-reviewer-claim", task.taskId(), task.version()));
                var claimed = users.first().getHumanTask(new GetHumanTaskQuery(task.taskId()));
                assertTrue(claimed.assignedToMe());
                var approved = users.first().completeHumanTask(new CompleteHumanTaskCommand(
                        "s07-reviewer-approval", task.taskId(), claimed.version(), Map.of("approved", true)));
                assertNotNull(approved.approvalReceiptId());
                assertEquals(Boolean.TRUE, approved.approved());
                assertEquals(1L, count(db(StoreOwner.PROCESS), """
                        SELECT count(*) FROM tianji_process.tianji_human_task_receipt
                        WHERE tenant_id=? AND subject_type='USER' AND subject_issuer=? AND subject_id=?
                          AND requester_type='USER' AND requester_issuer=? AND requester_id=?
                          AND command_type='TASK_COMPLETE' AND task_kind='APPROVAL' AND approved IS TRUE
                          AND approval_receipt_id=? AND task_id=? AND process_instance_id=?
                          AND start_command_id=? AND start_request_sha256=? AND authorized_role_id=?
                        """, id, issuer, tenant.reviewerSubjects().getFirst(), issuer, tenant.ownerSubject(),
                        approved.approvalReceiptId(), task.taskId(), scope.instanceId(), intent,
                        OrchestrationRequestDigests.start(source), Long.parseLong(tenant.roleIds().getFirst())));
                var dispatched = startGovernedRun(agent, businessKey, "s07-approved-dispatch");
                assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, dispatched.status());
                String instance = governedInstance(agent.getResult(
                        new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(dispatched.runId())));
                assertEquals(action.intentId(), governedAction(tenant, businessKey, waiting.runId(), dispatched.runId(), instance).intentId());
                assertEquals(1L, count(db(StoreOwner.AGENT), """
                        SELECT count(*) FROM tianji_agent.agent_tool_action_intent
                        WHERE tenant_id=? AND intent_id=? AND state='APPLIED' AND risk='APPROVAL_REQUIRED'
                          AND approval_receipt_id=? AND approval_approved IS TRUE AND approval_consumed_at IS NOT NULL
                          AND approval_proof IS NOT NULL AND dispatch_epoch=1
                        """, id, action.intentId(), approved.approvalReceiptId()));
                assertEquals(1L, count(db(StoreOwner.AGENT), """
                        SELECT count(*) FROM tianji_agent.agent_tool_invocation
                        WHERE tenant_id=? AND intent_id=? AND dispatch_epoch>0
                        """, id, action.intentId()));
            }
            assertEquals((long) tenants().size(), count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                    WHERE command_type='START' AND release_key=?
                    """, S07GovernedStartFixture.RELEASE));
        }

        private record ApprovalActionVO(UUID intentId, String requestSha256, String businessVersion, String toolSha256) { }

        private top.egon.cola.platform.tianji.contract.agent.AgentRunDTO startGovernedRun(HttpAgentClient client,
                String businessKey, String commandId) {
            var input = new top.egon.cola.platform.tianji.contract.agent.AgentInputDTO(null, List.of(),
                    Map.of("requestedAmount", S07GovernedStartFixture.AMOUNT));
            var started = client.start(new top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand(
                    S07GovernedStartFixture.AGENT, 1, null, commandId, businessKey, commandId, input, null));
            var query = new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(started.runId());
            return harness.await(Phase.STANDARD_PATH, () -> {
                var current = client.get(query);
                return switch (current.status()) {
                    case COMPLETED, FAILED, CANCELLED, TIMED_OUT, SUSPENDED -> Optional.of(current);
                    default -> Optional.empty();
                };
            });
        }

        private static String governedInstance(top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult result) {
            assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED, result.status());
            String output = assertInstanceOf(String.class, result.output().get("text"));
            require(output.startsWith("s07-started:"), "S07 exact terminal fixture marker");
            return new GetProcessInstanceQuery(output.substring("s07-started:".length())).instanceId();
        }

        private GovernedActionVO governedAction(TenantBindingDTO tenant, String businessKey, String runId, String instance) {
            return governedAction(tenant, businessKey, runId, runId, instance);
        }

        private GovernedActionVO governedAction(TenantBindingDTO tenant, String businessKey, String originRun, String dispatchRun, String instance) {
            long id = tenant.tenantId();
            String issuer = issuerSha(tenant);
            String executor = setting(tenant, "s07.service-subject");
            var action = one(db(StoreOwner.AGENT), """
                    SELECT a.intent_id,a.revision FROM tianji_agent.agent_tool_action_intent a
                    JOIN tianji_agent.agent_tool_invocation i
                      ON (a.tenant_id,a.dispatch_run_id,a.dispatch_invocation_id)=(i.tenant_id,i.run_id,i.invocation_id)
                    JOIN tianji_agent.agent_budget_reservation b
                      ON (i.tenant_id,i.run_id,i.reservation_id)=(b.tenant_id,b.run_id,b.reservation_id)
                    WHERE a.tenant_id=? AND a.owner_type='USER' AND a.owner_issuer=? AND a.owner_id=?
                      AND a.business_key=? AND a.business_version=? AND a.origin_run_id=? AND a.dispatch_run_id=?
                      AND a.executor_issuer=? AND a.executor_id=? AND a.dispatch_epoch=1 AND a.state='APPLIED'
                      AND i.state='SUCCEEDED' AND i.business_receipt_id=a.business_receipt_id AND i.intent_id=a.intent_id
                      AND i.authorization_audit->>'boundary'='DISPATCH' AND i.authorization_audit->>'decision'='ALLOW'
                      AND b.state='SETTLED'
                    """, (rs, row) -> new GovernedActionVO(rs.getObject("intent_id", UUID.class), rs.getLong("revision")),
                    id, issuer, tenant.ownerSubject(), businessKey, Long.toString(S07GovernedStartFixture.BUSINESS_VERSION),
                    originRun, dispatchRun, issuer, executor).orElseThrow();
            String commandId = action.intentId().toString();
            var command = new StartProcessCommand(S07GovernedStartFixture.BPMN, S07GovernedStartFixture.RELEASE, businessKey,
                    commandId, commandId, Map.of("amount", S07GovernedStartFixture.AMOUNT));
            assertEquals(1L, count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                    WHERE tenant_id=? AND subject_type='SERVICE' AND subject_issuer=? AND subject_id=?
                      AND command_type='START' AND command_id=? AND request_sha256=? AND release_key=?
                      AND correlation_id=? AND state='APPLIED' AND resource_id=?
                    """, id, issuer, executor, commandId, OrchestrationRequestDigests.start(command),
                    S07GovernedStartFixture.RELEASE, commandId, instance));
            assertEquals(1L, count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.act_ru_task
                    WHERE tenant_id_=? AND proc_inst_id_=? AND task_def_key_='review'
                    """, Long.toString(id), instance));
            return action;
        }

        private record GovernedActionVO(UUID intentId, long revision) { }

        List<TenantBindingDTO> tenants() { return manifest.tenants(); }
        ExpenseAcceptanceHarness harness() { return harness; }
        ExpenseAcceptanceManifestDTO receiveLinkManifest() {
            require(manifest.suite().observesReceiveLinks(), "Exact receive-link fixture suite");
            return manifest;
        }
        ExpenseAcceptanceManifestDTO metricsManifest() {
            require(manifest.suite().collectsMetrics(), "Exact metrics fixture suite");
            return manifest;
        }
        Duration relayBudget() { return Duration.ofSeconds(Math.min(manifest.limits().recoverySeconds(), 60)); }
        Duration linkedHoldBudget() { return Duration.ofSeconds(manifest.limits().linkedHoldSeconds()); }
        ExpenseAcceptanceFaultRelay relay() { return Objects.requireNonNull(relay); }
        ChildIdentityDTO child(ChildRole role) { return Objects.requireNonNull(children.get(role)); }

        private void startChild(ChildRole role) {
            Path privateConfig = harness.writeChildConfiguration(role, configs.get(role));
            children.put(role, harness.startChild(role, privateConfig, ignored -> ready(role)));
        }

        ChildIdentityDTO replaceChild(ChildRole role) {
            Path privateConfig = harness.writeChildConfiguration(role, configs.get(role));
            var replacement = harness.replaceChild(child(role), privateConfig, ignored -> ready(role));
            children.put(role, replacement);
            return replacement;
        }

        RunDTO start(TenantBindingDTO tenant, Scenario scenario, String window, long amount) {
            require(tenants().contains(tenant) && window.matches("[a-z][a-z0-9_-]{0,47}"), "Expense run scope");
            String label = scenario.name().toLowerCase(Locale.ROOT) + "-" + window;
            UUID operation = UUID.nameUUIDFromBytes(("expense-fixture-v1:" + label).getBytes(StandardCharsets.UTF_8));
            String businessKey = "expense-" + label;
            String commandId = "start-" + label;
            String correlation = "corr-" + label;
            Map<String, Object> input = Map.of("operationId", operation.toString(), "amount", amount);
            var command = new StartProcessCommand(BPMN, RELEASE, businessKey, commandId, correlation, input);
            var owner = clients(tenant).owner();
            var instance = owner.start(command);
            assertEquals(instance, owner.start(command));
            reject(409, () -> owner.start(new StartProcessCommand(BPMN, RELEASE, businessKey, commandId, correlation,
                    Map.of("operationId", operation.toString(), "amount", amount + 1))));
            require(ownedStart(tenant, instance, commandId), "Exact owned START receipt");
            var run = new RunDTO(tenant, scenario, window, operation, instance);
            assertNull(runs.putIfAbsent(instance.instanceId(), run));
            String object = "object-" + operation;
            ExpenseBusinessWorkerFixture.seedDisposableObject(stores.get(StoreOwner.BUSINESS).setup(), tenantSourceId(tenant),
                    tenant.tenantId(), object, 0, 0);
            var store = stores.get(StoreOwner.BUSINESS);
            var worker = new ExpenseBusinessWorkerFixture(store.runtime(), store.observer().getDataSource(), tenantSourceId(tenant), tenant.tenantId());
            worker.registerExpectedOperation(instance, operation, object, 0, 0, amount);
            assertNull(business.putIfAbsent(instance.instanceId(), worker));
            deadlines.put(instance.instanceId(), harness.beginPhase(switch (scenario) {
                case MANUAL_CASE -> Phase.MANUAL_CASE;
                case RECOVERY -> Phase.RECOVERY;
                default -> Phase.STANDARD_PATH;
            }));
            await(run, () -> decision(run).map(actual -> {
                assertEquals(amount > 1000 ? "largeExpense" : "smallExpense", actual.rule());
                assertEquals(amount > 1000 ? "exception" : "standard", actual.lane());
                return actual;
            }));
            return run;
        }

        RemoteWorkerPoller startWorker(RunDTO run, boolean recovery) {
            require(runs.get(run.instance().instanceId()) == run && !pollers.containsKey(run.instance().instanceId()),
                    "Owned Worker lifecycle");
            var poller = fixture(run).openPoller(workerClient(run), recovery);
            pollers.put(run.instance().instanceId(), poller);
            poller.start();
            return poller;
        }

        void stopWorker(RunDTO run) {
            var poller = pollers.remove(run.instance().instanceId());
            if (poller != null) poller.stop();
        }

        HttpProcessClient workerClient(RunDTO run) { return clients(run.tenant()).worker(); }
        EffectsVO effects(RunDTO run) { return fixture(run).readEffects(run.operationId()); }
        Optional<CompleteWorkerTaskCommand> droppedComplete(RunDTO run) { return Optional.ofNullable(dropped.get(run.instance().instanceId())); }

        void assertNormalEnd(RunDTO run) {
            awaitEnd(run, "normalEnd");
            assertAgent(run, "COMPLETED", "APPLIED");
            assertEffects(run, 1, 0);
            if (manifest.suite() == Suite.S12_OWNER_OBSERVATION) reconcile(run);
        }

        private void awaitEnd(RunDTO run, String endActivity) {
            assertEquals(endActivity, await(run, () -> endActivity(run)));
            assertEquals(ProcessInstanceStatusEnum.COMPLETED,
                    clients(run.tenant()).owner().get(new GetProcessInstanceQuery(run.instance().instanceId())).status());
        }

        private Optional<String> endActivity(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT end_act_id_ FROM tianji_process.act_hi_procinst
                    WHERE proc_inst_id_=? AND tenant_id_=? AND business_key_=? AND end_time_ IS NOT NULL
                    """, (rs, row) -> rs.getString(1), run.instance().instanceId(), tenantText(run), run.instance().businessKey());
        }

        private void assertAgent(RunDTO run, String status, String inboxState) {
            var dispatch = await(run, () -> dispatch(run).filter(value -> value.runId() != null));
            assertEquals(status, await(run, () -> agentStatus(run, dispatch.runId()).filter(Driver::terminal)));
            var inbox = await(run, () -> inbox(run).filter(value -> !value.state().equals("PENDING")));
            assertEquals(inboxState, inbox.state());
            assertEquals("SUCCEEDED", await(run, () -> outbox(run, inbox.eventId()).filter(value -> settledOutbox(value.status()))).status());
            if ("APPLIED".equals(inboxState)) {
                assertEquals(1L, count(db(StoreOwner.PROCESS), """
                        SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                        WHERE tenant_id=? AND command_type='AGENT_RESULT' AND command_id=? AND state='APPLIED'
                        """, run.tenant().tenantId(), inbox.eventId()));
                assertEquals(1L, nativeActivityCount(run, "waitAgentResult"));
            }
        }

        private void assertEffects(RunDTO run, long notice, long undo) {
            var effect = effects(run);
            assertEquals(1, effect.originalCount());
            assertEquals(notice, effect.noticeCount());
            assertEquals(undo, effect.undoCount());
            assertEquals(undo == 1 ? 0 : decisionAmount(run), effect.reservedAmount());
            assertEquals(undo == 1 ? 2 : 1, effect.objectVersion());
            assertEquals(effect.originalReceiptId(), workerReceipt(run, "reserveExpense"));
            assertEquals(notice == 1 ? effect.noticeReceiptId() : effect.undoReceiptId(),
                    workerReceipt(run, notice == 1 ? "notifyExpense" : "undoReservation"));
        }

        private long decisionAmount(RunDTO run) {
            return switch (run.scenario()) {
                case NORMAL -> 100;
                case AGENT_FAILURE -> 700;
                case MANUAL_CASE -> 1500;
                case RECOVERY -> 200;
            };
        }

        private void armManualBeforeWorker(RunDTO run) {
            relay().arm(new FaultWindowDTO(FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART, run.tenant().tenantId(),
                    sourceId(run), run.instance().instanceId(), null, null, null, null, Duration.ofSeconds(60)));
        }

        private Map<WitnessKind, WitnessDTO> driveManualCase(RunDTO run) {
            var deferred = relay().awaitObserved(FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART, Duration.ofSeconds(50));
            assertEquals(run.instance().instanceId(), deferred.instanceId());
            assertEquals(0L, nativeActivityCount(run, "waitAgentTimeout"), "Real terminal delivery was deferred before native Timer won");
            await(run, () -> nativeActivityCount(run, "waitAgentTimeout") == 1 ? Optional.of(Boolean.TRUE) : Optional.empty());
            relay().release(FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART);
            var users = clients(run.tenant());
            var task = await(run, () -> task(users.first(), HumanTaskScopeTypeEnum.PROCESS, run.instance().instanceId(), "reviewExpense"));
            assertEquals("exception", task.lane());
            assertEquals(task.taskId(), task(users.second(), HumanTaskScopeTypeEnum.PROCESS, run.instance().instanceId(), "reviewExpense")
                    .orElseThrow().taskId());
            rejectForeign(() -> clients(otherTenant(run.tenant())).first().getHumanTask(new GetHumanTaskQuery(task.taskId())));
            rejectForeign(() -> users.owner().claimHumanTask(new ClaimHumanTaskCommand(
                    "owner-claim-" + run.window(), task.taskId(), task.version())));
            var winner = raceClaim(users, task, run.window());
            var claimed = winner.getHumanTask(new GetHumanTaskQuery(task.taskId()));
            assertTrue(claimed.assignedToMe());
            var complete = new CompleteHumanTaskCommand("review-done-" + run.window(), task.taskId(), claimed.version(),
                    Map.of("finding", "fixture reviewed"));
            var receipt = winner.completeHumanTask(complete);
            assertEquals(receipt, winner.completeHumanTask(complete));
            reject(409, () -> winner.completeHumanTask(new CompleteHumanTaskCommand(
                    complete.commandId(), task.taskId(), claimed.version(), Map.of("finding", "different finding"))));

            var origin = await(run, () -> caseOrigin(run));
            assertNotNull(origin.sourceOccurrence());
            rejectForeign(() -> clients(otherTenant(run.tenant())).first().get(new CaseInstanceQuery(origin.id())));
            assertEquals(CaseInstanceStatusEnum.ACTIVE, winner.get(new CaseInstanceQuery(origin.id())).status());
            completeCaseTask(run, winner, origin.id(), "primaryTask");
            var supplemental = await(run, () -> caseItem(winner, origin.id(), "supplementalTask")
                    .filter(item -> item.state() == CasePlanItemStateEnum.ENABLED));
            if (run.tenant().tenantId() == tenants().getFirst().tenantId()) {
                completeCaseTask(run, winner, origin.id(), "supplementalTask");
            } else {
                var active = startCaseItem(winner, origin.id(), supplemental, "supplement-start-" + run.window());
                assertTrue(active.availableActions().contains(CasePlanItemActionEnum.TERMINATE));
                var terminate = new ActOnPlanItemCommand("supplement-stop-" + run.window(), origin.id(),
                        active.planItemInstanceId(), active.version(), CasePlanItemActionEnum.TERMINATE);
                var terminal = winner.actOnPlanItem(terminate);
                assertEquals(CasePlanItemStateEnum.TERMINATED, terminal.resultingState());
                assertEquals(terminal, winner.actOnPlanItem(terminate));
            }
            var finished = await(run, () -> Optional.of(winner.get(new CaseInstanceQuery(origin.id())))
                    .filter(value -> value.status() != CaseInstanceStatusEnum.ACTIVE));
            assertEquals(CaseInstanceStatusEnum.COMPLETED, finished.status());
            assertEquals(1L, count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.act_cmmn_hi_mil_inst m
                    JOIN tianji_process.tianji_case_start_provenance p ON p.case_instance_id=m.case_inst_id_
                    WHERE p.tenant_id=? AND p.parent_process_instance_id=? AND m.case_inst_id_=? AND m.element_id_='milestone'
                    """, run.tenant().tenantId(), run.instance().instanceId(), origin.id()));
            String completion = await(run, () -> one(db(StoreOwner.PROCESS), """
                    SELECT i.completion_command_id FROM tianji_process.tianji_case_completion_inbox i
                    JOIN tianji_process.tianji_process_command_receipt c
                      ON c.tenant_id=i.tenant_id AND c.command_id=i.completion_command_id
                     AND c.command_type='CASE_COMPLETION_SERVICE' AND c.state='APPLIED'
                    WHERE i.tenant_id=? AND i.case_instance_id=? AND i.source_id=? AND i.state='APPLIED'
                      AND c.subject_type=i.service_type AND c.subject_issuer=i.service_issuer AND c.subject_id=i.service_subject
                    """, (rs, row) -> rs.getString(1), run.tenant().tenantId(), origin.id(), origin.sourceId()));
            assertEquals(1L, nativeActivityCount(run, "waitCaseResult"));
            awaitEnd(run, "compensatedEnd");
            assertAgent(run, "COMPLETED", "IGNORED_LATE");
            assertEffects(run, 0, 1);
            return Map.of(WitnessKind.HUMAN_TASK, proved(task.taskId()), WitnessKind.CASE_INSTANCE, proved(origin.id()),
                    WitnessKind.CASE_COMPLETION, proved(completion));
        }

        private static HttpProcessClient raceClaim(TenantClients users, HumanTaskDTO task, String window) {
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = threads.submit(() -> claimIfAllowed(users.first(), task, "claim-a-" + window, ready, release));
                var second = threads.submit(() -> claimIfAllowed(users.second(), task, "claim-b-" + window, ready, release));
                require(ready.await(2, TimeUnit.SECONDS), "Both real reviewers ready");
                release.countDown();
                boolean a = first.get(10, TimeUnit.SECONDS);
                boolean b = second.get(10, TimeUnit.SECONDS);
                require(a != b, "Exactly one real reviewer claimed the same version");
                return a ? users.first() : users.second();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Expense claim race interrupted");
            } catch (ExecutionException | TimeoutException failure) {
                throw new AssertionError("Expense claim race did not settle");
            } finally {
                release.countDown();
            }
        }

        private static boolean claimIfAllowed(HttpProcessClient actor, HumanTaskDTO task, String command,
                                              CountDownLatch ready, CountDownLatch release) throws InterruptedException {
            ready.countDown();
            require(release.await(2, TimeUnit.SECONDS), "Expense reviewer race barrier");
            try {
                return task.taskId().equals(actor.claimHumanTask(new ClaimHumanTaskCommand(command, task.taskId(), task.version())).taskId());
            } catch (ProcessClientException denied) {
                if (denied.httpStatus() == 404 || denied.httpStatus() == 409) return false;
                throw denied;
            }
        }

        private void completeCaseTask(RunDTO run, HttpProcessClient client, String caseId, String definitionKey) {
            var item = await(run, () -> caseItem(client, caseId, definitionKey).filter(value -> value.state() == CasePlanItemStateEnum.ENABLED));
            startCaseItem(client, caseId, item, definitionKey + "-start-" + run.window());
            var task = await(run, () -> task(client, HumanTaskScopeTypeEnum.CASE, caseId, definitionKey));
            client.claimHumanTask(new ClaimHumanTaskCommand(definitionKey + "-claim-" + run.window(), task.taskId(), task.version()));
            var owned = client.getHumanTask(new GetHumanTaskQuery(task.taskId()));
            assertTrue(owned.assignedToMe());
            client.completeHumanTask(new CompleteHumanTaskCommand(definitionKey + "-done-" + run.window(),
                    task.taskId(), owned.version(), Map.of()));
        }

        private static CasePlanItemDTO startCaseItem(HttpProcessClient client, String caseId, CasePlanItemDTO item, String command) {
            assertTrue(item.availableActions().contains(CasePlanItemActionEnum.START));
            var start = new ActOnPlanItemCommand(command, caseId, item.planItemInstanceId(), item.version(), CasePlanItemActionEnum.START);
            var started = client.actOnPlanItem(start);
            assertEquals(CasePlanItemStateEnum.ACTIVE, started.resultingState());
            return caseItem(client, caseId, item.planItemDefinitionKey()).orElseThrow();
        }

        private static Optional<CasePlanItemDTO> caseItem(HttpProcessClient client, String caseId, String key) {
            return client.get(new CaseInstanceQuery(caseId)).planItems().stream()
                    .filter(value -> value.planItemDefinitionKey().equals(key)).findFirst();
        }

        private static Optional<HumanTaskDTO> task(HttpProcessClient client, HumanTaskScopeTypeEnum scope,
                                                  String scopeId, String definitionKey) {
            return task(client, scope, scopeId, definitionKey, RELEASE);
        }

        private static Optional<HumanTaskDTO> task(HttpProcessClient client, HumanTaskScopeTypeEnum scope,
                                                  String scopeId, String definitionKey, String release) {
            var page = client.humanTasks(new ListHumanTasksQuery(scope, HumanTaskSelectionEnum.CANDIDATE, release, scopeId,
                    definitionKey, null, 16));
            require(page.nextAfterTaskId() == null && page.items().size() <= 1, "Unique scoped expense task");
            return page.items().stream().findFirst();
        }

        void assertDisjoint(RunDTO first, RunDTO second) {
            assertNotEquals(first.tenant().tenantId(), second.tenant().tenantId());
            assertEquals(first.operationId(), second.operationId());
            assertEquals(first.instance().businessKey(), second.instance().businessKey());
            assertEquals(first.instance().correlationId(), second.instance().correlationId());
            assertNotEquals(first.instance().instanceId(), second.instance().instanceId());
            assertNotEquals(dispatch(first).orElseThrow().runId(), dispatch(second).orElseThrow().runId());
            assertNotEquals(effects(first).originalReceiptId(), effects(second).originalReceiptId());
            reject(404, () -> clients(first.tenant()).owner().get(new GetProcessInstanceQuery(second.instance().instanceId())));
            reject(404, () -> clients(second.tenant()).owner().get(new GetProcessInstanceQuery(first.instance().instanceId())));
        }

        void verifyOwnerObservations(RunDTO first, RunDTO second) {
            require(manifest.suite() == Suite.S12_OWNER_OBSERVATION, "Exact S12 owner observation fixture suite");
            var processUntil = observerTime(StoreOwner.PROCESS);
            var agentUntil = observerTime(StoreOwner.AGENT);
            for (var own : List.of(first, second)) {
                var foreign = own == first ? second : first;
                var tenant = own.tenant();
                var instance = own.instance();
                var dispatch = dispatch(own).orElseThrow();
                var ownedAgent = ownedAgentRun(own, dispatch.runId()).orElseThrow();
                require("COMPLETED".equals(ownedAgent.status()), "Linked original AS owner run completed");
                var process = new HttpProcessClient(URI.create(manifest.endpoints().process()), http,
                        () -> setting(tenant, S12OwnerObservationEvidence.PROCESS_TOKEN), NETWORK);
                var agent = new HttpAgentClient(URI.create(manifest.endpoints().agent()), http,
                        () -> setting(tenant, S12OwnerObservationEvidence.AGENT_TOKEN), NETWORK, 0, Duration.ZERO);
                var starts = process.listByCorrelation(new ListProcessInstancesByCorrelationQuery(instance.correlationId(),
                        processUntil.minus(Duration.ofHours(1)), processUntil, null));
                var outcome = process.getAgentOutcomes(new GetProcessAgentOutcomesQuery(instance.instanceId(), null));
                var agents = agent.listByCorrelation(new ListAgentRunsByCorrelationQuery(instance.correlationId(),
                        agentUntil.minus(Duration.ofHours(1)), agentUntil, null));
                S12OwnerObservationEvidence.verify(instance, dispatch.runId(), ownedAgent.createdAt(), AGENT, starts, outcome, agents);
                reject(404, () -> process.getAgentOutcomes(new GetProcessAgentOutcomesQuery(foreign.instance().instanceId(), null)));
            }
        }

        void verifyModelUsage(RunDTO run) {
            require(manifest.suite() == Suite.S12_MODEL_USAGE, "Exact S12 model usage fixture suite");
            String runId = dispatch(run).orElseThrow().runId();
            var facts = db(StoreOwner.AGENT).query("""
                    SELECT r.run_id,b.reservation_id,b.lease_epoch,b.kind,b.state AS budget_state,
                           b.actual_input_tokens AS budget_input,b.actual_output_tokens AS budget_output,b.actual_cost AS budget_cost,
                           b.settled_at,u.lease_epoch AS usage_epoch,u.model_route_ref,u.model_name,u.price_version_ref,u.currency,
                           u.input_rate_per_million,u.output_rate_per_million,u.state AS usage_state,u.actual_input_tokens,
                           u.actual_output_tokens,u.actual_cost,u.safe_reason,u.final_sha256,u.created_at,u.measured_at
                    FROM tianji_agent.agent_run r
                    JOIN tianji_agent.agent_process_dispatch d ON (d.tenant_id,d.run_id)=(r.tenant_id,r.run_id)
                    JOIN tianji_agent.agent_budget_reservation b ON (b.tenant_id,b.run_id)=(r.tenant_id,r.run_id)
                    LEFT JOIN tianji_agent.agent_model_usage u
                      ON (u.tenant_id,u.run_id,u.reservation_id)=(b.tenant_id,b.run_id,b.reservation_id)
                    WHERE r.tenant_id=? AND r.run_id=? AND r.subject_type='SERVICE' AND r.subject_issuer=? AND r.subject_id=?
                      AND r.definition_key='expenseAnalysis' AND r.definition_version=1 AND r.status='COMPLETED' AND r.correlation_id=?
                      AND d.subject_issuer=r.subject_issuer AND d.subject_id=r.subject_id
                      AND d.source_id=? AND d.scope_type='BPMN' AND d.scope_id=?
                    LIMIT 2
                    """, S12ModelUsageEvidence::read, run.tenant().tenantId(), runId, issuerSha(run.tenant()),
                    setting(run.tenant(), "identity.bridge-subject"), run.instance().correlationId(),
                    sourceId(run), run.instance().instanceId());
            S12ModelUsageEvidence.verify(run.tenant().tenantId(), runId, facts);
        }

        private Instant observerTime(StoreOwner owner) {
            return Objects.requireNonNull(db(owner).queryForObject("SELECT pg_catalog.clock_timestamp()", OffsetDateTime.class))
                    .toInstant();
        }

        ExpensePathResultDTO result(RunDTO run, Map<WitnessKind, WitnessDTO> additional) {
            reconcile(run);
            var dispatch = dispatch(run).orElseThrow();
            var decision = decision(run).orElseThrow();
            var effects = effects(run);
            var original = completedLease(run, "reserveExpense").orElseThrow();
            var witnesses = new EnumMap<WitnessKind, WitnessDTO>(WitnessKind.class);
            for (var kind : WitnessKind.values()) witnesses.put(kind, new WitnessDTO(WitnessState.NOT_REACHED, null));
            witnesses.put(WitnessKind.START_COMMAND, proved("start-" + run.scenario().name().toLowerCase(Locale.ROOT) + "-" + run.window()));
            witnesses.put(WitnessKind.PROCESS_INSTANCE, proved(run.instance().instanceId()));
            witnesses.put(WitnessKind.DECISION_EXECUTION, proved(decision.id()));
            witnesses.put(WitnessKind.DECISION_RULE, proved(decision.rule()));
            witnesses.put(WitnessKind.AGENT_DISPATCH, proved(dispatch.occurrenceId()));
            witnesses.put(WitnessKind.AGENT_RUN, proved(dispatch.runId()));
            witnesses.put(WitnessKind.AGENT_STATUS, proved(agentStatus(run, dispatch.runId()).orElseThrow()));
            String end = endActivity(run).orElseThrow();
            assertEquals(run.scenario() == Scenario.NORMAL || run.scenario() == Scenario.RECOVERY
                    ? "normalEnd" : "compensatedEnd", end);
            witnesses.put(WitnessKind.PROCESS_END, proved(end));
            if (run.scenario() != Scenario.MANUAL_CASE) {
                assertEquals(0L, count(db(StoreOwner.PROCESS), """
                        SELECT count(*) FROM tianji_process.act_hi_actinst
                        WHERE proc_inst_id_=? AND tenant_id_=? AND act_id_='reviewExpense'
                        """, run.instance().instanceId(), tenantText(run)));
                assertTrue(caseOrigin(run).isEmpty());
            }
            witnesses.put(WitnessKind.WORKER_JOB, proved(original.jobId()));
            witnesses.put(WitnessKind.WORKER_OCCURRENCE, proved(original.occurrenceId()));
            witnesses.put(WitnessKind.ORIGINAL_RECEIPT, proved(effects.originalReceiptId()));
            if (effects.noticeReceiptId() != null) witnesses.put(WitnessKind.NOTICE_RECEIPT, proved(effects.noticeReceiptId()));
            if (effects.undoReceiptId() != null) witnesses.put(WitnessKind.UNDO_RECEIPT, proved(effects.undoReceiptId()));
            additional.forEach((kind, witness) -> {
                require(witnesses.get(kind).state() == WitnessState.NOT_REACHED && witness.state() == WitnessState.PROVED,
                        "Distinct positive acceptance witness");
                witnesses.put(kind, witness);
            });
            return new ExpensePathResultDTO(run.tenant().tenantId(), run.scenario(), run.operationId().toString(),
                    run.instance().businessKey(), run.instance().correlationId(), true, witnesses);
        }

        private void reconcile(RunDTO run) {
            var dispatch = await(run, () -> dispatch(run).filter(value -> "CLOSED".equals(value.state())));
            require(dispatch.runId() != null, "Closed Agent dispatch has an owned run");
            assertEquals("CLOSED", await(run, () -> one(db(StoreOwner.AGENT), """
                    SELECT state FROM tianji_agent.agent_process_dispatch
                    WHERE tenant_id=? AND source_id=? AND scope_type='BPMN' AND scope_id=? AND occurrence_id=? AND run_id=?
                    """, (rs, row) -> rs.getString(1), run.tenant().tenantId(), sourceId(run), run.instance().instanceId(),
                    dispatch.occurrenceId(), dispatch.runId()).filter("CLOSED"::equals)));
            assertEquals(0L, count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.tianji_worker_lease_receipt
                    WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND state='LEASED'
                    """, run.tenant().tenantId(), run.instance().instanceId()));
        }

        Optional<DispatchDTO> dispatch(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT occurrence_id,run_id,state,source_id FROM tianji_process.tianji_process_agent_dispatch
                    WHERE tenant_id=? AND source_id=? AND scope_type='BPMN' AND scope_id=?
                      AND subject_issuer=? AND subject_id=? AND release_key=? AND process_model_sha256=?
                      AND business_key=? AND correlation_id=? AND element_id='agentStart'
                    """, (rs, row) -> new DispatchDTO(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                    run.tenant().tenantId(), sourceId(run), run.instance().instanceId(), issuerSha(run.tenant()),
                    setting(run.tenant(), "identity.bridge-subject"), RELEASE, release.bpmnSha(),
                    run.instance().businessKey(), run.instance().correlationId());
        }

        Optional<InboxDTO> inbox(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT i.event_id,i.event_sha256,i.state FROM tianji_process.tianji_process_agent_result_inbox i
                    JOIN tianji_process.tianji_process_agent_dispatch d
                      ON d.tenant_id=i.tenant_id AND d.source_id=i.source_id AND d.scope_type=i.scope_type
                     AND d.scope_id=i.scope_id AND d.occurrence_id=i.occurrence_id
                    WHERE i.tenant_id=? AND i.source_id=? AND i.scope_type='BPMN' AND i.scope_id=?
                      AND i.owner_issuer=d.subject_issuer AND i.owner_subject=d.subject_id AND (d.run_id IS NULL OR i.run_id=d.run_id)
                      AND i.producer_issuer=? AND i.producer_subject=? AND i.producer_id=?
                    """, (rs, row) -> new InboxDTO(rs.getString(1), rs.getString(2), rs.getString(3)), run.tenant().tenantId(),
                    sourceId(run), run.instance().instanceId(), issuerSha(run.tenant()), setting(run.tenant(), "identity.result-subject"), PRODUCER);
        }

        Optional<OutboxDTO> outbox(RunDTO run, String eventId) {
            return one(db(StoreOwner.AGENT), """
                    SELECT message_id,status,attempt_count FROM tianji_agent.egon_cola_outbox_message
                    WHERE message_id=? AND destination=? AND channel='tianji-agent-result'
                    """, (rs, row) -> new OutboxDTO(rs.getString(1), rs.getString(2), rs.getInt(3)), eventId, destination(run.tenant()));
        }

        String committedReceiveLinkCarrier(RunDTO run) {
            require(manifest.suite().observesReceiveLinks(), "Exact receive-link fixture suite");
            String eventId = inbox(run).filter(row -> "APPLIED".equals(row.state())).orElseThrow().eventId();
            return one(db(StoreOwner.AGENT), """
                    SELECT trace_id FROM tianji_agent.egon_cola_outbox_message
                    WHERE message_id=? AND destination=? AND channel='tianji-agent-result' AND status='SUCCEEDED'
                    """, (rs, row) -> rs.getString(1), eventId, destination(run.tenant())).orElseThrow();
        }

        Optional<NativeWaitDTO> nativeWait(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT e.id_,j.id_,j.duedate_ FROM tianji_process.act_ru_event_subscr e
                    JOIN tianji_process.act_ru_timer_job j ON j.process_instance_id_=e.proc_inst_id_ AND j.tenant_id_=e.tenant_id_
                    WHERE e.proc_inst_id_=? AND e.tenant_id_=? AND e.event_type_='message'
                      AND e.event_name_='TianjiAgentFinishedV1' AND e.activity_id_='waitAgentResult'
                      AND j.element_id_='waitAgentTimeout'
                    """, (rs, row) -> new NativeWaitDTO(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
                    run.instance().instanceId(), tenantText(run));
        }

        Optional<LeaseDTO> lease(RunDTO run) {
            return leaseRows(run, "reserveExpense").stream().findFirst();
        }

        List<LeaseDTO> leaseRows(RunDTO run, String element) {
            return db(StoreOwner.PROCESS).query("""
                    SELECT job_id,occurrence_id,idempotency_key,leased_at,lease_expires_at,state,token_sha256
                    FROM tianji_process.tianji_worker_lease_receipt
                    WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND release_key=? AND model_sha256=? AND element_id=?
                      AND subject_issuer=? AND subject_id=?
                    ORDER BY leased_at DESC LIMIT 33
                    """, (rs, row) -> new LeaseDTO(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getObject(4, java.time.OffsetDateTime.class).toInstant(), rs.getObject(5, java.time.OffsetDateTime.class).toInstant(),
                    rs.getString(6), rs.getString(7)), run.tenant().tenantId(), run.instance().instanceId(), RELEASE, release.bpmnSha(), element,
                    issuerSha(run.tenant()), setting(run.tenant(), "identity.worker-subject"));
        }

        Optional<String> workerJob(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT id_ FROM tianji_process.act_ru_external_job
                    WHERE process_instance_id_=? AND tenant_id_=? AND element_id_='reserveExpense' AND handler_cfg_='reserveBusiness:1'
                    """, (rs, row) -> rs.getString(1), run.instance().instanceId(), tenantText(run));
        }

        private Optional<LeaseDTO> completedLease(RunDTO run, String element) {
            var rows = leaseRows(run, element).stream().filter(row -> row.state().equals("COMPLETED")).toList();
            require(rows.size() <= 1, "Unique completed expense Worker receipt");
            return rows.stream().findFirst();
        }

        private String workerReceipt(RunDTO run, String element) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT business_receipt_id FROM tianji_process.tianji_worker_lease_receipt
                    WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND release_key=? AND model_sha256=?
                      AND element_id=? AND state='COMPLETED' AND subject_issuer=? AND subject_id=?
                    """, (rs, row) -> rs.getString(1), run.tenant().tenantId(), run.instance().instanceId(), RELEASE, release.bpmnSha(), element,
                    issuerSha(run.tenant()), setting(run.tenant(), "identity.worker-subject"))
                    .orElseThrow();
        }

        private Optional<String> agentStatus(RunDTO run, String agentRunId) {
            return ownedAgentRun(run, agentRunId).map(OwnedAgentRunDTO::status);
        }

        private Optional<OwnedAgentRunDTO> ownedAgentRun(RunDTO run, String agentRunId) {
            return one(db(StoreOwner.AGENT), """
                    SELECT r.status,r.created_at FROM tianji_agent.agent_run r JOIN tianji_agent.agent_process_dispatch d
                      ON d.tenant_id=r.tenant_id AND d.run_id=r.run_id
                    WHERE r.tenant_id=? AND r.run_id=? AND r.subject_type='SERVICE' AND r.subject_id=? AND r.subject_issuer=?
                      AND r.definition_key='expenseAnalysis' AND r.definition_version=1 AND r.correlation_id=?
                      AND d.source_id=? AND d.scope_type='BPMN' AND d.scope_id=?
                    """, (rs, row) -> new OwnedAgentRunDTO(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant()),
                    run.tenant().tenantId(), agentRunId, setting(run.tenant(), "identity.bridge-subject"), issuerSha(run.tenant()), run.instance().correlationId(),
                    sourceId(run), run.instance().instanceId());
        }

        private Optional<CaseOriginDTO> caseOrigin(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT case_instance_id,source_id,source_occurrence_id FROM tianji_process.tianji_case_start_provenance
                    WHERE tenant_id=? AND parent_process_instance_id=? AND owner_type='USER' AND owner_issuer=? AND owner_subject=?
                      AND source_element_id='caseSource' AND start_kind='LINKED_BPMN' AND release_key=?
                      AND parent_model_sha256=? AND case_model_sha256=? AND case_definition_key='expenseException'
                    """, (rs, row) -> new CaseOriginDTO(rs.getString(1), rs.getString(2), rs.getString(3)), run.tenant().tenantId(),
                    run.instance().instanceId(), issuerSha(run.tenant()), run.tenant().ownerSubject(), RELEASE, release.bpmnSha(), release.caseSha());
        }

        private long nativeActivityCount(RunDTO run, String activity) {
            return count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.act_hi_actinst
                    WHERE proc_inst_id_=? AND tenant_id_=? AND act_id_=? AND end_time_ IS NOT NULL AND delete_reason_ IS NULL
                    """, run.instance().instanceId(), tenantText(run), activity);
        }

        private Optional<DecisionDTO> decision(RunDTO run) {
            return one(db(StoreOwner.PROCESS), """
                    SELECT h.id_,h.execution_json_ FROM tianji_process.act_dmn_hi_decision_execution h
                    JOIN tianji_process.tianji_process_release_definition d ON d.engine_definition_id=h.decision_definition_id_
                    WHERE h.instance_id_=? AND h.tenant_id_=? AND h.activity_id_='classifyExpense' AND h.failed_=false
                      AND h.end_time_ IS NOT NULL AND d.tenant_id=? AND d.release_key=? AND d.model_type='DMN' AND d.definition_key='expenseRouting'
                    """, (rs, row) -> decodeDecision(rs.getString(1), rs.getString(2)), run.instance().instanceId(),
                    tenantText(run), run.tenant().tenantId(), RELEASE);
        }

        private boolean ownedStart(TenantBindingDTO tenant, ProcessInstanceDTO instance, String commandId) {
            return count(db(StoreOwner.PROCESS), """
                    SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                    WHERE tenant_id=? AND subject_type='USER' AND subject_issuer=? AND subject_id=?
                      AND command_type='START' AND command_id=? AND state='APPLIED' AND resource_id=? AND release_key=? AND correlation_id=?
                    """, tenant.tenantId(), issuerSha(tenant), tenant.ownerSubject(), commandId, instance.instanceId(),
                    RELEASE, instance.correlationId()) == 1L;
        }

        private static DecisionDTO decodeDecision(String id, String raw) {
            require(raw != null && raw.length() <= 65_536, "Bounded exact native DMN audit");
            var audit = JSON.readTree(raw);
            var matched = new ArrayList<Integer>();
            audit.path("ruleExecutions").properties().forEach(entry -> {
                var value = entry.getValue();
                if (value.path("valid").booleanValue()) matched.add(value.path("ruleNumber").intValue());
            });
            var results = audit.path("decisionResult");
            require(matched.size() == 1 && results.isArray() && results.size() == 1, "One matched native DMN rule");
            String lane = results.get(0).path("reviewLane").stringValue();
            String rule = switch (matched.getFirst()) {
                case 1 -> "smallExpense";
                case 2 -> "largeExpense";
                default -> throw new AssertionError("Native expense DMN rule outside the fixed table");
            };
            require(Set.of("standard", "exception").contains(lane), "Native expense lane result");
            return new DecisionDTO(id, rule, lane);
        }

        @Override
        public boolean owned(FaultWindowDTO window, ObservedExchangeDTO request) {
            var run = runs.get(window.instanceId());
            if (run == null || run.tenant().tenantId() != window.tenantId() || !sourceId(run).equals(window.sourceId())
                    || !Objects.equals(window.occurrenceId(), request.occurrenceId())) return false;
            if (request.direction() == ExpenseAcceptanceFaultRelay.Direction.WORKER) {
                return leaseRows(run, "reserveExpense").stream().anyMatch(row ->
                        Objects.equals(row.jobId(), request.jobId()) && row.occurrenceId().equals(window.occurrenceId()));
            }
            return dispatch(run).filter(row -> row.occurrenceId().equals(window.occurrenceId())
                    && row.sourceId().equals(window.sourceId())).isPresent();
        }

        @Override
        public boolean businessCommitted(FaultWindowDTO window, ObservedExchangeDTO request, String token, String receiptId) {
            if (!owned(window, request)) return false;
            var run = runs.get(window.instanceId());
            var committed = effects(run);
            if (committed.originalCount() != 1 || !Objects.equals(committed.originalReceiptId(), receiptId)
                    || leaseRows(run, "reserveExpense").stream().noneMatch(row -> row.jobId().equals(request.jobId())
                    && row.occurrenceId().equals(window.occurrenceId()) && "LEASED".equals(row.state()) && row.tokenSha256().equals(WorkerLeaseStore.tokenHash(token)))) {
                return false;
            }
            var old = new CompleteWorkerTaskCommand(request.jobId(), window.occurrenceId(), token, receiptId, Map.of());
            var previous = dropped.putIfAbsent(window.instanceId(), old);
            return previous == null || previous.equals(old);
        }

        @Override
        public boolean agentCommitted(FaultWindowDTO window, ObservedExchangeDTO request, String runId) {
            return owned(window, request) && agentStatus(runs.get(window.instanceId()), runId).isPresent();
        }

        @Override
        public boolean inboxCommitted(FaultWindowDTO window, ObservedExchangeDTO request) {
            if (!owned(window, request)) return false;
            boolean exact = inbox(runs.get(window.instanceId())).filter(value ->
                    value.eventId().equals(request.eventId()) && value.fingerprint().equals(request.fingerprint())).isPresent();
            if (exact) outboxDropAgent.putIfAbsent(window.instanceId(), child(ChildRole.AGENT));
            return exact;
        }

        @Override
        public boolean replacementReady(FaultWindowDTO window, ObservedExchangeDTO request) {
            var old = outboxDropAgent.get(window.instanceId());
            var now = children.get(ChildRole.AGENT);
            return owned(window, request) && old != null && now != null && old.pid() != now.pid()
                    && old.startedAt().isBefore(now.startedAt());
        }

        private boolean ready(ChildRole role) {
            try {
                for (var tenant : tenants()) {
                    if (role == ChildRole.AGENT) {
                        new HttpAgentClient(URI.create(manifest.endpoints().agent()), http, token(tenant, "token.agent-owner"), NETWORK,
                                0, Duration.ZERO).list(new ListAgentRunsQuery(null, 1));
                    } else {
                        try {
                            clients(tenant).owner().get(new GetProcessReleaseQuery(RELEASE));
                        } catch (ProcessClientException absent) {
                            if (absent.httpStatus() != 404) throw absent;
                        }
                    }
                }
                return true;
            } catch (AgentClientException | ProcessClientException pending) {
                return false;
            }
        }

        private HttpProcessClient processClient(URI uri, TenantBindingDTO tenant, String key) {
            return new HttpProcessClient(uri, http, token(tenant, key), NETWORK);
        }

        private Supplier<String> token(TenantBindingDTO tenant, String key) {
            return () -> {
                String bearer = value(privateProperties(tenant.credentialFile()), key);
                if (logs != null) logs.secret(S12ChildLogPrivacy.Credential.HTTP_BEARER, bearer);
                return bearer;
            };
        }

        private TenantClients clients(TenantBindingDTO tenant) { return Objects.requireNonNull(clients.get(tenant.tenantId())); }
        private ExpenseBusinessWorkerFixture fixture(RunDTO run) { return Objects.requireNonNull(business.get(run.instance().instanceId())); }
        private JdbcTemplate db(StoreOwner owner) { return stores.get(owner).observer(); }
        private StoreBindingDTO binding(StoreOwner owner) {
            return manifest.stores().stream().filter(store -> store.owner() == owner).findFirst().orElseThrow();
        }
        private String setting(TenantBindingDTO tenant, String key) { return value(tenantSettings.get(tenant.tenantId()), key); }
        String sourceId(RunDTO run) { return tenantSourceId(run.tenant()); }
        private TenantBindingDTO otherTenant(TenantBindingDTO tenant) {
            return tenants().stream().filter(other -> other.tenantId() != tenant.tenantId()).findFirst().orElseThrow();
        }

        <T> T await(RunDTO run, Supplier<Optional<T>> witness) {
            return harness.await(Objects.requireNonNull(deadlines.get(run.instance().instanceId())), witness);
        }

        private static boolean terminal(String state) { return Set.of("COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT").contains(state); }
        private static boolean settledOutbox(String state) { return Set.of("SUCCEEDED", "DEAD").contains(state); }

        void publish(List<ExpensePathResultDTO> paths) {
            close();
            harness.publishEvidence(harness.evidence(true, paths));
        }

        void publishFailure(List<ExpensePathResultDTO> paths) {
            close();
            harness.publishEvidence(harness.evidence(false, paths));
        }

        @Override
        public void close() {
            boolean failed = false;
            for (var poller : List.copyOf(pollers.values())) {
                try { poller.stop(); } catch (RuntimeException failure) { failed = true; }
            }
            pollers.clear();
            for (var worker : List.copyOf(business.values())) {
                try { worker.close(); } catch (RuntimeException failure) { failed = true; }
            }
            business.clear();
            try { harness.close(); } catch (RuntimeException failure) { failed = true; }
            if (relay != null) {
                try { relay.close(); } catch (RuntimeException failure) { failed = true; }
                relay = null;
            }
            http.close();
            dropped.clear();
            if (failed) throw new IllegalStateException("Expense fixture could not stop an owned resource");
        }

        private Map<String, String> childConfig(ChildRole role, char[] password) {
            Map<String, String> config = new TreeMap<>();
            String roleName = role.name().toLowerCase(Locale.ROOT);
            for (var tenant : tenants()) {
                // Operator supplies only existing IAM/registration keys for the isolated scope.
                tenantSettings.get(tenant.tenantId()).forEach((key, raw) -> {
                    String qualified = (String) key;
                    String prefix = roleName + ".config.";
                    if (!qualified.startsWith(prefix)) return;
                    String name = qualified.substring(prefix.length());
                    require(name.startsWith("egon.cola.platform.tianquan.") || name.startsWith("spring.security.oauth2.")
                            || role == ChildRole.AGENT && (name.startsWith("stellar.tianji.agent.execution-grants.")
                                    || name.startsWith("stellar.tianji.agent.execution-authorization.")), "Scoped IAM configuration key");
                    put(config, name, (String) raw);
                });
            }
            StoreBindingDTO store = binding(role == ChildRole.AGENT ? StoreOwner.AGENT : StoreOwner.PROCESS);
            Properties dbCredentials = privateProperties(store.credentialFile());
            URI endpoint = URI.create(role == ChildRole.AGENT ? manifest.endpoints().agent() : manifest.endpoints().process());
            String keyStore = role == ChildRole.AGENT ? manifest.tls().agentKeyStore() : manifest.tls().processKeyStore();
            put(config, "server.address", endpoint.getHost().replace("[", "").replace("]", ""));
            put(config, "server.port", Integer.toString(endpoint.getPort()));
            put(config, "server.ssl.enabled", "true");
            put(config, "server.ssl.key-store", Path.of(keyStore).toUri().toASCIIString());
            put(config, "server.ssl.key-store-type", "PKCS12");
            put(config, "server.ssl.key-store-password", new String(password));
            put(config, "spring.flyway.enabled", "false");
            put(config, "spring.sql.init.mode", "never");
            put(config, "spring.datasource.url", store.jdbcUrl());
            put(config, "spring.datasource.username", store.runtimeRole());
            String runtimePassword = value(dbCredentials, "runtime.password");
            if (logs != null) logs.secret(role == ChildRole.AGENT
                    ? S12ChildLogPrivacy.Credential.AGENT_DB : S12ChildLogPrivacy.Credential.PROCESS_DB, runtimePassword);
            put(config, "spring.datasource.password", runtimePassword);
            put(config, "spring.datasource.hikari.schema", store.schema());
            if (!manifest.suite().collectsMetrics() && !manifest.suite().isS07()
                    && manifest.suite() != Suite.S12_MODEL_USAGE) {
                put(config, "management.otlp.tracing.endpoint", manifest.endpoints().collector());
                put(config, "management.tracing.sampling.probability", "1.0");
            }
            put(config, "egon.cola.component.transactional-outbox.enabled", "true");
            put(config, "egon.cola.component.transactional-outbox.storage.type", "JDBC");
            put(config, "egon.cola.component.transactional-outbox.storage.jdbc.schema", store.schema());
            put(config, "egon.cola.component.transactional-outbox.storage.validate-schema", "true");
            put(config, "egon.cola.component.transactional-outbox.polling.enabled", Boolean.toString(role == ChildRole.AGENT));
            if (role == ChildRole.AGENT) agentConfig(config);
            else processConfig(config);
            if (manifest.suite().observesReceiveLinks()) {
                if (role == ChildRole.AGENT) {
                    put(config, "stellar.tianji.agent.terminal-events.span-links-enabled", "true");
                    put(config, "stellar.tianji.agent.result-delivery.cross-process-span-links-enabled", "true");
                } else {
                    put(config, "stellar.tianji.process.agent-results.receive-span-links-enabled", "true");
                }
            } else if (manifest.suite().collectsMetrics()) {
                String flag = role == ChildRole.AGENT
                        ? "stellar.tianji.agent.run-observation.initial-claim-metrics-enabled"
                        : "stellar.tianji.process.agent-results.lag-metrics-enabled";
                put(config, flag, "true");
            }
            return Map.copyOf(config);
        }

        private void agentConfig(Map<String, String> config) {
            String prefix = "stellar.tianji.agent.";
            if (manifest.suite().isS07()) {
                for (String flag : List.of("storage", "api", "runtime", "dispatch", "reconciliation", "execution-grants",
                        "execution-authorization")) put(config, prefix + flag + ".enabled", "true");
                put(config, "egon.cola.component.agent-flow.enabled", "true");
                put(config, "egon.cola.component.agent-flow-registry.enabled", "false");
                return;
            }
            for (String flag : List.of("storage", "api", "runtime", "dispatch", "reconciliation", "execution-grants",
                    "execution-authorization", "linked", "service-execution", "terminal-events", "result-delivery")) {
                put(config, prefix + flag + ".enabled", "true");
            }
            put(config, "egon.cola.component.agent-flow.enabled", "true");
            put(config, "egon.cola.component.agent-flow-registry.enabled", "false");
            put(config, prefix + "linked.submission-enabled", "true");
            put(config, prefix + "terminal-events.producer-id", PRODUCER);
            if (manifest.suite() == Suite.S12_MODEL_USAGE) {
                put(config, prefix + "storage.model-usage-enabled", "true");
                put(config, prefix + "storage.model-usage-cumulative-route-refs[0]", S12ModelUsageEvidence.ROUTE);
            }
            for (int index = 0; index < tenants().size(); index++) {
                var tenant = tenants().get(index);
                String bridge = setting(tenant, "identity.bridge-subject");
                var linked = prefix + "linked.bindings[" + index + "].";
                put(config, linked + "issuer", tenant.issuer());
                put(config, linked + "tenant-id", Long.toString(tenant.tenantId()));
                put(config, linked + "client-id", bridge);
                put(config, linked + "app-id", tenant.applicationId());
                put(config, linked + "business-code", setting(tenant, "identity.business-code"));
                put(config, linked + "application-code", setting(tenant, "identity.application-code"));
                put(config, linked + "environment", setting(tenant, "identity.environment"));
                put(config, linked + "source-id", tenantSourceId(tenant));
                put(config, linked + "scope-types[0]", "BPMN");
                put(config, linked + "definitions[0].key", AGENT);
                put(config, linked + "definitions[0].version", "1");
                put(config, linked + "definitions[0].content-sha256", AGENT_SHA);
                var service = prefix + "service-execution.sources[" + index + "].";
                put(config, service + "issuer", tenant.issuer());
                put(config, service + "tenant-id", Long.toString(tenant.tenantId()));
                put(config, service + "client-id", bridge);
                put(config, service + "resource-uri", tenant.agentResource());
                put(config, prefix + "terminal-events.destinations[" + tenantSourceId(tenant) + "]", destination(tenant));
                var delivery = prefix + "result-delivery.destinations[" + index + "].";
                put(config, delivery + "id", destination(tenant));
                put(config, delivery + "tenant-id", Long.toString(tenant.tenantId()));
                put(config, delivery + "base-uri", manifest.suite() == Suite.RECEIVE_LINK || manifest.suite().collectsMetrics()
                        ? manifest.endpoints().process() : manifest.endpoints().resultRelay());
                put(config, delivery + "resource-uri", tenant.processResource());
                put(config, delivery + "issuer", tenant.issuer());
                put(config, delivery + "registration-id", setting(tenant, "registration.result"));
                put(config, delivery + "owners[0].issuer", tenant.issuer());
                put(config, delivery + "owners[0].subject-id", bridge);
                put(config, delivery + "owners[0].source-id", tenantSourceId(tenant));
            }
        }

        private void processConfig(Map<String, String> config) {
            String prefix = "stellar.tianji.process.";
            if (manifest.suite().isS07()) {
                put(config, prefix + "api.enabled", "true");
                put(config, prefix + "decision-tasks.enabled", "true");
                if (manifest.suite() == Suite.S07_APPROVED_START) {
                    put(config, prefix + "human-tasks.enabled", "true");
                    put(config, prefix + "human-tasks.publish-enabled", "true");
                    put(config, prefix + "approval-verification.enabled", "true");
                }
                put(config, "flowable.database-schema-update", "false");
                put(config, "flowable.database-schema", "tianji_process");
                put(config, "flowable.async-executor-activate", "false");
                return;
            }
            for (String flag : List.of("api", "decision-tasks", "worker", "human-tasks", "agent-bridge", "agent-results", "cases")) {
                put(config, prefix + flag + ".enabled", "true");
            }
            put(config, prefix + "human-tasks.publish-enabled", "true");
            put(config, prefix + "cases.new-starts-enabled", "true");
            if (manifest.suite() == Suite.S12_OWNER_OBSERVATION) {
                put(config, prefix + "agent-results.owner-outcomes-enabled", "true");
            }
            put(config, "flowable.database-schema-update", "false");
            put(config, "flowable.database-schema", "tianji_process");
            put(config, "flowable.async-executor-activate", "true");
            put(config, "flowable.process.async.executor.async-job-acquisition-enabled", "true");
            put(config, "flowable.process.async.executor.reset-expired-job-enabled", "true");
            int workerIndex = 0;
            for (int index = 0; index < tenants().size(); index++) {
                var tenant = tenants().get(index);
                String id = Long.toString(tenant.tenantId());
                String bridge = setting(tenant, "identity.bridge-subject");
                String worker = setting(tenant, "identity.worker-subject");
                for (String topic : List.of("reserveBusiness", "notifyExpense", "undoReservation", "agentStart")) {
                    boolean internal = "agentStart".equals(topic);
                    String r = prefix + "worker.registrations[" + workerIndex++ + "].";
                    put(config, r + "tenant-id", id);
                    put(config, r + "issuer", tenant.issuer());
                    put(config, r + "subject-id", internal ? bridge : worker);
                    put(config, r + "topic", topic);
                    put(config, r + "schema-version", "1");
                    put(config, r + "effect-safety", internal ? "EXTERNAL_IDEMPOTENT" : "TRANSACTIONAL_RECEIPT");
                    put(config, r + "internal-agent-dispatch", Boolean.toString(internal));
                    put(config, r + "max-concurrent", "1");
                    put(config, r + "lease-duration", internal ? "PT45S" : "PT15S");
                    put(config, r + "retry-delay", "PT1S");
                    put(config, r + (internal ? "inputs.requestedAmount" : "inputs.operationId"), internal ? "INTEGER" : "STRING");
                    put(config, r + (internal ? "input-sources.requestedAmount[0]" : "input-sources.operationId[0]"), internal ? "amount" : "operationId");
                    if (internal) {
                        put(config, r + "outputs.runId", "STRING");
                        put(config, r + "output-targets.runId[0]", "agentRunId");
                    }
                }
                String dispatch = prefix + "agent-bridge.bindings[" + index + "].";
                put(config, dispatch + "tenant-id", id);
                put(config, dispatch + "issuer", tenant.issuer());
                put(config, dispatch + "subject-id", bridge);
                put(config, dispatch + "source-id", tenantSourceId(tenant));
                put(config, dispatch + "topic", "agentStart");
                put(config, dispatch + "schema-version", "1");
                put(config, dispatch + "definition-key", AGENT);
                put(config, dispatch + "definition-version", "1");
                put(config, dispatch + "definition-content-sha256", AGENT_SHA);
                put(config, dispatch + "instruction", INSTRUCTION);
                put(config, dispatch + "http-timeout", "PT15S");
                put(config, dispatch + "base-uri", manifest.endpoints().linkedRelay());
                put(config, dispatch + "resource-uri", tenant.agentResource());
                put(config, dispatch + "registration-id", setting(tenant, "registration.bridge"));
                String result = prefix + "agent-results.producers[" + index + "].";
                put(config, result + "tenant-id", id);
                put(config, result + "issuer", tenant.issuer());
                put(config, result + "subject-id", setting(tenant, "identity.result-subject"));
                put(config, result + "producer-id", PRODUCER);
                put(config, result + "source-id", tenantSourceId(tenant));
                put(config, result + "owner-issuer", tenant.issuer());
                put(config, result + "owner-subject-id", bridge);
                String caze = prefix + "cases.sources[" + index + "].";
                put(config, caze + "tenant-id", id);
                put(config, caze + "source-id", "expense-case-" + id);
                put(config, caze + "release-key", RELEASE);
                put(config, caze + "parent-definition-key", BPMN);
                put(config, caze + "parent-model-sha256", release.bpmnSha());
                put(config, caze + "source-element-id", "caseSource");
                put(config, caze + "case-definition-key", CASE);
                put(config, caze + "case-model-sha256", release.caseSha());
                put(config, caze + "issuer", tenant.issuer());
                put(config, caze + "registration-id", setting(tenant, "registration.case"));
                put(config, caze + "service-subject", setting(tenant, "identity.case-subject"));
                put(config, caze + "app-id", tenant.applicationId());
                put(config, caze + "resource-uri", tenant.processResource());
            }
        }
    }

    private static final String AGENT_SHA = "00b1d06cf7d973a32b03f2fdba1a25612892f0afa16ab6381b945f786d92ed7c";
    private static final String FIXTURE_SHA = "e40621b736dcaf1685c4335acba70f000c9a9c01e443f743edc25640ce2b7cb7";

    private static RenderedRelease renderRelease(ExpenseAcceptanceManifestDTO manifest) throws IOException {
        Path root = Path.of(manifest.fixtureRoot());
        byte[] fixture = Files.readAllBytes(root.resolve("fixtures/expense-four-path-contract.json"));
        assertEquals(FIXTURE_SHA, sha(fixture));
        var modelLock = JSON.readTree(fixture).path("models");
        byte[] bpmn = Files.readAllBytes(root.resolve("processes/expense-review.bpmn20.xml"));
        byte[] dmn = Files.readAllBytes(root.resolve("decisions/expense-routing.dmn"));
        byte[] cmmn = Files.readAllBytes(root.resolve("cases/expense-exception.cmmn"));
        assertEquals(modelLock.path("BPMN").path("sha256").stringValue(), sha(bpmn));
        assertEquals(modelLock.path("DMN").path("sha256").stringValue(), sha(dmn));
        assertEquals(modelLock.path("CMMN").path("sha256").stringValue(), sha(cmmn));
        String process = roleReplace(roleReplace(new String(bpmn, StandardCharsets.UTF_8), "501,601", roles(manifest, 0)),
                "502,602", roles(manifest, 1));
        String caze = roleReplace(roleReplace(new String(cmmn, StandardCharsets.UTF_8), "502,602", roles(manifest, 1)),
                "503,603", roles(manifest, 2));
        var validator = new top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator();
        var p = validator.validateBpmn(bytes(process), BPMN);
        var d = validator.validateDmn(dmn, DMN);
        validator.validateCmmn(bytes(caze), CASE);
        var inputs = Map.of("operationId", top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.STRING,
                "amount", top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType.INTEGER);
        validator.validateDecisionBinding(p, inputs, d);
        validator.validateHumanTaskBindings(p, inputs, d);
        validator.validateWorkerBindings(p, inputs, d);
        var publish = new PublishProcessReleaseCommand(RELEASE, List.of(
                new ProcessModelResource(ProcessModelTypeEnum.BPMN, BPMN, process,
                        Map.of("operationId", ProcessValueTypeEnum.STRING, "amount", ProcessValueTypeEnum.INTEGER)),
                new ProcessModelResource(ProcessModelTypeEnum.DMN, DMN, new String(dmn, StandardCharsets.UTF_8), Map.of()),
                new ProcessModelResource(ProcessModelTypeEnum.CMMN, CASE, caze, Map.of())));
        return new RenderedRelease(publish, sha(bytes(process)), sha(bytes(caze)));
    }

    private static String roles(ExpenseAcceptanceManifestDTO manifest, int lane) {
        return manifest.tenants().stream().map(tenant -> tenant.roleIds().get(lane)).map(Long::parseLong)
                .distinct().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static String roleReplace(String xml, String pinned, String rendered) {
        String marker = "roleIds=\"" + pinned + "\"";
        require(xml.indexOf(marker) >= 0 && xml.indexOf(marker) == xml.lastIndexOf(marker), "Exact fixture role slot");
        return xml.replace(marker, "roleIds=\"" + rendered + "\"");
    }

    private static Map<StoreOwner, StoreAccess> sourceAccess(ExpenseAcceptanceManifestDTO manifest) {
        var result = new EnumMap<StoreOwner, StoreAccess>(StoreOwner.class);
        for (var source : manifest.stores()) {
            Properties credentials = privateProperties(source.credentialFile());
            require(credentials.stringPropertyNames().equals(Set.of("setup.password", "runtime.password", "observer.password")),
                    "Closed disposable PostgreSQL credentials");
            var observer = new JdbcTemplate(dataSource(source, source.observerRole(), value(credentials, "observer.password"), true));
            observer.setQueryTimeout(3);
            result.put(source.owner(), new StoreAccess(dataSource(source, source.setupRole(), value(credentials, "setup.password"), false),
                    dataSource(source, source.runtimeRole(), value(credentials, "runtime.password"), false), observer));
        }
        return Map.copyOf(result);
    }

    private static PGSimpleDataSource dataSource(StoreBindingDTO source, String user, String password, boolean readOnly) {
        var data = new PGSimpleDataSource();
        data.setUrl(source.jdbcUrl());
        data.setUser(user);
        data.setPassword(password);
        data.setCurrentSchema(source.schema());
        data.setConnectTimeout(3);
        data.setSocketTimeout(4);
        data.setReadOnly(readOnly);
        return data;
    }

    private static SourceWitnessDTO verifySources(ExpenseAcceptanceManifestDTO manifest, Map<StoreOwner, StoreAccess> sources) {
        Path checkout = Path.of(manifest.checkoutRoot());
        Path parent = checkout.getParent();
        var revisions = new RevisionLockDTO(revision(checkout), revision(parent.resolve("Stellar-Enclosure-Components")),
                revision(parent.resolve("tianquan-shoubing-service")), revision(parent.resolve("tianquan-jianshen-service")),
                revision(parent.resolve("tianshu-service")));
        var stores = new ArrayList<StoreWitnessDTO>();
        for (var locked : manifest.stores()) {
            var access = sources.get(locked.owner());
            verifyRole(access.setup(), locked, locked.setupRole());
            verifyRole(access.runtime(), locked, locked.runtimeRole());
            verifyRole(access.observer().getDataSource(), locked, locked.observerRole());
            var jdbc = access.observer();
            String version = jdbc.queryForObject("SELECT current_setting('server_version')", String.class);
            String vector = locked.owner() == StoreOwner.AGENT ? one(jdbc,
                    "SELECT extversion FROM pg_extension WHERE extname='vector'", (rs, row) -> rs.getString(1)).orElseThrow() : "NONE";
            String system = new JdbcTemplate(access.setup()).queryForObject("SELECT system_identifier::text FROM pg_control_system()", String.class);
            String history = history(jdbc, locked.owner(), manifest.suite());
            boolean empty = switch (locked.owner()) {
                case AGENT -> count(jdbc, "SELECT count(*) FROM tianji_agent.agent_run") == 0
                        && count(jdbc, "SELECT count(*) FROM tianji_agent.egon_cola_outbox_message") == 0
                        && (manifest.suite() != Suite.S12_MODEL_USAGE
                            || count(jdbc, "SELECT count(*) FROM tianji_agent.agent_model_usage") == 0);
                case PROCESS -> count(jdbc, "SELECT count(*) FROM tianji_process.tianji_process_command_receipt") == 0
                        && count(jdbc, "SELECT count(*) FROM tianji_process.act_ru_execution") == 0;
                case BUSINESS -> count(jdbc, "SELECT count(*) FROM tianji_worker.tianji_worker_execution_receipt") == 0
                        && count(jdbc, """
                                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                                WHERE n.nspname='compensation_fixture'
                                  AND c.relname IN ('business_object','original_receipt','compensation_receipt','expense_notice')
                                """) == 0;
            };
            assertEquals(0L, count(jdbc, """
                    SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname=? AND c.relkind IN ('r','p')
                      AND has_table_privilege(current_user,c.oid,'INSERT,UPDATE,DELETE,TRUNCATE')
                    """, locked.schema()));
            switch (locked.owner()) {
                case AGENT -> require(count(jdbc, """
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema='tianji_agent' AND table_name IN
                          ('agent_run_execution_grant','agent_run_service_execution_grant','agent_process_dispatch','egon_cola_outbox_message')
                        """) == 4, "Exact Agent runtime storage prerequisites");
                case PROCESS -> {
                    var verifier = new top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier(access.runtime(), locked.schema());
                    verifier.verifyCase();
                    verifier.verifyAgentResultInbox();
                }
                case BUSINESS -> {
                    var verifier = new top.egon.cola.platform.tianji.worker.WorkerExecutionSchemaVerifier(access.runtime(), locked.schema());
                    verifier.verify();
                    verifier.verifyIntentReadable();
                }
            }
            stores.add(new StoreWitnessDTO(locked.owner(), locked.jdbcUrl(), system, locked.schema(), version, vector, history,
                    locked.setupRole(), locked.runtimeRole(), locked.observerRole(), empty));
        }
        return new SourceWitnessDTO(revisions, stores);
    }

    private static void verifyRole(DataSource dataSource, StoreBindingDTO store, String role) {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(3);
        assertEquals(role, jdbc.queryForObject("SELECT current_user", String.class));
        assertEquals(store.schema(), jdbc.queryForObject("SELECT current_schema()", String.class));
        String database = store.jdbcUrl().substring(store.jdbcUrl().lastIndexOf('/') + 1);
        assertEquals(database, jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("""
                SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin'
                """, Boolean.class));
    }

    private static String history(JdbcTemplate jdbc, StoreOwner owner, Suite suite) {
        String table = switch (owner) {
            case AGENT -> "tianji_agent.flyway_schema_history";
            case PROCESS -> "tianji_process.tianji_process_schema_history";
            case BUSINESS -> "tianji_worker.tianji_worker_schema_history";
        };
        int count = switch (owner) {
            case AGENT -> suite == Suite.S12_MODEL_USAGE ? 15 : 14;
            case PROCESS -> 10;
            case BUSINESS -> 2;
        };
        var rows = jdbc.query("SELECT version,script,checksum,success,type FROM " + table + " WHERE version IS NOT NULL ORDER BY installed_rank",
                (rs, index) -> {
                    require(rs.getBoolean("success") && "SQL".equals(rs.getString("type"))
                            && Integer.toString(index + 1).equals(rs.getString("version"))
                            && rs.getObject("checksum") != null, "Exact owned migration prefix");
                    return rs.getString("version") + ":" + rs.getString("script") + ":" + rs.getInt("checksum");
                });
        assertEquals(count, rows.size());
        var fingerprint = new ArrayList<>(rows);
        if (owner == StoreOwner.AGENT) {
            fingerprint.addAll(outboxHistory(jdbc, "tianji_agent.tianji_agent_outbox_schema_history"));
        } else if (owner == StoreOwner.PROCESS) {
            fingerprint.addAll(outboxHistory(jdbc, "tianji_process.tianji_process_outbox_schema_history"));
            String flowable = one(jdbc, "SELECT value_ FROM tianji_process.act_ge_property WHERE name_='schema.version'",
                    (rs, row) -> rs.getString(1)).orElseThrow();
            require(Set.of("8.0.0", "8.0.0.0").contains(flowable), "Exact supported Flowable schema");
            fingerprint.add(flowable);
        }
        return sha(bytes(String.join("\n", fingerprint)));
    }

    private static List<String> outboxHistory(JdbcTemplate jdbc, String knownHistory) {
        var rows = jdbc.query("SELECT script,checksum,success,type FROM " + knownHistory + " WHERE version='1'",
                (rs, row) -> {
                    require(rs.getBoolean("success") && "SQL".equals(rs.getString("type")) && rs.getObject("checksum") != null
                            && "V1__create_transactional_outbox_schema.sql".equals(rs.getString("script")), "Exact owned Outbox migration");
                    return rs.getString("script") + ":" + rs.getInt("checksum");
                });
        require(rows.size() == 1, "Exact separate Outbox history");
        return rows;
    }

    private static String revision(Path root) {
        try {
            Path git = root.resolve(".git");
            String head = Files.readString(git.resolve("HEAD")).strip();
            String sha = head;
            if (head.startsWith("ref: ")) {
                String ref = head.substring(5);
                require(ref.matches("refs/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+") && !ref.contains(".."), "Exact repository revision ref");
                Path loose = git.resolve(ref);
                if (Files.isRegularFile(loose, LinkOption.NOFOLLOW_LINKS)) sha = Files.readString(loose).strip();
                else {
                    try (var packed = Files.lines(git.resolve("packed-refs"))) {
                        sha = packed.filter(line -> line.endsWith(" " + ref)).map(line -> line.substring(0, line.indexOf(' ')))
                                .findFirst().orElseThrow();
                    }
                }
            }
            require(sha.matches("[0-9a-f]{40}"), "Exact repository revision hash");
            return sha;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Expense acceptance repository lock could not be read");
        }
    }

    private static Properties privateProperties(String path) {
        try (var input = Files.newInputStream(Path.of(path), LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(65_537);
            require(content.length > 0 && content.length <= 65_536, "Bounded private fixture credential input");
            var properties = new Properties();
            properties.load(new java.io.StringReader(new String(content, StandardCharsets.UTF_8)));
            Arrays.fill(content, (byte) 0);
            return properties;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Expense acceptance private configuration could not be read");
        }
    }

    private static char[] tlsPassword(String path) throws IOException {
        byte[] raw;
        try (var input = Files.newInputStream(Path.of(path), LinkOption.NOFOLLOW_LINKS)) {
            raw = input.readNBytes(257);
        }
        try {
            String value = new String(raw, StandardCharsets.UTF_8);
            if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
            require(raw.length <= 256 && !value.isBlank() && value.equals(value.trim())
                    && value.chars().noneMatch(Character::isISOControl), "Exact private TLS password file");
            return value.toCharArray();
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static SSLContext ssl(String trustPath, String keyPath, char[] password) throws Exception {
        var trust = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(trustPath), LinkOption.NOFOLLOW_LINKS)) { trust.load(input, password); }
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        KeyManagerFactory keys = null;
        if (keyPath != null) {
            var key = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(keyPath), LinkOption.NOFOLLOW_LINKS)) { key.load(input, password); }
            keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(key, password);
        }
        var context = SSLContext.getInstance("TLS");
        context.init(keys == null ? null : keys.getKeyManagers(), tm.getTrustManagers(), null);
        return context;
    }

    private static <T> Optional<T> one(JdbcTemplate db, String sql, RowMapper<T> mapper, Object... args) {
        var rows = db.query(sql, mapper, args);
        require(rows.size() <= 1, "Expense acceptance witness was ambiguous");
        return rows.stream().findFirst();
    }

    private static long count(JdbcTemplate db, String sql, Object... args) {
        return Objects.requireNonNull(db.queryForObject(sql, Long.class, args));
    }

    private static WitnessDTO proved(String value) { return new WitnessDTO(WitnessState.PROVED, Objects.requireNonNull(value)); }
    private static String tenantSourceId(TenantBindingDTO tenant) { return "expense-process-" + tenant.tenantId(); }
    private static String destination(TenantBindingDTO tenant) { return "expense-result-" + tenant.tenantId(); }
    private static String tenantText(RunDTO run) { return Long.toString(run.tenant().tenantId()); }
    private static String issuerSha(TenantBindingDTO tenant) {
        return ProcessActor.verified(tenant.tenantId(), ProcessActor.SubjectType.USER, tenant.ownerSubject(), tenant.issuer()).issuerFingerprint();
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        require(value != null && !value.isBlank() && value.length() <= 8192 && value.chars().noneMatch(Character::isISOControl),
                "Required private expense configuration value");
        return value;
    }

    private static void put(Map<String, String> map, String key, String value) {
        String previous = map.putIfAbsent(key, value);
        require(previous == null || previous.equals(value), "Conflicting fixture configuration key");
    }

    private static void reject(int expected, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(ProcessClientException.class, operation);
        assertEquals(expected, failure.httpStatus());
    }

    private static void rejectForeign(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(ProcessClientException.class, operation);
        assertTrue(failure.httpStatus() == 403 || failure.httpStatus() == 404);
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException missing) {
            throw new IllegalStateException("Expense acceptance SHA-256 is unavailable");
        }
    }
}
