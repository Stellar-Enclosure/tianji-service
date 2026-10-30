package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryPreferenceIntentStorageVerifierTest {
    @Test void the_new_version_is_frozen_after_its_single_creation() throws Exception {
        String raw = MemoryPreferenceIntentStorageVerifier.migration();
        assertEquals("767c1903e08fc9c60cb65802d06c40a60c0e8f9279de9272dd0e9da880dfb122", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void source_grant_lease_progress_and_cancellation_are_one_closed_table_contract_without_user_bearers_or_fact_bodies() throws Exception {
        String sql = sql(); var contract = MemoryPreferenceIntentStorageVerifier.contract(sql);
        assertEquals(43, contract.columns().size());
        for (var column : Set.of("actor_issuer", "actor_type", "actor_id", "scope_id", "binding_id", "policy_ref", "resource_uri", "binding_sha256", "grant_id", "source_event_id", "lease_owner", "commands_sha256", "cancel_command_id", "cancel_request_sha256")) assertTrue(contract.columns().get(column).cCollation());
        assertEquals("uuid", contract.columns().get("intent_id").type()); assertEquals("timestamp with time zone", contract.columns().get("deadline_at").type());
        assertFalse(contract.columns().containsKey("user_token")); assertFalse(contract.columns().containsKey("content_json")); assertFalse(contract.columns().containsKey("input_json"));
        assertTrue(contract.checks().get("preference_intent_actor_type").contains("USER")); assertTrue(contract.checks().get("preference_intent_grant").contains("^9[0-9]{62}$"));
        assertTrue(sql.contains("UNIQUE (tenant_id, actor_issuer, actor_type, actor_id, run_id, scope_id)"));
        assertTrue(sql.contains("REFERENCES tianji_agent.agent_run (tenant_id, run_id)")); assertFalse(sql.contains("ON DELETE CASCADE"));
        assertTrue(contract.checks().keySet().containsAll(Set.of("preference_intent_progress", "preference_intent_lease", "preference_intent_applied", "preference_intent_closed", "preference_intent_cancel")));
    }

    @Test void weakening_evidence_namespace_or_boolean_grouping_changes_the_verified_constraint() throws Exception {
        String sql = sql(); var original = MemoryPreferenceIntentStorageVerifier.contract(sql);
        var namespace = MemoryPreferenceIntentStorageVerifier.contract(sql.replace("'^9[0-9]{62}$'", "'^[89][0-9]{62}$'"));
        assertNotEquals(original.checks().get("preference_intent_grant"), namespace.checks().get("preference_intent_grant"));
        var grouping = MemoryPreferenceIntentStorageVerifier.contract(sql.replace("AND ciphertext IS NOT NULL AND ciphertext ~", "AND ciphertext IS NOT NULL OR ciphertext ~"));
        assertNotEquals(original.checks().get("preference_intent_grant"), grouping.checks().get("preference_intent_grant"));
        assertNotEquals(MemoryStorageVerifier.booleanSignature("(a AND b) OR (c AND d)"), MemoryStorageVerifier.booleanSignature("a AND (b OR c) AND d"));
    }

    @Test void readiness_refuses_an_ambient_transaction_before_reading_or_touching_storage() {
        var source = mock(DataSource.class); TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> new MemoryPreferenceIntentStorageVerifier(source).verify()); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source);
    }

    private static String sql() throws Exception {
        if (MemoryPreferenceIntentStorageVerifierTest.class.getResource("/db/tianji-agent/" + MemoryPreferenceIntentStorageVerifier.FILE) != null) return MemoryPreferenceIntentStorageVerifier.migration();
        String draft = System.getProperty("tianji.memory.preference.intent.ddl");
        return draft == null ? MemoryPreferenceIntentStorageVerifier.migration() : Files.readString(Path.of(draft));
    }
}
