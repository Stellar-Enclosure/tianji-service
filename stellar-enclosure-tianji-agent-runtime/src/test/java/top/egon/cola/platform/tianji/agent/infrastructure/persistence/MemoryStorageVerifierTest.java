package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStorageVerifierTest {
    @Test
    void frozen_v10_resource_describes_the_seven_memory_tables_without_changing_its_bytes() throws Exception {
        try (var source = getClass().getResourceAsStream("/db/tianji-agent/V10__add_memory_facts_and_index.sql")) {
            assertNotNull(source); byte[] bytes = source.readAllBytes();
            assertEquals("f26117a6d7c1fc6890d399d886a8b4982964d67744ed77315d95efa217e08c61",
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            String sql = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            var contract = MemoryStorageVerifier.contract(sql.replace("${tianjiMemoryDimensions}", "2"));
            assertEquals(7, contract.size());
            assertEquals("vector(2)", contract.get("agent_memory_vector_v1").columns().get("embedding").type());
            assertEquals(4, contract.get("agent_memory_vector_v1").columns().size());
            assertTrue(contract.get("agent_memory_run_grant").checks().containsKey("memory_grant_ticket"));
        }
    }
    @Test
    void boolean_grouping_cannot_be_weakened_by_parenthesis_normalization() {
        String original = "(a > 0 AND b > 0) OR c > 0";
        assertNotEquals(MemoryStorageVerifier.booleanSignature(original), MemoryStorageVerifier.booleanSignature("a > 0 AND (b > 0 OR c > 0)"));
        assertEquals(MemoryStorageVerifier.booleanSignature(original), MemoryStorageVerifier.booleanSignature("(((a > 0) AND (b > 0))) OR (c > 0)"));
        assertNotEquals(MemoryStorageVerifier.booleanSignature("NOT (a OR b)"), MemoryStorageVerifier.booleanSignature("NOT a OR b"));
        assertEquals(MemoryStorageVerifier.booleanSignature("state=ANY(ARRAY['USER','TEAM'])"), MemoryStorageVerifier.booleanSignature(
                "(state)::text = ANY ((ARRAY['USER'::character varying,'TEAM'::character varying])::text[])"));
        assertEquals(MemoryStorageVerifier.booleanSignature("ciphertext LIKE 'ENC:v1:%'"), MemoryStorageVerifier.booleanSignature("ciphertext ~~ 'ENC:v1:%'::text"));
        String view = "SELECT v.id FROM tianji_agent.vectors v WHERE state='ACTIVE' AND (confirmed OR public_flag)";
        assertEquals(MemoryStorageVerifier.viewSignature(view), MemoryStorageVerifier.viewSignature("SELECT v.id FROM vectors v WHERE (state)::text='ACTIVE'::text AND ((confirmed) OR public_flag);"));
        assertNotEquals(MemoryStorageVerifier.viewSignature(view), MemoryStorageVerifier.viewSignature("SELECT v.id FROM vectors v WHERE (state='ACTIVE' AND confirmed) OR public_flag"));
    }

    @Test
    void migration_contract_scanning_preserves_numeric_types_collation_nullability_and_nested_check_literals() {
        var contract = MemoryStorageVerifier.contract("""
                CREATE TABLE tianji_agent.test_memory (
                    id uuid PRIMARY KEY,
                    owner_id varchar(256) COLLATE "C" NOT NULL,
                    confidence numeric(7, 6) NOT NULL CONSTRAINT test_confidence CHECK (confidence >= 0::numeric AND confidence <= 1::numeric),
                    source jsonb,
                    CONSTRAINT test_source CHECK (source IS NULL OR (source->>'reference' = 'literal ) AND '' OR text'))
                );
                """);
        var table = contract.get("test_memory"); assertNotNull(table);
        assertEquals(4, table.columns().size()); assertTrue(table.columns().get("id").notNull());
        assertEquals("character varying(256)", table.columns().get("owner_id").type()); assertTrue(table.columns().get("owner_id").cCollation());
        assertEquals("numeric(7,6)", table.columns().get("confidence").type()); assertFalse(table.columns().get("source").notNull());
        assertEquals(2, table.checks().size());
        assertEquals(MemoryStorageVerifier.booleanSignature("source IS NULL OR (source->>'reference' = 'literal ) AND '' OR text')"), table.checks().get("test_source"));
    }

    @Test
    void versioned_checksum_matches_the_selected_flyway_implementation_without_resolving_dimension_placeholders() {
        String sql = "\uFEFFCREATE TABLE a (embedding vector(${tianjiMemoryDimensions}));\r\n";
        assertEquals(ChecksumCalculator.calculate(new StringResource(sql)), MemoryStorageVerifier.checksum(sql));
        assertNotEquals(MemoryStorageVerifier.checksum(sql), MemoryStorageVerifier.checksum(sql.replace("${tianjiMemoryDimensions}", "2")));
        assertEquals(MemoryStorageVerifier.checksum(sql), MemoryStorageVerifier.checksum(sql.replace("\r\n", "\n")));
    }
}
