package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.client.HttpAgentClient;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceipt;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryCommandClient.HistoricalStartReceiptCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator-only witness for a genuinely committed START with an unresolved original Agent dispatch. */
@EnabledIfSystemProperty(named = "tianji.postgres.historical-start.protected", matches = "true")
class AgentHistoricalStartReceiptHttpPostgresTest {
    private static final String PREFIX = "tianji.postgres.historical-start.protected.";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+";

    @Test
    @Timeout(90)
    void reconciles_the_exact_committed_process_start_without_repeating_or_rewriting_the_original_effect() throws Exception {
        var input = FixtureInput.load();
        String read = readSecret("read-token-file", TOKEN_PATTERN, 8192);
        String reconcile = readSecret("reconcile-token-file", TOKEN_PATTERN, 8192);
        String foreign = readSecret("foreign-token-file", TOKEN_PATTERN, 8192);
        String service = readSecret("service-read-token-file", TOKEN_PATTERN, 8192);
        assertEquals(4, List.of(read, reconcile, foreign, service).stream().distinct().count(),
                "the operator must supply four independently issued bearers");
        try (var wire = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
             var agentDb = input.agentStore().open();
             var processDb = input.processStore().open()) {
            var before = agentEvidence(agentDb, input);
            assertEquals(0, before.observationCount(), "requires a fresh historical reconciliation cut");
            var source = processEvidence(processDb, input, before.businessKey());
            var process = new HttpProcessClient(input.processOrigin(), wire, () -> service, TIMEOUT);
            var exactRead = new GetProcessStartReceiptQuery(input.command().intentId().toString(), source.requestSha256());
            var original = process.getStartReceipt(exactRead);
            assertTrue(original.status() == ProcessStartReceiptDTO.StatusEnum.APPLIED
                    && source.instanceId().equals(original.instanceId()) && source.recordedAt().equals(original.recordedAt()),
                    "original SERVICE READ must match the exact persisted Process receipt");

            var readOnly = agent(input.agentOrigin(), wire, read);
            assertEquals(input.command().runId(), readOnly.get(new GetAgentRunQuery(input.command().runId())).runId());
            assertEquals(403, assertThrows(AgentClientException.class,
                    () -> readOnly.reconcileHistoricalStart(input.command())).httpStatus());
            var foreignUser = agent(input.agentOrigin(), wire, foreign);
            assertEquals(404, assertThrows(AgentClientException.class,
                    () -> foreignUser.reconcileHistoricalStart(input.command())).httpStatus());
            assertTrue(before.equals(agentEvidence(agentDb, input))
                    && source.equals(processEvidence(processDb, input, before.businessKey())),
                    "neither denied caller may change either primary witness");

            var owner = agent(input.agentOrigin(), wire, reconcile);
            var result = owner.reconcileHistoricalStart(input.command());
            var applied = agentEvidence(agentDb, input);
            assertTrue(applied.observationCount() == 1 && before.sourceStamp().equals(applied.sourceStamp())
                    && source.requestSha256().equals(applied.observedProcessHash()),
                    "one positive observation must preserve the original V13, Run and budget facts");
            requireExactReceipt(result, original, applied.observation());
            requireExactReceipt(owner.reconcileHistoricalStart(input.command()), original, applied.observation());
            assertTrue(applied.equals(agentEvidence(agentDb, input))
                    && source.equals(processEvidence(processDb, input, before.businessKey())),
                    "protected re-observation must keep one Agent observation and the original Process start");
        } catch (SQLException failure) {
            throw new IllegalStateException("The disposable read-only PostgreSQL witness is unavailable");
        }
    }

    private static HttpAgentClient agent(URI endpoint, HttpClient wire, String token) {
        return new HttpAgentClient(endpoint, wire, () -> token, TIMEOUT, 0, Duration.ZERO);
    }

    static void requireExactReceipt(HistoricalStartReceipt result, ProcessStartReceiptDTO original,
                                    HistoricalStartReceipt observation) {
        if (!result.equals(observation) || original.status() != ProcessStartReceiptDTO.StatusEnum.APPLIED
                || !result.instanceId().equals(original.instanceId()) || !result.recordedAt().equals(original.recordedAt())) {
            throw new IllegalArgumentException("Historical START response does not match both durable receipts");
        }
    }

