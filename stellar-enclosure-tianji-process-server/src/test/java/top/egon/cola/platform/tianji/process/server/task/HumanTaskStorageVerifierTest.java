package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HumanTaskStorageVerifierTest {
    @Test void receipt_contract_has_only_reference_metadata_and_explicit_grouped_authority_constraints() throws Exception {
        String sql = sql(); var contract = HumanTaskStorageVerifier.contract(sql);
        assertEquals(34, contract.columns().size()); assertEquals("bigint", contract.columns().get("authorized_role_id").type()); assertFalse(contract.columns().get("authorized_role_id").required());
        assertEquals("'BPMN'", contract.columns().get("model_type").defaultValue()); assertEquals("'START'", contract.columns().get("start_command_type").defaultValue());
        assertFalse(contract.columns().containsKey("form")); assertFalse(contract.columns().containsKey("user_token")); assertFalse(contract.columns().containsKey("ciphertext"));
        assertTrue(contract.checks().containsKey("human_task_applicant_separation")); assertTrue(contract.checks().containsKey("human_task_authorized_role"));
        assertTrue(contract.checks().get("human_task_decision_kind").startsWith("OR[AND["));
        assertFalse(sql.contains("ON DELETE CASCADE")); assertTrue(sql.contains("REFERENCES tianji_process_command_receipt"));
    }

    @Test void removing_boolean_grouping_or_changing_a_decision_atom_never_preserves_the_contract() {
        var grouped = HumanTaskStorageVerifier.signature("(a IS NULL OR b='APPROVAL') AND c IS NOT NULL");
        assertNotEquals(grouped, HumanTaskStorageVerifier.signature("a IS NULL OR (b='APPROVAL' AND c IS NOT NULL)"));
        assertEquals(grouped, HumanTaskStorageVerifier.signature("(((a IS NULL) OR ((b)::text = 'APPROVAL'::text))) AND (c IS NOT NULL)"));
        assertNotEquals(grouped, HumanTaskStorageVerifier.signature("(a IS NULL OR b='ACTION') AND c IS NOT NULL"));
        assertEquals(HumanTaskStorageVerifier.signature("kind = ANY(ARRAY['A','B'])"), HumanTaskStorageVerifier.signature("((kind)::text = ANY ((ARRAY['A'::character varying,'B'::character varying])::text[]))"));
        assertNotEquals(HumanTaskStorageVerifier.signature("name='x OR y' AND a IS NULL"), HumanTaskStorageVerifier.signature("name='x' OR y AND a IS NULL"));
    }

    @Test void flyway_line_checksum_is_stable_across_bom_and_line_endings() throws Exception {
        String sql = sql(); assertEquals(HumanTaskStorageVerifier.checksum(sql), HumanTaskStorageVerifier.checksum("\uFEFF" + sql.replace("\n", "\r\n")));
        assertNotEquals(HumanTaskStorageVerifier.checksum(sql), HumanTaskStorageVerifier.checksum(sql.replace("requester_id <> subject_id", "requester_id = subject_id")));
    }

    @Test void ambient_transactions_are_rejected_before_any_schema_or_source_io() throws Exception {
        var source = mock(DataSource.class); var verifier = new HumanTaskStorageVerifier(source, "tianji_process", sql());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, verifier::verify); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source);
    }
    private static String sql() throws Exception {
        try (var resource = HumanTaskStorageVerifierTest.class.getResourceAsStream("/db/tianji-process/" + HumanTaskStorageVerifier.FILE)) {
            assertNotNull(resource); return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
