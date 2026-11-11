package top.egon.cola.platform.tianji.agent.server.acceptance;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only AS fault at the caller's completed HTTP response boundary. */
final class S11HistoricalStartLoss implements BeanPostProcessor {
    private static final String HTTP_BEAN = "tianjiGovernedProcessHttpClient";
    private static final String PREFIX = "tianji.expense.fixture.s11-cut.";
    private static final String S07 = "stellar.tianji.agent.s07-fixture.";
    private static final String DEFINITION = "s07GovernedStart";
    private static final String RELEASE = "s07-governed-release";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final URI start;
    private final String businessKey;
    private final CommitWitness witness;

    private S11HistoricalStartLoss(URI start, String businessKey, CommitWitness witness) {
        this.start = start;
        this.businessKey = businessKey;
        this.witness = witness;
    }

    static S11HistoricalStartLoss create(Properties settings, S07GovernedStartAgentFixtureConfiguration.FixturePlan plan) {
        var profile = plan.profiles().getFirst();
        var owner = profile.owner();
        var executor = TrustedAgentActor.verified(owner.tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                required(settings, S07 + "tenant-1.service-subject"), profile.issuer());
        URI origin = URI.create(required(settings, S07 + "process-base"));
        if (!"https".equals(origin.getScheme()) || !"127.0.0.1".equals(origin.getHost())
                || origin.getPort() < 1024 || origin.getPort() > 65535 || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null
                || !java.util.Set.of("", "/").contains(origin.getPath())) throw unavailable();
        var agent = observer(settings, "agent", "tianji_agent");
        var process = observer(settings, "process", "tianji_process");
        Path marker = Path.of(required(settings, PREFIX + "marker"));
        requirePrivateMarker(marker);
        var witness = new CommitWitness() {
            @Override public String before(StartProcessCommand command) {
                var rows = agent.query("""
                        SELECT a.dispatch_run_id FROM tianji_agent.agent_tool_action_intent a
                        JOIN tianji_agent.agent_tool_invocation i ON (i.tenant_id,i.run_id,i.invocation_id,i.intent_id)=
                            (a.tenant_id,a.dispatch_run_id,a.dispatch_invocation_id,a.intent_id)
                        JOIN tianji_agent.agent_run r ON (r.tenant_id,r.run_id,r.subject_type,r.subject_issuer,r.subject_id)=
                            (a.tenant_id,a.dispatch_run_id,a.owner_type,a.owner_issuer,a.owner_id)
                        WHERE a.tenant_id=? AND a.owner_type='USER' AND a.owner_issuer=? AND a.owner_id=?
                          AND a.intent_id=? AND a.business_key=? AND a.tool_sha256=? AND a.state='DISPATCHING'
                          AND a.executor_issuer=? AND a.executor_id=? AND i.executor_issuer=a.executor_issuer
                          AND i.executor_id=a.executor_id AND i.state='DISPATCHING' AND i.dispatch_epoch=a.dispatch_epoch
                          AND i.dispatch_nonce_sha256=a.dispatch_nonce_sha256 AND a.dispatch_epoch=1 AND r.status='RUNNING'
                          AND i.dispatch_not_after>pg_catalog.clock_timestamp()
                          AND i.authorization_audit->>'decision'='ALLOW' AND i.authorization_audit->>'boundary'='DISPATCH'
                        """, (row, index) -> row.getString(1), owner.tenantId(), owner.issuerFingerprint(), owner.subjectId(),
                        UUID.fromString(command.commandId()), command.businessKey(), profile.startTool().reference().contentSha256(),
                        executor.issuerFingerprint(), executor.subjectId());
                if (rows.size() != 1 || !UUID.fromString(rows.getFirst()).toString().equals(rows.getFirst())
                        || !Boolean.TRUE.equals(process.queryForObject("""
                        SELECT NOT EXISTS (SELECT 1 FROM tianji_process.tianji_process_command_receipt
                          WHERE tenant_id=? AND command_type='START' AND command_id=?)
                        AND NOT EXISTS (SELECT 1 FROM tianji_process.act_hi_procinst
                          WHERE tenant_id_=? AND business_key_=?)
                        """, Boolean.class, executor.tenantId(), command.commandId(), Long.toString(executor.tenantId()),
                        command.businessKey()))) throw unavailable();
                return rows.getFirst();
            }

            @Override public void committed(StartProcessCommand command, ProcessInstanceDTO response) {
                String hash = OrchestrationRequestDigests.start(command);
                if (!Boolean.TRUE.equals(process.queryForObject("""
                        SELECT (SELECT count(*) FROM tianji_process.tianji_process_command_receipt r
                          WHERE r.tenant_id=? AND r.subject_type='SERVICE' AND r.subject_issuer=? AND r.subject_id=?
                            AND r.command_type='START' AND r.command_id=? AND r.request_sha256=? AND r.correlation_id=?
                            AND r.release_key=? AND r.state='APPLIED' AND r.resource_id=?)=1
                        AND (SELECT count(*) FROM tianji_process.act_hi_procinst h
                          WHERE h.tenant_id_=? AND h.business_key_=? AND h.proc_inst_id_=?)=1
                        AND (SELECT count(*) FROM tianji_process.act_hi_procinst h
                          WHERE h.tenant_id_=? AND h.business_key_=?)=1
                        """, Boolean.class, executor.tenantId(), executor.issuerFingerprint(), executor.subjectId(),
                        command.commandId(), hash, command.correlationId(), command.releaseVersion(), response.instanceId(),
                        Long.toString(executor.tenantId()), command.businessKey(), response.instanceId(),
                        Long.toString(executor.tenantId()), command.businessKey()))) throw unavailable();
            }

            @Override public void discarded(StartProcessCommand command, String runId, ProcessInstanceDTO response) {
                writeMarker(marker, String.join("\n", runId, command.commandId(), response.instanceId(),
                        OrchestrationRequestDigests.start(command)) + "\n");
            }
        };
        return new S11HistoricalStartLoss(origin.resolve("/api/tianji/v1/process-instances"), profile.businessKey(), witness);
    }

    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!HTTP_BEAN.equals(name)) return bean;
        if (!(bean instanceof HttpClient http) || http.followRedirects() != HttpClient.Redirect.NEVER) throw unavailable();
        return new LossClient(http, start, businessKey, witness);
    }

    interface CommitWitness {
        String before(StartProcessCommand command);
        void committed(StartProcessCommand command, ProcessInstanceDTO response);
        void discarded(StartProcessCommand command, String runId, ProcessInstanceDTO response);
    }

    static final class LossClient extends HttpClient {
        private final HttpClient delegate;
        private final URI start;
        private final String businessKey;
        private final CommitWitness witness;
        private final AtomicBoolean claimed = new AtomicBoolean();

        LossClient(HttpClient delegate, URI start, String businessKey, CommitWitness witness) {
            this.delegate = delegate;
            this.start = start;
            this.businessKey = businessKey;
            this.witness = witness;
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            if (!"POST".equals(request.method()) || !start.equals(request.uri())) return delegate.sendAsync(request, handler);
            var command = command(request);
            if (!businessKey.equals(command.businessKey())) return delegate.sendAsync(request, handler);
            if (!DEFINITION.equals(command.definitionKey()) || !RELEASE.equals(command.releaseVersion())
                    || !command.commandId().equals(command.correlationId())
                    || !UUID.fromString(command.commandId()).toString().equals(command.commandId())
                    || !Map.of("amount", 10L).equals(command.variables()) || !claimed.compareAndSet(false, true)) {
                return CompletableFuture.failedFuture(unavailable());
            }

            long started = System.nanoTime();
            long budget = request.timeout().orElseThrow(S11HistoricalStartLoss::unavailable).toNanos();
            String runId = witness.before(command);
            if (System.nanoTime() - started >= budget) throw unavailable();
            var upstream = delegate.sendAsync(request, handler);
            var downstream = new CompletableFuture<HttpResponse<T>>();
            upstream.whenComplete((response, failure) -> {
                if (failure != null) {
                    downstream.completeExceptionally(failure);
                    return;
                }
                if (downstream.isDone()) return;
                try {
                    if (response.statusCode() != 200 || !noStoreJson(response)
                            || !(response.body() instanceof byte[] bytes) || bytes.length > 4096) throw unavailable();
                    var result = JSON.readValue(bytes, ProcessInstanceDTO.class);
                    if (!command.businessKey().equals(result.businessKey()) || !command.correlationId().equals(result.correlationId())
                            || !command.definitionKey().equals(result.definitionKey()) || !command.releaseVersion().equals(result.releaseVersion())) {
                        throw unavailable();
                    }
                    witness.committed(command, result);
                    if (System.nanoTime() - started >= budget) throw unavailable();
                    if (downstream.completeExceptionally(new IOException("S11 fixture discarded a committed START response"))) {
                        witness.discarded(command, runId, result);
                    }
                } catch (RuntimeException rejected) {
                    downstream.completeExceptionally(unavailable());
                }
            });
            downstream.whenComplete((ignored, failure) -> {
                if (downstream.isCancelled()) upstream.cancel(true);
            });
            return downstream;
        }

        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            if ("POST".equals(request.method()) && start.equals(request.uri())) throw unavailable();
            return delegate.send(request, handler);
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushes) {
            if ("POST".equals(request.method()) && start.equals(request.uri())) return CompletableFuture.failedFuture(unavailable());
            return delegate.sendAsync(request, handler, pushes);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
        @Override public SSLContext sslContext() { return delegate.sslContext(); }
        @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public Optional<Executor> executor() { return delegate.executor(); }
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public boolean awaitTermination(Duration timeout) throws InterruptedException { return delegate.awaitTermination(timeout); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public void shutdownNow() { delegate.shutdownNow(); }
        @Override public void close() { delegate.close(); }
    }

    private static StartProcessCommand command(HttpRequest request) {
        var publisher = request.bodyPublisher().orElseThrow(S11HistoricalStartLoss::unavailable);
        long length = publisher.contentLength();
        if (length < 1 || length > 4096) throw unavailable();
        var data = ByteBuffer.allocate((int) length);
        var completed = new CompletableFuture<byte[]>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            @Override public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                value.request(Long.MAX_VALUE);
            }
            @Override public void onNext(ByteBuffer bytes) {
                if (bytes.remaining() > data.remaining()) {
                    subscription.cancel();
                    completed.completeExceptionally(unavailable());
                    return;
                }
                data.put(bytes);
            }
            @Override public void onError(Throwable failure) { completed.completeExceptionally(unavailable()); }
            @Override public void onComplete() {
                if (data.hasRemaining()) completed.completeExceptionally(unavailable());
                else completed.complete(data.array());
            }
        });
        try {
            return JSON.readValue(completed.get(500, TimeUnit.MILLISECONDS), StartProcessCommand.class);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static boolean noStoreJson(HttpResponse<?> response) {
        return response.headers().allValues("Cache-Control").equals(java.util.List.of("no-store"))
                && response.headers().firstValue("Content-Type").map(value ->
                        "application/json".equalsIgnoreCase(value.split(";", 2)[0].trim())).orElse(false);
    }

    private static JdbcTemplate observer(Properties settings, String lane, String schema) {
        String key = PREFIX + lane + "-observer-";
        String url = required(settings, key + "url");
        String role = required(settings, key + "role");
        String store = "agent".equals(lane) ? "as" : "ps";
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[1-9][0-9]{0,4}/tianji_it_historical_" + store + "_[a-z0-9_]{1,20}")
                || !role.matches("tianji_it_historical_" + store + "_obs_[a-z0-9_]{1,20}")
                || role.equals(settings.getProperty("spring.datasource.username"))) {
            throw unavailable();
        }
        var source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(role);
        source.setPassword(required(settings, key + "password"));
        source.setReadOnlyMode("always");
        source.setReadOnly(true);
        source.setConnectTimeout(2);
        source.setSocketTimeout(2);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(1);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database()=? AND session_user=? AND current_user=session_user
                  AND pg_catalog.inet_server_addr()='127.0.0.1'::inet AND NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('transaction_read_only')='on'
                  AND NOT (rolsuper OR rolinherit OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
                  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=pg_roles.oid)
                  AND pg_catalog.has_schema_privilege(?, 'USAGE')
                FROM pg_catalog.pg_roles WHERE rolname=current_user
                """, Boolean.class, url.substring(url.lastIndexOf('/') + 1), role, schema))) throw unavailable();
        return jdbc;
    }

    private static void requirePrivateMarker(Path path) {
        try {
            var parent = path.getParent();
            var attrs = Files.readAttributes(parent, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!path.isAbsolute() || !path.equals(path.normalize()) || !parent.equals(parent.toRealPath())
                    || !attrs.isDirectory() || !attrs.owner().getName().equals(System.getProperty("user.name"))
                    || !attrs.permissions().equals(PosixFilePermissions.fromString("rwx------"))
                    || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static void writeMarker(Path path, String evidence) {
        requirePrivateMarker(path);
        try {
            Path temporary = Files.createTempFile(path.getParent(), ".s11-start-cut-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.writeString(temporary, evidence, StandardCharsets.UTF_8);
                Files.createLink(path, temporary);
            } finally {
                Files.delete(temporary);
            }
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static String required(Properties settings, String key) {
        String value = settings.getProperty(key);
        if (value == null || value.isBlank() || value.length() > 2048 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) throw unavailable();
        return value;
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("S11 historical START cut evidence unavailable");
    }
}
