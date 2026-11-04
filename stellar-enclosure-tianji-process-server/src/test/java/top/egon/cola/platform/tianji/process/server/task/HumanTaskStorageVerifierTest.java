package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Test void v8_widens_only_the_two_task_authority_checks_and_adds_action_only_case_guard() throws Exception {
        var original = HumanTaskStorageVerifier.contract(sql());
        var upgraded = HumanTaskStorageVerifier.contractV8(sql(), caseSql());
        assertEquals(original.columns(), upgraded.columns());
        assertEquals(HumanTaskStorageVerifier.signature("model_type = ANY(ARRAY['BPMN','CMMN'])"), upgraded.checks().get("human_task_model_type"));
        assertEquals(HumanTaskStorageVerifier.signature("(model_type='BPMN' AND start_command_type='START') OR (model_type='CMMN' AND start_command_type = ANY(ARRAY['START','CASE_START']))"), upgraded.checks().get("human_task_start_command_type"));
        assertEquals(HumanTaskStorageVerifier.signature("model_type <> 'CMMN' OR task_kind = 'ACTION'"), upgraded.checks().get("human_task_case_kind"));
        var preserved = new java.util.HashMap<>(upgraded.checks());
        preserved.remove("human_task_case_kind");
        preserved.put("human_task_model_type", original.checks().get("human_task_model_type"));
        preserved.put("human_task_start_command_type", original.checks().get("human_task_start_command_type"));
        assertEquals(original.checks(), preserved);
    }

    @Test void v8_contract_retains_grouped_model_start_authority_and_does_not_accept_case_approval() throws Exception {
        String migration = caseSql();
        var valid = HumanTaskStorageVerifier.contractV8(sql(), migration);
        var widenedApproval = HumanTaskStorageVerifier.contractV8(sql(), migration.replace("task_kind = 'ACTION'", "task_kind = ANY(ARRAY['ACTION','APPROVAL'])"));
        assertNotEquals(valid.checks(), widenedApproval.checks());
        assertFalse(valid.checks().get("human_task_start_command_type").equals(HumanTaskStorageVerifier.signature("model_type='BPMN' OR start_command_type = ANY(ARRAY['START','CASE_START'])")));
    }

    @Test void new_binary_verifies_both_unchanged_v7_and_widened_v8_task_catalogs() throws Exception {
        verifyCatalog(false, false);
        verifyCatalog(true, false);
    }

    @Test void v8_is_not_a_waiver_for_old_narrow_or_unvalidated_task_checks() throws Exception {
        verifyCatalog(true, true);
    }

    private static void verifyCatalog(boolean upgraded, boolean corrupt) throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var old = HumanTaskStorageVerifier.contract(sql());
        var checks = rawChecks(sql());
        if (upgraded) {
            checks.put("human_task_model_type", "model_type=ANY(ARRAY['BPMN','CMMN'])");
            checks.put("human_task_start_command_type", "(model_type='BPMN' AND start_command_type='START') OR (model_type='CMMN' AND start_command_type=ANY(ARRAY['START','CASE_START']))");
            checks.put("human_task_case_kind", "model_type <> 'CMMN' OR task_kind='ACTION'");
        }
        if (corrupt) checks.put("human_task_model_type", "model_type = 'BPMN'");
        String migration = sql();
        try (var bases = mockConstruction(ProcessApiSchemaVerifier.class, (mock, context) -> when(mock.hasCaseMigration()).thenReturn(upgraded));
             var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(invocation -> {
                 var args = invocation.getArguments();
                 if (args.length==0 || !(args[0] instanceof String query)) return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                 if (query.contains("pg_is_in_recovery()") || query.contains("has_table_privilege")) return true;
                 List<Map<String,Object>> rows = new ArrayList<>();
                 if (query.contains("schema_history")) rows.add(Map.of("success",true,"checksum",HumanTaskStorageVerifier.checksum(migration),"script",HumanTaskStorageVerifier.FILE));
                 else if (query.contains("attidentity")) old.columns().forEach((name,column) -> {
                     var row=new HashMap<String,Object>(); row.put("attname",name);row.put("type",column.type());row.put("attnotnull",column.required());row.put("attidentity","");row.put("attgenerated","");row.put("collname",column.cCollation()?"C":null);row.put("default_value",column.defaultValue());rows.add(row);
                 });
                 else if (query.contains("c.contype='c'")) checks.forEach((name,expression) -> rows.add(Map.of("conname",name,"convalidated",true,"expression",expression)));
                 else if (query.contains("c.contype IN ('p','u','f')")) {
                     var own=List.of("tenant_id","subject_type","subject_issuer","subject_id","command_type","command_id");
                     rows.add(key("p",own,null,List.of()));rows.add(key("f",own,"tianji_process_command_receipt",own));
                     rows.add(key("f",List.of("tenant_id","requester_type","requester_issuer","requester_id","start_command_type","start_command_id"),"tianji_process_command_receipt",own));
                     rows.add(key("f",List.of("tenant_id","release_key","model_type","definition_key"),"tianji_process_release_definition",List.of("tenant_id","release_key","model_type","definition_key")));
                 } else if (query.contains("FROM pg_index")) {
                     rows.add(index("tianji_human_task_receipt_pkey",List.of("tenant_id","subject_type","subject_issuer","subject_id","command_type","command_id"),true,null));
                     rows.add(index("tianji_human_task_approval_idx",List.of("tenant_id","subject_issuer","approval_receipt_id"),false,"approval_receipt_id IS NOT NULL"));
                     rows.add(index("tianji_human_task_completed_idx",List.of("tenant_id","subject_issuer","task_id"),false,"command_type='TASK_COMPLETE'"));
                 } else throw new AssertionError("Unexpected task catalogue query: "+query);
                 var callback=Arrays.stream(args).filter(RowCallbackHandler.class::isInstance).map(RowCallbackHandler.class::cast).findFirst();
                 if(callback.isPresent()){for(var row:rows)callback.orElseThrow().processRow(resultSet(row));return null;}
                 var mapper=Arrays.stream(args).filter(RowMapper.class::isInstance).map(RowMapper.class::cast).findFirst().orElseThrow();
                 var results=new ArrayList<>();for(var row:rows)results.add(mapper.mapRow(resultSet(row),results.size()));return results;
             }))) {
            var verifier=new HumanTaskStorageVerifier(source,"tianji_process",migration);
            if(corrupt)assertThrows(IllegalStateException.class,verifier::verify);else assertDoesNotThrow(verifier::verify);
            if(!corrupt){assertEquals(1,bases.constructed().size());verify(bases.constructed().getFirst()).verify();verify(bases.constructed().getFirst()).hasCaseMigration();verify(bases.constructed().getFirst(),never()).verifyCase();}
        }
    }
    private static HashMap<String,String> rawChecks(String sql) {
        var result = new HashMap<String,String>();
        var matcher = java.util.regex.Pattern.compile("CONSTRAINT\\s+([a-z0-9_]+)\\s+CHECK\\s*\\(").matcher(sql);
        while (matcher.find()) {
            int open = matcher.end()-1, depth = 0; boolean quote = false;
            for (int i=open;i<sql.length();i++) {
                char c=sql.charAt(i);
                if(c=='\'') { if(quote && i+1<sql.length() && sql.charAt(i+1)=='\'') i++; else quote=!quote; }
                else if(!quote) { if(c=='(')depth++; else if(c==')' && --depth==0){result.put(matcher.group(1),sql.substring(open+1,i));break;} }
            }
        }
        return result;
    }
    private static Map<String,Object> key(String type,List<String> fields,String target,List<String> targetFields){
        var row=new HashMap<String,Object>();row.put("contype",type);row.put("convalidated",true);row.put("condeferrable",false);row.put("condeferred",false);row.put("confupdtype","f".equals(type)?"a":"");row.put("deletion","f".equals(type)?"a":"");row.put("fields",fields);row.put("target_schema",target==null?null:"tianji_process");row.put("target",target);row.put("target_fields",targetFields);return row;
    }
    private static Map<String,Object> index(String name,List<String> fields,boolean primary,String predicate){
        var row=new HashMap<String,Object>();row.put("relname",name);row.put("fields",fields);row.put("indisunique",true);row.put("indisprimary",primary);row.put("indnkeyatts",fields.size());row.put("indnatts",fields.size());row.put("amname","btree");row.put("predicate",predicate);for(String flag:List.of("indisvalid","indisready","indislive","default_classes","column_collations"))row.put(flag,true);return row;
    }
    private static ResultSet resultSet(Map<String,Object> row)throws Exception{
        var rs=mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(i->row.get(i.getArgument(0))==null?null:row.get(i.getArgument(0)).toString());
        when(rs.getObject(anyString())).thenAnswer(i->row.get(i.getArgument(0)));
        when(rs.getBoolean(anyString())).thenAnswer(i->Boolean.TRUE.equals(row.get(i.getArgument(0))));
        when(rs.getInt(anyString())).thenAnswer(i->row.get(i.getArgument(0))instanceof Number n?n.intValue():0);
        when(rs.getArray(anyString())).thenAnswer(i->{var value=row.get(i.getArgument(0));if(value==null)return null;var array=mock(java.sql.Array.class);when(array.getArray()).thenReturn(((List<?>)value).toArray(String[]::new));return array;});return rs;
    }

    private static String caseSql() throws Exception {
        try (var resource = HumanTaskStorageVerifierTest.class.getResourceAsStream("/db/tianji-process/V8__add_formal_cmmn_case_support.sql")) {
            assertNotNull(resource); return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sql() throws Exception {
        try (var resource = HumanTaskStorageVerifierTest.class.getResourceAsStream("/db/tianji-process/" + HumanTaskStorageVerifier.FILE)) {
            assertNotNull(resource); return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
