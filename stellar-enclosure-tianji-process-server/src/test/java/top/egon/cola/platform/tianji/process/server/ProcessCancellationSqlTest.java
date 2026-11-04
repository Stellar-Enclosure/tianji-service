package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ProcessCancellationSqlTest {
    @Test
    void immutable_v9_contains_one_complete_audit_and_bounded_dispatch_index() throws Exception {
        byte[] bytes;
        try (var resource = getClass().getResourceAsStream("/db/tianji-process/V9__add_process_cancellation_receipts.sql")) {
            assertNotNull(resource); bytes = resource.readAllBytes();
        }
        String sql = new String(bytes, StandardCharsets.UTF_8);
        assertEquals("dd07ea808cb19f85e983f99a38950add4229208e66dc3c10a60970c1ffc0711b",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var crc = new java.util.zip.CRC32();
        sql.lines().forEach(line -> crc.update(line.getBytes(StandardCharsets.UTF_8)));
        assertEquals(-178365142, (int) crc.getValue());
        assertEquals(1, Pattern.compile("CREATE TABLE ").matcher(sql).results().count());
        assertEquals(List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "command_type", "command_id",
                "request_sha256", "process_instance_id", "start_command_type", "start_command_id", "start_request_sha256",
                "release_key", "model_type", "definition_key", "engine_definition_id", "definition_sha256", "business_key",
                "correlation_id", "expected_version", "reason", "accepted", "result_status", "result_version",
                "result_finished_at", "decided_at", "agent_closure_scheduled"),
                Pattern.compile("(?m)^    ([a-z_0-9]+) (?:bigint|varchar|char|boolean|timestamptz)\\b")
                        .matcher(sql).results().map(match -> match.group(1)).toList());
        assertEquals(3, Pattern.compile("FOREIGN KEY ").matcher(sql).results().count());
        assertTrue(sql.contains("PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"));
        assertTrue(sql.contains("REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)"));
        assertTrue(sql.contains("REFERENCES tianji_process_release_definition (tenant_id, release_key, model_type, definition_key)"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX tianji_process_cancel_accepted_idx\n    ON tianji_process_cancel_receipt (tenant_id, process_instance_id)\n    WHERE accepted;"));
        assertTrue(sql.contains("CREATE INDEX tianji_agent_dispatch_scope_open_idx\n    ON tianji_process_agent_dispatch (tenant_id, scope_type, scope_id, occurrence_id)\n    WHERE state <> 'CLOSED';"));
        assertTrue(sql.contains("result_finished_at timestamptz(6) NOT NULL"));
        assertTrue(sql.contains("decided_at timestamptz(6) NOT NULL"));
        assertTrue(sql.contains("accepted boolean NOT NULL"));
        assertTrue(sql.contains("agent_closure_scheduled boolean NOT NULL"));
        assertFalse(Pattern.compile("(?i)ALTER\\s+TABLE|GRANT\\s|REVOKE\\s|ACT_[A-Z_]+|INSERT\\s+INTO|UPDATE\\s|DELETE\\s+FROM").matcher(sql).find());
        assertFalse(Pattern.compile("(?:result_finished_at|decided_at)\\s*(?:<|>|=)").matcher(sql).find());
    }
}
