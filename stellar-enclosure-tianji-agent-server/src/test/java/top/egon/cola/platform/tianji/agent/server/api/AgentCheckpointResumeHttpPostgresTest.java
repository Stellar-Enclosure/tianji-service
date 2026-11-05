package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator-only S11 protected HTTP evidence against one prepared, disposable checkpoint. */
@EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.protected-resume", matches = "true")
class AgentCheckpointResumeHttpPostgresTest {
    private static final String PREFIX = "tianji.postgres.checkpoint.protected-resume.";
    private static final String RUN_PATH = "/api/tianji/v1/agent-runs/";
    private static final Pattern JDBC_URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_checkpoint_[a-z0-9_]{1,24})");
    private static final int MAX_BODY_BYTES = 16_384;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Test
    @Timeout(105)
    void requires_current_owner_permissions_and_replays_the_exact_durable_acknowledgement() throws Exception {
        var input = FixtureInput.load();
        String read = readSecret("read-token-file", "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", 8192);
        String resume = readSecret("resume-token-file", "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", 8192);
        assertTrue(!read.equals(resume), "the operator must supply separately issued bearers");
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
             var database = openWitness(input)) {
            var commandId = UUID.randomUUID().toString();
            var before = snapshot(database, input, commandId);
            assertTrue(before.suspended() && before.codec() == input.codec()
                    && before.receipts() == 0 && before.budget() == 0 && before.nativeEvents() == 1,
                    "requires the exact expired, model-free V16 cut before any request");
            var command = new ResumeCommand(input.runId(), commandId, before.revision());
            var owned = exchange(client, request(input.endpoint().resolve(RUN_PATH + input.runId()), read).GET().build());
            assertEquals(200, owned.statusCode(), "the independently issued READ bearer must access the owned run");
            assertTrue(isOwnedRead(owned.body(), command), "the protected READ must observe the exact suspended cut");

            assertEquals(401, resume(client, input, command, null).statusCode());
            assertEquals(403, resume(client, input, command, read).statusCode());
            var denied = snapshot(database, input, commandId);
            assertTrue(before.cutDigest().equals(denied.cutDigest()) && before.stateDigest().equals(denied.stateDigest()),
                    "rejected callers must leave the run, leases, checkpoint, Session and ledger unchanged");

            var expected = new Acknowledgement(input.runId(), AgentRunStatusEnum.RUNNING, 1, before.revision() + 2);
            var accepted = acknowledgement(resume(client, input, command, resume), expected);
            var completed = awaitCompletion(database, input, commandId);
            assertTrue(completed.completed() && completed.codec() == input.codec()
                    && completed.revision() > accepted.revision()
                    && completed.runEpoch() == before.runEpoch() + 1
                    && completed.sessionEpoch() == before.sessionEpoch() + 1
                    && completed.receipts() == 1 && completed.exactReceipt() && completed.budget() == 1
                    && completed.settledModel() && completed.nativeEvents() > before.nativeEvents()
                    && completed.claimEvents() == 2
                    && completed.cutDigest().equals(before.cutDigest()),
                    "one scoped continuation must preserve the original native prefix and persist the original ACK");

            acknowledgement(resume(client, input, command, resume), accepted);
            var changed = new ResumeCommand(input.runId(), commandId, before.revision() + 1);
            assertEquals(409, resume(client, input, changed, resume).statusCode());
            assertTrue(completed.equals(snapshot(database, input, commandId)),
                    "replay and changed-input rejection must leave the completed durable witness unchanged");
        } catch (SQLException failure) {
            throw new IllegalStateException("The disposable read-only PostgreSQL witness is unavailable");
        }
    }

    private static HttpResponse<byte[]> resume(HttpClient client, FixtureInput input, ResumeCommand command, String token)
            throws InterruptedException {
        return exchange(client, request(input.endpoint().resolve(AgentCheckpointResumeController.PATH), token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(command))).build());
    }

    private static HttpRequest.Builder request(URI uri, String token) {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(42)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static HttpResponse<byte[]> exchange(HttpClient client, HttpRequest request) throws InterruptedException {
        var exchange = client.sendAsync(request, response -> new BoundedBody());
        try {
            return exchange.get(42, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("The bounded isolated HTTPS request failed");
        } finally {
            exchange.cancel(true);
        }
    }

    private static Acknowledgement acknowledgement(HttpResponse<byte[]> response, Acknowledgement expected) {
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").map(value ->
                "application/json".equalsIgnoreCase(value.split(";", 2)[0].strip())).orElse(false),
                "the protected acknowledgement must use the JSON media type");
        assertTrue(response.headers().allValues("Cache-Control").stream().anyMatch(value ->
                Arrays.stream(value.split(",")).anyMatch(directive -> "no-store".equalsIgnoreCase(directive.strip()))),
                "the protected acknowledgement must prevent caching");
        return readAcknowledgement(response.body(), expected);
    }

    static Acknowledgement readAcknowledgement(byte[] body, Acknowledgement expected) {
        var node = jsonObject(body);
        if (node.size() != 4 || !expected.runId().equals(node.path("runId").stringValue(null))
                || !"RUNNING".equals(node.path("status").stringValue(null))
                || !exactInteger(node.path("attempt"), expected.attempt())
                || !exactInteger(node.path("revision"), expected.revision())) {
            throw new IllegalArgumentException("Checkpoint acknowledgement does not match the requested cut");
        }
        return expected;
    }

    private static boolean isOwnedRead(byte[] body, ResumeCommand command) {
        var node = jsonObject(body);
        return command.runId().equals(node.path("runId").stringValue(null))
                && "SUSPENDED".equals(node.path("status").stringValue(null))
                && exactInteger(node.path("attempt"), 1)
                && exactInteger(node.path("revision"), command.expectedRevision());
    }

    private static JsonNode jsonObject(byte[] bytes) {
        if (bytes != null && bytes.length <= MAX_BODY_BYTES) {
            try {
                var node = JSON.readTree(bytes);
                if (node != null && node.isObject()) return node;
            } catch (tools.jackson.core.JacksonException malformed) {
                // Do not propagate response text from a deployment or identity provider.
            }
        }
        throw new IllegalArgumentException("Checkpoint HTTP response is not a bounded JSON object");
    }

    private static boolean exactInteger(JsonNode node, long value) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() == value;
    }

    static URI loopbackOrigin(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() < 1 || uri.getPort() > 65535 || !uri.getRawPath().isEmpty()
                || !uri.getRawAuthority().equals("127.0.0.1:" + uri.getPort())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Checkpoint acceptance requires an exact isolated loopback HTTPS origin");
        }
        return uri;
    }

    private static String readSecret(String key, String pattern, int max) throws IOException {
        Path file = Path.of(required(key));
        var attrs = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!file.isAbsolute() || !attrs.isRegularFile() || !attrs.owner().getName().equals(System.getProperty("user.name"))
                || !attrs.permissions().equals(PosixFilePermissions.fromString("rw-------")) || attrs.size() > max + 1) {
            throw new IllegalArgumentException("Checkpoint acceptance requires private operator-owned credential files");
        }
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(max + 2);
            try {
                String text = new String(bytes, StandardCharsets.US_ASCII);
                if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
                if (text.length() > max || !text.matches(pattern)) {
                    throw new IllegalArgumentException("Checkpoint acceptance credential has an invalid format");
                }
                return text;
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    private static String required(String key) {
        String value = System.getProperty(PREFIX + key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing operator input " + key);
        return value;
    }

    private record FixtureInput(URI endpoint, String jdbc, String database, String role,
                                TrustedAgentActor owner, String runId, int codec) {
        static FixtureInput load() {
            String jdbc = required("jdbc");
            var matcher = JDBC_URL.matcher(jdbc);
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > 65535) {
                throw new IllegalArgumentException("Checkpoint acceptance requires a disposable loopback database");
            }
            String role = required("observer-role");
            if (!role.matches("tianji_it_checkpoint_observer_[a-z0-9_]{1,24}")) {
                throw new IllegalArgumentException("Checkpoint acceptance requires a distinct disposable observer role");
            }
            String runId = required("run-id");
            if (!UUID.fromString(runId).toString().equals(runId)) throw new IllegalArgumentException("Invalid fixture run ID");
            int codec = Integer.parseInt(required("codec"));
            if (codec != 1 && codec != 2) throw new IllegalArgumentException("Invalid fixture Session codec");
            var owner = TrustedAgentActor.verified(Long.parseLong(required("tenant")), TrustedAgentActor.SubjectType.USER,
                    required("subject"), required("issuer"));
            return new FixtureInput(loopbackOrigin(required("https-origin")), jdbc, matcher.group(2), role, owner, runId, codec);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_BODY_BYTES - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Checkpoint acceptance response exceeded its size limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }

    private static Connection openWitness(FixtureInput input) throws SQLException, IOException {
        var properties = new Properties();
        properties.setProperty("user", input.role());
        properties.setProperty("password", readSecret("observer-password-file", "[!-~]+", 1024));
        properties.setProperty("connectTimeout", "4");
        properties.setProperty("socketTimeout", "5");
        properties.setProperty("readOnlyMode", "always");
        var connection = DriverManager.getConnection(input.jdbc(), properties);
        try {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement("""
                    SELECT current_database()=? AND session_user=? AND current_user=session_user
                      AND pg_catalog.inet_server_addr()='127.0.0.1'::inet AND NOT pg_catalog.pg_is_in_recovery()
                      AND pg_catalog.current_setting('transaction_read_only')='on'
                      AND NOT (rolsuper OR rolinherit OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
                      AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=pg_roles.oid)
                      AND NOT EXISTS (SELECT 1 FROM pg_catalog.unnest(ARRAY[
                        'agent_run','agent_checkpoint','agent_event','agent_session','agent_session_lease',
                        'agent_session_event','agent_run_command_receipt','agent_budget_reservation']) AS evidence(name)
                        WHERE NOT pg_catalog.has_table_privilege('tianji_agent.' || name, 'SELECT')
                           OR pg_catalog.has_any_column_privilege('tianji_agent.' || name, 'INSERT,UPDATE,REFERENCES')
                           OR pg_catalog.has_table_privilege('tianji_agent.' || name, 'DELETE,TRUNCATE,TRIGGER'))
                    FROM pg_catalog.pg_roles WHERE rolname=current_user
                    """)) {
                statement.setQueryTimeout(4);
                statement.setString(1, input.database());
                statement.setString(2, input.role());
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) throw new SQLException("Invalid disposable observer role");
                }
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }

    private static Witness awaitCompletion(Connection database, FixtureInput input, String commandId)
            throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Witness witness;
        do {
            witness = snapshot(database, input, commandId);
            if (witness.completed()) return witness;
            Thread.sleep(100);
        } while (System.nanoTime() - deadline < 0);
        return witness;
    }

    private static Witness snapshot(Connection database, FixtureInput input, String commandId) throws SQLException {
        try (var statement = database.prepareStatement("""
                SELECT r.revision,r.lease_epoch AS run_epoch,l.lease_epoch AS session_epoch,s.state_codec_version AS codec,
                  r.status='SUSPENDED' AND r.wait_reason='LEASE_EXPIRED' AND r.attempt=1
                    AND r.revision=c.run_revision+1 AND r.last_event_sequence=c.run_event_sequence+1
                    AND r.lease_epoch=c.run_lease_epoch+1 AND r.lease_owner IS NULL AND r.lease_expires_at IS NULL
                    AND r.result IS NULL AND r.finished_at IS NULL AND r.cancel_requested_at IS NULL AND r.error_code IS NULL
                    AND r.deadline_at>pg_catalog.clock_timestamp()+interval '50 seconds'
                    AND l.lease_epoch=c.session_lease_epoch AND l.lease_owner IS NOT NULL
                    AND l.lease_expires_at<=pg_catalog.clock_timestamp()
                    AND s.revision=c.session_revision AND s.last_event_sequence=c.session_event_sequence AS suspended,
                  r.status='COMPLETED' AND r.attempt=1 AND r.result IS NOT NULL AND r.finished_at IS NOT NULL
                    AND r.error_code IS NULL AND r.lease_owner IS NULL AND r.lease_expires_at IS NULL
                    AND l.lease_owner IS NULL AND l.lease_expires_at IS NULL
                    AND r.model_calls_reserved=1 AND r.tool_calls_reserved=0
                    AND r.active_model_calls=0 AND r.active_tool_calls=0
                    AND session_events.same_codec AND session_events.same_invocation AND session_events.user_inputs=1
                    AND session_events.last_sequence=s.last_event_sequence AND events.completed AS completed,
                  receipt.total AS receipts,receipt.exact_receipt,budget.total AS budget,budget.settled_model,
                  session_events.total AS native_events,events.claim_events,
                  pg_catalog.md5(pg_catalog.jsonb_build_array(pg_catalog.to_jsonb(c),pg_catalog.to_jsonb(prefix))::text) AS cut_digest,
                  pg_catalog.md5(pg_catalog.jsonb_build_array(pg_catalog.to_jsonb(r),pg_catalog.to_jsonb(s),pg_catalog.to_jsonb(l),
                      receipt.digest,budget.digest,session_events.digest,events.digest)::text) AS state_digest
                FROM tianji_agent.agent_checkpoint c
                JOIN tianji_agent.agent_run r ON (r.tenant_id,r.run_id,r.subject_type,r.subject_issuer,r.subject_id,r.session_id)=
                    (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id,c.session_id)
                JOIN tianji_agent.agent_session s ON (s.tenant_id,s.subject_type,s.subject_issuer,s.subject_id,s.app_name,s.session_id)=
                    (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                JOIN tianji_agent.agent_session_lease l ON (l.tenant_id,l.subject_type,l.subject_issuer,l.subject_id,l.app_name,l.session_id)=
                    (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                JOIN tianji_agent.agent_session_event prefix
                  ON (prefix.tenant_id,prefix.subject_type,prefix.subject_issuer,prefix.subject_id,prefix.app_name,prefix.session_id,prefix.sequence)=
                    (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id,c.session_event_sequence)
                CROSS JOIN LATERAL (
                  SELECT count(*) AS total,
                    bool_or(t.command_id=? AND t.result=pg_catalog.jsonb_build_object('runId',c.run_id,
                        'status','RUNNING','attempt',1,'revision',c.run_revision+3)) AS exact_receipt,
                    pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(t) ORDER BY t.command_id)::text) AS digest
                  FROM tianji_agent.agent_run_command_receipt t
                  WHERE (t.tenant_id,t.run_id,t.subject_type,t.subject_issuer,t.subject_id)=
                      (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id) AND t.operation='RESUME'
                ) receipt
                CROSS JOIN LATERAL (
                  SELECT count(*) AS total, bool_or(b.kind='MODEL' AND b.state='SETTLED' AND b.settled_at IS NOT NULL
                        AND b.lease_epoch=r.lease_epoch) AS settled_model,
                    pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(b) ORDER BY b.reservation_id)::text) AS digest
                  FROM tianji_agent.agent_budget_reservation b WHERE (b.tenant_id,b.run_id)=(c.tenant_id,c.run_id)
                ) budget
                CROSS JOIN LATERAL (
                  SELECT count(*) AS total, max(e.sequence) AS last_sequence,
                    bool_and((e.payload::jsonb->>'codecVersion') IS NOT DISTINCT FROM s.state_codec_version::text) AS same_codec,
                    bool_and((e.payload::jsonb#>>'{event,invocationId}') IS NOT DISTINCT FROM c.native_invocation_id) AS same_invocation,
                    count(*) FILTER (WHERE e.payload::jsonb#>>'{event,author}'='user') AS user_inputs,
                    pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(e) ORDER BY e.sequence)::text) AS digest
                  FROM tianji_agent.agent_session_event e
                  WHERE (e.tenant_id,e.subject_type,e.subject_issuer,e.subject_id,e.app_name,e.session_id)=
                      (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                ) session_events
                CROSS JOIN LATERAL (
                  SELECT count(*) FILTER (WHERE e.event_type='RUN_STATUS_CHANGED' AND
                    ((e.sequence=c.run_event_sequence+2 AND e.payload='{"from":"SUSPENDED","status":"QUEUED","reason":"CHECKPOINT_RESUME"}'::jsonb)
                    OR (e.sequence=c.run_event_sequence+3 AND e.payload='{"from":"QUEUED","status":"RUNNING","reason":"CHECKPOINT_RESUME"}'::jsonb)))
                    AS claim_events,
                    bool_or(e.sequence=r.last_event_sequence AND e.event_type='RUN_STATUS_CHANGED'
                      AND e.payload='{"from":"RUNNING","status":"COMPLETED"}'::jsonb) AS completed,
                    pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(e) ORDER BY e.sequence)::text) AS digest
                  FROM tianji_agent.agent_event e WHERE (e.tenant_id,e.run_id)=(c.tenant_id,c.run_id)
                ) events
                WHERE c.tenant_id=? AND c.run_id=? AND c.subject_type='USER' AND c.subject_issuer=? AND c.subject_id=?
                  AND c.attempt=1 AND c.checkpoint_no=1 AND c.safe_boundary='FIRST_DURABLE_INPUT_V1'
                  AND prefix.native_event_id=c.native_event_id
                """)) {
            statement.setQueryTimeout(4);
            statement.setString(1, commandId);
            statement.setLong(2, input.owner().tenantId());
            statement.setString(3, input.runId());
            statement.setString(4, input.owner().issuerFingerprint());
            statement.setString(5, input.owner().subjectId());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing exact owned checkpoint");
                var witness = new Witness(result.getLong("revision"), result.getLong("run_epoch"),
                        result.getLong("session_epoch"), result.getInt("codec"), result.getBoolean("suspended"),
                        result.getBoolean("completed"), result.getInt("receipts"), result.getBoolean("exact_receipt"),
                        result.getInt("budget"), result.getBoolean("settled_model"), result.getInt("native_events"),
                        result.getInt("claim_events"), result.getString("cut_digest"), result.getString("state_digest"));
                if (result.next()) throw new SQLException("Ambiguous owned checkpoint");
                return witness;
            }
        }
    }

    private record Witness(long revision, long runEpoch, long sessionEpoch, int codec, boolean suspended,
                           boolean completed, int receipts, boolean exactReceipt, int budget, boolean settledModel,
                           int nativeEvents, int claimEvents, String cutDigest, String stateDigest) { }
}