    private static AgentEvidence agentEvidence(Connection database, FixtureInput input) throws SQLException {
        try (var statement = database.prepareStatement("""
                SELECT a.business_key,
                  pg_catalog.md5(pg_catalog.jsonb_build_array(pg_catalog.to_jsonb(a),pg_catalog.to_jsonb(i),
                      pg_catalog.to_jsonb(o),pg_catalog.to_jsonb(d),
                      (SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(b) ORDER BY b.run_id,b.reservation_id)
                       FROM tianji_agent.agent_budget_reservation b
                       WHERE b.tenant_id=a.tenant_id AND b.run_id IN (o.run_id,d.run_id)),
                      (SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(j) ORDER BY j.run_id,j.invocation_id)
                       FROM tianji_agent.agent_tool_invocation j WHERE j.tenant_id=a.tenant_id AND j.intent_id=a.intent_id)
                    )::text) AS source_stamp,
                  receipt.total,pg_catalog.md5(pg_catalog.to_jsonb(c)::text) AS observation_stamp,
                  c.result#>>'{process,requestSha256}' AS process_hash,c.result#>>'{process,instanceId}' AS instance_id,
                  c.result#>>'{process,recordedAt}' AS recorded_at,c.created_at AS observed_at,
                  c.result->>'schemaVersion'='1' AND c.result->>'status'='APPLIED_OBSERVED'
                    AND c.result#>>'{source,priorState}'=a.state AND c.result#>>'{source,actionRevision}'=a.revision::text
                    AND c.result#>>'{source,invocationRevision}'=i.revision::text AS positive
                FROM tianji_agent.agent_tool_action_intent a
                JOIN tianji_agent.agent_tool_invocation i ON (i.tenant_id,i.run_id,i.invocation_id,i.intent_id)=
                    (a.tenant_id,a.dispatch_run_id,a.dispatch_invocation_id,a.intent_id)
                JOIN tianji_agent.agent_run o ON (o.tenant_id,o.run_id,o.subject_type,o.subject_issuer,o.subject_id)=
                    (a.tenant_id,a.origin_run_id,a.owner_type,a.owner_issuer,a.owner_id)
                JOIN tianji_agent.agent_run d ON (d.tenant_id,d.run_id,d.subject_type,d.subject_issuer,d.subject_id)=
                    (a.tenant_id,a.dispatch_run_id,a.owner_type,a.owner_issuer,a.owner_id)
                LEFT JOIN tianji_agent.agent_run_command_receipt c
                  ON (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id)=
                      (a.tenant_id,d.run_id,a.owner_type,a.owner_issuer,a.owner_id)
                  AND c.operation='TOOL_START_RECEIPT_RECONCILE' AND c.command_id=a.intent_id::text||':'||a.dispatch_epoch::text
                CROSS JOIN LATERAL (
                    SELECT count(*) AS total FROM tianji_agent.agent_run_command_receipt w
                    WHERE (w.tenant_id,w.run_id,w.subject_type,w.subject_issuer,w.subject_id)=
                        (a.tenant_id,d.run_id,a.owner_type,a.owner_issuer,a.owner_id)
                      AND w.operation='TOOL_START_RECEIPT_RECONCILE' AND w.command_id LIKE a.intent_id::text||':%'
                ) receipt
                WHERE a.tenant_id=? AND a.owner_type='USER' AND a.owner_issuer=? AND a.owner_id=?
                  AND a.intent_id=? AND a.dispatch_run_id=? AND a.executor_issuer=? AND a.executor_id=?
                  AND i.executor_issuer=a.executor_issuer AND i.executor_id=a.executor_id
                  AND a.dispatch_epoch>0 AND i.dispatch_epoch=a.dispatch_epoch
                  AND a.state IN ('UNKNOWN','DISPATCHING') AND i.state=a.state
                  AND a.side_effect_outcome='UNKNOWN' AND i.side_effect_outcome='UNKNOWN'
                  AND i.dispatch_not_after<=pg_catalog.clock_timestamp()
                  AND d.lease_owner IS NULL AND d.lease_expires_at IS NULL
                  AND (d.status IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT')
                       OR (d.status='SUSPENDED' AND d.wait_reason='LEASE_EXPIRED'))
                """)) {
            statement.setQueryTimeout(4);
            statement.setLong(1, input.owner().tenantId());
            statement.setString(2, input.owner().issuerFingerprint());
            statement.setString(3, input.owner().subjectId());
            statement.setObject(4, input.command().intentId());
            statement.setString(5, input.command().runId());
            statement.setString(6, input.executor().issuerFingerprint());
            statement.setString(7, input.executor().subjectId());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing unresolved Agent START fixture");
                HistoricalStartReceipt observation = null;
                int count = result.getInt("total");
                if (count != 0) {
                    if (count != 1 || !result.getBoolean("positive")) throw new SQLException("Invalid stored observation");
                    observation = new HistoricalStartReceipt(input.command().intentId(), input.command().runId(),
                            result.getString("instance_id"), Instant.parse(result.getString("recorded_at")),
                            result.getTimestamp("observed_at").toInstant());
                }
                var evidence = new AgentEvidence(result.getString("business_key"), result.getString("source_stamp"),
                        count, result.getString("process_hash"), result.getString("observation_stamp"), observation);
                if (result.next()) throw new SQLException("Ambiguous unresolved Agent START fixture");
                return evidence;
            }
        }
    }

    private static ProcessEvidence processEvidence(Connection database, FixtureInput input, String businessKey) throws SQLException {
        try (var statement = database.prepareStatement("""
                SELECT r.resource_id,r.request_sha256,r.created_at,n.total,n.instance,
                  pg_catalog.md5(pg_catalog.jsonb_build_array(pg_catalog.to_jsonb(r),n.digest)::text) AS state_stamp
                FROM tianji_process.tianji_process_command_receipt r
                CROSS JOIN LATERAL (
                  SELECT count(*) AS total,min(h.proc_inst_id_) AS instance,
                    pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(h) ORDER BY h.proc_inst_id_)::text) AS digest
                  FROM tianji_process.act_hi_procinst h
                  WHERE h.tenant_id_=r.tenant_id::text AND h.business_key_=?
                ) n
                WHERE r.tenant_id=? AND r.subject_type='SERVICE' AND r.subject_issuer=? AND r.subject_id=?
                  AND r.command_type='START' AND r.command_id=? AND r.correlation_id=r.command_id AND r.state='APPLIED'
                """)) {
            statement.setQueryTimeout(4);
            statement.setString(1, businessKey);
            statement.setLong(2, input.executor().tenantId());
            statement.setString(3, input.executor().issuerFingerprint());
            statement.setString(4, input.executor().subjectId());
            statement.setString(5, input.command().intentId().toString());
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt("total") != 1 || !result.getString("resource_id").equals(result.getString("instance"))) {
                    throw new SQLException("Missing exact single native Process START");
                }
                var evidence = new ProcessEvidence(result.getString("resource_id"), result.getString("request_sha256"),
                        result.getTimestamp("created_at").toInstant(), result.getString("state_stamp"));
                if (result.next()) throw new SQLException("Ambiguous Process START receipt");
                return evidence;
            }
        }
    }

    private record AgentEvidence(String businessKey, String sourceStamp, int observationCount, String observedProcessHash,
                                 String observationStamp, HistoricalStartReceipt observation) { }

    private record ProcessEvidence(String instanceId, String requestSha256, Instant recordedAt, String stateStamp) { }

    private record FixtureInput(URI agentOrigin, URI processOrigin, WitnessStore agentStore, WitnessStore processStore,
                                TrustedAgentActor owner, TrustedAgentActor executor, HistoricalStartReceiptCommand command) {
        static FixtureInput load() {
            URI agentOrigin = AgentCheckpointResumeHttpPostgresTest.loopbackOrigin(required("agent-origin"));
            URI processOrigin = AgentCheckpointResumeHttpPostgresTest.loopbackOrigin(required("process-origin"));
            if (agentOrigin.equals(processOrigin)) throw new IllegalArgumentException("Distinct isolated origins are required");
            long tenant = Long.parseLong(required("tenant"));
            var owner = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER,
                    required("owner-subject"), required("owner-issuer"));
            var executor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.SERVICE,
                    required("service-subject"), required("service-issuer"));
            String runId = required("run-id");
            String intentId = required("intent-id");
            UUID intent = UUID.fromString(intentId);
            if (!UUID.fromString(runId).toString().equals(runId) || !intent.toString().equals(intentId)) {
                throw new IllegalArgumentException("Historical START fixture identifiers are invalid");
            }
            var command = new HistoricalStartReceiptCommand(runId, intent);
            return new FixtureInput(agentOrigin, processOrigin, WitnessStore.load("as", List.of(
                    "tianji_agent.agent_tool_action_intent", "tianji_agent.agent_tool_invocation",
                    "tianji_agent.agent_run", "tianji_agent.agent_budget_reservation", "tianji_agent.agent_run_command_receipt")),
                    WitnessStore.load("ps", List.of("tianji_process.tianji_process_command_receipt",
                            "tianji_process.act_hi_procinst")), owner, executor, command);
        }
    }

    private record WitnessStore(String lane, String jdbc, String database, String role, List<String> tables) {
        static WitnessStore load(String lane, List<String> tables) {
            String jdbc = required(lane + "-jdbc");
            var matcher = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/"
                    + "(tianji_it_historical_" + lane + "_[a-z0-9_]{1,20})").matcher(jdbc);
            String role = required(lane + "-observer-role");
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > 65535
                    || !role.matches("tianji_it_historical_" + lane + "_obs_[a-z0-9_]{1,20}")) {
                throw new IllegalArgumentException("Historical START requires an isolated loopback database and observer role");
            }
            return new WitnessStore(lane, jdbc, matcher.group(2), role, tables);
        }

        Connection open() throws SQLException, IOException {
            var properties = new Properties();
            properties.setProperty("user", role);
            properties.setProperty("password", readSecret(lane + "-observer-password-file", "[!-~]+", 1024));
            properties.setProperty("connectTimeout", "4");
            properties.setProperty("socketTimeout", "5");
            properties.setProperty("readOnlyMode", "always");
            var connection = DriverManager.getConnection(jdbc, properties);
            try {
                connection.setReadOnly(true);
                try (var statement = connection.prepareStatement("""
                        SELECT current_database()=? AND session_user=? AND current_user=session_user
                          AND pg_catalog.inet_server_addr()='127.0.0.1'::inet AND NOT pg_catalog.pg_is_in_recovery()
                          AND pg_catalog.current_setting('transaction_read_only')='on'
                          AND NOT (rolsuper OR rolinherit OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
                          AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=pg_roles.oid)
                          AND NOT EXISTS (SELECT 1 FROM pg_catalog.unnest(CAST(? AS text[])) AS evidence(name)
                            WHERE NOT pg_catalog.has_table_privilege(name, 'SELECT')
                               OR pg_catalog.has_any_column_privilege(name, 'INSERT,UPDATE,REFERENCES')
                               OR pg_catalog.has_table_privilege(name, 'DELETE,TRUNCATE,TRIGGER'))
                        FROM pg_catalog.pg_roles WHERE rolname=current_user
                        """)) {
                    statement.setQueryTimeout(4);
                    statement.setString(1, database);
                    statement.setString(2, role);
                    statement.setArray(3, connection.createArrayOf("text", tables.toArray(String[]::new)));
                    try (var result = statement.executeQuery()) {
                        if (!result.next() || !result.getBoolean(1)) throw new SQLException("Invalid isolated observer role");
                    }
                }
                return connection;
            } catch (SQLException | RuntimeException failure) {
                connection.close();
                throw failure;
            }
        }
    }

    private static String readSecret(String key, String pattern, int max) throws IOException {
        Path file = Path.of(required(key));
        var attrs = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!file.isAbsolute() || !attrs.isRegularFile() || !attrs.owner().getName().equals(System.getProperty("user.name"))
                || !attrs.permissions().equals(PosixFilePermissions.fromString("rw-------")) || attrs.size() > max + 1) {
            throw new IllegalArgumentException("Historical START requires private operator-owned credential files");
        }
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(max + 2);
            try {
                String text = new String(bytes, StandardCharsets.US_ASCII);
                if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
                if (text.length() > max || !text.matches(pattern)) {
                    throw new IllegalArgumentException("Historical START credential has an invalid format");
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
}
